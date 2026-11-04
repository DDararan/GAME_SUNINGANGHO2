package com.okdsfin.suningangho2.core

import kotlinx.serialization.Serializable

/** 도전: 웨이브를 깨면 앞으로. 방치: 보스 앞까지 같은 스테이지를 되풀이한다 */
@Serializable
enum class Mode { PUSH, FARM }

/** 지금 맞서는 적. [distance] 1 → 0: 화면 오른쪽에서 다가오는 중 (0이면 맞붙음) */
@Serializable
data class Enemy(
    val sprite: String,
    val name: String,
    val hp: Double,
    val maxHp: Double,
    val atk: Double,
    val boss: Boolean,
    val distance: Double = 1.0,
    val attackTimer: Double = Balance.ENEMY_INTERVAL,
)

@Serializable
data class GameState(
    val stage: Int = 1,
    val wave: Int = 1,
    val mode: Mode = Mode.PUSH,
    val silver: Double = 0.0,
    val inner: Double = 0.0,
    val realm: Int = 0,
    val levels: Map<Upgrade, Int> = emptyMap(),
    /** 무인의 기혈. 음수면 아직 정하지 않음(가득) */
    val heroHp: Double = -1.0,
    val heroAttackTimer: Double = 0.0,
    val enemy: Enemy? = null,
    /** 보스와 싸운 시간 */
    val bossTime: Double = 0.0,
    /** 다음 적이 나오거나 무인이 일어설 때까지 */
    val wait: Double = 0.0,
    val maxStage: Int = 1,
    val kills: Long = 0,
    /** 마지막으로 저장한 실제 시각 (오프라인 보상 계산) */
    val savedAt: Long = 0,
) {
    fun level(u: Upgrade): Int = levels[u] ?: 0

    fun hp(): Double = if (heroHp < 0) Balance.maxHp(this) else heroHp

    val isBossWave: Boolean get() = mode == Mode.PUSH && wave == Balance.WAVES
}

/** 화면 연출에 쓰는 일 (도트 장면이 받아서 그린다) */
sealed class GameEvent {
    data class HeroHit(val amount: Double, val crit: Boolean) : GameEvent()
    data class EnemyHit(val amount: Double) : GameEvent()
    data class Kill(val silver: Double, val inner: Double, val boss: Boolean) : GameEvent()
    data class StageClear(val stage: Int) : GameEvent()
    object HeroDown : GameEvent()
    object BossTimeout : GameEvent()
    data class Breakthrough(val realm: Int) : GameEvent()
}
