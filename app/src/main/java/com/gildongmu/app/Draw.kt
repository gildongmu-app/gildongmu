package com.gildongmu.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ================================================================ 조작용 아이콘
//
// 모두 가는 선과 작은 점으로 직접 그린다. 선은 끝이 둥글고, 길의 시작과 끝·가리키는 곳에는 점(마디)을 찍는다.
// draw(0..1)를 주면 선이 그어지는 중간 모습을 그린다 (길잡이에서 고른 아이콘이 다시 그어진다).

enum class G {
    Today, Book, Month, Settings, Mic, Back, Arrow, Plus, Check, Close, Park, Drive, Alone, Together, Rain, Ask, Lock, Wave,
    Trash, Route, Heart, Caution, Minus, Locate, Navi, Speaker, Moon, Scene, Spark, Pause, Pin,
}

@Composable
fun Glyph(g: G, color: Color, size: Dp = 28.dp, stroke: Dp = 2.dp, modifier: Modifier = Modifier, draw: Float = 1f, accent: Color? = null) {
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val sw = stroke.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val k = draw.coerceIn(0f, 1f)
        fun p(x: Float, y: Float) = Offset(x * w, y * w)
        fun line(a: Offset, b: Offset, c: Color = color) = drawLine(c, a, b, sw, StrokeCap.Round)
        /** 선을 k 만큼만 긋는다 */
        fun trace(path: Path, c: Color = color) {
            if (k >= 0.999f) { drawPath(path, c, style = st); return }
            val pm = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            pm.getSegment(0f, pm.length * k, part, true)
            drawPath(part, c, style = st)
        }
        // 빛 한 점: 그 그림의 마디(해의 속, 길이 닿는 곳, 사람의 머리, 책갈피, 마이크의 속)에 맺힌다
        if (accent != null && k > 0.6f) {
            val at = when (g) {
                G.Today -> p(0.50f, 0.575f)
                G.Route -> p(0.74f, 0.225f)
                G.Together -> p(0.38f, 0.32f)
                G.Alone -> p(0.5f, 0.32f)
                G.Book -> p(0.5f, 0.30f)
                G.Mic -> p(0.5f, 0.30f)
                else -> null
            }
            if (at != null) {
                val a = (k - 0.6f) / 0.4f
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.60f * a), Color.Transparent), at, w * 0.30f), w * 0.30f, at)
                drawCircle(accent.copy(alpha = a), w * 0.072f, at)
            }
        }
        when (g) {
            // 오늘: 물 위로 떠오르는 해
            G.Today -> {
                val up = (1f - k) * 0.10f
                line(p(0.10f, 0.68f), p(0.10f + 0.80f * k, 0.68f))
                drawArc(color, 180f, 180f * k, false, p(0.29f, 0.47f + up), Size(0.42f * w, 0.42f * w), style = st)
                if (k > 0.55f) {
                    val a = ((k - 0.55f) / 0.45f)
                    line(p(0.50f, 0.30f), p(0.50f, 0.30f - 0.13f * a))
                    line(p(0.28f, 0.42f), p(0.28f - 0.085f * a, 0.42f - 0.085f * a))
                    line(p(0.72f, 0.42f), p(0.72f + 0.085f * a, 0.42f - 0.085f * a))
                    line(p(0.36f, 0.84f), p(0.36f + 0.28f * a, 0.84f), color.copy(alpha = color.alpha * 0.7f))
                }
            }
            // 일기장: 펼친 책과 책갈피
            G.Book -> {
                trace(Path().apply {
                    moveTo(0.5f * w, 0.30f * w); cubicTo(0.38f * w, 0.20f * w, 0.22f * w, 0.20f * w, 0.10f * w, 0.25f * w)
                    lineTo(0.10f * w, 0.78f * w); cubicTo(0.22f * w, 0.73f * w, 0.38f * w, 0.73f * w, 0.5f * w, 0.83f * w)
                    cubicTo(0.62f * w, 0.73f * w, 0.78f * w, 0.73f * w, 0.90f * w, 0.78f * w); lineTo(0.90f * w, 0.25f * w)
                    cubicTo(0.78f * w, 0.20f * w, 0.62f * w, 0.20f * w, 0.5f * w, 0.30f * w); lineTo(0.5f * w, 0.83f * w)
                })
                if (k > 0.7f) {
                    val a = (k - 0.7f) / 0.3f
                    line(p(0.62f, 0.42f), p(0.62f + 0.17f * a, 0.385f), color.copy(alpha = color.alpha * 0.75f))
                    line(p(0.62f, 0.55f), p(0.62f + 0.12f * a, 0.525f), color.copy(alpha = color.alpha * 0.75f))
                }
            }
            // 이번 달: 달력
            G.Month -> {
                drawRoundRect(color, p(0.14f, 0.20f), Size(0.72f * w, 0.66f * w), CornerRadius(0.14f * w), style = st)
                line(p(0.14f, 0.41f), p(0.86f, 0.41f))
                line(p(0.34f, 0.12f), p(0.34f, 0.26f)); line(p(0.66f, 0.12f), p(0.66f, 0.26f))
                drawCircle(color, w * 0.048f, p(0.34f, 0.62f)); drawCircle(color, w * 0.048f, p(0.5f, 0.62f)); drawCircle(color, w * 0.048f, p(0.66f, 0.62f))
            }
            // 설정: 조절 막대 둘
            G.Settings -> {
                line(p(0.12f, 0.34f), p(0.88f, 0.34f)); line(p(0.12f, 0.68f), p(0.88f, 0.68f))
                drawCircle(color, w * 0.095f, p(0.36f, 0.34f)); drawCircle(color, w * 0.095f, p(0.64f, 0.68f))
            }
            G.Mic -> {
                drawRoundRect(color, p(0.365f, 0.10f), Size(0.27f * w, 0.47f * w), CornerRadius(0.135f * w), style = st)
                drawArc(color, 10f, 160f, false, p(0.22f, 0.24f), Size(0.56f * w, 0.50f * w), style = st)
                line(p(0.5f, 0.74f), p(0.5f, 0.88f)); line(p(0.37f, 0.88f), p(0.63f, 0.88f))
            }
            G.Back -> drawPath(Path().apply { moveTo(0.60f * w, 0.22f * w); lineTo(0.34f * w, 0.5f * w); lineTo(0.60f * w, 0.78f * w) }, color, style = st)
            G.Arrow -> {
                line(p(0.16f, 0.5f), p(0.84f, 0.5f))
                drawPath(Path().apply { moveTo(0.60f * w, 0.26f * w); lineTo(0.84f * w, 0.5f * w); lineTo(0.60f * w, 0.74f * w) }, color, style = st)
            }
            G.Plus -> { line(p(0.5f, 0.20f), p(0.5f, 0.80f)); line(p(0.20f, 0.5f), p(0.80f, 0.5f)) }
            G.Minus -> line(p(0.20f, 0.5f), p(0.80f, 0.5f))
            G.Check -> drawPath(Path().apply { moveTo(0.18f * w, 0.52f * w); lineTo(0.42f * w, 0.74f * w); lineTo(0.84f * w, 0.28f * w) }, color, style = st)
            G.Close -> { line(p(0.26f, 0.26f), p(0.74f, 0.74f)); line(p(0.74f, 0.26f), p(0.26f, 0.74f)) }
            G.Pause -> { line(p(0.38f, 0.26f), p(0.38f, 0.74f)); line(p(0.62f, 0.26f), p(0.62f, 0.74f)) }
            // 정차: 동그라미 안의 P
            G.Park -> {
                drawCircle(color, w * 0.39f, p(0.5f, 0.5f), style = st)
                drawPath(Path().apply {
                    moveTo(0.41f * w, 0.72f * w); lineTo(0.41f * w, 0.30f * w); lineTo(0.52f * w, 0.30f * w)
                    cubicTo(0.68f * w, 0.30f * w, 0.68f * w, 0.53f * w, 0.52f * w, 0.53f * w); lineTo(0.41f * w, 0.53f * w)
                }, color, style = st)
            }
            // 주행: 운전대
            G.Drive -> {
                drawCircle(color, w * 0.38f, p(0.5f, 0.5f), style = st)
                drawCircle(color, w * 0.075f, p(0.5f, 0.54f))
                line(p(0.14f, 0.47f), p(0.42f, 0.53f)); line(p(0.86f, 0.47f), p(0.58f, 0.53f)); line(p(0.5f, 0.62f), p(0.5f, 0.86f))
            }
            G.Alone -> person(p(0.5f, 0.32f), w * 0.14f, w, color, st)
            // 친구: 나란히 선 두 사람
            G.Together -> {
                person(p(0.66f, 0.36f), w * 0.105f, w * 0.74f, color.copy(alpha = color.alpha * 0.62f), st)
                person(p(0.38f, 0.32f), w * 0.13f, w * 0.90f, color, st)
            }
            G.Rain -> drawPath(Path().apply {
                moveTo(0.5f * w, 0.12f * w); cubicTo(0.72f * w, 0.40f * w, 0.80f * w, 0.52f * w, 0.78f * w, 0.64f * w)
                cubicTo(0.76f * w, 0.82f * w, 0.62f * w, 0.90f * w, 0.5f * w, 0.90f * w); cubicTo(0.38f * w, 0.90f * w, 0.24f * w, 0.82f * w, 0.22f * w, 0.64f * w)
                cubicTo(0.20f * w, 0.52f * w, 0.28f * w, 0.40f * w, 0.5f * w, 0.12f * w); close()
            }, color, style = st)
            // 길동무가 먼저 묻기: 말줄임표가 든 말칸
            G.Ask -> {
                drawRoundRect(color, p(0.12f, 0.18f), Size(0.76f * w, 0.52f * w), CornerRadius(0.20f * w), style = st)
                drawPath(Path().apply { moveTo(0.34f * w, 0.70f * w); lineTo(0.30f * w, 0.86f * w); lineTo(0.48f * w, 0.70f * w) }, color, style = st)
                for (i in 0..2) drawCircle(color, w * 0.04f, p(0.34f + i * 0.16f, 0.44f))
            }
            G.Lock -> {
                drawRoundRect(color, p(0.22f, 0.46f), Size(0.56f * w, 0.42f * w), CornerRadius(0.12f * w), style = st)
                drawArc(color, 180f, 180f, false, p(0.32f, 0.16f), Size(0.36f * w, 0.48f * w), style = st)
                line(p(0.32f, 0.40f), p(0.32f, 0.46f)); line(p(0.68f, 0.40f), p(0.68f, 0.46f))
            }
            G.Wave -> listOf(0.22f, 0.48f, 0.74f, 0.44f, 0.26f).forEachIndexed { i, h ->
                val x = (0.18f + i * 0.16f) * w
                drawLine(color, Offset(x, (0.5f - h / 2) * w), Offset(x, (0.5f + h / 2) * w), sw, StrokeCap.Round)
            }
            G.Trash -> {
                line(p(0.16f, 0.26f), p(0.84f, 0.26f))
                drawPath(Path().apply { moveTo(0.40f * w, 0.26f * w); lineTo(0.40f * w, 0.16f * w); lineTo(0.60f * w, 0.16f * w); lineTo(0.60f * w, 0.26f * w) }, color, style = st)
                drawPath(Path().apply { moveTo(0.26f * w, 0.26f * w); lineTo(0.30f * w, 0.86f * w); lineTo(0.70f * w, 0.86f * w); lineTo(0.74f * w, 0.26f * w) }, color, style = st)
            }
            // 길 위: 떠난 자리의 점에서 닿을 곳의 고리까지, 굽은 길
            G.Route -> {
                drawCircle(color, 0.052f * w, p(0.26f, 0.86f))
                trace(Path().apply { moveTo(0.26f * w, 0.86f * w); cubicTo(0.26f * w, 0.54f * w, 0.74f * w, 0.66f * w, 0.74f * w, 0.335f * w) })
                if (k > 0.8f) drawCircle(color, 0.10f * w * ((k - 0.8f) / 0.2f), p(0.74f, 0.225f), style = st)
            }
            G.Heart -> drawPath(Path().apply {
                moveTo(0.5f * w, 0.84f * w); cubicTo(0.10f * w, 0.58f * w, 0.12f * w, 0.20f * w, 0.34f * w, 0.20f * w)
                cubicTo(0.44f * w, 0.20f * w, 0.50f * w, 0.28f * w, 0.50f * w, 0.34f * w); cubicTo(0.50f * w, 0.28f * w, 0.56f * w, 0.20f * w, 0.66f * w, 0.20f * w)
                cubicTo(0.88f * w, 0.20f * w, 0.90f * w, 0.58f * w, 0.5f * w, 0.84f * w); close()
            }, color, style = st)
            G.Caution -> {
                drawPath(Path().apply { moveTo(0.5f * w, 0.14f * w); lineTo(0.90f * w, 0.84f * w); lineTo(0.10f * w, 0.84f * w); close() }, color, style = st)
                line(p(0.5f, 0.40f), p(0.5f, 0.60f))
                drawCircle(color, 0.045f * w, p(0.5f, 0.72f))
            }
            G.Locate -> {
                drawCircle(color, w * 0.26f, p(0.5f, 0.5f), style = st)
                drawCircle(color, w * 0.075f, p(0.5f, 0.5f))
                line(p(0.5f, 0.06f), p(0.5f, 0.20f)); line(p(0.5f, 0.80f), p(0.5f, 0.94f))
                line(p(0.06f, 0.5f), p(0.20f, 0.5f)); line(p(0.80f, 0.5f), p(0.94f, 0.5f))
            }
            G.Navi -> drawPath(Path().apply {
                moveTo(0.5f * w, 0.10f * w); lineTo(0.84f * w, 0.88f * w); lineTo(0.5f * w, 0.70f * w); lineTo(0.16f * w, 0.88f * w); close()
            }, color, style = st)
            G.Speaker -> {
                drawPath(Path().apply {
                    moveTo(0.14f * w, 0.38f * w); lineTo(0.30f * w, 0.38f * w); lineTo(0.50f * w, 0.20f * w); lineTo(0.50f * w, 0.80f * w)
                    lineTo(0.30f * w, 0.62f * w); lineTo(0.14f * w, 0.62f * w); close()
                }, color, style = st)
                drawArc(color, -45f, 90f, false, p(0.48f, 0.34f), Size(0.24f * w, 0.32f * w), style = st)
                drawArc(color, -50f, 100f, false, p(0.48f, 0.20f), Size(0.40f * w, 0.60f * w), style = st)
            }
            G.Moon -> {
                val disk = Path().apply { addOval(Rect(p(0.16f, 0.16f), p(0.84f, 0.84f))) }
                val cut = Path().apply { addOval(Rect(p(0.38f, 0.04f), p(0.98f, 0.64f))) }
                drawPath(Path().apply { op(disk, cut, PathOperation.Difference) }, color, style = st)
            }
            // 바탕 고르기: 창 안의 언덕과 해
            G.Scene -> {
                drawRoundRect(color, p(0.11f, 0.17f), Size(0.78f * w, 0.66f * w), CornerRadius(0.20f * w), style = st)
                drawPath(Path().apply {
                    moveTo(0.14f * w, 0.70f * w); cubicTo(0.28f * w, 0.46f * w, 0.42f * w, 0.50f * w, 0.54f * w, 0.64f * w)
                    cubicTo(0.64f * w, 0.54f * w, 0.76f * w, 0.54f * w, 0.86f * w, 0.66f * w)
                }, color, style = st)
                drawCircle(color, 0.055f * w, p(0.67f, 0.36f))
            }
            // 이 자리에: 지도 위의 핀
            G.Pin -> {
                drawPath(Path().apply {
                    moveTo(0.5f * w, 0.90f * w); cubicTo(0.18f * w, 0.58f * w, 0.20f * w, 0.12f * w, 0.5f * w, 0.12f * w)
                    cubicTo(0.80f * w, 0.12f * w, 0.82f * w, 0.58f * w, 0.5f * w, 0.90f * w); close()
                }, color, style = st)
                drawCircle(color, 0.085f * w, p(0.5f, 0.41f))
            }
            // 반짝임: 네 꼭짓점의 별
            G.Spark -> spark(p(0.5f, 0.5f), 0.44f * w, color)
        }
    }
}

