package com.naeilo.app.ui.wave

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.core.calc.WaveEngine
import com.naeilo.app.core.calc.waveLine
import com.naeilo.app.data.MeChar
import com.naeilo.app.ui.CharDir
import com.naeilo.app.ui.CharPose
import com.naeilo.app.ui.CharScene
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.MeMotion
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.game.GamePause
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.plainClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// 파도 타기 한 판 (2번: 플레이 화면). 파도·인물·점 효과를 Canvas 하나에 그리고 30fps 루프로 엔진을 굴린다.
// 끝나면 같은 창 위에 WaveResultPopup. 진행·별 저장(4번)은 onFinish 에 붙인다.
// 안드로이드: 루프는 withFrameNanos 로 화면 프레임마다 깨어 33ms 가 지났을 때만 한 걸음 (iOS Task.sleep 33ms 와 같은 dt).
// 엔진·연출은 Compose 상태가 아닌 보통 객체(WaveRun)라 매 걸음 frame 숫자만 올린다: 그리기 단계만 다시 (다시 짜기 없음).
// 위 글줄(HUD)은 바뀐 값이 있을 때만 다시 짠다 (Hud 데이터 클래스 비교).

// ios: Views/Wave/WavePlayView.swift:6,26
@Composable
fun WavePlayView(
    m: AppModel,   // ios: Views/Wave/WavePlayView.swift:7
    /** 없으면 내 자산 라인에서 새로 자른다 */
    course: WaveEngine.Course? = null,   // ios: Views/Wave/WavePlayView.swift:10
    onFinish: (WaveResult) -> Unit = {},   // ios: Views/Wave/WavePlayView.swift:11
) {
    val nav = LocalNavBack.current
    val dismiss = { nav?.pop?.invoke(); Unit }   // ios: Views/Wave/WavePlayView.swift:8
    val st = remember { WaveRun(WaveFeedback.shared) }   // g·fx·rides·boardDay (WaveRun 안)
    var result by remember { mutableStateOf<WaveResult?>(null) }   // ios: Views/Wave/WavePlayView.swift:15
    var run by remember { mutableIntStateOf(0) }   // ios: Views/Wave/WavePlayView.swift:18
    val board = remember { SurfCloset.board }   // ios: Views/Wave/WavePlayView.swift:19
    val hat = remember { SurfCloset.hat }   // ios: Views/Wave/WavePlayView.swift:20
    /** 3년 라인은 한 번만 만든다 (바로 다시 할 때 멈칫 없게) */
    var line by remember { mutableStateOf<List<WaveEngine.Pt>?>(null) }   // ios: Views/Wave/WavePlayView.swift:22
    /** 규칙 카드를 닫아야 파도가 움직인다 (바로 다시 할 땐 다시 안 뜸) */
    var started by remember { mutableStateOf(false) }   // ios: Views/Wave/WavePlayView.swift:24
    // 그리기 다시 하라는 신호 (그리기 람다만 읽는다) · 위 글줄 값 · 코스를 만들어 봤는지 (만들기 전엔 '짧아요' 글을 띄우지 않는다)
    val frame = remember { mutableLongStateOf(0L) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var tried by remember { mutableStateOf(false) }
    val tm = rememberTextMeasurer()
    fun publish() { frame.longValue++; hud = st.g?.let { Hud.of(it, st.fx, started) } }

    // ios: Views/Wave/WavePlayView.swift:178
    st.finish = { e, why ->
        val r = WaveResult(course = e.course, score = e.score, hearts = e.hearts, rides = st.rides.toList(), why = why)   // ios: Views/Wave/WavePlayView.swift:179
        r.missions = e.missions.map { WaveResult.MissionRow(it.title, e.missionsDone.contains(it)) }   // ios: Views/Wave/WavePlayView.swift:180
        // 꾸미기: 판 결과를 쌓고 새로 열린 보드·모자를 팝업에 (어려움 완주 = 어려움 코스를 시간 끝까지)
        r.unlocked = SurfCloset.add(score = e.score, fever = e.feverCount, splash = e.splashCount, snap = e.perfectCount,   // ios: Views/Wave/WavePlayView.swift:182
            hardClear = e.course.level == WaveEngine.Level.hard && why == WaveEngine.EndReason.time).map { it.name }   // ios: Views/Wave/WavePlayView.swift:183
        if (r.unlocked.isNotEmpty()) WaveFeedback.shared.crown()   // ios: Views/Wave/WavePlayView.swift:184 톡 톡 + 짤랑
        result = r   // ios: Views/Wave/WavePlayView.swift:185
        onFinish(r)   // ios: Views/Wave/WavePlayView.swift:186
    }

    // ios: Views/Wave/WavePlayView.swift:152
    fun tap() {
        if (!started) { started = true; publish(); return }   // ios: Views/Wave/WavePlayView.swift:153
        val e = st.g; if (e == null || e.isOver) return   // ios: Views/Wave/WavePlayView.swift:154
        val ev = if (e.riding) e.leave() else e.board()   // ios: Views/Wave/WavePlayView.swift:155
        frame.longValue++   // ios: Views/Wave/WavePlayView.swift:156 g = e: 같은 객체를 고쳤으니 그리기만 다시
        st.handle(ev); publish()   // ios: Views/Wave/WavePlayView.swift:157
    }

    // ios: Views/Wave/WavePlayView.swift:189
    fun again() {
        result = null; st.rides.clear(); st.fx = WaveFX()   // ios: Views/Wave/WavePlayView.swift:190
        val c = line?.let { WaveEngine.course(from = it) } ?: st.g?.course; if (c != null) st.g = WaveEngine(course = c)   // ios: Views/Wave/WavePlayView.swift:191
        run += 1   // ios: Views/Wave/WavePlayView.swift:192
    }

    val me = m.me ?: MeChar.current ?: MeChar()   // ios: Views/Wave/WavePlayView.swift:27
    // ios: Views/Wave/WavePlayView.swift:28,44
    Box(Modifier.fillMaxSize().drawBehind { frame.longValue; drawRect(WaveDraw.sky(st.g?.fever ?: false)) }) {
        val h = hud; val g = st.g
        // ios: Views/Wave/WavePlayView.swift:29
        if (h != null && g != null) {
            // ios: Views/Wave/WavePlayView.swift:30,31,32,33
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { tap() } }) {
                frame.longValue
                val e = st.g ?: return@Canvas
                WaveDraw.scene(this, Size(size.width / density, size.height / density), e, st.fx, me = me, board = board, hat = hat, tm = tm)
            }
            WaveHud(g, h, st.fx, frame, dismiss)   // ios: Views/Wave/WavePlayView.swift:34
        } else if (tried) {   // ios: Views/Wave/WavePlayView.swift:35
            // ios: Views/Wave/WavePlayView.swift:36
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T("파도를 만들 자산 기록이 아직 짧아요", style = appFont(14f), color = Theme.inkFixed)   // ios: Views/Wave/WavePlayView.swift:37
                T("닫기", Modifier.plainClickable(dismiss), style = appFont(14f, FontWeight.Bold), color = Theme.teal)   // ios: Views/Wave/WavePlayView.swift:38 Button
            }
        }
        if (h != null && !started) WaveRules(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { started = true; publish() })   // ios: Views/Wave/WavePlayView.swift:41
        result?.let { WaveResultPopup(it, again = { again() }, close = dismiss) }   // ios: Views/Wave/WavePlayView.swift:42
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { WaveFeedback.shared.prepare() } }   // ios: Views/Wave/WavePlayView.swift:45
    // 미니 게임이 켜져 있는 동안 시세·위젯 갱신을 미룬다 (벽돌깨기 RefreshPause 와 같은 수: App 의 1분 갱신이 본다)
    DisposableEffect(Unit) { GamePause.count += 1; onDispose { GamePause.count -= 1 } }

    // MARK: 굴리기
    // ios: Views/Wave/WavePlayView.swift:46,131
    LaunchedEffect(run) {
        // ios: Views/Wave/WavePlayView.swift:132
        if (st.g == null) {
            val l = line ?: withContext(Dispatchers.Default) { m.waveLine }   // ios: Views/Wave/WavePlayView.swift:133
            line = l   // ios: Views/Wave/WavePlayView.swift:134
            val c = course ?: WaveEngine.course(from = l); if (c == null) { tried = true; return@LaunchedEffect }   // ios: Views/Wave/WavePlayView.swift:135
            st.g = WaveEngine(course = c)   // ios: Views/Wave/WavePlayView.swift:136
        }
        tried = true; publish()
        var last = withFrameNanos { it }   // ios: Views/Wave/WavePlayView.swift:138
        // ios: Views/Wave/WavePlayView.swift:139
        while (isActive) {
            val now = withFrameNanos { it }; if (now - last < 33 * 1_000_000L - 1_000_000L) continue   // ios: Views/Wave/WavePlayView.swift:140 (화면 프레임 사이 반올림 1ms 여유)
            val e = st.g; if (e == null || e.isOver) break   // ios: Views/Wave/WavePlayView.swift:141
            val dt = min(0.1, (now - last) / 1e9)   // ios: Views/Wave/WavePlayView.swift:142
            last = now   // ios: Views/Wave/WavePlayView.swift:143
            if (!started) continue   // ios: Views/Wave/WavePlayView.swift:144
            val ev = e.tick(dt)   // ios: Views/Wave/WavePlayView.swift:145
            frame.longValue++   // ios: Views/Wave/WavePlayView.swift:146 g = e
            st.fx.step(dt, riding = e.riding, rising = WaveDraw.rising(e))   // ios: Views/Wave/WavePlayView.swift:147
            st.handle(ev); publish()   // ios: Views/Wave/WavePlayView.swift:148
        }
    }
}

