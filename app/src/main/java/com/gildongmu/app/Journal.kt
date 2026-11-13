package com.gildongmu.app

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

/** 일기장 안에서 '날마다'와 '이번 달'을 오간다 */
@Composable
private fun BookTabs(m: AppModel, modifier: Modifier = Modifier) {
    Choice(listOf(Screen.Book to "날마다", Screen.Month to "이번 달"), if (m.screen == Screen.Month) Screen.Month else Screen.Book, modifier) { m.screen = it }
}

/** 그날의 풍경: 마지막으로 마음을 남긴 순간에 창밖에 걸려 있던 풍경과 빛. 남아 있지 않으면 그때의 계절·시각·날씨·날짜로 정한다 */
private fun sceneOf(list: List<Entry>): SceneKind = list.last().let { e ->
    e.scene.view?.let { v -> runCatching { SceneKind.valueOf(v) }.getOrNull() } ?: Look.sceneOf(cal(e.time), e.scene.rain)
}
private fun partOf(list: List<Entry>): DayPart = list.last().let { e ->
    e.scene.light?.let { v -> runCatching { DayPart.valueOf(v) }.getOrNull() } ?: Look.partOf(cal(e.time))
}

/**
 * 그날의 창밖 — 말로 쓰는 그림일기의 '그림'.
 * 날마다 다른 곳, 다른 빛의 풍경이 남는다.
 */
@Composable
fun DayScene(list: List<Entry>, modifier: Modifier = Modifier, still: Boolean = true) {
    val c = cal(list.last().time)
    // 멈춘 작은 그림은 한 번만 그려 두고 다시 쓴다
    Scenery(
        if (still) modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen } else modifier,
        part = partOf(list), rain = list.last().scene.rain, speed = 0f,
        seed = c.get(Calendar.DAY_OF_YEAR) % 23 + 1, still = still, scene = sceneOf(list),
    )
}

// ================================================================ 일기장: 하루마다 글귀 한 장

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Book(m: AppModel) {
    val days = m.entries.groupBy { Store.dayKey(it.time) }.toSortedMap(compareByDescending { it }).entries.toList()
    Box(Modifier.fillMaxSize()) {
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().background(veil(DayPart.DAY, 0f to 0.30f, 1f to 0.50f)))
            Text(
                "아직 펼칠 페이지가 없어요.\n오늘, 첫 한마디를 남겨 볼까요?", style = ts(30, C.Hi, family = Serif, lh = 1.7f).onScene(), textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            BookHeader(m, 0, 0)
            return@Box
        }
        val pager = rememberPagerState { days.size }
        val scope = rememberCoroutineScope()
        val page = pager.currentPage.coerceIn(0, days.lastIndex)
        val cur = days[page].value
        // 펼친 날의 빛깔을 화면 가득 건다
        val scene = sceneOf(cur)
        val part = partOf(cur)
        val rain = cur.last().scene.rain
        SideEffect { if (m.screen == Screen.Book) Stage.hang(scene, part, rain) }

        // 글이 놓이는 왼쪽과 아래에 어스름
        Box(Modifier.fillMaxSize().background(veilH(0f to 0.44f, 0.46f to 0.14f, 0.80f to 0f)))
        Box(Modifier.fillMaxSize().background(veil(part, 0f to 0.26f, 0.20f to 0f, 0.52f to 0f, 1f to 0.56f)))

        HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
            val (key, list) = days[p]
            DayPage(
                m, key, list, m.current?.let { m.store.closedAt(it.id, key) } != null,
                Modifier.graphicsLayer {
                    val off = abs((pager.currentPage - p) + pager.currentPageOffsetFraction).coerceAtMost(1f)
                    alpha = 1f - 0.85f * off
                },
            )
        }
        BookHeader(m, days.size, m.entries.size)
        DayRibbon(days, page, Modifier.align(Alignment.BottomCenter).padding(bottom = 188.dp)) { scope.launch { pager.animateScrollToPage(it) } }
    }
}

