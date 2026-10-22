package com.naeilo.app.ui.town

import android.Manifest
import com.naeilo.app.core.tr
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.cformat
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeColor
import com.naeilo.app.data.MeGroup
import com.naeilo.app.data.Sample
import com.naeilo.app.net.Market
import com.naeilo.app.net.quote
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.CharDir
import com.naeilo.app.ui.CharScene
import com.naeilo.app.ui.EaseOut
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.MeMotion
import com.naeilo.app.ui.SceneSprite
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.TownWalker
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.me.MeAvatar
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.rememberPrefString
import com.naeilo.app.ui.stiffness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// 작은 세상(동네): 보유 종목이 동네 주민으로 서 있고, 누르면 대사창에 오르내림 한 줄.
// 가볍게: 배경은 한 번 그린 그림(Canvas·drawWithCache), 대사창이 열렸을 때만 글자를 찍는다. 새로 받는 시세 없음 (이미 받은 시세만).
// 걷기는 주민마다 Animatable 하나를 layout 단계(offset 람다)에서만 읽어 화면을 다시 짜지 않는다.
// 2초 박자는 화면에 있을 때(탭이 보이고 앱이 앞에 있을 때)만 돈다. '동작 줄이기'면 멈춘다.

/** 오늘 날짜 글자 (기기 시간대, yyyy-MM-dd) */
private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

/** @AppStorage("키") Int 자리 (rememberPrefString 과 같은 방식) */
@Composable
private fun rememberPrefInt(key: String, default: Int): androidx.compose.runtime.MutableState<Int> {
    val prefs = Defaults.prefs
    val s = remember(key) { mutableStateOf(prefs.getInt(key, default)) }
    return remember(key) {
        object : androidx.compose.runtime.MutableState<Int> by s {
            override var value: Int
                get() = s.value
                set(v) { s.value = v; prefs.edit().putInt(key, v).apply() }
        }
    }
}

/** .position(x:y:): 가운데를 (x, y) 에 둔다 (부모 Box 왼쪽 위 기준) */
private fun Modifier.centerAt(x: Dp, y: Dp): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2) }
}

/** overlay(alignment: .top) + offset(y:): 자리를 차지하지 않고 가운데 위에 띄운다 */
private fun Modifier.floatTop(y: Dp): Modifier = this.layout { m, _ ->
    val p = m.measure(Constraints())
    layout(0, 0) { p.place(-p.width / 2, y.roundToPx()) }
}

// ios: Views/HoldingsView.swift:964
private data class Meet(val a: String, val b: String, val home: Offset, val side: Float, val until: Int, val met: Boolean = false, val back: Boolean = false)

/** TownView 의 static 값 */
object TownView {
    const val maxRes = 10   // ios: Views/HoldingsView.swift:968 손가락으로 셀 만큼 (평가액 큰 순)
    // 주민 자리 (동네 판 안의 비율 좌표). 앞 번호부터 채운다
    val spots: List<Offset> = listOf(   // ios: Views/HoldingsView.swift:970
        Offset(0.28f, 0.36f), Offset(0.54f, 0.32f), Offset(0.76f, 0.40f), Offset(0.40f, 0.54f),   // ios: Views/HoldingsView.swift:971
        Offset(0.66f, 0.58f), Offset(0.22f, 0.66f), Offset(0.50f, 0.74f), Offset(0.78f, 0.72f),   // ios: Views/HoldingsView.swift:972
        Offset(0.34f, 0.82f), Offset(0.64f, 0.84f), Offset(0.84f, 0.26f), Offset(0.16f, 0.30f),   // ios: Views/HoldingsView.swift:973
        Offset(0.14f, 0.48f),   // ios: Views/HoldingsView.swift:974
    )
    val mayorSpot = Offset(0.84f, 0.26f)   // ios: Views/HoldingsView.swift:977 이장은 늘 오른쪽 위
    val visitSpot = Offset(0.14f, 0.48f)   // ios: Views/HoldingsView.swift:978 옛 이웃은 길 어귀에
    val meSpot = Offset(0.50f, 0.64f)      // ios: Views/HoldingsView.swift:988 나는 길가 가운데 (모임 날)
    val warshSpot = Offset(0.16f, 0.28f)   // ios: Views/HoldingsView.swift:989 워시는 왼쪽 위 나무 곁
    val stage = Offset(0.50f, 0.48f)       // ios: Views/HoldingsView.swift:990 어닝콜 날 그 주민이 서는 가운데
    // 바닥: 풀밭과 흙길 + 나무 몇 그루 (그림 파일 없이 색만). 계절은 실제 달력으로 풀·잎 색만 바뀐다
    val trees: List<Offset> = listOf(Offset(0.07f, 0.13f), Offset(0.68f, 0.10f), Offset(0.95f, 0.58f), Offset(0.05f, 0.93f))   // ios: Views/HoldingsView.swift:1200
}

/**
 * 작은 세상 첫 조각: 보유 종목이 동네 주민으로 서 있고, 누르면 대사창에 오르내림 한 줄.
 * scroll: 동네를 담은 세로 스크롤 (말 걸면 아래 칸이 보이게 페이지를 살짝 내린다). 없으면 안 내린다
 */
