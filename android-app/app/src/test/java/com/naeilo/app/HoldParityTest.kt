package com.naeilo.app

import com.naeilo.app.data.Period
import com.naeilo.app.data.StubPriceProvider
import com.naeilo.app.ui.hold.AvgCalc
import com.naeilo.app.ui.hold.TagChart
import com.naeilo.app.ui.hold.TodayBox
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor
import kotlin.math.sin

// 종목 탭(2026-10-23) 숫자 맞추기: 아이폰과 같은 입력으로 같은 값을 쓰는지
// (날 수·날짜로 자른 일봉·주봉, 평단 계산기, 축 꼬리표 자리, 차선 칸 나누기). Swift 쪽은 같은 함수 원문을 떼어 만든 작은 실행 파일이 쓴다
class HoldParityTest {
    private fun f(v: Double, n: Int) = String.format(Locale.ROOT, "%.${n}f", v)

    @Test fun write() {
        val out = ArrayList<String>()
        val sv = StubPriceProvider
        for ((y, m, d) in listOf(Triple(1970, 1, 1), Triple(2000, 2, 29), Triple(2024, 12, 31), Triple(2026, 10, 23), Triple(1999, 3, 1), Triple(2100, 3, 1)))
            out.add("day ${sv.dayNum(y, m, d)} ${sv.dayNum(String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)) ?: -1}")
        for (s in listOf("2026-10-23T00:00", "bad", "2026-1-5", "")) out.add("dayS ${sv.dayNum(s)?.toString() ?: "nil"}")
        // 3년치 평일 종가 (주말 빼고)
        val ds = ArrayList<String>(); val cs = ArrayList<Double>()
        var t = LocalDate.of(2023, 10, 20); var v = 100.0; var i = 0
        while (!t.isAfter(LocalDate.of(2026, 10, 23))) {
            if (t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY) { ds.add(t.toString()); v *= 1 + 0.01 * sin(i * 0.37); cs.add(floor(v * 1000 + 0.5) / 1000); i++ }
            t = t.plusDays(1)
        }
        for (p in listOf(Period.m1, Period.m3, Period.m6, Period.y1, Period.y3)) for (now in listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 1, 1))) {
            val r = sv.cut(ds, cs, p, 123.456, now); out.add("cut $p ${r.size} ${r.joinToString(",") { f(it, 6) }}")
        }
        out.add("cut1 ${sv.cut(listOf("2026-10-22"), listOf(50.0), Period.m1, 51.0, LocalDate.of(2026, 10, 23))}")
        out.add("cut0 ${sv.cut(listOf("2020-01-02", "2020-01-03"), listOf(50.0, 52.0), Period.m3, 55.0, LocalDate.of(2026, 10, 23))}")
        for (tab in listOf("qty", "amt")) for ((nq, na, np) in listOf(Triple<Double?, Double?, Double?>(10.0, 1000.0, 99.5), Triple<Double?, Double?, Double?>(null, null, null), Triple<Double?, Double?, Double?>(0.5, 123456.0, 1.0)))
            for ((qty, avg) in listOf(5.0 to 80.0, 0.0 to 0.0, 3.5 to 120.25)) for (k in listOf(1.0, 1388.5)) {
                val r = AvgCalc.calc(tab, nq, na, np, qty, avg, 101.25, k); out.add("calc ${listOf(r.first, r.second, r.third).joinToString(",") { f(it, 10) }}")
            }
        val yf = { x: Double -> (140 - x).toFloat() }
        val tg = { at: Double, dy: Float -> TagChart.Tag(at = at, dy = dy.dp, text = "", bg = Color.Black) }
        for (tags in listOf(listOf(tg(10.0, 0f), tg(12.0, 20f), tg(70.0, 0f)), listOf(tg(0.0, 0f), tg(1.0, 0f)), listOf(tg(139.0, 0f), tg(138.0, 0f)), listOf(tg(50.0, 0f))))
            out.add("place ${TagChart.place(tags, yf, 140f, 1f).joinToString(",") { f(it.second.toDouble(), 4) }}")
        for (s in listOf(listOf(0.5, 0.2, 0.3), listOf(0.0, 0.0, 0.0), listOf(0.95, 0.0, 0.05), listOf(0.33, 0.34, 0.33), listOf(0.5, 0.0, 0.5), listOf(0.04, 0.0, 0.96)))
            for (n in listOf(1, 7, 12, 13)) { val (u, d, fl) = TodayBox.lane(s, n); out.add("lane [$u, $d, $fl]") }
        val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/hold_parity").apply { mkdirs() }
        File(dir, "kotlin.txt").writeText(out.joinToString("\n") + "\n")
    }
}
