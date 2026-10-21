package com.naeilo.app.ui.wave

import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naeilo.app.AppModel
import com.naeilo.app.core.calc.WaveEngine
import com.naeilo.app.core.calc.waveLine
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.DashedCard
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTextScale
import com.naeilo.app.ui.NavBack
import com.naeilo.app.ui.SF
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Spinner
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.plainClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

typealias Island = WaveEngine.Island

private fun lerp(a: Double, b: Double, t: Float): Double = a + (b - a) * t

/** 파도 타기 섬 진행 저장: 섬마다 최고 점수·별 (앱 설정 정수 몇 개)
 *  섬은 낮잠 해변 → 올라올라 언덕 → 롤러코스터 만 → 멀미 주의 바다 순서로, 앞 섬에서 별 1개를 받으면 다음 섬이 열린다. */
// ios: Views/Wave/WaveHomeView.swift:5
object WaveMap {
    const val minDays = 10   // ios: Views/Wave/WaveHomeView.swift:6 이보다 짧으면 섬을 못 만든다
    val order: List<Island> = listOf(Island.nap, Island.climb, Island.coaster, Island.choppy)   // ios: Views/Wave/WaveHomeView.swift:7
    private val d: SharedPreferences get() = SurfCloset.store   // ios: Views/Wave/WaveHomeView.swift:8 처음 읽을 때 따로 둔 한 벌에서 되살린다

    fun stars(i: Island): Int = d.getInt("wave.star.${i.raw}", 0)   // ios: Views/Wave/WaveHomeView.swift:10
    fun best(i: Island): Int = d.getInt("wave.best.${i.raw}", 0)   // ios: Views/Wave/WaveHomeView.swift:11
    /** 열린 섬 수 (1...4): 앞에서부터 별이 있는 섬 다음까지 */
    val opened: Int get() = min(order.size, 1 + (order.indexOfFirst { stars(it) == 0 }.takeIf { it >= 0 } ?: order.size))   // ios: Views/Wave/WaveHomeView.swift:13
    fun isOpen(i: Island): Boolean = (order.indexOf(i).takeIf { it >= 0 } ?: 0) < opened   // ios: Views/Wave/WaveHomeView.swift:14

    /** 한 판 결과 적기 (판이 끝날 때). 별은 결과 팝업과 같은 WaveResult.stars. 별·최고 점수는 줄지 않는다 */
    // ios: Views/Wave/WaveHomeView.swift:17
    fun record(r: WaveResult) {
        val i = r.course.island   // ios: Views/Wave/WaveHomeView.swift:18
        if (r.stars > stars(i)) d.edit().putInt("wave.star.${i.raw}", r.stars).apply()   // ios: Views/Wave/WaveHomeView.swift:19
        if (r.score > best(i)) d.edit().putInt("wave.best.${i.raw}", r.score).apply()   // ios: Views/Wave/WaveHomeView.swift:20
        SurfCloset.keep()   // ios: Views/Wave/WaveHomeView.swift:21
    }

    private class Fit(val s: Int, val sd: Double, val dd: Double, val total: Double)

