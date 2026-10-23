package com.naeilo.app

import com.naeilo.app.data.go
import com.naeilo.app.ui.board.BoardView
import com.naeilo.app.ui.board.boardPage
import com.naeilo.app.core.calc.isGoal
import android.view.Window
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.NavStack
import com.naeilo.app.ui.home.HomeView
import com.naeilo.app.ui.hold.HoldingsView
import com.naeilo.app.ui.hold.HoldingDetailView
import com.naeilo.app.ui.analysis.AnalysisView
import com.naeilo.app.ui.analysis.AnalysisPager
import com.naeilo.app.ui.analysis.AnalysisRoute
import com.naeilo.app.ui.analysis.GoalSetPage
import com.naeilo.app.ui.NavPage
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.Period
import com.naeilo.app.ui.hold.KoreaSumCard
import com.naeilo.app.ui.Sheet
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.ui.home.homePage
import com.naeilo.app.ui.analysis.MyPathCompareView
import com.naeilo.app.ui.analysis.FactorDetailRoute
import com.naeilo.app.ui.analysis.FactorDetailView
import com.naeilo.app.ui.analysis.RateWhyRoute
import com.naeilo.app.ui.analysis.RateWhyView
import com.naeilo.app.ui.settings.SettingsView
import com.naeilo.app.ui.settings.settingsPage
import com.naeilo.app.ui.settings.WidgetPickView
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.NavBack
import com.naeilo.app.ui.start.StartView
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.Compute
import com.naeilo.app.core.calc.calmOpen
import com.naeilo.app.data.TrustedClock
import com.naeilo.app.data.catchUpDay
import com.naeilo.app.data.fixStarts
import com.naeilo.app.data.progress
import com.naeilo.app.data.recordDay
import com.naeilo.app.data.saveProgress
import com.naeilo.app.data.persists
import com.naeilo.app.net.Market
import com.naeilo.app.net.Sync
import com.naeilo.app.ui.game.GamePause
import com.naeilo.app.data.LineClock
import com.naeilo.app.data.Notifier
import com.naeilo.app.data.Remind
import com.naeilo.app.data.Support
import com.naeilo.app.data.WidgetBridge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.LocalTextScale
import com.naeilo.app.ui.Palette
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TextScale
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.stiffness

// ios: App.swift:23,33,35,37,39,43 (label·SF Symbol 이름·아이콘). 탭 순서는 여기 한 곳 (아래 막대도 이 순서). 설정은 탭이 아니라 홈 위 팝업 (2026-10-21)
enum class Tab(val label: String, val sf: String, @param:DrawableRes val icon: Int) {
    home("홈", "house", R.drawable.tab_home),
    hold("종목", "chart.bar", R.drawable.tab_hold),
    flow(com.naeilo.app.data.FlowTab.name, com.naeilo.app.data.FlowTab.icon, R.drawable.tab_flow),   // 차트 탭 (이름은 FlowTab 한 곳에서)
    analysis("분석", "chart.line.uptrend.xyaxis", R.drawable.tab_analysis),
    board("미션", "square.grid.2x2", R.drawable.tab_board),
}

/** 앱 바탕: 화면 모드·글자 크기 상한을 정해 RootView 에 넘긴다 */
@Composable
fun NaeiloApp(model: AppModel, window: Window) {   // ios: App.swift:5
    val sysDark = isSystemInDarkTheme()
    // ios: App.swift:16 preferredColorScheme
    val dark = when (model.appearance) { "light" -> false; "dark" -> true; else -> sysDark }
    val fontScale = LocalConfiguration.current.fontScale
    val scale = remember(fontScale) { TextScale.from(fontScale) }   // 큰 글자 상한은 TextScale.from (ios: App.swift:18)
    val density = LocalDensity.current
    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalPal provides if (dark) Palette.Dark else Palette.Light,
        LocalTextScale provides scale,
        // 글자 배율은 appFont 가 iOS 단계로 직접 곱한다 (sp 가 한 번 더 키우지 않게)
        LocalDensity provides Density(density.density, 1f),
        LocalReduceMotion provides model.reduceMotion,
    ) {
        RootView(model)   // ios: App.swift:14,15
    }
}

