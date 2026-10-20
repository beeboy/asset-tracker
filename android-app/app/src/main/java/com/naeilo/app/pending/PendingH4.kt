package com.naeilo.app.pending

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeColor
import com.naeilo.app.data.MeGroup
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.me.MeAvatar
import com.naeilo.app.ui.me.MePlaceholder
import com.naeilo.app.ui.me.mePixels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// MeViews(H4)가 부르는데 아직 옮기지 않은 Views/CharMotion.swift 이름의 자리.
// 진짜 이식(CharMotion 단계)이 오면 이 파일을 지운다. 버퍼 한 칸은 안드로이드 ARGB Int (0xAARRGGBB).

// TODO(Views/CharMotion.swift PixBuf): 지금 쓰는 것(on·rgb·dark·mix·luma·near·shade·outline)만 Swift 와 같은 규칙으로
object PixBuf {
    fun on(v: Int): Boolean = v ushr 24 != 0
    fun rgb(hex: Int): Int = (0xFF shl 24) or (hex and 0xFFFFFF)
    private fun r(v: Int) = ((v shr 16) and 0xFF).toDouble()
    private fun g(v: Int) = ((v shr 8) and 0xFF).toDouble()
    private fun b(v: Int) = (v and 0xFF).toDouble()
    private fun make(r: Double, g: Double, b: Double, a: Int): Int {
        fun c(x: Double) = max(0, min(255, x.roundToInt()))
        return (a shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
    fun dark(v: Int, k: Double): Int = make(r(v) * k, g(v) * k, b(v) * k, v ushr 24)
    fun mix(v: Int, hex: Int, t: Double): Int {
        val o = rgb(hex)
        return make(r(v) + (r(o) - r(v)) * t, g(v) + (g(o) - g(v)) * t, b(v) + (b(o) - b(v)) * t, v ushr 24)
    }
    fun luma(v: Int): Double = 0.3 * r(v) + 0.59 * g(v) + 0.11 * b(v)
    fun near(v: Int, hex: Int): Boolean { val o = rgb(hex); return abs(r(v) - r(o)) + abs(g(v) - g(o)) + abs(b(v) - b(o)) < 30 }

    /** 음영: 위에서 오는 빛 (Swift PixBuf.shade 와 같은 규칙) */
    fun shade(p: IntArray, w: Int, h: Int, head: Int = 16, legTop: Int = 28) {
        for (x in 0 until w) {
            val y = (0 until min(head, h)).firstOrNull { on(p[it * w + x]) } ?: continue
            p[y * w + x] = mix(p[y * w + x], 0xFFFFFF, 0.28)
        }
        for (y in 0 until h) {
            val xs = (0 until w).filter { on(p[y * w + it]) }
            val l = xs.firstOrNull() ?: continue
            val r = xs.last()
            if (r - l < 3) continue
            if (y < head) {
                p[y * w + r] = dark(p[y * w + r], 0.78)
            } else if (y > head && y < legTop) {
                p[y * w + l] = mix(p[y * w + l], 0xFFFFFF, 0.2)
                for (x in listOf(r, r - 1)) if (on(p[y * w + x])) p[y * w + x] = dark(p[y * w + x], 0.8)
            } else if (y >= legTop) {
                p[y * w + r] = dark(p[y * w + r], 0.8)
            }
        }
    }

    /** 바깥 테두리 한 줄 (빈 칸 중 상하좌우에 칠한 칸이 있으면 테두리색) */
    fun outline(p: IntArray, w: Int, h: Int) {
        val line = rgb(MeArt.outline)
        val f = BooleanArray(p.size) { on(p[it]) }
        for (y in 0 until h) for (x in 0 until w) {
            if (f[y * w + x]) continue
            if ((x > 0 && f[y * w + x - 1]) || (x < w - 1 && f[y * w + x + 1]) || (y > 0 && f[(y - 1) * w + x]) || (y < h - 1 && f[(y + 1) * w + x])) p[y * w + x] = line
        }
    }
}

// TODO(Views/CharMotion.swift MeMotion): 지금은 정면 서 있는 한 장(기본 CharScene)만. 방향·걷기·숨·포즈 장면은 CharMotion 단계에서
object MeMotion {
    const val legTop = 28
    private val cache = HashMap<MeChar, Bitmap?>()

    fun image(me: MeChar): Bitmap? {
        synchronized(cache) { if (cache.containsKey(me)) return cache[me] }
        val w = MeArt.W
        val h = MeArt.H
        val hair = MeColor.hair[MeColor.idx(me)].toInt()
        val skin = MeColor.skin[MeColor.skinIdx(me)].toInt()
        val p = IntArray(w * h)
        for (layer in MeGroup.layers) {
            val n = MeArt.option(me, layer)
            if (n <= 0) continue
            val src = mePixels(MeArt.asset(layer, n, me))
            if (src != null) {
                for (i in p.indices) if (PixBuf.on(src[i])) p[i] = src[i]
            } else {
                MePlaceholder.draw(layer, n, hair, skin) { x, y, pw, ph, c ->
                    for (yy in y until y + ph) for (xx in x until x + pw) if (xx in 0 until w && yy in 0 until h) p[yy * w + xx] = PixBuf.rgb(c)
                }
            }
        }
        // 볼 분홍 1px
        for (x in listOf(8, 15)) if (PixBuf.near(p[12 * w + x], skin)) p[12 * w + x] = PixBuf.mix(p[12 * w + x], 0xF08C8C, 0.5)
        // 눈 흰 반짝 1px
        for (xs in listOf(6 until 12, 12 until 19)) {
            val dots = ArrayList<Int>()
            var white = false
            for (y in 8..12) for (x in xs) {
                val v = p[y * w + x]
                if (!PixBuf.on(v)) continue
                if (PixBuf.luma(v) < 70) dots.add(y * w + x) else if (PixBuf.luma(v) > 238) white = true
            }
            if (dots.size >= 3 && !white) p[dots.first()] = PixBuf.rgb(0xF8F9FA)
        }
        PixBuf.shade(p, w, h, legTop = legTop)
        PixBuf.outline(p, w, h)
        val img = Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888)
        synchronized(cache) {
            if (cache.size > 400) cache.clear()
            cache[me] = img
        }
        return img
    }
}

// TODO(Views/CharMotion.swift MeTurntable): 돌리기·걷기 버튼과 숨쉬기 없이 서 있는 그림만 (아래 버튼 자리 26 은 비워 둔다)
@Composable
fun MeTurntable(me: MeChar, scale: Int) {
    Box(Modifier.padding(bottom = 26.dp)) { MeAvatar(me = me, width = (MeArt.W * scale).dp, height = (MeArt.H * scale).dp) }
}
