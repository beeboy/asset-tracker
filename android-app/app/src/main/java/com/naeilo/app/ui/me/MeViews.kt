package com.naeilo.app.ui.me

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as BmpCanvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.naeilo.app.AppModel
import com.naeilo.app.core.Store
import com.naeilo.app.core.WMe
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeColor
import com.naeilo.app.data.MeGroup
import com.naeilo.app.data.MeSample
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.background
import com.naeilo.app.data.backgrounds
import com.naeilo.app.data.bgDay
import com.naeilo.app.data.bgOn
import com.naeilo.app.data.friendName
import com.naeilo.app.data.meUnlocked
import com.naeilo.app.data.meVoice
import com.naeilo.app.data.roomBG
import com.naeilo.app.pending.MeMotion
import com.naeilo.app.pending.MeTurntable
import com.naeilo.app.pending.PixBuf
import com.naeilo.app.pending.WidgetBridge
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ConfirmSheet
import com.naeilo.app.ui.DialogAction
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.InputField
import com.naeilo.app.ui.LocalFg
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.drawableId
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.plainClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// 내 캐릭터 그리기·만들기 화면·쉼터 배경 고르기 (데이터와 열림 조건은 data/Me.kt)
// 도트 그림은 한 번만 합성하고(remember·캐시) 보간 없이 키운다. 버퍼 한 칸은 안드로이드 ARGB Int (0xAARRGGBB).

/** 인물 도트 하나: 기본 인물은 spr_/sil_ 그림, 내 캐릭터(me)는 부품을 겹쳐 그린다 */
// ios: Views/MeViews.swift:6,7,8,9,10,12,13
@Composable
fun CharPixel(id: String, width: Dp, height: Dp, modifier: Modifier = Modifier, sil: Boolean = false, model: AppModel? = null) {
    // 앱 안에서는 모델을 넘겨야 저장 직후 다시 그린다 (모델이 없는 곳은 MeChar.current)
    if (id == "me") {   // ios: Views/MeViews.swift:14
        val me = model?.me ?: MeChar.current; if (me != null) MeAvatar(me = me, width = width, height = height, modifier = modifier.alpha(if (sil) 0.45f else 1f))   // ios: Views/MeViews.swift:15
        else MeEmpty(width = width, height = height, modifier = modifier)   // ios: Views/MeViews.swift:16
    } else {   // ios: Views/MeViews.swift:17
        PixelN((if (sil) "sil_" else "spr_") + id, width, height, modifier)   // ios: Views/MeViews.swift:18
    }
}

/** 아직 만들지 않은 칸 (레이아웃만) */
// ios: Views/MeViews.swift:24,25,26,27
@Composable
fun MeEmpty(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val dash = Theme.dash
    Box(
        modifier
            .size(width, height)   // ios: Views/MeViews.swift:31
            .semantics { contentDescription = "내 캐릭터 만들기" }   // ios: Views/MeViews.swift:32
            .drawBehind { drawRoundRect(dash, cornerRadius = CornerRadius(6.dp.toPx()),   // ios: Views/MeViews.swift:28
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))) },   // ios: Views/MeViews.swift:29
        contentAlignment = Alignment.Center,
    ) {
        Sym("plus", max(10f, width.value * 0.35f), Theme.muted)   // ios: Views/MeViews.swift:30
    }
}

