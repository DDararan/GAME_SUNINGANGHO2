package com.okdsfin.suningangho2.core

import kotlin.math.hypot

/** 장소 종류 (기획서 2장) */
enum class PlaceType(val displayName: String) {
    VILLAGE("마을"),
    TOWN("현성"),
    CITY("도시"),
    CASTLE("성채"),
    SECT("문파"),
}

/** 지도 위 장소. [x], [y]는 세계 픽셀 좌표. [danger] 1~5: 근처 길의 위험 */
data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val x: Int,
    val y: Int,
    val danger: Int,
    val summary: String,
)

/** 두 장소를 잇는 길 */
data class Road(val a: String, val b: String) {
    val days: Double get() = WorldMap.roadDays(a, b)
}

/** 강호 지도 (기획서 2장). 세로로 긴 땅: 북쪽 초원·설산, 가운데 평야와 강, 남쪽 바다. 모든 장소는 가상이다 */
object WorldMap {
    const val WIDTH = 256
    const val HEIGHT = 384
    const val START = "cheongun"

    /** 길을 지나는 속도: 100픽셀에 하루 (현실 약 4분) */
    private const val PX_PER_DAY = 100.0

    val places: List<Place> = listOf(
        // 북쪽
        Place("cheonrang", "천랑궁", PlaceType.SECT, 206, 30, 5, "초원 끝의 마궁. 늑대의 무리를 닮았다."),
        Place("bukgwan", "북관", PlaceType.CASTLE, 128, 46, 4, "북쪽 변경의 관문. 관군이 지킨다."),
        Place("hanbing", "한빙촌", PlaceType.VILLAGE, 52, 42, 3, "눈이 녹지 않는 산골 마을. 사냥꾼들이 산다."),
        Place("changgung", "창궁검파", PlaceType.SECT, 78, 96, 2, "구름 위 봉우리의 검파. 하늘을 겨누는 검을 가르친다."),
        Place("yeongbuk", "영북진", PlaceType.TOWN, 154, 86, 3, "북방 교역의 진. 말과 털가죽이 오간다."),
        Place("geumgang", "금강사", PlaceType.SECT, 196, 116, 3, "산중 고찰. 무승들이 금강불괴를 닦는다."),
        // 가운데
        Place("heukpung", "흑풍채", PlaceType.CASTLE, 30, 150, 4, "검은 바람의 산채. 산적들이 길손을 노린다."),
        Place("songrim", "송림촌", PlaceType.VILLAGE, 96, 150, 2, "소나무 숲 속 마을. 숯을 굽는 연기가 오른다."),
        Place("cheonyang", "천양성", PlaceType.CITY, 150, 196, 2, "중원의 큰 성. 관청과 큰 시장, 무관이 있다."),
        Place("cheollo", "철로진", PlaceType.TOWN, 212, 172, 3, "쇠를 다루는 진. 대장간 망치 소리가 그치지 않는다."),
        Place("hyeolwol", "혈월곡", PlaceType.SECT, 236, 226, 5, "붉은 달이 뜨는 골짜기. 혈월신교의 그림자가 서려 있다."),
        Place("hayang", "하양현", PlaceType.TOWN, 56, 236, 1, "남쪽 길목의 현성. 객잔과 장터가 있다."),
        Place("unha", "운하진", PlaceType.TOWN, 112, 250, 2, "강을 낀 나루. 배와 짐이 모인다."),
        Place("dowon", "도원촌", PlaceType.VILLAGE, 176, 236, 2, "복숭아꽃이 흐드러진 마을."),
        Place("seoreung", "서릉성", PlaceType.CITY, 214, 270, 3, "남서쪽의 큰 성. 상단과 세가의 땅."),
        // 남쪽
        Place("honggabo", "홍가보", PlaceType.CASTLE, 90, 294, 2, "홍씨 세가의 보루. 담이 높고 무사가 많다."),
        Place("cheongun", "청운촌", PlaceType.VILLAGE, 30, 318, 1, "산자락의 작은 마을. 당신이 나고 자란 곳."),
        Place("noeul", "노을촌", PlaceType.VILLAGE, 152, 318, 2, "노을이 고운 들판 마을."),
        Place("nakhwa", "낙화검파", PlaceType.SECT, 202, 330, 3, "꽃잎처럼 흩날리는 쾌검의 검파."),
        Place("baekseok", "백석촌", PlaceType.VILLAGE, 66, 350, 1, "흰 돌이 많은 강가 마을. 돌을 캐는 사람들이 산다."),
        Place("galmae", "갈매촌", PlaceType.VILLAGE, 118, 360, 1, "바닷가 어촌. 갈매기가 지붕마다 앉아 있다."),
        Place("namhae", "남해항", PlaceType.TOWN, 232, 352, 3, "바다로 열린 항구. 먼 곳의 물건이 들어온다."),
    )

