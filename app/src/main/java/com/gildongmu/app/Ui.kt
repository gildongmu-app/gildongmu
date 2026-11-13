package com.gildongmu.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.sin

// ================================================================ 공용 말 조각

private val WEEK = arrayOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")
fun weekdayName(c: Calendar) = WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]
fun ampm(c: Calendar) = if (c.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
fun hhmm(c: Calendar): String {
    val h = c.get(Calendar.HOUR_OF_DAY) % 12
    return "${if (h == 0) 12 else h}:${"%02d".format(c.get(Calendar.MINUTE))}"
}
/** 24시간제 */
fun hm24(c: Calendar) = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
fun phaseLabel(s: Scene): String {
    val where = when (s.phase) {
        "depart" -> "길을 나서기 전"
        "arrive" -> "막 도착한 자리"
        "driving" -> "달리는 길 위"
        else -> "잠시 멈춘 차 안"
    }
    return if (s.rain) "비 오는 날, $where" else where
}

/**
 * 지금 화면에 깔 풍경을 바꿔 달라고 맡기는 자리.
 * 일기장은 펼친 날의 풍경을 여기에 걸어 두고, 나갈 때 내려놓는다.
 */
object Stage {
    var scene by mutableStateOf<SceneKind?>(null)
    var part by mutableStateOf<DayPart?>(null)
    var rain by mutableStateOf(false)
    fun hang(scene: SceneKind, part: DayPart, rain: Boolean) { this.scene = scene; this.part = part; this.rain = rain }
    fun clear() { scene = null; part = null; rain = false }
}

// ================================================================ 뼈대

@Composable
fun GildongmuApp(m: AppModel) {
    ProvideClock {
        val pan = remember { ScenePan() }
        CompositionLocalProvider(LocalScenePan provides pan) {
            // 지금 창밖의 풍경 (앱의 중심 색도 여기서 온다)
            val beat by produceState(0) { while (true) { delay(15_000); value++ } }
            val tick = m.carTick
            val now = remember(beat, m.clockOverride, tick) { m.clock() }
            val scene = Look.sceneNow(now, m.car.raining)
            SideEffect { Look.scene = scene; Look.partNow = Look.part(now) }

            // 길 위·친구에게 남기는 한마디는 그 화면의 작은 창에서 듣는다 (화면 전체를 덮지 않음)
            val diaryListening = m.listening.active && m.listenTarget == null && !m.listenFriends
            val lastListen = remember { mutableStateOf(Listening()) }
            if (diaryListening) lastListen.value = m.listening
            val lastMismatch = remember { mutableStateOf<VoiceMismatch?>(null) }
            if (m.mismatch != null) lastMismatch.value = m.mismatch

            // 여는 장면: 앱을 켤 때 한 번. 걷히기 시작할 때 뒤의 화면을 처음부터 다시 나타나게 한다.
            // (아직 시작하지 않았으면 환영 장면 대신 런치 화면이 선다)
            var opening by rememberSaveable { mutableStateOf(if (m.skipOpening || m.driving || m.launching) 2 else 0) }   // 0 여는 중 · 1 걷히는 중 · 2 끝
            var replay by remember { mutableStateOf(false) }                                                // 켜진 뒤에 환영 장면을 다시 트는 중
            // 런치 화면이 서면 환영 장면은 접는다 (켜자마자 런치 화면 명령이 온 경우에도 겹치지 않게)
            LaunchedEffect(m.launching) { if (m.launching) { if (opening < 2) opening = 2; replay = false; m.welcoming = false } }
            // 화면 전체를 덮는 것(듣는 중·목소리 확인·마무리)이 올라와 있는 동안에는, 그 밑의 장면은 그리지 않는다 (같은 장면을 두 번 그리지 않게)
            // 첫 화면이 한 번 다 나타난 뒤로는, 화면을 오갈 때 나타나는 박자를 빠르게 한다
            LaunchedEffect(opening) { if (opening == 2) { delay(4200); Pace.k = 0.45f } else Pace.k = 1f }
            val overlaid = diaryListening || m.mismatch != null || m.closing
            var resting by remember { mutableStateOf(false) }
            LaunchedEffect(overlaid) { if (overlaid) { delay(700); resting = true } else resting = false }
            Box(Modifier.fillMaxSize().background(C.Night)) {
                key(opening >= 1) {
                    AnimatedContent(
                        targetState = when { m.launching -> 2; m.pickingProfile || m.current == null -> 1; else -> 0 },
                        transitionSpec = { fadeIn(tween(620, delayMillis = 120)) togetherWith fadeOut(tween(320)) },
                        label = "root",
                    ) { root ->
                        when (root) {
                            2 -> LaunchScreen(m) { m.launchStarted = true; m.pickingProfile = true }
                            1 -> ProfileScreen(m)
                            else -> Shell(m, sceneOn = opening >= 1 && !resting, covered = overlaid)
                        }
                    }
                }
                AnimatedVisibility(diaryListening, enter = fadeIn(tween(520)), exit = fadeOut(tween(520))) { ListeningOverlay(m, lastListen.value) }
                AnimatedVisibility(m.mismatch != null, enter = fadeIn(tween(320)), exit = fadeOut(tween(320))) {
                    lastMismatch.value?.let { MismatchSheet(m, it) }
                }
                AnimatedVisibility(m.closing, enter = fadeIn(tween(600)), exit = fadeOut(tween(600))) { ClosingOverlay(m) }
                // 환영 장면: 차에 타면(앱을 켜면) 일기장이 펼쳐지며 맞는다. 운전자를 골랐을 때에도 다시 한 번.
                val cue = m.welcomeCue
                LaunchedEffect(cue) { if (cue > 0 && opening == 2 && !m.driving) replay = true else if (cue > 0 && opening == 2) m.welcoming = false }
                if (opening < 2 || replay) {
                    if (opening < 2) PrewarmScenes()   // 장면이 나오는 동안, 풍경들을 화면 구석에 하나씩 작게 그려 미리 준비해 둔다
                    key(cue) {
                        WelcomeGreeting(
                            m, fadeIn = replay,
                            cover = { if (replay) opening = 0 },
                            reveal = { if (opening < 1) opening = 1; m.welcomed() },
                            done = { opening = 2; replay = false; m.welcoming = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 풍경을 한 번 깔고, 그 위에 화면을 올린다.
 * '오늘'과 일기장에서는 풍경이 또렷하고, 글이 많은 화면에서는 흐릿하게 번져 바탕이 된다.
 */
@Composable
private fun Shell(m: AppModel, sceneOn: Boolean = true, covered: Boolean = false) {
    val tick = m.carTick
    val now = remember(m.clockOverride, tick, m.screen) { m.clock() }
    // 달리는 동안에는 '오늘'과 '길 위'만 열린다. 일기장·친구·설정을 보던 중에 출발하면 '오늘'로 돌아온다.
    val driving = m.driving
    val locked = driving && m.screen != Screen.Today && m.screen != Screen.Road
    LaunchedEffect(driving, locked) { if (locked) m.screen = Screen.Today; if (driving) Sheets.scenePicker = false }
    val s = if (locked) Screen.Today else m.screen
    val diary = s == Screen.Book || s == Screen.Day
    val scene = (if (diary) Stage.scene else null) ?: Look.scene
    val part = (if (diary) Stage.part else null) ?: Look.part(now)
    val rain = if (diary && Stage.scene != null) Stage.rain else m.car.raining
    val sharp = s == Screen.Today || s == Screen.Book || s == Screen.Day || s == Screen.Road
    // 달리는 동안에는(따로 '흐르게'를 고르지 않았다면) 바탕의 초점을 살짝 풀어, 눈길을 끌지 않게 한다
    val calmDrive = m.driving && s == Screen.Today && m.settings.driveMotion != "flow"
    val soft by animateFloatAsState(if (!sharp) 1f else if (calmDrive) 0.34f else 0f, tween(900, easing = EaseInOutCubic), label = "soft")
    // 실제로는 밤인데 밝은 시각(새벽·낮·노을)의 장면을 골라 둔 경우에는, 눈부시지 않게 한 톤 가라앉힌다
    val glare = if (Look.partOf(now) == DayPart.NIGHT && part != DayPart.NIGHT) 0.36f else 0f
    val dim by animateFloatAsState(
        (when (s) { Screen.Today -> if (calmDrive) 0.14f else 0f; Screen.Book, Screen.Day -> 0.08f; Screen.Road -> 0.06f; Screen.Settings -> 0.42f; else -> 0.34f } + glare).coerceAtMost(0.72f),
        tween(900), label = "dim",
    )
    val bd = remember { Backdrop() }
    // 화면 전체를 덮는 것(듣는 중·목소리 확인·마무리)이 올라오면, 이 화면의 글과 유리는 재빨리 비켜 준다 (두 화면의 글이 겹쳐 읽히지 않게).
    // 말하기 구슬만은 조금 더 머물러, 위에서 떠오르는 큰 구슬로 이어진다.
    val under = animateFloatAsState(if (covered) 0f else 1f, tween(if (covered) 160 else 480, delayMillis = if (covered) 0 else 140), label = "under")
    val underOrb = animateFloatAsState(if (covered) 0f else 1f, tween(if (covered) 300 else 480, delayMillis = if (covered) 0 else 140, easing = LinearEasing), label = "underOrb")
    val underFade = remember { { under.value } }
    val underOrbFade = remember { { underOrb.value } }
    // 글이 많은 화면에서는 뒤의 장면을 초점 밖으로 흐린다 (길 위에서는 지도가 가리므로 하늘은 그대로 둔다)
    LaunchedEffect(Unit) { snapshotFlow { soft }.collect { bd.glass.soften = it } }
    // 일기장을 나서면, 걸어 두었던 그날의 풍경을 내리고 지금의 창밖으로 돌아온다
    LaunchedEffect(s) { if (s != Screen.Book && s != Screen.Day) Stage.clear() }
    Box(Modifier.fillMaxSize().onGloballyPositioned { bd.origin = it.positionInRoot() }) {
        // 장면이 바뀔 때는 겹쳐서 넘어가고, 시각이 바뀔 때는 같은 장면의 빛이 천천히 넘어간다
        Box(Modifier.fillMaxSize().glassBackdrop(bd.glass)) {
            if (sceneOn) Dissolve(Pair(scene, Skin.art), 1400) { (sc, ar), _ ->
                Scenery(
                    Modifier.fillMaxSize(), part = part, rain = rain, speed = m.sceneSpeed,
                    scene = sc, soft = soft, dim = dim, art = ar, level = if (m.listening.active) m.listening.level else 0f,
                    economy = s == Screen.Road,
                )
            }
        }
        CompositionLocalProvider(LocalBackdrop provides bd) {
            AnimatedContent(
                targetState = s,
                transitionSpec = { (fadeIn(tween(360, delayMillis = 90)) + slideInVertically(tween(420, easing = EaseOutCubic)) { it / 40 }) togetherWith fadeOut(tween(180)) },
                label = "screen",
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = (1f - Sheets.cover) * under.value },
            ) { sc ->
                // 그 화면의 유리는 내용과 같은 박자로 나타나고 사라진다 (빈 유리판만 먼저 뜨거나 남지 않게)
                val vis = transition.animateFloat(
                    transitionSpec = { if (targetState == EnterExitState.Visible) tween(360, delayMillis = 90) else tween(140) }, label = "glassFade",
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                val fade = remember { { vis.value * under.value * (1f - Sheets.cover) } }      // (바탕 고르기가 열려 글이 비켜 있을 때는 유리도 함께 비킨다)
                // 오늘·길 위·일기장은 화면 끝까지 쓴다. 나머지는 위의 이름표와 아래 길잡이를 피해 앉는다.
                val bleed = sc == Screen.Road || sc == Screen.Today || sc == Screen.Book || sc == Screen.Day
                CompositionLocalProvider(LocalGlassFade provides fade) {
                    Box(Modifier.fillMaxSize().padding(start = if (bleed) 0.dp else 64.dp, end = if (bleed) 0.dp else 64.dp, top = if (bleed) 0.dp else 126.dp, bottom = if (bleed) 0.dp else 164.dp)) {
                        when (sc) {
                            Screen.Today -> Home(m)
                            Screen.Road -> RoadScreen(m)
                            Screen.Friends -> FriendsScreen(m)
                            Screen.Book -> Book(m)
                            Screen.Day -> DaySpread(m)
                            Screen.Month -> MonthBoard(m)
                            Screen.Settings -> SettingsScreen(m)
                        }
                    }
                }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides bd, LocalGlassFade provides underFade) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = under.value }) {
                TopBar(m)
                Toast(m)
            }
            Dock(m, underFade, underOrbFade)
            MakerLine(Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 12.dp))
            ScenePicker(m)
        }
    }
}

/**
 * 풍경이 바뀔 때: 앞 풍경을 그대로 둔 채 새 풍경을 그 위에 천천히 올린다.
 * (둘을 함께 흐리게 하면 중간에 화면이 어두워진다. 창밖은 깜빡이지 않아야 한다.)
 */
@Composable
private fun <T> Dissolve(target: T, millis: Int, prepare: suspend (T) -> Unit = {}, content: @Composable (T, Boolean) -> Unit) {
    var front by remember { mutableStateOf(target) }
    var back by remember { mutableStateOf<T?>(null) }
    var alpha by remember { mutableStateOf(1f) }
    LaunchedEffect(target) {
        if (target != front) {
            prepare(target) // 새 그림을 먼저 읽어 둔 뒤에 올린다 (읽는 동안 빈 화면이 비치지 않게)
            back = front; front = target; alpha = 0f
        }
        if (alpha < 1f) animate(alpha, 1f, animationSpec = tween((millis * (1f - alpha)).toInt().coerceAtLeast(1), easing = EaseInOutCubic)) { v, _ -> alpha = v }
        back = null
    }
    back?.let { b -> key(b) { content(b, false) } }
    key(front) { Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) { content(front, true) } }
}

// ================================================================ 위: 이름, 지금의 장면, 지금의 차

/** 화면 위에 잠깐 떠 있는 것들 (바탕 고르기) */
object Sheets {
    var scenePicker by mutableStateOf(false)
    /** 바탕 고르기가 열린 정도 (0..1). 열려 있는 동안에는 화면의 글을 치워, 바뀌는 바탕이 그대로 보이게 한다. */
    var cover by mutableStateOf(0f)
}

@Composable
private fun BoxScope.TopBar(m: AppModel) {
    val me = m.current ?: return
    @Suppress("UNUSED_VARIABLE") val tick = m.carTick
    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 64.dp, end = 58.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoGlyph(Modifier.size(42.dp))
        Spacer(Modifier.width(8.dp))
        Text("길동무", style = TextStyle(fontFamily = Myeongjo, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, color = C.Hi, letterSpacing = 0.04.em).onScene(0.5f))
        Spacer(Modifier.width(20.dp))
        // 지금의 장면: 누르면 바탕을 고른다
        Row(
            Modifier.pressable { if (m.driving) m.showToast("차를 세우면 바탕을 고를 수 있어요") else Sheets.scenePicker = true }
                .glass(dim = 0.10f).heightIn(min = 72.dp).padding(start = 18.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Lamp(C.Accent, size = 7.dp)
            Spacer(Modifier.width(4.dp))
            AnimatedContent(
                targetState = Look.scene.label + " · " + Look.partNow.label + if (m.car.raining) " · 비" else "",
                transitionSpec = { (fadeIn(tween(500, delayMillis = 120)) + slideInVertically(tween(600, easing = Ease.out)) { it / 2 }) togetherWith (fadeOut(tween(240)) + slideOutVertically(tween(300)) { -it / 2 }) },
                label = "sceneName",
            ) { label -> Text(label, style = ts(16, C.Hi.copy(alpha = 0.94f), FontWeight.Medium)) }
            Spacer(Modifier.width(10.dp))
            Glyph(G.Scene, C.Hi.copy(alpha = 0.78f), 22.dp, 1.7.dp)
        }
        Spacer(Modifier.weight(1f))
        // 지금의 차: 정차·주행, 혼자·함께, 비
        // (누르는 것이 아니라 알려 주는 것이므로 유리에 올리지 않는다: 글과 작은 그림만. 달리는 중만 초록 빛으로 알린다.)
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val parked = !m.driving
            val standing = m.standing      // 달리는 중이지만 신호 대기처럼 서 있는 동안
            val ink = C.Hi.copy(alpha = 0.92f)
            if (parked) Glyph(G.Park, ink, 22.dp, 1.8.dp) else Lamp(if (standing) C.Amber else C.Green, size = 10.dp, blink = !standing)
            Spacer(Modifier.width(7.dp))
            Text(if (parked) "주차" else if (standing) "정차" else "주행", style = ts(15, ink, FontWeight.Medium).onScene(0.6f))
            m.car.alone?.let { alone ->
                Spacer(Modifier.width(20.dp))
                Glyph(if (alone) G.Alone else G.Together, ink, 22.dp, 1.8.dp)
                Spacer(Modifier.width(7.dp))
                Text(if (alone) "혼자" else "함께", style = ts(15, ink, FontWeight.Medium).onScene(0.6f))
            }
            if (m.car.raining) {
                Spacer(Modifier.width(20.dp))
                Glyph(G.Rain, ink, 22.dp, 1.8.dp)
                Spacer(Modifier.width(7.dp))
                Text("비", style = ts(15, ink, FontWeight.Medium).onScene(0.6f))
            }
        }
        Spacer(Modifier.width(12.dp))
        val drv = m.driving
        Box(Modifier.size(76.dp).pressable { if (drv) m.showToast("차를 세우면 설정을 열 수 있어요") else m.screen = Screen.Settings }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).glass(dim = 0.14f), contentAlignment = Alignment.Center) { Glyph(G.Settings, C.Hi.copy(alpha = if (drv) 0.36f else 1f), 24.dp, 1.8.dp) }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(76.dp).pressable { if (drv) m.showToast("차를 세우면 운전하는 사람을 바꿀 수 있어요") else m.pickingProfile = true }, contentAlignment = Alignment.Center) {
            Avatar(me.tone(), me.initial(), 64.dp, dim = drv)
        }
    }
}

// ================================================================ 아래: 길잡이와 말하기 구슬

@Composable
private fun BoxScope.Dock(m: AppModel, fade: () -> Float = { 1f }, orbFade: () -> Float = { 1f }) {
    val active = when (m.screen) { Screen.Today -> 0; Screen.Road -> 1; Screen.Friends -> 2; Screen.Book, Screen.Day, Screen.Month -> 3; else -> -1 }
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) {
        Row(Modifier.align(Alignment.BottomCenter).height(92.dp).glass(dim = 0.18f, refract = 14f).graphicsLayer { alpha = fade() }.animateContentSize(tween(520, easing = Ease.inOut)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            val drv = m.driving
            // 달리는 동안에는 열 수 있는 것만 남긴다: 오늘 · (말하기 구슬) · 길 위
            NavItem(G.Today, "오늘", active == 0) { m.screen = Screen.Today }
            if (!drv) NavItem(G.Route, "길 위", active == 1) { m.screen = Screen.Road }
            Spacer(Modifier.width(168.dp))
            if (drv) NavItem(G.Route, "길 위", active == 1) { m.screen = Screen.Road }
            else {
                NavItem(G.Together, "친구", active == 2) { m.screen = Screen.Friends }
                NavItem(G.Book, "일기장", active == 3) { m.screen = Screen.Book }
            }
        }
        // 가운데: 말하기 구슬. 길잡이 위로 솟아 있어, 어느 화면에서든 한 번에 닿는다.
        // (빛이 번지는 자리는 넓지만, 눌리는 자리는 구슬의 동그라미만큼이다. 옆의 길잡이 칸을 덮지 않게)
        CompositionLocalProvider(LocalGlassFade provides orbFade) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 62.dp).size(216.dp).graphicsLayer { alpha = orbFade() }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(116.dp).glass(dim = 0.30f, refract = 16f))
                Orb(216.dp)
                Glyph(G.Mic, Color.White, 34.dp, 2.3.dp)
                Box(Modifier.size(136.dp).clip(CircleShape).pressable { m.startListening() })
            }
        }
    }
}

