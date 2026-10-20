package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.GoalSim
import com.naeilo.app.core.calc.Parallel
import com.naeilo.app.core.calc.Part
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.etaYears
import com.naeilo.app.core.calc.goalToDate
import com.naeilo.app.core.calc.goalToText
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keyName
import com.naeilo.app.core.calc.mixes
import com.naeilo.app.core.calc.nowMix
import com.naeilo.app.core.calc.pathStartDate
import com.naeilo.app.core.calc.saveBasket
import com.naeilo.app.core.calc.simulate
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.calc.verdict
import com.naeilo.app.core.calc.whenText
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.core.calc.yearAt
import com.naeilo.app.data.Currency
import com.naeilo.app.data.sectorText
import com.naeilo.app.net.ExtraStats
import com.naeilo.app.net.Market
import com.naeilo.app.net.SiteStats
import com.naeilo.app.net.StockHit
import com.naeilo.app.net.StockSearch
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.InputField
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.SF
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Spinner
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.pulse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// 내 길 화면 (Views/AnalysisDetail.swift 197-780). 숫자는 모두 계산값이고 사거나 팔라는 문구는 넣지 않는다.

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

/** SwiftUI .easeInOut 곡선 */
private val easeInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** 두 시각 사이 년 (timeIntervalSince / (365.25 * 86400) 의 앞부분: 초) */
private fun secs(a: java.time.Instant, b: java.time.Instant): Double = (b.toEpochMilli() - a.toEpochMilli()) / 1000.0

/** .position(x:y:): 글자 가운데를 (x, y) 에 둔다 */
private fun Modifier.tickAt(x: Dp, y: Dp): Modifier = this.layout { mm, _ ->
    val pl = mm.measure(Constraints())
    layout(pl.width, pl.height) { pl.place(x.roundToPx() - pl.width / 2, y.roundToPx() - pl.height / 2) }
}

/** 비중 바꿔 비교 화면으로 가는 길 (NavigationLink { MyPathCompareView() }) */
object MyPathCompareRoute {
    const val title = "비중 바꿔 비교"   // ios: Views/AnalysisDetail.swift:742
}

// MARK: 내 길 — 목표 루트는 판정·도달 시점 화면(B안), 회복 루트는 계획선(보라 점선) vs 실제
// ios: Views/AnalysisDetail.swift:199,200,201
@Composable
fun MyPathView(m: AppModel) {
    if (m.isGoal) MyPathGoalView(m) else MyPathClassicView(m)   // ios: Views/AnalysisDetail.swift:202
}

