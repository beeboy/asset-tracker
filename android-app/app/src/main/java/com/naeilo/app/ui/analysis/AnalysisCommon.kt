package com.naeilo.app.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.MC
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.goalSpan
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.calc.pathStartDate
import com.naeilo.app.core.calc.totalAt
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Lots
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.Market
import com.naeilo.app.net.PriceHistory
import com.naeilo.app.net.Sync
import com.naeilo.app.ui.DarkBox
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.ls
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// 분석 탭 상세 화면 공통 (Views/AnalysisDetail.swift 1-196). 숫자는 모두 계산값이고 사거나 팔라는 문구는 넣지 않는다.

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

// 내 길: 회복 = 본전을 1년 안에(적립 없음), 목표 = 목표 금액·목표일·매달 적립. 시작은 실제로 길을 시작한 날 (시작한 날은 GoalRoute.kt pathStartDate)
// ios: Views/AnalysisDetail.swift:8,18
val AppModel.pathStartText: String get() = DateTimeFormatter.ofPattern("M월 d일", AppModel.dateLocale).withZone(ZoneId.systemDefault()).format(pathStartDate)
// ios: Views/AnalysisDetail.swift:19
val AppModel.pathStartMonth: String get() = "${pathStartDate.atZone(ZoneId.systemDefault()).monthValue}월"
/** pts = 실제 평가액 (시작부터 년, 원). 거래일마다 하나, 끝은 오늘 지금 평가액 */
// ios: Views/AnalysisDetail.swift:21,23
class MyPath(val start: Double, val end: Double, val key: Double, val monthly: Double, val pts: List<Pair<Double, Double>>, val act: (Double) -> Double, val need: (Double) -> Double,
             /** 시작일 뒤에 처음 넣은 국내 주식 합계 (들어온 돈, 원). 시작 대비에서 뺀다 */
             val krIn: Double = 0.0)
/** 두 날짜("yyyy-MM-dd") 사이 년 (웹 yearsBetween 과 같은 식) */
// ios: Views/AnalysisDetail.swift:26
fun yrs(a: String, b: String): Double {
    val x = Day.date(a); val y = Day.date(b); if (x == null || y == null) return 0.0   // ios: Views/AnalysisDetail.swift:27
    return (y.toEpochMilli() - x.toEpochMilli()) / 1000.0 / (365.25 * 86400)   // ios: Views/AnalysisDetail.swift:28
}
/** 내 길 실제 평가액 (웹 목표 진행 actualSeries 와 같은 식): 시작일 무렵부터 거래일마다
 *  그날 수량(수량 기록, 없으면 지금 수량) × 그날 종가 × 그날 원/달러 종가. 과거 시세가 없는 종목은 지금 평가액 그대로
 *  현금은 그대로 있다고 보고 날마다 더한다 (웹 goalPath 와 같다) */
