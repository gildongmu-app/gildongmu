package com.gildongmu.app

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 환영 장면 — 차에 탄 순간(운전자를 골랐을 때), 인사를 글로 건넨다. (5초 남짓, 누르면 건너뛴다. 달리는 중에는 나오지 않는다.)
 *
 * 흐릿하게 깔린 오늘의 창밖 위로 큰 글씨 두 줄이 조용히 나타난다 — "어서 오세요, / 도현님."
 * 이름 아래로 가는 줄이 그어지고 작은 한 줄이 따라온다. 잠시 머문 뒤 글이 옅어지며 오늘의 화면이 나타나고, 길동무의 물음이 선다.
 *
 * (전에는 지난날의 말 몇 줄이 빛이 되어 인사로 모였다. 한마디들은 앱을 처음 켤 때의 화면에서 이미 보여 주므로,
 *  여기서는 꾸밈 없이 인사만 바로 건넨다. 그때의 코드는 저장소 밖 제작자료에 따로 두었다.)
 *
 * cover: 뒤의 화면이 다 가려졌을 때 한 번, reveal: 걷히기 시작할 때 한 번, done: 다 걷힌 뒤.
 */
@Composable
fun WelcomeGreeting(m: AppModel, fadeIn: Boolean, cover: () -> Unit, reveal: () -> Unit, done: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val me = m.current
    val tone = me?.tone() ?: AvatarTones[0]
    val now = remember { m.clock() }
    val words = remember { Words.welcome(me?.name, now.get(Calendar.HOUR_OF_DAY)) }
    val serifBold = remember { ResourcesCompat.getFont(ctx, R.font.maruburi_semibold) ?: Typeface.SERIF }
    val serif = remember { ResourcesCompat.getFont(ctx, R.font.maruburi_regular) ?: Typeface.SERIF }

    var page by remember { mutableStateOf<Greeting?>(null) }
    var t by remember { mutableFloatStateOf(0f) }
    var jump by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var covered = false; var greeted = false; var revealed = false
        while (true) {
            val v = (withFrameNanos { it } - start) / 1e9f + jump
            t = v
            if (!covered && v >= 0.36f) { covered = true; cover() }
            if (!greeted && v >= SAY && v < EXIT0) { greeted = true; Chime.play(); m.greet() }
            if (!revealed && v >= REVEAL) { revealed = true; reveal() }
            if (v >= END) break
        }
        done()
    }
    // 달리기 시작하면 곧바로 비켜 준다
    val driving = m.driving
    LaunchedEffect(driving) { if (driving && t < EXIT0) jump += EXIT0 - t }

    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    val enter = if (fadeIn) Ease.soft.transform(seg(0f, 0.36f)) else 1f
    val leave = Ease.inOut.transform(seg(REVEAL - 0.05f, END))

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter * (1f - leave) }
            .clickable(remember { MutableInteractionSource() }, null) { if (t < EXIT0) jump += EXIT0 - t }
            .onSizeChanged { sz ->
                if (page == null && sz.width > 0) page = Greeting.build(sz.width.toFloat(), sz.height.toFloat(), density, words, serifBold, serif, Random(now.get(Calendar.DAY_OF_YEAR)))
            },
    ) {
        // 오늘의 창밖이 아주 흐릿하게, 어둠 속에 깔린다 (마지막에 걷히면 또렷한 창밖이 그 자리에 있다)
        Scenery(Modifier.fillMaxSize(), part = Look.part(now), scene = Look.scene, speed = 0.08f, soft = 1f, dim = 0.36f)
        Canvas(Modifier.fillMaxSize()) {
            val p = page ?: return@Canvas
            // 흐린 창밖의 빛깔은 비치되, 가운데의 글자가 가장 밝게 서도록 가장자리와 가운데를 조금씩 가라앉힌다
            drawRect(Brush.radialGradient(listOf(Color(0x8805060C), Color(0x6605060C), Color(0xB805060C)), Offset(size.width / 2, size.height * 0.45f), size.width * 0.78f))
            drawIntoCanvas { c -> p.draw(c.nativeCanvas, t, tone) }
        }
    }
}

