package com.naeilo.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.R
import com.naeilo.app.Tab
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.goalPace
import com.naeilo.app.core.calc.goalReached
import com.naeilo.app.core.calc.goalSaved
import com.naeilo.app.core.calc.graduateToGoal
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keepPlan
import com.naeilo.app.core.calc.noviceOwn
import com.naeilo.app.core.calc.paceText
import com.naeilo.app.core.calc.suggestNextGoal
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.data.short
import com.naeilo.app.net.Market
import com.naeilo.app.net.quote
import com.naeilo.app.pending.AnalysisRoute
import com.naeilo.app.pending.CharPixel
import com.naeilo.app.pending.CharacterDetailView
import com.naeilo.app.pending.DepositLeverCard
import com.naeilo.app.pending.GoalSetView
import com.naeilo.app.pending.MarketEvents
import com.naeilo.app.pending.MeEditView
import com.naeilo.app.pending.ParallelBasketView
import com.naeilo.app.pending.ParallelHomeCard
import com.naeilo.app.pending.ParallelView
import com.naeilo.app.pending.ReaderView
import com.naeilo.app.pending.Remind
import com.naeilo.app.pending.RemindCat
import com.naeilo.app.pending.RemindGo
import com.naeilo.app.pending.RemindSheet
import com.naeilo.app.pending.ShelterView
import com.naeilo.app.pending.StoryBook
import com.naeilo.app.pending.color
import com.naeilo.app.pending.go
import com.naeilo.app.pending.myPath
import com.naeilo.app.pending.myPathGapToday
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.DashedCard
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.pulse
import com.naeilo.app.ui.rememberPrefBool
import com.naeilo.app.ui.stiffness
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 왼쪽 아래만 덜 둥근 말풍선 (UnevenRoundedRectangle 14·4·14·14) */
internal val BubbleCorner = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

// ios: Views/HomeView.swift:3,4,8
@Composable
fun HomeView(m: AppModel) {
    // ios: Views/HomeView.swift:5
    var inbox by remember { mutableStateOf(false) }
    // ios: Views/HomeView.swift:6 금액 가리기는 기기에 남는다
    var hideAmt by rememberPrefBool("hideAmt", false)
    // ios: Views/HomeView.swift:28 기록·소식 계산은 앱을 열 때와 1분 시세 갱신 때 한다 (App). 탭 이동 땐 말풍선만
    LaunchedEffect(Unit) { Remind.shared.nextBubble() }
    // ios: Views/HomeView.swift:9,25,26
    Refreshable({ Market.shared.pullRefresh(m) }, Modifier.background(Theme.bg)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // ios: Views/HomeView.swift:23
            Screen {
                // ios: Views/HomeView.swift:10
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.outset(16.dp)) { AppHeader(tab = "홈") }   // ios: Views/HomeView.swift:11 (-16)
                    SampleNote(m)   // ios: Views/HomeView.swift:12
                    Summary(m, hideAmt) { hideAmt = !hideAmt }   // ios: Views/HomeView.swift:13
                    // 회복 루트: 매달 더 넣으면 본전이 언제인지 (원인 진단·내 계획을 마친 뒤)
                    if (!m.isGoal && m.done.contains(3) && m.cost > m.total && m.rows.isNotEmpty()) DepositLeverCard(plan = m.keepPlan)   // ios: Views/HomeView.swift:15
                    ShelterBox(m) { inbox = true }   // ios: Views/HomeView.swift:16
                    ParallelHomeCard()   // ios: Views/HomeView.swift:17
                    Reminder { inbox = true }   // ios: Views/HomeView.swift:18
                    if (m.playUnlocked) RoutineCard(m) else LockedRoutine()   // ios: Views/HomeView.swift:19
                    if (!m.playUnlocked && m.rows.isNotEmpty()) YesterdayCard(m)   // ios: Views/HomeView.swift:20
                    T(Market.shared.asOfText, style = appFont(13f), color = Theme.sub2)   // ios: Views/HomeView.swift:21
                }
            }
        }
    }
    // ios: Views/HomeView.swift:29
    Sheet(inbox, onDismiss = { inbox = false; Remind.shared.markAllSeen() }) { RemindSheet() }
    // ios: Views/HomeView.swift:30 이동 막대 숨김: 첫 화면엔 막대가 없다 (NavStack 은 쌓인 화면에만 막대를 그린다)
}

