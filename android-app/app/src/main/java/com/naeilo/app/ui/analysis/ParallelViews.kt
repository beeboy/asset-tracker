package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.WChar
import com.naeilo.app.core.calc.PRace
import com.naeilo.app.core.calc.PVerdict
import com.naeilo.app.core.calc.PWorld
import com.naeilo.app.core.calc.Parallel
import com.naeilo.app.core.calc.fetchParallelUniverse
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.openParallelIfNeeded
import com.naeilo.app.core.calc.parallelBase
import com.naeilo.app.core.calc.parallelDistance
import com.naeilo.app.core.calc.parallelRace
import com.naeilo.app.core.calc.parallelSeries
import com.naeilo.app.core.calc.parallelTick
import com.naeilo.app.core.calc.parallelTitle
import com.naeilo.app.core.calc.parallelVerdictLine
import com.naeilo.app.core.calc.pickWho
import com.naeilo.app.core.calc.pworldShown
import com.naeilo.app.core.calc.saveBasket
import com.naeilo.app.core.calc.verdict
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.net.Market
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.DropMenu
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.MenuItem
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// 다른 방식이었다면? (평행 세계의 나). 계산은 core/calc/Parallel.kt. 홈 비교 카드에서 들어온다.
// 위: 결론 카드, 세 가지 방식 목록(수익률 순, 순위 배지 없음), 출발 후 흐름, 인물 규칙 카드, 기준 문단.
// 색은 화면 사이 고정 규칙(내 실제 투자 초록, 모의 바구니 주황, 인물 규칙 보라 점선). 유도 문구 없음.

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

// ios: Views/ParallelView.swift:7
object ParallelUI {
    val colors: List<Color> @Composable get() = listOf(Theme.myReal, Theme.basket, Theme.rule)   // ios: Views/ParallelView.swift:8
    val short: List<String> = listOf("실제", "모의")   // ios: Views/ParallelView.swift:9
}

/** 이동 막대 제목 (App.kt 홈 길에서 쓴다) */
object ParallelTitles {
    const val main = "다른 방식이었다면?"   // ios: Views/ParallelView.swift:43
    const val basket = "모의 바구니 고치기"   // ios: Views/ParallelView.swift:384
}

// ios: Views/ParallelView.swift:12,13,16
@Composable
fun ParallelView(m: AppModel) {
    val reduce = LocalReduceMotion.current
    val prog = remember { Animatable(0f) }   // ios: Views/ParallelView.swift:14
    // ios: Views/ParallelView.swift:44,45,46,47,48 선을 2.4초에 걸쳐 그린다 (easeInOut)
    LaunchedEffect(Unit) {
        m.openParallelIfNeeded()
        m.parallelTick()
        prog.snapTo(0f)
        if (reduce) prog.snapTo(1f) else prog.animateTo(1f, tween(2400, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)))
    }
    // ios: Views/ParallelView.swift:50,51,52 (받기는 IO 줄)
    LaunchedEffect(Unit) {
        Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet())
        m.parallelTick()
    }
    // ios: Views/ParallelView.swift:17,40,42 (제목은 이동 막대 ParallelTitles.main)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        Screen {
            // ios: Views/ParallelView.swift:18
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("평행 세계의 나", style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/ParallelView.swift:19
                val w = m.pworldShown; val race = m.parallelRace
                // ios: Views/ParallelView.swift:20
                if (w != null && race != null) {
                    val r = remember(race, w) { m.parallelSeries(race) }   // ios: Views/ParallelView.swift:21 (세 줄 수익률은 기록이 바뀔 때만)
                    val v = Parallel.verdict(r, at = race.last)   // ios: Views/ParallelView.swift:22
                    Verdict(m, race, r, v, w)   // ios: Views/ParallelView.swift:23
                    v.order.forEach { k -> Row0(m, k, race, r, v, w) }   // ios: Views/ParallelView.swift:24
                    Chart(m, race, r, w, prog.value.toDouble())   // ios: Views/ParallelView.swift:25
                    RuleCard(m, race, r, w)   // ios: Views/ParallelView.swift:26
                    Buttons(m, w)   // ios: Views/ParallelView.swift:27
                    PastBaskets(race, w)   // ios: Views/ParallelView.swift:28
                    if (m.isSample) T("샘플 종목 · 내 종목 넣으면 그날부터 비교", style = appFont(12f), color = Theme.sub)   // ios: Views/ParallelView.swift:29
                    Basis()   // ios: Views/ParallelView.swift:30
                } else {   // ios: Views/ParallelView.swift:31
                    // ios: Views/ParallelView.swift:32,33,34
                    Card { T(if (m.parallelBase.isEmpty()) "종목 탭에서 수량을 넣으면 비교 시작" else "지난 종가 받는 중 · 잠시 뒤 다시", style = appFont(14f), color = Theme.sub) }
                    if (m.isSample) T("샘플 종목 · 내 종목 넣으면 그날부터 비교", style = appFont(12f), color = Theme.sub)   // ios: Views/ParallelView.swift:36
                    T("모의 결과 · 투자 권유 아님", style = appFont(12f), color = Theme.muted)   // ios: Views/ParallelView.swift:37
                }
            }
        }
    }
}

