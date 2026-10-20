package com.naeilo.app.ui.goal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naeilo.app.AppModel
import com.naeilo.app.core.cformat
import com.naeilo.app.core.calc.Basket
import com.naeilo.app.core.calc.GoalSim
import com.naeilo.app.core.calc.GoalStep
import com.naeilo.app.core.calc.GoalWeek
import com.naeilo.app.core.calc.MissionRoute
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.autoGoal
import com.naeilo.app.core.calc.confirmGoalMonth
import com.naeilo.app.core.calc.editLastMonth
import com.naeilo.app.core.calc.eta
import com.naeilo.app.core.calc.focusName
import com.naeilo.app.core.calc.focusRow
import com.naeilo.app.core.calc.goalAvailable
import com.naeilo.app.core.calc.goalMixes
import com.naeilo.app.core.calc.goalMonthSteps
import com.naeilo.app.core.calc.goalNo
import com.naeilo.app.core.calc.goalPresets
import com.naeilo.app.core.calc.goalReached
import com.naeilo.app.core.calc.goalSaved
import com.naeilo.app.core.calc.goalStart
import com.naeilo.app.core.calc.goalSteps
import com.naeilo.app.core.calc.goalToDate
import com.naeilo.app.core.calc.goalTotal
import com.naeilo.app.core.calc.mineMix
import com.naeilo.app.core.calc.mixes
import com.naeilo.app.core.calc.monthEditable
import com.naeilo.app.core.calc.monthsTo
import com.naeilo.app.core.calc.neededMonthly
import com.naeilo.app.core.calc.noviceOwn
import com.naeilo.app.core.calc.selectedMix
import com.naeilo.app.core.calc.shift
import com.naeilo.app.core.calc.simulate
import com.naeilo.app.core.calc.suggestNextGoal
import com.naeilo.app.core.calc.switchRoute
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.data.monthGateText
import com.naeilo.app.net.last
import com.naeilo.app.pending.BrickGameHub
import com.naeilo.app.pending.GamePause
import com.naeilo.app.pending.WaveHub
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.DashedCard
import com.naeilo.app.ui.EaseOut
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.GuideBubble
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.InputField
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.LocalTextScale
import com.naeilo.app.ui.NavBack
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.Pose
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.SF
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.ScreenTitle
import com.naeilo.app.ui.ShareCardSheet
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.board.BasketList
import com.naeilo.app.ui.board.MissionScreen
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.home.FlowRow
import com.naeilo.app.ui.home.SampleNote
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.stiffness
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// 목표 달성 루트 화면 (Views/GoalViews.swift: 시안 g1·g1r·g3·g3r·gt·gi·gp1r, 목표 미션 판). 설명은 계산값, 종목 추천 문구 없음.
// 경로 계산(simulate·neededMonthly·goalPresets·shift·monthsTo)은 derivedStateOf 로 읽은 값이 바뀔 때만 다시 센다.
// 그래프·칸은 Canvas 하나(drawWithCache)로 그리고, 미니 벽돌깨기는 withFrameNanos 로 그리기 단계만 다시 그린다.

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

/** .overlay(RoundedRectangle(cornerRadius: r).stroke(c, lineWidth: w, dash:)) — 선은 가장자리 가운데 (SwiftUI stroke 와 같게) */
private fun Modifier.strokeR(c: Color, radius: Float, width: Float = 1f, dash: FloatArray? = null): Modifier = drawBehind {
    val pe = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { i -> d[i].dp.toPx() }) }
    drawRoundRect(c, cornerRadius = CornerRadius(radius.dp.toPx()), style = Stroke(width.dp.toPx(), pathEffect = pe))
}

/** .overlay(alignment: .bottom) { Divider() } */
private fun Modifier.bottomLine(c: Color): Modifier = drawBehind {
    drawLine(c, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
}

/** Image(systemName:): SF 표(ui/Controls.kt SF)에 아직 없는 기호는 같은 크기의 빈 칸으로 둔다 (표에 넣으면 바로 보인다) */
@Composable
private fun SymSafe(sf: String, size: Float, color: Color) {
    if (SF.containsKey(sf)) Sym(sf, size, color) else Box(Modifier.size((size * 1.25f * LocalTextScale.current.ratio).dp))
}

/** .onChange(of: v) { _, v in } : 처음 값은 건너뛰고 바뀔 때만 */
@Composable
private fun <V> OnChange(v: V, f: (V) -> Unit) {
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(v) { if (first[0]) first[0] = false else f(v) }
}

/** .fullScreenCover: 아래에서 올라와 화면을 다 덮는다. 안에서 닫기는 LocalNavBack.current?.pop */
@Composable
private fun FullCover(show: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (!show) return
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val y = remember { Animatable(1f) }
        LaunchedEffect(Unit) { y.animateTo(0f, tween(350, easing = EaseOut)) }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = y.value * size.height }.background(Theme.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            CompositionLocalProvider(LocalNavBack provides NavBack(null, onDismiss)) { content() }
        }
    }
}

// ios: Views/GoalViews.swift:6
private val mixColor: Map<String, Color> @Composable get() = mapOf("mine" to Theme.purple, "index" to hexD(0x1F3A7A, 0x7F9BE0), "grow" to Theme.green, "steady" to Theme.blue)
// ios: Views/GoalViews.swift:7
private val compColor: Map<String, Color> @Composable get() = mapOf("T" to Theme.purple, "I" to hexD(0x1F3A7A, 0x7F9BE0), "G" to Theme.green, "B" to Theme.blue, "C" to hexD(0xBFC6CD, 0x56616C))
// ios: Views/GoalViews.swift:8
private val compName: Map<String, String> = mapOf("T" to "비중 1위 종목", "I" to "지수", "G" to "성장 지속", "B" to "버팀목", "C" to "현금")

// 목표까지 길: 띠 = 100번 중 90번, 선 = 보통의 경우, 회색 점선 = 넣은 원금, 빨간 점선 = 목표
// ios: Views/GoalViews.swift:11
@Composable
fun GoalPathChart(
    sims: List<Triple<GoalSim, Color, Boolean>>,   // ios: Views/GoalViews.swift:12 (결과, 색, 고른 것)
    goal: Double,   // ios: Views/GoalViews.swift:13
    modifier: Modifier = Modifier,
    showBand: Boolean = true,   // ios: Views/GoalViews.swift:14
    showPrincipal: Boolean = true,   // ios: Views/GoalViews.swift:15
) {
    // ios: Views/GoalViews.swift:16
    val teal = Theme.teal; val muted = Theme.muted
    // ios: Views/GoalViews.swift:17,34
    Spacer(modifier.semantics { contentDescription = "목표까지 평가액이 지날 길" }.drawWithCache {
        // ios: Views/GoalViews.swift:18
        val main = sims.firstOrNull { it.third }?.first ?: sims.firstOrNull()?.first ?: return@drawWithCache onDrawBehind {}
        val n = main.med.size - 1; val ymax = max(goal * 1.3, (sims.maxOfOrNull { it.first.med.lastOrNull() ?: 0.0 } ?: 0.0) * 1.25)   // ios: Views/GoalViews.swift:19
        val X = { i: Int -> i.toFloat() / max(1, n).toFloat() * size.width }   // ios: Views/GoalViews.swift:20
        val Y = { v: Double -> size.height - (min(v, ymax) / ymax).toFloat() * size.height }   // ios: Views/GoalViews.swift:21
        fun line(a: List<Double>): Path = Path().apply { a.forEachIndexed { i, v -> if (i == 0) moveTo(X(i), Y(v)) else lineTo(X(i), Y(v)) } }   // ios: Views/GoalViews.swift:22
        // ios: Views/GoalViews.swift:23
        val band: Path? = if (showBand) {
            line(main.hi).apply {   // ios: Views/GoalViews.swift:24
                for (i in n downTo 0 step 1) lineTo(X(i), Y(main.lo[i]))   // ios: Views/GoalViews.swift:25
                close()   // ios: Views/GoalViews.swift:26
            }
        } else null
        val gy = Y(goal); val goalA = Offset(0f, gy)   // ios: Views/GoalViews.swift:29
        val goalB = Offset(size.width, gy)
        val principal = if (showPrincipal) line(main.principal) else null
        val meds = sims.map { (s, c, on) -> Triple(line(s.med), c, on) }
        onDrawBehind {
            if (band != null) drawPath(band, teal.copy(alpha = 0.15f))   // ios: Views/GoalViews.swift:27
            drawLine(hex(0xC8352E), goalA, goalB, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))   // ios: Views/GoalViews.swift:30
            if (principal != null) drawPath(principal, muted, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))   // ios: Views/GoalViews.swift:31
            for ((p, c, on) in meds) drawPath(p, c.copy(alpha = if (on) 1f else 0.55f), style = Stroke((if (on) 3f else 1.5f).dp.toPx()))   // ios: Views/GoalViews.swift:32
        }
    })
}

// ios: Views/GoalViews.swift:38
@Composable
private fun legend(c: Color, t: String) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:39
    Box(Modifier.size(12.dp, 12.dp).background(c, RoundedCornerShape(3.dp)))
    T(t, style = appFont(12f), color = Theme.sub)   // ios: Views/GoalViews.swift:40
}

// ios: Views/GoalViews.swift:43,44,45,46
@Composable
private fun Page(kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    // ios: Views/GoalViews.swift:47,48,55,56 (inline 제목 막대는 NavStack 몫)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen(Modifier.padding(top = 8.dp)) {   // ios: Views/GoalViews.swift:53
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/GoalViews.swift:49
                ScreenTitle(kicker = kicker, title = title)   // ios: Views/GoalViews.swift:50
                content()   // ios: Views/GoalViews.swift:51
            }
        }
    }
}

// ios: Views/GoalViews.swift:60,61,62,63,64
@Composable
private fun BoardLinks(m: AppModel, next: String?, to: MissionRoute?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/GoalViews.swift:65
        if (next != null && to != null) PrimaryButton(title = next) { m.boardPath = m.boardPath + to }   // ios: Views/GoalViews.swift:66
        // ios: Views/GoalViews.swift:67,68
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).plainClickable { m.boardPath = emptyList() }, contentAlignment = Alignment.Center) {
            T("미션 판으로", style = appFont(15f, FontWeight.SemiBold), color = Theme.teal)
        }
    }
}

// ios: Views/GoalViews.swift:73
@Composable
private fun inputField(label: String, text: String, onText: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/GoalViews.swift:74
        T(label, style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/GoalViews.swift:75
        // ios: Views/GoalViews.swift:77,78,79,80
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).background(Theme.card, RoundedCornerShape(10.dp)).strokeR(Theme.border, 10f, 2f).padding(horizontal = 12.dp).semantics { contentDescription = label }, contentAlignment = Alignment.CenterStart) {
            InputField(text, onText, placeholder = "", number = true, style = appFont(18f, FontWeight.SemiBold))   // ios: Views/GoalViews.swift:76
        }
    }
}

/** 금액 끝의 "원"을 뗀다 (826만원 → 826만, 5.1억원 → 5.1억) */
private fun noWon(s: String): String = if (s.endsWith("원")) s.dropLast(1) else s   // ios: Views/GoalViews.swift:85

// ios: Views/GoalViews.swift:87 (private extension AppModel)
/** 목표일 짧게 (2029.10.5) */
private val AppModel.goalToShort: String get() = DateTimeFormatter.ofPattern("yyyy.M.d").withZone(ZoneId.systemDefault()).format(goalToDate)   // ios: Views/GoalViews.swift:89

// ── 목표 미션 판 ──

