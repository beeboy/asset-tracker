package com.naeilo.app.ui.hold

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
// ios: Views/EarningsRiver.swift:47
class EarnPal(private val dark: Boolean) {
    private fun c(l: Long, d: Long) = Color((if (dark) d else l) or 0xFF000000)
    val water = c(0x7FA9B8, 0x3F6F80)
    val water2 = c(0xA9C7D2, 0x2C5160)
    val strand = c(0xB9C6CB, 0x4A5A60)
    val ghost = c(0xA9B3B0, 0x5D6965)
    val sprout = c(0x6FA85A, 0x8CC477)
    val gold = c(0xC8962E, 0xE0B04A)
    val fg = c(0x1D2624, 0xE4EBE8)
    val muted = c(0x6B7773, 0x8C9893)
    val screen = c(0xFFFFFF, 0x1A2220)
    val onwater = c(0xF4FAFB, 0xE4F0F3)
    companion object { val Light = EarnPal(false); val Dark = EarnPal(true) }
}
val EarnColor: EarnPal @Composable get() = if (LocalPal.current.isDark) EarnPal.Dark else EarnPal.Light

/** 강 팝업을 열 종목·수량 */
data class EarnTarget(val sym: Symbol, val qty: Double)   // ios: Views/EarningsRiver.swift:133

// ── 목록 작은 막대 ──

/** 폭 14 높이 26: 옅은 = 작년 같은 분기 내 몫 이익, 금색 = 이번 분기. 숫자 없음. 자료가 없으면(ETF) 짧은 줄표.
 *  높이는 크기(절댓값) 비율, 손실(순이익<0)은 같은 높이 규칙으로 속 빈 테두리 */
// ios: Views/EarningsRiver.swift:71
@Composable
fun EarnBars(s: Earnings.Summary?) {
    val still = LocalReduceMotion.current
    val ec = EarnColor
    Box(Modifier.size(14.dp, 26.dp), contentAlignment = Alignment.BottomCenter) {
        if (s != null) {
            val grow = remember(s.symbol) { Animatable(if (s.fresh && !still) 0f else 1f) }
            // 실적 나온 날 한 번 차오른다 (.9초 easeOut, .2초 뒤)
            LaunchedEffect(s.symbol) { if (grow.value < 1f) { delay(200); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) } }
            val a = s.lastYearNet ?: 0.0; val b = s.net ?: 0.0; val top = max(abs(a), abs(b))
            Row(Modifier.graphicsLayer { scaleY = grow.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                Bar(if (top > 0) abs(a) / top else 0.0, ec.ghost, hollow = a < 0)
                Bar(if (top > 0) abs(b) / top else 0.0, ec.gold, hollow = b < 0)
            }
        } else {
            Box(Modifier.size(8.dp, 2.dp).background(Theme.muted, CapsuleShape))
        }
    }
}

// ios: Views/EarningsRiver.swift:99
@Composable
private fun Bar(h: Double, c: Color, hollow: Boolean) {
    // 0(자료 없음)도 바닥 2 는 보이게. 손실은 채움 없이 같은 색 테두리
    val r = RoundedCornerShape(1.5.dp)
    if (hollow) Box(Modifier.size(6.dp, max(3.0, 26 * h).dp).border(1.2.dp, c, r))
    else Box(Modifier.size(6.dp, max(2.0, 26 * h).dp).background(c, r))
}

/** 목록 위 첫 안내 (닫으면 다시 안 뜸) */
// ios: Views/EarningsRiver.swift:112
@Composable
fun EarnTip() {
    var closed by remember { mutableStateOf(com.naeilo.app.core.Store.defaults.getBoolean("earn.tipClosed", false)) }
    if (closed) return
    Row(
        Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp, 14.dp).background(EarnColor.gold, RoundedCornerShape(1.5.dp)))
        T("금색 막대 누르면 내 몫 이익", Modifier.weight(1f), style = appFont(13f), color = Theme.sub)
        Box(Modifier.size(32.dp).plainClickable {
            closed = true; com.naeilo.app.core.Store.defaults.edit().putBoolean("earn.tipClosed", true).apply()
        }.semantics { contentDescription = tr("안내 닫기") }, contentAlignment = Alignment.Center) {
            Sym("xmark", 12f, Theme.muted, scaled = false)
        }
    }
}

