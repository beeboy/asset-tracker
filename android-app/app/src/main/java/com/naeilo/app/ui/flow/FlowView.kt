package com.naeilo.app.ui.flow

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.selected
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.home.BoxSchemeScope
import com.naeilo.app.ui.home.boxInk
import com.naeilo.app.ui.home.bx
import com.naeilo.app.ui.home.FreshLabel
import com.naeilo.app.ui.hold.FitAnnotated
import com.naeilo.app.ui.hold.MoodBand
import com.naeilo.app.ui.hold.TodayBox
import com.naeilo.app.ui.hold.moodBackdrop
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.naeilo.app.core.tr
import com.naeilo.app.ui.rememberPrefBool
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.AlpacaKeys
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.cformat
import com.naeilo.app.data.FlowLevel
import com.naeilo.app.data.FlowPattern
import com.naeilo.app.data.FlowSeries
import com.naeilo.app.data.FlowStore
import com.naeilo.app.data.FlowTab
import com.naeilo.app.data.Sample
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.Market
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.SheetBar
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.settings.SettingsRoute
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 차트 탭: 내 종목 흐름을 겹(추세 / 지지·저항 / 큰 패턴 / 캔들 패턴)으로 켜고 끈다.
// 키 없는 사용자는 샘플 종목 DRNK 만 그린다. 정보 제공용 (매매 권유 아님)
@Composable
fun FlowView(model: AppModel) {   // ios: Views/FlowView.swift:5,6
    var sel by remember { mutableStateOf<String?>(null) }   // ios: Views/FlowView.swift:7
    val lookRaw = BoxLook.raw   // ios: Views/FlowView.swift:8
    var showAll by remember { mutableStateOf(false) }   // ios: Views/FlowView.swift:8
    var layers by remember { mutableStateOf(setOf(FlowLayer.trend)) }   // ios: Views/FlowView.swift:9
    var term by remember { mutableStateOf<FlowTerm?>(null) }   // ios: Views/FlowView.swift:10
    var showGuide by remember { mutableStateOf(false) }   // ios: Views/FlowView.swift:11
    var guideFocus by remember { mutableStateOf<String?>(null) }   // ios: Views/FlowView.swift:12

    /** 차트 실데이터를 받을 키 (토스·알파카) */
    // 키를 넣고 받기를 마치면(tossDone) 다시 그린다   // ios: Views/FlowView.swift:15,16
    val hasKey = Market.shared.tossDone.let { TossKeys.on || AlpacaKeys.on }   // ios: Views/FlowView.swift:17
    /** 칩 줄: 키가 없으면 샘플 DRNK 를 맨 앞에, 키가 있으면 DRNK 는 빼고 내 종목만 */
    val ids = run {   // ios: Views/FlowView.swift:20
        val out = if (hasKey) mutableListOf() else mutableListOf("DRNK")   // ios: Views/FlowView.swift:21
        for (h in model.holdings) if (h.symbol !in out && h.symbol != KoreaEstimate.sumId && !Sample.isVirtual(h.symbol)) out.add(h.symbol)   // ios: Views/FlowView.swift:22
        out   // ios: Views/FlowView.swift:23
    }
    val store = FlowStore   // ios: Views/FlowView.swift:54
    /** 평가액 (원). 샘플 DRNK 는 담지 않았어도 1 */
    fun weight(id: String): Double = model.rows.firstOrNull { it.id == id }?.value ?: (if (id == "DRNK") 1.0 else 0.0)   // ios: Views/FlowView.swift:32
    /** 비중 큰 순서 */
    val byWeight: List<String> = ids.withIndex().sortedWith(compareByDescending<IndexedValue<String>> { weight(it.value) }.thenBy { it.index }).map { it.value }   // ios: Views/FlowView.swift:34
    val mood = flowMood(byWeight, ::weight) { store.peek(it) }   // ios: Views/FlowView.swift:40
    /** 아래 칩 줄: 큰 흐름 박스에 없는 종목만 비중 순 */
    val restIds = byWeight.filter { it !in mood.box }   // ios: Views/FlowView.swift:36
    /** 고른 종목이 없거나 칩 줄에서 빠졌으면(키를 넣어 DRNK 가 숨는 등) 큰 흐름 박스의 첫 종목 */
    // ios: Views/FlowView.swift:27,28,29,30
    val cur: String? = sel?.takeIf { it in ids } ?: mood.box.firstOrNull() ?: byWeight.firstOrNull()
    fun name(id: String): String = Sample.symbol(id)?.name ?: id   // ios: Views/FlowView.swift:53
    /** 키가 없어 못 그리는 종목이 있으면 설정 링크를 보인다 */

    // 열 때 담은 종목 일봉을 받는다 (하루 한 번만 실제로 부른다)
    LaunchedEffect(hasKey) { store.ensure(ids) }   // ios: Views/FlowView.swift:65
    // 추세·패턴 계산은 화면 그리기 밖에서 (받은 캔들이 바뀌면 다시). 그리는 쪽은 계산해 둔 것만 꺼내 쓴다
    LaunchedEffect(ids, store.toss, store.alpaca, Market.shared.updatedAt, Market.shared.toss, Market.shared.alpaca) { store.warm(ids) }   // 지금 가격이 바뀌면 장중 오늘 캔들도 다시

    // ios: Views/FlowView.swift:32,33
    // 당기면 다른 탭처럼 시세를 받고, 일봉도 다시 묻는다 (장이 끝나 새 날이 생겼을 때만 실제로 받는다)
    Refreshable({ Market.shared.pullRefresh(model); store.ensure(ids) }, Modifier.fillMaxSize().background(Theme.bg)) {   // ios: Views/FlowView.swift:67
        BoxWithConstraints(Modifier.fillMaxSize()) {   // ios: Views/FlowView.swift:57
        val vh = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ios: Views/FlowView.swift:88 (.padding(.bottom, -12): 아래 여백 24 를 12 로)
            Screen(Modifier.layout { m, c -> val p = m.measure(c); layout(p.width, max(0, p.height - 12.dp.roundToPx())) { p.place(0, 0) } }) {
                // 차트는 아래 탭 바 바로 위까지 (화면이 작아도 300 은 둔다)
                // ios: Views/FlowView.swift:58,84,89
                FillLast(minTotal = vh - 12.dp, minLast = 300.dp, gap = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/FlowView.swift:59
                    Box(Modifier.outset(16.dp)) { AppHeader(tab = FlowTab.name) }   // ios: Views/FlowView.swift:60 (-16)
                    if (mood.box.isNotEmpty()) BoxSchemeScope { MoodBox(mood, BoxLook.entries.firstOrNull { it.name == lookRaw } ?: BoxLook.jump, cur, ::name) { sel = it; term = null } }   // ios: Views/FlowView.swift:61
                    if (restIds.isNotEmpty()) Chips(restIds, cur, ::name, onAll = { showAll = true }) { sel = it; term = null }   // ios: Views/FlowView.swift:62
                    LayerRow(layers, onGuide = { guideFocus = null; showGuide = true }) { layers = it }   // ios: Views/FlowView.swift:63
                }
                    // ios: Views/FlowView.swift:64,84,85
                    Box(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp))) {
                        val s = cur?.let { store.peek(it) }
                        if (cur != null && s != null) {   // ios: Views/FlowView.swift:46
                            // ios: Views/FlowView.swift:48 (.id(sel): 종목이 바뀌면 확대·밀기 상태를 새로)
                            androidx.compose.runtime.key(cur) { FlowChart(series = s, title = if (cur == "DRNK") "DRNK · 샘플" else name(cur), layers = layers, term = term, setTerm = { term = it }, more = { t -> guideFocus = t.key; showGuide = true }) }   // ios: Views/FlowView.swift:47
                        } else if (cur != null && store.bars(cur) != null) {
                            // 캔들은 있고 계산하는 중: 빈 카드 (잠깐)
                        } else if (cur == null) {   // ios: Views/FlowView.swift:49
                            T("종목을 담으면 흐름이 보여요", Modifier.align(Alignment.Center), style = appFont(14f), color = Theme.sub)   // ios: Views/FlowView.swift:50,51
                        } else {   // ios: Views/FlowView.swift:52
                            // ios: Views/FlowView.swift:53,59
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                T(name(cur), style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/FlowView.swift:54
                                T(if (!FlowStore.canFetch(cur)) "키를 넣으면 이 종목 차트가 그려져요"   // ios: Views/FlowView.swift:55
                                  else if (cur in store.loading) "일봉을 받는 중이에요"   // ios: Views/FlowView.swift:56
                                  else if (cur in store.failed) "일봉을 못 받았어요"   // ios: Views/FlowView.swift:55
                                  else "아직 받은 일봉이 없어요",   // ios: Views/FlowView.swift:56
                                  style = appFont(13f), color = Theme.sub)   // ios: Views/FlowView.swift:57
                            }
                        }
                    }
                }
            }
        }
        }
    }
    // ios: Views/FlowView.swift:96
    AllSheet(showAll, byWeight, cur, ::name, onDismiss = { showAll = false }) { sel = it; term = null; showAll = false }
    Sheet(showGuide, onDismiss = { showGuide = false }) { FlowGuide(guideFocus) { showGuide = false } }   // ios: Views/FlowView.swift:63
}

/** 위 묶음 + 마지막 칸: 합이 minTotal 이 되게 마지막 칸을 늘린다 (minLast 보다 작게는 안 줄인다). SwiftUI maxHeight: .infinity + frame(minHeight:) 자리 */
@Composable
private fun FillLast(minTotal: Dp, minLast: Dp, gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { ms, c ->
        val head = ms[0].measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val g = gap.roundToPx()
        val h = max(minLast.roundToPx(), minTotal.roundToPx() - head.height - g)
        val last = ms[1].measure(Constraints.fixed(c.maxWidth, h))
        layout(c.maxWidth, head.height + g + h) { head.place(0, 0); last.place(0, head.height + g) }
    }
}