/** 일기장 위쪽: 어디에 있는지, 얼마나 쌓였는지, 날마다·이번 달 */
@Composable
private fun BookHeader(m: AppModel, days: Int, hearts: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, top = 116.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("일기장", style = ts(22, C.Hi.copy(alpha = 0.90f), FontWeight.Medium).onScene(0.6f))
        Spacer(Modifier.weight(1f))
        if (days > 0) {
            Num("%02d".format(days), 30, C.Accent, shadow = true)
            Spacer(Modifier.width(8.dp))
            Text("일의 기록", style = ts(15, C.Hi.copy(alpha = 0.78f), FontWeight.Medium).onScene(0.6f))
            Spacer(Modifier.width(22.dp))
            Num("%02d".format(hearts), 30, C.Hi, shadow = true)
            Spacer(Modifier.width(8.dp))
            Text("개의 마음", style = ts(15, C.Hi.copy(alpha = 0.78f), FontWeight.Medium).onScene(0.6f))
            Spacer(Modifier.width(26.dp))
        }
        BookTabs(m)
    }
}

/**
 * 하루 한 장: 그날의 장면 위에, 그날 남긴 말 한 줄이 글귀처럼 크게 놓인다.
 * 글은 모두 잔잔한 위쪽에 두고, 아래쪽은 그날의 장면이 그대로 보이게 비워 둔다.
 */
@Composable
private fun DayPage(m: AppModel, key: String, list: List<Entry>, closed: Boolean, modifier: Modifier) {
    val c = cal(list.first().time)
    val today = key == Store.dayKey(m.clock().timeInMillis)
    val ink = C.Hi
    val hero = list.last()
    Box(modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 168.dp, bottom = 296.dp)) {
        Column(Modifier.width(1240.dp)) {
            // 날짜
            Row(verticalAlignment = Alignment.Bottom) {
                Num("%d".format(c.get(Calendar.DAY_OF_MONTH)), 112, ink, Modifier.padding(end = 18.dp), shadow = true)
                Column(Modifier.padding(bottom = 18.dp)) {
                    Text(weekdayName(c), style = ts(24, ink, FontWeight.SemiBold).onScene(0.6f))
                    Text("${c.get(Calendar.YEAR)}년 ${c.get(Calendar.MONTH) + 1}월", style = ts(19, ink.copy(alpha = 0.90f), FontWeight.Medium).onScene(0.6f))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (today) { Lamp(C.Accent, size = 8.dp, blink = true); Spacer(Modifier.width(4.dp)); Text("오늘", style = ts(18, ink, FontWeight.Medium).onScene(0.6f)); Spacer(Modifier.width(12.dp)) }
                        else if (closed) { Lamp(C.Green, size = 8.dp); Spacer(Modifier.width(4.dp)); Text("마무리한 하루", style = ts(18, ink.copy(alpha = 0.9f), FontWeight.Medium).onScene(0.6f)); Spacer(Modifier.width(12.dp)) }
                        Text(sceneOf(list).label, style = ts(18, ink.copy(alpha = 0.84f), FontWeight.Medium).onScene(0.6f))
                    }
                }
            }
            Spacer(Modifier.height(34.dp))
            // 그날의 글귀
            Column(verticalArrangement = Arrangement.spacedBy((-24).dp)) {
                Text("“", style = ts(110, C.Accent.copy(alpha = 0.92f), family = Serif, lh = 0.56f))
                RevealText(
                    Words.headline(hero.text).keepWords(), ts(if (Words.headline(hero.text).length > 40) 50 else 58, ink, family = Serif, lh = 1.40f).copy(letterSpacing = (-0.012).em, lineBreak = LineBreak.Heading).onScene(0.5f),
                    Modifier.width(1010.dp), delayMs = 200, maxLines = 3,      // (오른쪽 위의 호와 겹치지 않는 폭)
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Num(hm24(cal(hero.time)), 24, ink.copy(alpha = 0.90f), weight = FontWeight.Normal, shadow = true)
                Spacer(Modifier.width(14.dp))
                MoodDot(hero.mood)
                Spacer(Modifier.width(18.dp))
                Text(Words.dayTitle(c, list).substringAfter(", "), style = ts(22, ink.copy(alpha = 0.86f), family = Serif).onScene(0.6f))
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Btn("하루 펼쳐 보기", glyph = G.Arrow, kind = BtnKind.Primary) { m.dayKey = key; m.screen = Screen.Day }
                if (list.size > 1) { Spacer(Modifier.width(18.dp)); Text("이날 남긴 말 ${list.size}개", style = ts(19, ink.copy(alpha = 0.86f), FontWeight.Medium).onScene(0.6f)) }
            }
        }
        // 오른쪽 위: 그날의 마음들과 하루의 호
        Column(Modifier.align(Alignment.TopEnd).padding(top = 96.dp).width(470.dp), horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.map { it.mood }.distinct().take(5).forEachIndexed { i, md -> MoodOrb(md, 62.dp, phase = i * 1.3f) }
            }
            Spacer(Modifier.height(4.dp))
            DayArc(list, Modifier.fillMaxWidth(), labels = false)
        }
    }
}