// ── 강 팝업 ──

// ios: Views/EarningsRiver.swift:135
@Composable
fun EarningsRiverSheet(target: EarnTarget?, onDismiss: () -> Unit) {
    Sheet(target != null, onDismiss) {
        val t = target ?: return@Sheet
        val s = EarningsFeed.shared.summary(t.sym, t.qty)
        Column(Modifier.fillMaxSize().background(EarnColor.screen), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).plainClickable(onDismiss).semantics { contentDescription = tr("닫기") }, contentAlignment = Alignment.Center) {
                    Sym("xmark", 15f, Theme.sub, scaled = false)
                }
            }
            if (s != null) {
                EarningsRiver(t.sym.name, t.qty, s, Modifier.widthIn(max = Theme.maxWidth).padding(start = 8.dp, end = 8.dp, bottom = 12.dp))
            } else {
                // 자료가 없으면 팝업을 열지 않는다 (목록이 상세로 보냄). 그사이 자료가 사라졌으면 바로 닫는다
                LaunchedEffect(Unit) { onDismiss() }
            }
        }
    }
}

private const val W = 360f; private const val H = 680f; private const val BED = 300f
private const val TOTAL = 50f; private const val MID = 175f
private const val END = 2.9   // 마지막 동작(떡잎)이 끝나는 초

/** 강 그림 (시안 판16). 물이 차고 실이 흐른 뒤 떡잎이 핀다(약 3초, 팝업이 열린 동안만). 동작 줄이기면 다 흐른 정지 화면 */
// ios: Views/EarningsRiver.swift:166
@Composable
fun EarningsRiver(name: String, qty: Double, s: Earnings.Summary, modifier: Modifier = Modifier, frozen: Boolean = false) {
    val still = LocalReduceMotion.current || frozen
    var el by remember { mutableFloatStateOf(0f) }
    // 한 번 흐르고(기다리는 중이면 물만 차오르고) 멈춘다
    LaunchedEffect(s.symbol, still) {
        if (still) return@LaunchedEffect
        val t0 = withFrameNanos { it }
        while (el < END + 0.1) { el = withFrameNanos { (it - t0) / 1e9f } }
    }
    val ec = EarnColor
    val tm = rememberTextMeasurer()
    val dens = LocalDensity.current
    val title = tr("$name${if (TownLook.batchim(name)) "이" else "가"} 번 돈 중 내 몫")
    Canvas(modifier.fillMaxWidth().aspectRatio(W / H).semantics { contentDescription = a11y(s, title) }) {
        val k = min(size.width / W, size.height / H)
        withTransform({ translate(left = (size.width - W * k) / 2, top = 0f); scale(k, k, pivot = Offset.Zero) }) {
            val d = RiverDraw(this, tm, dens.density * dens.fontScale, ec, s, name, qty, title, still)
            d.draw(if (still) 99.0 else el.toDouble())
        }
    }
}

// ios: Views/EarningsRiver.swift:202
private fun a11y(s: Earnings.Summary, title: String): String {
    s.pending?.let { return "$title. $it 실적 발표, 숫자를 기다리는 중" }
    var t = "$title ${s.months}월. "
    t += "내 몫 매출 ${Earnings.money(s.revenue ?: 0.0, s.currency)}"
    s.revYoY?.let { t += ", 작년보다 $it" }
    t += if (s.loss) ". 이번 분기 내 몫 손실 ${Earnings.money(s.short, s.currency)}" else ". 내 몫 순이익 ${s.netText}"
    s.netKrw?.let { t += ", $it" }
    return t
}

