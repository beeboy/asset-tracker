package com.naeilo.app.ui.board

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

// 미션 판 (회복 루트): 플러스 루트와 같은 모양. 인터미션 카드, 접힌 시작 미션, 초록 다음 미션 카드, 1000칸 (아래 블록·파도 게임 버튼)
// ios: Views/BoardView.swift:4
@Composable
fun BoardView(
    m: AppModel,   // ios: Views/BoardView.swift:5
) {
    var openDone by remember { mutableStateOf(false) }   // ios: Views/BoardView.swift:6
    var cellMode by remember { mutableStateOf("mine") }   // ios: Views/BoardView.swift:7
    // ios: Views/BoardView.swift:9,10,26,27 (이동 막대 숨김: NavStack 은 첫 화면에 막대를 그리지 않는다)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/BoardView.swift:24
        Screen {
            // ios: Views/BoardView.swift:11
            Column(Modifier.animateContentSize(easeSpring), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.outset(16.dp)) { AppHeader(tab = "미션") }   // ios: Views/BoardView.swift:12 (-16)
                SampleNote(m)   // ios: Views/BoardView.swift:13
                // 플러스 루트처럼: 맨 위 인터미션 카드, 접힌 시작 미션 목록, 초록 다음 미션 카드
                m.blocks.firstOrNull { it.inter }?.let { inter -> InterCard(m, inter) }   // ios: Views/BoardView.swift:15
                if (!m.interDone && m.playOn) CheckIn(m)   // ios: Views/BoardView.swift:16
                val starts = m.blocks.filter { !it.inter }   // ios: Views/BoardView.swift:17
                StartList(m, starts, openDone) { openDone = !openDone }   // ios: Views/BoardView.swift:18
                // ios: Views/BoardView.swift:19
                m.blocks.firstOrNull { !m.done.contains(it.id) && m.available(it) }?.takeIf { !it.inter }?.let { next ->
                    NextCard(m, next, steps = starts)   // ios: Views/BoardView.swift:20
                }
                if (m.playUnlocked) Cells(m, cellMode) { cellMode = it } else LockedPlay()   // ios: Views/BoardView.swift:22
            }
        }
    }
}

/** 미션 탭에서 여는 화면 (navigationDestination for: MissionRoute). App 의 미션 탭 NavStack 이 부른다 */
// ios: Views/BoardView.swift:28
fun boardPage(m: AppModel, r: MissionRoute): NavPage = missionPage(m, r)   // MissionScreen(m, route = r) 를 제목·막대와 함께

// ios: Views/BoardView.swift:31
private fun route(b: AppModel.Block): MissionRoute? =
    when (b.id) { 1 -> MissionRoute.m1; 2 -> MissionRoute.m2; 3 -> MissionRoute.m3; 5 -> MissionRoute.nx; else -> null }   // ios: Views/BoardView.swift:32

/** 다음 미션: 진한 초록 블록 (플러스 루트와 같은 모양) */
// ios: Views/BoardView.swift:36
@Composable
private fun NextCard(m: AppModel, b: AppModel.Block, steps: List<AppModel.Block>) {
    val idx = max(0, steps.indexOfFirst { it.id == b.id }) + 1   // ios: Views/BoardView.swift:37
    // ios: Views/BoardView.swift:48
    Fg(Color.White) {
        // ios: Views/BoardView.swift:38
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
                .background(Theme.missionGreen, RoundedCornerShape(20.dp))   // ios: Views/BoardView.swift:50
                .padding(20.dp),   // ios: Views/BoardView.swift:49
        ) {
            T("다음 미션 · $idx/${steps.size}", style = appFont(13f, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.8f))   // ios: Views/BoardView.swift:39
            T(b.title, style = appFont(20f, FontWeight.Bold))   // ios: Views/BoardView.swift:40
            T("열리는 것: ${b.reward}", style = appFont(14f), color = Color.White.copy(alpha = 0.85f))   // ios: Views/BoardView.swift:41
            Box(Modifier.padding(top = 4.dp)) {   // ios: Views/BoardView.swift:46 (buttonStyle plain = plainClickable)
                // ios: Views/BoardView.swift:42
                Box(
                    Modifier.plainClickable { route(b)?.let { r -> m.boardPath = m.boardPath + r } }
                        .fillMaxWidth().defaultMinSize(minHeight = 48.dp)   // ios: Views/BoardView.swift:44
                        .background(Color.White, RoundedCornerShape(14.dp)),   // ios: Views/BoardView.swift:45
                    contentAlignment = Alignment.Center,
                ) {
                    T("시작", style = appFont(16f, FontWeight.Bold), color = Theme.missionGreen)   // ios: Views/BoardView.swift:43
                }
            }
        }
    }
}

