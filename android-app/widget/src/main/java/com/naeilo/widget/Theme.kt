package com.naeilo.widget

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.naeilo.app.core.Fresh
import com.naeilo.app.core.Store
import com.naeilo.app.core.WL

/** 기본(밝은)·다크 화면은 색으로, 투명·틴트(리퀴드 글라스)에서는 시스템이 단색으로 칠하므로 ▲▼ 기호·굵기·명암으로 구분한다 */
// ios: Widgets/Views/Theme.swift:5
class Palette private constructor(val dark: Boolean) {
    // ios: Widgets/Views/Theme.swift:6
    val up = dyn(0xff6b6b, 0xd63939)
    // ios: Widgets/Views/Theme.swift:7
    val dn = dyn(0x6c9cff, 0x2f6fed)
    // ios: Widgets/Views/Theme.swift:8
    val goal = dyn(0xc08cf0, 0x8e44ad)
    // ios: Widgets/Views/Theme.swift:9
    val good = dyn(0x4cc38a, 0x1f9d55)
    // ios: Widgets/Views/Theme.swift:10
    val acc = dyn(0x6c9cff, 0x2f6fed)
    /** 글자·선 기본색: 다크에선 흰색, 밝은 화면에선 짙은 남색 */
    // ios: Widgets/Views/Theme.swift:12
    val ink = dyn(0xffffff, 0x1b2230)
    // ios: Widgets/Views/Theme.swift:13
    val bgTop = dyn(0x20252f, 0xffffff)
    // ios: Widgets/Views/Theme.swift:14
    val bgBottom = dyn(0x15181f, 0xeef1f6)
    /** 내 길: 실제 선·앞섬 (앱 내 길 화면의 민트·청록), 계획선 보라, 뒤처짐·모의 바구니 주황 */
    // ios: Widgets/Views/Theme.swift:16
    val teal = dyn(0x5fd0c4, 0x2a9d93)
    // ios: Widgets/Views/Theme.swift:17
    val plan = dyn(0xb79cf0, 0x8e6bd8)
    // ios: Widgets/Views/Theme.swift:18
    val orange = dyn(0xe9a564, 0xd9873a)
    /** 목표일 전망 띠·중앙선 (분석 화면 목표일 전망과 같은 청록) */
    // ios: Widgets/Views/Theme.swift:20
    val band = dyn(0x2a9d93, 0x2a9d93)
    // ios: Widgets/Views/Theme.swift:21
    val bandLine = dyn(0x8fd8ce, 0x1f7f76)
    /** 오늘의 움직임 히트맵: 바탕 칸 위에 등락색을 진하기만큼 덮는다 */
    // ios: Widgets/Views/Theme.swift:23
    val heatBase = dyn(0x38425a, 0xdfe3ea)
    // ios: Widgets/Views/Theme.swift:24
    val heatUp = dyn(0xe5484d, 0xe5484d)
    // ios: Widgets/Views/Theme.swift:25
    val heatDn = dyn(0x3d63c9, 0x3d63c9)
    /** 밝은·다크 화면에서 같은 색 (과녁판 고리) */
    // ios: Widgets/Views/Theme.swift:27
    fun fixed(x: Long): Color = dyn(x, x)

    // ios: Widgets/Views/Theme.swift:29,30
    fun dyn(dark: Long, light: Long): Color = if (this.dark) rgb(dark) else rgb(light)

    companion object {
        private val darkP = Palette(true)
        private val lightP = Palette(false)
        fun of(dark: Boolean): Palette = if (dark) darkP else lightP
        // ios: Widgets/Views/Theme.swift:32,33
        fun rgb(x: Long): Color = Color(red = ((x shr 16) and 0xff) / 255f, green = ((x shr 8) and 0xff) / 255f, blue = (x and 0xff) / 255f, alpha = 1f)
    }
}

/** 이 붓의 색 묶음 (밝은·다크) */
val Pen.pal: Palette get() = Palette.of(dark)

