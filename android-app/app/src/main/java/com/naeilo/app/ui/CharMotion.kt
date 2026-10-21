package com.naeilo.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeColor
import com.naeilo.app.data.MeGroup
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.me.MePlaceholder
import com.naeilo.app.ui.me.meBitmap
import com.naeilo.app.ui.me.mePixels
import com.naeilo.app.ui.town.TownBook
import com.naeilo.app.ui.town.TownFace
import com.naeilo.app.ui.town.TownLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// 인물 움직임 (GBA 젤다처럼 살짝 위에서 본 3/4 시점).
// 그림 파일을 더 넣지 않고, 이미 있는 24×34 버퍼(내 캐릭터·주민·샘플)나 친구 포즈 그림(mo_*)에서
// 방향·걷기·숨쉬기 장면을 픽셀로 만들어 캐시한다. 화면은 작은 그림만 바꿔 끼우니 매 프레임 다시 그리지 않는다.
// 안드로이드: 버퍼 한 칸은 ARGB Int (0xAARRGGBB, 위 줄부터). Bitmap 과 같은 순서라 Swift 의 R·G·B·A 바이트 순서와 값 규칙만 같다.
// 장면 넘김(k)은 그리기 단계에서만 읽는다 (화면을 다시 짜지 않는다). 장면 그림은 입력이 바뀔 때 한 번 만들어 둔다.

/** 보는 방향: 아래(정면)·위(뒷모습)·왼·오른 */
// ios: Views/CharMotion.swift:9
enum class CharDir {
    down, up, left, right;   // ios: Views/CharMotion.swift:10

    /** 돌리기: 아래 → 왼 → 위 → 오른 (k = -1 이면 거꾸로) */
    // ios: Views/CharMotion.swift:16
    fun turned(k: Int): CharDir {
        val ring: List<CharDir> = listOf(down, left, up, right)   // ios: Views/CharMotion.swift:17
        val i = ring.indexOf(this).takeIf { it >= 0 } ?: 0   // ios: Views/CharMotion.swift:18
        return ring[(i + k + 4) % 4]   // ios: Views/CharMotion.swift:19
    }

    companion object {
        /** 움직인 쪽으로 돌아선다 */
        // ios: Views/CharMotion.swift:12
        fun of(dx: Float, dy: Float): CharDir =
            if (abs(dx) >= abs(dy)) (if (dx < 0) CharDir.left else CharDir.right) else (if (dy < 0) CharDir.up else CharDir.down)   // ios: Views/CharMotion.swift:13
    }
}

/** 한 장면: 방향 + 다리(0 서기 · 1 왼발 · 2 오른발 1px 들기) + 윗몸 1px 들썩(걷기·숨쉬기) */
// ios: Views/CharMotion.swift:24
data class CharScene(
    val dir: CharDir = CharDir.down,   // ios: Views/CharMotion.swift:25
    val leg: Int = 0,   // ios: Views/CharMotion.swift:26
    val lift: Boolean = false,   // ios: Views/CharMotion.swift:27
    /** 머리 위 빈 2줄: 들썩여도 머리 윗줄·테두리가 잘리지 않게 (SceneSprite 로 그리면 틀 위로 넘친다) */
    val room: Boolean = false,   // ios: Views/CharMotion.swift:29
    /** 파도 타기 포즈 (기본은 그대로) */
    val pose: CharPose = CharPose.none,   // ios: Views/CharMotion.swift:31
) {
    companion object {
        /** k 번째 칸: 걸을 땐 서기·왼발·서기·오른발, 서 있을 땐 숨 두 장 */
        // ios: Views/CharMotion.swift:33
        fun at(k: Int, dir: CharDir, walking: Boolean): CharScene =
            if (walking) CharScene(dir = dir, leg = listOf(0, 1, 0, 2)[Math.floorMod(k, 4)], lift = Math.floorMod(k, 2) == 1, room = true)   // ios: Views/CharMotion.swift:34
            else CharScene(dir = dir, leg = 0, lift = Math.floorMod(k, 2) == 1, room = true)   // ios: Views/CharMotion.swift:35
    }
}

/** 파도 타기 포즈: 정면 버퍼(테두리 전)에서 눈·팔·다리만 몇 칸 바꾼다. 새 그림 없음 */
// ios: Views/CharMotion.swift:40
enum class CharPose {
    /** crouch 무릎 굽힘 · cheer 팔 벌리고 ^^ · dizzy 눈 ×× · yawn 보드에 앉아 하품 */
    none, crouch, cheer, dizzy, yawn;   // ios: Views/CharMotion.swift:42

    /** 다리를 접은 줄 수 (모자·왕관을 같이 내린다) */
    val sink: Int get() = if (this == crouch) 2 else if (this == yawn) 4 else 0   // ios: Views/CharMotion.swift:44