/** 처음 4주 체크인 카드: 주마다 비중을 지켰는지와 만난 친구 */
// ios: Views/BoardView.swift:54
@Composable
private fun InterCard(m: AppModel, b: AppModel.Block) {
    val done = m.done.contains(b.id); val avail = m.available(b); val n = m.weeks.size   // ios: Views/BoardView.swift:55
    val lit = done || avail
    // ios: Views/BoardView.swift:83
    Fg(if (lit) Color.White else Theme.sub2) {
        val fg = LocalFg.current(LocalPal.current)
        // ios: Views/BoardView.swift:56
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
                .background(if (lit) Theme.night else Theme.track, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:85
                .padding(16.dp)   // ios: Views/BoardView.swift:84
                .semantics(mergeDescendants = true) {}   // ios: Views/BoardView.swift:86
                .semantics { contentDescription = "처음 4주 체크인, $n/4주" },   // ios: Views/BoardView.swift:87
        ) {
            // ios: Views/BoardView.swift:57
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("처음 4주 체크인", style = appFont(12f, FontWeight.Bold), color = Theme.mint)   // ios: Views/BoardView.swift:58
                Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:59
                if (done) T("완료 ✓", style = appFont(12f, FontWeight.Bold), color = Theme.yellow)   // ios: Views/BoardView.swift:60
                else if (!avail) Sym("lock", 12f, fg)   // ios: Views/BoardView.swift:61
            }
            T(
                if (done || n >= 4) "4주 체크인 끝"   // ios: Views/BoardView.swift:63
                else if (avail) "주 1회 비중 체크 · $n/4주"   // ios: Views/BoardView.swift:64
                else "시작 미션 뒤 열림",   // ios: Views/BoardView.swift:65
                style = appFont(16f, FontWeight.Bold),   // ios: Views/BoardView.swift:66
            )
            // ios: Views/BoardView.swift:67
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/BoardView.swift:68
                for (i in 0 until 4) {
                    val pick: WeekPick? = if (avail || done) (if (i < n) m.weeks[i] else null) else null   // ios: Views/BoardView.swift:69
                    val cur = avail && !done && i == n   // ios: Views/BoardView.swift:70
                    val edge = pick?.color ?: (if (cur) Theme.yellow else Theme.slate)
                    // ios: Views/BoardView.swift:71
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 60.dp)   // ios: Views/BoardView.swift:75
                            .background(pick?.color ?: Color.Transparent, RoundedCornerShape(10.dp))   // ios: Views/BoardView.swift:77
                            // ios: Views/BoardView.swift:78 테두리 선은 가장자리 가운데에 (SwiftUI stroke), 10 모서리
                            .drawBehind {
                                drawRoundRect(edge, cornerRadius = CornerRadius(10.dp.toPx()),
                                    // ios: Views/BoardView.swift:79
                                    style = Stroke(width = 2.dp.toPx(), pathEffect = if (pick == null) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null))
                            },
                    ) {
                        val c = if (pick != null) Color.White else if (cur) Theme.yellow else Theme.muted   // ios: Views/BoardView.swift:76
                        T("${i + 1}주째", style = appFont(11f), color = c)   // ios: Views/BoardView.swift:72
                        T(pick?.label ?: (if (cur) "이번 주" else "–"), style = appFont(13f, FontWeight.Bold), color = c)   // ios: Views/BoardView.swift:73
                    }
                }
            }
        }
    }
}

/** 시작 미션: 접힌 한 줄, 펼치면 번호 목록. 끝냈거나 열린 미션은 눌러서 들어간다 */
// ios: Views/BoardView.swift:91
@Composable
private fun StartList(m: AppModel, list: List<AppModel.Block>, openDone: Boolean, toggle: () -> Unit) {
    val done = list.count { m.done.contains(it.id) }; val all = done == list.size   // ios: Views/BoardView.swift:92
    // ios: Views/BoardView.swift:93,94,102,123,124 withAnimation: 펼치면 목록 높이가 기본 스프링으로 자란다 (animateContentSize). 버튼(plain) = 바로 아래 머리 줄
    Column(Modifier.fillMaxWidth().background(if (all) Theme.tealBg else Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)).animateContentSize(easeSpring), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            Modifier.fillMaxWidth().plainClickable(toggle)
                .defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp),   // ios: Views/BoardView.swift:101
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/BoardView.swift:95
        ) {
            val fg = if (all) Theme.tealDark else Theme.ink; val st = appFont(14f, FontWeight.SemiBold)   // ios: Views/BoardView.swift:100
            T(if (all) "✓ 시작 미션 $done/${list.size}" else "시작 미션 $done/${list.size}", Modifier.weight(1f), style = st.copy(fontWeight = FontWeight.Bold), color = fg)   // ios: Views/BoardView.swift:96
            Spacer(Modifier.width(0.dp))   // ios: Views/BoardView.swift:97 (minLength 0: 글이 weight 로 남은 폭을 쓴다)
            Sym(if (openDone) "chevron.up" else "chevron.down", 14f, fg)   // ios: Views/BoardView.swift:98
        }
        // ios: Views/BoardView.swift:103
        if (openDone) {
            // ios: Views/BoardView.swift:104
            list.forEach { b ->
                val ok = m.done.contains(b.id); val avail = m.available(b); val r = route(b)   // ios: Views/BoardView.swift:105
                HDivider(Theme.border)   // ios: Views/BoardView.swift:106
                val sym = 13f; val tap = r != null && (ok || avail)   // ios: Views/BoardView.swift:107,116
                Row(
                    Modifier.fillMaxWidth().then(r?.takeIf { tap }?.let { rr -> Modifier.plainClickable { m.boardPath = m.boardPath + rr } } ?: Modifier)
                        .defaultMinSize(minHeight = 44.dp).padding(horizontal = 14.dp)
                        .semantics(mergeDescendants = true) { if (!tap) disabled() }   // ios: Views/BoardView.swift:118
                        .semantics { contentDescription = "시작 미션 ${b.num}: ${b.title}" + (if (ok) ", 끝냄" else "") },   // ios: Views/BoardView.swift:119
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/BoardView.swift:108
                ) {
                    T(b.num, Modifier.width(18.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BoardView.swift:109
                    T(b.title, Modifier.weight(1f), style = appFont(14f), color = if (ok || avail) Theme.ink else Theme.sub2)   // ios: Views/BoardView.swift:110
                    Spacer(Modifier.width(0.dp))   // ios: Views/BoardView.swift:111 (minLength 0)
                    if (ok) Sym("checkmark", sym, Theme.teal)   // ios: Views/BoardView.swift:112
                    else if (!avail) Sym("lock", sym, Theme.sub2)   // ios: Views/BoardView.swift:113
                    else Sym("chevron.right", sym, Theme.sub)   // ios: Views/BoardView.swift:114
                }
            }
        }
    }
}

