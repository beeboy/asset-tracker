package com.naeilo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.naeilo.app.core.Fmt
import com.naeilo.app.core.Fresh
import com.naeilo.app.core.Store
import com.naeilo.app.core.WChar
import com.naeilo.app.core.WL
import com.naeilo.app.core.WReward
import com.naeilo.app.core.WidgetUnlock
import com.naeilo.app.core.cformat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 인물 보상 위젯 그림 (아이폰 CharWidgets.swift 의 View 들). 모두 위젯 전체 좌표(0..w, 0..h)로 그린다.
// 기본은 인물 색 그라데이션 바탕, 다크 모드는 #1c1c1f 바탕에 인물 색은 빛·제목·막대·블록·말풍선 테두리에만.
// 위젯은 움직이지 못해서 ↻ 받는 중(Store.isReloading)에는 애니메이션이 끝난 모습 한 장을 그린다.

// MARK: 공통

// ios: Widgets/CharWidgets.swift:111,112
private fun rgb(x: Long): Color = Color(red = ((x shr 16) and 0xff) / 255f, green = ((x shr 8) and 0xff) / 255f, blue = (x and 0xff) / 255f)

/** Color(white:) */
private fun white(v: Float, a: Float = 1f): Color = Color(v, v, v, a)
/** .white.opacity(a) */
private fun wh(a: Float): Color = Color.White.copy(alpha = a)
/** 기호 글자 크기 → 그릴 칸 (Material 기호는 칸 안에 여백이 있다) */
private fun symSize(font: Float): Float = font * 1.2f
/** SF Symbols 이름 → 쓰는 기호 */
private fun sf(n: String): Icon = when (n) { "ellipsis" -> Icon.more; "arrow.clockwise" -> Icon.refresh; else -> Icon.lock }
/** 아이폰 .rounded(): 반올림 (0.5 는 0 에서 먼 쪽) */
private fun swRound(x: Double): Int = (if (x >= 0) floor(x + 0.5) else ceil(x - 0.5)).toInt()
/** #available(iOS 18, *): 안드로이드엔 판 구분이 없어 늘 참 */
private fun availableIOS(v: Int): Boolean = v > 0

/** 인물 색: 잠겨 있으면 회색 */
// ios: Widgets/CharWidgets.swift:116,117,118,119
class CharStyle(val c: WChar, val locked: Boolean, val dark: Boolean) {
    // ios: Widgets/CharWidgets.swift:120
    val accent: Color get() = if (locked) white(0.55f) else rgb(c.accent)
    /** 제목 색: 다크에선 인물 색, 기본에선 흰색 조금 흐리게 */
    // ios: Widgets/CharWidgets.swift:122
    val heading: Color get() = if (dark) accent else wh(0.78f)
    // ios: Widgets/CharWidgets.swift:123
    val bubbleFill: Color get() = if (dark) Color.Transparent else wh(0.14f)
    // ios: Widgets/CharWidgets.swift:124
    val bubbleStroke: Color get() = if (dark) accent.copy(alpha = accent.alpha * 0.7f) else Color.Transparent
}

/** 인물 위젯 틀: 7일 무료 뒤 잠금은 다른 위젯과 같은 Gate 로. 잠긴 모습만 인물 바탕을 지킨다 */
// ios: Widgets/CharWidgets.swift:127,128,129,130,131,132,134
fun Pen.charFrame(e: CharEntry, kind: String, open: Pen.() -> Unit) {
    val g = Gate(kind = kind, preview = e.preview, content = { charOpen(e, kind, open) }, locked = { charClosed(e, kind) })
    g.body(this)
}

/** 누르면 갈 주소: 열렸으면 못 만난 인물은 쉼터, 아니면 안내. 잠겼으면 Gate 와 같이 (단계 잠금 → 앱, 구독 잠금 → 구독) */
// ios: Widgets/CharWidgets.swift:160
fun charURL(e: CharEntry, kind: String): String =
    if (e.preview || Store.isUnlocked(kind)) (if (e.locked) "naeilo://shelter" else "naeilo://guide")
    else if (Store.widgetsOpen) Catalog.openURL else Catalog.subscribeURL

// ios: Widgets/CharWidgets.swift:136,137,138,147,148,151
private fun Pen.charClosed(e: CharEntry, kind: String) {
    charBG(e.c, locked = true, dark = dark)
    ink = Color.White
    val x0 = pad; val maxW = w - pad * 2; val gap = 6f; val hGap = 6f
    // ios: Widgets/CharWidgets.swift:139,140
    val isz = symSize(12f)
    val name = Catalog.name(kind)
    val th = size(name, 13f, W.bold).height
    icon(sf("lock.fill"), x0 + isz / 2, pad + th / 2, isz, ink)
    text(name, 13f, x0 + isz + hGap, pad, W.bold, maxW = maxW - isz - hGap, maxLines = 1, minScale = 0.8f)
    // ios: Widgets/CharWidgets.swift:142,143,144,145
    val how = WidgetUnlock.how(kind)
    val cta = WidgetUnlock.cta
    val hsz = size(how, 11f, W.semibold, maxW = maxW, maxLines = 3)
    val csz = size(cta, 10f, W.bold)
    val spacer = max(0f, (h - pad * 2) - th - gap - gap - hsz.height - gap - csz.height)   // Spacer(minLength: 0): 남는 자리
    var y = pad + th + gap + spacer + gap
    text(how, 11f, x0, y, W.semibold, maxW = maxW, maxLines = 3, alpha = 0.75f)
    y += hsz.height + gap
    text(cta, 10f, x0, y, W.bold, maxW = maxW, alpha = 0.5f)
}

/** 잠긴 인물 위젯을 읽는 문장 */
// ios: Widgets/CharWidgets.swift:149,150
fun charLockedSpeech(kind: String): String = WL.t("{name} 잠김. {how}", mapOf("name" to Catalog.name(kind), "how" to WidgetUnlock.how(kind)))

// 열린 모습: 글자는 늘 흰색(colorScheme .dark), 기본·다크 구분(charDark)은 Pen.dark 그대로, charKind 는 Pen.kind
// ios: Widgets/CharWidgets.swift:153,154,155,156,157,158,159
private fun Pen.charOpen(e: CharEntry, kind: String, open: Pen.() -> Unit) {
    charBG(e.c, locked = e.locked, dark = dark)
    this.kind = kind
    ink = Color.White
    open()
}

/** 인물 위젯 바탕 (위젯 전체) */
// ios: Widgets/CharWidgets.swift:164,165,166,167
fun Pen.charBG(c: WChar, locked: Boolean, dark: Boolean) {
    if (dark) {
        // ios: Widgets/CharWidgets.swift:168,169
        rect(0f, 0f, w, h, Color(red = 0x1c / 255f, green = 0x1c / 255f, blue = 0x1f / 255f))
        // ios: Widgets/CharWidgets.swift:170
        val g = (if (locked) Color(0xFF8E8E93) else rgb(c.accent)).copy(alpha = 0.22f)
        rect(0f, 0f, w, h, Brush.radialGradient(listOf(g, Color.Transparent), center = Offset(0f, h), radius = 220f))
    // ios: Widgets/CharWidgets.swift:172,173
    } else if (locked) {
        rect(0f, 0f, w, h, Brush.linearGradient(listOf(white(0.16f), white(0.30f)), start = Offset(0f, 0f), end = Offset(w, h)))
    // ios: Widgets/CharWidgets.swift:174,175
    } else {
        rect(0f, 0f, w, h, Brush.linearGradient(listOf(rgb(c.g0), rgb(c.g1)), start = Offset(0f, 0f), end = Offset(w, h)))
    }
}

// 그림 묶음을 옮기거나 키워 그린다 (lambda 안의 this 는 Pen)
private inline fun Pen.scaled(sx: Float, sy: Float, pivot: Offset, crossinline block: () -> Unit) = d.scale(sx, sy, pivot) { block() }
private inline fun Pen.rotated(deg: Float, pivot: Offset, crossinline block: () -> Unit) = d.rotate(deg, pivot) { block() }
private inline fun Pen.moved(dx: Float, dy: Float, crossinline block: () -> Unit) = d.translate(dx, dy) { block() }
private inline fun Pen.clipped(p: Path, crossinline block: () -> Unit) = d.clipPath(p) { block() }

