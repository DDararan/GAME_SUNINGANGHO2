package com.okdsfin.suningangho2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.okdsfin.suningangho2.Selection
import com.okdsfin.suningangho2.core.Calendar
import com.okdsfin.suningangho2.core.Game
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.IncidentKind
import com.okdsfin.suningangho2.core.Josa
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
import kotlin.math.ceil
import kotlin.math.roundToInt

val Ink = Color(0xFF16131C)
val PanelBg = Color(0xFF241E2C)
val Glass = Color(0xD9161320)
val Edge = Color(0xFF4A3F58)
val Paper = Color(0xFFF4F0E6)
val Dim = Color(0xFFA89FB4)
val GoldC = Color(0xFFE8C04A)
val Jade = Color(0xFF5AC05A)
val Blood = Color(0xFFE8574A)

/** 지도 한 칸을 화면 몇 픽셀로 키우는가: 화면 폭에 가상 픽셀 약 110개 */
private const val TARGET_COLUMNS = 110

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

private enum class Sheet(val title: String) { JOURNAL("일지"), PLACES("장소"), WORLD("강호"), HERO("주인공") }

@Composable
fun GameScreen(game: GameViewModel) {
    val s = game.ui
    if (s == null) {
        StartScreen(game)
        return
    }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    Box(Modifier.fillMaxSize().background(Ink)) {
        MapView(game)
        Column(Modifier.fillMaxWidth().background(Glass).statusBarsPadding()) {
            Hud(s, game)
        }
        if (!game.following) {
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 92.dp, end = 10.dp)) {
                Button(onClick = game::recenter, shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = Glass, contentColor = GoldC)) {
                    Text("◎ 내 위치")
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            if (s.pending.isNotEmpty() && sheet == null) {
                Text(
                    "! 사건이 기다린다 (${s.pending.size})",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(GoldC).clickable { sheet = Sheet.JOURNAL }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            game.selection?.let { if (sheet == null) SelectionCard(it, s, game) }
            sheet?.let { current ->
                SheetPanel(current.title, onClose = { sheet = null }) {
                    when (current) {
                        Sheet.JOURNAL -> JournalPanel(s, game)
                        Sheet.PLACES -> PlacesPanel(s, game)
                        Sheet.WORLD -> WorldPanel(s, game)
                        Sheet.HERO -> HeroPanel(s, game)
                    }
                }
            }
            BottomBar(sheet, s) { picked -> sheet = if (sheet == picked) null else picked }
        }
        if (s.fight != null || game.state?.fight != null) BattleOverlay(game)
    }
    game.away?.let { r ->
        AlertDialog(
            onDismissRequest = game::dismissAway,
            confirmButton = { Button(onClick = game::dismissAway, shape = RectangleShape) { Text("알겠다") } },
            title = { Text("자리를 비운 ${r.days.toInt()}일 동안") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (r.entries.isEmpty()) Text("조용히 지나갔다.")
                    r.entries.takeLast(30).forEach { Text("${dateShort(it.day)} · ${it.text}", fontSize = 13.sp) }
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

private fun dateShort(day: Int) = Calendar.text(day.toDouble()).removePrefix("강호력 ")

@Composable
private fun StartScreen(game: GameViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Ink).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("강호 유랑", color = GoldC, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            "열다섯, 청운촌을 나선다. 현실의 하루가 강호의 1년이다. 목적지를 정하지 않으면 발길 닿는 대로 떠돌고, 지도에서 장소를 누르면 그곳으로 갈 수 있다.",
            color = Paper,
        )
        OutlinedTextField(value = name, onValueChange = { name = it.take(8) }, label = { Text("이름") }, singleLine = true)
        Button(onClick = { game.newGame(name) }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("길을 나선다") }
    }
}

/** 지금 무엇을 하고 있는가 */
private fun doing(s: GameState): String {
    val t = s.travel
    return when {
        s.fight != null -> "싸우는 중!"
        s.recover > 0 -> "상처를 돌보는 중 (${"%.1f".format(s.recover)}일)"
        t != null -> "${Josa.ro(WorldMap.place(t.to).name)} 가는 길"
        s.hold -> "${WorldMap.place(s.at!!).name}에 머무는 중"
        else -> "${WorldMap.place(s.at!!).name}에 머무는 중 · ${"%.1f".format(s.stay.coerceAtLeast(0.0))}일 뒤 떠난다"
    }
}

@Composable
private fun Hud(s: GameState, game: GameViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.hero.name} · ${s.ageYears}세 · ${Realms.name(s.hero.realm)}", color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("은자 ${Num.kr(s.hero.silver.toDouble())}", color = GoldC, fontWeight = FontWeight.Bold)
        }
        Text(Calendar.text(s.day) + " · " + doing(s), color = Dim, fontSize = 13.sp)
        val dest = s.destination
        if (dest != null || s.hold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (dest != null) "목적지 ${WorldMap.place(dest).name}" else "목적지에 머문다",
                    color = GoldC,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("떠돌기 ▶", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { game.wander() }.padding(4.dp))
            }
        }
    }
}