// ---------------------------------------------------------------- 박자 (초)

private const val IN0 = 0.28f        // 윗줄이 나타나기 시작한다 (아랫줄은 조금 뒤따라)
private const val SAY = 0.80f        // 인사를 소리로도 건넨다 (띠링)
private const val EXIT0 = 4.00f      // 글이 옅어지기 시작 (인사를 소리로 다 건넬 즈음까지 머문다)
private const val REVEAL = 4.30f     // 오늘 화면이 나타나기 시작
private const val END = 5.20f

private fun ease(x: Float): Float { val v = x.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }
private fun easeOut(x: Float): Float { val v = x.coerceIn(0f, 1f); return 1f - (1f - v) * (1f - v) * (1f - v) }

/** 인사 글: 큰 글씨 두 줄과 작은 한 줄. 모든 값은 화면 픽셀 기준이고, 안드로이드 캔버스에 직접 그린다. */
private class Greeting(val w: Float, val h: Float, val dp: Float, val lines: List<Line>, val sub: Line, val dust: FloatArray) {
    class Line(val text: String, val x: Float, val y: Float, val paint: Paint)

    private val halo = lines.map { Paint(it.paint).apply { maskFilter = BlurMaskFilter(26f * dp, BlurMaskFilter.Blur.NORMAL) } }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; color = 0xFFFFF1DC.toInt() }

    fun draw(c: android.graphics.Canvas, t: Float, tone: Tone) {
        val warm = 0xFFFFF1DC.toInt()
        val acc = tone.a.toArgb()

        // 떠다니는 먼지빛 (깊이)
        val dn = dust.size / 3
        for (k in 0 until dn) {
            val x = dust[k * 3]; val y0 = dust[k * 3 + 1]; val ph = dust[k * 3 + 2]
            val y = (y0 - t * 14f * dp * (0.5f + ph)).mod(h)
            dustPaint.alpha = (40 + 50 * (0.5f + 0.5f * sin(t * 1.7f + ph * 9f))).toInt()
            dustPaint.strokeWidth = (1.2f + ph * 1.6f) * dp
            c.drawPoint(x + sin(t * 0.6f + ph * 7f) * 6f * dp, y, dustPaint)
        }

        // 떠날 때: 조금 떠오르며 옅어진다
        val out = ease((t - EXIT0) / 0.32f)
        if (out >= 0.99f) return
        val lift = -10f * dp * out

        // 인사 두 줄: 윗줄부터, 아래에서 살짝 떠오르며 또렷해진다 (글자 뒤로 옅은 빛이 번진다)
        for ((k, l) in lines.withIndex()) {
            val p = easeOut((t - IN0 - 0.18f * k) / 0.62f)
            val a = p * (1f - out)
            if (a <= 0.01f) continue
            val y = l.y + (1f - p) * 16f * dp + lift
            val hp = halo[k]
            hp.color = if (k == lines.lastIndex) acc else warm
            hp.alpha = (96 * a).toInt()
            c.drawText(l.text, l.x, y, hp)
            l.paint.alpha = (255 * a).toInt()
            c.drawText(l.text, l.x, y, l.paint)
        }
        // 이름 아래로 가는 줄이 가운데에서 양쪽으로 그어진다
        val u = easeOut((t - IN0 - 0.55f) / 0.8f)
        if (u > 0.01f) {
            val last = lines.last()
            val cx = w / 2f
            val y = last.y + 0.30f * last.paint.textSize + lift
            val half = (last.paint.measureText(last.text) / 2f + 40f * dp) * u
            rule.shader = android.graphics.LinearGradient(cx - half, y, cx + half, y, intArrayOf(0x00FFFFFF, acc, 0xFFFFFFFF.toInt(), acc, 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP)
            rule.strokeWidth = 1.6f * dp
            rule.alpha = (255 * (1f - out)).toInt()
            c.drawLine(cx - half, y, cx + half, y, rule)
        }
        // 작은 한 줄: 아래에서 떠오른다
        val sp = easeOut((t - IN0 - 0.72f) / 0.6f)
        if (sp * (1f - out) > 0.01f) {
            sub.paint.alpha = (235 * sp * (1f - out)).toInt()
            c.drawText(sub.text, sub.x, sub.y + (1f - sp) * 16f * dp + lift, sub.paint)
        }
    }

    companion object {
        fun build(w: Float, h: Float, dp: Float, words: Triple<String, String, String>, bold: Typeface, regular: Typeface, rnd: Random): Greeting {
            // 화면을 채우는 큰 글씨 두 줄 (가운데)
            val g = min(w * 0.105f, h * 0.165f)
            fun paint(tf: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; isSubpixelText = true; letterSpacing = -0.02f }
            val cy = h * 0.43f
            val b1 = cy - 0.18f * g
            val b2 = b1 + 1.22f * g
            val p1 = paint(bold, g, 0xFFFFF6EA.toInt())
            val p2 = paint(bold, g, 0xFFFFF6EA.toInt())
            val lines = listOf(
                Line(words.first, (w - p1.measureText(words.first)) / 2f, b1, p1),
                Line(words.second, (w - p2.measureText(words.second)) / 2f, b2, p2),
            )
            val ps = paint(regular, g * 0.25f, 0xFFF4EEE4.toInt())
            val sub = Line(words.third, (w - ps.measureText(words.third)) / 2f, b2 + 0.86f * g, ps)
            val dust = FloatArray(3 * 160)
            for (k in 0 until 160) { dust[k * 3] = rnd.nextFloat() * w; dust[k * 3 + 1] = rnd.nextFloat() * h; dust[k * 3 + 2] = rnd.nextFloat() }
            return Greeting(w, h, dp, lines, sub, dust)
        }
    }
}