/** 짧게 알려 주는 한 줄 (어느 화면에서든 아래 길잡이 바로 위에 뜬다) */
@Composable
private fun BoxScope.Toast(m: AppModel) {
    val last = remember { mutableStateOf("") }
    m.toast?.let { last.value = it }
    val shown by animateFloatAsState(if (m.toast != null) 1f else 0f, tween(320, easing = Ease.out), label = "toast")
    if (shown <= 0.01f) return
    val high = m.listening.active && (m.listenTarget != null || m.listenFriends)
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = if (high) 440.dp else 206.dp)
            .glass(amount = shown, dim = 0.42f).graphicsLayer { alpha = shown }.heightIn(min = 68.dp).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(C.Accent, size = 9.dp)
        Spacer(Modifier.width(8.dp))
        Text(last.value, style = ts(19, C.Hi, FontWeight.Medium))
    }
}

@Composable
private fun NavItem(g: G, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val tint by animateColorAsState(if (!enabled) C.Hi.copy(alpha = 0.24f) else if (on) C.Hi else C.Hi.copy(alpha = 0.58f), tween(320), label = "nav")
    // 고르면 아이콘이 처음부터 다시 그어지고, 아래에 빛이 하나 맺힌다
    val stroke = remember { Animatable(1f) }
    LaunchedEffect(on) { if (on) { stroke.snapTo(0f); stroke.animateTo(1f, tween(900, easing = Ease.out)) } else stroke.snapTo(1f) }
    val dot by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 320f), label = "navDot")
    Column(Modifier.width(126.dp).fillMaxHeight().pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(46.dp)) {
                if (dot > 0.01f) drawCircle(Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.30f * dot), Color.Transparent), center, size.minDimension / 2f), size.minDimension / 2f)
            }
            Glyph(g, tint, 31.dp, if (on) 2.1.dp else 1.9.dp, draw = stroke.value, accent = if (on) C.Accent else null)
        }
        Text(label, style = ts(16, tint, if (on) FontWeight.SemiBold else FontWeight.Medium), modifier = Modifier.offset(y = (-6).dp))
        Canvas(Modifier.size(18.dp, 5.dp).offset(y = (-4).dp)) {
            if (dot > 0.01f) {
                drawCircle(Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.8f * dot), Color.Transparent), center, 9.dp.toPx()), 9.dp.toPx())
                drawCircle(C.Accent, 2.6.dp.toPx() * dot, center)
            }
        }
    }
}

