package com.naeilo.app.ui.home

import com.naeilo.app.core.calc.bare
import com.naeilo.app.ui.shelter.shelterPage
import com.naeilo.app.core.tr
import com.naeilo.app.ui.shelter.readerPage
import com.naeilo.app.ui.shelter.characterPage
import com.naeilo.app.core.cformat
import com.naeilo.app.ui.analysis.ParallelBasketView
import com.naeilo.app.ui.analysis.ParallelHomeCard
import com.naeilo.app.ui.analysis.ParallelTitles
import com.naeilo.app.ui.analysis.ParallelView
import com.naeilo.app.ui.LocalNavBack
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.R
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.goalPace
import com.naeilo.app.core.calc.goalReached
import com.naeilo.app.core.calc.goalSaved
import com.naeilo.app.core.calc.graduateToGoal
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keepPlan
import com.naeilo.app.core.calc.paceText
import com.naeilo.app.core.calc.suggestNextGoal
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.data.short
import com.naeilo.app.net.Market
import com.naeilo.app.net.quote
import com.naeilo.app.ui.analysis.AnalysisRoute
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.ui.goal.GoalSetView
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.ui.me.meEditPage
import com.naeilo.app.data.Remind
import com.naeilo.app.data.RemindCat
import com.naeilo.app.data.RemindGo
import com.naeilo.app.ui.remind.RemindSheet
import com.naeilo.app.data.StoryBook
import com.naeilo.app.data.color
import com.naeilo.app.data.go
import com.naeilo.app.ui.analysis.myPath
import com.naeilo.app.ui.analysis.myPathGapToday
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.DashedCard
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.hold.HoldingsNews
import com.naeilo.app.ui.town.TownView
import com.naeilo.app.ui.wave.TownButton
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.pulse
import com.naeilo.app.ui.rememberPrefBool
import com.naeilo.app.ui.stiffness
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.util.lerp
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.ui.ContextMenu
import com.naeilo.app.ui.DayMood
import com.naeilo.app.ui.FitRow
import com.naeilo.app.ui.FriendMotion
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.MenuItem
import com.naeilo.app.ui.MoodFigure
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 왼쪽 아래만 덜 둥근 말풍선 (UnevenRoundedRectangle 14·4·14·14) */
internal val BubbleCorner = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

// ios: Views/HomeView.swift:3,4,9
@Composable
fun HomeView(m: AppModel) {
    // ios: Views/HomeView.swift:5
    var inbox by remember { mutableStateOf(false) }
    // ios: Views/HomeView.swift:6 금액 가리기는 기기에 남는다
    var hideAmt by rememberPrefBool("hideAmt", false)
    val lookRaw = BoxLook.raw   // ios: Views/HomeView.swift:7
    // ios: Views/HomeView.swift:33 기록·소식 계산은 앱을 열 때와 1분 시세 갱신 때 한다 (App). 탭 이동 땐 말풍선만
    LaunchedEffect(Unit) { Remind.shared.nextBubble() }
    // ios: Views/HomeView.swift:10,30,31
    Refreshable({ Market.shared.pullRefresh(m) }, Modifier.background(Theme.bg)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // ios: Views/HomeView.swift:28
            Screen {
                // ios: Views/HomeView.swift:11
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 위젯·설정은 맨 아래 버튼 대신 우상단 아이콘으로 (2026-10-24)
                    Box(Modifier.outset(16.dp)) {   // ios: Views/HomeView.swift:13 (-16)
                        AppHeader(tab = "홈")
                        // ios: Views/HomeView.swift:14,15,18,19 overlay(.trailing) · 기호 오른쪽 끝을 평가액 박스보다 조금 안쪽에 (실기기 재서 2 → 6.5)
                        Row(Modifier.align(Alignment.CenterEnd).padding(end = 6.5.dp)) {
                            HeaderIcon("위젯", "rectangle.3.group") { m.widgetsOpen = true }   // ios: Views/HomeView.swift:16
                            HeaderIcon("설정", "gearshape") { m.openSettings() }   // ios: Views/HomeView.swift:17
                        }
                    }
                    SampleNote(m)   // ios: Views/HomeView.swift:13
                    Summary(m, lookRaw, { BoxLook.raw = it }, hideAmt) { hideAmt = !hideAmt }   // ios: Views/HomeView.swift:14
                    // '매달 더 넣으면?'은 홈에 두지 않는다. 미션 화면에만 (2026-10-26)   // ios: Views/HomeView.swift:24
                    ShelterBox(m) { inbox = true }   // ios: Views/HomeView.swift:17
                    // 말풍선은 한 줄 해석, 바로 아래 '오늘 많이 움직인 종목'이 그 근거 (2026-10-23)
                    HoldingsNews(m, moversOnly = true)   // ios: Views/HomeView.swift:19
                    ParallelHomeCard(m)   // ios: Views/HomeView.swift:20
                    Reminder { inbox = true }   // ios: Views/HomeView.swift:21
                    EarnHomeCard(m)   // ios: Views/HomeView.swift:22
                    TownButton { TownView(m, scroll = it) }   // ios: Views/HomeView.swift:23
                }
            }
        }
    }
    // ios: Views/HomeView.swift:34
    Sheet(inbox, onDismiss = { inbox = false; Remind.shared.markAllSeen() }) { RemindSheet(m) { inbox = false; Remind.shared.markAllSeen() } }
    // ios: Views/HomeView.swift:35 이동 막대 숨김: 첫 화면엔 막대가 없다 (NavStack 은 쌓인 화면에만 막대를 그린다)
}

/** 홈에서 여는 화면 (navigationDestination for: String) */
// ios: Views/HomeView.swift:36
fun homePage(m: AppModel, route: String): NavPage {
    // 이동 막대 제목은 화면이 정한다 (navigationTitle). 읽기·인물 상세는 막대를 스스로 그린다
    val parts = route.split(":")   // ios: Views/HomeView.swift:47 char:<인물> = 소설 속 갈래로, char:<인물>:invest = 투자하는 갈래로, …:invest:h<번호> = 그 버릇을 펴서
    return when {
        route == "shelter" -> shelterPage(m)   // ios: Views/HomeView.swift:37
        route == "library" -> shelterPage(m, onlyLibrary = true)   // ios: Views/HomeView.swift:38
        route == "parallel" -> NavPage(ParallelTitles.main) { ParallelView(m) }   // ios: Views/HomeView.swift:39
        route == "parallel:basket" -> NavPage(ParallelTitles.basket) { val nb = LocalNavBack.current; ParallelBasketView(m) { nb?.pop?.invoke() } }   // ios: Views/HomeView.swift:40
        route == "goal" -> NavPage { GoalSetView(m, fromSettings = true) }   // ios: Views/HomeView.swift:41
        route.startsWith("read:") && route.drop(5).toIntOrNull() != null -> readerPage(m, index = route.drop(5).toInt())   // ios: Views/HomeView.swift:42
        route.startsWith("vol1:") && route.drop(5).toIntOrNull() != null -> readerPage(m, index = route.drop(5).toInt(), book = StoryBook.vol1)   // ios: Views/HomeView.swift:43
        route == "me" -> meEditPage(m)   // ios: Views/HomeView.swift:44 (제목은 화면이 정한다)
        // ios: Views/HomeView.swift:45,48,49
        route.startsWith("char:") && Shelter.friends.any { it.id == route.drop(5).split(":").firstOrNull() } ->
            characterPage(m, Shelter.friends.first { it.id == route.drop(5).split(":").firstOrNull() }, invest = parts.contains("invest"), focus = parts.lastOrNull()?.let { if (it.startsWith("h")) it.drop(1).toIntOrNull() else null })
        else -> NavPage {}
    }
}

// 평가액 박스: 고른 모양 (기본 점프 직전). 길게 누르면 모양 고르기 (설정 > 화면에도 있다)
// ios: Views/HomeView.swift:55
@Composable
private fun Summary(m: AppModel, lookRaw: String, setLook: (String) -> Unit, hideAmt: Boolean, toggleHide: () -> Unit) {
    val look = BoxLook.entries.firstOrNull { it.name == lookRaw } ?: BoxLook.jump   // ios: Views/HomeView.swift:56
    var menu by remember { mutableStateOf(false) }
    // ios: Views/HomeView.swift:57,64 (.contextMenu: 길게 누르면 메뉴)
    Box(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { menu = true }) }
        .semantics { onLongClick(label = tr("평가액 박스 모양")) { menu = true; true } }) {   // ios: Views/HomeView.swift:65
        // ios: Views/HomeView.swift:58,59,68 (.boxScheme(): 상단 박스만 고른 밝기 모드)
        BoxSchemeScope {
            if (look == BoxLook.bar) {
                if (m.isGoal) GoalSummary(m, hideAmt, toggleHide) else RecoverSummary(m, hideAmt, toggleHide)
            } else {   // ios: Views/HomeView.swift:60
                SceneSummary(m, look, hideAmt, toggleHide)   // ios: Views/HomeView.swift:61
            }
        }
    }
    // ios: Views/HomeView.swift:66 Picker: 고른 모양에 ✓
    ContextMenu(menu, BoxLook.entries.map { l -> MenuItem(l.label, icon = if (l == look) "checkmark" else null, onClick = { setLook(l.name) }) }) { menu = false }
}

/** 장면 박스 값: 가로 자리 = 지금 값 ÷ 목표(본전), 계획 자리 = 내 길 계획선(적립 포함)의 오늘 값, 눈금 = 계획선의 1년·2년 값.
 *  내 길 계산이 무거워 값(AppModel 상태)이 바뀔 때만 다시 센다 */
