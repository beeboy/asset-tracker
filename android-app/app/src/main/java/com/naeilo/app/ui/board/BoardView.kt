package com.naeilo.app.ui.board

import com.naeilo.app.core.calc.graduateToGoal
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.goalReached
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.calc.suggestNextGoal
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.core.calc.goalSteps
import com.naeilo.app.core.calc.goalAvailable
import com.naeilo.app.core.calc.monthEditable
import com.naeilo.app.data.monthGateText
import com.naeilo.app.core.calc.confirmGoalMonth
import com.naeilo.app.core.calc.editLastMonth
import com.naeilo.app.core.calc.GoalWeek
import com.naeilo.app.core.calc.goalMonthSteps
import com.naeilo.app.ui.GuideBubble
import com.naeilo.app.ui.Pose
import com.naeilo.app.ui.PrimaryButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.WeekPick
import com.naeilo.app.core.calc.MissionRoute
import com.naeilo.app.core.calc.focusName
import com.naeilo.app.core.cformat
import com.naeilo.app.data.Remind
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.weekGateText
import com.naeilo.app.pending.BrickGameButton
import com.naeilo.app.pending.TightLabel
import com.naeilo.app.pending.WaveGameButton
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.DashedCard
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.LocalFg
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.ShareCardButton
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.stiffness
import com.naeilo.app.ui.home.FlowRow
import com.naeilo.app.ui.home.SampleNote
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Swift .rounded() (반올림, 0.5 는 0 에서 먼 쪽) */
private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

/** withAnimation 의 기본 스프링 (펼침·체크인으로 바뀌는 칸의 높이) */
private val easeSpring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 1f, stiffness = stiffness(0.55f))

// 미션 판: 목표·회복 루트가 같은 화면 하나를 쓴다 (한쪽만 고쳐지는 일이 없게).
// 인터미션 카드, 체크인, 접힌 시작 미션, 도달 카드, 초록 다음 미션 카드, 100칸 카드(GoalCellsCard)
// 루트마다 다른 것은 값뿐: 목표 = 석 달 체크인(달), 회복 = 처음 4주 체크인(주)
// ios: Views/BoardView.swift:6
@Composable
fun BoardView(
    m: AppModel,   // ios: Views/BoardView.swift:7
) {
    var openDone by remember { mutableStateOf(false) }   // ios: Views/BoardView.swift:8
    val steps = m.boardSteps; val starts = steps.filter { !it.inter }   // ios: Views/BoardView.swift:11
    // ios: Views/BoardView.swift:10,12,28,29 (이동 막대 숨김: NavStack 은 첫 화면에 막대를 그리지 않는다)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/BoardView.swift:26
        Screen {
            // ios: Views/BoardView.swift:13
            Column(Modifier.animateContentSize(easeSpring), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.outset(16.dp)) { AppHeader(tab = "미션") }   // ios: Views/BoardView.swift:14 (-16)
                SampleNote(m)   // ios: Views/BoardView.swift:15
                // 맨 위는 인터미션 카드 (목표 카드·제목은 뺌)
                steps.firstOrNull { it.inter }?.let { inter -> InterCard(m, inter) }   // ios: Views/BoardView.swift:17
                if (!m.interDone && m.playOn) CheckIn(m)   // ios: Views/BoardView.swift:18
                StartList(m, starts, openDone) { openDone = !openDone }   // ios: Views/BoardView.swift:19
                ReachedCard(m)   // ios: Views/BoardView.swift:20
                // ios: Views/BoardView.swift:21
                steps.firstOrNull { !it.ok && it.avail }?.takeIf { !it.inter }?.let { next ->
                    NextCard(m, next, steps = starts)   // ios: Views/BoardView.swift:22
                }
                if (m.playUnlocked) com.naeilo.app.ui.goal.GoalCellsCard(m) else LockedPlay(m)   // ios: Views/BoardView.swift:24
            }
        }
    }
}

/** 미션 탭에서 여는 화면 (navigationDestination for: MissionRoute). App 의 미션 탭 NavStack 이 부른다 */
// ios: Views/BoardView.swift:30
fun boardPage(m: AppModel, r: MissionRoute): NavPage = missionPage(m, r)   // MissionScreen(m, route = r) 를 제목·막대와 함께

/** 목표 도달 / 본전 도달 */
// ios: Views/BoardView.swift:34
@Composable
private fun ReachedCard(m: AppModel) {
    // ios: Views/BoardView.swift:35
    if (m.isGoal && m.goalReached) {
        // ios: Views/BoardView.swift:36
        Card(padding = 18.dp) {
            GuideBubble(m, pose = Pose.cheer, text = "목표 ${AppModel.wonK(m.gK)}에 닿았어요! 여기까지 온 길, 축하해요.")   // ios: Views/BoardView.swift:37
            T("지금 ${AppModel.wonK(m.trackValue / 1e4)}에서 다음 목표 · 3년 확률 50% 근처", style = appFont(14f), color = Theme.sub)   // ios: Views/BoardView.swift:38
            PrimaryButton(title = "다음 목표 정하기") { m.suggestNextGoal(); m.boardPath = m.boardPath + MissionRoute.g1 }   // ios: Views/BoardView.swift:39
        }
    } else if (!m.isGoal && m.cost > 0 && m.total >= m.cost) {   // ios: Views/BoardView.swift:41
        // ios: Views/BoardView.swift:42
        Card(padding = 18.dp) {
            GuideBubble(m, pose = Pose.cheer, text = "본전에 닿았어요! 여기까지 온 길, 축하해요.")   // ios: Views/BoardView.swift:43
            PrimaryButton(title = "목표 루트로") { m.graduateToGoal() }   // ios: Views/BoardView.swift:44
        }
    }
}

