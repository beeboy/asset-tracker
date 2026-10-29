package com.naeilo.app.ui.hold

import com.naeilo.app.ui.rememberPrefBool
import com.naeilo.app.core.swFmtFrac
import com.naeilo.app.core.usdText
import androidx.compose.ui.text.AnnotatedString
import com.naeilo.app.ui.TA
import androidx.compose.foundation.layout.widthIn
import com.naeilo.app.ui.FitText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import kotlin.math.max
import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.cfmt
import com.naeilo.app.core.swFmt
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Period
import com.naeilo.app.data.Profile
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.recordDay
import com.naeilo.app.data.sectorText
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.Market
import com.naeilo.app.net.StockHit
import com.naeilo.app.net.StockSearch
import com.naeilo.app.net.AlpacaMinutes
import com.naeilo.app.net.LongbridgeMinutes
import com.naeilo.app.net.TossMinutes
import com.naeilo.app.net.YahooSample
import com.naeilo.app.net.last
import com.naeilo.app.net.quote
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.ConfirmSheet
import com.naeilo.app.ui.DialogAction
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.InputField
import com.naeilo.app.ui.IosAlert
import com.naeilo.app.ui.LogoTile
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Scrub
import com.naeilo.app.ui.Sparkline
import com.naeilo.app.ui.Spinner
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.rememberPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** 종목 상세에서 고치기 화면으로 (holdPath 에 쌓는다) */
data class EditRoute(val sym: Symbol)

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
private fun won(x: Double): String = cformat("%,d", x.swRound().roundToLong())

// 종목 세부 (시안 v38)

