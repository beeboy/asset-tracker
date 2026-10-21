package com.naeilo.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.random.Random

// 시안(목업 49판)의 색. 오르면 빨강, 내리면 파랑 (국내 관례).
// 다크 모드: 같은 이름의 색을 어두운 화면용 값으로 바꿔 쓴다 (폰 설정 또는 앱 설정의 화면 모드).
// 색은 밝은·어두운 두 벌(Palette)을 한 번만 만들고, 화면은 LocalPal 로 지금 벌을 읽는다 (매번 새로 만들지 않게).
// ios: Theme.swift:5,6,7
fun hex(h: Long, opacity: Float = 1f): Color = Color(
    red = ((h shr 16) and 0xFF) / 255f,   // ios: Theme.swift:8
    green = ((h shr 8) and 0xFF) / 255f,  // ios: Theme.swift:9
    blue = (h and 0xFF) / 255f,           // ios: Theme.swift:10
    alpha = opacity,                      // ios: Theme.swift:11
)

// ios: Theme.swift:13
class Palette(val isDark: Boolean) {
    // ios: Theme.swift:14,15
    private fun ui(h: Long) = Color(((h shr 16) and 0xFF) / 255f, ((h shr 8) and 0xFF) / 255f, (h and 0xFF) / 255f, 1f)
    private fun c(light: Long, dark: Long = light) = if (isDark) ui(dark) else ui(light)   // ios: Theme.swift:17

    val bg = c(0xF2F4F6, 0x0E1318)            // ios: Theme.swift:22
    val card = c(0xFFFFFF, 0x1A222B)          // ios: Theme.swift:23 흰 카드
    val ink = c(0x15202B, 0xE8EDF2)           // ios: Theme.swift:24 본문 글자
    val inkFixed = c(0x15202B)                // ios: Theme.swift:25 밝은 칩(민트·노랑) 위 글자
    val night = c(0x15202B, 0x1E2A36)         // ios: Theme.swift:26 어두운 요약 카드 바탕
    val teal = c(0x0B6B66, 0x2A9D93)          // ios: Theme.swift:27
    val tealDark = c(0x0B4F4B, 0x8FD8CE)      // ios: Theme.swift:28
    val mint = c(0x5FD0C4)                    // ios: Theme.swift:29
    val mintBg = c(0xF3FAF9, 0x16302D)        // ios: Theme.swift:30
    val missionGreen = c(0x2E6B63, 0x24564F)  // ios: Theme.swift:31 미션 탭 다음 미션 블록
    val tealBg = c(0xE3F1EF, 0x173531)        // ios: Theme.swift:32
    val sub = c(0x4A5560, 0xAEB8C2)           // ios: Theme.swift:33
    val sub2 = c(0x5B6670, 0x9AA5AF)          // ios: Theme.swift:34
    val muted = c(0x8A949E, 0x7F8A95)         // ios: Theme.swift:35
    val border = c(0xDCE1E6, 0x2E3945)        // ios: Theme.swift:36
    val line = c(0xEEF1F3, 0x26303A)          // ios: Theme.swift:37
    val track = c(0xE6EAEE, 0x2C3742)         // ios: Theme.swift:38
    val dash = c(0xC9D0D6, 0x3E4A56)          // ios: Theme.swift:39
    val yellow = c(0xF2C14E)                  // ios: Theme.swift:40
    val gold = c(0xF3CF6A)                    // ios: Theme.swift:41
    val cream = c(0xFFF7E0, 0x3A321C)         // ios: Theme.swift:42
    val slate = c(0x3A4651)                   // ios: Theme.swift:43
    val shelter = c(0x1E2436, 0x1A2032)       // ios: Theme.swift:44
    val up = c(0xC0392B, 0xF07468)            // ios: Theme.swift:45
    val down = c(0x1B5E96, 0x6FB0EA)          // ios: Theme.swift:46
    val orange = c(0xE8862A)                  // ios: Theme.swift:47
    val purple = c(0x7A4FC8, 0x9B78E0)        // ios: Theme.swift:48
    val blue = c(0x2F8FD8)                    // ios: Theme.swift:49
    val green = c(0x12A06E)                   // ios: Theme.swift:50
    val forecast = c(0x2C62A8, 0x6FA6E8)      // ios: Theme.swift:51 이번 주 예보 글자·고른 칸
    val forecastBg = c(0xEEF4FB, 0x16263A)    // ios: Theme.swift:52 예보 설명 상자
    val brown = c(0x8A5A1E, 0xD9A86A)         // ios: Theme.swift:53 미니 미션 작은 제목
    val holdColors: List<Color> = listOf(hex(0x7A4FC8), hex(0x2F8FD8), hex(0x12A06E),   // ios: Theme.swift:54
                                         hex(0xE8862A), hex(0xC2477A), hex(0x5B6670))   // ios: Theme.swift:55