/** 큰 흐름 박스 값: 종목마다 세 흐름 마지막 방향(+1·0·-1)을 출렁임에 반비례한 무게로 더해 점수를 내고, 종목 수로 나눈다 (평가액은 안 씀) */
// ios: Views/FlowView.swift:39
class Mood(val shares: List<Double> = listOf(0.0, 0.0, 0.0), val side: Int = 0, val strong: Boolean = false, val box: List<String> = emptyList(), val net: Double = 0.0)

// ios: Views/FlowView.swift:40
@Suppress("UNUSED_PARAMETER")
private fun flowMood(byWeight: List<String>, weight: (String) -> Double, series: (String) -> FlowSeries?): Mood {
    // 무게 = 작은 6% · 중간 12% · 큰 22% 의 역수 비율 (작은 56% · 중간 28% · 큰 15%)
    val k = listOf(1 / 0.06, 1 / 0.12, 1 / 0.22); val ks = k.sum()   // ios: Views/FlowView.swift:42
    // ios: Views/FlowView.swift:43,44,45,46
    val items: List<Pair<String, Double>> = byWeight.mapNotNull { id ->
        val d = (series(id)?.trends ?: emptyList()).mapNotNull { ts -> ts.lastOrNull()?.let { if (it.dir > 0) 1.0 else if (it.dir < 0) -1.0 else 0.0 } }
        if (d.size == k.size) id to d.indices.sumOf { d[it] * k[it] } / ks else null
    }
    if (items.isEmpty()) return Mood()   // ios: Views/FlowView.swift:47
    val n = items.size.toDouble(); val up = items.sumOf { maxOf(it.second, 0.0) } / n; val dn = items.sumOf { maxOf(-it.second, 0.0) } / n   // ios: Views/FlowView.swift:48
    // 점수 평균이 0 에 가까우면 횡보. 박스 버튼 = 같은 쪽 점수 종목 (횡보면 전부)
    val net = up - dn; val side = if (abs(net) < 0.005) 0 else if (net > 0) 1 else -1   // ios: Views/FlowView.swift:50
    return Mood(listOf(up, maxOf(0.0, 1 - up - dn), dn), side, abs(net) > 0.5, items.filter { side == 0 || it.second * side > 0 }.map { it.first }, abs(net))   // ios: Views/FlowView.swift:51
}

// 맨 위 큰 흐름 박스: 홈에서 고른 박스 모양. 큰 글자 = 종목 점수 평균의 방향·크기 (절반 넘으면 강한), 버튼 = 그쪽 종목만
// ios: Views/FlowView.swift:103
@Composable
private fun MoodBox(md: Mood, look: BoxLook, cur: String?, name: (String) -> String, onPick: (String) -> Unit) {
    val c = if (md.side > 0) TodayBox.upC else if (md.side < 0) TodayBox.downC else TodayBox.dim   // ios: Views/FlowView.swift:105
    // ios: Views/FlowView.swift:106,107,108
    val head = if (md.side == 0) tr("횡보")
        else if (md.side > 0) (if (md.strong) tr("강한 상승") else tr("약한 상승"))
        else (if (md.strong) tr("강한 하락") else tr("약한 하락"))
    val share = md.net   // ios: Views/FlowView.swift:109
    // ios: Views/FlowView.swift:110,123,124,125,126
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).moodBackdrop(look).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp)) {
        // ios: Views/FlowView.swift:111
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("내 종목 · 최근 흐름", Modifier.weight(1f), style = appFont(13f), color = TodayBox.dim); FreshLabel() }
        // ios: Views/FlowView.swift:112,113,114
        FitAnnotated(buildAnnotatedString {
            withStyle(SpanStyle(color = boxInk().ink)) { append("$head · ") }
            withStyle(SpanStyle(color = c)) { append(AppModel.pct(share)) }
        }, TextStyle(fontSize = fx(30f), fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), 0.6f, boxInk().ink, Modifier.padding(top = 6.dp))
        Box(Modifier.padding(top = 14.dp)) { MoodBand(look, md.shares, road = 10.dp, up = md.side >= 0) }   // ios: Views/FlowView.swift:115
        // ios: Views/FlowView.swift:116,117,118,119,120,121 (오른쪽 끝 16% 를 흐리게)
        Box(Modifier.outset(18.dp).padding(top = 10.dp).fadeEnd(0.84f)) {   // ios: Views/FlowView.swift:122
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { md.box.forEach { BoxChip(it, c, cur, name, onPick) } }
        }
    }
}

// ios: Views/FlowView.swift:129
@Composable
private fun BoxChip(id: String, dot: Color, cur: String?, name: (String) -> String, onPick: (String) -> Unit) {
    val on = cur == id   // ios: Views/FlowView.swift:130
    val ink = boxInk()
    // ios: Views/FlowView.swift:131,132,136,137,138,140,141
    Row(
        Modifier.heightIn(min = 24.dp).clip(RoundedCornerShape(50)).background(bx(ink.dk, 0xFFFFFF, 0xFFFFFF, 0.55f, 0f))
            .border(1.dp, if (on) ink.ink else bx(ink.dk, 0x16181D, 0xFFFFFF, 0.16f, 0.22f), RoundedCornerShape(50))   // ios: Views/FlowView.swift:139,140
            .semantics { if (on) selected = true }.plainClickable { onPick(id) }.padding(horizontal = 10.dp),   // 알약은 작게: 박스 높이를 종목 탭 박스에 맞춘다 (2026-10-27)
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, androidx.compose.foundation.shape.CircleShape))   // ios: Views/FlowView.swift:133
        // ios: Views/FlowView.swift:134,136
        T(if (id == "DRNK") "DRNK" else name(id), style = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) ink.ink else ink.sub, maxLines = 1)
    }
}

/** 글자 크기 고정 (.font(.system(size:)) 자리: 손쉬운 사용 크기를 따르지 않는다) */
@Composable
private fun fx(size: Float): androidx.compose.ui.unit.TextUnit = (size / LocalDensity.current.fontScale).sp

/** 오른쪽 끝을 흐리게 지워 잘린 칩이 '더 있음'으로 녹아들게 */
// ios: Views/FlowView.swift:77
private fun Modifier.fadeEnd(from: Float = 0.9f): Modifier = this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent { drawContent(); drawRect(Brush.horizontalGradient(from to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn) }

// 큰 흐름 박스 밖 내 종목 칩 줄 + '전체'
@Composable
private fun Chips(ids: List<String>, cur: String?, name: (String) -> String, onAll: () -> Unit, onPick: (String) -> Unit) {   // ios: Views/FlowView.swift:71
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/FlowView.swift:72
        // ios: Views/FlowView.swift:73,74
        Box(Modifier.weight(1f).fadeEnd()) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ids.forEach { Chip(it, cur, name, onPick) } } }
        // ios: Views/FlowView.swift:78,79
        T("전체", Modifier.heightIn(min = 36.dp).plainClickable(onAll).padding(vertical = 8.dp), style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)
    }
}

@Composable
private fun Chip(id: String, cur: String?, name: (String) -> String, onPick: (String) -> Unit) {   // ios: Views/FlowView.swift:83
    val on = cur == id; val locked = id != "DRNK" && !FlowStore.canFetch(id)   // ios: Views/FlowView.swift:84
    val fg = if (on) Color.White else Theme.ink   // ios: Views/FlowView.swift:91
    // ios: Views/FlowView.swift:85,86,92,93,94,96
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (on) Theme.teal else Theme.card)
            .border(1.dp, if (on) Color.Transparent else Theme.border, RoundedCornerShape(50))
            .plainClickable { onPick(id) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T(if (id == "DRNK") "DRNK" else name(id), style = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), color = fg, maxLines = 1)   // ios: Views/FlowView.swift:87
        if (id == "DRNK") T("샘플", style = appFont(10f), color = if (on) Color.White.copy(alpha = 0.8f) else Theme.muted)   // ios: Views/FlowView.swift:88
        if (locked) T("키 필요", style = appFont(10f), color = if (on) Color.White.copy(alpha = 0.8f) else Theme.muted)   // ios: Views/FlowView.swift:89
    }
}

@Composable
private fun AllSheet(show: Boolean, ids: List<String>, cur: String?, name: (String) -> String, onDismiss: () -> Unit, onPick: (String) -> Unit) {   // ios: Views/FlowView.swift:99
    // ios: Views/FlowView.swift:100,115
    Sheet(show, onDismiss, medium = true) {
        SheetBar("전체 종목", "닫기", onClick = onDismiss)   // ios: Views/FlowView.swift:112,113
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {   // ios: Views/FlowView.swift:101
            ids.forEach { id ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Theme.card).plainClickable { onPick(id) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/FlowView.swift:102,103
                    T(if (id == "DRNK") "DRNK · " + name(id) else name(id), color = Theme.ink)   // ios: Views/FlowView.swift:104
                    Spacer(Modifier.weight(1f))   // ios: Views/FlowView.swift:105
                    if (id == "DRNK") T("샘플", style = appFont(12f), color = Theme.muted)   // ios: Views/FlowView.swift:106
                    else if (!FlowStore.canFetch(id)) T("키 필요", style = appFont(12f), color = Theme.muted)   // ios: Views/FlowView.swift:107
                    if (cur == id) Sym("checkmark", 17f, Theme.teal, Modifier.padding(start = 8.dp))   // ios: Views/FlowView.swift:108
                }
            }
        }
    }
}

