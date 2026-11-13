package com.gildongmu.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 만든 이 표시와 봉인된 서명.
 *  - 화면 아래의 'Made by 한끗 Team': 봉인을 여는 비밀번호를 아는 사람만 가릴 수 있다.
 *  - 봉인된 서명: 만든 사람을 밝힌 글을 비밀번호로 암호화해 넣어 둔 것이다. 비밀번호 없이는 읽을 수도, 다른 글로 바꿔 넣을 수도 없다.
 *    (만드는 도구: tools/maker/seal.py. 비밀번호는 저장소에 두지 않는다.)
 * 코드가 공개되어 있으므로 표시 한 줄을 지우는 것까지 막을 수는 없다. 그래서 지워도 남는 증거를 함께 둔다:
 * 이 봉인(누가 만들었는지를 나중에 증명할 수 있다), 저장소에 올린 날짜, 그리고 LICENSE.
 */
object Maker {
    const val CREDIT = "Made by 한끗 Team"
    const val NOTICE = "© 2026 한끗 · All rights reserved"
    private const val SEAL = "gd1:Mp64g0JQKPoEal+IjOaYugqHLPpgUKwU8uSqpR3n7RgYNP1mj0yoAqDN5rXASiMOn9PTK7s3zpWD+MXDwi2goLUoKZKGNpukFC+3S7RtjYVAWwGc2DPkDzPE0dwxAyvKPMX7cRzR8H5GpB5n0WzRg1nXDJrpUJz+Ow=="
    private const val ROUNDS = 120_000

    /** 화면 아래의 표시를 가렸는지 (비밀번호로 봉인을 연 사람만 바꿀 수 있다) */
    var hidden by mutableStateOf(false); private set
    fun load(context: Context) { hidden = context.getSharedPreferences("maker", Context.MODE_PRIVATE).getBoolean("hidden", false) }
    fun setHidden(context: Context, on: Boolean) { hidden = on; context.getSharedPreferences("maker", Context.MODE_PRIVATE).edit().putBoolean("hidden", on).apply() }

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(key, "HmacSHA256")); parts.forEach { m.update(it) }; return m.doFinal()
    }

    /** 비밀번호가 맞으면 봉인된 글을, 아니면 null 을 돌려준다 (시간이 조금 걸리므로 화면을 그리는 줄에서는 부르지 않는다) */
    fun open(password: String): String? = runCatching {
        val raw = android.util.Base64.decode(SEAL.removePrefix("gd1:"), android.util.Base64.DEFAULT)
        val salt = raw.copyOfRange(0, 16); val ct = raw.copyOfRange(16, raw.size - 32); val tag = raw.copyOfRange(raw.size - 32, raw.size)
        val keys = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.trim().toCharArray(), salt, ROUNDS, 512)).encoded
        val enc = keys.copyOfRange(0, 32); val mac = keys.copyOfRange(32, 64)
        if (!MessageDigest.isEqual(hmac(mac, salt, ct), tag)) return null
        val out = ByteArray(ct.size)
        var j = 0
        while (j * 32 < ct.size) {
            val ks = hmac(enc, salt, byteArrayOf((j ushr 24).toByte(), (j ushr 16).toByte(), (j ushr 8).toByte(), j.toByte()))
            for (i in 0 until 32) { val p = j * 32 + i; if (p < ct.size) out[p] = (ct[p].toInt() xor ks[i].toInt()).toByte() }
            j++
        }
        String(out, Charsets.UTF_8)
    }.getOrNull()
}

/** 화면 아래 구석의 작은 표시 */
@Composable
fun MakerLine(modifier: Modifier = Modifier, alpha: Float = 0.46f) {
    if (!Maker.hidden) Text(Maker.CREDIT, style = ts(14, C.Hi.copy(alpha = alpha), FontWeight.Medium).copy(letterSpacing = 0.08.em), modifier = modifier)
}

/** 설정의 '만든 이': 봉인된 서명을 비밀번호로 열어 보고, 열린 뒤에만 화면 아래의 표시를 가리거나 다시 보이게 할 수 있다 */
@Composable
fun ColumnScope.MakerSeal() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableIntStateOf(0) }      // 0 기다림 · 1 여는 중 · 2 틀림
    Text("${Maker.CREDIT}  ·  ${Maker.NOTICE}", style = ts(19, C.Hi, FontWeight.Medium))
    Spacer(Modifier.height(8.dp))
    Text("이 앱에는 만든 사람의 서명이 암호로 봉인되어 들어 있어요. 비밀번호를 아는 사람만 열어 볼 수 있고, 지우거나 다른 이름으로 바꿔 넣을 수 없어요. 화면 아래의 표시도 봉인을 연 사람만 가릴 수 있어요.", style = ts(16, C.Mid, lh = 1.5f), modifier = Modifier.width(900.dp))
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(360.dp)) {
            BasicTextField(
                value = pw, onValueChange = { pw = it.take(60); state = 0 }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                textStyle = ts(22, C.Hi, FontWeight.Medium), cursorBrush = SolidColor(C.Accent), modifier = Modifier.padding(vertical = 6.dp).width(360.dp),
                decorationBox = { inner -> Box { if (pw.isEmpty()) Text("비밀번호", style = ts(20, C.Low)); inner() } },
            )
            Box(Modifier.align(Alignment.BottomStart).width(360.dp).height(1.5.dp).background(C.Hi.copy(alpha = 0.32f), CircleShape))
        }
        Btn(if (state == 1) "여는 중…" else "서명 열기", glyph = G.Spark) {
            if (state == 1 || pw.isBlank()) return@Btn
            state = 1
            scope.launch {
                val r = withContext(Dispatchers.Default) { Maker.open(pw) }
                opened = r; state = if (r == null) 2 else 0
            }
        }
    }
    if (state == 2) { Spacer(Modifier.height(10.dp)); Text("비밀번호가 맞지 않아요", style = ts(17, C.Amber)) }
    opened?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = ts(24, C.Hi, FontWeight.SemiBold, family = Serif))
        Spacer(Modifier.height(12.dp))
        Btn(if (Maker.hidden) "화면 아래의 표시 다시 보이기" else "화면 아래의 표시 가리기") { Maker.setHidden(ctx, !Maker.hidden) }
    }
}
