package com.gildongmu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.UserManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 차 사용자 이름: 이 차의 운전자 프로필 이름을 일반 앱이 읽을 수 있을 때만 쓴다.
 * 읽을 수 없거나 비어 있거나 기본 이름("운전자", "Driver" 같은)이면 null — 그때는 이름 없이 인사하고, 시작할 때 이름을 직접 정한다.
 *
 * 2026-11-13 조사 결과 (지금은 null 이 된다):
 *  - 이 앱이 쓰는 Pleos Connect SDK(Vehicle·NaviHelper·STT·TTS)에는 사용자·운전자 프로필 API가 없다 (차 식별은 VIN 정도).
 *  - 운전자 이름은 Pleos Platform SDK 의 ProfileManager.getActiveDriver()?.nickname 으로 얻을 수 있지만,
 *    ai.pleos.permission.PROFILE_QUERY(signature|privileged)가 필요해 승인된 privileged 앱만 쓸 수 있고, Pleos Connect 3.5 이상이 대상이다.
 *  - 안드로이드의 UserManager.getUserName() 도 시스템 앱 전용이다 (일반 앱은 SecurityException). 에뮬레이터의 사용자 이름은 '운전자'.
 * Pleos 가 일반 앱에도 운전자 프로필을 열면, 이 함수 한 곳만 바꾸면 된다.
 */
object CarUser {
    private val generic = setOf("driver", "owner", "guest", "user", "운전자", "게스트", "사용자")
    fun name(context: Context): String? = runCatching {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        um.userName?.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() && it.lowercase() !in generic && it.length <= 12 }
}

/**
 * 런치(온보딩) 화면 — 아직 길동무를 시작하지 않았을 때 앱을 켜면 서는 첫 화면. 설정 › 시연 도구 › '시작 화면부터 보기'로 다시 볼 수 있다.
 *
 *  1. 밤길이 그어지고, 차 한 대가 달려 들어와 길 위를 달린다 (가운데 선이 다가와 발밑으로 지나간다).
 *  2. 달리는 동안, 하늘에 누군가 차 안에서 했을 법한 한마디 네 줄이 빠르게 한 줄씩 적힌다.
 *  3. 말이 끝나면 차는 길 끝으로 멀어져 불빛 한 점이 되고, 길의 두 선이 천천히 휘어 올라 따옴표 둘이 된다.
 *     그 사이에 물음이 적힌다: ‘오늘 하루는 어떠셨나요?’ (목소리로도 묻는다)
 *  4. 물음이 스러지면 두 따옴표가 하나로 모여 로고 「따옴길」이 되고, 길 끝에 남아 있던 불빛이 날아와 로고의 불빛으로 켜진다 (띠링).
 *  5. "오늘의 한마디를 남겨 주세요."와 [시작하기]가 떠오른다. 누르면 이름과 목소리를 정하는 화면으로 넘어간다.
 *
 * 11초 남짓. 화면을 누르면 바로 마지막 모습으로 넘어간다. 달리는 동안에는 움직임 없이 마지막 모습만 보이고, 시작할 수 없다.
 * 로고의 도형은 아이콘과 같은 것이다 (LogoPaths).
 */
@Composable
fun LaunchScreen(m: AppModel, onStart: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val now = remember(m.clockOverride) { m.clock() }
    val part = Look.part(now)
    val driving = m.driving
    val ink = C.Hi
    val invite = "오늘의 한마디를 남겨 주세요."
    val serifBold = remember { ResourcesCompat.getFont(ctx, R.font.maruburi_semibold) ?: Typeface.SERIF }
    val serif = remember { ResourcesCompat.getFont(ctx, R.font.maruburi_regular) ?: Typeface.SERIF }

    var field by remember { mutableStateOf<LaunchField?>(null) }
    val time = remember { mutableFloatStateOf(0f) }          // 그리는 쪽에서만 읽는다 (프레임마다 화면을 다시 짜지 않게)
    var jump by remember { mutableFloatStateOf(0f) }
    var stage by remember { mutableIntStateOf(0) }   // 0 적히는 중 · 1 빛이 맺힘(불빛이 켜짐) · 2 다 나타남
    // (앱이 막 켜졌을 때는 기어를 아직 읽지 못해 잠깐 '주행'으로 보일 수 있다. 그래서 처음 한순간은 기다렸다가, 그때도 달리는 중이면 마지막 모습으로 넘어간다)
    val drivingNow by rememberUpdatedState(driving)
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var spoke = false
        var asked = false
        while (true) {
            val f = field
            val v = (withFrameNanos { it } - start) / 1e9f + jump
            if (f == null) continue
            if (drivingNow && v > 0.8f && v < f.tEnd) { jump += f.tEnd - v; continue }      // 달리는 중이면 움직임 없이 마지막 모습으로
            time.floatValue = v
            // 두 선이 따옴표가 되어 물음을 건넬 때, 길동무가 소리로도 묻는다
            if (!asked && v >= f.tAsk) { asked = true; if (v < f.tAsk + 0.5f) m.voice.say(ASK) }
            if (stage < 1 && v >= f.tIgnite) {
                stage = 1
                if (!spoke && v < f.tIgnite + 0.5f) { spoke = true; Chime.play() }      // 불빛이 켜지는 순간: 띠링 (건너뛰었으면 조용히)
            }
            if (stage < 2 && v >= f.tEnd - 0.75f) stage = 2
        }
    }
    // 창밖: 어둡고 흐릿하게 시작해서, 불빛이 켜질 때 조금 밝아지며 초점이 살짝 돌아온다
    val lit by animateFloatAsState(if (stage >= 1) 1f else 0f, tween(1800, easing = Ease.out), label = "lit")

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, null) { field?.let { f -> if (time.floatValue < f.tEnd) jump += f.tEnd - time.floatValue } }
            .onSizeChanged { sz ->
                if (field == null && sz.width > 0) field = LaunchField.build(sz.width.toFloat(), sz.height.toFloat(), density, invite, serif, serifBold)
            },
    ) {
        val hDp = maxHeight
        SceneStage(Modifier.fillMaxSize(), scene = Look.scene, part = part, rain = m.car.raining, soft = 1f - 0.34f * lit, dim = 0.58f - 0.24f * lit, seed = 7) {
            Box(Modifier.fillMaxSize().background(veil(part, 0f to 0.34f, 0.5f to 0.16f, 1f to 0.46f)))
            Motes(Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val f = field ?: return@Canvas
                drawIntoCanvas { c -> f.draw(c.nativeCanvas, time.floatValue) }
            }
            MakerLine(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).graphicsLayer { alpha = field?.let { ease((time.floatValue - it.tEnd + 0.6f) / 0.8f) } ?: 0f }, alpha = 0.6f)
            // 위 왼쪽: 이름표 (마지막에 조용히)
            Row(
                Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 50.dp).graphicsLayer { alpha = field?.let { ease((time.floatValue - it.tEnd + 0.9f) / 0.8f) } ?: 0f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("길동무", style = ts(34, ink, FontWeight.SemiBold, family = Myeongjo).copy(letterSpacing = 0.04.em).onScene(0.5f))
                Spacer(Modifier.width(14.dp))
                Text("운전길에 말로 쓰는 일기", style = ts(19, ink.copy(alpha = 0.84f), FontWeight.Medium).copy(letterSpacing = 0.05.em).onScene(0.6f))
            }
            // 아래: 한 줄 설명과 [시작하기]
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = hDp * 0.690f).fillMaxWidth()
                    .graphicsLayer {
                        val a = field?.let { ease((time.floatValue - it.tEnd + 0.85f) / 0.7f) } ?: 0f
                        alpha = a; translationY = (1f - a) * 22.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("운전하면서 말로 남기면, 길동무가 받아 적어 오늘의 일기로 담아 둘게요.", style = ts(24, ink.copy(alpha = 0.86f), lh = 1.5f).onScene(0.6f))
                Spacer(Modifier.height(34.dp))
                if (driving) Row(verticalAlignment = Alignment.CenterVertically) {
                    Lamp(C.Amber, size = 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("차를 세우면 시작할 수 있어요", style = ts(24, ink.copy(alpha = 0.92f), FontWeight.Medium).onScene(0.6f))
                } else if (stage >= 2) {
                    Breathing { Btn("시작하기", glyph = G.Arrow, kind = BtnKind.Primary, big = true) { onStart() } }
                    Spacer(Modifier.height(14.dp))
                    Text("처음 한 번, 이름과 목소리만 정하면 돼요", style = ts(18, ink.copy(alpha = 0.74f)).onScene(0.6f))
                } else Spacer(Modifier.height(84.dp))
            }
        }
    }
}

