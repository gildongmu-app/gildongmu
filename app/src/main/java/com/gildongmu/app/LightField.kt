package com.gildongmu.app

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * 빛의 바탕 — 글 뒤에 깔리는 것.
 *
 * 길동무의 화면에는 그림도 사진도 없다. 운전하는 사람의 말이 주인공이고, 바탕은 천천히 번지는 빛깔뿐이다.
 *  - 빛깔(SceneKind)마다 다섯 가지 색: 위·아래의 바탕색과, 그 위를 천천히 떠다니는 빛 세 덩이
 *  - 시각이 바뀌면 같은 빛깔이 새벽·낮·노을·밤의 빛으로 물든다 (하루를 덮을 때 그대로 밤으로 저문다)
 *  - 달리는 동안에는 빛이 차창 밖처럼 옆으로 흘러간다
 *  - 결(Art)에 따라 번지는 모양이 다르다: 오로라(부드럽게 번짐), 그레인(입자와 색면), 먹(먹빛 종이 위의 옅은 빛)
 */
internal class Hue(val top: Color, val bot: Color, val a: Color, val b: Color, val c: Color)

private fun hue(top: Long, bot: Long, a: Long, b: Long, c: Long) = Hue(Color(top), Color(bot), Color(a), Color(b), Color(c))

/** 빛깔마다의 색 (그 빛깔이 본래 가진 시각의 빛으로) */
private val HUES = mapOf(
    SceneKind.GINKGO to hue(0xFFB48F2C, 0xFF5A4A7A, 0xFFFFE08A, 0xFFFFB36B, 0xFFB79CFF),
    SceneKind.REEDS to hue(0xFF54477C, 0xFF9A5560, 0xFFFFB56E, 0xFFF08C6C, 0xFFF3D9B0),
    SceneKind.COAST to hue(0xFF1F7087, 0xFF0E3F58, 0xFF6FE0D2, 0xFFB4F0F2, 0xFF3AA7BC),
    SceneKind.CITY to hue(0xFF1C2556, 0xFF2E2046, 0xFFFFB061, 0xFF6C7BFF, 0xFFC0507A),
    SceneKind.RIDGES to hue(0xFF475782, 0xFF80586E, 0xFFFFC3A8, 0xFFB9C4F0, 0xFFE89AA6),
    SceneKind.SNOW to hue(0xFF5F7FA6, 0xFF34527A, 0xFFD8ECFF, 0xFFAFC8E8, 0xFFB7A8E6),
    SceneKind.BLOSSOM to hue(0xFFB0657F, 0xFF64407A, 0xFFFFC4D6, 0xFFFFE0C2, 0xFFC58AE0),
    SceneKind.GREEN to hue(0xFF2F7F5C, 0xFF1A4A52, 0xFFB8F08A, 0xFF6FD6A8, 0xFFE8F0A0),
    SceneKind.RAIN to hue(0xFF141B3A, 0xFF0C1022, 0xFF6E86FF, 0xFF9A6BD6, 0xFF3FA7C9),
    SceneKind.AURORA to hue(0xFF0A0E24, 0xFF05060F, 0xFF3D6BFF, 0xFF2BD9C9, 0xFF9A5BFF),
)

private fun mix(x: Hue, y: Hue, k: Float): Hue =
    if (k <= 0f) x else if (k >= 1f) y else Hue(lerp(x.top, y.top, k), lerp(x.bot, y.bot, k), lerp(x.a, y.a, k), lerp(x.b, y.b, k), lerp(x.c, y.c, k))

private fun Color.dark(k: Float) = Color(red * k, green * k, blue * k, alpha)

