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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
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
import com.naeilo.app.ui.hold.HoldFmt
import com.naeilo.app.ui.rememberPrefBool
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

/** overlay(alignment: .top) + alignmentGuide(.top) { $0[.bottom] + gap }: 아랫변이 gap 만큼 위에 오게 띄운다 (줄이 늘면 위로 자란다) */
private fun Modifier.floatAbove(gap: Dp): Modifier = this.layout { m, _ ->
    val p = m.measure(Constraints())
    layout(0, 0) { p.place(-p.width / 2, -p.height - gap.roundToPx()) }
}

/** 동네 판 밖 오른쪽 아래 점수판: 전체 내 평가액 + ▲▼(글자의 70%) 오늘 등락 금액·%. 종목 탭 '오늘' 박스와 같은 값 */
@Composable
private fun TownTotal(m: AppModel) {   // ios: Views/HoldingsView.swift:1791
    val hideAmt by rememberPrefBool("hideAmt", false)   // 홈에서 켠 금액 가리기를 따른다
    if (m.total <= 0) return
    val mv = m.todayWon
    val c = if (mv > 0) Theme.up else if (mv < 0) Theme.down else Theme.sub
    val num = appFont(10f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum")   // 안드로이드는 iOS(11)보다 한 단계 작게 (드링커님 확인)
    // 넓은 기기는 한 줄(글자 밑선 맞춤), 모자라면 두 줄. iOS ViewThatFits 자리
    Layout({
        T(if (hideAmt) "••••••" else HoldFmt.won(m.total), style = appFont(15f, FontWeight.Bold), color = Theme.ink, maxLines = 1)
        Row {
            if (mv != 0.0) T(if (mv > 0) "▲ " else "▼ ", Modifier.alignByBaseline(), style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold), color = c)
            T((if (hideAmt) "" else HoldFmt.won(abs(mv)) + " ") + cformat("%.2f%%", abs(m.todayMove) * 100), Modifier.alignByBaseline(), style = num, color = c, maxLines = 1)
        }
    }) { ms, cs ->
        val a = ms[0].measure(Constraints()); val b = ms[1].measure(Constraints()); val gap = 6.dp.roundToPx()
        if (!cs.hasBoundedWidth || a.width + gap + b.width <= cs.maxWidth) {
            val dy = max(0, a[FirstBaseline] - b[FirstBaseline])
            layout(a.width + gap + b.width, max(a.height, b.height + dy)) { a.place(0, 0); b.place(a.width + gap, dy) }
        } else { val w = max(a.width, b.width); layout(w, a.height + b.height) { a.place(w - a.width, 0); b.place(w - b.width, a.height) } }
    }
}

/** 말풍선이 판 양옆 4dp 안에 들도록 옆으로 미는 만큼 (cx: 말하는 이 가운데, w: 판 폭) */
private fun bubbleShift(t: String, cx: Float, w: Float): Float {
    val half = (min(180f, t.length * 11f + 4f) + 23f) / 2   // 말풍선 폭 + 안쪽 여백 20 + 테두리
    return max(4 + half - cx, min(0f, w - 4 - half - cx))
}

private data class Meet(val a: String, val b: String, val home: Offset, val side: Float, val until: Int, val met: Boolean = false, val back: Boolean = false)

/** 말풍선 주고받기: 내 캐릭터가 곁에 가거나, 주민끼리 붙어 섰을 때 (한 마디에 2칸). lines: (말하는 이, 글) */
private data class Duet(val ids: Set<String>, val lines: List<Pair<String, String>>, val i: Int = 0, val until: Int)   // ios: Views/HoldingsView.swift:1486

/** TownView 의 static 값 */
object TownView {
    const val maxRes = 10   // ios:  손가락으로 셀 만큼 (평가액 큰 순)
    // 주민 자리 (동네 판 안의 비율 좌표). 앞 번호부터 채운다
    val spots: List<Offset> = listOf(   // ios: Views/HoldingsView.swift:1338
        Offset(0.28f, 0.36f), Offset(0.54f, 0.32f), Offset(0.76f, 0.40f), Offset(0.40f, 0.54f),   // ios: Views/HoldingsView.swift:1339
        Offset(0.66f, 0.58f), Offset(0.22f, 0.66f), Offset(0.50f, 0.74f), Offset(0.78f, 0.72f),   // ios: Views/HoldingsView.swift:1340
        Offset(0.34f, 0.82f), Offset(0.64f, 0.84f), Offset(0.84f, 0.26f), Offset(0.16f, 0.30f),   // ios: Views/HoldingsView.swift:1341
        Offset(0.14f, 0.48f),   // ios: Views/HoldingsView.swift:1342
    )
    val mayorSpot = Offset(0.84f, 0.26f)   // ios:  이장은 늘 오른쪽 위
    val visitSpot = Offset(0.14f, 0.48f)   // ios:  옛 이웃은 길 어귀에
    val meSpot = Offset(0.50f, 0.64f)      // ios: Views/HoldingsView.swift:1356 나는 길가 가운데 (모임 날)
    val warshSpot = Offset(0.16f, 0.28f)   // ios: Views/HoldingsView.swift:1357 워시는 왼쪽 위 나무 곁
    val stage = Offset(0.50f, 0.48f)       // ios: Views/HoldingsView.swift:1358 어닝콜 날 그 주민이 서는 가운데
    // 바닥: 풀밭과 흙길 + 나무 몇 그루 (그림 파일 없이 색만). 계절은 실제 달력으로 풀·잎 색만 바뀐다
    val trees: List<Offset> = listOf(Offset(0.07f, 0.13f), Offset(0.68f, 0.10f), Offset(0.95f, 0.58f), Offset(0.05f, 0.93f))   // ios: Views/HoldingsView.swift:1568
}

/**
 * 작은 세상 첫 조각: 보유 종목이 동네 주민으로 서 있고, 누르면 대사창에 오르내림 한 줄.
 * scroll: 동네를 담은 세로 스크롤 (말 걸면 아래 칸이 보이게 페이지를 살짝 내린다). 없으면 안 내린다
 */