// ios: Views/AnalysisDetail.swift:33
fun AppModel.pathSeries(start: String): List<Pair<String, Double>> {
    val rs = rows; val today = Day.today; val fxH = PriceHistory.of("FX"); val cash = cashKrw   // ios: Views/AnalysisDetail.swift:34
    val hs = rs.map { Triple(it, PriceHistory.of(it.id), Sync.siteTicker(it.id)) }   // ios: Views/AnalysisDetail.swift:35
    val from = Day.str((Day.date(start) ?: Instant.now()).minus(10, ChronoUnit.DAYS))   // ios: Views/AnalysisDetail.swift:36
    val ds = HashSet<String>()   // ios: Views/AnalysisDetail.swift:37
    for (x in hs) { val h = x.second ?: continue; for (d in h.dates.asReversed()) { if (d < from) break; if (d < today) ds.add(d) } }   // ios: Views/AnalysisDetail.swift:38
    // ios: Views/AnalysisDetail.swift:39
    return ds.sorted().map { d ->
        val q = Lots.on(lots, d)   // ios: Views/AnalysisDetail.swift:40
        // ios: Views/AnalysisDetail.swift:41
        val v = hs.fold(0.0) { a, x ->
            val h = x.second ?: return@fold a + x.first.value   // ios: Views/AnalysisDetail.swift:42
            val px = h.on(d) ?: return@fold a   // ios: Views/AnalysisDetail.swift:43
            val n = q?.let { it[x.third] ?: 0.0 } ?: x.first.h.qty   // ios: Views/AnalysisDetail.swift:44
            a + px * n * (if (x.first.sym.currency == Currency.usd) (fxH?.on(d) ?: Market.shared.valFx) else 1.0)   // ios: Views/AnalysisDetail.swift:45
        }
        Pair(d, v + cash)   // ios: Views/AnalysisDetail.swift:47
    }
}
// ios: Views/AnalysisDetail.swift:50
val AppModel.myPath: MyPath get() {
    val mEnd = if (isGoal) goalSpan else 1.0     // ios: Views/AnalysisDetail.swift:51 목표: 시작일부터 목표일까지 (사이트와 같은 날짜)
    val sD = Day.str(pathStartDate); val today = Day.today   // ios: Views/AnalysisDetail.swift:52
    // 시작한 날부터 오늘까지 (년). 오늘 시작이면 하루로 둔다
    val mS = min(mEnd * 0.95, max(1.0 / 365.25, yrs(sD, today))); val mM = if (isGoal) gM * 1e4 else 0.0; val K = keyValue   // ios: Views/AnalysisDetail.swift:54
    val now = trackValue   // ios: Views/AnalysisDetail.swift:55
    val pts: MutableList<Pair<Double, Double>>   // ios: Views/AnalysisDetail.swift:56
    // ios: Views/AnalysisDetail.swift:57
    if (route == Route.novice) {
        pts = (0..30).map { i -> val t = mS * i.toDouble() / 30; Pair(t, max(0.0, now - mM * 12 * (mS - t))) }.toMutableList()   // ios: Views/AnalysisDetail.swift:58
    } else if (isSample) {   // ios: Views/AnalysisDetail.swift:59
        // 샘플(가상 종목 DRNK)은 날짜별 시세가 없어 지난 3년 흐름에서 어림한다
        pts = (0..30).map { i -> val t = mS * i.toDouble() / 30; Pair(t, if (i == 30) now else totalAt(1 - (mS - t) / 3)) }.toMutableList()   // ios: Views/AnalysisDetail.swift:61
    } else {   // ios: Views/AnalysisDetail.swift:62
        // 웹처럼 시작일 또는 그 뒤 첫 거래일 값에서 출발 (시작일 뒤 거래일이 아직 없으면 그 전 마지막 거래일)
        val S = pathSeries(start = sD)   // ios: Views/AnalysisDetail.swift:64
        val i0 = S.indexOfFirst { it.first >= sD }.let { if (it < 0) max(0, S.size - 1) else it }   // ios: Views/AnalysisDetail.swift:65
        pts = mutableListOf()   // ios: Views/AnalysisDetail.swift:66
        // ios: Views/AnalysisDetail.swift:67
        if (S.isNotEmpty()) {
            pts.add(Pair(0.0, S[i0].second))   // ios: Views/AnalysisDetail.swift:68
            for (x in S.drop(i0 + 1)) pts.add(Pair(yrs(sD, x.first), x.second))   // ios: Views/AnalysisDetail.swift:69
        }
        pts.add(Pair(mS, now))   // ios: Views/AnalysisDetail.swift:71
    }
    val P = pts.toList()   // ios: Views/AnalysisDetail.swift:73
    // ios: Views/AnalysisDetail.swift:74
    val act: (Double) -> Double = { t ->
        if (t >= mS) now   // ios: Views/AnalysisDetail.swift:75
        else (P.lastOrNull { it.first <= t } ?: P.firstOrNull())?.second ?: now   // ios: Views/AnalysisDetail.swift:76
    }
    val vS = P.firstOrNull()?.second ?: now; val kAdj = K - mM * 12 * mEnd   // ios: Views/AnalysisDetail.swift:78
    // 시작일 뒤에 처음 넣은 국내 주식 합계는 들어온 돈: 그날부터 길에도 그 금액을 더한다 (앞선 것으로 보이지 않게. 웹 goalPath 와 같다)
    val KL = CustomSymbols.shared.krLog   // ios: Views/AnalysisDetail.swift:80
    val kin = if (krSum > 0 && route != Route.novice && !isSample && MC.krAt(KL, sD) == 0.0) KL.firstOrNull { it.d > sD && it.v > 0 } else null   // ios: Views/AnalysisDetail.swift:81
    val tIn = kin?.let { yrs(sD, it.d) } ?: Double.POSITIVE_INFINITY; val vIn = kin?.v ?: 0.0   // ios: Views/AnalysisDetail.swift:82
    val need: (Double) -> Double = { t -> mM * 12 * t + (if (kAdj > 0 && vS > 0) vS * (kAdj / vS).pow(t / mEnd) else vS + (K - vS) * t / mEnd) + (if (t >= tIn) vIn else 0.0) }   // ios: Views/AnalysisDetail.swift:83
    return MyPath(start = mS, end = mEnd, key = K, monthly = mM, pts = P, act = act, need = need, krIn = vIn)   // ios: Views/AnalysisDetail.swift:84
}
// ios: Views/AnalysisDetail.swift:86
val AppModel.myPathGapToday: Double get() { val p = myPath; val n = p.need(p.start); return if (n > 0) p.act(p.start) / n - 1 else 0.0 }

