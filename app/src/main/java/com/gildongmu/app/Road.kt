package com.gildongmu.app

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 길 위 — 내비가 아니다. GPS로 내 차만 보여 주고, 다른 길동무가 남긴 한마디(별)·공유 일기(하트)·주의(세모)를
 * 길가의 작은 전광판으로 세워 둔다. 지나가면 도로 전광판처럼 그 한마디가 저절로 켜진다.
 * 주차 중에는 오른쪽 목록으로 둘러보고, 나도 한마디를 이어 남길 수 있다.
 * 지도: 실제 성수동·서울숲 일대 (© OpenStreetMap contributors). 이야기들은 시연용 예시.
 */

private val MapBg = Color(MapArt.LAND)
private val Halo = Shadow(MapBg, Offset.Zero, 9f)
private const val STORY_W = 548      // 고른 이야기가 놓이는 폭
private const val STORY_END = 150    // 그 오른쪽 여백 (가장자리의 눈금 줄이 놓일 자리)
private const val RULER_W = 104      // 눈금 줄을 누를 수 있는 폭
private const val VIEW_SHIFT = 400   // 주차 중에는 지도의 중심을 그만큼 왼쪽으로 둔다 (오른쪽에 이야기가 놓이므로)

private fun placeTone(p: Place): Color = when (p.kind) {
    PlaceKind.CAUTION -> C.Amber
    PlaceKind.DIARY -> if (p.id.startsWith("my_")) C.Accent else Color(0xFFFF7F9E)
    PlaceKind.VOICE -> if (p.id.startsWith("my_")) C.Accent else p.mood.tone().a
}

/** 고른 이야기의 표지(지도 위)와 그 글(오른쪽)을 잇는 선이 지날 자리 */
private class Link { var head = Offset.Unspecified; var cardX = Float.NaN; var cardY = Float.NaN; var tone = Color.White }

@Composable
fun RoadScreen(m: AppModel) = RoadBody(m)

