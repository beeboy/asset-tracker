package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
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
import com.naeilo.app.ui.calm.CalmView
import com.naeilo.app.ui.me.CharHead
import com.naeilo.app.ui.goal.GoalSetView
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.ui.support.SubLocked
import com.naeilo.app.data.Support
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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.naeilo.app.core.calc.GoalSim
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.etaYears
import com.naeilo.app.core.calc.goalToDate
import com.naeilo.app.core.calc.mixes
import com.naeilo.app.core.calc.nowMix
import com.naeilo.app.core.calc.simulate
import com.naeilo.app.core.calc.verdict
import com.naeilo.app.core.calc.whenText
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.core.calc.yearAt
import com.naeilo.app.data.Period
import com.naeilo.app.data.Symbol
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.NavStack
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.home.BoxSchemeScope
import com.naeilo.app.ui.home.ThemeTrack
import com.naeilo.app.ui.home.boxInk
import com.naeilo.app.ui.home.bx
import com.naeilo.app.ui.home.FreshLabel
import com.naeilo.app.ui.hold.FitAnnotated
import com.naeilo.app.ui.hold.HoldingDetailView
import com.naeilo.app.ui.hold.moodBackdrop
import com.naeilo.app.ui.pulse
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.min

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

// ios: Views/AnalysisView.swift:3
enum class AnalysisRoute { forecast, rateScen, external, dividend, tax, fx, glance, calm, goal }

// ios: Views/AnalysisView.swift:7,8
fun AnalysisRoute.title(m: AppModel): String = when (this) {
    AnalysisRoute.calm -> "결과에 끌려 사고팔까?"   // ios: Views/AnalysisView.swift:9
    AnalysisRoute.forecast -> m.forecastTitle   // ios: Views/AnalysisView.swift:11
    AnalysisRoute.rateScen -> "금리가 바뀌면 내 목표는?"   // ios: Views/AnalysisView.swift:11
    AnalysisRoute.external -> "바깥 일, 내 돈은?"   // ios: Views/AnalysisView.swift:13
    AnalysisRoute.dividend -> "배당"   // ios: Views/AnalysisView.swift:14
    AnalysisRoute.tax -> "세금"   // ios: Views/AnalysisView.swift:15
    AnalysisRoute.goal -> "목표 고치기"   // ios: Views/AnalysisView.swift:16
    AnalysisRoute.fx -> "환율이 바뀌면?"   // ios: Views/AnalysisView.swift:17
    AnalysisRoute.glance -> "종목 한눈에"   // ios: Views/AnalysisView.swift:18
}

/** 분석 상세 한 쪽 */
// ios: Views/AnalysisView.swift:42,43,44,45
@Composable
fun AnalysisPage(m: AppModel, route: AnalysisRoute) = when (route) {
    // 7일 무료 뒤 구독하지 않으면 목표(내 길)·확률 전망만 잠근다 (Support.pro)
    AnalysisRoute.forecast -> if (Support.shared.pro) ForecastView(m) else SubLocked(m, what = "확률 전망")   // ios: Views/AnalysisView.swift:47
    AnalysisRoute.rateScen -> RateScenarioView(m)   // ios: Views/AnalysisView.swift:49
    AnalysisRoute.external -> ExternalView(m)   // ios: Views/AnalysisView.swift:50
    AnalysisRoute.dividend -> DividendView(m, only = "div")   // ios: Views/AnalysisView.swift:51
    AnalysisRoute.tax -> DividendView(m, only = "tax")   // ios: Views/AnalysisView.swift:52
    AnalysisRoute.goal -> GoalSetView(m, fromSettings = true)   // ios: Views/AnalysisView.swift:53
    AnalysisRoute.fx -> FxImpactView(m)   // ios: Views/AnalysisView.swift:54
    AnalysisRoute.glance -> GlanceView(m)   // ios: Views/AnalysisView.swift:55
    AnalysisRoute.calm -> CalmView(m)   // ios: Views/AnalysisView.swift:56
}

/** 홈·알림에서 바로 들어오는 상세 (분석 탭 위에 쌓인다). 넘기기 화면 안인지는 LocalDetailInPager */
// ios: Views/AnalysisView.swift:62,67,69
@Composable
fun AnalysisPager(m: AppModel, start: AnalysisRoute) {
    val order = AnalysisPager.order
    val ps = rememberPagerState(initialPage = max(0, order.indexOf(start))) { order.size }   // ios: Views/AnalysisView.swift:64,70
    val sel = order[ps.currentPage]
    val nav = LocalNavBack.current

    // ios: Views/AnalysisView.swift:69
    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        // 제목 밑 점: 지금 몇 번째 분석인지, 좌우로 넘길 수 있다는 표시
        // ios: Views/AnalysisView.swift:77,78,79,80,81,82,90,91
        NavBar(sel.title(m), back = nav?.back, center = {
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = tr("${sel.title(m)}, ${order.indexOf(sel) + 1} / ${order.size}, 좌우로 넘기면 다른 분석") },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // ios: Views/AnalysisView.swift:94 .animation(.easeInOut(duration: 0.2), value: sel)
                AnimatedContent(sel.title(m), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "t") { t ->
                    T(t, style = appFont(16f, FontWeight.Bold), maxLines = 1)   // ios: Views/AnalysisView.swift:83
                }
                // ios: Views/AnalysisView.swift:84,85,86
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    order.forEach { r -> Box(Modifier.size(5.dp, 5.dp).background(if (r == sel) Theme.teal else Theme.border, CircleShape)) }
                }
            }
        }, onBack = nav?.backAction)
        // ios: Views/AnalysisView.swift:70,71,73,75
        CompositionLocalProvider(LocalDetailInPager provides true) {
            HorizontalPager(ps, Modifier.weight(1f), beyondViewportPageCount = 0, key = { order[it] }) { i -> Box(Modifier.fillMaxSize()) { AnalysisPage(m, order[i]) } }
        }
    }
}
object AnalysisPager {
    // ios: Views/AnalysisView.swift:65
    val order: List<AnalysisRoute> = listOf(AnalysisRoute.calm, AnalysisRoute.forecast, AnalysisRoute.rateScen, AnalysisRoute.external, AnalysisRoute.fx, AnalysisRoute.glance, AnalysisRoute.dividend, AnalysisRoute.tax)
}

/** 분석 재료 시트: 제목 왼쪽, X 오른쪽. 시트 안 링크는 같은 시트에서 넘기고(뒤로 화살표), X 는 몇 단계 들어갔어도 한 번에 닫는다 */
// ios: Views/AnalysisView.swift:100,101,102,103,105,106
@Composable
fun AnalysisSheet(m: AppModel, route: AnalysisRoute, dismiss: () -> Unit) {
    var path by remember { mutableStateOf(listOf<Any>()) }
    // 시트 안 화면이 분석 탭 길(m.analysisPath)에 쌓는 링크는 시트 안 길로 옮긴다 (ios: 시트 안 NavigationStack)
    val base = remember { m.analysisPath }
    LaunchedEffect(Unit) {
        snapshotFlow { m.analysisPath }.collect { p ->
            if (p.size > base.size && p.take(base.size) == base) { path = path + p.drop(base.size); m.analysisPath = base }
        }
    }
    // ios: Views/AnalysisView.swift:116,120 (.detailSheet(period: .y1), inPopup)
    CompositionLocalProvider(com.naeilo.app.ui.LocalInPopup provides true) { com.naeilo.app.ui.hold.DetailSheet(m, Period.y1) {
    Box(Modifier.fillMaxSize()) {
        NavStack(path, { path = it }, root = {
            Column(Modifier.fillMaxSize()) {
                // 첫 화면 제목은 글자만 (다른 팝업 GameHeader 와 같은 크기)
                // ios: Views/AnalysisView.swift:107,110,111,112,113,114,115
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).background(Theme.bg).padding(start = 16.dp, end = 56.dp), contentAlignment = Alignment.CenterStart) {
                    FitText(route.title(m), appFont(20f, FontWeight.Bold), 0.8f, color = Theme.ink)
                }
                // ios: Views/AnalysisView.swift:108,109
                CompositionLocalProvider(LocalDetailInPager provides true) { Box(Modifier.weight(1f)) { AnalysisPage(m, route) } }
            }
        }) { r -> analysisSheetPage(m, r) }   // ios: Views/AnalysisView.swift:117,118
        // 넘긴 화면에도 같은 자리에
        // ios: Views/AnalysisView.swift:121,122,123,124,126,127,128
        com.naeilo.app.PopupX(Modifier.align(Alignment.TopEnd), dismiss)
    }
    } }
}

/** 시트 안에서 넘긴 화면 (분석 탭 길과 같은 갈래: App.kt) */
// ios: Views/AnalysisView.swift:117,118
@Composable
private fun analysisSheetPage(m: AppModel, r: Any): NavPage = when (r) {
    is AnalysisRoute -> NavPage(r.title(m)) { AnalysisPage(m, r) }   // ios: Views/AnalysisView.swift:117 (detailInPager false)
    is FactorDetailRoute -> NavPage(FactorDetailView.navTitle(r.factor)) { FactorDetailView(m, r.factor, r.start) }
    is RateWhyRoute -> NavPage("근거") { RateWhyView(r.r, r.`when`, r.epsLast) }
    TaxRulesRoute -> NavPage { com.naeilo.app.ui.settings.TaxRulesView(m) }
    is com.naeilo.app.ui.calm.CalmFriendRoute -> com.naeilo.app.ui.shelter.characterPage(m, r.friend, invest = true)
    else -> NavPage { GoalSetPage(m) }   // 전망의 '목표 고치기'
}

