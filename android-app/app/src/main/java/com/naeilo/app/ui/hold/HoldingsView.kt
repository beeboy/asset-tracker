package com.naeilo.app.ui.hold

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

// 종목 탭: 내 종목 | 소식판. 내 종목은 내 돈이 얼마 있고 얼마 벌었나. 맨 위 평가액 → 1년 전부터 가졌다면 그래프 → 미국/국내 묶음 목록
// ios: Views/HoldingsView.swift:6,7,18
@Composable
fun HoldingsView(m: AppModel) {
    var period by remember { mutableStateOf(Period.m1) }        // ios: Views/HoldingsView.swift:8 위 그래프에만 걸린다 (목록 %는 늘 산 가격 대비)
    var said by remember { mutableStateOf<String?>(null) }          // ios: Views/HoldingsView.swift:9 지운 뒤 인물의 한 줄 (평정 지수)
    var swiped by remember { mutableStateOf<String?>(null) }        // ios: Views/HoldingsView.swift:10 왼쪽으로 밀어 지우기 버튼이 보이는 종목
    var askDel by remember { mutableStateOf<Symbol?>(null) }        // ios: Views/HoldingsView.swift:11 지울지 한 번 묻는 종목
    var river by remember { mutableStateOf<EarnTarget?>(null) }     // ios: Views/HoldingsView.swift:12 내 몫 실적 강 팝업 (목록 오른쪽 막대를 누르면)
    var inUsd by rememberPrefBool("holdUsd", false)   // ios: Views/HoldingsView.swift:13 목록 금액: 미국 종목을 달러로 볼지
    val dts = LocalTextScale.current   // ios: Views/HoldingsView.swift:14
    val arrive by rememberPrefString("town.arrive", "")   // ios: Views/HoldingsView.swift:15

    // 방금 더한 종목: 홈 아래 [동네]가 바로 연다
    // ios: Views/HoldingsView.swift:70,71
    LaunchedEffect(arrive) { if (arrive.isNotEmpty()) m.tab = com.naeilo.app.Tab.home }

    // ios: Views/HoldingsView.swift:22,23,63,64,65,66
    NavStack(m.holdPath, { m.holdPath = it }, {
        Refreshable({ Market.shared.pullRefresh(m) }, Modifier.fillMaxSize().background(Theme.bg)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // ios: Views/HoldingsView.swift:61
                Screen {
                    val rows = m.rows   // ios: Views/HoldingsView.swift:19
                    val us = m.sortedRows.filter { it.sym.currency == Currency.usd }   // ios: Views/HoldingsView.swift:20
                    val kr = m.sortedRows.filter { it.sym.currency != Currency.usd }   // ios: Views/HoldingsView.swift:21
                    // ios: Views/HoldingsView.swift:24
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.outset(16.dp)) { AppHeader(tab = "종목") }   // ios: Views/HoldingsView.swift:25 (-16)
                        SampleNote(m)   // ios: Views/HoldingsView.swift:26
                        // ios: Views/HoldingsView.swift:27 소식판은 뺐다 (많이 움직인 종목·동네는 홈 아래로, 2026-10-21)
                        run {
                            TotalCard(m, us = us.size, kr = kr.size)   // ios: Views/HoldingsView.swift:28
                            TotalChart(m, rows, period) { period = it }   // ios: Views/HoldingsView.swift:29
                            // ios: Views/HoldingsView.swift:30
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // ios: Views/HoldingsView.swift:31,35
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    T("종목 ${rows.size}", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:32
                                    T("%는 산 가격 대비", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:33
                                }
                                UnitToggle(inUsd) { inUsd = it }   // ios: Views/HoldingsView.swift:36
                                // ios: Views/HoldingsView.swift:37,38,39,40,43
                                DropMenu(HoldSort.entries.map { c -> MenuItem(c.label, icon = if (c == m.holdSort) "checkmark" else null, onClick = { m.holdSort = c }) }) {
                                    // ios: Views/HoldingsView.swift:44,45,47 (Label + systemImage "chevron.down")
                                    Row(Modifier.defaultMinSize(minHeight = 32.dp).semantics(mergeDescendants = true) { contentDescription = tr("정렬: ${m.holdSort.label}") },
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Sym("chevron.down", 13f, Theme.teal)
                                        T(m.holdSort.label, style = appFont(13f, FontWeight.Bold), color = Theme.teal)
                                    }
                                }
                            }
                            // ios: Views/HoldingsView.swift:49
                            if (us.isNotEmpty()) { EarnTip(); Group(m, "미국", us, inUsd, dts.isAccessibilitySize, swiped, { swiped = it }, { river = it }) { askDel = it } }   // ios: Views/HoldingsView.swift:49
                            // ios: Views/HoldingsView.swift:50
                            if (kr.isNotEmpty()) Group(m, "국내", kr, inUsd, dts.isAccessibilitySize, swiped, { swiped = it }, { river = it }) { askDel = it }
                            // ios: Views/HoldingsView.swift:51,52,53,54,55,56
                            Row(Modifier.fillMaxWidth().plainClickable { m.holdPath = m.holdPath + "add" }.defaultMinSize(minHeight = 48.dp)
                                .border(2.dp, Theme.teal, RoundedCornerShape(12.dp)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Sym("plus", 15f, Theme.teal); Spacer(Modifier.width(6.dp))
                                T("종목 추가", style = appFont(15f, FontWeight.Bold), color = Theme.teal)
                            }
                        }
                        T(Market.shared.asOfText, style = appFont(13f), color = Theme.sub2)   // ios: Views/HoldingsView.swift:58
                        Spacer(Modifier.height(24.dp))   // ios: Views/HoldingsView.swift:59 아래 탭바가 마지막 카드를 가리지 않게
                    }
                }
            }
        }
    }) { r ->
        // ios: Views/HoldingsView.swift:67,69
        if (r is Symbol) NavPage(r.name) { HoldingDetailView(m, sym = r, period0 = period) }
        else if (r is EditRoute) NavPage(r.sym.name) { HoldingEditView(m, sym = r.sym) { m.holdPath = m.holdPath.dropLast(1) } }   // 고치기 (상세의 NavigationLink)
        else NavPage { AddHoldingView(m) { m.holdPath = m.holdPath.dropLast(1) } }
    }
    // ios: Views/HoldingsView.swift:72,73,74
    IosAlert(said != null, "내 투자 습관", said, listOf(DialogAction("확인", onClick = {}))) { said = null }
    EarningsRiverSheet(river) { river = null }   // ios: Views/HoldingsView.swift:68
    // ios: Views/HoldingsView.swift:76,77,78,79,81,82,84,85 (76 은 검사 예외: 같은 제목)
    ConfirmSheet(askDel != null, "${askDel?.name ?: ""} 종목을 지울까요?", "목록과 평가액에서 빠져요. 다시 넣으려면 종목 추가에서 넣으면 돼요.",
        listOf(DialogAction("지우기", destructive = true, onClick = {
            val sym = askDel ?: return@DialogAction
            val avg = m.holdings.firstOrNull { it.symbol == sym.id }?.avg ?: sym.close
            swiped = null   // 밀린 줄은 snappy 스프링으로 돌아간다 (Group 의 offset)
            // 세부정보의 지우기와 같이: 판 것으로 평정 지수에 남긴다
            said = HoldingEdit.save(m, sym, 0.0, avg, buy = false)
            askDel = null
        })), cancel = "취소") { askDel = null; swiped = null }
}

