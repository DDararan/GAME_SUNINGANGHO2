package com.okdsfin.suningangho2

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.okdsfin.suningangho2.core.GameEvent
import com.okdsfin.suningangho2.core.GameState
import com.okdsfin.suningangho2.core.Mode
import com.okdsfin.suningangho2.core.Offline
import com.okdsfin.suningangho2.core.OfflineReward
import com.okdsfin.suningangho2.core.Realms
import com.okdsfin.suningangho2.core.SaveCodec
import com.okdsfin.suningangho2.core.Sim
import com.okdsfin.suningangho2.core.Upgrade
import com.okdsfin.suningangho2.pixel.Fx
import java.io.File
import kotlin.random.Random

/**
 * 게임 한 판: 화면 프레임마다 [frame]으로 전투를 흘려 보내고, 연출([fx])과 화면 정보([ui])를 갱신한다.
 * 화면 아래 패널은 매 프레임이 아니라 0.2초마다 다시 그린다.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val file = File(application.filesDir, "save.json")
    private val random = Random(System.nanoTime())

    /** 전투 장면이 읽는 최신 상태 (매 프레임) */
    var state: GameState = load()
        private set
    val fx = Fx()

    /** 장면을 다시 그리게 하는 신호 */
    var frameCount by mutableLongStateOf(0L)
        private set

    /** 아래 패널용 상태 (0.2초마다) */
    var ui by mutableStateOf(state)
        private set

    /** 자리를 비운 동안 쌓인 것 (확인 창) */
    var offline by mutableStateOf<OfflineReward?>(null)
        private set

    /** 경지 돌파 알림 */
    var message by mutableStateOf<String?>(null)
        private set

    private var uiTimer = 0.0
    private var saveTimer = 0.0

    fun frame(dtRaw: Double) {
        val dt = dtRaw.coerceIn(0.0, 0.1)
        val events = mutableListOf<GameEvent>()
        val before = state
        state = Sim.step(state, dt, random, events)
        fx.update(dt, before, state, events)
        events.filterIsInstance<GameEvent.StageClear>().lastOrNull()?.let { if (it.stage % 10 == 0) message = "${it.stage}스테이지 돌파! 새 지역으로 간다." }
        frameCount++
        uiTimer += dt
        if (uiTimer >= 0.2) {
            uiTimer = 0.0
            ui = state
        }
        saveTimer += dt
        if (saveTimer >= 15) {
            saveTimer = 0.0
            save()
        }
    }

    private fun act(change: (GameState) -> GameState) {
        state = change(state)
        ui = state
        save()
    }

    fun buy(u: Upgrade) = act { Sim.buy(it, u) }

    fun setMode(mode: Mode) = act { Sim.setMode(it, mode) }

    fun breakthrough() {
        val events = mutableListOf<GameEvent>()
        act { Sim.breakthrough(it, events) }
        events.filterIsInstance<GameEvent.Breakthrough>().firstOrNull()?.let { message = "${Realms.name(it.realm)}의 경지에 올랐다! 모든 힘이 두 배." }
    }

    fun dismissMessage() {
        message = null
    }

    /** 앱이 다시 보일 때: 비운 시간만큼 보상 */
    fun onStart() {
        val now = System.currentTimeMillis()
        val saved = state.savedAt
        if (saved > 0 && offline == null) {
            Offline.reward(state, (now - saved) / 1000.0)?.let { offline = it }
        }
        state = state.copy(savedAt = now)
    }

    fun claimOffline() {
        val r = offline ?: return
        offline = null
        act { Offline.apply(it, r) }
    }

    fun onStop() = save()

    private fun save() {
        state = state.copy(savedAt = System.currentTimeMillis())
        runCatching { file.writeText(SaveCodec.encode(state)) }
    }

    private fun load(): GameState = runCatching { SaveCodec.decode(file.readText()) }.getOrNull() ?: GameState()

    override fun onCleared() = save()
}