// 분석 탭 = 내 길 메인 (2026-10-23 확정 시안 '1차 시안'): 확률 박스 → 왜 낮을까(줄다리기) → 언제 흔들릴까(그래프 + 일정 깃발) → 어떻게 올릴까(구성별 닿는 때).
// 일곱 재료(전망·금리·외부 요인·배당·세금·환율·종목 한눈에·습관)는 시트로 연다. 숫자는 각 상세 화면과 같은 식 (허브 전용 숫자를 따로 계산하지 않는다)
// ios: Views/AnalysisView.swift:135
object AnalysisView {
    /** 색은 의미 3개만: 초록 들어오는 돈·좋아짐 / 주황 나가는 돈·늦어짐 / 회색 정보 */
    val good: Color @Composable get() = hexD(0x2C5A54, 0x8FD0C4)   // ios: Views/AnalysisView.swift:141
    val warn: Color @Composable get() = hexD(0xA8521A, 0xE8A060)   // ios: Views/AnalysisView.swift:142
    val goodBg: Color @Composable get() = hexD(0xE3F0ED, 0x173531)   // ios: Views/AnalysisView.swift:143
    val warnBg: Color @Composable get() = hexD(0xFBEBDD, 0x3A2A1C)   // ios: Views/AnalysisView.swift:143
    val goodInk: Color @Composable get() = hexD(0x2F4A46, 0xB8DCD5)   // ios: Views/AnalysisView.swift:144
    val warnInk: Color @Composable get() = hexD(0x6B4A33, 0xE6C6AA)   // ios: Views/AnalysisView.swift:144

    /** 월 4.8만 · 9천 */
    // ios: Views/AnalysisView.swift:267
    fun short(krw: Double): String {
        if (AppModel.en) return AppModel.wonRead(krw)   // ios: Views/AnalysisView.swift:268
        // ios: Views/AnalysisView.swift:269
        if (krw >= 1e4) {
            val v = (krw / 1e3).swRound() / 10   // ios: Views/AnalysisView.swift:270
            return (if (v == v.swRound()) v.toInt().toString() else cformat("%.1f", v)) + "만"   // ios: Views/AnalysisView.swift:271
        }
        return "${max(1, (krw / 1e3).swRound().toInt())}천"   // ios: Views/AnalysisView.swift:273
    }
}

// ios: Views/AnalysisView.swift:146
private fun name(r: AppModel.Row): String = if (r.sym.name.isEmpty()) r.id else r.sym.name

// ios: Views/AnalysisView.swift:148
@Composable
fun AnalysisView(m: AppModel) {
    val lookRaw = BoxLook.raw   // ios: Views/AnalysisView.swift:137
    var sheet by remember { mutableStateOf<AnalysisRoute?>(null) }   // ios: Views/AnalysisView.swift:138
    var toGoal by remember { mutableStateOf(true) }   // ios: Views/AnalysisView.swift:139
    val p by remember(m) { derivedStateOf { m.myPath } }; val f = m.forecast; val pro = Support.shared.pro   // ios: Views/AnalysisView.swift:149
    // 확률: 목표 = 목표일에 목표 이상, 회복 = 기간 안 본전 (내 길 판정 카드와 같은 값)
    val pPlan = if (m.isGoal) f.pGoal else f.prob(min(3.0, p.end - p.start))   // ios: Views/AnalysisView.swift:151
    val open = { r: AnalysisRoute -> sheet = r }
    // ios: Views/AnalysisView.swift:168,169
    LaunchedEffect(Unit) { MacroData.shared.load() }
    LaunchedEffect(Unit) { SiteCalc.shared.refresh() }
    // 당기면 시세는 새로, 계산·금리 자료는 아직 신선하면 다시 받지 않는다
    // ios: Views/AnalysisView.swift:152,166,167,171,172,173,174
    Refreshable({ Market.shared.pullRefresh(m); MacroData.shared.load(); SiteCalc.shared.refresh() }, Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ios: Views/AnalysisView.swift:164
            Screen {
                // ios: Views/AnalysisView.swift:153
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.outset(16.dp)) { AppHeader(tab = "분석") }   // ios: Views/AnalysisView.swift:154 (-16)
                    // ios: Views/AnalysisView.swift:156,157,159
                    Box(Modifier.plainClickable { open(AnalysisRoute.forecast) }) {
                        BoxSchemeScope { ChanceBox(look = BoxLook.entries.firstOrNull { it.name == lookRaw } ?: BoxLook.jump, d = chance(m, p, f, pPlan, pro = pro)) }
                    }
                    Tug(m, pPlan, known = pro && f.exact, open)   // ios: Views/AnalysisView.swift:160
                    Shake(m, p, f, pro = pro, toGoal = toGoal, setToGoal = { toGoal = it }, open)   // ios: Views/AnalysisView.swift:161
                    if (m.isGoal && pro) Raise(m)   // ios: Views/AnalysisView.swift:162
                    SampleNote(m)   // ios: Views/AnalysisView.swift:162 샘플 안내는 맨 아래 (2026-10-28)
                }
            }
        }
    }
    // ios: Views/AnalysisView.swift:177
    Sheet(show = sheet != null, onDismiss = { sheet = null }) {
        sheet?.let { r -> key(r) { AnalysisSheet(m, r) { sheet = null } } }
    }
    // 알림·탭 화면 링크로 들어온 분석도 같은 분석 팝업으로 (예전엔 탭 안에서 넘겼다). 팝업이 떠 있으면 팝업이 제 길로 옮긴다
    // ios: Views/AnalysisView.swift:129,130,133,134,135,136
    LaunchedEffect(Unit) {
        snapshotFlow { m.analysisPath }.collect { p ->
            val r = p.firstOrNull() as? AnalysisRoute
            if (sheet == null && r != null) { m.analysisPath = emptyList(); sheet = r }
        }
    }
}

/** 판 흰 카드 (시안: 테두리 없이 둥근 20) */
// ios: Views/AnalysisView.swift:181,182,186,187
@Composable
private fun Panel(title: String, spacing: Dp = 12.dp, c: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(spacing)) {
        T(title, style = appFont(17f, FontWeight.Bold))   // ios: Views/AnalysisView.swift:183
        c()   // ios: Views/AnalysisView.swift:184
    }
}

// MARK: 확률 박스 값: 가로 자리 = 금액 ÷ 눈금 끝. 목표선 ≈ 83%, 좋을 때 ≤ 92% (시안 자리)
// ios: Views/AnalysisView.swift:191
private fun chance(m: AppModel, p: MyPath, f: AppModel.Forecast, pPlan: Double, pro: Boolean): ChanceBox.Data {
    val K = max(1.0, p.key); val now = m.trackValue   // ios: Views/AnalysisView.swift:192
    val left = AppModel.leftText(if (m.isGoal) yrs(Day.today, Day.str(m.goalToDate)) else p.end - p.start)   // ios: Views/AnalysisView.swift:193
    // Views/AnalysisView.swift:195,196 (검사 예외: 같은 글)
    val head = if (m.isGoal) "${AppModel.wonK(m.gK)} · ${ForecastView.ym(Day.str(m.goalToDate)).second} · $left 남음"
               else "본전 ${AppModel.eok(K)} · 1년 안 · $left 남음"
    val goalName = if (m.isGoal) AppModel.wonK(m.gK) else "본전"   // ios: Views/AnalysisView.swift:197
    val nowText = "지금 ${AppModel.eok(now)}"   // ios: Views/AnalysisView.swift:198
    // ios: Views/AnalysisView.swift:199,200
    if (!pro) return ChanceBox.Data(head = head, verdict = null, pct = "", status = "구독 시 열림", now = 0.0, goal = null, dots = emptyList(), goalLabel = goalName, foot = "7일 무료 끝", nowText = nowText)
    // 목표일(회복: 1년 뒤)의 나쁠 때·보통·좋을 때 = 전망 5%·가운데·95% (전망 화면 '10번 중 9번'과 같은 끝), 현금 더함
    val i = f.idx(years = p.end - p.start); val add = if (f.exact) m.cashKrw else 0.0   // ios: Views/AnalysisView.swift:203
    val three: List<Double> = if (f.exact && i < f.q50.size) listOf(f.q5[i], f.q50[i], f.q95[i]).map { it + add } else emptyList()   // ios: Views/AnalysisView.swift:204
    val top = maxOf(K / 0.83, (three.maxOrNull() ?: 0.0) / 0.92, now / 0.9)   // ios: Views/AnalysisView.swift:205
    val over = three.count { it >= K }   // ios: Views/AnalysisView.swift:206
    val foot = if (three.isEmpty()) "" else listOf("셋 다 선 아래", "셋 중 하나만 선을 넘음", "셋 중 둘이 선을 넘음", "셋 다 선을 넘음")[over]   // ios: Views/AnalysisView.swift:207
    // ios: Views/AnalysisView.swift:208,209
    return ChanceBox.Data(head = head, verdict = if (f.exact) AppModel.verdict(pPlan) else null, pct = AppModel.pct(pPlan), status = "가능성 계산 중",
                          now = now / top, goal = K / top, dots = three.map { it / top }, goalLabel = goalName, foot = foot, nowText = nowText)
}

// MARK: 왜 낮을까? 줄다리기: 왼쪽 끌어내리는 힘, 오른쪽 끌어올리는 힘. 칸 값은 각 상세 화면 첫 숫자와 같은 식
// ios: Views/AnalysisView.swift:213
private class Pull(val label: String, val value: String, val route: AnalysisRoute)

