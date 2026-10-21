package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.pulse
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// 목표일 전망 화면 조각: 선형 축 범위 그래프, 일정 한 줄 (외부 요인 화면도 같이 쓸 수 있게 따로 둔다)

/** 선형 축 범위 그래프: 고른 시나리오의 50%·90% 띠와 중앙값, 다른 시나리오 중앙값(회색 점선), 목표선, 지금 점 */
// ios: Views/ForecastParts.swift:6
object ScenarioChart {
    /** 눈금 간격: 위 끝까지 눈금이 3칸 이하가 되는 가장 작은 값 (5천만, 1억, 2.5억, 5억, 10억 …) */
    // ios: Views/ForecastParts.swift:17
    fun step(top: Double): Double {
        var s = 5e7   // ios: Views/ForecastParts.swift:18
        val mult: List<Double> = listOf(2.0, 2.5, 2.0)   // ios: Views/ForecastParts.swift:19
        var i = 0   // ios: Views/ForecastParts.swift:20
        while (top / s > 3) { s *= mult[i % 3]; i += 1 }   // ios: Views/ForecastParts.swift:21
        return s   // ios: Views/ForecastParts.swift:22
    }
    // ios: Views/ForecastParts.swift:24
    fun axis(v: Double): String {
        if (v <= 0) return "0"   // ios: Views/ForecastParts.swift:25
        val e = v / 1e8   // ios: Views/ForecastParts.swift:26
        return if (e == kotlin.math.round(e)) "${e.toInt()}억" else cformat("%.1f억", e)   // Views/ForecastParts.swift:27 (검사 예외: 같은 글)
    }
}

/** 그래프 위 끝 (눈금 간격의 배수) */
private fun tops(top0: Double): Pair<Double, Double> { val st = ScenarioChart.step(top0); return Pair(st, max(st, ceil(top0 / st) * st)) }