// ios: Views/HoldingsView.swift:939,940,941
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun TownView(m: AppModel, scroll: ScrollState? = null) {
    var bg by rememberPrefInt("town.bg", 0)   // ios: Views/HoldingsView.swift:942 동네 배경 (TownGround.names)
    val scheme = LocalPal.current.isDark   // ios: Views/HoldingsView.swift:943
    var arrive by rememberPrefString("town.arrive", "")   // ios: Views/HoldingsView.swift:944
    var talk by remember { mutableStateOf<TownLook.Resident?>(null) }   // ios: Views/HoldingsView.swift:945
    var dress by remember { mutableStateOf<TownLook.Resident?>(null) }   // ios: Views/HoldingsView.swift:946
    var us by remember { mutableStateOf<TownLook.Resident?>(null) }   // ios: Views/HoldingsView.swift:947
    val tabS = remember { mutableStateOf(0) }; var tab by tabS   // ios: Views/HoldingsView.swift:948 대사창 아래 칸: 0 기본, 1 우리 사이, 2 꾸미기
    val lineS = remember { mutableStateOf<String?>(null) }; var line by lineS   // ios: Views/HoldingsView.swift:949 고른 말에 대한 주민 대답 (대사창에)
    val lookS = remember { mutableStateOf<TownBook.Note?>(null) }; var look by lookS   // ios: Views/HoldingsView.swift:950 꾸미기 미리 보기
    val askS = remember { mutableStateOf(0) }; var ask by askS   // ios: Views/HoldingsView.swift:951 오늘의 한 줄을 누르면 답장 칸 열기
    var typing by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:952 내 답을 쓰는 중
    var kbTop by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }   // ios: Views/HoldingsView.swift:953 키보드 윗변 (창 좌표 px)
    var talkBottom by remember { mutableFloatStateOf(0f) }   // ios: Views/HoldingsView.swift:954 대사창 제자리 아랫변 (창 좌표 px)
    var fresh by remember { mutableStateOf(setOf<String>()) }   // ios: Views/HoldingsView.swift:955 오늘 이사 온 주민 (첫 대사가 인사)
    var tick by remember { mutableIntStateOf(0) }   // ios: Views/HoldingsView.swift:956 2초마다 한 칸
    val walk = remember { mutableStateMapOf<String, Offset>() }   // ios: Views/HoldingsView.swift:957 주민마다 제자리에서 걸어 나간 만큼 (dp)
    val face = remember { mutableStateMapOf<String, CharDir>() }   // ios: Views/HoldingsView.swift:958 주민이 보는 쪽
    var moving by remember { mutableStateOf(setOf<String>()) }   // ios: Views/HoldingsView.swift:959 지금 걷는 중인 주민
    var hop by remember { mutableStateOf<String?>(null) }   // ios: Views/HoldingsView.swift:960 이번 칸에 폴짝 뛴 주민
    var meet by remember { mutableStateOf<Meet?>(null) }   // ios: Views/HoldingsView.swift:961 이웃을 찾아가 수다 떠는 중 (한 번에 한 쌍만)
    val pace = remember { HashMap<String, Double>() }   // ios: Views/HoldingsView.swift:962 이번 걸음에 걸리는 초
    val seats = remember { mutableStateMapOf<String, Offset>() }   // ios: Views/HoldingsView.swift:963 주민 제자리 (dp)
    var order by remember { mutableStateOf((0 until 13).toList()) }   // ios: Views/HoldingsView.swift:965 주민 → 자리 번호
    val phase = androidx.lifecycle.compose.LocalLifecycleOwner.current   // ios: Views/HoldingsView.swift:966
    val book = TownBook.shared   // ios: Views/HoldingsView.swift:967
    val sky = TownSky.shared   // ios: Views/HoldingsView.swift:991
    val scope = rememberCoroutineScope()
    val active = LocalTabActive.current
    val reduce = LocalReduceMotion.current
    var resumed by remember { mutableStateOf(true) }
    val dens = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 걷는 보간: 주민마다 하나. 값은 layout 단계(offset 람다)에서만 읽는다
    val walkAnim = remember { HashMap<String, Animatable<Offset, AnimationVector2D>>() }
    fun walkAnimatable(k: String) = walkAnim.getOrPut(k) { Animatable(walk[k] ?: Offset.Zero, Offset.VectorConverter) }

    /** 내 캐릭터 걷기: 주민과 같은 초당 16pt보다 조금 빠르게(40pt), 가는 쪽을 본다 */
    // ios: Views/HoldingsView.swift:980
    fun walkMe(to: Offset) {
        val k = TownLook.meID; val w = walk[k] ?: Offset.Zero   // ios: Views/HoldingsView.swift:981
        val d = max(0.3, hypot((to.x - w.x).toDouble(), (to.y - w.y).toDouble()) / 40)   // ios: Views/HoldingsView.swift:982
        face[k] = CharDir.of(to.x - w.x, to.y - w.y)   // ios: Views/HoldingsView.swift:983
        pace[k] = d; moving = moving + k   // ios: Views/HoldingsView.swift:984
        walk[k] = to; scope.launch { walkAnimatable(k).animateTo(to, tween((d * 1000).toInt(), easing = LinearEasing)) }   // ios: Views/HoldingsView.swift:985
        scope.launch { delay((d * 1000).toLong()); if (walk[k] == to) { moving = moving - k; face[k] = CharDir.down } }   // ios: Views/HoldingsView.swift:986
    }

    val rows = m.rows   // ios: Views/HoldingsView.swift:993
    val all = remember(rows) { TownLook.residents(rows) }   // ios: Views/HoldingsView.swift:994
    val res = all.take(TownView.maxRes)   // ios: Views/HoldingsView.swift:995
    // 동네에 서 있는 이들: 주민 + 이장 + (오늘 놀러 온 옛 이웃)
    val mayor = TownLook.Resident(id = TownLook.mayorID, rows = emptyList())   // ios: Views/HoldingsView.swift:997
    val guest = book.visitor(except = all.map { it.id }.toSet())?.let { TownLook.Resident(id = it, rows = emptyList(), old = book.note(it).co ?: it) }   // ios: Views/HoldingsView.swift:998
    // 행사: 누군가의 어닝콜 날이면 주민이 그 주민 둘레로 모여 듣는다
    val host = remember(res) { res.firstOrNull { TownLook.earnDay(it) != null } }   // ios: Views/HoldingsView.swift:1000
    val others = res.filter { it.id != host?.id }   // ios: Views/HoldingsView.swift:1001
    val place: List<Pair<TownLook.Resident, Offset>> = host?.let { h ->   // ios: Views/HoldingsView.swift:1002
        listOf(h to TownView.stage) + others.mapIndexed { i, r ->   // ios: Views/HoldingsView.swift:1003
            val a = i.toDouble() / max(1, others.size).toDouble() * 2 * PI + PI / 2   // ios: Views/HoldingsView.swift:1004
            r to Offset((TownView.stage.x + 0.26 * cos(a)).toFloat(), (TownView.stage.y + 0.26 * sin(a) + 0.04).toFloat())   // ios: Views/HoldingsView.swift:1005
        }
    } ?: res.mapIndexed { i, r -> r to TownView.spots[order[i]] }   // ios: Views/HoldingsView.swift:1007
    // 워시는 FOMC 앞뒤 며칠 동안 동네에 머문다
    // 이장·워시·옛 이웃도 주민 다음 빈자리에 선다 (앱에 다시 들어오면 같이 섞임). 모임 날엔 가장자리 제자리
    val fomc = remember { TownLook.fomc() }
    val extra = listOf(mayor to TownView.mayorSpot) + (if (fomc != null) listOf(TownLook.Resident(id = TownLook.warshID, rows = emptyList()) to TownView.warshSpot) else emptyList()) +   // ios: Views/HoldingsView.swift:1010
        (guest?.let { listOf(it to TownView.visitSpot) } ?: emptyList()) +   // ios: Views/HoldingsView.swift:1011
        (if ((m.me ?: MeChar.current) != null) listOf(TownLook.Resident(id = TownLook.meID, rows = emptyList()) to TownView.meSpot) else emptyList())   // ios: Views/HoldingsView.swift:1012
    // 위에서 내려다보니 아래(앞)에 선 주민이 나중에 그려져 앞으로 겹친다
    val folk: List<Pair<TownLook.Resident, Offset>> = (place + extra.mapIndexed { k, e ->   // ios: Views/HoldingsView.swift:1014
        e.first to (if (host == null && res.size + k < TownView.spots.size) TownView.spots[order[res.size + k]] else e.second)   // ios: Views/HoldingsView.swift:1015
    }).sortedBy { it.second.y }   // ios: Views/HoldingsView.swift:1016

    // 위치 허락: iOS 처럼 동네를 열 때 한 번만 묻는다 (답이 오면 다시 날씨). 거절해도 서울로 이어진다
    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> sky.didChangeAuthorization() }
    DisposableEffect(Unit) {
        sky.ask = { askLoc.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
        onDispose { sky.ask = null }
    }

    // 말 걸면 아래 칸 끝이 화면 아래에 오게 (scrollTo("townPanel", anchor: .bottom))
    val anchors = remember { HashMap<String, LayoutCoordinates>() }
    var rootH by remember { mutableFloatStateOf(0f) }   // 창 높이 (px, 키보드 윗변 계산용)
    val navB = WindowInsets.navigationBars.getBottom(dens)
    suspend fun scrollToPanel(id: String, spec: AnimationSpec<Float>) {
        val s = scroll ?: return; val a = anchors[id] ?: return
        if (!a.isAttached) return
        val gap = a.boundsInWindow().bottom - (a.findRootCoordinates().size.height - navB)
        s.animateScrollTo((s.value + gap).roundToInt().coerceIn(0, s.maxValue), spec)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/HoldingsView.swift:1017
        BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp)) {   // ios: Views/HoldingsView.swift:1018,1019,1108 말 걸어도 동네 판 크기는 그대로
            val gw = maxWidth.value; val gh = maxHeight.value
            LaunchedEffect(folk.joinToString("") { "${it.first.id}${it.second.x}${it.second.y}" } + "$gw,$gh") {   // ios: Views/HoldingsView.swift:1020
                seats.clear(); for ((r, p) in folk) if (!seats.containsKey(r.id)) seats[r.id] = Offset(p.x * gw, p.y * gh)   // ios: Views/HoldingsView.swift:1021
            }
            // 다크 모드: 배경만 어둡게 (주민은 그 위)
            Box(Modifier.matchParentSize()
                .clip(RoundedCornerShape(18.dp))   // ios: Views/HoldingsView.swift:1027
                // 배경을 누르면 키보드를 내리고 대사창도 닫는다 (쓰던 답은 '그만'처럼)
                .pointerInput(Unit) {
                    detectTapGestures { px ->   // ios: Views/HoldingsView.swift:1029
                        val loc = Offset(px.x / density, px.y / density)
                        // 말 거는 중이 아니면 땅을 누른 곳으로 내 캐릭터가 걸어간다 (앞·옆·뒤 네 방향)
                        val st = seats[TownLook.meID]; if (talk == null && st != null) { walkMe(Offset(loc.x - st.x, loc.y - st.y)); return@detectTapGestures }   // ios: Views/HoldingsView.swift:1031
                        focusManager.clearFocus(); keyboard?.hide()   // ios: Views/HoldingsView.swift:1032
                        talk = null; typing = false   // ios: Views/HoldingsView.swift:1033
                    }
                }) {
                if (bg == 0) ground(Modifier.matchParentSize()) else TownGround(kind = bg, season = TownLook.season(), modifier = Modifier.matchParentSize())   // ios: Views/HoldingsView.swift:1023,1024
                if (scheme) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))   // ios: Views/HoldingsView.swift:1025
            }
            folk.forEachIndexed { i, (r, p) ->   // ios: Views/HoldingsView.swift:1035,1036
                key(i) {
                    // 잡담: 4칸(8초)마다 한 명이 2칸 동안 말풍선. 수다 중이면 찾아간 이웃만 말한다
                    val chat = talk == null && (meet?.let { it.met && !it.back && it.b == r.id }   // ios: Views/HoldingsView.swift:1039
                        ?: (tick % 4 < 2 && (TownLook.seed("chat${tick / 4}") % max(1, folk.size).toULong()).toInt() == i))   // ios: Views/HoldingsView.swift:1040
                    // 나는 누르면 크게 점프
                    val hopY = animateFloatAsState(if (hop == r.id) (if (r.id == TownLook.meID) -14f else -4f) else 0f,   // ios: Views/HoldingsView.swift:1063
                        spring(dampingRatio = 0.45f, stiffness = stiffness(0.28f)), label = "hop")   // ios: Views/HoldingsView.swift:1064
                    Box(Modifier
                        .centerAt((p.x * gw).dp, (p.y * gh - 34).dp)   // ios: Views/HoldingsView.swift:1066
                        .offset { val o = walkAnimatable(r.id).value; IntOffset(o.x.dp.roundToPx(), o.y.dp.roundToPx()) }   // ios: Views/HoldingsView.swift:1065 걷는 보간은 go()의 animateTo 가 맡는다
                        .offset { IntOffset(0, hopY.value.dp.roundToPx()) }) {
                        // 나는 눌러도 대사창 없이 폴짝
                        Box(Modifier
                            .semantics { contentDescription = tr(TownLook.shown(r, book.note(r.id))) }   // ios: Views/HoldingsView.swift:1052
                            .plainClickable { if (r.id == TownLook.meID) hop = r.id else talk = r }) {   // ios: Views/HoldingsView.swift:1041,1051
                            TownWalker(id = r.id, note = book.note(r.id), dir = face[r.id] ?: CharDir.down, walking = moving.contains(r.id))   // ios: Views/HoldingsView.swift:1042
                            // 기분: 별사탕 받은 날은 하트, 자기 종목이 내리면 시무룩
                            Box(Modifier.align(Alignment.TopEnd)) {   // ios: Views/HoldingsView.swift:1044
                                if (r.id == host?.id) T("🎤", style = appFont(13f))   // ios: Views/HoldingsView.swift:1046
                                else if (book.cheered(r.id)) T("♥", style = appFont(13f, FontWeight.Bold), color = Color(0.95f, 0.35f, 0.45f))   // ios: Views/HoldingsView.swift:1047
                                else if (TownLook.mood(r) < 0) T("…", style = appFont(13f, FontWeight.Bold), color = Color.White)   // ios: Views/HoldingsView.swift:1048
                            }
                        }
                        Box(Modifier.align(Alignment.TopCenter).floatTop((-24).dp)) {   // ios: Views/HoldingsView.swift:1053,1054,1059,1062
                            androidx.compose.animation.AnimatedVisibility(visible = chat, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                                T(TownLook.chat(r, tick / 4), style = appFont(11f), color = Color.Black, maxLines = 1, modifier = Modifier   // ios: Views/HoldingsView.swift:1055
                                    .background(Color.White, RoundedCornerShape(6.dp))   // ios: Views/HoldingsView.swift:1057
                                    .border(1.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))   // ios: Views/HoldingsView.swift:1058
                                    .padding(horizontal = 6.dp, vertical = 3.dp))   // ios: Views/HoldingsView.swift:1056
                            }
                        }
                    }
                }
            }
            TownWeather(kind = sky.kind, modifier = Modifier.matchParentSize())   // ios: Views/HoldingsView.swift:1068 누름은 받지 않는다
            // 배경 바꾸기: 누를 때마다 다음 그림
            Row(Modifier.align(Alignment.TopStart).padding(start = 8.dp)   // ios: Views/HoldingsView.swift:1077
                .heightIn(min = 44.dp).plainClickable { bg = (bg + 1) % TownGround.names.size },   // ios: Views/HoldingsView.swift:1070,1074,1076
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.background(Color.Black.copy(alpha = 0.35f), CapsuleShape)   // ios: Views/HoldingsView.swift:1073
                    .padding(horizontal = 10.dp, vertical = 6.dp),   // ios: Views/HoldingsView.swift:1072
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym("photo", 11f, Color.White)   // ios: Views/HoldingsView.swift:1071
                    T(TownGround.names[min(bg, TownGround.names.size - 1)], style = appFont(11f, FontWeight.Bold), color = Color.White)
                }
            }
            // 행사 띠: 어닝콜 모임 · 세계 기념일 (그날만)
            val t = host?.let { TownLook.earnDay(it) }?.let { "$it 발표 날 · 다 같이 듣는 중" } ?: TownLook.holiday()?.let { "오늘은 " + it }   // ios: Views/HoldingsView.swift:1079
            if (t != null) {
                T(t, style = appFont(12f, FontWeight.Bold), color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)   // ios: Views/HoldingsView.swift:1080,1083,1084
                    .background(Color.Black.copy(alpha = 0.35f), CapsuleShape)   // ios: Views/HoldingsView.swift:1082
                    .padding(horizontal = 10.dp, vertical = 4.dp))   // ios: Views/HoldingsView.swift:1081
            }
            if (res.isEmpty()) {   // ios: Views/HoldingsView.swift:1086
                T("아직 이사 온 주민이 없어요", Modifier.align(Alignment.Center), style = appFont(14f), color = Color.White)   // ios: Views/HoldingsView.swift:1087
            }
            val r = talk
            if (r != null) {   // ios: Views/HoldingsView.swift:1089
                // 답을 쓰는 동안 대사창만 제자리에서 키보드 바로 위까지 올라간다 (동네는 고정)
                val lift = if (typing) min(0f, kbTop - with(dens) { 8.dp.toPx() } - talkBottom) else 0f   // ios: Views/HoldingsView.swift:1091
                val liftA = animateFloatAsState(lift, tween(250, easing = EaseOut), label = "lift")   // ios: Views/HoldingsView.swift:1097
                Column(Modifier.matchParentSize()   // ios: Views/HoldingsView.swift:1092
                    .onGloballyPositioned { c ->   // ios: Views/HoldingsView.swift:1100,1101
                        talkBottom = c.boundsInWindow().bottom - with(dens) { 10.dp.toPx() }   // ios: Views/HoldingsView.swift:1102,1103
                        rootH = c.findRootCoordinates().size.height.toFloat()
                    }
                    .padding(10.dp)) {   // ios: Views/HoldingsView.swift:1099
                    Spacer(Modifier.weight(1f))   // ios: Views/HoldingsView.swift:1093
                    TownTalk(res = r, note = look ?: book.note(r.id), hello = fresh.contains(r.id), typing = { typing = it }, line = line, tab = tab, ask = ask,   // ios: Views/HoldingsView.swift:1094
                        dress = { tab = if (tab == 2) 0 else 2 }, us = { tab = if (tab == 1) 0 else 1 },   // ios: Views/HoldingsView.swift:1095
                        modifier = Modifier.offset { IntOffset(0, liftA.value.roundToInt()) }) { talk = null; typing = false }   // ios: Views/HoldingsView.swift:1096
                }
            }
        }
        // 대사창 아래 칸: 오늘의 한 줄·할 말 고르기 / 우리 사이 / 꾸미기 (지난 대화는 우리 사이 → 전체 보기)
        val r = talk
        if (r != null) {   // ios: Views/HoldingsView.swift:1110
            // 우리 사이·꾸미기는 넉넉히 280 (페이지가 스크롤되니 화면 높이에 매이지 않는다)
            TownPanel(res = r, tab = tabS, line = lineS, look = lookS, ask = askS, height = if (tab == 0) TownPanel.height else 280.dp)   // ios: Views/HoldingsView.swift:1112
            Spacer(Modifier.height(4.dp).onGloballyPositioned { anchors["townPanel"] = it })   // ios: Views/HoldingsView.swift:1113 스크롤 맞춤 자리 (칸 아랫변이 탭바에 붙지 않게)
        }
    }
    LaunchedEffect(talk?.id) {   // ios: Views/HoldingsView.swift:1116
        tab = 0; line = null; look = null   // ios: Views/HoldingsView.swift:1117
        if (talk != null) { delay(50); scrollToPanel("townPanel", tween(300, easing = EaseOut)) }   // ios: Views/HoldingsView.swift:1118
    }
    LaunchedEffect(tab) { if (tab != 0) scrollToPanel("townPanel", tween(300, easing = EaseOut)) }   // ios: Views/HoldingsView.swift:1120
    val imeB = WindowInsets.ime.getBottom(dens)   // ios: Views/HoldingsView.swift:1121 키보드 높이가 바뀔 때마다
    LaunchedEffect(imeB, rootH) { kbTop = if (imeB > 0) rootH - imeB else Float.POSITIVE_INFINITY }   // ios: Views/HoldingsView.swift:1122

    LaunchedEffect(Unit) { sky.refresh() }   // ios: Views/HoldingsView.swift:1125 날씨는 하루 한 번만 받는다
    DisposableEffect(phase) {
        var away = false
        val obs = LifecycleEventObserver { _, e ->   // ios: Views/HoldingsView.swift:1126
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { away = true; resumed = false }
                // 다른 앱에 갔다 돌아오면 주민들이 자리를 바꿔 서 있다
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    if (away) { order = order.shuffled(); walk.clear(); face.clear(); meet = null; scope.launch { walkAnim.values.forEach { it.snapTo(Offset.Zero) } } }   // ios: Views/HoldingsView.swift:1128
                    away = false
                }
                else -> {}
            }
        }
        phase.lifecycle.addObserver(obs)
        onDispose { phase.lifecycle.removeObserver(obs) }
    }
    // 화면에 있을 때만 2초에 한 칸 (사라지거나 탭이 숨으면·앱이 뒤로 가면 멈춤). 평소엔 숨만 쉬고, 6초(3칸)에 한 번 작은 일 하나만:
    // 고개 돌려 둘러보기 · 근처 산책 · 가까운 이웃 찾아가 수다 · 가끔 폴짝
    LaunchedEffect(active, reduce, resumed) {   // ios: Views/HoldingsView.swift:1130
        if (!active || reduce || !resumed) return@LaunchedEffect
        while (isActive) {   // ios: Views/HoldingsView.swift:1133
            delay(2000)   // ios: Views/HoldingsView.swift:1134
            tick += 1   // ios: Views/HoldingsView.swift:1135
            hop = null   // ios: Views/HoldingsView.swift:1136
            // 걷기: 초당 16pt로 아주 천천히. 도착하면 그 주민만 멈춰 숨쉬기로
            fun go(k: String, to: Offset) {   // ios: Views/HoldingsView.swift:1138
                val w = walk[k] ?: Offset.Zero; val d = max(0.6, hypot((to.x - w.x).toDouble(), (to.y - w.y).toDouble()) / 16)   // ios: Views/HoldingsView.swift:1139
                // 동네에선 뒷모습 없이 옆모습으로만 걷는다 (위아래로 가도 가는 쪽 옆을 본다)
                if (to.x != w.x) face[k] = if (to.x < w.x) CharDir.left else CharDir.right   // ios: Views/HoldingsView.swift:1141
                pace[k] = d; moving = moving + k   // ios: Views/HoldingsView.swift:1142
                walk[k] = to; launch { walkAnimatable(k).animateTo(to, tween((d * 1000).toInt(), easing = LinearEasing)) }   // ios: Views/HoldingsView.swift:1143 걸어가는 동안 위치를 고르게 보간
                launch { delay((d * 1000).toLong()); moving = moving - k }   // ios: Views/HoldingsView.swift:1144
            }
            fun at(k: String): Offset? = seats[k]?.let { Offset(it.x + (walk[k]?.x ?: 0f), it.y + (walk[k]?.y ?: 0f)) }   // ios: Views/HoldingsView.swift:1146
            // 수다: 도착하면 서로 마주 보고, 3칸 뒤 찾아간 주민이 원래 자리로 돌아간다. 그동안 다른 일은 쉰다
            val mt = meet
            if (mt != null) {   // ios: Views/HoldingsView.swift:1148
                if (moving.contains(mt.a)) continue   // ios: Views/HoldingsView.swift:1149 아직 걷는 중
                if (mt.back) { meet = null; face[mt.a] = CharDir.down }   // ios: Views/HoldingsView.swift:1150
                else if (tick >= mt.until) { go(mt.a, mt.home); meet = meet?.copy(back = true) }   // ios: Views/HoldingsView.swift:1151
                else if (!mt.met) {   // ios: Views/HoldingsView.swift:1152
                    face[mt.a] = if (mt.side < 0) CharDir.right else CharDir.left; face[mt.b] = if (mt.side < 0) CharDir.left else CharDir.right; meet = meet?.copy(met = true)   // ios: Views/HoldingsView.swift:1153
                }
                continue   // ios: Views/HoldingsView.swift:1155
            }
            if (tick % 3 != 0) continue   // ios: Views/HoldingsView.swift:1157
            val ids = TownLook.residents(m.rows).take(TownView.maxRes).map { it.id } + listOf(TownLook.mayorID) + (if (TownLook.fomc() != null) listOf(TownLook.warshID) else emptyList())   // ios: Views/HoldingsView.swift:1158
            val id = ids.randomOrNull() ?: continue   // ios: Views/HoldingsView.swift:1159
            val now = walk[id] ?: Offset.Zero   // ios: Views/HoldingsView.swift:1160
            when (Random.nextInt(0, 10)) {   // ios: Views/HoldingsView.swift:1161
                in 0 until 4 ->   // ios: Views/HoldingsView.swift:1162 둘러보기: 걷지 않고 옆으로만 돌아본다
                    face[id] = if (face[id] == CharDir.left) CharDir.right else CharDir.left   // ios: Views/HoldingsView.swift:1163 옆모습으로만
                in 4 until 7 -> {   // ios: Views/HoldingsView.swift:1164 산책: 멀리 나갔으면 반쯤은 제자리로, 아니면 가까운 곳으로
                    val to = if (now != Offset.Zero && Random.nextBoolean()) Offset.Zero else Offset((listOf(-1, 1).random() * (6..12).random()).toFloat(), (-2..2).random().toFloat())   // ios: Views/HoldingsView.swift:1165,1166 주로 옆으로 걷는다
                    go(id, to)   // ios: Views/HoldingsView.swift:1167
                }
                in 7 until 9 -> {   // ios: Views/HoldingsView.swift:1168 수다: 120pt 안의 가장 가까운 이웃 옆으로 걸어가 선다
                    val pa = seats[id]; val here = at(id); if (pa == null || here == null) continue   // ios: Views/HoldingsView.swift:1169
                    val dist: (Offset) -> Float = { hypot(it.x - here.x, it.y - here.y) }   // ios: Views/HoldingsView.swift:1170
                    val others2: List<String> = (ids + listOf(TownLook.meID)).filter { it != id && !moving.contains(it) }   // ios: Views/HoldingsView.swift:1171
                    val cands: List<Pair<String, Offset>> = others2.mapNotNull { k -> at(k)?.let { k to it } }   // ios: Views/HoldingsView.swift:1172
                    val near = cands.minByOrNull { dist(it.second) }; if (near == null || dist(near.second) >= 120) continue   // ios: Views/HoldingsView.swift:1173
                    val (b, pb) = near   // ios: Views/HoldingsView.swift:1174
                    val side: Float = if (here.x < pb.x) -1f else 1f   // ios: Views/HoldingsView.swift:1175
                    val to = Offset(pb.x + side * 30 - pa.x, pb.y + 2 - pa.y)   // ios: Views/HoldingsView.swift:1176
                    meet = Meet(a = id, b = b, home = now, side = side, until = tick + 5)   // ios: Views/HoldingsView.swift:1177
                    go(id, to)   // ios: Views/HoldingsView.swift:1178
                }
                else -> hop = id   // ios: Views/HoldingsView.swift:1179
            }
        }
    }
    LaunchedEffect(all.joinToString(",") { it.id }) {   // ios: Views/HoldingsView.swift:1183
        // 다 판 주민은 떠난 날을 적어 옛 이웃으로 (시세·보유를 아직 못 읽었으면 건너뜀)
        if (m.rows.isNotEmpty()) book.sync(all)   // ios: Views/HoldingsView.swift:1185
        TownLook.host = res.firstOrNull { TownLook.earnDay(it) != null }?.let { TownLook.Host(it.id, TownLook.shown(it, book.note(it.id))) }   // ios: Views/HoldingsView.swift:1186
        // 처음 본 주민은 오늘을 이사 온 날로 적는다 (수첩이 비어 있던 첫 실행은 인사 없이)
        val first = book.isEmpty   // ios: Views/HoldingsView.swift:1188
        for (x in res) if (book.welcome(x.id) && !first) fresh = fresh + x.id   // ios: Views/HoldingsView.swift:1189
        if (arrive.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1190
            res.firstOrNull { x -> x.rows.any { it.id == arrive } }?.let { x -> fresh = fresh + x.id; talk = x }   // ios: Views/HoldingsView.swift:1191
            arrive = ""   // ios: Views/HoldingsView.swift:1192
        }
    }
    dress?.let { TownDress(res = it) { dress = null } }   // ios: Views/HoldingsView.swift:1195
    us?.let { TownUs(res = it) { us = null } }   // ios: Views/HoldingsView.swift:1196
}

