package com.gildongmu.app

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * 살아 있는 바탕 — 장면마다 셰이더 한 편.
 *
 * 길동무의 화면 뒤에는 그림 파일이 없다. 강 건너 도시, 새벽 능선, 윤슬, 바람 부는 언덕, 억새밭, 벚꽃, 은행잎, 눈, 비 오는 창, 빛결.
 * 모두 그 자리에서 그려지고, 바람이 불고, 시각에 따라 빛이 바뀌고, 달리는 동안에는 차창 밖처럼 옆으로 흘러간다.
 *  - 원본: assets/scenes/_lib.agsl (공용) + assets/scenes/<장면>.agsl
 *  - 만드는 법과 맥에서 바로 그려 보는 도구: 제작자료/도구/agslr
 */
internal object SceneSrc {
    private val cache = HashMap<String, RuntimeShader?>()
    private var lib: String? = null

    /** 디버그 빌드에서 장면을 다시 읽을 때마다 올라간다 (이 값을 읽는 화면이 다시 그려진다) */
    var version by mutableIntStateOf(0)
        private set

    private fun asset(ctx: Context, name: String): String? =
        runCatching { ctx.assets.open("scenes/$name").bufferedReader().use { it.readText() } }.getOrNull()

    /** 개발용: 앱 저장소의 scenes/ 에 같은 이름의 파일을 넣어 두면 그것을 먼저 쓴다 (앱을 다시 빌드하지 않고 장면을 고쳐 볼 수 있다) */
    private fun local(ctx: Context, name: String): String? {
        if ((ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return null
        return File(File(ctx.filesDir, "scenes"), name).takeIf { it.exists() }?.readText()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun shader(ctx: Context, key: String): RuntimeShader? {
        if (cache.containsKey(key)) return cache[key]      // 만들지 못한 장면도 기억해 둔다 (나올 때마다 다시 만들려 들지 않게)
        val made = build(ctx, key)
        cache[key] = made
        return made
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun build(ctx: Context, key: String): RuntimeShader? {
        val l = lib ?: (local(ctx, "_lib.agsl") ?: asset(ctx, "_lib.agsl"))?.also { lib = it } ?: return null
        val s = local(ctx, "$key.agsl") ?: asset(ctx, "$key.agsl") ?: return null
        return try {
            RuntimeShader(l + "\n" + s)
        } catch (e: Throwable) {
            Log.e("Gildongmu", "scene $key 를 만들지 못했어요: ${e.message}")
            null
        }
    }

    fun reload() { cache.clear(); lib = null; version++ }

    /** 개발용: 파일 하나가 이 기기에서 셰이더로 만들어지는지 확인한다 ("OK" 또는 오류 글) */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun check(ctx: Context, file: File): String {
        val l = asset(ctx, "_lib.agsl") ?: return "FAIL _lib.agsl 없음"
        if (!file.exists()) return "FAIL 파일 없음: ${file.path}"
        return try { RuntimeShader(l + "\n" + file.readText()); "OK" } catch (e: Throwable) { "FAIL " + (e.message ?: "").take(900) }
    }
}

/** 시각을 셰이더의 값으로: 0 새벽 · 1 낮 · 2 노을 · 3 밤 */
private fun DayPart.dayValue(): Float = ordinal.toFloat()

/**
 * 장면을 그린다.
 * speed: 흘러가는 빠르기 (정차 중 0.1, 주행 중 3), dim: 어둡게 하는 정도, night: 0→1 로 올리면 밤으로 저문다,
 * seed: 날마다 다른 배치, still: 멈춘 그림 (작은 동그라미 여러 개를 놓을 때), soft: 0→1 로 올리면 잔잔해진다,
 * level: 목소리의 크기 (말할 때 바탕이 함께 숨 쉰다)
 */
@Composable
internal fun LiveScene(
    modifier: Modifier, scene: SceneKind, part: DayPart, rain: Boolean, speed: Float, dim: Float, night: Float,
    seed: Int, still: Boolean, soft: Float, art: Art, level: Float = 0f, economy: Boolean = false,
) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val ver = SceneSrc.version
    val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember(scene, ver) { SceneSrc.shader(ctx.applicationContext, scene.key) } else null
    if (shader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // 셰이더를 쓸 수 없는 기기에서는 번지는 빛으로 대신한다
        LightField(modifier, scene, part, rain, speed, dim, night, seed, still, soft, art)
        return
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    // 시각이 바뀌면 하늘빛이 천천히 넘어간다 (늘 가까운 쪽으로)
    val day = remember { Animatable(part.dayValue()) }
    // 손으로 해를 끄는 동안에는 그 값을 그대로 따른다 (하늘빛이 손끝을 따라 바로 바뀐다)
    val scrub = if (still) null else Look.dayScrub
    if (scrub != null) LaunchedEffect(scrub) { day.snapTo(scrub) }
    LaunchedEffect(part, scrub == null) {
        if (scrub != null) return@LaunchedEffect
        if (still) { day.snapTo(part.dayValue()); return@LaunchedEffect }
        var d = part.dayValue() - ((day.value % 4f) + 4f) % 4f
        if (d > 2f) d -= 4f
        if (d < -2f) d += 4f
        day.animateTo(day.value + d, tween((3200 * kotlin.math.abs(d).coerceIn(0.6f, 1.6f)).toInt(), easing = androidx.compose.animation.core.LinearEasing))
    }
    val driving = speed != 0.1f                                   // 서 있을 때의 빠르기는 0.1 로 약속되어 있다
    val sp by animateFloatAsState(speed, tween(2200), label = "speed")
    // 바람: '흐르게' 달릴 때만 세게 분다
    val wind by animateFloatAsState(if (speed >= 2f) 0.92f else if (driving) 0.30f else 0.42f, tween(2600), label = "wind")
    // 장면의 시간이 흐르는 빠르기: 서 있을 때와 '흐르게'는 그대로, '잔잔하게' 달릴 때는 느리게, '흐르지 않게'는 멈춘다
    val rate by animateFloatAsState(if (!driving || speed >= 2f) 1f else if (speed >= 0.5f) 0.30f else 0f, tween(2400), label = "rate")
    // (빗빛 장면은 비 오는 창이 그 장면의 모습이라, 비가 오지 않아도 창에 빗방울이 맺혀 있다)
    val wet by animateFloatAsState(if (rain || scene == SceneKind.RAIN) 1f else 0f, tween(1800), label = "rain")
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), tween(260), label = "level")
    val shared = LocalScenePan.current
    val own = remember { ScenePan() }
    val pan = if (still || shared == null) own else shared
    val time = remember(scene) { SceneTime(20.0 + seed * 3.0) }
    // 흐려서 까는 화면에서는 절반 크기로만 그려도 티가 나지 않는다. 그 밖에는 기기의 형편에 맞춘 크기로.
    val scale = if (still) 1f else minOf(SceneQuality.scale, if (soft > 0.25f || economy) 0.5f else 1f)
    // 다시 그리는 박자: 보통은 화면의 박자 그대로(초당 60장), 흐려서 깔 때·달릴 때·기기가 버거워할 때는 초당 30장.
    // 다시 그리지 않는 프레임에는 앞서 그려 둔 그림을 그대로 쓴다 (셰이더가 돌지 않는다).
    val capped = soft > 0.25f || driving || economy || SceneQuality.scale < 0.99f
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(still, capped) {
        if (still) return@LaunchedEffect
        var shown = 0L
        while (true) withFrameNanos { n ->
            if (shared != null && !economy) SceneQuality.frame(n)      // (아껴 그리는 화면의 박자로 장면의 크기를 정하지는 않는다)
            if (!capped || n - shown >= 30_000_000L) { shown = n; frame.longValue = n }
        }
    }
    ScaledLayer(scale, modifier.clipToBounds(), cache = !still) {
      Canvas(Modifier.fillMaxSize()) {
        val n = frame.longValue
        val t: Float
        if (still) t = 12f + seed * 3f
        else {
            val dt = if (time.lastN > 0L) ((n - time.lastN) / 1e9f).coerceIn(0f, 0.1f) else 0f
            time.lastN = n
            time.t += dt * rate
            t = time.t.toFloat()
            if (pan.lastN != n) {                                  // 장면이 겹쳐 넘어가는 동안에도 한 프레임에 한 번만 흘려보낸다
                val dp = if (pan.lastN > 0L) ((n - pan.lastN) / 1e9f).coerceIn(0f, 0.1f) else 0f
                pan.lastN = n
                if (sp >= 0.5f) pan.travel += dp * sp * 0.085f
            }
        }
        val d0 = ((day.value % 4f) + 4f) % 4f
        val dv = if (night > 0f && d0 <= 3f) d0 + (3f - d0) * night.coerceIn(0f, 1f) else d0
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", t)
        shader.setFloatUniform("uFlow", pan.travel)
        shader.setFloatUniform("uDay", dv)
        shader.setFloatUniform("uWind", if (still) 0.4f else wind)
        shader.setFloatUniform("uRain", wet)
        shader.setFloatUniform("uLevel", lv)
        shader.setFloatUniform("uSoft", soft.coerceIn(0f, 1f))
        shader.setFloatUniform("uSeed", seed * 1.37f)
        shader.setFloatUniform("uGrade", when (art) { Art.AURA -> 0f; Art.GRAIN -> 1f; Art.INK -> 2f })
        drawRect(brush)
        // 비 오는 날: (비 오는 창이 아닌 장면에서도) 가는 빗줄기가 바람에 비스듬히 내린다
        if (!still && wet > 0.02f && scene != SceneKind.RAIN) {
            val u = size.height / 1000f
            val span = size.width / u + 300f
            val lean = 10f + 34f * wind
            for (k in 0 until 96) {
                val x = wrap(rnd(k, 82) * span - t * (60f + 120f * rnd(k, 83)) * wind - pan.travel * 520f, span) - 150f
                val y = wrap(rnd(k, 84) * 1000f + t * 900f * (0.7f + rnd(k, 81) * 0.8f), 1000f)
                val a = (0.05f + rnd(k, 94) * 0.11f) * wet * (1f - 0.5f * soft)
                drawLine(Color.White.copy(alpha = a), Offset(x * u, y * u), Offset((x - lean * 0.4f) * u, (y + 42f) * u), 1.2f * u)
            }
            drawRect(Color(0xFF1A2238).copy(alpha = 0.16f * wet))
        }
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.92f)))
      }
    }
}

