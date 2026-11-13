package com.gildongmu.app

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * 움직임 — 길동무의 글과 선이 나타나는 방식.
 *
 *  - 글은 줄마다 가림막 뒤에서 올라오고([RevealText]), 큰 글은 초점이 맞으며 나타난다([focusIn]).
 *  - 숫자는 굴러서 바뀐다([RollingNumber]).
 *  - 선은 그어지며 나타나고, 그 위의 점은 하나씩 켜진다([DayArc]).
 * 빠르게 떠나서 길게 미끄러지듯 멈추는 것이 기본 박자다.
 */
/**
 * 나타나는 박자. 앱을 켠 뒤 첫 화면은 느긋하게 차례로 나타나지만(1), 그 뒤로 화면을 오갈 때는 기다림을 줄여 빠르게 자리 잡는다(0.45).
 * (화면에 올 때마다 여덟 조각이 1.4초에 걸쳐 차례로 뜨면, 두 번째부터는 기다림으로 느껴진다.)
 */
object Pace {
    @Volatile var k = 1f
    fun ms(v: Int): Long = (v * k).toLong()
    fun dur(v: Int): Int = (v * (0.55f + 0.45f * k)).toInt()
}

object Ease {
    /** 빠르게 떠나 길게 미끄러지며 멈춘다 */
    val out = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
    val inOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val soft = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
}

/**
 * 초점이 맞으며 나타나기: 흐릿하게 떠 있다가 또렷해지며 제자리에 앉는다.
 * 번짐이 잘리지 않도록, 둘레에 보이지 않는 여백을 두고 그린다 (자리는 차지하지 않는다).
 */
@Composable
fun Modifier.focusIn(index: Int = 0, delayMs: Int = 0, durationMs: Int = 1000, blur: Dp = 12.dp, distance: Dp = 14.dp): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(Pace.ms(delayMs + index * 80))
        a.animateTo(1f, tween(Pace.dur(durationMs), easing = Ease.out))
    }
    val density = LocalDensity.current
    val d = with(density) { distance.toPx() }
    val b = with(density) { blur.toPx() }
    val pad = 22.dp
    return this
        .layout { measurable, constraints ->
            val px = pad.roundToPx()
            val p = measurable.measure(constraints.offset(px * 2, px * 2))
            layout((p.width - px * 2).coerceAtLeast(0), (p.height - px * 2).coerceAtLeast(0)) { p.place(-px, -px) }
        }
        .graphicsLayer {
            val k = a.value
            alpha = (k * 1.5f).coerceAtMost(1f)
            translationY = (1f - k) * d
            val r = (1f - k) * b
            renderEffect = if (r > 0.6f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(r, r, TileMode.Decal) else null
        }
        .padding(pad)
}

/**
 * 줄마다 가림막 뒤에서 올라오는 글. 줄이 차례로, 아래에서 위로 미끄러져 올라와 자리를 잡는다.
 * 글이 바뀌면 처음부터 다시 올라온다.
 */
@Composable
fun RevealText(
    text: String, style: TextStyle, modifier: Modifier = Modifier, delayMs: Int = 0, stagger: Int = 120, durationMs: Int = 1100,
    maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null,
) {
    var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
    val run = remember(text) { Animatable(0f) }
    val total = durationMs + stagger * 6
    LaunchedEffect(text) {
        run.snapTo(0f)
        delay(Pace.ms(delayMs))
        run.animateTo(1f, tween((total * (0.62f + 0.38f * Pace.k)).toInt(), easing = LinearEasing))
    }
    Text(
        text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign, onTextLayout = { lay = it },
        modifier = modifier.drawWithContent {
            val l = lay ?: return@drawWithContent
            val now = run.value * total
            if (run.value >= 1f) { drawContent(); return@drawWithContent }
            for (i in 0 until l.lineCount) {
                val p = Ease.out.transform(((now - i * stagger) / durationMs).coerceIn(0f, 1f))
                if (p <= 0f) continue
                val top = l.getLineTop(i)
                val bottom = l.getLineBottom(i)
                val h = bottom - top
                // 바깥 가림막은 제자리에, 안쪽 가림막은 글과 함께 움직인다 (이웃 줄이 비쳐 보이지 않게)
                clipRect(-40f, top, size.width + 40f, bottom + h * 0.10f) {
                    translate(0f, (1f - p) * h) {
                        clipRect(-40f, top, size.width + 40f, bottom + h * 0.10f) { this@drawWithContent.drawContent() }
                    }
                }
            }
        },
    )
}

/** 굴러서 바뀌는 숫자 (시계, 개수). 숫자가 바뀐 자리만 아래에서 올라온다. */
@Composable
fun RollingNumber(text: String, size: Int, color: Color = C.Hi, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Light, shadow: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            if (ch.isDigit()) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(900, easing = Ease.out)) { it / 2 } + fadeIn(tween(600, delayMillis = 80))) togetherWith
                            (slideOutVertically(tween(520, easing = Ease.inOut)) { -it / 2 } + fadeOut(tween(300)))
                    },
                    label = "digit$i",
                ) { c -> Num(c.toString(), size, color, weight = weight, shadow = shadow) }
            } else Num(ch.toString(), size, color, weight = weight, shadow = shadow)
        }
    }
}

