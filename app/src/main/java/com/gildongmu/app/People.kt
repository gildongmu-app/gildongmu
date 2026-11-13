package com.gildongmu.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Calendar

// ================================================================ 오늘은 누가 운전하나요?

/** 첫 화면: 화면 가득한 창밖 위에서, 운전하는 사람을 고른다 */
@Composable
fun ProfileScreen(m: AppModel) {
    var creating by remember { mutableStateOf(m.profiles.isEmpty()) }
    val now = remember(m.clockOverride) { m.clock() }
    val part = Look.part(now)
    val soft by animateFloatAsState(if (creating) 1f else 0f, tween(700, easing = EaseInOutCubic), label = "soft")
    val dim by animateFloatAsState(if (creating) 0.44f else 0f, tween(700), label = "dim")
    val ink = C.Hi
    SceneStage(Modifier.fillMaxSize(), scene = Look.scene, part = part, rain = m.car.raining, soft = soft, dim = dim, seed = 3) {
        Box(Modifier.fillMaxSize().background(veilH(0f to 0.38f, 0.50f to 0.12f, 0.85f to 0f)))
        Box(Modifier.fillMaxSize().background(veil(part, 0f to 0.28f, 0.24f to 0f, 0.48f to 0.04f, 1f to 0.66f)))
        // 이름을 적느라 키보드가 올라오면 화면이 절반쯤으로 좁아진다. 그때는 머리글을 걷고, 만들기 칸을 위에서부터 놓는다
        // (아래에 붙여 둔 채로는 위로 밀려 올라가 머리글과 겹치고 제목이 잘린다).
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val tight = maxHeight < 900.dp
        val head by animateFloatAsState(if (tight) 0f else 1f, tween(140), label = "head")
        Row(Modifier.padding(start = 56.dp, top = 44.dp).alpha(head), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(72.dp).rise(0))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("길동무", style = TextStyle(fontFamily = Myeongjo, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, color = ink, letterSpacing = 0.04.em).onScene(0.5f), modifier = Modifier.rise(0))
                Spacer(Modifier.height(4.dp))
                Text("운전길에 말로 쓰는 일기", style = ts(22, ink.copy(alpha = 0.92f), family = Serif).onScene(0.6f), modifier = Modifier.rise(1))
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 66.dp, end = 64.dp).alpha(head).rise(2), verticalAlignment = Alignment.CenterVertically) {
            Lamp(C.Accent, size = 8.dp)
            Spacer(Modifier.width(6.dp))
            Text("${Look.scene.label} · ${part.label}", style = ts(15, ink.copy(alpha = 0.90f), FontWeight.Medium).onScene(0.6f))
        }
        AnimatedContent(
            targetState = creating,
            transitionSpec = { fadeIn(tween(480, delayMillis = 100)) togetherWith fadeOut(tween(220)) },
            label = "create", modifier = Modifier.fillMaxSize(),
        ) { cr ->
            Box(Modifier.fillMaxSize()) {
                if (cr) Box(Modifier.align(if (tight) Alignment.TopStart else Alignment.BottomStart).padding(top = if (tight) 26.dp else 0.dp)) { CreateProfile(m, onDone = { creating = false }, canCancel = m.profiles.isNotEmpty()) }
                else Box(Modifier.align(Alignment.TopStart).padding(top = 232.dp)) { Picker(m) { creating = true } }
            }
        }
        }
    }
}

@Composable
private fun Picker(m: AppModel, onNew: () -> Unit) {
    val ink = C.Hi
    Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 76.dp)) {
        RevealText("오늘은 누가 운전하나요?", ts(68, ink, FontWeight.SemiBold, family = Serif, lh = 1.3f).copy(letterSpacing = (-0.012).em).onScene(), delayMs = 160)
        Spacer(Modifier.height(10.dp))
        Text("길동무는 운전하는 사람마다 따로 일기를 써요.", style = ts(24, ink.copy(alpha = 0.92f)).onScene(0.6f), modifier = Modifier.rise(1, 300))
        Spacer(Modifier.height(54.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            m.profiles.forEachIndexed { i, p ->
                Column(Modifier.rise(2 + i).pressable { m.choose(p, welcome = true) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(p.tone(), p.initial(), 140.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(p.name, style = ts(27, ink, FontWeight.SemiBold).onScene(0.6f))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(if (p.voice != null) G.Wave else G.Mic, ink.copy(alpha = 0.74f), 15.dp, 1.6.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (p.voice != null) "목소리를 기억해요" else "목소리 등록 전", style = ts(17, ink.copy(alpha = 0.84f), FontWeight.Medium).onScene(0.6f))
                    }
                }
            }
            Column(Modifier.rise(2 + m.profiles.size).pressable(onClick = onNew), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(140.dp).frost(CircleShape, tint = C.Raised), contentAlignment = Alignment.Center) { Glyph(G.Plus, ink, 40.dp, 2.2.dp) }
                Spacer(Modifier.height(16.dp))
                Text("새 길동무", style = ts(27, ink.copy(alpha = 0.92f), FontWeight.SemiBold).onScene(0.6f))
                Spacer(Modifier.height(4.dp))
                Text("함께 타는 사람 더하기", style = ts(17, ink.copy(alpha = 0.84f), FontWeight.Medium).onScene(0.6f))
            }
        }
    }
}

