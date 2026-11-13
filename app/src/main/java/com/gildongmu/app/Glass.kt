package com.gildongmu.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 유리 — 뒤의 장면이 진짜로 흐려지고, 가장자리에서 살짝 굴절되는 면.
 *
 * 화면 위의 알약·동그라미·시트는 자기 자리와 모양만 알려 주고([glass]),
 * 그 뒤에 깔린 겹(장면이나 지도)이 그 자리를 유리로 바꿔 그린다([glassBackdrop]).
 * 그래서 유리 뒤로 구름이 지나가고 불빛이 흘러가는 것이 그대로 비친다.
 */
class Glass {
    internal class Pane(
        var l: Float = 0f, var t: Float = 0f, var r: Float = 0f, var b: Float = 0f, var radius: Float = 0f,
        var amount: Float = 1f, var dim: Float = 0.16f, var refract: Float = 10f, var fade: () -> Float = { 1f },
    )

    internal val panes = LinkedHashMap<Any, Pane>()

    /** 유리의 자리나 세기가 바뀔 때마다 올라간다 */
    var tick by mutableIntStateOf(0)
        private set

    /** 0→1 화면 전체를 초점 밖으로 흐린다 (글이 많은 화면에서, 뒤의 장면이 부드러운 빛으로만 남도록) */
    var soften by mutableFloatStateOf(0f)

    internal fun put(id: Any, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        val p = panes.getOrPut(id) { Pane() }
        if (p.l == l && p.t == t && p.r == r && p.b == b && p.radius == radius) return
        p.l = l; p.t = t; p.r = r; p.b = b; p.radius = radius
        tick++
    }

    internal fun tune(id: Any, amount: Float, dim: Float, refract: Float, fade: () -> Float) {
        val p = panes.getOrPut(id) { Pane() }
        if (p.amount == amount && p.dim == dim && p.refract == refract && p.fade === fade) return
        p.amount = amount; p.dim = dim; p.refract = refract; p.fade = fade
        tick++
    }

    internal fun remove(id: Any) { if (panes.remove(id) != null) tick++ }

    companion object { const val MAX = 16 }
}

private const val GLASS_SRC = """
uniform shader content;
uniform float2 uOrigin;
uniform float uCount;
uniform float uDp;
uniform float4 uRect[16];
uniform float4 uOpt[16];

// 화소마다 고르게 흩어지는 값 (모래알처럼 뭉치지 않는다)
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }
float sdRound(float2 p, float4 r, float rad) {
    float2 c = (r.xy + r.zw) * 0.5;
    float2 h = (r.zw - r.xy) * 0.5;
    float2 q = abs(p - c) - h + float2(rad);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rad;
}
half4 main(float2 xy) {
    float2 p = xy + uOrigin;
    half4 base = content.eval(xy);
    float best = 100000.0;
    float4 R = float4(0.0);
    float4 O = float4(0.0);
    float shade = 0.0;
    for (int i = 0; i < 16; i++) {
        if (float(i) < uCount) {
            float d = sdRound(p, uRect[i], uOpt[i].x);
            if (d < best) { best = d; R = uRect[i]; O = uOpt[i]; }
            float ds = sdRound(p - float2(0.0, 9.0 * uDp), uRect[i], uOpt[i].x);
            shade = max(shade, exp(-max(ds, 0.0) / (18.0 * uDp)) * uOpt[i].y);
        }
    }
    // 유리 밖: 아래로 떨어지는 옅은 그림자
    half4 outside = half4(base.rgb * half(1.0 - 0.20 * shade), base.a);
    if (best >= 0.0) return outside;

    float rad = O.x;
    float amt = O.y;
    float depth = -best / uDp;
    float2 g = float2(
        sdRound(p + float2(1.0, 0.0), R, rad) - sdRound(p - float2(1.0, 0.0), R, rad),
        sdRound(p + float2(0.0, 1.0), R, rad) - sdRound(p - float2(0.0, 1.0), R, rad));
    float2 n = g / max(length(g), 0.0001);
    // 가장자리에서는 유리가 둥글게 말려 있어서, 뒤의 것이 안쪽으로 당겨져 보인다
    float bevel = 1.0 - smoothstep(0.0, 26.0, depth);
    float2 q = xy - n * bevel * bevel * O.w * 1.5 * uDp;
    float spin = ign(p) * 6.2831;
    half4 acc = half4(0.0);
    float blur = 30.0 * amt * uDp;
    for (int k = 0; k < 18; k++) {
        float fk = float(k) + 0.5;
        float rr = sqrt(fk / 18.0) * blur;
        float a = fk * 2.39996 + spin;
        acc += content.eval(q + float2(cos(a), sin(a)) * rr);
    }
    acc /= 18.0;
    half3 c = acc.rgb;
    half l = dot(c, half3(0.299, 0.587, 0.114));
    c = mix(half3(l), c, half(1.22));                         // 유리 너머의 빛깔은 조금 더 맑게
    c *= half(1.0 - O.z);
    c += half3(1.0) * half(0.050) * acc.a;
    // 위에서 떨어지는 빛: 위쪽 테두리가 밝게, 아래쪽 테두리는 은은하게 받는다
    float edge = smoothstep(2.4, 0.2, depth);
    float lit = dot(n, normalize(float2(-0.30, -1.0)));
    float spec = edge * (0.10 + 0.62 * clamp(lit, 0.0, 1.0) + 0.24 * clamp(-lit, 0.0, 1.0));
    c += half3(1.0) * half(spec * 0.62) * acc.a;
    c += half3(1.0) * half(0.055 * bevel * bevel) * acc.a;
    // 위쪽 절반에 어리는 옅은 빛
    float sheen = clamp(1.0 - (p.y - R.y) / max(R.w - R.y, 1.0), 0.0, 1.0);
    c += half3(1.0) * half(0.030 * sheen * sheen) * acc.a;
    half4 glass = half4(c, acc.a);
    float cover = smoothstep(0.0, 1.3, depth) * amt;
    return mix(outside, glass, half(cover));
}
"""

