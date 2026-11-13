package com.gildongmu.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------- 하나의 시계로 모든 움직임을

val LocalClock = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

@Composable
fun ProvideClock(content: @Composable () -> Unit) {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (it - start) / 1_000_000_000f }
    }
    CompositionLocalProvider(LocalClock provides t, content = content)
}

fun rnd(i: Int, k: Int): Float {
    val x = sin(i * 12.9898f + k * 78.233f) * 43758.547f
    return x - floor(x)
}

// ---------------------------------------------------------------- 면과 움직임

/** 작은 조작은 알약 모양, 큰 면은 크게 굴린다. 네모난 모서리는 쓰지 않는다. */
val R4: RoundedCornerShape get() = RoundedCornerShape(Skin.t.rS)
val R10: RoundedCornerShape get() = RoundedCornerShape(Skin.t.rM)
val R16: RoundedCornerShape get() = RoundedCornerShape(Skin.t.rL)

/** 유리 면 (뒤의 풍경이 흐릿하게 비친다). fill 이 투명하면 가는 테두리만 두른다. */
fun Modifier.panel(shape: Shape = R16, fill: Color = C.Panel, line: Color = C.Line): Modifier =
    if (fill.alpha < 0.02f) this.border(1.dp, C.Line, shape) else this.frost(shape, tint = C.Panel)

/** 아래에서 조용히 올라오며 나타나기 */
@Composable
fun Modifier.rise(index: Int = 0, delayMs: Int = 0, distance: Dp = 22.dp, durationMs: Int = 900): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(Pace.ms(delayMs + index * 70))
        a.animateTo(1f, tween(Pace.dur(durationMs), easing = Ease.out))
    }
    val d = with(LocalDensity.current) { distance.toPx() }
    return this.graphicsLayer { alpha = (a.value * 1.6f).coerceAtMost(1f); translationY = (1f - a.value) * d }
}

/** 누르면 살짝 눌리는 감촉 */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, null, enabled = enabled) { onClick() }
}

/** 한 글자씩 떠오르는 글 (자리는 미리 잡아 두어 흔들리지 않게) */
@Composable
fun TypeText(text: String, style: TextStyle, modifier: Modifier = Modifier, speedMs: Long = 30, delayMs: Long = 200) {
    var n by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        n = 0
        delay(delayMs)
        while (n < text.length) { delay(speedMs); n++ }
    }
    val shown = buildAnnotatedString {
        append(text.take(n))
        withStyle(SpanStyle(color = Color.Transparent)) { append(text.drop(n)) }
    }
    Text(shown, style = style, modifier = modifier)
}

/** 천천히 올라가는 숫자 */
@Composable
fun CountUpLed(value: Int, pitch: Dp, color: Color = C.Hi, modifier: Modifier = Modifier, delayMs: Long = 200, digits: Int = 0) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { delay(delayMs); a.animateTo(value.toFloat(), tween(1200, easing = EaseOutCubic)) }
    val n = a.value.roundToInt()
    LedText(if (digits > 0) n.toString().padStart(digits, '0') else n.toString(), pitch, color, modifier)
}

// ---------------------------------------------------------------- 조작: 버튼, 표지, 빛, 스위치

enum class BtnKind { Primary, Secondary, Ghost, Danger }

/**
 * 알약 모양 버튼.
 * Primary 는 지금 장면에서 온 빛으로 채우고 둘레로 그 빛이 옅게 번진다. Secondary 는 가는 빛의 테를 두른 유리, Ghost 는 글만.
 */
@Composable
fun Btn(
    label: String, modifier: Modifier = Modifier, glyph: G? = null, kind: BtnKind = BtnKind.Secondary,
    big: Boolean = false, tone: Color = C.Accent, glass: Boolean = false, onClick: () -> Unit,
) {
    val fg = when (kind) { BtnKind.Primary -> C.AccentInk; BtnKind.Danger -> Color(0xFFFF9A90); BtnKind.Ghost -> C.Hi.copy(alpha = 0.80f); else -> C.Hi }
    val base = modifier.pressable(onClick = onClick)
    val shaped = when (kind) {
        BtnKind.Primary -> base
            .drawBehind {
                // 버튼 둘레로 번지는 빛
                val r = size.height * 0.9f
                drawRoundRect(
                    Brush.radialGradient(listOf(tone.copy(alpha = 0.34f), Color.Transparent), Offset(size.width / 2f, size.height * 0.62f), size.width * 0.62f),
                    topLeft = Offset(-r * 0.4f, -r * 0.2f), size = Size(size.width + r * 0.8f, size.height + r * 0.6f), cornerRadius = CornerRadius(r),
                )
            }
            .background(Brush.verticalGradient(listOf(lerp(tone, Color.White, 0.30f), tone, lerp(tone, Color.Black, 0.06f))), CircleShape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.08f))), CircleShape)
        // 또렷한 장면 위에 홀로 서는 버튼은 진짜 유리로 (뒤의 장면이 흐려 비친다), 흐린 바탕이나 시트 안에서는 옅은 면으로
        BtnKind.Secondary -> if (glass) base.glass(dim = 0.18f) else base.frost(CircleShape, tint = C.Raised)
        BtnKind.Danger -> base.background(Color(0x24FF6B5E), CircleShape).border(1.dp, Color(0x40FF8A80), CircleShape)
        BtnKind.Ghost -> base
    }
    Row(
        shaped.heightIn(min = if (big) 84.dp else 76.dp).padding(horizontal = if (big) 38.dp else if (kind == BtnKind.Ghost) 20.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Glyph(glyph, fg, if (big) 26.dp else 21.dp, if (big) 2.1.dp else 1.8.dp); Spacer(Modifier.width(if (big) 12.dp else 10.dp)) }
        Text(label, style = ts(if (big) 23 else 17, fg, if (kind == BtnKind.Primary) FontWeight.SemiBold else FontWeight.Medium))
    }
}