// 인터미션 체크인: 이번 주 비중을 지켰는지 고르면 블록이 채워지고 친구가 하나 온다
// ios: Views/BoardView.swift:128,129 (@Bindable: AppModel 값은 mutableState 라 그대로 바꾼다)
@Composable
private fun CheckIn(m: AppModel) {
    // 다음 주차가 열리기 전에는 방금 한 주차를 다시 고를 수 있다
    // ios: Views/BoardView.swift:131
    val edit = m.weekEditable; val i = if (edit) m.weeks.size - 1 else m.weeks.size; val step = Shelter.weekSteps[min(i, 3)]
    // ios: Views/BoardView.swift:132
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
            .background(Theme.card, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:172
            .border(1.dp, Theme.border, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:173
            .animateContentSize(easeSpring)
            .padding(16.dp),   // ios: Views/BoardView.swift:171
    ) {
        T("${i + 1}주차 체크인", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/BoardView.swift:133
        // ios: Views/BoardView.swift:134
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(step.whereText, style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed,   // ios: Views/BoardView.swift:135
              modifier = Modifier.background(Theme.mint, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp))   // ios: Views/BoardView.swift:136
            T(step.task, style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)   // ios: Views/BoardView.swift:137
        }
        T("이번 주 ${m.focusName} 비중은?", style = appFont(14f), color = Theme.ink)   // ios: Views/BoardView.swift:139
        // ios: Views/BoardView.swift:140
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/BoardView.swift:141
            WeekPick.entries.forEach { p ->
                val on = if (edit) m.weeks[i] == p else m.weekCur == p   // ios: Views/BoardView.swift:142
                // ios: Views/BoardView.swift:144
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                        .plainClickable { if (edit) m.editLastWeek(p) else m.weekCur = p }   // ios: Views/BoardView.swift:143,154 (buttonStyle plain)
                        .defaultMinSize(minHeight = 64.dp)   // ios: Views/BoardView.swift:150
                        .background(Theme.card, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:152
                        .background(if (on) p.color.copy(alpha = 0.15f) else Theme.card, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:151
                        .border(if (on) 3.dp else 2.dp, p.color, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:153
                        .padding(vertical = 8.dp),   // ios: Views/BoardView.swift:149
                ) {
                    T(p.label, style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/BoardView.swift:145,148
                    T(p.sub, style = appFont(11f), color = Theme.sub)   // ios: Views/BoardView.swift:146
                }
            }
        }
        // 주차는 시작한 날부터 7일마다 열린다 (서버 시각). 열리기 전에는 남은 날을 보여 준다
        if (edit) T("다음 주차 열리기 전까지 수정 가능", style = appFont(12f), color = Theme.muted)   // ios: Views/BoardView.swift:158
        val wait = m.weekGateText
        // ios: Views/BoardView.swift:159
        if (wait != null) {
            // ios: Views/BoardView.swift:160,161
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sym("clock", 13f, Theme.sub)
                T(wait, Modifier.weight(1f), style = appFont(13f), color = Theme.sub)
            }
        } else if (m.weekCur != null) {   // ios: Views/BoardView.swift:162
            // ios: Views/BoardView.swift:163,168 withAnimation (buttonStyle plain): 체크인하면 카드가 바뀌는 높이는 이 버튼과 위 카드의 animateContentSize 가 스프링으로 맞춘다
            Box(
                Modifier.fillMaxWidth().animateContentSize(easeSpring).plainClickable { m.confirmWeek() }
                    .defaultMinSize(minHeight = 44.dp)   // ios: Views/BoardView.swift:165
                    .background(Theme.teal, RoundedCornerShape(10.dp)),   // ios: Views/BoardView.swift:167
                contentAlignment = Alignment.Center,
            ) {
                T("체크인", style = appFont(14f, FontWeight.SemiBold), color = Color.White)   // ios: Views/BoardView.swift:164,166
            }
        }
    }
}

// ios: Views/BoardView.swift:176
@Composable
private fun LockedPlay() {
    // ios: Views/BoardView.swift:177
    DashedCard {
        val fg = LocalFg.current(LocalPal.current)
        // ios: Views/BoardView.swift:178
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Sym("lock", 15f, fg); T("100칸 블록", style = appFont(15f, FontWeight.Bold), color = fg) }
        T("시작 3단계 뒤 열림 · 칸 채우기·블록·파도", style = appFont(13f))   // ios: Views/BoardView.swift:179
    }
}

// 1000칸: 본전 = 1000칸. 진한 칸 = 시작 뒤 바닥에도 있던 칸, 연한 칸 = 바닥 뒤 회복한 칸
// ios: Views/BoardView.swift:184
@Composable
private fun Cells(m: AppModel, cellMode: String, setMode: (String) -> Unit) {
    // ios: Views/BoardView.swift:185
    val n = m.cellsNow; val nFloor = min(n, m.cellsFloor); val nY = m.cellsYesterday; val chg = n - nY
    val nTxt = cformat("%,d", n)
    val unit = AppModel.man(m.cellUnit)   // ios: Views/BoardView.swift:186
    val parts = stockParts(m)
    // ios: Views/BoardView.swift:187
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
            .background(Theme.card, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:219
            .border(2.dp, Theme.ink, RoundedCornerShape(18.dp))   // ios: Views/BoardView.swift:220
            .padding(horizontal = 14.dp, vertical = 12.dp),   // ios: Views/BoardView.swift:218
    ) {
        // ios: Views/BoardView.swift:188
        Row(Modifier.fillMaxWidth()) {
            T("본전까지 1000칸", Modifier.alignByBaseline(), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BoardView.swift:189
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:190
            T("1칸 = $unit", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.sub)   // ios: Views/BoardView.swift:191
        }
        // ios: Views/BoardView.swift:193
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("${nTxt}칸", Modifier.alignByBaseline(), style = appFont(26f, FontWeight.Bold), roll = "${nTxt}칸")   // ios: Views/BoardView.swift:194
            T("/ 1,000칸", Modifier.alignByBaseline(), style = appFont(14f), color = Theme.sub)   // ios: Views/BoardView.swift:195

            T(if (chg == 0) "어제와 같음" else "어제보다 ${if (chg > 0) "+" else ""}${chg}칸", Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold), color = if (chg == 0) Theme.sub else Theme.change(chg.toDouble()))   // Views/BoardView.swift:196 (검사 예외: 같은 글)
        }
        ChipRow(listOf("mine" to "바닥 / 회복", "stock" to "종목별", "chg" to "오늘 바뀐 칸"), selection = cellMode, onSelect = setMode)   // ios: Views/BoardView.swift:198
        CellGrid(filled = n, floor = nFloor, yesterday = nY, mode = cellMode, parts = parts,   // ios: Views/BoardView.swift:199
                 modifier = Modifier.fillMaxWidth().aspectRatio(320.0f / 200.0f)   // ios: Views/BoardView.swift:200
                     .semantics { contentDescription = "1000칸 중 ${n}칸" })   // ios: Views/BoardView.swift:201
        // ios: Views/BoardView.swift:202
        FlowRow(spacing = 10.dp) {
            // ios: Views/BoardView.swift:203
            if (cellMode == "stock") {
                parts.forEach { (k, c, cnt) -> Legend(c, "$k ${cnt}칸") }   // ios: Views/BoardView.swift:204
            } else {   // ios: Views/BoardView.swift:205
                Legend(Theme.teal, "바닥 ${nFloor}칸")   // ios: Views/BoardView.swift:206
                Legend(hexD(0x7FD3C9, 0x3C8F86), "회복 ${n - nFloor}칸")   // ios: Views/BoardView.swift:207
                Legend(Theme.yellow, "마지막 칸 = 본전")   // ios: Views/BoardView.swift:208
            }
        }
        // 공유 카드와 게임 둘(블록·파도)을 한 줄에
        // ios: Views/BoardView.swift:212
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ShareCardButton(m, title = "공유 카드") }   // ios: Views/BoardView.swift:213
            Box(Modifier.weight(1f)) { BrickGameButton(m, filled = n, floor = nFloor, yesterday = nY, title = "블록", icon = "square.grid.3x3.fill", labelStyle = TightLabel) }   // ios: Views/BoardView.swift:214
            Box(Modifier.weight(1f)) { WaveGameButton(labelStyle = TightLabel) }   // ios: Views/BoardView.swift:215
        }
    }
}

