package com.naeilo.widget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.naeilo.app.core.Fmt
import com.naeilo.app.core.Snapshot
import com.naeilo.app.core.WL
import com.naeilo.app.core.WSmall
import com.naeilo.app.core.cformat
import java.time.Instant
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// 위젯 그래프들. 아이폰 GeometryReader 대신 그릴 칸 (x, y, gw, gh) 을 받아 그 안 좌표로 그린다

/** 점 하나 (아이폰 Circle().frame(width:height:).position(...)) */
private fun Pen.dot(c: Offset, ww: Float, hh: Float, col: Color) = oval(c.x - ww / 2, c.y - hh / 2, ww, hh, col)
/** 테두리만 있는 점 (아이폰 Circle().stroke) */
private fun Pen.ring(c: Offset, ww: Float, hh: Float, col: Color, lw: Float) =
    d.drawOval(col, Offset(c.x - ww / 2, c.y - hh / 2), Size(ww, hh), alpha = alpha, style = Stroke(lw))
/** 글자를 (cx, cy) 가운데에 (아이폰 .fixedSize().position(...)) */
private fun Pen.textAt(s: String, fs: Float, cx: Float, cy: Float, col: Color, weight: androidx.compose.ui.text.font.FontWeight = W.regular) {
    val sz = size(s, fs, weight)
    text(s, fs, cx - sz.width / 2, cy - sz.height / 2, weight, col)
}

