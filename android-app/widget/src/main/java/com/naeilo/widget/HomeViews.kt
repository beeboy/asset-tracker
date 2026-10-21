package com.naeilo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.naeilo.app.core.Day
import com.naeilo.app.core.Fmt
import com.naeilo.app.core.Snapshot
import com.naeilo.app.core.Store
import com.naeilo.app.core.WL
import com.naeilo.app.core.WSmall
import com.naeilo.app.core.cformat
import com.naeilo.app.core.sample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 홈 화면 작은 위젯 7개 (시안 '작은 위젯 시안' 기준). 서재는 StoryWidget.swift
// 투명·틴트에서는 시스템이 한 색으로 칠하므로 색 대신 ▲▼ 기호·꽉 찬 점/빈 점·진하기로 구분한다
// 아이폰 VStack·HStack·Spacer 는 좌표 계산으로: 콘텐츠 자리는 사방 pad 안쪽, Spacer 는 남는 자리

/** 받는 동안 흐리게 (아이폰 .flip(x): 값이 바뀌면 뒤집히는 애니메이션. 위젯은 움직이지 않아 받는 중 모습만) */
@Suppress("UNUSED_PARAMETER")
private fun <T> Pen.flip(x: Double, block: () -> T): T = fade(block)
/** 한 줄 글자 높이 */
private fun Pen.lineH(fs: Float, weight: FontWeight = W.regular): Float = size("0", fs, weight).height
private fun Pen.dotH(cx: Float, cy: Float, ww: Float, hh: Float, col: Color) = oval(cx - ww / 2, cy - hh / 2, ww, hh, col)
private fun Pen.ringH(cx: Float, cy: Float, ww: Float, hh: Float, col: Color, lw: Float) =
    d.drawOval(col, Offset(cx - ww / 2, cy - hh / 2), Size(ww, hh), alpha = alpha, style = Stroke(lw))

// ios: Widgets/Views/HomeViews.swift:7,8
private fun Pen.big(text: String, x: Float, y: Float, maxW: Float, size: Float = 30f, color: Color? = null, align: Float = 0f): Size =
    text(text, size, x, y, W.heavy, color, maxW = maxW, maxLines = 1, minScale = 0.6f, align = align)
// ios: Widgets/Views/HomeViews.swift:9,10
private fun Pen.mid(text: String, x: Float, y: Float, maxW: Float, color: Color? = null, align: Float = 0f): Size =
    text(text, 20f, x, y, W.heavy, color, maxW = maxW, maxLines = 1, minScale = 0.6f, align = align)

// MARK: 자산 추이
// ios: Widgets/Views/HomeViews.swift:13,14,15,16,17
fun Pen.assetSmall(s: Snapshot) = assetSmallView(s)
private fun Pen.assetSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:26
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:18
    val t = Tint(this); val hide = Store.isHidden(kind)
    // ios: Widgets/Views/HomeViews.swift:19
    val x = pad; val cw = w - pad * 2; var y = pad; val sp = 3f
    // ios: Widgets/Views/HomeViews.swift:20
    y = money { hd -> val sz = flip(s.total) { big(if (hd) hiddenAmount else Fmt.eok(s.total), x, y, cw) }; Rect(x, y, x + sz.width, y + sz.height) }.bottom + sp
    // ios: Widgets/Views/HomeViews.swift:21
    y += flip(s.dayChg) { text("${Fmt.arrow(s.dayChg)} ${Fmt.pct(s.dayChg)}" + (if (hide) "" else " · ${Fmt.man(s.dayAmt)}"), 12f, x, y, W.bold, t.chg(s.dayChg), maxW = cw, maxLines = 1) }.height + sp
    // ios: Widgets/Views/HomeViews.swift:22
    fade { trendLine(s.spark.takeLast(10), t.chg(s.dayChg), t, x, y + 6, cw, 50f) }
    // ios: Widgets/Views/HomeViews.swift:23,24
    val footTop = max(y + 6 + 50 + 0f, h - pad - 22); foot(s, x, footTop + 22, cw, WL.t("10일"))
}