// ios: Views/AnalysisView.swift:215
@Composable
private fun Tug(m: AppModel, p: Double, known: Boolean, open: (AnalysisRoute) -> Unit) {
    val tot = max(1.0, m.total)   // ios: Views/AnalysisView.swift:216
    val down = ArrayList<Pull>(); val up = ArrayList<Pull>()   // ios: Views/AnalysisView.swift:217
    // 종목 한눈에: 비중 1위가 40% 넘으면 쏠림
    // ios: Views/AnalysisView.swift:219
    m.rows.maxByOrNull { it.value }?.let { top ->
        val w = top.value / tot   // ios: Views/AnalysisView.swift:220
        if (w >= 0.4) down.add(Pull("${name(top)} 쏠림", AppModel.pct(w), AnalysisRoute.glance))   // ios: Views/AnalysisView.swift:221
        else up.add(Pull("고루 나눔", "1위 ${AppModel.pct(w)}", AnalysisRoute.glance))   // ios: Views/AnalysisView.swift:222
    }
    // 금리: 기본 프리셋("작년만큼 또 오른다")일 때 목표 확률 (목표 루트·구독). 그 밖엔 도착 시점 변화. 확률이 오르면 끌어올리는 쪽
    val rp = if (known && m.isGoal) RateScenarioView.hubProb(m) else null   // ios: Views/AnalysisView.swift:225
    val rt = RateScenarioView.hubTiming(m)   // ios: Views/AnalysisView.swift:226
    // ios: Views/AnalysisView.swift:227,228,229
    val rate = rp?.let { AppModel.pct(it) }
        ?: rt?.replace("약 ", "")
        ?: (if (MacroData.shared.failed) "자료 없음" else "계산 중")
    val rateUp = rp?.let { it > p + 0.005 } ?: (rt?.endsWith("빨리") == true)   // ios: Views/AnalysisView.swift:230
    val ratePull = Pull("금리 오르면", rate, AnalysisRoute.rateScen)   // ios: Views/AnalysisView.swift:231
    if (rateUp) up.add(ratePull) else down.add(ratePull)   // ios: Views/AnalysisView.swift:232
    // 환율: 원화가 10원 강해지면 달러 평가액 × 10 / 기준 환율만큼 준다
    val usdV = m.rows.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value }   // ios: Views/AnalysisView.swift:234
    down.add(Pull("환율 10원↓", if (usdV > 0) "−" + AppModel.man(usdV * 10 / max(1.0, Market.shared.valFx)) else "그대로", AnalysisRoute.fx))   // ios: Views/AnalysisView.swift:235
    val tax = DividendView.hubTax(m)   // ios: Views/AnalysisView.swift:236
    if (tax > 0) down.add(Pull("5월 세금", "−" + AppModel.man(tax), AnalysisRoute.tax))   // ios: Views/AnalysisView.swift:237
    if (m.isGoal && m.gM > 0) up.add(Pull("매달 적립", AppModel.wonK(m.gM), AnalysisRoute.goal))   // ios: Views/AnalysisView.swift:238
    val mo = DividendView.hubMonthly(m)   // ios: Views/AnalysisView.swift:239
    up.add(Pull("배당", if (mo > 0) "월 ${AnalysisView.short(mo)}" else "없음", AnalysisRoute.dividend))   // ios: Views/AnalysisView.swift:240
    val verdict = AppModel.verdict(p)   // ios: Views/AnalysisView.swift:241
    // ios: Views/AnalysisView.swift:242
    val title = if (!known) "무엇이 당길까?" else if (verdict == AppModel.verdict(0.0)) "왜 낮을까?" else if (verdict == AppModel.verdict(1.0)) "왜 높을까?" else "왜 반반일까?"
    // 줄 굵기: 지는 쪽이 굵다 (확률이 낮으면 끌어내리는 힘이 굵다)
    val hd = if (!known) 6.dp else if (p < 0.5) 8.dp else 5.dp; val hu = if (!known) 6.dp else if (p < 0.5) 5.dp else 8.dp   // ios: Views/AnalysisView.swift:244
    val warn = AnalysisView.warn; val good = AnalysisView.good
    // ios: Views/AnalysisView.swift:245
    Panel(title, spacing = 14.dp) {
        // ios: Views/AnalysisView.swift:246,247,248,249,251
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(hd).background(warn, RoundedCornerShape(topStart = hd / 2, bottomStart = hd / 2)))
            Box(Modifier.size(20.dp).background(Theme.ink, CircleShape))
            Box(Modifier.weight(1f).height(hu).background(good, RoundedCornerShape(topEnd = hu / 2, bottomEnd = hu / 2)))
        }
        // ios: Views/AnalysisView.swift:252
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            // ios: Views/AnalysisView.swift:253,254,255
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("끌어내리는 힘", style = appFont(12f, FontWeight.Bold), color = warn)
                down.forEach { PullChip(it, down = true, open) }
            }
            // ios: Views/AnalysisView.swift:257,258,259,260
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("끌어올리는 힘", style = appFont(12f, FontWeight.Bold), color = good)
                up.forEach { PullChip(it, down = false, open) }
                HabitChip(m, open)
            }
        }
    }
}

// ios: Views/AnalysisView.swift:276
@Composable
private fun PullChip(x: Pull, down: Boolean, open: (AnalysisRoute) -> Unit) {
    // ios: Views/AnalysisView.swift:277,278,283,284,285,287,288
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(if (down) AnalysisView.warnBg else AnalysisView.goodBg, RoundedCornerShape(12.dp))
        .plainClickable { open(x.route) }.clearAndSetSemantics { contentDescription = tr("${x.label} ${x.value}") }.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(end = 2.dp)) { FitText(x.label, appFont(13f, FontWeight.Bold), 0.75f, color = Theme.ink) }   // ios: Views/AnalysisView.swift:279,280
        T(x.value + " ›", style = appFont(13f), color = if (down) AnalysisView.warnInk else AnalysisView.goodInk, maxLines = 1)   // ios: Views/AnalysisView.swift:281
    }
}

// 내 투자 습관: 노란 꼬리표 칸 (홈 리마인드 띠와 같은 모양)
// ios: Views/AnalysisView.swift:292
@Composable
private fun HabitChip(m: AppModel, open: (AnalysisRoute) -> Unit) {
    val s = m.calm.stats(); val done = s.n >= Calm.minEvents   // ios: Views/AnalysisView.swift:293
    val v = if (done) "지수 ${s.index}" else "기록 ${s.n}/${Calm.minEvents}"   // ios: Views/AnalysisView.swift:294
    LaunchedEffect(Unit) { if (m.calm.type != null && m.calm.ladder?.type != m.calm.type) m.calm.climb() }   // ios: Views/AnalysisView.swift:308
    // ios: Views/AnalysisView.swift:295,296,302,303,304,307
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(Theme.cream, RoundedCornerShape(12.dp))
        .plainClickable { open(AnalysisRoute.calm) }.clearAndSetSemantics { contentDescription = tr("내 투자 습관, $v") }.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/AnalysisView.swift:297,298
        T("투자 습관", Modifier.background(Theme.yellow, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed, maxLines = 1)
        Spacer(Modifier.weight(1f).widthIn(min = 2.dp))   // ios: Views/AnalysisView.swift:299
        FitText("$v ›", appFont(13f), 0.8f, color = Theme.ink)   // ios: Views/AnalysisView.swift:300
    }
}

// MARK: 언제 흔들릴까? 내 길 계획선 그래프 + 앞으로 올 일정 깃발 (앱 일정표 MarketEvents · 세금은 내년 5월)
// ios: Views/AnalysisView.swift:312
private class Flag(val t: Double, val text: String, val warn: Boolean, val route: AnalysisRoute, val focus: String? = null)

// ios: Views/AnalysisView.swift:314
private fun flags(m: AppModel, p: MyPath, until: Double): List<Flag> {   // ios: Views/AnalysisView.swift:313
    val days = max(0.0, (until - p.start) * 365.25).toInt()   // ios: Views/AnalysisView.swift:314
    val hits = MarketEvents.upcoming(held = m.rows.map { it.id }, days = min(days, 400))   // ios: Views/AnalysisView.swift:316
    val dday = { d: String -> val n = Day.dday(d); if (n == 0) "오늘" else "D-$n" }   // ios: Views/AnalysisView.swift:317
    val out = ArrayList<Flag>()   // ios: Views/AnalysisView.swift:318
    // ios: Views/AnalysisView.swift:319,320
    hits.firstOrNull { it.id == "x_fomc" }?.let { h -> out.add(Flag(p.start + Day.dday(h.day) / 365.25, dday(h.day) + " FOMC", true, AnalysisRoute.external, "${h.id}.${h.day}")) }
    // ios: Views/AnalysisView.swift:322,323
    hits.firstOrNull { it.id.startsWith("e_") && it.held }?.let { h -> out.add(Flag(p.start + Day.dday(h.day) / 365.25, dday(h.day) + " " + h.kind.replace(" 실적", ""), false, AnalysisRoute.external)) }
    // ios: Views/AnalysisView.swift:325
    if (DividendView.hubTax(m) > 0) {
        val y = java.time.LocalDate.now().year + 1   // ios: Views/AnalysisView.swift:326
        val t = p.start + yrs(Day.today, "$y-05-31")   // ios: Views/AnalysisView.swift:327
        if (t <= until) out.add(Flag(t, "$y.5 세금", true, AnalysisRoute.tax))   // ios: Views/AnalysisView.swift:328
    }
    return out.filter { it.t <= until }.sortedBy { it.t }   // ios: Views/AnalysisView.swift:330
}