/** 내 캐릭터: 24×34 판에 부품을 아래부터 겹치고 바깥 테두리를 한 줄 두른 그림 (MeArt.image), 최근접 확대 */
// ios: Views/MeViews.swift:37,38,39,40,41
@Composable
fun MeAvatar(me: MeChar, width: Dp = 24.dp, height: Dp = 34.dp, modifier: Modifier = Modifier) {
    val img = remember(me) { MeArt.image(me)?.asImageBitmap() }   // ios: Views/MeViews.swift:43 (합성은 me 가 바뀔 때 한 번)
    Box(modifier   // ios: Views/MeViews.swift:42
        .size(width, height)   // ios: Views/MeViews.swift:45
        .semantics { contentDescription = me.shownName }) {   // ios: Views/MeViews.swift:46
        if (img != null) Image(img, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
    }
}

// ios: Views/MeViews.swift:50
object MeArt {
    const val W = 24; const val H = 34   // ios: Views/MeViews.swift:51
    const val outline: Int = 0x2A1E2E   // ios: Views/MeViews.swift:52 기본 스프라이트와 같은 바깥 테두리 색

    /** 부품 그림 이름. body: me_body_<피부> · face: me_face_<모양>_<피부> · hair: me_hair_<모양>_<머리색> · 나머지: me_<그룹>_<번호> */
    // ios: Views/MeViews.swift:56
    fun asset(layer: String, n: Int, me: MeChar): String {
        val f = { x: Int -> "%02d".format(x) }   // ios: Views/MeViews.swift:57
        return when (layer) {   // ios: Views/MeViews.swift:58
            "body" -> "me_body_" + f(me.skin)   // ios: Views/MeViews.swift:59
            "face" -> "me_face_${f(n)}_${f(me.skin)}"   // ios: Views/MeViews.swift:60
            "hair" -> "me_hair_${f(n)}_${f(me.part(MeGroup.color))}"   // ios: Views/MeViews.swift:61
            else -> "me_${layer}_" + f(n)   // ios: Views/MeViews.swift:62
        }
    }
    /** 이 부품의 번호 (몸은 늘 있다) */
    // ios: Views/MeViews.swift:66
    fun option(me: MeChar, layer: String): Int {
        if (layer == "body") return 1   // ios: Views/MeViews.swift:67
        return MeGroup.entries.firstOrNull { it.raw == layer }?.let { me.part(it) } ?: 0   // ios: Views/MeViews.swift:68
    }

    private val known = ConcurrentHashMap<String, Boolean>()   // ios: Views/MeViews.swift:71
    /** 에셋이 있는지 (쉼터 배경 그림 등) */
    // ios: Views/MeViews.swift:73
    @SuppressLint("DiscouragedApi")
    fun has(name: String): Boolean {
        known[name]?.let { return it }   // ios: Views/MeViews.swift:74
        val k = Store.ctx.resources.getIdentifier(name, "drawable", Store.ctx.packageName) != 0   // ios: Views/MeViews.swift:75
        known[name] = k   // ios: Views/MeViews.swift:76
        return k   // ios: Views/MeViews.swift:77
    }

    /** 정면 서 있는 그림 (부품 겹치기·음영·테두리는 MeMotion) */
    fun image(me: MeChar): Bitmap? = MeMotion.image(me)   // ios: Views/MeViews.swift:81

    /** 위젯이 읽는 내 캐릭터 (me.json · me.png). 바뀐 때만 다시 쓴다. 파일 일은 IO 에서 한 줄로 */
    private var lastKey = ""   // ios: Views/MeViews.swift:84
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    // ios: Views/MeViews.swift:85,86
    fun push(m: AppModel) {
        val me = m.me; val unlocked = m.meUnlocked; val voice = m.meVoice   // 모델 값은 main 에서 읽어 둔다
        io.launch { lock.withLock {
            if (me == null || !unlocked) {   // ios: Views/MeViews.swift:87
                if (lastKey != "none") {   // ios: Views/MeViews.swift:88
                    Store.remove("me.json"); Store.remove("me.png"); MePose.all.keys.forEach { Store.remove("me_$it.png") }   // ios: Views/MeViews.swift:89
                    lastKey = "none"   // ios: Views/MeViews.swift:90
                }
                return@withLock   // ios: Views/MeViews.swift:92
            }
            val i = MeColor.idx(me)   // ios: Views/MeViews.swift:94
            val w = WMe(name = me.shownName, voice = voice, accent = MeColor.accent[i], g0 = MeColor.g0, g1 = MeColor.g1[i])   // ios: Views/MeViews.swift:95
            val key = "${me.hashCode()}|${w.voice}"   // ios: Views/MeViews.swift:96
            if (key == lastKey) return@withLock   // ios: Views/MeViews.swift:97
            val scale = 1   // ios: Views/MeViews.swift:99 (그림 1칸 = 1px)
            val r = MeArt.image(me)?.let { Bitmap.createScaledBitmap(it, 192 * scale, 272 * scale, false) }   // ios: Views/MeViews.swift:98 (MeAvatar 192×272, 최근접)
            val png = r?.pngData() ?: return@withLock   // ios: Views/MeViews.swift:100
            Store.writeData(png, "me.png")   // ios: Views/MeViews.swift:101
            // 위젯 포즈 28장 (부품 그림이 다 있을 때만. 없으면 위젯은 서 있는 me.png)
            for (k in MePose.all.keys) {   // ios: Views/MeViews.swift:103
                val d = MePose.image(me, k)?.pngData(); if (d != null) Store.writeData(d, "me_$k.png") else Store.remove("me_$k.png")   // ios: Views/MeViews.swift:104
            }
            Store.write(w, "me.json")   // ios: Views/MeViews.swift:106
            lastKey = key   // ios: Views/MeViews.swift:107
        } }
    }
}

/** 그림이 오기 전 자리표시 도트 (24×34 칸). 색은 RGB Int */
// ios: Views/MeViews.swift:112
object MePlaceholder {
    private const val ink = 0x2A2230; private const val white = 0xF8F9FA   // ios: Views/MeViews.swift:113
    private val tops: List<Int> = listOf(0x4C6EF5, 0xE8590C, 0x2F9E44, 0xF1F3F5, 0x343A40, 0xAE3EC9, 0xF59F00, 0x1C3F7A)   // ios: Views/MeViews.swift:114
    private val bottoms: List<Int> = listOf(0x2C3E6B, 0x4D7CC4, 0x2B2B33, 0xB59B6A, 0xC0392B, 0x868E96)   // ios: Views/MeViews.swift:115

    // ios: Views/MeViews.swift:117
    fun draw(layer: String, n: Int, hair: Int, skin: Int, fill: (Int, Int, Int, Int, Int) -> Unit) {
        fun p(x: Int, y: Int, w: Int, h: Int, c: Int) = fill(x, y, w, h, c)   // ios: Views/MeViews.swift:118
        val dark = shade(hair, 0.72)   // ios: Views/MeViews.swift:119
        when (layer) {   // ios: Views/MeViews.swift:120
            "body" -> {   // ios: Views/MeViews.swift:121
                p(10, 16, 4, 1, shade(skin, 0.9))                 // ios: Views/MeViews.swift:122 목
                p(4, 24, 2, 2, skin); p(18, 24, 2, 2, skin)        // ios: Views/MeViews.swift:123 손
                p(8, 28, 3, 4, skin); p(13, 28, 3, 4, skin)        // ios: Views/MeViews.swift:124 다리
            }
            "face" -> {   // ios: Views/MeViews.swift:125
                val s = skin   // ios: Views/MeViews.swift:126
                when (n) {   // ios: Views/MeViews.swift:127
                    2 -> p(6, 5, 12, 11, s)   // ios: Views/MeViews.swift:128
                    3 -> { p(8, 4, 8, 1, s); p(7, 5, 10, 11, s); p(8, 16, 8, 1, s) }   // ios: Views/MeViews.swift:129
                    4 -> { p(7, 6, 10, 1, s); p(5, 7, 14, 8, s); p(7, 15, 10, 1, s) }   // ios: Views/MeViews.swift:130
                    else -> { p(8, 5, 8, 1, s); p(7, 6, 10, 1, s); p(6, 7, 12, 7, s); p(7, 14, 10, 1, s); p(8, 15, 8, 1, s) }   // ios: Views/MeViews.swift:131
                }
                p(5, 10, 1, 2, s); p(18, 10, 1, 2, s)          // ios: Views/MeViews.swift:133 귀
            }
            "eyes" -> when (n) {   // ios: Views/MeViews.swift:134,135
                2 -> { p(8, 10, 2, 2, ink); p(14, 10, 2, 2, ink); p(8, 10, 1, 1, white); p(14, 10, 1, 1, white) }   // ios: Views/MeViews.swift:136
                3 -> { p(8, 11, 2, 1, ink); p(14, 11, 2, 1, ink) }   // ios: Views/MeViews.swift:137
                4 -> { p(8, 10, 2, 1, ink); p(9, 11, 1, 1, ink); p(14, 10, 2, 1, ink); p(14, 11, 1, 1, ink) }   // ios: Views/MeViews.swift:138
                5 -> { p(8, 10, 2, 2, 0x3A6FD8); p(14, 10, 2, 2, 0x3A6FD8); p(8, 10, 1, 1, white); p(14, 10, 1, 1, white) }   // ios: Views/MeViews.swift:139
                6 -> { p(8, 9, 2, 3, ink); p(14, 9, 2, 3, ink); p(8, 9, 1, 1, white); p(14, 9, 1, 1, white) }   // ios: Views/MeViews.swift:140
                else -> { p(9, 10, 1, 2, ink); p(14, 10, 1, 2, ink) }   // ios: Views/MeViews.swift:141
            }
            "mouth" -> when (n) {   // ios: Views/MeViews.swift:143,144
                2 -> p(10, 13, 4, 1, ink)   // ios: Views/MeViews.swift:145
                3 -> p(11, 13, 2, 2, 0xB2454A)   // ios: Views/MeViews.swift:146
                4 -> p(11, 13, 2, 1, 0xB2454A)   // ios: Views/MeViews.swift:147
                5 -> { p(10, 13, 4, 1, white); p(10, 14, 4, 1, ink) }   // ios: Views/MeViews.swift:148
                6 -> p(11, 13, 2, 2, ink)   // ios: Views/MeViews.swift:149
                else -> { p(10, 12, 1, 1, ink); p(11, 13, 2, 1, ink); p(13, 12, 1, 1, ink) }   // ios: Views/MeViews.swift:150
            }
            "hair" -> {   // ios: Views/MeViews.swift:152
                p(7, 3, 10, 1, dark); p(6, 4, 12, 2, hair)        // ios: Views/MeViews.swift:153 정수리
                when (n) {   // ios: Views/MeViews.swift:154
                    1 -> { p(5, 5, 2, 4, hair); p(17, 5, 2, 4, hair); p(7, 6, 10, 1, hair) }   // ios: Views/MeViews.swift:155
                    2 -> { p(5, 5, 2, 4, hair); p(17, 5, 2, 3, hair); p(6, 6, 7, 2, hair); p(13, 6, 3, 1, hair) }   // ios: Views/MeViews.swift:156
                    3 -> { p(7, 6, 10, 2, hair); p(5, 5, 2, 11, hair); p(17, 5, 2, 11, hair) }   // ios: Views/MeViews.swift:157
                    4 -> { p(6, 6, 12, 1, hair); p(5, 5, 2, 9, hair); p(17, 5, 2, 9, hair) }   // ios: Views/MeViews.swift:158
                    5 -> { p(6, 2, 2, 1, hair); p(10, 1, 2, 2, hair); p(14, 2, 2, 1, hair); p(7, 6, 2, 2, hair); p(11, 6, 2, 1, hair); p(15, 6, 2, 2, hair) }   // ios: Views/MeViews.swift:159
                    6 -> { p(10, 0, 4, 3, hair); p(5, 5, 2, 4, hair); p(17, 5, 2, 4, hair); p(7, 6, 10, 1, hair) }   // ios: Views/MeViews.swift:160
                    7 -> { p(5, 5, 2, 5, hair); p(17, 5, 2, 4, hair); p(7, 6, 10, 1, hair); p(17, 7, 2, 2, dark) }   // ios: Views/MeViews.swift:161
                    else -> p(6, 4, 12, 1, dark)                      // ios: Views/MeViews.swift:162 짧게 민 머리
                }
            }
            "top" -> {   // ios: Views/MeViews.swift:164
                val c = tops[(n - 1) % tops.size]; val d = shade(c, 0.78)   // ios: Views/MeViews.swift:165
                p(6, 17, 12, 9, c); p(4, 17, 2, 7, d); p(18, 17, 2, 7, d)   // ios: Views/MeViews.swift:166
                when (n) {   // ios: Views/MeViews.swift:167
                    2 -> p(7, 16, 10, 2, d)                         // ios: Views/MeViews.swift:168 후드
                    4 -> p(11, 18, 2, 6, 0xC92A2A)                  // ios: Views/MeViews.swift:169 넥타이
                    5 -> p(11, 17, 2, 9, white)                     // ios: Views/MeViews.swift:170 열린 재킷
                    8 -> { p(6, 19, 12, 1, white); p(6, 22, 12, 1, white) }   // ios: Views/MeViews.swift:171 줄무늬
                    else -> p(10, 17, 4, 1, d)   // ios: Views/MeViews.swift:172
                }
            }
            "bottom" -> {   // ios: Views/MeViews.swift:174
                val c = bottoms[(n - 1) % bottoms.size]   // ios: Views/MeViews.swift:175
                when (n) {   // ios: Views/MeViews.swift:176
                    5 -> p(6, 26, 12, 4, c)          // ios: Views/MeViews.swift:177 치마
                    6 -> p(7, 26, 10, 3, c)          // ios: Views/MeViews.swift:178 반바지
                    else -> { p(7, 26, 10, 2, c); p(7, 28, 4, 4, c); p(13, 28, 4, 4, c) }   // ios: Views/MeViews.swift:179
                }
                p(7, 32, 4, 2, ink); p(13, 32, 4, 2, ink)          // ios: Views/MeViews.swift:181 신발
            }
            "glasses" -> {   // ios: Views/MeViews.swift:182
                val c: Int = listOf(ink, 0xC9A227, 0xC92A2A, 0x222226)[(n - 1) % 4]   // ios: Views/MeViews.swift:183
                if (n == 4) { p(7, 9, 4, 3, c); p(13, 9, 4, 3, c) }    // ios: Views/MeViews.swift:184 선글라스
                else {   // ios: Views/MeViews.swift:185
                    for (x in listOf(7, 13)) { p(x, 9, 4, 1, c); p(x, 12, 4, 1, c); p(x, 9, 1, 4, c); p(x + 3, 9, 1, 4, c) }   // ios: Views/MeViews.swift:186
                }
                p(11, 10, 2, 1, c)   // ios: Views/MeViews.swift:188
            }
            "mask" -> {   // ios: Views/MeViews.swift:189
                val c: Int = listOf(white, 0x212529, 0x96F2D7)[(n - 1) % 3]   // ios: Views/MeViews.swift:190
                p(7, 12, 10, 4, c); p(6, 12, 1, 1, shade(c, 0.7)); p(17, 12, 1, 1, shade(c, 0.7))   // ios: Views/MeViews.swift:191
            }
            "prop" -> when (n) {   // ios: Views/MeViews.swift:192,193
                1 -> { p(19, 21, 3, 4, white); p(22, 22, 1, 2, white); p(19, 21, 3, 1, 0x7A4A2E) }     // ios: Views/MeViews.swift:194 커피
                2 -> { p(19, 19, 4, 6, 0xC0392B); p(19, 19, 1, 6, 0x8E2A20) }                         // ios: Views/MeViews.swift:195 책
                3 -> { p(19, 21, 4, 4, 0xF2C230); p(20, 22, 2, 2, 0xD9A400) }                         // ios: Views/MeViews.swift:196 동전
                4 -> { p(20, 24, 3, 2, 0x8D5A3B); p(19, 20, 2, 3, 0x2F9E44); p(21, 19, 2, 4, 0x40C057) } // ios: Views/MeViews.swift:197 화분
                5 -> { p(20, 19, 2, 6, 0xF2C14E); p(18, 21, 6, 2, 0xF2C14E) }                         // ios: Views/MeViews.swift:198 별
                else -> { p(20, 20, 3, 5, 0x2B2B33); p(20, 21, 3, 3, 0x5FC9B0) }                        // ios: Views/MeViews.swift:199 휴대폰
            }
            else -> {}   // ios: Views/MeViews.swift:201
        }
    }
    // ios: Views/MeViews.swift:204
    fun shade(c: Int, k: Double): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt(); val g = (((c shr 8) and 0xFF) * k).toInt(); val b = ((c and 0xFF) * k).toInt()   // ios: Views/MeViews.swift:205
        return (r shl 16) or (g shl 8) or b   // ios: Views/MeViews.swift:206
    }
}