// ---------------------------------------------------------------- 띠링

/**
 * 띠링 — 인사가 서는 순간(그리고 처음 화면에서 로고가 켜지는 순간) 울리는 맑은 두 음. 소리 파일 없이 그 자리에서 만든다 (종처럼 빨리 맺고 길게 잦아든다).
 * 소리를 낼 수 없는 환경에서는 조용히 넘어간다.
 */
object Chime {
    const val RATE = 44_100

    /** 두 음의 파형 (-1..1). 영상 편집 등에서 같은 소리를 쓰려고 따로 떼어 두었다. */
    fun wave(): FloatArray {
        val n = (RATE * 1.5f).toInt()
        val out = FloatArray(n)
        fun bell(at: Float, f: Float, amp: Float) {
            val s = (at * RATE).toInt()
            for (i in s until n) {
                val x = (i - s) / RATE.toFloat()
                val attack = min(1f, x * 260f)
                val body = sin(2f * PI.toFloat() * f * x) * exp(-x * 4.6f)
                val shine = sin(2f * PI.toFloat() * f * 2.76f * x) * exp(-x * 11f) * 0.32f
                val air = sin(2f * PI.toFloat() * f * 5.4f * x) * exp(-x * 22f) * 0.12f
                out[i] += (body + shine + air) * attack * amp
            }
        }
        bell(0f, 1318.5f, 0.30f)     // 미
        bell(0.17f, 1760f, 0.34f)    // 라
        return out
    }

    fun play() {
        thread(name = "chime", isDaemon = true) {
            runCatching {
                val w = wave()
                val pcm = ShortArray(w.size) { (w[it].coerceIn(-1f, 1f) * 32767f * 0.8f).toInt().toShort() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(1700)
                track.release()
            }
        }
    }
}
