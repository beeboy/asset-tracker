package com.naeilo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.naeilo.app.core.WMe
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 위젯 그리기 도구. 안드로이드 홈 위젯(RemoteViews)은 Canvas·겹쳐 놓기·애니메이션을 못 해서, 위젯 한 장을 그림 한 장으로 그린다.
// 같은 그리기를 앱 미리보기(설정 위젯 목록·공유 카드)는 Compose Canvas 로, 홈 위젯은 비트맵으로 쓴다.
// 단위는 모두 dp(=아이폰 pt): 붓이 캔버스를 화면 밀도만큼 키워 둔다. 글자 크기도 dp (큰 글자 설정으로 커지지 않는다: 위젯 칸이 정해져 있다)

/** 투명·틴트(아이폰 iOS 18 홈 화면)는 안드로이드에 없어 늘 fullColor */
enum class WidgetRenderingMode { fullColor, accented, vibrant }

/** 위젯을 누르는 자리 (dp). 홈 위젯은 이 자리에 투명 버튼을 얹는다 */
sealed class Act {
    object Reload : Act()
    object StoryReload : Act()
    class Toggle(val kind: String) : Act()
}
class Hit(val r: Rect, val act: Act)

/** 글자 무게: 아이폰 .regular·.medium·.semibold·.bold·.heavy */
object W {
    val regular = FontWeight.Normal
    val medium = FontWeight.Medium
    val semibold = FontWeight.SemiBold
    val bold = FontWeight.Bold
    val heavy = FontWeight.ExtraBold
}

/** 한 가지 모양의 글자 조각 (아이폰 Text + Text 이어 붙이기) */
class Seg(val s: String, val size: Float, val weight: FontWeight = W.regular, val color: Color? = null, val italic: Boolean = false, val serif: Boolean = false, val mono: Boolean = false)