/** 요약 박스(이름+티커, 현재가 큰 값·괄호 다른 통화, $/원) → 한 주 가격 그래프(축 꼬리표 지금 가격·달라진 금액·시작 가격) → 평균 단가·보유 수량 → 평단 계산기 */
// ios: Views/HoldingsView.swift:575,576,595
@Composable
fun HoldingDetailView(m: AppModel,
    sym: Symbol,   // ios: Views/HoldingsView.swift:577
    period0: Period,
    inSheet: Boolean = false,   // ios: Views/HoldingsView.swift:689 팝업이면 이름은 머리에 있으니 카드에서 뺀다
    onEdit: ((Symbol) -> Unit)? = null,   // 팝업 안이면 고치기를 그 팝업 안에서 넘긴다 (ios: 시트 안 navigationDestination)
) {
    var period by remember { mutableStateOf(period0) }   // ios: Views/HoldingsView.swift:578
    var editing by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:579
    var opened by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:580
    var inUsd by rememberPrefBool("holdUsd", false)   // ios: Views/HoldingsView.swift:581
    var mins by remember { mutableStateOf<TossMinutes.Day?>(null) }   // ios: Views/HoldingsView.swift:633 1일: 토스 1분봉, 없으면 Alpaca 1분봉 (키가 있을 때만)
    val isUsd = sym.currency == Currency.usd   // ios: Views/HoldingsView.swift:583
    val du = isUsd && inUsd   // ios: Views/HoldingsView.swift:584
    /** 원래 통화 값 → 지금 고른 통화 글 (가격: $는 소수 둘째 자리) */
    // ios: Views/HoldingsView.swift:586
    fun price(v: Double, usd: Boolean): String {
        if (!isUsd) return won(v) + tr("원")   // ios: Views/HoldingsView.swift:587
        return if (usd) "$" + v.usdText() else won(v * Market.shared.valFx) + tr("원")   // ios: Views/HoldingsView.swift:588
    }
    /** 큰 값과 괄호 값 (국내 종목은 괄호 없음) */
    // ios: Views/HoldingsView.swift:591,592
    fun pair(v: Double): Pair<String, String?> = if (isUsd) Pair(price(v, du), price(v, !du)) else Pair(price(v, false), null)

    val h = m.holdings.firstOrNull { it.symbol == sym.id } ?: Holding(symbol = sym.id, qty = 0.0, avg = sym.close)   // ios: Views/HoldingsView.swift:596
    // 그래프는 한 주 가격 (고른 통화). $ 보기면 소수 둘째 자리, 원 보기면 원 단위 (2026-10-27)
    val k = if (isUsd && !du) Market.shared.valFx else 1.0   // ios: Views/HoldingsView.swift:599
    val stamp = Market.shared.updatedAt
    val day = if (period == Period.d1) mins else null   // ios: Views/HoldingsView.swift:652
    val pts = remember(period, sym, stamp, k, day) { (day?.let { listOf(Market.shared.quote(sym).prevClose) + it.closes } ?: m.prices.series(sym, period)).map { it * k } }   // ios: Views/HoldingsView.swift:653 (기간·시세가 바뀔 때만)
    val big = { v: Double -> if (du) "$" + abs(v).usdText() else won(abs(v)) + tr("원") }   // ios: Views/HoldingsView.swift:601
    val S = pts.firstOrNull() ?: 0.0; val E = pts.lastOrNull() ?: 0.0; val up = E >= S   // ios: Views/HoldingsView.swift:602
    val p = pair(sym.last); val a = pair(h.avg); val v = pair(sym.last * h.qty)   // ios: Views/HoldingsView.swift:603

    // ios: Views/HoldingsView.swift:656 캡처용: --es editMode buy 면 고치기를 바로 연다
    LaunchedEffect(Unit) { if (m.arg("editMode") != null && !opened) { opened = true; editing = true } }
    // ios: Views/HoldingsView.swift:655
    LaunchedEffect(editing) { if (editing) { editing = false; onEdit?.invoke(sym) ?: run { m.holdPath = m.holdPath + EditRoute(sym) } } }
    // ios: Views/HoldingsView.swift:711
    LaunchedEffect(period) { if (period == Period.d1 && mins == null) { val y = CustomSymbols.yahoo(sym); mins = TossMinutes.day(y) ?: AlpacaMinutes.day(y) ?: LongbridgeMinutes.day(y) } }   // ios: Views/HoldingsView.swift:747

    // ios: Views/HoldingsView.swift:666,667,668
    @Composable
    fun Kv(k0: String, v0: AnnotatedString) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(k0, Modifier.weight(1f), style = appFont(14f), color = Theme.sub); TA(v0, style = appFont(14f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"))
        }
    }

    // ios: Views/HoldingsView.swift:604,648,650,651,652,653 (제목은 이동 막대: sym.name)
    Refreshable({ Market.shared.pullRefresh(m) }, Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            Screen {
                // ios: Views/HoldingsView.swift:605
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ios: Views/HoldingsView.swift:606
                    Card {
                        // ios: Views/HoldingsView.swift:607
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!inSheet) T(sym.name, Modifier.weight(1f, fill = false), style = appFont(17f, FontWeight.Bold), maxLines = 1)   // ios: Views/HoldingsView.swift:721
                            // ios: Views/HoldingsView.swift:609,610
                            T(sym.id, Modifier.background(Theme.line, CapsuleShape).padding(horizontal = 9.dp, vertical = 2.dp), style = appFont(12f, FontWeight.Bold), color = Theme.sub, maxLines = 1)
                            Spacer(Modifier.weight(1f).widthIn(min = 4.dp))   // ios: Views/HoldingsView.swift:611
                            // ios: Views/HoldingsView.swift:612
                            if (isUsd) {
                                HoldSeg(listOf(true to "$", false to "원"), inUsd, { inUsd = it }, label = "금액 단위: ${if (inUsd) tr("달러") else tr("원")}")   // ios: Views/HoldingsView.swift:613
                            }
                        }
                        // ios: Views/HoldingsView.swift:617
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // ios: Views/HoldingsView.swift:618,619
                            FitText(p.first, appFont(30f, FontWeight.ExtraBold).copy(letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"), 0.6f, Modifier.weight(1f, fill = false).alignByBaseline(), maxLines = 1, roll = sym.last)
                            p.second?.let { s -> T("($s)", Modifier.alignByBaseline(), style = appFont(14f), color = Theme.sub, maxLines = 1) }   // ios: Views/HoldingsView.swift:620
                        }
                    }
                    // ios: Views/HoldingsView.swift:623
                    Card {
                        // ios: Views/HoldingsView.swift:624
                        if (period != Period.d1 && !m.prices.hasHistory(sym)) {
                            // ios: Views/HoldingsView.swift:625,626
                            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 140.dp), contentAlignment = Alignment.Center) { T("기록 모으는 중", style = appFont(15f, FontWeight.Bold), color = Theme.sub) }
                        } else {   // ios: Views/HoldingsView.swift:627
                            // ios: Views/HoldingsView.swift:628,629,630,631,632
                            TagChart(points = pts, height = 140.dp, tags = listOf(
                                TagChart.Tag(at = E, text = big(E), bg = Theme.ink, fg = Theme.card),
                                TagChart.Tag(at = E, dy = 20.dp, text = (if (up) "+" else "−") + big(E - S), bg = if (up) hex(0xFF8A80) else hex(0x8CC4F2)),
                                TagChart.Tag(at = S, text = big(S), bg = TagChart.gray),
                            ), left = day?.first ?: startLabel(period), right = "지금",   // ios: Views/HoldingsView.swift:685
                                scrub = day?.let { dd -> Scrub(date = { x -> dd.at[(Math.round(x * dd.at.size).toInt() - 1).coerceIn(0, dd.at.size - 1)] }, value = { big(it) }) } ?: Scrub.period(period) { big(it) })
                        }
                        ChipRow(items = Period.entries.map { it to it.label }, selection = period, onSelect = { period = it }, fill = true, pad = 4.dp)   // ios: Views/HoldingsView.swift:634
                    }
                    // ios: Views/HoldingsView.swift:636
                    if (h.qty > 0) {
                        // ios: Views/HoldingsView.swift:637,638
                        Card(padding = 0.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                // ios: Views/HoldingsView.swift:639
                                Kv("평균 단가", buildAnnotatedString { append(a.first); a.second?.let { withStyle(SpanStyle(fontSize = fx(12f), fontWeight = FontWeight.Normal, color = Theme.sub)) { append(" ($it)") } } })
                                HDivider(Theme.line)   // ios: Views/HoldingsView.swift:640
                                Kv("보유 수량", AnnotatedString(h.qty.swFmt() + tr("주")))   // ios: Views/HoldingsView.swift:641
                                HDivider(Theme.line)
                                Kv("평가금", buildAnnotatedString { append(v.first); v.second?.let { withStyle(SpanStyle(fontSize = fx(12f), fontWeight = FontWeight.Normal, color = Theme.sub)) { append(" ($it)") } } })   // 축이 한 주 가격이라 내 평가금은 여기 (2026-10-27)
                            }
                        }
                        AvgCalc(sym = sym, qty = h.qty, avg = h.avg, du = du)   // ios: Views/HoldingsView.swift:644
                    }
                    // ios: Views/HoldingsView.swift:646
                    if (sym.currency == Currency.krw && !CustomSymbols.shared.isLive(sym.id)) CustomSymbols.shared.saved(sym.id)?.let { KRPriceCard(saved = it) }
                }
            }
        }
    }
}

/** .font(.system(size:)): 글자 크기 키우기를 따르지 않는 고정 크기 */
@Composable
private fun fx(size: Float): androidx.compose.ui.unit.TextUnit = (size / LocalDensity.current.fontScale).sp