/**
 * 작은 표제: 글자 사이를 넓게 벌린 작은 글. 앞에 점 하나와 가는 선을 그어, 무엇에 대한 글인지 가리킨다.
 */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = C.Hi.copy(alpha = 0.74f), node: Color? = C.Accent, line: Dp = 26.dp, sp: Int = 20) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(120); grow.animateTo(1f, tween(1100, easing = Ease.out)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (node != null) {
            Canvas(Modifier.width(line + 10.dp).height(12.dp)) {
                val y = size.height / 2
                val r = 3.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(node.copy(alpha = 0.55f), Color.Transparent), Offset(r, y), r * 3.2f), r * 3.2f, Offset(r, y))
                drawCircle(node, r * (0.4f + 0.6f * grow.value), Offset(r, y))
                val x1 = r * 2.6f + (size.width - r * 2.6f) * grow.value
                drawLine(Brush.horizontalGradient(listOf(node.copy(alpha = 0.9f), color.copy(alpha = 0.25f)), r * 2.6f, size.width), Offset(r * 2.6f, y), Offset(x1, y), 1.dp.toPx(), StrokeCap.Round)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            style = TextStyle(
                fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = sp.sp, color = color, letterSpacing = 0.05.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

/**
 * 하루의 호 — 자정에서 자정까지를 잇는 한 줄의 호. 마음을 남긴 시각마다 그 감정의 빛이 맺히고,
 * 지금의 시각은 호를 따라 움직이는 밝은 점으로 보인다. 처음 나타날 때 왼쪽에서부터 그어진다.
 */
@Composable
fun DayArc(list: List<Entry>, modifier: Modifier = Modifier, now: Calendar? = null, labels: Boolean = true) {
    val clock = LocalClock.current
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(260); draw.animateTo(1f, tween(1800, easing = Ease.out)) }
    Canvas(modifier.fillMaxWidth().height(if (labels) 124.dp else 92.dp)) {
        val w = size.width
        val base = size.height - (if (labels) 32.dp.toPx() else 6.dp.toPx())
        val rise = base - 8.dp.toPx()
        val ink = C.Hi
        fun at(k: Float) = Offset(w * k, base - sin(k * PI).toFloat() * rise)
        val path = Path().apply {
            moveTo(0f, base)
            for (i in 1..64) { val p = at(i / 64f); lineTo(p.x, p.y) }
        }
        // 땅의 선과 호
        drawLine(Brush.horizontalGradient(0f to Color.Transparent, 0.06f to ink.copy(alpha = 0.22f), 0.94f to ink.copy(alpha = 0.22f), 1f to Color.Transparent), Offset(0f, base), Offset(w, base), 1.dp.toPx())
        val pm = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        pm.getSegment(0f, pm.length * draw.value, seg, true)
        drawPath(seg, ink.copy(alpha = 0.42f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        // 지금까지 지나온 길은 밝게
        val nowK = now?.let { (it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) / 1440f }
        if (nowK != null) {
            val lit = Path()
            pm.getSegment(0f, pm.length * minOf(nowK, draw.value), lit, true)
            drawPath(lit, Brush.horizontalGradient(listOf(C.Accent.copy(alpha = 0.25f), C.Accent), 0f, w * nowK.coerceAtLeast(0.02f)), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        // 여섯 시간마다의 작은 눈금
        for (h in 0..4) {
            val k = h / 4f
            if (k > draw.value + 0.001f) continue
            drawCircle(ink.copy(alpha = 0.42f), 1.6.dp.toPx(), Offset(w * k, base))
        }
        // 마음을 남긴 시각의 빛
        list.forEachIndexed { i, e ->
            val c = Calendar.getInstance().apply { timeInMillis = e.time }
            val k = (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) / 1440f
            val a = ((draw.value - k) * 6f).coerceIn(0f, 1f)
            if (a <= 0f) return@forEachIndexed
            val p = at(k)
            val tone = e.mood.tone().a
            val breath = 0.85f + 0.15f * sin(clock.value * 1.4f + i * 1.9f)
            drawLine(tone.copy(alpha = 0.34f * a), Offset(p.x, p.y), Offset(p.x, base), 1.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.60f * a * breath), Color.Transparent), p, 15.dp.toPx()), 15.dp.toPx(), p)
            drawCircle(tone.copy(alpha = a), 4.6.dp.toPx() * (0.5f + 0.5f * a), p)
            drawCircle(Color.White.copy(alpha = 0.9f * a), 1.6.dp.toPx(), p)
        }
        // 지금
        if (nowK != null && draw.value >= nowK) {
            val p = at(nowK)
            val k = (clock.value * 0.55f) % 1f
            drawCircle(ink.copy(alpha = 0.55f * (1f - k)), (5f + 13f * k).dp.toPx(), p, style = Stroke(1.2.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), p, 13.dp.toPx()), 13.dp.toPx(), p)
            drawCircle(Color.White, 3.6.dp.toPx(), p)
        }
    }
}

/** 하루의 호 아래에 놓는 시각 눈금 글자 */
@Composable
fun DayArcLabels(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        listOf("0", "6", "12", "18", "24").forEach {
            Text(it, style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = C.Hi.copy(alpha = 0.66f), fontFeatureSettings = "tnum"))
        }
    }
}
