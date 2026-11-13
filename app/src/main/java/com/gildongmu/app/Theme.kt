package com.gildongmu.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 길동무 디자인 — "글과 빛"
 *
 * 화면의 주인공은 운전하는 사람의 말이다. 그림도 사진도 없이, 말 한 줄이 글귀처럼 크게 놓인다.
 *  - 바탕: 계절·시각·날씨·날짜에 따라 바뀌는 아홉 가지 빛깔이 천천히 번진다. 말을 할 때도, 하루를 덮을 때도, 일기장의 날마다 그때의 빛깔이 함께한다.
 *  - 네모난 판은 쓰지 않는다. 글은 가장자리 없는 어스름 위에, 조작은 유리로 된 알약과 동그라미에 올린다.
 *  - 말하기는 안에서 빛이 흐르는 구슬 하나. 길동무가 듣고 있을 때 목소리를 따라 숨 쉰다.
 *  - 글자: 사람의 말은 명조(마루 부리), 숫자와 조작은 고딕(프리텐다드). 줄은 낱말 단위로 바뀐다.
 */
object C {
    // 바탕과 글: 화풍을 따른다
    val Night: Color get() = Skin.t.night   // 풍경이 없는 자리의 바탕
    val Panel: Color get() = Skin.t.panel   // 유리에 도는 옅은 빛
    val Raised: Color get() = Skin.t.raised // 조금 더 밝은 유리
    val Line: Color get() = Skin.t.line     // 가는 선
    val LineSoft: Color get() = Skin.t.lineSoft
    val Off: Color get() = Skin.t.off       // 꺼진 점
    val Hi: Color get() = Skin.t.hi
    val Mid: Color get() = Skin.t.mid
    val Low: Color get() = Skin.t.low
    val Faint: Color get() = Skin.t.faint

    // 빛
    /** 지금 창밖 풍경에서 온 중심 색 */
    val Accent: Color get() = Skin.accent(Look.season())
    val AccentInk: Color get() = Skin.t.accentInk
    /** 주의·알림 (계절과 무관) */
    val Amber = Color(0xFFFFB02E)
    val Red = Color(0xFFFF4B3E)
    val Green = Color(0xFF3FD97F)
    val Blue = Color(0xFF5AA9FF)
}

/** 등 색 하나(a)와, 그보다 깊은 색(b) */
data class Tone(val a: Color, val b: Color)

/** 감정마다 다른 등 색 */
fun Mood.tone(): Tone = when (this) {
    Mood.JOY -> Tone(Color(0xFFFFC53D), Color(0xFFB7791F))
    Mood.FLUTTER -> Tone(Color(0xFFFF7FA8), Color(0xFFB8436B))
    Mood.CALM -> Tone(Color(0xFF5FE0C0), Color(0xFF2B8F7B))
    Mood.WORRY -> Tone(Color(0xFFFF9A4D), Color(0xFFB85F24))
    Mood.TIRED -> Tone(Color(0xFFB4B8C5), Color(0xFF6C7080))
    Mood.SAD -> Tone(Color(0xFF6FA8FF), Color(0xFF3E66B8))
    Mood.ANGRY -> Tone(Color(0xFFFF5A4F), Color(0xFFB3352D))
}

/** 사람마다 고르는 '나의 빛' */
val AvatarTones = listOf(
    Tone(Color(0xFFFFB02E), Color(0xFF9A6A18)), // 노을
    Tone(Color(0xFFFF7FA8), Color(0xFF9C4264)), // 벚꽃
    Tone(Color(0xFF5AA9FF), Color(0xFF30619C)), // 바다
    Tone(Color(0xFF4FD68A), Color(0xFF2A7F51)), // 숲
    Tone(Color(0xFFB59CFF), Color(0xFF6A58A8)), // 라일락
    Tone(Color(0xFFFF6A45), Color(0xFF9C3D26)), // 불씨
)
val AvatarToneNames = listOf("노을", "벚꽃", "바다", "숲", "라일락", "불씨")

/** 예전 자료와의 호환용 (지금 화면에서는 쓰지 않는다) */
enum class Motif(val label: String) { Leaf("잎새"), Star("별"), Moon("달"), Wave("물결"), Bloom("꽃"), Spark("반짝임") }

fun Profile.tone(): Tone = AvatarTones[colorIdx.mod(AvatarTones.size)]
/** 그 사람이 고른 화풍 (예전 자료엔 없으므로 점묘로) */
fun Profile.art(): Art = Art.from(emoji)
fun Profile.motif(): Motif = runCatching { Motif.valueOf(emoji) }.getOrElse { Motif.entries[colorIdx.mod(Motif.entries.size)] }
fun Profile.initial(): String = name.trim().take(1)

// ---------------------------------------------------------------- 글꼴

/** 조작과 설명: 프리텐다드 */
val Sans = FontFamily(
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
)

/** 명조: 마루 부리 */
val Myeongjo = FontFamily(
    Font(R.font.maruburi_regular, FontWeight.Normal),
    Font(R.font.maruburi_semibold, FontWeight.SemiBold),
    Font(R.font.maruburi_bold, FontWeight.Bold),
)

/** 사람의 말과 제목: 화풍에 따라 명조 또는 고딕 */
val Serif: FontFamily get() = if (Skin.t.serif) Myeongjo else Sans

fun ts(
    size: Int,
    color: Color = C.Hi,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Sans,
    spacing: Float = 0f,
    lh: Float = 1.4f,
): TextStyle {
    // 차 화면은 멀리서 보니까, 작은 글씨일수록 조금 더 키운다
    val s = if (size < 30) size + (30 - size) * 0.42f else size.toFloat()
    return TextStyle(
        fontFamily = family, fontWeight = weight, fontSize = s.sp, color = color,
        letterSpacing = spacing.em, lineHeight = (s * lh).sp,
    )
}

/** 풍경 위에 올리는 글: 흰 글자엔 옅은 그림자, 먹색 글자엔 종이빛 테두리를 깔아 어느 하늘에서도 읽히게 */
fun TextStyle.onScene(strength: Float = 0.55f): TextStyle {
    val lightInk = color.red + color.green + color.blue > 1.5f
    return copy(shadow = if (lightInk) Shadow(Color.Black.copy(alpha = (strength + 0.12f).coerceAtMost(1f)), Offset(0f, 2f), 22f) else Shadow(Color.White.copy(alpha = 0.9f), Offset.Zero, 10f))
}

/**
 * 줄이 바뀔 때 낱말이 중간에서 끊기지 않게 한다 (글귀는 어절 단위로 줄이 바뀌어야 읽기 좋다).
 * 낱말 안의 글자 사이에 '줄 바꿈 금지' 표시(U+2060)를 넣는다.
 */
fun String.keepWords(): String = split(' ').joinToString(" ") { w -> if (w.length <= 1) w else w.toList().joinToString("\u2060") }
