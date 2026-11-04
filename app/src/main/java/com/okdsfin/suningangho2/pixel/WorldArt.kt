package com.okdsfin.suningangho2.pixel

import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.PlaceType
import com.okdsfin.suningangho2.core.WorldMap
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 도트 월드맵 (기획서 2장). 지형은 한 번만 만들어 두고, 화면에는 주인공을 따라 120×68만큼 잘라 보인다.
 */
object WorldArt {
    private const val MW = WorldMap.WIDTH
    private const val MH = WorldMap.HEIGHT

    private fun c(v: Long) = v.toInt()
    private val GRASS = c(0xFF6FA34E)
    private val GRASS2 = c(0xFF62954A)
    private val STEPPE = c(0xFFB8A860)
    private val STEPPE2 = c(0xFFA89A58)
    private val FOREST = c(0xFF2F6B3A)
    private val TREE = c(0xFF1F4A2A)
    private val HILL = c(0xFF8A9A58)
    private val ROCK = c(0xFF7A7470)
    private val ROCK_DARK = c(0xFF5A5450)
    private val SNOW = c(0xFFE8ECF0)
    private val WATER = c(0xFF3A6EA8)
    private val WATER_LIGHT = c(0xFF5A8EC8)
    private val SAND = c(0xFFD8C890)
    private val ROAD = c(0xFFC8A870)
    private val ROAD_DARK = c(0xFFA8885A)
    private val INK = c(0xFF1A1420)
    private val WALL = c(0xFFB0A898)
    private val WALL_DARK = c(0xFF7A7268)
    private val ROOF = c(0xFF8A3A2A)
    private val ROOF_BLUE = c(0xFF3A5A8A)
    private val HUT = c(0xFFA0784A)
    private val FLAG = c(0xFFC0392B)
    private val GOLD = c(0xFFE8C04A)

    private fun hash(x: Int, y: Int): Double {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647.0
    }

