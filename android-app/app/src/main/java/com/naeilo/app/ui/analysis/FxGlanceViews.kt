package com.naeilo.app.ui.analysis

import androidx.compose.foundation.layout.requiredSize
import com.naeilo.app.core.tr
import com.naeilo.app.core.cformat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.Sym
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.core.calc.SiteCalc
import com.naeilo.app.core.calc.goalToDate
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.swFmtFrac
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Period
import com.naeilo.app.ui.hold.HoldFmt
import com.naeilo.app.ui.hold.HoldSeg
import com.naeilo.app.data.StubPriceProvider
import com.naeilo.app.data.short
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.Market
import com.naeilo.app.net.SiteStats
import com.naeilo.app.net.YahooSample
import com.naeilo.app.net.last
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.pulse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 환율 영향·종목 한눈에 (Views/AnalysisDetail.swift 2192-2691). 숫자만, 사거나 팔라는 문구 없음.

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
/** Int.formatted(): 세 자리 쉼표 */
private fun Long.f(): String = cformat("%,d", this)
/** .monospacedDigit() */
private fun TextStyle.tnum() = copy(fontFeatureSettings = "tnum")
/** .position(x:y:): 가운데를 (x, y) 에 둔다 (y 는 화소) */
private fun Modifier.centerAt(x: Dp, yPx: Float): Modifier = this.layout { mm, cc ->
    val p = mm.measure(cc.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place((x.toPx() - p.width / 2f).roundToInt(), (yPx - p.height / 2f).roundToInt()) }
}

// MARK: 환율 영향