// ios: Views/HoldingsView.swift:1307,1308,1309
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun TownView(m: AppModel, scroll: ScrollState? = null) {
    var bg by rememberPrefInt("town.bg", 0)   // ios: Views/HoldingsView.swift:1310 동네 배경 (TownGround.names)
    val scheme = LocalPal.current.isDark   // ios: Views/HoldingsView.swift:1311
    var arrive by rememberPrefString("town.arrive", "")   // ios: Views/HoldingsView.swift:1312
    var talk by remember { mutableStateOf<TownLook.Resident?>(null) }   // ios: Views/HoldingsView.swift:1313
    var dress by remember { mutableStateOf<TownLook.Resident?>(null) }   // ios: Views/HoldingsView.swift:1314
    var us by remember { mutableStateOf<TownLook.Resident?>(null) }   // ios: Views/HoldingsView.swift:1315
    val tabS = remember { mutableStateOf(0) }; var tab by tabS   // ios: Views/HoldingsView.swift:1316 대사창 아래 칸: 0 기본, 1 우리 사이, 2 꾸미기
    val lineS = remember { mutableStateOf<String?>(null) }; var line by lineS   // ios:  고른 말에 대한 주민 대답 (대사창에)
    val lookS = remember { mutableStateOf<TownBook.Note?>(null) }; var look by lookS   // ios: Views/HoldingsView.swift:1318 꾸미기 미리 보기
    val askS = remember { mutableStateOf(0) }; var ask by askS   // ios: Views/HoldingsView.swift:1319 오늘의 한 줄을 누르면 답장 칸 열기
    var typing by remember { mutableStateOf(false) }   // ios:  내 답을 쓰는 중
    var kbTop by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }   // ios:  키보드 윗변 (창 좌표 px)
    var talkBottom by remember { mutableFloatStateOf(0f) }   // ios: Views/HoldingsView.swift:1322 대사창 제자리 아랫변 (창 좌표 px)
    var fresh by remember { mutableStateOf(setOf<String>()) }   // ios: Views/HoldingsView.swift:1323 오늘 이사 온 주민 (첫 대사가 인사)
    var tick by remember { mutableIntStateOf(0) }   // ios:  2초마다 한 칸
    val walk = remember { mutableStateMapOf<String, Offset>() }   // ios:  주민마다 제자리에서 걸어 나간 만큼 (dp)
    val face = remember { mutableStateMapOf<String, CharDir>() }   // ios:  주민이 보는 쪽
    var moving by remember { mutableStateOf(setOf<String>()) }   // ios:  지금 걷는 중인 주민
    var hop by remember { mutableStateOf<String?>(null) }   // ios:  이번 칸에 폴짝 뛴 주민
    var meet by remember { mutableStateOf<Meet?>(null) }   // ios: Views/HoldingsView.swift:1329 이웃을 찾아가 수다 떠는 중 (한 번에 한 쌍만)
    var duet by remember { mutableStateOf<Duet?>(null) }   // ios: Views/HoldingsView.swift:1484 말풍선 주고받기
    val quiet = remember { HashMap<String, Int>() }   // ios: Views/HoldingsView.swift:1485 방금 이야기한 짝은 30초(15칸) 쉰다
    val pace = remember { HashMap<String, Double>() }   // ios: Views/HoldingsView.swift:1330 이번 걸음에 걸리는 초
    val seats = remember { mutableStateMapOf<String, Offset>() }   // ios: Views/HoldingsView.swift:1331 주민 제자리 (dp)
    var order by remember { mutableStateOf((0 until 13).toList()) }   // ios: Views/HoldingsView.swift:1333 주민 → 자리 번호
    val phase = androidx.lifecycle.compose.LocalLifecycleOwner.current   // ios: Views/HoldingsView.swift:1334
    val book = TownBook.shared   // ios: Views/HoldingsView.swift:1335
    val sky = TownSky.shared
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
    fun walkMe(to: Offset) {
        val k = TownLook.meID; val w = walk[k] ?: Offset.Zero   // ios: Views/HoldingsView.swift:1349
        val d = max(0.3, hypot((to.x - w.x).toDouble(), (to.y - w.y).toDouble()) / 40)   // ios: Views/HoldingsView.swift:1350
        face[k] = CharDir.of(to.x - w.x, to.y - w.y)   // ios: Views/HoldingsView.swift:1351
        pace[k] = d; moving = moving + k; duet = null   // ios: Views/HoldingsView.swift:1505
        walk[k] = to; scope.launch { walkAnimatable(k).animateTo(to, tween((d * 1000).toInt(), easing = LinearEasing)) }   // ios: Views/HoldingsView.swift:1506
        scope.launch {
            delay((d * 1000).toLong())   // ios: Views/HoldingsView.swift:1508
            if (walk[k] != to) return@launch   // ios: Views/HoldingsView.swift:1509
            moving = moving - k; face[k] = CharDir.down   // ios: Views/HoldingsView.swift:1510
            // 도착한 곳 56pt 안에 주민이 있으면 서로 마주 보고 말을 주고받는다
            val st = seats[k] ?: return@launch   // ios: Views/HoldingsView.swift:1512
            val me = Offset(st.x + to.x, st.y + to.y)   // ios: Views/HoldingsView.swift:1504
            val near = seats.entries.mapNotNull { e ->   // ios: Views/HoldingsView.swift:1505
                if (e.key == k || moving.contains(e.key) || TownSay.folk[e.key] == null) null   // ios: Views/HoldingsView.swift:1506
                else e.key to Offset(e.value.x + (walk[e.key]?.x ?: 0f), e.value.y + (walk[e.key]?.y ?: 0f))   // ios: Views/HoldingsView.swift:1516
            }.minByOrNull { hypot(it.second.x - me.x, it.second.y - me.y) }   // ios: Views/HoldingsView.swift:1517
            if (near == null || hypot(near.second.x - me.x, near.second.y - me.y) >= 56) return@launch   // ios: Views/HoldingsView.swift:1518
            val r = TownSay.folk[near.first] ?: return@launch
            val id = near.first; val p = near.second   // ios: Views/HoldingsView.swift:1510
            face[k] = if (p.x < me.x) CharDir.left else CharDir.right; face[id] = if (p.x < me.x) CharDir.right else CharDir.left   // ios: Views/HoldingsView.swift:1511
            duet = Duet(ids = setOf(k, id), lines = TownSay.duet(r).map { (if (it.first) k else id) to it.second }, until = tick + 2)   // ios: Views/HoldingsView.swift:1522
        }
    }

    val rows = m.rows   // ios: Views/HoldingsView.swift:1334
    val all = remember(rows) { TownLook.residents(rows) }   // ios: Views/HoldingsView.swift:1335
    val res = all.take(TownView.maxRes)   // ios: Views/HoldingsView.swift:1363
    // 동네에 서 있는 이들: 주민 + 이장 + (오늘 놀러 온 옛 이웃)
    val mayor = TownLook.Resident(id = TownLook.mayorID, rows = emptyList())   // ios: Views/HoldingsView.swift:1365
    val guest = book.visitor(except = all.map { it.id }.toSet())?.let { TownLook.Resident(id = it, rows = emptyList(), old = book.note(it).co ?: it) }   // ios: Views/HoldingsView.swift:1366
    // 행사: 누군가의 어닝콜 날이면 주민이 그 주민 둘레로 모여 듣는다
    val host = remember(res) { res.firstOrNull { TownLook.earnDay(it) != null } }   // ios: Views/HoldingsView.swift:1368
    val others = res.filter { it.id != host?.id }   // ios: Views/HoldingsView.swift:1369
    val place: List<Pair<TownLook.Resident, Offset>> = host?.let { h ->
        listOf(h to TownView.stage) + others.mapIndexed { i, r ->   // ios: Views/HoldingsView.swift:1371
            val a = i.toDouble() / max(1, others.size).toDouble() * 2 * PI + PI / 2   // ios: Views/HoldingsView.swift:1372
            r to Offset((TownView.stage.x + 0.26 * cos(a)).toFloat(), (TownView.stage.y + 0.26 * sin(a) + 0.04).toFloat())
        }
    } ?: res.mapIndexed { i, r -> r to TownView.spots[order[i]] }
    // 워시는 FOMC 앞뒤 며칠 동안 동네에 머문다
    // 이장·워시·옛 이웃도 주민 다음 빈자리에 선다 (앱에 다시 들어오면 같이 섞임). 모임 날엔 가장자리 제자리
    val fomc = remember { TownLook.fomc() }
    val extra = listOf(mayor to TownView.mayorSpot) + (if (fomc != null) listOf(TownLook.Resident(id = TownLook.warshID, rows = emptyList()) to TownView.warshSpot) else emptyList()) +   // ios: Views/HoldingsView.swift:1378
        (guest?.let { listOf(it to TownView.visitSpot) } ?: emptyList()) +   // ios: Views/HoldingsView.swift:1379
        (if ((m.me ?: MeChar.current) != null) listOf(TownLook.Resident(id = TownLook.meID, rows = emptyList()) to TownView.meSpot) else emptyList())   // ios: Views/HoldingsView.swift:1380
    // 위에서 내려다보니 아래(앞)에 선 주민이 나중에 그려져 앞으로 겹친다
    val folk: List<Pair<TownLook.Resident, Offset>> = (place + extra.mapIndexed { k, e ->   // ios: Views/HoldingsView.swift:1382
        e.first to (if (host == null && res.size + k < TownView.spots.size) TownView.spots[order[res.size + k]] else e.second)   // ios: Views/HoldingsView.swift:1383
    }).sortedBy { it.second.y }   // ios: Views/HoldingsView.swift:1384

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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp)) {   // ios: Views/HoldingsView.swift:1476 말 걸어도 동네 판 크기는 그대로
            val gw = maxWidth.value; val gh = maxHeight.value
            LaunchedEffect(folk.joinToString("") { "${it.first.id}${it.second.x}${it.second.y}" } + "$gw,$gh") {   // ios: Views/HoldingsView.swift:1388
                seats.clear(); for ((r, p) in folk) if (!seats.containsKey(r.id)) seats[r.id] = Offset(p.x * gw, p.y * gh)   // ios: Views/HoldingsView.swift:1389
                TownSay.folk = folk.associate { it.first.id to it.first }   // ios: Views/HoldingsView.swift:1558
                TownNews.fetch()   // ios: Views/HoldingsView.swift:1560 동네 신문 (6시간에 한 번)
            }
            // 다크 모드: 배경만 어둡게 (주민은 그 위)
            Box(Modifier.matchParentSize()
                .clip(RoundedCornerShape(18.dp))   // ios: Views/HoldingsView.swift:1395
                // 배경을 누르면 키보드를 내리고 대사창도 닫는다 (쓰던 답은 '그만'처럼)
                .pointerInput(Unit) {
                    detectTapGestures { px ->
                        val loc = Offset(px.x / density, px.y / density)
                        // 말 거는 중이 아니면 땅을 누른 곳으로 내 캐릭터가 걸어간다 (앞·옆·뒤 네 방향)
                        val st = seats[TownLook.meID]; if (talk == null && st != null) { walkMe(Offset(loc.x - st.x, loc.y - st.y)); return@detectTapGestures }   // ios: Views/HoldingsView.swift:1399
                        focusManager.clearFocus(); keyboard?.hide()   // ios: Views/HoldingsView.swift:1400
                        talk = null; typing = false   // ios: Views/HoldingsView.swift:1401
                    }
                }) {
                if (bg == 0) ground(Modifier.matchParentSize()) else TownGround(kind = bg, season = TownLook.season(), modifier = Modifier.matchParentSize())   // ios: Views/HoldingsView.swift:1391,1392
                if (scheme) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))   // ios: Views/HoldingsView.swift:1393
            }
            folk.forEachIndexed { i, (r, p) ->
                key(i) {
                    // 말풍선은 내 캐릭터가 주민 곁에 갔을 때만: 지금 말할 차례인 쪽만 (나 · 그 주민)
                    val duo: String? = duet?.let { d ->   // ios: Views/HoldingsView.swift:1579
                        val l = d.lines[min(d.i, d.lines.size - 1)]   // ios: Views/HoldingsView.swift:1580
                        if (l.first == r.id) l.second else null   // ios: Views/HoldingsView.swift:1581
                    }
                    val bub = duo   // ios: Views/HoldingsView.swift:1596
                    // 나는 누르면 크게 점프
                    val hopY = animateFloatAsState(if (hop == r.id) (if (r.id == TownLook.meID) -14f else -4f) else 0f,
                        spring(dampingRatio = 0.45f, stiffness = stiffness(0.28f)), label = "hop")   // ios: Views/HoldingsView.swift:1432
                    Box(Modifier
                        .zIndex(if (bub != null) 1f else 0f)   // 말하는 쪽은 앞으로 (말풍선이 다른 주민 뒤에 가리지 않게)
                        .centerAt((p.x * gw).dp, (p.y * gh - 34).dp)
                        .offset { val o = walkAnimatable(r.id).value; IntOffset(o.x.dp.roundToPx(), o.y.dp.roundToPx()) }   // ios: Views/HoldingsView.swift:1433 걷는 보간은 go()의 animateTo 가 맡는다
                        .offset { IntOffset(0, hopY.value.dp.roundToPx()) }) {
                        // 나는 눌러도 대사창 없이 폴짝
                        Box(Modifier
                            .semantics { contentDescription = tr(TownLook.shown(r, book.note(r.id))) }
                            .plainClickable { if (r.id == TownLook.meID) hop = r.id else talk = r }) {   // ios: Views/HoldingsView.swift:1419
                            TownWalker(id = r.id, note = book.note(r.id), dir = face[r.id] ?: CharDir.down, walking = moving.contains(r.id))   // ios: Views/HoldingsView.swift:1410
                            // 기분: 별사탕 받은 날은 하트, 자기 종목이 내리면 시무룩
                            Box(Modifier.align(Alignment.TopEnd)) {   // ios: Views/HoldingsView.swift:1412
                                if (r.id == host?.id) T("🎤", style = appFont(13f))   // ios: Views/HoldingsView.swift:1414
                                else if (book.cheered(r.id)) T("♥", style = appFont(13f, FontWeight.Bold), color = Color(0.95f, 0.35f, 0.45f))   // ios: Views/HoldingsView.swift:1415
                                else if (TownLook.mood(r) < 0) T("…", style = appFont(13f, FontWeight.Bold), color = Color.White)   // ios: Views/HoldingsView.swift:1416
                            }
                        }
                        Box(Modifier.align(Alignment.TopCenter).floatAbove(4.dp)
                            .offset { IntOffset(bubbleShift(bub ?: "", p.x * gw + walkAnimatable(r.id).value.x, gw).dp.roundToPx(), 0) }) {   // ios: Views/HoldingsView.swift:1611 판 밖으로 나가지 않게
                            androidx.compose.animation.AnimatedVisibility(visible = bub != null, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                                val t = bub ?: ""
                                T(t, style = appFont(11f).copy(textAlign = TextAlign.Center), color = Color.Black, maxLines = 3, modifier = Modifier   // ios: Views/HoldingsView.swift:1597
                                    .width(min(180f, t.length * 11f + 4f).dp + 20.dp)   // ios: Views/HoldingsView.swift:1605
                                    .background(Color.White, RoundedCornerShape(10.dp))   // ios: Views/HoldingsView.swift:1608
                                    .border(1.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))   // ios: Views/HoldingsView.swift:1609
                                    .padding(horizontal = 10.dp, vertical = 6.dp))   // ios: Views/HoldingsView.swift:1600 글이 테두리에 붙지 않게 넉넉히
                            }
                        }
                    }
                }
            }
            TownWeather(kind = sky.kind, modifier = Modifier.matchParentSize())   // ios:  누름은 받지 않는다
            // 배경 바꾸기: 누를 때마다 다음 그림
            Row(Modifier.align(Alignment.TopStart).padding(start = 8.dp)
                .heightIn(min = 44.dp).plainClickable { bg = (bg + 1) % TownGround.names.size },   // ios: Views/HoldingsView.swift:1438
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.background(Color.Black.copy(alpha = 0.35f), CapsuleShape)   // ios: Views/HoldingsView.swift:1441
                    .padding(horizontal = 10.dp, vertical = 6.dp),   // ios: Views/HoldingsView.swift:1440
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym("photo", 11f, Color.White)   // ios: Views/HoldingsView.swift:1439
                    T(TownGround.names[min(bg, TownGround.names.size - 1)], style = appFont(11f, FontWeight.Bold), color = Color.White)
                }
            }
            // 행사 띠: 어닝콜 모임 · 세계 기념일 (그날만)
            val t = host?.let { TownLook.earnDay(it) }?.let { "$it 발표 날 · 다 같이 듣는 중" } ?: TownLook.holiday()?.let { "오늘은 " + it }   // ios: Views/HoldingsView.swift:1447
            if (t != null) {
                T(t, style = appFont(12f, FontWeight.Bold), color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)   // ios: Views/HoldingsView.swift:1448,1451,1452
                    .background(Color.Black.copy(alpha = 0.35f), CapsuleShape)   // ios: Views/HoldingsView.swift:1450
                    .padding(horizontal = 10.dp, vertical = 4.dp))   // ios: Views/HoldingsView.swift:1449
            }
            if (res.isEmpty()) {   // ios: Views/HoldingsView.swift:1454
                T("아직 이사 온 주민이 없어요", Modifier.align(Alignment.Center), style = appFont(14f), color = Color.White)   // ios: Views/HoldingsView.swift:1455
            }
            val r = talk
            if (r != null) {   // ios: Views/HoldingsView.swift:1457
                // 답을 쓰는 동안 대사창만 제자리에서 키보드 바로 위까지 올라간다 (동네는 고정)
                val lift = if (typing) min(0f, kbTop - with(dens) { 8.dp.toPx() } - talkBottom) else 0f   // ios: Views/HoldingsView.swift:1459
                val liftA = animateFloatAsState(lift, tween(250, easing = EaseOut), label = "lift")   // ios: Views/HoldingsView.swift:1465
                Column(Modifier.matchParentSize().zIndex(2f)   // ios: Views/HoldingsView.swift:1460 대사창은 말하는 주민(1)보다 앞
                    .onGloballyPositioned { c ->   // ios: Views/HoldingsView.swift:1468,1469
                        talkBottom = c.boundsInWindow().bottom - with(dens) { 10.dp.toPx() }   // ios: Views/HoldingsView.swift:1470,1471
                        rootH = c.findRootCoordinates().size.height.toFloat()
                    }
                    .padding(10.dp)) {   // ios: Views/HoldingsView.swift:1467
                    Spacer(Modifier.weight(1f))   // ios: Views/HoldingsView.swift:1461
                    TownTalk(res = r, note = look ?: book.note(r.id), hello = fresh.contains(r.id), typing = { typing = it }, line = line, tab = tab, ask = ask,   // ios: Views/HoldingsView.swift:1462
                        dress = { tab = if (tab == 2) 1 else 2 },   // ios: Views/HoldingsView.swift:1640
                        modifier = Modifier.offset { IntOffset(0, liftA.value.roundToInt()) }) { talk = null; typing = false }   // ios: Views/HoldingsView.swift:1464
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { TownTotal(m) }   // ios: Views/HoldingsView.swift:1650 점수판: 전체 내 평가액·오늘 등락 (판 밖 오른쪽 아래)
        // 대사창 아래 칸: 우리 사이 / 꾸미기. 이장·워시는 아래 칸 없음 (지난 대화는 우리 사이 → 전체 보기)
        val r = talk
        if (r != null && r.id != TownLook.mayorID && r.id != TownLook.warshID) {   // ios: Views/HoldingsView.swift:1652
            // 우리 사이·꾸미기는 넉넉히 280 (페이지가 스크롤되니 화면 높이에 매이지 않는다)
            TownPanel(res = r, tab = tabS, line = lineS, look = lookS, ask = askS, height = if (tab == 0) TownPanel.height else 280.dp)   // ios: Views/HoldingsView.swift:1480
            Spacer(Modifier.height(4.dp).onGloballyPositioned { anchors["townPanel"] = it })   // ios:  스크롤 맞춤 자리 (칸 아랫변이 탭바에 붙지 않게)
        }
    }
    LaunchedEffect(talk?.id) {   // ios: Views/HoldingsView.swift:1484
        tab = 1; line = null; look = null   // 우리 사이가 기본   // ios: Views/HoldingsView.swift:1666
        if (talk != null) { delay(50); scrollToPanel("townPanel", tween(300, easing = EaseOut)) }   // ios: Views/HoldingsView.swift:1486
    }
    LaunchedEffect(tab) { if (tab != 0) scrollToPanel("townPanel", tween(300, easing = EaseOut)) }
    val imeB = WindowInsets.ime.getBottom(dens)   // ios: Views/HoldingsView.swift:1489 키보드 높이가 바뀔 때마다
    LaunchedEffect(imeB, rootH) { kbTop = if (imeB > 0) rootH - imeB else Float.POSITIVE_INFINITY }   // ios: Views/HoldingsView.swift:1490

    LaunchedEffect(Unit) { sky.refresh() }   // ios: Views/HoldingsView.swift:1493 날씨는 하루 한 번만 받는다
    DisposableEffect(phase) {
        var away = false
        val obs = LifecycleEventObserver { _, e ->   // ios: Views/HoldingsView.swift:1494
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { away = true; resumed = false }
                // 다른 앱에 갔다 돌아오면 주민들이 자리를 바꿔 서 있다
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    if (away) { order = order.shuffled(); walk.clear(); face.clear(); meet = null; scope.launch { walkAnim.values.forEach { it.snapTo(Offset.Zero) } } }   // ios: Views/HoldingsView.swift:1496
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
    LaunchedEffect(active, reduce, resumed) {   // ios: Views/HoldingsView.swift:1498
        if (!active || reduce || !resumed) return@LaunchedEffect
        while (isActive) {   // ios: Views/HoldingsView.swift:1501
            delay(2000)
            tick += 1   // ios: Views/HoldingsView.swift:1503
            hop = null   // ios: Views/HoldingsView.swift:1504
            duet?.let { d ->
                if (tick >= d.until) {   // ios: Views/HoldingsView.swift:1687
                    duet = if (d.i + 1 < d.lines.size) d.copy(i = d.i + 1, until = tick + 2) else null   // ios: Views/HoldingsView.swift:1682,1686
                    if (duet == null) quiet[d.ids.sorted().joinToString("|")] = tick + 15   // ios: Views/HoldingsView.swift:1683
                }
            }
            // 주민끼리 겹치거나 붙어 서 있으면 (36pt 안) 둘이 말을 주고받는다
            if (duet == null && talk == null) {   // ios: Views/HoldingsView.swift:1686
                val here = seats.entries.mapNotNull { e ->   // ios: Views/HoldingsView.swift:1687
                    if (e.key == TownLook.meID || moving.contains(e.key) || TownSay.folk[e.key] == null) null   // ios: Views/HoldingsView.swift:1688
                    else e.key to Offset(e.value.x + (walk[e.key]?.x ?: 0f), e.value.y + (walk[e.key]?.y ?: 0f))   // ios: Views/HoldingsView.swift:1689
                }.sortedBy { it.first }
                run pair@{
                    for (x in here.indices) for (y in here.indices) {   // ios: Views/HoldingsView.swift:1697
                        if (y <= x) continue
                        val a = here[x]; val b = here[y]; val q = listOf(a.first, b.first).sorted().joinToString("|")   // ios: Views/HoldingsView.swift:1692
                        if (hypot(a.second.x - b.second.x, a.second.y - b.second.y) >= 36 || (quiet[q] ?: 0) > tick) continue   // ios: Views/HoldingsView.swift:1693
                        val ra = TownSay.folk[a.first] ?: continue; val rb = TownSay.folk[b.first] ?: continue   // ios: Views/HoldingsView.swift:1694
                        face[a.first] = if (a.second.x < b.second.x) CharDir.right else CharDir.left; face[b.first] = if (a.second.x < b.second.x) CharDir.left else CharDir.right   // ios: Views/HoldingsView.swift:1695
                        duet = Duet(ids = setOf(a.first, b.first), lines = TownSay.pair(ra, rb), until = tick + 2)   // ios: Views/HoldingsView.swift:1696
                        return@pair   // ios: Views/HoldingsView.swift:1697
                    }
                }
            }
            // 걷기: 초당 16pt로 아주 천천히. 도착하면 그 주민만 멈춰 숨쉬기로
            fun go(k: String, to: Offset) {   // ios: Views/HoldingsView.swift:1506
                val w = walk[k] ?: Offset.Zero; val d = max(0.6, hypot((to.x - w.x).toDouble(), (to.y - w.y).toDouble()) / 16)   // ios: Views/HoldingsView.swift:1507
                // 동네에선 뒷모습 없이 옆모습으로만 걷는다 (위아래로 가도 가는 쪽 옆을 본다)
                if (to.x != w.x) face[k] = if (to.x < w.x) CharDir.left else CharDir.right   // ios: Views/HoldingsView.swift:1509
                pace[k] = d; moving = moving + k   // ios: Views/HoldingsView.swift:1510
                walk[k] = to; launch { walkAnimatable(k).animateTo(to, tween((d * 1000).toInt(), easing = LinearEasing)) }   // ios: Views/HoldingsView.swift:1511 걸어가는 동안 위치를 고르게 보간
                launch { delay((d * 1000).toLong()); moving = moving - k }   // ios: Views/HoldingsView.swift:1512
            }
            fun at(k: String): Offset? = seats[k]?.let { Offset(it.x + (walk[k]?.x ?: 0f), it.y + (walk[k]?.y ?: 0f)) }   // ios: Views/HoldingsView.swift:1503 (0f ?? 0)
            // 수다: 도착하면 서로 마주 보고, 3칸 뒤 찾아간 주민이 원래 자리로 돌아간다. 그동안 다른 일은 쉰다
            val mt = meet
            if (mt != null) {   // ios: Views/HoldingsView.swift:1516
                if (moving.contains(mt.a)) continue   // ios: Views/HoldingsView.swift:1517 아직 걷는 중
                if (mt.back) { meet = null; face[mt.a] = CharDir.down }   // ios: Views/HoldingsView.swift:1518
                else if (tick >= mt.until) { go(mt.a, mt.home); meet = meet?.copy(back = true) }
                else if (!mt.met) {
                    face[mt.a] = if (mt.side < 0) CharDir.right else CharDir.left; face[mt.b] = if (mt.side < 0) CharDir.left else CharDir.right; meet = meet?.copy(met = true)   // ios: Views/HoldingsView.swift:1521
                }
                continue   // ios: Views/HoldingsView.swift:1523
            }
            if (tick % 3 != 0) continue   // ios: Views/HoldingsView.swift:1525
            val ids = TownLook.residents(m.rows).take(TownView.maxRes).map { it.id } + listOf(TownLook.mayorID) + (if (TownLook.fomc() != null) listOf(TownLook.warshID) else emptyList())   // ios: Views/HoldingsView.swift:1536
            val id = ids.filter { duet?.ids?.contains(it) != true }.randomOrNull() ?: continue   // ios: Views/HoldingsView.swift:1722 말하는 중인 주민은 그 자리에
            val now = walk[id] ?: Offset.Zero   // ios: Views/HoldingsView.swift:1528
            when (Random.nextInt(0, 10)) {   // ios: Views/HoldingsView.swift:1529
                in 0 until 4 ->   // ios: Views/HoldingsView.swift:1530 둘러보기: 걷지 않고 옆으로만 돌아본다
                    face[id] = if (face[id] == CharDir.left) CharDir.right else CharDir.left   // ios: Views/HoldingsView.swift:1511 옆모습으로만
                in 4 until 7 -> {   // ios: Views/HoldingsView.swift:1532 산책: 멀리 나갔으면 반쯤은 제자리로, 아니면 가까운 곳으로
                    val to = if (now != Offset.Zero && Random.nextBoolean()) Offset.Zero else Offset((listOf(-1, 1).random() * (6..12).random()).toFloat(), (-2..2).random().toFloat())   // ios: Views/HoldingsView.swift:1533,1534 주로 옆으로 걷는다
                    go(id, to)
                }
                in 7 until 9 -> {   // ios: Views/HoldingsView.swift:1536 수다: 120pt 안의 가장 가까운 이웃 옆으로 걸어가 선다
                    val pa = seats[id]; val here = at(id); if (pa == null || here == null) continue   // ios: Views/HoldingsView.swift:1537
                    val dist: (Offset) -> Float = { hypot(it.x - here.x, it.y - here.y) }
                    val others2: List<String> = (ids + listOf(TownLook.meID)).filter { it != id && !moving.contains(it) }   // ios: Views/HoldingsView.swift:1539
                    val cands: List<Pair<String, Offset>> = others2.mapNotNull { k -> at(k)?.let { k to it } }   // ios: Views/HoldingsView.swift:1540
                    val near = cands.minByOrNull { dist(it.second) }; if (near == null || dist(near.second) >= 120) continue   // ios: Views/HoldingsView.swift:1541
                    val (b, pb) = near   // ios: Views/HoldingsView.swift:1542
                    val side: Float = if (here.x < pb.x) -1f else 1f   // ios: Views/HoldingsView.swift:1543
                    val to = Offset(pb.x + side * 30 - pa.x, pb.y + 2 - pa.y)   // ios: Views/HoldingsView.swift:1544
                    meet = Meet(a = id, b = b, home = now, side = side, until = tick + 5)
                    go(id, to)   // ios: Views/HoldingsView.swift:1546
                }
                else -> hop = id   // ios: Views/HoldingsView.swift:1510
            }
        }
    }
    LaunchedEffect(all.joinToString(",") { it.id }) {   // ios: Views/HoldingsView.swift:1551
        // 다 판 주민은 떠난 날을 적어 옛 이웃으로 (시세·보유를 아직 못 읽었으면 건너뜀)
        if (m.rows.isNotEmpty()) book.sync(all)   // ios: Views/HoldingsView.swift:1553
        TownLook.host = res.firstOrNull { TownLook.earnDay(it) != null }?.let { TownLook.Host(it.id, TownLook.shown(it, book.note(it.id))) }   // ios: Views/HoldingsView.swift:1554
        // 처음 본 주민은 오늘을 이사 온 날로 적는다 (수첩이 비어 있던 첫 실행은 인사 없이)
        val first = book.isEmpty   // ios: Views/HoldingsView.swift:1556
        for (x in res) if (book.welcome(x.id) && !first) fresh = fresh + x.id   // ios: Views/HoldingsView.swift:1557
        if (arrive.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1558
            res.firstOrNull { x -> x.rows.any { it.id == arrive } }?.let { x -> fresh = fresh + x.id; talk = x }   // ios: Views/HoldingsView.swift:1559
            arrive = ""   // ios: Views/HoldingsView.swift:1560
        }
    }
    dress?.let { TownDress(res = it) { dress = null } }
    us?.let { TownUs(res = it) { us = null } }   // ios: Views/HoldingsView.swift:1564
}

/** 바닥: 풀밭과 흙길 + 나무 몇 그루 (그림 파일 없이 색만). 계절은 실제 달력으로 풀·잎 색만 바뀐다. 한 번 그려 두고 크기가 같으면 다시 안 그린다 */
@Composable
private fun ground(modifier: Modifier) {
    val se = remember { TownLook.season() }   // ios: Views/HoldingsView.swift:1570
    val grass = listOf(Color(0.55f, 0.78f, 0.43f), Color(0.40f, 0.68f, 0.35f),
        Color(0.66f, 0.67f, 0.37f), Color(0.84f, 0.88f, 0.91f))[se]   // ios: Views/HoldingsView.swift:1572
    val leaf = listOf(Color(0.98f, 0.74f, 0.82f), Color(0.20f, 0.50f, 0.24f),   // ios: Views/HoldingsView.swift:1573
        Color(0.92f, 0.52f, 0.20f), Color(0.97f, 0.98f, 1.0f))[se]   // ios: Views/HoldingsView.swift:1574
    Spacer(modifier.drawWithCache {
        val k = density
        val w = size.width / k; val h = size.height / k
        val road = Path().apply {   // ios: Views/HoldingsView.swift:1577
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.35f, h * 0.40f, w * 0.65f, h * 0.66f,   // ios: Views/HoldingsView.swift:1580
                w, h * 0.42f)   // ios: Views/HoldingsView.swift:1579
        }
        onDrawBehind {
            scale(k, k, pivot = Offset.Zero) {
                drawRect(grass, size = Size(w, h))   // ios: Views/HoldingsView.swift:1576
                drawPath(road, Color(0.84f, 0.74f, 0.53f), style = Stroke(26f))
                for (i in 0 until TownView.trees.size) {   // ios: Views/HoldingsView.swift:1583
                    val t = TownView.trees[i]   // ios: Views/HoldingsView.swift:1584
                    val cx = t.x * w; val cy = t.y * h   // ios: Views/HoldingsView.swift:1590 VStack 가운데가 이 점
                    val top = cy - (30f + 0f + 12f) / 2   // ios: Views/HoldingsView.swift:1585 (spacing 0: 잎 30 + 줄기 12)
                    drawRoundRect(leaf, Offset(cx - 34f / 2, top), Size(34f, 30f), CornerRadius(6f))   // ios: Views/HoldingsView.swift:1586
                    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(cx - 34f / 2, top), Size(34f, 30f), CornerRadius(6f), style = Stroke(2f))   // ios: Views/HoldingsView.swift:1602
                    drawRect(Color(0.45f, 0.30f, 0.18f), Offset(cx - 7f / 2, top + 30f), Size(7f, 12f))   // ios: Views/HoldingsView.swift:1588
                }
            }
        }
    })
}