// ios: Views/AnalysisView.swift:333
@Composable
private fun Shake(m: AppModel, p: MyPath, f: AppModel.Forecast, pro: Boolean, toGoal: Boolean, setToGoal: (Boolean) -> Unit, open: (AnalysisRoute) -> Unit) {
    val goal = m.isGoal; val mS = p.start; val mEnd = p.end; val need = p.need; val K = p.key; val now = m.trackValue   // ios: Views/AnalysisView.swift:334
    // 그래프: 세로 눈금은 기간을 바꿔도 같게 (목표일까지 전체 값으로 정한다). 내 길 상세 그래프와 같은 식
    val x1 = if (toGoal) mEnd else mS + 1.0 / 12   // ios: Views/AnalysisView.swift:336
    val actPts = p.pts   // ios: Views/AnalysisView.swift:337
    val needAll = (0..60).map { i -> val t = mEnd * i / 60; Pair(t, need(t)) }   // ios: Views/AnalysisView.swift:338
    val needPts = (0..60).map { i -> val t = x1 * i / 60; Pair(t, need(t)) }   // ios: Views/AnalysisView.swift:339
    val iE = f.idx(years = mEnd - mS)   // ios: Views/AnalysisView.swift:340
    // ios: Views/AnalysisView.swift:341,342
    val bandAll: List<Triple<Double, Double, Double>> = if (!pro || f.tY.isEmpty()) emptyList()
        else (0..max(min(1, f.tY.size - 1), iE)).map { i -> Triple(mS + f.tY[i], f.q75[i] + m.cashKrw, f.q25[i] + m.cashKrw) }
    val bandPts = if (toGoal) bandAll else emptyList()   // ios: Views/AnalysisView.swift:343
    val vals = actPts.map { it.second } + needAll.map { it.second } + bandAll.flatMap { listOf(it.second, it.third) } + listOf(K)   // ios: Views/AnalysisView.swift:344
    val hi = (vals.maxOrNull() ?: 1.0) * 1.05; val lo = (vals.filter { it > 0 }.minOrNull() ?: 0.0) * 0.9   // ios: Views/AnalysisView.swift:345
    // 왼쪽 2% 비움: 시작점 동그라미가 그래프 왼쪽 끝에서 반쯤 잘리지 않게
    val X = { t: Double -> 0.02 + 0.98 * t / max(1e-6, x1) }; val Y = { v: Double -> (hi - v) / max(1.0, hi - lo) }   // ios: Views/AnalysisView.swift:346
    val fl = flags(m, p, until = x1)   // ios: Views/AnalysisView.swift:347 지금까지 보기는 다음 달까지 일정만
    val fog = if (iE < f.q50.size) f.q50[iE] + (if (f.exact) m.cashKrw else 0.0) else 0.0   // ios: Views/AnalysisView.swift:348
    val gc = m.goalToDate.atZone(ZoneId.systemDefault())   // ios: Views/AnalysisView.swift:349
    val chartH = 150.dp; val topH = 30.dp   // ios: Views/AnalysisView.swift:350
    val teal = Theme.teal; val ink = Theme.ink; val purple = Theme.purple
    val lastY = actPts.lastOrNull()?.second ?: now
    // ios: Views/AnalysisView.swift:351
    Panel("언제 흔들릴까?") {
        // ios: Views/AnalysisView.swift:352,353,354,403,404
        BoxWithConstraints(Modifier.fillMaxWidth().height(topH + chartH)) {
            val w = maxWidth
            // ios: Views/AnalysisView.swift:355,370,371,372 (안드로이드: 맥박은 세 번 뛰고 멈춘다. 계속 그리면 삼성 기기에서 프레임마다 로그가 쌓여 느려진다)
            Canvas(Modifier.offset(y = topH).size(w, chartH).pulse(teal, 8.dp, beats = 3, key = lastY) { s -> Offset((X(mS) * s.width).toFloat(), (Y(lastY) * s.height).toFloat()) }) {
                // ios: Views/AnalysisView.swift:356,357,358,359,360
                if (bandPts.isNotEmpty()) {
                    val band = linePath(bandPts.map { Pair(X(it.first), Y(it.second)) }, size)
                    for (q in bandPts.asReversed()) band.lineTo((X(q.first) * size.width).toFloat(), (Y(q.third) * size.height).toFloat())
                    band.close()
                    drawPath(band, teal.copy(alpha = 0.14f))
                }
                // ios: Views/AnalysisView.swift:362,363,364
                val gy = (Y(K) * size.height).toFloat()
                drawLine(ink.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
                // ios: Views/AnalysisView.swift:365
                drawPath(linePath(needPts.map { Pair(X(it.first), Y(it.second)) }, size), purple, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                // ios: Views/AnalysisView.swift:366
                drawPath(linePath(actPts.map { Pair(X(it.first), Y(it.second)) }, size), teal, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
                // ios: Views/AnalysisView.swift:367,368
                drawCircle(teal, 4.dp.toPx(), Offset((X(mS) * size.width).toFloat(), (Y(lastY) * size.height).toFloat()))
            }
            // 깃발: 일정 날짜 자리에 세로 줄, 이름표는 겹치지 않게 한 줄씩 내려 단다
            // ios: Views/AnalysisView.swift:374
            fl.forEachIndexed { i, g ->
                val x = w * min(1.0, max(0.0, X(g.t))).toFloat(); val y = 22.dp * i   // ios: Views/AnalysisView.swift:375
                val c = if (g.warn) hex(0xA8521A) else Theme.muted   // ios: Views/AnalysisView.swift:376
                Box(Modifier.offset(x = x, y = y + 18.dp).size(1.dp, (topH + chartH - y - 18.dp).coerceAtLeast(0.dp)).background(c))   // ios: Views/AnalysisView.swift:377
                // ios: Views/AnalysisView.swift:378,379,380,381,383,384
                Box(Modifier.offset(x = minOf((x - 4.dp).coerceAtLeast(0.dp), (w - 110.dp).coerceAtLeast(0.dp)), y = y).height(18.dp)
                    .background(if (g.warn) hex(0xA8521A) else Theme.line, RoundedCornerShape(6.dp)).plainClickable { ExternalView.focus = g.focus; open(g.route) }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    T(g.text, style = appFont(11f, FontWeight.Bold), color = if (g.warn) Color.White else Theme.ink, maxLines = 1)
                }
            }
            // 오른쪽: 목표선 이름 + 안개(목표일 보통 값) → 전망 시트
            // ios: Views/AnalysisView.swift:386,387,388,389,390,391,392,393,394,395,396,397
            Box(Modifier.width(w).offset(y = (topH + chartH * Y(K).toFloat() - 34.dp).coerceAtLeast(0.dp)), contentAlignment = Alignment.TopEnd) {
                Column(Modifier.background(Theme.card.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).plainClickable { open(AnalysisRoute.forecast) }.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    val st = appFont(11f, FontWeight.Bold).copy(textAlign = TextAlign.End)
                    T("${m.keyName} ${if (goal) AppModel.wonK(m.gK) else AppModel.eok(K)}", style = st, color = teal)
                    T(if (pro && f.exact && fog > 0) "안개: ${AppModel.eok(fog)}쯤 ›" else "전망 ›", style = st, color = teal)
                }
            }
        }
        // ios: Views/AnalysisView.swift:405,406,407,409
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(m.pathStartMonth, Modifier.weight(1f), style = appFont(12f), color = Theme.sub)
            T(if (toGoal) (if (goal) "${gc.year}.${gc.monthValue}" else "1년 뒤") else "다음 달", style = appFont(12f), color = Theme.sub)
        }
        // ios: Views/AnalysisView.swift:410
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/AnalysisView.swift:411,412,413,414,416
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T("─ 실제", style = appFont(11f), color = teal, maxLines = 1)
                T("- - 계획", style = appFont(11f), color = purple, maxLines = 1)
                if (toGoal && pro) T("▒ 보통", style = appFont(11f), color = Theme.sub, maxLines = 1)
            }
            Spacer(Modifier.weight(1f).widthIn(min = 4.dp))   // ios: Views/AnalysisView.swift:417
            SmallPill(if (goal) "목표일까지" else "1년 뒤까지", on = toGoal) { setToGoal(true) }   // ios: Views/AnalysisView.swift:418
            SmallPill("지금까지", on = !toGoal) { setToGoal(false) }   // ios: Views/AnalysisView.swift:419
        }
    }
}

// ios: Views/AnalysisView.swift:424
@Composable
private fun SmallPill(t: String, on: Boolean, act: () -> Unit) {
    // ios: Views/AnalysisView.swift:425,426,427,428,429,432
    Box(Modifier.defaultMinSize(minHeight = 30.dp).background(if (on) Theme.teal else Theme.card, CapsuleShape)
        .then(if (!on) Modifier.border(1.dp, Theme.border, CapsuleShape) else Modifier)
        .plainClickable(act).semantics { selected = on }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        T(t, style = appFont(12f, FontWeight.Bold), color = if (on) Color.White else Theme.ink, maxLines = 1)
    }
}

/** .position(x:y:): 가운데를 (x, y) 에 둔다 */
@Composable
private fun At(x: Dp, y: Dp, content: @Composable () -> Unit) {
    Layout(content) { ms, _ ->
        val p = ms.first().measure(Constraints())
        layout(0, 0) { p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2) }
    }
}

