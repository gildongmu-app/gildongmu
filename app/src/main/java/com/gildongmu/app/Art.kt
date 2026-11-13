package com.gildongmu.app

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.Paint as APaint
import android.graphics.Path as APath

/**
 * 작은 그림들: 감정 일곱 가지와 길 위의 표지.
 * 모두 가는 선 몇 줄로 그린 그림이고, 은은하게 빛나는 유리 알 안에 담아 쓴다.
 */

private val pen = APaint(APaint.ANTI_ALIAS_FLAG)
private fun pen(): APaint = pen.apply { reset(); isAntiAlias = true; strokeJoin = APaint.Join.ROUND; strokeCap = APaint.Cap.ROUND }

enum class Ic { SUN, HEART, WAVE, CLOUD, MOON, RAIN, BOLT, STAR, CAUTION }

fun Mood.ic(): Ic = when (this) {
    Mood.JOY -> Ic.SUN; Mood.FLUTTER -> Ic.HEART; Mood.CALM -> Ic.WAVE; Mood.WORRY -> Ic.CLOUD
    Mood.TIRED -> Ic.MOON; Mood.SAD -> Ic.RAIN; Mood.ANGRY -> Ic.BOLT
}

private fun circle(x: Float, y: Float, r: Float) = APath().apply { addCircle(x, y, r, APath.Direction.CW) }
private fun union(vararg p: APath) = APath().apply { p.forEach { op(it, APath.Op.UNION) } }
private fun cloudShape(dx: Float, dy: Float, k: Float) = union(
    circle(dx - 0.40f * k, dy + 0.14f * k, 0.34f * k), circle(dx + 0.02f * k, dy - 0.12f * k, 0.48f * k), circle(dx + 0.48f * k, dy + 0.16f * k, 0.32f * k),
    APath().apply { addRoundRect(RectF(dx - 0.74f * k, dy + 0.10f * k, dx + 0.80f * k, dy + 0.48f * k), 0.19f * k, 0.19f * k, APath.Direction.CW) },
)

/** 반지름 1 짜리 선 그림들 (그릴 때 늘려 쓴다). 별만은 속을 채운 네 꼭짓점의 반짝임이다. */
private val shapes: Map<Ic, APath> by lazy {
    mapOf(
        Ic.HEART to APath().apply {
            moveTo(0f, 0.78f); cubicTo(-1.24f, -0.05f, -0.60f, -0.98f, 0f, -0.34f); cubicTo(0.60f, -0.98f, 1.24f, -0.05f, 0f, 0.78f); close()
        },
        Ic.SUN to APath().apply {
            addCircle(0f, 0f, 0.40f, APath.Direction.CW)
            for (k in 0 until 8) {
                val a = k * PI / 4
                moveTo((cos(a) * 0.66).toFloat(), (sin(a) * 0.66).toFloat()); lineTo((cos(a) * 0.94).toFloat(), (sin(a) * 0.94).toFloat())
            }
        },
        Ic.CLOUD to cloudShape(0f, -0.06f, 1f),
        Ic.MOON to APath().apply { addCircle(-0.08f, 0f, 0.80f, APath.Direction.CW); op(circle(0.36f, -0.26f, 0.70f), APath.Op.DIFFERENCE) },
        Ic.RAIN to APath().apply {
            addPath(cloudShape(0f, -0.34f, 0.80f))
            for (x in floatArrayOf(-0.42f, 0f, 0.42f)) { moveTo(x + 0.06f, 0.44f); lineTo(x - 0.06f, 0.86f) }
        },
        Ic.BOLT to APath().apply {
            moveTo(0.20f, -0.94f); lineTo(-0.52f, 0.10f); lineTo(-0.04f, 0.10f); lineTo(-0.22f, 0.94f); lineTo(0.56f, -0.20f); lineTo(0.08f, -0.20f); close()
        },
        Ic.WAVE to APath().apply {
            for (y in floatArrayOf(-0.32f, 0.32f)) {
                moveTo(-0.90f, y)
                cubicTo(-0.60f, y - 0.44f, -0.30f, y - 0.44f, 0f, y); cubicTo(0.30f, y + 0.44f, 0.60f, y + 0.44f, 0.90f, y)
            }
        },
        Ic.STAR to APath().apply {
            val q = 0.11f
            moveTo(0f, -1f); cubicTo(q, -q * 2.2f, q * 2.2f, -q, 1f, 0f); cubicTo(q * 2.2f, q, q, q * 2.2f, 0f, 1f)
            cubicTo(-q, q * 2.2f, -q * 2.2f, q, -1f, 0f); cubicTo(-q * 2.2f, -q, -q, -q * 2.2f, 0f, -1f); close()
        },
        Ic.CAUTION to APath().apply {
            moveTo(0f, -0.84f); lineTo(0.92f, 0.72f); lineTo(-0.92f, 0.72f); close()
            moveTo(0f, -0.26f); lineTo(0f, 0.20f)
            moveTo(0f, 0.46f); lineTo(0f, 0.47f)
        },
    )
}

