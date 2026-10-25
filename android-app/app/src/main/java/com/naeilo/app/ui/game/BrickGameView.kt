package com.naeilo.app.ui.game

import android.content.Context
import com.naeilo.app.core.tr
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.naeilo.app.AppModel
import com.naeilo.app.core.cformat
import com.naeilo.app.core.game.BrickGame
import com.naeilo.app.core.game.GameLevel
import com.naeilo.app.core.game.GameLines
import com.naeilo.app.core.game.GamePlayer
import com.naeilo.app.core.game.GameRecord
import com.naeilo.app.core.game.GameFx
import com.naeilo.app.core.game.GameMotion
import com.naeilo.app.core.game.GameSound
import com.naeilo.app.core.game.GameStage
import com.naeilo.app.core.game.Gravity
import com.naeilo.app.core.game.Impact
import com.naeilo.app.core.game.Notice
import com.naeilo.app.core.game.Orientation
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MeSample
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.meUnlocked
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.CharDir
import com.naeilo.app.ui.CharScene
import com.naeilo.app.ui.EaseOut
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.FriendMotion
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.MeMotion
import com.naeilo.app.ui.MotionSprite
import com.naeilo.app.ui.NavBack
import com.naeilo.app.ui.Palette
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.Pose
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.SF
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.SheetBar
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.WaveFeedbackKeys
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.drawableId
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.me.MeAvatar
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.wave.GameHeader
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// 판 깨기 화면: 고르는 화면(난이도·조작·선수·단계 12곳)과 게임 화면. 엔진과 규칙은 core/game/BrickGame.kt (Data/BrickGame.swift)
// 그리기: 판은 Canvas 하나(drawWithCache). 매 프레임 바뀌는 시각(clock)은 그리는 곳에서만 읽어 다시 짜지 않고 다시 그리기만 한다.
// 위 숫자·말풍선은 프레임마다 작은 값 묶음(Hud)으로 떠 두고, 값이 바뀐 때만 다시 짠다.

/** 1000칸 카드의 버튼 */
// ios: Views/BrickGameView.swift:6,13
@Composable
fun BrickGameButton(
    m: AppModel,
    // 그 카드의 칸 (회복 루트: 바닥/회복, 목표 루트: 내 돈/시장)
    filled: Int, floor: Int, yesterday: Int,   // ios: Views/BrickGameView.swift:8
    title: String = "벽돌깨기",   // ios: Views/BrickGameView.swift:9
    icon: String = "gamecontroller",   // ios: Views/BrickGameView.swift:10
    plain: Boolean = false,   // ios: Views/BrickGameView.swift:11 흰 바탕 · 회색 테두리 (미션 탭 보조 버튼)
    tight: Boolean = false,   // .labelStyle(TightLabel()): 아이콘·글자 사이 3
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }   // ios: Views/BrickGameView.swift:12
    val fg = if (plain) Theme.ink else Color.White   // ios: Views/BrickGameView.swift:17
    // ios: Views/BrickGameView.swift:14,15,16,18,19,21
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
            .background(if (plain) Theme.card else Theme.teal, RoundedCornerShape(12.dp))
            .border(1.5.dp, if (plain) Theme.border else Color.Transparent, RoundedCornerShape(12.dp))
            .plainClickable { show = true },
        horizontalArrangement = Arrangement.spacedBy(if (tight) 3.dp else 6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (SF.containsKey(icon)) Sym(icon, 14f, fg)
        FitText(title, appFont(14f, FontWeight.Bold), 0.8f, maxLines = 1, color = fg)
    }
    GameCover(show, { show = false }) { BrickGameHub(m, filled = filled, floor = floor, yesterday = yesterday) }   // ios: Views/BrickGameView.swift:22
}

/** .fullScreenCover: 아래에서 올라오는 전체 화면 (닫기는 LocalNavBack.pop = dismiss) */
@Composable
private fun GameCover(show: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (!show) return
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val y = remember { Animatable(1f) }
        LaunchedEffect(Unit) { y.animateTo(0f, tween(350, easing = EaseOut)) }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = y.value * size.height }.background(Theme.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            CompositionLocalProvider(LocalNavBack provides NavBack(null, onDismiss)) { content() }
        }
    }
}

