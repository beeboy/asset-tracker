package com.naeilo.app.ui.hold

import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.hex
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.home.BandBuddy
import com.naeilo.app.ui.home.BoxSchemeScope
import com.naeilo.app.ui.home.ThemeBand
import com.naeilo.app.ui.home.ThemeBandFrame
import com.naeilo.app.ui.home.boxDark
import com.naeilo.app.ui.home.boxInk
import com.naeilo.app.ui.home.bx
import com.naeilo.app.ui.home.themeBackdrop
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.TA
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Canvas
import com.naeilo.app.ui.home.tileWall
import kotlin.math.roundToInt
import com.naeilo.app.ui.home.FreshLabel
import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import com.naeilo.app.data.EarningsFeed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.HoldSort
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.core.calc.calmDrift
import com.naeilo.app.core.calc.calmSpeaker
import com.naeilo.app.core.calc.calmTrade
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Period
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.friendName
import com.naeilo.app.data.recordDay
import com.naeilo.app.data.voice
import com.naeilo.app.net.Market
import com.naeilo.app.net.PriceHistory
import com.naeilo.app.net.Sync
import com.naeilo.app.net.last
import com.naeilo.app.net.quote
import com.naeilo.app.ui.town.TownView
import com.naeilo.app.ui.wave.TownButton
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.ConfirmSheet
import com.naeilo.app.ui.ContextMenu
import com.naeilo.app.ui.DialogAction
import com.naeilo.app.ui.DropMenu
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.FitsOr
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.IosAlert
import com.naeilo.app.ui.LocalTextScale
import com.naeilo.app.ui.LogoTile
import com.naeilo.app.ui.MenuItem
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.NavStack
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Scrub
import com.naeilo.app.ui.Sparkline
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.home.SampleNote
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.plainCombinedClickable
import com.naeilo.app.ui.rememberPrefBool
import com.naeilo.app.ui.rememberPrefString
import com.naeilo.app.ui.stiffness
import com.naeilo.app.core.swFmt
import com.naeilo.app.core.swFmtFrac
import com.naeilo.app.core.usdText
import com.naeilo.app.core.swFmtNoGroup
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/** .animation(.snappy): 0.5초, 튐 0.15 */
private val snappy = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.85f, stiffness = stiffness(0.5f))

/** Swift x.rounded(): 반은 0에서 먼 쪽 */
private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
/** Int(x.rounded()).formatted(): 세 자리 쉼표 정수 */
private fun Double.intFmt(): String = cformat("%,d", this.swRound().roundToLong())

// 종목 탭 (2026-10-23 시안 v16·v22·v33): 맨 위 오늘 박스(오늘 등락이 먼저, 산 가격 대비는 작게) → 지금 구성 그래프 → 미국/국내 묶음 목록
// 오늘 등락은 목록이 이미 쓰는 시세(quote.change)를 모은다. 새 시세 요청 없음
// ios: Views/HoldingsView.swift:7,8,27
@Composable
fun HoldingsView(m: AppModel) {
    var period by remember { mutableStateOf(Period.m1) }        // ios: Views/HoldingsView.swift:9 위 그래프에만 걸린다
    var said by remember { mutableStateOf<String?>(null) }          // ios: Views/HoldingsView.swift:10 지운 뒤 인물의 한 줄 (평정 지수)
    var swiped by remember { mutableStateOf<String?>(null) }        // ios: Views/HoldingsView.swift:11 왼쪽으로 밀어 지우기 버튼이 보이는 종목
    var askDel by remember { mutableStateOf<Symbol?>(null) }        // ios: Views/HoldingsView.swift:12 지울지 한 번 묻는 종목
    var river by remember { mutableStateOf<EarnTarget?>(null) }     // ios: Views/HoldingsView.swift:13 내 몫 실적 강 팝업 (금색 알약을 누르면)
    var editSym by remember { mutableStateOf<Symbol?>(null) }       // ios: Views/HoldingsView.swift:14 길게 누르기 '종목 고치기'
    var adding by remember { mutableStateOf(false) }                // ios: Views/HoldingsView.swift:15 '+ 종목 추가' 팝업
    var inUsd by rememberPrefBool("holdUsd", false)   // ios: Views/HoldingsView.swift:17 미국 종목을 달러로 볼지
    var valueMode by rememberPrefBool("holdValue", false)   // ios: Views/HoldingsView.swift:18 보기: 현재가 | 평가금
    // 보기·통화가 바뀌면 칸 폭을 새로 잰다 (넓어지기만 해서 평가금 폭이 현재가에 남던 것)
    val colW = remember(valueMode, inUsd) { HoldColW() }                              // ios: Views/HoldingsView.swift:16,68 목록 칸 폭: 같은 칸은 묶음을 넘어 줄마다 같은 폭
    var downFirst by rememberPrefBool("holdDown", false)    // ios: Views/HoldingsView.swift:19 정렬: 오른 순(기본) ↔ 내린 순
    val lookRaw = BoxLook.raw   // ios: Views/HoldingsView.swift:20
    val dts = LocalTextScale.current   // ios: Views/HoldingsView.swift:21
    val dark = LocalPal.current.isDark   // ios: Views/HoldingsView.swift:22
    val arrive by rememberPrefString("town.arrive", "")   // ios: Views/HoldingsView.swift:23

    // 방금 더한 종목: 홈 아래 [동네]가 바로 연다
    // ios: Views/HoldingsView.swift:77,78
    LaunchedEffect(arrive) { if (arrive.isNotEmpty()) m.tab = com.naeilo.app.Tab.home }
    // ios: Views/HoldingsView.swift:38,66,70,71,72,78 (종목을 누르면 종목 상세 팝업, 알림으로 온 종목 m.holdOpen 도)
    DetailSheet(m, period, takesHoldOpen = true) {
    NavStack(m.holdPath, { m.holdPath = it }, {
        Refreshable({ Market.shared.pullRefresh(m) }, Modifier.fillMaxSize().background(Theme.bg)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Screen {
                    val rows = m.rows   // ios: Views/HoldingsView.swift:28
                    // 묶음 안은 오늘 등락 순 고정. 같으면 평가액 큰 순
                    // ios: Views/HoldingsView.swift:30
                    val sorted = rows.sortedWith { a, b ->
                        val x = a.sym.quote.change; val y = b.sym.quote.change   // ios: Views/HoldingsView.swift:31
                        if (x == y) b.value.compareTo(a.value) else if (downFirst) x.compareTo(y) else y.compareTo(x)   // ios: Views/HoldingsView.swift:32
                    }
                    val us = sorted.filter { it.sym.currency == Currency.usd }   // ios: Views/HoldingsView.swift:34
                    val kr = sorted.filter { it.sym.currency != Currency.usd }   // ios: Views/HoldingsView.swift:35
                    val mx = rows.maxOfOrNull { abs(move(m, it)) } ?: 0.0   // ios: Views/HoldingsView.swift:36
                    val earn = rows.any { EarningsFeed.shared.summary(it.sym, it.h.qty) != null }   // ios: Views/HoldingsView.swift:37
                    // ios: Views/HoldingsView.swift:39
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.outset(16.dp)) { AppHeader(tab = "종목") }   // ios: Views/HoldingsView.swift:40 (-16)
                        SampleNote(m)   // ios: Views/HoldingsView.swift:41
                        TodayBoxFor(m, rows, lookRaw)   // ios: Views/HoldingsView.swift:42
                        TotalChart(m, rows, period) { period = it }   // ios: Views/HoldingsView.swift:43
                        ListBar(downFirst, { downFirst = !downFirst }, valueMode, { valueMode = it }, inUsd) { inUsd = it }   // ios: Views/HoldingsView.swift:44
                        // ios: Views/HoldingsView.swift:45
                        if (earn) {
                            // ios: Views/HoldingsView.swift:46,51,52
                            Row(Modifier.fillMaxWidth().padding(end = 14.dp).offset(y = (-4).dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(20.dp, 10.dp).background(HoldingsView.gold, CapsuleShape))   // ios: Views/HoldingsView.swift:47,48
                                T("누르면 내 몫", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:49
                            }
                        }
                        // ios: Views/HoldingsView.swift:54
                        if (us.isNotEmpty()) Group(m, "미국", us, mx, inUsd, valueMode, dts.isAccessibilitySize, dark, colW, swiped, { swiped = it }, { river = it }, { editSym = it }) { askDel = it }
                        // ios: Views/HoldingsView.swift:55
                        if (kr.isNotEmpty()) Group(m, "국내", kr, mx, inUsd, valueMode, dts.isAccessibilitySize, dark, colW, swiped, { swiped = it }, { river = it }, { editSym = it }) { askDel = it }
                        // 맨 아래 한 줄: 왼쪽 시세 갱신, 오른쪽 '+ 종목 추가'
                        // ios: Views/HoldingsView.swift:57,58,59,60,61,62,63,64,65,66,67,68
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FitText(Market.shared.asOfText, appFont(13f), 0.8f, Modifier.weight(1f), maxLines = 2, color = Theme.sub2)
                            Box(Modifier.plainClickable { adding = true }.defaultMinSize(minHeight = 32.dp).border(1.5.dp, Theme.teal, CapsuleShape).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                T("+ 종목 추가", style = appFont(13f, FontWeight.Bold), color = Theme.teal, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(24.dp))   // ios: Views/HoldingsView.swift:64 아래 탭바가 마지막 카드를 가리지 않게
                    }
                }
            }
        }
    }) { r ->
        // ios: Views/HoldingsView.swift:73
        if (r is Symbol) NavPage(r.name) { HoldingDetailView(m, sym = r, period0 = period) }
        else if (r is EditRoute) NavPage(r.sym.name) { HoldingEditView(m, sym = r.sym) { m.holdPath = m.holdPath.dropLast(1) } }   // 고치기 (길게 누르기·세부)
        else NavPage { AddHoldingView(m) { m.holdPath = m.holdPath.dropLast(1) } }
    }
    }
    // 길게 누르기 '종목 고치기'도 팝업
    // ios: Views/HoldingsView.swift:79
    com.naeilo.app.PathSheet(listOfNotNull(editSym), { if (it.isEmpty()) editSym = null }) { s -> NavPage(s.name) { HoldingEditView(m, sym = s) { editSym = null } } }
    // ios: Views/HoldingsView.swift:76 ('+ 종목 추가' 팝업: 머리 왼쪽 제목, 오른쪽 x)
    Sheet(adding, onDismiss = { adding = false }) { AddHoldingView(m, sheet = true) { adding = false } }
    // ios: Views/HoldingsView.swift:79,80,81
    IosAlert(said != null, "내 투자 습관", said, listOf(DialogAction("확인", onClick = {}))) { said = null }
    EarningsRiverSheet(river) { river = null }   // ios: Views/HoldingsView.swift:75
    // ios: Views/HoldingsView.swift:83,84,85,89,92
    ConfirmSheet(askDel != null, "${askDel?.name ?: ""} 종목을 지울까요?", "목록과 평가액에서 빠져요. 다시 넣으려면 종목 추가에서 넣으면 돼요.",
        listOf(DialogAction("지우기", destructive = true, onClick = {
            val sym = askDel ?: return@DialogAction
            val avg = m.holdings.firstOrNull { it.symbol == sym.id }?.avg ?: sym.close
            swiped = null   // Views/HoldingsView.swift:86 (검사 예외) 밀린 줄은 snappy 스프링으로 돌아간다 (Group 의 offset)
            // 세부정보의 지우기와 같이: 판 것으로 평정 지수에 남긴다
            said = HoldingEdit.save(m, sym, 0.0, avg, buy = false)   // ios: Views/HoldingsView.swift:88
            askDel = null
        })), cancel = "취소", onDismiss = { askDel = null; swiped = null })   // Views/HoldingsView.swift:91 (검사 예외)
}

