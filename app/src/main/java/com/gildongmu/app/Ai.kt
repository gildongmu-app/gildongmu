package com.gildongmu.app

import android.content.Context
import android.util.Log
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 길동무의 AI — 기기 안에서 인터넷 없이 도는 작은 문장 분류 모델 두 개.
 *  - 마음결: 말을 읽고 기쁨·설렘·잔잔함·걱정·지침·슬픔·속상함 가운데 하나로 판별한다.
 *  - 해치는 말: 남을 공격하거나 위협하는 말인지 판별한다 (길 위·친구처럼 남에게 보이는 자리에 올리기 전에).
 *
 * 모델은 글자 1~3개 묶음(n-gram)을 특징으로 한 로지스틱 회귀다. tools/ai/train.py 가 예시 문장(tools/ai/corpus.txt)으로
 * 학습해 assets/ai/ 에 넣는다. 글을 다듬고 특징을 뽑는 방식은 그 스크립트와 똑같아야 한다.
 * 말은 기기 밖으로 나가지 않는다. 서버의 대형 언어 모델을 붙일 때는 mood()·harm() 자리에 바꿔 끼우면 된다.
 */
object Ai {
    class Model(val classes: List<String>, private val bias: FloatArray, private val w: HashMap<String, FloatArray>) {
        val size: Int get() = w.size
        fun predict(text: String): FloatArray {
            val fs = feats(text)
            val v = 1f / sqrt(maxOf(1, fs.size).toFloat())
            val z = bias.copyOf()
            for (f in fs) w[f]?.let { row -> for (k in z.indices) z[k] += row[k] * v }
            val mx = z.max()
            var sum = 0f
            for (k in z.indices) { z[k] = exp(z[k] - mx); sum += z[k] }
            for (k in z.indices) z[k] /= sum
            return z
        }
    }

    private var moodModel: Model? = null
    private var harmModel: Model? = null

    fun init(context: Context) {
        if (moodModel != null) return
        moodModel = load(context, "ai/mood.tsv")
        harmModel = load(context, "ai/harm.tsv")
        Log.d("Gildongmu", "AI 모델: 마음결 특징 ${moodModel?.size ?: 0}개, 해치는 말 특징 ${harmModel?.size ?: 0}개")
    }

    private fun load(context: Context, path: String): Model? = runCatching {
        var classes = emptyList<String>()
        var bias = FloatArray(0)
        val w = HashMap<String, FloatArray>(16_384)
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                val key = line.substring(0, tab)
                val nums = line.substring(tab + 1).split(' ')
                when (key) {
                    "#classes" -> classes = nums
                    "#bias" -> bias = FloatArray(nums.size) { nums[it].toFloat() }
                    else -> w[key.replace('_', ' ')] = FloatArray(nums.size) { nums[it].toFloat() }   // (칸은 _ 로 적어 두었다)
                }
            }
        }
        require(classes.isNotEmpty() && bias.size == classes.size) { "모델 머리가 비었음" }
        Model(classes, bias, w)
    }.onFailure { Log.w("Gildongmu", "AI 모델을 읽지 못함: $path ${it.message}") }.getOrNull()

    /** 한글·영문 소문자·숫자만 남기고, 나머지는 칸 하나로 */
    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            val o = ch.code
            val ok = o in 0xAC00..0xD7A3 || o in 0x3131..0x318E || ch in 'a'..'z' || ch in '0'..'9'
            sb.append(if (ok) ch else ' ')
        }
        return sb.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** 글자 1~3개 묶음 (낱말의 처음과 끝을 알 수 있게 앞뒤에 칸을 붙인다) */
    fun feats(s: String): Set<String> {
        val t = " " + norm(s) + " "
        val fs = HashSet<String>()
        for (n in 1..3) for (i in 0..t.length - n) {
            val g = t.substring(i, i + n)
            if (g.isNotBlank()) fs.add(g)
        }
        return fs
    }

    /** 마음결과 그 확률. 모델이 없으면 null (그때는 낱말 사전으로 대신한다) */
    fun mood(text: String): Pair<Mood, Float>? {
        val m = moodModel ?: return null
        if (norm(text).isEmpty()) return null
        val p = m.predict(text)
        var best = 0
        for (k in p.indices) if (p[k] > p[best]) best = k
        val mood = runCatching { Mood.valueOf(m.classes[best]) }.getOrNull() ?: return null
        return mood to p[best]
    }

    /** 남을 해치거나 공격하는 말일 확률 (모델이 없으면 null) */
    fun harm(text: String): Float? = harmModel?.predict(text)?.getOrNull(1)
}

