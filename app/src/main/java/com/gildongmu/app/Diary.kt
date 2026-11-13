package com.gildongmu.app

import android.util.Log
import java.util.Calendar
import kotlin.math.abs

/**
 * 길동무의 말투와 일기 문장.
 * 지금은 기기 안에서 규칙과 문장 틀로 일기를 엮는다. (서버 AI 연결 시 같은 입력으로 교체 가능)
 */

enum class Mood(val label: String, val emoji: String, val weather: String) {
    JOY("기쁨", "☀️", "맑음"),
    FLUTTER("설렘", "🌸", "맑음"),
    CALM("잔잔함", "🌤", "맑음"),
    WORRY("걱정", "⛅", "흐림"),
    TIRED("지침", "🌫", "흐림"),
    SAD("슬픔", "🌧", "비"),
    ANGRY("속상함", "⛈", "비"),
}

object MoodReader {
    private val lexicon: Map<Mood, List<String>> = mapOf(
        Mood.JOY to listOf("좋았", "좋아", "좋다", "좋네", "행복", "기뻐", "기쁘", "신나", "웃", "칭찬", "잘됐", "잘 됐", "잘 끝", "성공", "고마", "감사", "다행", "뿌듯", "칼퇴"),
        Mood.FLUTTER to listOf("설레", "설렌", "설렜", "두근", "기대", "고백", "데이트", "처음", "보고 싶어서"),
        Mood.CALM to listOf("괜찮", "편안", "편하", "조용", "여유", "노을", "하늘", "바람", "음악", "노래", "평화"),
        Mood.WORRY to listOf("걱정", "긴장", "불안", "떨려", "떨리", "발표", "시험", "면접", "어떡", "무서"),
        Mood.TIRED to listOf("피곤", "힘들", "힘드", "지쳐", "지친", "졸려", "졸리", "야근", "늦게", "녹초", "깨지고", "깨졌"),
        Mood.SAD to listOf("슬퍼", "슬프", "울었", "울고", "울어", "눈물", "속상", "외로", "그리워", "보고 싶", "서운"),
        Mood.ANGRY to listOf("화나", "화가", "짜증", "싸웠", "싸움", "답답", "열받", "억울"),
    )

    fun read(text: String): Mood = score(text).first

    /**
     * 마음결과 확신 (0이면 짐작할 거리가 없어 '잔잔함'으로 둔 것).
     * 기기 안의 AI(문장 분류 모델, Ai.kt)가 판별한다. 모델을 읽지 못했을 때만 아래 낱말 사전으로 대신한다.
     */
    fun score(text: String): Pair<Mood, Int> {
        Ai.mood(text)?.let { (m, p) ->
            // 두 모델을 함께 본다: '남을 해치는 말' 모델이 공격적인 말로 본 문장은 즐거운 마음결로 읽지 않는다
            val hostile = (Ai.harm(text) ?: 0f) >= 0.6f && m in setOf(Mood.JOY, Mood.FLUTTER, Mood.CALM)
            // 확신이 낮으면(0.40 아래) 잔잔함으로 둔다: 사실을 말했을 뿐인 문장에 엉뚱한 마음결을 붙이지 않으려고
            val mood = if (hostile) Mood.ANGRY else if (p < 0.40f) Mood.CALM else m
            Log.d("Gildongmu", "AI 마음결: ${mood.label} %.2f${if (hostile) " (공격적인 말)" else ""}  \"${text.take(24)}\"".format(p))
            return mood to (if (hostile || p >= 0.40f) 1 else 0)
        }
        return lexicon(text)
    }

    private fun lexicon(text: String): Pair<Mood, Int> {
        var best = Mood.CALM
        var bestScore = 0
        for ((mood, words) in lexicon) {
            val score = words.sumOf { w -> Regex(Regex.escape(w)).findAll(text).count() }
            if (score > bestScore) { best = mood; bestScore = score }
        }
        return best to bestScore
    }
}

/** 기록 순간의 차 안 배경 */
data class Scene(
    val rain: Boolean = false,
    val night: Boolean = false,
    val phase: String = "parked", // depart | parked | arrive
    val alone: Boolean? = null,
    val view: String? = null,  // 그때 창밖에 걸려 있던 풍경 (SceneKind 이름)
    val light: String? = null, // 그때의 빛 (DayPart 이름)
)