/** 도트 그림: 최근접 확대. 못 만난 인물은 실루엣. (x, 아래 bottom) 에 높이 height, align 0 왼쪽 · 0.5 가운데 · 1 오른쪽이 x */
// ios: Widgets/CharWidgets.swift:181,182,183,184,186,187,188,189
fun Pen.charSprite(name: String, locked: Boolean, height: Float, x: Float, bottom: Float, shake: Boolean = true, align: Float = 0f) {
    val busy = Store.isReloading
    // ios: Widgets/CharWidgets.swift:191
    val dot = if (name.contains("_pose_")) 64.0f / 34 else height / 34
    // ios: Widgets/CharWidgets.swift:211,228,229,230
    val img = image(name)
    val sw = if (img == null || img.height == 0) 0f else height * img.width / img.height
    val left = x - sw * align
    val cx = left + sw / 2
    // 발밑 그림자 (뛰면 작아졌다 커진다). 잠김·틴트 화면은 한 색 덩어리가 되니 뺀다
    // ios: Widgets/CharWidgets.swift:200,202,203,204,205,207
    if (!locked && mode != WidgetRenderingMode.accented) {
        val k = if (busy) 0.7f else 1f
        val ew = dot * 16 * k
        val eh = dot * 5 * k
        val ecy = bottom - dot * 5 / 2 + dot * 2
        oval(cx - ew / 2, ecy - eh / 2, ew, eh, Color.Black, a = 0.22f)
    }
    if (img == null) return
    // 갱신 순간: 폴짝 늘어난 모습 (스프링으로 내려앉는 움직임은 위젯이 못 한다)
    // ios: Widgets/CharWidgets.swift:197,198
    moved(0f, if (busy) -3f else 0f) {
        scaled(if (busy) 0.94f else 1f, if (busy) 1.08f else 1f, Offset(cx, bottom)) {
            // 포즈 그림이 없어 기본 그림으로 떨어진 인물은 동작이 안 바뀌니, 갱신 때 흔들어 움직임을 보인다
            // ios: Widgets/CharWidgets.swift:192,194
            if (shake || !name.contains("_pose_")) charShake(cx, bottom) { spriteImg(name, img, locked, left, bottom - height, sw, height) }
            else spriteImg(name, img, locked, left, bottom - height, sw, height)
        }
    }
}

/** 인물 흔들기 (Flip.swift charShake): 받는 동안 움츠렸다 뛰어올라 왼쪽으로 기운 모습 */
private inline fun Pen.charShake(cx: Float, bottom: Float, crossinline block: () -> Unit) {
    val busy = Store.isReloading
    moved(if (busy) -5f else 0f, if (busy) -12f else 0f) {
        rotated(if (busy) -9f else 0f, Offset(cx, bottom)) {
            scaled(if (busy) 1.06f else 1f, if (busy) 0.92f else 1f, Offset(cx, bottom)) { block() }
        }
    }
}

// 내 캐릭터(w_me…)는 앱이 겹쳐 그린 그림 (동작별 me_pose_*.png, 없으면 me.png): Sprites 가 WMe.image 로 읽는다.
// 투명·틴트(accented)에서는 원래 색 그대로 (widgetAccentedRenderingMode(.fullColor)) = 기본과 같은 그리기
// ios: Widgets/CharWidgets.swift:212,214,215,216,218,219,221,222,225,226
private fun Pen.spriteImg(name: String, img: androidx.compose.ui.graphics.ImageBitmap, locked: Boolean, x: Float, y: Float, ww: Float, hh: Float) {
    if (name.startsWith("w_me")) {
        if (locked) pixels(img, x, y, ww, hh, tint = wh(0.22f))
        else if (mode == WidgetRenderingMode.accented && availableIOS(18)) pixels(img, x, y, ww, hh)
        else pixels(img, x, y, ww, hh)
    } else if (locked) pixels(img, x, y, ww, hh, tint = wh(0.22f))
    else if (mode == WidgetRenderingMode.accented && availableIOS(18)) pixels(img, x, y, ww, hh)
    else pixels(img, x, y, ww, hh)
}

// ios: Widgets/CharWidgets.swift:245
private fun lockLine(c: WChar): String = WL.t("인터미션 {w}주차에 만나요", mapOf("w" to "${c.week}"))

/** 1억 2,340만 */
// ios: Widgets/CharWidgets.swift:248,249,250,251,252
private fun eokMan(v: Double): String {
    if (WL.en) return Fmt.krwC(abs(v))
    val man = swRound(abs(v) / 1e4); val eok = man / 10000; val rest = man % 10000
    if (eok == 0) return Fmt.comma(man) + "만"
    return "${eok}억" + (if (rest > 0) " ${Fmt.comma(rest)}만" else "")
}
// ios: Widgets/CharWidgets.swift:254
private val upC = Color(red = 1f, green = 0.42f, blue = 0.42f)
// ios: Widgets/CharWidgets.swift:255
private val dnC = Color(red = 0.42f, green = 0.61f, blue = 1f)
// ios: Widgets/CharWidgets.swift:256
private fun pctText(c: Double): String = Fmt.arrow(c) + cformat("%.1f%%", abs(c) * 100)

/** 동작 그림 w_<인물>_<동작> 이 있으면 그것, 없으면 fallback 그림 */
// ios: Widgets/CharWidgets.swift:260,261,262,263
private fun Pen.poseOr(c: WChar, p: String, fallback: String): String {
    val n = "w_${c.id}_$p"
    if (c.id == "me") return if (Store.url("me_$p.png").exists()) n else fallback
    return if (Sprites.has(ctx, n)) n else fallback
}
/** 확정 포즈는 28×34 그림을 64pt로 그릴 때와 같은 배율(1px = 64/34pt), 발은 아래 맞춤. 그 밖은 64pt */
// ios: Widgets/CharWidgets.swift:266,267,268
private fun Pen.poseH(name: String): Float {
    if (!name.contains("_pose_") || !Sprites.has(ctx, name)) return 64f
    val i = image(name) ?: return 64f
    return i.height * 64f / 34
}
// ios: Widgets/CharWidgets.swift:270,271,272,273
private fun Pen.poseOr(c: WChar, p: String): String {
    val n = "w_${c.id}_$p"
    if (c.id == "me") return if (Store.url("me_$p.png").exists()) n else "w_${c.id}"
    return if (Sprites.has(ctx, n)) n else "w_${c.id}"
}

/** 아이폰 Alignment: h·v 는 -1 앞(왼쪽·위) · 0 가운데 · 1 뒤(오른쪽·아래) */
private enum class Al(val h: Int, val v: Int) {
    topLeading(-1, -1), top(0, -1), topTrailing(1, -1),
    leading(-1, 0), center(0, 0), trailing(1, 0),
    bottomLeading(-1, 1), bottom(0, 1), bottomTrailing(1, 1),
}

/** 시안처럼 위젯 가장자리부터 그리는 그림 (Canvas 좌표 = 위젯 좌표): 콘텐츠 자리 여백을 되돌린다 */
// ios: Widgets/CharWidgets.swift:276,278
private inline fun Pen.charBleed(crossinline block: () -> Unit) = moved(pad - 16f, pad - 16f) { block() }

/** 갱신 때 인물이 dy 만큼 톡 움직인 모습 (통통 돌아오는 움직임은 위젯이 못 한다) */
// ios: Widgets/CharWidgets.swift:280,281
private fun charHop(dy: Float): Float {
    val busy = Store.isReloading
    return if (busy) dy else 0f
}

/** 시안 좌표(위젯 가장자리부터 pt)로 놓는다: 콘텐츠 자리(여백 16) 안에 맞춘 뒤 옮긴다. 크기 vw×vh 인 그림의 왼쪽 위 */
// ios: Widgets/CharWidgets.swift:285,286,287,288
private fun Pen.charPin(a: Al, vw: Float, vh: Float, x: Float = 16f, y: Float = 16f): Offset {
    val ox: Float = if (a.h < 0) x - 16 else if (a.h > 0) 16 - x else 0f
    val oy: Float = if (a.v < 0) y - 16 else if (a.v > 0) 16 - y else 0f
    val bx = if (a.h < 0) pad else if (a.h > 0) w - pad - vw else (w - vw) / 2
    val by = if (a.v < 0) pad else if (a.v > 0) h - pad - vh else (h - vh) / 2
    return Offset(bx + ox, by + oy)
}

/** 인물 그림을 charPin 자리에 (발이 아래) */
private fun Pen.pinSprite(name: String, locked: Boolean, a: Al, x: Float = 16f, y: Float = 16f, dx: Float = 0f, dy: Float = 0f) {
    val hh = poseH(name)
    val o = charPin(a, 0f, hh, x, y)
    charSprite(name, locked, hh, o.x + dx, o.y + hh + dy, shake = false, align = (a.h + 1) / 2f)
}

/** '3분 전': 지금 값의 시세를 받은 때 (갤러리 미리보기처럼 모르면 안 보인다). right = 오른쪽 끝 */
// ios: Widgets/CharWidgets.swift:293,294,295,296
private fun Pen.charAgo(e: CharEntry, right: Float, top: Float) {
    val at = e.at ?: return
    text(Fresh.ago(at, now = e.date), 9f, right, top, W.medium, maxLines = 1, align = 1f, alpha = 0.55f)
}