// ================================================================ 오늘: 살아 있는 장면 위에, 시각과 오늘의 글귀

@Composable
private fun Home(m: AppModel) {
    val me = m.current ?: return
    val beat by produceState(0) { while (true) { delay(10_000); value++ } }
    val now = remember(beat, m.clockOverride) { m.clock() }
    val today = m.entries.filter { Store.dayKey(it.time) == Store.dayKey(now.timeInMillis) }
    @Suppress("UNUSED_VARIABLE") val tick = m.carTick
    val driving = m.driving
    val part = Look.part(now)
    val (hello, sub) = Words.greeting(me.name, now.get(Calendar.HOUR_OF_DAY))
    val lastAsk = remember { mutableStateOf<Ask?>(null) }
    if (m.ask != null) lastAsk.value = m.ask
    val asking = m.ask != null && !driving
    // 다른 자리에 누가 타고 있고 '함께 탈 때는 내 글 가리기'를 켜 두었으면, 오늘 남긴 말을 띄우지 않는다
    val veiled = m.settings.hideWhenTogether && m.car.alone == false
    // 대답이 없으면 물음은 한동안 머물다 조용히 물러나고, 그 자리에 다시 오늘의 말이 선다
    LaunchedEffect(m.ask) { val a = m.ask; if (a != null) { delay(48_000); if (m.ask === a && !m.listening.active) m.dismissAsk() } }

    Box(Modifier.fillMaxSize()) {
        // 글이 읽히도록 위쪽에만 옅은 어스름 (볼거리가 있는 아래쪽은 그대로 둔다)
        Box(Modifier.fillMaxSize().background(veil(part, 0f to 0.34f, 0.26f to 0.16f, 0.54f to 0f, 0.84f to 0f, 1f to 0.34f)))

        // 왼쪽 위: 이 화면의 주인공은 '말'이다.
        //  오늘 남긴 말이 있으면 그 말이 글귀로 크게 서고, 아직 없으면 인사가 그 자리에 선다.
        //  길동무가 먼저 물을 때는 물음이, 달리는 동안에는 짧은 두 줄만.
        AnimatedContent(
            targetState = when { driving -> 2; asking -> 3; today.isEmpty() -> 0; veiled -> 4; else -> 1 },
            transitionSpec = { (fadeIn(tween(520, delayMillis = 160))).togetherWith(fadeOut(tween(200))).using(androidx.compose.animation.SizeTransform(clip = false)) },
            label = "lead", modifier = Modifier.padding(start = 64.dp, top = 142.dp),
        ) { mode ->
            when (mode) {
                1 -> TodayWords(m, today, now)
                3 -> lastAsk.value?.let { AskLead(m, it) } ?: Spacer(Modifier.size(1.dp))
                2 -> Column(Modifier.width(1000.dp)) {
                    Kicker(hello, Modifier.rise(0), color = C.Hi.copy(alpha = 0.94f), sp = 23)
                    Spacer(Modifier.height(30.dp))
                    RevealText("말하고 싶을 때,\n구슬을 눌러요.", ts(68, C.Hi, FontWeight.SemiBold, family = Serif, lh = 1.32f).onScene(), delayMs = 200)
                }
                4 -> Column(Modifier.width(1000.dp)) {
                    Kicker("오늘 남긴 말 ${today.size}", Modifier.rise(0), color = C.Hi.copy(alpha = 0.94f), sp = 23)
                    Spacer(Modifier.height(30.dp))
                    RevealText(hello, ts(68, C.Hi, FontWeight.SemiBold, family = Serif, lh = 1.3f).copy(letterSpacing = (-0.012).em).onScene(), delayMs = 200)
                    Spacer(Modifier.height(16.dp))
                    Text("함께 타고 있어서, 오늘의 말은 잠시 접어 두었어요.", style = ts(26, C.Hi.copy(alpha = 0.92f), family = Serif).onScene(0.6f), modifier = Modifier.rise(0, 520))
                }
                else -> Column(Modifier.width(1000.dp)) {
                    Kicker("오늘의 첫 마디", Modifier.rise(0), color = C.Hi.copy(alpha = 0.94f), sp = 23)
                    Spacer(Modifier.height(30.dp))
                    RevealText(hello, ts(68, C.Hi, FontWeight.SemiBold, family = Serif, lh = 1.3f).copy(letterSpacing = (-0.012).em).onScene(), delayMs = 200)
                    Spacer(Modifier.height(16.dp))
                    Text(sub, style = ts(26, C.Hi.copy(alpha = 0.92f)).onScene(0.6f), modifier = Modifier.rise(0, 520))
                    Spacer(Modifier.height(34.dp))
                    Text("아래의 구슬을 누르고, 지금 마음을 한마디만 들려주세요.", style = ts(26, C.Hi.copy(alpha = 0.88f), family = Serif).onScene(0.6f), modifier = Modifier.rise(0, 760))
                }
            }
        }

        // 오른쪽 위: 지금 시각과 오늘, 그리고 하루를 잇는 호 (마음을 남긴 시각마다 빛이 맺힌다)
        Column(Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = 112.dp).width(470.dp), horizontalAlignment = Alignment.End) {
            RollingNumber(hm24(now), 104, C.Hi, Modifier.focusIn(0, 80).offset(x = 5.dp), shadow = true)
            Text(
                "${now.get(Calendar.MONTH) + 1}월 ${now.get(Calendar.DAY_OF_MONTH)}일 ${weekdayName(now)}",
                style = ts(22, C.Hi.copy(alpha = 0.92f), FontWeight.Medium).copy(letterSpacing = 0.04.em).onScene(0.6f), modifier = Modifier.rise(0, 300).offset(y = (-6).dp),
            )
            if (!driving) {
                Spacer(Modifier.height(10.dp))
                DayArc(
                    today, now = now, labels = false,
                    modifier = Modifier.rise(2, 420).drawBehind {
                        // 밝은 바탕에서도 가는 선이 읽히도록 깔아 주는 어스름. 네모로 잘리지 않게, 가장자리에서 완전히 사라지는 눌린 동그라미로.
                        val rw = size.width * 0.62f
                        val c = Offset(size.width / 2f, size.height * 0.60f)
                        scale(1f, size.height * 1.1f / rw, pivot = c) {
                            drawCircle(Brush.radialGradient(0f to Color.Black.copy(alpha = 0.18f), 0.5f to Color.Black.copy(alpha = 0.10f), 1f to Color.Transparent, center = c, radius = rw), radius = rw, center = c)
                        }
                    },
                )
            }
        }

        // 오늘을 여기서 마무리할지: 오른쪽 아래
        if (!driving && !asking && today.isNotEmpty()) DayWrapBar(m, now, Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 44.dp))
    }
}