// MARK: 내 길 (실제 선 · 계획선 · 앞으로의 보통 범위)
// ios: Widgets/Views/HomeViews.swift:31,32,33,34
fun Pen.pathSmall(s: Snapshot) = pathSmallView(s)
private fun Pen.pathSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:55
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:35
    val t = Tint(this); val d = Small.load() ?: (if (s.placeholder) WSmall.sample else null)
    // ios: Widgets/Views/HomeViews.swift:36,37
    val x = pad; val cw = w - pad * 2; val y = pad; val bottom = h - pad; val sp = 2f
    // ios: Widgets/Views/HomeViews.swift:38
    val sp1 = 1f; val midH = lineH(20f, W.heavy); val right = x + cw
    // ios: Widgets/Views/HomeViews.swift:46,47,48
    val pTxt = s.pGoal?.let { "${(it * 100).roundToInt()}%" } ?: "-"; val l2 = WL.t("달성 확률"); val rightW = max(size(l2, 12f, W.bold).width, size(pTxt, 20f, W.heavy).width)
    label2(l2, right, y + (22 - lineH(12f, W.bold)) / 2, align = 1f); flip(s.pGoal ?: 0.0) { mid(pTxt, right, y + 22 + 1f + 4, rightW, align = 1f) }
    // ios: Widgets/Views/HomeViews.swift:45
    val leftW = cw - rightW - 2
    // ios: Widgets/Views/HomeViews.swift:39
    val cy = y + 11; val lw = label2(WL.t("내 길"), x, cy - lineH(12f, W.bold) / 2).width; val bx = x + lw + 4; val bw = stamp(s, 0f, 0f, button = true, time = false, measureOnly = true); stamp(s, bx + bw, cy, button = true, time = false); val tw = stamp(s, 0f, 0f, button = false, measureOnly = true); stamp(s, bx + bw + 4 + tw, cy, button = false)
    // ios: Widgets/Views/HomeViews.swift:40,41,42
    if (d != null) { val g = d.gapNow(s.total); flip(g) { mid(WL.t(if (g >= 0) "앞섬 {p}" else "뒤처짐 {p}", mapOf("p" to cformat("%.1f%%", abs(g) * 100))), x, y + 22 + sp1 + 4, leftW, t.c(if (g >= 0) pal.teal else pal.orange)) } }
    val hb = y + 22 + sp1 + 4 + midH
    // ios: Widgets/Views/HomeViews.swift:51,52
    if (d != null) { val top = hb + sp + 4; fade { pathChart(d, d.nowValue(s.total), s.fan, Day.date(s.goalDate), t, x, top, cw, bottom - top) } }
    // ios: Widgets/Views/HomeViews.swift:53
    else { val m = WL.t("앱을 열면 보여요"); val th = size(m, 11f, maxW = cw, maxLines = 3).height; text(m, 11f, x, hb + sp + (bottom - hb - sp - th) / 2, color = pal.ink.copy(alpha = 0.7f), maxW = cw, maxLines = 3) }
}

