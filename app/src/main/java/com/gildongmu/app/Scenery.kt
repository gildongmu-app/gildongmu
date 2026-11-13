package com.gildongmu.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import java.util.Calendar

/**
 * 오늘의 빛깔 — 글 뒤에 깔리는 바탕.
 *
 * 그림이나 사진 대신, 아홉 가지 빛깔(은행빛·억새빛·바다빛·불빛·안개빛·눈빛·꽃빛·풀빛·빗빛)이 있다.
 * 계절·시각·날씨·날짜에 따라 그날의 빛깔이 정해지고, 달리는 동안에는 빛이 차창 밖처럼 옆으로 흘러간다.
 * 그날의 일기에는 그 말을 남긴 순간의 빛깔이 함께 남는다. 그리는 일은 LightField.kt 가 맡는다.
 */
enum class Season(val label: String) { SPRING("봄"), SUMMER("여름"), AUTUMN("가을"), WINTER("겨울") }
enum class DayPart(val label: String) { DAWN("새벽"), DAY("낮"), DUSK("노을"), NIGHT("밤") }

/** 바탕 위에 내리는 것 */
enum class Fall { NONE, GINKGO, PETAL, SNOW, RAIN }

/**
 * 장면 하나: 이름표, 이름, 그 장면이 가장 예쁜 시각, 시각마다의 중심 색(새벽·낮·노을·밤), 내리는 것.
 * 그리는 일은 assets/scenes/<이름표>.agsl 이 맡는다.
 */
enum class SceneKind(val key: String, val label: String, val line: String, val light: DayPart, private val tones: LongArray, val fall: Fall) {
    GINKGO("ginkgo", "은행빛", "은행잎이 지는 길", DayPart.DAY, longArrayOf(0xFFFFE08A, 0xFFFFD84A, 0xFFFFBE64, 0xFFFFCF6E), Fall.NONE),
    REEDS("reeds", "억새빛", "바람에 눕는 억새밭", DayPart.DUSK, longArrayOf(0xFFFFD2C4, 0xFFFFE9B3, 0xFFFFC777, 0xFFC6D4FF), Fall.NONE),
    COAST("coast", "바다빛", "윤슬이 부서지는 바다", DayPart.DAY, longArrayOf(0xFFFFD6B8, 0xFFFFE2A6, 0xFFFFC987, 0xFFBCD2FF), Fall.NONE),
    CITY("city", "불빛", "강 건너 도시의 불빛", DayPart.DUSK, longArrayOf(0xFFFFC9B4, 0xFFFFE3A6, 0xFFFFBC8E, 0xFFFFCB8A), Fall.NONE),
    RIDGES("ridges", "안개빛", "새벽안개가 흐르는 능선", DayPart.DAWN, longArrayOf(0xFFFFC8A6, 0xFF8EE8D8, 0xFFFFC06A, 0xFFFFE9B8), Fall.NONE),
    SNOW("snow", "눈빛", "소복이 내리는 눈", DayPart.DAY, longArrayOf(0xFFC2DAFF, 0xFFEAF3FF, 0xFFFFCBDD, 0xFFFFD28C), Fall.SNOW),
    BLOSSOM("blossom", "꽃빛", "바람에 날리는 벚꽃", DayPart.DAY, longArrayOf(0xFFFFC4DC, 0xFFFFD0E2, 0xFFFFCDA8, 0xFFFFB8A8), Fall.NONE),
    GREEN("green", "풀빛", "바람 부는 언덕", DayPart.DAY, longArrayOf(0xFFFFD3C8, 0xFFE8F8A6, 0xFFFFD08A, 0xFFD6FF82), Fall.NONE),
    RAIN("rain", "빗빛", "비 오는 창", DayPart.NIGHT, longArrayOf(0xFFFFC89A, 0xFFFFE2B0, 0xFFFF9DBF, 0xFFFFB95C), Fall.RAIN),
    AURORA("aurora", "빛결", "비단처럼 흐르는 빛", DayPart.NIGHT, longArrayOf(0xFFFFB98A, 0xFF7DF5CF, 0xFFFF9472, 0xFF5AD8FF), Fall.NONE);

