package com.naeilo.app

import com.naeilo.app.net.Market
import com.naeilo.app.ui.DayMood
import com.naeilo.app.ui.MoodScript
import com.naeilo.app.ui.home.EarnHomeCard
import com.naeilo.app.ui.home.TubScene
import androidx.test.core.app.ApplicationProvider
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

// 홈 탭(2026-10-23) 숫자 맞추기: 아이폰과 같은 입력으로 같은 글을 쓰는지 (오늘 장 흐름·대본 칸·받은 지·자릿수·다음 분기·물높이)
// Swift 쪽은 같은 함수 원문을 떼어 만든 작은 실행 파일이 쓴다. 비교는 입력 칸을 빼고 결과만
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeParityTest {
    @Test fun write() {
        ApplicationProvider.getApplicationContext<android.content.Context>().let { Store.init(it); Defaults.init(it) }   // Market 을 부르면 저장소를 연다
        val out = ArrayList<String>()
        for (i in -500..500 step 5) { val v = i.toDouble() / 10000; out.add("mood $i ${DayMood.of(v)}") }
        for (m in listOf(DayMood.bigUp, DayMood.up, DayMood.flat, DayMood.down, DayMood.bigDown)) for (k in 0 until 160) {
            val s = MoodScript.at(m, k); out.add("step $m $k ${s.act} ${s.dir} ${s.leg} ${s.dx} ${s.dy}")
        }
        for (sec in listOf(-5.0, 0.0, 59.0, 60.0, 61.0, 3599.0, 3600.0, 7199.0, 7200.0, 86399.0, 86400.0, 200000.0, 1e7)) out.add("ago _ ${Market.agoText(sec)}")
        for (p in listOf(-0.12345, -0.0001, 0.0, 0.00004, 0.004999, 0.005, 0.0123456, 1.5)) out.add("sgn _ ${AppModel.sgn(p)} ${AppModel.sgn2(p)}")
        for (q in listOf("1~3", "4~6", "7~9", "10~12", "x")) out.add("next $q ${EarnHomeCard.nextMonths(q)}")
        for (f in listOf(-1.0, 0.0, 0.3, 0.5, 1.0, 1.2, 1.5)) out.add("level _ ${String.format(Locale.ROOT, "%.6f", TubScene.level(f).toDouble())}")
        val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/home_parity").apply { mkdirs() }
        File(dir, "kotlin.txt").writeText(out.joinToString("\n") + "\n")
    }
}