@Composable
private fun CreateProfile(m: AppModel, onDone: () -> Unit, canCancel: Boolean) {
    // 차의 운전자 이름을 알 수 있으면 미리 넣어 둔다 (첫 계정일 때만)
    var name by remember { mutableStateOf(if (m.profiles.isEmpty()) m.carUserName.orEmpty().take(8) else "") }
    var color by remember { mutableIntStateOf(m.profiles.size % AvatarTones.size) }
    var consent by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf<FloatArray?>(null) }
    var status by remember { mutableStateOf("") }
    var art by remember { mutableStateOf(Skin.art) }
    val before = remember { Skin.art }
    val tone = AvatarTones[color]
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 64.dp)) {
        Text("새 길동무를 만들어요", style = ts(46, C.Hi, FontWeight.SemiBold, family = Serif))
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
            Avatar(tone, name.take(1).ifBlank { "길" }, 168.dp)
            Column(Modifier.width(540.dp)) {
                Tag("이름", C.Low)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = name, onValueChange = { name = it.take(8) },
                    textStyle = ts(40, C.Hi, FontWeight.SemiBold), singleLine = true, cursorBrush = SolidColor(tone.a),
                    // 이름을 다 적고 키보드의 완료(엔터)를 누르면 키보드가 내려간다
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) Text("어떻게 불러 드릴까요?", style = ts(30, C.Low, family = Serif))
                            inner()
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(tone.a, tone.a.copy(alpha = 0.10f))), CircleShape))
                Spacer(Modifier.height(22.dp))
                // 길동무는 처음에 나와 다른 성별의 목소리로 말을 건넨다 (설정에서 바꿀 수 있다)
                Tag("나는", C.Low)
                Spacer(Modifier.height(10.dp))
                Choice(listOf("M" to "남성", "F" to "여성", "" to "고르지 않을래요"), gender) { gender = it }
                Spacer(Modifier.height(8.dp))
                Text("길동무는 ${if (gender == "F") "남성" else "여성"} 목소리로 말을 건네요. 나중에 설정에서 바꿀 수 있어요.", style = ts(14, C.Low))
                Spacer(Modifier.height(20.dp))
                Tag("나의 결", C.Low)
                Spacer(Modifier.height(12.dp))
                ArtPicker(art, size = 92.dp) { art = it; Skin.art = it }
                Spacer(Modifier.height(26.dp))
                Tag("나의 빛", C.Low)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    AvatarTones.forEachIndexed { i, tn ->
                        val on = i == color
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable { color = i }) {
                            Box(
                                Modifier.size(52.dp).border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape).padding(5.dp)
                                    .clip(CircleShape).background(Brush.linearGradient(listOf(tn.a, tn.b)))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(AvatarToneNames[i], style = ts(12, if (on) C.Hi else C.Low, FontWeight.Medium))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("목소리를 기억해 둘까요?", style = ts(24, C.Hi, FontWeight.SemiBold))
                        Spacer(Modifier.height(4.dp))
                        Text("다른 사람이 일기를 남기면, 누구의 일기에 담을지 물어볼 수 있어요. 녹음은 남기지 않고, 목소리 특징을 나타내는 숫자만 이 차 안에 저장해요.", style = ts(16, C.Mid, lh = 1.55f))
                    }
                    Spacer(Modifier.width(22.dp))
                    Switch(consent) { consent = it }
                }
                AnimatedVisibility(consent, enter = fadeIn(tween(300)) + expandVertically(tween(380)), exit = fadeOut(tween(200)) + shrinkVertically(tween(300))) {
                    Column(Modifier.padding(top = 26.dp).fillMaxWidth()) {
                        HLine()
                        Spacer(Modifier.height(22.dp))
                        Tag("이 문장을 소리 내어 읽어 주세요", C.Low)
                        Spacer(Modifier.height(8.dp))
                        Text("“오늘도 같은 길, 다른 마음. 길동무와 함께 달려요.”", style = ts(25, C.Hi, family = Serif, lh = 1.45f))
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("읽어 주기", glyph = G.Mic) {
                                status = "listening"
                                m.enroll { sig -> voice = sig; status = if (sig != null) "목소리를 기억했어요" else "조금 더 길게 읽어 줄래요?"; m.listening = Listening() }
                            }
                            m.demoDir.listFiles()?.filter { it.name.startsWith("enroll") }?.sortedBy { it.name }?.forEach { f ->
                                Btn("시연 · ${f.nameWithoutExtension.removePrefix("enroll_")}", kind = BtnKind.Ghost) {
                                    status = "listening"
                                    m.enroll(f) { sig -> voice = sig; status = if (sig != null) "목소리를 기억했어요" else "목소리를 읽지 못했어요"; m.listening = Listening() }
                                }
                            }
                            if (status.isNotBlank() && status != "listening") {
                                val ok = voice != null
                                Spacer(Modifier.width(6.dp))
                                Lamp(if (ok) C.Green else C.Amber, size = 8.dp)
                                Text(status, style = ts(15, if (ok) C.Green else C.Amber, FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(42.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("이 이름으로 시작하기", glyph = G.Arrow, kind = BtnKind.Primary, big = true, tone = tone.a) {
                if (name.isNotBlank()) {
                    val p = m.createProfile(name, art.name, color, if (consent) voice else null, gender)
                    onDone(); m.choose(p, welcome = true)
                }
            }
            if (canCancel) Btn("돌아가기", big = true) { Skin.art = before; onDone() }
        }
    }
}

// ================================================================ 설정

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(m: AppModel) {
    val s = m.settings
    Column(Modifier.fillMaxSize()) {
        Text("설정", style = ts(48, C.Hi, FontWeight.SemiBold, family = Serif), modifier = Modifier.rise(0))
        Spacer(Modifier.height(2.dp))
        Text("길동무가 언제, 어떻게 곁에 있을지 정해요.", style = ts(19, C.Mid), modifier = Modifier.rise(1))
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(84.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(38.dp)) {
                Group("바탕", Modifier.rise(2)) {
                    Text("화면 뒤의 장면은 그 자리에서 그려져요. 계절·시각·날씨와 날짜를 따라 그날의 장면이 정해지고, 비가 오면(와이퍼) 비 오는 창이 깔려요. 마음에 드는 장면에 머물 수도 있어요.", style = ts(15, C.Low, lh = 1.5f))
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(132.dp, 96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(alpha = 0.30f), androidx.compose.foundation.shape.RoundedCornerShape(26.dp))) {
                            Scenery(Modifier.fillMaxSize(), part = Look.partNow, seed = 5, still = true, scene = Look.scene)
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(if (s.scene == "auto") "그날의 장면  ·  지금은 ${Look.scene.label}" else Look.scene.label, style = ts(21, C.Hi, FontWeight.SemiBold))
                            Text(Look.scene.line, style = ts(15, C.Low))
                            Spacer(Modifier.height(12.dp))
                            Btn("바탕 고르기", glyph = G.Scene, kind = BtnKind.Primary) { Sheets.scenePicker = true }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("결", style = ts(17, C.Mid, FontWeight.Medium), modifier = Modifier.width(64.dp))
                        Choice(Art.entries.map { it to it.label }, Skin.art) { m.setArt(it) }
                        Spacer(Modifier.width(14.dp))
                        Text(Skin.art.line, style = ts(14, C.Low))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("계절", style = ts(17, C.Mid, FontWeight.Medium), modifier = Modifier.width(64.dp))
                        Choice(listOf("auto" to "자동") + Season.entries.map { it.name to it.label }, s.season) { m.updateSettings(s.copy(season = it)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시각", style = ts(17, C.Mid, FontWeight.Medium), modifier = Modifier.width(64.dp))
                        Choice(listOf("auto" to "자동") + DayPart.entries.map { it.name to it.label }, s.dayPart) { m.updateSettings(s.copy(dayPart = it)) }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("달리는 동안에는 눈길을 끌지 않도록, 바탕이 초점을 풀고 느리게만 흘러요. 차창 밖처럼 흐르게 하거나 아예 흐르지 않게 할 수도 있어요.", style = ts(15, C.Low, lh = 1.5f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("달릴 때", style = ts(17, C.Mid, FontWeight.Medium), modifier = Modifier.width(84.dp))
                        Choice(listOf("calm" to "잔잔하게", "flow" to "흐르게", "still" to "흐르지 않게"), s.driveMotion) { m.updateSettings(s.copy(driveMotion = it)) }
                    }
                }
                Group("말 걸기", Modifier.rise(4)) {
                    SettingRow(G.Mic, "길동무의 목소리", "처음에는 나와 다른 성별의 목소리로 말을 건네요. 여기서 언제든 바꿀 수 있어요.") {}
                    Row(Modifier.padding(start = 68.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Choice(GuideVoice.all, GuideVoice.of(m.current)) { m.setGuide(it) }
                    }
                    HLine(color = C.LineSoft)
                    SettingRow(G.Alone, "혼자 있을 때만 말 걸기", "다른 자리에 누가 앉아 있으면, 길동무는 먼저 묻지 않아요.") {
                        Switch(s.askOnlyWhenAlone) { m.updateSettings(s.copy(askOnlyWhenAlone = it)) }
                    }
                    HLine(color = C.LineSoft)
                    SettingRow(G.Together, "함께 탈 때는 내 글 가리기", "다른 자리에 누가 타고 있으면, 첫 화면에 오늘 남긴 말을 띄우지 않아요.") {
                        Switch(s.hideWhenTogether) { m.updateSettings(s.copy(hideWhenTogether = it)) }
                    }
                    HLine(color = C.LineSoft)
                    SettingRow(G.Wave, "목소리로 일기 주인 확인하기", "다른 목소리가 들리면, 누구의 일기에 담을지 먼저 물어봐요.") {
                        Switch(s.voiceCheck) { m.updateSettings(s.copy(voiceCheck = it)) }
                    }
                    HLine(color = C.LineSoft)
                    SettingRow(G.Ask, "하루에 먼저 묻는 횟수", "길동무가 조용히 기다리는 날도 있어야 하니까요.") {
                        Choice(listOf(1 to "1번", 2 to "2번", 3 to "3번"), s.maxAsksPerDay) { m.updateSettings(s.copy(maxAsksPerDay = it)) }
                    }
                    HLine(color = C.LineSoft)
                    SettingRow(G.Lock, "녹음은 남기지 않아요", "글과 목소리 특징값만, 운전하는 사람마다 따로 이 차 안에 저장해요.") {}
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(38.dp)) {
                Group("내가 한 말", Modifier.rise(3)) {
                    SettingRow(G.Pin, "말한 것을 남기는 곳", "‘길 위에도 남기기’를 고르면, 일기에 담은 말이 말한 그 자리에도 이름 없이 남아 그곳을 지나는 운전자에게 보여요.") {}
                    Row(Modifier.padding(start = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                        Choice(listOf("diary" to "내 일기에만", "road" to "길 위에도 남기기"), s.share) { m.updateSettings(s.copy(share = it)) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.padding(start = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                        Lamp(C.Accent, size = 7.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("욕설이나 거친 말은 담지 않아요. 다듬어서 다시 말해 달라고 청해요. 남에게 보이는 자리에 올라가는 말은 AI 필터를 거쳐, 개인 정보와 광고는 알아서 걸러 내 일기에만 담아요.", style = ts(15, C.Low, lh = 1.5f))
                    }
                }
                Group("길동무 계정", Modifier.rise(3)) {
                    m.profiles.forEachIndexed { i, p ->
                        if (i > 0) HLine(color = C.LineSoft)
                        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.tone(), p.initial(), 60.dp)
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = ts(22, C.Hi, FontWeight.SemiBold))
                                Text("${p.art().label}  ·  " + if (p.voice != null) "목소리를 기억해요" else "목소리 등록 전", style = ts(14, C.Low))
                            }
                            // 일기까지 함께 지워지므로, 한 번 더 눌러야 지워진다 (4초 안에)
                            var sure by remember(p.id) { mutableStateOf(false) }
                            androidx.compose.runtime.LaunchedEffect(sure) { if (sure) { kotlinx.coroutines.delay(4000); sure = false } }
                            Btn(if (sure) "일기까지 모두 지울까요?" else "지우기", glyph = G.Trash, kind = BtnKind.Danger) { if (sure) m.deleteProfile(p) else sure = true }
                        }
                    }
                }
                var demoOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                Group("시연 도구", Modifier.rise(4)) {
                    // 처음 화면 다시 보기: 심사·시연 때 몇 번이고 볼 수 있게 (펼치지 않아도 보인다)
                    Text("처음 켰을 때의 시작 화면과, 차에 탔을 때의 환영 화면을 다시 볼 수 있어요.", style = ts(17, C.Mid, lh = 1.5f))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("시작 화면부터 보기", glyph = G.Arrow, kind = BtnKind.Primary) { m.showLaunch() }
                        Btn("환영 화면 보기", glyph = G.Spark) { m.replayWelcome() }
                    }
                    Spacer(Modifier.height(22.dp))
                    HLine(color = C.LineSoft)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("에뮬레이터에서 실제 운행을 대신해 보는 도구예요. 심사·시연 때만 펼쳐 쓰세요.", style = ts(17, C.Mid, lh = 1.5f), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(16.dp))
                        Btn(if (demoOpen) "접기" else "펼치기", glyph = if (demoOpen) G.Minus else G.Plus) { demoOpen = !demoOpen }
                    }
                    if (!demoOpen) return@Group
                    Spacer(Modifier.height(16.dp))
                    fun at(h: Int, mi: Int) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("평일 8시 반 출근 기록") { m.seedTrips() }
                        Btn("지난 2주 예시 일기") { m.seedEntries() }
                        Btn("질문 횟수 초기화") { m.current?.let { m.store.resetAsks(it.id) } }
                        Btn("혼자 탄 상황", glyph = if (m.car.simulateAlone == true) G.Check else G.Alone) {
                            m.car.simulateAlone = if (m.car.simulateAlone == true) null else true; m.carTick++
                        }
                        Btn("비 오는 날", glyph = if (m.car.simulateRain == true) G.Check else G.Rain) {
                            m.car.simulateRain = if (m.car.simulateRain == true) null else true; m.carTick++
                        }
                        Btn("이른 출발 6:50") { m.think("depart", now = at(6, 50)); m.screen = Screen.Today }
                        Btn("늦은 밤 23:40") { m.think("arrive", now = at(23, 40)); m.screen = Screen.Today }
                        Btn("먼 길 끝 2시간 30분") { m.think("arrive", tripMinutes = 150, now = at(15, 10)); m.screen = Screen.Today }
                        Btn("잠시 머무름") { m.think("idle"); m.screen = Screen.Today }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Lamp(C.Green, size = 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("최근 판단  ·  ${m.askWhy.ifBlank { "아직 없음" }}", style = ts(15, C.Mid))
                    }
                    val demos = m.demoDir.listFiles()?.filter { it.extension == "pcm" && !it.name.startsWith("enroll") }.orEmpty().sortedBy { it.name }
                    if (demos.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Tag("시연 음성 (마이크 대신)", C.Low)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 파일 이름 대신 사람이 읽을 이름으로 (예: 2_도현_퇴근길 → 도현 · 퇴근길)
                            demos.forEach { f -> Btn(f.nameWithoutExtension.substringAfter('_').replace("_", " · "), glyph = G.Wave) { m.screen = Screen.Today; m.startListening(f) } }
                        }
                    }
                }
                Group("만든 이", Modifier.rise(5)) { MakerSeal() }
            }
        }
    }
}

/** 설정의 한 묶음: 판 없이, 작은 제목과 넉넉한 여백으로만 나눈다 */
@Composable
private fun Group(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Tag(title, C.Accent)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingRow(g: G, title: String, desc: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).frost(CircleShape, tint = C.Raised), contentAlignment = Alignment.Center) { Glyph(g, C.Hi, 22.dp, 1.8.dp) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(21, C.Hi, FontWeight.SemiBold))
            Text(desc, style = ts(15, C.Low, lh = 1.5f))
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

/** 고를 수 있는 알약 하나 */
@Composable
private fun Pick(label: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) C.Hi.copy(alpha = 0.92f) else C.Raised, tween(220), label = "pick")
    Box(Modifier.pressable(onClick = onClick).background(bg, CircleShape).padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, style = ts(15, if (on) C.AccentInk else C.Mid, FontWeight.Medium))
    }
}

/** 화풍 고르기: 지금 풍경을 그 화풍으로 미리 보여 주는 동그란 창들 */
@Composable
fun ArtPicker(value: Art, modifier: Modifier = Modifier, size: Dp = 104.dp, onPick: (Art) -> Unit) {
    val ctx = LocalContext.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Art.entries.forEach { a ->
            val on = a == value
            val ready = true
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable(enabled = ready) { onPick(a) }) {
                Box(
                    Modifier.size(size).border(if (on) 2.5.dp else 1.dp, if (on) C.Accent else Color.White.copy(alpha = 0.30f), CircleShape).padding(if (on) 5.dp else 0.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Scenery(Modifier.fillMaxSize(), part = Look.scene.light, seed = 5, still = true, art = a, scene = Look.scene)
                    if (!ready) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
                        Text("준비 중", style = ts(12, C.Mid, FontWeight.Medium))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(a.label, style = ts(13, if (on) C.Hi else C.Low, FontWeight.Medium))
            }
        }
    }
}