object Words {
    fun partOfDay(hour: Int): String = when (hour) {
        in 5..8 -> "이른 아침"
        in 9..11 -> "오전"
        in 12..13 -> "한낮"
        in 14..17 -> "오후"
        in 18..20 -> "저녁"
        in 21..23 -> "밤"
        else -> "깊은 밤"
    }

    fun clock(cal: Calendar): String {
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$ampm ${h12}시" else "$ampm ${h12}시 ${m}분"
    }

    /**
     * 차에 탄 순간의 인사 (환영 장면에서 오늘 쪽에 적힌다): 큰 두 줄과 작은 한 줄.
     * 꾸미지 않은, 사람이 건넬 법한 말로.
     */
    fun welcome(name: String?, hour: Int): Triple<String, String, String> {
        val first = if (hour in 5..10) "좋은 아침이에요," else "어서 오세요,"
        val second = if (name.isNullOrBlank()) "반가워요." else "${name}님."
        val sub = when (hour) {
            in 5..10 -> "오늘도 잘 다녀와요."
            in 11..16 -> "오늘 하루, 잘 가고 있나요."
            in 17..20 -> "오늘도 수고 많았어요."
            else -> "늦게까지 수고 많았어요."
        }
        return Triple(first, second, sub)
    }

    fun greeting(name: String, hour: Int): Pair<String, String> = when (hour) {
        in 5..10 -> "좋은 아침이에요, ${name}님" to "오늘도 잘 다녀와요."
        in 11..16 -> "어서 오세요, ${name}님" to "오늘 하루, 잘 가고 있나요."
        in 17..20 -> "오늘도 수고 많았어요, ${name}님" to "집에 가는 길, 하고 싶은 말이 있으면 들려줘요."
        else -> "늦게까지 수고 많았어요, ${name}님" to "오늘 있었던 일, 한마디만 남기고 가요."
    }

    private fun pick(list: List<String>, seed: String): String = list[abs(seed.hashCode()) % list.size]

    /** 한 기록 옆에 붙는, 길동무의 한 줄 (마음결에 맞춰). 꾸미지 않은 말로 짧게. */
    fun reflect(mood: Mood, seed: String): String = pick(
        when (mood) {
            Mood.JOY -> listOf(
                "좋은 일이 있었네요. 듣는 저도 좋아요.",
                "오늘 같은 날은 오래 기억해 둘게요.",
                "목소리가 밝아서 좋네요.",
            )
            Mood.FLUTTER -> listOf(
                "설레는 일이 있다는 건 좋은 거죠.",
                "기다려지는 일이 생겼네요. 잘 적어 둘게요.",
                "두근거리는 게 여기까지 들려요.",
            )
            Mood.CALM -> listOf(
                "별일 없는 하루도 잘 적어 둘게요.",
                "이런 날이 사실 제일 좋은 날이죠.",
                "편안하게 들었어요.",
            )
            Mood.WORRY -> listOf(
                "걱정되는 게 당연해요. 잘하고 싶은 거니까요.",
                "일단 출발했잖아요. 절반은 한 거예요.",
                "막상 닥치면 생각보다 괜찮을 거예요.",
            )
            Mood.TIRED -> listOf(
                "오늘도 끝까지 왔어요. 그걸로 충분해요.",
                "애쓴 하루였어요. 정말로.",
                "오늘은 일찍 쉬어요. 그래도 되는 날이에요.",
            )
            Mood.SAD -> listOf(
                "말해 줘서 고마워요. 잘 적어 둘게요.",
                "울고 싶으면 울어도 돼요. 차 안이니까요.",
                "오늘은 여기 내려놓고 가요.",
            )
            Mood.ANGRY -> listOf(
                "그럴 만했어요. 화날 일이네요.",
                "속상한 건 속상한 거죠. 잘 적어 둘게요.",
                "숨 한 번 크게 쉬고, 창문 조금 열어 봐요.",
            )
        }, seed
    )