    companion object {
        // ios: Views/CharMotion.swift:46
        fun apply(pose: CharPose, p: IntArray, w: Int, h: Int, skin: Int, legTop: Int) {
            val ink = PixBuf.rgb(MeArt.outline); val sk = PixBuf.rgb(skin)   // ios: Views/CharMotion.swift:47
            fun put(x: Int, y: Int, c: Int) { if (x >= 0 && x < w && y >= 0 && y < h) p[y * w + x] = c }   // ios: Views/CharMotion.swift:48
            /** 눈 두 개를 피부로 지우고 가운데 자리를 돌려준다 (새 눈은 거기 그린다) */
            // ios: Views/CharMotion.swift:50
            fun eyes(): List<Pair<Int, Int>> {
                val cs = ArrayList<Pair<Int, Int>>()   // ios: Views/CharMotion.swift:51
                for (xs in listOf(6 until 12, 12 until 19)) {   // ios: Views/CharMotion.swift:52
                    val pts = ArrayList<Pair<Int, Int>>()   // ios: Views/CharMotion.swift:53
                    for (y in 8..12) { for (x in xs) if (PixBuf.on(p[y * w + x]) && PixBuf.luma(p[y * w + x]) < 70) pts.add(x to y) }   // ios: Views/CharMotion.swift:54
                    if (pts.isEmpty()) continue   // ios: Views/CharMotion.swift:55
                    val x0 = pts.minOf { it.first }; val x1 = pts.maxOf { it.first }; val y0 = pts.minOf { it.second }; val y1 = pts.maxOf { it.second }   // ios: Views/CharMotion.swift:56
                    for (y in y0..y1) { for (x in x0..x1) if (PixBuf.on(p[y * w + x])) p[y * w + x] = sk }   // ios: Views/CharMotion.swift:57
                    cs.add(((x0 + x1) / 2) to ((y0 + y1 + 1) / 2))   // ios: Views/CharMotion.swift:58
                }
                return cs   // ios: Views/CharMotion.swift:60
            }
            /** 다리 n줄을 접어 몸을 n줄 내린다 */
            // ios: Views/CharMotion.swift:63
            fun fold(n: Int) {
                val r = legTop + 1   // ios: Views/CharMotion.swift:64
                for (y in (r + n - 1) downTo n step 1) { for (x in 0 until w) p[y * w + x] = p[(y - n) * w + x] }   // ios: Views/CharMotion.swift:65 stride(by: -1)
                for (i in 0 until (n * w)) p[i] = 0   // ios: Views/CharMotion.swift:66
            }
            when (pose) {   // ios: Views/CharMotion.swift:68
                CharPose.none -> return   // ios: Views/CharMotion.swift:69
                CharPose.crouch -> fold(2)   // ios: Views/CharMotion.swift:70
                CharPose.cheer -> {   // ios: Views/CharMotion.swift:71
                    for ((x, y) in eyes()) { put(x - 1, y, ink); put(x, y - 1, ink); put(x + 1, y, ink) }   // ios: Views/CharMotion.swift:72
                    // 팔: 어깨에서 바깥 위로 세 칸 + 손
                    val y = 17; val xs = (0 until w).filter { PixBuf.on(p[y * w + it]) }   // ios: Views/CharMotion.swift:74
                    val l = xs.firstOrNull(); val r = xs.lastOrNull()   // ios: Views/CharMotion.swift:75
                    if (l != null && r != null) {
                        val shirt = p[y * w + l]   // ios: Views/CharMotion.swift:76
                        for (k in 1..3) { put(l - k, y - k, if (k < 3) shirt else sk); put(r + k, y - k, if (k < 3) shirt else sk) }   // ios: Views/CharMotion.swift:77
                        put(l - 3, y - 4, sk); put(r + 3, y - 4, sk)   // ios: Views/CharMotion.swift:78
                    }
                }
                CharPose.dizzy ->   // ios: Views/CharMotion.swift:80
                    for ((x, y) in eyes()) { for (d in -1..1) { put(x + d, y + d, ink); put(x + d, y - d, ink) } }   // ios: Views/CharMotion.swift:81
                CharPose.yawn -> {   // ios: Views/CharMotion.swift:82
                    for ((x, y) in eyes()) { for (d in -1..1) put(x + d, y, ink) }   // ios: Views/CharMotion.swift:83
                    for (x in 11..12) if (PixBuf.on(p[13 * w + x])) p[13 * w + x] = PixBuf.rgb(0x9A3B3B)   // ios: Views/CharMotion.swift:84
                    fold(4)   // ios: Views/CharMotion.swift:85
                }
            }
        }
    }
}

/** ARGB 버퍼(한 칸 Int 0xAARRGGBB, 위 줄부터) 다루기. 값 규칙은 Swift PixBuf 와 같다 */
// ios: Views/CharMotion.swift:91
object PixBuf {
    fun on(v: Int): Boolean = v ushr 24 != 0   // ios: Views/CharMotion.swift:92
    /** 0xRRGGBB → 불투명 칸 (Swift 는 바이트 순서를 바꾸지만 안드로이드 ARGB 는 그대로 0xFF 만 붙인다) */
    fun rgb(hex: Int): Int = (0xFF shl 24) or ((hex shr 16 and 0xFF) shl 16) or (hex and 0xFF00) or (hex and 0xFF)   // ios: Views/CharMotion.swift:93
    private fun parts(v: Int): Triple<Double, Double, Double> = Triple(((v shr 16) and 0xFF).toDouble(), ((v shr 8) and 0xFF).toDouble(), (v and 0xFF).toDouble())   // ios: Views/CharMotion.swift:94
    // ios: Views/CharMotion.swift:95
    private fun make(r: Double, g: Double, b: Double, a: Int): Int {
        fun c(x: Double): Int = max(0.0, min(255.0, Math.round(x).toDouble())).toInt()   // ios: Views/CharMotion.swift:96 (.rounded = 반올림, 0.5 는 위로)
        return (a shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)   // ios: Views/CharMotion.swift:97
    }
    /** k배 어둡게 (k<1) */
    fun dark(v: Int, k: Double): Int { val (r, g, b) = parts(v); return make(r * k, g * k, b * k, v ushr 24) }   // ios: Views/CharMotion.swift:100
    /** 색 쪽으로 t만큼 섞기 */
    // ios: Views/CharMotion.swift:102
    fun mix(v: Int, hex: Int, t: Double): Int {
        val (r, g, b) = parts(v); val (R, G, B) = parts(rgb(hex))   // ios: Views/CharMotion.swift:103
        return make(r + (R - r) * t, g + (G - g) * t, b + (B - b) * t, v ushr 24)   // ios: Views/CharMotion.swift:104
    }
    fun luma(v: Int): Double { val (r, g, b) = parts(v); return 0.3 * r + 0.59 * g + 0.11 * b }   // ios: Views/CharMotion.swift:106
    // ios: Views/CharMotion.swift:107
    fun near(v: Int, hex: Int): Boolean {
        val (r, g, b) = parts(v); val (R, G, B) = parts(rgb(hex))   // ios: Views/CharMotion.swift:108
        return abs(r - R) + abs(g - G) + abs(b - B) < 30   // ios: Views/CharMotion.swift:109
    }