// ios: Views/BrickGameView.swift:26,27,28,41,42,43
@Composable
fun BrickGameHub(m: AppModel, filled: Int, floor: Int, yesterday: Int) {
    val dismissTo = LocalNavBack.current   // ios: Views/BrickGameView.swift:29
    val dismiss = { dismissTo?.pop?.invoke(); Unit }
    // 기록은 이 폰 안에만 (GameRecord.load 는 설정 한 줄을 읽는다, 처음 한 번)
    var rec by remember { mutableStateOf(GameRecord.load(), neverEqualPolicy()) }   // ios: Views/BrickGameView.swift:30
    var game by remember { mutableStateOf<BrickGame?>(null) }   // ios: Views/BrickGameView.swift:31
    var board by remember { mutableStateOf<GameStage?>(null) }   // ios: Views/BrickGameView.swift:32
    var allStages by remember { mutableStateOf(false) }   // ios: Views/BrickGameView.swift:33
    var showItems by remember { mutableStateOf(false) }   // ios: Views/BrickGameView.swift:34

    // ios: Views/BrickGameView.swift:36,37
    val players = Shelter.friends.withIndex().filter { m.friendOn(it.index) }.map { it.value.id } + (if (m.meUnlocked) listOf("me") else emptyList())
    val ctx = LocalContext.current; val view = LocalView.current
    val fx = remember { BrickFx(ctx, view) }   // 소리·진동
    val tilt = remember { BrickTilt(ctx, view) }   // 기울이기 (판마다 엔진이 켜고 끈다)
    val items = remember { Shelter.items.map { it.id } }   // ios: Views/BrickGameView.swift:39 게임에선 처음부터 물건 모두 (쉼터 방은 하루 하나씩 돌아온 만큼)

    /** 내 캐릭터는 평정 지수 유형의 스킬 (아직 없으면 세리) */
    fun skillOf(p: String): String = if (p == "me") (m.calm.type ?: m.me?.type ?: "seri") else p   // ios: Views/BrickGameView.swift:122

    /** carry: 방금 깬 판 (이어하기 — 남은 생명 그대로, 점수 배율 +10%) */
    // ios: Views/BrickGameView.swift:127
    fun make(s: GameStage, lv: GameLevel, carry: BrickGame?): BrickGame {
        rec = rec.copy(level = lv)   // ios: Views/BrickGameView.swift:128
        // ios: Views/BrickGameView.swift:129,130,131
        val g = BrickGame(s, lv, rec.player, skillOf(rec.player), items, rec.tilt,
            rec.lossCount(s.n, lv) >= 3, filled, min(filled, floor), yesterday,
            carry?.let { it.chain + 1 } ?: 0, carry?.let { max(1, it.lives) }, fx = fx, motion = tilt)
        g.friends = players   // ios: Views/BrickGameView.swift:132
        return g   // ios: Views/BrickGameView.swift:133
    }

    fun start(s: GameStage, lv: GameLevel) { game = make(s, lv, carry = null) }   // ios: Views/BrickGameView.swift:124

    // ios: Views/BrickGameView.swift:136,137
    @Composable fun inlineRow(title: String, c: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, Modifier.width(52.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub); Box(Modifier.weight(1f)) { c() }
    }

    // ios: Views/BrickGameView.swift:140,141
    @Composable fun section(title: String, c: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(title, style = appFont(13f, FontWeight.Bold), color = Theme.sub); c()
    }

    // ios: Views/BrickGameView.swift:144
    @Composable fun playerCard(id: String) {
        val on = rec.player == id   // ios: Views/BrickGameView.swift:145
        // ios: Views/BrickGameView.swift:146
        val name = if (id == "me") (m.me?.name?.takeIf { it.isNotEmpty() } ?: "나") else (Shelter.friends.firstOrNull { it.id == id }?.name ?: id)
        // ios: Views/BrickGameView.swift:147,148,152,153,154,156,157
        Column(
            Modifier.size(72.dp, 84.dp)
                .background(if (on) Theme.tealBg else Theme.card, RoundedCornerShape(12.dp))
                .border(2.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(12.dp))
                .plainClickable { rec = rec.copy(player = id) }
                .semantics { selected = on },
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CharPixel(id, 36.dp, 51.dp, Modifier.charIdle(id.length), model = m)   // ios: Views/BrickGameView.swift:149
            T(name, style = appFont(12f, FontWeight.Bold), maxLines = 1)   // ios: Views/BrickGameView.swift:150
        }
    }

    // ios: Views/BrickGameView.swift:175
    @Composable fun stageCard(s: GameStage, open: Boolean, done: Boolean, modifier: Modifier) {
        val ctx = LocalContext.current
        // ios: Views/BrickGameView.swift:176,177,193,194,195,197,198
        Row(
            modifier.then(if (open) Modifier else Modifier.alpha(0.5f))   // .disabled(!open): 닫힌 단계는 카드 전체가 흐리다
                .background(Theme.card, RoundedCornerShape(14.dp)).border(1.5.dp, Theme.border, RoundedCornerShape(14.dp))
                .then(if (open) Modifier.plainClickable { start(s, rec.level) } else Modifier)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val bgId = drawableId(ctx, s.bg.id)
            // ios: Views/BrickGameView.swift:178,179,180
            if (bgId != 0) Image(ImageBitmap.imageResource(bgId), null, Modifier.size(80.dp, 50.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.None, alpha = if (open) 1f else 0.5f,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (open) 1f else 0f) }))
            else Box(Modifier.size(80.dp, 50.dp))
            Column(Modifier.weight(1f).widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {   // ios: Views/BrickGameView.swift:181,190
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:182
                    T(s.bg.volume, style = appFont(12f, FontWeight.Bold), color = Theme.tealDark)   // ios: Views/BrickGameView.swift:183
                    T(s.bg.name, Modifier.weight(1f, fill = false), style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/BrickGameView.swift:184
                    if (done) Sym("checkmark.circle.fill", 13f, Theme.teal)   // ios: Views/BrickGameView.swift:185
                }
                // ios: Views/BrickGameView.swift:187
                T(if (open) s.rule + " 벽돌 ${s.bricks(rec.level)}개" + (if (s.tetrisCount(rec.level) == 0) "." else ", 숨은 테트리스 블럭 ${s.tetrisCount(rec.level)}개.") else "앞 단계를 깨면 열려요",
                    style = appFont(12f), color = Theme.sub)
                rec.bestOf(s.n, rec.level)?.let { b -> val best = cformat("%,d", b); T("최고 ${best}점", style = appFont(12f, FontWeight.Bold), color = Theme.sub) }   // ios: Views/BrickGameView.swift:188
            }
            Glyph(if (open) "play.fill" else "lock.fill", 17f, if (open) Theme.teal else Theme.sub)   // ios: Views/BrickGameView.swift:191
        }
    }

    // ios: Views/BrickGameView.swift:160
    @Composable fun stageRow(s: GameStage) {
        val open = rec.open(s.n, rec.level)   // ios: Views/BrickGameView.swift:161
        val done = rec.cleared[rec.level.name].orEmpty().contains(s.n)   // ios: Views/BrickGameView.swift:162
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:163
            stageCard(s, open = open, done = done, modifier = Modifier.weight(1f))   // ios: Views/BrickGameView.swift:164
            if (open) {   // ios: Views/BrickGameView.swift:165
                // ios: Views/BrickGameView.swift:166,168,170
                Box(Modifier.size(44.dp, 44.dp).background(Theme.tealBg, RoundedCornerShape(12.dp)).plainClickable { board = s }
                    .semantics(mergeDescendants = true) { contentDescription = tr("${s.bg.volume} 내 기록 순위") }, contentAlignment = Alignment.Center) {
                    Glyph("trophy", 16f, Theme.tealDark)   // ios: Views/BrickGameView.swift:167
                }
            }
        }
    }

    // ios: Views/BrickGameView.swift:103
    Box(Modifier.fillMaxSize().background(Theme.bg)) {
        // ios: Views/BrickGameView.swift:101
        Screen(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {   // ios: Views/BrickGameView.swift:44
                GameHeader(title = "내 자산으로 하는 벽돌깨기", dismiss = dismiss)   // ios: Views/BrickGameView.swift:45,104 맨 위는 파도 타기와 같은 GameHeader
                // 이름 옆에 버튼을 한 줄로
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/BrickGameView.swift:47
                    inlineRow("난이도") { ChipRow(items = GameLevel.entries.map { it to it.title }, selection = rec.level, onSelect = { rec = rec.copy(level = it) }, fill = true) }   // ios: Views/BrickGameView.swift:48
                    T(rec.level.note, style = appFont(12f), color = Theme.sub)   // ios: Views/BrickGameView.swift:49
                }
                inlineRow("조작") { ChipRow(items = listOf(true to "기울이기", false to "끌기"), selection = rec.tilt, onSelect = { rec = rec.copy(tilt = it) }, fill = true) }   // ios: Views/BrickGameView.swift:51
                section("선수") {   // ios: Views/BrickGameView.swift:52
                    // ios: Views/BrickGameView.swift:53,54
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { players.forEach { key(it) { playerCard(it) } } }
                    T(GamePlayer.skill(skillOf(rec.player)), style = appFont(13f, FontWeight.SemiBold), color = Theme.tealDark)   // ios: Views/BrickGameView.swift:56
                }
                Row(verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:58
                    T("단계 · 『중첩된 현실』 쉼터", style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BrickGameView.swift:59
                    Spacer(Modifier.weight(1f))   // ios: Views/BrickGameView.swift:60
                    val total = cformat("%,d", rec.total(rec.level))
                    T("${rec.level.title} 기록 합계 ${total}점", style = appFont(12f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BrickGameView.swift:61
                }
                // 처음엔 세 단계만, 나머지는 접어 둔다
                GameStage.all.take(3).forEach { key(it.n) { stageRow(it) } }   // ios: Views/BrickGameView.swift:64
                // ios: Views/BrickGameView.swift:65,66,67,68,70
                AnimatedVisibility(allStages, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { GameStage.all.drop(3).forEach { key(it.n) { stageRow(it) } } }
                }
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(Theme.tealBg, RoundedCornerShape(12.dp)).plainClickable { allStages = !allStages }, contentAlignment = Alignment.Center) {
                    T(if (allStages) "접기 ▴" else "나머지 ${GameStage.all.size - 3}단계 펼치기 ▾", style = appFont(14f, FontWeight.SemiBold), color = Theme.tealDark)
                }
                // 물건 아이템은 접어 둔다
                // ios: Views/BrickGameView.swift:72,73,78,80
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).animateContentSize().plainClickable { showItems = !showItems }, verticalAlignment = Alignment.CenterVertically) {
                    T("물건 아이템 ${items.size}개", style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BrickGameView.swift:74
                    Spacer(Modifier.weight(1f))   // ios: Views/BrickGameView.swift:75
                    T(if (showItems) "접기 ▴" else "펼치기 ▾", style = appFont(13f, FontWeight.SemiBold), color = Theme.tealDark)   // ios: Views/BrickGameView.swift:76
                }
                AnimatedVisibility(showItems, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {   // ios: Views/BrickGameView.swift:81
                    if (items.isEmpty()) {   // ios: Views/BrickGameView.swift:82
                        T("쉼터에 물건이 돌아오면 벽돌에서 아이템으로 떨어져요.", style = appFont(13f), color = Theme.sub)   // ios: Views/BrickGameView.swift:83
                    } else {   // ios: Views/BrickGameView.swift:84
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/BrickGameView.swift:85
                            items.forEach { id ->   // ios: Views/BrickGameView.swift:86
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:87
                                    PixelN("art_" + id, 24.dp, 24.dp)   // ios: Views/BrickGameView.swift:88
                                    T(Shelter.items.firstOrNull { it.id == id }?.name ?: id, style = appFont(13f, FontWeight.Bold))   // ios: Views/BrickGameView.swift:89
                                    T(GamePlayer.item(id), Modifier.weight(1f), style = appFont(12f), color = Theme.sub)   // ios: Views/BrickGameView.swift:90
                                }
                            }
                        }
                    }
                }
                section("내 기록") {   // ios: Views/BrickGameView.swift:96
                    T("기록과 순위는 이 폰 안에만 남아요. 아무 데도 보내지 않아요.", style = appFont(12f), color = Theme.sub)   // ios: Views/BrickGameView.swift:97,98
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (!players.contains(rec.player)) rec = rec.copy(player = players.firstOrNull() ?: "seri") }   // ios: Views/BrickGameView.swift:106
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(rec) { if (first) first = false else rec.save() }   // ios: Views/BrickGameView.swift:107
    board?.let { st -> Sheet(true, onDismiss = { board = null }) { BrickBoardSheet(st, rec.level, rec.top(st.n, rec.level)) { board = null } } }   // ios: Views/BrickGameView.swift:108
    // ios: Views/BrickGameView.swift:109
    GameCover(game != null, { game?.end(); game = null }) {
        game?.let { g ->   // ios: Views/BrickGameView.swift:110
            key(g.id) {   // ios: Views/BrickGameView.swift:116
                BrickPlayView(m, g, tilt, record = rec, setRecord = { rec = it }) { next ->   // ios: Views/BrickGameView.swift:111
                    g.end()   // ios: Views/BrickGameView.swift:112
                    // 다음 판은 화면을 닫지 않고 그 자리에서 바꾼다. 이어하기면 남은 생명·점수 배율을 넘긴다
                    if (next != null) game = make(next.first, next.second, carry = if (next.third) g else null) else game = null   // ios: Views/BrickGameView.swift:114
                }
            }
        }
    }
}

/** SF 기호가 SF 표에 있으면 그것, 없으면 같은 모양을 그린다 (play.fill·pause.fill·trophy 는 표에 아직 없음) */
@Composable
private fun Glyph(sf: String, size: Float, color: Color) {
    if (SF.containsKey(sf)) { Sym(sf, size, color); return }
    if (sf == "trophy" && SF.containsKey("trophy.fill")) { Sym("trophy.fill", size, color); return }
    val d = (size * 1.25f).dp
    androidx.compose.foundation.Canvas(Modifier.size(d)) {
        val w = this.size.width; val h = this.size.height
        when (sf) {
            "play.fill" -> drawPath(Path().apply { moveTo(w * 0.25f, h * 0.15f); lineTo(w * 0.85f, h * 0.5f); lineTo(w * 0.25f, h * 0.85f); close() }, color)
            "pause.fill" -> { drawRect(color, Offset(w * 0.22f, h * 0.15f), Size(w * 0.2f, h * 0.7f)); drawRect(color, Offset(w * 0.58f, h * 0.15f), Size(w * 0.2f, h * 0.7f)) }
            else -> drawCircle(color, w * 0.3f)
        }
    }
}

/** 화면 위쪽 숫자·말풍선이 읽는 엔진 값 (프레임마다 떠 두고, 바뀐 때만 다시 짠다) */
private data class Hud(
    val phase: BrickGame.Phase, val score: Int, val lives: Int, val broken: Int, val target: Int, val net: Int, val shield: Boolean,
    val effects: List<String>, val picked: Map<String, Int>, val speaking: Boolean, val speech: String, val mood: GameLines.Event,
    val guestShown: Boolean, val guest: String, val guestLine: String, val toast: String?, val blink: Boolean, val newBest: Boolean,
) {
    companion object {
        fun of(g: BrickGame, tt: Double): Hud = Hud(
            g.phase, g.score, g.lives, g.broken, g.target, g.net, g.shield, g.activeEffects.toList(), g.picked.toMap(),
            g.speaking, g.speech, g.mood, g.guestShown, g.guest, g.guestLine, g.toast?.first,
            // ios: Views/BrickGameView.swift:281 3.1초와 4.3초 주기를 섞어 일정하지 않게 깜빡인다
            tt.mod(3.1) < 0.14 || tt.mod(4.3) < 0.12, g.newBest,
        )
    }
}

/** 테트리스 조각 색 (I O T S Z J L) */
// ios: Views/BrickGameView.swift:218,219
private val tetrisColors: List<Color> = listOf(hex(0x00C8E8), hex(0xF2D000), hex(0xA03CE0),
    hex(0x2EC84A), hex(0xE8343C), hex(0x2F5BE8), hex(0xF08C1E))

/** 판 위 그림 한 장: 틀(w×h)의 아래에 붙여 그림(높이 imgH)을 그린다. 머리 위 여유 줄은 틀 위로 넘친다 */
private class Sprite(val img: ImageBitmap, val w: Float, val h: Float, val imgH: Float = h)

// ios: Views/BrickGameView.swift:202,221
@Composable
fun BrickPlayView(
    m: AppModel,   // ios: Views/BrickGameView.swift:210
    game: BrickGame,   // ios: Views/BrickGameView.swift:203
    tilt: BrickTilt,
    record: GameRecord, setRecord: (GameRecord) -> Unit,   // ios: Views/BrickGameView.swift:204
    // 닫을 때 (다음에 시작할 단계·난이도·이어하기, 없으면 고르는 화면으로)
    onClose: (Triple<GameStage, GameLevel, Boolean>?) -> Unit,   // ios: Views/BrickGameView.swift:206
) {
    val scenePhase = m.active   // ios: Views/BrickGameView.swift:207 (앱이 앞에 있는지)
    var saved by remember { mutableStateOf(false) }   // ios: Views/BrickGameView.swift:208
    val pace = remember { PaddleStride() }   // ios: Views/BrickGameView.swift:209
    val view = LocalView.current
    val px = view.resources.displayMetrics.density.toDouble()   // ios: Views/BrickGameView.swift:211
    val pal = LocalPal.current
    val brickDark = Theme.teal   // ios: Views/BrickGameView.swift:213
    val brickLight = hexD(0x7FD3C9, 0x3C8F86)   // ios: Views/BrickGameView.swift:214
    val clay = hexD(0xC9A27E, 0x8A6A4E)   // ios: Views/BrickGameView.swift:215
    val stackColor = hexD(0xB9C3E6, 0x4E5C8C)   // ios: Views/BrickGameView.swift:216 단계가 더 쌓은 칸
    val colors = Brick(pal, brickDark, brickLight, clay, stackColor)

    val g = game   // ios: Views/BrickGameView.swift:222
    val ctx = LocalContext.current
    val active = LocalTabActive.current
    val reduce = LocalReduceMotion.current
    val record1 by rememberUpdatedState(record)
    val close by rememberUpdatedState(onClose)
    // 그리기 시각(초): 그리는 곳만 읽는다. hud 는 값이 바뀔 때만 다시 짠다
    val clock = remember { mutableDoubleStateOf(SystemClock.uptimeMillis() / 1000.0) }
    var hud by remember { mutableStateOf(Hud.of(g, clock.doubleValue)) }
    fun act(f: () -> Unit) { f(); hud = Hud.of(g, clock.doubleValue) }   // 버튼이 바꾼 phase 를 바로 보인다 (멈춘 동안은 프레임이 없다)
    val live = hud.phase != BrickGame.Phase.paused   // ios: Views/BrickGameView.swift:223 끝난 뒤에도 인물은 계속 움직인다 (엔진은 멈춤)
    val sim = remember { doubleArrayOf(0.0, 0.0) }   // 엔진 시각(초)·남은 시간: 1/60초씩 고정 걸음
    // 매 프레임 엔진을 한 걸음 움직이고 다시 그린다 (멈춤·끝나면 쉼)
    // ios: Views/BrickGameView.swift:225,226
    LaunchedEffect(g, live, active) {
        if (!live || !active) return@LaunchedEffect
        var last = -1L
        animateEachFrame { n ->
            if (last < 0) { if (sim[0] == 0.0) g.tick(sim[0]) } else {
                sim[1] = min(0.25, sim[1] + (n - last) / 1e9)
                while (sim[1] >= STEP) { sim[0] += STEP; g.tick(sim[0]); sim[1] -= STEP }
            }
            last = n
            clock.doubleValue = n / 1e9
            hud = Hud.of(g, clock.doubleValue)
        }
    }
    // 기울이기는 판이 움직이는 때(준비·진행)·앱이 앞에 있고 탭이 보일 때만 센서를 단다
    val tiltOn = scenePhase && active && (hud.phase == BrickGame.Phase.ready || hud.phase == BrickGame.Phase.playing)
    DisposableEffect(tilt, tiltOn) { tilt.visible(tiltOn); onDispose { tilt.visible(false) } }
    DisposableEffect(g) { onDispose { g.dispose() } }   // deinit: 기울기 끄기
    LaunchedEffect(g) { g.begin() }   // ios: Views/BrickGameView.swift:232
    LaunchedEffect(scenePhase) { if (!scenePhase) act { g.pause() } }   // ios: Views/BrickGameView.swift:233
    // ios: Views/BrickGameView.swift:234
    LaunchedEffect(hud.phase) {
        val p = hud.phase
        if (!((p == BrickGame.Phase.cleared || p == BrickGame.Phase.over) && !saved)) return@LaunchedEffect   // ios: Views/BrickGameView.swift:235
        saved = true   // ios: Views/BrickGameView.swift:236
        // ios: Views/BrickGameView.swift:237
        val r = record1.copy(); g.newBest = r.finish(g.stage.n, g.level, g.score, p == BrickGame.Phase.cleared, g.player); setRecord(r); r.save()
        hud = Hud.of(g, clock.doubleValue)
        // 깨면 바로 다음 단계로 이어진다 (잠깐 보여 주고)
        val next = GameStage.all.firstOrNull { it.n == g.stage.n + 1 }   // ios: Views/BrickGameView.swift:239
        if (p == BrickGame.Phase.cleared && next != null) {
            delay(1400); close(Triple(next, g.level, true))   // ios: Views/BrickGameView.swift:240
        }
    }
    // 미니 게임 중엔 시세·위젯 갱신을 미룬다
    RefreshPause(true)   // ios: Views/BrickGameView.swift:229
    // ios: Views/BrickGameView.swift:243
    DisposableEffect(view) {
        val w = (view.parent as? DialogWindowProvider)?.window
        val c = w?.let { WindowCompat.getInsetsController(it, view) }
        c?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { c?.show(WindowInsetsCompat.Type.statusBars()) }
    }

    val tm = rememberTextMeasurer()
    // 판 위에 그릴 그림: 물건 (art_<id>), 그림 장면이 없는 선수의 도트 그림
    val arts = g.items.mapNotNull { id -> drawableId(ctx, "art_$id").takeIf { it != 0 }?.let { "art_$id" to ImageBitmap.imageResource(it) } }.toMap()
    val sprId = if (g.player == "me") 0 else drawableId(ctx, "spr_${g.player}")
    val sprBmp = if (sprId != 0) ImageBitmap.imageResource(sprId) else null
    val me = if (g.player == "me") m.me else null
    val meBmp = remember(me) { me?.let { MeArt.image(it)?.asImageBitmap() } }
    val bgId = drawableId(ctx, g.stage.bg.id)
    val bgBmp = if (bgId != 0) ImageBitmap.imageResource(bgId) else null

    // ios: Views/BrickGameView.swift:230,231
    Box(Modifier.fillMaxSize().pointerInput(g) { detectTapGestures { g.launch() } }) {
        // ios: Views/BrickGameView.swift:227,246,247,272,273
        Column(Modifier.fillMaxSize().background(Theme.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HudBar(g, hud) { act(it) }   // ios: Views/BrickGameView.swift:248
            Host(g, hud, clock, m, reduce)   // ios: Views/BrickGameView.swift:249
            // ios: Views/BrickGameView.swift:250,251,252,259,266
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val s = min(maxWidth.value / BrickGame.W.toFloat(), maxHeight.value / BrickGame.H.toFloat())
                val fw = BrickGame.W.toFloat() * s; val fh = BrickGame.H.toFloat() * s
                Box(
                    Modifier.offset(x = (maxWidth - fw.dp) / 2, y = (maxHeight - fh.dp) / 2).size(fw.dp, fh.dp)
                        .clip(RoundedCornerShape(6.dp))   // ios: Views/BrickGameView.swift:260
                        // ios: Views/BrickGameView.swift:262,263,264,265
                        .pointerInput(g) {
                            val minimumDistance = 0f   // 누른 자리부터 바로 따라간다
                            awaitEachGesture {
                                val down = awaitFirstDown(); val k = size.width / BrickGame.W
                                if (minimumDistance <= 0f) g.drag(down.position.x / k); down.consume()
                                while (true) {
                                    val e = awaitPointerEvent(); val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) { c.consume(); break }
                                    g.drag(c.position.x / k); c.consume()
                                }
                                g.launch()
                            }
                        }
                        .semantics { contentDescription = tr("판 깨기 게임. 깬 칸 ${hud.broken}, 기준 ${hud.target}") }   // ios: Views/BrickGameView.swift:267
                        // ios: Views/BrickGameView.swift:253,254,255,256,257
                        .drawWithCache {
                            val bgSrc = bgBmp?.let { cropSrc(IntSize(it.width, it.height), size) }
                            onDrawBehind {
                                drawRect(pal.card)
                                if (bgBmp != null && bgSrc != null) drawImage(bgBmp, bgSrc.first, bgSrc.second, IntOffset0, IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = 0.3f, filterQuality = FilterQuality.None)
                                field(g, clock.doubleValue, px, colors, pace, tm, arts, sprBmp, meBmp, me)
                            }
                        }
                        .border(2.dp, Theme.ink, RoundedCornerShape(6.dp)),   // ios: Views/BrickGameView.swift:261
                )
            }
            // ios: Views/BrickGameView.swift:269,270
            T(if (g.tilt) "기울여 이동 · 눌러서 발사" else "끌어서 이동 · 떼면 발사", style = appFont(12f), color = Theme.sub)
        }
        // ios: Views/BrickGameView.swift:274
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { Overlay(g, hud, record, close) { act(it) } }
    }
}