/** 화면 전체를 채우는 도트 지도. 누르면 고르고, 끌면 둘러본다 */
@Composable
private fun MapView(game: GameViewModel) {
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scale = maxOf(2, (widthPx / TARGET_COLUMNS).roundToInt())
        val vw = ceil(widthPx / scale).toInt()
        val vh = ceil(heightPx / scale).toInt()
        val frame = remember(vw, vh) { Frame(vw, vh) }
        val bitmap = remember(vw, vh) { Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        LaunchedEffect(vw, vh) { game.setViewport(vw, vh) }
        val placeStyle = TextStyle(color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black, Offset(2f, 2f), 0f))
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(scale) {
                    detectTapGestures { pos -> game.tapWorld(pos.x / scale + game.camX, pos.y / scale + game.camY) }
                }
                .pointerInput(scale) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        game.pan(drag.x / scale.toDouble(), drag.y / scale.toDouble())
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION") game.frameCount
            val s = game.state ?: return@Canvas
            WorldArt.render(frame, s, game.life, game.fx.time, game.camX, game.camY)
            bitmap.setPixels(frame.px, 0, vw, 0, 0, vw, vh)
            drawImage(image, dstOffset = IntOffset.Zero, dstSize = IntSize(vw * scale, vh * scale), filterQuality = FilterQuality.None)
            val cx = game.camX
            val cy = game.camY
            for (p in WorldMap.places) {
                val sx = (p.x - cx) * scale
                val sy = (p.y - cy + 7) * scale
                if (sx < -100 || sy < -40 || sx > size.width + 100 || sy > size.height) continue
                val layout = measurer.measure(p.name, placeStyle)
                drawText(layout, topLeft = Offset((sx - layout.size.width / 2).toFloat(), sy.toFloat()))
            }
            for (inc in s.incidents) {
                val (ix, iy) = inc.position()
                val sx = (ix - cx) * scale
                val sy = (iy - cy - 18) * scale
                if (sx < -100 || sy < -40 || sx > size.width + 100 || sy > size.height) continue
                val style = placeStyle.copy(color = incidentColor(inc.kind), fontSize = 10.sp)
                val layout = measurer.measure(inc.kind.displayName, style)
                drawText(layout, topLeft = Offset((sx - layout.size.width / 2).toFloat(), sy.toFloat()))
            }
        }
    }
}

private fun incidentColor(kind: IncidentKind): Color = when (kind) {
    IncidentKind.RAID, IncidentKind.BEAST, IncidentKind.FIRE -> Blood
    IncidentKind.PATROL -> Color(0xFF8AB4F0)
    IncidentKind.MASTER -> GoldC
    else -> Color(0xFFFFE08A)
}