    /** 음영: 위에서 오는 빛. 머리 윗면 밝게·오른쪽 어둡게, 윗옷 왼쪽 1열 밝게·오른쪽 2열 어둡게, 다리 오른쪽 1열 어둡게 */
    // ios: Views/CharMotion.swift:113
    fun shade(p: IntArray, w: Int, h: Int, head: Int = 16, legTop: Int = 28) {
        for (x in 0 until w) {   // ios: Views/CharMotion.swift:114
            val y = (0 until min(head, h)).firstOrNull { on(p[it * w + x]) }; if (y != null) p[y * w + x] = mix(p[y * w + x], 0xFFFFFF, 0.28)   // ios: Views/CharMotion.swift:115
        }
        for (y in 0 until h) {   // ios: Views/CharMotion.swift:117
            val xs = (0 until w).filter { on(p[y * w + it]) }   // ios: Views/CharMotion.swift:118
            val l = xs.firstOrNull(); val r = xs.lastOrNull(); if (l == null || r == null || r - l < 3) continue   // ios: Views/CharMotion.swift:119 (r - l >= 3 이 아니면 건너뜀)
            if (y < head) {   // ios: Views/CharMotion.swift:120
                p[y * w + r] = dark(p[y * w + r], 0.78)   // ios: Views/CharMotion.swift:121
            } else if (y > head && y < legTop) {   // ios: Views/CharMotion.swift:122
                p[y * w + l] = mix(p[y * w + l], 0xFFFFFF, 0.2)   // ios: Views/CharMotion.swift:123
                for (x in listOf(r, r - 1)) if (on(p[y * w + x])) p[y * w + x] = dark(p[y * w + x], 0.8)   // ios: Views/CharMotion.swift:124
            } else if (y >= legTop) {   // ios: Views/CharMotion.swift:125
                p[y * w + r] = dark(p[y * w + r], 0.8)   // ios: Views/CharMotion.swift:126
            }
        }
    }
    /** 다리 한쪽(1 왼쪽 반 · 2 오른쪽 반)을 1px 들어 올린다 (발이 올라오고 다리는 1px 짧아짐) */
    // ios: Views/CharMotion.swift:131
    fun legs(p: IntArray, w: Int, h: Int, leg: Int, top: Int, split: Int) {
        if (leg <= 0) return   // ios: Views/CharMotion.swift:132 guard leg > 0
        val xs = if (leg == 1) 0 until split else split until w   // ios: Views/CharMotion.swift:133
        for (x in xs) {   // ios: Views/CharMotion.swift:134
            for (y in (top + 1) until (h - 1)) p[y * w + x] = p[(y + 1) * w + x]   // ios: Views/CharMotion.swift:135
            p[(h - 1) * w + x] = 0   // ios: Views/CharMotion.swift:136
        }
    }
    /** 맨 위에 빈 줄 n개를 붙인다 (IntArray 는 길이가 정해져 있어 새 버퍼를 돌려준다) */
    fun pad(p: IntArray, w: Int, top: Int): IntArray = if (top == 0) p else IntArray(top * w + p.size).also { p.copyInto(it, top * w, 0) }   // ios: Views/CharMotion.swift:140 repeatElement(0, count: n * w)
    /** 윗몸(허리 위)을 1px 올린다. 허리 줄은 한 줄 늘어난다 */
    // ios: Views/CharMotion.swift:142
    fun lift(p: IntArray, w: Int, until: Int) {
        for (y in 0 until (until - 1)) { for (x in 0 until w) p[y * w + x] = p[(y + 1) * w + x] }   // ios: Views/CharMotion.swift:143
    }
    // ios: Views/CharMotion.swift:145
    fun mirror(p: IntArray, w: Int, h: Int) {
        for (y in 0 until h) { for (x in 0 until (w / 2)) { val a = y * w + x; val b = y * w + w - 1 - x; val t = p[a]; p[a] = p[b]; p[b] = t } }   // ios: Views/CharMotion.swift:146 swapAt
    }
    /** 바깥 테두리 한 줄 (MeArt 와 같은 규칙: 빈 칸 중 상하좌우에 칠한 칸이 있으면 테두리색) */
    // ios: Views/CharMotion.swift:149
    fun outline(p: IntArray, w: Int, h: Int) {
        val line = rgb(MeArt.outline); val f = BooleanArray(p.size) { on(p[it]) }   // ios: Views/CharMotion.swift:150
        for (y in 0 until h) {   // ios: Views/CharMotion.swift:151
            for (x in 0 until w) { if (f[y * w + x]) continue   // ios: Views/CharMotion.swift:152
                if ((x > 0 && f[y * w + x - 1]) || (x < w - 1 && f[y * w + x + 1]) || (y > 0 && f[(y - 1) * w + x]) || (y < h - 1 && f[(y + 1) * w + x])) {   // ios: Views/CharMotion.swift:153
                    p[y * w + x] = line   // ios: Views/CharMotion.swift:154
                }
            }
        }
    }
    /** 그림을 w×h 버퍼로 (보간 없이, 크기가 다르면 최근접). draw 는 Swift 처럼 받기만 하고 쓰지 않는다 */
    // ios: Views/CharMotion.swift:159
    fun read(cg: Bitmap, w: Int, h: Int, @Suppress("UNUSED_PARAMETER") draw: ((android.graphics.Canvas) -> Unit)? = null): IntArray {
        val p = IntArray(w * h) { 0 }   // ios: Views/CharMotion.swift:160 repeating: 0
        // ios: Views/CharMotion.swift:161,163 (CGContext 자리: ARGB_8888 버퍼에 그대로 읽는다)
        // Views/CharMotion.swift:162 (검사 예외: CGContext 비트 수 8·한 줄 바이트 w*4 = ARGB_8888 에 정해져 있음)
        val s = if (cg.width == w && cg.height == h) cg else Bitmap.createScaledBitmap(cg, w, h, false)   // ios: Views/CharMotion.swift:164 interpolationQuality = .none
        s.getPixels(p, 0, w, 0, 0, w, h)   // ios: Views/CharMotion.swift:165 draw(cg, in: CGRect(x: 0, y: 0, ...))
        return p   // ios: Views/CharMotion.swift:167
    }
    // ios: Views/CharMotion.swift:169
    fun image(p: IntArray, w: Int, h: Int): Bitmap? {
        val q = p   // ios: Views/CharMotion.swift:170
        // ios: Views/CharMotion.swift:171,173 (CGContext → makeImage = ARGB_8888 Bitmap)
        // Views/CharMotion.swift:172 (검사 예외: CGContext 비트 수 8·한 줄 바이트 w*4 = ARGB_8888 에 정해져 있음)
        val cg: Bitmap? = runCatching { Bitmap.createBitmap(q, w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
        return cg   // ios: Views/CharMotion.swift:175
    }
}

/** 내 캐릭터·주민·샘플: 부품을 24×34 에 겹친 뒤 방향·음영·볼·눈 반짝·다리·숨을 입힌다 */
// ios: Views/CharMotion.swift:180
object MeMotion {
    private data class Key(val me: MeChar, val f: CharScene)   // ios: Views/CharMotion.swift:181
    private val cache = HashMap<Key, Bitmap?>()   // ios: Views/CharMotion.swift:182
    /** 얼굴 부품: 뒷모습에선 뺀다. 옆모습은 부품마다 옆모습 그림(_side, tools/gen_me_side.py)을 쓴다 (없으면 보는 쪽으로 1px) */
    private val facial: Set<String> = setOf("eyes", "mouth", "mask", "glasses")   // ios: Views/CharMotion.swift:184
    const val legTop = 28   // ios: Views/CharMotion.swift:185

    /** 장면 그림. 처음 한 번만 만들고 캐시 (아무 스레드에서나). 화면은 remember 로 입력이 바뀔 때만 부른다 */
    // ios: Views/CharMotion.swift:187
    fun image(me: MeChar, f: CharScene = CharScene()): Bitmap? {
        val key = Key(me = me, f = f)   // ios: Views/CharMotion.swift:188
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }   // ios: Views/CharMotion.swift:189
        val w = MeArt.W; val h = MeArt.H   // ios: Views/CharMotion.swift:190
        val hair = MeColor.hair[MeColor.idx(me)].toInt(); val skin = MeColor.skin[MeColor.skinIdx(me)].toInt()   // ios: Views/CharMotion.swift:191
        val side = f.dir == CharDir.left || f.dir == CharDir.right   // ios: Views/CharMotion.swift:192
        var p = IntArray(w * h) { 0 }   // ios: Views/CharMotion.swift:193 repeating: 0
        // ios: Views/CharMotion.swift:194,196,197 (CGContext 대신 버퍼에 바로 겹친다. 보간 없음)
        // Views/CharMotion.swift:195 (검사 예외: CGContext 비트 수 8·한 줄 바이트 w*4 = ARGB_8888 에 정해져 있음)
        for (layer in MeGroup.layers) {   // ios: Views/CharMotion.swift:198
            val n = MeArt.option(me, layer)   // ios: Views/CharMotion.swift:199
            if (n <= 0 || (f.dir == CharDir.up && facial.contains(layer))) continue   // ios: Views/CharMotion.swift:200 guard n > 0
            val name = MeArt.asset(layer, n, me); val sideArt = side && MeArt.has(name + "_side")   // ios: Views/CharMotion.swift:201
            val dx = if (side && !sideArt && facial.contains(layer)) 1 else 0   // ios: Views/CharMotion.swift:202
            val img = mePixels(if (sideArt) name + "_side" else name)   // ios: Views/CharMotion.swift:203
            if (img != null) {
                for (y in 0 until h) for (x in 0 until w) { val t = x + dx; if (t < w) p[y * w + t] = over(p[y * w + t], img[y * w + x]) }   // ios: Views/CharMotion.swift:204 CGRect(x: dx, y: 0, width: w, height: h)
            } else {   // ios: Views/CharMotion.swift:205
                MePlaceholder.draw(layer, n, hair, skin) { x, y, pw, ph, c ->   // ios: Views/CharMotion.swift:206
                    val col = Color(red = ((c shr 16) and 0xFF) / 255f, green = ((c shr 8) and 0xFF) / 255f, blue = (c and 0xFF) / 255f, alpha = 1f).toArgb()   // ios: Views/CharMotion.swift:207
                    for (yy in y until y + ph) for (xx in x + dx until x + dx + pw) if (xx in 0 until w && yy in 0 until h) p[yy * w + xx] = col   // ios: Views/CharMotion.swift:208 (CG 의 h - y - ph 는 위 줄부터 y)
                }
            }
        }
        if (f.dir == CharDir.up) {   // ios: Views/CharMotion.swift:213
            // 뒷모습: 머리(0~13줄)만 머리색으로 (머리 부품이 없는 유명인은 피부를 살짝 어둡게). 목(14줄)·윗옷(15줄~)은 그대로
            val back = if (me.part(MeGroup.hair) > 0) hair else skin; val c = PixBuf.rgb(back)   // ios: Views/CharMotion.swift:215
            // 긴 머리는 등 뒤로 내려오니 그 줄의 머리 사이(윗옷 위)도 머리색으로 메운다
            for (y in 14 until min(h, 24)) {   // ios: Views/CharMotion.swift:217
                val xs = (0 until w).filter { PixBuf.near(p[y * w + it], hair) }   // ios: Views/CharMotion.swift:218
                val a = xs.firstOrNull(); val b = xs.lastOrNull(); if (me.part(MeGroup.hair) <= 0 || a == null || b == null || b - a <= 3) continue   // ios: Views/CharMotion.swift:219 guard … b - a > 3
                for (x in a..b) if (PixBuf.on(p[y * w + x])) p[y * w + x] = c   // ios: Views/CharMotion.swift:220
            }
            for (y in 0..13) { for (x in 0 until w) if (PixBuf.on(p[y * w + x])) {   // ios: Views/CharMotion.swift:222
                p[y * w + x] = if (y >= 12) PixBuf.dark(c, 0.85) else c    // ios: Views/CharMotion.swift:223 목덜미 쪽 두 줄은 한 단 어둡게
            } }
            // 귀 살짝 (머리 양옆 9~10줄), 정수리 밝은 점 몇 개
            for (y in 9..10) { for (x in listOf(5, 18)) if (PixBuf.on(p[y * w + x])) p[y * w + x] = PixBuf.dark(PixBuf.rgb(skin), 0.9) }   // ios: Views/CharMotion.swift:226
            for ((x, y) in listOf(8 to 3, 9 to 3, 10 to 3, 8 to 5)) if (PixBuf.on(p[y * w + x])) p[y * w + x] = PixBuf.mix(c, 0xFFFFFF, 0.3)   // ios: Views/CharMotion.swift:227
        } else {   // ios: Views/CharMotion.swift:228
            // 볼 분홍 1px (옆모습은 앞쪽 볼 하나)
            for (x in if (side) listOf(16) else listOf(8, 15)) if (PixBuf.near(p[12 * w + x], skin)) p[12 * w + x] = PixBuf.mix(p[12 * w + x], 0xF08C8C, 0.5)   // ios: Views/CharMotion.swift:230
            // 눈 흰 반짝 1px: 눈동자가 3칸 이상이고 아직 흰 점이 없을 때 왼쪽 위 칸
            for (xs in listOf(6 until 12, 12 until 19)) {   // ios: Views/CharMotion.swift:232
                val dots = ArrayList<Int>(); var white = false   // ios: Views/CharMotion.swift:233
                for (y in 8..12) { for (x in xs) {   // ios: Views/CharMotion.swift:234
                    val v = p[y * w + x]   // ios: Views/CharMotion.swift:235
                    if (!PixBuf.on(v)) continue   // ios: Views/CharMotion.swift:236
                    if (PixBuf.luma(v) < 70) dots.add(y * w + x) else if (PixBuf.luma(v) > 238) white = true   // ios: Views/CharMotion.swift:237
                } }
                if (dots.size >= 3 && !white) p[dots.first()] = PixBuf.rgb(0xF8F9FA)   // ios: Views/CharMotion.swift:239
            }
        }
        if (f.pose != CharPose.none) CharPose.apply(f.pose, p, w, h, skin = skin, legTop = legTop)   // ios: Views/CharMotion.swift:242
        PixBuf.shade(p, w, h, legTop = legTop)   // ios: Views/CharMotion.swift:243
        PixBuf.legs(p, w, h, f.leg, top = legTop, split = w / 2)   // ios: Views/CharMotion.swift:244
        val top = if (f.room) 2 else 0   // ios: Views/CharMotion.swift:245
        p = PixBuf.pad(p, w, top = top)   // ios: Views/CharMotion.swift:246
        if (f.lift) PixBuf.lift(p, w, until = legTop + top)   // ios: Views/CharMotion.swift:247
        if (f.dir == CharDir.left) PixBuf.mirror(p, w, h + top)   // ios: Views/CharMotion.swift:248
        PixBuf.outline(p, w, h + top)   // ios: Views/CharMotion.swift:249
        val img = PixBuf.image(p, w, h + top)   // ios: Views/CharMotion.swift:250
        synchronized(cache) {
            if (cache.size > 400) cache.clear()   // ios: Views/CharMotion.swift:251
            cache[key] = img   // ios: Views/CharMotion.swift:252
        }
        return img   // ios: Views/CharMotion.swift:253
    }

    /** CGContext.draw 의 겹치기(source-over). 도트 그림은 거의 0·255 라 대부분 그대로 덮는다 */
    private fun over(dst: Int, src: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        val a = sa + da * (255 - sa) / 255
        if (a == 0) return 0
        fun ch(sh: Int): Int = (((src shr sh) and 0xFF) * sa + ((dst shr sh) and 0xFF) * da * (255 - sa) / 255) / a
        return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** 친구 다섯: 이미 있는 포즈 그림 mo_<id>_stand·back·side_l·side (30×35 도트를 8배)에서 걷기·숨 장면을 만든다 */
// ios: Views/CharMotion.swift:258
object FriendMotion {
    const val W = 30; const val H = 35; const val legTop = 27   // ios: Views/CharMotion.swift:259
    private data class Key(val id: String, val f: CharScene)   // ios: Views/CharMotion.swift:260
    private val cache = HashMap<Key, Bitmap?>()   // ios: Views/CharMotion.swift:261
    fun has(id: String): Boolean = MeArt.has("mo_${id}_stand")   // ios: Views/CharMotion.swift:262
    // ios: Views/CharMotion.swift:263
    fun image(id: String, f: CharScene): Bitmap? {
        val key = Key(id = id, f = f)   // ios: Views/CharMotion.swift:264
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }   // ios: Views/CharMotion.swift:265
        val name = "mo_${id}_" + listOf("stand", "back", "side_l", "side")[f.dir.ordinal]   // ios: Views/CharMotion.swift:266
        val cg = meBitmap(name) ?: return null   // ios: Views/CharMotion.swift:267
        var p = PixBuf.read(cg, W, H)   // ios: Views/CharMotion.swift:268
        // 테두리까지 그려진 그림이라 다리·윗몸만 옮긴다
        PixBuf.legs(p, W, H, f.leg, top = legTop, split = W / 2)   // ios: Views/CharMotion.swift:270
        val top = if (f.room) 2 else 0   // ios: Views/CharMotion.swift:271
        p = PixBuf.pad(p, W, top = top)   // ios: Views/CharMotion.swift:272
        if (f.lift) PixBuf.lift(p, W, until = legTop + top)   // ios: Views/CharMotion.swift:273
        val img = PixBuf.image(p, W, H + top)   // ios: Views/CharMotion.swift:274
        synchronized(cache) {
            if (cache.size > 200) cache.clear()   // ios: Views/CharMotion.swift:275
            cache[key] = img   // ios: Views/CharMotion.swift:276
        }
        return img   // ios: Views/CharMotion.swift:277
    }
}

/** 장면 그림을 틀(원래 칸수 기준 width×height)에 그린다. 머리 위 여유 줄은 틀 위로 넘친다 */
// ios: Views/CharMotion.swift:282,283,284,285,286,287
@Composable
fun SceneSprite(img: Bitmap, width: Dp, height: Dp, rows: Int, modifier: Modifier = Modifier) {
    val b = remember(img) { img.asImageBitmap() }
    SceneSprite(frame = { b }, width = width, height = height, rows = rows, modifier = modifier)
}

/** SceneSprite 의 그리기 단계 판: 장면(frame)을 그릴 때만 읽어 화면을 다시 짜지 않는다 */
@Composable
fun SceneSprite(frame: () -> ImageBitmap?, width: Dp, height: Dp, rows: Int, modifier: Modifier = Modifier) {
    // ios: Views/CharMotion.swift:288 interpolation(.none) = FilterQuality.None, resizable
    Canvas(modifier.size(width, height)) {   // ios: Views/CharMotion.swift:290 frame(width: width, height: height, alignment: .bottom)
        val img = frame() ?: return@Canvas
        val dh = size.height * img.height / rows.toFloat()   // ios: Views/CharMotion.swift:289 height * img.size.height / CGFloat(rows)
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(0, (size.height - dh).roundToInt()),
            IntSize(size.width.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.None)
    }
}

/** 장면 시계: 걸을 땐 0.15초, 서 있을 땐 인물마다 다른 박자(0.55~0.95초)로 한 칸 (화면에 있을 때만 돈다).
 *  content 는 k 를 함수로 받아 그리기 단계(graphicsLayer·Canvas)에서만 읽는다 */
// ios: Views/CharMotion.swift:295
@Composable
fun CharClock(
    walking: Boolean,   // ios: Views/CharMotion.swift:296
    breathe: Boolean = true,   // ios: Views/CharMotion.swift:297
    @Suppress("UNUSED_PARAMETER") seed: Int = 0,   // ios: Views/CharMotion.swift:298 (iOS 도 박자에 안 쓴다)
    content: @Composable (k: () -> Int) -> Unit,   // ios: Views/CharMotion.swift:299
) {
    val k = remember { mutableIntStateOf(0) }   // ios: Views/CharMotion.swift:300 @State private var k = 0
    val reduce = LocalReduceMotion.current
    val active = LocalTabActive.current
    content { k.intValue }   // ios: Views/CharMotion.swift:301,302
    // ios: Views/CharMotion.swift:303 .task(id: walking ? 1 : breathe ? 2 : 0) — 숨은 탭·동작 줄이기면 멈춘다
    androidx.compose.runtime.LaunchedEffect(if (walking) 1 else if (breathe) 2 else 0, reduce, active) {
        if (!(walking || breathe)) { k.intValue = 0; return@LaunchedEffect }   // ios: Views/CharMotion.swift:304
        if (reduce || !active) return@LaunchedEffect
        // 숨은 인물마다 빠르기·시작을 다르게, 숨마다 조금씩 흔들어 다 같이 들썩이지 않게
        val beat = Random.nextDouble(0.55, 0.95)   // ios: Views/CharMotion.swift:306
        if (!walking) delay((Random.nextDouble(0.0, beat) * 1000).toLong())   // ios: Views/CharMotion.swift:307
        while (true) {   // ios: Views/CharMotion.swift:308 (취소되면 delay 에서 끝난다)
            delay(((if (walking) 0.15 else beat * Random.nextDouble(0.8, 1.25)) * 1000).toLong())   // ios: Views/CharMotion.swift:309
            k.intValue += 1   // ios: Views/CharMotion.swift:310 &+= 1 (넘치면 돈다)
        }
    }
}

/** 발밑 그림자: 반투명 타원 (도트 16×5 비율). dot = 한 도트 크기(dp) */
// ios: Views/CharMotion.swift:317,318,319
@Composable
fun CharShadow(dot: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier
        .offset(y = dot * 2)   // ios: Views/CharMotion.swift:322
        .requiredSize(dot * 16, dot * 5)   // ios: Views/CharMotion.swift:321
    ) { drawOval(Color.Black.copy(alpha = 0.22f)) }   // ios: Views/CharMotion.swift:320,323 Ellipse (Canvas 는 누름을 받지 않는다)
}

/** .background(alignment: .bottom) { CharShadow(dot:) }: 그림자를 틀 아래 가운데, 인물 뒤에 */
@Composable
private fun WithShadow(dot: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) { CharShadow(dot) }
        content()
    }
}

