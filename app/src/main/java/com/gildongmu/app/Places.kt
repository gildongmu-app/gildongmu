package com.gildongmu.app

import kotlin.math.roundToInt

/** 길 위에 남겨진 것: 짧은 한마디(별), 공유한 일기(하트), 주의 알림 */
enum class PlaceKind(val label: String) { VOICE("한마디"), DIARY("공유 일기"), CAUTION("주의") }

/** 한 장소에 쌓인 한마디 하나 */
data class Word(
    val id: String, val author: String, val time: Long, val text: String, val mood: Mood,
    val likes: Int, val mine: Boolean = false,
)

/** 누군가 이 자리에서 쓰고, 다른 길동무에게 공유한 일기 */
data class SharedDiary(val title: String, val body: String, val time: Long, val author: String, val mood: Mood, val likes: Int)

data class Place(
    val id: String, val name: String, val lat: Double, val lng: Double, val kind: PlaceKind,
    val words: List<Word>, val diary: SharedDiary? = null,
) {
    /** 처음 남겨진 말 (뒤의 말들은 이어 남긴 한마디) */
    val lead: Word? get() = words.firstOrNull()
    val mood: Mood get() = diary?.mood ?: lead?.mood ?: Mood.CALM
    val latest: Long get() = maxOf(diary?.time ?: 0L, words.maxOfOrNull { it.time } ?: 0L)
    val likes: Int get() = (diary?.likes ?: 0) + words.sumOf { it.likes }
    val hasMine: Boolean get() = words.any { it.mine }
}

/** 내가 길 위에 남긴 한마디 (기기에 저장). placeId 가 있으면 그 장소에 이어 남긴 말 */
data class MyWord(
    val id: String, val placeId: String?, val name: String, val lat: Double, val lng: Double,
    val text: String, val mood: Mood, val time: Long,
)

data class PlaceTarget(val placeId: String?, val name: String, val lat: Double, val lng: Double)

/** GPS 한 점 */
data class Fix(val lat: Double, val lng: Double, val bearing: Float, val time: Long, val speed: Float = 0f)

/** 지나가며 자동으로 띄운 이야기 */
data class Popup(val placeId: String, val at: Long, val auto: Boolean)

object Places {
    /** 시연 위치: 성수동1가, 서울숲 서쪽 뚝섬로 (실제로는 GPS·내비가 알려 주는 현재 위치) */
    const val HERE_LAT = 37.543551
    const val HERE_LNG = 127.03731
    const val HERE_NAME = "성수동1가 · 서울숲 앞"

    /** 시연 주행 경로: 뚝섬로를 따라 서울숲 → 성수동 (실제 도로 형상, 약 1.6km) */
    val DEMO_DRIVE: List<Pair<Double, Double>> = doubleArrayOf(
        37.543551, 127.03731, 37.543401, 127.037877, 37.543231, 127.038455, 37.542453, 127.041206, 37.542376, 127.041552,
        37.541989, 127.043356, 37.541948, 127.043553, 37.541923, 127.043684, 37.541874, 127.044079, 37.541854, 127.044246,
        37.541818, 127.044358, 37.541326, 127.045873, 37.541234, 127.04615, 37.54109, 127.046634, 37.540643, 127.04808,
        37.5404, 127.04891, 37.540324, 127.049222, 37.54023, 127.04961, 37.540015, 127.050454, 37.539922, 127.050835,
        37.539716, 127.051681, 37.539411, 127.05294, 37.539235, 127.053582, 37.539188, 127.053739, 37.539171, 127.053797,
        37.53885, 127.05488,
    ).let { a -> List(a.size / 2) { a[it * 2] to a[it * 2 + 1] } }

    /** 시연용 운행 기록의 두 자리: 일터(서울숲 앞)와 집(성수동2가). 어느 한마디의 '가까이 왔어요' 범위에도 들지 않는 곳으로 잡았다 */
    val DEMO_WORK = 37.54367 to 127.04300
    val DEMO_HOME = 37.53680 to 127.05700

