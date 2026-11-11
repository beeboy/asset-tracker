package com.naeilo.app.mock
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.core.cformat
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// 모의투자 일지 탭 · 설정 · 매매 기록 · 이유 사슬 · 탭 묶음

/** 종목 하나의 손익 (판 몫 + 지금 들고 있는 몫) */
data class MockStockPnl(   // ios: Mock/MockOrderViews.swift:7
    var code: String,   // ios: Mock/MockOrderViews.swift:8
    var name: String,   // ios: Mock/MockOrderViews.swift:9
    var pnl: Double = 0.0,   // ios: Mock/MockOrderViews.swift:10
    var basis: Double = 0.0,   // ios: Mock/MockOrderViews.swift:11
) {
    val ret: Double get() = if (basis > 0) pnl / basis else 0.0   // ios: Mock/MockOrderViews.swift:12
}

/** 지난 하루 (판 매매 기준) */
data class MockDay(   // ios: Mock/MockOrderViews.swift:16
    var day: String,   // ios: Mock/MockOrderViews.swift:17
    var trades: Int = 0,   // ios: Mock/MockOrderViews.swift:18
    var pnl: Double = 0.0,   // ios: Mock/MockOrderViews.swift:19
)

/** 오늘 판 종목 한 줄 */
data class MockSold(val code: String, val name: String, val qty: Int, val pnl: Double, val basis: Double)

fun MockDesk.perStock(): List<MockStockPnl> {   // ios: Mock/MockOrderViews.swift:22,23
    val l = ledger(shown)   // ios: Mock/MockOrderViews.swift:24
    val by = LinkedHashMap<String, MockStockPnl>()   // ios: Mock/MockOrderViews.swift:25
    for (e in events) {
        if (!(e.ledger == l.id && e.kind == MockEvent.Kind.fill && e.side == MockSide.sell)) continue   // ios: Mock/MockOrderViews.swift:26
        val c = e.code ?: continue; val p = e.pnl ?: continue   // ios: Mock/MockOrderViews.swift:27
        by.getOrPut(c) { MockStockPnl(code = c, name = e.name ?: c) }.pnl += p   // ios: Mock/MockOrderViews.swift:28
        by[c]?.let { it.basis += e.basis ?: 0.0 }   // ios: Mock/MockOrderViews.swift:29
    }
    for (p in l.positions.values) {   // ios: Mock/MockOrderViews.swift:31
        val now = s.prices[p.code] ?: p.avg   // ios: Mock/MockOrderViews.swift:32
        by.getOrPut(p.code) { MockStockPnl(code = p.code, name = p.name) }.pnl += p.qty.toDouble() * now - p.cost   // ios: Mock/MockOrderViews.swift:33
        by[p.code]?.let { it.basis += p.cost }   // ios: Mock/MockOrderViews.swift:34
    }
    return by.values.toList()   // ios: Mock/MockOrderViews.swift:36
}

/** 오늘 판 종목 (보이는 칸 장부의 매도 체결을 종목마다 모은다, 처음 판 때 순) */
fun MockDesk.soldToday(): List<MockSold> {   // ios: Mock/MockOrderViews.swift:40
    val id = ledger(shown).id; val today = MockFmt.day(mockNow())   // ios: Mock/MockOrderViews.swift:41
    val order = ArrayList<String>(); val by = HashMap<String, MockSold>()   // ios: Mock/MockOrderViews.swift:42
    for (e in events) {
        if (!(e.ledger == id && e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && MockFmt.day(e.at) == today)) continue   // ios: Mock/MockOrderViews.swift:43
        val c = e.code ?: continue   // ios: Mock/MockOrderViews.swift:44
        if (by[c] == null) order.add(c)   // ios: Mock/MockOrderViews.swift:45
        val x = by[c] ?: MockSold(c, e.name ?: c, 0, 0.0, 0.0)   // ios: Mock/MockOrderViews.swift:46
        by[c] = MockSold(c, x.name, x.qty + (e.qty ?: 0), x.pnl + (e.pnl ?: 0.0), x.basis + (e.basis ?: 0.0))   // ios: Mock/MockOrderViews.swift:47
    }
    return order.map { by[it]!! }   // ios: Mock/MockOrderViews.swift:49
}

/** 오늘 전 기록이 있는 날 (최근부터) */
fun MockDesk.pastDays(before: String): List<MockDay> {   // ios: Mock/MockOrderViews.swift:53
    val id = ledger(shown).id   // ios: Mock/MockOrderViews.swift:54
    val order = ArrayList<String>(); val by = HashMap<String, MockDay>()   // ios: Mock/MockOrderViews.swift:55
    for (e in events.reversed()) {
        if (e.ledger != id) continue   // ios: Mock/MockOrderViews.swift:56
        val k = MockFmt.day(e.at)   // ios: Mock/MockOrderViews.swift:57
        if (k >= before) continue   // ios: Mock/MockOrderViews.swift:58
        if (by[k] == null) { order.add(k); by[k] = MockDay(day = k) }   // ios: Mock/MockOrderViews.swift:59
        val p = e.pnl
        if (e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && p != null) { by[k]?.let { it.trades += 1; it.pnl += p } }   // ios: Mock/MockOrderViews.swift:60
    }
    return order.mapNotNull { by[it] }   // ios: Mock/MockOrderViews.swift:62
}

// MARK: 일지 (분석 대신)

private data class Held(   // ios: Mock/MockOrderViews.swift:79
    val code: String,   // ios: Mock/MockOrderViews.swift:80
    val name: String,   // ios: Mock/MockOrderViews.swift:81
    val avg: Double,   // ios: Mock/MockOrderViews.swift:82
    val value: Double,         // 들고 있으면 지금 평가액, 다 팔았으면 판 금액   // ios: Mock/MockOrderViews.swift:83
    val r: Double,   // ios: Mock/MockOrderViews.swift:84
    val wait: Int = 0,              // 팔기 대기 주수   // ios: Mock/MockOrderViews.swift:85
    val sold: Int = 0,              // 오늘 팔린 주수   // ios: Mock/MockOrderViews.swift:86
    val outside: Boolean = false,       // [실전] 칸: 앱이 사지 않은 토스 보유 (보기만)   // ios: Mock/MockOrderViews.swift:87
) {
    val id: String get() = code + (if (outside) ".out" else "")   // ios: Mock/MockOrderViews.swift:88
}