/** 바닥: 풀밭과 흙길 + 나무 몇 그루 (그림 파일 없이 색만). 계절은 실제 달력으로 풀·잎 색만 바뀐다. 한 번 그려 두고 크기가 같으면 다시 안 그린다 */
// ios: Views/HoldingsView.swift:1201
@Composable
private fun ground(modifier: Modifier) {
    val se = remember { TownLook.season() }   // ios: Views/HoldingsView.swift:1202
    val grass = listOf(Color(0.55f, 0.78f, 0.43f), Color(0.40f, 0.68f, 0.35f),   // ios: Views/HoldingsView.swift:1203
        Color(0.66f, 0.67f, 0.37f), Color(0.84f, 0.88f, 0.91f))[se]   // ios: Views/HoldingsView.swift:1204
    val leaf = listOf(Color(0.98f, 0.74f, 0.82f), Color(0.20f, 0.50f, 0.24f),   // ios: Views/HoldingsView.swift:1205
        Color(0.92f, 0.52f, 0.20f), Color(0.97f, 0.98f, 1.0f))[se]   // ios: Views/HoldingsView.swift:1206
    Spacer(modifier.drawWithCache {   // ios: Views/HoldingsView.swift:1207
        val k = density
        val w = size.width / k; val h = size.height / k
        val road = Path().apply {   // ios: Views/HoldingsView.swift:1209
            moveTo(0f, h * 0.62f)   // ios: Views/HoldingsView.swift:1210
            cubicTo(w * 0.35f, h * 0.40f, w * 0.65f, h * 0.66f,   // ios: Views/HoldingsView.swift:1212
                w, h * 0.42f)   // ios: Views/HoldingsView.swift:1211
        }
        onDrawBehind {
            scale(k, k, pivot = Offset.Zero) {
                drawRect(grass, size = Size(w, h))   // ios: Views/HoldingsView.swift:1208
                drawPath(road, Color(0.84f, 0.74f, 0.53f), style = Stroke(26f))   // ios: Views/HoldingsView.swift:1214
                for (i in 0 until TownView.trees.size) {   // ios: Views/HoldingsView.swift:1215
                    val t = TownView.trees[i]   // ios: Views/HoldingsView.swift:1216
                    val cx = t.x * w; val cy = t.y * h   // ios: Views/HoldingsView.swift:1222 VStack 가운데가 이 점
                    val top = cy - (30f + 0f + 12f) / 2   // ios: Views/HoldingsView.swift:1217 (spacing 0: 잎 30 + 줄기 12)
                    drawRoundRect(leaf, Offset(cx - 34f / 2, top), Size(34f, 30f), CornerRadius(6f))   // ios: Views/HoldingsView.swift:1218
                    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(cx - 34f / 2, top), Size(34f, 30f), CornerRadius(6f), style = Stroke(2f))   // ios: Views/HoldingsView.swift:1219
                    drawRect(Color(0.45f, 0.30f, 0.18f), Offset(cx - 7f / 2, top + 30f), Size(7f, 12f))   // ios: Views/HoldingsView.swift:1220
                }
            }
        }
    })
}

