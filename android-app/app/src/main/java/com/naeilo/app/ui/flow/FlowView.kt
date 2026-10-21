package com.naeilo.app.ui.flow

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
import com.naeilo.app.Tab
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
    var sel by remember { mutableStateOf("DRNK") }   // ios: Views/FlowView.swift:7
    var showAll by remember { mutableStateOf(false) }   // ios: Views/FlowView.swift:8
    var layers by remember { mutableStateOf(setOf(FlowLayer.trend)) }   // ios: Views/FlowView.swift:9
    var term by remember { mutableStateOf<FlowTerm?>(null) }   // ios: Views/FlowView.swift:10

    /** 차트 실데이터를 받을 키 (토스·알파카) */
    // 키를 넣고 받기를 마치면(tossDone) 다시 그린다   // ios: Views/FlowView.swift:13,14
    val hasKey = Market.shared.tossDone.let { TossKeys.on || AlpacaKeys.on }   // ios: Views/FlowView.swift:15
    /** 칩 줄: 키가 없으면 샘플 DRNK 를 맨 앞에, 키가 있으면 DRNK 는 빼고 내 종목만 */
    val ids = run {   // ios: Views/FlowView.swift:18
        val out = if (hasKey) mutableListOf() else mutableListOf("DRNK")   // ios: Views/FlowView.swift:19
        for (h in model.holdings) if (h.symbol !in out && h.symbol != KoreaEstimate.sumId && h.symbol != "DRNK") out.add(h.symbol)   // ios: Views/FlowView.swift:20
        out   // ios: Views/FlowView.swift:21
    }
    /** 고른 종목이 칩 줄에서 빠졌으면(키를 넣어 DRNK 가 숨는 등) 첫 종목 */
    val cur: String? = if (sel in ids) sel else ids.firstOrNull()   // ios: Views/FlowView.swift:24
    fun name(id: String): String = Sample.symbol(id)?.name ?: id   // ios: Views/FlowView.swift:25
    val store = FlowStore   // ios: Views/FlowView.swift:26
    /** 키가 없어 못 그리는 종목이 있으면 설정 링크를 보인다 */
    val needKey = ids.any { it != "DRNK" && !FlowStore.canFetch(it) }   // ios: Views/FlowView.swift:28

    // 열 때 담은 종목 일봉을 받는다 (하루 한 번만 실제로 부른다)
    LaunchedEffect(hasKey) { store.ensure(ids) }   // ios: Views/FlowView.swift:74
    // 추세·패턴 계산은 화면 그리기 밖에서 (받은 캔들이 바뀌면 다시). 그리는 쪽은 계산해 둔 것만 꺼내 쓴다
    LaunchedEffect(ids, store.toss, store.alpaca) { store.warm(ids) }

    // ios: Views/FlowView.swift:30,31,68,70,71
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen {
            // ios: Views/FlowView.swift:32
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.outset(16.dp)) { AppHeader(tab = FlowTab.name) }   // ios: Views/FlowView.swift:33 (-16)
                if (ids.isNotEmpty()) Chips(ids, cur, ::name, onAll = { showAll = true }) { sel = it; term = null }   // ios: Views/FlowView.swift:34
                if (needKey) {   // ios: Views/FlowView.swift:35
                    // ios: Views/FlowView.swift:36,37,38,40
                    T("설정 > 오픈 API에서 키를 넣으면 내 종목도 그려져요", Modifier.plainClickable { model.tab = Tab.settings; model.settingsPath = listOf(SettingsRoute.toss) },
                        style = appFont(13f).copy(textDecoration = TextDecoration.Underline), color = Theme.teal)
                }
                LayerRow(layers) { layers = it }   // ios: Views/FlowView.swift:42
                // ios: Views/FlowView.swift:43,62,63
                Box(Modifier.fillMaxWidth().height(420.dp).background(Theme.card, RoundedCornerShape(16.dp))) {
                    val s = cur?.let { store.peek(it) }
                    if (cur != null && s != null) {   // ios: Views/FlowView.swift:44
                        // ios: Views/FlowView.swift:45,46 (.id(sel): 종목이 바뀌면 확대·밀기 상태를 새로)
                        androidx.compose.runtime.key(cur) { FlowChart(series = s, title = if (cur == "DRNK") "DRNK · 샘플" else name(cur), layers = layers, onTerm = { term = it }) }
                    } else if (cur != null && store.bars(cur) != null) {
                        // 캔들은 있고 계산하는 중: 빈 카드 (잠깐)
                    } else if (cur == null) {   // ios: Views/FlowView.swift:47
                        T("종목을 담으면 흐름이 보여요", Modifier.align(Alignment.Center), style = appFont(14f), color = Theme.sub)   // ios: Views/FlowView.swift:48,49
                    } else {   // ios: Views/FlowView.swift:50
                        // ios: Views/FlowView.swift:51,59
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            T(name(cur), style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/FlowView.swift:52
                            T(if (!FlowStore.canFetch(cur)) "키를 넣으면 이 종목 차트가 그려져요"   // ios: Views/FlowView.swift:53
                              else if (cur in store.loading) "일봉을 받는 중이에요"   // ios: Views/FlowView.swift:54
                              else if (cur in store.failed) "일봉을 못 받았어요"   // ios: Views/FlowView.swift:55
                              else "아직 받은 일봉이 없어요",   // ios: Views/FlowView.swift:56
                              style = appFont(13f), color = Theme.sub)   // ios: Views/FlowView.swift:57
                        }
                    }
                }
                // ios: Views/FlowView.swift:64,65
                T("두 손가락으로 벌리면 가까이, 오므리면 주봉·월봉으로 접혀요 · 옆으로 밀면 지난 날", Modifier.heightIn(min = 32.dp), style = appFont(12f), color = Theme.muted, maxLines = 2)
                Explain(term)   // ios: Views/FlowView.swift:66
            }
        }
    }
    // ios: Views/FlowView.swift:72
    AllSheet(showAll, ids, cur, ::name, onDismiss = { showAll = false }) { sel = it; term = null; showAll = false }
}