// ios: Views/AnalysisDetail.swift:2084,2085,2091
@Composable
fun FxImpactView(m: AppModel) {
    var period by remember { mutableStateOf("1y") }   // ios: Views/AnalysisDetail.swift:2086
    /** 지금 환율(BASE)에서 옮긴 폭(원). 입구 '환율 10원↓'로 들어오니 −10 에서 시작. 폭으로 두어 시세가 늦게 와도 변화가 같게 */
    var off by remember { mutableStateOf(-10.0) }   // ios: Views/AnalysisDetail.swift:1889

    // 웹(전망 › 환율 영향)과 같은 자료·식: 지난 원/달러는 사이트 값(calc.json), 앞날 범위는 웹 전망 모형의 환율 μ·σ(stats.json)로
    // 지금·exp((μ − σ²/2)t + zσ√t)
    // 차트와 앞날 범위는 웹처럼 지금 환율(FX), 화면의 "지금 환율"·평가액 계산은 웹 fxBase 와 같게 매매기준율(BASE) 하나로
    val FX = Market.shared.fx.last; val BASE = Market.shared.valFx; val c = SiteCalc.shared.file?.fx; val fxp = SiteStats.fx(); val mu = fxp.first; val vol = fxp.second   // ios: Views/AnalysisDetail.swift:2095
    val usd = m.rows.filter { it.sym.currency == Currency.usd }   // ios: Views/AnalysisDetail.swift:2096
    val usdV = usd.fold(0.0) { a, r -> a + r.value }; val usdW = usdV / max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:2097
    val n = mapOf("1m" to 22, "3m" to 64, "1y" to 253, "3y" to 780)[period] ?: 253   // ios: Views/AnalysisDetail.swift:2098
    val label = mapOf("1m" to "1개월", "3m" to "3개월", "1y" to "1년", "3y" to "3년")[period] ?: "1년"   // ios: Views/AnalysisDetail.swift:2099
    val yh = YahooSample.history["FX"]   // ios: Views/AnalysisDetail.swift:2100
    val closesAll = c?.closes ?: yh?.closes ?: emptyList()   // ios: Views/AnalysisDetail.swift:2101
    val k0 = max(0, closesAll.size - n); val closes = remember(closesAll, k0) { closesAll.subList(k0, closesAll.size).toList() }   // ios: Views/AnalysisDetail.swift:2102
    val past = remember(closes) { closes.mapIndexed { i, v -> Pair(if (closes.size > 1) i.toDouble() / (closes.size - 1) - 1 else 0.0, v) } }   // ios: Views/AnalysisDetail.swift:2103
    val q = { z: Double -> (0..20).map { i -> val t = i.toDouble() / 20; Pair(t, SiteCalc.fxAt(now = FX, t = t, z = z, mu = mu, vol = vol)) } }   // ios: Views/AnalysisDetail.swift:2104
    val end = listOf(1.645, 0.674, -0.674, -1.645).map { SiteCalc.fxAt(now = FX, t = 1.0, z = it, mu = mu, vol = vol) }   // ios: Views/AnalysisDetail.swift:2105
    val vals = past.map { it.second } + listOf(FX) + end   // ios: Views/AnalysisDetail.swift:2106
    val ticks = remember(vals) { fxTicks(lo = vals.minOrNull() ?: FX, hi = vals.maxOrNull() ?: FX) }   // ios: Views/AnalysisDetail.swift:2107
    val hi = ticks.lastOrNull() ?: (FX + 10); val lo = ticks.firstOrNull() ?: (FX - 10)   // ios: Views/AnalysisDetail.swift:2108
    val X = { t: Double -> if (t <= 0) (t + 1) * 0.7 else 0.7 + t * 0.3 }   // ios: Views/AnalysisDetail.swift:2109
    val Y = { v: Double -> max(0.0, min(1.0, (hi - v) / max(1.0, hi - lo))) }   // ios: Views/AnalysisDetail.swift:2110
    val valAt = { r: Double -> m.total + usdV * (r / BASE - 1) }   // ios: Views/AnalysisDetail.swift:2111
    val r = BASE + off; val dv = valAt(r) - m.total   // ios: Views/AnalysisDetail.swift:1912
    val tY = max(0.01, (m.goalToDate.toEpochMilli() - System.currentTimeMillis()) / 1000.0 / (365.25 * 86400))   // ios: Views/AnalysisDetail.swift:2114
    val goalD = DateTimeFormatter.ofPattern("yyyy.M", AppModel.dateLocale).withZone(ZoneId.systemDefault()).format(m.goalToDate)   // ios: Views/AnalysisDetail.swift:2115
    val at = { z: Double -> SiteCalc.fxAt(now = FX, t = tY, z = z, mu = mu, vol = vol).swRound().toLong().f() }   // ios: Views/AnalysisDetail.swift:2116
    val won = { v: Double -> "${v.swRound().toLong().f()}원" }   // ios: Views/AnalysisDetail.swift:2117
    val eff1y = c?.ret_1y?.let { usdW * it }   // ios: Views/AnalysisDetail.swift:2118
    // ios: Views/AnalysisDetail.swift:2119
    val baseDay: String = run {
        val s = Market.shared.marUse?.date ?: ""   // ios: Views/AnalysisDetail.swift:2120
        val p = s.split("-").mapNotNull { it.toIntOrNull() }   // ios: Views/AnalysisDetail.swift:2121
        if (p.size == 3) return@run "${p[1]}/${p[2]}"   // ios: Views/AnalysisDetail.swift:2122
        DateTimeFormatter.ofPattern("M/d", AppModel.dateLocale).withZone(ZoneId.systemDefault()).format(Instant.now())   // ios: Views/AnalysisDetail.swift:2123
    }   // ios: Views/AnalysisDetail.swift:2124
    val lo1 = c?.lo1; val hi1 = c?.hi1   // ios: Views/AnalysisDetail.swift:2126
    // 지금을 가운데 두고 좌우 같은 폭: 슬라이더 손잡이가 '지금' 칩 아래에 온다
    val span = 150.0   // ios: Views/AnalysisDetail.swift:1926

    LaunchedEffect(Unit) { SiteCalc.shared.refresh() }   // ios: Views/AnalysisDetail.swift:2271
    // ios: Views/AnalysisDetail.swift:2269,2270 (제목 줄은 늘 한 줄: NavBar)
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        // 설명 줄이 없어 흰 띠만 남았다: 띠 없이 제목만 건다
        // ios: Views/AnalysisDetail.swift:2130
        PinnedLayout(header = {
            DetailHead(title = "환율이 바뀌면?", sub = "")   // ios: Views/AnalysisDetail.swift:2132
        }, content = {   // ios: Views/AnalysisDetail.swift:2133
            // ios: Views/AnalysisDetail.swift:2134,2267
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. 환율이 바뀌면 내 자산은? (입구 숫자와 같은 −10원에서 시작)
                // ios: Views/AnalysisDetail.swift:2210
                if (usdV > 0) {
                    // ios: Views/AnalysisDetail.swift:2211
                    Card {
                        // 제목은 화면 머리('환율이 바뀌면?')로 올렸다
                        // ios: Views/AnalysisDetail.swift:2217,2223,2224
                        Column(Modifier.fillMaxWidth().background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                T("${won(r)}이면", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2218
                                Spacer(Modifier.weight(1f))
                                T("주가 고정", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)
                            }
                            T(AppModel.man(valAt(r)), style = appFont(24f, FontWeight.Bold).tnum(), roll = AppModel.man(valAt(r)))   // ios: Views/AnalysisDetail.swift:2219
                            // ios: Views/AnalysisDetail.swift:2220,2221
                            T(if (abs(dv) < 0.5) "지금과 같음" else (if (dv > 0) "+" else "−") + AppModel.man(abs(dv)),
                                style = appFont(13f, FontWeight.Bold), color = if (abs(dv) < 0.5) Theme.muted else Theme.change(dv))
                        }
                        IosSlider(value = off, onChange = { off = it.swRound() }, range = -span..span, step = 1.0, tint = Theme.blue, modifier = Modifier.semantics { contentDescription = tr("환율") })   // ios: Views/AnalysisDetail.swift:1949
                        // 내린 쪽 · 지금 · 오른 쪽을 같은 폭으로 (입구 '10원↓'가 바로 눌린 칸)
                        // ios: Views/AnalysisDetail.swift:1951
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // ios: Views/AnalysisDetail.swift:1952
                            listOf(-50.0, -10.0, 0.0, 10.0, 50.0).forEach { v ->
                                val on = off == v   // ios: Views/AnalysisDetail.swift:1953
                                // ios: Views/AnalysisDetail.swift:1954
                                Box(Modifier.weight(1f).plainClickable { off = v }.defaultMinSize(minHeight = 34.dp)
                                    .background(if (on) Theme.blue else Theme.card, CapsuleShape).border(1.5.dp, if (on) Theme.blue else Theme.border, CapsuleShape), contentAlignment = Alignment.Center) {
                                    T(if (v == 0.0) "지금" else (BASE + v).swRound().toLong().f(), style = appFont(12f, FontWeight.Bold), maxLines = 1, color = if (on) Color.White else Theme.ink)   // ios: Views/AnalysisDetail.swift:2230
                                }
                            }
                        }
                    }
                }
                // 2. 환율 카드
                // ios: Views/AnalysisDetail.swift:2151
                Card {
                    // ios: Views/AnalysisDetail.swift:2152
                    Row(Modifier.fillMaxWidth()) {
                        // ios: Views/AnalysisDetail.swift:2153
                        Column(Modifier.alignByBaseline(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            T("원/달러", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2154
                            // ios: Views/AnalysisDetail.swift:2155,2156
                            T(BASE.swFmtFrac(0, 1) + "원", style = appFont(26f, FontWeight.Bold).tnum())
                        }
                        Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2158
                        T(baseDay, Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:2159
                    }
                    // ios: Views/AnalysisDetail.swift:2161
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // y축 눈금
                        // ios: Views/AnalysisDetail.swift:2163,2169
                        BoxWithConstraints(Modifier.size(36.dp, 160.dp)) {
                            val hPx = constraints.maxHeight.toFloat()
                            // ios: Views/AnalysisDetail.swift:2164
                            ticks.forEach { t ->
                                // ios: Views/AnalysisDetail.swift:2165,2166
                                T(t.toLong().f(), Modifier.centerAt(18.dp, (Y(t) * hPx).toFloat()), style = appFont(10f).tnum(), color = Theme.muted, maxLines = 1)
                            }
                        }
                        FxChart(past, end, ticks, FX, q, X, Y)
                    }
                    // ios: Views/AnalysisDetail.swift:2199,2200
                    Row(Modifier.fillMaxWidth().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        val st = appFont(11f); val mc = Theme.muted
                        T("$label 전", style = st, color = mc); Spacer(Modifier.weight(1f)); T("오늘", style = st, color = mc); Spacer(Modifier.widthIn(max = 60.dp).fillMaxWidth()); T("1년 뒤", style = st, color = mc)
                    }
                    if (past.size < 2) T("지난 자료 없음 · 지금 값만", style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:2201
                    // ios: Views/AnalysisDetail.swift:2202
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FxLegend(Theme.ink, 2.dp, "실제")   // ios: Views/AnalysisDetail.swift:2203
                        FxLegend(Theme.blue.copy(alpha = 0.4f), 10.dp, "2번 중 1번 · ${end[2].swRound().toLong().f()}~${end[1].swRound().toLong().f()}")   // ios: Views/AnalysisDetail.swift:2204
                        FxLegend(Theme.blue.copy(alpha = 0.15f), 10.dp, "10번 중 9번 · ${end[3].swRound().toLong().f()}~${end[0].swRound().toLong().f()}")   // ios: Views/AnalysisDetail.swift:2205
                    }
                    ChipRow(items = listOf("1m" to "1개월", "3m" to "3개월", "1y" to "1년", "3y" to "3년"), selection = period, onSelect = { period = it }, accent = Theme.blue, fill = true)   // ios: Views/AnalysisDetail.swift:2207
                }
                // 3. 숫자 칸 4개 (금리 일정 칸 모양)
                Card {   // ios: Views/AnalysisDetail.swift:2026
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/AnalysisDetail.swift:2027
                        FxCell("1년 범위", lo1?.let { l -> hi1?.let { "${l.swRound().toLong().f()}~${it.swRound().toLong().f()}" } } ?: "-", "")   // ios: Views/AnalysisDetail.swift:2028
                        FxCell("출렁임", volWord(vol), "연 ${cformat("%.1f", vol * 100)}%")   // Views/AnalysisDetail.swift:2029 (검사 예외: 보간 속 따옴표)
                        FxCell("$goalD 예상", at(0.0), "${at(-1.645)}~${at(1.645)}")   // ios: Views/AnalysisDetail.swift:2030
                        FxCell("환율 손익 · 1년", eff1y?.let { AppModel.sgn(it) } ?: "-", "", color = Theme.change(eff1y ?: 0.0))   // ios: Views/AnalysisDetail.swift:2031
                    }
                    T("지난 흐름 기준 예상", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:2033
                }
            }
        })
    }
}

