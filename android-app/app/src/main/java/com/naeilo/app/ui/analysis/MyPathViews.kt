package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
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
import com.naeilo.app.ui.SheetTitleBar
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

/** 도달 시점 한 줄: 점 = 보통(가운데) 닿는 때, 막대 = 10번 중 8번(10~90%) 범위, 세로 점선 = 목표일. 위치는 연도 축 a0~a1 */
// ios: Views/AnalysisDetail.swift:209,216
@Composable
fun EtaRow(
    name: String,   // ios: Views/AnalysisDetail.swift:210
    sub: String,   // ios: Views/AnalysisDetail.swift:211
    sim: GoalSim,   // ios: Views/AnalysisDetail.swift:212
    a0: Double, a1: Double, goalYear: Double,   // ios: Views/AnalysisDetail.swift:213
    strong: Boolean = true,   // ios: Views/AnalysisDetail.swift:214
) {
    val x = { y: Double -> min(1.0, max(0.0, (y - a0) / max(0.5, a1 - a0))) }   // ios: Views/AnalysisDetail.swift:217
    val lo = x(AppModel.yearAt(sim.etaLo)); val hi = if (sim.etaHi.isFinite()) x(AppModel.yearAt(sim.etaHi)) else 1.0   // ios: Views/AnalysisDetail.swift:218
    val mid = if (sim.etaMonths.isFinite()) x(AppModel.yearAt(sim.etaMonths)) else 1.0; val gx = x(goalYear)   // ios: Views/AnalysisDetail.swift:219
    val c = if (strong) Theme.teal else Theme.muted   // ios: Views/AnalysisDetail.swift:220
    // ios: Views/AnalysisDetail.swift:236 (값이 바뀌면 점·막대가 미끄러진다)
    val ease = tween<Float>(250, easing = easeInOut)
    val loA by animateFloatAsState(lo.toFloat(), ease, label = "lo"); val hiA by animateFloatAsState(hi.toFloat(), ease, label = "hi")
    val midA by animateFloatAsState(mid.toFloat(), ease, label = "mid"); val gxA by animateFloatAsState(gx.toFloat(), ease, label = "gx")
    val tm = AppModel.whenText(sim.etaMonths, cap = sim.capYears); val tLo = AppModel.whenText(sim.etaLo, cap = sim.capYears); val tHi = AppModel.whenText(sim.etaHi, cap = sim.capYears)
    val label = "$name, 보통 $tm, 10번 중 8번은 ${tLo}에서 $tHi 사이"   // ios: Views/AnalysisDetail.swift:242
    // ios: Views/AnalysisDetail.swift:221,241
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = tr(label) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ios: Views/AnalysisDetail.swift:222,224
        Row(Modifier.fillMaxWidth()) {
            T(name, Modifier.weight(1f).alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:223
            T(tm, Modifier.alignByBaseline(), style = appFont(13f, FontWeight.SemiBold), color = c)   // ios: Views/AnalysisDetail.swift:225
        }
        val border = Theme.border; val ink = Theme.ink
        // ios: Views/AnalysisDetail.swift:227,228,229,238
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val w = maxWidth
            Box(Modifier.offset(y = 9.dp).size(w, 2.dp).background(border.copy(alpha = 0.5f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:230
            Box(Modifier.offset(x = w * loA, y = 7.dp).size(androidx.compose.ui.unit.max(6.dp, w * (hiA - loA)), 6.dp).background(c.copy(alpha = 0.35f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:231
            Box(Modifier.offset(x = w * midA - 6.dp, y = 4.dp).size(12.dp, 12.dp).background(c, CircleShape))   // ios: Views/AnalysisDetail.swift:232
            // ios: Views/AnalysisDetail.swift:233,234
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { val gpx = gxA * size.width; drawLine(ink, Offset(gpx, 0f), Offset(gpx, 20.dp.toPx()), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))) }
        }
        if (sub.isNotEmpty()) T(sub, style = appFont(11f), color = Theme.muted, maxLines = 2)   // ios: Views/AnalysisDetail.swift:239
    }
}

object EtaRow {
    /** 여러 줄을 감싸는 연도 축 (목표일 포함, 정수 해) */
    // ios: Views/AnalysisDetail.swift:246
    fun axis(sims: List<GoalSim>, goalYear: Double): Pair<Double, Double> {
        var lo = goalYear; var hi = goalYear   // ios: Views/AnalysisDetail.swift:247
        // ios: Views/AnalysisDetail.swift:248
        for (s in sims) {
            lo = min(lo, AppModel.yearAt(s.etaLo))   // ios: Views/AnalysisDetail.swift:249
            hi = max(hi, AppModel.yearAt(if (s.etaHi.isFinite()) s.etaHi else (s.capYears * 12).toDouble()))   // ios: Views/AnalysisDetail.swift:250
        }
        return Pair(floor(lo), max(floor(lo) + 2, ceil(hi)))   // ios: Views/AnalysisDetail.swift:252
    }
}

/** 연도 눈금 (a0~a1, 2년마다) */
// ios: Views/AnalysisDetail.swift:257,259
@Composable
fun EtaTicks(
    a0: Double, a1: Double,   // ios: Views/AnalysisDetail.swift:258
) {
    val step = if (a1 - a0 > 6) 2 else 1   // ios: Views/AnalysisDetail.swift:260
    val ys = (a0.toInt()..a1.toInt() step step).toList()   // ios: Views/AnalysisDetail.swift:261
    // ios: Views/AnalysisDetail.swift:262,268,269
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics {}) {
        val wd = maxWidth.value.toDouble()
        // ios: Views/AnalysisDetail.swift:263
        ys.forEach { y ->
            val cx = min(wd - 14, max(14.0, (y - a0) / max(0.5, a1 - a0) * wd)); val cy = 7.dp   // ios: Views/AnalysisDetail.swift:265
            T(y.toString(), Modifier.tickAt(cx.dp, cy), style = appFont(11f), color = Theme.muted, maxLines = 1)   // ios: Views/AnalysisDetail.swift:264
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
// ios: Views/AnalysisDetail.swift:460,470
@Composable
fun CompareAddSheet(
    have: Set<String>,   // ios: Views/AnalysisDetail.swift:461
    add: (String) -> Unit,   // ios: Views/AnalysisDetail.swift:462
    dismiss: () -> Unit,   // ios: Views/AnalysisDetail.swift:463
) {
    var query by remember { mutableStateOf("") }   // ios: Views/AnalysisDetail.swift:464
    var remote by remember { mutableStateOf(listOf<StockHit>()) }   // ios: Views/AnalysisDetail.swift:465
    var searching by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:466
    var loading by remember { mutableStateOf<String?>(null) }   // ios: Views/AnalysisDetail.swift:467
    var failed by remember { mutableStateOf<String?>(null) }   // ios: Views/AnalysisDetail.swift:468
    val scope = rememberCoroutineScope()
    val q = query.trim()   // ios: Views/AnalysisDetail.swift:471
    // 앱 안 목록 찾기는 딴 줄(Default)에서 (한국 상장 종목을 훑는다: 그리는 줄을 막지 않게)
    var local by remember { mutableStateOf(listOf<StockHit>()) }
    LaunchedEffect(q) { local = if (q.isEmpty()) emptyList() else withContext(Dispatchers.Default) { StockSearch.local(q) } }   // ios: Views/AnalysisDetail.swift:472
    val hits = (local + remote.filter { r -> local.none { it.id == r.id } }).filter { !have.contains(it.id) }   // ios: Views/AnalysisDetail.swift:473

    // ios: Views/AnalysisDetail.swift:508
    suspend fun search() {
        remote = emptyList()   // ios: Views/AnalysisDetail.swift:509
        val q0 = query.trim()   // ios: Views/AnalysisDetail.swift:510
        if (q0.isEmpty() || !q0.all { it.code < 128 }) { searching = false; return }   // ios: Views/AnalysisDetail.swift:511
        searching = true   // ios: Views/AnalysisDetail.swift:512
        delay(400)   // ios: Views/AnalysisDetail.swift:513
        if (!currentCoroutineContext().isActive) return   // ios: Views/AnalysisDetail.swift:514
        val r = StockSearch.remote(q0)   // ios: Views/AnalysisDetail.swift:515
        if (!currentCoroutineContext().isActive) return   // ios: Views/AnalysisDetail.swift:516
        remote = r; searching = false   // ios: Views/AnalysisDetail.swift:517
    }

    /** 사이트 표에 있으면 바로, 없으면 3년 종가를 받은 뒤 넣는다 */
    // ios: Views/AnalysisDetail.swift:521
    suspend fun pick(h: StockHit) {
        failed = null   // ios: Views/AnalysisDetail.swift:522
        if (Market.shared.stats?.tickers?.get(SiteStats.mcKey(h.id)) != null) { add(h.id); dismiss(); return }   // ios: Views/AnalysisDetail.swift:523
        loading = h.id   // ios: Views/AnalysisDetail.swift:524
        try {
            ExtraStats.shared.fetch(h); add(h.id); dismiss()   // ios: Views/AnalysisDetail.swift:526
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            failed = "${h.name} 시세 못 받음 · 다시 눌러 주세요"   // ios: Views/AnalysisDetail.swift:527
        } finally { loading = null }   // ios: Views/AnalysisDetail.swift:525
    }

    LaunchedEffect(query) { search() }   // ios: Views/AnalysisDetail.swift:491

    // ios: Views/AnalysisDetail.swift:474
    Column(Modifier.fillMaxSize()) {
        // ios: Views/AnalysisDetail.swift:492
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            T("종목 추가", Modifier.align(Alignment.Center), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            T("닫기", Modifier.align(Alignment.CenterStart).plainClickable { dismiss() }, style = TextStyle(fontSize = 17.sp), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:493
        }
        // ios: Views/AnalysisDetail.swift:490
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Theme.card, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            InputField(query, { query = it }, "이름·티커·종목 코드")
        }
        // ios: Views/AnalysisDetail.swift:475
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // ios: Views/AnalysisDetail.swift:476
            if (q.isEmpty()) {
                // ios: Views/AnalysisDetail.swift:477
                Parallel.universe.filter { !have.contains(it.id) }.forEach { u ->
                    SheetRow(onClick = { add(u.id); dismiss() }) { line(u.name, u.id, busy = false) }   // ios: Views/AnalysisDetail.swift:478
                }
            } else {   // ios: Views/AnalysisDetail.swift:480
                // ios: Views/AnalysisDetail.swift:481
                hits.forEach { h ->
                    // ios: Views/AnalysisDetail.swift:482,483
                    SheetRow(enabled = loading == null, onClick = { scope.launch { pick(h) } }) { line(h.name, h.id + " · " + h.market, busy = loading == h.id) }
                }
                // ios: Views/AnalysisDetail.swift:485
                if (searching) Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Spinner(); T("찾는 중", style = appFont(13f), color = Theme.sub) }
                else if (hits.isEmpty()) T("없음 · 이름·티커·종목 코드로", Modifier.padding(vertical = 10.dp), style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:486
            }
            failed?.let { T(it, Modifier.padding(vertical = 10.dp), style = appFont(13f), color = Theme.orange) }   // ios: Views/AnalysisDetail.swift:488
        }
    }
}

// ios: Views/AnalysisDetail.swift:497,498
@Composable
private fun line(name: String, sub: String, busy: Boolean) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    // ios: Views/AnalysisDetail.swift:499,503
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(name, style = appFont(15f, FontWeight.Bold), color = Theme.ink, maxLines = 1)   // ios: Views/AnalysisDetail.swift:500
        T(sub, style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:501
    }
    if (busy) Spinner()   // ios: Views/AnalysisDetail.swift:504
}

// MARK: 모의바구니 구성 (B-2안). 저장하면 모의바구니(담아 본 나)가 된다. 팝업(Sheet)으로 뜬다
// ios: Views/AnalysisDetail.swift:308
@Composable
fun MyPathCompareView(m: AppModel, dismiss: () -> Unit) {
    var w0 by remember { mutableStateOf(mapOf<String, Int>()) }   // ios: Views/AnalysisDetail.swift:536
    var w by remember { mutableStateOf(mapOf<String, Int>()) }   // ios: Views/AnalysisDetail.swift:537
    var order by remember { mutableStateOf(listOf<String>()) }   // ios: Views/AnalysisDetail.swift:538
    var loaded by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:539
    var adding by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:540

    val sum = w.values.fold(0) { a, x -> a + x }   // ios: Views/AnalysisDetail.swift:543
    val yrsE = m.etaYears   // ios: Views/AnalysisDetail.swift:544
    val nowSim = m.simulate(m.nowMix, years = yrsE)   // ios: Views/AnalysisDetail.swift:545
    // ios: Views/AnalysisDetail.swift:546
    val parts: List<Part> = order.mapNotNull { id ->
        val x = w[id]; if (x == null || x <= 0) return@mapNotNull null   // ios: Views/AnalysisDetail.swift:547
        val usd = m.rows.firstOrNull { it.id == id }?.let { it.sym.currency == Currency.usd } ?: ExtraStats.shared.usd(id) ?: true   // ios: Views/AnalysisDetail.swift:548
        Part(key = SiteStats.mcKey(id), ccy = if (usd) "USD" else "KRW", w = x.toDouble() / max(1, sum).toDouble())   // ios: Views/AnalysisDetail.swift:549
    }
    val newSim = if (w == w0 || parts.isEmpty()) nowSim else m.simulate(m.nowMix, years = yrsE, parts = parts)   // ios: Views/AnalysisDetail.swift:551
    val goalMo = max(0.0, secs(java.time.Instant.now(), m.goalToDate) / (365.25 * 86400) * 12)   // ios: Views/AnalysisDetail.swift:552
    val goalYear = AppModel.yearAt(goalMo)   // ios: Views/AnalysisDetail.swift:553
    val ax = EtaRow.axis(listOf(nowSim, newSim), goalYear = goalYear); val a0 = ax.first; val a1 = ax.second   // ios: Views/AnalysisDetail.swift:554
    val gc = m.goalToDate.atZone(ZoneId.systemDefault())   // ios: Views/AnalysisDetail.swift:555
    val canSave = sum == 100 && m.pworld != null && w.filter { it.value > 0 }.keys.all { Parallel.has(it) }   // ios: Views/AnalysisDetail.swift:556
    // ios: Views/AnalysisDetail.swift:557
    // 결과 문구: 얼마나 달라졌는지를 숫자로 (지금 비중 · 목표일과 비교)
    val cap = newSim.capYears; val eN = newSim.etaMonths; val e0 = nowSim.etaMonths   // ios: Views/AnalysisDetail.swift:333
    val whenT = AppModel.whenText(eN, cap = cap)   // ios: Views/AnalysisDetail.swift:334
    // ios: Views/AnalysisDetail.swift:335
    val headline: String = run {
        if (!eN.isFinite()) return@run "${cap}년 안에 닿기 어려워요"   // ios: Views/AnalysisDetail.swift:336
        if (!e0.isFinite()) return@run "지금보다 빨라요 · ${whenT}쯤"   // ios: Views/AnalysisDetail.swift:337
        val d = (eN - e0).swRound().toInt()   // ios: Views/AnalysisDetail.swift:338
        if (d == 0) return@run "지금과 거의 같아요 · ${whenT}쯤"   // ios: Views/AnalysisDetail.swift:339
        if (d < 0) "지금보다 ${span(-d)} 빨라요 · ${whenT}쯤" else "지금보다 ${span(d)} 늦어요 · ${whenT}쯤"   // ios: Views/AnalysisDetail.swift:340,341
    }
    // ios: Views/AnalysisDetail.swift:343
    val goalLine: String? = run {
        if (!eN.isFinite()) return@run null   // ios: Views/AnalysisDetail.swift:344
        val d = (eN - goalMo).swRound().toInt()   // ios: Views/AnalysisDetail.swift:345
        if (d == 0) return@run "목표일 즈음이에요"   // ios: Views/AnalysisDetail.swift:346
        if (d < 0) "목표일보다 ${span(-d)} 앞서요" else "목표일보다 ${span(d)} 늦어요"   // ios: Views/AnalysisDetail.swift:347
    }
    val sprN = newSim.etaHi - newSim.etaLo; val spr0 = nowSim.etaHi - nowSim.etaLo   // ios: Views/AnalysisDetail.swift:349 무한대 = 기간 안에 못 닿는 경우가 섞임
    // ios: Views/AnalysisDetail.swift:350
    val spread: String = run {
        val how = if (sprN.isFinite() && spr0.isFinite() && kotlin.math.abs(sprN - spr0) < 6 || !sprN.isFinite() && !spr0.isFinite()) "흔들림은 지금과 비슷해요"   // ios: Views/AnalysisDetail.swift:351
            else if (!sprN.isFinite() || (spr0.isFinite() && sprN > spr0)) "흔들림이 커져요" else "흔들림이 줄어요"   // ios: Views/AnalysisDetail.swift:352
        tr(how) + " · " + tr("빠르면 ${AppModel.whenText(newSim.etaLo, cap = cap)}, 늦으면 ${AppModel.whenText(newSim.etaHi, cap = cap)}")   // ios: Views/AnalysisDetail.swift:353
    }

    // ios: Views/AnalysisDetail.swift:633
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect   // ios: Views/AnalysisDetail.swift:634
        loaded = true   // ios: Views/AnalysisDetail.swift:635
        val t = max(1.0, m.rows.fold(0.0) { a, r -> a + r.value })   // ios: Views/AnalysisDetail.swift:636
        val x = LinkedHashMap<String, Int>()   // ios: Views/AnalysisDetail.swift:637
        for (r in m.rows) x[r.id] = (x[r.id] ?: 0) + (r.value / t * 100).swRound().toInt()   // ios: Views/AnalysisDetail.swift:638
        x.maxByOrNull { it.value }?.key?.let { top -> x[top] = (x[top] ?: 0) + 100 - x.values.fold(0) { a, v -> a + v } }   // ios: Views/AnalysisDetail.swift:639
        order = m.rows.map { it.id }.fold(listOf<String>()) { acc, id -> if (acc.contains(id)) acc else acc + id }   // ios: Views/AnalysisDetail.swift:640
        w0 = x.toMap(); w = x.toMap()   // ios: Views/AnalysisDetail.swift:641
    }

    // ios: Views/AnalysisDetail.swift:645
    @Composable
    fun row(id: String, color: Color) {
        val r = m.rows.firstOrNull { it.id == id }   // ios: Views/AnalysisDetail.swift:646
        val name = r?.sym?.name ?: Parallel.universe.firstOrNull { it.id == id }?.name ?: ExtraStats.shared.name(id) ?: id   // ios: Views/AnalysisDetail.swift:647
        val kind = r?.sym?.sectorText ?: "새로 담음"   // ios: Views/AnalysisDetail.swift:648
        val d = (w[id] ?: 0) - (w0[id] ?: 0)   // ios: Views/AnalysisDetail.swift:649
        val bordered = if (Theme.isDark) Color(0x3D767680) else Color(0x1F767680)   // .buttonStyle(.bordered) 바탕
        // ios: Views/AnalysisDetail.swift:650
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp, 12.dp).background(color, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:651
            // 종목 이름 옆에 분류·변화 (한 줄이라 줄 높이가 낮다)
            // ios: Views/AnalysisDetail.swift:448
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(name, Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/AnalysisDetail.swift:449
                val dTxt = if (d == 0) "그대로" else if (d > 0) "+${d}%p" else "${d}%p"
                T("$kind · $dTxt", Modifier.weight(1f, fill = false).alignByBaseline(), style = appFont(11f), color = Theme.muted, maxLines = 1)   // Views/AnalysisDetail.swift:450 (검사 예외: 같은 글)
            }
            // ios: Views/AnalysisDetail.swift:453,454
            Box(Modifier.size(30.dp, 28.dp).background(bordered, RoundedCornerShape(8.dp)).plainClickable { w = w + (id to max(0, (w[id] ?: 0) - 5)) }.semantics { contentDescription = tr("$name 비중 5% 줄이기") }, contentAlignment = Alignment.Center) {
                val s = "minus"; if (SF.containsKey(s)) Sym(s, 17f, Theme.teal) else Box(Modifier.size(14.dp, 2.dp).background(Theme.teal, CapsuleShape))   // SF 표에 아직 없으면 막대로
            }
            T("${w[id] ?: 0}%", Modifier.width(44.dp), style = appFont(14f, FontWeight.Bold).copy(textAlign = TextAlign.Center))   // ios: Views/AnalysisDetail.swift:659
            // ios: Views/AnalysisDetail.swift:456,457
            Box(Modifier.size(30.dp, 28.dp).background(bordered, RoundedCornerShape(8.dp)).plainClickable { w = w + (id to min(100, (w[id] ?: 0) + 5)) }.semantics { contentDescription = tr("$name 비중 5% 늘리기") }, contentAlignment = Alignment.Center) {
                Sym("plus", 17f, Theme.teal)
            }
        }
    }

    // ios: Views/AnalysisDetail.swift:356,357
    Column(Modifier.fillMaxSize().background(Theme.bg)) {
    SheetTitleBar("모의바구니 구성", close = dismiss)   // ios: Views/AnalysisDetail.swift:357
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ios: Views/AnalysisDetail.swift:574
            Card {
                // ios: Views/AnalysisDetail.swift:575
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("비중", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:576
                    T("처음으로", Modifier.plainClickable { w = w0; order = order.filter { w0[it] != null } }, style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:577
                }
                // ios: Views/AnalysisDetail.swift:579,589
                BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp).clip(CapsuleShape)) {
                    val bw = maxWidth
                    // ios: Views/AnalysisDetail.swift:580,585
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                        // ios: Views/AnalysisDetail.swift:581
                        order.indices.forEach { i ->
                            key(order[i]) {
                                val frac by animateFloatAsState(((w[order[i]] ?: 0).toDouble() / max(100, sum).toDouble()).toFloat(), tween(250, easing = easeInOut), label = "w")   // ios: Views/AnalysisDetail.swift:583,587
                                Box(Modifier.fillMaxHeight().width(bw * frac).background(Theme.holdColors[i % Theme.holdColors.size]))   // ios: Views/AnalysisDetail.swift:582
                            }
                        }
                    }
                }
                order.indices.forEach { i -> row(order[i], color = Theme.holdColors[i % Theme.holdColors.size]) }   // ios: Views/AnalysisDetail.swift:590
                Box(Modifier.plainClickable { adding = true }.defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.CenterStart) { T("+ 종목 추가", style = appFont(14f, FontWeight.Bold), color = Theme.teal) }   // ios: Views/AnalysisDetail.swift:591,592
                Sheet(adding, onDismiss = { adding = false }) { CompareAddSheet(have = w.keys.toSet(), add = { id -> w = w + (id to 0); order = order + id }, dismiss = { adding = false }) }   // ios: Views/AnalysisDetail.swift:594,595
                val sumTxt = if (sum == 100) "합계 100%" else "합계 ${sum}% · 100%로 맞추기"   // ios: Views/AnalysisDetail.swift:597
                // ios: Views/AnalysisDetail.swift:598,599,600
                val ok = sum == 100; val tint = if (ok) Theme.teal else hexD(0x8A4614, 0xE8A060)
                T(sumTxt, Modifier.fillMaxWidth().background((if (ok) Theme.teal else Theme.orange).copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(10.dp), style = appFont(13f, FontWeight.Bold), color = tint)
            }

            // ios: Views/AnalysisDetail.swift:603
            Card {
                T("닿는 때", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:604
                EtaTicks(a0 = a0, a1 = a1)   // ios: Views/AnalysisDetail.swift:605
                EtaRow(name = "지금 비중", sub = "", sim = nowSim, a0 = a0, a1 = a1, goalYear = goalYear, strong = false)   // ios: Views/AnalysisDetail.swift:606
                EtaRow(name = "바꾼 비중", sub = "", sim = newSim, a0 = a0, a1 = a1, goalYear = goalYear, strong = true)   // ios: Views/AnalysisDetail.swift:607
                T("점선 = 목표일 ${gc.year}.${gc.monthValue} · 점 보통 · 막대 10번 중 8번", style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:608
            }

            // ios: Views/AnalysisDetail.swift:611
            Card {
                T(headline, style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:398
                if (goalLine != null) T(goalLine, style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:399
                T(spread, style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:613
            }

            // ios: Views/AnalysisDetail.swift:617,618
            val save = { m.saveBasket(w.filter { it.value > 0 }.mapValues { it.value.toDouble() }); dismiss() }
            // ios: Views/AnalysisDetail.swift:616,619,621,622,624
            Box(Modifier.fillMaxWidth().then(if (canSave) Modifier.plainClickable(onClick = save) else Modifier.semantics { disabled() }).defaultMinSize(minHeight = 52.dp).background(if (canSave) Theme.teal else Theme.border, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                T("저장", style = appFont(16f, FontWeight.Bold), color = if (canSave) Color.White else Theme.sub)   // ios: Views/AnalysisDetail.swift:620
            }
            if (m.pworld == null) T("평행 세계를 열면 저장 가능", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:412,413
            T("과거 수익률 기준 · 투자 권유 아님", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:415
        }
    }
    }
}

/** 개월 수를 "8개월" · "1년 3개월"로 */
// ios: Views/AnalysisDetail.swift:434
private fun span(mo: Int): String =
    if (mo < 12) tr("${mo}개월") else if (mo % 12 == 0) tr("${mo / 12}년") else tr("${mo / 12}년 ${mo % 12}개월")

// ios: Views/AnalysisDetail.swift:666,667,668
@Composable
private fun legend(c: Color, t: String) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp, 12.dp).background(c, RoundedCornerShape(3.dp))); T(t, style = appFont(12f), color = Theme.sub)
}
