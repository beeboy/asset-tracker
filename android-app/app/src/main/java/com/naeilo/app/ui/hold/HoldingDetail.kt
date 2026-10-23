package com.naeilo.app.ui.hold

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

// ios: Views/HoldingsView.swift:573,574,593
@Composable
fun HoldingDetailView(m: AppModel,
    sym: Symbol,   // ios: Views/HoldingsView.swift:575
    period0: Period,
) {
    var period by remember { mutableStateOf(period0) }   // ios: Views/HoldingsView.swift:576
    var editing by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:577
    var opened by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:578
    val h = m.holdings.firstOrNull { it.symbol == sym.id } ?: Holding(symbol = sym.id, qty = 0.0, avg = sym.close)   // ios: Views/HoldingsView.swift:594
    val stamp = Market.shared.updatedAt
    val pts = remember(period, sym, stamp) { m.prices.series(sym, period) }   // ios: Views/HoldingsView.swift:289 (기간·시세가 바뀔 때만)
    val chg = (pts.lastOrNull() ?: 1.0) / (pts.firstOrNull() ?: 1.0) - 1   // ios: Views/HoldingsView.swift:290
    val row = m.rows.firstOrNull { it.id == sym.id }   // ios: Views/HoldingsView.swift:291
    val k = remember(row, stamp) { row?.let { m.metrics(it) } }   // ios: Views/HoldingsView.swift:292
    val v = row?.value ?: 0.0; val cost = row?.cost ?: 0.0; val gain = v - cost   // ios: Views/HoldingsView.swift:293
    val r = k?.vc ?: 0.0; val need = h.avg / sym.last - 1   // ios: Views/HoldingsView.swift:294

    // ios: Views/HoldingsView.swift:654 캡처용: --es editMode buy 면 고치기를 바로 연다
    LaunchedEffect(Unit) { if (m.arg("editMode") != null && !opened) { opened = true; editing = true } }
    // ios: Views/HoldingsView.swift:653
    LaunchedEffect(editing) { if (editing) { editing = false; m.holdPath = m.holdPath + EditRoute(sym) } }

    // ios: Views/HoldingsView.swift:368
    @Composable
    fun Kv(k0: String, v0: String, last: Boolean = false) {
        // ios: Views/HoldingsView.swift:369
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // ios: Views/HoldingsView.swift:370,371
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(k0, Modifier.weight(1f), style = appFont(14f), color = Theme.sub); T(v0, style = appFont(14f, FontWeight.SemiBold))
            }
            if (!last) HDivider(Theme.line, Modifier.padding(horizontal = 16.dp))   // ios: Views/HoldingsView.swift:372
        }
    }

    // ios: Views/HoldingsView.swift:602,646,648,649,650 (제목은 이동 막대: sym.name)
    Refreshable({ Market.shared.pullRefresh(m) }, Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            Screen {
                // ios: Views/HoldingsView.swift:603
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 이름은 위 제목줄에 있으니 여기선 로고와 시장·업종만
                    // ios: Views/HoldingsView.swift:298
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LogoTile(symbol = sym.id, size = 36.dp)   // ios: Views/HoldingsView.swift:299
                        T((listOf(sym.id, sym.market) + (if (sym.sector.isEmpty()) emptyList() else listOf(sym.sectorText))).joinToString(" · "), style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:300
                    }
                    // ios: Views/HoldingsView.swift:302
                    if (h.qty > 0) {
                        // ios: Views/HoldingsView.swift:303
                        Card {
                            T("내 평가액", style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:304
                            // ios: Views/HoldingsView.swift:305
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                T(AppModel.man(v), Modifier.alignByBaseline(), style = appFont(28f, FontWeight.Bold), roll = v)   // ios: Views/HoldingsView.swift:306
                                T(AppModel.sgn(r), Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold), color = Theme.change(r))   // ios: Views/HoldingsView.swift:307
                            }
                            T(summary(m, sym, gain, r, k), style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:309
                        }
                    }
                    // ios: Views/HoldingsView.swift:621
                    Card {
                        // ios: Views/HoldingsView.swift:313,315
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            T("주가", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:314
                            // ios: Views/HoldingsView.swift:317 (298 은 검사 예외: 같은 글)
                            T("${if (period == Period.d1) "오늘" else period.label} ${AppModel.sgn(chg)}", style = appFont(13f, FontWeight.Bold), color = Theme.change(chg))
                        }
                        // ios: Views/HoldingsView.swift:622
                        if (period != Period.d1 && !m.prices.hasHistory(sym)) {
                            // ios: Views/HoldingsView.swift:623,624
                            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 140.dp), contentAlignment = Alignment.Center) { T("기록 모으는 중", style = appFont(15f, FontWeight.Bold), color = Theme.sub) }
                        } else {   // ios: Views/HoldingsView.swift:625
                            // ios: Views/HoldingsView.swift:323,324,325
                            Sparkline(points = pts, modifier = Modifier.fillMaxWidth().height(140.dp), avg = if (h.qty > 0) h.avg else null, lineWidth = 2.2.dp, showEndDot = true,
                                      avgLabel = if (h.qty > 0) "평균 단가 ${AppModel.price(sym, h.avg)}" else null, avgAlways = true,
                                      scrub = Scrub.period(period) { AppModel.price(sym, it) })
                        }
                        ChipRow(items = Period.entries.map { it to it.label }, selection = period, onSelect = { period = it }, fill = true)   // ios: Views/HoldingsView.swift:327
                    }
                    // ios: Views/HoldingsView.swift:329,330
                    Card(padding = 0.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Kv("보유 수량", h.qty.swFmt() + "주")   // ios: Views/HoldingsView.swift:331
                            Kv("평균 단가", AppModel.price(sym, h.avg))   // ios: Views/HoldingsView.swift:332
                            // ios: Views/HoldingsView.swift:333
                            Kv(if (Market.shared.isToss(sym)) "오픈 API 시세" else if (Market.shared.isAlpaca(sym)) "Alpaca(IEX) 시세" else if (Market.shared.isLongbridge(sym)) "Longbridge 시세" else if (sym.currency == Currency.krw) (if (CustomSymbols.shared.isLive(sym.id)) "전일 종가 (테스트)" else if (KoreaEstimate.info(id = sym.id, name = sym.name).proxy != null) "추정 가격" else "넣은 가격") else if (sym.quote.live) "지금 가격" else "전일 종가", AppModel.price(sym, sym.last))
                            if (sym.quote.live) Kv("오늘 등락", AppModel.sgn(sym.quote.change))   // ios: Views/HoldingsView.swift:334
                            if (sym.currency == Currency.usd) Kv("환율", "1달러 " + won(Market.shared.valFx) + "원")   // ios: Views/HoldingsView.swift:335
                            breakEven(need).let { (a, b) -> Kv(a, b) }
                            Kv("비중", k?.let { AppModel.pct(it.w) } ?: "-", last = true)   // ios: Views/HoldingsView.swift:338
                        }
                    }
                    // ios: Views/HoldingsView.swift:644
                    if (sym.currency == Currency.krw && !CustomSymbols.shared.isLive(sym.id)) CustomSymbols.shared.saved(sym.id)?.let { KRPriceCard(saved = it) }
                    // ios: Views/HoldingsView.swift:342
                    if (h.qty > 0) {
                        // ios: Views/HoldingsView.swift:343,344,345,346,347 (Label + systemImage "pencil")
                        Row(Modifier.fillMaxWidth().plainClickable { m.holdPath = m.holdPath + EditRoute(sym) }.defaultMinSize(minHeight = 48.dp).border(2.dp, Theme.teal, RoundedCornerShape(12.dp)),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Sym("pencil", 15f, Theme.teal); Spacer(Modifier.width(6.dp))
                            T("수량·평균 단가 고치기", style = appFont(15f, FontWeight.Bold), color = Theme.teal)
                        }
                    }
                    Sample.profiles[sym.id]?.let { p -> ProfileCard(p) } ?: NoProfile(sym)   // ios: Views/HoldingsView.swift:349
                }
            }
        }
    }
}