// MARK: 목표일 전망 (지금부터 목표일까지 부채꼴)
// ios: Widgets/Views/HomeViews.swift:60,61,62,63,64
fun Pen.futureSmall(s: Snapshot) = futureSmallView(s)
private fun Pen.futureSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:80
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:65
    val t = Tint(this); val up = s.term?.let { if (s.total > 0) it.p50 / s.total - 1 else 0.0 }
    // ios: Widgets/Views/HomeViews.swift:66,67
    val x = pad; val cw = w - pad * 2; val y = pad; val bottom = h - pad; val sp = 2f; val right = x + cw; val midH = lineH(20f, W.heavy)
    // 지금보다 몇 % (분석 화면 목표일 전망과 같은 비교)
    // ios: Widgets/Views/HomeViews.swift:70,71,73
    val upTxt = up?.let { Fmt.pct(it, 0) } ?: "-"; val stW = stamp(s, right, y + 11, measureOnly = true); stamp(s, right, y + 11); val rightW = max(stW, size(upTxt, 20f, W.heavy).width); val sp1 = 1f
    flip(up ?: 0.0) { mid(upTxt, right, y + 22 + sp1, rightW, t.c(if ((up ?: 0.0) >= 0) pal.good else pal.dn), align = 1f) }
    // ios: Widgets/Views/HomeViews.swift:69
    val leftW = cw - rightW - 2
    // ios: Widgets/Views/HomeViews.swift:68
    val l2h = label2(WL.t("목표일 전망"), x, y, maxW = leftW).height; money { hd -> val sz = flip(s.term?.p50 ?: 0.0) { mid(if (hd) hiddenAmount else s.term?.let { Fmt.eok(it.p50) } ?: "-", x, y + l2h + 1, leftW) }; Rect(x, y + l2h + 1, x + sz.width, y + l2h + 1 + sz.height) }
    val hb = max(y + l2h + sp1 + midH, y + 22 + sp1 + midH)
    // ios: Widgets/Views/HomeViews.swift:76,77
    val f = s.fan; if (f != null && f.md.isNotEmpty()) { val top = hb + sp + 4; fade { goalFan(s.total, f, s.goal, s.termLabel, t, Store.isHidden(kind), x, top, cw, bottom - top) } }
    // ios: Widgets/Views/HomeViews.swift:78
    else { val m = WL.t("앱을 열면 계산합니다"); val th = size(m, 11f, maxW = cw, maxLines = 3).height; text(m, 11f, x, hb + sp + (bottom - hb - sp - th) / 2, color = pal.ink.copy(alpha = 0.7f), maxW = cw, maxLines = 3) }
}

