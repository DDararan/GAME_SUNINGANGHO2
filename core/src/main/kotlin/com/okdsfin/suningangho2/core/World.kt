package com.okdsfin.suningangho2.core

import kotlin.math.ceil
import kotlin.math.hypot

/** 장소 종류 (기획서 2장) */
enum class PlaceType(val displayName: String) {
    VILLAGE("마을"),
    TOWN("현성"),
    CITY("도시"),
    CASTLE("성채"),
    SECT("문파"),
}

/** 지도 위 장소. [x], [y]는 240×160 세계 픽셀 좌표. [danger] 1~5: 근처 길의 위험 */
data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val x: Int,
    val y: Int,
    val danger: Int,
    val summary: String,
)

/** 두 장소를 잇는 길. 거리 10픽셀당 하루 */
data class Road(val a: String, val b: String) {
    val days: Int get() = WorldMap.roadDays(a, b)
}

/** 강호 지도 (기획서 2장). 모든 장소는 가상이다 */
object WorldMap {
    const val WIDTH = 240
    const val HEIGHT = 160
    const val START = "cheongun"

    val places: List<Place> = listOf(
        Place("cheongun", "청운촌", PlaceType.VILLAGE, 30, 128, 1, "산자락의 작은 마을. 당신이 나고 자란 곳."),
        Place("baekseok", "백석촌", PlaceType.VILLAGE, 72, 146, 1, "흰 돌이 많은 강가 마을. 돌을 캐는 사람들이 산다."),
        Place("hayang", "하양현", PlaceType.TOWN, 60, 102, 1, "남쪽 길목의 현성. 객잔과 장터가 있다."),
        Place("songrim", "송림촌", PlaceType.VILLAGE, 98, 72, 2, "소나무 숲 속 마을. 숯을 굽는 연기가 오른다."),
        Place("changgung", "창궁검파", PlaceType.SECT, 84, 40, 2, "구름 위 봉우리의 검파. 하늘을 겨누는 검을 가르친다."),
        Place("heukpung", "흑풍채", PlaceType.CASTLE, 34, 56, 4, "검은 바람의 산채. 산적들이 길손을 노린다."),
        Place("unha", "운하진", PlaceType.TOWN, 112, 120, 2, "강을 낀 나루. 배와 짐이 모인다."),
        Place("cheonyang", "천양성", PlaceType.CITY, 134, 88, 2, "중원의 큰 성. 관청과 큰 시장, 무관이 있다."),
        Place("noeul", "노을촌", PlaceType.VILLAGE, 152, 140, 2, "노을이 고운 들판 마을."),
        Place("geumgang", "금강사", PlaceType.SECT, 168, 56, 3, "산중 고찰. 무승들이 금강불괴를 닦는다."),
        Place("cheollo", "철로진", PlaceType.TOWN, 178, 96, 3, "쇠를 다루는 진. 대장간 망치 소리가 그치지 않는다."),
        Place("bukgwan", "북관", PlaceType.CASTLE, 128, 22, 4, "북쪽 변경의 관문. 관군이 지킨다."),
        Place("cheonrang", "천랑궁", PlaceType.SECT, 206, 22, 5, "초원 끝의 마궁. 늑대의 무리를 닮았다."),
        Place("seoreung", "서릉성", PlaceType.CITY, 204, 114, 3, "서쪽의 큰 성. 상단과 세가의 땅."),
        Place("nakhwa", "낙화검파", PlaceType.SECT, 186, 148, 3, "꽃잎처럼 흩날리는 쾌검의 검파."),
        Place("namhae", "남해항", PlaceType.TOWN, 224, 140, 3, "바다로 열린 항구. 먼 곳의 물건이 들어온다."),
    )

    val roads: List<Road> = listOf(
        Road("cheongun", "hayang"), Road("cheongun", "baekseok"), Road("hayang", "baekseok"),
        Road("hayang", "songrim"), Road("hayang", "heukpung"), Road("hayang", "unha"),
        Road("baekseok", "unha"), Road("songrim", "changgung"), Road("songrim", "heukpung"),
        Road("songrim", "cheonyang"), Road("changgung", "bukgwan"), Road("unha", "cheonyang"),
        Road("unha", "noeul"), Road("cheonyang", "geumgang"), Road("cheonyang", "cheollo"),
        Road("cheonyang", "bukgwan"), Road("geumgang", "cheollo"), Road("geumgang", "cheonrang"),
        Road("bukgwan", "cheonrang"), Road("cheollo", "seoreung"), Road("noeul", "nakhwa"),
        Road("seoreung", "nakhwa"), Road("seoreung", "namhae"), Road("nakhwa", "namhae"),
    )

    private val byId = places.associateBy { it.id }

    fun place(id: String): Place = byId.getValue(id)

    fun distance(a: String, b: String): Double {
        val p = place(a)
        val q = place(b)
        return hypot((p.x - q.x).toDouble(), (p.y - q.y).toDouble())
    }

    fun roadDays(a: String, b: String): Int = ceil(distance(a, b) / 10).toInt()

    fun neighbors(id: String): List<String> = roads.mapNotNull {
        when (id) {
            it.a -> it.b
            it.b -> it.a
            else -> null
        }
    }

    /** 최단 경로 (날짜 기준). [from]과 [to]를 포함한다 */
    fun path(from: String, to: String): List<String> {
        if (from == to) return listOf(from)
        val dist = mutableMapOf(from to 0)
        val prev = mutableMapOf<String, String>()
        val open = mutableSetOf(from)
        while (open.isNotEmpty()) {
            val cur = open.minBy { dist.getValue(it) }
            open.remove(cur)
            if (cur == to) break
            for (n in neighbors(cur)) {
                val d = dist.getValue(cur) + roadDays(cur, n)
                if (d < (dist[n] ?: Int.MAX_VALUE)) {
                    dist[n] = d
                    prev[n] = cur
                    open += n
                }
            }
        }
        if (to !in prev) return emptyList()
        val route = mutableListOf(to)
        while (route.last() != from) route += prev.getValue(route.last())
        return route.reversed()
    }

    /** 경로 전체 날수 */
    fun pathDays(route: List<String>): Int = route.zipWithNext().sumOf { (a, b) -> roadDays(a, b) }

    /** 길의 위험: 양 끝 중 높은 쪽 */
    fun roadDanger(a: String, b: String): Int = maxOf(place(a).danger, place(b).danger)
}