/** 상세 '본전까지' 줄 (숫자 맞추기 F 가 같이 쓴다) */
// ios: Views/HoldingsView.swift:336,337
internal fun breakEven(need: Double): Pair<String, String> = Pair(if (need > 0.0005) "본전까지" else "본전 대비", if (need > 0.0005) "주가가 " + cfmt("%.1f", need * 100) + "% 오르면 본전"
    else if (need > -0.0005) "본전과 같아요" else "본전보다 " + cfmt("%.1f", -need * 100) + "% 위")

/** 고치기 화면 '본전까지' 칸 (숫자 맞추기 F 가 같이 쓴다) */
// ios: Views/HoldingsView.swift:1026
internal fun needText(a: Double, px: Double): String = if (a / px - 1 > 0.0005) "+" + cfmt("%.1f", (a / px - 1) * 100) + "% 남음" else "본전 넘음"

/** 평가액 아래 한 줄: 원화로 얼마 벌었/잃었는지 + 1년 고점 대비 (환율은 지금 기준) */
// ios: Views/HoldingsView.swift:362
private fun summary(m: AppModel, sym: Symbol, gain: Double, r: Double, k: AppModel.HoldMetrics?): String {
    val dir = if (gain >= 0) "벌었어요" else "줄었어요"
    val g = if (abs(r) < 0.0005) "산 가격과 같아요" else "산 가격보다 ${AppModel.won(gain)} $dir"   // Views/HoldingsView.swift:341 (검사 예외: 같은 글)
    if (k == null || !m.prices.hasHistory(sym)) return g   // ios: Views/HoldingsView.swift:364
    return g + " · 1년 고점보다 " + cformat("%.0f", abs(k.dd) * 100) + "% " + (if (k.dd > -0.005) "가까이" else "아래")   // ios: Views/HoldingsView.swift:365
}