// 겹 켜고 끄기. 칸 크기는 늘 같고 색만 바뀐다
@Composable
private fun LayerRow(layers: Set<FlowLayer>, onGuide: () -> Unit, set: (Set<FlowLayer>) -> Unit) {   // ios: Views/FlowView.swift:119
    val all = layers.size == FlowLayer.entries.size   // ios: Views/FlowView.swift:120
    // ios: Views/FlowView.swift:121,122
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowLayer.entries.forEach { l ->   // ios: Views/FlowView.swift:123
            val on = l in layers   // ios: Views/FlowView.swift:124
            // ios: Views/FlowView.swift:125,126,127,130,131,132,134 (겹 투명도는 FlowChart 가 같은 0.2초로 바꾼다)
            val bg by animateColorAsState(if (on) Theme.tealBg else Theme.card, tween(200), label = "layerBg")
            Box(Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                .border(1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(10.dp))
                .plainClickable { set(if (on) layers - l else layers + l) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                T(l.title, style = appFont(13f, FontWeight.SemiBold), color = if (on) Theme.tealDark else Theme.sub)   // ios: Views/FlowView.swift:128,129
            }
        }
        // ios: Views/FlowView.swift:136,137,138,144,146
        Box(Modifier.heightIn(min = 34.dp).plainClickable { set(if (all) setOf(FlowLayer.trend) else FlowLayer.entries.toSet()) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            // 두 글자 폭을 같게 두어 바뀔 때 줄이 흔들리지 않게
            val st = appFont(13f, FontWeight.SemiBold)   // ios: Views/FlowView.swift:140,144
            val aOn by animateFloatAsState(if (all) 0f else 1f, tween(200), label = "allOn"); T("모두 켜기", Modifier.graphicsLayer { alpha = aOn }, style = st, color = Theme.teal)   // ios: Views/FlowView.swift:137,141
            val aOff by animateFloatAsState(if (all) 1f else 0f, tween(200), label = "allOff"); T("추세만", Modifier.graphicsLayer { alpha = aOff }, style = st, color = Theme.teal)   // ios: Views/FlowView.swift:142
        }
        // ios: Views/FlowView.swift:147,148,149,151,152
        Box(Modifier.size(34.dp, 34.dp).plainClickable(onGuide).semantics { contentDescription = tr("흐름 읽는 법") }, contentAlignment = Alignment.Center) {
            Sym("questionmark.circle", 18f, Theme.teal)
        }
    }
}

enum class FlowLayer {   // ios: Views/FlowView.swift:158
    trend, levels, big, candle;   // ios: Views/FlowView.swift:159
    val title: String get() = when (this) {   // ios: Views/FlowView.swift:160,161
        trend -> "추세"   // ios: Views/FlowView.swift:162
        levels -> "지지·저항"   // ios: Views/FlowView.swift:163
        big -> "큰 패턴"   // ios: Views/FlowView.swift:164
        candle -> "캔들 패턴"   // ios: Views/FlowView.swift:165
    }
}

/** 설명 칸에 띄울 용어 */
sealed class FlowTerm {   // ios: Views/FlowView.swift:171
    // ios: Views/FlowView.swift:172
    object dots : FlowTerm(); object band : FlowTerm(); object support : FlowTerm(); object resistance : FlowTerm(); data class pattern(val k: FlowPattern.Kind) : FlowTerm()
    /** 설명 페이지에서 그 줄로 바로 가는 이름 */
    // ios: Views/FlowView.swift:174,175
    val key: String get() = when (this) {
        dots -> "dots"   // ios: Views/FlowView.swift:176
        band -> "band"   // ios: Views/FlowView.swift:177
        support -> "support"   // ios: Views/FlowView.swift:178
        resistance -> "resistance"   // ios: Views/FlowView.swift:179
        is pattern -> k.name   // ios: Views/FlowView.swift:180
    }
    val title: String get() = when (this) {   // ios: Views/FlowView.swift:183,184
        dots -> "추세 점"   // ios: Views/FlowView.swift:185
        band -> "추세 띠"   // ios: Views/FlowView.swift:186
        support -> "지지선"   // ios: Views/FlowView.swift:187
        resistance -> "저항선"   // ios: Views/FlowView.swift:188
        is pattern -> k.title   // ios: Views/FlowView.swift:189
    }
    val body: String get() = when (this) {   // ios: Views/FlowView.swift:192,193
        dots -> "고점·저점 표시 · 계단 위로 상승 · 아래로 하락 · 같은 높이 횡보"   // ios: Views/FlowView.swift:194
        band -> "큰 흐름 · 22% 넘는 출렁임만 셈\n중간 흐름 · 12% 넘는 출렁임\n작은 흐름 · 6% 넘는 출렁임\n빨강 상승 · 파랑 하락 · 회색 횡보"   // ios: Views/FlowView.swift:195
        support -> "여러 번 내려와 받친 가격 · 많이 닿을수록 진하게"   // ios: Views/FlowView.swift:196
        resistance -> "여러 번 올라와 막힌 가격 · 많이 닿을수록 진하게"   // ios: Views/FlowView.swift:197
        is pattern -> k.body   // ios: Views/FlowView.swift:198
    }
}

// ios: Views/FlowView.swift:203,204,205
val FlowPattern.Kind.title: String get() = when (this) {
    FlowPattern.Kind.doubleBottom -> "더블바텀"   // ios: Views/FlowView.swift:206
    FlowPattern.Kind.doubleTop -> "더블탑"   // ios: Views/FlowView.swift:207
    FlowPattern.Kind.headShoulders -> "헤드앤숄더"   // ios: Views/FlowView.swift:208
    FlowPattern.Kind.ascTriangle -> "상승삼각형"   // ios: Views/FlowView.swift:209
    FlowPattern.Kind.descTriangle -> "하락삼각형"   // ios: Views/FlowView.swift:210
    FlowPattern.Kind.bullFlag, FlowPattern.Kind.bearFlag -> "플래그"   // ios: Views/FlowView.swift:211
    FlowPattern.Kind.hammer -> "해머"   // ios: Views/FlowView.swift:212
    FlowPattern.Kind.shootingStar -> "유성"   // ios: Views/FlowView.swift:213
    FlowPattern.Kind.bullEngulf -> "강세 포위형"   // ios: Views/FlowView.swift:214
    FlowPattern.Kind.bearEngulf -> "약세 포위형"   // ios: Views/FlowView.swift:215
    FlowPattern.Kind.insideBar -> "인사이드바"   // ios: Views/FlowView.swift:216
}
// ios: Views/FlowView.swift:219,220
val FlowPattern.Kind.body: String get() = when (this) {
    FlowPattern.Kind.doubleBottom -> "비슷한 바닥 두 번 (W) · 사이 고점 넘는 종가에 확정"   // ios: Views/FlowView.swift:221
    FlowPattern.Kind.doubleTop -> "비슷한 꼭대기 두 번 (M) · 사이 저점 깨는 종가에 확정"   // ios: Views/FlowView.swift:222
    FlowPattern.Kind.headShoulders -> "가운데가 가장 높은 세 봉우리 · 두 골짜기 선 깨면 확정"   // ios: Views/FlowView.swift:223
    FlowPattern.Kind.ascTriangle -> "꼭대기 같고 바닥은 높아짐 · 꼭대기 넘는 종가에 확정"   // ios: Views/FlowView.swift:224
    FlowPattern.Kind.descTriangle -> "바닥 같고 꼭대기는 낮아짐 · 바닥 깨는 종가에 확정"   // ios: Views/FlowView.swift:225
    FlowPattern.Kind.bullFlag -> "급등(깃대) 뒤 쉬어 가기 · 위로 벗어나면 확정"   // ios: Views/FlowView.swift:226
    FlowPattern.Kind.bearFlag -> "급락(깃대) 뒤 쉬어 가기 · 아래로 벗어나면 확정"   // ios: Views/FlowView.swift:227
    FlowPattern.Kind.hammer -> "아래꼬리가 긴 캔들 · 밀렸다 끌어올린 날"   // ios: Views/FlowView.swift:228
    FlowPattern.Kind.shootingStar -> "위꼬리가 긴 캔들 · 올랐다 밀려난 날"   // ios: Views/FlowView.swift:229
    FlowPattern.Kind.bullEngulf -> "전날 음봉을 양봉이 다 덮음"   // ios: Views/FlowView.swift:230
    FlowPattern.Kind.bearEngulf -> "전날 양봉을 음봉이 다 덮음"   // ios: Views/FlowView.swift:231
    FlowPattern.Kind.insideBar -> "전날 범위 안에서만 움직인 날 · 잠시 멈춤"   // ios: Views/FlowView.swift:232
}

/** 흐름 읽는 법 '이럴 때': 흔히 생기는 장면 한 줄 (널리 쓰이는 차트 읽기 지식, 특정 종목 사례 아님) */
// ios: Views/FlowView.swift:311,312
val FlowPattern.Kind.`when`: String get() = when (this) {
    FlowPattern.Kind.doubleBottom -> "실적 쇼크 급락 뒤 · 첫 바닥 근처에서 또 사들임"   // ios: Views/FlowView.swift:313
    FlowPattern.Kind.doubleTop -> "신고가 뒤 차익 매물 · 같은 가격에서 또 막힘"   // ios: Views/FlowView.swift:314
    FlowPattern.Kind.headShoulders -> "긴 상승 끝물 · 호재에 마지막 신고가"   // ios: Views/FlowView.swift:315
    FlowPattern.Kind.ascTriangle -> "한 가격에 매물 · 사는 쪽이 점점 높게 받침"   // ios: Views/FlowView.swift:316
    FlowPattern.Kind.descTriangle -> "받치는 가격 하나 · 파는 쪽이 점점 낮게 내놓음"   // ios: Views/FlowView.swift:317
    FlowPattern.Kind.bullFlag -> "실적 깜짝·큰 계약에 급등 뒤 쉬어 가기"   // ios: Views/FlowView.swift:318
    FlowPattern.Kind.bearFlag -> "악재 급락 뒤 · 힘 약한 저가 매수 반등"   // ios: Views/FlowView.swift:319
    FlowPattern.Kind.hammer -> "며칠 하락 뒤 · 아침 악재에 빠졌다 오후에 회복"   // ios: Views/FlowView.swift:320
    FlowPattern.Kind.shootingStar -> "며칠 상승 뒤 · 아침 호재에 치솟다 차익 매물에 밀림"   // ios: Views/FlowView.swift:321
    FlowPattern.Kind.bullEngulf -> "공포로 급락한 다음 날 · 하락을 다 되돌림"   // ios: Views/FlowView.swift:322
    FlowPattern.Kind.bearEngulf -> "기대로 급등한 다음 날 · 실적 실망에 다 내줌"   // ios: Views/FlowView.swift:323
    FlowPattern.Kind.insideBar -> "금리 결정·실적·물가 발표 하루 전"   // ios: Views/FlowView.swift:324
}

/** 이름표 자리 잡기에 넘기는 것 */
class TagWant(val key: String, val layer: FlowLayer, val strong: Boolean, val w: Float, val cands: List<Offset>)   // ios: Views/FlowView.swift:547

/** .position(x:y:): 가운데를 (x, y) dp 에 둔다 (부모 Box 왼쪽 위 기준) */
private fun Modifier.centerAt(x: Float, y: Float): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(x.dp.roundToPx() - p.width / 2, y.dp.roundToPx() - p.height / 2) }
}

