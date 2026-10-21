package com.naeilo.app.ui.analysis

import androidx.compose.animation.AnimatedVisibility
import com.naeilo.app.core.tr
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.Tab
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.Invest
import com.naeilo.app.core.calc.Parallel
import com.naeilo.app.core.calc.eventsSource
import com.naeilo.app.core.calc.parallelRun
import com.naeilo.app.core.calc.parallelSeries
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeSample
import com.naeilo.app.data.Shelter
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.me.MeAvatar
import com.naeilo.app.ui.shelter.spriteBacking
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

// 인물 자세히 보기의 "규칙과 결과" 갈래 (데이터는 core/calc/Invest.kt, 사건 그래프는 core/calc/InvestEvents.kt).
// 순서: 한 줄 결론 → 규칙 세 줄(+더 자세한 규칙 접기) → 과거 폭락장에서 써봤다면(세로, 최신순) → 못 막는 하락 → 내 종목에 썼다면 → 반대로 간 사람 → 기준.
// 결과는 비율·수익률로만. 매매 내역, "따라 해 보세요" 같은 말은 넣지 않는다.

// ios: Views/InvestViews.swift:7,8,9,10,11
@Composable
fun InvestBranch(m: AppModel, id: String, focus: Int? = null, toStory: () -> Unit = {}) {
    var habitsOpen by remember { mutableStateOf(false) }   // ios: Views/InvestViews.swift:12
    val friend = Shelter.friends.firstOrNull { it.id == id }   // ios: Views/InvestViews.swift:14
    val name = friend?.name ?: ""   // ios: Views/InvestViews.swift:15
    LaunchedEffect(Unit) { if (focus != null) habitsOpen = true }   // ios: Views/InvestViews.swift:33

    // ios: Views/InvestViews.swift:17,18
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ios: Views/InvestViews.swift:19,20,21,22,23
        Invest.summary[id]?.let { t ->
            T(t, Modifier.fillMaxWidth().background(Theme.myReal, RoundedCornerShape(16.dp)).padding(16.dp), style = appFont(17f, FontWeight.Bold).ls(4f), color = Color.White)
        }
        // ios: Views/InvestViews.swift:25
        InvestBranch0.Habits(id, friend?.ruleSteps ?: emptyList(), name, focus, habitsOpen) { habitsOpen = !habitsOpen }
        InvestBranch0.Cases(id, name)   // ios: Views/InvestViews.swift:26
        InvestBranch0.Facts(id)   // ios: Views/InvestViews.swift:27
        InvestBranch0.Mine(m, id, name)   // ios: Views/InvestViews.swift:28
        InvestBranch0.Foil(id, name, toStory)   // ios: Views/InvestViews.swift:29
        // ios: Views/InvestViews.swift:30,31
        T(listOf(Invest.holdNote[id], "현금 연 2%", Invest.eventsSource, Invest.disclaimer).filterNotNull().joinToString(" · "), style = appFont(12f), color = Theme.muted)
    }
}