/** 날들의 띠: 날마다의 빛깔이 동그랗게 줄지어 있다 */
@Composable
private fun DayRibbon(days: List<Map.Entry<String, List<Entry>>>, current: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(current) { state.animateScrollToItem((current - 4).coerceAtLeast(0)) }
    LazyRow(
        modifier.fillMaxWidth().height(136.dp), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(days, key = { _, d -> d.key }) { i, d ->
            val on = i == current
            val size by animateDpAsState(if (on) 98.dp else 78.dp, tween(320, easing = EaseOutCubic), label = "ribbon")
            val mo = cal(d.value.first().time).get(Calendar.MONTH)
            val head = i == 0 || cal(days[i - 1].value.first().time).get(Calendar.MONTH) != mo
            Column(Modifier.height(136.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            // 달이 바뀌는 자리에 그 달의 이름
            Text(if (head) "${mo + 1}월" else "", style = ts(18, C.Hi.copy(alpha = 0.86f), FontWeight.Medium).onScene(0.6f))
            Box(Modifier.height(108.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size).pressable { onPick(i) }.clip(CircleShape)
                    .border(if (on) 2.5.dp else 1.dp, if (on) C.Accent else Color.White.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DayScene(d.value, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (on) 0.16f else 0.34f)))
                Num("%d".format(cal(d.value.first().time).get(Calendar.DAY_OF_MONTH)), if (on) 30 else 22, C.Hi, weight = FontWeight.Normal, shadow = true)
            }
            }
            }
        }
    }
}

// ================================================================ 하루 펼쳐 보기

