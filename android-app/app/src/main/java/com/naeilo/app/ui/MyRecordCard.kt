package com.naeilo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.data.DayRec
import kotlin.math.max

// naeilo를 쓴 날부터의 내 기록 (종목 탭): 연 날마다 남긴 실제 평가액과 넣은 돈.
// 위 '지금 구성으로 본' 그래프(지금 수량 × 지난 가격)와 따로 보여 준다
// ios: Views/MyRecordCard.swift:5,6,8
@Composable
fun MyRecordCard(m: AppModel) {
    val D = if (m.isSample) emptyList() else m.myRecord   // ios: Views/MyRecordCard.swift:9
    val L = D.lastOrNull()
    // ios: Views/MyRecordCard.swift:10
    if (L != null) {
        val gain = if (L.c > 0) L.v / L.c - 1 else null   // ios: Views/MyRecordCard.swift:11
        // ios: Views/MyRecordCard.swift:12
        Card {
            Head(D)   // ios: Views/MyRecordCard.swift:13
            // ios: Views/MyRecordCard.swift:14
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Cell("평가액", AppModel.man(L.v), Theme.ink)   // ios: Views/MyRecordCard.swift:15
                Cell("넣은 돈", AppModel.man(L.c), Theme.ink)   // ios: Views/MyRecordCard.swift:16
                Cell("손익", gain?.let { AppModel.sgn(it) } ?: "-", gain?.let { Theme.change(it) } ?: Theme.ink)   // ios: Views/MyRecordCard.swift:17
            }
            // ios: Views/MyRecordCard.swift:19
            if (D.size >= 2) {
                RecordLines(v = D.map { it.v }, c = D.map { it.c }, Modifier.fillMaxWidth().height(110.dp))   // ios: Views/MyRecordCard.swift:20
                // ios: Views/MyRecordCard.swift:21
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend("평가액", Theme.teal, dash = false)   // ios: Views/MyRecordCard.swift:22
                    Legend("넣은 돈", Theme.muted, dash = true)   // ios: Views/MyRecordCard.swift:23
                }
            } else {   // ios: Views/MyRecordCard.swift:25
                T("내일 다시 열면 선이 그려져요", style = appFont(12f), color = Theme.muted)   // ios: Views/MyRecordCard.swift:26
            }
        }
    } else {   // ios: Views/MyRecordCard.swift:29
        // 아직 한 줄도 없을 때도 카드 자리는 보인다 (어디에 쌓이는지 알 수 있게)
        // ios: Views/MyRecordCard.swift:31
        Card {
            T("내 기록", style = appFont(15f, FontWeight.Bold))   // ios: Views/MyRecordCard.swift:32
            T(if (m.isSample || m.holdings.isEmpty()) "내 종목 넣은 날부터 하루 한 줄 · 샘플은 안 남김"   // ios: Views/MyRecordCard.swift:33
              else "시세 받으면 첫 줄이 남아요 · 당겨서 새로 고침",   // ios: Views/MyRecordCard.swift:34
              style = appFont(12f).ls(3f), color = Theme.muted)   // ios: Views/MyRecordCard.swift:35
        }
    }
}

// ios: Views/MyRecordCard.swift:40
@Composable
private fun Head(D: List<DayRec>) {
    // ios: Views/MyRecordCard.swift:41,43
    Row(Modifier.fillMaxWidth()) {
        T("내 기록", Modifier.weight(1f).alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/MyRecordCard.swift:42
        // ios: Views/MyRecordCard.swift:44
        val f = D.firstOrNull(); val l = D.lastOrNull()
        if (f != null && l != null) T("${Day.md(f.d)} → ${Day.md(l.d)}", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)
    }
}

// ios: Views/MyRecordCard.swift:48
@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(k: String, v: String, c: Color) {
    // ios: Views/MyRecordCard.swift:49,52
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(k, style = appFont(12f), color = Theme.sub)   // ios: Views/MyRecordCard.swift:50
        FitText(v, appFont(15f, FontWeight.Bold), 0.6f, maxLines = 1, color = c)   // ios: Views/MyRecordCard.swift:51
    }
}
// ios: Views/MyRecordCard.swift:54
@Composable
private fun Legend(t: String, c: Color, dash: Boolean) {
    // ios: Views/MyRecordCard.swift:55
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/MyRecordCard.swift:56,57
        Canvas(Modifier.size(14.dp, 8.dp)) {
            drawLine(c, Offset(0f, 4.dp.toPx()), Offset(14.dp.toPx(), 4.dp.toPx()), 2.dp.toPx(), pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null)
        }
        T(t, style = appFont(11f), color = Theme.sub)   // ios: Views/MyRecordCard.swift:58
    }
}

/** 평가액(실선)과 넣은 돈(점선) 두 줄. 같은 세로 눈금 */
// ios: Views/MyRecordCard.swift:64,65,66
@Composable
private fun RecordLines(v: List<Double>, c: List<Double>, modifier: Modifier) {
    val muted = Theme.muted; val teal = Theme.teal
    // ios: Views/MyRecordCard.swift:67,79
    Canvas(modifier.semantics { hideFromAccessibility() }) {
        val all = v + c; val lo = all.minOrNull() ?: 0.0; val hi = all.maxOrNull() ?: 1.0   // ios: Views/MyRecordCard.swift:68
        val n = max(1, v.size - 1).toFloat()   // ios: Views/MyRecordCard.swift:69
        // ios: Views/MyRecordCard.swift:70,71
        val pt = { i: Int, y: Double ->
            Offset(i.toFloat() / n * size.width, (1 - ((y - lo) / max(1.0, hi - lo)).toFloat()) * (size.height - 4.dp.toPx()) + 2.dp.toPx())
        }
        // ios: Views/MyRecordCard.swift:73
        val line = { ys: List<Double> -> Path().apply { ys.forEachIndexed { i, y -> val o = pt(i, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } } }
        // ios: Views/MyRecordCard.swift:74
        drawPath(line(c), muted, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))   // ios: Views/MyRecordCard.swift:75
        drawPath(line(v), teal, style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))   // ios: Views/MyRecordCard.swift:76
    }
}