/** 홈에서 여는 화면 (navigationDestination for: String) */
// ios: Views/HomeView.swift:31
fun homePage(route: String): NavPage = NavPage {
    when {
        route == "shelter" -> ShelterView()   // ios: Views/HomeView.swift:32
        route == "library" -> ShelterView(onlyLibrary = true)   // ios: Views/HomeView.swift:33
        route == "parallel" -> ParallelView()   // ios: Views/HomeView.swift:34
        route == "parallel:basket" -> ParallelBasketView()   // ios: Views/HomeView.swift:35
        route == "goal" -> GoalSetView(fromSettings = true)   // ios: Views/HomeView.swift:36
        route.startsWith("read:") && route.drop(5).toIntOrNull() != null -> ReaderView(index = route.drop(5).toInt())   // ios: Views/HomeView.swift:37
        route.startsWith("vol1:") && route.drop(5).toIntOrNull() != null -> ReaderView(index = route.drop(5).toInt(), book = StoryBook.vol1)   // ios: Views/HomeView.swift:38
        route == "me" -> MeEditView()   // ios: Views/HomeView.swift:39
        // ios: Views/HomeView.swift:40
        route.startsWith("char:") && Shelter.friends.any { it.id == route.drop(5).split(":").firstOrNull() } -> {
            val f = Shelter.friends.first { it.id == route.drop(5).split(":").firstOrNull() }
            // char:<인물> = 소설 속 갈래로, char:<인물>:invest = 투자하는 갈래로, …:invest:h<번호> = 그 버릇을 펴서
            val parts = route.split(":")   // ios: Views/HomeView.swift:42
            CharacterDetailView(friend = f, invest = parts.contains("invest"),   // ios: Views/HomeView.swift:43
                                focus = parts.lastOrNull()?.let { if (it.startsWith("h")) it.drop(1).toIntOrNull() else null })   // ios: Views/HomeView.swift:44
        }
    }
}

// 어제 종가 기준 평가액 + 본전 진행 막대
// ios: Views/HomeView.swift:50,51
@Composable
private fun Summary(m: AppModel, hideAmt: Boolean, toggleHide: () -> Unit) {
    if (m.isGoal) GoalSummary(m, hideAmt, toggleHide) else RecoverSummary(m)
}

private val C9 = hex(0xC9D0D6)

// 목표 루트: 지금 평가액(플러스) 또는 모은 돈(시작 전)과 목표 진행
// ios: Views/HomeView.swift:55
@Composable
private fun GoalSummary(m: AppModel, hideAmt: Boolean, toggleHide: () -> Unit) {
    // ios: Views/HomeView.swift:56
    val now = if (m.route == Route.novice) m.goalSaved else m.total / 1e4; val value = m.route == Route.plus || m.noviceOwn; val prog = min(1.0, now / max(1.0, m.gK))
    // ios: Views/HomeView.swift:57,91,92,93,94
    Fg(Color.White) {
        Column(
            Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ios: Views/HomeView.swift:58,59
            T(if (value) "평가액" else "모은 돈", style = appFont(13f), color = hex(0xC9D0D6))
            // ios: Views/HomeView.swift:60
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 금액은 길어도 꺾지 않고 한 줄로, 자리가 모자라면 글자를 줄인다
                // 누르면 금액 가리기(다시 누르면 보이기)
                // ios: Views/HomeView.swift:63,64,65
                FitText(if (hideAmt) "••••••" else AppModel.man(now * 1e4) + "원", appFont(32f, FontWeight.Bold).copy(letterSpacing = (-0.5).sp), 0.4f,
                    Modifier.weight(1f, fill = false).alignByBaseline().plainClickable(toggleHide), maxLines = 1, roll = now)
                // ios: Views/HomeView.swift:66
                if (value && m.cost > 0) {
                    // ios: Views/HomeView.swift:67,68
                    FitText(AppModel.sgn(m.ret), appFont(14f, FontWeight.Bold), 0.6f, Modifier.alignByBaseline(), maxLines = 1, color = if (m.ret >= 0) hex(0xFF8A80) else hex(0x8CC4F2))
                }
            }
            if (value && m.route == Route.plus && m.total > 0) TodayLine(m)   // ios: Views/HomeView.swift:71
            // ios: Views/HomeView.swift:72
            Row(Modifier.fillMaxWidth()) {
                T("목표 ${AppModel.wonK(m.gK)} · ${m.gY}년", Modifier.weight(1f), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:73,76
                T(AppModel.pct(prog), style = appFont(13f, FontWeight.Bold), color = Theme.yellow)   // ios: Views/HomeView.swift:74
            }
            ProgressBar(value = prog)   // ios: Views/HomeView.swift:77
            // ios: Views/HomeView.swift:78
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (m.goalReached) "목표 ${AppModel.wonK(m.gK)} 도달!"   // ios: Views/HomeView.swift:79
                  else if (m.route == Route.novice) "매달 ${AppModel.wonK(m.gM)} 적립 · 다음 적립일 +${AppModel.wonK(m.gM)}"   // ios: Views/HomeView.swift:80
                  else AppModel.paceText(m.goalPace()) + (if (m.gM > 0) " · 다음달 ${AppModel.wonK(m.gM)} 적립" else " · 추가 적립 미정"),   // ios: Views/HomeView.swift:81
                  Modifier.weight(1f), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:82
                // ios: Views/HomeView.swift:83
                if (!m.goalReached && m.route != Route.novice && m.gM <= 0) {
                    Spacer(Modifier.width(8.dp))   // ios: Views/HomeView.swift:84
                    // ios: Views/HomeView.swift:85,86
                    Box(Modifier.defaultMinSize(minHeight = 32.dp).plainClickable { m.homePath = m.homePath + "goal" }.semantics { contentDescription = "다음달 적립 정하기" }, contentAlignment = Alignment.Center) {
                        T("정하기 ›", style = appFont(13f, FontWeight.Bold), color = Theme.yellow)
                    }
                }
            }
            if (m.goalReached) DarkButton("다음 목표 정하기") { m.suggestNextGoal(); m.homePath = m.homePath + "goal" }   // ios: Views/HomeView.swift:89
        }
    }
}