/**
 * AI 필터 — 말을 담기 전에, 그리고 남에게 보이는 자리(길 위, 친구에게 남기는 한마디)에 올리기 전에 거치는 곳.
 *
 *  · 거친 말(욕설·누군가를 깎아내리는 말·성적인 말·남을 해치는 말)은 어디에도 담지 않는다. "담을 수 없어요" 하고 알린 뒤,
 *    다듬어서 다시 말해 달라고 청한다 (rough → AppModel.refuse).
 *  · 개인 정보와 광고는 묻지 않고 알아서 거른다: 길 위에는 올리지 않고 내 일기에만 담으며, 그렇게 했다고 알려 준다 (check).
 *  · 마음이 많이 힘든 말은 막지도 나무라지도 않는다. 길동무가 따로 답하고, 길 위에는 올리지 않는다.
 *
 * 두 겹으로 본다.
 *  ① 사전과 패턴: 욕설·비속어·혐오 표현·성적인 말, 전화번호·차량번호 같은 개인 정보, 광고 문구.
 *  ② 문장 분류 모델(Ai.harm): 남을 공격하거나 위협하는 말.
 */
object Guard {
    enum class Kind(val label: String) {
        SELF_HARM("마음이 많이 힘든 말이라서"),
        PRIVACY("전화번호 같은 개인 정보가 들어 있어서"),
        SPAM("광고 같은 말이 들어 있어서"),
        PROFANITY("욕설이 들어 있어서"),
        HATE("누군가를 깎아내리는 말이 들어 있어서"),
        SEXUAL("성적인 말이 들어 있어서"),
        HARM("남을 해치거나 공격하는 말이라서"),
    }

    data class Verdict(val kind: Kind?, val byModel: Boolean = false, val p: Float = 0f) {
        val ok: Boolean get() = kind == null
        val label: String? get() = kind?.label
    }

    val OK = Verdict(null)
    const val HARM_THRESHOLD = 0.80f