// ios: Views/AnalysisDetail.swift:88
fun AppModel.Companion.manS(krw: Double): String = (if (krw >= 0) "+" else "−") + man(kotlin.math.abs(krw))
// ios: Views/AnalysisDetail.swift:89
fun AppModel.Companion.won1(krw: Double): String = if (en) krwC(krw) else if (krw > 0 && krw < 1e4) "%,d".format(java.util.Locale.ROOT, krw.swRound().toLong()) + "원" else man(krw)
/** 읽기 쉬운 금액: 4만 6천원 · 9천원 (천원 단위 반올림, 1천만원 넘으면 man) */
// ios: Views/AnalysisDetail.swift:91
fun AppModel.Companion.wonRead(krw: Double): String {
    if (en) return krwC(krw)   // ios: Views/AnalysisDetail.swift:92
    val k = (krw / 1e3).swRound().toInt()   // ios: Views/AnalysisDetail.swift:93
    if (k <= 0) return won1(krw)   // ios: Views/AnalysisDetail.swift:94
    if (k >= 10000) return man(krw)   // ios: Views/AnalysisDetail.swift:95
    val w = k / 10; val c = k % 10   // ios: Views/AnalysisDetail.swift:96
    return if (w == 0) "${c}천원" else if (c == 0) "${w}만원" else "${w}만 ${c}천원"   // Views/AnalysisDetail.swift:97 (검사 예외: 같은 글)
}
// ios: Views/AnalysisDetail.swift:103
fun AppModel.weight(id: String): Double = rows.firstOrNull { it.id == id }?.let { it.value / max(1.0, total) } ?: 0.0

/** 분석 넘기기 화면 안인지 (넘기기 화면이 지금 쪽 제목을 단다) */
// ios: Views/AnalysisView.swift:6
private object DetailInPagerKey { const val defaultValue = false }
// ios: Views/AnalysisView.swift:7,8,9,10 (detailInPager)
val LocalDetailInPager = compositionLocalOf { DetailInPagerKey.defaultValue }

/** 상세 화면 머리: 제목은 위 막대 가운데(뒤로 가기 옆)에 두고, 여기에는 한 줄 설명만 */
// ios: Views/AnalysisDetail.swift:107,108,109,110
@Composable
fun DetailHead(title: String, sub: String) {
    // 설명이 없으면 높이 0 (막대에 제목이 붙도록 자리만 둔다)
    // ios: Views/AnalysisDetail.swift:112,113,114,115
    if (sub.isEmpty()) Box(Modifier.fillMaxWidth().height(0.dp).alpha(0f).detailTitle(title))
    else T(sub, Modifier.fillMaxWidth().alpha(1f).detailTitle(title), style = appFont(14f), color = Theme.sub)
}