// ios: Views/HomeView.swift:97
@Composable
private fun RecoverSummary(m: AppModel) {
    val need = m.cost / max(1.0, m.total) - 1   // ios: Views/HomeView.swift:98
    val needY = m.cost / (m.total / (1 + m.todayMove)) - 1   // ios: Views/HomeView.swift:99
    // ios: Views/HomeView.swift:100,123,124,125,126
    Fg(Color.White) {
        Column(
            Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T("평가액 · 넣은 돈 ${AppModel.man(m.cost)}", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:101
            // ios: Views/HomeView.swift:102
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // ios: Views/HomeView.swift:103,104
                FitText(AppModel.man(m.total), appFont(32f, FontWeight.Bold).copy(letterSpacing = (-0.5).sp), 0.4f, Modifier.weight(1f, fill = false).alignByBaseline(), maxLines = 1, color = Color.White, roll = m.total)
                // ios: Views/HomeView.swift:105,106
                T(AppModel.sgn(m.ret), Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold), maxLines = 1, color = if (m.ret >= 0) hex(0xFF8A80) else hex(0x8CC4F2))
            }
            if (m.total > 0) TodayLine(m)   // ios: Views/HomeView.swift:108
            // ios: Views/HomeView.swift:109
            Row(Modifier.fillMaxWidth()) {
                T("본전까지", Modifier.weight(1f), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:110,111,115
                // ios: Views/HomeView.swift:112,113
                T(if (need > 0) "+" + String.format(java.util.Locale.US, "%.1f", need * 100) + "% 남음" else "본전 도달", style = appFont(13f, FontWeight.Bold), color = Theme.yellow)
            }
            ProgressBar(value = m.total / max(1.0, m.cost))   // ios: Views/HomeView.swift:116
            // ios: Views/HomeView.swift:117,119,120 (118 은 검사 예외: 같은 줄에 옮김)
            T(if (need > 0) "어제 ${String.format(java.util.Locale.US, "%.1f", needY * 100)}% → 오늘 ${String.format(java.util.Locale.US, "%.1f", need * 100)}%"
              else "본전 넘음 · 종목·친구 그대로", style = appFont(13f), color = hex(0xC9D0D6))
            if (need <= 0) DarkButton("목표 루트로") { m.graduateToGoal(); m.tab = Tab.board }   // ios: Views/HomeView.swift:121
        }
    }
}