// 손익 중심: 홈(평가액·목표)과 겹치지 않게 산 돈 대비 수익을 크게
// ios: Views/HoldingsView.swift:90
@Composable
private fun TotalCard(m: AppModel, us: Int, kr: Int) {
    val gain = m.total - m.cost   // ios: Views/HoldingsView.swift:91
    // ios: Views/HoldingsView.swift:92
    Card {
        T("수익", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/HoldingsView.swift:93
        // ios: Views/HoldingsView.swift:94
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ios: Views/HoldingsView.swift:96 (96 은 검사 예외: 같은 글)
            T("${if (gain >= 0) "+" else "−"}${AppModel.man(abs(gain))}원", Modifier.alignByBaseline(), style = appFont(28f, FontWeight.Bold).tnum(), color = Theme.change(gain), roll = gain)
            T(AppModel.sgn(m.ret), Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold), color = Theme.change(gain))   // ios: Views/HoldingsView.swift:97
        }
        // ios: Views/HoldingsView.swift:99,100
        T("원금 ${AppModel.man(m.cost)} → 평가 ${AppModel.man(m.total)}", style = appFont(14f).tnum(), color = Theme.sub)
        // ios: Views/HoldingsView.swift:102 (102 는 검사 예외: 같은 글)
        T("${us + kr}종목" + (if (us > 0) " · $1 = ${cformat("%,d", Market.shared.valFx.swRound().roundToLong())}원" else ""), style = appFont(12f), color = Theme.muted)
    }
}

/** .monospacedDigit(): 숫자 칸 너비를 같게 */
private fun androidx.compose.ui.text.TextStyle.tnum() = copy(fontFeatureSettings = "tnum")

/** 위 그래프의 숫자: 기간·보유·시세가 바뀔 때만 다시 센다 (그리는 중엔 계산하지 않는다) */
private class Total(val tot: List<Double>, val first: Double, val last: Double, val chg: Double, val noHist: List<String>)