/** 그 빛깔을 다른 시각의 빛으로 물들인다 */
private fun shift(h: Hue, from: DayPart, to: DayPart): Hue = when {
    from == to -> h
    to == DayPart.DAWN -> Hue(
        lerp(h.top, Color(0xFF7F8CBC), 0.50f), lerp(h.bot, Color(0xFFB98F98), 0.45f),
        lerp(h.a, Color(0xFFFFD2BE), 0.50f), lerp(h.b, Color(0xFFC6CCF2), 0.45f), lerp(h.c, Color(0xFFF0B0B8), 0.40f),
    )
    to == DayPart.DAY -> Hue(
        lerp(h.top, Color(0xFF3F7CA8), 0.30f), lerp(h.bot, Color(0xFF2A5A7C), 0.30f),
        lerp(h.a, Color.White, 0.12f), lerp(h.b, Color.White, 0.10f), h.c,
    )
    to == DayPart.DUSK -> Hue(
        lerp(h.top, Color(0xFF453C7C), 0.60f), lerp(h.bot, Color(0xFFA2506A), 0.55f),
        lerp(h.a, Color(0xFFFFB886), 0.50f), lerp(h.b, Color(0xFFF08A8A), 0.45f), lerp(h.c, Color(0xFF9A6BD6), 0.35f),
    )
    else -> Hue(   // 밤: 가라앉히고 남빛으로
        lerp(h.top.dark(0.34f), Color(0xFF0E1330), 0.55f), lerp(h.bot.dark(0.30f), Color(0xFF080A18), 0.55f),
        lerp(h.a.dark(0.62f), Color(0xFF3B4FB0), 0.30f), lerp(h.b.dark(0.55f), Color(0xFF5A3F96), 0.30f), h.c.dark(0.50f),
    )
}

/** 지금 그릴 색: 빛깔 + 시각 + 저무는 정도 + 비 + 결 */
internal fun hueAt(scene: SceneKind, part: DayPart, night: Float = 0f, rain: Boolean = false, art: Art = Skin.art): Hue {
    val own = HUES.getValue(scene)
    var h = shift(own, scene.light, part)
    if (night > 0f) h = mix(h, shift(own, scene.light, DayPart.NIGHT), night)
    if (rain && scene != SceneKind.RAIN) {
        val wet = Color(0xFF55607A)
        h = Hue(lerp(h.top, wet, 0.38f).dark(0.86f), lerp(h.bot, wet, 0.34f).dark(0.84f), lerp(h.a, wet, 0.30f), lerp(h.b, wet, 0.30f), lerp(h.c, wet, 0.30f))
    }
    if (art == Art.INK) {
        // 먹빛 종이: 바탕은 따뜻한 먹색, 그 빛깔은 옅은 번짐으로만 남는다
        h = Hue(lerp(Color(0xFF292520), h.top, 0.12f), lerp(Color(0xFF151210), h.bot, 0.10f), lerp(h.a, Color(0xFF8A7F70), 0.35f), lerp(h.b, Color(0xFF6E665C), 0.45f), lerp(h.c, Color(0xFF6E665C), 0.50f))
    }
    return h
}

/** 빛이 흘러간 자리: travel 은 달리는 동안 흘러간 거리 (화면이 바뀌어도 이어진다) */
class ScenePan { var last = -1f; var travel = 0f; var lastN = 0L }

/** 화면이 바뀌어도 바탕의 빛은 이어서 흘러가도록, 앱 전체가 함께 쓰는 자리 */
val LocalScenePan = staticCompositionLocalOf<ScenePan?> { null }

/** 지금 빛의 바탕이 깔려 있다는 표시. 그 위의 유리(frost)와 안개(mist)가 맑게 설지, 어둑하게 설지를 정한다. */
class Backdrop { var origin = Offset.Zero; val glass = Glass() }

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private const val FIELD_SRC = """
uniform float2 uSize;
uniform float uTime;
uniform float uFlow;
uniform float uSeed;
uniform float uGrain;
uniform float uGlow;
uniform float uBand;
layout(color) uniform half4 uTop;
layout(color) uniform half4 uBot;
layout(color) uniform half4 uA;
layout(color) uniform half4 uB;
layout(color) uniform half4 uC;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

half4 main(float2 xy) {
    float2 uv = xy / uSize;
    float asp = uSize.x / uSize.y;
    float2 p = float2(uv.x * asp, uv.y);
    float t = uTime;
    // 결을 따라 살짝 일렁인다
    float2 q = p + 0.10 * float2(sin(p.y * 2.3 + t * 0.23 + uSeed), cos(p.x * 1.9 - t * 0.19 + uSeed * 1.7));
    float g = smoothstep(-0.05, 1.05, uv.y + 0.06 * sin(q.x * 1.6 + t * 0.11));
    half3 col = mix(uTop.rgb, uBot.rgb, half(g));
    // 빛 세 덩이: 저마다 다른 빠르기로 떠다니고, 달리는 동안에는 옆으로 흘러간다
    float period = asp + 2.6;
    float2 ca = float2(mod(asp * 0.26 + 0.16 * sin(t * 0.13 + uSeed) - uFlow * 0.9 + 1.3, period) - 1.3, 0.36 + 0.10 * cos(t * 0.11 + uSeed));
    float2 cb = float2(mod(asp * 0.82 + 0.14 * cos(t * 0.09 + uSeed * 2.1) - uFlow * 0.6 + 1.3, period) - 1.3, 0.74 + 0.09 * sin(t * 0.14));
    float2 cc = float2(mod(asp * 0.60 + 0.20 * sin(t * 0.07 + 1.7) - uFlow * 1.3 + 1.3, period) - 1.3, 0.10 + 0.08 * cos(t * 0.10 + 2.0));
    float da = length((q - ca) * float2(0.80, 1.20));
    float db = length((q - cb) * float2(0.72, 1.05));
    float dc = length((q - cc) * float2(1.05, 1.45));
    col = mix(col, uA.rgb, half(exp(-da * da * 2.4) * uGlow));
    col = mix(col, uB.rgb, half(exp(-db * db * 2.8) * uGlow * 0.85));
    col = mix(col, uC.rgb, half(exp(-dc * dc * 5.0) * uGlow * 0.70));
    // 색면: 밝기를 몇 단으로 끊고 입자로 풀어서, 종이에 찍은 듯하게
    if (uBand > 0.5) {
        float l = dot(col, half3(0.299, 0.587, 0.114));
        float s = (floor(l * uBand + hash21(xy) * 0.999) + 0.5) / uBand;
        col *= half(mix(1.0, s / max(l, 0.02), 0.55));
    }
    float n = hash21(xy + fract(t * 0.37) * 91.0) - 0.5;
    col += half3(half(n * uGrain));
    // 가장자리는 살짝 가라앉힌다
    float2 v = uv - 0.5;
    col *= half(1.0 - 0.34 * dot(v, v));
    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0);
}
"""