// ios: Views/BoardView.swift:223
private fun stockParts(m: AppModel): List<Triple<String, Color, Int>> {
    var acc = 0.0   // ios: Views/BoardView.swift:224
    // ios: Views/BoardView.swift:225
    return m.rows.sortedByDescending { it.value }.map { r ->
        val a = m.cells(acc); val b = m.cells(acc + r.value); acc += r.value   // ios: Views/BoardView.swift:226
        Triple(r.id, r.color, b - a)   // ios: Views/BoardView.swift:227
    }
}

// ios: Views/BoardView.swift:231
@Composable
private fun Legend(c: Color, t: String) {
    // ios: Views/BoardView.swift:232,233
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp, 12.dp).background(c, RoundedCornerShape(3.dp))); T(t, style = appFont(12f), color = Theme.sub)
    }
}

// ios: Views/BoardView.swift:238
object WeekForecastCard {
    // ios: Views/BoardView.swift:242
    val names: Map<String, String> = mapOf("lo" to "더 낮게", "in" to "예보대로", "hi" to "더 높게")

    /** 범위 숫자: 억 단위 소수 한 자리 (lo·hi가 같게 반올림되면 두 자리), 1억 아래는 만원 단위 */
    // ios: Views/BoardView.swift:245
    fun eok(v: Double, d: Int): String {
        if (AppModel.en) return AppModel.krwC(v)   // ios: Views/BoardView.swift:246
        return if (v < 1e8) AppModel.man((v / 1e5).swRound() * 1e5) else cformat("%.${d}f억", v / 1e8)   // ios: Views/BoardView.swift:247
    }
}