// MARK: 만들기 화면

/** 이동 막대 제목까지 붙인 만들기 화면 (홈 NavStack route "me") */
// ios: Views/MeViews.swift:232
fun meEditPage(m: AppModel): NavPage = NavPage("내 캐릭터") { MeEditView(m) }

/** 만들기 화면의 @State 묶음 (Swift struct 의 private 상태) */
private class MeEditState {
    var draft by mutableStateOf(MeChar())   // ios: Views/MeViews.swift:216
    var group by mutableStateOf(MeGroup.hair)   // ios: Views/MeViews.swift:217
    var loaded by mutableStateOf(false)   // ios: Views/MeViews.swift:218
    var note by mutableStateOf<String?>(null)   // ios: Views/MeViews.swift:219
    var askDelete by mutableStateOf(false)   // ios: Views/MeViews.swift:220
    var samples by mutableStateOf(false)           // ios: Views/MeViews.swift:221 샘플(소설 속 인물) 목록을 보는 중
    var sample by mutableStateOf<MeSample?>(null)   // ios: Views/MeViews.swift:222 오른쪽 위에 크게 보이는 샘플 (처음엔 빈 칸)
}

/** 내 캐릭터 만들기: 그룹(세로) · 그룹별 선택지(세로) · 캐릭터. 아래에 이름·투자 유형·저장 (저장 칸 1개) */
// ios: Views/MeViews.swift:213,214,224
@Composable
fun MeEditView(m: AppModel) {
    val dismiss = LocalNavBack.current   // ios: Views/MeViews.swift:215
    val st = remember { MeEditState() }
    LaunchedEffect(Unit) { if (!st.loaded) { st.draft = m.me ?: MeChar(); st.loaded = true } }   // ios: Views/MeViews.swift:233
    // ios: Views/MeViews.swift:225
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxSize()
        .background(Theme.bg)   // ios: Views/MeViews.swift:231
        .padding(horizontal = 12.dp).padding(top = 8.dp)) {   // ios: Views/MeViews.swift:230
        st.Left(m, Modifier.width(84.dp).fillMaxHeight())   // ios: Views/MeViews.swift:226
        st.Options(Modifier.width(76.dp).fillMaxHeight())   // ios: Views/MeViews.swift:227
        st.Right(m, Modifier.weight(1f).fillMaxHeight()) { dismiss?.pop?.invoke() }   // ios: Views/MeViews.swift:228
    }
    // ios: Views/MeViews.swift:234
    ConfirmSheet(show = st.askDelete, title = "처음부터 다시 만들까요?", onDismiss = { st.askDelete = false },
        message = if (m.me != null) "저장한 내 캐릭터도 지워져요." else "고른 부품과 이름이 지워져요.",   // ios: Views/MeViews.swift:244,245
        actions = listOf(DialogAction("리셋", destructive = true, onClick = {   // ios: Views/MeViews.swift:235
            // 리셋: 저장한 캐릭터도 지우고 빈 칸으로
            if (m.me != null) {   // ios: Views/MeViews.swift:237
                m.me = null   // ios: Views/MeViews.swift:238
                if (m.homeFriend == "me") m.homeFriend = "seri"   // ios: Views/MeViews.swift:239
                WidgetBridge.write(m)   // ios: Views/MeViews.swift:240
            }
            st.draft = MeChar(); st.sample = null; st.note = null   // ios: Views/MeViews.swift:242
        })))
}

/** 왼쪽: 샘플 · 부품 그룹, 그 아래 투자 유형 (보라색) */
// ios: Views/MeViews.swift:250
@Composable
private fun MeEditState.Left(m: AppModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {   // ios: Views/MeViews.swift:251
        Column(
            Modifier.padding(bottom = 12.dp),   // ios: Views/MeViews.swift:277
            verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/MeViews.swift:252
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 40.dp)   // ios: Views/MeViews.swift:256
                    .background(if (samples) Theme.teal else Theme.mintBg, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:257
                    .border(1.dp, if (samples) Theme.teal else Theme.border, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:258
                    .plainClickable { samples = true }   // ios: Views/MeViews.swift:253,260
                    .semantics(mergeDescendants = true) { contentDescription = "샘플, 소설 속 인물 조합"; selected = samples },   // ios: Views/MeViews.swift:261,262
                contentAlignment = Alignment.Center,
            ) {
                T("샘플", style = appFont(13f, if (samples) FontWeight.Bold else FontWeight.Normal),   // ios: Views/MeViews.swift:254
                    color = if (samples) Color.White else Theme.ink)   // ios: Views/MeViews.swift:255
            }
            MeGroup.entries.forEach { g ->   // ios: Views/MeViews.swift:263
                val on = !samples && g == group   // ios: Views/MeViews.swift:264
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp)   // ios: Views/MeViews.swift:268
                        .background(if (on) Theme.teal else Theme.card, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:269
                        .border(1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:270
                        .plainClickable { group = g; samples = false }   // ios: Views/MeViews.swift:265,272
                        .semantics { selected = on },   // ios: Views/MeViews.swift:273
                    contentAlignment = Alignment.Center,
                ) {
                    FitText(g.label, appFont(13f, if (on) FontWeight.Bold else FontWeight.Normal), 0.8f, maxLines = 1,   // ios: Views/MeViews.swift:266
                        color = if (on) Color.White else Theme.ink)   // ios: Views/MeViews.swift:267
                }
            }
            Types(m, Modifier.padding(top = 14.dp))   // ios: Views/MeViews.swift:275
        }
    }
}

/** 투자 유형: 평정 지수가 매칭되기 전에는 고르고, 매칭되면 그 유형만 켜진다 (말투도 그 인물) */
// ios: Views/MeViews.swift:282
@Composable
private fun MeEditState.Types(m: AppModel, modifier: Modifier) {
    val matched = m.calm.type   // ios: Views/MeViews.swift:283
    val cur = matched ?: draft.type   // ios: Views/MeViews.swift:284
    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/MeViews.swift:285
        T("투자 유형", style = appFont(13f, FontWeight.Bold), color = Theme.purple)   // ios: Views/MeViews.swift:286
        Calm.kinds.forEach { k ->   // ios: Views/MeViews.swift:287
            val on = cur == k.id   // ios: Views/MeViews.swift:288
            Box(
                Modifier.fillMaxWidth()
                    .alpha(if (matched != null && !on) 0.4f else 1f)   // ios: Views/MeViews.swift:299
                    .background(if (on) Theme.purple else Theme.purple.copy(alpha = 0.12f), CapsuleShape)   // ios: Views/MeViews.swift:294
                    .border(1.dp, Theme.purple.copy(alpha = if (on) 1f else 0.4f), CapsuleShape)   // ios: Views/MeViews.swift:295
                    .then(if (matched == null) Modifier.plainClickable { draft = draft.copy(type = k.id) } else Modifier)   // ios: Views/MeViews.swift:289,297,298
                    .semantics(mergeDescendants = true) { contentDescription = "${k.title}, ${m.friendName(k.id)} 말투"; selected = on }   // ios: Views/MeViews.swift:300,301
                    .padding(horizontal = 4.dp).heightIn(min = 34.dp),   // ios: Views/MeViews.swift:293
                contentAlignment = Alignment.Center,
            ) {
                FitText(k.title + (if (on) " ✓" else ""), appFont(12f, if (on) FontWeight.Bold else FontWeight.Normal),   // ios: Views/MeViews.swift:290
                    0.7f, maxLines = 1,   // ios: Views/MeViews.swift:291
                    color = if (on) Color.White else Theme.purple)   // ios: Views/MeViews.swift:292
            }
        }
    }
}