// ios: Views/GoalViews.swift:94,95,98
@Composable
fun GoalBoardView(m: AppModel) {
    var openDone by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:96
    val steps = m.goalSteps; val done = steps.filter { m.gDone.contains(it.id) }   // ios: Views/GoalViews.swift:99
    // ios: Views/GoalViews.swift:100,122,123 (이동 막대 숨김: NavStack 은 첫 화면에 막대를 그리지 않는다)
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen {   // ios: Views/GoalViews.swift:120
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {   // ios: Views/GoalViews.swift:101
                Box(Modifier.outset(16.dp)) { AppHeader(tab = "미션") }   // ios: Views/GoalViews.swift:102
                SampleNote(m)   // ios: Views/GoalViews.swift:103
                // 맨 위는 인터미션 카드 (목표 카드·제목은 뺌)
                steps.firstOrNull { it.inter }?.let { inter -> InterCard(m, inter) }   // ios: Views/GoalViews.swift:105
                if (!m.interDone && m.playOn) MonthCheckIn(m)   // ios: Views/GoalViews.swift:106
                StartList(m, steps.filter { !it.inter }, done = done.filter { !it.inter }.size, openDone = openDone) { openDone = it }   // ios: Views/GoalViews.swift:107
                // ios: Views/GoalViews.swift:108
                if (m.goalReached) {
                    // ios: Views/GoalViews.swift:109
                    Card(padding = 18.dp) {
                        GuideBubble(m, Pose.cheer, "목표 ${AppModel.wonK(m.gK)}에 닿았어요! 여기까지 온 길, 축하해요.")   // ios: Views/GoalViews.swift:110
                        T("지금 ${AppModel.wonK(m.trackValue / 1e4)}에서 다음 목표 · 3년 확률 50% 근처", style = appFont(14f), color = Theme.sub)   // ios: Views/GoalViews.swift:111
                        PrimaryButton(title = "다음 목표 정하기") { m.suggestNextGoal(); m.boardPath = m.boardPath + MissionRoute.g1 }   // ios: Views/GoalViews.swift:112
                    }
                }
                val next = steps.firstOrNull { !m.gDone.contains(it.id) && m.goalAvailable(it) }   // ios: Views/GoalViews.swift:115
                if (next != null && !next.inter) NextCard(m, next, steps = steps.filter { !it.inter })   // ios: Views/GoalViews.swift:116
                if (m.playUnlocked) GoalCellsCard(m) else LockedPlay()   // ios: Views/GoalViews.swift:118
            }
        }
    }
}

/** 미션 판에서 여는 화면 (navigationDestination for: MissionRoute). App.kt 미션 탭 NavStack 의 page 로 쓴다 */
fun goalBoardPage(m: AppModel, route: MissionRoute): NavPage = com.naeilo.app.ui.board.missionPage(m, route)   // MissionScreen(m, route = route) 를 제목·막대와 함께   // ios: Views/GoalViews.swift:124

// 인터미션 체크인 (색 없는 박스): 첫 달은 바로, 지난 달은 다음 달이 열리기 전까지 다시 고를 수 있다
// ios: Views/GoalViews.swift:128
@Composable
private fun MonthCheckIn(m: AppModel) {
    val edit = m.monthEditable; val k = if (edit) m.gWeeks.size - 1 else m.gWeeks.size; val step = AppModel.goalMonthSteps[min(k, 2)]   // ios: Views/GoalViews.swift:129
    // ios: Views/GoalViews.swift:166,167,168
    val box = Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).strokeR(Theme.border, 18f).padding(16.dp)
    Column(box, verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:130
        T("${k + 1}달째 체크인", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/GoalViews.swift:131
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:132
            val chip = Modifier.background(Theme.mint, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp)   // ios: Views/GoalViews.swift:134
            T(step.whereText, chip, style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed)   // ios: Views/GoalViews.swift:133
            T(step.task, style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)   // ios: Views/GoalViews.swift:135
        }
        T("이번 달 계획대로?", style = appFont(14f), color = Theme.ink)   // ios: Views/GoalViews.swift:137
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:138
            GoalWeek.entries.forEach { p ->   // ios: Views/GoalViews.swift:139
                WeekButton(m, p, on = if (edit) m.gWeeks[k] == p else m.gWeekCur == p, edit = edit)   // ios: Views/GoalViews.swift:140
            }
        }
        if (edit) T("다음 달 열리기 전까지 수정 가능", style = appFont(12f), color = Theme.muted)   // ios: Views/GoalViews.swift:153
        val wait = m.monthGateText   // ios: Views/GoalViews.swift:154
        if (wait != null) WaitLabel(wait)
        else {   // ios: Views/GoalViews.swift:157
            // ios: Views/GoalViews.swift:158,163 withAnimation: 체크인하면 버튼이 흐려지며 사라진다
            AnimatedVisibility(m.gWeekCur != null, enter = fadeIn(), exit = fadeOut()) { Box(Modifier.fillMaxWidth().plainClickable { m.confirmGoalMonth() }
                .defaultMinSize(minHeight = 44.dp).background(Theme.teal, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {   // ios: Views/GoalViews.swift:160,162
                T("체크인", style = appFont(14f, FontWeight.SemiBold), color = Color.White)   // ios: Views/GoalViews.swift:159,161
            } }
        }
    }
}

/** 이번 달 고르기 버튼 (미션 판 체크인·인터미션 화면이 같은 모양) */
// ios: Views/GoalViews.swift:141,150,953,962
@Composable
private fun RowScope.WeekButton(m: AppModel, p: GoalWeek, on: Boolean, edit: Boolean) = Column(
    Modifier.weight(1f).plainClickable { if (edit) m.editLastMonth(p) else m.gWeekCur = p }
        .defaultMinSize(minHeight = 64.dp)   // ios: Views/GoalViews.swift:147,959
        .background(if (on) p.color.copy(alpha = 0.15f) else Theme.card, RoundedCornerShape(12.dp))   // ios: Views/GoalViews.swift:148,960
        .strokeR(p.color, 12f, if (on) 3f else 2f)   // ios: Views/GoalViews.swift:149,961
        .padding(vertical = 8.dp),   // ios: Views/GoalViews.swift:146,958 (글자색 ink)
    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,   // ios: Views/GoalViews.swift:142,954
) {
    T(p.label, style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/GoalViews.swift:143,955
    T("월 " + AppModel.wonK(m.gM * p.factor), style = appFont(11f), color = Theme.sub)   // ios: Views/GoalViews.swift:144,956
}

/** 다음 달이 열리기 전: 남은 날 (시계 기호) */
// ios: Views/GoalViews.swift:155,156,968,969
@Composable
private fun WaitLabel(wait: String) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    SymSafe("clock", 13f, Theme.sub); T(wait, style = appFont(13f), color = Theme.sub)
}

// ios: Views/GoalViews.swift:171
@Composable
private fun LockedPlay() {
    DashedCard {   // ios: Views/GoalViews.swift:172
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Sym("lock", 15f, Theme.sub); T("목표 100칸", style = appFont(15f, FontWeight.Bold)) }   // ios: Views/GoalViews.swift:173
        T("시작 3단계 뒤 열림 · 칸 채우기·블록·파도", style = appFont(13f))   // ios: Views/GoalViews.swift:174
    }
}

/** 다음 미션: 진한 초록 블록 */
// ios: Views/GoalViews.swift:179
@Composable
private fun NextCard(m: AppModel, next: GoalStep, steps: List<GoalStep>) {
    val idx = steps.indexOfFirst { it.id == next.id }.let { if (it < 0) 0 else it } + 1   // ios: Views/GoalViews.swift:180
    val box = Modifier.fillMaxWidth().background(Theme.missionGreen, RoundedCornerShape(20.dp))   // ios: Views/GoalViews.swift:193
    Fg(Color.White) {   // ios: Views/GoalViews.swift:191
        Column(box.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:181,192
            T("다음 미션 · ${idx}/${steps.size}", style = appFont(13f, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.8f))   // ios: Views/GoalViews.swift:182
            T(next.title, style = appFont(20f, FontWeight.Bold))   // ios: Views/GoalViews.swift:183
            T("열리는 것: ${next.reward}", style = appFont(14f), color = Color.White.copy(alpha = 0.85f))   // ios: Views/GoalViews.swift:184
            val btn = Modifier.defaultMinSize(minHeight = 48.dp).background(Color.White, RoundedCornerShape(14.dp))   // ios: Views/GoalViews.swift:187,188
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().plainClickable { m.boardPath = m.boardPath + next.to }.then(btn), contentAlignment = Alignment.Center) {   // ios: Views/GoalViews.swift:185,189
                T("시작", style = appFont(16f, FontWeight.Bold), color = Theme.missionGreen)   // ios: Views/GoalViews.swift:186
            }
        }
    }
}

/** 처음 석 달 체크인 카드: 달마다 계획대로 넣었는지와 받은 위젯 */
// ios: Views/GoalViews.swift:197
@Composable
private fun InterCard(m: AppModel, b: GoalStep) {
    val done = m.gDone.contains(b.id); val avail = m.goalAvailable(b); val n = m.gWeeks.size   // ios: Views/GoalViews.swift:198
    val fg = if (done || avail) Color.White else Theme.sub2   // ios: Views/GoalViews.swift:226
    val bg = Modifier.background(if (done || avail) Theme.night else Theme.track, RoundedCornerShape(18.dp))   // ios: Views/GoalViews.swift:228
    // ios: Views/GoalViews.swift:199,230
    val tap = if (done || avail) Modifier.plainClickable { if (done || avail) m.boardPath = m.boardPath + b.to } else Modifier
    val label = Modifier.semantics(mergeDescendants = true) { contentDescription = "처음 석 달 체크인, ${n}/3달" }   // ios: Views/GoalViews.swift:231
    Fg(fg) {
        Column(Modifier.fillMaxWidth().then(tap).then(label).then(bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:200,227
            Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:201
                T("석 달 체크인", style = appFont(12f, FontWeight.Bold), color = Theme.mint)   // ios: Views/GoalViews.swift:202
                Spacer(Modifier.weight(1f))   // ios: Views/GoalViews.swift:203
                if (done) T("완료 ✓", style = appFont(12f, FontWeight.Bold), color = Theme.yellow)   // ios: Views/GoalViews.swift:204
                else if (!avail) Sym("lock", 12f, fg)   // ios: Views/GoalViews.swift:205
            }
            // ios: Views/GoalViews.swift:207
            if (!(done || n >= 3)) {
                T(if (avail) "월 1회 계획대로 체크 · ${n}/3달" else "시작 미션 뒤 열림", style = appFont(16f, FontWeight.Bold))   // ios: Views/GoalViews.swift:208,209
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:211
                for (i in 0 until 3) {   // ios: Views/GoalViews.swift:212
                    val pick: GoalWeek? = if (i < n) m.gWeeks[i] else null   // ios: Views/GoalViews.swift:213
                    val cur = avail && i == n
                    val c = if (pick != null) Color.White else if (cur) Theme.yellow else Theme.muted   // ios: Views/GoalViews.swift:219
                    val fill = Modifier.background(pick?.color ?: Color.Transparent, RoundedCornerShape(10.dp))   // ios: Views/GoalViews.swift:220
                    // ios: Views/GoalViews.swift:221,222
                    val line = Modifier.strokeR(pick?.color ?: (if (cur) Theme.yellow else Theme.slate), 10f, 2f, dash = if (pick == null) floatArrayOf(5f, 4f) else null)
                    // ios: Views/GoalViews.swift:214,218
                    Column(Modifier.weight(1f).defaultMinSize(minHeight = 60.dp).then(fill).then(line), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                        T("${i + 1}달", style = appFont(11f), color = c)   // ios: Views/GoalViews.swift:215
                        T(pick?.label ?: (if (cur) "이번 달" else "–"), style = appFont(13f, FontWeight.Bold), color = c)   // ios: Views/GoalViews.swift:216
                    }
                }
            }
        }
    }
}

/** 시작 미션: 접힌 한 줄, 펼치면 번호 목록. 끝냈거나 열린 미션은 눌러서 들어간다 */
// ios: Views/GoalViews.swift:235
@Composable
private fun StartList(m: AppModel, list: List<GoalStep>, done: Int, openDone: Boolean, setOpen: (Boolean) -> Unit) {
    val all = done == list.size   // ios: Views/GoalViews.swift:236
    val bg = Modifier.background(if (all) Theme.tealBg else Theme.card, RoundedCornerShape(14.dp))   // ios: Views/GoalViews.swift:267
    val line = Modifier.strokeR(Theme.border, 14f)   // ios: Views/GoalViews.swift:268
    Column(Modifier.fillMaxWidth().then(bg).then(line), verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Views/GoalViews.swift:237
        val rowMod = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp)   // ios: Views/GoalViews.swift:245
        // ios: Views/GoalViews.swift:238,246 withAnimation: 펼친 목록은 AnimatedVisibility 로 나타난다
        StartHeader(all, done, list.size, openDone, Modifier.fillMaxWidth().plainClickable { setOpen(!openDone) }.then(rowMod))
        AnimatedVisibility(openDone, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column { list.forEachIndexed { i, b -> StartItem(m, i, b) } }   // ios: Views/GoalViews.swift:247,248
        }
    }
}

// ios: Views/GoalViews.swift:239
@Composable
private fun StartHeader(all: Boolean, done: Int, n: Int, openDone: Boolean, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    val st = appFont(14f, FontWeight.SemiBold); val hc = if (all) Theme.tealDark else Theme.ink   // ios: Views/GoalViews.swift:244
    T(if (all) "✓ 시작 미션 ${done}/${n}" else "시작 미션 ${done}/${n}", style = st.copy(fontWeight = FontWeight.Bold), color = hc)   // ios: Views/GoalViews.swift:240
    Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/GoalViews.swift:241
    Sym(if (openDone) "chevron.up" else "chevron.down", st.fontSize.value, hc, scaled = false)   // ios: Views/GoalViews.swift:242
}