/** 위젯 한 장을 그리는 붓. w·h 는 위젯 크기 (dp) */
class Pen(val d: DrawScope, val tm: TextMeasurer, val ctx: Context, val dark: Boolean, val w: Float, val h: Float,
          val mode: WidgetRenderingMode = WidgetRenderingMode.fullColor) {
    val hits = mutableListOf<Hit>()
    /** 지금 글자 기본색 (아이폰 foregroundStyle 을 위에서 준 것) */
    var ink: Color = Color.White
    /** 그림 전체 흐리기 (flipFade: 받는 중엔 0.35) */
    var alpha: Float = 1f
    /** 아이폰 환경값: 그리는 위젯 종류(widgetKind·charKind), 예시 값인지(isSample), 이 그림의 시각(entryDate) */
    var kind: String = ""
    var isSample: Boolean = false
    var now: java.time.Instant = java.time.Instant.now()
    /** 앱 미리보기면 true: 버튼 자리(hit)를 만들지 않는다 (아이폰 #if WIDGET_EXT 의 앱 쪽) */
    var inApp: Boolean = false

    fun hit(x: Float, y: Float, ww: Float, hh: Float, act: Act) { if (!inApp) hits.add(Hit(Rect(x, y, x + ww, y + hh), act)) }

    // MARK: 글자

    private fun style(sg: Seg, color: Color) = TextStyle(
        fontSize = sg.size.sp, fontWeight = sg.weight, color = color,
        fontStyle = if (sg.italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = if (sg.serif) FontFamily.Serif else if (sg.mono) FontFamily.Monospace else FontFamily.Default,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    private fun annotated(segs: List<Seg>, k: Float): AnnotatedString = buildAnnotatedString {
        for (sg in segs) {
            val st = style(Seg(sg.s, sg.size * k, sg.weight, sg.color, sg.italic, sg.serif, sg.mono), sg.color ?: ink).toSpanStyle()
            pushStyle(SpanStyle(color = st.color, fontSize = st.fontSize, fontWeight = st.fontWeight, fontStyle = st.fontStyle, fontFamily = st.fontFamily))
            append(sg.s); pop()
        }
    }

    /** 글자 재기 (그리지 않음). minScale: 아이폰 minimumScaleFactor (넘치면 이 배율까지 줄인다) */
    fun measure(segs: List<Seg>, maxW: Float = Float.POSITIVE_INFINITY, maxLines: Int = 1, minScale: Float = 1f, lineSpacing: Float = 0f): TextLayoutResult {
        var k = 1f
        var r = layout(segs, maxW, maxLines, k, lineSpacing)
        if (minScale < 1f && maxW.isFinite()) {
            // 한 줄에 다 들어갈 때까지 줄인다 (아이폰처럼 minScale 아래로는 안 줄이고 말줄임)
            val full = layout(segs, Float.POSITIVE_INFINITY, maxLines, 1f, lineSpacing)
            if (full.size.width > maxW) {
                k = max(minScale, (maxW / full.size.width) * 0.995f)
                r = layout(segs, maxW, maxLines, k, lineSpacing)
            }
        }
        return r
    }

    private fun layout(segs: List<Seg>, maxW: Float, maxLines: Int, k: Float, lineSpacing: Float): TextLayoutResult {
        val base = segs.first()
        val lh = if (lineSpacing > 0f) (base.size * k * 1.2f + lineSpacing).sp else androidx.compose.ui.unit.TextUnit.Unspecified
        return tm.measure(annotated(segs, k), style(Seg("", base.size * k, base.weight, base.color, base.italic, base.serif, base.mono), base.color ?: ink).copy(lineHeight = lh),
            overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1, maxLines = maxLines,
            constraints = if (maxW.isFinite()) Constraints(maxWidth = max(1, ceil(maxW).toInt())) else Constraints())
    }

    /** 글자 그리기. (x, y) = 글 상자 왼쪽 위. align: 0 왼쪽, 0.5 가운데, 1 오른쪽 (x 가 그 자리). 그린 크기를 준다 */
    fun text(segs: List<Seg>, x: Float, y: Float, maxW: Float = Float.POSITIVE_INFINITY, maxLines: Int = 1, minScale: Float = 1f,
             align: Float = 0f, alpha: Float = 1f, lineSpacing: Float = 0f): Size {
        val r = measure(segs, maxW, maxLines, minScale, lineSpacing)
        val tw = r.size.width.toFloat()
        d.drawText(r, topLeft = Offset(x - tw * align, y), alpha = alpha * this.alpha)
        return Size(tw, r.size.height.toFloat())
    }
    fun text(s: String, size: Float, x: Float, y: Float, weight: FontWeight = W.regular, color: Color? = null, maxW: Float = Float.POSITIVE_INFINITY,
             maxLines: Int = 1, minScale: Float = 1f, align: Float = 0f, alpha: Float = 1f, italic: Boolean = false, serif: Boolean = false,
             mono: Boolean = false, lineSpacing: Float = 0f): Size =
        text(listOf(Seg(s, size, weight, color, italic, serif, mono)), x, y, maxW, maxLines, minScale, align, alpha, lineSpacing)
    /** 글 상자 크기만 */
    fun size(s: String, size: Float, weight: FontWeight = W.regular, maxW: Float = Float.POSITIVE_INFINITY, maxLines: Int = 1, minScale: Float = 1f): Size {
        val r = measure(listOf(Seg(s, size, weight)), maxW, maxLines, minScale)
        return Size(r.size.width.toFloat(), r.size.height.toFloat())
    }
    fun size(segs: List<Seg>, maxW: Float = Float.POSITIVE_INFINITY, maxLines: Int = 1, minScale: Float = 1f): Size {
        val r = measure(segs, maxW, maxLines, minScale)
        return Size(r.size.width.toFloat(), r.size.height.toFloat())
    }
    /** 첫 줄 글자 바닥선 (윗변부터). 아이폰 .firstTextBaseline 맞춤 */
    fun baseline(s: String, size: Float, weight: FontWeight = W.regular): Float = measure(listOf(Seg(s, size, weight))).firstBaseline

    // MARK: 모양

    fun rect(x: Float, y: Float, ww: Float, hh: Float, c: Color, a: Float = 1f) =
        d.drawRect(c, Offset(x, y), Size(ww, hh), alpha = a * alpha)
    fun rect(x: Float, y: Float, ww: Float, hh: Float, b: Brush, a: Float = 1f) =
        d.drawRect(b, Offset(x, y), Size(ww, hh), alpha = a * alpha)
    fun rrect(x: Float, y: Float, ww: Float, hh: Float, r: Float, c: Color, a: Float = 1f) =
        d.drawRoundRect(c, Offset(x, y), Size(ww, hh), CornerRadius(r, r), alpha = a * alpha)
    fun rrectStroke(x: Float, y: Float, ww: Float, hh: Float, r: Float, c: Color, lw: Float, dash: FloatArray? = null, a: Float = 1f) =
        d.drawRoundRect(c, Offset(x, y), Size(ww, hh), CornerRadius(r, r), style = Stroke(lw, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }), alpha = a * alpha)
    /** 아이폰 strokeBorder: 선이 모양 안쪽에 들어간다 */
    fun rrectBorder(x: Float, y: Float, ww: Float, hh: Float, r: Float, c: Color, lw: Float, dash: FloatArray? = null) =
        rrectStroke(x + lw / 2, y + lw / 2, ww - lw, hh - lw, max(0f, r - lw / 2), c, lw, dash)
    fun capsule(x: Float, y: Float, ww: Float, hh: Float, c: Color, a: Float = 1f) = rrect(x, y, ww, hh, min(ww, hh) / 2, c, a)
    fun circle(cx: Float, cy: Float, r: Float, c: Color, a: Float = 1f) = d.drawCircle(c, r, Offset(cx, cy), alpha = a * alpha)
    fun circleStroke(cx: Float, cy: Float, r: Float, c: Color, lw: Float, a: Float = 1f) =
        d.drawCircle(c, r, Offset(cx, cy), style = Stroke(lw), alpha = a * alpha)
    fun oval(x: Float, y: Float, ww: Float, hh: Float, c: Color, a: Float = 1f) = d.drawOval(c, Offset(x, y), Size(ww, hh), alpha = a * alpha)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Color, lw: Float, dash: FloatArray? = null, cap: StrokeCap = StrokeCap.Butt, a: Float = 1f) =
        d.drawLine(c, Offset(x0, y0), Offset(x1, y1), lw, cap, dash?.let { PathEffect.dashPathEffect(it) }, alpha = a * alpha)
    fun fill(p: Path, c: Color, a: Float = 1f) = d.drawPath(p, c, alpha = a * alpha, style = Fill)
    fun fill(p: Path, b: Brush, a: Float = 1f) = d.drawPath(p, b, alpha = a * alpha, style = Fill)
    fun stroke(p: Path, c: Color, lw: Float, dash: FloatArray? = null, cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter, a: Float = 1f) =
        d.drawPath(p, c, alpha = a * alpha, style = Stroke(lw, cap = cap, join = join, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }))

    /** 받는 동안 흐리게 (아이폰 Flip.swift 의 flip·flipFade: 위젯은 움직이지 않으니 받는 중 모습 한 장) */
    fun <T> fade(block: () -> T): T {
        val a0 = alpha
        if (com.naeilo.app.core.Store.isReloading) alpha = a0 * 0.35f
        try { return block() } finally { alpha = a0 }
    }

    /** 그림 묶음을 (dx, dy) 옮겨 그린다 */
    fun at(dx: Float, dy: Float, block: () -> Unit) = d.translate(dx, dy) { block() }

    // MARK: 기호 (SF Symbols 자리, Material Symbols Rounded 모양. tools/port_check/symbols.md)

    /** 기호 하나를 size 칸 (가운데 cx, cy) 에 그린다 */
    fun icon(i: Icon, cx: Float, cy: Float, size: Float, c: Color, a: Float = 1f) {
        val k = size / 960f
        d.translate(cx - size / 2, cy - size / 2) {
            scale(k, k, Offset.Zero) { translate(0f, 960f) { drawPath(i.path, c, alpha = a * alpha) } }
        }
    }

    // MARK: 도트 그림

    /** 그림 이름으로 비트맵 (위젯 그림 w_*, 내 캐릭터는 앱이 쓴 파일). 없으면 null */
    fun image(name: String): ImageBitmap? = Sprites.get(ctx, name)

    /** 도트 그림을 최근접 확대로 (x, y) 에 ww × hh 로. tint 가 있으면 모양만 그 색 (실루엣) */
    fun pixels(img: ImageBitmap, x: Float, y: Float, ww: Float, hh: Float, a: Float = 1f, tint: Color? = null) {
        d.translate(x, y) {
            scale(ww / img.width, hh / img.height, Offset.Zero) {
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset.Zero, IntSize(img.width, img.height),
                    alpha = a * alpha, colorFilter = tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) }, filterQuality = FilterQuality.None)
            }
        }
    }
    /** 사진처럼 채우기 (.aspectRatio(.fill)): 상자를 덮고 넘치는 곳은 자른다 */
    fun cover(img: ImageBitmap, x: Float, y: Float, ww: Float, hh: Float, r: Float, pixel: Boolean = true) {
        val k = max(ww / img.width, hh / img.height)
        val sw = ww / k
        val sh = hh / k
        val sx = ((img.width - sw) / 2).roundToInt().coerceAtLeast(0)
        val sy = ((img.height - sh) / 2).roundToInt().coerceAtLeast(0)
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + ww, y + hh, CornerRadius(r, r))) }
        d.clipPath(clip) {
            drawImage(img, IntOffset(sx, sy), IntSize(sw.roundToInt().coerceAtMost(img.width), sh.roundToInt().coerceAtMost(img.height)),
                IntOffset(x.roundToInt(), y.roundToInt()), IntSize(ww.roundToInt(), hh.roundToInt()), alpha = alpha,
                filterQuality = if (pixel) FilterQuality.None else FilterQuality.Low)
        }
    }
}

