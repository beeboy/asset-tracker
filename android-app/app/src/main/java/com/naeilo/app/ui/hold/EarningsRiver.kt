package com.naeilo.app.ui.hold

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.core.cformat
import com.naeilo.app.core.swFmt
import com.naeilo.app.core.tr
import com.naeilo.app.data.Earnings
import com.naeilo.app.data.EarningsFeed
import com.naeilo.app.data.Symbol
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.serifDigits
import com.naeilo.app.ui.town.TownLook
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// 내 몫 실적: 목록 작은 막대 두 개 → 누르면 강 팝업 (시안 판16, viewBox 360×680). 아이폰 Views/EarningsRiver.swift 와 같은 좌표·색·동작.
// 강: 이번 분기 내 몫 매출이 물높이, 강바닥에서 실(원가·판관비·이익·연구비)이 새어 나온다. 금색 실 끝이 내 몫 순이익.

/** 강·막대 색 (밝은·어두운) */
// ios: Views/EarningsRiver.swift:53
class EarnPal(private val dark: Boolean) {
    private fun c(l: Long, d: Long) = Color((if (dark) d else l) or 0xFF000000)
    val water = c(0x7FA9B8, 0x3F6F80)   // ios: Views/EarningsRiver.swift:54
    val water2 = c(0xA9C7D2, 0x2C5160)   // ios: Views/EarningsRiver.swift:55
    val strand = c(0xB9C6CB, 0x4A5A60)   // ios: Views/EarningsRiver.swift:56
    val ghost = c(0xA9B3B0, 0x5D6965)   // ios: Views/EarningsRiver.swift:57
    val sprout = c(0x6FA85A, 0x8CC477)   // ios: Views/EarningsRiver.swift:58
    val gold = c(0xC8962E, 0xE0B04A)   // ios: Views/EarningsRiver.swift:59
    val fg = c(0x1D2624, 0xE4EBE8)   // ios: Views/EarningsRiver.swift:60
    val muted = c(0x6B7773, 0x8C9893)   // ios: Views/EarningsRiver.swift:61
    val screen = c(0xFFFFFF, 0x1A2220)   // ios: Views/EarningsRiver.swift:62
    val onwater = c(0xF4FAFB, 0xE4F0F3)   // ios: Views/EarningsRiver.swift:63
    companion object { val Light = EarnPal(false); val Dark = EarnPal(true) }
}
val EarnColor: EarnPal @Composable get() = if (LocalPal.current.isDark) EarnPal.Dark else EarnPal.Light   // ios: Views/EarningsRiver.swift:53

/** 강 팝업을 열 종목·수량 */
data class EarnTarget(val sym: Symbol, val qty: Double)   // ios: Views/EarningsRiver.swift:141

// ── 목록 작은 막대 ──

/** 폭 14 높이 26: 옅은 = 작년 같은 분기 내 몫 이익, 금색 = 이번 분기. 숫자 없음. 자료가 없으면(ETF) 짧은 줄표.
 *  높이는 크기(절댓값) 비율, 손실(순이익<0)은 같은 높이 규칙으로 속 빈 테두리 */
@Composable
fun EarnBars(s: Earnings.Summary?) {   // ios: Views/EarningsRiver.swift:78,79
    val still = LocalReduceMotion.current   // ios: Views/EarningsRiver.swift:80
    val ec = EarnColor
    Box(Modifier.size(14.dp, 26.dp), contentAlignment = Alignment.BottomCenter) {   // ios: Views/EarningsRiver.swift:83,84,102
        if (s != null) {   // ios: Views/EarningsRiver.swift:85
            val grow = remember(s.symbol) { Animatable(if (s.fresh && !still) 0f else 1f) }   // ios: Views/EarningsRiver.swift:81,94,95
            // 실적 나온 날 한 번 차오른다 (.9초 easeOut, .2초 뒤)
            LaunchedEffect(s.symbol) { if (grow.value < 1f) { delay(200); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) } }   // ios: Views/EarningsRiver.swift:93,96
            val a = s.lastYearNet ?: 0.0; val b = s.net ?: 0.0; val top = max(abs(a), abs(b))   // ios: Views/EarningsRiver.swift:87
            Row(Modifier.graphicsLayer { scaleY = grow.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) },   // ios: Views/EarningsRiver.swift:92
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {   // ios: Views/EarningsRiver.swift:88
                Bar(if (top > 0) abs(a) / top else 0.0, ec.ghost, hollow = a < 0)   // ios: Views/EarningsRiver.swift:89
                Bar(if (top > 0) abs(b) / top else 0.0, ec.gold, hollow = b < 0)   // ios: Views/EarningsRiver.swift:90
            }
        } else {   // ios: Views/EarningsRiver.swift:98
            Box(Modifier.size(8.dp, 2.dp).background(Theme.muted, CapsuleShape))   // ios: Views/EarningsRiver.swift:99
        }
    }
}