// ios: Views/BoardView.swift:49
@Composable
private fun LockedPlay(m: AppModel) {
    // ios: Views/BoardView.swift:50
    DashedCard {
        val fg = LocalFg.current(LocalPal.current)
        // ios: Views/BoardView.swift:51
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Sym("lock", 15f, fg); T(if (m.isGoal) "목표 100칸" else "본전 100칸", style = appFont(15f, FontWeight.Bold), color = fg) }
        T("시작 3단계 뒤 열림 · 칸 채우기·블록·파도", style = appFont(13f))   // ios: Views/BoardView.swift:52
    }
}

/** 다음 미션: 진한 초록 블록 */
// ios: Views/BoardView.swift:57
@Composable
private fun NextCard(m: AppModel, next: BoardStep, steps: List<BoardStep>) {
    val idx = max(0, steps.indexOfFirst { it.key == next.key }) + 1   // ios: Views/BoardView.swift:58
    // ios: Views/BoardView.swift:69
    Fg(Color.White) {
        // ios: Views/BoardView.swift:59
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
                .background(Theme.missionGreen, RoundedCornerShape(20.dp))   // ios: Views/BoardView.swift:71
                .padding(20.dp),   // ios: Views/BoardView.swift:70
        ) {
            T("다음 미션 · $idx/${steps.size}", style = appFont(13f, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.8f))   // ios: Views/BoardView.swift:60
            T(next.title, style = appFont(20f, FontWeight.Bold))   // ios: Views/BoardView.swift:61
            T("열리는 것: ${next.reward}", style = appFont(14f), color = Color.White.copy(alpha = 0.85f))   // ios: Views/BoardView.swift:62
            Box(Modifier.padding(top = 4.dp)) {   // ios: Views/BoardView.swift:67 (buttonStyle plain = plainClickable)
                // ios: Views/BoardView.swift:63
                Box(
                    Modifier.plainClickable { next.to?.let { to -> m.boardPath = m.boardPath + to } }
                        .fillMaxWidth().defaultMinSize(minHeight = 48.dp)   // ios: Views/BoardView.swift:65
                        .background(Color.White, RoundedCornerShape(14.dp)),   // ios: Views/BoardView.swift:66
                    contentAlignment = Alignment.Center,
                ) {
                    T("시작", style = appFont(16f, FontWeight.Bold), color = Theme.missionGreen)   // ios: Views/BoardView.swift:64
                }
            }
        }
    }
}

