package com.okdsfin.suningangho2.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** 길 위에 있는 동안 */
@Serializable
data class Travel(val from: String, val to: String, val progress: Double, val total: Double)

@Serializable
data class LogEntry(val day: Int, val text: String)

/** 고르기를 기다리는 사건 */
@Serializable
data class Pending(val eventId: String, val day: Int, val danger: Int)

@Serializable
data class GameState(
    val hero: Hero,
    /** 길을 나선 뒤 흐른 게임 날수 */
    val day: Double = 0.0,
    /** 머무는 장소 (길 위면 null) */
    val at: String? = WorldMap.START,
    val travel: Travel? = null,
    /** 자동 방랑: 이 장소에 더 머물 날수 */
    val stay: Double = 0.3,
    /** 지정한 목적지 */
    val destination: String? = null,
    /** 지정한 목적지에 닿아 머무는 중 (자동으로 떠나지 않는다) */
    val hold: Boolean = false,
    /** 진 뒤 상처를 돌보는 날수 (그동안 길을 멈춘다) */
    val recover: Double = 0.0,
    val cameFrom: String? = null,
    val visited: Set<String> = setOf(WorldMap.START),
    val fight: Fight? = null,
    val pending: List<Pending> = emptyList(),
    /** 지도 위에서 벌어지는 일들 */
    val incidents: List<Incident> = emptyList(),
    val nextIncidentAt: Double = 0.02,
    val nextIncidentId: Int = 1,
    val log: List<LogEntry> = emptyList(),
    /** 마지막으로 저장한 실제 시각 */
    val savedAt: Long = 0,
) {
    val ageYears: Int get() = Hero.START_AGE + (day / Calendar.DAYS_PER_YEAR).toInt()

    /** 지도 위 주인공 위치 (세계 픽셀) */
    fun position(): Pair<Double, Double> {
        val t = travel ?: return WorldMap.place(at ?: WorldMap.START).let { it.x.toDouble() to it.y.toDouble() }
        val a = WorldMap.place(t.from)
        val b = WorldMap.place(t.to)
        val r = (t.progress / t.total).coerceIn(0.0, 1.0)
        return a.x + (b.x - a.x) * r to a.y + (b.y - a.y) * r
    }
}

/** 강호력 (현실 하루 = 게임 1년, 기획서 1장) */
object Calendar {
    const val DAYS_PER_YEAR = 365
    const val MS_PER_DAY = 86_400_000.0 / DAYS_PER_YEAR

    fun text(day: Double): String {
        val d = day.toInt()
        val year = d / DAYS_PER_YEAR + 1
        val inYear = d % DAYS_PER_YEAR
        val month = (inYear / 30.42).toInt().coerceAtMost(11) + 1
        val date = (inYear - ((month - 1) * 30.42).toInt()) + 1
        return "강호력 ${year}년 ${month}월 ${date}일"
    }
}

// ── 선택지 사건 (기획서 4장) ──

data class Outcome(val text: String, val silver: Int = 0, val inner: Double = 0.0, val stat: Stat? = null)

data class Choice(
    val label: String,
    val cost: Int = 0,
    val check: Stat? = null,
    val chance: Double = 1.0,
    val success: Outcome,
    val failure: Outcome = success,
)

data class RoadEvent(val id: String, val title: String, val text: String, val choices: List<Choice>)

