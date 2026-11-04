package com.okdsfin.suningangho2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimTest {

    private fun run(start: GameState, seconds: Double, dt: Double = 0.05, random: Random = Random(1), events: MutableList<GameEvent>? = null): GameState {
        var s = start
        var t = 0.0
        while (t < seconds) {
            s = Sim.step(s, dt, random, events)
            t += dt
        }
        return s
    }

    @Test
    fun heroKillsEnemiesAndEarns() {
        val events = mutableListOf<GameEvent>()
        val s = run(GameState(), 30.0, events = events)
        assertTrue(s.kills > 3)
        assertTrue(s.silver > 0)
        assertTrue(events.any { it is GameEvent.HeroHit })
        assertTrue("웨이브가 오른다", s.wave > 1 || s.stage > 1)
    }

    @Test
    fun bossBlocksAndRetreatsToFarm() {
        // 너무 강한 보스: 시간 안에 못 잡고 방치로 돌아간다
        val s = GameState(stage = 40, wave = Balance.WAVES)
        val events = mutableListOf<GameEvent>()
        val after = run(s, 40.0, events = events)
        assertTrue(events.any { it is GameEvent.HeroDown || it is GameEvent.BossTimeout })
        assertEquals(Mode.FARM, after.mode)
        assertTrue(after.wave < Balance.WAVES)
        assertTrue("방치 사냥에는 보스가 없다", run(after, 120.0).stage <= 40)
    }

    @Test
    fun upgradesAndBreakthrough() {
        var s = GameState(silver = 1000.0, inner = 40.0)
        val atk = Balance.attack(s)
        s = Sim.buy(s, Upgrade.ATTACK)
        assertEquals(1, s.level(Upgrade.ATTACK))
        assertEquals(1000 - Balance.cost(Upgrade.ATTACK, 0), s.silver, 1e-9)
        assertTrue(Balance.attack(s) > atk)
        s = Sim.breakthrough(s)
        assertEquals(1, s.realm)
        assertEquals("경지마다 ×2", Balance.attack(s.copy(realm = 0)) * 2, Balance.attack(s), 1e-9)
        assertEquals("내공이 모자라면 그대로", s, Sim.breakthrough(s))
    }

    @Test
    fun offlineReward() {
        val s = GameState(stage = 5)
        val r = Offline.reward(s, 3600.0)!!
        assertTrue(r.silver > 0 && r.inner > 0)
        assertEquals("12시간까지만", Balance.OFFLINE_CAP_SECONDS, Offline.reward(s, 99_999.0)!!.seconds, 1e-9)
        assertEquals(null, Offline.reward(s, 10.0))
        assertEquals(s.copy(silver = r.silver, inner = r.inner, kills = r.kills), Offline.apply(s, r))
    }

    @Test
    fun saveRoundTrip() {
        val s = run(GameState(), 20.0).copy(levels = mapOf(Upgrade.AGILITY to 3))
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }

    /** 욕심껏 사는 플레이어의 진행 속도 (숫자를 고칠 때 보는 곳) */
    @Test
    fun pacing() {
        var s = GameState()
        val random = Random(7)
        val report = mutableListOf<String>()
        val checkpoints = listOf(5, 15, 30, 60, 120, 240, 480, 960, 1440).map { it * 60 }
        var second = 0
        for (cp in checkpoints) {
            while (second < cp) {
                s = run(s, 1.0, dt = 0.1, random = random)
                second++
                // 가장 싼 수련부터 산다
                while (true) {
                    val u = Upgrade.entries.filter { Sim.canBuy(s, it) }.minByOrNull { Balance.cost(it, s.level(it)) } ?: break
                    s = Sim.buy(s, u)
                }
                if (Sim.canBreakthrough(s)) s = Sim.breakthrough(s)
                if (s.mode == Mode.FARM && second % 30 == 0) s = Sim.setMode(s, Mode.PUSH)
            }
            report += "${cp / 60}분: 스테이지 ${s.maxStage}, 경지 ${Realms.name(s.realm)}, 외공 ${s.level(Upgrade.ATTACK)} 기혈 ${s.level(Upgrade.VITALITY)} 신법 ${s.level(Upgrade.AGILITY)} 안목 ${s.level(Upgrade.INSIGHT)}"
        }
        println(report.joinToString("\n"))
        assertTrue(report.joinToString("\n"), s.maxStage in 100..250)
    }
}