/** 길동무가 먼저 묻는다: 판 없이, 글귀가 놓이던 자리에 물음이 크게 서고 그 아래에 답할 길이 놓인다 */
@Composable
private fun AskLead(m: AppModel, a: Ask) {
    Column(Modifier.width(1040.dp)) {
        Kicker("길동무가 묻습니다", Modifier.rise(0), color = C.Hi.copy(alpha = 0.94f), sp = 23)
        Spacer(Modifier.height(32.dp))
        TypeText(Words.headline(a.text).keepWords(), ts(if (Words.headline(a.text).length > 34) 50 else 58, C.Hi, family = Serif, lh = 1.42f).copy(letterSpacing = (-0.012).em, lineBreak = LineBreak.Heading).onScene(0.5f))
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.rise(0, 700)) {
            if (a.key == AskTemplates.WRAP) {
                Btn("오늘 하루 마무리", glyph = G.Moon, kind = BtnKind.Primary, big = true) { m.endDay() }
                Btn("한마디 더", glyph = G.Mic, big = true, glass = true) { m.answerAsk() }
                Btn("나중에", kind = BtnKind.Ghost, big = true) { m.dismissAsk() }
            } else {
                Btn("들려줄게요", glyph = G.Mic, kind = BtnKind.Primary, big = true) { m.answerAsk() }
                Btn("다음에요", big = true, glass = true) { m.dismissAsk() }
            }
        }
    }
}