// 오늘 등락: 부호는 한 번만 (▲/▼ 뒤 퍼센트·금액은 부호 없이). 목표·회복 카드 공통
// ios: Views/HomeView.swift:130
@Composable
private fun TodayLine(m: AppModel) {
    val mv = m.todayMove; val diff = m.stockTotal - m.stockTotal / (1 + mv)   // ios: Views/HomeView.swift:131
    // ios: Views/HomeView.swift:132,133
    T("오늘 " + (if (mv >= 0) "▲ " else "▼ ") + String.format(java.util.Locale.US, "%.1f%%", abs(mv) * 100) + " · " + AppModel.won(abs(diff)),
      style = appFont(13f), color = if (mv >= 0) hex(0xFF8A80) else hex(0x8CC4F2))
}

// 어두운 요약 카드 안의 노란 버튼 (목표 루트로 · 다음 목표)
// ios: Views/HomeView.swift:137,138,142
@Composable
private fun DarkButton(t: String, act: () -> Unit) {
    // ios: Views/HomeView.swift:139,140,141
    Box(Modifier.fillMaxWidth().plainClickable(onClick = act).defaultMinSize(minHeight = 44.dp).background(Theme.yellow, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        T(t + " ›", style = appFont(15f, FontWeight.Bold), color = Theme.inkFixed)
    }
}

// 쉼터 박스: 홈에 둔 친구 + 말풍선 (리마인드 소식이 있으면 그 소식) + 새 소식 + 다른 친구 칸
// ios: Views/HomeView.swift:146
@Composable
private fun ShelterBox(m: AppModel, openInbox: () -> Unit) {
    val on = (0 until 5).count { m.friendOn(it) }   // ios: Views/HomeView.swift:147
    val next = (0 until 5).firstOrNull { !m.friendOn(it) }   // ios: Views/HomeView.swift:148
    val r = Remind.shared; val b = r.bubble; val n = r.fresh.size   // ios: Views/HomeView.swift:149
    val line: String = b?.let { it.text + (if (it.go != RemindGo.nowhere) " ›" else "") } ?: m.homeSay   // ios: Views/HomeView.swift:150
    // 정보 문장(오늘 움직인 종목)을 먼저, 대사는 그 아래
    val say = m.todayMoversText?.let { it + "\n" + m.friendName(m.homeFriendShown) + ": “" + line + "”" } ?: line   // ios: Views/HomeView.swift:152
    val tint: Color = b?.let { it.cat.color.copy(alpha = 0.14f) } ?: Theme.mintBg   // ios: Views/HomeView.swift:153
    // ios: Views/HomeView.swift:154,203,204,205
    Column(
        Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ios: Views/HomeView.swift:155,156,157,169,171,172
        Row(
            Modifier.fillMaxWidth()
                .plainClickable { if (b != null && b.go != RemindGo.nowhere) m.go(b.go) else m.homePath = m.homePath + "shelter" }
                .semantics(mergeDescendants = true) { contentDescription = b?.let { "${it.cat.label} 소식: ${it.text}" } ?: "쉼터 보기" },
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),   // ios: Views/HomeView.swift:158
        ) {
            CharPixel(id = m.homeFriendShown, width = 56.dp, height = 80.dp, modifier = Modifier.charIdle())   // ios: Views/HomeView.swift:159,160
            // ios: Views/HomeView.swift:161,162,163,164,165,166,167
            T(say, Modifier.weight(1f).background(tint, RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
              style = appFont(14f).ls(3f).copy(textAlign = TextAlign.Start))
        }
        // ios: Views/HomeView.swift:173
        if (r.items.isNotEmpty()) {
            // ios: Views/HomeView.swift:174,175,186
            Row(Modifier.fillMaxWidth().offset(y = (-4).dp), horizontalArrangement = Arrangement.End) {
                // ios: Views/HomeView.swift:176,177,178,179,180,181,183,184
                Box(Modifier.defaultMinSize(minHeight = 32.dp).plainClickable(openInbox)
                    .semantics { contentDescription = if (n > 0) "새 소식 ${n}개 보기" else "소식함 보기" }, contentAlignment = Alignment.Center) {
                    T(if (n > 0) "새 소식 $n ›" else "소식함 ›", Modifier.background(if (n > 0) Theme.yellow else Theme.line, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                      style = appFont(12f, FontWeight.Bold), color = if (n > 0) Theme.inkFixed else Theme.sub)
                }
            }
        }
        HDivider(Theme.line)   // ios: Views/HomeView.swift:188
        // ios: Views/HomeView.swift:189,198,200,201
        Row(
            Modifier.fillMaxWidth().plainClickable { m.homePath = m.homePath + "shelter" }.semantics(mergeDescendants = true) { contentDescription = "쉼터 보기" },
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),   // ios: Views/HomeView.swift:190
        ) {
            // ios: Views/HomeView.swift:191,192
            Shelter.friends.withIndex().filter { it.value.id != m.homeFriendShown }.forEach { (i, f) ->
                PixelN((if (m.friendOn(i)) "spr_" else "sil_") + f.id, width = 20.dp, height = 28.dp, alpha = if (m.friendOn(i)) 1f else 0.5f)
            }
            Spacer(Modifier.weight(1f))   // ios: Views/HomeView.swift:194
            // ios: Views/HomeView.swift:195,196
            FitTwoLines("친구 $on/5" + (next?.let { " · 다음 ${Shelter.friends[it].name} ${m.friendWhen(it)}" } ?: " · 외전 5장") + " ›", appFont(12f), 0.8f, Theme.sub, lines = 2)
        }
    }
}

/** .lineLimit(2).minimumScaleFactor(k).multilineTextAlignment(.trailing): 두 줄까지, 넘치면 글자를 줄인다 */
@Composable
private fun FitTwoLines(text: String, style: androidx.compose.ui.text.TextStyle, minScale: Float, color: Color, lines: Int) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints {
        var k = 1f
        if (constraints.hasBoundedWidth) {
            while (k > minScale && measurer.measure(text, style.copy(fontSize = style.fontSize * k), maxLines = lines, constraints = Constraints(maxWidth = constraints.maxWidth)).hasVisualOverflow) k -= 0.05f
        }
        T(text, style = style.copy(fontSize = style.fontSize * maxOf(k, minScale), textAlign = TextAlign.End), color = color, maxLines = lines)
    }
}