/** 새 디자인(B) 오른쪽 위: '3분 전'과 ↻ */
// ios: Widgets/CharWidgets.swift:314,315,316,317
private fun Pen.cornerB(e: CharEntry) {
    if (!e.locked) {
        charAgo(e, right = w - 38f, top = 17f)
        charReload(left = w - 12f - 20f, top = 12f)
    }
}

/** 못 만난 인물: 자물쇠와 만나는 주차 */
// ios: Widgets/CharWidgets.swift:322,323,324,325,326
private fun Pen.meetLater(c: WChar, x: Float, y: Float) {
    val isz = symSize(22f)
    icon(sf("lock.fill"), x + isz / 2, y + isz / 2, isz, wh(0.7f))
    text(WL.t("인터미션\n{w}주차에 만나요", mapOf("w" to "${c.week}")), 12f, x, y + isz + 4, W.semibold, wh(0.8f), maxW = w - x - pad, maxLines = 4)
}

// ios: Widgets/CharWidgets.swift:330,331,332
private fun speakMoney(e: CharEntry, kind: String): String =
    if (e.locked) "${e.c.title}, ${lockLine(e.c)}"
    else "${Catalog.name(kind)}. ${e.c.title}, " + WL.t("총자산 {v}, 오늘 {p} {dir}", mapOf("v" to (if (Store.isHidden(kind)) WL.t("숨김") else eokMan(e.r.total)), "p" to cformat("%.1f%%", abs(e.r.dayChg) * 100), "dir" to WL.dir(e.r.dayChg)))
// ios: Widgets/CharWidgets.swift:334,335,336
private fun speakRecover(e: CharEntry, kind: String): String =
    if (e.locked) "${e.c.title}, ${lockLine(e.c)}"
    else "${Catalog.name(kind)}. ${e.c.title}, " + WL.t("{key}까지 {p}%, 남은 금액 {v}", mapOf("key" to WL.t(e.r.keyName), "p" to "${swRound(e.r.pct * 100)}", "v" to (if (Store.isHidden(kind)) WL.t("숨김") else eokMan(e.r.remain))))
// ios: Widgets/CharWidgets.swift:338,339,340
private fun speakBlock(e: CharEntry, kind: String): String =
    if (e.locked) "${e.c.title}, ${lockLine(e.c)}"
    else "${Catalog.name(kind)}. ${e.c.title}, " + WL.t("1000칸 중 {n}칸, 어제보다 {d}칸", mapOf("n" to "${e.r.cells}", "d" to (if (e.r.cells >= e.r.cellsYesterday) "+" else "") + "${e.r.cells - e.r.cellsYesterday}"))

/** 위젯 하나를 읽는 한 문장 (TalkBack) */
fun charSpeech(kind: String, e: CharEntry): String = charActions(kind, e).label