// ios: Views/AnalysisDetail.swift:206,207,211
@Composable
fun MyPathClassicView(m: AppModel) {
    // 고르기 전에는 시작한 지 석 달이 안 됐으면 '시작부터' (목표일까지 보면 실제 선이 점처럼 짧다)
    var picked by remember { mutableStateOf<String?>(null) }   // ios: Views/AnalysisDetail.swift:209
    // 실제 평가액은 지난 시세를 훑는 계산이라 읽은 값이 바뀔 때만 다시 한다
    val p by remember(m) { derivedStateOf { m.myPath } }; val mS = p.start; val mEnd = p.end; val K = p.key; val act = p.act; val need = p.need   // ios: Views/AnalysisDetail.swift:212
    val view = picked ?: (if (mS < 0.25) "past" else "all")   // ios: Views/AnalysisDetail.swift:213
    val gap = if (p.need(mS) > 0) p.act(mS) / p.need(mS) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:214
    val f = m.forecast   // ios: Views/AnalysisDetail.swift:215
    val x1 = if (view == "past") mS + 1.0 / 12 else mEnd   // ios: Views/AnalysisDetail.swift:216
    val actPts = p.pts   // ios: Views/AnalysisDetail.swift:217
    val needPts = (0..60).map { i -> val t = x1 * i / 60; Pair(t, need(t)) }   // ios: Views/AnalysisDetail.swift:218
    // 전망(오늘부터 목표일까지 월 경계마다)을 그래프 끝까지 겹친다
    // 전망 경로는 종목만이라 현금을 더해 내 길(현금 포함)과 같은 기준으로
    // ios: Views/AnalysisDetail.swift:219,222
    val bandPts: List<Triple<Double, Double, Double>> = if (view == "band")
        (0..max(min(1, f.tY.size - 1), f.idx(years = x1 - mS))).map { i -> Triple(mS + f.tY[i], f.q75[i] + m.cashKrw, f.q25[i] + m.cashKrw) } else emptyList()
    val vals = actPts.map { it.second } + needPts.map { it.second } + bandPts.flatMap { listOf(it.second, it.third) }   // ios: Views/AnalysisDetail.swift:223
    val hi = (vals.maxOrNull() ?: 1.0) * 1.03; val lo = (vals.minOrNull() ?: 0.0) * 0.97   // ios: Views/AnalysisDetail.swift:224
    val X = { t: Double -> t / x1 }; val Y = { v: Double -> (hi - v) / max(1.0, hi - lo) }   // ios: Views/AnalysisDetail.swift:225
    val yrsLeft = mEnd - mS   // ios: Views/AnalysisDetail.swift:226
    // 남은 기간: 웹 목표 진행과 같은 표시 (3.0년, 1년 안 되면 개월)
    val leftText = AppModel.leftText(if (m.isGoal) yrs(Day.today, Day.str(m.goalToDate)) else yrsLeft)   // ios: Views/AnalysisDetail.swift:228
    // 지난 달마다: 시작한 날부터 꽉 채운 달만 (최근 6달), 끝은 오늘
    val zone = ZoneId.systemDefault()
    val months = runCatching { ChronoUnit.MONTHS.between(m.pathStartDate.atZone(zone), ZonedDateTime.now(zone)).toInt() }.getOrDefault(0)   // ios: Views/AnalysisDetail.swift:230
    // ios: Views/AnalysisDetail.swift:231
    val monthRows: List<Pair<String, Double>> = (max(1, months - 5)..max(1, months)).mapNotNull { k ->
        // ios: Views/AnalysisDetail.swift:232
        if (k > months) return@mapNotNull null
        val d = m.pathStartDate.atZone(zone).plusMonths(k.toLong()).toInstant()
        Pair("${k}달째", secs(m.pathStartDate, d) / (365.25 * 86400))   // ios: Views/AnalysisDetail.swift:233
    } + listOf(Pair("오늘", mS))   // ios: Views/AnalysisDetail.swift:234
    val now = m.trackValue; val mM = p.monthly   // ios: Views/AnalysisDetail.swift:235
    val needCagr = if (now > 0 && yrsLeft > 0) (max(1.0, K - mM * 12 * yrsLeft) / now).pow(1 / yrsLeft) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:236
    val pPlan = if (m.isGoal) f.pGoal else f.prob(min(3.0, yrsLeft))   // ios: Views/AnalysisDetail.swift:237 목표 루트: 목표일에 목표 이상일 확률 (웹 목표 확률)
    val lead = if (gap >= 0) Theme.teal else hexD(0xB5651D, 0xE8A060)   // ios: Views/AnalysisDetail.swift:238
    val teal = Theme.teal; val ink = Theme.ink; val purple = Theme.purple; val card = Theme.card

    // ios: Views/AnalysisDetail.swift:328
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        // ios: Views/AnalysisDetail.swift:329 (제목 줄 표시 방식은 NavStack 몫)
        PinnedLayout(header = {   // ios: Views/AnalysisDetail.swift:240
            // ios: Views/AnalysisDetail.swift:241
            PinnedBox {
                DetailHead(title = "내 길", sub = if (m.isGoal) "목표 ${AppModel.wonK(m.gK)} · ${m.goalToText} · 월 ${AppModel.wonK(m.gM)} · ${m.pathStartText} 시작" else "본전 ${AppModel.eok(K)} · 1년 안 · ${m.pathStartText} 시작")   // ios: Views/AnalysisDetail.swift:242
                // ios: Views/AnalysisDetail.swift:243,245
                Row(Modifier.fillMaxWidth()) {
                    val gapTxt = (if (gap >= 0) "앞섬" else "뒤처짐") + " " + cformat("%.1f", abs(gap) * 100) + "%"   // Views/AnalysisDetail.swift:244 (검사 예외: 같은 글)
                    T(gapTxt, Modifier.weight(1f).alignByBaseline(), style = appFont(22f, FontWeight.Bold), color = lead, roll = gapTxt)
                    // 웹 목표 진행처럼 금액 차이 (금액은 이 요약과 아래 달마다 막대에만)
                    T("계획보다 ${AppModel.eokS(act(mS) - need(mS))}", Modifier.alignByBaseline(), style = appFont(13f, FontWeight.SemiBold), color = lead)   // ios: Views/AnalysisDetail.swift:247
                }
                // 웹 목표 진행의 '시작 대비'와 같은 줄: 시작일 평가액 → 지금 평가액 (웹과 숫자를 나란히 맞춰 볼 수 있게)
                val v0 = p.pts.firstOrNull()?.second ?: 0.0   // ios: Views/AnalysisDetail.swift:250
                if (v0 > 0) {
                    // ios: Views/AnalysisDetail.swift:251,256
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        T("시작보다 ${AppModel.sgn((act(mS) - p.krIn) / v0 - 1)}", Modifier.weight(1f).alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:252,253
                        T("${AppModel.eok(v0)} → ${AppModel.eok(act(mS))}", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:254
                    }
                }
                // 웹 목표 진행 요약과 같은 목표 달성 확률 (전망 계산이 끝나기 전에는 '계산 중')
                // ios: Views/AnalysisDetail.swift:259,264
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    T(if (m.isGoal) "달성 확률" else "${m.keyName} 확률", Modifier.weight(1f).alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:260,261
                    T(if (f.exact) AppModel.pct(pPlan) else "계산 중", Modifier.alignByBaseline(), style = appFont(if (f.exact) 17f else 13f, FontWeight.Bold), color = if (f.exact) Theme.teal else Theme.muted)   // ios: Views/AnalysisDetail.swift:262
                }
                // ios: Views/AnalysisDetail.swift:286,289,296,303
                Box(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "계획선과 실제 평가액 그래프" }) {
                    // 길은 크기·값이 바뀔 때만 만든다 (그리는 중엔 계산 없음). 박동 원만 따로 다시 그린다
                    // ios: Views/AnalysisDetail.swift:265,287
                    Box(Modifier.matchParentSize().pulse(teal, dot = 8.dp) { s -> Offset((X(mS) * s.width).toFloat(), (Y(actPts.lastOrNull()?.second ?: 0.0) * s.height).toFloat()) }.drawWithCache {
                        val w = size.width; val h = size.height
                        // ios: Views/AnalysisDetail.swift:266
                        val band: Path? = if (bandPts.isEmpty()) null else
                            linePath(bandPts.map { Pair(X(it.first), Y(it.second)) }, size).apply {   // ios: Views/AnalysisDetail.swift:267
                                for (q in bandPts.asReversed()) lineTo((X(q.first) * w).toFloat(), (Y(q.third) * h).toFloat())   // ios: Views/AnalysisDetail.swift:268
                                close()   // ios: Views/AnalysisDetail.swift:269
                            }
                        val tx = (X(mS) * w).toFloat()   // ios: Views/AnalysisDetail.swift:272
                        val needPath = linePath(needPts.map { Pair(X(it.first), Y(it.second)) }, size)
                        val actPath = linePath(actPts.map { Pair(X(it.first), Y(it.second)) }, size)
                        // 거래일이 적을 때는 날마다 작은 점도 찍는다
                        val dots = if (actPts.size <= 40) actPts.dropLast(1).map { q -> Offset((X(q.first) * w).toFloat(), (Y(q.second) * h).toFloat()) } else emptyList()   // ios: Views/AnalysisDetail.swift:278,279
                        val ty = (Y(actPts.lastOrNull()?.second ?: 0.0) * h).toFloat()   // ios: Views/AnalysisDetail.swift:283
                        onDrawBehind {
                            if (band != null) drawPath(band, teal.copy(alpha = 0.14f))   // ios: Views/AnalysisDetail.swift:270
                            drawLine(ink, Offset(tx, 0f), Offset(tx, h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))   // ios: Views/AnalysisDetail.swift:273,274
                            drawPath(needPath, purple, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))   // ios: Views/AnalysisDetail.swift:275
                            drawPath(actPath, teal, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))   // ios: Views/AnalysisDetail.swift:276
                            for (d in dots) drawOval(teal, Offset(d.x - 2.dp.toPx(), d.y - 2.dp.toPx()), Size(4.dp.toPx(), 4.dp.toPx()))   // ios: Views/AnalysisDetail.swift:280
                            drawOval(teal, Offset(tx - 4.dp.toPx(), ty - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))   // ios: Views/AnalysisDetail.swift:284
                        }
                    })
                    // 범례와 예보 겹치기 확률은 그래프 안에 작게
                    // ios: Views/AnalysisDetail.swift:290,294
                    Column(Modifier.align(Alignment.TopStart).background(card.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        legend(teal, "실제"); legend(purple, "계획선")   // ios: Views/AnalysisDetail.swift:291
                        if (view == "band") legend(teal.copy(alpha = 0.3f), "보통 범위")   // ios: Views/AnalysisDetail.swift:292
                    }
                    // ios: Views/AnalysisDetail.swift:297
                    if (view == "band" && f.exact) {
                        // ios: Views/AnalysisDetail.swift:298,299,300
                        T("넘을 확률 ${AppModel.pct(pPlan)}", Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp).background(card.copy(alpha = 0.85f), CapsuleShape).padding(horizontal = 8.dp, vertical = 4.dp), style = appFont(12f, FontWeight.Bold), color = purple)
                    }
                }
                // ios: Views/AnalysisDetail.swift:304
                Row(Modifier.fillMaxWidth()) { val st = appFont(11f); T("시작 ${m.pathStartMonth}", Modifier.weight(1f), style = st, color = Theme.muted); T(if (view == "past") "다음 달" else if (m.isGoal) "목표일 ${m.goalToText}" else "1년 뒤", style = st, color = Theme.muted) }
                ChipRow(listOf("past" to "시작부터", "all" to "목표일까지", "band" to "보통 범위 겹치기"), selection = view, onSelect = { picked = it }, fill = true)   // ios: Views/AnalysisDetail.swift:305
            }
        }) {   // ios: Views/AnalysisDetail.swift:307
            // ios: Views/AnalysisDetail.swift:308,326
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ios: Views/AnalysisDetail.swift:309
                Card {
                    T("달마다 계획과 차이", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:310
                    // ios: Views/AnalysisDetail.swift:311
                    monthRows.indices.forEach { i ->
                        val t = monthRows[i].second; val g = if (need(t) > 0) act(t) / need(t) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:312
                        SignedBar(label = monthRows[i].first, value = AppModel.eokS(act(t) - need(t)), g = g, scale = 0.2, pos = Theme.green, neg = Theme.orange,   // ios: Views/AnalysisDetail.swift:313
                                  valueColor = if (g >= 0) Theme.teal else hexD(0xB5651D, 0xE8A060))   // ios: Views/AnalysisDetail.swift:314
                    }
                    if (months < 1) T("한 달 지나면 쌓여요", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:316
                }
                // ios: Views/AnalysisDetail.swift:318,322
                KpiGrid(listOf(
                    Kpi(k = "남은 기간", v = leftText, sub = if (m.isGoal) m.goalToText else "1년 목표"),   // ios: Views/AnalysisDetail.swift:319
                    Kpi(k = "필요 연수익", v = AppModel.sgn(needCagr), sub = if (m.isGoal) (if (mM > 0) "적립 포함" else "추가 납입 없이") else "본전까지"),   // ios: Views/AnalysisDetail.swift:320
                    Kpi(k = "이번 달 납입", v = if (m.isGoal) AppModel.wonK(m.gM) else "없음", sub = if (m.isGoal) "계획에 포함" else "적립 없음"),   // ios: Views/AnalysisDetail.swift:321
                ), dark = true)
                val cashTxt = if (m.cashKrw > 0) " + 현금" else ""   // Views/AnalysisDetail.swift:323 (검사 예외: 같은 글)
                Footnote(if (m.isGoal) "기준선 · 실제 = 그날 수량×종가×환율$cashTxt"
                         else "기준선 · 본전 기준")   // ios: Views/AnalysisDetail.swift:324
            }
        }
    }
}