/** 한 판 상태 (Swift WavePlayView 의 @State g·fx·rides·boardDay). 매 걸음 바뀌니 Compose 상태가 아닌 보통 필드 */
class WaveRun(private val feedback: WaveFeedback) {
    var g: WaveEngine? = null   // ios: Views/Wave/WavePlayView.swift:13
    var fx = WaveFX()   // ios: Views/Wave/WavePlayView.swift:14
    val rides = ArrayList<WaveResult.Ride>()   // ios: Views/Wave/WavePlayView.swift:16
    var boardDay = 0   // ios: Views/Wave/WavePlayView.swift:17
    var finish: (WaveEngine, WaveEngine.EndReason) -> Unit = { _, _ -> }

    // ios: Views/Wave/WavePlayView.swift:160
    fun handle(ev: List<WaveEngine.Event>) {
        val e = g; if (ev.isEmpty() || e == null) return   // ios: Views/Wave/WavePlayView.swift:161
        feedback.play(ev)   // ios: Views/Wave/WavePlayView.swift:162
        // ios: Views/Wave/WavePlayView.swift:163
        for (x in ev) {
            // ios: Views/Wave/WavePlayView.swift:164
            when (x) {
                is WaveEngine.Event.board -> boardDay = x.day   // ios: Views/Wave/WavePlayView.swift:165
                is WaveEngine.Event.leave -> rides.add(WaveResult.Ride(from = boardDay, to = x.day, gain = x.gain, splash = false))   // ios: Views/Wave/WavePlayView.swift:166
                // ios: Views/Wave/WavePlayView.swift:167
                is WaveEngine.Event.splash -> {
                    val v = e.course.values   // ios: Views/Wave/WavePlayView.swift:168
                    rides.add(WaveResult.Ride(from = boardDay, to = x.day, gain = v[x.day] / v[boardDay] - 1, splash = true))   // ios: Views/Wave/WavePlayView.swift:169
                }
                is WaveEngine.Event.streak -> if (x.n == 3) feedback.crown()   // ios: Views/Wave/WavePlayView.swift:170
                is WaveEngine.Event.end -> finish(e, x.why)   // ios: Views/Wave/WavePlayView.swift:171
                is WaveEngine.Event.calm, is WaveEngine.Event.fever, is WaveEngine.Event.flip, is WaveEngine.Event.land, is WaveEngine.Event.item,   // ios: Views/Wave/WavePlayView.swift:172
                is WaveEngine.Event.saved, is WaveEngine.Event.mission, is WaveEngine.Event.time -> {}
            }
            fx.react(x)   // ios: Views/Wave/WavePlayView.swift:174
        }
    }
}