/** 쓰는 기호 셋 (Material Symbols Rounded 960 판, 위로 960 옮겨 그린다) */
enum class Icon(d: String) {
    /** lock.fill */
    lock("M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5T800-560v400q0 33-23.5 56.5T720-80H240Zm240-200q33 0 56.5-23.5T560-360q0-33-23.5-56.5T480-440q-33 0-56.5 23.5T400-360q0 33 23.5 56.5T480-280ZM360-640h240v-80q0-50-35-85t-85-35q-50 0-85 35t-35 85v80Z"),
    /** arrow.clockwise */
    refresh("M480-160q-134 0-227-93t-93-227q0-134 93-227t227-93q69 0 132 28.5T720-690v-110h80v280H520v-80h168q-32-56-87.5-88T480-720q-100 0-170 70t-70 170q0 100 70 170t170 70q77 0 139-44t87-116h84q-28 106-114 173t-196 67Z"),
    /** ellipsis */
    more("M240-400q-33 0-56.5-23.5T160-480q0-33 23.5-56.5T240-560q33 0 56.5 23.5T320-480q0 33-23.5 56.5T240-400Zm240 0q-33 0-56.5-23.5T400-480q0-33 23.5-56.5T480-560q33 0 56.5 23.5T560-480q0 33-23.5 56.5T480-400Zm240 0q-33 0-56.5-23.5T640-480q0-33 23.5-56.5T720-560q33 0 56.5 23.5T800-480q0 33-23.5 56.5T720-400Z");
    val path: Path = PathParser().parsePathString(d).toPath()
}

