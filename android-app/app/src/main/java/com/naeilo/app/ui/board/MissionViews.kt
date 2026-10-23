package com.naeilo.app.ui.board

import android.content.Intent
import com.naeilo.app.core.tr
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.Tab
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.calc.Basket
import com.naeilo.app.core.calc.ChartPlan
import com.naeilo.app.core.calc.Deposit
import com.naeilo.app.core.calc.MissionRoute
import com.naeilo.app.core.calc.PathChart
import com.naeilo.app.core.calc.Plan
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.deposit
import com.naeilo.app.core.calc.eta
import com.naeilo.app.core.calc.focusIndustry
import com.naeilo.app.core.calc.focusName
import com.naeilo.app.core.calc.focusRow
import com.naeilo.app.core.calc.freedAmount
import com.naeilo.app.core.calc.graduateToGoal
import com.naeilo.app.core.calc.halfway
import com.naeilo.app.core.calc.horizonLabel
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keepPlan
import com.naeilo.app.core.calc.lossShareFocus
import com.naeilo.app.core.calc.planBasket
import com.naeilo.app.core.calc.plans
import com.naeilo.app.core.calc.quizRight
import com.naeilo.app.core.calc.restName
import com.naeilo.app.core.calc.restRet
import com.naeilo.app.core.calc.restRows
import com.naeilo.app.core.calc.saveBasket
import com.naeilo.app.core.calc.selectedPlan
import com.naeilo.app.core.calc.taxAfter
import com.naeilo.app.core.calc.taxBefore
import com.naeilo.app.core.calc.taxLossMan
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.core.cformat
import com.naeilo.app.core.swFmtNoGroup
import com.naeilo.app.data.Currency
import com.naeilo.app.data.EnData
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Period
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.short
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.last
import com.naeilo.app.net.quote
import com.naeilo.app.data.Notifier
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.GuideBubble
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.LogoTile
import com.naeilo.app.ui.MotionSprite
import com.naeilo.app.ui.NavBar
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.NavStack
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.Pose
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.ScreenTitle
import com.naeilo.app.ui.Sparkline
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.goal.GoalHoldResultView
import com.naeilo.app.ui.goal.GoalIntermissionView
import com.naeilo.app.ui.goal.GoalMixResultView
import com.naeilo.app.ui.goal.GoalMixView
import com.naeilo.app.ui.goal.GoalSetResultView
import com.naeilo.app.ui.goal.GoalSetView
import com.naeilo.app.ui.goal.GoalTaxView
import com.naeilo.app.ui.guideFriend
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.hold.AddHoldingView
import com.naeilo.app.ui.home.FlowRow
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.ui.me.MeEditView
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.settings.SettingsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// 회복 루트 미션 화면 (시안 m1~m4r, nx). 숫자 설명은 모두 계산값, 종목 추천 문구 없음.
// Views/MissionViews.swift 전체 (1-751).

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

/**
 * 미션 판 NavStack 의 화면 하나 (Swift .navigationDestination(for: MissionRoute.self) { MissionScreen(route: $0) }).
 * App.kt: Tab.board -> NavStack(model.boardPath, { model.boardPath = it }, { BoardView(model) }) { missionPage(model, it) }
 * 앱 시작 3단계(nx)는 안에서 내 캐릭터 화면을 한 겹 더 밀어서 이동 막대를 스스로 그린다 (bar = false).
 */
fun missionPage(m: AppModel, r: MissionRoute): NavPage = when (r) {
    MissionRoute.m3 -> NavPage(Mission3Nav.title) { MissionScreen(m, r) }
    MissionRoute.nx -> NavPage(bar = false) { MissionScreen(m, r) }
    else -> NavPage { MissionScreen(m, r) }
}

// ios: Views/MissionViews.swift:5,6,7
@Composable
fun MissionScreen(m: AppModel, route: MissionRoute) {
    // ios: Views/MissionViews.swift:8
    when (route) {
        MissionRoute.m1 -> Mission1View(m)   // ios: Views/MissionViews.swift:9
        MissionRoute.m1r -> Mission1ResultView(m)   // ios: Views/MissionViews.swift:10
        MissionRoute.m2 -> Mission2View(m)   // ios: Views/MissionViews.swift:11
        MissionRoute.m2r -> Mission2ResultView(m)   // ios: Views/MissionViews.swift:12
        MissionRoute.m3 -> Mission3View(m)   // ios: Views/MissionViews.swift:13
        MissionRoute.m3r -> Mission3ResultView(m)   // ios: Views/MissionViews.swift:14
        MissionRoute.m4 -> TaxInputView(m)   // ios: Views/MissionViews.swift:15
        MissionRoute.m4r -> TaxResultView(m)   // ios: Views/MissionViews.swift:16
        MissionRoute.nx -> AppStartView(m)   // ios: Views/MissionViews.swift:17
        MissionRoute.gp1r -> GoalHoldResultView(m)   // ios: Views/MissionViews.swift:18
        MissionRoute.g1 -> GoalSetView(m)   // ios: Views/MissionViews.swift:19
        MissionRoute.g1r -> GoalSetResultView(m)   // ios: Views/MissionViews.swift:20
        MissionRoute.g3 -> GoalMixView(m)   // ios: Views/MissionViews.swift:21
        MissionRoute.g3r -> GoalMixResultView(m)   // ios: Views/MissionViews.swift:22
        MissionRoute.gt -> GoalTaxView(m)   // ios: Views/MissionViews.swift:23
        MissionRoute.gi -> GoalIntermissionView(m)   // ios: Views/MissionViews.swift:24
    }
}

// 공통 틀
// ios: Views/MissionViews.swift:30,34
@Composable
fun MissionPage(
    kicker: String,   // ios: Views/MissionViews.swift:31
    title: String,   // ios: Views/MissionViews.swift:32
    content: @Composable ColumnScope.() -> Unit,   // ios: Views/MissionViews.swift:33
) {
    // ios: Views/MissionViews.swift:35,42
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/MissionViews.swift:40
        Screen(Modifier.padding(top = 8.dp)) {
            // ios: Views/MissionViews.swift:36
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle(kicker = kicker, title = title)   // ios: Views/MissionViews.swift:37
                content()   // ios: Views/MissionViews.swift:38
            }
        }
    }
    // ios: Views/MissionViews.swift:43 (inline 제목 줄은 NavStack 의 NavBar 몫)
}

// ios: Views/MissionViews.swift:47,48,51
@Composable
fun BoardButtons(m: AppModel,
    next: String?,   // ios: Views/MissionViews.swift:49
    to: MissionRoute?,   // ios: Views/MissionViews.swift:50
) {
    // ios: Views/MissionViews.swift:52
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (next != null && to != null) PrimaryButton(title = next) { m.boardPath = m.boardPath + to }   // ios: Views/MissionViews.swift:53
        // ios: Views/MissionViews.swift:54,55
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).plainClickable { m.boardPath = emptyList() }, contentAlignment = Alignment.Center) { T("미션 판으로", style = appFont(15f, FontWeight.SemiBold), color = Theme.teal) }
    }
}

// ios: Views/MissionViews.swift:60
@Composable
private fun horizonChips(sel: Int, onSel: (Int) -> Unit) {
    ChipRow(items = listOf(3 to "3개월", 6 to "6개월", 12 to "1년"), selection = sel, onSelect = onSel, fill = true)   // ios: Views/MissionViews.swift:61
}

// ios: Views/MissionViews.swift:64
@Composable
private fun statBox(k: String, v: String, c: Color = Theme.teal, modifier: Modifier = Modifier) {
    // ios: Views/MissionViews.swift:65,69,70,71
    Column(modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(k, style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:66
        T(v, style = appFont(26f, FontWeight.Bold), color = c, roll = v)   // ios: Views/MissionViews.swift:67
    }
}

/** 글 한 줄 폭 (px): ViewThatFits 가 재는 이상적인 폭 자리 */
@Composable
private fun widthOf(s: String, st: TextStyle): Int {
    val tm = rememberTextMeasurer()
    return remember(s, st) { tm.measure(s, st, maxLines = 1).size.width }
}

/** ViewThatFits(in: .horizontal) 자리: 가로 줄에 필요한 폭(needPx)이 들어가면 first(폭을 나눠 채움), 아니면 second */
@Composable
private fun FitsRow(needPx: Int, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) { if (needPx <= constraints.maxWidth) first() else second() }
}

/** statBox 한 칸이 가로로 필요한 폭 (padding 14 양쪽 + 큰 글자) */
@Composable
private fun statW(k: String, v: String): Int {
    val pad = with(LocalDensity.current) { 28.dp.roundToPx() }
    return max(widthOf(k, appFont(13f)), widthOf(v, appFont(26f, FontWeight.Bold))) + pad
}