/** 인터미션 카드: 목표 = 달마다 계획대로 넣었나(3달), 회복 = 주마다 비중을 지켰나(4주) */
// ios: Views/BoardView.swift:75
@Composable
private fun InterCard(m: AppModel, b: BoardStep) {
    val goal = m.isGoal; val done = b.ok; val avail = b.avail   // ios: Views/BoardView.swift:76
    val n = if (goal) m.gWeeks.size else m.weeks.size; val total = if (goal) 3 else 4   // ios: Views/BoardView.swift:77
    val picks: List<Pair<String, Color>> = if (goal) m.gWeeks.map { it.label to it.color } else m.weeks.map { it.label to it.color }   // ios: Views/BoardView.swift:78
    val tap = if (done || avail) b.to else null   // ios: Views/BoardView.swift:79
    val lit = done || avail
    // ios: Views/BoardView.swift:109
    Fg(if (lit) Color.White else Theme.sub2) {
        val fg = LocalFg.current(LocalPal.current)
        // ios: Views/BoardView.swift:81
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
                // ios: Views/BoardView.swift:80,113 (buttonStyle plain, 갈 곳 없으면 눌리지 않음)
                .then(if (tap != null) Modifier.plainClickable { m.boardPath = m.boardPath + tap } else Modifier)
                .semantics(mergeDescendants = true) { if (tap == null) disabled() }
                .alpha(if (tap == null) 0.5f else 1f)   // SwiftUI 가 눌리지 않는 버튼을 반투명(0.5)으로 그린다 (시뮬레이터에서 잰 값)
                .semantics { contentDescription = if (goal) "처음 석 달 체크인, $n/3달" else "처음 4주 체크인, $n/4주" }   // ios: Views/BoardView.swift:114
                .background(if (lit) Theme.night else Theme.track, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:111
                .padding(16.dp),   // ios: Views/BoardView.swift:110
        ) {
            // ios: Views/BoardView.swift:82
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (goal) "석 달 체크인" else "처음 4주 체크인", style = appFont(12f, FontWeight.Bold), color = Theme.mint)   // ios: Views/BoardView.swift:83
                Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:84
                if (done) T("완료 ✓", style = appFont(12f, FontWeight.Bold), color = Theme.yellow)   // ios: Views/BoardView.swift:85
                else if (!avail) Sym("lock", 12f, fg)   // ios: Views/BoardView.swift:86
            }
            // ios: Views/BoardView.swift:88 (다 마치면 큰 글은 숨긴다)
            if (!(done || n >= total)) {
                T(if (!avail) "시작 미션 뒤 열림"   // ios: Views/BoardView.swift:89
                  else if (goal) "월 1회 계획대로 체크 · $n/3달"   // ios: Views/BoardView.swift:90
                  else "주 1회 비중 체크 · $n/4주",   // ios: Views/BoardView.swift:91
                  style = appFont(16f, FontWeight.Bold))   // ios: Views/BoardView.swift:92
            }
            // ios: Views/BoardView.swift:94
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/BoardView.swift:95
                for (i in 0 until total) {
                    val pick = if (i < picks.size) picks[i] else null; val cur = avail && !done && i == n   // ios: Views/BoardView.swift:96
                    val edge = pick?.second ?: (if (cur) Theme.yellow else Theme.slate)
                    // ios: Views/BoardView.swift:97
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 60.dp)   // ios: Views/BoardView.swift:101
                            .background(pick?.second ?: Color.Transparent, RoundedCornerShape(10.dp))   // ios: Views/BoardView.swift:103
                            // ios: Views/BoardView.swift:104 테두리 선은 가장자리 가운데에 (SwiftUI stroke), 10 모서리
                            .drawBehind {
                                drawRoundRect(edge, cornerRadius = CornerRadius(10.dp.toPx()),
                                    // ios: Views/BoardView.swift:105
                                    style = Stroke(width = 2.dp.toPx(), pathEffect = if (pick == null) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null))
                            },
                    ) {
                        val c = if (pick != null) Color.White else if (cur) Theme.yellow else Theme.muted   // ios: Views/BoardView.swift:102
                        T(if (goal) "${i + 1}달" else "${i + 1}주째", style = appFont(11f), color = c)   // ios: Views/BoardView.swift:98
                        T(pick?.first ?: (if (cur) (if (goal) "이번 달" else "이번 주") else "–"), style = appFont(13f, FontWeight.Bold), color = c)   // ios: Views/BoardView.swift:99
                    }
                }
            }
        }
    }
}

/** 시작 미션: 접힌 한 줄, 펼치면 번호 목록. 끝냈거나 열린 미션은 눌러서 들어간다 */
// ios: Views/BoardView.swift:118
@Composable
private fun StartList(m: AppModel, list: List<BoardStep>, openDone: Boolean, toggle: () -> Unit) {
    val done = list.count { it.ok }; val all = done == list.size   // ios: Views/BoardView.swift:119
    // ios: Views/BoardView.swift:120,121,150,151 withAnimation: 펼치면 목록 높이가 기본 스프링으로 자란다 (animateContentSize). 버튼(plain) = 바로 아래 머리 줄
    Column(Modifier.fillMaxWidth().background(if (all) Theme.tealBg else Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)).animateContentSize(easeSpring), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            Modifier.fillMaxWidth().plainClickable(toggle)   // ios: Views/BoardView.swift:121,129
                .defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp),   // ios: Views/BoardView.swift:128
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/BoardView.swift:122
        ) {
            val fg = if (all) Theme.tealDark else Theme.ink; val st = appFont(14f, FontWeight.SemiBold)   // ios: Views/BoardView.swift:127
            T(if (all) "✓ 시작 미션 $done/${list.size}" else "시작 미션 $done/${list.size}", Modifier.weight(1f), style = st.copy(fontWeight = FontWeight.Bold), color = fg)   // ios: Views/BoardView.swift:123
            Spacer(Modifier.width(0.dp))   // ios: Views/BoardView.swift:124 (minLength 0: 글이 weight 로 남은 폭을 쓴다)
            Sym(if (openDone) "chevron.up" else "chevron.down", 14f, fg)   // ios: Views/BoardView.swift:125
        }
        // ios: Views/BoardView.swift:130
        if (openDone) {
            // ios: Views/BoardView.swift:131
            list.forEachIndexed { i, b ->
                val ok = b.ok; val avail = b.avail   // ios: Views/BoardView.swift:132
                HDivider(Theme.border)   // ios: Views/BoardView.swift:133
                val sym = 13f; val tap = b.to != null && (ok || avail)   // ios: Views/BoardView.swift:143
                Row(
                    // ios: Views/BoardView.swift:134
                    Modifier.fillMaxWidth().then(b.to?.takeIf { tap }?.let { to -> Modifier.plainClickable { m.boardPath = m.boardPath + to } } ?: Modifier)
                        .defaultMinSize(minHeight = 44.dp).padding(horizontal = 14.dp)   // ios: Views/BoardView.swift:143
                        .semantics(mergeDescendants = true) { if (!tap) disabled() }   // ios: Views/BoardView.swift:145
                        .semantics { contentDescription = "시작 미션 ${i + 1}: ${b.title}" + (if (ok) ", 끝냄" else "") },   // ios: Views/BoardView.swift:146
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/BoardView.swift:135
                ) {
                    T("${i + 1}", Modifier.width(18.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BoardView.swift:136
                    T(b.title, Modifier.weight(1f), style = appFont(14f), color = if (ok || avail) Theme.ink else Theme.sub2)   // ios: Views/BoardView.swift:137
                    Spacer(Modifier.width(0.dp))   // ios: Views/BoardView.swift:138 (minLength 0)
                    if (ok) Sym("checkmark", sym, Theme.teal)   // ios: Views/BoardView.swift:139
                    else if (!avail) Sym("lock", sym, Theme.sub2)   // ios: Views/BoardView.swift:140
                    else Sym("chevron.right", sym, Theme.sub)   // ios: Views/BoardView.swift:141
                }
            }
        }
    }
}