// MARK: 지금 채우는 블록 (한 블록 = 목표의 1/10)
// 오른쪽 10층 탑이 목표 전체, 왼쪽 격자는 지금 채우는 층을 확대한 100칸
// ios: Widgets/Views/HomeViews.swift:86,87,88,90
fun Pen.blockSmall(s: Snapshot) = blockSmallView(s)
private fun Pen.blockSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:144
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:89
    val cell = 7f; val gap = 1.6f; val link = 12f; val towerW = 16f
    // ios: Widgets/Views/HomeViews.swift:91
    val t = Tint(this)
    // ios: Widgets/Views/HomeViews.swift:92
    val inBlock = s.cells % 100; val block = min(s.cells / 100 + 1, 10); val prevIn = if (s.cellsPrev / 100 == s.cells / 100) s.cellsPrev % 100 else (if (s.cellsPrev > s.cells) 100 else 0)
    // ios: Widgets/Views/HomeViews.swift:93
    val d = s.cells - s.cellsPrev
    // ios: Widgets/Views/HomeViews.swift:94
    val side = cell * 10 + gap * 9; val floorTop = (10 - block) * (cell + gap)
    // ios: Widgets/Views/HomeViews.swift:95
    val x = pad; val cw = w - pad * 2; val y = pad; val sp = 6f
    // ios: Widgets/Views/HomeViews.swift:96
    val gy = y + label2(WL.t("{n}번째 블록 채우는 중", mapOf("n" to "$block")), x, y, maxW = cw).height + sp
    // ios: Widgets/Views/HomeViews.swift:97,98,110
    val hsp = 0f; fade {
        // ios: Widgets/Views/HomeViews.swift:99,100
        for (r in 0 until 10) {
            // ios: Widgets/Views/HomeViews.swift:101,102
            for (c in 0 until 10) { val i = r * 10 + c
                // ios: Widgets/Views/HomeViews.swift:103,104,105
                rrect(x + c * (cell + gap), gy + r * (cell + gap), cell, cell, 1.5f, if (i < inBlock) t.c(pal.goal) else (if (i < prevIn) t.c(pal.dn, 0.6f) else pal.ink.copy(alpha = 0.14f)))
            }
        }
    }
    // 격자와 오른쪽 끝 탑 사이를 채우는 띠: 격자 전체 → 탑의 지금 층
    // ios: Widgets/Views/HomeViews.swift:112,113,120
    val bx = x + side + hsp; val bw = max(link, cw - side - towerW); val m = 2f
    // ios: Widgets/Views/HomeViews.swift:114,115
    val p = Path(); p.moveTo(bx + m, gy + 0); p.lineTo(bx + bw - m, gy + floorTop)
    // ios: Widgets/Views/HomeViews.swift:116
    p.lineTo(bx + bw - m, gy + floorTop + cell); p.lineTo(bx + m, gy + side); p.close()
    // ios: Widgets/Views/HomeViews.swift:118
    fill(p, t.c(pal.goal, 0.18f))
    // ios: Widgets/Views/HomeViews.swift:121,122,135
    val tx = bx + bw; for (k in 0 until 10) {
        // ios: Widgets/Views/HomeViews.swift:123,124,132
        val n = 10 - k; val ty = gy + k * (cell + gap) // 위가 10층, 아래가 1층
        // ios: Widgets/Views/HomeViews.swift:125
        rrect(tx, ty, towerW, cell, 1.5f, pal.ink.copy(alpha = 0.14f))
        // ios: Widgets/Views/HomeViews.swift:126
        if (n < block) rrect(tx, ty, towerW, cell, 1.5f, t.c(pal.goal))
        // ios: Widgets/Views/HomeViews.swift:127
        if (n == block) {
            // ios: Widgets/Views/HomeViews.swift:128
            rrect(tx, ty, towerW * inBlock / 100, cell, 1.5f, t.c(pal.goal))
            // ios: Widgets/Views/HomeViews.swift:129
            rrectStroke(tx, ty, towerW, cell, 1.5f, t.c(pal.goal), 1f)
        }
    }
    // ios: Widgets/Views/HomeViews.swift:137
    val ry = gy + side + sp; val midH = lineH(20f, W.heavy); val rh = max(midH, 22f); val cy = ry + rh / 2; val hs = 5f
    // ios: Widgets/Views/HomeViews.swift:138
    val my = cy - midH / 2; val mw = flip(inBlock.toDouble()) { mid("$inBlock", x, my, cw) }.width; val sw = text("/100", 11f, x + mw + 1, my + baseline("0", 20f, W.heavy) - baseline("0", 11f), alpha = 0.6f).width
    // HomeViews.swift:139 (검사기 한계: 보간 속 따옴표, 예외 목록 참고)
    val dTxt = WL.t("어제 {d}", mapOf("d" to "${if (d >= 0) "+" else ""}$d")); val stW = stamp(s, x + cw, cy, measureOnly = true); val dh = lineH(11f, W.bold)
    flip(d.toDouble()) { text(dTxt, 11f, x + mw + 1 + sw + hs, cy - dh / 2, W.bold, if (d == 0) pal.ink else t.chg(d.toDouble()), maxW = max(0f, cw - mw - 1 - sw - hs * 2 - stW), maxLines = 1) }
    // ios: Widgets/Views/HomeViews.swift:140,141
    val spacer = 0f; stamp(s, x + cw - spacer, cy)
}