/** 주민 얼굴: 종목 코드를 씨앗으로 캐릭터 생성 부품을 즉석 조합 (저장 없음, 같은 종목은 늘 같은 얼굴) */
// ios: Views/HoldingsView.swift:1597
object TownLook {
    /** 주민 = 사람. 대표가 같은 종목(테슬라·스페이스X → 머스크)은 한 주민이 같이 맡는다. 대표 정보가 없으면 종목마다 한 명. old: 옛 이웃이면 그때 회사 이름 */
    class Resident(val id: String, val rows: List<AppModel.Row>, val old: String? = null) {   // ios: Views/HoldingsView.swift:1599
        val name: String get() = if (id == mayorID) "트럼프" else if (id == warshID) "연준 의장 워시" else old ?: rows.joinToString(" · ") { it.sym.name }   // ios: Views/HoldingsView.swift:1600
        val value: Double get() = rows.fold(0.0) { a, r -> a + r.value }   // ios: Views/HoldingsView.swift:1601
    }
    // ios: Views/HoldingsView.swift:1603
    fun residents(rows: List<AppModel.Row>): List<Resident> {
        val keys = ArrayList<String>(); val by = HashMap<String, MutableList<AppModel.Row>>()   // ios: Views/HoldingsView.swift:1604
        for (r in rows.sortedByDescending { it.value }) {   // ios: Views/HoldingsView.swift:1605
            val k = Sample.profilesKo[r.id]?.let { "ceo:" + it.ceo.second } ?: r.id
            if (by[k] == null) keys.add(k)
            by.getOrPut(k) { mutableListOf() }.add(r)   // ios: Views/HoldingsView.swift:1608
        }
        return keys.map { Resident(id = it, rows = by[it]!!) }.sortedByDescending { it.value }   // ios: Views/HoldingsView.swift:1610
    }
    /** 기본 별명: 회사 이름 앞 글자 + 꼬리 (같은 주민은 늘 같은 별명) */
    // ios: Views/HoldingsView.swift:1613
    fun nick(res: Resident): String {
        if (res.id == mayorID) return "이장님"
        if (res.id == warshID) return "워시"   // ios: Views/HoldingsView.swift:1615
        if (res.id == meID) return MeChar.current?.let { if (it.name.isEmpty()) "나" else it.name } ?: "나"   // ios: Views/HoldingsView.swift:1633
        val base = (res.rows.firstOrNull()?.sym?.name ?: res.old ?: res.id).replace(" ", "")   // ios: Views/HoldingsView.swift:1617
        val ko = if (base.isEmpty()) false else base.codePointAt(0) in 0xAC00..0xD7A3   // ios: Views/HoldingsView.swift:1618
        val tails = if (ko) listOf("돌이", "순이", "찡", "뽀", "군") else listOf("y", "o", "ie", "ster")   // ios: Views/HoldingsView.swift:1619
        return base.take(if (ko) 2 else 4) + tails[(seed(res.id) % tails.size.toULong()).toInt()]   // ios: Views/HoldingsView.swift:1620
    }
    fun shown(res: Resident, n: TownBook.Note): String = n.name ?: nick(res)
    /** 기기·실행마다 같은 값이 나오는 해시 (FNV-1a 64) */
    // ios: Views/HoldingsView.swift:1624
    fun seed(s: String): ULong {
        var h: ULong = 0xcbf29ce484222325uL   // Views/HoldingsView.swift:1228 (검사 예외: Long 범위를 넘는 16진 리터럴은 u 접미사가 필요)
        for (b in s.encodeToByteArray()) h = (h xor b.toUByte().toULong()) * 0x100000001b3L.toULong()   // ios: Views/HoldingsView.swift:1626
        return h   // ios: Views/HoldingsView.swift:1627
    }
    /** n 이 있으면 꾸미기 '모습'에서 고른 부품을 덮어쓴다 (유명인은 머리를 따로 그리니 머리 모양·색은 빼고) */
    // ios: Views/HoldingsView.swift:1630
    fun char(id: String, n: TownBook.Note? = null): MeChar {
        if (id == meID) MeChar.current?.let { return it }   // ios: Views/HoldingsView.swift:1631
        var x = seed(id) or 1L.toULong()
        // ios: Views/HoldingsView.swift:1633 xorshift
        fun next(n: Int): Int {
            x = x xor (x shl 13); x = x xor (x shr 7); x = x xor (x shl 17)   // ios: Views/HoldingsView.swift:1634
            return (x % max(1, n).toULong()).toInt()
        }
        val p = HashMap<String, Int>(); val c = MeChar(name = "", parts = emptyMap(), type = "seri")
        for (g in listOf(MeGroup.hair, MeGroup.face, MeGroup.eyes, MeGroup.mouth, MeGroup.top, MeGroup.bottom)) p[g.raw] = 1 + next(g.count)   // ios: Views/HoldingsView.swift:1638
        p[MeGroup.color.raw] = 1 + next(MeColor.hair.size)   // ios: Views/HoldingsView.swift:1639
        p["skin"] = 1 + next(MeColor.skin.size)   // ios: Views/HoldingsView.swift:1640
        p[MeGroup.glasses.raw] = if (next(4) == 0) 1 + next(MeGroup.glasses.count) else 0   // ios: Views/HoldingsView.swift:1641
        p[MeGroup.mask.raw] = 0   // ios: Views/HoldingsView.swift:1642
        p[MeGroup.prop.raw] = 0   // ios: Views/HoldingsView.swift:1643
        // 유명인: 머리는 따로 그리니 부품 머리는 빼고, 피부·안경·윗옷만 맞춘다
        val st = TownStar.find(id)
        if (st != null) {
            p[MeGroup.hair.raw] = 0   // ios: Views/HoldingsView.swift:1646
            p["skin"] = st.skin   // ios: Views/HoldingsView.swift:1647
            p[MeGroup.glasses.raw] = st.glasses   // ios: Views/HoldingsView.swift:1648
            if (st.top > 0) p[MeGroup.top.raw] = st.top   // ios: Views/HoldingsView.swift:1649
        }
        val star = TownStar.find(id) != null
        for ((k, v) in n?.parts ?: emptyMap()) if (!(star && (k == "hair" || k == "color"))) p[k] = v   // ios: Views/HoldingsView.swift:1652
        return c.copy(parts = p.toMap())
    }
    /** 하루 한마디: 언어별 문장 틀 + 빈칸({move} 오르내림 · {season} 계절 · {days} 함께한 날). 틀은 주민·날짜 씨앗으로 골라 날마다 바뀐다 */
    val frames: Map<String, List<String>> = mapOf("ko" to listOf(   // ios: Views/HoldingsView.swift:1657
        "{season}이네요. {move}",
        "같이 지낸 지 {days}일째예요. {move}",   // ios: Views/HoldingsView.swift:1659
        "{move}\n오늘 마음은 어때요?",
        "{move}\n내일은 어떨 것 같아요?",   // ios: Views/HoldingsView.swift:1661
        "{season} 바람이 좋네요. {move}",   // ios: Views/HoldingsView.swift:1662
        "벌써 {days}일이나 함께했네요. {move}",
        "안녕하세요! {move}",
        "{move}\n한 줄 남겨 줄래요?",   // ios: Views/HoldingsView.swift:1665
        "왔어요? {move}",   // ios: Views/HoldingsView.swift:1666
        "{move}\n오늘은 어떤 하루였어요?",   // ios: Views/HoldingsView.swift:1667
        "{weather} {move}",
        "{weather}\n{move}",   // ios: Views/HoldingsView.swift:1669
    ))
    /** 이장 = 큰손. 동네에 늘 있고 원·달러 이야기를 한다 (이미 받은 환율만) */
    const val mayorID = "mayor"
    /** 워시: FOMC 5일 전부터 3일 뒤까지 상주 */
    const val warshID = "warsh"   // ios: Views/HoldingsView.swift:1674
    /** 나: 캐릭터 만들기에서 만든 내 캐릭터가 동네에 같이 산다 (만들었을 때만) */
    const val meID = "me"
    val extras: Set<String> = setOf(mayorID, warshID, meID)   // 주민이 아닌 이들 (옛 이웃이 될 수 없다)
    /** FOMC 까지 남은 날(음수면 지난 날), 앞뒤 기간이 아니면 null */
    // ios: Views/HoldingsView.swift:1677
    fun fomc(now: Instant = Instant.now()): Int? {
        val from = Day.str(now.plusSeconds(-3 * 86400L)); val until = Day.str(now.plusSeconds(5 * 86400L))
        val r = MarketEvents.rules.firstOrNull { it.id == "x_fomc" } ?: return null; val d = MarketEvents.dates(r, from = from, until = until).firstOrNull() ?: return null   // ios: Views/HoldingsView.swift:1679
        return Day.dday(d)
    }
    // ios: Views/HoldingsView.swift:1682
    fun warshLine(): String {
        val n = fomc() ?: 0   // ios: Views/HoldingsView.swift:1683
        if (n > 1) return "FOMC까지 ${n}일 남았어요. 금리 얘기하러 미리 왔어요."   // ios: Views/HoldingsView.swift:1684
        if (n == 1) return "내일이 FOMC예요. 다들 긴장되죠?"   // ios: Views/HoldingsView.swift:1685
        if (n == 0) return "오늘 FOMC예요. 결과가 나오면 알려 줄게요."   // ios: Views/HoldingsView.swift:1686
        return "FOMC 끝났어요. 며칠 더 머물다 갈게요."   // ios: Views/HoldingsView.swift:1687
    }
    /** 옛 이웃: 다 팔아 떠난 주민이 가끔 놀러 와 함께한 시간을 얘기한다 */
    val oldFrames = listOf("오랜만이에요! 놀러 왔어요.\n{days}일 함께했던 거 기억나요?", "지나가다 들렀어요. 다들 잘 지내죠?", "{weather} 옛날 생각나서 왔어요.",
        "여기 그대로네요. 반가워요!", "함께한 {days}일, 가끔 생각나요.")   // ios: Views/HoldingsView.swift:1692
    /** 계절: 0 봄 · 1 여름 · 2 가을 · 3 겨울 (실제 달력) */
    fun season(d: LocalDate = LocalDate.now()): Int {
        val mo = d.monthValue
        return if (mo in 3..5) 0 else if (mo in 6..8) 1 else if (mo in 9..11) 2 else 3
    }
    // ios: Views/HoldingsView.swift:1708
    fun fxLine(): String {
        val q = Market.shared.fx
        if (!(q.last > 0)) return "오늘 환율은 아직 못 봤어요."   // ios: Views/HoldingsView.swift:1710
        val w = cformat("%.0f", q.last); val d = q.last - q.prevClose   // ios: Views/HoldingsView.swift:1711
        if (!q.live || abs(d) < 0.5) return "원·달러는 ${w}원이에요."   // ios: Views/HoldingsView.swift:1712
        return cformat("원·달러는 ${w}원, 어제보다 %.0f원 ", abs(d)) + (if (d > 0) "올랐어요. 달러가 힘이 세졌네요." else "내렸어요. 원화가 힘을 냈네요.")   // ios: Views/HoldingsView.swift:1713
    }
    // ios: Views/HoldingsView.swift:1715
    fun daily(res: Resident, n: TownBook.Note, lang: String = "ko"): String {
        val wx = TownSky.shared.phrase
        // ios: Views/HoldingsView.swift:1717
        fun pick(a: List<String>): String {
            val pool = a.filter { wx != null || !it.contains("{weather}") }   // ios: Views/HoldingsView.swift:1718
            return pool[(seed(res.id + TownLog.day) % pool.size.toULong()).toInt()].replace("{weather}", wx ?: "")   // ios: Views/HoldingsView.swift:1719
        }
        val h = holiday()?.let { "오늘은 " + it + (if (batchim(it)) "이에요!\n" else "예요!\n") } ?: ""   // ios: Views/HoldingsView.swift:1721
        if (res.id == warshID) return h + warshLine() + "\n" + fxLine()   // ios: Views/HoldingsView.swift:1722
        if (res.id == mayorID) return h + TownSay.mayorOpen()   // ios: Views/HoldingsView.swift:1899 이장 첫마디는 동네 소식. 환율은 누르다 보면 가끔
        // 행사: 어닝콜 날은 모두 모여 그 주민의 한 줄을 듣는다. 전날엔 예측 한 줄을 부탁한다
        if (res.old == null && earnDay(res) != null) {
            val y = Day.str(Instant.now().plusSeconds(-86400))   // ios: Views/HoldingsView.swift:1726
            val mine = TownLog.shared.log(res.id).lastOrNull { it.day == y }?.me   // ios: Views/HoldingsView.swift:1727
            return "오늘은 제 실적 발표 날이에요! 다들 들으러 모였네요.\n" + (mine?.let { "어제 남긴 예측: “${it}”\n" } ?: "") + line(res)   // ios: Views/HoldingsView.swift:1728
        }
        if (res.old == null && earnDay(res, 1) != null) return "내일은 제 실적 발표 날이에요.\n어떨 것 같아요? 예측 한 줄 남겨 줄래요?"   // ios: Views/HoldingsView.swift:1730
        val ho = host; if (res.old == null && ho != null && ho.id != res.id) return h + "오늘은 ${ho.name} 실적 날이라 다 같이 듣고 있어요.\n" + line(res)   // ios: Views/HoldingsView.swift:1731
        // 몇 달 전 내 답이 있으면 그날은 그걸 꺼내 얘기한다
        TownLog.shared.recall(res.id, price(res))?.let { back -> return if (res.old != null) back else back + "\n" + line(res) }   // ios: Views/HoldingsView.swift:1733
        if (res.old != null) {   // ios: Views/HoldingsView.swift:1734
            val together = if (n.moved.isEmpty()) 0 else max(1, TownLog.age(n.moved) - TownLog.age(n.left ?: TownLog.day) + 1)   // ios: Views/HoldingsView.swift:1735
            return pick(oldFrames.filter { together > 1 || !it.contains("{days}") }).replace("{days}", "$together")   // ios: Views/HoldingsView.swift:1736
        }
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1738
        val days = runCatching { LocalDate.parse(n.moved, f) }.getOrNull()?.let { (runCatching { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }.getOrNull() ?: 0) + 1 } ?: 0   // ios: Views/HoldingsView.swift:1739
        val mo = LocalDate.now().monthValue   // ios: Views/HoldingsView.swift:1740
        val season = if (mo in 3..5) "봄" else if (mo in 6..8) "여름" else if (mo in 9..11) "가을" else "겨울"   // ios: Views/HoldingsView.swift:1741
        val all = frames[lang] ?: frames["ko"]!!   // ios: Views/HoldingsView.swift:1742
        // 계절은 석 달 내내 같아서 매일 말하면 지겹다. 계절이 바뀐 첫 주에만 꺼낸다
        val fresh = listOf(3, 6, 9, 12).contains(mo) && LocalDate.now().dayOfMonth <= 7   // ios: Views/HoldingsView.swift:1744
        val t = pick(all.filter { (days > 1 || !it.contains("{days}")) && (fresh || !it.contains("{season}")) })   // ios: Views/HoldingsView.swift:1745
        return h + t.replace("{move}", line(res))   // ios: Views/HoldingsView.swift:1746
            .replace("{season}", season)   // ios: Views/HoldingsView.swift:1747
            .replace("{days}", "$days")   // ios: Views/HoldingsView.swift:1748
    }
    /** 행사: 보유 종목 실적 날 (앱 일정표 MarketEvents 의 실적만, 바깥에서 따로 안 받음). ahead 0 오늘 · 1 내일 */
    fun earnDay(res: Resident, ahead: Int = 0): String? {
        if (res.id == mayorID || res.id == warshID || res.old != null) return null   // ios: Views/HoldingsView.swift:1752
        val d = Day.str(Instant.now().plusSeconds(ahead.toLong() * 86400))
        return MarketEvents.live.firstOrNull { r -> r.id.startsWith("e_") && res.rows.any { it.id == r.target } && MarketEvents.dates(r, from = d, until = d).isNotEmpty() }?.kind   // ios: Views/HoldingsView.swift:1754
    }
    /** 오늘 모임의 주인공 (동네가 정해 둔다) */
    data class Host(val id: String, val name: String)
    var host: Host? = null   // ios: Views/HoldingsView.swift:1757
    /** 세계 기념일 (달력 고정 날만, 앱 안 표) */
    val holidays: Map<String, String> = mapOf(   // ios: Views/HoldingsView.swift:1759
        "01-01" to "새해 첫날", "02-14" to "밸런타인데이", "03-08" to "세계 여성의 날", "03-20" to "국제 행복의 날",   // ios: Views/HoldingsView.swift:1760
        "04-22" to "지구의 날", "05-01" to "노동절", "06-05" to "세계 환경의 날", "09-21" to "세계 평화의 날",   // ios: Views/HoldingsView.swift:1761
        "10-24" to "유엔의 날", "10-31" to "핼러윈", "12-25" to "크리스마스", "12-31" to "한 해 마지막 날",
    )
    // ios: Views/HoldingsView.swift:1764
    fun holiday(d: LocalDate = LocalDate.now()): String? {
        val f = DateTimeFormatter.ofPattern("MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1765
        return holidays[d.format(f)]
    }
    fun batchim(s: String): Boolean {
        if (s.isNotEmpty() && s.last() in '0'..'9') return s.last() !in "2459"   // ios: Views/HoldingsView.swift:1928 숫자는 읽는 소리로
        if (s.isEmpty()) return false; val c = s.codePointBefore(s.length); if (c !in 0xAC00..0xD7A3) return false   // ios: Views/HoldingsView.swift:1769
        return (c - 0xAC00) % 28 != 0   // ios: Views/HoldingsView.swift:1770
    }
    /** 주민 대사: 맡은 종목이 여럿이면 종목 이름을 붙여 한 줄씩 */
    // ios: Views/HoldingsView.swift:1774
    fun line(res: Resident): String =
        if (res.rows.size == 1) line(res.rows[0]) else res.rows.joinToString("\n") { it.sym.name + ", " + line(it) }   // ios: Views/HoldingsView.swift:1775
    /** 오르내림 한 줄. 오늘 시세가 있으면 오늘, 없으면 산 가격 대비 */
    // ios: Views/HoldingsView.swift:1777
    fun line(r: AppModel.Row): String {
        val live = r.sym.quote.live   // ios: Views/HoldingsView.swift:1778
        val v = move(r)   // ios: Views/HoldingsView.swift:1779
        val pct = cformat("%.1f%%", abs(v) * 100)   // ios: Views/HoldingsView.swift:1780
        val wh = if (live) "오늘" else "산 뒤로"   // ios: Views/HoldingsView.swift:1781
        if (abs(v) < 0.001) return if (live) "오늘은 그대로예요. 조용한 하루네요." else "산 뒤로 그대로예요."
        return if (v > 0) "$wh $pct 올랐어요!" else "$wh $pct 내렸어요…"   // ios: Views/HoldingsView.swift:1783
    }
    // ios: Views/HoldingsView.swift:1785
    fun move(r: AppModel.Row): Double =
        if (r.sym.quote.live) r.sym.quote.change else (if (r.cost > 0) r.value / r.cost - 1 else 0.0)   // ios: Views/HoldingsView.swift:1786
    /** 기분: 맡은 종목 오르내림을 평가액으로 묶은 값. 0 아래면 시무룩 */
    // ios: Views/HoldingsView.swift:1789
    fun mood(res: Resident): Double {
        val v = res.value   // ios: Views/HoldingsView.swift:1791
        return if (v > 0) res.rows.fold(0.0) { a, r -> a + move(r) * r.value } / v else 0.0   // ios: Views/HoldingsView.swift:1791
    }
    /** 예측을 매길 값: 대표 종목의 지금 가격 */
    fun price(res: Resident): Double? {
        val p = res.rows.firstOrNull()?.sym?.quote?.last; if (p == null || !(p > 0)) return null
        return p
    }
    /** 내 답에서 방향 읽기: 오른다 +1, 내린다 -1, 모르겠으면 0 */
    // ios: Views/HoldingsView.swift:1799
    fun guess(s: String): Int {
        val up = listOf("오를", "오르", "올라", "오른", "상승", "반등", "떡상", "↑")   // ios: Views/HoldingsView.swift:1800
        val dn = listOf("내릴", "내려", "내린", "떨어", "하락", "빠질", "빠져", "떡락", "↓")   // ios: Views/HoldingsView.swift:1801
        val u = up.any { s.contains(it) }; val d = dn.any { s.contains(it) }   // ios: Views/HoldingsView.swift:1802
        return if (u == d) 0 else (if (u) 1 else -1)
    }
}

/**
 * 고전 RPG 대사창: 얼굴 · 이름 · 한 글자씩 찍히는 하루 한마디 · 내 답 한 줄.
 * 대사를 누르면 다 찍기 → 다 나왔으면 답장 칸. typing: 내 답을 쓰는 중인지 (@Binding 자리: 바뀔 때 알린다)
 */
@Composable
fun TownTalk(
    res: TownLook.Resident,   // ios: Views/HoldingsView.swift:1840
    note: TownBook.Note,   // ios: Views/HoldingsView.swift:1811
    hello: Boolean = false,   // ios: Views/HoldingsView.swift:1812
    typing: (Boolean) -> Unit,
    line: String? = null,   // ios: Views/HoldingsView.swift:1814 꾸미기에서 온 대답 (있으면 오늘 한마디 대신)
    tab: Int = 0,   // ios: Views/HoldingsView.swift:1815
    ask: Int = 0,   // ios: Views/HoldingsView.swift:1816
    dress: () -> Unit,   // ios: Views/HoldingsView.swift:1817
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") close: () -> Unit,   // ios: Views/HoldingsView.swift:1819
) {
    var shown by remember { mutableIntStateOf(0) }
    var me by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf(false) }   // ios:  대사 박스를 누르면 답장 칸이 열린다
    var focus by remember { mutableStateOf(false) }
    var say by remember { mutableStateOf("") }   // ios: Views/HoldingsView.swift:1824
    var more by remember(res.id, line) { mutableStateOf<String?>(null) }   // ios: Views/HoldingsView.swift:1994,2059,2060 대사창을 누를 때마다 다음 한마디 (TownSay)
    val log = TownLog.shared
    fun fullOf(s: String): String = more ?: line ?: ((if (hello) (if (TownLook.batchim(TownLook.shown(res, note))) "${TownLook.shown(res, note)}이에요. 오늘 이사 왔어요! 이름이랑 모자 골라 줄래요?\n" else "${TownLook.shown(res, note)}예요. 오늘 이사 왔어요! 이름이랑 모자 골라 줄래요?\n") else "") + s)   // ios: Views/HoldingsView.swift:1826
    val gold = Color(1f, 0.85f, 0.4f)   // ios: Views/HoldingsView.swift:1827
    val req = remember { FocusRequester() }
    var want by remember { mutableIntStateOf(0) }   // focus = true 자리: 답장 칸이 생긴 뒤 커서를 넣는다
    val fm = LocalFocusManager.current
    val full = fullOf(say)   // ios: Views/HoldingsView.swift:1828
    /** 오늘 첫 답이면 고맙다는 말로 */
    fun send() {   // ios: Views/HoldingsView.swift:2077
        val first = !(log.log(res.id).lastOrNull()?.let { it.day == TownLog.day && it.me != null } ?: false)   // ios: Views/HoldingsView.swift:2078
        log.answer(res.id, me, price = TownLook.price(res), say = more ?: line)   // ios: Views/HoldingsView.swift:2100
        if (first && me.isNotEmpty()) more = TownSay.praise(res)   // ios: Views/HoldingsView.swift:2080
    }
    // 스크린샷처럼: 왼쪽에 큰 초상화가 박스 위로 솟고, 이름표는 초상화 아래, 대사는 오른쪽
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {   // ios: Views/HoldingsView.swift:1831
        Column(Modifier.fillMaxWidth()
            .background(Color(0.10f, 0.11f, 0.20f), RoundedCornerShape(8.dp))   // ios: Views/HoldingsView.swift:1860
            .border(2.dp, Color.White, RoundedCornerShape(8.dp))   // ios: Views/HoldingsView.swift:1861
            .clickable(remember { MutableInteractionSource() }, indication = null) {   // ios: Views/HoldingsView.swift:1862
                // 글자가 다 안 나왔으면 다 보여 주고, 다 나왔으면 다음 한마디 (겹치지 않게 갈래를 돌린다)
                if (shown < full.length) shown = full.length   // ios: Views/HoldingsView.swift:1865
                else more = TownSay.next(res, note)   // ios: Views/HoldingsView.swift:2039
            }
            .padding(start = 128.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),   // ios: Views/HoldingsView.swift:1859
            verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/HoldingsView.swift:1832
            T(full.take(shown), Modifier.fillMaxWidth().heightIn(min = 58.dp), style = appFont(15f), color = Color.White)   // ios: Views/HoldingsView.swift:1833,1834
            if (reply && say.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1835
                Row(Modifier.fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),   // ios: Views/HoldingsView.swift:1847
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(me, { me = it }, Modifier.weight(1f).focusRequester(req)
                        .onFocusChanged { s ->   // ios: Views/HoldingsView.swift:1880
                            if (s.isFocused != focus) {
                                focus = s.isFocused; typing(focus)
                                // 남기지 않고 닫으면 쓰던 글은 버리고 남겨 둔 답으로
                                if (!focus) me = log.log(res.id).lastOrNull()?.let { if (it.day == TownLog.day) it.me else null } ?: ""
                            }
                        },
                        textStyle = appFont(14f).copy(color = Color.White), singleLine = true, cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { send() }),   // ios: Views/HoldingsView.swift:1840
                        decorationBox = { inner ->   // ios: Views/HoldingsView.swift:1837
                            Box { if (me.isEmpty()) T("한 줄 답 (예측·마음)", style = appFont(14f), color = Color.White.copy(alpha = 0.4f), maxLines = 1); inner() }
                        })
                    if (focus) {   // ios: Views/HoldingsView.swift:1841
                        T("그만", Modifier.plainClickable { fm.clearFocus() }, style = appFont(13f), color = Color.White.copy(alpha = 0.7f))   // ios: Views/HoldingsView.swift:1842
                        T("남기기", Modifier.plainClickable { send(); fm.clearFocus() },   // ios: Views/HoldingsView.swift:1843
                            style = appFont(13f, FontWeight.Bold), color = gold)   // ios: Views/HoldingsView.swift:1844
                    }
                }
            }
            // 대답하기·꾸미기: 대사 박스 안 오른쪽 아래에 붙인다 (우리 사이는 아래 칸에 늘 보인다)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.weight(1f).widthIn(min = 0.dp))
                if (say.isNotEmpty()) chip("대답하기", reply, gold) { reply = true; want += 1 }   // ios: Views/HoldingsView.swift:2028
                if (res.id != TownLook.mayorID && res.id != TownLook.warshID) chip("꾸미기", tab == 2, gold, dress)   // ios: Views/HoldingsView.swift:2029
            }
        }
        // 큰 초상화: 박스 왼쪽에서 위로 솟는다 (누르는 건 박스가 받는다)
        Column(Modifier.padding(start = 6.dp, bottom = 6.dp),   // ios: Views/HoldingsView.swift:1878
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-10).dp)) {   // ios: Views/HoldingsView.swift:1869
            TownFace(id = res.id, note = note, width = 110.dp, height = 155.dp)   // ios: Views/HoldingsView.swift:1870
            T(TownLook.shown(res, note), style = appFont(13f, FontWeight.Bold), color = gold, maxLines = 1, modifier = Modifier
                .widthIn(max = 116.dp)   // ios: Views/HoldingsView.swift:1873
                .background(Color(0.10f, 0.11f, 0.20f), RoundedCornerShape(6.dp))   // ios: Views/HoldingsView.swift:1874
                .border(2.dp, Color.White, RoundedCornerShape(6.dp))   // ios: Views/HoldingsView.swift:1875
                .padding(horizontal = 10.dp, vertical = 4.dp))   // ios: Views/HoldingsView.swift:1872
        }
    }
    LaunchedEffect(want) {   // 답장 칸이 그려진 다음 커서를 넣는다 (focus = true)
        if (want > 0) { withFrameNanos { }; runCatching { req.requestFocus() } }
    }
    val lastAsk = remember { intArrayOf(ask) }
    LaunchedEffect(ask) { if (ask != lastAsk[0]) { lastAsk[0] = ask; if (say.isNotEmpty()) { reply = true; want += 1 } } }   // ios: Views/HoldingsView.swift:1885
    LaunchedEffect(res.id + "|" + (line ?: "") + "|" + (more ?: "")) {   // ios: Views/HoldingsView.swift:2061
        // 오늘 처음 말을 걸 때 한마디를 정해 기록에 남긴다. 그 뒤로 다시 열면 열 때마다 새 한마디
        val had = log.log(res.id).lastOrNull()?.day == TownLog.day   // ios: Views/HoldingsView.swift:2063
        val e = log.today(res.id) { TownLook.daily(res, note) }   // ios: Views/HoldingsView.swift:2087
        if (had && !hello && line == null && more == null) { more = TownSay.next(res, note); return@LaunchedEffect }   // ios: Views/HoldingsView.swift:2083
        say = e.say   // ios: Views/HoldingsView.swift:2043
        if (!focus) { me = e.me ?: ""; reply = me.isNotEmpty() }   // ios: Views/HoldingsView.swift:2060 오늘 남긴 답이 있으면 칸을 열어 둔다 (쓰는 중이면 그대로)
        shown = 0   // ios: Views/HoldingsView.swift:1891
        val n = fullOf(e.say).length   // ios: Views/HoldingsView.swift:1892
        while (shown < n) {   // ios: Views/HoldingsView.swift:1893
            delay(45)   // ios: Views/HoldingsView.swift:1894
            if (!isActive) return@LaunchedEffect   // ios: Views/HoldingsView.swift:1895
            shown += 1   // ios: Views/HoldingsView.swift:1896
        }
    }
}

