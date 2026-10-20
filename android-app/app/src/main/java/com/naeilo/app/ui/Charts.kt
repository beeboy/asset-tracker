package com.naeilo.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.data.Period
import com.naeilo.app.data.Sample
import com.naeilo.app.net.StockCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 작은 가격 그래프: 오르면 빨강, 내리면 파랑. 평균 단가 점선은 범위 안일 때만.
// 선은 Canvas 에 직접 그리고, 길·눈금은 크기·점이 바뀔 때만 다시 만든다 (그리는 중 계산 없음).
// 점이 화면 화소보다 많으면 화소 칸마다 첫·최소·최대·끝 값만 남겨 그린다 (모양은 같고 그릴 점은 폭만큼).
// ios: Views/Charts.swift:4,14
@Composable
fun Sparkline(
    points: List<Double>,   // ios: Views/Charts.swift:5
    modifier: Modifier = Modifier,
    avg: Double? = null,   // ios: Views/Charts.swift:6
    lineWidth: Dp = 1.6.dp,   // ios: Views/Charts.swift:7
    showEndDot: Boolean = false,   // ios: Views/Charts.swift:8
    avgLabel: String? = null,      // ios: Views/Charts.swift:9 점선 바로 밑, 오른쪽에 붙이는 글자
    avgAlways: Boolean = false,            // ios: Views/Charts.swift:10 평균 단가가 범위 밖이어도 세로 범위를 넓혀 점선을 늘 보인다
    scrub: Scrub? = null,          // ios: Views/Charts.swift:11 있으면 끌어서 그 시점 날짜·시세를 본다
) {
    var at by remember { mutableStateOf<Int?>(null) }   // ios: Views/Charts.swift:12
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val muted = Theme.muted; val sub = Theme.sub
    val lo0 = points.minOrNull() ?: 0.0; val hi0 = points.maxOrNull() ?: 1.0   // ios: Views/Charts.swift:16
    val avgIn = avg?.let { avgAlways || (it >= lo0 && it <= hi0) } ?: false   // ios: Views/Charts.swift:17
    val lo = if (avgIn) min(lo0, avg) else lo0; val hi = if (avgIn) max(hi0, avg) else hi0   // ios: Views/Charts.swift:18
    val n = max(1, points.size - 1)   // ios: Views/Charts.swift:19
    // 점선 금액을 붙일 때는 아래에 글자 한 줄 자리를 남겨 두어 늘 점선 밑에 들어가게 한다
    val bot = with(density) { (if (avgIn && avgLabel != null) 18 else 2).dp.toPx() }   // ios: Views/Charts.swift:22
    val up = (points.lastOrNull() ?: 0.0) >= (points.firstOrNull() ?: 0.0)   // ios: Views/Charts.swift:24
    val c = Theme.change(if (up) 1.0 else -1.0)   // ios: Views/Charts.swift:42,44,48 (오르면 빨강 1, 내리면 파랑 -1)
    // ios: Views/Charts.swift:15,69
    BoxWithConstraints(modifier.semantics { invisibleToUser() }) {
        val W = with(density) { maxWidth.toPx() }; val H = with(density) { maxHeight.toPx() }
        val x = { i: Int -> i.toFloat() / n * W }   // ios: Views/Charts.swift:20
        val y = { v: Double -> (1 - ((v - lo) / max(1e-9, hi - lo)).toFloat()) * (H - with(density) { 2.dp.toPx() } - bot) + with(density) { 2.dp.toPx() } }   // ios: Views/Charts.swift:23
        // ios: Views/Charts.swift:25,63,64,65,66,67
        Box(
            Modifier.fillMaxSize()
                .then(if (scrub == null || points.size < 2) Modifier else Modifier.pointerInput(points.size, W) {
                    fun idx(px: Float) = ((min(max(px / max(1f, W), 0f), 1f)) * n).roundToInt()
                    awaitEachGesture {
                        val d = awaitFirstDown()
                        at = idx(d.position.x); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        while (true) {
                            val e = awaitPointerEvent(); val ch = e.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            val i = idx(ch.position.x); if (i != at) { at = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            ch.consume()
                        }
                        at = null
                    }
                })
                .drawWithCache {
                    // 크기·점이 바뀔 때만 길을 만든다
                    val line = Path()   // ios: Views/Charts.swift:36
                    val keep = thin(points, size.width.roundToInt())
                    // ios: Views/Charts.swift:37,38,39
                    keep.forEachIndexed { k, i -> val pt = Offset(x(i), y(points[i])); if (k == 0) line.moveTo(pt.x, pt.y) else line.lineTo(pt.x, pt.y) }
                    val stroke = Stroke(lineWidth.toPx(), join = StrokeJoin.Round)   // ios: Views/Charts.swift:42
                    val dash = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))   // ios: Views/Charts.swift:28
                    onDrawBehind {
                        // ios: Views/Charts.swift:26,27
                        if (avgIn) drawLine(muted, Offset(0f, y(avg)), Offset(size.width, y(avg)), dash.width, pathEffect = dash.pathEffect)
                        drawPath(line, c, style = stroke)
                        // 끄는 동안: 세로선 + 점
                        // ios: Views/Charts.swift:47,48,49,50,51
                        val i = at
                        if (scrub != null && i != null && i < points.size) {
                            val px = x(i)
                            drawLine(sub, Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
                            drawOval(c, Offset(px - 4.dp.toPx(), y(points[i]) - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))
                        }
                    }
                },
        ) {
            // ios: Views/Charts.swift:29,30,31,32,33
            if (avgIn && avgLabel != null) {
                T(avgLabel, Modifier.align(Alignment.TopEnd).layout { m, cs -> val p = m.measure(cs); layout(p.width, p.height) { p.place(0, (y(avg) + 9.dp.toPx() - p.height / 2f).roundToInt()) } }
                    .background(Theme.card.copy(alpha = 0.85f), CapsuleShape).padding(horizontal = 3.dp),
                  style = appFont(11f, FontWeight.SemiBold), color = muted, maxLines = 1)
            }
            // ios: Views/Charts.swift:43,44
            val l = points.lastOrNull()
            if (showEndDot && l != null && at == null) {
                PulseDot(color = Theme.change(if (up) 1.0 else -1.0), modifier = Modifier.offset { IntOffset((x(points.size - 1) - 7.dp.toPx() * 4.2f / 2).roundToInt(), (y(l) - 7.dp.toPx() * 4.2f / 2).roundToInt()) })
            }
            // 그림처럼: 위에 날짜·시각(작게, 회색), 밑에 시세(크게). 테두리 없이 카드 바탕만 깔아 제목 글자를 가린다
            val i = at
            if (scrub != null && i != null && i < points.size) {
                val px = x(i)
                // ios: Views/Charts.swift:53,58,59,60 (가운데가 x 70 ~ 폭-70 안, 위로 24)
                Column(
                    Modifier.wrapContentSize(unbounded = true).layout { m, cs ->
                        val p = m.measure(cs.copy(minWidth = 0, maxWidth = Int.MAX_VALUE, minHeight = 0))
                        val cx = min(max(px, 70.dp.toPx()), W - 70.dp.toPx())
                        layout(0, 0) { p.place((cx - p.width / 2f).roundToInt(), (-24.dp.toPx() - p.height / 2f).roundToInt()) }
                    }.background(Theme.card).padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    T(Scrub.fmt.format(scrub.date(i.toDouble() / n)), style = appFont(12f), color = muted, maxLines = 1)   // ios: Views/Charts.swift:54,57
                    T(scrub.value(points[i]), style = appFont(20f, FontWeight.Bold), color = Theme.ink, maxLines = 1)   // ios: Views/Charts.swift:55
                }
            }
        }
    }
}