@Composable
// ios: Views/EarningsRiver.swift:105,106
private fun Bar(h: Double, c: Color, hollow: Boolean) {
    // 0(자료 없음)도 바닥 2 는 보이게. 손실은 채움 없이 같은 색 테두리
    val r = RoundedCornerShape(1.5.dp)   // ios: Views/EarningsRiver.swift:108
    if (hollow) Box(Modifier.size(6.dp, max(3.0, 26 * h).dp).border(1.2.dp, c, r))   // ios: Views/EarningsRiver.swift:109,110
    else Box(Modifier.size(6.dp, max(2.0, 26 * h).dp).background(c, r))   // ios: Views/EarningsRiver.swift:111,112
}

/** 목록 위 첫 안내 (닫으면 다시 안 뜸) */
@Composable
// ios: Views/EarningsRiver.swift:118
fun EarnTip() {
    var closed by remember { mutableStateOf(com.naeilo.app.core.Store.defaults.getBoolean("earn.tipClosed", false)) }   // ios: Views/EarningsRiver.swift:119
    AnimatedVisibility(!closed, exit = fadeOut() + shrinkVertically()) {   // ios: Views/EarningsRiver.swift:120,121,126
    Row(
        Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp))   // ios: Views/EarningsRiver.swift:133,134
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),   // ios: Views/EarningsRiver.swift:132
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/EarningsRiver.swift:122
    ) {
        Box(Modifier.size(6.dp, 14.dp).background(EarnColor.gold, RoundedCornerShape(1.5.dp)))   // ios: Views/EarningsRiver.swift:123
        T("금색 막대 누르면 내 몫 이익", Modifier.weight(1f), style = appFont(13f), color = Theme.sub)   // ios: Views/EarningsRiver.swift:124,125
        Box(Modifier.size(32.dp, 32.dp).plainClickable {   // ios: Views/EarningsRiver.swift:126,128
            closed = true; com.naeilo.app.core.Store.defaults.edit().putBoolean("earn.tipClosed", true).apply()
        }.semantics { contentDescription = tr("안내 닫기") }, contentAlignment = Alignment.Center) {   // ios: Views/EarningsRiver.swift:130
            Sym("xmark", 12f, Theme.muted, scaled = false)   // ios: Views/EarningsRiver.swift:127
        }
    }
    }
}

// ── 강 팝업 ──