private fun ease(x: Float): Float { val v = x.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }
private fun easeOut(x: Float): Float { val v = x.coerceIn(0f, 1f); return 1f - (1f - v) * (1f - v) * (1f - v) }
/** 0 → 1 → 0 으로 한 번 솟는 모양 (x: 0..1) */
private fun bump(x: Float): Float = if (x <= 0f || x >= 1f) 0f else sin(x * Math.PI.toFloat())
/** 살짝 넘쳤다가 제자리로 */
private fun overshoot(x: Float): Float { val v = x.coerceIn(0f, 1f) - 1f; return 1f + 2.2f * v * v * v + 1.2f * v * v }

/** 차 안에서 누군가 했을 법한 한마디들 (앞의 둘은 의뢰인이 준 말 그대로) */
private val SAID = listOf(
    "“아 오늘 하루도 고생했다. 어서 집에 가서 쉬자.”",
    "“오늘 정말 재미있었다. 인생에 꼽히는 날이야.”",
    "“엄마한테 전화나 해 볼까. 괜히 보고 싶네.”",
    "“드디어 금요일이다. 주말엔 애들이랑 바다 보러 가야지.”",
)
private const val ASK = "오늘 하루는 어떠셨나요?"

/**
 * 런치 화면의 그림: 밤길을 달리는 차 → 그 위로 적히는 한마디들 → 길의 두 선이 따옴표가 되어 건네는 물음 → 두 따옴표가 모인 로고.
 * 모든 값은 화면 픽셀 기준. 안드로이드 캔버스에 직접 그린다.
 *
 * 로고 「따옴길」은 '따옴표의 꼬리가 차선이 되어 길 끝의 불빛에 닿는' 그림이다. 이 화면은 그 그림을 거꾸로 푼다:
 * 처음에는 길(두 선)과 길 끝이 있고, 그 두 선이 따옴표가 되고, 길 끝으로 멀어진 차의 불빛이 로고의 불빛이 된다.
 */
