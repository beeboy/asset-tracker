package com.naeilo.app.shot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.Palette
import com.naeilo.app.ui.analysis.ChanceBox
import com.naeilo.app.ui.hold.TodayBox
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.home.BoxScene
import com.naeilo.app.ui.home.SceneBox
import com.naeilo.app.ui.home.boxInk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 상단 박스 테마 일곱 × 라이트·다크: 테마마다 홈 · 종목(오른 날) · 분석. 움직임은 멈춘 첫 장면 (동작 줄이기)
// 찍기 ./gradlew :app:recordRoborazziDebug --tests 'com.naeilo.app.shot.TopBoxShot'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2300dp-xhdpi")
class TopBoxShot {
    @get:Rule val rule = createComposeRule()

    private val goal = BoxScene(label = "평가액", amount = 4.9782e8, f = 0.5, plan = 0.55, ticks = listOf("1년" to 0.62, "2년" to 0.8),
        target = "10억", half = "5억", today = -0.043, ret = 0.703, status = "조금 늦어요")
    private val chance = ChanceBox.Data(head = "10억 · 2029.10 · 3.0년 남음", verdict = "낮음", pct = "33%", status = "", now = 0.42, goal = 0.83,
        dots = listOf(0.5, 0.58, 0.92), goalLabel = "10억", foot = "셋 중 하나만 선을 넘음", nowText = "지금 5.04억")

    private fun shot(looks: List<BoxLook>, dark: Boolean) {
        ApplicationProvider.getApplicationContext<android.content.Context>().let { Store.init(it); Defaults.init(it) }
        rule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true, LocalPal provides if (dark) Palette.Dark else Palette.Light) {
                Column(Modifier.background(if (dark) Color(0xFF0B0E14) else Color(0xFFEEF0F3)).padding(16.dp).width(361.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (l in looks) Three(l)
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Composable private fun Three(l: BoxLook) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(boxInk().card)) { SceneBox(l, goal, hide = false) {} }
        TodayBox(look = l, move = 4.49e6, pct = 0.009, parts = listOf(3.1e8, 0.15e8, 0.7e8), gain = 2.0573e8, ret = 0.703)
        ChanceBox(l, chance)
    }

    @Test fun newLight() = shot(listOf(BoxLook.dory, BoxLook.pino, BoxLook.mug), false)
    @Test fun newDark() = shot(listOf(BoxLook.dory, BoxLook.pino, BoxLook.mug), true)
    @Test fun oldLight() = shot(listOf(BoxLook.jump, BoxLook.horizon, BoxLook.tub, BoxLook.bar), false)
    @Test fun oldDark() = shot(listOf(BoxLook.jump, BoxLook.horizon, BoxLook.tub, BoxLook.bar), true)
}
