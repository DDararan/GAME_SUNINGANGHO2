package com.okdsfin.suningangho2.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 지도 위를 오가는 사람들의 종류 */
enum class AgentKind(val displayName: String, val speed: Double) {
    MERCHANT("상단", 0.55),
    SOLDIER("관군", 0.7),
    BANDIT("산적", 0.9),
    MONK("승려", 0.6),
    WANDERER("낭인", 0.8),
    COURIER("파발마", 2.2),
    FOLK("마을 사람", 0.25),
}

/** 오가는 한 사람 (또는 한 무리) */
class Agent(
    val id: Int,
    val kind: AgentKind,
    val name: String,
    var from: String,
    var to: String,
    /** 길 위 진행 (픽셀) */
    var progress: Double = 0.0,
    /** 장소 안에 머무는 남은 초 (그동안 보이지 않는다) */
    var dwell: Double = 0.0,
    /** 마을 사람: 집 둘레를 거니는 위치 */
    var x: Double = 0.0,
    var y: Double = 0.0,
    var angle: Double = 0.0,
) {
    val traveling: Boolean get() = kind != AgentKind.FOLK && dwell <= 0
}

/**
 * 강호의 오가는 사람들 (기획서 2장, 화면 연출). 규칙에는 들어가지 않고, 실제 초 단위로 지도 위를 움직인다.
 * 상단은 고을과 성 사이를, 관군은 성채와 도시 사이를, 산적은 위험한 길을, 승려는 문파 사이를 오간다.
 */
class Life(private val random: Random = Random(42)) {
    val agents = mutableListOf<Agent>()
    private var nextId = 1

    private val merchantNames = listOf("천양 상단", "서릉 상단", "남해 상단", "영북 마상", "운하 짐배꾼", "철로 대장장이")
    private val wandererNames = listOf("떠돌이 검객", "협객", "낭인", "약초꾼", "유람객", "점쟁이")

    init {
        val towns = WorldMap.places.filter { it.type == PlaceType.TOWN || it.type == PlaceType.CITY }.map { it.id }
        val castles = WorldMap.places.filter { it.type == PlaceType.CASTLE || it.type == PlaceType.CITY }.map { it.id }
        val sects = WorldMap.places.filter { it.type == PlaceType.SECT }.map { it.id }
        val wild = WorldMap.places.filter { it.danger >= 3 }.map { it.id }
        repeat(24) { spawn(AgentKind.MERCHANT, merchantNames.random(random), towns.random(random)) }
        repeat(10) { spawn(AgentKind.SOLDIER, "관군", castles.random(random)) }
        repeat(10) { spawn(AgentKind.BANDIT, "산적 무리", wild.random(random)) }
        repeat(8) { spawn(AgentKind.MONK, "탁발승", sects.random(random)) }
        repeat(18) { spawn(AgentKind.WANDERER, wandererNames.random(random), WorldMap.places.random(random).id) }
        repeat(5) { spawn(AgentKind.COURIER, "파발마", castles.random(random)) }
        for (p in WorldMap.places) {
            val n = when (p.type) {
                PlaceType.CITY -> 10
                PlaceType.TOWN -> 6
                PlaceType.VILLAGE -> 4
                else -> 2
            }
            repeat(n) {
                val a = Agent(nextId++, AgentKind.FOLK, "${p.name} 사람", p.id, p.id)
                a.x = p.x + random.nextDouble(-5.0, 5.0)
                a.y = p.y + random.nextDouble(-4.0, 4.0)
                a.angle = random.nextDouble(0.0, 6.28)
                agents += a
            }
        }
        // 처음부터 길 곳곳에 흩어져 있게
        repeat(400) { step(0.25) }
    }

    private fun spawn(kind: AgentKind, name: String, home: String) {
        val a = Agent(nextId++, kind, name, home, home, dwell = random.nextDouble(0.0, 6.0))
        agents += a
    }

    /** 이 무리가 갈 수 있는 다음 장소 */
    private fun nextStop(a: Agent): String {
        val options = WorldMap.neighbors(a.to).filter { id ->
            val p = WorldMap.place(id)
            when (a.kind) {
                AgentKind.MERCHANT -> p.type != PlaceType.SECT && p.danger <= 3
                AgentKind.SOLDIER -> p.type != PlaceType.SECT
                AgentKind.BANDIT -> WorldMap.roadDanger(a.to, id) >= 3
                AgentKind.MONK -> true
                else -> true
            }
        }.filter { it != a.from || WorldMap.neighbors(a.to).size == 1 }
        return (options.ifEmpty { WorldMap.neighbors(a.to) }).random(random)
    }

    fun step(dt: Double) {
        for (a in agents) {
            if (a.kind == AgentKind.FOLK) {
                val home = WorldMap.place(a.from)
                a.angle += random.nextDouble(-0.6, 0.6) * dt * 4
                a.x += cos(a.angle) * a.kind.speed * dt
                a.y += sin(a.angle) * a.kind.speed * dt
                // 집에서 너무 멀어지면 돌아선다
                if ((a.x - home.x) * (a.x - home.x) + (a.y - home.y) * (a.y - home.y) > 49) a.angle += 3.14
                continue
            }
            if (a.dwell > 0) {
                a.dwell -= dt
                if (a.dwell <= 0) {
                    a.from = a.to
                    a.to = nextStop(a)
                    a.progress = 0.0
                }
                continue
            }
            a.progress += a.kind.speed * dt
            if (a.progress >= WorldMap.distance(a.from, a.to)) {
                a.dwell = random.nextDouble(2.0, 9.0)
                a.progress = 0.0
                a.from = a.to
            }
        }
    }

    /** 지도 위 위치 (머무는 중이면 null) */
    fun position(a: Agent): Pair<Double, Double>? {
        if (a.kind == AgentKind.FOLK) return a.x to a.y
        if (a.dwell > 0) return null
        val len = WorldMap.distance(a.from, a.to)
        return WorldMap.along(a.from, a.to, if (len <= 0) 0.0 else a.progress / len)
    }

    /** 오른쪽으로 가는 중인가 (도트를 뒤집을지) */
    fun facingRight(a: Agent): Boolean = if (a.kind == AgentKind.FOLK) cos(a.angle) >= 0 else WorldMap.place(a.to).x >= WorldMap.place(a.from).x
}