private class LaunchField(
    val w: Float, val h: Float, val dp: Float,
    val lines: List<Typed>, val ask: Typed, val invite: Line,
    val unit: Float, val ox: Float, val oy: Float,           // 로고의 자리: 108칸 좌표의 원점과 한 칸의 크기
) {
    class Line(val text: String, val x: Float, val y: Float, val width: Float, val paint: Paint)
    /** 한 글자씩 적히는 글: at[k] = k번째 글자가 나타나는 시각, xs[k] = 그 글자의 x */
    class Typed(val text: String, val x: Float, val y: Float, val width: Float, val paint: Paint, val at: FloatArray, val xs: FloatArray, val start: Float, val end: Float)

    // ---- 때
    val tOut = lines.last().end + 0.5f       // 한마디들이 스러지고, 차가 길 끝으로 멀어진다
    val tQ0 = tOut + 0.45f                   // 길의 두 선이 휘어 올라 따옴표가 되기 시작한다
    val tAsk = ask.start                     // 물음이 적히기 시작한다
    val tMerge = ask.end + 1.35f             // 물음이 스러지고, 두 따옴표가 모이기 시작한다
    val tLock = tMerge + 1.15f               // 두 따옴표가 로고의 자리에 선다
    val tIgnite = tLock + 0.35f              // 길 끝에서 날아온 불빛이 켜진다 (띠링)
    val tEnd = tIgnite + 1.3f

    // ---- 길: 지평선의 한 점으로 모이는 두 선과 가운데 선
    private val hor = h * 0.60f
    private val vx = w * 0.5f
    private val half = w * 0.47f
    private fun ry(u: Float) = hor + (h - hor) * u                 // u: 0 지평선 … 1 화면 아래
    private fun rx(side: Float, u: Float) = vx + side * half * u

    // ---- 따옴표: 물음 곁에 설 때의 자리(왼쪽은 여는 따옴표, 오른쪽은 뒤집힌 닫는 따옴표)와, 로고가 될 때의 자리
    private val qs = ask.paint.textSize * 1.12f / 31.8f
    private val qy = ask.y - ask.paint.textSize * 0.50f
    private val qxL = ask.x - 15f * qs
    private val qxR = ask.x + ask.width + 15f * qs
    private val lcx0 = ox + C0X * unit
    private val lcx1 = ox + (C0X + 19.162f) * unit
    private val lcy = oy + C0Y * unit
    private val lightX = ox + LogoPaths.LIGHT_X * unit
    private val lightY = oy + LogoPaths.LIGHT_Y * unit

    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = add }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = add }
    private val path = Path()
    private val glowText = Paint(invite.paint).apply { maskFilter = BlurMaskFilter(22f * dp, BlurMaskFilter.Blur.NORMAL) }
    private val glowAsk = Paint(ask.paint).apply { maskFilter = BlurMaskFilter(20f * dp, BlurMaskFilter.Blur.NORMAL) }

    // 길바닥: 젖은 밤길 (뒤의 불빛을 가라앉힌다)
    private val roadPath = Path().apply { moveTo(vx, hor); lineTo(rx(1f, 1.05f), ry(1.05f)); lineTo(rx(-1f, 1.05f), ry(1.05f)); close() }
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, hor, 0f, h, intArrayOf(0x000B0D1A, 0x660B0D1A, 0x8F0A0B16.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, hor, 0f, h, intArrayOf(0xFFFFB25C.toInt(), 0xFFFFDDB4.toInt(), 0xFFFFF4E6.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.4f * dp; xfermode = add
        shader = LinearGradient(vx - w * 0.36f, 0f, vx + w * 0.36f, 0f, intArrayOf(0x00FFC98A, 0x99FFD9A8.toInt(), 0x00FFC98A), null, Shader.TileMode.CLAMP)
    }

    // 차 (뒤에서 본 모습, 너비 1 · 바닥 가운데가 원점)
    private val carBody = Path().apply {
        moveTo(-0.50f, 0f); lineTo(-0.50f, -0.30f)
        cubicTo(-0.50f, -0.44f, -0.44f, -0.50f, -0.40f, -0.54f)
        cubicTo(-0.36f, -0.70f, -0.32f, -0.82f, -0.22f, -0.84f)
        lineTo(0.22f, -0.84f)
        cubicTo(0.32f, -0.82f, 0.36f, -0.70f, 0.40f, -0.54f)
        cubicTo(0.44f, -0.50f, 0.50f, -0.44f, 0.50f, -0.30f)
        lineTo(0.50f, 0f)
        cubicTo(0.50f, 0.04f, 0.46f, 0.06f, 0.42f, 0.06f); lineTo(-0.42f, 0.06f)
        cubicTo(-0.46f, 0.06f, -0.50f, 0.04f, -0.50f, 0f); close()
    }
    private val carGlass = Path().apply {
        moveTo(-0.21f, -0.78f); lineTo(0.21f, -0.78f)
        cubicTo(0.27f, -0.77f, 0.30f, -0.68f, 0.33f, -0.56f); lineTo(-0.33f, -0.56f)
        cubicTo(-0.30f, -0.68f, -0.27f, -0.77f, -0.21f, -0.78f); close()
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, -0.84f, 0f, 0.06f, intArrayOf(0xFF262B4A.toInt(), 0xFF14172C.toInt(), 0xFF0A0C18.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(-0.3f, -0.80f, 0.3f, -0.54f, intArrayOf(0xCC55608F.toInt(), 0xCC222848.toInt(), 0xCC3A4470.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.012f; shader = LinearGradient(0f, -0.86f, 0f, -0.30f, 0x99FFF1DC.toInt(), 0x00FFF1DC, Shader.TileMode.CLAMP) }
    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(-0.46f, 0f, 0.46f, 0f, intArrayOf(0xFFFF6A4F.toInt(), 0xFFFFA070.toInt(), 0xFFFF7A5A.toInt(), 0xFFFFA070.toInt(), 0xFFFF6A4F.toInt()), null, Shader.TileMode.CLAMP) }
    private val tailGlowL = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = add; shader = RadialGradient(-0.31f, -0.37f, 0.30f, intArrayOf(0x99FF6A4F.toInt(), 0x33FF6A4F, 0x00FF6A4F), null, Shader.TileMode.CLAMP) }
    private val tailGlowR = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = add; shader = RadialGradient(0.31f, -0.37f, 0.30f, intArrayOf(0x99FF6A4F.toInt(), 0x33FF6A4F, 0x00FF6A4F), null, Shader.TileMode.CLAMP) }
    private val wetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = add; shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x70FF6A4F, 0x2EFF6A4F, 0x00FF6A4F), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) }
    private val rect = android.graphics.RectF()

    // 로고 (108 칸 좌표의 도형과 칠)
    private val quoteL: Path = PathParser.createPathFromPathData(LogoPaths.QUOTE_L)
    // 선이 따옴표로 바뀌는 모양 변형: 따옴표의 윤곽을 점으로 뽑아, '발에서 꼬리 끝으로 가는 등뼈'를 기준으로 한 자리(등뼈를 따라 qsx, 옆으로 qsy)로 적어 둔다.
    // 길의 선도 같은 차례의 점들(등뼈를 따라 lu, 어느 쪽 가장자리인지 lv)로 보면, 두 모양 사이를 점마다 이어 줄 수 있다.
    private val qsx = FloatArray(MN); private val qsy = FloatArray(MN); private val lu = FloatArray(MN); private val lv = FloatArray(MN)
    private val morphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    init {
        val pm = android.graphics.PathMeasure(quoteL, true)
        val len = pm.length; val pos = FloatArray(2)
        val ax = TIP_X - FOOT_X; val ay = TIP_Y - FOOT_Y
        val sl = kotlin.math.hypot(ax, ay); val ux = ax / sl; val uy = ay / sl
        var k0 = 1; var minS = Float.MAX_VALUE
        for (i in 0 until MN) {
            pm.getPosTan(len * i / MN, pos, null)
            val dx = pos[0] - FOOT_X; val dy = pos[1] - FOOT_Y
            qsx[i] = (dx * ux + dy * uy) / sl; qsy[i] = (-dx * uy + dy * ux) / sl
            if (qsx[i] < minS) { minS = qsx[i]; k0 = i }
        }
        k0 = k0.coerceIn(1, MN - 2)
        var sum = 0f; for (i in 0 until k0) sum += qsy[i]
        val sgn = if (sum >= 0f) 1f else -1f
        for (i in 0 until MN) {
            if (i <= k0) { lu[i] = 1f - i.toFloat() / k0; lv[i] = sgn } else { lu[i] = (i - k0).toFloat() / (MN - k0); lv[i] = -sgn }
        }
    }
    private val dashL = LogoPaths.DASH_L.map { PathParser.createPathFromPathData(it) }
    private val dashR = LogoPaths.DASH_R.map { PathParser.createPathFromPathData(it) }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, LogoPaths.INK_Y0, 0f, LogoPaths.INK_Y1, LogoPaths.INK_COLORS, LogoPaths.INK_STOPS, Shader.TileMode.CLAMP)
    }
    private val flash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt(); xfermode = add }
    private val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y, LogoPaths.SKY_GLOW, intArrayOf(0x408C7BFF, 0x208C7BFF, 0x0A8C7BFF, 0x008C7BFF), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    private val lightGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y, LogoPaths.LIGHT_GLOW, intArrayOf(0xA8FFC98A.toInt(), 0x54FFC98A, 0x18FFC98A, 0x00FFC98A), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        xfermode = add
    }
    private val lightCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y, LogoPaths.LIGHT_CORE, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF4E6.toInt(), 0xFFFFD9A8.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = LogoPaths.HORIZON_T }
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; xfermode = add }
    private val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = add }

    /** 한 글자씩 적히는 글 (방금 적힌 글자는 아래에서 살짝 떠오른다) */
    private fun typed(c: android.graphics.Canvas, l: Typed, t: Float, alpha: Float, dy: Float, caret: Boolean) {
        if (t < l.start || alpha <= 0.01f) return
        var shown = 0
        while (shown < l.at.size && l.at[shown] <= t) shown++
        var full = shown
        while (full > 0 && t - l.at[full - 1] < 0.14f) full--
        if (full > 0) { l.paint.alpha = (255 * alpha).toInt(); c.drawText(l.text, 0, full, l.x, l.y + dy, l.paint) }
        for (j in full until shown) {
            val av = ((t - l.at[j]) / 0.14f).coerceIn(0f, 1f)
            l.paint.alpha = (255 * alpha * av).toInt()
            c.drawText(l.text, j, j + 1, l.x + l.xs[j], l.y + dy + (1f - av) * 7f * dp, l.paint)
        }
        if (caret && shown < l.at.size) {       // 글자 끝을 따라가는 빛
            val size = l.paint.textSize
            val cx = l.x + l.xs[shown] + 8f * dp
            val pulse = 0.65f + 0.35f * sin(t * 9f)
            dot.color = 0xFFFFF1DC.toInt(); dot.alpha = (210 * pulse * alpha).toInt(); c.drawCircle(cx, l.y - size * 0.30f, 3.2f * dp, dot)
            dot.alpha = (60 * pulse * alpha).toInt(); c.drawCircle(cx, l.y - size * 0.30f, 10f * dp, dot)
        }
    }

    /** 따옴표 하나 (로고의 왼쪽 따옴표 도형을, 가운데를 잡고 놓는다) */
    private fun quote(c: android.graphics.Canvas, cx: Float, cy: Float, s: Float, deg: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        c.save(); c.translate(cx, cy); c.rotate(deg); c.scale(s, s); c.translate(-C0X, -C0Y)
        inkPaint.alpha = (255 * alpha).toInt(); c.drawPath(quoteL, inkPaint)
        c.restore()
    }

    /**
     * 길의 선 하나가 따옴표 하나로 바뀌는 모습 (q: 0 길의 선 … 1 따옴표).
     * 선이 통째로 떠올라 돌면서 짧아지고(먼저), 발 쪽이 둥글게 부풀며 꼬리가 휘어 따옴표의 윤곽이 된다(나중). 겹쳐 바꾸는 것이 아니라 같은 도형이 자란다.
     */
    private fun morph(c: android.graphics.Canvas, side: Float, q: Float) {
        val a0x = rx(side, 1.05f); val a0y = ry(1.05f)                    // 길의 선: 발밑 끝 → 지평선 끝
        val qx = if (side < 0f) qxL else qxR; val sg = if (side < 0f) 1f else -1f
        val fx = qx + sg * (FOOT_X - C0X) * qs; val fy = qy + sg * (FOOT_Y - C0Y) * qs      // 따옴표: 발 → 꼬리 끝
        val tx = qx + sg * (TIP_X - C0X) * qs; val ty = qy + sg * (TIP_Y - C0Y) * qs
        val l0 = kotlin.math.hypot(vx - a0x, hor - a0y); val l1 = kotlin.math.hypot(tx - fx, ty - fy)
        val th0 = kotlin.math.atan2(hor - a0y, vx - a0x)
        var d = kotlin.math.atan2(ty - fy, tx - fx) - th0
        val pi = Math.PI.toFloat()
        while (d > pi) d -= 2f * pi
        while (d < -pi) d += 2f * pi
        val e = ease(q)
        val th = th0 + d * e
        val len = kotlin.math.exp(kotlin.math.ln(l0) + (kotlin.math.ln(l1) - kotlin.math.ln(l0)) * easeOut(q * 1.15f))
        val mx = (a0x + vx) / 2f + ((fx + tx) / 2f - (a0x + vx) / 2f) * e
        val my = (a0y + hor) / 2f + ((fy + ty) / 2f - (a0y + hor) / 2f) * e - sin(q * pi) * h * 0.045f
        val ca = kotlin.math.cos(th); val sa = sin(th)
        val f0x = mx - ca * len / 2f; val f0y = my - sa * len / 2f
        val k = ease((q - 0.22f) / 0.78f)
        path.rewind()
        for (i in 0 until MN) {
            val u = lu[i]
            val hw = (3.4f + (0.6f - 3.4f) * u) * dp
            val along = (u + (qsx[i] - u) * k) * len
            val perp = lv[i] * hw + (qsy[i] * l1 - lv[i] * hw) * k
            val x = f0x + ca * along - sa * perp; val y = f0y + sa * along + ca * perp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        morphPaint.shader = LinearGradient(f0x, f0y, f0x + ca * len, f0y + sa * len, LogoPaths.INK_COLORS, LogoPaths.INK_STOPS, Shader.TileMode.CLAMP)
        c.drawPath(path, morphPaint)
    }

    /** 뒤에서 본 차: cx·base 는 바닥 가운데, cw 는 너비 */
    private fun car(c: android.graphics.Canvas, cx: Float, base: Float, cw: Float, alpha: Float, t: Float) {
        if (alpha <= 0.01f) return
        if (cw < 30f * dp) {                    // 멀어지면 불빛 한 점
            val k = (cw / (30f * dp)).coerceIn(0f, 1f)
            dot.color = 0xFFFF8A60.toInt(); dot.alpha = (150 * alpha).toInt(); c.drawCircle(cx, base - cw * 0.37f, (4f + 8f * k) * dp, dot)
            dot.color = 0xFFFFD9A8.toInt(); dot.alpha = (230 * alpha).toInt(); c.drawCircle(cx, base - cw * 0.37f, (1.6f + 1.6f * k) * dp, dot)
            return
        }
        val a = (255 * alpha).toInt()
        c.save(); c.translate(cx, base + sin(t * 5.3f) * 0.9f * dp); c.scale(cw, cw)
        // 젖은 길에 비친 뒷불
        for (sx in floatArrayOf(-0.31f, 0.31f)) {
            wetPaint.alpha = (a * (0.75f + 0.25f * sin(t * 3.1f + sx * 9f))).toInt()
            c.save(); c.translate(sx, 0.08f); c.scale(0.17f, 0.78f); c.drawCircle(0f, 0f, 1f, wetPaint); c.restore()
        }
        fill.shader = null; fill.color = 0xFF04050A.toInt(); fill.alpha = (a * 0.55f).toInt()
        rect.set(-0.58f, -0.02f, 0.58f, 0.13f); c.drawOval(rect, fill)                         // 바닥 그림자
        fill.alpha = a
        rect.set(-0.475f, -0.04f, -0.30f, 0.11f); c.drawRoundRect(rect, 0.035f, 0.035f, fill)     // 바퀴
        rect.set(0.30f, -0.04f, 0.475f, 0.11f); c.drawRoundRect(rect, 0.035f, 0.035f, fill)
        bodyPaint.alpha = a; c.drawPath(carBody, bodyPaint)
        glassPaint.alpha = a; c.drawPath(carGlass, glassPaint)
        rimPaint.alpha = a; c.drawPath(carBody, rimPaint)
        fill.color = 0xFF080A14.toInt(); fill.alpha = a
        rect.set(-0.105f, -0.265f, 0.105f, -0.185f); c.drawRoundRect(rect, 0.012f, 0.012f, fill)   // 번호판 자리
        // 뒷불: 가로로 긴 불빛 두 개와 그 사이의 가는 줄
        tailPaint.alpha = a
        rect.set(-0.455f, -0.405f, -0.17f, -0.335f); c.drawRoundRect(rect, 0.03f, 0.03f, tailPaint)
        rect.set(0.17f, -0.405f, 0.455f, -0.335f); c.drawRoundRect(rect, 0.03f, 0.03f, tailPaint)
        tailPaint.alpha = (a * 0.55f).toInt(); rect.set(-0.17f, -0.378f, 0.17f, -0.362f); c.drawRect(rect, tailPaint)
        val pulse = 0.85f + 0.15f * sin(t * 2.3f)
        tailGlowL.alpha = (a * pulse).toInt(); c.drawCircle(-0.31f, -0.37f, 0.30f, tailGlowL)
        tailGlowR.alpha = (a * pulse).toInt(); c.drawCircle(0.31f, -0.37f, 0.30f, tailGlowR)
        c.restore()
    }

    /** 가는 사다리꼴 선: (ax,ay) 에서 (bx,by) 까지, 양 끝의 반 너비 ha·hb */
    private fun taper(ax: Float, ay: Float, bx: Float, by: Float, ha: Float, hb: Float): Path {
        val dx = bx - ax; val dy = by - ay
        val d = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = -dy / d; val ny = dx / d
        path.rewind()
        path.moveTo(ax + nx * ha, ay + ny * ha); path.lineTo(bx + nx * hb, by + ny * hb)
        path.lineTo(bx - nx * hb, by - ny * hb); path.lineTo(ax - nx * ha, ay - ny * ha); path.close()
        return path
    }

    fun draw(c: android.graphics.Canvas, t: Float) {
        val warm = 0xFFFFF1DC.toInt()
        val amb = 0xFFFFC98A.toInt()
        val roadIn = easeOut(t / 1.0f)                                   // 길이 발밑에서부터 그어진다
        val roadA = roadIn * (1f - ease((t - tQ0) / 0.7f))               // 따옴표가 되기 시작하면 길바닥과 가운데 선은 스러진다
        val q1 = ((t - tQ0) / 1.55f).coerceIn(0f, 1f)                    // 길의 두 선 → 따옴표 둘 (morph 가 스스로 느리게 시작하고 느리게 끝낸다)
        val q2 = ease((t - tMerge - 0.1f) / (tLock - tMerge - 0.1f))     // 따옴표 둘 → 로고

        // 1) 길바닥, 지평선의 빛, 다가오는 가운데 선
        if (roadA > 0.01f) {
            surface.alpha = (255 * roadA).toInt(); c.drawPath(roadPath, surface)
            horizonPaint.alpha = (255 * roadA).toInt(); c.drawLine(vx - w * 0.36f, hor, vx + w * 0.36f, hor, horizonPaint)
            val flow = t * 0.62f
            for (k in 0 until 8) {
                val u0 = ((k + flow) / 8f) % 1f
                val ua = Math.pow(u0.toDouble(), 2.1).toFloat(); val ub = Math.pow((u0 + 0.05f).coerceAtMost(1.02f).toDouble(), 2.1).toFloat()
                val a = roadA * ease(u0 / 0.14f) * (1f - ease((u0 - 0.90f) / 0.10f))
                if (a <= 0.01f || ua > 1f - (1f - roadIn)) continue
                taper(vx, ry(ua), vx, ry(ub), (0.5f + 4.2f * ua) * dp, (0.5f + 4.2f * ub) * dp)
                fill.shader = null; fill.color = amb; fill.alpha = (235 * a).toInt(); c.drawPath(path, fill)
                glowFill.color = amb; glowFill.alpha = (46 * a).toInt()
                taper(vx, ry(ua), vx, ry(ub), (3f + 12f * ua) * dp, (3f + 12f * ub) * dp); c.drawPath(path, glowFill)
            }
        }

        // 2) 길의 두 선: 그어지고 → 휘어 올라 따옴표의 꼬리가 된다
        for (side in floatArrayOf(-1f, 1f)) {
            if (q1 <= 0f) {
                val top = 1.05f - 1.03f * roadIn
                if (roadIn > 0.01f) {
                    glowFill.color = amb; glowFill.alpha = (34 * ease(t / 0.4f)).toInt()
                    c.drawPath(taper(rx(side, 1.05f), ry(1.05f), rx(side, top), ry(top), 13f * dp, (2f + 9f * top) * dp), glowFill)
                    edgePaint.alpha = (240 * ease(t / 0.4f)).toInt(); c.drawPath(taper(rx(side, 1.05f), ry(1.05f), rx(side, top), ry(top), 3.4f * dp, (0.5f + 2.9f * top) * dp), edgePaint)
                }
            } else if (q1 < 1f) morph(c, side, q1)
        }

        // 3) 차 (길의 선보다 앞에 있다): 발밑에서 달려 들어와 길 위를 달리다가, 한마디가 끝나면 길 끝으로 멀어져 불빛 한 점이 된다
        val away = ((t - (tOut - 0.15f)) / 1.3f).coerceIn(0f, 1f)
        val uCar = if (away <= 0f) 0.60f + 0.70f * (1f - easeOut(t / 1.2f)) else 0.015f + 0.585f * Math.pow((1f - away * away).toDouble(), 2.2).toFloat()
        val carX = vx + 0.5f * half * uCar
        val farX = vx + 0.5f * half * 0.015f; val farY = ry(0.015f)
        val carGone = t > tOut - 0.15f + 1.3f
        if (!carGone) {
            val cw = 0.68f * half * uCar
            val carA = ease(t / 0.5f)
            // 앞을 비추는 불빛
            val poolU = uCar * 0.60f
            bloom.shader = RadialGradient(vx + 0.5f * half * poolU, ry(poolU), cw * 1.15f, intArrayOf(0x30FFE9C4, 0x12FFE9C4, 0x00FFE9C4), null, Shader.TileMode.CLAMP)
            c.save(); c.scale(1f, 0.30f, 0f, ry(poolU)); bloom.alpha = (255 * carA * roadA.coerceAtLeast(0.3f)).toInt(); c.drawCircle(vx + 0.5f * half * poolU, ry(poolU), cw * 1.15f, bloom); c.restore()
            car(c, carX, ry(uCar), cw, carA, t)
        }

        // 4) 누군가의 한마디: 하늘에 한 줄씩, 빠르게. 다음 말이 나오면 앞의 말은 조금 물러난다.
        if (t < tOut + 0.6f) {
            val out = ease((t - tOut) / 0.55f)
            for ((k, l) in lines.withIndex()) {
                val next = lines.getOrNull(k + 1)?.start ?: Float.MAX_VALUE
                typed(c, l, t, (0.97f - 0.30f * ease((t - next) / 0.5f)) * (1f - out), -26f * dp * out, true)
            }
        }

        // 5) 따옴표 둘과 그 사이의 물음 → 로고의 자리로 모인다 (오른쪽 따옴표는 돌아서 같은 쪽을 본다)
        val qa = if (q1 >= 1f) 1f else 0f
        if (qa > 0.01f) {
            val arc = sin(q2 * Math.PI.toFloat()) * h * 0.035f
            val s = qs + (unit - qs) * q2
            quote(c, qxL + (lcx0 - qxL) * q2, qy + (lcy - qy) * q2 - arc, s, 0f, qa)
            quote(c, qxR + (lcx1 - qxR) * q2, qy + (lcy - qy) * q2 - arc, s, 180f + 180f * q2, qa)
        }
        if (t >= tAsk && t < tMerge + 0.5f) {
            val out = ease((t - tMerge) / 0.45f)
            glowAsk.color = warm; glowAsk.alpha = (90 * ease((t - tAsk - 0.3f) / 0.5f) * (1f - out)).toInt()
            if (glowAsk.alpha > 2) c.drawText(ask.text, ask.x, ask.y - 16f * dp * out, glowAsk)
            typed(c, ask, t, 1f - out, -16f * dp * out, false)
        }

        // 6) 로고: 차선이 가까운 것부터 켜지고, 길 끝에 남아 있던 불빛이 날아와 켜진다
        val fly = ((t - (tLock - 0.45f)) / (tIgnite - tLock + 0.45f)).coerceIn(0f, 1f)
        if (carGone && t < tIgnite) {
            val e = ease(fly)
            val x = farX + (lightX - farX) * e; val y = farY + (lightY - farY) * e - sin(e * Math.PI.toFloat()) * h * 0.04f
            val tw = 0.75f + 0.25f * sin(t * 6f)
            bloom.shader = RadialGradient(x, y, 30f * dp, intArrayOf(0x88FFC98A.toInt(), 0x30FFC98A, 0x00FFC98A), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            bloom.alpha = (255 * tw).toInt(); c.drawCircle(x, y, 30f * dp, bloom)
            dot.color = warm; dot.alpha = (250 * tw).toInt(); c.drawCircle(x, y, 3.6f * dp, dot)
        }
        if (q2 > 0.985f) {
            val li = t - tIgnite
            val idle = if (t > tEnd) ((t - tEnd) % 5.2f) else -1f
            val rr = li / 1.5f
            if (rr > 0f && rr < 1f) {          // 불빛이 켜지는 순간: 은은한 빛이 번지고, 지평선을 따라 빛줄기가 그어졌다가 스러진다
                val k = bump(rr).let { it * it }
                val br = w * (0.10f + 0.12f * easeOut(rr))
                bloom.shader = RadialGradient(lightX, lightY, br, intArrayOf(amb and 0x00FFFFFF or ((70 * k).toInt() shl 24), amb and 0x00FFFFFF or ((26 * k).toInt() shl 24), amb and 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                bloom.alpha = 255; c.drawCircle(lightX, lightY, br, bloom)
                val reach = w * 0.56f * easeOut(rr / 0.5f)
                val fade = 1f - ease((rr - 0.30f) / 0.70f)
                if (reach > 2f && fade > 0.01f) {
                    val aw = (235 * fade).toInt() shl 24; val aa = (120 * fade).toInt() shl 24
                    beam.shader = LinearGradient(lightX - reach, 0f, lightX + reach, 0f, intArrayOf(amb and 0x00FFFFFF, amb and 0x00FFFFFF or aa, warm and 0x00FFFFFF or aw, amb and 0x00FFFFFF or aa, amb and 0x00FFFFFF), floatArrayOf(0f, 0.34f, 0.5f, 0.66f, 1f), Shader.TileMode.CLAMP)
                    beam.alpha = 255; beam.strokeWidth = 1.5f * dp; c.drawLine(lightX - reach, lightY, lightX + reach, lightY, beam)
                    beam.alpha = 90; beam.strokeWidth = 7f * dp; c.drawLine(lightX - reach * 0.7f, lightY, lightX + reach * 0.7f, lightY, beam)
                }
            }
            c.save(); c.translate(ox, oy); c.scale(unit, unit)
            if (li > 0f) {
                sky.alpha = (255 * ease(li / 1.2f)).toInt(); c.drawCircle(LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y, LogoPaths.SKY_GLOW, sky)
                val e = easeOut(li / 0.9f)
                val x0 = LogoPaths.LIGHT_X + (LogoPaths.HORIZON_X0 - LogoPaths.LIGHT_X) * e
                val x1 = LogoPaths.LIGHT_X + (LogoPaths.HORIZON_X1 - LogoPaths.LIGHT_X) * e
                horizon.shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(0x00FFC98A, 0xE6FFD9A8.toInt(), 0x00FFC98A), floatArrayOf(0f, (LogoPaths.LIGHT_X - x0) / (x1 - x0).coerceAtLeast(0.01f), 1f), Shader.TileMode.CLAMP)
                c.drawLine(x0, LogoPaths.LIGHT_Y, x1, LogoPaths.LIGHT_Y, horizon)
            }
            for (j in 0..2) {
                val t0 = tLock - 0.10f + j * 0.12f
                val da = easeOut((t - t0) / 0.26f)
                if (da <= 0.01f) continue
                inkPaint.alpha = (255 * da).toInt()
                c.drawPath(dashL[j], inkPaint); c.drawPath(dashR[j], inkPaint)
                val fl = 0.85f * bump((t - t0) / 0.42f) + (if (idle >= 0f) 0.34f * bump((idle - 0.3f - j * 0.13f) / 0.5f) else 0f)
                if (fl > 0.01f) { flash.alpha = (255 * fl.coerceAtMost(1f)).toInt(); c.drawPath(dashL[j], flash); c.drawPath(dashR[j], flash) }
            }
            if (li > 0f) {
                val breathe = 1f + 0.10f * sin(t * 1.5f) + (if (idle >= 0f) 0.5f * bump((idle - 0.75f) / 0.7f) else 0f)
                val pop = overshoot(li / 0.5f)
                c.save(); c.scale(pop * breathe, pop * breathe, LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y)
                c.drawCircle(LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y, LogoPaths.LIGHT_GLOW, lightGlow); c.restore()
                c.save(); c.scale(pop, pop, LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y)
                c.drawCircle(LogoPaths.LIGHT_X, LogoPaths.LIGHT_Y, LogoPaths.LIGHT_CORE, lightCore); c.restore()
            }
            c.restore()
        }

        // 7) 마지막 한 줄: 불빛이 켜지면 왼쪽부터 또렷해진다
        val f = (t - tIgnite - 0.15f) / 0.75f
        if (f > 0f) {
            glowText.color = warm; glowText.alpha = (110 * ease(f) * (0.4f + 0.6f * (1f - ease((t - tIgnite - 0.9f) / 1.4f)))).toInt()
            if (glowText.alpha > 2) c.drawText(invite.text, invite.x, invite.y, glowText)
            val p = invite.paint
            p.alpha = 255
            if (f < 1.26f) { val x1 = invite.x + invite.width * f; p.shader = LinearGradient(x1 - invite.width * 0.26f, 0f, x1, 0f, INK, INK and 0x00FFFFFF, Shader.TileMode.CLAMP) }
            c.drawText(invite.text, invite.x, invite.y, p)
            p.shader = null
        }
    }

    companion object {
        const val INK = 0xFFFFF6EA.toInt()
        // 따옴표 도형(왼쪽)의 가운데, 발, 꼬리 끝 (108칸 좌표)
        const val C0X = 38.5f; const val C0Y = 61.0f
        const val FOOT_X = 38.6f; const val FOOT_Y = 69.5f
        const val TIP_X = 42.994f; const val TIP_Y = 46.104f
        const val MN = 96                       // 윤곽을 뽑는 점의 수

        fun build(w: Float, h: Float, dp: Float, inviteText: String, regular: Typeface, bold: Typeface): LaunchField {
            fun paint(tf: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; color = INK; isSubpixelText = true; letterSpacing = -0.015f }
            val u = min(w / 1680f, h / 1208f)          // 화면이 달라도 같은 비례로
            fun type(text: String, p: Paint, x: Float, y: Float, start: Float, per: Float): Typed {
                val xs = FloatArray(text.length) { p.measureText(text, 0, it) }
                val at = FloatArray(text.length)
                var tt = start
                for (i in text.indices) { at[i] = tt; tt += when (text[i]) { '.', '…', '?' -> per + 0.13f; ',' -> per + 0.07f; ' ' -> per * 0.6f; else -> per } }
                return Typed(text, x, y, p.measureText(text), p, at, xs, start, tt)
            }
            // ---- 한마디 네 줄: 길 위의 하늘에, 번갈아 왼쪽·오른쪽으로, 빠르게 한 줄씩
            val rows = floatArrayOf(0.135f, 0.245f, 0.355f, 0.465f)
            val said = ArrayList<Typed>()
            var start = 0.9f
            for ((k, text) in SAID.withIndex()) {
                val p = paint(regular, 50f * u)
                val width = p.measureText(text)
                val x = if (k % 2 == 0) w * (0.085f + 0.030f * k) else w - width - w * (0.085f + 0.018f * k)
                val l = type(text, p, x, h * rows[k], start, 0.024f)
                said += l
                start = l.end + 0.14f
            }
            val tQ0 = said.last().end + 0.5f + 0.45f
            // ---- 물음: 두 따옴표 사이에 (가운데)
            val pAsk = paint(bold, 82f * u)
            val ask = type(ASK, pAsk, (w - pAsk.measureText(ASK)) / 2f, h * 0.405f, tQ0 + 1.15f, 0.055f)
            // ---- 마지막 모습: 로고와 한 줄
            val pInvite = paint(bold, 78f * u)
            val wInvite = pInvite.measureText(inviteText)
            val invite = Line(inviteText, (w - wInvite) / 2f, h * 0.640f, wInvite, pInvite)
            val unit = 372f * u / 72f                      // 로고의 한 칸 (보이는 72칸이 372dp)
            val ox = w / 2f - 6f * unit - 54f * unit       // (따옴표와 길의 무게 중심이 화면 가운데에 오도록 살짝 왼쪽으로)
            val oy = h * 0.385f - 54f * unit
            return LaunchField(w, h, dp, said, ask, invite, unit, ox, oy)
        }
    }
}

/** 로고 (앱 아이콘의 앞 그림). 은은한 빛이 한 번 숨 쉬듯 번진다. */
@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.9f; scaleY = 1.9f }) {
            val t = clock.value
            val a = 0.16f + 0.08f * sin(t * 1.3f)
            drawCircle(Brush.radialGradient(listOf(C.Accent.copy(alpha = a), Color.Transparent), center, size.minDimension / 2f), size.minDimension / 2f)
        }
        // 아이콘의 앞 그림은 108 칸 가운데 72 칸에 그려져 있으므로, 그만큼 키워서 보인다
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "길동무", modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
    }
}