// MARK: 내 길 (목표 루트) — 판정 카드 · 구성에 따라 언제 닿을까 · 계획선 그래프 · 숫자 자세히

/** 도달 시점 한 줄: 점 = 보통(가운데) 닿는 때, 막대 = 10번 중 8번(10~90%) 범위, 세로 점선 = 목표일. 위치는 연도 축 a0~a1 */
// ios: Views/AnalysisDetail.swift:336,343
@Composable
fun EtaRow(
    name: String,   // ios: Views/AnalysisDetail.swift:337
    sub: String,   // ios: Views/AnalysisDetail.swift:338
    sim: GoalSim,   // ios: Views/AnalysisDetail.swift:339
    a0: Double, a1: Double, goalYear: Double,   // ios: Views/AnalysisDetail.swift:340
    strong: Boolean = true,   // ios: Views/AnalysisDetail.swift:341
) {
    val x = { y: Double -> min(1.0, max(0.0, (y - a0) / max(0.5, a1 - a0))) }   // ios: Views/AnalysisDetail.swift:344
    val lo = x(AppModel.yearAt(sim.etaLo)); val hi = if (sim.etaHi.isFinite()) x(AppModel.yearAt(sim.etaHi)) else 1.0   // ios: Views/AnalysisDetail.swift:345
    val mid = if (sim.etaMonths.isFinite()) x(AppModel.yearAt(sim.etaMonths)) else 1.0; val gx = x(goalYear)   // ios: Views/AnalysisDetail.swift:346
    val c = if (strong) Theme.teal else Theme.muted   // ios: Views/AnalysisDetail.swift:347
    // ios: Views/AnalysisDetail.swift:363 (값이 바뀌면 점·막대가 미끄러진다)
    val ease = tween<Float>(250, easing = easeInOut)
    val loA by animateFloatAsState(lo.toFloat(), ease, label = "lo"); val hiA by animateFloatAsState(hi.toFloat(), ease, label = "hi")
    val midA by animateFloatAsState(mid.toFloat(), ease, label = "mid"); val gxA by animateFloatAsState(gx.toFloat(), ease, label = "gx")
    val tm = AppModel.whenText(sim.etaMonths, cap = sim.capYears); val tLo = AppModel.whenText(sim.etaLo, cap = sim.capYears); val tHi = AppModel.whenText(sim.etaHi, cap = sim.capYears)
    val label = "$name, 보통 $tm, 10번 중 8번은 ${tLo}에서 $tHi 사이"   // ios: Views/AnalysisDetail.swift:369
    // ios: Views/AnalysisDetail.swift:348,368
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ios: Views/AnalysisDetail.swift:349,351
        Row(Modifier.fillMaxWidth()) {
            T(name, Modifier.weight(1f).alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:350
            T(tm, Modifier.alignByBaseline(), style = appFont(13f, FontWeight.SemiBold), color = c)   // ios: Views/AnalysisDetail.swift:352
        }
        val border = Theme.border; val ink = Theme.ink
        // ios: Views/AnalysisDetail.swift:354,355,356,365
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val w = maxWidth
            Box(Modifier.offset(y = 9.dp).size(w, 2.dp).background(border.copy(alpha = 0.5f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:357
            Box(Modifier.offset(x = w * loA, y = 7.dp).size(androidx.compose.ui.unit.max(6.dp, w * (hiA - loA)), 6.dp).background(c.copy(alpha = 0.35f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:358
            Box(Modifier.offset(x = w * midA - 6.dp, y = 4.dp).size(12.dp, 12.dp).background(c, CircleShape))   // ios: Views/AnalysisDetail.swift:359
            // ios: Views/AnalysisDetail.swift:360,361
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { val gpx = gxA * size.width; drawLine(ink, Offset(gpx, 0f), Offset(gpx, 20.dp.toPx()), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))) }
        }
        if (sub.isNotEmpty()) T(sub, style = appFont(11f), color = Theme.muted, maxLines = 2)   // ios: Views/AnalysisDetail.swift:366
    }
}

object EtaRow {
    /** 여러 줄을 감싸는 연도 축 (목표일 포함, 정수 해) */
    // ios: Views/AnalysisDetail.swift:373
    fun axis(sims: List<GoalSim>, goalYear: Double): Pair<Double, Double> {
        var lo = goalYear; var hi = goalYear   // ios: Views/AnalysisDetail.swift:374
        // ios: Views/AnalysisDetail.swift:375
        for (s in sims) {
            lo = min(lo, AppModel.yearAt(s.etaLo))   // ios: Views/AnalysisDetail.swift:376
            hi = max(hi, AppModel.yearAt(if (s.etaHi.isFinite()) s.etaHi else (s.capYears * 12).toDouble()))   // ios: Views/AnalysisDetail.swift:377
        }
        return Pair(floor(lo), max(floor(lo) + 2, ceil(hi)))   // ios: Views/AnalysisDetail.swift:379
    }
}

/** 연도 눈금 (a0~a1, 2년마다) */
// ios: Views/AnalysisDetail.swift:384,386
@Composable
fun EtaTicks(
    a0: Double, a1: Double,   // ios: Views/AnalysisDetail.swift:385
) {
    val step = if (a1 - a0 > 6) 2 else 1   // ios: Views/AnalysisDetail.swift:387
    val ys = (a0.toInt()..a1.toInt() step step).toList()   // ios: Views/AnalysisDetail.swift:388
    // ios: Views/AnalysisDetail.swift:389,395,396
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics {}) {
        val wd = maxWidth.value.toDouble()
        // ios: Views/AnalysisDetail.swift:390
        ys.forEach { y ->
            val cx = min(wd - 14, max(14.0, (y - a0) / max(0.5, a1 - a0) * wd)); val cy = 7.dp   // ios: Views/AnalysisDetail.swift:392
            T(y.toString(), Modifier.tickAt(cx.dp, cy), style = appFont(11f), color = Theme.muted, maxLines = 1)   // ios: Views/AnalysisDetail.swift:391
        }
    }
}

// ios: Views/AnalysisDetail.swift:400,401,405
@Composable
fun MyPathGoalView(m: AppModel) {
    var toGoal by remember { mutableStateOf(true) }   // ios: Views/AnalysisDetail.swift:402
    var more by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:403
    // 실제 평가액은 지난 시세를 훑는 계산이라 읽은 값이 바뀔 때만 다시 한다
    val p by remember(m) { derivedStateOf { m.myPath } }; val mS = p.start; val mEnd = p.end; val act = p.act; val need = p.need   // ios: Views/AnalysisDetail.swift:406
    val f = m.forecast; val K = m.gK * 1e4; val now = m.trackValue   // ios: Views/AnalysisDetail.swift:407
    val yrsE = m.etaYears   // ios: Views/AnalysisDetail.swift:408
    // ios: Views/AnalysisDetail.swift:409,410
    val sims: List<Triple<String, String, GoalSim>> = listOf(Triple("지금 구성", m.nowMix.desc, m.simulate(m.nowMix, years = yrsE))) +
        listOf(AppModel.mixes[1], AppModel.mixes[2]).map { Triple(it.name, it.desc, m.simulate(it, years = yrsE)) }
    val mine = sims[0].third   // ios: Views/AnalysisDetail.swift:411
    val goalMo = max(0.0, secs(java.time.Instant.now(), m.goalToDate) / (365.25 * 86400) * 12)   // ios: Views/AnalysisDetail.swift:412
    val goalYear = AppModel.yearAt(goalMo)   // ios: Views/AnalysisDetail.swift:413
    val ax = EtaRow.axis(sims.map { it.third }, goalYear = goalYear); val a0 = ax.first; val a1 = ax.second   // ios: Views/AnalysisDetail.swift:414
    val leftText = AppModel.leftText(yrs(Day.today, Day.str(m.goalToDate)))   // ios: Views/AnalysisDetail.swift:415
    val zone = ZoneId.systemDefault()
    val gc = m.goalToDate.atZone(zone)   // ios: Views/AnalysisDetail.swift:416
    // 그래프: 세로 눈금은 기간을 바꿔도 같게 (목표일까지 전체 값으로 정한다)
    val x1 = if (toGoal) mEnd else mS + 1.0 / 12   // ios: Views/AnalysisDetail.swift:418
    val actPts = p.pts   // ios: Views/AnalysisDetail.swift:419
    val needAll = (0..60).map { i -> val t = mEnd * i / 60; Pair(t, need(t)) }   // ios: Views/AnalysisDetail.swift:420
    val needPts = (0..60).map { i -> val t = x1 * i / 60; Pair(t, need(t)) }   // ios: Views/AnalysisDetail.swift:421
    // ios: Views/AnalysisDetail.swift:422,423
    val bandAll: List<Triple<Double, Double, Double>> = if (f.tY.isEmpty()) emptyList()
        else (0..max(min(1, f.tY.size - 1), f.idx(years = mEnd - mS))).map { i -> Triple(mS + f.tY[i], f.q75[i] + m.cashKrw, f.q25[i] + m.cashKrw) }
    val bandPts = if (toGoal) bandAll else emptyList()   // ios: Views/AnalysisDetail.swift:424
    val vals = actPts.map { it.second } + needAll.map { it.second } + bandAll.flatMap { listOf(it.second, it.third) } + listOf(K)   // ios: Views/AnalysisDetail.swift:425
    val hi = (vals.maxOrNull() ?: 1.0) * 1.05; val lo = (vals.filter { it > 0 }.minOrNull() ?: 0.0) * 0.9   // ios: Views/AnalysisDetail.swift:426
    val X = { t: Double -> t / max(1e-6, x1) }; val Y = { v: Double -> (hi - v) / max(1.0, hi - lo) }   // ios: Views/AnalysisDetail.swift:427
    val months = runCatching { ChronoUnit.MONTHS.between(m.pathStartDate.atZone(zone), ZonedDateTime.now(zone)).toInt() }.getOrDefault(0)   // ios: Views/AnalysisDetail.swift:428
    val gap = if (need(mS) > 0) act(mS) / need(mS) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:429
    val yrsLeft = mEnd - mS; val mM = p.monthly   // ios: Views/AnalysisDetail.swift:430
    val needCagr = if (now > 0 && yrsLeft > 0) (max(1.0, K - mM * 12 * yrsLeft) / now).pow(1 / yrsLeft) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:431
    val dark = hexD(0x1E4A44, 0x16302C)   // ios: Views/AnalysisDetail.swift:432
    val teal = Theme.teal; val ink = Theme.ink; val purple = Theme.purple; val card = Theme.card

    // ios: Views/AnalysisDetail.swift:434,564
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/AnalysisDetail.swift:565 (제목 줄 표시 방식은 NavStack 몫)
        // ios: Views/AnalysisDetail.swift:435,562
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 1. 판정 카드
            // ios: Views/AnalysisDetail.swift:437,459,460,461
            Column(Modifier.fillMaxWidth().background(dark, RoundedCornerShape(18.dp)).semantics(mergeDescendants = true) {}.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("${AppModel.wonK(m.gK)} · ${m.goalToText} · $leftText 남음", style = appFont(13f), color = Color.White.copy(alpha = 0.85f))   // ios: Views/AnalysisDetail.swift:438
                // ios: Views/AnalysisDetail.swift:439,440
                val vText = if (f.exact) "가능성 ${AppModel.verdict(f.pGoal)} · ${AppModel.pct(f.pGoal)}" else "가능성 계산 중"
                T(vText, Modifier.fillMaxWidth(), style = appFont(22f, FontWeight.Bold), color = Color.White, roll = vText)
                // ios: Views/AnalysisDetail.swift:441,442,447
                BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.18f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:443
                    Box(Modifier.fillMaxHeight().width(maxWidth * min(1.0, max(0.0, now / max(1.0, K))).toFloat()).background(hexD(0x9CCFC6, 0x9CCFC6), CapsuleShape))   // ios: Views/AnalysisDetail.swift:444
                }
                val st12 = appFont(12f, FontWeight.SemiBold); val fg85 = Color.White.copy(alpha = 0.85f)   // ios: Views/AnalysisDetail.swift:451
                // ios: Views/AnalysisDetail.swift:448,449
                Row(Modifier.fillMaxWidth()) { T(AppModel.eok(now), Modifier.weight(1f), style = st12, color = fg85); T(AppModel.eok(K), style = st12, color = fg85) }
                // ios: Views/AnalysisDetail.swift:452
                Row(Modifier.fillMaxWidth()) {
                    T("지금대로면 ${AppModel.whenText(mine.etaMonths, cap = mine.capYears)} 도달", Modifier.weight(1f).padding(end = 8.dp).alignByBaseline(), style = appFont(13f), color = Color.White.copy(alpha = 0.9f))   // ios: Views/AnalysisDetail.swift:453,454,455
                    if (f.exact) T("목표일 달성 확률 ${AppModel.pct(f.pGoal)}", Modifier.alignByBaseline(), style = appFont(11f), color = Color.White.copy(alpha = 0.6f))   // ios: Views/AnalysisDetail.swift:456
                }
            }

            // 2. 구성에 따라 언제 닿을까?
            // ios: Views/AnalysisDetail.swift:464
            Card {
                // ios: Views/AnalysisDetail.swift:465,467
                Row(Modifier.fillMaxWidth()) {
                    T("언제 닿을까?", Modifier.weight(1f).alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:466
                    T("● 보통 · ━ 10번 중 8번", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:468
                }
                EtaTicks(a0 = a0, a1 = a1)   // ios: Views/AnalysisDetail.swift:470
                // ios: Views/AnalysisDetail.swift:471
                sims.indices.forEach { i ->
                    EtaRow(name = sims[i].first, sub = sims[i].second, sim = sims[i].third, a0 = a0, a1 = a1, goalYear = goalYear, strong = i == 0)   // ios: Views/AnalysisDetail.swift:472
                }
                T("┆ 목표일 ${gc.year}.${gc.monthValue}", style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:474
                // ios: Views/AnalysisDetail.swift:475
                if (m.route != Route.novice && m.rows.isNotEmpty()) {
                    // ios: Views/AnalysisDetail.swift:476,480
                    Box(Modifier.fillMaxWidth().plainClickable { m.analysisPath = m.analysisPath + MyPathCompareRoute }
                        .defaultMinSize(minHeight = 48.dp).background(Theme.teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {   // ios: Views/AnalysisDetail.swift:478
                        T("내 구성으로 비교", style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Views/AnalysisDetail.swift:477
                    }
                }
            }

            // 3. 계획선 그래프
            // ios: Views/AnalysisDetail.swift:485
            Card {
                T(if (toGoal) "목표일까지" else "시작부터 지금까지", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:486
                // ios: Views/AnalysisDetail.swift:505,512
                Box(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "계획선과 실제 평가액 그래프, 목표 ${AppModel.eok(K)}, 오늘 ${AppModel.eok(now)}" }) {
                    // ios: Views/AnalysisDetail.swift:487,506
                    Box(Modifier.matchParentSize().pulse(teal, dot = 8.dp) { s -> Offset((X(mS) * s.width).toFloat(), (Y(actPts.lastOrNull()?.second ?: now) * s.height).toFloat()) }.drawWithCache {
                        val w = size.width; val h = size.height
                        // ios: Views/AnalysisDetail.swift:488
                        val band: Path? = if (bandPts.isEmpty()) null else
                            linePath(bandPts.map { Pair(X(it.first), Y(it.second)) }, size).apply {   // ios: Views/AnalysisDetail.swift:489
                                for (q in bandPts.asReversed()) lineTo((X(q.first) * w).toFloat(), (Y(q.third) * h).toFloat())   // ios: Views/AnalysisDetail.swift:490
                                close()   // ios: Views/AnalysisDetail.swift:491
                            }
                        val gy = (Y(K) * h).toFloat()   // ios: Views/AnalysisDetail.swift:494
                        val tx = (X(mS) * w).toFloat()   // ios: Views/AnalysisDetail.swift:497
                        val needPath = linePath(needPts.map { Pair(X(it.first), Y(it.second)) }, size)
                        val actPath = linePath(actPts.map { Pair(X(it.first), Y(it.second)) }, size)
                        val ty = (Y(actPts.lastOrNull()?.second ?: now) * h).toFloat()   // ios: Views/AnalysisDetail.swift:502
                        onDrawBehind {
                            if (band != null) drawPath(band, teal.copy(alpha = 0.14f))   // ios: Views/AnalysisDetail.swift:492
                            drawLine(ink.copy(alpha = 0.6f), Offset(0f, gy), Offset(w, gy), 1.dp.toPx())   // ios: Views/AnalysisDetail.swift:495,496
                            drawLine(ink, Offset(tx, 0f), Offset(tx, h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))   // ios: Views/AnalysisDetail.swift:498,499
                            drawPath(needPath, purple, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))   // ios: Views/AnalysisDetail.swift:500
                            drawPath(actPath, teal, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))   // ios: Views/AnalysisDetail.swift:501
                            drawOval(teal, Offset(tx - 4.dp.toPx(), ty - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))   // ios: Views/AnalysisDetail.swift:503
                        }
                    })
                    // ios: Views/AnalysisDetail.swift:507,508,509,510
                    T("목표 ${AppModel.eok(K)}", Modifier.align(Alignment.TopEnd).offset(y = max(0.0, Y(K) * 160 - 18).dp).background(card.copy(alpha = 0.85f), CapsuleShape).padding(horizontal = 6.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold), color = ink)
                }
                val st11 = appFont(11f)   // ios: Views/AnalysisDetail.swift:517
                // ios: Views/AnalysisDetail.swift:513
                Row(Modifier.fillMaxWidth()) {
                    T(m.pathStartMonth, Modifier.weight(1f), style = st11, color = Theme.muted)   // ios: Views/AnalysisDetail.swift:514
                    T(if (toGoal) "${gc.year}.${gc.monthValue}" else "다음 달", style = st11, color = Theme.muted)   // ios: Views/AnalysisDetail.swift:515
                }
                // ios: Views/AnalysisDetail.swift:518
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    legend(teal, "실제"); legend(purple, "계획")   // ios: Views/AnalysisDetail.swift:519
                    if (toGoal) legend(teal.copy(alpha = 0.3f), "보통 범위")   // ios: Views/AnalysisDetail.swift:520
                }
                ChipRow(listOf(true to "목표일까지", false to "지금까지"), selection = toGoal, onSelect = { toGoal = it }, fill = true)   // ios: Views/AnalysisDetail.swift:522
            }

            // 4. 숫자 자세히 보기 (DisclosureGroup: 이름 줄을 누르면 펼친다)
            // ios: Views/AnalysisDetail.swift:526
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                // ios: Views/AnalysisDetail.swift:556
                Row(Modifier.fillMaxWidth().plainClickable { more = !more }, verticalAlignment = Alignment.CenterVertically) {
                    T("자세히", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:557
                    val rot by animateFloatAsState(if (more) 90f else 0f, label = "chev")
                    Sym("chevron.right", 13f, Theme.teal, Modifier.rotate(rot))
                }
                AnimatedVisibility(more) {
                    // ios: Views/AnalysisDetail.swift:527,555
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // ios: Views/AnalysisDetail.swift:528,530
                        Row(Modifier.fillMaxWidth()) {
                            T("계획보다 ${AppModel.sgn(gap)}", Modifier.weight(1f).alignByBaseline(), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:529
                            T(AppModel.eokS(act(mS) - need(mS)), Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:531
                        }
                        val v0 = p.pts.firstOrNull()?.second ?: 0.0   // ios: Views/AnalysisDetail.swift:533
                        if (v0 > 0) {
                            // ios: Views/AnalysisDetail.swift:534,536
                            Row(Modifier.fillMaxWidth()) {
                                T("시작보다 ${AppModel.sgn((act(mS) - p.krIn) / v0 - 1)}", Modifier.weight(1f).alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:535
                                T("${AppModel.eok(v0)} → ${AppModel.eok(act(mS))}", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:537
                            }
                        }
                        // ios: Views/AnalysisDetail.swift:540
                        if (months >= 1) {
                            // ios: Views/AnalysisDetail.swift:541
                            (max(1, months - 5)..months).forEach { k ->
                                val d = m.pathStartDate.atZone(zone).plusMonths(k.toLong()).toInstant()   // ios: Views/AnalysisDetail.swift:542
                                val t = secs(m.pathStartDate, d) / (365.25 * 86400); val g = if (need(t) > 0) act(t) / need(t) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:543
                                SignedBar(label = "${k}달째", value = AppModel.eokS(act(t) - need(t)), g = g, scale = 0.2, pos = Theme.green, neg = Theme.orange,   // ios: Views/AnalysisDetail.swift:544
                                          valueColor = if (g >= 0) Theme.teal else hexD(0xB5651D, 0xE8A060))   // ios: Views/AnalysisDetail.swift:545
                            }
                        }
                        // ios: Views/AnalysisDetail.swift:548,553
                        KpiGrid(listOf(
                            Kpi(k = "남은 기간", v = leftText, sub = m.goalToText),   // ios: Views/AnalysisDetail.swift:549
                            Kpi(k = "필요 연수익", v = AppModel.sgn(needCagr), sub = if (mM > 0) "적립 포함" else "추가 납입 없이"),   // ios: Views/AnalysisDetail.swift:550
                            Kpi(k = "이번 달 납입", v = AppModel.wonK(m.gM), sub = "계획에 포함"),   // ios: Views/AnalysisDetail.swift:551
                            Kpi(k = "달성 확률", v = if (f.exact) AppModel.pct(f.pGoal) else "계산 중", sub = "목표일 기준"),   // ios: Views/AnalysisDetail.swift:552
                        ), dark = true)
                    }
                }
            }

            Footnote("과거 수익률로 만든 예상")   // ios: Views/AnalysisDetail.swift:560
        }
    }
}

/** 시트 목록 한 줄 (List 의 Button 줄) */
@Composable
private fun SheetRow(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Theme.card).then(if (enabled) Modifier.plainClickable(onClick = onClick) else Modifier.semantics { disabled() })
        .defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) { content() }
    HDivider(Theme.line)
}

/** 비중 바꿔 비교 · 종목 추가: 빈칸이면 자주 쓰는 목록, 찾으면 미국·한국 종목 아무거나. 사이트 표에 없는 종목은 3년 종가를 받아 앱에서 계산한다 (ExtraStats) */
// ios: Views/AnalysisDetail.swift:570,580
@Composable
fun CompareAddSheet(
    have: Set<String>,   // ios: Views/AnalysisDetail.swift:571
    add: (String) -> Unit,   // ios: Views/AnalysisDetail.swift:572
    dismiss: () -> Unit,   // ios: Views/AnalysisDetail.swift:573
) {
    var query by remember { mutableStateOf("") }   // ios: Views/AnalysisDetail.swift:574
    var remote by remember { mutableStateOf(listOf<StockHit>()) }   // ios: Views/AnalysisDetail.swift:575
    var searching by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:576
    var loading by remember { mutableStateOf<String?>(null) }   // ios: Views/AnalysisDetail.swift:577
    var failed by remember { mutableStateOf<String?>(null) }   // ios: Views/AnalysisDetail.swift:578
    val scope = rememberCoroutineScope()
    val q = query.trim()   // ios: Views/AnalysisDetail.swift:581
    // 앱 안 목록 찾기는 딴 줄(Default)에서 (한국 상장 종목을 훑는다: 그리는 줄을 막지 않게)
    var local by remember { mutableStateOf(listOf<StockHit>()) }
    LaunchedEffect(q) { local = if (q.isEmpty()) emptyList() else withContext(Dispatchers.Default) { StockSearch.local(q) } }   // ios: Views/AnalysisDetail.swift:582
    val hits = (local + remote.filter { r -> local.none { it.id == r.id } }).filter { !have.contains(it.id) }   // ios: Views/AnalysisDetail.swift:583

    // ios: Views/AnalysisDetail.swift:618
    suspend fun search() {
        remote = emptyList()   // ios: Views/AnalysisDetail.swift:619
        val q0 = query.trim()   // ios: Views/AnalysisDetail.swift:620
        if (q0.isEmpty() || !q0.all { it.code < 128 }) { searching = false; return }   // ios: Views/AnalysisDetail.swift:621
        searching = true   // ios: Views/AnalysisDetail.swift:622
        delay(400)   // ios: Views/AnalysisDetail.swift:623
        if (!currentCoroutineContext().isActive) return   // ios: Views/AnalysisDetail.swift:624
        val r = StockSearch.remote(q0)   // ios: Views/AnalysisDetail.swift:625
        if (!currentCoroutineContext().isActive) return   // ios: Views/AnalysisDetail.swift:626
        remote = r; searching = false   // ios: Views/AnalysisDetail.swift:627
    }

    /** 사이트 표에 있으면 바로, 없으면 3년 종가를 받은 뒤 넣는다 */
    // ios: Views/AnalysisDetail.swift:631
    suspend fun pick(h: StockHit) {
        failed = null   // ios: Views/AnalysisDetail.swift:632
        if (Market.shared.stats?.tickers?.get(SiteStats.mcKey(h.id)) != null) { add(h.id); dismiss(); return }   // ios: Views/AnalysisDetail.swift:633
        loading = h.id   // ios: Views/AnalysisDetail.swift:634
        try {
            ExtraStats.shared.fetch(h); add(h.id); dismiss()   // ios: Views/AnalysisDetail.swift:636
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            failed = "${h.name} 시세 못 받음 · 다시 눌러 주세요"   // ios: Views/AnalysisDetail.swift:637
        } finally { loading = null }   // ios: Views/AnalysisDetail.swift:635
    }

    LaunchedEffect(query) { search() }   // ios: Views/AnalysisDetail.swift:601

    // ios: Views/AnalysisDetail.swift:584
    Column(Modifier.fillMaxSize()) {
        // ios: Views/AnalysisDetail.swift:602
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            T("종목 추가", Modifier.align(Alignment.Center), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            T("닫기", Modifier.align(Alignment.CenterStart).plainClickable { dismiss() }, style = TextStyle(fontSize = 17.sp), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:603
        }
        // ios: Views/AnalysisDetail.swift:600
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Theme.card, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            InputField(query, { query = it }, "이름·티커·종목 코드")
        }
        // ios: Views/AnalysisDetail.swift:585
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // ios: Views/AnalysisDetail.swift:586
            if (q.isEmpty()) {
                // ios: Views/AnalysisDetail.swift:587
                Parallel.universe.filter { !have.contains(it.id) }.forEach { u ->
                    SheetRow(onClick = { add(u.id); dismiss() }) { line(u.name, u.id, busy = false) }   // ios: Views/AnalysisDetail.swift:588
                }
            } else {   // ios: Views/AnalysisDetail.swift:590
                // ios: Views/AnalysisDetail.swift:591
                hits.forEach { h ->
                    // ios: Views/AnalysisDetail.swift:592,593
                    SheetRow(enabled = loading == null, onClick = { scope.launch { pick(h) } }) { line(h.name, h.id + " · " + h.market, busy = loading == h.id) }
                }
                // ios: Views/AnalysisDetail.swift:595
                if (searching) Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Spinner(); T("찾는 중", style = appFont(13f), color = Theme.sub) }
                else if (hits.isEmpty()) T("없음 · 이름·티커·종목 코드로", Modifier.padding(vertical = 10.dp), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:596
            }
            failed?.let { T(it, Modifier.padding(vertical = 10.dp), style = appFont(13f), color = Theme.orange) }   // ios: Views/AnalysisDetail.swift:598
        }
    }
}

// ios: Views/AnalysisDetail.swift:607,608
@Composable
private fun line(name: String, sub: String, busy: Boolean) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    // ios: Views/AnalysisDetail.swift:609,613
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(name, style = appFont(15f, FontWeight.Bold), color = Theme.ink, maxLines = 1)   // ios: Views/AnalysisDetail.swift:610
        T(sub, style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:611
    }
    if (busy) Spinner()   // ios: Views/AnalysisDetail.swift:614
}

// MARK: 내 길 — 종목 비중 바꿔 비교 (B-2안). 저장하면 모의 바구니(담아 본 나)가 된다
// ios: Views/AnalysisDetail.swift:643,644,652
@Composable
fun MyPathCompareView(m: AppModel) {
    val nav = LocalNavBack.current
    val dismiss = { nav?.pop?.invoke(); Unit }   // ios: Views/AnalysisDetail.swift:645
    var w0 by remember { mutableStateOf(mapOf<String, Int>()) }   // ios: Views/AnalysisDetail.swift:646
    var w by remember { mutableStateOf(mapOf<String, Int>()) }   // ios: Views/AnalysisDetail.swift:647
    var order by remember { mutableStateOf(listOf<String>()) }   // ios: Views/AnalysisDetail.swift:648
    var loaded by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:649
    var adding by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:650

    val sum = w.values.fold(0) { a, x -> a + x }   // ios: Views/AnalysisDetail.swift:653
    val yrsE = m.etaYears   // ios: Views/AnalysisDetail.swift:654
    val nowSim = m.simulate(m.nowMix, years = yrsE)   // ios: Views/AnalysisDetail.swift:655
    // ios: Views/AnalysisDetail.swift:656
    val parts: List<Part> = order.mapNotNull { id ->
        val x = w[id]; if (x == null || x <= 0) return@mapNotNull null   // ios: Views/AnalysisDetail.swift:657
        val usd = m.rows.firstOrNull { it.id == id }?.let { it.sym.currency == Currency.usd } ?: ExtraStats.shared.usd(id) ?: true   // ios: Views/AnalysisDetail.swift:658
        Part(key = SiteStats.mcKey(id), ccy = if (usd) "USD" else "KRW", w = x.toDouble() / max(1, sum).toDouble())   // ios: Views/AnalysisDetail.swift:659
    }
    val newSim = if (w == w0 || parts.isEmpty()) nowSim else m.simulate(m.nowMix, years = yrsE, parts = parts)   // ios: Views/AnalysisDetail.swift:661
    val goalMo = max(0.0, secs(java.time.Instant.now(), m.goalToDate) / (365.25 * 86400) * 12)   // ios: Views/AnalysisDetail.swift:662
    val goalYear = AppModel.yearAt(goalMo)   // ios: Views/AnalysisDetail.swift:663
    val ax = EtaRow.axis(listOf(nowSim, newSim), goalYear = goalYear); val a0 = ax.first; val a1 = ax.second   // ios: Views/AnalysisDetail.swift:664
    val gc = m.goalToDate.atZone(ZoneId.systemDefault())   // ios: Views/AnalysisDetail.swift:665
    val canSave = sum == 100 && m.pworld != null && w.filter { it.value > 0 }.keys.all { Parallel.has(it) }   // ios: Views/AnalysisDetail.swift:666
    // ios: Views/AnalysisDetail.swift:667
    val headline: String = run {
        if (newSim.etaMonths <= goalMo) return@run "보통 목표일 안"   // ios: Views/AnalysisDetail.swift:668
        if (newSim.etaMonths < nowSim.etaMonths - 0.5) return@run "빨라짐 · 보통 목표일 넘김"   // ios: Views/AnalysisDetail.swift:669
        if (newSim.etaMonths > nowSim.etaMonths + 0.5) return@run "늦어짐 · 보통 목표일 넘김"   // ios: Views/AnalysisDetail.swift:670
        "비슷 · 보통 목표일 넘김"   // ios: Views/AnalysisDetail.swift:671
    }   // ios: Views/AnalysisDetail.swift:672
    val width = if (newSim.etaHi.isFinite()) newSim.etaHi - newSim.etaLo else Double.POSITIVE_INFINITY   // ios: Views/AnalysisDetail.swift:673
    val spread = if (width > 48) "편차 큼 · 아주 빠를 수도, 한참 늦을 수도"   // ios: Views/AnalysisDetail.swift:674
        else if (width > 24) "편차 보통" else "편차 작음"   // ios: Views/AnalysisDetail.swift:675

    // ios: Views/AnalysisDetail.swift:743
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect   // ios: Views/AnalysisDetail.swift:744
        loaded = true   // ios: Views/AnalysisDetail.swift:745
        val t = max(1.0, m.rows.fold(0.0) { a, r -> a + r.value })   // ios: Views/AnalysisDetail.swift:746
        val x = LinkedHashMap<String, Int>()   // ios: Views/AnalysisDetail.swift:747
        for (r in m.rows) x[r.id] = (x[r.id] ?: 0) + (r.value / t * 100).swRound().toInt()   // ios: Views/AnalysisDetail.swift:748
        x.maxByOrNull { it.value }?.key?.let { top -> x[top] = (x[top] ?: 0) + 100 - x.values.fold(0) { a, v -> a + v } }   // ios: Views/AnalysisDetail.swift:749
        order = m.rows.map { it.id }.fold(listOf<String>()) { acc, id -> if (acc.contains(id)) acc else acc + id }   // ios: Views/AnalysisDetail.swift:750
        w0 = x.toMap(); w = x.toMap()   // ios: Views/AnalysisDetail.swift:751
    }

    // ios: Views/AnalysisDetail.swift:755
    @Composable
    fun row(id: String, color: Color) {
        val r = m.rows.firstOrNull { it.id == id }   // ios: Views/AnalysisDetail.swift:756
        val name = r?.sym?.name ?: Parallel.universe.firstOrNull { it.id == id }?.name ?: ExtraStats.shared.name(id) ?: id   // ios: Views/AnalysisDetail.swift:757
        val kind = r?.sym?.sectorText ?: "새로 담음"   // ios: Views/AnalysisDetail.swift:758
        val d = (w[id] ?: 0) - (w0[id] ?: 0)   // ios: Views/AnalysisDetail.swift:759
        val bordered = if (Theme.isDark) Color(0x3D767680) else Color(0x1F767680)   // .buttonStyle(.bordered) 바탕
        // ios: Views/AnalysisDetail.swift:760
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp, 12.dp).background(color, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:761
            // ios: Views/AnalysisDetail.swift:762,766
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(name, style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/AnalysisDetail.swift:763
                val dTxt = if (d == 0) "그대로" else if (d > 0) "+${d}%p" else "${d}%p"
                T("$kind · $dTxt", style = appFont(11f), color = Theme.muted, maxLines = 1)   // Views/AnalysisDetail.swift:764 (검사 예외: 같은 글)
            }
            // ios: Views/AnalysisDetail.swift:767,768
            Box(Modifier.size(36.dp, 36.dp).background(bordered, RoundedCornerShape(8.dp)).plainClickable { w = w + (id to max(0, (w[id] ?: 0) - 5)) }.semantics { contentDescription = "$name 비중 5% 줄이기" }, contentAlignment = Alignment.Center) {
                val s = "minus"; if (SF.containsKey(s)) Sym(s, 17f, Theme.teal) else Box(Modifier.size(14.dp, 2.dp).background(Theme.teal, CapsuleShape))   // SF 표에 아직 없으면 막대로
            }
            T("${w[id] ?: 0}%", Modifier.width(44.dp), style = appFont(14f, FontWeight.Bold).copy(textAlign = TextAlign.Center))   // ios: Views/AnalysisDetail.swift:769
            // ios: Views/AnalysisDetail.swift:770,771
            Box(Modifier.size(36.dp, 36.dp).background(bordered, RoundedCornerShape(8.dp)).plainClickable { w = w + (id to min(100, (w[id] ?: 0) + 5)) }.semantics { contentDescription = "$name 비중 5% 늘리기" }, contentAlignment = Alignment.Center) {
                Sym("plus", 17f, Theme.teal)
            }
        }
    }

    // ios: Views/AnalysisDetail.swift:677,741
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/AnalysisDetail.swift:678,739
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ios: Views/AnalysisDetail.swift:679
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("비중 바꾸면 언제 닿을까?", style = appFont(20f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:680
                T("목표 ${AppModel.wonK(m.gK)} · ${m.goalToText}까지", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:681
            }

            // ios: Views/AnalysisDetail.swift:684
            Card {
                // ios: Views/AnalysisDetail.swift:685
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("비중", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:686
                    T("처음으로", Modifier.plainClickable { w = w0; order = order.filter { w0[it] != null } }, style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:687
                }
                // ios: Views/AnalysisDetail.swift:689,699
                BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp).clip(CapsuleShape)) {
                    val bw = maxWidth
                    // ios: Views/AnalysisDetail.swift:690,695
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        // ios: Views/AnalysisDetail.swift:691
                        order.indices.forEach { i ->
                            key(order[i]) {
                                val frac by animateFloatAsState(((w[order[i]] ?: 0).toDouble() / max(100, sum).toDouble()).toFloat(), tween(250, easing = easeInOut), label = "w")   // ios: Views/AnalysisDetail.swift:693,697
                                Box(Modifier.fillMaxHeight().width(bw * frac).background(Theme.holdColors[i % Theme.holdColors.size]))   // ios: Views/AnalysisDetail.swift:692
                            }
                        }
                    }
                }
                order.indices.forEach { i -> row(order[i], color = Theme.holdColors[i % Theme.holdColors.size]) }   // ios: Views/AnalysisDetail.swift:700
                Box(Modifier.plainClickable { adding = true }.defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.CenterStart) { T("+ 종목 추가", style = appFont(14f, FontWeight.Bold), color = Theme.teal) }   // ios: Views/AnalysisDetail.swift:701,702
                Sheet(adding, onDismiss = { adding = false }) { CompareAddSheet(have = w.keys.toSet(), add = { id -> w = w + (id to 0); order = order + id }, dismiss = { adding = false }) }   // ios: Views/AnalysisDetail.swift:704,705
                val sumTxt = if (sum == 100) "합계 100%" else "합계 ${sum}% · 100%로 맞추기"   // ios: Views/AnalysisDetail.swift:707
                // ios: Views/AnalysisDetail.swift:708,709,710
                val ok = sum == 100; val tint = if (ok) Theme.teal else hexD(0x8A4614, 0xE8A060)
                T(sumTxt, Modifier.fillMaxWidth().background((if (ok) Theme.teal else Theme.orange).copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(10.dp), style = appFont(13f, FontWeight.Bold), color = tint)
            }

            // ios: Views/AnalysisDetail.swift:713
            Card {
                T("닿는 때", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:714
                EtaTicks(a0 = a0, a1 = a1)   // ios: Views/AnalysisDetail.swift:715
                EtaRow(name = "지금 비중", sub = "", sim = nowSim, a0 = a0, a1 = a1, goalYear = goalYear, strong = false)   // ios: Views/AnalysisDetail.swift:716
                EtaRow(name = "바꾼 비중", sub = "", sim = newSim, a0 = a0, a1 = a1, goalYear = goalYear, strong = true)   // ios: Views/AnalysisDetail.swift:717
                T("점선 = 목표일 ${gc.year}.${gc.monthValue} · 점 보통 · 막대 10번 중 8번", style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:718
            }

            // ios: Views/AnalysisDetail.swift:721
            Card {
                T(headline, style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:722
                T(spread, style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:723
            }

            // ios: Views/AnalysisDetail.swift:727,728
            val save = { m.saveBasket(w.filter { it.value > 0 }.mapValues { it.value.toDouble() }); dismiss() }
            // ios: Views/AnalysisDetail.swift:726,729,731,732,734
            Box(Modifier.fillMaxWidth().then(if (canSave) Modifier.plainClickable(onClick = save) else Modifier.semantics { disabled() }).defaultMinSize(minHeight = 52.dp).background(if (canSave) Theme.teal else Theme.border, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                T("저장", style = appFont(16f, FontWeight.Bold), color = if (canSave) Color.White else Theme.sub)   // ios: Views/AnalysisDetail.swift:730
            }
            T(if (m.pworld == null) "평행 세계를 열면 저장 가능" else "모의 바구니로 저장 · 실제 수량 그대로", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:735,736
            T("과거 수익률 기준 · 투자 권유 아님", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:737
        }
    }
}

// ios: Views/AnalysisDetail.swift:776,777,778
@Composable
private fun legend(c: Color, t: String) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp, 12.dp).background(c, RoundedCornerShape(3.dp))); T(t, style = appFont(12f), color = Theme.sub)
}