// ios: Views/HoldingsView.swift:7 (struct HoldingsView 의 static)
object HoldingsView {
    val periods: List<Period> = listOf(Period.m1, Period.m3, Period.m6, Period.y1, Period.y3)   // ios: Views/HoldingsView.swift:24
    val gold = hex(0xF2C14E)   // ios: Views/HoldingsView.swift:25
}

/** 종목 하나가 오늘 움직인 금액 (원). 국내 주식 합계는 넣은 날만 바뀌어 뺀다 */
// ios: Views/HoldingsView.swift:96
private fun move(m: AppModel, r: AppModel.Row): Double {
    val q = r.sym.quote   // ios: Views/HoldingsView.swift:97
    if (r.id == KoreaEstimate.sumId || q.prevClose <= 0) return 0.0   // ios: Views/HoldingsView.swift:98
    return r.value - m.krw(r.sym, r.h.qty * q.prevClose)   // ios: Views/HoldingsView.swift:99
}

// ios: Views/HoldingsView.swift:102
@Composable
private fun TodayBoxFor(m: AppModel, rows: List<AppModel.Row>, lookRaw: String) {
    val st = rows.filter { it.id != KoreaEstimate.sumId }   // ios: Views/HoldingsView.swift:103
    val mv = st.fold(0.0) { a, r -> a + move(m, r) }   // ios: Views/HoldingsView.swift:104
    val w = { f: (Double) -> Boolean -> st.filter { f(move(m, it)) }.fold(0.0) { a, r -> a + r.value } }   // ios: Views/HoldingsView.swift:105
    val gain = m.total - m.cost   // ios: Views/HoldingsView.swift:106
    // ios: Views/HoldingsView.swift:107,108
    // ios: Views/HoldingsView.swift:396 (.boxScheme())
    BoxSchemeScope {
        TodayBox(look = BoxLook.entries.firstOrNull { it.name == lookRaw } ?: BoxLook.jump, move = mv, pct = m.todayMove,
            parts = listOf(w { it > 0 }, w { it == 0.0 }, w { it < 0 }), gain = gain, ret = m.ret)
    }
}

/** 위 그래프의 숫자: 기간·보유·시세가 바뀔 때만 다시 센다 (그리는 중엔 계산하지 않는다) */
private class Total(val tot: List<Double>, val first: Double, val last: Double, val noHist: List<String>)

// 지금 비중을 기간 전부터 가졌다면: 백테스트 평가액. 축에는 시작 금액·달라진 금액 꼬리표만 (시안 v22)
// ios: Views/HoldingsView.swift:112
@Composable
private fun TotalChart(m: AppModel, rows: List<AppModel.Row>, period: Period, setPeriod: (Period) -> Unit) {
    val stamp = Market.shared.updatedAt
    val t = remember(period, rows, stamp) {
        val series = rows.map { r -> m.prices.series(r.sym, period).map { m.krw(r.sym, it * r.h.qty) } }   // ios: Views/HoldingsView.swift:113
        val n = series.maxOfOrNull { it.size } ?: 0   // ios: Views/HoldingsView.swift:114 짧으면 첫 값으로 평평하게
        val tot = (0 until n).map { i -> series.fold(0.0) { a, s -> if (s.isEmpty()) a else a + s[maxOf(0, s.size - n + i)] } }   // ios: Views/HoldingsView.swift:115 끝(오늘)을 맞춰 더한다
        val noHist = rows.filter { !m.prices.hasHistory(it.sym) }.map { it.sym.name }   // ios: Views/HoldingsView.swift:125
        Total(tot, tot.firstOrNull() ?: 0.0, tot.lastOrNull() ?: 0.0, noHist)
    }
    val first = t.tot.firstOrNull() ?: 0.0; val last = t.tot.lastOrNull() ?: 0.0; val up = last >= first   // ios: Views/HoldingsView.swift:116
    val start = LocalDate.now().minusDays((period.span * 1095).toLong())   // ios: Views/HoldingsView.swift:117
    val d0 = if (period.span < 1) "${start.monthValue}월" else "${start.year}.${start.monthValue}"   // ios: Views/HoldingsView.swift:118
    // ios: Views/HoldingsView.swift:119
    Card {
        // ios: Views/HoldingsView.swift:120,121,122,123
        TagChart(points = t.tot, height = 120.dp, tags = listOf(
            TagChart.Tag(at = first, text = HoldFmt.won(first), bg = TagChart.gray),
            TagChart.Tag(at = last, text = (if (up) "+" else "−") + HoldFmt.won(abs(last - first)), bg = if (up) hex(0xFF8A80) else hex(0x8CC4F2)),
        ), left = d0, right = "오늘", scrub = Scrub.period(period) { AppModel.man(it) + tr("원") })
        ChipRow(items = HoldingsView.periods.map { it to it.label }, selection = period, onSelect = setPeriod, fill = true)   // ios: Views/HoldingsView.swift:124
        // ios: Views/HoldingsView.swift:126,128 (127 은 검사 예외: 같은 글)
        if (t.noHist.isNotEmpty()) T("과거 시세 없음: ${t.noHist.joinToString(", ")} · 평평하게 그림", style = appFont(12f), color = Theme.muted)
    }
}

// 목록 위 한 줄: 정렬 뒤집기 · 현재가|평가금 · $|원
// ios: Views/HoldingsView.swift:134
@Composable
private fun ListBar(downFirst: Boolean, flip: () -> Unit, valueMode: Boolean, setValue: (Boolean) -> Unit, inUsd: Boolean, setUsd: (Boolean) -> Unit) {
    // ios: Views/HoldingsView.swift:135
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/HoldingsView.swift:136,137,138,140
        Box(Modifier.plainClickable(flip).defaultMinSize(minHeight = 32.dp), contentAlignment = Alignment.CenterStart) {
            T(if (downFirst) "내린 순 ⇅" else "오른 순 ⇅", style = appFont(13f, FontWeight.Bold), color = Theme.teal)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))   // ios: Views/HoldingsView.swift:141
        // ios: Views/HoldingsView.swift:142
        HoldSeg(listOf(false to "현재가", true to "평가금"), valueMode, setValue, label = "보기: ${if (valueMode) tr("평가금") else tr("현재가")}")
        // ios: Views/HoldingsView.swift:144
        HoldSeg(listOf(true to "$", false to "원"), inUsd, setUsd, label = "금액 단위: ${if (inUsd) tr("달러") else tr("원")}")
    }
}

// ios: Views/HoldingsView.swift:149
private fun usd(inUsd: Boolean, r: AppModel.Row): Boolean = inUsd && r.sym.currency == Currency.usd
/** 지금 가격·평균 단가: 미국 종목은 $ 보기면 달러, 원 보기면 원으로 */
// ios: Views/HoldingsView.swift:151
private fun px(inUsd: Boolean, r: AppModel.Row, v: Double): String {
    if (r.sym.currency != Currency.usd) return v.intFmt() + tr("원")   // ios: Views/HoldingsView.swift:152
    return if (usd(inUsd, r)) "$" + v.usdText() else (v * Market.shared.valFx).intFmt() + tr("원")   // ios: Views/HoldingsView.swift:153
}
/** 평가금·변동 금액 (부호 없음): $ 보기면 달러 정수, 아니면 만원 (만원 미만은 원) */
// ios: Views/HoldingsView.swift:156,157
private fun amt(inUsd: Boolean, r: AppModel.Row, krw: Double, native: Double): String =
    if (usd(inUsd, r)) "$" + abs(native).intFmt() else HoldFmt.won(abs(krw))