    /** 지나갈 때 저절로 보여 줄 거리 (미터) */
    fun radius(p: Place) = if (p.kind == PlaceKind.CAUTION) 260f else 130f

    /** 시연용: 다른 길동무들이 남긴 이야기 (실제로는 서버에서 받아 온다) */
    fun seed(now: Long): List<Place> {
        fun ago(min: Int) = now - min * 60_000L
        fun w(id: String, author: String, min: Int, text: String, mood: Mood, likes: Int) = Word(id, author, ago(min), text, mood, likes)
        return listOf(
            // ---- 뚝섬로를 따라 (지나가면 저절로 떠오르는 것들)
            Place(
                "p_forest_gate", "서울숲 정문 앞 신호", 37.54362, 127.03872, PlaceKind.VOICE, listOf(
                    w("w1", "숲길 산책러", 190, "여기 신호가 길어요. 창문 열면 숲 냄새가 나요.", Mood.CALM, 34),
                    w("w2", "초보운전 3주차", 70, "신호 기다리면서 나무 보는 게 요즘 출근길 낙이에요.", Mood.JOY, 12),
                    w("w3", "익명의 길동무", 25, "비 온 다음 날엔 냄새가 더 진해요. 오늘이 그래요.", Mood.CALM, 8),
                ),
            ),
            Place(
                "p_rain", "서울숲 사거리 신호", 37.542453, 127.041206, PlaceKind.VOICE, listOf(
                    w("wr1", "동네 할아버지", 62, "여기 지금 비가 오네요. 다들 조심해서 다니세요.", Mood.CALM, 23),
                ),
            ),
            Place(
                "p_kid", "뚝섬로 · 서울숲 주차장", 37.54214, 127.04300, PlaceKind.DIARY, emptyList(),
                SharedDiary(
                    "아이랑 처음 가 본 서울숲",
                    "뒷자리에서 아이가 창밖 나무를 세며 노래를 불렀다. 주차할 곳을 찾느라 한참 돌았지만, " +
                        "돌아오는 길에 잠든 아이 얼굴을 보니 다 괜찮았다. 다음엔 도시락을 싸 와야지.",
                    ago(5 * 1440 + 90), "주말 아빠", Mood.JOY, 57,
                ),
            ),
            Place(
                "p_works", "뚝섬로 2차로 공사", 37.54096, 127.04698, PlaceKind.CAUTION, listOf(
                    w("w7", "출근길 기사님", 38, "2차로 공사 중이에요. 미리 1차로로 바꾸세요.", Mood.WORRY, 17),
                    w("w8", "익명의 길동무", 9, "지금도 공사 중이에요. 확인했어요.", Mood.WORRY, 4),
                ),
            ),
            Place(
                "p_ilro", "성수일로 입구", 37.54048, 127.04945, PlaceKind.VOICE, listOf(
                    w("w16", "출근길 운전자", 360, "끼어들기 때문에 속상했는데, 앞차가 비상등으로 미안하대요. 금방 풀렸어요.", Mood.ANGRY, 15),
                    w("w17", "익명의 길동무", 120, "그런 작은 인사가 하루를 바꾸죠.", Mood.JOY, 6),
                ),
            ),
            Place(
                "p_shoes", "성수동 수제화 거리", 37.53962, 127.05232, PlaceKind.VOICE, listOf(
                    w("w11", "새내기 직장인", 130, "첫 출근하는 날이에요. 다들 좋은 하루 보내세요!", Mood.FLUTTER, 21),
                    w("w12", "옆자리 길동무", 95, "첫 출근 축하해요. 오늘 잘하실 거예요.", Mood.JOY, 9),
                ),
            ),
            Place(
                "p_cheer", "뚝섬로 · 퇴근길 신호", 37.53905, 127.05420, PlaceKind.VOICE, listOf(
                    w("wc1", "익명의 길동무", 34, "오늘 모두 야근 고생하셨고, 집 가서 행복하게 사랑하는 사람과 마무리하세요.", Mood.JOY, 128),
                    w("wc2", "야근 3일째", 12, "이 말 듣고 울 뻔했어요. 고맙습니다.", Mood.JOY, 31),
                ),
            ),
            // ---- 주변 (주차 중에 둘러보는 것들)
            Place(
                "p_interview", "서울숲역 앞", 37.54330, 127.04520, PlaceKind.VOICE, listOf(
                    w("w4", "면접 가는 길", 42, "면접 보러 가는 길이에요. 떨리지만 잘할 수 있겠죠?", Mood.WORRY, 27),
                    w("w5", "옆 차선 길동무", 31, "할 수 있어요. 숨 한 번 크게 쉬고 들어가요.", Mood.JOY, 15),
                    w("w6", "익명의 길동무", 12, "저도 작년에 여기 지나서 면접 붙었어요. 좋은 길이에요.", Mood.JOY, 11),
                ),
            ),
            Place(
                "p_ramp", "강변북로 진입로", 37.53990, 127.04150, PlaceKind.CAUTION, listOf(
                    w("w9r", "동네 주민", 240, "합류 구간이 짧아요. 깜빡이 미리 켜고 천천히 들어가세요.", Mood.WORRY, 12),
                ),
            ),
            Place(
                "p_lake", "서울숲 호수", 37.54420, 127.03860, PlaceKind.DIARY, emptyList(),
                SharedDiary(
                    "오랜만에 해 있을 때 퇴근한 날",
                    "해가 있을 때 퇴근한 게 얼마 만인지 모르겠다. 서울숲 옆을 지나는데 바람이 좋아서 일부러 천천히 달렸다. " +
                        "호수 쪽으로 산책 나온 사람들이 보였고, 나도 주말엔 걸어 보기로 했다. 아무 일도 없었지만, 그래서 좋은 날.",
                    ago(3 * 1440 + 200), "숲길 운전자", Mood.CALM, 48,
                ),
            ),
            Place(
                "p_bridge", "성수대교 북단", 37.53960, 127.03540, PlaceKind.VOICE, listOf(
                    w("w9", "퇴근길 고양이", 300, "다리 건너면서 본 노을이 오늘따라 진했어요.", Mood.JOY, 52),
                    w("w10", "익명의 길동무", 2900, "한강 보니까 하루가 조금 가벼워져요.", Mood.CALM, 19),
                ),
            ),
            Place(
                "p_cafe", "연무장길 카페거리", 37.54362, 127.05430, PlaceKind.DIARY, emptyList(),
                SharedDiary(
                    "엄마 생신",
                    "엄마 생신 케이크를 사러 성수동에 왔다. 주차할 데가 없어서 골목을 세 바퀴나 돌았다. " +
                        "그래도 케이크를 받아 든 엄마가 소녀처럼 웃어서 다 괜찮았다. 내년엔 미리 예약해 둬야지.",
                    ago(6 * 1440 + 400), "막내딸", Mood.JOY, 63,
                ),
            ),
            Place(
                "p_ttukseom", "뚝섬역 사거리", 37.54720, 127.04710, PlaceKind.VOICE, listOf(
                    w("w13t", "퇴근길 길동무", 400, "뚝섬역 앞 붕어빵 트럭이 돌아왔어요. 겨울이 왔나 봐요.", Mood.JOY, 22),
                ),
            ),
            Place(
                "p_eungbong", "응봉산 아래", 37.55020, 127.03520, PlaceKind.VOICE, listOf(
                    w("w13", "야근러", 540, "힘든 하루였는데, 여기서 한강 야경 보니까 조금 괜찮아졌어요.", Mood.SAD, 41),
                ),
            ),
            Place(
                "p_wangsimni", "왕십리로 고가 아래", 37.55080, 127.04410, PlaceKind.CAUTION, listOf(
                    w("w14", "동네 주민", 240, "비 오면 이 구간에 물이 고여요. 천천히 지나가세요.", Mood.WORRY, 9),
                ),
            ),
            Place(
                "p_seongsu", "성수역 고가 아래", 37.54460, 127.05600, PlaceKind.VOICE, listOf(
                    w("w15", "익명의 길동무", 1500, "야근 끝. 머리 위로 지하철 지나가는 소리가 오늘은 위로가 되네요.", Mood.TIRED, 19),
                ),
            ),
            Place(
                "p_yeongdong", "영동대교 북단", 37.53500, 127.05990, PlaceKind.DIARY, emptyList(),
                SharedDiary(
                    "새벽 배송",
                    "새벽 4시의 강변북로는 조용하다. 라디오에서 좋아하는 노래가 나와서 혼자 크게 따라 불렀다. " +
                        "다리 위에서 해가 뜨기 시작했다. 오늘도 무사히, 한 집 한 집.",
                    ago(2 * 1440 + 600), "새벽 배송 기사님", Mood.CALM, 88,
                ),
            ),
        )
    }

