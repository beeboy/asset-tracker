package com.naeilo.widget

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.naeilo.app.core.Fresh
import com.naeilo.app.core.Store
import com.naeilo.app.core.WStory
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

/** SF Symbols 이름 → 위젯 기호 (tools/port_check/symbols.md) */
private fun storySym(name: String): Icon = when (name) { "lock.fill" -> Icon.lock; "ellipsis" -> Icon.more; else -> Icon.refresh }

/** 작은 서재 (시안): 머리 '서재 · 3분 전 ↻', 장 그림 띠, 장 제목, 읽던 문장 한두 줄을 인용처럼, 아래 진행 숫자·막대와 인물 */
fun Pen.storyWidget(s: WStory) = StoryWidgetView.body(this, s, now)

/** 누르면 갈 곳 (아이폰 .widgetURL) */
// ios: Widgets/StoryWidget.swift:94
fun storyURL(s: WStory): String = s.url.toString()

/** VoiceOver 한 문장 (아이폰 .accessibilityLabel) */
// ios: Widgets/StoryWidget.swift:95,96,97
fun storySpeech(s: WStory): String { val en = s.lang == "en"; return if (en) (if (s.open) "Library, ${s.label}. ${s.line}" else "Library locked. ${s.line}")
    else (if (s.open) "서재, ${s.label}. ${s.line}" else "서재 잠김. ${s.line}") }

/** 앱 위젯 목록 미리보기 */
@Composable
fun StoryWidgetPreview(s: WStory) = FaceCanvas { storyWidget(s) }

// ios: Widgets/StoryWidget.swift:38,39,40
object StoryWidgetView {
    // ios: Widgets/StoryWidget.swift:42
    private val mint = Color(red = 0x5F / 255f, green = 0xD0 / 255f, blue = 0xC4 / 255f)

    // ios: Widgets/StoryWidget.swift:44,41
    fun body(p: Pen, s: WStory, date: Instant) = with(p) {
        // ios: Widgets/StoryWidget.swift:90,91,92
        rect(0f, 0f, w, h, Brush.linearGradient(listOf(Color(red = 0x1E / 255f, green = 0x24 / 255f, blue = 0x36 / 255f), Color(red = 0x2A / 255f, green = 0x33 / 255f, blue = 0x50 / 255f)), start = Offset(0f, 0f), end = Offset(w, h)))
        // ios: Widgets/StoryWidget.swift:88,89
        ink = Color.White
        // ios: Widgets/StoryWidget.swift:45
        val en = s.lang == "en"
        // ios: Widgets/StoryWidget.swift:46,47
        val x = pad; val cw = w - pad * 2; var y = pad; val sp = 4f; val hs = 4f; val right = x + cw
        // ios: Widgets/StoryWidget.swift:48
        val h11 = size("0", 11f, W.semibold).height; text(if (en) "Library" else "서재", 11f, x, y + 10 - h11 / 2, W.semibold, mint)
        // ios: Widgets/StoryWidget.swift:51
        charReload(right - 20, y)
        // ios: Widgets/StoryWidget.swift:49,50
        val ago = Fresh.ago(written(), now = date); val h10 = size(ago, 10f, W.medium).height; val minSp = 0f; text(ago, 10f, right - 20 - hs, y + 10 - h10 / 2, W.medium, Color.White.copy(alpha = 0.7f), maxW = max(minSp, cw - 20 - hs * 2 - 30), maxLines = 1, align = 1f)
        y += 20 + sp
        // ios: Widgets/StoryWidget.swift:53
        fade { art(p, x, y, cw, 34f, 6f) }; y += 34 + sp
        // ios: Widgets/StoryWidget.swift:54
        y += fade { text(s.label, 11f, x, y, W.bold, maxW = cw, maxLines = 1, minScale = 0.8f, alpha = 0.85f) }.height + sp
        // ios: Widgets/StoryWidget.swift:55,56,57,58,59,60,61
        fade { text("“${s.line}”", 13f, x, y, color = Color.White.copy(alpha = if (s.open) 0.92f else 0.7f), maxW = cw, maxLines = 2, italic = true, serif = true, lineSpacing = 1f) }
        // ios: Widgets/StoryWidget.swift:62,63
        val bottom = h - pad; val spMin = 0f; val fsp = 6f
        // ios: Widgets/StoryWidget.swift:85
        val img = image("w_${s.friend}"); val sh = 30f; val swd = if (img != null) sh * img.width / img.height else sh; charSprite(p, img, locked = !s.open, right - swd + 4, bottom - sh + 4, swd, sh)
        val lw = max(spMin, cw - swd - fsp)
        // ios: Widgets/StoryWidget.swift:64
        if (s.open) {
            // ios: Widgets/StoryWidget.swift:65,80
            fade { val vs = 3f; val barY = bottom - 3; val h10s = size("0", 10f, W.semibold).height; val rowY = barY - vs - h10s
                // ios: Widgets/StoryWidget.swift:66,67,68,69,71
                val pw = text("${(s.progress * 100).roundToInt()}%", 10f, x, rowY, W.semibold, mint, maxLines = 1).width; text(where_(s, en), 10f, x + lw, rowY, W.semibold, maxW = max(0f, lw - pw - 4), maxLines = 1, align = 1f, alpha = 0.7f)
                // ios: Widgets/StoryWidget.swift:72,73,74,78
                capsule(x, barY, lw, 3f, Color.White.copy(alpha = 0.18f))
                // ios: Widgets/StoryWidget.swift:75
                capsule(x, barY, max(4f, lw * s.progress.toFloat()), 3f, mint)
            }
        // ios: Widgets/StoryWidget.swift:81,82,83
        } else { val lt = if (en) "Locked" else "잠김"; val ls = size(lt, 11f, W.semibold); val ly = bottom - ls.height; icon(storySym("lock.fill"), x + 6, ly + ls.height / 2, 14f, Color.White.copy(alpha = 0.7f)); text(lt, 11f, x + 12 + 4, ly, W.semibold, Color.White.copy(alpha = 0.7f), maxW = max(0f, lw - 16)) }
    }