// 지금 비중을 1년(기간) 전부터 가졌다면: 백테스트 평가액
// ios: Views/HoldingsView.swift:107
@Composable
private fun TotalChart(m: AppModel, rows: List<AppModel.Row>, period: Period, setPeriod: (Period) -> Unit) {
    val stamp = Market.shared.updatedAt
    val t = remember(period, rows, stamp) {
        val series = rows.map { r -> m.prices.series(r.sym, period).map { m.krw(r.sym, it * r.h.qty) } }   // ios: Views/HoldingsView.swift:108
        val n = series.minOfOrNull { it.size } ?: 0   // ios: Views/HoldingsView.swift:109
        val tot = (0 until n).map { i -> series.fold(0.0) { a, s -> a + s[i] } }   // ios: Views/HoldingsView.swift:110
        val first = tot.firstOrNull() ?: 0.0; val last = tot.lastOrNull() ?: 0.0   // ios: Views/HoldingsView.swift:111
        val chg = last / max(1.0, first) - 1   // ios: Views/HoldingsView.swift:112
        val noHist = rows.filter { !m.prices.hasHistory(it.sym) }.map { it.sym.name }   // ios: Views/HoldingsView.swift:132
        Total(tot, first, last, chg, noHist)
    }
    val start = LocalDate.now().minusDays((period.span * 1095).toLong())   // ios: Views/HoldingsView.swift:113
    // ios: Views/HoldingsView.swift:114
    Card {
        T("지금 구성, ${period.label} 전부터라면", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:115
        // ios: Views/HoldingsView.swift:116,119
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/HoldingsView.swift:117,118
            FitText("${AppModel.man(t.first)} → ${AppModel.man(t.last)}", appFont(14f, FontWeight.SemiBold).tnum(), 0.8f, Modifier.weight(1f), maxLines = 1)
            T(AppModel.sgn(t.chg), style = appFont(15f, FontWeight.Bold), color = Theme.change(t.chg))   // ios: Views/HoldingsView.swift:120
        }
        // 점선 = 시작 금액
        // ios: Views/HoldingsView.swift:123,124
        Sparkline(points = t.tot, modifier = Modifier.fillMaxWidth().height(120.dp), avg = t.first, lineWidth = 2.2.dp, showEndDot = true,
                  scrub = Scrub.period(period) { AppModel.man(it) + "원" })
        // ios: Views/HoldingsView.swift:125
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/HoldingsView.swift:126,130
            T(if (period.span < 1) "${start.monthValue}월" else "${start.year}.${start.monthValue}", Modifier.weight(1f), style = appFont(11f), color = Theme.muted)
            T("오늘", style = appFont(11f), color = Theme.muted)   // ios: Views/HoldingsView.swift:127,128
        }
        ChipRow(items = periods.map { it to it.label }, selection = period, onSelect = setPeriod, fill = true)   // ios: Views/HoldingsView.swift:131
        // ios: Views/HoldingsView.swift:133,135 (135 는 검사 예외: 같은 글)
        val names = t.noHist.joinToString(", ")
        if (t.noHist.isNotEmpty()) T("과거 시세 없음: $names · 평평하게 그림", style = appFont(12f), color = Theme.muted)
    }
}
private val periods: List<Period> = listOf(Period.m1, Period.m3, Period.m6, Period.y1, Period.y3)   // ios: Views/HoldingsView.swift:16