// ios: Views/HomeView.swift:72
private fun sceneOf(m: AppModel): BoxScene {
    val p = m.myPath; val K = max(1.0, p.key); val me = m.trackValue; val n = p.need(p.start)   // ios: Views/HomeView.swift:73
    val onPlan = n <= 0 || me >= n   // ios: Views/HomeView.swift:74
    // ios: Views/HomeView.swift:75
    if (m.isGoal) {
        val value = m.isGoal && !m.bare   // ios: Views/HomeView.swift:76
        // ios: Views/HomeView.swift:79
        return BoxScene(label = if (value) "평가액" else "모은 돈",
            amount = if (m.bare) m.goalSaved * 1e4 else m.total,   // ios: Views/HomeView.swift:80
            f = me / K, plan = if (n > 0) n / K else null,   // ios: Views/HomeView.swift:81
            target = AppModel.wonK(m.gK),   // ios: Views/HomeView.swift:82
            today = if (value && m.route == Route.plus && m.total > 0) m.todayMove else null,   // ios: Views/HomeView.swift:83
            ret = if (value && m.cost > 0) m.ret else null,   // ios: Views/HomeView.swift:84
            status = if (m.goalReached) "목표 도달!" else if (onPlan) "계획대로" else "조금 늦어요")   // ios: Views/HomeView.swift:85
    }
    val need = m.cost / max(1.0, m.total) - 1   // ios: Views/HomeView.swift:87
    // ios: Views/HomeView.swift:88
    return BoxScene(label = "평가액", amount = m.total, f = me / K, plan = if (n > 0) n / K else null,
        target = "본전",   // ios: Views/HomeView.swift:89
        today = if (m.total > 0) m.todayMove else null, ret = m.ret,   // ios: Views/HomeView.swift:90
        status = if (need > 0) "본전까지 +${cformat("%.1f", need * 100)}%" else "본전 도달")   // Views/HomeView.swift:92 (검사 예외: 옮김)
}

// ios: Views/HomeView.swift:94
@Composable
private fun SceneSummary(m: AppModel, look: BoxLook, hideAmt: Boolean, toggleHide: () -> Unit) {
    val scene by remember(m) { derivedStateOf { sceneOf(m) } }   // ios: Views/HomeView.swift:72 (scene)
    // 적립 정하기·목표 루트로·다음 목표 정하기는 홈에 두지 않는다. 미션에만 (2026-10-27)
    // ios: Views/HomeView.swift:97
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(boxInk().card), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        SceneBox(look = look, s = scene, hide = hideAmt, toggleHide = toggleHide)
    }
}

private val C9 = hex(0xC9D0D6)

// 목표 루트: 지금 평가액(플러스) 또는 모은 돈(시작 전)과 목표 진행
// ios: Views/HomeView.swift:119
@Composable
private fun GoalSummary(m: AppModel, hideAmt: Boolean, toggleHide: () -> Unit) {
    val ink = boxInk()   // ios: Views/HomeView.swift:119 (라이트는 흰 카드 + 짙은 글자)
    // ios: Views/HomeView.swift:120
    val now = if (m.bare) m.goalSaved else m.total / 1e4; val value = m.isGoal && !m.bare; val prog = min(1.0, now / max(1.0, m.gK))
    // ios: Views/HomeView.swift:121,158,159,160,161
    Fg(ink.ink) {
        Column(
            Modifier.fillMaxWidth().background(ink.card, RoundedCornerShape(20.dp)).border(1.dp, ink.cardRim, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ios: Views/HomeView.swift:122,124
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (value) "평가액" else "모은 돈", Modifier.weight(1f), style = appFont(13f), color = ink.sub)   // ios: Views/HomeView.swift:123
                if (value) FreshLabel()   // ios: Views/HomeView.swift:125
            }
            // ios: Views/HomeView.swift:127
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 금액은 길어도 꺾지 않고 한 줄로, 자리가 모자라면 글자를 줄인다
                // 누르면 금액 가리기(다시 누르면 보이기)
                // ios: Views/HomeView.swift:130,131,132
                FitText(if (hideAmt) "••••••" else AppModel.man(now * 1e4) + "원", appFont(32f, FontWeight.Bold).copy(letterSpacing = (-0.5).sp), 0.4f,
                    Modifier.weight(1f, fill = false).alignByBaseline().plainClickable(toggleHide), maxLines = 1, roll = now)
                // ios: Views/HomeView.swift:133
                if (value && m.cost > 0) {
                    // ios: Views/HomeView.swift:134,135
                    FitText(AppModel.sgn(m.ret), appFont(14f, FontWeight.Bold), 0.6f, Modifier.alignByBaseline(), maxLines = 1, color = if (m.ret >= 0) ink.up else ink.down)
                }
            }
            if (value && m.route == Route.plus && m.total > 0) TodayLine(m)   // ios: Views/HomeView.swift:138
            // ios: Views/HomeView.swift:139
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("목표 ${AppModel.wonK(m.gK)} · ${m.gY}년", Modifier.weight(1f), style = appFont(13f), color = ink.sub)   // ios: Views/HomeView.swift:140,143
                T(AppModel.pct(prog), style = appFont(13f, FontWeight.Bold), color = ink.gold)   // ios: Views/HomeView.swift:141
            }
            ProgressBar(value = prog, height = 8.dp, track = bx(ink.dk, 0xE6E9EE, 0x3A4651))   // ios: Views/HomeView.swift:144
            // ios: Views/HomeView.swift:145
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (m.goalReached) "목표 ${AppModel.wonK(m.gK)} 도달!"   // ios: Views/HomeView.swift:146
                  else if (m.bare) "매달 ${AppModel.wonK(m.gM)} 적립 · 다음 적립일 +${AppModel.wonK(m.gM)}"   // ios: Views/HomeView.swift:147
                  else AppModel.paceText(m.goalPace()) + (if (m.gM > 0) " · 다음달 ${AppModel.wonK(m.gM)} 적립" else " · 추가 적립 미정"),   // ios: Views/HomeView.swift:148
                  Modifier.weight(1f), style = appFont(13f), color = ink.sub)   // ios: Views/HomeView.swift:149
            }
        }
    }
}

// ios: Views/HomeView.swift:164
@Composable
private fun RecoverSummary(m: AppModel, hideAmt: Boolean, toggleHide: () -> Unit) {
    val ink = boxInk()
    val need = m.cost / max(1.0, m.total) - 1   // ios: Views/HomeView.swift:165
    val needY = m.cost / (m.total / (1 + m.todayMove)) - 1   // ios: Views/HomeView.swift:166
    // ios: Views/HomeView.swift:167,195,196,197,198
    Fg(ink.ink) {
        Column(
            Modifier.fillMaxWidth().background(ink.card, RoundedCornerShape(20.dp)).border(1.dp, ink.cardRim, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ios: Views/HomeView.swift:168,170
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("평가액 · 넣은 돈 ${AppModel.man(m.cost)}", Modifier.weight(1f), style = appFont(13f), color = ink.sub)   // ios: Views/HomeView.swift:169
                FreshLabel()   // ios: Views/HomeView.swift:171
            }
            // ios: Views/HomeView.swift:173
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // ios: Views/HomeView.swift:174,175,176 (금액을 누르면 가린다)
                FitText(if (hideAmt) "••••••" else AppModel.man(m.total), appFont(32f, FontWeight.Bold).copy(letterSpacing = (-0.5).sp), 0.4f, Modifier.weight(1f, fill = false).alignByBaseline().plainClickable { toggleHide() }, maxLines = 1, color = ink.ink, roll = m.total)
                // ios: Views/HomeView.swift:177,178
                T(AppModel.sgn(m.ret), Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold), maxLines = 1, color = if (m.ret >= 0) ink.up else ink.down)
            }
            if (m.total > 0) TodayLine(m)   // ios: Views/HomeView.swift:180
            // ios: Views/HomeView.swift:181
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("본전까지", Modifier.weight(1f), style = appFont(13f), color = ink.sub)   // ios: Views/HomeView.swift:182,183,187
                // ios: Views/HomeView.swift:184,185
                T(if (need > 0) "+" + cformat("%.1f", need * 100) + "% 남음" else "본전 도달", style = appFont(13f, FontWeight.Bold), color = ink.gold)
            }
            ProgressBar(value = m.total / max(1.0, m.cost), height = 8.dp, track = bx(ink.dk, 0xE6E9EE, 0x3A4651))   // ios: Views/HomeView.swift:188
            // ios: Views/HomeView.swift:189,191,192 (118 은 검사 예외: 같은 줄에 옮김)
            T(if (need > 0) "어제 ${cformat("%.1f", needY * 100)}% → 오늘 ${cformat("%.1f", need * 100)}%"
              else "본전 넘음 · 종목·친구 그대로", style = appFont(13f), color = ink.sub)
        }
    }
}

// 오늘 등락: 부호는 한 번만 (▲/▼ 뒤 퍼센트·금액은 부호 없이). 목표·회복 카드 공통
// ios: Views/HomeView.swift:202
@Composable
private fun TodayLine(m: AppModel) {
    val ink = boxInk()
    val mv = m.todayMove; val diff = m.stockTotal - m.stockTotal / (1 + mv)   // ios: Views/HomeView.swift:203
    // ios: Views/HomeView.swift:204,205
    T("오늘 " + (if (mv >= 0) "▲ " else "▼ ") + cformat("%.1f%%", abs(mv) * 100) + " · " + AppModel.won(abs(diff)),
      style = appFont(13f), color = if (mv >= 0) ink.up else ink.down)
}

