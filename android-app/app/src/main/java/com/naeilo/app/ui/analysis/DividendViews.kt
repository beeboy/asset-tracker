package com.naeilo.app.ui.analysis

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.calc.SiteCalc
import com.naeilo.app.data.Currency
import com.naeilo.app.net.Market
import com.naeilo.app.net.last
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// 배당·세금 화면 (Views/AnalysisDetail.swift 1820-2191). 숫자만 보이고 사거나 팔라는 문구는 넣지 않는다.

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

// ios: Views/AnalysisDetail.swift:1712
object DividendView {
    /** 종목 하나의 앞으로 1년 배당 (화면용) */
    // ios: Views/AnalysisDetail.swift:1720
    class Paid(val id: String, val name: String, val out: SiteCalc.DivOut, val tax: Double, val krw: Boolean, val payMonths: List<Int>)

    /** 분석 허브 줄: 한 달 평균 세후 배당(원)과 내년 5월 양도세(원). 이 화면 첫 문장과 같은 식 */
    // ios: Views/AnalysisDetail.swift:1723
    fun hubMonthly(m: AppModel): Double {
        val t = SiteCalc.today(); val fx = Market.shared.valFx   // ios: Views/AnalysisDetail.swift:1724
        // ios: Views/AnalysisDetail.swift:1725
        val s = m.rows.fold(0.0) { a, r ->
            val krw = r.sym.currency == Currency.krw   // ios: Views/AnalysisDetail.swift:1726
            val o = SiteCalc.shared.div(r.id)?.let { SiteCalc.project(it.events, shares = r.h.qty, fx = if (krw) 1.0 else fx, krw = krw, price = r.sym.last, today = t) }   // ios: Views/AnalysisDetail.swift:1727
            a + (o?.y1 ?: 0.0)   // ios: Views/AnalysisDetail.swift:1728
        }
        return s / 12   // ios: Views/AnalysisDetail.swift:1729
    }
    // ios: Views/AnalysisDetail.swift:1731
    fun hubTax(m: AppModel): Double = if (m.taxGain > 0) max(0.0, SiteCalc.capGainTax(m.taxGain * 1e4)) else 0.0

    /** "2026-11" → "2026.11" */
    // ios: Views/AnalysisDetail.swift:2053
    fun ym(s: String): String = if (s.length >= 7) "${s.take(4)}.${s.takeLast(2).toIntOrNull() ?: 0}" else ""
    /** 막대 위 짧은 금액: 11.4만 · 6천 */
    // ios: Views/AnalysisDetail.swift:2055
    fun short(v: Double): String = if (v >= 1e4) cformat("%.1f만", v / 1e4) else "${(v / 1e3).swRound().toInt()}천"
}

/** 종목별 배당 계산 한 줄 (Swift 튜플 (id, out, known, tax) 자리) */
private class Per(val id: String, val out: SiteCalc.DivOut?, val known: Boolean, val tax: Double)
/** 화면 계산 묶음 (본문에서 한 번 계산하고 시세·자료가 바뀔 때만 다시) */
private class DivData(val months: List<String>, val byMonth: List<Double>, val paid: List<DividendView.Paid>, val net: Double, val gross: Double,
                      val zero: List<String>, val unknown: List<String>, val usIds: List<String>, val usU: Double)