// ios: App.swift:25,26,28,29
@Composable
fun RootView(model: AppModel) {
    val showStart = !model.onboarded   // ios: App.swift:30
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ios: App.swift:31
            // 키보드가 뜨면 탭 막대(49)·내비게이션 막대 위로 넘친 만큼만 화면을 줄인다 (입력칸이 키보드에 가리지 않게. 배치 단계에서만 읽어 다시 그리지 않음)
            TabHost(model.tab, Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars).exclude(WindowInsets(bottom = 49.dp)).only(WindowInsetsSides.Bottom))) { t ->
                // 종목·분석·미션 탭 안 화면은 다음 단계에서 채운다 (지금은 머리글만 있는 빈 자리)
                when (t) {
                    Tab.home -> NavStack(model.homePath, { model.homePath = it }, { HomeView(model) }) { homePage(model, it) }       // ios: App.swift:32 (HomeView·homePath)
                    Tab.hold -> HoldingsView(model)       // ios: App.swift:34 (HoldingsView·holdPath)
                    Tab.flow -> com.naeilo.app.ui.flow.FlowView(model)   // ios: App.swift:36 (FlowView)
                    Tab.analysis -> NavStack(model.analysisPath, { model.analysisPath = it }, { AnalysisView(model) }) { r ->   // ios: App.swift:38 (AnalysisView·analysisPath)
                        when (r) {
                            is AnalysisRoute -> NavPage(bar = false) { AnalysisPager(model, start = r) }   // ios: Views/AnalysisView.swift:128 (넘기기 화면은 막대를 스스로 그린다)
                            is Symbol -> NavPage(r.name) { HoldingDetailView(model, sym = r, period0 = Period.y1) }   // ios: Views/AnalysisView.swift:83
                            is FactorDetailRoute -> NavPage(FactorDetailView.navTitle(r.factor)) { FactorDetailView(model, r.factor, r.start) }   // 외부 요인 상세
                            is RateWhyRoute -> NavPage("근거") { RateWhyView(r.r, r.`when`, r.epsLast) }   // 금리 시나리오 근거
                            is com.naeilo.app.ui.calm.CalmFriendRoute -> com.naeilo.app.ui.shelter.characterPage(model, r.friend, invest = true)   // 내 투자 습관 → 인물 투자 갈래
                            else -> NavPage { GoalSetPage(model) }   // 전망의 '목표 고치기'
                        }
                    }
                    // ios: App.swift:40,41 (목표·회복 루트 같은 미션 판)
                    Tab.board -> NavStack(model.boardPath, { model.boardPath = it }, { BoardView(model) }) { r -> boardPage(model, r) }
                }
            }
            TabBar(model.tab, tint = Theme.teal) { model.tab = it }   // ios: App.swift:45
        }
        // 처음 실행이면 첫 질문을 탭 화면 위에 덮는다
        StartOverlay(model, showStart)
    }
    HomeSheets(model)   // ios: App.swift:46
    AppHooks(model)
}

/** 설정·위젯 팝업: 다른 팝업처럼 시트, 맨 위 줄 제목 + X로 닫는다. 어느 탭에서든 뜬다 */
// ios: App.swift:181,182,183,184,185
@Composable
private fun HomeSheets(model: AppModel) {
    val closeSettings = { model.settingsOpen = false; model.settingsPath = emptyList() }
    // ios: App.swift:188,189 (닫으면 settingsPath 비우기)
    Sheet(model.settingsOpen, onDismiss = closeSettings) {
        CompositionLocalProvider(LocalNavBack provides NavBack(null, closeSettings)) {
            NavStack(model.settingsPath, { model.settingsPath = it }, { SettingsView(model, cover = true) }) { settingsPage(model, it) }
        }
    }
    // ios: App.swift:191,192
    Sheet(model.widgetsOpen, onDismiss = { model.widgetsOpen = false }) {
        CompositionLocalProvider(LocalNavBack provides NavBack(null) { model.widgetsOpen = false }) {
            Column(Modifier.fillMaxSize().background(Theme.bg)) { WidgetPickView(model, cover = true) }
        }
    }
}

