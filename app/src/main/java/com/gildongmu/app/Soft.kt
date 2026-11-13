package com.gildongmu.app

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * 부드러운 것들 — 유리, 안개, 구슬.
 *
 * 길동무의 화면에는 네모난 판이 없다. 풍경이 화면을 가득 채우고, 그 위에
 *  - 유리(frost): 뒤의 풍경이 흐릿하게 비치는 알약·동그라미 (버튼, 아래 길잡이)
 *  - 안개(mist): 가장자리 없이 번지는 흐림 (글이 놓이는 자리)
 *  - 구슬(Orb): 안에서 빛이 천천히 흐르는 둥근 빛 (말하기)
 * 만 얹는다.
 */

// ---------------------------------------------------------------- 유리

/** 유리: 빛 위에 맑게 선 면. 알약·동그라미·크게 굴린 모양으로만 쓴다. (지도 위에서는 어둑한 유리로 선다) */
fun Modifier.frost(shape: Shape = R16, tint: Color = C.Panel, dim: Float = 0.30f): Modifier = composed {
    val bd = LocalBackdrop.current
    this.clip(shape).drawBehind {
        if (bd != null) { drawRect(Color.Black.copy(alpha = dim * 0.42f)); drawRect(Color.White.copy(alpha = 0.05f)) }
        else drawRect(Color(0xFF151922).copy(alpha = 0.90f))
        drawRect(tint)
    }.border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.05f))), shape)
}

// ---------------------------------------------------------------- 안개

/** 안개가 번지는 방향: 그쪽이 짙고, 반대쪽으로 스르르 사라진다. CLOUD 는 가운데가 짙은 구름. */
enum class Mist { LEFT, RIGHT, UP, DOWN, CLOUD, FULL }

/**
 * 안개: 가장자리가 없는 어스름. 바탕을 살짝 가라앉혀서, 그 위의 글이 읽히게 한다.
 * reach 는 사라져 가는 구간의 길이 (전체 대비).
 */