/** 주민 얼굴: 종목 코드를 씨앗으로 캐릭터 생성 부품을 즉석 조합 (저장 없음, 같은 종목은 늘 같은 얼굴) */
// ios: Views/HoldingsView.swift:1229
object TownLook {
    /** 주민 = 사람. 대표가 같은 종목(테슬라·스페이스X → 머스크)은 한 주민이 같이 맡는다. 대표 정보가 없으면 종목마다 한 명. old: 옛 이웃이면 그때 회사 이름 */
    class Resident(val id: String, val rows: List<AppModel.Row>, val old: String? = null) {   // ios: Views/HoldingsView.swift:1231
        val name: String get() = if (id == mayorID) "트럼프" else if (id == warshID) "연준 의장 워시" else old ?: rows.joinToString(" · ") { it.sym.name }   // ios: Views/HoldingsView.swift:1232
        val value: Double get() = rows.fold(0.0) { a, r -> a + r.value }   // ios: Views/HoldingsView.swift:1233
    }
    // ios: Views/HoldingsView.swift:1235
    fun residents(rows: List<AppModel.Row>): List<Resident> {
        val keys = ArrayList<String>(); val by = HashMap<String, MutableList<AppModel.Row>>()   // ios: Views/HoldingsView.swift:1236
        for (r in rows.sortedByDescending { it.value }) {   // ios: Views/HoldingsView.swift:1237
            val k = Sample.profilesKo[r.id]?.let { "ceo:" + it.ceo.second } ?: r.id   // ios: Views/HoldingsView.swift:1238
            if (by[k] == null) keys.add(k)   // ios: Views/HoldingsView.swift:1239
            by.getOrPut(k) { mutableListOf() }.add(r)   // ios: Views/HoldingsView.swift:1240
        }
        return keys.map { Resident(id = it, rows = by[it]!!) }.sortedByDescending { it.value }   // ios: Views/HoldingsView.swift:1242
    }
    /** 기본 별명: 회사 이름 앞 글자 + 꼬리 (같은 주민은 늘 같은 별명) */
    // ios: Views/HoldingsView.swift:1245
    fun nick(res: Resident): String {
        if (res.id == mayorID) return "이장님"   // ios: Views/HoldingsView.swift:1246
        if (res.id == warshID) return "워시"   // ios: Views/HoldingsView.swift:1247
        if (res.id == meID) return MeChar.current?.let { if (it.name.isEmpty()) "나" else it.name } ?: "나"   // ios: Views/HoldingsView.swift:1248
        val base = (res.rows.firstOrNull()?.sym?.name ?: res.old ?: res.id).replace(" ", "")   // ios: Views/HoldingsView.swift:1249
        val ko = if (base.isEmpty()) false else base.codePointAt(0) in 0xAC00..0xD7A3   // ios: Views/HoldingsView.swift:1250
        val tails = if (ko) listOf("돌이", "순이", "찡", "뽀", "군") else listOf("y", "o", "ie", "ster")   // ios: Views/HoldingsView.swift:1251
        return base.take(if (ko) 2 else 4) + tails[(seed(res.id) % tails.size.toULong()).toInt()]   // ios: Views/HoldingsView.swift:1252
    }
    fun shown(res: Resident, n: TownBook.Note): String = n.name ?: nick(res)   // ios: Views/HoldingsView.swift:1254
    /** 기기·실행마다 같은 값이 나오는 해시 (FNV-1a 64) */
    // ios: Views/HoldingsView.swift:1256
    fun seed(s: String): ULong {
        var h: ULong = 0xcbf29ce484222325uL   // Views/HoldingsView.swift:1230 (검사 예외: Long 범위를 넘는 16진 리터럴은 u 접미사가 필요)
        for (b in s.encodeToByteArray()) h = (h xor b.toUByte().toULong()) * 0x100000001b3L.toULong()   // ios: Views/HoldingsView.swift:1258
        return h   // ios: Views/HoldingsView.swift:1259
    }
    /** n 이 있으면 꾸미기 '모습'에서 고른 부품을 덮어쓴다 (유명인은 머리를 따로 그리니 머리 모양·색은 빼고) */
    // ios: Views/HoldingsView.swift:1262
    fun char(id: String, n: TownBook.Note? = null): MeChar {
        if (id == meID) MeChar.current?.let { return it }   // ios: Views/HoldingsView.swift:1263
        var x = seed(id) or 1L.toULong()   // ios: Views/HoldingsView.swift:1264
        // ios: Views/HoldingsView.swift:1265 xorshift
        fun next(n: Int): Int {
            x = x xor (x shl 13); x = x xor (x shr 7); x = x xor (x shl 17)   // ios: Views/HoldingsView.swift:1266
            return (x % max(1, n).toULong()).toInt()   // ios: Views/HoldingsView.swift:1267
        }
        val p = HashMap<String, Int>(); val c = MeChar(name = "", parts = emptyMap(), type = "seri")   // ios: Views/HoldingsView.swift:1269
        for (g in listOf(MeGroup.hair, MeGroup.face, MeGroup.eyes, MeGroup.mouth, MeGroup.top, MeGroup.bottom)) p[g.raw] = 1 + next(g.count)   // ios: Views/HoldingsView.swift:1270
        p[MeGroup.color.raw] = 1 + next(MeColor.hair.size)   // ios: Views/HoldingsView.swift:1271
        p["skin"] = 1 + next(MeColor.skin.size)   // ios: Views/HoldingsView.swift:1272
        p[MeGroup.glasses.raw] = if (next(4) == 0) 1 + next(MeGroup.glasses.count) else 0   // ios: Views/HoldingsView.swift:1273
        p[MeGroup.mask.raw] = 0   // ios: Views/HoldingsView.swift:1274
        p[MeGroup.prop.raw] = 0   // ios: Views/HoldingsView.swift:1275
        // 유명인: 머리는 따로 그리니 부품 머리는 빼고, 피부·안경·윗옷만 맞춘다
        val st = TownStar.find(id)
        if (st != null) {   // ios: Views/HoldingsView.swift:1277
            p[MeGroup.hair.raw] = 0   // ios: Views/HoldingsView.swift:1278
            p["skin"] = st.skin   // ios: Views/HoldingsView.swift:1279
            p[MeGroup.glasses.raw] = st.glasses   // ios: Views/HoldingsView.swift:1280
            if (st.top > 0) p[MeGroup.top.raw] = st.top   // ios: Views/HoldingsView.swift:1281
        }
        val star = TownStar.find(id) != null   // ios: Views/HoldingsView.swift:1283
        for ((k, v) in n?.parts ?: emptyMap()) if (!(star && (k == "hair" || k == "color"))) p[k] = v   // ios: Views/HoldingsView.swift:1284
        return c.copy(parts = p.toMap())   // ios: Views/HoldingsView.swift:1285
    }
    /** 하루 한마디: 언어별 문장 틀 + 빈칸({move} 오르내림 · {season} 계절 · {days} 함께한 날). 틀은 주민·날짜 씨앗으로 골라 날마다 바뀐다 */
    val frames: Map<String, List<String>> = mapOf("ko" to listOf(   // ios: Views/HoldingsView.swift:1289
        "{season}이네요. {move}",   // ios: Views/HoldingsView.swift:1290
        "같이 지낸 지 {days}일째예요. {move}",   // ios: Views/HoldingsView.swift:1291
        "{move}\n오늘 마음은 어때요?",   // ios: Views/HoldingsView.swift:1292
        "{move}\n내일은 어떨 것 같아요?",   // ios: Views/HoldingsView.swift:1293
        "{season} 바람이 좋네요. {move}",   // ios: Views/HoldingsView.swift:1294
        "벌써 {days}일이나 함께했네요. {move}",   // ios: Views/HoldingsView.swift:1295
        "안녕하세요! {move}",   // ios: Views/HoldingsView.swift:1296
        "{move}\n한 줄 남겨 줄래요?",   // ios: Views/HoldingsView.swift:1297
        "왔어요? {move}",   // ios: Views/HoldingsView.swift:1298
        "{move}\n오늘은 어떤 하루였어요?",   // ios: Views/HoldingsView.swift:1299
        "{weather} {move}",   // ios: Views/HoldingsView.swift:1300
        "{weather}\n{move}",   // ios: Views/HoldingsView.swift:1301
    ))
    /** 이장 = 큰손. 동네에 늘 있고 원·달러 이야기를 한다 (이미 받은 환율만) */
    const val mayorID = "mayor"   // ios: Views/HoldingsView.swift:1304
    /** 워시: FOMC 5일 전부터 3일 뒤까지 상주 */
    const val warshID = "warsh"   // ios: Views/HoldingsView.swift:1306
    /** 나: 캐릭터 만들기에서 만든 내 캐릭터가 동네에 같이 산다 (만들었을 때만) */
    const val meID = "me"   // ios: Views/HoldingsView.swift:1308
    /** FOMC 까지 남은 날(음수면 지난 날), 앞뒤 기간이 아니면 null */
    // ios: Views/HoldingsView.swift:1309
    fun fomc(now: Instant = Instant.now()): Int? {
        val from = Day.str(now.plusSeconds(-3 * 86400L)); val until = Day.str(now.plusSeconds(5 * 86400L))   // ios: Views/HoldingsView.swift:1310
        val r = MarketEvents.rules.firstOrNull { it.id == "x_fomc" } ?: return null; val d = MarketEvents.dates(r, from = from, until = until).firstOrNull() ?: return null   // ios: Views/HoldingsView.swift:1311
        return Day.dday(d)   // ios: Views/HoldingsView.swift:1312
    }
    // ios: Views/HoldingsView.swift:1314
    fun warshLine(): String {
        val n = fomc() ?: 0   // ios: Views/HoldingsView.swift:1315
        if (n > 1) return "FOMC까지 ${n}일 남았어요. 금리 얘기하러 미리 왔어요."   // ios: Views/HoldingsView.swift:1316
        if (n == 1) return "내일이 FOMC예요. 다들 긴장되죠?"   // ios: Views/HoldingsView.swift:1317
        if (n == 0) return "오늘 FOMC예요. 결과가 나오면 알려 줄게요."   // ios: Views/HoldingsView.swift:1318
        return "FOMC 끝났어요. 며칠 더 머물다 갈게요."   // ios: Views/HoldingsView.swift:1319
    }
    val mayorFrames = listOf("동네는 오늘도 평화로워요. {fx}", "{weather} {fx}", "{fx}\n다들 잘 지내죠?", "이장이에요. {fx}", "{fx}\n바다 건너 소식도 챙겨요.")   // ios: Views/HoldingsView.swift:1321
    /** 옛 이웃: 다 팔아 떠난 주민이 가끔 놀러 와 함께한 시간을 얘기한다 */
    val oldFrames = listOf("오랜만이에요! 놀러 왔어요.\n{days}일 함께했던 거 기억나요?", "지나가다 들렀어요. 다들 잘 지내죠?", "{weather} 옛날 생각나서 왔어요.",   // ios: Views/HoldingsView.swift:1323
        "여기 그대로네요. 반가워요!", "함께한 {days}일, 가끔 생각나요.")   // ios: Views/HoldingsView.swift:1324
    /** 잡담 말풍선: 짧은 혼잣말 (기분·자리로 고름) */
    // ios: Views/HoldingsView.swift:1326
    fun chat(res: Resident, k: Int): String {
        val pool: List<String>   // ios: Views/HoldingsView.swift:1327
        if (res.id == mayorID) pool = listOf("환율 좀 봐!", "동네 최고!", "다들 잘하고 있어", "아주 훌륭해")   // ios: Views/HoldingsView.swift:1328
        else if (res.id == warshID) pool = listOf("금리는…", "데이터를 봐야죠", "음, 물가가…", "곧 FOMC")   // ios: Views/HoldingsView.swift:1329
        else if (res.old != null) pool = listOf("그대로네", "반가워!", "놀러 왔어")   // ios: Views/HoldingsView.swift:1330
        else if (mood(res) < 0) pool = listOf("흑…", "괜찮아질 거야", "오늘은 쉬자", "…")   // ios: Views/HoldingsView.swift:1331
        else pool = listOf("♪", "날씨 좋다", "차트 봤어?", "음냐", "산책 중", "기분 좋아!", "하암…")   // ios: Views/HoldingsView.swift:1332
        return pool[(seed(res.id + "$k") % pool.size.toULong()).toInt()]   // ios: Views/HoldingsView.swift:1333
    }
    /** 계절: 0 봄 · 1 여름 · 2 가을 · 3 겨울 (실제 달력) */
    // ios: Views/HoldingsView.swift:1336
    fun season(d: LocalDate = LocalDate.now()): Int {
        val mo = d.monthValue   // ios: Views/HoldingsView.swift:1337
        return if (mo in 3..5) 0 else if (mo in 6..8) 1 else if (mo in 9..11) 2 else 3   // ios: Views/HoldingsView.swift:1338
    }
    // ios: Views/HoldingsView.swift:1340
    fun fxLine(): String {
        val q = Market.shared.fx   // ios: Views/HoldingsView.swift:1341
        if (!(q.last > 0)) return "오늘 환율은 아직 못 봤어요."   // ios: Views/HoldingsView.swift:1342
        val w = cformat("%.0f", q.last); val d = q.last - q.prevClose   // ios: Views/HoldingsView.swift:1343
        if (!q.live || abs(d) < 0.5) return "원·달러는 ${w}원이에요."   // ios: Views/HoldingsView.swift:1344
        return cformat("원·달러는 ${w}원, 어제보다 %.0f원 ", abs(d)) + (if (d > 0) "올랐어요. 달러가 힘이 세졌네요." else "내렸어요. 원화가 힘을 냈네요.")   // ios: Views/HoldingsView.swift:1345
    }
    // ios: Views/HoldingsView.swift:1347
    fun daily(res: Resident, n: TownBook.Note, lang: String = "ko"): String {
        val wx = TownSky.shared.phrase   // ios: Views/HoldingsView.swift:1348
        // ios: Views/HoldingsView.swift:1349
        fun pick(a: List<String>): String {
            val pool = a.filter { wx != null || !it.contains("{weather}") }   // ios: Views/HoldingsView.swift:1350
            return pool[(seed(res.id + TownLog.day) % pool.size.toULong()).toInt()].replace("{weather}", wx ?: "")   // ios: Views/HoldingsView.swift:1351
        }
        val h = holiday()?.let { "오늘은 " + it + (if (batchim(it)) "이에요!\n" else "예요!\n") } ?: ""   // ios: Views/HoldingsView.swift:1353
        if (res.id == warshID) return h + warshLine() + "\n" + fxLine()   // ios: Views/HoldingsView.swift:1354
        if (res.id == mayorID) return h + pick(mayorFrames).replace("{fx}", fxLine())   // ios: Views/HoldingsView.swift:1355
        // 행사: 어닝콜 날은 모두 모여 그 주민의 한 줄을 듣는다. 전날엔 예측 한 줄을 부탁한다
        if (res.old == null && earnDay(res) != null) {   // ios: Views/HoldingsView.swift:1357
            val y = Day.str(Instant.now().plusSeconds(-86400))   // ios: Views/HoldingsView.swift:1358
            val mine = TownLog.shared.log(res.id).lastOrNull { it.day == y }?.me   // ios: Views/HoldingsView.swift:1359
            return "오늘은 제 실적 발표 날이에요! 다들 들으러 모였네요.\n" + (mine?.let { "어제 남긴 예측: “${it}”\n" } ?: "") + line(res)   // ios: Views/HoldingsView.swift:1360
        }
        if (res.old == null && earnDay(res, 1) != null) return "내일은 제 실적 발표 날이에요.\n어떨 것 같아요? 예측 한 줄 남겨 줄래요?"   // ios: Views/HoldingsView.swift:1362
        val ho = host; if (res.old == null && ho != null && ho.id != res.id) return h + "오늘은 ${ho.name} 실적 날이라 다 같이 듣고 있어요.\n" + line(res)   // ios: Views/HoldingsView.swift:1363
        // 몇 달 전 내 답이 있으면 그날은 그걸 꺼내 얘기한다
        TownLog.shared.recall(res.id, price(res))?.let { back -> return if (res.old != null) back else back + "\n" + line(res) }   // ios: Views/HoldingsView.swift:1365
        if (res.old != null) {   // ios: Views/HoldingsView.swift:1366
            val together = if (n.moved.isEmpty()) 0 else max(1, TownLog.age(n.moved) - TownLog.age(n.left ?: TownLog.day) + 1)   // ios: Views/HoldingsView.swift:1367
            return pick(oldFrames.filter { together > 1 || !it.contains("{days}") }).replace("{days}", "$together")   // ios: Views/HoldingsView.swift:1368
        }
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1370
        val days = runCatching { LocalDate.parse(n.moved, f) }.getOrNull()?.let { (runCatching { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }.getOrNull() ?: 0) + 1 } ?: 0   // ios: Views/HoldingsView.swift:1371
        val mo = LocalDate.now().monthValue   // ios: Views/HoldingsView.swift:1372
        val season = if (mo in 3..5) "봄" else if (mo in 6..8) "여름" else if (mo in 9..11) "가을" else "겨울"   // ios: Views/HoldingsView.swift:1373
        val all = frames[lang] ?: frames["ko"]!!   // ios: Views/HoldingsView.swift:1374
        // 계절은 석 달 내내 같아서 매일 말하면 지겹다. 계절이 바뀐 첫 주에만 꺼낸다
        val fresh = listOf(3, 6, 9, 12).contains(mo) && LocalDate.now().dayOfMonth <= 7   // ios: Views/HoldingsView.swift:1376
        val t = pick(all.filter { (days > 1 || !it.contains("{days}")) && (fresh || !it.contains("{season}")) })   // ios: Views/HoldingsView.swift:1377
        return h + t.replace("{move}", line(res))   // ios: Views/HoldingsView.swift:1378
            .replace("{season}", season)   // ios: Views/HoldingsView.swift:1379
            .replace("{days}", "$days")   // ios: Views/HoldingsView.swift:1380
    }
    /** 행사: 보유 종목 실적 날 (앱 일정표 MarketEvents 의 실적만, 바깥에서 따로 안 받음). ahead 0 오늘 · 1 내일 */
    // ios: Views/HoldingsView.swift:1383
    fun earnDay(res: Resident, ahead: Int = 0): String? {
        if (res.id == mayorID || res.id == warshID || res.old != null) return null   // ios: Views/HoldingsView.swift:1384
        val d = Day.str(Instant.now().plusSeconds(ahead.toLong() * 86400))   // ios: Views/HoldingsView.swift:1385
        return MarketEvents.live.firstOrNull { r -> r.id.startsWith("e_") && res.rows.any { it.id == r.target } && MarketEvents.dates(r, from = d, until = d).isNotEmpty() }?.kind   // ios: Views/HoldingsView.swift:1361
    }
    /** 오늘 모임의 주인공 (동네가 정해 둔다) */
    data class Host(val id: String, val name: String)
    var host: Host? = null   // ios: Views/HoldingsView.swift:1389
    /** 세계 기념일 (달력 고정 날만, 앱 안 표) */
    val holidays: Map<String, String> = mapOf(   // ios: Views/HoldingsView.swift:1391
        "01-01" to "새해 첫날", "02-14" to "밸런타인데이", "03-08" to "세계 여성의 날", "03-20" to "국제 행복의 날",   // ios: Views/HoldingsView.swift:1392
        "04-22" to "지구의 날", "05-01" to "노동절", "06-05" to "세계 환경의 날", "09-21" to "세계 평화의 날",   // ios: Views/HoldingsView.swift:1393
        "10-24" to "유엔의 날", "10-31" to "핼러윈", "12-25" to "크리스마스", "12-31" to "한 해 마지막 날",   // ios: Views/HoldingsView.swift:1394
    )
    // ios: Views/HoldingsView.swift:1396
    fun holiday(d: LocalDate = LocalDate.now()): String? {
        val f = DateTimeFormatter.ofPattern("MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1397
        return holidays[d.format(f)]   // ios: Views/HoldingsView.swift:1398
    }
    // ios: Views/HoldingsView.swift:1400
    fun batchim(s: String): Boolean {
        if (s.isEmpty()) return false; val c = s.codePointBefore(s.length); if (c !in 0xAC00..0xD7A3) return false   // ios: Views/HoldingsView.swift:1401
        return (c - 0xAC00) % 28 != 0   // ios: Views/HoldingsView.swift:1402
    }
    /** 주민 대사: 맡은 종목이 여럿이면 종목 이름을 붙여 한 줄씩 */
    // ios: Views/HoldingsView.swift:1406
    fun line(res: Resident): String =
        if (res.rows.size == 1) line(res.rows[0]) else res.rows.joinToString("\n") { it.sym.name + ", " + line(it) }   // ios: Views/HoldingsView.swift:1407
    /** 오르내림 한 줄. 오늘 시세가 있으면 오늘, 없으면 산 가격 대비 */
    // ios: Views/HoldingsView.swift:1409
    fun line(r: AppModel.Row): String {
        val live = r.sym.quote.live   // ios: Views/HoldingsView.swift:1410
        val v = move(r)   // ios: Views/HoldingsView.swift:1411
        val pct = cformat("%.1f%%", abs(v) * 100)   // ios: Views/HoldingsView.swift:1412
        val wh = if (live) "오늘" else "산 뒤로"   // ios: Views/HoldingsView.swift:1413
        if (abs(v) < 0.001) return if (live) "오늘은 그대로예요. 조용한 하루네요." else "산 뒤로 그대로예요."   // ios: Views/HoldingsView.swift:1414
        return if (v > 0) "$wh $pct 올랐어요!" else "$wh $pct 내렸어요…"   // ios: Views/HoldingsView.swift:1415
    }
    // ios: Views/HoldingsView.swift:1417
    fun move(r: AppModel.Row): Double =
        if (r.sym.quote.live) r.sym.quote.change else (if (r.cost > 0) r.value / r.cost - 1 else 0.0)   // ios: Views/HoldingsView.swift:1418
    /** 기분: 맡은 종목 오르내림을 평가액으로 묶은 값. 0 아래면 시무룩 */
    // ios: Views/HoldingsView.swift:1421
    fun mood(res: Resident): Double {
        val v = res.value   // ios: Views/HoldingsView.swift:1422
        return if (v > 0) res.rows.fold(0.0) { a, r -> a + move(r) * r.value } / v else 0.0   // ios: Views/HoldingsView.swift:1423
    }
    /** 예측을 매길 값: 대표 종목의 지금 가격 */
    // ios: Views/HoldingsView.swift:1426
    fun price(res: Resident): Double? {
        val p = res.rows.firstOrNull()?.sym?.quote?.last; if (p == null || !(p > 0)) return null   // ios: Views/HoldingsView.swift:1427
        return p   // ios: Views/HoldingsView.swift:1428
    }
    /** 내 답에서 방향 읽기: 오른다 +1, 내린다 -1, 모르겠으면 0 */
    // ios: Views/HoldingsView.swift:1431
    fun guess(s: String): Int {
        val up = listOf("오를", "오르", "올라", "오른", "상승", "반등", "떡상", "↑")   // ios: Views/HoldingsView.swift:1432
        val dn = listOf("내릴", "내려", "내린", "떨어", "하락", "빠질", "빠져", "떡락", "↓")   // ios: Views/HoldingsView.swift:1433
        val u = up.any { s.contains(it) }; val d = dn.any { s.contains(it) }   // ios: Views/HoldingsView.swift:1434
        return if (u == d) 0 else (if (u) 1 else -1)   // ios: Views/HoldingsView.swift:1435
    }
}

/**
 * 고전 RPG 대사창: 얼굴 · 이름 · 한 글자씩 찍히는 하루 한마디 · 내 답 한 줄.
 * 대사를 누르면 다 찍기 → 다 나왔으면 답장 칸. typing: 내 답을 쓰는 중인지 (@Binding 자리: 바뀔 때 알린다)
 */
// ios: Views/HoldingsView.swift:1441
@Composable
fun TownTalk(
    res: TownLook.Resident,   // ios: Views/HoldingsView.swift:1442
    note: TownBook.Note,   // ios: Views/HoldingsView.swift:1443
    hello: Boolean = false,   // ios: Views/HoldingsView.swift:1444
    typing: (Boolean) -> Unit,   // ios: Views/HoldingsView.swift:1445
    line: String? = null,   // ios: Views/HoldingsView.swift:1446 할 말 고르기·꾸미기에서 온 대답 (있으면 오늘 한마디 대신)
    tab: Int = 0,   // ios: Views/HoldingsView.swift:1447
    ask: Int = 0,   // ios: Views/HoldingsView.swift:1448
    dress: () -> Unit,   // ios: Views/HoldingsView.swift:1449
    us: () -> Unit,   // ios: Views/HoldingsView.swift:1450
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") close: () -> Unit,   // ios: Views/HoldingsView.swift:1451
) {
    var shown by remember { mutableIntStateOf(0) }   // ios: Views/HoldingsView.swift:1452
    var me by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1453
    var reply by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:1454 대사 박스를 누르면 답장 칸이 열린다
    var focus by remember { mutableStateOf(false) }   // ios: Views/HoldingsView.swift:1455
    var say by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1456
    val log = TownLog.shared   // ios: Views/HoldingsView.swift:1457
    fun fullOf(s: String): String = line ?: ((if (hello) "${TownLook.shown(res, note)}예요. 오늘 이사 왔어요! 이름이랑 모자 골라 줄래요?\n" else "") + s)   // ios: Views/HoldingsView.swift:1458
    val gold = Color(1f, 0.85f, 0.4f)   // ios: Views/HoldingsView.swift:1459
    val req = remember { FocusRequester() }
    var want by remember { mutableIntStateOf(0) }   // focus = true 자리: 답장 칸이 생긴 뒤 커서를 넣는다
    val fm = LocalFocusManager.current
    val full = fullOf(say)   // ios: Views/HoldingsView.swift:1460,1461
    // 스크린샷처럼: 왼쪽에 큰 초상화가 박스 위로 솟고, 이름표는 초상화 아래, 대사는 오른쪽
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {   // ios: Views/HoldingsView.swift:1463
        Column(Modifier.fillMaxWidth()
            .background(Color(0.10f, 0.11f, 0.20f), RoundedCornerShape(8.dp))   // ios: Views/HoldingsView.swift:1492
            .border(2.dp, Color.White, RoundedCornerShape(8.dp))   // ios: Views/HoldingsView.swift:1493
            .clickable(remember { MutableInteractionSource() }, indication = null) {   // ios: Views/HoldingsView.swift:1494,1495
                // 글자가 다 안 나왔으면 다 보여 주고, 다 나왔으면 답장 칸을 연다
                if (shown < full.length) shown = full.length   // ios: Views/HoldingsView.swift:1497
                else if (say.isNotEmpty()) { reply = true; want += 1 }   // ios: Views/HoldingsView.swift:1498
            }
            .padding(start = 128.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),   // ios: Views/HoldingsView.swift:1491
            verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/HoldingsView.swift:1464
            T(full.take(shown), Modifier.fillMaxWidth().heightIn(min = 58.dp), style = appFont(16f), color = Color.White)   // ios: Views/HoldingsView.swift:1465,1466
            if (reply && say.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1467
                Row(Modifier.fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))   // ios: Views/HoldingsView.swift:1480
                    .padding(horizontal = 8.dp, vertical = 6.dp),   // ios: Views/HoldingsView.swift:1479
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/HoldingsView.swift:1468
                    BasicTextField(me, { me = it }, Modifier.weight(1f).focusRequester(req)
                        .onFocusChanged { s ->   // ios: Views/HoldingsView.swift:1471,1512
                            if (s.isFocused != focus) {
                                focus = s.isFocused; typing(focus)   // ios: Views/HoldingsView.swift:1513
                                // 남기지 않고 닫으면 쓰던 글은 버리고 남겨 둔 답으로
                                if (!focus) me = log.log(res.id).lastOrNull()?.let { if (it.day == TownLog.day) it.me else null } ?: ""   // ios: Views/HoldingsView.swift:1515
                            }
                        },
                        textStyle = appFont(14f).copy(color = Color.White), singleLine = true, cursorBrush = SolidColor(Color.White),   // ios: Views/HoldingsView.swift:1470
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { log.answer(res.id, me, price = TownLook.price(res)) }),   // ios: Views/HoldingsView.swift:1472
                        decorationBox = { inner ->   // ios: Views/HoldingsView.swift:1469
                            Box { if (me.isEmpty()) T("한 줄 답 (예측·마음)", style = appFont(14f), color = Color.White.copy(alpha = 0.4f), maxLines = 1); inner() }
                        })
                    if (focus) {   // ios: Views/HoldingsView.swift:1473
                        T("그만", Modifier.plainClickable { fm.clearFocus() }, style = appFont(13f), color = Color.White.copy(alpha = 0.7f))   // ios: Views/HoldingsView.swift:1474
                        T("남기기", Modifier.plainClickable { log.answer(res.id, me, price = TownLook.price(res)); fm.clearFocus() },   // ios: Views/HoldingsView.swift:1475
                            style = appFont(13f, FontWeight.Bold), color = gold)   // ios: Views/HoldingsView.swift:1476
                    }
                }
            }
            // 우리 사이·꾸미기: 대사 박스 안 오른쪽 아래에 붙인다
            if (res.id != TownLook.mayorID && res.id != TownLook.warshID) {   // ios: Views/HoldingsView.swift:1483
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/HoldingsView.swift:1484
                    Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/HoldingsView.swift:1485
                    chip("우리 사이", tab == 1, gold, us)   // ios: Views/HoldingsView.swift:1486
                    chip("꾸미기", tab == 2, gold, dress)   // ios: Views/HoldingsView.swift:1487
                }
            }
        }
        // 큰 초상화: 박스 왼쪽에서 위로 솟는다 (누르는 건 박스가 받는다)
        Column(Modifier.padding(start = 6.dp, bottom = 6.dp),   // ios: Views/HoldingsView.swift:1509,1510
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-10).dp)) {   // ios: Views/HoldingsView.swift:1501
            TownFace(id = res.id, note = note, width = 110.dp, height = 155.dp)   // ios: Views/HoldingsView.swift:1502
            T(TownLook.shown(res, note), style = appFont(13f, FontWeight.Bold), color = gold, maxLines = 1, modifier = Modifier   // ios: Views/HoldingsView.swift:1503
                .widthIn(max = 116.dp)   // ios: Views/HoldingsView.swift:1505
                .background(Color(0.10f, 0.11f, 0.20f), RoundedCornerShape(6.dp))   // ios: Views/HoldingsView.swift:1506
                .border(2.dp, Color.White, RoundedCornerShape(6.dp))   // ios: Views/HoldingsView.swift:1507
                .padding(horizontal = 10.dp, vertical = 4.dp))   // ios: Views/HoldingsView.swift:1504
        }
    }
    LaunchedEffect(want) {   // 답장 칸이 그려진 다음 커서를 넣는다 (focus = true)
        if (want > 0) { withFrameNanos { }; runCatching { req.requestFocus() } }
    }
    val lastAsk = remember { intArrayOf(ask) }
    LaunchedEffect(ask) { if (ask != lastAsk[0]) { lastAsk[0] = ask; if (say.isNotEmpty()) { reply = true; want += 1 } } }   // ios: Views/HoldingsView.swift:1517
    LaunchedEffect(res.id + "|" + (line ?: "")) {   // ios: Views/HoldingsView.swift:1518
        // 오늘 처음 말을 걸 때 한마디를 정해 기록에 남긴다 (하루 동안 같은 말)
        val e = log.today(res.id) { TownLook.daily(res, note) }   // ios: Views/HoldingsView.swift:1520
        say = e.say; me = e.me ?: ""   // ios: Views/HoldingsView.swift:1521
        reply = me.isNotEmpty()   // ios: Views/HoldingsView.swift:1522 오늘 남긴 답이 있으면 칸을 열어 둔다
        shown = 0   // ios: Views/HoldingsView.swift:1523
        val n = fullOf(e.say).length   // ios: Views/HoldingsView.swift:1524
        while (shown < n) {   // ios: Views/HoldingsView.swift:1525
            delay(45)   // ios: Views/HoldingsView.swift:1526
            if (!isActive) return@LaunchedEffect   // ios: Views/HoldingsView.swift:1527
            shown += 1   // ios: Views/HoldingsView.swift:1528
        }
    }
}