@Composable
private fun RoadBody(m: AppModel) {
    val ctx = LocalContext.current
    val map = remember { GeoMap.get(ctx) }
    var base by remember { mutableStateOf(MapArt.ready) }
    LaunchedEffect(Unit) { if (base == null) base = withContext(Dispatchers.Default) { MapArt.base(map) } }
    val proj = remember { MapProjection() }
    val tm = rememberTextMeasurer(cacheSize = 128)
    val clock = LocalClock.current
    val scope = rememberCoroutineScope()
    val driving = m.driving
    val glass = LocalBackdrop.current?.glass
    val link = remember { Link() }

    // 내 차: GPS 가 0.5~1초마다 들어와도 부드럽게 미끄러지듯
    val here = m.here
    val carTarget = remember(here.lat, here.lng) { map.xy(here.lat, here.lng) }
    val carX = remember { Animatable(carTarget.x) }
    val carY = remember { Animatable(carTarget.y) }
    LaunchedEffect(carTarget) {
        val ms = if (m.demoDriving) 520 else 0
        launch { carX.animateTo(carTarget.x, tween(ms, easing = LinearEasing)) }
        launch { carY.animateTo(carTarget.y, tween(ms, easing = LinearEasing)) }
    }
    val bearing = m.gps?.bearing ?: 105f

    var filter by remember { mutableStateOf<PlaceKind?>(null) }
    val distOf = { p: Place -> val q = map.xy(p.lat, p.lng); hypot(q.x - carX.value, q.y - carY.value) }
    val shown = remember(m.places, filter, carTarget) { m.places.filter { filter == null || it.kind == filter }.sortedBy(distOf) }
    val rankOf = remember(shown) { shown.withIndex().associate { it.value.id to it.index } }

    // 지도는 늘 내 기준으로 돈다: 내가 가는 방향이 화면 위쪽이다 (북쪽 고정이 아니다).
    val head = remember { Animatable(bearing) }
    LaunchedEffect(bearing) {
        val d = ((bearing - head.value) % 360f + 540f) % 360f - 180f
        head.animateTo(head.value + d, tween(if (m.demoDriving) 700 else 900, easing = EaseInOutCubic))
    }
    /** 내 차에서 가는 방향으로 d 만큼 앞선 자리 (그 자리를 화면 가운데에 두면 차는 아래쪽에 놓인다) */
    fun ahead(x: Float, y: Float, d: Float): Offset {
        val r = Math.toRadians(bearing.toDouble())
        return Offset(x + sin(r).toFloat() * d, y - cos(r).toFloat() * d)
    }
    // 카메라: 처음엔 높이서 내려다보다가 내 차 쪽으로 내려앉는다. 주행 중에는 차를 따라간다.
    val camX = remember { Animatable(carTarget.x - 260f) }
    val camY = remember { Animatable(carTarget.y + 520f) }
    val zoom = remember { Animatable(0.5f) }
    val tilt = remember { Animatable(12f) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val a0 = ahead(carTarget.x, carTarget.y, 130f)
        launch { camX.animateTo(a0.x, tween(2600, easing = Ease.inOut)) }
        launch { camY.animateTo(a0.y, tween(2600, easing = Ease.inOut)) }
        launch { zoom.animateTo(1.0f, tween(2600, easing = Ease.inOut)) }
        launch { tilt.animateTo(52f, tween(3000, easing = Ease.inOut)) }
        delay(800)
        appear.animateTo(1f, tween(1600))
    }
    fun focus(x: Float, y: Float, z: Float, ms: Int = 1100) {
        scope.launch { camX.animateTo(x, tween(ms, easing = Ease.inOut)) }
        scope.launch { camY.animateTo(y, tween(ms, easing = Ease.inOut)) }
        scope.launch { zoom.animateTo(z, tween(ms, easing = Ease.inOut)) }
    }
    LaunchedEffect(driving) { if (driving) { scope.launch { tilt.animateTo(56f, tween(1400)) }; scope.launch { zoom.animateTo(1.2f, tween(1400)) } } }
    LaunchedEffect(carTarget, driving) {
        if (!driving) return@LaunchedEffect
        val a1 = ahead(carTarget.x, carTarget.y, 190f)
        launch { camX.animateTo(a1.x, tween(560, easing = LinearEasing)) }
        launch { camY.animateTo(a1.y, tween(560, easing = LinearEasing)) }
    }
    // 이야기를 고르면 그 자리로 다가가고, 표지와 글을 잇는 선이 그어진다
    val linkGrow = remember { Animatable(0f) }
    LaunchedEffect(m.selectedPlace) {
        linkGrow.snapTo(0f)
        val p = m.places.firstOrNull { it.id == m.selectedPlace } ?: return@LaunchedEffect
        if (driving) return@LaunchedEffect
        val t = map.xy(p.lat, p.lng)
        focus(t.x, t.y - 30f, maxOf(zoom.targetValue, 1.15f).coerceAtMost(1.5f))
        delay(520)
        linkGrow.animateTo(1f, tween(1100, easing = Ease.out))
    }
    LaunchedEffect(m.mapCmd) {
        when (val c = m.mapCmd) {
            null -> return@LaunchedEffect
            "home" -> { m.selectedPlace = null; ahead(carX.value, carY.value, 130f).let { focus(it.x, it.y, 1.0f) } }
            "in" -> focus(camX.value, camY.value, (zoom.value * 1.3f).coerceAtMost(1.8f))
            "out" -> focus(camX.value, camY.value, (zoom.value / 1.3f).coerceAtLeast(0.45f))
            else -> if (c.startsWith("filter:")) filter = runCatching { PlaceKind.valueOf(c.removePrefix("filter:")) }.getOrNull()
        }
        m.mapCmd = null
    }

    // 빛 조각이 튀는 순간들 (누를 때, 저절로 켜질 때)
    val bursts = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(m.popup?.placeId, m.popup?.at) { m.popup?.let { bursts[it.placeId] = System.currentTimeMillis() } }
    LaunchedEffect(m.selectedPlace) { m.selectedPlace?.let { bursts[it] = System.currentTimeMillis() } }

    val panel by animateFloatAsState(if (driving) 0f else 1f, tween(700, easing = Ease.inOut), label = "panel")
    val hits = remember { ArrayList<Pair<String, Offset>>() }
    val labelCache = remember { HashMap<String, TextLayoutResult>() }
    val bitmapPaint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val groundPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    Box(Modifier.fillMaxSize()) {
        // 지도: 위쪽으로 갈수록 하늘 속으로 스며든다 (그 뒤에는 지금의 장면이 그대로 흐르고 있다)
        Canvas(
            Modifier.fillMaxSize()
                .glassBackdrop(glass)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val hit = hits.minByOrNull { (it.second - pos).getDistance() }?.takeIf { (it.second - pos).getDistance() < 60f }
                        if (m.driving) hit?.let { m.openPopup(it.first) } else m.selectedPlace = hit?.first
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { c, pan, z, _ ->
                        if (m.driving) return@detectTransformGestures
                        val a = proj.ground(c.x - pan.x, c.y - pan.y)
                        val b = proj.ground(c.x, c.y)
                        scope.launch {
                            camX.snapTo((camX.value - (b.x - a.x)).coerceIn(-map.hx + 150f, map.hx - 150f))
                            camY.snapTo((camY.value - (b.y - a.y)).coerceIn(-map.hy + 150f, map.hy - 150f))
                            if (z != 1f) zoom.snapTo((zoom.value * z).coerceIn(0.45f, 1.8f))
                        }
                    }
                }
        ) {
            val t = clock.value
            val now = System.currentTimeMillis()
            val viewW = size.width - VIEW_SHIFT.dp.toPx() * panel
            val reading = m.selectedPlace != null && !driving
            val storyL = size.width - (STORY_END + STORY_W + 40).dp.toPx()      // 고른 이야기의 글이 놓이는 자리의 왼쪽 끝
            proj.update(camX.value, camY.value, zoom.value, tilt.value, head.value, viewW / 2f, size.height * 0.62f)
            drawRect(MapBg)

            // 1) 실제 지도 (도로·강·공원·건물)
            base?.let { bmp ->
                drawIntoCanvas { c ->
                    val mb = android.graphics.Matrix(proj.matrix)
                    mb.preTranslate(-map.hx, -map.hy)
                    mb.preScale(1f / MapArt.PX, 1f / MapArt.PX)
                    c.nativeCanvas.drawBitmap(bmp, mb, bitmapPaint)
                }
            }
            // 2) 땅 위: 내 위치에서 번져 나가는 고리 (GPS 범위)
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save(); n.concat(proj.matrix)
                groundPaint.style = android.graphics.Paint.Style.FILL
                groundPaint.color = C.Accent.copy(alpha = 0.07f).toArgb()
                n.drawCircle(carX.value, carY.value, 74f, groundPaint)
                groundPaint.style = android.graphics.Paint.Style.STROKE
                for (k in 0 until 3) {
                    val ph = ((if (driving) 0f else t * 0.22f) + k / 3f) % 1f
                    groundPaint.strokeWidth = 1.5f
                    groundPaint.color = C.Accent.copy(alpha = 0.50f * (1f - ph)).toArgb()
                    n.drawCircle(carX.value, carY.value, 26f + 96f * ph, groundPaint)
                }
                n.restore()
            }
            // 3) 이야기의 표지가 놓일 자리와 내 차의 자리를 먼저 잡는다 (지명이 그 위에 겹치지 않게)
            val fogTop = size.height * 0.17f
            val drawn = shown.map { p -> p to map.xy(p.lat, p.lng) }
                .map { (p, q) -> Triple(p, proj.screen(q.x, q.y), proj.localScale(q.x, q.y)) }
                .filter { (_, s, _) -> s[0] in -60f..(size.width + 40f) && s[1] in fogTop..(size.height + 60f) }
                .sortedBy { it.second[1] }
            val cs = proj.screen(carX.value, carY.value)
            val keepOut = ArrayList<Rect>()
            keepOut += Rect(cs[0] - 74f, cs[1] - 74f, cs[0] + 74f, cs[1] + 74f)
            drawn.forEach { (p, s, ls) ->
                val k = (ls / zoom.value).coerceIn(0.8f, 1.3f)
                keepOut += Rect(s[0] - 32f * k, s[1] - 100f * k, s[0] + 32f * k, s[1] + 16f)
                // 고른 이야기의 이름표가 놓일 자리
                if (p.id == m.selectedPlace || p.id == m.popup?.placeId) keepOut += Rect(s[0] - 190f, s[1] - 190f * k, s[0] + 190f, s[1] - 96f * k)
            }
            keepOut += Rect(0f, 0f, 470.dp.toPx(), 190.dp.toPx())                                             // 왼쪽 위: 지금 위치
            keepOut += Rect(0f, size.height - 150.dp.toPx(), 480.dp.toPx(), size.height)                       // 왼쪽 아래: 한마디 버튼
            keepOut += Rect(size.width * 0.28f, size.height - 150.dp.toPx(), size.width * 0.72f, size.height)  // 아래 가운데: 길잡이
            if (!driving) keepOut += Rect(0f, size.height * 0.30f, 150.dp.toPx(), size.height * 0.74f)                    // 왼쪽 가운데: 확대·축소
            if (panel > 0.5f) keepOut += Rect(size.width - 150.dp.toPx(), 0f, size.width, size.height)                      // 오른쪽 가장자리: 눈금 줄
            if (reading) keepOut += Rect(storyL, 170.dp.toPx(), size.width, size.height)                                    // 고른 이야기의 글
            drawMapLabels(map, proj, tm, labelCache, size.width, keepOut)
            // 가까이 붙어 선 표지들은 하나만 세우고(고른 것 먼저, 그다음 가까운 것), 나머지는 작은 빛으로 남긴다 (겹쳐서 읽을 수 없게 되지 않도록)
            val stood = ArrayList<Offset>()
            val minor = HashSet<String>()
            drawn.sortedBy { (p, _, _) -> if (p.id == m.selectedPlace || p.id == m.popup?.placeId) -1 else (rankOf[p.id] ?: 99) }.forEach { (p, s, ls) ->
                val o = Offset(s[0], s[1])
                val k = (ls / zoom.value).coerceIn(0.8f, 1.3f)
                if (stood.any { (it - o).getDistance() < 64f * k }) minor += p.id else stood += o
            }
            // 아래의 길잡이와 버튼이 놓인 자리에는 표지를 세우지 않는다 (반쯤 가려진 채 서 있지 않게)
            val dockTop = size.height - 196.dp.toPx()
            val btnR = 560.dp.toPx()
            val ramp = 50.dp.toPx()
            // 4) 길가에 세워 둔 이야기들
            hits.clear()
            link.head = Offset.Unspecified
            drawn.forEachIndexed { rank, (p, s, ls) ->
                val isSel = p.id == m.selectedPlace || p.id == m.popup?.placeId
                val grow = EaseOutBack.transform(((appear.value * 1.6f) - rank * 0.05f).coerceIn(0f, 1f))
                val jl = m.justLeft?.takeIf { it.placeId == p.id }
                val drop = if (jl != null) ((now - jl.at) / 1000f).coerceIn(0f, 1f) else 1f
                val sc = (ls / zoom.value).coerceIn(0.8f, 1.3f) * grow
                val clear = if (s[0] < btnR || (s[0] > size.width * 0.26f && s[0] < size.width * 0.74f)) ((dockTop - s[1]) / ramp).coerceIn(0f, 1f) else 1f
                val under = if (reading && !isSel && s[0] > storyL - 24f) 0.18f else 1f          // 글 밑에 깔리는 표지는 뒤로 물러난다
                val fade = ((s[1] - fogTop) / (size.height * 0.10f)).coerceIn(0f, 1f) * clear * under
                if (fade <= 0.01f) return@forEachIndexed
                if (p.id in minor && !isSel) {
                    // 작은 빛: 누르면 그 이야기가 앞으로 나선다
                    val c = Offset(s[0], s[1]); val tone = placeTone(p); val a = fade * grow.coerceIn(0f, 1f)
                    drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.36f * a), Color.Transparent), c, 16f * sc.coerceAtLeast(0.5f)), 16f * sc.coerceAtLeast(0.5f), c)
                    drawCircle(tone.copy(alpha = 0.94f * a), 3.6f * sc.coerceAtLeast(0.5f), c)
                    if (grow > 0.5f && under > 0.5f && clear > 0.5f) hits += p.id to c
                    return@forEachIndexed
                }
                val burstAge = bursts[p.id]?.let { (now - it) / 1000f } ?: -1f
                // 달리는 동안에는 표지가 일렁이거나 고리가 번지지 않는다 (눈길을 끌지 않게, 제자리에 가만히 서 있다)
                val hd = placeMarker(Offset(s[0], s[1]), p, placeTone(p), sc, if (driving) 0f else t, fade * grow.coerceIn(0f, 1f), isSel, if (driving) -1f else burstAge, drop, p.id in m.liked, (p.id.hashCode() and 0xFF))
                if (isSel && !driving) { nameTag(hd, p.name, placeTone(p), tm, labelCache, sc); if (p.id == m.selectedPlace) { link.head = hd; link.tone = placeTone(p) } }
                if (grow > 0.5f && fade > 0.5f) hits += p.id to hd
            }
            // 5) 내 차
            drawCar(Offset(cs[0], cs[1]), proj.screenDir(carX.value, carY.value, bearing - 90f), t, driving)
            // 6) 멀리는 하늘 속으로: 위로 갈수록 지도가 옅어지고, 그 경계에 지평선의 빛이 한 줄 걸린다
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Transparent, 0.36f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(0.08f to Color.Transparent, 0.22f to C.Accent.copy(alpha = 0.13f), 0.40f to Color.Transparent))
        }

        // 고른 이야기의 표지에서 그 글까지 이어지는 선 (그어지며 나타나고, 빛 한 점이 그 위를 오간다)
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val h = link.head
            if (m.selectedPlace == null || driving || h == Offset.Unspecified || link.cardY.isNaN() || link.cardX.isNaN() || linkGrow.value <= 0.01f || panel < 0.95f) return@Canvas
            if (m.listening.active && m.listenTarget != null) return@Canvas       // 한마디를 남기는 동안에는 글이 비켜 있으니, 선도 긋지 않는다
            val start = Offset(h.x + 34f, h.y)
            val end = Offset(link.cardX - 20.dp.toPx(), link.cardY.coerceIn(170.dp.toPx(), size.height - 190.dp.toPx()))
            if (end.x - start.x < 40f) return@Canvas
            val dx = (end.x - start.x) * 0.5f
            val path = Path().apply { moveTo(start.x, start.y); cubicTo(start.x + dx, start.y, end.x - dx, end.y, end.x, end.y) }
            val pm = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            pm.getSegment(0f, pm.length * linkGrow.value, part, true)
            drawPath(part, Brush.horizontalGradient(listOf(link.tone.copy(alpha = 0.95f), Color.White.copy(alpha = 0.75f)), start.x, end.x), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
            if (linkGrow.value > 0.98f) {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), end, 12.dp.toPx()), 12.dp.toPx(), end)
                drawCircle(Color.White, 3.4.dp.toPx(), end)
                val k = (t * 0.42f) % 1f
                val pos = pm.getPosition(pm.length * k)
                drawCircle(Brush.radialGradient(listOf(link.tone.copy(alpha = 0.9f * sin(k * PI).toFloat()), Color.Transparent), pos, 9.dp.toPx()), 9.dp.toPx(), pos)
                drawCircle(Color.White.copy(alpha = sin(k * PI).toFloat()), 2.dp.toPx(), pos)
            }
        }

        // 오른쪽: 지도가 어스름해지는 자리에 고른 이야기 하나 (판 없이 글만), 가장자리에는 이 근처 이야기들의 눈금 줄
        val sel = m.places.firstOrNull { it.id == m.selectedPlace }
        val lastSel = remember { mutableStateOf<Place?>(null) }
        if (sel != null) lastSel.value = sel
        if (sel == null) { link.cardY = Float.NaN; link.cardX = Float.NaN }
        val leaving = m.listening.active && m.listenTarget != null          // 한마디를 남기는 동안에는 글을 치워 자리를 내준다
        val story by animateFloatAsState(if (sel != null && !driving && !leaving) 1f else 0f, tween(520, easing = Ease.out), label = "story")
        if (panel > 0.01f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = story * panel; compositingStrategy = CompositingStrategy.Offscreen }) {
                val l = size.width - 1020.dp.toPx()
                val ink = Color(0xFF07061A)
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.40f to ink.copy(alpha = 0.50f), 1f to ink.copy(alpha = 0.80f), startX = l, endX = size.width),
                    topLeft = Offset(l, 0f), size = Size(size.width - l, size.height),
                )
                // 위쪽 하늘은 가리지 않는다
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.10f to Color.Transparent, 0.36f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
            }
            if (story > 0.01f) lastSel.value?.let { p ->
                androidx.compose.runtime.key(p.id) {
                    StoryBlock(
                        m, p, distOf(p), link,
                        Modifier.align(Alignment.CenterEnd).padding(end = STORY_END.dp, top = 30.dp).width(STORY_W.dp)
                            .graphicsLayer { alpha = story * panel; translationX = (1f - story) * 40.dp.toPx() },
                    )
                }
            }
            StoryRuler(
                m, shown, distOf,
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = 168.dp, bottom = 176.dp).width(RULER_W.dp).fillMaxHeight()
                    .graphicsLayer { alpha = panel; translationX = (1f - panel) * 140f },
            )
        }

        // 지도 위의 조작
        Box(Modifier.fillMaxSize()) {
            HereChip(m, Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 108.dp))
            if (!driving) Column(Modifier.align(Alignment.CenterStart).padding(start = 56.dp, top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 확대·축소·내 자리로: 유리 알약 하나에
                Column(Modifier.glass(dim = 0.24f), horizontalAlignment = Alignment.CenterHorizontally) {
                    MapKey(G.Plus) { m.mapCmd = "in" }
                    Box(Modifier.width(26.dp).height(1.dp).background(C.Hi.copy(alpha = 0.16f)))
                    MapKey(G.Minus) { m.mapCmd = "out" }
                    Box(Modifier.width(26.dp).height(1.dp).background(C.Hi.copy(alpha = 0.16f)))
                    MapKey(G.Locate) { m.mapCmd = "home" }
                }
                Spacer(Modifier.height(12.dp))
                NorthNeedle(head.value)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Btn("이 자리에 한마디", glyph = G.Pin, kind = BtnKind.Primary, big = true) { m.leaveWord(m.here) }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier.pressable { if (!m.navi.open()) m.showToast("내비를 열 수 없어요 (시연 환경)") }.glass(dim = 0.24f).heightIn(min = 78.dp).padding(horizontal = 26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.Navi, C.Hi, 22.dp, 1.8.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("내비", style = ts(19, C.Hi, FontWeight.Medium))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("© OpenStreetMap contributors", style = TextStyle(fontFamily = Sans, fontSize = 13.sp, color = C.Hi.copy(alpha = 0.46f)))
            }
            PopupSign(m, distOf, Modifier.align(Alignment.TopCenter).padding(top = 190.dp))
            val target = m.listenTarget
            ListenSheet(
                m, m.listening.active && target != null,
                title = "${target?.name ?: "이곳"}에 남길 한마디", doneTitle = "${target?.name ?: "이곳"}에 남겼어요",
                doneSub = "다른 길동무에게는 익명으로 보여요  ·  내 일기에도 담았어요",
                onRetry = { target?.let { m.leaveWord(it) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 206.dp),   // 아래의 말하기 구슬과 겹치지 않게
            )
        }
    }
}