    /** 부드러운 값 잡음 */
    private fun noise(x: Double, y: Double): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        fun s(t: Double) = t * t * (3 - 2 * t)
        val a = hash(x0, y0)
        val b = hash(x0 + 1, y0)
        val c = hash(x0, y0 + 1)
        val d = hash(x0 + 1, y0 + 1)
        return (a + (b - a) * s(fx)) + ((c + (d - c) * s(fx)) - (a + (b - a) * s(fx))) * s(fy)
    }

    private fun fbm(x: Double, y: Double) = noise(x / 16, y / 16) * 0.6 + noise(x / 7, y / 7) * 0.3 + noise(x / 3, y / 3) * 0.1

    /** 강: 북쪽에서 남쪽으로 굽이친다 */
    private val river = listOf(96 to 0, 104 to 30, 110 to 55, 118 to 85, 122 to 108, 118 to 132, 121 to 160)

    private fun riverDistance(x: Int, y: Int): Double {
        var best = 99.0
        for ((p, q) in river.zipWithNext()) {
            val (x1, y1) = p
            val (x2, y2) = q
            val dx = (x2 - x1).toDouble()
            val dy = (y2 - y1).toDouble()
            val t = (((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(x - (x1 + dx * t), y - (y1 + dy * t)))
        }
        return best
    }

    private fun isSea(x: Int, y: Int): Boolean = x >= 234 + (sin(y * 0.3) * 2).roundToInt() || (x >= 206 && y >= 154 + (sin(x * 0.4)).roundToInt())

    /** 지형 (한 번만 계산) */
    val terrain: IntArray by lazy {
        val px = IntArray(MW * MH)
        val mountains = WorldMap.places.filter { it.type == PlaceType.SECT || it.id == "bukgwan" || it.id == "heukpung" }
        for (y in 0 until MH) for (x in 0 until MW) {
            val n = fbm(x.toDouble(), y.toDouble())
            var elev = n * 0.55 + (if (y < 40) (40 - y) / 40.0 * 0.35 else 0.0)
            for (m in mountains) {
                val d2 = ((x - m.x) * (x - m.x) + (y - m.y) * (y - m.y)).toDouble()
                elev += 0.45 * exp(-d2 / 260)
            }
            val r = riverDistance(x, y)
            val steppe = x > 176 && y < 52
            val color = when {
                isSea(x, y) -> if (hash(x, y) > 0.85) WATER_LIGHT else WATER
                isSea(x - 2, y) || isSea(x, y + 2) -> SAND
                r < 1.6 -> if ((x + y) % 5 == 0) WATER_LIGHT else WATER
                elev > 0.86 -> SNOW
                elev > 0.74 -> if (hash(x, y) > 0.6) ROCK_DARK else ROCK
                elev > 0.64 -> HILL
                !steppe && noise(x / 9.0 + 40, y / 9.0) > 0.62 -> if (hash(x, y) > 0.55) TREE else FOREST
                steppe -> if (hash(x, y) > 0.7) STEPPE2 else STEPPE
                else -> if (hash(x, y) > 0.75) GRASS2 else GRASS
            }
            px[y * MW + x] = color
        }
        // 길: 장소 사이를 흙빛 선으로
        for (road in WorldMap.roads) {
            val a = WorldMap.place(road.a)
            val b = WorldMap.place(road.b)
            line(px, a.x, a.y, b.x, b.y)
        }
        px
    }

    private fun line(px: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            if (x in 0 until MW && y in 0 until MH) px[y * MW + x] = if ((x + y) % 4 == 0) ROAD_DARK else ROAD
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** 카메라: 주인공을 가운데에 두되 지도 밖으로 나가지 않게 */
    fun camera(s: GameState): Pair<Int, Int> {
        val (hx, hy) = s.position()
        val cx = (hx - W / 2).roundToInt().coerceIn(0, MW - W)
        val cy = (hy - H / 2).roundToInt().coerceIn(0, MH - H)
        return cx to cy
    }

    fun render(f: Frame, s: GameState, time: Double) {
        val (cx, cy) = camera(s)
        val t = terrain
        for (y in 0 until H) for (x in 0 until W) f.px[y * W + x] = t[(y + cy) * MW + x + cx]
        // 물결 반짝임
        val shimmer = (time * 2).toInt()
        for (y in 0 until H) for (x in 0 until W) {
            if (f.px[y * W + x] == WATER && hash(x + cx + shimmer, y + cy) > 0.97) f.px[y * W + x] = WATER_LIGHT
        }
        for (p in WorldMap.places) {
            val sx = p.x - cx
            val sy = p.y - cy
            if (sx < -8 || sy < -8 || sx > W + 8 || sy > H + 8) continue
            drawPlace(f, p.type, sx, sy, visited = p.id in s.visited)
        }
        s.destination?.let { id ->
            val p = WorldMap.place(id)
            if ((time * 3).toInt() % 2 == 0) drawFlag(f, p.x - cx + 4, p.y - cy - 9)
        }
        val (hx, hy) = s.position()
        val walking = s.travel != null && s.recover <= 0 && s.fight == null
        drawHero(f, (hx - cx).roundToInt(), (hy - cy).roundToInt(), walking && (time * 4).toInt() % 2 == 0)
    }

    private fun drawPlace(f: Frame, type: PlaceType, x: Int, y: Int, visited: Boolean) {
        when (type) {
            PlaceType.VILLAGE -> {
                hut(f, x - 4, y - 2)
                hut(f, x + 1, y - 1)
            }
            PlaceType.TOWN -> {
                f.rect(x - 4, y - 3, 9, 6, INK)
                f.rect(x - 3, y - 2, 7, 4, WALL)
                f.rect(x - 2, y - 4, 5, 2, ROOF)
                f.rect(x - 1, y + 1, 1, 1, INK)
            }
            PlaceType.CITY -> {
                f.rect(x - 6, y - 4, 13, 9, INK)
                f.rect(x - 5, y - 3, 11, 7, WALL)
                f.rect(x - 5, y - 3, 11, 1, WALL_DARK)
                for (i in 0 until 5) f.set(x - 5 + i * 2, y - 5, WALL)
                f.rect(x - 3, y - 7, 7, 3, ROOF_BLUE)
                f.rect(x - 1, y + 1, 3, 3, INK)
            }
            PlaceType.CASTLE -> {
                f.rect(x - 3, y - 6, 7, 10, INK)
                f.rect(x - 2, y - 5, 5, 8, WALL_DARK)
                f.set(x - 2, y - 7, WALL_DARK)
                f.set(x, y - 7, WALL_DARK)
                f.set(x + 2, y - 7, WALL_DARK)
                f.line(x, y - 8, x, y - 12, INK)
                f.rect(x + 1, y - 12, 3, 2, FLAG)
            }
            PlaceType.SECT -> {
                f.rect(x - 4, y - 1, 9, 2, INK)
                f.rect(x - 3, y - 3, 7, 2, ROOF)
                f.rect(x - 2, y - 5, 5, 2, WALL)
                f.rect(x - 3, y - 6, 7, 1, ROOF)
                f.rect(x - 1, y - 8, 3, 2, WALL)
                f.set(x, y - 9, GOLD)
            }
        }
        if (!visited) f.set(x, y + 4, GOLD)
    }

    private fun hut(f: Frame, x: Int, y: Int) {
        f.rect(x, y, 4, 3, HUT)
        f.rect(x - 1, y - 1, 6, 1, ROOF)
        f.rect(x, y - 2, 4, 1, ROOF)
        f.set(x + 1, y + 2, INK)
    }

    private fun drawFlag(f: Frame, x: Int, y: Int) {
        f.line(x, y, x, y + 7, INK)
        f.rect(x + 1, y, 4, 3, GOLD)
    }

    /** 지도 위 작은 주인공 (5×7) */
    private val heroGrid = listOf(
        ".kkk.",
        "krrrk",
        "kssk.",
        "kwwwk",
        "kwwwk",
        ".k.k.",
        ".k.k.",
    )
    private val heroStep = listOf(
        ".kkk.",
        "krrrk",
        "kssk.",
        "kwwwk",
        "kwwwk",
        ".kk..",
        "k..k.",
    )

    private fun drawHero(f: Frame, x: Int, y: Int, step: Boolean) {
        val grid = if (step) heroStep else heroGrid
        for ((gy, row) in grid.withIndex()) for ((gx, ch) in row.withIndex()) {
            val color = when (ch) {
                'k' -> INK
                'r' -> FLAG
                's' -> c(0xFFF2C9A0)
                'w' -> c(0xFFE8E4D8)
                else -> continue
            }
            f.set(x - 2 + gx, y - 8 + gy, color)
        }
    }
}