/** 동네 주민 한 명: 방향·걷기·숨 + 그림자. 모자·유명인 머리는 TownFace 가 같이 그린다.
 *  장면(걷기 4장·숨 2장)마다 TownFace 를 한 번씩 짜 두고, 박자마다 보이는 장만 그리기 단계에서 바꾼다 */
// ios: Views/CharMotion.swift:328
@Composable
fun TownWalker(
    id: String,   // ios: Views/CharMotion.swift:329
    note: TownBook.Note,   // ios: Views/CharMotion.swift:330
    dir: CharDir,   // ios: Views/CharMotion.swift:331
    walking: Boolean,   // ios: Views/CharMotion.swift:332
    width: Dp = 48.dp,   // ios: Views/CharMotion.swift:333
    height: Dp = 68.dp,   // ios: Views/CharMotion.swift:334
    modifier: Modifier = Modifier,
) {
    val seed = remember(id) { java.lang.Long.remainderUnsigned(TownLook.seed(id).toLong(), 97L).toInt() }   // ios: Views/CharMotion.swift:336 seed(id) % 97
    WithShadow(width / MeArt.W, modifier) {   // ios: Views/CharMotion.swift:335,342 CharShadow(dot: width / CGFloat(MeArt.W))
        CharClock(walking = walking, seed = seed) { k ->
            // 왼쪽은 오른쪽 장면을 뒤집어 모자까지 같이 돌린다
            val n = if (walking) 4 else 2
            Box {
                for (i in 0 until n) {
                    val f = CharScene.at(i, dir = if (dir == CharDir.left) CharDir.right else dir, walking = walking)   // ios: Views/CharMotion.swift:338
                    Box(Modifier.graphicsLayer {
                        alpha = if (Math.floorMod(k(), n) == i) 1f else 0f
                        scaleX = if (dir == CharDir.left) -1f else 1f; scaleY = 1f   // ios: Views/CharMotion.swift:340
                    }) { TownFace(id = id, note = note, width = width, height = height, frame = f) }   // ios: Views/CharMotion.swift:339
                }
            }
        }
    }
}