    /** 그 시각의 중심 색 */
    fun accent(part: DayPart): Color = Color(tones[part.ordinal])
    val accent: Color get() = accent(light)

    companion object {
        /**
         * 그날·그때의 빛깔을 고른다. 계절의 빛깔과 사철 빛깔(바다빛·불빛·안개빛)이 날마다 번갈아 나오고,
         * 새벽엔 안개빛, 저녁엔 억새빛이나 불빛, 밤엔 불빛, 비 오는 밤엔 빗빛이 먼저 나온다.
         */
        fun pick(season: Season, part: DayPart, rain: Boolean, day: Int): SceneKind {
            if (rain && (part == DayPart.DUSK || part == DayPart.NIGHT)) return RAIN
            val own = when (season) { Season.SPRING -> BLOSSOM; Season.SUMMER -> GREEN; Season.AUTUMN -> GINKGO; Season.WINTER -> SNOW }
            val cool = season == Season.AUTUMN || season == Season.WINTER
            val pool = when (part) {
                DayPart.DAWN -> listOf(RIDGES, own, RIDGES)
                DayPart.DAY -> listOf(own, COAST, own)
                DayPart.DUSK -> if (cool) listOf(REEDS, CITY, own) else listOf(CITY, own, COAST)
                DayPart.NIGHT -> listOf(CITY, own, CITY)
            }
            return pool[(day + part.ordinal).mod(pool.size)]
        }
    }
}

object Look {
    /** 설정에서 직접 고른 계절·시각·빛깔 (null 이면 날짜와 시각을 따른다) */
    var seasonOverride by mutableStateOf<Season?>(null)
    var partOverride by mutableStateOf<DayPart?>(DayPart.NIGHT)
    /** 손으로 해를 끌어 시각을 고르는 동안의 값 (0 새벽 · 1 낮 · 2 노을 · 3 밤, 사이 값은 그 사이의 빛). 끌지 않을 때는 null. */
    var dayScrub by mutableStateOf<Float?>(null)
    var sceneOverride by mutableStateOf<SceneKind?>(SceneKind.RAIN)

    /** 지금의 장면과 시각 (앱의 중심 색이 여기서 온다) */
    var scene by mutableStateOf(SceneKind.RAIN)
    var partNow by mutableStateOf(DayPart.NIGHT)

    fun seasonOf(c: Calendar): Season = when (c.get(Calendar.MONTH) + 1) {
        3, 4, 5 -> Season.SPRING; 6, 7, 8 -> Season.SUMMER; 9, 10, 11 -> Season.AUTUMN; else -> Season.WINTER
    }

    // 달마다의 해 뜨는 시각과 지는 시각 (분, 서울 기준 어림값)
    private val SUNRISE = intArrayOf(465, 440, 400, 355, 320, 310, 325, 350, 375, 400, 430, 458)
    private val SUNSET = intArrayOf(1055, 1090, 1118, 1145, 1172, 1195, 1193, 1162, 1118, 1074, 1040, 1036)

    /**
     * 그 시각의 빛. 해 뜨고 지는 시각이 달마다 달라서, 같은 저녁 6시라도 여름은 낮이고 11월은 이미 밤이다.
     * (계절을 직접 골라 둔 경우에는 그 계절 한가운데 달의 해로 셈한다)
     */
    fun partOf(c: Calendar, s: Season = seasonOf(c)): DayPart {
        val month = if (s == seasonOf(c)) c.get(Calendar.MONTH) else when (s) { Season.SPRING -> 3; Season.SUMMER -> 6; Season.AUTUMN -> 9; Season.WINTER -> 0 }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val rise = SUNRISE[month]
        val set = SUNSET[month]
        return when {
            m in (rise - 45)..(rise + 50) -> DayPart.DAWN
            m in (rise + 51)..(set - 56) -> DayPart.DAY
            m in (set - 55)..(set + 30) -> DayPart.DUSK
            else -> DayPart.NIGHT
        }
    }

    fun season(c: Calendar = Calendar.getInstance()): Season = seasonOverride ?: seasonOf(c)
    fun part(c: Calendar = Calendar.getInstance()): DayPart = partOverride ?: partOf(c, season(c))