// ios: Views/HoldingsView.swift:1901
@Composable
private fun chip(t: String, on: Boolean, gold: Color, a: () -> Unit) {
    T(t, style = appFont(12f, FontWeight.Bold), color = if (on) Color(0.10f, 0.11f, 0.20f) else Color.White, modifier = Modifier.plainClickable(a)
        .background(if (on) gold else Color.Transparent, RoundedCornerShape(4.dp))   // ios: Views/HoldingsView.swift:1904
        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))   // ios: Views/HoldingsView.swift:1905
        .padding(horizontal = 8.dp, vertical = 4.dp))   // ios: Views/HoldingsView.swift:1903
}

/** 지난 대화: 주민 말·내 답을 목록으로 (최근 것이 위). 내가 답한 것만 */
@Composable
fun TownPast(id: String, modifier: Modifier = Modifier, tall: Boolean = false) {   // ios: Views/HoldingsView.swift:1910,1911 tall: 전체 보기 시트 (남는 높이까지)
    val log = TownLog.shared   // ios: Views/HoldingsView.swift:1912
    val past = log.log(id).filter { it.me != null }.reversed()   // ios: Views/HoldingsView.swift:1913,1914
    if (past.isNotEmpty()) {   // ios: Views/HoldingsView.swift:1915
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("지난 대화", style = appFont(13f, FontWeight.Bold), color = Theme.sub)
            Column(Modifier.fillMaxWidth().heightIn(max = if (tall) 560.dp else 260.dp)   // ios: Views/HoldingsView.swift:2123
                .background(Theme.card, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())   // ios: Views/HoldingsView.swift:1918
                .padding(12.dp),   // ios: Views/HoldingsView.swift:1939
                verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/HoldingsView.swift:1919
                for (e in past) {
                    // 메신저 풍선: 날짜 · 주민 말(왼쪽) · 내 답(오른쪽)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/HoldingsView.swift:1922
                        T(e.day.drop(5).replace("-", "."), style = appFont(11f), color = Theme.sub2)   // ios: Views/HoldingsView.swift:1923
                        Box(Modifier.fillMaxWidth().padding(end = 40.dp), contentAlignment = Alignment.CenterStart) {   // ios: Views/HoldingsView.swift:1927,1928
                            T(e.say, Modifier.background(Theme.sub2.copy(alpha = 0.15f), RoundedCornerShape(14.dp))   // ios: Views/HoldingsView.swift:1924,1926
                                .padding(horizontal = 10.dp, vertical = 7.dp), style = appFont(14f))
                        }
                        val a = e.me
                        if (a != null) {   // ios: Views/HoldingsView.swift:1929
                            Box(Modifier.fillMaxWidth().padding(start = 40.dp), contentAlignment = Alignment.CenterEnd) {   // ios: Views/HoldingsView.swift:1933,1934
                                T(a, Modifier.background(Theme.teal, RoundedCornerShape(14.dp))   // ios: Views/HoldingsView.swift:1930
                                    .padding(horizontal = 10.dp, vertical = 7.dp), style = appFont(14f), color = Color.White)   // ios: Views/HoldingsView.swift:1931
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 주민 수첩: 별명 · 모자 · 모자 색 · 이사 온 날. 주민마다 몇 글자라 앱 설정 한 칸에 JSON(Base64, 백업과 같은 꼴). 떠나도 지우지 않는다: 옛 이웃 */
// ios: Views/HoldingsView.swift:1949
class TownBook private constructor() {
    companion object {
        val shared: TownBook by lazy { TownBook() }
        private const val key = "town.book"   // ios: Views/HoldingsView.swift:1956
        private fun load(): Map<String, Note> = Defaults.prefs.getString(key, null)?.let {   // ios: Views/HoldingsView.swift:1958
            runCatching { NJson.decodeFromString<Map<String, Note>>(Base64.getDecoder().decode(it).decodeToString()) }.getOrNull() } ?: emptyMap()
    }
    // co: 회사 이름(떠나도 부르려고), left: 다 팔고 떠난 날, parts: 꾸미기 '모습'에서 고른 부품, candyDay: 이 주민에게 답을 남겨 별사탕을 받은 날
    @Serializable
    data class Note(var name: String? = null, var hat: Int = 0, var tint: Int = 0, var moved: String = "", var gifts: Int? = null, var cheer: String? = null,   // ios: Views/HoldingsView.swift:1951
        var co: String? = null, var left: String? = null, var prop: Int? = null,   // ios: Views/HoldingsView.swift:1952
        var parts: Map<String, Int>? = null,   // ios: Views/HoldingsView.swift:1953
        var candyDay: String? = null)   // ios: Views/HoldingsView.swift:1954
    var notes: Map<String, Note> by mutableStateOf(load())   // ios: Views/HoldingsView.swift:1957
        private set
    /** 별사탕: 주민에게 답을 남길 때마다 하나 (주민마다 하루 하나). 시무룩한 주민에게 주면 그날 기운 차림 */
    var candy: Int by mutableIntStateOf(Defaults.prefs.getInt("town.candy", 0))   // ios: Views/HoldingsView.swift:1979
        private set
    /** 백업에서 불러온 뒤 다시 읽는다 */
    fun reload() {
        notes = load()   // ios: Views/HoldingsView.swift:1962
        candy = Defaults.prefs.getInt("town.candy", 0)   // ios: Views/HoldingsView.swift:1963
    }
    val isEmpty: Boolean get() = notes.isEmpty()   // ios: Views/HoldingsView.swift:1965
    fun note(id: String): Note = notes[id]?.copy() ?: Note()   // ios: Views/HoldingsView.swift:1966
    fun set(id: String, n: Note) {
        notes = notes + (id to n.copy())
        runCatching { Defaults.prefs.edit().putString(key, Base64.getEncoder().encodeToString(NJson.encodeToString(notes).encodeToByteArray())).apply() }   // ios: Views/HoldingsView.swift:1969
    }
    /** 처음 본 주민이면 오늘을 이사 온 날로 적는다. 새로 왔으면 true */
    // ios: Views/HoldingsView.swift:1972
    fun welcome(id: String): Boolean {
        if (notes[id] != null) return false   // ios: Views/HoldingsView.swift:1975
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:1956
        set(id, Note(moved = LocalDate.now().format(f)))   // ios: Views/HoldingsView.swift:1975
        return true
    }
    // ios: Views/HoldingsView.swift:1980
    fun earn(id: String) {
        val d = TownLog.day
        if (notes[id]?.candyDay == d) return   // ios: Views/HoldingsView.swift:1982
        val n = note(id); n.candyDay = d; set(id, n)   // ios: Views/HoldingsView.swift:1983
        candy += 1; Defaults.prefs.edit().putInt("town.candy", candy).apply()   // ios: Views/HoldingsView.swift:1984
    }
    // ios: Views/HoldingsView.swift:1986
    fun give(id: String) {
        if (!(candy > 0)) return   // ios: Views/HoldingsView.swift:1987
        candy -= 1; Defaults.prefs.edit().putInt("town.candy", candy).apply()   // ios: Views/HoldingsView.swift:1988
        val n = note(id); n.gifts = (n.gifts ?: 0) + 1; n.cheer = TownLog.day   // ios: Views/HoldingsView.swift:1989
        set(id, n)   // ios: Views/HoldingsView.swift:1975
    }
    /** 꾸미기에서 별사탕으로 사기. 모자라면 false */
    // ios: Views/HoldingsView.swift:1993
    fun spend(n: Int): Boolean {
        if (!(n > 0 && candy >= n)) return n <= 0   // ios: Views/HoldingsView.swift:1994
        candy -= n; Defaults.prefs.edit().putInt("town.candy", candy).apply()
        return true   // ios: Views/HoldingsView.swift:1996
    }
    fun cheered(id: String): Boolean = notes[id]?.cheer == TownLog.day   // ios: Views/HoldingsView.swift:1998
    /** 지금 주민과 수첩 맞추기: 있는 주민은 회사 이름을 적고 떠난 표시를 지운다, 없어진 주민은 오늘을 떠난 날로 */
    // ios: Views/HoldingsView.swift:2000
    fun sync(res: List<TownLook.Resident>) {
        val now = res.map { it.id }.toSet()   // ios: Views/HoldingsView.swift:2001
        for (r in res) {   // ios: Views/HoldingsView.swift:2002
            val n = notes[r.id]?.copy() ?: continue
            if (n.co != r.name || n.left != null) { n.co = r.name; n.left = null; set(r.id, n) }   // ios: Views/HoldingsView.swift:2004
        }
        // 이장·워시·나는 주민이 아니라 떠날 수 없다 (예전에 떠난 걸로 적혔으면 지운다: 옛 이웃으로 또 와서 둘이 서던 결함)
        for ((id, x) in notes) if (TownLook.extras.contains(id) && x.left != null) { val n = x.copy(); n.left = null; set(id, n) }
        for ((id, x) in notes) if (!now.contains(id) && x.left == null && !TownLook.extras.contains(id)) {
            val n = x.copy(); n.left = TownLog.day; set(id, n)
        }
    }
    /** 오늘 놀러 온 옛 이웃 (사흘에 한 번쯤, 날마다 한 명). 지금 주민은 빼고 */
    fun visitor(except: Set<String>): String? {
        val olds = notes.filter { it.value.left != null && !except.contains(it.key) && !TownLook.extras.contains(it.key) }.keys.sorted()
        val s = TownLook.seed("visit" + TownLog.day)   // ios: Views/HoldingsView.swift:2013
        if (olds.isEmpty() || s % 3.toULong() != 0.toULong()) return null   // ios: Views/HoldingsView.swift:2014
        return olds[((s / 3.toULong()) % olds.size.toULong()).toInt()]   // ios: Views/HoldingsView.swift:2015
    }
}

/**
 * 동네 날씨: 내 지역 실제 날씨를 하루 한 번만 받는다 (Open-Meteo, 키 없음).
 * 위치는 대략(ACCESS_COARSE_LOCATION, 기기 LocationManager 의 네트워크 위치)만 쓰고 소수 한 자리로 줄인다. 허락하지 않았거나 못 찾으면 서울.
 */
// ios: Views/HoldingsView.swift:2020
class TownSky private constructor() {
    companion object { val shared: TownSky by lazy { TownSky() } }   // ios: Views/HoldingsView.swift:2021
    enum class Kind { clear, cloud, fog, rain, snow, storm }   // ios: Views/HoldingsView.swift:2022
    var kind: Kind? by mutableStateOf(null)
        private set
    private val lm: LocationManager? get() = runCatching { Defaults.ctx.getSystemService(LocationManager::class.java) }.getOrNull()
    private var busy = false
    private val main = MainScope()
    /** 위치 허락 창 띄우기 (권한 창은 화면에서만 띄울 수 있어 동네 화면이 넣어 준다) */
    var ask: (() -> Unit)? = null

    init {
        val d = Defaults.prefs   // ios: Views/HoldingsView.swift:2028
        if (d.getString("town.skyDay", null) == TownLog.day) kind = Kind.entries.getOrNull(d.getInt("town.sky", 0))   // ios: Views/HoldingsView.swift:2029
    }
    // ios: Views/HoldingsView.swift:2031,2032
    val phrase: String? get() = when (kind) {
        Kind.clear -> "오늘은 맑네요."   // ios: Views/HoldingsView.swift:2033
        Kind.cloud -> "구름이 꼈네요."   // ios: Views/HoldingsView.swift:2034
        Kind.fog -> "안개가 자욱해요."   // ios: Views/HoldingsView.swift:2035
        Kind.rain -> "비가 오네요."   // ios: Views/HoldingsView.swift:2036
        Kind.snow -> "눈이 와요!"   // ios: Views/HoldingsView.swift:2037
        Kind.storm -> "천둥이 쳐요!"   // ios: Views/HoldingsView.swift:2038
        null -> null   // ios: Views/HoldingsView.swift:2039
    }
    private fun granted(): Boolean = Defaults.ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    /** 아직 묻지 않음 (iOS notDetermined): 허락 안 됐고 한 번도 안 물었다 */
    private fun notDetermined(): Boolean = !granted() && !Defaults.prefs.getBoolean("town.locAsked", false)

    // ios: Views/HoldingsView.swift:2042
    suspend fun refresh() {
        val d = Defaults.prefs   // ios: Views/HoldingsView.swift:2043
        if (d.getString("town.skyDay", null) == TownLog.day || busy) return   // ios: Views/HoldingsView.swift:2044
        busy = true; try {   // ios: Views/HoldingsView.swift:2045
            val a = ask
            if (notDetermined() && a != null) {   // ios: Views/HoldingsView.swift:2046,2047,2048
                a()   // ios:  답이 오면 위치 → 다시 refresh
                return   // ios: Views/HoldingsView.swift:2050
            }
            if (granted() && !d.contains("town.lat")) {   // ios: Views/HoldingsView.swift:2052,2053,2054
                requestLocation()   // ios: Views/HoldingsView.swift:2055 처음 한 번만. 받으면 다시 refresh
                return   // ios: Views/HoldingsView.swift:2056
            }
            val lat = if (d.contains("town.lat")) Double.fromBits(d.getLong("town.lat", 0)) else 37.6; val lon = if (d.contains("town.lon")) Double.fromBits(d.getLong("town.lon", 0)) else 127.0   // ios: Views/HoldingsView.swift:2058
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=weather_code"   // ios: Views/HoldingsView.swift:2059
            val got = withContext(Dispatchers.IO) {   // ios: Views/HoldingsView.swift:2060
                runCatching {
                    (URL(url).openConnection() as HttpURLConnection).run { connectTimeout = 15000; readTimeout = 15000; inputStream.bufferedReader().use { it.readText() } }
                }.getOrNull()
            } ?: return
            val j = runCatching { NJson.parseToJsonElement(got).jsonObject }.getOrNull() ?: return   // ios: Views/HoldingsView.swift:2061
            val c = runCatching { j["current"]?.jsonObject?.get("weather_code")?.jsonPrimitive?.intOrNull }.getOrNull() ?: return   // ios: Views/HoldingsView.swift:2062
            val k: Kind = if (c <= 1) Kind.clear else if (c <= 3) Kind.cloud else if (c <= 48) Kind.fog else if (c in 71..77 || c in 85..86) Kind.snow else if (c >= 95) Kind.storm else Kind.rain   // ios: Views/HoldingsView.swift:2063
            kind = k
            d.edit().putInt("town.sky", k.ordinal).putString("town.skyDay", TownLog.day).apply()
        } finally { busy = false }
    }
    /** 허락 창의 답이 왔다 (거절이어도 다시 묻지 않고 서울로) */
    // ios: Views/HoldingsView.swift:2067
    fun didChangeAuthorization() {
        Defaults.prefs.edit().putBoolean("town.locAsked", true).apply()
        main.launch { if (!notDetermined()) refresh() }
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
    // ios: Views/HoldingsView.swift:2070
    private fun didUpdateLocations(l: Location) {
        val c = l   // ios: Views/HoldingsView.swift:2071
        val lat = rnd(c.latitude * 10) / 10; val lon = rnd(c.longitude * 10) / 10   // ios: Views/HoldingsView.swift:2072
        main.launch {   // ios: Views/HoldingsView.swift:2073
            Defaults.prefs.edit().putLong("town.lat", lat.toRawBits()).putLong("town.lon", lon.toRawBits()).apply()
            refresh()
        }
    }
    // ios: Views/HoldingsView.swift:2078
    private fun didFailWithError() {
        main.launch {   // ios: Views/HoldingsView.swift:2079
            Defaults.prefs.edit().putLong("town.lat", 37.6.toRawBits()).putLong("town.lon", 127.0.toRawBits()).apply()   // ios: Views/HoldingsView.swift:2080 못 찾으면 서울
            refresh()
        }
    }
    /** Swift .rounded(): 0.5 는 0 에서 먼 쪽으로 */
    private fun rnd(x: Double): Double = if (x < 0) -floor(-x + 0.5) else floor(x + 0.5)
}

/** 날씨 그림: 멈춘 빗줄기·눈송이·구름 그늘 (애니메이션 없음). 자리는 씨앗으로 정해 한 번만 계산 */
@Composable
fun TownWeather(kind: TownSky.Kind?, modifier: Modifier = Modifier) {   // ios: Views/HoldingsView.swift:2087,2088
    Spacer(modifier.drawWithCache {   // ios: Views/HoldingsView.swift:2089,2090
        val s = size; val u = density
        onDrawBehind {
            when (kind) {   // ios: Views/HoldingsView.swift:2091
                TownSky.Kind.rain, TownSky.Kind.storm -> {   // ios: Views/HoldingsView.swift:2092
                    if (kind == TownSky.Kind.storm) drawRect(Color.Black.copy(alpha = 0.18f))
                    for (i in 0 until 40) {   // ios: Views/HoldingsView.swift:2094
                        val x = (TownLook.seed("r$i") % 1000.toULong()).toFloat() / 1000 * s.width; val y = (TownLook.seed("ry$i") % 1000.toULong()).toFloat() / 1000 * s.height   // ios: Views/HoldingsView.swift:2095
                        val a = Offset(x, y); val b = Offset(x - 4 * u, y + 12 * u)
                        drawLine(Color(0.75f, 0.85f, 1f).copy(alpha = 0.7f), a, b, strokeWidth = 1.5f * u)   // ios: Views/HoldingsView.swift:2097
                    }
                }
                TownSky.Kind.snow -> {
                    for (i in 0 until 36) {   // ios: Views/HoldingsView.swift:2100
                        val x = (TownLook.seed("s$i") % 1000.toULong()).toFloat() / 1000 * s.width; val y = (TownLook.seed("sy$i") % 1000.toULong()).toFloat() / 1000 * s.height
                        drawRect(Color.White.copy(alpha = 0.9f), Offset(x, y), Size(4 * u, 4 * u))   // ios: Views/HoldingsView.swift:2102
                    }
                }
                TownSky.Kind.cloud, TownSky.Kind.fog -> {   // ios: Views/HoldingsView.swift:2104
                    drawRect(Color.White.copy(alpha = if (kind == TownSky.Kind.fog) 0.28f else 0.0f))   // ios: Views/HoldingsView.swift:2105
                    drawRect(Color.Black.copy(alpha = if (kind == TownSky.Kind.cloud) 0.10f else 0.0f))
                }
                TownSky.Kind.clear ->   // ios: Views/HoldingsView.swift:2107
                    drawOval(Color(1f, 0.86f, 0.35f), Offset(s.width * 0.42f, 8 * u), Size(22 * u, 22 * u))   // ios: Views/HoldingsView.swift:2108
                null -> {}   // ios: Views/HoldingsView.swift:2109
            }
        }
    })
}

/** 대화 기록(일기장): 주민마다 날짜 · 하루 한마디 · 내 답 한 줄. 앱 설정 한 칸에 JSON(Base64, 백업과 같은 꼴), 주민마다 최근 400일만 */
// ios: Views/HoldingsView.swift:2116
class TownLog private constructor() {
    companion object {
        val shared: TownLog by lazy { TownLog() }   // ios: Views/HoldingsView.swift:2117
        private const val key = "town.log"
        private const val keep = 400
        private fun load(): Map<String, List<Entry>> = Defaults.prefs.getString(key, null)?.let {   // ios: Views/HoldingsView.swift:2123
            runCatching { NJson.decodeFromString<Map<String, List<Entry>>>(Base64.getDecoder().decode(it).decodeToString()) }.getOrNull() } ?: emptyMap()
        val day: String get() { val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US); return LocalDate.now().format(f) }   // ios: Views/HoldingsView.swift:2128
        const val ripe = 90   // ios: Views/HoldingsView.swift:2161 내 답을 꺼내고 예측을 매기는 날 수 (석 달)
        // ios: Views/HoldingsView.swift:2162
        fun age(day: String): Int {
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)   // ios: Views/HoldingsView.swift:2202
            return runCatching { LocalDate.parse(day, f) }.getOrNull()?.let { runCatching { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }.getOrNull() ?: 0 } ?: 0
        }
    }
    // lang: 한마디를 만든 앱 언어 (없으면 ko), p: 답할 때 가격, back: 꺼내 봤음
    @Serializable
    data class Entry(var day: String, var say: String, var me: String? = null, var p: Double? = null, var back: Boolean? = null, var lang: String? = null)
    var logs: Map<String, List<Entry>> by mutableStateOf(load())
        private set
    // ios: Views/HoldingsView.swift:2125
    fun reload() {
        logs = load()   // ios: Views/HoldingsView.swift:2126
    }
    fun log(id: String): List<Entry> = logs[id] ?: emptyList()   // ios: Views/HoldingsView.swift:2129
    /** 오늘 한마디. 오늘 처음이면 make 로 정해 적는다 */
    // ios: Views/HoldingsView.swift:2131
    fun today(id: String, make: () -> String): Entry {
        val d = day   // ios: Views/HoldingsView.swift:2132
        val lang = if (AppModel.en) "en" else "ko"
        val last = logs[id]?.lastOrNull()
        if (last != null && last.day == d) {   // ios: Views/HoldingsView.swift:2124
            // 언어를 바꾼 날: 오늘 한마디만 새 언어로 다시 (내 답·가격은 그대로)
            if ((last.lang ?: "ko") == lang) return last
            val e = last.copy(say = make(), lang = lang)   // ios: Views/HoldingsView.swift:2137
            val l = logs[id]!!; logs = logs + (id to (l.dropLast(1) + e)); save()   // ios: Views/HoldingsView.swift:2138
            return e   // ios: Views/HoldingsView.swift:2139
        }
        val e = Entry(day = d, say = make(), lang = lang)   // ios: Views/HoldingsView.swift:2141
        val l = (logs[id] ?: emptyList()).toMutableList()   // ios: Views/HoldingsView.swift:2142
        l.add(e)   // ios: Views/HoldingsView.swift:2143
        if (l.size > keep) repeat(l.size - keep) { l.removeAt(0) }   // ios: Views/HoldingsView.swift:2144
        logs = logs + (id to l.toList())   // ios: Views/HoldingsView.swift:2145
        save()   // ios: Views/HoldingsView.swift:2146
        return e   // ios: Views/HoldingsView.swift:2147
    }
    /** 오늘 내 답 한 줄 (빈 칸이면 지움) */
    // ios: Views/HoldingsView.swift:2150
    fun answer(id: String, me: String, price: Double? = null, say: String? = null) {   // ios: Views/HoldingsView.swift:2334 say: 그때 대사창에 떠 있던 말 (오늘 첫 답이면 그 말로 남긴다)
        val l = logs[id]?.toMutableList(); val last = l?.lastOrNull(); if (l == null || last == null || last.day != day) return   // ios: Views/HoldingsView.swift:2151
        val t = me.trim()   // ios: Views/HoldingsView.swift:2152
        val v: String? = if (t.isEmpty()) null else t.take(60)   // ios: Views/HoldingsView.swift:2153
        if (v == last.me) return   // ios: Views/HoldingsView.swift:2154
        l[l.size - 1] = l[l.size - 1].copy(me = v)   // ios: Views/HoldingsView.swift:2155
        l[l.size - 1] = l[l.size - 1].copy(p = if (v == null) null else (last.p ?: price))   // ios: Views/HoldingsView.swift:2156
        if (last.me == null && v != null && say != null) l[l.size - 1] = l[l.size - 1].copy(say = say)
        logs = logs + (id to l.toList())   // ios: Views/HoldingsView.swift:2157
        save()   // ios: Views/HoldingsView.swift:2158
        if (v != null) TownBook.shared.earn(id)   // ios: Views/HoldingsView.swift:2159
    }
    /** 몇 달 전 내 답 하나 꺼내기 (오래된 것부터 한 번씩). 그때 가격과 견줘 맞혔는지 */
    // ios: Views/HoldingsView.swift:2167
    fun recall(id: String, price: Double?): String? {
        val l = logs[id]?.toMutableList() ?: return null; val i = l.indexOfFirst { it.me != null && it.back != true && age(it.day) >= ripe }
        if (i < 0) return null; val me = l[i].me ?: return null   // ios: Views/HoldingsView.swift:2169
        l[i] = l[i].copy(back = true)   // ios: Views/HoldingsView.swift:2170
        logs = logs + (id to l.toList())
        save()
        var s = "${max(3, age(l[i].day) / 30)}달 전에 \"${me}\"라고 했죠."
        val p = l[i].p; val now = price
        if (p != null && now != null && p > 0) {
            val v = now / p - 1
            s += cformat(" 그때보다 %.1f%% ", abs(v) * 100) + (if (v >= 0) "올랐어요." else "내렸어요.")
            val g = TownLook.guess(me)   // ios: Views/HoldingsView.swift:2177
            if (g != 0) s += if ((g > 0) == (v >= 0)) " 맞혔네요!" else " 빗나갔네요."   // ios: Views/HoldingsView.swift:2178
        }
        return s   // ios: Views/HoldingsView.swift:2180
    }
    /** 우리 사이: 남긴 답 · 매긴 예측(석 달 지난 것) · 맞힌 예측 */
    data class Score(val said: Int, val judged: Int, val hit: Int)
    // ios: Views/HoldingsView.swift:2183
    fun score(id: String, price: Double?): Score {
        val mine = log(id).filter { it.me != null }   // ios: Views/HoldingsView.swift:2184
        var j = 0; var h = 0   // ios: Views/HoldingsView.swift:2185
        if (price != null) {   // ios: Views/HoldingsView.swift:2186
            val now: Double = price
            for (e in mine) if (age(e.day) >= ripe) {
                val p = e.p; val me = e.me; if (p == null || !(p > 0) || me == null) continue   // ios: Views/HoldingsView.swift:2157
                val g = TownLook.guess(me)   // ios: Views/HoldingsView.swift:2189
                if (g == 0) continue   // ios: Views/HoldingsView.swift:2190
                j += 1   // ios: Views/HoldingsView.swift:2191
                if ((g > 0) == (now >= p)) h += 1
            }
        }
        return Score(mine.size, j, h)   // ios: Views/HoldingsView.swift:2195
    }
    // ios: Views/HoldingsView.swift:2197
    private fun save() {
        runCatching { Defaults.prefs.edit().putString(key, Base64.getEncoder().encodeToString(NJson.encodeToString(logs).encodeToByteArray())).apply() }
    }
}

/** 도트 한 칸 덩어리: (x, y, 너비, 높이, 색 0xRRGGBB). y 는 위에서부터 */
data class Px(val x: Int, val y: Int, val w: Int, val h: Int, val c: Long)

/** 주민 얼굴 + 모자. 모자는 그림 파일 없이 24×34 칸 위에 도트로 (테두리 한 칸 포함). frame: 동네에서 걷는 장면 (없으면 정면 서 있는 그림) */
// ios: Views/HoldingsView.swift:2203
@Composable
fun TownFace(
    id: String,   // ios: Views/HoldingsView.swift:2204
    note: TownBook.Note,   // ios: Views/HoldingsView.swift:2195
    width: Dp,
    height: Dp,   // ios: Views/HoldingsView.swift:2207
    frame: CharScene? = null,   // ios: Views/HoldingsView.swift:2209
    modifier: Modifier = Modifier,
) {
    val ch = remember(id, note) { TownLook.char(id, note) }
    Box(modifier.size(width, height)) {   // ios: Views/HoldingsView.swift:2212
        val img = if (frame != null) remember(ch, frame) { MeMotion.image(ch, frame) } else null
        if (frame != null && img != null) {   // ios: Views/HoldingsView.swift:2214
            SceneSprite(img = img, width = width, height = height, rows = MeArt.H)   // ios: Views/HoldingsView.swift:2215
        } else MeAvatar(me = ch, width = width, height = height)   // ios: Views/HoldingsView.swift:2216
        val star = TownStar.find(id)?.px ?: emptyList()   // ios: Views/HoldingsView.swift:2218
        val pr = note.prop ?: 0   // ios: Views/HoldingsView.swift:2211
        if (note.hat > 0 || pr > 0 || star.isNotEmpty()) {   // ios: Views/HoldingsView.swift:2221
            val lift = frame?.lift == true
            Canvas(Modifier.size(width, height)   // ios: Views/HoldingsView.swift:2222,2232
                .offset { IntOffset(0, if (lift) -(height / MeArt.H).roundToPx() else 0) }) {   // ios: Views/HoldingsView.swift:2231 숨·걸음에 머리가 1px 오르면 모자도 같이
                val u = size.width / MeArt.W   // ios: Views/HoldingsView.swift:2223
                val c = TownFace.tints[min(note.tint, TownFace.tints.size - 1)]   // ios: Views/HoldingsView.swift:2224
                val d = frame?.dir
                val px = TownFace.turn(star, d, hair = true) + TownFace.turn(TownFace.pixels(note.hat, c) + TownFace.props(pr), d)   // ios: Views/HoldingsView.swift:2226
                fun col(v: Long): Color = Color(red = ((v shr 16) and 0xFF) / 255f, green = ((v shr 8) and 0xFF) / 255f, blue = (v and 0xFF) / 255f)
                for (r in px) drawRect(col(MeArt.outline.toLong()), Offset((r.x - 1) * u, (r.y - 1) * u), Size((r.w + 2) * u, (r.h + 2) * u))   // ios: Views/HoldingsView.swift:2228
                for (r in px + TownFace.turn(TownFace.thin(pr), d)) drawRect(col(r.c), Offset(r.x * u, r.y * u), Size(r.w * u, r.h * u))   // ios: Views/HoldingsView.swift:2229
            }
        }
    }
}

/** TownFace 의 static 값·덧그림 도트 */
object TownFace {
    val hats = listOf("없음", "밀짚모자", "비니", "왕관", "야구모자", "꽃")   // ios: Views/HoldingsView.swift:2210
    val tints: List<Long> = listOf(0xE8B04B, 0xD94F4F, 0x4F7FD9, 0x4FB06A, 0x8A5FD0, 0xF2F2F2, 0x303030)   // ios: Views/HoldingsView.swift:2211
    /** 옆으로 걸을 때 덧그림도 옆모습으로: 한 칸 앞으로. 유명인 머리는 앞쪽 옆머리를 빼고 뒤쪽 옆머리로 뒤통수를 덮는다. 왼쪽은 뒤집는다 */
    // ios: Views/HoldingsView.swift:2237
    fun turn(px: List<Px>, d: CharDir?, hair: Boolean = false): List<Px> {
        if (!(d == CharDir.left || d == CharDir.right)) return px
        return px.mapNotNull { r ->
            var x = r.x; val y = r.y; var w = r.w; val h = r.h; val c = r.c
            if (hair && y >= 5 && x >= 14) return@mapNotNull null   // ios: Views/HoldingsView.swift:2241
            if (hair && y >= 5 && x + w <= 7) w = 11 - x   // ios: Views/HoldingsView.swift:2242
            x += 1   // ios: Views/HoldingsView.swift:2243
            if (d == CharDir.left) x = MeArt.W - x - w   // ios: Views/HoldingsView.swift:2244
            Px(x, y, w, h, c)   // ios: Views/HoldingsView.swift:2245
        }
    }
    fun pixels(hat: Int, c: Long): List<Px> {
        val dark = ((((c shr 16) and 0xFF) * 7 / 10) shl 16) or ((((c shr 8) and 0xFF) * 7 / 10) shl 8) or ((c and 0xFF) * 7 / 10)   // ios: Views/HoldingsView.swift:2250
        return when (hat) {   // ios: Views/HoldingsView.swift:2251
            1 -> listOf(Px(3, 5, 18, 1, c), Px(7, 2, 10, 3, c), Px(7, 4, 10, 1, dark))   // ios: Views/HoldingsView.swift:2252
            2 -> listOf(Px(6, 2, 12, 4, c), Px(6, 5, 12, 1, dark), Px(11, 0, 2, 2, 0xFFFFFF))   // ios: Views/HoldingsView.swift:2253
            3 -> listOf(Px(7, 3, 10, 2, c), Px(7, 1, 2, 2, c), Px(11, 0, 2, 3, c), Px(15, 1, 2, 2, c), Px(11, 3, 2, 1, 0xD94F4F))   // ios: Views/HoldingsView.swift:2254
            4 -> listOf(Px(6, 2, 12, 3, c), Px(12, 5, 9, 1, dark))   // ios: Views/HoldingsView.swift:2255
            5 -> listOf(Px(15, 2, 3, 3, c), Px(16, 3, 1, 1, 0xFFD54A))   // ios: Views/HoldingsView.swift:2256
            else -> emptyList()   // ios: Views/HoldingsView.swift:2257
        }
    }
    val propNames = listOf("없음", "안경", "목도리", "풍선")
    /** 소품 (모자처럼 테두리 한 칸). 1 안경은 테가 가늘어 thin 에만, 2 목도리, 3 풍선(손에 든 줄은 thin) */
    // ios: Views/HoldingsView.swift:2262
    fun props(n: Int): List<Px> = when (n) {   // ios: Views/HoldingsView.swift:2263
        2 -> listOf(Px(7, 15, 10, 2, 0xD94F4F), Px(13, 17, 3, 4, 0xD94F4F), Px(13, 19, 3, 1, 0xA83A3A), Px(8, 15, 1, 2, 0xF2F2F2), Px(11, 15, 1, 2, 0xF2F2F2))   // ios: Views/HoldingsView.swift:2264
        3 -> listOf(Px(19, 1, 4, 7, 0xE85D75), Px(18, 2, 6, 5, 0xE85D75), Px(20, 8, 2, 1, 0xC94860), Px(19, 3, 1, 2, 0xFFFFFF))   // ios: Views/HoldingsView.swift:2265
        else -> emptyList()   // ios: Views/HoldingsView.swift:2266
    }
    /** 테두리 없이 그리는 가는 선 (안경테 · 풍선 줄) */
    fun thin(n: Int): List<Px> = when (n) {
        1 -> {   // ios: Views/HoldingsView.swift:2272
            val k: Long = 0x2B2B33   // ios: Views/HoldingsView.swift:2273
            listOf(7, 13).flatMap { x -> listOf(Px(x, 9, 4, 1, k), Px(x, 12, 4, 1, k), Px(x, 9, 1, 4, k), Px(x + 3, 9, 1, 4, k)) } + listOf(Px(11, 10, 2, 1, k))   // ios: Views/HoldingsView.swift:2274
        }
        3 -> listOf(Px(21, 9, 1, 12, 0x555555))   // ios: Views/HoldingsView.swift:2275
        else -> emptyList()   // ios: Views/HoldingsView.swift:2276
    }
}

/**
 * 유명인 주민: 부품 얼굴 위에 머리 모양만 따로 그린 도트를 얹는다 (풍자 캐리커처).
 * 주민 id 는 대표 이름("ceo:젠슨 황") 또는 종목 코드. 이장은 트럼프
 */
// ios: Views/HoldingsView.swift:2283
object TownStar {
    data class Look(val skin: Int = 1, val glasses: Int = 0, val top: Int = 0, val px: List<Px>)   // ios: Views/HoldingsView.swift:2284,2285
    /** 짧은 머리 기본꼴: 정수리 + 옆머리 */
    private fun crop(c: Long, d: Long, side: Int = 3): List<Px> = listOf(Px(7, 3, 10, 1, d), Px(6, 4, 12, 2, c), Px(5, 5, 2, side, c), Px(17, 5, 2, side, c))
    val looks: Map<String, Look> by lazy {   // ios: Views/HoldingsView.swift:2288
        val brown: Long = 0x6B4A2E; val dbrown: Long = 0x4A3220; val black: Long = 0x26201E; val grey: Long = 0xA9ADB4; val silver: Long = 0xD4D7DC   // ios: Views/HoldingsView.swift:2289
        val m = HashMap<String, Look>()   // ios: Views/HoldingsView.swift:2290
        // 빅7
        m["musk"] = Look(px = crop(0x4A3426, 0x33241A) + listOf(Px(6, 6, 5, 1, 0x4A3426)))   // ios: Views/HoldingsView.swift:2292
        m["jensen"] = Look(top = 5, px = crop(black, 0x111111, side = 4) + listOf(Px(8, 4, 4, 1, grey), Px(14, 4, 2, 1, grey)))   // ios: Views/HoldingsView.swift:2293
        m["nadella"] = Look(skin = 2, glasses = 1, px = listOf(Px(8, 4, 8, 1, grey), Px(5, 6, 2, 4, grey), Px(17, 6, 2, 4, grey)))   // ios: Views/HoldingsView.swift:2294
        m["zuck"] = Look(top = 5, px = listOf(Px(7, 3, 10, 1, dbrown), Px(6, 4, 12, 2, brown), Px(6, 6, 12, 1, brown), Px(5, 5, 1, 3, brown), Px(18, 5, 1, 3, brown)))   // ios: Views/HoldingsView.swift:2295
        m["pichai"] = Look(skin = 2, px = listOf(Px(8, 3, 8, 1, 0x111111), Px(6, 4, 12, 1, black), Px(5, 5, 2, 3, black), Px(17, 5, 2, 3, black)))
        m["jassy"] = Look(px = listOf(Px(5, 7, 2, 3, 0x5A4A40), Px(17, 7, 2, 3, 0x5A4A40), Px(9, 5, 2, 1, 0xFFF4E6)))   // ios: Views/HoldingsView.swift:2297 민머리 + 반짝
        m["ternus"] = Look(px = crop(0x7A5134, 0x5A3A24) + listOf(Px(12, 6, 5, 1, 0x7A5134)))   // ios: Views/HoldingsView.swift:2298
        // AI
        m["altman"] = Look(px = crop(brown, dbrown) + listOf(Px(8, 2, 2, 1, brown), Px(12, 2, 2, 1, brown), Px(15, 2, 2, 1, brown)))   // ios: Views/HoldingsView.swift:2300
        m["amodei"] = Look(glasses = 1, px = listOf(Px(6, 2, 12, 2, black), Px(5, 4, 14, 2, black), Px(4, 5, 2, 5, black), Px(18, 5, 2, 5, black), Px(7, 6, 3, 1, black), Px(13, 6, 3, 1, black)))   // ios: Views/HoldingsView.swift:2301
        m["hassabis"] = Look(px = listOf(Px(8, 3, 8, 1, black), Px(6, 4, 12, 1, black), Px(5, 5, 2, 3, black), Px(17, 5, 2, 3, black)))   // ios: Views/HoldingsView.swift:2302
        // 금리·정치
        m["warsh"] = Look(top = 8, px = crop(silver, grey) + listOf(Px(6, 6, 4, 1, silver), Px(11, 4, 1, 2, grey)))   // ios: Views/HoldingsView.swift:2304
        m["trump"] = Look(top = 8, px = listOf(Px(6, 2, 12, 2, 0xE8C060), Px(4, 4, 16, 2, 0xE8C060), Px(4, 6, 6, 1, 0xE8C060), Px(4, 5, 2, 4, 0xE8C060), Px(18, 5, 2, 4, 0xE8C060),   // ios: Views/HoldingsView.swift:2305
            Px(12, 3, 6, 1, 0xC9A040), Px(11, 17, 2, 6, 0xD32F2F)))
        m
    }   // ios: Views/HoldingsView.swift:2308
    /** 주민 id → 얼굴 열쇠 (대표 이름이나 종목 코드로 찾는다) */
    val keys: Map<String, String> = mapOf(   // ios: Views/HoldingsView.swift:2310
        "ceo:일론 머스크" to "musk", "TSLA" to "musk", "ceo:젠슨 황" to "jensen", "NVDA" to "jensen", "ceo:사티아 나델라" to "nadella", "MSFT" to "nadella",   // ios: Views/HoldingsView.swift:2311
        "ceo:마크 저커버그" to "zuck", "META" to "zuck", "ceo:순다르 피차이" to "pichai", "GOOGL" to "pichai", "GOOG" to "pichai",
        "ceo:앤디 재시" to "jassy", "AMZN" to "jassy", "ceo:존 터너스" to "ternus", "ceo:팀 쿡" to "ternus", "AAPL" to "ternus",
        "ceo:샘 올트먼" to "altman", "ceo:다리오 아모데이" to "amodei", "ceo:데미스 허사비스" to "hassabis",
        TownLook.warshID to "warsh", TownLook.mayorID to "trump",
    )
    fun find(id: String): Look? = keys[id]?.let { looks[it] }   // ios: Views/HoldingsView.swift:2317
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
fun TownDress(res: TownLook.Resident, dismiss: () -> Unit) {   // ios: Views/HoldingsView.swift:2321,2322,2323
    var n by remember(res.id) { mutableStateOf(TownBook.shared.note(res.id)) }   // ios: Views/HoldingsView.swift:2324,2325
    Sheet(show = true, onDismiss = dismiss, medium = true) {   // ios: Views/HoldingsView.swift:2326,2327,2367
        TownSheetBar(res.name, "닫기", onClickLeft = dismiss, right = "저장", onClickRight = { TownBook.shared.set(res.id, n); dismiss() })   // ios: Views/HoldingsView.swift:2361,2362,2363,2364
        Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TownFace(id = res.id, note = n, width = 96.dp, height = 136.dp)   // ios: Views/HoldingsView.swift:2329
            BasicTextField(n.name ?: "", { v -> n = n.copy(name = if (v.isEmpty()) null else v.take(10)) },   // ios: Views/HoldingsView.swift:2330
                Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(10.dp)).padding(10.dp),   // ios: Views/HoldingsView.swift:2332
                textStyle = appFont(16f).copy(color = Theme.ink, textAlign = TextAlign.Center), singleLine = true, cursorBrush = SolidColor(Theme.teal),   // ios: Views/HoldingsView.swift:2331
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) { if ((n.name ?: "").isEmpty()) T(TownLook.nick(res), style = appFont(16f), color = Theme.muted, maxLines = 1); inner() }
                })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/HoldingsView.swift:2334
                TownFace.hats.forEachIndexed { i, t ->   // ios: Views/HoldingsView.swift:2335
                    T(t, Modifier.plainClickable { n = n.copy(hat = i) }   // ios: Views/HoldingsView.swift:2336
                        .background(if (n.hat == i) Theme.card else Color.Transparent, CapsuleShape)   // ios: Views/HoldingsView.swift:2339
                        .border(1.dp, Theme.border, CapsuleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),   // ios: Views/HoldingsView.swift:2338
                        style = appFont(13f, if (n.hat == i) FontWeight.Bold else FontWeight.Normal))   // ios: Views/HoldingsView.swift:2337
                }
            }
            if (n.hat > 0) {   // ios: Views/HoldingsView.swift:2346
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/HoldingsView.swift:2347
                    TownFace.tints.forEachIndexed { i, c ->   // ios: Views/HoldingsView.swift:2348
                        Box(Modifier.size(28.dp, 28.dp)   // ios: Views/HoldingsView.swift:2351
                            .plainClickable { n = n.copy(tint = i) }   // ios: Views/HoldingsView.swift:2349
                            .background(Color(red = (c shr 16 and 0xFF) / 255f, green = (c shr 8 and 0xFF) / 255f, blue = (c and 0xFF) / 255f), CircleShape)   // ios: Views/HoldingsView.swift:2350
                            .border(if (n.tint == i) 2.dp else 1.dp, if (n.tint == i) Theme.ink else Theme.border, CircleShape))   // ios: Views/HoldingsView.swift:2352
                    }
                }
            }
            Spacer(Modifier.weight(1f))   // ios: Views/HoldingsView.swift:2358
        }
    }
}