/** 네 꼭짓점의 별 (속을 채워서) */
fun DrawScope.spark(c: Offset, r: Float, color: Color) {
    val q = r * 0.10f
    drawPath(Path().apply {
        moveTo(c.x, c.y - r); cubicTo(c.x + q, c.y - q * 2.2f, c.x + q * 2.2f, c.y - q, c.x + r, c.y)
        cubicTo(c.x + q * 2.2f, c.y + q, c.x + q, c.y + q * 2.2f, c.x, c.y + r)
        cubicTo(c.x - q, c.y + q * 2.2f, c.x - q * 2.2f, c.y + q, c.x - r, c.y)
        cubicTo(c.x - q * 2.2f, c.y - q, c.x - q, c.y - q * 2.2f, c.x, c.y - r); close()
    }, color)
}

private fun DrawScope.person(head: Offset, r: Float, width: Float, color: Color, st: Stroke) {
    drawCircle(color, r, head, style = st)
    drawPath(Path().apply {
        moveTo(head.x - width * 0.30f, head.y + r * 3.6f)
        cubicTo(head.x - width * 0.28f, head.y + r * 1.8f, head.x + width * 0.28f, head.y + r * 1.8f, head.x + width * 0.30f, head.y + r * 3.6f)
    }, color, style = st)
}