object RoadEvents {
    val all: List<RoadEvent> = listOf(
        RoadEvent(
            "traveler", "쓰러진 나그네", "길가에 나그네가 쓰러져 있다. 입술이 하얗게 말랐다.",
            listOf(
                Choice("물과 은자를 건넨다", cost = 10, success = Outcome("나그네가 정신을 차리고 품에서 낡은 책 한 장을 내민다. 호흡법이 적혀 있다.", inner = 8.0)),
                Choice("갈 길을 간다", success = Outcome("뒤를 돌아보지 않았다.")),
            ),
        ),
        RoadEvent(
            "peddler", "수상한 약장수", "약장수가 소매를 잡는다. \"백 년 묵은 산삼이오. 은자 마흔 냥만.\"",
            listOf(
                Choice("산다", cost = 40, chance = 0.6, success = Outcome("진짜였다! 뜨거운 기운이 단전으로 흘러든다.", inner = 25.0), failure = Outcome("도라지였다. 약장수는 벌써 사라졌다.")),
                Choice("거절한다", success = Outcome("약장수가 혀를 차며 다른 길손에게 간다.")),
            ),
        ),
        RoadEvent(
            "old_man", "갈림길의 노인", "갈림길에 앉은 노인이 지팡이로 바위를 두드린다. \"젊은이, 그 바위를 들어 보겠나?\"",
            listOf(
                Choice("바위를 든다", check = Stat.STRENGTH, chance = 0.4, success = Outcome("바위가 들렸다. 노인이 웃으며 힘 쓰는 법을 일러 준다.", stat = Stat.STRENGTH), failure = Outcome("꿈쩍도 않는다. 노인이 껄껄 웃는다.")),
                Choice("고개를 숙이고 지나간다", success = Outcome("노인이 혼잣말을 한다. \"요즘 젊은 것들은…\"")),
            ),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun event(id: String): RoadEvent = byId.getValue(id)
}

/** 하루하루 흐르는 강호 (기획서 1~4장) */
object Game {
    const val ENCOUNTER = 0.35
    const val MAX_PENDING = 3
    const val MAX_LOG = 150
    const val HEAL_PER_DAY = 0.25
    const val RECOVER_DAYS = 3.0

    fun new(name: String, random: Random): GameState {
        var s = GameState(Hero.roll(name, random)).log("${Hero.START_AGE}세, 청운촌을 나서 강호로 간다.")
        // 처음부터 강호 곳곳에서 일이 벌어지고 있다
        repeat(4) { s = spawnIncident(s, random) }
        return s
    }

    private fun GameState.log(text: String): GameState =
        copy(log = (log + LogEntry(day.toInt(), text)).takeLast(MAX_LOG))

    /**
     * [days]만큼 흘린다. 싸움이 나면 [live]일 때는 멈추고 돌려준다(앱이 도트 전투로 보여 준다).
     * 앱을 꺼 둔 동안([live] = false)은 즉시 결판을 낸다.
     */
    fun advance(start: GameState, days: Double, random: Random, live: Boolean): GameState {
        var s = start
        var left = days
        while (left > 1e-9 && s.hero.alive) {
            val fight = s.fight
            if (fight != null) {
                if (live) return s
                s = finishFight(s, FightSim.runToEnd(fight, random))
                continue
            }
            val toNextDay = floor(s.day) + 1 - s.day
            val toIncident = (s.nextIncidentAt - s.day).coerceAtLeast(1e-6)
            val dt = min(left, min(toNextDay, toIncident))
            s = flow(s, dt, random)
            left -= dt
            if (dt >= toNextDay - 1e-9) s = daily(s.copy(day = floor(s.day + 1e-6)), random)
            if (s.day >= s.nextIncidentAt - 1e-9) s = spawnIncident(s, random)
        }
        return s
    }

    /** 시간에 따라 이어지는 것: 회복, 걷기, 머물기 */
    private fun flow(start: GameState, dt: Double, random: Random): GameState {
        val h = start.hero
        val day = start.day + dt
        var s = start.copy(
            day = day,
            hero = h.copy(hp = min(h.maxHp, h.currentHp() + h.maxHp * HEAL_PER_DAY * dt)),
            incidents = start.incidents.filter { it.end > day },
        )
        if (s.recover > 0) return s.copy(recover = (s.recover - dt).coerceAtLeast(0.0))
        val t = s.travel
        if (t != null) {
            val moved = t.copy(progress = t.progress + dt)
            // 길 위의 사건(습격·맹수)이 있는 곳을 지나면 휘말린다
            val hit = s.incidents.firstOrNull { inc ->
                (inc.kind == IncidentKind.RAID || inc.kind == IncidentKind.BEAST) && inc.onRoad(t.from, t.to) &&
                    (if (inc.roadA == t.from) inc.at else 1 - inc.at).let { mark -> t.progress / t.total < mark && moved.progress / moved.total >= mark }
            }
            if (hit != null) return roadIncident(s.copy(travel = moved), hit, random)
            return if (moved.progress >= moved.total) visit(arrive(s, moved, random), random) else s.copy(travel = moved)
        }
        if (s.hold) return s
        s = s.copy(stay = s.stay - dt)
        return if (s.stay <= 0) depart(s, random) else s
    }

    /** 새 날: 수명, 길 위 사건, 돌파 */
    private fun daily(start: GameState, random: Random): GameState {
        var s = start
        val h = s.hero
        if (s.ageYears >= Realms.lifespan(h.realm)) {
            return s.copy(hero = h.copy(alive = false)).log("${s.ageYears}세, 천수를 다했다. 강호를 떠도는 길이 여기서 끝난다.")
        }
        val t = s.travel
        val patrolled = t != null && s.incidents.any { it.kind == IncidentKind.PATROL && it.onRoad(t.from, t.to) }
        if (t != null && !patrolled && s.recover <= 0 && random.nextDouble() < ENCOUNTER) s = encounter(s, t, random)
        while (s.hero.realm < Realms.last && s.hero.inner >= Realms.breakthroughCost(s.hero.realm)) {
            val hero = s.hero
            val next = hero.copy(inner = hero.inner - Realms.breakthroughCost(hero.realm), realm = hero.realm + 1, hp = -1.0)
            s = s.copy(hero = next).log("${Realms.name(next.realm)}의 경지에 올랐다! 모든 힘이 두 배가 되었다.")
        }
        return s
    }

    private fun encounter(s: GameState, t: Travel, random: Random): GameState {
        val danger = WorldMap.roadDanger(t.from, t.to)
        val r = random.nextDouble()
        return when {
            r < 0.55 -> {
                val f = FightSim.roadFight(s.hero, danger, random)
                s.copy(fight = f).log("${Josa.i(f.enemy.name)} 길을 막았다!")
            }
            r < 0.7 -> {
                val inner = 4.0 * danger
                s.copy(hero = s.hero.copy(inner = s.hero.inner + inner)).log("길섶에서 약초를 캤다. 내공 +${inner.toInt()}")
            }
            r < 0.8 -> {
                val silver = random.nextInt(5, 16) * danger
                s.copy(hero = s.hero.copy(silver = s.hero.silver + silver)).log("떨어진 돈주머니를 주웠다. 은자 +$silver")
            }
            else -> {
                val e = RoadEvents.all.random(random)
                s.copy(pending = (s.pending + Pending(e.id, s.day.toInt(), danger)).takeLast(MAX_PENDING)).log("${e.title}: 사건이 기다린다.")
            }
        }
    }

    /** 싸움이 끝났다 */
    fun finishFight(s: GameState, f: Fight): GameState {
        val h = s.hero
        return if (FightSim.won(f)) {
            s.copy(
                fight = null,
                hero = h.copy(hp = f.hero.hp, silver = h.silver + f.silver, inner = h.inner + f.inner, wins = h.wins + 1),
            ).log("${Josa.eul(f.enemy.name)} 물리쳤다. 은자 +${f.silver}, 내공 +${f.inner.toInt()}")
        } else {
            val lost = h.silver * 3 / 10
            s.copy(
                fight = null,
                recover = RECOVER_DAYS,
                hero = h.copy(hp = h.maxHp * 0.05, silver = h.silver - lost, losses = h.losses + 1),
            ).log("${Josa.ege(f.enemy.name)} 졌다. 은자 ${lost}냥을 빼앗기고 길가에서 상처를 돌본다.")
        }
    }

    private fun arrive(s: GameState, t: Travel, random: Random): GameState {
        val place = WorldMap.place(t.to)
        val first = t.to !in s.visited
        val base = s.copy(at = t.to, travel = null, cameFrom = t.from, visited = s.visited + t.to)
        val firstText = if (first) " 처음 와 보는 곳이다. ${place.summary}" else ""
        return when {
            s.destination == t.to -> base.copy(destination = null, hold = true).log("목적지 ${Josa.e(place.name)} 닿았다. 이곳에 머문다.$firstText")
            s.destination != null -> base.copy(stay = 0.3).log("${Josa.eul(place.name)} 지난다.$firstText")
            else -> base.copy(stay = random.nextDouble(1.0, 3.0)).log("${Josa.e(place.name)} 닿았다.$firstText")
        }
    }

    private fun depart(s: GameState, random: Random): GameState {
        val here = s.at ?: return s
        val next = nextStop(s, here, random) ?: return s.copy(stay = 1.0)
        val days = WorldMap.roadDays(here, next)
        val name = WorldMap.place(next).name
        return s.copy(at = null, travel = Travel(here, next, 0.0, days)).log("${Josa.ro(name)} 길을 나섰다.")
    }

    /** 다음 행선지: 목적지가 있으면 최단 경로, 없으면 방랑 (안 가 본 곳을 좋아하고, 너무 위험한 곳은 피한다) */
    fun nextStop(s: GameState, here: String, random: Random): String? {
        s.destination?.let { return WorldMap.path(here, it).getOrNull(1) }
        val all = WorldMap.neighbors(here)
        val safe = all.filter { FightSim.dangerRealm(WorldMap.place(it).danger) <= s.hero.realm + 1 }.ifEmpty { listOf(all.minBy { WorldMap.place(it).danger }) }
        val weights = safe.map { id ->
            val lively = s.incidents.any { it.place == id || it.onRoad(here, id) }
            (if (id in s.visited) 1.0 else 3.0) * (if (id == s.cameFrom && safe.size > 1) 0.3 else 1.0) * (if (lively) 4.0 else 1.0)
        }
        var roll = random.nextDouble() * weights.sum()
        for ((i, id) in safe.withIndex()) {
            roll -= weights[i]
            if (roll <= 0) return id
        }
        return safe.last()
    }

    // ── 지도 위 사건 ──

    private fun spawnIncident(s: GameState, random: Random): GameState {
        val next = s.copy(nextIncidentAt = s.day + Incidents.gap(random))
        if (s.incidents.size >= Incidents.MAX_ACTIVE) return next
        val inc = Incidents.spawn(s.nextIncidentId, s.day, s.incidents, random, s.position()) ?: return next
        return next.copy(incidents = next.incidents + inc, nextIncidentId = s.nextIncidentId + 1)
    }

    /** 길 위 사건에 휘말렸다: 습격·맹수는 싸움 */
    private fun roadIncident(s: GameState, inc: Incident, random: Random): GameState {
        val danger = WorldMap.roadDanger(inc.roadA!!, inc.roadB!!)
        val base = FightSim.roadFight(s.hero, danger, random)
        val f = when (inc.kind) {
            IncidentKind.RAID -> base.copy(enemy = base.enemy.copy(name = "산적 떼", sprite = "bandit"), silver = base.silver * 3)
            else -> base.copy(enemy = base.enemy.copy(name = "굶주린 늑대", sprite = "wolf", interval = 0.8), inner = base.inner * 2)
        }
        val text = if (inc.kind == IncidentKind.RAID) "산적 떼가 상단을 덮친 자리에 끼어들었다!" else "굶주린 늑대 떼와 마주쳤다!"
        return s.copy(fight = f, incidents = s.incidents - inc).log(text)
    }

    /** 장소에 닿았을 때 그곳의 사건 */
    private fun visit(s: GameState, random: Random): GameState {
        val here = s.at ?: return s
        val inc = s.incidents.firstOrNull { it.place == here } ?: return s
        val d = WorldMap.place(here).danger
        val h = s.hero
        val rest = s.copy(incidents = s.incidents - inc)
        return when (inc.kind) {
            IncidentKind.FESTIVAL -> rest.copy(hero = h.copy(hp = -1.0, inner = h.inner + 3.0 * d)).log("잔치에서 배불리 먹고 마셨다. 기혈이 가득 찼다. (내공 +${3 * d})")
            IncidentKind.MARKET -> {
                val pay = 10 * d + random.nextInt(0, 11)
                rest.copy(hero = h.copy(silver = h.silver + pay)).log("장날 짐을 날라 주었다. 품삯 은자 +$pay")
            }
            IncidentKind.FIRE -> rest.copy(hero = h.copy(silver = h.silver + 5 * d, inner = h.inner + 2.0 * d)).log("불을 끄는 걸 도왔다. 사람들이 고마워하며 은자 ${5 * d}냥을 쥐여 주었다.")
            IncidentKind.MASTER -> {
                val gain = Realms.breakthroughCost(h.realm) * 0.3
                rest.copy(hero = h.copy(inner = h.inner + gain)).log("기인에게 한 수 배웠다. 막혔던 혈이 트인다. (내공 +${gain.toInt()})")
            }
            IncidentKind.TOURNAMENT -> {
                val realm = h.realm
                val scale = 2.0.pow(realm) * random.nextDouble(0.75, 0.9)
                val foe = Fighter("비무대회 고수", "chief", 50 * scale, 50 * scale, 5 * scale, 1.1)
                rest.copy(fight = Fight(FightSim.heroFighter(h), foe, (60 * 1.5.pow(d)).toInt(), 5.0 * d)).log("비무대회에 나섰다!")
            }
            else -> s
        }
    }

    // ── 손으로 하는 일 ──

    /** 목적지를 정한다. 지금 머무는 곳이면 그곳에 머문다. 길 위라면 지금 길을 마친 뒤 경로를 따른다 */
    fun setDestination(s: GameState, id: String): GameState {
        if (s.at == id) return s.copy(destination = null, hold = true)
        val from = s.at ?: s.travel?.to ?: return s
        if (from != id && WorldMap.path(from, id).isEmpty()) return s
        val name = WorldMap.place(id).name
        return s.copy(destination = id, hold = false, stay = if (s.at != null) 0.0 else s.stay).log("${Josa.ro(name)} 가기로 했다.")
    }

    /** 목적지 없이 다시 떠돈다 */
    fun wander(s: GameState): GameState =
        s.copy(destination = null, hold = false, stay = if (s.at != null) 1.0 else s.stay).log("발길 닿는 대로 떠돌기로 했다.")

    fun choiceBlock(s: GameState, c: Choice): String? = if (s.hero.silver < c.cost) "은자 ${c.cost}냥이 필요하다" else null

    fun resolve(s: GameState, pending: Pending, choiceIndex: Int, random: Random): GameState {
        if (pending !in s.pending) return s
        val e = RoadEvents.event(pending.eventId)
        val c = e.choices[choiceIndex]
        if (choiceBlock(s, c) != null) return s
        val chance = c.chance + (c.check?.let { (s.hero.stat(it) - 10.5) * 0.04 } ?: 0.0)
        val o = if (random.nextDouble() < chance) c.success else c.failure
        val h = s.hero
        val stats = o.stat?.let { h.stats + (it to h.stat(it) + 1) } ?: h.stats
        val hero = h.copy(silver = h.silver - c.cost + o.silver, inner = h.inner + o.inner * pending.danger, stats = stats)
        val gains = listOfNotNull(
            if (o.inner > 0) "내공 +${(o.inner * pending.danger).toInt()}" else null,
            o.stat?.let { "${it.displayName} +1" },
        ).joinToString(", ")
        return s.copy(hero = hero, pending = s.pending - pending).log(o.text + if (gains.isNotEmpty()) " ($gains)" else "")
    }
}

object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(s: GameState): String = json.encodeToString(GameState.serializer(), s)

    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)
}
