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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.core.cformat
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.LocalFg
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.Spinner
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hold.MoodBand
import com.naeilo.app.ui.hold.moodBackdrop
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.home.BoxSchemeScope
import com.naeilo.app.ui.home.boxInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.layout
import com.naeilo.app.ui.outset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.wrapContentHeight

// 모의투자 세 화면(관찰·선정·일지)의 공통 부품 · 관찰 · 선정.
// 개발자 전용 화면이라 글은 번역 목록(Localizable)에 넣지 않는다: 모든 글은 mt(…) = 번역 없이 그대로

/** Text(verbatim:) 자리: tr() 를 거치지 않는다 */
@Composable
fun mt(s: String, modifier: Modifier = Modifier, style: TextStyle = appFont(17f), color: Color = LocalFg.current(LocalPal.current), maxLines: Int = Int.MAX_VALUE) =   // ios: Mock/MockViews.swift:7
    BasicText(s, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
@Composable
fun mt(s: AnnotatedString, modifier: Modifier = Modifier, style: TextStyle = appFont(17f), color: Color = LocalFg.current(LocalPal.current), maxLines: Int = Int.MAX_VALUE) =
    BasicText(s, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
/** .monospacedDigit() */
internal fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = "tnum")
/** TimelineView(.everyMinute): 1분마다 지금 시각 */
@Composable
internal fun everyMinute(): MockDate {
    var now by remember { mutableDoubleStateOf(mockNow()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000 - (System.currentTimeMillis() % 60_000)); now = mockNow() } }
    return now
}
/** .overlay(Capsule().stroke(c.opacity(0.6))) + 가로 8 · 세로 3 여백 */
internal fun Modifier.capsuleLine(c: Color, h: androidx.compose.ui.unit.Dp, v: androidx.compose.ui.unit.Dp, a: Float): Modifier = border(1.dp, c.copy(alpha = c.alpha * a), RoundedCornerShape(50)).padding(horizontal = h, vertical = v)

enum class MockTab {   // ios: Mock/MockViews.swift:9
    observe, select, journal, account;   // ios: Mock/MockViews.swift:10
    val id: String get() = name   // ios: Mock/MockViews.swift:11
    val label: String get() = when (this) {   // ios: Mock/MockViews.swift:14,15
        observe -> "관찰"   // ios: Mock/MockViews.swift:16
        select -> "선정"   // ios: Mock/MockViews.swift:17
        journal -> "일지"   // ios: Mock/MockViews.swift:18
        account -> accountName   // ios: Mock/MockViews.swift:19
    }
    val icon: String get() = when (this) {   // ios: Mock/MockViews.swift:22,23
        observe -> "binoculars"   // ios: Mock/MockViews.swift:24
        select -> "checklist"   // ios: Mock/MockViews.swift:25
        journal -> "book.closed"   // ios: Mock/MockViews.swift:26
        account -> "creditcard"   // ios: Mock/MockViews.swift:27
    }
    companion object {
        /** 계좌 탭 이름 (바꿀 때 여기 한 곳만) */
        const val accountName = "계좌"   // ios: Mock/MockViews.swift:13
    }
}

/** 만원 (만원 아래는 원): "1,240만원" */
fun MockFmt.man(x: Double): String =   // ios: Mock/MockViews.swift:34,32
    if (abs(x) < 1e4) won(x) else (x / 1e4).swRound().toLong().fmtInt() + "만원"   // ios: Mock/MockViews.swift:35
/** 줄 가운데 회색 값: 100만원 넘으면 만원으로 */
fun MockFmt.small(x: Double): String = if (abs(x) >= 1e6) man(x) else won(x)   // ios: Mock/MockViews.swift:38
/** 부호 붙인 원: "+12,300원" */
fun MockFmt.signedWon(x: Double): String = signed(x.swRound()) + "원"   // ios: Mock/MockViews.swift:40
/** "2026-10-24" → "10/24" */
fun MockFmt.md(day: String): String {   // ios: Mock/MockViews.swift:42
    val p = day.split("-")   // ios: Mock/MockViews.swift:43
    val m = p.getOrNull(1)?.toIntOrNull(); val d = p.getOrNull(2)?.toIntOrNull(); if (p.size != 3 || m == null || d == null) return day   // ios: Mock/MockViews.swift:44
    return "$m/$d"   // ios: Mock/MockViews.swift:45
}

object MockNum {   // ios: Mock/MockViews.swift:49
    /** 입력 칸 글 → 수 (쉼표·긴 빼기 허용, 비면 null) */
    fun parse(s: String): Double? {   // ios: Mock/MockViews.swift:51
        val t = s.replace(",", "").replace("−", "-").replace("+", "")   // ios: Mock/MockViews.swift:52
            .trim { it == ' ' || it == '\t' }   // ios: Mock/MockViews.swift:53
        return if (t.isEmpty()) null else t.toDoubleOrNull()   // ios: Mock/MockViews.swift:54
    }
}

fun MockDesk.busyName(b: String): String =   // ios: Mock/MockViews.swift:59,58
    when (b) {   // ios: Mock/MockViews.swift:60
        "observe" -> "훑는"   // ios: Mock/MockViews.swift:61
        "select" -> "고르는"   // ios: Mock/MockViews.swift:62
        "order" -> "주문"   // ios: Mock/MockViews.swift:63
        "cycle" -> "한 바퀴"   // ios: Mock/MockViews.swift:64
        "hand" -> "손 주문"   // ios: Mock/MockViews.swift:65
        "sell" -> "파는"   // ios: Mock/MockViews.swift:66
        else -> b   // ios: Mock/MockViews.swift:67
    }

/** 오늘 시작 평가액: 오늘 전 마지막 점 (없으면 오늘 첫 점, 장부가 오늘 시작했으면 시작 현금)
 *  [실전]은 오늘 처음 읽은 토스 계좌 평가액 (없으면 오늘 첫 점, 그것도 없으면 지금 토스 평가액) */
fun MockDesk.dayBase(b: MockBook): Double {   // ios: Mock/MockViews.swift:73
    val today = MockFmt.day(mockNow())   // ios: Mock/MockViews.swift:74
    if (b == MockBook.real) {   // ios: Mock/MockViews.swift:75
        val base = s.realBase; if (s.realBaseDay == today && base != null) return base   // ios: Mock/MockViews.swift:76
        return marks(b).firstOrNull { MockFmt.day(it.at) == today }?.eq ?: realEquity ?: 0.0   // ios: Mock/MockViews.swift:77
    }
    val l = ledger(b)   // ios: Mock/MockViews.swift:79
    if (MockFmt.day(l.started) == today) return l.startCash   // ios: Mock/MockViews.swift:80
    val ms = marks(b)   // ios: Mock/MockViews.swift:81
    ms.lastOrNull { MockFmt.day(it.at) < today }?.let { return it.eq }   // ios: Mock/MockViews.swift:82
    return ms.firstOrNull { MockFmt.day(it.at) == today }?.eq ?: l.startCash   // ios: Mock/MockViews.swift:83
}
val MockDesk.dayBase: Double get() = dayBase(shown)   // ios: Mock/MockViews.swift:85

/** 하루 손실 한도까지 남은 돈 (보이는 칸의 장부. 0 이하면 넘음) */
val MockDesk.lossRoom: Double get() = equity(shown) - dayBase * (1 - guards.dayLoss)   // ios: Mock/MockViews.swift:88

/** [실전] 칸 요약의 평가액·현금: 토스 계좌 기준 (못 읽었으면 null). [모의]는 장부 */
val MockDesk.shownCash: Double? get() = if (shown == MockBook.real) account?.cash else ledger(MockBook.rule).cash   // ios: Mock/MockViews.swift:91
val MockDesk.shownEquity: Double? get() {   // ios: Mock/MockViews.swift:92
    if (shown != MockBook.real) return equity(MockBook.rule)   // ios: Mock/MockViews.swift:93
    val a = account ?: return null   // ios: Mock/MockViews.swift:94
    return (a.cash ?: 0.0) + a.items.fold(0.0) { acc, it -> acc + it.qty.toDouble() * (s.prices[it.code] ?: it.avg ?: 0.0) }   // ios: Mock/MockViews.swift:95
}
/** [실전] 칸: 실전 장부에 없는 토스 보유 (앱이 사지 않은 몫. 보기만, 팔기 조건·주문을 붙이지 않는다) */
val MockDesk.outside: List<TossOrderAPI.Account.Item> get() {   // ios: Mock/MockViews.swift:98
    val a = account; if (shown != MockBook.real || a == null) return emptyList()   // ios: Mock/MockViews.swift:99
    val l = ledger(MockBook.real)   // ios: Mock/MockViews.swift:100
    return a.items.mapNotNull { x ->   // ios: Mock/MockViews.swift:101
        val y = x.copy()   // ios: Mock/MockViews.swift:102
        y.qty = x.qty - (l.positions[x.code]?.qty ?: 0)   // ios: Mock/MockViews.swift:103
        if (y.qty > 0) y else null   // ios: Mock/MockViews.swift:104
    }
}

/** 보유 종목을 산 값 대비 오른 · 그대로 · 내린으로 나눈 평가액과 수 */
val MockDesk.moods: Pair<List<Double>, List<Int>> get() {   // ios: Mock/MockViews.swift:109
    val v = mutableListOf(0.0, 0.0, 0.0); val n = mutableListOf(0, 0, 0)   // ios: Mock/MockViews.swift:110
    for (p in ledger(shown).positions.values) {   // ios: Mock/MockViews.swift:111
        val now = s.prices[p.code] ?: p.avg   // ios: Mock/MockViews.swift:112
        val r = if (p.avg > 0) now / p.avg - 1 else 0.0   // ios: Mock/MockViews.swift:113
        val k = if (r > 0.0005) 0 else if (r < -0.0005) 2 else 1   // ios: Mock/MockViews.swift:114
        v[k] += p.qty.toDouble() * now   // ios: Mock/MockViews.swift:115
        n[k] += 1   // ios: Mock/MockViews.swift:116
    }
    return v to n   // ios: Mock/MockViews.swift:118
}

// MARK: 요약 박스 (세 탭 위. 홈에서 고른 평가액 박스 모양의 바탕·색, 밝기 모드)

/** 라벨 + 상태 알약 / 큰 숫자 + 옆 숫자 / 도형 한 줄 / 꼬리 두 칸 */
@Composable
fun MockBox(   // ios: Mock/MockViews.swift:125
    label: String,   // ios: Mock/MockViews.swift:126
    big: AnnotatedString,   // ios: Mock/MockViews.swift:127
    bigColor: Color,
    side: String,   // ios: Mock/MockViews.swift:128
    sideColor: Color = boxInk().sub,   // ios: Mock/MockViews.swift:129
    tailL: String,   // ios: Mock/MockViews.swift:130
    tailLColor: Color,
    tailR: String,   // ios: Mock/MockViews.swift:131
    seg: Boolean = false,   // ios: Mock/MockViews.swift:132
    ai: Boolean = false,   // ios: Mock/MockViews.swift:133
    shape: @Composable () -> Unit,   // ios: Mock/MockViews.swift:134
) {
    val ink = boxInk()
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:137,160,161,136
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockViews.swift:138
            mt(label, Modifier.weight(1f).padding(end = 4.dp), style = appFont(13f), color = ink.dim, maxLines = 1)   // ios: Mock/MockViews.swift:139,140
            if (ai && MockAI.on) MockAIPill()   // ios: Mock/MockViews.swift:141
            if (MockDesk.shared.shown == MockBook.real) {   // ios: Mock/MockViews.swift:142
                mt("실전", Modifier.background(ink.up, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), style = appFont(11f, FontWeight.Bold), color = Color.White)   // ios: Mock/MockViews.swift:143
            }
            if (seg) MockSegPill()   // ios: Mock/MockViews.swift:145
            MockStatePill()   // ios: Mock/MockViews.swift:146
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:148
            FitText(big.text, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), minScale = 0.5f, modifier = Modifier.weight(1f, fill = false), maxLines = 1, color = bigColor)   // ios: Mock/MockViews.swift:149,150
            FitText(side, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), minScale = 0.7f, maxLines = 1, color = sideColor)   // ios: Mock/MockViews.swift:151
        }
        shape()   // ios: Mock/MockViews.swift:153
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockViews.swift:154
            FitText(tailL, style = appFont(13f), minScale = 0.7f, modifier = Modifier.weight(1f).padding(end = 4.dp), color = tailLColor)   // ios: Mock/MockViews.swift:155,156
            FitText(tailR, style = appFont(13f), minScale = 0.7f, maxLines = 1, color = ink.dim)   // ios: Mock/MockViews.swift:157
        }
    }
}

/** 몫 띠 한 줄 (전체 중 몫만큼 칠한다) */
@Composable
fun MockShareBar(value: Double, color: Color, minShare: Double = 0.0) {   // ios: Mock/MockViews.swift:166,167,168,169,170
    val ink = boxInk()
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(ink.ink.copy(alpha = 0.12f))) {   // ios: Mock/MockViews.swift:171,172,173,179
        if (value > 0) {   // ios: Mock/MockViews.swift:174
            Box(Modifier.fillMaxWidth(max(minShare, min(1.0, value)).toFloat()).height(10.dp).background(color, RoundedCornerShape(50)))   // ios: Mock/MockViews.swift:175
        }
    }
}

/** 탭마다 요약 박스 */
@Composable
fun MockTabBox(tab: MockTab) {   // ios: Mock/MockViews.swift:184,185
    val lookRaw = BoxLook.raw   // ios: Mock/MockViews.swift:186
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:189,188
    BoxSchemeScope {   // ios: Mock/MockViews.swift:200
        Box(Modifier.clip(RoundedCornerShape(20.dp)).moodBackdrop(BoxLook.entries.firstOrNull { it.name == lookRaw } ?: BoxLook.jump)) {   // ios: Mock/MockViews.swift:190,198,199
            when (tab) {   // ios: Mock/MockViews.swift:191
                MockTab.observe -> MockTabBoxObserve(d)   // ios: Mock/MockViews.swift:192
                MockTab.select -> MockTabBoxSelect(d)   // ios: Mock/MockViews.swift:193
                MockTab.journal -> MockTabBoxJournal(d, lookRaw)   // ios: Mock/MockViews.swift:194
                MockTab.account -> MockTabBoxAccount(d)   // ios: Mock/MockViews.swift:195
            }
        }
    }
}

/** 꼬리 줄용 만원 (끝 '원' 뺌): "1,024만" */
private fun boxMan(x: Double): String {   // ios: Mock/MockViews.swift:204
    val t = MockFmt.man(x)   // ios: Mock/MockViews.swift:205
    return if (t.endsWith("만원")) t.dropLast(1) else t   // ios: Mock/MockViews.swift:206
}

@Composable
private fun MockTabBoxObserve(d: MockDesk) {   // ios: Mock/MockViews.swift:209
    val ink = boxInk()
    val n = d.conds.count { it.on }   // ios: Mock/MockViews.swift:210
    val sc = d.s.scan   // ios: Mock/MockViews.swift:211
    val hits = sc?.hits?.size ?: 0; val total = sc?.total ?: MockDesk.universe.size   // ios: Mock/MockViews.swift:212
    val r = if (total > 0) hits.toDouble() / total.toDouble() else 0.0   // ios: Mock/MockViews.swift:213
    MockBox(label = "조건 ${n}개 · 국내 전체",   // ios: Mock/MockViews.swift:214
                   big = AnnotatedString("${hits}종목"), bigColor = ink.ink,   // ios: Mock/MockViews.swift:215
                   side = "${total.fmtInt()} 중",   // ios: Mock/MockViews.swift:216
                   tailL = "걸림 " + cformat("%.1f%%", r * 100), tailLColor = ink.sub,   // ios: Mock/MockViews.swift:217
                   tailR = sc?.let { MockFmt.time(it.at) + " · 일봉 " + MockFmt.md(it.barDay) } ?: "아직 안 훑음", seg = true) {   // ios: Mock/MockViews.swift:218
        MockShareBar(value = r, color = ink.good, minShare = if (hits > 0) 0.06 else 0.0)   // ios: Mock/MockViews.swift:219
    }
}

@Composable
private fun MockTabBoxSelect(d: MockDesk) {   // ios: Mock/MockViews.swift:223
    val ink = boxInk()
    val l = d.ledger(d.shown)   // ios: Mock/MockViews.swift:224
    // 고른 종목 카드와 같은 셈: 대기 주문 + 고른 종목 (보유는 카드에 안 보이니 뺌)
    val n = l.pending.size + d.picks.size   // ios: Mock/MockViews.swift:226
    val bought = l.positions.values.fold(0.0) { a, p -> a + p.cost }   // ios: Mock/MockViews.swift:227
    val all = bought + l.cash + l.reserved + l.settling   // ios: Mock/MockViews.swift:228
    val r = if (all > 0) bought / all else 0.0   // ios: Mock/MockViews.swift:229
    MockBox(label = "고른 종목",   // ios: Mock/MockViews.swift:230
                   big = AnnotatedString("${n}종목"), bigColor = ink.ink,   // ios: Mock/MockViews.swift:231
                   side = MockFmt.man(bought),   // ios: Mock/MockViews.swift:232
                   tailL = "산 돈 " + cformat("%.0f%%", r * 100), tailLColor = ink.sub,   // ios: Mock/MockViews.swift:233
                   tailR = "현금 " + (d.shownCash?.let { MockFmt.man(it) } ?: "계좌 못 읽음") + (if (l.settling > 0) " · 결제 대기 " + MockFmt.man(l.settling) else ""), seg = true) {   // ios: Mock/MockViews.swift:234
        MockShareBar(value = r, color = Theme.gold)   // ios: Mock/MockViews.swift:235
    }
}

/** 계좌: 평가액 · 손익 (실전 = 토스 비용 뺀 손익, 모의 = 오늘) / 주문가능 · 손실 한도까지 남은 돈 / 손실 한도를 쓴 만큼 띠 */
@Composable
private fun MockTabBoxAccount(d: MockDesk) {   // ios: Mock/MockViews.swift:240
    val ink = boxInk()
    val eq = d.equity(d.shown); val base = d.dayBase   // ios: Mock/MockViews.swift:241
    val real = d.shown == MockBook.real   // ios: Mock/MockViews.swift:242
    val pl = if (real) d.account?.plAfterCost else eq - base   // ios: Mock/MockViews.swift:243
    val c = if ((pl ?: 0.0) > 0) ink.up else if ((pl ?: 0.0) < 0) ink.down else ink.dim   // ios: Mock/MockViews.swift:244
    val room = d.lossRoom; val cap = base * d.guards.dayLoss   // ios: Mock/MockViews.swift:245
    MockBox(label = (if (real) "토스 계좌" else d.shown.tab) + " · 평가액",   // ios: Mock/MockViews.swift:246
                   big = AnnotatedString(d.shownEquity?.let { MockFmt.man(it) } ?: "계좌 못 읽음"), bigColor = ink.ink,   // ios: Mock/MockViews.swift:247
                   side = pl?.let { (if (it < 0) "−" else if (it > 0) "+" else "") + boxMan(abs(it)) + (if (real) " 비용 뒤" else " 오늘") } ?: "—", sideColor = c,   // ios: Mock/MockViews.swift:248
                   tailL = "주문가능 " + (d.shownCash?.let { boxMan(it) } ?: "—"), tailLColor = ink.sub,   // ios: Mock/MockViews.swift:249
                   tailR = if (room > 0) "손실 한도까지 " + boxMan(room) else "손실 한도 넘음", ai = true) {   // ios: Mock/MockViews.swift:250
        MockShareBar(value = if (cap > 0) 1 - room / cap else 0.0, color = if (room > 0) ink.gold else ink.up)   // ios: Mock/MockViews.swift:251
    }
}

@Composable
private fun MockTabBoxJournal(d: MockDesk, lookRaw: String) {   // ios: Mock/MockViews.swift:255
    val ink = boxInk()
    val eq = d.equity(d.shown); val base = d.dayBase   // ios: Mock/MockViews.swift:256
    val mv = eq - base; val pct = if (base > 0) mv / base else 0.0   // ios: Mock/MockViews.swift:257
    val c = if (mv > 0) ink.up else if (mv < 0) ink.down else ink.dim   // ios: Mock/MockViews.swift:258
    val tri = if (mv > 0) "▲ " else if (mv < 0) "▼ " else ""   // ios: Mock/MockViews.swift:259
    val m = d.moods   // ios: Mock/MockViews.swift:260
    val sum = m.first.fold(0.0) { a, b -> a + b }   // ios: Mock/MockViews.swift:261
    val shares = if (sum > 0) m.first.map { it / sum } else listOf(0.0, 0.0, 0.0)   // ios: Mock/MockViews.swift:262
    val look = BoxLook.entries.firstOrNull { it.name == lookRaw } ?: BoxLook.jump   // ios: Mock/MockViews.swift:263
    MockBox(label = "오늘 일지 · 수수료·세금 뒤",   // ios: Mock/MockViews.swift:264
                   big = buildAnnotatedString { withStyle(SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)) { append(tri) }; append(MockFmt.man(abs(mv))) }, bigColor = c,   // ios: Mock/MockViews.swift:265
                   side = MockFmt.pct(pct), sideColor = c,   // ios: Mock/MockViews.swift:266
                   tailL = "오른 ${m.second[0]} · 그대로 ${m.second[1]} · 내린 ${m.second[2]}", tailLColor = ink.sub,   // ios: Mock/MockViews.swift:267
                   tailR = d.shownEquity?.let { "평가액 " + boxMan(it) + " · 현금 " + boxMan(d.shownCash ?: 0.0) } ?: "계좌 못 읽음") {   // ios: Mock/MockViews.swift:268
        MoodBand(look = look, shares = shares, up = mv >= 0)   // ios: Mock/MockViews.swift:269
    }
}

