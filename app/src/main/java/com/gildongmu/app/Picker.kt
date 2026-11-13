package com.gildongmu.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * 바탕 고르기 — 판 없이, 지금의 장면 위에서 바로 고른다.
 * 화면의 글이 비켜 선 자리에 고른 장면의 이름이 크게 서고, 그 아래에 장면들이 구슬로 늘어선다.
 * 고르는 즉시 화면 전체의 바탕이 그 장면으로 넘어가고, 해를 끌면 하늘빛이 그 시각의 빛으로 바로 바뀐다.
 * '그날의 장면'을 고르면 계절·시각·날씨·날짜를 따라 저절로 바뀐다.
 */
@Composable
fun ScenePicker(m: AppModel) {
    val shown by animateFloatAsState(if (Sheets.scenePicker) 1f else 0f, tween(560, easing = Ease.out), label = "picker")
    androidx.compose.runtime.SideEffect { Sheets.cover = shown }
    if (shown <= 0.01f) return
    val s = m.settings
    val now = remember(m.clockOverride, Sheets.scenePicker) { m.clock() }
    val part = Look.part(now)
    val auto = SceneKind.pick(Look.season(now), part, m.car.raining, now.get(Calendar.DAY_OF_YEAR))
    val picked = SceneKind.entries.firstOrNull { it.name == s.scene }
    val row = rememberLazyListState()
    LaunchedEffect(Sheets.scenePicker) {
        if (Sheets.scenePicker) { val i = SceneKind.entries.indexOfFirst { it.name == s.scene }; if (i >= 6) row.scrollToItem(i - 3) }
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) {
        // 글이 읽히도록 위에서부터 옅어지는 어스름 (가장자리가 없다). 바깥을 누르면 닫힌다.
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.50f), 0.55f to Color.Black.copy(alpha = 0.34f), 1f to Color.Black.copy(alpha = 0.16f)))
                .clickable(remember { MutableInteractionSource() }, null) { Sheets.scenePicker = false },
        )
        Column(Modifier.fillMaxSize().padding(top = 136.dp).graphicsLayer { translationY = (1f - shown) * -18.dp.toPx() }) {
            Row(Modifier.padding(horizontal = 64.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Kicker("바탕 고르기", color = C.Hi.copy(alpha = 0.94f), sp = 23)
                    Spacer(Modifier.height(20.dp))
                    RevealText(picked?.label ?: "그날의 장면", ts(76, C.Hi, FontWeight.SemiBold, family = Serif, lh = 1.2f).onScene(), delayMs = 80, durationMs = 760)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        picked?.line ?: "계절·시각·날씨를 따라 저절로 바뀌어요  ·  지금은 ${auto.label}",
                        style = ts(26, C.Hi.copy(alpha = 0.92f), family = Serif).onScene(0.6f),
                    )
                }
                Btn("닫기", glyph = G.Close, glass = true) { Sheets.scenePicker = false }
            }
            Spacer(Modifier.height(40.dp))
            CompositionLocalProvider(LocalScenePan provides null) {
                LazyRow(
                    state = row, contentPadding = PaddingValues(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(30.dp),
                    modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                        drawContent()
                        // 화면 끝에서는 스르르 사라진다
                        drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.035f to Color.Black, 0.965f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    },
                ) {
                    item(key = "auto") { SceneBead(auto, "그날의 장면", s.scene == "auto", part, true) { m.updateSettings(s.copy(scene = "auto")) } }
                    items(SceneKind.entries, key = { it.name }) { k ->
                        SceneBead(k, k.label, s.scene == k.name, part, false) { m.updateSettings(s.copy(scene = k.name)) }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.padding(horizontal = 64.dp), verticalAlignment = Alignment.Bottom) {
                // 하루의 길: 해를 끌어 시각을 고른다
                Column(Modifier.width(820.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시각", style = ts(22, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
                        Spacer(Modifier.width(16.dp))
                        Text(if (s.dayPart == "auto") "지금 시각을 따라가요  ·  ${part.label}" else "${part.label}에 머물러요", style = ts(20, C.Hi.copy(alpha = 0.80f)))
                    }
                    SunDial(m, part, Modifier.fillMaxWidth().height(200.dp))
                }
                Spacer(Modifier.width(40.dp))
                Column(Modifier.padding(bottom = 22.dp)) {
                    Btn("지금 시각으로", glyph = G.Today, kind = if (s.dayPart == "auto") BtnKind.Primary else BtnKind.Secondary, glass = true) { m.updateSettings(m.settings.copy(dayPart = "auto")) }
                    Spacer(Modifier.height(30.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("결", style = ts(22, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
                        Spacer(Modifier.width(16.dp))
                        Choice(Art.entries.map { it to it.label }, Skin.art) { m.setArt(it) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(Skin.art.line, style = ts(20, C.Hi.copy(alpha = 0.80f)))
                }
            }
        }
    }
}

/** 장면 하나를 들여다보는 구슬. 고른 구슬에는 빛의 테가 둘린다. */
@Composable
private fun SceneBead(scene: SceneKind, label: String, on: Boolean, part: DayPart, auto: Boolean, onClick: () -> Unit) {
    val lift by animateFloatAsState(if (on) 1f else 0f, tween(420, easing = Ease.out), label = "bead")
    Column(Modifier.width(164.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(150.dp).graphicsLayer { val k = 1f + 0.06f * lift; scaleX = k; scaleY = k }.clip(CircleShape)
                    .border(if (on) 2.5.dp else 1.2.dp, if (on) Brush.verticalGradient(listOf(Color.White, C.Accent)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.46f), Color.White.copy(alpha = 0.10f))), CircleShape),
            ) {
                Scenery(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }, part = part, seed = 5, still = true, scene = scene)
                if (auto) Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(34.dp).background(Color.Black.copy(alpha = 0.40f), CircleShape), contentAlignment = Alignment.Center) { Glyph(G.Spark, C.Accent, 18.dp, 1.8.dp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (on) { Lamp(C.Accent, size = 8.dp); Spacer(Modifier.width(4.dp)) }
            Text(label, style = ts(21, if (on) C.Hi else C.Hi.copy(alpha = 0.84f), if (on) FontWeight.SemiBold else FontWeight.Medium).onScene(0.6f))
        }
    }
}

/**
 * 하루의 길 — 새벽에 떠서 낮에 가장 높고, 노을에 지평선에 닿았다가 밤에는 그 아래로 내려가는 한 줄.
 * 그 위의 해를 끌면, 끄는 동안 화면의 하늘이 그 자리의 빛으로 바로 바뀐다. 손을 떼면 가장 가까운 시각에 머문다.
 */
@Composable
private fun SunDial(m: AppModel, part: DayPart, modifier: Modifier) {
    val target = part.ordinal.toFloat()
    val pos = remember { Animatable(target) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(target, dragging) { if (!dragging) pos.animateTo(target, spring(dampingRatio = 0.74f, stiffness = 170f)) }
    val scope = rememberCoroutineScope()
    val tm = rememberTextMeasurer(cacheSize = 16)
    val clock = LocalClock.current
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(1300, easing = Ease.out)) }
    Canvas(
        modifier.pointerInput(Unit) {
            val pad = 46.dp.toPx()
            fun u(x: Float) = ((x - pad) / (size.width - 2 * pad) * 3f).coerceIn(0f, 3f)
            awaitEachGesture {
                val down = awaitFirstDown()
                dragging = true
                var cur = u(down.position.x)
                Look.dayScrub = cur; scope.launch { pos.snapTo(cur) }
                drag(down.id) { c -> cur = u(c.position.x); Look.dayScrub = cur; scope.launch { pos.snapTo(cur) }; c.consume() }
                m.updateSettings(m.settings.copy(dayPart = DayPart.entries[cur.roundToInt().coerceIn(0, 3)].name))
                Look.dayScrub = null
                dragging = false
            }
        }
    ) {
        val pad = 46.dp.toPx()
        val w = size.width - 2 * pad
        val base = size.height * 0.56f
        val rise = base - 30.dp.toPx()
        fun at(u: Float): Offset {
            val h = if (u <= 2f) sin(u / 2f * PI).toFloat() else -0.40f * sin((u - 2f) * PI / 2).toFloat()
            return Offset(pad + w * u / 3f, base - h * rise)
        }
        val ink = C.Hi
        // 지평선
        drawLine(Brush.horizontalGradient(0f to Color.Transparent, 0.06f to ink.copy(alpha = 0.30f), 0.94f to ink.copy(alpha = 0.30f), 1f to Color.Transparent), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        // 해가 가는 길 (처음에 왼쪽에서부터 그어진다), 지나온 길은 밝게
        val path = Path().apply { moveTo(at(0f).x, at(0f).y); for (i in 1..72) { val q = at(i / 72f * 3f); lineTo(q.x, q.y) } }
        val pm = PathMeasure().apply { setPath(path, false) }
        val seg = Path(); pm.getSegment(0f, pm.length * draw.value, seg, true)
        drawPath(seg, ink.copy(alpha = 0.44f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        val k = (pos.value / 3f).coerceIn(0f, 1f)
        val lit = Path(); pm.getSegment(0f, pm.length * minOf(k, draw.value), lit, true)
        drawPath(lit, Brush.horizontalGradient(listOf(C.Accent.copy(alpha = 0.30f), C.Accent), pad, pad + w * k.coerceAtLeast(0.02f)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        // 네 시각의 자리와 이름
        DayPart.entries.forEachIndexed { i, dp ->
            val q = at(i.toFloat())
            val near = (1f - abs(pos.value - i)).coerceIn(0f, 1f)
            drawCircle(ink.copy(alpha = 0.50f + 0.50f * near), (3f + 1.4f * near).dp.toPx(), q)
            val lay = tm.measure(dp.label, TextStyle(fontFamily = Sans, fontWeight = if (near > 0.5f) FontWeight.SemiBold else FontWeight.Medium, fontSize = 21.sp, color = C.Hi))
            drawText(lay, topLeft = Offset(q.x - lay.size.width / 2f, size.height - lay.size.height - 2.dp.toPx()), alpha = (0.66f + 0.34f * near) * draw.value)
        }
        // 해 (지평선 아래로 내려가면 달빛으로 식는다)
        val p = at(pos.value)
        val night = ((pos.value - 2f) / 0.7f).coerceIn(0f, 1f)
        val tone = lerp(C.Accent, Color(0xFFCFE0FF), night)
        val breath = 0.90f + 0.10f * sin(clock.value * 1.6f)
        drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.60f * breath), Color.Transparent), p, 46.dp.toPx()), 46.dp.toPx(), p)
        drawCircle(Brush.radialGradient(listOf(Color.White, lerp(Color.White, tone, 0.55f)), p, 15.dp.toPx()), 15.dp.toPx(), p)
        drawCircle(Color.White.copy(alpha = 0.85f), 19.dp.toPx(), p, style = Stroke(1.3.dp.toPx()))
    }
}