/** 샘플: 소설 속 인물 조합. 누르면 오른쪽 위에 크게 보이고, '적용'을 눌러야 내 캐릭터에 입힌다 */
// ios: Views/MeViews.swift:307
@Composable
private fun MeEditState.SampleList(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {   // ios: Views/MeViews.swift:308
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/MeViews.swift:309
            MeSample.all.forEach { x ->   // ios: Views/MeViews.swift:310
                val on = sample?.id == x.id   // ios: Views/MeViews.swift:311
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (on) Theme.tealBg else Theme.card, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:319
                        .border(if (on) 2.5.dp else 1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:320
                        .plainClickable { sample = x }   // ios: Views/MeViews.swift:312,322
                        .semantics(mergeDescendants = true) { contentDescription = x.name; selected = on }   // ios: Views/MeViews.swift:323,324
                        .padding(horizontal = 2.dp).heightIn(min = 80.dp),   // ios: Views/MeViews.swift:318
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),   // ios: Views/MeViews.swift:313
                ) {
                    Fg(Theme.ink) {   // ios: Views/MeViews.swift:317
                        MeAvatar(me = MeChar(parts = x.parts), width = 34.dp, height = 48.dp)   // ios: Views/MeViews.swift:314
                        FitLines(x.name, appFont(10f).copy(textAlign = TextAlign.Center), maxLines = 2, minScale = 0.7f)   // ios: Views/MeViews.swift:315
                    }
                }
            }
        }
    }
}

// ios: Views/MeViews.swift:330
@Composable
private fun MeEditState.Options(modifier: Modifier) {
    if (samples) SampleList(modifier) else PartList(modifier)   // ios: Views/MeViews.swift:331
}

// ios: Views/MeViews.swift:334
@Composable
private fun MeEditState.PartList(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {   // ios: Views/MeViews.swift:335
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/MeViews.swift:336
            group.options.forEach { o ->   // ios: Views/MeViews.swift:337
                val on = draft.picked(group, o)   // ios: Views/MeViews.swift:338
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (on) Theme.tealBg else Theme.card, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:360
                        .border(if (on) 2.5.dp else 1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:361
                        .plainClickable { draft = draft.with(group, o) }   // ios: Views/MeViews.swift:339,363
                        .semantics(mergeDescendants = true) { contentDescription = if (group == MeGroup.color || o == 0) group.name(o) else "${group.label} ${group.name(o)}"; selected = on }   // ios: Views/MeViews.swift:364,365
                        .padding(horizontal = 2.dp).heightIn(min = 72.dp),   // ios: Views/MeViews.swift:359
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),   // ios: Views/MeViews.swift:340
                ) {
                    Fg(Theme.ink) {   // ios: Views/MeViews.swift:358
                        if (group == MeGroup.color && o > 100) {   // ios: Views/MeViews.swift:341
                            Box(Modifier.size(34.dp, 34.dp).background(hex(MeColor.skin[o - 101]), CircleShape)   // ios: Views/MeViews.swift:342
                                .border(1.dp, Theme.border, CircleShape))   // ios: Views/MeViews.swift:343
                            FitText(group.name(o), appFont(11f), 0.7f, maxLines = 1)   // ios: Views/MeViews.swift:344
                        } else if (group == MeGroup.color) {   // ios: Views/MeViews.swift:345
                            Box(Modifier.size(34.dp, 34.dp).background(hex(MeColor.hair[o - 1]), CircleShape)   // ios: Views/MeViews.swift:346
                                .border(1.dp, Theme.border, CircleShape))   // ios: Views/MeViews.swift:347
                            FitText(group.name(o), appFont(11f), 0.7f, maxLines = 1)   // ios: Views/MeViews.swift:348
                        } else if (o == 0) {   // ios: Views/MeViews.swift:349
                            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Sym("nosign", 22f, Theme.muted) }   // ios: Views/MeViews.swift:350
                            T("없음", style = appFont(11f))   // ios: Views/MeViews.swift:351
                        } else {   // ios: Views/MeViews.swift:352
                            MeAvatar(me = draft.with(group, o), width = 34.dp, height = 48.dp)   // ios: Views/MeViews.swift:353
                            FitLines(group.name(o), appFont(10f).copy(textAlign = TextAlign.Center), color = Theme.sub,   // ios: Views/MeViews.swift:354
                                maxLines = 2, minScale = 0.7f)   // ios: Views/MeViews.swift:355
                        }
                    }
                }
            }
        }
    }
}

/** 오른쪽: 위는 고른 샘플(소개·적용), 아래는 내 캐릭터(같은 크기)와 이름·저장. 둘 다 가운데. 그림 크기는 24×34 의 정수배(3~6배) */
// ios: Views/MeViews.swift:373
@Composable
private fun MeEditState.Right(m: AppModel, modifier: Modifier, dismiss: () -> Unit) {
    BoxWithConstraints(modifier) {   // ios: Views/MeViews.swift:374
        val s = scale(maxWidth, maxHeight)   // ios: Views/MeViews.swift:375
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {   // ios: Views/MeViews.swift:376
            Column(
                Modifier.fillMaxWidth()   // ios: Views/MeViews.swift:420
                    .padding(bottom = 12.dp),   // ios: Views/MeViews.swift:421
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp),   // ios: Views/MeViews.swift:377
            ) {
                val x = sample
                if (x != null) {   // ios: Views/MeViews.swift:378
                    Big(MeChar(parts = x.parts), s, Modifier.padding(top = 14.dp))   // ios: Views/MeViews.swift:379
                    Column(
                        Modifier.padding(top = 14.dp),   // ios: Views/MeViews.swift:386
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/MeViews.swift:380
                    ) {
                        val center = TextAlign.Center   // ios: Views/MeViews.swift:385
                        T(x.name, style = appFont(15f, FontWeight.Bold).copy(textAlign = center))   // ios: Views/MeViews.swift:381
                        T(x.about, style = appFont(11f).copy(textAlign = center), color = Theme.sub)   // ios: Views/MeViews.swift:382
                        T(parts(x), style = appFont(10f).copy(textAlign = center), color = Theme.muted)   // ios: Views/MeViews.swift:383
                    }
                    val applied = draft.parts == x.parts   // ios: Views/MeViews.swift:387
                    Box(
                        Modifier.padding(top = 10.dp)   // ios: Views/MeViews.swift:395
                            .size(boxW(s), 42.dp)   // ios: Views/MeViews.swift:390
                            .background(if (applied) Theme.muted else Theme.teal, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:391
                            .then(if (applied) Modifier else Modifier.plainClickable { draft = draft.copy(parts = x.parts) }),   // ios: Views/MeViews.swift:388,393,394
                        contentAlignment = Alignment.Center,
                    ) {
                        T(if (applied) "적용했어요" else "적용 ↓", style = appFont(15f, FontWeight.Bold), color = Color.White)   // ios: Views/MeViews.swift:389
                    }
                } else {   // ios: Views/MeViews.swift:396
                    Empty(s, Modifier.padding(top = 14.dp))   // ios: Views/MeViews.swift:397
                    T("왼쪽 샘플에 『중첩된 현실』 시리즈의 주요 인물들이 있어요. 골라서 적용해 보세요.",   // ios: Views/MeViews.swift:398
                        style = appFont(11f).copy(textAlign = TextAlign.Center), color = Theme.sub,   // ios: Views/MeViews.swift:399
                        modifier = Modifier.padding(top = 14.dp))   // ios: Views/MeViews.swift:400
                }
                // 처음(저장한 것도, 고른 부품도 없을 때)에는 빈 칸
                Box(Modifier.padding(top = 28.dp)) {   // ios: Views/MeViews.swift:403,406
                    if (draft.parts.isEmpty() && m.me == null) Empty(s) else Big(draft, s)   // ios: Views/MeViews.swift:404
                }
                T("나만의 캐릭터를 만들어 홈과 위젯에서 함께하세요",   // ios: Views/MeViews.swift:407
                    style = appFont(11f).copy(textAlign = TextAlign.Center), color = Theme.sub,   // ios: Views/MeViews.swift:408
                    modifier = Modifier.padding(top = 8.dp))   // ios: Views/MeViews.swift:409
                NameRow(m, dismiss, Modifier.padding(top = 10.dp))   // ios: Views/MeViews.swift:410
                note?.let { T(it, Modifier.padding(top = 8.dp), style = appFont(13f, FontWeight.Bold), color = Theme.teal) }   // ios: Views/MeViews.swift:411
                val resetOff = m.me == null && draft == MeChar() && sample == null   // ios: Views/MeViews.swift:417
                Row(
                    Modifier.padding(top = 4.dp)   // ios: Views/MeViews.swift:418
                        .then(if (resetOff) Modifier else Modifier.plainClickable { askDelete = true })   // ios: Views/MeViews.swift:412,416
                        .heightIn(min = 40.dp).padding(horizontal = 12.dp),   // ios: Views/MeViews.swift:414
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("arrow.counterclockwise", 13f, Theme.muted); T("리셋", style = appFont(13f), color = Theme.muted)   // ios: Views/MeViews.swift:413
                }
            }
        }
    }
}

