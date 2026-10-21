package com.naeilo.app.ui.wave

import androidx.compose.animation.core.Animatable
import com.naeilo.app.core.tr
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.stiffness
import kotlinx.coroutines.delay

/** 한 판이 끝나면 게임 창 위에 뜨는 팝업: 별 · 점수 · 탄/내린 곳 · 진짜 날짜 · 한 판 더 */
// ios: Views/Wave/WaveResultPopup.swift:4,10
@Composable
fun WaveResultPopup(
    result: WaveResult,   // ios: Views/Wave/WaveResultPopup.swift:5
    again: () -> Unit,   // ios: Views/Wave/WaveResultPopup.swift:6
    close: () -> Unit,   // ios: Views/Wave/WaveResultPopup.swift:7
) {
    // @State shown: 처음 뜰 때 0 → 1 로 튀어 오른다 (팝업 크기·흐림)
    val shown = remember { Animatable(0f) }   // ios: Views/Wave/WaveResultPopup.swift:8
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = stiffness(0.3f))) }   // ios: Views/Wave/WaveResultPopup.swift:76
    // ios: Views/Wave/WaveResultPopup.swift:11
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(remember { MutableInteractionSource() }, indication = null) {})   // ios: Views/Wave/WaveResultPopup.swift:12
        // ios: Views/Wave/WaveResultPopup.swift:13,69,70,71,72,73,74
        Column(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 320.dp).fillMaxWidth()
                .graphicsLayer { val k = 0.85f + 0.15f * shown.value; scaleX = k; scaleY = k; alpha = shown.value.coerceIn(0f, 1f) }
                .background(Theme.card, RoundedCornerShape(20.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(result.course.island.label, style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/Wave/WaveResultPopup.swift:14
            // ios: Views/Wave/WaveResultPopup.swift:15,24,25
            Row(Modifier.clearAndSetSemantics { contentDescription = tr("별 ${result.stars}개") }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // ios: Views/Wave/WaveResultPopup.swift:16
                for (i in 0 until 3) {
                    // ios: Views/Wave/WaveResultPopup.swift:20,21
                    val pop = remember { Animatable(0.3f) }
                    LaunchedEffect(Unit) { delay((150 * i).toLong()); pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = stiffness(0.35f))) }
                    // ios: Views/Wave/WaveResultPopup.swift:17,18,19
                    SymOr(if (i < result.stars) "star.fill" else "star", 30f, if (i < result.stars) Theme.yellow else Theme.dash, scaled = false,
                        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                }
            }
            T("${result.score}점", style = appFont(34f, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"), color = Theme.ink)   // ios: Views/Wave/WaveResultPopup.swift:26
            T(result.headline, style = appFont(13f), color = Theme.sub)   // ios: Views/Wave/WaveResultPopup.swift:27
            WaveRideChart(result, Modifier.fillMaxWidth().height(84.dp))   // ios: Views/Wave/WaveResultPopup.swift:28
            // ios: Views/Wave/WaveResultPopup.swift:29,33
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val st = appFont(11f); val c = Theme.muted
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { SymOr("circle.fill", 11f, c); T("탄 곳", style = st, color = c) }   // ios: Views/Wave/WaveResultPopup.swift:30
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { SymOr("circle", 11f, c); T("내린 곳", style = st, color = c) }   // ios: Views/Wave/WaveResultPopup.swift:31
            }
            // ios: Views/Wave/WaveResultPopup.swift:34
            if (result.missions.isNotEmpty()) {
                // ios: Views/Wave/WaveResultPopup.swift:35
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // ios: Views/Wave/WaveResultPopup.swift:36,37
                    for (x in result.missions) {
                        val st = appFont(12f, FontWeight.Bold)   // ios: Views/Wave/WaveResultPopup.swift:45
                        // ios: Views/Wave/WaveResultPopup.swift:38
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SymOr(if (x.done) "checkmark.circle.fill" else "circle", 12f,   // ios: Views/Wave/WaveResultPopup.swift:39
                                if (x.done) Theme.teal else Theme.dash)   // ios: Views/Wave/WaveResultPopup.swift:40
                            T(x.title, style = st, color = if (x.done) Theme.ink else Theme.muted)   // ios: Views/Wave/WaveResultPopup.swift:41
                            Spacer(Modifier.weight(1f))   // ios: Views/Wave/WaveResultPopup.swift:42
                            if (x.done) T("+50", style = st.copy(fontFeatureSettings = "tnum"), color = Theme.teal)   // ios: Views/Wave/WaveResultPopup.swift:43
                        }
                    }
                }
            }
            // ios: Views/Wave/WaveResultPopup.swift:49
            if (result.unlocked.isNotEmpty()) {
                T("새로 열렸어요! " + result.unlocked.joinToString(" · "),   // ios: Views/Wave/WaveResultPopup.swift:50
                    style = appFont(13f, FontWeight.ExtraBold).copy(textAlign = TextAlign.Center), color = hex(0xD9480F))   // ios: Views/Wave/WaveResultPopup.swift:51
            }
            T(result.dateLine, style = appFont(13f, FontWeight.Bold).copy(textAlign = TextAlign.Center), color = Theme.ink)   // ios: Views/Wave/WaveResultPopup.swift:53
            // ios: Views/Wave/WaveResultPopup.swift:54,66,67
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ios: Views/Wave/WaveResultPopup.swift:55,56,57,58
                Box(Modifier.weight(1f).plainClickable(close).background(Theme.line, RoundedCornerShape(12.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    T("닫기", style = appFont(15f, FontWeight.Bold), color = Theme.ink)
                }
                // ios: Views/Wave/WaveResultPopup.swift:60,61,62,63
                Box(Modifier.weight(1f).plainClickable(again).background(Theme.teal, RoundedCornerShape(12.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    T("바로 다시", style = appFont(15f, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

/** 코스 전체 선 + 탄 구간 (번 곳 빨강 · 잃은 곳 파랑, 탄 곳 ● 내린 곳 ○) */
// ios: Views/Wave/WaveResultPopup.swift:81,82,83
@Composable
fun WaveRideChart(result: WaveResult, modifier: Modifier = Modifier) {
    val dash = Theme.dash; val up = Theme.up; val down = Theme.down; val card = Theme.card
    // ios: Views/Wave/WaveResultPopup.swift:84,106
    Canvas(modifier.semantics { contentDescription = tr(result.headline) }) {
        val k = density   // 아래 수는 pt
        val v = result.course.values; val n = v.size   // ios: Views/Wave/WaveResultPopup.swift:85
        if (!(n > 1)) return@Canvas; val lo = v.min(); val hi = v.max()   // ios: Views/Wave/WaveResultPopup.swift:86
        // ios: Views/Wave/WaveResultPopup.swift:87
        fun pt(i: Int): Offset =
            Offset(size.width * i / (n - 1), size.height - 6 * k - ((v[i] - lo) / maxOf(hi - lo, 1.0)).toFloat() * (size.height - 12 * k))   // ios: Views/Wave/WaveResultPopup.swift:88
        val all = Path()   // ios: Views/Wave/WaveResultPopup.swift:90
        all.moveTo(pt(0).x, pt(0).y)   // ios: Views/Wave/WaveResultPopup.swift:91
        for (i in 1 until n) all.lineTo(pt(i).x, pt(i).y)   // ios: Views/Wave/WaveResultPopup.swift:92
        drawPath(all, dash, style = Stroke(2 * k, join = StrokeJoin.Round))   // ios: Views/Wave/WaveResultPopup.swift:93
        // ios: Views/Wave/WaveResultPopup.swift:94
        for (r in result.rides) {
            if (!(r.to > r.from)) continue
            val seg = Path()   // ios: Views/Wave/WaveResultPopup.swift:95
            seg.moveTo(pt(r.from).x, pt(r.from).y)   // ios: Views/Wave/WaveResultPopup.swift:96
            for (i in (r.from + 1)..r.to) seg.lineTo(pt(i).x, pt(i).y)   // ios: Views/Wave/WaveResultPopup.swift:97
            val col = if (r.gain > 0) up else down   // ios: Views/Wave/WaveResultPopup.swift:98
            drawPath(seg, col, style = Stroke(3 * k, cap = StrokeCap.Round, join = StrokeJoin.Round))   // ios: Views/Wave/WaveResultPopup.swift:99
            val a = pt(r.from); val b = pt(r.to)   // ios: Views/Wave/WaveResultPopup.swift:100
            drawOval(col, Offset(a.x - 3.5f * k, a.y - 3.5f * k), Size(7 * k, 7 * k))   // ios: Views/Wave/WaveResultPopup.swift:101
            drawOval(card, Offset(b.x - 3.5f * k, b.y - 3.5f * k), Size(7 * k, 7 * k))   // ios: Views/Wave/WaveResultPopup.swift:102
            drawOval(col, Offset(b.x - 3.5f * k, b.y - 3.5f * k), Size(7 * k, 7 * k), style = Stroke(2 * k))   // ios: Views/Wave/WaveResultPopup.swift:103
        }
    }
}