// 이번 주 예보: 그 주 처음 연 날에 금요일 평가액 범위를 적어 두고, 금요일 종가로 판정한다.
// 예보 · 맞혀보기 · 예보 성적 세 카드로 나눈다 (예보는 앱의 것, 맞혀보기는 사용자의 것).
// ios: Views/BoardView.swift:252 WeekForecastCard 본문
@Composable
fun WeekForecastCard(
    m: AppModel,   // ios: Views/BoardView.swift:239
) {
    var whyOpen by remember { mutableStateOf(false) }   // ios: Views/BoardView.swift:240
    val r = m.weekRange   // ios: Views/BoardView.swift:253
    val span = r.hi - r.lo; val x0 = r.lo - span * 0.6; val x1 = r.hi + span * 0.6   // ios: Views/BoardView.swift:254
    val px = { v: Double -> max(0.0, min(1.0, (v - x0) / (x1 - x0))) }   // ios: Views/BoardView.swift:255
    val inside = r.actual >= r.lo && r.actual <= r.hi   // ios: Views/BoardView.swift:256
    val pos = if (r.actual < r.lo) "lo" else if (r.actual > r.hi) "hi" else "in"   // ios: Views/BoardView.swift:257
    val d = if (WeekForecastCard.eok(r.lo, 1) == WeekForecastCard.eok(r.hi, 1)) 2 else 1   // ios: Views/BoardView.swift:258
    val lo = WeekForecastCard.eok(r.lo, d); val hi = WeekForecastCard.eok(r.hi, d)   // ios: Views/BoardView.swift:259
    // 개발자 넘기기는 기록에 없으니 이번 주 결과를 붙여 본다
    val stamps = if (m.weekFridaySkip) (m.pastWeeks + (if (inside) 1 else 0)).takeLast(8) else m.pastWeeks   // ios: Views/BoardView.swift:261
    // ios: Views/BoardView.swift:262
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ForecastCard(m, r, px, inside, lo, hi)   // ios: Views/BoardView.swift:263
        GuessCard(m, pos, inside, lo, hi)   // ios: Views/BoardView.swift:264
        RecordCard(m, stamps, whyOpen) { whyOpen = !whyOpen }   // ios: Views/BoardView.swift:265
    }
    LaunchedEffect(Unit) { Remind.shared.recordWeek(m) }   // ios: Views/BoardView.swift:267
}

// 1. 예보
// ios: Views/BoardView.swift:271
@Composable
private fun ForecastCard(m: AppModel, r: AppModel.WeekRange, px: (Double) -> Double, inside: Boolean, lo: String, hi: String) {
    val track = Theme.track; val blue = Theme.blue; val card = Theme.card
    val dot = if (m.weekFriday) (if (inside) Theme.green else Theme.orange) else Theme.ink
    // ios: Views/BoardView.swift:272
    Card(Modifier.semantics(mergeDescendants = true) {}) {   // ios: Views/BoardView.swift:297
        // ios: Views/BoardView.swift:273
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym("sun.max.fill", 13f, Theme.forecast); T("${m.weekFridayText} 금 마감 예보", style = appFont(13f, FontWeight.Bold), color = Theme.forecast)
        }
        FitText("$lo ~ $hi", appFont(28f, FontWeight.Bold), 0.7f, maxLines = 1, roll = "$lo ~ $hi")   // ios: Views/BoardView.swift:274
        // ios: Views/BoardView.swift:275,276 막대 하나는 Canvas 로 (크기가 바뀔 때만 다시 잰다)
        Spacer(
            Modifier.fillMaxWidth().height(26.dp)   // ios: Views/BoardView.swift:285
                .drawWithCache {
                    val w = size.width
                    val cy = size.height / 2   // ios: Views/BoardView.swift:283 (가운데)
                    val h = 12.dp.toPx()   // ios: Views/BoardView.swift:277
                    val band = blue.copy(alpha = 0.55f); val bh = 12.dp.toPx(); val bx = (px(r.lo) * w).toFloat(); val bw = ((px(r.hi) - px(r.lo)) * w).toFloat()   // ios: Views/BoardView.swift:278
                    val dot18 = Size(18.dp.toPx(), 18.dp.toPx()); val dx = (px(r.actual) * w).toFloat() - 9.dp.toPx()   // ios: Views/BoardView.swift:281
                    onDrawBehind {
                        drawRoundRect(track, Offset(0f, cy - h / 2), Size(w, h), CornerRadius(h / 2))
                        drawRoundRect(band, Offset(bx, cy - bh / 2), Size(bw, bh), CornerRadius(bh / 2))
                        drawOval(dot, Offset(dx, cy - dot18.height / 2), dot18)   // ios: Views/BoardView.swift:279
                        drawOval(card, Offset(dx, cy - dot18.height / 2), dot18, style = Stroke(3.dp.toPx()))   // ios: Views/BoardView.swift:280
                    }
                },
        )
        // ios: Views/BoardView.swift:286
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val st = appFont(12f)   // ios: Views/BoardView.swift:291
            T(lo, style = st, color = Theme.sub); Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:287
            T((if (m.weekFriday) "금 " else "지금 ") + WeekForecastCard.eok(r.actual, 2), style = st.copy(fontWeight = FontWeight.Bold), color = Theme.sub); Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:288
            T(hi, style = st, color = Theme.sub)   // ios: Views/BoardView.swift:289
        }
        // ios: Views/BoardView.swift:292
        TA(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("2주 중 1주") }; append("는 이 범위 안") },
           style = appFont(13f).ls(2f), color = Theme.ink,   // ios: Views/BoardView.swift:293
           modifier = Modifier.fillMaxWidth()
               .background(Theme.forecastBg, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:295
               .padding(12.dp))   // ios: Views/BoardView.swift:294
    }
}