/** 그림 배율: 폭에 들어가고, 두 그림과 글·버튼이 한 화면에 들어가는 가장 큰 정수배 */
// ios: Views/MeViews.swift:427
private fun scale(width: Dp, height: Dp): Int {
    val byW = ((width.value - 20) / MeArt.W).toInt()   // ios: Views/MeViews.swift:428
    val byH = ((height.value - 330) / (MeArt.H * 2)).toInt()   // ios: Views/MeViews.swift:429
    return max(3, minOf(6, byW, byH))   // ios: Views/MeViews.swift:430
}
private fun boxW(s: Int): Dp = (MeArt.W * s + 20).dp   // ios: Views/MeViews.swift:432

/** 그림 자리 빈 칸 (같은 크기, 점선) */
// ios: Views/MeViews.swift:435
@Composable
private fun Empty(s: Int, modifier: Modifier = Modifier) {
    val dash = Theme.dash
    Canvas(modifier
        .size(boxW(s), (MeArt.H * s + 20).dp)) {   // ios: Views/MeViews.swift:438
        drawRoundRect(dash, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))   // ios: Views/MeViews.swift:436,437
    }
}

/** 고른 샘플·내 캐릭터 같은 크기 (24×34 의 s배) */
// ios: Views/MeViews.swift:442
@Composable
private fun Big(c: MeChar, s: Int, modifier: Modifier = Modifier) {
    Box(modifier
        .semantics(mergeDescendants = true) { contentDescription = c.shownName }   // ios: Views/MeViews.swift:446
        .border(1.dp, Theme.border, RoundedCornerShape(14.dp))   // ios: Views/MeViews.swift:445
        .padding(10.dp)) {   // ios: Views/MeViews.swift:444
        MeTurntable(me = c, scale = s)   // ios: Views/MeViews.swift:443
    }
}

/** 샘플 구성 한 줄: 머리·색·옷·소품 이름 */
// ios: Views/MeViews.swift:450
private fun parts(x: MeSample): String {
    val c = MeChar(parts = x.parts)   // ios: Views/MeViews.swift:451
    return MeGroup.entries.mapNotNull { g ->   // ios: Views/MeViews.swift:452
        val o = if (g == MeGroup.color) c.part(MeGroup.color) else c.part(g)   // ios: Views/MeViews.swift:453
        if (g.optional && o == 0) return@mapNotNull null   // ios: Views/MeViews.swift:454
        if (g == MeGroup.color) "${g.name(o)} 머리 · ${MeColor.skinNames[MeColor.skinIdx(c)]}" else g.name(o)   // ios: Views/MeViews.swift:455
    }.joinToString(" · ")   // ios: Views/MeViews.swift:456
}

// ios: Views/MeViews.swift:459
@Composable
private fun MeEditState.NameRow(m: AppModel, dismiss: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/MeViews.swift:460
        val font = appFont(16f); val padH = 10.dp; val minH = 44.dp   // ios: Views/MeViews.swift:462
        Box(
            Modifier.weight(1f)
                .background(Theme.card, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:463
                .border(1.dp, Theme.border, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:464
                .heightIn(min = minH).padding(horizontal = padH),
            contentAlignment = Alignment.CenterStart,
        ) {
            InputField(value = draft.name, placeholder = "이름 (${MeChar.nameMax}자)", style = font,   // ios: Views/MeViews.swift:461
                // 한글은 조합 중에 바로 바꾸면 무시돼서, 한 박자 뒤에 다듬는다 (저장할 때도 한 번 더)
                onChange = { v -> draft = draft.copy(name = v)   // ios: Views/MeViews.swift:466
                    val c = MeChar.clean(v)   // ios: Views/MeViews.swift:467
                    if (c != v) scope.launch { yield(); draft = draft.copy(name = MeChar.clean(draft.name)) }   // ios: Views/MeViews.swift:468
                },
                onSubmit = { draft = draft.copy(name = MeChar.clean(draft.name)) })   // ios: Views/MeViews.swift:470
        }
        Box(
            Modifier.size(56.dp, 44.dp).background(Theme.teal, RoundedCornerShape(10.dp))   // ios: Views/MeViews.swift:478
                .plainClickable {   // ios: Views/MeViews.swift:471,480
                    draft = draft.copy(name = MeChar.clean(draft.name).trim { it == ' ' || it == '\t' })   // ios: Views/MeViews.swift:472
                    m.me = draft   // ios: Views/MeViews.swift:473
                    WidgetBridge.write(m)   // ios: Views/MeViews.swift:474
                    dismiss()   // ios: Views/MeViews.swift:475 저장하면 바로 이전 화면으로
                },
            contentAlignment = Alignment.Center,
        ) {
            T("저장", style = appFont(16f, FontWeight.Bold), color = Color.White)   // ios: Views/MeViews.swift:476,477
        }
    }
}

// MARK: 쉼터 방 그림

/** 쉼터 방 바탕: 고른 배경(bg_NN, 라이트·다크), 없으면 기본 방. 그림이 오기 전에는 기본 방을 색만 바꿔 보여 준다. gray = .grayscale */
// ios: Views/MeViews.swift:488,489,490
@Composable
fun RoomImage(bg: String?, modifier: Modifier = Modifier, gray: Float = 0f) {
    val dark = LocalPal.current.isDark
    if (bg != null && MeArt.has(bg)) {   // ios: Views/MeViews.swift:491
        RoomArt(if (dark && MeArt.has(bg + "_dark")) bg + "_dark" else bg, modifier, gray, 0f)   // ios: Views/MeViews.swift:492
    } else {   // ios: Views/MeViews.swift:493
        val i = bg?.let { id -> Shelter.backgrounds.indexOfFirst { it.id == id }.takeIf { it >= 0 } }   // ios: Views/MeViews.swift:494
        RoomArt(if (dark && MeArt.has("art_room_dark")) "art_room_dark" else "art_room", modifier, gray, ((i ?: -1) + 1) * 30f)   // ios: Views/MeViews.swift:495
    }
}

/** 쉼터 아래 '쉼터 배경' 상자 (접어 둠). 처음 연 날부터 하루 2곳씩 열린다 */
// ios: Views/MeViews.swift:501,502,503,506
@Composable
fun ShelterBGBox(m: AppModel, @Suppress("UNUSED_PARAMETER") support: Boolean = false, @Suppress("UNUSED_PARAMETER") setSupport: (Boolean) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }   // ios: Views/MeViews.swift:504
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, Theme.border, RoundedCornerShape(18.dp))   // ios: Views/MeViews.swift:533
            .clip(RoundedCornerShape(18.dp))   // ios: Views/MeViews.swift:532
            .background(Theme.card),   // ios: Views/MeViews.swift:531
        verticalArrangement = Arrangement.spacedBy(0.dp),   // ios: Views/MeViews.swift:507
    ) {
        // ios: Views/MeViews.swift:508
        val ease = tween<Float>(200, easing = LinearOutSlowInEasing); val onClick = { open = !open }
        Row(
            Modifier.fillMaxWidth().plainClickable(onClick)   // ios: Views/MeViews.swift:518,520
                .semantics(mergeDescendants = true) { contentDescription = "쉼터 배경, ${if (open) "펼침" else "접힘"}" }   // Views/MeViews.swift:521 (검사 예외: 보간 안 따옴표)
                .padding(horizontal = 14.dp).heightIn(min = 44.dp),   // ios: Views/MeViews.swift:517
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/MeViews.swift:509
        ) {
            val ink = Theme.ink   // ios: Views/MeViews.swift:516
            val rot by animateFloatAsState(if (open) 90f else 0f, ease, label = "chev")   // ios: Views/MeViews.swift:511
            Sym("chevron.right", 12f, Theme.teal, Modifier.rotate(rot))   // ios: Views/MeViews.swift:510
            T("쉼터 배경", style = appFont(14f, FontWeight.Bold), color = ink)   // ios: Views/MeViews.swift:512
            Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/MeViews.swift:513
            T(Shelter.background(m.roomBG)?.name ?: "기본 쉼터", style = appFont(12f), color = Theme.muted, maxLines = 1)   // ios: Views/MeViews.swift:514
        }
        AnimatedVisibility(open, enter = expandVertically(tween(200, easing = LinearOutSlowInEasing)) + fadeIn(ease),   // ios: Views/MeViews.swift:522
            exit = shrinkVertically(tween(200, easing = LinearOutSlowInEasing)) + fadeOut(ease)) {
            Column {
                HDivider(Theme.line)   // ios: Views/MeViews.swift:523
                BGRow(m, null, "기본 쉼터", "처음 방")   // ios: Views/MeViews.swift:524
                Shelter.backgrounds.forEach { b ->   // ios: Views/MeViews.swift:525
                    HDivider(Theme.line)   // ios: Views/MeViews.swift:526
                    BGRow(m, b.id, b.name, b.short, b.volume)   // ios: Views/MeViews.swift:527
                }
            }
        }
    }
}