/** 캔들 차트. 보이는 구간의 캔들만 그리고, 넓게 보면 5일·21일씩 묶어 주봉·월봉으로 접는다 */
@Composable
fun FlowChart(series: FlowSeries, title: String, layers: Set<FlowLayer>, term: FlowTerm?, setTerm: (FlowTerm?) -> Unit,   // ios: Views/FlowView.swift:238,239,240,241,242
              more: (FlowTerm) -> Unit = {}) {   // ios: Views/FlowView.swift:243
    var count by remember { mutableStateOf(160) }          // 보이는 날 수   // ios: Views/FlowView.swift:244
    var end by remember { mutableStateOf<Int?>(null) }      // 맨 오른쪽 날 (null = 마지막 날)   // ios: Views/FlowView.swift:245
    var pinchBase by remember { mutableStateOf<Int?>(null) }   // ios: Views/FlowView.swift:246
    var dragBase by remember { mutableStateOf<Int?>(null) }   // ios: Views/FlowView.swift:247
    var tapAt by remember { mutableStateOf(Offset.Zero) }   // ios: Views/FlowView.swift:248 마지막으로 누른 자리 (dp, 말풍선을 그 근처에 띄운다)
    var hintSeen by rememberPrefBool("flow.hintSeen", false)   // ios: Views/FlowView.swift:249
    val onTerm = { t: FlowTerm -> setTerm(t) }
    // ios: Views/FlowView.swift:462
    LaunchedEffect(term) { if (term != null) hintSeen = true }
    val axisW = 46f; val bandH = 36f; val topH = 50f; val botH = 20f   // ios: Views/FlowView.swift:349
    val haptic = LocalHapticFeedback.current
    val dens = LocalDensity.current.density
    // 겹 투명도: 켜고 끌 때 0.2초에 걸쳐 (자리는 그대로)
    val aTrend by animateFloatAsState(if (FlowLayer.trend in layers) 1f else 0f, tween(200), label = "trend")   // ios: Views/FlowView.swift:392
    val aLevels by animateFloatAsState(if (FlowLayer.levels in layers) 1f else 0f, tween(200), label = "levels")   // ios: Views/FlowView.swift:415
    val aBig by animateFloatAsState(if (FlowLayer.big in layers) 1f else 0f, tween(200), label = "big")   // ios: Views/FlowView.swift:434
    val aCandle by animateFloatAsState(if (FlowLayer.candle in layers) 1f else 0f, tween(200), label = "candle")   // ios: Views/FlowView.swift:445
    val up = Theme.up; val down = Theme.down; val dash = Theme.dash; val teal = Theme.teal; val card = Theme.card; val line = Theme.line; val purple = Theme.purple

    // ios: Views/FlowView.swift:253,254,494,495
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        val k = series.candles; val n = k.size   // ios: Views/FlowView.swift:255
        val cnt = max(20, min(n, count))   // ios: Views/FlowView.swift:256
        val hi = max(cnt - 1, min(n - 1, end ?: (n - 1))); val lo = hi - cnt + 1   // ios: Views/FlowView.swift:257
        val step = if (cnt <= 200) 1 else if (cnt <= 600) 5 else 21   // ios: Views/FlowView.swift:258
        val fullH = maxHeight.value; val W = maxWidth.value - axisW; val top = topH + bandH + 12; val H = fullH - top - botH   // ios: Views/FlowView.swift:259
        var pMin = Double.MAX_VALUE; var pMax = -Double.MAX_VALUE; for (i in lo..hi) { pMin = min(pMin, k[i].l); pMax = max(pMax, k[i].h) }   // ios: Views/FlowView.swift:260
        if (pMin > pMax) { pMin = 0.0; pMax = 1.0 }   // ios: Views/FlowView.swift:261
        val pad = (pMax - pMin) * 0.08   // ios: Views/FlowView.swift:262
        val yMin = pMin - pad; val yMax = pMax + pad   // ios: Views/FlowView.swift:263
        val x = { i: Double -> ((i - lo + 0.5) / cnt).toFloat() * W }   // ios: Views/FlowView.swift:269
        val y = { v: Double -> top + (1 - (v - yMin) / max(1e-12, yMax - yMin)).toFloat() * H }   // ios: Views/FlowView.swift:264
        val last = k[hi]   // ios: Views/FlowView.swift:265
        val piv = series.pivotSets[if (step == 1) 0 else if (step == 5) 1 else 2]   // ios: Views/FlowView.swift:266
        // 지지·저항: 보이는 것 중 많이 닿은 4개. 이름표는 위아래로 붙으면 뒤의 것을 뺀다
        val lvls = series.levels.filter { it.price >= yMin && it.price <= yMax && it.from <= hi }.take(4)   // ios: Views/FlowView.swift:268
        // 월봉으로 볼 땐 패턴 없이 추세·지지/저항만 (한 칸이 한 달이라 몇 주짜리 모양은 뜻이 없다)
        val bigs = if (step == 21) emptyList() else series.patterns.filter { it.big && it.to >= lo && it.from <= hi }   // ios: Views/FlowView.swift:271
        // 흐린(확정 전·거래량 적은) 캔들 패턴은 확대해서 60일 이하로 볼 때만 띄운다
        val showWeak: Boolean = cnt <= 60   // ios: Views/FlowView.swift:273
        val smalls = if (step == 1) series.patterns.filter { !it.big && it.to >= lo && it.to <= hi && (it.strong || showWeak) } else emptyList()   // ios: Views/FlowView.swift:274
        // 큰 패턴 틀: 구간 안 최고가·최저가
        val box = { pt: FlowPattern ->   // ios: Views/FlowView.swift:276
            val a = x(max(lo, pt.from).toDouble() - 0.5); val b = x(min(hi, pt.to).toDouble() + 0.5)   // ios: Views/FlowView.swift:277
            var sh = -Double.MAX_VALUE; var sl = Double.MAX_VALUE; for (i in max(lo, pt.from)..min(hi, pt.to)) { sh = max(sh, k[i].h); sl = min(sl, k[i].l) }   // ios: Views/FlowView.swift:278
            floatArrayOf(a, b, y(sh) - 4, y(sl) + 4)   // ios: Views/FlowView.swift:279
        }
        // ios: Views/FlowView.swift:281
        val spots = placeTags(levels = lvls.map { lv ->
            val w = tagW(levelText(lv)); val yy = y(lv.price); val cx = W - w / 2 - 2   // ios: Views/FlowView.swift:282
            TagWant("L${lv.id}", FlowLayer.levels, true, w, listOf(Offset(cx, yy), Offset(cx, yy - 12), Offset(cx, yy + 12)))   // ios: Views/FlowView.swift:283
        }, big = bigs.map { pt ->   // ios: Views/FlowView.swift:284
            val (a, b, t, bt) = box(pt); val w = tagW(pt.kind.title); val cx = (a + b) / 2   // ios: Views/FlowView.swift:285
            val up0 = t - 13; val dn = bt + 13   // ios: Views/FlowView.swift:286
            val pref = if (pt.below) listOf(dn, bt - 13, up0) else listOf(up0, t + 13, dn)   // ios: Views/FlowView.swift:287
            // ios: Views/FlowView.swift:288,289
            TagWant("B${pt.id}", FlowLayer.big, pt.strong, w, pref.map { Offset(cx, it) } + listOf(Offset(cx - w, pref[0]), Offset(cx + w, pref[0])))
        }, candle = smalls.map { pt ->   // ios: Views/FlowView.swift:290
            val w = tagW(pt.kind.title); val cx = x((pt.from + pt.to) / 2.0); val yy = y(pt.price)   // ios: Views/FlowView.swift:291
            val other = if (pt.below) y(k[pt.to].h) - 14 else y(k[pt.to].l) + 14   // ios: Views/FlowView.swift:292
            val d = if (pt.below) 1f else -1f   // ios: Views/FlowView.swift:293
            // ios: Views/FlowView.swift:294,295
            TagWant("C${pt.id}", FlowLayer.candle, pt.strong, w, listOf(Offset(cx, yy + 14 * d), Offset(cx, yy + 34 * d), Offset(cx, other), Offset(cx, yy + 54 * d)))
        }, on = layers, bounds = Rect(0f, top - 6, W, top - 6 + H + 14))   // ios: Views/FlowView.swift:296

        LaunchedEffect(step) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }   // ios: Views/FlowView.swift:492

        // ios: Views/FlowView.swift:298,461
        Box(
            Modifier.fillMaxSize()
                // 점·선 근처를 누르면 그 뜻. 이름표는 누를 수 있는 칸이라 먼저 받는다
                .pointerInput(lo, hi, layers, W, H) {   // ios: Views/FlowView.swift:464
                    detectTapGestures { o ->
                        val lx = o.x / dens; val ly = o.y / dens
                        setTerm(null); tapAt = Offset(lx, ly)   // ios: Views/FlowView.swift:465
                        // ios: Views/FlowView.swift:466,467,468
                        val lv = if (FlowLayer.levels in layers) lvls.minByOrNull { abs(y(it.price) - ly) } else null
                        if (lv != null && abs(y(lv.price) - ly) < 10) { onTerm(if (lv.support) FlowTerm.support else FlowTerm.resistance); return@detectTapGestures }
                        // ios: Views/FlowView.swift:470,471
                        if (FlowLayer.trend in layers && piv.any { it.i in lo..hi && hypot(x(it.i.toDouble()) - lx, y(it.price) - ly) < 22 }) onTerm(FlowTerm.dots)
                    }
                }
                // 이름표 위에서 시작해도 밀기·핀치가 먹게: 두 손가락은 확대, 한 손가락은 옆으로 밀 때만 (세로는 화면 스크롤에 넘긴다)
                .pointerInput(n, W) {   // ios: Views/FlowView.swift:475,476,484
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var side: Boolean? = null; var acc = Offset.Zero; var mag = 1f; var tx = 0f
                        do {
                            val ev = awaitPointerEvent()
                            val down = ev.changes.count { it.pressed }
                            if (down >= 2) {
                                mag *= ev.calculateZoom()   // ios: Views/FlowView.swift:485
                                val base = pinchBase ?: count   // ios: Views/FlowView.swift:486
                                if (pinchBase == null) pinchBase = count   // ios: Views/FlowView.swift:487
                                count = max(20, min(n, (base / max(0.05f, mag)).toInt()))   // ios: Views/FlowView.swift:488
                                ev.changes.forEach { it.consume() }
                            } else if (down == 1) {
                                val d = ev.calculatePan()
                                if (side == null) { acc += d; if (acc.getDistance() > 8 * dens) side = abs(acc.x) > abs(acc.y) }   // ios: Views/FlowView.swift:476 (minimumDistance 8. 세로면 화면 스크롤에 넘긴다)
                                if (side == true) {
                                    tx += d.x / dens
                                    val c0 = max(20, min(n, count)); val h0 = max(c0 - 1, min(n - 1, end ?: (n - 1)))
                                    val base = dragBase ?: h0   // ios: Views/FlowView.swift:477,478
                                    if (dragBase == null) { dragBase = h0; setTerm(null) }   // ios: Views/FlowView.swift:479
                                    val shift = (tx / max(1f, W) * c0).roundToInt()   // ios: Views/FlowView.swift:480
                                    end = max(c0 - 1, min(n - 1, base - shift))   // ios: Views/FlowView.swift:481
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                        dragBase = null; pinchBase = null   // ios: Views/FlowView.swift:483,490
                    }
                },
        ) {
            // 머리: ▲44% 테슬라 · 시작 대비 (세모는 숫자의 70%) + 지금 단위
            // ios: Views/FlowView.swift:397,398
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val v = series.pct(last.c)   // ios: Views/FlowView.swift:399
                // ios: Views/FlowView.swift:400
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ios: Views/FlowView.swift:401,402,403
                    com.naeilo.app.ui.TA(buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = fx(15f), baselineShift = androidx.compose.ui.text.style.BaselineShift(2f / 15f))) { append(if (v > 0) "▲" else if (v < 0) "▼" else "") }
                        append(cformat("%.0f%%", abs(v)))
                    }, Modifier.alignByBaseline(), style = TextStyle(fontSize = fx(22f), fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = Theme.change(v), maxLines = 1)
                    T(title + " · " + tr("시작 대비"), Modifier.alignByBaseline(), style = appFont(13f), color = Theme.muted, maxLines = 1)   // ios: Views/FlowView.swift:404
                }
                Spacer(Modifier.width(6.dp))   // ios: Views/FlowView.swift:406
                // 지금 단위 표시: 손 그림으로 두 손가락 확대를 알린다. 핀치로 넓히면 주봉·월봉으로 바뀐다
                Row(Modifier.background(Theme.line, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/FlowView.swift:408,412,413
                    com.naeilo.app.ui.Sym("hand.pinch", 12f, Theme.sub)   // ios: Views/FlowView.swift:409
                    T(if (step == 1) "일봉" else if (step == 5) "주봉" else "월봉", style = appFont(12f, FontWeight.SemiBold), color = Theme.sub, maxLines = 1)   // ios: Views/FlowView.swift:410
                }
            }

            // 오른쪽 눈금 (+x% 시작 대비)
            val ticks = (0 until 4).map { t -> yMin + (yMax - yMin) * (0.1 + 0.8 * t / 3.0) }   // ios: Views/FlowView.swift:327,328
            Canvas(Modifier.fillMaxSize()) { ticks.forEach { v -> drawLine(line, Offset(0f, y(v) * dens), Offset(W * dens, y(v) * dens), 1.dp.toPx()) } }   // ios: Views/FlowView.swift:329,330
            ticks.forEach { v ->
                // ios: Views/FlowView.swift:331,332,333
                T(cformat("%+.0f%%", series.pct(v)), Modifier.width((axisW - 4).dp).centerAt(W + axisW / 2 + 2, y(v)), style = appFont(10f).copy(fontFeatureSettings = "tnum"), color = Theme.muted, maxLines = 1)
            }

            // 캔들 (늘 보임)
            Canvas(Modifier.width(W.dp).fillMaxSize().clipToBounds()) {   // ios: Views/FlowView.swift:337,355
                var g0 = lo / step   // ios: Views/FlowView.swift:338
                while (g0 * step <= hi) {   // ios: Views/FlowView.swift:339
                    val a = max(lo, g0 * step); val b = min(hi, g0 * step + step - 1)   // ios: Views/FlowView.swift:340
                    val o = k[a].o; val c = k[b].c   // ios: Views/FlowView.swift:341
                    var h = -Double.MAX_VALUE; var l = Double.MAX_VALUE   // ios: Views/FlowView.swift:342
                    for (i in a..b) { h = max(h, k[i].h); l = min(l, k[i].l) }   // ios: Views/FlowView.swift:343
                    val cx = x((a + b) / 2.0)   // ios: Views/FlowView.swift:344
                    val bw = max(1f, (b - a + 1).toFloat() / cnt * W * 0.68f)   // ios: Views/FlowView.swift:345
                    // 장중 오늘 캔들은 흐리게 (아직 끝나지 않은 날)
                    val col = (if (c >= o) up else down).copy(alpha = if (series.live && b == n - 1) 0.4f else 1f)   // ios: Views/FlowView.swift:347
                    drawLine(col, Offset(cx * dens, y(h) * dens), Offset(cx * dens, y(l) * dens), 1.dp.toPx())   // ios: Views/FlowView.swift:348,349
                    drawRect(col, Offset((cx - bw / 2) * dens, y(max(o, c)) * dens), Size(bw * dens, max(1f, abs(y(o) - y(c))) * dens))   // ios: Views/FlowView.swift:350,351
                    g0 += 1   // ios: Views/FlowView.swift:352
                }
            }

            // 겹 1: 추세 띠 + 추세 점
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aTrend }) {   // ios: Views/FlowView.swift:358,392
                Canvas(Modifier.width(W.dp).fillMaxSize().clipToBounds()) {   // ios: Views/FlowView.swift:359,381
                    for ((row, ts) in series.trends.reversed().withIndex()) {   // ios: Views/FlowView.swift:360
                        for (t in ts) { if (!(t.to >= lo && t.from <= hi)) continue   // ios: Views/FlowView.swift:361
                            val a = x(max(lo, t.from) - 0.5); val b = x(min(hi, t.to) + 0.5)   // ios: Views/FlowView.swift:362
                            // ios: Views/FlowView.swift:363,364
                            drawRect(if (t.dir > 0) up else if (t.dir < 0) down else dash, Offset(a * dens, (topH + row * (bandH / 3) + 1.5f) * dens), Size(max(1f, b - a) * dens, (bandH / 3 - 3) * dens))
                        }
                    }
                    val vis = piv.filter { it.i >= lo - 40 && it.i <= hi + 40 }   // ios: Views/FlowView.swift:367
                    val path = Path()   // ios: Views/FlowView.swift:368
                    vis.forEachIndexed { j, p ->   // ios: Views/FlowView.swift:369
                        val px = x(p.i.toDouble()) * dens; val py = (y(p.price) + if (p.high) -5 else 5) * dens   // ios: Views/FlowView.swift:370
                        if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)   // ios: Views/FlowView.swift:371
                    }
                    drawPath(path, teal.copy(alpha = 0.55f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))   // ios: Views/FlowView.swift:373
                    for (p in vis) { if (!(p.i >= lo && p.i <= hi)) continue   // ios: Views/FlowView.swift:374
                        val c = Offset(x(p.i.toDouble()) * dens, (y(p.price) + if (p.high) -5 else 5) * dens)   // ios: Views/FlowView.swift:375
                        val r = Rect(Offset(c.x - 3.5f * dens, c.y - 3.5f * dens), Size(7f * dens, 7f * dens))   // ios: Views/FlowView.swift:376
                        drawOval(teal, r.topLeft, r.size)   // ios: Views/FlowView.swift:377
                        drawOval(card, r.topLeft, r.size, style = Stroke(1.2.dp.toPx()))   // ios: Views/FlowView.swift:378
                    }
                }
                // 띠 이름: 오른쪽 눈금 칸에 아주 작게 (차트 폭은 그대로)
                listOf("큰", "중간", "작은").forEachIndexed { row, t ->   // ios: Views/FlowView.swift:383
                    T(t, Modifier.centerAt(W + 4 + 12, topH + row * (bandH / 3) + bandH / 6), style = appFont(8f), color = Theme.muted, maxLines = 1)   // ios: Views/FlowView.swift:384,385
                }
                // 띠를 누르면 띠 설명
                // ios: Views/FlowView.swift:388,389,390,393
                if (FlowLayer.trend in layers) Box(Modifier.width(W.dp).height((bandH + 6).dp).layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.place(0, (topH - 3).dp.roundToPx()) } }.plainClickable { tapAt = Offset(W / 2, topH + bandH); onTerm(FlowTerm.band) })
            }

            // 겹 2: 지지·저항 가로선
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aLevels }) {   // ios: Views/FlowView.swift:396,415
                Canvas(Modifier.fillMaxSize()) {
                    for (lv in lvls) {   // ios: Views/FlowView.swift:397
                        val a = max(0f, x(max(lo, lv.from) - 0.5)); val yy = y(lv.price)   // ios: Views/FlowView.swift:398
                        val col = if (lv.support) up else down   // ios: Views/FlowView.swift:399
                        val op = min(1.0, 0.25 + 0.2 * lv.touches).toFloat()   // ios: Views/FlowView.swift:400
                        drawLine(col.copy(alpha = op), Offset(a * dens, yy * dens), Offset(W * dens, yy * dens), (if (lv.touches >= 4) 2f else 1.2f).dp.toPx())   // ios: Views/FlowView.swift:401,402
                    }
                }
                for (lv in lvls) {
                    val sp = spots["L${lv.id}"] ?: continue   // ios: Views/FlowView.swift:403
                    val col = if (lv.support) up else down; val op = min(1.0, 0.25 + 0.2 * lv.touches).toFloat()
                    // ios: Views/FlowView.swift:404,405,406,407,408,410,411,416
                    T(levelText(lv), Modifier.centerAt(sp.x, sp.y).heightIn(min = 24.dp).then(if (FlowLayer.levels in layers) Modifier.plainClickable { tapAt = sp; onTerm(if (lv.support) FlowTerm.support else FlowTerm.resistance) } else Modifier)
                        .padding(vertical = 4.dp).background(card.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 4.dp),
                        style = appFont(10f, FontWeight.SemiBold), color = col.copy(alpha = max(0.7f, op)), maxLines = 1)
                }
            }

            // 겹 3: 큰 패턴 (구간을 묶는 틀 + 이름표)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aBig }) {   // ios: Views/FlowView.swift:419,434
                Canvas(Modifier.fillMaxSize()) {
                    for (pt in bigs) {   // ios: Views/FlowView.swift:420
                        val (a, b, t, bt) = box(pt)   // ios: Views/FlowView.swift:421
                        val col = purple   // ios: Views/FlowView.swift:422
                        val sz = Size(max(4f, b - a) * dens, max(4f, bt - t) * dens); val r = CornerRadius(6.dp.toPx())   // ios: Views/FlowView.swift:423,426
                        val ctr = Offset((a + b) / 2 * dens, (t + bt) / 2 * dens); val tl = Offset(ctr.x - sz.width / 2, ctr.y - sz.height / 2)   // ios: Views/FlowView.swift:427
                        drawRoundRect(col.copy(alpha = if (pt.strong) 0.07f else 0.03f), tl, sz, CornerRadius(6.dp.toPx()))   // ios: Views/FlowView.swift:425
                        // ios: Views/FlowView.swift:424,428
                        drawRoundRect(col.copy(alpha = if (pt.strong) 0.9f else 0.45f), tl, sz, r, style = Stroke((if (pt.strong) 1.5f else 1f).dp.toPx(), pathEffect = if (pt.strong) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    }
                }
                for (pt in bigs) {
                    val sp = spots["B${pt.id}"] ?: continue   // ios: Views/FlowView.swift:429
                    Tag(pt.kind.title, purple, pt.strong, Modifier.centerAt(sp.x, sp.y), FlowLayer.big in layers) { tapAt = sp; onTerm(FlowTerm.pattern(pt.kind)) }   // ios: Views/FlowView.swift:430,435
                }
            }

            // 겹 4: 캔들 패턴 (일봉으로 볼 때만. 주봉·월봉에선 캔들 하나가 하루가 아니라 뜻이 달라진다)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aCandle }) {   // ios: Views/FlowView.swift:438,445
                for (pt in smalls) {   // ios: Views/FlowView.swift:439
                    val sp = spots["C${pt.id}"] ?: continue   // ios: Views/FlowView.swift:440
                    Tag(pt.kind.title, Theme.orange, pt.strong, Modifier.centerAt(sp.x, sp.y), FlowLayer.candle in layers) { tapAt = sp; onTerm(FlowTerm.pattern(pt.kind)) }   // ios: Views/FlowView.swift:441,446
                }
            }

            // 아래: 보이는 기간
            // ios: Views/FlowView.swift:449,450,451
            T(date(series, lo) + " – " + date(series, hi) + "  ·  " + tr("← 옆으로 밀면 지난 날"), Modifier.centerAt(W / 2, fullH - botH / 2), style = appFont(10f).copy(fontFeatureSettings = "tnum"), color = Theme.muted, maxLines = 1)

            // 말풍선: 누른 것의 뜻. 처음 한 번은 사용 안내
            // ios: Views/FlowView.swift:454,455
            if (term != null) Bubble(term.title, term.body, at = tapAt, top = top, W = W, onClose = { setTerm(null) }) { more(term) }
            // ios: Views/FlowView.swift:456,457,458
            else if (!hintSeen) Bubble("이름표나 선을 눌러 보세요", "누른 것의 뜻이 여기 떠요. 흐린 점선 이름표는 확정 전이거나 거래량이 적었던 것이에요.", at = Offset(W / 2, fullH - botH), top = top, W = W, onClose = { hintSeen = true }, more = null)
        }
    }
}

