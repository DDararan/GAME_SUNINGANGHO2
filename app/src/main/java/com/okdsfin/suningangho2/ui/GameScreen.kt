package com.okdsfin.suningangho2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okdsfin.suningangho2.GameViewModel
import com.okdsfin.suningangho2.core.Balance
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.Mode
import com.okdsfin.suningangho2.core.Num
import com.okdsfin.suningangho2.core.Realms
import com.okdsfin.suningangho2.core.Regions
import com.okdsfin.suningangho2.core.Sim
import com.okdsfin.suningangho2.core.Upgrade
import com.okdsfin.suningangho2.pixel.Frame
import com.okdsfin.suningangho2.pixel.H
import com.okdsfin.suningangho2.pixel.W
import com.okdsfin.suningangho2.pixel.renderScene

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
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        ResourceBar(game.ui)
        PixelScene(game)
        StageBar(game.ui, game)
        game.message?.let { msg ->
            Text(
                msg,
                color = Ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(GoldC).clickable { game.dismissMessage() }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Panels(game.ui, game)
    }
    game.offline?.let { r ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { Button(onClick = game::claimOffline) { Text("받는다") } },
            title = { Text("좌선하는 동안") },
            text = {
                Text(
                    "${formatDuration(r.seconds)} 동안 홀로 수련했다.\n" +
                        "처치 ${Num.kr(r.kills.toDouble())} · 은자 +${Num.kr(r.silver)} · 내공 +${Num.kr(r.inner)}\n(최대 12시간까지 쌓인다)",
                )
            },
            shape = RectangleShape,
            containerColor = PanelBg,
        )
    }
}

private fun formatDuration(seconds: Double): String {
    val m = (seconds / 60).toInt()
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}

@Composable
private fun ResourceBar(s: GameState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("은자 ${Num.kr(s.silver)}", color = GoldC, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("내공 ${Num.kr(s.inner)}", color = Jade, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(Realms.name(s.realm), color = Paper, fontWeight = FontWeight.Bold)
    }
}

/** 도트 장면: 가상 화면을 정수배로만 키운다 (픽셀이 고르게 보이도록) */
@Composable
private fun PixelScene(game: GameViewModel) {
    val frame = remember { Frame(W, H) }
    val bitmap = remember { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
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
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val scale = maxOf(1, (widthPx / W).toInt())
        val heightDp = with(LocalDensity.current) { (H * scale).toDp() }
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            @Suppress("UNUSED_EXPRESSION") game.frameCount
            renderScene(frame, game.state, game.fx)
            bitmap.setPixels(frame.px, 0, W, 0, 0, W, H)
            val shakeX = if (game.fx.shake > 0) ((game.fx.time * 60).toInt() % 3 - 1) * scale else 0
            val left = ((size.width - W * scale) / 2).toInt() + shakeX
            drawImage(image, dstOffset = IntOffset(left, 0), dstSize = IntSize(W * scale, H * scale), filterQuality = FilterQuality.None)
        }
    }
}

@Composable
private fun StageBar(s: GameState, game: GameViewModel) {
    val region = Regions.of(s.stage)
    Row(
        Modifier.fillMaxWidth().background(PanelBg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${region.name} · 스테이지 ${s.stage}", color = Paper, fontWeight = FontWeight.Bold)
            Text(
                if (s.mode == Mode.PUSH) "도전 중 · 웨이브 ${s.wave}/${Balance.WAVES}" + if (s.isBossWave) " · 보스!" else ""
                else "방치 사냥 · 같은 스테이지를 되풀이한다",
                color = if (s.isBossWave) Blood else Dim,
                fontSize = 13.sp,
            )
        }
        if (s.mode == Mode.FARM) {
            Button(onClick = { game.setMode(Mode.PUSH) }, shape = RectangleShape) { Text("도전") }
        } else {
            OutlinedButton(onClick = { game.setMode(Mode.FARM) }, shape = RectangleShape, border = BorderStroke(1.dp, Edge)) { Text("방치", color = Dim) }
        }
    }
}

