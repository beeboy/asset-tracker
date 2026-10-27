package com.naeilo.app.shot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.home.BoxScene
import com.naeilo.app.ui.home.SceneBox
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 홈 평가액 박스 모양 세 가지 (점프 직전·지평선·욕조와 오리). 움직임은 멈춘 첫 장면 (동작 줄이기)
// 찍기 ./gradlew :app:recordRoborazziDebug --tests 'com.naeilo.app.shot.HomeBoxShot'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1400dp-xxhdpi")
class HomeBoxShot {
    @get:Rule val rule = createComposeRule()

    private val goal = BoxScene(label = "평가액", amount = 5.16e8, f = 0.516, plan = 0.55,
        target = "10억", today = -0.043, ret = 0.12, status = "조금 늦어요")

    @Test fun looks() {
        ApplicationProvider.getApplicationContext<android.content.Context>().let { Store.init(it); Defaults.init(it) }
        rule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                Column(Modifier.background(Color.White).padding(16.dp).width(361.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (l in listOf(BoxLook.jump, BoxLook.horizon, BoxLook.tub)) {
                        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Theme.night)) { SceneBox(l, goal, hide = false) {} }
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }
}
