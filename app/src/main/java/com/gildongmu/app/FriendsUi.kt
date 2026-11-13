package com.gildongmu.app

import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 친구들의 오늘 — 같은 길동무 앱을 쓰는 친구끼리 오늘 하루를 한마디씩 나누고, 좋아요를 누른다.
 * 주차 중에만 볼 수 있다. (시연: 친구와 한마디는 기기 안 예시, 실제로는 서버로 주고받는다)
 */
@Composable
fun FriendsScreen(m: AppModel) {
    AnimatedContent(
        targetState = m.driving,
        transitionSpec = { fadeIn(tween(360)) togetherWith fadeOut(tween(200)) },
        label = "friends",
    ) { driving -> if (driving) ParkedOnly() else FriendsBody(m) }
}

/** 주행 중: 주차하면 볼 수 있다고 알려 준다 */
@Composable
private fun ParkedOnly() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Orb(280.dp, energy = 0.25f)
                Glyph(G.Park, C.Hi, 46.dp, 2.6.dp)
            }
            Text("주차하면 친구들의 오늘을 볼 수 있어요", style = ts(40, C.Hi, FontWeight.SemiBold, family = Serif))
            Spacer(Modifier.height(10.dp))
            Text("운전 중에는 길 위의 한마디만, 목소리로 알려 드려요.", style = ts(20, C.Mid))
        }
    }
}