// MARK: 어떻게 올릴까? 구성마다 목표에 닿는 때: 금색 알약 이름 · 슬라이드(점 = 보통, 막대 = 10번 중 8번, 점선 = 목표일) · 연도
// ios: Views/AnalysisView.swift:446
@Composable
private fun Raise(m: AppModel) {
    val yrsE = m.etaYears   // ios: Views/AnalysisView.swift:447
    val mixes = listOf("지금 구성" to m.nowMix, "성장 구성" to AppModel.mixes[1], "방패 구성" to AppModel.mixes[2])   // ios: Views/AnalysisView.swift:448
    // 모의바구니: 평행 세계에 저장한 모의바구니 (없으면 줄을 숨긴다)
    val mine = m.pworld?.segs?.lastOrNull()?.w?.filter { it.value > 0 } ?: emptyMap()   // ios: Views/AnalysisView.swift:447
    // ios: Views/AnalysisView.swift:448,449,450,451
    val customSim: GoalSim? = if (mine.isEmpty()) null else m.simulate(m.nowMix, years = yrsE, parts = mine.map { (id, x) ->
        val usd = m.rows.firstOrNull { it.id == id }?.let { it.sym.currency == Currency.usd } ?: com.naeilo.app.net.ExtraStats.shared.usd(id) ?: true
        com.naeilo.app.core.calc.Part(key = com.naeilo.app.net.SiteStats.mcKey(id), ccy = if (usd) "USD" else "KRW", w = x)
    })
    // ios: Views/AnalysisView.swift:452,453,454,455
    val customDesc = mine.entries.sortedByDescending { it.value }.take(3).joinToString(" · ") { (id, x) ->
        val name = m.rows.firstOrNull { it.id == id }?.sym?.name ?: com.naeilo.app.core.calc.Parallel.universe.firstOrNull { it.id == id }?.name ?: com.naeilo.app.net.ExtraStats.shared.name(id) ?: id
        "$name ${(x * 100).roundToInt()}"
    } + if (mine.size > 3) " …" else ""
    val sims = mixes.map { m.simulate(it.second, years = yrsE) } + listOfNotNull(customSim)   // ios: Views/AnalysisView.swift:457
    val goalMo = max(0.0, (m.goalToDate.toEpochMilli() - System.currentTimeMillis()) / 1000.0 / (365.25 * 86400) * 12)   // ios: Views/AnalysisView.swift:450
    val ax = EtaRow.axis(sims, goalYear = AppModel.yearAt(goalMo)); val a0 = ax.first; val a1 = ax.second   // ios: Views/AnalysisView.swift:451
    val x = { y: Double -> min(1.0, max(0.0, (y - a0) / max(0.5, a1 - a0))) }   // ios: Views/AnalysisView.swift:452
    val step = if (a1 - a0 > 6) 2 else 1   // ios: Views/AnalysisView.swift:453
    val ys = (a0.toInt()..a1.toInt() step step).toList()   // ios: Views/AnalysisView.swift:454
    // ios: Views/AnalysisView.swift:455
    Panel("어떻게 올릴까?") {
        // ios: Views/AnalysisView.swift:456,457,458,464,465,467
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.width(84.dp))
            BoxWithConstraints(Modifier.weight(1f).height(14.dp)) {
                val w = maxWidth
                // ios: Views/AnalysisView.swift:459,460,461
                ys.forEach { y ->
                    At(minOf(w - 10.dp, (w * x(y.toDouble()).toFloat()).coerceAtLeast(10.dp)), 7.dp) { T(cformat("'%02d", y % 100), style = appFont(11f), color = Theme.sub, maxLines = 1) }
                }
            }
            Spacer(Modifier.width(52.dp))
        }
        // ios: Views/AnalysisView.swift:468,469
        mixes.indices.forEach { i -> EtaLine(mixes[i].first, mixes[i].second.desc, sims[i], x, gx = x(AppModel.yearAt(goalMo)), mine = i == 0) }   // ios: Views/AnalysisView.swift:475
        if (customSim != null) EtaLine("모의바구니", customDesc, customSim, x, gx = x(AppModel.yearAt(goalMo)), mine = false)   // ios: Views/AnalysisView.swift:478,479
        // ios: Views/AnalysisView.swift:471
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { FitText("● 보통 · ─ 10번 중 8번 · ┊ 목표일", appFont(12f), 0.8f, color = Theme.sub) }   // ios: Views/AnalysisView.swift:472,473
            // ios: Views/AnalysisView.swift:474,475,476,477
            if (m.rows.isNotEmpty()) {   // 처음 루트도 종목을 담았으면 보인다
                var comparing by remember { mutableStateOf(false) }   // ios: Views/AnalysisView.swift:140
                Box(Modifier.defaultMinSize(minHeight = 30.dp).background(Theme.teal, CapsuleShape).plainClickable { comparing = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    T("구성 변경", style = appFont(12f, FontWeight.Bold), color = Color.White, maxLines = 1)
                }
                Sheet(comparing, onDismiss = { comparing = false }) { MyPathCompareView(m, dismiss = { comparing = false }) }   // ios: Views/AnalysisView.swift .sheet(isPresented: $comparing)
            }
        }
    }
}

// ios: Views/AnalysisView.swift:485
@Composable
private fun EtaLine(name: String, desc: String, s: GoalSim, x: (Double) -> Double, gx: Double, mine: Boolean) {
    val lo = x(AppModel.yearAt(s.etaLo)); val hi = if (s.etaHi.isFinite()) x(AppModel.yearAt(s.etaHi)) else 1.0   // ios: Views/AnalysisView.swift:486
    val mid = if (s.etaMonths.isFinite()) x(AppModel.yearAt(s.etaMonths)) else 1.0   // ios: Views/AnalysisView.swift:487
    val range = if (mine) hexD(0xA9D6CB, 0x2F6B62) else hexD(0xA9B0BA, 0x4A5260)   // ios: Views/AnalysisView.swift:488
    val dot = if (mine) Theme.teal else hexD(0x4A5260, 0xA9B0BA)   // ios: Views/AnalysisView.swift:489
    val whenT = AppModel.whenText(s.etaMonths, cap = s.capYears)   // ios: Views/AnalysisView.swift:490
    // ios: Views/AnalysisView.swift:505 (값이 바뀌면 점·막대가 미끄러진다)
    val ease = tween<Float>(250, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))
    val loA by animateFloatAsState(lo.toFloat(), ease, label = "lo"); val hiA by animateFloatAsState(hi.toFloat(), ease, label = "hi"); val midA by animateFloatAsState(mid.toFloat(), ease, label = "mid")
    val sub = Theme.sub
    // ios: Views/AnalysisView.swift:491,512,513
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = tr("$name, 보통 $whenT, 10번 중 8번은 ${AppModel.whenText(s.etaLo, cap = s.capYears)}에서 ${AppModel.whenText(s.etaHi, cap = s.capYears)} 사이") },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ios: Views/AnalysisView.swift:492
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/AnalysisView.swift:493,494,495
            Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterStart) {
                T(name, Modifier.background(Theme.yellow, CapsuleShape).padding(horizontal = 9.dp, vertical = 3.dp), style = appFont(12f, FontWeight.Bold), color = Theme.inkFixed, maxLines = 1)
            }
            // ios: Views/AnalysisView.swift:496,497,498,507
            BoxWithConstraints(Modifier.weight(1f).height(14.dp)) {
                val w = maxWidth
                Box(Modifier.offset(y = 6.dp).size(w, 2.dp).background(Theme.dash))   // ios: Views/AnalysisView.swift:499
                // ios: Views/AnalysisView.swift:500,501
                Canvas(Modifier.fillMaxSize()) { val g = (gx * size.width).toFloat(); drawLine(sub, Offset(g, -2.dp.toPx()), Offset(g, 16.dp.toPx()), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))) }
                Box(Modifier.offset(x = w * loA, y = 5.dp).size((w * (hiA - loA)).coerceAtLeast(6.dp), 4.dp).background(range, CapsuleShape))   // ios: Views/AnalysisView.swift:502
                Box(Modifier.offset(x = w * midA - 7.dp).size(14.dp).background(dot, CircleShape))   // ios: Views/AnalysisView.swift:503
            }
            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) { FitText(whenT, appFont(14f, FontWeight.Bold), 0.6f, color = Theme.ink) }   // ios: Views/AnalysisView.swift:508
        }
        T(desc, style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/AnalysisView.swift:510
    }
}

/** .font(.system(size:)): 큰 글자 설정을 따르지 않는다 */
@Composable
private fun fx(size: Float): androidx.compose.ui.unit.TextUnit = (size / LocalDensity.current.fontScale).sp

// MARK: 확률 박스 (상단 박스 네 테마 · 2026-10-23 확정). 글자·배치는 같고, 바탕과 막대 모양만 홈에서 고른 박스 모양을 따른다
// 막대: 노란 채움 = 지금, 흰 세로줄 = 목표(결승선), 세 점 = 목표일의 나쁠 때·보통·좋을 때
// ios: Views/AnalysisView.swift:519,537
@Composable
fun ChanceBox(look: BoxLook, d: ChanceBox.Data) {
    val dim = ChanceBox.dim
    val label = "${d.head}, ${d.verdict?.let { "가능성 $it ${d.pct}" } ?: d.status}, ${d.foot}, ${d.nowText}"   // ios: Views/AnalysisView.swift:560
    // ios: Views/AnalysisView.swift:538,555,556,557,558,559
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).moodBackdrop(look).clearAndSetSemantics { contentDescription = tr(label) }
        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp)) {   // ios: Views/AnalysisView.swift:570
        // ios: Views/AnalysisView.swift:539,540,541,542,544
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(end = 8.dp)) { FitText(d.head, TextStyle(fontSize = fx(14f)), 0.8f, color = dim) }
            FreshLabel(color = dim)
        }
        // ios: Views/AnalysisView.swift:545,563,564,565,566,567,568,571,572 (흐름 탭 큰 흐름 박스와 같은 크기)
        Box(Modifier.padding(top = 6.dp)) {
            val st = TextStyle(fontSize = fx(30f), fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            val v = d.verdict
            val ink = boxInk()
            if (v != null) FitAnnotated(buildAnnotatedString { append(tr("가능성 $v · ")); withStyle(SpanStyle(color = ink.gold)) { append(d.pct) } }, st, 0.6f, ink.ink)
            else FitText(d.status, st, 0.6f, color = ink.ink)
        }
        ChanceBar(look, d, Modifier.padding(top = 10.dp).fillMaxWidth().height(50.dp))   // ios: Views/AnalysisView.swift:567
        // 맨 아래 '셋 중 … 선을 넘음 · 지금 …' 줄은 뺀다. 박스 높이를 종목 탭 박스에 맞춘다 (2026-10-27)
    }
}