// 쉼터 박스: 홈에 둔 친구 + 말풍선 (리마인드 소식이 있으면 그 소식) + 새 소식 + 다른 친구 칸
// ios: Views/HomeView.swift:218
@Composable
private fun ShelterBox(m: AppModel, openInbox: () -> Unit) {
    val on = (0 until 5).count { m.friendOn(it) }   // ios: Views/HomeView.swift:219
    val next = (0 until 5).firstOrNull { !m.friendOn(it) }   // ios: Views/HomeView.swift:220
    val r = Remind.shared; val b = r.bubble; val n = r.fresh.size   // ios: Views/HomeView.swift:221
    // 말풍선은 오늘 브리핑 한 문장. 인물 대사는 빼고, 인물은 오늘 장 흐름을 동작으로 보인다 (2026-10-23)
    // 리마인드 소식은 정보라 브리핑 아래에 둔다. 둘 다 없을 때(종목 없음)만 인물 한마디
    val news = b?.let { it.text + (if (it.go != RemindGo.nowhere) " ›" else "") }   // ios: Views/HomeView.swift:224
    val say = listOfNotNull(m.todayMoversText, news).joinToString("\n")   // ios: Views/HomeView.swift:225
    val who = m.homeFriendShown   // ios: Views/HomeView.swift:226
    val tint: Color = b?.let { it.cat.color.copy(alpha = 0.14f) } ?: Theme.mintBg   // ios: Views/HomeView.swift:227
    // ios: Views/HomeView.swift:228,285,286,287
    Column(
        Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ios: Views/HomeView.swift:229,230,231,249,251,252
        Row(
            Modifier.fillMaxWidth()
                .plainClickable { if (b != null && b.go != RemindGo.nowhere) m.go(b.go) else m.homePath = m.homePath + "shelter" }
                .semantics(mergeDescendants = true) { contentDescription = tr(b?.let { "${it.cat.label} 소식: ${it.text}" } ?: "쉼터 보기") },
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),   // ios: Views/HomeView.swift:232
        ) {
            // ios: Views/HomeView.swift:233,240 (읽어 주기는 바깥 묶음 글이 맡는다)
            Box {
                // ios: Views/HomeView.swift:234,235
                if (if (who == "me") m.me != null else FriendMotion.has(who)) {
                    MoodFigure(who = who, me = m.me, mood = DayMood.of(m.todayMove), width = 56.dp, height = 80.dp)
                } else {   // ios: Views/HomeView.swift:236
                    CharPixel(id = who, width = 56.dp, height = 80.dp, modifier = Modifier.charIdle(), model = m)   // ios: Views/HomeView.swift:237
                }
            }
            // ios: Views/HomeView.swift:241,242,243,244,245,246,247
            T(if (say.isEmpty()) m.homeSay else say, Modifier.weight(1f).background(tint, RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
              style = appFont(14f).ls(3f).copy(textAlign = TextAlign.Start))
        }
        // ios: Views/HomeView.swift:253
        if (r.any) {
            // ios: Views/HomeView.swift:254,255,266
            Row(Modifier.fillMaxWidth().offset(y = (-4).dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/HomeView.swift:256,257,258,259,260,261,263,264
                Box(Modifier.defaultMinSize(minHeight = 32.dp).plainClickable(openInbox)
                    .semantics { contentDescription = tr(if (n > 0) "새 소식 ${n}개 보기" else "소식함 보기") }, contentAlignment = Alignment.Center) {
                    T(if (n > 0) "새 소식 $n ›" else "소식함 ›", Modifier.background(if (n > 0) Theme.yellow else Theme.line, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                      style = appFont(12f, FontWeight.Bold), color = if (n > 0) Theme.inkFixed else Theme.sub)
                }
            }
        }
        if (on < 5) {   // ios: Views/HomeView.swift:268
        HDivider(Theme.line)   // ios: Views/HomeView.swift:269
        // ios: Views/HomeView.swift:270,279,281,282
        Row(
            Modifier.fillMaxWidth().plainClickable { m.homePath = m.homePath + "shelter" }.semantics(mergeDescendants = true) { contentDescription = tr("쉼터 보기") },
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),   // ios: Views/HomeView.swift:271
        ) {
            // ios: Views/HomeView.swift:272,273
            Shelter.friends.withIndex().filter { it.value.id != m.homeFriendShown }.forEach { (i, f) ->
                PixelN((if (m.friendOn(i)) "spr_" else "sil_") + f.id, width = 20.dp, height = 28.dp, alpha = if (m.friendOn(i)) 1f else 0.5f)
            }
            Spacer(Modifier.weight(1f))   // ios: Views/HomeView.swift:275
            // ios: Views/HomeView.swift:276,277
            FitTwoLines("친구 $on/5" + (next?.let { " · 다음 ${Shelter.friends[it].name} ${m.friendWhen(it)}" } ?: " · 외전 5장") + " ›", appFont(12f), 0.8f, Theme.sub, lines = 2)
        }
        }
    }
}

/** .lineLimit(2).minimumScaleFactor(k).multilineTextAlignment(.trailing): 두 줄까지, 넘치면 글자를 줄인다 */
@Composable
private fun FitTwoLines(text: String, style: androidx.compose.ui.text.TextStyle, minScale: Float, color: Color, lines: Int) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints {
        var k = 1f
        if (constraints.hasBoundedWidth) {
            while (k > minScale && measurer.measure(com.naeilo.app.core.tr(text), style.copy(fontSize = style.fontSize * k), maxLines = lines, constraints = Constraints(maxWidth = constraints.maxWidth)).hasVisualOverflow) k -= 0.05f
        }
        T(text, style = style.copy(fontSize = style.fontSize * maxOf(k, minScale), textAlign = TextAlign.End), color = color, maxLines = lines)
    }
}

/** 실적 나온 날 (숫자 기다리는 중이거나 오늘 발표): 보유 종목마다 "{회사} 7~9월 실적" + 작은 막대 둘 + 작년 대비 %.
 *  금액·평가하는 말 없음. 누르면 강 팝업. 나온 종목이 없으면 안 보인다 */
@Composable
private fun EarnHomeCard(m: AppModel) {   // ios: Views/HomeView.swift:329,330
    var river by remember { mutableStateOf<com.naeilo.app.ui.hold.EarnTarget?>(null) }   // ios: Views/HomeView.swift:331
    // ios: Views/HomeView.swift:333,335,336,337
    // 발표가 먼저인 종목부터 (기다리는 중이면 발표일, 아니면 마지막 분기 발표일)
    val items = m.rows.mapNotNull { r -> com.naeilo.app.data.EarningsFeed.shared.summary(r.sym, r.h.qty)?.takeIf { it.fresh }?.let { r to it } }
        .sortedBy { it.second.pending ?: it.second.announced }   // ios: Views/HomeView.swift:338
    if (items.isEmpty()) return   // ios: Views/HomeView.swift:339
    // ios: Views/HomeView.swift:340,366,367
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items.forEachIndexed { i, (r, s) ->   // ios: Views/HomeView.swift:341
            // ios: Views/HomeView.swift:342,359
            Row(
                Modifier.fillMaxWidth().plainClickable { river = com.naeilo.app.ui.hold.EarnTarget(r.sym, r.h.qty) }
                    .defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp)
                    .semantics { onClick(label = tr("내 몫 실적 보기")) { river = com.naeilo.app.ui.hold.EarnTarget(r.sym, r.h.qty); true } },   // ios: Views/HomeView.swift:361,362
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),   // ios: Views/HomeView.swift:343
            ) {
                // 발표했지만 숫자(신고서)가 아직이면 지난 분기가 아니라 발표한 분기 이름으로
                T("${r.sym.name} ${if (s.pending != null) EarnHomeCard.nextMonths(s.months) else s.months}월 실적", Modifier.weight(1f).padding(end = 4.dp), style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/HomeView.swift:345,346
                // ios: Views/HomeView.swift:347
                if (s.pending != null) {
                    T("숫자 기다리는 중", style = appFont(13f), color = Theme.sub, maxLines = 1)   // ios: Views/HomeView.swift:348
                } else {   // ios: Views/HomeView.swift:349
                    // ios: Views/HomeView.swift:350,351,352
                    if (s.netYoY != null) { val y = s.netYoY; T(if (y.startsWith("+") || y.startsWith("−")) "작년보다 $y" else y, style = appFont(13f).copy(fontFeatureSettings = "tnum"), color = Theme.sub, maxLines = 1) }
                    // ios: Views/HomeView.swift:353,354
                    else if (s.loss) T("적자 ${com.naeilo.app.data.Earnings.money(s.short, s.currency)}", style = appFont(13f).copy(fontFeatureSettings = "tnum"), color = Theme.sub, maxLines = 1)
                    com.naeilo.app.ui.hold.EarnBars(s)   // ios: Views/HomeView.swift:356
                }
            }
            if (i < items.size - 1) HDivider(Theme.line, Modifier.padding(start = 14.dp))   // ios: Views/HomeView.swift:363
        }
    }
    com.naeilo.app.ui.hold.EarningsRiverSheet(river) { river = null }   // ios: Views/HomeView.swift:368
}

// ios: Views/HomeView.swift:329 (struct EarnHomeCard 의 static)
object EarnHomeCard {
    /** 다음 분기 달: "4~6" → "7~9" */
    // ios: Views/HomeView.swift:373
    fun nextMonths(m: String): String {
        val q = listOf("1~3", "4~6", "7~9", "10~12")   // ios: Views/HomeView.swift:374
        return q.indexOf(m).takeIf { it >= 0 }?.let { q[(it + 1) % 4] } ?: m   // ios: Views/HomeView.swift:375
    }
}

/** 노란 띠: 3일 안의 일정 (내 종목 먼저). 없으면 인터미션 이번 할 일. 누르면 소식함 */
// ios: Views/HomeView.swift:291
@Composable
private fun Reminder(openInbox: () -> Unit) {
    val r = Remind.shared   // ios: Views/HomeView.swift:292
    val e = r.strip
    // ios: Views/HomeView.swift:293 (212 는 검사 예외: 같은 줄에 옮김)
    if (e != null) StripRow(tag = "일정", text = "${MarketEvents.`when`(e.day ?: "")} ${e.text} · ${e.sub ?: ""}", openInbox)
    // 인터미션 할 일은 미션에만 (2026-10-27)
}