/** 강 한 장 그리기. 좌표는 viewBox 360×680 */
private class RiverDraw(
    val ds: DrawScope, val tm: TextMeasurer, val spPx: Float, val ec: EarnPal,
    val s: Earnings.Summary, val name: String, val qty: Double, val title: String, val still: Boolean,
) {
    // 0~1 구간 안에서 0~1 로 (밖은 0 또는 1)
    fun seg(t: Double, a: Double, b: Double) = max(0.0, min(1.0, (t - a) / (b - a)))
    /** cubic-bezier(.4,0,.2,1) */
    fun ease(x: Double): Double {
        if (x <= 0) return 0.0; if (x >= 1) return 1.0
        fun bz(u: Double, p1: Double, p2: Double) = 3 * (1 - u) * (1 - u) * u * p1 + 3 * (1 - u) * u * u * p2 + u * u * u
        var lo = 0.0; var hi = 1.0
        repeat(20) { val m = (lo + hi) / 2; if (bz(m, 0.4, 0.2) < x) lo = m else hi = m }
        return bz((lo + hi) / 2, 0.0, 1.0)
    }
    /** 튀듯 커짐 (back-out) */
    fun pop(x: Double): Double { val c = 1.70158; return if (x <= 0) 0.0 else 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2) }
    /** 글자: at 초부터 .4초 페이드 */
    fun fade(t: Double, at: Double) = seg(t, at, at + 0.4)

    /** anchor: 0 왼쪽(leading)·0.5 가운데·1 오른쪽(trailing), 세로는 가운데 */
    fun text(str: String, size: Float, w: FontWeight, c: Color, x: Float, y: Float, ax: Float, op: Double = 1.0, serif: Boolean = false) {
        if (op <= 0) return
        val st = TextStyle(fontSize = (size / spPx).sp, fontWeight = w, color = c, fontFamily = if (serif) FontFamily.Serif else FontFamily.Default)
        val lay = tm.measure(AnnotatedString(str), st, softWrap = false)
        ds.drawText(lay, topLeft = Offset(x - lay.size.width * ax, y - lay.size.height / 2f), alpha = op.toFloat())
    }

    /** 회사 매출 "570억" (억 단위) */
    fun eok(v: Double): String {
        val e = v / 1e8
        return (if (e >= 10) Earnings.grouped(Math.round(e)) else cformat("%.1f", e)) + "억"
    }

    fun draw(t: Double) = with(ds) {
        val fmt = { v: Double -> Earnings.money(v, s.currency) }
        // 제목
        text(title, 20f, FontWeight.Bold, ec.fg, 24f, 42f, 0f, serif = true)

        // 물높이 lv(v) = 270 − (v−min)/(max−min)×210 (지난 4분기 + 지금, 회사 매출)
        val co = s.coRevenue ?: 0.0
        val vals = s.gauge.map { it.co ?: 0.0 } + co
        val lo = vals.minOrNull() ?: 0.0; val hi = vals.maxOrNull() ?: 1.0
        val lv = { v: Double -> if (hi > lo) 270f - ((v - lo) / (hi - lo)).toFloat() * 210f else 165f }
        val S = lv(co)

        // 물: rect 0,S~300 + 물결. 위→아래로 채워진다 (.6초). 기다리는 중이면 강바닥에서 지난 분기 높이까지 2초 차오른다
        val wet = s.pending == null
        val top = if (wet) S else BED - (BED - S) * ease(seg(t, 0.0, 2.0)).toFloat()
        val fill = if (wet) ease(seg(t, 0.0, 0.6)).toFloat() else 1f
        clipRect(0f, top, W, top + (BED - top) * fill) {
            drawRect(ec.water, Offset(0f, top), Size(W, BED - top))
            val w = Path().apply {
                moveTo(0f, 230f); cubicTo(90f, 210f, 200f, 250f, 360f, 225f)
                lineTo(360f, 300f); lineTo(0f, 300f); close()
            }
            drawPath(w, ec.water2)
        }
        // 강 위 글: 물이 낮아 글이 물 밖이면 진한 글자로
        val base = min(S, 200f)
        val c1 = if (base + 40 - 8 > S) ec.onwater else ec.fg; val c2 = if (base + 70 - 12 > S) ec.onwater else ec.fg
        s.pending?.let { p ->
            val md = p.split("-").mapNotNull { it.toIntOrNull() }
            val day = if (md.size == 3) "${md[1]}월 ${md[2]}일" else p
            text(tr("실적이 나왔어요 · $day"), 12f, FontWeight.Normal, c1, 24f, base + 40, 0f, 0.85 * fade(t, 1.8))
            text(tr("숫자가 들어오면 강이 차요"), 20f, FontWeight.Bold, c2, 24f, base + 70, 0f, fade(t, 1.8), serif = true)
            gauge(lv, null, t)
            return@with
        }
        text(tr("$name · 이번 분기"), 12f, FontWeight.Normal, c1, 24f, base + 40, 0f, 0.85 * fade(t, 0.3))
        val unit = if (s.currency == "USD") tr(" 달러") else " " + s.currency
        text(tr("매출 ${eok(co)}$unit"), 20f, FontWeight.Bold, c2, 24f, base + 70, 0f, fade(t, 0.3), serif = true)
        gauge(lv, S, t)

        // 물안개: 남는 돈(이자·세금·기타). 피었다가 실 끝 전에 걷힌다 (글자 없음). 흐림 10 은 가장자리로 옅어지는 둥근 무늬로
        val rev0 = s.revenue
        if (s.mist > 0 && rev0 != null && rev0 > 0) {
            val m = sin(PI * seg(t, 0.6, 1.7)) * (if (still) 0 else 1)
            if (m > 0) {
                val r = 14f + 40f * min(1.0, s.mist / rev0 * 4).toFloat()
                for ((dx, dy) in listOf(-50f to 0f, 0f to -8f, 52f to 2f)) {
                    val cx = MID + dx; val cy = S - 16 + dy
                    withTransform({ scale(1f, 0.35f, pivot = Offset(cx, cy)) }) {
                        val R = r + 10f
                        drawCircle(Brush.radialGradient(0f to ec.water2, (r - 10f).coerceAtLeast(0f) / R to ec.water2, 1f to ec.water2.copy(alpha = 0f), center = Offset(cx, cy), radius = R),
                            radius = R, center = Offset(cx, cy), alpha = (0.55 * m).toFloat())
                    }
                }
            }
        }

        // 내 몫 (강바닥 아래)
        text(tr("내 몫 ${fmt(s.revenue ?: 0.0)}"), 12f, FontWeight.SemiBold, ec.fg, 216f, 326f, 0f, fade(t, 0.4))
        text(tr("${qty.swFmt()}주만큼 · 확대"), 11f, FontWeight.Normal, ec.muted, 216f, 343f, 0f, fade(t, 0.4))

        // 실: 왼→오 원가·판관비·이익·연구비. 굵기 = 내 몫 매출 안 비율 × 50, 이익 실은 최소 3. 가운데 175 에 나란히
        val rev = max(s.revenue ?: 0.0, 1e-12); val sc = TOTAL / rev.toFloat()
        val byK = s.branches.associate { it.k to it.v }
        val ss = ArrayList<Pair<String, Float>>()
        byK["cogs"]?.let { ss += "cogs" to it.toFloat() * sc }
        byK["spent"]?.let { ss += "spent" to it.toFloat() * sc }
        byK["sga"]?.let { ss += "sga" to it.toFloat() * sc }
        if (!s.loss) ss += "net" to max(3f, (s.net ?: 0.0).toFloat() * sc)
        byK["rnd"]?.let { ss += "rnd" to it.toFloat() * sc }
        var x = MID - ss.sumOf { it.second.toDouble() }.toFloat() / 2
        val hollow = if (s.loss) s.short.toFloat() * sc else 0f   // 적자: 마지막 실 속 빈 테두리
        var goldX = MID
        ss.forEachIndexed { i, (key, sw) ->
            val sx = x + sw / 2; x += sw
            val h = if (i == ss.size - 1) hollow else 0f
            val p = Path().apply { moveTo(sx, BED) }
            when (key) {
                "cogs", "spent" -> {
                    p.lineTo(sx, 350f); p.cubicTo(sx, 400f, 70f, 392f, 40f, 440f)
                    flow(p, sw, Offset(40f, 440f), sx, null, ease(seg(t, 0.0, 1.0)), h)
                    val label = if (key == "cogs") tr("원가 ${fmt(byK["cogs"] ?: 0.0)}") else tr("쓴 돈 ${fmt(byK["spent"] ?: 0.0)}")
                    text(label, 12f, FontWeight.Normal, ec.muted, 24f, 466f, 0f, fade(t, 0.7))
                }
                "sga" -> {
                    p.lineTo(sx, 470f); p.cubicTo(sx, 508f, 100f, 506f, 70f, 540f)
                    flow(p, sw, Offset(70f, 540f), sx, null, ease(seg(t, 0.4, 1.7)), h)
                    text(tr("판매·관리비 ${fmt(byK["sga"] ?: 0.0)}"), 12f, FontWeight.Normal, ec.muted, 24f, 564f, 0f, fade(t, 1.4))
                }
                "rnd" -> {
                    p.lineTo(sx, 410f); p.cubicTo(sx, 450f, 290f, 448f, 320f, 488f)
                    flow(p, sw, Offset(320f, 488f), sx, ec.sprout, ease(seg(t, 0.2, 1.4)), h)
                    sprout(pop(seg(t, 2.2, 2.8)))
                    text(tr("연구비 ${fmt(byK["rnd"] ?: 0.0)}"), 12f, FontWeight.Normal, ec.muted, 336f, 512f, 1f, fade(t, 1.1))
                }
                else -> {   // net: 금색 실이 곧게 내려간다
                    goldX = sx
                    val ly = s.lastYearNet; val n = s.net
                    if (ly != null && ly > 0 && n != null && n > 0) {
                        val g = Path().apply { moveTo(sx + 14, 520f); lineTo(sx + 14, 600f) }
                        drawPath(trim(g, ease(seg(t, 1.5, 2.1))), ec.ghost.copy(alpha = 0.6f), style = Stroke(sw * (ly / n).toFloat(), cap = StrokeCap.Butt))
                        text(tr("작년 ${fmt(ly)}"), 11f, FontWeight.Normal, ec.muted, sx + 20, 560f, 0f, fade(t, 2.0))
                    }
                    p.lineTo(sx, 600f)
                    drawPath(trim(p, ease(seg(t, 0.0, 2.0))), ec.gold, style = Stroke(sw, cap = StrokeCap.Butt))
                }
            }
        }

        // 금색 끝: 큰 금액(+원화) · 설명. 적자면 옅은 손실
        if (s.loss) {
            text(fmt(-s.short), 30f, FontWeight.Bold, ec.muted, MID, 636f, 0.5f, fade(t, 1.9), serif = true)
            text(tr("이번 분기 내 몫 손실"), 12f, FontWeight.Normal, ec.muted, MID, 660f, 0.5f, fade(t, 2.1))
        } else {
            val op = fade(t, 1.9)
            if (op > 0) {
                val big = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = (30f / spPx).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = ec.gold)) { append(s.netText) }
                    s.netKrw?.let { w -> withStyle(SpanStyle(fontSize = (13f / spPx).sp, fontWeight = FontWeight.SemiBold, color = ec.muted)) { append("  $w") } }
                }
                val lay = tm.measure(big, TextStyle.Default, softWrap = false)
                drawText(lay, topLeft = Offset(goldX - lay.size.width / 2f, 636f - lay.size.height / 2f), alpha = op.toFloat())
            }
            text(tr("회사가 이번 분기 내 몫으로 번 순이익"), 12f, FontWeight.Normal, ec.muted, goldX, 660f, 0.5f, fade(t, 2.1))
        }
    }

    /** 수위표: 막대 x268 y62, 눈금 x261~279. S = 지금 물높이 (기다리는 중이면 null) */
    // ios: Views/EarningsRiver.swift:363
    fun gauge(lv: (Double) -> Float, S: Float?, t: Double) = with(ds) {
        drawRect(ec.muted.copy(alpha = 0.55f), Offset(268f, 62f), Size(4f, 238f))
        val tick = { y: Float, strong: Boolean ->
            drawRect(if (strong) ec.fg else ec.onwater.copy(alpha = 0.6f), Offset(261f, y - (if (strong) 1f else 0.75f)), Size(18f, if (strong) 2f else 1.5f))
        }
        val others = ArrayList<Pair<Float, String>>()
        for (g in s.gauge) {
            val y = lv(g.co ?: 0.0)
            val strong = g.lastYear
            tick(y, strong)
            val v = g.co?.let(::eok) ?: "–"
            if (strong) text(tr("작년 $v"), 11f, FontWeight.Normal, ec.muted, 284f, y - 5, 0f)
            else others += (y + 4) to v
        }
        others.sortBy { it.first }
        var last = -100f
        for (i in others.indices) { if (others[i].first - last < 13) others[i] = (last + 13) to others[i].second; last = others[i].first }   // 겹치면 앞보다 13 아래로
        for (o in others) text(o.second, 11f, FontWeight.Normal, ec.onwater, 284f, o.first, 0f, 0.75)
        if (S != null) {
            tick(S, false)
            val op = fade(t, 0.3)
            text(tr("지금 ${eok(s.coRevenue ?: 0.0)}"), 11f, FontWeight.SemiBold, ec.onwater, 284f, S + 17, 0f, op)
            s.revYoY?.let { text(it, 11f, FontWeight.SemiBold, ec.onwater, 284f, S + 31, 0f, op) }
        }
    }

    fun trim(p: Path, to: Double): Path {
        val pm = PathMeasure(); pm.setPath(p, false)
        val out = Path(); pm.getSegment(0f, pm.length * to.toFloat(), out, true)
        return out
    }

    /** 흐르는 실: 시작 색 strand → 끝 색(옅어짐(null) 또는 떡잎색). 적자 마지막 실은 hollow 만큼 속 빈 테두리 */
    // ios: Views/EarningsRiver.swift:391
    fun flow(p: Path, w: Float, fadeTo: Offset, sx: Float, end: Color?, grow: Double, hollow: Float) = with(ds) {
        if (grow <= 0) return@with
        val tp = trim(p, grow)
        if (hollow > 0) { strand(tp, w, end ?: ec.strand, hollow); return@with }
        val sh = Brush.linearGradient(listOf(ec.strand, end ?: ec.strand.copy(alpha = 0f)), start = Offset(sx, BED), end = fadeTo)
        drawPath(tp, sh, style = Stroke(w, cap = StrokeCap.Butt))
    }

    /** 실 하나. hollow > 0 이면 그만큼(모자란 몫)은 속 빈 테두리로 옆에 붙인다 */
    // ios: Views/EarningsRiver.swift:402
    fun strand(p: Path, w: Float, c: Color, hollow: Float) = with(ds) {
        val solid = max(0f, w - hollow)
        if (solid > 0) translate(left = -hollow / 2) { drawPath(p, c, style = Stroke(solid, cap = StrokeCap.Butt)) }
        if (hollow > 0) {
            val cv = drawContext.canvas
            cv.saveLayer(Rect(0f, 0f, W, H), Paint())
            translate(left = solid / 2) {
                drawPath(p, c, style = Stroke(hollow, cap = StrokeCap.Butt))
                drawPath(p, Color.Black, style = Stroke(max(0f, hollow - 3), cap = StrokeCap.Butt), blendMode = BlendMode.DstOut)
            }
            cv.restore()
        }
    }

    /** 떡잎 (331,490): 줄기 0,0→0,−11, 잎 (±5,−13) rx5.5 ry2.6 ∓28도 */
    // ios: Views/EarningsRiver.swift:421
    fun sprout(scale: Double) = with(ds) {
        if (scale <= 0) return@with
        withTransform({ translate(331f, 490f); scale(scale.toFloat(), scale.toFloat(), pivot = Offset.Zero) }) {
            drawLine(ec.sprout, Offset.Zero, Offset(0f, -11f), strokeWidth = 1.5f)
            for (side in listOf(-1f, 1f)) {
                withTransform({ translate(5f * side, -13f); rotate(-28f * side, pivot = Offset.Zero) }) {
                    drawOval(ec.sprout, Offset(-5.5f, -2.6f), Size(11f, 5.2f))
                }
            }
        }
    }
}