fun Modifier.mist(kind: Mist, dim: Float = 0.36f, reach: Float = 0.32f): Modifier = composed {
    val bd = LocalBackdrop.current
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            if (bd != null) drawRect(Color.Black.copy(alpha = dim * 0.50f)) else drawRect(Color(0xFF0B0E14).copy(alpha = 0.74f))
            when (kind) {
                Mist.RIGHT -> drawRect(Brush.horizontalGradient(0f to Color.Transparent, reach to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
                Mist.LEFT -> drawRect(Brush.horizontalGradient(0f to Color.Black, 1f - reach to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                Mist.DOWN -> drawRect(Brush.verticalGradient(0f to Color.Transparent, reach to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
                Mist.UP -> drawRect(Brush.verticalGradient(0f to Color.Black, 1f - reach to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                Mist.CLOUD -> {
                    val rx = size.width / 2f
                    val ry = size.height / 2f
                    withTransform({ scale(1f, ry / rx, center) }) {
                        drawRect(
                            Brush.radialGradient(0f to Color.Black, 0.58f to Color.Black, 1f to Color.Transparent, center = center, radius = rx),
                            topLeft = Offset(0f, center.y - rx), size = Size(size.width, rx * 2f), blendMode = BlendMode.DstIn,
                        )
                    }
                }
                Mist.FULL -> {}
            }
            drawContent()
        }
}

// ---------------------------------------------------------------- 구슬

private const val ORB_SRC = """
uniform float2 uSize;
uniform float uTime;
uniform float uLevel;
uniform float uEnergy;
uniform float uMorph;
layout(color) uniform half4 uA;
layout(color) uniform half4 uB;
layout(color) uniform half4 uC;

float h21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float vn(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), u.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), u.x), u.y);
}
// 구슬의 테두리: 동그라미에서 네 꼭짓점의 별로 (uMorph 0 → 1)
float rim(float ang, float t) {
    float k = uMorph;
    float star = 0.5 + 0.5 * cos(4.0 * (ang - t * 0.16));
    float wob = 0.022 * uLevel * sin(ang * 3.0 + t * 5.0) + 0.014 * uLevel * sin(ang * 7.0 - t * 7.3);
    return 0.575 * (1.0 - 0.165 * k * (1.0 - star * star)) + 0.006 * sin(t * 1.3) + 0.050 * uLevel + wob;
}
half4 main(float2 xy) {
    float m = min(uSize.x, uSize.y) * 0.5;
    float2 uv = (xy - uSize * 0.5) / m;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    float t = uTime;
    float R = rim(ang, t);
    float d = r - R;
    float px = 1.0 / m;
    // 테두리를 따라 도는 빛: 두 갈래의 빛줄기가 서로 다른 빠르기로 돈다
    float sp = 0.42 + 0.9 * uEnergy;
    float a1 = 0.5 + 0.5 * cos(ang - t * sp);
    float a2 = 0.5 + 0.5 * cos(ang * 2.0 + t * sp * 0.63 + 1.7);
    float arc = pow(a1, 2.2) * 0.85 + pow(a2, 3.0) * 0.55;
    float3 hue = mix(float3(uA.rgb), float3(uB.rgb), 0.5 + 0.5 * sin(ang + t * 0.31 + 0.6));
    hue = mix(hue, float3(uC.rgb), pow(a1, 6.0) * 0.55);
    float base = 0.30 + 0.70 * clamp(arc, 0.0, 1.0);
    // 가는 선과 그 둘레의 번짐
    float line = exp(-d * d / (px * px * 2.4));
    float glowIn = exp(-max(-d, 0.0) * (7.5 - 2.5 * uLevel)) * step(d, 0.0);
    float glowOut = exp(-max(d, 0.0) * (5.2 - 2.4 * uLevel));
    float edge = 1.0 - smoothstep(0.76, 0.99, r);
    glowOut *= edge * step(0.0, d);
    float3 col = float3(0.0);
    float alpha = 0.0;
    // 안: 어둑한 유리에 빛이 옅게 고인다
    float inside = 1.0 - smoothstep(-px * 1.2, px * 1.2, d);
    float3 hueC = mix((float3(uA.rgb) + float3(uB.rgb)) * 0.5, hue, smoothstep(0.08, 0.60, r / max(R, 0.01)));
    float3 pool = hueC * (0.20 + 0.16 * uEnergy + 0.30 * uLevel) * (0.35 + 0.65 * glowIn);
    float swirl = vn(uv * 2.4 + float2(t * 0.20, -t * 0.14)) * vn(uv * 4.1 - float2(t * 0.11, t * 0.17));
    pool += hueC * swirl * 0.34 * (0.5 + 0.5 * uEnergy);
    // 위쪽에 어리는 빛
    float sheen = clamp(1.0 - length((uv - float2(-0.10, -0.26)) * float2(1.25, 2.3)) / 0.62, 0.0, 1.0);
    pool += float3(1.0) * sheen * sheen * 0.10;
    col += pool * inside;
    alpha += inside * (0.42 + 0.20 * uEnergy);
    // 테두리의 빛
    col += hue * line * (0.55 + 0.75 * base) + float3(1.0) * line * pow(a1, 5.0) * 0.8;
    alpha = max(alpha, line * (0.5 + 0.5 * base));
    col += hue * glowOut * base * (0.62 + 0.36 * uEnergy + 0.5 * uLevel);
    alpha = max(alpha, glowOut * base * 0.72);
    // 말할 때 밖으로 퍼지는 고리
    for (int i = 0; i < 3; i++) {
        float ph = fract(t * 0.55 + float(i) / 3.0);
        float rr = d - ph * 0.34;
        float ring = exp(-rr * rr / (px * px * 3.0)) * (1.0 - ph) * (1.0 - ph) * uEnergy * (0.25 + 0.75 * uLevel) * edge;
        col += hue * ring * 0.8;
        alpha = max(alpha, ring * 0.6);
    }
    // 테두리 위의 작은 흰 점 넷 (꼭짓점)
    for (int i = 0; i < 4; i++) {
        float na = t * 0.16 + float(i) * 1.5708;
        float nr = rim(na, t);
        float nd = length(uv - float2(cos(na), sin(na)) * nr);
        float node = 1.0 - smoothstep(0.030 - px, 0.030 + px, nd);
        float halo = exp(-nd * nd / 0.0030) * 0.35;
        col = mix(col, float3(1.0), node);
        col += hue * halo * (1.0 - node);
        alpha = max(alpha, max(node, halo * 0.6));
    }
    alpha = clamp(alpha, 0.0, 1.0);
    // 겹쳐 그릴 수 있도록, 빛은 투명도보다 밝지 않게 맞춘다
    float3 outc = min(clamp(col, float3(0.0), float3(1.0)), float3(1.0));
    alpha = max(alpha, max(outc.r, max(outc.g, outc.b)));
    half4 o = half4(half3(outc), half(alpha));
    return half4(o.rgb, o.a);
}
"""

/**
 * 구슬: 테두리를 따라 빛이 도는 둥근 유리. 말하기 버튼이자, 길동무가 듣고 있다는 표시.
 * 쉬는 동안에는 가는 빛의 고리와 그 위의 작은 점 넷이 천천히 돈다. 듣는 동안에는 목소리를 따라 테두리가 일렁이고,
 * 고리가 밖으로 퍼져 나가며, 동그라미가 네 꼭짓점의 별 쪽으로 부푼다.
 * level 은 목소리의 크기(0..1), energy 는 깨어 있는 정도(0 쉬는 중, 1 듣는 중), morph 는 별에 가까운 정도.
 * 구슬은 주어진 크기의 58% 쯤을 차지하고, 나머지는 둘레로 번지는 빛의 자리다.
 */
@Composable
fun Orb(
    size: Dp, modifier: Modifier = Modifier, level: Float = 0f, energy: Float = 0f,
    a: Color = C.Accent, b: Color = lerp(C.Accent, Color(0xFF8C7BFF), 0.72f), c: Color = Color(0xFFFFF8EA), phase: Float = 0f,
    morph: Float = -1f,
) {
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.6f, stiffness = 220f), label = "orbLevel")
    val en by animateFloatAsState(energy.coerceIn(0f, 1f), tween(700), label = "orbEnergy")
    val mo by animateFloatAsState(if (morph >= 0f) morph else (energy * 0.35f + level * 0.5f).coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 90f), label = "orbMorph")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OrbShader(size, modifier, lv, en, a, b, c, phase, mo)
    else OrbPlain(size, modifier, lv, en, a, b, c)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun OrbShader(size: Dp, modifier: Modifier, lv: Float, en: Float, a: Color, b: Color, c: Color, phase: Float, mo: Float) {
    val clock = LocalClock.current
    val shader = remember { RuntimeShader(ORB_SRC) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier.size(size)) {
        shader.setFloatUniform("uSize", this.size.width, this.size.height)
        shader.setFloatUniform("uTime", clock.value + phase)
        shader.setFloatUniform("uLevel", lv)
        shader.setFloatUniform("uEnergy", en)
        shader.setFloatUniform("uMorph", mo)
        shader.setColorUniform("uA", a.toArgb())
        shader.setColorUniform("uB", b.toArgb())
        shader.setColorUniform("uC", c.toArgb())
        drawRect(brush)
    }
}

/** 셰이더를 쓸 수 없는 기기에서는 겹친 빛으로 대신한다 */
@Composable
private fun OrbPlain(size: Dp, modifier: Modifier, lv: Float, en: Float, a: Color, b: Color, c: Color) {
    val clock = LocalClock.current
    Canvas(modifier.size(size)) {
        val m = this.size.minDimension / 2f
        val r = m * (0.60f + 0.012f * sin(clock.value * 1.2f) + 0.07f * lv)
        drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.40f + 0.3f * en), a.copy(alpha = 0.10f), Color.Transparent), center, m), m)
        drawCircle(Brush.radialGradient(listOf(c, a, b), Offset(center.x - r * 0.35f, center.y - r * 0.45f), r * 1.7f), r)
    }
}

// ---------------------------------------------------------------- 숫자, 사람, 감정

/** 가늘고 큰 숫자 (시각, 날짜, 개수) */
@Composable
fun Num(text: String, size: Int, color: Color = C.Hi, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Light, shadow: Boolean = false) {
    Text(
        text, modifier = modifier,
        style = TextStyle(
            fontFamily = Sans, fontWeight = weight, fontSize = size.sp, color = color, letterSpacing = (-0.02).em,
            fontFeatureSettings = "tnum", lineHeight = (size * 1.05f).sp,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.46f), Offset(0f, 3f), 30f) else null,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

/** 사람: 그 사람의 빛으로 채운 동그라미에 이름 첫 글자 */
@Composable
fun Avatar(tone: Tone, initial: String, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, unknown: Boolean = false) {
    Box(
        modifier.size(size).graphicsLayer { alpha = if (dim) 0.45f else 1f }.clip(CircleShape)
            .background(Color(0xFF12101E).copy(alpha = 0.66f))
            .background(Brush.linearGradient(listOf(tone.a.copy(alpha = 0.58f), tone.b.copy(alpha = 0.16f))))
            .border((size.value * 0.026f).coerceIn(1.5f, 3f).dp, Brush.linearGradient(listOf(lerp(tone.a, Color.White, 0.55f), tone.a.copy(alpha = 0.50f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (unknown) "?" else initial.ifBlank { "길" },
            style = TextStyle(fontFamily = Myeongjo, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.40f).sp, color = Color.White, platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}

/** 감정: 그 감정의 빛깔로 은은하게 숨 쉬는 구슬 */
@Composable
fun MoodOrb(mood: Mood, size: Dp, modifier: Modifier = Modifier, phase: Float = 0f) {
    val clock = LocalClock.current
    val tone = mood.tone().a
    Canvas(modifier.size(size)) {
        val k = 0.85f + 0.15f * sin(clock.value * 1.3f + phase)
        glowIcon(mood.ic(), center, this.size.minDimension / 2f, tone, k)
    }
}