/**
 * RootView 의 onChange·task 자리: 값이 바뀔 때 위젯·저장·동기화, 앱이 앞으로 올 때 서버 시각·당겨 받기, 앞에 있는 동안 1분마다 시세.
 * 값은 snapshotFlow 로 지켜본다 (화면을 다시 그리지 않고, 처음 값은 건너뛰어 onChange 와 같게).
 */
@Composable
private fun AppHooks(model: AppModel) {
    val phase = model.active   // ios: App.swift:27 scenePhase (앞 = onStart, 뒤 = onStop)
    // 보유·루트가 바뀌면 위젯도 바로. 홈 인물을 바꾸면 위젯 인물도 같이 ('홈 인물 따라가기' 위젯)
    // ios: App.swift:48,49,50,52,53,54
    LaunchedEffect(Unit) {
        snapshotFlow { listOf<Any?>(model.holdings, model.route, model.nxStep, model.homeFriend, Sync.shared.isOn, Sync.shared.isDev) }.drop(1).collect { WidgetBridge.write(model) }
    }
    // 켜 둔 채 날이 바뀌었으면 오늘의 1분도 다음 날로.
    // 전망 몬테카를로: 앱이 뒤로 가면 돌던 계산도 멈추고, 다시 앞으로 오면 이어 간다 (뒤에서 폰이 뜨거워지지 않게)
    val first = remember { booleanArrayOf(true) }
    // ios: App.swift:59,60,62
    LaunchedEffect(phase) {
        if (first[0]) { first[0] = false } else {
            Compute.setActive(phase)
            if (phase && model.persists) { model.catchUpDay(); model.calmOpen() }   // calmOpen 안에 recordDay
        }
    }
    // 미션 진행: 바뀔 때마다 저장, 동기화가 켜져 있으면 다른 기기로도
    // ios: App.swift:65
    LaunchedEffect(Unit) {
        snapshotFlow { model.progress }.drop(1).collect { model.saveProgress(); Remind.shared.refresh(model); if (model.persists) Sync.shared.changed(model) }
    }
    // ios: App.swift:66
    LaunchedEffect(Unit) { snapshotFlow { model.holdings }.drop(1).collect { if (model.persists) Sync.shared.changed(model) } }
    // 내 기록: 새 날 줄이 생기면 다른 기기로도 (같은 날 값 고침은 다음 변경 때 같이)
    // ios: App.swift:69
    LaunchedEffect(Unit) { snapshotFlow { model.myRecord.size }.drop(1).collect { if (model.persists) Sync.shared.changed(model) } }
    // 앱을 열거나 앞으로 올 때 다른 기기 값 받기. 서버 시각도 같이 받는다 (인터미션 날짜·내 캐릭터 조건, Me)
    // ios: App.swift:72,73,74,75
    LaunchedEffect(phase) {
        if (!phase) return@LaunchedEffect
        launch { TrustedClock.shared.sync(); model.fixStarts(); Support.shared.tick() }   // 7일 무료·캐릭터 상품 열림 날짜도
        if (model.persists) launch { Sync.shared.pull(model) }
    }
    // 알림: 설정이 바뀌면 다시 예약
    // ios: App.swift:78,79
    LaunchedEffect(Unit) { snapshotFlow { model.alerts to model.alertHr }.drop(1).collect { Notifier.reschedule(model) } }
    // 지금 시세: 화면이 켜져 있는 동안만 1분마다 갱신 (뒤로 가면 멈춰 배터리·데이터를 아낀다)
    // ios: App.swift:104,105
    LaunchedEffect(phase) {
        if (!phase) return@LaunchedEffect
        var lastResched = Instant.EPOCH   // ios: App.swift:106
        var lastKey = ""   // ios: App.swift:107 날짜·평가액이 그대로면 뒤 계산은 건너뛴다 (장 닫힌 동안 매분 화면이 멈칫하지 않게)
        // ios: App.swift:108
        while (isActive) {
            // 미니 게임 중엔 갱신을 미뤄 화면이 멈칫하지 않게 (끝나면 곧 다시 받는다)
            while (GamePause.on && isActive) delay(2000)   // ios: App.swift:110
            Market.shared.refresh(held = model.holdings.map { it.symbol }.toSet())   // ios: App.swift:111
            val key = "${Day.today}|${model.total}"   // ios: App.swift:112
            // ios: App.swift:113
            if (key != lastKey) {
                lastKey = key   // ios: App.swift:114
                if (model.persists) model.recordDay()   // ios: App.swift:115 내 기록: 시세를 받은 뒤 오늘 줄을 지금 값으로
                Remind.shared.refresh(model)     // ios: App.swift:116 홈 리마인드 (위젯 말풍선·다음 일정도 여기서)
                WidgetBridge.write(model)        // ios: App.swift:117 위젯에 지금 숫자를 넘긴다
                Notifier.check(model)       // ios: App.swift:118 본전 도달 · 비중 이탈
            }
            // ios: App.swift:120,121 아침 한 줄은 10분마다 지금 숫자로 (매분 지우고 다시 넣지 않게)
            if (Duration.between(lastResched, Instant.now()).seconds >= 600) {
                lastResched = Instant.now(); Notifier.reschedule(model)
            }
            delay(60000)   // ios: App.swift:123
        }
    }
    LineTurns(model, phase)   // ios: App.swift:56
    KoreaSumHooks(model)   // ios: App.swift:67
}