// ios: Views/MeViews.swift:536
@Composable
private fun BGRow(m: AppModel, id: String?, name: String, intro: String, volume: String? = null) {
    // 하루 2곳씩 열린다. 아직이면 열리는 날을 보여 준다
    val locked = !m.bgOn(id); val on = !locked && m.meBg == id   // ios: Views/MeViews.swift:538
    Row(
        Modifier.fillMaxWidth()   // ios: Views/MeViews.swift:556
            .plainClickable { if (!locked) m.meBg = id }   // ios: Views/MeViews.swift:539,558
            .semantics(mergeDescendants = true) { contentDescription = (volume?.let { it + " " } ?: "") + name + (if (on) ", 지금 배경" else if (locked) ", 잠김" else "") }   // ios: Views/MeViews.swift:559
            .padding(horizontal = 14.dp).heightIn(min = 56.dp),   // ios: Views/MeViews.swift:555
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,   // ios: Views/MeViews.swift:540
    ) {
        val ink = Theme.ink   // ios: Views/MeViews.swift:554
        RoomImage(bg = id, modifier = Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(6.dp))   // ios: Views/MeViews.swift:541
            .alpha(if (locked && id != null) 0.6f else 1f), gray = if (locked && id != null) 1f else 0f)   // ios: Views/MeViews.swift:542
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/MeViews.swift:543
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/MeViews.swift:544
                T(name, style = appFont(14f, FontWeight.Bold), color = ink)   // ios: Views/MeViews.swift:545
                if (volume != null) T(volume, style = appFont(11f), color = Theme.muted)   // ios: Views/MeViews.swift:546
            }
            val day = id?.let { m.bgDay(it) } ?: 1; T(if (locked) "${day}일째 열림" else intro, style = appFont(12f), color = Theme.sub, maxLines = 2)   // ios: Views/MeViews.swift:548
        }
        Spacer(Modifier.widthIn(min = 0.dp))   // ios: Views/MeViews.swift:550 (글 칸이 남는 폭을 다 쓴다)
        if (on) Sym("checkmark", 14f, Theme.teal)   // ios: Views/MeViews.swift:551
        else if (locked && id != null) Sym("lock.fill", 12f, Theme.muted)   // ios: Views/MeViews.swift:552
    }
}

/** 둥근 머리만 (사용량 잠금 화면 위젯과 같은 자르기: 얼굴 그림을 위쪽 맞춤으로 키워 동그랗게) */
// ios: Views/MeViews.swift:564,565,566,567,568
@Composable
fun CharHead(id: String, size: Dp, model: AppModel? = null) {
    val s = size / 31   // ios: Views/MeViews.swift:569
    Box(Modifier.size(size, size).clip(CircleShape), contentAlignment = Alignment.Center) {   // ios: Views/MeViews.swift:582,583
        Box(Modifier.requiredSize(40 * s, 40 * s)   // ios: Views/MeViews.swift:580
            .offset(y = 3 * s)) {   // ios: Views/MeViews.swift:581
            Box(Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true)) {   // ios: Views/MeViews.swift:570
                if (id == "me") {   // ios: Views/MeViews.swift:571
                    val me = model?.me ?: MeChar.current; if (me != null) MeAvatar(me = me, width = 40 * s, height = 58 * s)   // ios: Views/MeViews.swift:572
                    else Spacer(Modifier)   // ios: Views/MeViews.swift:573 Color.clear
                } else {   // ios: Views/MeViews.swift:574
                    // 원래 색 캐릭터 그림에서 머리만 잘라 씀 (u_*_face는 잠금화면 위젯용 색)
                    PixelFit("spr_$id",   // ios: Views/MeViews.swift:576
                        Modifier.size(40 * s, 58 * s))   // ios: Views/MeViews.swift:577
                }
            }
        }
    }
}

/** 내 캐릭터 위젯 포즈 28장 (widget-drafts/me-poses/pose.py 와 같은 규칙). 몸·상의의 팔 칸을 지우고 포즈별 팔 9마디에 원래 팔 색을 옮긴다.
 *  그림 폭 32(좌우 4px 여백). 위젯은 me_<포즈>.png 를 읽는다 */