/** 그래프 왼쪽 아래 시작 날짜: 1년 넘으면 2025.10, 아니면 9.23 */
// ios: Views/HoldingsView.swift:660
private fun startLabel(period: Period): String {
    val days: Double = if (period == Period.d1) 1.0 else if (period == Period.w1) 7.0 else period.span * 1095   // ios: Views/HoldingsView.swift:661
    val d = java.time.LocalDateTime.now().minusSeconds((days * 86400).toLong())   // ios: Views/HoldingsView.swift:662
    return if (period.span >= 1.0 / 3) "${d.year}.${d.monthValue}" else "${d.monthValue}.${d.dayOfMonth}"   // ios: Views/HoldingsView.swift:663
}

/** 고치기 화면 '본전까지' 칸 (숫자 맞추기 F 가 같이 쓴다) */
// ios: Views/HoldingsView.swift:1028
internal fun needText(a: Double, px: Double): String = if (a / px - 1 > 0.0005) "+" + cfmt("%.1f", (a / px - 1) * 100) + "% 남음" else "본전 넘음"

/** 평단 계산기 (시안 v38): 결과(평균 단가·산 가격 대비·보유 수량 전 → 후)가 위, 입력 칸이 아래.
 *  수량 탭 = 살 가격(지금 가격으로 채움)·더 살 수량, 금액 탭 = 더 살 금액을 지금 가격으로. 칸은 $/원 버튼을 따른다 (계산은 원래 통화) */
// ios: Views/HoldingsView.swift:674,696
@Composable
fun AvgCalc(sym: Symbol,   // ios: Views/HoldingsView.swift:675
    qty: Double, avg: Double,   // ios: Views/HoldingsView.swift:676
    du: Boolean,   // ios: Views/HoldingsView.swift:677
) {
    var tab by remember { mutableStateOf("qty") }   // ios: Views/HoldingsView.swift:678
    var px by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:679
    var q by remember { mutableStateOf("10") }   // ios: Views/HoldingsView.swift:680
    var amt by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:681
    val isUsd = sym.currency == Currency.usd   // ios: Views/HoldingsView.swift:683
    /** 원래 통화 → 칸 통화 배율 (미국 종목을 원으로 볼 때만 환율) */
    val k = if (isUsd && !du) Market.shared.valFx else 1.0   // ios: Views/HoldingsView.swift:685
    val cur = if (isUsd && du) "$" else tr("원")   // ios: Views/HoldingsView.swift:686
    // ios: Views/HoldingsView.swift:687,688
    fun money(v: Double): String = if (isUsd && du) "$" + v.swFmtFrac(2) else won(v * k) + tr("원")
    // ios: Views/HoldingsView.swift:691
    fun fill() {
        px = if (isUsd && du) cformat("%.2f", sym.last) else (sym.last * k).swRound().toLong().toString()   // ios: Views/HoldingsView.swift:692
        amt = if (isUsd) (if (du) "1000" else (1000 * k).swRound().toLong().toString()) else "1000000"   // ios: Views/HoldingsView.swift:693
    }
    // ios: Views/HoldingsView.swift:729,730
    LaunchedEffect(du) { fill() }

    val last = sym.last   // ios: Views/HoldingsView.swift:697
    val r = AvgCalc.calc(tab, AvgCalc.num(q), AvgCalc.num(amt), AvgCalc.num(px), qty, avg, last, k)
    val add = r.first; val buy = r.second; val na = r.third
    val gridH = 6.dp; val gridV = 8.dp   // ios: Views/HoldingsView.swift:707

    // ios: Views/HoldingsView.swift:734,735,736 지금 가격이 평균 단가보다 얼마나 위·아래인지
    @Composable fun Pct(a: Double, mod: Modifier) { val rr = if (a > 0) sym.last / a - 1 else 0.0; T(AppModel.sgn(rr), mod, style = appFont(14f, FontWeight.Bold).copy(fontFeatureSettings = "tnum"), color = if (rr > 0) Theme.up else if (rr < 0) Theme.down else Theme.sub, maxLines = 1) }
    // ios: Views/HoldingsView.swift:739,740,741,742,743,744 (Grid: 오른쪽 맞춤, 화살표 세로 맞춤)
    @Composable fun Line(k0: String, a0: @Composable (Modifier) -> Unit, b0: @Composable (Modifier) -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gridH), verticalAlignment = Alignment.CenterVertically) {
            T(k0, Modifier.weight(1f), style = appFont(14f), color = Theme.sub, maxLines = 1)
            Box(Modifier.width(IntrinsicSize.Max).widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) { a0(Modifier) }
            T("→", style = appFont(14f), color = Theme.sub)
            Box(Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) { b0(Modifier) }
        }
    }
    // ios: Views/HoldingsView.swift:748,749,750,751,752,753,754,755,757
    @Composable fun Field(label: String, v: String, set: (String) -> Unit, mod: Modifier) {
        Column(mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(label, style = appFont(12f), color = Theme.sub, maxLines = 1)
            InputField(v, set, "", Modifier.background(Theme.bg, RoundedCornerShape(10.dp)).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 10.dp)
                .semantics { contentDescription = tr(label) }, number = true, style = appFont(16f, FontWeight.Bold).copy(fontFeatureSettings = "tnum"))
        }
    }
    val num = appFont(14f, FontWeight.Bold).copy(fontFeatureSettings = "tnum")   // ios: Views/HoldingsView.swift:712
    // ios: Views/HoldingsView.swift:701,726,727,728
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ios: Views/HoldingsView.swift:702
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("평단 계산기", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:703,704
            val tabs = listOf("qty" to "수량", "amt" to "금액")   // ios: Views/HoldingsView.swift:705
            HoldSeg(tabs, tab, { tab = it }, label = tabs.first { it.first == tab }.second)
        }
        Column(verticalArrangement = Arrangement.spacedBy(gridV)) {
            Line("평균 단가", { T(money(avg), it, style = num, maxLines = 1) }, { T(money(na), it, style = num, maxLines = 1) })   // ios: Views/HoldingsView.swift:708
            Line("산 가격 대비", { Pct(avg, it) }, { Pct(na, it) })   // ios: Views/HoldingsView.swift:709
            Line("보유 수량", { T(qty.swFmt() + tr("주"), it, style = num, maxLines = 1) }, { T((qty + add).swFmtFrac(0, 2) + tr("주"), it, style = num, maxLines = 1) })   // ios: Views/HoldingsView.swift:710
        }
        HDivider(Theme.line)   // ios: Views/HoldingsView.swift:713
        // ios: Views/HoldingsView.swift:714
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ios: Views/HoldingsView.swift:715
            if (tab == "qty") {
                Field("살 가격 (${cur})", px, { px = it }, Modifier.weight(1f))   // ios: Views/HoldingsView.swift:716
                Field("더 살 수량 (주)", q, { q = it }, Modifier.weight(1f))   // ios: Views/HoldingsView.swift:717
            } else {   // ios: Views/HoldingsView.swift:718
                Field("더 살 금액 (${cur})", amt, { amt = it }, Modifier.weight(1f))   // ios: Views/HoldingsView.swift:719
                Spacer(Modifier.weight(1f).height(1.dp))   // ios: Views/HoldingsView.swift:720
            }
        }
        // ios: Views/HoldingsView.swift:723,724
        T(if (tab == "qty") "총 ${money(buy * (AvgCalc.num(q) ?: 0.0))}" else "약 ${add.swFmtFrac(if (add < 10) 2 else 1)}주",
            Modifier.offset(y = (-4).dp), style = appFont(12f).copy(fontFeatureSettings = "tnum"), color = Theme.sub)
    }
}