/** 하루를 가는 빛 한 줄로: 마음을 남긴 시각마다 그 감정의 빛이 맺힌다 */
@Composable
fun DayStrip(list: List<Entry>, modifier: Modifier = Modifier, now: Calendar? = null) {
    Box(modifier) {
        DayArc(list, now = now)
        DayArcLabels(Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * 오늘 남긴 말이 글귀가 되어 크게 선다.
 *
 * 무엇이 뜨는가 — 기준은 하나다: **이 운전자가 오늘 가장 마지막에 남긴 말.**
 *  - 길동무가 지어낸 글이나 다른 사람의 말, 지난날의 말은 여기에 뜨지 않는다. (오늘 남긴 말이 없으면 인사가 대신 선다.)
 *  - 새로 말하면 그 말로 바뀐다. 그 밖에는 저절로 바뀌지 않는다. (예전에는 오늘의 말들을 11초마다 돌려 보여서 아무 글이나 뜨는 것처럼 보였다.)
 *  - 누가·언제(길 위에 남겼다면 어디에) 남긴 말인지를 글귀 위에 먼저 밝힌다.
 * 누르면 오늘의 일기가 펼쳐져, 오늘 남긴 말을 모두 볼 수 있다.
 */
@Composable
private fun TodayWords(m: AppModel, today: List<Entry>, now: Calendar) {
    val me = m.current ?: return
    val hero = today.firstOrNull { it.id == m.lastSaidId } ?: today.maxByOrNull { it.time } ?: return
    val text = hero.text.trim()
    val at = remember(hero.id) { Calendar.getInstance().apply { timeInMillis = hero.time } }
    // 짧은 말은 크게, 긴 말은 조금 작게 (어느 쪽이든 네 줄 안에)
    val size = when { text.length <= 16 -> 78; text.length <= 34 -> 68; text.length <= 58 -> 58; else -> 50 }
    Column(Modifier.width(1060.dp).pressable { m.dayKey = Store.dayKey(now.timeInMillis); m.screen = Screen.Day }) {
        AnimatedContent(targetState = hero.id, transitionSpec = { fadeIn(tween(420, delayMillis = 120)) togetherWith fadeOut(tween(160)) }, label = "heroWho") { _ ->
            Kicker(
                "${me.name}님이 ${ampm(at)} ${hhmm(at)}에 남긴 말" + (hero.place?.let { "  ·  $it" } ?: ""),
                Modifier.rise(0), color = C.Hi.copy(alpha = 0.94f), sp = 23,
            )
        }
        Spacer(Modifier.height(22.dp))
        // 여는 따옴표는 글귀 바로 위에 바짝 붙여 놓는다 (사이가 뜨면 따로 노는 장식처럼 보인다)
        Column(verticalArrangement = Arrangement.spacedBy((-26).dp)) {
            Text("“", style = ts(108, C.Accent.copy(alpha = 0.94f), family = Serif, lh = 0.56f), modifier = Modifier.rise(1, 160).offset(x = (-5).dp))
            RevealText(
                text.keepWords(),
                ts(size, C.Hi, family = Serif, lh = 1.36f).copy(letterSpacing = (-0.012).em, lineBreak = LineBreak.Heading).onScene(0.5f),
                delayMs = 300, maxLines = 4,
            )
        }
        Spacer(Modifier.height(22.dp))
        // 그 말의 마음, 그리고 오늘 남긴 말이 모두 몇인지 (누르면 오늘의 일기가 펼쳐진다)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(0, 900)) {
            MoodDot(hero.mood)
            Spacer(Modifier.width(18.dp))
            Text(if (today.size > 1) "오늘 남긴 말 ${today.size}개  ·  모두 보기" else "오늘의 일기 펼쳐 보기", style = ts(20, C.Hi.copy(alpha = 0.88f), FontWeight.Medium).onScene(0.6f))
            Spacer(Modifier.width(8.dp))
            Glyph(G.Arrow, C.Hi.copy(alpha = 0.74f), 20.dp, 1.8.dp)
        }
    }
}

/** 오늘을 여기서 마무리할지 (오늘의 일기는 글귀를 누르면 펼쳐진다) */
@Composable
private fun DayWrapBar(m: AppModel, now: Calendar, modifier: Modifier) {
    val closed = m.closedAt
    Row(modifier.rise(1, 120), verticalAlignment = Alignment.CenterVertically) {      // (유리 알약이 먼저 비어 보이지 않게, 글도 곧바로 따라 나온다)
        if (closed == null) {
            Btn("오늘 하루 마무리", glyph = G.Moon, glass = true) { m.endDay() }
        } else {
            val c = Calendar.getInstance().apply { timeInMillis = closed }
            Lamp(C.Green, size = 9.dp)
            Spacer(Modifier.width(8.dp))
            Text("${ampm(c)} ${hhmm(c)}에 마무리한 하루", style = ts(20, C.Hi.copy(alpha = 0.92f), FontWeight.Medium).onScene(0.6f))
            Spacer(Modifier.width(18.dp))
            Btn("일기 보기", glyph = G.Book, glass = true) { m.dayKey = Store.dayKey(now.timeInMillis); m.screen = Screen.Day }
        }
    }
}

// ================================================================ 듣는 중: 흐려진 풍경 위에, 내 말이 떠오르고 구슬이 숨 쉰다

/** 목소리를 따라 숨 쉬는 구슬 (목소리 크기가 바뀔 때 이 조각만 다시 그린다) */
@Composable
private fun LiveOrb(m: AppModel, size: androidx.compose.ui.unit.Dp, awake: Boolean) {
    Orb(size, level = if (awake) m.listening.level else 0f, energy = if (awake) 1f else 0.25f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListeningOverlay(m: AppModel, l: Listening) {
    val clock = LocalClock.current
    // (새로 듣기 시작하면 — 한마디 더, 다시 듣기 — 시계도 처음부터)
    val t0 = remember(l.turn) { clock.value }
    val sec by produceState(0, t0) { while (true) { value = (clock.value - t0).toInt(); delay(250) } }
    val now = remember(m.clockOverride) { m.clock() }
    val part = Look.part(now)
    val stage = when { l.error != null -> 2; l.notice != null -> 3; l.done && !l.forEnroll -> 1; else -> 0 }
    val ink = C.Hi
    SceneStage(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {},
        scene = Look.scene, part = part, rain = m.car.raining, speed = m.sceneSpeed, soft = 0.92f, dim = 0.30f,
        level = if (stage == 0) m.listening.level else 0f,
    ) {
        Row(Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 50.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stage == 0) {
                Lamp(C.Accent, size = 10.dp, blink = true)
                Spacer(Modifier.width(10.dp))
                Text(if (l.forEnroll) "목소리를 기억하고 있어요" else if (l.saving) "잘 들었어요" else if (l.again) "다시 듣고 있어요" else "듣고 있어요", style = ts(19, ink.copy(alpha = 0.92f), FontWeight.Medium))
                if (!l.saving) {
                    Spacer(Modifier.width(18.dp))
                    Num("%d:%02d".format(sec / 60, sec % 60), 24, ink.copy(alpha = 0.72f))
                }
                if (!l.forEnroll) {
                    // 지금 하는 말이 어디에 남는지 늘 밝혀 둔다 (설정의 '말한 것을 남기는 곳')
                    Spacer(Modifier.width(18.dp))
                    Text("·   " + if (m.settings.share == "road") "길 위에도 남겨요" else "내 일기에만", style = ts(18, ink.copy(alpha = 0.80f), FontWeight.Medium))
                }
            } else if (stage == 1) {
                Lamp(C.Green, size = 10.dp)
                Spacer(Modifier.width(10.dp))
                Text("일기에 담았어요", style = ts(19, ink.copy(alpha = 0.92f), FontWeight.Medium))
            } else if (stage == 3) {
                Lamp(C.Amber, size = 10.dp)
                Spacer(Modifier.width(10.dp))
                Text("방금 한 말은 담지 않았어요", style = ts(19, ink.copy(alpha = 0.92f), FontWeight.Medium))
            }
        }
        Text(
            Look.scene.label + " · " + part.label + if (m.car.raining) " · 비" else "",
            style = ts(15, ink.copy(alpha = 0.78f), FontWeight.Medium),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = 54.dp),
        )
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(420, delayMillis = 60)) togetherWith fadeOut(tween(200)) },
            label = "listen",
            modifier = Modifier.align(Alignment.Center),
        ) { st ->
            when (st) {
                0 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(1320.dp)) {
                    val words = remember(l.text) { l.text.split(" ").filter { it.isNotBlank() }.takeLast(18) }      // (길게 말하면 방금 한 말만 보인다)
                    // 받아 적는 자리: 안내말도 첫 낱말도 같은 자리(왼쪽 위)에서 시작한다
                    val caption = !l.forEnroll && !m.driving
                    // 구슬은 아래 길잡이의 말하기 구슬이 그대로 떠올라 커진 것이다
                    val lift = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(80); lift.animateTo(1f, tween(860, easing = Ease.inOut)) }
                    val scroll = rememberScrollState()
                    LaunchedEffect(words.size) { if (caption) scroll.animateScrollTo(scroll.maxValue, tween(520, easing = Ease.out)) }
                    Box(
                        Modifier.width(if (caption) 1120.dp else 1320.dp).height(300.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // 글이 길어져 위로 밀려 올라간 줄은 위에서 옅어지며 사라진다
                                if (scroll.value > 0) drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.26f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
                            }
                            .then(if (caption) Modifier.verticalScroll(scroll, enabled = false) else Modifier),
                        contentAlignment = if (caption) Alignment.TopStart else Alignment.BottomCenter,
                    ) {
                        if (l.forEnroll) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("이 문장을 소리 내어 읽어 주세요", style = ts(19, ink.copy(alpha = 0.85f), FontWeight.Medium))
                            Spacer(Modifier.height(14.dp))
                            Text("“오늘도 같은 길, 다른 마음. 길동무와 함께 달려요.”", style = ts(42, ink, FontWeight.SemiBold, family = Serif), textAlign = TextAlign.Center)
                        } else if (m.driving) {
                            // 달리는 동안에는 받아 적는 글을 띄우지 않는다 (읽을거리를 만들지 않는다). 구슬이 듣고 있다는 것만 보여 준다.
                            Text("듣고 있어요. 말을 마치면 담아 둘게요.", style = ts(34, ink.copy(alpha = 0.90f), family = Serif), textAlign = TextAlign.Center)
                        } else if (words.isEmpty()) {
                            Text(Words.listening, style = ts(34, ink.copy(alpha = 0.88f), family = Serif), modifier = Modifier.padding(top = 78.dp, start = 4.dp))
                        } else FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(13.dp, Alignment.Start),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        ) {
                            words.forEachIndexed { i, w -> key(i) { WordIn(w, ink) } }
                        }
                    }
                    var orbDy by remember { mutableFloatStateOf(0f) }
                    val dockY = with(LocalDensity.current) { 72.dp.toPx() }
                    Box(
                        Modifier.onGloballyPositioned { c ->
                            // (떠오르는 동안의 움직임은 graphicsLayer 가 하므로, 여기서 재는 자리는 늘 제자리다)
                            orbDy = (c.findRootCoordinates().size.height - dockY) - (c.positionInRoot().y + c.size.height / 2f)
                        }.graphicsLayer {
                            val k = 1f - lift.value
                            translationY = orbDy * k
                            val sc = 1f - k * (1f - 216f / 400f)
                            scaleX = sc; scaleY = sc
                        },
                    ) { LiveOrb(m, 400.dp, awake = !l.saving) }
                    // 단추는 구슬이 지나간 뒤에 나타난다 (떠오르는 구슬과 겹치지 않게). 말이 끝나 담는 동안에는 단추 대신 한 줄.
                    Box(Modifier.heightIn(min = 84.dp).graphicsLayer { alpha = ((lift.value - 0.62f) / 0.38f).coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
                        if (l.saving) Row(verticalAlignment = Alignment.CenterVertically) {
                            Lamp(C.Accent, size = 10.dp, blink = true)
                            Spacer(Modifier.width(10.dp))
                            Text(if (l.forEnroll) "목소리를 기억하고 있어요" else "일기에 담고 있어요", style = ts(26, ink.copy(alpha = 0.94f), family = Serif))
                        } else Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Btn("다 말했어요", glyph = G.Check, kind = BtnKind.Primary, big = true) { m.stopListening() }
                            Btn("그만두기", glyph = G.Close, big = true) { m.closeListening() }
                        }
                    }
                }
                1 -> SavedMoment(m, l, ink)
                3 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(1100.dp)) {
                    // 거친 말을 들었을 때: 담지 않고, 안 된다고 알린 뒤 다듬어서 다시 말해 달라고 청한다 (처음에는 곧바로 다시 듣는다)
                    Orb(220.dp, energy = 0.2f, a = C.Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(l.notice ?: "", style = ts(38, ink, family = Serif, lh = 1.45f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(if (l.again) "마음이 조금 가라앉으면, 그때 다시 들려주세요." else "조금만 다듬어서, 한 번만 더 들려주세요.", style = ts(26, ink.copy(alpha = 0.90f), family = Serif), textAlign = TextAlign.Center)
                    if (!m.driving) {
                        Spacer(Modifier.height(18.dp))
                        Text("욕설·거친 말은 담지 않고, 개인 정보는 길동무가 알아서 걸러요.", style = ts(17, ink.copy(alpha = 0.74f)), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(30.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("다시 말할게요", glyph = G.Mic, kind = BtnKind.Primary, big = true) { m.listenAgain() }
                        Btn("그만두기", glyph = G.Close, big = true) { m.closeListening() }
                    }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(1100.dp)) {
                    Orb(220.dp, energy = 0.2f, a = C.Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(l.error ?: "", style = ts(34, ink, family = Serif, lh = 1.5f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!l.forEnroll) Btn("다시 들려줄게요", glyph = G.Mic, kind = BtnKind.Primary, big = true) { m.startListening() }
                        Btn("닫기", glyph = G.Close, big = true) { m.closeListening() }
                    }
                }
            }
        }
    }
}

/** 자막 한 낱말: 흐릿하게 떠올라 초점이 맞으며 제자리에 앉는다 */
@Composable
private fun WordIn(word: String, ink: Color) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(720, easing = Ease.out)) }
    Text(
        word, style = ts(56, ink, FontWeight.SemiBold, family = Serif, lh = 1.3f).onScene(0.5f),
        modifier = Modifier.graphicsLayer {
            alpha = (a.value * 1.5f).coerceAtMost(1f)
            translationY = (1f - a.value) * 18.dp.toPx()
            val r = (1f - a.value) * 9.dp.toPx()
            renderEffect = if (r > 0.6f && android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal) else null
        }.padding(horizontal = 3.dp, vertical = 2.dp),
    )
}

