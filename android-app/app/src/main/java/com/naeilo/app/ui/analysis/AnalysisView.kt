package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.Lens
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.core.calc.SiteCalc
import com.naeilo.app.core.calc.calmSpeaker
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keyName
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.data.Currency
import com.naeilo.app.net.Market
import com.naeilo.app.pending.CalmView
import com.naeilo.app.pending.CharHead
import com.naeilo.app.pending.GoalSetView
import com.naeilo.app.pending.MarketEvents
import com.naeilo.app.pending.SubLocked
import com.naeilo.app.pending.Support
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.NavBar
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.ToggleRow
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.home.SampleNote
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.stiffness
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

// ios: Views/AnalysisView.swift:3
enum class AnalysisRoute { forecast, myPath, rateScen, external, dividend, fx, glance, calm }

/** 분석 상세 화면을 좌우로 넘겨 바로 옆 분석으로 간다 (분석 탭 목록 순서). 넘기기 화면 안인지는 LocalDetailInPager */
// ios: Views/AnalysisView.swift:23,24,57
@Composable
fun AnalysisPager(m: AppModel, start: AnalysisRoute) {
    val order = AnalysisPagerRoutes.order
    val ps = rememberPagerState(initialPage = order.indexOf(start)) { order.size }   // ios: Views/AnalysisView.swift:25,28
    val sel = order[ps.currentPage]
    val nav = LocalNavBack.current

    // ios: Views/AnalysisView.swift:30,31
    fun title(r: AnalysisRoute): String = when (r) {
        AnalysisRoute.calm -> "내 투자 습관"   // ios: Views/AnalysisView.swift:32
        AnalysisRoute.myPath -> "내 길"   // ios: Views/AnalysisView.swift:33
        AnalysisRoute.forecast -> m.forecastTitle   // ios: Views/AnalysisView.swift:34
        AnalysisRoute.rateScen -> "금리 충격 테스트"   // ios: Views/AnalysisView.swift:35
        AnalysisRoute.external -> "외부 요인"   // ios: Views/AnalysisView.swift:36
        AnalysisRoute.dividend -> "배당·세금"   // ios: Views/AnalysisView.swift:37
        AnalysisRoute.fx -> "환율 영향"   // ios: Views/AnalysisView.swift:38
        AnalysisRoute.glance -> "종목 한눈에"   // ios: Views/AnalysisView.swift:39
    }

    // ios: Views/AnalysisView.swift:43,44
    @Composable
    fun Page(r: AnalysisRoute) = when (r) {
        // 7일 무료 뒤 구독하지 않으면 목표(내 길)·확률 전망만 잠근다 (Support.pro)
        AnalysisRoute.forecast -> if (Support.shared.pro) ForecastView(m) else SubLocked(what = "확률 전망")   // ios: Views/AnalysisView.swift:46
        AnalysisRoute.myPath -> if (Support.shared.pro) MyPathView(m) else SubLocked(what = "내 길(목표까지 확률)")   // ios: Views/AnalysisView.swift:47
        AnalysisRoute.rateScen -> RateScenarioScreen(m)   // ios: Views/AnalysisView.swift:48
        AnalysisRoute.external -> ExternalScreen(m)   // ios: Views/AnalysisView.swift:49
        AnalysisRoute.dividend -> DividendScreen(m)   // ios: Views/AnalysisView.swift:50
        AnalysisRoute.fx -> FxImpactView(m)   // ios: Views/AnalysisView.swift:51
        AnalysisRoute.glance -> GlanceView(m)   // ios: Views/AnalysisView.swift:52
        AnalysisRoute.calm -> CalmView()   // ios: Views/AnalysisView.swift:53
    }

    // ios: Views/AnalysisView.swift:64
    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        // 제목 밑 점: 지금 몇 번째 분석인지, 좌우로 넘길 수 있다는 표시
        // ios: Views/AnalysisView.swift:65,66,67,69,70,78,79
        NavBar(title(sel), back = nav?.back, center = {
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${title(sel)}, ${order.indexOf(sel) + 1} / ${order.size}, 좌우로 넘기면 다른 분석" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // ios: Views/AnalysisView.swift:82 .animation(.easeInOut(duration: 0.2), value: sel)
                AnimatedContent(title(sel), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "t") { t ->
                    T(t, style = appFont(16f, FontWeight.Bold), maxLines = 1)   // ios: Views/AnalysisView.swift:71
                }
                // ios: Views/AnalysisView.swift:72,73,74
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    order.forEach { r -> Box(Modifier.size(5.dp, 5.dp).background(if (r == sel) Theme.teal else Theme.border, CircleShape)) }
                }
            }
        }) { nav?.pop?.invoke() }
        // ios: Views/AnalysisView.swift:58,59,61,62,63
        CompositionLocalProvider(LocalDetailInPager provides true) {
            HorizontalPager(ps, Modifier.weight(1f), beyondViewportPageCount = 0, key = { order[it] }) { i -> Box(Modifier.fillMaxSize()) { Page(order[i]) } }
        }
    }
}
object AnalysisPagerRoutes {
    // ios: Views/AnalysisView.swift:26 허브 순서와 같게
    val order: List<AnalysisRoute> = listOf(AnalysisRoute.calm, AnalysisRoute.myPath, AnalysisRoute.forecast, AnalysisRoute.rateScen, AnalysisRoute.external, AnalysisRoute.fx, AnalysisRoute.glance, AnalysisRoute.dividend)
}

