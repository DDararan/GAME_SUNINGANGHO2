package com.okdsfin.suningangho2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {

    private fun fresh(seed: Int = 1) = Game.new("시험", Random(seed))

    @Test
    fun mapIsConnected() {
        for (p in WorldMap.places) {
            assertTrue("${p.name}까지 길이 있다", WorldMap.path(WorldMap.START, p.id).isNotEmpty())
            assertTrue(p.x in 0 until WorldMap.WIDTH && p.y in 0 until WorldMap.HEIGHT)
        }
        val route = WorldMap.path("cheongun", "namhae")
        assertEquals("cheongun", route.first())
        assertEquals("namhae", route.last())
        assertTrue(WorldMap.pathDays(route) in 15..40)
    }

    @Test
    fun heroWandersOnHisOwn() {
        val s = Game.advance(fresh(), 60.0, Random(2), live = false)
        assertTrue("두 달이면 여러 곳을 떠돈다: ${s.visited}", s.visited.size >= 3)
        assertTrue(s.log.any { "길을 나섰다" in it.text })
        assertEquals(15, s.ageYears)
    }

    @Test
    fun destinationIsFollowedAndHeld() {
        var s = Game.setDestination(fresh(), "cheonyang")
        assertEquals("cheonyang", s.destination)
        s = Game.advance(s, 60.0, Random(3), live = false)
        assertEquals("목적지에 머문다", "cheonyang", s.at)
        assertTrue(s.hold)
        assertNull(s.destination)
        val later = Game.advance(s, 30.0, Random(4), live = false)
        assertEquals("목적지에 닿으면 떠나지 않는다", "cheonyang", later.at)
    }

    @Test
    fun liveFightsPauseTime() {
        var s = fresh()
        var guard = 0
        while (s.fight == null && guard++ < 2000) s = Game.advance(s, 1.0, Random(guard), live = true)
        val f = assertNotNull(s.fight).let { s.fight!! }
        val paused = Game.advance(s, 5.0, Random(1), live = true)
        assertEquals("싸우는 동안 시간은 멈춘다", s.day, paused.day, 1e-9)
        val done = Game.finishFight(s, FightSim.runToEnd(f, Random(1)))
        assertNull(done.fight)
        assertTrue(done.hero.wins + done.hero.losses == s.hero.wins + s.hero.losses + 1)
    }

    @Test
    fun roadEventsResolve() {
        val s = fresh().copy(pending = listOf(Pending("peddler", 0, 2)), hero = fresh().hero.copy(silver = 100))
        val r = Game.resolve(s, s.pending.single(), 0, Random(1))
        assertTrue(r.pending.isEmpty())
        assertEquals(60, r.hero.silver)
        val poor = s.copy(hero = s.hero.copy(silver = 5))
        assertEquals("은자가 모자라면 못 고른다", poor, Game.resolve(poor, poor.pending.single(), 0, Random(1)))
    }

    @Test
    fun aYearOnTheRoad() {
        val s = Game.advance(fresh(7), 365.0, Random(7), live = false)
        assertTrue(s.hero.alive)
        assertEquals(16, s.ageYears)
        assertTrue("1년이면 지도 절반쯤: ${s.visited.size}", s.visited.size >= 6)
        assertTrue("싸움이 있었다", s.hero.wins + s.hero.losses > 5)
        assertTrue("경지가 오른다: ${s.hero.realm}", s.hero.realm >= 1)
        println("1년: 경지 ${Realms.name(s.hero.realm)}, ${s.hero.wins}승 ${s.hero.losses}패, 은자 ${s.hero.silver}, 다녀간 곳 ${s.visited.size}")
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }

    @Test
    fun lifeEnds() {
        val old = fresh().copy(day = (70 - Hero.START_AGE) * 365.0 - 1)
        val s = Game.advance(old, 3.0, Random(1), live = false)
        assertFalse(s.hero.alive)
        assertTrue(s.log.last().text.contains("천수"))
    }

    @Test
    fun calendarAndJosa() {
        assertEquals("강호력 1년 1월 1일", Calendar.text(0.0))
        assertEquals("강호력 2년 1월 1일", Calendar.text(365.0))
        assertEquals("천양성으로", Josa.ro("천양성"))
        assertEquals("하양현으로", Josa.ro("하양현"))
        assertEquals("북관으로", Josa.ro("북관"))
        assertEquals("남해항으로", Josa.ro("남해항"))
        assertEquals("운하진으로", Josa.ro("운하진"))
        assertEquals("금강사로", Josa.ro("금강사"))
    }
}