    // 쉬운 말 개편 색 규칙: 화면 사이에서 같은 뜻엔 같은 색 (유리/불리는 색맹을 고려해 파랑/주황)
    val myReal = c(0x2E6B63, 0x5FB3A6)    // ios: Theme.swift:58 내 실제 투자
    val basket = c(0xD9873A, 0xE9A564)    // ios: Theme.swift:59 모의 바구니
    val rule = c(0x6847B8, 0x9B78E0)      // ios: Theme.swift:60 인물 규칙 (점선과 함께)
    val hold = c(0x9AA1AC, 0x7F8792)      // ios: Theme.swift:61 그냥 둠
    val favor = c(0x2F6FB8, 0x6FA6E6)     // ios: Theme.swift:62 유리
    val against = c(0xD9873A, 0xE9A564)   // ios: Theme.swift:63 불리

    fun change(r: Double): Color = if (r >= 0) up else down   // ios: Theme.swift:65
    /** 가로 화면에서 내용 폭 상한 */
    val maxWidth: Dp = 720.dp   // ios: Theme.swift:67

    companion object {
        val Light = Palette(false)
        val Dark = Palette(true)
    }
}

/** 지금 화면 모드의 색 벌 (iOS 의 동적 색이 화면 모드를 따라가는 자리) */
val LocalPal = staticCompositionLocalOf { Palette.Light }

/** iOS 처럼 Theme.bg 로 쓴다 (enum Theme → 색 벌 두 개 + 지금 벌 읽기) */
// ios: Theme.swift:21
val Theme: Palette @Composable get() = LocalPal.current

/** foregroundStyle: 색 벌을 받아 색을 고르는 함수로 둬서, 진한 상자 안에선 같은 이름이 어두운 값으로 바뀐다 */
val LocalFg = staticCompositionLocalOf<(Palette) -> Color> { { it.ink } }

/** 지금 보이는 탭인지: 숨은 탭의 반복 애니메이션은 멈춘다 (iOS 는 화면에서 사라지면 task 가 끝난다) */
val LocalTabActive = compositionLocalOf { true }   // 바뀌면 읽는 곳만 다시 그린다

/** 동작 줄이기 (안드로이드: 개발자 옵션·접근성의 애니메이션 끄기) */
val LocalReduceMotion = staticCompositionLocalOf { false }

// 흰 카드 (시안의 border 1px · radius 18)
// ios: Theme.swift:71
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,                 // ios: Theme.swift:72
    bg: Color = Theme.card,              // ios: Theme.swift:73
    stroke: Color? = Theme.border,       // ios: Theme.swift:74
    /** 요약 박스: 홈의 지금 평가액처럼 진한 바탕 (안의 글자색은 어두운 모드 색을 쓴다) */
    dark: Boolean = false,               // ios: Theme.swift:76
    content: @Composable ColumnScope.() -> Unit,   // ios: Theme.swift:77
) {
    // ios: Theme.swift:78
    val fill = if (dark) Theme.night else bg   // 바탕은 바깥 화면 모드로 고른다 (iOS 와 같은 순서)
    DarkScheme(on = dark) {   // ios: Theme.swift:82
        Column(
            modifier
                .fillMaxWidth()   // ios: Theme.swift:80
                .background(fill, RoundedCornerShape(18.dp))   // ios: Theme.swift:83
                // ios: Theme.swift:84,85
                .then(if (stroke != null && !dark) Modifier.border(1.dp, stroke, RoundedCornerShape(18.dp)) else Modifier)
                .padding(padding),   // ios: Theme.swift:81
            verticalArrangement = Arrangement.spacedBy(10.dp),   // ios: Theme.swift:79 VStack(alignment: .leading)
        ) { content() }
    }
}

/** 진한 요약 박스 (Card 가 아닌 묶음에) */
// ios: Theme.swift:90,92,93,102,103,104,105
@Composable
fun DarkBox(modifier: Modifier = Modifier, radius: Dp = 18.dp, content: @Composable () -> Unit) {
    val night = Theme.night
    Box(modifier.background(night, RoundedCornerShape(radius))) { DarkScheme(on = true, content) }
}

// ios: Theme.swift:96,97,98,99
@Composable
private fun DarkScheme(on: Boolean, content: @Composable () -> Unit) {
    if (on) CompositionLocalProvider(LocalPal provides Palette.Dark, content = content) else content()
}

