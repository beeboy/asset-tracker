package com.naeilo.app.shot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.naeilo.app.data.Defaults
import com.naeilo.app.core.Store
import com.naeilo.app.data.Earnings
import com.naeilo.app.ui.hold.EarningsRiver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 화면 그림 시험: 에뮬레이터 없이 내 몫 실적 강을 그려 PNG로 남긴다 (빌드 폴더 outputs/roborazzi)
// 찍기 ./gradlew :app:recordRoborazziDebug --tests 'com.naeilo.app.shot.*'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class EarningsRiverShot {
    @get:Rule val rule = createComposeRule()

    @Test fun drnk() {
        ApplicationProvider.getApplicationContext<android.content.Context>().let { Store.init(it); Defaults.init(it) }   // 앱이 켤 때처럼
        val s = Earnings.summary(Earnings.drnk, 10.0, 1400.0)!!
        rule.setContent { Box(Modifier.background(Color.White)) { EarningsRiver("DRNK", 10.0, s, frozen = true) } }
        rule.onRoot().captureRoboImage()
    }
}