/** 크게 보이는 인물(쉼터·만들기 화면): 서 있을 땐 숨쉬기, 누르면 한 바퀴 돌며 걷는다.
 *  me 가 있으면 부품 합성, 없으면 친구 id 의 포즈 그림. dot = 한 도트 크기(dp) */
// ios: Views/CharMotion.swift:348
@Composable
fun CharFigure(
    me: MeChar? = null,   // ios: Views/CharMotion.swift:349
    friend: String? = null,   // ios: Views/CharMotion.swift:350
    dot: Dp,   // ios: Views/CharMotion.swift:351
    dir: CharDir = CharDir.down,   // ios: Views/CharMotion.swift:352
    walking: Boolean = false,   // ios: Views/CharMotion.swift:353
    /** 누르면 아래·왼·위·오른으로 걸으며 한 바퀴 (바깥에서 방향을 정하면 끈다) */
    tapToTurn: Boolean = true,   // ios: Views/CharMotion.swift:355
    modifier: Modifier = Modifier,
) {
    var spin by remember { mutableStateOf<CharDir?>(null) }   // ios: Views/CharMotion.swift:356 @State private var spin
    var spinning by remember { mutableStateOf(false) }   // ios: Views/CharMotion.swift:357 @State private var spinning
    val scope = rememberCoroutineScope()
    val d = spin ?: dir; val walk = walking || spinning   // ios: Views/CharMotion.swift:358,359
    val w = if (me != null) MeArt.W else FriendMotion.W; val h = if (me != null) MeArt.H else FriendMotion.H   // ios: Views/CharMotion.swift:360
    // 장면 그림은 (인물·방향·걷기)가 바뀔 때만 만든다. 박자마다는 고르기만
    val frames: List<ImageBitmap?> = remember(me, friend, d, walk) {
        (0 until if (walk) 4 else 2).map { i ->
            val f = CharScene.at(i, dir = d, walking = walk)   // ios: Views/CharMotion.swift:362
            val img = me?.let { MeMotion.image(it, f) } ?: friend?.let { FriendMotion.image(it, f) }   // ios: Views/CharMotion.swift:363
            img?.asImageBitmap()
        }
    }
    val src = remember { MutableInteractionSource() }
    WithShadow(dot, modifier   // ios: Views/CharMotion.swift:367 CharShadow(dot: dot)
        .clickable(src, indication = null) {   // ios: Views/CharMotion.swift:368,369 contentShape(Rectangle()) + onTapGesture
            if (!tapToTurn || spinning) return@clickable   // ios: Views/CharMotion.swift:370
            spinning = true   // ios: Views/CharMotion.swift:371
            scope.launch {   // ios: Views/CharMotion.swift:372
                for (k in 1..4) {   // ios: Views/CharMotion.swift:373
                    spin = (spin ?: dir).turned(1)   // ios: Views/CharMotion.swift:374
                    delay(600_000_000L / 1_000_000)   // ios: Views/CharMotion.swift:375 nanoseconds → ms
                    if (k == 4) spin = null   // ios: Views/CharMotion.swift:376
                }
                spinning = false   // ios: Views/CharMotion.swift:378
            }
        }) {
        CharClock(walking = walk, seed = (friend ?: "me").length) { k ->   // ios: Views/CharMotion.swift:361
            if (frames.any { it != null }) SceneSprite(frame = { frames[Math.floorMod(k(), frames.size)] }, width = dot * w, height = dot * h, rows = h)   // ios: Views/CharMotion.swift:364
            else Box(Modifier.size(dot * w, dot * h))   // ios: Views/CharMotion.swift:365 Color.clear.frame
        }
    }
}