// 2. 맞혀보기
// ios: Views/BoardView.swift:301
@Composable
private fun GuessCard(m: AppModel, pos: String, inside: Boolean, lo: String, hi: String) {
    // ios: Views/BoardView.swift:302
    Card(Modifier.animateContentSize(easeSpring)) {
        // ios: Views/BoardView.swift:303
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("맞혀보기", style = appFont(12f, FontWeight.Bold), color = Theme.brown)   // ios: Views/BoardView.swift:304
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:305
            T("결과는 토요일", style = appFont(12f), color = Theme.sub)   // ios: Views/BoardView.swift:306
        }
        T("금요일엔?", style = appFont(17f, FontWeight.Bold))   // ios: Views/BoardView.swift:308
        // ios: Views/BoardView.swift:309
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/BoardView.swift:310
            listOf("lo" to "$lo ↓", "in" to "범위 안", "hi" to "$hi ↑").forEach { o ->
                val on = m.weekGuess == o.first   // ios: Views/BoardView.swift:311
                val fg = if (on) Color.White else Theme.ink   // ios: Views/BoardView.swift:318
                // ios: Views/BoardView.swift:313
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                        // ios: Views/BoardView.swift:312,322 (buttonStyle plain)
                        .then(if (!m.weekFriday) Modifier.plainClickable { m.weekGuess = o.first; Remind.shared.recordWeek(m) } else Modifier)
                        .semantics { if (m.weekFriday) disabled() }   // ios: Views/BoardView.swift:323
                        .semantics { selected = on }   // ios: Views/BoardView.swift:324
                        .defaultMinSize(minHeight = 64.dp)   // ios: Views/BoardView.swift:317
                        .background(if (on) Theme.forecast else Theme.card, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:319
                        .border(2.dp, if (on) Theme.forecast else Theme.border, RoundedCornerShape(12.dp)),   // ios: Views/BoardView.swift:320
                ) {
                    T(WeekForecastCard.names[o.first] ?: "", style = appFont(15f, FontWeight.Bold), color = fg)   // ios: Views/BoardView.swift:314
                    FitText(o.second, appFont(11f), 0.7f, maxLines = 1, color = fg.copy(alpha = 0.85f))   // ios: Views/BoardView.swift:315
                }
            }
        }
        // ios: Views/BoardView.swift:327
        if (m.weekFriday) {

            val out = if (pos == "in") "범위 안" else "범위보다 ${if (pos == "hi") "높게" else "낮게"}"   // Views/BoardView.swift:328 (검사 예외: 같은 글)
            T(text = (m.weekGuess?.let { if (it == pos) "맞혔어요! " else "아쉬워요. 고른 답 ‘${WeekForecastCard.names[it] ?: ""}’ · " } ?: "") + out,   // Views/BoardView.swift:329 (검사 예외: 같은 글)
              style = appFont(14f, FontWeight.SemiBold),   // ios: Views/BoardView.swift:330
              modifier = Modifier.fillMaxWidth()
                  .background(if (inside) hexD(0xE3F4EC, 0x163226) else hexD(0xFDF0E1, 0x3A2A17), RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:332
                  .padding(10.dp))   // ios: Views/BoardView.swift:331
        } else {   // ios: Views/BoardView.swift:333

            m.weekGuess?.let { g -> T("‘${WeekForecastCard.names[g] ?: ""}’ 고름 · 금요일 마감 뒤 확인", style = appFont(13f, FontWeight.SemiBold), color = Theme.forecast) }   // Views/BoardView.swift:334 (검사 예외: 같은 글)
            // ios: Views/BoardView.swift:335
            if (m.devAll) {
                val und = appFont(13f).copy(textDecoration = TextDecoration.Underline); val minH = Modifier.defaultMinSize(minHeight = 40.dp)   // ios: Views/BoardView.swift:337
                // ios: Views/BoardView.swift:336 withAnimation: 결과 칸으로 바뀌는 높이는 이 줄과 카드의 animateContentSize 가 스프링으로 맞춘다
                Box(Modifier.animateContentSize(easeSpring).plainClickable { m.weekFridaySkip = true }.then(minH), contentAlignment = Alignment.CenterStart) {
                    T("금요일로 넘기기 (개발자)", style = und, color = Theme.sub)
                }
            }
        }
    }
}

