package com.gildongmu.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 작은 빛들 — 숫자, 감정, 사람, 목소리, 좋아요.
 * 모두 가는 글자와 둥근 빛으로만 이루어진다.
 */

// ================================================================ 숫자와 글

/** 가는 숫자 한 줄. pitch 는 예전 점 글자의 점 간격이라, 글자 크기로 바꿔 쓴다. */
@Composable
fun LedText(
    text: String, pitch: Dp, color: Color = C.Accent, modifier: Modifier = Modifier, big: Boolean = false, bold: Boolean = false,
    off: Boolean = false, glow: Float = 0.5f, revealMs: Int = 0, delayMs: Int = 0, shade: Float = 0f,
) {
    Num(text, (pitch.value * 9.2f).roundToInt(), color, modifier, shadow = shade > 0f)
}

/** 한 줄에 넘치면 말줄임으로 */
@Composable
fun LedMarquee(text: String, pitch: Dp, cols: Int, color: Color = C.Accent, modifier: Modifier = Modifier, big: Boolean = false, speed: Float = 16f) {
    Text(text, style = ts(26, color, FontWeight.SemiBold, family = Serif), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.width(pitch * cols))
}

/** 목소리의 물결: 가운데 선에서 위아래로 번지는 둥근 막대 (왼쪽이 옛 소리, 오른쪽이 지금) */
@Composable
fun LedMeter(level: () -> Float, cols: Int, rows: Int, pitch: Dp, color: Color = C.Accent, modifier: Modifier = Modifier, active: Boolean = true) {
    val clock = LocalClock.current
    val n = (cols / 2).coerceAtLeast(8)
    val hist = remember { FloatArray(n) }
    val head = remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        while (true) {
            delay(60)
            val i = head.intValue
            hist[i % n] = if (active) level().coerceIn(0f, 1f) else 0f
            head.intValue = i + 1
        }
    }
    Canvas(modifier.size(pitch * cols, pitch * rows)) {
        val t = clock.value
        val h0 = head.intValue
        val step = size.width / n
        val mid = size.height / 2
        val wdt = step * 0.36f
        for (k in 0 until n) {
            val v = hist[((h0 + k) % n + n) % n]
            val idle = 0.08f + 0.06f * sin(t * 2.2f + k * 0.84f)
            val a = ((if (active) v else 0f).coerceAtLeast(idle)) * (mid - wdt / 2).coerceAtLeast(1f)
            val x = step * (k + 0.5f)
            val edge = 1f - kotlin.math.abs(k - (n - 1) / 2f) / (n / 2f)       // 양 끝으로 갈수록 옅게
            drawLine(color.copy(alpha = (0.30f + 0.70f * (a / mid)) * (0.25f + 0.75f * edge)), Offset(x, mid - a), Offset(x, mid + a), wdt, StrokeCap.Round)
        }
    }
}

// ================================================================ 감정과 사람

/** 감정 하나를 빛나는 구슬로 */
@Composable
fun MoodLamp(mood: Mood, size: Dp, modifier: Modifier = Modifier, plate: Boolean = true, phase: Float = 0f) = MoodOrb(mood, size, modifier, phase)

/** 감정 색 빛 하나와 이름 */
@Composable
fun MoodDot(mood: Mood, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Lamp(mood.tone().a, size = 8.dp)
        Spacer(Modifier.width(4.dp))
        Text(mood.label, style = ts(18, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
    }
}

/** 사람의 이름표 */
@Composable
fun Badge(tone: Tone, initial: String, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, unknown: Boolean = false) =
    Avatar(tone, initial, size, modifier, dim, unknown)

/** 길 위의 이야기 한 곳을 나타내는 작은 구슬 (목록·팝업용) */
@Composable
fun PlaceLamp(p: Place, tone: Color, size: Dp, modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    Canvas(modifier.size(size)) {
        val k = 0.85f + 0.15f * sin(clock.value * 2f + p.id.hashCode() % 7)
        glowIcon(when (p.kind) { PlaceKind.VOICE -> Ic.STAR; PlaceKind.DIARY -> Ic.HEART; PlaceKind.CAUTION -> Ic.CAUTION }, center, this.size.minDimension / 2f, tone, k)
    }
}

// ================================================================ 좋아요

@Composable
fun HeartButton(on: Boolean, count: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fill = remember { Animatable(if (on) 1f else 0f) }
    val boom = remember { Animatable(1f) }
    val seed = remember { (Math.random() * 1000).toInt() }
    val was = remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !was.value) {
            fill.snapTo(0f)
            fill.animateTo(1f, tween(420, easing = LinearEasing))
            boom.snapTo(0f); boom.animateTo(1f, tween(900))
        } else if (!on) fill.snapTo(0f)
        was.value = on
    }
    Box(modifier) {
        Row(
            Modifier.pressable(onClick = onClick).frost(CircleShape, tint = if (on) C.Red.copy(alpha = 0.22f) else C.Raised).heightIn(min = 76.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(26.dp, 24.dp)) {
                icon(Ic.HEART, center, size.height * 0.50f, if (fill.value > 0f) C.Red else C.Hi.copy(alpha = 0.55f))
            }
            if (count != null) {
                Spacer(Modifier.width(9.dp))
                Text("$count", style = ts(17, if (on) Color(0xFFFF9A90) else C.Hi, FontWeight.Medium))
            }
        }
        Canvas(Modifier.matchParentSize()) {
            if (boom.value < 1f) softBurst(Offset(16.dp.toPx() + 13.dp.toPx(), size.height / 2), C.Red, boom.value * 1.1f, 0.7f, seed)
        }
    }
}