private const val STEP = 1.0 / 60
/** TimelineView(.animation): 화면이 그릴 때마다 (취소되면 끝) */
private suspend inline fun animateEachFrame(crossinline f: (Long) -> Unit) { while (true) androidx.compose.runtime.withFrameNanos { f(it) } }
private val IntOffset0 = androidx.compose.ui.unit.IntOffset(0, 0)

/** scaledToFill 의 원본 자르기 (가운데 기준) */
private fun cropSrc(img: IntSize, dst: Size): Pair<androidx.compose.ui.unit.IntOffset, IntSize> {
    val k = max(dst.width / img.width, dst.height / img.height)
    val w = (dst.width / k).roundToInt().coerceIn(1, img.width); val h = (dst.height / k).roundToInt().coerceIn(1, img.height)
    return androidx.compose.ui.unit.IntOffset((img.width - w) / 2, (img.height - h) / 2) to IntSize(w, h)
}

/** 벽돌 색 묶음 (그리기 안에서는 Theme 를 못 읽어 미리 받아 둔다) */
private class Brick(val pal: Palette, val dark: Color, val light: Color, val clay: Color, val stack: Color)

/** 위쪽 인물: 가끔 눈을 깜빡이고, 시작·물건·놓침·끝에 말풍선. 말이 없을 때는 방 설명 */
// ios: Views/BrickGameView.swift:278,279
@Composable
private fun Host(g: BrickGame, hud: Hud, clock: State<Double>, m: AppModel, reduce: Boolean) {
    // 말풍선은 한 줄씩 넓게: 위 줄은 선수, 아래 줄은 끼어든 인물
    // 끼어든 인물 줄 자리를 늘 비워 둬서, 말풍선이 떠도 아래 게임판이 움직이지 않게 한다
    // ios: Views/BrickGameView.swift:283,323
    Column(Modifier.fillMaxWidth().height((52 + 6 + 56).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/BrickGameView.swift:284
            // ios: Views/BrickGameView.swift:285,290,291
            Box(Modifier.size(44.dp, 52.dp).alive(clock, 0.0, reduce) { g.speaking && g.t - g.moodAt < 0.5 }, contentAlignment = Alignment.Center) {
                if (g.player == "me") CharPixel("me", 34.dp, 48.dp, model = m)   // ios: Views/BrickGameView.swift:286
                else if (hud.speaking) MotionSprite(friend = g.player, pose = pose(hud.mood), height = 52.dp)   // ios: Views/BrickGameView.swift:287
                else PixelN("spr_${g.player}" + (if (hud.blink) "_blink" else ""), 34.dp, 48.dp)   // ios: Views/BrickGameView.swift:288
            }
            // ios: Views/BrickGameView.swift:292,322
            Crossfade(hud.speaking && hud.speech.isNotEmpty(), Modifier.weight(1f), animationSpec = tween(150, easing = EaseOut), label = "speak") { talking ->
                if (talking) {
                    // ios: Views/BrickGameView.swift:293,294,295,296,297
                    Box(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(12.dp)).border(1.5.dp, Theme.ink, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                        FitText(hud.speech, appFont(21f, FontWeight.Bold), 0.6f, maxLines = 1)
                    }
                } else {   // ios: Views/BrickGameView.swift:298
                    // ios: Views/BrickGameView.swift:299,300
                    T(g.stage.scene, Modifier.fillMaxWidth(), style = appFont(14f), color = Theme.sub, maxLines = 1)
                }
            }
        }
        // 끼어든 인물 (오른쪽)
        // ios: Views/BrickGameView.swift:304,319,325
        AnimatedVisibility(hud.guestShown, enter = slideInHorizontally(tween(250, easing = EaseOut)) { it } + fadeIn(tween(250, easing = EaseOut)),
            exit = slideOutHorizontally(tween(250, easing = EaseOut)) { it } + fadeOut(tween(250, easing = EaseOut))) {
            Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:305,318
                // ios: Views/BrickGameView.swift:306,307,308,309,310
                Box(Modifier.weight(1f).background(Theme.tealBg, RoundedCornerShape(12.dp)).border(1.5.dp, Theme.teal, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    FitText(hud.guestLine, appFont(21f, FontWeight.Bold), 0.6f, maxLines = 1)
                }
                Column(Modifier.width(44.dp), verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/BrickGameView.swift:311,316
                    // ios: Views/BrickGameView.swift:312,313
                    Box(Modifier.alive(clock, 1.7, reduce) { g.t - (g.guestUntil - 3.6) < 0.5 }.graphicsLayer { scaleX = -1f; scaleY = 1f }) { GuestFigure(hud.guest) }
                    T(guestName(hud.guest), style = appFont(9f, FontWeight.Bold), color = Theme.sub, maxLines = 1)   // ios: Views/BrickGameView.swift:314
                }
            }
        }
    }
}