/** 원/달러 지난 흐름(0.7 폭) + 앞으로 1년 범위(0.3 폭). 선·띠는 크기가 바뀔 때만 다시 만든다 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.FxChart(past: List<Pair<Double, Double>>, end: List<Double>, ticks: List<Double>, FX: Double,
                    q: (Double) -> List<Pair<Double, Double>>, X: (Double) -> Double, Y: (Double) -> Double) {
    val tm = rememberTextMeasurer()
    val lineC = Theme.line; val blueC = Theme.blue; val mutedC = Theme.muted; val inkC = Theme.ink; val subC = Theme.sub
    // ios: Views/AnalysisDetail.swift:2170,2195,2196,2197
    Box(Modifier.weight(1f).height(160.dp)
        .pulse(blueC, 8.dp) { s -> Offset((X(0.0) * s.width).toFloat(), (Y(FX) * s.height).toFloat()) }
        .semantics { contentDescription = tr("원/달러 지난 흐름과 앞으로 1년 범위") }
        .drawWithCache {
            // ios: Views/AnalysisDetail.swift:2171
            fun band(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>): Path {
                val p = linePath(a.map { Pair(X(it.first), Y(it.second)) }, size)   // ios: Views/AnalysisDetail.swift:2172
                for (x in b.asReversed()) p.lineTo((X(x.first) * size.width).toFloat(), (Y(x.second) * size.height).toFloat())   // ios: Views/AnalysisDetail.swift:2173
                p.close(); return p   // ios: Views/AnalysisDetail.swift:2174
            }
            // ios: Views/AnalysisDetail.swift:2176
            val tickYs = ticks.map { t ->
                (Y(t) * size.height).toFloat()   // ios: Views/AnalysisDetail.swift:2177
            }
            val tickLines = tickYs.map { y -> Pair(Offset(0f, y), Offset(size.width, y)) }; val tickW = 1.dp.toPx()   // ios: Views/AnalysisDetail.swift:2178
            val outer = band(q(1.645), q(-1.645)); val outerC = blueC.copy(alpha = 0.12f)   // ios: Views/AnalysisDetail.swift:2180
            val inner = band(q(0.674), q(-0.674)); val innerC = blueC.copy(alpha = 0.26f)   // ios: Views/AnalysisDetail.swift:2181
            val nx = (X(0.0) * size.width).toFloat()   // ios: Views/AnalysisDetail.swift:2182
            val nowA = Offset(nx, 0f); val nowB = Offset(nx, size.height); val nowW = 1.dp.toPx(); val nowDash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))   // ios: Views/AnalysisDetail.swift:2183
            val pastP = if (past.size > 1) linePath(past.map { Pair(X(it.first), Y(it.second)) }, size) else null; val pastS = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round)   // ios: Views/AnalysisDetail.swift:2184
            val ny = (Y(FX) * size.height).toFloat()   // ios: Views/AnalysisDetail.swift:2185
            val dotAt = Offset(nx - 4.dp.toPx(), ny - 4.dp.toPx()); val dotSize = Size(8.dp.toPx(), 8.dp.toPx())   // ios: Views/AnalysisDetail.swift:2186
            // 띠 오른쪽 끝 값 4개 (겹치면 위아래로 띄움)
            val ys = end.map { (Y(it) * size.height).toFloat() }.toMutableList()   // ios: Views/AnalysisDetail.swift:2188
            for (i in 1 until ys.size) if (ys[i] - ys[i - 1] < 11.dp.toPx()) ys[i] = ys[i - 1] + 11.dp.toPx()   // ios: Views/AnalysisDetail.swift:2189
            // ios: Views/AnalysisDetail.swift:2190
            val labels = end.mapIndexed { i, v ->
                val tl = tm.measure(v.swRound().toLong().f(), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = subC))   // ios: Views/AnalysisDetail.swift:2191
                // ios: Views/AnalysisDetail.swift:2192 (anchor .trailing: 오른쪽 끝·세로 가운데)
                Pair(tl, Offset(size.width - 2.dp.toPx() - tl.size.width, min(size.height - 5.dp.toPx(), max(5.dp.toPx(), ys[i])) - tl.size.height * 0.5f))
            }

            onDrawBehind {
                tickLines.forEach { drawLine(lineC, it.first, it.second, tickW) }
                drawPath(outer, outerC); drawPath(inner, innerC)
                drawLine(mutedC, nowA, nowB, nowW, pathEffect = nowDash)
                pastP?.let { drawPath(it, inkC, style = pastS) }
                drawOval(blueC, dotAt, dotSize)
                labels.forEach { drawText(it.first, topLeft = it.second) }
            }
        })
}

// ios: Views/AnalysisDetail.swift:2274,2275,2276
/** 작은 칸 하나 (금리 일정 칸 모양): 이름, 값, 아래 작은 글 */
// ios: Views/AnalysisDetail.swift:2044
@Composable
private fun androidx.compose.foundation.layout.RowScope.FxCell(k: String, v: String, note: String, color: Color = Theme.ink) {
    // ios: Views/AnalysisDetail.swift:2045,2050-2053
    Column(Modifier.weight(1f).heightIn(min = 64.dp).background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp))
        .padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(k, style = appFont(11f), minScale = 0.7f, color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2046
        FitText(v, style = appFont(14f, FontWeight.Bold).tnum(), minScale = 0.6f, color = color)   // ios: Views/AnalysisDetail.swift:2047
        if (note.isNotEmpty()) FitText(note, style = appFont(10f), minScale = 0.7f, color = Theme.muted)   // ios: Views/AnalysisDetail.swift:2048
    }
}

