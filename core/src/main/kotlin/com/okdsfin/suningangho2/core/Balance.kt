package com.okdsfin.suningangho2.core

import kotlin.math.min
import kotlin.math.pow

/** 경지 (기획서 4장): 무림 17단계 + 수선계 9단계 */
object Realms {
    val names: List<String> = listOf(
        "9급무사", "8급무사", "7급무사", "6급무사", "5급무사", "4급무사", "3급무사", "2급무사", "1급무사",
        "삼류", "이류", "일류", "절정", "초절정", "화경", "현경", "생사경",
        "연기기", "축기기", "금단기", "원영기", "화신기", "연허기", "합체기", "대승기", "도겁기",
    )

    /** 무림 경지 수 (그다음부터 수선계) */
    const val MURIM = 17

    fun name(r: Int): String = names[r.coerceIn(0, names.lastIndex)]

    val last: Int get() = names.lastIndex
}

/** 은자로 올리는 수련 (기획서 3장) */
enum class Upgrade(val displayName: String, val description: String, val maxLevel: Int) {
    ATTACK("외공", "공격력", Int.MAX_VALUE),
    VITALITY("기혈", "최대 기혈", Int.MAX_VALUE),
    AGILITY("신법", "공격 속도", 100),
    INSIGHT("안목", "치명 확률", 90),
}

/** 성장 공식 (기획서 2~4장). 숫자를 고칠 때는 기획서도 함께 고친다 */
object Balance {
    const val WAVES = 10
    const val BOSS_TIME = 30.0
    const val APPROACH_TIME = 1.0
    const val RESPAWN_TIME = 0.35
    const val REVIVE_TIME = 1.5
    const val ENEMY_INTERVAL = 1.2
    const val REGEN_PER_SEC = 0.01
    const val CRIT_MULT = 2.0
    const val OFFLINE_CAP_SECONDS = 12 * 3600.0

    /** 25레벨마다 2배 */
    private fun milestone(level: Int): Double = 2.0.pow(level / 25)

    /** 경지마다 공격·기혈 2배 */
    fun realmMult(realm: Int): Double = 2.0.pow(realm)

    fun attack(s: GameState): Double {
        val l = s.level(Upgrade.ATTACK)
        return 5.0 * (1 + l) * milestone(l) * realmMult(s.realm)
    }

    fun maxHp(s: GameState): Double {
        val l = s.level(Upgrade.VITALITY)
        return 60.0 * (1 + 0.5 * l) * milestone(l) * realmMult(s.realm)
    }

    /** 공격 간격 (초) */
    fun attackInterval(s: GameState): Double = 1.0 / (1 + 0.02 * min(s.level(Upgrade.AGILITY), 100))

    fun critChance(s: GameState): Double = 0.05 + 0.005 * min(s.level(Upgrade.INSIGHT), 90)

    fun cost(u: Upgrade, level: Int): Double = when (u) {
        Upgrade.ATTACK -> 8 * 1.15.pow(level)
        Upgrade.VITALITY -> 10 * 1.15.pow(level)
        Upgrade.AGILITY -> 50 * 1.25.pow(level)
        Upgrade.INSIGHT -> 100 * 1.22.pow(level)
    }

    // ── 적 (기획서 2장) ──

    fun enemyHp(stage: Int, boss: Boolean): Double = 12 * 1.15.pow(stage - 1) * if (boss) 6.0 else 1.0
    fun enemyAtk(stage: Int, boss: Boolean): Double = 2 * 1.13.pow(stage - 1) * if (boss) 1.6 else 1.0
    fun silverDrop(stage: Int, boss: Boolean): Double = 2 * 1.11.pow(stage - 1) * if (boss) 8.0 else 1.0
    fun innerDrop(stage: Int, boss: Boolean): Double = 0.1 * 1.05.pow(stage - 1) * if (boss) 8.0 else 1.0

    // ── 내공과 돌파 (기획서 4장) ──

    fun breakthroughCost(realm: Int): Double = 30 * 2.2.pow(realm)

    /** 좌선으로 초당 쌓이는 내공 */
    fun innerPerSec(realm: Int): Double = 0.5 * 1.5.pow(realm)
}