/** 위 글줄에 보이는 값 (바뀔 때만 다시 짠다) */
private data class Hud(
    val score: Int, val hearts: Int, val secs: Int, val flash: Int, val got: Int, val streak: Int,
    val fever: Boolean, val dolphin: Boolean, val vest: Boolean, val boost: Int, val slow: Int,
    val done: List<Boolean>, val hint: Boolean?,
) {
    companion object {
        /** 남은 초 값(timeFlash·boost·slow)은 올림한 정수로: 0 보다 큰지는 그대로이고, 매 프레임 다시 짜지 않는다 */
        fun of(e: WaveEngine, fx: WaveFX, started: Boolean) = Hud(
            score = e.score, hearts = e.hearts, secs = ceil(e.timeLeft).toInt(), flash = ceil(fx.timeFlash).toInt(), got = fx.timeGot.toInt(), streak = e.streak,
            fever = e.fever, dolphin = e.dolphin, vest = e.vest, boost = ceil(e.boost).toInt(), slow = ceil(e.slow).toInt(),
            done = e.missions.map { e.missionsDone.contains(it) },
            // 처음 6초 도움말: null 이면 안 보임, 값은 타고 있는지
            hint = if (started && e.elapsed < 6 && !e.isOver) e.riding else null,   // ios: Views/Wave/WavePlayView.swift:118
        )
    }
}

/** 규칙: 처음 한 번 보여 주고 톡 하면 시작 */
// ios: Views/Wave/WavePlayView.swift:50
@Composable
private fun WaveRules(modifier: Modifier) {
    // ios: Views/Wave/WavePlayView.swift:51
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))   // ios: Views/Wave/WavePlayView.swift:52
        // ios: Views/Wave/WavePlayView.swift:53,59,60,61,62,63
        Fg(Theme.ink) {
            Column(
                Modifier.padding(horizontal = 24.dp).widthIn(max = 320.dp).fillMaxWidth().background(Theme.card, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T("이렇게 타요", style = appFont(18f, FontWeight.ExtraBold))   // ios: Views/Wave/WavePlayView.swift:54
                for (s in WavePlayRules.ruleLines) T(s, style = appFont(13f))   // ios: Views/Wave/WavePlayView.swift:55
                T("톡 하면 시작!", Modifier.fillMaxWidth().padding(top = 6.dp), style = appFont(15f, FontWeight.Bold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Theme.teal)   // ios: Views/Wave/WavePlayView.swift:56,57
            }
        }
    }
}

/** WavePlayView 의 static 값 */
object WavePlayRules {
    // ios: Views/Wave/WavePlayView.swift:66
    val ruleLines = listOf(
        "🌊 파도는 내 자산이 지나온 날들이에요. 30초, 점점 빨라져요.",   // ios: Views/Wave/WavePlayView.swift:67
        "⬇️ 바닥에서 톡: 보드에 타요. 바닥 그날 타면 \"딱!\" +50",   // ios: Views/Wave/WavePlayView.swift:68
        "⬆️ 꼭대기에서 톡: 내려요. 탄 뒤 오른 만큼 점수 (0.01% = 1점)",   // ios: Views/Wave/WavePlayView.swift:69
        "💦 탄 채 크게 꺼지면 풍덩, 하트 하나 잃어요. 3번이면 끝",   // ios: Views/Wave/WavePlayView.swift:70
        "🤸 벌고 내리면 점프! 공중에서 톡톡 하면 공중제비 점수",   // ios: Views/Wave/WavePlayView.swift:71
        "🔥 바닥 \"딱!\" 3번 연속이면 피버: 6초간 점수 2배",   // ios: Views/Wave/WavePlayView.swift:72
        "⏱️ \"딱!\"·아이템·피버·미션마다 시간이 늘어요",   // ios: Views/Wave/WavePlayView.swift:73
        "🎁 탄 채 아이템을 지나면 먹어요: ⏰ +3초 · 🚀 빨라지고 점수 1.5배 · 🐢🐌 느리게 · 🗼 바닥 표시 · 🛟 풍덩 1번 막기 · 🐬 다음 점프 2배",   // ios: Views/Wave/WavePlayView.swift:74
    )
}

private val tnum = "tnum"

// ios: Views/Wave/WavePlayView.swift:77
@Composable
private fun WaveHud(e: WaveEngine, h: Hud, fx: WaveFX, frame: MutableLongState, dismiss: () -> Unit) {
    val ink = Theme.inkFixed; val teal = Theme.teal; val track = Theme.track
    Fg(ink) {
        // ios: Views/Wave/WavePlayView.swift:78,125,126
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ios: Views/Wave/WavePlayView.swift:79
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/Wave/WavePlayView.swift:80,81
                Box(Modifier.plainClickable(dismiss).padding(6.dp).semantics { contentDescription = "그만하기" }) { SymOr("xmark", 15f, ink) }
                // ios: Views/Wave/WavePlayView.swift:82
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    T(e.course.island.label, style = appFont(13f, FontWeight.Bold))   // ios: Views/Wave/WavePlayView.swift:83
                    T(e.course.level.label, Modifier.alpha(0.7f), style = appFont(11f))   // ios: Views/Wave/WavePlayView.swift:84
                }
                Spacer(Modifier.weight(1f))   // ios: Views/Wave/WavePlayView.swift:86
                T("${h.score}점", style = appFont(20f, FontWeight.ExtraBold).copy(fontFeatureSettings = tnum))   // ios: Views/Wave/WavePlayView.swift:87
                Spacer(Modifier.weight(1f))   // ios: Views/Wave/WavePlayView.swift:88
                // ios: Views/Wave/WavePlayView.swift:89,94
                Row(Modifier.semantics { contentDescription = "하트 ${h.hearts}개" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // ios: Views/Wave/WavePlayView.swift:90
                    for (i in 0 until WaveEngine.maxHearts) {
                        SymOr(if (i < h.hearts) "heart.fill" else "heart", 17f, Theme.up)   // ios: Views/Wave/WavePlayView.swift:91
                    }
                }
            }
            // ios: Views/Wave/WavePlayView.swift:96
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/Wave/WavePlayView.swift:97 (기본 ProgressView: 4pt 막대, 매 걸음 그리기만 다시)
                Box(Modifier.weight(1f).height(4.dp).drawBehind {
                    frame.longValue
                    val r = (e.timeLeft / e.duration).coerceIn(0.0, 1.0).toFloat(); val cr = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = cr); drawRoundRect(teal, size = Size(size.width * r, size.height), cornerRadius = cr)
                })
                T("${h.secs}초", style = appFont(12f, FontWeight.Bold).copy(fontFeatureSettings = tnum))   // ios: Views/Wave/WavePlayView.swift:98
                if (h.flash > 0) T("+${h.got}초", style = appFont(12f, FontWeight.ExtraBold), color = hex(0x2F9E44))   // ios: Views/Wave/WavePlayView.swift:99 timeFlash > 0
                if (h.streak >= 2) T("${h.streak}연속", style = appFont(12f, FontWeight.Bold), color = Theme.orange)   // ios: Views/Wave/WavePlayView.swift:100
                if (h.fever) T("피버 ×2", style = appFont(12f, FontWeight.ExtraBold), color = hex(0xD9480F))   // ios: Views/Wave/WavePlayView.swift:101
                if (h.dolphin) T("🐬")   // ios: Views/Wave/WavePlayView.swift:102
                if (h.vest) T("🛟")   // ios: Views/Wave/WavePlayView.swift:103
                if (h.boost > 0) T("🚀")   // ios: Views/Wave/WavePlayView.swift:104 boost > 0
                if (h.slow > 0) T("🐢")   // ios: Views/Wave/WavePlayView.swift:105 slow > 0
            }
            // ios: Views/Wave/WavePlayView.swift:107,116
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start)) {
                // ios: Views/Wave/WavePlayView.swift:108
                e.missions.forEachIndexed { k, x ->
                    val done = h.done[k]   // ios: Views/Wave/WavePlayView.swift:109
                    // ios: Views/Wave/WavePlayView.swift:110,111,112,113
                    T((if (done) "✓ " else "") + x.title,
                        Modifier.alpha(if (done) 1f else 0.8f).background(Color.White.copy(alpha = if (done) 0.9f else 0.55f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = appFont(11f, FontWeight.Bold))
                }
            }
            Spacer(Modifier.weight(1f))   // ios: Views/Wave/WavePlayView.swift:117
            // ios: Views/Wave/WavePlayView.swift:119,120,121,122 (누르는 것을 받지 않는다: 그냥 글자)
            h.hint?.let { riding -> T(if (riding) "꼭대기에서 톡! 내려요" else "바닥에서 톡! 타요", Modifier.background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp), style = appFont(13f, FontWeight.Bold)) }
        }
    }
}

