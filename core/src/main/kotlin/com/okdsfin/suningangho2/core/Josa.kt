package com.okdsfin.suningangho2.core

/** 받침에 맞는 조사 */
object Josa {
    private fun final(word: String): Int? {
        val c = word.lastOrNull() ?: return null
        if (c !in '가'..'힣') return null
        return (c - '가') % 28
    }

    private fun has(word: String) = (final(word) ?: 0) != 0

    fun i(word: String) = word + if (has(word)) "이" else "가"
    fun eul(word: String) = word + if (has(word)) "을" else "를"
    fun wa(word: String) = word + if (has(word)) "과" else "와"
    fun e(word: String) = word + "에"
    fun ege(word: String) = word + "에게"

    /** 으로/로 (ㄹ 받침은 로) */
    fun ro(word: String): String {
        val f = final(word) ?: 0
        return word + if (f == 0 || f == 8) "로" else "으로"
    }
}