/** 누른 자리 바로 위에 띄운다. 차트 위쪽 끝이면 바로 아래로. 가로는 화면 밖으로 안 나가게. 꼬리는 누른 자리를 가리킨다 (자리는 dp) */
// ios: Views/FlowView.swift:499
@Composable
private fun Bubble(title: String, body: String, at: Offset, top: Float, W: Float, onClose: () -> Unit, more: (() -> Unit)?) {
    val p = at
    val bw: Float = min(280f, W - 8)   // ios: Views/FlowView.swift:597
    val cx: Float = min(max(p.x, bw / 2 + 4), W - bw / 2 - 4)   // ios: Views/FlowView.swift:501
    val below: Boolean = p.y < top + 110   // ios: Views/FlowView.swift:502
    val tailX: Float = min(max(p.x - (cx - bw / 2), 16f), bw - 16)   // ios: Views/FlowView.swift:503
    // ios: Views/FlowView.swift:504,505
    // 말풍선: 라이트는 흰 바탕 + 테두리 + 그림자, 다크는 카드보다 한 단 밝은 바탕
    val bb = Bb.pick(Theme.isDark)   // ios: Views/FlowView.swift:345,346,347
    // 꼬리: 바탕색으로 채우고 바깥 두 변만 테두리. 몸통에 1pt 겹쳐 이음매 테두리를 덮는다
    // ios: Views/FlowView.swift:602,603,604,605,606,607
    @Composable fun Tail() = Box(Modifier.width(bw.dp).height(7.dp).offset(y = (if (below) 1 else -1).dp).zIndex(1f)) {
        Canvas(Modifier.offset(x = (tailX - 7).dp).size(14.dp, 7.dp)) {
            drawPath(FlowTail.path(below, size), bb.bg)
            drawPath(FlowTail.path(below, size, open = true), bb.line, style = Stroke(1.dp.toPx()))
        }
    }
    // ios: Views/FlowView.swift:507,531,532,535,537 (누른 자리 p 에 붙여 폭 bw 로 재고, 키는 글 따라)
    Column(
        Modifier.layout { m, c ->
            val pl = m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(bw.dp.roundToPx()))
            val off = (if (below) 8 else -8).dp.roundToPx()   // ios: Views/FlowView.swift:534
            layout(c.maxWidth, c.maxHeight) { pl.place((cx - bw / 2).dp.roundToPx(), p.y.dp.roundToPx() + off - (if (below) 0 else pl.height)) }
        }.plainClickable(onClose),
        verticalArrangement = Arrangement.spacedBy(0.dp),   // ios: Views/FlowView.swift:508
    ) {
        if (below) Tail()   // ios: Views/FlowView.swift:509
        // ios: Views/FlowView.swift:612,626,627,628,629,634
        val sh = if (Theme.isDark) Color.Black.copy(alpha = 0.55f) else hex(0x15202B, 0.16f)
        Column(Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(12.dp), ambientColor = sh, spotColor = sh).background(bb.bg, RoundedCornerShape(12.dp)).border(1.dp, bb.line, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(title, style = appFont(15f, FontWeight.Bold), color = bb.title)   // ios: Views/FlowView.swift:613
            T(body, style = appFont(14f), color = bb.body)   // ios: Views/FlowView.swift:614
            // ios: Views/FlowView.swift:513
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("정보 제공용 · 매매 권유 아님", Modifier.weight(1f).padding(end = 4.dp), style = appFont(11f), color = bb.foot)   // ios: Views/FlowView.swift:616
                // ios: Views/FlowView.swift:516,517,518,520
                if (more != null) T("자세히 ›", Modifier.plainClickable(more), style = appFont(13f, FontWeight.SemiBold), color = bb.more)   // ios: Views/FlowView.swift:620
            }
        }
        if (!below) Tail()   // ios: Views/FlowView.swift:529
    }
}