// ios: Views/HoldingsView.swift:1533
@Composable
private fun chip(t: String, on: Boolean, gold: Color, a: () -> Unit) {
    T(t, style = appFont(12f, FontWeight.Bold), color = if (on) Color(0.10f, 0.11f, 0.20f) else Color.White, modifier = Modifier.plainClickable(a)   // ios: Views/HoldingsView.swift:1534
        .background(if (on) gold else Color.Transparent, RoundedCornerShape(4.dp))   // ios: Views/HoldingsView.swift:1536
        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))   // ios: Views/HoldingsView.swift:1537
        .padding(horizontal = 8.dp, vertical = 4.dp))   // ios: Views/HoldingsView.swift:1535
}

/** 지난 대화: 주민 말·내 답을 목록으로 (최근 것이 위). 내가 답한 것만 */
@Composable
fun TownPast(id: String, modifier: Modifier = Modifier) {   // ios: Views/HoldingsView.swift:1542,1543
    val log = TownLog.shared   // ios: Views/HoldingsView.swift:1544
    val past = log.log(id).filter { it.me != null }.reversed()   // ios: Views/HoldingsView.swift:1545,1546
    if (past.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1547
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/HoldingsView.swift:1548
            T("지난 대화", style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/HoldingsView.swift:1549
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp)   // ios: Views/HoldingsView.swift:1573
                .background(Theme.card, RoundedCornerShape(12.dp))   // ios: Views/HoldingsView.swift:1574
                .verticalScroll(rememberScrollState())   // ios: Views/HoldingsView.swift:1550
                .padding(12.dp),   // ios: Views/HoldingsView.swift:1571
                verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/HoldingsView.swift:1551
                for (e in past) {   // ios: Views/HoldingsView.swift:1552
                    // 메신저 풍선: 날짜 · 주민 말(왼쪽) · 내 답(오른쪽)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/HoldingsView.swift:1554
                        T(e.day.drop(5).replace("-", "."), style = appFont(11f), color = Theme.sub2)   // ios: Views/HoldingsView.swift:1555
                        Box(Modifier.fillMaxWidth().padding(end = 40.dp), contentAlignment = Alignment.CenterStart) {   // ios: Views/HoldingsView.swift:1559,1560
                            T(e.say, Modifier.background(Theme.sub2.copy(alpha = 0.15f), RoundedCornerShape(14.dp))   // ios: Views/HoldingsView.swift:1556,1558
                                .padding(horizontal = 10.dp, vertical = 7.dp), style = appFont(14f))   // ios: Views/HoldingsView.swift:1557
                        }
                        val a = e.me
                        if (a != null) {   // ios: Views/HoldingsView.swift:1561
                            Box(Modifier.fillMaxWidth().padding(start = 40.dp), contentAlignment = Alignment.CenterEnd) {   // ios: Views/HoldingsView.swift:1565,1566
                                T(a, Modifier.background(Theme.teal, RoundedCornerShape(14.dp))   // ios: Views/HoldingsView.swift:1562,1564
                                    .padding(horizontal = 10.dp, vertical = 7.dp), style = appFont(14f), color = Color.White)   // ios: Views/HoldingsView.swift:1563
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 주민 수첩: 별명 · 모자 · 모자 색 · 이사 온 날. 주민마다 몇 글자라 앱 설정 한 칸에 JSON(Base64, 백업과 같은 꼴). 떠나도 지우지 않는다: 옛 이웃 */
// ios: Views/HoldingsView.swift:1581
class TownBook private constructor() {
    companion object {
        val shared: TownBook by lazy { TownBook() }   // ios: Views/HoldingsView.swift:1582
        private const val key = "town.book"   // ios: Views/HoldingsView.swift:1588
        private fun load(): Map<String, Note> = Defaults.prefs.getString(key, null)?.let {   // ios: Views/HoldingsView.swift:1590
            runCatching { NJson.decodeFromString<Map<String, Note>>(Base64.getDecoder().decode(it).decodeToString()) }.getOrNull() } ?: emptyMap()
    }
    // co: 회사 이름(떠나도 부르려고), left: 다 팔고 떠난 날, parts: 꾸미기 '모습'에서 고른 부품, candyDay: 이 주민에게 답을 남겨 별사탕을 받은 날
    @Serializable
    data class Note(var name: String? = null, var hat: Int = 0, var tint: Int = 0, var moved: String = "", var gifts: Int? = null, var cheer: String? = null,   // ios: Views/HoldingsView.swift:1583
        var co: String? = null, var left: String? = null, var prop: Int? = null,   // ios: Views/HoldingsView.swift:1584
        var parts: Map<String, Int>? = null,   // ios: Views/HoldingsView.swift:1585
        var candyDay: String? = null)   // ios: Views/HoldingsView.swift:1586
    var notes: Map<String, Note> by mutableStateOf(load())   // ios: Views/HoldingsView.swift:1587,1589
        private set
    /** 별사탕: 주민에게 답을 남길 때마다 하나 (주민마다 하루 하나). 시무룩한 주민에게 주면 그날 기운 차림 */
    var candy: Int by mutableIntStateOf(Defaults.prefs.getInt("town.candy", 0))   // ios: Views/HoldingsView.swift:1611
        private set
    /** 백업에서 불러온 뒤 다시 읽는다 */
    // ios: Views/HoldingsView.swift:1593
    fun reload() {
        notes = load()   // ios: Views/HoldingsView.swift:1594
        candy = Defaults.prefs.getInt("town.candy", 0)   // ios: Views/HoldingsView.swift:1595
    }
    val isEmpty: Boolean get() = notes.isEmpty()   // ios: Views/HoldingsView.swift:1597
    fun note(id: String): Note = notes[id]?.copy() ?: Note()   // ios: Views/HoldingsView.swift:1598
    // ios: Views/HoldingsView.swift:1599
    fun set(id: String, n: Note) {
        notes = notes + (id to n.copy())   // ios: Views/HoldingsView.swift:1600
        runCatching { Defaults.prefs.edit().putString(key, Base64.getEncoder().encodeToString(NJson.encodeToString(notes).encodeToByteArray())).apply() }   // ios: Views/HoldingsView.swift:1601
    }
    /** 처음 본 주민이면 오늘을 이사 온 날로 적는다. 새로 왔으면 true */
    // ios: Views/HoldingsView.swift:1604
    fun welcome(id: String): Boolean {
        if (notes[id] != null) return false   // ios: Views/HoldingsView.swift:1605
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1606
        set(id, Note(moved = LocalDate.now().format(f)))   // ios: Views/HoldingsView.swift:1607
        return true   // ios: Views/HoldingsView.swift:1608
    }
    // ios: Views/HoldingsView.swift:1612
    fun earn(id: String) {
        val d = TownLog.day   // ios: Views/HoldingsView.swift:1613
        if (notes[id]?.candyDay == d) return   // ios: Views/HoldingsView.swift:1614
        val n = note(id); n.candyDay = d; set(id, n)   // ios: Views/HoldingsView.swift:1615
        candy += 1; Defaults.prefs.edit().putInt("town.candy", candy).apply()   // ios: Views/HoldingsView.swift:1616
    }
    // ios: Views/HoldingsView.swift:1618
    fun give(id: String) {
        if (!(candy > 0)) return   // ios: Views/HoldingsView.swift:1619
        candy -= 1; Defaults.prefs.edit().putInt("town.candy", candy).apply()   // ios: Views/HoldingsView.swift:1620
        val n = note(id); n.gifts = (n.gifts ?: 0) + 1; n.cheer = TownLog.day   // ios: Views/HoldingsView.swift:1621
        set(id, n)   // ios: Views/HoldingsView.swift:1622
    }
    /** 꾸미기에서 별사탕으로 사기. 모자라면 false */
    // ios: Views/HoldingsView.swift:1625
    fun spend(n: Int): Boolean {
        if (!(n > 0 && candy >= n)) return n <= 0   // ios: Views/HoldingsView.swift:1626
        candy -= n; Defaults.prefs.edit().putInt("town.candy", candy).apply()   // ios: Views/HoldingsView.swift:1627
        return true   // ios: Views/HoldingsView.swift:1628
    }
    fun cheered(id: String): Boolean = notes[id]?.cheer == TownLog.day   // ios: Views/HoldingsView.swift:1630
    /** 지금 주민과 수첩 맞추기: 있는 주민은 회사 이름을 적고 떠난 표시를 지운다, 없어진 주민은 오늘을 떠난 날로 */
    // ios: Views/HoldingsView.swift:1632
    fun sync(res: List<TownLook.Resident>) {
        val now = res.map { it.id }.toSet()   // ios: Views/HoldingsView.swift:1633
        for (r in res) {   // ios: Views/HoldingsView.swift:1634
            val n = notes[r.id]?.copy() ?: continue   // ios: Views/HoldingsView.swift:1635
            if (n.co != r.name || n.left != null) { n.co = r.name; n.left = null; set(r.id, n) }   // ios: Views/HoldingsView.swift:1636
        }
        for ((id, x) in notes) if (!now.contains(id) && x.left == null && id != TownLook.mayorID) {   // ios: Views/HoldingsView.swift:1638
            val n = x.copy(); n.left = TownLog.day; set(id, n)   // ios: Views/HoldingsView.swift:1639
        }
    }
    /** 오늘 놀러 온 옛 이웃 (사흘에 한 번쯤, 날마다 한 명). 지금 주민은 빼고 */
    // ios: Views/HoldingsView.swift:1643
    fun visitor(except: Set<String>): String? {
        val olds = notes.filter { it.value.left != null && !except.contains(it.key) }.keys.sorted()   // ios: Views/HoldingsView.swift:1644
        val s = TownLook.seed("visit" + TownLog.day)   // ios: Views/HoldingsView.swift:1645
        if (olds.isEmpty() || s % 3.toULong() != 0.toULong()) return null   // ios: Views/HoldingsView.swift:1646
        return olds[((s / 3.toULong()) % olds.size.toULong()).toInt()]   // ios: Views/HoldingsView.swift:1647
    }
}

/**
 * 동네 날씨: 내 지역 실제 날씨를 하루 한 번만 받는다 (Open-Meteo, 키 없음).
 * 위치는 대략(ACCESS_COARSE_LOCATION, 기기 LocationManager 의 네트워크 위치)만 쓰고 소수 한 자리로 줄인다. 허락하지 않았거나 못 찾으면 서울.
 */
// ios: Views/HoldingsView.swift:1652
class TownSky private constructor() {
    companion object { val shared: TownSky by lazy { TownSky() } }   // ios: Views/HoldingsView.swift:1653
    enum class Kind { clear, cloud, fog, rain, snow, storm }   // ios: Views/HoldingsView.swift:1654
    var kind: Kind? by mutableStateOf(null)   // ios: Views/HoldingsView.swift:1655
        private set
    private val lm: LocationManager? get() = runCatching { Defaults.ctx.getSystemService(LocationManager::class.java) }.getOrNull()   // ios: Views/HoldingsView.swift:1656
    private var busy = false   // ios: Views/HoldingsView.swift:1657
    private val main = MainScope()
    /** 위치 허락 창 띄우기 (권한 창은 화면에서만 띄울 수 있어 동네 화면이 넣어 준다) */
    var ask: (() -> Unit)? = null

    // ios: Views/HoldingsView.swift:1658,1659
    init {
        val d = Defaults.prefs   // ios: Views/HoldingsView.swift:1660
        if (d.getString("town.skyDay", null) == TownLog.day) kind = Kind.entries.getOrNull(d.getInt("town.sky", 0))   // ios: Views/HoldingsView.swift:1661
    }
    // ios: Views/HoldingsView.swift:1663,1664
    val phrase: String? get() = when (kind) {
        Kind.clear -> "오늘은 맑네요."   // ios: Views/HoldingsView.swift:1665
        Kind.cloud -> "구름이 꼈네요."   // ios: Views/HoldingsView.swift:1666
        Kind.fog -> "안개가 자욱해요."   // ios: Views/HoldingsView.swift:1667
        Kind.rain -> "비가 오네요."   // ios: Views/HoldingsView.swift:1668
        Kind.snow -> "눈이 와요!"   // ios: Views/HoldingsView.swift:1669
        Kind.storm -> "천둥이 쳐요!"   // ios: Views/HoldingsView.swift:1670
        null -> null   // ios: Views/HoldingsView.swift:1671
    }
    private fun granted(): Boolean = Defaults.ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    /** 아직 묻지 않음 (iOS notDetermined): 허락 안 됐고 한 번도 안 물었다 */
    private fun notDetermined(): Boolean = !granted() && !Defaults.prefs.getBoolean("town.locAsked", false)

    // ios: Views/HoldingsView.swift:1674
    suspend fun refresh() {
        val d = Defaults.prefs   // ios: Views/HoldingsView.swift:1675
        if (d.getString("town.skyDay", null) == TownLog.day || busy) return   // ios: Views/HoldingsView.swift:1676
        busy = true; try {   // ios: Views/HoldingsView.swift:1677
            val a = ask
            if (notDetermined() && a != null) {   // ios: Views/HoldingsView.swift:1678,1679,1680
                a()   // ios: Views/HoldingsView.swift:1681 답이 오면 위치 → 다시 refresh
                return   // ios: Views/HoldingsView.swift:1682
            }
            if (granted() && !d.contains("town.lat")) {   // ios: Views/HoldingsView.swift:1684,1685,1686
                requestLocation()   // ios: Views/HoldingsView.swift:1687 처음 한 번만. 받으면 다시 refresh
                return   // ios: Views/HoldingsView.swift:1688
            }
            val lat = if (d.contains("town.lat")) Double.fromBits(d.getLong("town.lat", 0)) else 37.6; val lon = if (d.contains("town.lon")) Double.fromBits(d.getLong("town.lon", 0)) else 127.0   // ios: Views/HoldingsView.swift:1690
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=weather_code"   // ios: Views/HoldingsView.swift:1691
            val got = withContext(Dispatchers.IO) {   // ios: Views/HoldingsView.swift:1692
                runCatching {
                    (URL(url).openConnection() as HttpURLConnection).run { connectTimeout = 15000; readTimeout = 15000; inputStream.bufferedReader().use { it.readText() } }
                }.getOrNull()
            } ?: return
            val j = runCatching { NJson.parseToJsonElement(got).jsonObject }.getOrNull() ?: return   // ios: Views/HoldingsView.swift:1693
            val c = runCatching { j["current"]?.jsonObject?.get("weather_code")?.jsonPrimitive?.intOrNull }.getOrNull() ?: return   // ios: Views/HoldingsView.swift:1694
            val k: Kind = if (c <= 1) Kind.clear else if (c <= 3) Kind.cloud else if (c <= 48) Kind.fog else if (c in 71..77 || c in 85..86) Kind.snow else if (c >= 95) Kind.storm else Kind.rain   // ios: Views/HoldingsView.swift:1695
            kind = k   // ios: Views/HoldingsView.swift:1696
            d.edit().putInt("town.sky", k.ordinal).putString("town.skyDay", TownLog.day).apply()   // ios: Views/HoldingsView.swift:1697
        } finally { busy = false }
    }
    /** 허락 창의 답이 왔다 (거절이어도 다시 묻지 않고 서울로) */
    // ios: Views/HoldingsView.swift:1699
    fun didChangeAuthorization() {
        Defaults.prefs.edit().putBoolean("town.locAsked", true).apply()
        main.launch { if (!notDetermined()) refresh() }   // ios: Views/HoldingsView.swift:1700
    }
    /** 대략 위치 한 번 (네트워크 위치: 와이파이·기지국). 30초 안에 못 받으면 실패 */
    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        main.launch {
            val m = lm
            val loc: Location? = if (m == null || !m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) null else runCatching {
                m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: withTimeoutOrNull(30_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        if (Build.VERSION.SDK_INT >= 30) {
                            m.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, Defaults.ctx.mainExecutor) { l -> if (cont.isActive) cont.resume(l) }
                        } else {
                            val listener = object : LocationListener {
                                override fun onLocationChanged(l: Location) { if (cont.isActive) cont.resume(l) }
                                override fun onProviderEnabled(p: String) {}
                                override fun onProviderDisabled(p: String) { if (cont.isActive) cont.resume(null) }
                                @Deprecated("API 29 이하 기본 구현이 없다")
                                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                            }
                            @Suppress("DEPRECATION")
                            m.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { m.removeUpdates(listener) } }
                        }
                    }
                }
            }.getOrNull()
            if (loc != null) didUpdateLocations(loc) else didFailWithError()
        }
    }
    // ios: Views/HoldingsView.swift:1702
    private fun didUpdateLocations(l: Location) {
        val c = l   // ios: Views/HoldingsView.swift:1703
        val lat = rnd(c.latitude * 10) / 10; val lon = rnd(c.longitude * 10) / 10   // ios: Views/HoldingsView.swift:1704
        main.launch {   // ios: Views/HoldingsView.swift:1705
            Defaults.prefs.edit().putLong("town.lat", lat.toRawBits()).putLong("town.lon", lon.toRawBits()).apply()   // ios: Views/HoldingsView.swift:1706
            refresh()   // ios: Views/HoldingsView.swift:1707
        }
    }
    // ios: Views/HoldingsView.swift:1710
    private fun didFailWithError() {
        main.launch {   // ios: Views/HoldingsView.swift:1711
            Defaults.prefs.edit().putLong("town.lat", 37.6.toRawBits()).putLong("town.lon", 127.0.toRawBits()).apply()   // ios: Views/HoldingsView.swift:1712 못 찾으면 서울
            refresh()   // ios: Views/HoldingsView.swift:1713
        }
    }
    /** Swift .rounded(): 0.5 는 0 에서 먼 쪽으로 */
    private fun rnd(x: Double): Double = if (x < 0) -floor(-x + 0.5) else floor(x + 0.5)
}

/** 날씨 그림: 멈춘 빗줄기·눈송이·구름 그늘 (애니메이션 없음). 자리는 씨앗으로 정해 한 번만 계산 */
@Composable
fun TownWeather(kind: TownSky.Kind?, modifier: Modifier = Modifier) {   // ios: Views/HoldingsView.swift:1719,1720
    Spacer(modifier.drawWithCache {   // ios: Views/HoldingsView.swift:1721,1722
        val s = size; val u = density
        onDrawBehind {
            when (kind) {   // ios: Views/HoldingsView.swift:1723
                TownSky.Kind.rain, TownSky.Kind.storm -> {   // ios: Views/HoldingsView.swift:1724
                    if (kind == TownSky.Kind.storm) drawRect(Color.Black.copy(alpha = 0.18f))   // ios: Views/HoldingsView.swift:1725
                    for (i in 0 until 40) {   // ios: Views/HoldingsView.swift:1726
                        val x = (TownLook.seed("r$i") % 1000.toULong()).toFloat() / 1000 * s.width; val y = (TownLook.seed("ry$i") % 1000.toULong()).toFloat() / 1000 * s.height   // ios: Views/HoldingsView.swift:1727
                        val a = Offset(x, y); val b = Offset(x - 4 * u, y + 12 * u)   // ios: Views/HoldingsView.swift:1728
                        drawLine(Color(0.75f, 0.85f, 1f).copy(alpha = 0.7f), a, b, strokeWidth = 1.5f * u)   // ios: Views/HoldingsView.swift:1729
                    }
                }
                TownSky.Kind.snow -> {   // ios: Views/HoldingsView.swift:1731
                    for (i in 0 until 36) {   // ios: Views/HoldingsView.swift:1732
                        val x = (TownLook.seed("s$i") % 1000.toULong()).toFloat() / 1000 * s.width; val y = (TownLook.seed("sy$i") % 1000.toULong()).toFloat() / 1000 * s.height   // ios: Views/HoldingsView.swift:1733
                        drawRect(Color.White.copy(alpha = 0.9f), Offset(x, y), Size(4 * u, 4 * u))   // ios: Views/HoldingsView.swift:1734
                    }
                }
                TownSky.Kind.cloud, TownSky.Kind.fog -> {   // ios: Views/HoldingsView.swift:1736
                    drawRect(Color.White.copy(alpha = if (kind == TownSky.Kind.fog) 0.28f else 0.0f))   // ios: Views/HoldingsView.swift:1737
                    drawRect(Color.Black.copy(alpha = if (kind == TownSky.Kind.cloud) 0.10f else 0.0f))   // ios: Views/HoldingsView.swift:1738
                }
                TownSky.Kind.clear ->   // ios: Views/HoldingsView.swift:1739
                    drawOval(Color(1f, 0.86f, 0.35f), Offset(s.width * 0.42f, 8 * u), Size(22 * u, 22 * u))   // ios: Views/HoldingsView.swift:1740
                null -> {}   // ios: Views/HoldingsView.swift:1741
            }
        }
    })
}

/** 대화 기록(일기장): 주민마다 날짜 · 하루 한마디 · 내 답 한 줄. 앱 설정 한 칸에 JSON(Base64, 백업과 같은 꼴), 주민마다 최근 400일만 */
// ios: Views/HoldingsView.swift:1748
class TownLog private constructor() {
    companion object {
        val shared: TownLog by lazy { TownLog() }   // ios: Views/HoldingsView.swift:1749
        private const val key = "town.log"   // ios: Views/HoldingsView.swift:1752
        private const val keep = 400   // ios: Views/HoldingsView.swift:1753
        private fun load(): Map<String, List<Entry>> = Defaults.prefs.getString(key, null)?.let {   // ios: Views/HoldingsView.swift:1754,1755
            runCatching { NJson.decodeFromString<Map<String, List<Entry>>>(Base64.getDecoder().decode(it).decodeToString()) }.getOrNull() } ?: emptyMap()
        val day: String get() { val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US); return LocalDate.now().format(f) }   // ios: Views/HoldingsView.swift:1760
        const val ripe = 90   // ios: Views/HoldingsView.swift:1793 내 답을 꺼내고 예측을 매기는 날 수 (석 달)
        // ios: Views/HoldingsView.swift:1794
        fun age(day: String): Int {
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1795
            return runCatching { LocalDate.parse(day, f) }.getOrNull()?.let { runCatching { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }.getOrNull() ?: 0 } ?: 0   // ios: Views/HoldingsView.swift:1796
        }
    }
    // lang: 한마디를 만든 앱 언어 (없으면 ko), p: 답할 때 가격, back: 꺼내 봤음
    @Serializable
    data class Entry(var day: String, var say: String, var me: String? = null, var p: Double? = null, var back: Boolean? = null, var lang: String? = null)   // ios: Views/HoldingsView.swift:1750
    var logs: Map<String, List<Entry>> by mutableStateOf(load())   // ios: Views/HoldingsView.swift:1751
        private set
    // ios: Views/HoldingsView.swift:1757
    fun reload() {
        logs = load()   // ios: Views/HoldingsView.swift:1758
    }
    fun log(id: String): List<Entry> = logs[id] ?: emptyList()   // ios: Views/HoldingsView.swift:1761
    /** 오늘 한마디. 오늘 처음이면 make 로 정해 적는다 */
    // ios: Views/HoldingsView.swift:1763
    fun today(id: String, make: () -> String): Entry {
        val d = day   // ios: Views/HoldingsView.swift:1764
        val lang = if (AppModel.en) "en" else "ko"   // ios: Views/HoldingsView.swift:1765
        val last = logs[id]?.lastOrNull()
        if (last != null && last.day == d) {   // ios: Views/HoldingsView.swift:1766
            // 언어를 바꾼 날: 오늘 한마디만 새 언어로 다시 (내 답·가격은 그대로)
            if ((last.lang ?: "ko") == lang) return last   // ios: Views/HoldingsView.swift:1768
            val e = last.copy(say = make(), lang = lang)   // ios: Views/HoldingsView.swift:1769
            val l = logs[id]!!; logs = logs + (id to (l.dropLast(1) + e)); save()   // ios: Views/HoldingsView.swift:1770
            return e   // ios: Views/HoldingsView.swift:1771
        }
        val e = Entry(day = d, say = make(), lang = lang)   // ios: Views/HoldingsView.swift:1773
        val l = (logs[id] ?: emptyList()).toMutableList()   // ios: Views/HoldingsView.swift:1774
        l.add(e)   // ios: Views/HoldingsView.swift:1775
        if (l.size > keep) repeat(l.size - keep) { l.removeAt(0) }   // ios: Views/HoldingsView.swift:1776
        logs = logs + (id to l.toList())   // ios: Views/HoldingsView.swift:1777
        save()   // ios: Views/HoldingsView.swift:1778
        return e   // ios: Views/HoldingsView.swift:1779
    }
    /** 오늘 내 답 한 줄 (빈 칸이면 지움) */
    // ios: Views/HoldingsView.swift:1782
    fun answer(id: String, me: String, price: Double? = null) {
        val l = logs[id]?.toMutableList(); val last = l?.lastOrNull(); if (l == null || last == null || last.day != day) return   // ios: Views/HoldingsView.swift:1783
        val t = me.trim()   // ios: Views/HoldingsView.swift:1784
        val v: String? = if (t.isEmpty()) null else t.take(60)   // ios: Views/HoldingsView.swift:1785
        if (v == last.me) return   // ios: Views/HoldingsView.swift:1786
        l[l.size - 1] = l[l.size - 1].copy(me = v)   // ios: Views/HoldingsView.swift:1787
        l[l.size - 1] = l[l.size - 1].copy(p = if (v == null) null else (last.p ?: price))   // ios: Views/HoldingsView.swift:1788
        logs = logs + (id to l.toList())   // ios: Views/HoldingsView.swift:1789
        save()   // ios: Views/HoldingsView.swift:1790
        if (v != null) TownBook.shared.earn(id)   // ios: Views/HoldingsView.swift:1791
    }
    /** 몇 달 전 내 답 하나 꺼내기 (오래된 것부터 한 번씩). 그때 가격과 견줘 맞혔는지 */
    // ios: Views/HoldingsView.swift:1799
    fun recall(id: String, price: Double?): String? {
        val l = logs[id]?.toMutableList() ?: return null; val i = l.indexOfFirst { it.me != null && it.back != true && age(it.day) >= ripe }   // ios: Views/HoldingsView.swift:1800
        if (i < 0) return null; val me = l[i].me ?: return null   // ios: Views/HoldingsView.swift:1801
        l[i] = l[i].copy(back = true)   // ios: Views/HoldingsView.swift:1802
        logs = logs + (id to l.toList())   // ios: Views/HoldingsView.swift:1803
        save()   // ios: Views/HoldingsView.swift:1804
        var s = "${max(3, age(l[i].day) / 30)}달 전에 \"${me}\"라고 했죠."   // ios: Views/HoldingsView.swift:1805
        val p = l[i].p; val now = price
        if (p != null && now != null && p > 0) {   // ios: Views/HoldingsView.swift:1806
            val v = now / p - 1   // ios: Views/HoldingsView.swift:1807
            s += cformat(" 그때보다 %.1f%% ", abs(v) * 100) + (if (v >= 0) "올랐어요." else "내렸어요.")   // ios: Views/HoldingsView.swift:1808
            val g = TownLook.guess(me)   // ios: Views/HoldingsView.swift:1809
            if (g != 0) s += if ((g > 0) == (v >= 0)) " 맞혔네요!" else " 빗나갔네요."   // ios: Views/HoldingsView.swift:1810
        }
        return s   // ios: Views/HoldingsView.swift:1812
    }
    /** 우리 사이: 남긴 답 · 매긴 예측(석 달 지난 것) · 맞힌 예측 */
    data class Score(val said: Int, val judged: Int, val hit: Int)
    // ios: Views/HoldingsView.swift:1815
    fun score(id: String, price: Double?): Score {
        val mine = log(id).filter { it.me != null }   // ios: Views/HoldingsView.swift:1816
        var j = 0; var h = 0   // ios: Views/HoldingsView.swift:1817
        if (price != null) {   // ios: Views/HoldingsView.swift:1818
            val now: Double = price
            for (e in mine) if (age(e.day) >= ripe) {   // ios: Views/HoldingsView.swift:1819
                val p = e.p; val me = e.me; if (p == null || !(p > 0) || me == null) continue   // ios: Views/HoldingsView.swift:1820
                val g = TownLook.guess(me)   // ios: Views/HoldingsView.swift:1821
                if (g == 0) continue   // ios: Views/HoldingsView.swift:1822
                j += 1   // ios: Views/HoldingsView.swift:1823
                if ((g > 0) == (now >= p)) h += 1   // ios: Views/HoldingsView.swift:1824
            }
        }
        return Score(mine.size, j, h)   // ios: Views/HoldingsView.swift:1827
    }
    // ios: Views/HoldingsView.swift:1829
    private fun save() {
        runCatching { Defaults.prefs.edit().putString(key, Base64.getEncoder().encodeToString(NJson.encodeToString(logs).encodeToByteArray())).apply() }   // ios: Views/HoldingsView.swift:1830
    }
}