// ios: Views/ParallelView.swift:56
private fun who(m: AppModel, w: PWorld): String = m.friendName(w.who)
// ios: Views/ParallelView.swift:57
private fun title(m: AppModel, k: Int, w: PWorld): String = m.parallelTitle(k, who = w.who)
// ios: Views/ParallelView.swift:58
private fun shortName(m: AppModel, k: Int, w: PWorld): String = if (k == 2) who(m, w) else ParallelUI.short[k]

// MARK: 결론 카드 (진한 바탕)
// ios: Views/ParallelView.swift:61
@Composable
private fun Verdict(m: AppModel, race: PRace, r: List<DoubleArray>, v: PVerdict, w: PWorld) {
    val t = race.last   // ios: Views/ParallelView.swift:62
    val start = ParallelView0.md(race.dates.firstOrNull() ?: "")   // ios: Views/ParallelView.swift:63
    val first = (if (m.isGoal) "" else "최저점 ") + "$start · 같은 ${AppModel.eok(race.V0)}으로 출발"   // ios: Views/ParallelView.swift:64
    val lead = title(m, v.order[0], w)   // ios: Views/ParallelView.swift:65
    val head = if (v.similar) "셋 다 비슷해요" else "$lead${WChar.josa(lead)} 앞서요"   // ios: Views/ParallelView.swift:66
    val gap = cformat("%.1f%%p", v.gap * 100) + " · " + AppModel.aboutWon(race.V0 * v.gap)   // ios: Views/ParallelView.swift:67
    val goalPcts = (0 until 3).map { AppModel.pct(min(1.0, race.V0 * (1 + r[it][t]) / max(1.0, m.gK * 1e4))) }.toSet()   // ios: Views/ParallelView.swift:68
    val mid = race.V0 * (1 + r[v.order[1]][t])   // ios: Views/ParallelView.swift:69
    // ios: Views/ParallelView.swift:70,86,87,88
    Column(Modifier.fillMaxWidth().background(Theme.myReal, RoundedCornerShape(16.dp)).semantics(mergeDescendants = true) {}.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(first, style = appFont(13f), color = Color.White.copy(alpha = 0.8f))   // ios: Views/ParallelView.swift:71
        T(head, style = appFont(22f, FontWeight.Bold), color = Color.White, roll = head)   // ios: Views/ParallelView.swift:72
        // ios: Views/ParallelView.swift:73,74 ("**gap**" 굵게)
        TA(mdBold("차이 **$gap** · 몇 달 더 지켜봐요"), style = appFont(13f), color = Color.White.copy(alpha = 0.85f))
        val p = goalPcts.firstOrNull()
        // ios: Views/ParallelView.swift:75
        if (m.isGoal && goalPcts.size == 1 && p != null) {
            T("셋 다 목표 $p", style = appFont(13f, FontWeight.Bold), color = Color.White)   // ios: Views/ParallelView.swift:76
            // ios: Views/ParallelView.swift:77,78,79,80,83
            Box(Modifier.fillMaxWidth().height(6.dp).semantics { invisibleToUser() }.background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(min(1.0, mid / max(1.0, m.gK * 1e4)).toFloat()).background(Color.White, CircleShape))
            }
        }
    }
}