@Composable
fun DaySpread(m: AppModel) {
    val list = m.entries.filter { Store.dayKey(it.time) == m.dayKey }
    val closedAt = m.current?.let { m.store.closedAt(it.id, m.dayKey) }
    val ink = C.Hi
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().background(veil(DayPart.DAY, 0f to 0.30f, 1f to 0.50f)))
            Text("이날은 말없이 달렸어요.", style = ts(34, ink, family = Serif).onScene(), modifier = Modifier.align(Alignment.Center))
            Btn("일기장", Modifier.padding(start = 64.dp, top = 112.dp), glyph = G.Back) { m.screen = Screen.Book }
            return@Box
        }
        val c = cal(list.first().time)
        val scene = sceneOf(list)
        val part = partOf(list)
        val rain = list.last().scene.rain
        SideEffect { if (m.screen == Screen.Day) Stage.hang(scene, part, rain) }
        Box(Modifier.fillMaxSize().background(veilH(0f to 0.46f, 0.40f to 0.14f, 0.62f to 0f)))
        Box(Modifier.fillMaxSize().background(veil(part, 0f to 0.26f, 0.20f to 0f, 0.52f to 0f, 1f to 0.56f)))

        Btn("일기장", Modifier.padding(start = 64.dp, top = 112.dp).rise(0), glyph = G.Back) { m.screen = Screen.Book }

        // 왼쪽: 그날의 얼굴 — 날짜, 제목, 마음의 날씨, 오늘의 한 줄
        Column(Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 178.dp).width(720.dp)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.rise(1)) {
                Num("%d".format(c.get(Calendar.DAY_OF_MONTH)), 132, ink, Modifier.padding(end = 20.dp), shadow = true)
                Column(Modifier.padding(bottom = 26.dp)) {
                    Text(weekdayName(c), style = ts(24, ink, FontWeight.SemiBold).onScene(0.6f))
                    Text("${c.get(Calendar.YEAR)}년 ${c.get(Calendar.MONTH) + 1}월  ·  ${scene.label}", style = ts(16, ink.copy(alpha = 0.86f), FontWeight.Medium).onScene(0.6f))
                    if (closedAt != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Lamp(C.Green, size = 8.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("${hm24(cal(closedAt))} 마무리", style = ts(14, ink.copy(alpha = 0.92f), FontWeight.Medium).onScene(0.6f))
                        }
                    }
                }
            }
            RevealText(Words.dayTitle(c, list).substringAfter(", "), ts(42, ink, FontWeight.SemiBold, family = Serif, lh = 1.3f).onScene(), delayMs = 260)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                list.map { it.mood }.distinct().take(5).forEachIndexed { i, md ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.rise(3 + i, 300)) {
                        MoodOrb(md, 68.dp, phase = i * 1.1f)
                        Text(md.label, style = ts(17, ink.copy(alpha = 0.90f), FontWeight.Medium).onScene(0.6f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DayArc(list, Modifier.width(520.dp), labels = false)
            Spacer(Modifier.height(26.dp))
            Text(Words.dayLine(list).keepWords(), style = ts(28, ink, family = Serif, lh = 1.4f).onScene(), modifier = Modifier.rise(8, 600))
            Spacer(Modifier.height(10.dp))
            Text("${Words.closing}   — 길동무", style = ts(19, ink.copy(alpha = 0.80f)).onScene(0.6f))
        }

        // 오른쪽: 하루의 기록 — 판 없이, 장면이 오른쪽으로 어스름해지는 자리에 그날의 말들을 시간의 줄에 꿰어 놓는다
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val l = size.width - 1060.dp.toPx()
            val dark = Color(0xFF07061A)
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 0.40f to dark.copy(alpha = 0.46f), 1f to dark.copy(alpha = 0.72f), startX = l, endX = size.width),
                topLeft = Offset(l, 0f), size = androidx.compose.ui.geometry.Size(size.width - l, size.height),
            )
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.06f to Color.Transparent, 0.26f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 64.dp).width(700.dp).fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // 위아래 끝에서는 스르르 사라진다 (아래의 길잡이 뒤로 글이 깔리지 않게)
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.045f to Color.Black, 0.76f to Color.Black, 0.89f to Color.Transparent, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
                .verticalScroll(rememberScrollState()).padding(top = 46.dp, bottom = 320.dp),
        ) {
            Kicker("${m.current?.name ?: ""}님의 하루  ·  말 ${list.size}", Modifier.padding(start = 26.dp), color = C.Hi.copy(alpha = 0.94f), sp = 22, node = null)
            Spacer(Modifier.height(28.dp))
            list.forEachIndexed { i, e -> PageEntry(e, i, last = i == list.lastIndex) }
        }
    }
}