// 3. 예보 성적 + 배지
// ios: Views/BoardView.swift:344
@Composable
private fun RecordCard(m: AppModel, stamps: List<Int>, whyOpen: Boolean, toggleWhy: () -> Unit) {
    // ios: Views/BoardView.swift:345
    Card {
        // ios: Views/BoardView.swift:346
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(if (stamps.isEmpty()) "예보 성적" else "지난 ${stamps.size}주", style = appFont(15f, FontWeight.Bold))   // ios: Views/BoardView.swift:347
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:348
            if (stamps.isNotEmpty()) T("${stamps.sum()}/${stamps.size} 범위 안", style = appFont(13f), color = Theme.sub)   // ios: Views/BoardView.swift:349
        }
        // ios: Views/BoardView.swift:351
        if (stamps.isEmpty()) {
            T("첫 주 끝나면 쌓여요", style = appFont(13f), color = Theme.sub)   // ios: Views/BoardView.swift:352
        } else {   // ios: Views/BoardView.swift:353
            // ios: Views/BoardView.swift:354
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = "${stamps.size}주 중 ${stamps.sum()}주 범위 안" },   // ios: Views/BoardView.swift:368,369
            ) {
                // ios: Views/BoardView.swift:355
                stamps.indices.forEach { i ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/BoardView.swift:356,365
                        val shape = RoundedCornerShape(8.dp)   // ios: Views/BoardView.swift:357
                        Box(
                            Modifier.fillMaxWidth().height(32.dp)   // ios: Views/BoardView.swift:361
                                .background(if (stamps[i] == 1) Theme.forecast else Theme.card, shape)   // ios: Views/BoardView.swift:358
                                .border(2.dp, if (stamps[i] == 1) Theme.forecast else Theme.dash, RoundedCornerShape(8.dp)),   // ios: Views/BoardView.swift:359
                            contentAlignment = Alignment.Center,
                        ) {
                            if (stamps[i] == 1) Sym("checkmark", 13f, Color.White)   // ios: Views/BoardView.swift:360
                        }
                        // ios: Views/BoardView.swift:362
                        FitText(if (i == stamps.size - 1) "지난주" else "${stamps.size - i}주 전", appFont(10f), color = Theme.muted,
                                minScale = 0.7f, maxLines = 1)   // ios: Views/BoardView.swift:363
                    }
                }
            }
            // ios: Views/BoardView.swift:370
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Theme.forecast, null, "범위 안")   // ios: Views/BoardView.swift:371
                Legend(Theme.card, Theme.dash, "범위 밖")   // ios: Views/BoardView.swift:372
            }
        }
        HDivider(Theme.border)   // ios: Views/BoardView.swift:375
        // ios: Views/BoardView.swift:376
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("배지", style = appFont(14f, FontWeight.Bold))   // ios: Views/BoardView.swift:377
            Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:378
            T("${Remind.badges.count { m.guessHits >= it.n }}개", style = appFont(13f), color = Theme.sub)   // ios: Views/BoardView.swift:379
        }
        // ios: Views/BoardView.swift:381
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/BoardView.swift:382
            Remind.badges.forEach { b ->
                val got = m.guessHits >= b.n   // ios: Views/BoardView.swift:383
                // ios: Views/BoardView.swift:384
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)   // ios: Views/BoardView.swift:391
                        .semantics(mergeDescendants = true) {}   // ios: Views/BoardView.swift:392

                        .semantics { contentDescription = "${b.name} 배지, ${if (got) "받음" else "${b.n}번 맞히면 받아요"}" },   // Views/BoardView.swift:393 (검사 예외: 같은 글)
                ) {
                    Box(
                        Modifier.size(40.dp, 40.dp)   // ios: Views/BoardView.swift:387
                            .background(if (got) Theme.gold else Theme.track, CircleShape),   // ios: Views/BoardView.swift:388
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(b.icon, 16f, if (got) Theme.inkFixed else Theme.muted)   // ios: Views/BoardView.swift:385,386 (16 bold)
                    }
                    FitText(if (b.n >= 20) "달인" else "${b.n}회", appFont(10f), 0.7f, maxLines = 1, color = if (got) Theme.ink else Theme.muted)   // ios: Views/BoardView.swift:389
                }
            }
        }
        // ios: Views/BoardView.swift:396,404 withAnimation (buttonStyle plain): 펼치면 아래 글이 AnimatedVisibility 로 나타난다 (버튼 = 바로 아래 줄)
        Column(Modifier.fillMaxWidth().animateContentSize(easeSpring)) {
            Row(Modifier.fillMaxWidth().plainClickable(toggleWhy)
                .defaultMinSize(minHeight = 40.dp),   // ios: Views/BoardView.swift:402
                verticalAlignment = Alignment.CenterVertically,   // ios: Views/BoardView.swift:397
            ) {
                T("반만 맞아도 괜찮은 이유", style = appFont(13f, FontWeight.SemiBold), color = Theme.forecast)   // ios: Views/BoardView.swift:398
                Spacer(Modifier.weight(1f))   // ios: Views/BoardView.swift:399
                Sym(if (whyOpen) "chevron.up" else "chevron.down", 12f, Theme.forecast)   // ios: Views/BoardView.swift:400
            }
            // ios: Views/BoardView.swift:405
            AnimatedVisibility(whyOpen) {
                T("범위는 2번 중 1번 맞게 잡아요.\n넓으면 쓸모없고, 좁으면 자주 틀려요.",   // ios: Views/BoardView.swift:406
                  style = appFont(13f).ls(2f), color = Theme.ink,   // ios: Views/BoardView.swift:407
                  modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
                      .background(Theme.forecastBg, RoundedCornerShape(12.dp))   // ios: Views/BoardView.swift:409
                      .padding(12.dp))   // ios: Views/BoardView.swift:408
            }
        }
    }
}