// ios: Views/AnalysisView.swift:575
@Composable
private fun ChanceBar(look: BoxLook, d: ChanceBox.Data, modifier: Modifier) {
    // ios: Views/AnalysisView.swift:576,577
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val X = { v: Double -> w * min(1.0, max(0.0, v)).toFloat() }   // ios: Views/AnalysisView.swift:578
        val xs = d.dots.map(X)
        // 이름표: 세 점과 목표 이름을 한 줄에 놓고 서로 밀어 낸다 (0~2 = 점, 3 = 목표)   // ios: Views/AnalysisView.swift:598,599
        val tags = ChanceBox.tags(xs.map { it.value }, d.goal?.let { X(it).value })
        val lx = ChanceBox.spread(tags.map { it.first }, gap = 38f, w = w.value).map { it.dp }
        val names = listOf("나쁠 때", "보통", "좋을 때")   // ios: Views/AnalysisView.swift:600
        // 막대 위에 세 점을 겹치고 이름표는 그 아래. 인물·목표 옆 그림은 이 탭에서 뺀다 (2026-10-27)
        ProgressTrack(look, d.now, Modifier.offset(y = 10.dp).width(w).height(14.dp))   // ios: Views/AnalysisView.swift:603
        val ink = boxInk(); val dk = ink.dk
        d.goal?.let { gx ->
            // 목표선 (점프 직전 라이트는 어두운 도로 위라 흰 선 + 옅은 그림자)
            val line = if (look == BoxLook.jump) Color.White else ink.ink
            if (look == BoxLook.jump && !dk) Box(Modifier.offset(x = X(gx) - 1.5.dp, y = 5.5.dp).size(3.dp, 23.dp).background(hex(0x16181D, 0.45f)))
            Box(Modifier.offset(x = X(gx) - 1.dp, y = 6.dp).size(2.dp, 22.dp).background(line))   // ios: Views/AnalysisView.swift:606,607,608
        }
        xs.indices.forEach { i ->
            val r = if (i == 2) 8.dp else 7.dp   // ios: Views/AnalysisView.swift:611
            // ios: Views/AnalysisView.swift:612,613,614
            At(xs[i], 17.dp) { Box(Modifier.size(r * 2).background(ChanceBox.dotC(dk)[i], CircleShape).then(if (i == 1 && !dk) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier)) }
        }
        tags.indices.forEach { j ->
            val k = tags[j].second
            // ios: Views/AnalysisView.swift:617,618,619,620,621
            At(lx[j], 42.dp) {
                if (k == 3) T(d.goalLabel, style = TextStyle(fontSize = fx(10f), fontWeight = if (dk) FontWeight.Normal else FontWeight.Bold), color = ink.ink, maxLines = 1)
                else T(names[k], style = TextStyle(fontSize = fx(10f)), color = ChanceBox.labC(dk)[k], maxLines = 1)
            }
        }
    }
}

// ios: Views/AnalysisView.swift:519 (struct ChanceBox 의 Data·static)
object ChanceBox {
    // ios: Views/AnalysisView.swift:520
    class Data(
        val head: String,
        val verdict: String?,        // ios: Views/AnalysisView.swift:522 낮음 · 반반 · 높음 (null = 계산 중·잠김)
        val pct: String,
        val status: String,          // ios: Views/AnalysisView.swift:524 verdict 가 없을 때 큰 글
        val now: Double,             // ios: Views/AnalysisView.swift:525 0~1
        val goal: Double?,
        val dots: List<Double>,      // ios: Views/AnalysisView.swift:527 나쁠 때 · 보통 · 좋을 때 (0~1)
        val goalLabel: String,
        val foot: String,
        val nowText: String,
    )
    val dim @Composable get() = boxInk().sub   // ios: Views/AnalysisView.swift:545
    // ios: Views/AnalysisView.swift:546,547
    fun dotC(dk: Boolean) = listOf(bx(dk, 0x7D8794, 0x8A96A3), bx(dk, 0xB5BDC7, 0xC9D0D6), bx(dk, 0x2E9C8C, 0x3FB3A3))
    fun labC(dk: Boolean) = listOf(bx(dk, 0x5F6670, 0x8A96A3), bx(dk, 0x6B7480, 0xC9D0D6), bx(dk, 0x1F8577, 0x3FB3A3))

    /** 이름표 자리: 점 바로 위, 서로 gap 보다 가까우면 밀어 낸다 (박스 밖으로 나가지 않게) */
    // ios: Views/AnalysisView.swift:598
    /** 이름표 순서: 세 점(0~2)과 목표(3)를 가로 자리 순으로 */
    fun tags(xs: List<Float>, goal: Float?): List<Pair<Float, Int>> =   // ios: Views/AnalysisView.swift:648
        (xs.mapIndexed { i, x -> x to i } + listOfNotNull(goal?.let { it to 3 })).sortedBy { it.first }

    fun spread(xs: List<Float>, gap: Float, w: Float): List<Float> {
        if (xs.isEmpty()) return emptyList()   // ios: Views/AnalysisView.swift:599
        val l = xs.map { min(w - gap / 2, max(gap / 2, it)) }.toMutableList()   // ios: Views/AnalysisView.swift:600
        for (i in 1 until l.size) if (l[i] - l[i - 1] < gap) l[i] = l[i - 1] + gap   // ios: Views/AnalysisView.swift:601
        // ios: Views/AnalysisView.swift:602
        if (l[l.size - 1] > w - gap / 2) {
            l[l.size - 1] = w - gap / 2   // ios: Views/AnalysisView.swift:603
            for (i in l.size - 2 downTo 0) if (l[i + 1] - l[i] < gap) l[i] = l[i + 1] - gap   // ios: Views/AnalysisView.swift:604
        }
        return l
    }
}

/** 확률 박스 막대: 홈에서 고른 박스 모양 (몫 띠 MoodBand 와 같은 결). fill = 지금 자리 0~1, 채운 만큼 노랑 */
// ios: Views/AnalysisView.swift:611,617,618
@Composable
fun ProgressTrack(look: BoxLook, fill: Double, modifier: Modifier = Modifier) {
    val f = min(1.0, max(0.0, fill)).toFloat()   // ios: Views/AnalysisView.swift:615
    val on = Theme.yellow   // ios: Views/AnalysisView.swift:614
    val dk = boxInk().dk
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        when (look) {
            BoxLook.jump -> Road(f, on, Modifier.fillMaxSize(), dk)   // ios: Views/AnalysisView.swift:619
            BoxLook.horizon -> Wave(f, on, Modifier.fillMaxSize(), dk)   // ios: Views/AnalysisView.swift:620
            // ios: Views/AnalysisView.swift:621,622,623,624,625,628,629,630
            BoxLook.tub -> Box(Modifier.fillMaxWidth().background(bx(dk, 0xFFFFFF, 0xEEF2F8), RoundedCornerShape(7.dp)).border(1.dp, bx(dk, 0xB7CAE3, 0xB7CAE3, 1f, 0f), RoundedCornerShape(7.dp)).padding(3.dp)) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(bx(dk, 0xCFDDF0, 0x3C5F9C))) { Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(on)) }
            }
            BoxLook.dory, BoxLook.pino, BoxLook.mug -> ThemeTrack(look, f, on)   // ios: Views/AnalysisView.swift:631,632
            // ios: Views/AnalysisView.swift:633,634,635,636,637,640
            BoxLook.bar -> Box(Modifier.fillMaxWidth().height(6.dp).background(bx(dk, 0xE6E9EE, 0x2E3B48), CapsuleShape)) { Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(on, CapsuleShape)) }
        }
    }
}

/** 점프 직전: 박스 폭 끝까지 이어진 밤 도로, 가운데 점선. 지금까지 온 칸은 노랗게 빛난다 */
// ios: Views/AnalysisView.swift:642,643,660
@Composable
private fun Road(f: Float, on: Color, modifier: Modifier, dk: Boolean) {
    Canvas(modifier.outset(18.dp).fillMaxSize()) {   // outset 은 안쪽 최소 폭을 0 으로 풀어 다시 채운다
        val pad = 18.dp.toPx(); val w = size.width - pad * 2; val cut = pad + w * f   // ios: Views/AnalysisView.swift:644
        drawRect(bx(dk, 0x2F3650, 0x151925))   // ios: Views/AnalysisView.swift:645
        drawRect(bx(dk, 0x2F3650, 0x232A3E), Offset.Zero, Size(size.width, 1.dp.toPx()))   // ios: Views/AnalysisView.swift:646
        val dash = 14.dp.toPx(); val gap = 7.dp.toPx(); val h = 3.dp.toPx(); val y = size.height / 2 - h / 2   // ios: Views/AnalysisView.swift:647
        var x = 0f   // ios: Views/AnalysisView.swift:648
        // ios: Views/AnalysisView.swift:649
        while (x < w) {
            val a = pad + x; val b = pad + min(w, x + dash)   // ios: Views/AnalysisView.swift:650
            // ios: Views/AnalysisView.swift:651,652,653,654
            if (cut > a) drawIntoCanvas { cv ->
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = on.toArgb(); setShadowLayer(2.dp.toPx(), 0f, 0f, on.toArgb()) }
                cv.nativeCanvas.drawRect(a, y, min(b, cut), y + h, p)
            }
            if (cut < b) drawRect(bx(dk, 0x5A6585, 0x3A4466), Offset(max(a, cut), y), Size(b - max(a, cut), h))   // ios: Views/AnalysisView.swift:656
            x += dash + gap   // ios: Views/AnalysisView.swift:657
        }
    }
}