/** 상태 알약: 도는 중 · 정상 n분 전 · 장 밖(시험) · 장 확인 못 함. 1분 주기는 보이지 않는다 (깜빡이지 않게)
 *  하루 구간 알약: 지켜보기·정리는 금색, 장 시작 전·마감 후·휴장은 '정지'. 고르고 주문 구간엔 숨긴다 */
@Composable
fun MockSegPill() {   // ios: Mock/MockViews.swift:276,277
    val ink = boxInk()
    val now = everyMinute()   // ios: Mock/MockViews.swift:278
    val g = MockDesk.shared.seg(now)   // ios: Mock/MockViews.swift:279
    if (!g.buys) {   // ios: Mock/MockViews.swift:280
        val c = if (g == MockClock.Seg.watch || g == MockClock.Seg.tidy) ink.gold else ink.dim   // ios: Mock/MockViews.swift:281
        mt(if (g == MockClock.Seg.watch || g == MockClock.Seg.tidy) g.label else g.label + " · 정지", Modifier.capsuleLine(c, 8.dp, 3.dp, 0.6f), style = appFont(11f, FontWeight.SemiBold), color = c)   // ios: Mock/MockViews.swift:282,283,284
    }
}

/** AI 이번 달 남은 돈: 앱 한도 − 서버가 적은 쓴 돈 (Anthropic 계정 잔액은 API 로 못 읽는다, 2026-10-29). 아직 못 받았으면 숨긴다 */
@Composable
fun MockAIPill() {   // ios: Mock/MockViews.swift:291,292
    val ink = boxInk()
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:293
    val w = d.s.aiWallet ?: return   // ios: Mock/MockViews.swift:294
    mt("AI $" + cformat("%.2f", max(0.0, d.s.ai.month - w.month)), Modifier.capsuleLine(ink.gold, 8.dp, 3.dp, 0.6f), style = appFont(11f, FontWeight.SemiBold).tnum(),   // ios: Mock/MockViews.swift:295,297,298
        color = ink.gold, maxLines = 1)   // ios: Mock/MockViews.swift:296
}

@Composable
fun MockStatePill() {   // ios: Mock/MockViews.swift:303,304
    val now = everyMinute()   // ios: Mock/MockViews.swift:305
    val (t, c) = mockState(now)   // ios: Mock/MockViews.swift:306
    Row(Modifier.capsuleLine(c, 8.dp, 3.dp, 0.6f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Mock/MockViews.swift:307,311,312,313
        if (MockDesk.shared.busy != null) Spinner(10.dp)   // ios: Mock/MockViews.swift:308
        mt(t, style = appFont(11f, FontWeight.SemiBold), color = c)   // ios: Mock/MockViews.swift:309
    }
}

@Composable
private fun mockState(now: MockDate): Pair<String, Color> {   // ios: Mock/MockViews.swift:317
    val ink = boxInk()
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:318
    d.busy?.let { return (d.busyName(it) + " 중") to ink.gold }   // ios: Mock/MockViews.swift:319
    if (d.halted) return "모두 멈춤" to ink.up   // ios: Mock/MockViews.swift:320
    return when (d.phase) {   // ios: Mock/MockViews.swift:321
        MockDesk.Phase.open -> {   // ios: Mock/MockViews.swift:322
            val at = d.s.pricesAt ?: d.s.runs["observe"]?.at ?: return "정상" to ink.good   // ios: Mock/MockViews.swift:323
            val n = max(0, ((now - at) / 60).toInt())   // ios: Mock/MockViews.swift:324
            ("정상 · " + (if (n < 1) "방금" else "${n}분 전")) to ink.good   // ios: Mock/MockViews.swift:325
        }
        MockDesk.Phase.closed -> "장 밖 · 시험" to ink.dim   // ios: Mock/MockViews.swift:326
        MockDesk.Phase.unknown -> "장 확인 못 함" to ink.up   // ios: Mock/MockViews.swift:327
    }
}


// MARK: 공통 부품

/** teal 채움 작은 알약 (36 높이). 단추 일이 도는 동안만 막힌다 (1분 주기로는 막히지 않는다) */
@Composable
fun MockGoPill(title: String, enabled: Boolean = true, action: suspend () -> Unit) {   // ios: Mock/MockViews.swift:335,336,337,338,339
    val scope = rememberCoroutineScope()
    val blocked = MockDesk.shared.busy != null || !enabled   // ios: Mock/MockViews.swift:340
    Box(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (blocked) Theme.muted else Theme.teal)   // ios: Mock/MockViews.swift:343,344,345
        .clickable(enabled = !blocked) { scope.launch { action() } }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:341,347,348,343
        mt(title, style = appFont(14f, FontWeight.Bold), color = Color.White, maxLines = 1)   // ios: Mock/MockViews.swift:342
    }
}

/** 조건 알약 (보라). 누르면 켜고 끈다, 길게 누르면 고치기·지우기 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MockCondPill(text: String, on: Boolean = true, tap: (() -> Unit)? = null, edit: (() -> Unit)? = null, delete: (() -> Unit)? = null) {   // ios: Mock/MockViews.swift:353,354,355,356,357,358,359
    var menu by remember { mutableStateOf(false) }
    Box {
        mt(text, Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(50))   // ios: Mock/MockViews.swift:362,365
            .background(if (on) Theme.rule.copy(alpha = 0.12f) else Theme.card)   // ios: Mock/MockViews.swift:363
            .border(1.dp, if (on) Theme.rule.copy(alpha = 0.7f) else Theme.border, RoundedCornerShape(50))   // ios: Mock/MockViews.swift:364
            .combinedClickable(onClick = { tap?.invoke() }, onLongClick = { if (edit != null || delete != null) menu = true })   // ios: Mock/MockViews.swift:366,367,369,372,375
            .padding(horizontal = 12.dp, vertical = 7.dp),   // ios: Mock/MockViews.swift:362
            style = appFont(13f, FontWeight.SemiBold), color = if (on) Theme.rule else Theme.muted, maxLines = 1)   // ios: Mock/MockViews.swift:360,361
        com.naeilo.app.ui.ContextMenu(show = menu, items = listOfNotNull(   // ios: Mock/MockViews.swift:367
            edit?.let { e -> com.naeilo.app.ui.MenuItem("고치기", icon = "pencil") { e() } },   // ios: Mock/MockViews.swift:368,369
            delete?.let { x -> com.naeilo.app.ui.MenuItem("지우기", icon = "trash", destructive = true) { x() } },   // ios: Mock/MockViews.swift:371,372
        ), onDismiss = { menu = false })
    }
}

/** '+ 조건' (보라 점선) */
@Composable
fun MockAddPill(title: String = "+ 조건", action: () -> Unit) {   // ios: Mock/MockViews.swift:380,381,382,383
    val c = Theme.rule
    Box(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(50)).clickable(onClick = action)   // ios: Mock/MockViews.swift:384,386,388,390
        .dashedCapsule(c.copy(alpha = 0.7f), 0.7f, 1.2.dp, 4.dp, 3.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:387,386
        mt(title, style = appFont(13f, FontWeight.SemiBold), color = Theme.rule)   // ios: Mock/MockViews.swift:385
    }
}
/** Capsule().stroke(dash: [4, 3]) */
internal fun Modifier.dashedCapsule(c: Color, a: Float, lw: androidx.compose.ui.unit.Dp, on: androidx.compose.ui.unit.Dp, off: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    val r = size.height / 2
    drawRoundRect(c.copy(alpha = a), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = androidx.compose.ui.graphics.drawscope.Stroke(lw.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))))
}

/** 펼침 안 종류 고르기 알약 */
@Composable
fun MockKindChip(name: String, on: Boolean, action: () -> Unit) {   // ios: Mock/MockViews.swift:395,396,397,398,399
    Box(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(50)).background(if (on) Theme.rule else Theme.card)   // ios: Mock/MockViews.swift:403,404,406
        .border(1.dp, if (on) Theme.rule else Theme.border, RoundedCornerShape(50)).clickable(onClick = action)   // ios: Mock/MockViews.swift:400,405,408
        .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:403
        mt(name, style = appFont(13f, FontWeight.SemiBold), color = if (on) Color.White else Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:401,402
    }
}

/** 수 입력 칸 */
@Composable
fun MockNumField(text: String, onChange: (String) -> Unit, width: androidx.compose.ui.unit.Dp = 72.dp) {   // ios: Mock/MockViews.swift:413,414,415,416
    val ink = Theme.ink; val muted = Theme.muted
    val style = appFont(14f).tnum().copy(color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.End)   // ios: Mock/MockViews.swift:418,419
    androidx.compose.foundation.text.BasicTextField(text, onChange,   // ios: Mock/MockViews.swift:417
        Modifier.width(width + 16.dp).height(34.dp).background(Theme.card, RoundedCornerShape(8.dp)).border(1.dp, Theme.border, RoundedCornerShape(8.dp)),   // ios: Mock/MockViews.swift:420,421,422,423
        textStyle = style, singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(Theme.teal),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),   // ios: Mock/MockViews.swift:418
        decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) { if (text.isEmpty()) mt("비움", style = style, color = muted); inner() } })   // ios: Mock/MockViews.swift:417
}

/** 펼침 아래 '닫기 · 넣기' */
@Composable
fun MockFormButtons(commitTitle: String, canCommit: Boolean, close: () -> Unit, commit: () -> Unit) {   // ios: Mock/MockViews.swift:428,429,430,431,432,433
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {   // ios: Mock/MockViews.swift:434,435
        Box(Modifier.height(36.dp).clip(RoundedCornerShape(50)).border(1.dp, Theme.border, RoundedCornerShape(50)).clickable(onClick = close).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:436,438,439,441
            mt("닫기", style = appFont(14f, FontWeight.SemiBold), color = Theme.sub)   // ios: Mock/MockViews.swift:437
        }
        Box(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (canCommit) Theme.teal else Theme.muted).clickable(enabled = canCommit, onClick = commit).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:442,444,445,447,448
            mt(commitTitle, style = appFont(14f, FontWeight.Bold), color = Color.White)   // ios: Mock/MockViews.swift:443
        }
    }
}

/** 관찰·예외 조건 하나를 고치는 중인 값 */
data class MockPieceDraft(   // ios: Mock/MockViews.swift:454
    val edit: String? = null,   // ios: Mock/MockViews.swift:455
    val kind: MockPiece.Kind = MockPiece.Kind.change,   // ios: Mock/MockViews.swift:456
    val lo: String = "",   // ios: Mock/MockViews.swift:457
    val hi: String = "",   // ios: Mock/MockViews.swift:458
    val days: String = "20",   // ios: Mock/MockViews.swift:459
    val above: Boolean = true,   // ios: Mock/MockViews.swift:460
) {
    constructor(p: MockPiece) : this(   // ios: Mock/MockViews.swift:463,462
        edit = p.id, kind = p.kind,   // ios: Mock/MockViews.swift:464
        lo = p.lo?.let { MockFmt.num(it) } ?: "", hi = p.hi?.let { MockFmt.num(it) } ?: "",   // ios: Mock/MockViews.swift:465
        days = "${p.days}", above = p.above)   // ios: Mock/MockViews.swift:466

    /** 넣을 조건. 값이 하나도 없으면 null (이동평균은 값이 없다) */
    val piece: MockPiece? get() {   // ios: Mock/MockViews.swift:470
        val x = MockPiece(id = edit ?: newId(), kind = kind, days = min(120, max(2, days.toIntOrNull() ?: 20)), above = above)   // ios: Mock/MockViews.swift:471
        if (kind != MockPiece.Kind.ma) {   // ios: Mock/MockViews.swift:472
            x.lo = MockNum.parse(lo); x.hi = MockNum.parse(hi)   // ios: Mock/MockViews.swift:473
            if (x.lo == null && x.hi == null) return null   // ios: Mock/MockViews.swift:474
        }
        val a = x.lo; val b = x.hi; if (a != null && b != null && a > b) { x.lo = b; x.hi = a }   // ios: Mock/MockViews.swift:476
        return x   // ios: Mock/MockViews.swift:477
    }

    companion object {
        fun unit(k: MockPiece.Kind): String = when (k) {   // ios: Mock/MockViews.swift:480,481
            MockPiece.Kind.price -> "원"   // ios: Mock/MockViews.swift:482
            MockPiece.Kind.change, MockPiece.Kind.rise -> "%"   // ios: Mock/MockViews.swift:483
            MockPiece.Kind.volume -> "배"   // ios: Mock/MockViews.swift:484
            MockPiece.Kind.ma -> ""   // ios: Mock/MockViews.swift:485
            MockPiece.Kind.amount -> "억"   // ios: Mock/MockViews.swift:486
        }
    }
}

/** 조건 펼침: 종류 알약 → 값 → 닫기·넣기 (별도 화면 없이 카드 아래에) */
@Composable
fun MockPieceForm(draft: MockPieceDraft, setDraft: (MockPieceDraft) -> Unit,   // ios: Mock/MockViews.swift:492,493
                  kinds: List<MockPiece.Kind> = listOf(MockPiece.Kind.price, MockPiece.Kind.change, MockPiece.Kind.volume, MockPiece.Kind.ma, MockPiece.Kind.rise),   // ios: Mock/MockViews.swift:494
                  close: () -> Unit, commit: (MockPiece) -> Unit) {   // ios: Mock/MockViews.swift:495,496
    val ks = if (kinds.contains(draft.kind)) kinds else kinds + draft.kind   // ios: Mock/MockViews.swift:499,498
    Column(Modifier.fillMaxWidth().background(Theme.rule.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:500,511,512
        com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockViews.swift:501
            ks.forEach { k ->   // ios: Mock/MockViews.swift:502
                MockKindChip(name = k.label, on = draft.kind == k) { setDraft(draft.copy(kind = k)) }   // ios: Mock/MockViews.swift:503
            }
        }
        MockPieceFields(draft, setDraft)   // ios: Mock/MockViews.swift:506
        MockFormButtons(commitTitle = if (draft.edit == null) "넣기" else "고치기", canCommit = draft.piece != null, close = close) {   // ios: Mock/MockViews.swift:507
            draft.piece?.let { commit(it) }   // ios: Mock/MockViews.swift:508
        }
    }
}

