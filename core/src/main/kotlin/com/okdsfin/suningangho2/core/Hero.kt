package com.okdsfin.suningangho2.core

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random

/** 경지 (1편과 같은 이름): 무림 17단계 + 수선계 9단계 */
object Realms {
    val names: List<String> = listOf(
        "9급무사", "8급무사", "7급무사", "6급무사", "5급무사", "4급무사", "3급무사", "2급무사", "1급무사",
        "삼류", "이류", "일류", "절정", "초절정", "화경", "현경", "생사경",
        "연기기", "축기기", "금단기", "원영기", "화신기", "연허기", "합체기", "대승기", "도겁기",
    )

    fun name(r: Int): String = names[r.coerceIn(0, names.lastIndex)]

    val last: Int get() = names.lastIndex

    /** 경지별 수명 (1편 표를 따른다) */
    fun lifespan(r: Int): Int = when {
        r <= 8 -> 70
        r == 9 -> 80
        r == 10 -> 90
        r == 11 -> 100
        r == 12 -> 120
        r == 13 -> 150
        r == 14 -> 200
        r == 15 -> 300
        r == 16 -> 500
        else -> 500 + (r - 16) * 300
    }

    /** 돌파에 드는 내공 */
    fun breakthroughCost(r: Int): Double = 20 * 2.0.pow(r)
}

@Serializable
enum class Stat(val displayName: String) {
    STRENGTH("근력"),
    CONSTITUTION("체질"),
    AGILITY("신법"),
}

/** 주인공 */
@Serializable
data class Hero(
    val name: String,
    val stats: Map<Stat, Int>,
    val realm: Int = 0,
    val inner: Double = 0.0,
    val silver: Int = 30,
    /** 지금 기혈. 음수면 가득 */
    val hp: Double = -1.0,
    val wins: Int = 0,
    val losses: Int = 0,
    val alive: Boolean = true,
) {
    fun stat(s: Stat): Int = stats[s] ?: 10

    val atk: Double get() = 6.0 * (1 + (stat(Stat.STRENGTH) - 10) * 0.05) * 2.0.pow(realm)
    val maxHp: Double get() = 60.0 * (1 + (stat(Stat.CONSTITUTION) - 10) * 0.05) * 2.0.pow(realm)
    val interval: Double get() = 1.0 / (1 + (stat(Stat.AGILITY) - 10) * 0.03)

    fun currentHp(): Double = if (hp < 0) maxHp else hp.coerceAtMost(maxHp)

    companion object {
        const val START_AGE = 15

        /** 3d6 */
        fun roll(name: String, random: Random): Hero =
            Hero(name, Stat.entries.associateWith { (1..3).sumOf { random.nextInt(1, 7) } })
    }
}
