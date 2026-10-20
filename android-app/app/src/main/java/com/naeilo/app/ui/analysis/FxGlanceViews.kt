package com.naeilo.app.ui.analysis

import androidx.compose.foundation.layout.requiredSize
import com.naeilo.app.core.cformat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
import com.naeilo.app.core.calc.keyName
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.swFmtFrac
import com.naeilo.app.data.Currency
import com.naeilo.app.data.short
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.Market
import com.naeilo.app.net.SiteStats
import com.naeilo.app.net.YahooSample
import com.naeilo.app.net.last
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
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
    /** null 이면 지금 환율(BASE). 시세가 늦게 와도 시작 상태 변화가 0 이 되게 */
    var what by remember { mutableStateOf<Double?>(null) }   // ios: Views/AnalysisDetail.swift:2088
    var more by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:2089

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
    val r = what ?: BASE; val dv = valAt(r) - m.total   // ios: Views/AnalysisDetail.swift:2112
    val per10 = usdV * 10 / BASE   // ios: Views/AnalysisDetail.swift:2113
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
    val why = if (usdW >= 0.995) "주식 100% 달러" else "달러 ${AppModel.pct(usdW)}"   // ios: Views/AnalysisDetail.swift:2125
    val lo1 = c?.lo1; val hi1 = c?.hi1   // ios: Views/AnalysisDetail.swift:2126
    val gap = max(0.0, m.keyValue - valAt(r)); val prog = if (m.keyValue > 0) min(1.0, max(0.0, valAt(r) / m.keyValue)) else 0.0   // ios: Views/AnalysisDetail.swift:2127
    val sMin = min(1100.0, floor(BASE - 50)); val sMax = max(1600.0, ceil(BASE + 50))   // ios: Views/AnalysisDetail.swift:2128

    LaunchedEffect(Unit) { SiteCalc.shared.refresh() }   // ios: Views/AnalysisDetail.swift:2271
    // ios: Views/AnalysisDetail.swift:2269,2270 (제목 줄은 늘 한 줄: NavBar)
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        // 설명 줄이 없어 흰 띠만 남았다: 띠 없이 제목만 건다
        // ios: Views/AnalysisDetail.swift:2130
        PinnedLayout(header = {
            DetailHead(title = "환율 영향", sub = "")   // ios: Views/AnalysisDetail.swift:2132
        }, content = {   // ios: Views/AnalysisDetail.swift:2133
            // ios: Views/AnalysisDetail.swift:2134,2267
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. 한 줄 요약
                // ios: Views/AnalysisDetail.swift:2136
                Card(dark = true) {
                    T(if (usdV <= 0) "환율" else "환율 10원 ↓", style = appFont(12f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2137
                    // ios: Views/AnalysisDetail.swift:2138
                    if (usdV <= 0) {
                        T("달러 자산 없음 · 영향 없음", style = appFont(18f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2139
                    } else {   // ios: Views/AnalysisDetail.swift:2140
                        T("내 자산 ${AppModel.man(per10)} ↓", style = appFont(20f, FontWeight.Bold), roll = "내 자산 ${AppModel.man(per10)} ↓")   // ios: Views/AnalysisDetail.swift:2141
                        // ios: Views/AnalysisDetail.swift:2142,2143
                        T(why, style = appFont(13f).ls(2f), color = Theme.sub)
                        // ios: Views/AnalysisDetail.swift:2144
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(trendWord(c?.trend))   // ios: Views/AnalysisDetail.swift:2145
                            c?.ret_1y?.let { y -> Pill("1년 ${AppModel.sgn(y)}") }   // ios: Views/AnalysisDetail.swift:2146
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
                // 3. 환율이 바뀌면 내 자산은?
                // ios: Views/AnalysisDetail.swift:2210
                if (usdV > 0) {
                    // ios: Views/AnalysisDetail.swift:2211
                    Card {
                        // ios: Views/AnalysisDetail.swift:2212
                        Row(Modifier.fillMaxWidth()) {
                            T("환율이 바뀌면?", Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2213
                            Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2214
                            T("주가 고정", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:2215
                        }
                        // ios: Views/AnalysisDetail.swift:2217,2223,2224
                        Column(Modifier.fillMaxWidth().background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            T("${won(r)}이면", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2218
                            T(AppModel.man(valAt(r)), style = appFont(24f, FontWeight.Bold).tnum(), roll = AppModel.man(valAt(r)))   // ios: Views/AnalysisDetail.swift:2219
                            // ios: Views/AnalysisDetail.swift:2220,2221
                            T(if (abs(dv) < 0.5) "지금과 같음" else (if (dv > 0) "+" else "−") + AppModel.man(abs(dv)),
                                style = appFont(13f, FontWeight.Bold), color = if (abs(dv) < 0.5) Theme.muted else Theme.change(dv))
                        }
                        IosSlider(value = r, onChange = { what = it }, range = sMin..sMax, step = 1.0, tint = Theme.blue, modifier = Modifier.semantics { contentDescription = "환율" })   // ios: Views/AnalysisDetail.swift:2225
                        // ios: Views/AnalysisDetail.swift:2226
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // ios: Views/AnalysisDetail.swift:2227
                            listOf(1250.0, 1300.0, 0.0, 1450.0, 1500.0).forEach { v ->
                                val on = if (v == 0.0) what == null || what == BASE else what == v   // ios: Views/AnalysisDetail.swift:2228
                                // ios: Views/AnalysisDetail.swift:2229,2231,2232,2233,2234,2236
                                Box(Modifier.weight(1f).plainClickable { what = if (v == 0.0) null else v }.defaultMinSize(minHeight = 34.dp)
                                    .background(if (on) Theme.blue else Theme.card, CapsuleShape).border(1.5.dp, if (on) Theme.blue else Theme.border, CapsuleShape), contentAlignment = Alignment.Center) {
                                    T(if (v == 0.0) "지금" else v.toLong().f(), style = appFont(12f, FontWeight.Bold), maxLines = 1, color = if (on) Color.White else Theme.ink)   // ios: Views/AnalysisDetail.swift:2230
                                }
                            }
                        }
                        HDivider()   // ios: Views/AnalysisDetail.swift:2239
                        T(if (gap > 0) "${m.keyName}까지 ${AppModel.man(gap)}" else "${m.keyName} 도달", style = appFont(13f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:2240
                        // ios: Views/AnalysisDetail.swift:2241,2242,2247
                        BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
                            Box(Modifier.fillMaxSize().background(Theme.track, CapsuleShape))   // ios: Views/AnalysisDetail.swift:2243
                            Box(Modifier.fillMaxHeight().width(maxWidth * prog.toFloat()).background(Theme.blue, CapsuleShape))   // ios: Views/AnalysisDetail.swift:2244
                        }
                    }
                }
                // 4. 자세히 보기
                val turn by animateFloatAsState(if (more) 90f else 0f, label = "more")
                // ios: Views/AnalysisDetail.swift:2251,2265 (DisclosureGroup: 누르면 펼침, 화살표는 sub 색)
                Column(Modifier.fillMaxWidth()) {
                    // ios: Views/AnalysisDetail.swift:2262
                    Row(Modifier.fillMaxWidth().plainClickable { more = !more }, verticalAlignment = Alignment.CenterVertically) {
                        T("자세히", Modifier.weight(1f), style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)   // ios: Views/AnalysisDetail.swift:2263
                        Sym("chevron.right", 14f, Theme.sub, Modifier.rotate(turn))
                    }
                    // ios: Views/AnalysisDetail.swift:2252,2261
                    AnimatedVisibility(more) {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // ios: Views/AnalysisDetail.swift:2253
                            KpiGrid(listOf(
                                Kpi(k = "1년 범위", v = lo1?.let { l -> hi1?.let { "${l.swRound().toLong().f()}~${it.swRound().toLong().f()}" } } ?: "-", sub = ""),   // ios: Views/AnalysisDetail.swift:2254

                                Kpi(k = "출렁임", v = "${volWord(vol)} · 연 ${cformat("%.1f", vol * 100)}%", sub = ""),   // Views/AnalysisDetail.swift:2365 (검사 예외: 보간 속 따옴표)
                                Kpi(k = "$goalD 예상", v = at(0.0) + "원", sub = "10번 중 9번 ${at(-1.645)}~${at(1.645)}"),   // ios: Views/AnalysisDetail.swift:2256
                                Kpi(k = "환율 손익 · 1년", v = eff1y?.let { AppModel.sgn(it) } ?: "-", sub = "", color = Theme.change(eff1y ?: 0.0)),   // ios: Views/AnalysisDetail.swift:2257
                            ))
                            Footnote("지난 흐름 기준 예상")   // ios: Views/AnalysisDetail.swift:2259
                        }
                    }
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
        .semantics { contentDescription = "원/달러 지난 흐름과 앞으로 1년 범위" }
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
@Composable
private fun Pill(s: String) = T(s, Modifier.background(Color.White.copy(alpha = 0.12f), CapsuleShape).padding(horizontal = 10.dp, vertical = 5.dp), style = appFont(12f, FontWeight.SemiBold))

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

/** 사이트 추세 문구(웹 model.js trendLabel)를 쉬운 말로 */
// ios: Views/AnalysisDetail.swift:2300
internal fun trendWord(t: String?): String {
    // ios: Views/AnalysisDetail.swift:2301
    return when (t ?: "") {
        "상승 추세", "약한 상승" -> "오르는 중"   // ios: Views/AnalysisDetail.swift:2302
        "하락 추세", "약세 전환 주의" -> "내려가는 중"   // ios: Views/AnalysisDetail.swift:2303
        else -> "옆으로"   // ios: Views/AnalysisDetail.swift:2304
    }
}

/** 연 변동성을 말로 */
// ios: Views/AnalysisDetail.swift:2309
internal fun volWord(v: Double): String = if (v < 0.07) "낮음" else if (v <= 0.12) "보통" else "큼"

// MARK: 종목 한눈에 — 숫자만, 추천 문구 없음

/** 종목 한눈에: 지금 가격이 지난 1년 중 어디쯤인지. 정렬은 종목 탭과 따로 (2026-10-14 결정) */
object GlanceView {
    // ios: Views/AnalysisDetail.swift:2319
    class Item(val row: AppModel.Row, val k: AppModel.HoldMetrics, val vol: Double, val has: Boolean)

    /** 출렁임 단계: 연 변동성 20% 미만 작은 편 / 40% 미만 보통 / 그 위 큰 편 (2026-10-14 결정) */
    // ios: Views/AnalysisDetail.swift:2322
    fun swing(v: Double): Int = if (v < 0.2) 0 else if (v < 0.4) 1 else 2
    // ios: Views/AnalysisDetail.swift:2323
    val swingName = listOf("작음", "보통", "큼")
}

// ios: Views/AnalysisDetail.swift:2314,2315,2325
@Composable
fun GlanceView(m: AppModel) {
    var order by remember { mutableStateOf("w") }   // ios: Views/AnalysisDetail.swift:2316
    var tip by remember { mutableStateOf<GlanceTip?>(null) }   // ios: Views/AnalysisDetail.swift:2317

    val rows = m.rows; val stamp = Market.shared.updatedAt; val stats = Market.shared.stats
    // ios: Views/AnalysisDetail.swift:2326
    val all = remember(rows, stamp, stats) { rows.map { r -> GlanceView.Item(row = r, k = m.metrics(r), vol = SiteStats.params(r.id).third, has = m.prices.hasHistory(r.sym)) } }
    // ios: Views/AnalysisDetail.swift:2327
    val sorted = all.sortedWith { a, b ->
        // ios: Views/AnalysisDetail.swift:2328
        when (order) {
            "dd" -> a.k.dd.compareTo(b.k.dd)   // ios: Views/AnalysisDetail.swift:2329
            "vol" -> b.vol.compareTo(a.vol)   // ios: Views/AnalysisDetail.swift:2330
            else -> b.k.w.compareTo(a.k.w)   // ios: Views/AnalysisDetail.swift:2331
        }
    }
    // 거의 안 움직이는 자산(SGOV 등)은 "그 외"로, 국내는 한 카드에 줄로
    val calm = sorted.filter { it.has && it.vol < 0.02 }   // ios: Views/AnalysisDetail.swift:2335
    val kr = sorted.filter { it.row.sym.currency == Currency.krw && it.row.id !in calm.map { c -> c.row.id } }   // ios: Views/AnalysisDetail.swift:2336
    val us = sorted.filter { it.row.sym.currency != Currency.krw && it.row.id !in calm.map { c -> c.row.id } }   // ios: Views/AnalysisDetail.swift:2337
    val onTip = { t: GlanceTip -> tip = t }

    // ios: Views/AnalysisDetail.swift:2339,2359,2360 (제목 줄은 늘 한 줄: NavBar)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        // ios: Views/AnalysisDetail.swift:2340,2355,2356,2357
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHead(title = "종목 한눈에", sub = "")   // ios: Views/AnalysisDetail.swift:2341
            // ios: Views/AnalysisDetail.swift:2342
            all.maxByOrNull { it.k.w }?.let { top ->
                // ios: Views/AnalysisDetail.swift:2343,2346,2347
                Column(Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(summary(top), style = appFont(14f), color = Theme.ink)   // ios: Views/AnalysisDetail.swift:2344
                }
            }
            ChipRow(items = listOf("w" to "비중순", "dd" to "하락순", "vol" to "출렁임순"), selection = order, onSelect = { order = it }, fill = true)   // ios: Views/AnalysisDetail.swift:2349
            us.forEach { GlanceCard(m, it, onTip) }   // ios: Views/AnalysisDetail.swift:2350
            if (kr.isNotEmpty()) KrCard(m, kr)   // ios: Views/AnalysisDetail.swift:2351
            if (calm.isNotEmpty()) CalmCard(m, calm)   // ios: Views/AnalysisDetail.swift:2352
            Footnote("매매 추천 아님")   // ios: Views/AnalysisDetail.swift:2353
        }
    }
    // ios: Views/AnalysisDetail.swift:2361 (medium·large 중 처음 열리는 medium)
    Sheet(show = tip != null, onDismiss = { tip = null }, medium = true) { tip?.let { t -> GlanceSheet(tip = t, items = all) { tip = null } } }
}

// ios: Views/AnalysisDetail.swift:2364
private fun summary(t: GlanceView.Item): String {
    val n = t.row.sym.name; val w = AppModel.pct(t.k.w)   // ios: Views/AnalysisDetail.swift:2365
    if (t.k.w >= 0.5) return "자산의 ${w}가 $n · 함께 움직여요"   // ios: Views/AnalysisDetail.swift:2366
    if (t.k.w >= 0.3) return "자산의 ${w}가 $n · 크게 따라 움직여요"   // ios: Views/AnalysisDetail.swift:2367
    return "1위 $n $w · 여러 종목에 나뉨"   // ios: Views/AnalysisDetail.swift:2368
}

// ios: Views/AnalysisDetail.swift:2371,2372,2373
@Composable
private fun Info(t: String, d: GlanceView.Item, onTip: (GlanceTip) -> Unit) =
    Box(Modifier.plainClickable { onTip(GlanceTip(topic = t, id = d.row.id)) }) { Sym("info.circle", 12f, Theme.muted) }

// ios: Views/AnalysisDetail.swift:2376,2381
@Composable
private fun Chip(text: String, t: String, d: GlanceView.Item, onTip: (GlanceTip) -> Unit) {
    // ios: Views/AnalysisDetail.swift:2377,2378,2379,2380
    Row(Modifier.plainClickable { onTip(GlanceTip(topic = t, id = d.row.id)) }.background(Theme.line, CapsuleShape).padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        T(text, style = appFont(12f, FontWeight.SemiBold), color = Theme.ink); Sym("info.circle", 10f, Theme.ink)
    }
}

// ios: Views/AnalysisDetail.swift:2384
@Composable
private fun GlanceCard(m: AppModel, d: GlanceView.Item, onTip: (GlanceTip) -> Unit) {
    val s = d.row.sym   // ios: Views/AnalysisDetail.swift:2385
    // ios: Views/AnalysisDetail.swift:2386,2430,2431,2432
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ios: Views/AnalysisDetail.swift:2387
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(s.name, style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2388
            T(s.short, style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:2389
            Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2390
            // ios: Views/AnalysisDetail.swift:2391,2394
            Box(Modifier.plainClickable { onTip(GlanceTip(topic = "w", id = d.row.id)) }) {
                // ios: Views/AnalysisDetail.swift:2392,2393
                T(AppModel.pct(d.k.w), Modifier.background(Theme.tealBg, CapsuleShape).padding(horizontal = 9.dp, vertical = 4.dp), style = appFont(12f, FontWeight.SemiBold), color = Theme.tealDark)
            }
        }
        // ios: Views/AnalysisDetail.swift:2396
        if (d.has) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { T("1년 최고가 대비", style = appFont(13f), color = Theme.sub); Info("dd", d, onTip) }   // ios: Views/AnalysisDetail.swift:2397
            // ios: Views/AnalysisDetail.swift:2398
            T(if (d.k.dd > -0.0005) "지금이 최고가" else AppModel.sgn(d.k.dd), style = appFont(28f, FontWeight.Bold), color = if (d.k.dd > -0.0005) Theme.up else Theme.down, roll = if (d.k.dd > -0.0005) "지금이 최고가" else AppModel.sgn(d.k.dd))
            // ios: Views/AnalysisDetail.swift:2399,2411
            Column(Modifier.fillMaxWidth().background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ios: Views/AnalysisDetail.swift:2400
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(AppModel.price(s, s.last), style = appFont(13f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:2401
                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2402
                }
                RangeBar(lo = d.k.lo, hi = d.k.hi, now = s.last)   // ios: Views/AnalysisDetail.swift:2404
                // ios: Views/AnalysisDetail.swift:2405
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(AppModel.price(s, d.k.lo), style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2406
                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2407
                    T(AppModel.price(s, d.k.hi), style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2408
                }
            }
            // ios: Views/AnalysisDetail.swift:2412
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("1년 ${AppModel.sgn(d.k.r1)}", "r1", d, onTip)   // ios: Views/AnalysisDetail.swift:2413
                Chip("출렁임 ${GlanceView.swingName[GlanceView.swing(d.vol)]}", "vol", d, onTip)   // ios: Views/AnalysisDetail.swift:2414
            }
        } else {   // ios: Views/AnalysisDetail.swift:2416
            T(AppModel.price(s, s.last), style = appFont(13f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:2417
            RangeBar(lo = 0.0, hi = 0.0, now = 0.0)   // ios: Views/AnalysisDetail.swift:2418
            T("1년 기록 모으는 중", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2419
        }
        HDivider()   // ios: Views/AnalysisDetail.swift:2421
        // ios: Views/AnalysisDetail.swift:2422,2423,2427,2428 (NavigationLink(value:): 분석 길에 종목을 넣으면 종목 상세가 열린다)
        Row(Modifier.fillMaxWidth().plainClickable { m.analysisPath = m.analysisPath + s }, verticalAlignment = Alignment.CenterVertically) {
            T(if (d.row.cost > 0 && s.id != KoreaEstimate.sumId) "내 보유 ${AppModel.sgn(d.k.vc)}" else "내 보유", style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:2424
            Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2425
            Sym("chevron.right", 12f, Theme.teal)   // ios: Views/AnalysisDetail.swift:2426
        }
    }
}

// ios: Views/AnalysisDetail.swift:2435
@Composable
private fun KrCard(m: AppModel, list: List<GlanceView.Item>) {
    // ios: Views/AnalysisDetail.swift:2436,2465,2466,2467
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ios: Views/AnalysisDetail.swift:2437
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("국내 ${list.size}", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2438
            Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2439
            T(AppModel.pct(list.fold(0.0) { a, x -> a + x.k.w }), style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2440
        }
        // ios: Views/AnalysisDetail.swift:2442
        list.forEachIndexed { i, d ->
            if (i > 0) HDivider()   // ios: Views/AnalysisDetail.swift:2443
            val s = d.row.sym   // ios: Views/AnalysisDetail.swift:2444
            // ios: Views/AnalysisDetail.swift:2445,2446,2461,2462 (NavigationLink(value:))
            Column(Modifier.fillMaxWidth().plainClickable { m.analysisPath = m.analysisPath + s }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // ios: Views/AnalysisDetail.swift:2447
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(s.name, style = appFont(14f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:2448
                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2449
                    // ios: Views/AnalysisDetail.swift:2450,2451
                    T(if (!d.has) AppModel.price(s, s.last) else if (d.k.dd > -0.0005) "1년 최고가" else "최고가 대비 ${AppModel.sgn(d.k.dd)}",
                        style = appFont(12f, FontWeight.SemiBold), color = if (!d.has) Theme.sub else Theme.change(if (d.k.dd > -0.0005) 1.0 else -1.0))
                }
                // ios: Views/AnalysisDetail.swift:2453
                if (d.has) {
                    RangeBar(lo = d.k.lo, hi = d.k.hi, now = s.last)   // ios: Views/AnalysisDetail.swift:2454
                    // ios: Views/AnalysisDetail.swift:2455,2456
                    T("${d.k.lo.swRound().toLong().f()}~${AppModel.price(s, d.k.hi)} · 1년 ${AppModel.sgn(d.k.r1)} · 출렁임 ${GlanceView.swingName[GlanceView.swing(d.vol)]}",
                        style = appFont(11f), color = Theme.sub)
                } else {   // ios: Views/AnalysisDetail.swift:2457
                    RangeBar(lo = 0.0, hi = 0.0, now = 0.0)   // ios: Views/AnalysisDetail.swift:2458
                    T("1년 기록 모으는 중", style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2459
                }
            }
        }
    }
}

// ios: Views/AnalysisDetail.swift:2470
@Composable
private fun CalmCard(m: AppModel, list: List<GlanceView.Item>) {
    // ios: Views/AnalysisDetail.swift:2471,2486,2487,2488
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("그 외", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2472
        // ios: Views/AnalysisDetail.swift:2473
        list.forEach { d ->
            // ios: Views/AnalysisDetail.swift:2474,2475,2482,2483 (NavigationLink(value:))
            Row(Modifier.fillMaxWidth().plainClickable { m.analysisPath = m.analysisPath + d.row.sym }, verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/AnalysisDetail.swift:2476
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(d.row.sym.name, style = appFont(14f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:2477
                }
                Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2479
                // ios: Views/AnalysisDetail.swift:2480,2481
                T("현금성", Modifier.background(Theme.tealBg, CapsuleShape).padding(horizontal = 8.dp, vertical = 4.dp), style = appFont(11f, FontWeight.SemiBold), color = Theme.tealDark)
            }
        }
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