/** 한국 종목 가격·국내 주식 합계를 고치면 다른 기기로도 보내고, 리마인드 '오늘 국내 주식 합계'를 누르면 입력 칸을 한 번 띄운다 */
// ios: App.swift:188,189,190,191
@Composable
private fun KoreaSumHooks(model: AppModel) {
    // ios: App.swift:192
    LaunchedEffect(Unit) { snapshotFlow { CustomSymbols.shared.krStamp }.drop(1).collect { if (model.persists) Sync.shared.changed(model) } }
    // 키보드 '완료'는 자판의 완료 키로 (NavigationStack 은 필요 없다)
    // ios: App.swift:193,195,196,197,198,200 (화면 절반 높이 시트)
    Sheet(model.krSumAsk, onDismiss = { model.krSumAsk = false }, medium = true) {
        Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
            KoreaSumCard(model, done = { model.krSumAsk = false })
        }
    }
}

/** 인물 대사 차례 올리기: 탭·화면 이동, 앱을 앞으로 가져올 때 (1분 자동 갱신으로는 안 바뀐다) */
// ios: App.swift:171,172,173,174,175
@Composable
private fun LineTurns(model: AppModel, phase: Boolean) {
    LaunchedEffect(phase) { if (phase) LineClock.shared.next() }   // ios: App.swift:176
    // ios: App.swift:177,178,179,180,181,182 (탭·홈·미션·설정 경로, 분석·종목 경로 길이)
    LaunchedEffect(Unit) {
        snapshotFlow { listOf<Any>(model.tab, model.homePath, model.boardPath, model.settingsPath, model.analysisPath.size, model.holdPath.size) }.drop(1).collect { LineClock.shared.next() }
    }
}

// ios: App.swift:127,128 .transition(.opacity) + .animation(.default): iOS 17 기본 = 감쇠 1 · 0.55초 스프링
@Composable
private fun StartOverlay(model: AppModel, show: Boolean) {
    val a = animateFloatAsState(if (show) 1f else 0f, spring(dampingRatio = 1f, stiffness = stiffness(response = 0.55f)), label = "start")
    if (a.value > 0f) StartView(model, Modifier.graphicsLayer { alpha = a.value }.windowInsetsPadding(WindowInsets.safeDrawing))
}

/** 탭 화면 자리 */
@Composable
private fun TabScreen(t: Tab) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { AppHeader(t.label) }
}

/**
 * TabView 자리: 한 번 연 탭은 그대로 두고(스크롤 위치·상태 유지), 보이는 탭 하나만 재고 그린다.
 * 숨은 탭은 측정·그리기를 건너뛰므로 전환에 드는 일은 '고른 탭 하나 재기'뿐이다. 전환 애니메이션 없음 (iOS 탭 막대와 같게).
 */