@Composable
private fun MockPieceFields(draft: MockPieceDraft, setDraft: (MockPieceDraft) -> Unit) {   // ios: Mock/MockViews.swift:515
    val u = MockPieceDraft.unit(draft.kind)   // ios: Mock/MockViews.swift:516
    if (draft.kind == MockPiece.Kind.ma) {   // ios: Mock/MockViews.swift:517
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockViews.swift:518
            MockNumField(draft.days, { setDraft(draft.copy(days = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:519
            mt("일선", style = appFont(14f))   // ios: Mock/MockViews.swift:520
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:521
            mt("가격이", style = appFont(13f), color = Theme.sub)   // ios: Mock/MockViews.swift:522
            com.naeilo.app.ui.hold.HoldSeg(items = listOf(true to "위", false to "아래"), selection = draft.above, set = { setDraft(draft.copy(above = it)) }, label = "가격이")   // ios: Mock/MockViews.swift:523
        }
    } else {   // ios: Mock/MockViews.swift:525
        if (draft.kind == MockPiece.Kind.volume || draft.kind == MockPiece.Kind.rise) {   // ios: Mock/MockViews.swift:526
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockViews.swift:527
                MockNumField(draft.days, { setDraft(draft.copy(days = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:528
                mt(if (draft.kind == MockPiece.Kind.volume) "일 평균과 견줘" else "일 동안", style = appFont(14f), color = Theme.sub)   // ios: Mock/MockViews.swift:529
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockViews.swift:532
            MockNumField(draft.lo, { setDraft(draft.copy(lo = it)) })   // ios: Mock/MockViews.swift:533
            mt(u + " 이상", style = appFont(14f))   // ios: Mock/MockViews.swift:534
            Spacer(Modifier.weight(1f).widthIn(min = 4.dp))   // ios: Mock/MockViews.swift:535
            MockNumField(draft.hi, { setDraft(draft.copy(hi = it)) })   // ios: Mock/MockViews.swift:536
            mt(u + " 이하", style = appFont(14f))   // ios: Mock/MockViews.swift:537
        }
    }
}

/** 고르기 조건 하나를 고치는 중인 값 */
data class MockRuleDraft(   // ios: Mock/MockViews.swift:544
    val edit: String? = null,   // ios: Mock/MockViews.swift:545
    val kind: MockRuleCond.Kind = MockRuleCond.Kind.per,   // ios: Mock/MockViews.swift:546
    val sort: MockPiece.Kind = MockPiece.Kind.volume,   // ios: Mock/MockViews.swift:547
    val capKind: MockPiece.Kind = MockPiece.Kind.volume,   // ios: Mock/MockViews.swift:548
    val n: String = "5",   // ios: Mock/MockViews.swift:549
    val pct: String = "20",   // ios: Mock/MockViews.swift:550
    val lim: String = "20",   // ios: Mock/MockViews.swift:551
) {
    constructor(c: MockRuleCond) : this(   // ios: Mock/MockViews.swift:554,553
        edit = c.id, kind = c.kind, sort = c.sort, capKind = c.capKind,   // ios: Mock/MockViews.swift:555
        n = "${c.n}", pct = MockFmt.num(if (c.kind == MockRuleCond.Kind.slip) c.pct * 100 else (c.pct * 100).swRound()), lim = MockFmt.num(c.lim))   // ios: Mock/MockViews.swift:556

    val cond: MockRuleCond? get() {   // ios: Mock/MockViews.swift:559
        val c = MockRuleCond(id = edit ?: newId(), kind = kind)   // ios: Mock/MockViews.swift:560
        when (kind) {   // ios: Mock/MockViews.swift:561
            MockRuleCond.Kind.top -> {   // ios: Mock/MockViews.swift:562
                val x = n.toIntOrNull(); if (x == null || x < 1 || x > 30) return null   // ios: Mock/MockViews.swift:563
                c.n = x; c.sort = sort   // ios: Mock/MockViews.swift:564
            }
            MockRuleCond.Kind.per, MockRuleCond.Kind.cash -> {   // ios: Mock/MockViews.swift:565
                val x = MockNum.parse(pct); if (x == null || x < (if (kind == MockRuleCond.Kind.per) 1 else 0) || x > 100) return null   // ios: Mock/MockViews.swift:566
                c.pct = x / 100   // ios: Mock/MockViews.swift:567
            }
            MockRuleCond.Kind.cap -> {   // ios: Mock/MockViews.swift:568
                val x = MockNum.parse(lim); if (x == null || x <= 0) return null   // ios: Mock/MockViews.swift:569
                c.capKind = capKind; c.lim = x   // ios: Mock/MockViews.swift:570
            }
            MockRuleCond.Kind.slip -> {   // ios: Mock/MockViews.swift:571
                val x = MockNum.parse(pct); if (x == null || x <= 0 || x > 10) return null   // ios: Mock/MockViews.swift:572
                c.pct = x / 100   // ios: Mock/MockViews.swift:573
            }
            MockRuleCond.Kind.split -> {   // ios: Mock/MockViews.swift:574
                val x = MockNum.parse(pct); if (x == null || x < 10 || x >= 100) return null   // ios: Mock/MockViews.swift:575
                c.pct = x / 100   // ios: Mock/MockViews.swift:576
            }
        }
        return c   // ios: Mock/MockViews.swift:578
    }
}

/** 고르기 조건 펼침 */
@Composable
fun MockRuleForm(draft: MockRuleDraft, setDraft: (MockRuleDraft) -> Unit, close: () -> Unit, commit: (MockRuleCond) -> Unit) {   // ios: Mock/MockViews.swift:583,584,585,586
    Column(Modifier.fillMaxWidth().background(Theme.rule.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:589,600,601,588
        com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockViews.swift:590
            (MockRuleCond.Kind.choices + (if (draft.kind == MockRuleCond.Kind.cap) listOf(MockRuleCond.Kind.cap) else emptyList())).forEach { k ->   // ios: Mock/MockViews.swift:591
                MockKindChip(name = k.label, on = draft.kind == k) { setDraft(draft.copy(kind = k)) }   // ios: Mock/MockViews.swift:592
            }
        }
        MockRuleFields(draft, setDraft)   // ios: Mock/MockViews.swift:595
        MockFormButtons(commitTitle = if (draft.edit == null) "넣기" else "고치기", canCommit = draft.cond != null, close = close) {   // ios: Mock/MockViews.swift:596
            draft.cond?.let { commit(it) }   // ios: Mock/MockViews.swift:597
        }
    }
}

@Composable
private fun MockRuleFields(draft: MockRuleDraft, setDraft: (MockRuleDraft) -> Unit) {   // ios: Mock/MockViews.swift:604
    @Composable fun line(sp: androidx.compose.ui.unit.Dp, content: @Composable RowScope.() -> Unit) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp), content = content)
    when (draft.kind) {   // ios: Mock/MockViews.swift:605
        MockRuleCond.Kind.top -> {   // ios: Mock/MockViews.swift:606
            com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockViews.swift:607
                listOf(MockPiece.Kind.volume, MockPiece.Kind.change, MockPiece.Kind.amount, MockPiece.Kind.rise).forEach { k ->   // ios: Mock/MockViews.swift:608
                    MockKindChip(name = k.label + " 순", on = draft.sort == k) { setDraft(draft.copy(sort = k)) }   // ios: Mock/MockViews.swift:609
                }
            }
            line(6.dp) {   // ios: Mock/MockViews.swift:612
                mt("상위", style = appFont(14f))   // ios: Mock/MockViews.swift:613
                MockNumField(draft.n, { setDraft(draft.copy(n = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:614
                mt("종목", style = appFont(14f))   // ios: Mock/MockViews.swift:615
            }
        }
        MockRuleCond.Kind.per -> line(6.dp) {   // ios: Mock/MockViews.swift:617,618
            mt("종목당 ≤", style = appFont(14f))   // ios: Mock/MockViews.swift:619
            MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:620
            mt("% (평가액 대비)", style = appFont(14f))   // ios: Mock/MockViews.swift:621
        }
        MockRuleCond.Kind.cash -> line(6.dp) {   // ios: Mock/MockViews.swift:623,624
            mt("현금", style = appFont(14f))   // ios: Mock/MockViews.swift:625
            MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:626
            mt("% 남김", style = appFont(14f))   // ios: Mock/MockViews.swift:627
        }
        MockRuleCond.Kind.cap -> {   // ios: Mock/MockViews.swift:629
            com.naeilo.app.ui.hold.HoldSeg(items = listOf(MockPiece.Kind.volume to "거래량", MockPiece.Kind.rise to "최근 5일"), selection = draft.capKind, set = { setDraft(draft.copy(capKind = it)) }, label = "폭등 거르기")   // ios: Mock/MockViews.swift:630
            line(6.dp) {   // ios: Mock/MockViews.swift:631
                mt("≤", style = appFont(14f))   // ios: Mock/MockViews.swift:632
                MockNumField(draft.lim, { setDraft(draft.copy(lim = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:633
                mt(if (draft.capKind == MockPiece.Kind.rise) "% 넘으면 거름" else "배 넘으면 거름", style = appFont(14f))   // ios: Mock/MockViews.swift:634
            }
        }
        MockRuleCond.Kind.slip -> line(6.dp) {   // ios: Mock/MockViews.swift:636,637
            mt("평균 단가가 1호가에서", style = appFont(14f))   // ios: Mock/MockViews.swift:638
            MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:639
            mt("% 넘게 벗어나면 나눔", style = appFont(14f))   // ios: Mock/MockViews.swift:640
        }
        MockRuleCond.Kind.split -> line(6.dp) {   // ios: Mock/MockViews.swift:642,643
            mt("종목 몫의", style = appFont(14f))   // ios: Mock/MockViews.swift:644
            MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:645
            mt("%씩 · 더 담기는 이익 중에만", style = appFont(14f))   // ios: Mock/MockViews.swift:646
        }
    }
}

/** 팔기 조건 하나를 고치는 중인 값 */
data class MockSellDraft(   // ios: Mock/MockViews.swift:653
    val edit: String? = null,   // ios: Mock/MockViews.swift:654
    val kind: MockSellCond.Kind = MockSellCond.Kind.loss,   // ios: Mock/MockViews.swift:655
    val pct: String = "5",   // ios: Mock/MockViews.swift:656
    val n: String = "5",   // ios: Mock/MockViews.swift:657
) {
    constructor(c: MockSellCond) : this(   // ios: Mock/MockViews.swift:660,659
        edit = c.id, kind = c.kind,   // ios: Mock/MockViews.swift:661
        pct = when (c.kind) {   // ios: Mock/MockViews.swift:662
            MockSellCond.Kind.vol -> MockFmt.num(c.pct)   // ios: Mock/MockViews.swift:663
            MockSellCond.Kind.idle, MockSellCond.Kind.trail -> MockFmt.num((c.pct * 1000).swRound() / 10)   // ios: Mock/MockViews.swift:664
            else -> MockFmt.num((c.pct * 100).swRound())   // ios: Mock/MockViews.swift:665
        },
        n = "${c.n}")   // ios: Mock/MockViews.swift:667

    /** 종류를 바꾸면 그 종류의 기본값으로 (손절 5 · 익절 10 · 움직임 60분 ±1 · 되밀림 2 · 흔들림 ×1) */
    fun fill(k: MockSellCond.Kind): MockSellDraft {   // ios: Mock/MockViews.swift:671
        if (edit != null) return this   // ios: Mock/MockViews.swift:672
        return when (k) {   // ios: Mock/MockViews.swift:673
            MockSellCond.Kind.loss -> copy(pct = "5")   // ios: Mock/MockViews.swift:674
            MockSellCond.Kind.gain -> copy(pct = "10")   // ios: Mock/MockViews.swift:675
            MockSellCond.Kind.idle -> copy(pct = "1", n = "60")   // ios: Mock/MockViews.swift:676
            MockSellCond.Kind.trail -> copy(pct = "2")   // ios: Mock/MockViews.swift:677
            MockSellCond.Kind.vol -> copy(pct = "1")   // ios: Mock/MockViews.swift:678
            else -> this   // ios: Mock/MockViews.swift:679
        }
    }

    val cond: MockSellCond? get() {   // ios: Mock/MockViews.swift:683
        val c = MockSellCond(id = edit ?: newId(), kind = kind)   // ios: Mock/MockViews.swift:684
        when (kind) {   // ios: Mock/MockViews.swift:685
            MockSellCond.Kind.loss, MockSellCond.Kind.gain -> {   // ios: Mock/MockViews.swift:686
                val x = MockNum.parse(pct); if (x == null || x < 1 || x > 90) return null   // ios: Mock/MockViews.swift:687
                c.pct = x / 100   // ios: Mock/MockViews.swift:688
            }
            MockSellCond.Kind.days -> {   // ios: Mock/MockViews.swift:689
                val x = n.toIntOrNull(); if (x == null || x < 1 || x > 60) return null   // ios: Mock/MockViews.swift:690
                c.n = x   // ios: Mock/MockViews.swift:691
            }
            MockSellCond.Kind.idle -> {   // ios: Mock/MockViews.swift:692
                val m = n.toIntOrNull(); val x = MockNum.parse(pct); if (m == null || m < 5 || m > 360 || x == null || x <= 0 || x > 10) return null   // ios: Mock/MockViews.swift:693
                c.n = m; c.pct = x / 100   // ios: Mock/MockViews.swift:694
            }
            MockSellCond.Kind.trail -> {   // ios: Mock/MockViews.swift:695
                val x = MockNum.parse(pct); if (x == null || x < 0.5 || x > 20) return null   // ios: Mock/MockViews.swift:696
                c.pct = x / 100   // ios: Mock/MockViews.swift:697
            }
            MockSellCond.Kind.vol -> {   // ios: Mock/MockViews.swift:698
                val x = MockNum.parse(pct); if (x == null || x < 0.3 || x > 5) return null   // ios: Mock/MockViews.swift:699
                c.pct = x   // ios: Mock/MockViews.swift:700
            }
            MockSellCond.Kind.gone, MockSellCond.Kind.close -> {}   // ios: Mock/MockViews.swift:701
        }
        return c   // ios: Mock/MockViews.swift:703
    }
}

/** 팔기 조건 펼침 */
@Composable
fun MockSellForm(draft: MockSellDraft, setDraft: (MockSellDraft) -> Unit, close: () -> Unit, commit: (MockSellCond) -> Unit) {   // ios: Mock/MockViews.swift:708,709,710,711
    Column(Modifier.fillMaxWidth().background(Theme.rule.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:714,758,759,713
        com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockViews.swift:715
            (MockSellCond.Kind.choices + (if (draft.kind == MockSellCond.Kind.days) listOf(MockSellCond.Kind.days) else emptyList())).forEach { k ->   // ios: Mock/MockViews.swift:716
                MockKindChip(name = k.label, on = draft.kind == k) { setDraft(draft.copy(kind = k).fill(k)) }   // ios: Mock/MockViews.swift:717
            }
        }
        com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockViews.swift:720
            val f = appFont(14f)
            when (draft.kind) {   // ios: Mock/MockViews.swift:721
                MockSellCond.Kind.loss -> {   // ios: Mock/MockViews.swift:722
                    mt("산 값보다", style = appFont(14f))   // ios: Mock/MockViews.swift:723
                    MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:724
                    mt("% 내리면", style = appFont(14f))   // ios: Mock/MockViews.swift:725
                }
                MockSellCond.Kind.gain -> {   // ios: Mock/MockViews.swift:726
                    mt("산 값보다", style = appFont(14f))   // ios: Mock/MockViews.swift:727
                    MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:728
                    mt("% 오르면", style = appFont(14f))   // ios: Mock/MockViews.swift:729
                }
                MockSellCond.Kind.days -> {   // ios: Mock/MockViews.swift:730
                    mt("산 뒤 거래일", style = appFont(14f))   // ios: Mock/MockViews.swift:731
                    MockNumField(draft.n, { setDraft(draft.copy(n = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:732
                    mt("일 넘으면", style = appFont(14f))   // ios: Mock/MockViews.swift:733
                }
                MockSellCond.Kind.idle -> {   // ios: Mock/MockViews.swift:734
                    mt("산 뒤", style = appFont(14f))   // ios: Mock/MockViews.swift:735
                    MockNumField(draft.n, { setDraft(draft.copy(n = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:736
                    mt("분 지나도 ±", style = appFont(14f))   // ios: Mock/MockViews.swift:737
                    MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:738
                    mt("% 안이면", style = appFont(14f))   // ios: Mock/MockViews.swift:739
                }
                MockSellCond.Kind.trail -> {   // ios: Mock/MockViews.swift:740
                    mt("반 판 뒤 고점보다", style = appFont(14f))   // ios: Mock/MockViews.swift:741
                    MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 56.dp)   // ios: Mock/MockViews.swift:742
                    mt("% 내리면", style = appFont(14f))   // ios: Mock/MockViews.swift:743
                }
                MockSellCond.Kind.vol -> {   // ios: Mock/MockViews.swift:744
                    mt("산 값 − 지난 20일 하루 폭 ×", style = appFont(14f))   // ios: Mock/MockViews.swift:745
                    MockNumField(draft.pct, { setDraft(draft.copy(pct = it)) }, width = 48.dp)   // ios: Mock/MockViews.swift:746
                    mt("아래로", style = appFont(14f))   // ios: Mock/MockViews.swift:747
                }
                MockSellCond.Kind.gone -> mt("켠 관찰 조건이 약한 쪽으로 풀리면", style = appFont(14f))   // ios: Mock/MockViews.swift:748,749
                MockSellCond.Kind.close -> mt("${MockClock.hm(MockClock.tidyFrom)} 정리 구간에 남은 몫 모두", style = appFont(14f))   // ios: Mock/MockViews.swift:750,751
            }
        }
        MockFormButtons(commitTitle = if (draft.edit == null) "넣기" else "고치기", canCommit = draft.cond != null, close = close) {   // ios: Mock/MockViews.swift:754
            draft.cond?.let { commit(it) }   // ios: Mock/MockViews.swift:755
        }
    }
}

/** 상태 한 줄 (성공은 회색, 실패는 빨강) */
@Composable
fun MockRunLine(run: MockRun?, empty: String = "아직 돌리지 않았어요") {   // ios: Mock/MockViews.swift:764,765,766,767
    mt(run?.text ?: empty, style = appFont(12f), color = if (run?.ok == false) Theme.up else Theme.muted)   // ios: Mock/MockViews.swift:768,769
}

/** 누가 냈나 · 시험 표시 */
@Composable
fun MockTag(text: String, color: Color = Theme.sub) {   // ios: Mock/MockViews.swift:774,775,776,777
    mt(text, Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),   // ios: Mock/MockViews.swift:779,780
        style = appFont(11f, FontWeight.SemiBold), color = color)   // ios: Mock/MockViews.swift:778
}
object MockTagWho {
    @Composable
    fun who(w: MockWho) = MockTag(text = w.label, color = if (w == MockWho.rule) Theme.rule else if (w == MockWho.hand) Theme.orange else Theme.blue)   // ios: Mock/MockViews.swift:782,783
}

/** 종목 탭 그룹 카드 모양: 머리줄 + 한 줄 행들 */
@Composable
fun MockListCard(modifier: Modifier = Modifier, head: @Composable RowScope.() -> Unit, rows: @Composable ColumnScope.() -> Unit) {   // ios: Mock/MockViews.swift:788,789,790,791
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Theme.card).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:792,797,798,799
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = head)   // ios: Mock/MockViews.swift:793,794
        rows()   // ios: Mock/MockViews.swift:795
    }
}


/** 머리줄 오른쪽 등락 (▲ 9pt + %) */
@Composable
fun MockMove(r: Double) {   // ios: Mock/MockViews.swift:804,805,806
    val s = buildAnnotatedString { withStyle(SpanStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)) { append(if (r > 0) "▲ " else if (r < 0) "▼ " else "") }; append(MockFmt.pct(r, digits = 1)) }   // ios: Mock/MockViews.swift:807
    mt(s, style = appFont(14f, FontWeight.SemiBold).tnum(), maxLines = 1,   // ios: Mock/MockViews.swift:808
        color = if (r > 0) Theme.up else if (r < 0) Theme.down else Theme.sub)   // ios: Mock/MockViews.swift:809
}

/** 머리줄 정렬 토글: 오른 순 ⇅ / 내린 순 ⇅ */
@Composable
fun MockSortToggle(downFirst: Boolean, set: (Boolean) -> Unit) {   // ios: Mock/MockViews.swift:814,815,816
    Box(Modifier.heightIn(min = 32.dp).clickable { set(!downFirst) }, contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:817,819,821
        mt(if (downFirst) "내린 순 ⇅" else "오른 순 ⇅", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Mock/MockViews.swift:818
    }
}

/** 빈 카드 한 줄 */
@Composable
fun MockEmptyRow(text: String) {   // ios: Mock/MockViews.swift:826,827,828
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:829
        com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:830
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {   // ios: Mock/MockViews.swift:832,833
            mt(text, style = appFont(13f), color = Theme.muted)   // ios: Mock/MockViews.swift:831
        }
    }
}

/** 글자 폭을 재어 모으고, 같은 칸 가장 넓은 폭에 오른쪽 끝을 맞춘다 (종목 탭과 같은 칸 맞춤) */
internal fun Modifier.holdCol(k: Int, cw: com.naeilo.app.ui.hold.HoldColW?): Modifier = if (cw == null) this else this.layout { mm, c ->
    val p = mm.measure(c.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
    cw.reduce(k, p.width)
    val w = max(p.width, cw.w[k] ?: 0)
    layout(w, p.height) { p.place(w - p.width, 0) }
}

/** 종목 한 줄: 이름(· 빨간 점) · 가운데(12 sub) · % · 값 · 오른쪽 알약. 뒤 왼쪽에 수익률 띠 (종목 탭 tint 와 같은 색, 최대 34%) */
@Composable
fun MockStockRow(   // ios: Mock/MockViews.swift:839
    code: String,   // ios: Mock/MockViews.swift:840
    name: String,   // ios: Mock/MockViews.swift:841
    mid: String,   // ios: Mock/MockViews.swift:842
    pct: Double,   // ios: Mock/MockViews.swift:843
    price: String? = null,   // ios: Mock/MockViews.swift:844
    band: Double = 0.0,   // ios: Mock/MockViews.swift:845
    struck: Boolean = false,   // ios: Mock/MockViews.swift:846
    seen: Int = 0,   // ios: Mock/MockViews.swift:847
    filled: Boolean = false,   // ios: Mock/MockViews.swift:848
    outside: Boolean = false,   // ios: Mock/MockViews.swift:849
    cut: String? = null,   // ios: Mock/MockViews.swift:850
    colW: com.naeilo.app.ui.hold.HoldColW? = null,   // ios: Mock/MockViews.swift:851
    right: @Composable () -> Unit = {},   // ios: Mock/MockViews.swift:852,928,930
) {
    val dark = LocalPal.current.isDark   // ios: Mock/MockViews.swift:853
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:856,867,855
        com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:857
        // 줄이 화면보다 넓으면 카드가 밀려 좌우 여백이 잘린다. 모자라면 가운데 칸을 뺀다
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (struck) 0.55f else 1f)) {   // ios: Mock/MockViews.swift:863,865
            // 뒤 왼쪽 수익률 띠
            val c = if (pct > 0) com.naeilo.app.ui.hex(0xFF8A80) else if (pct < 0) com.naeilo.app.ui.hex(0x8CC4F2) else if (dark) com.naeilo.app.ui.hex(0x4A5672) else com.naeilo.app.ui.hex(0xAAB2BE)   // ios: Mock/MockViews.swift:908
            val f = max(0.12, min(1.0, band))   // ios: Mock/MockViews.swift:909
            Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxWidth((0.34 * f).toFloat()).fillMaxSize().background(c.copy(alpha = if (dark) 0.26f else 0.32f))) }   // ios: Mock/MockViews.swift:864,907,910,911,913
            com.naeilo.app.ui.FitsOr(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),   // ios: Mock/MockViews.swift:859,863
                first = { MockStockLine(true, name, mid, pct, price, struck, seen, filled, outside, cut, colW, right) },   // ios: Mock/MockViews.swift:860
                second = { MockStockLine(false, name, mid, pct, price, struck, seen, filled, outside, cut, colW, right) })   // ios: Mock/MockViews.swift:861
        }
    }
}

@Composable
private fun MockStockLine(show: Boolean, name: String, mid: String, pct: Double, price: String?, struck: Boolean, seen: Int, filled: Boolean, outside: Boolean, cut: String?,   // ios: Mock/MockViews.swift:870
                          colW: com.naeilo.app.ui.hold.HoldColW?, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockViews.swift:871
        Row(Modifier.weight(1f).widthIn(min = 56.dp).defaultMinSize(minWidth = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (filled) 9.dp else 4.dp)) {   // ios: Mock/MockViews.swift:872,886
            Box {
                mt(name, style = appFont(15f, FontWeight.Bold).copy(textDecoration = if (struck) androidx.compose.ui.text.style.TextDecoration.LineThrough else null), maxLines = 1)   // ios: Mock/MockViews.swift:873,874
                // 체결: 이름 오른쪽 위 모서리 노란 점
                if (filled) {   // ios: Mock/MockViews.swift:876
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-3).dp).size(7.dp, 7.dp).background(Theme.gold, CircleShape)   // ios: Mock/MockViews.swift:877
                        .semantics { contentDescription = "체결" })   // ios: Mock/MockViews.swift:878
                }
            }
            if (outside) {   // ios: Mock/MockViews.swift:881
                Box(Modifier.size(6.dp, 6.dp).background(Theme.muted, CircleShape)   // ios: Mock/MockViews.swift:882
                    .semantics { contentDescription = "외부" })   // ios: Mock/MockViews.swift:883
            }
        }
        // 걸린 횟수 숫자 · 거른 이유 점: 이름 뒤 한 칸에 모아 줄마다 같은 자리 (오른쪽 맞춤)
        if (seen >= 2 || cut != null) {   // ios: Mock/MockViews.swift:888
            Row(Modifier.holdCol(4, colW), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Mock/MockViews.swift:889,896
                if (seen >= 2) MockSeenPill(n = seen)   // ios: Mock/MockViews.swift:890
                if (cut != null) {   // ios: Mock/MockViews.swift:891
                    Box(Modifier.size(7.dp, 7.dp).background(MockCutTone.color(cut), CircleShape)   // ios: Mock/MockViews.swift:892
                        .semantics { contentDescription = "거름 · " + cut })   // ios: Mock/MockViews.swift:893
                }
            }
        }
        if (show) mt(mid, Modifier.holdCol(0, colW), style = appFont(12f).tnum(), color = Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:898
        mt(MockFmt.pct(pct), Modifier.holdCol(1, colW), style = appFont(14f, FontWeight.Bold).tnum(), maxLines = 1,   // ios: Mock/MockViews.swift:899,901
            color = if (pct > 0) Theme.up else if (pct < 0) Theme.down else Theme.sub)   // ios: Mock/MockViews.swift:900
        if (price != null) mt(price, Modifier.holdCol(2, colW), style = appFont(14f, FontWeight.Bold).tnum(), maxLines = 1)   // ios: Mock/MockViews.swift:902
        right()   // ios: Mock/MockViews.swift:903
    }
}

/** 줄 오른쪽 'N주'(금색, 체결) · 'N주 대기'(회색) */
@Composable
fun MockQtyPill(text: String, done: Boolean) {   // ios: Mock/MockViews.swift:918,919,920,921
    mt(text, Modifier.background(if (done) Theme.gold else Theme.track, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 3.dp),   // ios: Mock/MockViews.swift:923,924
        style = appFont(12f, FontWeight.Bold), color = if (done) Color.Black else Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:922
}

/** 팝업 머리: 제목 왼쪽, 닫기(X) 오른쪽 (SheetTitleBar 와 같은 모양, 글은 번역 목록 밖) */
@Composable
fun MockSheetBar(title: String, close: () -> Unit) {   // ios: Mock/MockViews.swift:935,936,937,938
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:939,948
        mt(title, Modifier.weight(1f).padding(end = 8.dp), style = appFont(22f, FontWeight.Bold), color = Theme.ink, maxLines = 1)   // ios: Mock/MockViews.swift:940,941
        Box(Modifier.size(44.dp, 44.dp).clickable(onClick = close).semantics { contentDescription = "닫기" }, contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:942,944,946
            com.naeilo.app.ui.Sym("xmark", 15f, Theme.sub)   // ios: Mock/MockViews.swift:943
        }
    }
}

/** 탭 화면 틀: 머리 · 요약 박스 · 본문 */
@Composable
fun MockScreen(tab: MockTab, gear: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {   // ios: Mock/MockViews.swift:953,954,955,956
    var live by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:957,958
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:959
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {   // ios: Mock/MockViews.swift:960,998,999
        com.naeilo.app.ui.Screen {   // ios: Mock/MockViews.swift:996
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Mock/MockViews.swift:961
                Row(Modifier.outset(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:962,992
                    Box(Modifier.weight(1f)) { com.naeilo.app.ui.AppHeader(tab = tab.label) }   // ios: Mock/MockViews.swift:963
                    // 계좌 탭만: 실전 규칙 목록 · 모의투자 설정 (그 뒤에 전환)
                    if (tab == MockTab.account && MockDesk.realOK) {   // ios: Mock/MockViews.swift:965
                        Box(Modifier.size(44.dp, 44.dp).clickable { live = true }.semantics { contentDescription = "실전 규칙" }, contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:966,968,970
                            com.naeilo.app.ui.Sym("checkmark.shield", 18f, Theme.sub)   // ios: Mock/MockViews.swift:967
                        }
                    }
                    if (gear != null) {   // ios: Mock/MockViews.swift:972
                        Box(Modifier.size(44.dp, 44.dp).clickable(onClick = gear).semantics { contentDescription = "모의투자 설정" }, contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:973,975,977
                            com.naeilo.app.ui.Sym("gearshape", 18f, Theme.sub)   // ios: Mock/MockViews.swift:974
                        }
                    }
                    // 모의 ↔ 실전 전환: 누를 때마다 바뀌고 네 탭이 같이 따른다 (실전은 개발자 기기만)
                    if (MockDesk.realOK) {   // ios: Mock/MockViews.swift:980
                        Box(Modifier.padding(end = 8.dp).size(44.dp, 44.dp).clickable { d.show(if (d.shown == MockBook.real) MockBook.rule else MockBook.real) }   // ios: Mock/MockViews.swift:981,984,987
                            .semantics { contentDescription = if (d.shown == MockBook.real) "실전 · 눌러서 모의로" else "모의 · 눌러서 실전으로" }, contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:986
                            com.naeilo.app.ui.Sym(if (d.shown == MockBook.real) "bolt.fill" else "testtube.2", 18f,   // ios: Mock/MockViews.swift:982
                                if (d.shown == MockBook.real) Theme.up else Theme.sub)   // ios: Mock/MockViews.swift:983
                        }
                    } else {   // ios: Mock/MockViews.swift:988
                        Spacer(Modifier.width(8.dp))   // ios: Mock/MockViews.swift:989
                    }
                }
                MockTabBox(tab = tab)   // ios: Mock/MockViews.swift:993
                content()   // ios: Mock/MockViews.swift:994
            }
        }
    }
    com.naeilo.app.ui.Sheet(show = live, onDismiss = { live = false }) { MockLiveSheet(tab = tab) { live = false } }   // ios: Mock/MockViews.swift:1000
    LaunchedEffect(Unit) {   // ios: Mock/MockViews.swift:1001
        MockDesk.shared.refreshPhase()   // ios: Mock/MockViews.swift:1002
        // [실전] 칸으로 탭을 열 때 토스 계좌를 한 번 읽는다 (계좌 탭은 제 LaunchedEffect 에서 읽는다)
        if (tab != MockTab.account && MockDesk.shared.shown == MockBook.real) MockDesk.shared.refreshAccount()   // ios: Mock/MockViews.swift:1004
    }
}

/** 실전 규칙: 규칙 · 반영 방식 · 아직 다른 점 (목록은 MockLive.rules 한 곳). 탭마다 그 탭에 걸린 규칙만 */
@Composable
fun MockLiveSheet(tab: MockTab, dismiss: () -> Unit) {   // ios: Mock/MockViews.swift:1010,1011,1012,1013
    val d = MockDesk.shared; val br = d.broker(d.shown)   // ios: Mock/MockViews.swift:1014
    val t: MockLive.Tab = if (tab == MockTab.observe) MockLive.Tab.observe else if (tab == MockTab.select) MockLive.Tab.select else MockLive.Tab.journal   // ios: Mock/MockViews.swift:1015
    Column(Modifier.fillMaxWidth().background(Theme.bg), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1016,1045
        MockSheetBar(title = tab.label + " · 실전 규칙") { dismiss() }   // ios: Mock/MockViews.swift:1017
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Mock/MockViews.swift:1018,1019,1042
            mt("주문층: " + br.name + (if (br.live) " · 실전" else " · 실제 주문 없음"), style = appFont(13f, FontWeight.SemiBold), color = if (br.live) Theme.up else Theme.sub)   // ios: Mock/MockViews.swift:1020
            if (t == MockLive.Tab.observe) {   // ios: Mock/MockViews.swift:1021
                // 지금 관찰 조건 (켠 알약) — 아래는 실전에 더 볼 조건
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Mock/MockViews.swift:1023
                    mt("지금 관찰 조건", style = appFont(15f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1024
                    val on = d.conds.filter { it.on }   // ios: Mock/MockViews.swift:1025
                    mt(if (on.isEmpty()) "켠 조건 없음" else on.joinToString(" · ") { it.piece.text }, style = appFont(13f), color = Theme.ink)   // ios: Mock/MockViews.swift:1026
                }
                com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1028
                mt("실전에 더 볼 조건", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Mock/MockViews.swift:1029
            }
            MockLive.rules(t).forEach { r ->   // ios: Mock/MockViews.swift:1031
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Mock/MockViews.swift:1032
                    mt(r.name, style = appFont(15f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1033
                    if (r.how != "—") mt(r.how, style = appFont(13f), color = Theme.ink)   // ios: Mock/MockViews.swift:1034
                    val g = r.gap; if (g != null) {   // ios: Mock/MockViews.swift:1035
                        mt("실전과 다른 점 · " + g, style = appFont(12f), color = Theme.orange)   // ios: Mock/MockViews.swift:1036
                    }
                }
                com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1039
            }
        }
    }
}


// MARK: 관찰

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MockObserveView() {   // ios: Mock/MockViews.swift:1051,1059
    var draft by remember { mutableStateOf(MockPieceDraft()) }   // ios: Mock/MockViews.swift:1052
    var adding by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1053
    var more by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1054
    var downFirst by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1055
    val colW = remember { com.naeilo.app.ui.hold.HoldColW() }   // ios: Mock/MockViews.swift:1056,1067
    var info by remember { mutableStateOf<MockStockRef?>(null) }   // ios: Mock/MockViews.swift:1057
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:1060

    fun toggle(c: MockCond) {   // ios: Mock/MockViews.swift:1099,1102
        val cs = d.conds.toMutableList()   // ios: Mock/MockViews.swift:1100
        val i = cs.indexOfFirst { it.id == c.id }.takeIf { it >= 0 } ?: return   // ios: Mock/MockViews.swift:1101
        cs[i] = cs[i].copy(on = !cs[i].on)   // ios: Mock/MockViews.swift:1101
        d.setConds((if (cs[i].on) "켬 · " else "끔 · ") + c.piece.text, cs)   // ios: Mock/MockViews.swift:1103
    }

    fun remove(c: MockCond) {   // ios: Mock/MockViews.swift:1106
        d.setConds("지움 · " + c.piece.text, d.conds.filter { it.id != c.id })   // ios: Mock/MockViews.swift:1107
        if (draft.edit == c.id) adding = false   // ios: Mock/MockViews.swift:1108
    }

    fun commit(p: MockPiece) {   // ios: Mock/MockViews.swift:1111
        val cs = d.conds.toMutableList()   // ios: Mock/MockViews.swift:1112,1113
        val i = cs.indexOfFirst { it.id == p.id }   // ios: Mock/MockViews.swift:1112
        if (i >= 0) {
            cs[i] = cs[i].copy(piece = p)   // ios: Mock/MockViews.swift:1114
            d.setConds("고침 · " + p.text, cs)   // ios: Mock/MockViews.swift:1115,1116
        } else {   // ios: Mock/MockViews.swift:1115
            cs.add(MockCond(piece = p))   // ios: Mock/MockViews.swift:1117
            d.setConds("넣음 · " + p.text, cs)   // ios: Mock/MockViews.swift:1118
        }
        adding = false   // ios: Mock/MockViews.swift:1120
    }

    MockScreen(tab = MockTab.observe) {   // ios: Mock/MockViews.swift:1061,1062
        // condCard
        Card {   // ios: Mock/MockViews.swift:1071,1072
            // 훑기 단추는 '+ 조건'과 같은 줄 오른쪽 끝 (자리가 모자라면 다음 줄 오른쪽)
            com.naeilo.app.ui.home.FlowRow(spacing = 6.dp, tail = if (d.nearOnly) 2 else 1) {   // ios: Mock/MockViews.swift:1074
                d.conds.forEach { c ->   // ios: Mock/MockViews.swift:1075
                    MockCondPill(text = c.piece.text, on = c.on,   // ios: Mock/MockViews.swift:1076
                        tap = { toggle(c) },   // ios: Mock/MockViews.swift:1077
                        edit = { draft = MockPieceDraft(c.piece); adding = true },   // ios: Mock/MockViews.swift:1078
                        delete = { remove(c) })   // ios: Mock/MockViews.swift:1079
                }
                // 새 배수: 곡선이 없어도 기본 U자 곡선으로 바로 손으로 켠다 (2026-11-05). 다 쌓이기 전엔 쌓인 날 수를 붙이고, 다 쌓이면 저절로 켜진다
                MockCondPill(text = "새 배수" + (if (d.curveDays < MockClock.newVolDays) " · 곡선 ${d.curveDays}/${MockClock.newVolDays}일" else ""),   // ios: Mock/MockViews.swift:1082
                    on = d.s.newVol == true, tap = { d.setNewVol(d.s.newVol != true) })   // ios: Mock/MockViews.swift:1083
                MockAddPill { draft = MockPieceDraft(); adding = true }   // ios: Mock/MockViews.swift:1084
                if (d.nearOnly) MockGoPill(title = "전체 훑기") { d.runObserve(full = true) }   // ios: Mock/MockViews.swift:1085
                MockGoPill(title = "지금 훑기") { d.runObserve() }   // ios: Mock/MockViews.swift:1086
            }
            if (adding) {   // ios: Mock/MockViews.swift:1088
                MockPieceForm(draft = draft, setDraft = { draft = it }, close = { adding = false }) { commit(it) }   // ios: Mock/MockViews.swift:1089
            }
            d.s.runs["observe"]?.let { r -> if (!r.ok) MockRunLine(run = r) }   // ios: Mock/MockViews.swift:1091
            // 현황판이 없을 때만 안내를 카드 안에 둔다
            val plan = d.scanPlan
            if (d.progress == null && d.s.scan == null && plan != null) {   // ios: Mock/MockViews.swift:1093
                mt("다음 훑기 · " + plan, style = appFont(12f), color = Theme.muted, maxLines = 1)   // ios: Mock/MockViews.swift:1094
            }
        }
        MockObserveBoard(d)   // ios: Mock/MockViews.swift:1063
        d.s.scan?.let { sc ->   // ios: Mock/MockViews.swift:1064
            // hitsCard: 거른 종목도 함께 보인다 (이름 뒤 이유별 색 점, 뜻은 목록 아래 캡션). 고르기는 sc.hits 만 쓴다
            val cut = LinkedHashMap<String, String>()   // ios: Mock/MockViews.swift:1163,1161
            (sc.skipped ?: emptyList()).forEach { k -> k.hit?.let { h -> if (!cut.containsKey(h.code)) cut[h.code] = k.why.split(" · ").first() } }   // ios: Mock/MockViews.swift:1163
            val all = sc.hits + (sc.skipped ?: emptyList()).mapNotNull { it.hit }   // ios: Mock/MockViews.swift:1164
            val ch = all.mapNotNull { it.change }   // ios: Mock/MockViews.swift:1165
            val avg = if (ch.isEmpty()) 0.0 else ch.fold(0.0) { a, b -> a + b } / ch.size.toDouble()   // ios: Mock/MockViews.swift:1166
            val mx = ch.map { abs(it) }.maxOrNull() ?: 0.0   // ios: Mock/MockViews.swift:1167
            val sorted = all.sortedWith { a, b ->   // ios: Mock/MockViews.swift:1168
                val x = a.change ?: 0.0; val y = b.change ?: 0.0   // ios: Mock/MockViews.swift:1169
                if (downFirst) x.compareTo(y) else y.compareTo(x)   // ios: Mock/MockViews.swift:1176,1170
            }
            val shown = sorted.take(if (more) 200 else 10)   // ios: Mock/MockViews.swift:1172
            val twice = MockDesk.shared.seenToday   // ios: Mock/MockViews.swift:1173
            MockListCard(modifier = Modifier.animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 400f)), head = {   // ios: Mock/MockViews.swift:1174,1199
                mt("걸린 종목 ${all.size}", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1175
                MockSortToggle(downFirst = downFirst, set = { downFirst = it })   // ios: Mock/MockViews.swift:1176
                Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1177
                if (ch.isNotEmpty()) MockMove(r = avg)   // ios: Mock/MockViews.swift:1178,1179
            }) {   // ios: Mock/MockViews.swift:1183
                if (all.isEmpty()) MockEmptyRow(text = "걸린 종목이 없어요")   // ios: Mock/MockViews.swift:1180
                shown.forEach { h ->   // ios: Mock/MockViews.swift:1181
                    val c = h.change ?: 0.0   // ios: Mock/MockViews.swift:1182
                    var menu by remember(h.code) { mutableStateOf(false) }
                    Box(Modifier.combinedClickable(onClick = { info = MockStockRef(code = h.code, name = h.name) },   // ios: Mock/MockViews.swift:1183,1187,1188,1190
                        onLongClick = { if ((twice[h.code] ?: 0) >= 2) menu = true })) {   // ios: Mock/MockViews.swift:1189,1190
                        MockStockRow(code = h.code, name = h.name, mid = listOfNotNull(h.vol?.let { cformat("%.1f배", it) }, MockDesk.shared.warnText(h.code)).joinToString(" · "),   // ios: Mock/MockViews.swift:1184
                            pct = c, price = MockFmt.won(h.price), band = if (mx > 0) abs(c) / mx else 0.0, seen = twice[h.code] ?: 0, cut = cut[h.code], colW = colW)   // ios: Mock/MockViews.swift:1185
                        com.naeilo.app.ui.ContextMenu(show = menu, items = listOf(
                            com.naeilo.app.ui.MenuItem("걸린 횟수 지우기 (오늘 ${twice[h.code] ?: 0}번)", icon = "arrow.counterclockwise", destructive = true) { MockDesk.shared.clearSeenToday(h.code) },   // ios: Mock/MockViews.swift:1189,1190,1191
                        ), onDismiss = { menu = false })
                    }
                }
                if (all.size > 10) {   // ios: Mock/MockViews.swift:1200,1196
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1201,1197
                        com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1198
                        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { more = !more }, contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:1199,1201,1203
                            mt(if (more) "접기" else "${all.size - 10}종목 더 보기", style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Mock/MockViews.swift:1200
                        }
                    }
                }
                MockCutCaption(cut = cut, seen = all.any { (twice[it.code] ?: 0) >= 2 })   // ios: Mock/MockViews.swift:1206
            }
        }
        MockCurveCard(curves = d.s.curves ?: emptyList(), note = d.s.curveNote)   // ios: Mock/MockViews.swift:1065
    }
    com.naeilo.app.ui.Sheet(show = info != null, onDismiss = { info = null }) {   // ios: Mock/MockViews.swift:1068
        info?.let { MockStockSheet(code = it.code, name = it.name, seen = true) { info = null } }
    }
}

/** 훑기 상자와 걸린 종목 상자 사이 전광판: 훑는 중엔 받음 · 걸린 · 못 받은, 끝나면 걸린 · 거른 · 못 받은 */
@Composable
private fun MockObserveBoard(d: MockDesk) {   // ios: Mock/MockViews.swift:1124,1125
    val p = d.progress
    val sc = d.s.scan
    if (p != null) {   // ios: Mock/MockViews.swift:1124
        val l = d.scanLive ?: (0 to 0)   // ios: Mock/MockViews.swift:1126
        MockFlipBoard(slots = listOf("받음" to "${p.first.fmtInt()}/${p.second.fmtInt()}", "걸린" to l.first.fmtInt(), "못 받은" to l.second.fmtInt()), note = d.scanPlan)   // ios: Mock/MockViews.swift:1127,1128
    } else if (sc != null) {   // ios: Mock/MockViews.swift:1127
        val sk = sc.skipped ?: emptyList()   // ios: Mock/MockViews.swift:1129
        MockFlipBoard(slots = listOf("걸린" to (sc.hits.size + sk.size).fmtInt(), "거른" to sk.size.fmtInt(), "못 받은" to (sc.fails ?: emptyMap()).values.fold(0) { a, b -> a + b }.fmtInt()), note = d.scanPlan)   // ios: Mock/MockViews.swift:1130
    }
}

/** 목록 아래 점 캡션: 거른 이유별 색 점 · 수, 숫자 알약 = 오늘 걸린 횟수. 둘 다 없으면 숨긴다 */
@Composable
private fun MockCutCaption(cut: Map<String, String>, seen: Boolean) {   // ios: Mock/MockViews.swift:1135,1136
    if (cut.isNotEmpty() || seen) {   // ios: Mock/MockViews.swift:1135
        val count = cut.values.groupingBy { it }.eachCount()   // ios: Mock/MockViews.swift:1137
        val reasons = count.keys.sortedWith { a, b -> val x = count[a] ?: 0; val y = count[b] ?: 0; if (x != y) y.compareTo(x) else a.compareTo(b) }   // ios: Mock/MockViews.swift:1138
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1139
            com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1140
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {   // ios: Mock/MockViews.swift:1155,1156
                com.naeilo.app.ui.home.FlowRow(spacing = 10.dp) {   // ios: Mock/MockViews.swift:1141
                    reasons.forEach { r ->   // ios: Mock/MockViews.swift:1142
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1143
                            Box(Modifier.size(7.dp, 7.dp).background(MockCutTone.color(r), CircleShape))   // ios: Mock/MockViews.swift:1144
                            mt("$r ${count[r] ?: 0}", style = appFont(11f), color = Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:1145
                        }
                    }
                    if (seen) {   // ios: Mock/MockViews.swift:1148
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1149
                            MockSeenPill(n = null)   // ios: Mock/MockViews.swift:1150
                            mt("오늘 걸린 횟수", style = appFont(11f), color = Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:1151
                        }
                    }
                }
            }
        }
    }
}

/** 하루 거래량 곡선 (관찰 탭 아래): 최근 곡선들의 칸별 가운데 값 · 지금 쌓임 · 시각별 쌓임. 새 배수가 나누는 값 */
@Composable
fun MockCurveCard(curves: List<MockCurve>, note: String? = null) {   // ios: Mock/MockViews.swift:1212,1213,1214,1217
    val cs = curves.filter { it.cum.size == 27 }   // ios: Mock/MockViews.swift:1218
    everyMinute()
    Card {   // ios: Mock/MockViews.swift:1219
        Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1220
            mt("거래량 곡선", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1221
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1222
            cs.lastOrNull()?.let { last -> mt("${cs.size}일 · 마지막 ${MockFmt.md(last.day)}", style = appFont(12f), color = Theme.muted) }   // ios: Mock/MockViews.swift:1223
        }
        val mid = MockCalc.curveNow(cs)   // ios: Mock/MockViews.swift:1225
        if (mid != null) {
            val now = MockCurveCard.nowX()   // ios: Mock/MockViews.swift:1226
            MockCurvePlot(mid, now = now, modifier = Modifier.fillMaxWidth().height(60.dp))   // ios: Mock/MockViews.swift:1227
            val f = if (now != null) MockCalc.frac(cs, at = mockNow()) else null   // ios: Mock/MockViews.swift:1228
            if (f != null) {
                mt("지금 쌓임 ${(f * 100).swRound().toInt()}%", style = appFont(12f).tnum(), color = Theme.sub)   // ios: Mock/MockViews.swift:1229
            }
            mt(MockCurveCard.marks.joinToString(" · ") { k -> "${MockCurveCard.time(k)} ${(mid[k] * 100).swRound().toInt()}%" },   // ios: Mock/MockViews.swift:1231
                style = appFont(11f).tnum(), color = Theme.muted)   // ios: Mock/MockViews.swift:1232
        }
        if (cs.isEmpty()) {   // ios: Mock/MockViews.swift:1234 비어 있을 때: 기본 곡선으로 나눈다는 것 · 언제 쌓이는지 · 마지막 실패 이유
            mt("아직 곡선이 없어요 · 기본 U자로 나눠요", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Mock/MockViews.swift:1235
            mt("장 밖(8:30 전·15:45 뒤)에 투자 모드로 앱을 열어 두면 하루 하나씩 쌓여요 · " + MockCurveCard.next(),   // ios: Mock/MockViews.swift:1236
                style = appFont(12f), color = Theme.muted)   // ios: Mock/MockViews.swift:1237
            if (note != null) mt(note, style = appFont(12f), color = Theme.orange)   // ios: Mock/MockViews.swift:1238
        }
    }
}

object MockCurveCard {
    val marks = listOf(2, 4, 12, 20, 24)   // 9:30 10:00 12:00 14:00 15:00   // ios: Mock/MockViews.swift:1215

    /** 다음 받을 때: 장중이면 15:45, 장 밖이면 곧 */
    fun next(): String {   // ios: Mock/MockViews.swift:1244
        val m = MockClock.minute(mockNow())   // ios: Mock/MockViews.swift:1245
        return if (m >= MockClock.curveUntil && m < MockClock.curveFrom) "다음 ${MockClock.hm(MockClock.curveFrom)}" else "곧 받아요"   // ios: Mock/MockViews.swift:1246
    }

    fun time(k: Int): String {   // ios: Mock/MockViews.swift:1249
        val m = 540 + 15 * k   // ios: Mock/MockViews.swift:1250
        return "${m / 60}:" + cformat("%02d", m % 60)   // ios: Mock/MockViews.swift:1251
    }

    /** 9:00~15:30 사이 지금 위치 (0~1). 장 밖이면 null */
    fun nowX(): Float? {   // ios: Mock/MockViews.swift:1279
        val c = java.time.Instant.ofEpochMilli((mockNow() * 1000).toLong()).atZone(MockFmt.kst)   // ios: Mock/MockViews.swift:1280,1281
        val m = c.hour * 60 + c.minute - 540
        return if (m > 0 && m < 390) m.toFloat() / 390f else null   // ios: Mock/MockViews.swift:1283
    }
}

/** 0~100% 꺾은선. 장중이면 지금 시각 위치에 점 하나 */
@Composable
private fun MockCurvePlot(ys: List<Double>, now: Float?, modifier: Modifier) {   // ios: Mock/MockViews.swift:1255
    val line = Theme.line; val teal = Theme.teal; val ink = Theme.ink
    androidx.compose.foundation.Canvas(modifier) {   // ios: Mock/MockViews.swift:1256,1260
        val w = size.width; val h = size.height; val n = ys.size - 1   // ios: Mock/MockViews.swift:1257
        val y = { v: Double -> h * (1f - min(1.0, max(0.0, v)).toFloat()) }   // ios: Mock/MockViews.swift:1258
        val pt = { k: Int -> androidx.compose.ui.geometry.Offset(w * k / n, y(ys[k])) }   // ios: Mock/MockViews.swift:1259
        drawLine(line, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(w, h), strokeWidth = 1.dp.toPx())   // ios: Mock/MockViews.swift:1261
        val p = androidx.compose.ui.graphics.Path()   // ios: Mock/MockViews.swift:1262
        p.moveTo(pt(0).x, pt(0).y)   // ios: Mock/MockViews.swift:1263
        for (k in 1..n) p.lineTo(pt(k).x, pt(k).y)   // ios: Mock/MockViews.swift:1264
        drawPath(p, teal, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))   // ios: Mock/MockViews.swift:1266
        if (now != null) {   // ios: Mock/MockViews.swift:1267
            // 15분 칸 사이는 곧게 잇는다 (frac 과 같게)
            val u = now.toDouble() * n; val k = min(n - 1, u.toInt()); val r = u - k   // ios: Mock/MockViews.swift:1269
            val v = ys[k] + (ys[k + 1] - ys[k]) * r   // ios: Mock/MockViews.swift:1270
            drawCircle(ink, radius = androidx.compose.ui.geometry.Size(7.dp.toPx(), 7.dp.toPx()).width / 2, center = androidx.compose.ui.geometry.Offset((w * u / n).toFloat(), y(v)))   // ios: Mock/MockViews.swift:1271,1272
        }
    }
}


// MARK: 선정 (주문 합침)

/** 고른 종목 한 줄: 검토 중 · 대기 · 체결 · 손으로 뺌 */
data class MockPickRow(   // ios: Mock/MockViews.swift:1290
    val code: String,   // ios: Mock/MockViews.swift:1292
    val name: String,   // ios: Mock/MockViews.swift:1293
    val qty: Int,   // ios: Mock/MockViews.swift:1294
    val kind: Kind,   // ios: Mock/MockViews.swift:1295
    val price: Double,            // 지금 가격   // ios: Mock/MockViews.swift:1296
    val pct: Double,              // 체결 = 산 값 대비, 나머지 = 오늘 등락   // ios: Mock/MockViews.swift:1297
    val side: MockSide = MockSide.buy,   // ios: Mock/MockViews.swift:1298
    val orderId: String? = null,   // ios: Mock/MockViews.swift:1299
    val add: Boolean = false,              // 보유 종목 추가 매수   // ios: Mock/MockViews.swift:1300
) {
    enum class Kind { pick, wait, filled, dropped }   // ios: Mock/MockViews.swift:1291
    val id: String get() = code + kind.name + side.name   // ios: Mock/MockViews.swift:1301
}

@Composable
fun MockSelectView() {   // ios: Mock/MockViews.swift:1304,1311
    var draft by remember { mutableStateOf(MockRuleDraft()) }   // ios: Mock/MockViews.swift:1305
    var adding by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1306
    var editRow by remember { mutableStateOf<MockPickRow?>(null) }   // ios: Mock/MockViews.swift:1307
    var downFirst by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1308
    val colW = remember { com.naeilo.app.ui.hold.HoldColW() }   // ios: Mock/MockViews.swift:1309,1318
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:1312

    fun toggle(c: MockRuleCond) {   // ios: Mock/MockViews.swift:1347,1350
        val cs = d.rconds.toMutableList()   // ios: Mock/MockViews.swift:1348
        val i = cs.indexOfFirst { it.id == c.id }.takeIf { it >= 0 } ?: return   // ios: Mock/MockViews.swift:1349
        cs[i] = cs[i].copy(on = !cs[i].on)   // ios: Mock/MockViews.swift:1348
        d.setRuleConds((if (cs[i].on) "켬 · " else "끔 · ") + c.text, cs)   // ios: Mock/MockViews.swift:1351
    }

    fun remove(c: MockRuleCond) {   // ios: Mock/MockViews.swift:1354
        d.setRuleConds("지움 · " + c.text, d.rconds.filter { it.id != c.id })   // ios: Mock/MockViews.swift:1355
        if (draft.edit == c.id) adding = false   // ios: Mock/MockViews.swift:1356
    }

    fun commit(c: MockRuleCond) {   // ios: Mock/MockViews.swift:1359
        val cs = d.rconds.toMutableList()   // ios: Mock/MockViews.swift:1360
        val i = cs.indexOfFirst { it.id == c.id }   // ios: Mock/MockViews.swift:1359
        if (i >= 0) {
            val x = c.copy(on = cs[i].on)   // ios: Mock/MockViews.swift:1360,1361
            cs[i] = x   // ios: Mock/MockViews.swift:1362,1363
            d.setRuleConds("고침 · " + c.text, cs)   // ios: Mock/MockViews.swift:1365,1366,1367
        } else {   // ios: Mock/MockViews.swift:1364
            cs.add(c)   // ios: Mock/MockViews.swift:1365
            d.setRuleConds("넣음 · " + c.text, cs)   // ios: Mock/MockViews.swift:1368
        }
        adding = false   // ios: Mock/MockViews.swift:1370
    }

    fun rows(): List<MockPickRow> {   // ios: Mock/MockViews.swift:1373
        val l = d.ledger(d.shown)   // ios: Mock/MockViews.swift:1374
        val change = LinkedHashMap<String, Double>()   // ios: Mock/MockViews.swift:1376
        (d.s.scan?.hits ?: emptyList()).forEach { if (!change.containsKey(it.code)) change[it.code] = it.change ?: 0.0 }   // ios: Mock/MockViews.swift:1375
        fun px(c: String, fallback: Double): Double = d.s.prices[c] ?: fallback   // ios: Mock/MockViews.swift:1376
        // 보유 종목은 일지에서 본다 (여기는 대기 주문 · 오늘 고른 것 · 뺀 것만)
        val out = ArrayList<MockPickRow>()   // ios: Mock/MockViews.swift:1378
        for (o in l.pending) {   // ios: Mock/MockViews.swift:1379
            val now = px(o.code, o.limit ?: 0.0)   // ios: Mock/MockViews.swift:1380
            out.add(MockPickRow(code = o.code, name = o.name, qty = o.qty, kind = MockPickRow.Kind.wait, price = now, pct = change[o.code] ?: 0.0, side = o.side, orderId = o.id,   // ios: Mock/MockViews.swift:1381
                add = o.side == MockSide.buy && l.positions[o.code] != null))   // ios: Mock/MockViews.swift:1382,1386
        }
        for (p in d.picks) {   // ios: Mock/MockViews.swift:1384
            out.add(MockPickRow(code = p.code, name = p.name, qty = p.qty, kind = MockPickRow.Kind.pick, price = px(p.code, p.price), pct = change[p.code] ?: 0.0,   // ios: Mock/MockViews.swift:1385
                add = l.positions[p.code] != null))   // ios: Mock/MockViews.swift:1382
        }
        for (p in d.dropped) {   // ios: Mock/MockViews.swift:1388
            out.add(MockPickRow(code = p.code, name = p.name, qty = p.qty, kind = MockPickRow.Kind.dropped, price = px(p.code, p.price), pct = change[p.code] ?: 0.0))   // ios: Mock/MockViews.swift:1389
        }
        return out.sortedWith { a, b -> if (downFirst) a.pct.compareTo(b.pct) else b.pct.compareTo(a.pct) }   // ios: Mock/MockViews.swift:1391
    }

    MockScreen(tab = MockTab.select) {   // ios: Mock/MockViews.swift:1313,1314,1315,1316
        // ruleCard
        Card {   // ios: Mock/MockViews.swift:1324,1325
            com.naeilo.app.ui.home.FlowRow(spacing = 6.dp) {   // ios: Mock/MockViews.swift:1326
                d.rconds.forEach { c ->   // ios: Mock/MockViews.swift:1327
                    MockCondPill(text = c.text, on = c.on,   // ios: Mock/MockViews.swift:1328
                        tap = { toggle(c) },   // ios: Mock/MockViews.swift:1329
                        edit = { draft = MockRuleDraft(c); adding = true },   // ios: Mock/MockViews.swift:1330
                        delete = { remove(c) })   // ios: Mock/MockViews.swift:1331
                }
                MockAddPill { draft = MockRuleDraft(); adding = true }   // ios: Mock/MockViews.swift:1333
            }
            if (adding) {   // ios: Mock/MockViews.swift:1335
                MockRuleForm(draft = draft, setDraft = { draft = it }, close = { adding = false }) { commit(it) }   // ios: Mock/MockViews.swift:1336
            }
            if (d.s.scan == null) mt("관찰 결과가 없어요 · 관찰 탭에서 먼저 훑어요", style = appFont(12f), color = Theme.up)   // ios: Mock/MockViews.swift:1338
            d.s.runs["select"]?.let { r -> if (!r.ok) MockRunLine(run = r) }   // ios: Mock/MockViews.swift:1339
            Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1340
                Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1341
                MockGoPill(title = "지금 고르기") { d.runSelect() }   // ios: Mock/MockViews.swift:1342
            }
        }
        // picksCard
        val rs = rows()   // ios: Mock/MockViews.swift:1395,1394
        val mx = rs.map { abs(it.pct) }.maxOrNull() ?: 0.0   // ios: Mock/MockViews.swift:1396
        MockListCard(head = {   // ios: Mock/MockViews.swift:1397
            mt("고른 종목 ${rs.count { it.kind != MockPickRow.Kind.dropped }}", style = appFont(14f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1398
            MockSortToggle(downFirst = downFirst, set = { downFirst = it })   // ios: Mock/MockViews.swift:1399
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1400
            d.s.pickedAt?.let { t -> mt(MockFmt.stamp(t) + " 고름", style = appFont(12f), color = Theme.muted) }   // ios: Mock/MockViews.swift:1401
        }) {   // ios: Mock/MockViews.swift:1400
            if (rs.isEmpty()) MockEmptyRow(text = "아직 고른 종목이 없어요")   // ios: Mock/MockViews.swift:1403,1404
            rs.forEach { r ->   // ios: Mock/MockViews.swift:1402
                Box(Modifier.clickable { editRow = r }) {   // ios: Mock/MockViews.swift:1401,1405
                    MockStockRow(code = r.code, name = r.name, mid = MockFmt.small(r.price), pct = r.pct, price = MockFmt.man(r.qty.toDouble() * r.price),   // ios: Mock/MockViews.swift:1406
                        band = if (mx > 0) abs(r.pct) / mx else 0.0, struck = r.kind == MockPickRow.Kind.dropped, filled = r.kind == MockPickRow.Kind.filled, colW = colW) {   // ios: Mock/MockViews.swift:1407
                        Box(Modifier.holdCol(3, colW)) { MockPickPill(r) }   // ios: Mock/MockViews.swift:1407
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1409,1411
                com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1412
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1413,1422
                    if (d.picks.isNotEmpty()) {   // ios: Mock/MockViews.swift:1414
                        val sum = d.picks.fold(0.0) { a, p -> a + p.budget }   // ios: Mock/MockViews.swift:1415
                        FitText("${d.picks.size}종목 · " + MockFmt.man(sum) + " · 수수료 " + MockFmt.won(d.s.fees.fee(sum)),   // ios: Mock/MockViews.swift:1416
                            style = appFont(12f).tnum(), minScale = 0.7f, color = Theme.sub, maxLines = 1, modifier = Modifier.weight(1f, fill = false))   // ios: Mock/MockViews.swift:1417
                    }
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1417,1419
                    MockGoPill(title = "주문", enabled = d.picks.isNotEmpty()) { d.orderPicks() }   // ios: Mock/MockViews.swift:1420
                }
            }
        }
        // cycleCard
        Card {   // ios: Mock/MockViews.swift:1441,1442,1440
            MockCycleLine(order = d.cycleOrder, setOrder = { v -> d.edit(if (v) "한 바퀴 주문 켬" else "한 바퀴 주문 끔") { it.cycleOrder = v } },   // ios: Mock/MockViews.swift:1442
                sell = d.cycleSell) { v -> d.edit(if (v) "한 바퀴 팔기 켬" else "한 바퀴 팔기 끔") { it.cycleSell = v } }   // ios: Mock/MockViews.swift:1443,1444
            Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1445
                Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1445
                MockGoPill(title = "지금 한 바퀴") { d.runCycle() }   // ios: Mock/MockViews.swift:1446
            }
        }
    }
    com.naeilo.app.ui.Sheet(show = editRow != null, onDismiss = { editRow = null }) {   // ios: Mock/MockViews.swift:1319,1320
        editRow?.let { r -> MockRowEdit(row = r) { editRow = null } }   // ios: Mock/MockViews.swift:1320
    }
}

@Composable
private fun MockPickPill(r: MockPickRow) {   // ios: Mock/MockViews.swift:1427
    when (r.kind) {   // ios: Mock/MockViews.swift:1428
        MockPickRow.Kind.filled ->   // ios: Mock/MockViews.swift:1429
            Spacer(Modifier.size(1.dp, 1.dp))   // ios: Mock/MockViews.swift:1430
        MockPickRow.Kind.wait, MockPickRow.Kind.pick ->   // ios: Mock/MockViews.swift:1431
            MockQtyPill(text = (if (r.side == MockSide.sell) "팔기 " else if (r.add) "추가 " else "") + "${r.qty}주 대기", done = false)   // ios: Mock/MockViews.swift:1432
        MockPickRow.Kind.dropped ->   // ios: Mock/MockViews.swift:1433
            mt("뺌", Modifier.border(1.dp, Theme.border, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 3.dp),   // ios: Mock/MockViews.swift:1434,1435,1436
                style = appFont(12f, FontWeight.Bold), color = Theme.muted, maxLines = 1)   // ios: Mock/MockViews.swift:1434
    }
}

/** 훑기 → 고르기 → [주문] → [팔기]: 주문·팔기는 알약 (누르면 켜고 끔, 끄면 건너뜀). order 가 없으면 주문은 글자 */
@Composable
fun MockCycleLine(order: Boolean? = null, setOrder: (Boolean) -> Unit = {}, sell: Boolean, set: (Boolean) -> Unit) {   // ios: Mock/MockViews.swift:1453,1454,1455,1456,1457,1458
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1459
        if (order != null) {   // ios: Mock/MockViews.swift:1460
            FitText("훑기 → 고르기 →", style = appFont(14f, FontWeight.SemiBold), minScale = 0.8f, color = Theme.sub, maxLines = 1, modifier = Modifier.weight(1f, fill = false))   // ios: Mock/MockViews.swift:1461
            MockCondPill(text = if (order) "✓ 주문" else "주문", on = order, tap = { setOrder(!order) })   // ios: Mock/MockViews.swift:1462
            mt("→", style = appFont(14f, FontWeight.SemiBold), color = Theme.sub)   // ios: Mock/MockViews.swift:1463,1464
        } else {   // ios: Mock/MockViews.swift:1465
            FitText("훑기 → 고르기 → 주문 →", style = appFont(14f, FontWeight.SemiBold), minScale = 0.8f, color = Theme.sub, maxLines = 1, modifier = Modifier.weight(1f, fill = false))   // ios: Mock/MockViews.swift:1465
        }
        MockCondPill(text = if (sell) "✓ 팔기" else "팔기", on = sell, tap = { set(!sell) })   // ios: Mock/MockViews.swift:1467
        Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Mock/MockViews.swift:1468
    }
}


/** 고른 종목 줄을 눌렀을 때: 검토 중 = 주수 고치기·빼기, 체결 = 주수 고치기·빼기(손 매매), 대기 = 지정가·취소 */
@Composable
fun MockRowEdit(row: MockPickRow, dismiss: () -> Unit) {   // ios: Mock/MockViews.swift:1474,1475,1476
    var qty by remember { mutableStateOf("${row.qty}") }   // ios: Mock/MockViews.swift:1477,1536,1537
    var limit by remember { mutableStateOf(MockFmt.num(row.price)) }   // ios: Mock/MockViews.swift:1478,1538
    var note by remember { mutableStateOf<String?>(null) }   // ios: Mock/MockViews.swift:1479
    var sending by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1480
    val scope = rememberCoroutineScope()
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:1483,1482

    fun done(ok: Boolean) {   // ios: Mock/MockViews.swift:1555
        if (ok) dismiss() else note = d.events.lastOrNull()?.let { it.kind.label + " · " + it.text } ?: "하지 못했어요"   // ios: Mock/MockViews.swift:1556
    }

    suspend fun setQty() {   // ios: Mock/MockViews.swift:1559
        val t = qty.toIntOrNull()?.takeIf { it >= 1 } ?: return   // ios: Mock/MockViews.swift:1560
        if (row.kind == MockPickRow.Kind.pick) { d.setPickQty(row.code, t); dismiss(); return }   // ios: Mock/MockViews.swift:1561
        if (t == row.qty) { dismiss(); return }   // ios: Mock/MockViews.swift:1562
        val ok: Boolean   // ios: Mock/MockViews.swift:1563
        if (t < row.qty) ok = d.runHand(MockSide.sell, code = row.code, name = row.name, qty = row.qty - t, type = MockOrderType.market, limit = null)   // ios: Mock/MockViews.swift:1564
        else ok = d.runHand(MockSide.buy, code = row.code, name = row.name, qty = t - row.qty, type = MockOrderType.market, limit = null)   // ios: Mock/MockViews.swift:1565
        done(ok)   // ios: Mock/MockViews.swift:1566
    }

    suspend fun drop() {   // ios: Mock/MockViews.swift:1569
        if (row.kind == MockPickRow.Kind.pick) { d.dropPick(row.code); dismiss(); return }   // ios: Mock/MockViews.swift:1570
        val ok = d.runHand(MockSide.sell, code = row.code, name = row.name, qty = null, type = MockOrderType.market, limit = null)   // ios: Mock/MockViews.swift:1571
        done(ok)   // ios: Mock/MockViews.swift:1572
    }

    /** 대기 주문 정정 (가격·수량). 실전이면 토스 정정 */
    suspend fun relimit() {   // ios: Mock/MockViews.swift:1576
        val id = row.orderId ?: return   // ios: Mock/MockViews.swift:1577
        val lim = MockNum.parse(limit)?.takeIf { it > 0 } ?: return
        val n = qty.toIntOrNull()?.takeIf { it >= 1 } ?: return
        val why = d.modify(d.shown, orderId = id, qty = n, limit = lim)   // ios: Mock/MockViews.swift:1578
        if (why != null) note = why else dismiss()
    }

    /** 지금 고친 주수대로 나갈 주문 (검토 중 = 그 주수 매수, 체결 = 늘리면 매수·줄이면 매도) */
    val planned: Pair<MockSide, Int>? = run {   // ios: Mock/MockViews.swift:1582
        val t = qty.toIntOrNull()?.takeIf { it >= 1 } ?: return@run null   // ios: Mock/MockViews.swift:1583
        if (row.kind == MockPickRow.Kind.pick) return@run MockSide.buy to t   // ios: Mock/MockViews.swift:1584
        if (t == row.qty) null else (if (t > row.qty) MockSide.buy else MockSide.sell) to abs(t - row.qty)   // ios: Mock/MockViews.swift:1585
    }

    @Composable
    fun actionButton(title: String, color: Color, enabled: Boolean, action: suspend () -> Unit) {   // ios: Mock/MockViews.swift:1542,1543
        val can = enabled && !sending
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).background(if (can) color else Theme.muted, RoundedCornerShape(12.dp))   // ios: Mock/MockViews.swift:1544,1545,1549,1546,1548,1551,1552
            .clickable(enabled = can) {   // ios: Mock/MockViews.swift:1542
                sending = true   // ios: Mock/MockViews.swift:1544
                scope.launch { action(); sending = false }   // ios: Mock/MockViews.swift:1545
            }, contentAlignment = Alignment.Center) {
            mt(title, style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Mock/MockViews.swift:1547
        }
    }

    Column(Modifier.fillMaxWidth().background(Theme.bg).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Mock/MockViews.swift:1486,1487,1533,1535,1484,1485
        MockSheetBar(title = row.name) { dismiss() }   // ios: Mock/MockViews.swift:1486
        Box(Modifier.padding(horizontal = 16.dp)) { MockStockChart(code = row.code, height = 120.dp) }   // ios: Mock/MockViews.swift:1487
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Mock/MockViews.swift:1488,1528,1530
            when (row.kind) {   // ios: Mock/MockViews.swift:1489
                MockPickRow.Kind.pick, MockPickRow.Kind.filled -> {   // ios: Mock/MockViews.swift:1490
                    mt(if (row.kind == MockPickRow.Kind.pick) "검토 중 · 주문 전" else "체결 · 지금 ${row.qty}주", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockViews.swift:1491
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1492
                        mt("주수", style = appFont(15f))   // ios: Mock/MockViews.swift:1493
                        Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1494
                        MockNumField(text = qty, onChange = { qty = it }, width = 90.dp)   // ios: Mock/MockViews.swift:1495
                        mt("주", style = appFont(15f))   // ios: Mock/MockViews.swift:1496
                    }
                    planned?.let { (side, n) -> MockConfirmCard(code = row.code, side = side, qty = n) }   // ios: Mock/MockViews.swift:1498
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:1499
                        Box(Modifier.weight(1f)) { actionButton(if (row.kind == MockPickRow.Kind.pick) "고치기" else "주수 맞추기", color = Theme.teal, enabled = (qty.toIntOrNull() ?: 0) >= 1) { setQty() } }   // ios: Mock/MockViews.swift:1500
                        Box(Modifier.weight(1f)) { actionButton("빼기", color = Theme.up, enabled = true) { drop() } }   // ios: Mock/MockViews.swift:1501
                    }
                }
                MockPickRow.Kind.wait -> {   // ios: Mock/MockViews.swift:1503
                    mt("${if (row.side == MockSide.sell) "팔기" else "사기"} ${row.qty}주 대기", style = appFont(13f), color = Theme.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1505
                        mt("주수", style = appFont(15f))   // ios: Mock/MockViews.swift:1506
                        Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1507
                        MockNumField(text = qty, onChange = { qty = it }, width = 90.dp)   // ios: Mock/MockViews.swift:1508
                        mt("주", style = appFont(15f))   // ios: Mock/MockViews.swift:1509
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1511
                        mt("지정가", style = appFont(15f))   // ios: Mock/MockViews.swift:1512
                        Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1513
                        MockNumField(text = limit, onChange = { limit = it }, width = 110.dp)   // ios: Mock/MockViews.swift:1514
                        mt("원", style = appFont(15f))   // ios: Mock/MockViews.swift:1515
                    }
                    val n = qty.toIntOrNull(); val lim = MockNum.parse(limit)
                    if (n != null && n >= 1 && lim != null && lim > 0) {   // ios: Mock/MockViews.swift:1517
                        MockConfirmCard(code = row.code, side = row.side, qty = n, limit = lim)   // ios: Mock/MockViews.swift:1518
                    }
                    actionButton("정정", color = Theme.teal, enabled = (MockNum.parse(limit) ?: 0.0) > 0 && (qty.toIntOrNull() ?: 0) >= 1) { relimit() }   // ios: Mock/MockViews.swift:1520
                    actionButton("취소", color = Theme.up, enabled = true) {   // ios: Mock/MockViews.swift:1521
                        row.orderId?.let { id -> d.cancel(d.shown, orderId = id) }   // ios: Mock/MockViews.swift:1522
                        dismiss()   // ios: Mock/MockViews.swift:1523
                    }
                }
                MockPickRow.Kind.dropped ->   // ios: Mock/MockViews.swift:1525
                    mt("검토에서 뺌", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockViews.swift:1526
            }
            note?.let { mt(it, style = appFont(12f), color = Theme.up) }   // ios: Mock/MockViews.swift:1528
        }
        Box(Modifier.padding(horizontal = 16.dp)) { MockTradeLog(code = row.code) }   // ios: Mock/MockViews.swift:1531
    }
}

/** 주문 직전 확인: 지금 호가로 다 채우면 나갈(들어올) 돈 · 수수료·세금 · 예상 평균 · 미끄러짐 · 한 번 주문 상한 남은 돈 */
@Composable
fun MockConfirmCard(code: String, side: MockSide, qty: Int, limit: Double? = null) {   // ios: Mock/MockViews.swift:1590,1591,1592,1593,1594
    var book by remember { mutableStateOf<MockCalc.Book?>(null) }   // ios: Mock/MockViews.swift:1595
    var fail by remember { mutableStateOf<String?>(null) }   // ios: Mock/MockViews.swift:1596
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:1599,1598

    @Composable
    fun kv(k: String, v: String, c: Color = Theme.ink) {   // ios: Mock/MockViews.swift:1630,1631
        Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1632
            mt(k, style = appFont(12f), color = Theme.sub)   // ios: Mock/MockViews.swift:1632
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))   // ios: Mock/MockViews.swift:1633
            FitText(v, style = appFont(12f, FontWeight.SemiBold).tnum(), minScale = 0.7f, color = c, maxLines = 1)   // ios: Mock/MockViews.swift:1634
        }
    }

    Column(Modifier.fillMaxWidth().background(Theme.line.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {   // ios: Mock/MockViews.swift:1600,1623,1624
        val bk = book
        if (bk != null) {   // ios: Mock/MockViews.swift:1601
            val p = MockCalc.preview(side = side, type = if (limit == null) MockOrderType.market else MockOrderType.limit, qty = qty, limit = limit?.let { MockCalc.snap(it, side = side) }, book = bk, fees = d.s.fees)   // ios: Mock/MockViews.swift:1602
            if (p.qty == 0) {   // ios: Mock/MockViews.swift:1603
                kv("지금 채움", "0주 · 대기")   // ios: Mock/MockViews.swift:1604
            } else {   // ios: Mock/MockViews.swift:1605
                kv(if (side == MockSide.buy) "나갈 돈" else "들어올 돈", MockFmt.won(if (side == MockSide.buy) p.total else p.amount - p.fee - p.tax))   // ios: Mock/MockViews.swift:1606
                kv("수수료 · 세금", MockFmt.won(p.fee) + " · " + MockFmt.won(p.tax))   // ios: Mock/MockViews.swift:1607
                kv("예상 평균", MockFmt.won(p.avg) + " · " + MockFmt.pct(p.slip), if (p.slip > 0.003) Theme.up else Theme.ink)   // ios: Mock/MockViews.swift:1608
                if (p.qty < qty) kv("지금 채움", "${p.qty} / ${qty}주")   // ios: Mock/MockViews.swift:1609
                if (side == MockSide.buy) {   // ios: Mock/MockViews.swift:1610
                    val room = d.guards.orderCap - p.total   // ios: Mock/MockViews.swift:1611
                    kv("앱 상한 남음", if (room >= 0) MockFmt.won(room) else "넘음", if (room >= 0) Theme.ink else Theme.up)   // ios: Mock/MockViews.swift:1612
                }
                val r = d.ready
                if (d.shown == MockBook.real && r != null) {   // ios: Mock/MockViews.swift:1614
                    val room = r.maxKrw - p.amount   // ios: Mock/MockViews.swift:1615
                    kv("서버 상한 남음", if (room >= 0) MockFmt.won(room) else "넘음", if (room >= 0) Theme.ink else Theme.up)   // ios: Mock/MockViews.swift:1616
                }
            }
        } else {   // ios: Mock/MockViews.swift:1619
            mt(fail ?: "호가 받는 중", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockViews.swift:1620
        }
    }
    LaunchedEffect(code) {   // ios: Mock/MockViews.swift:1625
        try { book = d.broker(d.shown).book(code) } catch (e: Exception) { fail = (e as? MockFeed.Fail)?.why ?: "호가 못 받음" }   // ios: Mock/MockViews.swift:1626
    }
}

/** 국내 종목 찾기 (KRX 전체 목록) */
@Composable
fun MockStockFinder(title: String, pick: (MockStock) -> Unit, dismiss: () -> Unit) {   // ios: Mock/MockViews.swift:1640,1641,1642,1643
    var q by remember { mutableStateOf("") }   // ios: Mock/MockViews.swift:1644,1645
    val key = com.naeilo.app.net.StockSearch.norm(q)   // ios: Mock/MockViews.swift:1646
    val rows = if (key.isEmpty()) emptyList() else com.naeilo.app.net.KrxList.all.asSequence().filter { it.key.contains(key) }.take(40).toList()   // ios: Mock/MockViews.swift:1647
    Column(Modifier.fillMaxWidth().background(Theme.bg)) {   // ios: Mock/MockViews.swift:1648
        com.naeilo.app.ui.SheetTitleBar(title = title) { dismiss() }   // ios: Mock/MockViews.swift:1664,1665
        com.naeilo.app.ui.InputField(value = q, onChange = { q = it }, placeholder = "이름이나 코드", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))   // ios: Mock/MockViews.swift:1663
        Column(Modifier.verticalScroll(rememberScrollState())) {   // ios: Mock/MockViews.swift:1649
            rows.forEach { r ->   // ios: Mock/MockViews.swift:1650
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {   // ios: Mock/MockViews.swift:1651
                    pick(MockStock(code = r.hit.id, name = r.hit.name, kospi = r.hit.market == "코스피"))   // ios: Mock/MockViews.swift:1652
                    dismiss()   // ios: Mock/MockViews.swift:1653,1654
                }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1655
                    mt(r.hit.name, color = Theme.ink)   // ios: Mock/MockViews.swift:1656
                    Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1657
                    mt(r.hit.id + " · " + r.hit.market, style = appFont(12f), color = Theme.muted)   // ios: Mock/MockViews.swift:1658
                }
                com.naeilo.app.ui.HDivider(Theme.line)
            }
        }
    }
}


// MARK: 종목 상세

/** 상세를 열 종목 */
data class MockStockRef(val code: String, val name: String) {   // ios: Mock/MockViews.swift:1675,1676,1677,1674
    val id: String get() = code   // ios: Mock/MockViews.swift:1677
}

/** 종목 상세 (종목 탭 상세와 같은 모양): 지금 가격 · 기간 차트 · 산 값. 1일은 마지막 거래일 1분봉, 나머지는 일봉 */
@Composable
fun MockStockSheet(code: String, name: String, seen: Boolean = false, fills: Boolean = false, dismiss: () -> Unit) {   // ios: Mock/MockViews.swift:1681,1682,1683,1684,1685,1686
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:1689,1688
    val p = d.ledger(d.shown).positions[code]   // ios: Mock/MockViews.swift:1690
    val now = d.s.prices[code] ?: p?.avg ?: 0.0   // ios: Mock/MockViews.swift:1691,1692
    Column(Modifier.fillMaxWidth().background(Theme.bg).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Mock/MockViews.swift:1693,1694,1734,1736,1695
        MockSheetBar(title = name) { dismiss() }   // ios: Mock/MockViews.swift:1694
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Mock/MockViews.swift:1696,1732
            Card {   // ios: Mock/MockViews.swift:1696
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1697
                    FitText(MockFmt.won(now), style = appFont(30f, FontWeight.ExtraBold).tnum().copy(letterSpacing = (-0.5).sp), minScale = 0.6f, maxLines = 1, modifier = Modifier.weight(1f))   // ios: Mock/MockViews.swift:1698
                    Spacer(Modifier.width(4.dp))   // ios: Mock/MockViews.swift:1699
                    mt(code, Modifier.background(Theme.line, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp), style = appFont(12f, FontWeight.Bold), color = Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:1700,1701
                }
            }
            MockStockChart(code = code)   // ios: Mock/MockViews.swift:1704
            if (p != null) {   // ios: Mock/MockViews.swift:1705
                val r = if (p.avg > 0) now / p.avg - 1 else 0.0   // ios: Mock/MockViews.swift:1706
                Card(padding = 0.dp) {   // ios: Mock/MockViews.swift:1707
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1708,1715
                        MockStockCell("산 값", MockFmt.won(p.avg))   // ios: Mock/MockViews.swift:1709
                        MockVLine()   // ios: Mock/MockViews.swift:1710
                        MockStockCell("주수", "${p.qty}주")   // ios: Mock/MockViews.swift:1711
                        MockVLine()   // ios: Mock/MockViews.swift:1712
                        MockStockCell("수익률", MockFmt.pct(r), if (r > 0) Theme.up else if (r < 0) Theme.down else Theme.sub)   // ios: Mock/MockViews.swift:1713
                    }
                    com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1716
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1717,1725
                        val at = MockCalc.afterTax(p, price = now, fees = d.s.fees)   // ios: Mock/MockViews.swift:1718
                        MockStockCell("세후 손익", MockFmt.signedWon(at), Theme.change(at))   // ios: Mock/MockViews.swift:1719
                        MockVLine()   // ios: Mock/MockViews.swift:1720
                        MockStockCell("손절까지", d.s.sell.stopLoss?.let { MockFmt.pct(MockCalc.stopRoom(p, price = now, stop = it), digits = 1) } ?: "—")   // ios: Mock/MockViews.swift:1721
                        MockVLine()   // ios: Mock/MockViews.swift:1722
                        MockStockCell("유의", d.warnText(code) ?: "—")   // ios: Mock/MockViews.swift:1723
                    }
                }
            }
            MockBookCard(code = code)   // ios: Mock/MockViews.swift:1728
            if (seen) MockSeenLog(code = code)   // ios: Mock/MockViews.swift:1729
            if (fills) MockFillLog(code = code)   // ios: Mock/MockViews.swift:1730
        }
    }
}