    /** 3년 자산 라인을 40거래일씩 훑어 섬마다 들어갈 수 있는 시작 위치를 모은다 (3거래일 간격, 한 번만).
     *  딱 맞는 구간이 없는 섬(예: 주식 위주라 잔잔한 40일이 없으면 낮잠 해변)은 그 섬에 가장 가까운 구간 5개를 쓴다 */
    // ios: Views/Wave/WaveHomeView.swift:26
    fun starts(line: List<WaveEngine.Pt>): Map<Island, List<Int>> {
        val n = min(WaveEngine.days, line.size)   // ios: Views/Wave/WaveHomeView.swift:27 시세가 40거래일보다 짧으면 가진 만큼만
        if (!(n >= minDays)) return emptyMap()   // ios: Views/Wave/WaveHomeView.swift:28
        val vs = line.map { it.v }   // ios: Views/Wave/WaveHomeView.swift:29
        val out = LinkedHashMap<Island, List<Int>>()   // ios: Views/Wave/WaveHomeView.swift:30
        val fit = ArrayList<Fit>()   // ios: Views/Wave/WaveHomeView.swift:31
        // ios: Views/Wave/WaveHomeView.swift:32
        for (s in 0..(vs.size - n) step 3) {
            val v = vs.subList(s, s + n)   // ios: Views/Wave/WaveHomeView.swift:33
            val isl = WaveEngine.classify(v).first; out[isl] = (out[isl] ?: emptyList()) + s   // ios: Views/Wave/WaveHomeView.swift:34
            var peak = v[0]; var dd = 0.0   // ios: Views/Wave/WaveHomeView.swift:35
            for (x in v) { peak = max(peak, x); dd = max(dd, 1 - x / peak) }   // ios: Views/Wave/WaveHomeView.swift:36
            fit.add(Fit(s, WaveEngine.stdev(WaveEngine.rets(v)), dd, v.last() / v.first() - 1))   // ios: Views/Wave/WaveHomeView.swift:37
        }
        // ios: Views/Wave/WaveHomeView.swift:39
        for (i in order) {
            if (out[i] != null) continue
            val ranked: List<Int>   // ios: Views/Wave/WaveHomeView.swift:40
            // ios: Views/Wave/WaveHomeView.swift:41
            when (i) {
                Island.nap -> ranked = fit.sortedBy { it.sd }.map { it.s }   // ios: Views/Wave/WaveHomeView.swift:42
                Island.climb -> ranked = fit.sortedByDescending { it.total - it.dd }.map { it.s }   // ios: Views/Wave/WaveHomeView.swift:43
                Island.coaster -> ranked = fit.sortedByDescending { it.dd }.map { it.s }   // ios: Views/Wave/WaveHomeView.swift:44
                Island.choppy -> ranked = fit.sortedByDescending { it.sd }.map { it.s }   // ios: Views/Wave/WaveHomeView.swift:45
            }
            out[i] = ranked.take(5)   // ios: Views/Wave/WaveHomeView.swift:47
        }
        return out   // ios: Views/Wave/WaveHomeView.swift:49
    }

    /** 그 섬 이름으로 코스 만들기 (난이도는 구간 모양으로) */
    // ios: Views/Wave/WaveHomeView.swift:53
    fun course(line: List<WaveEngine.Pt>, at: Int, island: Island): WaveEngine.Course {
        val part = line.subList(at, at + min(WaveEngine.days, line.size)); val vs = part.map { it.v }   // ios: Views/Wave/WaveHomeView.swift:54
        return WaveEngine.Course(dates = part.map { it.d }, values = vs, island = island, level = WaveEngine.classify(vs).second)   // ios: Views/Wave/WaveHomeView.swift:55
    }
}