/** 선 그래프 (최근 값 흐름) */
// ios: Widgets/Views/Charts.swift:4,5,6,7,8,9
fun Pen.spark(values: List<Double>, color: Color, x: Float, y: Float, gw: Float, gh: Float, width: Float = 2.2f) = at(x, y) {
    // ios: Widgets/Views/Charts.swift:10
    val v = values; val lo = v.minOrNull() ?: 0.0; val hi = v.maxOrNull() ?: 1.0; val r = max(hi - lo, 1.0)
    // ios: Widgets/Views/Charts.swift:11,12
    val p = Path(); for ((i, xv) in v.withIndex()) {
        // ios: Widgets/Views/Charts.swift:13
        val pt = Offset(gw * i / max(1, v.size - 1), gh * (1 - ((xv - lo) / r).toFloat()))
        // ios: Widgets/Views/Charts.swift:14
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    // ios: Widgets/Views/Charts.swift:17
    stroke(p, color, width, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

/** 점들을 지나는 매끈한 곡선 (Catmull-Rom) */
// ios: Widgets/Views/Charts.swift:22,24,25,26
fun Path.smooth(p: List<Offset>) { val f = p.firstOrNull() ?: return; moveTo(f.x, f.y)
    // ios: Widgets/Views/Charts.swift:27,28
    for (i in 0 until max(0, p.size - 1)) { val p0 = p[max(0, i - 1)]; val p1 = p[i]; val p2 = p[i + 1]; val p3 = p[min(p.size - 1, i + 2)]
        // ios: Widgets/Views/Charts.swift:29,30
        cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
    }
}

/** 자산 추이: 최근 며칠을 매끈한 선 하나로, 밑은 연한 채움, 첫날 값 높이에 점선.
 *  최고 날은 꽉 찬 점, 최저 날은 (투명·틴트에서는 빈) 점, 오늘은 테두리 있는 큰 점 */
// ios: Widgets/Views/Charts.swift:37,38,39,40,41,42
fun Pen.trendLine(values: List<Double>, color: Color, tint: Tint, x: Float, y: Float, gw: Float, gh: Float) = at(x, y) {
    // ios: Widgets/Views/Charts.swift:43
    val v = values; val lo = v.minOrNull() ?: 0.0; val hi = v.maxOrNull() ?: 1.0; val rg = max(hi - lo, 1.0)
    // ios: Widgets/Views/Charts.swift:44
    val pd = 4f; val w = gw - pd * 2; val h = gh - pd * 2
    // ios: Widgets/Views/Charts.swift:45
    val pts = v.indices.map { i -> Offset(pd + w * i / max(1, v.size - 1), pd + h * (1 - ((v[i] - lo) / rg).toFloat())) }
    // ios: Widgets/Views/Charts.swift:46
    val a = pts.firstOrNull(); val z = pts.lastOrNull(); if (a != null && z != null && pts.size > 1) {
        // ios: Widgets/Views/Charts.swift:47
        val iHi = v.lastIndexOf(hi).takeIf { it >= 0 } ?: 0; val iLo = v.lastIndexOf(lo).takeIf { it >= 0 } ?: 0
        // ios: Widgets/Views/Charts.swift:48,49
        fill(Path().apply { smooth(pts); lineTo(z.x, gh); lineTo(a.x, gh); close() }, color.copy(alpha = color.alpha * (if (tint.full) 0.16f else 0.22f)))
        // ios: Widgets/Views/Charts.swift:50,51
        line(0f, a.y, gw, a.y, pal.ink.copy(alpha = if (tint.full) 0.35f else 0.5f), 1f, dash = floatArrayOf(3f, 3f))
        // ios: Widgets/Views/Charts.swift:52
        stroke(Path().apply { smooth(pts) }, color, 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // ios: Widgets/Views/Charts.swift:53
        if (hi > lo && iHi != pts.size - 1) dot(pts[iHi], 5f, 5f, tint.c(pal.up))
        // ios: Widgets/Views/Charts.swift:54
        if (hi > lo && iLo != pts.size - 1) {
            // ios: Widgets/Views/Charts.swift:55
            if (tint.full) dot(pts[iLo], 5f, 5f, pal.dn)
            // ios: Widgets/Views/Charts.swift:56
            else ring(pts[iLo], 5f, 5f, pal.ink, 1.4f)
        }
        // ios: Widgets/Views/Charts.swift:58
        dot(z, 7f, 7f, color); ring(z, 7f, 7f, pal.ink, 1.5f)
    }
}

/** 목표일 전망: 지금에서 출발해 전망 끝(목표일)까지 청록 띠 두 겹(90%·50%)과 중앙선, 주황 점선 목표선, 0부터 시작하는 눈금.
 *  end: 오른쪽 끝 이름 (목표일 / 3년 뒤), hide: 금액을 숨겼으면 눈금 금액도 뺀다 */
// ios: Widgets/Views/Charts.swift:65,66,67,68,69,70,71,72,73
fun Pen.goalFan(now: Double, fan: Snapshot.Fan, goal: Double, end: String, tint: Tint, hide: Boolean, x: Float, y: Float, gw: Float, gh: Float) = at(x, y) {
    // ios: Widgets/Views/Charts.swift:74
    val axisW = if (hide) 0f else 22f; val w = gw - axisW; val h = gh - 12
    // ios: Widgets/Views/Charts.swift:75
    val d0 = Instant.now(); val dEnd = fan.md.lastOrNull() ?: d0; val span = max(86400.0, (dEnd.toEpochMilli() - d0.toEpochMilli()) / 1000.0)
    // ios: Widgets/Views/Charts.swift:76
    val n = minOf(fan.md.size, fan.p5.size, fan.p25.size, fan.p50.size, fan.p75.size, fan.p95.size)
    // ios: Widgets/Views/Charts.swift:77
    val xs = listOf(axisW) + fan.md.take(n).map { axisW + w * min(1.0, max(0.0, (it.toEpochMilli() - d0.toEpochMilli()) / 1000.0 / span)).toFloat() }
    // ios: Widgets/Views/Charts.swift:78
    val top = GoalFan.top(max(goal * 1.1, (fan.p75.take(n).maxOrNull() ?: goal) * 1.1)); val step = top / 3
    // ios: Widgets/Views/Charts.swift:79
    val yy = { v: Double -> h * (1 - (min(max(v, 0.0), top) / top).toFloat()) }
    // ios: Widgets/Views/Charts.swift:80
    val lineP = { a: List<Double> -> Path().apply { for ((k, v) in (listOf(now) + a.take(n)).withIndex()) { val q = Offset(xs[k], yy(v)); if (k == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } } }
    // ios: Widgets/Views/Charts.swift:81,82
    val bandP = { a: List<Double>, b: List<Double> -> Path().apply { val aa = listOf(now) + a.take(n); val bb = listOf(now) + b.take(n)
        // ios: Widgets/Views/Charts.swift:83
        for (k in aa.indices) { val q = Offset(xs[k], yy(aa[k])); if (k == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
        // ios: Widgets/Views/Charts.swift:84
        for (k in bb.indices.reversed()) lineTo(xs[k], yy(bb[k]))
        // ios: Widgets/Views/Charts.swift:85
        close()
    } }
    // ios: Widgets/Views/Charts.swift:87
    val years = (span / (365.25 * 86400)).toInt()
    // ios: Widgets/Views/Charts.swift:88,89
    for (i in 0 until 4) {
        // ios: Widgets/Views/Charts.swift:90
        line(axisW, yy(step * i), gw, yy(step * i), pal.ink.copy(alpha = 0.14f), 1f)
        // ios: Widgets/Views/Charts.swift:91
        if (!hide) { val cx = (axisW - 3) / 2; val lab = GoalFan.lab(step * i); val sz = size(lab, 8f); text(lab, 8f, cx + (axisW - 3) / 2, yy(step * i) - sz.height / 2, color = pal.ink.copy(alpha = 0.65f), maxW = axisW - 3, maxLines = 1, align = 1f) }
    }
    // ios: Widgets/Views/Charts.swift:93
    fill(bandP(fan.p5, fan.p95), tint.c(pal.band, if (tint.full) 0.14f else 0.16f))
    // ios: Widgets/Views/Charts.swift:94
    fill(bandP(fan.p25, fan.p75), tint.c(pal.band, if (tint.full) 0.30f else 0.32f))
    // ios: Widgets/Views/Charts.swift:95
    stroke(lineP(fan.p50), tint.c(pal.bandLine), 2.4f)
    // ios: Widgets/Views/Charts.swift:96,97
    line(axisW, yy(goal), gw, yy(goal), tint.c(pal.orange), 1.6f, dash = floatArrayOf(5f, 4f))
    // ios: Widgets/Views/Charts.swift:98
    text(if (hide) WL.t("목표") else WL.t("목표 {v}", mapOf("v" to GoalFan.lab(goal))), 8f, axisW + 3, max(0f, yy(goal) - 11), W.semibold, tint.c(pal.orange))
    // ios: Widgets/Views/Charts.swift:99
    dot(Offset(axisW, yy(now)), 7f, 7f, pal.ink)
    // ios: Widgets/Views/Charts.swift:100
    text(WL.t("지금"), 8f, axisW, h + 2, color = pal.ink.copy(alpha = 0.65f))
    // ios: Widgets/Views/Charts.swift:101,102
    if (years >= 1) for (k in 1..years) {
        // ios: Widgets/Views/Charts.swift:103
        val xk = axisW + w * (k.toDouble() * 365.25 * 86400 / span).toFloat()
        // ios: Widgets/Views/Charts.swift:104
        if (xk < gw - 30 && xk > axisW + 24) textAt(WL.t("{n}년 뒤", mapOf("n" to "$k")), 8f, xk, h + 7, pal.ink.copy(alpha = 0.65f))
    }
    // ios: Widgets/Views/Charts.swift:107
    text(end, 8f, gw, h + 2, color = pal.ink.copy(alpha = 0.65f), align = 1f)
}

// ios: Widgets/Views/Charts.swift:65
object GoalFan {
    /** 눈금 위 끝: 세 칸이 1·2·2.5·5 의 10배수로 떨어지게 */
    // ios: Widgets/Views/Charts.swift:112,113
    fun top(x: Double): Double { val raw = max(x, 1.0) / 3; val mag = 10.0.pow(floor(log10(raw)))
        // ios: Widgets/Views/Charts.swift:114
        val m = listOf(1.0, 2.0, 2.5, 5.0, 10.0).firstOrNull { it * mag >= raw } ?: 10.0
        // ios: Widgets/Views/Charts.swift:115
        return 3 * m * mag
    }
    /** 0 · 2.5억 · 5천만 */
    // ios: Widgets/Views/Charts.swift:118
    fun lab(v: Double): String {
        // ios: Widgets/Views/Charts.swift:119
        if (v <= 0) return "0"
        // ios: Widgets/Views/Charts.swift:120
        if (WL.en || v < 1e8) return Fmt.man(v)
        // ios: Widgets/Views/Charts.swift:121
        return cformat("%g억", Math.round(v / 1e8 * 10) / 10.0)
    }
}

/** 내 길: 시작부터 지금까지 실제(민트 선), 시작부터 목표일까지 계획선(보라 점선), 지금부터 목표일까지 보통 범위 띠(25~75%) */
// ios: Widgets/Views/Charts.swift:126,127,128,129,130,131,132,133
fun Pen.pathChart(d: WSmall, now: Double, fan: Snapshot.Fan?, goalDate: Instant?, tint: Tint, x: Float, y: Float, gw: Float, gh: Float) = at(x, y) {
    // ios: Widgets/Views/Charts.swift:134
    val w = gw; val h = gh - 12
    // ios: Widgets/Views/Charts.swift:135
    val pts = d.path.filter { it.size == 2 }
    // ios: Widgets/Views/Charts.swift:136
    val fNow = min(1.0, max(0.0, pts.lastOrNull()?.get(0) ?: 0.0))
    // ios: Widgets/Views/Charts.swift:137
    val act: List<Pair<Double, Double>> = pts.dropLast(1).map { Pair(it[0], it[1]) } + listOf(Pair(fNow, now))
    // ios: Widgets/Views/Charts.swift:138
    val band = bandPts(fNow, now, fan, goalDate)
    // ios: Widgets/Views/Charts.swift:139
    val all = act.map { it.second } + d.need + band.flatMap { listOf(it.a, it.b) }
    // ios: Widgets/Views/Charts.swift:140
    val lo = (all.minOrNull() ?: 0.0) * 0.97; val hi = max((all.maxOrNull() ?: 1.0) * 1.03, lo + 1)
    // ios: Widgets/Views/Charts.swift:141
    val xf = { f: Double -> w * f.toFloat() }
    // ios: Widgets/Views/Charts.swift:142
    val yf = { v: Double -> h * (1 - ((v - lo) / (hi - lo)).toFloat()) }
    // ios: Widgets/Views/Charts.swift:143,144,145
    if (band.size > 1) { val p = Path()
        // ios: Widgets/Views/Charts.swift:146
        for ((k, b) in band.withIndex()) { val q = Offset(xf(b.f), yf(b.b)); if (k == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
        // ios: Widgets/Views/Charts.swift:147
        for (b in band.reversed()) p.lineTo(xf(b.f), yf(b.a))
        // ios: Widgets/Views/Charts.swift:148
        p.close()
        // ios: Widgets/Views/Charts.swift:150
        fill(p, tint.c(pal.band, if (tint.full) 0.14f else 0.2f))
    }
    // ios: Widgets/Views/Charts.swift:152
    val need = Path(); for ((k, v) in d.need.withIndex()) { val q = Offset(xf(k.toDouble() / max(1, d.need.size - 1).toDouble()), yf(v)); if (k == 0) need.moveTo(q.x, q.y) else need.lineTo(q.x, q.y) }
    // ios: Widgets/Views/Charts.swift:153
    stroke(need, tint.c(pal.plan), 1.5f, dash = floatArrayOf(4f, 3f))
    // ios: Widgets/Views/Charts.swift:154
    val ap = Path(); for ((k, a) in act.withIndex()) { val q = Offset(xf(a.first), yf(a.second)); if (k == 0) ap.moveTo(q.x, q.y) else ap.lineTo(q.x, q.y) }
    // ios: Widgets/Views/Charts.swift:155
    stroke(ap, tint.c(pal.teal), 2f, join = StrokeJoin.Round)
    // ios: Widgets/Views/Charts.swift:156
    dot(Offset(xf(fNow), yf(now)), 6f, 6f, tint.c(pal.teal))
    // ios: Widgets/Views/Charts.swift:157
    if (xf(fNow) > 26) text(WL.t("시작"), 8f, 0f, h + 2, color = pal.ink.copy(alpha = 0.65f))
    // ios: Widgets/Views/Charts.swift:158
    textAt(WL.t("지금"), 8f, min(max(xf(fNow), 10f), w - 34), h + 7, pal.ink.copy(alpha = 0.65f))
    // ios: Widgets/Views/Charts.swift:159
    text(WL.t("목표일"), 8f, w, h + 2, color = pal.ink.copy(alpha = 0.65f), align = 1f)
}

/** 보통 범위 띠의 한 점 (f, 25%, 75%) */
private class BandPt(val f: Double, val a: Double, val b: Double)

/** 보통 범위 띠: 전망 날짜를 지금(fNow) ~ 목표일(1) 사이로 옮긴다. 목표일 뒤는 버린다 */
// ios: Widgets/Views/Charts.swift:165,166
private fun bandPts(fNow: Double, now: Double, fan: Snapshot.Fan?, goalDate: Instant?): List<BandPt> { val out = mutableListOf(BandPt(fNow, now, now))
    // ios: Widgets/Views/Charts.swift:167
    if (fan == null) return out
    // ios: Widgets/Views/Charts.swift:168
    val t0 = System.currentTimeMillis(); val left = max(86400.0, ((goalDate ?: Instant.now()).toEpochMilli() - t0) / 1000.0)
    // ios: Widgets/Views/Charts.swift:169
    for (k in fan.md.indices) { if (!(k < fan.p25.size && k < fan.p75.size)) continue
        // ios: Widgets/Views/Charts.swift:170
        val x = fNow + (1 - fNow) * ((fan.md[k].toEpochMilli() - t0) / 1000.0) / left
        // ios: Widgets/Views/Charts.swift:171
        if (x > fNow && x <= 1.0001) out.add(BandPt(min(1.0, x), fan.p25[k], fan.p75[k]))
    }
    // ios: Widgets/Views/Charts.swift:173
    return out
}

/** 과녁판 고리 한 겹 (반지름, 채움, 선, 투명·틴트 진하기) */
private class Ring(val r: Float, val fill: Long, val line: Long, val o: Float)

/** 과녁판: 가운데부터 노랑·빨강·파랑·검정 (양궁 과녁, 흰 고리 없음). off = −1 범위 아래 끝 … 0 예측 중앙값 … 1 위 끝.
 *  투명·틴트에서는 고리를 흰색 진하기로, 점은 가장 밝게. (x, y) 왼쪽 위, 76 × 76 */
// ios: Widgets/Views/Charts.swift:179,180,181
fun Pen.targetBoard(off: Double, tint: Tint, x: Float, y: Float) {
    // ios: Widgets/Views/Charts.swift:182
    val rings = listOf(Ring(36f, 0x1b1b1b, 0x555555, 0.1f), Ring(27f, 0x0096dc, 0x0a6fa8, 0.18f), Ring(18f, 0xe3262f, 0xa81c22, 0.28f), Ring(9f, 0xffd83a, 0xc9a400, 0.4f))
    // ios: Widgets/Views/Charts.swift:194,195,196,183,184
    val cx = x + 76f / 2; val cy = y + 76f / 2
    // ios: Widgets/Views/Charts.swift:185,186
    for (i in rings.indices) { val r = rings[i]
        // ios: Widgets/Views/Charts.swift:187,189
        circle(cx, cy, r.r * 2 / 2, if (tint.full) pal.fixed(r.fill) else pal.ink.copy(alpha = r.o))
        // ios: Widgets/Views/Charts.swift:188
        circleStroke(cx, cy, r.r, if (tint.full) pal.fixed(r.line) else pal.ink.copy(alpha = 0.45f), 0.8f)
    }
    // ios: Widgets/Views/Charts.swift:191,192
    val c = Offset(cx, cy - off.toFloat() * 31.5f); dot(c, 9f, 9f, if (tint.full) pal.fixed(0x15181f) else pal.ink); val lw = if (tint.full) 2f else 0f; if (lw > 0) ring(c, 9f, 9f, Color.White, lw)
}