// MARK: 목표 페이스 (나 vs 모의 바구니, 같은 날 출발한 뒤 수익률)
// ios: Widgets/Views/HomeViews.swift:149,150,151,152
fun Pen.paceSmall(s: Snapshot) = paceSmallView(s)
private fun Pen.paceSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:173
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:153
    val t = Tint(this); val d = Small.load() ?: (if (s.placeholder) WSmall.sample else null)
    // ios: Widgets/Views/HomeViews.swift:154,155
    val x = pad; val cw = w - pad * 2; var y = pad + 0; y += label2(WL.t("목표 페이스"), x, y, maxW = cw).height
    // ios: Widgets/Views/HomeViews.swift:170,171
    val footTop = h - pad - 22 - 0; foot(s, x, h - pad, cw)
    // ios: Widgets/Views/HomeViews.swift:156
    val me = d?.me; val bk = d?.basket; if (me != null && bk != null) {
        // ios: Widgets/Views/HomeViews.swift:157
        val lo = minOf(0.0, me, bk); val hi = maxOf(me, bk, lo + 0.0001)
        // ios: Widgets/Views/HomeViews.swift:158
        val at = { r: Double -> 0.15 + 0.75 * (r - lo) / (hi - lo) }
        // ios: Widgets/Views/HomeViews.swift:159,160
        y += 8; y += flip(me - bk) { text(WL.t(if (me >= bk) "내가 {p} 빨라요" else "내가 {p} 느려요", mapOf("p" to cformat("%.1f%%", abs(me - bk) * 100))), 15f, x, y, W.heavy, maxW = cw, maxLines = 1, minScale = 0.7f) }.height
        // ios: Widgets/Views/HomeViews.swift:161,165
        y += 10; fade { val vs = 8f
            // ios: Widgets/Views/HomeViews.swift:162
            y += track(WL.t("나"), me, at(me), t.c(pal.teal), filled = true, x, y, cw) + vs
            // ios: Widgets/Views/HomeViews.swift:163
            track(WL.t("모의 바구니"), bk, at(bk), t.c(pal.orange), filled = t.full, x, y, cw)
        }
    // ios: Widgets/Views/HomeViews.swift:166,167,168
    } else { val m = WL.t("앱에서 모의 바구니를 담으면 보여요"); val th = size(m, 13f, W.semibold, maxW = cw, maxLines = 6).height; text(m, 13f, x, y + max(0f, footTop - y - th) / 2, W.semibold, pal.ink.copy(alpha = 0.75f), maxW = cw, maxLines = 6) }
}

/** 한 줄 트랙: 이름·수익률, 출발선에서 지금 위치까지. 투명·틴트에서는 바구니 점을 빈 점으로. 높이를 준다 */
// ios: Widgets/Views/HomeViews.swift:177,178
private fun Pen.track(name: String, r: Double, f: Double, c: Color, filled: Boolean, x: Float, y: Float, cw: Float): Float { val vs = 3f
    // ios: Widgets/Views/HomeViews.swift:179
    val lh = lineH(11f, W.bold); val pw = text(Fmt.pct(r), 11f, x + cw, y, W.bold, maxLines = 1, align = 1f).width; text(name, 11f, x, y, W.bold, c, maxW = max(0f, cw - pw - 8), maxLines = 1)
    // ios: Widgets/Views/HomeViews.swift:180,181,189
    val gy = y + lh + vs; val gh = 12f; val px = x + cw * f.toFloat(); val cy = gy + gh / 2
    // ios: Widgets/Views/HomeViews.swift:182,183,187
    capsule(x, cy - 2f / 2, cw, 2f, pal.ink.copy(alpha = 0.14f))
    // ios: Widgets/Views/HomeViews.swift:184
    capsule(x, cy - 2f / 2, px - x, 2f, c.copy(alpha = c.alpha * (if (filled) 1f else 0.6f)))
    // ios: Widgets/Views/HomeViews.swift:185
    val ox = px - 6; if (filled) dotH(ox + 12f / 2, cy, 12f, 12f, c) else ringH(ox + 12f / 2, cy, 12f, 12f, c, if (filled) 0f else 2f)
    return lh + vs + gh
}