/** 미션 탭 '파도 타기' 첫 화면: 위에 보드 탄 내 캐릭터, 아래로 섬 지도(섬 윤곽 = 내 자산 코스 모양), 지금 섬 카드 */
// ios: Views/Wave/WaveHomeView.swift:60,61,81
@Composable
fun WaveHomeView(m: AppModel) {
    val still = LocalReduceMotion.current   // ios: Views/Wave/WaveHomeView.swift:62
    var line by remember { mutableStateOf<List<WaveEngine.Pt>>(emptyList()) }   // ios: Views/Wave/WaveHomeView.swift:63
    var starts by remember { mutableStateOf<Map<Island, List<Int>>>(emptyMap()) }   // ios: Views/Wave/WaveHomeView.swift:64
    var ready by remember { mutableStateOf(false) }   // ios: Views/Wave/WaveHomeView.swift:65
    var sel by remember { mutableStateOf<Island?>(null) }   // ios: Views/Wave/WaveHomeView.swift:66
    val bob = remember { Animatable(0f) }   // ios: Views/Wave/WaveHomeView.swift:67 (0 ↔ 1, 그리기 때만 읽는다)
    var closet by remember { mutableStateOf(false) }   // ios: Views/Wave/WaveHomeView.swift:68
    // 꾸미기 시트에서 고르면 바로 바뀌게 (SurfCloset.board·hat 이 같은 키에 쓴다)
    var board by remember { mutableIntStateOf(Defaults.prefs.getInt("surf.board", 0)) }   // ios: Views/Wave/WaveHomeView.swift:70
    var hat by remember { mutableIntStateOf(Defaults.prefs.getInt("surf.hat", 0)) }   // ios: Views/Wave/WaveHomeView.swift:71
    var play by remember { mutableStateOf<WaveEngine.Course?>(null) }   // ios: Views/Wave/WaveHomeView.swift:72
    var tick by remember { mutableIntStateOf(0) }   // ios: Views/Wave/WaveHomeView.swift:73 판이 끝나고 돌아오면 별을 다시 읽는다

    // ios: Views/Wave/WaveHomeView.swift:75
    val current: Island = run {
        val s = sel; if (s != null && WaveMap.isOpen(s)) return@run s   // ios: Views/Wave/WaveHomeView.swift:76
        val open = WaveMap.order.take(WaveMap.opened)   // ios: Views/Wave/WaveHomeView.swift:77
        open.firstOrNull { WaveMap.stars(it) == 0 } ?: open.lastOrNull() ?: Island.nap   // ios: Views/Wave/WaveHomeView.swift:78
    }

    // ios: Views/Wave/WaveHomeView.swift:96
    key(tick) {
        // ios: Views/Wave/WaveHomeView.swift:82
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WaveRider(board, hat, bob) { closet = true }   // ios: Views/Wave/WaveHomeView.swift:83
            // ios: Views/Wave/WaveHomeView.swift:84
            if (!ready) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) { Spinner() }   // ios: Views/Wave/WaveHomeView.swift:85
            } else if (line.size < WaveMap.minDays) {   // ios: Views/Wave/WaveHomeView.swift:86
                // ios: Views/Wave/WaveHomeView.swift:87
                DashedCard {
                    // ios: Views/Wave/WaveHomeView.swift:88 (Label: 기호 + 글자)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { SymOr("water.waves", 15f, Theme.sub); T("섬이 아직 없어요", style = appFont(15f, FontWeight.Bold)) }
                    T("종목을 넣으면 내 자산 길로 섬이 생겨요", style = appFont(13f))   // ios: Views/Wave/WaveHomeView.swift:89
                }
            } else {   // ios: Views/Wave/WaveHomeView.swift:91
                IslandMap(line, starts, current) { sel = it }   // ios: Views/Wave/WaveHomeView.swift:92
                IslandCard(current, starts) { s -> play = WaveMap.course(line, s, current) }   // ios: Views/Wave/WaveHomeView.swift:93
            }
        }
    }
    // ios: Views/Wave/WaveHomeView.swift:97
    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect   // ios: Views/Wave/WaveHomeView.swift:98
        val l = withContext(Dispatchers.Default) { m.waveLine }   // ios: Views/Wave/WaveHomeView.swift:99
        line = l; starts = withContext(Dispatchers.Default) { WaveMap.starts(l) }; ready = true   // ios: Views/Wave/WaveHomeView.swift:100
    }
    // 위: 보드 탄 캐릭터가 흔들린다 (줄인 움직임이면 그대로)
    // ios: Views/Wave/WaveHomeView.swift:139,140,141
    LaunchedEffect(still) {
        if (still) { bob.snapTo(0f); return@LaunchedEffect }
        bob.animateTo(1f, infiniteRepeatable(tween(1300, easing = EaseInOut), RepeatMode.Reverse))
    }
    SurfClosetSheet(closet, dismiss = { closet = false }, onPick = { board = SurfCloset.board; hat = SurfCloset.hat })   // ios: Views/Wave/WaveHomeView.swift:102
    // ios: Views/Wave/WaveHomeView.swift:103
    WaveCover(play != null, onDismiss = { play = null; tick += 1 }, edge = true) {
        play?.let { WavePlayView(m, course = it, onFinish = { r -> WaveMap.record(r) }) }
    }
}

// MARK: 위: 보드 타고 물살 가르는 내 캐릭터 (누르면 꾸미기)