@Composable
private fun TabHost(selected: Tab, modifier: Modifier, content: @Composable (Tab) -> Unit) {
    val opened = remember { BooleanArray(Tab.entries.size) }
    opened[selected.ordinal] = true
    Layout(
        content = {
            Tab.entries.forEach { t ->
                Box(Modifier.fillMaxSize()) {
                    if (opened[t.ordinal]) CompositionLocalProvider(LocalTabActive provides (t == selected)) { content(t) }
                }
            }
        },
        modifier = modifier,
    ) { ms, c ->
        val p = ms[selected.ordinal].measure(c)
        layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
    }
}

/** iOS 탭 막대: 높이 49, 아이콘 위·글자 아래, 고른 탭은 tint 색, 나머지는 시스템 회색. 물결 없음. 글자는 큰 글자 설정을 따르지 않는다 (iOS 와 같게). */
@Composable
private fun TabBar(selected: Tab, tint: androidx.compose.ui.graphics.Color, onSelect: (Tab) -> Unit) {
    val gray = hex(0x8E8E93)
    Row(Modifier.fillMaxWidth().background(Theme.bg).windowInsetsPadding(WindowInsets.navigationBars).height(49.dp)) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val c = if (on) tint else gray
            Column(
                Modifier.weight(1f).fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(t) }
                    .semantics { this.selected = on }
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Image(painterResource(t.icon), null, Modifier.size(25.dp), colorFilter = ColorFilter.tint(c))
                T(t.label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = c, maxLines = 1)
            }
        }
    }
}


// ── 밖에서 여는 길: 알림을 누름 (iOS NotificationDelegate.open) · naeilo:// 주소 (iOS onOpenURL) ──

/** 알림이 실어 온 열 곳 (Notifier 가 PendingIntent 에 넣는 글자. iOS 와 같은 값) */
const val NOTIFY_OPEN = "naeilo.open"

/** 액티비티로 들어온 것: 알림이면 그 화면, naeilo:// 주소면 그 화면 */
fun openIntent(model: AppModel, intent: android.content.Intent?) {
    intent ?: return
    intent.getStringExtra(NOTIFY_OPEN)?.let { o -> intent.removeExtra(NOTIFY_OPEN); openNotification(model, o); return }
    val u = intent.data ?: return
    if (u.scheme == "naeilo") { intent.data = null; openUrl(model, u) }
}

// ios: App.swift:80,81,82
fun openNotification(model: AppModel, o: String) {
    when (o) {
        "home" -> model.tab = Tab.home   // ios: App.swift:83
        "tax" -> model.openSettings(com.naeilo.app.ui.settings.SettingsRoute.tax)   // ios: App.swift:84
        "rate" -> model.go(com.naeilo.app.data.RemindGo.rateScen)   // ios: App.swift:85
        "ext" -> model.go(com.naeilo.app.data.RemindGo.external)   // ios: App.swift:86
        else -> model.tab = Tab.board   // ios: App.swift:87
    }
}

// 잠긴 위젯을 누르면 앱 시작 3단계로
// ios: App.swift:92
fun openUrl(model: AppModel, url: android.net.Uri) {
    // ios: App.swift:93 개발자: 토스 시세 주소
    if (url.host == "toss") { com.naeilo.app.core.TossFeed.setBase(from = url); kotlinx.coroutines.MainScope().launch { com.naeilo.app.net.Market.shared.refresh(held = model.holdings.map { it.symbol }.toSet(), force = true) }; return }
    if (url.host == "shelter") { model.tab = Tab.home; model.homePath = listOf("shelter"); return }   // ios: App.swift:94 인물 위젯 (못 만난 인물)
    val i = url.lastPathSegment?.toIntOrNull()
    // ios: App.swift:95 서재 위젯
    if (url.host == "read" && i != null && i in 0 until 6 && model.chapterOn(i)) {
        model.tab = Tab.home; model.homePath = listOf("shelter", "read:$i"); return   // ios: App.swift:96
    }
    if (url.host == "subscribe") { model.openSettings(com.naeilo.app.ui.settings.SettingsRoute.support); return }   // ios: App.swift:98 7일 무료가 끝나 잠긴 위젯
    if (url.host != "unlock") return   // ios: App.swift:99
    model.tab = Tab.board   // ios: App.swift:100
    model.boardPath = if (model.playOn) emptyList() else listOf(com.naeilo.app.core.calc.MissionRoute.nx)   // ios: App.swift:101
}
