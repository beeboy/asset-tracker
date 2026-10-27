package com.naeilo.app.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeColor
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.MeMotion
import com.naeilo.app.ui.MoodScript
import com.naeilo.app.ui.Palette
import com.naeilo.app.ui.PixBuf
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.me.MeArt
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import com.naeilo.app.AppModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

// 새 테마 셋 (2026-10-24): 도리와 문어 · 피노키오와 새 · 철수와 영희(비튼 무궁화)
// 홈 장면(300×170 판) + 종목·흐름 띠·바탕 + 분석 막대 + 작은 등장인물. 기준 시안: 새 홈 3장, 종목·흐름·분석 다크 18장 · 라이트 28장
// 공통: 등장인물 자리 = 목표 대비 지금, 50% 자리에 '계획' 눈금, 끝점 = 목표 금액.
// 장면 그림은 값이 바뀔 때만 다시 그리고, 움직임은 작은 조각만 정해진 박자에 짧게 (그리기 단계에서만 읽는다. 숨은 탭·동작 줄이기면 멈춘다)
// ios: Views/ThemeScenes.swift:1,4

/** 오른 날·떨어진 날 (오늘 등락이 없으면 오른 날처럼 차분하게) */
// ios: Views/ThemeScenes.swift:10,11,12,13
internal fun moodDown(v: Double?): Boolean = (v ?: 0.0) < 0

/** 지금 움직임을 멈춰야 하는지 (동작 줄이기 · 숨은 탭) */
@Composable
internal fun stillNow(): Boolean = LocalReduceMotion.current || !LocalTabActive.current

/** seconds 마다 한 번 0 → 1 로 durMs 동안 돈다 (첫 번은 delay + seconds 뒤). 값은 그리기 단계에서만 읽는다.
 *  Swift Every + keyframeAnimator 자리: 쉬는 동안 0, 도는 동안 0~1 */
// ios: Views/ThemeScenes.swift:16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32
@Composable
internal fun every(seconds: Double, delay: Double = 0.0, durMs: Int, key: Any? = null): () -> Float {
    val a = remember { Animatable(0f) }
    val still = stillNow()
    LaunchedEffect(still, seconds, key) {
        a.snapTo(0f)
        if (still) return@LaunchedEffect
        delay((delay * 1000).toLong())
        while (true) {
            delay((seconds * 1000).toLong())
            a.snapTo(0f); a.animateTo(1f, tween(durMs, easing = LinearEasing)); a.snapTo(0f)
        }
    }
    return { a.value }
}

/** 꺾은선 사이 값: (때 0~1, 값) 점들을 차례로 잇는다 */
internal fun pw(t: Float, vararg k: Pair<Float, Float>): Float {
    if (t <= k.first().first) return k.first().second
    for (i in 1 until k.size) {
        val (t1, v1) = k[i]; val (t0, v0) = k[i - 1]
        if (t <= t1) return v0 + (v1 - v0) * ((t - t0) / max(1e-6f, t1 - t0))
    }
    return k.last().second
}

/** 눈 깜빡임 세로 배율 (1 = 뜬 눈) */
// ios: Views/ThemeScenes.swift:35,36,37,38,39,40,41,42,43,44,45,46,47
internal fun blinkY(t: Float): Float = pw(t, 0f to 1f, 0.32f to 0.08f, 0.6f to 0.08f, 1f to 1f)
internal const val blinkMs = 250

/** 장(한국 평일 9:00~15:30, 미국 평일 9:30~16:00 뉴욕) 중 하나라도 열렸는지. 휴장일은 따지지 않는다 */
// ios: Views/ThemeScenes.swift:57,58,59,60,61,62,63,64,65,66
internal object MarketClock {
    fun open(): Boolean {
        fun inside(tz: String, a: Int, b: Int): Boolean {
            val d = ZonedDateTime.now(ZoneId.of(tz))
            if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) return false
            val m = d.hour * 60 + d.minute
            return m in a until b
        }
        return inside("Asia/Seoul", 540, 930) || inside("America/New_York", 570, 960)
    }
}

/** 상단 박스 밝기 모드: 자동 · 밝게 · 어둡게. 상단 박스에만 덮어씌우고 앱 전체 모드는 그대로 */
// ios: Views/ThemeScenes.swift:71,72,73,74,75,76,77,78,79,80
enum class BoxScheme {
    system, light, dark;
    val label: String get() = when (this) { system -> "자동"; light -> "밝게"; dark -> "어둡게" }
    companion object {
        const val key = "homeBoxScheme"
        private val state by lazy { mutableStateOf(Defaults.prefs.getString(key, null) ?: system.name) }
        var raw: String
            get() = state.value
            set(v) { state.value = v; Defaults.prefs.edit().putString(key, v).apply() }
    }
}

/** 상단 박스(홈·종목·흐름·분석)에 고른 밝기 모드를 덮어씌운다 */
// ios: Views/ThemeScenes.swift:83,84,85,86,87,88,89,92,93,94
@Composable
fun BoxSchemeScope(content: @Composable () -> Unit) {
    when (BoxScheme.entries.firstOrNull { it.name == BoxScheme.raw } ?: BoxScheme.system) {
        BoxScheme.light -> CompositionLocalProvider(LocalPal provides Palette.Light, content = content)
        BoxScheme.dark -> CompositionLocalProvider(LocalPal provides Palette.Dark, content = content)
        BoxScheme.system -> content()
    }
}

/** 지금 박스가 어두운 쪽인지 (덮어쓴 모드 포함) */
@Composable
internal fun boxDark(): Boolean = LocalPal.current.isDark

/** 상단 박스 색 한 쌍 (라이트, 다크). 모양·자리·움직임은 한 벌을 같이 쓰고 색만 고른다 */
// ios: Views/ThemeScenes.swift:103,104
internal fun bx(dk: Boolean, light: Long, dark: Long, la: Float = 1f, da: Float = la): Color = if (dk) hex(dark, da) else hex(light, la)

/** 상단 박스 글자·띠 색 (라이트 시안 28장 · 다크 시안) */
// ios: Views/ThemeScenes.swift:112,113,114,115,116,117,118,119,120
class BoxInk(val dk: Boolean) {
    val ink = bx(dk, 0x16181D, 0xFFFFFF)
    val sub = bx(dk, 0x5F6670, 0xC9D0D6)
    val dim = bx(dk, 0x5F6670, 0x8A93A6)
    val up = bx(dk, 0xC0392B, 0xFF8A80); val down = bx(dk, 0x1B5E96, 0x8CC4F2)
    val gold = bx(dk, 0x9A6A00, 0xF2C14E); val good = bx(dk, 0x1F8577, 0x3FB3A3)
    val upF = bx(dk, 0xF08A7E, 0xFF8A80); val downF = bx(dk, 0x6FB0E6, 0x8CC4F2); val flatF = bx(dk, 0xC3CAD3, 0x4A5672)
    /** 진행 막대(무늬 없는) 박스 바탕 · 라이트 테두리 */
    val card = bx(dk, 0xFFFFFF, 0x1E2A36); val cardRim = bx(dk, 0xE1E5EA, 0xFFFFFF, 1f, 0f)
    companion object { val Light = BoxInk(false); val Dark = BoxInk(true) }
}

@Composable
fun boxInk(): BoxInk = if (boxDark()) BoxInk.Dark else BoxInk.Light

private fun c(v: Long, o: Float = 1f) = hex(v, o)
private fun c(dk: Boolean, l: Long, d: Long, o: Float = 1f) = hex(if (dk) d else l, o)
private fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx, cy, x, y)
private fun oval(x: Float, y: Float, r: Float) = Path().apply { addOval(Rect(x - r, y - r, x + r, y + r)) }
private fun poly(vararg p: Pair<Float, Float>) = Path().apply { p.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }

// 그림 조각 (각자 제 원점 기준 좌표, 시안 SVG 그대로)
// ios: Views/ThemeScenes.swift:129,130
internal object ThemeArt {
    /** 도리 (오른쪽을 본다). 꼬리·눈은 따로 움직일 때 뺀다 */
    // ios: Views/ThemeScenes.swift:132,133,134,135,136,137,138,139,140,141,142,143
    fun DrawScope.dory(dk: Boolean = true, tail: Boolean = true, eye: Boolean = true) {
        if (tail) doryTail(dk)
        drawOval(c(0x2F6FE0), Offset(-15f, -10f), Size(30f, 20f))
        val st = Path().apply { moveTo(-9f, -7f); q(-2f, -2f, -6f, 4f); q(2f, 2f, 6f, -6f) }
        drawPath(st, c(0x141B2B), style = Stroke(2.4f, cap = StrokeCap.Round))
        val fin = Path().apply { moveTo(-3f, -9f); q(2f, -14f, 9f, -9f) }
        drawPath(fin, c(0x2F6FE0)); drawPath(fin, c(0x141B2B), style = Stroke(1.2f))
        drawPath(Path().apply { moveTo(2f, 6f); q(6f, 10f, 2f, 12f) }, c(dk, 0xFFC21F, 0xFFD23F))
        if (eye) doryEye(Offset(9f, -2f))
        drawPath(Path().apply { moveTo(12f, 4f); q(14f, 5f, 15f, 3f) }, c(0x141B2B), style = Stroke(1f, cap = StrokeCap.Round))
    }
    // ios: Views/ThemeScenes.swift:145,146,147
    fun DrawScope.doryTail(dk: Boolean = true) = drawPath(poly(-14f to 0f, -22f to -7f, -20f to 0f, -22f to 7f), c(dk, 0xFFC21F, 0xFFD23F))
    // ios: Views/ThemeScenes.swift:149,150,151,152
    fun DrawScope.doryEye(o: Offset) {
        drawPath(oval(o.x, o.y, 4f), Color.White)
        drawPath(oval(o.x + 1, o.y, 2.2f), c(0x1D2330))
        drawPath(oval(o.x + 1.8f, o.y - 0.8f, 0.7f), Color.White)
    }
    /** 수족관 문어 몸 (다리·눈 빼고). down = 빨개진 문어, 아니면 벽 색 점선으로 숨은 문어 */
    // ios: Views/ThemeScenes.swift:156,157,158,159
    fun DrawScope.octoBody(down: Boolean, dk: Boolean = true) {
        drawOval(c(if (down) 0xFF5A4F else 0x9FD6EE), Offset(-13f, -16f), Size(26f, 24f))
        drawOval(c(if (down) (if (dk) 0xFF8A80 else 0xFF7A6E) else 0xBFE9FB), Offset(-13f, -16f), Size(26f, 24f),
            style = Stroke(1f, pathEffect = if (down) null else PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
    }
    // ios: Views/ThemeScenes.swift:161,162,163,164,165,166,167,168
    fun DrawScope.octoArms(down: Boolean, dk: Boolean = true) {
        val a = Path().apply {
            moveTo(-11f, 2f); q(-15f, 12f, -20f, 14f); moveTo(-6f, 4f); q(-8f, 14f, -11f, 18f); moveTo(0f, 5f); q(0f, 15f, 1f, 19f)
            moveTo(6f, 4f); q(9f, 14f, 13f, 16f); moveTo(11f, 2f); q(16f, 10f, 20f, 11f)
        }
        drawPath(a, c(if (down) (if (dk) 0xFF8A80 else 0xFF7A6E) else 0xBFE9FB),
            style = Stroke(3.5f, cap = StrokeCap.Round, pathEffect = if (down) null else PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
    }
    /** 문어 눈: 평소 작은 점 눈, 떨어진 날 놀란 흰 눈 */
    // ios: Views/ThemeScenes.swift:171,172,173,174,175,176
    fun DrawScope.octoEyes(down: Boolean) {
        if (down) for (x in listOf(-5f, 5f)) { drawPath(oval(x, -4f, 4f), Color.White); drawPath(oval(x, -4f, 1.8f), c(0x1D2330)) }
        else for (x in listOf(-4.5f, 4.5f)) drawPath(oval(x, -3f, 1.6f), c(0x1D2330))
    }
    // ios: Views/ThemeScenes.swift:178,179,180,181,182,183
    fun DrawScope.octoMouth(down: Boolean) {
        if (down) drawOval(c(0x7A1A14), Offset(-2.5f, 1f), Size(5f, 6f))
        else drawPath(Path().apply { moveTo(-2f, 3f); q(0f, 4.5f, 2f, 3f) }, c(0x1D2330), style = Stroke(1f, cap = StrokeCap.Round))
    }
    /** 시드니 오페라하우스 (왼쪽 위 원점, 폭 52). 라이트는 옅은 외곽선 */
    // ios: Views/ThemeScenes.swift:187,188,189,190,191,192,193,194,195
    fun DrawScope.opera(dk: Boolean = true) {
        drawRect(c(dk, 0x8A97B0, 0x6D7A94), Offset(-4f, 18f), Size(52f, 5f))
        val shells = listOf(
            Triple(Path().apply { moveTo(2f, 18f); q(8f, 2f, 16f, 0f); q(14f, 10f, 16f, 18f); close() }, 0xF4F1EA, 0xFFFFFF),
            Triple(Path().apply { moveTo(14f, 18f); q(22f, 0f, 32f, -3f); q(29f, 9f, 31f, 18f); close() }, 0xFBF9F4, 0xFFFFFF),
            Triple(Path().apply { moveTo(29f, 18f); q(36f, 6f, 44f, 5f); q(41f, 12f, 43f, 18f); close() }, 0xE9E4D8, 0xF3EFE6))
        for ((p, d, l) in shells) {
            drawPath(p, c((if (dk) d else l).toLong()))
            if (!dk) drawPath(p, c(0x9AA6BD), style = Stroke(0.8f))
        }
    }
    /** 새 (발 = 원점, 왼쪽을 본다). laugh = 깔깔 웃는 얼굴. wing = 날개 각도 (음수면 위로 편다) */
    // ios: Views/ThemeScenes.swift:199,200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224
    fun DrawScope.bird(laugh: Boolean, wing: Float = 0f) {
        val legs = Path().apply { moveTo(-2f, 0f); lineTo(-2f, -2.4f); moveTo(1.6f, 0f); lineTo(1.6f, -2.4f); moveTo(-3.4f, 0f); lineTo(-0.8f, 0f); moveTo(0.4f, 0f); lineTo(3f, 0f) }
        drawPath(legs, c(0xFF9F2E), style = Stroke(1f, cap = StrokeCap.Round))
        drawPath(poly(6f to -7.5f, 12f to -11f, 11f to -6.8f, 13f to -4.4f, 6.5f to -4.5f), c(0x2F6FB8))
        drawOval(c(0x5FB2F2), Offset(-7.8f, -15.8f), Size(15.6f, 14.8f))
        drawOval(c(0xEAF6FF), Offset(-6.4f, -10.2f), Size(9.6f, 8.4f))
        val w = Path().apply { moveTo(1.4f, -10.4f); q(7f, -11.2f, 7.6f, -5.6f); q(3.6f, -5f, 1.4f, -7.4f); close() }
        rotate(wing, pivot = Offset(1.4f, -9f)) { drawPath(w, c(0x3E8FD6)) }
        drawPath(Path().apply { moveTo(-2f, -15.4f); q(-1f, -18.4f, 1f, -17.8f); q(-0.6f, -17f, 0f, -15.4f); close() }, c(0x3E8FD6))
        drawPath(oval(-4.6f, -7f, 1.6f), c(0xFF8F9A, 0.6f))
        if (laugh) {
            drawPath(Path().apply { moveTo(-5f, -10.4f); q(-3.4f, -12.8f, -1.8f, -10.4f) }, c(0x1D2330), style = Stroke(1.3f, cap = StrokeCap.Round))
            drawPath(poly(-7.4f to -10.4f, -11.4f to -10.8f, -7.4f to -8.8f), c(0xFF9F2E))
            drawPath(poly(-7.4f to -8.2f, -10.6f to -6.6f, -7.4f to -6.8f), c(0xE8851E))
        } else {
            drawPath(oval(-3.2f, -10.6f, 1.5f), c(0x1D2330)); drawPath(oval(-3.7f, -11.1f, 0.5f), Color.White)
            drawPath(poly(-7.4f to -9.6f, -11f to -8.3f, -7.4f to -7f), c(0xFF9F2E))
        }
    }
    // ios: Views/ThemeScenes.swift:227,228,229,230,231,232,233
    fun DrawScope.leaf(light: Boolean = false, dk: Boolean = true) {
        val p = Path().apply {
            moveTo(0f, 0f)
            if (light) { q(-1f, -5f, 4f, -6f); q(4f, -2f, 0f, 0f) } else { q(-2f, -6f, 3f, -8f); q(4f, -3f, 0f, 0f) }
            close()
        }
        drawPath(p, c(if (light) 0x74C06C else if (dk) 0x5FAE5A else 0x4E9A49))
    }
    /** 옛 동화책 피노키오 (발끝 사이 = (0, 152) 기준 좌표). up = 새에게 손을 뻗는다, 아니면 배를 잡고 깔깔 */
    // ios: Views/ThemeScenes.swift:236
    fun DrawScope.pino(up: Boolean, dk: Boolean = true) {
        // ios: Views/ThemeScenes.swift:237,238,239,240
        fun line(ps: List<Pair<Float, Float>>, col: Long, w: Float) {
            val p = Path().apply { ps.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) } }
            drawPath(p, c(col), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        line(listOf(-3.5f to 120f, -6f to 126f, -6.5f to 131.5f), 0xB88450, 3f)   // 뒷팔
        drawPath(oval(-6.6f, 132.4f, 2f), c(0xE8C193))
        drawRoundRect(c(0xD29A60), Offset(-5.2f, 138f), Size(3.4f, 12f), CornerRadius(1.5f))   // 다리
        drawRoundRect(c(0xDCA66B), Offset(1.4f, 138f), Size(3.4f, 12f), CornerRadius(1.5f))
        drawPath(oval(-3.5f, 144f, 1.5f), c(0xA8743F)); drawPath(oval(3.1f, 144f, 1.5f), c(0xA8743F))
        drawPath(Path().apply { moveTo(-8f, 152.6f); q(-8f, 148.6f, -3.4f, 148.8f); q(1f, 149f, 1.4f, 152.6f); close() }, c(0x5A3A20))
        drawPath(Path().apply { moveTo(-1.2f, 152.6f); q(-1f, 148.6f, 3.6f, 148.8f); q(8.6f, 149.2f, 9.4f, 152.6f); close() }, c(0x6B4728))
        drawPath(poly(-8f to 132.5f, 8f to 132.5f, 8.6f to 139.5f, 1f to 139.5f, 0f to 137f, -1f to 139.5f, -8.6f to 139.5f), c(0xC8453A))   // 반바지
        drawPath(Path().apply { moveTo(-6.5f, 117f); q(-9.5f, 125f, -9f, 134f); lineTo(9f, 134f); q(9.5f, 125f, 6.5f, 117f); close() }, c(0xE9B949))   // 꽃무늬 웃옷
        for ((x, y) in listOf(-4f to 123f, 1.5f to 127f, -5f to 130.5f, 5.5f to 121.5f)) { drawPath(oval(x, y, 1.1f), c(0xD2523F)); drawPath(oval(x, y, 0.4f), c(0xFFF4D8)) }
        drawRoundRect(c(0x7A4A24), Offset(-9f, 131.5f), Size(18f, 2.4f), CornerRadius(1f))
        // 주름 깃 (라이트: 흰 깃 + 옅은 외곽선)
        for ((x, y) in listOf(-7f to 117.4f, -3.5f to 118.4f, 0f to 118.8f, 3.5f to 118.4f, 7f to 117.4f)) {
            drawPath(oval(x, y, 2.6f), c(dk, 0xFFFFFF, 0xFBF4E4))
            if (!dk) drawPath(oval(x, y, 2.6f), c(0xE2D3B0), style = Stroke(0.5f))
        }
        // 앞팔
        if (up) {
            line(listOf(2.5f to 120.5f, 8.5f to 121.5f, 14.5f to 119.5f), 0xDCA66B, 3.2f)
            drawPath(oval(8.5f, 121.5f, 1.4f), c(0xA8743F)); drawPath(oval(2.5f, 120.5f, 3.6f), c(0xE9B949)); drawPath(oval(15.8f, 119.2f, 2.3f), c(0xF2CFA0))
        } else {
            line(listOf(2.5f to 120.5f, 6.5f to 125.5f, 1.5f to 129f), 0xDCA66B, 3.2f)
            drawPath(oval(6.5f, 125.5f, 1.4f), c(0xA8743F)); drawPath(oval(2.5f, 120.5f, 3.6f), c(0xE9B949)); drawPath(oval(0.8f, 129.4f, 2.3f), c(0xF2CFA0))
        }
        // 머리
        drawPath(Path().apply { moveTo(-12.5f, 99f); q(-16.5f, 104f, -13.5f, 109f); q(-11.5f, 108f, -10.5f, 105f); q(-11.5f, 108f, -8.9f, 110f); q(-8.3f, 106f, -7.5f, 104f); close() }, c(0x4A2E18))
        drawPath(oval(0f, 103f, 12f), c(0xF2CFA0))
        if (!dk) drawPath(oval(0f, 103f, 12f), c(0xD9AE7C), style = Stroke(0.6f))
        drawPath(Path().apply { moveTo(-12f, 104f); q(-11f, 112f, -4f, 114.6f); q(2f, 116f, 7f, 112.6f) }, c(0xE2B17C), style = Stroke(1f))
        drawOval(c(0xE6B986), Offset(-7.6f, 100.9f), Size(5.2f, 7.2f))
        drawPath(Path().apply { moveTo(-5.6f, 103f); q(-4.2f, 104.5f, -5.6f, 106f) }, c(0xC99560), style = Stroke(0.7f))
        drawPath(oval(4.5f, 109f, 3.2f), c(0xFF8F7A, 0.5f))
        // 빳빳한 종이 고깔
        val hat = poly(-13.4f to 98.6f, 10.6f to 90.4f, -23f to 79.5f)
        drawPath(hat, c(dk, 0xFFFFFF, 0xF1E6C8)); if (!dk) drawPath(hat, c(0xD9CAA3), style = Stroke(0.6f))
        drawPath(poly(-13.4f to 98.6f, -1.4f to 94.5f, -23f to 79.5f), c(dk, 0xE6D9B6, 0xD9CAA3))
        drawPath(poly(-13.8f to 98.4f, 10.8f to 90f, 11.6f to 92.6f, -13.2f to 101f), c(0xC9433A))
        // 얼굴
        if (up) {
            drawPath(Path().apply { moveTo(2.4f, 96.2f); q(5f, 94.8f, 7.8f, 96.6f) }, c(0x5A3A20), style = Stroke(1.1f, cap = StrokeCap.Round))
            drawOval(Color.White, Offset(2.6f, 97.4f), Size(5.2f, 6.4f))
            drawPath(oval(6.4f, 100.8f, 1.8f), c(0x1D2330)); drawPath(oval(7f, 100f, 0.6f), Color.White)
            drawPath(Path().apply { moveTo(5f, 110.6f); q(7.6f, 109.7f, 10f, 110.9f) }, c(0x7A3A24), style = Stroke(1.1f, cap = StrokeCap.Round))
        } else {
            drawPath(Path().apply { moveTo(2.4f, 95.4f); q(5f, 93.6f, 7.8f, 95.4f) }, c(0x5A3A20), style = Stroke(1.1f, cap = StrokeCap.Round))
            drawPath(Path().apply { moveTo(2.6f, 101f); q(5.2f, 97.6f, 7.8f, 101f) }, c(0x1D2330), style = Stroke(1.5f, cap = StrokeCap.Round))
            drawPath(Path().apply { moveTo(4f, 108.6f); q(7.5f, 115f, 11.6f, 108.4f); close() }, c(0x7A1F16))
            drawPath(Path().apply { moveTo(6f, 111.6f); q(8.2f, 113.4f, 10.2f, 111.6f) }, c(0xFF8F7A))
        }
    }
    /** EXIT 출구 문 (작은 것: 18×22) */
    // ios: Views/ThemeScenes.swift:318,319,320,321,322,323
    fun DrawScope.miniExit(tm: TextMeasurer) {
        drawRoundRect(c(0x1D6B45), Offset(1.5f, 6f), Size(15f, 16f), CornerRadius(1f))
        drawRoundRect(c(0x0F3D27), Offset(1.5f, 6f), Size(15f, 16f), CornerRadius(1f), style = Stroke(1f))
        drawRect(c(0xC8FFD9), Offset(3.5f, 8f), Size(11f, 14f))
        drawRoundRect(c(0x0F3D27), Offset(0f, 0f), Size(18f, 5.5f), CornerRadius(1f))
        Sk.text(this, tm, "EXIT", 9f, 2.9f, c(0x7DFFAE), size = 4f, bold = true, anchor = Anchor.center)
    }
}

// 도트 인물: 영희(선배 얼굴 + 영희 옷) · 기훈(내 캐릭터 얼굴 + 녹색 체육복)
// ios: Views/ThemeScenes.swift:327,328
object MugArt {
    enum class Eyes { open, closed, peek }   // ios: Views/ThemeScenes.swift:329
    /** 영희 30×35 도트 (뜬 눈 · 감은 눈 · 한쪽 눈) */
    // ios: Views/ThemeScenes.swift:331
    private val yh = mapOf(
        Eyes.open to "iVBORw0KGgoAAAANSUhEUgAAAB4AAAAjCAYAAACD1LrRAAABpklEQVR42mNgGCDASIpiLTm9//jkrz26xEh1i7Xk9P5vm6KDU/7GjUcMRVM+Ee0ARmpYCrMYBoqmfCJoORM1LGVgYGDQ0JAjKY6ZqGEpOujL4SOYHpiomVJJ8TULqYaLKYThlHt6/RYDA8Mj6lqMz0JyANNAFSAkWxySUUWU2PDx8ZoZbUSJ0czHl3efZri8+zTtKgnkguDMlia8ap9ev8XgX7qEqPKapEoCxt7YHYMiB7OMlBqKkVjLrm6TZGDYdoOBwUuD4eXDp3A14lc/o4g5ZTIR5QAWfJZe3SaJVU5cXhrBuXoDRW7f9H9QB+j9x2c52YkL2dfkAOLL6m03UGhxJN/emnKUgd9XgfrZaVvdl4EvQOhi8fd/mYOnyKRL81ZLTu//1ZyPJBl4695bBgYGBobAbUoMNMlONAtqcnzLwMDAoKYkzMDAwMCw3use3gbf4PKxlpze//Ve9zDijVg2DODzNSMp/SRYtWjiU4dXjJj+FAuxHTB0h1x7dIkRmxhN2tX/5K2IEqNab1FLTu+/oOAfFLH371kYsIlRpSFAy6AeMAAAiD63+nMEWf4AAAAASUVORK5CYII=",
        Eyes.closed to "iVBORw0KGgoAAAANSUhEUgAAAB4AAAAjCAYAAACD1LrRAAABoElEQVR42mNgGCDASIpiLTm9//jkrz26xEh1i7Xk9P5vm6KDU/7GjUcMRVM+Ee0ARmpYCrMYBoqmfCJoORM1LGVgYGDQ0JAjKY6ZqGEpOujL4SOYHpiomVJJ8TULqYaLKYThlHt6/RYDA8Mj6lqMz0JyANNAFSBDw2ITnzoGE586gmJUT1xntjQRJUbToL68+zTD5d2naVdJIBcEhHz39PotBv/SJUSV1yRVEjD2xu4YFDmYZaTUUIzEWnZ1myQDw7YbDAxeGgwvHz6FqxG/+hlFzCmTiSgHsOCz9Oo2Saxy4vLSCM7VGyhy+6b/gzpA7z8+y8lOXMi+JgcQn5223UChxZF8e2vKUQZ+XwXqZ6dtdV9GSJH5/V/m8KskCObjqzkfSTLw1r23DAwMDAyB25QYaJKdaBbU5PiWgYGBQU1JmIGBgYFhvdc9vA2+weVjLTm9/+u97mHEG7FsGMDna0ZS+kmwahG5xYFNjJj+FAuxHTB0h1x7dIkRmxhNmj7/5K2IEqNab1FLTu+/oOAfFLH371kYsIlRpSFAy6AeMAAAaDe5+Jl4bSgAAAAASUVORK5CYII=",
        Eyes.peek to "iVBORw0KGgoAAAANSUhEUgAAAB4AAAAjCAYAAACD1LrRAAABrElEQVR42mNgGCDASIpiLTm9//jkrz26xEh1i7Xk9P5vm6KDU/7GjUcMRVM+Ee0ARmpYCrMYBoqmfCJoORM1LGVgYGDQ0JAjKY6ZqGEpOujL4SOYHpiomVJJ8TULqYaLKYThlHt6/RYDA8Mj6lqMz0JyANNAFSBUsTgko4q2Fpv41DGY+NRRxcckJa4zW5qwiq+Z0Ua/oL68+zTD5d2naVdJIBcEuHyMnJ38S5cQVV6TVEnA2Bu7Y1DkYJaRUkMxEmvZ1W2SDAzbbjAweGkwvHz4FK5G/OpnFDGnTCaiHMCCz9Kr2ySxyonLSyM4V2+gyO2b/g/qAL3/+CwnO3Eh+5q22WnbDRRaHMm3t6YcZeD3VaB+dtpW92VwFplUt/j7v8zBXUlQveTSktP7fzXnI0kG3rr3loGBgYEhcJsSA02yE82CmhzfMjAwMKgpCTMwMDAwrPe6h7fBN7h8rCWn93+91z2MeCOWDQP4fM1ISj8JVi0it0KwiRHTn2IhtgOG7pBrjy4xYhOjSdPnn7wVUWJU6y1qyen9FxT8gyL2/j0LAzYxqjQEaBnUAwYADQ24+g/Tu+4AAAAASUVORK5CYII=",
    )
    /** 기훈 몸 (녹색 체육복, 24×36 · 위 2줄 빈 줄). 머리는 내 캐릭터 것을 쓴다 */
    private const val suit = "iVBORw0KGgoAAAANSUhEUgAAABgAAAAkCAYAAACTz/ouAAAB7klEQVR42mNgGOqAkZACLTm9//jkrz26hNcMFkKGu3uY4pR//uoVAwMDw398lrAQMvzkkcMMCjJSDA+ePGNgYGBAYTMwMDCoaSngtYSRkOGEwIfPXxjUtHUYbl17gTW4mHBpJMZwGLh4/hx5cYAO9qzoQuG7RJQR1MNCioHY5J9ev8XglodbHROt88HAWYAcPKYRDRjyyGJduRGDPIhOr2ggSoxoC649usRo4lPHwMDAwPD37ju4+KkdJxhO7TgB5/+9+47hwu1HDPWzDuEsk1jwFWImPnX/z2xpgouZeVjA2U+v36K8NEUuSTd2x8DFYa4mpkRlxGcwa4MfivjRiDIG3ZZcBj4VWbjYt7tPGVgXn8VpEQs2w9ENxge4lKUZGBqkGb4/eM6gtQCzVMUayVrCUnAXHyWivPn+4Dl5ZZE1gbJocBcVw9cC5CRKtgXztbQYjpuYEG1IJyc3QycnN+k++N2wCSWZoqeqoxFlDL8bNpFWZV57dInRyDkPXkQwMTGjWAjL4d8fPGc4f/4CAwMDA0P19APE52RkhYRadcSURQOfiv79+4tVnFNBkigLCLaLmO7sg6coEwZIHKzh5mNgYGBguHJoDeUW/FNxQuJBajlJ3wwGBgYGhhfnLzAwQCOYKs13bJFPqPk+9AEA5jTD+hEL1A8AAAAASUVORK5CYII="   // ios: Views/ThemeScenes.swift:337
    private val eyeCache = HashMap<Eyes, ImageBitmap?>()
    private data class Key(val me: MeChar, val leg: Int, val lift: Boolean)
    private val cache = HashMap<Key, ImageBitmap?>()

    private fun png(s: String): Bitmap? = runCatching { Base64.decode(s, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()

    // ios: Views/ThemeScenes.swift:344,345,346,347,348,349
    fun younghi(e: Eyes): ImageBitmap? = synchronized(eyeCache) { eyeCache.getOrPut(e) { yh[e]?.let(::png)?.asImageBitmap() } }

    /** 기훈 한 장 (24×36, 위 2줄 빈 줄). 목 위는 내 캐릭터(소품 빼고), 어깨 아래는 체육복. 다리·들썩은 장면 도구 그대로 */
    // ios: Views/ThemeScenes.swift:352,353,354,355,356,357,358,359,360,361,362,363,364,365,366,367,368
    fun gihun(me: MeChar, leg: Int = 0, lift: Boolean = false): ImageBitmap? {
        val key = Key(me, leg, lift)
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val who = me.copy(parts = me.parts + ("prop" to 0))
        val head = MeMotion.image(who) ?: return null
        val body = png(suit) ?: return null
        val w = MeArt.W; val h = MeArt.H; val top = MeMotion.legTop
        var p = PixBuf.read(head, w, h)
        val s = PixBuf.read(body, w, h + 2)
        for (y in 15 until h) for (x in 0 until w) p[y * w + x] = s[(y + 2) * w + x]
        PixBuf.legs(p, w, h, leg, top = top, split = w / 2)
        p = PixBuf.pad(p, w, top = 2)
        if (lift) PixBuf.lift(p, w, until = top + 2)
        val img = PixBuf.image(p, w, h + 2)?.asImageBitmap()
        synchronized(cache) { if (cache.size > 60) cache.clear(); cache[key] = img }
        return img
    }
}

/** 도트 한 장을 판 좌표 (가운데 x, 아래 y) 에 w×h 로 그린다. 보간 없이 (tint = 곱할 색) */
private fun DrawScope.dot(img: ImageBitmap?, cx: Float, bottom: Float, w: Float, h: Float, sx: Float = 1f, sy: Float = 1f, tint: Color? = null) {
    img ?: return
    withTransform({ translate(cx, bottom); scale(sx * w / img.width, sy * h / img.height, pivot = Offset.Zero) }) {
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(-img.width / 2, -img.height), IntSize(img.width, img.height),
            filterQuality = FilterQuality.None, colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) })
    }
}

/** 날아오르는 음표 하나 (t = 0~1 진행). 1.6초 동안 떠올랐다 사라진다 */
// ios: Views/ThemeScenes.swift:382,383,384,385,386,387,388,389,390,391,392,393,394,395,396,397,398,399,400,401,402,403
private fun DrawScope.flyNote(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color, size: Float, t: Float, still: Boolean) {
    val o = if (still) 1f else pw(t, 0f to 0f, 0.174f to 1f, 0.739f to 0.9f, 1f to 0f)
    if (o <= 0f) return
    val dx = if (still) 0f else pw(t, 0f to 0f, 0.435f to 5f, 0.739f to -2f, 1f to 3f)
    val dy = if (still) 0f else pw(t, 0f to 0f, 0.435f to -9f, 0.739f to -16f, 1f to -24f)
    val r = if (still) 0f else pw(t, 0f to -14f, 0.435f to 12f, 0.739f to -10f, 1f to 8f)
    val sc = if (still) 1f else pw(t, 0f to 0.7f, 0.435f to 1f, 1f to 1f)
    val lay = tm.measure(text, TextStyle(fontSize = (size / (density * fontScale)).sp, fontWeight = FontWeight.Bold, color = color), softWrap = false)
    withTransform({ translate(x + dx, y + dy); rotate(r, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
        drawText(lay, topLeft = Offset(-lay.size.width / 2f, -lay.size.height / 2f), alpha = o)
    }
}
private const val noteMs = 1610

// 홈 장면 1 도리와 문어: 왼쪽 수족관(문어)에서 오른쪽 시드니 오페라하우스(목표)까지 헤엄친다
// ios: Views/ThemeScenes.swift:408,409,410,411,412
@Composable
internal fun DoryScene(s: BoxScene) {
    val down = moodDown(s.today); val dk = boxDark()
    val tm = rememberTextMeasurer()
    val dxTo = 62 + 206 * min(1.0, max(0.0, s.f)).toFloat()
    val dx by animateFloatAsState(dxTo, tween(1200), label = "dory")
    val flip by animateFloatAsState(if (down) -1f else 1f, tween(400), label = "flip")
    // ios: Views/ThemeScenes.swift:415
    Plate {
        val all = Size(300f, 170f)
        drawRect(Brush.verticalGradient(listOf(c(dk, 0xB3D9F4, 0x0F1A35), c(dk, 0xF4F9FE, 0x26406F)), 0f, 170f), Offset.Zero, all)
        if (dk) for ((x, y, r) in listOf(Triple(190f, 20f, 0.8f), Triple(230f, 44f, 0.6f), Triple(160f, 36f, 0.5f))) drawPath(oval(x, y, r), c(0xC9D3EA))
        else { drawOval(Color.White, Offset(182f, 25f), Size(36f, 10f)); drawOval(Color.White, Offset(201f, 21f), Size(18f, 10f)) }
        translate(246f, 60f) { with(ThemeArt) { opera(dk) } }
        // 바다
        drawRect(Brush.verticalGradient(listOf(c(dk, 0x6FC0EA, 0x1F78B4), c(dk, 0x2F7FBF, 0x0A3560)), 82f, 170f), Offset(0f, 82f), Size(300f, 88f))
        val ray = if (dk) c(0xBFE9FF) else Color.White
        drawRect(Brush.radialGradient(listOf(ray.copy(alpha = if (dk) 0.25f else 0.4f), ray.copy(alpha = 0f)), Offset(210f, 82f), 200f), Offset(0f, 82f), Size(300f, 88f))
        val wave = Path().apply { moveTo(0f, 82f); for (i in 0 until 15) { val x = i * 20f; q(x + 10, if (i % 2 == 0) 79f else 85f, x + 20, 82f) } }
        drawPath(wave, if (dk) c(0x9FD6EE, 0.7f) else c(0xFFFFFF, 0.8f), style = Stroke(1.2f))
        drawPath(Path().apply { moveTo(0f, 160f); q(60f, 154f, 120f, 160f); q(180f, 166f, 240f, 158f); q(300f, 150f, 300f, 160f); lineTo(300f, 170f); lineTo(0f, 170f); close() }, c(dk, 0xE6CB94, 0xC7A46A))
        for (p in listOf(Path().apply { moveTo(210f, 160f); q(207f, 148f, 211f, 140f); q(214f, 148f, 212f, 160f); close() },
                         Path().apply { moveTo(216f, 160f); q(219f, 150f, 215f, 144f); q(213f, 152f, 216f, 160f); close() },
                         Path().apply { moveTo(120f, 160f); q(118f, 151f, 122f, 146f); q(123f, 153f, 120f, 160f); close() })) drawPath(p, c(dk, 0x3A9A6A, 0x3FAE7A))
        // 25·50·75% 눈금 (도리 자리와 같은 축)
        for ((t, f) in Sk.pctTicks) {
            val x = 62f + 206f * f.toFloat()
            drawPath(Sk.line(Offset(x, 150f), Offset(x, 160f)), c(dk, 0x8A5A00, 0xFFD98A), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
            Sk.text(this, tm, t, x, 165.5f, c(dk, 0x8A5A00, 0xFFD98A), size = 8f, bold = !dk, anchor = Anchor.center)
        }
        Sk.text(this, tm, "출발", 62f, 165.5f, c(dk, 0x8A5A00, 0xFFD98A), size = 8f, bold = !dk, anchor = Anchor.center)
        Sk.text(this, tm, s.target, 268f, 165.5f, c(dk, 0x8A5A00, 0xFFD98A), size = 8.5f, bold = true, anchor = Anchor.center)
        // 수족관: 판 위쪽 30을 잘라 내므로 0.7배로 줄여 글 아래로 내린다 (바닥 왼쪽 모서리 기준, 2026-10-27)
        scale(DoryScene.tankK, DoryScene.tankK, DoryScene.tankPivot) {   // ios: Views/ThemeScenes.swift:453,466,467
            drawRoundRect(if (dk) c(0x9FD6EE, 0.18f) else c(0xFFFFFF, 0.28f), Offset(6f, 90f), Size(44f, 66f), CornerRadius(3f))
            drawRoundRect(c(dk, 0xFFFFFF, 0xCFEFFF), Offset(6f, 90f), Size(44f, 66f), CornerRadius(3f), style = Stroke(1.5f))
            drawRect(if (dk) c(0xCFEFFF, 0.35f) else c(0xFFFFFF, 0.5f), Offset(6f, 90f), Size(44f, 6f))
            for ((x, y, r) in listOf(Triple(14f, 153f, 2.5f), Triple(20f, 154f, 2f), Triple(40f, 153f, 3f))) drawPath(oval(x, y, r), c(dk, 0x8A94A8, 0x7F8AA3))
            drawPath(Path().apply { moveTo(44f, 153f); q(42f, 139f, 46f, 129f); q(48f, 139f, 44f, 153f); close() }, c(dk, 0x3A9A6A, 0x3FAE7A))
        }
    }
    // 움직임: 문어 다리(오른 날 배웅)·눈·느낌표, 도리 헤엄·꼬리·눈·거품
    val arms = every(3.2, 0.6, 1800, down)   // ios: Views/ThemeScenes.swift:468
    val oEye = every(5.6, 1.3, blinkMs)       // ios: Views/ThemeScenes.swift:478
    val excl = every(10.0, 0.0, 800, down)    // ios: Views/ThemeScenes.swift:481
    val swim = Loop(0.8); val wag = Loop(0.35)   // ios: Views/ThemeScenes.swift:508,510
    val dEye = every(4.2, 0.0, blinkMs)       // ios: Views/ThemeScenes.swift:518
    val bubs = listOf(every(2.4, 0.0, 2400), every(2.4, 0.8, 2400), every(2.4, 1.6, 2400))   // ios: Views/ThemeScenes.swift:504,505,506
    Plate(Modifier.graphicsLayer()) {
        // 문어 (25, 132): 수족관과 같이 0.7배
        scale(DoryScene.tankK, DoryScene.tankK, DoryScene.tankPivot) { translate(25f, 132f) {
            with(ThemeArt) {
                rotate(if (down) 0f else pw(arms(), 0f to 0f, 0.25f to 8f, 0.5f to -2f, 0.75f to 8f, 1f to 0f), Offset(0f, 2f)) { octoArms(down, dk) }
                octoBody(down, dk); octoMouth(down)
                withTransform({ scale(1f, blinkY(oEye()), Offset(0f, -4f)) }) { octoEyes(down) }
            }
            if (down) {
                val a = pw(excl(), 0f to 1f, 0.25f to 0.1f, 0.75f to 0.1f, 1f to 1f)
                val lay = tm.measure("!", TextStyle(fontSize = (12f / (density * fontScale)).sp, fontWeight = FontWeight.ExtraBold, color = c(dk, 0xE09A00, 0xFFD23F)), softWrap = false)
                drawText(lay, topLeft = Offset(15.5f - lay.size.width / 2f, -20.5f - lay.size.height / 2f), alpha = a)
            }
        } }
        // 도리: 하락장엔 문어 쪽(왼쪽), 상승장엔 오페라하우스 쪽(오른쪽)
        withTransform({ translate(dx, 130f); scale(flip, 1f, Offset.Zero) }) {   // ios: Views/ThemeScenes.swift:470
            for ((i, b) in bubs.withIndex()) {
                val t = b(); if (t <= 0f) continue
                val (bx0, by0, r) = listOf(Triple(14f, -8f, 1.6f), Triple(16f, -10f, 1.1f), Triple(13f, -7f, 1.3f))[i]
                drawCircle(c(0xCFEFFF), r, Offset(bx0, by0 - 26f * t), alpha = pw(t, 0f to 0f, 0.2f to 0.8f, 1f to 0f), style = Stroke(0.8f))
            }
            val on = swim()
            withTransform({ translate(0f, -2f * on); rotate(-3f * on, Offset.Zero) }) {
                with(ThemeArt) {
                    withTransform({ scale(1f, 1f - 0.4f * wag(), Offset(-14f, 0f)) }) { doryTail(dk) }
                    dory(dk, tail = false, eye = false)
                    withTransform({ scale(1f, blinkY(dEye()), Offset(9f, -2f)) }) { doryEye(Offset(9f, -2f)) }
                }
            }
        }
    }
}

private object DoryScene {
    const val tankK = 0.7f; val tankPivot = Offset(6f, 156f)   // ios: Views/ThemeScenes.swift:467
}

// 홈 장면 2 피노키오와 새: 제페토 공방 벽. 코가 오른쪽으로 자라고(길이 = 진행), 새는 늘 코끝
// ios: Views/ThemeScenes.swift:549,550,551,552,553,554
@Composable
internal fun PinoScene(s: BoxScene) {
    val up = !moodDown(s.today); val dk = boxDark()
    val tm = rememberTextMeasurer()
    val tip by animateFloatAsState(64 + 226 * min(1.0, max(0.0, s.f)).toFloat(), tween(900, easing = EaseOut), label = "tip")
    Plate {
        drawRect(Brush.verticalGradient(listOf(c(dk, 0xEBD6B4, 0x22170E), c(dk, 0xFBF2E3, 0x4A331D)), 0f, 170f), Offset.Zero, Size(300f, 170f))
        for (x in listOf(70f, 140f, 210f, 280f)) drawPath(Sk.line(Offset(x, 0f), Offset(x, 152f)), if (dk) c(0x3A2816, 0.8f) else c(0xD7BC94), style = Stroke(1f))
        // 벽에 걸린 연장 그림자
        val tool = c(if (dk) 0x1A110A else 0xC9A877, 0.55f)
        drawRoundRect(tool, Offset(226f, 40f), Size(3f, 22f), CornerRadius(1f)); drawRoundRect(tool, Offset(221f, 58f), Size(13f, 6f), CornerRadius(2f))
        drawPath(poly(252f to 40f, 255f to 40f, 255f to 56f, 259f to 62f, 248f to 62f, 252f to 56f), tool)
        drawCircle(tool, 6f, Offset(198f, 48f), style = Stroke(2f))
        val lamp = c(if (dk) 0xFFD58A else 0xFFCE78)
        drawRect(Brush.radialGradient(listOf(lamp.copy(alpha = if (dk) 0.34f else 0.45f), lamp.copy(alpha = 0f)), Offset(42f, 119f), 150f), Offset.Zero, Size(300f, 170f))
        // 바닥 띠는 뺀다 (2026-10-27)   // ios: Views/ThemeScenes.swift:588
        // 25·50·75% 눈금 (코끝 자리와 같은 축) · 끝 = 목표
        val mark = c(dk, 0x8A5A00, 0xFFD98A); val label = c(dk, 0x6E4600, 0xFFD98A)
        for ((t, f) in Sk.pctTicks) {
            val x = 64f + 226f * f.toFloat()
            drawPath(Sk.line(Offset(x, 146f), Offset(x, 158f)), mark, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
            Sk.text(this, tm, t, x, 164.5f, label, size = 8f, bold = !dk, anchor = Anchor.center)
        }
        Sk.text(this, tm, "출발", 64f, 164.5f, label, size = 8f, bold = !dk, anchor = Anchor.center)
        drawPath(Sk.line(Offset(290f, 146f), Offset(290f, 158f)), mark, style = Stroke(1.5f))
        Sk.text(this, tm, s.target, 290f, 164f, label, size = 8.5f, bold = true, anchor = Anchor.trailing)
    }
    // 몸·코·새 한 덩어리: 가끔 발끝(42, 152)을 축으로 짧게 들썩이고, 새는 날개로 균형을 잡는다
    // ios: Views/ThemeScenes.swift:586,587,588,589,590,591
    val sway = every(if (up) 4.2 else 3.6, 1.0, if (up) 1200 else 900, up)
    val notesP = listOf(every(7.0, 2.0, noteMs), every(7.0, 2.35, noteMs), every(7.0, 2.7, noteMs))
    val slow = every(9.0, 4.0, noteMs)
    val still = stillNow()
    val note = c(dk, 0xB07A00, 0xFFD98A)
    // 바닥 띠를 뺀 만큼 내려 발끝을 판 아래 끝에 둔다 (2026-10-27)
    Plate(Modifier.graphicsLayer()) { translate(0f, 16f) {   // ios: Views/ThemeScenes.swift:600
        val t = sway()
        val th = if (up) pw(t, 0f to 0f, 1 / 6f to 0.2f, 2 / 6f to 0.4f, 3 / 6f to 0.45f, 4 / 6f to 0.32f, 5 / 6f to 0.12f, 1f to 0f)
                 else pw(t, 0f to 0f, 1 / 6f to 0.4f, 2 / 6f to -0.4f, 3 / 6f to 0.35f, 4 / 6f to -0.3f, 5 / 6f to 0.2f, 1f to 0f)
        rotate(th, Offset(42f, 152f)) {
            val len = tip - 52
            // 코: 밑동 52 에서 tip 까지
            val nose = Path().apply { moveTo(52f, 101.8f); lineTo(tip, 103f); q(tip + 1.8f, 103.5f, tip, 104.1f); lineTo(52f, 105.4f); close() }
            drawPath(nose, Brush.verticalGradient(0f to c(0xECBC82), 0.55f to c(0xD49A5E), 1f to c(0xB47B44), startY = 101.8f, endY = 105.4f))
            if (!dk) drawPath(nose, c(0x9C6A38), style = Stroke(0.4f))
            drawPath(Sk.line(Offset(53f, 102.7f), Offset(max(53f, tip - 3), 103.3f)), c(0xF6D3A3), style = Stroke(0.7f, cap = StrokeCap.Round))
            // 코가 길어지면 잎이 돋는다
            with(ThemeArt) {
                if (len > 95) translate(52 + len * 0.42f, 102.8f) { leaf(dk = dk) }
                if (len > 170) withTransform({ translate(52 + len * 0.7f, 103f); scale(1f, -1f, Offset.Zero) }) { leaf(light = true) }
                translate(42f, 0f) { pino(up, dk) }
            }
            if (!up) for (i in 0..2) flyNote(tm, listOf("♪", "♫", "♪")[i], listOf(55f, 59f, 53f)[i], listOf(89f, 91f, 92f)[i], note, 8f, notesP[i](), still)
            // 코끝 새: 몸이 들썩일 때 날개를 펴고 몸이 살짝 밀렸다 돌아온다
            val wing = pw(t, 0f to 0f, 0.19f to -40f, 0.38f to -12f, 0.57f to -34f, 0.76f to -6f, 1f to 0f)
            val bob = pw(t, 0f to 0f, 0.2f to -1.5f, 0.47f to 0.8f, 0.73f to -0.5f, 1f to 0f)
            translate(tip - 5, 102.6f + bob) { with(ThemeArt) { bird(!up, wing) } }
            if (up) flyNote(tm, "♪", tip - 14, 83.6f, note, 8f, slow(), still)
            else for (i in 0..2) flyNote(tm, listOf("♪", "♫", "♬")[i], tip - 5 + listOf(9f, 13f, 6f)[i], 102.6f - listOf(17f, 20f, 20f)[i],
                if (dk) c(0xFFE6A8) else note, 8.5f, notesP[i](), still)
        }
    } }
}

// 홈 장면 3 철수와 영희 (비튼 무궁화): 왼쪽 영희 눈을 피해 오른쪽 EXIT 로 간다
// ios: Views/ThemeScenes.swift:672,673,674,675,676,677,678,679,680
@Composable
internal fun MugScene(s: BoxScene) {
    val down = moodDown(s.today); val dk = boxDark()
    val tm = rememberTextMeasurer()
    // 다크: 하늘·땅 밖 인물·사물을 남색으로 살짝 눌러 어두운 하늘에 녹인다 (도트 원본은 그대로)
    val dim = if (dk) c(0xC8D0E0) else Color.White
    fun m(col: Color) = Color(col.red * dim.red, col.green * dim.green, col.blue * dim.blue, col.alpha)
    val gx = 68 + 212 * min(1.0, max(0.0, s.f)).toFloat()
    var eyes by remember { mutableStateOf(MugArt.Eyes.open) }
    val still = stillNow()
    // 장중엔 눈 뜨고 가끔 감고, 장 마감·주말엔 감고 가끔 한쪽 눈
    // ios: Views/ThemeScenes.swift:729,730,731,732,733,734,735,736,737,738,739,740
    LaunchedEffect(still) {
        while (true) {
            val shut = !MarketClock.open()
            eyes = if (shut) MugArt.Eyes.closed else MugArt.Eyes.open
            if (still) { delay(60_000); continue }
            delay(if (shut) 5200 else 4200)
            eyes = if (shut) MugArt.Eyes.peek else MugArt.Eyes.closed
            delay(if (shut) 1300 else 1800)
        }
    }
    Plate {
        // 다크 하늘·땅은 종목·흐름·분석 바탕과 같은 밝기
        drawRect(Brush.verticalGradient(listOf(c(dk, 0xA9D6F6, 0x14304F), c(dk, 0xE4F3FD, 0x2C5F8F)), 0f, 170f), Offset.Zero, Size(300f, 170f))
        drawRect(c(dk, 0xE2C595, 0x8F7550), Offset(0f, 136f), Size(300f, 34f))
        drawRect(c(dk, 0xC9A673, 0x7A6243), Offset(0f, 136f), Size(300f, 2.5f))
        // 구름은 위쪽 (기훈 머리와 겹치지 않게)
        for ((x, y, rx, ry) in listOf(listOf(210f, 30f, 22f, 7f), listOf(224f, 25f, 12f, 7f), listOf(168f, 62f, 18f, 5f), listOf(178f, 58f, 9f, 5f)))
            drawOval(m(if (dk) c(0xE9F3FB, 0.6f) else Color.White), Offset(x - rx, y - ry), Size(rx * 2, ry * 2))
        for (r in listOf(Rect(96f, 150f, 99f, 151.5f), Rect(180f, 158f, 184f, 159.5f), Rect(236f, 147f, 238f, 148.5f))) drawRect(m(c(dk, 0xC9A673, 0x6E583A, if (dk) 0.8f else 0.6f)), r.topLeft, r.size)
        // 25·50·75% 눈금 (기훈 자리와 같은 축). 다크 흙 위에선 금색
        for ((t, f) in Sk.pctTicks) {
            val x = 68f + 212f * f.toFloat()
            drawPath(Sk.line(Offset(x, 140f), Offset(x, 152f)), m(c(dk, 0x7A4A10, 0xFFD98A)), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
            Sk.text(this, tm, t, x, 159.5f, m(c(dk, 0x7A4A10, 0xFFD98A)), size = 8f, bold = !dk, anchor = Anchor.center)
        }
        Sk.text(this, tm, "출발", 68f, 159.5f, m(c(dk, 0x7A4A10, 0xFFD98A)), size = 8f, bold = !dk, anchor = Anchor.center)
        Sk.text(this, tm, s.target, 280f, 159.5f, m(c(dk, 0x7A4A10, 0xFFD98A)), size = 8.5f, bold = true, anchor = Anchor.center)
        // 영희 앞 분필 선 (사선)
        drawPath(Sk.line(Offset(64f, 137.5f), Offset(50f, 170f)), m(if (dk) c(0xFBF6EA, 0.9f) else Color.White), style = Stroke(2.6f, cap = StrokeCap.Square))
        // 왼쪽 끝 나무
        drawRect(m(c(dk, 0x7A5634, 0x6B4A2B)), Offset(-1f, 94f), Size(6f, 44f))
        drawPath(oval(0f, 90f, 13f), m(c(dk, 0x3A8048, 0x2F6B3A))); drawPath(oval(8f, 98f, 6f), m(c(dk, 0x4A9356, 0x3A7A45)))
        // 출구
        drawRoundRect(m(c(0x1D6B45)), Offset(268f, 96f), Size(28f, 42f), CornerRadius(2f))
        drawRoundRect(m(c(0x0F3D27)), Offset(268f, 96f), Size(28f, 42f), CornerRadius(2f), style = Stroke(2f))
        drawRect(m(c(0xC8FFD9)), Offset(271f, 99f), Size(22f, 39f))
        drawRoundRect(m(c(0x0F3D27)), Offset(267f, 84f), Size(30f, 9f), CornerRadius(2f))
        Sk.text(this, tm, "EXIT", 282f, 88.5f, m(c(0x7DFFAE)), size = 7f, bold = true, anchor = Anchor.center)
    }
    // 출구 빛 (오른 날엔 조금 빨리)
    val glow = Loop(if (down) 1.2 else 0.7)   // ios: Views/ThemeScenes.swift:714
    val songs = listOf(every(1.5, 0.0, noteMs, eyes), every(1.5, 0.5, noteMs, eyes), every(1.5, 1.0, noteMs, eyes))
    // 기훈: 자리가 바뀌면 옆걸음, 서 있으면 영희 눈에 맞춰 차분히 숨, 영희가 눈 감으면 크게 숨 (오른 날엔 한 번 폴짝). 떨어진 날 영희가 보고 있으면 식은땀
    // ios: Views/ThemeScenes.swift:752,753,754,755,756,757,758,759
    var x by remember { mutableStateOf<Float?>(null) }
    var k by remember { mutableIntStateOf(0) }
    var shutAt by remember { mutableIntStateOf(-99) }
    LaunchedEffect(eyes) { if (eyes != MugArt.Eyes.open) shutAt = k }
    LaunchedEffect(still, gx) {
        if (still) { x = gx; return@LaunchedEffect }
        while (true) {
            delay((MoodScript.tick * 1000).toLong())
            val now = x ?: gx; val gap = gx - now
            if (abs(gap) > 0.5f) x = now + (if (gap > 0) 1 else -1) * min(1.4f, abs(gap)) else if (x == null) x = gx
            k += 1
        }
    }
    val me = MeChar.current ?: MeChar()
    val yhImg = MugArt.younghi(eyes)
    Plate(Modifier.graphicsLayer()) {
        val gA = 1f - 0.45f * glow()
        drawOval(Brush.radialGradient(listOf(c(if (dk) 0xB8FFCF else 0x5FE08F, if (dk) 0.6f else 0.55f), c(if (dk) 0xB8FFCF else 0x5FE08F, 0f)), Offset(282f, 124f), 30f),
            Offset(256f, 92f), Size(52f, 64f), alpha = gA, colorFilter = if (dk) ColorFilter.tint(dim, BlendMode.Modulate) else null)
        dot(yhImg, 30.5f, 148.5f, 45f, 52.5f, tint = if (dk) dim else null)
        if (eyes == MugArt.Eyes.closed && !still) for (i in 0..2)
            flyNote(tm, listOf("♪", "♫", "♪")[i], listOf(53f, 58f, 50f)[i], listOf(101f, 98f, 97f)[i], m(c(0xFFF4C2)), 8.5f, songs[i](), false)
        // ios: Views/ThemeScenes.swift:762,763,764,765,766,767,768,769,770,771,772,773,774
        val cx = x ?: gx; val d = gx - cx; val walking = abs(d) > 0.5f
        val st = k - shutAt
        var dy = 0f; var sy = 1f
        val img = if (walking) { val i = (k / 2) % 4; MugArt.gihun(me, leg = listOf(0, 1, 0, 2)[i], lift = i % 2 == 1) }
        else {
            if (eyes == MugArt.Eyes.open) sy = if (k % 24 < 21) 1f else 1.012f
            else { sy = if (k % 8 < 4) 1.035f else 1f; if (!down && st in 3..4) dy = -3f }
            MugArt.gihun(me)
        }
        dot(img, cx, 150f + dy, 36f, 54f, sx = if (walking && d < 0) -1f else 1f, sy = sy, tint = if (dk) dim else null)
        if (down && !walking && eyes != MugArt.Eyes.closed) {
            translate(cx + 11f, 103.5f + dy) {
                val drop = Path().apply { moveTo(3f, 1f); q(5.4f, 4.4f, 3f, 6.6f); q(0.6f, 4.4f, 3f, 1f); close() }
                drawPath(drop, m(c(dk, 0x7CCBFF, 0x9FDCFF))); drawPath(drop, m(c(0x2A1E2E)), style = Stroke(0.6f))
            }
        }
    }
}

// 종목·흐름·분석 작은 등장인물 (시안 18장 · 라이트 28장). 오른 날·떨어진 날에 따라 바뀐다

/** 작은 그림 한 칸 (w×h dp 에 그 그림의 좌표로 그린다) */
@Composable
private fun Mini(w: Float, h: Float, draw: DrawScope.(TextMeasurer) -> Unit) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.size(w.dp, h.dp)) { val k = density; withTransform({ scale(k, k, Offset.Zero) }) { draw(tm) } }
}

// ios: Views/ThemeScenes.swift:862,863,864,865,866,867,868,869,870,871,872,873,874,875,876,877,878,879,880,881,882,883,884,885,886
@Composable private fun MiniDory(dk: Boolean) = Mini(28f, 19f) { withTransform({ translate(14f, 9.5f); scale(0.72f, 0.72f, Offset.Zero); translate(3.5f, 1f) }) { with(ThemeArt) { dory(dk) } } }
@Composable private fun MiniOcto(dk: Boolean) = Mini(26f, 26f) { tm ->
    withTransform({ translate(13f, 15f); scale(0.55f, 0.55f, Offset.Zero) }) {
        with(ThemeArt) { octoArms(true, dk); octoBody(true, dk); octoEyes(true); octoMouth(true) }
        Sk.text(this, tm, "!", 15.5f, -18f, c(dk, 0xE09A00, 0xFFD23F), size = 12f, bold = true, anchor = Anchor.center)
    }
}
@Composable private fun MiniOpera(dk: Boolean) = Mini(26f, 14f) { withTransform({ translate(2f, 2f); scale(0.5f, 0.5f, Offset.Zero) }) { with(ThemeArt) { opera(dk) } } }
@Composable private fun MiniBird(laugh: Boolean) = Mini(20f, 19f) { withTransform({ translate(10.6f, 18f); scale(0.8f, 0.8f, Offset.Zero) }) { with(ThemeArt) { bird(laugh) } } }
@Composable private fun MiniLeaf(dk: Boolean) = Mini(10f, 10f) { translate(4f, 9f) { with(ThemeArt) { leaf(dk = dk) } } }
@Composable private fun MiniNote(dk: Boolean) = Mini(10f, 18f) { tm -> Sk.text(this, tm, "♪", 5f, 6f, c(dk, 0xB07A00, 0xFFE6A8), size = 10f, bold = true, anchor = Anchor.center) }
@Composable private fun MiniExit() = Mini(18f, 22f) { tm -> with(ThemeArt) { miniExit(tm) } }
@Composable private fun MiniGihun() { val img = MugArt.gihun(MeChar.current ?: MeChar()); Mini(16f, 24f) { dot(img, 8f, 24f, 16f, 24f) } }
@Composable private fun MiniCar(dk: Boolean) = Mini(36f, 13.3f) { withTransform({ scale(36f / 95f, 36f / 95f, Offset.Zero) }) { car(dk) } }
@Composable private fun MiniFire(dk: Boolean) = Mini(20f, 8f) {
    drawRoundRect(c(0xFF7A1F, if (dk) 0.85f else 0.9f), Offset(0.7f, 0.8f), Size(19.3f, 2.1f), CornerRadius(1.05f))
    drawRoundRect(c(0xFFB84A, if (dk) 0.85f else 0.9f), Offset(3.3f, 4.5f), Size(16.7f, 2.1f), CornerRadius(1.05f))
}
@Composable private fun MiniPortal(dk: Boolean) = Mini(16f, 30f) {
    withTransform({ scale(1f, 30f / 16f, Offset(8f, 0f)) }) {
        drawCircle(Brush.radialGradient(0f to c(dk, 0xFFFFFF, 0xE8F7FF), 0.45f to c(0x5FC8FF, if (dk) 0.8f else 0.85f), 1f to c(0x2A6CFF, 0f), center = Offset(8f, 8f), radius = 8f), 8f, Offset(8f, 8f))
    }
}
@Composable private fun MiniGlow(dk: Boolean) = Mini(22f, 22f) {
    val g = c(dk, 0xF2B42E, 0xF2C14E)
    drawCircle(g.copy(alpha = if (dk) 0.22f else 0.3f), 10f, Offset(11f, 11f)); drawCircle(g.copy(alpha = if (dk) 0.35f else 0.45f), 6.5f, Offset(11f, 11f))
    drawCircle(c(dk, 0xE8A417, 0xFFF4D6), 4f, Offset(11f, 11f)); if (!dk) drawCircle(Color.White, 4f, Offset(11f, 11f), style = Stroke(1.2f))
}
@Composable private fun MiniWave(dk: Boolean) = Mini(20f, 10f) {
    val p = Path().apply { moveTo(1f, 6f); for (i in 0..2) { val x = 1 + i * 6f; q(x + 3, if (i % 2 == 0) 2f else 10f, x + 6, 6f) } }
    drawPath(p, c(dk, 0x1B5E96, 0x8CC4F2), style = Stroke(1.6f))
}
@Composable private fun MiniSun(dk: Boolean) = Mini(24f, 14f) {
    drawCircle(if (dk) c(0xFFD98A, 0.22f) else c(0xF2B42E, 0.3f), 11f, Offset(12f, 12f))
    drawArc(c(dk, 0xE8A417, 0xFFD98A), 180f, 180f, true, Offset(6f, 6f), Size(12f, 12f))
}
@Composable private fun MiniDuck() = Mini(26f, 21f) { withTransform({ translate(13f - 23f * 0.56f, 10.5f - 19f * 0.56f); scale(0.56f, 0.56f, Offset.Zero) }) { duck() } }
@Composable private fun MiniSplash(dk: Boolean) = Mini(12f, 18f) {
    val col = c(dk, 0x4C8CCB, 0x9EC1FF)
    drawPath(Path().apply { moveTo(3f, 4f); q(5f, 7f, 3f, 9f); q(1f, 7f, 3f, 4f); close() }, col)
    drawPath(Path().apply { moveTo(9f, 1f); q(10.6f, 3.4f, 9f, 5f); q(7.4f, 3.4f, 9f, 1f); close() }, col)
    drawPath(oval(7f, 11f, 1.4f), col)
}
@Composable private fun MiniFoam(dk: Boolean) = Mini(22f, 14f) {
    for ((x, y, r) in listOf(Triple(5f, 9f, 4f), Triple(17f, 9.5f, 3.6f), Triple(11f, 7f, 5f))) { drawPath(oval(x, y, r), Color.White); if (!dk) drawPath(oval(x, y, r), c(0x9FB7D8), style = Stroke(0.8f)) }
    if (dk) drawPath(oval(11f, 7f, 5f), c(0xCFE0F5), style = Stroke(0.8f))
}

/** 구면 뒤집기: 작은 구. ripple = 떨어진 날 테두리 물결, inside = 안쪽 면 색(목표 자리) */
// ios: Views/ThemeScenes.swift:901
@Composable private fun MiniSphere(dk: Boolean, ripple: Boolean = false, inside: Boolean = false) = Mini(18f, 18f) {
    val col = if (inside) c(dk, 0xE2B44A, 0xE9C46A) else c(dk, 0x3E9C8F, 0x4FB3A5)
    val p = Path().apply {
        for (i in 0 until 48) {
            val t = 2 * PI * i / 48; val r = 8 * (1 + (if (ripple) 0.1 * sin(6 * t) else 0.0))
            val x = 9f + (r * cos(t)).toFloat(); val y = 9f + (r * sin(t)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(p, col)
    drawPath(oval(6.5f, 6f, 2.2f), Color.White.copy(alpha = 0.25f))
}

/** 몫 띠 위 오른쪽 끝에 서는 인물 */
// ios: Views/ThemeScenes.swift:807,808,809,810,811,812,813,814,815,816,817,818,819,820,821,822,823,824,825,826
@Composable
fun BandBuddy(look: BoxLook, up: Boolean, modifier: Modifier = Modifier) {
    val dk = boxDark()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        when (look) {
            BoxLook.dory -> if (up) MiniDory(dk) else MiniOcto(dk)
            BoxLook.pino -> if (up) { MiniLeaf(dk); MiniBird(false) } else { MiniNote(dk); MiniBird(true) }
            BoxLook.mug -> MiniSphere(dk, ripple = !up)   // ios: Views/ThemeScenes.swift:828
            BoxLook.jump -> if (up) { MiniFire(dk); MiniCar(dk) } else MiniCar(dk)
            BoxLook.horizon -> if (up) MiniGlow(dk) else { MiniWave(dk); MiniGlow(dk) }
            BoxLook.tub -> if (up) MiniDuck() else { MiniSplash(dk); MiniDuck() }
            BoxLook.bar -> {}
        }
    }
}

/** 분석 막대: 지금 자리에 서는 인물 · 목표선 옆에 서는 것 */
// ios: Views/ThemeScenes.swift:829,830,831,832,833,834,835,836,837,838,839,840,841,842,843,844,845,846,847,848,849,850,851,852,853,854,855,856
@Composable
fun TrackBuddy(look: BoxLook, goal: Boolean = false) {
    val dk = boxDark()
    if (goal) when (look) {
        BoxLook.dory -> MiniOpera(dk); BoxLook.mug -> MiniSphere(dk, inside = true); BoxLook.jump -> MiniPortal(dk)   // ios: Views/ThemeScenes.swift:850
        BoxLook.horizon -> MiniSun(dk); BoxLook.tub -> MiniFoam(dk); else -> {}
    } else when (look) {
        BoxLook.dory -> MiniDory(dk); BoxLook.pino -> MiniBird(false); BoxLook.mug -> MiniSphere(dk)   // ios: Views/ThemeScenes.swift:860
        BoxLook.jump -> MiniCar(dk); BoxLook.horizon -> MiniGlow(dk); BoxLook.tub -> MiniDuck(); BoxLook.bar -> {}
    }
}

// 종목·흐름 바탕 · 몫 띠 · 분석 막대 (새 테마). 색은 라이트·다크 한 쌍

/** 박스 바탕 (새 테마 셋) */
// ios: Views/ThemeScenes.swift:952,953,954,955,956,957,958,959,960,961,962,963,964,965,966,967,968,969,970,971,972,973,974
internal fun DrawScope.themeBackdrop(look: BoxLook, dk: Boolean) {
    when (look) {
        BoxLook.dory -> {
            drawRect(Brush.verticalGradient(0f to c(dk, 0x93CBEC, 0x0F1A35), 0.6f to c(dk, 0xC4E3F6, 0x1B3866), 1f to c(dk, 0xE6F3FC, 0x1F6AA3)))
            drawRect(Brush.radialGradient(listOf(if (dk) c(0xBFE9FF, 0.22f) else c(0xFFFFFF, 0.55f), Color.Transparent), Offset(size.width * 0.7f, size.height * 1.2f), 200.dp.toPx()))
        }
        BoxLook.pino -> {
            drawRect(Brush.verticalGradient(listOf(c(dk, 0xEFDDC0, 0x22170E), c(dk, 0xFBF2E3, 0x4A331D))))
            var x = 69.dp.toPx()
            while (x < size.width) { drawRect(if (dk) c(0x3A2816, 0.8f) else c(0xB9915F, 0.4f), Offset(x, 0f), Size(1.dp.toPx(), size.height)); x += 70.dp.toPx() }
            drawRect(Brush.radialGradient(listOf(if (dk) c(0xFFD58A, 0.28f) else c(0xFFCE78, 0.4f), Color.Transparent), Offset(size.width * 0.1f, size.height * 0.8f), 200.dp.toPx()))
        }
        // 구면 뒤집기: 홈 박스와 같은 바탕 (소품 없이 고요하게)
        BoxLook.mug -> drawRect(Brush.verticalGradient(listOf(c(dk, 0xC9E3DF, 0x123A44), c(dk, 0xF1E7CC, 0x4A3F24))))   // ios: Views/ThemeScenes.swift:997,999
        else -> {
            drawRect(Brush.verticalGradient(listOf(c(dk, 0xBCE0F8, 0x14304F), c(dk, 0xE8F5FD, 0x2C5F8F))))
            drawRect(c(dk, 0xE2C595, 0x8F7550), Offset(0f, size.height - 12.dp.toPx()), Size(size.width, 12.dp.toPx()))
        }
    }
}

/** 새 테마 몫 띠·막대 테두리·바탕 색 */
// ios: Views/ThemeScenes.swift:978,979,980,981,982,983,984,985
internal object ThemeBand {
    val pinoShape: Shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, bottomEnd = 6.dp, topEnd = 6.dp)
    val wood = Brush.verticalGradient(0f to hex(0xECBC82), 0.55f to hex(0xD49A5E), 1f to hex(0xB47B44))
    fun woodFlat(dk: Boolean) = bx(dk, 0x7A5634, 0x6B4A2B)
    fun glass(dk: Boolean) = bx(dk, 0xFFFFFF, 0x9FD6EE, 0.6f, 0.16f); fun glassRim(dk: Boolean) = bx(dk, 0x6FAFD8, 0xCFEFFF)
    fun chalk(dk: Boolean) = bx(dk, 0xFFFFFF, 0xFBF6EA); fun dirt(dk: Boolean) = bx(dk, 0xD9BB8C, 0x8F7550)
    fun dirtRim(dk: Boolean) = bx(dk, 0xC4A374, 0xC4A374, 1f, 0f)
    fun noseDash(dk: Boolean) = bx(dk, 0xB47B44, 0xECBC82, 0.6f, 0.45f)
    fun sphereTrack(dk: Boolean) = bx(dk, 0xFFFFFF, 0xFFFFFF, 0.7f, 0.16f)   // ios: Views/ThemeScenes.swift:1016
}

/** 새 테마 몫 띠: 바깥 틀 (안쪽 띠는 band 가 그린다) */
// ios: Views/HoldingsView.swift:436,437,438,439,440,441,442,443,444,445,446,447,448,449
@Composable
internal fun ThemeBandFrame(look: BoxLook, band: @Composable (height: Dp, radius: Dp) -> Unit) {
    val dk = boxDark()
    when (look) {
        BoxLook.dory -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(ThemeBand.glass(dk)).border(1.5.dp, ThemeBand.glassRim(dk), RoundedCornerShape(50)).padding(2.dp)) { band(8.dp, 4.dp) }
        BoxLook.pino -> Box(Modifier.fillMaxWidth().background(ThemeBand.wood, ThemeBand.pinoShape).padding(2.dp)) { band(7.dp, 3.dp) }
        BoxLook.mug -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(ThemeBand.sphereTrack(dk)).padding(2.dp)) { band(6.dp, 3.dp) }   // ios: Views/HoldingsView.swift:464
        else -> Column(Modifier.fillMaxWidth().border(1.dp, ThemeBand.dirtRim(dk))) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(ThemeBand.chalk(dk)))
            Box(Modifier.fillMaxWidth().background(ThemeBand.dirt(dk)).padding(vertical = 3.dp)) { band(4.dp, 0.dp) }
            Box(Modifier.fillMaxWidth().height(2.dp).background(ThemeBand.chalk(dk)))
        }
    }
}

/** 분석 막대 (새 테마). fill = 지금 자리 0~1, 채운 만큼 노랑 (피노키오는 나무 코) */
// ios: Views/ThemeScenes.swift:989,990,991,992,993,994,995,996,997,998,999,1000,1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1013,1014,1015,1016,1017,1018
@Composable
internal fun ThemeTrack(look: BoxLook, f: Float, on: Color, modifier: Modifier = Modifier) {
    val dk = boxDark()
    Box(modifier.fillMaxWidth().height(14.dp).drawBehind {
        val w = size.width; val px = 1.dp.toPx()
        when (look) {
            BoxLook.dory -> {
                drawRoundRect(ThemeBand.glass(dk), cornerRadius = CornerRadius(7 * px))
                drawRoundRect(on, Offset(2 * px, 2 * px), Size(max(0f, (w - 4 * px) * f), 10 * px), CornerRadius(5 * px))
                drawRoundRect(ThemeBand.glassRim(dk), Offset(0.75f * px, 0.75f * px), Size(w - 1.5f * px, 12.5f * px), CornerRadius(6.25f * px), style = Stroke(1.5f * px))
            }
            BoxLook.pino -> {
                fun nose(width: Float) = Path().apply { addRoundRect(RoundRect(Rect(0f, 2 * px, width, 12 * px), CornerRadius(2 * px), CornerRadius(5 * px), CornerRadius(5 * px), CornerRadius(2 * px))) }
                drawPath(nose(w), ThemeBand.noseDash(dk), style = Stroke(1.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * px, 3 * px))))
                if (f > 0) drawPath(nose(max(0f, w * f)), ThemeBand.wood)
            }
            BoxLook.mug -> {   // ios: Views/ThemeScenes.swift:1040
                drawRoundRect(ThemeBand.sphereTrack(dk), Offset(0f, 3 * px), Size(w, 8 * px), CornerRadius(4 * px))
                drawRoundRect(on, Offset(0f, 3 * px), Size(max(0f, w * f), 8 * px), CornerRadius(4 * px))
            }
            else -> {
                drawRect(ThemeBand.chalk(dk), Offset.Zero, Size(w, 2 * px))
                drawRect(ThemeBand.dirt(dk), Offset(0f, 2 * px), Size(w, 10 * px))
                drawRect(on, Offset(0f, 4 * px), Size(max(0f, w * f), 6 * px))
                drawRect(ThemeBand.chalk(dk), Offset(0f, 12 * px), Size(w, 2 * px))
                drawRect(ThemeBand.dirtRim(dk), Offset.Zero, Size(w, 14 * px), style = Stroke(1 * px))
            }
        }
    })
}

// 홈 장면 3 구면 뒤집기 (2026-10-27, 철수와 영희 자리): 10권 P6. 큰 구 하나가 진행률만큼 안과 밖이 바뀐다
// 바깥 면 = 청록, 안쪽 면 = 호박색. 0~5% 는 매끈한 구, 그 뒤 구간은 임시값 (나머지의 20 · 45 · 55 · 100%). 단계 안에서도 진행률 따라 조금씩 바뀐다.
// 움직임: 몇 초에 한 번 천천히, 지금 단계 안에서 뒤집기가 살짝 나아갔다 돌아온다 (회전 없음)
// ios: Views/ThemeScenes.swift:1062
@Composable
internal fun SphereScene(s: BoxScene) {
    val down = moodDown(s.today); val dk = boxDark(); val still = stillNow()
    val dur = if (down) 6.0 else 4.5   // ios: Views/ThemeScenes.swift:1071
    val p = remember { Animatable(0f) }
    LaunchedEffect(still, down) {
        p.snapTo(0f)
        if (still) return@LaunchedEffect
        while (true) {
            delay(if (down) 5000 else 3500)
            p.snapTo(0f); p.animateTo(1f, tween((dur * 1000).toInt(), easing = LinearEasing)); p.snapTo(0f)
        }
    }
    val tm = rememberTextMeasurer()
    Plate { sphereDraw(s, dk, down, p.value.toDouble(), tm) }
}

/** p: 이번 움직임의 0~1 (쉬는 동안 0) */
// ios: Views/ThemeScenes.swift:1093
private fun DrawScope.sphereDraw(s: BoxScene, dk: Boolean, down: Boolean, p: Double, tm: TextMeasurer) {
    val cx = 225f; val cy = 86f; val R = 40f
    val f = min(1.0, max(0.0, s.f))
    val e = p * p * (3 - 2 * p)                 // 부드럽게 출발·정지
    val k = if (down) 0.85 else 1.0             // 떨어진 날은 덜 출렁인다
    val outC = c(dk, 0x3E9C8F, 0x4FB3A5); val inC = c(dk, 0xE2B44A, 0xE9C46A)
    val bg0 = c(dk, 0xC9E3DF, 0x123A44); val bg1 = c(dk, 0xF1E7CC, 0x4A3F24)
    drawRect(Brush.verticalGradient(listOf(bg0, bg1), 0f, 170f), Offset.Zero, Size(300f, 170f))

    fun blob(r: (Double) -> Double) = Path().apply {
        for (i in 0 until 96) {
            val t = 2 * PI * i / 96; val rr = R * r(t).toFloat()
            val x = cx + rr * cos(t).toFloat(); val y = cy + rr * sin(t).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    fun shine(o: Double = 0.22) = drawPath(oval(cx - 0.3f * R, cy - 0.34f * R, 0.24f * R), Color.White.copy(alpha = o.toFloat()))
    // 처음 무늬 'ㄴ'. 다 뒤집히면 거울로 보인다 (바깥에서 뒷면을 보는 셈)
    fun mark(mirror: Boolean, o: Double = 1.0) {
        val sx = if (mirror) -1f else 1f; val x0 = cx + sx * 0.14f * R; val y0 = cy - 0.32f * R
        val m = Path().apply { moveTo(x0, y0); lineTo(x0, y0 + 0.24f * R); lineTo(x0 + sx * 0.2f * R, y0 + 0.24f * R) }
        drawPath(m, c(0x14303A, o.toFloat()), style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 움직일 때는 지금 단계 안에서만 뒤집기가 살짝 나아갔다가 돌아온다 (단계 폭의 15%, 다음 단계로 넘어가지 않음. 0~5% 매끈한 구는 그대로)
    val g0 = max(0.0, (f - 0.05) / 0.95)
    val hi = listOf(0.2, 0.45, 0.55, 1.0).firstOrNull { g0 < it } ?: 1.0
    val lo = listOf(0.0, 0.2, 0.45, 0.55).lastOrNull { g0 >= it } ?: 0.0
    val g = if (g0 > 0) min(hi - 0.001, g0 + 0.15 * (hi - lo) * sin(PI * e)) else 0.0
    fun sm(x: Double): Double { val q = min(1.0, max(0.0, x)); return q * q * (3 - 2 * q) }
    fun mix(a: Double, b: Double, m: Double) = a + (b - a) * m
    fun wavy(t: Double, amp: Double, off: Double) = 1 + amp * k * sin(6 * t + off)
    fun core(t: Double, base: Double, amp: Double, off: Double) = base + amp * k * sin(6 * t + PI + off)
    fun lobe(t: Double, sg: Double) = 0.4 + 0.6 * k * max(0.0, sg * cos(2 * t))

    if (f >= 1) {
        // ⑤ 같은 구: 모양은 처음과 같고 향하는 면만 다르다
        drawPath(oval(cx, cy, R), inC)
        shine(0.22 + 0.12 * sin(PI * p)); mark(true)
    } else if (g < 0.2) {
        // ① 굽이: 물결이 0에서 점점 커지고 조금씩 비틀린다. 안쪽 색이 스쳤다가 겹치기 직전 사라진다 (E5)
        val u = g / 0.2; val off = u * PI / 3
        drawPath(blob { wavy(it, 0.12 * u, off) }, outC)
        if (p > 0 && g > 0) {
            val gh = Path().apply { moveTo(cx - 0.4f * R, cy - 0.56f * R); quadraticTo(cx + 0.16f * R, cy - 0.2f * R, cx - 0.12f * R, cy + 0.64f * R) }
            drawPath(gh, inC.copy(alpha = ((0.15 + 0.3 * u) * sin(PI * p)).toFloat()), style = Stroke(0.2f * R, cap = StrokeCap.Round))
        }
        shine(0.22 * (1 - u)); mark(false, 1 - u)
    } else if (g < 0.45) {
        // ② 통과: 안쪽 면이 가운데서 자라며 바깥 면과 반대로 돌아 지나간다
        val u = (g - 0.2) / 0.25; val off = PI / 3 + u * PI / 6
        drawPath(blob { wavy(it, 0.12, off) }, outC)
        drawPath(blob { core(it, 0.72 * u, 0.18 * u, off) }, inC.copy(alpha = (0.8 * u).toFloat()))
    } else if (g < 0.55) {
        // ③ 반환점: ②의 끝 → 안도 밖도 아닌 네 잎 → ④의 시작(색만 바뀐 같은 꼴)
        val u = (g - 0.45) / 0.1; val off = PI / 2
        if (u < 0.5) {
            val m = sm(u * 2)
            drawPath(blob { mix(wavy(it, 0.12, off), lobe(it, 1.0), m) }, outC)
            drawPath(blob { mix(core(it, 0.72, 0.18, off), lobe(it, -1.0), m) }, inC.copy(alpha = mix(0.8, 1.0, m).toFloat()))
        } else {
            val m = sm((u - 0.5) * 2)
            drawPath(blob { mix(lobe(it, -1.0), wavy(it, 0.12, off), m) }, inC)
            drawPath(blob { mix(lobe(it, 1.0), core(it, 0.72, 0.18, off), m) }, outC.copy(alpha = mix(1.0, 0.8, m).toFloat()))
        }
        val hole = 0.25f * R * sin(PI * u).toFloat()
        if (hole > 0) drawPath(oval(cx, cy, hole), Brush.verticalGradient(listOf(bg0, bg1), cy - hole, cy + hole))
    } else {
        // ④ 돌아 나옴: 겉은 안쪽 색. 바깥 색 굽이가 비틀리며 줄어 100%에서 사라진다
        val u = (g - 0.55) / 0.45; val off = PI / 2 + u * PI / 3
        drawPath(blob { wavy(it, 0.12 * (1 - u), off) }, inC)
        drawPath(blob { core(it, 0.72 * (1 - u), 0.18 * (1 - u), off) }, outC.copy(alpha = (0.8 * (1 - u)).toFloat()))
    }

    // 진행률: 구 아래 짧은 막대 + 오른쪽 숫자 (둘을 묶어 구 가운데에 맞춤, 반환점을 지나면 안쪽 색)
    val barC = if (f < 0.5) outC else inC; val x0 = 182f; val w = 56f
    drawRoundRect(Color.White.copy(alpha = if (dk) 0.16f else 0.7f), Offset(x0, 147f), Size(w, 5f), CornerRadius(2.5f))
    drawRoundRect(barC, Offset(x0, 147f), Size(w * f.toFloat(), 5f), CornerRadius(2.5f))
    Sk.text(this, tm, AppModel.pct(f), x0 + w + 6, 149.5f, c(dk, 0x2F5F59, 0xFFD98A), size = 12f, bold = true)
}