/** 만들기 화면 큰 미리보기: 좌우 돌리기 버튼 · 걷기 토글 · 서 있을 땐 숨쉬기 */
// ios: Views/CharMotion.swift:385
@Composable
fun MeTurntable(
    me: MeChar,   // ios: Views/CharMotion.swift:386
    scale: Int,   // ios: Views/CharMotion.swift:387
) {
    var dir by remember { mutableStateOf(CharDir.down) }   // ios: Views/CharMotion.swift:388 @State private var dir
    var walking by remember { mutableStateOf(false) }   // ios: Views/CharMotion.swift:389 @State private var walking
    Box(Modifier.padding(bottom = 26.dp)) {   // ios: Views/CharMotion.swift:390,400
        CharFigure(me = me, dot = scale.dp, dir = dir, walking = walking, tapToTurn = false)   // ios: Views/CharMotion.swift:391
        // ios: Views/CharMotion.swift:392 overlay(alignment: .bottom): 틀 크기는 그대로, 넘치면 밖으로
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
            Row(Modifier.wrapContentSize(unbounded = true).offset(y = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/CharMotion.swift:393,398
                Pill("↺", "왼쪽으로 돌리기") { dir = dir.turned(-1) }   // ios: Views/CharMotion.swift:394
                Pill(if (walking) "멈춤" else "걷기", if (walking) "걷기 멈추기" else "걷기") { walking = !walking }   // ios: Views/CharMotion.swift:395
                Pill("↻", "오른쪽으로 돌리기") { dir = dir.turned(1) }   // ios: Views/CharMotion.swift:396
            }
        }
    }
}