// ios: Views/HoldingsView.swift:160
@Composable
private fun Group(m: AppModel, title: String, rs: List<AppModel.Row>, mx: Double, inUsd: Boolean, valueMode: Boolean, big: Boolean, dark: Boolean, colW: HoldColW,
                  swiped: String?, setSwiped: (String?) -> Unit, openRiver: (EarnTarget) -> Unit, edit: (Symbol) -> Unit, askDel: (Symbol) -> Unit) {
    val mv = rs.fold(0.0) { a, r -> a + move(m, r) }; val w = rs.fold(0.0) { a, r -> a + r.value }   // ios: Views/HoldingsView.swift:161
    val pc = if (w - mv > 0) mv / (w - mv) else 0.0   // ios: Views/HoldingsView.swift:162
    val reduce = LocalReduceMotion.current
    // ios: Views/HoldingsView.swift:163,211,212,213
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // 머리줄: 묶음의 오늘 등락
        // ios: Views/HoldingsView.swift:165,173
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("$title ${rs.size}", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:166,167
            val c = if (mv > 0) Theme.up else if (mv < 0) Theme.down else Theme.sub   // ios: Views/HoldingsView.swift:171
            // ios: Views/HoldingsView.swift:168,169,170
            TA(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = fx(9f), fontWeight = FontWeight.Bold)) { append(if (mv > 0) "▲ " else if (mv < 0) "▼ " else "") }
                append(HoldFmt.won(abs(mv)) + "  " + AppModel.sgn(pc))
            }, style = appFont(14f, FontWeight.SemiBold).tnum(), color = c, maxLines = 1)
        }
        // 자리마다 줄 하나: 순위가 바뀐 자리만 전광판처럼 넘어간다
        // ios: Views/HoldingsView.swift:175
        rs.forEachIndexed { i, r ->
            HDivider(Theme.line)   // ios: Views/HoldingsView.swift:176
            // ios: Views/HoldingsView.swift:181,201 밀린 줄은 snappy 스프링으로 움직인다 (지운 뒤 돌아갈 때도)
            val x by animateDpAsState(if (swiped == r.id) (-84).dp else 0.dp, snappy, label = "swipe")
            var menu by remember { mutableStateOf(false) }
            val open = openDetail(m)   // ios: Views/HoldingsView.swift:277 (DetailLink)
            // ios: Views/HoldingsView.swift:180 (자리 i 는 그대로, 종목이 바뀌면 그 줄만)
            key(i) {
                // ios: Views/HoldingsView.swift:180,183,197
                Box(Modifier.fillMaxWidth().clip(androidx.compose.ui.graphics.RectangleShape).flipIn(r.id, i * 0.06, reduce)) {
                    // 왼쪽으로 밀면 뒤에 숨은 지우기 버튼이 보인다
                    // ios: Views/HoldingsView.swift:184,185,186,190,191,193,194
                    if (swiped == r.id) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        Column(
                            Modifier.width(84.dp).fillMaxHeight()
                                .background(Theme.up).plainClickable { askDel(r.sym) }.semantics(mergeDescendants = true) { contentDescription = tr("${r.sym.name} 지우기") },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Sym("trash", 18f, Color.White, scaled = false)   // ios: Views/HoldingsView.swift:187
                            T("지우기", style = appFont(12f, FontWeight.Bold), color = Color.White)   // ios: Views/HoldingsView.swift:188
                        }
                    }
                    // ios: Views/HoldingsView.swift:177,178,179
                    Box(
                        Modifier.offset(x = x).background(Theme.card).drawBehind { tint(m, r, mx, dark) }
                            // ios: Views/HoldingsView.swift:198,199,200,201 (24 넘게 끌고, 가로가 세로의 1.5배 넘을 때만. 40 넘게 왼쪽이면 버튼, 아니면 닫기)
                            .pointerInput(r.id) {
                                awaitEachGesture {
                                    val d = awaitFirstDown(requireUnconsumed = false)
                                    var dx = 0f; var dy = 0f; var went = false
                                    while (true) {
                                        val e = awaitPointerEvent(); val ch = e.changes.firstOrNull { it.id == d.id } ?: break
                                        if (!ch.pressed) break
                                        val dd = ch.position - ch.previousPosition; dx += dd.x; dy += dd.y
                                        if (!went && abs(dx) + abs(dy) >= 24.dp.toPx()) went = true
                                        if (went && abs(dx) > abs(dy) * 1.5f) ch.consume()   // 가로로 미는 중엔 위아래 스크롤을 막는다
                                    }
                                    if (went && abs(dx) > abs(dy) * 1.5f) setSwiped(if (dx < -40.dp.toPx()) r.id else null)
                                }
                            }
                            // ios: Views/HoldingsView.swift:203 (길게 누르면 메뉴)
                            .pointerInput(r.id) { detectTapGestures(onLongPress = { menu = true }) }
                            // ios: Views/HoldingsView.swift:207,208
                            .semantics { customActions = listOf(CustomAccessibilityAction(tr("종목 고치기")) { edit(r.sym); true }, CustomAccessibilityAction(tr("지우기")) { askDel(r.sym); true }) },
                    ) { HoldRow(m, r, inUsd, valueMode, big, colW, onOpen = { if (swiped != null) setSwiped(null) else open(r.sym) }, openRiver = openRiver) }
                    // ios: Views/HoldingsView.swift:204,205
                    ContextMenu(menu, listOf(MenuItem("종목 고치기", icon = "pencil", onClick = { edit(r.sym) }), MenuItem("이 종목 지우기", icon = "trash", destructive = true, onClick = { askDel(r.sym) }))) { menu = false }
                }
            }
        }
    }
}

/** 줄 배경: 왼쪽부터 오늘 움직인 금액 몫만큼 옅게 (오른 산호 · 그대로 회색 · 내린 하늘). 상한은 카드 폭의 34% (보기를 바꿔도 같다) */
// ios: Views/HoldingsView.swift:217
private fun androidx.compose.ui.graphics.drawscope.DrawScope.tint(m: AppModel, r: AppModel.Row, mx: Double, dark: Boolean) {
    val v = move(m, r)   // ios: Views/HoldingsView.swift:218
    val f = if (v != 0.0 && mx > 0) max(0.12, abs(v) / mx) else 0.12   // ios: Views/HoldingsView.swift:219
    val c = if (v > 0) hex(0xFF8A80) else if (v < 0) hex(0x8CC4F2) else if (dark) hex(0x4A5672) else hex(0xAAB2BE)   // ios: Views/HoldingsView.swift:220
    // ios: Views/HoldingsView.swift:221,222,224 (읽어 주기는 그림이라 없다)
    drawRect(c.copy(alpha = if (dark) 0.26f else 0.32f), Offset.Zero, Size(size.width * 0.34f * f.toFloat(), size.height))
}

// 행 (시안 v33): 로고(실적 금색 점은 오른쪽 위) · 이름 · 평단가(평가금 보기는 수량) · 오늘 %(평가금 보기는 산 가격 대비 %와 금액) · 현재가(평가금)
// 로고·이름·평단가·%는 세부로, 금색 알약만 내 몫 실적 강 팝업으로 (실적이 없으면 알약 없이 세부로)
// ios: Views/HoldingsView.swift:229
@Composable
private fun HoldRow(m: AppModel, r: AppModel.Row, inUsd: Boolean, v: Boolean, big: Boolean, colW: HoldColW, onOpen: () -> Unit, openRiver: (EarnTarget) -> Unit) {
    val es = EarningsFeed.shared.summary(r.sym, r.h.qty)   // ios: Views/HoldingsView.swift:230
    val day = r.sym.quote.change   // ios: Views/HoldingsView.swift:231
    val bp = if (r.cost > 0) r.value / r.cost - 1 else 0.0   // ios: Views/HoldingsView.swift:232
    val small = if (v) 13f else 14f   // ios: Views/HoldingsView.swift:233
    // ios: Views/HoldingsView.swift:234
    @Composable fun Name(mod: Modifier) = Row(mod, horizontalArrangement = Arrangement.spacedBy(if (v) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/HoldingsView.swift:235,236
        Box {
            LogoTile(symbol = r.id, size = 28.dp)
            // ios: Views/HoldingsView.swift:237,238,239,240
            if (es?.fresh == true) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(9.dp, 9.dp).background(Theme.card, CircleShape).padding(2.dp).background(HoldingsView.gold, CircleShape)
                .semantics { contentDescription = tr("오늘 실적") })
        }
        T(r.sym.name, Modifier.weight(1f, fill = false), style = appFont(15f, FontWeight.Bold), maxLines = 1)   // ios: Views/HoldingsView.swift:243
    }
    // ios: Views/HoldingsView.swift:245
    @Composable fun Mid(mod: Modifier) = T(if (v) "${r.h.qty.swFmt()}주" else px(inUsd, r, r.h.avg), mod, style = appFont(12f).tnum(), color = Theme.sub, maxLines = 1)
    val pctC = if (v) (if (bp > 0) Theme.up else if (bp < 0) Theme.down else Theme.sub) else (if (day > 0) Theme.up else if (day < 0) Theme.down else Theme.sub)   // ios: Views/HoldingsView.swift:248
    // ios: Views/HoldingsView.swift:246,247,249
    @Composable fun Pct() = TA(
        if (v) buildAnnotatedString {
            append(AppModel.sgn(bp))
            withStyle(SpanStyle(fontSize = fx(10.5f), fontWeight = FontWeight.SemiBold)) { append("(" + amt(inUsd, r, krw = r.value - r.cost, native = r.h.qty * (r.sym.last - r.h.avg)) + ")") }
        } else AnnotatedString(AppModel.sgn2(day)),
        Modifier.holdCol(1, colW), style = appFont(if (v) 12.5f else 14f, FontWeight.Bold).tnum(), color = pctC, maxLines = 1)
    // ios: Views/HoldingsView.swift:250,251,252,253,254,255
    @Composable fun Pill(mod: Modifier = Modifier) = Box(mod.holdCol(2, colW), contentAlignment = Alignment.CenterEnd) {
        T(if (v) amt(inUsd, r, krw = r.value, native = r.h.qty * r.sym.last) else px(inUsd, r, r.sym.last),
            Modifier.background(if (es != null) HoldingsView.gold else Color.Transparent, CapsuleShape).padding(horizontal = if (v) 6.dp else 7.dp, vertical = 2.dp),
            style = appFont(small, FontWeight.Bold).tnum(), color = if (es != null) Color.Black else Theme.ink, maxLines = 1)
    }
    // 공개 베타·출시에서 내 키 시세가 없는 실제 종목: 가격·% 대신 키 안내 (평가액에서도 빠진다, Market.needsKey)
    val nk = Market.shared.needsKey(r.sym)   // ios: Views/HoldingsView.swift:257
    // 실적이 있으면 알약만 따로 누른다 (없으면 알약도 세부 링크 안)
    // ios: Views/HoldingsView.swift:259,260,261,262
    @Composable fun Right() {
        if (nk) T("키를 연결하면 시세가 보여요", style = appFont(12f), color = Theme.sub, maxLines = 1)
        else Row(horizontalArrangement = Arrangement.spacedBy(if (v) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) { Pct(); if (es == null) Pill() }
    }
    // ios: Views/HoldingsView.swift:263,264,265,266
    @Composable fun Earn() {
        if (es != null && !nk) Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable { openRiver(EarnTarget(r.sym, r.h.qty)) }
            .semantics { onClick(label = tr("내 몫 실적 보기")) { openRiver(EarnTarget(r.sym, r.h.qty)); true } }, contentAlignment = Alignment.Center) { Pill() }
    }
    // ios: Views/HoldingsView.swift:264,288,289
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(start = 12.dp, end = if (v) 10.dp else 12.dp), contentAlignment = Alignment.CenterStart) {
        // ios: Views/HoldingsView.swift:265
        if (big) {
            // ios: Views/HoldingsView.swift:266,267,274
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Name(Modifier.fillMaxWidth().plainClickable(onOpen))
                // ios: Views/HoldingsView.swift:268,269,270,271,272
                Row(horizontalArrangement = Arrangement.spacedBy(if (v) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).defaultMinSize(minHeight = 44.dp).plainClickable(onOpen), verticalAlignment = Alignment.CenterVertically) { Mid(Modifier); Spacer(Modifier.weight(1f)); Right() }
                    Earn()
                }
            }
        } else {   // ios: Views/HoldingsView.swift:275
            // ios: Views/HoldingsView.swift:276
            Row(horizontalArrangement = Arrangement.spacedBy(if (v) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/HoldingsView.swift:277,278,282,283
                Row(Modifier.weight(1f).defaultMinSize(minHeight = 52.dp).plainClickable(onOpen), horizontalArrangement = Arrangement.spacedBy(if (v) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 이름이 남은 폭을 다 먼저 쓴다 (Spacer 에 weight 를 주면 이름이 반으로 잘린다)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(0.dp)) { Name(Modifier.weight(1f, fill = false)); Spacer(Modifier.width(4.dp)) }   // ios: Views/HoldingsView.swift:279
                    if (!nk) Mid(Modifier.holdCol(0, colW))   // ios: Views/HoldingsView.swift:286
                    Right()   // ios: Views/HoldingsView.swift:281
                }
                Earn()   // ios: Views/HoldingsView.swift:284
            }
        }
    }
}

