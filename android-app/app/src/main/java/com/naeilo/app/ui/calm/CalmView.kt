package com.naeilo.app.ui.calm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.naeilo.app.core.calc.CalmEvent.Act
import com.naeilo.app.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
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
import com.naeilo.app.ui.LocalFg
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
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

/** 분석 넘기기의 투자 습관 화면 (결과에 끌려 사고팔까?). 인물을 누르면 CalmFriendRoute 를 분석 길에 쌓는다 */
// ios: Views/CalmView.swift:40,41,51
@Composable
fun CalmView(m: AppModel) {
    var shown by remember { mutableStateOf(10) }   // ios: Views/CalmView.swift:42 최근 기록을 몇 건까지 펼쳤는지
    var recentOpen by remember { mutableStateOf(false) }   // ios: Views/CalmView.swift:43 최근 기록 목록은 접어 둔다 (2026-10-21)
    val s = remember(m.calm) { m.calm.stats() }; val who = m.calmSpeaker; val k = Calm.kind(m.calm.type)   // ios: Views/CalmView.swift:52
    val name = m.friendName(who)   // ios: Views/CalmView.swift:53
    val ready = s.n >= Calm.minEvents   // ios: Views/CalmView.swift:54
    LaunchedEffect(Unit) { climbIfNeeded(m) }   // ios: Views/CalmView.swift:194
    val go = { f: Friend -> m.analysisPath = m.analysisPath + CalmFriendRoute(f) }
    // ios: Views/CalmView.swift:55,193,195
    Box(Modifier.fillMaxSize().background(Theme.bg).detailTitle("결과에 끌려 사고팔까?").verticalScroll(rememberScrollState())) {
        CompositionLocalProvider(LocalFg provides { it.ink }) {   // ios: Views/CalmView.swift:191 (색 벌을 따른다: 진한 카드 안은 어두운 모드 글자색)
            // ios: Views/CalmView.swift:56,190
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 지수 하나를 크게 (진한 바탕). 모으는 중이면 기록 수 (기록이 적으면 지수는 50 근처라 막대로 보이지 않는다)
                Card(dark = true) {   // ios: Views/CalmView.swift:58
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/CalmView.swift:59
                        CharPixel(id = who, width = 56.dp, height = 80.dp, modifier = Modifier.charIdle(3), model = m)   // ios: Views/CalmView.swift:60
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/CalmView.swift:61
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/CalmView.swift:62 firstTextBaseline
                                val big = if (ready) "${s.index}" else "${s.n}"
                                T(big, Modifier.alignByBaseline(), style = appFont(40f, FontWeight.ExtraBold).copy(letterSpacing = (-1).sp), roll = big)   // ios: Views/CalmView.swift:63
                                T(if (ready) "/ 100" else "/ ${Calm.minEvents}", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.SemiBold), color = Theme.muted)   // ios: Views/CalmView.swift:64
                            }
                            ProgressBar(value = if (ready) s.index.toDouble() / 100 else s.n.toDouble() / Calm.minEvents.toDouble(), fill = Theme.mint, track = Theme.track)   // ios: Views/CalmView.swift:66
                            Row(Modifier.fillMaxWidth()) {   // ios: Views/CalmView.swift:67,72
                                T(if (ready) "끌림" else "매매 기록", style = appFont(11f), color = Theme.muted)   // ios: Views/CalmView.swift:68
                                Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:69
                                T(if (ready) "규칙대로" else "모이면 공개", style = appFont(11f), color = Theme.muted)   // ios: Views/CalmView.swift:70
                            }
                        }
                    }
                    val f = Shelter.friends.firstOrNull { it.id == m.calm.type }   // ios: Views/CalmView.swift:75
                    if (k != null && f != null) {
                        // ios: Views/CalmView.swift:76,79 (NavigationLink, plain 버튼)
                        T("${name} · ${k.title} ›", Modifier.plainClickable { go(f) }, style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:77
                    }
                    // ios: Views/CalmView.swift:81,82
                    T("“${m.calm.say(s, who = m.voice(who))}”", style = appFont(13f).ls(2f), color = Theme.mint)
                }

                // 네 가지 습관을 막대로. 앞의 둘은 60% 점선을 넘으면 앞서 달리는 형 (10건 전에는 빈 막대와 —)
                Card {   // ios: Views/CalmView.swift:86
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/CalmView.swift:87 firstTextBaseline
                        T("무엇이 끌렸나", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:88
                        Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:89
                        T(if (ready) "매매 ${s.trades}건" else "${Calm.minEvents}건부터", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:90
                    }
                    Bar("오르면 더 삼", s.chase, ready, Theme.orange, line = true)   // ios: Views/CalmView.swift:92
                    Bar("내리면 팖", s.switchR, ready, Theme.orange.copy(alpha = 0.45f), line = true)   // ios: Views/CalmView.swift:93
                    Bar("급변일 기다림", s.waitR, ready, Theme.teal)   // ios: Views/CalmView.swift:94
                    Bar("규칙대로", s.ruleR, ready, Theme.teal)   // ios: Views/CalmView.swift:95
                    // ios: Views/CalmView.swift:97 (96 은 검사 예외: 보간 속 따옴표)
                    T("┆ 60% 넘으면 ${Calm.kind("ir")?.title ?: ""}", Modifier.fillMaxWidth(), style = appFont(11f).copy(textAlign = TextAlign.End), color = Theme.orange)
                }

                // 다음 걸음 (잠겼으면 점선 상자)
                val l = m.calm.ladder; val steps = l?.let { Calm.steps[it.type] }   // ios: Views/CalmView.swift:101
                if (l != null && steps != null) LadderCard(m, l, steps)
                else {   // ios: Views/CalmView.swift:102
                    val dash = Theme.dash
                    T("인물 정해지면 다음 걸음 3개",   // ios: Views/CalmView.swift:103
                        // ios: Views/CalmView.swift:104,105,106 (선은 가장자리 가운데에: SwiftUI stroke 와 같게)
                        Modifier.fillMaxWidth().drawBehind {
                            drawRoundRect(dash, cornerRadius = CornerRadius(18.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                        }.padding(16.dp),
                        style = appFont(13f), color = Theme.sub)
                }

                // 최근 기록: 칸 색으로 한눈에, 머리를 누르면 목록을 펼친다
                Card {   // ios: Views/CalmView.swift:110
                    Row(Modifier.fillMaxWidth().plainClickable { recentOpen = !recentOpen }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/CalmView.swift:111,112,118,120 firstTextBaseline
                        T("최근 기록", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:113
                        Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:114
                        T("${m.calm.events.size}건", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:115
                        Sym(if (recentOpen) "chevron.up" else "chevron.down", 11f, Theme.muted)   // ios: Views/CalmView.swift:116
                    }
                    if (m.calm.events.isNotEmpty()) {   // ios: Views/CalmView.swift:121
                        val ev = m.calm.events.takeLast(28)   // ios: Views/CalmView.swift:122
                        // ios: Views/CalmView.swift:123,132
                        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            (0 until ev.size step 14).forEach { i ->   // ios: Views/CalmView.swift:124
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {   // ios: Views/CalmView.swift:125
                                    ev.subList(i, min(i + 14, ev.size)).forEach { e ->   // ios: Views/CalmView.swift:126
                                        Box(Modifier.size(width = 16.dp, height = 16.dp).background(CalmView.tint(e.act), RoundedCornerShape(4.dp)))   // ios: Views/CalmView.swift:127
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/CalmView.swift:133
                            Legend(CalmView.tint(Act.chase), "더 삼")   // ios: Views/CalmView.swift:134
                            Legend(CalmView.tint(Act.switchOut), "팖")   // ios: Views/CalmView.swift:135
                            Legend(CalmView.tint(Act.wait), "기다림·규칙")   // ios: Views/CalmView.swift:136
                            Legend(CalmView.tint(Act.fresh), "새 종목")   // ios: Views/CalmView.swift:137
                        }
                    }
                    if (recentOpen) {   // ios: Views/CalmView.swift:140
                    m.calm.events.takeLast(shown).reversed().forEach { e ->   // ios: Views/CalmView.swift:141
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {   // ios: Views/CalmView.swift:142
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/CalmView.swift:143
                                T("${e.sym} · ${e.label}", style = appFont(13f))   // ios: Views/CalmView.swift:144
                                // ios: Views/CalmView.swift:145,146
                                T(if (Calm.reason(e.act).isEmpty()) Day.md(e.day) else "${Day.md(e.day)} · ${Calm.reason(e.act)}", style = appFont(12f), color = Theme.muted)
                            }
                            Spacer(Modifier.padding(start = 8.dp))   // ios: Views/CalmView.swift:148
                            // ios: Views/CalmView.swift:149
                            T(if (e.pts == 0) "0" else (if (e.pts > 0) "+" else "") + "${e.pts}", style = appFont(13f, FontWeight.Bold),
                                color = if (e.pts > 0) Theme.teal else if (e.pts < 0) Theme.up else Theme.muted)   // ios: Views/CalmView.swift:150
                        }
                    }
                    if (m.calm.events.size > shown) {   // ios: Views/CalmView.swift:153
                        // ios: Views/CalmView.swift:154,155 (Button)
                        T("더 보기 (${m.calm.events.size - shown}건 남음)", Modifier.plainClickable { shown += 20 }, style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)
                    }
                    // 손으로 남기는 기록은 없다: 수량 고치기(더 샀어요·팔았어요)와 크게 움직인 날 앱 열기로 생긴다
                    T("수량 바꾸면 기록", style = appFont(12f), color = Theme.muted)   // ios: Views/CalmView.swift:158
                    }
                }

                com.naeilo.app.ui.MyRecordCard(m)   // ios: Views/CalmView.swift:162 종목 탭 맨 아래에서 옮겼다 (2026-10-21)

                // 다섯 인물: 가로 한 줄, 지금 인물은 테두리
                Card {   // ios: Views/CalmView.swift:165
                    T("닮을 인물", style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:166
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.Top) {   // ios: Views/CalmView.swift:167
                        Calm.kinds.forEach { kd ->   // ios: Views/CalmView.swift:168
                            val on = kd.id == m.calm.type   // ios: Views/CalmView.swift:169
                            // ios: Views/CalmView.swift:170,171 (NavigationLink: 인물이 있으면 인물 상세)
                            val fr = Shelter.friends.firstOrNull { it.id == kd.id }
                            // ios: Views/CalmView.swift:172,173,181,182,184,185 (label: plain 버튼)
                            Column(Modifier.weight(1f).then(if (fr != null) Modifier.plainClickable { go(fr) } else Modifier)
                                .semantics(mergeDescendants = true) { contentDescription = (fr?.name ?: "") + " · " + kd.title },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // ios: Views/CalmView.swift:174,175,176,177
                                Box(Modifier.size(width = 44.dp, height = 44.dp).background(if (on) Theme.teal.copy(alpha = 0.12f) else Theme.line, CircleShape)
                                    .then(if (on) Modifier.border(2.dp, Theme.teal, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                                    PixelN("spr_" + kd.id, 21.dp, 30.dp)
                                }
                                // ios: Views/CalmView.swift:178,179
                                T(CalmView.short[kd.id] ?: kd.title, style = appFont(11f, if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) Theme.teal else Theme.sub, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ios: Views/CalmView.swift:40 (static 은 같은 이름 object 에)
object CalmView {
    /** 다섯 인물을 가로 한 줄에 둘 때 짧은 이름 */
    // ios: Views/CalmView.swift:46,47,48
    val short: Map<String, String> = mapOf("ir" to "앞서 달림", "sua" to "자람", "seonbae" to "되물음", "sio" to "기다림", "seri" to "원칙")

    /** 최근 기록 칸 색: 결과 뒤 산 것·판 것·기다림과 규칙·새 종목 */
    // ios: Views/CalmView.swift:261,262
    @Composable
    fun tint(a: Act): Color = when (a) {
        Act.chase, Act.avgDown -> Theme.orange   // ios: Views/CalmView.swift:263
        Act.switchOut, Act.trim -> Theme.orange.copy(alpha = 0.45f)   // ios: Views/CalmView.swift:264
        Act.rule, Act.wait -> Theme.teal   // ios: Views/CalmView.swift:265
        Act.fresh -> Theme.muted.copy(alpha = 0.4f)   // ios: Views/CalmView.swift:266
    }
}

/** 지금 유형의 걸음 3개를 가로 한 줄 칸으로: 완료한 날, 지금 걸음 진행. 인물 순위는 보여 주지 않는다 */
// ios: Views/CalmView.swift:199
@Composable
private fun LadderCard(m: AppModel, l: Ladder, steps: List<Calm.Step>) {
    val p = m.calm.progress()   // ios: Views/CalmView.swift:200
    Card {   // ios: Views/CalmView.swift:201
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/CalmView.swift:202 firstTextBaseline
            T("다음 걸음은?", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/CalmView.swift:203
            Spacer(Modifier.weight(1f))   // ios: Views/CalmView.swift:204
            T(if (l.step > 3) "다 올랐어요" else "${l.step}/3", Modifier.alignByBaseline(), style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/CalmView.swift:205
        }
        // ios: Views/CalmView.swift:207,227 (칸 높이를 맞춘다)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, st ->   // ios: Views/CalmView.swift:208
                val n = i + 1; val on = n == l.step; val did = n < l.step   // ios: Views/CalmView.swift:209
                val day = l.done.lastOrNull { it.type == l.type && it.step == n }?.day   // ios: Views/CalmView.swift:210
                // ios: Views/CalmView.swift:211,220,221,222,223,224
                Column(Modifier.weight(1f).fillMaxHeight()
                    .background(if (on) Theme.teal.copy(alpha = 0.1f) else Theme.line, RoundedCornerShape(10.dp))
                    .then(if (on) Modifier.border(2.dp, Theme.teal, RoundedCornerShape(10.dp)) else Modifier)
                    .semantics(mergeDescendants = true) { stateDescription = st.what }
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // ios: Views/CalmView.swift:212,213
                    T(if (did) "✓ " + st.name else st.name, style = appFont(13f, FontWeight.Bold), color = if (did) Theme.sub else Theme.ink)
                    if (did && day != null) T(Day.md(day), style = appFont(11f), color = Theme.muted)   // ios: Views/CalmView.swift:214
                    if (on && p != null) {   // ios: Views/CalmView.swift:215
                        T(p.text, style = appFont(11f), color = Theme.sub)   // ios: Views/CalmView.swift:216
                        ProgressBar(value = min(1.0, p.cur.toDouble() / max(1, p.goal).toDouble()), fill = Theme.teal, track = Theme.track)   // ios: Views/CalmView.swift:217
                    }
                }
            }
        }
        if (l.step > 3) {   // ios: Views/CalmView.swift:228
            // ios: Views/CalmView.swift:229,230
            T(if (l.type == "seri") "지금처럼 지켜요. 여섯 달마다 기록해 둘게요." else "유형은 한 달에 한 번 다시 봐요. 그때 새 걸음이 열려요.", style = appFont(12f), color = Theme.muted)
        }
    }
}

/** 습관 하나를 막대로. line 이면 60% 자리(앞서 달리는 형 기준)에 점선 */
// ios: Views/CalmView.swift:236
@Composable
private fun Bar(k: String, v: Double, ready: Boolean, c: Color, line: Boolean = false) {
    val txt = AppModel.dash(AppModel.pct(v), ready = ready)   // ios: Views/CalmView.swift:237
    val orange = Theme.orange; val track = Theme.line
    // ios: Views/CalmView.swift:238,257
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = tr(k) + " " + txt }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(k, Modifier.width(92.dp), style = appFont(13f), color = Theme.sub, maxLines = 1)   // ios: Views/CalmView.swift:239
        // ios: Views/CalmView.swift:240,241,242,243,244,245,246,247,249,253
        Box(Modifier.weight(1f).height(14.dp).drawBehind {
            val r = CornerRadius(4.dp.toPx())
            drawRoundRect(track, cornerRadius = r)
            if (ready) drawRoundRect(c, size = size.copy(width = size.width * v.coerceIn(0.0, 1.0).toFloat()), cornerRadius = r)
            if (line) {
                val x = size.width * 0.6f
                drawLine(orange, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
            }
        })
        // ios: Views/CalmView.swift:254,255
        T(txt, Modifier.width(40.dp), style = appFont(13f, FontWeight.Bold).copy(textAlign = TextAlign.End), color = if (ready && line && v >= 0.6) Theme.orange else Theme.ink)
    }
}

// ios: Views/CalmView.swift:270
@Composable
private fun Legend(c: Color, t: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/CalmView.swift:271
        Box(Modifier.size(width = 9.dp, height = 9.dp).background(c, RoundedCornerShape(2.dp)))   // ios: Views/CalmView.swift:272
        T(t, style = appFont(11f), color = Theme.muted)   // ios: Views/CalmView.swift:273
    }
}
