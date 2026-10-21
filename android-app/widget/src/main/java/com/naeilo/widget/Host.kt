package com.naeilo.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.naeilo.app.core.Store
import kotlin.math.ceil
import kotlin.math.min

// 위젯 그림을 비트맵(홈 위젯)이나 Compose Canvas(앱 미리보기)로 그린다. 그리기 코드는 하나 (Pen)

/** 앱 미리보기·공유 카드가 정하는 인물 위젯 다크 모양 (아이폰 환경값 charDark) */
val LocalCharDark = compositionLocalOf { false }
/** 앱 공유 카드가 금액 숨김을 정할 때 (null 이면 위젯 설정을 따른다. 아이폰 charHide) */
val LocalCharHide = compositionLocalOf<Boolean?> { null }

/** 위젯 안쪽 여백 (아이폰 작은 위젯 콘텐츠 여백 16pt) */
const val pad = 16f
/** 위젯 그림의 기준 폭 (아이폰 작은 위젯 158pt): 앱 안내 그림·홈 위젯이 같은 배치가 되게 */
const val designW = 158f
/** 홈 2×2 칸의 세로/가로 비 (삼성 기본 위젯 기준) */
const val tileRatio = 1.2f

/** 시스템이 다크 모드인지 */
fun isDark(ctx: Context): Boolean = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** 위젯 모서리 둥글기 (dp): 안드로이드 12 부터 시스템 값, 그 전은 16 */
fun cornerDp(ctx: Context): Float {
    if (Build.VERSION.SDK_INT >= 31) {
        val px = ctx.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        return px / ctx.resources.displayMetrics.density
    }
    return 16f
}

private var measurer: TextMeasurer? = null
/** 위젯용 글자 재개 (dp 단위로 재게 밀도 1). 한 번 만들어 다시 쓴다 */
fun textMeasurer(ctx: Context): TextMeasurer =
    measurer ?: TextMeasurer(createFontFamilyResolver(ctx.applicationContext), Density(1f), LayoutDirection.Ltr).also { measurer = it }

class Rendered(val bitmap: Bitmap, val hits: List<Hit>)

/** 위젯 한 장 (wDp × hDp) 을 비트맵으로. 모서리는 둥글게 자른다 */
fun render(ctx: Context, wDp: Float, hDp: Float, dark: Boolean, designW: Float = 0f, clip: Boolean = true, setup: Pen.() -> Unit = {}, draw: Pen.() -> Unit): Rendered {
    Store.init(ctx)
    if (designW > 0f && kotlin.math.abs(hDp - wDp) > 0.5f) {
        // 칸이 정사각형이 아니면: 설정 안내 그림과 똑같은 정사각형으로 그려 가운데 두고, 남는 곳은 바탕 끝줄을 늘려 채운다 (CLAMP)
        val side = min(wDp, hDp)
        val sq = render(ctx, side, side, dark, designW, clip = false, setup = setup, draw = draw)
        val px = sq.bitmap.width / side   // 1dp 당 픽셀
        val w = ceil(wDp * px).toInt(); val h = ceil(hDp * px).toInt()
        val dx = (wDp - side) / 2; val dy = (hDp - side) / 2
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sh = android.graphics.BitmapShader(sq.bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(android.graphics.Matrix().apply { setTranslate(dx * px, dy * px) }) }
        val rp = cornerDp(ctx) * px
        android.graphics.Canvas(out).drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), rp, rp, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = sh })
        return Rendered(out, sq.hits.map { Hit(it.r.translate(dx, dy), it.act) })
    }
    val dm = ctx.resources.displayMetrics
    // 비트맵이 너무 크면 위젯에 못 넘긴다 (RemoteViews 비트맵 한도): 큰 위젯도 밀도를 줄여 600px 안쪽으로
    val density = min(dm.density, 600f / maxOf(wDp, hDp, 1f))
    val bmp = Bitmap.createBitmap(maxOf(1, ceil(wDp * density).toInt()), maxOf(1, ceil(hDp * density).toInt()), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp.asImageBitmap())
    val tm = textMeasurer(ctx)
    var hits: List<Hit> = emptyList()
    val r = cornerDp(ctx)
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(bmp.width.toFloat(), bmp.height.toFloat())) {
        scale(density, Offset.Zero) {
            // designW 가 있으면 앱 안내 그림(Tile)처럼 폭 designW 로 그린 뒤 칸 크기로 키운다
            val k = if (designW > 0f) wDp / designW else 1f
            val body: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                scale(k, Offset.Zero) {
                    val p = Pen(this, tm, ctx, dark, wDp / k, hDp / k)
                    p.setup()
                    p.draw()
                    hits = p.hits.map { Hit(androidx.compose.ui.geometry.Rect(it.r.left * k, it.r.top * k, it.r.right * k, it.r.bottom * k), it.act) }
                }
            }
            if (clip) clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, wDp, hDp, CornerRadius(r, r))) }) { body() } else body()
        }
    }
    return Rendered(bmp, hits)
}

/** 앱 미리보기: 부모 칸을 위젯 콘텐츠 자리로 보고 (위젯 크기 = 부모 + 여백 bleed), 같은 그리기를 Canvas 로 */
@Composable
fun FaceCanvas(modifier: Modifier = Modifier.fillMaxSize(), bleed: Float = pad, dark: Boolean = LocalCharDark.current,
               setup: Pen.() -> Unit = {}, draw: Pen.() -> Unit) {
    val ctx = LocalContext.current
    val ffr = LocalFontFamilyResolver.current
    val tm = remember(ffr) { TextMeasurer(ffr, Density(1f), LayoutDirection.Ltr) }
    Canvas(modifier) {
        val dn = density
        val wDp = size.width / dn + bleed * 2
        val hDp = size.height / dn + bleed * 2
        scale(dn, Offset.Zero) {
            translate(-bleed, -bleed) {
                val p = Pen(this, tm, ctx, dark, wDp, hDp)
                p.inApp = true
                p.setup()
                p.draw()
            }
        }
    }
}

/** 앱 미리보기·공유 카드의 인물 위젯 바탕 (아이폰 CharBG View) */
@Composable
fun CharBG(c: com.naeilo.app.core.WChar, locked: Boolean, dark: Boolean) = FaceCanvas(bleed = 0f, dark = dark) { charBG(c, locked, dark) }