// ios: Views/GoalViews.swift:249
@Composable
private fun StartItem(m: AppModel, i: Int, b: GoalStep) {
    val ok = m.gDone.contains(b.id); val avail = m.goalAvailable(b)
    HDivider(Theme.border)   // ios: Views/GoalViews.swift:250
    val sz = 13f; val rowMod = Modifier.defaultMinSize(minHeight = 44.dp).padding(horizontal = 14.dp)   // ios: Views/GoalViews.swift:260
    // ios: Views/GoalViews.swift:251,262
    val tap = if (ok || avail) Modifier.plainClickable { if (ok || avail) m.boardPath = m.boardPath + b.to } else Modifier
    val label = Modifier.semantics(mergeDescendants = true) { contentDescription = "시작 미션 ${i + 1}: ${b.title}" + (if (ok) ", 끝냄" else "") }   // ios: Views/GoalViews.swift:263
    Row(Modifier.fillMaxWidth().then(tap).then(label).then(rowMod), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:252
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) { T("${i + 1}", style = appFont(13f, FontWeight.Bold), color = Theme.sub) }   // ios: Views/GoalViews.swift:253
        T(b.title, style = appFont(14f), color = if (ok || avail) Theme.ink else Theme.sub2)   // ios: Views/GoalViews.swift:254
        Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/GoalViews.swift:255
        if (ok) Sym("checkmark", sz, Theme.teal)   // ios: Views/GoalViews.swift:256
        else if (!avail) Sym("lock", sz, Theme.sub2)   // ios: Views/GoalViews.swift:257
        else Sym("chevron.right", sz, Theme.sub)   // ios: Views/GoalViews.swift:258
    }
}

// 목표까지 100칸 (1칸 = 목표의 1/100): 진한 칸 = 내가 넣은 돈, 민트 = 투자로 불어난 돈, 주황 = 잠시 줄어든 돈
// 공유 카드·벽돌깨기는 1000칸 그대로 (값만 넘긴다)
// ios: Views/GoalViews.swift:274,275,276
@Composable
fun GoalCellsCard(m: AppModel) {
    var showGame by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:350
    var showWave by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:351
    var showShare by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:352
    val ctx = LocalContext.current
    val game = remember { MiniBreakout(ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager) }   // ios: Views/GoalViews.swift:353
    DisposableEffect(game) { onDispose { game.stop() } }
    var playing by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:354
    val scenePhase = m.active   // ios: Views/GoalViews.swift:355 (앱이 앞에 있는지)
    var party by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:356 안 죽고 다 깨면 친구들이 올라와 축하 (숨은 재미)
    val reduce = LocalReduceMotion.current

    // ios: Views/GoalViews.swift:358
    fun startMini(fV: Double, mine: Double) {
        val n = ceil(fV).toInt()   // ios: Views/GoalViews.swift:359
        if (n == 0 || reduce) { showGame = true; return }   // ios: Views/GoalViews.swift:360 (동작 줄이기면 미니 대신 본 게임)
        game.setup(n = n, mine = mine)   // ios: Views/GoalViews.swift:361
        playing = true   // ios: Views/GoalViews.swift:362
    }

    val K = m.gK * 1e4; val unit = K / 100   // ios: Views/GoalViews.swift:277
    val tkV = m.trackValue   // ios: Views/GoalViews.swift:278
    val tkP = if (m.route == Route.novice) tkV / 1.02 else tkV * max(0.8, min(1.25, m.cost / max(1.0, m.total)))   // ios: Views/GoalViews.swift:279
    val fV = min(100.0, tkV / max(1.0, unit)); val fP = min(100.0, tkP / max(1.0, unit))   // ios: Views/GoalViews.swift:280
    val mine = min(fV, fP); val lost = max(0.0, fP - fV); val gain = max(0.0, tkV - tkP)   // ios: Views/GoalViews.swift:281
    // 공유 카드·벽돌깨기용 1000칸
    val cl = { v: Double -> max(0, min(1000, floor(v / max(1.0, K / 1000)).toInt())) }   // ios: Views/GoalViews.swift:283
    val nV = cl(tkV); val nP = cl(tkP); val nMine = min(nV, nP); val nY = cl(tkV / (1 + m.todayMove))   // ios: Views/GoalViews.swift:284
    val pct = (tkV / max(1.0, K) * 100).toInt(); @Suppress("UNUSED_VARIABLE") val stone = listOf(75, 50, 25).firstOrNull { pct >= it }   // ios: Views/GoalViews.swift:285
    // ios: Views/GoalViews.swift:300,321 박스·패들 칸을 누르면 시작
    val tapStart = rememberUpdatedState { if (!playing && !party) startMini(fV, mine) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/GoalViews.swift:286
        // ios: Views/GoalViews.swift:287,342,343
        Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth()) {   // ios: Views/GoalViews.swift:288
                T("${AppModel.wonK(m.gK)}까지 100칸", Modifier.alignByBaseline(), style = appFont(18f, FontWeight.Bold))   // ios: Views/GoalViews.swift:289
                Spacer(Modifier.weight(1f))   // ios: Views/GoalViews.swift:290
                T(if (fV >= 100) "100칸 완성!" else "1칸 = ${AppModel.man(unit)}", Modifier.alignByBaseline(), style = appFont(14f), color = Theme.sub)   // ios: Views/GoalViews.swift:291
            }
            // 박스는 60% 폭, 정보는 오른쪽
            SplitRow(ratio = 0.6f, spacing = 14.dp) {   // ios: Views/GoalViews.swift:294
                // 100칸이 곧 벽돌. 아래 빈 곳을 누르면 공 발사, 끌면 패들
                // ios: Views/GoalViews.swift:296,297
                BrickBox(mine = mine, filled = fV, lost = lost, g = game, on = playing, setOn = { playing = it }, end = { clear -> if (clear) party = true else showGame = true },
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { tapStart.value() } }   // ios: Views/GoalViews.swift:299,300
                        .semantics { contentDescription = "100칸 중 ${fV.toInt()}칸, 목표의 ${pct}%" })   // ios: Views/GoalViews.swift:301
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:302,313
                    legend(Theme.teal, null, "넣은 돈", AppModel.man(min(tkV, tkP)))   // ios: Views/GoalViews.swift:303
                    if (lost > 0) legend(GoalCellsCard.lostC, null, "줄어든 돈", "−" + AppModel.man(tkP - tkV))   // ios: Views/GoalViews.swift:304
                    else legend(GoalCellsCard.mintC, null, "불어난 돈", "+" + AppModel.man(gain))   // ios: Views/GoalViews.swift:305
                    legend(Theme.card, Theme.gold, "마지막 칸 = 목표", null)   // ios: Views/GoalViews.swift:306
                    action("공유하기", "square.and.arrow.up", fill = false) { showShare = true }   // ios: Views/GoalViews.swift:307
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:308
                        action("블록", "square.grid.3x3.fill", fill = true, tight = true, modifier = Modifier.weight(1f)) { showGame = true }   // ios: Views/GoalViews.swift:309
                        action("파도", "water.waves", fill = true, tight = true, modifier = Modifier.weight(1f)) { showWave = true }   // ios: Views/GoalViews.swift:310
                    }
                }
            }
            // 패들 조작 칸: 손가락이 패들을 가리지 않게 박스 아래 빈 곳에서 끈다
            // 평소엔 톡 눌러 시작(위아래 스크롤은 그대로), 게임 중에만 끌어서 패들
            Box(Modifier.fillMaxWidth().height(44.dp)   // ios: Views/GoalViews.swift:316,318,327
                // ios: Views/GoalViews.swift:319,320
                .pointerInput(Unit) { detectTapGestures { p -> game.px = max(0.0, min(1.0, p.x.toDouble() / max(1, size.width))); tapStart.value() } }
                // ios: Views/GoalViews.swift:323,325 DragGesture(minimumDistance: 0): 누르는 순간부터 패들
                .pointerInput(playing) { if (playing) awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val c = currentEvent.changes.getOrNull(0) ?: break
                        game.px = max(0.0, min(1.0, c.position.x.toDouble() / max(1, size.width))); c.consume()   // ios: Views/GoalViews.swift:324
                    } while (awaitPointerEvent().changes.any { it.pressed })
                } })
            // 캐릭터가 자랑하자고 권한다 (누르는 곳 아님, 공유는 오른쪽 버튼). 다 깨면 친구들이 이 자리를 채운다
            if (party) MiniParty(Modifier.fillMaxWidth()) else {   // ios: Views/GoalViews.swift:329
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:330
                    CharPixel(id = m.homeFriendShown, width = 42.dp, height = 60.dp, modifier = Modifier.charIdle().clearAndSetSemantics {}, model = m)   // ios: Views/GoalViews.swift:331
                    val bubble = RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp)   // ios: Views/GoalViews.swift:338
                    // ios: Views/GoalViews.swift:332,336,337
                    Column(Modifier.weight(1f).background(Theme.mintBg, bubble).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T(m.friendName(m.homeFriendShown), style = appFont(12f, FontWeight.SemiBold), color = Theme.blue)   // ios: Views/GoalViews.swift:333
                        T(GoalCellsCard.boast(pct, fV.toInt()), style = appFont(14f))   // ios: Views/GoalViews.swift:334
                    }
                }
            }
        }
    }
    LaunchedEffect(scenePhase) { if (!scenePhase) party = false }   // ios: Views/GoalViews.swift:345 다른 앱으로 가면 친구들 퇴장
    Sheet(showShare, onDismiss = { showShare = false }) { ShareCardSheet(m, party = party, dismiss = { showShare = false }) }   // ios: Views/GoalViews.swift:346
    FullCover(showGame, { showGame = false }) { BrickGameHub(m, filled = nV, floor = nMine, yesterday = nY) }   // ios: Views/GoalViews.swift:347
    FullCover(showWave, { showWave = false }) { WaveHub(m) }   // ios: Views/GoalViews.swift:348
}

/** GoalCellsCard 의 static 값·함수 */
object GoalCellsCard {
    /** 진행만큼 자랑하자는 한마디 */
    // ios: Views/GoalViews.swift:366,367
    fun boast(pct: Int, n: Int): String = when {
        pct >= 100 -> "100칸 완성! 이건 무조건 자랑이야."   // ios: Views/GoalViews.swift:368
        pct >= 75 -> "거의 다 왔어. 지금 자랑할 때야!"   // ios: Views/GoalViews.swift:369
        pct >= 50 -> "반 넘었어! 보여 주면 다들 놀랄걸."   // ios: Views/GoalViews.swift:370
        pct >= 25 -> "${pct}%나 왔어. 이건 자랑해야지."   // ios: Views/GoalViews.swift:371
        else -> if (n > 0) "벌써 ${n}칸 채웠어. 시작한 것도 자랑이야!" else "첫 칸 채우면 바로 자랑하자!"   // ios: Views/GoalViews.swift:372
    }

    val mintC: Color @Composable get() = hexD(0x8FCFC3, 0xA8E6DC)   // ios: Views/GoalViews.swift:387 다크: 넣은 돈(진한 청록)과 구분되게 밝게
    val lostC: Color @Composable get() = hexD(0xFBE3CF, 0x4A3020)   // ios: Views/GoalViews.swift:388
}

// ios: Views/GoalViews.swift:376
@Composable
private fun action(t: String, icon: String, fill: Boolean, modifier: Modifier = Modifier, tight: Boolean = false, go: () -> Unit) {
    val c = if (fill) Color.White else Theme.teal   // ios: Views/GoalViews.swift:380
    val bg = Modifier.background(if (fill) Theme.teal else Color.Transparent, RoundedCornerShape(10.dp))   // ios: Views/GoalViews.swift:381
    val line = Modifier.strokeR(Theme.teal, 10f, 1.5f)   // ios: Views/GoalViews.swift:382
    // TightLabel: 기호와 글자 사이 3, 보통 Label 은 6
    Row(modifier.plainClickable(onClick = go).fillMaxWidth().defaultMinSize(minHeight = 40.dp).then(bg).then(line),   // ios: Views/GoalViews.swift:377,379,384
        horizontalArrangement = Arrangement.spacedBy(if (tight) 3.dp else 6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        SymSafe(icon, 14f, c); FitText(t, style = appFont(14f, FontWeight.Bold), minScale = 0.8f, maxLines = 1, color = c)   // ios: Views/GoalViews.swift:378
    }
}

// 좁은 오른쪽 칸: 색 + 이름, 값은 아래 줄
// ios: Views/GoalViews.swift:391
@Composable
private fun legend(c: Color, stroke: Color?, t: String, v: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/GoalViews.swift:392
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:393
            // ios: Views/GoalViews.swift:394,395,396
            Box(Modifier.size(12.dp, 12.dp).background(c, RoundedCornerShape(3.dp)).strokeR(stroke ?: c, 3f, 1.5f))
            FitText(t, style = appFont(13f), minScale = 0.8f, maxLines = 1, color = Theme.sub)   // ios: Views/GoalViews.swift:397
        }
        if (v != null) FitText(v, style = appFont(15f, FontWeight.Bold), minScale = 0.7f, modifier = Modifier.padding(start = 18.dp), maxLines = 1)   // ios: Views/GoalViews.swift:399
    }
}

