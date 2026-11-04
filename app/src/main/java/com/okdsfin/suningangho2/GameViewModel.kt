package com.okdsfin.suningangho2

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.okdsfin.suningangho2.core.Calendar
import com.okdsfin.suningangho2.core.FightEvent
import com.okdsfin.suningangho2.core.FightSim
import com.okdsfin.suningangho2.core.Game
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.LogEntry
import com.okdsfin.suningangho2.core.Pending
import com.okdsfin.suningangho2.core.SaveCodec
import com.okdsfin.suningangho2.core.WorldMap
import com.okdsfin.suningangho2.pixel.Fx
import java.io.File
import kotlin.random.Random

/** 자리를 비운 동안 있었던 일 */
data class AwayReport(val days: Double, val entries: List<LogEntry>)

/**
 * 강호 유랑 한 판 (기획서 v0.2): 프레임마다 게임 시간을 흘리고(현실 하루 = 1년),
 * 싸움이 나면 시간을 멈추고 도트 자동 전투를 보여 준 뒤 결과를 반영한다.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val file = File(application.filesDir, "save.json")
    private val random = Random(System.nanoTime())
    val isDebug: Boolean = (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** 장면이 읽는 최신 상태 (매 프레임). 새 판이면 null */
    var state: GameState? = load()
        private set
    val fx = Fx()

    var frameCount by mutableLongStateOf(0L)
        private set

    /** 아래 패널용 (0.25초마다) */
    var ui by mutableStateOf(state)
        private set

    var away by mutableStateOf<AwayReport?>(null)
        private set

    /** 싸움이 끝난 뒤 결과를 보여 주는 시간 */
    private var fightOverFor = 0.0
    private var uiTimer = 0.0
    private var saveTimer = 0.0

    fun frame(dtRaw: Double) {
        val s = state ?: return
        val dt = dtRaw.coerceIn(0.0, 0.1)
        val fight = s.fight
        if (fight != null) {
            if (!FightSim.over(fight)) {
                val events = mutableListOf<FightEvent>()
                val next = FightSim.step(fight, dt, random, events)
                fx.update(dt, next, events)
                if (FightSim.over(next)) {
                    fx.finish(next)
                    fightOverFor = 0.0
                }
                state = s.copy(fight = next)
            } else {
                fx.tick(dt)
                fightOverFor += dt
                if (fightOverFor > 1.2) {
                    state = Game.finishFight(s, fight)
                    push()
                }
            }
        } else {
            fx.tick(dt)
            val next = Game.advance(s, dt * 1000 / Calendar.MS_PER_DAY, random, live = true)
            if (next.fight != null) fx.reset()
            state = next
        }
        frameCount++
        uiTimer += dt
        if (uiTimer >= 0.25) {
            uiTimer = 0.0
            ui = state
        }
        saveTimer += dt
        if (saveTimer >= 20) {
            saveTimer = 0.0
            save()
        }
    }

    private fun push() {
        ui = state
        save()
    }

    private fun act(change: (GameState) -> GameState) {
        val s = state ?: return
        state = change(s)
        push()
    }

    fun newGame(name: String) {
        state = Game.new(name.trim().ifEmpty { "무명" }, random)
        push()
    }

    fun setDestination(id: String) = act { Game.setDestination(it, id) }

    fun wander() = act { Game.wander(it) }

    fun resolve(p: Pending, choice: Int) = act { Game.resolve(it, p, choice, random) }

    /** 지도에서 누른 곳 (세계 좌표) 가까이에 장소가 있으면 목적지로 */
    fun tapWorld(x: Double, y: Double) {
        val p = WorldMap.places.minBy { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
        if ((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) <= 64) setDestination(p.id)
    }

    /** 디버그: 지금 길의 위험도로 싸움을 건다 */
    fun debugFight() {
        val s = state ?: return
        if (s.fight != null) return
        val danger = s.travel?.let { WorldMap.roadDanger(it.from, it.to) } ?: WorldMap.place(s.at ?: WorldMap.START).danger
        fx.reset()
        state = s.copy(fight = com.okdsfin.suningangho2.core.FightSim.roadFight(s.hero, danger, random))
    }

    /** 디버그: 날짜를 앞당긴다 (앱을 꺼 둔 것과 같은 계산) */
    fun debugSkip(days: Double) = catchUp(days)

    /** 앱이 다시 보일 때: 비운 시간만큼 흘린다 */
    fun onStart() {
        val s = state ?: return
        if (s.savedAt <= 0) return
        val days = (System.currentTimeMillis() - s.savedAt) / Calendar.MS_PER_DAY
        if (days >= 0.05) catchUp(days)
    }

    private fun catchUp(days: Double) {
        val s = state ?: return
        // 보고 있던 싸움이 있으면 먼저 끝낸다
        val start = s.fight?.let { Game.finishFight(s, FightSim.runToEnd(it, random)) } ?: s
        val next = Game.advance(start, days, random, live = false)
        state = next
        if (days >= 0.5) away = AwayReport(days, newEntries(start, next))
        push()
    }

    /** 로그는 150줄까지만 남으므로, 마지막 공통 항목 뒤부터 새 항목으로 본다 */
    private fun newEntries(before: GameState, after: GameState): List<LogEntry> {
        val last = before.log.lastOrNull() ?: return after.log
        val idx = after.log.lastIndexOf(last)
        return if (idx >= 0) after.log.drop(idx + 1) else after.log
    }

    fun dismissAway() {
        away = null
    }

    fun onStop() = save()

    private fun save() {
        val s = state ?: return
        state = s.copy(savedAt = System.currentTimeMillis())
        runCatching { file.writeText(SaveCodec.encode(state!!)) }
    }

    private fun load(): GameState? = runCatching { SaveCodec.decode(file.readText()) }.getOrNull()

    override fun onCleared() = save()
}