// ios: Views/AnalysisDetail.swift:1723,1746
@Composable
fun DividendView(
    m: AppModel,
    /** 분석 탭 시트: "div" 배당만 · "tax" 세금만 (나눈 두 페이지). null 이면 예전처럼 둘을 전환 */
    only: String? = null,   // ios: Views/AnalysisDetail.swift:1726
) {
    var tab by remember { mutableStateOf("div") }   // ios: Views/AnalysisDetail.swift:1714
    var mode by remember { mutableStateOf("month") }   // ios: Views/AnalysisDetail.swift:1715
    var extra by remember { mutableStateOf(0.0) }   // ios: Views/AnalysisDetail.swift:1716
    val calc = SiteCalc.shared   // ios: Views/AnalysisDetail.swift:1717

    // 웹(종목 › 배당·세금)과 같은 식: 지난 12개월 배당락(사이트 시세에서 뽑은 날짜·금액)이 앞으로 같은 달에 (1+증가율)로 다시 온다고 본 세후 원화
    // 환율은 웹 fxBase 와 같게 매매기준율 (없거나 5일 넘게 묵었으면 지금 환율)
    val t = SiteCalc.today(); val fx = Market.shared.valFx   // ios: Views/AnalysisDetail.swift:1736
    val rows = m.rows; val file = calc.file; val stamp = Market.shared.updatedAt
    val dd = remember(rows, file, fx, t, stamp) {
        // ios: Views/AnalysisDetail.swift:1737
        val per = rows.map { r ->
            val d = calc.div(r.id); val krw = r.sym.currency == Currency.krw   // ios: Views/AnalysisDetail.swift:1738
            val o = d?.let { SiteCalc.project(it.events, shares = r.h.qty, fx = if (krw) 1.0 else fx, krw = krw, price = r.sym.last, today = t) }   // ios: Views/AnalysisDetail.swift:1739
            Per(r.id, o, d != null, if (krw) SiteCalc.krDivTax else SiteCalc.wht)   // ios: Views/AnalysisDetail.swift:1740
        }
        val months = (1..12).map { SiteCalc.addMonths(t, it).take(7) }   // ios: Views/AnalysisDetail.swift:1742
        val names = LinkedHashMap<String, String>().apply { rows.forEach { putIfAbsent(it.id, it.sym.name) } }   // ios: Views/AnalysisDetail.swift:1743
        // ios: Views/AnalysisDetail.swift:1744
        val paid = per.mapNotNull { p ->
            val o = p.out ?: return@mapNotNull null   // ios: Views/AnalysisDetail.swift:1745
            val pm = months.filter { (o.byMonth[it] ?: 0.0) > 0 }.map { it.takeLast(2).toIntOrNull() ?: 0 }   // ios: Views/AnalysisDetail.swift:1746
            val nm = names[p.id] ?: ""   // ios: Views/AnalysisDetail.swift:1747
            DividendView.Paid(id = p.id, name = if (nm == p.id) "" else nm, out = o, tax = p.tax, krw = p.tax == SiteCalc.krDivTax, payMonths = pm)   // ios: Views/AnalysisDetail.swift:1748
        }
        val net = paid.fold(0.0) { a, x -> a + x.out.y1 }; val gross = paid.fold(0.0) { a, x -> a + x.out.y1 / (1 - x.tax) }   // ios: Views/AnalysisDetail.swift:1750
        val byMonth = months.map { mo -> paid.fold(0.0) { a, x -> a + (x.out.byMonth[mo] ?: 0.0) } }   // ios: Views/AnalysisDetail.swift:1751
        val zero = per.filter { it.known && it.out == null }.map { it.id }   // ios: Views/AnalysisDetail.swift:1752
        val unknown = per.filter { !it.known }.map { it.id }   // ios: Views/AnalysisDetail.swift:1753
        // 해외주식 평가 이익: 웹과 같게 (지금 가격 − 매수 단가) × 수량 × 매매기준율. 매수 단가를 넣은 종목만
        val us = rows.filter { it.sym.currency == Currency.usd && it.h.avg > 0 }   // ios: Views/AnalysisDetail.swift:1755
        val usU = us.fold(0.0) { a, r -> a + (r.sym.last - r.h.avg) * r.h.qty * fx }   // ios: Views/AnalysisDetail.swift:1756
        DivData(months, byMonth, paid, net, gross, zero, unknown, us.map { it.id }, usU)
    }
    val G = m.taxGain * 1e4   // ios: Views/AnalysisDetail.swift:1757

    LaunchedEffect(Unit) { calc.refresh() }   // ios: Views/AnalysisDetail.swift:1776
    // ios: Views/AnalysisDetail.swift:1772,1773,1774,1775,1776,1777,1778
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if ((only ?: tab) == "div") {
                Income(dd.months, dd.byMonth, dd.paid, dd.net, dd.gross, dd.zero, dd.unknown, mode) { mode = it }
            } else {
                CapTax(G = G, usU = dd.usU, usIds = dd.usIds, extra = extra) { extra = it }
            }
        }
    }
    // ios: Views/AnalysisDetail.swift:1794,1795 (제목 줄은 늘 한 줄: NavBar)
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        // 나눈 한 페이지: 위에 고정할 전환 칸이 없으니 그냥 넘긴다
        // ios: Views/AnalysisDetail.swift:1781,1782,1783,1784
        if (only != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).detailTitle(if (only == "div") "배당" else "세금")) { inner() }
        } else {
            // ios: Views/AnalysisDetail.swift:1785,1786,1787,1788,1790
            PinnedLayout(header = {
                PinnedBox {
                    DetailHead(title = "배당·세금", sub = "")
                    SegTabs(items = listOf("div" to "받는 배당", "tax" to "낼 양도세"), selection = tab, onSelect = { tab = it })
                }
            }, content = { inner() })
        }
    }
}