/** 읽는 문장 + 따로 고를 수 있는 동작 (↻·금액 숨기기). 앱 미리보기(inApp)면 문장만 */
fun charActions(kind: String, e: CharEntry, inApp: Boolean = false): CharSpeak = when (kind) {
    // ios: Widgets/CharWidgets.swift:370
    "char.hill" -> charSpeak(speakMoney(e, "char.hill"), kind = "char.hill", money = !e.locked, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:437
    "char.ladder" -> charSpeak(speakRecover(e, "char.ladder"), kind = "char.ladder", money = !e.locked, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:482
    "char.tower" -> charSpeak(speakBlock(e, "char.tower"), kind = "char.tower", money = false, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:539
    "char.board" -> charSpeak(speakMoney(e, "char.board"), kind = "char.board", money = !e.locked, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:592
    "char.height" -> charSpeak(speakMoney(e, "char.height"), kind = "char.height", money = !e.locked, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:675
    "char.puzzle" -> charSpeak(speakRecover(e, "char.puzzle"), kind = "char.puzzle", money = !e.locked, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:762
    "char.garden" -> charSpeak(speakBlock(e, "char.garden"), kind = "char.garden", money = false, reload = !e.locked, inApp = inApp)
    // ios: Widgets/CharWidgets.swift:856
    "char.tug" -> charSpeak(speakMoney(e, "char.tug"), kind = "char.tug", money = !e.locked, reload = !e.locked, inApp = inApp)
    else -> charSpeak(speakMoney(e, kind), kind = kind, money = !e.locked, reload = !e.locked, inApp = inApp)
}

// MARK: 시안 B ① 자산 추이 · 픽셀 언덕: 최근 10일을 기둥 10개로, 인물은 오른쪽 아래

// ios: Widgets/CharWidgets.swift:345
@Composable fun HillCharView(e: CharEntry) {
    val hide = LocalCharHide.current
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.hill"; ink = Color.White; hillChar(e, hide = hide) }
}

// ios: Widgets/CharWidgets.swift:346,347,348,349,350,351
fun Pen.hillChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    val sp = (r.spark ?: emptyList()).takeLast(10)
    // ios: Widgets/CharWidgets.swift:352,353,361
    val tx = 16f; val ty = 36f
    if (e.locked) meetLater(e.c, tx, ty) else {
        // ios: Widgets/CharWidgets.swift:354,355
        val mw = w - pad * 2
        val m = charMoney(hide) { hd -> fade { val z = text(if (hd) charHidden else eokMan(r.total), 24f, tx, ty, maxW = mw, maxLines = 1, minScale = 0.6f); Rect(tx, ty, tx + z.width, ty + z.height) } }
        val y = m.bottom + 0f
        // ios: Widgets/CharWidgets.swift:356,357
        fade { text(pctText(r.dayChg) + " " + WL.t("오늘"), 11f, tx, y, W.semibold, if (r.dayChg >= 0) upC else dnC, maxW = mw) }
    }
    // ios: Widgets/CharWidgets.swift:362
    if (!e.locked && sp.size >= 2) hill(sp, s.accent, charPin(Al.bottomLeading, 0f, 0f).x, h - 16f)
    // ios: Widgets/CharWidgets.swift:363
    val lp = charPin(Al.bottomLeading, w - pad * 2, 1f, y = 15f); rect(lp.x, lp.y, w - pad * 2, 1f, wh(0.35f))
    // 오른 날 trend_up, 내린 날 trend_down, 갱신 중엔 trend_up 으로 1pt 뛴다
    // ios: Widgets/CharWidgets.swift:365,366
    val hn = poseOr(e.c, if (!Store.isReloading && r.dayChg < 0) "pose_trend_down" else "pose_trend_up")
    pinSprite(hn, e.locked, Al.bottomTrailing, dy = charHop(-1f))
    // ios: Widgets/CharWidgets.swift:367
    cornerB(e)
}

/** 기둥 10개: 가장 낮은 날 8pt ~ 가장 높은 날 30pt, 오래된 날일수록 옅게, 오늘만 진하게. (left, 아래 bottom) */
// ios: Widgets/CharWidgets.swift:374,375,376,377,378,379,380,383
private fun Pen.hill(v: List<Double>, c: Color, left: Float, bottom: Float) {
    val lo = v.minOrNull() ?: 0.0; val hi = v.maxOrNull() ?: 1.0; val span = max(hi - lo, 1e-9)
    fade {
        var x = left
        for ((i, xv) in v.withIndex()) {
            val bh = 8 + 22 * ((xv - lo) / span).toFloat()
            rrect(x, bottom - bh, 7.5f, bh, 1f, c, a = if (i == v.size - 1) 1f else 0.25f + 0.04f * i)
            x += 7.5f + 1.5f
        }
    }
}

// MARK: 시안 B ② 본전 진행 · 사다리: 오른쪽 사다리가 차오르고 꼭대기에 깃발, 인물이 옆에서 오른다

// ios: Widgets/CharWidgets.swift:389
@Composable fun LadderCharView(e: CharEntry) {
    val hide = LocalCharHide.current
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.ladder"; ink = Color.White; ladderChar(e, hide = hide) }
}

// ios: Widgets/CharWidgets.swift:390,391,392,393,394,395
fun Pen.ladderChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    val p = (if (e.locked) 0.0 else min(1.0, max(0.0, r.pct))).toFloat()
    // ios: Widgets/CharWidgets.swift:396,397,408,409
    fade {
        val o = charPin(Al.bottomTrailing, 12f, 16f + 0f + 96f, x = 18f)
        val fs = flag(s.accent, o.x - 4, o.y)
        // ios: Widgets/CharWidgets.swift:398,399,400,406
        val ly = o.y + fs.height
        rrect(o.x, ly, 12f, 96f, 2f, wh(0.12f))
        rrect(o.x, ly + 96f - 96 * p, 12f, 96 * p, 2f, s.accent)
        // ios: Widgets/CharWidgets.swift:401,402,403
        val gap = max(0f, (96f - 9) / 10)
        for (k in 1 until 10) rect(o.x, ly + gap * k + 1 * (k - 1), 12f, 1f, wh(0.25f))
    }
    // 오른 날 push_up, 내린 날 push_down, 갱신 중엔 push_up 으로 1pt 앞으로
    // ios: Widgets/CharWidgets.swift:411
    val ln = poseOr(e.c, if (!Store.isReloading && r.pct < r.pctYesterday) "pose_push_down" else "pose_push_up", fallback = "w_${e.c.id}_raise")
    // ios: Widgets/CharWidgets.swift:412,414
    pinSprite(ln, e.locked, Al.bottomTrailing, x = 31.5f, dx = if (Store.isReloading) 1f else 0f)
    // ios: Widgets/CharWidgets.swift:415,416,423
    val tx = 16f; val ty = 36f
    if (e.locked) meetLater(e.c, tx, ty) else {
        // ios: Widgets/CharWidgets.swift:417,418,419
        val mw = w - pad * 2
        val z = fade { text("${swRound(r.pct * 100)}%", 34f, tx, ty, maxW = mw, maxLines = 1, minScale = 0.6f) }
        text(WL.t("{key}까지", mapOf("key" to WL.t(r.keyName))), 10f, tx, ty + z.height + 4, W.semibold, alpha = 0.6f)
    }
    // ios: Widgets/CharWidgets.swift:424,425,426,432
    if (!e.locked) {
        val d = (r.pct - r.pctYesterday) * 100
        val x = 16f; val y = 118f; val gap = 2f
        // ios: Widgets/CharWidgets.swift:427
        val m = charMoney(hide) { hd -> fade { val z = text(WL.t("남은 {v}", mapOf("v" to (if (hd) charHidden else eokMan(r.remain)))), 11f, x, y, maxLines = 1); Rect(x, y, x + z.width, y + z.height) } }
        // ios: Widgets/CharWidgets.swift:428,429,430
        text(listOf(Seg(WL.t("어제 "), 10f, color = wh(0.75f)),
            Seg((if (d >= 0) "+" else "") + cformat("%.1f%%p", d), 10f, color = if (d >= 0) upC else dnC)), x, m.bottom + gap, maxLines = 1)
    }
    // ios: Widgets/CharWidgets.swift:434
    cornerB(e)
}

/** 깃대(흰색 2pt) + 인물 색 깃발. 그린 틀 크기를 준다 */
// ios: Widgets/CharWidgets.swift:441,442,443,444,446
private fun Pen.flag(c: Color, x: Float, y: Float): Size {
    rect(x + 9, y, 2f, 16f, Color.White)
    rect(x, y, 9f, 6f, c)
    return Size(12f, 16f)
}

// MARK: 시안 B ③ 블록 · 탑 쌓기: 100칸이 한 층, 10층 탑. 쉼터 고양이가 왼쪽 아래에

// ios: Widgets/CharWidgets.swift:452
@Composable fun TowerCharView(e: CharEntry) {
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.tower"; ink = Color.White; towerChar(e) }
}

// ios: Widgets/CharWidgets.swift:453,454,455,456,457,458,459
@Suppress("UNUSED_PARAMETER")
fun Pen.towerChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    val floor = min(10, r.cells / 100 + 1); val fill = if (r.cells >= 1000) 100 else r.cells % 100; val d = r.cells - r.cellsYesterday
    // ios: Widgets/CharWidgets.swift:460,461,471
    val tx = 16f; val ty = 16f
    if (e.locked) meetLater(e.c, tx, ty) else {
        // ios: Widgets/CharWidgets.swift:462,463,464
        val gap = 5f
        val z = fade { text(listOf(Seg("$floor", 30f), Seg(" " + WL.t("층"), 13f, W.semibold, wh(0.8f))), tx, ty, maxLines = 1) }
        // ios: Widgets/CharWidgets.swift:465,466,467
        text(listOf(Seg("$fill/100", 10f), Seg(WL.t(" · 어제 "), 10f, color = wh(0.75f)),
            Seg((if (d >= 0) "+" else "") + "$d", 10f, color = if (d >= 0) upC else dnC)), tx, ty + z.height + gap, maxLines = 1)
    }
    // ios: Widgets/CharWidgets.swift:472
    val tw = 56f; val th = 10 * 9f + 9 * 1.5f
    val to = charPin(Al.bottomTrailing, tw, th, y = 17.5f); tower(e, floor, fill, s.accent, to.x, to.y, tw)
    // 칸이 는 날 block_up, 준 날 block_down, 갱신 중엔 block_refresh2 (블록을 탑 위에)
    // ios: Widgets/CharWidgets.swift:474
    val tn = poseOr(e.c, if (Store.isReloading) "pose_block_refresh2" else if (d < 0) "pose_block_down" else "pose_block_up", fallback = "w_${e.c.id}_spread")
    // ios: Widgets/CharWidgets.swift:475
    pinSprite(tn, e.locked, Al.bottom, dx = -16f)   // 탑과 키 재기 기둥 간격만큼 띄움
    // ios: Widgets/CharWidgets.swift:476,477
    if (!e.locked && mode != WidgetRenderingMode.accented) {
        val cat = image("w_cat"); val co = charPin(Al.bottomLeading, 16f, 16f, x = 20f)
        if (cat != null) pixels(cat, co.x, co.y, 16f, 16f)
    }
    // ios: Widgets/CharWidgets.swift:479
    cornerB(e)
}

/** 다 쌓은 층은 진하게, 지금 층은 10칸(한 칸 = 10칸), 남은 층은 점선 */
// ios: Widgets/CharWidgets.swift:486,487,488,503
private fun Pen.tower(e: CharEntry, floor: Int, fill: Int, c: Color, x: Float, y: Float, tw: Float) = fade {
    var fy = y; val sp = 1.5f
    for (f in (0 until 10).reversed()) {
        // ios: Widgets/CharWidgets.swift:489,490,500
        val done = !e.locked && f < floor - 1; val cur = !e.locked && f == floor - 1
        val fh = 9f
        // ios: Widgets/CharWidgets.swift:491
        if (done) rrect(x, fy, tw, fh, 1.5f, c)
        // ios: Widgets/CharWidgets.swift:492,493,494
        else if (cur) {
            val gap = 1.1f; val cw = (tw - gap * 9) / 10
            for (k in 0 until 10) rrect(x + k * (cw + gap), fy, cw, fh, 1f, if (k < fill / 10) c else wh(0.15f))
        // ios: Widgets/CharWidgets.swift:496,497
        } else {
            rrectBorder(x, fy, tw, fh, 1.5f, wh(0.22f), 1f, dash = floatArrayOf(2f, 2f))
        }
        fy += fh + sp
    }
}

// MARK: 시안 B ④ 오늘의 움직임 · 전광판: 총자산·다음 일정, 아래에 종목 2줄 + '외 n개' 줄, 인물은 오른쪽 아래

// ios: Widgets/CharWidgets.swift:509
@Composable fun BoardCharView(e: CharEntry) {
    val hide = LocalCharHide.current
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.board"; ink = Color.White; boardChar(e, hide = hide) }
}

// ios: Widgets/CharWidgets.swift:510,511,512,513,514,515,516,529
fun Pen.boardChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    val x = 16f; val mw = w - pad * 2
    var y = 16f + 0f
    // ios: Widgets/CharWidgets.swift:517
    y += text(WL.t("오늘의 움직임"), 12f, x, y, W.semibold, s.heading, maxW = mw, maxLines = 1).height + 12f
    // ios: Widgets/CharWidgets.swift:518,519,520
    if (e.locked) {
        text(lockLine(e.c), 11f, x, y, W.semibold, wh(0.8f), maxW = 84f, maxLines = 4)
    // ios: Widgets/CharWidgets.swift:521,522
    } else {
        val m = charMoney(hide) { hd -> fade { val z = text(if (hd) charHidden else eokMan(r.total), 15f, x, y, maxW = mw, maxLines = 1, minScale = 0.7f); Rect(x, y, x + z.width, y + z.height) } }
        y = m.bottom
        // ios: Widgets/CharWidgets.swift:523,524
        y += fade { text(pctText(r.dayChg) + " " + WL.t("오늘"), 11f, x, y, W.semibold, if (r.dayChg >= 0) upC else dnC, maxW = mw, maxLines = 1) }.height
        // ios: Widgets/CharWidgets.swift:525,526
        text(WL.t("다음: ") + (r.next ?: WL.t("예정된 일정 없음")), 9f, x, y + 3f, W.medium, wh(0.8f), maxW = mw, maxLines = 1)
    }
    // 오른 날 point_up, 내린 날 point_down, 갱신 중엔 point_refresh1 → point_up → point_refresh3
    // ios: Widgets/CharWidgets.swift:531
    val spin = listOf("pose_point_refresh1", "pose_point_up", "pose_point_refresh3")[e.frame % 3]
    // ios: Widgets/CharWidgets.swift:532
    val bn = poseOr(e.c, if (Store.isReloading) spin else if (r.dayChg < 0) "pose_point_down" else "pose_point_up", fallback = poseOr(e.c, "pose_point_up"))
    // ios: Widgets/CharWidgets.swift:533,534
    pinSprite(bn, e.locked, Al.bottomTrailing, x = 6f)
    // ios: Widgets/CharWidgets.swift:535
    if (!e.locked && r.tiles.isNotEmpty()) rows(r, 92f, y = 18f)
    // ios: Widgets/CharWidgets.swift:536
    cornerB(e)
}

// ios: Widgets/CharWidgets.swift:542,543,544,545,546,550
private fun Pen.rows(r: WReward, rw: Float, y: Float) = fade {
    val all = r.tiles.sortedByDescending { it.w }
    val top = all.take(2); val rest = all.drop(2)
    val ww = rest.fold(0.0) { a, t -> a + t.w }; val rc = if (ww > 0) rest.fold(0.0) { a, t -> a + t.w * t.c } / ww else 0.0
    val n = top.size + (if (rest.isEmpty()) 0 else 1); val gap = 2f
    val o = charPin(Al.bottomLeading, rw, n * 14f + (n - 1) * gap, y = y)
    var ry = o.y
    // ios: Widgets/CharWidgets.swift:547
    for (t in top) { row(t.t, t.c, o.x, ry, rw); ry += 14f + gap }
    // ios: Widgets/CharWidgets.swift:548
    if (rest.isNotEmpty()) row(WL.t("외 {n}개", mapOf("n" to "${rest.size}")), rc, o.x, ry, rw)
}

/** 한 줄: 점 · 이름(길면 말줄임) · 등락 */
// ios: Widgets/CharWidgets.swift:554,555,556,562,563
private fun Pen.row(name: String, c: Double, x: Float, y: Float, rw: Float) {
    val col: Color = if (mode == WidgetRenderingMode.accented) Color.White else if (c >= 0) upC else dnC
    val padH = 4f; val gap = 4f; val rh = 14f
    rrect(x, y, rw, rh, 4f, Color.Black, a = 0.18f)
    // ios: Widgets/CharWidgets.swift:557
    circle(x + padH + 4f / 2, y + rh / 2, 4f / 2, col)
    // ios: Widgets/CharWidgets.swift:559,560
    val pt = pctText(c)
    val pz = size(pt, 9.5f, W.semibold)
    fade { text(pt, 9.5f, x + rw - padH - pz.width, y + (rh - pz.height) / 2, W.semibold, col, maxLines = 1) }
    // ios: Widgets/CharWidgets.swift:558
    val nx = x + padH + 4f + gap; val nz = size(name, 9f, W.semibold, maxW = max(1f, x + rw - padH - pz.width - gap - nx), maxLines = 1)
    text(name, 9f, nx, y + (rh - nz.height) / 2, W.semibold, maxW = max(1f, x + rw - padH - pz.width - gap - nx), maxLines = 1)
}

// MARK: 새 시안 ① 자산 추이 · 키 재기: 문틀 기둥에 지난 6거래일 자산을 금으로, 오늘 금은 인물 머리 높이에 빨갛게

// ios: Widgets/CharWidgets.swift:569
@Composable fun HeightCharView(e: CharEntry) {
    val hide = LocalCharHide.current
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.height"; ink = Color.White; heightChar(e, hide = hide) }
}

// ios: Widgets/CharWidgets.swift:570,571,572,573,574
fun Pen.heightChar(e: CharEntry, hide: Boolean? = null) = styleReader { _ ->
    val r = e.r
    // ios: Widgets/CharWidgets.swift:575
    charBleed { heightMarks(if (e.locked) emptyList() else (r.spark ?: emptyList()).takeLast(7), today = e.date) }
    // ios: Widgets/CharWidgets.swift:576
    val pn = poseOr(e.c, if (Store.isReloading) "pose_measure_up" else if (r.dayChg < 0) "pose_measure_down" else "pose_measure_base")
    // ios: Widgets/CharWidgets.swift:577,578
    pinSprite(pn, e.locked, Al.bottom, y = 10f, dy = charHop(-4f))
    // ios: Widgets/CharWidgets.swift:579,580,588
    val tx = 16f; val ty = 40f
    if (e.locked) meetLater(e.c, tx, ty) else {
        // ios: Widgets/CharWidgets.swift:581,582
        val gap = 4f
        val m = charMoney(hide) { hd -> fade { val z = text(if (hd) charHidden else eokMan(r.total), 24f, tx, ty, maxW = 92f, maxLines = 1, minScale = 0.6f); Rect(tx, ty, tx + z.width, ty + z.height) } }
        // ios: Widgets/CharWidgets.swift:583,584
        fade { text(listOf(Seg(pctText(r.dayChg), 11f, W.semibold, if (r.dayChg >= 0) upC else dnC), Seg(" " + WL.t("오늘"), 11f, W.semibold)), tx, m.bottom + gap, maxLines = 1) }
    }
    // ios: Widgets/CharWidgets.swift:589
    cornerB(e)
}

/** 기둥은 오른쪽 가장자리에서 46~56pt, 오늘 금은 인물 머리(아래에서 74pt). 가장 멀리 간 날이 머리에서 54pt (2% 안쪽 차이는 덜 벌린다)
 *  금액이 줄면 지난 날 금이 머리 위로 올라간다 */
// ios: Widgets/CharWidgets.swift:598,599,600,601,602,603,628
private fun Pen.heightMarks(v: List<Double>, today: Instant) = fade {
    val mono = mode != WidgetRenderingMode.fullColor
    // ios: Widgets/CharWidgets.swift:604,605
    val px = w - 56; val head = h - 74
    var ys: List<Float> = emptyList()
    // ios: Widgets/CharWidgets.swift:606,607,608
    val now = v.lastOrNull()
    if (now != null && v.size >= 2) {
        val past = v.dropLast(1); val dev = max(max(past.maxOfOrNull { abs(it - now) } ?: 0.0, abs(now) * 0.02), 1e-9)
        ys = past.map { min(h - 6, max(44f, head + ((now - it) / dev).toFloat() * 54)) }
    }
    // ios: Widgets/CharWidgets.swift:610,611
    val top = min(head - 8, (ys.minOrNull() ?: head) - 8)
    rect(px, top, 10f, h - top, if (mono) wh(0.22f) else rgb(0xd9a877))
    // ios: Widgets/CharWidgets.swift:612
    if (!mono) rect(px, top, 2f, h - top, rgb(0xf0c89a))
    // ios: Widgets/CharWidgets.swift:613,614
    val used = if (v.isEmpty()) mutableListOf() else mutableListOf(head)
    for (i in ys.indices.reversed()) {   // 최근 날부터: 날짜 글자가 겹치면 오래된 날 글자를 뺀다
        val y = ys[i]
        // ios: Widgets/CharWidgets.swift:615,616
        line(px - 2, y, px + 12, y, if (mono) wh(0.7f) else rgb(0x4a3426), 1.5f)
        // ios: Widgets/CharWidgets.swift:617,618
        if (!used.all { abs(it - y) >= 9 }) continue
        used.add(y)
        // ios: Widgets/CharWidgets.swift:619,620
        val t = tradeDay(ys.size - i, today); val z = size(t, 8f)
        text(t, 8f, px + 16, y - z.height / 2, color = wh(0.7f))
    }
    // ios: Widgets/CharWidgets.swift:622,623,624,625
    if (v.isNotEmpty()) {
        line(px - 46, head, px + 12, head, upC, 2f)
        val t = WL.t("오늘"); val z = size(t, 8f, W.bold)
        text(t, 8f, px + 16, head - z.height / 2, W.bold, upC)
    }
}

/** n 거래일 전 'M/d' (주말은 건너뛴다. 공휴일은 모른다) */
// ios: Widgets/CharWidgets.swift:633,634,635,636,637,638
private fun tradeDay(n: Int, d: Instant): String {
    var x = d.atZone(ZoneId.systemDefault()).toLocalDate(); var k = 0
    while (k < n) { x = x.minusDays(1); if (x.dayOfWeek != DayOfWeek.SATURDAY && x.dayOfWeek != DayOfWeek.SUNDAY) k += 1 }
    return "${x.monthValue}/${x.dayOfMonth}"
}

// MARK: 새 시안 ② 본전 진행 · 퍼즐: 5×4=20조각(1조각 5%), 다 맞추면 깃발 그림. 인물이 다음 조각을 든다

// ios: Widgets/CharWidgets.swift:643
@Composable fun PuzzleCharView(e: CharEntry) {
    val hide = LocalCharHide.current
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.puzzle"; ink = Color.White; puzzleChar(e, hide = hide) }
}

// ios: Widgets/CharWidgets.swift:644,645,646,647,648,649
fun Pen.puzzleChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    val n = min(20, max(0, floor(r.pct * 20 + 1e-9).toInt()))
    // ios: Widgets/CharWidgets.swift:650
    if (!e.locked) charBleed { puzzleBoard(n = n, c = s.accent) }
    // ios: Widgets/CharWidgets.swift:651
    val pn = poseOr(e.c, if (Store.isReloading) "pose_puzzle_refresh" else "pose_puzzle_base")
    // ios: Widgets/CharWidgets.swift:652,653
    pinSprite(pn, e.locked, Al.bottomTrailing, x = 8f, y = 26f, dy = charHop(3f))
    // ios: Widgets/CharWidgets.swift:654,655
    val kt = WL.t("{key}까지", mapOf("key" to WL.t(r.keyName))); val kz = size(kt, 12f, W.semibold, maxW = w - pad * 2, maxLines = 1)
    text(kt, 12f, 16f, 16f + (20f - kz.height) / 2, W.semibold, s.heading, maxW = w - pad * 2, maxLines = 1)
    // ios: Widgets/CharWidgets.swift:656,657,658,661
    val tx = 16f; val ty = 38f
    if (e.locked) meetLater(e.c, tx, ty) else {
        fade { text("${swRound(r.pct * 100)}%", 34f, tx, ty, maxW = w - pad * 2, maxLines = 1, minScale = 0.6f) }
    }
    // ios: Widgets/CharWidgets.swift:662,663,664,669,670
    if (!e.locked) {
        val d = (r.pct - r.pctYesterday) * 100
        val col = wh(0.75f); val gap = 0f
        val lh = size(WL.t(" · 어제 "), 10f, W.medium, maxLines = 1).height
        val o = charPin(Al.bottomLeading, 0f, lh)
        // ios: Widgets/CharWidgets.swift:665
        val m = charMoney(hide) { hd -> fade { val z = text(WL.t("남은 {v}", mapOf("v" to (if (hd) charHidden else eokMan(r.remain)))), 10f, o.x, o.y, W.medium, col, maxLines = 1); Rect(o.x, o.y, o.x + z.width, o.y + z.height) } }
        // ios: Widgets/CharWidgets.swift:666,667
        val z2 = text(WL.t(" · 어제 "), 10f, m.right + gap, o.y, W.medium, col, maxLines = 1)
        text((if (d >= 0) "+" else "") + cformat("%.1f%%p", d), 10f, m.right + gap + z2.width + gap, o.y, W.medium, if (d >= 0) upC else dnC, maxLines = 1)
    }
    // ios: Widgets/CharWidgets.swift:672
    cornerB(e)
}

/** 조각 하나: 오른쪽·위에 둥근 이음새. 시안 170pt 좌표 */
// ios: Widgets/CharWidgets.swift:680,681,682,683,684
private fun puzzlePiece(x: Float, y: Float, right: Boolean, top: Boolean): Path {
    val w = 12.5f; val r = 2.2f
    val p = Path()
    p.moveTo(x, y + w)
    p.lineTo(x, y)
    // ios: Widgets/CharWidgets.swift:685
    if (top) { p.lineTo(x + w / 2 - r, y); p.arcTo(Rect(center = Offset(x + w / 2, y), radius = r), 180f, 360f - 180f, false) }
    // ios: Widgets/CharWidgets.swift:686
    p.lineTo(x + w, y)
    // ios: Widgets/CharWidgets.swift:687
    if (right) { p.lineTo(x + w, y + w / 2 - r); p.arcTo(Rect(center = Offset(x + w, y + w / 2), radius = r), -90f, 90f + 90f, false) }
    // ios: Widgets/CharWidgets.swift:688,689,690
    p.lineTo(x + w, y + w)
    p.close()
    return p
}

/** 아래 줄 왼쪽부터 맞춘다. 맞춘 조각에만 깃발 그림이 보이고, 다음 조각은 인물 손 위에 (위젯이 작으면 같이 줄인다) */
// ios: Widgets/CharWidgets.swift:694,695,696,697,698,699,700,701,732
private fun Pen.puzzleBoard(n: Int, c: Color) = fade {
    val mono = mode != WidgetRenderingMode.fullColor; val fc: Color = if (mono) wh(0.35f) else c
    val k = min(w, h) / 170
    scaled(k, k, Offset.Zero) {
        // ios: Widgets/CharWidgets.swift:702,703
        val pw = 12.5f; val y0 = 134 - 4 * pw
        val done = Path(); val pieces = mutableListOf<Path>()
        // ios: Widgets/CharWidgets.swift:704,705,706,707,708
        for (i in 0 until 20) {
            val col = i % 5; val row = i / 5   // row 0 = 맨 아래 줄
            val p = puzzlePiece(16 + col * pw, y0 + (3 - row) * pw, right = col < 4, top = row < 3)
            if (i < n) { done.addPath(p); pieces.add(p) }
            else stroke(p, wh(if (mono) 0.25f else 0.4f), 1f, dash = floatArrayOf(2f, 1.5f))
        }
        // ios: Widgets/CharWidgets.swift:710
        fill(done, fc)
        // ios: Widgets/CharWidgets.swift:711,712
        clipped(done) {
            // 떠오르는 해: 지평선 위 해 + 햇살, 아래 물에 비친 빛
            // ios: Widgets/CharWidgets.swift:714,715
            val sun: Color = if (mono) wh(0.9f) else rgb(0xffb547); val cx = 47.25f; val hy = 114f
            oval(cx - 12, hy - 12, 24f, 24f, sun)
            // ios: Widgets/CharWidgets.swift:716,717,718,719
            var a = 30.0
            while (a <= 150.0) {
                val rd = a * PI / 180; val dx = cos(rd).toFloat(); val dy = sin(rd).toFloat()
                line(cx + 16 * dx, hy - 16 * dy, cx + 24 * dx, hy - 24 * dy, sun, 2f, cap = StrokeCap.Round)
                a += 30.0
            }
            // ios: Widgets/CharWidgets.swift:721,722
            rect(16f, hy, 62.5f, 134 - hy, fc)
            rect(16f, hy - 0.75f, 62.5f, 1.5f, Color.White)
            // ios: Widgets/CharWidgets.swift:723,724
            for ((i, bw) in listOf(18f, 11f, 5f).withIndex()) {
                rrect(cx - bw / 2, hy + 4 + i * 5f, bw, 1.8f, 0.9f, sun)
            }
        }
        // ios: Widgets/CharWidgets.swift:726
        for (p in pieces) stroke(p, Color.Black.copy(alpha = 0.3f), 0.8f)
        // ios: Widgets/CharWidgets.swift:727,728,729
        if (n < 20) {
            val p = puzzlePiece(146f, 64f, right = true, top = false)
            fill(p, fc); stroke(p, Color.White, 1f)
        }
    }
}

// MARK: 새 시안 ③ 블록 · 꽃밭: 100칸 밭에 블록 1개 = 꽃 1송이, 어제 늘어난 칸은 봉오리. 밭은 10번째까지

// ios: Widgets/CharWidgets.swift:738
@Composable fun GardenCharView(e: CharEntry) {
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.garden"; ink = Color.White; gardenChar(e) }
}

// ios: Widgets/CharWidgets.swift:739,740,741,742,743,744,745,746
@Suppress("UNUSED_PARAMETER")
fun Pen.gardenChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    val g = min(10, r.cells / 100 + 1); val fill = if (r.cells >= 1000) 100 else r.cells % 100
    val gy = min(10, r.cellsYesterday / 100 + 1); val fy = if (gy == g) (if (r.cellsYesterday >= 1000) 100 else r.cellsYesterday % 100) else 0
    val d = r.cells - r.cellsYesterday
    // ios: Widgets/CharWidgets.swift:747
    if (!e.locked) charBleed { gardenBed(fill = fill, bloom = min(fill, fy), garden = g, c = s.accent) }
    // ios: Widgets/CharWidgets.swift:748
    val pn = poseOr(e.c, if (Store.isReloading) "pose_garden_refresh" else "pose_garden_base")
    // ios: Widgets/CharWidgets.swift:749,750
    pinSprite(pn, e.locked, Al.bottomTrailing, x = 8f, y = 10f)
    // ios: Widgets/CharWidgets.swift:751,752
    val gt = WL.t("{n}번째 꽃밭", mapOf("n" to "$g")); val gz = size(gt, 11f, W.semibold, maxW = w - pad * 2, maxLines = 1)
    text(gt, 11f, 16f, 16f + (20f - gz.height) / 2, W.semibold, s.heading, maxW = w - pad * 2, maxLines = 1)
    // ios: Widgets/CharWidgets.swift:753
    if (e.locked) meetLater(e.c, 16f, 40f) else {
        // ios: Widgets/CharWidgets.swift:754,755,756,757
        val segs = listOf(Seg("$fill", 20f), Seg("/100" + WL.t(" · 어제 "), 10f, color = wh(0.75f)),
            Seg((if (d >= 0) "+" else "") + "$d", 10f, color = if (d >= 0) upC else dnC))
        val o = charPin(Al.bottomLeading, 0f, size(segs, maxLines = 1).height)
        fade { text(segs, o.x, o.y, maxLines = 1) }
    }
    // ios: Widgets/CharWidgets.swift:759
    cornerB(e)
}