// ios: Views/Wave/WaveHomeView.swift:108
@Composable
private fun WaveRider(board: Int, hat: Int, bob: Animatable<Float, *>, openCloset: () -> Unit) {
    val sky = listOf(hexD(0xBFE6F7, 0x163A50), hexD(0x5BB8E0, 0x1C4D66))   // ios: Views/Wave/WaveHomeView.swift:110
    Box(Modifier.fillMaxWidth()) {
        // ios: Views/Wave/WaveHomeView.swift:109,124,125,126,127,128,129,130
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).plainClickable(openCloset)
                .clearAndSetSemantics { contentDescription = "보드 탄 내 캐릭터, 눌러서 꾸미기"; role = Role.Button },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(sky)))   // ios: Views/Wave/WaveHomeView.swift:111
            // 물결 두 겹 (phase 0↔1: bob 값을 그리기 때 바로 읽는다)
            Canvas(Modifier.fillMaxWidth().height(54.dp)) { drawPath(WaveBand.path(size, lerp(0.0, 1.0, bob.value), density), Color.White.copy(alpha = 0.35f)) }   // ios: Views/Wave/WaveHomeView.swift:112
            val deep = hexD(0x2F8FD8, 0x2A6F99)   // ios: Views/Wave/WaveHomeView.swift:113
            Canvas(Modifier.fillMaxWidth().height(34.dp)) { drawPath(WaveBand.path(size, lerp(1.0, 0.0, bob.value), density), deep) }
            // 움직이는 그림은 누르는 자리가 애니메이션 끝 위치라 빗나가니, 탭은 바깥 칸 전체로 받는다
            // ios: Views/Wave/WaveHomeView.swift:115,116,117,118
            SurfRider(board = board, hat = hat, dot = 2.6f, modifier = Modifier.graphicsLayer {
                val b = bob.value
                transformOrigin = TransformOrigin(0.5f, 1f); rotationZ = 3 + (-5 - 3) * b   // 기울기: bob ? -5 : 3 (아래 가운데 축)
                translationX = (-6 + (6 - -6) * b) * density; translationY = (-18 + (-26 - -18) * b) * density   // x: bob ? 6 : -6, y: bob ? -26 : -18
            })
            // 물보라
            // ios: Views/Wave/WaveHomeView.swift:120,121,122
            Row(Modifier.graphicsLayer { translationX = -48 * density; translationY = (-16 + (-24 - -16) * bob.value) * density }, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 3) Box(Modifier.size((7 - i * 2).dp).background(Color.White.copy(alpha = 0.85f), CircleShape))
            }
        }
        // ios: Views/Wave/WaveHomeView.swift:131,132,133,134,135,137
        Box(Modifier.align(Alignment.TopEnd).plainClickable(openCloset).padding(10.dp)) {
            T("꾸미기", Modifier.background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), style = appFont(12f, FontWeight.Bold), color = Theme.inkFixed)
        }
    }
}

// MARK: 섬 지도: 옆으로 이어진 섬, 점선 뱃길, 안 열린 섬은 안개

// ios: Views/Wave/WaveHomeView.swift:147
@Composable
private fun IslandMap(line: List<WaveEngine.Pt>, starts: Map<Island, List<Int>>, current: Island, pick: (Island) -> Unit) {
    val muted = Theme.muted
    // ios: Views/Wave/WaveHomeView.swift:148,149,155
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/Wave/WaveHomeView.swift:150
        WaveMap.order.forEachIndexed { k, i ->
            if (k > 0) Canvas(Modifier.size(28.dp, 2.dp)) { drawPath(SeaRoute.path(size), muted, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))) }   // ios: Views/Wave/WaveHomeView.swift:151
            IslandTile(i, line, starts, current, pick)   // ios: Views/Wave/WaveHomeView.swift:152
        }
    }
}

