package com.okdsfin.suningangho2.core

import kotlin.random.Random

/** 지역: 10스테이지마다 바뀐다. [sprites]는 도트 이름 (앱의 스프라이트 표와 맞춘다) */
data class Region(val name: String, val foes: List<Pair<String, String>>, val boss: Pair<String, String>, val palette: Int)

object Regions {
    val all: List<Region> = listOf(
        Region("청운촌 뒷산", listOf("wolf" to "들개", "bandit" to "좀도둑"), "chief" to "뒷산 두목", 0),
        Region("흑풍채", listOf("bandit" to "흑풍채 산적", "wolf" to "산채 사냥개"), "chief" to "흑풍채주", 1),
        Region("낙엽림", listOf("wolf" to "회색 늑대", "cultist" to "떠돌이 마인"), "elder" to "낙엽림 마두", 2),
        Region("혈월곡", listOf("cultist" to "혈월교도", "bandit" to "혈월 사병"), "elder" to "혈월 호법", 3),
    )

    fun of(stage: Int): Region = all[((stage - 1) / 10) % all.size]
}

/** 자동 전투 (기획서 1~2장): [dt]초만큼 흘려 보낸다 */
object Sim {

    fun spawn(s: GameState, random: Random): Enemy {
        val region = Regions.of(s.stage)
        val boss = s.isBossWave
        val (sprite, name) = if (boss) region.boss else region.foes.random(random)
        val hp = Balance.enemyHp(s.stage, boss)
        return Enemy(sprite, name, hp, hp, Balance.enemyAtk(s.stage, boss), boss)
    }

    fun step(start: GameState, dt: Double, random: Random, events: MutableList<GameEvent>? = null): GameState {
        var s = start
        val maxHp = Balance.maxHp(s)
        // 좌선: 싸우는 동안에도 내공은 조금씩 돈다. 기혈도 천천히 차오른다
        s = s.copy(inner = s.inner + Balance.innerPerSec(s.realm) * dt, heroHp = minOf(maxHp, s.hp() + maxHp * Balance.REGEN_PER_SEC * dt))

        if (s.wait > 0) {
            s = s.copy(wait = s.wait - dt)
            if (s.wait > 0) return s
        }
        val enemy = s.enemy ?: return s.copy(enemy = spawn(s, random), bossTime = 0.0)

        // 다가오는 중: 무인은 걷고 배경이 흐른다
        if (enemy.distance > 0) {
            return s.copy(enemy = enemy.copy(distance = (enemy.distance - dt / Balance.APPROACH_TIME).coerceAtLeast(0.0)))
        }

        var foe = enemy
        var hp = s.hp()
        var heroTimer = s.heroAttackTimer - dt
        val interval = Balance.attackInterval(s)
        while (heroTimer <= 0 && foe.hp > 0) {
            val crit = random.nextDouble() < Balance.critChance(s)
            val dmg = Balance.attack(s) * random.nextDouble(0.9, 1.1) * if (crit) Balance.CRIT_MULT else 1.0
            foe = foe.copy(hp = foe.hp - dmg)
            events?.add(GameEvent.HeroHit(dmg, crit))
            heroTimer += interval
        }
        var foeTimer = foe.attackTimer - dt
        while (foe.hp > 0 && foeTimer <= 0 && hp > 0) {
            val dmg = foe.atk * random.nextDouble(0.9, 1.1)
            hp -= dmg
            events?.add(GameEvent.EnemyHit(dmg))
            foeTimer += Balance.ENEMY_INTERVAL
        }
        foe = foe.copy(attackTimer = foeTimer)
        s = s.copy(heroHp = hp, heroAttackTimer = heroTimer.coerceAtLeast(-interval), enemy = foe)

        if (foe.hp <= 0) return defeated(s, foe, events)
        if (hp <= 0) {
            events?.add(GameEvent.HeroDown)
            return retreat(s)
        }
        if (foe.boss) {
            val t = s.bossTime + dt
            if (t >= Balance.BOSS_TIME) {
                events?.add(GameEvent.BossTimeout)
                return retreat(s)
            }
            s = s.copy(bossTime = t)
        }
        return s
    }

    private fun defeated(s: GameState, foe: Enemy, events: MutableList<GameEvent>?): GameState {
        val silver = Balance.silverDrop(s.stage, foe.boss)
        val inner = Balance.innerDrop(s.stage, foe.boss)
        events?.add(GameEvent.Kill(silver, inner, foe.boss))
        val base = s.copy(silver = s.silver + silver, inner = s.inner + inner, kills = s.kills + 1, enemy = null, wait = Balance.RESPAWN_TIME, heroAttackTimer = 0.0)
        return when {
            foe.boss -> {
                events?.add(GameEvent.StageClear(s.stage))
                val next = s.stage + 1
                base.copy(stage = next, wave = 1, maxStage = maxOf(s.maxStage, next), heroHp = Balance.maxHp(s))
            }
            s.mode == Mode.FARM -> base.copy(wave = s.wave % (Balance.WAVES - 1) + 1)
            else -> base.copy(wave = s.wave + 1)
        }
    }

    /** 졌거나 보스를 놓쳤다: 보스에게 막혔으면 그 스테이지에서, 잡졸에게 졌으면 한 스테이지 물러나 방치 사냥 */
    private fun retreat(s: GameState): GameState = s.copy(
        stage = if (s.isBossWave) s.stage else maxOf(1, s.stage - 1),
        wave = 1,
        mode = Mode.FARM,
        enemy = null,
        heroHp = Balance.maxHp(s),
        bossTime = 0.0,
        wait = Balance.REVIVE_TIME,
    )

    // ── 손으로 하는 일 ──

    /** 도전 ↔ 방치 */
    fun setMode(s: GameState, mode: Mode): GameState =
        if (s.mode == mode) s else s.copy(mode = mode, wave = 1, enemy = null, wait = Balance.RESPAWN_TIME, bossTime = 0.0)

    fun canBuy(s: GameState, u: Upgrade): Boolean = s.level(u) < u.maxLevel && s.silver >= Balance.cost(u, s.level(u))

    fun buy(s: GameState, u: Upgrade): GameState {
        if (!canBuy(s, u)) return s
        val cost = Balance.cost(u, s.level(u))
        return s.copy(silver = s.silver - cost, levels = s.levels + (u to s.level(u) + 1))
    }

    fun canBreakthrough(s: GameState): Boolean = s.realm < Realms.last && s.inner >= Balance.breakthroughCost(s.realm)

    fun breakthrough(s: GameState, events: MutableList<GameEvent>? = null): GameState {
        if (!canBreakthrough(s)) return s
        val next = s.copy(inner = s.inner - Balance.breakthroughCost(s.realm), realm = s.realm + 1)
        events?.add(GameEvent.Breakthrough(next.realm))
        return next.copy(heroHp = Balance.maxHp(next))
    }
}