// 분석 탭 허브 (시안 B · 아이콘 + 한 줄 결과): 습관 띠, 결론 카드, 결과 행 8개.
// 행의 값은 그 상세 화면 첫 문장의 숫자와 같은 식 (허브 전용 숫자를 따로 계산하지 않는다)
object AnalysisHub {
    /** 허브 색은 의미 3개만: 초록 들어오는 돈·좋아짐 / 주황 나가는 돈·늦어짐 / 회색 정보 */
    val good: Color @Composable get() = hexD(0x2C5A54, 0x8FD0C4)   // ios: Views/AnalysisView.swift:92
    val warn: Color @Composable get() = hexD(0xA8521A, 0xE8A060)   // ios: Views/AnalysisView.swift:93
    enum class Tone { good, warn, info }   // ios: Views/AnalysisView.swift:94

    // ios: Views/AnalysisView.swift:96
    class Line(val icon: String, val tone: Tone, val label: String, val value: String, val valueTone: Tone? = null, val donut: Double? = null, val route: AnalysisRoute)

    /** 확률을 빈도로: 3번 중 1번처럼 */
    // ios: Views/AnalysisView.swift:99
    fun freq(p: Double): String {
        for ((a, b) in listOf(1 to 2, 1 to 3, 2 to 3, 1 to 4, 3 to 4)) if (abs(p - a.toDouble() / b.toDouble()) < 0.03) return "${b}번 중 ${a}번"   // ios: Views/AnalysisView.swift:100
        val n = (p * 10).swRound().toInt()   // ios: Views/AnalysisView.swift:101
        return if (n <= 0) "10번 중 1번 미만" else if (n >= 10) "10번 중 거의 10번" else "10번 중 ${n}번"   // ios: Views/AnalysisView.swift:102
    }
}

// ios: Views/AnalysisView.swift:105
private fun name(r: AppModel.Row): String = if (r.sym.name.isEmpty()) r.id else r.sym.name

// ios: Views/AnalysisView.swift:89,90,107
@Composable
fun AnalysisView(m: AppModel) {
    // ios: Views/AnalysisView.swift:120,121
    LaunchedEffect(Unit) { MacroData.shared.load() }
    LaunchedEffect(Unit) { SiteCalc.shared.refresh() }
    // 당기면 시세는 새로, 계산·금리 자료는 아직 신선하면 다시 받지 않는다
    // ios: Views/AnalysisView.swift:108,118,119,123,124,125,126
    Refreshable({ Market.shared.pullRefresh(m); MacroData.shared.load(); SiteCalc.shared.refresh() }, Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ios: Views/AnalysisView.swift:116
            Screen {
                // ios: Views/AnalysisView.swift:109
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.outset(16.dp)) { AppHeader(tab = "분석") }   // ios: Views/AnalysisView.swift:110 (-16)
                    SampleNote(m)   // ios: Views/AnalysisView.swift:111
                    HabitBand(m)   // ios: Views/AnalysisView.swift:112
                    Box(Modifier.padding(top = 6.dp)) { Summary(m) }   // ios: Views/AnalysisView.swift:113
                    lines(m).forEach { HubRow(m, it) }   // ios: Views/AnalysisView.swift:114
                }
            }
        }
    }
}

// MARK: 내 투자 습관 띠 (위젯처럼 성격이 다른 영역, 대사는 상세에만)
// ios: Views/AnalysisView.swift:132
@Composable
private fun HabitBand(m: AppModel) {
    val s = m.calm.stats(); val done = s.n >= Calm.minEvents   // ios: Views/AnalysisView.swift:133
    // ios: Views/AnalysisView.swift:156
    LaunchedEffect(Unit) { if (m.calm.type != null && m.calm.ladder?.type != m.calm.type) m.calm.climb() }
    // ios: Views/AnalysisView.swift:134,151,152,153,154,155
    Row(
        Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(16.dp)).openOnTap(m, AnalysisRoute.calm)
            .semantics(mergeDescendants = true) { contentDescription = if (done) "내 투자 습관, 지수 ${s.index}" else "내 투자 습관, 기록 ${s.n} / ${Calm.minEvents}" }
            .defaultMinSize(minHeight = 44.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        CharHead(id = m.calmSpeaker, size = 32.dp)   // ios: Views/AnalysisView.swift:135
        // ios: Views/AnalysisView.swift:136
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ios: Views/AnalysisView.swift:137,139
            Row(Modifier.fillMaxWidth()) {
                T("투자 습관", Modifier.weight(1f).alignByBaseline(), style = appFont(14f, FontWeight.Bold), color = Theme.mint)   // ios: Views/AnalysisView.swift:138
                T(if (done) "지수 ${s.index}" else "${s.n}/${Calm.minEvents}", Modifier.alignByBaseline(), style = appFont(13f), color = hex(0xC3C8D0))   // ios: Views/AnalysisView.swift:140
            }
            // ios: Views/AnalysisView.swift:142,143,144,145,148
            Box(Modifier.fillMaxWidth().height(4.dp).background(hex(0x343A47), CircleShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(min(1.0, s.n.toDouble() / Calm.minEvents.toDouble()).toFloat()).background(Theme.mint, CircleShape))
            }
        }
    }
}