/** 아래 줄 왼쪽부터 핀다. 오른쪽 위에 밭 10개(5×2): 다 핀 밭 + 테두리 친 지금 밭 + 남은 밭. 물뿌리개는 마지막으로 핀 칸의 줄 오른쪽 끝 옆 */
// ios: Widgets/CharWidgets.swift:767,768,769,770,771,772,773,774,818
private fun Pen.gardenBed(fill: Int, bloom: Int, garden: Int, c: Color) = fade {
    val mono = mode != WidgetRenderingMode.fullColor
    val k = min(w, h) / 170
    scaled(k, k, Offset.Zero) {
        // ios: Widgets/CharWidgets.swift:775,776
        val dirt = if (mono) wh(0.15f) else rgb(0x5a3a28); val leaf = if (mono) wh(0.5f) else rgb(0x7bc46a)
        val petal = if (mono) wh(0.95f) else rgb(0xff9fb4); val core = if (mono) wh(0f) else rgb(0xffe27a)
        // ios: Widgets/CharWidgets.swift:777,778,779
        for (i in 0 until 100) {
            val x = 16 + (i % 10) * 8.6f; val y = 42 + (9 - i / 10) * 8.6f
            rrect(x, y, 7f, 7f, 1.2f, dirt)
            // ios: Widgets/CharWidgets.swift:780,781,782,783
            if (i < bloom) {
                rect(x + 3, y + 4, 1f, 3f, leaf)
                rrect(x + 1.5f, y + 1, 4f, 3.4f, 1f, petal)
                rect(x + 3, y + 2, 1f, 1f, core)
            // ios: Widgets/CharWidgets.swift:784,785,786
            } else if (i < fill) {
                rect(x + 3, y + 3.5f, 1f, 3.5f, leaf)
                oval(x + 2.5f, y + 2, 2f, 2f, leaf)
            }
        }
        // 밭 10개를 5개씩 2줄: 다 핀 밭은 꽃, 지금 밭은 테두리+번호, 남은 밭은 흐린 칸
        // ios: Widgets/CharWidgets.swift:790,791
        for (j in 0 until 10) {
            val b = Rect(Offset(105 + (j % 5) * 10f, 43.5f + (j / 5) * 10f), Size(9f, 9f))
            // ios: Widgets/CharWidgets.swift:792,793,794
            if (j < garden - 1) {
                rrect(b.left, b.top, b.width, b.height, 2f, petal)
                rect(b.left + 3, b.top + 3, 3f, 3f, core)
            // ios: Widgets/CharWidgets.swift:795,796,797,798
            } else if (j == garden - 1) {
                rrect(b.left, b.top, b.width, b.height, 2f, dirt)
                rrectStroke(b.left, b.top, b.width, b.height, 2f, c, 1.2f)
                val t = "$garden"; val z = size(t, 6f, W.bold)
                text(t, 6f, b.center.x - z.width / 2, b.center.y - z.height / 2, W.bold, c)
            // ios: Widgets/CharWidgets.swift:799,800
            } else {
                rrectStroke(b.left + 0.5f, b.top + 0.5f, b.width - 0.5f * 2, b.height - 0.5f * 2, 2f, wh(0.22f), 1f)
            }
        }
        // ios: Widgets/CharWidgets.swift:803
        val can = if (mono) wh(0.5f) else rgb(0x8fb7d9); val drop = if (mono) wh(0.5f) else rgb(0xbfe3ff)
        // 물뿌리개: 마지막으로 핀 칸(0송이면 첫 칸)이 있는 줄의 오른쪽 끝 옆. 7~9번째 줄은 밭 칸과 겹쳐서 줄 끝 칸 위(빈 윗줄)에 둔다
        // ios: Widgets/CharWidgets.swift:805,806,807
        val t = max(fill, 1) - 1; val row = t / 10; val ry = 42 + (9 - row) * 8.6f
        val bx: Float = if (row in 6..8) 92f else 104f; val by = ry - 9
        rrect(bx, by, 9f, 7f, 1f, can)
        // ios: Widgets/CharWidgets.swift:808,809,810
        val tip = Offset(bx - 4, by - 1.5f)
        line(bx + 0.5f, by + 1, tip.x, tip.y, can, 2f)
        // 물방울: 주둥이에서 그 칸 위로 떨어지는 곡선 위 3개
        // ios: Widgets/CharWidgets.swift:812,813,814,815
        val px = 16 + (t % 10) * 8.6f + 3.5f; val py = ry + 1
        for (f in listOf(0.35f, 0.65f, 0.9f)) {
            val x = tip.x + (px - tip.x) * f; val y = tip.y + (py - tip.y) * f * f
            oval(x - 0.8f, y - 0.8f, 1.6f, 1.6f, drop)
        }
    }
}