// ios: Views/ForecastParts.swift:6,41
@Composable
fun ScenarioChart(
    sel: AppModel.Forecast,   // ios: Views/ForecastParts.swift:7
    /** 고른 시나리오 값에 더할 금액 (몬테카를로 경로에 없는 현금) */
    selShift: Double,   // ios: Views/ForecastParts.swift:9
    selLabel: String,   // ios: Views/ForecastParts.swift:10
    others: List<Pair<String, List<Double>>>,   // ios: Views/ForecastParts.swift:11
    goal: Double,   // ios: Views/ForecastParts.swift:12
    goalLabel: String,   // ios: Views/ForecastParts.swift:13
    endLabel: String,   // ios: Views/ForecastParts.swift:14
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val ink = Theme.ink; val muted = Theme.muted; val line = Theme.line; val teal = Theme.teal; val tealDark = Theme.tealDark; val orange = Theme.orange
    val now0 = ForecastView.amt((sel.q50.firstOrNull() ?: 0.0) + selShift)

    /** 지금 점 자리 (박동 원을 그래프 위에 따로 얹는 데 쓴다). 그림의 x·y 와 같은 셈 */
    // ios: Views/ForecastParts.swift:31
    fun nowPoint(size: Size, dp: Float): Offset? {
        val t0 = sel.tY.firstOrNull(); if (sel.q50.size <= 1 || t0 == null) return null   // ios: Views/ForecastParts.swift:32
        val sh = selShift   // ios: Views/ForecastParts.swift:33
        val top0 = maxOf(goal, sel.q95.map { it + sh }.maxOrNull() ?: 1.0, others.flatMap { it.second }.maxOrNull() ?: 0.0) * 1.05   // ios: Views/ForecastParts.swift:34
        val (st, top) = tops(top0)   // ios: Views/ForecastParts.swift:35
        val left = 36 * dp; val W = size.width - left; val H = size.height - 20 * dp   // ios: Views/ForecastParts.swift:36
        val span = max(1e-9, sel.tY.lastOrNull() ?: 1.0); val v = sel.q50[0] + sh   // ios: Views/ForecastParts.swift:37
        if (st <= 0) return null
        return Offset(left + (t0 / span).toFloat() * W, H - (min(max(v, 0.0), top) / top).toFloat() * H)   // ios: Views/ForecastParts.swift:38
    }

    // .font(.system(size: 10)): 큰 글자 설정을 따르지 않는다
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // ios: Views/ForecastParts.swift:42,103,104
    Canvas(modifier.fillMaxWidth().semantics { contentDescription = tr("시나리오별 자산 범위 그래프") }
        .pulse(ink, dot = 8.dp) { s -> nowPoint(s, density) }
        .drawWithCache {
            // 크기·값이 바뀔 때만 길·글자를 만든다 (그리는 중엔 계산 없음)
            val n = sel.q50.size - 1   // ios: Views/ForecastParts.swift:43
            val sh = selShift   // ios: Views/ForecastParts.swift:45
            val q5 = sel.q5.map { it + sh }; val q25 = sel.q25.map { it + sh }; val q50 = sel.q50.map { it + sh }   // ios: Views/ForecastParts.swift:46
            val q75 = sel.q75.map { it + sh }; val q95 = sel.q95.map { it + sh }   // ios: Views/ForecastParts.swift:47
            val top0 = maxOf(goal, q95.maxOrNull() ?: 1.0, others.flatMap { it.second }.maxOrNull() ?: 0.0) * 1.05   // ios: Views/ForecastParts.swift:48
            val (st, top) = tops(top0)   // ios: Views/ForecastParts.swift:49
            val left = 36.dp.toPx(); val bottom = 20.dp.toPx(); val W = size.width - left; val H = size.height - bottom   // ios: Views/ForecastParts.swift:50
            val span = max(1e-9, sel.tY.lastOrNull() ?: 1.0)   // ios: Views/ForecastParts.swift:51
            val x = { i: Int -> left + (sel.tY[i] / span).toFloat() * W }   // ios: Views/ForecastParts.swift:52
            val y = { v: Double -> H - (min(max(v, 0.0), top) / top).toFloat() * H }   // ios: Views/ForecastParts.swift:53
            // ios: Views/ForecastParts.swift:54,55
            fun line(a: List<Double>): Path = Path().apply { for (i in 0..min(n, a.size - 1)) { val px = x(i); val py = y(a[i]); if (i == 0) moveTo(px, py) else lineTo(px, py) } }
            // ios: Views/ForecastParts.swift:57,58
            fun band(a: List<Double>, b: List<Double>): Path = Path().apply {
                for (i in 0..n) { val px = x(i); val py = y(a[i]); if (i == 0) moveTo(px, py) else lineTo(px, py) }   // ios: Views/ForecastParts.swift:59
                for (i in IntProgression.fromClosedRange(n, 0, -1)) lineTo(x(i), y(b[i]))   // ios: Views/ForecastParts.swift:60
                close()   // ios: Views/ForecastParts.swift:61
            }
            // 눈금 (가로선 + 왼쪽 값)
            val ticks = ArrayList<Pair<Float, TextLayoutResult>>()
            var v = 0.0   // ios: Views/ForecastParts.swift:65
            // ios: Views/ForecastParts.swift:66,67,69,70
            if (n > 0) while (v <= top + 1) { ticks.add(Pair(y(v), tm.measure(ScenarioChart.axis(v), TextStyle(fontSize = 10.sp, color = muted)))); v += st }
            val oth = others.filter { it.second.size > n }.map { o -> Triple(line(o.second), y(o.second[n]), tm.measure(com.naeilo.app.core.tr(o.first), TextStyle(fontSize = 10.sp, color = muted))) }   // ios: Views/ForecastParts.swift:75,77
            val selT = tm.measure(com.naeilo.app.core.tr(selLabel), TextStyle(fontSize = 10.sp, color = tealDark, fontWeight = FontWeight.Bold))   // ios: Views/ForecastParts.swift:81
            val goalT = tm.measure(com.naeilo.app.core.tr(goalLabel), TextStyle(fontSize = 10.sp, color = orange, fontWeight = FontWeight.SemiBold))   // ios: Views/ForecastParts.swift:87
            val nowT = tm.measure(com.naeilo.app.core.tr("지금 $now0"), TextStyle(fontSize = 10.sp, color = ink, fontWeight = FontWeight.SemiBold))   // ios: Views/ForecastParts.swift:92
            val nowAx = tm.measure(com.naeilo.app.core.tr("지금"), TextStyle(fontSize = 10.sp, color = muted))   // ios: Views/ForecastParts.swift:96
            // X축: 지금 / 1년 뒤 / 2년 뒤 / 끝날
            val yrsT = (1..max(1, span.toInt())).filter { it.toDouble() < span - 0.4 && it <= 4 }.map { yr -> Pair(left + (yr / span).toFloat() * W, tm.measure(com.naeilo.app.core.tr("${yr}년 뒤"), TextStyle(fontSize = 10.sp, color = muted))) }   // ios: Views/ForecastParts.swift:97,98,99
            val endT = tm.measure(com.naeilo.app.core.tr(endLabel), TextStyle(fontSize = 10.sp, color = muted))   // ios: Views/ForecastParts.swift:101
            val outer = if (n > 0) band(q95, q5) else null; val inner = if (n > 0) band(q75, q25) else null; val med = if (n > 0) line(q50) else null
            onDrawBehind {
                if (n <= 0) return@onDrawBehind   // ios: Views/ForecastParts.swift:44
                ticks.forEach { (yy, t) -> drawLine(line, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx()); drawText(t, topLeft = Offset(left - 4.dp.toPx() - t.size.width, yy - t.size.height / 2f)) }   // ios: Views/ForecastParts.swift:68,69
                // 띠와 선
                drawPath(outer!!, teal.copy(alpha = 0.14f))   // ios: Views/ForecastParts.swift:73
                drawPath(inner!!, teal.copy(alpha = 0.30f))   // ios: Views/ForecastParts.swift:74
                // ios: Views/ForecastParts.swift:76,78
                oth.forEach { (p, yy, t) ->
                    drawPath(p, muted, style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    drawText(t, topLeft = Offset(size.width - 2.dp.toPx() - t.size.width, yy - 7.dp.toPx() - t.size.height / 2f))
                }
                drawPath(med!!, tealDark, style = Stroke(2.4.dp.toPx()))   // ios: Views/ForecastParts.swift:80
                drawText(selT, topLeft = Offset(size.width - 2.dp.toPx() - selT.size.width, y(q50[n]) - 8.dp.toPx() - selT.size.height / 2f))   // ios: Views/ForecastParts.swift:82
                // 목표선
                val gy = y(goal)   // ios: Views/ForecastParts.swift:84
                drawLine(orange, Offset(left, gy), Offset(size.width, gy), 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))   // ios: Views/ForecastParts.swift:85,86
                drawText(goalT, topLeft = Offset(left + 4.dp.toPx(), (if (gy < 12.dp.toPx()) gy + 8.dp.toPx() else gy - 8.dp.toPx()) - goalT.size.height / 2f))   // ios: Views/ForecastParts.swift:88
                // 지금 점
                val p0 = Offset(x(0), y(q50[0]))   // ios: Views/ForecastParts.swift:90
                drawOval(ink, Offset(p0.x - 4.dp.toPx(), p0.y - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))   // ios: Views/ForecastParts.swift:91
                drawText(nowT, topLeft = Offset(p0.x + 6.dp.toPx(), p0.y + 12.dp.toPx() - nowT.size.height / 2f))   // ios: Views/ForecastParts.swift:93
                val ly = size.height - 6.dp.toPx()   // ios: Views/ForecastParts.swift:95
                drawText(nowAx, topLeft = Offset(left, ly - nowAx.size.height / 2f))
                yrsT.forEach { (xx, t) -> drawText(t, topLeft = Offset(xx - t.size.width / 2f, ly - t.size.height / 2f)) }
                drawText(endT, topLeft = Offset(size.width - endT.size.width, ly - endT.size.height / 2f))
            }
        }) {}
}