// MARK: 이번 주 과녁 (가운데 = 예측 중앙값, 바깥 원 = 예측 범위, 점 = 지금)
// ios: Widgets/Views/HomeViews.swift:195,196,197,198
fun Pen.targetSmall(s: Snapshot) = targetSmallView(s)
private fun Pen.targetSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:223
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:199
    val t = Tint(this)
    // ios: Widgets/Views/HomeViews.swift:200
    val x = pad; val cw = w - pad * 2; val y = pad + 0; val bottom = h - pad
    // ios: Widgets/Views/HomeViews.swift:201
    val wk = s.week; if (wk != null) {
        // ios: Widgets/Views/HomeViews.swift:202
        val g = wk.act / wk.p50 - 1; val half = if (wk.act >= wk.p50) wk.hi - wk.p50 else wk.p50 - wk.lo
        // ios: Widgets/Views/HomeViews.swift:203
        val dw = text(if (wk.dday == 0) WL.t("오늘") else "D-${wk.dday}", 12f, x + cw, y, W.bold, align = 1f).width; val lh = label2(WL.t("이번 주 과녁"), x, y, maxW = cw - dw - 8).height
        // ios: Widgets/Views/HomeViews.swift:204,211
        val by = y + lh + 8; val hs = 8f
        // ios: Widgets/Views/HomeViews.swift:205
        fade { targetBoard(if (half > 0) max(-1.0, min(1.0, (wk.act - wk.p50) / half)) else 0.0, t, x, by) }
        // ios: Widgets/Views/HomeViews.swift:206
        val vs = 6f; val h11 = lineH(11f); val midH = lineH(20f, W.heavy); val vy = by + (76 - (h11 + vs + midH)) / 2; val vx = x + 76 + hs
        // ios: Widgets/Views/HomeViews.swift:207
        text(WL.t("예측보다"), 11f, vx, vy, alpha = 0.75f, maxW = cw - 76 - hs)
        // ios: Widgets/Views/HomeViews.swift:208
        flip(g) { mid(Fmt.pct(g), vx, vy + h11 + vs, cw - 76 - hs, t.chg(g)) }
        // ios: Widgets/Views/HomeViews.swift:212,214
        val hitsH = max(lineH(10f), 9f); val hy = bottom - hitsH; hits(s, t, x, hy, hitsH); val gap = 4f + 0f
        // ios: Widgets/Views/HomeViews.swift:213
        stamp(s, x + cw, hy - gap - 11)
    // ios: Widgets/Views/HomeViews.swift:215,216
    } else { val lh = label2(WL.t("이번 주 과녁"), x, y, maxW = cw).height
        // ios: Widgets/Views/HomeViews.swift:217,218,219
        val m = WL.t("이번 주 예측은 앱이나 사이트를 열면 만들어집니다"); val th = size(m, 12f, maxW = cw, maxLines = 6).height; text(m, 12f, x, y + lh + max(0f, bottom - 22 - y - lh - th) / 2, alpha = 0.75f, maxW = cw, maxLines = 6)
        // ios: Widgets/Views/HomeViews.swift:220
        stamp(s, x + cw, bottom - 11)
    }
}

/** 지난 8주 적중: 꽉 찬 점 = 맞힘, 빈 점 = 못 맞힘 (예측을 적기 전 주는 흐리게). (x, y) 왼쪽 위, 줄 높이 rh */
// ios: Widgets/Views/HomeViews.swift:227,228
private fun Pen.hits(s: Snapshot, t: Tint, x: Float, y: Float, rh: Float) { val hs = s.hits.takeLast(8)
    // ios: Widgets/Views/HomeViews.swift:229,236
    val cy = y + rh / 2; val sp = 4f; var cx = x
    // ios: Widgets/Views/HomeViews.swift:230
    val label = "${hs.count { it.hit }}/${hs.size}"; val lh = lineH(10f); cx += text(label, 10f, cx, cy - lh / 2, alpha = 0.7f).width + 2 + sp
    // ios: Widgets/Views/HomeViews.swift:231
    for (hh in hs) {
        // ios: Widgets/Views/HomeViews.swift:232
        if (hh.hit) dotH(cx + 9f / 2, cy, 9f, 9f, t.c(pal.good, if (hh.retro) 0.45f else 1f))
        // ios: Widgets/Views/HomeViews.swift:233
        else { val box = Size(9f, 9f); ringH(cx + box.width / 2, cy + box.height / 2 - 9f / 2, 7f, 7f, pal.ink.copy(alpha = if (hh.retro) 0.35f else 0.6f), 1f) }
        cx += 9f + sp
    }
}