/** 지평선: 물결 한 줄, 지금까지는 노랑 */
// ios: Views/AnalysisView.swift:664,665
@Composable
private fun Wave(f: Float, on: Color, modifier: Modifier, dk: Boolean) {
    Canvas(modifier) {
        val p = Path(); var x = 0f; var up = true   // ios: Views/AnalysisView.swift:666
        val mid = size.height / 2; val s = 12.dp.toPx(); val a = 6.dp.toPx()   // ios: Views/AnalysisView.swift:667
        p.moveTo(0f, mid)   // ios: Views/AnalysisView.swift:668
        // ios: Views/AnalysisView.swift:669,670,671
        while (x < size.width) { p.quadraticTo(x + s / 2, if (up) mid - a else mid + a, x + s, mid); x += s; up = !up }
        drawPath(p, bx(dk, 0xC9D0DA, 0x3A4656), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))   // ios: Views/AnalysisView.swift:673
        // ios: Views/AnalysisView.swift:674,675,676,677
        clipRect(-4.dp.toPx(), -4.dp.toPx(), size.width * f + 4.dp.toPx(), size.height + 4.dp.toPx()) {
            drawIntoCanvas { cv ->
                val pt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = on.toArgb(); style = android.graphics.Paint.Style.STROKE; strokeWidth = 3.dp.toPx(); strokeCap = android.graphics.Paint.Cap.ROUND
                    setShadowLayer(3.dp.toPx(), 0f, 0f, on.toArgb())
                }
                cv.nativeCanvas.drawPath(p.asAndroidPath(), pt)
            }
        }
    }
}

/** 분석 탭 행·칸: 누르면 그 분석 (기본 이동 링크) */
// ios: Views/AnalysisView.swift:683,684,685,686
@Composable
fun Modifier.openOnTap(m: AppModel, route: AnalysisRoute): Modifier = this.plainClickable { m.analysisPath = m.analysisPath + route }

// 목표일 전망: 그래프 먼저, 그 밑 세 가지 미래 표의 줄을 눌러 시나리오를 고른다. 확률·근거 글은 요약 박스에서 이미 봤으니 두지 않는다 (2026-10-28)
// ios: Views/AnalysisView.swift:761,765
@Composable
fun ForecastView(m: AppModel) {
    var showEv by remember { mutableStateOf(false) }   // ios: Views/AnalysisView.swift:763
    val fs = Lens.entries.associateWith { m.forecast(it) }   // ios: Views/AnalysisView.swift:766 (같은 경로를 μ만 바꿔 다시 무게 달기: 엔진이 딴 줄에서 하고 결과를 둔다)
    val sel = m.lens; val f = fs[sel] ?: m.forecast   // ios: Views/AnalysisView.swift:767
    // 몬테카를로 경로는 종목만이라 현금을 더해 지금 평가액과 같은 기준으로 (닫힌 식은 이미 포함)
    val add = { x: AppModel.Forecast -> if (x.exact) m.cashKrw else 0.0 }   // ios: Views/AnalysisView.swift:769
    val now = (f.q50.firstOrNull() ?: 0.0) + add(f)   // ios: Views/AnalysisView.swift:770
    val med = (f.q50.lastOrNull() ?: 0.0) + add(f); val lo = (f.q5.lastOrNull() ?: 0.0) + add(f); val hi = (f.q95.lastOrNull() ?: 0.0) + add(f)   // ios: Views/AnalysisView.swift:771
    val chg = if (now > 0) med / now - 1 else 0.0   // ios: Views/AnalysisView.swift:772
    val end = ForecastView.ym(m.forecastHorizon)   // ios: Views/AnalysisView.swift:773
    val others: List<Pair<String, List<Double>>> = ForecastView.others(fs, sel = sel, add = add)   // ios: Views/AnalysisView.swift:774
    val hits = MarketEvents.upcoming(held = m.rows.map { it.id }, days = 60).take(4).sortedBy { it.day }   // ios: Views/AnalysisView.swift:775

    // ios: Views/AnalysisView.swift:776,816,817,818,820
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        // ios: Views/AnalysisView.swift:777,815
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailHead(title = m.forecastTitle, sub = if (f.exact) "" else "계산 중 (어림값)")   // ios: Views/AnalysisView.swift:778

            // ios: Views/AnalysisView.swift:785
            Card {
                // Views/AnalysisView.swift:786 (검사 예외: 같은 글)
                T(if (m.isGoal) "${end.second} 내 자산은?" else "3년 뒤 내 자산은?", style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/AnalysisView.swift:787
                // ios: Views/AnalysisView.swift:788
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // ios: Views/AnalysisView.swift:789
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("보통", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisView.swift:790
                        // ios: Views/AnalysisView.swift:791,792 (.font(.system(size: 22)): 큰 글자 설정을 따르지 않는다)
                        FitText(ForecastView.amt(med), TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold), 0.7f, Modifier.weight(1f, fill = false).alignByBaseline(), maxLines = 1, color = Theme.ink, roll = ForecastView.amt(med))
                        T(AppModel.sgn0(chg), Modifier.alignByBaseline(), style = appFont(13f, FontWeight.Bold), color = if (chg >= 0) Theme.green else Theme.up)   // ios: Views/AnalysisView.swift:793
                    }
                    T("10번 중 9번 ${ForecastView.amt(lo)} ~ ${ForecastView.amt(hi)}", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisView.swift:795
                }
                // ios: Views/AnalysisView.swift:797,798,799,800
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    ScenarioChart(sel = f, selShift = add(f), selLabel = ForecastView.short(sel), others = others,
                                  goal = m.keyValue, goalLabel = m.keyName, endLabel = if (m.isGoal) end.second else "3년 뒤", modifier = Modifier.height(190.dp))
                    if (hits.isNotEmpty()) EvFlag(m, hits, span = f.tY.lastOrNull() ?: 1.0, showEv) { showEv = !showEv }
                }
                Legend()   // ios: Views/AnalysisView.swift:801
                if (showEv && hits.isNotEmpty()) EvList(m, hits)   // ios: Views/AnalysisView.swift:802
                // ios: Views/AnalysisView.swift:803
                if (f.mu > 0.30) {
                    // ios: Views/AnalysisView.swift:804,805,806
                    T("연 ${AppModel.sgn0(f.mu)}는 드문 속도 · 실제론 더 낮을 수도", Modifier.fillMaxWidth().background(Theme.cream, RoundedCornerShape(10.dp)).padding(10.dp),
                      style = appFont(12f), color = Theme.ink)
                }
            }

            Card { Table(m, fs, add = add) }   // ios: Views/AnalysisView.swift:805

            // ios: Views/AnalysisView.swift:810,811
            T("지난 3년 기준 3,000가지 경우", style = appFont(12f), color = Theme.muted)
            // ios: Views/AnalysisView.swift:812,813
            T("구성 바꾸기 → 목표 고치기", Modifier.plainClickable { m.analysisPath = m.analysisPath + GoalRoute0 }, style = appFont(14f, FontWeight.Bold), color = Theme.teal)
        }
    }
}

/** 전망에서 여는 목표 고치기 (NavigationLink { GoalSetView(fromSettings: true) }) */
object GoalRoute0
@Composable
fun GoalSetPage(m: AppModel) = GoalSetView(m, fromSettings = true)

/** 판정 기간: 목표 = 목표일까지, 회복 = 내 길 기간 (3년까지). 분석 탭 확률 박스와 같은 셈 */
// ios: Views/AnalysisView.swift:855,856,857,858
private val AppModel.planYears: Double get() = if (isGoal) yrs(Day.today, Day.str(goalToDate)) else myPath.let { p -> min(3.0, p.end - p.start) }
// ios: Views/AnalysisView.swift:860
private fun AppModel.plan(f: AppModel.Forecast): Double = if (isGoal) f.pGoal else f.prob(planYears)

/** 슬라이더 ('내 설정' 줄을 골랐을 때만 그 줄 밑에 펼친다). 끝까지 밀어도 '내 설정'에 머문다 */
// ios: Views/AnalysisView.swift:887
@Composable
private fun TrustSlider(m: AppModel) {
    // ios: Views/AnalysisView.swift:889,899
    Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // ios: Views/AnalysisView.swift:890
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("지난 3년 추세 반영", Modifier.weight(1f), style = appFont(12f, FontWeight.Bold))   // ios: Views/AnalysisView.swift:891
            T("${m.trust.toInt()}%", style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/AnalysisView.swift:893
        }
        // ios: Views/AnalysisView.swift:895 (0~100, 5씩)
        IosSlider(m.trust, { m.trust = it }, 0.0..100.0, step = 5.0, modifier = Modifier.semantics { contentDescription = tr("지난 3년 추세를 얼마나 믿나요") }, tint = Theme.teal)
        // ios: Views/AnalysisView.swift:896,897
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("신중", style = appFont(11f), color = Theme.muted); Spacer(Modifier.weight(1f)); T("반반", style = appFont(11f), color = Theme.muted); Spacer(Modifier.weight(1f)); T("3년처럼", style = appFont(11f), color = Theme.muted)
        }
    }
}