// ios: Views/MeViews.swift:590
object MePose {
    /** l·r: 팔 9마디 × [바깥칸 x,y, 안쪽칸 x,y] (24×34 기준). prop 0 오른손 · 1 왼손(좌우 뒤집기) · 2 숨김. ex: 손에 든 물건 [x,y,w,h,색] */
    class P(val l: List<Int>, val r: List<Int>, val dx: Int, val dy: Int, val prop: Int, val ex: List<List<Int>>)   // ios: Views/MeViews.swift:592
    const val W = 32; const val PAD = 4   // ios: Views/MeViews.swift:593
    val restL: List<Int> = (0 until 9).flatMap { listOf(5, 16 + it, 6, 16 + it) }   // ios: Views/MeViews.swift:594
    val restR: List<Int> = (0 until 9).flatMap { listOf(18, 16 + it, 17, 16 + it) }   // ios: Views/MeViews.swift:595
    val all: Map<String, P> = mapOf(   // ios: Views/MeViews.swift:596
        "pose_trend_up" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,4,12,5,12,4,11,5,11,4,10,5,10,4,9,5,9,3,8,4,8), restR, 0, 0, 0, listOf()),   // ios: Views/MeViews.swift:597
        "pose_trend_down" to P(listOf(6,16,6,17,5,16,5,17,4,16,4,17,3,16,3,17,2,16,2,17,1,16,1,17,0,15,0,16,-1,15,-1,16,-2,15,-2,16), restR, 0, 0, 0, listOf()),   // ios: Views/MeViews.swift:598
        "pose_trend_refresh1" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,5,12,6,12,5,11,6,11,5,10,6,10,5,9,6,9,5,8,6,8), restR, 0, 0, 0, listOf()),   // ios: Views/MeViews.swift:599
        "pose_trend_refresh2" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,4,12,5,12,4,11,5,11,4,10,5,10,4,9,5,9,3,8,4,8), restR, 0, -1, 0, listOf()),   // ios: Views/MeViews.swift:600
        "pose_push_up" to P(restL, listOf(17,17,17,18,18,17,18,18,19,17,19,18,20,17,20,18,21,17,21,18,22,17,22,18,23,17,23,18,24,17,24,18,25,17,25,18), 0, 0, 1, listOf()),   // ios: Views/MeViews.swift:601
        "pose_push_down" to P(restL, listOf(17,17,17,18,17,17,17,18,18,17,18,18,19,18,19,19,19,18,19,19,20,18,20,19,21,19,21,20,21,19,21,20,22,19,22,20), -1, 0, 1, listOf()),   // ios: Views/MeViews.swift:602
        "pose_push_refresh1" to P(restL, listOf(17,16,17,17,18,16,18,17,19,16,19,17,20,16,20,17,21,16,21,17,22,16,22,17,23,16,23,17,24,16,24,17,25,16,25,17), 0, 0, 1, listOf()),   // ios: Views/MeViews.swift:603
        "pose_push_refresh2" to P(restL, listOf(17,17,17,18,18,17,18,18,19,17,19,18,20,17,20,18,21,17,21,18,22,17,22,18,23,17,23,18,24,17,24,18,25,17,25,18), 1, 0, 1, listOf()),   // ios: Views/MeViews.swift:604
        "pose_block_up" to P(restL, listOf(17,17,17,18,18,17,18,18,19,17,19,18,20,17,20,18,21,17,21,18,22,17,22,18,23,17,23,18,24,17,24,18,25,17,25,18), 0, 0, 1, listOf(listOf(25,15,3,3,0xF0A268))),   // ios: Views/MeViews.swift:605
        "pose_block_down" to P(listOf(5,16,6,16,5,17,6,17,5,18,6,18,7,19,7,20,8,19,8,20,9,19,9,20,10,19,10,20,11,19,11,20,12,19,12,20), listOf(18,16,17,16,18,17,17,17,18,18,17,18,16,20,16,21,15,20,15,21,14,20,14,21,13,20,13,21,12,20,12,21,11,20,11,21), 0, 0, 2, listOf(listOf(10,16,4,3,0xF0A268))),   // ios: Views/MeViews.swift:606
        "pose_block_refresh1" to P(restL, listOf(17,16,17,17,18,16,18,17,19,16,19,17,20,16,20,17,21,16,21,17,22,16,22,17,23,16,23,17,24,16,24,17,25,16,25,17), 0, -1, 1, listOf(listOf(25,14,3,3,0xF0A268))),   // ios: Views/MeViews.swift:607
        "pose_block_refresh2" to P(restL, listOf(17,17,17,18,18,17,18,18,19,17,19,18,20,17,20,18,21,17,21,18,22,17,22,18,23,17,23,18,24,17,24,18,25,17,25,18), 0, 0, 1, listOf()),   // ios: Views/MeViews.swift:608
        "pose_point_up" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,4,12,5,12,4,11,5,11,4,10,5,10,4,9,5,9,3,8,4,8), listOf(17,16,17,17,18,17,18,18,19,17,19,18,20,18,20,19,21,18,21,19,22,19,22,20,23,20,23,21,24,20,24,21,25,21,25,22), 0, 0, 2, listOf()),   // ios: Views/MeViews.swift:609
        "pose_point_down" to P(listOf(5,16,6,16,5,15,6,15,6,14,7,14,6,13,7,13,7,12,8,12,7,11,8,11,7,10,8,10,8,9,9,9,8,8,9,8), listOf(17,16,17,17,18,17,18,18,19,17,19,18,20,18,20,19,21,18,21,19,22,19,22,20,23,20,23,21,24,20,24,21,25,21,25,22), 0, 0, 2, listOf()),   // ios: Views/MeViews.swift:610
        "pose_point_refresh1" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,4,12,5,12,4,11,5,11,4,10,5,10,4,9,5,9,3,8,4,8), listOf(17,16,17,17,18,16,18,17,19,17,19,18,20,17,20,18,21,17,21,18,22,18,22,19,23,18,23,19,24,18,24,19,25,18,25,19), 0, 0, 2, listOf()),   // ios: Views/MeViews.swift:611
        "pose_point_refresh2" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,4,12,5,12,4,11,5,11,4,10,5,10,4,9,5,9,3,8,4,8), listOf(17,16,17,17,18,17,18,18,19,17,19,18,20,18,20,19,21,18,21,19,22,19,22,20,23,20,23,21,24,20,24,21,25,21,25,22), 0, 0, 2, listOf()),   // ios: Views/MeViews.swift:612
        "pose_point_refresh3" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,4,12,5,12,4,11,5,11,4,10,5,10,4,9,5,9,3,8,4,8), listOf(17,16,17,17,18,17,18,18,19,18,19,19,20,19,20,20,21,20,21,21,22,21,22,22,23,22,23,23,24,23,24,24,25,24,25,25), 0, 0, 2, listOf()),   // ios: Views/MeViews.swift:613
        "pose_measure_base" to P(restL, listOf(18,16,17,16,18,15,17,15,18,14,17,14,18,13,17,13,18,12,17,12,18,11,17,11,18,10,17,10,18,9,17,9,18,8,17,8), 0, 0, 1, listOf()),   // ios: Views/MeViews.swift:614
        "pose_measure_up" to P(restL, listOf(18,16,17,16,18,15,17,15,18,14,17,14,18,13,17,13,18,12,17,12,18,11,17,11,18,10,17,10,18,9,17,9,18,8,17,8), 0, -2, 1, listOf()),   // ios: Views/MeViews.swift:615
        "pose_measure_down" to P(restL, listOf(18,16,17,16,18,15,17,15,18,14,17,14,18,13,17,13,19,12,18,12,19,11,18,11,19,10,18,10,19,9,18,9,20,8,19,8), 0, 0, 1, listOf()),   // ios: Views/MeViews.swift:616
        "pose_puzzle_base" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,5,12,6,12,5,11,6,11,5,10,6,10,5,9,6,9,5,8,6,8), listOf(18,16,17,16,18,15,17,15,18,14,17,14,18,13,17,13,18,12,17,12,18,11,17,11,18,10,17,10,18,9,17,9,18,8,17,8), 0, 0, 2, listOf(listOf(16,5,3,3,0xF0A268))),   // ios: Views/MeViews.swift:617
        "pose_puzzle_refresh" to P(listOf(5,16,6,16,5,15,6,15,5,14,6,14,5,13,6,13,5,12,6,12,5,11,6,11,5,10,6,10,5,9,6,9,5,8,6,8), listOf(18,16,17,16,18,15,17,15,18,14,17,14,18,13,17,13,18,12,17,12,18,11,17,11,18,10,17,10,18,9,17,9,18,8,17,8), 0, 0, 2, listOf(listOf(16,4,3,3,0xF0A268))),   // ios: Views/MeViews.swift:618
        "pose_garden_base" to P(restL, listOf(17,16,17,17,18,16,18,17,19,17,19,18,20,17,20,18,21,18,21,19,22,18,22,19,23,18,23,19,24,19,24,20,25,19,25,20), 0, 0, 1, listOf(listOf(24,19,4,3,0x4A6FB0))),   // ios: Views/MeViews.swift:619
        "pose_garden_refresh" to P(restL, listOf(17,16,17,17,18,17,18,18,19,17,19,18,20,18,20,19,21,18,21,19,22,19,22,20,23,20,23,21,24,20,24,21,25,21,25,22), 0, 0, 1, listOf(listOf(24,21,4,3,0x4A6FB0), listOf(27,25,1,1,0x9FD3F0), listOf(26,27,1,1,0x9FD3F0))),   // ios: Views/MeViews.swift:620
        "pose_tug_right" to P(restL, listOf(17,18,17,19,18,18,18,19,19,18,19,19,20,18,20,19,21,18,21,19,22,18,22,19,23,18,23,19,24,18,24,19,25,18,25,19), 1, 0, 1, listOf()),   // ios: Views/MeViews.swift:621
        "pose_tug_left" to P(listOf(6,16,6,17,5,16,5,17,4,16,4,17,3,16,3,17,2,16,2,17,1,16,1,17,0,15,0,16,-1,15,-1,16,-2,15,-2,16), restR, -1, 0, 0, listOf()),   // ios: Views/MeViews.swift:622
        "pose_tug_tie" to P(listOf(5,16,6,16,5,17,6,17,5,18,6,18,7,19,7,20,8,19,8,20,9,19,9,20,10,19,10,20,11,19,11,20,12,19,12,20), listOf(18,16,17,16,18,17,17,17,18,18,17,18,16,20,16,21,15,20,15,21,14,20,14,21,13,20,13,21,12,20,12,21,11,20,11,21), 0, 0, 2, listOf()),   // ios: Views/MeViews.swift:623
        "pose_tug_refresh" to P(restL, listOf(18,16,17,16,18,15,17,15,18,14,17,14,18,13,17,13,18,12,17,12,18,11,17,11,18,10,17,10,18,9,17,9,18,8,17,8), 0, 0, 1, listOf()),   // ios: Views/MeViews.swift:624
    )

    /** RGB → 버퍼 한 칸 (안드로이드 ARGB) */
    private fun px(rgb: Int): Int = (0xFF shl 24) or ((rgb shr 16 and 0xFF) shl 16) or (rgb and 0xFF00) or (rgb and 0xFF)   // ios: Views/MeViews.swift:628

    /** 부품이 다 있어야 그린다 (자리표시 도트일 땐 null → 위젯은 서 있는 me.png). IO 에서 부른다 */
    // ios: Views/MeViews.swift:631
    fun image(me: MeChar, pose: String): Bitmap? {
        val p = all[pose] ?: return null   // ios: Views/MeViews.swift:632
        val w = MeArt.W; val h = MeArt.H; val cw = W   // ios: Views/MeViews.swift:633
        fun pixels(name: String): IntArray? {   // ios: Views/MeViews.swift:634
            val cg = meBitmap(name) ?: return null   // ios: Views/MeViews.swift:635
            val buf = IntArray(w * h) { 0 }   // ios: Views/MeViews.swift:636
            val ok = runCatching {   // ios: Views/MeViews.swift:637
                // Views/MeViews.swift:638 (검사 예외: CGContext 비트 수·한 줄 바이트 = ARGB_8888 에 정해져 있음)
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val ctx = BmpCanvas(out)   // ios: Views/MeViews.swift:639
                val paint = Paint().apply { isFilterBitmap = false }   // ios: Views/MeViews.swift:640
                ctx.drawBitmap(cg, null, Rect(0, 0, w, h), paint)   // ios: Views/MeViews.swift:641
                out.getPixels(buf, 0, w, 0, 0, w, h); true   // ios: Views/MeViews.swift:642
            }.getOrDefault(false)
            return if (ok) buf else null   // ios: Views/MeViews.swift:644
        }
        val layers = mutableListOf<Pair<String, IntArray>>()   // ios: Views/MeViews.swift:646
        for (layer in MeGroup.layers) {   // ios: Views/MeViews.swift:647
            val n = MeArt.option(me, layer)   // ios: Views/MeViews.swift:648
            if (n <= 0) continue   // ios: Views/MeViews.swift:649
            val b = pixels(MeArt.asset(layer, n, me)) ?: return null   // ios: Views/MeViews.swift:650
            layers.add(layer to b)   // ios: Views/MeViews.swift:651
        }
        // 원래 팔 색을 떼어 두고 몸·상의에서 팔 칸을 지운다 (키: 쪽*100 + 마디*2 + 바깥0/안쪽1)
        val tex = HashMap<Int, Int>()   // ios: Views/MeViews.swift:654
        for (k in layers.indices) { if (layers[k].first != "body" && layers[k].first != "top") continue   // ios: Views/MeViews.swift:655
            for ((s, rest) in listOf(restL, restR).withIndex()) {   // ios: Views/MeViews.swift:656
                for (i in 0 until 9) {   // ios: Views/MeViews.swift:657
                    for (j in 0 until 2) {   // ios: Views/MeViews.swift:658
                        val at = rest[i * 4 + j * 2 + 1] * w + rest[i * 4 + j * 2]   // ios: Views/MeViews.swift:659
                        if (layers[k].second[at] ushr 24 != 0) tex[s * 100 + i * 2 + j] = layers[k].second[at]   // ios: Views/MeViews.swift:660
                        layers[k].second[at] = 0   // ios: Views/MeViews.swift:661
                    }
                }
            }
        }
        val cv = IntArray(cw * h) { 0 }; val torso = BooleanArray(cw * h)   // ios: Views/MeViews.swift:666
        fun put(src: IntArray, flip: Boolean = false, mark: Boolean = false) {   // ios: Views/MeViews.swift:667
            for (y in 0 until h) {   // ios: Views/MeViews.swift:668
                for (x in 0 until w) {   // ios: Views/MeViews.swift:669
                    val v = src[y * w + (if (flip) w - 1 - x else x)]   // ios: Views/MeViews.swift:670
                    val X = x + PAD + p.dx; val Y = y + p.dy   // ios: Views/MeViews.swift:671
                    if (v ushr 24 == 0 || X < 0 || X >= cw || Y < 0 || Y >= h) continue   // ios: Views/MeViews.swift:672
                    cv[Y * cw + X] = v   // ios: Views/MeViews.swift:673
                    if (mark) torso[Y * cw + X] = true   // ios: Views/MeViews.swift:674
                }
            }
        }
        for ((k, b) in layers) if (k != "prop") put(b, mark = k == "body" || k == "top" || k == "bottom")   // ios: Views/MeViews.swift:678
        // 새 팔 (키: y*1000 + x). 몸 앞을 지나면 테두리선, 어깨 이음새(첫 두 마디 근처)만 빼고
        val arm = HashMap<Int, Int>()   // ios: Views/MeViews.swift:680
        val root = HashSet<Int>()   // ios: Views/MeViews.swift:681
        for ((s, a) in listOf(p.l, p.r).withIndex()) {   // ios: Views/MeViews.swift:682
            for (i in 0 until 9) {   // ios: Views/MeViews.swift:683
                for (j in 0 until 2) {   // ios: Views/MeViews.swift:684
                    val X = a[i * 4 + j * 2] + PAD + p.dx; val Y = a[i * 4 + j * 2 + 1] + p.dy   // ios: Views/MeViews.swift:685
                    if (i < 2) for ((ox, oy) in listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)) root.add((Y + oy) * 1000 + X + ox)   // ios: Views/MeViews.swift:686
                    tex[s * 100 + i * 2 + j]?.let { v -> arm[Y * 1000 + X] = v }   // ios: Views/MeViews.swift:687
                }
            }
        }
        val line = px(MeArt.outline)   // ios: Views/MeViews.swift:691
        for (key in arm.keys) {   // ios: Views/MeViews.swift:692
            val x = key % 1000; val y = key / 1000   // ios: Views/MeViews.swift:693
            for ((nx, ny) in listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) { if (nx < 0 || nx >= cw || ny < 0 || ny >= h) continue   // ios: Views/MeViews.swift:694
                val i = ny * cw + nx   // ios: Views/MeViews.swift:695
                if (arm[ny * 1000 + nx] != null || cv[i] ushr 24 == 0 || (torso[i] && root.contains(ny * 1000 + nx))) continue   // ios: Views/MeViews.swift:696
                cv[i] = line   // ios: Views/MeViews.swift:697
            }
        }
        for ((key, v) in arm) if (key % 1000 < cw && key / 1000 < h) cv[(key / 1000) * cw + key % 1000] = v   // ios: Views/MeViews.swift:700
        for (e in p.ex) {   // ios: Views/MeViews.swift:701
            val x0 = e[0] + PAD + p.dx; val y0 = e[1] + p.dy; val c = px(e[4])   // ios: Views/MeViews.swift:702
            for (y in (y0 - 1)..(y0 + e[3])) {   // ios: Views/MeViews.swift:703
                for (x in (x0 - 1)..(x0 + e[2])) { if (x < 0 || x >= cw || y < 0 || y >= h) continue   // ios: Views/MeViews.swift:704
                    cv[y * cw + x] = if (x >= x0 && x < x0 + e[2] && y >= y0 && y < y0 + e[3]) c else line   // ios: Views/MeViews.swift:705
                }
            }
        }
        if (p.prop != 2) layers.firstOrNull { it.first == "prop" }?.second?.let { put(it, flip = p.prop == 1) }   // ios: Views/MeViews.swift:709
        // 앱 안 그림과 같은 음영 (위에서 오는 빛)
        PixBuf.shade(cv, cw, h, head = 16 + p.dy, legTop = MeMotion.legTop + p.dy)   // ios: Views/MeViews.swift:711
        // 바깥 테두리 (MeArt.image 와 같은 규칙)
        val filled = BooleanArray(cv.size) { cv[it] ushr 24 != 0 }   // ios: Views/MeViews.swift:713
        for (y in 0 until h) {   // ios: Views/MeViews.swift:714
            for (x in 0 until cw) { if (filled[y * cw + x]) continue   // ios: Views/MeViews.swift:715
                if ((x > 0 && filled[y * cw + x - 1]) || (x < cw - 1 && filled[y * cw + x + 1])   // ios: Views/MeViews.swift:716
                    || (y > 0 && filled[(y - 1) * cw + x]) || (y < h - 1 && filled[(y + 1) * cw + x])) cv[y * cw + x] = line   // ios: Views/MeViews.swift:717
            }
        }
        // 8배로 키워 me.png(8배)와 같은 해상도
        val s = 8; val bw = cw * s; val bh = h * s   // ios: Views/MeViews.swift:721
        val big = IntArray(bw * bh) { 0 }   // ios: Views/MeViews.swift:722
        for (y in 0 until bh) { for (x in 0 until bw) { big[y * bw + x] = cv[(y / s) * cw + x / s] } }   // ios: Views/MeViews.swift:723
        val cg: Bitmap? = runCatching {   // ios: Views/MeViews.swift:724
            // Views/MeViews.swift:725 (검사 예외: CGContext 비트 수·한 줄 바이트 = ARGB_8888 에 정해져 있음)
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).apply { setPixels(big, 0, bw, 0, 0, bw, bh) }   // ios: Views/MeViews.swift:726
        }.getOrNull()
        return cg   // ios: Views/MeViews.swift:728
    }
}