// 맨 위 내 종목 칩 줄 + '전체'
@Composable
private fun Chips(ids: List<String>, cur: String?, name: (String) -> String, onAll: () -> Unit, onPick: (String) -> Unit) {   // ios: Views/FlowView.swift:78
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/FlowView.swift:79
        // ios: Views/FlowView.swift:80,81
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ids.forEach { Chip(it, cur, name, onPick) } }
        // ios: Views/FlowView.swift:83,84
        T("전체", Modifier.heightIn(min = 36.dp).plainClickable(onAll).padding(vertical = 8.dp), style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)
    }
}

@Composable
private fun Chip(id: String, cur: String?, name: (String) -> String, onPick: (String) -> Unit) {   // ios: Views/FlowView.swift:88
    val on = cur == id; val locked = id != "DRNK" && !FlowStore.canFetch(id)   // ios: Views/FlowView.swift:89
    val fg = if (on) Color.White else Theme.ink   // ios: Views/FlowView.swift:96
    // ios: Views/FlowView.swift:90,91,97,98,99,101
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (on) Theme.teal else Theme.card)
            .border(1.dp, if (on) Color.Transparent else Theme.border, RoundedCornerShape(50))
            .plainClickable { onPick(id) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T(if (id == "DRNK") "DRNK" else name(id), style = appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), color = fg, maxLines = 1)   // ios: Views/FlowView.swift:92
        if (id == "DRNK") T("샘플", style = appFont(10f), color = if (on) Color.White.copy(alpha = 0.8f) else Theme.muted)   // ios: Views/FlowView.swift:93
        if (locked) T("키 필요", style = appFont(10f), color = if (on) Color.White.copy(alpha = 0.8f) else Theme.muted)   // ios: Views/FlowView.swift:94
    }
}

@Composable
private fun AllSheet(show: Boolean, ids: List<String>, cur: String?, name: (String) -> String, onDismiss: () -> Unit, onPick: (String) -> Unit) {   // ios: Views/FlowView.swift:104
    // ios: Views/FlowView.swift:105,120
    Sheet(show, onDismiss, medium = true) {
        SheetBar("전체 종목", "닫기", onClick = onDismiss)   // ios: Views/FlowView.swift:117,118
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {   // ios: Views/FlowView.swift:106
            ids.forEach { id ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Theme.card).plainClickable { onPick(id) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/FlowView.swift:107,108
                    T(if (id == "DRNK") "DRNK · " + name(id) else name(id), color = Theme.ink)   // ios: Views/FlowView.swift:109
                    Spacer(Modifier.weight(1f))   // ios: Views/FlowView.swift:110
                    if (id == "DRNK") T("샘플", style = appFont(12f), color = Theme.muted)   // ios: Views/FlowView.swift:111
                    else if (!FlowStore.canFetch(id)) T("키 필요", style = appFont(12f), color = Theme.muted)   // ios: Views/FlowView.swift:112
                    if (cur == id) Sym("checkmark", 17f, Theme.teal, Modifier.padding(start = 8.dp))   // ios: Views/FlowView.swift:113
                }
            }
        }
    }
}