// ios: Theme.swift:109,110,111
@Composable
fun DashedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dash = Theme.dash
    CompositionLocalProvider(LocalFg provides { it.sub }) {   // ios: Theme.swift:116
        Column(
            modifier
                .fillMaxWidth()   // ios: Theme.swift:113
                // ios: Theme.swift:115 테두리 선은 가장자리 가운데에 그린다 (SwiftUI stroke 와 같게)
                .drawBehind {
                    drawRoundRect(dash, cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                }
                .padding(14.dp),   // ios: Theme.swift:114
            verticalArrangement = Arrangement.spacedBy(8.dp),   // ios: Theme.swift:112
        ) { content() }
    }
}

// 칩 한 줄: 누르면 같은 자리의 그래프·숫자가 바뀐다
// ios: Theme.swift:121
@Composable
fun <K> ChipRow(
    items: List<Pair<K, String>>,   // ios: Theme.swift:122
    selection: K,                   // ios: Theme.swift:123
    onSelect: (K) -> Unit,
    accent: Color = Theme.teal,     // ios: Theme.swift:124
    fill: Boolean = false,          // ios: Theme.swift:125
) {
    // ios: Theme.swift:126
    val style = appFont(13f, FontWeight.Bold)   // ios: Theme.swift:132
    @Composable fun chip(item: Pair<K, String>, mod: Modifier) {   // ios: Theme.swift:128
        val on = item.first == selection   // ios: Theme.swift:129
        Box(
            mod
                // ios: Theme.swift:130,140
                .plainClickable { onSelect(item.first) }
                .defaultMinSize(minHeight = 40.dp)   // ios: Theme.swift:135
                .background(if (on) accent else Theme.card, CapsuleShape)   // ios: Theme.swift:137
                .border(2.dp, if (on) accent else Theme.border, CapsuleShape)   // ios: Theme.swift:138
                .semantics { selected = on }   // ios: Theme.swift:141
                .padding(horizontal = if (fill) 8.dp else 14.dp),   // ios: Theme.swift:134
            contentAlignment = Alignment.Center,
        ) {
            T(
                item.second,   // ios: Theme.swift:131
                style = style,
                maxLines = 1,   // ios: Theme.swift:133
                color = if (on) Color.White else Theme.ink,   // ios: Theme.swift:136
            )
        }
    }
    // ios: Theme.swift:127
    val gap = 6.dp
    // 칸에 다 들어가면 같은 폭으로 채우고, 큰 글자라 넘치면 가로로 밀어 보게 한다
    // ios: Theme.swift:145
    @Composable fun scroll() = Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) { items.forEach { chip(it, Modifier) } }
    if (fill) {
        // ios: Theme.swift:146 ViewThatFits: 글자 폭을 재서 한 줄에 들어가면 같은 폭, 아니면 가로 스크롤
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val need = with(androidx.compose.ui.platform.LocalDensity.current) {
                items.sumOf { measurer.measure(com.naeilo.app.core.tr(it.second), style, maxLines = 1).size.width + (8.dp * 2).roundToPx() } +
                    (gap * (items.size - 1)).roundToPx()
            }
            if (need <= constraints.maxWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEach { chip(it, Modifier.weight(1f)) }
                }
            } else scroll()
        }
    } else scroll()
}

// ios: Theme.swift:150
@Composable
fun PrimaryButton(
    title: String,                    // ios: Theme.swift:151
    color: Color = Theme.teal,        // ios: Theme.swift:152
    fg: Color = Color.White,          // ios: Theme.swift:153
    action: () -> Unit,               // ios: Theme.swift:154
) {
    // ios: Theme.swift:155,156,162
    Box(
        Modifier
            .plainClickable(onClick = action)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)   // ios: Theme.swift:158
            .background(color, RoundedCornerShape(12.dp)),   // ios: Theme.swift:160
        contentAlignment = Alignment.Center,
    ) {
        T(title, style = appFont(16f, FontWeight.Bold), color = fg)   // ios: Theme.swift:157,159
    }
}