/** 상황 → 동작 그림 (Guide 의 mo_<인물>_<동작>) */
// ios: Views/BrickGameView.swift:327,328
private fun pose(e: GameLines.Event): Pose = when (e) {
    GameLines.Event.start -> Pose.wave   // ios: Views/BrickGameView.swift:329
    GameLines.Event.item -> Pose.spread   // ios: Views/BrickGameView.swift:330
    GameLines.Event.lose, GameLines.Event.last -> Pose.think   // ios: Views/BrickGameView.swift:331
    GameLines.Event.half -> Pose.point   // ios: Views/BrickGameView.swift:332
    GameLines.Event.clear -> Pose.cheer   // ios: Views/BrickGameView.swift:333
    GameLines.Event.over -> Pose.side   // ios: Views/BrickGameView.swift:334
    GameLines.Event.rewind -> Pose.back   // ios: Views/BrickGameView.swift:335
}

/** 끼어든 인물 그림: 기본 인물은 도트 그림, 보조 인물은 마네킹 샘플 조합 */
// ios: Views/BrickGameView.swift:340
@Composable
private fun GuestFigure(id: String) {
    val sm = if (id.startsWith("s:")) MeSample.all.firstOrNull { it.name == id.drop(2) } else null   // ios: Views/BrickGameView.swift:341
    if (sm != null) {
        MeAvatar(me = MeChar(name = sm.name, parts = sm.parts), width = 30.dp, height = 42.dp, modifier = Modifier.charIdle(sm.name.length))   // ios: Views/BrickGameView.swift:342
    } else {   // ios: Views/BrickGameView.swift:343
        PixelN("spr_$id", 30.dp, 42.dp, Modifier.charIdle(id.length))   // ios: Views/BrickGameView.swift:344
    }
}
// ios: Views/BrickGameView.swift:347,348
private fun guestName(id: String): String =
    if (id.startsWith("s:")) id.drop(2) else (Shelter.friends.firstOrNull { it.id == id }?.name ?: "")

private val Tnum = "tnum"