/** 미니 벽돌깨기 색 (그리기 단계에서 쓴다) */
class BrickColors(val teal: Color, val mint: Color, val track: Color, val gold: Color)

/// 100칸 박스가 곧 미니 벽돌깨기. 패들은 카드 아래 빈 곳에서 조작. 끝나면 end(다 깼나)
/// 게임 중이고 화면에 보일 때만 매 프레임 그린다 (그리기 단계만 다시, 화면 짜기는 그대로)
// ios: Views/GoalViews.swift:406
@Composable
fun BrickBox(
    mine: Double, filled: Double, lost: Double,   // ios: Views/GoalViews.swift:407
    g: MiniBreakout,   // ios: Views/GoalViews.swift:408
    on: Boolean, setOn: (Boolean) -> Unit,   // ios: Views/GoalViews.swift:409
    end: (Boolean) -> Unit,   // ios: Views/GoalViews.swift:410
    modifier: Modifier = Modifier,
) {
    // ios: Views/GoalViews.swift:411
    val reduce = LocalReduceMotion.current; val tabOn = LocalTabActive.current; val dens = LocalDensity.current.density
    var seen by remember { mutableStateOf(true) }   // 스크롤로 화면 밖에 나가면 멈춘다
    val frame = remember { mutableLongStateOf(0L) }   // 그리기 단계만 읽는 프레임 번호
    val box = remember { DoubleArray(2) }   // 지금 크기 (pt)
    val endNow by rememberUpdatedState(end); val setOnNow by rememberUpdatedState(setOn)
    val cols = BrickColors(teal = Theme.teal, mint = GoalCellsCard.mintC, track = Theme.track, gold = Theme.gold)
    // ios: Views/GoalViews.swift:414 TimelineView(.animation(paused: !on)): 게임 중이고 보일 때만 돈다 (동작 줄이기면 멈춤)
    val animateOn = on && seen && tabOn && !reduce
    LaunchedEffect(animateOn) {
        if (!animateOn) return@LaunchedEffect
        var running = true
        while (running && isActive) {
            withFrameNanos { t ->   // ios: Views/GoalViews.swift:415,416
                val r = g.step(t, box[0], box[1])   // ios: Views/GoalViews.swift:417
                frame.longValue++
                if (r != 0) { running = false; setOnNow(false); endNow(r == 1) }   // ios: Views/GoalViews.swift:418
            }
        }
    }
    // ios: Views/GoalViews.swift:412,424
    Box(modifier.fillMaxWidth().aspectRatio(0.9f)
        .onGloballyPositioned { c -> box[0] = c.size.width / dens.toDouble(); box[1] = c.size.height / dens.toDouble(); seen = c.boundsInWindow().height > 0f }
        .drawWithContent { drawContent(); if (frame.longValue >= 0L) g.draw(this, cells = on, col = cols) },   // ios: Views/GoalViews.swift:420
        contentAlignment = Alignment.TopCenter) {
        if (!on) GoalCellGrid(mine = mine, filled = filled, lost = lost, modifier = Modifier.fillMaxWidth().aspectRatio(1f))   // ios: Views/GoalViews.swift:413
    }
    DisposableEffect(on) { if (on) GamePause.on = true; onDispose { if (on) GamePause.on = false } }   // ios: Views/GoalViews.swift:425 게임 중엔 1분 시세 갱신을 미룬다
}

/** 다 깨면 캐릭터와 친구들이 아래에서 차례로 튀어 올라 축하 */
// ios: Views/GoalViews.swift:430,432
@Composable
fun MiniParty(modifier: Modifier = Modifier) {
    var up by remember { mutableStateOf(false) }   // ios: Views/GoalViews.swift:431
    val ids = Shelter.friends.map { it.id } + listOf("me")   // ios: Views/GoalViews.swift:433 친구 다섯 + 나, 전부 여섯
    val ys = remember { ids.map { Animatable(if (up) 0f else 160f) } }   // ios: Views/GoalViews.swift:438
    val textA = remember { Animatable(if (up) 1f else 0f) }   // ios: Views/GoalViews.swift:449
    // ios: Views/GoalViews.swift:439
    LaunchedEffect(up) { if (up) ids.indices.forEach { i -> launch { delay((i * 0.08 * 1000).toLong()); ys[i].animateTo(0f, spring(dampingRatio = 0.55f, stiffness = stiffness(0.45f))) } } }
    LaunchedEffect(Unit) { up = true; textA.animateTo(1f, tween(300, easing = EaseOut)) }   // ios: Views/GoalViews.swift:453
    // 원래 캐릭터 줄 높이(60)를 넘지 않게: 축하 글은 위 빈 칸에 띄운다 (글자 키워도 탭과 안 겹치게)
    Box(modifier.height(60.dp).clearAndSetSemantics {}, contentAlignment = Alignment.BottomCenter) {   // ios: Views/GoalViews.swift:442,451,452
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/GoalViews.swift:435
            ids.forEachIndexed { i, id ->   // ios: Views/GoalViews.swift:436
                CharPixel(id = id, width = 42.dp, height = 60.dp, modifier = Modifier.offset { IntOffset(0, ys[i].value.dp.roundToPx()) }.charIdle(i))   // ios: Views/GoalViews.swift:437
            }
        }
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopCenter) {   // ios: Views/GoalViews.swift:443
            // ios: Views/GoalViews.swift:446,447,448
            val above = Modifier.layout { mm, c -> val p = mm.measure(c.copy(minHeight = 0)); layout(p.width, p.height) { p.place(0, -p.height - 6.dp.roundToPx()) } }.graphicsLayer { alpha = textA.value }.background(Theme.teal, CapsuleShape)
            FitText("다 깼다! 대단해!", style = appFont(14f, FontWeight.Bold), minScale = 0.6f, maxLines = 1, color = Color.White,   // ios: Views/GoalViews.swift:444
                modifier = above.padding(horizontal = 12.dp, vertical = 6.dp))   // ios: Views/GoalViews.swift:445
        }
    }
}

/** 100칸 미니 벽돌깨기 한 판. 크기는 pt(dp) 단위로 셈하고 그릴 때만 화소로 늘린다 */
// ios: Views/GoalViews.swift:457
class MiniBreakout(private val sm: SensorManager?) : SensorEventListener {
    val alive = BooleanArray(100)   // ios: Views/GoalViews.swift:458
    val bomb = BooleanArray(100)   // ios: Views/GoalViews.swift:459 맞으면 둘레 8칸까지 함께 터진다 (찬 칸의 약 12%)
    var mineN = 0; var left = 0   // ios: Views/GoalViews.swift:460
    var bx = 0.0; var by = 0.0; var vx = 0.0; var vy = 0.0   // ios: Views/GoalViews.swift:461
    var px = 0.5   // ios: Views/GoalViews.swift:462 패들 가운데 (폭 비율)
    var held = true   // ios: Views/GoalViews.swift:463
    var last: Long? = null   // ios: Views/GoalViews.swift:464 (지난 프레임 시각, 나노초)
    // ios: Views/GoalViews.swift:465 기울여서 패들 (본 게임과 같은 감도). 중력 x 를 iOS 부호로 (오른쪽이 내려가면 +)
    @Volatile private var gx: Double? = null
    override fun onSensorChanged(e: SensorEvent) { gx = (-e.values[0] / SensorManager.GRAVITY_EARTH).toDouble() }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    // ios: Views/GoalViews.swift:467
    fun setup(n: Int, mine: Double) {
        for (i in 0 until 100) { alive[i] = i < n; bomb[i] = i < n && Random.nextDouble(0.0, 1.0) < 0.12 }   // ios: Views/GoalViews.swift:468
        left = n; mineN = mine.toInt(); held = true; last = null   // ios: Views/GoalViews.swift:469
        sm?.let { s -> s.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { s.registerListener(this, it, (1.0 / 60 * 1e6).toInt()) } }   // ios: Views/GoalViews.swift:470
    }
    fun stop() { sm?.unregisterListener(this) }   // ios: Views/GoalViews.swift:472 (deinit: 카드가 화면을 떠날 때)

    /** 한 프레임 진행. 0 계속, 1 다 깸, 2 놓침. w·h 는 pt */
    // ios: Views/GoalViews.swift:475
    fun step(now: Long, w: Double, h: Double): Int {
        val gap = 4.0; val c = (w - gap * 9) / 10 + gap   // ios: Views/GoalViews.swift:476
        val py = h - 6; val pw = w * 0.28   // ios: Views/GoalViews.swift:477
        val dt = min(1.0 / 30, last?.let { (now - it) / 1e9 } ?: 0.0); last = now   // ios: Views/GoalViews.swift:478
        // 좌우로 약 24도 기울이면 끝까지. 센서가 없으면 손가락으로
        gx?.let { g ->   // ios: Views/GoalViews.swift:480
            val goal = 0.5 + max(-1.0, min(1.0, g / 0.4)) * 0.5   // ios: Views/GoalViews.swift:481
            px += (goal - px) * min(1.0, dt * 14)   // ios: Views/GoalViews.swift:482
        }
        val x = max(pw / 2, min(w - pw / 2, px * w))   // ios: Views/GoalViews.swift:484
        if (held) {   // ios: Views/GoalViews.swift:485
            held = false; bx = x; by = py - 7   // ios: Views/GoalViews.swift:486
            vx = w * 0.38 * (if (Random.nextBoolean()) 1 else -1); vy = -w * 0.92   // ios: Views/GoalViews.swift:487
            return 0   // ios: Views/GoalViews.swift:488
        }
        var nx = bx + vx * dt; var ny = by + vy * dt   // ios: Views/GoalViews.swift:490
        if (nx < 4 || nx > w - 4) { vx = -vx; nx = max(4.0, min(w - 4, nx)) }   // ios: Views/GoalViews.swift:491
        if (ny < 4) { vy = abs(vy); ny = 4.0 }   // ios: Views/GoalViews.swift:492
        val col = (nx / c).toInt(); val row = (ny / c).toInt()   // ios: Views/GoalViews.swift:493
        if (ny < w && col in 0 until 10 && row in 0 until 10 && alive[row * 10 + col]) {   // ios: Views/GoalViews.swift:494
            val hit = row * 10 + col   // ios: Views/GoalViews.swift:495
            alive[hit] = false; left -= 1   // ios: Views/GoalViews.swift:496
            if (bomb[hit]) {   // ios: Views/GoalViews.swift:497
                for (r in max(0, row - 1)..min(9, row + 1)) { for (c2 in max(0, col - 1)..min(9, col + 1)) if (alive[r * 10 + c2]) { alive[r * 10 + c2] = false; left -= 1 } }   // ios: Views/GoalViews.swift:498
            }
            if ((bx / c).toInt() != col) vx = -vx else vy = -vy   // ios: Views/GoalViews.swift:500
            if (left == 0) { sm?.unregisterListener(this); return 1 }   // ios: Views/GoalViews.swift:501
            return 0   // ios: Views/GoalViews.swift:502
        }
        if (vy > 0 && by < py - 4 && ny >= py - 4 && abs(nx - x) < pw / 2 + 4) {   // ios: Views/GoalViews.swift:504
            val sp = hypot(vx, vy); val o = max(-1.0, min(1.0, (nx - x) / (pw / 2)))   // ios: Views/GoalViews.swift:505
            vx = sp * 0.75 * o; vy = -sqrt(sp * sp - vx * vx); ny = py - 4   // ios: Views/GoalViews.swift:506
        }
        if (ny > h + 8) { sm?.unregisterListener(this); return 2 }   // ios: Views/GoalViews.swift:508
        bx = nx; by = ny   // ios: Views/GoalViews.swift:509
        return 0   // ios: Views/GoalViews.swift:510
    }

