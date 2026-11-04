package com.okdsfin.suningangho2.core

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random

/** 싸움 한 판의 한쪽 */
@Serializable
data class Fighter(
    val name: String,
    /** 도트 이름 (앱의 스프라이트 표) */
    val sprite: String,
    val hp: Double,
    val maxHp: Double,
    val atk: Double,
    val interval: Double,
    val timer: Double = 0.0,
    val boss: Boolean = false,
)

/**
 * 도트 자동 전투 (기획서 5장). 앱을 보고 있으면 실시간으로 흘리고, 꺼 둔 동안은 [runToEnd]로 즉시 끝낸다.
 * [distance] 1 → 0: 적이 다가오는 동안. [silver]·[inner]는 이기면 받는 것.
 */
@Serializable
data class Fight(
    val hero: Fighter,
    val enemy: Fighter,
    val silver: Int,
    val inner: Double,
    val distance: Double = 1.0,
    val time: Double = 0.0,
)

/** 화면 연출에 쓰는 일 */
sealed class FightEvent {
    data class HeroHit(val amount: Double, val crit: Boolean) : FightEvent()
    data class EnemyHit(val amount: Double) : FightEvent()
}

object FightSim {
    const val APPROACH = 0.8
    const val CRIT = 0.08
    const val TIME_LIMIT = 40.0

    /** 위험도 d가 대략 몇 경지에 해당하는가: 1 → 0, 2 → 1, 3 → 3, 4 → 4, 5 → 6 */
    fun dangerRealm(danger: Int): Int = ((danger - 1) * 1.5).toInt()

    /** 길 위의 적: 위험도에 맞는 경지에서 ±1경지 흔들린다 */
    fun roadFight(hero: Hero, danger: Int, random: Random): Fight {
        val realm = (dangerRealm(danger) + random.nextInt(-1, 2)).coerceAtLeast(0)
        val scale = 2.0.pow(realm) * random.nextDouble(0.75, 0.95)
        val (sprite, name) = when {
            danger >= 4 && random.nextBoolean() -> "cultist" to "마인"
            random.nextDouble() < 0.4 -> "wolf" to if (danger >= 3) "회색 늑대" else "들개"
            else -> "bandit" to if (danger >= 3) "산적 두목" else "산적"
        }
        val enemy = Fighter(name, sprite, 50 * scale, 50 * scale, 5 * scale, if (sprite == "wolf") 0.9 else 1.2)
        val reward = 1.8.pow(danger - 1)
        return Fight(heroFighter(hero), enemy, (10 * reward * random.nextDouble(0.7, 1.3)).toInt(), 3 * reward)
    }

    fun heroFighter(h: Hero) = Fighter(h.name, "hero", h.currentHp(), h.maxHp, h.atk, h.interval)

    fun over(f: Fight): Boolean = f.hero.hp <= 0 || f.enemy.hp <= 0 || f.time >= TIME_LIMIT

    /** 이겼는가 (시간이 다 되면 진 것) */
    fun won(f: Fight): Boolean = f.enemy.hp <= 0 && f.hero.hp > 0

    fun step(f: Fight, dt: Double, random: Random, events: MutableList<FightEvent>? = null): Fight {
        if (over(f)) return f
        if (f.distance > 0) return f.copy(distance = (f.distance - dt / APPROACH).coerceAtLeast(0.0))
        var hero = f.hero.copy(timer = f.hero.timer - dt)
        var enemy = f.enemy.copy(timer = f.enemy.timer - dt)
        while (hero.timer <= 0 && enemy.hp > 0) {
            val crit = random.nextDouble() < CRIT
            val dmg = hero.atk * random.nextDouble(0.85, 1.15) * if (crit) 2.0 else 1.0
            enemy = enemy.copy(hp = enemy.hp - dmg)
            events?.add(FightEvent.HeroHit(dmg, crit))
            hero = hero.copy(timer = hero.timer + hero.interval)
        }
        while (enemy.timer <= 0 && enemy.hp > 0 && hero.hp > 0) {
            val dmg = enemy.atk * random.nextDouble(0.85, 1.15)
            hero = hero.copy(hp = hero.hp - dmg)
            events?.add(FightEvent.EnemyHit(dmg))
            enemy = enemy.copy(timer = enemy.timer + enemy.interval)
        }
        return f.copy(hero = hero, enemy = enemy, time = f.time + dt)
    }

    fun runToEnd(start: Fight, random: Random): Fight {
        var f = start
        while (!over(f)) f = step(f, 0.05, random)
        return f
    }
}