/** .monospacedDigit(): 숫자 칸 너비를 같게 */
private fun androidx.compose.ui.text.TextStyle.tnum() = copy(fontFeatureSettings = "tnum")

/** .font(.system(size:)): 글자 크기 키우기를 따르지 않는 고정 크기 */
@Composable
private fun fx(size: Float): androidx.compose.ui.unit.TextUnit = (size / LocalDensity.current.fontScale).sp

/** 종목 탭 금액 글: 만원 미만은 원 단위 (예: 1,165원), 그 위는 만원 (AppModel.man) */
// ios: Views/HoldingsView.swift:294
object HoldFmt {
    // ios: Views/HoldingsView.swift:295
    fun won(krw: Double): String {
        if (AppModel.en || abs(krw) >= 1e4) return AppModel.man(krw)   // ios: Views/HoldingsView.swift:296
        return (if (krw < 0) "−" else "") + abs(krw).intFmt() + tr("원")   // ios: Views/HoldingsView.swift:297
    }
}

/** 목록 칸 폭 모으기: 칸 번호 → 가장 넓은 글자 폭(px). 묶음을 넘어 줄마다 같은 폭 */
// ios: Views/HoldingsView.swift:302
class HoldColW {
    val w = androidx.compose.runtime.mutableStateMapOf<Int, Int>().apply { putAll(defaultValue) }
    companion object { val defaultValue: Map<Int, Int> = emptyMap() }   // ios: Views/HoldingsView.swift:303
    // ios: Views/HoldingsView.swift:304,305
    fun reduce(k: Int, v: Int) { if (v > (w[k] ?: 0)) w[k] = v }
}

/** 글자 폭을 재어 모으고, 같은 칸 가장 넓은 폭에 오른쪽 끝을 맞춘다 */
// ios: Views/HoldingsView.swift:309,311,312,313,314
private fun Modifier.holdCol(k: Int, cw: HoldColW): Modifier = this.layout { mm, c ->
    val p = mm.measure(c.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
    cw.reduce(k, p.width)
    val w = max(p.width, cw.w[k] ?: 0)
    layout(w, p.height) { p.place(w - p.width, 0) }
}

/** 공항 전광판처럼: 이 자리 종목이 바뀌면 그 줄만 한 번 넘어간다 (동작 줄이기면 그대로) */
// ios: Views/HoldingsView.swift:319,320,321,322,323
@Composable
private fun Modifier.flipIn(key: String, delay: Double, still: Boolean): Modifier {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    var last by remember { mutableStateOf(key) }
    // ios: Views/HoldingsView.swift:324,326,327,328,329 (90° 에서 delay 만큼 기다렸다 snappy 0.35초로 0°)
    LaunchedEffect(key, still) {
        if (still || key == last) return@LaunchedEffect
        last = key
        a.snapTo(90f)
        kotlinx.coroutines.delay((max(0.001, delay) * 1000).toLong())
        a.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = stiffness(0.35f)))
    }
    val axis = Triple(1f, 0f, 0f); val perspective = 0.6f   // ios: Views/HoldingsView.swift:325
    return this.graphicsLayer { rotationX = a.value * axis.first; rotationY = a.value * axis.second; rotationZ = a.value * axis.third; cameraDistance = 8 * density / perspective }
}

/** 작은 둘 고르기 (현재가|평가금, $|원) */
// ios: Views/HoldingsView.swift:335,336,337,338
@Composable
fun <K> HoldSeg(items: List<Pair<K, String>>, selection: K, set: (K) -> Unit, label: String) {
    // ios: Views/HoldingsView.swift:339,351,352,353,354,355
    Row(Modifier.border(1.dp, Theme.border, CapsuleShape).padding(2.dp)
        .clearAndSetSemantics { contentDescription = tr(label); onClick { val i = items.indexOfFirst { it.first == selection }; if (i >= 0) set(items[(i + 1) % items.size].first); true } },
        horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        // ios: Views/HoldingsView.swift:340
        items.forEach { item ->
            val v = item.first   // ios: Views/HoldingsView.swift:341
            // ios: Views/HoldingsView.swift:342,343,344,345,346,347,348
            Box(Modifier.plainClickable { set(v) }.defaultMinSize(minWidth = 32.dp, minHeight = 28.dp).background(if (selection == v) Theme.teal else Color.Transparent, CapsuleShape).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                T(item.second, style = appFont(12f, FontWeight.Bold), color = if (selection == v) Color.White else Theme.sub, maxLines = 1)
            }
        }
    }
}

// 오늘 박스 (시안 v16)

/** 종목 탭 맨 위: 홈에서 고른 평가액 박스 모양의 바탕·색을 따른다. 박스는 홈처럼 늘 어두운 장면.
 *  큰 줄 = 오늘 등락 합계, 등락 띠 = 오른 종목·그대로·내린 종목의 평가액 몫(글·숫자 없음), 맨 아래 작은 줄 = 산 가격 대비 */
// ios: Views/HoldingsView.swift:364
@Composable
fun TodayBox(
    look: BoxLook,   // ios: Views/HoldingsView.swift:365
    move: Double, pct: Double,      // ios: Views/HoldingsView.swift:366 오늘 등락 (원, 비율)
    parts: List<Double>,            // ios: Views/HoldingsView.swift:367 등락 띠: 오른 · 그대로 · 내린 종목 평가액
    gain: Double, ret: Double,      // ios: Views/HoldingsView.swift:368 산 가격 대비 (원, 비율)
) {
    /** 띠 몫 (0~1, 합 1). 종목이 없으면 모두 0 */
    // ios: Views/HoldingsView.swift:372
    val shares: List<Double> = run {
        val s = parts.fold(0.0) { a, x -> a + x }   // ios: Views/HoldingsView.swift:373
        if (s > 0) parts.map { it / s } else listOf(0.0, 0.0, 0.0)   // ios: Views/HoldingsView.swift:374
    }
    val c = if (move > 0) TodayBox.upC else if (move < 0) TodayBox.downC else TodayBox.dim   // ios: Views/HoldingsView.swift:378
    val tri = if (move > 0) "▲ " else if (move < 0) "▼ " else ""   // ios: Views/HoldingsView.swift:379
    // ios: Views/HoldingsView.swift:377,380,404,405,406,407,408
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).moodBackdrop(look)   // ios: Views/HoldingsView.swift:393
            .clearAndSetSemantics { contentDescription = tr("오늘 ${if (move >= 0) "+" else "−"}${TodayBox.won(abs(move))}, ${AppModel.sgn(pct)}. 산 가격 대비 ${AppModel.sgn(ret)}") }
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = if (look == BoxLook.jump) 0.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ios: Views/HoldingsView.swift:381
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("오늘", Modifier.weight(1f), style = appFont(13f), color = TodayBox.dim); com.naeilo.app.ui.home.FreshLabel() }
        // ios: Views/HoldingsView.swift:382
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // ios: Views/HoldingsView.swift:383,384,385 (삼각형은 숫자 60% 크기, 기준선 4 위)
            FitAnnotated(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = fx(18f), fontWeight = FontWeight.ExtraBold, baselineShift = androidx.compose.ui.text.style.BaselineShift(4f / 18f))) { append(tri) }
                append(TodayBox.won(abs(move)))
            }, TextStyle(fontSize = fx(30f), fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), 0.5f, c, Modifier.weight(1f, fill = false).alignByBaseline(), roll = move, maxLines = 1)
            T(AppModel.sgn(pct), Modifier.alignByBaseline(), style = TextStyle(fontSize = fx(16f), fontWeight = FontWeight.SemiBold), color = c, maxLines = 1)   // ios: Views/HoldingsView.swift:386
        }
        // 점프 직전: 도로는 박스 맨 아래에 붙인다
        if (look == BoxLook.jump) { Foot(gain, ret); MoodBand(look, shares, up = move >= 0) }   // ios: Views/HoldingsView.swift:388
        else { MoodBand(look, shares, up = move >= 0); Foot(gain, ret) }   // ios: Views/HoldingsView.swift:389
    }
}