    // ios: Views/GoalViews.swift:513
    fun draw(d: DrawScope, cells: Boolean, col: BrickColors) = with(d) {
        val w = size.width / density; val gap = 4f; val s = (w - gap * 9) / 10; val pw = w * 0.28f   // ios: Views/GoalViews.swift:514
        val h = size.height / density
        scale(density, density, pivot = Offset.Zero) {
            if (cells) {   // ios: Views/GoalViews.swift:515
                for (i in 0 until 100) {   // ios: Views/GoalViews.swift:516
                    val r = Rect(Offset((i % 10) * (s + gap), (i / 10) * (s + gap)), Size(s, s))   // ios: Views/GoalViews.swift:517
                    val cr = CornerRadius(5f)   // ios: Views/GoalViews.swift:518
                    drawRoundRect(if (alive[i]) (if (i < mineN) col.teal else col.mint) else col.track, r.topLeft, r.size, cr)   // ios: Views/GoalViews.swift:519
                    if (alive[i] && bomb[i]) drawOval(col.gold, Offset(r.left + s * 0.32f, r.top + s * 0.32f), Size(s - s * 0.64f, s - s * 0.64f))   // ios: Views/GoalViews.swift:520
                    if (i == 99 && !alive[i]) drawRoundRect(col.gold, Offset(r.left + 1, r.top + 1), Size(s - 2, s - 2), CornerRadius(4f), style = Stroke(2f))   // ios: Views/GoalViews.swift:521
                }
            }
            val x = max(pw / 2, min(w - pw / 2, px.toFloat() * w)); val py = h - 6   // ios: Views/GoalViews.swift:524
            drawRoundRect(col.teal, Offset(x - pw / 2, py - 2.5f), Size(pw, 5f), CornerRadius(2.5f))   // ios: Views/GoalViews.swift:525
            val ball = if (cells && !held) Offset(bx.toFloat(), by.toFloat()) else Offset(x, py - 7)   // ios: Views/GoalViews.swift:526
            drawOval(col.gold, Offset(ball.x - 4, ball.y - 4), Size(8f, 8f))   // ios: Views/GoalViews.swift:527
        }
    }
}

/** 왼쪽 칸은 폭의 ratio만큼 정사각형, 오른쪽은 남은 폭에 세로 가운데 */
// ios: Views/GoalViews.swift:532,533
@Composable
fun SplitRow(ratio: Float, spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->   // ios: Views/GoalViews.swift:534,540
        val w = if (c.hasBoundedWidth) c.maxWidth else 320.dp.roundToPx(); val side = (w * ratio).roundToInt(); val sp = spacing.roundToPx()   // ios: Views/GoalViews.swift:535,541
        val ps = ms.mapIndexed { i, x -> x.measure(if (i == 0) Constraints(minWidth = side, maxWidth = side) else Constraints(maxWidth = max(0, w - side - sp))) }
        val lh = ps.firstOrNull()?.height ?: 0   // ios: Views/GoalViews.swift:536
        val h = if (ms.size > 1) ps[1].height else 0   // ios: Views/GoalViews.swift:537
        layout(w, max(lh, h)) {   // ios: Views/GoalViews.swift:538
            val H = max(lh, h)
            ps.firstOrNull()?.let { it.place(0, (H - it.height) / 2) }   // ios: Views/GoalViews.swift:542
            if (ps.size > 1) ps[1].place(side + sp, (H - ps[1].height) / 2)   // ios: Views/GoalViews.swift:543
        }
    }
}

/** 10×10 칸, 왼쪽 위부터 줄마다 채운다. 경계 칸은 비율만큼 가로로 나눠 칠한다. 마지막 칸(오른쪽 아래)은 금색 테두리 = 목표 */
// ios: Views/GoalViews.swift:548,549,550
@Composable
fun GoalCellGrid(mine: Double, filled: Double, lost: Double, modifier: Modifier = Modifier) {
    val track = Theme.track; val card = Theme.card; val gold = Theme.gold; val teal = Theme.teal; val mintC = GoalCellsCard.mintC; val lostC = GoalCellsCard.lostC
    Spacer(modifier.drawWithCache {   // ios: Views/GoalViews.swift:551
        val k = density; val gap = 4f; val s = (size.width / k - gap * 9) / 10   // ios: Views/GoalViews.swift:552
        // 칸 모양은 크기가 바뀔 때만 만든다
        val cells = (0 until 100).map { i ->   // ios: Views/GoalViews.swift:553
            val r = Rect(Offset((i % 10) * (s + gap), (i / 10) * (s + gap)), Size(s, s))   // ios: Views/GoalViews.swift:554
            Path().apply { addRoundRect(RoundRect(r, CornerRadius(5f))) } to r   // ios: Views/GoalViews.swift:555
        }
        onDrawBehind {
            scale(k, k, pivot = Offset.Zero) {
                cells.forEachIndexed { i, (path, r) ->
                    drawPath(path, track)   // ios: Views/GoalViews.swift:556
                    if (i == 99 && filled < 100) {   // ios: Views/GoalViews.swift:557
                        drawPath(path, card)   // ios: Views/GoalViews.swift:558
                        drawRoundRect(gold, Offset(r.left + 1, r.top + 1), Size(r.width - 2, r.height - 2), CornerRadius(4f), style = Stroke(2f))   // ios: Views/GoalViews.swift:559
                        return@forEachIndexed   // ios: Views/GoalViews.swift:560
                    }
                    // 이 칸 [i, i+1] 안에서 각 색이 차지하는 비율
                    val lo = i.toDouble()   // ios: Views/GoalViews.swift:563
                    val seg = listOf(Triple(0.0, mine, teal), Triple(mine, filled, mintC), Triple(filled, filled + lost, lostC))   // ios: Views/GoalViews.swift:564
                    clipPath(path) {   // ios: Views/GoalViews.swift:565,566
                        for ((a, b, c) in seg) {   // ios: Views/GoalViews.swift:567
                            val x0 = max(0.0, min(1.0, a - lo)); val x1 = max(0.0, min(1.0, b - lo))   // ios: Views/GoalViews.swift:568
                            if (x1 > x0) drawRect(c, Offset(r.left + s * x0.toFloat(), r.top), Size(s * (x1 - x0).toFloat(), s))   // ios: Views/GoalViews.swift:569
                        }
                    }
                }
            }
        }
    })
}

// ── 플러스 미션 1 결과: 지금 평가액 ──

// ios: Views/GoalViews.swift:579,580,581
@Composable
fun GoalHoldResultView(m: AppModel) {
    Page(kicker = "미션 1 완료 · 평가액·수익률 열림", title = "지금 내 평가액") {   // ios: Views/GoalViews.swift:582
        if (m.ret >= 0) GuideBubble(m, Pose.spread, "지금 평가액이에요. 들어간 돈보다 ${AppModel.sgn(m.ret)} 위에 있어요. 여기서 출발해 목표 금액까지 가는 길을 그려요.")   // ios: Views/GoalViews.swift:583
        val box = Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp))   // ios: Views/GoalViews.swift:593
        Fg(Color.White) {
            Column(box.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/GoalViews.swift:584,592
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Spacer(Modifier.weight(1f)); T("원금 ${noWon(AppModel.man(m.cost))}", style = appFont(13f), color = hex(0xC9D0D6)) }   // ios: Views/GoalViews.swift:585
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:586
                    T(noWon(AppModel.man(m.total)), Modifier.alignByBaseline(), style = appFont(30f, FontWeight.Bold), roll = noWon(AppModel.man(m.total)))   // ios: Views/GoalViews.swift:587
                    T(AppModel.sgn(m.ret), Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold), color = if (m.ret >= 0) hex(0xFF8A80) else hex(0x8CC4F2))   // ios: Views/GoalViews.swift:588
                }
                T("${m.focusName} 비중 ${AppModel.pct(m.focusWeight)}", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/GoalViews.swift:590
            }
        }
        if (m.ret >= 0) {   // ios: Views/GoalViews.swift:594
            BoardLinks(m, next = "다음: 목표 정하기", to = MissionRoute.g1)   // ios: Views/GoalViews.swift:595
        } else {   // ios: Views/GoalViews.swift:596
            val note = Modifier.fillMaxWidth().background(hexD(0xFDF0E1, 0x3A2A17), RoundedCornerShape(12.dp))   // ios: Views/GoalViews.swift:599
            T("지금 마이너스 · 회복 루트가 더 맞아요 · 종목은 그대로", note.padding(12.dp), style = appFont(15f))   // ios: Views/GoalViews.swift:597,598
            PrimaryButton(title = "회복 루트로") { m.switchRoute(Route.recover) }   // ios: Views/GoalViews.swift:600
            BoardLinks(m, next = null, to = null)   // ios: Views/GoalViews.swift:601
            // ios: Views/GoalViews.swift:602,603
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).plainClickable { m.boardPath = m.boardPath + MissionRoute.g1 }, contentAlignment = Alignment.Center) {
                T("목표 루트로 계속", style = appFont(15f, FontWeight.SemiBold), color = Theme.sub)
            }
        }
    }
}

// ── 목표 정하기: 경로 그래프 고정 + 배수 카드 ──