// ios: Views/HomeView.swift:299
@Composable
private fun StripRow(tag: String, text: String, openInbox: () -> Unit) {
    // ios: Views/HomeView.swift:300,301,308,309,310,312,313
    Row(
        Modifier.fillMaxWidth().plainClickable(openInbox).semantics(mergeDescendants = true) { contentDescription = tr("$tag: $text. 소식함 열기") }
            .defaultMinSize(minHeight = 44.dp).background(Theme.cream, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ios: Views/HomeView.swift:302,303
        T(tag, Modifier.background(Theme.yellow, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed)
        T(text, Modifier.weight(1f).padding(end = 0.dp), style = appFont(13f), maxLines = 2)   // ios: Views/HomeView.swift:304,305 (Spacer minLength 0)
        Sym("chevron.right", 11f, Theme.muted)   // ios: Views/HomeView.swift:306
    }
}

/** 작은 알약 두 개를 가운데에 (꽉 찬 버튼은 거슬려 줄였다, 2026-10-23). 누르는 자리는 44 높이 그대로 */
// ios: Views/HomeView.swift:321,322,328,329
@Composable
private fun HeaderIcon(t: String, icon: String, act: () -> Unit) {
    // ios: Views/HomeView.swift:323,324,325,326,327
    Box(Modifier.size(44.dp).plainClickable(act).semantics { contentDescription = tr(t) }, contentAlignment = Alignment.Center) {
        Sym(icon, 16f, Theme.ink)
    }
}

// 오늘의 1분: 내 종목 오늘 숫자 확인 + 날마다 바뀌는 질문 하나 (지난 7일과 안 겹치게). 설명은 계산값으로.
// ios: Views/HomeView.swift:380,381,388
@Composable
fun RoutineCard(m: AppModel) {
    val days = listOf("월", "화", "수", "목", "금", "토", "일")   // ios: Views/HomeView.swift:382
    /** 이번 주 칸 중 채운 날 수 (요일 7칸과 같은 기준) */
    // ios: Views/HomeView.swift:384,385
    val weekDone = (0 until 7).count { i -> val d = m.day - m.day % 7 + i; m.dayLog[d]?.let { it.answer != null } ?: (d < 5 && d < m.day) }
    val t = m.today; val q = m.question   // ios: Views/HomeView.swift:389
    Fg(Color.White) {
        // ios: Views/HomeView.swift:390,410,423,451,455,456,457 withAnimation: 확인·답·날 넘기기로 바뀌는 칸은 기본 스프링으로 자란다
        Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).animateContentSize(spring(dampingRatio = 1f, stiffness = stiffness(0.55f))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ios: Views/HomeView.swift:391,393
            Row(Modifier.fillMaxWidth()) {
                T("오늘의 1분", Modifier.weight(1f).alignByBaseline(), style = appFont(13f, FontWeight.Bold).copy(letterSpacing = 1.sp), color = Theme.mint)   // ios: Views/HomeView.swift:392
                T("이번 주 ${weekDone}일", Modifier.alignByBaseline(), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:394
            }
            // ios: Views/HomeView.swift:396
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/HomeView.swift:397
                for (i in 0 until 7) {
                    val d = m.day - m.day % 7 + i   // ios: Views/HomeView.swift:398
                    val ok = m.dayLog[d]?.let { it.answer != null } ?: (d < 5 && d < m.day)   // ios: Views/HomeView.swift:399
                    // ios: Views/HomeView.swift:400,401,402,403,404
                    Box(
                        Modifier.weight(1f).defaultMinSize(minHeight = 28.dp)
                            .background(if (ok) Theme.mint else if (d == m.day) Color.Transparent else Theme.slate, RoundedCornerShape(8.dp))
                            .then(if (d == m.day && !ok) Modifier.border(2.dp, Theme.yellow, RoundedCornerShape(8.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { T(days[i], style = appFont(12f, FontWeight.Bold), color = if (ok) Theme.inkFixed else if (d == m.day) Theme.yellow else Theme.muted) }
                }
            }
            // ios: Views/HomeView.swift:407
            Step(tag = "1 · 오늘 숫자") {
                TA(md(m.routineFact), style = appFont(15f).ls(3f))   // ios: Views/HomeView.swift:408
                // ios: Views/HomeView.swift:409
                if (!t.seen) {
                    // ios: Views/HomeView.swift:410,411,412,413
                    Box(Modifier.plainClickable { m.markSeen() }.defaultMinSize(minHeight = 40.dp).background(Theme.teal, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        T("확인했어요", style = appFont(14f, FontWeight.Bold), color = Color.White)
                    }
                }
            }
            // ios: Views/HomeView.swift:416,417
            if (t.seen) Step(tag = "2 · ${q.tag}") {
                T(q.q, style = appFont(15f, FontWeight.SemiBold).ls(3f))   // ios: Views/HomeView.swift:418
                // ios: Views/HomeView.swift:419,420
                FlowRow(spacing = 6.dp) {
                    q.opts.indices.forEach { i ->
                        val ans = t.answer   // ios: Views/HomeView.swift:421
                        val right = q.right >= 0 && i == q.right   // ios: Views/HomeView.swift:422
                        // ios: Views/HomeView.swift:423,424,425,426,427,428,429
                        Box(
                            Modifier.then(if (ans == null) Modifier.plainClickable { m.answer(i) } else Modifier.semantics { disabled() })
                                .defaultMinSize(minHeight = 40.dp)
                                .background(if (ans == null) Theme.card else if (right) Theme.tealBg else if (i == ans) Theme.night else Theme.card, RoundedCornerShape(10.dp))
                                .border(2.dp, if (ans == null) Theme.dash else if (right) Theme.teal else if (i == ans) Theme.ink else Theme.track, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { T(q.opts[i], style = appFont(14f, FontWeight.Bold), color = if (ans == null) Theme.ink else if (right) Theme.tealDark else if (i == ans) Color.White else Theme.muted) }
                    }
                }
                // ios: Views/HomeView.swift:432
                t.answer?.let { a ->
                    // ios: Views/HomeView.swift:433,434,435,436,437
                    T((t.ok?.let { if (it) "맞아요! " else "아쉬워요. " } ?: "") + q.fb(a),
                      Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp), style = appFont(13f).ls(3f))
                }
            }
            // ios: Views/HomeView.swift:441
            if (t.answer != null) {
                val sc = m.quizScore   // ios: Views/HomeView.swift:442
                // ios: Views/HomeView.swift:443,445
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(m.routineDoneText, Modifier.weight(1f).alignByBaseline(), style = appFont(14f, FontWeight.SemiBold), color = Theme.yellow)   // ios: Views/HomeView.swift:444
                    if (sc.all > 0) T("퀴즈 ${sc.right}/${sc.all}", Modifier.alignByBaseline(), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/HomeView.swift:446
                }
            }
            // 날짜 넘기기는 개발자 확인용 (일반 사용자 화면에는 안 보인다)
            // ios: Views/HomeView.swift:450,451,452
            if (m.devAll) Box(Modifier.defaultMinSize(minHeight = 40.dp).plainClickable { m.day += 1 }, contentAlignment = Alignment.Center) {
                T("다음 날로 넘기기 (개발자)", style = appFont(13f).copy(textDecoration = TextDecoration.Underline), color = Theme.muted)
            }
        }
    }
}

/** **굵게** 만 읽는 짧은 마크다운 (AttributedString(markdown:) inlineOnly 자리) */
private fun md(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf("**", i)
        val b = if (a >= 0) s.indexOf("**", a + 2) else -1
        if (a < 0 || b < 0) { append(s.substring(i)); break }
        append(s.substring(i, a))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(a + 2, b)) }
        i = b + 2
    }
}

// ios: Views/HomeView.swift:460,461,465,466,467,468
@Composable
private fun Step(tag: String, c: @Composable () -> Unit) {
    Fg(Theme.ink) {
        Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(tag, style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/HomeView.swift:462
            c()   // ios: Views/HomeView.swift:463
        }
    }
}

// 줄바꿈되는 가로 배치
@Composable
// ios: Views/HomeView.swift:519,520,521,531
fun FlowRow(spacing: Dp = 6.dp, content: @Composable () -> Unit) {
    Layout(content) { ms, c ->
        val sp = spacing.roundToPx()
        val w = if (c.hasBoundedWidth) c.maxWidth else Int.MAX_VALUE   // ios: Views/HomeView.swift:522
        val ps = ms.map { it.measure(Constraints()) }   // ios: Views/HomeView.swift:525,534 (.unspecified)
        var x = 0; var y = 0; var rowH = 0; var maxX = 0   // ios: Views/HomeView.swift:523,532
        val at = ps.map { s ->   // ios: Views/HomeView.swift:524,533
            if (x > 0 && x + s.width > w) { x = 0; y += rowH + sp; rowH = 0 }   // ios: Views/HomeView.swift:526,535
            val o = Pair(x, y)   // ios: Views/HomeView.swift:536
            x += s.width + sp; rowH = max(rowH, s.height); maxX = max(maxX, x - sp)   // ios: Views/HomeView.swift:527,537
            o
        }
        layout(min(w, maxX), y + rowH) { ps.forEachIndexed { i, p -> p.place(at[i].first, at[i].second) } }   // ios: Views/HomeView.swift:529
    }
}

/** 샘플 종목 안내: 새 사용자는 가상 종목 DRNK·QQQ 샘플로 시작한다. 내 종목을 넣기 전까지 탭마다 보인다 */
// ios: Views/HomeView.swift:543,544,545
@Composable
fun SampleNote(m: AppModel) {
    // ios: Views/HomeView.swift:546
    if (m.isSample) {
        // ios: Views/HomeView.swift:547,553,554,555
        Row(
            Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.orange.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ios: Views/HomeView.swift:548,549
            T("샘플", Modifier.alignByBaseline().background(Theme.orange, CapsuleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = appFont(12f, FontWeight.Bold), color = Color.White)
            // ios: Views/HomeView.swift:550,551
            T("지금 숫자는 샘플(가상 종목 DRNK·QQQ)이에요. 종목 탭에서 내 종목을 넣으면 바뀌어요.", Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)
        }
    }
}


// 평가액 박스 모양 (2026-10-23). 사용자가 고른다 점프 직전(기본) → 지평선 → 욕조와 오리 → 도리와 문어 → 피노키오와 새 → 철수와 영희 → 진행 막대(예전 모습)
// 색은 테마마다 라이트·다크 한 쌍 (상단 박스 밝기 모드: 자동·밝게·어둡게, BoxScheme). 모양·움직임은 한 벌
// 장면은 300×170 판에 그리고 박스 폭에 맞춰 늘린다. 글자는 장면 왼쪽 위에 겹친다.
// 가로 위치·물높이 = 지금 값 ÷ 목표(목표 루트) 또는 넣은 돈(회복 루트). 계획 자리 = 내 길 계획선(적립 포함, myPath.need)의 오늘 값.
// 움직임은 작은 조각만 반복 애니메이션 (장면 그림 층은 값이 바뀔 때만 다시 그린다). 동작 줄이기·숨은 탭이면 멈춘다

// ios: Views/HomeView.swift:565
enum class BoxLook {
    jump, horizon, tub, dory, pino, mug, bar;   // ios: Views/HomeView.swift:527
    val id: String get() = name   // ios: Views/HomeView.swift:567
    /** 보이는 이름 (Swift name: Kotlin enum 의 name 은 rawValue 자리라 label) */
    // ios: Views/HomeView.swift:568,569
    val label: String get() = when (this) {
        horizon -> "지평선"   // ios: Views/HomeView.swift:570
        jump -> "점프 직전"   // ios: Views/HomeView.swift:571
        tub -> "욕조와 오리"   // ios: Views/HomeView.swift:572
        dory -> "도리와 문어"   // ios: Views/HomeView.swift:534
        pino -> "피노키오와 새"   // ios: Views/HomeView.swift:535
        mug -> "구면 뒤집기"   // ios: Views/HomeView.swift:493
        bar -> "진행 막대"   // ios: Views/HomeView.swift:573
    }
    companion object {
        const val key = "homeBoxLook"   // ios: Views/HomeView.swift:576
        /** @AppStorage(key) 자리: 홈과 설정이 같은 값을 보고, 바꾸면 둘 다 다시 그린다 */
        private val state by lazy { mutableStateOf(com.naeilo.app.data.Defaults.prefs.getString(key, null) ?: jump.name) }
        var raw: String
            get() = state.value
            set(v) { state.value = v; com.naeilo.app.data.Defaults.prefs.edit().putString(key, v).apply() }
    }
}

/** 장면에 넘기는 값 한 벌 (HomeView 가 만든다) */
// ios: Views/HomeView.swift:580
data class BoxScene(
    val label: String,          // 평가액 · 모은 돈   // ios: Views/HomeView.swift:581
    val amount: Double,         // 크게 쓰는 금액 (원)   // ios: Views/HomeView.swift:582
    val f: Double,              // 지금 자리 (0 = 출발, 1 = 목표)   // ios: Views/HomeView.swift:583
    val plan: Double?,          // 계획 자리   // ios: Views/HomeView.swift:584
    val target: String,         // 10억 · 본전   // ios: Views/HomeView.swift:586
    val today: Double?,         // 오늘 등락   // ios: Views/HomeView.swift:588
    val ret: Double?,           // 넣은 돈 대비   // ios: Views/HomeView.swift:589
    val status: String,         // 계획대로 · 본전까지 +3.1%   // ios: Views/HomeView.swift:590
)

/** 박스 오른쪽 위 '1분 전'. 오래되면 '10/21 23:50 기준' (Market.freshText). 분이 바뀔 때마다 다시 쓴다 */
@Composable
fun FreshLabel(color: Color = Color.Unspecified) {   // ios: Views/HomeView.swift:594,595
    var now by remember { mutableDoubleStateOf(System.currentTimeMillis() / 1000.0) }
    // ios: Views/HomeView.swift:596,597 TimelineView(.everyMinute)
    LaunchedEffect(Unit) { while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L); now = System.currentTimeMillis() / 1000.0 } }
    T(Market.shared.freshText(now) ?: "", style = appFont(12f), color = if (color == Color.Unspecified) boxInk().dim else color, maxLines = 1)   // ios: Views/HomeView.swift:598
}

/** .font(.system(size:)): 글자 크기 키우기를 따르지 않는 고정 크기 */
@Composable
private fun fx(size: Float): androidx.compose.ui.unit.TextUnit = (size / LocalDensity.current.fontScale).sp

// ios: Views/HomeView.swift:603 (struct SceneBox 의 static)
private object SceneBox {
    val dim @Composable get() = boxInk().dim   // ios: Views/HomeView.swift:570
    val upC @Composable get() = boxInk().up; val downC @Composable get() = boxInk().down   // ios: Views/HomeView.swift:571
}

// ios: Views/HomeView.swift:603,604,605,606
@Composable
fun SceneBox(look: BoxLook, s: BoxScene, hide: Boolean, toggleHide: () -> Unit) {
    val amountText = if (hide) "••••••" else AppModel.man(s.amount)   // ios: Views/HomeView.swift:608
    // 욕조: 아래 수익률 줄까지 타일 벽이 이어지게 (장면 타일과 같은 칸 크기)
    val dk = boxDark()
    val wall = if (look == BoxLook.tub) Modifier.drawBehind { tileWall(22 * size.width / 300, dk) } else Modifier   // ios: Views/HomeView.swift:619
    // ios: Views/HomeView.swift:612,613,620,621
    Column(Modifier.fillMaxWidth().then(wall).semantics(mergeDescendants = true) { contentDescription = tr("${s.label} $amountText, ${s.target}까지 ${AppModel.pct(s.f)}, ${s.status}") },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Scene(look, s, amountText, toggleHide)   // ios: Views/HomeView.swift:614
        // 욕조: 수익률·계획 줄은 장면 아래 따로 (박스 끝에 붙지 않게)
        if (look == BoxLook.tub) SubLine(s, 13f, Modifier.padding(top = 2.dp, bottom = 12.dp))   // ios: Views/HomeView.swift:616
    }
}

// ios: Views/HomeView.swift:624,625,653
@Composable
private fun Scene(look: BoxLook, s: BoxScene, amountText: String, toggleHide: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(300f / 170f)) {
        val k = maxWidth.value / 300   // ios: Views/HomeView.swift:626
        // ios: Views/HomeView.swift:627,628,629,637 (판을 k 배로 늘린 층들. 누름은 받지 않는다)
        Box(Modifier.matchParentSize()) {
            when (look) {
                BoxLook.jump -> JumpScene(s)   // ios: Views/HomeView.swift:630
                BoxLook.tub -> TubScene(s)   // ios: Views/HomeView.swift:631
                BoxLook.dory -> DoryScene(s)   // ios: Views/HomeView.swift:593
                BoxLook.pino -> PinoScene(s)   // ios: Views/HomeView.swift:594
                BoxLook.mug -> SphereScene(s)   // ios: Views/HomeView.swift:554
                else -> HorizonScene(s)   // ios: Views/HomeView.swift:632
            }
        }
        // ios: Views/HomeView.swift:638
        if (look == BoxLook.tub) {
            // 욕조: 금액은 욕조 앞면 (바닥 테두리 위)
            // ios: Views/HomeView.swift:640,641
            Box(Modifier.matchParentSize()) {
                Box(Modifier.centerAt(150 * k, 130 * k).width((200 * k).dp), contentAlignment = Alignment.Center) {
                    AmountView(amountText, s.amount, 20 * k, hex(0x16203A), toggleHide)
                }
            }
            Head(s, Modifier.padding(horizontal = 18.dp).padding(top = 12.dp))   // ios: Views/HomeView.swift:642
        } else {   // ios: Views/HomeView.swift:643
            // ios: Views/HomeView.swift:644,649
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Head(s)   // ios: Views/HomeView.swift:645
                AmountView(amountText, s.amount, 30f, boxInk().ink, toggleHide)   // ios: Views/HomeView.swift:646
                SubLine(s, 13f)   // ios: Views/HomeView.swift:647
            }
        }
    }
}

/** .position(x:y:): 판 좌표(dp)를 가운데로 둔다 */
private fun Modifier.centerAt(x: Float, y: Float): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place((x.dp.toPx() - p.width / 2f).roundToInt(), (y.dp.toPx() - p.height / 2f).roundToInt()) }
}