// MARK: 미션 1 — 종목·단가 넣기
// 내 종목에 있는 종목을 그대로 보여 주고 수량·평균 단가를 고친다. 샘플 종목이면 빈칸으로 시작
// ios: Views/MissionViews.swift:76,77,84
@Composable
fun Mission1View(m: AppModel) {
    val ids = remember { mutableStateListOf<String>() }   // ios: Views/MissionViews.swift:78
    val q = remember { mutableStateMapOf<String, String>() }   // ios: Views/MissionViews.swift:79
    val p = remember { mutableStateMapOf<String, String>() }   // ios: Views/MissionViews.swift:80

    fun num(t: String?): Double = (t ?: "").replace(",", "").toDoubleOrNull() ?: 0.0   // ios: Views/MissionViews.swift:82

    // ios: Views/MissionViews.swift:111
    fun load() {
        // 샘플(가상 DRNK·QQQ)이면 빈칸으로 시작한다: 종목 추가로 내 종목을 넣는다
        val want = if (m.isSample) emptyList() else m.holdings.map { it.symbol }.filter { it != KoreaEstimate.sumId }   // ios: Views/MissionViews.swift:113 국내 주식 합계는 아래 칸에서
        // ios: Views/MissionViews.swift:114
        for (id in want) { if (ids.contains(id)) continue
            ids.add(id)   // ios: Views/MissionViews.swift:115
            // ios: Views/MissionViews.swift:116
            m.holdings.firstOrNull { it.symbol == id }?.let { h -> q[id] = h.qty.swFmtNoGroup(); p[id] = h.avg.swFmtNoGroup() }
        }
    }

    val ok = ids.any { num(q[it]) > 0 && num(p[it]) > 0 }   // ios: Views/MissionViews.swift:85
    // ios: Views/MissionViews.swift:86
    MissionPage(kicker = "미션 1 / 4", title = "종목과 산 가격은?") {
        GuideBubble(m, pose = Pose.point, text = "매수 단가가 있어야 \"본전\"을 계산할 수 있어요. " + (if (m.syncOn) "동기화가 켜져 있어서, 이 폰에서 암호화한 값만 같은 비밀번호의 기기로 가요." else "넣은 숫자는 기기 밖으로 나가지 않아요."))   // ios: Views/MissionViews.swift:87
        // ios: Views/MissionViews.swift:88
        ids.forEach { id ->
            Sample.symbol(id)?.let { s -> key(id) { entry(s, q, p) } }   // ios: Views/MissionViews.swift:89
        }
        // '+종목 추가' 버튼 대신 그 자리에 바로 종목 추가 상자
        AddHoldingView(m, inline = true)   // ios: Views/MissionViews.swift:92
        // ios: Views/MissionViews.swift:93
        PrimaryButton(title = if (ok) (if (m.route == Route.recover) "회복 확률 보기" else "평가액 보기") else "수량·단가 넣기", color = if (ok) Theme.teal else Theme.muted) {
            if (!ok) return@PrimaryButton   // ios: Views/MissionViews.swift:94
            val hs = m.holdings.toMutableList()
            // ios: Views/MissionViews.swift:95
            for (id in ids) {
                val qn = num(q[id]); val pn = num(p[id])   // ios: Views/MissionViews.swift:96
                val i = hs.indexOfFirst { it.symbol == id }   // ios: Views/MissionViews.swift:97
                if (i >= 0) {
                    if (qn > 0 && pn > 0) hs[i] = Holding(symbol = id, qty = qn, avg = pn) else hs.removeAt(i)   // ios: Views/MissionViews.swift:98
                } else if (qn > 0 && pn > 0) hs.add(Holding(symbol = id, qty = qn, avg = pn))   // ios: Views/MissionViews.swift:99
            }
            m.holdings = hs
            if (m.route == Route.recover) { m.done = m.done + 1; m.boardPath = m.boardPath + MissionRoute.m1r }   // ios: Views/MissionViews.swift:101
            else { m.gDone = m.gDone + "hold"; m.boardPath = m.boardPath + MissionRoute.gp1r }   // ios: Views/MissionViews.swift:102
        }
    }
    // 종목 추가에서 돌아오면 새 종목도 칸에 넣는다
    LaunchedEffect(Unit) { load() }   // ios: Views/MissionViews.swift:107
    LaunchedEffect(m.holdings) { load() }   // ios: Views/MissionViews.swift:108
}

/** 지금 가격 (시세 기준과 같음). DRNK 는 가상 종목 */
// ios: Views/MissionViews.swift:121
private fun now(s: Symbol): String =
    (if (s.quote.live) "지금 " else "전일 종가 ") + AppModel.price(s, s.last) + (if (s.id == "DRNK") " (가상)" else "")   // ios: Views/MissionViews.swift:122

// ios: Views/MissionViews.swift:125
@Composable
private fun entry(s: Symbol, q: MutableMap<String, String>, p: MutableMap<String, String>) {
    val unit = if (s.currency == Currency.usd) "$" else "원"   // ios: Views/MissionViews.swift:126
    val qb = q[s.id] ?: ""; val pb = p[s.id] ?: ""; val qSet = { v: String -> q[s.id] = v }; val pSet = { v: String -> p[s.id] = v }   // ios: Views/MissionViews.swift:127
    // ios: Views/MissionViews.swift:128
    Card {
        // ios: Views/MissionViews.swift:129
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { LogoTile(symbol = s.id, size = 28.dp); T(s.short, style = appFont(17f, FontWeight.Bold)); Spacer(Modifier.weight(1f)); T(now(s), style = appFont(13f), color = Theme.sub) }
        // ios: Views/MissionViews.swift:130 (ViewThatFits: 두 칸 글 폭이 다 들어가면 가로로)
        val pad = with(LocalDensity.current) { 24.dp.roundToPx() to 10.dp.roundToPx() }
        val lab = appFont(13f, FontWeight.SemiBold); val big = appFont(18f, FontWeight.SemiBold)
        val need = max(widthOf("수량(주)", lab), widthOf(qb, big) + pad.first) + max(widthOf("평균 단가(${unit})", lab), widthOf(pb, big) + pad.first) + pad.second
        FitsRow(need, first = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { field("수량(주)", qb, qSet, Modifier.weight(1f)); field("평균 단가(${unit})", pb, pSet, Modifier.weight(1f)) }   // ios: Views/MissionViews.swift:131
        }, second = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { field("수량(주)", qb, qSet, Modifier.fillMaxWidth()); field("평균 단가(${unit})", pb, pSet, Modifier.fillMaxWidth()) }   // ios: Views/MissionViews.swift:132
        })
    }
}

// ios: Views/MissionViews.swift:137
@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    // ios: Views/MissionViews.swift:138
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(label, style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/MissionViews.swift:139
        // ios: Views/MissionViews.swift:142
        Box(Modifier.fillMaxWidth().border(2.dp, Theme.border, RoundedCornerShape(10.dp))
            // ios: Views/MissionViews.swift:141
            .padding(horizontal = 12.dp).defaultMinSize(minHeight = 48.dp)
            // ios: Views/MissionViews.swift:143
            .semantics { contentDescription = tr(label) }, contentAlignment = Alignment.CenterStart) {
            // ios: Views/MissionViews.swift:140
            BasicTextField(value, onChange, Modifier.fillMaxWidth(), textStyle = appFont(18f, FontWeight.SemiBold).copy(color = Theme.ink), singleLine = true, cursorBrush = SolidColor(Theme.teal), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
}

// MARK: 미션 1 결과 — 회복 확률
// ios: Views/MissionViews.swift:149,150,151
@Composable
fun Mission1ResultView(m: AppModel) {
    // ios: Views/MissionViews.swift:152 (@Bindable: 기간 칩은 m.horizon 을 바로 바꾼다)
    val keep = m.keepPlan; val Ty = m.horizon.toDouble() / 12   // ios: Views/MissionViews.swift:153
    // ios: Views/MissionViews.swift:154
    MissionPage(kicker = "미션 1 완료 · 회복 확률 열림", title = "지금 평가액과 본전") {
        // ios: Views/MissionViews.swift:155
        if (m.ret >= 0) {
            // ios: Views/MissionViews.swift:156
            Card {
                T("이미 본전 위 (${AppModel.sgn(m.ret)})", style = appFont(17f, FontWeight.Bold))   // ios: Views/MissionViews.swift:157
                T("목표 루트가 맞아요 · 종목·위젯·친구 그대로", style = appFont(14f), color = Theme.sub)   // ios: Views/MissionViews.swift:158
                PrimaryButton(title = "목표 루트로") { m.graduateToGoal() }   // ios: Views/MissionViews.swift:159
            }
        } else {   // ios: Views/MissionViews.swift:161
            GuideBubble(m, pose = Pose.spread, text = "…… 여기가 지금 위치예요. 본전까지는 ${AppModel.sgn(m.cost / max(1.0, m.total) - 1)}가 필요해요. 기간을 바꾸면 확률이 다시 계산돼요.")   // ios: Views/MissionViews.swift:162
            // ios: Views/MissionViews.swift:163,174,175
            Fg(Color.White) { Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ios: Views/MissionViews.swift:164,165
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { val st = appFont(13f); val c = hex(0xC9D0D6); T("지금 평가액", Modifier.weight(1f), style = st, color = c); T("들어간 돈 ${AppModel.man(m.cost)}", style = st, color = c) }
                // ios: Views/MissionViews.swift:166
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(AppModel.man(m.total), Modifier.alignByBaseline(), style = appFont(30f, FontWeight.Bold), roll = AppModel.man(m.total))   // ios: Views/MissionViews.swift:167
                    T(AppModel.sgn(m.ret), Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold), color = hex(0x8CC4F2))   // ios: Views/MissionViews.swift:168
                }
                ProgressBar(value = m.total / m.cost)   // ios: Views/MissionViews.swift:170
                // ios: Views/MissionViews.swift:171
                T("본전의 ${AppModel.pct(min(1.0, m.total / m.cost))} · 절반 회복 ${AppModel.sgn(m.halfway / m.total - 1)} · 본전 ${AppModel.sgn(m.cost / m.total - 1)} 올라야",
                  style = appFont(14f), color = hex(0xC9D0D6))   // ios: Views/MissionViews.swift:172
            } }
            horizonChips(m.horizon) { m.horizon = it }   // ios: Views/MissionViews.swift:176
            // ios: Views/MissionViews.swift:177 (ViewThatFits: 두 칸 글 폭이 다 들어가면 나란히)
            val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
            val need = statW("${m.horizonLabel} 안 절반 회복", AppModel.pct(keep.prob(m.halfway, Ty))) + statW("${m.horizonLabel} 안 본전", AppModel.pct(keep.prob(m.cost, Ty))) + gap
            FitsRow(need, first = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { probBoxes(m, keep, Ty, Modifier.weight(1f)) }   // ios: Views/MissionViews.swift:178
            }, second = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { probBoxes(m, keep, Ty, Modifier.fillMaxWidth()) }   // ios: Views/MissionViews.swift:179
            })
            // ios: Views/MissionViews.swift:181
            Card {
                T("${m.horizonLabel} 뒤 내 손익", style = appFont(14f, FontWeight.Bold))   // ios: Views/MissionViews.swift:182
                outcomeRow("나쁜 경우", keep.outcome(m.cost, Ty, -1.0))   // ios: Views/MissionViews.swift:183
                outcomeRow("보통", keep.outcome(m.cost, Ty, 0.0))   // ios: Views/MissionViews.swift:184
                outcomeRow("좋은 경우", keep.outcome(m.cost, Ty, 1.0))   // ios: Views/MissionViews.swift:185
                T("10번 중 9번 이 사이 · 지금 ${AppModel.sgn(m.ret)}", style = appFont(12f), color = Theme.muted)   // ios: Views/MissionViews.swift:186
            }
        }
        // 본전 위면 원인 진단은 건너뛴다 (목표 루트로만)
        BoardButtons(m, next = if (m.ret >= 0) null else "다음: 원인 진단", to = if (m.ret >= 0) null else MissionRoute.m2)   // ios: Views/MissionViews.swift:190
    }
}