// 도트 그림은 보간 없이 키운다
// ios: Theme.swift:167
@Composable
fun Pixel(
    @DrawableRes name: Int,   // ios: Theme.swift:168
    width: Dp,                // ios: Theme.swift:169
    height: Dp,               // ios: Theme.swift:170
) {
    // ios: Theme.swift:171,172 interpolation(.none) = FilterQuality.None
    Image(ImageBitmap.imageResource(name), null, Modifier.size(width, height), filterQuality = FilterQuality.None,
        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
}

/**
 * 서 있는 인물 숨쉬기: 1pt 들썩였다 내려오고, 가끔 움찔한다. 늘리지 않아 픽셀이 뭉개지지 않는다.
 * 인물마다 박자·시작이 달라 다 같이 숨쉬지 않는다.
 * 화면에 있을 때만 돌고('동작 줄이기'면 끔), 걷기·숨 장면이 있는 인물(CharFigure·TownWalker)엔 붙이지 않는다.
 * 움직임은 그리기 직전(layout)에서만 읽어 화면을 다시 짜지 않는다.
 */
@Composable
fun Modifier.charIdle(seed: Int = 0): Modifier {   // ios: Theme.swift:179,180,210,211
    val reduce = LocalReduceMotion.current         // ios: Theme.swift:181
    val active = LocalTabActive.current
    var up by remember { mutableStateOf(false) }   // ios: Theme.swift:182
    val hop = remember { Animatable(0f) }          // ios: Theme.swift:183
    // ios: Theme.swift:184 (seed 는 iOS 에서도 박자에 안 쓴다: 박자는 무작위)
    LaunchedEffect(reduce, active) {   // ios: Theme.swift:187
        // ios: Theme.swift:201 화면 밖(숨은 탭)·동작 줄이기: 반복을 끊고 제자리
        if (reduce || !active) { up = false; hop.snapTo(0f); return@LaunchedEffect }   // ios: Theme.swift:188,204,205,206
        val beat = Random.nextDouble(1.2, 2.0)   // ios: Theme.swift:189
        delay((Random.nextDouble(0.0, beat) * 1000).toLong())   // ios: Theme.swift:190
        while (true) {   // ios: Theme.swift:191,193 (취소되면 delay 에서 끝난다)
            delay((beat * Random.nextDouble(0.8, 1.25) * 1000).toLong())   // ios: Theme.swift:192
            up = !up   // ios: Theme.swift:194
            if (up || Random.nextInt(0, 5) != 0) continue   // ios: Theme.swift:195
            hop.animateTo(-1.5f, tween(90, easing = EaseOut))   // ios: Theme.swift:196 easeOut 0.09초
            delay(110)   // ios: Theme.swift:197
            hop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = stiffness(response = 0.25f)))   // ios: Theme.swift:198
        }
    }
    // ios: Theme.swift:185,186
    return this.layout { m, c ->
        val p = m.measure(c)
        layout(p.width, p.height) { p.place(IntOffset(0, ((if (up) -1f else 0f) + hop.value).dp.roundToPx())) }
    }
}

/** SwiftUI .easeOut 곡선 */
val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
/** SwiftUI spring(response:) 를 Compose 강성으로: (2π/response)² */
fun stiffness(response: Float): Float = ((2 * PI / response) * (2 * PI / response)).toFloat()

// ios: Theme.swift:214
@Composable
fun ProgressBar(
    value: Double,                 // ios: Theme.swift:215
    height: Dp = 10.dp,            // ios: Theme.swift:216
    fill: Color = Theme.yellow,    // ios: Theme.swift:217
    track: Color = Theme.slate,    // ios: Theme.swift:218
    modifier: Modifier = Modifier,
) {
    // ios: Theme.swift:219,220,221,226
    Box(modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().background(track, CapsuleShape))   // ios: Theme.swift:222
        // ios: Theme.swift:223
        Box(Modifier.fillMaxHeight().fillMaxWidth(maxOf(0.0, minOf(1.0, value)).toFloat()).background(fill, CapsuleShape))
    }
}

// ios: Theme.swift:230
@Composable
fun ScreenTitle(
    kicker: String?,   // ios: Theme.swift:231
    title: String,     // ios: Theme.swift:232
) {
    // ios: Theme.swift:233,234,238
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (kicker != null) T(kicker, style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)   // ios: Theme.swift:235
        T(title, style = appFont(24f, FontWeight.Bold).copy(letterSpacing = (-0.4).sp))   // ios: Theme.swift:236
    }
}

// 탭 화면 공통 바탕
// ios: Theme.swift:242,244
@Composable
fun Screen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val maxW = Theme.maxWidth
    // ios: Theme.swift:247 가로 화면: 가운데 한 줄로
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        CompositionLocalProvider(LocalFg provides { it.ink }) {   // ios: Theme.swift:248
            Column(
                Modifier
                    .widthIn(max = maxW).fillMaxWidth()   // ios: Theme.swift:246
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),   // ios: Theme.swift:245
            ) { content() }
        }
    }
}