// ios: Views/HomeView.swift:656,657
@Composable
private fun Head(s: BoxScene, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T(s.label, Modifier.weight(1f), style = appFont(13f), color = SceneBox.dim); FreshLabel() }
}

// ios: Views/HomeView.swift:659,660,661,662
@Composable
private fun AmountView(text: String, amount: Double, size: Float, color: Color, toggleHide: () -> Unit) {
    FitText(text, TextStyle(fontSize = fx(size), fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), 0.5f, Modifier.plainClickable(toggleHide), maxLines = 1, color = color, roll = amount)
}

// ios: Views/HomeView.swift:664,665,666,667
@Composable
private fun SubLine(s: BoxScene, size: Float, modifier: Modifier = Modifier) {
    val st = TextStyle(fontSize = fx(size))
    FitRow(0.7f, modifier) {
        T(s.ret?.let { AppModel.sgn(it) + " · " } ?: "", style = st, color = if ((s.ret ?: 0.0) >= 0) SceneBox.upC else SceneBox.downC, maxLines = 1)
        T(s.status, style = st, color = SceneBox.dim, maxLines = 1)
    }
}

/** 욕조 뒤 남색 타일 벽 (cell = 타일 한 칸 크기) */
// ios: Views/HomeView.swift:672,673,674,675
internal fun DrawScope.tileWall(cell: Float, dk: Boolean = true) {
    drawRect(hex(if (dk) 0x1B2744 else 0xE2ECF8), Offset.Zero, size)   // ios: Views/HomeView.swift:676
    if (cell <= 1) return   // ios: Views/HomeView.swift:677
    val grid = Path(); var x = 0f; var y = 0f   // ios: Views/HomeView.swift:678
    while (x <= size.width) { grid.moveTo(x, 0f); grid.lineTo(x, size.height); x += cell }   // ios: Views/HomeView.swift:679
    while (y <= size.height) { grid.moveTo(0f, y); grid.lineTo(size.width, y); y += cell }   // ios: Views/HomeView.swift:680
    drawPath(grid, hex(if (dk) 0x2A3A60 else 0xC6D6EB), style = Stroke(1.5f * cell / 22))   // ios: Views/HomeView.swift:681
}

/** 글 붙이는 자리 (UnitPoint .leading · .center · .trailing) */
internal enum class Anchor { leading, center, trailing }

