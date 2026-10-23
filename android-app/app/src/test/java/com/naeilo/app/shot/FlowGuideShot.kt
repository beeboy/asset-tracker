package com.naeilo.app.shot

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.flow.FlowGuide
import androidx.compose.foundation.layout.Box
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 흐름 탭 '흐름 읽는 법' 페이지 (작은 그림 + 설명). 해머 줄을 골라 연 모습
// 찍기 ./gradlew :app:recordRoborazziDebug --tests 'com.naeilo.app.shot.FlowGuideShot'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class FlowGuideShot {
    @get:Rule val rule = createComposeRule()

    @Test fun guide() {
        ApplicationProvider.getApplicationContext<android.content.Context>().let { Store.init(it); Defaults.init(it) }
        rule.setContent { Box(Modifier.width(393.dp).height(2000.dp)) { FlowGuide("hammer") {} } }
        rule.onRoot().captureRoboImage()
    }
}
