package com.okdsfin.suningangho2.pixel

import com.okdsfin.suningangho2.core.Agent
import com.okdsfin.suningangho2.core.AgentKind
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.Incident
import com.okdsfin.suningangho2.core.IncidentKind
import com.okdsfin.suningangho2.core.Life
import com.okdsfin.suningangho2.core.PlaceType
import com.okdsfin.suningangho2.core.WorldMap
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 도트 월드맵 (기획서 2장). 지형은 한 번만 만들어 두고, 화면 크기만큼 잘라 보인다.
 * 그 위에 오가는 사람들([Life])과 지도 위 사건([Incident])을 실시간으로 그린다.
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
    val DEEP = c(0xFF2A5A90)
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
    private val RED = c(0xFFC0392B)
    private val GOLD = c(0xFFE8C04A)
    private val SKIN = c(0xFFF2C9A0)
    private val WHITE = c(0xFFF4F0E6)
    private val ORANGE = c(0xFFE8892A)
    private val YELLOW = c(0xFFFFD84A)
    private val SMOKE = c(0xFF9A9498)
    private val BLUE = c(0xFF4A7AB8)

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
        val cc = hash(x0, y0 + 1)
        val d = hash(x0 + 1, y0 + 1)
        val top = a + (b - a) * s(fx)
        val bottom = cc + (d - cc) * s(fx)
        return top + (bottom - top) * s(fy)
    }

    private fun fbm(x: Double, y: Double) = noise(x / 16, y / 16) * 0.6 + noise(x / 7, y / 7) * 0.3 + noise(x / 3, y / 3) * 0.1

    /** 강: 북쪽 산에서 남쪽 바다로 */
    private val river = listOf(112 to 0, 120 to 60, 108 to 120, 128 to 180, 126 to 250, 132 to 300, 126 to 384)

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

    private fun isSea(x: Int, y: Int): Boolean =
        y >= 371 + (sin(x * 0.25) * 2).roundToInt() || (x >= 246 + (sin(y * 0.3) * 2).roundToInt() && y >= 292)

    /** 지형 (한 번만 계산) */
    val terrain: IntArray by lazy {
        val px = IntArray(MW * MH)
        val peaks = WorldMap.places.filter { it.type == PlaceType.SECT || it.id in setOf("bukgwan", "heukpung", "hanbing") }
        for (y in 0 until MH) for (x in 0 until MW) {
            var elev = fbm(x.toDouble(), y.toDouble()) * 0.55 + (if (y < 110) (110 - y) / 110.0 * 0.35 else 0.0)
            for (m in peaks) {
                val d2 = ((x - m.x) * (x - m.x) + (y - m.y) * (y - m.y)).toDouble()
                elev += 0.45 * exp(-d2 / 260)
            }
            val r = riverDistance(x, y)
            val steppe = x > 160 && y < 70
            px[y * MW + x] = when {
                isSea(x, y) -> if (hash(x, y) > 0.85) WATER_LIGHT else WATER
                isSea(x + 2, y) || isSea(x, y + 2) -> SAND
                r < 1.6 -> if ((x + y) % 5 == 0) WATER_LIGHT else WATER
                elev > 0.86 -> SNOW
                elev > 0.74 -> if (hash(x, y) > 0.6) ROCK_DARK else ROCK
                elev > 0.64 -> HILL
                !steppe && noise(x / 9.0 + 40, y / 9.0) > 0.62 -> if (hash(x, y) > 0.55) TREE else FOREST
                steppe -> if (hash(x, y) > 0.7) STEPPE2 else STEPPE
                else -> if (hash(x, y) > 0.75) GRASS2 else GRASS
            }
        }
        for (road in WorldMap.roads) {
            val a = WorldMap.place(road.a)
            val b = WorldMap.place(road.b)
            roadLine(px, a.x, a.y, b.x, b.y)
        }
        px
    }

    private fun roadLine(px: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
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

    /** 주인공을 화면 가운데에 두는 카메라 (지도보다 화면이 크면 지도를 가운데에) */
    fun follow(s: GameState, w: Int, h: Int): Pair<Double, Double> {
        val (hx, hy) = s.position()
        return clamp(hx - w / 2.0, hy - h / 2.0, w, h)
    }

    fun clamp(x: Double, y: Double, w: Int, h: Int): Pair<Double, Double> {
        val cx = if (w >= MW) (MW - w) / 2.0 else x.coerceIn(0.0, (MW - w).toDouble())
        val cy = if (h >= MH) (MH - h) / 2.0 else y.coerceIn(0.0, (MH - h).toDouble())
        return cx to cy
    }

    fun render(f: Frame, s: GameState, life: Life, time: Double, camX: Double, camY: Double) {
        val cx = camX.roundToInt()
        val cy = camY.roundToInt()
        val t = terrain
        val shimmer = (time * 2).toInt()
        for (y in 0 until f.h) {
            val wy = y + cy
            for (x in 0 until f.w) {
                val wx = x + cx
                var color = if (wx in 0 until MW && wy in 0 until MH) t[wy * MW + wx] else DEEP
                if ((color == WATER || color == DEEP) && hash(wx + shimmer, wy) > 0.97) color = WATER_LIGHT
                f.px[y * f.w + x] = color
            }
        }
        for (p in WorldMap.places) {
            val sx = p.x - cx
            val sy = p.y - cy
            if (sx < -10 || sy < -12 || sx > f.w + 10 || sy > f.h + 10) continue
            drawPlace(f, p.type, sx, sy, visited = p.id in s.visited)
        }
        for (inc in s.incidents) {
            val (ix, iy) = inc.position()
            drawIncident(f, inc, (ix - cx).roundToInt(), (iy - cy).roundToInt(), time)
        }
        for (a in life.agents) {
            val (ax, ay) = life.position(a) ?: continue
            drawAgent(f, a, (ax - cx).roundToInt(), (ay - cy).roundToInt(), life.facingRight(a), time)
        }
        s.destination?.let { id ->
            val p = WorldMap.place(id)
            if ((time * 3).toInt() % 2 == 0) drawFlag(f, p.x - cx + 5, p.y - cy - 12)
        }
        val (hx, hy) = s.position()
        val walking = s.travel != null && s.recover <= 0 && s.fight == null
        drawHero(f, (hx - cx).roundToInt(), (hy - cy).roundToInt(), walking && (time * 4).toInt() % 2 == 0, time)
    }

    // ── 장소 ──

    private fun drawPlace(f: Frame, type: PlaceType, x: Int, y: Int, visited: Boolean) {
        when (type) {
            PlaceType.VILLAGE -> {
                hut(f, x - 5, y - 2)
                hut(f, x + 1, y - 1)
                hut(f, x - 2, y + 2)
            }
            PlaceType.TOWN -> {
                f.rect(x - 5, y - 3, 11, 7, INK)
                f.rect(x - 4, y - 2, 9, 5, WALL)
                f.rect(x - 3, y - 5, 7, 3, ROOF)
                f.rect(x - 1, y + 1, 2, 2, INK)
            }
            PlaceType.CITY -> {
                f.rect(x - 8, y - 5, 17, 11, INK)
                f.rect(x - 7, y - 4, 15, 9, WALL)
                f.rect(x - 7, y - 4, 15, 1, WALL_DARK)
                for (i in 0 until 8) f.set(x - 7 + i * 2, y - 6, WALL)
                f.rect(x - 4, y - 9, 9, 4, ROOF_BLUE)
                f.rect(x - 2, y - 11, 5, 2, ROOF_BLUE)
                f.rect(x - 1, y + 2, 3, 3, INK)
            }
            PlaceType.CASTLE -> {
                f.rect(x - 4, y - 7, 9, 12, INK)
                f.rect(x - 3, y - 6, 7, 10, WALL_DARK)
                for (i in 0 until 4) f.set(x - 3 + i * 2, y - 8, WALL_DARK)
                f.line(x, y - 9, x, y - 14, INK)
                f.rect(x + 1, y - 14, 4, 2, RED)
                f.rect(x - 1, y + 1, 2, 3, INK)
            }
            PlaceType.SECT -> {
                f.rect(x - 5, y - 1, 11, 2, INK)
                f.rect(x - 4, y - 3, 9, 2, ROOF)
                f.rect(x - 3, y - 5, 7, 2, WALL)
                f.rect(x - 4, y - 6, 9, 1, ROOF)
                f.rect(x - 2, y - 8, 5, 2, WALL)
                f.rect(x - 3, y - 9, 7, 1, ROOF)
                f.set(x, y - 10, GOLD)
            }
        }
        if (!visited) f.set(x, y + 6, GOLD)
    }

    private fun hut(f: Frame, x: Int, y: Int) {
        f.rect(x, y, 4, 3, HUT)
        f.rect(x - 1, y - 1, 6, 1, ROOF)
        f.rect(x, y - 2, 4, 1, ROOF)
        f.set(x + 1, y + 2, INK)
    }

    private fun drawFlag(f: Frame, x: Int, y: Int) {
        f.line(x, y, x, y + 8, INK)
        f.rect(x + 1, y, 5, 3, GOLD)
    }

    // ── 사건 ──

    private fun drawIncident(f: Frame, inc: Incident, x: Int, y: Int, time: Double) {
        val blink = (time * 6).toInt() % 2 == 0
        val seed = inc.id * 7
        when (inc.kind) {
            IncidentKind.RAID -> {
                person(f, x - 4, y, RED, INK, step = blink)
                person(f, x + 3, y, c(0xFF8A6A44), INK, step = !blink, flip = true)
                f.rect(x + 5, y - 1, 4, 3, HUT)
                if (blink) spark(f, x, y - 4, YELLOW) else spark(f, x, y - 4, WHITE)
            }
            IncidentKind.BEAST -> {
                for (i in 0 until 3) {
                    val wx = x - 5 + i * 5 + if ((time * 3 + i).toInt() % 2 == 0) 1 else 0
                    f.rect(wx, y - 1, 4, 2, c(0xFF8A8F98))
                    f.set(wx + 3, y - 2, c(0xFF8A8F98))
                    f.set(wx, y + 1, INK)
                    f.set(wx + 3, y + 1, INK)
                    if (blink) f.set(wx + 3, y - 1, RED)
                }
            }
            IncidentKind.PATROL -> {
                for (i in 0 until 4) {
                    val px = x - 7 + i * 4 + ((time * 2).toInt() % 3)
                    person(f, px, y, BLUE, INK, step = (time * 4 + i).toInt() % 2 == 0)
                    f.line(px + 2, y - 6, px + 2, y - 2, WHITE)
                }
                f.line(x - 9, y - 9, x - 9, y - 2, INK)
                f.rect(x - 8, y - 9, 3, 2, BLUE)
            }
            IncidentKind.FESTIVAL -> {
                for (i in 0 until 7) {
                    val a = i * 0.9 + time * 0.8
                    val lx = x + (cos(a) * 9).roundToInt()
                    val ly = y - 2 + (sin(a) * 5).roundToInt() + if ((time * 2 + i).toInt() % 2 == 0) 0 else -1
                    f.set(lx, ly, if (i % 2 == 0) RED else YELLOW)
                    f.set(lx, ly + 1, ORANGE)
                }
            }
            IncidentKind.MARKET -> {
                val colors = listOf(RED, BLUE, YELLOW, c(0xFF5AC05A))
                for (i in 0 until 4) f.rect(x - 9 + i * 5, y + 5, 4, 1, colors[i])
                for (i in 0 until 9) {
                    val jitter = ((time * 3 + i * 1.7).toInt() % 3) - 1
                    f.set(x - 9 + i * 2 + jitter, y + 7 + (i % 2), if (i % 3 == 0) SKIN else colors[(i + seed) % 4])
                }
            }
            IncidentKind.TOURNAMENT -> {
                f.rect(x - 7, y + 6, 15, 1, WHITE)
                f.rect(x - 7, y + 6, 1, 3, WHITE)
                f.rect(x + 7, y + 6, 1, 3, WHITE)
                person(f, x - 4, y + 11, RED, INK, step = blink)
                person(f, x + 3, y + 11, BLUE, INK, step = !blink, flip = true)
                if (blink) spark(f, x, y + 5, WHITE)
                f.line(x + 10, y - 14, x + 10, y - 4, INK)
                f.rect(x + 11, y - 14 + if (blink) 0 else 1, 5, 3, RED)
            }
            IncidentKind.FIRE -> {
                for (i in 0 until 10) {
                    val fx = x - 5 + ((i * 37 + seed) % 11)
                    val h = ((time * 8 + i * 1.3).toInt() % 3)
                    f.set(fx, y - 3 - h, if (h == 0) YELLOW else ORANGE)
                    f.set(fx, y - 2 - h, RED)
                }
                for (i in 0 until 6) {
                    val age = (time * 0.7 + i / 6.0) % 1.0
                    f.set(x - 2 + ((i * 3) % 5) + (age * 3).toInt(), y - 6 - (age * 12).toInt(), SMOKE)
                }
            }
            IncidentKind.MASTER -> {
                val r = 7 + ((time * 2).toInt() % 3)
                var a = 0.0
                while (a < 6.28) {
                    f.set(x + (cos(a) * r).roundToInt(), y - 2 + (sin(a) * r * 0.6).roundToInt(), if (blink) GOLD else WHITE)
                    a += 0.35
                }
                person(f, x + 9, y - 1, WHITE, INK, step = false)
            }
        }
    }

    private fun spark(f: Frame, x: Int, y: Int, color: Int) {
        f.set(x, y, color)
        f.set(x - 1, y, color)
        f.set(x + 1, y, color)
        f.set(x, y - 1, color)
        f.set(x, y + 1, color)
        f.set(x - 2, y - 2, color)
        f.set(x + 2, y + 2, color)
        f.set(x + 2, y - 2, color)
        f.set(x - 2, y + 2, color)
    }

    // ── 사람들 ──

    /** 3×5 사람: 머리, 몸, 다리 (발이 (x, y)) */
    private fun person(f: Frame, x: Int, y: Int, body: Int, head: Int, step: Boolean, flip: Boolean = false, hat: Int? = null) {
        f.set(x + 1, y - 5, hat ?: head)
        f.set(x + 1, y - 4, SKIN)
        f.rect(x, y - 3, 3, 2, body)
        if (step) {
            f.set(x, y - 1, INK)
            f.set(x + 2, y - 1, INK)
        } else {
            f.set(x + 1, y - 1, INK)
        }
        if (hat != null) {
            f.set(x, y - 5, hat)
            f.set(x + 2, y - 5, hat)
        }
        if (flip) Unit
    }

    private fun drawAgent(f: Frame, a: Agent, x: Int, y: Int, right: Boolean, time: Double) {
        if (x < -6 || y < -6 || x > f.w + 6 || y > f.h + 6) return
        val step = (time * 4 + a.id).toInt() % 2 == 0
        val dir = if (right) 1 else -1
        when (a.kind) {
            AgentKind.MERCHANT -> {
                f.rect(x - dir * 5 - 2, y - 3, 4, 3, HUT)
                f.set(x - dir * 5 - 1, y, INK)
                f.set(x - dir * 5 + 1, y, INK)
                person(f, x - 1, y, c(0xFF7A5A3A), INK, step)
            }
            AgentKind.SOLDIER -> {
                person(f, x - 1, y, BLUE, INK, step, hat = c(0xFF2A3A5A))
                f.line(x + dir * 2, y - 7, x + dir * 2, y - 2, WHITE)
            }
            AgentKind.BANDIT -> {
                person(f, x - 3, y, c(0xFF5A3F28), RED, step)
                person(f, x + 1, y, c(0xFF5A3F28), RED, !step)
            }
            AgentKind.MONK -> person(f, x - 1, y, ORANGE, SKIN, step)
            AgentKind.WANDERER -> person(f, x - 1, y, c(0xFF8A8A9A), INK, step, hat = c(0xFFB8A070))
            AgentKind.COURIER -> {
                f.rect(x - 3, y - 3, 6, 2, c(0xFF7A4A2A))
                f.set(x + dir * 3, y - 4, c(0xFF7A4A2A))
                f.set(x - 2, y - 1, INK)
                f.set(x + 2, y - 1, INK)
                if (step) {
                    f.set(x - 3, y - 1, INK)
                    f.set(x + 1, y - 1, INK)
                }
                f.set(x, y - 4, RED)
                f.set(x, y - 5, SKIN)
            }
            AgentKind.FOLK -> {
                val colors = listOf(c(0xFF9A6A4A), c(0xFF6A7A9A), c(0xFFB0A070), c(0xFF8A5A6A))
                f.set(x, y - 3, SKIN)
                f.rect(x - 1, y - 2, 2, 2, colors[a.id % colors.size])
            }
        }
    }

    /** 지도 위 주인공: 다른 사람보다 크게, 발밑에 금빛 표시 */
    private val heroGrid = listOf(
        ".kkk.",
        "krrrk",
        "kssk.",
        "kwwwk",
        "kwbwk",
        ".k.k.",
        ".k.k.",
    )
    private val heroStep = listOf(
        ".kkk.",
        "krrrk",
        "kssk.",
        "kwwwk",
        "kwbwk",
        ".kk..",
        "k..k.",
    )

    private fun drawHero(f: Frame, x: Int, y: Int, step: Boolean, time: Double) {
        if ((time * 2).toInt() % 2 == 0) {
            f.set(x - 3, y, GOLD)
            f.set(x + 3, y, GOLD)
        }
        for (i in -2..2) f.shade(x + i, y + 1)
        val grid = if (step) heroStep else heroGrid
        for ((gy, row) in grid.withIndex()) for ((gx, ch) in row.withIndex()) {
            val color = when (ch) {
                'k' -> INK
                'r' -> RED
                's' -> SKIN
                'w' -> WHITE
                'b' -> GOLD
                else -> continue
            }
            f.set(x - 2 + gx, y - 7 + gy, color)
        }
    }
}