/** 이 겹 위에 놓인 유리들을 그려 준다 (장면이나 지도처럼, 유리 뒤에 깔리는 겹에 붙인다) */
fun Modifier.glassBackdrop(glass: Glass?): Modifier = composed {
    if (glass == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this
    val shader = remember { RuntimeShader(GLASS_SRC) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rects = remember { FloatArray(Glass.MAX * 4) }
    val opts = remember { FloatArray(Glass.MAX * 4) }
    val dp = LocalDensity.current.density
    this.onGloballyPositioned { origin = it.positionInRoot() }.graphicsLayer {
        @Suppress("UNUSED_VARIABLE") val tick = glass.tick
        var n = 0
        for (pn in glass.panes.values) {
            if (n >= Glass.MAX) break
            val amount = pn.amount * pn.fade().coerceIn(0f, 1f)      // 화면이 바뀌는 동안에는 내용과 함께 나타나고 사라진다
            if (amount <= 0.004f || pn.r - pn.l < 2f) continue
            rects[n * 4] = pn.l; rects[n * 4 + 1] = pn.t; rects[n * 4 + 2] = pn.r; rects[n * 4 + 3] = pn.b
            opts[n * 4] = pn.radius; opts[n * 4 + 1] = amount; opts[n * 4 + 2] = pn.dim; opts[n * 4 + 3] = pn.refract
            n++
        }
        val soften = glass.soften
        if (n == 0 && soften <= 0.01f) { renderEffect = null; return@graphicsLayer }
        // 화면 전체를 초점 밖으로: 먼저 고르게 흐린 다음(모래알이 끼지 않는다), 그 위에 유리를 세운다
        val r = soften * 38f * dp
        val blur = if (r > 0.6f) RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP) else null
        if (n == 0) { renderEffect = blur?.asComposeRenderEffect(); return@graphicsLayer }
        shader.setFloatUniform("uDp", dp)
        shader.setFloatUniform("uOrigin", origin.x, origin.y)
        shader.setFloatUniform("uCount", n.toFloat())
        shader.setFloatUniform("uRect", rects)
        shader.setFloatUniform("uOpt", opts)
        val panes = RenderEffect.createRuntimeShaderEffect(shader, "content")
        renderEffect = (if (blur != null) RenderEffect.createChainEffect(panes, blur) else panes).asComposeRenderEffect()
    }
}

/** 화면이 바뀌는 동안 그 화면의 유리가 내용과 함께 나타나고 사라지게 하는 값 (0..1). 화면을 바꾸는 쪽에서 걸어 둔다. */
val LocalGlassFade = androidx.compose.runtime.compositionLocalOf<() -> Float> { { 1f } }

/**
 * 유리로 된 면. radius 가 높이의 절반보다 크면 알약(또는 동그라미)이 된다.
 * amount: 0→1 유리가 나타나는 정도 (나타나고 사라질 때 함께 움직인다), dim: 뒤를 어둡게 가라앉히는 정도, refract: 가장자리의 굴절(px)
 */
@Composable
fun Modifier.glass(radius: Dp = 1000.dp, amount: Float = 1f, dim: Float = 0.16f, refract: Float = 11f): Modifier {
    val glass = LocalBackdrop.current?.glass
    val id = remember { Any() }
    val rp = with(LocalDensity.current) { radius.toPx() }
    if (glass != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        DisposableEffect(glass) { onDispose { glass.remove(id) } }
        val fade = LocalGlassFade.current
        SideEffect { glass.tune(id, amount.coerceIn(0f, 1f), dim, refract, fade) }
        return this.onGloballyPositioned { c ->
            val o = c.positionInRoot()
            val w = c.size.width.toFloat(); val h = c.size.height.toFloat()
            glass.put(id, o.x, o.y, o.x + w, o.y + h, minOf(rp, w / 2f, h / 2f))
        }
    }
    // 유리를 그려 줄 겹이 없는 자리(또는 예전 기기): 어둑한 반투명 면으로 선다
    return this.drawBehind {
        val r = minOf(rp, size.width / 2f, size.height / 2f)
        drawRoundRect(Color(0xFF12141C).copy(alpha = 0.78f * amount), cornerRadius = CornerRadius(r))
        drawRoundRect(Color.White.copy(alpha = 0.07f * amount), cornerRadius = CornerRadius(r))
    }
}