// MARK: 결론 카드
// ios: Views/AnalysisView.swift:160
@Composable
private fun Summary(m: AppModel) {
    val K = m.keyValue; val now = m.trackValue; val r = if (K > 0) now / K else 0.0; val pct = (r * 100).swRound().toInt()   // ios: Views/AnalysisView.swift:161
    val head = if (r >= 1) "${m.keyName} 도달" else if (pct in 45..55) "${m.keyName}까지 절반" else "${m.keyName}까지 ${pct}%"   // Views/AnalysisView.swift:162 (검사 예외: 같은 글)
    val end = ForecastView.ym(m.forecastHorizon).second   // ios: Views/AnalysisView.swift:163
    // ios: Views/AnalysisView.swift:164,178,179,180
    Column(
        Modifier.fillMaxWidth().background(hex(0x2C5A54), RoundedCornerShape(22.dp)).semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        T(head, style = appFont(24f, FontWeight.Bold), color = Color.White, roll = head)   // ios: Views/AnalysisView.swift:165
        // ios: Views/AnalysisView.swift:166
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ios: Views/AnalysisView.swift:167,168,169,170,173
            Box(Modifier.fillMaxWidth().height(10.dp).background(hex(0x46736C), CircleShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(min(1.0, max(0.0, r)).toFloat()).background(hex(0xA9D6CB), CircleShape))
            }
            // ios: Views/AnalysisView.swift:174,175
            Row(Modifier.fillMaxWidth()) { T(AppModel.eok(now), Modifier.weight(1f), style = appFont(14f), color = hex(0xDCEDE9)); T("${AppModel.eok(K)} · $end", style = appFont(14f), color = hex(0xDCEDE9)) }
        }
    }
}

// MARK: 결과 행
// ios: Views/AnalysisView.swift:184
@Composable
private fun color(t: AnalysisHub.Tone): Color = if (t == AnalysisHub.Tone.good) AnalysisHub.good else if (t == AnalysisHub.Tone.warn) AnalysisHub.warn else Theme.ink

// ios: Views/AnalysisView.swift:186
@Composable
private fun HubRow(m: AppModel, l: AnalysisHub.Line) {
    val bg: Color = if (l.tone == AnalysisHub.Tone.good) hexD(0xE3F0ED, 0x173531) else if (l.tone == AnalysisHub.Tone.warn) hexD(0xFBEBDD, 0x3A2A1C) else Theme.line   // ios: Views/AnalysisView.swift:187
    val fg: Color = if (l.tone == AnalysisHub.Tone.good) AnalysisHub.good else if (l.tone == AnalysisHub.Tone.warn) AnalysisHub.warn else Theme.sub   // ios: Views/AnalysisView.swift:188
    // ios: Views/AnalysisView.swift:189,206,207,208,209
    Row(
        Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).openOnTap(m, l.route).semantics(mergeDescendants = true) {}
            .defaultMinSize(minHeight = 72.dp).padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        // ios: Views/AnalysisView.swift:190,201
        Box(Modifier.size(44.dp, 44.dp).background(bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            val d = l.donut
            // ios: Views/AnalysisView.swift:191
            if (d != null) {
                val border = Theme.border; val good = AnalysisHub.good
                // ios: Views/AnalysisView.swift:192,193,194,196 (0 에서 d 까지 잘라 -90도 돌림)
                Canvas(Modifier.size(22.dp, 22.dp)) {
                    drawCircle(border, (size.minDimension - 5.dp.toPx()) / 2, style = Stroke(5.dp.toPx()))
                    val from = 0.0
                    drawArc(good, -90f, (360 * (d - from)).toFloat(), false, topLeft = Offset(2.5.dp.toPx(), 2.5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 5.dp.toPx(), size.height - 5.dp.toPx()), style = Stroke(5.dp.toPx()))
                }
            } else {   // ios: Views/AnalysisView.swift:197
                Sym(l.icon, 18f, fg, scaled = false)   // ios: Views/AnalysisView.swift:198
            }
        }
        T(l.label, Modifier.weight(1f).padding(end = 6.dp), style = appFont(15f), color = Theme.sub, maxLines = 1)   // ios: Views/AnalysisView.swift:202,203
        FitText(l.value, appFont(18f, FontWeight.Bold), 0.7f, maxLines = 1, color = color(l.valueTone ?: AnalysisHub.Tone.info), roll = l.value)   // ios: Views/AnalysisView.swift:204
    }
}