// MARK: 새 시안 ④ 오늘의 움직임 · 줄다리기: 왼쪽 내린 종목 팀, 오른쪽 오른 종목 팀. 리본이 오늘 등락만큼 끌려가고 인물은 심판

// ios: Widgets/CharWidgets.swift:824
@Composable fun TugCharView(e: CharEntry) {
    val hide = LocalCharHide.current
    FaceCanvas(dark = LocalCharDark.current) { kind = "char.tug"; ink = Color.White; tugChar(e, hide = hide) }
}

// ios: Widgets/CharWidgets.swift:825,826,827,828,829,830
fun Pen.tugChar(e: CharEntry, hide: Boolean? = null) = styleReader { dark ->
    val s = CharStyle(c = e.c, locked = e.locked, dark = dark); val r = e.r
    // ios: Widgets/CharWidgets.swift:831
    charBleed { tugRope(chg = if (e.locked) 0.0 else r.dayChg) }
    // ios: Widgets/CharWidgets.swift:832,833,834
    if (!e.locked) {
        team(r.tiles.filter { it.c < 0 }, up = false, Al.topLeading, y = 64f)
        team(r.tiles.filter { it.c > 0 }, up = true, Al.topTrailing, x = 4f, y = 64f)
    }
    // ios: Widgets/CharWidgets.swift:836
    val pn = poseOr(e.c, if (Store.isReloading) "pose_tug_refresh" else if (abs(r.dayChg) < 0.0005) "pose_tug_tie" else if (r.dayChg > 0) "pose_tug_right" else "pose_tug_left")
    // ios: Widgets/CharWidgets.swift:837,838
    pinSprite(pn, e.locked, Al.bottomLeading, x = 44f, y = 8f)
    // ios: Widgets/CharWidgets.swift:839,840,844
    val x = 16f; val mw = w - pad * 2; val gap = 0f
    val tz = size(WL.t("오늘의 움직임"), 12f, W.semibold, maxW = mw, maxLines = 1)
    text(WL.t("오늘의 움직임"), 12f, x, 16f + (20f - tz.height) / 2, W.semibold, s.heading, maxW = mw, maxLines = 1)
    // ios: Widgets/CharWidgets.swift:841,842
    text(if (e.locked) lockLine(e.c) else WL.t("다음: ") + (r.next ?: WL.t("예정된 일정 없음")), 9f, x, 16f + 20f + gap + 4f, W.medium, wh(0.8f), maxW = mw, maxLines = 1)
    // ios: Widgets/CharWidgets.swift:845,846,851
    if (!e.locked) {
        val right = w - 12f; val sp = 2f
        val pz = size(pctText(r.dayChg), 11f, W.semibold, maxLines = 1)
        val py = h - 16f - pz.height
        // ios: Widgets/CharWidgets.swift:847
        charMoney(hide) { hd -> fade { val tt = if (hd) charHidden else eokMan(r.total); val mz = size(tt, 12f, maxLines = 1); val my = py - sp - mz.height; text(tt, 12f, right, my, maxLines = 1, align = 1f); Rect(right - mz.width, my, right, my + mz.height) } }
        // ios: Widgets/CharWidgets.swift:848,849
        fade { text(pctText(r.dayChg) + " " + WL.t("오늘"), 11f, right, py, W.semibold, if (r.dayChg >= 0) upC else dnC, maxLines = 1, align = 1f) }
    }
    // ios: Widgets/CharWidgets.swift:853
    cornerB(e)
}