@Composable
private fun MockVLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.line))

/** 한 줄 세 칸 중 하나: 위 이름(작게) · 아래 값 */
@Composable
private fun RowScope.MockStockCell(k: String, v: String, c: Color = Theme.ink) {   // ios: Mock/MockViews.swift:1740
    Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {   // ios: Mock/MockViews.swift:1741,1745,1746
        mt(k, style = appFont(12f), color = Theme.sub)   // ios: Mock/MockViews.swift:1742
        FitText(v, style = appFont(14f, FontWeight.SemiBold).tnum(), minScale = 0.7f, maxLines = 1, color = c)   // ios: Mock/MockViews.swift:1743
    }
}

/** 기간 차트 카드. 열 때 한 번 받는다 (1분봉은 서버가 1분, 일봉은 앱이 그날 동안 다시 쓴다) */
@Composable
fun MockStockChart(code: String, height: androidx.compose.ui.unit.Dp = 140.dp) {   // ios: Mock/MockViews.swift:1751,1752,1753
    var span by remember { mutableStateOf(MockStockChart.Span.d1) }   // ios: Mock/MockViews.swift:1754
    var day by remember { mutableStateOf("") }   // ios: Mock/MockViews.swift:1755
    var mins by remember { mutableStateOf<List<MockBar>>(emptyList()) }   // ios: Mock/MockViews.swift:1756
    var days by remember { mutableStateOf<List<MockBar>>(emptyList()) }   // ios: Mock/MockViews.swift:1757
    var fail by remember { mutableStateOf<String?>(null) }   // ios: Mock/MockViews.swift:1758
    var loaded by remember { mutableStateOf(false) }   // ios: Mock/MockViews.swift:1759
    var chainOf by remember { mutableStateOf<MockEvent?>(null) }   // ios: Mock/MockViews.swift:1760

    val b = if (span == MockStockChart.Span.d1) mins else days.takeLast(span.n + 1)   // ios: Mock/MockViews.swift:1772,1775,1774
    val pts = b.map { it.c }   // ios: Mock/MockViews.swift:1775

    /** 내 체결을 차트 가로 자리에 (1일 = 그날 분, 기간 = 그 날짜 봉) */
    fun trades(b: List<MockBar>): List<Pair<Double, MockEvent>> {   // ios: Mock/MockViews.swift:1800
        if (b.size <= 1) return emptyList()   // ios: Mock/MockViews.swift:1801
        val fs = MockDesk.shared.events.filter { it.code == code && it.book == MockDesk.shared.shown && it.kind == MockEvent.Kind.fill && !it.test && it.price != null }   // ios: Mock/MockViews.swift:1802
        val n = (b.size - 1).toDouble()   // ios: Mock/MockViews.swift:1803
        return fs.mapNotNull { e ->   // ios: Mock/MockViews.swift:1807,1804
            val i: Int?   // ios: Mock/MockViews.swift:1805
            if (span == MockStockChart.Span.d1) {   // ios: Mock/MockViews.swift:1806
                if (MockFmt.day(e.at) != day) return@mapNotNull null   // ios: Mock/MockViews.swift:1811,1810
                val t = MockFmt.time(e.at)   // ios: Mock/MockViews.swift:1808
                i = b.indexOfFirst { it.d >= t }.takeIf { it >= 0 } ?: (b.size - 1)   // ios: Mock/MockViews.swift:1809
            } else {   // ios: Mock/MockViews.swift:1813
                i = b.indexOfFirst { it.d == MockFmt.day(e.at) }.takeIf { it >= 0 }   // ios: Mock/MockViews.swift:1811
            }
            i?.let { Pair(it.toDouble() / n, e) }   // ios: Mock/MockViews.swift:1817,1818
        }
    }

    fun label(x: MockBar): String = if (span == MockStockChart.Span.d1) x.d else MockFmt.md(x.d)   // ios: Mock/MockViews.swift:1829

    fun date(x: MockBar): java.time.Instant {   // ios: Mock/MockViews.swift:1831
        val f = java.time.format.DateTimeFormatter.ofPattern(if (span == MockStockChart.Span.d1) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd", java.util.Locale.US)   // ios: Mock/MockViews.swift:1833
        return try {   // ios: Mock/MockViews.swift:1838
            if (span == MockStockChart.Span.d1) java.time.LocalDateTime.parse(day + " " + x.d, f).atZone(MockFmt.kst).toInstant()   // ios: Mock/MockViews.swift:1834
            else java.time.LocalDate.parse(x.d, f).atStartOfDay(MockFmt.kst).toInstant()
        } catch (e: Exception) { java.time.Instant.now() }
    }

    Card {   // ios: Mock/MockViews.swift:1776
        if (!loaded) {   // ios: Mock/MockViews.swift:1777
            Box(Modifier.fillMaxWidth().heightIn(min = height), contentAlignment = Alignment.Center) { Spinner() }   // ios: Mock/MockViews.swift:1778
        } else if (pts.size < 2) {   // ios: Mock/MockViews.swift:1779
            Box(Modifier.fillMaxWidth().heightIn(min = height), contentAlignment = Alignment.Center) {   // ios: Mock/MockViews.swift:1781,1782
                mt(fail ?: "봉이 아직 없어요", style = appFont(14f, FontWeight.Bold), color = Theme.sub)   // ios: Mock/MockViews.swift:1780
            }
        } else {   // ios: Mock/MockViews.swift:1783
            val S = pts.first(); val E = pts.last(); val up = E >= S   // ios: Mock/MockViews.swift:1783
            val tr = trades(b)
            com.naeilo.app.ui.hold.TagChart(points = pts, height = height, tags = listOf(   // ios: Mock/MockViews.swift:1784
                com.naeilo.app.ui.hold.TagChart.Tag(at = E, text = MockFmt.won(E), bg = Theme.ink, fg = Theme.card),   // ios: Mock/MockViews.swift:1785
                com.naeilo.app.ui.hold.TagChart.Tag(at = E, dy = 20.dp, text = (if (up) "+" else "−") + MockFmt.won(abs(E - S)), bg = if (up) com.naeilo.app.ui.hex(0xFF8A80) else com.naeilo.app.ui.hex(0x8CC4F2)),   // ios: Mock/MockViews.swift:1786
                com.naeilo.app.ui.hold.TagChart.Tag(at = S, text = MockFmt.won(S), bg = com.naeilo.app.ui.hold.TagChart.gray),   // ios: Mock/MockViews.swift:1787
            ), left = label(b.first()), right = label(b.last()),   // ios: Mock/MockViews.swift:1788
                scrub = com.naeilo.app.ui.Scrub(date = { x -> date(b[min(b.size - 1, max(0, (x * (b.size - 1).toDouble()).swRound().toInt()))]) }, value = { MockFmt.won(it) }),   // ios: Mock/MockViews.swift:1789
                marks = tr.map { com.naeilo.app.ui.hold.TagChart.Mark(x = it.first, v = it.second.price ?: 0.0, color = if (it.second.side == MockSide.sell) Theme.down else Theme.up) },   // ios: Mock/MockViews.swift:1790
                onMark = { i -> chainOf = tr[i].second })   // ios: Mock/MockViews.swift:1791
        }
        com.naeilo.app.ui.ChipRow(items = MockStockChart.Span.entries.map { it to it.label }, selection = span, onSelect = { span = it }, fill = true, pad = 4.dp)   // ios: Mock/MockViews.swift:1793
    }
    LaunchedEffect(code) {   // ios: Mock/MockViews.swift:1795
        try {   // ios: Mock/MockViews.swift:1819
            val r = MockFeed.detail(code)   // ios: Mock/MockViews.swift:1819
            day = r.day; mins = r.minutes; days = r.daily   // ios: Mock/MockViews.swift:1820
            // 1분봉이 비면(장 전 등) 1주로 연다
            if (mins.size < 2 && span == MockStockChart.Span.d1) span = MockStockChart.Span.w1   // ios: Mock/MockViews.swift:1822
        } catch (e: Exception) {   // ios: Mock/MockViews.swift:1823
            fail = (e as? MockFeed.Fail)?.why ?: "봉을 받지 못했어요"   // ios: Mock/MockViews.swift:1824
        }
        loaded = true   // ios: Mock/MockViews.swift:1826
    }
    com.naeilo.app.ui.Sheet(show = chainOf != null, onDismiss = { chainOf = null }, medium = true) {   // ios: Mock/MockViews.swift:1796
        chainOf?.let { MockChainSheet(fill = it) { chainOf = null } }
    }
}

object MockStockChart {
    enum class Span {   // ios: Mock/MockViews.swift:1762
        d1, w1, m1, m3, m6;   // ios: Mock/MockViews.swift:1763
        val label: String get() = when (this) {   // ios: Mock/MockViews.swift:1764
            d1 -> "1일"; w1 -> "1주"; m1 -> "1개월"; m3 -> "3개월"; m6 -> "6개월"   // ios: Mock/MockViews.swift:1765
        }
        val n: Int get() = when (this) {   // ios: Mock/MockViews.swift:1767
            d1 -> 0; w1 -> 5; m1 -> 21; m3 -> 63; m6 -> 126   // ios: Mock/MockViews.swift:1768
        }
    }
}

/** 체결 하나의 이유 사슬: 관찰 → 선정 → 주문 → 체결 (오래된 단계부터) */
@Composable
fun MockChainSheet(fill: MockEvent, dismiss: () -> Unit) {   // ios: Mock/MockViews.swift:1838,1839,1840,1841
    val xs = MockCalc.chain(fill, MockDesk.shared.events)   // ios: Mock/MockViews.swift:1842
    Column(Modifier.fillMaxWidth().background(Theme.bg), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Mock/MockViews.swift:1841,1863,1843
        MockSheetBar(title = (fill.name ?: "") + " · 이유") { dismiss() }   // ios: Mock/MockViews.swift:1844
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Mock/MockViews.swift:1844,1845,1860,1846
            xs.forEach { e ->   // ios: Mock/MockViews.swift:1847
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockViews.swift:1848
                    mt(MockFmt.stamp(e.at), Modifier.alignByBaseline(), style = appFont(11f).tnum(), color = Theme.muted, maxLines = 1)   // ios: Mock/MockViews.swift:1849
                    mt(e.kind.label, Modifier.alignByBaseline(), style = appFont(12f, FontWeight.Bold), maxLines = 1)   // ios: Mock/MockViews.swift:1850
                    mt(e.text, Modifier.alignByBaseline().weight(1f), style = appFont(12f), color = Theme.sub)   // ios: Mock/MockViews.swift:1851
                }
            }
            val x = fill.expect; val p = fill.price
            if (x != null && p != null) {   // ios: Mock/MockViews.swift:1854
                mt("예상 " + MockFmt.won(x) + " · 실제 " + MockFmt.won(p) + " · " + MockFmt.pct(MockCalc.slipGap(side = fill.side ?: MockSide.buy, expect = x, actual = p)),   // ios: Mock/MockViews.swift:1855
                    style = appFont(12f, FontWeight.SemiBold).tnum())   // ios: Mock/MockViews.swift:1856
            }
            fill.tossId?.let { t -> androidx.compose.foundation.text.selection.SelectionContainer { mt("토스 " + t, style = appFont(11f), color = Theme.muted) } }   // ios: Mock/MockViews.swift:1858
        }
    }
}