// ios: Views/AnalysisView.swift:212
private fun lines(m: AppModel): List<AnalysisHub.Line> {
    val fc = m.forecast; val tot = max(1.0, m.total)   // ios: Views/AnalysisView.swift:213
    val out = ArrayList<AnalysisHub.Line>()   // ios: Views/AnalysisView.swift:214
    val G = AnalysisHub.Tone.good; val W = AnalysisHub.Tone.warn; val I = AnalysisHub.Tone.info
    // 내 길: 목표일에 목표 이상일 확률을 빈도로 (7일 무료 뒤 구독하지 않으면 잠금)
    val pro = Support.shared.pro   // ios: Views/AnalysisView.swift:216
    // ios: Views/AnalysisView.swift:217
    if (!pro) {
        out.add(AnalysisHub.Line(icon = "lock.fill", tone = I, label = "내 길", value = "구독 시 열림", route = AnalysisRoute.myPath))   // ios: Views/AnalysisView.swift:218
    } else if (m.isGoal) {   // ios: Views/AnalysisView.swift:219
        out.add(AnalysisHub.Line(icon = "flag", tone = G, label = "내 길", value = if (fc.exact) AnalysisHub.freq(fc.pGoal) else "계산 중", valueTone = G, route = AnalysisRoute.myPath))   // ios: Views/AnalysisView.swift:220
    } else {   // ios: Views/AnalysisView.swift:221
        val gap = m.myPathGapToday   // ios: Views/AnalysisView.swift:222
        out.add(AnalysisHub.Line(icon = "flag", tone = G, label = "내 길", value = cformat("%.1f%% ", abs(gap) * 100) + (if (gap >= 0) "앞섬" else "뒤처짐"), valueTone = if (gap >= 0) G else W, route = AnalysisRoute.myPath))   // ios: Views/AnalysisView.swift:223
    }
    // 전망: 상세 첫 카드처럼 경로 값에 현금을 더한다
    val med = (fc.q50.lastOrNull() ?: 0.0) + (if (fc.exact) m.cashKrw else 0.0)   // ios: Views/AnalysisView.swift:226
    val endS = ForecastView.ym(m.forecastHorizon).second
    // Views/AnalysisView.swift:227 (검사 예외: 같은 글)
    out.add(AnalysisHub.Line(icon = if (pro) "calendar" else "lock.fill", tone = if (pro) G else I, label = if (m.isGoal) "$endS 전망" else "3년 뒤 전망",
                             value = if (pro) AppModel.eok(med) else "구독 시 열림", route = AnalysisRoute.forecast))   // ios: Views/AnalysisView.swift:228
    // 금리 충격: 기본 프리셋("작년만큼 또 오른다")의 도착 시점 변화
    val t = RateScenarioView.hubTiming(m)   // ios: Views/AnalysisView.swift:230
    val late = t?.endsWith("늦게") == true; val early = t?.endsWith("빨리") == true   // ios: Views/AnalysisView.swift:231
    // ios: Views/AnalysisView.swift:232,233,234
    out.add(AnalysisHub.Line(icon = "chart.line.uptrend.xyaxis", tone = W, label = "금리 오르면",
                             value = t?.replace("약 ", "") ?: (if (MacroData.shared.failed) "자료 없음" else "계산 중"),
                             valueTone = if (late) W else if (early) G else I, route = AnalysisRoute.rateScen))
    // 외부 요인: 가장 가까운 발표 일정 (없으면 시장 −10% 일 때)
    val e = ExternalView.hubNext(m)
    // ios: Views/AnalysisView.swift:236
    if (e != null) {
        val n = Day.dday(e.day)   // ios: Views/AnalysisView.swift:237
        out.add(AnalysisHub.Line(icon = "building.columns", tone = I, label = e.kind.replace(" 결정", "") + " " + (if (n == 0) "오늘" else "D-$n"), value = "±" + AppModel.man(e.amt), route = AnalysisRoute.external))   // ios: Views/AnalysisView.swift:238
    } else {   // ios: Views/AnalysisView.swift:239
        val mkt10 = m.rows.fold(0.0) { a, r -> a + r.value * (ExternalView.mktBeta(r.id)?.let { SiteCalc.factorMove("mkt", beta = it, shock = -10.0) } ?: 0.0) }   // ios: Views/AnalysisView.swift:240
        out.add(AnalysisHub.Line(icon = "building.columns", tone = I, label = "시장 −10%면", value = AppModel.man(mkt10), valueTone = W, route = AnalysisRoute.external))   // ios: Views/AnalysisView.swift:241
    }
    // 환율: 상세 첫 문장과 같은 per10 = 달러 평가액 × 10 / 기준 환율
    val usdV = m.rows.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value }   // ios: Views/AnalysisView.swift:244
    // ios: Views/AnalysisView.swift:245,246
    out.add(AnalysisHub.Line(icon = "arrow.left.arrow.right", tone = I, label = "환율 +10원",
                             value = if (usdV > 0) "+" + AppModel.man(usdV * 10 / max(1.0, Market.shared.valFx)) else "그대로", route = AnalysisRoute.fx))
    // 종목 한눈에: 비중 1위
    val top = m.rows.maxByOrNull { it.value }   // ios: Views/AnalysisView.swift:248
    // ios: Views/AnalysisView.swift:249,250
    out.add(AnalysisHub.Line(icon = "chart.pie", tone = I, label = "종목 한눈에", value = top?.let { "${name(it)} ${AppModel.pct(it.value / tot)}" } ?: "종목 없음",
                             donut = top?.let { it.value / tot }, route = AnalysisRoute.glance))
    // 배당·세금: 두 줄로 따로, 한 숫자로 합치지 않는다
    val mo = DividendView.hubMonthly(m); val tax = DividendView.hubTax(m)   // ios: Views/AnalysisView.swift:252
    out.add(AnalysisHub.Line(icon = "arrow.down.to.line", tone = G, label = "배당 · 월", value = if (mo > 0) "+" + AppModel.wonRead(mo) else "없음", valueTone = if (mo > 0) G else I, route = AnalysisRoute.dividend))   // ios: Views/AnalysisView.swift:253
    out.add(AnalysisHub.Line(icon = "arrow.up.to.line", tone = W, label = "세금 · 내년 5월", value = if (tax > 0) "−" + AppModel.man(tax) else "없음", valueTone = if (tax > 0) W else I, route = AnalysisRoute.dividend))   // ios: Views/AnalysisView.swift:254
    return out   // ios: Views/AnalysisView.swift:255
}