/** 싸움: 지도 위에 도트 싸움터를 띄운다 */
@Composable
private fun BattleOverlay(game: GameViewModel) {
    val frame = remember { Frame(W, H) }
    val bitmap = remember { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    val measurer = rememberTextMeasurer()
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val density = LocalDensity.current
            val scale = maxOf(1, (with(density) { maxWidth.toPx() } / W).toInt())
            val heightDp = with(density) { (H * scale).toDp() }
            val nameStyle = TextStyle(color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black, Offset(2f, 2f), 0f))
            Canvas(Modifier.fillMaxWidth().height(heightDp).border(2.dp, GoldC)) {
                @Suppress("UNUSED_EXPRESSION") game.frameCount
                val s = game.state ?: return@Canvas
                val fight = s.fight ?: return@Canvas
                val danger = s.travel?.let { WorldMap.roadDanger(it.from, it.to) } ?: WorldMap.place(s.at ?: WorldMap.START).danger
                renderBattle(frame, fight, game.fx, battlePalette(danger))
                bitmap.setPixels(frame.px, 0, W, 0, 0, W, H)
                val shakeX = if (game.fx.shake > 0) ((game.fx.time * 60).toInt() % 3 - 1) * scale else 0
                val left = ((size.width - W * scale) / 2).toInt()
                drawImage(image, dstOffset = IntOffset(left + shakeX, 0), dstSize = IntSize(W * scale, H * scale), filterQuality = FilterQuality.None)
                val layout = measurer.measure(fight.enemy.name, nameStyle)
                drawText(layout, topLeft = Offset(left + (W - 4) * scale - layout.size.width.toFloat(), 2f * scale))
            }
        }
    }
}

@Composable
private fun BottomBar(open: Sheet?, s: GameState, onPick: (Sheet) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Glass)) {
        Sheet.entries.forEach { sh ->
            val badge = when (sh) {
                Sheet.JOURNAL -> s.pending.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                Sheet.WORLD -> " ${s.incidents.size}"
                else -> ""
            }
            Box(
                Modifier.weight(1f).clickable { onPick(sh) }.background(if (open == sh) PanelBg else Color.Transparent).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(sh.title + badge, color = if (open == sh) GoldC else Paper, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SheetPanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.55f).background(PanelBg).border(1.dp, Edge)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = GoldC, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("닫기 ✕", color = Dim, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

/** 지도에서 누른 것의 설명과 할 일 */
@Composable
private fun SelectionCard(sel: Selection, s: GameState, game: GameViewModel) {
    Column(Modifier.fillMaxWidth().background(PanelBg).border(1.dp, GoldC).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (sel) {
            is Selection.PlaceSel -> {
                val p = WorldMap.place(sel.id)
                val from = s.at ?: s.travel?.to ?: WorldMap.START
                Text("${p.name} · ${p.type.displayName} · 위험 ${"★".repeat(p.danger)}", color = GoldC, fontWeight = FontWeight.Bold)
                Text(p.summary, color = Paper)
                s.incidents.filter { it.place == p.id }.forEach { Text("지금: ${it.kind.displayName} — ${it.kind.description}", color = incidentColor(it.kind), fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.id == s.at) {
                        Text("지금 여기 있다", color = Dim, modifier = Modifier.weight(1f))
                    } else {
                        Button(onClick = { game.setDestination(p.id) }, shape = RectangleShape, modifier = Modifier.weight(1f)) {
                            Text("이곳으로 간다 (${"%.1f".format(WorldMap.pathDays(WorldMap.path(from, p.id)))}일)")
                        }
                    }
                    OutlinedButton(onClick = game::clearSelection, shape = RectangleShape, border = BorderStroke(1.dp, Edge)) { Text("닫기", color = Paper) }
                }
            }
            is Selection.IncidentSel -> {
                val inc = game.incident(sel.id)
                if (inc == null) {
                    Text("이미 끝난 일이다.", color = Dim)
                } else {
                    Text(inc.title, color = incidentColor(inc.kind), fontWeight = FontWeight.Bold)
                    Text(inc.kind.description, color = Paper)
                    Text("남은 때: ${"%.1f".format((inc.end - s.day).coerceAtLeast(0.0))}일", color = Dim, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inc != null && inc.kind != IncidentKind.PATROL) {
                        Button(onClick = { game.setDestination(inc.target) }, shape = RectangleShape, modifier = Modifier.weight(1f)) { Text("가 본다") }
                    }
                    OutlinedButton(onClick = game::clearSelection, shape = RectangleShape, border = BorderStroke(1.dp, Edge)) { Text("닫기", color = Paper) }
                }
            }
            is Selection.AgentSel -> {
                val a = sel.agent
                val where = if (a.from == a.to) "${WorldMap.place(a.from).name} 근처" else "${WorldMap.place(a.from).name}에서 ${Josa.ro(WorldMap.place(a.to).name)} 가는 길"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, color = GoldC, fontWeight = FontWeight.Bold)
                        Text("${a.kind.displayName} · $where", color = Paper, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = game::clearSelection, shape = RectangleShape, border = BorderStroke(1.dp, Edge)) { Text("닫기", color = Paper) }
                }
            }
        }
    }
}

@Composable
private fun JournalPanel(s: GameState, game: GameViewModel) {
    s.pending.forEach { p ->
        val e = RoadEvents.event(p.eventId)
        Column(Modifier.fillMaxWidth().border(2.dp, GoldC).background(Ink).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.title, color = GoldC, fontWeight = FontWeight.Bold)
            Text(e.text, color = Paper)
            e.choices.forEachIndexed { i, c ->
                val block = Game.choiceBlock(s, c)
                Button(
                    onClick = { game.resolve(p, i) },
                    enabled = block == null,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = PanelBg, disabledContentColor = Dim),
                ) { Text(c.label + (if (c.cost > 0) " (은자 ${c.cost})" else "") + (block?.let { " · $it" } ?: "")) }
            }
        }
    }
    s.log.asReversed().take(80).forEach { Text("${dateShort(it.day)} · ${it.text}", color = Paper, fontSize = 14.sp) }
}