    /** 말 속에서 알아들은 낱말에 답하는 한 줄 (위에서부터 먼저 맞는 것) */
    private val heard: List<Pair<Regex, List<String>>> = listOf(
        // 마음이 많이 힘든 말에는 무엇보다 먼저, 곁에 있다는 말과 도움받을 곳을 건넨다 (자살예방 상담전화 109, 24시간)
        Regex("죽고 ?싶|자살|살기 ?싫|사라지고 ?싶|목숨을 ?끊|그만 ?살고 ?싶") to listOf("많이 힘들었군요. 혼자 견디지 않아도 돼요. 이야기할 곳이 필요하면 109에 전화해 보세요. 언제든 받아요."),
        Regex("야근.*(깨지|깨졌|혼나|혼났|욕먹|욕 먹|한 ?소리|털렸)|(깨지|깨졌|혼나|혼났|욕먹|욕 먹|한 ?소리|털렸).*야근") to listOf("혼나고, 야근까지. 오늘 정말 애쓰셨어요."),
        Regex("깨지|깨졌|혼나|혼났|욕먹|욕 먹|한 ?소리 (듣|들)|털렸") to listOf("그 말은 여기 내려놓고 가요. 오늘 애쓰셨어요."),
        Regex("야근") to listOf("야근까지 하고 가는 길이네요. 오늘 정말 애쓰셨어요."),
        Regex("칼퇴|정시 ?퇴근|일찍 퇴근|(일찍|빨리) ?끝났") to listOf("일찍 가는 날이네요. 이런 날은 천천히 누려요."),
        Regex("(엄마|아빠|어머니|아버지|부모님|할머니|할아버지).*(전화|통화|보고 싶)") to listOf("전화 한 통에 마음이 움직였네요. 잘 적어 둘게요."),
        Regex("라면|치킨|맥주|야식|떡볶이") to listOf("맛있게 먹어요. 오늘 하루 버틴 상이에요."),
        Regex("막히|막혀|막힌|막힌다") to listOf("길이 막히면 마음도 급해지죠. 천천히 가도 돼요."),
    )
    private val plain = listOf("들려줘서 고마워요. 잘 적어 둘게요.", "마음, 잘 담아 둘게요.", "오늘의 한마디, 잘 받았어요.")

    /**
     * 방금 들은 말에 길동무가 건네는 한 줄.
     * 알아들은 낱말이 있으면 그 말에 답하고, 없으면 마음결에 맞춘 한 줄을, 마음결도 짐작할 수 없으면 담담한 한 줄을 건넨다
     * (짐작이 틀린 위로를 건네느니 담담한 편이 낫다).
     */
    /**
     * 길게 말했을 때 글귀로 올릴 한 문장: 말을 문장으로 나눠, 그날의 마음결이 가장 짙게 실린 문장을 고른다.
     * 짧은 말은 그대로다. 일기에는 언제나 말 전체가 담기고, 큰 글귀 자리와 길 위에는 이 한 문장만 놓인다.
     */
    fun headline(text: String): String {
        val t = text.trim()
        if (t.length <= HEADLINE_MAX) return t
        // 음성 인식은 마침표를 찍어 주지 않을 때가 많다: 마침표가 없으면 '~다 · ~요 · ~어' 같은 말끝에서 나눈다
        val parts = Regex("(?<=[.!?…])\\s+|(?<=[가-힣](?:다|요|어|야|지|네|까|죠))\\s+").split(t).map { it.trim() }.filter { it.isNotEmpty() }
        val sentences = ArrayList<String>()
        for (p in parts) if (sentences.isNotEmpty() && sentences.last().length < 9) sentences[sentences.lastIndex] = sentences.last() + " " + p else sentences += p
        if (sentences.size <= 1) return clip(t)
        val whole = MoodReader.score(t).first
        val best = sentences.withIndex().maxByOrNull { (i, s) ->
            val m = Ai.mood(s)
            val p = if (m == null) 0.2f else if (m.first == whole) m.second else m.second * 0.5f
            p + (if (i == sentences.lastIndex) 0.05f else 0f) - (if (s.length > HEADLINE_MAX) 0.2f else 0f)
        }!!.value
        return clip(best)
    }
    private const val HEADLINE_MAX = 46
    private fun clip(s: String): String = if (s.length <= HEADLINE_MAX) s else s.take(HEADLINE_MAX).substringBeforeLast(' ').trimEnd() + "…"