/** 한 팀: 비중 큰 종목 하나 + 나머지는 '외 n' 한 칸 */
// ios: Widgets/CharWidgets.swift:860,861,862,863,867
private fun Pen.team(t: List<WReward.Tile>, up: Boolean, a: Al, x: Float = 16f, y: Float = 16f) = fade {
    val all = t.sortedByDescending { it.w }; val rest = all.drop(1)
    val ww = rest.fold(0.0) { s, v -> s + v.w }; val rc = if (ww > 0) rest.fold(0.0) { s, v -> s + v.w * v.c } / ww else 0.0
    val gap = 2f
    val tw = (if (all.isEmpty()) 0f else 38f) + (if (rest.isEmpty()) 0f else gap + 26f)
    val o = charPin(a, tw, 28f, x, y)
    var bx = o.x
    // ios: Widgets/CharWidgets.swift:864
    all.firstOrNull()?.let { top -> block(top.t, top.c, width = 38f, up = up, x = bx, y = o.y); bx += 38f + gap }
    // ios: Widgets/CharWidgets.swift:865
    if (rest.isNotEmpty()) block(WL.t("외 {n}", mapOf("n" to "${rest.size}")), rc, width = 26f, up = up, x = bx, y = o.y)
}

// ios: Widgets/CharWidgets.swift:870,871,872,873,878,879
private fun Pen.block(name: String, c: Double, width: Float, up: Boolean, x: Float, y: Float) {
    val fill: Color = if (mode == WidgetRenderingMode.accented) wh(0.2f)
        else if (up) Color(red = 0.95f, green = 0.35f, blue = 0.38f).copy(alpha = 0.55f) else Color(red = 0.42f, green = 0.58f, blue = 0.95f).copy(alpha = 0.6f)
    val bh = 28f; val p = 3f; val gap = 0f
    rrect(x, y, width, bh, 6f, fill)
    // ios: Widgets/CharWidgets.swift:874
    text(name, 8f, x + p, y + p, W.semibold, maxW = width - p * 2, maxLines = 1)
    // ios: Widgets/CharWidgets.swift:875,876
    val pt = pctText(c); val pz = size(pt, 9f, maxW = width - p * 2, maxLines = 1, minScale = 0.7f)
    fade { text(pt, 9f, x + p, max(y + p + gap + 0f, y + bh - p - pz.height), maxW = width - p * 2, maxLines = 1, minScale = 0.7f) }
}