    /** 지금의 빛깔 (설정에서 고른 빛깔이 있으면 그것) */
    fun sceneNow(c: Calendar = Calendar.getInstance(), rain: Boolean = false): SceneKind =
        sceneOverride ?: SceneKind.pick(season(c), part(c), rain, c.get(Calendar.DAY_OF_YEAR))

    /** 지난 어느 날·어느 때의 빛깔 (일기에 남는 바탕) */
    fun sceneOf(c: Calendar, rain: Boolean = false): SceneKind {
        val s = seasonOf(c)
        return SceneKind.pick(s, partOf(c, s), rain, c.get(Calendar.DAY_OF_YEAR))
    }

    fun accentOf(s: Season): Color = scene.accent(partNow)
    val accent: Color get() = scene.accent(partNow)

    fun apply(season: String, part: String, scene: String = "auto") {
        seasonOverride = runCatching { Season.valueOf(season) }.getOrNull()
        partOverride = runCatching { DayPart.valueOf(part) }.getOrNull()
        sceneOverride = runCatching { SceneKind.valueOf(scene) }.getOrNull()
    }
}

/**
 * 빛의 바탕을 그린다.
 * scene: 어느 빛깔인지 (기본은 지금의 빛깔), part: 시각 (그 빛깔을 그 시각의 빛으로 물들인다),
 * speed: 흘러가는 빠르기 (정차 중 0.1, 주행 중 3), dim: 어둡게 하는 정도,
 * night: 0→1 로 올리면 밤으로 저문다 (하루를 덮을 때), seed: 빛이 놓이는 자리,
 * still: 멈춘 그림 (작은 동그라미 여러 개를 놓을 때), soft: 0→1 로 올리면 빛이 잔잔해진다,
 * backdrop: 이 바탕 위에 놓일 유리(frost)와 안개(mist)가 맑게 서도록 넘겨 주는 표시
 */
@Composable
fun Scenery(
    modifier: Modifier = Modifier, season: Season = Look.season(), part: DayPart = Look.part(), rain: Boolean = false,
    speed: Float = 0.1f, dim: Float = 0f, night: Float = 0f, seed: Int = 0, still: Boolean = false, art: Art = Skin.art,
    scene: SceneKind = Look.scene, soft: Float = 0f, backdrop: Backdrop? = null, level: Float = 0f, economy: Boolean = false,
) {
    LiveScene(modifier, scene, part, rain, speed, dim, night, seed, still, soft, art, level, economy)
}

/**
 * 빛의 바탕을 깔고 그 위에 화면을 올린다.
 */
@Composable
fun SceneStage(
    modifier: Modifier = Modifier, scene: SceneKind = Look.scene, part: DayPart = Look.part(), rain: Boolean = false,
    speed: Float = 0.1f, soft: Float = 0f, dim: Float = 0f, night: Float = 0f, seed: Int = 0, level: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val bd = remember { Backdrop() }
    // 잔잔하게 깔 때는 장면을 초점 밖으로 흐린다 (그 위의 글과 구슬이 또렷하게 서도록)
    androidx.compose.runtime.SideEffect { bd.glass.soften = (soft * 0.85f).coerceIn(0f, 1f) }
    Box(modifier.onGloballyPositioned { bd.origin = it.positionInRoot() }) {
        Scenery(Modifier.fillMaxSize().glassBackdrop(bd.glass), part = part, rain = rain, speed = speed, dim = dim, night = night, seed = seed, scene = scene, soft = soft, backdrop = bd, level = level)
        CompositionLocalProvider(LocalBackdrop provides bd) { content() }
    }
}

/** 0..1 사이의 고른 난수 (같은 i·s 에는 늘 같은 값) */
internal fun hash(i: Int, s: Int): Float {
    var n = i * 374761393 + s * 668265263
    n = (n xor (n ushr 13)) * 1274126177
    n = n xor (n ushr 16)
    return (n and 0x7fffffff) / 2147483647f
}

/** 음수에서도 0..span 사이로 접히는 나머지 */
internal fun wrap(v: Float, span: Float) = ((v % span) + span) % span