// ios: Views/MissionViews.swift:193
@Composable
private fun probBoxes(m: AppModel, keep: Plan, Ty: Double, mod: Modifier) {
    statBox("${m.horizonLabel} 안 절반 회복", AppModel.pct(keep.prob(m.halfway, Ty)), modifier = mod)   // ios: Views/MissionViews.swift:194
    statBox("${m.horizonLabel} 안 본전", AppModel.pct(keep.prob(m.cost, Ty)), modifier = mod)   // ios: Views/MissionViews.swift:195
}

// ios: Views/MissionViews.swift:197
@Composable
private fun outcomeRow(k: String, v: Double) {
    // ios: Views/MissionViews.swift:198,199
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { val st = appFont(15f); T(k, Modifier.weight(1f), style = st, color = Theme.sub); T(AppModel.sgn0(v), style = st.copy(fontWeight = FontWeight.Bold), color = Theme.change(v)) }
}

// 회복 루트 적립 지렛대 (계획 4안과 별개): 매달 더 넣으면 본전이 언제. 비율·확률로만 보인다
// ios: Views/MissionViews.swift:204,205,207
@Composable
fun DepositLeverCard(m: AppModel,
    plan: Plan,   // ios: Views/MissionViews.swift:206
) {
    // ios: Views/MissionViews.swift:208 (@Bindable: 칩은 m.recM 을 바로 바꾼다)
    // 경로 셈은 계획·금액·기간이 바뀔 때만 다시 한다 (그릴 때마다 세지 않게)
    val Tm = m.horizon; val base = remember(plan, Tm, m.total, m.cost) { m.deposit(plan, monthly = 0, months = Tm) }; val wi = remember(plan, m.recM, Tm, m.total, m.cost) { m.deposit(plan, monthly = m.recM, months = Tm) }   // ios: Views/MissionViews.swift:209
    // ios: Views/MissionViews.swift:210
    Card {
        T("매달 더 넣으면?", style = appFont(14f, FontWeight.Bold))   // ios: Views/MissionViews.swift:211
        // ios: Views/MissionViews.swift:212,213
        T("지금 구성 그대로 · 넣은 만큼 본전도 오름", style = appFont(12f), color = Theme.sub)
        ChipRow(items = chips(m), selection = m.recM, onSelect = { m.recM = it }, fill = true)   // ios: Views/MissionViews.swift:214
        // ios: Views/MissionViews.swift:215
        if (m.recM > 0) {
            T("월 ${AppModel.wonK(m.recM.toDouble())} · 평가액의 ${AppModel.pct(m.recM.toDouble() * 1e4 / max(1.0, m.total))}", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/MissionViews.swift:216
        }
        val st1 = appFont(15f)   // ios: Views/MissionViews.swift:220
        // ios: Views/MissionViews.swift:218
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("${m.horizonLabel} 안 본전", Modifier.weight(1f), style = st1, color = Theme.sub)
            T(if (m.recM == 0) AppModel.pct(base.prob) else "${AppModel.pct(base.prob)} → ${AppModel.pct(wi.prob)}", style = st1.copy(fontWeight = FontWeight.Bold)) }   // ios: Views/MissionViews.swift:219
        val st2 = appFont(15f)   // ios: Views/MissionViews.swift:223
        // ios: Views/MissionViews.swift:221
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("보통 본전까지", Modifier.weight(1f), style = st2, color = Theme.sub)
            T(if (m.recM == 0) depEta(base) else "${depEta(base)} → ${depEta(wi)}", style = st2.copy(fontWeight = FontWeight.Bold)) }   // ios: Views/MissionViews.swift:222
        // ios: Views/MissionViews.swift:224
        if (m.recM > 0) {
            // 넣는 돈이 평가액보다 크면 손실 금액은 그대로인데 비율만 작아져 본전이 빨라 보인다 (회복이 아니라 묽어짐)
            val loss = m.cost - m.total; val add = m.recM.toDouble() * 1e4 * Tm.toDouble()   // ios: Views/MissionViews.swift:226
            // ios: Views/MissionViews.swift:227
            T("손실 ${AppModel.man(loss)}은 그대로 · ${AppModel.man(add)} 더 넣으면 비율만 ${AppModel.sgn(-loss / max(1.0, m.cost))}→${AppModel.sgn(-loss / max(1.0, m.cost + add))} · 본전이 빨라 보일 뿐",
              style = appFont(12f).ls(2f), color = Theme.sub)   // ios: Views/MissionViews.swift:228
        }
        // ios: Views/MissionViews.swift:230,231
        T("전망과 같은 3,000번 경로 · 월말 기준 · 지금 비중대로 나눠 넣음", style = appFont(12f), color = Theme.muted)
    }
}

/** 칩: 지금 평가액의 5·10·20·50% (만원 단위로 반올림, 최소 1만원). 작은 계좌에서도 말이 되는 크기 */
// ios: Views/MissionViews.swift:235
private fun chips(m: AppModel): List<Pair<Int, String>> {
    val out = mutableListOf(0 to "안 넣음")   // ios: Views/MissionViews.swift:236
    // ios: Views/MissionViews.swift:237
    for (r in listOf(5, 10, 20, 50)) {
        val v = max(1, (m.total * r.toDouble() / 100 / 1e4).swRound().toInt())   // ios: Views/MissionViews.swift:238
        if (out.none { it.first == v }) out.add(v to "${r}%")   // ios: Views/MissionViews.swift:239
    }
    return out   // ios: Views/MissionViews.swift:241
}

// ios: Views/MissionViews.swift:243
private fun depEta(d: Deposit): String =
    if (d.eta == 0.0) "지금" else if (d.eta.isFinite()) AppModel.eta(d.eta).replace(" 뒤", "") else "3년 넘게"   // ios: Views/MissionViews.swift:244

