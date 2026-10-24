package com.naeilo.app.shot

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.naeilo.app.AppModel
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.analysis.AnalysisView
import com.naeilo.app.ui.flow.FlowView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 탭 화면 통째로: 흐름(키 없음 → 샘플 DRNK)·분석(내 길 메인). 움직임은 멈춘 장면
// 찍기 ./gradlew :app:recordRoborazziDebug --tests 'com.naeilo.app.shot.TabsShot'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1800dp-xxhdpi")
class TabsShot {
    @get:Rule val rule = createComposeRule()

    private fun model(): AppModel {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Store.init(ctx); Defaults.init(ctx)   // 앱이 켤 때처럼
        return AppModel(ctx, null)
    }

    @Test fun flow() {
        val m = model()
        rule.setContent { CompositionLocalProvider(LocalReduceMotion provides true) { Box(Modifier.width(393.dp).height(852.dp)) { FlowView(m) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage()
    }

    @Test fun analysis() {
        val m = model()
        rule.setContent { CompositionLocalProvider(LocalReduceMotion provides true) { Box(Modifier.width(393.dp).height(1800.dp)) { AnalysisView(m) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage()
    }
}
