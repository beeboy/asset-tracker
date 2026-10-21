package com.naeilo.app.ui.remind

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.data.Remind
import com.naeilo.app.data.RemindCat
import com.naeilo.app.data.RemindGo
import com.naeilo.app.data.RemindItem
import com.naeilo.app.data.color
import com.naeilo.app.data.go
import com.naeilo.app.ui.wave.GameHeader
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.ToggleRow
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable
import java.time.Instant

// 소식함: 홈 말풍선의 '새 소식 n' 또는 노란 띠를 누르면 열린다.
// 칩 5개(전체·할 일·일정·증시 요약·지난 소식) → 지금 해 주면 좋아요 → 진행 중 → 다가오는 일정 → 증시 요약 → 지난 30일
// 부르는 쪽(홈)이 Sheet 안에 넣는다. dismiss = 시트 닫기 (홈의 inbox = false · 모두 읽음)
// ios: Views/RemindViews.swift:8
private enum class Tab { all, todo, event, market, past }

// ios: Views/RemindViews.swift:5
@Composable
fun RemindSheet(
    m: AppModel,   // ios: Views/RemindViews.swift:6
    dismiss: () -> Unit,   // ios: Views/RemindViews.swift:7
) {
    var tab by remember { mutableStateOf(Tab.all) }   // ios: Views/RemindViews.swift:9
    // ios: Views/RemindViews.swift:11
    val r = Remind.shared   // ios: Views/RemindViews.swift:12
    val todo = (r.fresh + r.doing + r.past).filter { it.isTodo }   // ios: Views/RemindViews.swift:13
    val top = todo.firstOrNull { it.action != null }   // ios: Views/RemindViews.swift:14
    val doing = todo.filter { it.id != top?.id }   // ios: Views/RemindViews.swift:15
    val events = r.events   // ios: Views/RemindViews.swift:16
    val market = (r.fresh + r.past).filter { !it.isTodo && it.cat == RemindCat.market }   // ios: Views/RemindViews.swift:17
    val past = (r.fresh + r.past).filter { !it.isTodo && it.cat != RemindCat.market }   // ios: Views/RemindViews.swift:18
    val showTodo = tab == Tab.all || tab == Tab.todo; val showEv = tab == Tab.all || tab == Tab.event; val showPast = tab == Tab.all || tab == Tab.past   // ios: Views/RemindViews.swift:19
    val showMk = tab == Tab.all || tab == Tab.market   // ios: Views/RemindViews.swift:20
    // 소식을 누르면 시트를 닫고 그 화면으로
    // ios: Views/RemindViews.swift:84
    val open: (RemindItem) -> Unit = { i ->
        if (i.go != RemindGo.nowhere) {   // ios: Views/RemindViews.swift:85
            dismiss()   // ios: Views/RemindViews.swift:86
            m.go(i.go)   // ios: Views/RemindViews.swift:87
        }
    }
    // ios: Views/RemindViews.swift:21,22,58 NavigationStack 은 이 판 하나라 쌓지 않는다
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        // ios: Views/RemindViews.swift:59,61 이동 막대 없음 · 시트는 큰 크기 (Sheet 기본)
        val maxW = Theme.maxWidth
        // ios: Views/RemindViews.swift:23
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = maxW).fillMaxWidth()   // ios: Views/RemindViews.swift:55,56
                .padding(16.dp),   // ios: Views/RemindViews.swift:54
        ) {
            GameHeader(title = "소식", dismiss = dismiss)   // ios: Views/RemindViews.swift:24 게임·동네 창과 같은 맨 위 줄: 왼쪽 제목, 오른쪽 X
            // ios: Views/RemindViews.swift:25
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),   // ios: Views/RemindViews.swift:26
            ) {
                Chip(Tab.all, "전체", tab) { tab = it }   // ios: Views/RemindViews.swift:27
                Chip(Tab.todo, if (todo.isEmpty()) "할 일" else "할 일 ${todo.size}", tab) { tab = it }   // ios: Views/RemindViews.swift:28
                Chip(Tab.event, "일정", tab) { tab = it }   // ios: Views/RemindViews.swift:29
                Chip(Tab.past, "지난 소식", tab) { tab = it }   // ios: Views/RemindViews.swift:30
            }
            if (showTodo && top != null) ActionCard(top, open)   // ios: Views/RemindViews.swift:33
            // ios: Views/RemindViews.swift:34
            if (showTodo && doing.isNotEmpty()) {
                Section("진행 중") { doing.forEach { i -> if (i.checks == null) RemindRow(m, i, dismiss) else CheckCard(i, open) } }   // ios: Views/RemindViews.swift:35
            }
            // ios: Views/RemindViews.swift:37
            if (showEv && events.isNotEmpty()) {
                Section("다가오는 일정") { events.forEach { EventRow(it, open) } }   // ios: Views/RemindViews.swift:38
                T("날짜는 하루 이틀 바뀔 수 있어요.", style = appFont(12f), color = Theme.muted)   // ios: Views/RemindViews.swift:39
            }
            // ios: Views/RemindViews.swift:41
            if (showMk && market.isNotEmpty()) {
                Section("증시 요약") { market.forEach { PastRow(it, open) } }   // ios: Views/RemindViews.swift:42
            }
            // ios: Views/RemindViews.swift:44
            if (showPast && past.isNotEmpty()) {
                Section("지난 30일") { past.forEach { PastRow(it, open) } }   // ios: Views/RemindViews.swift:45
            }
            // ios: Views/RemindViews.swift:47
            if ((if (showTodo) todo.size else 0) + (if (showEv) events.size else 0) + (if (showMk) market.size else 0) + (if (showPast) past.size else 0) == 0) {
                T("아직 소식이 없어요.", Modifier.padding(vertical = 20.dp), style = appFont(14f), color = Theme.sub)   // ios: Views/RemindViews.swift:48
            }
            // ios: Views/RemindViews.swift:50
            if (showPast && past.any { it.cat == RemindCat.analysis }) {
                T("분석 소식은 내 숫자로 계산한 값이에요. 종목 추천이 아닙니다.", style = appFont(12f), color = Theme.muted)   // ios: Views/RemindViews.swift:51
            }
        }
    }
}