// MARK: 한 판 결과

// ios: Views/Wave/WavePlayView.swift:198
class WaveResult(
    val course: WaveEngine.Course,   // ios: Views/Wave/WavePlayView.swift:200
    val score: Int,   // ios: Views/Wave/WavePlayView.swift:201
    val hearts: Int,   // ios: Views/Wave/WavePlayView.swift:202
    val rides: List<Ride>,   // ios: Views/Wave/WavePlayView.swift:203
    val why: WaveEngine.EndReason,   // ios: Views/Wave/WavePlayView.swift:204
) {
    data class Ride(val from: Int, val to: Int, val gain: Double, val splash: Boolean)   // ios: Views/Wave/WavePlayView.swift:199
    /** 판 미션 한 줄 (Swift 튜플 (title, done)) */
    data class MissionRow(val title: String, val done: Boolean)
    /** 판 미션 (이름, 깼나) */
    var missions: List<MissionRow> = emptyList()   // ios: Views/Wave/WavePlayView.swift:206
    /** 이번 판에 새로 열린 보드·모자 이름 */
    var unlocked: List<String> = emptyList()   // ios: Views/Wave/WavePlayView.swift:208

    /** 별: 점수 1점 이상 1개 · 300점 2개 · 600점 3개. 하트를 다 잃으면 1개까지 */
    // ios: Views/Wave/WavePlayView.swift:211
    val stars: Int get() {
        val s = if (score >= 600) 3 else if (score >= 300) 2 else if (score > 0) 1 else 0   // ios: Views/Wave/WavePlayView.swift:212
        return if (why == WaveEngine.EndReason.hearts) min(s, 1) else s   // ios: Views/Wave/WavePlayView.swift:213
    }
    // ios: Views/Wave/WavePlayView.swift:215
    val headline: String get() {
        if (rides.isEmpty()) return "한 번도 안 탔어요. 바닥에서 톡!"   // ios: Views/Wave/WavePlayView.swift:216
        val win = rides.count { it.gain > 0 }; val sp = rides.count { it.splash }   // ios: Views/Wave/WavePlayView.swift:217
        return "${rides.size}번 타서 ${win}번 벌었어요" + (if (sp > 0) " · 풍덩 ${sp}번" else "")   // ios: Views/Wave/WavePlayView.swift:218
    }
    /** "이 코스는 2024년 8월의 내 자산이었어요" */
    // ios: Views/Wave/WavePlayView.swift:221
    val dateLine: String get() {
        val p = course.startDate.split("-").filter { it.isNotEmpty() }   // ios: Views/Wave/WavePlayView.swift:222
        val mo = if (p.size >= 2) p[1].toIntOrNull() else null; if (mo == null) return ""   // ios: Views/Wave/WavePlayView.swift:223
        return "이 코스는 ${p[0]}년 ${mo}월의 내 자산이었어요"   // ios: Views/Wave/WavePlayView.swift:224
    }
}

// MARK: 연출 상태 (타이머 몇 개 + 점 몇십 개)

// ios: Views/Wave/WavePlayView.swift:230
class WaveFX {
    enum class Kind { spray, spark, coin }   // ios: Views/Wave/WavePlayView.swift:231
    class Dot(var x: Double, var y: Double, var vx: Double, var vy: Double, var life: Double, val kind: Kind)   // ios: Views/Wave/WavePlayView.swift:232
    class Pop(val text: String, var life: Double, val color: Color)   // ios: Views/Wave/WavePlayView.swift:233
    companion object { const val flipTime = 0.6; const val dizzyTime = 0.9 }   // ios: Views/Wave/WavePlayView.swift:234

    val dots = ArrayList<Dot>()   // ios: Views/Wave/WavePlayView.swift:236
    var pop: Pop? = null   // ios: Views/Wave/WavePlayView.swift:237
    var flip = 0.0; var dizzy = 0.0; var boarded = 0.0; var calm = 0.0   // ios: Views/Wave/WavePlayView.swift:238
    /** 시간 보너스: 타이머 옆 "+n초" (겹치면 더해 보여 준다) */
    var timeFlash = 0.0; var timeGot = 0.0   // ios: Views/Wave/WavePlayView.swift:240
    /** 공중제비 각도: turn(목표)을 빠르게 따라간다. 톡마다 한 바퀴씩 늘어난다 */
    var angle = 0.0; var turn = 0.0   // ios: Views/Wave/WavePlayView.swift:242
    var rising = false   // ios: Views/Wave/WavePlayView.swift:243
    private var sparkT = 0.0; private var sprayT = 0.0   // ios: Views/Wave/WavePlayView.swift:244