// ios: Views/AnalysisDetail.swift:2279
@Composable
private fun FxLegend(c: Color, h: Dp, s: String) {
    // ios: Views/AnalysisDetail.swift:2280
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp, h).background(c, RoundedCornerShape(2.dp)))   // ios: Views/AnalysisDetail.swift:2281
        T(s, style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2283
    }
}

/** 깔끔한 y축 눈금: 20·50·100·200원 단위 중 눈금 3~5개가 되는 것 */
// ios: Views/AnalysisDetail.swift:2289
internal fun fxTicks(lo: Double, hi: Double): List<Double> {
    // ios: Views/AnalysisDetail.swift:2290
    for (step in listOf(20.0, 50.0, 100.0, 200.0, 500.0)) {
        val a = floor(lo / step) * step; val b = ceil(hi / step) * step   // ios: Views/AnalysisDetail.swift:2291
        val k = ((b - a) / step).swRound().toInt()   // ios: Views/AnalysisDetail.swift:2292
        if (k <= 4) return (0..max(1, k)).map { a + it * step }   // ios: Views/AnalysisDetail.swift:2293
    }
    val a = floor(lo / 500) * 500   // ios: Views/AnalysisDetail.swift:2295
    return listOf(a, ceil(hi / 500) * 500)   // ios: Views/AnalysisDetail.swift:2296
}

/** 연 변동성을 말로 */
// ios: Views/AnalysisDetail.swift:2309
internal fun volWord(v: Double): String = if (v < 0.07) "낮음" else if (v <= 0.12) "보통" else "큼"

// MARK: 종목 한눈에 — 숫자만, 추천 문구 없음

/** 종목 한눈에: 비중만큼 나눈 지도. 칸 색은 고른 기간 등락, 누르면 아래에 세 줄 상세 (2026-10-28 결정) */
object GlanceView {
    // ios: Views/AnalysisDetail.swift:2319
    /** mv: 고른 기간 등락 (기록이 없으면 null) */
    class Item(val row: AppModel.Row, val k: AppModel.HoldMetrics, val vol: Double, val has: Boolean, val mv: Double? = null)   // ios: Views/AnalysisDetail.swift:2137