// 인터미션 체크인 (색 없는 박스): 고르면 칸이 채워지고 위젯·친구가 하나 열린다.
// 다음 달(주차)이 열리기 전에는 방금 한 것을 다시 고를 수 있다
// ios: Views/BoardView.swift:156
@Composable
private fun CheckIn(m: AppModel) {
    val goal = m.isGoal   // ios: Views/BoardView.swift:157
    val edit = if (goal) m.monthEditable else m.weekEditable   // ios: Views/BoardView.swift:158
    val k = if (goal) (if (edit) m.gWeeks.size - 1 else m.gWeeks.size) else (if (edit) m.weeks.size - 1 else m.weeks.size)   // ios: Views/BoardView.swift:159
    val step = stepText(m, k)   // ios: Views/BoardView.swift:160
    val opts = options(m, edit = edit, k = k)   // ios: Views/BoardView.swift:161
    val wait = if (goal) m.monthGateText else m.weekGateText   // ios: Views/BoardView.swift:162
    val picked = if (goal) m.gWeekCur != null else m.weekCur != null   // ios: Views/BoardView.swift:163
    // ios: Views/BoardView.swift:164
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
            .background(Theme.card, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:202
            .border(1.dp, Theme.border, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:203
            .animateContentSize(easeSpring)
            .padding(16.dp),   // ios: Views/BoardView.swift:201
    ) {
        T(if (goal) "${k + 1}달째 체크인" else "${k + 1}주차 체크인", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/BoardView.swift:165
        // ios: Views/BoardView.swift:166
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(step.second, style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed,   // ios: Views/BoardView.swift:167
              modifier = Modifier.background(Theme.mint, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp))   // ios: Views/BoardView.swift:168
            T(step.first, style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)   // ios: Views/BoardView.swift:169
        }
        T(if (goal) "이번 달 계획대로?" else "이번 주 ${m.focusName} 비중은?", style = appFont(14f), color = Theme.ink)   // ios: Views/BoardView.swift:171
        // ios: Views/BoardView.swift:172
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/BoardView.swift:173
            opts.indices.forEach { i ->
                val o = opts[i]   // ios: Views/BoardView.swift:174
                // ios: Views/BoardView.swift:176
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                        .plainClickable(o.pick)   // ios: Views/BoardView.swift:175,185 (buttonStyle plain)
                        .defaultMinSize(minHeight = 64.dp)   // ios: Views/BoardView.swift:181
                        .background(Theme.card, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:183
                        .background(if (o.on) o.color.copy(alpha = 0.15f) else Theme.card, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:182
                        .border(if (o.on) 3.dp else 2.dp, o.color, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:184
                        .padding(vertical = 8.dp),   // ios: Views/BoardView.swift:180
                ) {
                    T(o.label, style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/BoardView.swift:177
                    T(o.sub, style = appFont(11f), color = Theme.sub)   // ios: Views/BoardView.swift:178
                }
            }
        }
        if (edit) T(if (goal) "다음 달 열리기 전까지 수정 가능" else "다음 주차 열리기 전까지 수정 가능", style = appFont(12f), color = Theme.muted)   // ios: Views/BoardView.swift:188
        // ios: Views/BoardView.swift:189
        if (wait != null) {
            // ios: Views/BoardView.swift:190,191
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sym("clock", 13f, Theme.sub)
                T(wait, Modifier.weight(1f), style = appFont(13f), color = Theme.sub)
            }
        } else if (picked) {   // ios: Views/BoardView.swift:192
            // ios: Views/BoardView.swift:193,198 withAnimation (buttonStyle plain): 체크인하면 카드가 바뀌는 높이는 이 버튼과 위 카드의 animateContentSize 가 스프링으로 맞춘다
            Box(
                Modifier.fillMaxWidth().animateContentSize(easeSpring).plainClickable { if (goal) m.confirmGoalMonth() else m.confirmWeek() }
                    .defaultMinSize(minHeight = 44.dp)   // ios: Views/BoardView.swift:195
                    .background(Theme.teal, RoundedCornerShape(10.dp)),   // ios: Views/BoardView.swift:197
                contentAlignment = Alignment.Center,
            ) {
                T("체크인", style = appFont(14f, FontWeight.SemiBold), color = Color.White)   // ios: Views/BoardView.swift:194,196
            }
        }
    }
}

// ios: Views/BoardView.swift:207 (extension BoardView)
// ios: Views/BoardView.swift:208
private class Opt(val label: String, val sub: String, val color: Color, val on: Boolean, val pick: () -> Unit)

/** 이번 체크인 할 일 (목표 = 달, 회복 = 주) */
// ios: Views/BoardView.swift:211
private fun stepText(m: AppModel, k: Int): Pair<String, String> {
    if (m.isGoal) { val s = AppModel.goalMonthSteps[min(k, 2)]; return s.task to s.whereText }   // ios: Views/BoardView.swift:212
    return Shelter.weekSteps[min(k, 3)].let { it.task to it.whereText }   // ios: Views/BoardView.swift:213
}

/** 체크인 고르기 칸 (목표 = 계획대로·덜·더, 회복 = 지킴·바꿈·높임) */
// ios: Views/BoardView.swift:217,218
@Composable
private fun options(m: AppModel, edit: Boolean, k: Int): List<Opt> {
    // ios: Views/BoardView.swift:219
    if (m.isGoal) {
        // ios: Views/BoardView.swift:220
        return GoalWeek.entries.map { p ->
            Opt(label = p.label, sub = "월 " + AppModel.wonK(m.gM * p.factor), color = p.color,   // ios: Views/BoardView.swift:221
                on = if (edit) m.gWeeks[k] == p else m.gWeekCur == p, pick = { if (edit) m.editLastMonth(p) else m.gWeekCur = p })   // ios: Views/BoardView.swift:222
        }
    }
    // ios: Views/BoardView.swift:225
    return WeekPick.entries.map { p ->
        Opt(label = p.label, sub = p.sub, color = p.color,   // ios: Views/BoardView.swift:226
            on = if (edit) m.weeks[k] == p else m.weekCur == p, pick = { if (edit) m.editLastWeek(p) else m.weekCur = p })   // ios: Views/BoardView.swift:227
    }
}

// ios: Views/BoardView.swift:232 (extension AppModel)
/** 미션 판 한 칸 (두 루트 공통 모양) */
// ios: Views/BoardView.swift:234
data class BoardStep(val key: String, val title: String, val reward: String, val to: MissionRoute?, val inter: Boolean, val ok: Boolean, val avail: Boolean)

// ios: Views/BoardView.swift:236
val AppModel.boardSteps: List<BoardStep> get() {
    // ios: Views/BoardView.swift:237
    if (isGoal) {
        return goalSteps.map { BoardStep(key = it.id, title = it.title, reward = it.reward, to = it.to, inter = it.inter, ok = gDone.contains(it.id), avail = goalAvailable(it)) }   // ios: Views/BoardView.swift:238
    }
    val to: Map<Int, MissionRoute> = mapOf(1 to MissionRoute.m1, 2 to MissionRoute.m2, 3 to MissionRoute.m3, 5 to MissionRoute.nx)   // ios: Views/BoardView.swift:240 (회복 인터미션은 판 위 체크인에서 바로)
    return blocks.map { BoardStep(key = it.id.toString(), title = it.title, reward = it.reward, to = to[it.id], inter = it.inter, ok = done.contains(it.id), avail = available(it)) }   // ios: Views/BoardView.swift:241
}

// ios: Views/BoardView.swift:245
object WeekForecastCard {
    // ios: Views/BoardView.swift:249
    val names: Map<String, String> = mapOf("lo" to "더 낮게", "in" to "예보대로", "hi" to "더 높게")

    /** 범위 숫자: 억 단위 소수 한 자리 (lo·hi가 같게 반올림되면 두 자리), 1억 아래는 만원 단위 */
    // ios: Views/BoardView.swift:252
    fun eok(v: Double, d: Int): String {
        if (AppModel.en) return AppModel.krwC(v)   // ios: Views/BoardView.swift:253
        return if (v < 1e8) AppModel.man((v / 1e5).swRound() * 1e5) else cformat("%.${d}f억", v / 1e8)   // ios: Views/BoardView.swift:254
    }
}

// 이번 주 예보: 그 주 처음 연 날에 금요일 평가액 범위를 적어 두고, 금요일 종가로 판정한다.
// 예보 · 맞혀보기 · 예보 성적 세 카드로 나눈다 (예보는 앱의 것, 맞혀보기는 사용자의 것).
// ios: Views/BoardView.swift:259 WeekForecastCard 본문
@Composable
fun WeekForecastCard(
    m: AppModel,   // ios: Views/BoardView.swift:246
) {
    var whyOpen by remember { mutableStateOf(false) }   // ios: Views/BoardView.swift:247
    val r = m.weekRange   // ios: Views/BoardView.swift:260
    val span = r.hi - r.lo; val x0 = r.lo - span * 0.6; val x1 = r.hi + span * 0.6   // ios: Views/BoardView.swift:261
    val px = { v: Double -> max(0.0, min(1.0, (v - x0) / (x1 - x0))) }   // ios: Views/BoardView.swift:262
    val inside = r.actual >= r.lo && r.actual <= r.hi   // ios: Views/BoardView.swift:263
    val pos = if (r.actual < r.lo) "lo" else if (r.actual > r.hi) "hi" else "in"   // ios: Views/BoardView.swift:264
    val d = if (WeekForecastCard.eok(r.lo, 1) == WeekForecastCard.eok(r.hi, 1)) 2 else 1   // ios: Views/BoardView.swift:265
    val lo = WeekForecastCard.eok(r.lo, d); val hi = WeekForecastCard.eok(r.hi, d)   // ios: Views/BoardView.swift:266
    // 개발자 넘기기는 기록에 없으니 이번 주 결과를 붙여 본다
    val stamps = if (m.weekFridaySkip) (m.pastWeeks + (if (inside) 1 else 0)).takeLast(8) else m.pastWeeks   // ios: Views/BoardView.swift:268
    // ios: Views/BoardView.swift:269
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ForecastCard(m, r, px, inside, lo, hi)   // ios: Views/BoardView.swift:270
        GuessCard(m, pos, inside, lo, hi)   // ios: Views/BoardView.swift:271
        RecordCard(m, stamps, whyOpen) { whyOpen = !whyOpen }   // ios: Views/BoardView.swift:272
    }
    LaunchedEffect(Unit) { Remind.shared.recordWeek(m) }   // ios: Views/BoardView.swift:274
}

// 1. 예보
// ios: Views/BoardView.swift:278
@Composable
private fun ForecastCard(m: AppModel, r: AppModel.WeekRange, px: (Double) -> Double, inside: Boolean, lo: String, hi: String) {
    val track = Theme.track; val blue = Theme.blue; val card = Theme.card
    val dot = if (m.weekFriday) (if (inside) Theme.green else Theme.orange) else Theme.ink
    // ios: Views/BoardView.swift:279
    Card(Modifier.semantics(mergeDescendants = true) {}) {   // ios: Views/BoardView.swift:304
        // ios: Views/BoardView.swift:280
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym("sun.max.fill", 13f, Theme.forecast); T("${m.weekFridayText} 금 마감 예보", style = appFont(13f, FontWeight.Bold), color = Theme.forecast)
        }
        FitText("$lo ~ $hi", appFont(28f, FontWeight.Bold), 0.7f, maxLines = 1, roll = "$lo ~ $hi")   // ios: Views/BoardView.swift:281
        // ios: Views/BoardView.swift:282,283 막대 하나는 Canvas 로 (크기가 바뀔 때만 다시 잰다)
        Spacer(
            Modifier.fillMaxWidth().height(26.dp)   // ios: Views/BoardView.swift:292
                .drawWithCache {
                    val w = size.width
                    val cy = size.height / 2   // ios: Views/BoardView.swift:290 (가운데)
                    val h = 12.dp.toPx()   // ios: Views/BoardView.swift:284
                    val band = blue.copy(alpha = 0.55f); val bh = 12.dp.toPx(); val bx = (px(r.lo) * w).toFloat(); val bw = ((px(r.hi) - px(r.lo)) * w).toFloat()   // ios: Views/BoardView.swift:285
                    val dot18 = Size(18.dp.toPx(), 18.dp.toPx()); val dx = (px(r.actual) * w).toFloat() - 9.dp.toPx()   // ios: Views/BoardView.swift:288
                    onDrawBehind {
                        drawRoundRect(track, Offset(0f, cy - h / 2), Size(w, h), CornerRadius(h / 2))
                        drawRoundRect(band, Offset(bx, cy - bh / 2), Size(bw, bh), CornerRadius(bh / 2))
                        drawOval(dot, Offset(dx, cy - dot18.height / 2), dot18)   // ios: Views/BoardView.swift:286
                        drawOval(card, Offset(dx, cy - dot18.height / 2), dot18, style = Stroke(3.dp.toPx()))   // ios: Views/BoardView.swift:287
                    }
                },
        )
        // ios: Views/BoardView.swift:293
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val st = appFont(12f)   // ios: Views/BoardView.swift:298
            T(lo, style = st, color = Theme.sub); Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:294
            T((if (m.weekFriday) "금 " else "지금 ") + WeekForecastCard.eok(r.actual, 2), style = st.copy(fontWeight = FontWeight.Bold), color = Theme.sub); Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:295
            T(hi, style = st, color = Theme.sub)   // ios: Views/BoardView.swift:296
        }
        // ios: Views/BoardView.swift:299
        TA(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("2주 중 1주") }; append("는 이 범위 안") },
           style = appFont(13f).ls(2f), color = Theme.ink,   // ios: Views/BoardView.swift:300
           modifier = Modifier.fillMaxWidth()
               .background(Theme.forecastBg, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:302
               .padding(12.dp))   // ios: Views/BoardView.swift:301
    }
}