@Composable
fun MockJournalView() {   // ios: Mock/MockOrderViews.swift:68,91
    var unit by remember { mutableStateOf(MockCalc.Unit.hour) }   // 그래프 기본은 시간 (2026-10-26)   // ios: Mock/MockOrderViews.swift:69
    var downFirst by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:70
    var pastOpen by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:71
    var noteOpen by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:72
    var noteMore by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:73
    val colW = remember { com.naeilo.app.ui.hold.HoldColW() }   // ios: Mock/MockOrderViews.swift:74,102
    var sdraft by remember { mutableStateOf(MockSellDraft()) }   // ios: Mock/MockOrderViews.swift:75
    var sadding by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:76
    var info by remember { mutableStateOf<MockStockRef?>(null) }   // ios: Mock/MockOrderViews.swift:77
    val d = MockDesk.shared   // ios: Mock/MockOrderViews.swift:92
    val snappy = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 1f, stiffness = 400f)

    fun sToggle(c: MockSellCond) {   // ios: Mock/MockOrderViews.swift:150
        val cs = d.sconds.toMutableList()   // ios: Mock/MockOrderViews.swift:151
        val i = cs.indexOfFirst { it.id == c.id }.takeIf { it >= 0 } ?: return   // ios: Mock/MockOrderViews.swift:152
        cs[i] = cs[i].copy(on = !cs[i].on)   // ios: Mock/MockOrderViews.swift:153
        d.setSellConds((if (cs[i].on) "켬 · " else "끔 · ") + c.text, cs)   // ios: Mock/MockOrderViews.swift:154
    }

    fun sRemove(c: MockSellCond) {   // ios: Mock/MockOrderViews.swift:157
        d.setSellConds("지움 · " + c.text, d.sconds.filter { it.id != c.id })   // ios: Mock/MockOrderViews.swift:158
        if (sdraft.edit == c.id) sadding = false   // ios: Mock/MockOrderViews.swift:159
    }

    fun sCommit(c: MockSellCond) {   // ios: Mock/MockOrderViews.swift:162
        val cs = d.sconds.toMutableList()   // ios: Mock/MockOrderViews.swift:163
        val i = cs.indexOfFirst { it.id == c.id }   // ios: Mock/MockOrderViews.swift:164
        if (i >= 0) {
            val x = c.copy(on = cs[i].on)   // ios: Mock/MockOrderViews.swift:165,166
            cs[i] = x   // ios: Mock/MockOrderViews.swift:167
            d.setSellConds("고침 · " + c.text, cs)   // ios: Mock/MockOrderViews.swift:168
        } else {   // ios: Mock/MockOrderViews.swift:169
            cs.add(c)   // ios: Mock/MockOrderViews.swift:170
            d.setSellConds("넣음 · " + c.text, cs)   // ios: Mock/MockOrderViews.swift:171
        }
        sadding = false   // ios: Mock/MockOrderViews.swift:173
    }

    /** 칸마다 마지막 평가액 (지금 칸은 지금 평가액). 기록이 다 들어가면 장부 시작 현금부터. 첫 칸 이름 · 칸마다 시각도 함께 */
    fun series(): Triple<String, List<Double>, List<MockDate>> {   // ios: Mock/MockOrderViews.swift:205
        val l = d.ledger(d.shown)   // ios: Mock/MockOrderViews.swift:206
        val take: Int = when (unit) {   // ios: Mock/MockOrderViews.swift:207,208
            MockCalc.Unit.hour -> 24   // ios: Mock/MockOrderViews.swift:209
            MockCalc.Unit.day -> 30   // ios: Mock/MockOrderViews.swift:210
            MockCalc.Unit.week -> 26   // ios: Mock/MockOrderViews.swift:211
            MockCalc.Unit.month -> 24   // ios: Mock/MockOrderViews.swift:212
            MockCalc.Unit.year -> 10   // ios: Mock/MockOrderViews.swift:213
        }
        val order = ArrayList<String>(); val last = HashMap<String, Double>(); val at = HashMap<String, MockDate>()   // ios: Mock/MockOrderViews.swift:215
        for (m in d.marks.sortedBy { it.at }) {   // ios: Mock/MockOrderViews.swift:216
            val k = MockCalc.bucket(m.at, unit)   // ios: Mock/MockOrderViews.swift:217
            if (last[k] == null) order.add(k)   // ios: Mock/MockOrderViews.swift:218
            last[k] = m.eq; at[k] = m.at   // ios: Mock/MockOrderViews.swift:219
        }
        val nowKey = MockCalc.bucket(mockNow(), unit)   // ios: Mock/MockOrderViews.swift:221
        if (last[nowKey] == null) order.add(nowKey)   // ios: Mock/MockOrderViews.swift:222
        last[nowKey] = d.equity(d.shown); at[nowKey] = mockNow()   // ios: Mock/MockOrderViews.swift:223
        val keys = order.takeLast(take)   // ios: Mock/MockOrderViews.swift:224
        val eqs = keys.mapNotNull { last[it] }.toMutableList(); val dates = keys.mapNotNull { at[it] }.toMutableList()   // ios: Mock/MockOrderViews.swift:225
        var left = keys.firstOrNull() ?: ""   // ios: Mock/MockOrderViews.swift:226
        // 기록이 다 들어가면 맨 앞은 시작: [모의]는 시작 현금, [실전]은 첫 평가액 점(토스 계좌, 이미 점 안에 있다)
        if (order.size < take) {   // ios: Mock/MockOrderViews.swift:228
            if (d.shown != MockBook.real) { eqs.add(0, l.startCash); dates.add(0, l.started) }   // ios: Mock/MockOrderViews.swift:229
            left = "시작"   // ios: Mock/MockOrderViews.swift:230
        }
        return Triple(left, eqs, dates)   // ios: Mock/MockOrderViews.swift:232
    }

    MockScreen(tab = MockTab.journal) {   // ios: Mock/MockOrderViews.swift:93
        // retCard: 평가액 그래프: 종목 탭 그래프와 같은 모양 (오른쪽 세로 축 꼬리표 = 지금 평가액 · 달라진 금액 · 시작 평가액, 아래 가로 축 = 처음 칸 · 지금, 기간 단추는 아래).
        // 왼쪽 위 = 그 기간 수익률 (첫 칸 평가액 대비라 시간·일·주·월·년마다 다르다). 손가락으로 훑으면 시각 · 평가액 · 수익률
        run {
            val (left, pts, dates) = series()   // ios: Mock/MockOrderViews.swift:94,178,179
            val S = pts.firstOrNull() ?: 0.0; val E = pts.lastOrNull() ?: 0.0; val up = E >= S   // ios: Mock/MockOrderViews.swift:180
            val r = if (S > 0) E / S - 1 else 0.0   // ios: Mock/MockOrderViews.swift:181
            Card {   // ios: Mock/MockOrderViews.swift:182
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockOrderViews.swift:183
                    mt(MockFmt.pct(r), Modifier.alignByBaseline(), style = appFont(18f, FontWeight.ExtraBold).tnum(), color = if (r > 0) Theme.up else if (r < 0) Theme.down else Theme.sub)   // ios: Mock/MockOrderViews.swift:184
                    mt(unit.label + " 기준", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:185
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:186
                }
                if (pts.size < 2) {   // ios: Mock/MockOrderViews.swift:188
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:190
                        mt("평가액 점이 쌓이면 그려요", style = appFont(14f, FontWeight.Bold), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:189
                    }
                } else {   // ios: Mock/MockOrderViews.swift:191
                    com.naeilo.app.ui.hold.TagChart(points = pts, height = 120.dp, tags = listOf(   // ios: Mock/MockOrderViews.swift:192
                        com.naeilo.app.ui.hold.TagChart.Tag(at = E, text = MockFmt.man(E), bg = Theme.ink, fg = Theme.card),   // ios: Mock/MockOrderViews.swift:193
                        com.naeilo.app.ui.hold.TagChart.Tag(at = E, dy = 20.dp, text = (if (up) "+" else "−") + MockFmt.man(abs(E - S)), bg = if (up) com.naeilo.app.ui.hex(0xFF8A80) else com.naeilo.app.ui.hex(0x8CC4F2)),   // ios: Mock/MockOrderViews.swift:194
                        com.naeilo.app.ui.hold.TagChart.Tag(at = S, text = MockFmt.man(S), bg = com.naeilo.app.ui.hold.TagChart.gray),   // ios: Mock/MockOrderViews.swift:195
                    ), left = left, right = "지금",   // ios: Mock/MockOrderViews.swift:196
                        scrub = com.naeilo.app.ui.Scrub(date = { x -> java.time.Instant.ofEpochMilli((dates[min(dates.size - 1, max(0, (x * (dates.size - 1).toDouble()).swRound().toInt()))] * 1000).toLong()) },   // ios: Mock/MockOrderViews.swift:197
                            value = { v -> MockFmt.man(v) + " · " + MockFmt.pct(if (S > 0) v / S - 1 else 0.0) }))   // ios: Mock/MockOrderViews.swift:198
                }
                com.naeilo.app.ui.ChipRow(items = MockCalc.Unit.entries.map { it to it.label }, selection = unit, onSelect = { unit = it }, fill = true)   // ios: Mock/MockOrderViews.swift:200
            }
        }
        // sellCard: 팔기 조건: 켠 알약에 맞는 내 종목을 판다 (선정의 고르기 반대). 돌리기가 켜져 있고 팔기 알약이 켜져 있으면 1분 주기도 같은 조건으로 장중 가격을 본다
        Card {   // ios: Mock/MockOrderViews.swift:95,128,129
            com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockOrderViews.swift:130
                d.sconds.forEach { c ->   // ios: Mock/MockOrderViews.swift:131
                    MockCondPill(text = c.text, on = c.on,   // ios: Mock/MockOrderViews.swift:132
                        tap = { sToggle(c) },   // ios: Mock/MockOrderViews.swift:133
                        edit = { sdraft = MockSellDraft(c); sadding = true },   // ios: Mock/MockOrderViews.swift:134
                        delete = { sRemove(c) })   // ios: Mock/MockOrderViews.swift:135
                }
                MockAddPill { sdraft = MockSellDraft(); sadding = true }   // ios: Mock/MockOrderViews.swift:137
            }
            if (sadding) {   // ios: Mock/MockOrderViews.swift:139
                MockSellForm(draft = sdraft, setDraft = { sdraft = it }, close = { sadding = false }) { sCommit(it) }   // ios: Mock/MockOrderViews.swift:140
            }
            d.s.runs["sell"]?.let { r -> MockRunLine(run = r) }   // ios: Mock/MockOrderViews.swift:142
            Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:143
                Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:144
                MockGoPill(title = "지금 팔기", enabled = d.ledger(d.shown).positions.isNotEmpty()) { d.runSell() }   // ios: Mock/MockOrderViews.swift:145
            }
        }
        // mineCard
        run {
            val l = d.ledger(d.shown)   // ios: Mock/MockOrderViews.swift:96,235,236
            val today = MockFmt.day(mockNow())   // ios: Mock/MockOrderViews.swift:237
            val wait = HashMap<String, Int>(); val sold = HashMap<String, Int>()   // ios: Mock/MockOrderViews.swift:238
            for (o in l.pending) if (o.side == MockSide.sell) wait[o.code] = (wait[o.code] ?: 0) + o.qty   // ios: Mock/MockOrderViews.swift:239
            for (e in d.events) {
                if (!(e.ledger == l.id && e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && MockFmt.day(e.at) == today)) continue   // ios: Mock/MockOrderViews.swift:240
                val c = e.code ?: continue   // ios: Mock/MockOrderViews.swift:241
                sold[c] = (sold[c] ?: 0) + (e.qty ?: 0)   // ios: Mock/MockOrderViews.swift:242
            }
            // 들고 있는 종목만. 다 판 종목은 팔기 칸 '오늘 판'에 (금색 알약 = 오늘 일부 판 주수)
            val rows = l.positions.values.map { p ->   // ios: Mock/MockOrderViews.swift:245
                val now = d.s.prices[p.code] ?: p.avg   // ios: Mock/MockOrderViews.swift:246
                Held(code = p.code, name = p.name, avg = p.avg, value = p.qty.toDouble() * now, r = if (p.avg > 0) now / p.avg - 1 else 0.0,   // ios: Mock/MockOrderViews.swift:247
                    wait = wait[p.code] ?: 0, sold = sold[p.code] ?: 0)   // ios: Mock/MockOrderViews.swift:248
            }.toMutableList()
            // [실전] 칸: 실전 장부에 없는 토스 보유도 '외부'로 보기만 (팔기 조건·주문을 붙이지 않는다)
            rows += d.outside.map { x ->   // ios: Mock/MockOrderViews.swift:251
                val avg = x.avg ?: 0.0; val now = d.s.prices[x.code] ?: avg   // ios: Mock/MockOrderViews.swift:252
                Held(code = x.code, name = MockDesk.name(x.code), avg = avg, value = x.qty.toDouble() * now, r = if (avg > 0) now / avg - 1 else 0.0, outside = true)   // ios: Mock/MockOrderViews.swift:253
            }
            rows.sortWith { a, b -> if (downFirst) a.r.compareTo(b.r) else b.r.compareTo(a.r) }   // ios: Mock/MockOrderViews.swift:255
            val pnl = l.positions.values.fold(0.0) { a, p -> a + p.qty.toDouble() * (d.s.prices[p.code] ?: p.avg) - p.cost }   // ios: Mock/MockOrderViews.swift:256
            val mx = rows.map { abs(it.r) }.maxOrNull() ?: 0.0   // ios: Mock/MockOrderViews.swift:257
            val anyPill = rows.any { it.wait > 0 }   // ios: Mock/MockOrderViews.swift:258
            MockListCard(head = {   // ios: Mock/MockOrderViews.swift:259
                mt("내 종목 ${rows.size}", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:260
                MockSortToggle(downFirst = downFirst, set = { downFirst = it })   // ios: Mock/MockOrderViews.swift:261
                Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:262
                mt(MockFmt.signedWon(pnl), style = appFont(14f, FontWeight.SemiBold).tnum(), maxLines = 1,   // ios: Mock/MockOrderViews.swift:263
                    color = if (pnl > 0) Theme.up else if (pnl < 0) Theme.down else Theme.sub)   // ios: Mock/MockOrderViews.swift:264
            }) {   // ios: Mock/MockOrderViews.swift:265
                if (rows.isEmpty()) MockEmptyRow(text = "아직 산 종목이 없어요")   // ios: Mock/MockOrderViews.swift:266
                rows.forEach { h ->   // ios: Mock/MockOrderViews.swift:267
                    androidx.compose.runtime.key(h.id) {
                        if (h.outside) {   // ios: Mock/MockOrderViews.swift:268
                            MockStockRow(code = h.code, name = h.name, mid = MockFmt.small(h.avg), pct = h.r,   // ios: Mock/MockOrderViews.swift:269
                                price = MockFmt.man(h.value), band = if (mx > 0) abs(h.r) / mx else 0.0, outside = true, colW = colW) {   // ios: Mock/MockOrderViews.swift:270
                                if (anyPill) MockPillGap(colW, 3, 0)   // ios: Mock/MockOrderViews.swift:271
                            }
                        } else {   // ios: Mock/MockOrderViews.swift:273
                            Box(Modifier.clickable { info = MockStockRef(code = h.code, name = h.name) }) {   // ios: Mock/MockOrderViews.swift:274,281
                                MockStockRow(code = h.code, name = h.name, mid = MockFmt.small(h.avg), pct = h.r,   // ios: Mock/MockOrderViews.swift:275
                                    price = MockFmt.man(h.value), band = if (mx > 0) abs(h.r) / mx else 0.0, filled = h.sold > 0, colW = colW) {   // ios: Mock/MockOrderViews.swift:276
                                    if (h.wait > 0) Box(Modifier.holdCol(3, colW)) { MockQtyPill(text = "팔기 ${h.wait}주 대기", done = false) }   // ios: Mock/MockOrderViews.swift:277
                                    else if (anyPill) MockPillGap(colW, 3, 0)   // ios: Mock/MockOrderViews.swift:278
                                }
                            }
                        }
                    }
                }
            }
        }
        // soldCard: 오늘 판 종목 (최고·최저 위 따로 박스, 없으면 숨김). 누르면 매수·매도 기록
        run {
            val sold = d.soldToday()   // ios: Mock/MockOrderViews.swift:97,107,108
            if (sold.isNotEmpty()) {   // ios: Mock/MockOrderViews.swift:109
                val sum = sold.fold(0.0) { a, x -> a + x.pnl }   // ios: Mock/MockOrderViews.swift:110
                MockListCard(head = {   // ios: Mock/MockOrderViews.swift:111
                    mt("오늘 판 종목 ${sold.size}", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:112
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:113
                    mt(MockFmt.signedWon(sum), style = appFont(14f, FontWeight.SemiBold).tnum(), maxLines = 1, color = Theme.change(sum))   // ios: Mock/MockOrderViews.swift:114
                }) {   // ios: Mock/MockOrderViews.swift:115
                    sold.forEach { x ->   // ios: Mock/MockOrderViews.swift:116
                        Box(Modifier.clickable { info = MockStockRef(code = x.code, name = x.name) }) {   // ios: Mock/MockOrderViews.swift:117,121
                            MockStockRow(code = x.code, name = x.name, mid = "${x.qty}주", pct = if (x.basis > 0) x.pnl / x.basis else 0.0,   // ios: Mock/MockOrderViews.swift:118
                                price = MockFmt.signedWon(x.pnl), colW = colW)   // ios: Mock/MockOrderViews.swift:119
                        }
                    }
                }
            }
        }
        // bestWorst
        run {
            val all = d.perStock()   // ios: Mock/MockOrderViews.swift:98,287,288
            val best = all.filter { it.pnl > 0 }.maxByOrNull { it.pnl }   // ios: Mock/MockOrderViews.swift:289
            val worst = all.filter { it.pnl < 0 }.minByOrNull { it.pnl }   // ios: Mock/MockOrderViews.swift:290
            // 가운데 세로 선은 줄 높이 그대로 그린다 (IntrinsicSize 는 FitText(SubcomposeLayout) 를 못 재서 멈춘다)
            val sep = Theme.line
            Row(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp))   // ios: Mock/MockOrderViews.swift:298,299,300
                .drawBehind { drawRect(sep, topLeft = androidx.compose.ui.geometry.Offset((size.width - 1.dp.toPx()) / 2, 0f), size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)) },
                horizontalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockOrderViews.swift:291
                Box(Modifier.weight(1f).clickable(enabled = best != null) { best?.let { x -> info = MockStockRef(code = x.code, name = x.name) } }) { MockJournalCell("최고", best) }   // ios: Mock/MockOrderViews.swift:292,293
                Spacer(Modifier.width(1.dp))   // ios: Mock/MockOrderViews.swift:294 (선은 줄 drawBehind)
                Box(Modifier.weight(1f).clickable(enabled = worst != null) { worst?.let { x -> info = MockStockRef(code = x.code, name = x.name) } }) { MockJournalCell("최저", worst) }   // ios: Mock/MockOrderViews.swift:295,296
            }
        }
        // pastCard
        run {
            val days = d.pastDays(before = MockFmt.day(mockNow()))   // ios: Mock/MockOrderViews.swift:99,323,324
            MockListCard(modifier = Modifier.animateContentSize(snappy), head = {   // ios: Mock/MockOrderViews.swift:325
                Row(Modifier.weight(1f).animateContentSize().heightIn(min = 28.dp).clickable(enabled = days.isNotEmpty()) { pastOpen = !pastOpen }, verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:326,327,332,334,335
                    mt("지난 ${days.size}일", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:328
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:329
                    com.naeilo.app.ui.Sym(if (pastOpen) "chevron.down" else "chevron.right", 13f, Theme.muted)   // ios: Mock/MockOrderViews.swift:330
                }
            }) {   // ios: Mock/MockOrderViews.swift:336
                if (pastOpen) {   // ios: Mock/MockOrderViews.swift:337
                    days.forEach { x ->   // ios: Mock/MockOrderViews.swift:338
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockOrderViews.swift:339
                            com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:340
                            Row(Modifier.padding(horizontal = 14.dp).heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:341,348
                                mt(MockFmt.md(x.day), style = appFont(14f, FontWeight.SemiBold))   // ios: Mock/MockOrderViews.swift:342
                                Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:343
                                mt("판 ${x.trades}건", style = appFont(12f), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:344
                                mt(MockFmt.signedWon(x.pnl), style = appFont(14f, FontWeight.Bold).tnum(),   // ios: Mock/MockOrderViews.swift:345
                                    color = if (x.pnl > 0) Theme.up else if (x.pnl < 0) Theme.down else Theme.sub)   // ios: Mock/MockOrderViews.swift:346
                            }
                        }
                    }
                }
            }
        }
        // noteCard: 오류 · 규칙 바뀜: 한 줄씩 (최근부터, 처음엔 접어 둔다. 규칙 줄 앞엔 작게 '규칙')
        run {
            val all = d.events.reversed().filter { (it.book == d.shown && it.kind == MockEvent.Kind.error) || it.kind == MockEvent.Kind.rules }   // ios: Mock/MockOrderViews.swift:100,356,357
            val errN = all.count { it.kind == MockEvent.Kind.error }   // ios: Mock/MockOrderViews.swift:358
            val shown = if (noteOpen) all.take(if (noteMore) 60 else 8) else emptyList()   // ios: Mock/MockOrderViews.swift:359
            MockListCard(modifier = Modifier.animateContentSize(snappy), head = {   // ios: Mock/MockOrderViews.swift:360
                Row(Modifier.weight(1f).animateContentSize().heightIn(min = 28.dp).clickable(enabled = all.isNotEmpty()) { noteOpen = !noteOpen }, verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:361,362,367,369,370
                    mt("오류 $errN", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:363
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:364
                    com.naeilo.app.ui.Sym(if (noteOpen) "chevron.down" else "chevron.right", 13f, Theme.muted)   // ios: Mock/MockOrderViews.swift:365
                }
            }) {   // ios: Mock/MockOrderViews.swift:371
                shown.forEach { e ->   // ios: Mock/MockOrderViews.swift:372
                    androidx.compose.runtime.key(e.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockOrderViews.swift:373
                            com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:374
                            MockSwipe("지우기", { d.deleteNotes(setOf(e.id)) }, spacing = 6.dp) {   // ios: Mock/MockOrderViews.swift:375,382
                                Row(Modifier.weight(1f).padding(horizontal = 14.dp).heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:381
                                    mt(MockFmt.stamp(e.at), style = appFont(11f).tnum(), color = Theme.muted, maxLines = 1)   // ios: Mock/MockOrderViews.swift:376
                                    if (e.kind == MockEvent.Kind.rules) mt("규칙", style = appFont(10f, FontWeight.Bold), color = Theme.rule, maxLines = 1)   // ios: Mock/MockOrderViews.swift:377
                                    mt(e.text, Modifier.weight(1f, fill = false), style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Mock/MockOrderViews.swift:378
                                    Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Mock/MockOrderViews.swift:379
                                }
                            }
                        }
                    }
                }
                if (noteOpen && all.size > 8) {   // ios: Mock/MockOrderViews.swift:385
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockOrderViews.swift:386
                        com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:387
                        Box(Modifier.fillMaxWidth().animateContentSize().heightIn(min = 44.dp).clickable { noteMore = !noteMore }, contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:388,390,392
                            mt(if (noteMore) "접기" else "${min(all.size, 60) - 8}줄 더 보기", style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Mock/MockOrderViews.swift:389
                        }
                    }
                }
            }
        }
    }
    com.naeilo.app.ui.Sheet(show = info != null, onDismiss = { info = null }) {   // ios: Mock/MockOrderViews.swift:103
        info?.let { MockStockSheet(code = it.code, name = it.name, fills = true) { info = null } }
    }
}