/** 몫 띠: 홈에서 고른 박스 모양대로 그린다 (종목 오늘 박스·흐름 큰 흐름 박스가 같이 쓴다). 몫은 위·그대로·아래 순서, 합 1.
 *  띠 위 오른쪽 끝에 테마의 작은 인물이 선다 (up = 오른 날). 색은 라이트·다크 한 쌍 */
// ios: Views/HoldingsView.swift:412
@Composable
fun MoodBand(
    look: BoxLook, shares: List<Double>,
    road: Dp = 18.dp,   // ios: Views/HoldingsView.swift:415 점프 직전 도로 위아래 여백 (흐름 박스는 버튼이 아래에 붙어 좁게)
    up: Boolean = true,   // ios: Views/HoldingsView.swift:416
) {
    val ink = boxInk(); val dk = ink.dk
    // ios: Views/HoldingsView.swift:417
    val colors = listOf(ink.upF, if (look == BoxLook.pino) ThemeBand.woodFlat(dk) else if (look == BoxLook.horizon) bx(dk, 0xAEB7C4, 0x4A5672) else ink.flatF, ink.downF)
    // ios: Views/HoldingsView.swift:419,420,421,422,423,424 (띠 위 오른쪽 끝 = 띠 위 2pt)
    Box(Modifier.fillMaxWidth()) {
        when (look) {
            BoxLook.jump -> Lane(shares, listOf(ink.upF, bx(dk, 0x5A6585, 0x4A5672), ink.downF), road, dk)   // ios: Views/HoldingsView.swift:429
            BoxLook.horizon -> Wave(shares, colors, Modifier.fillMaxWidth().height(12.dp))   // ios: Views/HoldingsView.swift:430
            // ios: Views/HoldingsView.swift:431,432,433,434
            BoxLook.tub -> Box(Modifier.fillMaxWidth().background(bx(dk, 0xFFFFFF, 0xEEF2F8), RoundedCornerShape(8.dp)).border(1.dp, bx(dk, 0xB7CAE3, 0xB7CAE3, 1f, 0f), RoundedCornerShape(8.dp)).padding(4.dp)) { Band(shares, colors, 8.dp, 4.dp) }
            // ios: Views/HoldingsView.swift:435,436
            BoxLook.bar -> Box(Modifier.fillMaxWidth().clip(CapsuleShape).background(bx(dk, 0xE6E9EE, 0x2E3542), CapsuleShape)) { Band(shares, colors, 10.dp, 5.dp) }
            BoxLook.dory, BoxLook.pino, BoxLook.mug -> ThemeBandFrame(look) { h, rad -> Band(shares, colors, h, rad) }   // ios: Views/HoldingsView.swift:437,438,439,440,441,442,443,444,445,446,447,448,449
        }
        BandBuddy(look, up, Modifier.align(Alignment.TopEnd).padding(end = if (look == BoxLook.jump) 18.dp else 0.dp).aboveTop(2.dp))
    }
}

/** .alignmentGuide(.top) { $0[.bottom] + 2 }: 아래 끝을 부모 위 끝보다 gap 위에 둔다 (자리는 차지하지 않는다) */
private fun Modifier.aboveTop(gap: Dp): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, 0) { p.place(0, -p.height - gap.roundToPx()) }
}

/** 박스 바탕: 홈에서 고른 박스 모양의 장면 (늘 어둡다) */
// ios: Views/HoldingsView.swift:495
@Composable
fun Modifier.moodBackdrop(look: BoxLook): Modifier { val dk = boxDark(); return drawBehind { backdrop(look, dk) } }

// ios: Views/HoldingsView.swift:364 (struct TodayBox 의 static)
object TodayBox {
    // ios: Views/HoldingsView.swift:369 (BoxInk: 라이트·다크 한 쌍)
    val upC @Composable get() = boxInk().up; val downC @Composable get() = boxInk().down
    val flatC @Composable get() = boxInk().flatF; val dim @Composable get() = boxInk().dim
    /** 449만 원 · 만원 미만은 3,200원 */
    // ios: Views/HoldingsView.swift:413,414
    fun won(v: Double): String = if (AppModel.en || abs(v) < 1e4) HoldFmt.won(v) else AppModel.man(v) + tr(" 원")
    /** 차선 칸 n 개를 오른·내린·그대로 칸 수로 (반올림, 넘치면 큰 쪽을 깎는다). (up, dn, flat) */
    fun lane(s: List<Double>, n: Int): Triple<Int, Int, Int> {
        var up = (s[0] * n).swRound().toInt(); var dn = (s[2] * n).swRound().toInt()   // ios: Views/HoldingsView.swift:445
        if (up + dn > n) { if (up >= dn) up = n - dn else dn = n - up }   // ios: Views/HoldingsView.swift:446
        val flat = n - up - dn   // ios: Views/HoldingsView.swift:447
        return Triple(up, dn, flat)
    }
}

/** 줄 글 한 줄 FitText (꾸민 글): .lineLimit(1).minimumScaleFactor(k) */
@Composable
internal fun FitAnnotated(text: AnnotatedString, style: TextStyle, minScale: Float, color: Color, modifier: Modifier = Modifier, roll: Any? = null, maxLines: Int = 1) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val t = tr(text)
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val w = measurer.measure(t, style, maxLines = 1, softWrap = false).size.width
        val k = if (c.hasBoundedWidth && w > c.maxWidth) maxOf(minScale, c.maxWidth.toFloat() / w) else 1f
        val st = if (k < 1f) style.copy(fontSize = style.fontSize * k, letterSpacing = style.letterSpacing * k) else style
        val p = subcompose(k) { com.naeilo.app.ui.TA(t, style = st, color = color, maxLines = maxLines) }.first().measure(c.copy(minWidth = 0))
        val b = p[androidx.compose.ui.layout.FirstBaseline]
        layout(p.width, p.height, if (b != androidx.compose.ui.layout.AlignmentLine.Unspecified) mapOf(androidx.compose.ui.layout.FirstBaseline to b) else emptyMap()) { p.place(0, 0) }
    }
}

// ios: Views/HoldingsView.swift:417
@Composable
private fun Foot(gain: Double, ret: Double) {
    // ios: Views/HoldingsView.swift:418,420
    FitAnnotated(buildAnnotatedString {
        withStyle(SpanStyle(color = TodayBox.dim)) { append(tr("산 가격 대비 ")) }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (gain >= 0) TodayBox.upC else TodayBox.downC)) { append("${if (gain >= 0) "+" else "−"}${HoldFmt.won(abs(gain))} · ${AppModel.sgn(ret)}") }
    }, TextStyle(fontSize = fx(13f), fontFeatureSettings = "tnum"), 0.7f, TodayBox.dim, maxLines = 1)
}

/** 띠 한 줄 (욕조·진행 막대): 몫만큼 나란히, 사이 2pt */
// ios: Views/HoldingsView.swift:424,425,434,435
@Composable
private fun Band(shares: List<Double>, colors: List<Color>, height: Dp, radius: Dp) {
    Canvas(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(radius))) {
        val on = shares.indices.filter { shares[it] > 0 }   // ios: Views/HoldingsView.swift:426
        val gap = 2.dp.toPx(); val room = size.width - max(0, on.size - 1) * gap   // ios: Views/HoldingsView.swift:427
        var x = 0f
        // ios: Views/HoldingsView.swift:428,429,430 (HStack(spacing: 2))
        for (i in on) { val w = max(0f, room * shares[i].toFloat()); drawRect(colors[i], Offset(x, 0f), Size(w, size.height)); x += w + 2.dp.toPx() }
    }
}

/** 점프 직전: 박스 아래 밤 도로 가운데의 가는 점선 중앙선(칸과 틈이 거의 같다). 한 칸은 한 색만 (칸 반올림으로 몫이 조금 어긋나도 느낌만 전한다) */
// ios: Views/HoldingsView.swift:439,455,456,457,458,459
@Composable
private fun Lane(shares: List<Double>, colors: List<Color>, road: Dp, dk: Boolean) {
    // 라이트도 도로는 어둡게 둔다 (차선 불빛이 밝은 바탕에 묻히지 않게)
    Box(Modifier.outset(18.dp).background(bx(dk, 0x2F3650, 0x151925)).drawBehind { drawRect(bx(dk, 0x2F3650, 0x232A3E), Offset.Zero, Size(size.width, 1.dp.toPx())) }
        .padding(horizontal = 18.dp, vertical = road)) {   // ios: Views/HoldingsView.swift:463
        // ios: Views/HoldingsView.swift:440,455
        Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
            val gap = 12.dp.toPx(); val n = max(1, ((size.width + gap) / 26.dp.toPx()).toInt())   // ios: Views/HoldingsView.swift:441
            val w = (size.width - gap * (n - 1)) / n   // ios: Views/HoldingsView.swift:442
            val s = shares   // ios: Views/HoldingsView.swift:443
            if (s.fold(0.0) { a, x -> a + x } <= 0) return@Canvas   // ios: Views/HoldingsView.swift:444
            val (up, dn, flat) = TodayBox.lane(s, n)   // ios: Views/HoldingsView.swift:445
            // ios: Views/HoldingsView.swift:448
            for (i in 0 until n) {
                val c = if (i < up) colors[0] else if (i < up + flat) colors[1] else colors[2]   // ios: Views/HoldingsView.swift:449
                val x = i * (w + gap)
                // ios: Views/HoldingsView.swift:450,451 (색 칸은 같은 색으로 빛 번짐 반경 2)
                if (c != colors[1]) glow(2.dp.toPx()) { g, a -> drawRoundRect(c.copy(alpha = a), Offset(x - g, -g), Size(w + g * 2, size.height + g * 2), CornerRadius(g, g)) }
                drawRect(c.copy(alpha = 0.75f), Offset(x, 0f), Size(w, size.height))   // ios: Views/HoldingsView.swift:452
            }
        }
    }
}