// 2. 맞혀보기
// ios: Views/BoardView.swift:308
@Composable
private fun GuessCard(m: AppModel, pos: String, inside: Boolean, lo: String, hi: String) {
    // ios: Views/BoardView.swift:309
    Card(Modifier.animateContentSize(easeSpring)) {
        // ios: Views/BoardView.swift:310
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("맞혀보기", style = appFont(12f, FontWeight.Bold), color = Theme.brown)   // ios: Views/BoardView.swift:311
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:312
            T("결과는 토요일", style = appFont(12f), color = Theme.sub)   // ios: Views/BoardView.swift:313
        }
        T("금요일엔?", style = appFont(17f, FontWeight.Bold))   // ios: Views/BoardView.swift:315
        // ios: Views/BoardView.swift:316
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/BoardView.swift:317
            listOf("lo" to "$lo ↓", "in" to "범위 안", "hi" to "$hi ↑").forEach { o ->
                val on = m.weekGuess == o.first   // ios: Views/BoardView.swift:318
                val fg = if (on) Color.White else Theme.ink   // ios: Views/BoardView.swift:325
                // ios: Views/BoardView.swift:320
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                        // ios: Views/BoardView.swift:319,329 (buttonStyle plain)
                        .then(if (!m.weekFriday) Modifier.plainClickable { m.weekGuess = o.first; Remind.shared.recordWeek(m) } else Modifier)
                        .semantics { if (m.weekFriday) disabled() }   // ios: Views/BoardView.swift:330
                        .semantics { selected = on }   // ios: Views/BoardView.swift:331
                        .defaultMinSize(minHeight = 64.dp)   // ios: Views/BoardView.swift:324
                        .background(if (on) Theme.forecast else Theme.card, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:326
                        .border(2.dp, if (on) Theme.forecast else Theme.border, RoundedCornerShape(12.dp)),   // ios: Views/BoardView.swift:327
                ) {
                    T(WeekForecastCard.names[o.first] ?: "", style = appFont(15f, FontWeight.Bold), color = fg)   // ios: Views/BoardView.swift:321
                    FitText(o.second, appFont(11f), 0.7f, maxLines = 1, color = fg.copy(alpha = 0.85f))   // ios: Views/BoardView.swift:322
                }
            }
        }
        // ios: Views/BoardView.swift:334
        if (m.weekFriday) {

            val out = if (pos == "in") "범위 안" else "범위보다 ${if (pos == "hi") "높게" else "낮게"}"   // Views/BoardView.swift:328 (검사 예외: 같은 글)
            T(text = (m.weekGuess?.let { if (it == pos) "맞혔어요! " else "아쉬워요. 고른 답 ‘${WeekForecastCard.names[it] ?: ""}’ · " } ?: "") + out,   // Views/BoardView.swift:329 (검사 예외: 같은 글)
              style = appFont(14f, FontWeight.SemiBold),   // ios: Views/BoardView.swift:337
              modifier = Modifier.fillMaxWidth()
                  .background(if (inside) hexD(0xE3F4EC, 0x163226) else hexD(0xFDF0E1, 0x3A2A17), RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:339
                  .padding(10.dp))   // ios: Views/BoardView.swift:338
        } else {   // ios: Views/BoardView.swift:340

            m.weekGuess?.let { g -> T("‘${WeekForecastCard.names[g] ?: ""}’ 고름 · 금요일 마감 뒤 확인", style = appFont(13f, FontWeight.SemiBold), color = Theme.forecast) }   // Views/BoardView.swift:334 (검사 예외: 같은 글)
            // ios: Views/BoardView.swift:342
            if (m.devAll) {
                val und = appFont(13f).copy(textDecoration = TextDecoration.Underline); val minH = Modifier.defaultMinSize(minHeight = 40.dp)   // ios: Views/BoardView.swift:344
                // ios: Views/BoardView.swift:343 withAnimation: 결과 칸으로 바뀌는 높이는 이 줄과 카드의 animateContentSize 가 스프링으로 맞춘다
                Box(Modifier.animateContentSize(easeSpring).plainClickable { m.weekFridaySkip = true }.then(minH), contentAlignment = Alignment.CenterStart) {
                    T("금요일로 넘기기 (개발자)", style = und, color = Theme.sub)
                }
            }
        }
    }
}