/** fromSettings: 설정·홈·전망에서 열었을 때. 저장하면 돌아가고, 미션 진행은 건드리지 않는다 */
// ios: Views/GoalViews.swift:611,612,615,622
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSetView(m: AppModel, fromSettings: Boolean = false) {
    val nav = LocalNavBack.current   // ios: Views/GoalViews.swift:613 (dismiss)
    val focus = LocalFocusManager.current   // ios: Views/GoalViews.swift:616 (@FocusState)
    var k by remember { mutableStateOf("") }   // ios: Views/GoalViews.swift:617
    var y by remember { mutableStateOf("") }   // ios: Views/GoalViews.swift:618
    var a by remember { mutableStateOf("") }   // ios: Views/GoalViews.swift:619
    var mo by remember { mutableStateOf("") }   // ios: Views/GoalViews.swift:620
    // 플러스: 내가 매달 넣을 돈(gM)으로 닿을 확률. 시작 전: 보통의 경우로 맞춘 월 적립으로
    val plus = m.route != Route.novice   // ios: Views/GoalViews.swift:624
    // ios: Views/GoalViews.swift:625 (경로 계산은 읽은 값이 바뀔 때만 다시)
    val need by remember(m) { derivedStateOf { m.neededMonthly() } }; val path by remember(m) { derivedStateOf { m.simulate(if (m.route != Route.novice) m.mineMix else AppModel.mixes[0], monthly = if (m.route != Route.novice) m.gM else m.neededMonthly()) } }
    val start = m.goalStart; val cagr = if (start > 0) (m.gK / start).pow(1 / m.gY.toDouble()) - 1 else 0.0   // ios: Views/GoalViews.swift:626
    val ok = m.gK > 0 && m.gY >= 1   // ios: Views/GoalViews.swift:627
    val teal = Theme.teal; val line = Theme.line
    // 위로 끌면 자판을 내린다 (scrollDismissesKeyboard(.interactively))
    val dismissKb = remember(focus) { object : NestedScrollConnection { override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset { if (available.y != 0f) focus.clearFocus(); return Offset.Zero } } }   // ios: Views/GoalViews.swift:702
    Box(Modifier.fillMaxSize().background(Theme.bg).nestedScroll(dismissKb)) {   // ios: Views/GoalViews.swift:699,700 (inline 제목 막대는 NavStack 몫)
        PinnedLayout(header = {   // ios: Views/GoalViews.swift:628
            // ios: Views/GoalViews.swift:629,648
            Column(Modifier.fillMaxWidth().background(Theme.card).bottomLine(line).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val gNo = m.goalNo("goal")
                // Views/GoalViews.swift:630 (검사 예외: 보간 속 따옴표, 같은 글)
                T(if (fromSettings || m.gDone.contains("goal")) "목표 고치기" else "미션 ${gNo} / ${m.goalTotal}", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)
                T("언제까지, 얼마나?", style = appFont(22f, FontWeight.Bold))   // ios: Views/GoalViews.swift:631
                Row(Modifier.fillMaxWidth()) {   // ios: Views/GoalViews.swift:632
                    T("${m.gY}년 뒤 ${noWon(AppModel.wonK(m.gK))}", Modifier.alignByBaseline(), style = appFont(17f, FontWeight.Bold))   // ios: Views/GoalViews.swift:633
                    Spacer(Modifier.weight(1f))   // ios: Views/GoalViews.swift:634
                    TA(buildAnnotatedString { append("닿을 확률 "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = teal)) { append(AppModel.pct(path.prob)) } }, Modifier.alignByBaseline(), style = appFont(14f))   // ios: Views/GoalViews.swift:635
                }
                if (plus) {   // ios: Views/GoalViews.swift:637
                    T("월 ${noWon(AppModel.wonK(m.gM))} · " + (if (need > 0) "맞추려면 월 ${noWon(AppModel.wonK(need))}" else "이미 충분"), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/GoalViews.swift:638
                } else {   // ios: Views/GoalViews.swift:639
                    T((if (need > 0) "월 " + noWon(AppModel.wonK(need)) else "이미 충분") + " · 1년 ${noWon(AppModel.wonK(need * 12))}", style = appFont(14f, FontWeight.SemiBold))   // ios: Views/GoalViews.swift:640
                }
                val x1 = cformat("%.1f", m.gK / max(1.0, start))
                // ios: Views/GoalViews.swift:642
                val sub = if (m.route == Route.novice) (if (m.gK <= 1000) "첫 목표 · 적립 습관" else "연 7%면 해마다 +${noWon(AppModel.wonK(m.gK * 0.07))}")
                    else "지금의 ${x1}배 · 적립 없이 연 +${(cagr * 100).swRound().toInt()}%"   // Views/GoalViews.swift:643 (검사 예외: 보간 속 따옴표, 같은 글)
                T(sub, style = appFont(13f), color = Theme.sub)   // ios: Views/GoalViews.swift:644
                GoalPathChart(listOf(Triple(path, Theme.teal, true)), goal = m.gK, modifier = Modifier.fillMaxWidth().height(80.dp))   // ios: Views/GoalViews.swift:645
                val st11 = appFont(11f); val mu = Theme.muted   // ios: Views/GoalViews.swift:646
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("지금", style = st11, color = mu); Spacer(Modifier.weight(1f)); T("┅ 목표 · ┈ 원금", style = st11, color = mu); Spacer(Modifier.weight(1f)); T("${m.gY}년", style = st11, color = mu) }
            }
        }, content = {   // ios: Views/GoalViews.swift:649
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/GoalViews.swift:650,697
                if (fromSettings || m.gDone.contains("goal")) {   // ios: Views/GoalViews.swift:651
                    GuideBubble(m, Pose.point, "카드를 누르거나 숫자를 고치면 위 그래프와 확률이 바로 바뀌어요. 저장하면 미션 진행은 그대로 두고 목표만 바꿔요.")   // ios: Views/GoalViews.swift:652
                } else if (m.route == Route.novice) {   // ios: Views/GoalViews.swift:653
                    GuideBubble(m, Pose.wave, "처음 시작하시는군요. …… 첫 목표부터 정해요. 지금 모아 둔 투자금이 없으면 0을 넣으세요.")   // ios: Views/GoalViews.swift:654
                } else {   // ios: Views/GoalViews.swift:655
                    GuideBubble(m, Pose.think, "언제까지, 얼마를 모으고 싶나요? …… 카드는 매달 넣는 돈으로 닿을 확률이 반반쯤인 금액이에요. 모두 지금 ${AppModel.wonK(start)}에서 출발한 계산이에요.")   // ios: Views/GoalViews.swift:656
                }
                if (m.route != Route.novice) {   // ios: Views/GoalViews.swift:658
                    T((if (m.gM > 0) "월 ${noWon(AppModel.wonK(m.gM))}씩 넣으면" else "적립 없이") + " 확률 50%인 목표", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/GoalViews.swift:659
                }
                val presets by remember(m) { derivedStateOf { m.goalPresets } }   // ios: Views/GoalViews.swift:662 (목표 카드: 경로 계산이라 값이 바뀔 때만)
                // ios: Views/GoalViews.swift:661 두 열 격자: 열 사이 8 (GridItem spacing 8·8), 줄 사이 8
                val gridItems = listOf(8.dp, 8.dp); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { presets.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(gridItems[0])) {
                    pair.forEach { p ->
                        val on = m.gK == p.k && m.gY == p.y   // ios: Views/GoalViews.swift:663
                        val tap = Modifier.plainClickable { m.gK = p.k; m.gY = p.y; k = p.k.toInt().toString(); y = p.y.toString() }   // ios: Views/GoalViews.swift:664,673
                        // ios: Views/GoalViews.swift:670,671,672
                        val card = Modifier.defaultMinSize(minHeight = 96.dp).background(if (on) Theme.mintBg else Theme.card, RoundedCornerShape(14.dp)).strokeR(if (on) Theme.teal else Theme.border, 14f, 2f).padding(12.dp)
                        Column(Modifier.weight(1f).then(tap).then(card), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/GoalViews.swift:665
                            Row { T(p.name, Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold)); T("${p.y}년", Modifier.alignByBaseline().padding(start = 8.dp), style = appFont(12f), color = Theme.sub) }   // ios: Views/GoalViews.swift:666
                            if (p.mean.isNotEmpty()) T(p.mean, style = appFont(12f), color = Theme.sub)   // ios: Views/GoalViews.swift:667
                            T(p.need, style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/GoalViews.swift:668
                        }
                    }
                    if (pair.size < 2) Spacer(Modifier.weight(1f))
                } } }
                inputField("목표 (만원)", k) { k = it }; OnChange(k) { v -> v.replace(",", "").toDoubleOrNull()?.let { d -> if (d > 0) m.gK = d } }   // ios: Views/GoalViews.swift:676
                inputField("기간 (년)", y) { y = it }; OnChange(y) { v -> v.replace(",", "").toIntOrNull()?.let { d -> m.gY = min(30, max(1, d)) } }   // ios: Views/GoalViews.swift:677
                if (m.noviceOwn) {   // ios: Views/GoalViews.swift:678
                    T("출발 ${noWon(AppModel.wonK(start))} · 내 종목 기준", style = appFont(13f), color = Theme.sub)   // ios: Views/GoalViews.swift:679
                } else if (m.route == Route.novice) {   // ios: Views/GoalViews.swift:680
                    inputField("모아 둔 돈 (만원)", a) { a = it }; OnChange(a) { v -> m.gA = max(0.0, v.toDoubleOrNull() ?: 0.0) }   // ios: Views/GoalViews.swift:681
                } else {   // ios: Views/GoalViews.swift:682
                    inputField("월 적립 (만원)", mo) { mo = it }; OnChange(mo) { v -> m.gM = max(0.0, v.replace(",", "").toDoubleOrNull() ?: 0.0) }   // ios: Views/GoalViews.swift:683
                    // ios: Views/GoalViews.swift:684,685
                    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).plainClickable { focus.clearFocus(); m.gK = m.autoGoal(years = m.gY); k = m.gK.toInt().toString() }, contentAlignment = Alignment.Center) {
                        T("50%로 맞추기", style = appFont(15f, FontWeight.SemiBold), color = Theme.teal)
                    }
                    T("출발 ${noWon(AppModel.wonK(start))} · 목표일 ${m.goalToShort}", style = appFont(13f), color = Theme.sub)   // ios: Views/GoalViews.swift:686
                }
                PrimaryButton(title = if (fromSettings || m.gDone.contains("goal")) "저장" else "길 보기", color = if (ok) Theme.teal else Theme.muted) {   // ios: Views/GoalViews.swift:688
                    if (!ok) return@PrimaryButton   // ios: Views/GoalViews.swift:689
                    focus.clearFocus()   // ios: Views/GoalViews.swift:690
                    if (m.route == Route.novice) m.gM = max(5.0, (need / 5).swRound() * 5)   // ios: Views/GoalViews.swift:691
                    if (fromSettings) { nav?.pop?.invoke(); return@PrimaryButton }   // ios: Views/GoalViews.swift:692
                    if (m.gDone.contains("goal")) { m.boardPath = m.boardPath.dropLast(1); return@PrimaryButton }   // ios: Views/GoalViews.swift:693 고치기: 미션 판으로 돌아간다
                    m.gDone = m.gDone + "goal"; m.boardPath = m.boardPath + MissionRoute.g1r   // ios: Views/GoalViews.swift:694
                }
            }
        })
        // ios: Views/GoalViews.swift:703 자판 위 막대의 완료 (자판이 떠 있을 때만)
        if (WindowInsets.isImeVisible) Row(Modifier.align(Alignment.BottomCenter).imePadding().fillMaxWidth().background(Theme.card).bottomLine(line).plainClickable { focus.clearFocus() }.padding(horizontal = 16.dp, vertical = 10.dp)) { Spacer(Modifier.weight(1f)); T("완료", style = appFont(17f, FontWeight.SemiBold), color = Theme.teal) }
    }
    LaunchedEffect(Unit) { k = m.gK.toInt().toString(); y = m.gY.toString(); a = m.gA.toInt().toString(); mo = m.gM.toInt().toString() }   // ios: Views/GoalViews.swift:701
}

// ios: Views/GoalViews.swift:707,708,709
@Composable
fun GoalSetResultView(m: AppModel) {
    val need by remember(m) { derivedStateOf { m.neededMonthly() } }; val base = if (m.route == Route.novice) AppModel.mixes[0] else m.mineMix   // ios: Views/GoalViews.swift:710
    val path by remember(m) { derivedStateOf { m.simulate(if (m.route == Route.novice) AppModel.mixes[0] else m.mineMix, monthly = m.neededMonthly()) } }   // ios: Views/GoalViews.swift:711
    val gNo = m.goalNo("goal"); val sub = Theme.sub
    // Views/GoalViews.swift:712 (검사 예외: 보간 속 따옴표, 같은 글)
    Page(kicker = "미션 ${gNo} 완료 · 경로 열림", title = "${m.gY}년 뒤 ${noWon(AppModel.wonK(m.gK))}까지") {
        val box = Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp))   // ios: Views/GoalViews.swift:718
        Fg(Color.White) {
            Column(box.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/GoalViews.swift:713,717
                T("보통의 경우 매달", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/GoalViews.swift:714
                T(if (need > 0) noWon(AppModel.wonK(need)) else "0 · 이미 충분", style = appFont(30f, FontWeight.Bold), roll = if (need > 0) noWon(AppModel.wonK(need)) else "0 · 이미 충분")   // ios: Views/GoalViews.swift:715
            }
        }
        GuideBubble(m, Pose.back, if (m.route == Route.novice)   // ios: Views/GoalViews.swift:719
            "같이 봐요. 매달 ${AppModel.wonK(need)}씩 넣으면 지날 길이에요. 점선은 내가 넣은 원금이에요."   // ios: Views/GoalViews.swift:720
            else "같이 봐요. 지금 금액이 보통의 경우처럼 자란다고 보고, 모자란 만큼을 매달로 나눈 금액이에요.", note = "그래프 쪽을 돌아봄")   // ios: Views/GoalViews.swift:721
        Card {   // ios: Views/GoalViews.swift:722
            TA(buildAnnotatedString { append("월 ${noWon(AppModel.wonK(need))}씩 "); withStyle(SpanStyle(color = sub)) { append("· ${base.name}") } }, style = appFont(15f, FontWeight.Bold))   // ios: Views/GoalViews.swift:723
            GoalPathChart(listOf(Triple(path, Theme.teal, true)), goal = m.gK, modifier = Modifier.fillMaxWidth().height(150.dp))   // ios: Views/GoalViews.swift:724
            val half = cformat("%g", m.gY.toDouble() / 2); val st11 = appFont(11f); val mu = Theme.muted
            // Views/GoalViews.swift:725 (검사 예외: 보간 속 따옴표, 같은 글)
            Row(Modifier.fillMaxWidth()) { T("지금", style = st11, color = mu); Spacer(Modifier.weight(1f)); T("${half}년", style = st11, color = mu); Spacer(Modifier.weight(1f)); T("${m.gY}년", style = st11, color = mu) }
            FlowRow(spacing = 10.dp) {   // ios: Views/GoalViews.swift:726
                legend(Theme.teal, "보통의 경우"); legend(Theme.teal.copy(alpha = 0.2f), "100번 중 90번")   // ios: Views/GoalViews.swift:727
                legend(Theme.muted, "원금"); legend(hex(0xC8352E), "목표")   // ios: Views/GoalViews.swift:728
            }
            TA(buildAnnotatedString { append("목표일 달성 "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(AppModel.pct(path.prob)) }; append(" · 보통 ${path.etaText}") }, style = appFont(14f))   // ios: Views/GoalViews.swift:730
        }
        BoardLinks(m, next = "다음: 구성 고르기", to = MissionRoute.g3)   // ios: Views/GoalViews.swift:732
    }
}

// ── 구성 고르기: 구성별 길 비교 고정 + 칩 ──