/** 노란 띠: 3일 안의 일정 (내 종목 먼저). 없으면 인터미션 이번 할 일. 누르면 소식함 */
// ios: Views/HomeView.swift:209
@Composable
private fun Reminder(openInbox: () -> Unit) {
    val r = Remind.shared   // ios: Views/HomeView.swift:210
    val e = r.strip
    // ios: Views/HomeView.swift:211 (212 는 검사 예외: 같은 줄에 옮김)
    if (e != null) StripRow(tag = "일정", text = "${MarketEvents.`when`(e.day ?: "")} ${e.text} · ${e.sub ?: ""}", openInbox)
    // ios: Views/HomeView.swift:213,214
    else if (r.isOn(RemindCat.doing)) r.doing.firstOrNull { it.id == "do.inter" }?.let { d -> StripRow(tag = "리마인드", text = d.text, openInbox) }
}

// ios: Views/HomeView.swift:217
@Composable
private fun StripRow(tag: String, text: String, openInbox: () -> Unit) {
    // ios: Views/HomeView.swift:218,219,226,227,228,230,231
    Row(
        Modifier.fillMaxWidth().plainClickable(openInbox).semantics(mergeDescendants = true) { contentDescription = "$tag: $text. 소식함 열기" }
            .defaultMinSize(minHeight = 44.dp).background(Theme.cream, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ios: Views/HomeView.swift:220,221
        T(tag, Modifier.background(Theme.yellow, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed)
        T(text, Modifier.weight(1f).padding(end = 0.dp), style = appFont(13f), maxLines = 2)   // ios: Views/HomeView.swift:222,223 (Spacer minLength 0)
        Sym("chevron.right", 11f, Theme.muted)   // ios: Views/HomeView.swift:224
    }
}

// ios: Views/HomeView.swift:234,235
@Composable
private fun LockedRoutine() {
    DashedCard {
        // ios: Views/HomeView.swift:236 Label(systemImage: "lock")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Sym("lock", 15f, Theme.sub); T("오늘의 1분", style = appFont(15f, FontWeight.Bold)) }
        T("시작 3단계 뒤 열림 · 숫자 하나, 질문 하나", style = appFont(13f))   // ios: Views/HomeView.swift:237
    }
}

// ios: Views/HomeView.swift:241,242
@Composable
private fun YesterdayCard(m: AppModel) {
    Card {
        // ios: Views/HomeView.swift:243,245
        Row(Modifier.fillMaxWidth()) {
            T("오늘의 움직임", Modifier.weight(1f).alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/HomeView.swift:244
            T(AppModel.sgn(m.todayMove), Modifier.alignByBaseline(), style = appFont(20f, FontWeight.Bold), color = Theme.teal, roll = AppModel.sgn(m.todayMove))   // ios: Views/HomeView.swift:246
        }
        // ios: Views/HomeView.swift:248
        T(m.rows.joinToString(" · ") { "${it.sym.short} ${AppModel.sgn(it.sym.quote.change)}" } + " · 원/달러 ${AppModel.sgn(Market.shared.fx.change)}", style = appFont(13f), color = Theme.sub)
    }
}

// 오늘의 1분: 내 종목 오늘 숫자 확인 + 날마다 바뀌는 질문 하나 (지난 7일과 안 겹치게). 설명은 계산값으로.
// ios: Views/HomeView.swift:254,255,262
@Composable
fun RoutineCard(m: AppModel) {
    val days = listOf("월", "화", "수", "목", "금", "토", "일")   // ios: Views/HomeView.swift:256
    /** 이번 주 칸 중 채운 날 수 (요일 7칸과 같은 기준) */
    // ios: Views/HomeView.swift:258,259
    val weekDone = (0 until 7).count { i -> val d = m.day - m.day % 7 + i; m.dayLog[d]?.let { it.answer != null } ?: (d < 5 && d < m.day) }
    val t = m.today; val q = m.question   // ios: Views/HomeView.swift:263
    Fg(Color.White) {
        // ios: Views/HomeView.swift:264,284,297,325,329,330,331 withAnimation: 확인·답·날 넘기기로 바뀌는 칸은 기본 스프링으로 자란다
        Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).animateContentSize(spring(dampingRatio = 1f, stiffness = stiffness(0.55f))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ios: Views/HomeView.swift:265,267
            Row(Modifier.fillMaxWidth()) {
                T("오늘의 1분", Modifier.weight(1f).alignByBaseline(), style = appFont(13f, FontWeight.Bold).copy(letterSpacing = 1.sp), color = Theme.mint)   // ios: Views/HomeView.swift:266
                T("이번 주 ${weekDone}일", Modifier.alignByBaseline(), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:268
            }
            // ios: Views/HomeView.swift:270
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // ios: Views/HomeView.swift:271
                for (i in 0 until 7) {
                    val d = m.day - m.day % 7 + i   // ios: Views/HomeView.swift:272
                    val ok = m.dayLog[d]?.let { it.answer != null } ?: (d < 5 && d < m.day)   // ios: Views/HomeView.swift:273
                    // ios: Views/HomeView.swift:274,275,276,277,278
                    Box(
                        Modifier.weight(1f).defaultMinSize(minHeight = 28.dp)
                            .background(if (ok) Theme.mint else if (d == m.day) Color.Transparent else Theme.slate, RoundedCornerShape(8.dp))
                            .then(if (d == m.day && !ok) Modifier.border(2.dp, Theme.yellow, RoundedCornerShape(8.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { T(days[i], style = appFont(12f, FontWeight.Bold), color = if (ok) Theme.inkFixed else if (d == m.day) Theme.yellow else Theme.muted) }
                }
            }
            // ios: Views/HomeView.swift:281
            Step(tag = "1 · 오늘 숫자") {
                TA(md(m.routineFact), style = appFont(15f).ls(3f))   // ios: Views/HomeView.swift:282
                // ios: Views/HomeView.swift:283
                if (!t.seen) {
                    // ios: Views/HomeView.swift:284,285,286,287
                    Box(Modifier.plainClickable { m.markSeen() }.defaultMinSize(minHeight = 40.dp).background(Theme.teal, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        T("확인했어요", style = appFont(14f, FontWeight.Bold), color = Color.White)
                    }
                }
            }
            // ios: Views/HomeView.swift:290,291
            if (t.seen) Step(tag = "2 · ${q.tag}") {
                T(q.q, style = appFont(15f, FontWeight.SemiBold).ls(3f))   // ios: Views/HomeView.swift:292
                // ios: Views/HomeView.swift:293,294
                FlowRow(spacing = 6.dp) {
                    q.opts.indices.forEach { i ->
                        val ans = t.answer   // ios: Views/HomeView.swift:295
                        val right = q.right >= 0 && i == q.right   // ios: Views/HomeView.swift:296
                        // ios: Views/HomeView.swift:297,298,299,300,301,302,303
                        Box(
                            Modifier.then(if (ans == null) Modifier.plainClickable { m.answer(i) } else Modifier.semantics { disabled() })
                                .defaultMinSize(minHeight = 40.dp)
                                .background(if (ans == null) Theme.card else if (right) Theme.tealBg else if (i == ans) Theme.night else Theme.card, RoundedCornerShape(10.dp))
                                .border(2.dp, if (ans == null) Theme.dash else if (right) Theme.teal else if (i == ans) Theme.ink else Theme.track, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { T(q.opts[i], style = appFont(14f, FontWeight.Bold), color = if (ans == null) Theme.ink else if (right) Theme.tealDark else if (i == ans) Color.White else Theme.muted) }
                    }
                }
                // ios: Views/HomeView.swift:306
                t.answer?.let { a ->
                    // ios: Views/HomeView.swift:307,308,309,310,311
                    T((t.ok?.let { if (it) "맞아요! " else "아쉬워요. " } ?: "") + q.fb(a),
                      Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp), style = appFont(13f).ls(3f))
                }
            }
            // ios: Views/HomeView.swift:315
            if (t.answer != null) {
                val sc = m.quizScore   // ios: Views/HomeView.swift:316
                // ios: Views/HomeView.swift:317,319
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(m.routineDoneText, Modifier.weight(1f).alignByBaseline(), style = appFont(14f, FontWeight.SemiBold), color = Theme.yellow)   // ios: Views/HomeView.swift:318
                    if (sc.all > 0) T("퀴즈 ${sc.right}/${sc.all}", Modifier.alignByBaseline(), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:320
                }
            }
            // 날짜 넘기기는 개발자 확인용 (일반 사용자 화면에는 안 보인다)
            // ios: Views/HomeView.swift:324,325,326
            if (m.devAll) Box(Modifier.defaultMinSize(minHeight = 40.dp).plainClickable { m.day += 1 }, contentAlignment = Alignment.Center) {
                T("다음 날로 넘기기 (개발자)", style = appFont(13f).copy(textDecoration = TextDecoration.Underline), color = Theme.muted)
            }
        }
    }
}

/** **굵게** 만 읽는 짧은 마크다운 (AttributedString(markdown:) inlineOnly 자리) */
private fun md(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf("**", i)
        val b = if (a >= 0) s.indexOf("**", a + 2) else -1
        if (a < 0 || b < 0) { append(s.substring(i)); break }
        append(s.substring(i, a))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(a + 2, b)) }
        i = b + 2
    }
}

// ios: Views/HomeView.swift:334,335,339,340,341,342
@Composable
private fun Step(tag: String, c: @Composable () -> Unit) {
    Fg(Theme.ink) {
        Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(tag, style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/HomeView.swift:336
            c()   // ios: Views/HomeView.swift:337
        }
    }
}

// 지금 평가액 카드 오른쪽 작은 그래프: 내 길 '시작부터' (실제 = 민트 선, 내 길 = 보라 점선). 누르면 분석 > 내 길
// ios: Views/HomeView.swift:347,348,349
@Composable
fun MiniPath(m: AppModel) {
    // 분석 > 내 길 '지금까지' 그래프와 같은 점·같은 세로 범위 (목표 금액 포함), 눈금·범위 띠만 뺀다
    val p = m.myPath; val x1 = p.start + 1.0 / 12; val gap = m.myPathGapToday; val K = m.gK * 1e4   // ios: Views/HomeView.swift:351
    val act = p.pts   // ios: Views/HomeView.swift:352
    val need: List<Pair<Double, Double>> = (0..30).map { i -> val t = x1 * i.toDouble() / 30; Pair(t, p.need(t)) }   // ios: Views/HomeView.swift:353
    val needAll = (0..30).map { i -> p.need(p.end * i.toDouble() / 30) }   // ios: Views/HomeView.swift:354
    val vals = act.map { it.second } + needAll + listOf(K)   // ios: Views/HomeView.swift:355
    val hi = (vals.maxOrNull() ?: 1.0) * 1.05; val lo = (vals.filter { it > 0 }.minOrNull() ?: 0.0) * 0.9   // ios: Views/HomeView.swift:356
    val mint = Theme.mint
    // ios: Views/HomeView.swift:357,358,359,360,385,387 (388 은 검사 예외: 같은 줄에 옮김)
    Box(
        Modifier.plainClickable {
            m.tab = Tab.analysis
            m.analysisPath = listOf(AnalysisRoute.myPath)
        }.semantics(mergeDescendants = true) { contentDescription = "내 길 그래프, ${if (gap >= 0) "계획대로" else "계획보다 조금 늦어요"}, 눌러서 분석의 내 길 보기" }
            .padding(bottom = 15.dp),   // ios: Views/HomeView.swift:384
    ) {
        // ios: Views/HomeView.swift:361,377,378
        Canvas(Modifier.size(84.dp, 40.dp).pulse(Theme.mint, dot = 6.dp) { s -> act.lastOrNull()?.let { Offset((p.start / max(1e-6, x1)).toFloat() * s.width, ((hi - it.second) / max(1.0, hi - lo)).toFloat() * s.height) } }) {
            // ios: Views/HomeView.swift:362,363
            fun pt(q: Pair<Double, Double>) = Offset((q.first / max(1e-6, x1)).toFloat() * size.width, ((hi - q.second) / max(1.0, hi - lo)).toFloat() * size.height)
            // ios: Views/HomeView.swift:365,366,367,368
            fun line(qs: List<Pair<Double, Double>>): Path {
                val path = Path()
                qs.forEachIndexed { i, q -> val o = pt(q); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                return path
            }
            drawPath(line(need), hex(0xB79CF0), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))   // ios: Views/HomeView.swift:370
            drawPath(line(act), mint, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))   // ios: Views/HomeView.swift:371
            // ios: Views/HomeView.swift:372
            act.lastOrNull()?.let { last ->
                val end = pt(Pair(p.start, last.second))   // ios: Views/HomeView.swift:373
                drawOval(mint, topLeft = Offset(end.x - 3.dp.toPx(), end.y - 3.dp.toPx()), size = Size(6.dp.toPx(), 6.dp.toPx()))   // ios: Views/HomeView.swift:374
            }
        }
        // ios: Views/HomeView.swift:379,380,381,382
        T(if (gap >= 0) "계획대로" else "조금 늦어요", Modifier.align(Alignment.BottomCenter).offset(y = 16.dp), style = appFont(10f, FontWeight.Bold), color = if (gap >= 0) Theme.teal else hexD(0xB5651D, 0xE8A060), maxLines = 1)
    }
}

// 줄바꿈되는 가로 배치
@Composable
// ios: Views/HomeView.swift:393,394,395,405
fun FlowRow(spacing: Dp = 6.dp, content: @Composable () -> Unit) {
    Layout(content) { ms, c ->
        val sp = spacing.roundToPx()
        val w = if (c.hasBoundedWidth) c.maxWidth else Int.MAX_VALUE   // ios: Views/HomeView.swift:396
        val ps = ms.map { it.measure(Constraints()) }   // ios: Views/HomeView.swift:399,408 (.unspecified)
        var x = 0; var y = 0; var rowH = 0; var maxX = 0   // ios: Views/HomeView.swift:397,406
        val at = ps.map { s ->   // ios: Views/HomeView.swift:398,407
            if (x > 0 && x + s.width > w) { x = 0; y += rowH + sp; rowH = 0 }   // ios: Views/HomeView.swift:400,409
            val o = Pair(x, y)   // ios: Views/HomeView.swift:410
            x += s.width + sp; rowH = max(rowH, s.height); maxX = max(maxX, x - sp)   // ios: Views/HomeView.swift:401,411
            o
        }
        layout(min(w, maxX), y + rowH) { ps.forEachIndexed { i, p -> p.place(at[i].first, at[i].second) } }   // ios: Views/HomeView.swift:403
    }
}

/** 샘플 종목 안내: 새 사용자는 가상 종목 DRNK·QQQ 샘플로 시작한다. 내 종목을 넣기 전까지 탭마다 보인다 */
// ios: Views/HomeView.swift:417,418,419
@Composable
fun SampleNote(m: AppModel) {
    // ios: Views/HomeView.swift:420
    if (m.isSample) {
        // ios: Views/HomeView.swift:421,427,428,429
        Row(
            Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.orange.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ios: Views/HomeView.swift:422,423
            T("샘플", Modifier.alignByBaseline().background(Theme.orange, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(12f, FontWeight.Bold), color = Color.White)
            // ios: Views/HomeView.swift:424,425
            T("지금 숫자는 샘플(가상 종목 DRNK·QQQ)이에요. 종목 탭에서 내 종목을 넣으면 바뀌어요.", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)
        }
    }
}