    /** 가장 짙은 색이 되는 등락 폭. 기간이 길수록 넓게 (2026-10-28 결정) */
    // ios: Views/AnalysisDetail.swift:2140
    fun scale(p: Period): Double = when (p) { Period.d1 -> 0.03; Period.w1 -> 0.07; Period.m1 -> 0.12; Period.m3 -> 0.2; Period.m6 -> 0.28; Period.y1 -> 0.4; Period.y3 -> 0.7 }

    /** 고른 기간 원/달러 변화 (지금 ÷ 기간 첫날). 기록이 없으면 1 */
    // ios: Views/AnalysisDetail.swift:2147
    fun fxRatio(p: Period): Double {
        val now = Market.shared.valFx
        var start = 0.0
        if (p == Period.d1) start = Market.shared.fx.prevClose
        else Market.shared.prices?.h?.get("KRW=X")?.let { h ->
            val c = java.time.LocalDate.now()
            val from = StubPriceProvider.dayNum(c.year, c.monthValue, c.dayOfMonth) - p.days
            for ((d, v) in h.dates.zip(h.closes)) {
                val n = StubPriceProvider.dayNum(d)
                if (n != null && n >= from) { start = v; break }
            }
        }
        return if (start > 0 && now > 0) now / start else 1.0
    }

    /** 출렁임 단계: 연 변동성 20% 미만 작은 편 / 40% 미만 보통 / 그 위 큰 편 (2026-10-14 결정) */
    // ios: Views/AnalysisDetail.swift:2322
    fun swing(v: Double): Int = if (v < 0.2) 0 else if (v < 0.4) 1 else 2
    // ios: Views/AnalysisDetail.swift:2323
    val swingName = listOf("작음", "보통", "큼")

    /** 거의 안 움직이는 자산(SGOV 등) */
    fun calm(d: Item): Boolean = d.has && d.vol < 0.02   // ios: Views/AnalysisDetail.swift:2196

    /** 비중 지도 칸 나누기 (squarified treemap). 값은 큰 순서로 받는다 */
    // ios: Views/AnalysisDetail.swift:2254
    fun squarify(vals: List<Double>, bw: Double, bh: Double): List<Rect> {
        val total = vals.sum()
        if (total <= 0 || bw <= 0 || bh <= 0) return vals.map { Rect.Zero }
        val scale = bw * bh / total
        val areas = ArrayDeque(vals.map { it * scale })
        var x0 = 0.0; var y0 = 0.0; var w0 = bw; var h0 = bh
        val out = mutableListOf<Rect>()
        while (areas.isNotEmpty()) {
            val side = minOf(w0, h0)
            fun worst(row: List<Double>): Double {
                val sum = row.sum(); val mx = row.maxOrNull() ?: 0.0; val mn = row.minOrNull() ?: 0.0
                if (sum <= 0 || mn <= 0) return Double.POSITIVE_INFINITY
                return maxOf(side * side * mx / (sum * sum), sum * sum / (side * side * mn))
            }
            val row = mutableListOf<Double>()
            while (areas.isNotEmpty() && (row.isEmpty() || worst(row + areas.first()) <= worst(row))) row.add(areas.removeFirst())
            val sum = row.sum()
            if (w0 >= h0) {
                // 왼쪽에 세로 한 줄
                val w = sum / h0; var y = y0
                for (a in row) { val h = a / w; out.add(Rect(x0.toFloat(), y.toFloat(), (x0 + w).toFloat(), (y + h).toFloat())); y += h }
                x0 += w; w0 = maxOf(0.0, w0 - w)
            } else {
                // 위에 가로 한 줄
                val h = sum / w0; var x = x0
                for (a in row) { val w = a / h; out.add(Rect(x.toFloat(), y0.toFloat(), (x + w).toFloat(), (y0 + h).toFloat())); x += w }
                y0 += h; h0 = maxOf(0.0, h0 - h)
            }
        }
        return out
    }
}