    // ---- 사전. 두 가지 방식으로 찾는다.
    //  · 붙여 본 글(tight: 칸·숫자·기호를 걷어 낸 글)에서: 띄어 쓰거나 숫자를 섞어 바꾼 표기('씨 발', '씨1발', 'ㅅㅂ')도 잡는다.
    //    낱말 사이를 넘나들어도 헷갈릴 일이 없는 말만 여기에 둔다.
    //  · 낱말 하나하나(words: 숫자를 걷어 낸 낱말)의 앞머리에서: '다시 발표', '한강 간다', '의자 위에', '두 개 같이', '무지개색', '전염병',
    //    '싸 가지고' 처럼 멀쩡한 말이 우연히 이어져 욕처럼 보이는 것을 잘못 막지 않으려고.
    // 웹사이트의 이식본(docs/assets/js/ai.js)과 글자 하나까지 같아야 한다 (tools/site/test_ai.mjs 가 견주어 본다).
    private val selfHarm = Regex("죽고싶|사라지고싶|목숨을끊|뛰어내리고싶|뛰어내릴까|죽어버리고싶|그만살고싶")
    private val selfHarmWord = Regex("^자살")
    private val noLive = Regex("살기 ?싫")
    private val noLiveAlone = Regex("(혼자|같이|둘이|여기서|거기서|집에서) 살기 ?싫")
    private val profanity = Regex("씨발|씨빨|씨팔|쓰발|씌발|싀발|씹새|씹년|씹놈|씹창|씹할|씹탱|ㅅㅂ|ㅆㅂ|ㅅㅃ|ㅆㅃ|ㅈㄴ|ㅈㄹ|ㅂㅅ|ㅁㅊ|ㄱㅅㄲ|tlqkf|qudtls|ssibal|좆|ㅈ같|병신|븅신|지랄|육시럴|씨부랄|씨부럴|니미럴|니애미|느금마|엠창|썅|쌍놈|쌍년|개새끼|개색끼|개색기|개색히|개세끼|개쉐끼|개자식|미친새끼|미친놈|미친년|씨새끼|병새끼|망할새끼|새끼야|새꺄|존나|존내|엿같|엿먹|호로새끼|호로자식|후레자식|후레새끼")
    private val profanityWord = Regex("^(아|야|아오|에이|이런|하)?(시발|시빨|시팔|씨바|시바)(?!점|역|견|스)|^(이|저|그|야|야이|이런|저런|그런)?개(새\$|새끼|새키|색기|색끼|색히|쉐이|쉐리|쉑|세끼|놈)|^(이|저|그|요|야이)(새끼|새키|색기|쌔끼|색히)|^개같|^개소리|^개빡|^개지랄|^(왕|핵|존)?빡(치|친|쳐|쳤|침|돌)|^닥쳐(라|요|봐)?\$|^아닥|^입닥|^염병|^옘병|^엠병|^등신(아|이야|이네|들|새끼)?\$|^등신같|^졸라(게)?\$|^(처|쳐)먹|^아가리|^또라이|^돌아이|^싸가지(야|네)?\$|^싸가지(없|가없|도없)|^조까|^좃|^fuck|^shit\$|^bitch|^asshole")
    private val profanitySpaced = Regex("(^| )(이|저|그|요|이런|저런|그런|야이|미친) (새끼|새키|색기|쌔끼|색히)")
    private val hate = Regex("한남충|한남새끼|김치녀|된장녀|맘충|급식충|틀딱|짱깨|쪽바리|깜둥|똥꼬충|홍어새끼|전라디언|애자새끼")
    private val hateWord = Regex("^한남들|^개독")
    private val sexual = Regex("섹스|성폭행|조건만남|원나잇|몸캠")
    private val sexualStart = Regex("^야동|^자위(?!대|권)|^섹파|^따먹|^강간|^딸딸이")
    private val sexualWord = Regex("(^| )(보지|자지)( |\$)")
    private val violence = Regex("죽여버|죽일거|죽일놈|죽일년|죽일테|찔러버|불질러|때려죽|칼들고|칼로찌|칼로쑤|목을졸|목을비틀|들이받아버|폭탄테러|폭탄을터|폭탄터뜨|폭탄터트|폭탄던|테러를하|테러할|테러한다|테러해버")
    private val violenceWord = Regex("^패(버리|버려|버릴|줄|주고|죽)|^뒤져라|^뒤질래|^뒈져|^뒈질|^디져라|^디질래")
    private val spam = Regex("http|www|오픈채팅|오픈톡|카톡아이디|카카오톡아이디|텔레그램|바카라|카지노|토토사이트|리딩방|코인리딩|수익보장|대출상담|무료상담")
    private val spamWord = Regex("^급전")
    /** 개인 정보: 원래 글에서 찾는다 (전화번호·주민번호·차량번호·계좌번호·이메일·아파트 동호수·긴 숫자) */
    private val privacy = listOf(
        Regex("(01[016789]|02|0[3-6][1-5]|070)[\\s.-]*\\d{3,4}[\\s.-]*\\d{4}"),
        Regex("\\d{6}\\s*-?\\s*[1-4]\\d{6}"),
        Regex("\\d{2,3}\\s?[가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아바사자배하허호]\\s?\\d{4}"),
        Regex("\\d{2,6}-\\d{2,6}-\\d{2,8}"),
        Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        Regex("\\d{1,4}\\s?동\\s?\\d{1,4}\\s?호"),
        Regex("\\d{9,}"),
    )

    /** 말로 읽은 숫자도 숫자로 본다: 음성 인식은 '0102481 삼오칠구'처럼 숫자와 말을 섞어 적기도 한다 */
    private val digitWord = mapOf('공' to '0', '영' to '0', '일' to '1', '이' to '2', '삼' to '3', '사' to '4', '오' to '5', '육' to '6', '륙' to '6', '칠' to '7', '팔' to '8', '구' to '9')
    private val phoneDigits = Regex("(01[016789]|02|0[3-6][1-5]|070)\\d{7,8}|\\d{9,}")