// MARK: 오늘의 움직임 (비중 순 히트맵: 1위 가로 띠, 2위 아래 왼쪽 정사각형, 3위·나머지 오른쪽 가로 두 칸)
// ios: Widgets/Views/HomeViews.swift:241,242,243,244
fun Pen.movesSmall(s: Snapshot) = movesSmallView(s)
private fun Pen.movesSmallView(s: Snapshot) {
    // ios: Widgets/Views/HomeViews.swift:270
    ink = pal.ink
    // ios: Widgets/Views/HomeViews.swift:245
    val t = Tint(this); val ms = s.moves
    // ios: Widgets/Views/HomeViews.swift:246,247
    val x = pad; val cw = w - pad * 2; val y = pad; val bottom = h - pad; val sp = 6f
    // ios: Widgets/Views/HomeViews.swift:253
    val stW = stamp(s, x + cw, y + 11, measureOnly = true); stamp(s, x + cw, y + 11)
    // ios: Widgets/Views/HomeViews.swift:252
    val leftW = cw - stW - 2
    // ios: Widgets/Views/HomeViews.swift:248,249
    val sp1 = 1f; val lh = label2(WL.t("오늘의 움직임"), x, y, maxW = leftW).height
    // ios: Widgets/Views/HomeViews.swift:250
    val ch = flip(s.dayChg) { text("${Fmt.arrow(s.dayChg)}${Fmt.pct(s.dayChg)}", 12f, x, y + lh + sp1, W.heavy, t.chg(s.dayChg), maxW = leftW) }.height
    // ios: Widgets/Views/HomeViews.swift:255
    val gy = max(y + lh + sp1 + ch, y + 22) + sp; val gw = cw; val gh = bottom - gy
    // ios: Widgets/Views/HomeViews.swift:256,257
    val gap = 2f; val h1 = (gh - gap) * 0.46f; val h2 = gh - gap - h1
    // ios: Widgets/Views/HomeViews.swift:258
    tile(ms.firstOrNull(), t, row = true, big = true, x, gy, gw, h1)
    // ios: Widgets/Views/HomeViews.swift:259,260
    val y2 = gy + h1 + gap; tile(if (ms.size > 1) ms[1] else null, t, row = false, big = false, x, y2, h2, h2)
    // ios: Widgets/Views/HomeViews.swift:261,265
    val rx = x + h2 + gap; val rw = max(0f, gw - h2 - gap); val th = (h2 - gap) / 2
    // ios: Widgets/Views/HomeViews.swift:262
    tile(if (ms.size > 2) ms[2] else null, t, row = true, big = false, rx, y2, rw, th)
    // ios: Widgets/Views/HomeViews.swift:263
    tile(rest(ms), t, row = true, big = false, rx, y2 + th + gap, rw, th)
}

/** 넷째 칸: 남은 종목이 하나면 그 종목, 여럿이면 '기타 N개' (평가액 가중 등락) */
// ios: Widgets/Views/HomeViews.swift:274
private fun rest(ms: List<Snapshot.Move>): Snapshot.Move? {
    // ios: Widgets/Views/HomeViews.swift:275
    val r = ms.drop(3)
    // ios: Widgets/Views/HomeViews.swift:276
    if (r.size <= 1) return r.firstOrNull()
    // ios: Widgets/Views/HomeViews.swift:277
    val krw = r.fold(0.0) { a, m -> a + m.krw }
    // ios: Widgets/Views/HomeViews.swift:278
    val pct = if (krw > 0) r.fold(0.0) { a, m -> a + m.krw * m.pct } / krw else 0.0
    // ios: Widgets/Views/HomeViews.swift:279
    return Snapshot.Move(t = WL.t("기타 {n}개", mapOf("n" to "${r.size}")), pct = pct, krw = krw, w = 0.0)
}