    /** "6장 중 2장" (프롤로그는 '프롤로그') */
    // ios: Widgets/StoryWidget.swift:101
    private fun where_(s: WStory, en: Boolean): String {
        // ios: Widgets/StoryWidget.swift:102
        if (s.chapter == 0) return if (en) "Prologue" else "프롤로그"
        // ios: Widgets/StoryWidget.swift:103
        return if (en) "Ch. ${s.chapter} of 6" else "6장 중 ${s.chapter}장"
    }

    /** 장 그림을 한 번 읽어 둔다: 파일이 바뀐 때(lastModified)만 다시 읽는다 (그릴 때마다 디코딩하지 않게) */
    private var artAt = -1L
    private var artImg: ImageBitmap? = null
    private fun artBitmap(): ImageBitmap? = synchronized(this) {
        val f = Store.url(WStory.artFile); val at = if (f.exists()) f.lastModified() else 0L
        if (at != artAt) { artImg = if (at == 0L) null else BitmapFactory.decodeFile(f.path)?.asImageBitmap(); artAt = at }
        artImg
    }

    /** 장 그림 띠: 투명·틴트에서도 그림 원래 색 그대로 (인물 그림과 같게). 그림이 아직 없으면 줄무늬 자리 */
    // ios: Widgets/StoryWidget.swift:107,108
    private fun art(p: Pen, x: Float, y: Float, ww: Float, hh: Float, r: Float) = with(p) { val img = artBitmap(); if (img != null) {
            // ios: Widgets/StoryWidget.swift:109
            cover(img, x, y, ww, hh, r, pixel = true)   // StoryWidget.swift:110 투명·틴트(iOS 18 accented) 전용은 예외 목록
        // ios: Widgets/StoryWidget.swift:111,112
        } else { rrect(x, y, ww, hh, 6f, mint.copy(alpha = 0.18f)) }
    }

    /** 앱이 story.json 을 마지막으로 쓴 때 */
    // ios: Widgets/StoryWidget.swift:117
    fun written(): Instant? { val f = Store.url("story.json"); return if (f.exists()) Instant.ofEpochMilli(f.lastModified()) else null }

    /** 서재만 다시 그리는 ↻ (CharWidgets.swift CharReload storyOnly): 20pt 동그라미, 받는 동안은 '받는 중' 모양 */
    private fun Pen.charReload(x: Float, y: Float) {
        val busy = Store.isReloading
        circle(x + 10, y + 10, 10f, Color.White, a = if (busy) 0.35f else 0.18f)
        icon(storySym(if (busy) "ellipsis" else "arrow.clockwise"), x + 10, y + 10, 13f, Color.White)
        hit(x - 4, y - 4, 28f, 28f, Act.StoryReload)
    }

    /** 인물 도트 그림 (CharWidgets.swift CharSprite): 최근접 확대, 잠기면 흰 실루엣, 아니면 발밑 그림자 */
    private fun charSprite(p: Pen, img: ImageBitmap?, locked: Boolean, x: Float, y: Float, ww: Float, hh: Float) = with(p) {
        val dot = hh / 34
        if (!locked && mode != WidgetRenderingMode.accented) oval(x + ww / 2 - dot * 16 / 2, y + hh + dot * 2 - dot * 5, dot * 16, dot * 5, Color.Black, a = 0.22f)
        if (img != null) pixels(img, x, y, ww, hh, tint = if (locked) Color.White.copy(alpha = 0.22f) else null)
    }
}