// 가운데 0 을 기준으로 왼쪽(음수)·오른쪽(양수)으로 뻗는 막대
// ios: Views/AnalysisDetail.swift:120,128
@Composable
internal fun SignedBar(
    label: String,   // ios: Views/AnalysisDetail.swift:121
    value: String,   // ios: Views/AnalysisDetail.swift:122
    g: Double,   // ios: Views/AnalysisDetail.swift:123
    scale: Double,   // ios: Views/AnalysisDetail.swift:124
    pos: Color = Theme.up,   // ios: Views/AnalysisDetail.swift:125
    neg: Color = Theme.down,   // ios: Views/AnalysisDetail.swift:126
    valueColor: Color? = null,   // ios: Views/AnalysisDetail.swift:127
) {
    // ios: Views/AnalysisDetail.swift:129,144,145
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label $value" }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Modifier.widthIn(min = 52.dp), style = appFont(13f, FontWeight.SemiBold), maxLines = 1)   // ios: Views/AnalysisDetail.swift:130
        // ios: Views/AnalysisDetail.swift:131,132,133,138,140
        BoxWithConstraints(Modifier.weight(1f).height(16.dp)) {
            val half = maxWidth / 2; val w = half * min(1.0, kotlin.math.abs(g) / max(1e-9, scale)).toFloat()
            val line = Theme.line
            Box(Modifier.width(1.dp).fillMaxHeight().offset(x = half).background(line))   // ios: Views/AnalysisDetail.swift:134
            if (g < 0) Box(Modifier.align(Alignment.CenterStart).offset(x = half - w).size(w, 12.dp).background(neg, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:135
            if (g > 0) Box(Modifier.align(Alignment.CenterStart).offset(x = half).size(w, 12.dp).background(pos, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:136
        }
        // ios: Views/AnalysisDetail.swift:141,142
        T(value, Modifier.widthIn(min = 76.dp), style = appFont(13f, FontWeight.Bold).copy(fontFeatureSettings = "tnum", textAlign = androidx.compose.ui.text.style.TextAlign.End), color = valueColor ?: (if (g >= 0) pos else neg))
    }
}

// ios: Views/AnalysisDetail.swift:149,154
internal class Kpi(
    val k: String, val v: String, val sub: String,   // ios: Views/AnalysisDetail.swift:150
    val color: Color? = null,   // ios: Views/AnalysisDetail.swift:151 (없으면 Theme.ink)
    val dark: Boolean = false,   // ios: Views/AnalysisDetail.swift:152
) {
    fun darkened(on: Boolean): Kpi = Kpi(k, v, sub, color, on)   // ios: Views/AnalysisDetail.swift:153
}
@Composable
internal fun KpiBox(x: Kpi, modifier: Modifier = Modifier) {
    // ios: Views/AnalysisDetail.swift:155,160
    @Composable fun Box0(m0: Modifier) = Column(m0.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(x.k, style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:156
        FitText(x.v, appFont(18f, FontWeight.Bold), 0.7f, maxLines = 1, color = x.color ?: Theme.ink, roll = x.v)   // ios: Views/AnalysisDetail.swift:157
        T(x.sub, style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:158
    }
    // ios: Views/AnalysisDetail.swift:161,162,163,164,165
    if (x.dark) DarkBox(modifier.fillMaxWidth(), radius = 14.dp) { Box0(Modifier.fillMaxWidth()) }
    else Box0(modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)))
}

// ios: Views/AnalysisDetail.swift:170,171,172 (두 칸: 칸마다 사이 8, 줄 사이 8)
@Composable
internal fun KpiGrid(items: List<Kpi>, dark: Boolean = false) {
    val cols = listOf(8.dp, 8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(cols.size).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(cols[0])) { r.forEach { KpiBox(it.darkened(dark), Modifier.weight(1f)) }; if (r.size == 1) Box(Modifier.weight(1f)) } }
    }
}

// ios: Views/AnalysisDetail.swift:176,177
@Composable
internal fun Footnote(t: String) = T(t, Modifier.fillMaxWidth(), style = appFont(12f).ls(2f), color = Theme.muted)

// ios: Views/AnalysisDetail.swift:180,181,182,183,184
@Composable
internal fun PinnedBox(c: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Theme.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { c() }
    HDivider(Color(0x4D3C3C43))
}

// 0~1 정규 좌표 선 그리기
// ios: Views/AnalysisDetail.swift:188,189
internal fun linePath(pts: List<Pair<Double, Double>>, size: Size): Path = Path().apply {
    // ios: Views/AnalysisDetail.swift:190,191,192
    pts.forEachIndexed { i, q -> val pt = Offset((q.first * size.width).toFloat(), (q.second * size.height).toFloat()); if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
}

/** DetailTitle: 넘기기 화면 안이면 넘기기 화면이 제목을 단다. 밖이면 읽어 주기 화면 이름으로 */
// ios: Views/AnalysisView.swift:15,16,17,18,19
@Composable
fun Modifier.detailTitle(title: String): Modifier {
    val inPager = LocalDetailInPager.current
    return if (inPager) this else this.semantics { paneTitle = title }
}
