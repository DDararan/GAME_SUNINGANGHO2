package com.okdsfin.suningangho2.pixel

/**
 * 도트 스프라이트: 문자 격자 + 팔레트. 같은 몸 격자에 팔레트만 바꿔 여러 인물을 만든다.
 * 나중에 직접 그린 PNG로 바꿀 때는 [Sprite]만 같은 이름으로 내주면 된다.
 */
class Sprite(val w: Int, val h: Int, val px: IntArray)

object Sprites {
    private const val CLEAR = 0

    private fun c(hex: Long): Int = hex.toInt()

    /** 모든 인물이 공유하는 색 */
    private val base = mapOf(
        'k' to c(0xFF1A1420),
        's' to c(0xFFF2C9A0),
        'S' to c(0xFFD9A67C),
        'p' to c(0xFF3A3A4A),
        'b' to c(0xFF4A3424),
    )

    // 무인 몸 (오른쪽을 본다). 손은 (12, 9)
    private val bodyA = listOf(
        "................",
        "......kkkk......",
        ".....kxxxxk.....",
        "....khhhhhhk....",
        "....khhsssSk....",
        "....khssksSk....",
        ".....kssssk.....",
        "....kwrrrrwk....",
        "...krwrrrrRrk...",
        "...krrbbbbrrsk..",
        "...kRrrrrrrRk...",
        "....kRrrrrRk....",
        "....kpk..kpk....",
        "....kpk..kpk....",
        "....kkk..kkk....",
        "................",
    )
    private val bodyB = bodyA.take(12) + listOf(
        "...kpk....kpk...",
        "..kpk......kpk..",
        "..kkk......kkk..",
        "................",
    )

    // 늑대 (오른쪽을 본다)
    private val wolfA = listOf(
        "................",
        "................",
        "................",
        "................",
        "...........k.k..",
        "..........kgkgk.",
        "..........kgggk.",
        "k........kggegkk",
        "kgk......kggggGk",
        ".kgkkkkkkggggkk.",
        "..kgggggggggk...",
        "..kGgggggggGk...",
        "..kgk.kgk.kgk...",
        "..kgk.kk..kgk...",
        "..kk......kk....",
        "................",
    )
    private val wolfB = wolfA.take(12) + listOf(
        "..kgk..kgkkgk...",
        ".kgk...kk.kgk...",
        ".kk.......kk....",
        "................",
    )

    /** 인물 팔레트: 머리띠·머리·옷·옷 그늘·옷깃 */
    private data class Look(val band: Long, val hair: Long, val robe: Long, val shade: Long, val trim: Long)

    private val looks = mapOf(
        "hero" to Look(0xFFC0392B, 0xFF2B2233, 0xFFE8E4D8, 0xFFB8B2A4, 0xFF4A7AB8),
        "bandit" to Look(0xFF5A3F28, 0xFF2B2233, 0xFF8A6A44, 0xFF5E4630, 0xFFA88A5C),
        "cultist" to Look(0xFF8A2A3A, 0xFF241628, 0xFF3A2440, 0xFF241628, 0xFF8A2A3A),
        "chief" to Look(0xFF2B2233, 0xFF2B2233, 0xFF8A2A20, 0xFF5E1C16, 0xFFC9A227),
        "elder" to Look(0xFFC9A227, 0xFFDCDCDC, 0xFF2A1A30, 0xFF160E1A, 0xFFC9A227),
    )

    private fun build(grid: List<String>, colors: Map<Char, Int>): Sprite {
        val h = grid.size
        val w = grid[0].length
        require(grid.all { it.length == w }) { "격자 폭이 다르다" }
        val px = IntArray(w * h) { i -> colors[grid[i / w][i % w]] ?: CLEAR }
        return Sprite(w, h, px)
    }

    private fun person(look: Look, grid: List<String>) = build(
        grid,
        base + mapOf('x' to c(look.band), 'h' to c(look.hair), 'r' to c(look.robe), 'R' to c(look.shade), 'w' to c(look.trim)),
    )

    private val wolfColors = base + mapOf('g' to c(0xFF8A8F98), 'G' to c(0xFFC4C8CE), 'e' to c(0xFFE0B020))

    /** 이름 → (걷기 1, 걷기 2) */
    val frames: Map<String, Pair<Sprite, Sprite>> = looks.mapValues { (_, look) -> person(look, bodyA) to person(look, bodyB) } +
        ("wolf" to (build(wolfA, wolfColors) to build(wolfB, wolfColors)))

    /** 무기 색 (칼날, 손잡이) */
    fun blade(name: String): Pair<Int, Int> = when (name) {
        "hero" -> c(0xFFE6EEF2) to c(0xFFC9A227)
        "elder" -> c(0xFFB04060) to c(0xFFC9A227)
        else -> c(0xFFA0A4A8) to c(0xFF5A3F28)
    }

    /** 무기를 드는 인물인가 (늑대는 문다) */
    fun armed(name: String): Boolean = name != "wolf"

    const val HAND_X = 12
    const val HAND_Y = 9
}