/** 도트 한 칸 덩어리: (x, y, 너비, 높이, 색 0xRRGGBB). y 는 위에서부터 */
data class Px(val x: Int, val y: Int, val w: Int, val h: Int, val c: Long)

/** 주민 얼굴 + 모자. 모자는 그림 파일 없이 24×34 칸 위에 도트로 (테두리 한 칸 포함). frame: 동네에서 걷는 장면 (없으면 정면 서 있는 그림) */
// ios: Views/HoldingsView.swift:1835
@Composable
fun TownFace(
    id: String,   // ios: Views/HoldingsView.swift:1836
    note: TownBook.Note,   // ios: Views/HoldingsView.swift:1837
    width: Dp,   // ios: Views/HoldingsView.swift:1838
    height: Dp,   // ios: Views/HoldingsView.swift:1839
    frame: CharScene? = null,   // ios: Views/HoldingsView.swift:1841
    modifier: Modifier = Modifier,
) {
    val ch = remember(id, note) { TownLook.char(id, note) }
    Box(modifier.size(width, height)) {   // ios: Views/HoldingsView.swift:1844,1845
        val img = if (frame != null) remember(ch, frame) { MeMotion.image(ch, frame) } else null
        if (frame != null && img != null) {   // ios: Views/HoldingsView.swift:1846
            SceneSprite(img = img, width = width, height = height, rows = MeArt.H)   // ios: Views/HoldingsView.swift:1847
        } else MeAvatar(me = ch, width = width, height = height)   // ios: Views/HoldingsView.swift:1848
        val star = TownStar.find(id)?.px ?: emptyList()   // ios: Views/HoldingsView.swift:1850,1851
        val pr = note.prop ?: 0   // ios: Views/HoldingsView.swift:1852
        if (note.hat > 0 || pr > 0 || star.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1853
            val lift = frame?.lift == true
            Canvas(Modifier.size(width, height)   // ios: Views/HoldingsView.swift:1854,1864
                .offset { IntOffset(0, if (lift) -(height / MeArt.H).roundToPx() else 0) }) {   // ios: Views/HoldingsView.swift:1863 숨·걸음에 머리가 1px 오르면 모자도 같이
                val u = size.width / MeArt.W   // ios: Views/HoldingsView.swift:1855
                val c = TownFace.tints[min(note.tint, TownFace.tints.size - 1)]   // ios: Views/HoldingsView.swift:1856
                val d = frame?.dir   // ios: Views/HoldingsView.swift:1857
                val px = TownFace.turn(star, d, hair = true) + TownFace.turn(TownFace.pixels(note.hat, c) + TownFace.props(pr), d)   // ios: Views/HoldingsView.swift:1858
                fun col(v: Long): Color = Color(red = ((v shr 16) and 0xFF) / 255f, green = ((v shr 8) and 0xFF) / 255f, blue = (v and 0xFF) / 255f)   // ios: Views/HoldingsView.swift:1859
                for (r in px) drawRect(col(MeArt.outline.toLong()), Offset((r.x - 1) * u, (r.y - 1) * u), Size((r.w + 2) * u, (r.h + 2) * u))   // ios: Views/HoldingsView.swift:1860
                for (r in px + TownFace.turn(TownFace.thin(pr), d)) drawRect(col(r.c), Offset(r.x * u, r.y * u), Size(r.w * u, r.h * u))   // ios: Views/HoldingsView.swift:1861
            }
        }
    }
}

/** TownFace 의 static 값·덧그림 도트 */
object TownFace {
    val hats = listOf("없음", "밀짚모자", "비니", "왕관", "야구모자", "꽃")   // ios: Views/HoldingsView.swift:1842
    val tints: List<Long> = listOf(0xE8B04B, 0xD94F4F, 0x4F7FD9, 0x4FB06A, 0x8A5FD0, 0xF2F2F2, 0x303030)   // ios: Views/HoldingsView.swift:1843
    /** 옆으로 걸을 때 덧그림도 옆모습으로: 한 칸 앞으로. 유명인 머리는 앞쪽 옆머리를 빼고 뒤쪽 옆머리로 뒤통수를 덮는다. 왼쪽은 뒤집는다 */
    // ios: Views/HoldingsView.swift:1869
    fun turn(px: List<Px>, d: CharDir?, hair: Boolean = false): List<Px> {
        if (!(d == CharDir.left || d == CharDir.right)) return px   // ios: Views/HoldingsView.swift:1870
        return px.mapNotNull { r ->   // ios: Views/HoldingsView.swift:1871
            var x = r.x; val y = r.y; var w = r.w; val h = r.h; val c = r.c   // ios: Views/HoldingsView.swift:1872
            if (hair && y >= 5 && x >= 14) return@mapNotNull null   // ios: Views/HoldingsView.swift:1873
            if (hair && y >= 5 && x + w <= 7) w = 11 - x   // ios: Views/HoldingsView.swift:1874
            x += 1   // ios: Views/HoldingsView.swift:1875
            if (d == CharDir.left) x = MeArt.W - x - w   // ios: Views/HoldingsView.swift:1876
            Px(x, y, w, h, c)   // ios: Views/HoldingsView.swift:1877
        }
    }
    // ios: Views/HoldingsView.swift:1881
    fun pixels(hat: Int, c: Long): List<Px> {
        val dark = ((((c shr 16) and 0xFF) * 7 / 10) shl 16) or ((((c shr 8) and 0xFF) * 7 / 10) shl 8) or ((c and 0xFF) * 7 / 10)   // ios: Views/HoldingsView.swift:1882
        return when (hat) {   // ios: Views/HoldingsView.swift:1883
            1 -> listOf(Px(3, 5, 18, 1, c), Px(7, 2, 10, 3, c), Px(7, 4, 10, 1, dark))   // ios: Views/HoldingsView.swift:1884
            2 -> listOf(Px(6, 2, 12, 4, c), Px(6, 5, 12, 1, dark), Px(11, 0, 2, 2, 0xFFFFFF))   // ios: Views/HoldingsView.swift:1885
            3 -> listOf(Px(7, 3, 10, 2, c), Px(7, 1, 2, 2, c), Px(11, 0, 2, 3, c), Px(15, 1, 2, 2, c), Px(11, 3, 2, 1, 0xD94F4F))   // ios: Views/HoldingsView.swift:1886
            4 -> listOf(Px(6, 2, 12, 3, c), Px(12, 5, 9, 1, dark))   // ios: Views/HoldingsView.swift:1887
            5 -> listOf(Px(15, 2, 3, 3, c), Px(16, 3, 1, 1, 0xFFD54A))   // ios: Views/HoldingsView.swift:1888
            else -> emptyList()   // ios: Views/HoldingsView.swift:1889
        }
    }
    val propNames = listOf("없음", "안경", "목도리", "풍선")   // ios: Views/HoldingsView.swift:1892
    /** 소품 (모자처럼 테두리 한 칸). 1 안경은 테가 가늘어 thin 에만, 2 목도리, 3 풍선(손에 든 줄은 thin) */
    // ios: Views/HoldingsView.swift:1894
    fun props(n: Int): List<Px> = when (n) {   // ios: Views/HoldingsView.swift:1895
        2 -> listOf(Px(7, 15, 10, 2, 0xD94F4F), Px(13, 17, 3, 4, 0xD94F4F), Px(13, 19, 3, 1, 0xA83A3A), Px(8, 15, 1, 2, 0xF2F2F2), Px(11, 15, 1, 2, 0xF2F2F2))   // ios: Views/HoldingsView.swift:1896
        3 -> listOf(Px(19, 1, 4, 7, 0xE85D75), Px(18, 2, 6, 5, 0xE85D75), Px(20, 8, 2, 1, 0xC94860), Px(19, 3, 1, 2, 0xFFFFFF))   // ios: Views/HoldingsView.swift:1897
        else -> emptyList()   // ios: Views/HoldingsView.swift:1898
    }
    /** 테두리 없이 그리는 가는 선 (안경테 · 풍선 줄) */
    // ios: Views/HoldingsView.swift:1902
    fun thin(n: Int): List<Px> = when (n) {   // ios: Views/HoldingsView.swift:1903
        1 -> {   // ios: Views/HoldingsView.swift:1904
            val k: Long = 0x2B2B33   // ios: Views/HoldingsView.swift:1905
            listOf(7, 13).flatMap { x -> listOf(Px(x, 9, 4, 1, k), Px(x, 12, 4, 1, k), Px(x, 9, 1, 4, k), Px(x + 3, 9, 1, 4, k)) } + listOf(Px(11, 10, 2, 1, k))   // ios: Views/HoldingsView.swift:1906
        }
        3 -> listOf(Px(21, 9, 1, 12, 0x555555))   // ios: Views/HoldingsView.swift:1907
        else -> emptyList()   // ios: Views/HoldingsView.swift:1908
    }
}