// 겹 켜고 끄기. 칸 크기는 늘 같고 색만 바뀐다
@Composable
private fun LayerRow(layers: Set<FlowLayer>, set: (Set<FlowLayer>) -> Unit) {   // ios: Views/FlowView.swift:124
    val all = layers.size == FlowLayer.entries.size   // ios: Views/FlowView.swift:125
    // ios: Views/FlowView.swift:126,127
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowLayer.entries.forEach { l ->   // ios: Views/FlowView.swift:128
            val on = l in layers   // ios: Views/FlowView.swift:129
            // ios: Views/FlowView.swift:130,131,132,135,136,137,139 (겹 투명도는 FlowChart 가 같은 0.2초로 바꾼다)
            val bg by animateColorAsState(if (on) Theme.tealBg else Theme.card, tween(200), label = "layerBg")
            Box(Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                .border(1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(10.dp))
                .plainClickable { set(if (on) layers - l else layers + l) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                T(l.title, style = appFont(13f, FontWeight.SemiBold), color = if (on) Theme.tealDark else Theme.sub)   // ios: Views/FlowView.swift:133,134
            }
        }
        // ios: Views/FlowView.swift:141,142,143,149,151
        Box(Modifier.heightIn(min = 34.dp).plainClickable { set(if (all) setOf(FlowLayer.trend) else FlowLayer.entries.toSet()) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            // 두 글자 폭을 같게 두어 바뀔 때 줄이 흔들리지 않게
            val st = appFont(13f, FontWeight.SemiBold)   // ios: Views/FlowView.swift:145,149
            val aOn by animateFloatAsState(if (all) 0f else 1f, tween(200), label = "allOn"); T("모두 켜기", Modifier.graphicsLayer { alpha = aOn }, style = st, color = Theme.teal)   // ios: Views/FlowView.swift:142,146
            val aOff by animateFloatAsState(if (all) 1f else 0f, tween(200), label = "allOff"); T("추세만", Modifier.graphicsLayer { alpha = aOff }, style = st, color = Theme.teal)   // ios: Views/FlowView.swift:147
        }
    }
}

// 차트 아래 고정 높이 설명 칸
@Composable
private fun Explain(term: FlowTerm?) {   // ios: Views/FlowView.swift:157
    // ios: Views/FlowView.swift:158,169,170,171,172
    Column(Modifier.fillMaxWidth().heightIn(min = 132.dp, max = 132.dp).background(Theme.card, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (term != null) {   // ios: Views/FlowView.swift:159
            T(term.title, style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/FlowView.swift:160
            T(term.body, style = appFont(13f), color = Theme.sub)   // ios: Views/FlowView.swift:161
        } else {   // ios: Views/FlowView.swift:162
            T("이름표나 선을 눌러 보세요", style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/FlowView.swift:163
            // ios: Views/FlowView.swift:164,165
            T("누른 것의 뜻만 여기에 떠요. 흐린 점선 이름표는 확정 전이거나 거래량이 적었던 것이에요. 정보 제공용이며 매매 권유가 아니에요.", style = appFont(13f), color = Theme.sub)
        }
        Spacer(Modifier.heightIn(min = 0.dp))   // ios: Views/FlowView.swift:167
    }
}

enum class FlowLayer {   // ios: Views/FlowView.swift:176
    trend, levels, big, candle;   // ios: Views/FlowView.swift:177
    val title: String get() = when (this) {   // ios: Views/FlowView.swift:178,179
        trend -> "추세"   // ios: Views/FlowView.swift:180
        levels -> "지지·저항"   // ios: Views/FlowView.swift:181
        big -> "큰 패턴"   // ios: Views/FlowView.swift:182
        candle -> "캔들 패턴"   // ios: Views/FlowView.swift:183
    }
}

/** 설명 칸에 띄울 용어 */
sealed class FlowTerm {   // ios: Views/FlowView.swift:189
    // ios: Views/FlowView.swift:190
    object dots : FlowTerm(); object band : FlowTerm(); object support : FlowTerm(); object resistance : FlowTerm(); data class pattern(val k: FlowPattern.Kind) : FlowTerm()
    val title: String get() = when (this) {   // ios: Views/FlowView.swift:191,192
        dots -> "추세 점"   // ios: Views/FlowView.swift:193
        band -> "추세 띠"   // ios: Views/FlowView.swift:194
        support -> "지지선"   // ios: Views/FlowView.swift:195
        resistance -> "저항선"   // ios: Views/FlowView.swift:196
        is pattern -> k.title   // ios: Views/FlowView.swift:197
    }
    val body: String get() = when (this) {   // ios: Views/FlowView.swift:200,201
        dots -> "고점·저점마다 찍은 점이에요. 점이 계단처럼 올라가면 상승, 내려가면 하락, 같은 높이를 오가면 횡보예요."   // ios: Views/FlowView.swift:202
        band -> "위에서부터 큰·중간·작은 흐름이에요. 빨강은 상승, 파랑은 하락, 회색은 횡보예요."   // ios: Views/FlowView.swift:203
        support -> "주가가 여러 번 내려와 받친 가격이에요. 여러 번 닿을수록 선이 진해요."   // ios: Views/FlowView.swift:204
        resistance -> "주가가 여러 번 올라와 튕겨 나간 가격이에요. 여러 번 닿을수록 선이 진해요."   // ios: Views/FlowView.swift:205
        is pattern -> k.body   // ios: Views/FlowView.swift:206
    }
}

// ios: Views/FlowView.swift:211,212,213
val FlowPattern.Kind.title: String get() = when (this) {
    FlowPattern.Kind.doubleBottom -> "더블바텀"   // ios: Views/FlowView.swift:214
    FlowPattern.Kind.doubleTop -> "더블탑"   // ios: Views/FlowView.swift:215
    FlowPattern.Kind.headShoulders -> "헤드앤숄더"   // ios: Views/FlowView.swift:216
    FlowPattern.Kind.ascTriangle -> "상승삼각형"   // ios: Views/FlowView.swift:217
    FlowPattern.Kind.descTriangle -> "하락삼각형"   // ios: Views/FlowView.swift:218
    FlowPattern.Kind.bullFlag, FlowPattern.Kind.bearFlag -> "플래그"   // ios: Views/FlowView.swift:219
    FlowPattern.Kind.hammer -> "해머"   // ios: Views/FlowView.swift:220
    FlowPattern.Kind.shootingStar -> "유성"   // ios: Views/FlowView.swift:221
    FlowPattern.Kind.bullEngulf -> "강세 포위형"   // ios: Views/FlowView.swift:222
    FlowPattern.Kind.bearEngulf -> "약세 포위형"   // ios: Views/FlowView.swift:223
    FlowPattern.Kind.insideBar -> "인사이드바"   // ios: Views/FlowView.swift:224
}
// ios: Views/FlowView.swift:227,228
val FlowPattern.Kind.body: String get() = when (this) {
    FlowPattern.Kind.doubleBottom -> "비슷한 높이의 바닥이 두 번 생긴 모양(W)이에요. 두 바닥 사이 고점을 종가가 넘으면 확정이에요."   // ios: Views/FlowView.swift:229
    FlowPattern.Kind.doubleTop -> "비슷한 높이의 꼭대기가 두 번 생긴 모양(M)이에요. 두 꼭대기 사이 저점 아래로 종가가 내려가면 확정이에요."   // ios: Views/FlowView.swift:230
    FlowPattern.Kind.headShoulders -> "가운데 꼭대기가 양옆보다 높은 세 봉우리예요. 두 골짜기를 이은 선 아래로 내려가면 확정이에요."   // ios: Views/FlowView.swift:231
    FlowPattern.Kind.ascTriangle -> "꼭대기는 같은 높이인데 바닥이 점점 높아지는 모양이에요. 그 꼭대기를 종가가 넘으면 확정이에요."   // ios: Views/FlowView.swift:232
    FlowPattern.Kind.descTriangle -> "바닥은 같은 높이인데 꼭대기가 점점 낮아지는 모양이에요. 그 바닥 아래로 종가가 내려가면 확정이에요."   // ios: Views/FlowView.swift:233
    FlowPattern.Kind.bullFlag -> "짧게 크게 오른 뒤(깃대) 잠시 쉬는 모양이에요. 쉬던 자리를 위로 벗어나면 확정이에요."   // ios: Views/FlowView.swift:234
    FlowPattern.Kind.bearFlag -> "짧게 크게 내린 뒤(깃대) 잠시 쉬는 모양이에요. 쉬던 자리를 아래로 벗어나면 확정이에요."   // ios: Views/FlowView.swift:235
    FlowPattern.Kind.hammer -> "아래꼬리가 몸통보다 훨씬 긴 캔들이에요. 내려갔다가 다시 끌어올린 날이라는 뜻이에요."   // ios: Views/FlowView.swift:236
    FlowPattern.Kind.shootingStar -> "위꼬리가 몸통보다 훨씬 긴 캔들이에요. 올라갔다가 다시 밀려 내려온 날이라는 뜻이에요."   // ios: Views/FlowView.swift:237
    FlowPattern.Kind.bullEngulf -> "전날 음봉 몸통을 다음 날 양봉 몸통이 다 덮은 모양이에요."   // ios: Views/FlowView.swift:238
    FlowPattern.Kind.bearEngulf -> "전날 양봉 몸통을 다음 날 음봉 몸통이 다 덮은 모양이에요."   // ios: Views/FlowView.swift:239
    FlowPattern.Kind.insideBar -> "전날 고가·저가 안에서만 움직인 날이에요. 힘이 잠시 멈춘 자리예요."   // ios: Views/FlowView.swift:240
}

/** 이름표 자리 잡기에 넘기는 것 */
class TagWant(val key: String, val layer: FlowLayer, val strong: Boolean, val w: Float, val cands: List<Offset>)   // ios: Views/FlowView.swift:485

/** .position(x:y:): 가운데를 (x, y) dp 에 둔다 (부모 Box 왼쪽 위 기준) */
private fun Modifier.centerAt(x: Float, y: Float): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(x.dp.roundToPx() - p.width / 2, y.dp.roundToPx() - p.height / 2) }
}

/** 캔들 차트. 보이는 구간의 캔들만 그리고, 넓게 보면 5일·21일씩 묶어 주봉·월봉으로 접는다 */
@Composable
fun FlowChart(series: FlowSeries, title: String, layers: Set<FlowLayer>, onTerm: (FlowTerm) -> Unit) {   // ios: Views/FlowView.swift:246,247,248,249,250
    var count by remember { mutableStateOf(160) }          // 보이는 날 수   // ios: Views/FlowView.swift:251
    var end by remember { mutableStateOf<Int?>(null) }      // 맨 오른쪽 날 (null = 마지막 날)   // ios: Views/FlowView.swift:252
    var pinchBase by remember { mutableStateOf<Int?>(null) }   // ios: Views/FlowView.swift:253
    var dragBase by remember { mutableStateOf<Int?>(null) }   // ios: Views/FlowView.swift:254
    val axisW = 46f; val bandH = 36f; val topH = 68f; val botH = 20f   // ios: Views/FlowView.swift:256
    val haptic = LocalHapticFeedback.current
    val dens = LocalDensity.current.density
    // 겹 투명도: 켜고 끌 때 0.2초에 걸쳐 (자리는 그대로)
    val aTrend by animateFloatAsState(if (FlowLayer.trend in layers) 1f else 0f, tween(200), label = "trend")   // ios: Views/FlowView.swift:383
    val aLevels by animateFloatAsState(if (FlowLayer.levels in layers) 1f else 0f, tween(200), label = "levels")   // ios: Views/FlowView.swift:406
    val aBig by animateFloatAsState(if (FlowLayer.big in layers) 1f else 0f, tween(200), label = "big")   // ios: Views/FlowView.swift:425
    val aCandle by animateFloatAsState(if (FlowLayer.candle in layers) 1f else 0f, tween(200), label = "candle")   // ios: Views/FlowView.swift:436
    val up = Theme.up; val down = Theme.down; val dash = Theme.dash; val teal = Theme.teal; val card = Theme.card; val line = Theme.line; val purple = Theme.purple

    // ios: Views/FlowView.swift:258,259,475,476
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        val k = series.candles; val n = k.size   // ios: Views/FlowView.swift:260
        val cnt = max(20, min(n, count))   // ios: Views/FlowView.swift:261
        val hi = max(cnt - 1, min(n - 1, end ?: (n - 1))); val lo = hi - cnt + 1   // ios: Views/FlowView.swift:262
        val step = if (cnt <= 200) 1 else if (cnt <= 600) 5 else 21   // ios: Views/FlowView.swift:263
        val fullH = maxHeight.value; val W = maxWidth.value - axisW; val top = topH + bandH + 12; val H = fullH - top - botH   // ios: Views/FlowView.swift:264
        var pMin = Double.MAX_VALUE; var pMax = -Double.MAX_VALUE; for (i in lo..hi) { pMin = min(pMin, k[i].l); pMax = max(pMax, k[i].h) }   // ios: Views/FlowView.swift:265
        if (pMin > pMax) { pMin = 0.0; pMax = 1.0 }   // ios: Views/FlowView.swift:266
        val pad = (pMax - pMin) * 0.08   // ios: Views/FlowView.swift:267
        val yMin = pMin - pad; val yMax = pMax + pad   // ios: Views/FlowView.swift:268
        val x = { i: Double -> ((i - lo + 0.5) / cnt).toFloat() * W }   // ios: Views/FlowView.swift:269
        val y = { v: Double -> top + (1 - (v - yMin) / max(1e-12, yMax - yMin)).toFloat() * H }   // ios: Views/FlowView.swift:270
        val last = k[hi]   // ios: Views/FlowView.swift:271
        val piv = series.pivotSets[if (step == 1) 0 else if (step == 5) 1 else 2]   // ios: Views/FlowView.swift:272
        // 지지·저항: 보이는 것 중 많이 닿은 4개. 이름표는 위아래로 붙으면 뒤의 것을 뺀다
        val lvls = series.levels.filter { it.price >= yMin && it.price <= yMax && it.from <= hi }.take(4)   // ios: Views/FlowView.swift:274
        // 월봉으로 볼 땐 패턴 없이 추세·지지/저항만 (한 칸이 한 달이라 몇 주짜리 모양은 뜻이 없다)
        val bigs = if (step == 21) emptyList() else series.patterns.filter { it.big && it.to >= lo && it.from <= hi }   // ios: Views/FlowView.swift:276
        val smalls = if (step == 1) series.patterns.filter { !it.big && it.to >= lo && it.to <= hi } else emptyList()   // ios: Views/FlowView.swift:277
        // 큰 패턴 틀: 구간 안 최고가·최저가
        val box = { pt: FlowPattern ->   // ios: Views/FlowView.swift:279
            val a = x(max(lo, pt.from).toDouble() - 0.5); val b = x(min(hi, pt.to).toDouble() + 0.5)   // ios: Views/FlowView.swift:280
            var sh = -Double.MAX_VALUE; var sl = Double.MAX_VALUE; for (i in max(lo, pt.from)..min(hi, pt.to)) { sh = max(sh, k[i].h); sl = min(sl, k[i].l) }   // ios: Views/FlowView.swift:281
            floatArrayOf(a, b, y(sh) - 4, y(sl) + 4)   // ios: Views/FlowView.swift:282
        }
        // ios: Views/FlowView.swift:284
        val spots = placeTags(levels = lvls.map { lv ->
            val w = tagW(levelText(lv)); val yy = y(lv.price); val cx = W - w / 2 - 2   // ios: Views/FlowView.swift:285
            TagWant("L${lv.id}", FlowLayer.levels, true, w, listOf(Offset(cx, yy), Offset(cx, yy - 12), Offset(cx, yy + 12)))   // ios: Views/FlowView.swift:286
        }, big = bigs.map { pt ->   // ios: Views/FlowView.swift:287
            val (a, b, t, bt) = box(pt); val w = tagW(pt.kind.title); val cx = (a + b) / 2   // ios: Views/FlowView.swift:288
            val up0 = t - 13; val dn = bt + 13   // ios: Views/FlowView.swift:289
            val pref = if (pt.below) listOf(dn, bt - 13, up0) else listOf(up0, t + 13, dn)   // ios: Views/FlowView.swift:290
            // ios: Views/FlowView.swift:291,292
            TagWant("B${pt.id}", FlowLayer.big, pt.strong, w, pref.map { Offset(cx, it) } + listOf(Offset(cx - w, pref[0]), Offset(cx + w, pref[0])))
        }, candle = smalls.map { pt ->   // ios: Views/FlowView.swift:293
            val w = tagW(pt.kind.title); val cx = x((pt.from + pt.to) / 2.0); val yy = y(pt.price)   // ios: Views/FlowView.swift:294
            val other = if (pt.below) y(k[pt.to].h) - 14 else y(k[pt.to].l) + 14   // ios: Views/FlowView.swift:295
            val d = if (pt.below) 1f else -1f   // ios: Views/FlowView.swift:296
            // ios: Views/FlowView.swift:297,298
            TagWant("C${pt.id}", FlowLayer.candle, pt.strong, w, listOf(Offset(cx, yy + 14 * d), Offset(cx, yy + 34 * d), Offset(cx, other), Offset(cx, yy + 54 * d)))
        }, on = layers, bounds = Rect(0f, top - 6, W, top - 6 + H + 14))   // ios: Views/FlowView.swift:299

        LaunchedEffect(step) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }   // ios: Views/FlowView.swift:473

        // ios: Views/FlowView.swift:301,444
        Box(
            Modifier.fillMaxSize()
                // 점·선 근처를 누르면 그 뜻. 이름표는 누를 수 있는 칸이라 먼저 받는다
                .pointerInput(lo, hi, layers, W, H) {   // ios: Views/FlowView.swift:446
                    detectTapGestures { o ->
                        val lx = o.x / dens; val ly = o.y / dens
                        // ios: Views/FlowView.swift:447,448,449
                        val lv = if (FlowLayer.levels in layers) lvls.minByOrNull { abs(y(it.price) - ly) } else null
                        if (lv != null && abs(y(lv.price) - ly) < 10) { onTerm(if (lv.support) FlowTerm.support else FlowTerm.resistance); return@detectTapGestures }
                        // ios: Views/FlowView.swift:451,452
                        if (FlowLayer.trend in layers && piv.any { it.i in lo..hi && hypot(x(it.i.toDouble()) - lx, y(it.price) - ly) < 22 }) onTerm(FlowTerm.dots)
                    }
                }
                // 이름표 위에서 시작해도 밀기·핀치가 먹게: 두 손가락은 확대, 한 손가락은 옆으로 밀 때만 (세로는 화면 스크롤에 넘긴다)
                .pointerInput(n, W) {   // ios: Views/FlowView.swift:456,457,465
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var side: Boolean? = null; var acc = Offset.Zero; var mag = 1f; var tx = 0f
                        do {
                            val ev = awaitPointerEvent()
                            val down = ev.changes.count { it.pressed }
                            if (down >= 2) {
                                mag *= ev.calculateZoom()
                                val base = pinchBase ?: count   // ios: Views/FlowView.swift:467
                                if (pinchBase == null) pinchBase = count   // ios: Views/FlowView.swift:468
                                count = max(20, min(n, (base / max(0.05f, mag)).toInt()))   // ios: Views/FlowView.swift:466,469
                                ev.changes.forEach { it.consume() }
                            } else if (down == 1) {
                                val d = ev.calculatePan()
                                if (side == null) { acc += d; if (acc.getDistance() > 8 * dens) side = abs(acc.x) > abs(acc.y) }   // ios: Views/FlowView.swift:457 (minimumDistance 8. 세로면 화면 스크롤에 넘긴다)
                                if (side == true) {
                                    tx += d.x / dens
                                    val c0 = max(20, min(n, count)); val h0 = max(c0 - 1, min(n - 1, end ?: (n - 1)))
                                    val base = dragBase ?: h0   // ios: Views/FlowView.swift:458,459
                                    if (dragBase == null) dragBase = h0   // ios: Views/FlowView.swift:460
                                    val shift = (tx / max(1f, W) * c0).roundToInt()   // ios: Views/FlowView.swift:461
                                    end = max(c0 - 1, min(n - 1, base - shift))   // ios: Views/FlowView.swift:462
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                        dragBase = null; pinchBase = null   // ios: Views/FlowView.swift:464,471
                    }
                },
        ) {
            // 머리: 종목·마지막 값 (시작 대비) + 지금 단위
            // ios: Views/FlowView.swift:303,316
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Views/FlowView.swift:304
                    T(title, style = appFont(12f), color = Theme.muted, maxLines = 1)   // ios: Views/FlowView.swift:305
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/FlowView.swift:306
                        // ios: Views/FlowView.swift:307,308
                        T(cformat("%+.0f%%", series.pct(last.c)), Modifier.alignByBaseline(), style = appFont(20f, FontWeight.Bold).copy(fontFeatureSettings = "tnum"), color = Theme.change(series.pct(last.c)))
                        T("시작 대비", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/FlowView.swift:309
                    }
                }
                Spacer(Modifier.weight(1f))   // ios: Views/FlowView.swift:312
                // ios: Views/FlowView.swift:313,314
                T(if (step == 1) "일봉" else if (step == 5) "주봉" else "월봉", Modifier.background(Theme.line, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), style = appFont(12f, FontWeight.SemiBold), color = Theme.sub)
            }

            // 오른쪽 눈금 (+x% 시작 대비)
            val ticks = (0 until 4).map { t -> yMin + (yMax - yMin) * (0.1 + 0.8 * t / 3.0) }   // ios: Views/FlowView.swift:319,320
            Canvas(Modifier.fillMaxSize()) { ticks.forEach { v -> drawLine(line, Offset(0f, y(v) * dens), Offset(W * dens, y(v) * dens), 1.dp.toPx()) } }   // ios: Views/FlowView.swift:321,322
            ticks.forEach { v ->
                // ios: Views/FlowView.swift:323,324,325
                T(cformat("%+.0f%%", series.pct(v)), Modifier.width((axisW - 4).dp).centerAt(W + axisW / 2 + 2, y(v)), style = appFont(10f).copy(fontFeatureSettings = "tnum"), color = Theme.muted, maxLines = 1)
            }

            // 캔들 (늘 보임)
            Canvas(Modifier.width(W.dp).fillMaxSize().clipToBounds()) {   // ios: Views/FlowView.swift:329,346
                var g0 = lo / step   // ios: Views/FlowView.swift:330
                while (g0 * step <= hi) {   // ios: Views/FlowView.swift:331
                    val a = max(lo, g0 * step); val b = min(hi, g0 * step + step - 1)   // ios: Views/FlowView.swift:332
                    val o = k[a].o; val c = k[b].c   // ios: Views/FlowView.swift:333
                    var h = -Double.MAX_VALUE; var l = Double.MAX_VALUE   // ios: Views/FlowView.swift:334
                    for (i in a..b) { h = max(h, k[i].h); l = min(l, k[i].l) }   // ios: Views/FlowView.swift:335
                    val cx = x((a + b) / 2.0)   // ios: Views/FlowView.swift:336
                    val bw = max(1f, (b - a + 1).toFloat() / cnt * W * 0.68f)   // ios: Views/FlowView.swift:337
                    val col = if (c >= o) up else down   // ios: Views/FlowView.swift:338
                    drawLine(col, Offset(cx * dens, y(h) * dens), Offset(cx * dens, y(l) * dens), 1.dp.toPx())   // ios: Views/FlowView.swift:339,340
                    drawRect(col, Offset((cx - bw / 2) * dens, y(max(o, c)) * dens), Size(bw * dens, max(1f, abs(y(o) - y(c))) * dens))   // ios: Views/FlowView.swift:341,342
                    g0 += 1   // ios: Views/FlowView.swift:343
                }
            }

            // 겹 1: 추세 띠 + 추세 점
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aTrend }) {   // ios: Views/FlowView.swift:349,383
                Canvas(Modifier.width(W.dp).fillMaxSize().clipToBounds()) {   // ios: Views/FlowView.swift:350,372
                    for ((row, ts) in series.trends.reversed().withIndex()) {   // ios: Views/FlowView.swift:351
                        for (t in ts) { if (!(t.to >= lo && t.from <= hi)) continue   // ios: Views/FlowView.swift:352
                            val a = x(max(lo, t.from) - 0.5); val b = x(min(hi, t.to) + 0.5)   // ios: Views/FlowView.swift:353
                            // ios: Views/FlowView.swift:354,355
                            drawRect(if (t.dir > 0) up else if (t.dir < 0) down else dash, Offset(a * dens, (topH + row * (bandH / 3) + 1.5f) * dens), Size(max(1f, b - a) * dens, (bandH / 3 - 3) * dens))
                        }
                    }
                    val vis = piv.filter { it.i >= lo - 40 && it.i <= hi + 40 }   // ios: Views/FlowView.swift:358
                    val path = Path()   // ios: Views/FlowView.swift:359
                    vis.forEachIndexed { j, p ->   // ios: Views/FlowView.swift:360
                        val px = x(p.i.toDouble()) * dens; val py = (y(p.price) + if (p.high) -5 else 5) * dens   // ios: Views/FlowView.swift:361
                        if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)   // ios: Views/FlowView.swift:362
                    }
                    drawPath(path, teal.copy(alpha = 0.55f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))   // ios: Views/FlowView.swift:364
                    for (p in vis) { if (!(p.i >= lo && p.i <= hi)) continue   // ios: Views/FlowView.swift:365
                        val c = Offset(x(p.i.toDouble()) * dens, (y(p.price) + if (p.high) -5 else 5) * dens)   // ios: Views/FlowView.swift:366
                        val r = Rect(Offset(c.x - 3.5f * dens, c.y - 3.5f * dens), Size(7f * dens, 7f * dens))   // ios: Views/FlowView.swift:367
                        drawOval(teal, r.topLeft, r.size)   // ios: Views/FlowView.swift:368
                        drawOval(card, r.topLeft, r.size, style = Stroke(1.2.dp.toPx()))   // ios: Views/FlowView.swift:369
                    }
                }
                // 띠 이름: 오른쪽 눈금 칸에 아주 작게 (차트 폭은 그대로)
                listOf("큰", "중간", "작은").forEachIndexed { row, t ->   // ios: Views/FlowView.swift:374
                    T(t, Modifier.centerAt(W + 4 + 12, topH + row * (bandH / 3) + bandH / 6), style = appFont(8f), color = Theme.muted, maxLines = 1)   // ios: Views/FlowView.swift:375,376
                }
                // 띠를 누르면 띠 설명
                // ios: Views/FlowView.swift:379,380,381,384
                if (FlowLayer.trend in layers) Box(Modifier.width(W.dp).height((bandH + 6).dp).layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.place(0, (topH - 3).dp.roundToPx()) } }.plainClickable { onTerm(FlowTerm.band) })
            }

            // 겹 2: 지지·저항 가로선
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aLevels }) {   // ios: Views/FlowView.swift:387,406
                Canvas(Modifier.fillMaxSize()) {
                    for (lv in lvls) {   // ios: Views/FlowView.swift:388
                        val a = max(0f, x(max(lo, lv.from) - 0.5)); val yy = y(lv.price)   // ios: Views/FlowView.swift:389
                        val col = if (lv.support) up else down   // ios: Views/FlowView.swift:390
                        val op = min(1.0, 0.25 + 0.2 * lv.touches).toFloat()   // ios: Views/FlowView.swift:391
                        drawLine(col.copy(alpha = op), Offset(a * dens, yy * dens), Offset(W * dens, yy * dens), (if (lv.touches >= 4) 2f else 1.2f).dp.toPx())   // ios: Views/FlowView.swift:392,393
                    }
                }
                for (lv in lvls) {
                    val sp = spots["L${lv.id}"] ?: continue   // ios: Views/FlowView.swift:394
                    val col = if (lv.support) up else down; val op = min(1.0, 0.25 + 0.2 * lv.touches).toFloat()
                    // ios: Views/FlowView.swift:395,396,397,398,399,401,402,407
                    T(levelText(lv), Modifier.centerAt(sp.x, sp.y).heightIn(min = 24.dp).then(if (FlowLayer.levels in layers) Modifier.plainClickable { onTerm(if (lv.support) FlowTerm.support else FlowTerm.resistance) } else Modifier)
                        .padding(vertical = 4.dp).background(card.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 4.dp),
                        style = appFont(10f, FontWeight.SemiBold), color = col.copy(alpha = max(0.7f, op)), maxLines = 1)
                }
            }

            // 겹 3: 큰 패턴 (구간을 묶는 틀 + 이름표)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aBig }) {   // ios: Views/FlowView.swift:410,425
                Canvas(Modifier.fillMaxSize()) {
                    for (pt in bigs) {   // ios: Views/FlowView.swift:411
                        val (a, b, t, bt) = box(pt)   // ios: Views/FlowView.swift:412
                        val col = purple   // ios: Views/FlowView.swift:413
                        val sz = Size(max(4f, b - a) * dens, max(4f, bt - t) * dens); val r = CornerRadius(6.dp.toPx())   // ios: Views/FlowView.swift:414,417
                        val ctr = Offset((a + b) / 2 * dens, (t + bt) / 2 * dens); val tl = Offset(ctr.x - sz.width / 2, ctr.y - sz.height / 2)   // ios: Views/FlowView.swift:418
                        drawRoundRect(col.copy(alpha = if (pt.strong) 0.07f else 0.03f), tl, sz, CornerRadius(6.dp.toPx()))   // ios: Views/FlowView.swift:416
                        // ios: Views/FlowView.swift:415,419
                        drawRoundRect(col.copy(alpha = if (pt.strong) 0.9f else 0.45f), tl, sz, r, style = Stroke((if (pt.strong) 1.5f else 1f).dp.toPx(), pathEffect = if (pt.strong) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    }
                }
                for (pt in bigs) {
                    val sp = spots["B${pt.id}"] ?: continue   // ios: Views/FlowView.swift:420
                    Tag(pt.kind.title, purple, pt.strong, Modifier.centerAt(sp.x, sp.y), FlowLayer.big in layers) { onTerm(FlowTerm.pattern(pt.kind)) }   // ios: Views/FlowView.swift:421,426
                }
            }

            // 겹 4: 캔들 패턴 (일봉으로 볼 때만. 주봉·월봉에선 캔들 하나가 하루가 아니라 뜻이 달라진다)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = aCandle }) {   // ios: Views/FlowView.swift:429,436
                for (pt in smalls) {   // ios: Views/FlowView.swift:430
                    val sp = spots["C${pt.id}"] ?: continue   // ios: Views/FlowView.swift:431
                    Tag(pt.kind.title, Theme.orange, pt.strong, Modifier.centerAt(sp.x, sp.y), FlowLayer.candle in layers) { onTerm(FlowTerm.pattern(pt.kind)) }   // ios: Views/FlowView.swift:432,437
                }
            }

            // 아래: 보이는 기간
            // ios: Views/FlowView.swift:440,441,442
            T(date(series, lo) + " – " + date(series, hi), Modifier.centerAt(W / 2, fullH - botH / 2), style = appFont(10f).copy(fontFeatureSettings = "tnum"), color = Theme.muted, maxLines = 1)
        }
    }
}