@Composable
fun EarningsRiverSheet(target: EarnTarget?, onDismiss: () -> Unit) {   // ios: Views/EarningsRiver.swift:143,144,145,149
    Sheet(target != null, onDismiss) {   // ios: Views/EarningsRiver.swift:149,191,192
        val t = target ?: return@Sheet
        val feed = EarningsFeed.shared   // ios: Views/EarningsRiver.swift:150
        val s = feed.summary(t.sym, t.qty)   // ios: Views/EarningsRiver.swift:151
        Column(Modifier.fillMaxSize().background(EarnColor.screen), verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/EarningsRiver.swift:153,189
            Row(Modifier.fillMaxWidth()) {   // ios: Views/EarningsRiver.swift:154
                Spacer(Modifier.weight(1f))   // ios: Views/EarningsRiver.swift:155
                Box(Modifier.size(44.dp, 44.dp).plainClickable(onDismiss).semantics { contentDescription = tr("닫기") }, contentAlignment = Alignment.Center) {   // ios: Views/EarningsRiver.swift:156,158,160
                    Sym("xmark", 15f, Theme.sub, scaled = false)   // ios: Views/EarningsRiver.swift:157
                }
            }
            if (s != null) {   // ios: Views/EarningsRiver.swift:162
                // 옆으로 밀면 지난 분기 (맨 오른쪽이 이번 분기). 장에 들어올 때마다 그 분기 강이 다시 흐른다
                val pages = feed.pages(t.sym)   // ios: Views/EarningsRiver.swift:152
                val page = rememberPagerState(initialPage = pages.size - 1) { pages.size }   // 처음은 이번 분기(맨 오른쪽, tag -1)   // ios: Views/EarningsRiver.swift:147,190
                HorizontalPager(page, Modifier.widthIn(max = Theme.maxWidth).weight(1f), key = { pages[it] ?: -1 }) { i ->   // TabView .page = HorizontalPager   // ios: Views/EarningsRiver.swift:164,165,172,175,188
                    val at = pages[i]
                    feed.summary(t.sym, t.qty, at)?.let { q ->   // ios: Views/EarningsRiver.swift:166,167
                        EarningsRiver(t.sym.name, t.qty, q, Modifier.padding(start = 8.dp, end = 8.dp), now = at == null)   // ios: Views/EarningsRiver.swift:168,171
                    }
                }
                if (pages.size > 1) {   // ios: Views/EarningsRiver.swift:182
                    // 장 표시 점: 이번 분기(맨 오른쪽)는 금색
                    Row(Modifier.padding(bottom = 10.dp).height(20.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/EarningsRiver.swift:196,198,199,205,206
                        pages.indices.forEach { i ->   // ios: Views/EarningsRiver.swift:200
                            val on = (pages[i] ?: -1) == (pages.getOrNull(page.currentPage) ?: -1)   // ios: Views/EarningsRiver.swift:201
                            Box(Modifier.size(if (on) 7.dp else 5.dp, if (on) 7.dp else 5.dp).background(if (on) EarnColor.gold else EarnColor.muted.copy(alpha = 0.35f), CircleShape))   // ios: Views/EarningsRiver.swift:202
                        }
                    }
                } else Spacer(Modifier.height(12.dp))
            } else {   // ios: Views/EarningsRiver.swift:183
                // 자료가 없으면 팝업을 열지 않는다 (목록이 상세로 보냄). 그사이 자료가 사라졌으면 바로 닫는다
                LaunchedEffect(Unit) { onDismiss() }   // ios: Views/EarningsRiver.swift:185
            }
        }
    }
}

private const val W = 360f; private const val H = 680f; private const val BED = 300f   // ios: Views/EarningsRiver.swift:222
private const val TOP = 110f   // 아이폰에도 있는 자리 값 (지금은 안 씀)   // ios: Views/EarningsRiver.swift:222
private const val LEFT = 24f; private const val RIGHT = 250f; private const val TOTAL = 50f; private const val MID = 175f   // ios: Views/EarningsRiver.swift:223
private const val END = 2.9   // 마지막 동작(떡잎)이 끝나는 초   // ios: Views/EarningsRiver.swift:224

/** 강 그림 (시안 판16). 물이 차고 실이 흐른 뒤 떡잎이 핀다(약 3초, 팝업이 열린 동안만). 동작 줄이기면 다 흐른 정지 화면 */
@Composable
fun EarningsRiver(name: String, qty: Double, s: Earnings.Summary, modifier: Modifier = Modifier, now: Boolean = true, frozen: Boolean = false) {   // ios: Views/EarningsRiver.swift:211,212,213,214,215,216
    val still = LocalReduceMotion.current || frozen   // ios: Views/EarningsRiver.swift:217,218
    var el by remember { mutableFloatStateOf(0f) }   // ios: Views/EarningsRiver.swift:230,235
    // 한 번 흐르고(기다리는 중이면 물만 차오르고) 멈춘다
    val paused = still   // ios: Views/EarningsRiver.swift:228
    val animate = !paused   // ios: Views/EarningsRiver.swift:229
    LaunchedEffect(s.symbol, animate) {   // ios: Views/EarningsRiver.swift:240
        if (!animate) return@LaunchedEffect
        val start = withFrameNanos { it }   // 장마다 새로 그려져 그 장의 시작 시각만 쓴다   // ios: Views/EarningsRiver.swift:219,241,242
        var done = false   // ios: Views/EarningsRiver.swift:220
        while (!done) { el = withFrameNanos { (it - start) / 1e9f }; done = el >= END + 0.1 }   // ios: Views/EarningsRiver.swift:243
    }
    val ec = EarnColor
    val tm = rememberTextMeasurer()
    val dens = LocalDensity.current
    val title = tr("$name${if (TownLook.batchim(name)) "이" else "가"} 번 돈 중 내 몫")   // ios: Views/EarningsRiver.swift:259
    Canvas(modifier.fillMaxWidth().aspectRatio(W / H).semantics { contentDescription = a11y(s, title, now) }) {   // ios: Views/EarningsRiver.swift:226,231,238,239,245,246
        val k = min(size.width / W, size.height / H)   // ios: Views/EarningsRiver.swift:232
        withTransform({ translate(left = (size.width - W * k) / 2, top = 0f); scale(k, k, pivot = Offset.Zero) }) {   // ios: Views/EarningsRiver.swift:233,234
            val d = RiverDraw(this, tm, dens.density * dens.fontScale, ec, s, name, qty, title, still, now)
            d.draw(if (still) 99.0 else el.toDouble())   // ios: Views/EarningsRiver.swift:235
        }
    }
}

// ios: Views/EarningsRiver.swift:249
private fun a11y(s: Earnings.Summary, title: String, now: Boolean): String {
    s.pending?.let { return "$title. $it 실적 발표, 숫자를 기다리는 중" }   // ios: Views/EarningsRiver.swift:250
    var t = if (now) "$title ${s.months}월. " else "$title ${s.label}. "   // ios: Views/EarningsRiver.swift:251
    t += "내 몫 매출 ${Earnings.money(s.revenue ?: 0.0, s.currency)}"   // ios: Views/EarningsRiver.swift:252
    s.revYoY?.let { t += ", 작년보다 $it" }   // ios: Views/EarningsRiver.swift:253
    t += if (s.loss) (if (now) ". 이번 분기" else ". 그 분기") + " 내 몫 손실 ${Earnings.money(s.short, s.currency)}" else ". 내 몫 순이익 ${s.netText}"   // ios: Views/EarningsRiver.swift:254
    s.netKrw?.let { t += ", $it" }   // ios: Views/EarningsRiver.swift:255
    return t   // ios: Views/EarningsRiver.swift:256
}

/** 강 한 장 그리기. 좌표는 viewBox 360×680 */
private class RiverDraw(
    val ds: DrawScope, val tm: TextMeasurer, val spPx: Float, val ec: EarnPal,
    val s: Earnings.Summary, val name: String, val qty: Double, val title: String, val still: Boolean,
    val now: Boolean = true,   // 이번 분기 장. 지난 분기 장은 분기 이름(25.7~9)을 쓴다
) {
    // 0~1 구간 안에서 0~1 로 (밖은 0 또는 1)
    fun seg(t: Double, a: Double, b: Double) = max(0.0, min(1.0, (t - a) / (b - a)))   // ios: Views/EarningsRiver.swift:264
    /** cubic-bezier(.4,0,.2,1) */
    fun ease(x: Double): Double {   // ios: Views/EarningsRiver.swift:266
        if (x <= 0) return 0.0; if (x >= 1) return 1.0   // ios: Views/EarningsRiver.swift:267
        fun bz(u: Double, p1: Double, p2: Double) = 3 * (1 - u) * (1 - u) * u * p1 + 3 * (1 - u) * u * u * p2 + u * u * u   // ios: Views/EarningsRiver.swift:268
        var lo = 0.0; var hi = 1.0   // ios: Views/EarningsRiver.swift:269
        (0 until 20).forEach { _ -> val m = (lo + hi) / 2; if (bz(m, 0.4, 0.2) < x) lo = m else hi = m }   // ios: Views/EarningsRiver.swift:270
        return bz((lo + hi) / 2, 0.0, 1.0)   // ios: Views/EarningsRiver.swift:271
    }
    /** 튀듯 커짐 (back-out) */
    fun pop(x: Double): Double { val c = 1.70158; return if (x <= 0) 0.0 else 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2) }   // ios: Views/EarningsRiver.swift:274
    /** 글자: at 초부터 .4초 페이드 */
    fun fade(t: Double, at: Double) = seg(t, at, at + 0.4)   // ios: Views/EarningsRiver.swift:276

    /** anchor: 0 왼쪽(leading)·0.5 가운데·1 오른쪽(trailing), 세로는 가운데 */
    fun text(str: String, size: Float, w: FontWeight, c: Color, x: Float, y: Float, ax: Float, op: Double = 1.0, serif: Boolean = false) {   // ios: Views/EarningsRiver.swift:278
        if (op <= 0) return   // ios: Views/EarningsRiver.swift:279
        val st = TextStyle(fontSize = (size / spPx).sp, fontWeight = w, color = c)   // ios: Views/EarningsRiver.swift:281
        val lay = tm.measure(if (serif) serifDigits(str) else AnnotatedString(str), st, softWrap = false)   // ios: Views/EarningsRiver.swift:281
        ds.drawText(lay, topLeft = Offset(x - lay.size.width * ax, y - lay.size.height / 2f), alpha = op.toFloat())   // ios: Views/EarningsRiver.swift:280
    }

    /** 회사 매출 "570억" (억 단위) */
    fun eok(v: Double): String {   // ios: Views/EarningsRiver.swift:286
        val e = v / 1e8   // ios: Views/EarningsRiver.swift:287
        return (if (e >= 10) Earnings.grouped(Math.round(e)) else cformat("%.1f", e)) + "억"   // ios: Views/EarningsRiver.swift:288
    }

    fun draw(t: Double) = with(ds) {   // ios: Views/EarningsRiver.swift:291
        val fmt = { v: Double -> Earnings.money(v, s.currency) }   // ios: Views/EarningsRiver.swift:292
        // 제목
        text(title, 20f, FontWeight.Bold, ec.fg, 24f, 42f, 0f, serif = true)   // ios: Views/EarningsRiver.swift:294

        // 물높이 lv(v) = 270 − (v−min)/(max−min)×210 (지난 4분기 + 지금, 회사 매출)
        val co = s.coRevenue ?: 0.0   // ios: Views/EarningsRiver.swift:297
        val vals = s.gauge.map { it.co ?: 0.0 } + co   // ios: Views/EarningsRiver.swift:298
        val lo = vals.minOrNull() ?: 0.0; val hi = vals.maxOrNull() ?: 1.0   // ios: Views/EarningsRiver.swift:299
        val lv = { v: Double -> if (hi > lo) 270f - ((v - lo) / (hi - lo)).toFloat() * 210f else 165f }   // ios: Views/EarningsRiver.swift:300
        val S = lv(co)   // ios: Views/EarningsRiver.swift:301

        // 물: rect 0,S~300 + 물결. 위→아래로 채워진다 (.6초). 기다리는 중이면 강바닥에서 지난 분기 높이까지 2초 차오른다
        val wet = s.pending == null   // ios: Views/EarningsRiver.swift:304
        val top = if (wet) S else BED - (BED - S) * ease(seg(t, 0.0, 2.0)).toFloat()   // ios: Views/EarningsRiver.swift:305
        val fill = if (wet) ease(seg(t, 0.0, 0.6)).toFloat() else 1f   // ios: Views/EarningsRiver.swift:306
        clipRect(0f, top, W, top + (BED - top) * fill) {   // ios: Views/EarningsRiver.swift:307,308
            drawRect(ec.water, Offset(0f, top), Size(W, BED - top))   // ios: Views/EarningsRiver.swift:309
            val w = Path().apply {   // ios: Views/EarningsRiver.swift:310
                moveTo(0f, 230f); cubicTo(90f, 210f, 200f, 250f, 360f, 225f)   // ios: Views/EarningsRiver.swift:311,312
                lineTo(360f, 300f); lineTo(0f, 300f); close()   // ios: Views/EarningsRiver.swift:313
            }
            drawPath(w, ec.water2)   // ios: Views/EarningsRiver.swift:314
        }
        // 강 위 글: 물이 낮아 글이 물 밖이면 진한 글자로
        val base = min(S, 200f)   // ios: Views/EarningsRiver.swift:317
        val c1 = if (base + 40 - 8 > S) ec.onwater else ec.fg; val c2 = if (base + 70 - 12 > S) ec.onwater else ec.fg   // ios: Views/EarningsRiver.swift:318
        s.pending?.let { p ->   // ios: Views/EarningsRiver.swift:319
            val md = p.split("-").mapNotNull { it.toIntOrNull() }   // ios: Views/EarningsRiver.swift:320
            val day = if (md.size == 3) "${md[1]}월 ${md[2]}일" else p   // ios: Views/EarningsRiver.swift:321
            text(tr("실적이 나왔어요 · $day"), 12f, FontWeight.Normal, c1, 24f, base + 40, 0f, 0.85 * fade(t, 1.8))   // ios: Views/EarningsRiver.swift:322
            text(tr("숫자가 들어오면 강이 차요"), 20f, FontWeight.Bold, c2, 24f, base + 70, 0f, fade(t, 1.8), serif = true)   // ios: Views/EarningsRiver.swift:323
            gauge(lv, null, t, lastS = S)   // ios: Views/EarningsRiver.swift:324
            return@with   // ios: Views/EarningsRiver.swift:325
        }
        text(if (now) tr("$name · 이번 분기") else "$name · ${s.label}", 12f, FontWeight.Normal, c1, 24f, base + 40, 0f, 0.85 * fade(t, 0.3))   // ios: Views/EarningsRiver.swift:327
        val unit = if (s.currency == "USD") tr(" 달러") else " " + s.currency   // ios: Views/EarningsRiver.swift:328
        text(tr("매출 ${eok(co)}$unit"), 20f, FontWeight.Bold, c2, 24f, base + 70, 0f, fade(t, 0.3), serif = true)   // ios: Views/EarningsRiver.swift:329
        gauge(lv, S, t)   // ios: Views/EarningsRiver.swift:330

        // 물안개: 남는 돈(이자·세금·기타). 피었다가 실 끝 전에 걷힌다 (글자 없음). 흐림 10 은 가장자리로 옅어지는 둥근 무늬로
        val rev0 = s.revenue
        if (s.mist > 0 && rev0 != null && rev0 > 0) {   // ios: Views/EarningsRiver.swift:333
            val m = sin(PI * seg(t, 0.6, 1.7)) * (if (still) 0 else 1)   // ios: Views/EarningsRiver.swift:334
            if (m > 0) {   // ios: Views/EarningsRiver.swift:335
                val r = 14f + 40f * min(1.0, s.mist / rev0 * 4).toFloat()   // ios: Views/EarningsRiver.swift:336
                for ((dx, dy) in listOf(-50f to 0f, 0f to -8f, 52f to 2f)) {   // ios: Views/EarningsRiver.swift:340
                    val ex = MID + dx - r; val ey = S - 16 + dy - r * 0.35f; val ew = r * 2; val eh = r * 0.7f   // ios: Views/EarningsRiver.swift:341
                    val cx = ex + ew / 2; val cy = ey + eh / 2
                    withTransform({ scale(1f, eh / ew, pivot = Offset(cx, cy)) }) {   // ios: Views/EarningsRiver.swift:337,341
                        val R = ew / 2 + 10f   // 흐림 10   // ios: Views/EarningsRiver.swift:338
                        drawCircle(Brush.radialGradient(0f to ec.water2, (r - 10f).coerceAtLeast(0f) / R to ec.water2, 1f to ec.water2.copy(alpha = 0f), center = Offset(cx, cy), radius = R),
                            radius = R, center = Offset(cx, cy), alpha = (0.55 * m).toFloat())   // ios: Views/EarningsRiver.swift:339
                    }
                }
            }
        }

        // 내 몫 (강바닥 아래)
        text(tr("내 몫 ${fmt(s.revenue ?: 0.0)}"), 12f, FontWeight.SemiBold, ec.fg, 216f, 326f, 0f, fade(t, 0.4))   // ios: Views/EarningsRiver.swift:348
        text(tr("${qty.swFmt()}주만큼 · 확대"), 11f, FontWeight.Normal, ec.muted, 216f, 343f, 0f, fade(t, 0.4))   // ios: Views/EarningsRiver.swift:349

        // 실: 왼→오 원가·판관비·이익·연구비. 굵기 = 내 몫 매출 안 비율 × 50, 이익 실은 최소 3. 가운데 175 에 나란히
        val rev = max(s.revenue ?: 0.0, 1e-12); val sc = TOTAL / rev.toFloat()   // ios: Views/EarningsRiver.swift:352
        val byK = s.branches.associate { it.k to it.v }   // ios: Views/EarningsRiver.swift:353
        val ss = ArrayList<Pair<String, Float>>()   // ios: Views/EarningsRiver.swift:354
        byK["cogs"]?.let { ss += "cogs" to it.toFloat() * sc }   // ios: Views/EarningsRiver.swift:355
        byK["spent"]?.let { ss += "spent" to it.toFloat() * sc }   // ios: Views/EarningsRiver.swift:356
        byK["sga"]?.let { ss += "sga" to it.toFloat() * sc }   // ios: Views/EarningsRiver.swift:357
        if (!s.loss) ss += "net" to max(3f, (s.net ?: 0.0).toFloat() * sc)   // ios: Views/EarningsRiver.swift:358
        byK["rnd"]?.let { ss += "rnd" to it.toFloat() * sc }   // ios: Views/EarningsRiver.swift:359
        var x = MID - ss.fold(0f) { a, b -> a + b.second } / 2   // ios: Views/EarningsRiver.swift:360
        val hollow = if (s.loss) s.short.toFloat() * sc else 0f   // 적자: 마지막 실 속 빈 테두리   // ios: Views/EarningsRiver.swift:361
        var goldX = MID   // ios: Views/EarningsRiver.swift:362
        ss.forEachIndexed { i, (key, sw) ->   // ios: Views/EarningsRiver.swift:363
            val sx = x + sw / 2; x += sw   // ios: Views/EarningsRiver.swift:364
            val h = if (i == ss.size - 1) hollow else 0f   // ios: Views/EarningsRiver.swift:365
            val p = Path().apply { moveTo(sx, BED) }   // ios: Views/EarningsRiver.swift:366
            when (key) {   // ios: Views/EarningsRiver.swift:367
                "cogs", "spent" -> {   // ios: Views/EarningsRiver.swift:368
                    p.lineTo(sx, 350f); p.cubicTo(sx, 400f, 70f, 392f, 40f, 440f)   // ios: Views/EarningsRiver.swift:369,370
                    flow(p, sw, Offset(40f, 440f), sx, null, ease(seg(t, 0.0, 1.0)), h)   // ios: Views/EarningsRiver.swift:371
                    val label = if (key == "cogs") tr("원가 ${fmt(byK["cogs"] ?: 0.0)}") else tr("쓴 돈 ${fmt(byK["spent"] ?: 0.0)}")
                    text(label, 12f, FontWeight.Normal, ec.muted, 24f, 466f, 0f, fade(t, 0.7))   // ios: Views/EarningsRiver.swift:373
                }
                "sga" -> {   // ios: Views/EarningsRiver.swift:374
                    p.lineTo(sx, 470f); p.cubicTo(sx, 508f, 100f, 506f, 70f, 540f)   // ios: Views/EarningsRiver.swift:375,376
                    flow(p, sw, Offset(70f, 540f), sx, null, ease(seg(t, 0.4, 1.7)), h)   // ios: Views/EarningsRiver.swift:377
                    text(tr("판매·관리비 ${fmt(byK["sga"] ?: 0.0)}"), 12f, FontWeight.Normal, ec.muted, 24f, 564f, 0f, fade(t, 1.4))
                }
                "rnd" -> {   // ios: Views/EarningsRiver.swift:379
                    p.lineTo(sx, 410f); p.cubicTo(sx, 450f, 290f, 448f, 320f, 488f)   // ios: Views/EarningsRiver.swift:380,381
                    flow(p, sw, Offset(320f, 488f), sx, ec.sprout, ease(seg(t, 0.2, 1.4)), h)   // ios: Views/EarningsRiver.swift:382
                    sprout(pop(seg(t, 2.2, 2.8)))   // ios: Views/EarningsRiver.swift:383
                    text(tr("연구비 ${fmt(byK["rnd"] ?: 0.0)}"), 12f, FontWeight.Normal, ec.muted, 336f, 512f, 1f, fade(t, 1.1))
                }
                else -> {   // net: 금색 실이 곧게 내려간다   // ios: Views/EarningsRiver.swift:385
                    goldX = sx   // ios: Views/EarningsRiver.swift:386
                    val ly = s.lastYearNet; val n = s.net   // ios: Views/EarningsRiver.swift:387
                    if (ly != null && ly > 0 && n != null && n > 0) {   // ios: Views/EarningsRiver.swift:387
                        val g = Path().apply { moveTo(sx + 14, 520f); lineTo(sx + 14, 600f) }   // ios: Views/EarningsRiver.swift:388
                        drawPath(trim(g, 0.0, ease(seg(t, 1.5, 2.1))), ec.ghost.copy(alpha = 0.6f), style = Stroke(sw * (ly / n).toFloat(), cap = StrokeCap.Butt))   // ios: Views/EarningsRiver.swift:389,390
                        text(tr("작년 ${fmt(ly)}"), 11f, FontWeight.Normal, ec.muted, sx + 20, 560f, 0f, fade(t, 2.0))   // ios: Views/EarningsRiver.swift:391
                    }
                    p.lineTo(sx, 600f)   // ios: Views/EarningsRiver.swift:393
                    drawPath(trim(p, 0.0, ease(seg(t, 0.0, 2.0))), ec.gold, style = Stroke(sw, cap = StrokeCap.Butt))   // ios: Views/EarningsRiver.swift:394
                }
            }
        }

        // 금색 끝: 큰 금액(+원화) · 설명. 적자면 옅은 손실
        if (s.loss) {   // ios: Views/EarningsRiver.swift:399
            text(fmt(-s.short), 30f, FontWeight.Bold, ec.muted, MID, 636f, 0.5f, fade(t, 1.9), serif = true)   // ios: Views/EarningsRiver.swift:400
            text(if (now) tr("이번 분기 내 몫 손실") else tr("그 분기 내 몫 손실"), 12f, FontWeight.Normal, ec.muted, MID, 660f, 0.5f, fade(t, 2.1))   // ios: Views/EarningsRiver.swift:401
        } else {   // ios: Views/EarningsRiver.swift:402
            val op = fade(t, 1.9)   // ios: Views/EarningsRiver.swift:403
            if (op > 0) {   // ios: Views/EarningsRiver.swift:404
                val big = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = (30f / spPx).sp, fontWeight = FontWeight.Bold, color = ec.gold)) { append(serifDigits(s.netText)) }   // ios: Views/EarningsRiver.swift:405
                    s.netKrw?.let { w -> withStyle(SpanStyle(fontSize = (13f / spPx).sp, fontWeight = FontWeight.SemiBold, color = ec.muted)) { append("  " + w) } }   // ios: Views/EarningsRiver.swift:406
                }
                val lay = tm.measure(big, TextStyle.Default, softWrap = false)
                drawText(lay, topLeft = Offset(goldX - lay.size.width / 2f, 636f - lay.size.height / 2f), alpha = op.toFloat())   // ios: Views/EarningsRiver.swift:407,408
            }
            text(if (now) tr("회사가 이번 분기 내 몫으로 번 순이익") else tr("회사가 그 분기 내 몫으로 번 순이익"), 12f, FontWeight.Normal, ec.muted, goldX, 660f, 0.5f, fade(t, 2.1))   // ios: Views/EarningsRiver.swift:411
        }
    }

    /** 수위표: 막대 x268 y62, 눈금 x261~279. S = 지금 물높이 (기다리는 중이면 null), lastS = 기다리는 중 지난 분기 높이 */
    // ios: Views/EarningsRiver.swift:416
    fun gauge(lv: (Double) -> Float, S: Float?, t: Double, lastS: Float? = null) = with(ds) {
        drawRect(ec.muted.copy(alpha = 0.55f), Offset(268f, 62f), Size(4f, 238f))   // ios: Views/EarningsRiver.swift:417
        val tick = { y: Float, strong: Boolean ->   // ios: Views/EarningsRiver.swift:418
            drawRect(if (strong) ec.fg else ec.onwater.copy(alpha = 0.6f), Offset(261f, y - (if (strong) 1f else 0.75f)), Size(18f, if (strong) 2f else 1.5f))   // ios: Views/EarningsRiver.swift:419,420
        }
        val others = ArrayList<Pair<Float, String>>()   // ios: Views/EarningsRiver.swift:422
        for (g in s.gauge) {   // ios: Views/EarningsRiver.swift:423
            val y = lv(g.co ?: 0.0)   // ios: Views/EarningsRiver.swift:424
            val strong = g.lastYear   // ios: Views/EarningsRiver.swift:425
            tick(y, strong)   // ios: Views/EarningsRiver.swift:426
            val v = g.co?.let(::eok) ?: "–"   // ios: Views/EarningsRiver.swift:427
            if (strong) text(tr("작년 $v"), 11f, FontWeight.Normal, ec.muted, 284f, y - 5, 0f)   // ios: Views/EarningsRiver.swift:428
            else others += (y + 4) to v   // ios: Views/EarningsRiver.swift:429
        }
        others.sortBy { it.first }   // ios: Views/EarningsRiver.swift:431
        var last = -100f   // ios: Views/EarningsRiver.swift:432
        for (i in others.indices) { if (others[i].first - last < 13) others[i] = (last + 13) to others[i].second; last = others[i].first }   // 겹치면 앞보다 13 아래로   // ios: Views/EarningsRiver.swift:433
        for (o in others) text(o.second, 11f, FontWeight.Normal, ec.onwater, 284f, o.first, 0f, 0.75)   // ios: Views/EarningsRiver.swift:434
        if (S != null) {   // ios: Views/EarningsRiver.swift:435
            tick(S, false)   // ios: Views/EarningsRiver.swift:436
            val op = fade(t, 0.3)   // ios: Views/EarningsRiver.swift:437
            text(if (now) tr("지금 ${eok(s.coRevenue ?: 0.0)}") else tr("이때 ${eok(s.coRevenue ?: 0.0)}"), 11f, FontWeight.SemiBold, ec.onwater, 284f, S + 17, 0f, op)   // ios: Views/EarningsRiver.swift:438
            s.revYoY?.let { text(it, 11f, FontWeight.SemiBold, ec.onwater, 284f, S + 31, 0f, op) }   // ios: Views/EarningsRiver.swift:439
        }
        // 기다리는 중: 지난 분기 높이 눈금. 물이 다 차오른 뒤(1.8초) 나타난다
        val L = lastS   // ios: Views/EarningsRiver.swift:442
        if (L != null) {
            val op = fade(t, 1.8)   // ios: Views/EarningsRiver.swift:443
            drawRect(ec.onwater.copy(alpha = 0.6f * op.toFloat()), Offset(261f, L - 0.75f), Size(18f, 1.5f))   // ios: Views/EarningsRiver.swift:444,445
            text(tr("지난 ${eok(s.coRevenue ?: 0.0)}"), 11f, FontWeight.SemiBold, ec.onwater, 284f, L + 17, 0f, op)   // ios: Views/EarningsRiver.swift:447
        }
    }

    fun trim(p: Path, from: Double, to: Double): Path {
        val pm = PathMeasure(); pm.setPath(p, false)
        val out = Path(); pm.getSegment(pm.length * from.toFloat(), pm.length * to.toFloat(), out, true)
        return out
    }

    /** 흐르는 실: 시작 색 strand → 끝 색(옅어짐(null) 또는 떡잎색). 적자 마지막 실은 hollow 만큼 속 빈 테두리 */
    // ios: Views/EarningsRiver.swift:452
    fun flow(p: Path, w: Float, fadeTo: Offset, sx: Float, end: Color?, grow: Double, hollow: Float) = with(ds) {
        if (grow <= 0) return@with   // ios: Views/EarningsRiver.swift:453
        val tp = trim(p, 0.0, grow)   // ios: Views/EarningsRiver.swift:454
        if (hollow > 0) { strand(tp, w, end ?: ec.strand, hollow); return@with }   // ios: Views/EarningsRiver.swift:455
        val sh = Brush.linearGradient(listOf(ec.strand, end ?: ec.strand.copy(alpha = 0f)), start = Offset(sx, BED), end = fadeTo)   // ios: Views/EarningsRiver.swift:456,457,458
        drawPath(tp, sh, style = Stroke(w, cap = StrokeCap.Butt))   // ios: Views/EarningsRiver.swift:459
    }

    /** 실 하나. hollow > 0 이면 그만큼(모자란 몫)은 속 빈 테두리로 옆에 붙인다 */
    // ios: Views/EarningsRiver.swift:463
    fun strand(p: Path, w: Float, c: Color, hollow: Float) = with(ds) {
        val solid = max(0f, w - hollow)   // ios: Views/EarningsRiver.swift:464
        if (solid > 0) translate(left = -hollow / 2) { drawPath(p, c, style = Stroke(solid, cap = StrokeCap.Butt)) }   // ios: Views/EarningsRiver.swift:465,466,467,468
        if (hollow > 0) {   // ios: Views/EarningsRiver.swift:471
            val cv = drawContext.canvas
            cv.saveLayer(Rect(0f, 0f, W, H), Paint())   // ios: Views/EarningsRiver.swift:472
            translate(left = solid / 2, top = 0f) {   // ios: Views/EarningsRiver.swift:473
                drawPath(p, c, style = Stroke(hollow, cap = StrokeCap.Butt))   // ios: Views/EarningsRiver.swift:474
                drawPath(p, Color.Black, style = Stroke(max(0f, hollow - 3), cap = StrokeCap.Butt), blendMode = BlendMode.DstOut)   // ios: Views/EarningsRiver.swift:475,476
            }
            cv.restore()
        }
    }

    /** 떡잎 (331,490): 줄기 0,0→0,−11, 잎 (±5,−13) rx5.5 ry2.6 ∓28도 */
    // ios: Views/EarningsRiver.swift:482
    fun sprout(scale: Double) = with(ds) {
        if (scale <= 0) return@with   // ios: Views/EarningsRiver.swift:483
        withTransform({ translate(331f, 490f); scale(scale.toFloat(), scale.toFloat(), pivot = Offset.Zero) }) {   // ios: Views/EarningsRiver.swift:484,485,486
            drawLine(ec.sprout, Offset.Zero, Offset(0f, -11f), strokeWidth = 1.5f)   // ios: Views/EarningsRiver.swift:487,488
            for (side in listOf(-1f, 1f)) {   // ios: Views/EarningsRiver.swift:489
                withTransform({ translate(5f * side, -13f); rotate(-28f * side, pivot = Offset.Zero) }) {   // ios: Views/EarningsRiver.swift:490,491,492
                    drawOval(ec.sprout, Offset(-5.5f, -2.6f), Size(11f, 5.2f))   // ios: Views/EarningsRiver.swift:493
                }
            }
        }
    }
}
