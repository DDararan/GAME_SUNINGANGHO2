package com.okdsfin.suningangho2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okdsfin.suningangho2.GameViewModel
import com.okdsfin.suningangho2.core.Calendar
import com.okdsfin.suningangho2.core.Game
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.Num
import com.okdsfin.suningangho2.core.Realms
import com.okdsfin.suningangho2.core.RoadEvents
import com.okdsfin.suningangho2.core.Stat
import com.okdsfin.suningangho2.core.WorldMap
import com.okdsfin.suningangho2.pixel.Frame
import com.okdsfin.suningangho2.pixel.H
import com.okdsfin.suningangho2.pixel.W
import com.okdsfin.suningangho2.pixel.WorldArt
import com.okdsfin.suningangho2.pixel.battlePalette
import com.okdsfin.suningangho2.pixel.renderBattle

val Ink = Color(0xFF16131C)
val PanelBg = Color(0xFF241E2C)
val Edge = Color(0xFF4A3F58)
val Paper = Color(0xFFF4F0E6)
val Dim = Color(0xFFA89FB4)
val GoldC = Color(0xFFE8C04A)
val Jade = Color(0xFF5AC05A)
val Blood = Color(0xFFE8574A)

@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GoldC, onPrimary = Ink, background = Ink, surface = PanelBg, onSurface = Paper, onBackground = Paper,
            secondary = Jade, outline = Edge,
        ),
        content = content,
    )
}

@Composable
fun GameScreen(game: GameViewModel) {
    val s = game.ui
    if (s == null) {
        StartScreen(game)
        return
    }
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        TopBar(s)
        PixelView(game)
        StatusBar(s, game)
        Panels(s, game)
    }
    game.away?.let { r ->
        AlertDialog(
            onDismissRequest = game::dismissAway,
            confirmButton = { Button(onClick = game::dismissAway, shape = RectangleShape) { Text("알겠다") } },
            title = { Text("자리를 비운 ${r.days.toInt()}일 동안") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (r.entries.isEmpty()) Text("조용히 지나갔다.")
                    r.entries.takeLast(30).forEach { Text("${Calendar.text(it.day.toDouble()).removePrefix("강호력 ")} · ${it.text}", fontSize = 13.sp) }
                }
            },
            shape = RectangleShape,
            containerColor = PanelBg,
        )
    }
    if (!s.hero.alive) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { Button(onClick = { game.newGame("") }, shape = RectangleShape) { Text("새 생을 시작한다") } },
            title = { Text("강호를 떠나다") },
            text = { Text("${s.hero.name}, ${s.ageYears}세. ${Realms.name(s.hero.realm)}. ${s.visited.size}곳을 떠돌았다.") },
            shape = RectangleShape,
            containerColor = PanelBg,
        )
    }
}

@Composable
private fun StartScreen(game: GameViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Ink).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("강호 유랑", color = GoldC, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            "열다섯, 청운촌을 나선다. 현실의 하루가 강호의 1년이다. 목적지를 정하지 않으면 발길 닿는 대로 떠돌고, 지도에서 장소를 누르면 그곳으로 간다.",
            color = Paper,
        )
        OutlinedTextField(value = name, onValueChange = { name = it.take(8) }, label = { Text("이름") }, singleLine = true)
        Button(onClick = { game.newGame(name) }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("길을 나선다") }
    }
}

@Composable
private fun TopBar(s: GameState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${s.hero.name} · ${s.ageYears}세 · ${Realms.name(s.hero.realm)}", color = Paper, fontWeight = FontWeight.Bold)
            Text(Calendar.text(s.day), color = Dim, fontSize = 13.sp)
        }
        Text("은자 ${Num.kr(s.hero.silver.toDouble())}", color = GoldC, fontWeight = FontWeight.Bold)
    }
}