// MARK: 미션 2 — 원인 진단 퀴즈
// ios: Views/MissionViews.swift:249,250,251
@Composable
fun Mission2View(m: AppModel) {
    val opts = listOf("conc" to "한 종목에 몰려서", "mkt" to "시장 전체 하락", "fx" to "환율")   // ios: Views/MissionViews.swift:252
    val right = m.quizRight; val ans = m.quizAnswer   // ios: Views/MissionViews.swift:253
    // ios: Views/MissionViews.swift:254
    MissionPage(kicker = "미션 2 / 4", title = "손실 가장 큰 원인은?") {
        // ios: Views/MissionViews.swift:255
        GuideBubble(m, pose = if (ans == null) Pose.think else if (ans == right) Pose.cheer else Pose.point,
                    text = if (ans == null) "감으로 하나 골라 보세요. 정답은 숫자로 확인해요." else if (ans == right) "맞아요. 아래 숫자로 확인해 봐요." else "숫자로 보면 조금 달라요. 아래를 같이 봐요.")   // ios: Views/MissionViews.swift:256
        // ios: Views/MissionViews.swift:257
        opts.forEach { (k, label) ->
            val isRight = k == right; val picked = ans == k   // ios: Views/MissionViews.swift:258
            // ios: Views/MissionViews.swift:259,263,265 (withAnimation: 고르면 바탕·테두리·글자 색이 기본 스프링으로 바뀐다)
            Box(Modifier.fillMaxWidth().plainClickable { m.quizAnswer = k }.background(animateColorAsState(if (ans != null && isRight) Theme.tealBg else Theme.card, spring(), label = "bg").value, RoundedCornerShape(14.dp))
                // ios: Views/MissionViews.swift:264
                .border(2.dp, animateColorAsState(if (ans == null) Theme.dash else if (isRight) Theme.teal else if (picked) Theme.muted else Theme.track, spring(), label = "line").value, RoundedCornerShape(14.dp))
                // ios: Views/MissionViews.swift:261
                .defaultMinSize(minHeight = 52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                // ios: Views/MissionViews.swift:260,262
                T(label, style = appFont(16f, FontWeight.SemiBold), color = animateColorAsState(if (ans == null) Theme.ink else if (isRight) Theme.tealDark else if (picked) Theme.sub else Theme.sub2, spring(), label = "fg").value)
            }
        }
        // ios: Views/MissionViews.swift:267 (withAnimation: 답을 고르면 나타난다)
        AnimatedVisibility(visible = ans != null) {
            val qRet = m.restRet   // ios: Views/MissionViews.swift:268
            // ios: Views/MissionViews.swift:269
            Card {
                T(if (ans == right) "맞아요" else "숫자로는 달라요", style = appFont(17f, FontWeight.Bold))   // ios: Views/MissionViews.swift:270
                // ios: Views/MissionViews.swift:271
                val why = if (m.cost > m.total) "손실 ${AppModel.man(m.cost - m.total)} 중 ${AppModel.pct(m.lossShareFocus)}가 ${m.focusName}에서"
                          else "들어간 돈보다 ${AppModel.sgn(m.ret)}"   // ios: Views/MissionViews.swift:272
                val same = if (m.restRows.isEmpty()) "" else " · 같은 기간 ${m.restName} ${AppModel.sgn(qRet)}"   // ios: Views/MissionViews.swift:273
                T(why + same, style = appFont(15f).ls(3f))   // ios: Views/MissionViews.swift:274
                // ios: Views/MissionViews.swift:275
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { val st = appFont(14f); T("${m.focusName} 비중", Modifier.weight(1f), style = st); T(AppModel.pct(m.focusWeight), style = st.copy(fontWeight = FontWeight.Bold)) }
                ProgressBar(value = m.focusWeight, height = 10.dp, fill = Theme.purple, track = Theme.track)   // ios: Views/MissionViews.swift:276
                PrimaryButton(title = "하나 더 보기") { m.done = m.done + 2; m.boardPath = m.boardPath + MissionRoute.m2r }   // ios: Views/MissionViews.swift:277
            }
        }
    }
}

// MARK: 미션 2 결과 — 흔들림 비용 + 회복 경로
// ios: Views/MissionViews.swift:285,286,287
@Composable
fun Mission2ResultView(m: AppModel) {
    val keep = m.keepPlan   // ios: Views/MissionViews.swift:288
    // ios: Views/MissionViews.swift:289
    MissionPage(kicker = "미션 2 완료 · 회복 경로 열림", title = "흔들리면 보통 결과가 깎여요") {
        GuideBubble(m, pose = Pose.back, text = "같이 봐요. 한 종목에 몰려 흔들림이 크면, 평균이 같아도 보통의 경우는 낮아져요. -50% 뒤에는 +100%가 있어야 본전이에요.", note = "그래프 쪽을 돌아봄")   // ios: Views/MissionViews.swift:290
        // ios: Views/MissionViews.swift:291
        Card {
            val yr = remember(keep) { keep.year }   // ios: Views/MissionViews.swift:292 (경로 정렬 셈은 계획이 바뀔 때만)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { val st = appFont(15f); T("한 해 흔들림", Modifier.weight(1f), style = st); T("±${AppModel.pct(yr.sigma)}", style = st.copy(fontWeight = FontWeight.Bold)) }   // ios: Views/MissionViews.swift:293
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { val st = appFont(15f); T("보통 연 성장", Modifier.weight(1f), style = st); T(AppModel.sgn(exp(yr.g) - 1), style = st.copy(fontWeight = FontWeight.Bold)) }   // ios: Views/MissionViews.swift:294
            T("평균 연 ${AppModel.sgn(yr.mean)} · 보통은 ${AppModel.sgn(exp(yr.g) - 1)}", style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:295
        }
        // ios: Views/MissionViews.swift:297
        Card {
            T("앞으로 1년 내 평가액", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:298
            PathChart(plans = listOf(ChartPlan(keep, hexD(0x2450C8, 0x7FA2F0), false)), V = m.total, C = m.cost, half = m.halfway, modifier = Modifier.fillMaxWidth().height(150.dp))   // ios: Views/MissionViews.swift:299
            // ios: Views/MissionViews.swift:300
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { val st = appFont(11f); val c = Theme.muted; T("지금", style = st, color = c); Spacer(Modifier.weight(1f)); T("3개월", style = st, color = c); Spacer(Modifier.weight(1f)); T("6개월", style = st, color = c); Spacer(Modifier.weight(1f)); Spacer(Modifier.weight(1f)); T("1년", style = st, color = c) }
            // ios: Views/MissionViews.swift:301
            FlowRow(spacing = 10.dp) {
                legend(hexD(0x2450C8, 0x7FA2F0), "보통"); legend(hexD(0x2450C8, 0x7FA2F0).copy(alpha = 0.2f), "10번 중 9번")   // ios: Views/MissionViews.swift:302
                legend(hex(0xC8352E), "본전"); legend(Theme.muted, "손실 절반")   // ios: Views/MissionViews.swift:303
            }
        }
        BoardButtons(m, next = "다음: 내 계획", to = MissionRoute.m3)   // ios: Views/MissionViews.swift:306
    }
}

// ios: Views/MissionViews.swift:311
@Composable
private fun legend(c: Color, t: String) {
    // ios: Views/MissionViews.swift:312,313
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp, 12.dp).background(c, RoundedCornerShape(3.dp))); T(t, style = appFont(12f), color = Theme.sub) }
}

// MARK: 미션 3 — 계획 고르기 (그래프 고정 + 칩)
/** 미션 3 이동 막대 제목 (missionPage 가 NavPage 제목으로 쓴다) */
object Mission3Nav {
    const val title = "미션 3 / 4"   // ios: Views/MissionViews.swift:357
}

// ios: Views/MissionViews.swift:317,318,321
@Composable
fun Mission3View(m: AppModel) {
    var open by remember { mutableStateOf(setOf<Basket>()) }   // ios: Views/MissionViews.swift:319
    // ios: Views/MissionViews.swift:322 (@Bindable: 칩은 m.planKey·m.horizon 을 바로 바꾼다)
    val sel = m.selectedPlan; val Ty = m.horizon.toDouble() / 12   // ios: Views/MissionViews.swift:323
    // ios: Views/MissionViews.swift:356
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        // ios: Views/MissionViews.swift:324
        PinnedLayout(header = {
            // ios: Views/MissionViews.swift:325,345
            Box(Modifier.fillMaxWidth().background(Theme.card)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ios: Views/MissionViews.swift:326
                Row(Modifier.fillMaxWidth()) {
                    T(sel.name, Modifier.alignByBaseline(), style = appFont(17f, FontWeight.Bold))   // ios: Views/MissionViews.swift:327
                    T(sel.tag, Modifier.alignByBaseline().padding(start = 8.dp), style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:328
                    Spacer(Modifier.weight(1f))   // ios: Views/MissionViews.swift:329
                    T("${m.horizonLabel} 기준", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:330
                }
                mixBar(sel)   // ios: Views/MissionViews.swift:332
                PathChart(plans = listOf(ChartPlan(m.keepPlan, Theme.muted, true), ChartPlan(sel, Theme.teal, false)), V = m.total, C = m.cost, marker = m.horizon, modifier = Modifier.fillMaxWidth().height(110.dp))   // ios: Views/MissionViews.swift:333
                T("점선 = 유지 · 빨간 선 = 본전", style = appFont(11f), color = Theme.muted)   // ios: Views/MissionViews.swift:334
                // ios: Views/MissionViews.swift:335
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    pill("절반 회복", AppModel.pct(sel.prob(m.halfway, Ty)), Modifier.weight(1f))   // ios: Views/MissionViews.swift:336
                    pill("본전", AppModel.pct(sel.prob(m.cost, Ty)), Modifier.weight(1f))   // ios: Views/MissionViews.swift:337
                    pill("나쁜 경우", AppModel.sgn0(sel.outcome(m.cost, Ty, -1.0)), Modifier.weight(1f))   // ios: Views/MissionViews.swift:338
                }
                // ios: Views/MissionViews.swift:340
                ChipRow(items = m.plans.map { it.id to it.name }, selection = m.planKey, fill = true, onSelect = { k ->
                    // 모의 바구니는 고른 계획대로 바꾼 내 종목 비중을 따른다
                    // ios: Views/MissionViews.swift:342 (onChange: 값이 바뀔 때만)
                    if (k != m.planKey) { m.planKey = k; if (m.pworld != null) m.saveBasket(m.planBasket(m.selectedPlan)) }
                })
                horizonChips(m.horizon) { m.horizon = it }   // ios: Views/MissionViews.swift:343
            }; HDivider(modifier = Modifier.align(Alignment.BottomCenter)) }
        }) {   // ios: Views/MissionViews.swift:346
            // ios: Views/MissionViews.swift:347,354
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GuideBubble(m, pose = Pose.side, text = "갈 길을 하나 골라 보세요. 칩을 누르면 위 그래프가 바로 바뀌어요. 흔들림을 줄이면 빠른 본전 확률도, 더 잃을 확률도 함께 줄어요.")   // ios: Views/MissionViews.swift:348
                T(sel.desc, style = appFont(15f).ls(3f))   // ios: Views/MissionViews.swift:349
                holdingsCard(m)   // ios: Views/MissionViews.swift:350
                basketsCard(Basket.entries.filter { it != Basket.C }, open) { open = it }   // ios: Views/MissionViews.swift:351
                PrimaryButton(title = "\"${sel.name}\"으로 정하기") { m.done = m.done + 3; m.boardPath = m.boardPath + MissionRoute.m3r }   // ios: Views/MissionViews.swift:352
            }
        }
    }
}