object InvestBranch0 {
    // MARK: 규칙 세 줄 + 더 자세한 규칙 (접어 둔다)
    // ios: Views/InvestViews.swift:37,39
    @Composable
    fun Habits(id: String, steps: List<String>, name: String, focus: Int?, habitsOpen: Boolean, toggle: () -> Unit) {
        val more = Invest.habits[id] ?: emptyList()   // ios: Views/InvestViews.swift:38
        Card {
            T("$name 규칙", style = appFont(15f, FontWeight.Bold))   // ios: Views/InvestViews.swift:40
            // ios: Views/InvestViews.swift:41
            steps.forEachIndexed { i, s ->
                // ios: Views/InvestViews.swift:42
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ios: Views/InvestViews.swift:43,44
                    Box(Modifier.size(20.dp, 20.dp).background(Theme.rule, CircleShape), contentAlignment = Alignment.Center) { T("${i + 1}", style = appFont(12f, FontWeight.Bold), color = Color.White) }
                    TA(ParallelView0.md(bold = s), Modifier.weight(1f), style = appFont(14f))   // ios: Views/InvestViews.swift:45
                }
            }
            // ios: Views/InvestViews.swift:48,49,51,54,56,57 (펴고 접기는 easeInOut 0.2초)
            val fade = tween<Float>(200, easing = FastOutSlowInEasing)
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 36.dp).plainClickable { toggle() }
                .semantics { contentDescription = tr(if (habitsOpen) "자세한 규칙 접기" else "자세한 규칙 펴기") }, verticalAlignment = Alignment.CenterVertically) {
                T("자세히 ${more.size}가지", style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/InvestViews.swift:50
                Spacer(Modifier.weight(1f))
                Sym(if (habitsOpen) "chevron.up" else "chevron.down", 17f, Theme.muted)   // ios: Views/InvestViews.swift:52
            }
            // ios: Views/InvestViews.swift:58
            AnimatedVisibility(habitsOpen, enter = fadeIn(fade) + expandVertically(tween(200, easing = FastOutSlowInEasing)), exit = fadeOut(fade) + shrinkVertically(tween(200, easing = FastOutSlowInEasing))) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ios: Views/InvestViews.swift:59,60,64,65
                    more.forEachIndexed { i, h ->
                        Row(Modifier.background(if (i == focus) Theme.tealBg else Color.Transparent, RoundedCornerShape(8.dp)).padding(vertical = if (i == focus) 6.dp else 0.dp, horizontal = if (i == focus) 6.dp else 0.dp),
                            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T("${i + 1}", Modifier.width(16.dp), style = appFont(12f, FontWeight.Bold).copy(textAlign = TextAlign.Center), color = Theme.rule)   // ios: Views/InvestViews.swift:61
                            T(h, style = appFont(14f, if (i == focus) FontWeight.Bold else FontWeight.Normal).ls(3f))   // ios: Views/InvestViews.swift:62
                        }
                    }
                    Invest.habitNote[id]?.let { n -> T(n, style = appFont(12f), color = Theme.muted) }   // ios: Views/InvestViews.swift:67
                }
            }
        }
    }

    // MARK: 과거 폭락장에서 써봤다면 (세로 한 목록, 연도 최신순)
    // ios: Views/InvestViews.swift:73
    @Composable
    fun Cases(id: String, name: String) {
        val list = Invest.cases(id)   // ios: Views/InvestViews.swift:74
        // ios: Views/InvestViews.swift:75,76
        if (list.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("과거 폭락장이었다면", style = appFont(15f, FontWeight.Bold))   // ios: Views/InvestViews.swift:77
            // ios: Views/InvestViews.swift:78,79
            Invest.premise[id]?.let { p -> TA(ParallelView0.md(bold = p), style = appFont(13f).ls(3f), color = Theme.sub) }
            list.forEach { e -> CaseCard(e, name) }   // ios: Views/InvestViews.swift:81
        }
    }

    // MARK: 이 규칙이 못 막는 하락 (fact 있는 사건만)
    // ios: Views/InvestViews.swift:87,88
    @Composable
    fun Facts(id: String) = Invest.facts(id).forEach { e ->
        // ios: Views/InvestViews.swift:89,97,98,99
        Column(Modifier.fillMaxWidth().background(Theme.against.copy(alpha = 0.1f), RoundedCornerShape(14.dp)).semantics(mergeDescendants = true) {}.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("못 막는 하락", style = appFont(13f, FontWeight.Bold), color = Theme.against)   // ios: Views/InvestViews.swift:90
            // ios: Views/InvestViews.swift:91
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(e.fact ?: "", Modifier.alignByBaseline(), style = appFont(24f, FontWeight.Bold), color = Theme.against)   // ios: Views/InvestViews.swift:92
                T("${e.title} ${e.`when`}", Modifier.alignByBaseline(), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/InvestViews.swift:93
            }
            T(e.text, style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/InvestViews.swift:95
        }
    }

    // MARK: 같은 날, 반대로 간 사람 (소설 조연, 컷 그림은 그대로)
    // ios: Views/InvestViews.swift:104
    @Composable
    fun Foil(id: String, name: String, toStory: () -> Unit) {
        val f = Invest.foils[id] ?: return   // ios: Views/InvestViews.swift:105
        // ios: Views/InvestViews.swift:106
        Card {
            T("반대로 간 사람", style = appFont(15f, FontWeight.Bold))   // ios: Views/InvestViews.swift:107
            T(f.did + " →", style = appFont(14f).ls(3f))   // ios: Views/InvestViews.swift:108
            T(f.result, style = appFont(14f, FontWeight.SemiBold))   // ios: Views/InvestViews.swift:109
            // ios: Views/InvestViews.swift:110
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // ios: Views/InvestViews.swift:111,112,113
                MeSample.all.firstOrNull { it.name == f.name }?.let { s ->
                    MeAvatar(MeChar(name = s.name, parts = s.parts), 36.dp, 51.dp, Modifier.background(Shelter.spriteBacking(""), RoundedCornerShape(8.dp)).padding(5.dp))
                }
                // ios: Views/InvestViews.swift:115
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(f.name, style = appFont(14f, FontWeight.Bold))   // ios: Views/InvestViews.swift:116
                    T(f.line, style = appFont(12f), color = Theme.sub)   // ios: Views/InvestViews.swift:117
                }
            }
            // 컷 글자가 그림에 박혀 있어 영어는 따로 그린 event_<인물>_en (tools/event_en)
            val art = "event_" + id + (if (AppModel.en) "_en" else "")   // ios: Views/InvestViews.swift:121
            // ios: Views/InvestViews.swift:122,123,124,125
            if (MeArt.has(art)) BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).semantics { contentDescription = tr("${f.name}의 ${f.model} 컷과 같은 날의 $name") }) {
                PixelN(art, maxWidth, maxWidth)
            }
            // ios: Views/InvestViews.swift:127,128,130
            Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable(onClick = toStory), contentAlignment = Alignment.CenterStart) {
                T("$name 이야기 ›", style = appFont(13f, FontWeight.Bold), color = Theme.rule)
            }
        }
    }

    // MARK: 이 규칙을 내 종목에 썼다면 (평행 세계 엔진, 같은 출발일·금액)
    // ios: Views/InvestViews.swift:136,137
    @Composable
    fun Mine(m: AppModel, id: String, name: String) = Card {
        T("내 종목에 썼다면", style = appFont(15f, FontWeight.Bold))   // ios: Views/InvestViews.swift:138
        val w = m.pworld
        val race = remember(w, id) { w?.let { m.parallelRun(it, who = id) } }   // 계산은 기록·인물이 바뀔 때만
        // ios: Views/InvestViews.swift:139
        if (w != null && race != null) {
            val s = remember(race) { m.parallelSeries(race) }   // ios: Views/InvestViews.swift:140
            val ret = (if (id == w.who) s[2] else race.r[2])[race.last]; val real = s[0][race.last]   // ios: Views/InvestViews.swift:141
            T(since(race.dates.firstOrNull() ?: ""), style = appFont(13f), color = Theme.sub)   // ios: Views/InvestViews.swift:142
            // ios: Views/InvestViews.swift:143
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell("$name 규칙", ret, Theme.rule, Modifier.weight(1f))   // ios: Views/InvestViews.swift:144
                Cell("내 실제", real, Theme.myReal, Modifier.weight(1f))   // ios: Views/InvestViews.swift:145
            }
            // ios: Views/InvestViews.swift:147,148
            TA(ParallelView0.md(bold = moneyLine(gap = (race.V0 + race.contrib) * (ret - real), ret = ret, idle = race.log.isEmpty())), style = appFont(14f).ls(3f))
            // ios: Views/InvestViews.swift:149,150,151,152,153
            T("$name: “${Parallel.line(id, place = if (ret > real) 1 else 3, day = Day.today)}”", Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
              style = appFont(13f).ls(3f))
        } else {   // ios: Views/InvestViews.swift:154
            // ios: Views/InvestViews.swift:155,156,158
            Box(Modifier.defaultMinSize(minHeight = 36.dp).plainClickable { m.tab = Tab.home; m.homePath = listOf("shelter", "parallel") }, contentAlignment = Alignment.CenterStart) {
                T("다른 방식이었다면? ›", style = appFont(14f, FontWeight.Bold), color = Theme.teal)
            }
        }
    }
    // ios: Views/InvestViews.swift:162,163,167,168
    @Composable
    private fun Cell(k: String, v: Double, c: Color, modifier: Modifier) = Column(modifier.background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(k, style = appFont(12f), color = Theme.sub)   // ios: Views/InvestViews.swift:164
        T(AppModel.sgn(v), style = appFont(20f, FontWeight.Bold), color = c)   // ios: Views/InvestViews.swift:165
    }
    /** 금액 문장: gap = 규칙대로 평가액 − 실제 평가액 (원). 1만원 미만이면 "거의 같음".
     *  부호 맞추기: 규칙 쪽이 손실이면 "덜 잃었을 / 더 잃었을", 이익이면 "더 벌었을 / 덜 벌었을" */
    // ios: Views/InvestViews.swift:172
    fun moneyLine(gap: Double, ret: Double, idle: Boolean): String {
        val why = if (idle) " · 규칙 실행 0회" else ""   // ios: Views/InvestViews.swift:173
        if (abs(gap) < 5e3) return "**거의 같음**" + why   // ios: Views/InvestViews.swift:174
        val verb = if (ret < 0) (if (gap > 0) "덜 잃었을" else "더 잃었을") else (if (gap > 0) "더 벌었을" else "덜 벌었을")   // ios: Views/InvestViews.swift:175
        return "**${AppModel.aboutWon(gap).dropLast(1)} $verb 것**" + why   // ios: Views/InvestViews.swift:176
    }
    /** 출발일 "2026-09-11" → "26.9.11~" */
    // ios: Views/InvestViews.swift:179
    fun since(d: String): String {
        val x = Day.date(d) ?: return d   // ios: Views/InvestViews.swift:180
        val f = DateTimeFormatter.ofPattern("yy.M.d", AppModel.dateLocale).withZone(ZoneId.systemDefault())   // ios: Views/InvestViews.swift:181
        return f.format(x) + "~"   // ios: Views/InvestViews.swift:182
    }
}