/** RemindSheet.ago 같은 정적 도움 (Swift static func) */
object RemindSheet {
    // ios: Views/RemindViews.swift:209
    fun ago(t: Double): String {
        val d = Day.str(Instant.ofEpochMilli((t * 1000).toLong())); val n = -Day.dday(d)   // ios: Views/RemindViews.swift:210
        return if (n <= 0) "오늘" else if (n == 1) "어제" else Day.md(d)   // ios: Views/RemindViews.swift:211
    }
}

// ios: Views/RemindViews.swift:64
@Composable
private fun Chip(t: Tab, label: String, tab: Tab, pick: (Tab) -> Unit) {
    val sel = tab == t   // ios: Views/RemindViews.swift:65
    // ios: Views/RemindViews.swift:66,73 (buttonStyle plain)
    Box(
        Modifier.plainClickable { pick(t) }
            .semantics { selected = sel }   // ios: Views/RemindViews.swift:74
            .background(if (sel) Theme.teal else Theme.card, CapsuleShape)   // ios: Views/RemindViews.swift:70
            .border(1.dp, if (sel) Color.Transparent else Theme.border, CapsuleShape)   // ios: Views/RemindViews.swift:71
            .defaultMinSize(minHeight = 34.dp).padding(horizontal = 12.dp),   // ios: Views/RemindViews.swift:69
        contentAlignment = Alignment.Center,
    ) {
        T(label, style = appFont(13f, FontWeight.SemiBold),   // ios: Views/RemindViews.swift:67
          color = if (sel) Color.White else Theme.sub, maxLines = 1)   // ios: Views/RemindViews.swift:68
    }
}

// ios: Views/RemindViews.swift:77
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    // ios: Views/RemindViews.swift:78
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(title, style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/RemindViews.swift:79
        content()   // ios: Views/RemindViews.swift:80
    }
}