/** 분석 허브 행: 누르면 그 분석 (기본 이동 링크) */
// ios: Views/AnalysisView.swift:260,261,262,263
@Composable
fun Modifier.openOnTap(m: AppModel, route: AnalysisRoute): Modifier = this.plainClickable { m.analysisPath = m.analysisPath + route }

// 목표일 전망: 시장이 어떻게 가느냐에 따라 목표일 자산이 얼마나 갈리나. 목표 판정은 내 목표(내 길) 화면 몫
// ios: Views/AnalysisView.swift:291,292,295,296
@Composable
fun ForecastView(m: AppModel) {
    var open by remember { mutableStateOf(false) }   // ios: Views/AnalysisView.swift:293
    val fs = Lens.entries.associateWith { m.forecast(it) }   // ios: Views/AnalysisView.swift:297 (같은 경로를 μ만 바꿔 다시 무게 달기: 엔진이 딴 줄에서 하고 결과를 둔다)
    val sel = m.lens; val f = fs[sel] ?: m.forecast   // ios: Views/AnalysisView.swift:298
    // 몬테카를로 경로는 종목만이라 현금을 더해 지금 평가액과 같은 기준으로 (닫힌 식은 이미 포함)
    val add = { x: AppModel.Forecast -> if (x.exact) m.cashKrw else 0.0 }   // ios: Views/AnalysisView.swift:300
    val now = (f.q50.firstOrNull() ?: 0.0) + add(f)   // ios: Views/AnalysisView.swift:301
    val med = (f.q50.lastOrNull() ?: 0.0) + add(f); val lo = (f.q5.lastOrNull() ?: 0.0) + add(f); val hi = (f.q95.lastOrNull() ?: 0.0) + add(f)   // ios: Views/AnalysisView.swift:302
    val chg = if (now > 0) med / now - 1 else 0.0   // ios: Views/AnalysisView.swift:303
    val end = ForecastView.ym(m.forecastHorizon)   // ios: Views/AnalysisView.swift:304
    val others: List<Pair<String, List<Double>>> = ForecastView.others(fs, sel = sel, add = add)   // ios: Views/AnalysisView.swift:305

    // ios: Views/AnalysisView.swift:306,346,347,349,350
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        // ios: Views/AnalysisView.swift:307,345
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailHead(title = m.forecastTitle, sub = if (f.exact) "" else "계산 중 (어림값)")   // ios: Views/AnalysisView.swift:308
            // Views/AnalysisView.swift:309 (검사 예외: 같은 글)
            T(if (m.isGoal) "${end.second} 내 자산은?" else "3년 뒤 내 자산은?",
              style = appFont(20f, FontWeight.Bold), color = Theme.ink)   // ios: Views/AnalysisView.swift:310

            // ios: Views/AnalysisView.swift:312
            Card {
                Segments(m)   // ios: Views/AnalysisView.swift:313
                TrustSlider(m)   // ios: Views/AnalysisView.swift:314
                // ios: Views/AnalysisView.swift:315
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T("그럴듯한 금액 · 연 ${AppModel.sgn0(f.mu)}", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisView.swift:316
                    // ios: Views/AnalysisView.swift:317
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ios: Views/AnalysisView.swift:318,319 (.font(.system(size: 28)): 큰 글자 설정을 따르지 않는다)
                        FitText(ForecastView.amt(med), TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), 0.7f, Modifier.weight(1f, fill = false).alignByBaseline(), maxLines = 1, color = Theme.ink, roll = ForecastView.amt(med))
                        T(AppModel.sgn0(chg), Modifier.alignByBaseline(), style = appFont(13f, FontWeight.Bold), color = if (chg >= 0) Theme.green else Theme.up)   // ios: Views/AnalysisView.swift:320
                    }
                    T("10번 중 9번 ${ForecastView.amt(lo)} ~ ${ForecastView.amt(hi)}", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisView.swift:322
                }
                // ios: Views/AnalysisView.swift:324,325,326
                ScenarioChart(sel = f, selShift = add(f), selLabel = ForecastView.short(sel), others = others,
                              goal = m.keyValue, goalLabel = m.keyName, endLabel = if (m.isGoal) end.second else "3년 뒤", modifier = Modifier.height(190.dp))
                Legend()   // ios: Views/AnalysisView.swift:327
                // ios: Views/AnalysisView.swift:328
                if (f.mu > 0.30) {
                    // ios: Views/AnalysisView.swift:329,330,331
                    T("연 ${AppModel.sgn0(f.mu)}는 드문 속도 · 실제론 더 낮을 수도", Modifier.fillMaxWidth().background(Theme.cream, RoundedCornerShape(10.dp)).padding(10.dp),
                      style = appFont(12f), color = Theme.ink)
                }
            }

            Table(m, fs, now = now, add = add)   // ios: Views/AnalysisView.swift:335
            Schedule(m)   // ios: Views/AnalysisView.swift:336
            Details(m, f, open) { open = it }   // ios: Views/AnalysisView.swift:337

            // ios: Views/AnalysisView.swift:339,340
            T("지난 3년 기준 3,000가지 경우", style = appFont(12f), color = Theme.muted)
            // ios: Views/AnalysisView.swift:341,342
            T("구성 바꾸기 → 목표 고치기", Modifier.plainClickable { m.analysisPath = m.analysisPath + GoalRoute0 }, style = appFont(14f, FontWeight.Bold), color = Theme.teal)
        }
    }
}