    val roads: List<Road> = listOf(
        Road("cheongun", "hayang"), Road("cheongun", "baekseok"), Road("cheongun", "honggabo"),
        Road("baekseok", "honggabo"), Road("baekseok", "galmae"), Road("hayang", "honggabo"),
        Road("hayang", "heukpung"), Road("hayang", "unha"), Road("honggabo", "unha"),
        Road("galmae", "noeul"), Road("unha", "noeul"), Road("unha", "cheonyang"),
        Road("unha", "dowon"), Road("noeul", "dowon"), Road("noeul", "nakhwa"),
        Road("nakhwa", "namhae"), Road("nakhwa", "seoreung"), Road("namhae", "seoreung"),
        Road("dowon", "seoreung"), Road("dowon", "cheonyang"), Road("seoreung", "hyeolwol"),
        Road("cheollo", "hyeolwol"), Road("cheollo", "seoreung"), Road("cheonyang", "cheollo"),
        Road("cheonyang", "songrim"), Road("songrim", "heukpung"), Road("songrim", "changgung"),
        Road("cheonyang", "geumgang"), Road("cheollo", "geumgang"), Road("geumgang", "yeongbuk"),
        Road("yeongbuk", "bukgwan"), Road("changgung", "bukgwan"), Road("changgung", "hanbing"),
        Road("hanbing", "bukgwan"), Road("bukgwan", "cheonrang"), Road("yeongbuk", "cheonrang"),
        Road("heukpung", "hanbing"), Road("songrim", "yeongbuk"),
    )

    private val byId = places.associateBy { it.id }

    fun place(id: String): Place = byId.getValue(id)

    fun distance(a: String, b: String): Double {
        val p = place(a)
        val q = place(b)
        return hypot((p.x - q.x).toDouble(), (p.y - q.y).toDouble())
    }

    fun roadDays(a: String, b: String): Double = distance(a, b) / PX_PER_DAY

    fun neighbors(id: String): List<String> = roads.mapNotNull {
        when (id) {
            it.a -> it.b
            it.b -> it.a
            else -> null
        }
    }

    fun connected(a: String, b: String): Boolean = roads.any { (it.a == a && it.b == b) || (it.a == b && it.b == a) }

    /** 최단 경로 (날짜 기준). [from]과 [to]를 포함한다 */
    fun path(from: String, to: String): List<String> {
        if (from == to) return listOf(from)
        val dist = mutableMapOf(from to 0.0)
        val prev = mutableMapOf<String, String>()
        val open = mutableSetOf(from)
        while (open.isNotEmpty()) {
            val cur = open.minBy { dist.getValue(it) }
            open.remove(cur)
            if (cur == to) break
            for (n in neighbors(cur)) {
                val d = dist.getValue(cur) + roadDays(cur, n)
                if (d < (dist[n] ?: Double.MAX_VALUE)) {
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
    fun pathDays(route: List<String>): Double = route.zipWithNext().sumOf { (a, b) -> roadDays(a, b) }

    /** 길의 위험: 양 끝 중 높은 쪽 */
    fun roadDanger(a: String, b: String): Int = maxOf(place(a).danger, place(b).danger)

    /** 길 위 한 점 (0 = a, 1 = b) */
    fun along(a: String, b: String, t: Double): Pair<Double, Double> {
        val p = place(a)
        val q = place(b)
        return p.x + (q.x - p.x) * t to p.y + (q.y - p.y) * t
    }
}