    fun merge(seed: List<Place>, mine: List<MyWord>, me: String): List<Place> {
        val list = seed.toMutableList()
        for (w in mine.sortedBy { it.time }) {
            val word = Word(w.id, "나 · $me", w.time, w.text, w.mood, 0, mine = true)
            val idx = list.indexOfFirst { it.id == w.placeId }
            if (idx >= 0) list[idx] = list[idx].copy(words = list[idx].words + word)
            else list += Place("my_${w.id}", w.name, w.lat, w.lng, PlaceKind.VOICE, listOf(word))
        }
        return list
    }

    fun ago(t: Long, now: Long): String {
        val m = ((now - t) / 60_000L).coerceAtLeast(0)
        return when {
            m < 1 -> "방금"
            m < 60 -> "${m}분 전"
            m < 24 * 60 -> "${m / 60}시간 전"
            m < 48 * 60 -> "어제"
            else -> "${m / (24 * 60)}일 전"
        }
    }

    fun distance(meters: Float): String =
        if (meters < 1000f) "${(meters / 10f).roundToInt() * 10}m" else "%.1fkm".format(meters / 1000f)
}

// ================================================================ 친구

/** 같은 길동무 앱을 쓰는 친구 */
data class Friend(val id: String, val name: String, val colorIdx: Int, val motif: Motif, val code: String)