/** 말풍선 색 (라이트 · 다크) */
// ios: Views/FlowView.swift:345,346,347
private class Bb(val bg: Color, val line: Color, val title: Color, val body: Color, val foot: Color, val more: Color) {
    companion object {
        val light = Bb(hex(0xFFFFFF), hex(0xD5DBE1), hex(0x15202B), hex(0x46525E), hex(0x8A949E), hex(0x0B6B66))
        val dark = Bb(hex(0x2E3945), hex(0x46535F), hex(0xF2F5F8), hex(0xC6CED6), hex(0x8E99A4), hex(0x8FD8CE))
        fun pick(isDark: Boolean) = if (isDark) dark else light
    }
}

/** 말풍선 꼬리 (작은 삼각형) */
object FlowTail {   // ios: Views/FlowView.swift:696
    // ios: Views/FlowView.swift:697,698,699
    fun path(up: Boolean, r: Size, open: Boolean = false): Path {   // open: 몸통 쪽 밑변 없이 두 변만 (테두리용)
        val p = Path()   // ios: Views/FlowView.swift:700
        if (up) { p.moveTo(0f, r.height); p.lineTo(r.width / 2, 0f); p.lineTo(r.width, r.height) }   // ios: Views/FlowView.swift:701,702
        else { p.moveTo(0f, 0f); p.lineTo(r.width / 2, r.height); p.lineTo(r.width, 0f) }   // ios: Views/FlowView.swift:703,704
        if (!open) p.close()   // ios: Views/FlowView.swift:706
        return p   // ios: Views/FlowView.swift:707
    }
}