/** 화소 칸마다 첫·최소·최대·끝 점만 남긴다 (점이 폭보다 적으면 그대로). 돌려주는 것은 원래 점의 번호 */
internal fun thin(p: List<Double>, w: Int): List<Int> {
    if (p.size <= max(2, w * 2)) return p.indices.toList()
    val out = ArrayList<Int>(w * 4); val n = p.size - 1
    var col = -1; var a = 0; var mn = 0; var mx = 0; var z = 0
    fun flush() { if (col < 0) return; listOf(a, mn, mx, z).distinct().sorted().forEach { out.add(it) } }
    for (i in p.indices) {
        val c = (i.toLong() * w / max(1, n)).toInt()
        if (c != col) { flush(); col = c; a = i; mn = i; mx = i; z = i }
        else { if (p[i] < p[mn]) mn = i; if (p[i] > p[mx]) mx = i; z = i }
    }
    flush()
    return out
}

/** 그래프 끌어 보기: 0…1 위치 → 시점, 값 → 글자 */
// ios: Views/Charts.swift:74
class Scrub(
    val date: (Double) -> Instant,   // ios: Views/Charts.swift:75
    val value: (Double) -> String,   // ios: Views/Charts.swift:76
) {
    companion object {
        // ios: Views/Charts.swift:77
        val fmt: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", AppModel.dateLocale).withZone(ZoneId.systemDefault()) }
        /** 기간 그래프: 왼쪽 끝 = 기간 전, 오른쪽 끝 = 지금 */
        // ios: Views/Charts.swift:79
        fun period(p: Period, value: (Double) -> String): Scrub {
            val days: Double = if (p == Period.d1) 1.0 else if (p == Period.w1) 7.0 else p.span * 1095   // ios: Views/Charts.swift:80
            val now = Instant.now()   // ios: Views/Charts.swift:81
            return Scrub(date = { now.minusMillis(((1 - it) * days * 86400 * 1000).toLong()) }, value = value)   // ios: Views/Charts.swift:82
        }
    }
}