/** 칸 맞춤 빈 자리 (알약 칸 폭, 높이 1) */
@Composable
private fun MockPillGap(colW: com.naeilo.app.ui.hold.HoldColW, k: Int, z: Int) {
    val w = with(androidx.compose.ui.platform.LocalDensity.current) { (colW.w[k] ?: z).toDp() }
    Spacer(Modifier.size(width = w, height = 1.dp))   // ios: Mock/MockOrderViews.swift:271,278
}

@Composable
private fun MockJournalCell(title: String, x: MockStockPnl?) {   // ios: Mock/MockOrderViews.swift:303
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Mock/MockOrderViews.swift:304,318,319,320
        mt(title, style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:305
        if (x != null) {   // ios: Mock/MockOrderViews.swift:306
            val c = if (x.pnl > 0) Theme.up else Theme.down   // ios: Mock/MockOrderViews.swift:307
            mt(x.name, style = appFont(15f, FontWeight.Bold), maxLines = 1)   // ios: Mock/MockOrderViews.swift:308
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockOrderViews.swift:309
                FitText(MockFmt.signedWon(x.pnl), style = appFont(14f, FontWeight.Bold).tnum(), minScale = 0.7f, maxLines = 1, color = c, modifier = Modifier.alignByBaseline().weight(1f, fill = false))   // ios: Mock/MockOrderViews.swift:310,313
                FitText(MockFmt.pct(x.ret), style = appFont(12f, FontWeight.SemiBold).tnum(), minScale = 0.7f, color = c, modifier = Modifier.alignByBaseline())   // ios: Mock/MockOrderViews.swift:311
            }
        } else {   // ios: Mock/MockOrderViews.swift:314
            mt("없어요", style = appFont(14f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:315
        }
    }
}

// MARK: 계좌 (투자 모드 5번째 칸. [모의]·[실전]을 따라 그 장부를 그림으로)

@Composable
fun MockAccountView() {   // ios: Mock/MockOrderViews.swift:401,411
    var autoLast by remember { mutableStateOf(Defaults.prefs.getInt("mockAutoLast", 10)) }   // 돌리기 멈춤을 풀 때 쓸 지난 간격 (분)   // ios: Mock/MockOrderViews.swift:402
    var settings by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:403
    var liveAsk by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:404
    var note by remember { mutableStateOf<String?>(null) }   // ios: Mock/MockOrderViews.swift:405
    var editRow by remember { mutableStateOf<MockPickRow?>(null) }   // ios: Mock/MockOrderViews.swift:406
    var sel by remember { mutableStateOf<String?>(null) }   // ios: Mock/MockOrderViews.swift:407
    var pastReads by remember { mutableStateOf(setOf<String>()) }   // 지난 AI 조언을 편 흐름   // ios: Mock/MockOrderViews.swift:408
    var clearAsk by remember { mutableStateOf<MockFlowRead.Kind?>(null) }   // 지우기를 물을 흐름 카드   // ios: Mock/MockOrderViews.swift:409
    val scope = rememberCoroutineScope()
    val d = MockDesk.shared   // ios: Mock/MockOrderViews.swift:412
    val ctx = MockAccountCtx(
        sel = sel, setSel = { sel = it }, setEditRow = { editRow = it },
        pastReads = pastReads, togglePast = { k -> pastReads = if (pastReads.contains(k)) pastReads - k else pastReads + k },
        setClearAsk = { clearAsk = it }, note = note, setNote = { note = it }, setLiveAsk = { liveAsk = it },
        autoLast = autoLast, setAutoLast = { autoLast = it; try { Defaults.prefs.edit().putInt("mockAutoLast", it).apply() } catch (_: Exception) {} },
        launch = { act -> scope.launch { act() } },
    )
    MockScreen(tab = MockTab.account, gear = { settings = true }) {   // ios: Mock/MockOrderViews.swift:413
        if (d.shown == MockBook.real && d.s.sellCheck == true) MockSellCheckCard(d)   // ios: Mock/MockOrderViews.swift:414
        MockWinCard(d)   // ios: Mock/MockOrderViews.swift:415
        MockFollowCard(d, ctx)   // ios: Mock/MockOrderViews.swift:416
        if (MockAI.on) MockReadCard(MockFlowRead.Kind.follow, d, ctx)   // ios: Mock/MockOrderViews.swift:417
        MockAfterCard(d, ctx)   // ios: Mock/MockOrderViews.swift:418
        if (MockAI.on) MockReadCard(MockFlowRead.Kind.after, d, ctx)   // ios: Mock/MockOrderViews.swift:419
        MockOrdersCard(d, ctx)   // ios: Mock/MockOrderViews.swift:420
        MockGapCard(d)   // ios: Mock/MockOrderViews.swift:421
        MockLights(d, ctx)   // ios: Mock/MockOrderViews.swift:422
    }
    LaunchedEffect(Unit) { d.refreshAccount() }   // ios: Mock/MockOrderViews.swift:424
    com.naeilo.app.ui.Sheet(show = editRow != null, onDismiss = { editRow = null }) {   // ios: Mock/MockOrderViews.swift:425
        editRow?.let { r -> MockRowEdit(row = r) { editRow = null } }
    }
    com.naeilo.app.ui.Sheet(show = settings, onDismiss = { settings = false }) { MockSettingsView { settings = false } }   // ios: Mock/MockOrderViews.swift:426
    com.naeilo.app.ui.ConfirmSheet(show = liveAsk, title = "실전을 켤까요?", message = null, actions = listOf(   // ios: Mock/MockOrderViews.swift:427
        com.naeilo.app.ui.DialogAction("실전 켜기 (실제 주문)", destructive = true, onClick = { note = d.setLive(true); liveAsk = false }),   // ios: Mock/MockOrderViews.swift:428
    ), onDismiss = { liveAsk = false })
    val ca = clearAsk
    com.naeilo.app.ui.ConfirmSheet(show = ca != null,   // ios: Mock/MockOrderViews.swift:431
        title = if (ca == MockFlowRead.Kind.follow) "걸린 종목 뒤 흐름을 지울까요?" else "판 뒤 흐름을 지울까요?",   // ios: Mock/MockOrderViews.swift:430
        message = if (ca == MockFlowRead.Kind.follow) "쌓인 기록과 지난 AI 조언을 모두 지워요" else "지금 장부의 판 기록과 지난 AI 조언을 지워요",   // ios: Mock/MockOrderViews.swift:437,438
        actions = listOf(com.naeilo.app.ui.DialogAction("지우기", destructive = true, onClick = {   // ios: Mock/MockOrderViews.swift:432,435
            if (ca == MockFlowRead.Kind.follow) d.clearFollows() else d.clearAfters(d.shown)   // ios: Mock/MockOrderViews.swift:433
            clearAsk = null   // ios: Mock/MockOrderViews.swift:434
        })),
        cancel = "그만두기", onDismiss = { clearAsk = null })
}

/** 계좌 탭 카드들이 함께 쓰는 상태 */
class MockAccountCtx(
    val sel: String?, val setSel: (String?) -> Unit, val setEditRow: (MockPickRow?) -> Unit,
    val pastReads: Set<String>, val togglePast: (String) -> Unit,
    val setClearAsk: (MockFlowRead.Kind?) -> Unit, val note: String?, val setNote: (String?) -> Unit, val setLiveAsk: (Boolean) -> Unit,
    val autoLast: Int, val setAutoLast: (Int) -> Unit,
    val launch: (suspend () -> Unit) -> Unit,
)

/** 팔기 규칙이 바뀌면 실전에 쓰기 전에 지금 팔릴 종목을 먼저 보인다. 확인 전엔 손절만 판다 */
@Composable
private fun MockSellCheckCard(d: MockDesk) {   // ios: Mock/MockOrderViews.swift:443
    val sigs = d.sellPreview(MockBook.real)   // ios: Mock/MockOrderViews.swift:444
    Card {   // ios: Mock/MockOrderViews.swift:445
        MockHead("팔기 규칙이 바뀌었어요", "확인 전엔 손절만")   // ios: Mock/MockOrderViews.swift:446
        if (sigs.isEmpty()) {   // ios: Mock/MockOrderViews.swift:447
            mt("지금 가격이면 팔릴 종목이 없어요", style = appFont(13f), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:448
        }
        sigs.forEach { g ->   // ios: Mock/MockOrderViews.swift:450
            mt(MockDesk.name(g.code) + " · " + g.why, style = appFont(13f))   // ios: Mock/MockOrderViews.swift:451,452
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).background(Theme.teal, RoundedCornerShape(12.dp)).clickable { d.confirmSellRule() }, contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:454,456,457,459
            mt("확인하고 실전에 적용", style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Mock/MockOrderViews.swift:455
        }
    }
}

@Composable
private fun MockHead(t: String, r: String) {   // ios: Mock/MockOrderViews.swift:463
    Row {   // ios: Mock/MockOrderViews.swift:464
        mt(t, Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:465
        Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:466
        FitText(r, style = appFont(12f).tnum(), minScale = 0.7f, maxLines = 1, color = Theme.sub, modifier = Modifier.alignByBaseline())   // ios: Mock/MockOrderViews.swift:467
    }
}

// MARK: 오늘 주문 — 끝 모습 띠 + 장 시간줄 점

object MockAccount {
    /** 주문 한 건의 끝 모습 */
    enum class End {   // ios: Mock/MockOrderViews.swift:474
        fill, wait, cancel, reject;   // ios: Mock/MockOrderViews.swift:474
        val label: String get() = listOf("체결", "대기", "취소", "거부")[ordinal]   // ios: Mock/MockOrderViews.swift:475
    }
    data class Dot(val id: String, val at: MockDate, val side: MockSide, val won: Double, val end: End)   // ios: Mock/MockOrderViews.swift:477

    /** 오늘 주문을 점으로: 접수 기록 하나가 점 하나, 접수 전에 거부된 것도 점 하나 */
    fun dots(d: MockDesk): List<Dot> {   // ios: Mock/MockOrderViews.swift:480
        val l = d.ledger(d.shown); val today = MockFmt.day(mockNow())   // ios: Mock/MockOrderViews.swift:481
        val es = d.ledgerEvents(d.shown).filter { MockFmt.day(it.at) == today && !it.test }   // ios: Mock/MockOrderViews.swift:482
        val wait = l.pending.map { it.id }.toSet()   // ios: Mock/MockOrderViews.swift:483
        val by = HashMap<String, MutableList<MockEvent>>()   // ios: Mock/MockOrderViews.swift:484
        for (e in es) if (e.kind != MockEvent.Kind.order) e.ref?.let { r -> by.getOrPut(r) { mutableListOf() }.add(e) }   // ios: Mock/MockOrderViews.swift:485
        val orders = es.filter { it.kind == MockEvent.Kind.order && it.qty != null && !it.text.startsWith("정정") }   // ios: Mock/MockOrderViews.swift:486
        val ids = orders.map { it.id }.toSet()   // ios: Mock/MockOrderViews.swift:487
        val out = orders.map { o ->   // ios: Mock/MockOrderViews.swift:488
            val after = by[o.id] ?: emptyList<MockEvent>()   // ios: Mock/MockOrderViews.swift:489
            val px = after.firstOrNull { it.kind == MockEvent.Kind.fill }?.price ?: o.code?.let { d.s.prices[it] } ?: 0.0   // ios: Mock/MockOrderViews.swift:490
            val end: End = if (wait.contains(o.id)) End.wait else if (after.any { it.kind == MockEvent.Kind.fill }) End.fill   // ios: Mock/MockOrderViews.swift:491
                else if (after.any { it.kind == MockEvent.Kind.cancel }) End.cancel else if (after.any { it.kind == MockEvent.Kind.reject }) End.reject else End.fill   // ios: Mock/MockOrderViews.swift:492
            Dot(id = o.id, at = o.at, side = o.side ?: MockSide.buy, won = px * (o.qty ?: 0).toDouble(), end = end)   // ios: Mock/MockOrderViews.swift:493
        }.toMutableList()
        out += es.filter { it.kind == MockEvent.Kind.reject && !(it.ref?.let { r -> ids.contains(r) } ?: false) }.map {   // ios: Mock/MockOrderViews.swift:495
            Dot(id = it.id, at = it.at, side = it.side ?: MockSide.buy, won = (it.code?.let { c -> d.s.prices[c] } ?: 0.0) * (it.qty ?: 0).toDouble(), end = End.reject)   // ios: Mock/MockOrderViews.swift:496
        }
        return out.sortedBy { it.at }   // ios: Mock/MockOrderViews.swift:498
    }

    /** 판 이유별 성적 (정식 체결만, 많은 순) */
    fun exits(es: List<MockEvent>): List<Pair<String, MockCalc.Stat>> {   // ios: Mock/MockOrderViews.swift:645
        val by = HashMap<String, MockCalc.Stat>()   // ios: Mock/MockOrderViews.swift:646
        for (e in es) if (e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && !e.test) by.getOrPut(e.exit ?: "예전 기록") { MockCalc.Stat() }.add(e)   // ios: Mock/MockOrderViews.swift:647
        return by.map { it.key to it.value }.sortedWith { a, b -> if (a.second.trades != b.second.trades) b.second.trades.compareTo(a.second.trades) else a.first.compareTo(b.first) }   // ios: Mock/MockOrderViews.swift:648
    }

    /** 판 뒤 오르내림이 이 안이면 '비슷' (수수료·세금 0.23% 남짓이라 그보다 작은 차이는 가리지 않는다) */
    const val afterEven = 0.003   // ios: Mock/MockOrderViews.swift:692
}

@Composable
private fun MockOrdersCard(d: MockDesk, ctx: MockAccountCtx) {   // ios: Mock/MockOrderViews.swift:501
    val ds = MockAccount.dots(d)   // ios: Mock/MockOrderViews.swift:502
    val n = MockAccount.End.entries.map { e -> ds.count { it.end == e } }   // ios: Mock/MockOrderViews.swift:503
    val o = ctx.sel?.let { id -> d.ledger(d.shown).pending.firstOrNull { it.id == id } }   // ios: Mock/MockOrderViews.swift:504
    Card {   // ios: Mock/MockOrderViews.swift:505
        MockHead("오늘 주문", "${ds.size}건")   // ios: Mock/MockOrderViews.swift:506
        if (ds.isEmpty()) {   // ios: Mock/MockOrderViews.swift:507
            mt("아직 없음", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:508
        } else {   // ios: Mock/MockOrderViews.swift:509
            // 끝 모습 띠
            val on = MockAccount.End.entries.filter { n[it.ordinal] > 0 }   // ios: Mock/MockOrderViews.swift:512
            BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {   // ios: Mock/MockOrderViews.swift:511,518
                val w = maxWidth - 3.dp * (on.size - 1)   // ios: Mock/MockOrderViews.swift:513
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {   // ios: Mock/MockOrderViews.swift:514
                    on.forEach { e -> MockSeg(e, Modifier.width(w * n[e.ordinal] / ds.size).fillMaxHeight()) }   // ios: Mock/MockOrderViews.swift:515
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Mock/MockOrderViews.swift:519
                MockAccount.End.entries.forEach { e ->   // ios: Mock/MockOrderViews.swift:520
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { MockMark(e, side = MockSide.buy, size = 10.dp, gray = true); mt(e.label + " ${n[e.ordinal]}", style = appFont(12f), color = Theme.sub) }   // ios: Mock/MockOrderViews.swift:521,522
                }
            }
        }
        MockOrderLine(ds, ctx)   // ios: Mock/MockOrderViews.swift:526
        if (o != null) {   // ios: Mock/MockOrderViews.swift:527
            Row(Modifier.fillMaxWidth().background(Theme.line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:528,539,540
                MockMark(MockAccount.End.wait, side = o.side, size = 12.dp)   // ios: Mock/MockOrderViews.swift:529
                FitText(o.name + " · " + o.side.label + " ${o.qty}주 · 대기", style = appFont(13f, FontWeight.SemiBold), minScale = 0.8f, maxLines = 1, modifier = Modifier.weight(1f).padding(end = 4.dp))   // ios: Mock/MockOrderViews.swift:530,531
                Box(Modifier.clickable { ctx.setEditRow(MockPickRow(code = o.code, name = o.name, qty = o.qty, kind = MockPickRow.Kind.wait, price = d.s.prices[o.code] ?: o.limit ?: 0.0, pct = 0.0, side = o.side, orderId = o.id)) }) {   // ios: Mock/MockOrderViews.swift:532,535
                    MockTag(text = "정정", color = Theme.teal)   // ios: Mock/MockOrderViews.swift:533
                }
                Box(Modifier.clickable { d.cancel(d.shown, orderId = o.id); ctx.setSel(null) }) { MockTag(text = "취소", color = Theme.up) }   // ios: Mock/MockOrderViews.swift:536,537
            }
        }
    }
}

/** 띠 한 칸 */
@Composable
fun MockSeg(e: MockAccount.End, modifier: Modifier) {   // ios: Mock/MockOrderViews.swift:546
    val r = RoundedCornerShape(4.dp)   // ios: Mock/MockOrderViews.swift:547
    when (e) {   // ios: Mock/MockOrderViews.swift:548
        MockAccount.End.fill -> Box(modifier.background(Theme.ink, r))   // ios: Mock/MockOrderViews.swift:549
        MockAccount.End.wait -> Box(modifier.border(2.dp, Theme.ink, r))   // ios: Mock/MockOrderViews.swift:550
        MockAccount.End.cancel -> Box(modifier.background(Theme.muted.copy(alpha = 0.35f), r))   // ios: Mock/MockOrderViews.swift:551
        MockAccount.End.reject -> Box(modifier.background(Theme.orange, r))   // ios: Mock/MockOrderViews.swift:552
    }
}

/** 점 하나: 체결 꽉 참 · 대기 빈 동그라미 · 취소 ✕ · 거부 주황 테두리 ! */
@Composable
fun MockMark(e: MockAccount.End, side: MockSide, size: androidx.compose.ui.unit.Dp, gray: Boolean = false) {   // ios: Mock/MockOrderViews.swift:557
    val c = if (gray) Theme.ink else if (side == MockSide.buy) Theme.up else Theme.down   // ios: Mock/MockOrderViews.swift:558
    when (e) {   // ios: Mock/MockOrderViews.swift:559
        MockAccount.End.fill -> Box(Modifier.size(size, size).background(c, CircleShape))   // ios: Mock/MockOrderViews.swift:560
        MockAccount.End.wait -> Box(Modifier.size(size, size).border(max(1.5f, size.value / 9).dp, c, CircleShape))   // ios: Mock/MockOrderViews.swift:561
        MockAccount.End.cancel -> Box(Modifier.size(size, size), contentAlignment = Alignment.Center) { com.naeilo.app.ui.Sym("xmark", size.value * 0.8f, Theme.muted) }   // ios: Mock/MockOrderViews.swift:562
        MockAccount.End.reject ->   // ios: Mock/MockOrderViews.swift:563
            Box(Modifier.size(size, size).border(max(1.5f, size.value / 9).dp, Theme.orange, CircleShape), contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:564
                mt("!", style = TextStyle(fontSize = (size.value * 0.6f).sp, fontWeight = FontWeight.Black), color = Theme.orange)   // ios: Mock/MockOrderViews.swift:565
            }
    }
}

/** 9:00~15:30 시간줄. 산 주문은 위, 판 주문은 아래. 점 크기 = 주문 금액 */
@Composable
private fun MockOrderLine(ds: List<MockAccount.Dot>, ctx: MockAccountCtx) {   // ios: Mock/MockOrderViews.swift:570
    val now = everyMinute()   // ios: Mock/MockOrderViews.swift:571
    val open = java.time.Instant.ofEpochMilli((now * 1000).toLong()).atZone(java.time.ZoneId.systemDefault()).withHour(9).withMinute(0).withSecond(0).withNano(0).toEpochSecond().toDouble()   // ios: Mock/MockOrderViews.swift:572
    val span = 390.0 * 60   // ios: Mock/MockOrderViews.swift:573
    val x = { t: MockDate -> min(1.0, max(0.0, (t - open) / span)) }   // ios: Mock/MockOrderViews.swift:574
    val top = max(1.0, ds.map { it.won }.maxOrNull() ?: 1.0)   // ios: Mock/MockOrderViews.swift:575
    BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp)) {   // ios: Mock/MockOrderViews.swift:576,579,601
        val w = maxWidth - 16.dp; val mid = 52.dp   // ios: Mock/MockOrderViews.swift:577
        val px = { t: MockDate -> 8.dp + w * x(t).toFloat() }   // ios: Mock/MockOrderViews.swift:578
        Box(Modifier.offset(x = 8.dp, y = mid - 2.dp / 2).size(w, 2.dp).background(Theme.line))   // ios: Mock/MockOrderViews.swift:580
        Box(Modifier.offset(x = 8.dp, y = mid - 2.dp / 2).size(w * x(now).toFloat(), 2.dp).background(Theme.muted))   // ios: Mock/MockOrderViews.swift:581
        if (x(now) > 0 && x(now) < 1) {   // ios: Mock/MockOrderViews.swift:582
            Box(Modifier.offset(x = px(now) - 0.75.dp, y = mid - 35.dp).size(1.5.dp, 70.dp).background(Theme.orange))   // ios: Mock/MockOrderViews.swift:583
        }
        ds.forEach { dot ->   // ios: Mock/MockOrderViews.swift:585
            val s = (10 + 12 * sqrt(dot.won / top)).dp   // ios: Mock/MockOrderViews.swift:586
            val cy = if (dot.side == MockSide.buy) mid - 22.dp else mid + 22.dp   // ios: Mock/MockOrderViews.swift:593
            Box(Modifier.offset(x = px(dot.at) - s / 2 - 6.dp, y = cy - s / 2 - 6.dp)   // ios: Mock/MockOrderViews.swift:593
                .clickable(interactionSource = null, indication = null) { ctx.setSel(if (dot.end == MockAccount.End.wait) (if (ctx.sel == dot.id) null else dot.id) else null) }   // ios: Mock/MockOrderViews.swift:587,592
                .padding(6.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:589
                MockMark(dot.end, side = dot.side, size = s)   // ios: Mock/MockOrderViews.swift:588
                if (ctx.sel == dot.id) Box(Modifier.requiredSize(s + 8.dp, s + 8.dp).border(4.dp, (if (dot.side == MockSide.buy) Theme.up else Theme.down).copy(alpha = 0.25f), CircleShape))   // ios: Mock/MockOrderViews.swift:590
            }
        }
        listOf("9시", "11시", "13시", "15:30").forEachIndexed { i, t ->   // ios: Mock/MockOrderViews.swift:595
            val cx = 8.dp + w * listOf(0, 120, 240, 390)[i].toFloat() / 390f   // ios: Mock/MockOrderViews.swift:597
            Box(Modifier.offset(x = cx - 20.dp, y = 112.dp - 8.dp).width(40.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:597
                mt(t, style = appFont(10f), color = Theme.muted, maxLines = 1)   // ios: Mock/MockOrderViews.swift:596
            }
        }
    }
}

// MARK: 하루 승률 — 날마다 막대 하나 (높이 = 승률, 색 = 그날 손익). 아래는 판 이유별 성적

@Composable
private fun MockWinCard(d: MockDesk) {   // ios: Mock/MockOrderViews.swift:606
    val es = d.ledgerEvents(d.shown); val id = d.ledger(d.shown).id   // ios: Mock/MockOrderViews.swift:607
    val days = MockCalc.periods(es, ledger = id, unit = MockCalc.Unit.day).take(10).reversed()   // ios: Mock/MockOrderViews.swift:608
    val all = MockCalc.stat(es, ledger = id)   // ios: Mock/MockOrderViews.swift:609
    val exits = MockAccount.exits(es)   // ios: Mock/MockOrderViews.swift:610
    fun rate(r: Double?): String = r?.let { "${(it * 100).swRound().toInt()}%" } ?: "—"   // ios: Mock/MockOrderViews.swift:611
    @Composable fun color(x: Double): Color = if (x > 0) Theme.up else if (x < 0) Theme.down else Theme.sub   // ios: Mock/MockOrderViews.swift:612
    Card {   // ios: Mock/MockOrderViews.swift:613
        MockHead("하루 승률", if (all.trades > 0) "전체 " + rate(all.winRate) + " · ${all.wins}/${all.trades}건" else "판 매매 없음")   // ios: Mock/MockOrderViews.swift:614
        if (days.isEmpty()) {   // ios: Mock/MockOrderViews.swift:615
            mt("아직 판 매매 없음", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:616
        } else {   // ios: Mock/MockOrderViews.swift:617
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {   // ios: Mock/MockOrderViews.swift:618,629
                days.forEach { x ->   // ios: Mock/MockOrderViews.swift:619
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Mock/MockOrderViews.swift:620,626
                        FitText(rate(x.second.winRate), style = appFont(10f, FontWeight.SemiBold).tnum(), minScale = 0.6f, maxLines = 1)   // ios: Mock/MockOrderViews.swift:621
                        Box(Modifier.widthIn(max = 26.dp).fillMaxWidth().height(max(3.0, 80 * (x.second.winRate ?: 0.0)).dp).background(color(x.second.pnl), RoundedCornerShape(4.dp)))   // ios: Mock/MockOrderViews.swift:622,623
                        FitText(x.first, style = appFont(10f), minScale = 0.6f, maxLines = 1, color = Theme.muted)   // ios: Mock/MockOrderViews.swift:624
                    }
                }
            }
            mt("높이는 승률, 빨강은 그날 손익 +, 파랑은 −", style = appFont(11f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:630
            com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:631
            exits.forEach { x ->   // ios: Mock/MockOrderViews.swift:632
                Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:633
                    mt(x.first, style = appFont(13f, FontWeight.SemiBold))   // ios: Mock/MockOrderViews.swift:634
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:635
                    mt("${x.second.trades}건 · " + rate(x.second.winRate) + " · ", style = appFont(12f).tnum(), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:636
                    mt(MockFmt.signedWon(x.second.pnl), style = appFont(12f, FontWeight.SemiBold).tnum(), color = color(x.second.pnl))   // ios: Mock/MockOrderViews.swift:637
                }
            }
        }
    }
}

// MARK: 걸린 종목 뒤 흐름 — 장중 처음 걸린 종목(거른 것 포함)의 30분·1시간·마감 평균. 산 것과 안 산 것을 나란히

@Composable
private fun MockFollowCard(d: MockDesk, ctx: MockAccountCtx) {   // ios: Mock/MockOrderViews.swift:653
    val fs = d.s.follows ?: emptyList<MockFollow>()   // ios: Mock/MockOrderViews.swift:654
    val bought = d.ledgerEvents(d.shown).filter { it.kind == MockEvent.Kind.fill && it.side == MockSide.buy && !it.test }.mapNotNull { e -> e.code?.let { MockFmt.day(e.at) + "|" + it } }.toSet()   // ios: Mock/MockOrderViews.swift:655
    val yes = fs.filter { bought.contains(it.day + "|" + it.code) }; val no = fs.filter { !bought.contains(it.day + "|" + it.code) }   // ios: Mock/MockOrderViews.swift:656
    val dayN = fs.map { it.day }.toSet().size   // ios: Mock/MockOrderViews.swift:657
    fun avg(xs: List<MockFollow>, k: (MockFollow) -> Double?): Double? {   // ios: Mock/MockOrderViews.swift:658
        val rs = xs.mapNotNull { it.ret(k(it)) }   // ios: Mock/MockOrderViews.swift:659
        return if (rs.isEmpty()) null else rs.fold(0.0) { a, b -> a + b } / rs.size.toDouble()   // ios: Mock/MockOrderViews.swift:660
    }
    @Composable fun RowScope.cell(r: Double?) {   // ios: Mock/MockOrderViews.swift:662
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {   // ios: Mock/MockOrderViews.swift:665
            mt(r?.let { MockFmt.pct(it, digits = 1) } ?: "—", style = appFont(13f, FontWeight.SemiBold).tnum(),   // ios: Mock/MockOrderViews.swift:663
                color = if ((r ?: 0.0) > 0) Theme.up else if ((r ?: 0.0) < 0) Theme.down else Theme.sub)   // ios: Mock/MockOrderViews.swift:664
        }
    }
    @Composable fun row(t: String, xs: List<MockFollow>) {   // ios: Mock/MockOrderViews.swift:667
        Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:668
            mt(t + " ${xs.size}", Modifier.width(84.dp), style = appFont(13f))   // ios: Mock/MockOrderViews.swift:669
            cell(avg(xs) { it.p30 }); cell(avg(xs) { it.p60 }); cell(avg(xs) { it.pEnd })   // ios: Mock/MockOrderViews.swift:670
        }
    }
    Card {   // ios: Mock/MockOrderViews.swift:673
        MockFlowHead("걸린 종목 뒤 흐름", if (fs.isEmpty()) "쌓는 중" else "${fs.size}종목 · ${dayN}일", MockFlowRead.Kind.follow, d, ctx, empty = fs.isEmpty())   // ios: Mock/MockOrderViews.swift:674
        if (fs.isEmpty()) {   // ios: Mock/MockOrderViews.swift:675
            mt("장중 훑기에서 걸린 종목부터 쌓여요", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:676
        } else {   // ios: Mock/MockOrderViews.swift:677
            Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:678
                Spacer(Modifier.size(84.dp, 1.dp))   // ios: Mock/MockOrderViews.swift:679
                listOf("30분", "1시간", "마감").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { mt(it, style = appFont(11f), color = Theme.muted) } }   // ios: Mock/MockOrderViews.swift:680
            }
            row("산", yes)   // ios: Mock/MockOrderViews.swift:682
            row("안 산", no)   // ios: Mock/MockOrderViews.swift:683
            mt("처음 걸린 값 대비 평균. 안 산 쪽이 더 오르면 선정이 좋은 종목을 놓친 거예요", style = appFont(11f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:684
        }
    }
}

// MARK: 판 뒤 흐름 — 판 값보다 뒤에 내리면 잘 판 것, 오르면 판 자리가 반환점

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MockAfterCard(d: MockDesk, ctx: MockAccountCtx) {   // ios: Mock/MockOrderViews.swift:694
    val xs = (d.s.afters ?: emptyList<MockAfter>()).filter { it.book == d.shown }   // ios: Mock/MockOrderViews.swift:695
    val done = xs.filter { it.ret != null }   // ios: Mock/MockOrderViews.swift:696
    val verdict = { r: Double -> if (r > MockAccount.afterEven) 1 else if (r < -MockAccount.afterEven) -1 else 0 }   // ios: Mock/MockOrderViews.swift:697
    val reasons = done.groupBy { it.exit }.entries.sortedByDescending { it.value.size }   // ios: Mock/MockOrderViews.swift:698
    Card {   // ios: Mock/MockOrderViews.swift:699
        MockFlowHead("판 뒤 흐름", if (xs.isEmpty()) "쌓는 중" else "${xs.size}건", MockFlowRead.Kind.after, d, ctx, empty = xs.isEmpty())   // ios: Mock/MockOrderViews.swift:700
        if (xs.isEmpty()) {   // ios: Mock/MockOrderViews.swift:701
            mt("판 종목이 판 뒤 어떻게 갔는지 쌓여요", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:702
        } else {   // ios: Mock/MockOrderViews.swift:703
            // 이유별: 판 게 나았나, 기다렸으면 나았나
            reasons.forEach { e ->   // ios: Mock/MockOrderViews.swift:705
                val k = e.key; val v = e.value   // ios: Mock/MockOrderViews.swift:706
                val good = v.count { verdict(it.ret ?: 0.0) < 0 }; val bad = v.count { verdict(it.ret ?: 0.0) > 0 }   // ios: Mock/MockOrderViews.swift:707
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockOrderViews.swift:708
                    mt(k + " ${v.size}", style = appFont(13f, FontWeight.SemiBold))   // ios: Mock/MockOrderViews.swift:709
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:710
                    mt("판 게 나음 $good", style = appFont(12f), color = Theme.down)   // ios: Mock/MockOrderViews.swift:711
                    mt("기다렸으면 나음 $bad", style = appFont(12f), color = Theme.up)   // ios: Mock/MockOrderViews.swift:712
                }
            }
            if (reasons.isNotEmpty()) com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:715
            xs.takeLast(8).reversed().forEach { x ->   // ios: Mock/MockOrderViews.swift:716
                androidx.compose.runtime.key(x.at) {
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true })) {   // 길게 눌러 한 건만 지운다   // ios: Mock/MockOrderViews.swift:718,719
                        MockAfterLine(x, verdict)   // ios: Mock/MockOrderViews.swift:717
                        com.naeilo.app.ui.ContextMenu(show = menu, items = listOf(
                            com.naeilo.app.ui.MenuItem("이 기록 지우기", icon = "trash", destructive = true, onClick = { d.deleteAfter(x) }),   // ios: Mock/MockOrderViews.swift:720
                        ), onDismiss = { menu = false })
                    }
                }
            }
            mt("판 값보다 뒤에 내리면 잘 판 거예요. 오르면 판 자리가 반환점이었던 거예요. 앱이 꺼져 놓친 때는 비워 둬요", style = appFont(11f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:723,724
        }
    }
}

@Composable
private fun MockAfterLine(x: MockAfter, verdict: (Double) -> Int) {   // ios: Mock/MockOrderViews.swift:729
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Mock/MockOrderViews.swift:730
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockOrderViews.swift:731
            mt(MockFmt.md(x.day) + " " + MockFmt.time(x.at), Modifier.alignByBaseline(), style = appFont(11f).tnum(), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:732
            mt(x.name, Modifier.alignByBaseline().weight(1f, fill = false), style = appFont(13f, FontWeight.SemiBold), maxLines = 1)   // ios: Mock/MockOrderViews.swift:733
            mt(x.exit, Modifier.alignByBaseline(), style = appFont(12f), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:734
            Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:735
            val r = x.ret
            if (r != null) {   // ios: Mock/MockOrderViews.swift:736
                val won = (x.last?.second ?: x.p0) - x.p0   // ios: Mock/MockOrderViews.swift:737
                val v = verdict(r)   // ios: Mock/MockOrderViews.swift:738
                mt(if (v < 0) "판 게 나음" else if (v > 0) "기다렸으면 " + MockFmt.signedWon(won * x.qty.toDouble()) else "비슷", Modifier.alignByBaseline(),   // ios: Mock/MockOrderViews.swift:739
                    style = appFont(12f, FontWeight.Bold), color = if (v < 0) Theme.down else if (v > 0) Theme.up else Theme.sub)   // ios: Mock/MockOrderViews.swift:740
            } else {   // ios: Mock/MockOrderViews.swift:741
                mt("기다리는 중", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:742
            }
        }
        x.last?.let { l ->   // ios: Mock/MockOrderViews.swift:745
            mt("판 값 " + MockFmt.won(x.p0) + " → " + l.first + " " + MockFmt.won(l.second) + " (" + MockFmt.pct(l.second / x.p0 - 1, digits = 1) + ")",   // ios: Mock/MockOrderViews.swift:746
                style = appFont(12f).tnum(), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:747
        }
    }
}

// MARK: 뒤 흐름 AI 조언 — 제목 옆 단추로 그 흐름만 읽힌다. 사라·팔아라는 하지 않는다 (2026-10-29)

@Composable
private fun MockFlowHead(t: String, r: String, k: MockFlowRead.Kind, d: MockDesk, ctx: MockAccountCtx, empty: Boolean) {   // ios: Mock/MockOrderViews.swift:754
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:755
        mt(t, style = appFont(15f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:756
        if (MockAI.on) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:757
            val can = !(empty || d.aiBusy != null || !d.active)
            MockMiniButton(if (d.aiBusy == k.name) "읽는 중" else "AI 조언", Theme.teal, enabled = can) { ctx.launch { d.aiFlow(k) } }   // ios: Mock/MockOrderViews.swift:758,759,761
            // AI 매매 연결 자리 (꺼짐 모양). 드링커님이 조건을 정해 켜기 전까지 잠가 둔다 (켤 때는 실전 안전장치를 거치게 잇는다)
            Box(Modifier.size(34.dp, 22.dp).alpha(0.5f).background(Theme.line, RoundedCornerShape(50)).padding(2.dp)   // ios: Mock/MockOrderViews.swift:763,764,765
                .semantics { contentDescription = "AI 매매 · 잠김" }, contentAlignment = Alignment.CenterStart) {   // ios: Mock/MockOrderViews.swift:766
                Box(Modifier.size(18.dp).background(Color.White, CircleShape))   // ios: Mock/MockOrderViews.swift:764
            }
        }
        Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:768
        FitText(r, style = appFont(12f).tnum(), minScale = 0.7f, maxLines = 1, color = Theme.sub)   // ios: Mock/MockOrderViews.swift:769
        if (!empty) {   // ios: Mock/MockOrderViews.swift:770
            MockMiniButton("지우기", Theme.sub, enabled = true) { ctx.setClearAsk(k) }   // ios: Mock/MockOrderViews.swift:771,772
        }
    }
}

/** .buttonStyle(.bordered).controlSize(.mini): 옅은 바탕 작은 단추 */
@Composable
private fun MockMiniButton(title: String, tint: Color, enabled: Boolean, action: () -> Unit) {
    Box(Modifier.height(24.dp).clip(RoundedCornerShape(50)).background(tint.copy(alpha = if (enabled) 0.15f else 0.06f)).clickable(enabled = enabled, onClick = action).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        mt(title, Modifier.height(18.dp).wrapContentHeight(), style = appFont(12f, FontWeight.SemiBold), color = if (enabled) tint else Theme.muted, maxLines = 1)   // ios: Mock/MockOrderViews.swift:759,771
    }
}

/** 흐름 카드 밑 조언 상자: 마지막 읽기, AI 매매 자리(잠금), 지난 조언 */
@Composable
private fun MockReadCard(k: MockFlowRead.Kind, d: MockDesk, ctx: MockAccountCtx) {   // ios: Mock/MockOrderViews.swift:778
    val rs = (d.s.flowReads ?: emptyList<MockFlowRead>()).filter { it.kind == k && it.book == d.shown }   // ios: Mock/MockOrderViews.swift:779
    val open = ctx.pastReads.contains(k.name)   // ios: Mock/MockOrderViews.swift:780
    // 마지막 조언 뒤에 부른 결과 (실패·안 부름). 단추가 아무 일도 안 한 것처럼 보이지 않게
    val run = d.s.runs["ai"]?.let { r -> if (r.text.contains(MockDesk.aiName(k.name)) && r.at > (rs.lastOrNull()?.at ?: Double.NEGATIVE_INFINITY)) r else null }   // ios: Mock/MockOrderViews.swift:782
    if (run != null && rs.isEmpty()) {   // ios: Mock/MockOrderViews.swift:783
        Card(bg = Theme.tealBg, stroke = null) { mt(run.text, style = appFont(12f), color = if (run.ok) Theme.sub else Theme.up) }   // ios: Mock/MockOrderViews.swift:784
    }
    rs.lastOrNull()?.let { a ->   // ios: Mock/MockOrderViews.swift:786
        Card(Modifier.animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 400f)), bg = Theme.tealBg, stroke = null) {   // ios: Mock/MockOrderViews.swift:787,793
            MockHead("AI 조언", a.range)   // ios: Mock/MockOrderViews.swift:788
            if (run != null) mt(run.text, style = appFont(12f), color = if (run.ok) Theme.sub else Theme.up)   // ios: Mock/MockOrderViews.swift:789
            MockReadBody(a)   // ios: Mock/MockOrderViews.swift:790
            if (rs.size > 1) {   // ios: Mock/MockOrderViews.swift:791
                com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:792
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).clickable { ctx.togglePast(k.name) }, verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:793,794,799,801
                    mt("지난 조언 ${rs.size - 1}", style = appFont(13f, FontWeight.SemiBold))   // ios: Mock/MockOrderViews.swift:795
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:796
                    com.naeilo.app.ui.Sym(if (open) "chevron.down" else "chevron.right", 13f, Theme.muted)   // ios: Mock/MockOrderViews.swift:797
                }
                if (open) {   // ios: Mock/MockOrderViews.swift:802
                    rs.dropLast(1).reversed().forEach { p ->   // ios: Mock/MockOrderViews.swift:803
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Mock/MockOrderViews.swift:804
                            mt(MockFmt.md(MockFmt.day(p.at)) + " " + MockFmt.time(p.at) + " · " + p.range, style = appFont(11f).tnum(), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:805
                            MockReadBody(p)   // ios: Mock/MockOrderViews.swift:806
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MockReadBody(a: MockFlowRead) {   // ios: Mock/MockOrderViews.swift:815
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockOrderViews.swift:816,822
        mt(a.text, style = appFont(13f))   // ios: Mock/MockOrderViews.swift:817
        a.terms.forEach { t ->   // ios: Mock/MockOrderViews.swift:818
            mt((t.firstOrNull() ?: "") + " · " + (t.drop(1).firstOrNull() ?: ""), style = appFont(12f), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:819
        }
    }
}

// MARK: 체결 차이 — 체결 한 건이 막대 하나. 비싸게 사면(불리) 위, 싸게 사면 아래

@Composable
private fun MockGapCard(d: MockDesk) {   // ios: Mock/MockOrderViews.swift:827
    val today = MockFmt.day(mockNow())   // ios: Mock/MockOrderViews.swift:828
    val fs = d.ledgerEvents(d.shown).filter { it.kind == MockEvent.Kind.fill && it.expect != null && it.price != null && !it.test }   // ios: Mock/MockOrderViews.swift:829
    val gap = { e: MockEvent -> MockCalc.slipGap(side = e.side ?: MockSide.buy, expect = e.expect ?: 0.0, actual = e.price ?: 0.0) }   // ios: Mock/MockOrderViews.swift:830
    val now = fs.filter { MockFmt.day(it.at) == today }   // ios: Mock/MockOrderViews.swift:831
    val past = fs.filter { MockFmt.day(it.at) != today }.takeLast(12)   // ios: Mock/MockOrderViews.swift:832
    val nowBars = now.takeLast(20)   // 막대는 오늘 최근 20건까지 (평균·금액은 오늘 모두)   // ios: Mock/MockOrderViews.swift:833
    val sp = if (nowBars.size + past.size > 10) 3.dp else 8.dp   // 막대가 많으면 붙여서 카드 폭 안에   // ios: Mock/MockOrderViews.swift:834
    val won = now.fold(0.0) { a, e -> a + ((e.price ?: 0.0) - (e.expect ?: 0.0)) * (e.qty ?: 0).toDouble() * (if (e.side == MockSide.sell) -1 else 1) }   // ios: Mock/MockOrderViews.swift:835
    val avg = if (now.isEmpty()) 0.0 else now.map(gap).fold(0.0) { a, b -> a + b } / now.size.toDouble()   // ios: Mock/MockOrderViews.swift:836
    val top = max(0.002, (now + past).map { abs(gap(it)) }.maxOrNull() ?: 0.0)   // ios: Mock/MockOrderViews.swift:837
    Card {   // ios: Mock/MockOrderViews.swift:838
        MockHead("체결 차이", if (now.isEmpty()) "오늘 없음" else "평균 " + MockFmt.pct(avg) + " · " + MockFmt.signedWon(-won))   // ios: Mock/MockOrderViews.swift:839
        if (fs.isEmpty()) {   // ios: Mock/MockOrderViews.swift:840
            mt("아직 체결 없음", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:841
        } else {   // ios: Mock/MockOrderViews.swift:842
            val line = Theme.line
            Row(Modifier.fillMaxWidth().drawBehind { drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2 - 0.75.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())) },   // ios: Mock/MockOrderViews.swift:855
                horizontalArrangement = Arrangement.spacedBy(sp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:843
                Column(Modifier.height(96.dp).padding(end = 8.dp - sp)) {   // ios: Mock/MockOrderViews.swift:844,849
                    val f11 = appFont(11f, FontWeight.SemiBold)
                    mt("비싸게", style = f11, color = Theme.orange, maxLines = 1)   // ios: Mock/MockOrderViews.swift:845
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:846
                    mt("싸게", style = f11, color = Theme.teal, maxLines = 1)   // ios: Mock/MockOrderViews.swift:847
                }
                nowBars.forEach { MockGapBar(gap(it), top) }   // ios: Mock/MockOrderViews.swift:850
                if (now.isNotEmpty() && past.isNotEmpty()) Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 70.dp).background(Theme.line))   // ios: Mock/MockOrderViews.swift:851
                past.forEach { Box(Modifier.alpha(0.45f)) { MockGapBar(gap(it), top) } }   // ios: Mock/MockOrderViews.swift:852
                Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Mock/MockOrderViews.swift:853
            }
            Row(Modifier.padding(start = 46.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockOrderViews.swift:856,861
                val f11 = appFont(11f)
                if (now.isNotEmpty()) mt("오늘", Modifier.widthIn(max = (18.dp + sp) * nowBars.size), style = f11, color = Theme.muted, maxLines = 1)   // ios: Mock/MockOrderViews.swift:857
                if (past.isNotEmpty()) mt("지난 날", style = f11, color = Theme.muted)   // ios: Mock/MockOrderViews.swift:858
                Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:859
            }
        }
    }
}

@Composable
private fun MockGapBar(g: Double, top: Double) {   // ios: Mock/MockOrderViews.swift:866
    val h = max(3.0, 46 * min(1.0, abs(g) / top)).dp   // ios: Mock/MockOrderViews.swift:867
    val r = if (g > 0) RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp) else RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)   // ios: Mock/MockOrderViews.swift:868
    Box(Modifier.widthIn(max = 18.dp).width(18.dp).height(96.dp)) {   // ios: Mock/MockOrderViews.swift:870
        Box(Modifier.align(Alignment.Center).offset(y = if (g > 0) -h / 2 else h / 2).widthIn(max = 18.dp).fillMaxWidth().height(h).background(if (g > 0) Theme.orange else Theme.teal, r))   // ios: Mock/MockOrderViews.swift:869,870
    }
}

// MARK: 불빛 둘 — [실전] 실전 자동주문 · 모두 멈춤 / [모의] 돌리기 주문 · 돌리기 멈춤

/** 자동주문 불빛은 '고르고 주문' 구간 밖이면 정지로 보인다 (저장된 켬·끔은 그대로, 누르면 켬·끔이 바뀐다) */
@Composable
private fun MockLights(d: MockDesk, ctx: MockAccountCtx) {   // ios: Mock/MockOrderViews.swift:876
    val seg = d.seg(everyMinute())   // ios: Mock/MockOrderViews.swift:877,880
    val stop = !seg.buys; val stopText = "자동 정지 · " + seg.label   // ios: Mock/MockOrderViews.swift:881
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockOrderViews.swift:882
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Mock/MockOrderViews.swift:883
            if (d.shown == MockBook.real) {   // ios: Mock/MockOrderViews.swift:884
                MockLight("실전 자동주문", if (d.live) (if (stop) stopText else "켬") else "끔", icon = "power", on = d.live && !stop, color = Theme.orange, ctx) {   // ios: Mock/MockOrderViews.swift:885
                    if (d.live) ctx.setNote(d.setLive(false)) else { d.checkLive(); ctx.setLiveAsk(true) }   // ios: Mock/MockOrderViews.swift:886
                }
                MockLight("모두 멈춤", if (d.halted) "멈춤" else "도는 중", icon = "pause.fill", on = d.halted, color = Theme.up, ctx) {   // ios: Mock/MockOrderViews.swift:888
                    if (d.halted) d.resumeAll() else d.haltAll()   // ios: Mock/MockOrderViews.swift:889
                }
            } else {   // ios: Mock/MockOrderViews.swift:891
                // 모의: 돌리기(한 바퀴 자동)에 주문까지 · 돌리기 자체를 멈춤 (풀면 지난 간격으로)
                MockLight("모의 자동주문", if (d.autoOrder) (if (stop) stopText else "켬") else "끔", icon = "power", on = d.autoOrder && !stop, color = Theme.teal, ctx) {   // ios: Mock/MockOrderViews.swift:893
                    val v = !d.autoOrder   // ios: Mock/MockOrderViews.swift:894
                    d.edit(if (v) "돌리기 주문 켬" else "돌리기 주문 끔") { it.autoOrder = v }   // ios: Mock/MockOrderViews.swift:895
                }
                MockLight("돌리기 멈춤", if (d.s.auto == 0) "멈춤" else "${d.s.auto}분마다 도는 중", icon = "pause.fill", on = d.s.auto == 0, color = Theme.down, ctx) {   // ios: Mock/MockOrderViews.swift:897
                    if (d.s.auto == 0) {   // ios: Mock/MockOrderViews.swift:898
                        val m = if (ctx.autoLast > 0) ctx.autoLast else 10   // ios: Mock/MockOrderViews.swift:899
                        d.edit("자동 ${m}분") { it.auto = m }   // ios: Mock/MockOrderViews.swift:900
                    } else {   // ios: Mock/MockOrderViews.swift:901
                        ctx.setAutoLast(d.s.auto)   // ios: Mock/MockOrderViews.swift:902
                        d.edit("자동 끔") { it.auto = 0 }   // ios: Mock/MockOrderViews.swift:903
                    }
                }
            }
        }
        ctx.note?.let { mt(it, style = appFont(12f), color = Theme.up) }   // ios: Mock/MockOrderViews.swift:908
    }
}

@Composable
private fun RowScope.MockLight(t: String, state: String, icon: String, on: Boolean, color: Color, ctx: MockAccountCtx, act: suspend () -> Unit) {   // ios: Mock/MockOrderViews.swift:912
    Column(Modifier.weight(1f).background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp))   // ios: Mock/MockOrderViews.swift:925,926,927
        .clickable { ctx.launch { act() } }.padding(vertical = 18.dp)   // ios: Mock/MockOrderViews.swift:913,925,929
        .semantics(mergeDescendants = true) { contentDescription = t + " · " + state },   // ios: Mock/MockOrderViews.swift:930
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Mock/MockOrderViews.swift:914
        Box(Modifier.padding(4.dp).size(64.dp, 64.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:915,921
            Box(Modifier.size(64.dp, 64.dp).shadow(if (on) 12.dp else 0.dp, CircleShape, ambientColor = color.copy(alpha = 0.55f), spotColor = color.copy(alpha = 0.55f)).background(if (on) color else Theme.line, CircleShape))   // ios: Mock/MockOrderViews.swift:916,917
            if (!on) Box(Modifier.size(64.dp, 64.dp).border(2.dp, Theme.muted.copy(alpha = 0.4f), CircleShape))   // ios: Mock/MockOrderViews.swift:918
            com.naeilo.app.ui.Sym(icon, 24f, if (on) Color.White else Theme.muted)   // ios: Mock/MockOrderViews.swift:919
        }
        mt(t, style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Mock/MockOrderViews.swift:922
        mt(state, style = appFont(12f, FontWeight.SemiBold), color = if (on) color else Theme.sub)   // ios: Mock/MockOrderViews.swift:923
    }
}

// MARK: 모의투자 설정 (계좌 머리 톱니)

@Composable
fun MockSettingsView(dismiss: () -> Unit) {   // ios: Mock/MockOrderViews.swift:936,937
    var findOpen by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:938
    var draft by remember { mutableStateOf(MockPieceDraft()) }   // ios: Mock/MockOrderViews.swift:939
    var adding by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:940
    var resetAsk by remember { mutableStateOf(false) }   // ios: Mock/MockOrderViews.swift:941
    var fees by remember { mutableStateOf(MockDesk.shared.s.fees) }   // ios: Mock/MockOrderViews.swift:942
    var auto by remember { mutableStateOf(MockDesk.shared.s.auto) }   // ios: Mock/MockOrderViews.swift:943
    var guards by remember { mutableStateOf(MockDesk.shared.guards) }   // ios: Mock/MockOrderViews.swift:944
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val d = MockDesk.shared   // ios: Mock/MockOrderViews.swift:946,947

    /** 예외 묶음 고치기 (첫 조건을 펼침에 채운다) */
    fun editAction(b: MockBundle): (() -> Unit)? {   // ios: Mock/MockOrderViews.swift:1020
        val p = b.pieces.firstOrNull() ?: return null   // ios: Mock/MockOrderViews.swift:1021
        return {   // ios: Mock/MockOrderViews.swift:1022
            draft = MockPieceDraft(p)   // ios: Mock/MockOrderViews.swift:1023
            adding = true   // ios: Mock/MockOrderViews.swift:1024
        }
    }

    /** 예외 조건은 조건 하나가 묶음 하나 (어느 하나라도 걸리면 뺀다) */
    fun commitException(d: MockDesk, p: MockPiece) {   // ios: Mock/MockOrderViews.swift:1029
        d.edit("예외 조건 · " + p.text) { s ->   // ios: Mock/MockOrderViews.swift:1030
            val i = s.exceptions.bundles.indexOfFirst { b -> b.pieces.any { it.id == p.id } }   // ios: Mock/MockOrderViews.swift:1031
            if (i >= 0) {
                val b = s.exceptions.bundles[i]   // ios: Mock/MockOrderViews.swift:1032
                val j = b.pieces.indexOfFirst { it.id == p.id }; if (j >= 0) b.pieces = b.pieces.toMutableList().also { it[j] = p }   // ios: Mock/MockOrderViews.swift:1033
                b.name = b.pieces.joinToString(" · ") { it.text }   // ios: Mock/MockOrderViews.swift:1034
                s.exceptions.bundles = s.exceptions.bundles.toMutableList().also { it[i] = b }   // ios: Mock/MockOrderViews.swift:1035
            } else {   // ios: Mock/MockOrderViews.swift:1036
                s.exceptions.bundles = s.exceptions.bundles + MockBundle(name = p.text, pieces = listOf(p))   // ios: Mock/MockOrderViews.swift:1037
            }
        }
        adding = false   // ios: Mock/MockOrderViews.swift:1040
    }

    fun save() {   // ios: Mock/MockOrderViews.swift:1160,1161
        val what = mutableListOf<String>()   // ios: Mock/MockOrderViews.swift:1162
        if (fees != d.s.fees) what.add("수수료 " + cformat("%.3f%%", fees.commission * 100) + " · 세금 " + cformat("%.2f%%", fees.sellTax * 100))   // ios: Mock/MockOrderViews.swift:1163
        if (auto != d.s.auto) what.add(if (auto == 0) "자동 끔" else "자동 ${auto}분")   // ios: Mock/MockOrderViews.swift:1164
        if (guards != d.guards) {   // ios: Mock/MockOrderViews.swift:1165
            what.add("하루 손실 한도 −" + MockFmt.num(guards.dayLoss * 100) + "% · 한 번 주문 " + MockFmt.man(guards.orderCap) + " · 시세 호출 초당 " + MockFmt.num(guards.perSec) + "번"   // ios: Mock/MockOrderViews.swift:1166
                + " · 시장 날씨 " + (guards.marketDrop?.let { "−" + MockFmt.num(it * 100) + "%" } ?: "끔")   // ios: Mock/MockOrderViews.swift:1167
                + " · 연속 손절 " + (guards.lossStreak?.let { "${it}번" } ?: "끔")   // ios: Mock/MockOrderViews.swift:1168
                + " · 호가 벽 " + (guards.bookWall?.let { MockFmt.num(it) + "배" } ?: "끔"))   // ios: Mock/MockOrderViews.swift:1169
            if (guards.lossStreak != d.guards.lossStreak) d.touchArm("streak")   // ios: Mock/MockOrderViews.swift:1170
            if (guards.bookWall != d.guards.bookWall) d.touchArm("wall")   // ios: Mock/MockOrderViews.swift:1171
            val n = guards.perSec   // ios: Mock/MockOrderViews.swift:1172
            MockGate.set(n)   // ios: Mock/MockOrderViews.swift:1173
        }
        if (what.isEmpty()) return   // ios: Mock/MockOrderViews.swift:1175
        d.edit("설정 · " + what.joinToString(" · ")) { s ->   // ios: Mock/MockOrderViews.swift:1176
            s.fees = fees; s.auto = auto; s.guards = guards   // ios: Mock/MockOrderViews.swift:1177
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.bg), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockOrderViews.swift:948,961
        MockSheetBar(title = "모의투자 설정") { dismiss() }   // ios: Mock/MockOrderViews.swift:949
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Mock/MockOrderViews.swift:950,951,958
            // 예외 종목
            run {
                val ex = d.s.exceptions   // ios: Mock/MockOrderViews.swift:978,979
                Card {   // ios: Mock/MockOrderViews.swift:952,980
                    Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:981
                        mt("예외 종목", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:982
                        Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:983
                        mt("사지도 팔지도 않음", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:984
                    }
                    com.naeilo.app.ui.ToggleRow(checked = ex.mine, onChange = { v -> d.edit(if (v) "예외: 내 보유 종목 전부 켬" else "예외: 내 보유 종목 전부 끔") { it.exceptions.mine = v } }, tint = Theme.teal) {   // ios: Mock/MockOrderViews.swift:986,992
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {   // ios: Mock/MockOrderViews.swift:987
                            mt("내 보유 종목 전부", style = appFont(14f, FontWeight.SemiBold))   // ios: Mock/MockOrderViews.swift:988
                            mt("지금 ${d.mine.size}종목", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:989
                        }
                    }
                    com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:993
                    mt("직접", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:994
                    com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockOrderViews.swift:995
                        ex.direct.forEach { st ->   // ios: Mock/MockOrderViews.swift:996
                            MockCondPill(text = st.name, delete = { d.edit("예외 종목 뺌 · ${st.name}") { s -> s.exceptions.direct = s.exceptions.direct.filter { it.code != st.code } } })   // ios: Mock/MockOrderViews.swift:997
                        }
                        MockAddPill(title = "+ 추가") { findOpen = true }   // ios: Mock/MockOrderViews.swift:999
                    }
                    mt("조건", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Mock/MockOrderViews.swift:1001
                    com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockOrderViews.swift:1002
                        ex.bundles.forEach { b ->   // ios: Mock/MockOrderViews.swift:1003
                            MockCondPill(text = b.pieces.joinToString(" · ") { it.text },   // ios: Mock/MockOrderViews.swift:1004
                                edit = editAction(b),   // ios: Mock/MockOrderViews.swift:1005
                                delete = { d.edit("예외 조건 지움 · " + b.pieces.joinToString(" · ") { it.text }) { s -> s.exceptions.bundles = s.exceptions.bundles.filter { it.id != b.id } } })   // ios: Mock/MockOrderViews.swift:1006
                        }
                        MockAddPill(title = "+ 추가") { draft = MockPieceDraft(kind = MockPiece.Kind.price); adding = true }   // ios: Mock/MockOrderViews.swift:1008
                    }
                    if (adding) {   // ios: Mock/MockOrderViews.swift:1010
                        MockPieceForm(draft = draft, setDraft = { draft = it }, close = { adding = false }) { p -> commitException(d, p) }   // ios: Mock/MockOrderViews.swift:1011
                    }
                    if (ex.bundles.isNotEmpty()) {   // ios: Mock/MockOrderViews.swift:1013
                        mt("마지막 훑기에서 조건에 걸린 ${d.s.exceptHits.size}종목 (다음 훑기 때 다시 셈)", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1014
                    }
                }
            }
            // 장부
            run {
                val l = d.ledger(d.shown)   // ios: Mock/MockOrderViews.swift:1043,1044
                Card {   // ios: Mock/MockOrderViews.swift:953,1045
                    Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:1046
                        mt("장부", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:1047
                        Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:1048
                        mt(d.shown.label + " · 시작 ${MockFmt.day(l.started)} · 평가 " + MockFmt.man(d.equity(d.shown)), style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1049
                    }
                    // 장부(state.json)와 기록(journal.jsonl) 파일을 공유 시트로 내보낸다
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { mockShare(ctx, MockDesk.exportFiles) }, verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:1052,1053,1058,1060
                        mt("내보내기", style = appFont(15f, FontWeight.SemiBold), color = Theme.ink)   // ios: Mock/MockOrderViews.swift:1054
                        Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:1055
                        mt("장부 · 기록 파일", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1056
                    }
                    com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:1061
                    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { resetAsk = true }, contentAlignment = Alignment.CenterStart) {   // ios: Mock/MockOrderViews.swift:1062,1064,1066
                        mt("장부 처음부터", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)   // ios: Mock/MockOrderViews.swift:1063
                    }
                    if (d.s.archived.isNotEmpty()) mt("보관한 옛 장부 ${d.s.archived.size}개", style = appFont(11f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1067
                }
            }
            // 돌리기
            Card {   // ios: Mock/MockOrderViews.swift:954,1142,1143
                mt("돌리기", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:1144
                MockCycleLine(order = MockDesk.shared.autoOrder, setOrder = { v -> MockDesk.shared.edit(if (v) "돌리기 주문 켬" else "돌리기 주문 끔") { it.autoOrder = v } },   // ios: Mock/MockOrderViews.swift:1145
                    sell = MockDesk.shared.autoSell) { v -> MockDesk.shared.edit(if (v) "돌리기 팔기 켬" else "돌리기 팔기 끔") { it.autoSell = v } }   // ios: Mock/MockOrderViews.swift:1146
                com.naeilo.app.ui.DropMenu(items = listOf(0, 5, 10, 15, 30, 60).map { n -> com.naeilo.app.ui.MenuItem(if (n == 0) "끔" else "${n}분마다") { auto = n } }) {   // ios: Mock/MockOrderViews.swift:1147,1148
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:1149
                        mt("한 바퀴 자동 (장중)", style = appFont(14f))   // ios: Mock/MockOrderViews.swift:1149
                        Spacer(Modifier.weight(1f))
                        mt(if (auto == 0) "끔" else "${auto}분마다", style = appFont(14f), color = Theme.teal)   // ios: Mock/MockOrderViews.swift:1150
                    }
                }
                com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:1151
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockOrderViews.swift:1152
                    MockStepper(fees.commission, 0.0..0.005, 0.00001, { fees = fees.copy(commission = it) }) { mt("수수료 (한쪽) " + cformat("%.3f%%", fees.commission * 100), style = appFont(14f)) }   // ios: Mock/MockOrderViews.swift:1153
                    MockStepper(fees.sellTax, 0.0..0.01, 0.0005, { fees = fees.copy(sellTax = it) }) { mt("매도 세금 " + cformat("%.2f%%", fees.sellTax * 100), style = appFont(14f)) }   // ios: Mock/MockOrderViews.swift:1154
                }
                mt("앱이 열려 있을 때만 돌아요.", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1156
            }
            // 안전장치: 하루 손실 한도 · 한 번 주문 상한 · 모두 멈춤
            Card {   // ios: Mock/MockOrderViews.swift:955,1082,1083
                Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockOrderViews.swift:1084
                    mt("안전장치", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:1085
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockOrderViews.swift:1086
                    if (d.dayLossHit) mt("오늘 손실 한도 넘음 · 새로 사지 않음", style = appFont(12f, FontWeight.SemiBold), color = Theme.up)   // ios: Mock/MockOrderViews.swift:1087
                }
                MockStepper(guards.dayLoss, 0.01..0.10, 0.005, { guards = guards.copy(dayLoss = it) }) {   // ios: Mock/MockOrderViews.swift:1089
                    mt("하루 손실 한도 −" + MockFmt.num(guards.dayLoss * 100) + "%", style = appFont(14f))   // ios: Mock/MockOrderViews.swift:1090
                }
                MockStepper(guards.orderCap, 500_000.0..10_000_000.0, 500_000.0, { guards = guards.copy(orderCap = it) }) {   // ios: Mock/MockOrderViews.swift:1092
                    mt("한 번 매수 주문 ≤ " + MockFmt.man(guards.orderCap), style = appFont(14f))   // ios: Mock/MockOrderViews.swift:1093
                }
                MockStepper(guards.perSec, 1.0..20.0, 1.0, { guards = guards.copy(apiPerSec = it) }) {   // ios: Mock/MockOrderViews.swift:1095
                    mt("토스 시세 호출 초당 " + MockFmt.num(guards.perSec) + "번까지", style = appFont(14f))   // ios: Mock/MockOrderViews.swift:1096
                }
                MockOptStep(on = guards.marketDrop != null, toggle = { guards = guards.copy(marketDrop = if (it) 0.01 else null) },   // ios: Mock/MockOrderViews.swift:1098
                    value = guards.marketDrop ?: 0.01, set = { guards = guards.copy(marketDrop = it) }, range = 0.005..0.05, step = 0.005,   // ios: Mock/MockOrderViews.swift:1099
                    text = "시장 날씨 · 그 시장 ETF −" + MockFmt.num((guards.marketDrop ?: 0.01) * 100) + "% 넘게 내리면 안 삼")   // ios: Mock/MockOrderViews.swift:1100
                MockOptStep(on = guards.lossStreak != null, toggle = { guards = guards.copy(lossStreak = if (it) 3 else null) },   // ios: Mock/MockOrderViews.swift:1101
                    value = (guards.lossStreak ?: 3).toDouble(), set = { guards = guards.copy(lossStreak = it.toInt()) }, range = 2.0..6.0, step = 1.0,   // ios: Mock/MockOrderViews.swift:1102
                    text = "오늘 손절 ${guards.lossStreak ?: 3}번 연달아면 그날 안 삼")   // ios: Mock/MockOrderViews.swift:1103
                MockOptStep(on = guards.bookWall != null, toggle = { guards = guards.copy(bookWall = if (it) 3.0 else null) },   // ios: Mock/MockOrderViews.swift:1104
                    value = guards.bookWall ?: 3.0, set = { guards = guards.copy(bookWall = it) }, range = 1.5..10.0, step = 0.5,   // ios: Mock/MockOrderViews.swift:1105
                    text = "호가 벽 · 매도 잔량이 매수의 " + MockFmt.num(guards.bookWall ?: 3.0) + "배 넘으면 미룸")   // ios: Mock/MockOrderViews.swift:1106
                mt("꺼 둔 것도 살 때마다 값을 적어요. 매매 ${MockCalc.armTrades}건·${MockCalc.armDays}일이 쌓이고 켰다면 나았으면 저절로 켜요. 손으로 만진 것은 그대로 둬요.",   // ios: Mock/MockOrderViews.swift:1107
                    style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1108
                com.naeilo.app.ui.ToggleRow(checked = d.fillAlert, onChange = { d.setFillAlert(it) }, tint = Theme.teal) {   // ios: Mock/MockOrderViews.swift:1109,1112
                    mt("체결 알림 (체결·부분 체결·거부)", style = appFont(14f))   // ios: Mock/MockOrderViews.swift:1110
                }
                MockBook.entries.forEach { b ->   // ios: Mock/MockOrderViews.swift:1113
                    d.check(b)?.let { c ->   // ios: Mock/MockOrderViews.swift:1114
                        mt("잔고 맞추기 · " + b.label + " · " + MockFmt.stamp(c.at) + " · " + c.text, style = appFont(12f),   // ios: Mock/MockOrderViews.swift:1115
                            color = if (c.ok) Theme.muted else Theme.up)   // ios: Mock/MockOrderViews.swift:1116
                    }
                }
                mt("지금 잔고 맞춰 보기", Modifier.clickable { d.runCheck() }, style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Mock/MockOrderViews.swift:1119,1120
                com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockOrderViews.swift:1121
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).background(if (d.halted) Theme.teal else Theme.up, RoundedCornerShape(12.dp))   // ios: Mock/MockOrderViews.swift:1124,1125
                    .clickable { if (d.halted) d.resumeAll() else d.haltAll() }, contentAlignment = Alignment.Center) {   // ios: Mock/MockOrderViews.swift:1122,1127
                    mt(if (d.halted) "다시 시작" else "모두 멈춤", style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Mock/MockOrderViews.swift:1123
                }
                mt(if (d.halted) "멈춤 중: 한 바퀴·돌리기·자동 팔기와 규칙 주문을 막아요. 손 주문은 돼요."   // ios: Mock/MockOrderViews.swift:1128
                    else "누르면 모든 대기 주문을 취소하고 자동매매를 멈춰요.", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockOrderViews.swift:1129
            }
            /** 앱을 켤 때 시작 칸 (기본 모의). 실주문 켜고 끄기는 계좌 탭 불빛에서 */
            Card {   // ios: Mock/MockOrderViews.swift:956,1072,1073
                com.naeilo.app.ui.ToggleRow(checked = d.s.startReal == true, onChange = { v -> d.setView { it.startReal = if (v) true else null } }, tint = Theme.up) {   // ios: Mock/MockOrderViews.swift:1074,1077
                    mt("시작 실전", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockOrderViews.swift:1075
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { save() } }   // ios: Mock/MockOrderViews.swift:962
    com.naeilo.app.ui.Sheet(show = findOpen, onDismiss = { findOpen = false }) {   // ios: Mock/MockOrderViews.swift:963
        MockStockFinder(title = "예외 종목 넣기", pick = { st ->   // ios: Mock/MockOrderViews.swift:964
            d.edit("예외 종목 넣음 · ${st.name}") { s ->   // ios: Mock/MockOrderViews.swift:965
                if (s.exceptions.direct.none { it.code == st.code }) s.exceptions.direct = s.exceptions.direct + st   // ios: Mock/MockOrderViews.swift:966
            }
        }, dismiss = { findOpen = false })
    }
    com.naeilo.app.ui.ConfirmSheet(show = resetAsk, title = "장부를 처음부터 할까요?",   // ios: Mock/MockOrderViews.swift:970
        message = "현금 1,000만원으로 다시 시작해요. 옛 장부와 기록은 지우지 않아요.",   // ios: Mock/MockOrderViews.swift:973,974
        actions = listOf(com.naeilo.app.ui.DialogAction("처음부터 (옛 장부는 보관)", destructive = true, onClick = { d.reset(d.shown); resetAsk = false })),   // ios: Mock/MockOrderViews.swift:971
        cancel = "그만두기", onDismiss = { resetAsk = false })
}

/** ShareLink(items:) 대신: 파일을 cacheDir/share 로 옮겨 공유 시트에 넘긴다 (FileProvider 는 그 폴더만 연다) */
private fun mockShare(ctx: android.content.Context, files: List<java.io.File>) {
    val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
    val uris = ArrayList(files.mapNotNull { f ->
        runCatching { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f.copyTo(java.io.File(dir, f.name), overwrite = true)) }.getOrNull()
    })
    if (uris.isEmpty()) return
    val send = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType("*/*")
        .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 켜고 끄는 안전장치 한 줄: 켜면 값 계단이 붙는다 */
@Composable
private fun MockOptStep(on: Boolean, toggle: (Boolean) -> Unit, value: Double, set: (Double) -> Unit, range: ClosedFloatingPointRange<Double>, step: Double, text: String) {   // ios: Mock/MockOrderViews.swift:1134
    com.naeilo.app.ui.ToggleRow(checked = on, onChange = toggle, tint = Theme.teal) { mt(text, style = appFont(14f)) }   // ios: Mock/MockOrderViews.swift:1135,1136
    if (on) {   // ios: Mock/MockOrderViews.swift:1137
        MockStepper(value, range, step, set) { mt("값 바꾸기", style = appFont(12f), color = Theme.muted) }   // ios: Mock/MockOrderViews.swift:1138
    }
}

/** iOS Stepper: 이름표 + [− | +] (범위 안에서 한 계단씩) */
@Composable
private fun MockStepper(value: Double, range: ClosedFloatingPointRange<Double>, step: Double, set: (Double) -> Unit, label: @Composable RowScope.() -> Unit) {
    fun go(k: Int) {
        val v = kotlin.math.round((value + k * step) / step) * step
        set(v.coerceIn(range.start, range.endInclusive))
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        label()
        Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
        Row(Modifier.background(Theme.rule.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val lo = value > range.start + step / 2
            val hi = value < range.endInclusive - step / 2
            Box(Modifier.width(46.dp).fillMaxHeight().clickable(enabled = lo) { go(-1) }, contentAlignment = Alignment.Center) {
                com.naeilo.app.ui.Sym("minus", 14f, if (lo) Theme.ink else Theme.muted)
            }
            Box(Modifier.width(1.dp).height(18.dp).background(Theme.line))
            Box(Modifier.width(46.dp).fillMaxHeight().clickable(enabled = hi) { go(1) }, contentAlignment = Alignment.Center) {
                com.naeilo.app.ui.Sym("plus", 14f, if (hi) Theme.ink else Theme.muted)
            }
        }
    }
}

/** 투자 모드의 네 탭 (홈 다음 자리, 태그는 원래 탭 그대로. 미션은 App 이 그대로 붙인다) */
object MockTabs {   // ios: Mock/MockOrderViews.swift:1183
    /** 원래 탭 자리 → 투자 모드 탭 */
    fun tab(t: com.naeilo.app.Tab): MockTab? = when (t) {
        com.naeilo.app.Tab.hold -> MockTab.observe   // ios: Mock/MockOrderViews.swift:1186
        com.naeilo.app.Tab.flow -> MockTab.select   // ios: Mock/MockOrderViews.swift:1188
        com.naeilo.app.Tab.analysis -> MockTab.journal   // ios: Mock/MockOrderViews.swift:1190
        // 투자 모드에서는 미션 칸 자리 (미션은 투자 모드를 끄면 돌아온다). 계좌도 [모의]·[실전]을 따른다
        com.naeilo.app.Tab.board -> MockTab.account   // ios: Mock/MockOrderViews.swift:1193
        else -> null
    }

    @Composable
    fun page(t: MockTab) {   // ios: Mock/MockOrderViews.swift:1184
        when (t) {
            MockTab.observe -> MockObserveView()   // ios: Mock/MockOrderViews.swift:1185
            MockTab.select -> MockSelectView()   // ios: Mock/MockOrderViews.swift:1187
            MockTab.journal -> MockJournalView()   // ios: Mock/MockOrderViews.swift:1189
            MockTab.account -> MockAccountView()   // ios: Mock/MockOrderViews.swift:1192
        }
    }
}

/** 홈 평가액 박스: 들어갈 수 있으면(설정에서 모의투자 허용 + 개발자 기기 또는 토스 키) 금액 글자 밖을 누를 때 투자 모드를 켜고 끈다 (금액 글자 탭 = 가리기, 길게 = 모양 고르기는 그대로) */
/** 홈 Summary 의 detectTapGestures(onTap) 가 tap() 을 부른다 (길게 누름 = 모양 고르기와 한 제스처로 받아야 놓을 때 탭이 같이 안 걸린다) */
object MockEntry {   // ios: Mock/MockOrderViews.swift:1198,1199
    fun tap() {
        val d = MockDesk.shared   // ios: Mock/MockOrderViews.swift:1200
        if (d.canEnter) d.on = !d.on   // ios: Mock/MockOrderViews.swift:1201,1202,1203
    }

    @Composable
    fun Feedback() {
        val d = MockDesk.shared
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        var first by remember { mutableStateOf(true) }
        LaunchedEffect(d.on) { if (first) first = false else haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }   // ios: Mock/MockOrderViews.swift:1204
    }
}

// MARK: 탭 묶음 (RootView 가 투자 모드에서 쓴다)

/** phase = 앱이 앞에 있음 (iOS ScenePhase.active) */
@Composable
fun MockTabsHooks(mine: Set<String>, phase: Boolean) {   // ios: Mock/MockOrderViews.swift:1210,1211,1212,1213
    val d = MockDesk.shared   // ios: Mock/MockOrderViews.swift:1214,1215
    LaunchedEffect(mine) { d.mine = mine }   // ios: Mock/MockOrderViews.swift:1216,1217
    // 멈춘 훑기는 앱이 다시 앞에 오면 이어 받는다
    var last by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) { if (phase != last) { last = phase; if (phase && d.active) d.resume() } }   // ios: Mock/MockOrderViews.swift:1219
    // 투자 모드이고 앱이 앞에 있는 동안만 1분마다: 가격·대기 주문·팔기 규칙·자동 (AI 는 꺼 둠)
    LaunchedEffect(d.active && phase) {   // ios: Mock/MockOrderViews.swift:1221
        if (!(d.active && phase)) return@LaunchedEffect   // ios: Mock/MockOrderViews.swift:1222
        d.refreshWallet()   // ios: Mock/MockOrderViews.swift:1223
        while (isActive) {   // ios: Mock/MockOrderViews.swift:1224
            d.tick()   // ios: Mock/MockOrderViews.swift:1225
            delay(60000)   // ios: Mock/MockOrderViews.swift:1226
        }
    }
}