@Composable
private fun FriendsBody(m: AppModel) {
    // 한마디를 남기는 동안에는 뒤의 글을 옅게 물려, 아래에 뜨는 작은 창과 겹쳐 읽히지 않게 한다
    val posting by androidx.compose.animation.core.animateFloatAsState(if (m.listening.active && m.listenFriends) 0.12f else 1f, androidx.compose.animation.core.tween(420), label = "posting")
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().graphicsLayer { alpha = posting }, horizontalArrangement = Arrangement.spacedBy(72.dp)) {
            Column(Modifier.width(600.dp).fillMaxHeight()) {
                Text("친구들의 오늘", style = ts(54, C.Hi, FontWeight.SemiBold, family = Serif).copy(letterSpacing = (-0.012).em), modifier = Modifier.rise(0))
                Spacer(Modifier.height(6.dp))
                Text("같은 길동무를 쓰는 친구끼리, 오늘 하루를 한마디씩.", style = ts(22, C.Hi.copy(alpha = 0.80f)), modifier = Modifier.rise(1))
                Spacer(Modifier.height(54.dp))
                MyLine(m, Modifier.rise(2))
                Spacer(Modifier.weight(1f))
                HLine()
                Spacer(Modifier.height(28.dp))
                Roster(m, Modifier.rise(3))
                Spacer(Modifier.height(30.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight().rise(2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("오늘의 한마디", style = ts(22, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
                    Spacer(Modifier.width(14.dp))
                    Num("%02d".format(m.friendLines.size), 32, C.Accent)
                    Spacer(Modifier.weight(1f))
                    Glyph(G.Park, C.Hi.copy(alpha = 0.70f), 22.dp, 1.8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("주차 중에만 보여요", style = ts(18, C.Hi.copy(alpha = 0.74f), FontWeight.Medium))
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(m.friendLines, key = { _, l -> l.id }) { i, l ->
                        LineRow(m, l, i)
                        if (i < m.friendLines.lastIndex) HLine(color = C.LineSoft)
                    }
                }
            }
        }
        ListenSheet(
            m, m.listening.active && m.listenFriends,
            title = "친구들에게 남길 오늘의 한마디", doneTitle = "친구들에게 남겼어요",
            doneSub = "친구만 볼 수 있어요  ·  내 일기에도 담았어요",
            onRetry = { m.postLine() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
        )
    }
}

/** 나의 오늘 한마디 */
@Composable
private fun MyLine(m: AppModel, modifier: Modifier) {
    val me = m.current ?: return
    val line = m.myLine
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(me.tone(), me.initial(), 76.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("나의 오늘 한마디", style = ts(21, C.Hi, FontWeight.SemiBold))
                Text(if (line == null) "아직 남기지 않았어요" else "${Places.ago(line.time, m.clock().timeInMillis)}  ·  ${line.where ?: ""}", style = ts(18, C.Hi.copy(alpha = 0.74f)))
            }
        }
        Spacer(Modifier.height(20.dp))
        AnimatedContent(targetState = line?.id, transitionSpec = { fadeIn(tween(360, delayMillis = 60)) togetherWith fadeOut(tween(160)) }, label = "mine") { id ->
            if (id == null || line == null) Column {
                Text("오늘 하루, 친구들에게 한마디로 들려주세요.", style = ts(28, C.Hi, family = Serif, lh = 1.5f))
                Spacer(Modifier.height(22.dp))
                Btn("말로 남기기", glyph = G.Mic, kind = BtnKind.Primary, big = true) { m.postLine() }
            } else Column {
                Text("“${line.text}”".keepWords(), style = ts(42, C.Hi, family = Serif, lh = 1.42f).copy(letterSpacing = (-0.012).em, lineBreak = LineBreak.Heading))
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 좋아해 준 친구마다 하트에 빛이 하나씩 들어온다
                    m.friends.take(6).forEach { f -> LikerLamp(f.name in m.myLikers); Spacer(Modifier.width(6.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (m.myLikers.isEmpty()) "친구들이 좋아하면 하트에 빛이 들어와요" else "${m.myLikers.joinToString(", ")}님이 좋아해요",
                        style = ts(18, C.Hi.copy(alpha = if (m.myLikers.isEmpty()) 0.70f else 0.92f), FontWeight.Medium), modifier = Modifier.weight(1f),
                    )
                    Btn("다시 남기기", glyph = G.Mic) { m.postLine() }
                }
            }
        }
    }
}

@Composable
private fun LikerLamp(on: Boolean) {
    val p = remember { Animatable(if (on) 1f else 0f) }
    LaunchedEffect(on) { if (on) p.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) else p.snapTo(0f) }
    Canvas(Modifier.size(22.dp, 20.dp).graphicsLayer { val s = 0.8f + 0.2f * p.value; scaleX = s; scaleY = s }) {
        icon(Ic.HEART, center, size.height * 0.52f, if (on) C.Red else C.Off)
    }
}

/** 친구 목록과 친구 추가 */
@Composable
private fun Roster(m: AppModel, modifier: Modifier) {
    val me = m.current ?: return
    var adding by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("친구", style = ts(22, C.Hi.copy(alpha = 0.92f), FontWeight.Medium))
            Spacer(Modifier.width(10.dp))
            Num("%02d".format(m.friends.size), 26, C.Mid)
            Spacer(Modifier.weight(1f))
            Btn(if (adding) "닫기" else "친구 추가", glyph = if (adding) G.Close else G.Plus) { adding = !adding; result = null }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            m.friends.forEach { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(AvatarTones[f.colorIdx.mod(AvatarTones.size)], f.name.take(1), 72.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(f.name, style = ts(18, C.Hi.copy(alpha = 0.86f), FontWeight.Medium))
                }
            }
        }
        AnimatedVisibility(adding, enter = fadeIn(tween(260)) + expandVertically(tween(320)), exit = fadeOut(tween(180)) + shrinkVertically(tween(260))) {
            Column(Modifier.padding(top = 24.dp)) {
                Tag("내 친구 코드", C.Low)
                Spacer(Modifier.height(4.dp))
                Num(Friends.myCode(me.id), 36, C.Accent, weight = FontWeight.Normal)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).frost(CircleShape, tint = C.Raised).padding(horizontal = 24.dp, vertical = 15.dp)) {
                        if (code.isEmpty()) Text("친구 코드 (예: GD-7302)", style = ts(17, C.Low))
                        BasicTextField(code, { code = it.uppercase().take(8) }, textStyle = ts(19, C.Hi, FontWeight.Medium, spacing = 0.08f), cursorBrush = SolidColor(C.Accent), singleLine = true)
                    }
                    Spacer(Modifier.width(10.dp))
                    Btn("추가", glyph = G.Plus, kind = BtnKind.Primary) {
                        val f = m.addFriend(code)
                        result = if (f != null) "${f.name}님과 친구가 되었어요" else "코드를 찾지 못했어요"
                        if (f != null) code = ""
                    }
                }
                result?.let { Text(it, style = ts(14, if (it.contains("친구가")) C.Green else C.Low), modifier = Modifier.padding(top = 10.dp)) }
            }
        }
    }
}

/** 친구의 오늘 한마디 한 줄 */
@Composable
private fun LineRow(m: AppModel, l: FriendLine, index: Int) {
    val f = m.friends.firstOrNull { it.id == l.friendId } ?: return
    val liked = l.id in m.likedLines
    Row(Modifier.fillMaxWidth().rise(index, delayMs = 160).padding(vertical = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(AvatarTones[f.colorIdx.mod(AvatarTones.size)], f.name.take(1), 78.dp)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, style = ts(22, C.Hi, FontWeight.SemiBold))
                Spacer(Modifier.width(14.dp))
                Text("${Places.ago(l.time, System.currentTimeMillis())}${l.where?.let { "  ·  $it" } ?: ""}", style = ts(18, C.Hi.copy(alpha = 0.72f)))
                Spacer(Modifier.width(12.dp))
                MoodDot(l.mood)
            }
            Spacer(Modifier.height(8.dp))
            Text("“${l.text}”".keepWords(), style = ts(32, C.Hi, family = Serif, lh = 1.44f).copy(letterSpacing = (-0.01).em, lineBreak = LineBreak.Heading))
        }
        Spacer(Modifier.width(16.dp))
        HeartButton(liked, l.likes + if (liked) 1 else 0) { m.likeLine(l.id) }
    }
}