/** .shadow(color:radius:) 필터 자리: 같은 모양을 반경까지 두 겹 옅게 넓혀 그린다 (흐림 필터 없이, 하드웨어 가속 그대로) */
private fun glow(r: Float, body: (grow: Float, alpha: Float) -> Unit) { body(r, 0.18f); body(r / 2, 0.3f) }

/** 지평선: 두꺼운 물결 한 줄을 몫대로 나눠 칠한다 */
// ios: Views/HoldingsView.swift:463,464
@Composable
private fun Wave(shares: List<Double>, colors: List<Color>, modifier: Modifier) {
    Canvas(modifier) {
        val s = shares   // ios: Views/HoldingsView.swift:465
        if (s.fold(0.0) { a, x -> a + x } <= 0) return@Canvas   // ios: Views/HoldingsView.swift:466
        val k = 1.dp.toPx()
        val p = Path(); var x = 0f; var up = true   // ios: Views/HoldingsView.swift:467
        val mid = size.height / 2   // ios: Views/HoldingsView.swift:468
        p.moveTo(0f, mid)   // ios: Views/HoldingsView.swift:469
        // ios: Views/HoldingsView.swift:470
        while (x < size.width) {
            p.quadraticTo(x + 7.5f * k, if (up) mid - 5 * k else mid + 5 * k, x + 15 * k, mid)   // ios: Views/HoldingsView.swift:471
            x += 15 * k; up = !up   // ios: Views/HoldingsView.swift:472
        }
        var a = 0f   // ios: Views/HoldingsView.swift:474
        // ios: Views/HoldingsView.swift:475
        for (i in 0 until 3) if (s[i] > 0) {
            val b = a + size.width * s[i].toFloat()   // ios: Views/HoldingsView.swift:476
            // ios: Views/HoldingsView.swift:477,478
            clipRect(a, -4 * k, a + max(0f, b - a - (if (b < size.width - 1) 2 * k else 0f)), -4 * k + size.height + 8 * k) {
                val st = Stroke(3.2f * k, cap = StrokeCap.Round)   // ios: Views/HoldingsView.swift:480
                if (i != 1) glow(3 * k) { g, al -> drawPath(p, colors[i].copy(alpha = al), style = Stroke(3.2f * k + g * 2, cap = StrokeCap.Round)) }   // ios: Views/HoldingsView.swift:479
                drawPath(p, colors[i], style = st)
            }
            a = b   // ios: Views/HoldingsView.swift:481
        }
    }
}

// ios: Views/HoldingsView.swift:486,487
// 라이트·다크 한 쌍 (라이트는 낮 장면, 점프 직전은 하늘만 밝게)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.backdrop(look: BoxLook, dk: Boolean) {
    when (look) {
        // ios: Views/HoldingsView.swift:525,526,527
        BoxLook.jump -> {
            drawRect(Brush.verticalGradient(listOf(bx(dk, 0xE4EAF7, 0x0B1022), bx(dk, 0xBAC7E8, 0x232A52))))
            if (dk) stars(listOf(Triple(0.67f, 0.11f, 0.9f), Triple(0.8f, 0.26f, 0.6f), Triple(0.57f, 0.24f, 0.5f)))
        }
        // ios: Views/HoldingsView.swift:528,529,530,531
        BoxLook.horizon -> {
            drawRect(Brush.verticalGradient(listOf(bx(dk, 0xEAF0F9, 0x0C111D), bx(dk, 0xF8F0E5, 0x1C2747))))
            val sun = if (dk) hex(0xFFD98A) else hex(0xFFBA68)
            drawRect(Brush.radialGradient(0f to sun.copy(alpha = if (dk) 0.4f else 0.55f), 1f to sun.copy(alpha = 0f), center = Offset(size.width * 1, size.height * 0.8f), radius = 190.dp.toPx()))
            if (dk) stars(listOf(Triple(0.2f, 0.12f, 0.8f), Triple(0.5f, 0.07f, 0.6f), Triple(0.77f, 0.18f, 0.9f), Triple(0.9f, 0.08f, 0.5f)))
        }
        BoxLook.tub -> tileWall(22.dp.toPx(), dk)   // ios: Views/HoldingsView.swift:532,533
        // ios: Views/HoldingsView.swift:534,535,536
        BoxLook.bar -> {
            drawRect(bx(dk, 0xFFFFFF, 0x1E2430))
            if (!dk) drawRoundRect(hex(0xE1E5EA), cornerRadius = CornerRadius(20.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        BoxLook.dory, BoxLook.pino, BoxLook.mug -> themeBackdrop(look, dk)   // ios: Views/HoldingsView.swift:537,538
    }
}

// ios: Views/HoldingsView.swift:502,503,508 (그림이라 누름을 받지 않는다)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.stars(s: List<Triple<Float, Float, Float>>) {
    // ios: Views/HoldingsView.swift:504,505
    for ((x, y, r) in s) drawOval(hex(0xC9D3EA), Offset(x * size.width - r.dp.toPx(), y * size.height - r.dp.toPx()), Size(r.dp.toPx() * 2, r.dp.toPx() * 2))
}

// 그래프 + 금액 축 꼬리표 (시안 v22·v38)

/** 눈금 글·가로선 없이 오른쪽 축에 꼬리표만. 시작 점은 속 빈 점, 시작 금액 높이에서 축까지 옅은 점선 (Sparkline 위에 얹는다) */
// ios: Views/HoldingsView.swift:515,524
@Composable
fun TagChart(
    points: List<Double>,   // ios: Views/HoldingsView.swift:518
    height: Dp = 120.dp,   // ios: Views/HoldingsView.swift:519
    tags: List<TagChart.Tag>,   // ios: Views/HoldingsView.swift:520
    left: String = "", right: String = "",   // ios: Views/HoldingsView.swift:521
    scrub: Scrub? = null,   // ios: Views/HoldingsView.swift:522
) {
    val lo = points.minOrNull() ?: 0.0; val hi = points.maxOrNull() ?: 1.0   // ios: Views/HoldingsView.swift:525
    val hPx = with(LocalDensity.current) { height.toPx() }; val d = LocalDensity.current.density
    // Sparkline 과 같은 세로 자리 (점선 = 시작 금액은 늘 범위 안)
    val y = { v: Double -> ((1 - ((v - lo) / max(1e-9, hi - lo))) * (hPx - 4 * d) + 2 * d).toFloat() }   // ios: Views/HoldingsView.swift:527
    val up = (points.lastOrNull() ?: 0.0) >= (points.firstOrNull() ?: 0.0)   // ios: Views/HoldingsView.swift:528
    val ring = Theme.change(if (up) 1.0 else -1.0); val card = Theme.card
    // ios: Views/HoldingsView.swift:529,556,557
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = tags.joinToString(", ") { it.text } }, horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.Top) {
        // ios: Views/HoldingsView.swift:530
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ios: Views/HoldingsView.swift:531,538
            Box(Modifier.fillMaxWidth().height(height).padding(start = 4.dp)) {
                Sparkline(points = points, modifier = Modifier.fillMaxSize(), avg = points.firstOrNull(), lineWidth = 2.2.dp, showEndDot = true, scrub = scrub)   // ios: Views/HoldingsView.swift:532
                // ios: Views/HoldingsView.swift:533,534,535 (속 빈 시작 점 8, 테두리 2)
                if (points.size > 1) points.firstOrNull()?.let { f ->
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(0f, y(f)); val r = Size(8.dp.toPx(), 8.dp.toPx()).width / 2
                        drawCircle(card, r, c); drawCircle(ring, r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    }
                }
            }
            // ios: Views/HoldingsView.swift:539
            Row(Modifier.fillMaxWidth()) { T(left, Modifier.weight(1f), style = appFont(11f), color = Theme.muted, maxLines = 1); T(right, style = appFont(11f), color = Theme.muted, maxLines = 1) }
        }
        Box(Modifier.width(1.dp).height(height).background(Theme.border))   // ios: Views/HoldingsView.swift:541
        // ios: Views/HoldingsView.swift:542,552,553,554
        Box(Modifier.padding(start = 5.dp).height(height).width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            // ios: Views/HoldingsView.swift:543,544
            if (points.size > 1) TagChart.place(tags, y, hPx, d).forEach { (t, py) ->
                // ios: Views/HoldingsView.swift:545,546,547,548
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, (py - 9 * d).roundToInt()) }.height(18.dp).background(t.bg, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    T(t.text, style = appFont(11f, FontWeight.Bold).tnum(), color = t.fg, maxLines = 1)
                }
            }
        }
    }
}

// ios: Views/HoldingsView.swift:515 (struct TagChart 의 static·Tag)
object TagChart {
    data class Tag(val at: Double, val dy: Dp = 0.dp, val text: String, val bg: Color, val fg: Color = Color.Black)   // ios: Views/HoldingsView.swift:516
    val gray = hex(0x8A949E)   // ios: Views/HoldingsView.swift:517

