package com.okdsfin.suningangho2.pixel

import com.okdsfin.suningangho2.core.Balance
import com.okdsfin.suningangho2.core.Enemy
import com.okdsfin.suningangho2.core.GameEvent
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.Num
import com.okdsfin.suningangho2.core.Regions
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 떠오르는 숫자 */
class Floater(val text: String, val x: Int, val y: Int, val color: Int, val scale: Int, var age: Double = 0.0)

/** 연출 상태: 전투 규칙과 상관없이 화면에만 쓰인다 */
class Fx {
    var time = 0.0
    var scroll = 0.0
    var heroSwing = 9.0
    var foeSwing = 9.0
    var foeFlash = 0.0
    var heroFlash = 0.0
    var dying: Enemy? = null
    var dyingAge = 0.0
    var shake = 0.0
    val floaters = mutableListOf<Floater>()

    fun update(dt: Double, before: GameState, s: GameState, events: List<GameEvent>) {
        time += dt
        heroSwing += dt
        foeSwing += dt
        foeFlash -= dt
        heroFlash -= dt
        shake -= dt
        dyingAge += dt
        floaters.forEach { it.age += dt }
        floaters.removeAll { it.age > FLOAT_LIFE }
        // 적이 다가오는 동안 무인이 걷는다: 배경이 흐른다
        val approaching = s.enemy.let { it == null || it.distance > 0 }
        if (approaching && s.wait <= 0) scroll += dt * WALK_SPEED
        val foe = s.enemy ?: before.enemy
        for (e in events) when (e) {
            is GameEvent.HeroHit -> {
                heroSwing = 0.0
                foeFlash = 0.07
                val big = foe?.boss == true
                floaters += Floater(Num.short(e.amount), foeX(foe) + if (big) 12 else 4, if (big) 14 else 30, if (e.crit) CRIT else WHITE, if (e.crit) 2 else 1)
            }
            is GameEvent.EnemyHit -> {
                foeSwing = 0.0
                heroFlash = 0.07
                floaters += Floater(Num.short(e.amount), HERO_X + 6, 30, RED, 1)
            }
            is GameEvent.Kill -> {
                dying = before.enemy
                dyingAge = 0.0
                floaters += Floater("+" + Num.short(e.silver), HERO_X + 22, 22, GOLD, 1)
                if (e.boss) shake = 0.3
            }
            GameEvent.HeroDown, GameEvent.BossTimeout -> shake = 0.25
            else -> Unit
        }
    }

    companion object {
        const val FLOAT_LIFE = 0.8
        const val WALK_SPEED = 28.0
    }
}

const val W = 120
const val H = 68
private const val GROUND = 52
const val HERO_X = 26
private val WHITE = 0xFFF4F0E6.toInt()
private val CRIT = 0xFFFFD84A.toInt()
private val RED = 0xFFE8574A.toInt()
private val GOLD = 0xFFE8C04A.toInt()

private fun foeX(e: Enemy?): Int {
    if (e == null) return 100
    val start = if (e.boss) 64 else 72
    return (start + e.distance * 60).roundToInt()
}

/** 지역 배경색 */
private class Palette(val skyTop: Long, val skyBottom: Long, val far: Long, val near: Long, val ground: Long, val grass: Long, val sun: Long)

private val palettes = listOf(
    Palette(0xFF6FA8DC, 0xFFF3D9B1, 0xFF8DA6B8, 0xFF5E8C5A, 0xFF6B4F35, 0xFF7FB05A, 0xFFFFF0C0),
    Palette(0xFF3A3560, 0xFFE08A5A, 0xFF5A4A6A, 0xFF3A3A40, 0xFF4A3A30, 0xFF5A6A40, 0xFFF0B060),
    Palette(0xFF88B4D0, 0xFFF0E0B0, 0xFFB08050, 0xFFA0502A, 0xFF5A3A24, 0xFFD08A30, 0xFFFFF0D0),
    Palette(0xFF120A18, 0xFF4A1A28, 0xFF2A1A2A, 0xFF1E1420, 0xFF241A1E, 0xFF5A2A2A, 0xFFC03040),
)