// 폰 설정의 글자 크기를 따른다.
// 시안의 px 크기를 iOS '기본 크기(큰)' 기준으로 두고, 설정에 맞춰 본문 글자와 같은 비율로 키운다.
// iOS 본문 글자 크기 단계(UIFontMetrics .body): xS 14 · S 15 · M 16 · L 17(기본) · XL 19 · XXL 21 · XXXL 23 · AX1 28 · AX2 33 · AX3 40
// 안드로이드 글자 배율을 가장 가까운 단계로 옮기고, iOS 처럼 AX3 에서 멈춘다 (아주 큰 글자에서도 화면이 무너지지 않게).
private val BODY_STEPS = floatArrayOf(14f, 15f, 16f, 17f, 19f, 21f, 23f, 28f, 33f, 40f)
private const val AX1 = 7   // 손쉬운 사용(아주 큰 글자) 단계가 시작하는 자리
private const val AX3 = 9

/** 글자 단계: 앱 시작·설정 변경 때 한 번만 계산한다 */
class TextScale(val step: Int) {
    val ratio: Float = BODY_STEPS[step] / 17f
    /** dts.isAccessibilitySize */
    val isAccessibilitySize: Boolean get() = step >= AX1

    companion object {
        fun from(fontScale: Float): TextScale {   // ios: Theme.swift:260,261
            val want = 17f * fontScale
            var best = 0
            BODY_STEPS.forEachIndexed { i, s -> if (kotlin.math.abs(s - want) < kotlin.math.abs(BODY_STEPS[best] - want)) best = i }
            return TextScale(minOf(best, AX3))   // ios: App.swift:18 .dynamicTypeSize(...accessibility3)
        }
    }
}

val LocalTextScale = staticCompositionLocalOf { TextScale(3) }

// ios: Theme.swift:254,255,256,257,259,262,266,267,268 (글자 단계 계산은 TextScale.from)
@Composable
fun appFont(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontSize = (size * LocalTextScale.current.ratio).sp, fontWeight = weight, lineBreak = KoLineBreak)

/** 한국어 줄바꿈을 iOS 처럼 띄어쓰기 단위로 (안드로이드 기본은 글자 단위라 "계\n산할"처럼 끊는다. API 33+ 에서 적용, 그 아래는 기본) */
val KoLineBreak = androidx.compose.ui.text.style.LineBreak(
    strategy = androidx.compose.ui.text.style.LineBreak.Strategy.Simple,
    strictness = androidx.compose.ui.text.style.LineBreak.Strictness.Normal,
    wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase,
)

/** 글자 한 줄: Text 자리. 색을 안 주면 foregroundStyle(LocalFg) 를 지금 색 벌로 고른다 */
@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = appFont(17f),
    color: Color = LocalFg.current(LocalPal.current),
    maxLines: Int = Int.MAX_VALUE,
    /** .roll(값): 값이 바뀌면 글자가 한 자리씩 위아래로 넘어간다 (Charts.kt RollText). null 이면 그냥 글자 */
    roll: Any? = null,
) {
    val text = com.naeilo.app.core.tr(text)   // 영어 기기면 영어로 (core/L10n.kt)
    if (roll != null) RollText(text, roll, modifier, style.copy(color = color))
    else BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

// 위 그래프를 고정하고 아래만 스크롤. 손쉬운 사용의 아주 큰 글자에서는 고정 영역이 화면을 다 차지하므로 함께 스크롤한다.
// ios: Theme.swift:273
@Composable
fun PinnedLayout(
    header: @Composable () -> Unit,    // ios: Theme.swift:276
    content: @Composable () -> Unit,   // ios: Theme.swift:277
) {
    val dts = LocalTextScale.current   // ios: Theme.swift:274
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    // ios: Theme.swift:275 세로 공간이 좁은 가로 화면 (iPhone 의 verticalSizeClass compact 자리)
    val vsc = conf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && conf.screenHeightDp < 500
    val maxW = Theme.maxWidth
    // ios: Theme.swift:278 아주 큰 글자나 가로 화면(세로 공간이 좁음)에서는 머리를 고정하지 않고 같이 스크롤
    if (dts.isAccessibilitySize || vsc) {   // ios: Theme.swift:280
        // ios: Theme.swift:281
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = maxW).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) { header(); content() }
        }
    } else {   // ios: Theme.swift:282
        // ios: Theme.swift:283
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            header()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
        }
    }
}

/** Capsule() */
val CapsuleShape = RoundedCornerShape(50)

/**
 * buttonStyle(.plain): 물결 없이, 누르는 동안만 살짝 흐리게 (iOS 기본 버튼 눌림과 같게).
 */
@Composable
fun Modifier.plainClickable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this.graphicsLayer { alpha = if (pressed) 0.6f else 1f }.clickable(src, indication = null, onClick = onClick)
}