    // ios: Views/Wave/WavePlayView.swift:246
    fun pose(riding: Boolean): CharPose {
        if (dizzy > 0) return CharPose.dizzy   // ios: Views/Wave/WavePlayView.swift:247
        if (flip > 0) return CharPose.cheer   // ios: Views/Wave/WavePlayView.swift:248
        if (riding) return if (boarded > 0 || !rising) CharPose.crouch else CharPose.cheer   // ios: Views/Wave/WavePlayView.swift:249
        return if (calm > 0) CharPose.yawn else CharPose.none   // ios: Views/Wave/WavePlayView.swift:250
    }

    // ios: Views/Wave/WavePlayView.swift:253
    fun step(dt: Double, riding: Boolean, rising: Boolean) {
        timeFlash = max(0.0, timeFlash - dt); if (timeFlash == 0.0) timeGot = 0.0   // ios: Views/Wave/WavePlayView.swift:254
        flip = max(0.0, flip - dt); dizzy = max(0.0, dizzy - dt); boarded = max(0.0, boarded - dt); calm = max(0.0, calm - dt)   // ios: Views/Wave/WavePlayView.swift:255
        this.rising = rising   // ios: Views/Wave/WavePlayView.swift:256
        if (flip > 0) angle += (turn - angle) * min(1.0, dt * 14) else { angle = 0.0; turn = 0.0 }   // ios: Views/Wave/WavePlayView.swift:257
        pop?.let { p -> p.life -= dt; pop = if (p.life > 0) p else null }   // ios: Views/Wave/WavePlayView.swift:258
        // ios: Views/Wave/WavePlayView.swift:259
        for (d in dots) {
            d.x += d.vx * dt; d.y += d.vy * dt   // ios: Views/Wave/WavePlayView.swift:260
            if (d.kind != Kind.spark) d.vy += 520 * dt   // ios: Views/Wave/WavePlayView.swift:261
            d.life -= dt   // ios: Views/Wave/WavePlayView.swift:262
        }
        dots.removeAll { it.life <= 0 }   // ios: Views/Wave/WavePlayView.swift:264
        if (!riding) return   // ios: Views/Wave/WavePlayView.swift:265
        sparkT -= dt; sprayT -= dt   // ios: Views/Wave/WavePlayView.swift:266
        if (rising && sparkT <= 0) { sparkT = 0.08; add(Kind.spark, 1) }   // ios: Views/Wave/WavePlayView.swift:267
        if (sprayT <= 0) { sprayT = 0.14; add(Kind.spray, 1) }   // ios: Views/Wave/WavePlayView.swift:268
    }

    // ios: Views/Wave/WavePlayView.swift:271
    fun react(e: WaveEngine.Event) {
        // ios: Views/Wave/WavePlayView.swift:272
        when (e) {
            // ios: Views/Wave/WavePlayView.swift:273
            is WaveEngine.Event.board -> {
                boarded = 0.35; add(Kind.spray, 6)   // ios: Views/Wave/WavePlayView.swift:274
                if (e.perfect) pop = Pop("딱!", life = 0.9, color = hex(0xF06A4A))   // ios: Views/Wave/WavePlayView.swift:275
            }
            // ios: Views/Wave/WavePlayView.swift:276
            is WaveEngine.Event.leave -> {
                if (e.gain > 0) { flip = flipTime; angle = 0.0; turn = 2 * PI; add(Kind.coin, 5) }   // ios: Views/Wave/WavePlayView.swift:277
                pop = Pop(if (e.points >= 0) "+${e.points}" else "${e.points}", life = 0.9, color = if (e.points >= 0) hex(0xE0A800) else hex(0x1B5E96))   // ios: Views/Wave/WavePlayView.swift:278
            }
            // ios: Views/Wave/WavePlayView.swift:279
            is WaveEngine.Event.splash -> {
                dizzy = dizzyTime; add(Kind.spray, 12)   // ios: Views/Wave/WavePlayView.swift:280
                pop = Pop("풍덩!", life = 0.9, color = hex(0x1B5E96))   // ios: Views/Wave/WavePlayView.swift:281
            }
            is WaveEngine.Event.calm -> calm = 1.5   // ios: Views/Wave/WavePlayView.swift:282
            is WaveEngine.Event.streak -> pop = Pop("${e.n}연속!", life = 0.9, color = hex(0xE8862A))   // ios: Views/Wave/WavePlayView.swift:283
            // ios: Views/Wave/WavePlayView.swift:284
            is WaveEngine.Event.fever -> {
                if (e.on) { pop = Pop("피버! ×2", life = 1.2, color = hex(0xD9480F)); add(Kind.spark, 10) }   // ios: Views/Wave/WavePlayView.swift:285
            }
            is WaveEngine.Event.flip -> { turn += 2 * PI; add(Kind.spark, 3) }   // ios: Views/Wave/WavePlayView.swift:286
            // ios: Views/Wave/WavePlayView.swift:287
            is WaveEngine.Event.land -> {
                pop = Pop((if (e.combo >= 2) "콤보${e.combo} " else "${e.flips}바퀴 ") + "+${e.points}", life = 0.9, color = hex(0xE8862A))   // ios: Views/Wave/WavePlayView.swift:288
                add(Kind.spray, 6)   // ios: Views/Wave/WavePlayView.swift:289
            }
            is WaveEngine.Event.item -> { pop = Pop(e.item.icon + " " + e.item.label, life = 1.2, color = hex(0x0F7B8A)); add(Kind.spark, 8) }   // ios: Views/Wave/WavePlayView.swift:290
            is WaveEngine.Event.time -> { timeGot += e.s; timeFlash = 1.2 }   // ios: Views/Wave/WavePlayView.swift:291
            is WaveEngine.Event.saved -> pop = Pop("🛟 살았다!", life = 1.0, color = hex(0x0F7B8A))   // ios: Views/Wave/WavePlayView.swift:292
            is WaveEngine.Event.mission -> { pop = Pop("✓ " + e.m.title + " +50", life = 1.2, color = hex(0x2F9E44)); add(Kind.coin, 4) }   // ios: Views/Wave/WavePlayView.swift:293
            is WaveEngine.Event.end -> {}   // ios: Views/Wave/WavePlayView.swift:294
        }
    }