// ── 이 파일 안 도움 (Swift 에 따로 없는 안드로이드 자리) ──

/** UIImage(named:) 자리: 도트 그림을 원래 크기(nodpi, 축소 없음)로 한 번만 읽어 둔다. 아무 스레드에서나 */
private val bitmaps = HashMap<String, Bitmap?>()

@SuppressLint("DiscouragedApi")
internal fun meBitmap(name: String): Bitmap? {
    synchronized(bitmaps) {
        if (bitmaps.containsKey(name)) return bitmaps[name]
        val res = Store.ctx.resources
        val id = res.getIdentifier(name, "drawable", Store.ctx.packageName)
        val b = if (id == 0) null else BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
        bitmaps[name] = b
        return b
    }
}

/** 부품 그림 한 장을 w×h 버퍼(ARGB, 위 줄부터)로. 크기가 다르면 최근접으로 맞춘다 */
internal fun mePixels(name: String, w: Int = MeArt.W, h: Int = MeArt.H): IntArray? {
    val b = meBitmap(name) ?: return null
    val s = if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, false)
    return IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }
}

private fun Bitmap.pngData(): ByteArray = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

/** 방 그림: 보간 없이 판에 꽉 채운다. hue = .hueRotation(도), gray = .grayscale(0~1) */
@Composable
private fun RoomArt(name: String, modifier: Modifier, gray: Float, hue: Float) {
    val id = drawableId(LocalContext.current, name)
    if (id == 0) { Box(modifier); return }
    val img = ImageBitmap.imageResource(id)
    val filter = remember(hue, gray) {
        if (hue == 0f && gray == 0f) null
        else ColorMatrix().apply { setToSaturation(1f - gray) }.also { if (hue != 0f) it.timesAssign(hueMatrix(hue)) }.let { ColorFilter.colorMatrix(it) }
    }
    Image(BitmapPainter(img, filterQuality = FilterQuality.None), null, modifier, contentScale = ContentScale.FillBounds, colorFilter = filter)
}

/** SwiftUI .hueRotation 과 같은 색 돌리기 행렬 (밝기 유지) */
private fun hueMatrix(deg: Float): ColorMatrix {
    val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
    return ColorMatrix(floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
}

/** 기본 인물 그림을 비율 그대로(.aspectRatio(.fit)) 보간 없이 */
@Composable
private fun PixelFit(name: String, modifier: Modifier) {
    val id = drawableId(LocalContext.current, name)
    if (id == 0) { Box(modifier); return }
    Image(BitmapPainter(ImageBitmap.imageResource(id), filterQuality = FilterQuality.None), null, modifier, contentScale = ContentScale.Fit)
}

/** .lineLimit(n).minimumScaleFactor(k) 의 여러 줄 판: n 줄에 안 들어가면 글자를 k 배까지 줄인다 */
@Composable
private fun FitLines(text: String, style: TextStyle, maxLines: Int, minScale: Float, modifier: Modifier = Modifier, color: Color = LocalFg.current(LocalPal.current)) {
    val measurer = rememberTextMeasurer()
    SubcomposeLayout(modifier) { c ->
        var k = 1f
        while (k > minScale) {
            val r = measurer.measure(text, style.copy(fontSize = style.fontSize * k), maxLines = maxLines, constraints = Constraints(maxWidth = c.maxWidth))
            if (!r.hasVisualOverflow) break
            k -= 0.05f
        }
        val st = style.copy(fontSize = style.fontSize * max(k, minScale), color = color)
        val p = subcompose(k) { BasicText(text, style = st, maxLines = maxLines, overflow = TextOverflow.Ellipsis) }.first().measure(c.copy(minWidth = 0))
        layout(p.width, p.height) { p.place(0, 0) }
    }
}