/** 전망에서 여는 목표 고치기 (NavigationLink { GoalSetView(fromSettings: true) }) */
object GoalRoute0
@Composable
fun GoalSetPage() = GoalSetView(fromSettings = true)

// MARK: 시나리오 버튼 3개 + 추세 슬라이더 (같은 축: 신중하게 = 0%, 지난 3년처럼 = 100%)
// ios: Views/AnalysisView.swift:354
@Composable
private fun Segments(m: AppModel) {
    // ios: Views/AnalysisView.swift:355,367,368
    Row(Modifier.fillMaxWidth().background(Theme.bg, RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // ios: Views/AnalysisView.swift:356
        Lens.entries.forEach { l ->
            val on = m.lens == l   // ios: Views/AnalysisView.swift:357
            // ios: Views/AnalysisView.swift:358,359,360,361,363,364
            Box(Modifier.weight(1f).plainClickable { m.lens = l }.semantics { selected = on }.background(if (on) Theme.teal else Color.Transparent, RoundedCornerShape(9.dp)).padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                T(l.label, style = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) Color.White else Theme.sub, maxLines = 1)
            }
        }
    }
}

/** 슬라이더에 보이는 값. 버튼을 골라도 '내 설정' 값(m.trust)은 그대로 기억한다 */
// ios: Views/AnalysisView.swift:372
private val AppModel.shownTrust: Double get() = if (lens == Lens.base) 0.0 else if (lens == Lens.smooth) 100.0 else trust

// ios: Views/AnalysisView.swift:374
@Composable
private fun TrustSlider(m: AppModel) {
    // ios: Views/AnalysisView.swift:375,376
    val set = { v: Double -> if (v <= 0) m.lens = Lens.base else if (v >= 100) m.lens = Lens.smooth else { m.lens = Lens.mine; m.trust = v } }
    // ios: Views/AnalysisView.swift:378
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // ios: Views/AnalysisView.swift:379,381
        Row(Modifier.fillMaxWidth()) {
            T("지난 3년 추세 반영", Modifier.weight(1f), style = appFont(13f, FontWeight.Bold))   // ios: Views/AnalysisView.swift:380
            T("${m.shownTrust.toInt()}%", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/AnalysisView.swift:382
        }
        // ios: Views/AnalysisView.swift:384 (0~100, 5씩)
        IosSlider(m.shownTrust, set, 0.0..100.0, step = 5.0, modifier = Modifier.semantics { contentDescription = "지난 3년 추세를 얼마나 믿나요" }, tint = Theme.teal)
        // ios: Views/AnalysisView.swift:385,386
        Row(Modifier.fillMaxWidth()) {
            T("신중", style = appFont(11f), color = Theme.muted); Spacer(Modifier.weight(1f)); T("반반", style = appFont(11f), color = Theme.muted); Spacer(Modifier.weight(1f)); T("3년처럼", style = appFont(11f), color = Theme.muted)
        }
    }
}