    fun reply(text: String, seed: String): String {
        heard.firstOrNull { it.first.containsMatchIn(text) }?.let { return pick(it.second, seed) }
        // 욕이 섞인 말: 일기에는 그대로 담고, 털어놓은 것을 받아 준다
        if (Guard.swears(text)) return pick(listOf("속상한 날이었네요. 여기서는 마음껏 털어놔도 돼요.", "그럴 만한 날이었네요. 일기에는 그대로 담아 둘게요."), seed)
        val (mood, score) = MoodReader.score(text)
        return if (score > 0) reflect(mood, seed) else pick(plain, seed)
    }

    fun sceneLine(cal: Calendar, scene: Scene): String {
        val part = partOfDay(cal.get(Calendar.HOUR_OF_DAY))
        val road = when (scene.phase) {
            "depart" -> "길을 나서기 전"
            "arrive" -> "막 도착한 자리"
            else -> "잠시 멈춘 차 안"
        }
        val weather = if (scene.rain) "비가 창을 두드리던 " else ""
        return "${clock(cal)} · $weather$part, $road"
    }

    fun dayTitle(cal: Calendar, entries: List<Entry>): String {
        val md = "${cal.get(Calendar.MONTH) + 1}월 ${cal.get(Calendar.DAY_OF_MONTH)}일"
        val rain = entries.any { it.scene.rain }
        val night = entries.any { it.scene.night }
        val first = entries.firstOrNull()?.mood
        val last = entries.lastOrNull()?.mood
        val phrase = when {
            rain -> "비가 길을 적시던 날"
            first == Mood.WORRY && last == Mood.JOY -> "걱정이 웃음이 된 날"
            last == Mood.SAD -> "조금은 울고 싶던 날"
            last == Mood.ANGRY -> "마음이 흔들리던 날"
            last == Mood.FLUTTER -> "두근거림이 남은 날"
            last == Mood.JOY -> "햇살 같던 날"
            last == Mood.TIRED -> "고단했지만 끝까지 달린 날"
            night -> "밤이 길었던 날"
            else -> "잔잔하게 흘러간 날"
        }
        return "$md, $phrase"
    }

    /** 하루를 한 문장으로 */
    fun dayLine(entries: List<Entry>): String {
        if (entries.isEmpty()) return "말없이 달린 날도, 길동무는 곁에 있었어요."
        val a = entries.first().mood
        val b = entries.last().mood
        if (entries.size == 1) return when (a) {
            Mood.JOY -> "웃음 하나를 싣고 달린 하루."
            Mood.FLUTTER -> "설렘 하나를 싣고 달린 하루."
            Mood.CALM -> "잔잔한 마음으로 달린 하루."
            Mood.WORRY -> "걱정을 안고도 끝내 출발한 하루."
            Mood.TIRED -> "고단함까지 싣고 끝까지 달린 하루."
            Mood.SAD -> "눈물 한 방울을 길 위에 내려둔 하루."
            Mood.ANGRY -> "흔들리던 마음을 다독이며 달린 하루."
        }
        // 모든 감정 이름이 받침으로 끝나서 '으로/이'가 자연스럽다
        return if (a == b) "${a.label}이 길게 이어진 하루."
        else "${a.label}으로 시작해 ${b.label}으로 돌아온 하루."
    }

    const val closing = "내일의 길도, 곁에서 함께 달릴게요."

    // ---------------------------------------------------------------- 녹음 화면

    const val listening = "듣고 있어요. 천천히 말해도 괜찮아요."
    const val saved = "마음, 잘 담아 둘게요."
    /** 길 위에 올리지 않은 말(개인 정보·광고)에 건네는 담담한 답 */
    const val received = "오늘의 한마디, 잘 받았어요."
    private val tidyAlways = listOf(
        Regex("됬") to "됐", Regex("되요") to "돼요", Regex("되서") to "돼서", Regex("되야") to "돼야",
        Regex("돼(고|는|면|니까|도록)") to "되$1",
    )
    private val tidyEnd = Regex("되(?=\\s*[.!?…,]|\\s*$)")
    private val measure = Regex("(한|두|세|네|닷|몇) 되$")