/**
 * 유명인 주민: 부품 얼굴 위에 머리 모양만 따로 그린 도트를 얹는다 (풍자 캐리커처).
 * 주민 id 는 대표 이름("ceo:젠슨 황") 또는 종목 코드. 이장은 트럼프
 */
// ios: Views/HoldingsView.swift:1915
object TownStar {
    data class Look(val skin: Int = 1, val glasses: Int = 0, val top: Int = 0, val px: List<Px>)   // ios: Views/HoldingsView.swift:1916,1917
    /** 짧은 머리 기본꼴: 정수리 + 옆머리 */
    private fun crop(c: Long, d: Long, side: Int = 3): List<Px> = listOf(Px(7, 3, 10, 1, d), Px(6, 4, 12, 2, c), Px(5, 5, 2, side, c), Px(17, 5, 2, side, c))   // ios: Views/HoldingsView.swift:1919
    val looks: Map<String, Look> by lazy {   // ios: Views/HoldingsView.swift:1920
        val brown: Long = 0x6B4A2E; val dbrown: Long = 0x4A3220; val black: Long = 0x26201E; val grey: Long = 0xA9ADB4; val silver: Long = 0xD4D7DC   // ios: Views/HoldingsView.swift:1921
        val m = HashMap<String, Look>()   // ios: Views/HoldingsView.swift:1922
        // 빅7
        m["musk"] = Look(px = crop(0x4A3426, 0x33241A) + listOf(Px(6, 6, 5, 1, 0x4A3426)))   // ios: Views/HoldingsView.swift:1924
        m["jensen"] = Look(top = 5, px = crop(black, 0x111111, side = 4) + listOf(Px(8, 4, 4, 1, grey), Px(14, 4, 2, 1, grey)))   // ios: Views/HoldingsView.swift:1925
        m["nadella"] = Look(skin = 2, glasses = 1, px = listOf(Px(8, 4, 8, 1, grey), Px(5, 6, 2, 4, grey), Px(17, 6, 2, 4, grey)))   // ios: Views/HoldingsView.swift:1926
        m["zuck"] = Look(top = 5, px = listOf(Px(7, 3, 10, 1, dbrown), Px(6, 4, 12, 2, brown), Px(6, 6, 12, 1, brown), Px(5, 5, 1, 3, brown), Px(18, 5, 1, 3, brown)))   // ios: Views/HoldingsView.swift:1927
        m["pichai"] = Look(skin = 2, px = listOf(Px(8, 3, 8, 1, 0x111111), Px(6, 4, 12, 1, black), Px(5, 5, 2, 3, black), Px(17, 5, 2, 3, black)))   // ios: Views/HoldingsView.swift:1928
        m["jassy"] = Look(px = listOf(Px(5, 7, 2, 3, 0x5A4A40), Px(17, 7, 2, 3, 0x5A4A40), Px(9, 5, 2, 1, 0xFFF4E6)))   // ios: Views/HoldingsView.swift:1929 민머리 + 반짝
        m["ternus"] = Look(px = crop(0x7A5134, 0x5A3A24) + listOf(Px(12, 6, 5, 1, 0x7A5134)))   // ios: Views/HoldingsView.swift:1930
        // AI
        m["altman"] = Look(px = crop(brown, dbrown) + listOf(Px(8, 2, 2, 1, brown), Px(12, 2, 2, 1, brown), Px(15, 2, 2, 1, brown)))   // ios: Views/HoldingsView.swift:1932
        m["amodei"] = Look(glasses = 1, px = listOf(Px(6, 2, 12, 2, black), Px(5, 4, 14, 2, black), Px(4, 5, 2, 5, black), Px(18, 5, 2, 5, black), Px(7, 6, 3, 1, black), Px(13, 6, 3, 1, black)))   // ios: Views/HoldingsView.swift:1933
        m["hassabis"] = Look(px = listOf(Px(8, 3, 8, 1, black), Px(6, 4, 12, 1, black), Px(5, 5, 2, 3, black), Px(17, 5, 2, 3, black)))   // ios: Views/HoldingsView.swift:1934
        // 금리·정치
        m["warsh"] = Look(top = 8, px = crop(silver, grey) + listOf(Px(6, 6, 4, 1, silver), Px(11, 4, 1, 2, grey)))   // ios: Views/HoldingsView.swift:1936
        m["trump"] = Look(top = 8, px = listOf(Px(6, 2, 12, 2, 0xE8C060), Px(4, 4, 16, 2, 0xE8C060), Px(4, 6, 6, 1, 0xE8C060), Px(4, 5, 2, 4, 0xE8C060), Px(18, 5, 2, 4, 0xE8C060),   // ios: Views/HoldingsView.swift:1937
            Px(12, 3, 6, 1, 0xC9A040), Px(11, 17, 2, 6, 0xD32F2F)))   // ios: Views/HoldingsView.swift:1938
        m   // ios: Views/HoldingsView.swift:1939
    }   // ios: Views/HoldingsView.swift:1940
    /** 주민 id → 얼굴 열쇠 (대표 이름이나 종목 코드로 찾는다) */
    val keys: Map<String, String> = mapOf(   // ios: Views/HoldingsView.swift:1942
        "ceo:일론 머스크" to "musk", "TSLA" to "musk", "ceo:젠슨 황" to "jensen", "NVDA" to "jensen", "ceo:사티아 나델라" to "nadella", "MSFT" to "nadella",   // ios: Views/HoldingsView.swift:1943
        "ceo:마크 저커버그" to "zuck", "META" to "zuck", "ceo:순다르 피차이" to "pichai", "GOOGL" to "pichai", "GOOG" to "pichai",   // ios: Views/HoldingsView.swift:1944
        "ceo:앤디 재시" to "jassy", "AMZN" to "jassy", "ceo:존 터너스" to "ternus", "ceo:팀 쿡" to "ternus", "AAPL" to "ternus",   // ios: Views/HoldingsView.swift:1945
        "ceo:샘 올트먼" to "altman", "ceo:다리오 아모데이" to "amodei", "ceo:데미스 허사비스" to "hassabis",   // ios: Views/HoldingsView.swift:1946
        TownLook.warshID to "warsh", TownLook.mayorID to "trump",   // ios: Views/HoldingsView.swift:1947
    )
    fun find(id: String): Look? = keys[id]?.let { looks[it] }   // ios: Views/HoldingsView.swift:1949
}

/** 시트 맨 위 줄: 왼쪽 버튼 · 가운데 제목 · 오른쪽 버튼 (NavigationStack 툴바 cancellationAction·confirmationAction 자리) */
@Composable
private fun TownSheetBar(title: String, left: String, onClickLeft: () -> Unit, right: String? = null, onClickRight: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
        T(left, Modifier.align(Alignment.CenterStart).plainClickable(onClickLeft), style = TextStyle(fontSize = 17.sp), color = Theme.teal)
        T(title, Modifier.align(Alignment.Center).widthIn(max = 220.dp), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        if (right != null) T(right, Modifier.align(Alignment.CenterEnd).plainClickable(onClickRight), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = Theme.teal)
    }
}

/** 꾸미기: 이름 · 모자 · 모자 색. 저장하면 수첩에 적는다. 시트째 그린다 (닫으면 dismiss) */
@Composable
fun TownDress(res: TownLook.Resident, dismiss: () -> Unit) {   // ios: Views/HoldingsView.swift:1953,1954,1955
    var n by remember(res.id) { mutableStateOf(TownBook.shared.note(res.id)) }   // ios: Views/HoldingsView.swift:1956,1957
    Sheet(show = true, onDismiss = dismiss, medium = true) {   // ios: Views/HoldingsView.swift:1958,1959,1999
        TownSheetBar(res.name, "닫기", onClickLeft = dismiss, right = "저장", onClickRight = { TownBook.shared.set(res.id, n); dismiss() })   // ios: Views/HoldingsView.swift:1993,1994,1995,1996
        Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp),   // ios: Views/HoldingsView.swift:1992
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {   // ios: Views/HoldingsView.swift:1960
            TownFace(id = res.id, note = n, width = 96.dp, height = 136.dp)   // ios: Views/HoldingsView.swift:1961
            BasicTextField(n.name ?: "", { v -> n = n.copy(name = if (v.isEmpty()) null else v.take(10)) },   // ios: Views/HoldingsView.swift:1962
                Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(10.dp)).padding(10.dp),   // ios: Views/HoldingsView.swift:1964
                textStyle = appFont(16f).copy(color = Theme.ink, textAlign = TextAlign.Center), singleLine = true, cursorBrush = SolidColor(Theme.teal),   // ios: Views/HoldingsView.swift:1963
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) { if ((n.name ?: "").isEmpty()) T(TownLook.nick(res), style = appFont(16f), color = Theme.muted, maxLines = 1); inner() }
                })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/HoldingsView.swift:1965,1966
                TownFace.hats.forEachIndexed { i, t ->   // ios: Views/HoldingsView.swift:1967
                    T(t, Modifier.plainClickable { n = n.copy(hat = i) }   // ios: Views/HoldingsView.swift:1968,1974
                        .background(if (n.hat == i) Theme.card else Color.Transparent, CapsuleShape)   // ios: Views/HoldingsView.swift:1971
                        .border(1.dp, Theme.border, CapsuleShape)   // ios: Views/HoldingsView.swift:1972
                        .padding(horizontal = 10.dp, vertical = 6.dp),   // ios: Views/HoldingsView.swift:1970
                        style = appFont(13f, if (n.hat == i) FontWeight.Bold else FontWeight.Normal))   // ios: Views/HoldingsView.swift:1969
                }
            }
            if (n.hat > 0) {   // ios: Views/HoldingsView.swift:1978
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/HoldingsView.swift:1979
                    TownFace.tints.forEachIndexed { i, c ->   // ios: Views/HoldingsView.swift:1980
                        Box(Modifier.size(28.dp, 28.dp)   // ios: Views/HoldingsView.swift:1983
                            .plainClickable { n = n.copy(tint = i) }   // ios: Views/HoldingsView.swift:1981,1986
                            .background(Color(red = (c shr 16 and 0xFF) / 255f, green = (c shr 8 and 0xFF) / 255f, blue = (c and 0xFF) / 255f), CircleShape)   // ios: Views/HoldingsView.swift:1982
                            .border(if (n.tint == i) 2.dp else 1.dp, if (n.tint == i) Theme.ink else Theme.border, CircleShape))   // ios: Views/HoldingsView.swift:1984
                    }
                }
            }
            Spacer(Modifier.weight(1f))   // ios: Views/HoldingsView.swift:1990
        }
    }
}

/** 우리 사이: 이사 온 날 · 함께한 날 · 내 답과 예측 적중 · 별사탕 주기 (시장 정보 아님). 시트째 그린다 */
@Composable
fun TownUs(res: TownLook.Resident, dismiss: () -> Unit) {   // ios: Views/HoldingsView.swift:2004,2005,2006
    val book = TownBook.shared   // ios: Views/HoldingsView.swift:2007
    val n = book.note(res.id)   // ios: Views/HoldingsView.swift:2009
    val s = TownLog.shared.score(res.id, TownLook.price(res))   // ios: Views/HoldingsView.swift:2010
    val days = if (n.moved.isEmpty()) 0 else TownLog.age(n.moved) - TownLog.age(n.left ?: TownLog.day) + 1   // ios: Views/HoldingsView.swift:2011
    val sad = TownLook.mood(res) < 0   // ios: Views/HoldingsView.swift:2012
    val cheer = book.cheered(res.id)   // ios: Views/HoldingsView.swift:2013
    Sheet(show = true, onDismiss = dismiss, medium = true) {   // ios: Views/HoldingsView.swift:2008,2014,2040
        TownSheetBar(TownLook.shown(res, n), "닫기", onClickLeft = dismiss)   // ios: Views/HoldingsView.swift:2037,2038
        Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp),   // ios: Views/HoldingsView.swift:2036
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/HoldingsView.swift:2015
            TownFace(id = res.id, note = n, width = 72.dp, height = 102.dp)   // ios: Views/HoldingsView.swift:2016
            Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(12.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Views/HoldingsView.swift:2017,2025
                row("이사 온 날", n.moved.replace("-", "."))   // ios: Views/HoldingsView.swift:2018
                row("함께한 날", "${days}일")   // ios: Views/HoldingsView.swift:2019
                val l = n.left; if (l != null) row("떠난 날", l.replace("-", "."))   // ios: Views/HoldingsView.swift:2020
                row("남긴 답", "${s.said}줄")   // ios: Views/HoldingsView.swift:2021
                row("예측 적중", if (s.judged == 0) "석 달 뒤부터" else "${s.hit} / ${s.judged}")   // ios: Views/HoldingsView.swift:2022
                row("받은 별사탕", "${n.gifts ?: 0}개")   // ios: Views/HoldingsView.swift:2023
            }
            val off = cheer || !sad || book.candy == 0   // ios: Views/HoldingsView.swift:2032
            Box(Modifier.fillMaxWidth()
                .then(if (off) Modifier else Modifier.plainClickable { book.give(res.id) })   // ios: Views/HoldingsView.swift:2026,2031
                .background(Theme.card, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {   // ios: Views/HoldingsView.swift:2029
                T(if (cheer) "기운 났어요! ♥" else if (sad) "별사탕 주기 (${book.candy}개)" else "지금은 기운이 넘쳐요", Modifier.padding(12.dp), style = appFont(15f, FontWeight.Bold), color = if (off) Theme.muted else Theme.ink)   // ios: Views/HoldingsView.swift:2027,2028
            }
            T("별사탕은 주민에게 답을 남기면 하나씩 생겨요", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:2033
            Spacer(Modifier.weight(1f))   // ios: Views/HoldingsView.swift:2034
        }
    }
}

// ios: Views/HoldingsView.swift:2042
@Composable
private fun row(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/HoldingsView.swift:2043,2044
        T(k, style = appFont(14f), color = Theme.sub); Spacer(Modifier.weight(1f)); T(v, style = appFont(14f, FontWeight.Bold))
    }
}

/** TownGround 의 static 값 */
object TownGround {
    val names = listOf("들판", "광장", "언덕 위 집", "노란 문 골목")   // ios: Views/HoldingsView.swift:2052
}

/**
 * 동네 배경 그림 (GBA 젤다처럼 위에서 비스듬히, 사물은 앞면·그림자로 입체감). 0번 들판은 TownView 의 ground 그대로.
 * 한 번 그려 두고(drawWithCache), 입력(kind·season)과 크기가 같으면 다시 그리지 않는다. 좌표는 iOS 와 같은 pt(=dp) 로 그린다.
 */
@Composable
fun TownGround(kind: Int, season: Int, modifier: Modifier = Modifier) {   // ios: Views/HoldingsView.swift:2050,2051
    Spacer(modifier.drawWithCache {   // ios: Views/HoldingsView.swift:2054,2055
        val k = density
        onDrawBehind { scale(k, k, pivot = Offset.Zero) { townGround(kind, season, size.width / k.toDouble(), size.height / k.toDouble()) } }
    })
}