private fun levelText(lv: FlowLevel): String =   // ios: Views/FlowView.swift:479
    (if (lv.support) "지지" else "저항") + " ×${lv.touches}"   // ios: Views/FlowView.swift:480
/** 10pt 굵은 글자 이름표 폭 어림 (한글 10.5, 영문 6.2 + 여백) */
private fun tagW(t0: String): Float { val t = com.naeilo.app.core.tr(t0); return t.fold(14f) { a, ch -> a + if (ch.code < 128) 6.2f else 10.5f } }   // ios: Views/FlowView.swift:483

/** 이름표 자리 잡기: 지지·저항 → 큰 패턴 → 캔들 패턴 순서로, 켜진 앞 겹 이름표와 이미 놓인 것을 피해
 *  후보 자리를 차례로 본다. 진한 것부터 놓으니 끝내 겹치면 약한 쪽이 숨는다.
 *  뒤 겹을 켜고 꺼도 앞 겹 이름표는 움직이지 않는다 */
fun placeTags(levels: List<TagWant>, big: List<TagWant>, candle: List<TagWant>, on: Set<FlowLayer>, bounds: Rect): Map<String, Offset> {   // ios: Views/FlowView.swift:490
    val out = HashMap<String, Offset>(); val taken = ArrayList<Rect>()   // ios: Views/FlowView.swift:491
    for (group in listOf(levels, big, candle)) {   // ios: Views/FlowView.swift:492
        val mine = ArrayList<Rect>()   // ios: Views/FlowView.swift:493
        for (t in group.sortedBy { if (it.strong) 0 else 1 }) {   // ios: Views/FlowView.swift:494
            for (c in t.cands) {   // ios: Views/FlowView.swift:495
                val cx = min(max(c.x, bounds.left + t.w / 2), bounds.right - t.w / 2)   // ios: Views/FlowView.swift:496
                val cy = min(max(c.y, bounds.top + 9), bounds.bottom - 9)   // ios: Views/FlowView.swift:497
                val r = Rect(cx - t.w / 2, cy - 9, cx + t.w / 2, cy - 9 + 18)   // ios: Views/FlowView.swift:498
                if ((taken + mine).none { it.overlaps(r) }) {   // ios: Views/FlowView.swift:499
                    out[t.key] = Offset(cx, cy); mine.add(r); break   // ios: Views/FlowView.swift:500
                }
            }
        }
        // 이 겹이 켜져 있을 때만 뒤 겹이 피한다
        val l = group.firstOrNull()?.layer; if (l != null && l in on) taken += mine   // ios: Views/FlowView.swift:505
    }
    return out   // ios: Views/FlowView.swift:507
}