@Composable
private fun MapKey(g: G, onClick: () -> Unit) {
    Box(Modifier.size(76.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) { Glyph(g, C.Hi, 26.dp, 1.9.dp) }
}

// ================================================================ 지도 위 그림

private val stationColor = mapOf(
    "성수역" to Color(0xFF3CB44A), "뚝섬역" to Color(0xFF3CB44A), "한양대역" to Color(0xFF3CB44A),
    "서울숲역" to Color(0xFFF5A200), "응봉역" to Color(0xFF77C4A3),
)

private fun labelStyle(kind: Int, text: String): TextStyle = when (kind) {
    0 -> TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 21.sp, color = Color(0xFFB4B1D8), letterSpacing = 0.16.em, shadow = Halo)
    1 -> TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Color(0xFFECE8F8), shadow = Halo)
    2 -> TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = Color(0xFF93CDB9), shadow = Halo)
    3 -> if (text.endsWith("강") || text.endsWith("천"))
        TextStyle(fontFamily = Myeongjo, fontWeight = FontWeight.Normal, fontSize = 24.sp, color = Color(0xFF7FA2E0), letterSpacing = 0.5.em, shadow = Halo)
    else TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Color(0xFF93B4EE), shadow = Halo)
    4 -> TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Color(0xFFC6C2E6), shadow = Halo)
    else -> TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, color = Color(0xFFA3A0C8), shadow = Halo)
}