/** TownGround 그리기 본문 (W·H 는 dp) */
private fun DrawScope.townGround(kind: Int, season: Int, W: Double, H: Double) {   // ios: Views/HoldingsView.swift:2056
    val grass = listOf(hex(0x8CC76E), hex(0x66AD59), hex(0xA8AB5E), hex(0xD6E0E8))[season]   // ios: Views/HoldingsView.swift:2057
    val leaf = listOf(hex(0xF5BBD0), hex(0x34803D), hex(0xEA8533), hex(0xF7FAFF))[season]   // ios: Views/HoldingsView.swift:2058
    // ios: Views/HoldingsView.swift:2059
    fun R(x: Double, y: Double, w: Double, h: Double, c: Color, r: Double = 0.0) {
        val rc = Rect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())   // ios: Views/HoldingsView.swift:2060
        if (r > 0) drawRoundRect(c, rc.topLeft, rc.size, CornerRadius(r.toFloat())) else drawRect(c, rc.topLeft, rc.size)   // ios: Views/HoldingsView.swift:2061
    }
    // ios: Views/HoldingsView.swift:2063
    fun O(x: Double, y: Double, w: Double, h: Double, c: Color) {
        drawOval(c, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))   // ios: Views/HoldingsView.swift:2064
    }
    fun shadow(x: Double, y: Double, w: Double, h: Double) { O(x, y, w, h, Color.Black.copy(alpha = 0.22f)) }   // ios: Views/HoldingsView.swift:2066
    // ios: Views/HoldingsView.swift:2067
    fun tufts(seed: Int) {
        var v = seed.toLong()   // ios: Views/HoldingsView.swift:2068
        for (n in 0 until 70) {   // ios: Views/HoldingsView.swift:2069
            v = (v * 9301 + 49297) % 233280; val x = (v % max(1.0, W).toLong()).toDouble()   // ios: Views/HoldingsView.swift:2070
            v = (v * 9301 + 49297) % 233280; val y = (v % max(1.0, H).toLong()).toDouble()   // ios: Views/HoldingsView.swift:2071
            R(x, y, 5.0, 3.0, Color.Black.copy(alpha = 0.12f), 1.5); R(x + 1, y - 2, 2.0, 2.0, Color.White.copy(alpha = 0.3f))   // ios: Views/HoldingsView.swift:2072
        }
    }
    // ios: Views/HoldingsView.swift:2075
    fun tree(x: Double, y: Double, k: Double, fruit: Boolean = false) {
        val w = 52 * k   // ios: Views/HoldingsView.swift:2076
        shadow(x - 4, y + w * 0.74, w + 10, w * 0.26)   // ios: Views/HoldingsView.swift:2077
        R(x + w * 0.4, y + w * 0.55, w * 0.2, w * 0.4, hex(0x7A4A2A), 2.0)   // ios: Views/HoldingsView.swift:2078
        R(x + w * 0.5, y + w * 0.55, w * 0.1, w * 0.4, hex(0x5A3418))   // ios: Views/HoldingsView.swift:2079
        O(x, y, w, w * 0.8, leaf); O(x, y, w, w * 0.8, Color.Black.copy(alpha = 0.28f))   // ios: Views/HoldingsView.swift:2080
        O(x + w * 0.06, y + 2, w * 0.82, w * 0.62, leaf)   // ios: Views/HoldingsView.swift:2081
        O(x + w * 0.18, y + w * 0.1, w * 0.36, w * 0.22, Color.White.copy(alpha = 0.3f))   // ios: Views/HoldingsView.swift:2082
        if (fruit) {   // ios: Views/HoldingsView.swift:2083
            for (p in listOf(0.2 to 0.4, 0.6 to 0.3, 0.45 to 0.52, 0.72 to 0.48, 0.3 to 0.2, 0.55 to 0.12)) {   // ios: Views/HoldingsView.swift:2084
                O(x + w * p.first, y + w * p.second, 6.0, 6.0, hex(0xC83030)); R(x + w * p.first + 1, y + w * p.second + 1, 2.0, 2.0, hex(0xFF9080))   // ios: Views/HoldingsView.swift:2085
            }
        }
    }
    // ios: Views/HoldingsView.swift:2089
    fun house(x: Double, y: Double, w: Double, roof: Color) {
        val rh = w * 0.55; val wh = w * 0.44; val fy = y + rh + wh   // ios: Views/HoldingsView.swift:2090
        shadow(x + 6, fy - 8, w + 12, 16.0)   // ios: Views/HoldingsView.swift:2091
        R(x + 4, y + rh, w - 8, wh, hex(0xF0E0C0)); R(x + 4, fy - 7, w - 8, 7.0, hex(0xD0B890))   // ios: Views/HoldingsView.swift:2092
        R(x, y, w, rh, roof, 3.0); R(x, y + rh - 9, w, 9.0, Color.Black.copy(alpha = 0.25f)); R(x, y + rh / 2 - 2, w, 3.0, Color.Black.copy(alpha = 0.2f))   // ios: Views/HoldingsView.swift:2093
        R(x + 7, y + 5, w * 0.4, 3.0, Color.White.copy(alpha = 0.35f))   // ios: Views/HoldingsView.swift:2094
        R(x + w * 0.7, y - 12, 14.0, 18.0, hex(0xA89880)); R(x + w * 0.7, y - 12, 14.0, 4.0, hex(0xD8D0C0))   // ios: Views/HoldingsView.swift:2095
        val dx = x + w / 2 - 10   // ios: Views/HoldingsView.swift:2096
        R(dx, fy - 34, 20.0, 34.0, hex(0x6A4020)); R(dx + 2, fy - 32, 16.0, 3.0, hex(0x8A5A30)); R(dx + 13, fy - 19, 3.0, 3.0, hex(0xF0D060))   // ios: Views/HoldingsView.swift:2097
        for (wx in listOf(x + 12, x + w - 32)) {   // ios: Views/HoldingsView.swift:2098
            R(wx, y + rh + 10, 20.0, 16.0, hex(0x5A4030)); R(wx + 2, y + rh + 12, 16.0, 12.0, hex(0x88C8F0)); R(wx + 2, y + rh + 12, 7.0, 5.0, hex(0xC8ECFF))   // ios: Views/HoldingsView.swift:2099
        }
    }
    // ios: Views/HoldingsView.swift:2102
    fun fence(x: Double, y: Double, n: Int) {
        R(x, y + 5, n.toDouble() * 14 - 8, 3.0, hex(0xC08850)); R(x, y + 12, n.toDouble() * 14 - 8, 3.0, hex(0xC08850))   // ios: Views/HoldingsView.swift:2103
        for (i in 0 until n) {   // ios: Views/HoldingsView.swift:2104
            val px = x + i.toDouble() * 14   // ios: Views/HoldingsView.swift:2105
            R(px, y, 5.0, 19.0, hex(0xA87040)); R(px, y, 5.0, 3.0, hex(0xD8A060)); R(px + 3, y + 3, 2.0, 16.0, hex(0x7A4A20))   // ios: Views/HoldingsView.swift:2106
        }
    }
    // ios: Views/HoldingsView.swift:2109
    fun path(x: Double, y: Double, w: Double, h: Double) {
        R(x, y, w, h, hex(0xE0C890)); R(x, y, w, 3.0, hex(0xC8A870)); R(x, y, 3.0, h, hex(0xC8A870))   // ios: Views/HoldingsView.swift:2110
    }
    // ios: Views/HoldingsView.swift:2112
    fun rock(x: Double, y: Double) {
        shadow(x - 2, y + 11, 24.0, 8.0); R(x, y, 20.0, 15.0, hex(0x8A8A8A), 7.0); R(x + 3, y + 2, 12.0, 7.0, hex(0xC0C0C0), 5.0)   // ios: Views/HoldingsView.swift:2113
    }
    // ios: Views/HoldingsView.swift:2115
    fun pond(x: Double, y: Double, w: Double, h: Double) {
        R(x - 4, y - 4, w + 8, h + 8, Color.Black.copy(alpha = 0.18f), 20.0); R(x, y, w, h, hex(0x3878C8), 18.0)   // ios: Views/HoldingsView.swift:2116
        R(x + 5, y + 6, w - 10, h - 12, hex(0x58A8E8), 14.0)   // ios: Views/HoldingsView.swift:2117
        R(x + w * 0.3, y + h * 0.35, 18.0, 3.0, hex(0xC8ECFF)); R(x + w * 0.55, y + h * 0.6, 12.0, 3.0, hex(0xC8ECFF))   // ios: Views/HoldingsView.swift:2118
    }
    // ios: Views/HoldingsView.swift:2120
    fun flowers(x: Double, y: Double, c: Long) {
        O(x, y, 5.0, 5.0, hex(c)); O(x + 9, y + 6, 5.0, 5.0, hex(c)); O(x + 2, y + 12, 5.0, 5.0, Color.White); O(x + 12, y - 4, 4.0, 4.0, hex(0xF8E060))   // ios: Views/HoldingsView.swift:2121
    }
    // ios: Views/HoldingsView.swift:2123
    fun cat(x: Double, y: Double) {
        val c = hex(0xE0A050)   // ios: Views/HoldingsView.swift:2124
        R(x, y, 16.0, 9.0, c, 4.0); R(x + 10, y - 6, 9.0, 9.0, c, 3.0); R(x + 11, y - 9, 3.0, 4.0, c); R(x + 16, y - 9, 3.0, 4.0, c)   // ios: Views/HoldingsView.swift:2125
        R(x - 5, y - 5, 4.0, 10.0, hex(0xC08030), 2.0); R(x + 15, y - 3, 2.0, 2.0, hex(0x203020))   // ios: Views/HoldingsView.swift:2126
    }
    // ios: Views/HoldingsView.swift:2128
    fun hedge(x: Double, y: Double, w: Double) {
        shadow(x, y + 24, w, 9.0); R(x, y + 9, w, 19.0, Color.Black.copy(alpha = 0.2f), 6.0); R(x, y + 9, w, 19.0, leaf.copy(alpha = 0.85f), 6.0)   // ios: Views/HoldingsView.swift:2129
        R(x, y, w, 15.0, leaf, 7.0); R(x + 5, y + 3, w - 10, 3.0, Color.White.copy(alpha = 0.3f), 1.5)   // ios: Views/HoldingsView.swift:2130
    }

    R(0.0, 0.0, W, H, grass)   // ios: Views/HoldingsView.swift:2133
    when (kind) {   // ios: Views/HoldingsView.swift:2134
        1 -> {   // ios: Views/HoldingsView.swift:2135 광장: 위에 집 두 채, 길이 만나는 곳, 대추나무, 연못
            tufts(7)   // ios: Views/HoldingsView.swift:2136
            path(W / 2 - 22, 0.0, 44.0, H); path(0.0, H * 0.5 - 18, W, 36.0)   // ios: Views/HoldingsView.swift:2137
            tree(-18.0, -18.0, 1.0); tree(W - 46, -16.0, 0.9)   // ios: Views/HoldingsView.swift:2138
            house(14.0, 10.0, 108.0, hex(0xD85848)); house(W - 122, 6.0, 108.0, hex(0x4878C8))   // ios: Views/HoldingsView.swift:2139
            fence(14.0, 126.0, 6); fence(W - 106, 126.0, 6); cat(W - 64, 121.0)   // ios: Views/HoldingsView.swift:2140
            tree(-12.0, H - 86, 1.3, fruit = true); pond(W - 96, H - 64, 82.0, 50.0)   // ios: Views/HoldingsView.swift:2141
            rock(W * 0.3, H * 0.9); flowers(W * 0.12, H * 0.42, 0xF070A0); flowers(W * 0.8, H * 0.58, 0xF8F8F8)   // ios: Views/HoldingsView.swift:2142
        }
        2 -> {   // ios: Views/HoldingsView.swift:2143 언덕 위 집: 절벽 위 집·대추나무, 계단으로 내려오는 아랫동네
            tufts(11)   // ios: Views/HoldingsView.swift:2144
            val cy = H * 0.26   // ios: Views/HoldingsView.swift:2145
            R(0.0, 0.0, W, cy, Color.White.copy(alpha = 0.1f))   // ios: Views/HoldingsView.swift:2146
            tree(-16.0, -18.0, 0.9); house(W / 2 - 56, -6.0, 112.0, hex(0xC86040)); tree(W - 70, 8.0, 1.0, fruit = true)   // ios: Views/HoldingsView.swift:2147
            R(36.0, 56.0, 4.0, 18.0, hex(0x5A4030)); R(30.0, 48.0, 18.0, 11.0, hex(0xD83838), 4.0)   // ios: Views/HoldingsView.swift:2148
            for ((x0, x1) in listOf(0.0 to W / 2 - 24, W / 2 + 24 to W)) {   // ios: Views/HoldingsView.swift:2149
                R(x0, cy, x1 - x0, 32.0, hex(0xB88858))   // ios: Views/HoldingsView.swift:2150
                var i = x0 + 8; while (i < x1) { R(i, cy + 6, 3.0, 22.0, hex(0x986838)); i += 20 }   // ios: Views/HoldingsView.swift:2151
                R(x0, cy + 26, x1 - x0, 6.0, hex(0x7A5028)); R(x0, cy - 6, x1 - x0, 8.0, grass); R(x0, cy - 6, x1 - x0, 2.0, Color.White.copy(alpha = 0.35f))   // ios: Views/HoldingsView.swift:2152
            }
            R(W / 2 - 24, cy - 4, 48.0, 36.0, hex(0xD8C8A0))   // ios: Views/HoldingsView.swift:2154
            var st = cy; while (st < cy + 32) { R(W / 2 - 24, st, 48.0, 3.0, hex(0xB0A080)); st += 9 }   // ios: Views/HoldingsView.swift:2155
            path(W / 2 - 20, cy + 32, 40.0, H); path(0.0, H * 0.64, W, 34.0)   // ios: Views/HoldingsView.swift:2156
            rock(W * 0.16, H * 0.45); rock(W * 0.82, H * 0.48); flowers(W * 0.3, H * 0.86, 0xF8E060)   // ios: Views/HoldingsView.swift:2157
            pond(10.0, H - 62, 76.0, 48.0); tree(W - 58, H - 80, 1.1)   // ios: Views/HoldingsView.swift:2158
        }
        3 -> {   // ios: Views/HoldingsView.swift:2159 노란 문 골목: 담장 가운데 노란빛 문, 돌길, 양옆 덤불, 가로등
            val fx = W * 0.18; val fw = W * 0.64; val wh = H * 0.2   // ios: Views/HoldingsView.swift:2160
            R(fx, 0.0, fw, H, hex(0xB8B0A0))   // ios: Views/HoldingsView.swift:2161
            var y = 0.0; var row = 0   // ios: Views/HoldingsView.swift:2162
            while (y < H) { var x = fx + (if (row % 2 == 0) 0 else 10); while (x < fx + fw - 4) { R(x, y, 18.0, 18.0, hex(0xC8C0B0), 3.0); x += 20 }; y += 20; row += 1 }   // ios: Views/HoldingsView.swift:2163
            R(0.0, 0.0, W, 8.0, hex(0xD8D0C0)); R(0.0, 8.0, W, wh, hex(0xA89880))   // ios: Views/HoldingsView.swift:2164
            var by = 24.0; while (by < wh + 8) { R(0.0, by, W, 2.0, hex(0x8A7A62)); by += 18 }   // ios: Views/HoldingsView.swift:2165
            R(0.0, wh + 8, W, 8.0, Color.Black.copy(alpha = 0.18f))   // ios: Views/HoldingsView.swift:2166
            O(W / 2 - 50, wh + 4, 100.0, 46.0, hex(0xFFE678, 0.35f))   // ios: Views/HoldingsView.swift:2167
            R(W / 2 - 24, 14.0, 48.0, wh - 6, hex(0x8A6D3B), 3.0); R(W / 2 - 19, 19.0, 38.0, wh - 11, hex(0xF8E070))   // ios: Views/HoldingsView.swift:2168
            R(W / 2 - 15, 23.0, 14.0, 30.0, hex(0xFFF6C0)); R(W / 2 + 11, 19 + wh * 0.45, 4.0, 4.0, hex(0x8A6D3B))   // ios: Views/HoldingsView.swift:2169
            cat(W - 70, 3.0)   // ios: Views/HoldingsView.swift:2170
            for (hy in listOf(0.32, 0.52, 0.72)) { hedge(0.0, H * hy, fx); hedge(fx + fw, H * hy, W - fx - fw) }   // ios: Views/HoldingsView.swift:2171
            val lx = fx + 4; val ly = H * 0.3   // ios: Views/HoldingsView.swift:2172
            O(lx - 26, ly + 52, 64.0, 28.0, hex(0xFFE678, 0.25f)); shadow(lx - 3, ly + 62, 20.0, 7.0)   // ios: Views/HoldingsView.swift:2173
            R(lx + 5, ly + 8, 4.0, 58.0, hex(0x404850)); R(lx, ly, 14.0, 12.0, hex(0x303840), 4.0); R(lx + 3, ly + 3, 8.0, 7.0, hex(0xFFE890), 3.0)   // ios: Views/HoldingsView.swift:2174
            rock(W * 0.66, H * 0.8); tree(-14.0, H - 70, 1.0); tree(W - 46, H - 66, 0.9)   // ios: Views/HoldingsView.swift:2175
        }
        else -> {}   // ios: Views/HoldingsView.swift:2176
    }
}