// ios: Views/GoalViews.swift:739
@Composable
private fun compositionBar(weights: Map<String, Double>) {
    val keys = listOf("T", "I", "G", "B", "C").filter { (weights[it] ?: 0.0) > 0.001 }   // ios: Views/GoalViews.swift:740
    val cc = compColor; val cols = keys.map { cc[it]!! }; val card = Theme.card; val track = Theme.track
    // ios: Views/GoalViews.swift:741,749,750,751
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(CapsuleShape).background(track, CapsuleShape).clearAndSetSemantics { contentDescription = keys.joinToString(", ") { "${compName[it]} ${AppModel.pct(weights[it] ?: 0.0)}" } }) {
        var x = 0f   // ios: Views/GoalViews.swift:742
        keys.forEachIndexed { i, k ->   // ios: Views/GoalViews.swift:743
            val w = size.width * (weights[k] ?: 0.0).toFloat(); drawRect(cols[i], Offset(x, 0f), Size(w, size.height))   // ios: Views/GoalViews.swift:744
            drawRect(card, Offset(x + w - 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height)); x += w   // ios: Views/GoalViews.swift:745
        }
    }
}

// ios: Views/GoalViews.swift:754,755,758
@Composable
fun GoalMixView(m: AppModel) {
    var open by remember { mutableStateOf(setOf<Basket>()) }   // ios: Views/GoalViews.swift:756
    val sims by remember(m) { derivedStateOf { m.goalMixes.map { m.simulate(it) } } }   // ios: Views/GoalViews.swift:759,760 (칩은 m.gMix 를 바로 바꾼다)
    val selS by remember(m) { derivedStateOf { sims.firstOrNull { it.mix.id == m.gMix } ?: sims[0] } }; val sel = selS   // ios: Views/GoalViews.swift:761
    val sh by remember(m) { derivedStateOf { m.shift(selS.mix) } }   // ios: Views/GoalViews.swift:762
    val mc = mixColor; val line = Theme.line
    Box(Modifier.fillMaxSize().background(Theme.bg)) {   // ios: Views/GoalViews.swift:803,804 (inline 제목 막대는 NavStack 몫)
        PinnedLayout(header = {   // ios: Views/GoalViews.swift:763
            Column(Modifier.fillMaxWidth().background(Theme.card).bottomLine(line).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/GoalViews.swift:764,786
                val gNo = m.goalNo("mix")
                // Views/GoalViews.swift:765 (검사 예외: 보간 속 따옴표, 같은 글)
                T("미션 ${gNo} / ${m.goalTotal}", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)
                T("어떤 구성으로?", style = appFont(20f, FontWeight.Bold))   // ios: Views/GoalViews.swift:766
                T("지수·업종 묶음 · 월 ${noWon(AppModel.wonK(m.gM))} · ${m.gY}년", style = appFont(12f), color = Theme.sub)   // ios: Views/GoalViews.swift:767
                GoalPathChart(sims.map { Triple(it, mc[it.mix.id] ?: Theme.teal, it.mix.id == sel.mix.id) }, goal = m.gK, showPrincipal = false, modifier = Modifier.fillMaxWidth().height(100.dp))   // ios: Views/GoalViews.swift:768
                FlowRow(spacing = 10.dp) {   // ios: Views/GoalViews.swift:769
                    sims.forEach { s -> legend(mc[s.mix.id] ?: Theme.teal, s.mix.name) }   // ios: Views/GoalViews.swift:770
                    legend(hex(0xC8352E), "목표")   // ios: Views/GoalViews.swift:771
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:773
                    Box(Modifier.size(10.dp, 10.dp).background(mc[sel.mix.id] ?: Theme.teal, RoundedCornerShape(3.dp)))   // ios: Views/GoalViews.swift:774
                    T(sel.mix.name, style = appFont(15f, FontWeight.Bold))   // ios: Views/GoalViews.swift:775
                    T(sel.mix.tag, style = appFont(12f), color = Theme.sub)   // ios: Views/GoalViews.swift:776
                }
                compositionBar(sh.weights)   // ios: Views/GoalViews.swift:778
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:779
                    pill("목표일 달성", AppModel.pct(sel.prob))   // ios: Views/GoalViews.swift:780
                    pill("보통 도착", sel.etaText)   // ios: Views/GoalViews.swift:781
                    pill("나쁜 해 한 번", AppModel.sgn0(sel.badYear))   // ios: Views/GoalViews.swift:782
                }
                ChipRow(items = m.goalMixes.map { it.id to it.name }, selection = m.gMix, onSelect = { m.gMix = it }, fill = true)   // ios: Views/GoalViews.swift:784
            }
        }, content = {   // ios: Views/GoalViews.swift:787
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/GoalViews.swift:788,801
                if (m.route == Route.novice) {   // ios: Views/GoalViews.swift:789
                    GuideBubble(m, Pose.think, "어떤 구성이 덜 흔들리는지 보세요. …… 처음이라면 단순한 구성부터 비교해 봐도 돼요.")   // ios: Views/GoalViews.swift:790
                } else {   // ios: Views/GoalViews.swift:791
                    GuideBubble(m, Pose.side, "구성을 하나씩 눌러 지금 내 구성과 비교해 보세요. 빨리 가는 구성일수록 더 흔들려요. 나쁜 해 한 번의 크기도 같이 보세요.")   // ios: Views/GoalViews.swift:792
                }
                if (sh.line.isNotEmpty()) T(sh.line, style = appFont(13f), color = Theme.sub)   // ios: Views/GoalViews.swift:794
                T(sel.mix.desc, style = appFont(15f).ls(3f))   // ios: Views/GoalViews.swift:795
                BasketList(baskets = listOf(Basket.G, Basket.B), open = open, onOpen = { open = it }, title = "업종 묶음")   // ios: Views/GoalViews.swift:796
                T("나쁜 해 한 번: 20년에 한 번쯤 오는 해의 1년 변화 · 가정값 · 투자 권유 아님",   // ios: Views/GoalViews.swift:797
                    style = appFont(12f).ls(2f), color = Theme.muted)   // ios: Views/GoalViews.swift:798
                PrimaryButton(title = "\"${sel.mix.name}\"으로 정하기") { m.gDone = m.gDone + "mix"; m.boardPath = m.boardPath + MissionRoute.g3r }   // ios: Views/GoalViews.swift:799
            }
        })
    }
}

// ios: Views/GoalViews.swift:807
@Composable
private fun RowScope.pill(k: String, v: String) {
    val bg = Modifier.background(Theme.bg, RoundedCornerShape(10.dp))   // ios: Views/GoalViews.swift:810
    Column(Modifier.weight(1f).then(bg).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/GoalViews.swift:809
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) { T(k, style = appFont(11f), color = Theme.sub); FitText(v, style = appFont(14f, FontWeight.Bold), minScale = 0.7f, maxLines = 1) }   // ios: Views/GoalViews.swift:808
    }
}

// ios: Views/GoalViews.swift:814,815,817
@Composable
fun GoalMixResultView(m: AppModel) {
    var open by remember { mutableStateOf(setOf<Basket>()) }   // ios: Views/GoalViews.swift:816
    val sel by remember(m) { derivedStateOf { m.simulate(m.selectedMix) } }; val sh by remember(m) { derivedStateOf { m.shift(m.selectedMix) } }   // ios: Views/GoalViews.swift:818
    val next = m.goalSteps.firstOrNull { !m.gDone.contains(it.id) && m.goalAvailable(it) }   // ios: Views/GoalViews.swift:819
    val gNo = m.goalNo("mix")
    // Views/GoalViews.swift:820 (검사 예외: 보간 속 따옴표, 같은 글)
    Page(kicker = "미션 ${gNo} 완료 · 구성 비교 열림", title = "내 계획: ${sel.mix.name}") {
        GuideBubble(m, Pose.cheer, if (m.route == Route.novice) "정했어요. 매달 적립을 나누는 비율이에요. 실제 매매 판단은 직접 하셔야 해요." else "정했어요. 계획만 기록해요. 실제 매매 판단은 직접 하셔야 해요.")   // ios: Views/GoalViews.swift:821
        T(sel.mix.desc, style = appFont(15f), color = Theme.sub)   // ios: Views/GoalViews.swift:822
        Card {   // ios: Views/GoalViews.swift:823
            val b = SpanStyle(fontWeight = FontWeight.Bold)
            TA(buildAnnotatedString { append("목표일 달성 "); withStyle(b) { append(AppModel.pct(sel.prob)) }; append(" · 보통 "); withStyle(b) { append(sel.etaText) } }, style = appFont(15f))   // ios: Views/GoalViews.swift:824
        }
        if (sh.rows.isNotEmpty()) {   // ios: Views/GoalViews.swift:826
            Card {   // ios: Views/GoalViews.swift:827
                T(if (m.route == Route.novice) "적립 나누기" else "비중이 바뀌면", style = appFont(15f, FontWeight.Bold))   // ios: Views/GoalViews.swift:828
                sh.rows.forEach { r ->   // ios: Views/GoalViews.swift:829
                    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 34.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:830,836
                        T(r.name, style = appFont(15f, FontWeight.SemiBold))   // ios: Views/GoalViews.swift:831
                        T(r.now, Modifier.padding(start = 8.dp), style = appFont(13f), color = Theme.sub)   // ios: Views/GoalViews.swift:832
                        Spacer(Modifier.weight(1f))   // ios: Views/GoalViews.swift:833
                        T(r.change, style = appFont(15f, FontWeight.Bold), color = if (r.up) Theme.teal else hexD(0x2450C8, 0x7FA2F0))   // ios: Views/GoalViews.swift:834
                    }
                }
                if (sh.note.isNotEmpty()) T(sh.note, style = appFont(12f).ls(2f), color = Theme.sub)   // ios: Views/GoalViews.swift:838
            }
        }
        sel.mix.basket?.let { b -> BasketList(baskets = listOf(b), open = open, onOpen = { open = it }, title = "담을 업종") }   // ios: Views/GoalViews.swift:841
        T("계획만 기록 · 매매는 직접", style = appFont(14f), color = Theme.sub)   // ios: Views/GoalViews.swift:842
        BoardLinks(m, next = next?.let { "다음: ${it.title}" }, to = next?.to)   // ios: Views/GoalViews.swift:843
    }
}

// ── 비중 조정 세금 (플러스) ──

/** Swift 튜플 (키, 이름, 설명, 세금) 자리 */
private data class TaxRow(val k: String, val name: String, val how: String, val tax: Double)