@Composable
private fun PageEntry(e: Entry, i: Int, last: Boolean = false) {
    val c = cal(e.time)
    val tone = e.mood.tone()
    Row(
        Modifier.fillMaxWidth().rise(i, 300).drawBehind {
            // 시간의 줄: 이 말의 빛에서 다음 말의 빛까지 (마지막 말 아래로는 옅어지며 끝난다)
            val x = 5.dp.toPx()
            val top = 26.dp.toPx()
            if (!last) drawLine(C.Hi.copy(alpha = 0.34f), Offset(x, top), Offset(x, size.height + 10.dp.toPx()), 1.dp.toPx())
            else drawLine(Brush.verticalGradient(listOf(C.Hi.copy(alpha = 0.34f), Color.Transparent), startY = top, endY = top + 70.dp.toPx()), Offset(x, top), Offset(x, top + 70.dp.toPx()), 1.dp.toPx())
        }.padding(bottom = 50.dp),
    ) {
        // 시간의 줄 위에 맺힌, 그 말의 마음빛
        Lamp(tone.a, size = 10.dp, modifier = Modifier.padding(top = 4.dp).offset(x = (-7).dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Num(hm24(c), 27, C.Hi, weight = FontWeight.Normal)
                Spacer(Modifier.width(14.dp))
                Text(e.mood.label + "  ·  " + phaseLabel(e.scene) + (e.place?.let { "  ·  길 위에 남김 · $it" } ?: ""), style = ts(19, C.Hi.copy(alpha = 0.80f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            e.question?.let {
                Spacer(Modifier.height(10.dp))
                Text("길동무  —  $it", style = ts(20, C.Hi.copy(alpha = 0.72f), lh = 1.5f))
            }
            Spacer(Modifier.height(10.dp))
            Text("“${e.text}”".keepWords(), style = ts(34, C.Hi, family = Serif, lh = 1.46f).copy(letterSpacing = (-0.01).em, lineBreak = LineBreak.Heading).onScene(0.5f))
            Spacer(Modifier.height(10.dp))
            // (이 한 줄은 내 말이 아니라, 그 말에 길동무가 붙인 한 줄이다. 누구의 말인지 밝혀 둔다.)
            Text(Words.reply(e.text, e.id) + "   — 길동무", style = ts(20, C.Hi.copy(alpha = 0.74f), lh = 1.5f))
        }
    }
}

// ================================================================ 이번 달: 마음 달력

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthBoard(m: AppModel) {
    val now = m.clockOverride ?: Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val list = m.entries.filter { cal(it.time).let { c -> c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == now.get(Calendar.YEAR) } }
    val byDay = list.groupBy { cal(it.time).get(Calendar.DAY_OF_MONTH) }
    val morning = list.count { cal(it.time).get(Calendar.HOUR_OF_DAY) < 12 }
    // 이달의 한 줄: 이달에 기쁨·설렘으로 남긴 말 가운데 가장 최근의 말 (그런 말이 없으면 이달에 마지막으로 남긴 말)
    val best = list.filter { it.mood == Mood.JOY || it.mood == Mood.FLUTTER }.maxByOrNull { it.time } ?: list.maxByOrNull { it.time }
    val counts = list.groupingBy { it.mood }.eachCount().entries.sortedByDescending { it.value }
    val weather = byDay.mapValues { (_, l) -> if (l.any { it.scene.rain }) "비" else l.last().mood.weather }
    val sunny = weather.values.count { it == "맑음" }
    val rainy = weather.values.count { it == "비" }
    val cloudy = weather.values.count { it == "흐림" }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(84.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().rise(0)) {
                Num("%d".format(month + 1), 132, C.Hi)
                Spacer(Modifier.width(14.dp))
                Text("월의 길동무", style = ts(36, C.Hi, FontWeight.SemiBold, family = Serif), modifier = Modifier.padding(bottom = 26.dp))
                Spacer(Modifier.weight(1f))
                BookTabs(m, Modifier.padding(bottom = 30.dp))
            }
            // 숫자판 대신 말로: 며칠 동안 몇 번, 그리고 마음이 가장 자주 머문 때
            Text(
                if (list.isEmpty()) "이번 달의 첫 마음을 기다리고 있어요."
                else "이번 달, ${m.current?.name}님은 ${koCount(byDay.size)} 날 동안 ${koCount(list.size)} 번 마음을 들려주었어요.\n마음이 가장 자주 머문 때는 ${if (morning * 2 >= list.size) "출근길 아침" else "돌아오는 저녁"}.",
                style = ts(27, C.Hi.copy(alpha = 0.94f), family = Serif, lh = 1.55f), modifier = Modifier.rise(1),
            )
            Spacer(Modifier.height(34.dp))
            // 이달의 마음들: 남긴 차례대로, 마음 하나에 빛 하나
            MoodLights(list, Modifier.fillMaxWidth().height(34.dp).rise(3))
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.rise(4)) {
                counts.forEach { (md, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Lamp(md.tone().a, size = 8.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("${md.label} $n", style = ts(19, C.Hi.copy(alpha = 0.88f), FontWeight.Medium))
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                when {
                    list.isEmpty() -> ""
                    rainy > sunny -> "비 온 날이 조금 많았던 달. 그래도 당신은 매일 길을 나섰어요."
                    sunny > cloudy + rainy -> "맑은 날이 더 많았던 달. 그 햇살이 다음 달에도 이어지길."
                    sunny == rainy -> "맑은 날과 비 온 날이 번갈아 찾아온 달. 둘 다 당신의 계절이었어요."
                    else -> "흐렸다 개었다, 그렇게 한 달을 건너왔어요."
                },
                style = ts(29, C.Hi, family = Serif, lh = 1.45f), modifier = Modifier.rise(5),
            )
            if (byDay.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Tag("이달의 빛깔", C.Low, Modifier.rise(5))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.rise(6)) {
                    byDay.entries.sortedByDescending { it.key }.take(6).forEach { (d, l) ->
                        Box(
                            Modifier.size(104.dp).pressable {
                                m.dayKey = Store.dayKey((now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis); m.screen = Screen.Day
                            }.clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.34f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            DayScene(l, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
                            Num("%d".format(d), 30, C.Hi, weight = FontWeight.Normal, shadow = true)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            best?.let {
                Row(Modifier.width(650.dp).rise(6), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(92.dp).background(Brush.verticalGradient(listOf(Color.Transparent, C.Accent, Color.Transparent)), CircleShape))
                    Spacer(Modifier.width(22.dp))
                    Column {
                        Tag("이달의 한 줄  ·  ${cal(it.time).get(Calendar.MONTH) + 1}월 ${cal(it.time).get(Calendar.DAY_OF_MONTH)}일에 남긴 말" + if (it.mood == Mood.JOY || it.mood == Mood.FLUTTER) " (이달의 가장 최근 기쁜 말)" else "", C.Hi.copy(alpha = 0.90f))
                        Spacer(Modifier.height(8.dp))
                        Text("“${Words.headline(it.text)}”".keepWords(), style = ts(27, C.Hi, FontWeight.SemiBold, family = Serif, lh = 1.45f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        MoodCalendar(Modifier.width(600.dp).fillMaxHeight().rise(2), m, now, byDay)
    }
}

@Composable
private fun Stat(label: String, value: Int, suffix: String) {
    Column {
        Tag(label, C.Low)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            CountUpLed(value, 7.dp, C.Hi, digits = 2)
            Spacer(Modifier.width(8.dp))
            Text(suffix, style = ts(18, C.Mid, FontWeight.Medium), modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

/** 하나, 둘이 아니라 '한 번', '두 날'처럼 세는 말 (99까지. 그 위는 숫자 그대로) */
private fun koCount(n: Int): String {
    if (n !in 1..99) return "$n"
    val ones = arrayOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
    val tens = arrayOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")
    if (n == 20) return "스무"
    return tens[n / 10] + ones[n % 10]
}

/** 이달에 남긴 마음들을 남긴 차례대로 한 줄에: 마음 하나에 그 감정의 빛 하나 (왼쪽에서부터 차례로 켜진다) */
@Composable
private fun MoodLights(list: List<Entry>, modifier: Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(300); p.animateTo(1f, tween(1500, easing = EaseOutCubic)) }
    val clock = LocalClock.current
    val moods = remember(list) { list.sortedBy { it.time }.map { it.mood }.let { all -> if (all.size <= 44) all else all.filterIndexed { i, _ -> i % ((all.size + 43) / 44) == 0 } } }
    Canvas(modifier) {
        if (moods.isEmpty()) return@Canvas
        val step = minOf(30.dp.toPx(), size.width / moods.size)
        val y = size.height / 2f
        drawLine(Brush.horizontalGradient(0f to C.Hi.copy(alpha = 0.30f), 0.9f to C.Hi.copy(alpha = 0.10f), 1f to Color.Transparent, endX = step * moods.size + 30.dp.toPx()), Offset(0f, y), Offset(step * moods.size + 30.dp.toPx(), y), 1.dp.toPx())
        moods.forEachIndexed { i, md ->
            val a = ((p.value * (moods.size + 6) - i) / 6f).coerceIn(0f, 1f)
            if (a <= 0f) return@forEachIndexed
            val c = Offset(step * (i + 0.5f), y)
            val tone = md.tone().a
            val breath = 0.86f + 0.14f * kotlin.math.sin(clock.value * 1.3f + i * 0.9f)
            drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.55f * a * breath), Color.Transparent), c, 15.dp.toPx()), 15.dp.toPx(), c)
            drawCircle(tone.copy(alpha = a), 5.dp.toPx() * (0.5f + 0.5f * a), c)
            drawCircle(Color.White.copy(alpha = 0.85f * a), 1.7.dp.toPx(), c)
        }
    }
}

/** 마음을 남긴 날마다, 그날의 감정이 빛으로 켜지는 달력 */
@Composable
private fun MoodCalendar(modifier: Modifier, m: AppModel, now: Calendar, byDay: Map<Int, List<Entry>>) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(300); reveal.animateTo(1f, tween(1400, easing = EaseOutCubic)) }
    val first = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = first.get(Calendar.DAY_OF_WEEK) - 1
    val days = now.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 26.dp)) {
            Text("마음 달력", style = ts(22, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Text("마음을 남긴 날마다 빛이 켜져요", style = ts(18, C.Hi.copy(alpha = 0.74f)))
        }
        Spacer(Modifier.height(22.dp))
        Row {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Tag(it, C.Low) }
            }
        }
        Spacer(Modifier.height(6.dp))
        val rows = (offset + days + 6) / 7
        Column(Modifier.height((rows * 122).dp).fillMaxWidth()) {
            for (r in 0 until rows) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val d = r * 7 + col - offset + 1
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (d in 1..days) {
                                val l = byDay[d]
                                val a = (reveal.value * 1.5f - d / days.toFloat() * 0.5f).coerceIn(0f, 1f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = if (l != null) Modifier.pressable {
                                        m.dayKey = Store.dayKey((now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis); m.screen = Screen.Day
                                    } else Modifier,
                                ) {
                                    if (l != null) MoodOrb(l.last().mood, 64.dp, Modifier.graphicsLayer { alpha = a }, phase = d * 0.7f)
                                    else Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(5.dp).background(C.Off, CircleShape)) }
                                    Text("$d", style = ts(18, if (d == today) C.Hi else C.Hi.copy(alpha = if (l != null) 0.86f else 0.56f), if (d == today) FontWeight.Bold else FontWeight.Medium))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