// ios: Views/Wave/WaveHomeView.swift:159
@Composable
private fun IslandTile(i: Island, line: List<WaveEngine.Pt>, starts: Map<Island, List<Int>>, current: Island, pick: (Island) -> Unit) {
    val open = WaveMap.isOpen(i); val on = i == current; val st = WaveMap.stars(i)   // ios: Views/Wave/WaveHomeView.swift:160
    val vs = remember(starts, i) { starts[i]?.firstOrNull()?.let { WaveMap.course(line, it, i).values } ?: emptyList() }   // ios: Views/Wave/WaveHomeView.swift:161
    val a11y = if (open) "${i.label}, 별 ${st}개" else "안개 속 섬, 아직 안 열림"   // ios: Views/Wave/WaveHomeView.swift:180
    // ios: Views/Wave/WaveHomeView.swift:162,163,174,175,176,178,179 (고른 칸 바탕·테두리가 0.2초 easeOut 으로 바뀐다 · 안 열린 섬은 못 누른다)
    val bg by animateColorAsState(if (on) Theme.forecastBg else Color.Transparent, tween(200, easing = EaseOut), label = "tileBg")
    val edge by animateColorAsState(if (on) Theme.forecast else Color.Transparent, tween(200, easing = EaseOut), label = "tileEdge")
    Column(
        Modifier.then(if (open) Modifier.plainClickable { pick(i) } else Modifier).semantics { contentDescription = a11y }
            .background(bg, RoundedCornerShape(14.dp)).border(2.dp, edge, RoundedCornerShape(14.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // ios: Views/Wave/WaveHomeView.swift:164,165,166,167,168,169
            val sand = hexD(0xF3DFA2, 0x8C7A4A); val grass = hexD(0x5FAE63, 0x3F7A45); val shore = hexD(0xBFE6F7, 0x1C4D66)
            Canvas(Modifier.size(96.dp, 56.dp).graphicsLayer { alpha = if (open) 1f else 0.45f }.blur(if (open) 0.dp else 3.dp, BlurredEdgeTreatment.Unbounded)) {
                // 아래 바닷가 띠: 112×12, 아래로 4 (섬보다 넓게)
                drawRoundRect(shore, Offset((size.width - 112.dp.toPx()) / 2, size.height - 12.dp.toPx() + 4.dp.toPx()), Size(112.dp.toPx(), 12.dp.toPx()), CornerRadius(6.dp.toPx()))
                drawPath(IslandShape.path(vs, size), sand)
                drawPath(IslandShape.path(vs, size, topOnly = true), grass, style = Stroke(3.dp.toPx()))
            }
            if (!open) SymOr("cloud.fill", 26f, Theme.muted.copy(alpha = 0.8f))   // ios: Views/Wave/WaveHomeView.swift:170
        }
        T(if (open) i.label else "안개 속", style = appFont(11f, FontWeight.Bold), color = if (open) Theme.ink else Theme.muted, maxLines = 1)   // ios: Views/Wave/WaveHomeView.swift:171
        Stars(n = st, size = 9f, modifier = Modifier.alpha(if (open) 1f else 0f))   // ios: Views/Wave/WaveHomeView.swift:172
    }
}

// MARK: 지금 섬 카드

// ios: Views/Wave/WaveHomeView.swift:185
@Composable
private fun IslandCard(i: Island, starts: Map<Island, List<Int>>, go: (Int) -> Unit) {
    val has = !(starts[i] ?: emptyList()).isEmpty(); val best = WaveMap.best(i)   // ios: Views/Wave/WaveHomeView.swift:186
    // ios: Views/Wave/WaveHomeView.swift:187
    Card {
        // ios: Views/Wave/WaveHomeView.swift:188
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(i.label, style = appFont(20f, FontWeight.Bold))   // ios: Views/Wave/WaveHomeView.swift:189
            Spacer(Modifier.weight(1f))   // ios: Views/Wave/WaveHomeView.swift:190
            Stars(n = WaveMap.stars(i), size = 16f)   // ios: Views/Wave/WaveHomeView.swift:191
        }
        T(if (has) i.line else "내 자산 길엔 아직 이런 물결이 없어요", style = appFont(14f), color = Theme.sub)   // ios: Views/Wave/WaveHomeView.swift:193,194
        if (best > 0) T("최고 ${best}점", style = appFont(12f), color = Theme.muted)   // ios: Views/Wave/WaveHomeView.swift:195
        // ios: Views/Wave/WaveHomeView.swift:196,197,198,203
        Box(Modifier.padding(top = 2.dp).fillMaxWidth().then(if (has) Modifier.plainClickable { starts[i]?.randomOrNull()?.let(go) } else Modifier)
            // ios: Views/Wave/WaveHomeView.swift:199,200,201
            .defaultMinSize(minHeight = 48.dp).background(if (has) Theme.forecast else Theme.muted, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            T("타러 가기", style = appFont(16f, FontWeight.Bold), color = Color.White)
        }
    }
}

/** WaveEngine.Course 의 id (Identifiable): 섬 + 시작 날짜 */
val WaveEngine.Course.id: String get() = "${island.raw}$startDate"   // ios: Views/Wave/WaveHomeView.swift:209

/** 별 3칸 */
// ios: Views/Wave/WaveHomeView.swift:212,213,214,215
@Composable
private fun Stars(n: Int, size: Float, modifier: Modifier = Modifier) {
    // ios: Views/Wave/WaveHomeView.swift:216,222
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // ios: Views/Wave/WaveHomeView.swift:217
        for (k in 0 until 3) {
            SymOr(if (k < n) "star.fill" else "star", size, if (k < n) Theme.gold else Theme.muted, scaled = false)   // ios: Views/Wave/WaveHomeView.swift:218,219
        }
    }
}