// ios: Views/BoardView.swift:414
@Composable
private fun Legend(c: Color, stroke: Color?, t: String) {
    // ios: Views/BoardView.swift:415
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp, 14.dp)   // ios: Views/BoardView.swift:418
                .background(c, RoundedCornerShape(4.dp))   // ios: Views/BoardView.swift:416
                .border(2.dp, stroke ?: c, RoundedCornerShape(4.dp)),   // ios: Views/BoardView.swift:417
        )
        T(t, style = appFont(12f), color = Theme.sub)   // ios: Views/BoardView.swift:419
    }
}

// 40×25 칸, 왼쪽 아래부터 채운다. 마지막 칸(본전)은 별.
// 1000칸을 한 Canvas 에 그린다: 칸 자리와 색은 크기·값이 바뀔 때만 다시 계산한다 (drawWithCache)
// ios: Views/BoardView.swift:425
@Composable
fun CellGrid(
    filled: Int,   // ios: Views/BoardView.swift:426
    floor: Int,   // ios: Views/BoardView.swift:427
    yesterday: Int,   // ios: Views/BoardView.swift:428
    mode: String,   // ios: Views/BoardView.swift:429
    parts: List<Triple<String, Color, Int>>,   // ios: Views/BoardView.swift:430
    modifier: Modifier = Modifier,
) {
    val track = Theme.track; val muted = Theme.muted; val teal = Theme.teal; val green = Theme.green
    val dark = LocalPal.current.isDark
    val up = Theme.up; val down = Theme.down; val yellow = Theme.yellow
    // ios: Views/BoardView.swift:432,433
    Spacer(
        modifier.drawWithCache {
            val s = size.width / 40   // ios: Views/BoardView.swift:434
            // ios: Views/BoardView.swift:435
            fun rect(i: Int): Rect =
                Rect(Offset((i % 40) * s + 0.5f, (24 - i / 40) * s + 0.5f), Size(s - 1, s - 1))   // ios: Views/BoardView.swift:436
            val stockColor = ArrayList<Color>()   // ios: Views/BoardView.swift:438
            if (mode == "stock") for ((_, c, n) in parts) repeat(maxOf(0, n)) { stockColor.add(c) }   // ios: Views/BoardView.swift:439
            val rects = ArrayList<Rect>(1000); val colors = ArrayList<Color>(1000)
            // ios: Views/BoardView.swift:440
            for (i in 0 until 1000) {
                val c: Color   // ios: Views/BoardView.swift:441
                if (i >= filled) c = track   // ios: Views/BoardView.swift:442
                else if (mode == "stock") c = if (i < stockColor.size) stockColor[i] else muted   // ios: Views/BoardView.swift:443
                else if (i < floor) c = if (mode == "mkt") (if (dark) hex(0x4A5560) else hex(0xB9C2CA)) else teal   // ios: Views/BoardView.swift:444
                else c = if (mode == "mine") (if (dark) hex(0x3C8F86) else hex(0x7FD3C9)) else green   // ios: Views/BoardView.swift:445
                rects.add(rect(i)); colors.add(c)   // ios: Views/BoardView.swift:446
            }
            // ios: Views/BoardView.swift:448
            val edges = if (mode == "chg" || mode == "mine") {
                val a = min(filled, yesterday); val b = max(filled, yesterday)   // ios: Views/BoardView.swift:449
                (a until b).map { rect(it) }
            } else emptyList()
            val edgeColor = if (filled >= yesterday) up else down
            val star = rect(999).let { Rect(it.left + 0.5f, it.top + 0.5f, it.right - 0.5f, it.bottom - 0.5f) }   // ios: Views/BoardView.swift:452 (insetBy 0.5)
            onDrawBehind {
                for (k in rects.indices) drawRect(colors[k], rects[k].topLeft, rects[k].size)
                for (e in edges) drawRect(edgeColor, e.topLeft, e.size, style = Stroke(1.2f))   // ios: Views/BoardView.swift:450
                drawOval(yellow, star.topLeft, star.size)
            }
        },
    )
}