// 글자 마크 로고 (상표 로고 아님). DRNK 는 드링커 도트 로고.
// ios: Views/Charts.swift:87,90
@Composable
fun LogoTile(
    symbol: String,   // ios: Views/Charts.swift:88
    size: Dp = 36.dp,   // ios: Views/Charts.swift:89
) {
    val p = Sample.profiles[symbol]   // ios: Views/Charts.swift:91
    val ctx = LocalContext.current
    // ios: Views/Charts.swift:92,93,103,104
    Box(Modifier.size(size).background(hex(p?.color ?: 0x5B6670), RoundedCornerShape(size * 0.28f)).semantics { invisibleToUser() }, contentAlignment = Alignment.Center) {
        val img = p?.logoImage
        // ios: Views/Charts.swift:94,95
        if (img != null) {
            Image(ImageBitmap.imageResource(drawableId(ctx, img)), null, Modifier.size(size * 0.86f, size * 0.86f), filterQuality = FilterQuality.None)
        } else {   // ios: Views/Charts.swift:96
            // 추가한 종목은 이름 첫 글자
            val mono = p?.mono ?: (Sample.symbol(symbol)?.name ?: StockCatalog.all.firstOrNull { it.id == symbol }?.name ?: symbol).take(1).uppercase()   // ios: Views/Charts.swift:98
            // ios: Views/Charts.swift:99,100 (.font(.system(size:)) 는 큰 글자를 따르지 않는다)
            BasicText(mono, style = TextStyle(fontSize = (size.value * (if (mono.length > 1) 0.36f else 0.46f)).sp, fontWeight = FontWeight.Black, color = hex(p?.fg ?: 0xFFFFFF)))
        }
    }
}

// 현재 점 심장 박동: 점에서 점 색 동심원이 두 번(두근) 살짝 퍼지다 사라지고 잠깐 쉰다.
// 원만 그리기 단계에서 그려서, 움직이는 동안 그래프(안쪽 층)는 다시 그리지 않는다
// ios: Views/Charts.swift:110
object Pulse {
    const val period = 1.6; const val spread = 0.9; const val gap = 0.2   // ios: Views/Charts.swift:111
}

/** 지금 시각(초)을 1/30초마다: 그리기 단계에서만 읽는다. 동작 줄이기·숨은 탭이면 멈춘다 (null) */
@Composable
private fun pulseClock(): androidx.compose.runtime.FloatState? {
    val still = LocalReduceMotion.current || !LocalTabActive.current   // ios: Views/Charts.swift:118,120
    val t = remember { mutableFloatStateOf(0f) }
    // Views/Charts.swift:121 TimelineView(.animation(minimumInterval: 1 / 30)): 화면 프레임 시계를 1/30초마다만 받는다 (검사 예외)
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = 0L; val gap = (1.0 / 30 * 1e9).toLong()
        while (true) withFrameNanos { n -> if (n - last >= gap) { last = n; t.floatValue = (n / 1e9).toFloat() } }
    }
    return if (still) null else t
}

/** 박동 원 그리기 (가운데 c, 점 지름 d) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.ring(color: Color, d: Float, c: Offset, now: Float) {
    val ph = now.toDouble() % Pulse.period   // ios: Views/Charts.swift:124
    // ios: Views/Charts.swift:122,125
    for (k in 0 until 2) {
        val a = (ph - k.toDouble() * Pulse.gap) / Pulse.spread   // ios: Views/Charts.swift:126
        if (!(a > 0 && a < 1)) continue   // ios: Views/Charts.swift:127
        val r = d / 2 + a.toFloat() * d * 1.5f   // ios: Views/Charts.swift:128
        // ios: Views/Charts.swift:129,130
        drawOval(color.copy(alpha = (0.6 * (1 - a)).toFloat()), Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(width = 1.5f * density))
    }
}

/** 퍼지는 원만 (가운데 = 점 자리). 동작 줄이기를 켜면 안 그린다 */
// ios: Views/Charts.swift:115,119
@Composable
fun PulseRing(
    color: Color,   // ios: Views/Charts.swift:116
    dot: Dp = 7.dp,   // ios: Views/Charts.swift:117
    modifier: Modifier = Modifier,
) {
    val t = pulseClock() ?: return
    // ios: Views/Charts.swift:123,134,135
    Canvas(modifier.size(dot * 4.2f, dot * 4.2f)) { ring(color, dot.toPx(), Offset(size.width / 2, size.height / 2), t.floatValue) }
}