@Composable
private fun SavedMoment(m: AppModel, l: Listening, ink: Color) {
    val mood = l.mood ?: Mood.CALM
    val tone = mood.tone()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(1200.dp)) {
        MoodOrb(mood, 230.dp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(1, 200)) {
            Lamp(tone.a, size = 9.dp)
            Spacer(Modifier.width(6.dp))
            Text((l.owner?.let { "${it}님의 일기에 담았어요" } ?: "오늘의 기록에 담았어요") + "  ·  ${mood.label}", style = ts(18, ink.copy(alpha = 0.92f), FontWeight.Medium))
        }
        Spacer(Modifier.height(20.dp))
        // 방금 들은 말에 길동무가 건네는 한 줄 (알아들은 낱말이 있으면 그 말에 답한다)
        val reply = l.reply ?: Words.saved
        Text(
            reply.keepWords(), style = ts(if (reply.length > 24) 40 else 46, ink, FontWeight.SemiBold, family = Serif, lh = 1.36f).copy(lineBreak = LineBreak.Heading),
            textAlign = TextAlign.Center, modifier = Modifier.rise(2, 200),
        )
        if (reply != Words.saved) {
            Spacer(Modifier.height(16.dp))
            Text(Words.saved, style = ts(27, ink.copy(alpha = 0.90f), family = Serif), modifier = Modifier.rise(3, 360))
        }
        Spacer(Modifier.height(34.dp))
        if (!m.driving) Text("“${Words.headline(l.text)}”".keepWords(), style = ts(28, ink.copy(alpha = 0.88f), family = Serif, lh = 1.45f), textAlign = TextAlign.Center, modifier = Modifier.rise(3, 200))
        // 이 말이 어디에 담겼는지 (내 일기에만 · 길 위에도). 차가 서 있을 때는 이 말 하나만 바꿀 수 있다.
        if (l.owner == null && l.entryId != null) {
            Spacer(Modifier.height(24.dp))
            ShareLine(m, l, ink, Modifier.rise(4, 420))
        }
        if (l.offerWrap) {
            Spacer(Modifier.height(30.dp))
            HLine(Modifier.width(460.dp), ink.copy(alpha = 0.28f))
            Spacer(Modifier.height(20.dp))
            Text("오늘 하루를 마무리할까요, 한마디 더 할까요?", style = ts(21, ink.copy(alpha = 0.88f)), modifier = Modifier.rise(4, 500))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.rise(5, 560)) {
                Btn("오늘 하루 마무리", glyph = G.Moon, kind = BtnKind.Primary, big = true) { m.endDay() }
                Btn("한마디 더", glyph = G.Mic, big = true) { m.startListening() }
                Btn("닫기", glyph = G.Close, big = true) { m.closeListening() }
            }
        }
    }
}