// ios: Views/HoldingsView.swift:674 (계산만: 숫자 맞추기가 같이 쓴다)
object AvgCalc {
    /** 칸 글 → 양수 (쉼표·빈칸 무시, 0 이하는 없음) */
    // ios: Views/HoldingsView.swift:690
    fun num(s: String): Double? = s.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 }
    /** (더 사는 수량, 사는 가격, 새 평균 단가). 금액 탭은 지금 가격으로 산다 */
    fun calc(tab: String, q: Double?, amt: Double?, px: Double?, qty: Double, avg: Double, last: Double, k: Double): Triple<Double, Double, Double> {
        val add = if (tab == "qty") (q ?: 0.0) else (amt ?: 0.0) / k / max(1e-9, last)   // ios: Views/HoldingsView.swift:698
        val buy = if (tab == "qty") (px ?: 0.0) / k else last   // ios: Views/HoldingsView.swift:699
        val na = if (qty + add > 0) (qty * avg + add * buy) / (qty + add) else avg   // ios: Views/HoldingsView.swift:700
        return Triple(add, buy, na)
    }
}

// 종목 추가: 한글·영문·티커·종목 코드 검색 → 수량·평균 단가
// 앱 안 목록(예시 종목 + 한글 이름 목록)은 바로, Yahoo 검색은 입력을 멈추면 아래에 더 붙는다 (StockSearch)
/** 국내 주식 합계 (숨긴 선택 항목): 증권사 앱의 국내 주식 평가액 합계를 한 줄로. 0 이면 지운다 (웹 보유 종목 아래 칸과 같다) */
// ios: Views/HoldingsView.swift:764,765,770
@Composable
fun KoreaSumCard(m: AppModel,
    title: String = "국내 주식 합계",   // ios: Views/HoldingsView.swift:766
    done: (() -> Unit)? = null,   // ios: Views/HoldingsView.swift:767
) {
    var t by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:768
    val focus = LocalFocusManager.current   // ios: Views/HoldingsView.swift:769
    val held = m.holdings.any { it.symbol == KoreaEstimate.sumId }   // ios: Views/HoldingsView.swift:771
    val cur: Double? = if (held) CustomSymbols.shared.krNow else null; val lastD = CustomSymbols.shared.krLog.lastOrNull()?.d   // ios: Views/HoldingsView.swift:772
    val ago = lastD?.let { Day.date(it) }?.let { floor(Duration.between(it, Instant.now()).seconds / 86400.0).toInt() } ?: 0   // ios: Views/HoldingsView.swift:773
    // ios: Views/HoldingsView.swift:774
    Card {
        T(title, style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:775
        // Views/HoldingsView.swift:409 (검사 예외: 같은 글)
        T("증권사 앱 합계 그대로 (0이면 지움)" + (if (held && ago > 0) " · ${ago}일 전 금액" else "") + " · 전망은 EWY×원/달러",
          style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:777
        // ios: Views/HoldingsView.swift:778
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/HoldingsView.swift:779,780,781
            InputField(t, { t = it }, cur?.let { won(it) + "원" } ?: "금액 (원)", Modifier.weight(1f).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                       number = true, onSubmit = { focus.clearFocus() })
            // ios: Views/HoldingsView.swift:782,783,784,785,786,787,788,789,790
            Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable {
                val v = t.replace(",", "").trim().toDoubleOrNull(); if (v == null || v < 0) return@plainClickable
                val id = KoreaEstimate.sumId
                CustomSymbols.shared.setKoreaSum(v)     // 날짜별 기록 (0 = 다 팔았어요, 지난 기록은 남는다)
                if (v == 0.0) m.holdings = m.holdings.filter { it.symbol != id }
                else if (m.holdings.none { it.symbol == id }) m.addHolding(id, "1", v.toString())
                else m.recordDay()
                t = ""; focus.clearFocus(); done?.invoke()
            }, contentAlignment = Alignment.Center) { T("저장", style = appFont(15f, FontWeight.Bold), color = Theme.teal) }
        }
    }
}