private fun lerpColor(a: Long, b: Long, t: Double): Int {
    fun ch(v: Long, shift: Int) = ((v shr shift) and 0xFF).toInt()
    fun mix(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).roundToInt()
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/** 한 장면을 그린다 */
fun renderScene(f: Frame, s: GameState, fx: Fx) {
    val pal = palettes[Regions.of(s.stage).palette % palettes.size]
    drawBackground(f, pal, fx.scroll)

    // 무인
    val walking = s.enemy.let { it == null || it.distance > 0 }
    val step = ((fx.time / 0.16).toInt() % 2 == 0)
    val (heroA, heroB) = Sprites.frames.getValue("hero")
    val lunge = if (fx.heroSwing < 0.12) 2 else 0
    val hx = HERO_X + lunge
    val hy = GROUND - 15
    f.blit(if (walking && step) heroB else heroA, hx, hy, tint = if (fx.heroFlash > 0) RED else null)
    drawBlade(f, "hero", hx, hy, flip = false, scale = 1, swing = fx.heroSwing, idleAngle = 55.0)
    if (fx.heroSwing < 0.14) drawSlash(f, hx + Sprites.HAND_X, hy + Sprites.HAND_Y, fx.heroSwing / 0.14)

    // 쓰러지는 적 (깜빡이며 사라진다)
    fx.dying?.let { d ->
        if (fx.dyingAge < 0.35 && (fx.dyingAge * 20).toInt() % 2 == 0) drawFoe(f, d, step = false, swing = 9.0, flash = true)
    }
    s.enemy?.let { e ->
        drawFoe(f, e, step = e.distance > 0 && step, swing = fx.foeSwing, flash = fx.foeFlash > 0)
        if (e.distance <= 0.0) drawBar(f, foeX(e) + if (e.boss) 4 else 1, if (e.boss) GROUND - 35 else GROUND - 19, if (e.boss) 24 else 14, e.hp / e.maxHp, RED)
    }

    // 위쪽 정보: 기혈, 보스 제한 시간
    drawBar(f, 3, 3, 30, s.hp() / Balance.maxHp(s), 0xFF5AC05A.toInt())
    if (s.enemy?.boss == true) {
        val left = (Balance.BOSS_TIME - s.bossTime) / Balance.BOSS_TIME
        drawBar(f, W / 2 - 25, 3, 50, left, CRIT)
        val t = (Balance.BOSS_TIME - s.bossTime).toInt().toString()
        f.text(t, W / 2 - Frame.textWidth(t) / 2, 8, CRIT)
    }

    for (fl in fx.floaters) {
        val rise = (fl.age / Fx.FLOAT_LIFE * 12).toInt()
        if (fl.age > Fx.FLOAT_LIFE * 0.75 && (fl.age * 30).toInt() % 2 == 0) continue
        f.text(fl.text, fl.x - Frame.textWidth(fl.text, fl.scale) / 2, fl.y - rise, fl.color, fl.scale)
    }
}

private fun drawFoe(f: Frame, e: Enemy, step: Boolean, swing: Double, flash: Boolean) {
    val (a, b) = Sprites.frames[e.sprite] ?: Sprites.frames.getValue("bandit")
    val scale = if (e.boss) 2 else 1
    val lunge = if (swing < 0.12) (if (e.sprite == "wolf") 5 else 2) * scale else 0
    val x = foeX(e) - lunge
    val y = GROUND + 1 - 16 * scale
    f.blit(if (step) b else a, x, y, flip = true, scale = scale, tint = if (flash) WHITE else null)
    if (Sprites.armed(e.sprite)) drawBlade(f, e.sprite, x, y, flip = true, scale = scale, swing = swing, idleAngle = 60.0)
}

/** 칼: 손에서 각도대로 뻗는 선. 휘두르는 동안 위에서 아래로 돈다 */
private fun drawBlade(f: Frame, who: String, x: Int, y: Int, flip: Boolean, scale: Int, swing: Double, idleAngle: Double) {
    val (blade, hilt) = Sprites.blade(who)
    val handX = x + (if (flip) 15 - Sprites.HAND_X else Sprites.HAND_X) * scale + scale / 2
    val handY = y + Sprites.HAND_Y * scale + scale / 2
    val p = swing / 0.12
    val deg = if (p < 1) -100 + 140 * p else idleAngle
    val rad = Math.toRadians(deg)
    val len = 9 * scale
    val dir = if (flip) -1 else 1
    val ex = handX + (cos(rad) * len * dir).roundToInt()
    val ey = handY + (sin(rad) * len).roundToInt()
    f.line(handX, handY, ex, ey, blade, from = 2 * scale, colorFrom = hilt)
    if (scale > 1) f.line(handX + dir, handY, ex + dir, ey, blade, from = 2 * scale, colorFrom = hilt)
}

/** 베는 궤적 */
private fun drawSlash(f: Frame, cx: Int, cy: Int, p: Double) {
    val color = if (p < 0.5) WHITE else 0xFFB8D0E0.toInt()
    var a = -70.0
    while (a <= 40) {
        val r = Math.toRadians(a)
        for (radius in 11..12) f.set(cx + (cos(r) * radius).roundToInt(), cy + (sin(r) * radius).roundToInt(), color)
        a += 6
    }
}

private fun drawBar(f: Frame, x: Int, y: Int, w: Int, ratio: Double, color: Int) {
    f.rect(x - 1, y - 1, w + 2, 4, Frame.SHADOW)
    f.rect(x, y, w, 2, 0xFF3A3040.toInt())
    f.rect(x, y, (w * ratio.coerceIn(0.0, 1.0)).roundToInt(), 2, color)
}

private fun drawBackground(f: Frame, pal: Palette, scroll: Double) {
    // 하늘: 띠마다 색을 나누고 경계는 바둑판으로 섞는다
    val bands = 7
    for (y in 0 until GROUND) {
        val t = y.toDouble() / GROUND * bands
        val band = t.toInt()
        val frac = t - band
        for (x in 0 until W) {
            val useNext = frac > 0.75 && (x + y) % 2 == 0
            f.px[y * W + x] = lerpColor(pal.skyTop, pal.skyBottom, (band + if (useNext) 1 else 0).toDouble() / bands)
        }
    }
    // 해 (혈월곡에서는 붉은 달)
    val sun = pal.sun.toInt()
    for (dy in -6..6) for (dx in -6..6) if (dx * dx + dy * dy <= 36) f.set(94 + dx, 14 + dy, sun)

    val far = pal.far.toInt()
    val near = pal.near.toInt()
    val farOff = scroll * 0.15
    val nearOff = scroll * 0.4
    for (x in 0 until W) {
        val fx = x + farOff
        val top = (24 + 6 * sin(fx * 0.05) + 3 * sin(fx * 0.14 + 1.3)).roundToInt()
        for (y in top until GROUND) f.px[y * W + x] = far
        val nx = x + nearOff
        val nTop = (39 + 3 * sin(nx * 0.08 + 2) + 2 * sin(nx * 0.2)).roundToInt()
        for (y in nTop until GROUND) f.px[y * W + x] = near
    }
    // 땅
    val ground = pal.ground.toInt()
    val grass = pal.grass.toInt()
    val dark = lerpColor(pal.ground, 0xFF000000, 0.35)
    val gOff = scroll.toInt()
    for (y in GROUND until H) for (x in 0 until W) {
        val wx = x + gOff
        f.px[y * W + x] = when {
            y == GROUND -> grass
            y == GROUND + 1 && wx % 3 == 0 -> grass
            (wx * 7 + y * 13) % 29 == 0 -> dark
            else -> ground
        }
    }
    for (x in 0 until W) if ((x + gOff) % 11 == 0) {
        f.set(x, GROUND - 1, grass)
        f.set(x + 1, GROUND - 2, grass)
    }
}