private fun DrawScope.drawMapLabels(map: GeoMap, proj: MapProjection, tm: TextMeasurer, cache: HashMap<String, TextLayoutResult>, viewW: Float, keepOut: List<Rect>) {
    val placed = ArrayList<Rect>(keepOut)
    val top = size.height * 0.24f
    val order = map.labels.sortedBy { when (it.kind) { 3 -> 0; 1 -> 1; 0 -> 2; 2 -> 3; 4 -> 4; else -> 5 } }
    for (l in order) {
        if (l.kind >= 5) continue
        val s = proj.screen(l.x, l.y)
        if (s[0] < 30f || s[0] > viewW - 30f || s[1] < top || s[1] > size.height - 20f) continue
        val lay = cache.getOrPut("${l.kind}|${l.text}") { tm.measure(l.text, labelStyle(l.kind, l.text)) }
        val w = lay.size.width.toFloat(); val h = lay.size.height.toFloat()
        val rect = Rect(s[0] - w / 2 - 14f, s[1] - h / 2 - 8f, s[0] + w / 2 + 14f, s[1] + h / 2 + 8f)
        // 화면 밖으로 잘리는 글자, 다른 것과 겹치는 글자는 그리지 않는다
        if (rect.left < 8f || rect.right > viewW - 8f) continue
        if (placed.any { it.overlaps(rect) }) continue
        placed += rect
        val fade = ((s[1] - top) / (size.height * 0.10f)).coerceIn(0f, 1f)
        val c = Offset(s[0], s[1])
        if (l.kind == 1) {
            val dot = stationColor[l.text] ?: Color(0xFF9A9892)
            drawCircle(MapBg, 9f, Offset(c.x - w / 2 - 14f, c.y), alpha = fade)
            drawCircle(dot, 6f, Offset(c.x - w / 2 - 14f, c.y), alpha = fade)
        }
        drawText(lay, topLeft = Offset(c.x - w / 2, c.y - h / 2), alpha = fade * if (l.kind == 4) 0.86f else 1f)
    }
}