/** '흐름 읽는 법': 겹마다 항목 한 줄씩, 작은 그림 + 두세 줄 설명 */
// ios: Views/FlowView.swift:607,608,609
@Composable
fun FlowGuide(focus: String?, dismiss: () -> Unit) {
    class Item(val id: String, val title: String, val body: String, val `when`: String? = null)   // ios: Views/FlowView.swift:716
    // ios: Views/FlowView.swift:612
    val groups: List<Pair<String, List<Item>>> = remember {
        val t = { x: FlowTerm -> Item(x.key, x.title, x.body) }   // ios: Views/FlowView.swift:613
        val k = { x: FlowPattern.Kind -> Item(x.name, x.title, x.body, x.`when`) }   // ios: Views/FlowView.swift:719
        // ios: Views/FlowView.swift:615
        listOf(
            FlowLayer.trend.title to listOf(t(FlowTerm.band), t(FlowTerm.dots)),   // ios: Views/FlowView.swift:616
            FlowLayer.levels.title to listOf(t(FlowTerm.support), t(FlowTerm.resistance)),   // ios: Views/FlowView.swift:617
            FlowLayer.big.title to listOf(k(FlowPattern.Kind.doubleTop), k(FlowPattern.Kind.doubleBottom), k(FlowPattern.Kind.headShoulders), k(FlowPattern.Kind.ascTriangle), k(FlowPattern.Kind.descTriangle), k(FlowPattern.Kind.bullFlag), k(FlowPattern.Kind.bearFlag)),   // ios: Views/FlowView.swift:618
            FlowLayer.candle.title to listOf(k(FlowPattern.Kind.hammer), k(FlowPattern.Kind.shootingStar), k(FlowPattern.Kind.bullEngulf), k(FlowPattern.Kind.bearEngulf), k(FlowPattern.Kind.insideBar)),   // ios: Views/FlowView.swift:619
        )
    }
    val scroll = rememberScrollState()
    var focusY by remember { mutableStateOf<Int?>(null) }
    // ios: Views/FlowView.swift:657 (고른 줄을 가운데로)
    LaunchedEffect(focusY) { focusY?.let { scroll.scrollTo(max(0, it - scroll.viewportSize / 2)) } }
    // ios: Views/FlowView.swift:663,664,665,676,677
    @Composable fun Row0(it: Item) {
        val on = it.id == focus
        Row(Modifier.fillMaxWidth().background(if (on) Theme.tealBg else Color.Transparent, RoundedCornerShape(16.dp)).padding(12.dp)
            .then(if (on) Modifier.onGloballyPositioned { c -> if (focusY == null) focusY = c.positionInParent().y.toInt() + c.size.height / 2 } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            // ios: Views/FlowView.swift:666,667,668,669
            Box(Modifier.background(Theme.bg, RoundedCornerShape(10.dp)).padding(4.dp)) { FlowMini(it.id, Modifier.size(56.dp, 40.dp)) }
            // ios: Views/FlowView.swift:670,674
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(it.title, style = appFont(17f, FontWeight.Bold), color = Theme.ink)   // ios: Views/FlowView.swift:776
                T(it.body, style = appFont(15f), color = Theme.sub)   // ios: Views/FlowView.swift:777
                // ios: Views/FlowView.swift:778,779,780
                it.`when`?.let { w ->
                    com.naeilo.app.ui.TA(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Theme.teal)) { append(tr("이럴 때")) }
                        withStyle(SpanStyle(color = Theme.sub)) { append("  " + tr(w)) }
                    }, Modifier.padding(top = 2.dp), style = appFont(14f), color = Theme.sub)
                }
            }
            Spacer(Modifier.widthIn(min = 0.dp))   // ios: Views/FlowView.swift:674
        }
    }
    // ios: Views/FlowView.swift:623,624,660
    Column(Modifier.fillMaxSize().background(Theme.bg), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ios: Views/FlowView.swift:625,634
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("흐름 읽는 법", Modifier.weight(1f).padding(end = 8.dp), style = appFont(22f, FontWeight.Bold), color = Theme.ink)   // ios: Views/FlowView.swift:731
            // ios: Views/FlowView.swift:628,629,630,632
            Box(Modifier.size(44.dp, 44.dp).plainClickable(dismiss).semantics { contentDescription = tr("닫기") }, contentAlignment = Alignment.Center) {
                Sym("xmark", 15f, Theme.sub, scaled = false)
            }
        }
        // ios: Views/FlowView.swift:635,636,637,654,656
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).background(Theme.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // ios: Views/FlowView.swift:638,639
            groups.forEach { g ->
                // ios: Views/FlowView.swift:640
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(g.first, Modifier.padding(start = 4.dp), style = appFont(14f, FontWeight.SemiBold), color = Theme.muted)   // ios: Views/FlowView.swift:746
                    // ios: Views/FlowView.swift:642,648
                    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        // ios: Views/FlowView.swift:643
                        g.second.forEachIndexed { i, it ->
                            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 84.dp).height(1.dp).background(Theme.line))   // ios: Views/FlowView.swift:644
                            Row0(it)   // ios: Views/FlowView.swift:645
                        }
                    }
                }
            }
            // ios: Views/FlowView.swift:651,652
            T("흐린 점선 = 확정 전·거래량 적음 · 투자 권유 아님", Modifier.padding(horizontal = 4.dp), style = appFont(13f), color = Theme.muted)   // ios: Views/FlowView.swift:757
        }
    }
}

/** 설명 페이지의 작은 그림. 좌표는 0~1 (y 0 = 위) */
// ios: Views/FlowView.swift:682
object FlowMini {
    // ios: Views/FlowView.swift:685,686,687,699
    val lines: Map<String, List<Offset>> = run {
        fun pts(a: List<Pair<Float, Float>>): List<Offset> = a.map { Offset(it.first, it.second) }
        mapOf(
            "dots" to pts(listOf(0f to 0.85f, 0.22f to 0.5f, 0.38f to 0.68f, 0.62f to 0.3f, 0.78f to 0.48f, 1f to 0.12f)),   // ios: Views/FlowView.swift:688
            "support" to pts(listOf(0f to 0.25f, 0.2f to 0.75f, 0.4f to 0.4f, 0.6f to 0.75f, 0.8f to 0.35f, 1f to 0.75f)),   // ios: Views/FlowView.swift:689
            "resistance" to pts(listOf(0f to 0.75f, 0.2f to 0.25f, 0.4f to 0.6f, 0.6f to 0.25f, 0.8f to 0.65f, 1f to 0.25f)),   // ios: Views/FlowView.swift:690
            "doubleTop" to pts(listOf(0f to 0.9f, 0.25f to 0.15f, 0.5f to 0.55f, 0.75f to 0.15f, 1f to 0.9f)),   // ios: Views/FlowView.swift:691
            "doubleBottom" to pts(listOf(0f to 0.1f, 0.25f to 0.85f, 0.5f to 0.45f, 0.75f to 0.85f, 1f to 0.1f)),   // ios: Views/FlowView.swift:692
            "headShoulders" to pts(listOf(0f to 0.9f, 0.18f to 0.42f, 0.33f to 0.65f, 0.5f to 0.08f, 0.67f to 0.65f, 0.82f to 0.42f, 1f to 0.9f)),   // ios: Views/FlowView.swift:693
            "ascTriangle" to pts(listOf(0f to 0.9f, 0.2f to 0.18f, 0.4f to 0.72f, 0.6f to 0.18f, 0.8f to 0.5f, 1f to 0.18f)),   // ios: Views/FlowView.swift:694
            "descTriangle" to pts(listOf(0f to 0.1f, 0.2f to 0.82f, 0.4f to 0.28f, 0.6f to 0.82f, 0.8f to 0.5f, 1f to 0.82f)),   // ios: Views/FlowView.swift:695
            "bullFlag" to pts(listOf(0f to 0.95f, 0.35f to 0.15f, 0.5f to 0.32f, 0.65f to 0.22f, 0.8f to 0.38f, 1f to 0.05f)),   // ios: Views/FlowView.swift:696
            "bearFlag" to pts(listOf(0f to 0.05f, 0.35f to 0.85f, 0.5f to 0.68f, 0.65f to 0.78f, 0.8f to 0.62f, 1f to 0.95f)),   // ios: Views/FlowView.swift:697
        )
    }
    /** 캔들 (시가, 고가, 저가, 종가), 0~1 (1 = 높음) */
    // ios: Views/FlowView.swift:701
    val candles: Map<String, List<FloatArray>> = mapOf(
        "hammer" to listOf(floatArrayOf(0.62f, 0.72f, 0.08f, 0.7f)),   // ios: Views/FlowView.swift:702
        "shootingStar" to listOf(floatArrayOf(0.38f, 0.92f, 0.3f, 0.32f)),   // ios: Views/FlowView.swift:703
        "bullEngulf" to listOf(floatArrayOf(0.62f, 0.68f, 0.4f, 0.45f), floatArrayOf(0.38f, 0.78f, 0.34f, 0.72f)),   // ios: Views/FlowView.swift:704
        "bearEngulf" to listOf(floatArrayOf(0.45f, 0.66f, 0.4f, 0.6f), floatArrayOf(0.7f, 0.74f, 0.28f, 0.34f)),   // ios: Views/FlowView.swift:705
        "insideBar" to listOf(floatArrayOf(0.25f, 0.95f, 0.08f, 0.85f), floatArrayOf(0.45f, 0.68f, 0.38f, 0.62f)),   // ios: Views/FlowView.swift:706
    )
    /** 겹 선: 지지는 아래, 저항·삼각형 꼭대기는 위 (색은 그릴 때 Theme 에서) */
    // ios: Views/FlowView.swift:709,710,711
    val guide: Map<String, Pair<Float, String>> = mapOf(
        "support" to (0.75f to "up"), "resistance" to (0.25f to "down"),
        "ascTriangle" to (0.18f to "purple"), "descTriangle" to (0.82f to "purple"),
    )
}