/** 아이콘 하나: 가는 선 그림 (별은 속을 채운 반짝임) */
fun DrawScope.icon(ic: Ic, center: Offset, r: Float, color: Color) {
    val path = shapes.getValue(ic)
    drawIntoCanvas { cv ->
        val c = cv.nativeCanvas
        c.save(); c.translate(center.x, center.y); c.scale(r, r)
        val p = pen()
        if (ic == Ic.STAR) { p.style = APaint.Style.FILL }
        else { p.style = APaint.Style.STROKE; p.strokeWidth = 0.15f; p.pathEffect = android.graphics.CornerPathEffect(0.12f) }
        p.color = color.toArgb()
        c.drawPath(path, p)
        c.restore()
    }
}

/** 빛나는 유리 알 안의 아이콘: 둘레로 빛이 번지고, 가는 테를 두른 알 안에 그 감정·표지의 그림이 뜬다 */
fun DrawScope.glowIcon(ic: Ic, center: Offset, r: Float, tone: Color, k: Float = 1f, alpha: Float = 1f) {
    drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.50f * k * alpha), tone.copy(alpha = 0.13f * k * alpha), Color.Transparent), center, r), r, center)
    val rr = r * 0.56f
    drawCircle(Brush.verticalGradient(listOf(lerp(tone, Color.White, 0.20f).copy(alpha = 0.38f * alpha), tone.copy(alpha = 0.16f * alpha)), center.y - rr, center.y + rr), rr, center)
    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.62f * alpha), Color.White.copy(alpha = 0.10f * alpha)), center.y - rr, center.y + rr), rr, center, style = Stroke((r * 0.028f).coerceAtLeast(1f)))
    icon(ic, center, r * (if (ic == Ic.STAR) 0.30f else 0.27f), lerp(tone, Color.White, 0.78f).copy(alpha = alpha))
}

/** 흩어지는 빛 (누르거나, 지나가며 떠오를 때): 둥근 조각들이 퍼진다 */
internal fun DrawScope.softBurst(c: Offset, tone: Color, age: Float, s: Float, seed: Int) {
    val p = (age / 1.1f).coerceIn(0f, 1f)
    if (p >= 1f) return
    val e = 1f - (1f - p) * (1f - p) * (1f - p)
    val fade = 1f - p
    drawCircle(tone.copy(alpha = fade * 0.5f), 16f * s + e * 70f * s, c, style = Stroke(2.4f * s * fade + 0.5f))
    drawCircle(Color.White.copy(alpha = fade * 0.25f), 16f * s + e * 104f * s, c, style = Stroke(1.2f * s * fade + 0.4f))
    for (k in 0 until 14) {
        val ang = k / 14f * 2 * PI + rnd(seed, k) * 0.7
        val dist = (34f + rnd(seed + 5, k) * 70f) * s * e
        val pos = Offset(c.x + cos(ang).toFloat() * dist, c.y + sin(ang).toFloat() * dist + p * p * 34f * s)
        drawCircle((if (k % 3 == 0) Color.White else tone).copy(alpha = fade), (2.2f + rnd(seed, k + 9) * 3.0f) * s * (1f - p * 0.5f), pos)
    }
}