/** 소개를 아직 안 쓴 종목: 아는 것만 (이름·시장·업종) 보여 주고 비었다고 말한다 */
// ios: Views/HoldingsView.swift:377
@Composable
private fun NoProfile(sym: Symbol) {
    // ios: Views/HoldingsView.swift:378
    Card {
        T("이 종목은", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:379
        val kind = if (sym.sector.isEmpty()) "종목" else sym.sectorText + " 종목"
        // Views/HoldingsView.swift:358 (검사 예외: 같은 글)
        T(if (sym.currency == Currency.krw) "한국 시장 ${kind}이에요." else "${sym.market} ${kind}이에요.",
          style = appFont(14f))   // ios: Views/HoldingsView.swift:381
    }
}

// ios: Views/HoldingsView.swift:385
@Composable
private fun ProfileCard(p: Profile) {
    // ios: Views/HoldingsView.swift:386
    Card {
        // ios: Views/HoldingsView.swift:387,389
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(if (p.etf) "이 ETF는" else "이 회사는", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:388
            // ios: Views/HoldingsView.swift:390,391,392
            if (p.virtual) T("가상 종목", Modifier.background(hexD(0xFFF1C9, 0x4A3D16), CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                             style = appFont(11f, FontWeight.Bold), color = hexD(0x5A3E00, 0xF0D28A))
        }
        T(p.what, style = appFont(14f).ls(3f))   // ios: Views/HoldingsView.swift:395
        // ios: Views/HoldingsView.swift:396,400,401
        Column(Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(if (p.etf) "따라가는 지수" else "비전", style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/HoldingsView.swift:397
            T(p.vision, style = appFont(14f, FontWeight.SemiBold))   // ios: Views/HoldingsView.swift:398
        }
        val rows: List<Pair<String, String>> = listOf(p.ceo, (p.hqKey ?: (if (p.etf) "시장" else "본사")) to p.hq, (if (p.etf) "상장" else "설립") to p.since) + (p.extra?.let { listOf(it) } ?: emptyList())   // ios: Views/HoldingsView.swift:402
        // ios: Views/HoldingsView.swift:403
        rows.forEach { (k, v) ->
            // ios: Views/HoldingsView.swift:404,405
            Row(verticalAlignment = Alignment.Top) { T(k, Modifier.width(72.dp), style = appFont(13f), color = Theme.sub); T(v, Modifier.weight(1f), style = appFont(13f)) }
        }
        // ios: Views/HoldingsView.swift:407,408,409
        T(if (p.virtual) "드링커는 『중첩된 현실』 속 회사를 바탕으로 한 가상 종목이에요. 가격 흐름은 내 지난 자산 기록을 비율로 바꾼 값이고, 투자 권유가 아니에요."
          else "공개 정보 기준(2026년 10월)이에요. 종목 추천이 아니에요.", style = appFont(12f).ls(2f), color = Theme.muted)
    }
}

// 종목 추가: 한글·영문·티커·종목 코드 검색 → 수량·평균 단가
// 앱 안 목록(예시 종목 + 한글 이름 목록)은 바로, Yahoo 검색은 입력을 멈추면 아래에 더 붙는다 (StockSearch)
/** 국내 주식 합계 (숨긴 선택 항목): 증권사 앱의 국내 주식 평가액 합계를 한 줄로. 0 이면 지운다 (웹 보유 종목 아래 칸과 같다) */
// ios: Views/HoldingsView.swift:762,763,768
@Composable
fun KoreaSumCard(m: AppModel,
    title: String = "국내 주식 합계",   // ios: Views/HoldingsView.swift:764
    done: (() -> Unit)? = null,   // ios: Views/HoldingsView.swift:765
) {
    var t by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:766
    val focus = LocalFocusManager.current   // ios: Views/HoldingsView.swift:767
    val held = m.holdings.any { it.symbol == KoreaEstimate.sumId }   // ios: Views/HoldingsView.swift:769
    val cur: Double? = if (held) CustomSymbols.shared.krNow else null; val lastD = CustomSymbols.shared.krLog.lastOrNull()?.d   // ios: Views/HoldingsView.swift:770
    val ago = lastD?.let { Day.date(it) }?.let { floor(Duration.between(it, Instant.now()).seconds / 86400.0).toInt() } ?: 0   // ios: Views/HoldingsView.swift:771
    // ios: Views/HoldingsView.swift:772
    Card {
        T(title, style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:773
        // Views/HoldingsView.swift:407 (검사 예외: 같은 글)
        T("증권사 앱 합계 그대로 (0이면 지움)" + (if (held && ago > 0) " · ${ago}일 전 금액" else "") + " · 전망은 EWY×원/달러",
          style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:775
        // ios: Views/HoldingsView.swift:776
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/HoldingsView.swift:777,778,779
            InputField(t, { t = it }, cur?.let { won(it) + "원" } ?: "금액 (원)", Modifier.weight(1f).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                       number = true, onSubmit = { focus.clearFocus() })
            // ios: Views/HoldingsView.swift:780,781,782,783,784,785,786,787,788
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
// ios: Views/HoldingsView.swift:796,797,800
fun KRPriceCard(saved: CustomSymbols.Saved) {
    var t by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:798
    val focus = LocalFocusManager.current   // ios: Views/HoldingsView.swift:799
    // ios: Views/HoldingsView.swift:801
    Card {
        T(if (saved.id == KoreaEstimate.sumId) "국내 주식 합계" else "한국 종목 가격", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:802
        T(KoreaEstimate.note(saved), style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:803
        // ios: Views/HoldingsView.swift:804
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/HoldingsView.swift:805,806,807
            InputField(t, { t = it }, if (saved.id == KoreaEstimate.sumId) "합계 금액 (원)" else "지금 가격 (원)", Modifier.weight(1f).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                       number = true, onSubmit = { focus.clearFocus() })
            // ios: Views/HoldingsView.swift:808,809,810,812,813
            Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable {
                val v = t.replace(",", "").trim().toDoubleOrNull()
                if (v != null && v > 0) { CustomSymbols.shared.setPrice(saved.id, v); t = "" }
                focus.clearFocus()
            }, contentAlignment = Alignment.Center) { T("저장", style = appFont(15f, FontWeight.Bold), color = Theme.teal) }
        }
    }
}

// ios: Views/HoldingsView.swift:820,821,822,840
@Composable
fun AddHoldingView(m: AppModel,
    /** 다른 화면 안에 상자로 넣을 때 (미션 1): 스크롤·제목 없이, 추가하면 닫지 않고 빈칸으로 */
    inline: Boolean = false,   // ios: Views/HoldingsView.swift:836
    dismiss: () -> Unit = {},
) {
    var arrive by rememberPrefString("town.arrive", "")   // ios: Views/HoldingsView.swift:823
    var query by remember { mutableStateOf(m.arg("searchTest") ?: "") }   // ios: Views/HoldingsView.swift:824
    var remote by remember { mutableStateOf(listOf<StockHit>()) }   // ios: Views/HoldingsView.swift:825
    var searching by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:826
    var picked by remember { mutableStateOf<Symbol?>(null) }   // ios: Views/HoldingsView.swift:827
    var loading by remember { mutableStateOf<String?>(null) }        // ios: Views/HoldingsView.swift:828 시세 받는 중인 종목
    var failed by remember { mutableStateOf<String?>(null) }   // ios: Views/HoldingsView.swift:829
    var qty by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:830
    var avg by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:831
    var kp by remember { mutableStateOf("") }                        // ios: Views/HoldingsView.swift:832 한국 종목: 지금 가격 (시세를 받지 않는다)
    var warn by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:833
    val focus = LocalFocusManager.current   // ios: Views/HoldingsView.swift:834
    val fr = remember { List(4) { FocusRequester() } }
    var local by remember { mutableStateOf(listOf<StockHit>()) }
    val scope = rememberCoroutineScope()

    // 앱 안 목록 찾기는 글자마다 딴 줄(Default)에서 (한국 상장 2,700여 종목을 훑는다: 그리는 줄을 막지 않게)
    LaunchedEffect(query) {
        val q = query
        local = if (q.trim().isEmpty()) emptyList() else withContext(Dispatchers.Default) { StockSearch.local(q) }   // ios: Views/HoldingsView.swift:858
    }

    // 입력을 0.4초 멈추면 Yahoo 검색
    // ios: Views/HoldingsView.swift:842,852,901
    LaunchedEffect(query) {
        remote = emptyList(); failed = null   // ios: Views/HoldingsView.swift:902
        val q = query.trim()   // ios: Views/HoldingsView.swift:903
        if (q.isEmpty() || !q.all { it.code < 128 }) { searching = false; return@LaunchedEffect }   // ios: Views/HoldingsView.swift:904
        searching = true   // ios: Views/HoldingsView.swift:905
        delay(400)   // ios: Views/HoldingsView.swift:906,907 (글자가 바뀌면 이 일은 취소된다)
        val r = StockSearch.remote(q)   // ios: Views/HoldingsView.swift:908,909 (받기는 IO 줄)
        remote = r; searching = false   // ios: Views/HoldingsView.swift:910
    }

    /** 이미 시세를 아는 종목 (예시 종목 중 앱에 자료가 있는 것, 전에 추가한 것) */
    // ios: Views/HoldingsView.swift:940
    fun known(h: StockHit): Symbol? {
        if (h.currency == Currency.krw && KoreaEstimate.liveOK(h.id, name = h.name) && !CustomSymbols.shared.isLive(h.id)) return null   // ios: Views/HoldingsView.swift:941 테스트 시세를 새로 받는다
        if (h.id == "DRNK" || YahooSample.quotes[h.id] != null || CustomSymbols.shared.symbol(h.id) != null) return Sample.symbol(h.id)   // ios: Views/HoldingsView.swift:942
        return null   // ios: Views/HoldingsView.swift:943
    }
    // ios: Views/HoldingsView.swift:958
    fun open(s: Symbol) {
        picked = s; qty = ""; warn = false   // ios: Views/HoldingsView.swift:959
        avg = if (s.currency == Currency.usd) cformat("%.2f", s.last) else ""   // ios: Views/HoldingsView.swift:960
        kp = if (s.currency == Currency.krw && s.last > 0) s.last.swRound().roundToLong().toString() else ""   // ios: Views/HoldingsView.swift:961
    }
    // ios: Views/HoldingsView.swift:946
    suspend fun pick(h: StockHit) {
        failed = null; focus.clearFocus()   // ios: Views/HoldingsView.swift:947
        known(h)?.let { open(it); return }   // ios: Views/HoldingsView.swift:948
        loading = h.id   // ios: Views/HoldingsView.swift:949
        // ios: Views/HoldingsView.swift:950,951,952
        try { open(CustomSymbols.shared.fetch(h)) }
        catch (e: Exception) {
            // 예시 종목은 시세를 못 받아도 예시 값으로 넣을 수 있다
            val s = Sample.symbols.firstOrNull { it.id == h.id }
            if (s != null) { open(s); failed = null }   // ios: Views/HoldingsView.swift:954
            else failed = "${h.name} 시세를 받지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요."   // ios: Views/HoldingsView.swift:955
        } finally { loading = null }
    }
    // ios: Views/HoldingsView.swift:995
    fun added(new: String?) {
        if (inline) { picked = null; query = ""; qty = ""; avg = ""; kp = "" } else { if (new != null) arrive = new; dismiss() }   // ios: Views/HoldingsView.swift:996
    }

    // ios: Views/HoldingsView.swift:999
    @Composable
    fun Field(label: String, v: String, set: (String) -> Unit, f: FocusRequester) {
        // ios: Views/HoldingsView.swift:1000
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(label, style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/HoldingsView.swift:1001
            // ios: Views/HoldingsView.swift:1002,1003,1004
            InputField(v, set, "", Modifier.focusRequester(f).border(1.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp), number = true, onSubmit = { focus.clearFocus() })
        }
    }

    // ios: Views/HoldingsView.swift:913
    @Composable
    fun HitList(hits: List<StockHit>) {
        // ios: Views/HoldingsView.swift:914,936
        Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // ios: Views/HoldingsView.swift:915
            hits.forEach { h ->
                val held = m.holdings.any { it.symbol == h.id }   // ios: Views/HoldingsView.swift:916
                // ios: Views/HoldingsView.swift:917,918,929,931,932
                Row(Modifier.fillMaxWidth().then(if (loading == null) Modifier.plainClickable { scope.launch { pick(h) } } else Modifier.semantics { disabled() })
                    .defaultMinSize(minHeight = 56.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoTile(symbol = h.id, size = 32.dp)   // ios: Views/HoldingsView.swift:919
                    // ios: Views/HoldingsView.swift:920,924
                    Column(Modifier.weight(1f)) {
                        T(h.name, style = appFont(15f, FontWeight.Bold), maxLines = 1)   // ios: Views/HoldingsView.swift:921
                        T("${h.id} · ${h.market}${KoreaEstimate.tag(h)}", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:922
                    }
                    // ios: Views/HoldingsView.swift:925,926,927
                    val s = known(h)
                    if (loading == h.id) Spinner()
                    else if (held) T("보유 중", style = appFont(13f, FontWeight.SemiBold), color = Theme.muted)
                    else if (s != null && s.last > 0) T(AppModel.price(s, s.last), style = appFont(13f, FontWeight.SemiBold))
                }
                HDivider(Theme.line)   // ios: Views/HoldingsView.swift:933
            }
        }
    }

    // ios: Views/HoldingsView.swift:964
    @Composable
    fun Form(p: Symbol) {
        // ios: Views/HoldingsView.swift:965
        Card {
            // ios: Views/HoldingsView.swift:966
            Row { T("${p.name} (${p.id})", style = appFont(16f, FontWeight.Bold)) }   // ios: Views/HoldingsView.swift:967
            // ios: Views/HoldingsView.swift:969
            if (p.currency == Currency.krw && !CustomSymbols.shared.isLive(p.id)) {
                val pk = KoreaEstimate.info(id = p.id, name = p.name).proxy != null   // ios: Views/HoldingsView.swift:970
                // ios: Views/HoldingsView.swift:971,972,973
                T(if (pk) "한국 시세는 받지 않아요. 지금 가격을 한 번 넣으면 그 뒤로는 같은 미국 ETF × 원/달러 움직임으로 추정해요."
                  else "한국 시세는 받지 않아요. 넣은 가격 그대로 계산하고, 움직임 예측은 한국 시장 평균(EWY × 원/달러)으로 해요.", style = appFont(13f), color = Theme.sub)
                Field("지금 가격 (원)", kp, { kp = it }, fr[3])   // ios: Views/HoldingsView.swift:974
            } else {   // ios: Views/HoldingsView.swift:975
                T((if (p.quote.live) "지금 " else "전일 종가 ") + AppModel.price(p, p.last), style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:976
            }
            Field("수량 (주)", qty, { qty = it }, fr[1])   // ios: Views/HoldingsView.swift:978
            val unit = if (p.currency == Currency.usd) "달러" else "원, 비우면 지금 가격"
            Field("평균 단가 ($unit)", avg, { avg = it }, fr[2])   // Views/HoldingsView.swift:599 (검사 예외: 같은 글)
            if (warn) T(if (p.currency == Currency.krw) "지금 가격과 수량을 0보다 큰 숫자로 넣어 주세요." else "수량과 평균 단가를 0보다 큰 숫자로 넣어 주세요.", style = appFont(13f), color = Theme.up)   // ios: Views/HoldingsView.swift:980
            // ios: Views/HoldingsView.swift:981
            PrimaryButton(title = "추가하기") {
                focus.clearFocus()   // ios: Views/HoldingsView.swift:982
                val fresh = m.holdings.none { it.symbol == p.id }   // ios: Views/HoldingsView.swift:983
                // ios: Views/HoldingsView.swift:984
                if (p.currency == Currency.krw && !CustomSymbols.shared.isLive(p.id)) {
                    val v = kp.replace(",", "").trim().toDoubleOrNull(); if (v == null || v <= 0) { warn = true; return@PrimaryButton }   // ios: Views/HoldingsView.swift:985
                    CustomSymbols.shared.setPrice(p.id, v)   // ios: Views/HoldingsView.swift:986
                    if (m.addHolding(p.id, qty, if (avg.trim().isEmpty()) kp else avg)) added(if (fresh) p.id else null) else warn = true   // ios: Views/HoldingsView.swift:987
                    return@PrimaryButton   // ios: Views/HoldingsView.swift:988
                }
                if (m.addHolding(p.id, qty, avg)) added(if (fresh) p.id else null) else warn = true   // ios: Views/HoldingsView.swift:990
            }
        }
    }

    // ios: Views/HoldingsView.swift:856
    @Composable
    fun Box0() {
        val blank = query.trim().isEmpty()   // ios: Views/HoldingsView.swift:857 빈칸이면 목록을 띄우지 않는다
        val more = remote.filter { r -> local.none { it.id == r.id } }   // ios: Views/HoldingsView.swift:859
        // ios: Views/HoldingsView.swift:860
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("종목 추가", style = appFont(if (inline) 17f else 22f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:513
            // 종목을 고른 뒤에도 위 칸에 글자를 넣으면 바로 아래에 다시 찾는다 ('다른 종목' 버튼 대신)
            // ios: Views/HoldingsView.swift:872,873,874,876,877,878,879
            InputField(query, { query = it; picked = null }, "한글·영문 이름, 티커, 종목 코드",
                Modifier.focusRequester(fr[0]).background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp))
            val p = picked
            // ios: Views/HoldingsView.swift:880,881
            if (p != null) Form(p)
            else {   // ios: Views/HoldingsView.swift:882
                failed?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/HoldingsView.swift:883,884
                if (local.isNotEmpty()) HitList(local)   // ios: Views/HoldingsView.swift:886
                // ios: Views/HoldingsView.swift:887
                if (more.isNotEmpty()) {
                    T("더 찾은 종목", Modifier.padding(top = 4.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/HoldingsView.swift:888
                    HitList(more)   // ios: Views/HoldingsView.swift:889
                }
                // ios: Views/HoldingsView.swift:891,892
                if (searching) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Spinner(); T("찾는 중", style = appFont(13f), color = Theme.sub) }
                // ios: Views/HoldingsView.swift:893,894,895
                else if (!blank && local.isEmpty() && more.isEmpty()) T("찾는 종목이 없어요. 이름, 티커, 종목 코드로 찾아 보세요.", style = appFont(14f), color = Theme.sub)
            }
        }
    }

    // ios: Views/HoldingsView.swift:841
    if (inline) Box0()
    else {   // ios: Views/HoldingsView.swift:843
        // ios: Views/HoldingsView.swift:844,845,846,848
        Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(top = 8.dp)) { Screen { Box0() } }
    }
}

// 수량·단가 고치기 (시안 29판): 위에 '저장하면 이렇게 바뀌어요' 전후 카드 고정, 직접 고치기 · 더 샀어요 · 팔았어요
// ios: Views/HoldingsView.swift:1010,1011,1020
@Composable
fun HoldingEditView(m: AppModel,
    sym: Symbol,   // ios: Views/HoldingsView.swift:1013
    dismiss: () -> Unit,   // ios: Views/HoldingsView.swift:1012
) {
    var mode by remember { mutableStateOf("fix") }   // ios: Views/HoldingsView.swift:1014
    var q by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1015
    var p by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1016
    var said by remember { mutableStateOf<String?>(null) }     // ios: Views/HoldingsView.swift:1017 매매를 저장한 뒤 인물의 한 줄 (평정 지수)
    var askDel by remember { mutableStateOf(false) }         // ios: Views/HoldingsView.swift:1018 지우기 전에 한 번 묻는다
    val h = m.holdings.firstOrNull { it.symbol == sym.id } ?: Holding(symbol = sym.id, qty = 0.0, avg = sym.close)   // ios: Views/HoldingsView.swift:1021
    val q0 = h.qty; val p0 = h.avg; val px = sym.last   // ios: Views/HoldingsView.swift:1022
    val r = HoldingEdit.compute(m, sym, mode, q, p, q0, p0)   // ios: Views/HoldingsView.swift:1023
    val q1 = r.q1; val p1 = r.p1; val bad = r.bad; val note = r.note   // ios: Views/HoldingsView.swift:1024
    val v0 = m.krw(sym, q0 * px); val v1 = m.krw(sym, (if (bad) q0 else q1) * px)   // ios: Views/HoldingsView.swift:1025
    val need = { a: Double -> needText(a, px) }
    // ios: Views/HoldingsView.swift:1027
    val rows: List<Triple<String, String, String>> = listOf(
        Triple("수량", "${q0.swFmt()}주", "${(if (bad) q0 else q1).swFmt()}주"),   // ios: Views/HoldingsView.swift:1028
        Triple("평균 단가", AppModel.price(sym, p0), AppModel.price(sym, if (bad) p0 else p1)),   // ios: Views/HoldingsView.swift:1029
        Triple("평가액", AppModel.man(v0), AppModel.man(v1)),   // ios: Views/HoldingsView.swift:1030
        Triple("본전까지", need(p0), need(if (bad) p0 else p1)),   // ios: Views/HoldingsView.swift:1031
    )

    // ios: Views/HoldingsView.swift:1089
    fun reset(h: Holding) { val x = HoldingEdit.initial(mode, h, sym); q = x.first; p = x.second }
    // ios: Views/HoldingsView.swift:1073,1074,1076 캡처용: --es editMode buy --es editQ 5 처럼 입력을 채워 둔다
    LaunchedEffect(Unit) { reset(h); m.arg("editMode")?.let { md -> mode = md; reset(h); q = m.arg("editQ") ?: q } }
    // ios: Views/HoldingsView.swift:1078
    var lastMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) { if (mode != lastMode) { lastMode = mode; if (m.arg("editMode") == null || q.isEmpty()) reset(h) } }

    // ios: Views/HoldingsView.swift:1091
    @Composable
    fun Field(label: String, v: String, set: (String) -> Unit) {
        // ios: Views/HoldingsView.swift:1092
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(label + (if (label.contains("주")) "" else if (sym.currency == Currency.usd) " (달러)" else " (원)"), style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/HoldingsView.swift:1093
            // ios: Views/HoldingsView.swift:1094,1095,1096,1097,1098
            InputField(v, set, "", Modifier.background(Theme.card, RoundedCornerShape(10.dp)).border(2.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp)
                .semantics { contentDescription = tr(label) }, number = true, style = appFont(18f, FontWeight.SemiBold))
        }
    }

    // ios: Views/HoldingsView.swift:1033,1051,1071,1072 (제목은 이동 막대: sym.name)
    PinnedLayout(header = {
        // ios: Views/HoldingsView.swift:1034,1050
        Column(Modifier.fillMaxWidth().background(Theme.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ios: Views/HoldingsView.swift:1035,1037
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // Spacer() 의 최소 간격 8
                T("저장하면 이렇게 바뀌어요", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:1036
                T("${sym.id} · 지금 ${AppModel.price(sym, px)}", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1038
            }
            // ios: Views/HoldingsView.swift:1040
            rows.forEach { (k, a, b) ->
                // ios: Views/HoldingsView.swift:1041,1043
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(k, Modifier.weight(1f), style = appFont(14f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1042
                    if (a != b) T(a, style = appFont(13f).copy(textDecoration = TextDecoration.LineThrough), color = Theme.muted)   // ios: Views/HoldingsView.swift:1044
                    T(b, style = appFont(16f, FontWeight.Bold), color = if (a == b) Theme.ink else Theme.teal)   // ios: Views/HoldingsView.swift:1045
                }
            }
            ChipRow(items = listOf("fix" to "직접 고치기", "buy" to "더 샀어요", "sell" to "팔았어요"), selection = mode, onSelect = { mode = it }, fill = true)   // ios: Views/HoldingsView.swift:1048
        }
        HDivider(androidx.compose.ui.graphics.Color(0x4D3C3C43))
    }) {
        // ios: Views/HoldingsView.swift:1052,1069
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(if (mode == "fix") "수량 (주)" else if (mode == "buy") "더 산 수량 (주)" else "판 수량 (주)", q) { q = it }   // ios: Views/HoldingsView.swift:1053
            Field(if (mode == "fix") "평균 단가" else if (mode == "buy") "산 가격" else "판 가격", p) { p = it }   // ios: Views/HoldingsView.swift:1054
            // ios: Views/HoldingsView.swift:1055,1056,1057
            if (note.isNotEmpty()) T(note, Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(10.dp)).padding(10.dp), style = appFont(13f))
            // ios: Views/HoldingsView.swift:1059
            PrimaryButton(title = "저장", color = if (bad) Theme.muted else Theme.teal) {
                if (bad) return@PrimaryButton   // ios: Views/HoldingsView.swift:1060
                HoldingEdit.commit(m, sym, mode, r, p0)?.let { said = it } ?: dismiss()   // ios: Views/HoldingsView.swift:1061
            }
            // ios: Views/HoldingsView.swift:1063,1064,1065
            Box(Modifier.fillMaxWidth().plainClickable { askDel = true }.defaultMinSize(minHeight = 48.dp).border(2.dp, Theme.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                T("이 종목 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
            }
            // ios: Views/HoldingsView.swift:1066,1067
            T("거래가 생기면 바뀐 수량과 평균 단가를 넣어 주세요. 앱은 거래를 하나씩 기록하지 않아요.", style = appFont(12f).ls(2f), color = Theme.muted)
        }
    }
    // ios: Views/HoldingsView.swift:1079,1080,1081,1082
    ConfirmSheet(askDel, "${sym.name} 종목을 지울까요?", "목록과 평가액에서 빠져요. 다시 넣으려면 종목 추가에서 넣으면 돼요.",
        listOf(DialogAction("지우기", destructive = true, onClick = { HoldingEdit.save(m, sym, 0.0, p0, buy = false)?.let { said = it } ?: dismiss() })), cancel = "취소") { askDel = false }
    // ios: Views/HoldingsView.swift:1083,1084,1085
    IosAlert(said != null, "내 투자 습관", said, listOf(DialogAction("확인", onClick = {}))) { said = null; dismiss() }
}