/** SwiftUI 쪽에서 쓰는 박동 점 (원 + 점) */
// ios: Views/Charts.swift:141,144
@Composable
fun PulseDot(
    color: Color,   // ios: Views/Charts.swift:142
    dot: Dp = 7.dp,   // ios: Views/Charts.swift:143
    modifier: Modifier = Modifier,
) {
    // ios: Views/Charts.swift:145
    Box(modifier.size(dot * 4.2f), contentAlignment = Alignment.Center) {
        PulseRing(color = color, dot = dot)   // ios: Views/Charts.swift:146
        Box(Modifier.size(dot, dot).background(color, androidx.compose.foundation.shape.CircleShape))   // ios: Views/Charts.swift:147
    }
}

/**
 * 그래프(Canvas) 위에 박동 원만 얹는다. at: 그래프 크기 → 점 자리 (없으면 null).
 * 시간은 그리기 단계에서만 읽어 화면을 다시 짜지 않는다. 안쪽 그래프는 따로 층이라 원만 다시 그린다.
 */
// ios: Views/Charts.swift:152,154,155,156,159
@Composable
fun Modifier.pulse(color: Color, dot: Dp, at: (Size) -> Offset?): Modifier {
    val t = pulseClock() ?: return this
    return this.drawWithContent {
        drawContent()
        val c = at(size) ?: return@drawWithContent   // ios: Views/Charts.swift:157
        ring(color, dot.toPx(), c, t.floatValue)
    }.graphicsLayer {}
}

// 금액이 바뀔 때 숫자가 한 자리씩 위아래로 넘어가며 바뀐다 (플립). 글로 된 숫자(분석 카드 값 등)도 같다
// ios: Views/Charts.swift:165,166,167,171,173,174
@Composable
fun RollText(text: String, value: Any, modifier: Modifier = Modifier, style: TextStyle) {
    // 오르면 새 숫자가 아래에서 올라오고, 내리면 위에서 내려온다 (.numericText(value:)). 글이면 위로
    var last by remember { mutableStateOf(value) }
    val upward = (value as? Double)?.let { v -> (last as? Double)?.let { v >= it } } ?: true
    LaunchedEffect(value) { last = value }
    // .animation(.snappy): 0.5초, 튐 0.15 스프링
    val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.85f, stiffness = stiffness(0.5f))
    // 글자 칸마다 넘기는 동안에도 첫 줄 기준선을 밖으로 넘긴다 (옆 글자와 기준선 맞추기)
    val full = androidx.compose.ui.text.rememberTextMeasurer().measure(text, style, maxLines = 1)
    val base = full.firstBaseline; val fullH = full.size.height
    androidx.compose.ui.layout.Layout(content = {
        Row(Modifier.clipToBounds()) {
            text.forEachIndexed { i, ch ->
                androidx.compose.runtime.key(text.length - i) {
                    AnimatedContent(ch, transitionSpec = {
                        (slideInVertically(spec) { h -> if (upward) h else -h }) togetherWith (slideOutVertically(spec) { h -> if (upward) -h else h })
                    }, label = "roll") { c ->
                        // 글자마다 글꼴이 달라도(숫자·한글) 같은 기준선에 서게 둔다
                        androidx.compose.ui.layout.Layout({ BasicText(c.toString(), style = style) }) { ms, cs ->
                            val p = ms[0].measure(cs); val b = p[androidx.compose.ui.layout.FirstBaseline]
                            val dy = if (b == androidx.compose.ui.layout.AlignmentLine.Unspecified) 0 else base.roundToInt() - b
                            layout(p.width, maxOf(fullH, p.height + dy)) { p.place(0, dy) }
                        }
                    }
                }
            }
        }
    }, modifier = modifier) { ms, c ->
        val p = ms.first().measure(c)
        layout(p.width, p.height, mapOf(androidx.compose.ui.layout.FirstBaseline to base.roundToInt())) { p.place(0, 0) }
    }
}