/** 친구가 남긴 오늘의 한마디 */
data class FriendLine(val id: String, val friendId: String, val time: Long, val text: String, val mood: Mood, val likes: Int, val where: String? = null)

object Friends {
    fun seed(): List<Friend> = listOf(
        Friend("f_doyun", "도윤", 2, Motif.Wave, "GD-2817"),
        Friend("f_seoyeon", "서연", 1, Motif.Bloom, "GD-5530"),
        Friend("f_hyunwoo", "현우", 3, Motif.Leaf, "GD-9142"),
        Friend("f_haeun", "하은", 4, Motif.Star, "GD-3368"),
    )

    /** 코드로 찾을 수 있는 친구 (시연용) */
    val directory = listOf(Friend("f_jiho", "지호", 5, Motif.Spark, "GD-7302"))

    fun lines(now: Long): List<FriendLine> {
        fun ago(min: Int) = now - min * 60_000L
        return listOf(
            FriendLine("l1", "f_haeun", ago(12), "드디어 운전면허 붙었어! 첫 드라이브는 한강으로 갈래.", Mood.FLUTTER, 8, "잠실 한강공원"),
            FriendLine("l2", "f_hyunwoo", ago(48), "야근 확정… 그래도 퇴근길 라디오는 내가 고른다.", Mood.TIRED, 3, "판교"),
            FriendLine("l3", "f_seoyeon", ago(150), "점심에 회사 앞에서 붕어빵 샀어. 진짜 겨울 시작인가 봐.", Mood.JOY, 5, "을지로"),
            FriendLine("l4", "f_doyun", ago(330), "출근길에 무지개 봤어. 다들 오늘 운 좋을 듯!", Mood.JOY, 6, "강변북로"),
            FriendLine("l5", "f_jiho", ago(20), "오늘 처음으로 아빠 차 혼자 몰고 출근했다. 손에 땀이 흥건.", Mood.WORRY, 2, "성수동"),
        )
    }

    fun myCode(profileId: String): String = "GD-" + (1000 + (profileId.hashCode().ushr(1) % 9000)).toString()
}