// ios: Views/FlowView.swift:683,714,715,756 (그림이라 읽어 주기 없음)
@Composable
fun FlowMini(key: String, modifier: Modifier) {
    val up = Theme.up; val down = Theme.down; val dash = Theme.dash; val purple = Theme.purple; val sub = Theme.sub; val teal = Theme.teal
    Canvas(modifier.clearAndSetSemantics {}) {
        val P = { p: Offset -> Offset(p.x * size.width, p.y * size.height) }   // ios: Views/FlowView.swift:716
        val d = density
        // ios: Views/FlowView.swift:717
        if (key == "band") {
            // ios: Views/FlowView.swift:718,719,720,721
            val rows: List<List<Triple<Float, Float, Color>>> = listOf(
                listOf(Triple(0f, 1f, up)),
                listOf(Triple(0f, 0.4f, dash), Triple(0.4f, 1f, up)),
                listOf(Triple(0f, 0.3f, down), Triple(0.3f, 0.7f, up), Triple(0.7f, 1f, down)),
            )
            // ios: Views/FlowView.swift:723,724
            for ((i, r) in rows.withIndex()) for ((a, b, c) in r) {
                val rect = Rect(Offset(a * size.width + 1 * d, i * size.height / 3 + 3 * d), Size((b - a) * size.width - 2 * d, size.height / 3 - 6 * d))   // ios: Views/FlowView.swift:725
                drawRoundRect(c, rect.topLeft, rect.size, CornerRadius(2 * d, 2 * d))   // ios: Views/FlowView.swift:726
            }
            return@Canvas   // ios: Views/FlowView.swift:729
        }
        // ios: Views/FlowView.swift:731
        FlowMini.guide[key]?.let { g ->
            val col = when (g.second) { "up" -> up; "down" -> down; else -> purple }
            drawLine(col.copy(alpha = 0.7f), P(Offset(0f, g.first)), P(Offset(1f, g.first)), 1.5f * d)   // ios: Views/FlowView.swift:732,733
        }
        // ios: Views/FlowView.swift:735
        FlowMini.lines[key]?.let { pts ->
            val l = Path(); pts.map(P).forEachIndexed { i, o -> if (i == 0) l.moveTo(o.x, o.y) else l.lineTo(o.x, o.y) }   // ios: Views/FlowView.swift:736
            val col: Color = if (key == "dots" || key == "support" || key == "resistance") sub else purple   // ios: Views/FlowView.swift:737
            drawPath(l, col, style = Stroke(1.8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))   // ios: Views/FlowView.swift:738
            // ios: Views/FlowView.swift:739
            if (key == "dots") {
                for (p in pts) drawOval(teal, Offset(P(p).x - 3 * d, P(p).y - 3 * d), Size(6 * d, 6 * d))   // ios: Views/FlowView.swift:740
            }
        }
        // ios: Views/FlowView.swift:743
        FlowMini.candles[key]?.let { cs ->
            val n = cs.size.toFloat(); val bw = 10f * d   // ios: Views/FlowView.swift:744
            // ios: Views/FlowView.swift:745
            for ((i, c) in cs.withIndex()) {
                val x = size.width * (i + 1) / (n + 1)   // ios: Views/FlowView.swift:746
                val Y = { v: Float -> (1 - v) * size.height }   // ios: Views/FlowView.swift:747
                val col = if (c[3] >= c[0]) up else down   // ios: Views/FlowView.swift:748
                drawLine(col, Offset(x, Y(c[1])), Offset(x, Y(c[2])), 1.2f * d)   // ios: Views/FlowView.swift:749,750
                val top = Y(max(c[0], c[3])); val h = max(2f * d, abs(Y(c[0]) - Y(c[3])))   // ios: Views/FlowView.swift:751
                drawRect(col, Offset(x - bw / 2, top), Size(bw, h))   // ios: Views/FlowView.swift:752
            }
        }
    }
}

private fun levelText(lv: FlowLevel): String =   // ios: Views/FlowView.swift:541
    (if (lv.support) "지지" else "저항") + " ×${lv.touches}"   // ios: Views/FlowView.swift:542
/** 10pt 굵은 글자 이름표 폭 어림 (한글 10.5, 영문 6.2 + 여백) */
private fun tagW(t0: String): Float { val t = com.naeilo.app.core.tr(t0); return t.fold(14f) { a, ch -> a + if (ch.code < 128) 6.2f else 10.5f } }   // ios: Views/FlowView.swift:545

/** 이름표 자리 잡기: 지지·저항 → 큰 패턴 → 캔들 패턴 순서로, 켜진 앞 겹 이름표와 이미 놓인 것을 피해
 *  후보 자리를 차례로 본다. 진한 것부터 놓으니 끝내 겹치면 약한 쪽이 숨는다.
 *  뒤 겹을 켜고 꺼도 앞 겹 이름표는 움직이지 않는다 */
fun placeTags(levels: List<TagWant>, big: List<TagWant>, candle: List<TagWant>, on: Set<FlowLayer>, bounds: Rect): Map<String, Offset> {   // ios: Views/FlowView.swift:552
    val out = HashMap<String, Offset>(); val taken = ArrayList<Rect>()   // ios: Views/FlowView.swift:553
    for (group in listOf(levels, big, candle)) {   // ios: Views/FlowView.swift:554
        val mine = ArrayList<Rect>()   // ios: Views/FlowView.swift:555
        for (t in group.sortedBy { if (it.strong) 0 else 1 }) {   // ios: Views/FlowView.swift:556
            for (c in t.cands) {   // ios: Views/FlowView.swift:557
                val cx = min(max(c.x, bounds.left + t.w / 2), bounds.right - t.w / 2)   // ios: Views/FlowView.swift:558
                val cy = min(max(c.y, bounds.top + 9), bounds.bottom - 9)   // ios: Views/FlowView.swift:559
                val r = Rect(cx - t.w / 2, cy - 9, cx + t.w / 2, cy - 9 + 18)   // ios: Views/FlowView.swift:560
                if ((taken + mine).none { it.overlaps(r) }) {   // ios: Views/FlowView.swift:561
                    out[t.key] = Offset(cx, cy); mine.add(r); break   // ios: Views/FlowView.swift:562
                }
            }
        }
        // 이 겹이 켜져 있을 때만 뒤 겹이 피한다
        val l = group.firstOrNull()?.layer; if (l != null && l in on) taken += mine   // ios: Views/FlowView.swift:567
    }
    return out   // ios: Views/FlowView.swift:569
}

/** 이름표: 확정은 진하게, 약한 것은 흐린 점선 테두리 */
@Composable
private fun Tag(t: String, col: Color, strong: Boolean, modifier: Modifier, enabled: Boolean, tap: () -> Unit) {   // ios: Views/FlowView.swift:573
    val card = Theme.card
    // ios: Views/FlowView.swift:574,577,578,579,580,581,583
    Box(modifier.heightIn(min = 24.dp).then(if (enabled) Modifier.plainClickable(tap) else Modifier), contentAlignment = Alignment.Center) {
        T(t, Modifier.graphicsLayer { alpha = if (strong) 1f else 0.75f }
            .background(if (strong) col else card.copy(alpha = 0.85f), RoundedCornerShape(50))
            .drawBehind { drawRoundRect(col.copy(alpha = if (strong) 0f else 0.6f), cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
            style = appFont(10f, FontWeight.Bold), color = if (strong) Color.White else col.copy(alpha = 0.8f), maxLines = 1)   // ios: Views/FlowView.swift:575,576
    }
}

/** 실데이터는 거래일, DRNK 기록은 어제가 마지막 날로 하루에 한 칸 */
private fun date(series: FlowSeries, i: Int): String {   // ios: Views/FlowView.swift:587
    series.dates?.let { d -> if (i < d.size) return d[i].replace("-", ".") }   // ios: Views/FlowView.swift:588
    return fmt.format(Instant.ofEpochSecond(Instant.now().epochSecond - (series.candles.size - i) * 86400L))   // ios: Views/FlowView.swift:589
}
private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US).withZone(ZoneId.systemDefault())   // ios: Views/FlowView.swift:591