/** 우리 사이: 이사 온 날 · 함께한 날 · 내 답과 예측 적중 · 별사탕 주기 (시장 정보 아님). 시트째 그린다 */
@Composable
fun TownUs(res: TownLook.Resident, dismiss: () -> Unit) {   // ios: Views/HoldingsView.swift:2372,2373,2374
    val book = TownBook.shared   // ios: Views/HoldingsView.swift:2375
    val n = book.note(res.id)   // ios: Views/HoldingsView.swift:2377
    val s = TownLog.shared.score(res.id, TownLook.price(res))   // ios: Views/HoldingsView.swift:2380
    val days = if (n.moved.isEmpty()) 0 else TownLog.age(n.moved) - TownLog.age(n.left ?: TownLog.day) + 1   // ios: Views/HoldingsView.swift:2393
    val sad = TownLook.mood(res) < 0   // ios: Views/HoldingsView.swift:2380
    val cheer = book.cheered(res.id)   // ios: Views/HoldingsView.swift:2381
    Sheet(show = true, onDismiss = dismiss, medium = true) {   // ios: Views/HoldingsView.swift:2376,2382,2408
        TownSheetBar(TownLook.shown(res, n), "닫기", onClickLeft = dismiss)   // ios: Views/HoldingsView.swift:2405,2406
        Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp),   // ios: Views/HoldingsView.swift:2404
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/HoldingsView.swift:2383
            TownFace(id = res.id, note = n, width = 72.dp, height = 102.dp)   // ios: Views/HoldingsView.swift:2384
            Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(12.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Views/HoldingsView.swift:2385,2393
                row("이사 온 날", n.moved.replace("-", "."))   // ios: Views/HoldingsView.swift:2386
                row("함께한 날", "${days}일")   // ios: Views/HoldingsView.swift:2387
                val l = n.left; if (l != null) row("떠난 날", l.replace("-", "."))   // ios: Views/HoldingsView.swift:2388
                row("남긴 답", "${s.said}줄")   // ios: Views/HoldingsView.swift:2389
                row("예측 적중", if (s.judged == 0) "석 달 뒤부터" else "${s.hit} / ${s.judged}")   // ios: Views/HoldingsView.swift:2390
                row("받은 별사탕", "${n.gifts ?: 0}개")
            }
            val off = cheer || !sad || book.candy == 0   // ios: Views/HoldingsView.swift:2400
            Box(Modifier.fillMaxWidth()
                .then(if (off) Modifier else Modifier.plainClickable { book.give(res.id) })   // ios: Views/HoldingsView.swift:2394,2399
                .background(Theme.card, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                T(if (cheer) "기운 났어요! ♥" else if (sad) "별사탕 주기 (${book.candy}개)" else "지금은 기운이 넘쳐요", Modifier.padding(12.dp), style = appFont(15f, FontWeight.Bold), color = if (off) Theme.muted else Theme.ink)   // ios: Views/HoldingsView.swift:2395
            }
            T("별사탕은 주민에게 답을 남기면 하나씩 생겨요", style = appFont(12f), color = Theme.sub)   // ios: Views/HoldingsView.swift:2401
            Spacer(Modifier.weight(1f))   // ios: Views/HoldingsView.swift:2402
        }
    }
}