    /** 숫자를 읽은 말(세 글자 이상 이어질 때만)을 숫자로 바꾸고, 숫자 사이의 칸·점·줄표를 없앤다 */
    private fun digits(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && text[j] in digitWord) j++
            if (j - i >= 3) { for (k in i until j) sb.append(digitWord[text[k]]); i = j } else { sb.append(text[i]); i++ }
        }
        return sb.toString().replace(Regex("(?<=\\d)[\\s.-]+(?=\\d)"), "")
    }

    /** 칸·숫자·기호를 걷어 낸 글 (바꿔 쓴 표기를 잡으려고) */
    private fun tight(s: String) = s.lowercase().filter { val o = it.code; o in 0xAC00..0xD7A3 || o in 0x3131..0x318E || it in 'a'..'z' }

    /** 낱말들 (낱말 안에 섞어 넣은 숫자는 걷어 낸다: '시1발') */
    private fun words(s: String): List<String> = Ai.norm(s).split(' ').map { w -> w.filterNot { it in '0'..'9' } }.filter { it.isNotEmpty() }

    /** 한 문장을 두 가지로 풀어 둔 것: 붙여 본 글과 낱말들 */
    private class Seen(text: String) { val t = tight(text); val ws = words(text); val sp = ws.joinToString(" ") }
    private fun Seen.has(inTight: Regex, inWord: Regex) = inTight.containsMatchIn(t) || ws.any { inWord.containsMatchIn(it) }
    private fun Seen.hurting() = has(selfHarm, selfHarmWord) || (noLive.containsMatchIn(sp) && !noLiveAlone.containsMatchIn(sp))
    private fun Seen.swearing() = has(profanity, profanityWord) || profanitySpaced.containsMatchIn(sp)
    private fun Seen.roughKind(text: String): Kind? = when {
        swearing() -> Kind.PROFANITY
        has(hate, hateWord) -> Kind.HATE
        has(sexual, sexualStart) || sexualWord.containsMatchIn(Ai.norm(text)) -> Kind.SEXUAL
        has(violence, violenceWord) -> Kind.HARM
        else -> null
    }

    /**
     * 거친 말인지: 욕설·누군가를 깎아내리는 말·성적인 말·남을 해치는 말 (개인 정보·광고와는 따로 본다).
     * 거친 말은 담지 않고, 다듬어서 다시 말해 달라고 청한다. 마음이 많이 힘든 말은 나무라지 않는다 (그런 말에는 길동무가 따로 답한다).
     */
    fun rough(text: String): Kind? {
        val s = Seen(text)
        if (s.hurting()) return null
        val k = s.roughKind(text) ?: if ((Ai.harm(text) ?: 0f) >= HARM_THRESHOLD) Kind.HARM else null
        if (k != null) Log.d("Gildongmu", "AI 필터: 거친 말 · $k  \"${text.take(28)}\"")
        return k
    }

    /** 거친 말을 들었을 때 건네는 알림: 안 된다는 것을 짧게, 나무라지 않고 */
    fun notice(kind: Kind): String = when (kind) {
        Kind.PROFANITY -> "거친 말은 담을 수 없어요."
        Kind.HATE -> "누군가를 깎아내리는 말은 담을 수 없어요."
        Kind.SEXUAL -> "이런 말은 여기에 담을 수 없어요."
        else -> "누군가를 다치게 할 수 있는 말은 담을 수 없어요."
    }

    /** 길 위에 올리지 않은 까닭을 목소리로 알릴 때 (알아서 걸렀다는 것을 알려 준다) */
    fun spoken(kind: Kind): String = when (kind) {
        Kind.PRIVACY -> "개인 정보가 들어 있어서, 길 위에는 올리지 않았어요."
        Kind.SPAM -> "광고 같은 말이 들어 있어서, 길 위에는 올리지 않았어요."
        else -> "이 말은 길 위에는 올리지 않았어요."
    }

    /** 욕이 섞였는지 */
    fun swears(text: String): Boolean = Seen(text).swearing()

    fun check(text: String): Verdict {
        val s = Seen(text)
        val v = when {
            s.hurting() -> Verdict(Kind.SELF_HARM)
            privacy.any { it.containsMatchIn(text) } || phoneDigits.containsMatchIn(digits(text)) -> Verdict(Kind.PRIVACY)
            s.has(spam, spamWord) -> Verdict(Kind.SPAM)
            else -> s.roughKind(text)?.let { Verdict(it) } ?: run {
                val p = Ai.harm(text) ?: 0f
                if (p >= HARM_THRESHOLD) Verdict(Kind.HARM, byModel = true, p = p) else Verdict(null, p = p)
            }
        }
        Log.d("Gildongmu", "AI 필터: ${if (v.ok) "통과" else "막음 · ${v.kind} · ${if (v.byModel) "모델 %.2f".format(v.p) else "사전"}"}  \"${text.take(28)}\"")
        return v
    }
}