// 미국 주식 / 국내 주식 묶음 카드
// 목록 금액 단위: $ / 원 (미국 종목만 바뀐다)
// ios: Views/HoldingsView.swift:142
@Composable
private fun UnitToggle(inUsd: Boolean, set: (Boolean) -> Unit) {
    // ios: Views/HoldingsView.swift:143,153,154,156 (156 은 검사 예외: 같은 글)
    Row(
        Modifier.border(1.dp, Theme.border, androidx.compose.foundation.shape.CircleShape).padding(2.dp)
            .semantics(mergeDescendants = true) { contentDescription = tr("금액 단위: ${if (inUsd) "달러" else "원"}"); customActions = listOf(CustomAccessibilityAction(tr("바꾸기")) { set(!inUsd); true }) },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ios: Views/HoldingsView.swift:144
        listOf(true, false).forEach { u ->
            // ios: Views/HoldingsView.swift:145,146,147,148,149,150
            Box(Modifier.plainClickable { set(u) }.size(32.dp, 28.dp).background(if (inUsd == u) Theme.teal else Color.Transparent, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                T(if (u) "$" else "원", style = appFont(13f, FontWeight.Bold), color = if (inUsd == u) Color.White else Theme.sub)
            }
        }
    }
}

// ios: Views/HoldingsView.swift:159
private fun usd(inUsd: Boolean, r: AppModel.Row): Boolean = inUsd && r.sym.currency == Currency.usd
// ios: Views/HoldingsView.swift:160
private fun amountText(inUsd: Boolean, rs: List<AppModel.Row>): String {
    // ios: Views/HoldingsView.swift:161
    if (rs.isNotEmpty() && rs.all { usd(inUsd, it) }) {
        return "$" + rs.fold(0.0) { a, r -> a + r.h.qty * r.sym.last }.swFmtFrac(2)   // ios: Views/HoldingsView.swift:162
    }
    return AppModel.man(rs.fold(0.0) { a, r -> a + r.value })   // ios: Views/HoldingsView.swift:164
}

// ios: Views/HoldingsView.swift:167
@Composable
private fun Group(m: AppModel, title: String, rs: List<AppModel.Row>, inUsd: Boolean, big: Boolean, swiped: String?, setSwiped: (String?) -> Unit, openRiver: (EarnTarget) -> Unit, askDel: (Symbol) -> Unit) {
    // ios: Views/HoldingsView.swift:168,208,209,210
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ios: Views/HoldingsView.swift:169,171,175
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("$title ${rs.size}", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:170
            // ios: Views/HoldingsView.swift:172,173
            T(amountText(inUsd, rs), style = appFont(14f, FontWeight.SemiBold).tnum(), color = Theme.sub, roll = rs.fold(0.0) { a, r -> a + r.value })
        }
        // ios: Views/HoldingsView.swift:176
        rs.forEach { r ->
            HDivider(Theme.line)   // ios: Views/HoldingsView.swift:177
            // ios: Views/HoldingsView.swift:79,180,200 밀린 줄은 snappy 스프링으로 움직인다 (지운 뒤 돌아갈 때도)
            val x by animateDpAsState(if (swiped == r.id) (-84).dp else 0.dp, snappy, label = "swipe")
            var menu by remember { mutableStateOf(false) }
            // ios: Views/HoldingsView.swift:182,196
            Box(Modifier.fillMaxWidth().clip(androidx.compose.ui.graphics.RectangleShape)) {
                // 왼쪽으로 밀면 뒤에 숨은 지우기 버튼이 보인다
                // ios: Views/HoldingsView.swift:183,184,185,189,190,192,193
                if (swiped == r.id) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                    Column(
                        Modifier.width(84.dp).fillMaxHeight()
                            .background(Theme.up).plainClickable { askDel(r.sym) }.semantics(mergeDescendants = true) { contentDescription = tr("${r.sym.name} 지우기") },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Sym("trash", 18f, Color.White, scaled = false)   // ios: Views/HoldingsView.swift:186
                        T("지우기", style = appFont(12f, FontWeight.Bold), color = Color.White)   // ios: Views/HoldingsView.swift:187
                    }
                }
                // ios: Views/HoldingsView.swift:178,179
                Box(
                    Modifier.offset(x = x).background(Theme.card)
                        // ios: Views/HoldingsView.swift:197,198,199,200,205 (24 넘게 끌고, 가로가 세로의 1.5배 넘을 때만. 40 넘게 왼쪽이면 버튼, 아니면 닫기. 움직임은 위 animateDpAsState 의 snappy)
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
                        .plainCombinedClickable(onLongClick = { menu = true }) { if (swiped != null) setSwiped(null) else m.holdPath = m.holdPath + r.sym }
                        .semantics { customActions = listOf(CustomAccessibilityAction(tr("지우기")) { askDel(r.sym); true }) },
                ) { HoldRow(m, r, inUsd, big, openRiver) }
                // ios: Views/HoldingsView.swift:202,203 (길게 누르면 메뉴)
                ContextMenu(menu, listOf(MenuItem("이 종목 지우기", icon = "trash", destructive = true, onClick = { askDel(r.sym) }))) { menu = false }
            }
        }
    }
}