private fun DrawScope.nameTag(head: Offset, name: String, tone: Color, tm: TextMeasurer, cache: HashMap<String, TextLayoutResult>, s: Float) {
    val lay = cache.getOrPut("sel|$name") { tm.measure(name, TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = C.Hi)) }
    val w = lay.size.width + 30f; val h = lay.size.height + 14f
    val tl = Offset(head.x - w / 2, head.y - 40f * s - h)
    drawRoundRect(Color(0xF216142C), tl, Size(w, h), CornerRadius(h / 2f))
    drawRoundRect(Brush.verticalGradient(listOf(tone.copy(alpha = 0.85f), tone.copy(alpha = 0.20f)), tl.y, tl.y + h), tl, Size(w, h), CornerRadius(h / 2f), style = Stroke(1.2f))
    drawText(lay, topLeft = Offset(tl.x + 15f, tl.y + 7f))
}

/** 내 차: 빛나는 알 하나와 가는 방향을 가리키는 화살. 둘레의 가는 고리 위로 점 넷이 천천히 돌고, 주행 중에는 앞으로 빛이 흘러간다. */
private fun DrawScope.drawCar(c: Offset, angle: Float, t: Float, driving: Boolean) {
    val tone = C.Accent
    drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.42f), Color.Transparent), c, 62f), 62f, c)
    drawCircle(tone.copy(alpha = 0.34f), 36f, c, style = Stroke(1.2f))
    for (k in 0 until 4) {
        val ang = k / 4f * 2 * PI + (if (driving) 0.0 else t * 0.5)
        drawCircle(Color.White, 2.6f, Offset(c.x + cos(ang).toFloat() * 36f, c.y + sin(ang).toFloat() * 36f))
    }
    drawCircle(Color(0xFF14122A), 23f, c)
    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), tone), c.y - 23f, c.y + 23f), 23f, c, style = Stroke(2.2f))
    rotate(angle + 90f, c) {
        drawPath(Path().apply { moveTo(c.x, c.y - 12.5f); lineTo(c.x + 9.5f, c.y + 10f); lineTo(c.x, c.y + 4.5f); lineTo(c.x - 9.5f, c.y + 10f); close() }, Color.White)
    }
}

// ================================================================ 지나는 길의 전광판