/** 사례 카드: 이름·기간, 유리/불리 배지(끝값 비교), 막대 두 줄, 한 줄 설명 */
// ios: Views/InvestViews.swift:187,188,189,191
@Composable
fun CaseCard(e: Invest.Event, name: String) {
    val a = e.ruleRet; val b = e.holdRet; val top = max(max(abs(a), abs(b)), 0.001)   // ios: Views/InvestViews.swift:192
    // ios: Views/InvestViews.swift:193,206,207,208,209
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).semantics(mergeDescendants = true) {}.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ios: Views/InvestViews.swift:194
        Row(Modifier.fillMaxWidth()) {
            // 제목·기간은 남는 폭 안에서 (기간을 먼저 재고 제목은 모자랄 때만 0.8배까지 줄인다), 배지는 오른쪽 끝
            Row(Modifier.weight(1f).alignByBaseline()) {
                FitText(e.title, appFont(15f, FontWeight.Bold), 0.8f, Modifier.alignByBaseline().weight(1f, fill = false), maxLines = 1)   // ios: Views/InvestViews.swift:195
                T(e.`when`, Modifier.alignByBaseline().padding(start = 8.dp), style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/InvestViews.swift:196
            }
            Spacer(Modifier.width(4.dp))   // ios: Views/InvestViews.swift:197
            // ios: Views/InvestViews.swift:198,199,200
            T(if (e.ruleWins) "유리" else "불리", Modifier.alignByBaseline().background(if (e.ruleWins) Theme.favor else Theme.against, CapsuleShape).padding(horizontal = 8.dp, vertical = 3.dp),
              style = appFont(11f, FontWeight.Bold), color = Color.White)
        }
        CaseBar(name, a, a / top, Theme.rule)   // ios: Views/InvestViews.swift:202
        CaseBar("그냥 둠", b, b / top, Theme.hold)   // ios: Views/InvestViews.swift:203
        T(e.text, style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/InvestViews.swift:204
    }
}
// ios: Views/InvestViews.swift:211,212
@Composable
private fun CaseBar(k: String, v: Double, w: Double, c: Color) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    T(k, Modifier.width(64.dp), style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/InvestViews.swift:213
    // ios: Views/InvestViews.swift:214,215,217
    BoxWithConstraints(Modifier.weight(1f).height(10.dp)) {
        Box(Modifier.width(max(3f, maxWidth.value * abs(w).toFloat()).dp).height(10.dp).background(c, RoundedCornerShape(3.dp)))
    }
    // ios: Views/InvestViews.swift:218
    T(AppModel.sgn(v), Modifier.width(62.dp), style = appFont(13f, FontWeight.Bold).copy(textAlign = TextAlign.End), color = if (v >= 0) Theme.up else Theme.down)
}