// ios: Views/GoalViews.swift:850,851,853
@Composable
fun GoalTaxView(m: AppModel) {
    var gain by remember { mutableStateOf("") }   // ios: Views/GoalViews.swift:852
    val sh by remember(m) { derivedStateOf { m.shift(m.selectedMix) } }; val sale = sh.sell * m.goalStart   // ios: Views/GoalViews.swift:854,855 (칩은 m.goalTaxPick 을 바로 바꾼다)
    // 양도세는 해외(미국) 주식만. 가장 큰 종목이 국내 주식이면 계산하지 않는다
    val drnk = m.focusRow; val us = drnk?.sym?.currency == Currency.usd; val gainRate = if (us) drnk?.let { 1 - it.h.avg / it.sym.last } ?: 0.0 else 0.0   // ios: Views/GoalViews.swift:857
    val gainSale = sale * gainRate; val G0 = m.taxGain   // ios: Views/GoalViews.swift:858
    val tx = { x: Double -> max(0.0, x - 250) * 0.22 }   // ios: Views/GoalViews.swift:859
    val tOne = tx(G0 + gainSale); val tSplit = tx(G0 + gainSale / 2) + tx(gainSale / 2); val tNo = tx(G0)   // ios: Views/GoalViews.swift:860
    val rows: List<TaxRow> = listOf(   // ios: Views/GoalViews.swift:861
        TaxRow("one", "한 해에 모두", "이익 ${noWon(AppModel.wonK(max(0.0, G0 + gainSale)))} − 공제 250만", tOne),   // ios: Views/GoalViews.swift:862
        TaxRow("split", "두 해에 반씩", "공제 250만 × 2", tSplit),   // ios: Views/GoalViews.swift:863
        TaxRow("none", "적립만", "오래 걸림", tNo),   // ios: Views/GoalViews.swift:864
    )
    val pick = rows.firstOrNull { it.k == m.goalTaxPick } ?: rows[1]   // ios: Views/GoalViews.swift:866
    val gNo = m.goalNo("tax"); val mixNo = m.goalNo("mix")
    // Views/GoalViews.swift:867 (검사 예외: 보간 속 따옴표, 같은 글)
    Page(kicker = "미션 ${gNo} / ${m.goalTotal}", title = "비중 맞추려 팔면 세금은?") {
        GuideBubble(m, Pose.point, "비중을 매도로 맞추면 이익에 세금이 붙어요. 공제는 해마다 있어서, 나누는 방식에 따라 세금이 달라져요. 계산 예시예요.")   // ios: Views/GoalViews.swift:868
        T("한국 거주자 · 미국 주식만", style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/GoalViews.swift:869
        inputField("올해 해외주식 이익 (만원)", gain) { gain = it }; OnChange(gain) { v -> m.taxGain = max(0.0, v.toDoubleOrNull() ?: 0.0) }   // ios: Views/GoalViews.swift:870
        if (sh.sell > 0 && gainSale > 0) {   // ios: Views/GoalViews.swift:871
            val box = Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp))   // ios: Views/GoalViews.swift:882
            Fg(Color.White) {
                Column(box.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/GoalViews.swift:872,881
                    T("내년 5월 세금 · ${pick.name}", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/GoalViews.swift:873
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/GoalViews.swift:874
                        if (pick.k != "one") T(noWon(AppModel.wonK(tOne)), Modifier.alignByBaseline(), style = appFont(18f).copy(textDecoration = TextDecoration.LineThrough), color = Theme.muted)   // ios: Views/GoalViews.swift:875
                        T(noWon(AppModel.wonK(pick.tax)), Modifier.alignByBaseline(), style = appFont(30f, FontWeight.Bold), roll = noWon(AppModel.wonK(pick.tax)))   // ios: Views/GoalViews.swift:876
                        T(if (pick.k == "one") "가장 많음" else "−${noWon(AppModel.wonK(max(0.0, tOne - pick.tax)))}", Modifier.alignByBaseline(), style = appFont(13f, FontWeight.Bold), color = Theme.mint)   // ios: Views/GoalViews.swift:877
                    }
                    T(pick.how, style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/GoalViews.swift:879
                }
            }
            ChipRow(items = listOf("one" to "한 해에", "split" to "두 해에", "none" to "적립만"), selection = m.goalTaxPick, onSelect = { m.goalTaxPick = it }, fill = true)   // ios: Views/GoalViews.swift:883
            T("가장 큰 종목 ${AppModel.pct(m.focusWeight)}→${AppModel.pct(m.focusWeight - sh.sell)} · 두 해에 나누면 세금 −${noWon(AppModel.wonK(max(0.0, tOne - tSplit)))}",   // ios: Views/GoalViews.swift:884
                style = appFont(14f).ls(2f), color = Theme.sub)   // ios: Views/GoalViews.swift:885
        } else {   // ios: Views/GoalViews.swift:886
            val bg = Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp))   // ios: Views/GoalViews.swift:891
            val line = Modifier.strokeR(Theme.border, 14f)   // ios: Views/GoalViews.swift:892
            T(if (!us) "가장 큰 종목이 국내 주식 · 양도세 계산 제외"   // ios: Views/GoalViews.swift:887
                else if (sh.sell > 0) "줄이는 부분이 손실 · 낼 세금 없음 · 같은 해 이익과 합산"   // ios: Views/GoalViews.swift:888
                else "\"${m.selectedMix.name}\"은 팔 게 없음 · 미션 ${mixNo}에서 다른 구성으로 비교",   // Views/GoalViews.swift:889 (검사 예외: 보간 속 따옴표, 같은 글)
                bg.then(line).padding(14.dp), style = appFont(15f))   // ios: Views/GoalViews.swift:890
        }
        T("결제일 기준 · 계산 예시 · 세무 상담·매매 권유 아님", style = appFont(12f), color = Theme.muted)   // ios: Views/GoalViews.swift:894
        PrimaryButton(title = "확인") { m.gDone = m.gDone + "tax"; m.boardPath = emptyList() }   // ios: Views/GoalViews.swift:895
    }
    LaunchedEffect(Unit) { gain = m.taxGain.toInt().toString() }   // ios: Views/GoalViews.swift:897
}

// ── 3개월 인터미션 ──

// ios: Views/GoalViews.swift:903,904,905
@Composable
fun GoalIntermissionView(m: AppModel) {
    val fixed = m.gWeeks; val i = min(fixed.size, 2); @Suppress("UNUSED_VARIABLE") val step = AppModel.goalMonthSteps[i]   // ios: Views/GoalViews.swift:906,907
    // ios: Views/GoalViews.swift:908 (도착까지 개월은 경로 계산이라 값이 바뀔 때만)
    @Suppress("UNUSED_VARIABLE") val now = m.goalSaved; val eta by remember(m) { derivedStateOf { m.monthsTo(from = m.goalSaved, monthly = m.gM) } }; val plan = (m.gY * 12 - fixed.size - (if (m.gWeekCur == null) 0 else 1)).toDouble()
    val last = m.gWeekCur ?: fixed.lastOrNull()   // ios: Views/GoalViews.swift:909
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {   // ios: Views/GoalViews.swift:910,1006,1007 (inline 제목 막대는 NavStack 몫)
        Screen(Modifier.padding(top = 8.dp)) {   // ios: Views/GoalViews.swift:1004
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/GoalViews.swift:911
                val box = Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp))   // ios: Views/GoalViews.swift:937
                Fg(Color.White) {
                    Column(box.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/GoalViews.swift:912,936
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("석 달 체크인", style = appFont(12f, FontWeight.Bold), color = Theme.mint); Spacer(Modifier.weight(1f)); T("${fixed.size}/3", style = appFont(13f), color = hex(0xC9D0D6)) }   // ios: Views/GoalViews.swift:913
                        T("이번 달, 계획대로 넣었나요?", style = appFont(20f, FontWeight.Bold))   // ios: Views/GoalViews.swift:914
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:915
                            for (j in 0 until 3) {   // ios: Views/GoalViews.swift:916
                                val p: GoalWeek? = if (j < fixed.size) fixed[j] else (if (j == fixed.size) m.gWeekCur else null)   // ios: Views/GoalViews.swift:917
                                val cur = j == fixed.size
                                val c = if (p != null) Color.White else if (cur) Theme.yellow else Theme.muted   // ios: Views/GoalViews.swift:923
                                val fill = Modifier.background(p?.color ?: Color.Transparent, RoundedCornerShape(12.dp))   // ios: Views/GoalViews.swift:924
                                val line = Modifier.strokeR(if (cur) Theme.yellow else (p?.color ?: Theme.slate), 12f, 2f, dash = if (p == null) floatArrayOf(5f, 4f) else null)   // ios: Views/GoalViews.swift:925
                                // ios: Views/GoalViews.swift:918,922
                                Column(Modifier.weight(1f).defaultMinSize(minHeight = 64.dp).then(fill).then(line), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                                    T("${j + 1}달" + (if (cur) " · 이번 달" else ""), style = appFont(12f), color = c)   // ios: Views/GoalViews.swift:919
                                    T(p?.label ?: (if (cur) "고르는 중" else ""), style = appFont(12f, FontWeight.Bold), color = c)   // ios: Views/GoalViews.swift:920
                                }
                            }
                        }
                        if (last != null) {   // ios: Views/GoalViews.swift:928
                            val head = if (last == GoalWeek.more) "더 넣음" else if (last == GoalWeek.less) "덜 넣음" else "계획대로"   // ios: Views/GoalViews.swift:929
                            val tail = if (eta.isFinite()) " · ${AppModel.eta(eta)} 도착 (" + (if (eta < plan) "${(plan - eta).toInt()}개월 빠름" else if (eta > plan) "${(eta - plan).toInt()}개월 늦음" else "목표 시점") + ")" else ""   // ios: Views/GoalViews.swift:930
                            val bg = if (last == GoalWeek.less) hexD(0xFDF0E1, 0x3A2A17) else if (last == GoalWeek.more) hexD(0xE4F0FB, 0x16283A) else hexD(0xE3F4EC, 0x163226); val shape = RoundedCornerShape(12.dp)   // ios: Views/GoalViews.swift:933
                            T(head + tail, Modifier.fillMaxWidth().background(bg, shape).padding(10.dp), style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)   // ios: Views/GoalViews.swift:931,932
                        }
                    }
                }

                GuideBubble(m, Pose.stand, "3개월 동안 매달 1분이면 돼요. 이번 달 할 일을 하고 나서 표시해 주세요. 체크인할 때마다 캐릭터 위젯이 하나씩 열려요.")   // ios: Views/GoalViews.swift:939

                if (fixed.size < 3) {   // ios: Views/GoalViews.swift:941
                    // 다음 달이 열리기 전에는 방금 한 달을 다시 고를 수 있다
                    val edit = m.monthEditable; val k = if (edit) fixed.size - 1 else fixed.size; val step2 = AppModel.goalMonthSteps[k]   // ios: Views/GoalViews.swift:943
                    Card {   // ios: Views/GoalViews.swift:944
                        T("${k + 1}달째 체크인", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/GoalViews.swift:945
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:946
                            T(step2.whereText, Modifier.background(Theme.mint, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold))   // ios: Views/GoalViews.swift:947
                            T(step2.task, style = appFont(15f, FontWeight.Bold))   // ios: Views/GoalViews.swift:948
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/GoalViews.swift:950
                            GoalWeek.entries.forEach { p ->   // ios: Views/GoalViews.swift:951
                                WeekButton(m, p, on = if (edit) fixed[k] == p else m.gWeekCur == p, edit = edit)   // ios: Views/GoalViews.swift:952
                            }
                        }
                        T("다음 달 전까지 바꿀 수 있음", style = appFont(12f), color = Theme.muted)   // ios: Views/GoalViews.swift:965
                        // 달은 시작한 날부터 30일마다 열린다 (서버 시각). 열리기 전에는 남은 날을 보여 준다
                        val wait = m.monthGateText   // ios: Views/GoalViews.swift:967
                        if (wait != null) WaitLabel(wait)
                        else {   // ios: Views/GoalViews.swift:970
                            // ios: Views/GoalViews.swift:971 withAnimation: 체크인하면 버튼이 흐려지며 사라진다
                            AnimatedVisibility(m.gWeekCur != null, enter = fadeIn(), exit = fadeOut()) { CheckInOutline("체크인", Modifier.plainClickable { m.confirmGoalMonth() }) }
                        }
                    }
                }
                Card {   // ios: Views/GoalViews.swift:977
                    T("체크인 보상 위젯", style = appFont(15f, FontWeight.Bold))   // ios: Views/GoalViews.swift:978
                    T("오늘의 움직임 · 1달 세리 · 2달 시오 · 3달 모두", style = appFont(13f), color = Theme.sub)   // ios: Views/GoalViews.swift:979
                    for (j in 0 until 3) {   // ios: Views/GoalViews.swift:980
                        val on = j < fixed.size   // ios: Views/GoalViews.swift:981
                        val rowBg = Modifier.background(if (on) Theme.card else hexD(0xF0F2F4, 0x1D252E), RoundedCornerShape(16.dp))   // ios: Views/GoalViews.swift:995
                        val rowLine = Modifier.strokeR(if (on) Theme.border else Theme.dash, 16f, 1f, dash = if (on) null else floatArrayOf(4f, 3f))   // ios: Views/GoalViews.swift:996
                        // ios: Views/GoalViews.swift:982,994,997
                        Row(Modifier.fillMaxWidth().alpha(if (on) 1f else 0.8f).then(rowBg).then(rowLine).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (j < 2) {   // ios: Views/GoalViews.swift:983
                                PixelN((if (on) "spr_" else "sil_") + listOf("seri", "sio")[j], 42.dp, 60.dp)   // ios: Views/GoalViews.swift:984
                            } else {   // ios: Views/GoalViews.swift:985
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) { Shelter.friends.forEach { f -> PixelN((if (on) "spr_" else "sil_") + f.id, 18.dp, 26.dp) } }   // ios: Views/GoalViews.swift:986
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/GoalViews.swift:988
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("오늘의 움직임", style = appFont(11f, FontWeight.Bold)); Spacer(Modifier.weight(1f)); T(if (on) "받음" else if (j == fixed.size) "이번 달" else "${j + 1}달 뒤", style = appFont(11f), color = Theme.sub) }   // ios: Views/GoalViews.swift:989
                                T(AppModel.sgn(m.todayMove), style = appFont(16f, FontWeight.Bold))   // ios: Views/GoalViews.swift:990
                                T(AppModel.goalMonthSteps[j].reward, style = appFont(12f), color = Theme.sub)   // ios: Views/GoalViews.swift:991
                            }
                        }
                    }
                }
                if (fixed.size >= 3) {   // ios: Views/GoalViews.swift:1000
                    PrimaryButton(title = if (m.interDone) "미션 판으로" else "인터미션 끝") { m.gDone = m.gDone + "inter"; m.boardPath = emptyList() }   // ios: Views/GoalViews.swift:1001
                }
            }
        }
    }
}

/** 점선 테두리 체크인 버튼 (인터미션 화면) */
// ios: Views/GoalViews.swift:972,973
@Composable
private fun CheckInOutline(t: String, modifier: Modifier) = Box(modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).strokeR(Theme.muted, 10f, 1f, dash = floatArrayOf(4f, 3f)), contentAlignment = Alignment.Center) {
    T(t, style = appFont(14f, FontWeight.SemiBold), color = Theme.ink)
}
