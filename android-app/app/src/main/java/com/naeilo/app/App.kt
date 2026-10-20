package com.naeilo.app

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

// ios: App.swift:23,33,35,37,41,43 (label·SF Symbol 이름·아이콘)
enum class Tab(val label: String, val sf: String, @DrawableRes val icon: Int) {
    home("홈", "house", R.drawable.tab_home),
    hold("종목", "chart.bar", R.drawable.tab_hold),
    analysis("분석", "chart.line.uptrend.xyaxis", R.drawable.tab_analysis),
    board("미션", "square.grid.2x2", R.drawable.tab_board),
    settings("설정", "gearshape", R.drawable.tab_settings),
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
            TabHost(model.tab, Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) { t ->
                // 탭 안 화면은 다음 단계에서 채운다 (지금은 머리글만 있는 빈 자리)
                when (t) {
                    Tab.home -> TabScreen(t)       // ios: App.swift:32 (HomeView·homePath: 빈 자리)
                    Tab.hold -> TabScreen(t)       // ios: App.swift:34 (HoldingsView·holdPath: 빈 자리)
                    Tab.analysis -> TabScreen(t)   // ios: App.swift:36 (AnalysisView·analysisPath: 빈 자리)
                    Tab.board -> TabScreen(t)      // ios: App.swift:38 (boardPath: 빈 자리)
                    Tab.settings -> TabScreen(t)   // ios: App.swift:42 (SettingsView·settingsPath: 빈 자리)
                }
            }
            TabBar(model.tab, tint = Theme.teal) { model.tab = it }   // ios: App.swift:45
        }
        // 처음 실행이면 첫 질문을 탭 화면 위에 덮는다
        StartOverlay(showStart) { model.finishStart() }
    }
}

// ios: App.swift:125,126 .transition(.opacity) + .animation(.default): iOS 17 기본 = 감쇠 1 · 0.55초 스프링
@Composable
private fun StartOverlay(show: Boolean, done: () -> Unit) {
    val a = animateFloatAsState(if (show) 1f else 0f, spring(dampingRatio = 1f, stiffness = stiffness(response = 0.55f)), label = "start")
    if (a.value > 0f) StartView(Modifier.graphicsLayer { alpha = a.value }, done)
}

/** 첫 질문 화면 자리 (StartView 이식 전: 아무 데나 누르면 탭으로) */
@Composable
private fun StartView(modifier: Modifier, done: () -> Unit) {
    Box(
        modifier.fillMaxSize().background(Theme.bg)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = done),
    )
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