/** 장면 하나가 흘러온 시간 (초). 장면이 바뀌면 처음부터 다시 센다 (아주 오래 켜 두어도 움직임이 거칠어지지 않게). */
internal class SceneTime(var t: Double) { var lastN = 0L }

/**
 * 앱을 켜는 동안, 장면들을 화면 구석에 아주 작게 하나씩 그려 본다.
 * 장면의 셰이더는 처음 그려질 때 한 번 만들어지는데, 그 일을 여는 장면 뒤에서 미리 해 두면 나중에 바탕을 바꿀 때 멈칫하지 않는다.
 */
@Composable
internal fun PrewarmScenes() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val index = remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        for (k in SceneKind.entries.indices) { index.intValue = k; kotlinx.coroutines.delay(240) }
        index.intValue = -1
    }
    val kind = SceneKind.entries.getOrNull(index.intValue) ?: return
    androidx.compose.runtime.key(kind) { Scenery(Modifier.size(6.dp), part = Look.partNow, seed = 5, still = true, scene = kind) }
}

/**
 * 장면을 그리는 크기를 기기의 형편에 맞춘다.
 * 화면이 한동안 제 박자(초당 60장)를 따라가지 못하면 장면을 한 단계 작게 그려서 늘려 붙이고, 넉넉해지면 다시 키운다.
 * 장면은 부드러운 그림이라 작게 그려도 크게 티가 나지 않고, 글과 유리는 늘 제 크기로 그려진다.
 */