/** 담은 말이 어디에 있는지: 내 일기에만 / 길 위에도 / (AI 필터가 막아) 길 위에는 올리지 않음 */
@Composable
private fun ShareLine(m: AppModel, l: Listening, ink: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val (lamp, text) = when {
            l.shared != null -> C.Accent to "길 위에도 남겼어요  ·  ${l.shared}"
            l.blocked != null -> C.Amber to "길 위에는 올리지 않았어요  ·  ${l.blocked}"
            else -> ink.copy(alpha = 0.72f) to "내 일기에만 담았어요"
        }
        Lamp(lamp, size = 9.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = ts(20, ink.copy(alpha = 0.92f), FontWeight.Medium))
        val id = l.entryId
        if (!m.driving && l.blocked == null && id != null) {
            Spacer(Modifier.width(20.dp))
            if (l.shared != null) Btn("길 위에서 내리기", glyph = G.Close, glass = true) { m.setShared(id, false) }
            else Btn("길 위에도 남기기", glyph = G.Pin, glass = true) { m.setShared(id, true) }
        }
    }
}

/** 오늘 하루를 일기장에 덮는 순간: 창밖이 천천히 밤으로 저문다 */
@Composable
private fun ClosingOverlay(m: AppModel) {
    val now = remember(m.clockOverride) { m.clock() }
    val today = m.entries.filter { Store.dayKey(it.time) == Store.dayKey(now.timeInMillis) }
    val night = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(350); night.animateTo(1f, tween(3200, easing = EaseInOutCubic)) }
    LaunchedEffect(Unit) { delay(400); line.animateTo(1f, tween(3200, easing = LinearEasing)) }
    val ink = C.Hi
    SceneStage(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {},
        scene = Look.scene, part = Look.part(now), rain = m.car.raining, speed = 0.1f, night = night.value, soft = 0.55f,
    ) {
        Box(Modifier.fillMaxSize().background(veil(DayPart.NIGHT, 0f to 0.34f, 0.45f to 0.26f, 1f to 0.72f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center).width(1100.dp)) {
            Num("%02d.%02d".format(now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)), 112, ink, Modifier.rise(0), shadow = true)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(1)) {
                Lamp(C.Accent, size = 8.dp)
                Spacer(Modifier.width(6.dp))
                Text("오늘을 마무리했어요", style = ts(19, ink.copy(alpha = 0.92f), FontWeight.Medium).onScene())
            }
            Spacer(Modifier.height(16.dp))
            Text(Words.dayTitle(now, today).substringAfter(", "), style = ts(58, ink, FontWeight.SemiBold, family = Serif, lh = 1.3f).onScene(), textAlign = TextAlign.Center, modifier = Modifier.rise(2, 120))
            Spacer(Modifier.height(12.dp))
            Text(Words.dayLine(today), style = ts(26, ink.copy(alpha = 0.90f), family = Serif).onScene(), textAlign = TextAlign.Center, modifier = Modifier.rise(3, 200))
            Spacer(Modifier.height(34.dp))
            if (today.isNotEmpty()) {
                DayArc(today, Modifier.width(640.dp).rise(4, 280), labels = false)
                Spacer(Modifier.height(34.dp))
            }
            // 일기장에 담기는 동안, 가는 빛 한 줄이 차오른다
            Canvas(Modifier.size(440.dp, 6.dp)) {
                val y = size.height / 2
                drawLine(ink.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                if (line.value > 0.005f) drawLine(C.Accent, Offset(0f, y), Offset(size.width * line.value, y), 2.dp.toPx(), StrokeCap.Round)
            }
            Spacer(Modifier.height(14.dp))
            Text("일기장에 담고 있어요", style = ts(15, ink.copy(alpha = 0.78f)).onScene(), modifier = Modifier.rise(5, 360))
        }
    }
}