// ① 받는 배당
// ios: Views/AnalysisDetail.swift:1781,1782
@Composable
private fun Income(months: List<String>, byMonth: List<Double>, paid: List<DividendView.Paid>, net: Double, gross: Double, zero: List<String>, unknown: List<String>,
                   mode: String, setMode: (String) -> Unit) {
    // ios: Views/AnalysisDetail.swift:1783
    Card {

        T("${DividendView.ym(months.firstOrNull() ?: "")} ~ ${DividendView.ym(months.lastOrNull() ?: "")}", style = appFont(12f), color = Theme.sub)   // Views/AnalysisDetail.swift:1894 (검사 예외: 보간 속 따옴표)
        // ios: Views/AnalysisDetail.swift:1785
        if (paid.isEmpty()) {
            T("1년 배당 없음", style = appFont(20f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1786
        } else {   // ios: Views/AnalysisDetail.swift:1787
            // ios: Views/AnalysisDetail.swift:1788,1789 (두 줄: 둘째 줄 금액만 바뀌니 그 줄을 넘긴다)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("월 평균\n".trimEnd(), style = appFont(22f, FontWeight.Bold))
                T(AppModel.wonRead(net / 12), style = appFont(22f, FontWeight.Bold), color = Theme.teal, roll = AppModel.wonRead(net / 12))
            }
            // ios: Views/AnalysisDetail.swift:1790,1791
            TA(buildAnnotatedString { append("1년 "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(AppModel.won1(net)) }; append(" · 세후") },
                style = appFont(13f), color = Theme.sub)
        }
    }
    // ios: Views/AnalysisDetail.swift:1794
    if (paid.isNotEmpty()) {
        // ios: Views/AnalysisDetail.swift:1795
        Card {
            SegTabs(items = listOf("month" to "달별", "stock" to "종목별"), selection = mode, onSelect = setMode, small = true)   // ios: Views/AnalysisDetail.swift:1796
            if (mode == "month") MonthChart(months, byMonth, paid) else StockList(paid)   // ios: Views/AnalysisDetail.swift:1797
        }
    }

    val none = (if (zero.isEmpty()) "" else "${zero.joinToString("·")} 무배당") +   // Views/AnalysisDetail.swift:1910 (검사 예외: 보간 속 따옴표)
        (if (unknown.isEmpty()) "" else (if (zero.isEmpty()) "" else " · ") + "${unknown.joinToString("·")} 자료 없음(0원)")   // Views/AnalysisDetail.swift:1911 (검사 예외: 보간 속 따옴표)
    // ios: Views/AnalysisDetail.swift:1802
    if (none.isNotEmpty()) {
        // ios: Views/AnalysisDetail.swift:1803,1804
        T(none, Modifier.fillMaxWidth().background(Theme.line, RoundedCornerShape(12.dp)).padding(12.dp), style = appFont(13f), color = Theme.sub)
    }
    // ios: Views/AnalysisDetail.swift:1806
    if (paid.isNotEmpty()) {
        val usP = paid.filter { !it.krw }; val krP = paid.filter { it.krw }   // ios: Views/AnalysisDetail.swift:1807
        val usTax = usP.fold(0.0) { a, x -> a + x.out.y1 / (1 - x.tax) - x.out.y1 }; val krTax = krP.fold(0.0) { a, x -> a + x.out.y1 / (1 - x.tax) - x.out.y1 }   // ios: Views/AnalysisDetail.swift:1808
        // ios: Views/AnalysisDetail.swift:1809
        Card {
            T("세금 뺀 금액", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1810
            ReceiptRow("세전", AppModel.won1(gross))   // ios: Views/AnalysisDetail.swift:1811
            if (usP.isNotEmpty()) ReceiptRow("미국 세금 15%", "−" + AppModel.won1(usTax))   // ios: Views/AnalysisDetail.swift:1812
            if (krP.isNotEmpty()) ReceiptRow("국내 세금 15.4%", "−" + AppModel.won1(krTax))   // ios: Views/AnalysisDetail.swift:1813
            HDivider()   // ios: Views/AnalysisDetail.swift:1814
            ReceiptRow("받는 돈", AppModel.won1(net), bold = true)   // ios: Views/AnalysisDetail.swift:1815
            if (usP.isNotEmpty()) T("한국 추가 세금 없음", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1816
        }
        // ios: Views/AnalysisDetail.swift:1818
        Card {
            T("금융소득 2,000만 기준", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1819
            // ios: Views/AnalysisDetail.swift:1820,1821,1825
            BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
                Box(Modifier.fillMaxSize().background(Theme.track, CapsuleShape))   // ios: Views/AnalysisDetail.swift:1822
                Box(Modifier.fillMaxHeight().width(maxOf(4.dp, maxWidth * min(1.0, gross / 2000e4).toFloat())).background(Theme.teal, CapsuleShape))   // ios: Views/AnalysisDetail.swift:1823
            }
            // ios: Views/AnalysisDetail.swift:1826
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TA(buildAnnotatedString { append("지금 "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(AppModel.won1(gross)) } }, style = appFont(13f))   // ios: Views/AnalysisDetail.swift:1827
                Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1828
                T(if (gross >= 2000e4) "기준 넘음" else "${AppModel.man(2000e4 - gross)} 남음", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1829
            }
            // ios: Views/AnalysisDetail.swift:1831,1832,1833,1834,1835
            T(if (gross >= 2000e4) "넘는 부분은 다른 소득과 합산 · 다음 해 5월 신고" else "아직 여유 · 예금 이자도 포함",
                Modifier.fillMaxWidth().background(if (gross >= 2000e4) Theme.cream else Theme.mintBg, RoundedCornerShape(10.dp)).padding(10.dp), style = appFont(13f))
        }
    }
    HowCalc(listOf("지난 12개월 배당이 같은 달에 들어옴", "지난 3년 늘어난 속도만큼 증가", "회사 결정에 따라 달라짐 · 세무 상담 아님"))   // ios: Views/AnalysisDetail.swift:1838
}

/** 달별 막대 계산 묶음 */
internal class MonthInfo(val mMax: Double, val hi: Set<Int>, val years: List<Pair<String, Int>>, val notes: List<String>, val label: String)

/** 달별 막대 계산 (화면 밖 몸통. 숫자 맞추기 G 가 부른다) */
internal fun monthInfo(months: List<String>, byMonth: List<Double>, paid: List<DividendView.Paid>): MonthInfo {
    val mo = { i: Int -> months[i].takeLast(2).toIntOrNull() ?: 0 }
    val mMax = max(1.0, byMonth.maxOrNull() ?: 1.0)   // ios: Views/AnalysisDetail.swift:1842
    val pos = byMonth.filter { it > 0 }.sorted()   // ios: Views/AnalysisDetail.swift:1843
    val med = if (pos.isEmpty()) 0.0 else pos[pos.size / 2]   // ios: Views/AnalysisDetail.swift:1844
    // 큰 달: 배당이 있는 달 중앙값의 1.5배 이상인 달, 큰 순으로 3개까지
    val hi = byMonth.indices.filter { byMonth[it] > 0 && byMonth[it] >= med * 1.5 }.sortedByDescending { byMonth[it] }.take(3).toSet()   // ios: Views/AnalysisDetail.swift:1846
    // 연도 구간: 같은 해 달끼리 묶음
    val years = mutableListOf<Pair<String, Int>>()   // ios: Views/AnalysisDetail.swift:1849
    for (s in months) { val y = s.take(4); if (years.lastOrNull()?.first == y) years[years.size - 1] = Pair(y, years.last().second + 1) else years.add(Pair(y, 1)) }   // ios: Views/AnalysisDetail.swift:1850
    // 설명 문장
    val notes = mutableListOf<String>()   // ios: Views/AnalysisDetail.swift:1852
    val hiSorted = hi.sorted()   // ios: Views/AnalysisDetail.swift:1853
    val causes = hiSorted.flatMap { i -> paid.filter { it.payMonths.size < 12 && (it.out.byMonth[months[i]] ?: 0.0) > 0 }.map { it.id } }.toSet().sorted()   // ios: Views/AnalysisDetail.swift:1854
    // ios: Views/AnalysisDetail.swift:1855
    if (hiSorted.isNotEmpty() && causes.size in 1..2) {

        notes.add("${hiSorted.joinToString("·") { "${mo(it)}" }}월: ${causes.joinToString("·")} 겹침")   // Views/AnalysisDetail.swift:1966 (검사 예외: 보간 속 따옴표)
    }
    val rest = byMonth.indices.filter { byMonth[it] > 0 && it !in hi }.map { byMonth[it] }.sorted()   // ios: Views/AnalysisDetail.swift:1858
    val zeroM = byMonth.indices.filter { byMonth[it] <= 0 }   // ios: Views/AnalysisDetail.swift:1859
    val monthly = paid.filter { it.payMonths.size == 12 }   // ios: Views/AnalysisDetail.swift:1860
    val parts = mutableListOf<String>()   // ios: Views/AnalysisDetail.swift:1861
    // ios: Views/AnalysisDetail.swift:1862
    if (zeroM.size in 1..3) {
        parts.add(zeroM.joinToString("·") { "${mo(it)}" } + "월 없음")   // ios: Views/AnalysisDetail.swift:1863
    } else if (zeroM.size > 3) {   // ios: Views/AnalysisDetail.swift:1864
        parts.add("배당 없는 달 ${zeroM.size}개")   // ios: Views/AnalysisDetail.swift:1865
    }
    // ios: Views/AnalysisDetail.swift:1867
    if (rest.isNotEmpty() && hi.isNotEmpty()) {
        val w = AppModel.wonRead(rest[rest.size / 2])   // ios: Views/AnalysisDetail.swift:1868
        parts.add("나머지 " + (if (monthly.size == 1) "${monthly[0].id} " else "") + "약 " + (if (w.endsWith("천원")) w.dropLast(1) else w))   // ios: Views/AnalysisDetail.swift:1869
    }
    if (parts.isNotEmpty()) notes.add(parts.joinToString(" · "))   // ios: Views/AnalysisDetail.swift:1871
    // ios: Views/AnalysisDetail.swift:1889
    val label = "달마다 배당: " + months.indices.filter { byMonth[it] > 0 }.joinToString(", ") { "${mo(it)}월 ${AppModel.won1(byMonth[it])}" }
    return MonthInfo(mMax, hi, years, notes, label)
}

// ios: Views/AnalysisDetail.swift:1841
@Composable
private fun MonthChart(months: List<String>, byMonth: List<Double>, paid: List<DividendView.Paid>) {
    val mo = { i: Int -> months[i].takeLast(2).toIntOrNull() ?: 0 }   // ios: Views/AnalysisDetail.swift:1847
    val info = remember(months, byMonth, paid) { monthInfo(months, byMonth, paid) }
    val mMax = info.mMax; val hi = info.hi; val years = info.years; val notes = info.notes

    // ios: Views/AnalysisDetail.swift:1873
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ios: Views/AnalysisDetail.swift:1874,1887,1888
        Row(Modifier.fillMaxWidth().height(118.dp).clearAndSetSemantics { contentDescription = tr(info.label) }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            // ios: Views/AnalysisDetail.swift:1875
            months.indices.forEach { i ->
                // ios: Views/AnalysisDetail.swift:1876,1884
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // ios: Views/AnalysisDetail.swift:1877,1878
                    T(if (i in hi) DividendView.short(byMonth[i]) else " ", Modifier.wrapContentWidth(unbounded = true), style = appFont(9f, FontWeight.SemiBold), color = Theme.tealDark, maxLines = 1)
                    // ios: Views/AnalysisDetail.swift:1879,1880,1881
                    Box(Modifier.fillMaxWidth().height(max(2.0, byMonth[i] / mMax * 80).dp)
                        .background(if (byMonth[i] <= 0) Theme.track else if (i in hi || hi.isEmpty()) Theme.teal else Theme.teal.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
                    T("${mo(i)}", style = appFont(10f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:1882
                }
            }
        }
        // 연도 구간: 막대 칸 폭(12칸, 사이 4)에 맞춰 해마다 선 + 연도
        // ios: Views/AnalysisDetail.swift:1891,1900
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val cell = (maxWidth - 44.dp) / 12   // ios: Views/AnalysisDetail.swift:1892
            val bc = Theme.border
            // ios: Views/AnalysisDetail.swift:1893
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/AnalysisDetail.swift:1894
                years.indices.forEach { k ->
                    // ios: Views/AnalysisDetail.swift:1895,1896,1897 (위 선은 칸 맨 위에 1 높이)
                    T(years[k].first + "년", Modifier.width(cell * years[k].second + 4.dp * (years[k].second - 1)).drawBehind { drawRect(bc, size = Size(size.width, 1.dp.toPx())) }.padding(top = 3.dp),
                        style = appFont(10f).copy(textAlign = TextAlign.Center), color = Theme.muted, maxLines = 1)
                }
            }
        }
        // ios: Views/AnalysisDetail.swift:1901
        notes.forEach { n ->
            // ios: Views/AnalysisDetail.swift:1902
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(5.dp, 5.dp).background(Theme.teal, CircleShape))   // ios: Views/AnalysisDetail.swift:1903
                T(n, Modifier.weight(1f), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1904
            }
        }
    }
}

// ios: Views/AnalysisDetail.swift:1910
@Composable
private fun StockList(paid: List<DividendView.Paid>) {
    val sMax = max(1.0, paid.maxOfOrNull { it.out.y1 } ?: 1.0)   // ios: Views/AnalysisDetail.swift:1911
    // ios: Views/AnalysisDetail.swift:1912
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ios: Views/AnalysisDetail.swift:1913
        paid.sortedByDescending { it.out.y1 }.forEach { x ->
            // ios: Views/AnalysisDetail.swift:1914
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // ios: Views/AnalysisDetail.swift:1915
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(x.id, Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1916
                    if (x.name.isNotEmpty()) T(x.name, Modifier.weight(1f).alignByBaseline(), style = appFont(12f), color = Theme.muted, maxLines = 1)   // ios: Views/AnalysisDetail.swift:1917
                    if (x.name.isEmpty()) Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1918 (이름이 있으면 이름 칸이 남은 폭을 채운다)
                    T(AppModel.won1(x.out.y1), Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1919
                }
                // ios: Views/AnalysisDetail.swift:1921,1922,1923
                BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(Modifier.fillMaxHeight().width(maxOf(4.dp, maxWidth * (x.out.y1 / sMax).toFloat())).background(Theme.teal, RoundedCornerShape(3.dp)))
                }

                T((if (x.out.n == 12) "매달" else "1년 ${x.out.n}번" + (if (x.payMonths.isEmpty()) "" else "(${x.payMonths.joinToString("·") { "$it" }}월)"))   // Views/AnalysisDetail.swift:2034 (검사 예외: 보간 속 따옴표)
                    + " · 연 ${cformat("%.2f", x.out.yld * 100)}%",   // Views/AnalysisDetail.swift:2035 (검사 예외: 보간 속 따옴표)
                    style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1926
            }
        }
    }
}

// ② 낼 양도세
// ios: Views/AnalysisDetail.swift:1934,1935
@Composable
private fun CapTax(G: Double, usU: Double, usIds: List<String>, extra: Double, setExtra: (Double) -> Unit) {
    val tax = SiteCalc.capGainTax(G); val ded = SiteCalc.cgtDed   // ios: Views/AnalysisDetail.swift:1936
    val year = (SiteCalc.today().take(4).toIntOrNull() ?: 2026) + 1   // ios: Views/AnalysisDetail.swift:1937
    // ios: Views/AnalysisDetail.swift:1938
    Card {
        T("올해 판 미국 주식", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1939
        // ios: Views/AnalysisDetail.swift:1940
        if (G <= 0) {
            T("올해 판 이익 없음\n낼 양도세 없음", style = appFont(20f, FontWeight.Bold).ls(2f))   // ios: Views/AnalysisDetail.swift:1941
        } else if (tax <= 0) {   // ios: Views/AnalysisDetail.swift:1942
            T("${AppModel.man(ded)} 공제 안\n낼 세금 없음", style = appFont(20f, FontWeight.Bold).ls(2f), roll = "${AppModel.man(ded)} 공제 안\n낼 세금 없음")   // ios: Views/AnalysisDetail.swift:1943
        } else {   // ios: Views/AnalysisDetail.swift:1944
            val orange = Theme.orange
            // ios: Views/AnalysisDetail.swift:1945,1946
            TA(buildAnnotatedString { append("내년 5월 세금\n"); withStyle(SpanStyle(color = orange)) { append("약 ${AppModel.man(tax)}원") } }, style = appFont(22f, FontWeight.Bold).ls(2f))
            T("연말 전 더 팔면 늘어요", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1947
            // ios: Views/AnalysisDetail.swift:1948,1952,1953
            Row(Modifier.fillMaxWidth().background(Theme.line, RoundedCornerShape(10.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym("calendar", 17f, Theme.muted)   // ios: Views/AnalysisDetail.swift:1949 (글자 크기는 기본 본문 17)
                TA(buildAnnotatedString { append("신고·납부 "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$year.5.1 ~ 31") } }, style = appFont(13f))   // ios: Views/AnalysisDetail.swift:1950
            }
        }
        // ios: Views/AnalysisDetail.swift:1955
        if (G > 0) {
            // ios: Views/AnalysisDetail.swift:1956,1957,1960,1961
            BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp))) {
                val bw = maxWidth
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().width(bw * min(1.0, ded / G).toFloat()).background(Theme.green.copy(alpha = 0.25f)))   // ios: Views/AnalysisDetail.swift:1958
                    if (G > ded) Box(Modifier.fillMaxHeight().weight(1f).background(Theme.orange.copy(alpha = 0.3f)))   // ios: Views/AnalysisDetail.swift:1959
                }
            }
            // ios: Views/AnalysisDetail.swift:1962
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Theme.green.copy(alpha = 0.25f), "${AppModel.man(ded)} 공제")   // ios: Views/AnalysisDetail.swift:1963
                if (G > ded) Legend(Theme.orange.copy(alpha = 0.3f), "나머지 22%")   // ios: Views/AnalysisDetail.swift:1964
            }
        }
        // ios: Views/AnalysisDetail.swift:1967
        if (tax > 0) {
            // ios: Views/AnalysisDetail.swift:1968,1976
            Column(Modifier.fillMaxWidth().background(Theme.line.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptRow("판 이익", AppModel.man(G))   // ios: Views/AnalysisDetail.swift:1969
                ReceiptRow("공제", "−" + AppModel.man(ded))   // ios: Views/AnalysisDetail.swift:1970
                ReceiptRow("과세 이익", AppModel.man(G - ded))   // ios: Views/AnalysisDetail.swift:1971
                ReceiptRow("세율", "× 22%")   // ios: Views/AnalysisDetail.swift:1972
                HDivider()   // ios: Views/AnalysisDetail.swift:1973
                ReceiptRow("세금", "약 " + AppModel.man(tax), bold = true, color = Theme.orange)   // ios: Views/AnalysisDetail.swift:1974
            }
        }
    }
    // ios: Views/AnalysisDetail.swift:1979
    Card {
        T("더 팔면?", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1980
        // ios: Views/AnalysisDetail.swift:1981
        if (usIds.isEmpty()) {
            T("매수 단가 넣은 미국 주식 없음", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1982
        } else if (usU <= 0) {   // ios: Views/AnalysisDetail.swift:1983
            T("안 판 이익 없음" + (if (usU < 0) " · 손실은 판 해 이익에서 빠짐" else ""), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1984
        } else {   // ios: Views/AnalysisDetail.swift:1985
            val x = min(extra, usU); val after = SiteCalc.capGainTax(G + x)   // ios: Views/AnalysisDetail.swift:1986
            // ios: Views/AnalysisDetail.swift:1987
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("안 판 이익", style = appFont(13f), color = Theme.sub); Spacer(Modifier.weight(1f)); T(AppModel.man(usU), style = appFont(13f, FontWeight.Bold)) }
            // ios: Views/AnalysisDetail.swift:1988
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("추가로 팔 이익", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1989
                Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1990
                T(if (x <= 0) "0원" else AppModel.man(x), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1991
            }
            // ios: Views/AnalysisDetail.swift:1993
            IosSlider(value = extra, onChange = setExtra, range = 0.0..usU, step = max(1e4, (usU / 200 / 1e4).swRound() * 1e4), tint = Theme.orange,
                // ios: Views/AnalysisDetail.swift:1994,1995
                modifier = Modifier.semantics { contentDescription = tr("추가로 팔 이익"); stateDescription = tr(if (x <= 0) "0원" else AppModel.man(x)) })
            // ios: Views/AnalysisDetail.swift:1996
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("0", style = appFont(11f), color = Theme.muted); Spacer(Modifier.weight(1f)); T("전부", style = appFont(11f), color = Theme.muted) }
            // ios: Views/AnalysisDetail.swift:1997,2005
            Column(Modifier.fillMaxWidth().background(Theme.line.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // ios: Views/AnalysisDetail.swift:1998
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("내년 5월 세금", style = appFont(13f))   // ios: Views/AnalysisDetail.swift:1999
                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:2000
                    T("약 " + AppModel.man(after), style = appFont(17f, FontWeight.Bold), color = Theme.orange, roll = "약 " + AppModel.man(after))   // ios: Views/AnalysisDetail.swift:2001
                }
                if (x > 0) T("지금보다 +${AppModel.man(after - tax)}", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2003
            }
        }
    }
    // ios: Views/AnalysisDetail.swift:2008
    Card {
        T("알아두기", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:2009
        Tip(1, "같은 해 손실은 이익에서 빼요")   // ios: Views/AnalysisDetail.swift:2010
        Tip(2, "${AppModel.man(ded)} 공제는 해마다 · 이월 없음")   // ios: Views/AnalysisDetail.swift:2011
    }
    HowCalc(listOf("판 이익: 앱 입력값", "안 판 이익: 산 날·오늘 환율로 환산", "국내 주식: 대주주 아니면 양도세 없음", "매매 권유·세무 상담 아님"))   // ios: Views/AnalysisDetail.swift:2013
}

// 작은 조각
// ios: Views/AnalysisDetail.swift:2018
@Composable
private fun ReceiptRow(k: String, v: String, bold: Boolean = false, color: Color = Theme.ink) {
    // ios: Views/AnalysisDetail.swift:2019
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(k, style = appFont(13f, if (bold) FontWeight.Bold else FontWeight.Normal), color = if (bold) Theme.ink else Theme.sub)   // ios: Views/AnalysisDetail.swift:2020
        Spacer(Modifier.weight(1f).padding(end = 8.dp))   // ios: Views/AnalysisDetail.swift:2021
        T(v, style = appFont(if (bold) 15f else 13f, if (bold) FontWeight.Bold else FontWeight.SemiBold), color = color)   // ios: Views/AnalysisDetail.swift:2022
    }
}

// ios: Views/AnalysisDetail.swift:2026
@Composable
private fun Legend(c: Color, t: String) {
    // ios: Views/AnalysisDetail.swift:2027
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp, 10.dp).background(c, RoundedCornerShape(2.dp)))   // ios: Views/AnalysisDetail.swift:2028
        T(t, style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2029
    }
}

// ios: Views/AnalysisDetail.swift:2033
@Composable
private fun Tip(n: Int, t: String) {
    // ios: Views/AnalysisDetail.swift:2034
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        // ios: Views/AnalysisDetail.swift:2035
        Box(Modifier.size(20.dp, 20.dp).background(Theme.teal, CircleShape), contentAlignment = Alignment.Center) { T("$n", style = appFont(12f, FontWeight.Bold), color = Color.White) }
        T(t, Modifier.weight(1f), style = appFont(13f))   // ios: Views/AnalysisDetail.swift:2036
    }
}

// ios: Views/AnalysisDetail.swift:2040
@Composable
private fun HowCalc(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "how")
    // ios: Views/AnalysisDetail.swift:2041,2048,2049 (DisclosureGroup: 누르면 펼침, 화살표는 sub 색)
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        // ios: Views/AnalysisDetail.swift:2045
        Row(Modifier.fillMaxWidth().plainClickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            T("계산 방법", Modifier.weight(1f), style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:2046
            Sym("chevron.right", 13f, Theme.sub, Modifier.rotate(turn))
        }
        // ios: Views/AnalysisDetail.swift:2042,2044
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { Footnote(it) }   // ios: Views/AnalysisDetail.swift:2043
            }
        }
    }
}