/** 작은 표지 */
@Composable
fun Tag(text: String, color: Color = C.Low, modifier: Modifier = Modifier) {
    Text(text, style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = color, letterSpacing = 0.04.em), modifier = modifier)
}

/** 작은 빛 하나 */
@Composable
fun Lamp(color: Color, on: Boolean = true, size: Dp = 10.dp, blink: Boolean = false, modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    Canvas(modifier.size(size * 2.4f)) {
        val k = if (!on) 0f else if (blink) (0.35f + 0.65f * (0.5f + 0.5f * sin(clock.value * 5f))) else 1f
        val r = size.toPx() / 2
        if (k > 0f) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.50f * k), Color.Transparent), center, r * 2.4f), r * 2.4f)
        drawCircle(if (on) color.copy(alpha = 0.35f + 0.65f * k) else C.Off, r)
    }
}

/** 양 끝이 스르르 사라지는 가는 선 */
@Composable
fun HLine(modifier: Modifier = Modifier, color: Color = C.Line) =
    Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(0f to Color.Transparent, 0.12f to color, 0.88f to color, 1f to Color.Transparent)))

/** 스위치: 켜지면 풍경의 빛이 들어온다 */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 43.dp else 5.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "x")
    val track by animateColorAsState(if (on) C.Accent.copy(alpha = 0.92f) else C.Raised, tween(220), label = "track")
    // 보이는 것보다 넉넉한 자리를 누를 수 있게 한다
    Box(Modifier.size(100.dp, 76.dp).pressable { onChange(!on) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(90.dp, 52.dp).clip(CircleShape).background(track).border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)) {
            Box(Modifier.offset(x = x, y = 5.dp).size(42.dp).clip(CircleShape).background(if (on) Color.White else C.Hi.copy(alpha = 0.85f)))
        }
    }
}

/** 몇 가지 중 하나 고르기: 고른 자리로 밝은 알약이 미끄러져 간다 */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, value: T, modifier: Modifier = Modifier, onChange: (T) -> Unit) {
    val xs = remember { mutableStateMapOf<Int, Float>() }
    val ws = remember { mutableStateMapOf<Int, Float>() }
    val idx = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val tx by animateFloatAsState(xs[idx] ?: 0f, spring(dampingRatio = 0.78f, stiffness = 380f), label = "choiceX")
    val tw by animateFloatAsState(ws[idx] ?: 0f, spring(dampingRatio = 0.78f, stiffness = 380f), label = "choiceW")
    Row(
        modifier.frost(CircleShape, tint = C.Panel).padding(5.dp).drawBehind {
            val pad = 5.dp.toPx()
            if (tw > 1f) drawRoundRect(C.Hi.copy(alpha = 0.94f), Offset(tx - pad, 0f), Size(tw, size.height), CornerRadius(size.height / 2f))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == value
            val fg by animateColorAsState(if (on) C.AccentInk else C.Mid, tween(260), label = "seg")
            Box(
                Modifier.onGloballyPositioned { xs[i] = it.positionInParent().x; ws[i] = it.size.width.toFloat() }
                    .pressable { onChange(v) }.heightIn(min = 68.dp).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = ts(16, fg, FontWeight.Medium)) }
        }
    }
}

/** 동그란 조작 버튼 (지도 확대·축소, 설정 등) */
@Composable
fun SquareButton(g: G, modifier: Modifier = Modifier, size: Dp = 70.dp, onClick: () -> Unit) {
    Box(modifier.size(size).pressable(onClick = onClick).frost(CircleShape, tint = C.Raised), contentAlignment = Alignment.Center) { Glyph(g, C.Hi, size * 0.40f, 1.9.dp) }
}

/** 풍경 위에 얹는 어둑한 막 (세로 그러데이션) — stops: 위치 to 진하기 */
fun veil(part: DayPart, vararg stops: Pair<Float, Float>): Brush {
    val v = Skin.veil(part)
    return Brush.verticalGradient(*stops.map { it.first to v.copy(alpha = it.second) }.toTypedArray())
}

/** 가로로 번지는 막 */
fun veilH(vararg stops: Pair<Float, Float>): Brush {
    val v = Skin.veil(DayPart.DAY)
    return Brush.horizontalGradient(*stops.map { it.first to v.copy(alpha = it.second) }.toTypedArray())
}