// ios: Views/MissionViews.swift:360
@Composable
private fun pill(k: String, v: String, modifier: Modifier) {
    // ios: Views/MissionViews.swift:361,362,363
    Column(modifier.background(Theme.bg, RoundedCornerShape(10.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { T(k, style = appFont(11f), color = Theme.sub); T(v, style = appFont(16f, FontWeight.Bold)) }
}

// ios: Views/MissionViews.swift:366
@Composable
private fun holdingsCard(m: AppModel) {
    // 비중 1위 종목의 업종 (업종 자료가 없는 종목은 이름만)
    val d = m.focusIndustry; val sector = m.focusRow?.sym?.sector ?: ""   // ios: Views/MissionViews.swift:368
    val b = d?.let { Basket.of(pe = it.pe, pe10 = it.pe10, growth = it.growth) } ?: "?"   // ios: Views/MissionViews.swift:369
    val ratio = d?.let { cformat("%.1f", it.pe / it.pe10) } ?: ""   // ios: Views/MissionViews.swift:370
    val restSub = if (m.restRows.all { it.sym.sector == "지수" }) "지수 · 분산" else m.restRows.joinToString(" · ") { it.sym.short }   // ios: Views/MissionViews.swift:371
    // ios: Views/MissionViews.swift:372
    Card {
        T("내 종목 업종", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:373
        // ios: Views/MissionViews.swift:374
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // ios: Views/MissionViews.swift:375
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("${m.focusName} ${AppModel.pct(m.focusWeight)}", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:376
                // ios: Views/MissionViews.swift:377
                T(d?.let { "${EnData.word(it.ko)} · PER 평균의 ${ratio}배 · 성장 ${it.growth.swRound().toInt()}%" } ?: (if (sector.isEmpty() || sector == "지수") "업종 자료 없음" else sector),
                  style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:378
            }
            // ios: Views/MissionViews.swift:380 (Spacer: 남는 폭은 위 글 칸 weight 가 채운다)
            tag(if (b == "H") "과열" else if (b == "?") (if (sector == "지수") "지수" else "업종 모름") else Basket.entries.firstOrNull { it.name == b }?.title ?: "세 묶음 밖", hot = b == "H", neutral = b == "?")   // ios: Views/MissionViews.swift:381
        }
        // ios: Views/MissionViews.swift:383
        if (d != null && b == "H") {
            // ios: Views/MissionViews.swift:384
            T("PER 10년 평균의 ${ratio}배 · 이익 성장 ${d.growth.swRound().toInt()}% · 과열이라 세 묶음 밖",
              style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/MissionViews.swift:385
        }
        // ios: Views/MissionViews.swift:387
        if (m.restRows.isNotEmpty()) {
            HDivider(Theme.line)   // ios: Views/MissionViews.swift:388
            // ios: Views/MissionViews.swift:389
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // ios: Views/MissionViews.swift:390
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T("${m.restName} ${AppModel.pct(1 - m.focusWeight)}", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:391
                    T(restSub, style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:392
                }
                // ios: Views/MissionViews.swift:394 (Spacer: 남는 폭은 위 글 칸 weight 가 채운다)
                tag("${m.restRows.size}종목", hot = false, neutral = true)   // ios: Views/MissionViews.swift:395
            }
        }
    }
}

// ios: Views/MissionViews.swift:401
@Composable
private fun tag(t: String, hot: Boolean, neutral: Boolean = false) {
    T(t, Modifier
        // ios: Views/MissionViews.swift:404
        .background(if (neutral) Theme.track else if (hot) hexD(0xFBE9E7, 0x3D1E1B) else Theme.tealBg, CapsuleShape)
        // ios: Views/MissionViews.swift:402
        .padding(horizontal = 8.dp, vertical = 3.dp), style = appFont(12f, FontWeight.Bold),
        // ios: Views/MissionViews.swift:403
        color = if (neutral) Theme.sub else if (hot) hexD(0xA3291F, 0xF08A80) else Theme.tealDark)
}

// ios: Views/MissionViews.swift:407
@Composable
private fun basketsCard(list: List<Basket>, open: Set<Basket>, onOpen: (Set<Basket>) -> Unit) {
    BasketList(baskets = list, open = open, onOpen = onOpen)   // ios: Views/MissionViews.swift:408
}

// ios: Views/MissionViews.swift:412,416
@Composable
fun BasketList(
    baskets: List<Basket>,   // ios: Views/MissionViews.swift:413
    open: Set<Basket>, onOpen: (Set<Basket>) -> Unit,   // ios: Views/MissionViews.swift:414 (@Binding)
    title: String = "업종 묶음",   // ios: Views/MissionViews.swift:415
) {
    // ios: Views/MissionViews.swift:417
    Card(padding = 14.dp) {
        // ios: Views/MissionViews.swift:418
        Row(Modifier.fillMaxWidth()) {
            T(title, Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/MissionViews.swift:419
            Spacer(Modifier.weight(1f))   // ios: Views/MissionViews.swift:420
            T("PER 지금 / 10년 평균 · 이익 성장", Modifier.alignByBaseline(), style = appFont(11f), color = Theme.muted)   // ios: Views/MissionViews.swift:421
        }
        // ios: Views/MissionViews.swift:423
        baskets.forEach { b ->
            val list = b.industries; val isOpen = open.contains(b)   // ios: Views/MissionViews.swift:424
            // ios: Views/MissionViews.swift:425,433,435 (withAnimation: 펼친 칸은 animateContentSize 기본 스프링으로 자란다)
            Column(Modifier.fillMaxWidth().animateContentSize(spring()), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).plainClickable { onOpen(if (isOpen) open - b else open + b) }
                // ios: Views/MissionViews.swift:436
                .semantics { selected = isOpen },
                // ios: Views/MissionViews.swift:426
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp, 12.dp).background(b.color, RoundedCornerShape(3.dp)))   // ios: Views/MissionViews.swift:427
                T("${b.title} ${list.size}", style = appFont(14f, FontWeight.Bold))   // ios: Views/MissionViews.swift:428
                T(list.take(3).joinToString(" · ") { EnData.word(it.ko) }, Modifier.weight(1f), style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/MissionViews.swift:429
                Spacer(Modifier.width(4.dp))   // ios: Views/MissionViews.swift:430 (minLength 4: 남는 폭은 앞 글 칸이 채운다)
                T(if (isOpen) "⌃" else "⌄", style = appFont(12f), color = Theme.teal)   // ios: Views/MissionViews.swift:431
            }
            // ios: Views/MissionViews.swift:437
            if (isOpen) {
                T(b.rule, style = appFont(12f), color = Theme.sub)   // ios: Views/MissionViews.swift:438
                // ios: Views/MissionViews.swift:439
                list.forEach { r ->
                    // ios: Views/MissionViews.swift:440
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(EnData.word(r.ko), Modifier.weight(1f), style = appFont(13f))   // ios: Views/MissionViews.swift:441
                        // ios: Views/MissionViews.swift:442 (Spacer: 남는 폭은 앞 글 칸 weight 가 채운다)
                        T(cformat("%.1f / %.1f", r.pe, r.pe10), style = appFont(12f).copy(fontFeatureSettings = "tnum"), color = Theme.sub)   // ios: Views/MissionViews.swift:443
                        T("+${r.growth.swRound().toInt()}%", Modifier.widthIn(min = 44.dp), style = appFont(12f, FontWeight.SemiBold).copy(textAlign = TextAlign.End))   // ios: Views/MissionViews.swift:444
                    }
                }
            }
            }
        }
        T("다모다란 업종 자료 26.1 · 10곳 미만 제외 · 상품 추천 아님", style = appFont(11f), color = Theme.muted)   // ios: Views/MissionViews.swift:449
    }
}

// 계획 비중 막대: 몰린 종목 + 묶음
// ios: Views/MissionViews.swift:455
@Composable
private fun mixBar(p: Plan) {
    val rest = 1 - p.wt   // ios: Views/MissionViews.swift:456
    val segs: List<Pair<Color, Double>> = listOf(Theme.purple to p.wt) + Basket.entries.map { it.color to rest * (p.mix[it] ?: 0.0) }   // ios: Views/MissionViews.swift:457
    val card = Theme.card; val track = Theme.track
    val label = "${p.focus} ${AppModel.pct(p.wt)}" + Basket.entries.mapNotNull { b -> p.mix[b]?.let { ", ${b.title} ${AppModel.pct(rest * it)}" } }.joinToString("")   // ios: Views/MissionViews.swift:474
    // ios: Views/MissionViews.swift:458,473
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = tr(label) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ios: Views/MissionViews.swift:459,467
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CapsuleShape).background(track, CapsuleShape).drawWithCache {
            var x = 0f   // ios: Views/MissionViews.swift:460 (HStack(spacing: 0): 칸을 틈 없이 이어 붙인다)
            // ios: Views/MissionViews.swift:461
            val rects = segs.indices.map { i ->
                val w = size.width * segs[i].second.toFloat(); val r = x to w; x += w; r   // ios: Views/MissionViews.swift:462
            }
            onDrawBehind {
                rects.forEachIndexed { i, (x0, w) ->
                    drawRect(segs[i].first, Offset(x0, 0f), Size(w, size.height))
                    // ios: Views/MissionViews.swift:463 (칸 오른쪽 끝 2pt 틈)
                    val sep = if (segs[i].second > 0) 2.dp.toPx() else 0f; drawRect(card, Offset(x0 + w - sep, 0f), Size(sep, size.height))
                }
            }
        })
        // ios: Views/MissionViews.swift:468
        FlowRow(spacing = 8.dp) {
            legend(Theme.purple, p.focus)   // ios: Views/MissionViews.swift:469
            Basket.entries.forEach { legend(it.color, it.title) }   // ios: Views/MissionViews.swift:470
        }
    }
}