/** 위젯 바탕 (밝은·다크 화면 따라감) */
// ios: Widgets/Views/Theme.swift:38,39
object WidgetBG { fun body(p: Pen) = p.rect(0f, 0f, p.w, p.h, Brush.linearGradient(listOf(p.pal.bgTop, p.pal.bgBottom), start = Offset(0f, 0f), end = Offset(p.w, p.h))) }

/** 투명·틴트에서는 시스템이 단색으로 칠하므로 글자를 늘 밝게(다크 기준) 둔다. 안드로이드는 늘 fullColor 라 바꿀 것이 없다 */
// ios: Widgets/Views/Theme.swift:43,44,45,46
fun Pen.schemeFix(): Boolean = mode == WidgetRenderingMode.fullColor

// ios: Widgets/Views/Theme.swift:50
class Tint(val p: Pen) {
    // ios: Widgets/Views/Theme.swift:51
    val mode: WidgetRenderingMode get() = p.mode
    // ios: Widgets/Views/Theme.swift:52
    val full: Boolean get() = mode == WidgetRenderingMode.fullColor
    // ios: Widgets/Views/Theme.swift:53
    fun c(color: Color, o: Float = 1f): Color = if (full) color.copy(alpha = color.alpha * o) else p.pal.ink.copy(alpha = o)
    // ios: Widgets/Views/Theme.swift:54
    fun chg(x: Double): Color = if (full) (if (x >= 0) p.pal.up else p.pal.dn) else p.pal.ink
}

/** 기본 위젯 바탕을 깔고 글자 기본색을 잡는다 (widgetBG) */
// ios: Widgets/Views/Theme.swift:57,58
fun Pen.widgetBG() { schemeFix(); WidgetBG.body(this); ink = pal.ink }

/** 작은 회색 제목. 그린 크기를 준다 */
// ios: Widgets/Views/Theme.swift:61,62,63
fun Pen.label2(text: String, x: Float, y: Float, maxW: Float = Float.POSITIVE_INFINITY, align: Float = 0f) =
    text(text, 12f, x, y, W.bold, pal.ink.copy(alpha = 0.62f), maxW = maxW, maxLines = 1, align = align)

/** 아래 줄: 앞말 + '3분 전 · 토스' + 다시 받기 버튼. (x, 아래 y) 부터 폭 ww. 높이 22 */
// ios: Widgets/Views/Theme.swift:66,67,68,69,70,71,72,73,74,75
fun Pen.foot(s: com.naeilo.app.core.Snapshot, x: Float, bottom: Float, ww: Float, text: String = ""): Float {
    val fresh = Fresh.label(s.pricedAt, s.feed, now = now)
    val hh = 22f
    val bw = reloadButton(x + ww, bottom - hh, measureOnly = true)
    reloadButton(x + ww - bw, bottom - hh)
    val sz = size(if (text.isEmpty()) fresh else "$text · $fresh", 11f, W.medium)
    this.text(if (text.isEmpty()) fresh else text + " · " + fresh, 11f, x, bottom - hh / 2 - sz.height / 2, W.medium, pal.ink.copy(alpha = 0.7f), maxW = kotlin.math.max(0f, ww - bw - 4), maxLines = 1)
    return hh
}

/** 갱신 시간 + ↻ (자리가 좁은 위젯의 머리·꼬리). 투명 바탕에서도 읽히게 글자를 너무 옅게 하지 않는다.
 *  (right, 가운데 cy) 에 오른쪽 맞춤으로 그리고 폭을 준다 */
// ios: Widgets/Views/Theme.swift:80,81,82,83,84,85,86,87,88
fun Pen.stamp(s: com.naeilo.app.core.Snapshot, right: Float, cy: Float, button: Boolean = true, time: Boolean = true, measureOnly: Boolean = false): Float {
    var x = right
    if (button) {
        val bw = reloadButton(x, cy - 11, measureOnly = true)
        if (!measureOnly) reloadButton(x - bw, cy - 11)
        x -= bw
    }
    if (time) {
        if (button) x -= 4
        val t = if (Store.fetchFailed) WL.t("받기 실패") else Fresh.ago(s.pricedAt, now = now)
        val sz = size(t, 10f, W.medium)
        if (!measureOnly) text(t, 10f, x - sz.width, cy - sz.height / 2, W.medium, pal.ink.copy(alpha = 0.7f), maxLines = 1)
        x -= sz.width
    }
    return right - x
}