/**
 * 빛의 바탕을 그린다.
 * speed: 흘러가는 빠르기 (정차 중 0.1, 주행 중 3), dim: 어둡게 하는 정도, night: 0→1 로 올리면 밤으로 저문다,
 * seed: 빛이 놓이는 자리 (날마다 다르게), still: 멈춘 그림 (작은 동그라미 여러 개를 놓을 때), soft: 0→1 로 올리면 빛이 잔잔해진다
 */
@Composable
internal fun LightField(
    modifier: Modifier, scene: SceneKind, part: DayPart, rain: Boolean, speed: Float, dim: Float, night: Float,
    seed: Int, still: Boolean, soft: Float, art: Art,
) {
    val clock = LocalClock.current
    val sp by animateFloatAsState(speed, tween(2200), label = "speed")
    val shared = LocalScenePan.current
    val own = remember { ScenePan() }
    val pan = if (still || shared == null) own else shared
    val shader = if (!still && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember { RuntimeShader(FIELD_SRC) } else null
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    Canvas(modifier.clipToBounds()) {
        val t = if (still) 12f + seed * 3f else clock.value
        if (!still) {
            val dt = if (pan.last >= 0f) (t - pan.last).coerceIn(0f, 0.1f) else 0f
            pan.last = t
            if (sp >= 0.5f) pan.travel += dt * sp * 0.085f
        }
        val h = hueAt(scene, part, night, rain, art)
        val glow = (when (art) { Art.AURA -> 0.86f; Art.GRAIN -> 0.80f; Art.INK -> 0.42f }) * (1f - 0.30f * soft.coerceIn(0f, 1f))
        if (shader != null && brush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) field(shader, brush, h, t, pan.travel, seed, glow, art)
        else plain(h, t, seed, glow)
        val dark = maxOf(if (part == DayPart.NIGHT || scene.light == DayPart.NIGHT) 1f else 0f, night)
        if (!still) motes(scene, rain, t, pan.travel, dark, 1f - 0.5f * soft.coerceIn(0f, 1f))
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.92f)))
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.field(shader: RuntimeShader, brush: ShaderBrush, h: Hue, t: Float, flow: Float, seed: Int, glow: Float, art: Art) {
    shader.setFloatUniform("uSize", size.width, size.height)
    shader.setFloatUniform("uTime", t)
    shader.setFloatUniform("uFlow", flow)
    shader.setFloatUniform("uSeed", seed * 1.37f)
    shader.setFloatUniform("uGrain", when (art) { Art.AURA -> 0.030f; Art.GRAIN -> 0.075f; Art.INK -> 0.05f })
    shader.setFloatUniform("uGlow", glow)
    shader.setFloatUniform("uBand", if (art == Art.GRAIN) 7f else 0f)
    shader.setColorUniform("uTop", h.top.toArgb())
    shader.setColorUniform("uBot", h.bot.toArgb())
    shader.setColorUniform("uA", h.a.toArgb())
    shader.setColorUniform("uB", h.b.toArgb())
    shader.setColorUniform("uC", h.c.toArgb())
    drawRect(brush)
}

