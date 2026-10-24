package com.naeilo.app.shot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Sample
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.home.BoxLook
import com.naeilo.app.ui.hold.AvgCalc
import com.naeilo.app.ui.hold.TagChart
import com.naeilo.app.ui.hold.TodayBox
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 종목 탭 맨 위 오늘 박스(박스 모양 4가지의 등락 띠) · 그래프 카드 꼬리표 · 평단 계산기
// 찍기 ./gradlew :app:recordRoborazziDebug --tests 'com.naeilo.app.shot.HoldBoxShot'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1700dp-xxhdpi")
class HoldBoxShot {
    @get:Rule val rule = createComposeRule()

    @Test fun today() {
        ApplicationProvider.getApplicationContext<android.content.Context>().let { Store.init(it); Defaults.init(it) }
        val pts = (0..40).map { 4.2e8 + 3e6 * kotlin.math.sin(it / 5.0) + it * 6e5 }
        rule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                Column(Modifier.background(Theme.bg).padding(16.dp).width(361.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (l in BoxLook.entries) TodayBox(look = l, move = 4.49e6, pct = 0.009, parts = listOf(3.1e8, 0.4e8, 1.6e8), gain = 2.0573e8, ret = 0.703)
                    Card {
                        TagChart(points = pts, height = 120.dp, tags = listOf(
                            TagChart.Tag(at = pts.first(), text = "4억 2,000만", bg = TagChart.gray),
                            TagChart.Tag(at = pts.last(), text = "+2,712만", bg = TodayBox.upC),
                        ), left = "9월", right = "오늘")
                    }
                    AvgCalc(sym = Sample.symbol("DRNK")!!, qty = 3.0, avg = 80.0, du = true)
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }
}