/** 한국 종목 가격 다시 넣기 (시세를 받지 않으므로 사용자가 넣은 가격으로 계산) */
@Composable
// ios: Views/HoldingsView.swift:798,799,802
fun KRPriceCard(saved: CustomSymbols.Saved) {
    var t by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:800
    val focus = LocalFocusManager.current   // ios: Views/HoldingsView.swift:801
    // ios: Views/HoldingsView.swift:803
    Card {
        T(if (saved.id == KoreaEstimate.sumId) "국내 주식 합계" else "한국 종목 가격", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:804
        T(KoreaEstimate.note(saved), style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:805
        // ios: Views/HoldingsView.swift:806
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/HoldingsView.swift:807,808,809
            InputField(t, { t = it }, if (saved.id == KoreaEstimate.sumId) "합계 금액 (원)" else "지금 가격 (원)", Modifier.weight(1f).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                       number = true, onSubmit = { focus.clearFocus() })
            // ios: Views/HoldingsView.swift:810,811,812,814,815
            Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable {
                val v = t.replace(",", "").trim().toDoubleOrNull()
                if (v != null && v > 0) { CustomSymbols.shared.setPrice(saved.id, v); t = "" }
                focus.clearFocus()
            }, contentAlignment = Alignment.Center) { T("저장", style = appFont(15f, FontWeight.Bold), color = Theme.teal) }
        }
    }
}