// ================================================================ 다른 목소리

@Composable
private fun MismatchSheet(m: AppModel, mm: VoiceMismatch) {
    val me = m.current ?: return
    val other = mm.suggested
    val clock = LocalClock.current
    val now = remember(m.clockOverride) { m.clock() }
    SceneStage(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {},
        scene = Look.scene, part = Look.part(now), rain = m.car.raining, soft = 1f, dim = 0.52f,
    ) {
        Column(Modifier.align(Alignment.Center).width(1120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(me.tone(), me.initial(), 124.dp, dim = true)
                    Spacer(Modifier.height(10.dp))
                    Text(me.name, style = ts(17, C.Low, FontWeight.Medium))
                }
                // 두 목소리 사이를 건너가는 빛
                Canvas(Modifier.size(280.dp, 60.dp)) {
                    val n = 22
                    val pitch = size.width / n
                    val tone = other?.tone()?.a ?: C.Hi
                    for (k in 1 until n) {
                        val ph = ((clock.value * 0.9f) - k / n.toFloat() + 2f) % 1f
                        val b = (1f - ph * 3f).coerceIn(0f, 1f)
                        drawCircle(if (b > 0.02f) tone.copy(alpha = b) else C.Off, pitch * (0.14f + 0.12f * b), Offset(pitch * k, size.height / 2))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (other != null) Avatar(other.tone(), other.initial(), 124.dp) else Avatar(Tone(Color(0xFF8E97AA), Color(0xFF596274)), "?", 124.dp, unknown = true)
                    Spacer(Modifier.height(10.dp))
                    Text(other?.name ?: "처음 듣는 목소리", style = ts(17, C.Mid, FontWeight.Medium))
                }
            }
            Spacer(Modifier.height(34.dp))
            Text(if (other != null) "혹시 ${other.name}님이신가요?" else "처음 듣는 목소리예요", style = ts(50, C.Hi, FontWeight.SemiBold, family = Serif))
            Spacer(Modifier.height(10.dp))
            Text("지금 목소리가 ${me.name}님과 조금 달라요. 방금 이야기는 누구의 일기에 담을까요?", style = ts(23, C.Hi.copy(alpha = 0.84f)), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text("“${mm.text}”".keepWords(), style = ts(34, C.Hi, family = Serif, lh = 1.4f).copy(lineBreak = LineBreak.Heading), textAlign = TextAlign.Center)
            Spacer(Modifier.height(38.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (other != null) {
                    Btn("${other.name}님 일기에 담기", glyph = G.Check, kind = BtnKind.Primary, big = true) { m.resolveMismatch(other) }
                    Btn("${me.name}님 일기에 그대로", big = true) { m.resolveMismatch(me) }
                } else {
                    Btn("${me.name}님 일기에 그대로", glyph = G.Check, kind = BtnKind.Primary, big = true) { m.resolveMismatch(me) }
                }
                Btn("담지 않기", glyph = G.Close, kind = BtnKind.Ghost, big = true) { m.mismatch = null }
            }
            if (other == null) {
                Spacer(Modifier.height(20.dp))
                Text("새로운 분이라면, 첫 화면 오른쪽 위의 이름 동그라미를 눌러 새 길동무를 만들 수 있어요.", style = ts(19, C.Hi.copy(alpha = 0.72f)))
            }
        }
    }
}