@Composable
private fun row(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/HoldingsView.swift:2411
        T(k, style = appFont(14f), color = Theme.sub); Spacer(Modifier.weight(1f)); T(v, style = appFont(14f, FontWeight.Bold))
    }
}

/** TownGround 의 static 값 */
object TownGround {
    val names = listOf("들판", "광장", "언덕 위 집", "노란 문 골목")   // ios: Views/HoldingsView.swift:2420
}

/**
 * 동네 배경 그림 (GBA 젤다처럼 위에서 비스듬히, 사물은 앞면·그림자로 입체감). 0번 들판은 TownView 의 ground 그대로.
 * 한 번 그려 두고(drawWithCache), 입력(kind·season)과 크기가 같으면 다시 그리지 않는다. 좌표는 iOS 와 같은 pt(=dp) 로 그린다.
 */
@Composable
fun TownGround(kind: Int, season: Int, modifier: Modifier = Modifier) {   // ios: Views/HoldingsView.swift:2418,2419
    Spacer(modifier.drawWithCache {   // ios: Views/HoldingsView.swift:2422,2423
        val k = density
        onDrawBehind { scale(k, k, pivot = Offset.Zero) { townGround(kind, season, size.width / k.toDouble(), size.height / k.toDouble()) } }
    })
}

/** TownGround 그리기 본문 (W·H 는 dp) */
private fun DrawScope.townGround(kind: Int, season: Int, W: Double, H: Double) {
    val grass = listOf(hex(0x8CC76E), hex(0x66AD59), hex(0xA8AB5E), hex(0xD6E0E8))[season]
    val leaf = listOf(hex(0xF5BBD0), hex(0x34803D), hex(0xEA8533), hex(0xF7FAFF))[season]   // ios: Views/HoldingsView.swift:2426
    fun R(x: Double, y: Double, w: Double, h: Double, c: Color, r: Double = 0.0) {
        val rc = Rect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())   // ios: Views/HoldingsView.swift:2428
        if (r > 0) drawRoundRect(c, rc.topLeft, rc.size, CornerRadius(r.toFloat())) else drawRect(c, rc.topLeft, rc.size)   // ios: Views/HoldingsView.swift:2429
    }
    fun O(x: Double, y: Double, w: Double, h: Double, c: Color) {
        drawOval(c, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))
    }
    fun shadow(x: Double, y: Double, w: Double, h: Double) { O(x, y, w, h, Color.Black.copy(alpha = 0.22f)) }   // ios: Views/HoldingsView.swift:2434
    // ios: Views/HoldingsView.swift:2435
    fun tufts(seed: Int) {
        var v = seed.toLong()   // ios: Views/HoldingsView.swift:2436
        for (n in 0 until 70) {   // ios: Views/HoldingsView.swift:2437
            v = (v * 9301 + 49297) % 233280; val x = (v % max(1.0, W).toLong()).toDouble()   // ios: Views/HoldingsView.swift:2438
            v = (v * 9301 + 49297) % 233280; val y = (v % max(1.0, H).toLong()).toDouble()
            R(x, y, 5.0, 3.0, Color.Black.copy(alpha = 0.12f), 1.5); R(x + 1, y - 2, 2.0, 2.0, Color.White.copy(alpha = 0.3f))   // ios: Views/HoldingsView.swift:2440
        }
    }
    // ios: Views/HoldingsView.swift:2443
    fun tree(x: Double, y: Double, k: Double, fruit: Boolean = false) {
        val w = 52 * k   // ios: Views/HoldingsView.swift:2444
        shadow(x - 4, y + w * 0.74, w + 10, w * 0.26)   // ios: Views/HoldingsView.swift:2445
        R(x + w * 0.4, y + w * 0.55, w * 0.2, w * 0.4, hex(0x7A4A2A), 2.0)   // ios: Views/HoldingsView.swift:2446
        R(x + w * 0.5, y + w * 0.55, w * 0.1, w * 0.4, hex(0x5A3418))   // ios: Views/HoldingsView.swift:2447
        O(x, y, w, w * 0.8, leaf); O(x, y, w, w * 0.8, Color.Black.copy(alpha = 0.28f))
        O(x + w * 0.06, y + 2, w * 0.82, w * 0.62, leaf)   // ios: Views/HoldingsView.swift:2449
        O(x + w * 0.18, y + w * 0.1, w * 0.36, w * 0.22, Color.White.copy(alpha = 0.3f))   // ios: Views/HoldingsView.swift:2450
        if (fruit) {   // ios: Views/HoldingsView.swift:2451
            for (p in listOf(0.2 to 0.4, 0.6 to 0.3, 0.45 to 0.52, 0.72 to 0.48, 0.3 to 0.2, 0.55 to 0.12)) {   // ios: Views/HoldingsView.swift:2452
                O(x + w * p.first, y + w * p.second, 6.0, 6.0, hex(0xC83030)); R(x + w * p.first + 1, y + w * p.second + 1, 2.0, 2.0, hex(0xFF9080))   // ios: Views/HoldingsView.swift:2453
            }
        }
    }
    // ios: Views/HoldingsView.swift:2457
    fun house(x: Double, y: Double, w: Double, roof: Color) {
        val rh = w * 0.55; val wh = w * 0.44; val fy = y + rh + wh   // ios: Views/HoldingsView.swift:2458
        shadow(x + 6, fy - 8, w + 12, 16.0)   // ios: Views/HoldingsView.swift:2459
        R(x + 4, y + rh, w - 8, wh, hex(0xF0E0C0)); R(x + 4, fy - 7, w - 8, 7.0, hex(0xD0B890))   // ios: Views/HoldingsView.swift:2460
        R(x, y, w, rh, roof, 3.0); R(x, y + rh - 9, w, 9.0, Color.Black.copy(alpha = 0.25f)); R(x, y + rh / 2 - 2, w, 3.0, Color.Black.copy(alpha = 0.2f))
        R(x + 7, y + 5, w * 0.4, 3.0, Color.White.copy(alpha = 0.35f))   // ios: Views/HoldingsView.swift:2462
        R(x + w * 0.7, y - 12, 14.0, 18.0, hex(0xA89880)); R(x + w * 0.7, y - 12, 14.0, 4.0, hex(0xD8D0C0))   // ios: Views/HoldingsView.swift:2463
        val dx = x + w / 2 - 10
        R(dx, fy - 34, 20.0, 34.0, hex(0x6A4020)); R(dx + 2, fy - 32, 16.0, 3.0, hex(0x8A5A30)); R(dx + 13, fy - 19, 3.0, 3.0, hex(0xF0D060))
        for (wx in listOf(x + 12, x + w - 32)) {
            R(wx, y + rh + 10, 20.0, 16.0, hex(0x5A4030)); R(wx + 2, y + rh + 12, 16.0, 12.0, hex(0x88C8F0)); R(wx + 2, y + rh + 12, 7.0, 5.0, hex(0xC8ECFF))
        }
    }
    // ios: Views/HoldingsView.swift:2470
    fun fence(x: Double, y: Double, n: Int) {
        R(x, y + 5, n.toDouble() * 14 - 8, 3.0, hex(0xC08850)); R(x, y + 12, n.toDouble() * 14 - 8, 3.0, hex(0xC08850))   // ios: Views/HoldingsView.swift:2471
        for (i in 0 until n) {   // ios: Views/HoldingsView.swift:2472
            val px = x + i.toDouble() * 14   // ios: Views/HoldingsView.swift:2473
            R(px, y, 5.0, 19.0, hex(0xA87040)); R(px, y, 5.0, 3.0, hex(0xD8A060)); R(px + 3, y + 3, 2.0, 16.0, hex(0x7A4A20))   // ios: Views/HoldingsView.swift:2474
        }
    }
    fun path(x: Double, y: Double, w: Double, h: Double) {
        R(x, y, w, h, hex(0xE0C890)); R(x, y, w, 3.0, hex(0xC8A870)); R(x, y, 3.0, h, hex(0xC8A870))
    }
    // ios: Views/HoldingsView.swift:2480
    fun rock(x: Double, y: Double) {
        shadow(x - 2, y + 11, 24.0, 8.0); R(x, y, 20.0, 15.0, hex(0x8A8A8A), 7.0); R(x + 3, y + 2, 12.0, 7.0, hex(0xC0C0C0), 5.0)   // ios: Views/HoldingsView.swift:2481
    }
    // ios: Views/HoldingsView.swift:2483
    fun pond(x: Double, y: Double, w: Double, h: Double) {
        R(x - 4, y - 4, w + 8, h + 8, Color.Black.copy(alpha = 0.18f), 20.0); R(x, y, w, h, hex(0x3878C8), 18.0)   // ios: Views/HoldingsView.swift:2484
        R(x + 5, y + 6, w - 10, h - 12, hex(0x58A8E8), 14.0)   // ios: Views/HoldingsView.swift:2485
        R(x + w * 0.3, y + h * 0.35, 18.0, 3.0, hex(0xC8ECFF)); R(x + w * 0.55, y + h * 0.6, 12.0, 3.0, hex(0xC8ECFF))   // ios: Views/HoldingsView.swift:2486
    }
    // ios: Views/HoldingsView.swift:2488
    fun flowers(x: Double, y: Double, c: Long) {
        O(x, y, 5.0, 5.0, hex(c)); O(x + 9, y + 6, 5.0, 5.0, hex(c)); O(x + 2, y + 12, 5.0, 5.0, Color.White); O(x + 12, y - 4, 4.0, 4.0, hex(0xF8E060))
    }
    // ios: Views/HoldingsView.swift:2491
    fun cat(x: Double, y: Double) {
        val c = hex(0xE0A050)
        R(x, y, 16.0, 9.0, c, 4.0); R(x + 10, y - 6, 9.0, 9.0, c, 3.0); R(x + 11, y - 9, 3.0, 4.0, c); R(x + 16, y - 9, 3.0, 4.0, c)   // ios: Views/HoldingsView.swift:2493
        R(x - 5, y - 5, 4.0, 10.0, hex(0xC08030), 2.0); R(x + 15, y - 3, 2.0, 2.0, hex(0x203020))   // ios: Views/HoldingsView.swift:2494
    }
    // ios: Views/HoldingsView.swift:2496
    fun hedge(x: Double, y: Double, w: Double) {
        shadow(x, y + 24, w, 9.0); R(x, y + 9, w, 19.0, Color.Black.copy(alpha = 0.2f), 6.0); R(x, y + 9, w, 19.0, leaf.copy(alpha = 0.85f), 6.0)   // ios: Views/HoldingsView.swift:2497
        R(x, y, w, 15.0, leaf, 7.0); R(x + 5, y + 3, w - 10, 3.0, Color.White.copy(alpha = 0.3f), 1.5)
    }

    R(0.0, 0.0, W, H, grass)   // ios: Views/HoldingsView.swift:2501
    when (kind) {   // ios: Views/HoldingsView.swift:2502
        1 -> {   // ios: Views/HoldingsView.swift:2503 광장: 위에 집 두 채, 길이 만나는 곳, 대추나무, 연못
            tufts(7)   // ios: Views/HoldingsView.swift:2504
            path(W / 2 - 22, 0.0, 44.0, H); path(0.0, H * 0.5 - 18, W, 36.0)   // ios: Views/HoldingsView.swift:2505
            tree(-18.0, -18.0, 1.0); tree(W - 46, -16.0, 0.9)   // ios: Views/HoldingsView.swift:2506
            house(14.0, 10.0, 108.0, hex(0xD85848)); house(W - 122, 6.0, 108.0, hex(0x4878C8))   // ios: Views/HoldingsView.swift:2507
            fence(14.0, 126.0, 6); fence(W - 106, 126.0, 6); cat(W - 64, 121.0)
            tree(-12.0, H - 86, 1.3, fruit = true); pond(W - 96, H - 64, 82.0, 50.0)
            rock(W * 0.3, H * 0.9); flowers(W * 0.12, H * 0.42, 0xF070A0); flowers(W * 0.8, H * 0.58, 0xF8F8F8)
        }
        2 -> {   // ios:  언덕 위 집: 절벽 위 집·대추나무, 계단으로 내려오는 아랫동네
            tufts(11)
            val cy = H * 0.26
            R(0.0, 0.0, W, cy, Color.White.copy(alpha = 0.1f))   // ios: Views/HoldingsView.swift:2514
            tree(-16.0, -18.0, 0.9); house(W / 2 - 56, -6.0, 112.0, hex(0xC86040)); tree(W - 70, 8.0, 1.0, fruit = true)   // ios: Views/HoldingsView.swift:2515
            R(36.0, 56.0, 4.0, 18.0, hex(0x5A4030)); R(30.0, 48.0, 18.0, 11.0, hex(0xD83838), 4.0)   // ios: Views/HoldingsView.swift:2516
            for ((x0, x1) in listOf(0.0 to W / 2 - 24, W / 2 + 24 to W)) {
                R(x0, cy, x1 - x0, 32.0, hex(0xB88858))   // ios: Views/HoldingsView.swift:2518
                var i = x0 + 8; while (i < x1) { R(i, cy + 6, 3.0, 22.0, hex(0x986838)); i += 20 }   // ios: Views/HoldingsView.swift:2519
                R(x0, cy + 26, x1 - x0, 6.0, hex(0x7A5028)); R(x0, cy - 6, x1 - x0, 8.0, grass); R(x0, cy - 6, x1 - x0, 2.0, Color.White.copy(alpha = 0.35f))   // ios: Views/HoldingsView.swift:2520
            }
            R(W / 2 - 24, cy - 4, 48.0, 36.0, hex(0xD8C8A0))
            var st = cy; while (st < cy + 32) { R(W / 2 - 24, st, 48.0, 3.0, hex(0xB0A080)); st += 9 }   // ios: Views/HoldingsView.swift:2523
            path(W / 2 - 20, cy + 32, 40.0, H); path(0.0, H * 0.64, W, 34.0)   // ios: Views/HoldingsView.swift:2524
            rock(W * 0.16, H * 0.45); rock(W * 0.82, H * 0.48); flowers(W * 0.3, H * 0.86, 0xF8E060)   // ios: Views/HoldingsView.swift:2525
            pond(10.0, H - 62, 76.0, 48.0); tree(W - 58, H - 80, 1.1)   // ios: Views/HoldingsView.swift:2526
        }
        3 -> {   // ios: Views/HoldingsView.swift:2527 노란 문 골목: 담장 가운데 노란빛 문, 돌길, 양옆 덤불, 가로등
            val fx = W * 0.18; val fw = W * 0.64; val wh = H * 0.2   // ios: Views/HoldingsView.swift:2528
            R(fx, 0.0, fw, H, hex(0xB8B0A0))   // ios: Views/HoldingsView.swift:2529
            var y = 0.0; var row = 0
            while (y < H) { var x = fx + (if (row % 2 == 0) 0 else 10); while (x < fx + fw - 4) { R(x, y, 18.0, 18.0, hex(0xC8C0B0), 3.0); x += 20 }; y += 20; row += 1 }   // ios: Views/HoldingsView.swift:2531
            R(0.0, 0.0, W, 8.0, hex(0xD8D0C0)); R(0.0, 8.0, W, wh, hex(0xA89880))   // ios: Views/HoldingsView.swift:2532
            var by = 24.0; while (by < wh + 8) { R(0.0, by, W, 2.0, hex(0x8A7A62)); by += 18 }   // ios: Views/HoldingsView.swift:2533
            R(0.0, wh + 8, W, 8.0, Color.Black.copy(alpha = 0.18f))
            O(W / 2 - 50, wh + 4, 100.0, 46.0, hex(0xFFE678, 0.35f))   // ios: Views/HoldingsView.swift:2535
            R(W / 2 - 24, 14.0, 48.0, wh - 6, hex(0x8A6D3B), 3.0); R(W / 2 - 19, 19.0, 38.0, wh - 11, hex(0xF8E070))   // ios: Views/HoldingsView.swift:2536
            R(W / 2 - 15, 23.0, 14.0, 30.0, hex(0xFFF6C0)); R(W / 2 + 11, 19 + wh * 0.45, 4.0, 4.0, hex(0x8A6D3B))   // ios: Views/HoldingsView.swift:2537
            cat(W - 70, 3.0)   // ios: Views/HoldingsView.swift:2538
            for (hy in listOf(0.32, 0.52, 0.72)) { hedge(0.0, H * hy, fx); hedge(fx + fw, H * hy, W - fx - fw) }   // ios: Views/HoldingsView.swift:2539
            val lx = fx + 4; val ly = H * 0.3
            O(lx - 26, ly + 52, 64.0, 28.0, hex(0xFFE678, 0.25f)); shadow(lx - 3, ly + 62, 20.0, 7.0)   // ios: Views/HoldingsView.swift:2541
            R(lx + 5, ly + 8, 4.0, 58.0, hex(0x404850)); R(lx, ly, 14.0, 12.0, hex(0x303840), 4.0); R(lx + 3, ly + 3, 8.0, 7.0, hex(0xFFE890), 3.0)   // ios: Views/HoldingsView.swift:2542
            rock(W * 0.66, H * 0.8); tree(-14.0, H - 70, 1.0); tree(W - 46, H - 66, 0.9)   // ios: Views/HoldingsView.swift:2543
        }
        else -> {}   // ios: Views/HoldingsView.swift:2544
    }
}