/** 도트 화면: 평소에는 지도, 싸움이 나면 싸움터. 가상 화면을 정수배로만 키운다 */
@Composable
private fun PixelView(game: GameViewModel) {
    val frame = remember { Frame(W, H) }
    val bitmap = remember { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    val measurer = rememberTextMeasurer()
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.frame((now - last) / 1e9)
                last = now
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = maxOf(1, (widthPx / W).toInt())
        val heightDp = with(density) { (H * scale).toDp() }
        val labelStyle = TextStyle(color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black, Offset(2f, 2f), 0f))
        Canvas(
            Modifier.fillMaxWidth().height(heightDp).pointerInput(scale) {
                detectTapGestures { pos ->
                    val s = game.state ?: return@detectTapGestures
                    if (s.fight != null) return@detectTapGestures
                    val left = (size.width - W * scale) / 2
                    val (cx, cy) = WorldArt.camera(s)
                    game.tapWorld((pos.x - left) / scale.toDouble() + cx, pos.y / scale.toDouble() + cy)
                }
            },
        ) {
            @Suppress("UNUSED_EXPRESSION") game.frameCount
            val s = game.state ?: return@Canvas
            val fight = s.fight
            if (fight != null) {
                val danger = s.travel?.let { WorldMap.roadDanger(it.from, it.to) } ?: 1
                renderBattle(frame, fight, game.fx, battlePalette(danger))
            } else {
                WorldArt.render(frame, s, game.fx.time)
            }
            bitmap.setPixels(frame.px, 0, W, 0, 0, W, H)
            val shakeX = if (game.fx.shake > 0) ((game.fx.time * 60).toInt() % 3 - 1) * scale else 0
            val left = ((size.width - W * scale) / 2).toInt()
            drawImage(image, dstOffset = IntOffset(left + shakeX, 0), dstSize = IntSize(W * scale, H * scale), filterQuality = FilterQuality.None)
            if (fight == null) {
                // 장소 이름 (지도 위)
                val (cx, cy) = WorldArt.camera(s)
                for (p in WorldMap.places) {
                    val sx = p.x - cx
                    val sy = p.y - cy
                    if (sx < 0 || sy < 0 || sx >= W || sy >= H) continue
                    val layout = measurer.measure(p.name, labelStyle)
                    drawText(layout, topLeft = Offset(left + sx * scale - layout.size.width / 2f, (sy + 5) * scale.toFloat()))
                }
            } else {
                val layout = measurer.measure(fight.enemy.name, labelStyle)
                drawText(layout, topLeft = Offset(left + (W - 4) * scale - layout.size.width.toFloat(), 2f * scale))
            }
        }
    }
}

/** 지금 무엇을 하고 있는가 */
private fun doing(s: GameState): String {
    val t = s.travel
    return when {
        s.fight != null -> "싸우는 중!"
        s.recover > 0 -> "상처를 돌보는 중 (${"%.1f".format(s.recover)}일)"
        t != null -> "${com.okdsfin.suningangho2.core.Josa.ro(WorldMap.place(t.to).name)} 가는 길 · ${t.progress.toInt() + 1}/${t.total.toInt()}일째"
        s.hold -> "${WorldMap.place(s.at!!).name}에 머무는 중"
        else -> "${WorldMap.place(s.at!!).name}에 머무는 중 · ${"%.1f".format(s.stay.coerceAtLeast(0.0))}일 뒤 떠난다"
    }
}

@Composable
private fun StatusBar(s: GameState, game: GameViewModel) {
    Row(Modifier.fillMaxWidth().background(PanelBg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(doing(s), color = Paper, fontWeight = FontWeight.Bold)
            val dest = s.destination
            Text(
                when {
                    dest != null -> "목적지: ${WorldMap.place(dest).name} (${(WorldMap.pathDays(WorldMap.path(s.at ?: s.travel!!.to, dest)) + (s.travel?.let { it.total - it.progress } ?: 0.0)).toInt()}일 남짓)"
                    s.hold -> "목적지에 닿았다. 지도에서 다른 곳을 누르거나 다시 떠돌 수 있다."
                    else -> "발길 닿는 대로 떠도는 중. 지도에서 장소를 누르면 목적지가 된다."
                },
                color = Dim,
                fontSize = 13.sp,
            )
        }
        if (s.destination != null || s.hold) {
            OutlinedButton(onClick = game::wander, shape = RectangleShape, border = BorderStroke(1.dp, Edge)) { Text("떠돌기", color = Paper) }
        }
    }
}

@Composable
private fun Panels(s: GameState, game: GameViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth()) {
        listOf("일지" + if (s.pending.isNotEmpty()) " (${s.pending.size})" else "", "장소", "주인공").forEachIndexed { i, title ->
            Box(
                Modifier.weight(1f).background(if (tab == i) Ink else PanelBg).clickable { tab = i }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(title, color = if (tab == i) GoldC else Dim, fontWeight = FontWeight.Bold) }
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (tab) {
            0 -> JournalPanel(s, game)
            1 -> PlacesPanel(s, game)
            else -> HeroPanel(s, game)
        }
    }
}

