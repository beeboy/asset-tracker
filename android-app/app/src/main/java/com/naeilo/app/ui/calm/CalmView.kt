package com.naeilo.app.ui.calm

import androidx.compose.foundation.background
import com.naeilo.app.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.core.calc.Ladder
import com.naeilo.app.core.calc.calmSpeaker
import com.naeilo.app.data.Friend
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.data.voice
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.LocalFg
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.analysis.AnalysisRoute
import com.naeilo.app.ui.analysis.detailTitle
import com.naeilo.app.ui.analysis.openOnTap
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.ui.plainClickable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// 평정 지수 (가위바위보 지수): 분석 탭 맨 위 카드 + 상세. 계산은 core/calc/Calm.kt
// 앱은 지수·내 인물·최근 기록만 짧게, 사건별 표와 추이 그래프는 두지 않는다.

/** 인물 상세로 가는 경로 (NavigationLink { CharacterDetailView(friend:, invest: true) } 자리). 분석 길(analysisPath)에 쌓는다 */
class CalmFriendRoute(val friend: Friend)

/** 습관 사다리를 다시 맞춘다 (Calm 은 값이라 고친 사본을 다시 넣어야 저장된다) */
private fun climbIfNeeded(m: AppModel) {
    if (m.calm.type != null && m.calm.ladder?.type != m.calm.type) { val c = m.calm.copy(); c.climb(); m.calm = c }
}

// ios: Views/CalmView.swift:6,7,9
@Composable
fun CalmCard(m: AppModel) {
    val s = remember(m.calm) { m.calm.stats() }; val who = m.calmSpeaker; val k = Calm.kind(m.calm.type)   // ios: Views/CalmView.swift:10
    LaunchedEffect(Unit) { climbIfNeeded(m) }   // ios: Views/CalmView.swift:36
    Fg(Color.White) {   // ios: Views/CalmView.swift:30
        Row(
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = tr("내 투자 습관 보기") }   // ios: Views/CalmView.swift:35
                .openOnTap(m, AnalysisRoute.calm)   // ios: Views/CalmView.swift:34
                .fillMaxWidth()   // ios: Views/CalmView.swift:32
                .background(Theme.night, RoundedCornerShape(20.dp))   // ios: Views/CalmView.swift:33
                .padding(horizontal = 14.dp, vertical = 14.dp),   // ios: Views/CalmView.swift:31
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/CalmView.swift:11
        ) {
            CharPixel(id = who, width = 42.dp, height = 60.dp, modifier = Modifier.charIdle(), model = m)   // ios: Views/CalmView.swift:12
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/CalmView.swift:13
                Row(Modifier.fillMaxWidth()) {   // ios: Views/CalmView.swift:14 firstTextBaseline
                    T("내 투자 습관", Modifier.alignByBaseline(), style = appFont(13f, FontWeight.Bold), color = Theme.mint)   // ios: Views/CalmView.swift:15
                    if (k != null) T("· ${k.title}", Modifier.padding(start = 8.dp).alignByBaseline(), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/CalmView.swift:16
                    Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:17
                    // ios: Views/CalmView.swift:18
                    T(if (s.n < Calm.minEvents) "–" else "${s.index}", Modifier.alignByBaseline(), style = appFont(28f, FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
                        roll = if (s.n < Calm.minEvents) "–" else "${s.index}")
                }
                // ios: Views/CalmView.swift:20,21
                T(m.calm.say(s, who = m.voice(who)), style = appFont(14f).ls(2f), color = hex(0xEEF0F3))
                // ios: Views/CalmView.swift:22,23
                T(Calm.meaning(s), style = appFont(12f).ls(2f), color = hex(0xC9D0D6))
                val l = m.calm.ladder; val p = m.calm.progress()   // ios: Views/CalmView.swift:24
                if (l != null && p != null) {
                    T("걸음 ${l.step}/3 · ${p.text}", style = appFont(12f), color = Theme.mint)   // ios: Views/CalmView.swift:25
                }
            }
        }
    }
}