// MARK: 세 가지 방식 (수익률 높은 순, 1등만 회색 칩)
// ios: Views/ParallelView.swift:92
@Composable
private fun Row0(m: AppModel, k: Int, race: PRace, r: List<DoubleArray>, v: PVerdict, w: PWorld) {
    val t = race.last; val ret = r[k][t]; val amount = race.V0 * (1 + ret)   // ios: Views/ParallelView.swift:93
    val goalSame = m.isGoal && (0 until 3).map { AppModel.pct(min(1.0, race.V0 * (1 + r[it][t]) / max(1.0, m.gK * 1e4))) }.toSet().size == 1   // ios: Views/ParallelView.swift:94
    val c = ParallelUI.colors[k]
    // ios: Views/ParallelView.swift:95,114,115,116,117
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp))
        .semantics(mergeDescendants = true) {}.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c, RoundedCornerShape(2.dp)))   // ios: Views/ParallelView.swift:96
        // ios: Views/ParallelView.swift:97,108
        Column(Modifier.weight(1f).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // ios: Views/ParallelView.swift:98
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title(m, k, w), style = appFont(15f, FontWeight.Bold))   // ios: Views/ParallelView.swift:99
                // ios: Views/ParallelView.swift:100,101,102
                if (k == v.order[0] && !v.similar) T("1위", Modifier.background(Theme.line, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold), color = Theme.sub)
            }
            T(desc(k, w), style = appFont(12f), color = Theme.sub)   // ios: Views/ParallelView.swift:105
            if (!goalSame) T(m.parallelDistance(amount), style = appFont(12f), color = Theme.muted)   // ios: Views/ParallelView.swift:106
        }
        // ios: Views/ParallelView.swift:109
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(AppModel.sgn(ret), style = appFont(17f, FontWeight.Bold), color = Theme.change(ret), roll = AppModel.sgn(ret))   // ios: Views/ParallelView.swift:110
            T(AppModel.eok(amount), style = appFont(12f), color = Theme.sub)   // ios: Views/ParallelView.swift:111
        }
    }
}
// ios: Views/ParallelView.swift:119,120
private fun desc(k: Int, w: PWorld): String = when (k) {
    0 -> "지금 종목 그대로"   // ios: Views/ParallelView.swift:121
    // ios: Views/ParallelView.swift:122
    1 -> {
        val parts = (w.segs.lastOrNull()?.w ?: emptyMap()).entries.sortedByDescending { it.value }.map { "${it.key} ${(it.value * 100).swRound().toInt()}" }   // ios: Views/ParallelView.swift:123
        parts.joinToString(" · ")   // ios: Views/ParallelView.swift:124
    }
    else -> Parallel.rules[w.who] ?: ""   // ios: Views/ParallelView.swift:125
}