/** 일정 한 줄: 날짜(D-day) + 이름. 데이터는 MarketEvents (전망 계산과 같은 목록) */
@Composable
// ios: Views/ForecastParts.swift:109,110,111
fun ScheduleRow(hit: MarketEvents.Hit, open: (AnalysisRoute) -> Unit) {
    // ios: Views/ForecastParts.swift:112,128
    Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ios: Views/ForecastParts.swift:113
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T(MarketEvents.`when`(hit.day), Modifier.width(92.dp).alignByBaseline(), style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/ForecastParts.swift:114
            T(hit.kind, Modifier.weight(1f).padding(end = 0.dp).alignByBaseline(), style = appFont(13f), color = Theme.ink)   // ios: Views/ForecastParts.swift:115,116
        }
        // 금리 결정 행 아래에만 금리 충격 테스트로 가는 버튼
        // ios: Views/ForecastParts.swift:119
        if (MarketEvents.isRate(hit.id)) {
            // ios: Views/ForecastParts.swift:120,121,122,123,125
            T("금리 오르면? ›", Modifier.padding(start = 102.dp).plainClickable { open(AnalysisRoute.rateScen) }
                .background(Theme.purple.copy(alpha = 0.12f), CapsuleShape).padding(horizontal = 12.dp, vertical = 7.dp),
              style = appFont(13f, FontWeight.Bold), color = Theme.purple)
        }
    }
}