// ios: Views/CharMotion.swift:402
@Composable
private fun Pill(t: String, label: String, a: () -> Unit) {
    val card = Theme.card; val border = Theme.border
    Box(Modifier
        .clearAndSetSemantics { contentDescription = label; role = Role.Button; onClick { a(); true } }   // ios: Views/CharMotion.swift:408 accessibilityLabel(label)
        .then(Modifier.plainClickable(a))   // ios: Views/CharMotion.swift:403,407 Button(action: a) · buttonStyle(.plain)
        .background(card, CapsuleShape)   // ios: Views/CharMotion.swift:405 background(Theme.card, in: Capsule()) · overlay(Capsule().stroke(Theme.border))
        .drawWithStroke(border)
        .padding(horizontal = 9.dp, vertical = 4.dp)) {   // ios: Views/CharMotion.swift:404 Text(t).appFont(12, .bold).padding(.horizontal, 9).padding(.vertical, 4)
        T(t, style = appFont(12f, FontWeight.Bold))
    }
}

/** Capsule().stroke(색): SwiftUI 처럼 선 가운데가 테두리에 걸치게 (Compose border 는 안쪽) */
private fun Modifier.drawWithStroke(c: Color, width: Dp = 1.dp): Modifier = this.drawWithContent {
    drawContent()
    val r = size.height / 2
    drawRoundRect(c, Offset.Zero, Size(size.width, size.height), CornerRadius(r, r), style = Stroke(width.toPx()))
}