/** 밧줄(위에서 78pt)과 가운데 점선. 리본은 오늘 1%에 6.7pt, 많아야 40pt 끌려간다 */
// ios: Widgets/CharWidgets.swift:884,885,886,887,888,898
private fun Pen.tugRope(chg: Double) = fade {
    val mid = w / 2; val y = 78f
    // ios: Widgets/CharWidgets.swift:889,890
    line(mid, 60f, mid, 98f, wh(0.4f), 1f, dash = floatArrayOf(2f, 2f))
    // ios: Widgets/CharWidgets.swift:891,892
    line(6f, y, w - 6, y, rgb(0xe8d3a8), 2.5f)
    // ios: Widgets/CharWidgets.swift:893
    val x = mid + max(-40.0, min(40.0, chg * 100 * 6.7)).toFloat(); val col = if (chg >= 0) upC else dnC
    // ios: Widgets/CharWidgets.swift:894,895
    val tri = Path(); tri.moveTo(x, y); tri.lineTo(x - 4, y - 8); tri.lineTo(x + 4, y - 8); tri.close()
    fill(tri, col)
    // ios: Widgets/CharWidgets.swift:896
    rect(x - 1.5f, y - 4, 3f, 8f, col)
}

/** CharFrame 이 글자를 늘 밝게 쓰므로, 기본·다크 구분은 원래 값(Pen.dark = 아이폰 charDark)을 따로 읽는다 */
// ios: Widgets/CharWidgets.swift:903,904,905,906
private inline fun <T> Pen.styleReader(content: (Boolean) -> T): T = content(dark)

// 아이폰 환경값: CharDarkKey(false) = Pen.dark, CharKindKey("") = Pen.kind, CharHideKey(nil) = 그리기 함수의 hide 인자 (앱은 LocalCharHide)
// ios: Widgets/CharWidgets.swift:909,910,911,912,914,915,916,919,920,921,923,924,925
private object CharEnv {
    object CharDarkKey { const val defaultValue = false }
    object CharKindKey { const val defaultValue = "" }
    object CharHideKey { val defaultValue: Boolean? = null }
    fun charKind(p: Pen): String = p.kind
    fun charHide(force: Boolean?): Boolean? = force ?: CharHideKey.defaultValue
    fun charDark(p: Pen): Boolean = p.dark || CharDarkKey.defaultValue
}

/** ↻ 다시 받기: 앱이 써 둔 최신 값으로 위젯을 다시 그린다 (기본 위젯의 버튼과 같은 동작). 읽는 이름을 준다
 *  서재 위젯(storyOnly)은 시세와 상관이 없어서 시세는 받지 않고 읽던 자리만 다시 그린다 */
// ios: Widgets/CharWidgets.swift:931,932,933,934,935,936,937
fun Pen.charReload(left: Float, top: Float, storyOnly: Boolean = false): String {
    val busy = Store.isReloading // 누른 뒤 받는 동안은 '받는 중' 모양
    circle(left + 20f / 2, top + 20f / 2, 20f / 2, Color.White, a = if (busy) 0.35f else 0.18f)
    icon(sf(if (busy) "ellipsis" else "arrow.clockwise"), left + 10f, top + 10f, symSize(10f), ink)
    val label = WL.t(if (busy) "받는 중" else "다시 받기")
    // 앱 미리보기에서는 그림만: hit 은 앱(inApp)에서 쌓지 않는다 (#if WIDGET_EXT)
    // ios: Widgets/CharWidgets.swift:938,939,940,941,942,943
    if (!inApp) {
        val onClick = if (storyOnly) Act.StoryReload else Act.Reload   // Button(intent: StoryReloadIntent()) · Button(intent: ReloadIntent())
        hit(left - 4, top - 4, 20f + 8, 20f + 8, onClick)
    }
    return label
}

/** 읽는 문장 + 따로 고를 수 있는 동작 이름 */
class CharSpeak(val label: String, val actions: List<Pair<String, Act>>)

/** VoiceOver·TalkBack: 위젯 하나를 한 문장으로 읽는다 (숫자 조각을 따로 읽지 않게). ↻·금액 숨기기는 따로 고를 수 있는 동작으로 남긴다. 앱 미리보기에서는 문장만 */
// ios: Widgets/CharWidgets.swift:947,950,951,952,953,954,955,956,957,958,959,961,962,963,964
fun charSpeak(label: String, kind: String, money: Boolean = true, reload: Boolean = true, inApp: Boolean = false): CharSpeak {
    if (inApp || !availableIOS(18)) return CharSpeak(label, emptyList())
    val reloadName = WL.t("다시 받기"); val moneyName = WL.t(if (Store.isHidden(kind)) "금액 보이기" else "금액 숨기기")
    val acts = mutableListOf<Pair<String, Act>>()
    if (reload) acts.add(reloadName to Act.Reload)
    if (money) acts.add(moneyName to Act.Toggle(kind))
    return CharSpeak(label, acts)
}

/** 숨긴 금액 자리 (기본 위젯과 같은 모양) */
// ios: Widgets/CharWidgets.swift:969
private const val charHidden = "••••"

/** 금액: 누르면 숨기고 한 번 더 누르면 보인다 (기본 위젯의 Money 와 같은 동작, 앱 미리보기에서는 그리기만). label 이 그린 상자를 준다 */
// ios: Widgets/CharWidgets.swift:972,973,974,975,976
private inline fun Pen.charMoney(force: Boolean?, label: (Boolean) -> Rect): Rect {
    // ios: Widgets/CharWidgets.swift:977,978
    if (!inApp) { val onClick = Act.Toggle(kind); val r = label(Store.isHidden(kind)); hit(r.left - 4, r.top - 4, r.width + 8, r.height + 8, onClick); return r }   // Button(intent: ToggleAmountIntent(kind: kind))
    // ios: Widgets/CharWidgets.swift:979,980,981
    return label(force ?: Store.isHidden(kind))
}