@Composable
private fun PlacesPanel(s: GameState, game: GameViewModel) {
    val from = s.at ?: s.travel?.to ?: WorldMap.START
    Text("누르면 목적지가 된다.", color = Dim, fontSize = 13.sp)
    WorldMap.places.sortedBy { WorldMap.pathDays(WorldMap.path(from, it.id)) }.forEach { p ->
        val days = WorldMap.pathDays(WorldMap.path(from, p.id))
        val here = p.id == s.at
        val target = p.id == s.destination
        val now = s.incidents.firstOrNull { it.place == p.id }
        Row(
            Modifier.fillMaxWidth().border(1.dp, if (target) GoldC else Edge).background(Ink).clickable { game.setDestination(p.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name + if (p.id !in s.visited) " ·처음" else "", color = if (here) GoldC else Paper, fontWeight = FontWeight.Bold)
                Text("${p.type.displayName} · 위험 ${"★".repeat(p.danger)}" + (now?.let { " · ${it.kind.displayName}" } ?: ""), color = Dim, fontSize = 13.sp)
            }
            Text(if (here) "여기" else "${"%.1f".format(days)}일", color = if (target) GoldC else Dim)
        }
    }
}

/** 강호: 지금 지도 위에서 벌어지는 일들 */
@Composable
private fun WorldPanel(s: GameState, game: GameViewModel) {
    if (s.incidents.isEmpty()) Text("지금은 조용하다.", color = Dim)
    s.incidents.sortedBy { it.end }.forEach { inc ->
        Row(
            Modifier.fillMaxWidth().border(1.dp, Edge).background(Ink).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(inc.title, color = incidentColor(inc.kind), fontWeight = FontWeight.Bold)
                Text(inc.kind.description, color = Dim, fontSize = 13.sp)
            }
            if (inc.kind != IncidentKind.PATROL) {
                Text("가 본다", color = GoldC, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { game.setDestination(inc.target) }.padding(6.dp))
            }
        }
    }
    Text("오가는 사람 ${game.life.agents.size}명. 지도에서 사람이나 사건을 누르면 무엇인지 보인다.", color = Dim, fontSize = 13.sp)
}

@Composable
private fun HeroPanel(s: GameState, game: GameViewModel) {
    val h = s.hero
    Column(Modifier.fillMaxWidth().border(1.dp, Edge).background(Ink).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${h.name} · ${s.ageYears}세 (수명 ${Realms.lifespan(h.realm)}세)", color = Paper, fontWeight = FontWeight.Bold)
        Text("경지 ${Realms.name(h.realm)} · 내공 ${Num.kr(h.inner)} / ${Num.kr(Realms.breakthroughCost(h.realm))} (차면 저절로 돌파)", color = Paper)
        Text("기혈 ${h.currentHp().toInt()} / ${h.maxHp.toInt()} · 공격 ${h.atk.toInt()}", color = Paper)
        Text(Stat.entries.joinToString("  ") { "${it.displayName} ${h.stat(it)}" }, color = Paper)
        Text("싸움 ${h.wins}승 ${h.losses}패 · 다녀간 곳 ${s.visited.size}/${WorldMap.places.size}", color = Dim)
    }
    if (game.isDebug) {
        Text("디버그", color = Dim, fontSize = 13.sp)
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