/** 로고 그림만 (빛 번짐 없이) — 위의 이름표처럼 작게 쓸 때 */
@Composable
fun LogoGlyph(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = modifier.graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
}

/** 단추가 천천히 숨 쉬는 빛 (눌러 달라고 재촉하지 않게, 아주 느리게) */
@Composable
private fun Breathing(content: @Composable () -> Unit) {
    val clock = LocalClock.current
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(300.dp, 120.dp)) {
            val k = 0.5f + 0.5f * sin(clock.value * 1.6f)
            drawRoundRect(
                Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.18f + 0.14f * k), Color.Transparent), center, size.width / 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
        content()
    }
}

/** 화면 가득 천천히 떠도는 빛 알갱이 (환영 화면의 빛과 같은 결) */
@Composable
private fun Motes(modifier: Modifier) {
    val clock = LocalClock.current
    val seeds = remember { List(90) { k -> Triple((k * 0.6180339f) % 1f, (k * 0.7548777f + 0.13f) % 1f, (k * 0.5698403f) % 1f) } }
    Canvas(modifier) {
        val t = clock.value
        for ((x0, y0, ph) in seeds) {
            val x = (x0 * size.width + sin(t * 0.25f + ph * 9f) * 18.dp.toPx())
            val y = ((y0 - t * 0.012f * (0.4f + ph)) % 1f + 1f) % 1f * size.height
            val a = 0.10f + 0.20f * (0.5f + 0.5f * sin(t * 1.1f + ph * 13f))
            val r = (0.9f + ph * 1.6f).dp.toPx()
            drawCircle(Color(0xFFFFF1DC).copy(alpha = a), r, Offset(x, y), blendMode = BlendMode.Plus)
        }
    }
}
