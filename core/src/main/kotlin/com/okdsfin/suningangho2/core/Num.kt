package com.okdsfin.suningangho2.core

import kotlin.math.floor

/** 큰 수 표시 */
object Num {
    private val korean = listOf("" to 1.0, "만" to 1e4, "억" to 1e8, "조" to 1e12, "경" to 1e16, "해" to 1e20, "자" to 1e24, "양" to 1e28)
    private val latin = listOf("" to 1.0, "K" to 1e3, "M" to 1e6, "B" to 1e9, "T" to 1e12)

    /** 화면 글: 1234 → "1234", 12345 → "1.23만", 3.4e9 → "34.0억" */
    fun kr(v: Double): String {
        if (v < 1e4) return floor(v).toLong().toString()
        val (unit, base) = korean.last { v >= it.second }
        return trim(v / base) + unit
    }

    /** 도트 숫자 (0-9, K M B T, 점만 쓴다) */
    fun short(v: Double): String {
        if (v < 1e3) return floor(v).toLong().coerceAtLeast(0).toString()
        if (v >= 1e15) return "999T"
        val (unit, base) = latin.last { v >= it.second }
        return trim(v / base) + unit
    }

    private fun trim(x: Double): String = when {
        x >= 100 -> floor(x).toLong().toString()
        x >= 10 -> "%.1f".format(floor(x * 10) / 10)
        else -> "%.2f".format(floor(x * 100) / 100)
    }
}