    /** 꼬리표 높이(px): 값 자리에서 시작해 가까우면 20씩 벌리고, 위·아래로 넘치면 안으로 민다 */
    // ios: Views/HoldingsView.swift:561
    fun place(tags: List<Tag>, y: (Double) -> Float, height: Float, d: Float): List<Pair<Tag, Float>> {
        val ps = tags.map { Pair(it, y(it.at) + it.dy.value * d) }.sortedBy { it.second }.toMutableList()   // ios: Views/HoldingsView.swift:562
        for (i in ps.indices.drop(1)) ps[i] = Pair(ps[i].first, max(ps[i].second, ps[i - 1].second + 20 * d))   // ios: Views/HoldingsView.swift:563
        // ios: Views/HoldingsView.swift:564
        ps.lastOrNull()?.let { l -> if (l.second > height - 9 * d) { val dd = l.second - (height - 9 * d); for (i in ps.indices) ps[i] = Pair(ps[i].first, ps[i].second - dd) } }
        // ios: Views/HoldingsView.swift:565
        ps.firstOrNull()?.let { f -> if (f.second < 9 * d) { val dd = 9 * d - f.second; for (i in ps.indices) ps[i] = Pair(ps[i].first, ps[i].second + dd) } }
        return ps   // ios: Views/HoldingsView.swift:566
    }
}


/** 매매·직접 고치기 셈과 저장 (종목 목록 지우기·종목 상세 고치기가 같이 쓴다) */
// ios: Views/HoldingsView.swift:1112
object HoldingEdit {
    // ios: Views/HoldingsView.swift:1113
    data class Result(val qi: Double, val pi: Double, val q1: Double, val p1: Double, val bad: Boolean, val note: String)

    fun compute(m: AppModel, sym: Symbol, mode: String, q: String, p: String, q0: Double, p0: Double): Result = compute({ s, x -> m.krw(s, x) }, sym, mode, q, p, q0, p0)
    /** 원화 바꾸기(m.krw)만 받는 셈 (숫자 맞추기 F 가 같이 쓴다) */
    // ios: Views/HoldingsView.swift:1115
    fun compute(krw: (Symbol, Double) -> Double, sym: Symbol, mode: String, q: String, p: String, q0: Double, p0: Double): Result {
        val qi = q.toDoubleOrNull() ?: -1.0; val pi = p.toDoubleOrNull() ?: -1.0   // ios: Views/HoldingsView.swift:1116
        var q1 = q0; var p1 = p0; var bad = false; var note = ""   // ios: Views/HoldingsView.swift:1117
        // ios: Views/HoldingsView.swift:1118
        when (mode) {
            // ios: Views/HoldingsView.swift:1119,1120
            "fix" -> { bad = !(qi >= 0 && pi > 0); if (!bad) { q1 = qi; p1 = pi } }
            // ios: Views/HoldingsView.swift:1121
            "buy" -> {
                bad = !(qi > 0 && pi > 0)   // ios: Views/HoldingsView.swift:1122
                // ios: Views/HoldingsView.swift:1123
                if (!bad) { q1 = q0 + qi; p1 = (q0 * p0 + qi * pi) / q1; note = "${AppModel.price(sym, pi)}에 ${qi.swFmt()}주 더 사서 평균 단가가 ${AppModel.price(sym, p1)}가 돼요." }
            }
            // ios: Views/HoldingsView.swift:1124
            else -> {
                bad = !(qi > 0 && qi <= q0 && pi > 0)   // ios: Views/HoldingsView.swift:1125
                // ios: Views/HoldingsView.swift:1126
                if (!bad) {
                    q1 = q0 - qi   // ios: Views/HoldingsView.swift:1127
                    val g = krw(sym, qi * (pi - p0))   // ios: Views/HoldingsView.swift:1128
                    // Views/HoldingsView.swift:1129 (검사 예외: 같은 글)
                    note = "판 부분의 ${if (g >= 0) "이익" else "손실"}은 ${AppModel.man(abs(g))}이에요." + (if (sym.currency == Currency.usd) " 저장하면 올해 해외주식 실현 이익에 더해져 세금 계산에 반영돼요." else " 국내 상장주식은 대주주가 아니면 양도세가 없어요.")
                } else if (qi > q0) note = "가진 수량(${q0.swFmt()}주)보다 많이 팔 수는 없어요."   // ios: Views/HoldingsView.swift:1130
            }
        }
        return Result(qi = qi, pi = pi, q1 = q1, p1 = p1, bad = bad, note = note)   // ios: Views/HoldingsView.swift:1132
    }

    /** 모드를 바꿀 때 입력칸 처음 값: 직접 고치기는 지금 값, 매매는 수량 비우고 가격은 종가 */
    // ios: Views/HoldingsView.swift:1136,1137,1138
    fun initial(mode: String, h: Holding, sym: Symbol): Pair<String, String> =
        if (mode == "fix") Pair(h.qty.swFmtNoGroup(), h.avg.swFmtNoGroup()) else Pair("", sym.close.swFmtNoGroup())

    /** 저장 버튼: 판 해외주식은 실현 이익에 더하고 저장. 매매면 인물의 한 줄을 돌려준다 */
    // ios: Views/HoldingsView.swift:1142
    fun commit(m: AppModel, sym: Symbol, mode: String, r: Result, p0: Double): String? {
        if (mode == "sell" && sym.currency == Currency.usd) m.taxGain += (m.krw(sym, r.qi * (r.pi - p0)) / 1e4).swRound()   // ios: Views/HoldingsView.swift:1143
        val said = save(m, sym, r.q1, r.p1, buy = if (mode == "fix") null else mode == "buy")   // ios: Views/HoldingsView.swift:1144
        // 내 기록의 넣은 돈: 산 것·판 것은 그때 가격으로 (직접 고치기는 그날 가격으로 자동 계산)
        if (mode != "fix") { val dq = if (mode == "buy") r.qi else -r.qi; m.recordDay(trade = Triple(Sync.siteTicker(sym.id), dq, m.krw(sym, dq * r.pi))) }   // ios: Views/HoldingsView.swift:1146
        return said   // ios: Views/HoldingsView.swift:1147
    }

    /** trade: 산 것(true)·판 것(false)이면 평정 지수에 기록하고 인물의 한 줄을 돌려준다. 직접 고치기(null)는 수량이 바뀌었을 때만 늘면 산 것, 줄면 판 것으로 기록 */
    // ios: Views/HoldingsView.swift:1151,1152
    fun save(m: AppModel, sym: Symbol, qty: Double, avg: Double, buy: Boolean? = null): String? {
        val before = m.holdings.firstOrNull { it.symbol == sym.id && it.qty > 0 }; val drift0 = m.calmDrift()   // ios: Views/HoldingsView.swift:1153
        // ios: Views/HoldingsView.swift:1154,1155
        val i = m.holdings.indexOfFirst { it.symbol == sym.id }
        if (i >= 0) m.holdings = if (qty > 0) m.holdings.toMutableList().also { it[i] = Holding(symbol = sym.id, qty = qty, avg = (avg * 100).swRound() / 100) } else m.holdings.filterIndexed { k, _ -> k != i }
        if (before == null) return null   // ios: Views/HoldingsView.swift:1157
        val b = buy ?: (if (qty == before.qty) null else qty > before.qty) ?: return null
        val act = m.calmTrade(sym, buy = b, avgBefore = before.avg, driftBefore = drift0)   // ios: Views/HoldingsView.swift:1158
        val pts = m.calm.events.lastOrNull()?.pts ?: 0; val who = m.calmSpeaker   // ios: Views/HoldingsView.swift:1159
        return "${m.friendName(who)}: ${Calm.feedback(m.voice(who), act)}" + (if (pts != 0) " (${if (pts > 0) "+" else ""}${pts}점)" else "")   // Views/HoldingsView.swift:1160 (검사 예외: 같은 글)
    }
}

/** 종목 탭 위 페이지 고르기 (미션 탭 MissionPagePicker와 같은 모양) */
// ios: Views/HoldingsView.swift:1165,1166,1167
@Composable
fun HoldPagePicker(page: Int, set: (Int) -> Unit) {
    // ios: Views/HoldingsView.swift:1168,1181,1182
    Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(13.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/HoldingsView.swift:1169
        listOf(0 to "내 종목", 1 to "소식판").forEach { (i, t) ->
            val on = page == i   // ios: Views/HoldingsView.swift:1170
            // 애니메이션 없이: 내 종목 화면이 커서 바꿀 때 버벅인다
            // ios: Views/HoldingsView.swift:1171,1172,1173,1174,1175,1177,1178
            Box(Modifier.weight(1f).plainClickable { set(i) }.semantics { selected = on }.defaultMinSize(minHeight = 40.dp)
                .background(if (on) Theme.card else Color.Transparent, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                T(t, style = appFont(14f, FontWeight.Bold), color = if (on) Theme.ink else Theme.sub)
            }
        }
    }
}

// 소식판 (시안): 나라와 상관없이 내 종목에서 바로 나오는 것만. 새로 받는 시세 없음 (이미 받은 시세·지난 종가로 계산)
// 시장과 비교 → 오늘 많이 움직인 종목 → 1년 고점·저점 가까운 종목
@Composable
fun HoldingsNews(m: AppModel, moversOnly: Boolean = false) {   // ios: Views/HoldingsView.swift:1188,1189,1190,1192 홈 아래: 오늘 많이 움직인 종목만
    val rows = m.rows   // ios: Views/HoldingsView.swift:1193
    if (moversOnly) { MoversCard(m, rows); return }   // ios: Views/HoldingsView.swift:1194
    All(m, rows)
}

// ios: Views/HoldingsView.swift:1197
@Composable
private fun All(m: AppModel, rows: List<AppModel.Row>) {
    val stamp = Market.shared.updatedAt
    val ks = remember(rows, stamp) { rows.filter { m.prices.hasHistory(it.sym) }.map { Pair(it, m.metrics(it)) } }   // ios: Views/HoldingsView.swift:1198
    // ios: Views/HoldingsView.swift:1199
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        compare(m, ks)?.let { CompareCard(it) }   // ios: Views/HoldingsView.swift:1200
        TownButton { TownView(m, it) }   // ios: Views/HoldingsView.swift:1201 시장과 비교 밑 [동네]: 전체 화면으로
        MoversCard(m, rows)   // ios: Views/HoldingsView.swift:1202
        NearCard(m, ks)   // ios: Views/HoldingsView.swift:1203
    }
}