internal object SceneQuality {
    var scale by mutableFloatStateOf(1f)
        private set
    private var lastN = 0L
    private var ema = 1f / 60f
    private var slow = 0
    private var fast = 0
    private var holdUntil = 0L
    private var hold = 30_000_000_000L          // 다시 키워 보기까지 기다리는 시간 (키웠다가 또 버거워지면 두 배로 늘린다)
    private var raisedAt = 0L

    /** 화면이 한 장 넘어갈 때마다 부른다 (n: 나노초) */
    fun frame(n: Long) {
        val dt = if (lastN > 0L) (n - lastN) / 1e9f else 0f
        if (n == lastN) return
        lastN = n
        if (dt <= 0f || dt > 0.25f) return
        ema += (dt - ema) * 0.06f
        when {
            ema > 0.0235f -> { slow++; fast = 0 }
            ema < 0.0185f -> { fast++; slow = 0 }
            else -> { slow = 0; fast = 0 }
        }
        if (slow > 90 && scale > 0.4f) {
            // 방금 키운 직후에 다시 버거워졌다면, 다음에는 더 오래 기다렸다가 키운다
            if (raisedAt > 0L && n - raisedAt < 20_000_000_000L) hold = (hold * 2).coerceAtMost(600_000_000_000L)
            scale = when { scale > 0.9f -> 0.8f; scale > 0.7f -> 0.66f; scale > 0.55f -> 0.5f; else -> 0.4f }
            slow = 0; ema = 1f / 60f; holdUntil = n + hold
            Log.d("Gildongmu", "장면을 ${(scale * 100).toInt()}% 크기로 그립니다 (화면이 박자를 따라가지 못해서)")
        } else if (fast > 900 && scale < 1f && n > holdUntil) {
            scale = when { scale < 0.45f -> 0.5f; scale < 0.6f -> 0.66f; scale < 0.7f -> 0.8f; else -> 1f }
            fast = 0; raisedAt = n; holdUntil = n + 10_000_000_000L
        }
    }
}

/**
 * content 를 scale 배 크기로 그려서, 제 자리 크기로 늘려 붙인다.
 * cache 가 켜져 있으면 늘 따로 그려 두었다가 붙인다: content 가 다시 그려지지 않는 프레임에는 그려 둔 그림을 그대로 쓴다.
 */
@Composable
private fun ScaledLayer(scale: Float, modifier: Modifier, cache: Boolean = false, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cw = (w * scale).toInt().coerceAtLeast(1)
        val ch = (h * scale).toInt().coerceAtLeast(1)
        val p = measurables.first().measure(Constraints.fixed(cw, ch))
        layout(w, h) {
            if (scale >= 0.999f && !cache) p.place(0, 0)
            else p.placeWithLayer(0, 0) {
                scaleX = w / cw.toFloat(); scaleY = h / ch.toFloat()
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}