    /**
     * 받아 적은 글의 흔한 맞춤법 잘못을 바로잡는다 (음성 인식은 소리 나는 대로 적기도 한다).
     * '되'로 끝난 말은 '돼'로 (긴장되 → 긴장돼, 안 되 → 안 돼), 되요 → 돼요, 됬 → 됐, 되서 → 돼서, 되야 → 돼야, 돼고·돼는·돼면 → 되고·되는·되면.
     */
    fun tidy(text: String): String {
        var t = text
        for ((re, to) in tidyAlways) t = re.replace(t, to)
        return tidyEnd.replace(t) { m -> if (measure.containsMatchIn(t.substring(0, m.range.last + 1))) "되" else "돼" }
    }

    /** 거친 말을 담지 않고, 다듬어서 다시 말해 달라고 청하는 말 (Guard.notice 뒤에 붙는다) */
    const val again = "조금만 다듬어서, 한 번만 더 들려줄래요?"
    /** 다시 들은 말도 거칠 때: 더 청하지 않고 맡긴다 */
    const val later = "마음이 조금 가라앉으면, 그때 다시 들려주세요."
    const val emptyToday = "아직 오늘의 이야기가 없어요.\n말하기 버튼을 누르고,\n지금 마음을 한마디만 들려주세요."
}

/** AI 길동무가 먼저 말을 거는 순간 */
data class Ask(val key: String, val text: String)

object AskTemplates {
    private fun span(min: Int) = when {
        min < 75 -> "한 시간쯤"
        min < 105 -> "한 시간 반쯤"
        min < 150 -> "두 시간쯤"
        else -> "${(min + 30) / 60}시간쯤"
    }
    fun earlyStart(minutes: Int) = Ask("early", "평소보다 ${span(minutes)} 일찍 길을 나섰네요. 오늘은 어떤 하루가 기다리고 있나요?")
    fun lateStart(minutes: Int) = Ask("late", "평소보다 ${span(minutes)} 늦은 출발이네요. 천천히 가도 괜찮아요. 무슨 일이 있었나요?")
    /** 저녁에 평소보다 한참 늦게 나서는 길: 야근 뒤의 퇴근 */
    fun lateLeave(minutes: Int) = Ask("leave", "오늘은 평소보다 ${span(minutes)} 늦게 퇴근하시네요. 오늘 하루, 많이 고단하셨나요?")
    val rain = Ask("rain", "창밖에 비가 내리네요. 빗소리 같은 마음이 있다면 들려줄래요?")
    val lateNight = Ask("night", "늦은 밤이에요. 오늘 하루, 정말 애썼어요. 마음에 남은 이야기가 있나요?")
    val longDrive = Ask("far", "꽤 먼 길을 달려왔어요. 이 길 끝에서 무엇을 만났나요?")
    val longIdle = Ask("idle", "잠시 쉬어 가는 중이네요. 하고 싶은 말이 있다면, 천천히 들을게요.")
    val longTime = Ask("days", "오랜만이에요. 그동안 어떻게 지냈어요?")
    val morning = Ask("morning", "좋은 아침이에요. 오늘의 첫 길, 어떤 마음으로 시작할까요?")
    val evening = Ask("evening", "오늘 하루, 어땠나요? 한마디면 충분해요.")
    /** 낮에 차에 탔을 때 */
    val daytime = Ask("day", "오늘 하루, 어떻게 가고 있나요? 한마디면 충분해요.")
    // 늘 가는 곳(Haunts)으로 알아본 순간들
    /** 저녁에 일터에서 나설 때 */
    val leaveWork = Ask("offwork", "퇴근길이네요. 오늘 하루, 어땠나요?")
    /** 저녁에 집에 닿았을 때 */
    val homeArrive = Ask("home", "집에 다 왔어요. 오늘 하루, 어땠나요? 한마디면 충분해요.")
    /** 평일 저녁, 집이 아닌 곳에 닿았을 때 */
    val detour = Ask("detour", "오늘은 집으로 바로 안 가셨네요. 특별한 약속이라도 있나요?")
    /** 운행 기록에 없던 곳에 닿았을 때 */
    val newPlace = Ask("newplace", "처음 와 보는 곳이네요. 오늘은 특별한 일이 있나요?")
    /** 저녁 귀가: 더 남길지, 오늘을 마무리할지 */
    fun wrapUp(n: Int) = Ask(WRAP, "오늘 마음 ${n}개를 남겼어요. 더 남기고 싶은 이야기가 있나요, 아니면 오늘은 여기서 마무리할까요?")
    const val WRAP = "wrap"
}