/** 지금 해 주면 좋아요: 진한 초록 테두리, 이유 한 줄, 버튼 */
// ios: Views/RemindViews.swift:91
@Composable
private fun ActionCard(i: RemindItem, open: (RemindItem) -> Unit) {
    // ios: Views/RemindViews.swift:92
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()   // ios: Views/RemindViews.swift:104
            .background(Theme.card, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:105
            .border(2.dp, Theme.green, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:106
            .padding(14.dp),   // ios: Views/RemindViews.swift:103
    ) {
        T("지금 해 주면 좋아요", style = appFont(12f, FontWeight.Bold), color = Theme.green)   // ios: Views/RemindViews.swift:93
        T(i.text, style = appFont(16f, FontWeight.Bold), color = Theme.ink)   // ios: Views/RemindViews.swift:94
        i.why?.let { w -> T(w, style = appFont(13f), color = Theme.sub) }   // ios: Views/RemindViews.swift:95
        // ios: Views/RemindViews.swift:96,101
        Box(
            Modifier.plainClickable { open(i) }
                .fillMaxWidth().defaultMinSize(minHeight = 44.dp)   // ios: Views/RemindViews.swift:98
                .background(Theme.green, RoundedCornerShape(12.dp)),   // ios: Views/RemindViews.swift:99
            contentAlignment = Alignment.Center,
        ) {
            T(i.action ?: "열기", style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Views/RemindViews.swift:97
        }
    }
}

/** 체크리스트 카드 (내 투자 캐릭터 찾기) */
// ios: Views/RemindViews.swift:110
@Composable
private fun CheckCard(i: RemindItem, open: (RemindItem) -> Unit) {
    val c = i.checks ?: emptyList(); val n = c.count { it.ok }   // ios: Views/RemindViews.swift:111
    // ios: Views/RemindViews.swift:112,129,131 (줄 전체를 누른다)
    Column(
        Modifier.fillMaxWidth().plainClickable { open(i) }
            .background(Theme.card, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:127
            .border(1.dp, Theme.border, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:128
            .padding(12.dp),   // ios: Views/RemindViews.swift:125,126
        verticalArrangement = Arrangement.spacedBy(8.dp),   // ios: Views/RemindViews.swift:113
    ) {
        T(i.text, style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/RemindViews.swift:114
        ProgressBar(value = n.toDouble() / maxOf(c.size, 1).toDouble(), height = 4.dp, fill = Theme.purple, track = Theme.track)   // ios: Views/RemindViews.swift:115
        // ios: Views/RemindViews.swift:116
        c.indices.forEach { k ->
            // ios: Views/RemindViews.swift:117
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym(if (c[k].ok) "checkmark.circle.fill" else "circle", 13f,   // ios: Views/RemindViews.swift:118
                    if (c[k].ok) Theme.green else Theme.muted)   // ios: Views/RemindViews.swift:119
                T(c[k].text, style = appFont(13f), color = if (c[k].ok) Theme.sub else Theme.ink)   // ios: Views/RemindViews.swift:120
            }
        }
        i.why?.let { w -> T(w, style = appFont(12f), color = Theme.muted) }   // ios: Views/RemindViews.swift:123
    }
}

/** 일정: 날짜 블록 + 제목 + 왜 중요한지 */
// ios: Views/RemindViews.swift:135
@Composable
private fun EventRow(i: RemindItem, open: (RemindItem) -> Unit) {
    val d = i.day ?: ""; val n = Day.dday(d)   // ios: Views/RemindViews.swift:136
    // ios: Views/RemindViews.swift:137,155,157 (줄 전체를 누른다)
    Row(
        Modifier.fillMaxWidth().plainClickable { open(i) }
            .background(Theme.card, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:153
            .border(1.dp, Theme.border, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:154
            .padding(12.dp),   // ios: Views/RemindViews.swift:151,152
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,   // ios: Views/RemindViews.swift:138
    ) {
        // ios: Views/RemindViews.swift:139
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.size(52.dp, 48.dp)   // ios: Views/RemindViews.swift:143
                .background(Theme.orange.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),   // ios: Views/RemindViews.swift:144
        ) {
            T(Day.md(d), style = appFont(13f, FontWeight.Bold), color = Theme.orange)   // ios: Views/RemindViews.swift:140
            T(if (n == 0) "오늘" else "D-$n", style = appFont(11f), color = Theme.sub)   // ios: Views/RemindViews.swift:141
        }
        // ios: Views/RemindViews.swift:145
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(i.text + (i.sub?.let { " · $it" } ?: ""), style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)   // ios: Views/RemindViews.swift:146
            i.why?.let { w -> T(w, style = appFont(12f), color = Theme.sub) }   // ios: Views/RemindViews.swift:147
        }
        Spacer(Modifier.width(0.dp))   // ios: Views/RemindViews.swift:149 (minLength 0: 글 칸이 weight 로 남은 폭을 쓴다)
    }
}

/** 지난 30일: 아이콘 없는 한 줄과 날짜 */
// ios: Views/RemindViews.swift:161
@Composable
private fun PastRow(i: RemindItem, open: (RemindItem) -> Unit) {
    // ios: Views/RemindViews.swift:162,173,175 (줄 전체를 누른다)
    Row(
        Modifier.fillMaxWidth().plainClickable { open(i) }
            .defaultMinSize(minHeight = 44.dp)   // ios: Views/RemindViews.swift:172
            .padding(vertical = 8.dp),   // ios: Views/RemindViews.swift:171
        horizontalArrangement = Arrangement.spacedBy(8.dp),   // ios: Views/RemindViews.swift:163 (firstTextBaseline)
    ) {
        // ios: Views/RemindViews.swift:164
        Column(Modifier.weight(1f).alignByBaseline(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(i.text, style = appFont(14f), color = if (Remind.shared.isSeen(i)) Theme.sub else Theme.ink)   // ios: Views/RemindViews.swift:165,166
        }
        Spacer(Modifier.width(0.dp))   // ios: Views/RemindViews.swift:168 (minLength 0)
        Remind.shared.log.first[i.id]?.let { t -> T(RemindSheet.ago(t), Modifier.alignByBaseline(), style = appFont(11f), color = Theme.muted) }   // ios: Views/RemindViews.swift:169
    }
}

// ios: Views/RemindViews.swift:178
@Composable
private fun RemindRow(m: AppModel, i: RemindItem, dismiss: () -> Unit) {
    val cc = i.cat.color
    // ios: Views/RemindViews.swift:179,206
    Row(
        Modifier.fillMaxWidth()
            .plainClickable {
                if (i.go != RemindGo.nowhere) {   // ios: Views/RemindViews.swift:180
                    dismiss()   // ios: Views/RemindViews.swift:181
                    m.go(i.go)   // ios: Views/RemindViews.swift:182
                }
            }
            // ios: Views/RemindViews.swift:183,204 (label · 줄 전체를 누른다)
            .background(Theme.card, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:202
            .border(1.dp, Theme.border, RoundedCornerShape(14.dp))   // ios: Views/RemindViews.swift:203
            .padding(12.dp),   // ios: Views/RemindViews.swift:200,201
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,   // ios: Views/RemindViews.swift:184
    ) {
        Box(Modifier.size(28.dp, 28.dp).background(cc.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {   // ios: Views/RemindViews.swift:186
            Sym(i.cat.icon, 13f, cc)   // ios: Views/RemindViews.swift:185 (13 bold)
        }
        // ios: Views/RemindViews.swift:187
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // ios: Views/RemindViews.swift:188
            i.day?.let { d ->

                T("${MarketEvents.`when`(d)} · ${i.sub ?: ""}", style = appFont(12f, FontWeight.SemiBold), color = cc)   // Views/RemindViews.swift:190 (검사 예외: 같은 글)
            }
            T(i.text, style = appFont(14f), color = Theme.ink)   // ios: Views/RemindViews.swift:191,192
            // ios: Views/RemindViews.swift:193
            Remind.shared.log.first[i.id]?.takeIf { i.once }?.let { t ->
                T(RemindSheet.ago(t), style = appFont(11f), color = Theme.muted)   // ios: Views/RemindViews.swift:194
            }
        }
        Spacer(Modifier.width(0.dp))   // ios: Views/RemindViews.swift:197 (minLength 0)
        if (i.go != RemindGo.nowhere) Sym("chevron.right", 11f, Theme.muted, Modifier.padding(top = 8.dp))   // ios: Views/RemindViews.swift:198
    }
}

/** 설정 › 알림 아래: 홈 리마인드 분류별 끄기 (기본은 모두 켜짐, 펼쳐야 보인다) */
// ios: Views/RemindViews.swift:216
@Composable
fun RemindOptions() {
    var open by remember { mutableStateOf(false) }   // ios: Views/RemindViews.swift:217
    val r = Remind.shared   // ios: Views/RemindViews.swift:218,219
    // ios: Views/RemindViews.swift:220 DisclosureGroup: 이름표 줄을 누르면 펼친다. 오른쪽 › 는 펼치면 아래로 돈다 (tint = sub)
    Column(Modifier.fillMaxWidth()) {
        val turn by animateFloatAsState(if (open) 90f else 0f, label = "disclosure")
        Row(Modifier.fillMaxWidth().plainClickable { open = !open }.defaultMinSize(minHeight = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            T("홈 리마인드", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/RemindViews.swift:230,231
            Sym("chevron.right", 13f, Theme.sub, Modifier.rotate(turn))   // ios: Views/RemindViews.swift:233
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/RemindViews.swift:221,229
                T("푸시 알림이 아니라 홈 말풍선·일정 띠·소식함에만 떠요. 끈 분류는 홈과 위젯에서 안 보여요.",   // ios: Views/RemindViews.swift:222
                  style = appFont(12f), color = Theme.sub)   // ios: Views/RemindViews.swift:223
                // ios: Views/RemindViews.swift:224
                RemindCat.entries.forEach { c ->
                    ToggleRow(checked = r.isOn(c), onChange = { r.setOn(c, it) },   // ios: Views/RemindViews.swift:226 (tint teal · 높이 44)
                              modifier = Modifier.defaultMinSize(minHeight = 44.dp), tint = Theme.teal) {
                        T(c.label, style = appFont(15f))   // ios: Views/RemindViews.swift:225
                    }
                }
            }
        }
    }
}