// 행: 로고 · "이름 수량" · "지금 가격 · 오늘 %" / 평가액 · 산 가격 대비 % · 내 몫 실적 막대 두 개
// 왼쪽(로고·이름)은 상세로, 오른쪽(평가액·막대)은 내 몫 실적 강 팝업으로 (자료가 없으면 상세로)
// ios: Views/HoldingsView.swift:215
@Composable
private fun HoldRow(m: AppModel, r: AppModel.Row, inUsd: Boolean, big: Boolean, openRiver: (EarnTarget) -> Unit) {
    val chg = if (r.cost > 0) r.value / r.cost - 1 else 0.0   // ios: Views/HoldingsView.swift:216
    val es = EarningsFeed.shared.summary(r.sym, r.h.qty)   // ios: Views/HoldingsView.swift:217
    // ios: Views/HoldingsView.swift:218,219 이름 옆 금색 점 (오늘 실적)
    @Composable fun Dot() = Box(Modifier.size(6.dp, 6.dp).alpha(if (es?.fresh == true) 1f else 0f).background(EarnColor.gold, CircleShape)
        .then(if (es?.fresh == true) Modifier.semantics { contentDescription = tr("오늘 실적") } else Modifier.clearAndSetSemantics {}))
    val onePx = with(LocalDensity.current) { 1.dp.roundToPx() }   // ios: Views/HoldingsView.swift:230
    val day = r.sym.quote.change   // ios: Views/HoldingsView.swift:220
    // ios: Views/HoldingsView.swift:221
    @Composable fun Name(mod: Modifier) = Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(symbol = r.id, size = 36.dp)   // ios: Views/HoldingsView.swift:222
        // ios: Views/HoldingsView.swift:223
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 글자를 줄이지 않는다(종목마다 크기가 같게). 이름 옆 한 줄이 먼저: 안 맞으면 수량·평균을 다음 줄로
            // ios: Views/HoldingsView.swift:226
            @Composable fun Qty() = T("${r.h.qty.swFmt()}주", style = appFont(12f).tnum(), color = Theme.sub, maxLines = 1)
            // ios: Views/HoldingsView.swift:227
            FitsOr(first = {
                // ios: Views/HoldingsView.swift:228,229,231
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(r.sym.name, Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold), maxLines = 1)
                    if (es?.fresh == true) Box(Modifier.alignBy { it.measuredHeight + onePx }) { Dot() }   // ios: Views/HoldingsView.swift:230 점 아래가 글 바탕선 1 위
                    Box(Modifier.alignByBaseline()) { Qty() }
                }
            }, second = {
                // ios: Views/HoldingsView.swift:233,234,235
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { T(r.sym.name, style = appFont(15f, FontWeight.Bold), maxLines = 1); if (es?.fresh == true) Dot() }
                    Qty()
                }
            })
            // ios: Views/HoldingsView.swift:238
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("${AppModel.price(r.sym, r.sym.last)} ·", style = appFont(12f).tnum(), color = Theme.sub, maxLines = 1)   // ios: Views/HoldingsView.swift:239,242
                T((if (day >= 0) "+" else "−") + cformat("%.2f%%", abs(day) * 100), style = appFont(12f).tnum(), color = Theme.change(day), maxLines = 1)   // ios: Views/HoldingsView.swift:240
            }
        }
    }
    // ios: Views/HoldingsView.swift:245
    @Composable fun Amount(mod: Modifier) = Column(mod, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(amountText(inUsd, listOf(r)), style = appFont(15f, FontWeight.Bold).tnum(), maxLines = 1, roll = r.value)   // ios: Views/HoldingsView.swift:246
        T(AppModel.sgn(chg), style = appFont(13f, FontWeight.Bold), color = Theme.change(chg))   // ios: Views/HoldingsView.swift:247
    }
    // 실적 자료가 있으면 강 팝업, 없으면(ETF·국내·못 받음) 줄표만 두고 줄 전체처럼 상세로
    // ios: Views/HoldingsView.swift:250,251,252,253,254,255,256,257
    @Composable fun Earn(mod: Modifier) = Row(
        mod.defaultMinSize(minHeight = 44.dp).then(if (es != null) Modifier.plainClickable { openRiver(EarnTarget(r.sym, r.h.qty)) }
            .semantics { onClick(label = tr("내 몫 실적 보기")) { openRiver(EarnTarget(r.sym, r.h.qty)); true } } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) { Amount(Modifier.widthIn(min = 72.dp)); EarnBars(es) }
    // ios: Views/HoldingsView.swift:260,275,276
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        // ios: Views/HoldingsView.swift:261,262,263,264,265
        if (big) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Name(Modifier); Earn(Modifier) }
        } else {   // ios: Views/HoldingsView.swift:266
            // ios: Views/HoldingsView.swift:267
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/HoldingsView.swift:268,269,270
                Row(Modifier.weight(1f).defaultMinSize(minHeight = 64.dp), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                    Name(Modifier.weight(1f, fill = true).padding(end = 4.dp))
                }
                Earn(Modifier)   // ios: Views/HoldingsView.swift:271
            }
        }
    }
}

/** 매매·직접 고치기 셈과 저장 (종목 목록 지우기·종목 상세 고치기가 같이 쓴다) */
// ios: Views/HoldingsView.swift:746
object HoldingEdit {
    // ios: Views/HoldingsView.swift:747
    data class Result(val qi: Double, val pi: Double, val q1: Double, val p1: Double, val bad: Boolean, val note: String)