/** 장면 그림 도구: 300×170 판 좌표 */
// ios: Views/HomeView.swift:687
internal object Sk {
    /** 평가액 박스 가로 눈금: 모든 모양이 같은 % (욕조는 세로로 따로 그린다) */
    val pctTicks: List<Pair<String, Double>> = listOf(Pair("25%", 0.25), Pair("50%", 0.5), Pair("75%", 0.75))   // ios: Views/HomeView.swift:614
    // ios: Views/HomeView.swift:688,689,690,691,692
    fun p(pts: List<Pair<Float, Float>>): Path {
        val path = Path()
        pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(q.first, q.second) else path.lineTo(q.first, q.second) }
        path.close()
        return path
    }
    fun line(a: Offset, b: Offset): Path { val p = Path(); p.moveTo(a.x, a.y); p.lineTo(b.x, b.y); return p }   // ios: Views/HomeView.swift:694
    fun dot(x: Float, y: Float, r: Float): Path = Path().apply { addOval(Rect(x - r, y - r, x - r + r * 2, y - r + r * 2)) }   // ios: Views/HomeView.swift:695
    /** 판 단위 글자 (판을 늘린 배율을 같이 받는다). 글자 크기는 판 단위라 기기 글자 크기와 상관없다 */
    // ios: Views/HomeView.swift:696,697
    fun text(ctx: DrawScope, tm: TextMeasurer, t: String, x: Float, y: Float, c: Color, size: Float = 9.5f, bold: Boolean = false, anchor: Anchor = Anchor.leading) {
        val r = tm.measure(tr(t), TextStyle(fontSize = (size / (ctx.density * ctx.fontScale)).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = c), softWrap = false)
        val w = r.size.width.toFloat(); val h = r.size.height.toFloat()
        ctx.drawText(r, topLeft = Offset(when (anchor) { Anchor.leading -> x; Anchor.center -> x - w / 2; Anchor.trailing -> x - w }, y - h / 2))
    }
    // ios: Views/HomeView.swift:699
    fun stars(ctx: DrawScope) {
        // ios: Views/HomeView.swift:700,701
        for ((x, y, r) in listOf(Triple(200f, 18f, 0.9f), Triple(240f, 42f, 0.6f), Triple(170f, 30f, 0.5f), Triple(270f, 22f, 0.7f), Triple(120f, 88f, 0.5f))) {
            ctx.drawPath(dot(x, y, r), hex(0xC9D3EA))
        }
    }
    /** 오늘 등락 글: ▼4.3% */
    fun today(v: Double): String = tr("오늘 ") + (if (v >= 0) "▲" else "▼") + cformat("%.1f%%", abs(v) * 100)   // ios: Views/HomeView.swift:705
    fun todayColor(v: Double, dk: Boolean = true): Color = if (v >= 0) hex(if (dk) 0xFF8A80 else 0xC0392B) else hex(if (dk) 0x9EC1FF else 0x1B5E96)   // ios: Views/HomeView.swift:706
}

/** 판 한 층: 300×170 판 좌표로 그리고 박스 폭에 맞춰 늘린다 */
@Composable
internal fun Plate(modifier: Modifier = Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier.fillMaxSize()) {
        // ios: Views/HomeView.swift:635,636 (.frame(width: 300, height: 170).scaleEffect(k, anchor: .topLeading))
        val plate = Size(300f, 170f); val k = size.width / plate.width
        withTransform({ scale(k, k, pivot = Offset.Zero) }) { draw() }
    }
}

/** 반복 움직임 하나: 0 → 1 로 돌고 되돌기(reverse)를 되풀이한다. 값은 그리기 단계에서만 읽는다.
 *  동작 줄이기·숨은 탭이면 0 에 멈춘다 */
// ios: Views/HomeView.swift:710,711,712,713,714,715,716,717,718,719,720
@Composable
internal fun Loop(duration: Double, reverse: Boolean = true, curve: DurationBasedAnimationSpec<Float>? = null): () -> Float {
    val still = LocalReduceMotion.current || !LocalTabActive.current
    if (still) return { 0f }
    val t = rememberInfiniteTransition(label = "loop")
    val on = t.animateFloat(0f, 1f, infiniteRepeatable(curve ?: tween((duration * 1000).toInt(), easing = EaseInOut), if (reverse) RepeatMode.Reverse else RepeatMode.Restart), label = "on")
    return { on.value }
}

// 시안 1 지평선: 새벽빛 밤하늘, 출발~목표 지평선(1년·2년 눈금), 숨 쉬는 내 불빛과 점선 계획 자리, 오늘 등락은 작은 물결
// ios: Views/HomeView.swift:726,727,728
@Composable
private fun HorizonScene(s: BoxScene) {
    val x0 = 10f; val x1 = 290f; val y = 130f   // ios: Views/HomeView.swift:729
    val X = { f: Double -> x0 + (x1 - x0) * min(1.0, max(0.0, f)).toFloat() }   // ios: Views/HomeView.swift:730
    val me = X(s.f)   // ios: Views/HomeView.swift:731
    val tm = rememberTextMeasurer()
    val dk = boxDark()
    // ios: Views/HomeView.swift:732,733
    Plate {
        val all = Rect(0f, 0f, 300f, 170f)   // ios: Views/HomeView.swift:734 (width: 300, height: 170)
        // ios: Views/HomeView.swift:700,701,702,703,704,705,706,707,708
        if (dk) {
            drawRect(Brush.linearGradient(listOf(hex(0x0C111D), hex(0x1C2747)), start = Offset.Zero, end = Offset(0f, 170f)), all.topLeft, all.size)   // ios: Views/HomeView.swift:735
            drawRect(Brush.radialGradient(0f to hex(0xFFD98A).copy(alpha = 0.45f), 1f to hex(0xFFD98A).copy(alpha = 0f), center = Offset(x1 - 8f, 140f), radius = 64f), all.topLeft, all.size)   // ios: Views/HomeView.swift:736
            Sk.stars(this)   // ios: Views/HomeView.swift:737
        } else {
            // 라이트: 새벽 대신 맑은 아침빛
            drawRect(Brush.linearGradient(listOf(hex(0xC9DAF0), hex(0xF8F0E5)), start = Offset.Zero, end = Offset(0f, 170f)), all.topLeft, all.size)
            drawRect(Brush.radialGradient(0f to hex(0xFFBA68).copy(alpha = 0.6f), 1f to hex(0xFFBA68).copy(alpha = 0f), center = Offset(x1 - 8f, 140f), radius = 64f), all.topLeft, all.size)
        }
        val line = hex(if (dk) 0x4A5672 else 0xB4BDCC); val road = hex(if (dk) 0xF2C14E else 0xE0A21E); val lab = hex(if (dk) 0x8A93A6 else 0x5F6670)   // ios: Views/HomeView.swift:709
        drawPath(Sk.line(Offset(x0, y), Offset(x1, y)), line, style = Stroke(1.5f))   // ios: Views/HomeView.swift:738
        // ios: Views/HomeView.swift:739
        if (me > x0) {
            // ios: Views/HomeView.swift:740,741
            drawPath(Sk.line(Offset(x0, y), Offset(me, y)),
                Brush.linearGradient(listOf(road.copy(alpha = if (dk) 0.15f else 0.2f), road.copy(alpha = if (dk) 0.9f else 1f)), start = Offset(x0, y), end = Offset(me, y)), style = Stroke(2.5f))
        }
        // ios: Views/HomeView.swift:743
        for ((t, f) in Sk.pctTicks) {
            drawPath(Sk.line(Offset(X(f), y - 4), Offset(X(f), y + 4)), line, style = Stroke(1f))   // ios: Views/HomeView.swift:744
            Sk.text(this, tm, t, X(f), 150f, lab, anchor = Anchor.center)   // ios: Views/HomeView.swift:745
        }
        drawPath(Sk.dot(x1, y, 3f), hex(if (dk) 0xFFD98A else 0xE0A21E))   // ios: Views/HomeView.swift:747
        // ios: Views/HomeView.swift:748
        s.plan?.let { p ->
            drawPath(Sk.dot(X(p), y, 7f), hex(if (dk) 0x8A93A6 else 0x6B7480), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))   // ios: Views/HomeView.swift:749
        }
        Sk.text(this, tm, "출발", x0, 150f, lab)   // ios: Views/HomeView.swift:751
        Sk.text(this, tm, s.target, x1, 150f, hex(if (dk) 0xFFD98A else 0x9A6A00), bold = !dk, anchor = Anchor.trailing)   // ios: Views/HomeView.swift:752
    }
    // 숨 쉬는 내 불빛
    val glow = Loop(1.5)   // ios: Views/HomeView.swift:755
    val v = s.today
    val bob = if (v != null) Loop(2.0) else ({ 0f })   // ios: Views/HomeView.swift:761,763
    val todayText = v?.let { Sk.today(it) }
    Plate(Modifier.graphicsLayer()) {
        val on = glow()
        val d = Size(32f, 32f); val k = lerp(1f, 1.25f, on); val op = lerp(1f, 0.35f, on)   // ios: Views/HomeView.swift:757
        // ios: Views/HomeView.swift:756,759 (endRadius: 16, position(x: me, y: y))
        val halo = hex(if (dk) 0xF2C14E else 0xF2B42E)
        drawCircle(Brush.radialGradient(0f to halo.copy(alpha = if (dk) 0.7f else 0.75f), 1f to halo.copy(alpha = 0f), center = Offset(me, y), radius = 16f * k), radius = d.width / 2 * k, center = Offset(me, y), alpha = op)
        val dot = Size(9f, 9f); drawCircle(hex(if (dk) 0xFFF4D6 else 0xE8A417), radius = dot.width / 2, center = Offset(me, y))   // ios: Views/HomeView.swift:760
        if (!dk) drawCircle(Color.White, radius = dot.width / 2, center = Offset(me, y), style = Stroke(1.5f))   // ios: Views/HomeView.swift:734
        // 오늘 등락: 내 불빛 위 작은 물결
        if (v != null && todayText != null) {
            val c = Sk.todayColor(v, dk)
            val r = tm.measure(todayText, TextStyle(fontSize = (9.5f / (density * fontScale)).sp, color = c), softWrap = false)   // ios: Views/HomeView.swift:766
            val w = 18f + 3f + r.size.width   // ios: Views/HomeView.swift:764 (spacing: 3)
            val cx = min(250f, max(50f, me + 20)); val cy = 112f + lerp(0f, -2f, bob())   // ios: Views/HomeView.swift:768,770
            val left = cx - w / 2; val ws = Size(18f, 4f)   // ios: Views/HomeView.swift:765
            drawPath(wave(Rect(Offset(left, cy - ws.height / 2), ws)), c, style = Stroke(1.3f))
            drawText(r, topLeft = Offset(left + ws.width + 3f, cy - r.size.height / 2f))
        }
    }
}