/** 분석 넘기기의 '내 투자 습관' 화면. 인물 이름을 누르면 CalmFriendRoute 를 분석 길에 쌓는다 */
// ios: Views/CalmView.swift:40,41,44
@Composable
fun CalmView(m: AppModel) {
    var shown by remember { mutableStateOf(10) }   // ios: Views/CalmView.swift:42 최근 기록을 몇 건까지 펼쳤는지
    val s = remember(m.calm) { m.calm.stats() }; val who = m.calmSpeaker; val k = Calm.kind(m.calm.type)   // ios: Views/CalmView.swift:45
    val name = m.friendName(who)   // ios: Views/CalmView.swift:46
    val ready = s.n >= Calm.minEvents   // ios: Views/CalmView.swift:47
    LaunchedEffect(Unit) { climbIfNeeded(m) }   // ios: Views/CalmView.swift:179
    val go = { f: Friend -> m.analysisPath = m.analysisPath + CalmFriendRoute(f) }
    // ios: Views/CalmView.swift:48,178,180
    Box(Modifier.fillMaxSize().background(Theme.bg).detailTitle("내 투자 습관").verticalScroll(rememberScrollState())) {
        CompositionLocalProvider(LocalFg provides { it.ink }) {   // ios: Views/CalmView.swift:176 (색 벌을 따른다: 진한 카드 안은 어두운 모드 글자색)
            // ios: Views/CalmView.swift:49,175
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 상태 카드 하나 (진한 바탕): 모으는 중이면 진행, 정해졌으면 내 인물
                Card(dark = true) {   // ios: Views/CalmView.swift:51
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {   // ios: Views/CalmView.swift:52
                        CharPixel(id = who, width = 56.dp, height = 80.dp, modifier = Modifier.charIdle(3), model = m)   // ios: Views/CalmView.swift:53
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/CalmView.swift:54
                            val f = Shelter.friends.firstOrNull { it.id == m.calm.type }   // ios: Views/CalmView.swift:55
                            if (k != null && f != null) {
                                // ios: Views/CalmView.swift:56,59 (NavigationLink, plain 버튼)
                                T("${name} · ${k.title} ›", Modifier.plainClickable { go(f) }, style = appFont(17f, FontWeight.Bold))   // ios: Views/CalmView.swift:57
                                T(Calm.does[k.id] ?: k.like, style = appFont(13f), color = Theme.sub)   // ios: Views/CalmView.swift:60
                                T(m.calm.stageLine, style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:61
                            } else {   // ios: Views/CalmView.swift:62
                                T("아직 모으는 중", style = appFont(17f, FontWeight.Bold))   // ios: Views/CalmView.swift:63
                                // ios: Views/CalmView.swift:64,65
                                T("${Calm.minEvents}건 모이면 닮은 인물 공개", style = appFont(13f), color = Theme.sub)
                            }
                        }
                    }
                    if (!ready) {   // ios: Views/CalmView.swift:69
                        // 진행 바는 기록 수 (지수가 아니다: 기록이 적으면 지수는 50 근처라 막대가 절반 넘게 찼다)
                        ProgressBar(value = s.n.toDouble() / Calm.minEvents.toDouble(), fill = Theme.mint, track = Theme.track)   // ios: Views/CalmView.swift:71
                        T("매매 기록 ${s.n} / ${Calm.minEvents}건", style = appFont(12f, FontWeight.SemiBold), color = Theme.mint)   // ios: Views/CalmView.swift:72
                    } else {   // ios: Views/CalmView.swift:73
                        ProgressBar(value = s.index.toDouble() / 100, fill = Theme.mint, track = Theme.track)   // ios: Views/CalmView.swift:74
                        // ios: Views/CalmView.swift:75,76
                        T("평정 지수 ${s.index} / 100 · ${Calm.meaning(s)}", style = appFont(12f), color = Theme.sub)
                    }
                    HDivider(color = Theme.border)   // ios: Views/CalmView.swift:78
                    // ios: Views/CalmView.swift:79,80
                    T("${name}: “${m.calm.say(s, who = m.voice(who))}”", style = appFont(14f).ls(3f))
                    val w = Calm.why(m.calm.type, s)   // ios: Views/CalmView.swift:81
                    if (w != null) {
                        T(w, style = appFont(12f).ls(2f), color = Theme.muted)   // ios: Views/CalmView.swift:82
                    }
                    // ios: Views/CalmView.swift:84
                    val pt = m.calm.prevType; val pk = pt?.let { Calm.kind(it) }; val pn = pt?.let { p -> Shelter.friends.firstOrNull { it.id == p }?.name }
                    if (pk != null && pn != null) {
                        T("지난달 ${pn}(${pk.title})에서 옮겨 왔어요.", style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:85
                    }
                }

                // 뭐예요
                Card {   // ios: Views/CalmView.swift:90
                    T("평정 지수란?", style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:91
                    // ios: Views/CalmView.swift:92,93
                    TA(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("규칙") }; append("대로 하면 점수 ↑ · 물타기 0점") },
                        style = appFont(13f).ls(2f), color = Theme.sub)
                    val sg = if (s.weekPts < 0) "−" else ""
                    T("이번 주 ${sg}${abs(s.weekPts)}점 · 주 최대 ${Calm.weekCap}점", style = appFont(12f), color = Theme.muted)   // Views/CalmView.swift:94 (검사 예외: 따옴표 겹침)
                }

                // 네 가지 (10건 전에는 0% 대신 —)
                Card {   // ios: Views/CalmView.swift:98
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/CalmView.swift:99 firstTextBaseline
                        T("내 매매 습관", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:100
                        Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:101
                        if (!ready) T("${Calm.minEvents}건부터", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:102
                    }
                    StatRow("오른 뒤 추가 매수", AppModel.dash(AppModel.pct(s.chase), ready = ready))   // ios: Views/CalmView.swift:104
                    StatRow("내린 뒤 매도", AppModel.dash(AppModel.pct(s.switchR), ready = ready))   // ios: Views/CalmView.swift:105
                    StatRow("급변일 기다림", AppModel.dash(AppModel.pct(s.waitR), ready = ready))   // ios: Views/CalmView.swift:106
                    StatRow("규칙대로 매매", AppModel.dash(AppModel.pct(s.ruleR), ready = ready))   // ios: Views/CalmView.swift:107
                    val t = s.trend; if (ready && t != null) StatRow("최근 30일 변화", (if (t >= 0) "+" else "") + "$t")   // ios: Views/CalmView.swift:108
                }

                // 최근 기록
                Card {   // ios: Views/CalmView.swift:112
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/CalmView.swift:113 firstTextBaseline
                        T("최근 기록", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:114
                        Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:115
                        T("${m.calm.events.size}건", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:116
                    }
                    m.calm.events.takeLast(shown).reversed().forEach { e ->   // ios: Views/CalmView.swift:118
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {   // ios: Views/CalmView.swift:119
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/CalmView.swift:120
                                T("${e.sym} · ${e.label}", style = appFont(13f))   // ios: Views/CalmView.swift:121
                                // ios: Views/CalmView.swift:122,123
                                T(if (Calm.reason(e.act).isEmpty()) Day.md(e.day) else "${Day.md(e.day)} · ${Calm.reason(e.act)}", style = appFont(12f), color = Theme.muted)
                            }
                            Spacer(Modifier.padding(start = 8.dp))   // ios: Views/CalmView.swift:125
                            // ios: Views/CalmView.swift:126
                            T(if (e.pts == 0) "0" else (if (e.pts > 0) "+" else "") + "${e.pts}", style = appFont(13f, FontWeight.Bold),
                                color = if (e.pts > 0) Theme.teal else if (e.pts < 0) Theme.up else Theme.muted)   // ios: Views/CalmView.swift:127
                        }
                    }
                    if (m.calm.events.size > shown) {   // ios: Views/CalmView.swift:130
                        // ios: Views/CalmView.swift:131,132 (Button)
                        T("더 보기 (${m.calm.events.size - shown}건 남음)", Modifier.plainClickable { shown += 20 }, style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)
                    }
                    // 손으로 남기는 기록은 없다: 수량 고치기(더 샀어요·팔았어요)와 크게 움직인 날 앱 열기로 생긴다
                    T("수량 바꾸면 기록", style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:135
                }

                // 다섯 인물 (행동으로 설명)
                Card {   // ios: Views/CalmView.swift:139
                    T("닮을 인물", style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:140
                    Calm.kinds.forEach { kd ->   // ios: Views/CalmView.swift:141
                        val on = kd.id == m.calm.type   // ios: Views/CalmView.swift:142
                        // ios: Views/CalmView.swift:143,144 (NavigationLink: 인물이 있으면 인물 상세)
                        val f = Shelter.friends.firstOrNull { it.id == kd.id }
                        // ios: Views/CalmView.swift:145,146,156,157,159 (label: plain 버튼, 줄 전체를 누른다)
                        Row(Modifier.fillMaxWidth().then(if (f != null) Modifier.plainClickable { go(f) } else Modifier).defaultMinSize(minHeight = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            PixelN("spr_" + kd.id, 21.dp, 30.dp)   // ios: Views/CalmView.swift:147
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/CalmView.swift:148
                                // ios: Views/CalmView.swift:149
                                T((Shelter.friends.firstOrNull { it.id == kd.id }?.name ?: "") + " · " + kd.title, style = appFont(14f, if (on) FontWeight.Bold else FontWeight.SemiBold),
                                    color = if (on) Theme.teal else Theme.ink)   // ios: Views/CalmView.swift:150
                                T(Calm.does[kd.id] ?: kd.`when`, style = appFont(12f), color = Theme.sub)   // ios: Views/CalmView.swift:151
                            }
                            Spacer(Modifier.widthIn(min = 0.dp))   // ios: Views/CalmView.swift:153
                            T("›", style = appFont(14f, FontWeight.Bold), color = Theme.muted)   // ios: Views/CalmView.swift:154
                        }
                    }
                }

                // 다음 걸음 (잠겼으면 점선 상자)
                val l = m.calm.ladder; val steps = l?.let { Calm.steps[it.type] }   // ios: Views/CalmView.swift:164
                if (l != null && steps != null) LadderCard(m, l, steps)
                else {   // ios: Views/CalmView.swift:165
                    val dash = Theme.dash
                    T("인물 정해지면 다음 걸음 3개",   // ios: Views/CalmView.swift:166
                        // ios: Views/CalmView.swift:167,168,169 (선은 가장자리 가운데에: SwiftUI stroke 와 같게)
                        Modifier.fillMaxWidth().drawBehind {
                            drawRoundRect(dash, cornerRadius = CornerRadius(18.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                        }.padding(16.dp),
                        style = appFont(13f), color = Theme.sub)
                }

                // ios: Views/CalmView.swift:172,173
                T("매달 갱신 · 최근 ${Calm.keep}건 기준", style = appFont(12f).ls(2f), color = Theme.muted)
            }
        }
    }
}

/** 지금 유형의 걸음 3개: 완료한 날, 지금 걸음 진행. 인물 순위는 보여 주지 않는다 */
// ios: Views/CalmView.swift:184
@Composable
private fun LadderCard(m: AppModel, l: Ladder, steps: List<Calm.Step>) {
    val p = m.calm.progress()   // ios: Views/CalmView.swift:185
    Card {   // ios: Views/CalmView.swift:186
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/CalmView.swift:187 firstTextBaseline
            T("다음 걸음", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:188
            Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:189
            T(if (l.step > 3) "다 올랐어요" else "${l.step}/3", Modifier.alignByBaseline(), style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/CalmView.swift:190
        }
        steps.forEachIndexed { i, st ->   // ios: Views/CalmView.swift:192
            val n = i + 1; val on = n == l.step; val did = n < l.step   // ios: Views/CalmView.swift:193
            val day = l.done.lastOrNull { it.type == l.type && it.step == n }?.day   // ios: Views/CalmView.swift:194
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {   // ios: Views/CalmView.swift:195
                // ios: Views/CalmView.swift:196,197
                Sym(if (did) "checkmark.circle.fill" else if (on) "circle.dotted" else "circle", 17f, if (did || on) Theme.teal else Theme.muted)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {   // ios: Views/CalmView.swift:198
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/CalmView.swift:199
                        T(st.name, style = appFont(14f, if (on) FontWeight.Bold else FontWeight.SemiBold), color = if (did) Theme.sub else Theme.ink)   // ios: Views/CalmView.swift:200
                        Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:201
                        if (did && day != null) T(Day.md(day), style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:202
                    }
                    T(st.what, style = appFont(12f), color = Theme.sub)   // ios: Views/CalmView.swift:204
                    if (on && p != null) {   // ios: Views/CalmView.swift:205
                        ProgressBar(value = min(1.0, p.cur.toDouble() / max(1, p.goal).toDouble()), fill = Theme.teal, track = Theme.track)   // ios: Views/CalmView.swift:206
                        T(p.text, style = appFont(12f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/CalmView.swift:207
                    }
                }
            }
        }
        if (l.step > 3) {   // ios: Views/CalmView.swift:212
            // ios: Views/CalmView.swift:213,214
            T(if (l.type == "seri") "지금처럼 지켜요. 여섯 달마다 기록해 둘게요." else "유형은 한 달에 한 번 다시 봐요. 그때 새 걸음이 열려요.", style = appFont(12f), color = Theme.muted)
        }
    }
}

// ios: Views/CalmView.swift:219
@Composable
private fun StatRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/CalmView.swift:220
        T(k, style = appFont(14f), color = Theme.sub)   // ios: Views/CalmView.swift:221
        Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:222
        T(v, style = appFont(14f, FontWeight.SemiBold))   // ios: Views/CalmView.swift:223
    }
}