/** 섬 윤곽: 코스 평가액을 언덕 모양으로 (양 끝은 바닷가로 낮아진다). topOnly면 위 능선만 */
// ios: Views/Wave/WaveHomeView.swift:227,228,229
private object IslandShape {
    // ios: Views/Wave/WaveHomeView.swift:230
    fun path(values: List<Double>, r: Size, topOnly: Boolean = false): Path {
        val vs = if (values.size > 1) values else listOf(1.0, 1.02, 1.05, 1.03, 1.06, 1.04, 1.02)   // ios: Views/Wave/WaveHomeView.swift:231
        val lo = vs.min(); val hi = vs.max(); val span = max(hi - lo, 1e-9); val n = vs.size   // ios: Views/Wave/WaveHomeView.swift:232
        // ios: Views/Wave/WaveHomeView.swift:233
        fun pt(k: Int): Offset {
            val t = k.toDouble() / (n - 1).toDouble(); val taper = sqrt(sin(PI * t))   // ios: Views/Wave/WaveHomeView.swift:234
            val h = (0.2 + 0.8 * (vs[k] - lo) / span) * taper   // ios: Views/Wave/WaveHomeView.swift:235
            return Offset((r.width * t).toFloat(), (r.height - r.height * h).toFloat())   // ios: Views/Wave/WaveHomeView.swift:236
        }
        val p = Path()   // ios: Views/Wave/WaveHomeView.swift:238
        p.moveTo(pt(0).x, pt(0).y)   // ios: Views/Wave/WaveHomeView.swift:239
        for (k in 1 until n) p.lineTo(pt(k).x, pt(k).y)   // ios: Views/Wave/WaveHomeView.swift:240
        if (!topOnly) { p.lineTo(r.width, r.height); p.lineTo(0f, r.height); p.close() }   // ios: Views/Wave/WaveHomeView.swift:241
        return p   // ios: Views/Wave/WaveHomeView.swift:242
    }
}

/** 섬 사이 뱃길 한 줄 */
// ios: Views/Wave/WaveHomeView.swift:247
private object SeaRoute {
    fun path(r: Size): Path = Path().apply { moveTo(0f, r.height / 2); lineTo(r.width, r.height / 2) }   // ios: Views/Wave/WaveHomeView.swift:248
}

/** 출렁이는 물결 띠 (phase 0↔1 사이를 애니메이션). 크기는 화소, 식의 수는 pt (k = 밀도) */
// ios: Views/Wave/WaveHomeView.swift:252,253,254
private object WaveBand {
    // ios: Views/Wave/WaveHomeView.swift:255
    fun path(r: Size, phase: Double, k: Float): Path {
        val p = Path()   // ios: Views/Wave/WaveHomeView.swift:256
        p.moveTo(0f, r.height)   // ios: Views/Wave/WaveHomeView.swift:257
        // ios: Views/Wave/WaveHomeView.swift:258
        for (x in generateSequence(0.0) { it + 6 }.takeWhile { it <= r.width / k }) {
            val y = 8 + 6 * sin(x / 34 + phase * PI)   // ios: Views/Wave/WaveHomeView.swift:259
            p.lineTo((x * k).toFloat(), (y * k).toFloat())   // ios: Views/Wave/WaveHomeView.swift:260
        }
        p.lineTo(r.width, r.height)   // ios: Views/Wave/WaveHomeView.swift:262
        p.close()   // ios: Views/Wave/WaveHomeView.swift:263
        return p   // ios: Views/Wave/WaveHomeView.swift:264
    }
}

/** 미션 탭 [파도] 버튼: 블록 게임처럼 전체 화면으로 섬 지도를 연다 (미션 탭은 섬·코스 계산을 안 한다).
 *  labelStyle = TightLabel 이면 기호와 글자를 붙인다 (BoardView 의 .labelStyle(TightLabel())) */