/** 흰 배경 + 굵게가 선택된 쪽인 전환 칸 (화면 전환·보기 전환) */
// ios: Views/AnalysisDetail.swift:2059,2063
@Composable
private fun SegTabs(
    items: List<Pair<String, String>>,   // ios: Views/AnalysisDetail.swift:2060
    selection: String, onSelect: (String) -> Unit,   // ios: Views/AnalysisDetail.swift:2061
    small: Boolean = false,   // ios: Views/AnalysisDetail.swift:2062
) {
    // ios: Views/AnalysisDetail.swift:2064,2077,2078
    Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(10.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/AnalysisDetail.swift:2065
        items.forEach { it ->
            val on = it.first == selection   // ios: Views/AnalysisDetail.swift:2066
            // ios: Views/AnalysisDetail.swift:2067,2070,2071,2073,2074
            Box(Modifier.weight(1f).plainClickable { onSelect(it.first) }.semantics { selected = on }.defaultMinSize(minHeight = if (small) 32.dp else 40.dp)
                .background(if (on) Theme.card else Color.Transparent, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                // ios: Views/AnalysisDetail.swift:2068,2069
                T(it.second, style = appFont(if (small) 12f else 14f, if (on) FontWeight.Bold else FontWeight.Normal), maxLines = 1, color = if (on) Theme.ink else Theme.sub)
            }
        }
    }
}