/** 히트맵 칸: 많이 움직일수록 진하다. 오르면 빨강·내리면 파랑, 투명·틴트에서는 흰색 진하기와 ▲▼ 로 */
// ios: Widgets/Views/HomeViews.swift:283
private fun Pen.tile(m: Snapshot.Move?, t: Tint, row: Boolean, big: Boolean, x: Float, y: Float, ww: Float, hh: Float) {
    // ios: Widgets/Views/HomeViews.swift:284
    if (m != null) {
        // ios: Widgets/Views/HomeViews.swift:285
        val a = min(1.0, abs(m.pct) / 0.03).toFloat()
        // ios: Widgets/Views/HomeViews.swift:286
        val nfs = if (big) 13f else 11f; val nmaxLines = 1
        // HomeViews.swift:287 (검사기 한계: 보간 속 따옴표, 예외 목록 참고)
        val pct = "${Fmt.arrow(m.pct)}${cformat("%.1f", abs(m.pct) * 100)}%"; val pfs = if (big) 17f else 11f
        // ios: Widgets/Views/HomeViews.swift:288,289
        rrect(x, y, ww, hh, 4f, if (t.full) pal.heatBase else pal.ink.copy(alpha = 0.08f + a * 0.32f))
        // ios: Widgets/Views/HomeViews.swift:290
        if (t.full) rrect(x, y, ww, hh, 4f, (if (m.pct >= 0) pal.heatUp else pal.heatDn).copy(alpha = 0.15f + a * 0.85f))
        // ios: Widgets/Views/HomeViews.swift:291,295,296,298
        val ph = 6f; val pv = if (row) 0f else 5f; val fg = if (t.full && a > 0.4f) Color.White else pal.ink; val iw = max(0f, ww - ph * 2)
        // ios: Widgets/Views/HomeViews.swift:292
        if (row) { val hs = 4f; val minSp = 0f; val cy = y + hh / 2; val ps = size(pct, pfs, W.heavy, maxW = iw, minScale = 0.7f); flip(m.pct) { text(pct, pfs, x + ww - ph, cy - ps.height / 2, W.heavy, fg, maxW = iw, maxLines = 1, minScale = 0.7f, align = 1f) }
            val nh = lineH(nfs, W.heavy); text(m.t, nfs, x + ph, cy - nh / 2, W.heavy, fg, maxW = max(0f, iw - ps.width - hs - minSp), maxLines = nmaxLines) }
        // ios: Widgets/Views/HomeViews.swift:293
        else { val minSp = 0f; text(m.t, nfs, x + ph, y + pv, W.heavy, fg, maxW = iw, maxLines = nmaxLines); val ps = size(pct, pfs, W.heavy, maxW = iw, minScale = 0.7f); flip(m.pct) { text(pct, pfs, x + ph, y + hh - pv - ps.height - minSp, W.heavy, fg, maxW = iw, maxLines = 1, minScale = 0.7f) } }
    // ios: Widgets/Views/HomeViews.swift:299,300
    } else { rrect(x, y, ww, hh, 4f, pal.ink.copy(alpha = 0.06f)) }
}

/** 앱 위젯 목록 미리보기: 같은 그리기를 Compose 로 */
@Composable
fun HomeWidgetPreview(kind: String, s: Snapshot) = FaceCanvas {
    this.kind = kind; isSample = s.placeholder; widgetBG()
    when (kind) {
        "asset.small" -> assetSmall(s)
        "path.small" -> pathSmall(s)
        "future.small" -> futureSmall(s)
        "block.small" -> blockSmall(s)
        "pace.small" -> paceSmall(s)
        "target.small" -> targetSmall(s)
        "moves.small" -> movesSmall(s)
    }
}