// MARK: 출발 후 수익률 흐름 (이 앱을 연 날 앞은 흐리게)
// ios: Views/ParallelView.swift:130
@Composable
private fun Chart(m: AppModel, race: PRace, r: List<DoubleArray>, w: PWorld, prog: Double) {
    val all: List<Double> = r.flatMap { it.toList() }   // ios: Views/ParallelView.swift:131
    val lo = min(0.0, all.minOrNull() ?: 0.0); val hi = max(0.0, all.maxOrNull() ?: 0.0)   // ios: Views/ParallelView.swift:132
    val span = max(1e-6, hi - lo)   // ios: Views/ParallelView.swift:133
    val n = race.dates.size; val t = race.last   // ios: Views/ParallelView.swift:134
    val openX: Double = if (n > 1) min(race.open, n - 1).toDouble() / (n - 1).toDouble() else 1.0   // ios: Views/ParallelView.swift:135
    val pts: List<List<Offset>> = remember(r) { r.map { ParallelView0.points(it.toList(), hi = hi, span = span) } }   // ios: Views/ParallelView.swift:136
    val zeroY = hi / span   // ios: Views/ParallelView.swift:137
    val ends = listOf(0, 1, 2).sortedByDescending { r[it][t] }   // ios: Views/ParallelView.swift:138
    val colors = ParallelUI.colors; val dash = Theme.dash; val muted = Theme.muted
    // ios: Views/ParallelView.swift:139
    Card {
        // ios: Views/ParallelView.swift:140,142
        Row(Modifier.fillMaxWidth()) {
            T("출발 후 수익률", Modifier.weight(1f).alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/ParallelView.swift:141
            val ms = cformat("%.1f%%p", ParallelView0.maxSpread(r, race) * 100)
            T("최대 차이 $ms", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.sub)   // Views/ParallelView.swift:143 (검사 예외: 같은 글)
        }
        // ios: Views/ParallelView.swift:145
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ios: Views/ParallelView.swift:146,147,163,164
            BoxWithConstraints(Modifier.weight(1f).height(160.dp).semantics { invisibleToUser() }) {
                val W = constraints.maxWidth.toFloat()
                // 진행(prog)이 매 프레임 바뀌므로 길은 그릴 때 만든다 (점 수백 개, 가볍다)
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(dash, Offset(0f, (zeroY * size.height).toFloat()), Offset(size.width, (zeroY * size.height).toFloat()), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))   // ios: Views/ParallelView.swift:148,149
                    // ios: Views/ParallelView.swift:150,151,152
                    if (race.open < n && race.open > 0) drawLine(muted, Offset((openX * size.width).toFloat(), 0f), Offset((openX * size.width).toFloat(), size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                    // ios: Views/ParallelView.swift:156
                    for (k in 0 until 3) {
                        // 인물 규칙은 규칙이 움직이기 전까지 내 실제 투자와 같은 길이라 점선으로 위에 그린다
                        drawPath(RaceLine.path(pts[k], from = 0.0, progress = prog, w = size.width, h = size.height), colors[k].copy(alpha = 0.35f), style = ParallelView0.lineStyle(k, 2.0f * density, density))   // ios: Views/ParallelView.swift:158
                        drawPath(RaceLine.path(pts[k], from = openX, progress = prog, w = size.width, h = size.height), colors[k], style = ParallelView0.lineStyle(k, 2.5f * density, density))   // ios: Views/ParallelView.swift:159
                    }
                }
                // ios: Views/ParallelView.swift:153,154
                if (race.open < n && race.open > 0) T("앱 연 날", Modifier.layout { mm, cc -> val p = mm.measure(cc); val cx = min(W - 34.dp.toPx(), max(34.dp.toPx(), (openX * W).toFloat())); layout(p.width, p.height) { p.place((cx - p.width / 2f).toInt(), (6.dp.toPx() - p.height / 2f).toInt()) } },
                    style = appFont(10f), color = Theme.muted)
            }
            // ios: Views/ParallelView.swift:165,166,170
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ends.forEach { k -> T("${shortName(m, k, w)} ${AppModel.sgn(r[k][t])}", style = appFont(11f, FontWeight.Bold), color = colors[k], maxLines = 1) }   // ios: Views/ParallelView.swift:167
            }
        }
        // ios: Views/ParallelView.swift:172,174,176
        Row(Modifier.fillMaxWidth()) {
            T(ParallelView0.mdShort(race.dates.firstOrNull() ?: ""), style = appFont(11f), color = Theme.muted)   // ios: Views/ParallelView.swift:173
            Spacer(Modifier.weight(1f))
            T("0% = 출발", style = appFont(11f), color = Theme.muted)   // ios: Views/ParallelView.swift:175
            Spacer(Modifier.weight(1f))
            T(ParallelView0.mdShort(race.dates.lastOrNull() ?: ""), style = appFont(11f), color = Theme.muted)   // ios: Views/ParallelView.swift:177
        }
        // ios: Views/ParallelView.swift:179,180
        if (ParallelView0.sameRoad(race)) T("${who(m, w)} 규칙 아직 실행 전 · 실제와 같은 길", style = appFont(12f), color = Theme.sub)
    }
}