// ================================================================ 말로 남기는 작은 창 (지도·친구 공용)

@Composable
fun ListenSheet(
    m: AppModel, visible: Boolean, title: String, doneTitle: String, doneSub: String,
    onRetry: () -> Unit, modifier: Modifier = Modifier,
) {
    val last = remember { mutableStateOf(Listening()) }
    val lastTitle = remember { mutableStateOf(Triple("", "", "")) }
    if (visible) { last.value = m.listening; lastTitle.value = Triple(title, doneTitle, doneSub) }
    AnimatedVisibility(
        visible, modifier = modifier,
        enter = fadeIn(tween(250)) + slideInVertically(tween(380, easing = EaseOutCubic)) { it / 3 },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 4 },
    ) {
        val l = last.value
        val (tt, dt, ds) = lastTitle.value
        Row(Modifier.width(880.dp).glass(radius = 64.dp, dim = 0.46f, refract = 14f).padding(start = 12.dp, end = 40.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            val err = l.error
            val note = l.notice
            val live = err == null && note == null && !l.done
            Orb(150.dp, level = if (live) m.listening.level else 0f, energy = if (live) 1f else 0.2f, a = if (l.blocked != null || note != null) C.Amber else if (l.done) C.Green else C.Accent)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                when {
                    note != null -> {
                        // 거친 말을 들었을 때: 담지 않고, 다듬어서 다시 말해 달라고 청한다
                        Text(note, style = ts(22, C.Hi, family = Serif, lh = 1.45f))
                        Text((if (l.again) "마음이 조금 가라앉으면, 그때 다시 들려주세요." else "조금만 다듬어서, 한 번만 더 들려주세요.") + "  ·  방금 한 말은 담지 않았어요", style = ts(15, C.Low))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Btn("다시 말할게요", glyph = G.Mic, kind = BtnKind.Primary) { m.listenAgain() }
                            Btn("닫기", glyph = G.Close) { m.closeListening() }
                        }
                    }
                    err != null -> {
                        Text(err, style = ts(22, C.Hi, family = Serif, lh = 1.45f))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Btn("다시 말하기", glyph = G.Mic, kind = BtnKind.Primary) { onRetry() }
                            Btn("닫기", glyph = G.Close) { m.closeListening() }
                        }
                    }
                    l.done && l.blocked != null -> {
                        // AI 필터가 막은 말: 남에게는 보이지 않게, 내 일기에만 담았다
                        Text("다른 사람에게는 보이지 않게 했어요", style = ts(21, C.Hi, FontWeight.SemiBold))
                        Text("${l.blocked}  ·  내 일기에만 담았어요", style = ts(15, C.Low))
                        Spacer(Modifier.height(10.dp))
                        Text("“${l.text}”".keepWords(), style = ts(27, C.Hi, family = Serif, lh = 1.4f))
                    }
                    l.done -> {
                        Text(dt, style = ts(21, C.Hi, FontWeight.SemiBold))
                        Text(ds, style = ts(15, C.Low))
                        Spacer(Modifier.height(10.dp))
                        Text("“${l.text}”".keepWords(), style = ts(27, C.Hi, family = Serif, lh = 1.4f))
                    }
                    else -> {
                        Text(tt, style = ts(18, C.Mid, FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        if (l.text.isBlank()) Text("천천히 말해 주세요. 말을 멈추면 남겨 둘게요.", style = ts(24, C.Hi.copy(alpha = 0.8f), family = Serif))
                        else Text("“${l.text}”".keepWords(), style = ts(27, C.Hi, family = Serif, lh = 1.4f))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Btn("다 말했어요", glyph = G.Check, kind = BtnKind.Primary) { m.stopListening() }
                            Btn("취소", glyph = G.Close) { m.closeListening() }
                        }
                    }
                }
            }
        }
    }
}