// ios: Views/HoldingsView.swift:822,823,824,842
@Composable
fun AddHoldingView(m: AppModel,
    /** 다른 화면 안에 상자로 넣을 때 (미션 1): 스크롤·제목 없이, 추가하면 닫지 않고 빈칸으로 */
    inline: Boolean = false,   // ios: Views/HoldingsView.swift:838
    /** 종목 탭 '+ 종목 추가' 팝업: 머리 왼쪽 제목, 오른쪽 x 닫기 */
    sheet: Boolean = false,   // ios: Views/HoldingsView.swift:840
    dismiss: () -> Unit = {},
    /** 종목 탭 맨 아래 상자 (inline): 오른쪽 x 로 접고, 추가하면 접는다 */
    close: (() -> Unit)? = null,   // ios: Views/HoldingsView.swift:961
) {
    var arrive by rememberPrefString("town.arrive", "")   // ios: Views/HoldingsView.swift:825
    var query by remember { mutableStateOf(m.arg("searchTest") ?: "") }   // ios: Views/HoldingsView.swift:826
    var remote by remember { mutableStateOf(listOf<StockHit>()) }   // ios: Views/HoldingsView.swift:827
    var searching by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:828
    var picked by remember { mutableStateOf<Symbol?>(null) }   // ios: Views/HoldingsView.swift:829
    var loading by remember { mutableStateOf<String?>(null) }        // ios: Views/HoldingsView.swift:830 시세 받는 중인 종목
    var failed by remember { mutableStateOf<String?>(null) }   // ios: Views/HoldingsView.swift:831
    var qty by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:832
    var avg by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:833
    var warn by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:835
    val focus = LocalFocusManager.current   // ios: Views/HoldingsView.swift:836
    val fr = remember { List(4) { FocusRequester() } }
    var local by remember { mutableStateOf(listOf<StockHit>()) }
    val scope = rememberCoroutineScope()

    // 앱 안 목록 찾기는 글자마다 딴 줄(Default)에서 (한국 상장 2,700여 종목을 훑는다: 그리는 줄을 막지 않게)
    LaunchedEffect(query) {
        val q = query
        local = if (q.trim().isEmpty()) emptyList() else withContext(Dispatchers.Default) { StockSearch.local(q) }   // ios: Views/HoldingsView.swift:860
    }

    // 입력을 0.4초 멈추면 Yahoo 검색
    // ios: Views/HoldingsView.swift:844,854,903
    LaunchedEffect(query) {
        remote = emptyList(); failed = null   // ios: Views/HoldingsView.swift:904
        val q = query.trim()   // ios: Views/HoldingsView.swift:905
        if (q.isEmpty() || !q.all { it.code < 128 }) { searching = false; return@LaunchedEffect }   // ios: Views/HoldingsView.swift:906
        searching = true   // ios: Views/HoldingsView.swift:907
        delay(400)   // ios: Views/HoldingsView.swift:908,909 (글자가 바뀌면 이 일은 취소된다)
        val r = StockSearch.remote(q)   // ios: Views/HoldingsView.swift:910,911 (받기는 IO 줄)
        remote = r; searching = false   // ios: Views/HoldingsView.swift:912
    }

    /** 이미 시세를 아는 종목 (예시 종목 중 앱에 자료가 있는 것, 전에 추가한 것) */
    // ios: Views/HoldingsView.swift:942
    fun known(h: StockHit): Symbol? {
        if (h.currency == Currency.krw && KoreaEstimate.liveOK(h.id, name = h.name) && !CustomSymbols.shared.isLive(h.id)) return null   // ios: Views/HoldingsView.swift:943 테스트 시세를 새로 받는다
        if (Sample.isVirtual(h.id) || YahooSample.quotes[h.id] != null || CustomSymbols.shared.symbol(h.id) != null) return Sample.symbol(h.id)   // ios: Views/HoldingsView.swift:944
        return null   // ios: Views/HoldingsView.swift:945
    }
    // ios: Views/HoldingsView.swift:960
    fun open(s: Symbol) {
        // 기본값은 1주·지금 가격이라 바로 [추가하기]를 눌러도 들어간다
        picked = s; qty = "1"; warn = false   // ios: Views/HoldingsView.swift:1089
        avg = if (s.last <= 0) "" else if (s.currency == Currency.usd) cformat("%.2f", s.last) else s.last.swRound().roundToLong().toString()   // ios: Views/HoldingsView.swift:1090
    }
    // ios: Views/HoldingsView.swift:948
    suspend fun pick(h: StockHit) {
        failed = null; focus.clearFocus()   // ios: Views/HoldingsView.swift:949
        loading = h.id   // ios: Views/HoldingsView.swift:1065
        try {
            // ios: Views/HoldingsView.swift:1067,1068,1069,1070
            var p: Symbol = known(h) ?: try { CustomSymbols.shared.fetch(h) }
            catch (e: Exception) {
                // 예시 종목은 시세를 못 받아도 예시 값으로 넣을 수 있다
                val s = Sample.symbols.firstOrNull { it.id == h.id }
                if (s != null) { open(s); failed = null }   // ios: Views/HoldingsView.swift:1073
                else failed = "${h.name} 시세를 받지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요."   // ios: Views/HoldingsView.swift:1074
                return
            }
            // 지금 가격은 모든 시세(사이트·Yahoo·토스·Alpaca·Longbridge)가 모이는 Market.quote 한 곳에서 본다.
            // 0이면 고른 그 자리에서 이 종목까지 넣어 모든 시세를 한 번에 다시 받는다 (어느 시세든 같은 길)
            if (p.last <= 0 && !Sample.isVirtual(p.id)) {   // ios: Views/HoldingsView.swift:1086
                Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet() + p.id, force = true)   // ios: Views/HoldingsView.swift:1087
                p = Sample.symbol(p.id) ?: p   // ios: Views/HoldingsView.swift:1088
            }
            // 국내 종목은 시세를 받아야만 넣는다 (0원·직접 넣은 가격으로 두지 않는다)
            if (p.currency == Currency.krw) {   // ios: Views/HoldingsView.swift:1090
                if (p.last <= 0) { failed = "${h.name} 시세를 받지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요."; return }   // ios: Views/HoldingsView.swift:1092
                CustomSymbols.shared.live(p.id, p.last)   // ios: Views/HoldingsView.swift:1093
            }
            open(p)   // ios: Views/HoldingsView.swift:1095
        } finally { loading = null }
    }
    // ios: Views/HoldingsView.swift:997
    fun added(new: String?) {
        if (inline) { picked = null; query = ""; qty = ""; avg = ""; close?.invoke() } else { if (new != null) arrive = new; dismiss() }   // ios: Views/HoldingsView.swift:998
    }

    // ios: Views/HoldingsView.swift:1001
    @Composable
    fun Field(label: String, v: String, set: (String) -> Unit, f: FocusRequester) {
        // ios: Views/HoldingsView.swift:1002
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(label, style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/HoldingsView.swift:1003
            // ios: Views/HoldingsView.swift:1004,1005,1006
            InputField(v, set, "", Modifier.focusRequester(f).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp), number = true, onSubmit = { focus.clearFocus() }, selectAll = true)   // ios: Views/HoldingsView.swift:1137
        }
    }

    // ios: Views/HoldingsView.swift:915
    @Composable
    fun HitList(hits: List<StockHit>) {
        // ios: Views/HoldingsView.swift:916,938
        Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // ios: Views/HoldingsView.swift:917
            hits.forEach { h ->
                val held = m.holdings.any { it.symbol == h.id }   // ios: Views/HoldingsView.swift:918
                // ios: Views/HoldingsView.swift:919,920,931,933,934
                Row(Modifier.fillMaxWidth().then(if (loading == null) Modifier.plainClickable { scope.launch { pick(h) } } else Modifier.semantics { disabled() })
                    .defaultMinSize(minHeight = 56.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (LogoTile.has(h.id)) LogoTile(symbol = h.id, size = 32.dp)   // ios: Views/HoldingsView.swift:1054
                    // ios: Views/HoldingsView.swift:922,926
                    Column(Modifier.weight(1f)) {
                        T(h.name, style = appFont(15f, FontWeight.Bold), maxLines = 1)   // ios: Views/HoldingsView.swift:923
                        T("${h.id} · ${h.market}${KoreaEstimate.tag(h)}", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:924
                    }
                    // ios: Views/HoldingsView.swift:927,928,929
                    val s = known(h)
                    if (loading == h.id) Spinner()
                    else if (held) T("보유 중", style = appFont(13f, FontWeight.SemiBold), color = Theme.muted)
                    else if (s != null && s.last > 0) T(AppModel.price(s, s.last), style = appFont(13f, FontWeight.SemiBold))
                }
                HDivider(Theme.line)   // ios: Views/HoldingsView.swift:935
            }
        }
    }

    // ios: Views/HoldingsView.swift:966
    @Composable
    fun Form(p: Symbol) {
        // ios: Views/HoldingsView.swift:967
        Card {
            // ios: Views/HoldingsView.swift:968
            Row { T("${p.name} (${p.id})", style = appFont(16f, FontWeight.Bold)) }   // ios: Views/HoldingsView.swift:969
            // ios: Views/HoldingsView.swift:971
            T((if (p.quote.live) "지금 " else "전일 종가 ") + AppModel.price(p, p.last), style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1108
            Field("수량 (주)", qty, { qty = it }, fr[1])   // ios: Views/HoldingsView.swift:980
            val unit = if (p.currency == Currency.usd) "달러" else "원"
            Field("평균 단가 ($unit)", avg, { avg = it }, fr[2])   // Views/HoldingsView.swift:601 (검사 예외: 같은 글)
            if (warn) T("수량과 평균 단가를 0보다 큰 숫자로 넣어 주세요.", style = appFont(13f), color = Theme.up)   // ios: Views/HoldingsView.swift:1111
            // ios: Views/HoldingsView.swift:983
            PrimaryButton(title = "추가하기") {
                focus.clearFocus()   // ios: Views/HoldingsView.swift:984
                val fresh = m.holdings.none { it.symbol == p.id }   // ios: Views/HoldingsView.swift:985
                if (m.addHolding(p.id, qty, avg)) added(if (fresh) p.id else null) else warn = true   // ios: Views/HoldingsView.swift:992
            }
        }
    }

    // ios: Views/HoldingsView.swift:858
    @Composable
    fun Box0() {
        val blank = query.trim().isEmpty()   // ios: Views/HoldingsView.swift:859 빈칸이면 목록을 띄우지 않는다
        val more = remote.filter { r -> local.none { it.id == r.id } }   // ios: Views/HoldingsView.swift:861
        // ios: Views/HoldingsView.swift:862
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ios: Views/HoldingsView.swift:863
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("종목 추가", Modifier.weight(1f), style = appFont(if (inline) 17f else 22f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:864,866
                // ios: Views/HoldingsView.swift:865
                if (sheet || close != null) {   // ios: Views/HoldingsView.swift:987
                    // ios: Views/HoldingsView.swift:867,868,869,871
                    Box(Modifier.size(44.dp, 44.dp).plainClickable(close ?: dismiss).semantics { contentDescription = tr("닫기") }, contentAlignment = Alignment.Center) {
                        Sym("xmark", 15f, Theme.sub, scaled = false)
                    }
                }
            }
            // 종목을 고른 뒤에도 위 칸에 글자를 넣으면 바로 아래에 다시 찾는다 ('다른 종목' 버튼 대신)
            // ios: Views/HoldingsView.swift:874,875,876,878,879,880,881
            InputField(query, { query = it; picked = null }, "한글·영문 이름, 티커, 종목 코드",
                Modifier.focusRequester(fr[0]).background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp))
            val p = picked
            // ios: Views/HoldingsView.swift:882,883
            if (p != null) Form(p)
            else {   // ios: Views/HoldingsView.swift:884
                failed?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/HoldingsView.swift:885,886
                if (local.isNotEmpty()) HitList(local)   // ios: Views/HoldingsView.swift:888
                // ios: Views/HoldingsView.swift:889
                if (more.isNotEmpty()) {
                    T("더 찾은 종목", Modifier.padding(top = 4.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/HoldingsView.swift:890
                    HitList(more)   // ios: Views/HoldingsView.swift:891
                }
                // ios: Views/HoldingsView.swift:893,894
                if (searching) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Spinner(); T("찾는 중", style = appFont(13f), color = Theme.sub) }
                // ios: Views/HoldingsView.swift:895,896,897
                else if (!blank && local.isEmpty() && more.isEmpty()) T("찾는 종목이 없어요. 이름, 티커, 종목 코드로 찾아 보세요.", style = appFont(14f), color = Theme.sub)
            }
        }
    }

    // ios: Views/HoldingsView.swift:843
    if (inline) Box0()
    else {   // ios: Views/HoldingsView.swift:845
        // ios: Views/HoldingsView.swift:846,847,848,849,850 (팝업이면 이동 막대 없음: 부르는 쪽이 Sheet 로 연다)
        Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(top = 8.dp)) { Screen { Box0() } }
    }
}