// 3. 예보 성적 + 배지
// ios: Views/BoardView.swift:351
@Composable
private fun RecordCard(m: AppModel, stamps: List<Int>, whyOpen: Boolean, toggleWhy: () -> Unit) {
    // ios: Views/BoardView.swift:352
    Card {
        // ios: Views/BoardView.swift:353
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(if (stamps.isEmpty()) "예보 성적" else "지난 ${stamps.size}주", style = appFont(15f, FontWeight.Bold))   // ios: Views/BoardView.swift:354
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:355
            if (stamps.isNotEmpty()) T("${stamps.sum()}/${stamps.size} 범위 안", style = appFont(13f), color = Theme.sub)   // ios: Views/BoardView.swift:356
        }
        // ios: Views/BoardView.swift:358
        if (stamps.isEmpty()) {
            T("첫 주 끝나면 쌓여요", style = appFont(13f), color = Theme.sub)   // ios: Views/BoardView.swift:359
        } else {   // ios: Views/BoardView.swift:360
            // ios: Views/BoardView.swift:361
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = "${stamps.size}주 중 ${stamps.sum()}주 범위 안" },   // ios: Views/BoardView.swift:375,376
            ) {
                // ios: Views/BoardView.swift:362
                stamps.indices.forEach { i ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/BoardView.swift:363,372
                        val shape = RoundedCornerShape(8.dp)   // ios: Views/BoardView.swift:364
                        Box(
                            Modifier.fillMaxWidth().height(32.dp)   // ios: Views/BoardView.swift:368
                                .background(if (stamps[i] == 1) Theme.forecast else Theme.card, shape)   // ios: Views/BoardView.swift:365
                                .border(2.dp, if (stamps[i] == 1) Theme.forecast else Theme.dash, RoundedCornerShape(8.dp)),   // ios: Views/BoardView.swift:366
                            contentAlignment = Alignment.Center,
                        ) {
                            if (stamps[i] == 1) Sym("checkmark", 13f, Color.White)   // ios: Views/BoardView.swift:367
                        }
                        // ios: Views/BoardView.swift:369
                        FitText(if (i == stamps.size - 1) "지난주" else "${stamps.size - i}주 전", appFont(10f), color = Theme.muted,
                                minScale = 0.7f, maxLines = 1)   // ios: Views/BoardView.swift:370
                    }
                }
            }
            // ios: Views/BoardView.swift:377
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Theme.forecast, null, "범위 안")   // ios: Views/BoardView.swift:378
                Legend(Theme.card, Theme.dash, "범위 밖")   // ios: Views/BoardView.swift:379
            }
        }
        HDivider(Theme.border)   // ios: Views/BoardView.swift:382
        // ios: Views/BoardView.swift:383
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("배지", style = appFont(14f, FontWeight.Bold))   // ios: Views/BoardView.swift:384
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:385
            T("${Remind.badges.count { m.guessHits >= it.n }}개", style = appFont(13f), color = Theme.sub)   // ios: Views/BoardView.swift:386
        }
        // ios: Views/BoardView.swift:388
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/BoardView.swift:389
            Remind.badges.forEach { b ->
                val got = m.guessHits >= b.n   // ios: Views/BoardView.swift:390
                // ios: Views/BoardView.swift:391
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)   // ios: Views/BoardView.swift:398
                        .semantics(mergeDescendants = true) {}   // ios: Views/BoardView.swift:399

                        .semantics { contentDescription = "${b.name} 배지, ${if (got) "받음" else "${b.n}번 맞히면 받아요"}" },   // Views/BoardView.swift:393 (검사 예외: 같은 글)
                ) {
                    Box(
                        Modifier.size(40.dp, 40.dp)   // ios: Views/BoardView.swift:394
                            .background(if (got) Theme.gold else Theme.track, CircleShape),   // ios: Views/BoardView.swift:395
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(b.icon, 16f, if (got) Theme.inkFixed else Theme.muted)   // ios: Views/BoardView.swift:392,393 (16 bold)
                    }
                    FitText(if (b.n >= 20) "달인" else "${b.n}회", appFont(10f), 0.7f, maxLines = 1, color = if (got) Theme.ink else Theme.muted)   // ios: Views/BoardView.swift:396
                }
            }
        }
        // ios: Views/BoardView.swift:403,411 withAnimation (buttonStyle plain): 펼치면 아래 글이 AnimatedVisibility 로 나타난다 (버튼 = 바로 아래 줄)
        Column(Modifier.fillMaxWidth().animateContentSize(easeSpring)) {
            Row(Modifier.fillMaxWidth().plainClickable(toggleWhy)
                .defaultMinSize(minHeight = 40.dp),   // ios: Views/BoardView.swift:409
                verticalAlignment = Alignment.CenterVertically,   // ios: Views/BoardView.swift:404
            ) {
                T("반만 맞아도 괜찮은 이유", style = appFont(13f, FontWeight.SemiBold), color = Theme.forecast)   // ios: Views/BoardView.swift:405
                Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:406
                Sym(if (whyOpen) "chevron.up" else "chevron.down", 12f, Theme.forecast)   // ios: Views/BoardView.swift:407
            }
            // ios: Views/BoardView.swift:412
            AnimatedVisibility(whyOpen) {
                T("범위는 2번 중 1번 맞게 잡아요.\n넓으면 쓸모없고, 좁으면 자주 틀려요.",   // ios: Views/BoardView.swift:413
                  style = appFont(13f).ls(2f), color = Theme.ink,   // ios: Views/BoardView.swift:414
                  modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
                      .background(Theme.forecastBg, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:416
                      .padding(12.dp))   // ios: Views/BoardView.swift:415
            }
        }
    }
}

// ios: Views/BoardView.swift:421
@Composable
private fun Legend(c: Color, stroke: Color?, t: String) {
    // ios: Views/BoardView.swift:422
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp, 14.dp)   // ios: Views/BoardView.swift:425
                .background(c, RoundedCornerShape(4.dp))   // ios: Views/BoardView.swift:423
                .border(2.dp, stroke ?: c, RoundedCornerShape(4.dp)),   // ios: Views/BoardView.swift:424
        )
        T(t, style = appFont(12f), color = Theme.sub)   // ios: Views/BoardView.swift:426
    }
}