/** 종목 상세 호가: 매도·매수 5칸씩, 내 대기 주문이 걸린 가격 줄 표시. 상한가·하한가 (VI 기준가·대기 순번은 토스 API 에 없음) */
@Composable
fun MockBookCard(code: String) {   // ios: Mock/MockViews.swift:1868,1869
    var book by remember { mutableStateOf<MockCalc.Book?>(null) }   // ios: Mock/MockViews.swift:1870
    var lim by remember { mutableStateOf<Pair<Double, Double>?>(null) }   // ios: Mock/MockViews.swift:1871
    var fail by remember { mutableStateOf<String?>(null) }   // ios: Mock/MockViews.swift:1872
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:1875,1874
    val mine = LinkedHashMap<Double, MockOrder>()   // ios: Mock/MockViews.swift:1876
    d.ledger(d.shown).pending.filter { it.code == code && it.limit != null }.forEach { if (!mine.containsKey(it.limit!!)) mine[it.limit!!] = it }   // ios: Mock/MockViews.swift:1876
    Card {   // ios: Mock/MockViews.swift:1877
        Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1878
            mt("호가", style = appFont(15f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1879
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1880
            lim?.let { l ->   // ios: Mock/MockViews.swift:1881
                mt("상한 " + MockFmt.won(l.first) + " · 하한 " + MockFmt.won(l.second), style = appFont(11f).tnum(), color = Theme.muted)   // ios: Mock/MockViews.swift:1882
            }
        }
        val bk = book
        if (bk != null) {   // ios: Mock/MockViews.swift:1885
            val asks = bk.asks.take(5).reversed(); val bids = bk.bids.take(5)   // ios: Mock/MockViews.swift:1886
            val mx = (asks + bids).map { it.v }.maxOrNull() ?: 1.0   // ios: Mock/MockViews.swift:1887
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Mock/MockViews.swift:1888
                asks.forEach { MockBookLevel(it, mx, Theme.down, mine[it.p]) }   // ios: Mock/MockViews.swift:1889
                bids.forEach { MockBookLevel(it, mx, Theme.up, mine[it.p]) }   // ios: Mock/MockViews.swift:1890
            }
            val off = mine.values.filter { o -> !(asks + bids).any { it.p == o.limit } }   // ios: Mock/MockViews.swift:1892
            off.forEach { o -> mt("내 주문 " + MockFmt.won(o.limit ?: 0.0) + " · 5칸 밖", style = appFont(11f), color = Theme.muted) }   // ios: Mock/MockViews.swift:1893
        } else {   // ios: Mock/MockViews.swift:1894
            mt(fail ?: "받는 중", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockViews.swift:1895
        }
    }
    LaunchedEffect(code) {   // ios: Mock/MockViews.swift:1898
        try { book = d.broker(d.shown).book(code) } catch (e: Exception) { fail = (e as? MockFeed.Fail)?.why ?: "호가 못 받음" }   // ios: Mock/MockViews.swift:1899
        lim = try { MockFeed.limits(code) } catch (e: Exception) { null }   // ios: Mock/MockViews.swift:1900
    }
}

@Composable
private fun MockBookLevel(l: MockCalc.Level, mx: Double, c: Color, o: MockOrder?) {   // ios: Mock/MockViews.swift:1904
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:1905,1914
        mt(MockFmt.won(l.p), Modifier.width(92.dp), style = appFont(12f, FontWeight.SemiBold).tnum(), color = c, maxLines = 1)   // ios: Mock/MockViews.swift:1906
        Box(Modifier.weight(1f).height(8.dp)) {   // ios: Mock/MockViews.swift:1907,1910
            Box(Modifier.fillMaxWidth(min(1.0, l.v / max(1.0, mx)).toFloat()).fillMaxHeight().background(c.copy(alpha = 0.18f), RoundedCornerShape(50)))   // ios: Mock/MockViews.swift:1908
        }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {   // ios: Mock/MockViews.swift:1911
            mt(l.v.toLong().fmtInt(), style = appFont(11f).tnum(), color = Theme.sub, maxLines = 1)   // ios: Mock/MockViews.swift:1911
        }
        if (o != null) MockTag(text = "내 ${o.qty}", color = Theme.teal)   // ios: Mock/MockViews.swift:1912
    }
}