// MARK: 인물 규칙 카드
// ios: Views/ParallelView.swift:192
@Composable
private fun RuleCard(m: AppModel, race: PRace, r: List<DoubleArray>, w: PWorld) {
    val f = Shelter.friends.firstOrNull { it.id == w.who }   // ios: Views/ParallelView.swift:193
    val place = Parallel.verdict(r, at = race.last).order.indexOf(2).let { if (it < 0) 2 else it + 1 }   // ios: Views/ParallelView.swift:194
    // ios: Views/ParallelView.swift:195
    Card {
        T("${who(m, w)} 규칙", style = appFont(15f, FontWeight.Bold))   // ios: Views/ParallelView.swift:196
        // ios: Views/ParallelView.swift:197
        (f?.ruleSteps ?: emptyList()).forEachIndexed { i, s -> RuleStep(i, s) }
        // ios: Views/ParallelView.swift:204,205
        if (w.who == "sua" && race.contrib > 0) T("월 ${AppModel.wonK(m.gM)} 적립분 ${AppModel.eok(race.contrib)} 포함", style = appFont(12f), color = Theme.sub)
        // ios: Views/ParallelView.swift:207,208,209,210,211
        T("${who(m, w)}: “${Parallel.line(w.who, place = place, day = Day.today)}”", Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
          style = appFont(13f).ls(3f))
        // ios: Views/ParallelView.swift:212,213,215 (char:<인물>:invest)
        Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable { m.tab = com.naeilo.app.Tab.home; m.homePath = m.homePath + "char:${w.who}:invest" }, contentAlignment = Alignment.CenterStart) {
            T("과거 폭락장에선? ›", style = appFont(13f, FontWeight.Bold), color = Theme.rule)
        }
    }
}

/** 규칙 한 줄 (번호 동그라미 + 굵게 표시가 든 글). 투자 갈래와 같이 쓴다 */
// ios: Views/ParallelView.swift:198,199,200,201
@Composable
internal fun RuleStep(i: Int, s: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(20.dp, 20.dp).background(Theme.rule, CircleShape), contentAlignment = Alignment.Center) { T("${i + 1}", style = appFont(12f, FontWeight.Bold), color = Color.White) }
        TA(ParallelView0.md(bold = s), Modifier.weight(1f), style = appFont(14f))
    }
}

// MARK: 보조 버튼 두 개
// ios: Views/ParallelView.swift:220
@Composable
private fun Buttons(m: AppModel, w: PWorld) {
    // ios: Views/ParallelView.swift:221
    if (m.pworld != null) {
        // ios: Views/ParallelView.swift:222
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).plainClickable { m.homePath = m.homePath + "parallel:basket" }) { Pill("바구니 바꾸기") }   // ios: Views/ParallelView.swift:223
            // ios: Views/ParallelView.swift:224,225,226,228
            Box(Modifier.weight(1f)) {
                DropMenu(Shelter.friends.filter { it.id != w.who && it.id != m.calm.type }.map { f -> MenuItem(f.name + " · " + f.does, onClick = { m.pickWho(f.id) }) }) { Pill("다른 인물 비교") }
            }
        }
    }
}
// ios: Views/ParallelView.swift:232,233,234,235,236
@Composable
private fun Pill(s: String) = Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
    T(s, style = appFont(13f, FontWeight.Bold), color = Theme.ink)
}

// MARK: 기준 문단
// ios: Views/ParallelView.swift:240,241,242
@Composable
private fun Basis() = T("어제 종가 기준 · 입출금 제외 · 투자 권유 아님", style = appFont(12f), color = Theme.muted)

// MARK: 지난 바구니 (고친 결과로 과거 기록을 덮지 않는다)
// ios: Views/ParallelView.swift:246
@Composable
private fun PastBaskets(race: PRace, w: PWorld) {
    val segs = w.segs.sortedBy { it.from }   // ios: Views/ParallelView.swift:247
    // ios: Views/ParallelView.swift:248
    if (segs.size > 1) {
        // ios: Views/ParallelView.swift:249
        Card {
            T("지난 바구니", style = appFont(15f, FontWeight.Bold))   // ios: Views/ParallelView.swift:250
            // ios: Views/ParallelView.swift:251
            segs.dropLast(1).forEachIndexed { i, g ->
                val end = segs[i + 1].from   // ios: Views/ParallelView.swift:252
                val ret: Double = if (i < race.segRet.size) race.segRet[i] else 0.0   // ios: Views/ParallelView.swift:253
                val parts: List<String> = g.w.entries.sortedByDescending { it.value }.map { "${it.key} ${AppModel.pct(it.value)}" }   // ios: Views/ParallelView.swift:254
                // ios: Views/ParallelView.swift:255
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // ios: Views/ParallelView.swift:256,258
                    Row(Modifier.fillMaxWidth()) {
                        T((if (i == 0) "출발" else ParallelView0.md(g.from)) + " ~ " + ParallelView0.md(end), Modifier.weight(1f), style = appFont(13f, FontWeight.Bold))   // ios: Views/ParallelView.swift:257
                        T(AppModel.sgn(ret), style = appFont(13f, FontWeight.Bold), color = if (ret >= 0) Theme.up else Theme.down)   // ios: Views/ParallelView.swift:259
                    }
                    T(parts.joinToString(" · "), style = appFont(12f), color = Theme.sub)   // ios: Views/ParallelView.swift:261
                }
            }
        }
    }
}