// MARK: 미션 3 결과 — 비중과 확률 (금액·종목별 매매 안내 없이 비율로만)
// ios: Views/MissionViews.swift:478,479,481
@Composable
fun Mission3ResultView(m: AppModel) {
    var open by remember { mutableStateOf(setOf<Basket>()) }   // ios: Views/MissionViews.swift:480
    val sel = m.selectedPlan; val keep = m.keepPlan; val Ty = m.horizon.toDouble() / 12; val freed = m.freedAmount   // ios: Views/MissionViews.swift:482
    val baskets = Basket.entries.filter { it != Basket.C && (sel.mix[it] ?: 0.0) > 0 }   // ios: Views/MissionViews.swift:483
    // ios: Views/MissionViews.swift:484
    MissionPage(kicker = "미션 3 완료 · 비중 비교 열림", title = "내 계획: ${sel.name}") {
        GuideBubble(m, pose = Pose.cheer, text = "정했어요. 이 계획은 기록만 해요. 실제 매매 판단은 직접 하셔야 해요.")   // ios: Views/MissionViews.swift:485
        // ios: Views/MissionViews.swift:486
        Card {
            // ios: Views/MissionViews.swift:487
            T("${m.horizonLabel} 안 절반 회복 ${AppModel.pct(sel.prob(m.halfway, Ty))} · 본전 ${AppModel.pct(sel.prob(m.cost, Ty))} · 나쁜 경우 ${AppModel.sgn0(sel.outcome(m.cost, Ty, -1.0))}",
              style = appFont(15f, FontWeight.SemiBold).ls(3f))   // ios: Views/MissionViews.swift:488
        }
        // ios: Views/MissionViews.swift:490
        Card {
            T("비중이 바뀌면", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:491
            // ios: Views/MissionViews.swift:492
            moveRow("가장 큰 종목", if (freed > 0) "${(m.focusWeight * 100).swRound().toInt()}→${AppModel.pct(sel.wt)}" else AppModel.pct(m.focusWeight),
                    if (freed > 0) Mission3ResultView.pp(sel.wt - m.focusWeight) else "그대로", up = false)   // ios: Views/MissionViews.swift:493
            if (m.restRows.isNotEmpty()) moveRow("나머지", AppModel.pct(1 - m.focusWeight), "그대로", up = true)   // ios: Views/MissionViews.swift:494
            // ios: Views/MissionViews.swift:495
            Basket.entries.filter { (sel.mix[it] ?: 0.0) > 0 && sel.id != "keep" }.forEach { b ->
                val r = freed * (sel.mix[b] ?: 0.0) / max(1.0, m.total)   // ios: Views/MissionViews.swift:496
                moveRow(b.title, "0→${AppModel.pct(r)}", Mission3ResultView.pp(r), up = true)   // ios: Views/MissionViews.swift:497
            }
            // ios: Views/MissionViews.swift:499
            if (sel.id != "keep") {
                // ios: Views/MissionViews.swift:500
                T("${m.horizonLabel} 안 본전 ${AppModel.pct(keep.prob(m.cost, Ty))}→${AppModel.pct(sel.prob(m.cost, Ty))} · 나쁜 경우 ${AppModel.sgn0(keep.outcome(m.cost, Ty, -1.0))}→${AppModel.sgn0(sel.outcome(m.cost, Ty, -1.0))}",
                  style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/MissionViews.swift:501
            }
            // ios: Views/MissionViews.swift:503
            T(if (sel.id == "keep") "지금 구성 그대로" else "적립·매도 중 맞추는 방법 따라 세금 다름 · 절세 화면",
              style = appFont(12f), color = Theme.muted)   // ios: Views/MissionViews.swift:504
        }
        if (baskets.isNotEmpty() && sel.id != "keep") BasketList(baskets = baskets, open = open, onOpen = { open = it }, title = "담을 업종")   // ios: Views/MissionViews.swift:506
        // 원인 진단·계획을 마친 뒤에 '매달 더 넣으면?' (2026-10-11 결정: 미션 3 뒤)
        if (m.cost > m.total) DepositLeverCard(m, plan = keep)   // ios: Views/MissionViews.swift:508
        T("계산 예시 · 매매 추천 아님", style = appFont(14f), color = Theme.sub)   // ios: Views/MissionViews.swift:509
        PrimaryButton(title = "다음: 앱 시작 3단계") { m.boardPath = m.boardPath + MissionRoute.nx }   // ios: Views/MissionViews.swift:510
        // ios: Views/MissionViews.swift:511,516
        Box(Modifier.fillMaxWidth().plainClickable { m.boardPath = m.boardPath + MissionRoute.m4 }
            // ios: Views/MissionViews.swift:515
            .border(2.dp, Theme.teal, RoundedCornerShape(12.dp))
            // ios: Views/MissionViews.swift:513
            .defaultMinSize(minHeight = 50.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            // ios: Views/MissionViews.swift:512,514
            T("해외주식 이익 있으면: 손실로 절세", style = appFont(15f, FontWeight.SemiBold).copy(textAlign = TextAlign.Center), color = Theme.teal)
        }
        BoardButtons(m, next = null, to = null)   // ios: Views/MissionViews.swift:517
    }
}

// ios: Views/MissionViews.swift:520
@Composable
private fun moveRow(label: String, sub: String, amt: String, up: Boolean) {
    // ios: Views/MissionViews.swift:521,527
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, style = appFont(15f, FontWeight.SemiBold))   // ios: Views/MissionViews.swift:522
        T(sub, Modifier.padding(start = 8.dp), style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:523
        Spacer(Modifier.weight(1f))   // ios: Views/MissionViews.swift:524
        T(amt, style = appFont(15f, FontWeight.Bold).copy(fontFeatureSettings = "tnum"), color = if (up) Theme.teal else hexD(0x2450C8, 0x7FA2F0))   // ios: Views/MissionViews.swift:525
    }
}

/** Mission3ResultView.pp (static func): 목표 루트 계산(GoalRoute.kt)도 쓴다 */
object Mission3ResultView {
    /** 비중 차이 %p (+10%p, -21%p) */
    fun pp(d: Double): String {   // ios: Views/MissionViews.swift:530
        val v = (d * 100).swRound().toInt()   // ios: Views/MissionViews.swift:531
        return if (v == 0) "그대로" else (if (v > 0) "+" else "") + "${v}%p"   // ios: Views/MissionViews.swift:532
    }
}

// MARK: 절세 (대한민국 거주자)
// ios: Views/MissionViews.swift:537,538,540
@Composable
fun TaxInputView(m: AppModel) {
    var gain by remember { mutableStateOf("") }   // ios: Views/MissionViews.swift:539
    // ios: Views/MissionViews.swift:541
    MissionPage(kicker = "절세 · 한국 거주자", title = "올해 판 해외주식 이익은?") {
        GuideBubble(m, pose = Pose.stand, text = "한국에서는 해외주식 한 해 이익과 손실을 합쳐 250만 원을 넘는 부분에만 22%가 붙어요. 올해 판 게 없으면 0을 넣으세요.")   // ios: Views/MissionViews.swift:542
        T("한국 거주자 · 미국 주식만", style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/MissionViews.swift:543
        com.naeilo.app.ui.goal.inputField("올해 실현 이익 (만원)", gain) { gain = it }   // ios: Views/MissionViews.swift:544
        // ios: Views/MissionViews.swift:545
        PrimaryButton(title = "세금 보기") {
            m.taxGain = max(0.0, gain.toDoubleOrNull() ?: 0.0); m.done = m.done + 4; m.boardPath = m.boardPath + MissionRoute.m4r   // ios: Views/MissionViews.swift:546
        }
    }
    LaunchedEffect(Unit) { gain = m.taxGain.toInt().toString() }   // ios: Views/MissionViews.swift:549
}

// ios: Views/MissionViews.swift:553,554,555
@Composable
fun TaxResultView(m: AppModel) {
    // ios: Views/MissionViews.swift:556 (@Bindable: 슬라이더는 m.taxSellQty 를 바로 바꾼다)
    val won0 = { x: Double -> cformat("%,d", x.swRound().toInt()) + "만" }   // ios: Views/MissionViews.swift:557
    val maxQ = m.focusRow?.h?.qty ?: 0.0   // ios: Views/MissionViews.swift:558
    val wAfter = m.focusRow?.let { max(0.0, it.value - m.krw(it.sym, m.taxSellQty * it.sym.last)) / m.total } ?: 0.0   // ios: Views/MissionViews.swift:559
    val over = m.taxSellQty > 0 && m.taxBefore > 0 && m.taxGain + m.taxLossMan < 249   // ios: Views/MissionViews.swift:560
    // ios: Views/MissionViews.swift:561
    MissionPage(kicker = "절세 열림", title = "올해 해외주식 세금") {
        GuideBubble(m, pose = Pose.point, text = "아래 슬라이더를 움직이면 위 숫자가 바로 바뀌어요. 계산 예시이고, 세무 상담은 아니에요.")   // ios: Views/MissionViews.swift:562
        // ios: Views/MissionViews.swift:563,573,574
        Fg(Color.White) { Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("내년 5월 세금", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/MissionViews.swift:564
            // ios: Views/MissionViews.swift:565
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(won0(m.taxBefore), Modifier.alignByBaseline(), style = appFont(18f).copy(textDecoration = TextDecoration.LineThrough), color = hex(0x8A949E))   // ios: Views/MissionViews.swift:566
                T(won0(m.taxAfter), Modifier.alignByBaseline(), style = appFont(30f, FontWeight.Bold), roll = won0(m.taxAfter))   // ios: Views/MissionViews.swift:567
                T("−${won0(max(0.0, m.taxBefore - m.taxAfter))}", Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold), color = Theme.mint)   // ios: Views/MissionViews.swift:568
            }

            // Views/MissionViews.swift:576 (검사 예외: 보간 속 따옴표 · 같은 글)
            val lossTxt = if (m.taxLossMan < 0) "−" + won0(-m.taxLossMan) else won0(m.taxLossMan)
            T("1위 종목 ${AppModel.pct(m.focusWeight)}→${AppModel.pct(wAfter)} (계획 ${AppModel.pct(m.planWeight)}) · 확정 손익 $lossTxt",
              // ios: Views/MissionViews.swift:571
              style = appFont(13f), color = hex(0xC9D0D6))
        } }
        // ios: Views/MissionViews.swift:575
        if (m.focusRow?.sym?.currency != Currency.usd) {
            T("가장 큰 종목이 국내 주식 · 양도세 계산 제외", Modifier.fillMaxWidth()   // ios: Views/MissionViews.swift:576
                .background(Theme.card, RoundedCornerShape(14.dp))   // ios: Views/MissionViews.swift:578
                .border(1.dp, Theme.border, RoundedCornerShape(14.dp))   // ios: Views/MissionViews.swift:579
                .padding(14.dp),   // ios: Views/MissionViews.swift:577
                style = appFont(15f))
        } else { Card {   // ios: Views/MissionViews.swift:580
                T("1위 종목 줄이면 (예시)", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:581
                T("보유의 ${AppModel.pct(m.taxSellQty / max(1.0, maxQ))} 줄임", style = appFont(13f), color = Theme.sub)   // ios: Views/MissionViews.swift:582
                // ios: Views/MissionViews.swift:583
                IosSlider(value = m.taxSellQty, onChange = { m.taxSellQty = it }, range = 0.0..max(1.0, maxQ), step = 1.0, tint = Theme.teal,
                    modifier = Modifier.semantics { contentDescription = tr("줄이는 비율") })   // ios: Views/MissionViews.swift:584
                // ios: Views/MissionViews.swift:585
                T("이익 ${won0(m.taxGain)} − 공제 250만 = 과세 ${won0(max(0.0, m.taxGain - 250))}",
                  style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/MissionViews.swift:586
                // ios: Views/MissionViews.swift:587
                if (over) {
                    // ios: Views/MissionViews.swift:588
                    T("이미 세금 0 · 더 줄여도 그대로", Modifier.fillMaxWidth()
                        // ios: Views/MissionViews.swift:591
                        .background(hexD(0xFDF0E1, 0x3A2A17), RoundedCornerShape(10.dp))
                        // ios: Views/MissionViews.swift:590
                        .padding(10.dp),
                        // ios: Views/MissionViews.swift:589
                        style = appFont(13f, FontWeight.SemiBold), color = hexD(0x8A4B00, 0xF0B070))
                }
        } }
        // ios: Views/MissionViews.swift:594
        T("계산 예시 · 세무 상담·매매 권유 아님 · 결제일 연도 기준",
          style = appFont(12f).ls(2f), color = Theme.muted)   // ios: Views/MissionViews.swift:595
        BoardButtons(m, next = "다음: 앱 시작 3단계", to = MissionRoute.nx)   // ios: Views/MissionViews.swift:596
    }
}

// MARK: 앱 시작 3단계 — 한 단계마다 위젯 하나
/** 내 종목 전체 평가액 3개월 흐름 (원화) */
// ios: Views/MissionViews.swift:611
private fun totalSeries(m: AppModel): List<Double> {
    val s = m.rows.map { r -> m.prices.series(r.sym, Period.m3).map { m.krw(r.sym, it * r.h.qty) } }   // ios: Views/MissionViews.swift:612
    val n = s.maxOfOrNull { it.size } ?: 0   // ios: Views/MissionViews.swift:613 짧은 종목은 첫 값으로 평평하게
    return (0 until n).map { i -> s.fold(0.0) { a, x -> if (x.isEmpty()) a else a + x[maxOf(0, x.size - n + i)] } }   // ios: Views/MissionViews.swift:614 끝(오늘)을 맞춰 더한다
}

// ios: Views/MissionViews.swift:602,603,616
@Composable
fun AppStartView(m: AppModel) {
    var denied by remember { mutableStateOf(false) }   // ios: Views/MissionViews.swift:604
    var meOpen by remember { mutableStateOf(false) }   // NavigationLink { MeEditView() } 자리: 이 화면 위에 한 겹 더 민다
    // ios: Views/MissionViews.swift:605
    val steps = listOf(
        Triple("종목 하나 이상 추가", "넣었으면 확인만", "자산 추이 위젯"),   // ios: Views/MissionViews.swift:606
        Triple("알림 하나 켜기", "본전 도달·비중 이탈 중 하나", "본전 진행 위젯"),   // ios: Views/MissionViews.swift:607
        Triple("오픈 API 연결", "안 하면 가상 주식 샘플만", "블록·오늘의 움직임 위젯"),   // ios: Views/MissionViews.swift:608
    )
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    // 3개월 흐름은 종목이 바뀔 때만 뒤에서 다시 센다 (시세 읽기를 그리기 밖에서)
    var series by remember { mutableStateOf(emptyList<Double>()) }
    LaunchedEffect(m.rows) { series = withContext(Dispatchers.Default) { totalSeries(m) } }
    val n = max(1, m.nxStep)   // ios: Views/MissionViews.swift:617 미션 4에 왔으면 1단계(종목 추가)는 이미 한 것
    // withAnimation { m.nxStep += 1 } 자리: 단계 카드의 색(animateColorAsState)·높이(animateContentSize)가 기본 스프링으로 번진다
    fun animateStep(by: Int) { m.nxStep += by }
    val nb = LocalNavBack.current

    NavStack(path = if (meOpen) listOf(Unit) else emptyList(), onPath = { meOpen = it.isNotEmpty() }, root = {
        Column(Modifier.fillMaxSize().background(Theme.bg)) {
            NavBar(null, back = nb?.back) { nb?.pop?.invoke() }
            Box(Modifier.weight(1f)) {
            // ios: Views/MissionViews.swift:618
            MissionPage(kicker = "미션 4 / 4", title = "세 가지 하면 위젯 하나씩") {
                GuideBubble(m, pose = Pose.wave, text = "마지막이에요. 위젯은 미리 드리지 않아요. 한 단계마다 하나씩, 세 단계를 다 하면 나머지 위젯도 모두 열려요.")   // ios: Views/MissionViews.swift:619
                // ios: Views/MissionViews.swift:620
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ios: Views/MissionViews.swift:621
                    tile(1, n, Modifier.weight(1f)) {
                        T("자산 추이", style = appFont(11f, FontWeight.Bold))   // ios: Views/MissionViews.swift:622
                        Sparkline(points = series, modifier = Modifier.fillMaxWidth().height(34.dp))   // ios: Views/MissionViews.swift:623
                        T(AppModel.man(m.total), style = appFont(12f, FontWeight.Bold))   // ios: Views/MissionViews.swift:624
                    }
                    // ios: Views/MissionViews.swift:626
                    tile(2, n, Modifier.weight(1f)) {
                        T("본전까지", style = appFont(11f, FontWeight.Bold))   // ios: Views/MissionViews.swift:627
                        T(AppModel.pct(min(1.0, m.total / max(1.0, m.cost))), style = appFont(22f, FontWeight.Bold), roll = AppModel.pct(min(1.0, m.total / max(1.0, m.cost))))   // ios: Views/MissionViews.swift:628
                        ProgressBar(value = m.total / max(1.0, m.cost), height = 8.dp, fill = Theme.teal, track = Theme.track)   // ios: Views/MissionViews.swift:629
                    }
                    // ios: Views/MissionViews.swift:631
                    tile(3, n, Modifier.weight(1f)) {
                        T("블록", style = appFont(11f, FontWeight.Bold))   // ios: Views/MissionViews.swift:632
                        val cells = m.blocks.filter { !it.inter }   // ios: Views/MissionViews.swift:634
                        // ios: Views/MissionViews.swift:633 (LazyVGrid 3칸)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            cells.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    // ios: Views/MissionViews.swift:635
                                    row.forEach { b -> Box(Modifier.weight(1f).height(14.dp).background(if (m.done.contains(b.id)) Theme.teal else Theme.track, RoundedCornerShape(4.dp))) }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        T("미션 진행", style = appFont(11f), color = Theme.sub)   // ios: Views/MissionViews.swift:638
                    }
                    // ios: Views/MissionViews.swift:640
                    tile(3, n, Modifier.weight(1f)) {
                        T("오늘의 움직임", style = appFont(11f, FontWeight.Bold))   // ios: Views/MissionViews.swift:641
                        T(AppModel.sgn(m.todayMove), style = appFont(18f, FontWeight.Bold), color = Theme.change(m.todayMove), roll = AppModel.sgn(m.todayMove))   // ios: Views/MissionViews.swift:642
                    }
                }
                // ios: Views/MissionViews.swift:645
                steps.indices.forEach { i ->
                    val done = i < n; val cur = i == n   // ios: Views/MissionViews.swift:646
                    // ios: Views/MissionViews.swift:695
                    Box(Modifier.fillMaxWidth().padding(top = if (done || cur) 24.dp else 0.dp)) {   // 높이 움직임(animateContentSize)은 안쪽 카드에: 바깥에 두면 위로 올라선 인물이 잘린다
                        // ios: Views/MissionViews.swift:688
                        Fg(if (done || cur) Theme.ink else Theme.muted) {
                            // ios: Views/MissionViews.swift:647,687
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().animateContentSize()
                                // ios: Views/MissionViews.swift:689
                                .background(animateColorAsState(if (done) Theme.tealBg else if (cur) Theme.card else hexD(0xF4F6F8, 0x1D252E), label = "stepBg").value, RoundedCornerShape(14.dp))
                                // ios: Views/MissionViews.swift:690
                                .border(if (cur) 2.dp else 1.dp, animateColorAsState(if (done) hexD(0xB9DCD7, 0x2E5A55) else if (cur) Theme.teal else Theme.track, label = "stepLine").value, RoundedCornerShape(14.dp))
                                // ios: Views/MissionViews.swift:686
                                .padding(horizontal = 14.dp, vertical = 12.dp)) {
                                // ios: Views/MissionViews.swift:648
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // ios: Views/MissionViews.swift:649,650
                                    T("${i + 1}", Modifier.size(24.dp, 24.dp).background(if (done || cur) Theme.teal else Theme.muted, CircleShape).wrapContentSize(), style = appFont(13f, FontWeight.Bold), color = Color.White)
                                    T(steps[i].first, style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:651
                                }
                                // ios: Views/MissionViews.swift:653
                                TA(buildAnnotatedString { append("${steps[i].second} · 받는 것: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(steps[i].third) } }, style = appFont(13f), color = Theme.sub)
                                // ios: Views/MissionViews.swift:654
                                if (cur && i == 1) {
                                    // 2단계는 실제로: 폰 알림 허용을 받고 본전 도달 알림을 켠다
                                    // ios: Views/MissionViews.swift:657
                                    val askNotify: () -> Unit = { scope.launch {
                                        // ios: Views/MissionViews.swift:658 (openNotificationSettingsURLString 자리: 이 앱의 알림 설정 화면)
                                        if (denied) { runCatching { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; return@launch }
                                        // ios: Views/MissionViews.swift:659
                                        if (Notifier.request()) {
                                            m.alerts = m.alerts + ((if (m.isGoal) "morn" else "be") to true)   // ios: Views/MissionViews.swift:660
                                            animateStep(1)   // ios: Views/MissionViews.swift:661
                                        } else { denied = true }   // ios: Views/MissionViews.swift:662
                                    } }
                                    // ios: Views/MissionViews.swift:656,665,666
                                    Box(Modifier.plainClickable(onClick = askNotify).background(Theme.teal, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp).defaultMinSize(minHeight = 44.dp), contentAlignment = Alignment.Center) { T(if (denied) "설정에서 알림 허용" else "알림 켜기", style = appFont(14f, FontWeight.Bold), color = Color.White) }
                                    if (denied) T("알림 꺼짐 · 설정 > 알림 > naeilo에서 허용 후 다시", style = appFont(12f), color = Theme.sub)   // ios: Views/MissionViews.swift:667
                                } else if (cur && i == 2) {   // ios: Views/MissionViews.swift:668
                                    // 3단계: 키를 넣었으면 확인만, 아니면 설정의 오픈 API 화면으로. 키 없이도 샘플로 계속할 수 있다
                                    val tossTap: () -> Unit = { if (TossKeys.on) animateStep(1) else { m.openSettings(SettingsRoute.toss) } }   // ios: Views/MissionViews.swift:670,671
                                    // ios: Views/MissionViews.swift:670,673,674
                                    Box(Modifier.plainClickable(onClick = tossTap).background(Theme.teal, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp).defaultMinSize(minHeight = 44.dp), contentAlignment = Alignment.Center) { T(if (TossKeys.on) "연결했어요" else "오픈 API 연결", style = appFont(14f, FontWeight.Bold), color = Color.White) }
                                    // ios: Views/MissionViews.swift:675
                                    if (!TossKeys.on) {
                                        // ios: Views/MissionViews.swift:676,677
                                        Box(Modifier.plainClickable { animateStep(1) }.defaultMinSize(minHeight = 44.dp), contentAlignment = Alignment.CenterStart) { T("샘플로 계속", style = appFont(13f, FontWeight.SemiBold), color = Theme.teal) }
                                    }
                                } else if (cur) {   // ios: Views/MissionViews.swift:679
                                    // ios: Views/MissionViews.swift:680,681,682
                                    Box(Modifier.plainClickable { animateStep(1) }.background(Theme.teal, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp).defaultMinSize(minHeight = 44.dp), contentAlignment = Alignment.Center) { T("했어요", style = appFont(14f, FontWeight.Bold), color = Color.White) }
                                }
                                if (done) T("완료 · 위젯 열림", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/MissionViews.swift:684
                            }
                        }
                        // 끝낸 단계엔 만세, 지금 단계엔 옆으로 걸어오는 인물 (카드 오른쪽 위에 올라선다)
                        // ios: Views/MissionViews.swift:692 (.overlay(alignment: .topTrailing))
                        // ios: Views/MissionViews.swift:693
                        if (done || cur) MotionSprite(friend = m.guideFriend, pose = if (done) Pose.cheer else Pose.side, height = 44.dp, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-38).dp).charIdle(4), model = m)
                    }
                }
                // 내 캐릭터는 여기서부터 만들어 보고 저장할 수 있다 (홈·위젯에 나온다)
                // ios: Views/MissionViews.swift:709
                Fg(Theme.ink) {
                    // ios: Views/MissionViews.swift:698,714 (NavigationLink { MeEditView() }: 이 화면 위에 내 캐릭터 화면을 민다)
                    Row(Modifier.fillMaxWidth().plainClickable { meOpen = true }
                        // ios: Views/MissionViews.swift:711
                        .background(Theme.card, RoundedCornerShape(14.dp))
                        // ios: Views/MissionViews.swift:712
                        .border(1.dp, Theme.border, RoundedCornerShape(14.dp))
                        // ios: Views/MissionViews.swift:710
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                        // ios: Views/MissionViews.swift:699
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CharPixel(id = "me", width = 30.dp, height = 42.dp, modifier = Modifier.charIdle(5))   // ios: Views/MissionViews.swift:700
                        // ios: Views/MissionViews.swift:701
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            T(if (m.me == null) "내 캐릭터 만들어 보기" else "내 캐릭터 고치기", style = appFont(15f, FontWeight.Bold))   // ios: Views/MissionViews.swift:702
                            // ios: Views/MissionViews.swift:703,704
                            T("쉼터 '본인' 칸 · 홈·위젯에 나옴", style = appFont(12f), color = Theme.sub)
                        }
                        Spacer(Modifier.widthIn(min = 0.dp))   // ios: Views/MissionViews.swift:706 (남는 폭은 앞 글 칸 weight 가 채운다)
                        T("›", style = appFont(17f, FontWeight.Bold), color = Theme.muted)   // ios: Views/MissionViews.swift:707
                    }
                }
                // ios: Views/MissionViews.swift:715
                if (n >= 3) {
                    // ios: Views/MissionViews.swift:716,726,727
                    Fg(Color.White) { Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("특별 선물", style = appFont(13f, FontWeight.Bold), color = Theme.yellow)   // ios: Views/MissionViews.swift:717
                        T("나머지 위젯 모두 열림", style = appFont(17f, FontWeight.Bold))   // ios: Views/MissionViews.swift:718
                        // ios: Views/MissionViews.swift:719
                        FlowRow(spacing = 6.dp) {
                            // ios: Views/MissionViews.swift:720
                            listOf("비중", "배당 달력", "환율", "본전 진행 (큰)").forEach {
                                // ios: Views/MissionViews.swift:721,722
                                T(it, Modifier.background(Theme.slate, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp), style = appFont(12f, FontWeight.SemiBold))
                            }
                        }
                    } }
                    PrimaryButton(title = if (m.playOn) "미션 판으로" else "인터미션 시작") { m.finishAppStart() }   // ios: Views/MissionViews.swift:728
                } else {   // ios: Views/MissionViews.swift:729
                    BoardButtons(m, next = null, to = null)   // ios: Views/MissionViews.swift:730
                }
            }
            }
        }
    }) { _ -> NavPage("내 캐릭터") { MeEditView(m) } }
}

// ios: Views/MissionViews.swift:735
@Composable
private fun tile(i: Int, n: Int, modifier: Modifier, c: @Composable ColumnScope.() -> Unit) {
    // ios: Views/MissionViews.swift:736 (Group)
    // ios: Views/MissionViews.swift:737
    if (n >= i) {
        // ios: Views/MissionViews.swift:738,739,740,741
        Column(modifier.fillMaxWidth().defaultMinSize(minHeight = 108.dp).background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { c() }
    } else {   // ios: Views/MissionViews.swift:742
        val dash = Theme.dash
        // ios: Views/MissionViews.swift:744
        Box(modifier.fillMaxWidth().defaultMinSize(minHeight = 108.dp)
            // ios: Views/MissionViews.swift:745
            .background(Theme.track, RoundedCornerShape(18.dp))
            // ios: Views/MissionViews.swift:746
            .drawBehind { drawRoundRect(dash, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))) },
            contentAlignment = Alignment.Center) {
            // ios: Views/MissionViews.swift:743
            T("${i}단계 하면 열림", style = appFont(12f).copy(textAlign = TextAlign.Center), color = Theme.sub2)
        }
    }
}