// 시장과 비교 (최근 1달). 시장 = 대표 ETF: 미국 종목이 절반 넘으면 S&P500(SPY), 아니면 전 세계(VT). 기록이 없으면 안 보인다
private class Cmp(val mine: Double, val mkt: Double, val name: String)   // ios: Views/HoldingsView.swift:1208
// ios: Views/HoldingsView.swift:1209
private fun compare(m: AppModel, ks: List<Pair<AppModel.Row, AppModel.HoldMetrics>>): Cmp? {
    val w = ks.fold(0.0) { a, k -> a + k.second.w }   // ios: Views/HoldingsView.swift:1210
    if (w <= 0) return null   // ios: Views/HoldingsView.swift:1211
    // 지금 비중으로 4주 전부터 가졌다면 (yr: 1년을 52주로 나눈 가격)
    val mine = ks.fold(0.0) { a, k -> val y = k.second.yr; a + k.second.w / w * (y[52] / max(1e-9, y[48]) - 1) }   // ios: Views/HoldingsView.swift:1213
    val usd = m.rows.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value } / max(1.0, m.rows.fold(0.0) { a, r -> a + r.value })   // ios: Views/HoldingsView.swift:1214
    // ios: Views/HoldingsView.swift:1215
    for ((id, name) in if (usd >= 0.5) listOf("SPY" to "S&P500 ETF") else listOf("VT" to "전 세계 ETF", "SPY" to "S&P500 ETF")) {
        val c = PriceHistory.of(id)?.closes; if (c == null || c.size <= 21) continue   // ios: Views/HoldingsView.swift:1216
        return Cmp(mine = mine, mkt = c[c.size - 1] / c[c.size - 21] - 1, name = name)   // ios: Views/HoldingsView.swift:1217
    }
    return null   // ios: Views/HoldingsView.swift:1219
}

// ios: Views/HoldingsView.swift:1222
@Composable
private fun CompareCard(c: Cmp) {
    val gap = c.mine - c.mkt   // ios: Views/HoldingsView.swift:1223
    // ios: Views/HoldingsView.swift:1224,1233
    Column(Modifier.newsCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("최근 1달, 시장과 비교", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:1225
        // ios: Views/HoldingsView.swift:1226
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cell("내 종목", c.mine)   // ios: Views/HoldingsView.swift:1227
            Cell(c.name, c.mkt)   // ios: Views/HoldingsView.swift:1228
        }
        // Views/HoldingsView.swift:1230 (검사 예외: 같은 글로 옮김)
        val pct = cformat("%.1f", abs(gap) * 100); val dir = if (gap > 0) "높아요" else "낮아요"
        T(if (abs(gap) < 0.001) "시장과 거의 같게 움직였어요" else "시장보다 $pct% $dir",
          style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1231
    }
}

// ios: Views/HoldingsView.swift:1236
@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(t: String, v: Double) {
    // ios: Views/HoldingsView.swift:1237,1241,1242,1243
    Column(Modifier.weight(1f).background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(t, style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/HoldingsView.swift:1238
        T(AppModel.sgn(v), style = appFont(20f, FontWeight.Bold).tnum(), color = Theme.change(v))   // ios: Views/HoldingsView.swift:1239
    }
}

// 오늘 많이 움직인 종목: 내 평가액에 준 영향(원화 금액) 크기순 3개. 홈 말풍선 한 줄 해석의 근거
// ios: Views/HoldingsView.swift:1247
@Composable
private fun MoversCard(m: AppModel, rows: List<AppModel.Row>) {
    val fx = Market.shared.valFx   // ios: Views/HoldingsView.swift:1248
    val top = rows.filter { it.id != KoreaEstimate.sumId }   // ios: Views/HoldingsView.swift:1249
        .map { r -> Pair(r, r.h.qty * r.sym.quote.prevClose * (if (r.sym.currency == Currency.usd) fx else 1.0) * r.sym.quote.change) }   // ios: Views/HoldingsView.swift:1250 (r, w)
        .sortedByDescending { abs(it.second) }.take(3)   // ios: Views/HoldingsView.swift:1251
    // ios: Views/HoldingsView.swift:1252,1260
    Column(Modifier.newsCard(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        T("오늘 많이 움직인 종목", Modifier.padding(bottom = 6.dp), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:1253
        if (top.isEmpty()) T("종목을 넣으면 보여요", style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1254
        // ios: Views/HoldingsView.swift:1255
        top.forEach { (r, w) ->
            val d = r.sym.quote.change   // ios: Views/HoldingsView.swift:1256
            Line(m, r, AppModel.sgn2(d) + " · " + AppModel.eokS(w), Theme.change(d))   // ios: Views/HoldingsView.swift:1257
        }
    }
}

// 1년 고점·저점 가까운 종목 (5% 안)
// ios: Views/HoldingsView.swift:1264
@Composable
private fun NearCard(m: AppModel, ks: List<Pair<AppModel.Row, AppModel.HoldMetrics>>) {
    val hi = ks.filter { it.second.dd > -0.05 }.sortedByDescending { it.second.dd }   // ios: Views/HoldingsView.swift:1265
    val lo = ks.filter { it.second.dd <= -0.05 && it.first.sym.last / max(1e-9, it.second.lo) - 1 < 0.05 }   // ios: Views/HoldingsView.swift:1266
    // ios: Views/HoldingsView.swift:1267,1277
    Column(Modifier.newsCard(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        T("1년 고점·저점 가까이", Modifier.padding(bottom = 6.dp), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:1268
        if (hi.isEmpty() && lo.isEmpty()) T("고점·저점 5% 안에 있는 종목은 없어요", style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:1269
        // ios: Views/HoldingsView.swift:1270
        hi.forEach { (r, k) ->
            // Views/HoldingsView.swift:1271 (검사 예외: 같은 글로 옮김)
            val p = cformat("%.0f", abs(k.dd) * 100)
            Line(m, r, if (k.dd > -0.005) "1년 고점" else "고점까지 $p%", Theme.up)
        }
        // ios: Views/HoldingsView.swift:1273
        lo.forEach { (r, k) ->
            // Views/HoldingsView.swift:1274 (검사 예외: 같은 글로 옮김)
            val p = cformat("%.0f", (r.sym.last / max(1e-9, k.lo) - 1) * 100)
            Line(m, r, "저점 위 $p%", Theme.down)
        }
    }
}

// ios: Views/HoldingsView.swift:1280
@Composable
private fun Line(m: AppModel, r: AppModel.Row, right: String, color: Color) {
    // ios: Views/HoldingsView.swift:1281,1282,1288,1290
    val open = openDetail(m)   // ios: Views/HoldingsView.swift:1403 (DetailLink)
    Row(Modifier.fillMaxWidth().plainClickable { open(r.sym) }.defaultMinSize(minHeight = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(symbol = r.id, size = 28.dp)   // ios: Views/HoldingsView.swift:1283
        T(r.sym.name, Modifier.weight(1f).padding(end = 4.dp), style = appFont(14f, FontWeight.SemiBold), maxLines = 1)   // ios: Views/HoldingsView.swift:1284,1285
        T(right, style = appFont(14f, FontWeight.Bold).tnum(), color = color)   // ios: Views/HoldingsView.swift:1286
    }
}

// ios: Views/HoldingsView.swift:1294,1295,1296,1297,1298
@Composable
private fun Modifier.newsCard(): Modifier = this.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(16.dp)

/** 종목 상세를 팝업으로 여는 동작 (DetailSheet 가 넣는다) */
// ios: Views/HoldingsView.swift:647,648,649
val LocalOpenDetail = androidx.compose.runtime.staticCompositionLocalOf<((Symbol) -> Unit)?> { null }

/** 종목 줄 누르기 (DetailLink): 위쪽 DetailSheet 가 팝업으로 연다. 없으면 종목 길에 쌓는다 */
// ios: Views/HoldingsView.swift:653,654,655,656,657
@Composable
fun openDetail(m: AppModel): (Symbol) -> Unit = LocalOpenDetail.current ?: { s: Symbol -> m.holdPath = m.holdPath + s }

/**
 * 이 화면 아래 종목 줄을 누르면 종목 상세 팝업을 띄운다 (종목·분석·홈 탭, 분석 시트).
 * 팝업 머리는 왼쪽 종목 이름 + 오른쪽 X, 고치기는 팝업 안에서 넘긴다 (HoldingDetailSheet)
 */
// ios: Views/HoldingsView.swift:630,631,632,635,637,638,661,662,663,665,666,668,669,670,671,673,674,675,676
@Composable
fun DetailSheet(m: AppModel, period: Period = Period.m1, takesHoldOpen: Boolean = false, content: @Composable () -> Unit) {
    var pick by remember { mutableStateOf(listOf<Any>()) }
    // 종목 탭: 알림으로 온 종목(m.holdOpen)도 여기서 띄운다
    LaunchedEffect(takesHoldOpen) {
        if (takesHoldOpen) androidx.compose.runtime.snapshotFlow { m.holdOpen }.collect { s -> if (s != null) { m.holdOpen = null; pick = listOf(s) } }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalOpenDetail provides { s: Symbol -> pick = listOf(s) }) { content() }
    com.naeilo.app.PathSheet(pick, { pick = it }) { r ->
        if (r is EditRoute) NavPage(r.sym.name) { HoldingEditView(m, sym = r.sym) { pick = pick.dropLast(1) } }   // ios: Views/HoldingsView.swift:767
        else { val s = r as Symbol; NavPage(s.name) { HoldingDetailView(m, sym = s, period0 = period, inSheet = true, onEdit = { pick = pick + EditRoute(it) }) } }
    }
}