// ios: Views/AnalysisDetail.swift:2314,2315,2325
@Composable
fun GlanceView(m: AppModel) {
    var pick by remember { mutableStateOf<String?>(null) }   // ios: Views/AnalysisDetail.swift:2133
    var period by remember { mutableStateOf(Period.d1) }   // ios: Views/AnalysisDetail.swift:2134
    var inUsd by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:2135

    val rows = m.rows; val stamp = Market.shared.updatedAt; val stats = Market.shared.stats
    // ios: Views/AnalysisDetail.swift:2326
    val all = remember(rows, stamp, stats) { rows.map { r -> GlanceView.Item(row = r, k = m.metrics(r), vol = SiteStats.params(r.id).third, has = m.prices.hasHistory(r.sym)) } }
    // ios: Views/AnalysisDetail.swift:2150,2151,2152,2153,2154
    // 원으로 보면 달러 종목은 기간 환율 변화까지 곱한다 (2026-10-28 결정)
    val fx = if (inUsd) 1.0 else GlanceView.fxRatio(period)   // ios: Views/AnalysisDetail.swift:2166
    val sorted = all.sortedByDescending { it.k.w }.map { d ->
        val pts = m.prices.series(d.row.sym, period)
        val k = if (d.row.sym.currency == Currency.usd) fx else 1.0
        val a = pts.firstOrNull(); val b = pts.lastOrNull()
        GlanceView.Item(d.row, d.k, d.vol, d.has, if ((period == Period.d1 || d.has) && a != null && b != null && a > 0) b / a * k - 1 else null)
    }

    // ios: Views/AnalysisDetail.swift:2339,2359,2360 (제목 줄은 늘 한 줄: NavBar)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        // ios: Views/AnalysisDetail.swift:2340,2355,2356,2357
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHead(title = "종목 한눈에", sub = "")   // ios: Views/AnalysisDetail.swift:2341
            // ios: Views/AnalysisDetail.swift:2178,2179,2180,2181
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                HoldSeg(listOf(true to "$", false to "원"), inUsd, { inUsd = it }, label = "금액 단위: ${if (inUsd) tr("달러") else tr("원")}")
            }
            ChipRow(items = Period.entries.map { it to it.label }, selection = period, onSelect = { period = it }, fill = true, pad = 4.dp)   // ios: Views/AnalysisDetail.swift:2161
            // ios: Views/AnalysisDetail.swift:2342
            sorted.firstOrNull()?.let { top ->
                // ios: Views/AnalysisDetail.swift:2343,2346,2347
                Column(Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(summary(top), style = appFont(14f), color = Theme.ink)   // ios: Views/AnalysisDetail.swift:2344
                }
            }
            Treemap(sorted, period, inUsd, pick) { id -> pick = if (pick == id) null else id }   // ios: Views/AnalysisDetail.swift:2156
            T("빨강은 오름, 파랑은 내림 · 짙을수록 크게", style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2157
            // ios: Views/AnalysisDetail.swift:2158
            val d = sorted.firstOrNull { it.row.id == pick }
            if (d != null) GlanceCard(m, d)   // ios: Views/AnalysisDetail.swift:2159
            else T("칸을 누르면 자세히 보여요", Modifier.fillMaxWidth().padding(vertical = 12.dp), style = appFont(13f).copy(textAlign = TextAlign.Center), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2161,2162
            Footnote("매매 추천 아님")   // ios: Views/AnalysisDetail.swift:2353
        }
    }
}

// ios: Views/AnalysisDetail.swift:2364
private fun summary(t: GlanceView.Item): String {
    val n = t.row.sym.name; val w = AppModel.pct(t.k.w)   // ios: Views/AnalysisDetail.swift:2365
    if (t.k.w >= 0.5) return "자산의 ${w}가 $n · 함께 움직여요"   // ios: Views/AnalysisDetail.swift:2366
    if (t.k.w >= 0.3) return "자산의 ${w}가 $n · 크게 따라 움직여요"   // ios: Views/AnalysisDetail.swift:2367
    return "1위 $n $w · 여러 종목에 나뉨"   // ios: Views/AnalysisDetail.swift:2368
}

/** 칸 글: 기간 등락, 현금성, 기록 없음 */
// ios: Views/AnalysisDetail.swift:2199
private fun tileText(d: GlanceView.Item): String {
    if (GlanceView.calm(d)) return "현금성"   // ios: Views/AnalysisDetail.swift:2200
    val mv = d.mv ?: return "기록 모으는 중"   // ios: Views/AnalysisDetail.swift:2201
    return AppModel.sgn(mv)   // ios: Views/AnalysisDetail.swift:2202
}

/** 칸 금액: 그 기간 평가금 변화. 달러로 보면 달러 종목만 $ (종목 탭과 같다) */
// ios: Views/AnalysisDetail.swift:2179
private fun amountText(d: GlanceView.Item, inUsd: Boolean): String? {
    val mv = d.mv
    if (GlanceView.calm(d) || mv == null || mv <= -1) return null
    val usd = inUsd && d.row.sym.currency == Currency.usd
    val v = if (usd) d.row.value / Market.shared.valFx else d.row.value
    val ch = v - v / (1 + mv)
    return (if (ch < 0) "−" else "+") + (if (usd) "$" + abs(ch).swRound().toLong().f() else HoldFmt.won(abs(ch)))
}

/** 칸 색: 오르면 빨강, 내리면 파랑. 기간마다 정한 폭(scale)에서 가장 짙다. 현금성·기록 없음은 회색 */
// ios: Views/AnalysisDetail.swift:2206
@Composable
private fun tone(d: GlanceView.Item, period: Period): Pair<Color, Color> {
    val mv = d.mv
    if (GlanceView.calm(d) || mv == null) return Theme.line to Theme.ink   // ios: Views/AnalysisDetail.swift:2207
    val t = minOf(1.0, abs(mv) / GlanceView.scale(period))   // ios: Views/AnalysisDetail.swift:2208
    return Theme.change(mv).copy(alpha = (0.15 + 0.75 * t).toFloat()) to (if (t > 0.45) Color.White else Theme.ink)   // ios: Views/AnalysisDetail.swift:2209
}

// ios: Views/AnalysisDetail.swift:2213
@Composable
private fun Treemap(list: List<GlanceView.Item>, period: Period, inUsd: Boolean, pick: String?, onPick: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(340.dp)) {
        // 0%인 종목도 누를 수 있게 아주 작은 칸은 남긴다
        val rs = GlanceView.squarify(list.map { maxOf(it.k.w, 0.006) }, maxWidth.value.toDouble(), 340.0)   // ios: Views/AnalysisDetail.swift:2216
        list.forEachIndexed { i, d ->
            val r = rs[i]
            val w = maxOf(0f, r.width - 3f); val h = maxOf(0f, r.height - 3f)   // ios: Views/AnalysisDetail.swift:2219
            Tile(d, period, inUsd, w, h, pick == d.row.id, Modifier.offset(x = (r.left + 1.5f).dp, y = (r.top + 1.5f).dp)) { onPick(d.row.id) }
        }
    }
}