/** 위젯 도트 그림 캐시: 한 번 읽은 그림은 다시 읽지 않는다 (위젯이 다시 그려질 때마다 파일을 읽지 않게) */
object Sprites {
    private val cache = HashMap<String, ImageBitmap?>()
    private val ids = HashMap<String, Int>()
    /** 그림 묶음에 있는지 (아이폰 UIImage(named:) != nil) */
    fun has(ctx: Context, name: String): Boolean = id(ctx, name) != 0
    private fun id(ctx: Context, name: String): Int = synchronized(ids) {
        ids.getOrPut(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
    }
    /** 그림 크기 (px = 도트) */
    fun get(ctx: Context, name: String): ImageBitmap? {
        // 내 캐릭터 그림은 앱이 바꿔 쓸 수 있어 캐시하지 않는다 (작은 파일)
        if (name.startsWith("w_me")) return WMe.image(name)?.asImageBitmap()
        synchronized(cache) {
            if (cache.containsKey(name)) return cache[name]
        }
        val i = id(ctx, name)
        val b: Bitmap? = if (i == 0) null else BitmapFactory.decodeResource(ctx.resources, i, BitmapFactory.Options().apply { inScaled = false })
        val img = b?.asImageBitmap()
        synchronized(cache) { cache[name] = img }
        return img
    }
}

/** small.json 을 한 번 읽어 두고, 앱이 다시 쓸 때(파일 시각이 바뀔 때)만 다시 읽는다 (그릴 때마다 파일을 풀지 않게) */
object Small {
    private var at = -1L
    private var v: com.naeilo.app.core.WSmall? = null
    @Synchronized fun load(): com.naeilo.app.core.WSmall? {
        val t = com.naeilo.app.core.Store.url(com.naeilo.app.core.WSmall.file).lastModified()
        if (t != at) { at = t; v = com.naeilo.app.core.WSmall.load() }
        return v
    }
}