@Composable
private fun Panels(s: GameState, game: GameViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth()) {
        listOf("수련", "경지", "기록").forEachIndexed { i, title ->
            Box(
                Modifier.weight(1f).background(if (tab == i) Ink else PanelBg).clickable { tab = i }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(title, color = if (tab == i) GoldC else Dim, fontWeight = FontWeight.Bold) }
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (tab) {
            0 -> Upgrade.entries.forEach { UpgradeRow(s, it, game) }
            1 -> RealmPanel(s, game)
            else -> RecordPanel(s)
        }
    }
}

private fun effect(s: GameState, u: Upgrade): String = when (u) {
    Upgrade.ATTACK -> Num.kr(Balance.attack(s))
    Upgrade.VITALITY -> Num.kr(Balance.maxHp(s))
    Upgrade.AGILITY -> "초당 %.2f회".format(1 / Balance.attackInterval(s))
    Upgrade.INSIGHT -> "%.1f%%".format(Balance.critChance(s) * 100)
}

@Composable
private fun UpgradeRow(s: GameState, u: Upgrade, game: GameViewModel) {
    val level = s.level(u)
    val maxed = level >= u.maxLevel
    val next = s.copy(levels = s.levels + (u to level + 1))
    Row(
        Modifier.fillMaxWidth().border(1.dp, Edge).background(PanelBg).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${u.displayName} Lv.$level", color = Paper, fontWeight = FontWeight.Bold)
            Text(
                "${u.description} ${effect(s, u)}" + if (maxed) " (최대)" else " → ${effect(next, u)}",
                color = Dim,
                fontSize = 13.sp,
            )
        }
        Button(
            onClick = { game.buy(u) },
            enabled = Sim.canBuy(s, u),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(disabledContainerColor = Ink, disabledContentColor = Dim),
        ) { Text(if (maxed) "최대" else "은자 ${Num.kr(Balance.cost(u, level))}", fontFamily = FontFamily.Default) }
    }
}

@Composable
private fun RealmPanel(s: GameState, game: GameViewModel) {
    val last = s.realm >= Realms.last
    val cost = Balance.breakthroughCost(s.realm)
    Column(Modifier.fillMaxWidth().border(1.dp, Edge).background(PanelBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Realms.name(s.realm), color = GoldC, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("경지 배율 ×${Num.kr(Balance.realmMult(s.realm))} · 좌선 초당 내공 +${"%.1f".format(Balance.innerPerSec(s.realm))}", color = Dim, fontSize = 13.sp)
        if (last) {
            Text("더 오를 경지가 없다. 비승은 다음 이야기에서.", color = Paper)
        } else {
            Text("다음 경지: ${Realms.name(s.realm + 1)} (모든 힘 ×2)", color = Paper)
            Box(Modifier.fillMaxWidth().height(10.dp).background(Ink)) {
                Box(Modifier.fillMaxWidth((s.inner / cost).toFloat().coerceIn(0f, 1f)).height(10.dp).background(Jade))
            }
            Text("내공 ${Num.kr(s.inner)} / ${Num.kr(cost)}", color = Dim, fontSize = 13.sp)
            Button(onClick = game::breakthrough, enabled = Sim.canBreakthrough(s), shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
                Text("돌파한다")
            }
        }
        if (s.realm + 1 == Realms.MURIM) Text("생사경 너머는 수선의 길이다.", color = Dim, fontSize = 13.sp)
    }
}

@Composable
private fun RecordPanel(s: GameState) {
    Column(Modifier.fillMaxWidth().border(1.dp, Edge).background(PanelBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("최고 스테이지 ${s.maxStage}", color = Paper)
        Text("벤 적 ${Num.kr(s.kills.toDouble())}", color = Paper)
        Text("경지 ${Realms.name(s.realm)} (${s.realm + 1}/${Realms.names.size})", color = Paper)
        Spacer(Modifier.width(4.dp))
        Text("앱을 꺼 두어도 최대 12시간까지 방치 사냥으로 은자와 내공이 쌓인다.", color = Dim, fontSize = 13.sp)
    }
}