// ios: Views/BrickGameView.swift:351,352
@Composable
private fun HudBar(g: BrickGame, hud: Hud, act: (() -> Unit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:353
            // ios: Views/BrickGameView.swift:354,355,356,358
            Box(Modifier.size(40.dp, 40.dp).background(Theme.card, CircleShape).border(1.5.dp, Theme.border, CircleShape).plainClickable { act { g.pause() } }
                .semantics(mergeDescendants = true) { contentDescription = tr("멈춤") }, contentAlignment = Alignment.Center) {
                Glyph("pause.fill", 16f, Theme.ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {   // ios: Views/BrickGameView.swift:359
                T("${g.stage.bg.volume} · ${g.level.title}", style = appFont(11f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BrickGameView.swift:360
                T(g.stage.bg.name, style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/BrickGameView.swift:361
            }
            Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/BrickGameView.swift:363
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.End) {   // ios: Views/BrickGameView.swift:364
                T("${cformat("%,d", hud.score)}점", style = appFont(18f, FontWeight.Bold).copy(fontFeatureSettings = Tnum))   // ios: Views/BrickGameView.swift:365
                T("남은 공 ${max(0, hud.lives - 1)}", style = appFont(12f, FontWeight.Bold), color = Theme.up)   // ios: Views/BrickGameView.swift:366
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:369
            ProgressBar(value = min(1.0, hud.broken.toDouble() / hud.target.toDouble()), height = 8.dp, fill = Theme.teal, modifier = Modifier.weight(1f))   // ios: Views/BrickGameView.swift:370
            T("${hud.broken}/${hud.target}칸", style = appFont(11f, FontWeight.Bold).copy(fontFeatureSettings = Tnum), color = Theme.sub)   // ios: Views/BrickGameView.swift:371
        }
        // ios: Views/BrickGameView.swift:373,381
        Row(Modifier.height(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (g.chain > 0) Tag("이어하기 · 점수 ×" + cformat("%.1f", g.scoreMul))   // ios: Views/BrickGameView.swift:374
            if (hud.net > 0) Tag("안전망 ${hud.net}", yellow = true)   // ios: Views/BrickGameView.swift:375
            if (hud.shield) Tag("보호막")   // ios: Views/BrickGameView.swift:376
            if (g.assist) Tag("도움 켜짐 · 공 느리게")   // ios: Views/BrickGameView.swift:377
            hud.effects.forEach { e -> effectName(e)?.let { n -> Tag(n) } }   // ios: Views/BrickGameView.swift:378
            Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/BrickGameView.swift:379
        }
        PickedRow(g, hud)   // ios: Views/BrickGameView.swift:382
    }
}

/** 이번 판에 먹은 물건 */
// ios: Views/BrickGameView.swift:387
@Composable
private fun PickedRow(g: BrickGame, hud: Hud) {
    val got = g.items.filter { (hud.picked[it] ?: 0) > 0 }   // ios: Views/BrickGameView.swift:388
    // ios: Views/BrickGameView.swift:389,398,400,401
    Row(Modifier.height(20.dp).fillMaxWidth().graphicsLayer { alpha = if (g.items.isEmpty()) 0f else 1f }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        T("먹은 물건", style = appFont(11f, FontWeight.Bold), color = Theme.sub)   // ios: Views/BrickGameView.swift:390
        if (got.isEmpty()) T("아직 없음", style = appFont(11f), color = Theme.sub)   // ios: Views/BrickGameView.swift:391
        got.forEach { id ->   // ios: Views/BrickGameView.swift:392
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/BrickGameView.swift:393
                PixelN("art_" + id, 18.dp, 18.dp)   // ios: Views/BrickGameView.swift:394
                T("×${hud.picked[id] ?: 0}", style = appFont(11f, FontWeight.Bold).copy(fontFeatureSettings = Tnum))   // ios: Views/BrickGameView.swift:395
            }
        }
    }
}

// ios: Views/BrickGameView.swift:404,405,406
@Composable
private fun Tag(s: String, yellow: Boolean = false) {
    Box(Modifier.height(20.dp).background(if (yellow) Theme.yellow else Theme.tealBg, CapsuleShape).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        T(s, style = appFont(11f, FontWeight.Bold), color = if (yellow) hex(0x5A4300) else Theme.tealDark, maxLines = 1)
    }
}

// ios: Views/BrickGameView.swift:409,410
private fun effectName(e: String): String? = when (e) {
    "slow" -> "보리차 · 느리게"   // ios: Views/BrickGameView.swift:411
    "light" -> "현관 등 · 길 보임"   // ios: Views/BrickGameView.swift:412
    "clock" -> "벽시계 · 멈춤"   // ios: Views/BrickGameView.swift:413
    "wide" -> "머리끈 · 긴 판"   // ios: Views/BrickGameView.swift:414
    "aim" -> "비대칭 그릇 · 꺾기"   // ios: Views/BrickGameView.swift:415
    "double" -> "대추씨 · 2배"   // ios: Views/BrickGameView.swift:416
    "pierce" -> "원로의 구슬 · 뚫기"   // ios: Views/BrickGameView.swift:417
    "fast" -> "오늘 칸 · 빠르게"   // ios: Views/BrickGameView.swift:418
    else -> null   // ios: Views/BrickGameView.swift:419
}

/** 판 그리기. now(clock) 를 여기서 읽어야 프레임마다 다시 그린다 (엔진 값은 지켜보지 않는다) */
// ios: Views/BrickGameView.swift:424,425,426
private fun DrawScope.field(
    g: BrickGame, now: Double, px: Double, c: Brick, pace: PaddleStride, tm: TextMeasurer,
    arts: Map<String, ImageBitmap>, sprBmp: ImageBitmap?, meBmp: ImageBitmap?, me: MeChar?,
) {
    val s = size.width / BrickGame.W   // 한 칸 크기 (화면 픽셀)
    val pal = c.pal
    fun r(x: Double, y: Double, w: Double, h: Double): Rect = Rect((x * s).toFloat(), (y * s).toFloat(), ((x + w) * s).toFloat(), ((y + h) * s).toFloat())   // ios: Views/BrickGameView.swift:427
    fun p(v: Double): Float = (v * px).toFloat()   // 포인트 → 화면 픽셀
    // ios: Views/BrickGameView.swift:541,542,543
    val symbols = HashMap<String, Sprite>()
    runner(g, s, now, pace, sprBmp, meBmp, me)?.let { symbols["player"] = it }
    g.items.forEach { id -> arts["art_" + id]?.let { symbols[id] = Sprite(it, (2.2 * s).toFloat(), (2.2 * s).toFloat()) } }

    // 벽돌
    if (g.dark) drawRect(Color.Black.copy(alpha = 0.25f), Offset(0f, 0f), Size((BrickGame.W * s).toFloat(), (BrickGame.H * s).toFloat()))   // ios: Views/BrickGameView.swift:429
    var op = if (g.dark) 0.4f else 1f   // ios: Views/BrickGameView.swift:430
    for (i in 0 until 1000) { if (g.hp[i].toInt() == 0) continue   // ios: Views/BrickGameView.swift:431
        val row = i / 40; val bx = (i % 40) + g.sway(row); val by = BrickGame.top + (24 - row)   // ios: Views/BrickGameView.swift:432 g.rect(i)
        val rc = r(bx + 0.05, by + 0.05, 0.9, 0.9)   // ios: Views/BrickGameView.swift:433
        if (g.hp[i].toInt() < 0) { drawRect(pal.ink, rc.topLeft, rc.size, alpha = op); continue }   // ios: Views/BrickGameView.swift:434
        val sp = g.special[i]; if (sp != null && g.revealed[i]) { special(sp, rc, now, pal, px, op); continue }   // ios: Views/BrickGameView.swift:435
        if (g.group[i] >= 0 && g.bigs[g.group[i]].revealed) {   // ios: Views/BrickGameView.swift:436 테트리스 색, 위·왼쪽은 밝게
            val cell = r(bx, by, 1.0, 1.0); val col = tetrisColors[g.bigs[g.group[i]].shape]   // ios: Views/BrickGameView.swift:437
            drawRect(col, cell.topLeft, cell.size, alpha = op)   // ios: Views/BrickGameView.swift:438
            drawRect(Color.White.copy(alpha = 0.45f), cell.topLeft, Size(cell.width, cell.height * 0.18f), alpha = op)   // ios: Views/BrickGameView.swift:439
            drawRect(Color.Black.copy(alpha = 0.25f), Offset(cell.left, cell.bottom - cell.height * 0.18f), Size(cell.width, cell.height * 0.18f), alpha = op)   // ios: Views/BrickGameView.swift:440
            continue   // ios: Views/BrickGameView.swift:441
        }
        val col = when (g.kind[i].toInt()) { 1 -> c.dark; 3 -> c.light; 4 -> pal.track; 5 -> c.stack; else -> c.light }   // ios: Views/BrickGameView.swift:443
        drawRect(col, rc.topLeft, rc.size, alpha = op)   // ios: Views/BrickGameView.swift:444
        if (g.kind[i].toInt() == 3) { val q = rc.inset(p(0.6), p(0.6)); drawRect(pal.up, q.topLeft, q.size, alpha = op, style = Stroke(p(1.2))) }   // ios: Views/BrickGameView.swift:445
        // ios: Views/BrickGameView.swift:446
        if (g.hp[i] >= 2) { val q = rc.inset(rc.width * 0.22f, rc.height * 0.22f); drawRoundRect(c.clay, q.topLeft, q.size, CornerRadius(p(1.5)), alpha = op) }
        if (g.hp[i] >= 3) { val q = rc.inset(p(0.8), p(0.8)); drawRect(c.clay, q.topLeft, q.size, alpha = op, style = Stroke(p(1.2))) }   // ios: Views/BrickGameView.swift:447
        if (g.hp[i].toInt() == 1 && g.revealed[i]) {   // ios: Views/BrickGameView.swift:448 한 번 맞은 벽돌: 금이 간다
            val path = Path()   // ios: Views/BrickGameView.swift:449
            path.moveTo(rc.left + rc.width * 0.2f, rc.top + rc.height * 0.15f)   // ios: Views/BrickGameView.swift:450
            path.lineTo(rc.center.x, rc.center.y)   // ios: Views/BrickGameView.swift:451
            path.lineTo(rc.right - rc.width * 0.25f, rc.bottom - rc.height * 0.1f)   // ios: Views/BrickGameView.swift:452
            drawPath(path, Color.White.copy(alpha = 0.85f), alpha = op, style = Stroke(max(p(1.0), rc.width * 0.12f)))   // ios: Views/BrickGameView.swift:453
        }
    }
    // 드러난 테트리스 블럭: 남은 횟수
    for (big in g.bigs) { if (!(big.revealed && big.hp > 0)) continue   // ios: Views/BrickGameView.swift:457
        val rs = big.cells.map { j -> val rw = j / 40; Offset(((j % 40) + g.sway(rw) + 0.5).toFloat(), (BrickGame.top + (24 - rw) + 0.5).toFloat()) }   // ios: Views/BrickGameView.swift:458
        val mid = Offset(rs.map { it.x }.fold(0f) { a, b -> a + b } / rs.size * s.toFloat(), rs.map { it.y }.fold(0f) { a, b -> a + b } / rs.size * s.toFloat())   // ios: Views/BrickGameView.swift:459
        // ios: Views/BrickGameView.swift:460
        val lay = tm.measure("${big.hp}", TextStyle(color = Color.White, fontSize = max(p(9.0), (s * 0.9).toFloat()).toSp(), fontWeight = FontWeight.ExtraBold))
        drawText(lay, topLeft = Offset(mid.x - lay.size.width / 2f, mid.y - lay.size.height / 2f), alpha = op)
    }
    // 특수 블럭이 터지는 그림
    for (b in g.blasts) {   // ios: Views/BrickGameView.swift:463
        val k = min(1.0, (g.t - b.t0) / 0.45); val a = (1 - k).toFloat()   // ios: Views/BrickGameView.swift:464
        when (b.kind) {   // ios: Views/BrickGameView.swift:465
            BrickGame.Special.bomb -> {   // ios: Views/BrickGameView.swift:466
                val rr = 1 + k * 2.6   // ios: Views/BrickGameView.swift:467
                val e = r(b.x - rr, b.y - rr, rr * 2, rr * 2); drawOval(pal.orange.copy(alpha = 0.55f * a), e.topLeft, e.size, alpha = op)   // ios: Views/BrickGameView.swift:468
                val e2 = r(b.x - rr, b.y - rr, rr * 2, rr * 2); drawOval(pal.yellow.copy(alpha = a), e2.topLeft, e2.size, alpha = op, style = Stroke(p(2.0)))   // ios: Views/BrickGameView.swift:469
            }
            BrickGame.Special.row -> { val e = r(0.0, b.y - 0.5, BrickGame.W, 1.0); drawRect(pal.yellow.copy(alpha = 0.8f * a), e.topLeft, e.size, alpha = op) }   // ios: Views/BrickGameView.swift:470
            BrickGame.Special.col -> { val e = r(b.x - 0.5, BrickGame.top, 1.0, BrickGame.H - BrickGame.top); drawRect(pal.yellow.copy(alpha = 0.8f * a), e.topLeft, e.size, alpha = op) }   // ios: Views/BrickGameView.swift:471
            BrickGame.Special.multi, BrickGame.Special.gift -> {   // ios: Views/BrickGameView.swift:472
                val rr = 0.6 + k * 1.4   // ios: Views/BrickGameView.swift:473
                val e = r(b.x - rr, b.y - rr, rr * 2, rr * 2); drawOval(pal.blue.copy(alpha = a), e.topLeft, e.size, alpha = op, style = Stroke(p(1.5)))   // ios: Views/BrickGameView.swift:474
            }
        }
    }
    op = 1f   // ios: Views/BrickGameView.swift:477
    if (g.exitCell >= 0 && g.hp[g.exitCell] > 0) {   // ios: Views/BrickGameView.swift:478
        val rw = g.exitCell / 40   // ios: Views/BrickGameView.swift:479 g.rect(exitCell)
        val e = r((g.exitCell % 40) + g.sway(rw), BrickGame.top + (24 - rw), 1.0, 1.0); drawRect(pal.yellow, e.topLeft, e.size, alpha = op)   // ios: Views/BrickGameView.swift:480
    }
    // 남은 공 (판 왼쪽 아래): 패들 위 지금 공은 빼고 그린다
    val lives = max(0, g.lives - 1)   // ios: Views/BrickGameView.swift:483
    for (k in 0 until lives) {   // ios: Views/BrickGameView.swift:484
        val e = r(0.8 + k * 1.4, BrickGame.H - 1.4, 0.9, 0.9)   // ios: Views/BrickGameView.swift:485
        drawOval(Color.White, e.topLeft, e.size); drawOval(pal.ink, e.topLeft, e.size, style = Stroke(p(1.0)))   // ios: Views/BrickGameView.swift:486
    }
    // 보호막
    if (g.shield) {   // ios: Views/BrickGameView.swift:489 진하고 두껍게, 살짝 숨 쉬듯
        val glow = (0.75 + 0.25 * sin(now * 4)).toFloat()   // ios: Views/BrickGameView.swift:490
        val bar = r(0.0, 0.15, BrickGame.W, 0.9)   // ios: Views/BrickGameView.swift:491
        val top = r(0.0, 0.0, BrickGame.W, 1.4); drawRect(pal.yellow.copy(alpha = 0.25f * glow), top.topLeft, top.size)   // ios: Views/BrickGameView.swift:492
        drawRoundRect(pal.orange.copy(alpha = glow), bar.topLeft, bar.size, CornerRadius((0.3 * s).toFloat()))   // ios: Views/BrickGameView.swift:493
        drawRoundRect(pal.yellow, bar.topLeft, bar.size, CornerRadius((0.3 * s).toFloat()), style = Stroke(p(2.0)))   // ios: Views/BrickGameView.swift:494
    }
    // 시오 안전망: 판 뒤 노란 줄
    if (g.net > 0) {   // ios: Views/BrickGameView.swift:497
        val y = (BrickGame.netY * s).toFloat()   // ios: Views/BrickGameView.swift:498
        val path = Path().apply { moveTo(0f, y); lineTo((BrickGame.W * s).toFloat(), y) }   // ios: Views/BrickGameView.swift:499
        drawPath(path, pal.yellow, style = Stroke(max(p(2.0), (s * 0.3).toFloat())))   // ios: Views/BrickGameView.swift:500
    }
    // 판과 선수
    val pw = g.paddleW   // ios: Views/BrickGameView.swift:503
    val pad = r(g.paddleX - pw / 2, BrickGame.PY, pw, BrickGame.PH); drawRoundRect(pal.ink, pad.topLeft, pad.size, CornerRadius((0.4 * s).toFloat()))   // ios: Views/BrickGameView.swift:504
    symbols["player"]?.let { sp ->   // ios: Views/BrickGameView.swift:505
        // 도트가 화면 픽셀에 딱 맞게 (어긋나면 머리 줄이 뭉개진다), 발끝 줄을 판 윗면에 붙인다
        fun snap(v: Double): Float = v.roundToInt().toFloat()   // ios: Views/BrickGameView.swift:507 (화면 픽셀 단위라 그대로 반올림)
        val foot = sp.h * pace.foot   // ios: Views/BrickGameView.swift:508
        drawSprite(sp, snap(g.paddleX * s - sp.w / 2), snap(BrickGame.PY * s - foot))   // ios: Views/BrickGameView.swift:509
    }
    // 떨어지는 물건 (위로 올라온다)
    for (d in g.drops) {   // ios: Views/BrickGameView.swift:512
        symbols[d.id]?.let { sp -> drawSprite(sp, (d.x * s).toFloat() - sp.w / 2, (d.y * s).toFloat() - sp.h / 2) }   // ios: Views/BrickGameView.swift:513
    }
    // 공
    val br = BrickGame.r   // ios: Views/BrickGameView.swift:516
    for (b in g.balls) {   // ios: Views/BrickGameView.swift:517
        if (g.on("light") && !b.stuck) {   // ios: Views/BrickGameView.swift:518
            val from = Offset((b.x * s).toFloat(), (b.y * s).toFloat())   // ios: Views/BrickGameView.swift:519
            val v = max(0.001, sqrt(b.vx * b.vx + b.vy * b.vy))   // ios: Views/BrickGameView.swift:520
            val to = Offset(((b.x + b.vx / v * 14) * s).toFloat(), ((b.y + b.vy / v * 14) * s).toFloat())   // ios: Views/BrickGameView.swift:521
            drawLine(pal.yellow, from, to, strokeWidth = p(1.5), pathEffect = PathEffect.dashPathEffect(floatArrayOf(p(4.0), p(4.0))))   // ios: Views/BrickGameView.swift:522
        }
        if (g.stage.mirror) {   // ios: Views/BrickGameView.swift:524
            for (mx in doubleArrayOf(b.x - 13, b.x + 13)) { if (!(mx > 0 && mx < BrickGame.W)) continue   // ios: Views/BrickGameView.swift:525
                val e = r(mx - br, b.y - br, br * 2, br * 2); drawOval(pal.ink.copy(alpha = 0.18f), e.topLeft, e.size)   // ios: Views/BrickGameView.swift:526
            }
        }
        val e = r(b.x - br, b.y - br, br * 2, br * 2)   // ios: Views/BrickGameView.swift:529
        drawOval(Color.White, e.topLeft, e.size)   // ios: Views/BrickGameView.swift:530
        drawOval(pal.ink, e.topLeft, e.size, style = Stroke(p(1.5)))   // ios: Views/BrickGameView.swift:531
    }
    if (g.stage.well) {   // ios: Views/BrickGameView.swift:533
        val e = r(BrickGame.W / 2 - 1.2, 13 - 1.2, 2.4, 2.4)   // ios: Views/BrickGameView.swift:534
        drawOval(pal.ink.copy(alpha = 0.35f), e.topLeft, e.size, style = Stroke(p(1.5), pathEffect = PathEffect.dashPathEffect(floatArrayOf(p(3.0), p(3.0)))))   // ios: Views/BrickGameView.swift:535
    }
    if (g.stage.portal) {   // ios: Views/BrickGameView.swift:537
        val a = r(0.0, BrickGame.PY + 2, 0.25, BrickGame.H - BrickGame.PY - 2); drawRect(pal.yellow.copy(alpha = 0.6f), a.topLeft, a.size)   // ios: Views/BrickGameView.swift:538
        val b = r(BrickGame.W - 0.25, BrickGame.PY + 2, 0.25, BrickGame.H - BrickGame.PY - 2); drawRect(pal.yellow.copy(alpha = 0.6f), b.topLeft, b.size)   // ios: Views/BrickGameView.swift:539
    }
}

/** 그림 한 장: (x, y) 는 틀 왼쪽 위. 그림은 틀 아래에 붙인다 */
private fun DrawScope.drawSprite(sp: Sprite, x: Float, y: Float) {
    drawImage(sp.img, IntOffset0, IntSize(sp.img.width, sp.img.height),
        androidx.compose.ui.unit.IntOffset(x.roundToInt(), (y + sp.h - sp.imgH).roundToInt()),
        IntSize(sp.w.roundToInt(), sp.imgH.roundToInt()), filterQuality = FilterQuality.None)
}

/** 판 위 선수: 판이 움직이는 쪽으로 걷고, 멈추면 앞을 보고 숨쉰다 (동네·쉼터와 같은 3/4 그림) */
// ios: Views/BrickGameView.swift:548
private fun runner(g: BrickGame, s: Double, now: Double, pace: PaddleStride, sprBmp: ImageBitmap?, meBmp: ImageBitmap?, mine: MeChar?): Sprite? {
    val t = now   // ios: Views/BrickGameView.swift:549
    val st = pace.step(g.paddleX, t)   // ios: Views/BrickGameView.swift:550
    // 걸을 땐 윗몸도 들썩인다 (머리 위 여유 줄이 있어 안 잘린다). 서 있을 땐 숨은 그리는 쪽 들썩임으로
    // ios: Views/BrickGameView.swift:552
    val k = (t / 0.15).toInt(); val f = CharScene(dir = st.first, leg = if (st.second) intArrayOf(0, 1, 0, 2)[k % 4] else 0, lift = st.second && k % 2 == 1, room = true)
    val me = if (g.player == "me") mine else null   // ios: Views/BrickGameView.swift:553
    val img = me?.let { MeMotion.image(it, f) } ?: (if (FriendMotion.has(g.player)) FriendMotion.image(g.player, f) else null)   // ios: Views/BrickGameView.swift:554
    val out = pace.measure(img)
    if (out != null) {   // ios: Views/BrickGameView.swift:555
        val h = if (me != null) MeArt.H else FriendMotion.H; val w = if (me != null) MeArt.W else FriendMotion.W   // ios: Views/BrickGameView.swift:556
        val d = dot(s, h)   // ios: Views/BrickGameView.swift:557
        return Sprite(out, (d * w).toFloat(), (d * h).toFloat(), (d * h * out.height / h).toFloat())   // ios: Views/BrickGameView.swift:558 SceneSprite(rows: h)
    } else {   // ios: Views/BrickGameView.swift:559
        val src = meBmp ?: sprBmp ?: return null
        return Sprite(src, (dot(s, 34) * 24).toFloat(), (dot(s, 34) * 34).toFloat())   // ios: Views/BrickGameView.swift:560
    }
}

/** 선수 한 도트 크기: 화면 픽셀 정수배로 내림 (s 가 이미 화면 픽셀이라 px 를 곱하고 나누지 않는다) */
private fun dot(s: Double, h: Int = FriendMotion.H): Double = max(1.0, floor(3.6 * s / h))   // ios: Views/BrickGameView.swift:565

/** 특수 블럭 한 칸 (폭탄은 반짝인다) */
// ios: Views/BrickGameView.swift:568
private fun DrawScope.special(sp: BrickGame.Special, rc: Rect, now: Double, pal: Palette, px: Double, op: Float) {
    val w = rc.width; val mid = rc.center   // ios: Views/BrickGameView.swift:569
    when (sp) {   // ios: Views/BrickGameView.swift:570
        BrickGame.Special.bomb -> {   // ios: Views/BrickGameView.swift:571
            drawRect(pal.slate, rc.topLeft, rc.size, alpha = op)   // ios: Views/BrickGameView.swift:572
            val pulse = (0.5 + 0.5 * sin(now * 6)).toFloat()   // ios: Views/BrickGameView.swift:573
            val q = rc.inset(w * 0.18f, w * 0.18f); drawOval(pal.up.copy(alpha = 0.6f + 0.4f * pulse), q.topLeft, q.size, alpha = op)   // ios: Views/BrickGameView.swift:574
            drawOval(pal.yellow, Offset(rc.right - w * 0.35f, rc.top + w * 0.05f), Size(w * 0.25f, w * 0.25f), alpha = op)   // ios: Views/BrickGameView.swift:575
        }
        BrickGame.Special.row, BrickGame.Special.col -> {   // ios: Views/BrickGameView.swift:576
            drawRect(pal.orange, rc.topLeft, rc.size, alpha = op)   // ios: Views/BrickGameView.swift:577
            val lw = max((1.2 * px).toFloat(), w * 0.18f)   // ios: Views/BrickGameView.swift:578,581
            if (sp == BrickGame.Special.row) drawLine(Color.White, Offset(rc.left + w * 0.15f, mid.y), Offset(rc.right - w * 0.15f, mid.y), lw, alpha = op)   // ios: Views/BrickGameView.swift:579
            else drawLine(Color.White, Offset(mid.x, rc.top + w * 0.15f), Offset(mid.x, rc.bottom - w * 0.15f), lw, alpha = op)   // ios: Views/BrickGameView.swift:580
        }
        BrickGame.Special.multi -> {   // ios: Views/BrickGameView.swift:582
            drawRect(pal.blue, rc.topLeft, rc.size, alpha = op)   // ios: Views/BrickGameView.swift:583
            for ((dx, dy) in listOf(-0.2f to 0.15f, 0.2f to 0.15f, 0f to -0.18f)) {   // ios: Views/BrickGameView.swift:584
                val d = w * 0.22f   // ios: Views/BrickGameView.swift:585
                drawOval(Color.White, Offset(mid.x + dx * w - d / 2, mid.y + dy * w - d / 2), Size(d, d), alpha = op)   // ios: Views/BrickGameView.swift:586
            }
        }
        BrickGame.Special.gift -> {   // ios: Views/BrickGameView.swift:588
            drawRect(pal.yellow, rc.topLeft, rc.size, alpha = op)   // ios: Views/BrickGameView.swift:589
            drawRect(pal.up, Offset(mid.x - w * 0.08f, rc.top), Size(w * 0.16f, w), alpha = op)   // ios: Views/BrickGameView.swift:590
            drawRect(pal.up, Offset(rc.left, mid.y - w * 0.08f), Size(w, w * 0.16f), alpha = op)   // ios: Views/BrickGameView.swift:591
        }
    }
}

/** 물건 먹을 때 뜨는 말 (진한 청록 바탕 · 흰 글씨 · 청록 테두리) */
private val toastBg = hex(0x0F2E2A)   // ios: Views/BrickGameView.swift:596

// ios: Views/BrickGameView.swift:598
@Composable
private fun Overlay(g: BrickGame, hud: Hud, record: GameRecord, onClose: (Triple<GameStage, GameLevel, Boolean>?) -> Unit, act: (() -> Unit) -> Unit) {
    when (hud.phase) {   // ios: Views/BrickGameView.swift:599
        BrickGame.Phase.ready ->   // ios: Views/BrickGameView.swift:600
            Intro(g) { act { g.startPlay() } }   // ios: Views/BrickGameView.swift:601
        BrickGame.Phase.playing ->   // ios: Views/BrickGameView.swift:602
            hud.toast?.let { t ->   // ios: Views/BrickGameView.swift:603
                // ios: Views/BrickGameView.swift:604,605,606,607,608,609
                Box(Modifier.padding(horizontal = 16.dp).defaultMinSize(minHeight = 48.dp).background(toastBg, CapsuleShape).border(2.dp, Theme.teal, CapsuleShape)
                    .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    FitText(t, appFont(22f, FontWeight.Bold), 0.6f, maxLines = 1, color = Color.White)
                }
            }
        BrickGame.Phase.paused ->   // ios: Views/BrickGameView.swift:611
            Dim {   // ios: Views/BrickGameView.swift:612
                Bubble(g, "잠깐 쉬어요.", sub = null) {   // ios: Views/BrickGameView.swift:613
                    GameButton("계속", primary = true) { act { g.resume() } }   // ios: Views/BrickGameView.swift:614
                    GameButton("그만하기") { g.end(); onClose(null) }   // ios: Views/BrickGameView.swift:615
                }
            }
        BrickGame.Phase.cleared, BrickGame.Phase.over -> {   // ios: Views/BrickGameView.swift:618
            val won = hud.phase == BrickGame.Phase.cleared   // ios: Views/BrickGameView.swift:619
            val next = GameStage.all.firstOrNull { it.n == g.stage.n + 1 }   // ios: Views/BrickGameView.swift:620
            val lose = record.lossCount(g.stage.n, g.level)   // ios: Views/BrickGameView.swift:621
            Dim {   // ios: Views/BrickGameView.swift:622
                if (won && next != null) {   // ios: Views/BrickGameView.swift:623
                    val sc = cformat("%,d", hud.score)
                    Bubble(g, hud.speech, sub = "${sc}점 · 다음 단계로 이어가요") {}   // ios: Views/BrickGameView.swift:624
                } else {   // ios: Views/BrickGameView.swift:625
                    // ios: Views/BrickGameView.swift:626
                    // Views/BrickGameView.swift:627 (검사 예외: 보간 안 따옴표라 검사가 문구를 못 나눔 — 옮김)
                    Bubble(g, hud.speech,
                        sub = "${if (won) "깼어요" else "끝났어요"} · ${cformat("%,d", hud.score)}점${if (hud.newBest) " · 새 최고 기록" else ""}${if (hud.picked.isEmpty()) "" else " · 먹은 물건 ${hud.picked.values.fold(0) { a, b -> a + b }}개"}") {
                        GameButton("다시 하기", primary = true) { onClose(Triple(g.stage, g.level, false)) }   // ios: Views/BrickGameView.swift:628
                        val easy = g.level.easier
                        if (!won && lose >= 3 && easy != null) {   // ios: Views/BrickGameView.swift:629
                            GameButton("쉬운 판으로 갈까요? · ${easy.title}") { onClose(Triple(g.stage, easy, false)) }   // ios: Views/BrickGameView.swift:630
                        }
                        GameButton("단계 고르기") { onClose(null) }   // ios: Views/BrickGameView.swift:632
                    }
                }
            }
        }
    }
}

/** 시작 카드: 방 이름·설명·방 특성만 여유 있게, "눌러서 시작"을 눌러야 시작한다 (2026-10-19) */
// ios: Views/BrickGameView.swift:640,641,656,657,658,659,660
@Composable
private fun Intro(g: BrickGame, onStart: () -> Unit) {
    Column(Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(2.dp, Theme.ink, RoundedCornerShape(16.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/BrickGameView.swift:642
            CharPixel(g.player, 34.dp, 48.dp, Modifier.charIdle())   // ios: Views/BrickGameView.swift:643
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/BrickGameView.swift:644
                T("${g.stage.bg.volume} ${g.stage.bg.name}", style = appFont(28f, FontWeight.Bold))   // ios: Views/BrickGameView.swift:645
                T(g.stage.scene, style = appFont(19f), color = Theme.sub)   // ios: Views/BrickGameView.swift:646
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/BrickGameView.swift:649
            if (g.chain > 0) Line("이어하기 · 점수 ×" + cformat("%.1f", g.scoreMul) + " · 생명 ${g.lives}")   // ios: Views/BrickGameView.swift:650
            if (g.lifeBonus) Line("연속 ${g.chain}단계 · 생명 +1", Theme.up, bold = true)   // ios: Views/BrickGameView.swift:651
            Line(g.stage.short)   // ios: Views/BrickGameView.swift:652
        }
        Box(Modifier.padding(top = 8.dp)) { GameButton("눌러서 시작", primary = true, onClick = onStart) }   // ios: Views/BrickGameView.swift:654
    }
}
// ios: Views/BrickGameView.swift:662,663
@Composable
private fun Line(t: String, color: Color = Theme.ink, bold: Boolean = false) =
    T(t, style = appFont(21f, if (bold) FontWeight.Bold else FontWeight.Normal).ls(4f), color = color)

// ios: Views/BrickGameView.swift:666,667
@Composable
private fun Dim(c: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { c() }

// ios: Views/BrickGameView.swift:670,671,682,683,684,685,686
@Composable
private fun Bubble(g: BrickGame, line: String, sub: String?, buttons: @Composable () -> Unit) {
    Column(Modifier.padding(24.dp).widthIn(max = 340.dp).fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(2.dp, Theme.ink, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/BrickGameView.swift:672
            CharPixel(g.player, 34.dp, 48.dp, Modifier.charIdle())   // ios: Views/BrickGameView.swift:673
            Column(Modifier.weight(1f).widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/BrickGameView.swift:674,678
                T(line, style = appFont(21f, FontWeight.Bold))   // ios: Views/BrickGameView.swift:675
                if (sub != null) T(sub, style = appFont(12f), color = Theme.sub)   // ios: Views/BrickGameView.swift:676
            }
        }
        buttons()   // ios: Views/BrickGameView.swift:680
    }
}

// ios: Views/BrickGameView.swift:689,690,691,692,693,694,696
@Composable
private fun GameButton(t: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(if (primary) Theme.teal else Theme.card, RoundedCornerShape(12.dp))
        .border(1.5.dp, Theme.teal, RoundedCornerShape(12.dp)).plainClickable(onClick), contentAlignment = Alignment.Center) {
        T(t, style = appFont(14f, FontWeight.Bold), color = if (primary) Color.White else Theme.teal)
    }
}

/** 단계 하나의 내 기록 순위 (이 폰의 높은 점수 10판) */
// ios: Views/BrickGameView.swift:701,707,708
@Composable
fun BrickBoardSheet(
    stage: GameStage,   // ios: Views/BrickGameView.swift:702
    level: GameLevel,   // ios: Views/BrickGameView.swift:703
    list: List<GameRecord.Play>,   // ios: Views/BrickGameView.swift:704
    dismiss: () -> Unit,   // ios: Views/BrickGameView.swift:705
) {
    Column(Modifier.fillMaxSize().background(Theme.bg)) {   // ios: Views/BrickGameView.swift:728
        SheetBar("내 기록 순위", trailing = "닫기", onClick = dismiss)   // ios: Views/BrickGameView.swift:729,730,731
        // ios: Views/BrickGameView.swift:709,710,726
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("${stage.bg.volume} ${stage.bg.name} · ${level.title}", style = appFont(15f, FontWeight.Bold))   // ios: Views/BrickGameView.swift:711
            if (list.isEmpty()) T("아직 이 단계 기록이 없어요.", style = appFont(14f), color = Theme.sub)   // ios: Views/BrickGameView.swift:712
            list.forEachIndexed { i, e ->   // ios: Views/BrickGameView.swift:713
                // ios: Views/BrickGameView.swift:714,721,722
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).background(Theme.card, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ios: Views/BrickGameView.swift:715
                    T("${i + 1}", Modifier.width(26.dp), style = appFont(14f, FontWeight.Bold).copy(fontFeatureSettings = Tnum, textAlign = androidx.compose.ui.text.style.TextAlign.End))
                    CharPixel(if (e.p == "me") "seri" else e.p, 17.dp, 24.dp)   // ios: Views/BrickGameView.swift:716
                    T(monthDay(e.at), style = appFont(14f, FontWeight.Bold))   // ios: Views/BrickGameView.swift:717
                    Spacer(Modifier.weight(1f))   // ios: Views/BrickGameView.swift:718
                    T("${cformat("%,d", e.score)}점", style = appFont(14f, FontWeight.Bold).copy(fontFeatureSettings = Tnum))   // ios: Views/BrickGameView.swift:719
                }
            }
            T("이 폰 안에만 남는 기록이에요. 아무 데도 보내지 않아요.", style = appFont(12f), color = Theme.sub)   // ios: Views/BrickGameView.swift:724
        }
    }
}

/** .dateTime.month().day() (한국어: 10월 21일) */
private fun monthDay(at: Instant): String {
    val d = at.atZone(ZoneId.systemDefault())
    return "${d.monthValue}월 ${d.dayOfMonth}일"
}

/** 정지 그림이 아니게: 숨 쉬듯 위아래로 조금, 아주 살짝 기울고, 말을 시작할 때 한 번 뛴다. 그리기 단계에서만 읽는다 */
// ios: Views/BrickGameView.swift:737,738,739,740,743
private fun Modifier.alive(clock: State<Double>, seed: Double, reduce: Boolean, hop: () -> Boolean): Modifier = if (reduce) this else graphicsLayer {
    val t = clock.value
    val bob = sin((t + seed) * 2.4) * 1.2   // ios: Views/BrickGameView.swift:741
    val jump = if (hop()) -abs(sin((t + seed) * 12)) * 4 else 0.0   // ios: Views/BrickGameView.swift:742
    transformOrigin = TransformOrigin(0.5f, 1f)
    translationY = ((bob + jump) * density).toFloat()   // ios: Views/BrickGameView.swift:744
    rotationZ = (sin((t + seed) * 1.3) * 1.5).toFloat()   // ios: Views/BrickGameView.swift:745
    scaleX = 1f; scaleY = (1 + sin((t + seed) * 2.4 + 1) * 0.012).toFloat()   // ios: Views/BrickGameView.swift:746
}

/** 판 걸음 기억: 직전 판 위치로 걷는 방향을 정한다 (그리기 중에 바꿔도 화면을 다시 부르지 않게 관찰 안 함) */
// ios: Views/BrickGameView.swift:751
private class PaddleStride {
    private var lastX = BrickGame.W / 2; private var movedAt = 0.0; private var dir = CharDir.down   // ios: Views/BrickGameView.swift:752
    /** 그림 높이 중 발끝(마지막 칠한 줄)까지의 비율. 그림마다 아래 빈 줄 수가 달라 한 번씩 재 둔다 */
    var foot = 1.0   // ios: Views/BrickGameView.swift:754
    private class Feet(val src: Bitmap, val out: ImageBitmap, val foot: Double)
    private val feet = IdentityHashMap<Bitmap, Feet>()   // ios: Views/BrickGameView.swift:755
    /** 그림 위에 빈 줄 한 줄을 덧대 돌려주고 발끝을 잰다 (그림이 없으면 판 바로 위).
     *  시오처럼 머리가 그림 맨 윗줄에 닿으면 가장자리라 윗줄이 반쯤 지워져 보여서 덧댄다 */
    fun measure(img: Bitmap?): ImageBitmap? {   // ios: Views/BrickGameView.swift:758
        if (img == null) { foot = 1.0; return null }   // ios: Views/BrickGameView.swift:759
        val k = img   // ios: Views/BrickGameView.swift:760
        val c = feet[k]; if (c != null && c.src === img) { foot = c.foot; return c.out }   // ios: Views/BrickGameView.swift:761
        if (img.config == Bitmap.Config.HARDWARE) { foot = 1.0; return img.asImageBitmap() }   // ios: Views/BrickGameView.swift:762 (픽셀을 못 읽는 그림)
        val w = img.width; val h = img.height + 1   // ios: Views/BrickGameView.swift:763
        val p = IntArray(w * h) { 0 }   // ios: Views/BrickGameView.swift:764
        // 아래 붙여 그려 맨 윗줄이 빈다 (첫 줄을 건너뛰고 원본 픽셀을 그대로 옮긴다: 보간 없음)
        // Views/BrickGameView.swift:766,767 (검사 예외: CGContext 비트맵 문맥 — 안드로이드는 ARGB_8888 Bitmap 이 같은 일)
        img.getPixels(p, w, w, 0, 0, w, h - 1)   // ios: Views/BrickGameView.swift:765,768,769,770
        val out = runCatching { Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (out == null) { foot = 1.0; return img.asImageBitmap() }   // ios: Views/BrickGameView.swift:772
        val last = (0 until h).lastOrNull { y -> (0 until w).any { p[y * w + it] ushr 24 != 0 } } ?: (h - 1)   // ios: Views/BrickGameView.swift:773
        if (feet.size > 64) feet.clear()   // ios: Views/BrickGameView.swift:774
        val ui = out.asImageBitmap()   // ios: Views/BrickGameView.swift:775
        foot = (last + 1).toDouble() / h.toDouble()   // ios: Views/BrickGameView.swift:776
        feet[k] = Feet(img, ui, foot)   // ios: Views/BrickGameView.swift:777
        return ui   // ios: Views/BrickGameView.swift:778
    }
    fun step(x: Double, t: Double): Pair<CharDir, Boolean> {   // ios: Views/BrickGameView.swift:780
        val dx = x - lastX   // ios: Views/BrickGameView.swift:781
        if (abs(dx) > 0.02) { dir = if (dx > 0) CharDir.right else CharDir.left; movedAt = t }   // ios: Views/BrickGameView.swift:782
        lastX = x   // ios: Views/BrickGameView.swift:783
        val walking = t - movedAt < 0.2   // ios: Views/BrickGameView.swift:784
        return (if (walking) dir else CharDir.down) to walking   // ios: Views/BrickGameView.swift:785
    }
}

/** 미니 게임이 켜져 있는 동안 시세·위젯 갱신을 미룬다 (App 의 1분 갱신이 본다) */
// ios: Views/BrickGameView.swift:790
object GamePause {
    @Volatile var count = 0   // ios: Views/BrickGameView.swift:791
    val on: Boolean get() = count > 0   // ios: Views/BrickGameView.swift:792
}

/** .pausesRefresh(): 화면에 있는 동안 GamePause 를 하나 잡는다 */
// ios: Views/BrickGameView.swift:795,796,797,798,799
@Composable
fun RefreshPause(active: Boolean) {
    val held = remember { booleanArrayOf(false) }
    // ios: Views/BrickGameView.swift:804,805,806,807
    fun hold(v: Boolean) {
        if (v == held[0]) return
        held[0] = v
        GamePause.count += if (v) 1 else -1
    }
    // ios: Views/BrickGameView.swift:800,801,802
    DisposableEffect(active) { hold(active); onDispose { } }
    DisposableEffect(Unit) { onDispose { hold(false) } }
}

// ios: Views/BrickGameView.swift:811,812
@Composable
fun Modifier.pausesRefresh(active: Boolean = true): Modifier { RefreshPause(active); return this }

// ── 기기 쪽: 엔진(core/game/BrickGame.kt)이 부르는 소리·진동(GameFx)과 기울기(GameMotion) ──
// iOS 는 엔진 안에서 AVAudioEngine·UIFeedbackGenerator·CMMotionManager 를 바로 쓴다. 안드로이드 엔진은 이 둘을 받아 쓴다.
// 효과음은 엔진의 GameSound(코드로 만든 파형을 AudioTrack 으로)가 내고, 여기서는 설정 스위치·무음 모드만 거른다.

/** 소리·진동: 설정의 '게임 소리'·'게임 진동'을 따른다 (WaveFeedbackKeys, 파도 타기와 같은 스위치) */
class BrickFx(ctx: Context, private val view: View) : GameFx {
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    @Volatile private var soundOn = true
    @Volatile private var hapticOn = true

    /** 판을 시작할 때 (begin): 스위치를 읽고 소리 판을 켠다 */
    override fun start() {
        soundOn = Defaults.prefs.getBoolean(WaveFeedbackKeys.soundKey, true)
        hapticOn = Defaults.prefs.getBoolean(WaveFeedbackKeys.hapticKey, true)
        if (soundOn) GameSound.shared.start()
    }
    /** 효과음: 무음·진동 모드면 내지 않는다 (iOS .ambient 가 무음 스위치를 따르듯) */
    override fun play(k: GameSound.Kind) {
        if (!soundOn) return
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        GameSound.shared.play(k)
    }
    /** UIImpactFeedbackGenerator: 세기(intensity)까지 Vibrator 로. light = 10ms·110 (파도 타기와 같은 표), 판·벽돌에 맞을 때 0.6 */
    override fun impact(style: Impact, intensity: Double) {
        if (!hapticOn) return
        val v = vib ?: return
        val (ms, amp) = when (style) { Impact.light -> 10L to 110; Impact.medium -> 14L to 180; Impact.heavy -> 18L to 255 }
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, (amp * intensity).roundToInt().coerceIn(1, 255))) }
    }
    /** UINotificationFeedbackGenerator: 깸 = success, 공 놓침 = warning. API 30 부터는 같은 뜻의 기기 진동(CONFIRM·REJECT) */
    override fun notify(type: Notice) {
        if (!hapticOn) return
        if (Build.VERSION.SDK_INT >= 30) {
            view.post { view.performHapticFeedback(if (type == Notice.success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT) }
            return
        }
        val v = vib ?: return
        val e = if (type == Notice.success) VibrationEffect.createWaveform(longArrayOf(0, 14, 80, 18), intArrayOf(0, 170, 0, 255), -1)
        else VibrationEffect.createWaveform(longArrayOf(0, 18, 100, 14), intArrayOf(0, 230, 0, 160), -1)
        runCatching { v.vibrate(e) }
    }
}

/**
 * 기울이기 (CMMotionManager 자리): 중력 센서, 없으면 가속도 센서를 걸러 쓴다.
 * 엔진이 켜 달라고 한 동안(begin~end) 중에서도 판이 움직이는 때(준비·진행)·화면에 보일 때만 센서를 단다 (visible).
 * 안드로이드 센서 축은 iOS 와 같고 부호만 반대(바닥을 미는 힘), 단위는 m/s² → −값/9.81 로 iOS gravity(g) 와 맞춘다.
 * 감도(약 24도에 끝까지)·따라가기(dt × 14)는 엔진 movePaddle 이 iOS 와 같이 한다.
 */
class BrickTilt(ctx: Context, private val view: View) : GameMotion, SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val raw = sensor?.type == Sensor.TYPE_ACCELEROMETER
    private var wanted = false
    private var shown = false
    override val isDeviceMotionAvailable: Boolean get() = sensor != null
    override var isDeviceMotionActive = false
        private set
    override var deviceMotionUpdateInterval = 1.0 / 60
    @Volatile override var gravity: Gravity? = null
        private set

    override fun startDeviceMotionUpdates() { wanted = true; apply() }
    override fun stopDeviceMotionUpdates() { wanted = false; apply() }
    /** 화면이 알려 준다: 판이 움직이는 때이고 보이는지 */
    fun visible(v: Boolean) { shown = v; apply() }
    private fun apply() {
        val on = wanted && shown
        if (on == isDeviceMotionActive) return
        val m = sm; val s = sensor
        if (on && m != null && s != null) { gravity = null; isDeviceMotionActive = m.registerListener(this, s, (deviceMotionUpdateInterval * 1_000_000).toInt()) }
        else { m?.unregisterListener(this); isDeviceMotionActive = false; gravity = null }
    }
    override fun onSensorChanged(e: SensorEvent) {
        val x = -e.values[0] / SensorManager.GRAVITY_EARTH.toDouble(); val y = -e.values[1] / SensorManager.GRAVITY_EARTH.toDouble()
        val g = gravity
        // 가속도 센서는 흔들림이 섞여 있어 저역 거르기로 중력만 남긴다 (중력 센서는 이미 걸러져 있다)
        gravity = if (raw && g != null) Gravity(g.x + (x - g.x) * 0.2, g.y + (y - g.y) * 0.2) else Gravity(x, y)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    /** iOS landscapeRight(홈 쪽이 오른쪽) = 안드로이드 ROTATION_90 */
    override fun orientation(): Orientation = when (view.display?.rotation) {
        Surface.ROTATION_90 -> Orientation.landscapeRight
        Surface.ROTATION_270 -> Orientation.landscapeLeft
        else -> Orientation.portrait
    }
}

/** CGRect.insetBy(dx:dy:) */
private fun Rect.inset(dx: Float, dy: Float): Rect = Rect(left + dx, top + dy, right - dx, bottom - dy)