// ios: Views/AnalysisView.swift:390
@Composable
private fun Legend() {
    val muted = Theme.muted
    // ios: Views/AnalysisView.swift:391
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Sw(Theme.teal.copy(alpha = 0.35f), "10번 중 5번")   // ios: Views/AnalysisView.swift:392
        Sw(Theme.teal.copy(alpha = 0.14f), "10번 중 9번")   // ios: Views/AnalysisView.swift:393
        // ios: Views/AnalysisView.swift:394
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/AnalysisView.swift:395,396
            Canvas(Modifier.size(14.dp, 10.dp)) { drawLine(muted, Offset(0f, 5.dp.toPx()), Offset(14.dp.toPx(), 5.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) }
            T("다른 시나리오", style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisView.swift:397,400
        }
    }
}
// ios: Views/AnalysisView.swift:402,403
@Composable
private fun Sw(c: Color, t: String) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp, 10.dp).background(c, RoundedCornerShape(2.dp))); T(t, style = appFont(11f), color = Theme.sub)
}

// MARK: 세 가지 미래 한눈에
// ios: Views/AnalysisView.swift:407
@Composable
private fun Table(m: AppModel, fs: Map<Lens, AppModel.Forecast>, now: Double, add: (AppModel.Forecast) -> Double) {
    val lineName = if (m.isGoal) ForecastView.amt(m.keyValue) else "본전"   // ios: Views/AnalysisView.swift:408
    val bads = Lens.entries.map { l -> fs[l]?.let { (it.q5.lastOrNull() ?: 0.0) + add(it) } ?: now }   // ios: Views/AnalysisView.swift:409
    val below = bads.count { it < now }   // ios: Views/AnalysisView.swift:410
    val nowS = ForecastView.amt(now)
    // Views/AnalysisView.swift:411,412,413 (검사 예외: 같은 글)
    val msg = if (below == bads.size) "운 나쁘면 지금($nowS)보다 줄 수도"
        else if (below > 0) "신중하게 보면 지금($nowS)보다 줄 수도"
        else "운 나빠도 지금($nowS)보다 많을 가능성 큼"
    // ios: Views/AnalysisView.swift:414
    Card {
        T("세 가지 미래", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisView.swift:415
        // ios: Views/AnalysisView.swift:416,417,418,419,420,422
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            T("", Modifier.weight(1f), style = appFont(11f), color = Theme.muted)
            T("보통", Modifier.width(70.dp), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.muted)
            T("나쁘면", Modifier.width(58.dp), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.muted)
            T("$lineName↑", Modifier.width(78.dp), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.muted)
        }
        // ios: Views/AnalysisView.swift:423
        Lens.entries.forEach { l ->
            val x = fs[l] ?: return@forEach   // ios: Views/AnalysisView.swift:424
            val on = m.lens == l   // ios: Views/AnalysisView.swift:425
            val st = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal)   // ios: Views/AnalysisView.swift:437
            // ios: Views/AnalysisView.swift:426,427,437,438,439,440,442,443
            Row(Modifier.fillMaxWidth().plainClickable { m.lens = l }.semantics { selected = on }.background(if (on) Theme.tealBg else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/AnalysisView.swift:428,432
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    T(ForecastView.short(l), style = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), color = Theme.ink)   // ios: Views/AnalysisView.swift:429
                    T(AppModel.sgn0(x.mu), style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisView.swift:430
                }
                T(ForecastView.amt((x.q50.lastOrNull() ?: 0.0) + add(x)), Modifier.width(70.dp), style = st.copy(textAlign = TextAlign.End), color = Theme.ink)   // ios: Views/AnalysisView.swift:433
                T(ForecastView.amt((x.q5.lastOrNull() ?: 0.0) + add(x)), Modifier.width(58.dp), style = st.copy(textAlign = TextAlign.End), color = Theme.ink)   // ios: Views/AnalysisView.swift:434
                T(AppModel.pct(x.pGoal), Modifier.width(78.dp), style = st.copy(textAlign = TextAlign.End), color = Theme.ink)   // ios: Views/AnalysisView.swift:435
            }
        }
        T(msg, style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisView.swift:446
    }
}

