package com.okdsfin.suningangho2.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** 지도 위에서 저절로 일어나는 일 (기획서 4장). 길 위([onRoad]) 또는 장소에서 */
@Serializable
enum class IncidentKind(val displayName: String, val onRoad: Boolean, val description: String) {
    RAID("산적 습격", true, "산적 떼가 상단을 덮쳤다. 지나가는 길이라면 끼어들 수 있다."),
    BEAST("맹수 출몰", true, "굶주린 늑대 떼가 길을 어슬렁거린다."),
    PATROL("관군 행군", true, "관군이 산적을 토벌하러 간다. 그 길은 한동안 안전하다."),
    FESTIVAL("잔치", false, "마을에 잔치가 열렸다. 등불이 걸리고 음식 냄새가 난다."),
    MARKET("장날", false, "장이 섰다. 짐을 나르면 품삯을 받을 수 있다."),
    TOURNAMENT("비무대회", false, "성에서 비무대회가 열렸다. 이기면 상금이 크다."),
    FIRE("불", false, "불이 났다! 불을 끄는 손이 모자란다."),
    MASTER("기인 출현", false, "이름 모를 고수가 머문다는 소문이 돈다. 운이 좋으면 한 수 배울 수 있다."),
}

@Serializable
data class Incident(
    val id: Int,
    val kind: IncidentKind,
    /** 장소에서 일어나면 그 장소 */
    val place: String? = null,
    /** 길 위에서 일어나면 그 길의 두 끝과 위치(0~1) */
    val roadA: String? = null,
    val roadB: String? = null,
    val at: Double = 0.5,
    val start: Double,
    val end: Double,
) {
    val title: String get() = place?.let { "${WorldMap.place(it).name} ${kind.displayName}" }
        ?: "${WorldMap.place(roadA!!).name}–${WorldMap.place(roadB!!).name} ${kind.displayName}"

    /** 지도 위 위치 */
    fun position(): Pair<Double, Double> = place?.let { WorldMap.place(it).let { p -> p.x.toDouble() to p.y.toDouble() } }
        ?: WorldMap.along(roadA!!, roadB!!, at)

    fun onRoad(a: String, b: String): Boolean = (roadA == a && roadB == b) || (roadA == b && roadB == a)

    /** 가 보려면 어디로 (길 위 사건은 가까운 끝) */
    val target: String get() = place ?: if (at < 0.5) roadA!! else roadB!!
}

object Incidents {
    const val MAX_ACTIVE = 6

    /** 다음 사건까지 (일) */
    fun gap(random: Random): Double = random.nextDouble(0.08, 0.25)

    /** 주인공 근처로 칠 거리 (세계 픽셀) */
    private const val NEAR = 110.0

    /** [near] 주인공 위치: 셋 중 둘은 그 근처에서 일어난다 (보이는 곳이 심심하지 않게) */
    fun spawn(id: Int, day: Double, active: List<Incident>, random: Random, near: Pair<Double, Double>? = null): Incident? {
        val kind = IncidentKind.entries.random(random)
        val local = near != null && random.nextDouble() < 0.6
        fun close(x: Double, y: Double) = !local || near == null || (x - near.first) * (x - near.first) + (y - near.second) * (y - near.second) <= NEAR * NEAR
        val duration = random.nextDouble(0.4, 1.2)
        if (kind.onRoad) {
            val roads = WorldMap.roads.filter { r ->
                active.none { it.onRoad(r.a, r.b) } && WorldMap.along(r.a, r.b, 0.5).let { (x, y) -> close(x, y) } && when (kind) {
                    IncidentKind.RAID -> WorldMap.roadDanger(r.a, r.b) >= 2
                    IncidentKind.BEAST -> WorldMap.roadDanger(r.a, r.b) >= 2
                    else -> true
                }
            }
            val r = roads.randomOrNull(random) ?: return null
            return Incident(id, kind, roadA = r.a, roadB = r.b, at = random.nextDouble(0.3, 0.7), start = day, end = day + duration)
        }
        val places = WorldMap.places.filter { p ->
            active.none { it.place == p.id } && close(p.x.toDouble(), p.y.toDouble()) && when (kind) {
                IncidentKind.FESTIVAL -> p.type == PlaceType.VILLAGE || p.type == PlaceType.TOWN
                IncidentKind.MARKET -> p.type == PlaceType.TOWN || p.type == PlaceType.CITY
                IncidentKind.TOURNAMENT -> p.type == PlaceType.CITY || p.type == PlaceType.CASTLE
                IncidentKind.FIRE -> p.type == PlaceType.VILLAGE || p.type == PlaceType.TOWN
                IncidentKind.MASTER -> p.type == PlaceType.SECT || p.type == PlaceType.VILLAGE
                else -> false
            }
        }
        val p = places.randomOrNull(random) ?: return null
        return Incident(id, kind, place = p.id, start = day, end = day + duration)
    }
}