/** 셰이더를 쓸 수 없는 기기와, 작은 멈춘 그림에서는 겹친 빛으로 그린다 */
private fun DrawScope.plain(h: Hue, t: Float, seed: Int, glow: Float) {
    drawRect(Brush.verticalGradient(listOf(h.top, h.bot)))
    val w = size.width
    val hh = size.height
    fun blob(c: Color, x: Float, y: Float, r: Float, a: Float) {
        val o = Offset(x, y)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = a * 0.40f), Color.Transparent), o, r), r, o)
    }
    blob(h.a, w * (0.26f + 0.10f * sin(t * 0.13f + seed)), hh * (0.36f + 0.08f * cos(seed * 1.3f)), w * 0.62f, 0.85f * glow)
    blob(h.b, w * (0.82f + 0.08f * cos(seed * 2.1f)), hh * 0.76f, w * 0.56f, 0.70f * glow)
    blob(h.c, w * 0.60f, hh * 0.08f, w * 0.40f, 0.55f * glow)
}

/** 바탕 위를 떠다니는 것: 빛 알갱이, 밤의 별, 비, 눈 */
private fun DrawScope.motes(scene: SceneKind, rain: Boolean, t: Float, travel: Float, dark: Float, show: Float) {
    if (show <= 0.02f) return
    val w = size.width
    val h = size.height
    val u = h / 1000f
    val wu = w / u
    fun at(k: Int, fall: Float, drift: Float): Offset {
        val span = wu + 300f
        val px = wrap(rnd(k, 82) * span - t * (drift * (0.6f + rnd(k, 83))) + sin(t * 0.9f + k) * 20f - travel * 380f, span) - 150f
        val py = wrap(rnd(k, 84) * 1000f + t * fall * (0.7f + rnd(k, 81) * 0.8f), 1000f)
        return Offset(px * u, py * u)
    }
    if (rain || scene.fall == Fall.RAIN) {
        for (k in 0 until 110) {
            val p = at(k, 860f, 90f)
            drawLine(Color.White.copy(alpha = (0.07f + rnd(k, 94) * 0.13f) * show), p, Offset(p.x - 8f * u, p.y + 40f * u), 1.3f * u)
        }
        return
    }
    if (scene.fall == Fall.SNOW) {
        for (k in 0 until 70) {
            val p = at(k, 40f, 10f)
            drawCircle(Color.White.copy(alpha = (0.30f + rnd(k, 64) * 0.40f) * show), (1.4f + rnd(k, 65) * 2.6f) * u, p)
        }
        return
    }
    // 빛 알갱이: 아주 천천히 떠오르며 깜빡인다. 밤에는 별처럼 또렷해진다.
    for (k in 0 until 26) {
        val p = at(k, -9f, 5f)
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.5f + rnd(k, 3) * 1.1f) + k * 1.7f))
        val r = (1.2f + rnd(k, 4) * 2.2f) * u
        val a = (0.10f + 0.22f * dark) * tw * show
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), p, r * 4f), r * 4f, p)
        drawCircle(Color.White.copy(alpha = (a * 2.2f).coerceAtMost(0.85f)), r * 0.55f, p)
    }
}

/** 작은 동그라미에 담는 그날의 빛깔 (일기장의 날짜 줄, 이달의 빛깔, 빛깔 고르기) */
internal fun DrawScope.hueDisc(scene: SceneKind, part: DayPart, art: Art, rain: Boolean = false) {
    val h = hueAt(scene, part, 0f, rain, art)
    val r = size.minDimension / 2f
    drawCircle(Brush.linearGradient(listOf(h.top, h.bot), Offset(0f, 0f), Offset(0f, size.height)), r, center)
    val o = Offset(center.x - r * 0.25f, center.y - r * 0.20f)
    drawCircle(Brush.radialGradient(listOf(h.a.copy(alpha = 0.90f), h.a.copy(alpha = 0.30f), Color.Transparent), o, r * 1.2f), r, center)
    val o2 = Offset(center.x + r * 0.45f, center.y + r * 0.50f)
    drawCircle(Brush.radialGradient(listOf(h.b.copy(alpha = 0.70f), Color.Transparent), o2, r * 0.9f), r, center)
}