@Composable
private fun PopupSign(m: AppModel, distOf: (Place) -> Float, modifier: Modifier) {
    val pop = m.popup
    val last = remember { mutableStateOf<Popup?>(null) }
    if (pop != null) last.value = pop
    // 서 있을 때는 따로 띄우지 않고 그 이야기를 고른다 (오른쪽에 글로 놓인다). 달리는 동안에만 이 한 줄이 뜬다.
    LaunchedEffect(pop?.placeId, pop?.at) { if (pop != null && !m.driving) m.selectedPlace = pop.placeId }
    AnimatedVisibility(
        pop != null && m.driving, modifier = modifier,
        enter = fadeIn(tween(320)) + slideInVertically(tween(460, easing = EaseOutCubic)) { -it / 3 },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(320)) { -it / 4 },
    ) {
        val pp = last.value ?: return@AnimatedVisibility
        val p = m.places.firstOrNull { it.id == pp.placeId } ?: return@AnimatedVisibility
        val tone = placeTone(p)
        val clock = LocalClock.current
        // 달리는 동안: 판 없이, 어디의 무슨 이야기인지 한 줄만 크게. 내용은 목소리로 읽어 주고, 글은 차를 세운 뒤에 볼 수 있다.
        Column(
            Modifier.width(1120.dp).drawBehind {
                // 글이 읽히도록 깔아 주는 어스름 (가장자리 없이 사라지는 눌린 동그라미)
                val rw = size.width * 0.62f
                val c = Offset(size.width / 2f, size.height * 0.46f)
                scale(1f, size.height * 0.95f / rw, pivot = c) {
                    drawCircle(Brush.radialGradient(0f to Color(0xFF07061A).copy(alpha = 0.66f), 0.55f to Color(0xFF07061A).copy(alpha = 0.40f), 1f to Color.Transparent, center = c, radius = rw), radius = rw, center = c)
                }
            }.padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lamp(tone, size = 11.dp)
                Spacer(Modifier.width(8.dp))
                Text(when { p.kind == PlaceKind.CAUTION -> "곧 지나는 곳 · 주의"; p.diary != null -> "지나는 길에 남겨진 일기"; else -> "지나는 길에 남겨진 한마디" }, style = ts(23, C.Hi.copy(alpha = 0.94f), FontWeight.Medium).onScene(0.6f))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = ts(48, C.Hi, FontWeight.SemiBold).onScene(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(24.dp))
                Num(Places.distance(distOf(p)), 48, C.Hi, weight = FontWeight.Normal, shadow = true)
            }
            // 신호에 멈춰 서 있는 동안에만: 그 한마디의 글이 함께 보인다. 다시 움직이면 글은 물러나고, 한 줄과 목소리만 남는다.
            val standing = m.standing
            AnimatedVisibility(
                standing,
                enter = fadeIn(tween(700, delayMillis = 250)) + expandVertically(tween(520, easing = EaseOutCubic)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(420)),
            ) {
                val d = p.diary
                val body = d?.title ?: p.lead?.text ?: ""
                val who = d?.author ?: p.lead?.author ?: ""
                val time = d?.time ?: p.lead?.time ?: System.currentTimeMillis()
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 20.dp)) {
                    Text(
                        "“$body”".keepWords(),
                        style = ts(if (body.length > 40) 40 else 46, C.Hi, family = Serif, lh = 1.42f).copy(letterSpacing = (-0.01).em, lineBreak = LineBreak.Heading).onScene(),
                        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("$who  ·  ${Places.ago(time, System.currentTimeMillis())}", style = ts(21, C.Hi.copy(alpha = 0.86f)).onScene(0.6f))
                }
            }
            Spacer(Modifier.height(16.dp))
            // 남은 시간: 가는 빛 한 줄이 줄어든다 (서 있는 동안에는 닫히지 않으므로 줄어들지 않는다)
            Canvas(Modifier.width(420.dp).height(8.dp)) {
                val total = if (pp.auto) 7000f else 9000f
                val k = if (standing) 1f else (1f - (System.currentTimeMillis() - pp.at) / total).coerceIn(0f, 1f) + clock.value * 0f
                val y = size.height / 2
                drawLine(C.Hi.copy(alpha = 0.20f), Offset(0f, y), Offset(size.width, y), 1.4.dp.toPx(), StrokeCap.Round)
                drawLine(tone, Offset(size.width * (0.5f - 0.5f * k), y), Offset(size.width * (0.5f + 0.5f * k), y), 2.dp.toPx(), StrokeCap.Round)
            }
            Spacer(Modifier.height(18.dp))
            Btn("닫기", glyph = G.Close, big = true, glass = true) { m.closePopup() }
        }
    }
}

// ================================================================ 지도 위 표지

@Composable
private fun MapToast(m: AppModel, modifier: Modifier) {
    val last = remember { mutableStateOf("") }
    m.toast?.let { last.value = it }
    AnimatedVisibility(m.toast != null, modifier = modifier, enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 2 }, exit = fadeOut(tween(260))) {
        Row(Modifier.glass(dim = 0.34f).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Lamp(C.Green, size = 9.dp)
            Spacer(Modifier.width(8.dp))
            Text(last.value, style = ts(17, C.Hi, FontWeight.Medium))
        }
    }
}

@Composable
private fun HereChip(m: AppModel, modifier: Modifier) {
    val driving = m.driving
    Row(modifier.glass(dim = 0.26f).padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Lamp(if (driving) C.Green else C.Accent, size = 10.dp, blink = driving)
        Spacer(Modifier.width(10.dp))
        Column {
            Tag(if (driving) "GPS · 주행 중" else "GPS · 주차 중", if (driving) C.Green else C.Low)
            Text(m.here.name, style = ts(18, C.Hi, FontWeight.Medium))
        }
        if (driving) {
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(36.dp).background(C.Line))
            Spacer(Modifier.width(18.dp))
            Text("지나가면 한마디가\n저절로 켜져요", style = ts(13, C.Mid, lh = 1.35f))
        }
    }
}