    /** 점 n개. 좌표는 발밑 기준 pt */
    // ios: Views/Wave/WavePlayView.swift:299
    private fun add(k: Kind, n: Int) {
        if (!(dots.size < 80)) return   // ios: Views/Wave/WavePlayView.swift:300
        // ios: Views/Wave/WavePlayView.swift:301
        for (i in 0 until n) {
            // ios: Views/Wave/WavePlayView.swift:302
            when (k) {
                Kind.spray -> dots.add(Dot(x = rnd(-16.0, 16.0), y = rnd(-2.0, 4.0), vx = rnd(-150.0, 40.0),   // ios: Views/Wave/WavePlayView.swift:303
                                           vy = rnd(-240.0, -120.0), life = 0.6, kind = k))   // ios: Views/Wave/WavePlayView.swift:304
                Kind.spark -> dots.add(Dot(x = rnd(-24.0, -8.0), y = rnd(-60.0, -10.0), vx = -140.0,   // ios: Views/Wave/WavePlayView.swift:305
                                           vy = rnd(-10.0, 10.0), life = 0.5, kind = k))   // ios: Views/Wave/WavePlayView.swift:306
                Kind.coin -> dots.add(Dot(x = rnd(-6.0, 6.0), y = -60.0, vx = rnd(-70.0, 70.0),   // ios: Views/Wave/WavePlayView.swift:307
                                          vy = rnd(-340.0, -220.0), life = 0.9, kind = k))   // ios: Views/Wave/WavePlayView.swift:308
            }
        }
    }

    private fun rnd(a: Double, b: Double): Double = Random.nextDouble(a, b)
}

// MARK: 그리기

// ios: Views/Wave/WavePlayView.swift:316
object WaveDraw {
    /** 한 거래일 = 화면 너비 1/9, 인물은 왼쪽 30%. 좌표는 pt */
    // ios: Views/Wave/WavePlayView.swift:318,319,320
    class Geo(val w: Float, val h: Float, val dx: Float, val rx: Float, val top: Float, val bot: Float, val lo: Double, val hi: Double, val p: Double) {
        fun x(i: Double): Float = rx + (i - p).toFloat() * dx   // ios: Views/Wave/WavePlayView.swift:321
        fun y(v: Double): Float = bot - ((v - lo) / max(hi - lo, 1.0)).toFloat() * (bot - top)   // ios: Views/Wave/WavePlayView.swift:322
    }

    // ios: Views/Wave/WavePlayView.swift:325,326,327
    fun sky(sunset: Boolean): Brush = Brush.verticalGradient(if (sunset) listOf(hex(0xFFB37A), hex(0xFFE7C2)) else listOf(hex(0xA9DDF3), hex(0xEAF7FB)))

    fun pos(e: WaveEngine): Double = min((e.course.count - 1).toDouble(), e.elapsed / WaveEngine.secondsPerDay)   // ios: Views/Wave/WavePlayView.swift:330

    /** 그린 곡선(가운데점 잇는 2차 곡선) 위 값. t = 거래일(소수) */
    // ios: Views/Wave/WavePlayView.swift:333
    fun value(e: WaveEngine, t: Double): Double {
        val v = e.course.values; val n = v.size   // ios: Views/Wave/WavePlayView.swift:334
        val i = min(max(floor(t + 0.5).toInt(), 0), n - 1)   // ios: Views/Wave/WavePlayView.swift:335
        val a = (v[max(i - 1, 0)] + v[i]) / 2; val b = (v[i] + v[min(i + 1, n - 1)]) / 2   // ios: Views/Wave/WavePlayView.swift:336
        val s = min(max(t - (i.toDouble() - 0.5), 0.0), 1.0)   // ios: Views/Wave/WavePlayView.swift:337
        return (1 - s) * (1 - s) * a + 2 * (1 - s) * s * v[i] + s * s * b   // ios: Views/Wave/WavePlayView.swift:338
    }
    fun rising(e: WaveEngine): Boolean { val p = pos(e); return value(e, p + 0.15) > value(e, p - 0.15) }   // ios: Views/Wave/WavePlayView.swift:340