    fun compute(m: AppModel, sym: Symbol, mode: String, q: String, p: String, q0: Double, p0: Double): Result = compute({ s, x -> m.krw(s, x) }, sym, mode, q, p, q0, p0)
    /** 원화 바꾸기(m.krw)만 받는 셈 (숫자 맞추기 F 가 같이 쓴다) */
    // ios: Views/HoldingsView.swift:749
    fun compute(krw: (Symbol, Double) -> Double, sym: Symbol, mode: String, q: String, p: String, q0: Double, p0: Double): Result {
        val qi = q.toDoubleOrNull() ?: -1.0; val pi = p.toDoubleOrNull() ?: -1.0   // ios: Views/HoldingsView.swift:750
        var q1 = q0; var p1 = p0; var bad = false; var note = ""   // ios: Views/HoldingsView.swift:751
        // ios: Views/HoldingsView.swift:752
        when (mode) {
            // ios: Views/HoldingsView.swift:753,754
            "fix" -> { bad = !(qi >= 0 && pi > 0); if (!bad) { q1 = qi; p1 = pi } }
            // ios: Views/HoldingsView.swift:755
            "buy" -> {
                bad = !(qi > 0 && pi > 0)   // ios: Views/HoldingsView.swift:756
                // ios: Views/HoldingsView.swift:757
                if (!bad) { q1 = q0 + qi; p1 = (q0 * p0 + qi * pi) / q1; note = "${AppModel.price(sym, pi)}에 ${qi.swFmt()}주 더 사서 평균 단가가 ${AppModel.price(sym, p1)}가 돼요." }
            }
            // ios: Views/HoldingsView.swift:758
            else -> {
                bad = !(qi > 0 && qi <= q0 && pi > 0)   // ios: Views/HoldingsView.swift:759
                // ios: Views/HoldingsView.swift:760
                if (!bad) {
                    q1 = q0 - qi   // ios: Views/HoldingsView.swift:761
                    val g = krw(sym, qi * (pi - p0))   // ios: Views/HoldingsView.swift:762
                    // Views/HoldingsView.swift:741 (검사 예외: 같은 글)
                    note = "판 부분의 ${if (g >= 0) "이익" else "손실"}은 ${AppModel.man(abs(g))}이에요." + (if (sym.currency == Currency.usd) " 저장하면 올해 해외주식 실현 이익에 더해져 세금 계산에 반영돼요." else " 국내 상장주식은 대주주가 아니면 양도세가 없어요.")
                } else if (qi > q0) note = "가진 수량(${q0.swFmt()}주)보다 많이 팔 수는 없어요."   // ios: Views/HoldingsView.swift:764
            }
        }
        return Result(qi = qi, pi = pi, q1 = q1, p1 = p1, bad = bad, note = note)   // ios: Views/HoldingsView.swift:766
    }

    /** 모드를 바꿀 때 입력칸 처음 값: 직접 고치기는 지금 값, 매매는 수량 비우고 가격은 종가 */
    // ios: Views/HoldingsView.swift:770,771,772
    fun initial(mode: String, h: Holding, sym: Symbol): Pair<String, String> =
        if (mode == "fix") Pair(h.qty.swFmtNoGroup(), h.avg.swFmtNoGroup()) else Pair("", sym.close.swFmtNoGroup())

    /** 저장 버튼: 판 해외주식은 실현 이익에 더하고 저장. 매매면 인물의 한 줄을 돌려준다 */
    // ios: Views/HoldingsView.swift:776
    fun commit(m: AppModel, sym: Symbol, mode: String, r: Result, p0: Double): String? {
        if (mode == "sell" && sym.currency == Currency.usd) m.taxGain += (m.krw(sym, r.qi * (r.pi - p0)) / 1e4).swRound()   // ios: Views/HoldingsView.swift:777
        val said = save(m, sym, r.q1, r.p1, buy = if (mode == "fix") null else mode == "buy")   // ios: Views/HoldingsView.swift:778
        // 내 기록의 넣은 돈: 산 것·판 것은 그때 가격으로 (직접 고치기는 그날 가격으로 자동 계산)
        if (mode != "fix") { val dq = if (mode == "buy") r.qi else -r.qi; m.recordDay(trade = Triple(Sync.siteTicker(sym.id), dq, m.krw(sym, dq * r.pi))) }   // ios: Views/HoldingsView.swift:780
        return said   // ios: Views/HoldingsView.swift:781
    }

    /** trade: 산 것(true)·판 것(false)이면 평정 지수에 기록하고 인물의 한 줄을 돌려준다. 직접 고치기(null)는 수량이 바뀌었을 때만 늘면 산 것, 줄면 판 것으로 기록 */
    // ios: Views/HoldingsView.swift:785,786
    fun save(m: AppModel, sym: Symbol, qty: Double, avg: Double, buy: Boolean? = null): String? {
        val before = m.holdings.firstOrNull { it.symbol == sym.id && it.qty > 0 }; val drift0 = m.calmDrift()   // ios: Views/HoldingsView.swift:787
        // ios: Views/HoldingsView.swift:788,789
        val i = m.holdings.indexOfFirst { it.symbol == sym.id }
        if (i >= 0) m.holdings = if (qty > 0) m.holdings.toMutableList().also { it[i] = Holding(symbol = sym.id, qty = qty, avg = (avg * 100).swRound() / 100) } else m.holdings.filterIndexed { k, _ -> k != i }
        if (before == null) return null   // ios: Views/HoldingsView.swift:791
        val b = buy ?: (if (qty == before.qty) null else qty > before.qty) ?: return null
        val act = m.calmTrade(sym, buy = b, avgBefore = before.avg, driftBefore = drift0)   // ios: Views/HoldingsView.swift:792
        val pts = m.calm.events.lastOrNull()?.pts ?: 0; val who = m.calmSpeaker   // ios: Views/HoldingsView.swift:793
        return "${m.friendName(who)}: ${Calm.feedback(m.voice(who), act)}" + (if (pts != 0) " (${if (pts > 0) "+" else ""}${pts}점)" else "")   // Views/HoldingsView.swift:772 (검사 예외: 같은 글)
    }
}