/** 물결 한 줄 (지평선 오늘 등락) */
// ios: Views/HomeView.swift:777,778
private fun wave(r: Rect): Path {
    val p = Path(); val n = 3; val w = r.width / n   // ios: Views/HomeView.swift:779
    p.moveTo(r.left, r.center.y)   // ios: Views/HomeView.swift:780
    // ios: Views/HomeView.swift:781
    for (i in 0 until n) {
        val a = r.left + w * i   // ios: Views/HomeView.swift:782
        p.quadraticTo(a + w / 2, if (i % 2 == 0) r.top else r.bottom, a + w, r.center.y)   // ios: Views/HomeView.swift:783
    }
    return p   // ios: Views/HomeView.swift:785
}

// 시안 3 욕조와 오리: 남색 타일 벽 앞 흰 욕조. 욕조 안쪽 맨 위 눈금이 목표, 그 위로 넘칠 여유. 노란 오리가 나, 금색 줄이 계획
// ios: Views/HomeView.swift:790,791
internal object TubScene {
    /** 물높이: 바닥 150, 목표 70 (넘치면 54까지) */
    fun level(f: Double): Float = 150 - 80 * min(1.2, max(0.0, f)).toFloat()   // ios: Views/HomeView.swift:793
    // ios: Views/HomeView.swift:794,795
    val tub: Path = Path().apply {
        moveTo(40f, 52f); lineTo(260f, 52f); lineTo(260f, 120f)   // ios: Views/HomeView.swift:796
        quadraticTo(260f, 150f, 230f, 150f)   // ios: Views/HomeView.swift:797
        lineTo(70f, 150f)   // ios: Views/HomeView.swift:798
        quadraticTo(40f, 150f, 40f, 120f)   // ios: Views/HomeView.swift:799
        close()   // ios: Views/HomeView.swift:800,801,802
    }
}

// ios: Views/HomeView.swift:804
@Composable
private fun TubScene(s: BoxScene) {
    val w = TubScene.level(s.f)   // ios: Views/HomeView.swift:805
    val tub = TubScene.tub
    val tm = rememberTextMeasurer()
    val dk = boxDark()   // ios: Views/HomeView.swift:783
    // ios: Views/HomeView.swift:806,807
    Plate {
        // 타일 벽
        drawRect(hex(if (dk) 0x1B2744 else 0xE2ECF8), Offset(0f, 0f), Size(300f, 170f))   // ios: Views/HomeView.swift:809
        val grid = Path()   // ios: Views/HomeView.swift:810
        for (i in 0..14) { val x = i * 22f; grid.moveTo(x, 0f); grid.lineTo(x, 170f) }   // ios: Views/HomeView.swift:811
        for (j in 0..8) { val y = j * 22f; grid.moveTo(0f, y); grid.lineTo(300f, y) }   // ios: Views/HomeView.swift:812
        drawPath(grid, hex(if (dk) 0x2A3A60 else 0xC6D6EB), style = Stroke(1.5f))   // ios: Views/HomeView.swift:813
        // 욕조 안쪽 (눈금은 물 위에 따로 그린다)
        // ios: Views/HomeView.swift:815,816,817
        clipPath(tub) { drawRect(hex(if (dk) 0xEEF2F8 else 0xFFFFFF), Offset(0f, 0f), Size(300f, 170f)) }
    }
    // 물: 욕조 안에서 출렁인다
    val flow = Loop(3.0, reverse = false, curve = tween(3 * 1000, easing = LinearEasing))   // ios: Views/HomeView.swift:820
    Plate(Modifier.graphicsLayer()) {
        val on = flow()
        // ios: Views/HomeView.swift:821,822,823
        val h = 170 - w + 6; val ox = lerp(-70f, -10f, on); val oy = w - 6
        // ios: Views/HomeView.swift:825,826
        clipRect(0f, 0f, 300f, 170f) { clipPath(tub) {
            translate(ox, oy) {
                drawPath(tubWater(400f, h), Brush.verticalGradient(   // ios: Views/HomeView.swift:821,822
listOf(hex(if (dk) 0x7CC8F0 else 0x8CCFF2).copy(alpha = if (dk) 0.85f else 0.9f), hex(if (dk) 0x3F7FC0 else 0x4C8CCB).copy(alpha = 0.9f)), startY = 0f, endY = h))
            }
        } }
    }
    // ios: Views/HomeView.swift:827
    Plate {
        // 눈금: 물 위에. 물에 잠긴 눈금은 흰색으로. 계획 줄은 뺀다 (2026-10-27)
        // ios: Views/HomeView.swift:829,830
        clipPath(tub) {
            val mark = { y: Float, dry: Color -> if (y > w) Color.White.copy(alpha = 0.9f) else dry }   // ios: Views/HomeView.swift:831
            drawPath(Sk.line(Offset(240f, 70f), Offset(254f, 70f)), mark(70f, hex(if (dk) 0x7D889E else 0x6B7690)), style = Stroke(1.6f))   // ios: Views/HomeView.swift:832
            Sk.text(this, tm, s.target, 237f, 70f, mark(70f, hex(if (dk) 0x7D889E else 0x5A6580)), size = 8.5f, bold = true, anchor = Anchor.trailing)   // ios: Views/HomeView.swift:833
            // ios: Views/HomeView.swift:834
            for ((t, f) in Sk.pctTicks) {
                val y = TubScene.level(f)   // ios: Views/HomeView.swift:776
                drawPath(Sk.line(Offset(246f, y), Offset(254f, y)), mark(y, hex(0x9AA6BD)), style = Stroke(1.2f))   // ios: Views/HomeView.swift:835
                Sk.text(this, tm, t, 243f, y, mark(y, hex(if (dk) 0x9AA6BD else 0x7D889E)), size = 8f, anchor = Anchor.trailing)   // ios: Views/HomeView.swift:836
            }
            // ios: Views/HomeView.swift:838
        }
        drawPath(tub, if (dk) Color.White else hex(0xB7CAE3), style = Stroke(3f))   // ios: Views/HomeView.swift:843
        drawRoundRect(Color.White, Offset(32f, 46f), Size(236f, 8f), CornerRadius(4f, 4f))   // ios: Views/HomeView.swift:844
        if (!dk) drawRoundRect(hex(0xB7CAE3), Offset(32f, 46f), Size(236f, 8f), CornerRadius(4f, 4f), style = Stroke(1f))   // ios: Views/HomeView.swift:827
    }
    // 오리 = 나
    val bob = Loop(1.3)   // ios: Views/HomeView.swift:847
    Plate(Modifier.graphicsLayer()) {
        val on = bob()
        val cx = 156f; val cy = w - 12   // ios: Views/HomeView.swift:848
        val turn = lerp(0f, -2f, on); val dy = lerp(0f, 2.5f, on)   // ios: Views/HomeView.swift:847
        translate(cx - Duck.size.width / 2, cy - Duck.size.height / 2 + dy) { rotate(turn, pivot = Offset(Duck.size.width / 2, Duck.size.height / 2)) { duck() } }
    }
    // ios: Views/HomeView.swift:849
    s.today?.let { v ->
        Plate { Sk.text(this, tm, Sk.today(v), 100f, w - 21, Sk.todayColor(v, dk), size = 9.5f, anchor = Anchor.center) }   // ios: Views/HomeView.swift:850,851
    }
}

// ios: Views/HomeView.swift:857,858
private fun tubWater(width: Float, height: Float): Path {
    val p = Path(); val w = 15f   // ios: Views/HomeView.swift:859
    p.moveTo(0f, 6f)   // ios: Views/HomeView.swift:860
    var x = 0f; var up = true   // ios: Views/HomeView.swift:861
    while (x < width) { p.quadraticTo(x + w, if (up) 1f else 11f, x + w * 2, 6f); x += w * 2; up = !up }   // ios: Views/HomeView.swift:862
    p.lineTo(x, height); p.lineTo(0f, height); p.close()   // ios: Views/HomeView.swift:863
    return p   // ios: Views/HomeView.swift:864
}

private object Duck { val size = Size(46f, 38f) }   // ios: Views/HomeView.swift:868,882

// ios: Views/HomeView.swift:869,870
internal fun DrawScope.duck() {
    // 판 좌표 (136~177, 96~130) 을 (0,0) 기준으로
    translate(-133f, -94f) {   // ios: Views/HomeView.swift:872
        val y = hex(0xFFD23F)   // ios: Views/HomeView.swift:873
        drawOval(y, Offset(133f, 110f), Size(34f, 20f))   // ios: Views/HomeView.swift:874
        // ios: Views/HomeView.swift:875
        val tail = Path(); tail.moveTo(136f, 116f); tail.quadraticTo(130f, 110f, 134f, 106f)
        tail.quadraticTo(140f, 110f, 140f, 114f); tail.close()   // ios: Views/HomeView.swift:876
        drawPath(tail, y)   // ios: Views/HomeView.swift:877
        drawPath(Sk.dot(162f, 105f, 8.5f), y)   // ios: Views/HomeView.swift:878
        drawPath(Sk.p(listOf(Pair(169f, 104f), Pair(177f, 106f), Pair(169f, 109f))), hex(0xFF8A3D))   // ios: Views/HomeView.swift:879
        drawPath(Sk.dot(164f, 103f, 1.4f), hex(0x1D2330))   // ios: Views/HomeView.swift:880
    }
}