@Composable
private fun JournalPanel(s: GameState, game: GameViewModel) {
    s.pending.forEach { p ->
        val e = RoadEvents.event(p.eventId)
        Column(Modifier.fillMaxWidth().border(2.dp, GoldC).background(PanelBg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.title, color = GoldC, fontWeight = FontWeight.Bold)
            Text(e.text, color = Paper)
            e.choices.forEachIndexed { i, c ->
                val block = Game.choiceBlock(s, c)
                Button(
                    onClick = { game.resolve(p, i) },
                    enabled = block == null,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Ink, disabledContentColor = Dim),
                ) { Text(c.label + (if (c.cost > 0) " (은자 ${c.cost})" else "") + (block?.let { " · $it" } ?: "")) }
            }
        }
    }
    s.log.asReversed().take(60).forEach {
        Text("${Calendar.text(it.day.toDouble()).removePrefix("강호력 ")} · ${it.text}", color = Paper, fontSize = 14.sp)
    }
}

@Composable
private fun PlacesPanel(s: GameState, game: GameViewModel) {
    val from = s.at ?: s.travel?.to ?: WorldMap.START
    s.at?.let { id ->
        val p = WorldMap.place(id)
        Column(Modifier.fillMaxWidth().border(1.dp, GoldC).background(PanelBg).padding(10.dp)) {
            Text("${p.name} (${p.type.displayName})", color = GoldC, fontWeight = FontWeight.Bold)
            Text(p.summary, color = Paper)
            Text("이곳에서 할 수 있는 일(수련·일·장사·인물)은 다음 단계에서 열린다.", color = Dim, fontSize = 13.sp)
        }
    }
    Text("가고 싶은 곳을 누르면 목적지가 된다.", color = Dim, fontSize = 13.sp)
    WorldMap.places.sortedBy { WorldMap.pathDays(WorldMap.path(from, it.id)) }.forEach { p ->
        val days = WorldMap.pathDays(WorldMap.path(from, p.id))
        val here = p.id == s.at
        val target = p.id == s.destination
        Row(
            Modifier.fillMaxWidth().border(1.dp, if (target) GoldC else Edge).background(PanelBg).clickable { game.setDestination(p.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name + if (p.id !in s.visited) " ·처음" else "", color = if (here) GoldC else Paper, fontWeight = FontWeight.Bold)
                Text("${p.type.displayName} · 위험 ${"★".repeat(p.danger)}", color = Dim, fontSize = 13.sp)
            }
            Text(if (here) "여기" else "${days}일", color = if (target) GoldC else Dim)
        }
    }
}

@Composable
private fun HeroPanel(s: GameState, game: GameViewModel) {
    val h = s.hero
    Column(Modifier.fillMaxWidth().border(1.dp, Edge).background(PanelBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${h.name} · ${s.ageYears}세 (수명 ${Realms.lifespan(h.realm)}세)", color = Paper, fontWeight = FontWeight.Bold)
        Text("경지 ${Realms.name(h.realm)} · 내공 ${Num.kr(h.inner)} / ${Num.kr(Realms.breakthroughCost(h.realm))} (차면 저절로 돌파)", color = Paper)
        Text("기혈 ${h.currentHp().toInt()} / ${h.maxHp.toInt()} · 공격 ${h.atk.toInt()}", color = Paper)
        Text(Stat.entries.joinToString("  ") { "${it.displayName} ${h.stat(it)}" }, color = Paper)
        Text("싸움 ${h.wins}승 ${h.losses}패 · 다녀간 곳 ${s.visited.size}/${WorldMap.places.size}", color = Dim)
    }
    if (game.isDebug) {
        Text("디버그: 시간 넘기기", color = Dim, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.0, 10.0, 100.0).forEach { d ->
                OutlinedButton(onClick = { game.debugSkip(d) }, shape = RectangleShape, border = BorderStroke(1.dp, Edge), modifier = Modifier.weight(1f)) {
                    Text("+${d.toInt()}일", color = Paper)
                }
            }
        }
        OutlinedButton(onClick = game::debugFight, shape = RectangleShape, border = BorderStroke(1.dp, Edge), modifier = Modifier.fillMaxWidth()) {
            Text("싸움 걸기", color = Paper)
        }
    }
}