/** 종목 탭 위 페이지 고르기 (미션 탭 MissionPagePicker와 같은 모양) */
// ios: Views/HoldingsView.swift:807,808,809
@Composable
fun HoldPagePicker(page: Int, set: (Int) -> Unit) {
    // ios: Views/HoldingsView.swift:810,823,824
    Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(13.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/HoldingsView.swift:811
        listOf(0 to "내 종목", 1 to "소식판").forEach { (i, t) ->
            val on = page == i   // ios: Views/HoldingsView.swift:812
            // 애니메이션 없이: 내 종목 화면이 커서 바꿀 때 버벅인다
            // ios: Views/HoldingsView.swift:813,814,815,816,817,819,820
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
fun HoldingsNews(m: AppModel, moversOnly: Boolean = false) {   // ios: Views/HoldingsView.swift:830,831,832,834 홈 아래: 오늘 많이 움직인 종목만
    val rows = m.rows   // ios: Views/HoldingsView.swift:835
    if (moversOnly) { MoversCard(m, rows); return }   // ios: Views/HoldingsView.swift:836
    val stamp = Market.shared.updatedAt
    val ks = remember(rows, stamp) { rows.filter { m.prices.hasHistory(it.sym) }.map { Pair(it, m.metrics(it)) } }   // ios: Views/HoldingsView.swift:839,840
    // ios: Views/HoldingsView.swift:841
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        compare(m, ks)?.let { CompareCard(it) }   // ios: Views/HoldingsView.swift:842
        TownButton { TownView(m, it) }   // ios: Views/HoldingsView.swift:843 시장과 비교 밑 [동네]: 전체 화면으로
        MoversCard(m, rows)   // ios: Views/HoldingsView.swift:844
        NearCard(m, ks)   // ios: Views/HoldingsView.swift:845
    }
}

// MARK: 시장과 비교 (최근 1달). 시장 = 대표 ETF: 미국 종목이 절반 넘으면 S&P500(SPY), 아니면 전 세계(VT). 기록이 없으면 안 보인다
// ios: Views/HoldingsView.swift:842
private class Cmp(val mine: Double, val mkt: Double, val name: String)
// ios: Views/HoldingsView.swift:843
private fun compare(m: AppModel, ks: List<Pair<AppModel.Row, AppModel.HoldMetrics>>): Cmp? {
    val w = ks.fold(0.0) { a, k -> a + k.second.w }   // ios: Views/HoldingsView.swift:844
    if (w <= 0) return null   // ios: Views/HoldingsView.swift:845
    // 지금 비중으로 4주 전부터 가졌다면 (yr: 1년을 52주로 나눈 가격)
    val mine = ks.fold(0.0) { a, k -> val y = k.second.yr; a + k.second.w / w * (y[52] / max(1e-9, y[48]) - 1) }   // ios: Views/HoldingsView.swift:847
    val usd = m.rows.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value } / max(1.0, m.rows.fold(0.0) { a, r -> a + r.value })   // ios: Views/HoldingsView.swift:848
    // ios: Views/HoldingsView.swift:849
    for ((id, name) in if (usd >= 0.5) listOf("SPY" to "S&P500 ETF") else listOf("VT" to "전 세계 ETF", "SPY" to "S&P500 ETF")) {
        val c = PriceHistory.of(id)?.closes; if (c == null || c.size <= 21) continue   // ios: Views/HoldingsView.swift:850
        return Cmp(mine = mine, mkt = c[c.size - 1] / c[c.size - 21] - 1, name = name)   // ios: Views/HoldingsView.swift:851
    }
    return null   // ios: Views/HoldingsView.swift:853
}

// ios: Views/HoldingsView.swift:856
@Composable
private fun CompareCard(c: Cmp) {
    val gap = c.mine - c.mkt   // ios: Views/HoldingsView.swift:857
    // ios: Views/HoldingsView.swift:858,867
    Column(Modifier.newsCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("최근 1달, 시장과 비교", style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:859
        // ios: Views/HoldingsView.swift:860
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cell("내 종목", c.mine)   // ios: Views/HoldingsView.swift:861
            Cell(c.name, c.mkt)   // ios: Views/HoldingsView.swift:862
        }
        // Views/HoldingsView.swift:837 (검사 예외: 같은 글로 옮김)
        val pct = cformat("%.1f", abs(gap) * 100); val dir = if (gap > 0) "높아요" else "낮아요"
        T(if (abs(gap) < 0.001) "시장과 거의 같게 움직였어요" else "시장보다 $pct% $dir",
          style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:865
    }
}

// ios: Views/HoldingsView.swift:870
@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(t: String, v: Double) {
    // ios: Views/HoldingsView.swift:871,875,876,877
    Column(Modifier.weight(1f).background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(t, style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/HoldingsView.swift:872
        T(AppModel.sgn(v), style = appFont(20f, FontWeight.Bold).tnum(), color = Theme.change(v))   // ios: Views/HoldingsView.swift:873
    }
}

// MARK: 오늘 많이 움직인 종목 (오늘 % 크기순 3개)
// ios: Views/HoldingsView.swift:881
@Composable
private fun MoversCard(m: AppModel, rows: List<AppModel.Row>) {
    val top = rows.sortedByDescending { abs(it.sym.quote.change) }.take(3)   // ios: Views/HoldingsView.swift:882
    // ios: Views/HoldingsView.swift:883,891
    Column(Modifier.newsCard(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        T("오늘 많이 움직인 종목", Modifier.padding(bottom = 6.dp), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:884
        if (top.isEmpty()) T("종목을 넣으면 보여요", style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:885
        // ios: Views/HoldingsView.swift:886
        top.forEach { r ->
            val d = r.sym.quote.change   // ios: Views/HoldingsView.swift:887
            Line(m, r, (if (d >= 0) "+" else "−") + cformat("%.2f%%", abs(d) * 100), Theme.change(d))   // ios: Views/HoldingsView.swift:888
        }
    }
}

// MARK: 1년 고점·저점 가까운 종목 (5% 안)
// ios: Views/HoldingsView.swift:895
@Composable
private fun NearCard(m: AppModel, ks: List<Pair<AppModel.Row, AppModel.HoldMetrics>>) {
    val hi = ks.filter { it.second.dd > -0.05 }.sortedByDescending { it.second.dd }   // ios: Views/HoldingsView.swift:896
    val lo = ks.filter { it.second.dd <= -0.05 && it.first.sym.last / max(1e-9, it.second.lo) - 1 < 0.05 }   // ios: Views/HoldingsView.swift:897
    // ios: Views/HoldingsView.swift:898,908
    Column(Modifier.newsCard(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        T("1년 고점·저점 가까이", Modifier.padding(bottom = 6.dp), style = appFont(15f, FontWeight.Bold))   // ios: Views/HoldingsView.swift:899
        if (hi.isEmpty() && lo.isEmpty()) T("고점·저점 5% 안에 있는 종목은 없어요", style = appFont(13f), color = Theme.sub)   // ios: Views/HoldingsView.swift:900
        // ios: Views/HoldingsView.swift:901
        hi.forEach { (r, k) ->
            // Views/HoldingsView.swift:875 (검사 예외: 같은 글로 옮김)
            val p = cformat("%.0f", abs(k.dd) * 100)
            Line(m, r, if (k.dd > -0.005) "1년 고점" else "고점까지 $p%", Theme.up)
        }
        // ios: Views/HoldingsView.swift:904
        lo.forEach { (r, k) ->
            // Views/HoldingsView.swift:878 (검사 예외: 같은 글로 옮김)
            val p = cformat("%.0f", (r.sym.last / max(1e-9, k.lo) - 1) * 100)
            Line(m, r, "저점 위 $p%", Theme.down)
        }
    }
}

// ios: Views/HoldingsView.swift:911
@Composable
private fun Line(m: AppModel, r: AppModel.Row, right: String, color: Color) {
    // ios: Views/HoldingsView.swift:912,913,919,921
    Row(Modifier.fillMaxWidth().plainClickable { m.holdPath = m.holdPath + r.sym }.defaultMinSize(minHeight = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(symbol = r.id, size = 28.dp)   // ios: Views/HoldingsView.swift:914
        T(r.sym.name, Modifier.weight(1f).padding(end = 4.dp), style = appFont(14f, FontWeight.SemiBold), maxLines = 1)   // ios: Views/HoldingsView.swift:915,916
        T(right, style = appFont(14f, FontWeight.Bold).tnum(), color = color)   // ios: Views/HoldingsView.swift:917
    }
}

// ios: Views/HoldingsView.swift:925,926,927,928,929
@Composable
private fun Modifier.newsCard(): Modifier = this.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(16.dp)