// ios: Views/AnalysisDetail.swift:2229
@Composable
private fun Tile(d: GlanceView.Item, period: Period, inUsd: Boolean, w: Float, h: Float, on: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val (bg, fg) = tone(d, period)
    val big = w > 120 && h > 80
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.size(w.dp, h.dp).background(bg, shape).then(if (on) Modifier.border(2.dp, Theme.ink, shape) else Modifier).plainClickable(onTap).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        if (w > 40 && h > 28) {
            FitText(d.row.sym.name, appFont(if (big) 15f else 11f, FontWeight.Bold), 0.6f, color = fg)   // ios: Views/AnalysisDetail.swift:2237
            if (h > 46) FitText(tileText(d), appFont(if (big) 22f else 12f, FontWeight.Bold), 0.6f, color = fg)   // ios: Views/AnalysisDetail.swift:2239
            val a = amountText(d, inUsd)
            if (h > 64 && a != null) FitText(a, appFont(if (big) 13f else 10f, FontWeight.SemiBold), 0.6f, color = fg)   // ios: Views/AnalysisDetail.swift:2242,2243
        }
    }
}

/** 세 줄 상세: 이름·비중·최고가 대비 / 1년 범위 막대 / 1년·출렁임·내 보유 */
// ios: Views/AnalysisDetail.swift:2298
@Composable
private fun GlanceCard(m: AppModel, d: GlanceView.Item) {
    val s = d.row.sym   // ios: Views/AnalysisDetail.swift:2299
    // ios: Views/AnalysisDetail.swift:2300,2301,2302
    val tail = (if (d.has) listOf("1년 ${AppModel.sgn(d.k.r1)}", "출렁임 ${GlanceView.swingName[GlanceView.swing(d.vol)]}") else listOf("1년 기록 모으는 중")).toMutableList()
    if (d.row.cost > 0 && s.id != KoreaEstimate.sumId) tail.add("내 보유 ${AppModel.sgn(d.k.vc)}")   // ios: Views/AnalysisDetail.swift:2303
    val open = com.naeilo.app.ui.hold.openDetail(m)   // ios: Views/AnalysisDetail.swift (DetailLink)
    // ios: Views/AnalysisDetail.swift:2304,2305,2320,2321,2322,2323
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).plainClickable { open(s) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ios: Views/AnalysisDetail.swift:2306
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            T(s.name, style = appFont(15f, FontWeight.Bold), maxLines = 1)   // ios: Views/AnalysisDetail.swift:2307
            T(AppModel.pct(d.k.w), style = appFont(12f, FontWeight.SemiBold), color = Theme.tealDark)   // ios: Views/AnalysisDetail.swift:2308
            Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2309
            // ios: Views/AnalysisDetail.swift:2310,2311,2312
            if (d.has) T(if (d.k.dd > -0.0005) "1년 최고가" else "최고가 대비 ${AppModel.sgn(d.k.dd)}", style = appFont(13f, FontWeight.SemiBold), color = Theme.change(if (d.k.dd > -0.0005) 1.0 else -1.0))
            Sym("chevron.right", 12f, Theme.teal)   // ios: Views/AnalysisDetail.swift:2314
        }
        if (d.has) RangeBar(lo = d.k.lo, hi = d.k.hi, now = s.last) else RangeBar(lo = 0.0, hi = 0.0, now = 0.0)   // ios: Views/AnalysisDetail.swift:2316
        FitText(tail.joinToString(" · "), appFont(12f), 0.8f, color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2317
    }
}

/** 1년 가격 범위 막대 (최저~최고, 지금 위치에 점). 기록이 없으면 점선만 */
// ios: Views/AnalysisDetail.swift:2493,2495
@Composable
private fun RangeBar(
    lo: Double, hi: Double, now: Double,   // ios: Views/AnalysisDetail.swift:2494
) {
    // ios: Views/AnalysisDetail.swift:2496,2511
    BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
        val ok = hi > lo && hi > 0   // ios: Views/AnalysisDetail.swift:2497
        val x = if (ok) min(1.0, max(0.0, (now - lo) / (hi - lo))) else 0.0   // ios: Views/AnalysisDetail.swift:2498
        val w = maxWidth
        // ios: Views/AnalysisDetail.swift:2499,2510
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            // ios: Views/AnalysisDetail.swift:2500
            if (ok) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(Theme.track, CapsuleShape))   // ios: Views/AnalysisDetail.swift:2501
                Box(Modifier.width(maxOf(6.dp, w * x.toFloat())).height(6.dp).background(Theme.dash, CapsuleShape))   // ios: Views/AnalysisDetail.swift:2502
                // ios: Views/AnalysisDetail.swift:2503,2504,2505
                // SwiftUI 테두리(lineWidth 2)는 원 가장자리 가운데에 그려져 1만큼 밖으로 나간다: 14 판에 바탕 고리 2, 안쪽 잉크 10 (requiredSize 가 12 칸 가운데에 둔다)
                Box(Modifier.offset(x = maxOf(0.dp, w * x.toFloat() - 6.dp)).size(12.dp, 12.dp).requiredSize(14.dp).background(Theme.card, CircleShape).padding(2.dp).background(Theme.ink, CircleShape))
            } else {   // ios: Views/AnalysisDetail.swift:2506
                val dashC = Theme.dash
                // ios: Views/AnalysisDetail.swift:2507,2508
                Box(Modifier.fillMaxSize().drawBehind { drawLine(dashC, Offset(0f, 6.dp.toPx()), Offset(size.width, 6.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))) })
            }
        }
    }
}

// ios: Views/AnalysisDetail.swift:2515
data class GlanceTip(val topic: String, val id: String)