// ================================================================ 오른쪽: 고른 이야기 하나와 눈금 줄 (주차 중에만)

/**
 * 고른 자리의 이야기 한 편. 판 없이, 지도가 어스름해지는 자리에 글만 놓인다.
 * 그 자리에 이어 남긴 한마디들은 목록으로 펼치지 않고, 한 번에 하나씩 넘겨 본다 (글을 누르면 다음 목소리).
 */
@Composable
private fun StoryBlock(m: AppModel, p: Place, meters: Float, link: Link, modifier: Modifier) {
    val tone = placeTone(p)
    val now = System.currentTimeMillis()
    val d = p.diary
    val voices = if (d != null) p.words else p.words.drop(1)
    val pages = 1 + voices.size
    var page by remember(p.id) { mutableIntStateOf(0) }
    val plus = if (p.id in m.liked) 1 else 0
    val headY = with(LocalDensity.current) { 15.dp.toPx() }
    Column(modifier.onGloballyPositioned { val o = it.positionInRoot(); link.cardX = o.x; link.cardY = o.y + headY }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(0, 60, durationMs = 520)) {
            Lamp(tone, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Text(p.kind.label, style = ts(20, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
            Spacer(Modifier.width(14.dp))
            Num(Places.distance(meters), 24, C.Hi.copy(alpha = 0.92f), weight = FontWeight.Normal)
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, style = ts(27, C.Hi, FontWeight.SemiBold).onScene(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.rise(0, 120, durationMs = 520))
        Spacer(Modifier.height(22.dp))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                ((fadeIn(tween(360, delayMillis = 140)) + slideInVertically(tween(480, easing = Ease.out)) { it / 12 }) togetherWith fadeOut(tween(140)))
                    .using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "voice",
        ) { pg ->
            Column(Modifier.fillMaxWidth().pressable(enabled = pages > 1) { page = (page + 1) % pages }) {
                if (pg == 0 && d != null) {
                    Text(d.title, style = ts(32, C.Hi, FontWeight.SemiBold, family = Serif, lh = 1.35f).onScene(0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text(d.body.keepWords(), style = ts(23, C.Hi.copy(alpha = 0.92f), family = Serif, lh = 1.62f).onScene(0.5f), maxLines = 7, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Text("${d.author}님의 일기  ·  ${Places.ago(d.time, now)}  ·  공감 ${d.likes + plus}", style = ts(19, C.Hi.copy(alpha = 0.80f)), modifier = Modifier.rise(0, 420, durationMs = 420))
                } else {
                    val w = if (pg == 0) p.lead else voices.getOrNull(pg - 1)
                    if (w != null) {
                        RevealText("“${w.text}”".keepWords(), ts(if (w.text.length > 44) 32 else 38, C.Hi, family = Serif, lh = 1.46f).onScene(0.5f), delayMs = 160, stagger = 90, durationMs = 760, maxLines = 6)
                        Spacer(Modifier.height(16.dp))
                        val tail = when {
                            pg == 0 && p.kind == PlaceKind.CAUTION -> "${p.words.size}명이 확인"
                            pg == 0 -> "공감 ${w.likes + plus}"
                            w.mine -> "내가 이어 남긴 말"
                            else -> "이어 남긴 한마디"
                        }
                        Text("${w.author}  ·  ${Places.ago(w.time, now)}  ·  $tail", style = ts(19, C.Hi.copy(alpha = 0.80f)), modifier = Modifier.rise(0, 520, durationMs = 420))
                    }
                }
            }
        }
        if (pages > 1) {
            // 이 자리에 쌓인 목소리 가운데 몇 번째인지: 지금 것은 길쭉한 빛, 나머지는 점. 누르면 다음 목소리로 넘어간다.
            Row(Modifier.heightIn(min = 76.dp).pressable { page = (page + 1) % pages }, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(pages) { i -> Box(Modifier.size(if (i == page) 24.dp else 8.dp, 8.dp).background(if (i == page) C.Accent else C.Hi.copy(alpha = 0.46f), CircleShape)) }
                }
                Spacer(Modifier.width(16.dp))
                Text(if (page == 0) "이어 남긴 한마디 ${voices.size}" else "${page + 1} / $pages", style = ts(18, C.Hi.copy(alpha = 0.86f), FontWeight.Medium))
                Spacer(Modifier.width(8.dp))
                Glyph(G.Arrow, C.Hi.copy(alpha = 0.70f), 20.dp, 1.8.dp)
            }
        } else Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(0, 260, durationMs = 520)) {
            Btn("이어서 한마디", glyph = G.Ask, kind = BtnKind.Primary) { m.leaveWord(PlaceTarget(p.id, p.name, p.lat, p.lng)) }
            Btn("듣기", glyph = G.Speaker) {
                val w = if (page == 0) null else voices.getOrNull(page - 1)
                m.voice.say(if (w != null) "${w.author}님이 이어 남긴 한마디. ${w.text}" else speakText(p))
            }
            HeartButton(p.id in m.liked, null) { m.like(p.id) }
        }
    }
}

/**
 * 이 근처의 이야기들을 가까운 순서로 꿴 눈금 줄. 아래가 가깝고 위가 멀다 (지도와 같은 방향).
 * 누르거나 위아래로 쓸면 이야기가 넘어가고, 지도가 그 자리로 따라간다.
 */
@Composable
private fun StoryRuler(m: AppModel, shown: List<Place>, distOf: (Place) -> Float, modifier: Modifier) {
    val n = shown.size
    val selIdx = shown.indexOfFirst { it.id == m.selectedPlace }
    val glow by animateFloatAsState(if (selIdx >= 0) 1f else 0f, tween(420), label = "rulerSel")
    val pos = remember { Animatable(0f) }       // 고른 눈금의 자리 (눈금 사이를 미끄러진다)
    LaunchedEffect(selIdx) { if (selIdx >= 0) pos.animateTo(selIdx.toFloat(), spring(dampingRatio = 0.80f, stiffness = 300f)) }
    val tm = rememberTextMeasurer(cacheSize = 32)
    val current = rememberUpdatedState(shown)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(900); appear.animateTo(1f, tween(1400, easing = Ease.out)) }
    Canvas(
        modifier.pointerInput(Unit) {
            fun pick(y: Float) {
                val list = current.value
                if (list.isEmpty()) return
                val top = 44.dp.toPx(); val bottom = size.height - 44.dp.toPx()
                val step = if (list.size > 1) (bottom - top) / (list.size - 1) else 0f
                val i = if (step <= 0f) 0 else ((bottom - y) / step).roundToInt().coerceIn(0, list.lastIndex)
                if (m.selectedPlace != list[i].id) m.selectedPlace = list[i].id
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                pick(down.position.y)
                drag(down.id) { c -> pick(c.position.y); c.consume() }
            }
        }
    ) {
        if (n == 0) return@Canvas
        val x = size.width - 38.dp.toPx()
        val top = 44.dp.toPx(); val bottom = size.height - 44.dp.toPx()
        val step = if (n > 1) (bottom - top) / (n - 1) else 0f
        val a = appear.value
        // 줄: 아래에서 위로 그어지며 나타난다
        val lineTop = bottom + 26.dp.toPx() - (bottom - top + 52.dp.toPx()) * a
        drawLine(
            Brush.verticalGradient(0f to Color.Transparent, 0.10f to C.Hi.copy(alpha = 0.34f), 0.90f to C.Hi.copy(alpha = 0.34f), 1f to Color.Transparent, startY = top - 26.dp.toPx(), endY = bottom + 26.dp.toPx()),
            Offset(x, lineTop), Offset(x, bottom + 26.dp.toPx()), 1.dp.toPx(),
        )
        shown.forEachIndexed { i, p ->
            val y = bottom - i * step
            val born = ((a * (n + 6) - i) / 6f).coerceIn(0f, 1f)
            if (born <= 0f) return@forEachIndexed
            val near = (1f - abs(pos.value - i)).coerceIn(0f, 1f) * glow
            val tone = placeTone(p)
            val c = Offset(x, y)
            if (near > 0.01f) drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.60f * near), Color.Transparent), c, 26.dp.toPx()), 26.dp.toPx(), c)
            val quiet = if (p.kind == PlaceKind.CAUTION) C.Amber.copy(alpha = 0.90f) else C.Hi.copy(alpha = 0.70f)
            drawCircle(lerp(quiet, tone, near).copy(alpha = born), (3.4f + 4.6f * near).dp.toPx(), c)
            if (near > 0.5f) drawCircle(Color.White.copy(alpha = near), (11f * near).dp.toPx(), c, style = Stroke(1.3.dp.toPx()))
        }
        // 눈금의 뜻: 고른 이야기까지의 거리. 아무것도 고르지 않았을 때는 가장 가까운 곳과 가장 먼 곳의 거리.
        fun label(i: Int, alpha: Float, bold: Boolean) {
            if (alpha <= 0.02f || i !in shown.indices) return
            val lay = tm.measure(Places.distance(distOf(shown[i])), TextStyle(fontFamily = Sans, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, fontSize = if (bold) 20.sp else 18.sp, color = C.Hi, fontFeatureSettings = "tnum"))
            drawText(lay, topLeft = Offset(x - 26.dp.toPx() - lay.size.width, bottom - i * step - lay.size.height / 2f), alpha = alpha)
        }
        if (selIdx >= 0) label(selIdx, glow * a, true)
        label(0, (1f - glow) * 0.80f * a, false)
        if (n > 1) label(n - 1, (1f - glow) * 0.80f * a, false)
        // 이 줄이 무엇인지: 맨 위에 한 줄
        val cap = tm.measure("이 근처의 이야기 $n", TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = C.Hi, letterSpacing = 0.02.em))
        drawText(cap, topLeft = Offset(x + 8.dp.toPx() - cap.size.width, top - 58.dp.toPx() - cap.size.height / 2f), alpha = 0.86f * a)
    }
}