// ios: Views/Wave/WaveHomeView.swift:269,272
@Composable
fun WaveGameButton(
    m: AppModel,
    plain: Boolean = false,   // ios: Views/Wave/WaveHomeView.swift:270 흰 바탕 · 회색 테두리 (미션 탭 보조 버튼)
    labelStyle: TightLabel? = null,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }   // ios: Views/Wave/WaveHomeView.swift:271
    val fg = if (plain) Theme.ink else Color.White
    // ios: Views/Wave/WaveHomeView.swift:273,277,278,280
    Box(
        modifier.fillMaxWidth().plainClickable { show = true }
            .background(if (plain) Theme.card else Theme.teal, RoundedCornerShape(12.dp))
            .border(1.5.dp, if (plain) Theme.border else Color.Transparent, RoundedCornerShape(12.dp)),
    ) {
        // ios: Views/Wave/WaveHomeView.swift:274,275,276 (Label: 기호 + 글자, 14 굵게, 한 줄, 0.8 까지 줄임, 높이 44 이상)
        val st = appFont(14f, FontWeight.Bold)
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy((labelStyle?.spacing ?: 6).dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            SymOr("water.waves", 14f, fg); FitText("파도", st, 0.8f, maxLines = 1, color = fg)
        }
    }
    WaveCover(show, onDismiss = { show = false }) { WaveHub(m) }   // ios: Views/Wave/WaveHomeView.swift:281
}

/** 섬 지도 전체 화면: 위에 닫기, 아래로 섬 지도 */
// ios: Views/Wave/WaveHomeView.swift:286,287
@Composable
fun WaveHub(m: AppModel) {
    // ios: Views/Wave/WaveHomeView.swift:288,295
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/Wave/WaveHomeView.swift:293 벽돌깨기와 같은 여백
        Screen {
            // ios: Views/Wave/WaveHomeView.swift:289
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GameHeader(title = "내 자산으로 하는 파도타기")   // ios: Views/Wave/WaveHomeView.swift:290
                WaveHomeView(m)   // ios: Views/Wave/WaveHomeView.swift:291
            }
        }
    }
}

/** 게임 화면 맨 위 줄 (벽돌깨기·파도 타기 공용): 왼쪽 굵은 제목, 오른쪽 X 닫기. dismiss 를 안 주면 감싼 전체 화면(LocalNavBack)을 닫는다 */
// ios: Views/Wave/WaveHomeView.swift:300,303
@Composable
fun GameHeader(
    title: String,   // ios: Views/Wave/WaveHomeView.swift:301
    dismiss: (() -> Unit)? = null,
) {
    val nav = LocalNavBack.current
    val close = dismiss ?: { nav?.pop?.invoke(); Unit }   // ios: Views/Wave/WaveHomeView.swift:302
    // ios: Views/Wave/WaveHomeView.swift:304,310
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FitText(title, appFont(20f, FontWeight.Bold), 0.8f, Modifier.weight(1f))   // ios: Views/Wave/WaveHomeView.swift:305
        Spacer(Modifier.size(0.dp))   // ios: Views/Wave/WaveHomeView.swift:306
        // ios: Views/Wave/WaveHomeView.swift:307,308
        Box(Modifier.plainClickable(close).padding(8.dp).semantics { contentDescription = "닫기" }) { SymOr("xmark", 16f, Theme.ink) }
    }
}

/** 종목 탭 소식판 시장과 비교 밑 [동네] 한 줄: 파도처럼 전체 화면으로 동네를 연다 (종목 탭은 동네를 안 그린다).
 *  방금 종목을 더했으면(town.arrive) 바로 연다. 동네 본문은 부르는 쪽이 준다 (ScrollViewProxy 자리 = 바깥 ScrollState) */