/** ⓘ 설명 시트 ("숫자 풀이" 화면을 대신한다) */
// ios: Views/AnalysisDetail.swift:2518,2523
@Composable
private fun GlanceSheet(
    tip: GlanceTip,   // ios: Views/AnalysisDetail.swift:2519
    items: List<GlanceView.Item>,   // ios: Views/AnalysisDetail.swift:2520
    dismiss: () -> Unit,   // ios: Views/AnalysisDetail.swift:2521
) {
    val d = items.firstOrNull { it.row.id == tip.id }   // ios: Views/AnalysisDetail.swift:2524
    val n = d?.row?.sym?.name ?: ""   // ios: Views/AnalysisDetail.swift:2525
    Column(Modifier.fillMaxSize()) {
        // ios: Views/AnalysisDetail.swift:2571 (손잡이: 시트 위 가운데 36×5)
        Box(Modifier.padding(top = 5.dp).size(36.dp, 5.dp).background(Theme.muted.copy(alpha = 0.4f), CapsuleShape).align(Alignment.CenterHorizontally))
        // ios: Views/AnalysisDetail.swift:2526,2570
        Column(Modifier.fillMaxSize().padding(20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ios: Views/AnalysisDetail.swift:2527
            when (tip.topic) {
                // ios: Views/AnalysisDetail.swift:2528
                "vol" -> {
                    Head("가격 출렁임", "지난 1년 동안 가격이 하루하루 얼마나 크게 오르내렸는지예요. 크면 크게 오를 수도, 크게 내릴 수도 있어요.")   // ios: Views/AnalysisDetail.swift:2529
                    val lv = GlanceView.swing(d?.vol ?: 0.0)   // ios: Views/AnalysisDetail.swift:2530
                    // ios: Views/AnalysisDetail.swift:2531
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // ios: Views/AnalysisDetail.swift:2532
                        (0 until 3).forEach { i ->
                            // ios: Views/AnalysisDetail.swift:2533
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth().height(8.dp).background(if (i == lv) Theme.teal else Theme.track, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:2534
                                // ios: Views/AnalysisDetail.swift:2535,2536
                                T(GlanceView.swingName[i] + (if (i == lv) " · $n" else ""), style = appFont(11f, if (i == lv) FontWeight.Bold else FontWeight.Normal),
                                    color = if (i == lv) Theme.ink else Theme.muted)
                            }
                        }
                    }
                    // ios: Views/AnalysisDetail.swift:2540
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        T("내 종목으로 보면", style = appFont(13f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2541
                        // ios: Views/AnalysisDetail.swift:2542
                        listOf(2, 1, 0).forEach { i ->
                            val names = items.filter { GlanceView.swing(it.vol) == i }.map { it.row.sym.name }   // ios: Views/AnalysisDetail.swift:2543
                            // ios: Views/AnalysisDetail.swift:2544
                            if (names.isNotEmpty()) {
                                // ios: Views/AnalysisDetail.swift:2545
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                    T(GlanceView.swingName[i], Modifier.width(52.dp), style = appFont(12f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:2546
                                    T(names.joinToString(", "), Modifier.weight(1f), style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2547
                                }
                            }
                        }
                    }
                    Small("숫자로 보고 싶다면: ${n}의 1년 변동성은 연 ${AppModel.pct(d?.vol ?: 0.0)}예요.")   // ios: Views/AnalysisDetail.swift:2552
                }
                // ios: Views/AnalysisDetail.swift:2553
                "dd" -> {
                    Head("1년 최고가 대비", "지난 1년 동안 가장 높았던 종가와 지금 가격을 비교한 값이에요. 0%에 가까우면 1년 중 가장 비싼 쪽이에요.")   // ios: Views/AnalysisDetail.swift:2554
                    if (d != null) Small("$n: 가장 비쌀 때 ${AppModel.price(d.row.sym, d.k.hi)}, 지금 ${AppModel.price(d.row.sym, d.row.sym.last)} (${AppModel.sgn(d.k.dd)})")   // ios: Views/AnalysisDetail.swift:2555
                }
                // ios: Views/AnalysisDetail.swift:2556
                "r1" -> {
                    Head("1년 전보다", "1년 전 종가와 지금 가격을 비교한 값이에요. 환율은 빼고 그 나라 돈으로 계산해요.")   // ios: Views/AnalysisDetail.swift:2557
                    if (d != null) Small("$n: ${AppModel.sgn(d.k.r1)}")   // ios: Views/AnalysisDetail.swift:2558
                }
                // ios: Views/AnalysisDetail.swift:2559
                else -> {
                    Head("내 자산에서 차지하는 몫", "내 종목 평가액을 모두 더한 것 중 이 종목이 차지하는 몫이에요. 크면 이 종목 가격이 내 자산을 크게 움직여요.")   // ios: Views/AnalysisDetail.swift:2560
                    if (d != null) Small("$n: 내 자산의 ${AppModel.pct(d.k.w)}")   // ios: Views/AnalysisDetail.swift:2561
                }
            }
            Spacer(Modifier.weight(1f).padding(end = 0.dp))   // ios: Views/AnalysisDetail.swift:2563
            // ios: Views/AnalysisDetail.swift:2564,2566,2567,2568
            Box(Modifier.fillMaxWidth().plainClickable { dismiss() }.background(Theme.teal, RoundedCornerShape(12.dp)).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                T("알겠어요", style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Views/AnalysisDetail.swift:2565
            }
        }
    }
}

// ios: Views/AnalysisDetail.swift:2574
@Composable
private fun Head(t: String, s: String) {
    // ios: Views/AnalysisDetail.swift:2575
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(t, style = appFont(18f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2576
        T(s, style = appFont(14f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2577
    }
}
// ios: Views/AnalysisDetail.swift:2580
@Composable
private fun Small(t: String) = T(t, style = appFont(12f), color = Theme.muted)