/**
 * 길 위에 남겨진 이야기 하나: 땅에 잔물결처럼 고리가 번지고, 거기서 가는 빛줄기가 올라와 그 끝에 빛나는 알이 떠 있다.
 * 반짝임 = 한마디, 하트 = 공유한 일기, 세모 = 주의. 알의 중심을 돌려준다.
 */
internal fun DrawScope.placeMarker(
    ground: Offset, p: Place, tone: Color, scale: Float, t: Float, a: Float, selected: Boolean,
    burstAge: Float, dropAge: Float, liked: Boolean, seed: Int,
): Offset {
    if (a <= 0.01f) return ground
    val s = scale * (if (selected) 1.22f else 1f)
    val al = a.coerceIn(0f, 1f)
    val lift = if (dropAge in 0f..1f) { val q = 1f - dropAge; q * q * q * 170f } else 0f
    val bob = sin(t * 1.3f + seed * 0.9f) * 3f * s
    val half = 22f * s
    val head = Offset(ground.x, ground.y - 40f * s - half + bob - lift)
    // 땅: 눕혀 놓은 고리가 잔물결처럼 번져 나간다
    for (k in 0 until 2) {
        val ph = ((t * 0.45f + seed * 0.13f + k * 0.5f) % 1f)
        val rr = (8f + 30f * ph) * s
        drawOval(tone.copy(alpha = (1f - ph) * (if (selected) 0.60f else 0.34f) * al), Offset(ground.x - rr, ground.y - rr * 0.40f), Size(rr * 2f, rr * 0.80f), style = Stroke(1.3f * s))
    }
    drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.60f * al), Color.Transparent), ground, 11f * s), 11f * s, ground)
    drawCircle(Color.White.copy(alpha = 0.85f * al), 2.2f * s, ground)
    // 가는 빛줄기
    drawLine(Brush.verticalGradient(listOf(tone.copy(alpha = 0.85f * al), tone.copy(alpha = 0.10f * al)), startY = head.y + half, endY = ground.y), Offset(head.x, head.y + half * 0.72f), ground, 1.4f * s)
    val pulse = 0.82f + 0.18f * sin(t * 2.1f + seed)
    val ic = when (p.kind) { PlaceKind.VOICE -> Ic.STAR; PlaceKind.DIARY -> Ic.HEART; PlaceKind.CAUTION -> Ic.CAUTION }
    glowIcon(ic, head, half * (if (selected) 1.95f else 1.62f), tone, pulse, al)
    if (selected) {
        // 고른 이야기: 알 둘레를 도는 가는 고리와 점 하나
        val rr = half * 1.26f
        drawCircle(Color.White.copy(alpha = 0.50f * al), rr, head, style = Stroke(1.2f * s))
        val ang = t * 1.4f
        drawCircle(Color.White.copy(alpha = al), 3.0f * s, Offset(head.x + cos(ang) * rr, head.y + sin(ang) * rr))
    }
    val extra = (p.words.size - 1).coerceIn(0, 4)
    for (j in 0 until extra) drawCircle(tone.copy(alpha = 0.9f * al), 1.9f * s, Offset(head.x + (j - (extra - 1) / 2f) * 7f * s, head.y + half + 9f * s))
    if (p.hasMine) drawCircle(C.Accent.copy(alpha = al), 3.6f * s, Offset(head.x - half * 0.80f, head.y - half * 0.80f))
    if (liked) drawCircle(C.Red.copy(alpha = al), 3.6f * s, Offset(head.x + half * 0.80f, head.y - half * 0.80f))
    if (burstAge in 0f..1.2f) softBurst(head, tone, burstAge, s, seed)
    return head
}