private fun speakText(p: Place): String {
    val d = p.diary
    if (d != null) return "${p.name}에서 ${d.author}님이 쓴 일기, ${d.title}. ${d.body}"
    val w = p.lead ?: return p.name
    return if (p.kind == PlaceKind.CAUTION) "${p.name}, 주의 알림이에요. ${w.text}" else "${p.name}에서 ${w.author}님이 남긴 한마디. ${w.text}"
}


/** 북쪽이 어느 쪽인지 알려 주는 가는 바늘 (지도는 내 방향을 따라 돌기 때문에). 빛이 맺힌 끝이 북쪽이다. */
@Composable
private fun NorthNeedle(heading: Float) {
    Box(Modifier.size(76.dp).glass(dim = 0.24f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            rotate(-heading, center) {
                val r = size.minDimension / 2f
                val tip = Offset(center.x, center.y - r * 0.54f)
                val tail = Offset(center.x, center.y + r * 0.54f)
                val w = r * 0.19f
                val north = Path().apply { moveTo(tip.x, tip.y); lineTo(center.x + w, center.y); lineTo(center.x - w, center.y); close() }
                val whole = Path().apply { moveTo(tip.x, tip.y); lineTo(center.x + w, center.y); lineTo(tail.x, tail.y); lineTo(center.x - w, center.y); close() }
                drawPath(north, Brush.verticalGradient(listOf(C.Accent.copy(alpha = 0.90f), C.Accent.copy(alpha = 0.25f)), tip.y, center.y))
                drawPath(whole, C.Hi.copy(alpha = 0.72f), style = Stroke(1.5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.70f), Color.Transparent), tip, r * 0.30f), r * 0.30f, tip)
                drawCircle(Color.White, r * 0.06f, tip)
            }
        }
    }
}