/** ↻ 다시 받기 (예시 값이면 앞에 '예시' 배지). (x, y) 왼쪽 위, 높이 22. 폭을 준다 */
// ios: Widgets/Views/Theme.swift:93,94,95,96,97,98
fun Pen.reloadButton(x: Float, y: Float, measureOnly: Boolean = false): Float {
    var cx = x
    if (isSample) { cx += sampleBadge(cx, y + 3, measureOnly) + 4 }
    if (!measureOnly) reloadIcon(cx, y)
    return cx + 22 - x
}

/** ↻ 동그라미. 돌려주는 값: 읽는 이름 (받는 중·다시 받기) */
// ios: Widgets/Views/Theme.swift:102,103,104,105,106,108,110
private fun Pen.reloadIcon(x: Float, y: Float): String {
    val busy = Store.isReloading
    circle(x + 22f / 2, y + 22f / 2, 11f, pal.ink, a = if (busy) 0.3f else 0.14f)
    icon(themeSym(if (busy) "ellipsis" else "arrow.clockwise"), x + 11, y + 11, 13f, ink)
    // 앱의 미리보기에서는 그림만 (버튼 동작은 위젯에만 둔다): hit 은 앱에서 쌓지 않는다
    val onClick = Act.Reload; hit(x - 4, y - 4, 30f, 30f, onClick)
    return WL.t(if (busy) "받는 중" else "다시 받기")
}

/** SF Symbols 이름 → 위젯 기호 (tools/port_check/symbols.md) */
private fun themeSym(name: String): Icon = if (name == "ellipsis") Icon.more else Icon.refresh

/** 체험 모드·연결 전: 실제 값이 아님을 표시. (x, y) 왼쪽 위, 높이 16. 폭을 준다 */
// ios: Widgets/Views/Theme.swift:115,116,117,118
fun Pen.sampleBadge(x: Float, y: Float, measureOnly: Boolean = false): Float {
    val t = WL.t("예시")
    val sz = size(t, 10f, W.heavy)
    val bw = sz.width + 6 * 2
    if (!measureOnly) {
        capsule(x, y, bw, 16f, Color(0xFFFF9500))   // Color.orange
        text(t, 10f, x + 6, y + 8 - sz.height / 2, W.heavy, Color.White)
    }
    return bw
}

// MARK: 금액 숨기기
// 아이폰 환경값 widgetKind·isSample·entryDate 는 붓(Pen)의 kind·isSample·now
// ios: Widgets/Views/Theme.swift:124
object KindKey { const val defaultValue = "" }
// ios: Widgets/Views/Theme.swift:133
object EntryDateKey { val defaultValue: java.time.Instant get() = java.time.Instant.now() }
// ios: Widgets/Views/Theme.swift:134
object SampleKey { const val defaultValue = false }
// ios: Widgets/Views/Theme.swift:125,127,129,131
val Pen.widgetKind: String get() = kind

/** 숨긴 금액 자리 */
// ios: Widgets/Views/Theme.swift:137
const val hiddenAmount = "••••"

/** 큰 금액: 누르면 숨기고 한 번 더 누르면 보인다. label 이 그린 상자를 버튼 자리로 */
// ios: Widgets/Views/Theme.swift:140,141,142,143,147,149
fun Pen.money(label: (Boolean) -> androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect {
    // 잠금화면 위젯의 금액은 폰이 잠겨 있으면 시스템이 가린다: 안드로이드엔 잠금화면 위젯이 없다 (lockPrivate 는 lock. 위젯만)
    val r = label(Store.isHidden(kind))
    val onClick = Act.Toggle(kind); hit(r.left - 4, r.top - 4, r.width + 8, r.height + 8, onClick)
    return r
}

/** 그릴 위젯 종류를 붓에 둔다 (아이폰 .kind(k)) */
// ios: Widgets/Views/Theme.swift:154,155
fun Pen.kind(k: String): Pen { kind = k; return this }
