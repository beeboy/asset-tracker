package com.naeilo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

// Views/Charts.swift 중 홈·첫 질문이 쓰는 박동 원 (나머지 그래프는 종목 단계에서 옮긴다)

// 현재 점 심장 박동: 점에서 점 색 동심원이 두 번(두근) 살짝 퍼지다 사라지고 잠깐 쉰다.
// 원만 그리기 단계에서 그려서, 움직이는 동안 그래프(안쪽 층)는 다시 그리지 않는다
// ios: Views/Charts.swift:110
object Pulse {
    const val period = 1.6; const val spread = 0.9; const val gap = 0.2   // ios: Views/Charts.swift:111
}

/**
 * 그래프(Canvas) 위에 박동 원만 얹는다. at: 그래프 크기 → 점 자리 (없으면 null).
 * 동작 줄이기·숨은 탭이면 안 그린다. 시간은 그리기 단계에서만 읽어 화면을 다시 짜지 않는다.
 */
// ios: Views/Charts.swift:154,155,156,159
@Composable
fun Modifier.pulse(color: Color, dot: Dp, at: (Size) -> Offset?): Modifier {
    val still = LocalReduceMotion.current || !LocalTabActive.current   // ios: Views/Charts.swift:118,120
    val t = remember { mutableFloatStateOf(0f) }
    // ios: Views/Charts.swift:121 TimelineView(.animation(minimumInterval: 1 / 30)): 1/30초마다
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { n -> if (n - last >= 1_000_000_000L / 30) { last = n; t.floatValue = (n / 1e9).toFloat() } }
    }
    if (still) return this
    return this.drawWithContent {
        drawContent()
        val c = at(size) ?: return@drawWithContent   // ios: Views/Charts.swift:157,123 (가운데 = 점 자리)
        val d = dot.toPx()
        val ph = t.floatValue.toDouble() % Pulse.period   // ios: Views/Charts.swift:124
        // ios: Views/Charts.swift:122,125
        for (k in 0 until 2) {
            val a = (ph - k.toDouble() * Pulse.gap) / Pulse.spread   // ios: Views/Charts.swift:126
            if (!(a > 0 && a < 1)) continue   // ios: Views/Charts.swift:127
            val r = d / 2 + a.toFloat() * d * 1.5f   // ios: Views/Charts.swift:128
            // ios: Views/Charts.swift:129,130
            drawCircle(color.copy(alpha = (0.6 * (1 - a)).toFloat()), r, c, style = Stroke(width = 1.5f * density))
        }
    }.graphicsLayer {}   // 안쪽 그래프는 따로 층에 둬서 원만 다시 그린다 (ios: Views/Charts.swift:134,135 원 칸은 손에 닿지 않음)
}