// MARK: 종목별 기록

/** 관찰 기록 (관찰 탭 종목 상세): 걸린 때 · 이유 · 가격 · 등락 (최근부터) */
@Composable
fun MockSeenLog(code: String) {   // ios: Mock/MockViews.swift:1921,1922,1923
    val xs = MockDesk.shared.seen(code).reversed()   // ios: Mock/MockViews.swift:1924
    val today = MockFmt.day(mockNow())   // ios: Mock/MockViews.swift:1925
    val n = MockDesk.slots(xs, day = today)   // ios: Mock/MockViews.swift:1926
    val days = xs.groupBy { MockFmt.day(it.at) }   // ios: Mock/MockViews.swift:1927
    val keys = days.keys.sortedDescending()   // ios: Mock/MockViews.swift:1928
    Card {   // ios: Mock/MockViews.swift:1929
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Mock/MockViews.swift:1930
            mt("관찰 기록", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1931
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:1932
            mt("오늘 ${n}번 · 7일 ${xs.size}번", style = appFont(12f), color = if (n >= 2) Theme.teal else Theme.muted)   // ios: Mock/MockViews.swift:1933
            if (n > 0) {   // ios: Mock/MockViews.swift:1934
                mt("오늘 지우기", Modifier.clickable { MockDesk.shared.clearSeenToday(code) }, style = appFont(12f, FontWeight.SemiBold), color = Theme.up)   // ios: Mock/MockViews.swift:1935,1936,1938
            }
        }
        if (xs.isEmpty()) mt("아직 관찰에 걸린 기록이 없어요", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockViews.swift:1941
        // 날짜 한 줄, 그 아래 같은 날 걸린 때 (조건 글은 뺀다: 시각 · 가격 · 등락)
        keys.forEach { k ->   // ios: Mock/MockViews.swift:1943
            if (k != keys.first()) com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:1944
            mt(if (k == today) "오늘 · " + MockFmt.md(k) else MockFmt.md(k), style = appFont(13f, FontWeight.Bold))   // ios: Mock/MockViews.swift:1945
            (days[k] ?: emptyList()).forEach { x ->   // ios: Mock/MockViews.swift:1946
                androidx.compose.runtime.key(x.at) {
                    MockSwipe("지우기", { MockDesk.shared.deleteSeen(code, at = x.at) }, spacing = 8.dp) {   // ios: Mock/MockViews.swift:1947,1961
                        mt(MockFmt.time(x.at), Modifier.alignByBaseline(), style = appFont(12f).tnum(), color = Theme.muted)   // ios: Mock/MockViews.swift:1948
                        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))   // ios: Mock/MockViews.swift:1949
                        x.vol?.let { v ->   // ios: Mock/MockViews.swift:1950
                            mt(cformat("%.1f배", v) + (x.volNew?.let { cformat(" · 새 %.1f배", it) } ?: ""),   // ios: Mock/MockViews.swift:1951
                                Modifier.alignByBaseline(), style = appFont(11f).tnum(), color = Theme.muted)   // ios: Mock/MockViews.swift:1952
                        }
                        mt(MockFmt.won(x.price), Modifier.alignByBaseline(), style = appFont(12f, FontWeight.SemiBold).tnum())   // ios: Mock/MockViews.swift:1954
                        x.change?.let { c ->   // ios: Mock/MockViews.swift:1955
                            Box(Modifier.alignByBaseline().widthIn(min = 54.dp), contentAlignment = Alignment.CenterEnd) {   // ios: Mock/MockViews.swift:1958
                                mt(MockFmt.pct(c, digits = 1), style = appFont(12f, FontWeight.Bold).tnum(),   // ios: Mock/MockViews.swift:1956
                                    color = if (c > 0) Theme.up else if (c < 0) Theme.down else Theme.sub)   // ios: Mock/MockViews.swift:1957
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 줄을 왼쪽으로 밀면 오른쪽에 빨간 단추 (지우기 · 숨기기). 세로 스크롤과 함께 받는다 */
@Composable
fun MockSwipe(title: String, action: () -> Unit, spacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable RowScope.() -> Unit) {   // ios: Mock/MockViews.swift:1969,1970,1971,1975,1976,2008,2009
    val dx = remember { androidx.compose.animation.core.Animatable(0f) }   // ios: Mock/MockViews.swift:1972
    var base by remember { mutableStateOf(0f) }   // ios: Mock/MockViews.swift:1973
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { 76.dp.toPx() }   // ios: Mock/MockViews.swift:1974
    val snappy = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 400f)
    Box(Modifier.fillMaxWidth().clipToBounds()) {   // ios: Mock/MockViews.swift:1977,1978,1993,1994
        if (dx.value < 0) {   // ios: Mock/MockViews.swift:1981
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {   // ios: Mock/MockViews.swift:1980
                Box(Modifier.width(with(density) { max(w, -dx.value).toDp() }).fillMaxHeight().background(Theme.up)   // ios: Mock/MockViews.swift:1985,1987,1988,1990
                    .clickable {   // ios: Mock/MockViews.swift:1982
                        scope.launch { dx.animateTo(0f, snappy) }; base = 0f   // ios: Mock/MockViews.swift:1983
                        action()   // ios: Mock/MockViews.swift:1984
                    }, contentAlignment = Alignment.Center) {
                    mt(title, style = appFont(13f, FontWeight.Bold), color = Color.White)   // ios: Mock/MockViews.swift:1986
                }
            }
        }
        Row(Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(dx.value.roundToInt(), 0) }   // ios: Mock/MockViews.swift:1979
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {   // ios: Mock/MockViews.swift:2000
                        val t = if (dx.value < -w / 2) -w else 0f   // ios: Mock/MockViews.swift:2001
                        scope.launch { dx.animateTo(t, snappy) }
                        base = if (dx.value < -w / 2) -w else 0f   // ios: Mock/MockViews.swift:2002
                    },
                ) { _, amount ->   // ios: Mock/MockViews.swift:1996
                    drag += amount
                    scope.launch { dx.snapTo(min(0f, max(-w * 1.6f, base + drag))) }   // ios: Mock/MockViews.swift:1998
                }
            }, horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}


/** 선정·매도 흐름 한 줄: 선정만 · 선정 → 예외 · 선정 → 주문 · 선정 → 주문 → 체결 (매도는 매도 신호부터) */
data class MockFlow(   // ios: Mock/MockViews.swift:2013,2014
    var id: String,                  // 흐름 첫 기록 (숨길 때 이 id)   // ios: Mock/MockViews.swift:2009
    var ids: MutableList<String> = mutableListOf(),          // 같은 날 같은 결과로 합친 흐름들   // ios: Mock/MockViews.swift:2015
    var at: MockDate,   // ios: Mock/MockViews.swift:2016
    var sell: Boolean = false,   // ios: Mock/MockViews.swift:2017
    var who: MockWho = MockWho.rule,   // ios: Mock/MockViews.swift:2018
    var test: Boolean = false,   // ios: Mock/MockViews.swift:2019
    var steps: MutableList<String>,           // "선정" · "주문" · "체결" …   // ios: Mock/MockViews.swift:2020
    var why: String? = null,        // 예외 이유   // ios: Mock/MockViews.swift:2021
    var qty: Int = 0,                   // 체결 주수   // ios: Mock/MockViews.swift:2022
    var ordered: Int = 0,               // 주문 주수   // ios: Mock/MockViews.swift:2023
    var split: Boolean = false,             // 미끄러짐 한도로 나눔   // ios: Mock/MockViews.swift:2024
    var amount: Double = 0.0,              // 체결 금액   // ios: Mock/MockViews.swift:2025
    var pnl: Double? = null,        // 매도 손익   // ios: Mock/MockViews.swift:2026
    var n: Int = 1,                     // 같은 날 같은 결과 횟수   // ios: Mock/MockViews.swift:2027
) {
    /** 시각을 뺀 줄 글 (같은 결과 합치기 열쇠) */
    val text: String get() {   // ios: Mock/MockViews.swift:2029
        var t = steps.joinToString(" → ")   // ios: Mock/MockViews.swift:2030
        why?.let { t += " (" + it + ")" }   // ios: Mock/MockViews.swift:2031
        if (qty > 0) t += " ${qty}주 · " + MockFmt.won(amount / qty.toDouble())   // ios: Mock/MockViews.swift:2032
        if (split) t += if (qty > 0 && qty < ordered) " (나눔 · 남은 ${ordered - qty}주)" else " (나눔)"   // ios: Mock/MockViews.swift:2033
        return t   // ios: Mock/MockViews.swift:2034
    }
}

/** 종목 하나의 흐름 한 줄씩 (규칙 장부, 옛 장부 포함). 날짜별 최근 날부터, 날 안은 시각 순. 같은 날 같은 결과는 한 줄로 센다.
 *  조건 문구는 다시 보이지 않는다 (예외는 이유만) */
fun MockDesk.flows(code: String): List<Pair<String, List<MockFlow>>> {   // ios: Mock/MockViews.swift:2038,2041
    val es = events.filter { it.code == code && it.book == shown }   // ios: Mock/MockViews.swift:2042
    val flows = ArrayList<MockFlow>(); val head = HashMap<String, Int>(); val orderOf = HashMap<String, Int>()   // ios: Mock/MockViews.swift:2043
    /** 예외 이유를 짧게: "예외 · 직접 · 삼성" → "직접", "폭등 거름 · 거래량 ≤ 20배" → "폭등 거름", "현금 부족 · 남은 …" → "현금 부족" */
    fun reason(e: MockEvent): String {   // ios: Mock/MockViews.swift:2045
        var t = e.text   // ios: Mock/MockViews.swift:2046
        for (p in listOf("주문 직전 예외 · ", "예외 · ")) if (t.startsWith(p)) t = t.drop(p.length)   // ios: Mock/MockViews.swift:2047
        if (e.kind == MockEvent.Kind.error) return "오류 · " + t.split(" · ").first()   // ios: Mock/MockViews.swift:2048
        if (t.startsWith("검토에서 뺌")) return "검토에서 뺌"   // ios: Mock/MockViews.swift:2049
        return t.split(" · ").first()   // ios: Mock/MockViews.swift:2050
    }
    for (e in es) {   // ios: Mock/MockViews.swift:2052
        when {   // ios: Mock/MockViews.swift:2053
            e.kind == MockEvent.Kind.select && e.who == MockWho.rule -> {   // ios: Mock/MockViews.swift:2054
                head[e.id] = flows.size   // ios: Mock/MockViews.swift:2055
                flows.add(MockFlow(id = e.id, at = e.at, test = e.test, steps = mutableListOf("선정")))   // ios: Mock/MockViews.swift:2056
            }
            e.kind == MockEvent.Kind.sell -> {   // ios: Mock/MockViews.swift:2057
                head[e.id] = flows.size   // ios: Mock/MockViews.swift:2058
                flows.add(MockFlow(id = e.id, at = e.at, sell = true, test = e.test, steps = mutableListOf("매도 신호 (" + reason(e) + ")")))   // ios: Mock/MockViews.swift:2059
            }
            e.kind == MockEvent.Kind.order && e.side != null -> {   // ios: Mock/MockViews.swift:2060
                val i = e.ref?.let { head[it] }
                if (i != null) {   // ios: Mock/MockViews.swift:2061
                    flows[i].steps.add("주문"); flows[i].test = flows[i].test || e.test   // ios: Mock/MockViews.swift:2062
                    flows[i].ordered = e.qty ?: 0; flows[i].split = e.text.contains("나눔")   // ios: Mock/MockViews.swift:2063
                    orderOf[e.id] = i   // ios: Mock/MockViews.swift:2064
                } else {   // ios: Mock/MockViews.swift:2065
                    // 손 주문 (앞 단계 없음)
                    orderOf[e.id] = flows.size   // ios: Mock/MockViews.swift:2067
                    flows.add(MockFlow(id = e.id, at = e.at, sell = e.side == MockSide.sell, who = e.who ?: MockWho.hand, test = e.test,   // ios: Mock/MockViews.swift:2068
                        steps = mutableListOf((e.who ?: MockWho.hand).label + " " + (if (e.side == MockSide.sell) "매도" else "매수") + " 주문"),   // ios: Mock/MockViews.swift:2069
                        ordered = e.qty ?: 0, split = e.text.contains("나눔")))   // ios: Mock/MockViews.swift:2070
                }
            }
            e.kind == MockEvent.Kind.fill -> {   // ios: Mock/MockViews.swift:2072
                val i = e.ref?.let { orderOf[it] } ?: continue   // ios: Mock/MockViews.swift:2073
                if (flows[i].qty == 0) flows[i].steps.add("체결")   // ios: Mock/MockViews.swift:2074
                flows[i].qty += e.qty ?: 0; flows[i].amount += e.amount ?: 0.0   // ios: Mock/MockViews.swift:2075
                e.pnl?.let { p -> flows[i].pnl = (flows[i].pnl ?: 0.0) + p }   // ios: Mock/MockViews.swift:2076
                flows[i].test = flows[i].test || e.test   // ios: Mock/MockViews.swift:2077
            }
            e.kind == MockEvent.Kind.cancel -> {   // ios: Mock/MockViews.swift:2078
                val i = e.ref?.let { orderOf[it] }
                if (i != null && !flows[i].steps.contains("취소")) flows[i].steps.add("취소")   // ios: Mock/MockViews.swift:2079
            }
            e.kind == MockEvent.Kind.reject -> {   // ios: Mock/MockViews.swift:2080
                // 거부: 주문층(브로커)·안전장치가 받지 않은 주문
                val i = e.ref?.let { head[it] }
                if (i != null) { flows[i].steps.addAll(listOf("주문", "거부")); flows[i].why = e.text }   // ios: Mock/MockViews.swift:2082
                else flows.add(MockFlow(id = e.id, at = e.at, sell = e.side == MockSide.sell, who = e.who ?: MockWho.hand, test = e.test, steps = mutableListOf("주문", "거부"), why = e.text))   // ios: Mock/MockViews.swift:2083
            }
            e.kind == MockEvent.Kind.select && !e.text.startsWith("검토에서 뺌") ->   // ios: Mock/MockViews.swift:2084
                continue   // 검토에서 주수만 고친 줄   // ios: Mock/MockViews.swift:2085
            e.kind == MockEvent.Kind.exclude || e.kind == MockEvent.Kind.short || e.kind == MockEvent.Kind.order || e.kind == MockEvent.Kind.error || e.kind == MockEvent.Kind.select -> {   // ios: Mock/MockViews.swift:2086
                // 앞 선정이 있으면 그 줄의 예외, 없으면(고르기 전에 거른 종목) 따로 한 줄
                val i = e.ref?.let { head[it] }
                if (i != null) {   // ios: Mock/MockViews.swift:2088
                    if (flows[i].why == null) { flows[i].steps.add("예외"); flows[i].why = reason(e) }   // ios: Mock/MockViews.swift:2089
                } else if (e.kind != MockEvent.Kind.select && e.kind != MockEvent.Kind.order && e.code != null) {   // ios: Mock/MockViews.swift:2090
                    flows.add(MockFlow(id = e.id, at = e.at, test = e.test, steps = mutableListOf("선정", "예외"), why = reason(e)))   // ios: Mock/MockViews.swift:2091
                }
            }
            else -> {}   // ios: Mock/MockViews.swift:2093,2094
        }
    }
    val days = HashMap<String, MutableList<MockFlow>>(); val keys = HashMap<String, HashMap<String, Int>>()   // ios: Mock/MockViews.swift:2097
    val hid = hiddenIds   // ios: Mock/MockViews.swift:2098
    for (f0 in flows) {   // ios: Mock/MockViews.swift:2099
        if (hid.contains(f0.id)) continue
        val f = f0.copy(ids = mutableListOf(f0.id))   // ios: Mock/MockViews.swift:2100
        val day = MockFmt.day(f.at)   // ios: Mock/MockViews.swift:2101
        val key = (if (f.sell) "s" else "b") + (if (f.test) "t" else "") + f.text + (f.pnl?.let { MockFmt.signedWon(it) } ?: "")   // ios: Mock/MockViews.swift:2102
        val i = keys[day]?.get(key)
        if (f.qty == 0 && i != null) { days[day]!![i].n += 1; days[day]!![i].ids.add(f.id); continue }   // ios: Mock/MockViews.swift:2103
        keys.getOrPut(day) { HashMap() }[key] = days.getOrPut(day) { mutableListOf() }.size   // ios: Mock/MockViews.swift:2104
        days.getOrPut(day) { mutableListOf() }.add(f)   // ios: Mock/MockViews.swift:2105
    }
    return days.keys.sortedDescending().take(10).map { it to days[it]!!.toList() }   // ios: Mock/MockViews.swift:2107
}

/** 선정 흐름 기록 (선정 탭 종목 시트). 날짜별로 지난 선정과 견줘 본다 */
@Composable
fun MockTradeLog(code: String) {   // ios: Mock/MockViews.swift:2112,2113,2114
    val days = MockDesk.shared.flows(code)   // ios: Mock/MockViews.swift:2115
    val today = MockFmt.day(mockNow())   // ios: Mock/MockViews.swift:2116
    Card {   // ios: Mock/MockViews.swift:2117
        Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:2118
            mt("선정 기록", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockViews.swift:2119
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:2120
            if (days.size > 1) mt("${days.size}일", style = appFont(12f), color = Theme.muted)   // ios: Mock/MockViews.swift:2121
        }
        if (days.isEmpty()) mt("아직 기록이 없어요", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockViews.swift:2123
        days.forEachIndexed { i, x ->   // ios: Mock/MockViews.swift:2124,2125
            if (i > 0) com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:2126
            mt(if (x.first == today) "오늘" else (if (i == 0) MockFmt.md(x.first) else "지난 선정 · " + MockFmt.md(x.first)),   // ios: Mock/MockViews.swift:2127
                style = appFont(13f, FontWeight.Bold), color = if (i == 0) Theme.ink else Theme.sub)   // ios: Mock/MockViews.swift:2128
            x.second.forEach { f ->   // ios: Mock/MockViews.swift:2129
                androidx.compose.runtime.key(f.id) {
                    MockSwipe("숨기기", { MockDesk.shared.hide(f.ids) }, spacing = 6.dp) {   // ios: Mock/MockViews.swift:2130,2137
                        mt(MockFmt.time(f.at), Modifier.alignByBaseline(), style = appFont(11f).tnum(), color = Theme.muted, maxLines = 1)   // ios: Mock/MockViews.swift:2138,2139,2136
                        if (f.test) Box(Modifier.alignByBaseline()) { MockTag(text = "시험", color = Theme.orange) }   // ios: Mock/MockViews.swift:2139
                        FitText(f.text + (if (f.n > 1) " · ${f.n}번" else ""), style = appFont(12f), minScale = 0.8f, maxLines = 1,   // ios: Mock/MockViews.swift:2140,2142
                            color = if (f.steps.contains("예외")) Theme.sub else Theme.ink, modifier = Modifier.alignByBaseline().weight(1f, fill = false))   // ios: Mock/MockViews.swift:2141
                        Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Mock/MockViews.swift:2142,2143
                        f.pnl?.let { p -> mt(MockFmt.signedWon(p), Modifier.alignByBaseline(), style = appFont(12f, FontWeight.Bold).tnum(), color = Theme.change(p), maxLines = 1) }   // ios: Mock/MockViews.swift:2144
                    }
                }
            }
        }
    }
}

/** 사고 판 기록 (일지 탭 종목 상세): 날짜별 최근 날부터, 날 안은 시각 순. 산 줄 = 주수·평균값·금액, 판 줄 = 주수·평균값·금액·손익 */
@Composable
fun MockFillLog(code: String) {   // ios: Mock/MockViews.swift:2150,2151,2152
    val d = MockDesk.shared   // ios: Mock/MockViews.swift:2153
    val hid = d.hiddenIds   // ios: Mock/MockViews.swift:2154
    val fs = d.events.filter { it.code == code && it.book == d.shown && it.kind == MockEvent.Kind.fill && !hid.contains(it.id) }   // ios: Mock/MockViews.swift:2155
    val days = fs.groupBy { MockFmt.day(it.at) }   // ios: Mock/MockViews.swift:2156
    val keys = days.keys.sortedDescending()   // ios: Mock/MockViews.swift:2157
    val pnl = fs.mapNotNull { it.pnl }.fold(0.0) { a, b -> a + b }   // ios: Mock/MockViews.swift:2158
    Card {   // ios: Mock/MockViews.swift:2159,2160
        Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:2159
            mt("매수 · 매도 기록", style = appFont(16f, FontWeight.Bold))   // ios: Mock/MockViews.swift:2161
            Spacer(Modifier.weight(1f))   // ios: Mock/MockViews.swift:2162
            if (fs.any { it.pnl != null }) {   // ios: Mock/MockViews.swift:2163
                mt("매도 손익 " + MockFmt.signedWon(pnl), style = appFont(12f, FontWeight.Bold).tnum(), color = Theme.change(pnl))   // ios: Mock/MockViews.swift:2164
            }
        }
        if (fs.isEmpty()) mt("아직 매수 · 매도 기록이 없어요", style = appFont(13f), color = Theme.muted)   // ios: Mock/MockViews.swift:2167
        keys.forEachIndexed { i, k ->   // ios: Mock/MockViews.swift:2168
            if (i > 0) com.naeilo.app.ui.HDivider(Theme.line)   // ios: Mock/MockViews.swift:2169
            mt(MockFmt.md(k), style = appFont(13f, FontWeight.Bold))   // ios: Mock/MockViews.swift:2170
            (days[k] ?: emptyList()).forEach { e ->   // ios: Mock/MockViews.swift:2171
                androidx.compose.runtime.key(e.id) {
                    MockSwipe("숨기기", { d.hide(listOf(e.id)) }) { MockFillLine(e) }   // ios: Mock/MockViews.swift:2172
                }
            }
        }
    }
}

@Composable
private fun MockFillLine(e: MockEvent) {   // ios: Mock/MockViews.swift:2178
    val sell = e.side == MockSide.sell   // ios: Mock/MockViews.swift:2179
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Mock/MockViews.swift:2180
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Mock/MockViews.swift:2181
            mt(MockFmt.time(e.at), Modifier.alignByBaseline(), style = appFont(11f).tnum(), color = Theme.muted, maxLines = 1)   // ios: Mock/MockViews.swift:2182
            mt(if (sell) "매도" else "매수", Modifier.alignByBaseline(), style = appFont(12f, FontWeight.Bold), color = if (sell) Theme.down else Theme.up, maxLines = 1)   // ios: Mock/MockViews.swift:2183
            if (e.test) Box(Modifier.alignByBaseline()) { MockTag(text = "시험", color = Theme.orange) }   // ios: Mock/MockViews.swift:2184
            FitText("${e.qty ?: 0}주 · ${MockFmt.won(e.price ?: 0.0)} · ${MockFmt.small(e.amount ?: 0.0)}"   // ios: Mock/MockViews.swift:2185
                + (e.expect?.let { " · 차이 " + MockFmt.pct(MockCalc.slipGap(side = e.side ?: MockSide.buy, expect = it, actual = e.price ?: 0.0)) } ?: ""), style = appFont(12f).tnum(), color = Theme.sub,   // ios: Mock/MockViews.swift:2186
                minScale = 0.8f, maxLines = 1, modifier = Modifier.alignByBaseline().weight(1f, fill = false))   // ios: Mock/MockViews.swift:2187
            Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Mock/MockViews.swift:2187,2188
            e.pnl?.let { p -> mt(MockFmt.signedWon(p), Modifier.alignByBaseline(), style = appFont(12f, FontWeight.Bold).tnum(), color = Theme.change(p), maxLines = 1) }   // ios: Mock/MockViews.swift:2189
        }
        // 판 이유는 자르지 않고 다 보인다 (이유 모르는 매도가 없게)
        MockDesk.shared.sellWhy(e)?.let { w ->   // ios: Mock/MockViews.swift:2192
            mt("판 이유 · " + w, style = appFont(12f), color = Theme.sub)   // ios: Mock/MockViews.swift:2193
        }
    }
}

/** 훑기 전광판: 청록 띠 한 줄 (라이트·다크 모두 테마 색). 값이 바뀐 글자 칸만 위에서 아래로 넘어간다 (묶음마다 몇 초에 한 번) */
@Composable
fun MockFlipBoard(slots: List<Pair<String, String>>, note: String? = null) {   // ios: Mock/MockViews.swift:2201,2202,2204,2205
    val a11y = (slots.map { "${it.first} ${it.second}" } + listOfNotNull(note?.let { "다음 훑기 " + it })).joinToString(", ")   // ios: Mock/MockViews.swift:2231,2232,2233,2234
    Row(Modifier.fillMaxWidth().height(40.dp).background(Theme.tealBg, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp)   // ios: Mock/MockViews.swift:2225,2226
        .clearAndSetSemantics { contentDescription = a11y },   // ios: Mock/MockViews.swift:2227,2228
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:2206
        slots.forEach { s ->   // ios: Mock/MockViews.swift:2207
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Mock/MockViews.swift:2208
                mt(s.first, style = appFont(10f, FontWeight.SemiBold), color = Theme.teal, maxLines = 1)   // ios: Mock/MockViews.swift:2209
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Mock/MockViews.swift:2210
                    val cs = s.second.toList()   // ios: Mock/MockViews.swift:2211
                    // 칸 자리는 오른쪽부터 센다: 9 → 10 처럼 자릿수가 늘어도 끝자리 칸이 그대로 남는다
                    cs.forEachIndexed { i, c ->   // ios: Mock/MockViews.swift:2213
                        androidx.compose.runtime.key(cs.size - i) { MockFlipCell(ch = c) }   // ios: Mock/MockViews.swift:2214
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Mock/MockViews.swift:2219
        if (note != null) {   // ios: Mock/MockViews.swift:2220
            FitText(note, style = appFont(11f), minScale = 0.8f, maxLines = 1, color = Theme.muted, modifier = Modifier.weight(1f, fill = false))   // ios: Mock/MockViews.swift:2221,2222
        }
    }
}

/** 전광판 글자 칸 하나. 글자가 바뀌면 반쯤 접혔다가 새 글자로 펴진다 */
@Composable
fun MockFlipCell(ch: Char) {   // ios: Mock/MockViews.swift:2239,2240
    var shown by remember { mutableStateOf(ch) }   // ios: Mock/MockViews.swift:2241,2253
    val angle = remember { androidx.compose.animation.core.Animatable(0f) }   // ios: Mock/MockViews.swift:2242
    val still = com.naeilo.app.ui.LocalReduceMotion.current   // ios: Mock/MockViews.swift:2243,2244
    val digit = shown.isDigit()   // ios: Mock/MockViews.swift:2245
    val line = Theme.line
    Box(Modifier.size(width = if (digit) 12.dp else 6.dp, height = 24.dp)   // ios: Mock/MockViews.swift:2249
        .graphicsLayer { rotationX = angle.value; cameraDistance = 6f * density }
        .background(if (digit) Theme.card else Color.Transparent, RoundedCornerShape(3.dp))   // ios: Mock/MockViews.swift:2250
        .drawWithContent { drawContent(); if (digit) drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2 - 0.5f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },   // ios: Mock/MockViews.swift:2251
        contentAlignment = Alignment.Center) {
        BasicText(shown.toString(), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Theme.tealDark))   // ios: Mock/MockViews.swift:2246,2247,2248
    }
    LaunchedEffect(ch) {   // ios: Mock/MockViews.swift:2254
        if (ch == shown) return@LaunchedEffect
        if (still) { shown = ch; return@LaunchedEffect }   // ios: Mock/MockViews.swift:2255
        angle.animateTo(-90f, androidx.compose.animation.core.tween(80, easing = androidx.compose.animation.core.EaseIn))   // ios: Mock/MockViews.swift:2256
        shown = ch; angle.snapTo(90f)   // ios: Mock/MockViews.swift:2257
        angle.animateTo(0f, androidx.compose.animation.core.tween(100, easing = androidx.compose.animation.core.EaseOut))   // ios: Mock/MockViews.swift:2258
    }
}

/** 오늘 걸린 횟수 알약 (2번부터). n 이 없으면 캡션용 빈 알약 "n" */
@Composable
fun MockSeenPill(n: Int?) {   // ios: Mock/MockViews.swift:2265,2266,2267
    mt(n?.let { "$it" } ?: "n",   // ios: Mock/MockViews.swift:2268
        Modifier.height(18.dp).background(Theme.tealBg, RoundedCornerShape(50)).padding(horizontal = 6.dp).wrapContentHeight()   // ios: Mock/MockViews.swift:2269,2270
            .semantics { contentDescription = n?.let { "오늘 ${it}번 걸림" } ?: "오늘 걸린 횟수" },   // ios: Mock/MockViews.swift:2271
        style = appFont(11f, FontWeight.Bold).tnum(), color = Theme.teal, maxLines = 1)   // ios: Mock/MockViews.swift:2268
}

/** 거른 이유 점 색 (걸린 종목 줄 · 목록 아래 캡션이 같은 색을 쓴다) */
object MockCutTone {   // ios: Mock/MockViews.swift:2276
    @Composable fun color(why: String): Color = when (why) {   // ios: Mock/MockViews.swift:2277,2278
        "시장 약함" -> Theme.up   // ios: Mock/MockViews.swift:2279
        "재확인 대기" -> Theme.gold   // ios: Mock/MockViews.swift:2280
        "거래대금 바닥" -> Theme.down   // ios: Mock/MockViews.swift:2281
        else -> Theme.sub        // 유의사항 (VI · 투자경고 · 거래정지 …)   // ios: Mock/MockViews.swift:2282
    }
}