// ios: Views/AnalysisView.swift:902
@Composable
private fun Legend() {
    val muted = Theme.muted
    // ios: Views/AnalysisView.swift:903
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Sw(Theme.teal.copy(alpha = 0.35f), "10번 중 5번")   // ios: Views/AnalysisView.swift:904
        Sw(Theme.teal.copy(alpha = 0.14f), "10번 중 9번")   // ios: Views/AnalysisView.swift:905
        // ios: Views/AnalysisView.swift:906
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/AnalysisView.swift:907,908
            Canvas(Modifier.size(14.dp, 10.dp)) { drawLine(muted, Offset(0f, 5.dp.toPx()), Offset(14.dp.toPx(), 5.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) }
            T("다른 시나리오", style = appFont(11f), color = Theme.sub)   // ios: Views/AnalysisView.swift:909,912
        }
    }
}
// ios: Views/AnalysisView.swift:914,915
@Composable
private fun Sw(c: Color, t: String) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp, 10.dp).background(c, RoundedCornerShape(2.dp))); T(t, style = appFont(11f), color = Theme.sub)
}

// MARK: 세 가지 미래: 줄을 누르면 시나리오가 바뀌고 그래프가 같이 움직인다
// ios: Views/AnalysisView.swift:919
@Composable
private fun Table(m: AppModel, fs: Map<Lens, AppModel.Forecast>, add: (AppModel.Forecast) -> Double) {
    val lineName = if (m.isGoal) ForecastView.amt(m.keyValue) else "본전"   // ios: Views/AnalysisView.swift:920
    // ios: Views/AnalysisView.swift:926
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ios: Views/AnalysisView.swift:927,932,933
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T("눌러서 고르기", Modifier.weight(1f), style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisView.swift:928
            T("보통", Modifier.width(62.dp), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.muted)   // ios: Views/AnalysisView.swift:929
            T("나쁘면", Modifier.width(56.dp), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.muted)   // ios: Views/AnalysisView.swift:930
            T("$lineName↑", Modifier.width(64.dp), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.muted)   // ios: Views/AnalysisView.swift:931
        }
        // ios: Views/AnalysisView.swift:934
        Lens.entries.forEach { l ->
            val x = fs[l] ?: return@forEach   // ios: Views/AnalysisView.swift:935
            val on = m.lens == l   // ios: Views/AnalysisView.swift:936
            val st = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal)   // ios: Views/AnalysisView.swift:949
            // ios: Views/AnalysisView.swift:937,957 (withAnimation: 슬라이더가 펼쳐지는 높이)
            Column(Modifier.fillMaxWidth().background(if (on) Theme.tealBg else Color.Transparent, RoundedCornerShape(10.dp)).animateContentSize(tween(200))) {
                // ios: Views/AnalysisView.swift:938,939,950,951,953,954
                Row(Modifier.fillMaxWidth().plainClickable { m.lens = l }.semantics { selected = on }
                    .padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ios: Views/AnalysisView.swift:940,944
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        T(ForecastView.short(l), style = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), color = Theme.ink)   // ios: Views/AnalysisView.swift:941
                        T(AppModel.sgn0(x.mu), style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisView.swift:942
                    }
                    T(ForecastView.amt((x.q50.lastOrNull() ?: 0.0) + add(x)), Modifier.width(62.dp), style = st.copy(textAlign = TextAlign.End), color = Theme.ink)   // ios: Views/AnalysisView.swift:945
                    T(ForecastView.amt((x.q5.lastOrNull() ?: 0.0) + add(x)), Modifier.width(56.dp), style = st.copy(textAlign = TextAlign.End), color = Theme.ink)   // ios: Views/AnalysisView.swift:946
                    T(AppModel.pct(m.plan(x)), Modifier.width(64.dp), style = st.copy(textAlign = TextAlign.End), color = if (on) Theme.teal else Theme.ink)   // ios: Views/AnalysisView.swift:947
                }
                if (on && l == Lens.mine) TrustSlider(m)   // ios: Views/AnalysisView.swift:955
            }
        }
    }
}

// MARK: 흔들 일정: 그래프 위 깃발 하나 (일정이 모두 앞쪽 한 달 안이라 하나로 묶는다). 누르면 아래에 목록과 반영 단추
// ios: Views/AnalysisView.swift:965
@Composable
private fun EvFlag(m: AppModel, hits: List<MarketEvents.Hit>, span: Double, open: Boolean, toggle: () -> Unit) {
    val last = Day.dday(hits.lastOrNull()?.day ?: Day.today) / 365.25   // ios: Views/AnalysisView.swift:966
    val on = m.shock   // ios: Views/AnalysisView.swift:967
    val c = if (on) hex(0xA8521A) else Theme.muted   // ios: Views/AnalysisView.swift:968
    // ios: Views/AnalysisView.swift:969
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val left = 36.dp; val W = maxWidth - left   // ios: Views/AnalysisView.swift:970
        val x = left + W * min(1.0, max(0.0, last / max(1e-6, span))).toFloat()   // ios: Views/AnalysisView.swift:971
        // ios: Views/AnalysisView.swift:973
        Box(Modifier.offset(x = x, y = 18.dp).width(1.dp).height((maxHeight - 20.dp - 18.dp).coerceAtLeast(0.dp)).background(c.copy(alpha = 0.7f)))
        val px = minOf(maxOf(left, x - 4.dp), maxOf(left, maxWidth - 90.dp))   // ios: Views/AnalysisView.swift:981
        // Views/AnalysisView.swift:975 (검사 예외: 같은 글)
        val label = if (on) "일정 ${hits.size} ›" else "일정 ${hits.size} · 뺌 ›"
        // ios: Views/AnalysisView.swift:974,976,977,978,980,982
        T(label, Modifier.offset(x = px).height(18.dp).plainClickable { toggle() }
            .semantics { contentDescription = tr("흔들 일정 ${hits.size}개, ${tr(if (on) "계산에 반영" else "계산에서 뺌")}") }
            .background(if (on) c else Theme.line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp),
          style = appFont(11f, FontWeight.Bold), color = if (on) Color.White else Theme.ink, maxLines = 1)
    }
}

// ios: Views/AnalysisView.swift:987
@Composable
private fun EvList(m: AppModel, hits: List<MarketEvents.Hit>) {
    // ios: Views/AnalysisView.swift:989,993,994
    Column(Modifier.fillMaxWidth().background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleRow(m.shock, { m.shock = it }, tint = Theme.teal) { T("계산에 반영", style = appFont(13f, FontWeight.Bold)) }   // ios: Views/AnalysisView.swift:990
        hits.indices.forEach { i -> ScheduleRow(hit = hits[i]) { r -> m.analysisPath = m.analysisPath + r } }   // ios: Views/AnalysisView.swift:991
    }
}

// ios: Views/AnalysisView.swift:761
object ForecastView {
    /** 비교선: 고르지 않은 두 시나리오의 중앙값 (현금 더함) */
    // ios: Views/AnalysisView.swift:491
    fun others(fs: Map<Lens, AppModel.Forecast>, sel: Lens, add: (AppModel.Forecast) -> Double): List<Pair<String, List<Double>>> {
        val out = ArrayList<Pair<String, List<Double>>>()   // ios: Views/AnalysisView.swift:492
        // ios: Views/AnalysisView.swift:493
        for (l in Lens.entries) { if (l == sel) continue
            fs[l]?.let { x -> val c = add(x); out.add(Pair(short(l), x.q50.map { it + c })) }   // ios: Views/AnalysisView.swift:494
        }
        return out   // ios: Views/AnalysisView.swift:496
    }

    // MARK: 표시
    /** 3.30억 · 11.5억 · 826만 (목업 표기) */
    // ios: Views/AnalysisView.swift:501
    fun amt(v: Double): String {
        val a = abs(v); val s = if (v < 0) "−" else ""   // ios: Views/AnalysisView.swift:502
        if (a >= 1e9) return s + cformat("%.1f억", a / 1e8)   // ios: Views/AnalysisView.swift:503
        if (a >= 1e8) return s + cformat("%.2f억", a / 1e8)   // ios: Views/AnalysisView.swift:504
        return s + cformat("%,d", (a / 1e4).swRound().toLong()) + "만"   // ios: Views/AnalysisView.swift:505
    }
    // ios: Views/AnalysisView.swift:507
    fun short(l: Lens): String = if (l == Lens.base) "신중" else if (l == Lens.smooth) "3년처럼" else "내 설정"
    /** "2029-10-14" → ("2029년 10월", "2029.10") */
    // ios: Views/AnalysisView.swift:509
    fun ym(d: String): Pair<String, String> {
        val p = d.split("-")   // ios: Views/AnalysisView.swift:510
        val y = p.getOrNull(0)?.toIntOrNull(); val mo = p.getOrNull(1)?.toIntOrNull(); if (p.size < 2 || y == null || mo == null) return Pair(d, d)   // ios: Views/AnalysisView.swift:511
        return Pair("${y}년 ${mo}월", cformat("%d.%02d", y, mo))   // Views/AnalysisView.swift:518 (검사 예외: 같은 글)
    }
}

