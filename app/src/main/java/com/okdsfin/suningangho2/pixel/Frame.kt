package com.okdsfin.suningangho2.pixel

import kotlin.math.abs

/** 가상 해상도의 픽셀 버퍼. 모든 장면은 여기에 찍은 뒤 정수배로 확대해 그린다 */
class Frame(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun clear(color: Int) = px.fill(color)

    fun set(x: Int, y: Int, color: Int) {
        if (x < 0 || y < 0 || x >= w || y >= h || color ushr 24 == 0) return
        px[y * w + x] = color
    }

    /** 그 자리 색을 어둡게 (그림자) */
    fun shade(x: Int, y: Int, amount: Double = 0.4) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        val c = px[y * w + x]
        fun ch(shift: Int) = (((c shr shift) and 0xFF) * (1 - amount)).toInt()
        px[y * w + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun rect(x: Int, y: Int, rw: Int, rh: Int, color: Int) {
        for (yy in y until y + rh) for (xx in x until x + rw) set(xx, yy, color)
    }

    /** 스프라이트를 찍는다. [flip]이면 좌우 반전, [scale]은 정수배, [tint]가 있으면 그 색 하나로 (피격 번쩍임) */
    fun blit(s: Sprite, x: Int, y: Int, flip: Boolean = false, scale: Int = 1, tint: Int? = null) {
        for (sy in 0 until s.h) for (sx in 0 until s.w) {
            val c = s.px[sy * s.w + sx]
            if (c ushr 24 == 0) continue
            val dx = if (flip) s.w - 1 - sx else sx
            val color = tint ?: c
            for (oy in 0 until scale) for (ox in 0 until scale) set(x + dx * scale + ox, y + sy * scale + oy, color)
        }
    }

    /** 브레젠험 직선 */
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, from: Int = 0, colorFrom: Int = color) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var i = 0
        while (true) {
            set(x, y, if (i < from) colorFrom else color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
            i++
        }
    }

    fun text(str: String, x: Int, y: Int, color: Int, scale: Int = 1, shadow: Int? = SHADOW) {
        var cx = x
        for (ch in str) {
            val g = PixelFont.glyph(ch)
            if (g != null) {
                for (gy in 0 until 5) for (gx in 0 until 3) if (g[gy][gx] == '#') {
                    for (oy in 0 until scale) for (ox in 0 until scale) {
                        shadow?.let { set(cx + gx * scale + ox + 1, y + gy * scale + oy + 1, it) }
                        set(cx + gx * scale + ox, y + gy * scale + oy, color)
                    }
                }
            }
            cx += 4 * scale
        }
    }

    companion object {
        const val SHADOW = 0xFF1A1420.toInt()
        fun textWidth(str: String, scale: Int = 1) = str.length * 4 * scale - scale
    }
}

/** 3×5 도트 숫자 */
object PixelFont {
    private val glyphs: Map<Char, List<String>> = mapOf(
        '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
        '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
        '2' to listOf("###", "..#", "###", "#..", "###"),
        '3' to listOf("###", "..#", ".##", "..#", "###"),
        '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
        '5' to listOf("###", "#..", "###", "..#", "###"),
        '6' to listOf("###", "#..", "###", "#.#", "###"),
        '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
        '8' to listOf("###", "#.#", "###", "#.#", "###"),
        '9' to listOf("###", "#.#", "###", "..#", "###"),
        'K' to listOf("#.#", "##.", "#..", "##.", "#.#"),
        'M' to listOf("#.#", "###", "###", "#.#", "#.#"),
        'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
        'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
        '.' to listOf("...", "...", "...", "...", ".#."),
        '+' to listOf("...", ".#.", "###", ".#.", "..."),
        '-' to listOf("...", "...", "###", "...", "..."),
        '/' to listOf("..#", "..#", ".#.", "#..", "#.."),
        '!' to listOf(".#.", ".#.", ".#.", "...", ".#."),
    )

    fun glyph(c: Char): List<String>? = glyphs[c]
}