// ios: Views/ParallelView.swift:12
object ParallelView0 {
    /** 세 선이 가장 크게 벌어진 차이 */
    // ios: Views/ParallelView.swift:185
    fun maxSpread(r: List<DoubleArray>, race: PRace): Double {
        var spread = 0.0   // ios: Views/ParallelView.swift:186
        for (t in race.dates.indices) spread = max(spread, (0 until 3).maxOf { r[it][t] } - (0 until 3).minOf { r[it][t] })   // ios: Views/ParallelView.swift:187
        return spread   // ios: Views/ParallelView.swift:188
    }
    /** 인물 규칙이 지금까지 내 실제 투자와 같은 길인지 (규칙이 한 번도 움직이지 않음) */
    // ios: Views/ParallelView.swift:269
    fun sameRoad(race: PRace): Boolean {
        var d = 0.0   // ios: Views/ParallelView.swift:270
        for (t in race.dates.indices) d = max(d, abs(race.r[0][t] - race.r[2][t]))   // ios: Views/ParallelView.swift:271
        return d < 0.0005   // ios: Views/ParallelView.swift:272
    }
    /** 내 실제 투자는 굵게, 인물 규칙은 점선 (두 줄이 겹쳐도 둘 다 보이게) */
    // ios: Views/ParallelView.swift:276
    fun lineStyle(k: Int, w: Float, density: Float): Stroke =
        if (k == 0) Stroke(w + 1.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)   // ios: Views/ParallelView.swift:277
        else if (k == 2) Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * density, 4 * density)))   // ios: Views/ParallelView.swift:278
        else Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)   // ios: Views/ParallelView.swift:279
    /** 그래프 점 (x 0~1 날짜 순서, y 0~1 위가 높은 수익률) */
    // ios: Views/ParallelView.swift:283
    fun points(s: List<Double>, hi: Double, span: Double): List<Offset> {
        val n = s.size   // ios: Views/ParallelView.swift:284
        val out = ArrayList<Offset>()   // ios: Views/ParallelView.swift:285
        // ios: Views/ParallelView.swift:286
        for (t in 0 until n) {
            val x: Double = if (n > 1) t.toDouble() / (n - 1).toDouble() else 0.0   // ios: Views/ParallelView.swift:287
            out.add(Offset(x.toFloat(), ((hi - s[t]) / span).toFloat()))   // ios: Views/ParallelView.swift:288
        }
        return out   // ios: Views/ParallelView.swift:290
    }
    // ios: Views/ParallelView.swift:293
    fun md(d: String): String {
        val x = Day.date(d) ?: return d   // ios: Views/ParallelView.swift:294
        return DateTimeFormatter.ofPattern("yy.M.d", AppModel.dateLocale).withZone(ZoneId.systemDefault()).format(x)   // ios: Views/ParallelView.swift:295,296
    }
    /** 그래프 축: "9.11" */
    // ios: Views/ParallelView.swift:299
    fun mdShort(d: String): String {
        val x = Day.date(d) ?: return d   // ios: Views/ParallelView.swift:300
        return DateTimeFormatter.ofPattern("M.d", AppModel.dateLocale).withZone(ZoneId.systemDefault()).format(x)   // ios: Views/ParallelView.swift:301,302
    }
    /** "**굵게**" 표시가 든 문구 */
    // ios: Views/ParallelView.swift:305
    fun md(bold: String, @Suppress("UNUSED_PARAMETER") b: Boolean = true): AnnotatedString = mdBold(bold)
}

/** **굵게** 만 읽는 짧은 마크다운 */
internal fun mdBold(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf("**", i); val b = if (a >= 0) s.indexOf("**", a + 2) else -1
        if (a < 0 || b < 0) { append(s.substring(i)); break }
        append(s.substring(i, a)); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(a + 2, b)) }; i = b + 2
    }
}