// ios: Views/Wave/WaveHomeView.swift:316,320
@Composable
fun TownButton(
    town: @Composable (ScrollState) -> Unit,   // ios: Views/Wave/WaveHomeView.swift:317
) {
    val prefs = Defaults.prefs
    var arrive by remember { mutableStateOf(prefs.getString("town.arrive", null) ?: "") }   // ios: Views/Wave/WaveHomeView.swift:318
    var show by remember { mutableStateOf(false) }   // ios: Views/Wave/WaveHomeView.swift:319
    // 홈 리마인드 노란 띠(HomeView.stripRow)와 같은 모양: 노란 꼬리표 + 한 줄 + ›
    // ios: Views/Wave/WaveHomeView.swift:322,323,330,331,332,333,335,336
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).plainClickable { show = true }
            .clearAndSetSemantics { contentDescription = "동네 한 바퀴: 내 종목 CEO가 한 자리에"; role = Role.Button }
            .background(Theme.cream, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(com.naeilo.app.ui.LocalFg provides { it.ink }) {
            // ios: Views/Wave/WaveHomeView.swift:324,325
            T("동네 한 바퀴", Modifier.background(Theme.yellow, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed)
            T("내 종목 CEO가 한 자리에", style = appFont(13f), maxLines = 2)   // ios: Views/Wave/WaveHomeView.swift:326
            Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/Wave/WaveHomeView.swift:327
            SymOr("chevron.right", 11f, Theme.muted)   // ios: Views/Wave/WaveHomeView.swift:328
        }
    }
    // 다른 화면(종목 더하기)이 town.arrive 를 쓰면 바로 연다
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { p, k -> if (k == "town.arrive") arrive = p.getString(k, null) ?: "" }
        prefs.registerOnSharedPreferenceChangeListener(l); onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    LaunchedEffect(arrive) { if (!arrive.isEmpty()) show = true }   // ios: Views/Wave/WaveHomeView.swift:337,338
    // ios: Views/Wave/WaveHomeView.swift:339,340
    WaveCover(show, onDismiss = { show = false }) {
        val sp = rememberScrollState()
        val focus = LocalFocusManager.current
        val dismissKb = remember(focus) { HideKeyboardOnScroll(focus) }
        // ios: Views/Wave/WaveHomeView.swift:341,348,349,350 (키보드에 안 들썩이게: 창이 키보드만큼 줄지 않는다 · 스크롤하면 키보드를 내린다)
        Column(Modifier.fillMaxSize().background(Theme.bg).nestedScroll(dismissKb).verticalScroll(sp)) {
            // ios: Views/Wave/WaveHomeView.swift:346
            Screen {
                // ios: Views/Wave/WaveHomeView.swift:342
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GameHeader(title = "우리 동네 주민들")   // ios: Views/Wave/WaveHomeView.swift:343
                    town(sp)   // ios: Views/Wave/WaveHomeView.swift:344
                }
            }
        }
    }
}

/** .scrollDismissesKeyboard(.immediately) */
private class HideKeyboardOnScroll(private val focus: FocusManager) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset { if (available.y != 0f) focus.clearFocus(); return Offset.Zero }
}

/** 아이콘과 글자를 기본보다 붙인 라벨 (미션 탭 [블록][파도]): 간격 3 (보통 Label 은 6) */
// ios: Views/Wave/WaveHomeView.swift:357,358,359
object TightLabel { const val spacing = 3 }

// ── 이 단계의 공용 조각 ──

/** .fullScreenCover: 아래에서 올라와 화면을 다 덮는다. 안에서 닫기는 LocalNavBack.current?.pop.
 *  edge = 게임판처럼 화면 끝까지 그린다 (안쪽이 직접 안전 영역을 비운다) */
@Composable
internal fun WaveCover(show: Boolean, onDismiss: () -> Unit, edge: Boolean = false, content: @Composable () -> Unit) {
    if (!show) return
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val y = remember { Animatable(1f) }
        LaunchedEffect(Unit) { y.animateTo(0f, tween(350, easing = EaseOut)) }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = y.value * size.height }.background(Theme.bg).then(if (edge) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
            CompositionLocalProvider(LocalNavBack provides NavBack(null, onDismiss)) { content() }
        }
    }
}

/** SF 표(Controls.kt SF)에 아직 없는 기호는 비슷한 글자로 그린다 (표에 들어오면 Sym 그대로) */
@Composable
internal fun SymOr(sf: String, size: Float, color: Color, modifier: Modifier = Modifier, scaled: Boolean = true) {
    if (SF.containsKey(sf)) { Sym(sf, size, color, modifier, scaled); return }
    val k = if (scaled) LocalTextScale.current.ratio else 1f
    Box(modifier.size((size * 1.25f * k).dp), contentAlignment = Alignment.Center) {
        BasicText(symText[sf] ?: "•", style = TextStyle(fontSize = (size * k).sp, fontWeight = FontWeight.Bold, color = color))
    }
}
private val symText = mapOf(
    "star" to "☆", "star.fill" to "★", "heart" to "♡", "heart.fill" to "♥", "cloud.fill" to "☁",
    "circle" to "○", "circle.fill" to "●", "water.waves" to "≋", "xmark" to "✕", "chevron.right" to "›",
)
