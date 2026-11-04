package com.okdsfin.suningangho2.core

import kotlinx.serialization.json.Json
import kotlin.random.Random

/** 자리를 비운 동안 쌓인 것 */
data class OfflineReward(val seconds: Double, val silver: Double, val inner: Double, val kills: Long)

/**
 * 오프라인 보상 (기획서 1장): 지금 스테이지에서 방치 사냥을 2분 돌려 본 속도로, 비운 시간(최대 12시간)만큼 준다.
 * 스테이지는 오르지 않는다. 자원만 쌓인다.
 */
object Offline {
    private const val SAMPLE_SECONDS = 120.0
    private const val SAMPLE_DT = 0.05

    fun reward(s: GameState, seconds: Double): OfflineReward? {
        val t = seconds.coerceAtMost(Balance.OFFLINE_CAP_SECONDS)
        if (t < 60) return null
        var sim = Sim.setMode(s.copy(enemy = null, wait = 0.0, heroHp = -1.0), Mode.FARM)
        val random = Random(s.kills + s.stage)
        var elapsed = 0.0
        while (elapsed < SAMPLE_SECONDS) {
            sim = Sim.step(sim, SAMPLE_DT, random)
            elapsed += SAMPLE_DT
        }
        val ratio = t / SAMPLE_SECONDS
        return OfflineReward(t, (sim.silver - s.silver) * ratio, (sim.inner - s.inner) * ratio, ((sim.kills - s.kills) * ratio).toLong())
    }

    fun apply(s: GameState, r: OfflineReward): GameState =
        s.copy(silver = s.silver + r.silver, inner = s.inner + r.inner, kills = s.kills + r.kills)
}

object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(s: GameState): String = json.encodeToString(GameState.serializer(), s)

    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)
}