/** 이름표: 확정은 진하게, 약한 것은 흐린 점선 테두리 */
@Composable
private fun Tag(t: String, col: Color, strong: Boolean, modifier: Modifier, enabled: Boolean, tap: () -> Unit) {   // ios: Views/FlowView.swift:511
    val card = Theme.card
    // ios: Views/FlowView.swift:512,515,516,517,518,519,521
    Box(modifier.heightIn(min = 24.dp).then(if (enabled) Modifier.plainClickable(tap) else Modifier), contentAlignment = Alignment.Center) {
        T(t, Modifier.graphicsLayer { alpha = if (strong) 1f else 0.75f }
            .background(if (strong) col else card.copy(alpha = 0.85f), RoundedCornerShape(50))
            .drawBehind { drawRoundRect(col.copy(alpha = if (strong) 0f else 0.6f), cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
            style = appFont(10f, FontWeight.Bold), color = if (strong) Color.White else col.copy(alpha = 0.8f), maxLines = 1)   // ios: Views/FlowView.swift:513,514
    }
}

/** 실데이터는 거래일, DRNK 기록은 어제가 마지막 날로 하루에 한 칸 */
private fun date(series: FlowSeries, i: Int): String {   // ios: Views/FlowView.swift:525
    series.dates?.let { d -> if (i < d.size) return d[i].replace("-", ".") }   // ios: Views/FlowView.swift:526
    return fmt.format(Instant.ofEpochSecond(Instant.now().epochSecond - (series.candles.size - i) * 86400L))   // ios: Views/FlowView.swift:527
}
private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US).withZone(ZoneId.systemDefault())   // ios: Views/FlowView.swift:529
