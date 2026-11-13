package com.gildongmu.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 결 — 운전하는 사람마다 고르는 바탕의 결.
 *  - 맑게: 장면을 있는 그대로 맑게
 *  - 필름: 고운 입자와 살짝 바랜 빛
 *  - 먹: 빛깔을 걷어 낸, 먹빛 종이 위의 농담
 * 화면의 뼈대(글·유리·구슬)와 장면은 같고, 그 위에 입히는 결만 그 사람의 것으로 바뀐다.
 */
enum class Art(val label: String, val line: String) {
    AURA("맑게", "장면을 있는 그대로 맑게"),
    GRAIN("필름", "고운 입자와 살짝 바랜 빛"),
    INK("먹", "빛깔을 걷어 낸 먹빛의 농담");

    companion object {
        /** 예전에 저장된 이름도 받아 준다 */
        fun from(name: String?): Art = when (name) {
            "GRAIN", "CLAY", "CUTE", "CRAYON" -> GRAIN
            "INK", "PAPER" -> INK
            else -> AURA
        }
    }
}

/** 면·글·선의 색과 모양 */
class Tokens(
    val night: Color, val panel: Color, val raised: Color, val line: Color, val lineSoft: Color, val off: Color,
    val hi: Color, val accentInk: Color,
    /** 모서리 반지름: 작게·중간·크게 (작은 조작은 알약 모양, 큰 면은 크게 굴린다) */
    val rS: Dp, val rM: Dp, val rL: Dp,
    /** 사람의 말을 명조체로 쓸지 */
    val serif: Boolean,
) {
    val mid = hi.copy(alpha = 0.82f)
    val low = hi.copy(alpha = 0.64f)
    val faint = hi.copy(alpha = 0.14f)
}

private fun c(v: Long) = Color(v)

/** 빛 위에 얹는 유리빛: 면은 옅은 흰빛, 글은 따뜻한 흰색 */
private val GLASS = Tokens(
    night = c(0xFF080A0F), panel = c(0x14FFFFFF), raised = c(0x24FFFFFF), line = c(0x26FFFFFF), lineSoft = c(0x14FFFFFF), off = c(0x1FFFFFFF),
    hi = c(0xFFF8F5EF), accentInk = c(0xFF15120C),
    rS = 100.dp, rM = 100.dp, rL = 44.dp, serif = true,
)

object Skin {
    /** 지금의 결 (운전하는 사람을 고르면 그 사람의 결로 바뀐다) */
    var art by mutableStateOf(Art.AURA)

    val t: Tokens get() = GLASS
    fun of(a: Art): Tokens = GLASS

    /** 중심 색은 지금의 빛깔에서 온다 */
    fun accent(season: Season, a: Art = art): Color = Look.accent

    /** 글자는 늘 빛 위의 따뜻한 흰색 */
    fun darkScene(part: DayPart, a: Art = art): Boolean = true
    fun ink(part: DayPart, a: Art = art): Color = GLASS.hi
    fun veil(part: DayPart, a: Art = art): Color = c(0xFF06080C)
}