// 시안 4 점프 직전: 밤 도로를 은빛 쐐기형 차가 불꽃 두 줄을 끌며 오른쪽 파란 일렁임(목표)으로. 앞코 = 내 자리, 이정표 봉 = 계획
// ios: Views/HomeView.swift:887,888,889
@Composable
private fun JumpScene(s: BoxScene) {
    val x0 = 20f; val x1 = 284f   // ios: Views/HomeView.swift:890
    val X = { f: Double -> x0 + (x1 - x0) * min(1.0, max(0.0, f)).toFloat() }   // ios: Views/HomeView.swift:891
    val nose = X(s.f); val rear = nose - 85   // ios: Views/HomeView.swift:892
    val tm = rememberTextMeasurer()
    val dk = boxDark()   // ios: Views/HomeView.swift:889
    // ios: Views/HomeView.swift:893 (.clipped())
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // ios: Views/HomeView.swift:894
        Plate {
            // 라이트: 하늘만 밝히고 도로는 어둡게 (차선 불빛이 묻히지 않게)
            drawRect(Brush.linearGradient(listOf(hex(if (dk) 0x0B1022 else 0xBAC7E8), hex(if (dk) 0x232A52 else 0xE4EAF7)), start = Offset.Zero, end = Offset(0f, 170f)), Offset(0f, 0f), Size(300f, 170f))   // ios: Views/HomeView.swift:895
            if (dk) for ((x, y, r) in listOf(Triple(200f, 18f, 0.9f), Triple(240f, 44f, 0.6f), Triple(170f, 40f, 0.5f))) drawPath(Sk.dot(x, y, r), hex(0xC9D3EA))   // ios: Views/HomeView.swift:896
            drawRect(hex(if (dk) 0x151925 else 0x2F3650), Offset(0f, 128f), Size(300f, 42f))   // ios: Views/HomeView.swift:897
            drawPath(Sk.line(Offset(0f, 160f), Offset(300f, 160f)), hex(if (dk) 0x3A4155 else 0x5A6585), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))   // ios: Views/HomeView.swift:898
            // ios: Views/HomeView.swift:900
            for ((i, f) in listOf(0.0, 0.25, 0.5, 0.75).withIndex()) {
                val x = X(f); val long = i % 2 == 0   // ios: Views/HomeView.swift:901
                drawPath(Sk.line(Offset(x, 128f), Offset(x, if (long) 134f else 131f)), hex(if (dk) 0x6B7489 else 0xAEB6C6), style = Stroke(1f))   // ios: Views/HomeView.swift:902
            }
            Sk.text(this, tm, "출발", x0, 166f, hex(if (dk) 0x6B7489 else 0xAEB6C6), size = 8f, anchor = Anchor.center)   // ios: Views/HomeView.swift:904
            // 계획 표지판은 뺀다 (2026-10-27). 눈금 글은 %만
            for ((t, f) in Sk.pctTicks) Sk.text(this, tm, t, X(f), 166f, hex(if (dk) 0x6B7489 else 0xAEB6C6), size = 8f, anchor = Anchor.center)   // ios: Views/HomeView.swift:851
            drawPath(Sk.line(Offset(x1, 128f), Offset(x1, 134f)), hex(if (dk) 0x6B7489 else 0xAEB6C6), style = Stroke(1f))   // ios: Views/HomeView.swift:852
            Sk.text(this, tm, s.target, x1, 166f, hex(if (dk) 0x9FDCFF else 0x1E5FA8), size = 8.5f, bold = true, anchor = Anchor.center)   // ios: Views/HomeView.swift:853
            // ios: Views/HomeView.swift:906
            s.today?.let { v -> Sk.text(this, tm, Sk.today(v), 6f, 120f, Sk.todayColor(v, dk)) }   // ios: Views/HomeView.swift:913
        }
        // 목표: 파란 일렁임
        val ripple = Loop(1.2)   // ios: Views/HomeView.swift:916
        // 불꽃 두 줄 (출발부터 차 뒤까지)
        val flame = if (rear + 13 > x0) Loop(0.4) else ({ 0f })   // ios: Views/HomeView.swift:922,923
        val shake = Loop(0.12)   // ios: Views/HomeView.swift:933
        Plate(Modifier.graphicsLayer()) {
            val on = ripple()
            // 타원 그러데이션: 원(지름 32)을 세로로 84/32 배 늘린다
            val sx = lerp(1f, 1.25f, on); val sy = 1f * 84f / 32f; val op = lerp(1f, 0.75f, on)   // ios: Views/HomeView.swift:918
            withTransform({ scale(sx, sy, pivot = Offset(x1, 112f)) }) {   // ios: Views/HomeView.swift:920
                drawCircle(Brush.radialGradient(0f to hex(if (dk) 0xE8F7FF else 0xFFFFFF), 0.5f to hex(0x5FC8FF).copy(alpha = if (dk) 0.8f else 0.85f), 1f to hex(0x2A6CFF).copy(alpha = 0f), center = Offset(x1, 112f), radius = 32f * 0.5f), radius = 16f, center = Offset(x1, 112f), alpha = op)   // ios: Views/HomeView.swift:917
            }
            if (rear + 13 > x0) {
                val top = 140f; val gap = 2.5f   // ios: Views/HomeView.swift:924,931 (VStack 간격, 왼쪽 위 = (x0, 140))
                val w1 = max(0f, rear + 13 - x0); val h1 = 2.5f   // ios: Views/HomeView.swift:925
                val w2 = max(0f, rear + 15 - x0 - 4); val h2 = 2.5f; val lead = 4f   // ios: Views/HomeView.swift:926
                // ios: Views/HomeView.swift:928 (VStack 폭 전체에 걸친 그러데이션)
                val fire = Brush.horizontalGradient(0f to hex(0xFF5A1F).copy(alpha = 0f), 0.6f to hex(0xFF7A1F), 1f to hex(0xFFE27A), startX = x0, endX = x0 + max(w1, w2 + lead))
                val a = lerp(1f, 0.7f, flame())   // ios: Views/HomeView.swift:929
                drawRoundRect(fire, Offset(x0, top), Size(w1, h1), CornerRadius(h1 / 2, h1 / 2), alpha = a)
                drawRoundRect(fire, Offset(x0 + lead, top + h1 + gap), Size(w2, h2), CornerRadius(h2 / 2, h2 / 2), alpha = a)
            }
            val carSize = Size(95f, 35f)   // ios: Views/HomeView.swift:934
            translate(nose - 84.7f + 47.5f - carSize.width / 2, 129.5f - carSize.height / 2 + lerp(0f, 0.6f, shake())) { car(dk) }   // ios: Views/HomeView.swift:933,935
        }
    }
}

/** 은빛 쐐기형 차 (시안 4 의 차를 0.7 배로, 앞코가 오른쪽 끝) */
// ios: Views/HomeView.swift:942,943,944
internal fun DrawScope.car(dk: Boolean = true) {
    // ios: Views/HomeView.swift:945,946
    withTransform({ scale(0.7f, 0.7f, pivot = Offset.Zero); translate(-100f, -97f) }) {
        val body = Sk.p(listOf(Pair(100f, 138f), Pair(100f, 123f), Pair(128f, 121f), Pair(146f, 109f), Pair(168f, 108f), Pair(188f, 121f), Pair(220f, 128f), Pair(221f, 133f), Pair(214f, 138f)))   // ios: Views/HomeView.swift:947
        drawPath(body, Brush.linearGradient(listOf(hex(if (dk) 0xEEF1F6 else 0xF4F6FA), hex(0x8D95A6)), start = Offset(0f, 108f), end = Offset(0f, 138f)))   // ios: Views/HomeView.swift:948
        if (!dk) drawPath(body, hex(0x6B7489), style = Stroke(0.8f))   // 라이트: 옅은 외곽선   // ios: Views/HomeView.swift:946
        drawPath(Sk.p(listOf(Pair(100f, 133f), Pair(220f, 133f), Pair(214f, 138f), Pair(100f, 138f))), hex(0x2A2E38))   // ios: Views/HomeView.swift:949
        drawPath(Sk.line(Offset(100f, 128f), Offset(220f, 130f)), hex(0xAAB1BF), style = Stroke(0.8f))   // ios: Views/HomeView.swift:950
        drawPath(Sk.p(listOf(Pair(150f, 111f), Pair(167f, 111f), Pair(180f, 120f), Pair(146f, 120f))), hex(if (dk) 0x2B3550 else 0x3A4766))   // ios: Views/HomeView.swift:951
        drawPath(Sk.line(Offset(158f, 111f), Offset(156f, 120f)), hex(0x9AA2B2), style = Stroke(1f))   // ios: Views/HomeView.swift:952
        for (x in listOf(131.0f, 135f, 139f)) drawPath(Sk.line(Offset(x, 120f), Offset(x + 6, 115f)), hex(0x59606F), style = Stroke(1f))   // ios: Views/HomeView.swift:953
        drawRect(hex(0x151925), Offset(113f, 131f), Size(18f, 9f))   // ios: Views/HomeView.swift:954
        drawRect(hex(0x151925), Offset(187f, 131f), Size(18f, 9f))   // ios: Views/HomeView.swift:955
        drawRect(hex(0xFFF6C8), Offset(214f, 128.5f), Size(6f, 2f))   // ios: Views/HomeView.swift:956
        drawRect(hex(0xFF4A4A), Offset(100f, 124f), Size(3f, 4f))   // ios: Views/HomeView.swift:957
        // ios: Views/HomeView.swift:958
        for (x in listOf(122.0f, 196f)) {
            drawPath(Sk.dot(x, 140f, 7f), hex(0x111111))   // ios: Views/HomeView.swift:959
            drawPath(Sk.dot(x, 140f, 7f), hex(0x7D8597), style = Stroke(2f))   // ios: Views/HomeView.swift:960
        }
        // 앞코·지붕 위 작은 번개
        // ios: Views/HomeView.swift:962
        run {
            val arc = Path()   // ios: Views/HomeView.swift:963
            arc.moveTo(222f, 121f); arc.lineTo(228f, 116f); arc.lineTo(226f, 123f); arc.lineTo(234f, 119f)   // ios: Views/HomeView.swift:964
            arc.moveTo(152f, 104f); arc.lineTo(156f, 98f); arc.lineTo(157f, 104f); arc.lineTo(162f, 99f)   // ios: Views/HomeView.swift:965
            drawPath(arc, hex(if (dk) 0x7FD4FF else 0x2A8FE0), style = Stroke(1.2f))   // ios: Views/HomeView.swift:966
        }
    }
}