/** 한 줄을 x(0~1) 기준으로 progress 까지만 그린다. from 앞은 그리지 않는다 (본선 진하게) */
// ios: Views/ParallelView.swift:309
object RaceLine {
    // ios: Views/ParallelView.swift:310,311,312,313,314,315,317
    fun path(pts: List<Offset>, from: Double, progress: Double, w: Float, h: Float): Path {
        val p = Path()   // ios: Views/ParallelView.swift:318
        var started = false   // ios: Views/ParallelView.swift:319
        // ios: Views/ParallelView.swift:320
        for (q in pts) { if (!(q.x >= from - 1e-9 && q.x <= progress + 1e-9)) continue
            val c = Offset(q.x * w, q.y * h)   // ios: Views/ParallelView.swift:321
            if (started) p.lineTo(c.x, c.y) else { p.moveTo(c.x, c.y); started = true }   // ios: Views/ParallelView.swift:322
        }
        return p   // ios: Views/ParallelView.swift:324
    }
}

/** 모의 바구니 고치기: 견본에서 시작해 비중(%)으로 고친다. 저장하면 오늘부터 새 기록 */
// ios: Views/ParallelView.swift:329,330,331,335
@Composable
fun ParallelBasketView(m: AppModel, dismiss: () -> Unit) {
    var w by remember { mutableStateOf(mapOf<String, Int>()) }   // ios: Views/ParallelView.swift:332
    var loaded by remember { mutableStateOf(false) }   // ios: Views/ParallelView.swift:333
    val sum = w.values.fold(0) { a, b -> a + b }   // ios: Views/ParallelView.swift:336

    // ios: Views/ParallelView.swift:393
    fun apply(x: Map<String, Double>) {
        val out = LinkedHashMap<String, Int>()   // ios: Views/ParallelView.swift:394
        for ((k, v) in Parallel.resolve(x)) out[k] = (v * 100).swRound().toInt()   // ios: Views/ParallelView.swift:395
        w = out   // ios: Views/ParallelView.swift:396
    }
    // ios: Views/ParallelView.swift:385,386,387,388
    LaunchedEffect(Unit) { if (!loaded) { loaded = true; apply(m.pworld?.segs?.lastOrNull()?.w ?: emptyMap()) } }
    LaunchedEffect(Unit) { Market.shared.fetchParallelUniverse() }   // ios: Views/ParallelView.swift:390

    // ios: Views/ParallelView.swift:399
    @Composable
    fun Row1(k: String) {
        val v = w[k] ?: 0   // ios: Views/ParallelView.swift:400
        // ios: Views/ParallelView.swift:401
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/ParallelView.swift:402,406
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                T(k, style = appFont(14f, FontWeight.Bold))   // ios: Views/ParallelView.swift:403
                T(Parallel.name(k), style = appFont(11f), color = Theme.sub)   // ios: Views/ParallelView.swift:404
            }
            // ios: Views/ParallelView.swift:407,408
            Box(Modifier.size(36.dp, 36.dp).plainClickable { w = w + (k to max(0, v - 5)) }.semantics { contentDescription = "$k 5%p 줄이기" }, contentAlignment = Alignment.Center) { Sym("minus", 17f, Theme.teal) }
            T("$v%", Modifier.widthIn(min = 48.dp), style = appFont(15f, FontWeight.Bold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))   // ios: Views/ParallelView.swift:409
            // ios: Views/ParallelView.swift:410,411
            Box(Modifier.size(36.dp, 36.dp).plainClickable { w = w + (k to min(100, v + 5)) }.semantics { contentDescription = "$k 5%p 늘리기" }, contentAlignment = Alignment.Center) { Sym("plus", 17f, Theme.teal) }
            // ios: Views/ParallelView.swift:412,413
            Box(Modifier.size(32.dp, 36.dp).plainClickable { w = w - k }.semantics { contentDescription = "$k 빼기" }, contentAlignment = Alignment.Center) { Sym("xmark", 17f, Theme.muted) }
        }
    }

    // ios: Views/ParallelView.swift:337,381,383 (제목은 이동 막대 ParallelTitles.basket)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        Screen {
            // ios: Views/ParallelView.swift:338
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("고치면 오늘부터 새 기록 · 이전 기록은 보관", style = appFont(13f), color = Theme.sub)   // ios: Views/ParallelView.swift:339,340
                T("견본", style = appFont(15f, FontWeight.Bold))   // ios: Views/ParallelView.swift:341
                // ios: Views/ParallelView.swift:342,343,344
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Parallel.templates.forEach { tpl ->
                        // ios: Views/ParallelView.swift:345,346,347,348
                        T(tpl.name, Modifier.plainClickable { apply(tpl.w) }.defaultMinSize(minHeight = 40.dp).background(Theme.card, CircleShape).border(2.dp, Theme.border, CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
                          style = appFont(13f, FontWeight.Bold))
                    }
                }
                // ios: Views/ParallelView.swift:352
                Card {
                    w.keys.sorted().forEach { k -> Row1(k) }   // ios: Views/ParallelView.swift:353
                    if (w.isEmpty()) T("담은 종목 없음", style = appFont(13f), color = Theme.sub)   // ios: Views/ParallelView.swift:354
                    // ios: Views/ParallelView.swift:355,356,357,358,360 (시세가 없는 종목은 고를 수 없다)
                    DropMenu(Parallel.universe.filter { w[it.id] == null && Parallel.has(it.id) }.map { u -> MenuItem(u.id + " · " + u.name + (if (Parallel.has(u.id)) "" else " (시세 준비 중)"), onClick = { w = w + (u.id to 10) }) }) {
                        Box(Modifier.defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.CenterStart) { T("+ 종목 담기", style = appFont(14f, FontWeight.Bold), color = Theme.teal) }   // ios: Views/ParallelView.swift:361
                    }
                    T("종가 있는 종목·견본 ETF만", style = appFont(12f), color = Theme.muted)   // ios: Views/ParallelView.swift:363
                }
                // ios: Views/ParallelView.swift:365
                Row(Modifier.fillMaxWidth()) {
                    T("합계", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold))   // ios: Views/ParallelView.swift:366
                    T("$sum%", style = appFont(14f, FontWeight.Bold), color = if (sum == 100) Theme.green else Theme.orange)   // ios: Views/ParallelView.swift:367
                }
                if (sum != 100 && sum > 0) T("저장 시 비율대로 100% 맞춤", style = appFont(12f), color = Theme.sub)   // ios: Views/ParallelView.swift:369
                // ios: Views/ParallelView.swift:370,371,372,373,374,375,376,378
                Box(Modifier.fillMaxWidth().then(if (sum > 0) Modifier.plainClickable { m.saveBasket(w.mapValues { it.value.toDouble() }); dismiss() } else Modifier)
                    .defaultMinSize(minHeight = 48.dp).background(if (sum > 0) Theme.teal else Theme.muted, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    T("오늘부터 시작", style = appFont(15f, FontWeight.Bold), color = Color.White)
                }
                T("모의 결과 · 투자 권유 아님", style = appFont(12f), color = Theme.muted)   // ios: Views/ParallelView.swift:379
            }
        }
    }
}

/** 홈 비교 카드: 다른 방식이었다면? (처음 진입도 여기서. 쉼터 문은 뺐다) */
// ios: Views/ParallelView.swift:419,420,421
@Composable
fun ParallelHomeCard(m: AppModel) {
    // ios: Views/ParallelView.swift:422
    if (m.parallelBase.isNotEmpty()) {
        // ios: Views/ParallelView.swift:423,424,432,433,434,435,437,438
        Row(Modifier.fillMaxWidth().plainClickable { m.homePath = m.homePath + "parallel" }.semantics(mergeDescendants = true) {}
            .defaultMinSize(minHeight = 44.dp).background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // ios: Views/ParallelView.swift:425,429
            Column(Modifier.weight(1f).padding(end = 0.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T("다른 방식이었다면?", style = appFont(15f, FontWeight.Bold))   // ios: Views/ParallelView.swift:426
                m.parallelVerdictLine?.let { T(it, style = appFont(13f, FontWeight.SemiBold), color = Theme.myReal) }   // ios: Views/ParallelView.swift:427
            }
            T("›", style = appFont(16f, FontWeight.Bold), color = Theme.sub)   // ios: Views/ParallelView.swift:430
        }
    }
}