    /** 글자는 pt 그대로 재고 (밀도 1), 그림 전체를 밀도만큼 키워 그린다 */
    private val unit = Density(1f, 1f)
    private fun DrawScope.textAt(tm: TextMeasurer, s: String, style: TextStyle, c: Offset, alpha: Float = 1f) {
        val l = tm.measure(s, style, density = unit)
        drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f), alpha = alpha)
    }

    // ios: Views/Wave/WavePlayView.swift:342
    fun scene(ctx: DrawScope, size: Size, e: WaveEngine, fx: WaveFX, me: MeChar, board: Int, hat: Int, tm: TextMeasurer) {
        val k = ctx.density
        ctx.drawContext.canvas.save(); ctx.drawContext.transform.scale(k, k, Offset.Zero)
        try { sceneIn(ctx, size, e, fx, me, board, hat, tm) } finally { ctx.drawContext.canvas.restore() }
    }

    private fun sceneIn(ctx: DrawScope, size: Size, e: WaveEngine, fx: WaveFX, me: MeChar, board: Int, hat: Int, tm: TextMeasurer) = with(ctx) {
        val v = e.course.values; val n = v.size   // ios: Views/Wave/WavePlayView.swift:343
        val g = Geo(w = size.width, h = size.height, dx = size.width / 9, rx = size.width * 0.3f,   // ios: Views/Wave/WavePlayView.swift:344
                    top = size.height * 0.34f, bot = size.height * 0.8f, lo = v.minOrNull() ?: 0.0, hi = v.maxOrNull() ?: 1.0, p = pos(e))   // ios: Views/Wave/WavePlayView.swift:345
        val sunset = e.fever   // ios: Views/Wave/WavePlayView.swift:346

        // 파도: 보이는 거래일만
        val i0 = max(0, (g.p - (g.rx / g.dx).toDouble()).toInt() - 1); val i1 = min(n - 1, (g.p + ((g.w - g.rx) / g.dx).toDouble()).toInt() + 2)   // ios: Views/Wave/WavePlayView.swift:349
        fun pt(i: Int): Offset = Offset(g.x(i.toDouble()), g.y(v[i]))   // ios: Views/Wave/WavePlayView.swift:350
        val endX = max(pt(i1).x, g.w + 10)   // ios: Views/Wave/WavePlayView.swift:359
        // 같은 선을 두 번 그리므로 (선 · 바다 윗변) 한 번 짠 순서를 두 Path 에 쓴다
        val trace = { p: Path ->
            p.moveTo(min(pt(i0).x, -10f), pt(i0).y)   // ios: Views/Wave/WavePlayView.swift:352
            p.lineTo(pt(i0).x, pt(i0).y)   // ios: Views/Wave/WavePlayView.swift:353
            // ios: Views/Wave/WavePlayView.swift:354
            for (i in i0 until i1) {
                val a = pt(i); val b = pt(i + 1)   // ios: Views/Wave/WavePlayView.swift:355
                p.quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)   // ios: Views/Wave/WavePlayView.swift:356
            }
            p.lineTo(pt(i1).x, pt(i1).y)   // ios: Views/Wave/WavePlayView.swift:358
            p.lineTo(endX, pt(i1).y)   // ios: Views/Wave/WavePlayView.swift:360
        }
        val line = Path().also(trace)   // ios: Views/Wave/WavePlayView.swift:351
        val sea = Path().also(trace)   // ios: Views/Wave/WavePlayView.swift:361
        sea.lineTo(endX, g.h); sea.lineTo(-10f, g.h); sea.close()   // ios: Views/Wave/WavePlayView.swift:362
        val water = if (sunset) listOf(hex(0xF28C6A), hex(0x8B4F9E)) else listOf(hex(0x4FB3E8), hex(0x1B5E96))   // ios: Views/Wave/WavePlayView.swift:363
        drawPath(sea, Brush.linearGradient(water, start = Offset(0f, g.top), end = Offset(0f, g.h)))   // ios: Views/Wave/WavePlayView.swift:364
        drawPath(line, Color.White.copy(alpha = 0.85f), style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))   // ios: Views/Wave/WavePlayView.swift:365

        // 탄 구간: 금빛 줄
        val b = e.boardedAt
        // ios: Views/Wave/WavePlayView.swift:368
        if (b != null && g.p > b.toDouble()) {
            val ride = Path()   // ios: Views/Wave/WavePlayView.swift:369
            ride.moveTo(g.x(b.toDouble()), g.y(value(e, b.toDouble())))   // ios: Views/Wave/WavePlayView.swift:370
            var t = b.toDouble()   // ios: Views/Wave/WavePlayView.swift:371
            while (t < g.p) { t = min(g.p, t + 0.25); ride.lineTo(g.x(t), g.y(value(e, t))) }   // ios: Views/Wave/WavePlayView.swift:372
            drawPath(ride, hex(0xFFD84D), style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))   // ios: Views/Wave/WavePlayView.swift:373
        }

        // 아이템: 파도 위에 둥실
        // ios: Views/Wave/WavePlayView.swift:377
        for ((d, itm) in e.items) {
            if (!(d >= i0 && d <= i1)) continue
            val c = Offset(g.x(d.toDouble()), g.y(v[d]) - 24 + sin(e.elapsed * 3 + d).toFloat() * 3)   // ios: Views/Wave/WavePlayView.swift:378
            textAt(tm, itm.icon, TextStyle(fontSize = 24.sp), c)   // ios: Views/Wave/WavePlayView.swift:379
        }

        // 등대: 앞으로 올 바닥에 작은 노란 깃발
        // ios: Views/Wave/WavePlayView.swift:383
        if (e.beacon > 0) {
            // ios: Views/Wave/WavePlayView.swift:384
            for (i in (e.day + 1) until max(e.day + 1, i1 + 1)) {
                if (!e.isBottom(i)) continue
                val p = pt(i)   // ios: Views/Wave/WavePlayView.swift:385
                val f = Path()   // ios: Views/Wave/WavePlayView.swift:386
                f.moveTo(p.x, p.y + 4); f.lineTo(p.x - 6, p.y + 14)   // ios: Views/Wave/WavePlayView.swift:387
                f.lineTo(p.x + 6, p.y + 14); f.close()   // ios: Views/Wave/WavePlayView.swift:388
                drawPath(f, hex(0xFFD84D))   // ios: Views/Wave/WavePlayView.swift:389
            }
        }

        // 인물
        var foot = Offset(g.rx, g.y(value(e, g.p)) - 6)   // ios: Views/Wave/WavePlayView.swift:394
        if (!e.riding && fx.dizzy <= 0) foot = foot.copy(y = foot.y + sin(e.elapsed * 4).toFloat() * 1.5f)   // ios: Views/Wave/WavePlayView.swift:395 둥실
        rider(ctx, foot, e, fx, me = me, board = board, hat = hat, g = g)   // ios: Views/Wave/WavePlayView.swift:396

        // 점 효과 · 글자
        // ios: Views/Wave/WavePlayView.swift:399
        for (d in fx.dots) {
            val c = Offset(foot.x + d.x.toFloat(), foot.y + d.y.toFloat()); val a = min(1.0, d.life * 2).toFloat()   // ios: Views/Wave/WavePlayView.swift:400
            // ios: Views/Wave/WavePlayView.swift:401
            when (d.kind) {
                WaveFX.Kind.spray ->   // ios: Views/Wave/WavePlayView.swift:402
                    drawOval(Color.White.copy(alpha = a), Offset(c.x - 2.5f, c.y - 2.5f), Size(5f, 5f))   // ios: Views/Wave/WavePlayView.swift:403
                // ios: Views/Wave/WavePlayView.swift:404
                WaveFX.Kind.spark -> {
                    val s = Path()   // ios: Views/Wave/WavePlayView.swift:405
                    s.moveTo(c.x, c.y - 3); s.lineTo(c.x + 3, c.y)   // ios: Views/Wave/WavePlayView.swift:406
                    s.lineTo(c.x, c.y + 3); s.lineTo(c.x - 3, c.y); s.close()   // ios: Views/Wave/WavePlayView.swift:407
                    drawPath(s, hex(0xFFD84D).copy(alpha = a))   // ios: Views/Wave/WavePlayView.swift:408
                }
                // ios: Views/Wave/WavePlayView.swift:409
                WaveFX.Kind.coin -> {
                    val tl = Offset(c.x - 4.5f, c.y - 4.5f); val r = Size(9f, 9f)   // ios: Views/Wave/WavePlayView.swift:410
                    drawOval(hex(0xFFD84D).copy(alpha = a), tl, r)   // ios: Views/Wave/WavePlayView.swift:411
                    drawOval(hex(0xE0A800).copy(alpha = a), tl, r, style = Stroke(1.5f))   // ios: Views/Wave/WavePlayView.swift:412
                }
            }
        }
        // ios: Views/Wave/WavePlayView.swift:415
        fx.pop?.let { p ->
            val op = min(1.0, p.life * 3).toFloat()   // ios: Views/Wave/WavePlayView.swift:416,417
            // ios: Views/Wave/WavePlayView.swift:418 (둥근 글꼴 없음: 기본 글꼴)
            textAt(tm, p.text, TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.color),
                Offset(foot.x, foot.y - 110 - (0.9 - p.life).toFloat() * 30), alpha = op)   // ios: Views/Wave/WavePlayView.swift:419
        }
    }

    /** 포즈 그림(MeMotion 이 이미 들고 있는 몇 장)을 Compose 그림으로 한 번만 감싼다 */
    private val bitmaps = HashMap<Bitmap, ImageBitmap>()

    /** 보드 + 포즈 그림 + 모자(연속 3번부터는 왕관). 버퍼 좌표 (x 0...23, 발 = 34줄)를 발밑 기준으로 옮긴다 */
    // ios: Views/Wave/WavePlayView.swift:424
    private fun rider(ctx: DrawScope, foot: Offset, e: WaveEngine, fx: WaveFX, me: MeChar, board: Int, hat: Int, g: Geo) {
        val dot = 2f; val pose = fx.pose(riding = e.riding)   // ios: Views/Wave/WavePlayView.swift:425
        val c = ctx.drawContext; c.canvas.save()   // ios: Views/Wave/WavePlayView.swift:426
        c.transform.translate(foot.x, foot.y)   // ios: Views/Wave/WavePlayView.swift:427
        if (fx.dizzy > 0) c.transform.translate(0f, sin((1 - fx.dizzy / WaveFX.dizzyTime) * PI).toFloat() * 22)   // ios: Views/Wave/WavePlayView.swift:428 풍덩: 잠겼다 올라옴
        // ios: Views/Wave/WavePlayView.swift:429
        if (fx.flip > 0) {
            val k = 1 - fx.flip / WaveFX.flipTime   // ios: Views/Wave/WavePlayView.swift:430
            c.transform.translate(0f, -sin(k * PI).toFloat() * 34 - 17 * dot)   // ios: Views/Wave/WavePlayView.swift:431
            c.transform.rotateRad(fx.angle.toFloat(), Offset.Zero)   // ios: Views/Wave/WavePlayView.swift:432
            c.transform.translate(0f, 17 * dot)   // ios: Views/Wave/WavePlayView.swift:433
        } else {   // ios: Views/Wave/WavePlayView.swift:434
            val y1 = g.y(value(e, g.p - 0.15)); val y2 = g.y(value(e, g.p + 0.15))   // ios: Views/Wave/WavePlayView.swift:435
            c.transform.rotateRad(max(-0.4, min(0.4, atan2((y2 - y1).toDouble(), (0.3 * g.dx)))).toFloat(), Offset.Zero)   // ios: Views/Wave/WavePlayView.swift:436
        }
        fun X(bx: Int): Float = (bx - 12) * dot   // ios: Views/Wave/WavePlayView.swift:438
        fun Y(by: Int): Float = (by - MeArt.H) * dot   // ios: Views/Wave/WavePlayView.swift:439
        // ios: Views/Wave/WavePlayView.swift:440
        fun cell(ch: Char, bx: Int, by: Int) {
            val rgb = SurfCloset.palette[ch] ?: return   // ios: Views/Wave/WavePlayView.swift:441
            ctx.drawRect(hex(rgb), Offset(X(bx), Y(by)), Size(dot * 2, dot * 2))   // ios: Views/Wave/WavePlayView.swift:442
        }
        // ios: Views/Wave/WavePlayView.swift:444
        MeMotion.image(me, CharScene(dir = CharDir.down, pose = pose))?.let { bm ->
            val img = synchronized(bitmaps) { bitmaps.getOrPut(bm) { bm.asImageBitmap() } }
            val iw = (dot * img.width).toInt(); val ih = (dot * img.height).toInt()   // ios: Views/Wave/WavePlayView.swift:445
            ctx.drawImage(img, dstOffset = IntOffset(X(0).toInt(), Y(MeArt.H).toInt() - ih), dstSize = IntSize(iw, ih), filterQuality = FilterQuality.None)   // ios: Views/Wave/WavePlayView.swift:446
        }
        val b = SurfCloset.boards[min(max(board, 0), SurfCloset.boards.size - 1)]   // ios: Views/Wave/WavePlayView.swift:448
        b.rows.forEachIndexed { r, row -> row.forEachIndexed { i, ch -> cell(ch, 2 * (b.x + i), MeArt.H + 2 * r) } }   // ios: Views/Wave/WavePlayView.swift:449
        for ((x, y, ch) in b.deco) cell(ch[0], 2 * x, MeArt.H + 2 * (y - 11))   // ios: Views/Wave/WavePlayView.swift:450
        val sink = pose.sink   // ios: Views/Wave/WavePlayView.swift:451
        // ios: Views/Wave/WavePlayView.swift:452
        if (e.streak >= 3) {
            listOf("y.y.y", "yyyyy").forEachIndexed { r, row -> row.forEachIndexed { i, ch -> cell(ch, 7 + 2 * i, -4 + 2 * r + sink) } }   // ios: Views/Wave/WavePlayView.swift:453
        } else {   // ios: Views/Wave/WavePlayView.swift:454
            val h = SurfCloset.hats[min(max(hat, 0), SurfCloset.hats.size - 1)]   // ios: Views/Wave/WavePlayView.swift:455
            h.rows.forEachIndexed { r, row -> row.forEachIndexed { i, ch -> cell(ch, 2 * (h.x + i), 2 * (h.y + r - 1) + sink) } }   // ios: Views/Wave/WavePlayView.swift:456
        }
        c.canvas.restore()
    }
}