// MARK: 전망을 흔들 수 있는 일정 (외부 요인 토글 자리)
// ios: Views/AnalysisView.swift:451,452
@Composable
private fun Schedule(m: AppModel) {
    val hits = MarketEvents.upcoming(held = m.rows.map { it.id }, days = 60).take(4)   // ios: Views/AnalysisView.swift:453
    // ios: Views/AnalysisView.swift:454
    Card {
        ToggleRow(m.shock, { m.shock = it }, tint = Theme.teal) { T("흔들 일정", style = appFont(15f, FontWeight.Bold)) }   // ios: Views/AnalysisView.swift:455
        T("켜면 계산에 반영", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisView.swift:456
        hits.indices.forEach { i -> ScheduleRow(hit = hits[i]) { r -> m.analysisPath = m.analysisPath + r } }   // ios: Views/AnalysisView.swift:457
    }
}

// MARK: 자세히 보기 (기본 접힘)
// ios: Views/AnalysisView.swift:462
@Composable
private fun Details(m: AppModel, f: AppModel.Forecast, open: Boolean, setOpen: (Boolean) -> Unit) {
    val what = if (m.isGoal) ForecastView.amt(m.keyValue) else "본전"   // ios: Views/AnalysisView.swift:463
    // ios: Views/AnalysisView.swift:464,465,491,492,493 (withAnimation: 기본 스프링으로 펼친다)
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp))
        .animateContentSize(spring(dampingRatio = 1f, stiffness = stiffness(0.55f))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ios: Views/AnalysisView.swift:465,466,468,471,473
        Row(Modifier.fillMaxWidth().plainClickable { setOpen(!open) }, verticalAlignment = Alignment.CenterVertically) {
            T("자세히", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisView.swift:467
            Sym(if (open) "chevron.up" else "chevron.down", 12f, Theme.muted, scaled = false)   // ios: Views/AnalysisView.swift:469
        }
        // ios: Views/AnalysisView.swift:474
        if (open) {
            // ios: Views/AnalysisView.swift:475
            Card(dark = true) {
                T("한 번이라도 $what 넘을 확률", style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisView.swift:476
                T("잠깐 넘는 경우 포함", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisView.swift:477 (.secondary)
                // ios: Views/AnalysisView.swift:478
                (0 until f.byYear.size).forEach { i ->
                    val y = f.byYear[i].year; val p = f.byYear[i].p   // ios: Views/AnalysisView.swift:479
                    // ios: Views/AnalysisView.swift:480
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("${y}년", Modifier.width(46.dp), style = appFont(13f))   // ios: Views/AnalysisView.swift:481
                        ProgressBar(value = p, height = 10.dp, fill = Theme.teal, track = Theme.track, modifier = Modifier.weight(1f))   // ios: Views/AnalysisView.swift:482
                        T(AppModel.pct(p), Modifier.width(40.dp), style = appFont(13f, FontWeight.Bold).copy(textAlign = TextAlign.End))   // ios: Views/AnalysisView.swift:483
                    }
                }
            }
            // ios: Views/AnalysisView.swift:487,488
            T("직접 옮긴 슬라이더 값은 '내 설정'에 저장", style = appFont(12f), color = Theme.sub)
        }
    }
}

// ios: Views/AnalysisView.swift:291
object ForecastView {
    /** 비교선: 고르지 않은 두 시나리오의 중앙값 (현금 더함) */
    // ios: Views/AnalysisView.swift:497
    fun others(fs: Map<Lens, AppModel.Forecast>, sel: Lens, add: (AppModel.Forecast) -> Double): List<Pair<String, List<Double>>> {
        val out = ArrayList<Pair<String, List<Double>>>()   // ios: Views/AnalysisView.swift:498
        // ios: Views/AnalysisView.swift:499
        for (l in Lens.entries) { if (l == sel) continue
            fs[l]?.let { x -> val c = add(x); out.add(Pair(short(l), x.q50.map { it + c })) }   // ios: Views/AnalysisView.swift:500
        }
        return out   // ios: Views/AnalysisView.swift:502
    }

    // MARK: 표시
    /** 3.30억 · 11.5억 · 826만 (목업 표기) */
    // ios: Views/AnalysisView.swift:507
    fun amt(v: Double): String {
        val a = abs(v); val s = if (v < 0) "−" else ""   // ios: Views/AnalysisView.swift:508
        if (a >= 1e9) return s + cformat("%.1f억", a / 1e8)   // ios: Views/AnalysisView.swift:509
        if (a >= 1e8) return s + cformat("%.2f억", a / 1e8)   // ios: Views/AnalysisView.swift:510
        return s + cformat("%,d", (a / 1e4).swRound().toLong()) + "만"   // ios: Views/AnalysisView.swift:511
    }
    // ios: Views/AnalysisView.swift:513
    fun short(l: Lens): String = if (l == Lens.base) "신중" else if (l == Lens.smooth) "3년처럼" else "내 설정"
    /** "2029-10-14" → ("2029년 10월", "2029.10") */
    // ios: Views/AnalysisView.swift:515
    fun ym(d: String): Pair<String, String> {
        val p = d.split("-")   // ios: Views/AnalysisView.swift:516
        val y = p.getOrNull(0)?.toIntOrNull(); val mo = p.getOrNull(1)?.toIntOrNull(); if (p.size < 2 || y == null || mo == null) return Pair(d, d)   // ios: Views/AnalysisView.swift:517
        return Pair("${y}년 ${mo}월", cformat("%d.%02d", y, mo))   // Views/AnalysisView.swift:518 (검사 예외: 같은 글)
    }
}