// 수량·단가 고치기 (시안 29판): 위에 '저장하면 이렇게 바뀌어요' 전후 카드 고정, 직접 고치기 · 더 샀어요 · 팔았어요
// ios: Views/HoldingsView.swift:1012,1013,1022
@Composable
fun HoldingEditView(m: AppModel,
    sym: Symbol,   // ios: Views/HoldingsView.swift:1015
    dismiss: () -> Unit,   // ios: Views/HoldingsView.swift:1014
) {
    var mode by remember { mutableStateOf("fix") }   // ios: Views/HoldingsView.swift:1016
    var q by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1017
    var p by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1018
    var said by remember { mutableStateOf<String?>(null) }     // ios: Views/HoldingsView.swift:1019 매매를 저장한 뒤 인물의 한 줄 (평정 지수)
    var askDel by remember { mutableStateOf(false) }         // ios: Views/HoldingsView.swift:1020 지우기 전에 한 번 묻는다
    val h = m.holdings.firstOrNull { it.symbol == sym.id } ?: Holding(symbol = sym.id, qty = 0.0, avg = sym.close)   // ios: Views/HoldingsView.swift:1023
    val q0 = h.qty; val p0 = h.avg; val px = sym.last   // ios: Views/HoldingsView.swift:1024
    val r = HoldingEdit.compute(m, sym, mode, q, p, q0, p0)   // ios: Views/HoldingsView.swift:1025
    val q1 = r.q1; val p1 = r.p1; val bad = r.bad; val note = r.note   // ios: Views/HoldingsView.swift:1026
    val v0 = m.krw(sym, q0 * px); val v1 = m.krw(sym, (if (bad) q0 else q1) * px)   // ios: Views/HoldingsView.swift:1027
    val need = { a: Double -> needText(a, px) }
    // ios: Views/HoldingsView.swift:1029
    val rows: List<Triple<String, String, String>> = listOf(
        Triple("수량", "${q0.swFmt()}주", "${(if (bad) q0 else q1).swFmt()}주"),   // ios: Views/HoldingsView.swift:1030
        Triple("평균 단가", AppModel.price(sym, p0), AppModel.price(sym, if (bad) p0 else p1)),   // ios: Views/HoldingsView.swift:1031
        Triple("평가액", AppModel.man(v0), AppModel.man(v1)),   // ios: Views/HoldingsView.swift:1032
        Triple("본전까지", need(p0), need(if (bad) p0 else p1)),   // ios: Views/HoldingsView.swift:1033
    )

    // ios: Views/HoldingsView.swift:1092
    fun reset(h: Holding) { val x = HoldingEdit.initial(mode, h, sym); q = x.first; p = x.second }
    // ios: Views/HoldingsView.swift:1076,1077,1079 캡처용: --es editMode buy --es editQ 5 처럼 입력을 채워 둔다
    LaunchedEffect(Unit) { reset(h); m.arg("editMode")?.let { md -> mode = md; reset(h); q = m.arg("editQ") ?: q } }
    // ios: Views/HoldingsView.swift:1081
    var lastMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) { if (mode != lastMode) { lastMode = mode; if (m.arg("editMode") == null || q.isEmpty()) reset(h) } }

    // ios: Views/HoldingsView.swift:1094
    @Composable
    fun Field(label: String, v: String, set: (String) -> Unit) {
        // ios: Views/HoldingsView.swift:1095
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(label + (if (label.contains("주")) "" else if (sym.currency == Currency.usd) " (달러)" else " (원)"), style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/HoldingsView.swift:1096
            // ios: Views/HoldingsView.swift:1097,1098,1099,1100,1101
            InputField(v, set, "", Modifier.background(Theme.card, RoundedCornerShape(10.dp)).border(2.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp)
                .semantics { contentDescription = tr(label) }, number = true, style = appFont(18f, FontWeight.SemiBold))
        }
    }

    // ios: Views/HoldingsView.swift:1035,1053,1073,1074 (제목은 이동 막대: sym.name)
    PinnedLayout(header = {
        // ios: Views/HoldingsView.swift:1036,1052
        Column(Modifier.fillMaxWidth().background(Theme.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ios: Views/HoldingsView.swift:1037,1039
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // Spacer() 의 최소 간격 8
                T("저장하면 이렇게 바뀌어요", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:1038
                T("${sym.id} · 지금 ${AppModel.price(sym, px)}", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1040
            }
            // ios: Views/HoldingsView.swift:1042
            rows.forEach { (k, a, b) ->
                // ios: Views/HoldingsView.swift:1043,1045
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(k, Modifier.weight(1f), style = appFont(14f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1044
                    if (a != b) T(a, style = appFont(13f).copy(textDecoration = TextDecoration.LineThrough), color = Theme.muted)   // ios: Views/HoldingsView.swift:1046
                    T(b, style = appFont(16f, FontWeight.Bold), color = if (a == b) Theme.ink else Theme.teal)   // ios: Views/HoldingsView.swift:1047
                }
            }
            ChipRow(items = listOf("fix" to "직접 고치기", "buy" to "더 샀어요", "sell" to "팔았어요"), selection = mode, onSelect = { mode = it }, fill = true)   // ios: Views/HoldingsView.swift:1050
        }
        HDivider(androidx.compose.ui.graphics.Color(0x4D3C3C43))
    }) {
        // ios: Views/HoldingsView.swift:1054,1071
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(if (mode == "fix") "수량 (주)" else if (mode == "buy") "더 산 수량 (주)" else "판 수량 (주)", q) { q = it }   // ios: Views/HoldingsView.swift:1055
            Field(if (mode == "fix") "평균 단가" else if (mode == "buy") "산 가격" else "판 가격", p) { p = it }   // ios: Views/HoldingsView.swift:1056
            // ios: Views/HoldingsView.swift:1057,1058,1059
            if (note.isNotEmpty()) T(note, Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(10.dp)).padding(10.dp), style = appFont(13f))
            // ios: Views/HoldingsView.swift:1061
            PrimaryButton(title = "저장", color = if (bad) Theme.muted else Theme.teal) {
                if (bad) return@PrimaryButton   // ios: Views/HoldingsView.swift:1062
                HoldingEdit.commit(m, sym, mode, r, p0)?.let { said = it } ?: dismiss()   // ios: Views/HoldingsView.swift:1063
            }
            // ios: Views/HoldingsView.swift:1065,1066,1067
            Box(Modifier.fillMaxWidth().plainClickable { askDel = true }.defaultMinSize(minHeight = 48.dp).border(2.dp, Theme.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                T("이 종목 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
            }
            // ios: Views/HoldingsView.swift:1068,1069
            T("거래가 생기면 바뀐 수량과 평균 단가를 넣어 주세요. 앱은 거래를 하나씩 기록하지 않아요.", style = appFont(12f).ls(2f), color = Theme.muted)
        }
    }
    // ios: Views/HoldingsView.swift:1082,1083,1084,1085
    ConfirmSheet(askDel, "${sym.name} 종목을 지울까요?", "목록과 평가액에서 빠져요. 다시 넣으려면 종목 추가에서 넣으면 돼요.",
        listOf(DialogAction("지우기", destructive = true, onClick = { HoldingEdit.save(m, sym, 0.0, p0, buy = false)?.let { said = it } ?: dismiss() })), cancel = "취소") { askDel = false }
    // ios: Views/HoldingsView.swift:1086,1087,1088
    IosAlert(said != null, "내 투자 습관", said, listOf(DialogAction("확인", onClick = {}))) { said = null; dismiss() }
}

