package com.naeilo.app.core.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.NJson
import com.naeilo.app.core.SwiftDate
import com.naeilo.app.core.game.GameSound.Kind
import com.naeilo.app.core.swFmt
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Lines
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.ShelterBG
import com.naeilo.app.data.backgrounds
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 판 깨기: 1000칸으로 하는 거꾸로 벽돌깨기. 채운 칸이 벽돌이고, 판(패들)은 위에, 공은 아래 벽돌로 떨어진다.
// 실제 칸은 바뀌지 않는다. 단계 = 『중첩된 현실』 쉼터 12곳(권마다 규칙 하나), 선수 = 기본 인물(+내 캐릭터, 스킬 하나),
// 아이템 = 쉼터에 돌아온 물건(모은 것만 떨어진다). 기록은 이 폰 안에만 저장한다 (Defaults.prefs game.v1, 개인 정보 없음).
// 판의 좌표: 가로 40칸 × 세로 46줄. 위 0줄 밖으로 공이 나가면 놓친 것, 21~45줄이 1000칸 (아래부터 채움, CellGrid 와 같은 배치).
//
// 안드로이드: 엔진은 화면·기기와 떨어져 있다 (같은 시드·같은 입력 → 같은 상태).
//  - 난수는 GameRng 하나로만 뽑고, Swift 표준 라이브러리(Int.random·Double.random·shuffled·randomElement·Bool.random)와 같은 셈으로 꺼낸다.
//    앱에서는 GameRng.System, 숫자 맞추기 J(JParityTest)는 SplitMix64 시드로 아이폰과 같은 판·같은 공 길을 낸다.
//  - 소리·진동은 GameFx (기본: GameSound 로 소리만. 화면은 ViewFx(view) 로 진동까지), 기울기는 GameMotion (SensorMotion(context)).
//  - 삼각함수는 StrictMath (기기마다 같은 값). 아이폰 libm 과는 끝자리만 다를 수 있어 숫자 맞추기에서 공 위치만 상대 1e-12 로 본다.

/** Swift RandomNumberGenerator 자리: next() 는 UInt64 비트 */
fun interface GameRng {
    fun next(): Long

    companion object {
        /** SystemRandomNumberGenerator 자리 (앱에서 쓰는 것) */
        val System = GameRng { kotlin.random.Random.Default.nextLong() }
    }
}

/** 64비트 부호 없는 곱의 윗자리 (multipliedFullWidth.high) */
private fun umulHi(x: Long, y: Long): Long {
    val x0 = x and 0xFFFFFFFFL; val x1 = x ushr 32
    val y0 = y and 0xFFFFFFFFL; val y1 = y ushr 32
    val p00 = x0 * y0; val p01 = x0 * y1; val p10 = x1 * y0; val p11 = x1 * y1
    val mid = (p00 ushr 32) + (p01 and 0xFFFFFFFFL) + (p10 and 0xFFFFFFFFL)
    return p11 + (p01 ushr 32) + (p10 ushr 32) + (mid ushr 32)
}

/** Swift next(upperBound:) (Lemire): 0 ..< upperBound (부호 없는 64비트) */
fun GameRng.nextBounded(upperBound: Long): Long {
    var random = next()
    var lo = random * upperBound
    var hi = umulHi(random, upperBound)
    if (java.lang.Long.compareUnsigned(lo, upperBound) < 0) {
        val t = java.lang.Long.remainderUnsigned(-upperBound, upperBound)
        while (java.lang.Long.compareUnsigned(lo, t) < 0) {
            random = next(); lo = random * upperBound; hi = umulHi(random, upperBound)
        }
    }
    return hi
}

/** Int.random(in: a..<b) */
fun GameRng.intIn(a: Int, b: Int): Int = (a.toLong() + nextBounded(b.toLong() - a.toLong())).toInt()

/** Int.random(in: a...b) */
fun GameRng.intInClosed(a: Int, b: Int): Int = (a.toLong() + nextBounded(b.toLong() - a.toLong() + 1)).toInt()

private const val HALF_ULP = 1.1102230246251565E-16   // Double.ulpOfOne / 2 = 2^-53

/** Double.random(in: a..<b) */
fun GameRng.doubleIn(a: Double, b: Double): Double {
    val delta = b - a
    while (true) {
        val rand = next() and ((1L shl 53) - 1)
        val v = delta * (rand.toDouble() * HALF_ULP) + a
        if (v != b) return v
    }
}

/** Double.random(in: a...b) */
fun GameRng.doubleInClosed(a: Double, b: Double): Double {
    val delta = b - a
    val rand = nextBounded((1L shl 53) + 1)
    if (rand == 1L shl 53) return b
    return delta * (rand.toDouble() * HALF_ULP) + a
}

/** Bool.random() */
fun GameRng.bool(): Boolean = ((next() ushr 17) and 1L) == 0L

/** shuffled() (Swift 의 Fisher–Yates 차례 그대로) */
fun <T> List<T>.swShuffled(rng: GameRng): MutableList<T> {
    val a = toMutableList()
    var amount = a.size
    var i = 0
    while (amount > 1) {
        val j = rng.intIn(0, amount)
        amount -= 1
        val tmp = a[i]; a[i] = a[i + j]; a[i + j] = tmp
        i += 1
    }
    return a
}

/** randomElement() (비었으면 난수를 쓰지 않고 null) */
fun <T> List<T>.swRandomElement(rng: GameRng): T? = if (isEmpty()) null else this[rng.intIn(0, size)]

/** Swift min·max (같으면 앞의 것: min 은 y < x ? y : x, max 는 y >= x ? y : x) */
private fun smin(x: Double, y: Double): Double = if (y < x) y else x
private fun smax(x: Double, y: Double): Double = if (y >= x) y else x

/** Swift .rounded() (반올림, .5 는 0 에서 먼 쪽) */
private fun swRound(x: Double): Double {
    val f = floor(x)
    val d = x - f
    return if (d > 0.5) f + 1 else if (d < 0.5) f else if (x >= 0) f + 1 else f
}

private fun sin(x: Double): Double = StrictMath.sin(x)
private fun cos(x: Double): Double = StrictMath.cos(x)
private fun atan2(y: Double, x: Double): Double = StrictMath.atan2(y, x)

/** 판 좌표의 네모 (CGRect 자리) */
data class GameRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val midX: Double get() = x + width / 2
    val midY: Double get() = y + height / 2
    val minY: Double get() = y
}

/** 진동 세기 (UIImpactFeedbackGenerator.FeedbackStyle) */
enum class Impact { light, medium, heavy }

/** 알림 진동 (UINotificationFeedbackGenerator.FeedbackType) */
enum class Notice { success, warning }

/** 소리·진동 자리. 화면(BrickGameView)이 진동을 붙인 것을 넘긴다 (ViewFx) */
interface GameFx {
    /** GameSound.shared.start() */
    fun start() { GameSound.shared.start() }
    /** GameSound.shared.play(k) */
    fun play(k: Kind) { GameSound.shared.play(k) }
    /** UIImpactFeedbackGenerator.prepare() */
    fun prepare(style: Impact) {}
    /** impactOccurred() · impactOccurred(intensity:) (intensity 1 = 기본) */
    fun impact(style: Impact, intensity: Double = 1.0) {}
    /** UINotificationFeedbackGenerator().notificationOccurred(_) */
    fun notify(type: Notice) {}

    companion object {
        /** 소리만 (진동 없음) */
        val Sound = object : GameFx {}
    }
}

/** 화면 진동: View.performHapticFeedback (권한·라이브러리 없이). 세기(intensity)는 안드로이드에 없어 무시한다 */
open class ViewFx(private val view: View) : GameFx {
    override fun impact(style: Impact, intensity: Double) {
        view.performHapticFeedback(when (style) {
            Impact.light -> HapticFeedbackConstants.CLOCK_TICK
            Impact.medium -> HapticFeedbackConstants.VIRTUAL_KEY
            Impact.heavy -> HapticFeedbackConstants.LONG_PRESS
        })
    }
    override fun notify(type: Notice) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) {
            if (type == Notice.success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else if (type == Notice.success) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS)
    }
}

/** 화면 방향 (UIInterfaceOrientation 중 쓰는 것) */
enum class Orientation { portrait, landscapeLeft, landscapeRight }

/** 중력 (CMDeviceMotion.gravity, 아이폰 단위·부호: g, 화면 오른쪽을 내리면 x+) */
data class Gravity(val x: Double, val y: Double)

/** CMMotionManager 자리 */
interface GameMotion {
    val isDeviceMotionAvailable: Boolean
    val isDeviceMotionActive: Boolean
    var deviceMotionUpdateInterval: Double
    fun startDeviceMotionUpdates()
    fun stopDeviceMotionUpdates()
    /** deviceMotion?.gravity (켜기 전·값이 오기 전에는 null) */
    val gravity: Gravity?
    /** 지금 화면 방향 (UIWindowScene.interfaceOrientation) */
    fun orientation(): Orientation

    /** 기울기 없음 (화면을 끌어서만) */
    object None : GameMotion {
        override val isDeviceMotionAvailable = false
        override val isDeviceMotionActive = false
        override var deviceMotionUpdateInterval = 0.0
        override fun startDeviceMotionUpdates() {}
        override fun stopDeviceMotionUpdates() {}
        override val gravity: Gravity? = null
        override fun orientation() = Orientation.portrait
    }
}

/** 기울기: 중력 센서 (없으면 가속도). 안드로이드 값(m/s², 반대 부호)을 아이폰 gravity(g) 로 바꾼다 */
class SensorMotion(context: Context) : GameMotion, SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    override val isDeviceMotionAvailable: Boolean get() = sensor != null
    override var isDeviceMotionActive = false
        private set
    override var deviceMotionUpdateInterval = 0.1
    @Volatile override var gravity: Gravity? = null
        private set
    override fun startDeviceMotionUpdates() {
        val s = sensor ?: return
        isDeviceMotionActive = sm.registerListener(this, s, (deviceMotionUpdateInterval * 1_000_000).toInt())
    }
    override fun stopDeviceMotionUpdates() { sm.unregisterListener(this); isDeviceMotionActive = false; gravity = null }
    override fun onSensorChanged(event: SensorEvent) {
        gravity = Gravity(-event.values[0] / SensorManager.GRAVITY_EARTH.toDouble(), -event.values[1] / SensorManager.GRAVITY_EARTH.toDouble())
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    @Suppress("DEPRECATION")
    override fun orientation(): Orientation = when (wm.defaultDisplay.rotation) {
        Surface.ROTATION_90 -> Orientation.landscapeRight
        Surface.ROTATION_270 -> Orientation.landscapeLeft
        else -> Orientation.portrait
    }
}

// ios: Data/BrickGame.swift:12,13
@Serializable
enum class GameLevel { easy, normal, hard;
    val id: String get() = name   // ios: Data/BrickGame.swift:14
    val rawValue: String get() = name
    val title: String get() = when (this) { easy -> "하수"; normal -> "중수"; hard -> "고수" }   // ios: Data/BrickGame.swift:15
    val speed: Double get() = when (this) { easy -> 21.0; normal -> 24.0; hard -> 27.0 }   // ios: Data/BrickGame.swift:16
    val paddle: Double get() = when (this) { easy -> 9.0; normal -> 8.0; hard -> 7.0 }   // ios: Data/BrickGame.swift:17
    val lives: Int get() = when (this) { easy -> 5; normal -> 4; hard -> 3 }   // ios: Data/BrickGame.swift:18
    /** 단계를 깨는 기준: 깰 수 있는 벽돌 중 이만큼 */
    val clear: Double get() = when (this) { easy -> 0.6; normal -> 0.75; hard -> 0.9 }   // ios: Data/BrickGame.swift:20
    val note: String get() = when (this) {   // ios: Data/BrickGame.swift:21,22
        easy -> "벽돌 750개 · 60% 깨면 다음 단계"   // ios: Data/BrickGame.swift:23
        normal -> "벽돌 800개 · 75% 깨면 다음 단계"   // ios: Data/BrickGame.swift:24
        hard -> "벽돌 850개 · 90% 깨면 다음 단계"   // ios: Data/BrickGame.swift:25
    }
    /** 한 번 맞히면 같이 깨지는 칸 (가운데 기준 (가로, 세로)) */
    val burst: List<Pair<Int, Int>> get() {   // ios: Data/BrickGame.swift:29
        val plus = listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)   // ios: Data/BrickGame.swift:30
        return when (this) {   // ios: Data/BrickGame.swift:31
            hard -> plus   // ios: Data/BrickGame.swift:32
            normal -> plus + listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)   // ios: Data/BrickGame.swift:33
            easy -> plus + listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1, 2 to 0, -2 to 0, 0 to 2, 0 to -2)   // ios: Data/BrickGame.swift:34
        }
    }
    /** 연습판 벽돌 수 (단계마다 10개씩 늘어난다) */
    val bricks: Int get() = when (this) { easy -> 750; normal -> 800; hard -> 850 }   // ios: Data/BrickGame.swift:38
    val easier: GameLevel? get() = when (this) { easy -> null; normal -> easy; hard -> normal }   // ios: Data/BrickGame.swift:39
}

/** 단계: 쉼터 배경 하나와 그 권의 규칙 */
// ios: Data/BrickGame.swift:43
data class GameStage(
    val n: Int,   // ios: Data/BrickGame.swift:44
    val rule: String,   // ios: Data/BrickGame.swift:45
    /** 시작 카드: 장면 한 줄 · 규칙 한 줄 (글줄이기 2026-10-18) */
    val scene: String = "", val short: String = "",   // ios: Data/BrickGame.swift:47
    val speedMul: Double = 1.0, val paddleMul: Double = 1.0, val extraLives: Int = 0, val items: Boolean = true,   // ios: Data/BrickGame.swift:48
    val mirror: Boolean = false,        // ios: Data/BrickGame.swift:49 (프리퀄: 거울에 공이 비쳐 보임, 그림만)
    val flicker: Boolean = false,       // ios: Data/BrickGame.swift:50 (1권: 형광등이 깜빡여 벽돌이 잠깐 안 보임, 출구 칸만 빛남)
    val gravity: Double = 0.0,          // ios: Data/BrickGame.swift:51 (+: 벽돌 쪽으로 2권 화성, −: 판 쪽으로 9권 화산 연기)
    val well: Boolean = false,          // ios: Data/BrickGame.swift:52 (3권: 가운데 원형 구조물이 공을 끌어당겨 휜다)
    val portal: Boolean = false,        // ios: Data/BrickGame.swift:53 (4권: 양쪽 벽이 입구, 반대편으로 나온다)
    val sway: Boolean = false,          // ios: Data/BrickGame.swift:54 (5권: 벽돌 줄이 물결처럼 흔들림)
    val dash: Boolean = false,          // ios: Data/BrickGame.swift:55 (7권: 줄표(—) 벽은 깨지지 않는다)
    val hard2: Double = 0.0,            // ios: Data/BrickGame.swift:56 (10권: 안 구운 그릇 벽돌, 세 번 쳐야 깨짐, 비율)
) {
    val id: Int get() = n   // ios: Data/BrickGame.swift:57
    /** 이 단계의 벽돌 수: 난이도 기본 + 단계마다 10개 (내 채운 칸이 먼저, 모자라면 위에 쌓는다) */
    fun bricks(lv: GameLevel): Int = min(1000, lv.bricks + 10 * n)   // ios: Data/BrickGame.swift:59

    /** 숨은 테트리스 블럭 수: 하수 3권·중수 2권·고수 1권부터 4개, 그 뒤 단계마다 2개씩 */
    fun tetrisCount(lv: GameLevel): Int {   // ios: Data/BrickGame.swift:64
        val start: Int = when (lv) { GameLevel.easy -> 3; GameLevel.normal -> 2; GameLevel.hard -> 1 }   // ios: Data/BrickGame.swift:65
        return if (n < start) 0 else 4 + 2 * (n - start)   // ios: Data/BrickGame.swift:66
    }
    val bg: ShelterBG get() = Shelter.backgrounds[n]   // ios: Data/BrickGame.swift:68

    companion object {
        const val specials = 10   // ios: Data/BrickGame.swift:60
        /** 두 번 맞아야 깨지는 벽돌 비율 (나머지는 한 번) */
        const val tough = 0.1   // ios: Data/BrickGame.swift:62

        val all: List<GameStage> = listOf(   // ios: Data/BrickGame.swift:70
        GameStage(n = 0, rule = "연습판. 거울 셋에 공이 비쳐 보여요. 공이 느리고 목숨이 둘 더 있어요.", scene = "거울 셋, 조금씩 다른 얼굴", short = "연습판 · 공 느림 · 목숨 +2", speedMul = 0.85, extraLives = 2, mirror = true),   // ios: Data/BrickGame.swift:71
        GameStage(n = 1, rule = "형광등이 가끔 깜빡여 벽돌이 안 보여요. 노랗게 빛나는 출구 칸은 보너스.", scene = "떨리는 형광등, 끝없는 복도", short = "형광등 깜빡임 · 노란 출구 칸 +100", flicker = true),   // ios: Data/BrickGame.swift:72
        GameStage(n = 2, rule = "중력이 약해요. 공이 느리고 벽돌 쪽으로 천천히 휘어요.", scene = "붉은 평원이 보이는 창", short = "약한 중력 · 공 느림 · 벽돌 쪽으로 휨", speedMul = 0.85, gravity = 7.0),   // ios: Data/BrickGame.swift:73
        GameStage(n = 3, rule = "가운데 원형 구조물이 지나가는 공을 끌어당겨요.", scene = "물 위 첨탑과 원형 구조물", short = "가운데 구조물이 공을 끌어당김", well = true),   // ios: Data/BrickGame.swift:74
        GameStage(n = 4, rule = "양쪽 벽이 입구예요. 공이 반대편 입구로 나와요.", scene = "두 입구 사이의 틈", short = "양쪽 벽이 입구 · 반대편으로 나옴", portal = true),   // ios: Data/BrickGame.swift:75
        GameStage(n = 5, rule = "벽돌 줄이 운하 물결처럼 천천히 흔들려요.", scene = "운하 건너 백 명의 마을", short = "벽돌 줄이 물결처럼 흔들림", sway = true),   // ios: Data/BrickGame.swift:76
        GameStage(n = 6, rule = "창이 좁아 판이 짧아요. 대신 공이 느려요.", scene = "손가락 몇 개만큼 연 창", short = "판 짧음 · 공 느림", speedMul = 0.8, paddleMul = 0.75),   // ios: Data/BrickGame.swift:77
        GameStage(n = 7, rule = "가운데 줄표(—)는 깨지지 않는 벽이에요.", scene = "빈 현수막과 줄표 하나", short = "줄표(—)는 안 깨지는 벽", dash = true),   // ios: Data/BrickGame.swift:78
        GameStage(n = 8, rule = "충전할 곳이 없어요. 물건이 떨어지지 않아요.", scene = "붉은 노을, 충전할 곳 없음", short = "물건 안 떨어짐", items = false),   // ios: Data/BrickGame.swift:79
        GameStage(n = 9, rule = "화산 연기가 공을 판 쪽으로 밀어 올려요.", scene = "계단식 논과 연기 오르는 화산", short = "연기가 공을 판 쪽으로 밂", gravity = -6.0),   // ios: Data/BrickGame.swift:80
        GameStage(n = 10, rule = "안 구운 그릇 벽돌은 세 번 쳐야 깨져요.", scene = "물레와 안 구운 그릇", short = "안 구운 그릇 · 세 번 쳐야 깨짐", hard2 = 0.3),   // ios: Data/BrickGame.swift:81
        GameStage(n = 11, rule = "모든 규칙이 조금씩 섞인 자유판.", scene = "도시 불빛이 보이는 방", short = "모든 규칙 조금씩 섞임", flicker = true, portal = true, sway = true, hard2 = 0.15),   // ios: Data/BrickGame.swift:82
        )
    }
}

/** 선수: 기본 인물 다섯 (+내 캐릭터는 평정 지수 유형의 스킬) */
object GamePlayer {   // ios: Data/BrickGame.swift:87
    fun skill(id: String): String =   // ios: Data/BrickGame.swift:88
        when (id) {   // ios: Data/BrickGame.swift:89
            "sio" -> "안전망 · 판 뒤 노란 줄이 공을 한 번 받아 줘요"   // ios: Data/BrickGame.swift:90
            "seonbae" -> "되묻기 · 놓친 공을 판마다 한 번 되감아요"   // ios: Data/BrickGame.swift:91
            "ir" -> "앞서 달림 · 공이 빠르고 점수 1.5배"   // ios: Data/BrickGame.swift:92
            "sua" -> "자라기 · 깰수록 판이 조금씩 길어져요"   // ios: Data/BrickGame.swift:93
            else -> "원칙 · 판이 길고 공 속도가 늘 같아요"   // ios: Data/BrickGame.swift:94
        }
    /** 물건을 먹었을 때 뜨는 짧은 말 (예: 현관 등 · 10초 공 길 보임) */
    fun itemShort(id: String): String =   // ios: Data/BrickGame.swift:98
        when (id) {   // ios: Data/BrickGame.swift:99
            "barley_tea" -> "10초 공 느림"   // ios: Data/BrickGame.swift:100
            "porch_light" -> "10초 공 길 보임"   // ios: Data/BrickGame.swift:101
            "wall_clock" -> "3초 공 멈춤"   // ios: Data/BrickGame.swift:102
            "hair_tie" -> "15초 판 길게"   // ios: Data/BrickGame.swift:103
            "table_chair" -> "공 하나 더"   // ios: Data/BrickGame.swift:104
            "asym_bowl" -> "10초 벽돌 많은 쪽으로"   // ios: Data/BrickGame.swift:105
            "unfired_bowl" -> "보호막 1번"   // ios: Data/BrickGame.swift:106
            "jujube_seed" -> "10초 점수 2배"   // ios: Data/BrickGame.swift:107
            "elder_bead" -> "5초 벽돌 뚫기"   // ios: Data/BrickGame.swift:108
            "rice_seeds" -> "공 셋으로"   // ios: Data/BrickGame.swift:109
            else -> item(id)   // ios: Data/BrickGame.swift:110
        }
    /** 쉼터 물건이 아이템일 때 하는 일 */
    fun item(id: String): String =   // ios: Data/BrickGame.swift:114
        when (id) {   // ios: Data/BrickGame.swift:115
            "barley_tea" -> "공이 10초 느려져요"   // ios: Data/BrickGame.swift:116
            "porch_light" -> "10초 동안 공이 갈 길이 보여요"   // ios: Data/BrickGame.swift:117
            "wall_clock" -> "공이 3초 멈춰요"   // ios: Data/BrickGame.swift:118
            "hair_tie" -> "판이 15초 길어져요"   // ios: Data/BrickGame.swift:119
            "table_chair" -> "공이 하나 더"   // ios: Data/BrickGame.swift:120
            "asym_bowl" -> "10초 동안 판에 맞은 공이 벽돌이 많은 쪽으로 꺾여요"   // ios: Data/BrickGame.swift:121
            "unfired_bowl" -> "위쪽에 한 번 막아 주는 보호막"   // ios: Data/BrickGame.swift:122
            "jujube_seed" -> "10초 동안 점수 2배"   // ios: Data/BrickGame.swift:123
            "elder_bead" -> "5초 동안 공이 벽돌을 뚫고 지나가요"   // ios: Data/BrickGame.swift:124
            "rice_seeds" -> "공이 셋으로 갈라져요"   // ios: Data/BrickGame.swift:125
            else -> ""   // ios: Data/BrickGame.swift:126
        }
}

/** 게임 대사: 상황 × 인물 줄 모음 + 쉼터 물건 대사(lines_ko.json 의 item.<물건>.<인물>)를 섞고,
 *  앞말·뒷말을 따로 골라 이어 붙여 같은 문장이 자주 반복되지 않게 한다. 한 바퀴 돌기 전에는 같은 줄을 다시 쓰지 않는다 */
object GameLines {   // ios: Data/BrickGame.swift:133
    enum class Event { start, item, lose, clear, over, half, rewind, last; val rawValue: String get() = name }   // ios: Data/BrickGame.swift:134

    private val pool: Map<String, Map<String, List<String>>> = mapOf(   // ios: Data/BrickGame.swift:136
        "seri" to mapOf(   // ios: Data/BrickGame.swift:137
            "start" to listOf("관찰을 시작합니다.", "{stage}. 기록을 켭니다.", "준비됐어요. 공은 아래로 가요.", "한 박자 두고, 시작해요.", "벽돌 {left}칸. 확인했어요."),   // ios: Data/BrickGame.swift:138
            "lose" to listOf("공 하나를 놓쳤어요. 남은 공 {n}개.", "기록했어요. 다음 공이 있어요.", "거기까지만요. 다시 받으면 돼요.", "위치가 조금 빨랐어요."),   // ios: Data/BrickGame.swift:139
            "clear" to listOf("기준 칸에 닿았어요.", "{stage}, 통과로 기록할게요.", "{score}점. 사실 그대로예요.", "다음 방이 열렸어요."),   // ios: Data/BrickGame.swift:140
            "over" to listOf("여기까지예요. 다시 하면 돼요.", "공이 다 떨어졌어요. 기록은 남았어요.", "오늘은 {broken}칸까지였어요."),   // ios: Data/BrickGame.swift:141
            "half" to listOf("절반을 넘었어요.", "기준의 반쯤 왔어요."),   // ios: Data/BrickGame.swift:142
            "rewind" to listOf("한 번은 되돌려요."),   // ios: Data/BrickGame.swift:143
        ),
        "sio" to mapOf(   // ios: Data/BrickGame.swift:145
            "start" to listOf("서두르지 않아도 돼.", "놓쳐도 괜찮아, 한 번은 받아 줄게.", "{stage}이야. 천천히 가 보자.", "기다리는 것도 실력이야.", "준비되면 눌러."),   // ios: Data/BrickGame.swift:146
            "lose" to listOf("괜찮아. 한 박자 쉬고 다시.", "놓쳐도 돼. 공은 {n}개 더 있어.", "조금 기다렸다 받아 봐.", "급했지? 숨 한 번."),   // ios: Data/BrickGame.swift:147
            "clear" to listOf("기다린 만큼 깼네.", "{stage}, 지나왔다.", "{score}점. 좋은 기다림이었어.", "다음 방으로 가자."),   // ios: Data/BrickGame.swift:148
            "over" to listOf("오늘은 여기까지. 내일 또 하자.", "괜찮아, 다시 기다리면 돼.", "{broken}칸이나 깼어."),   // ios: Data/BrickGame.swift:149
            "half" to listOf("반 왔어. 그대로.", "절반이야. 서두르지 마."),   // ios: Data/BrickGame.swift:150
            "rewind" to listOf("한 번 더 기다려 줄게."),   // ios: Data/BrickGame.swift:151
        ),
        "seonbae" to mapOf(   // ios: Data/BrickGame.swift:153
            "start" to listOf("어디로 보낼 건데?", "{stage}. 이 방, 어떻게 깰 생각이야?", "한 번은 되돌릴 수 있어. 알지?", "그게 정말 네 계획이야?", "시작해 봐. 질문은 나중에."),   // ios: Data/BrickGame.swift:154
            "lose" to listOf("왜 놓쳤는지 알겠어?", "방금 그건 판 탓이야, 네 탓이야?", "남은 공 {n}개. 다음엔 어디서 받을래?", "너무 끝으로 갔지?"),   // ios: Data/BrickGame.swift:155
            "clear" to listOf("그게 정말 네 실력이야? 맞네.", "{stage} 통과. 다음 질문으로 가자.", "{score}점이면 됐어. 다음은?", "어떻게 깼는지 말할 수 있어?"),   // ios: Data/BrickGame.swift:156
            "over" to listOf("다시 하면 뭘 바꿀 거야?", "끝났네. 그래서 배운 건?", "{broken}칸. 나쁘지 않은데, 다음은?"),   // ios: Data/BrickGame.swift:157
            "half" to listOf("반 왔어. 여기서 뭘 바꿀래?", "절반. 계속 이대로?"),   // ios: Data/BrickGame.swift:158
            "rewind" to listOf("한 번 되돌려 줄게. 이번엔 어디로?"),   // ios: Data/BrickGame.swift:159
        ),
        "ir" to mapOf(   // ios: Data/BrickGame.swift:161
            "start" to listOf("빨리 가자!", "{stage}! 바로 시작!", "점수는 더 줄게. 달려!", "벽돌 {left}칸, 다 깨 버리자!", "준비됐지? 간다!"),   // ios: Data/BrickGame.swift:162
            "lose" to listOf("앗, 너무 빨랐나!", "괜찮아, 공 {n}개 남았어!", "다시! 바로 다시!", "아깝다! 한 번 더!"),   // ios: Data/BrickGame.swift:163
            "clear" to listOf("벌써 끝! 다음 판!", "{stage} 통과! 빠르다!", "{score}점! 더 갈 수 있어!", "다음 방 열렸어, 가자!"),   // ios: Data/BrickGame.swift:164
            "over" to listOf("너무 달렸나. 한 번 더!", "끝! 근데 다시 하고 싶다!", "{broken}칸! 다음엔 더!"),   // ios: Data/BrickGame.swift:165
            "half" to listOf("반 왔어! 속도 올려!", "절반! 계속 계속!"),   // ios: Data/BrickGame.swift:166
            "rewind" to listOf("한 번 더 달려!"),   // ios: Data/BrickGame.swift:167
        ),
        "sua" to mapOf(   // ios: Data/BrickGame.swift:169
            "start" to listOf("깰수록 판이 자라요. 천천히요.", "{stage}이에요. 같이 가요.", "조금씩 하면 돼요.", "오늘은 어디까지 자랄까요?", "준비되면 눌러 주세요."),   // ios: Data/BrickGame.swift:170
            "lose" to listOf("괜찮아요. 다시 자라면 돼요.", "공이 {n}개 남았어요.", "조금 아쉬웠어요. 다음엔 더 잘될 거예요.", "천천히, 판을 가운데로요."),   // ios: Data/BrickGame.swift:171
            "clear" to listOf("조금씩 자라서 여기까지 왔어요.", "{stage}, 지나왔어요.", "{score}점이에요. 많이 컸어요.", "다음 방이 열렸어요."),   // ios: Data/BrickGame.swift:172
            "over" to listOf("다음엔 판이 더 일찍 자랄 거예요.", "오늘은 {broken}칸 자랐어요.", "쉬었다가 다시 해요."),   // ios: Data/BrickGame.swift:173
            "half" to listOf("절반 왔어요. 판도 자랐어요.", "반쯤 왔어요. 잘하고 있어요."),   // ios: Data/BrickGame.swift:174
            "rewind" to listOf("한 번 더 해 봐요."),   // ios: Data/BrickGame.swift:175
        ),
        "me" to mapOf(   // ios: Data/BrickGame.swift:177
            "start" to listOf("내 판이야. 가 보자.", "{stage}. 시작!", "벽돌 {left}칸, 해 보자.", "준비됐다."),   // ios: Data/BrickGame.swift:178
            "lose" to listOf("놓쳤다. 공 {n}개 남았어.", "다시 받자.", "아깝다."),   // ios: Data/BrickGame.swift:179
            "clear" to listOf("해냈다.", "{stage} 통과!", "{score}점!"),   // ios: Data/BrickGame.swift:180
            "over" to listOf("다시 해 보자.", "{broken}칸까지 왔어."),   // ios: Data/BrickGame.swift:181
            "half" to listOf("반 왔다."),   // ios: Data/BrickGame.swift:182
            "rewind" to listOf("한 번 더."),   // ios: Data/BrickGame.swift:183
        ),
    )
    /** 옆에서 끼어드는 다른 인물의 한마디 (상황별) */
    private val guest: Map<String, Map<String, List<String>>> = mapOf(   // ios: Data/BrickGame.swift:187
        "seri" to mapOf("last" to listOf("마지막 공이에요. 가운데를 지켜요.", "남은 공 하나. 관찰하고 받아요."), "half" to listOf("절반 지났어요. 기록 중이에요."),   // ios: Data/BrickGame.swift:188
                 "clear" to listOf("통과 확인했어요.", "저도 봤어요. 깔끔했어요."), "over" to listOf("기록은 남았어요. 다음에 이어 가요."), "item" to listOf("그 물건, 쉼터에서 본 거예요.")),   // ios: Data/BrickGame.swift:189
        "sio" to mapOf("last" to listOf("마지막이야. 서두르지 말고.", "하나 남았어. 기다렸다 받아."), "half" to listOf("반 왔네. 잘하고 있어."),   // ios: Data/BrickGame.swift:190
                "clear" to listOf("봤어? 기다린 보람 있지.", "멋졌어."), "over" to listOf("괜찮아. 다음엔 내가 옆에 있을게."), "item" to listOf("오, 그거 쓸모 있지.")),   // ios: Data/BrickGame.swift:191
        "seonbae" to mapOf("last" to listOf("마지막 공이야. 어디서 받을 거야?", "하나 남았는데, 계획 있어?"), "half" to listOf("반이야. 이대로 갈 거야?"),   // ios: Data/BrickGame.swift:192
                    "clear" to listOf("깼네. 다음 방은 쉬울 것 같아?", "나쁘지 않았어."), "over" to listOf("뭘 바꾸면 될지 알겠지?"), "item" to listOf("그거 언제 쓸지 생각했어?")),   // ios: Data/BrickGame.swift:193
        "ir" to mapOf("last" to listOf("마지막! 할 수 있어!", "하나 남았어, 집중!"), "half" to listOf("반 왔다! 더 빨리!"),   // ios: Data/BrickGame.swift:194
               "clear" to listOf("와, 빨랐다!", "최고! 다음 판 가자!"), "over" to listOf("한 번 더 하자, 한 번만!"), "item" to listOf("좋은 거 먹었다!")),   // ios: Data/BrickGame.swift:195
        "sua" to mapOf("last" to listOf("마지막 공이에요. 천천히요.", "하나 남았어요. 응원할게요."), "half" to listOf("절반이에요. 많이 자랐어요."),   // ios: Data/BrickGame.swift:196
                "clear" to listOf("축하해요!", "같이 와서 좋았어요."), "over" to listOf("쉬었다가 같이 다시 해요."), "item" to listOf("그 물건, 반가워요.")),   // ios: Data/BrickGame.swift:197
    )
    fun guestLine(e: Event, who: String, rng: GameRng = GameRng.System): String? {   // ios: Data/BrickGame.swift:199
        if (who.startsWith("s:")) return sampleLines[who.drop(2)]?.swRandomElement(rng)   // ios: Data/BrickGame.swift:200
        return guest[who]?.get(e.rawValue)?.swRandomElement(rng)   // ios: Data/BrickGame.swift:201
    }

    /** 마네킹 샘플(『중첩된 현실』 보조 인물)이 그 권의 방에서 끼어든다: 단계 → 인물 이름 (MeSample.all) */
    val stageSamples: Map<Int, List<String>> = mapOf(   // ios: Data/BrickGame.swift:205
        0 to listOf("미용사"), 1 to listOf("현실의 시오", "1권 선배"), 2 to listOf("니아", "묶은 머리 시오"), 4 to listOf("관리국의 수아"),   // ios: Data/BrickGame.swift:206
        6 to listOf("온유"), 7 to listOf("활동가"), 8 to listOf("담당관"), 9 to listOf("아내", "루"), 10 to listOf("아내", "루"), 11 to listOf("외전 세리"),   // ios: Data/BrickGame.swift:207
    )
    private val sampleLines: Map<String, List<String>> = mapOf(   // ios: Data/BrickGame.swift:209
        "미용사" to listOf("선택하지 않은 벽돌은 모두 잘라낼게요.", "가위 소리처럼, 사각. 잠시 멈춤. 사각.", "거울 셋 중 어느 쪽 공이 진짜일까요?"),   // ios: Data/BrickGame.swift:210
        "현실의 시오" to listOf("복도 끝에 출구가 있어. 노란 줄 하나.", "여기선 오래 버티는 게 이기는 거야.", "맨발로도 걸었어. 공 하나쯤이야."),   // ios: Data/BrickGame.swift:211
        "1권 선배" to listOf("그 방향, 정말 네가 고른 거야?", "출구는 늘 마지막에 보이지.", "질문 하나. 왜 거기서 받았어?"),   // ios: Data/BrickGame.swift:212
        "니아" to listOf("지구에 보낼 녹음에 이것도 넣을게요.", "여기선 공도 천천히 떨어져요.", "제2돔에서 응원하고 있어요."),   // ios: Data/BrickGame.swift:213
        "묶은 머리 시오" to listOf("고무줄 세 번. 다시 묶고 가자.", "0.1초 늦어도 괜찮아."),   // ios: Data/BrickGame.swift:214
        "관리국의 수아" to listOf("또 올게요. 약속은 아니에요.", "입구는 둘이에요. 공은 어느 쪽으로 나올까요?", "기록해 둘게요. 하급 연구원이라서요."),   // ios: Data/BrickGame.swift:215
        "온유" to listOf("창문은 손가락 몇 개만큼만요.", "좁아도 바람은 들어와요.", "천천히 해도 돼요. 여긴 그런 곳이에요."),   // ios: Data/BrickGame.swift:216
        "활동가" to listOf("잘린 문장도 끝내 붙일 수 있어요.", "줄표 하나는 남겨 둬요. 그건 깨지지 않아요.", "멀리서도 보여요. 계속해요."),   // ios: Data/BrickGame.swift:217
        "담당관" to listOf("솔직히 저도 당혹스럽습니다.", "충전할 곳은 없습니다. 있는 걸로 가시죠.", "보고서에는 잘했다고 쓰겠습니다."),   // ios: Data/BrickGame.swift:218
        "아내" to listOf("밥 먹고 해요.", "안 구운 그릇은 두 번 만져야 해요.", "묻지 않을게요. 계속해요."),   // ios: Data/BrickGame.swift:219
        "루" to listOf("꾸러미 배달 왔어요. 이건 응원.", "배송 유닛 XX-2214, 지켜보는 중.", "열여섯 살쯤인데, 저도 해 보고 싶어요."),   // ios: Data/BrickGame.swift:220
        "외전 세리" to listOf("화면 속에서 보고 있어요.", "프로필 사진처럼, 그대로 있어요.", "거기까지만요. 아니, 조금 더요."),   // ios: Data/BrickGame.swift:221
    )

    internal val bag: HashMap<String, MutableList<Int>> = HashMap()   // ios: Data/BrickGame.swift:224

    /** Lines.pick 자리 (숫자 맞추기 J 는 대사 파일 없이 null 로 바꿔 돌린다) */
    internal var linePick: (String, Int) -> String? = { key, salt -> Lines.pick(key, salt = salt) }

    /** who 의 그 상황 대사 한 줄. vars 로 {stage} {n} {score} {broken} {left} 를 채운다 */
    fun pick(e: Event, who: String, item: String? = null, vars: Map<String, String> = emptyMap(), rng: GameRng = GameRng.System): String {   // ios: Data/BrickGame.swift:227
        if (e == Event.item && item != null) { val id = item   // ios: Data/BrickGame.swift:228
            val name = Shelter.items.firstOrNull { it.id == id }?.name ?: id   // ios: Data/BrickGame.swift:229
            // 쉼터 대사(인물별 100줄)를 쓰고, 없으면 물건 이름과 효과
            // Data/BrickGame.swift:231 (검사 예외: 보간 속 따옴표) Lines.pick("item.<id>.<인물>", salt: Int.random(in: 1...9))
            linePick("item.$id.${if (who == "me") "seri" else who}", rng.intInClosed(1, 9))?.let { l -> return l }
            return "$name! ${GamePlayer.item(id)}."   // ios: Data/BrickGame.swift:232
        }
        val ev = if (e == Event.last) "lose" else e.rawValue   // ios: Data/BrickGame.swift:234
        val list = pool[who]?.get(ev) ?: pool["seri"]?.get(ev) ?: listOf("")   // ios: Data/BrickGame.swift:235
        val key = "$who.${e.rawValue}"   // ios: Data/BrickGame.swift:236
        if (bag[key]?.isEmpty() ?: true) bag[key] = list.indices.toList().swShuffled(rng)   // ios: Data/BrickGame.swift:237
        val i = bag[key]!!.let { it.removeAt(it.lastIndex) }   // ios: Data/BrickGame.swift:238
        var line = list[i % list.size]   // ios: Data/BrickGame.swift:239
        for ((k, v) in vars) line = line.replace("{$k}", v)   // ios: Data/BrickGame.swift:240
        return line   // ios: Data/BrickGame.swift:241
    }
}

/** 이 폰 안에만 남는 기록 (Defaults.prefs "game.v1", 아이폰 UserDefaults 와 같은 JSON 꼴).
 *  아이폰은 앱을 지웠다 깔아도 남게 키체인에 한 벌 더 둔다. 안드로이드는 Auto Backup(allowBackup)이 prefs 를 되살린다 */
@Serializable
data class GameRecord(   // ios: Data/BrickGame.swift:246
    var cleared: Map<String, Set<Int>> = emptyMap(),   // ios: Data/BrickGame.swift:247 (난이도별 깬 단계)
    var best: Map<String, Int> = emptyMap(),           // ios: Data/BrickGame.swift:248 ("난이도.단계" → 최고 점수)
    var losses: Map<String, Int> = emptyMap(),         // ios: Data/BrickGame.swift:249 ("난이도.단계" → 연속으로 진 횟수, 깨면 0)
    var level: GameLevel = GameLevel.normal,   // ios: Data/BrickGame.swift:250
    var tilt: Boolean = true,   // ios: Data/BrickGame.swift:251
    var player: String = "seri",   // ios: Data/BrickGame.swift:252
    var plays: Map<String, List<Play>>? = null,   // ios: Data/BrickGame.swift:253 ("난이도.단계" → 이 폰의 높은 점수 10판)
) {
    /** 내 기록 순위 한 줄: 점수·고른 인물·날짜. 서버로 보내지 않는다 */
    @Serializable
    data class Play(val score: Int, val p: String, @Serializable(with = SwiftDate::class) val at: Instant)   // ios: Data/BrickGame.swift:256

    companion object {
        private const val key = "game.v1"   // ios: Data/BrickGame.swift:258
        fun load(): GameRecord {   // ios: Data/BrickGame.swift:261
            Defaults.prefs.getString(key, null)?.let { d -> runCatching { NJson.decodeFromString(serializer(), d) }.getOrNull()?.let { r -> return r } }   // ios: Data/BrickGame.swift:262
            return GameRecord()
        }
    }
    fun save() {   // ios: Data/BrickGame.swift:270
        val d = runCatching { NJson.encodeToString(serializer(), this) }.getOrNull() ?: return   // ios: Data/BrickGame.swift:271
        Defaults.prefs.edit().putString(key, d).apply()   // ios: Data/BrickGame.swift:272
    }

    fun open(n: Int, lv: GameLevel): Boolean = n == 0 || (cleared[lv.rawValue] ?: emptySet()).contains(n - 1)   // ios: Data/BrickGame.swift:279
    fun bestOf(n: Int, lv: GameLevel): Int? = best["${lv.rawValue}.$n"]   // ios: Data/BrickGame.swift:280
    fun total(lv: GameLevel): Int = GameStage.all.fold(0) { a, s -> a + (bestOf(s.n, lv) ?: 0) }   // ios: Data/BrickGame.swift:281
    fun lossCount(n: Int, lv: GameLevel): Int = losses["${lv.rawValue}.$n"] ?: 0   // ios: Data/BrickGame.swift:282
    fun top(n: Int, lv: GameLevel): List<Play> = plays?.get("${lv.rawValue}.$n") ?: emptyList()   // ios: Data/BrickGame.swift:283

    /** 한 판 끝: 새 최고 점수면 true. 값 꼴(struct)이라 화면은 copy() 한 뒤 바꿔 넣는다 */
    fun finish(n: Int, lv: GameLevel, score: Int, won: Boolean, player: String = "seri"): Boolean {   // ios: Data/BrickGame.swift:286,287
        val k = "${lv.rawValue}.$n"   // ios: Data/BrickGame.swift:288
        if (score > 0) {   // ios: Data/BrickGame.swift:289
            val list = (plays?.get(k) ?: emptyList()).toMutableList()   // ios: Data/BrickGame.swift:290
            list.add(Play(score = score, p = player, at = Instant.now()))   // ios: Data/BrickGame.swift:291
            list.sortWith { a, b -> b.score.compareTo(a.score) }   // ios: Data/BrickGame.swift:292
            plays = (plays ?: emptyMap()) + mapOf(k to list.take(10))   // ios: Data/BrickGame.swift:293
        }
        if (won) { cleared = cleared + (lv.rawValue to ((cleared[lv.rawValue] ?: emptySet()) + n)); losses = losses + (k to 0) } else losses = losses + (k to (losses[k] ?: 0) + 1)   // ios: Data/BrickGame.swift:295
        val isBest = score > (best[k] ?: 0)   // ios: Data/BrickGame.swift:296
        if (isBest) best = best + (k to score)   // ios: Data/BrickGame.swift:297
        return isBest   // ios: Data/BrickGame.swift:298
    }
}

// ios: Data/BrickGame.swift:302,303
class BrickGame(
    stage: GameStage, level: GameLevel, player: String, skill: String, items: List<String>, tilt: Boolean, assist: Boolean,   // ios: Data/BrickGame.swift:386
    filled: Int, floor: Int, yesterday: Int, chain: Int = 0, lives: Int? = null,   // ios: Data/BrickGame.swift:387
    /** 소리·진동 (화면이 ViewFx(view) 를 넘긴다) */
    fx: GameFx = GameFx.Sound,
    /** 기울기 (화면이 SensorMotion(context) 를 넘긴다) */
    motion: GameMotion = GameMotion.None,
    /** 난수 (숫자 맞추기는 시드 있는 것) */
    rng: GameRng = GameRng.System,
    /** DispatchQueue.main.async 자리 */
    post: (() -> Unit) -> Unit = { r -> Handler(Looper.getMainLooper()).post(r); Unit },
) {
    private val fx = fx
    private val rng = rng
    private val post = post

    enum class Phase { ready, playing, paused, cleared, over }   // ios: Data/BrickGame.swift:304
    data class Ball(var x: Double, var y: Double, var vx: Double, var vy: Double, var stuck: Boolean = true, var off: Double = 0.0, var until: Double = 0.0, var spawned: Boolean = false)   // ios: Data/BrickGame.swift:305
    data class Drop(val id: String, var x: Double, var y: Double)   // ios: Data/BrickGame.swift:306
    /** 고전 벽돌깨기의 특수 블럭: 폭탄(둘레 터짐), 가로·세로 한 줄, 공 셋, 선물(물건 또는 점수) */
    enum class Special { bomb, row, col, multi, gift }   // ios: Data/BrickGame.swift:308
    data class Blast(val x: Double, val y: Double, val kind: Special, val t0: Double)   // ios: Data/BrickGame.swift:309
    /** 숨은 테트리스 블럭: 두 번 맞는 벽돌인 척 숨어 있다가 한 칸이 깨지면 드러나고, 칸 수(4)만큼 더 맞아야 없어진다 */
    data class Big(val cells: List<Int>, val shape: Int, var hp: Int, var revealed: Boolean = false)   // ios: Data/BrickGame.swift:311

    companion object {
        /** 테트리스 일곱 조각 (I O T S Z J L), 칸 (가로, 세로) */
        val tetrominoes: List<List<Pair<Int, Int>>> = listOf(   // ios: Data/BrickGame.swift:313
            listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),   // ios: Data/BrickGame.swift:314
            listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),   // ios: Data/BrickGame.swift:315
            listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),   // ios: Data/BrickGame.swift:316
        )

        const val W = 40.0; const val H = 46.0; const val PY = 5.0; const val PH = 0.8; const val top = 21.0; const val r = 0.45   // ios: Data/BrickGame.swift:319
        const val netY = 2.4   // ios: Data/BrickGame.swift:350
    }

    // 화면은 매 프레임 tick 을 부르며 다시 그린다. 지켜보는 값은 phase 뿐
    var phase by mutableStateOf(Phase.ready)   // ios: Data/BrickGame.swift:322

    val id: java.util.UUID = java.util.UUID.randomUUID()   // ios: Data/BrickGame.swift:324
    val stage: GameStage = stage   // ios: Data/BrickGame.swift:325,388
    val level: GameLevel = level   // ios: Data/BrickGame.swift:326
    val player: String = player       // ios: Data/BrickGame.swift:327 (그림에 쓰는 인물, me 포함)
    val skill: String = skill        // ios: Data/BrickGame.swift:328 (스킬을 정하는 인물, 내 캐릭터는 평정 지수 유형)
    val items: List<String> = if (stage.items) items else emptyList()   // ios: Data/BrickGame.swift:329,389
    val assist: Boolean = assist   // ios: Data/BrickGame.swift:330
    var tilt: Boolean = tilt   // ios: Data/BrickGame.swift:331,390

    var hp = IntArray(1000) { 0 }     // ios: Data/BrickGame.swift:333 (0 빈칸, -1 깨지지 않는 벽, 1·2 남은 횟수)
    var kind = IntArray(1000) { 0 }  // ios: Data/BrickGame.swift:334 (1 바닥에도 있던 칸, 2 회복한 칸, 3 오늘 늘어난 칸, 4 연습 칸, 5 단계가 더 쌓은 칸)
    var special = arrayOfNulls<Special>(1000)   // ios: Data/BrickGame.swift:335
    var revealed = BooleanArray(1000)   // ios: Data/BrickGame.swift:336 (특수 블럭은 한 번 맞아야 보인다)
    var group = IntArray(1000) { -1 }          // ios: Data/BrickGame.swift:337 (큰 숨은 블럭 번호)
    var bigs: MutableList<Big> = mutableListOf()   // ios: Data/BrickGame.swift:338
    private var touched: MutableSet<Int> = mutableSetOf()                     // ios: Data/BrickGame.swift:339 (한 번 맞을 때 큰 블럭은 한 번만 깎는다)
    var blasts: MutableList<Blast> = mutableListOf()          // ios: Data/BrickGame.swift:340 (터지는 그림, 잠깐)
    var stacked = 0                   // ios: Data/BrickGame.swift:341 (이 단계에서 더 쌓은 벽돌)
    var combo = 0                     // ios: Data/BrickGame.swift:342 (판에 다시 닿기 전까지 깬 벽돌)
    private var comboSaid = 0   // ios: Data/BrickGame.swift:343
    var exitCell = -1   // ios: Data/BrickGame.swift:344
    var balls: MutableList<Ball> = mutableListOf()   // ios: Data/BrickGame.swift:345
    var drops: MutableList<Drop> = mutableListOf()   // ios: Data/BrickGame.swift:346
    var effects: HashMap<String, Double> = HashMap()   // ios: Data/BrickGame.swift:347 (이름 → 끝나는 시각)
    var shield = false   // ios: Data/BrickGame.swift:348
    var net = 0                       // ios: Data/BrickGame.swift:349 (시오 안전망: 판 뒤 노란 줄이 놓친 공을 받아 주는 남은 횟수)
    /** 이어하기: 앞 단계를 깨고 바로 넘어온 횟수. 단계마다 점수 +10% */
    val chain: Int = chain   // ios: Data/BrickGame.swift:352,391
    val scoreMul: Double get() = 1 + 0.1 * chain.toDouble()   // ios: Data/BrickGame.swift:353
    /** 연속 3단계를 깰 때마다 (3·6·9…번째) 생명 +1 */
    val lifeBonus: Boolean get() = chain > 0 && chain % 3 == 0   // ios: Data/BrickGame.swift:355
    var rewindUsed = false   // ios: Data/BrickGame.swift:356
    var paddleX = W / 2   // ios: Data/BrickGame.swift:357
    var t = 0.0   // ios: Data/BrickGame.swift:358
    var score = 0   // ios: Data/BrickGame.swift:359
    var lives: Int = lives ?: (level.lives + stage.extraLives)   // ios: Data/BrickGame.swift:360,393 (어느 단계에서 시작해도 난이도 기본 생명, 이어하면 남은 생명 그대로)
    var broken = 0   // ios: Data/BrickGame.swift:361
    var total = 0   // ios: Data/BrickGame.swift:362
    var toast: Pair<String, Double>? = null   // ios: Data/BrickGame.swift:363
    var speech = ""                  // ios: Data/BrickGame.swift:364 (위쪽 인물 말풍선)
    var mood: GameLines.Event = GameLines.Event.start   // ios: Data/BrickGame.swift:365 (지금 말하는 상황, 동작 그림을 고른다)
    var moodAt = 0.0   // ios: Data/BrickGame.swift:366
    var speechUntil = 0.0   // ios: Data/BrickGame.swift:367
    private var halfSaid = false   // ios: Data/BrickGame.swift:368
    var friends: List<String> = emptyList()       // ios: Data/BrickGame.swift:369 (끼어들 수 있는 다른 인물, 열린 인물)
    var guest = ""                   // ios: Data/BrickGame.swift:370 (오른쪽에 나온 인물)
    var guestLine = ""   // ios: Data/BrickGame.swift:371
    var guestUntil = 0.0   // ios: Data/BrickGame.swift:372
    var newBest = false   // ios: Data/BrickGame.swift:373
    var picked: HashMap<String, Int> = HashMap()   // ios: Data/BrickGame.swift:374 (이번 판에 먹은 물건 → 횟수)

    private var lastTick: Double? = null   // ios: Data/BrickGame.swift:376 (Date 자리: 초)
    private var stopped = false   // ios: Data/BrickGame.swift:377
    private val motion: GameMotion = motion   // ios: Data/BrickGame.swift:378
    private val tap = Impact.light   // ios: Data/BrickGame.swift:379
    private var lastTap = 0.0   // ios: Data/BrickGame.swift:380
    private var axisSign: Pair<Boolean, Double> = Pair(true, 1.0)   // ios: Data/BrickGame.swift:381 (가로 화면이면 y축, 부호)
    private var axisCheck = -1.0   // ios: Data/BrickGame.swift:382

    val target: Int get() = max(1, ceil(total.toDouble() * level.clear).toInt())   // ios: Data/BrickGame.swift:384

    init {
        if (lives != null && chain > 0 && chain % 3 == 0) this.lives += 1   // ios: Data/BrickGame.swift:394
        this.net = if (skill == "sio") 1 else 0   // ios: Data/BrickGame.swift:395
        // 벽돌 수는 난이도·단계로 정한다. 내 채운 칸이 먼저 (아래부터), 모자라면 그 위에 더 쌓는다
        val n = stage.bricks(level); val f = max(0, min(n, filled))   // ios: Data/BrickGame.swift:397
        for (i in 0 until n) {   // ios: Data/BrickGame.swift:398
            val roll = rng.doubleIn(0.0, 1.0)   // ios: Data/BrickGame.swift:399 (대부분 한 번, 일부만 두 번, 10권 그릇은 세 번)
            hp[i] = if (roll < stage.hard2) 3 else if (roll < stage.hard2 + GameStage.tough) 2 else 1   // ios: Data/BrickGame.swift:400
            kind[i] = if (i >= f) 5 else if (i < floor) 1 else if (yesterday < f && i >= yesterday) 3 else 2   // ios: Data/BrickGame.swift:401
        }
        stacked = n - f   // ios: Data/BrickGame.swift:403
        if (stage.dash) { for (c in 14..25) hp[12 * 40 + c] = -1 }   // ios: Data/BrickGame.swift:404
        // 숨기는 곳: 맨 위 3번째 줄부터 바닥까지
        val topRow = ((0 until 1000).lastOrNull { hp[it] > 0 } ?: 0) / 40   // ios: Data/BrickGame.swift:406
        val hidden = { i: Int -> hp[i] > 0 && i / 40 <= topRow - 2 && group[i] < 0 && special[i] == null }   // ios: Data/BrickGame.swift:407
        // 숨은 테트리스 블럭 (안에는 특수 블럭이 없다)
        var tries = 0   // ios: Data/BrickGame.swift:409
        while (bigs.size < stage.tetrisCount(level) && tries < 3000) {   // ios: Data/BrickGame.swift:410
            tries += 1   // ios: Data/BrickGame.swift:411
            val shape = rng.intIn(0, tetrominoes.size)   // ios: Data/BrickGame.swift:412
            var pts = tetrominoes[shape]   // ios: Data/BrickGame.swift:413
            for (k in 0 until rng.intIn(0, 4)) pts = pts.map { Pair(it.second, -it.first) }   // ios: Data/BrickGame.swift:414 (돌리기)
            val mx = pts.minOf { it.first }; val my = pts.minOf { it.second }   // ios: Data/BrickGame.swift:415
            pts = pts.map { Pair(it.first - mx, it.second - my) }   // ios: Data/BrickGame.swift:416
            val w = pts.maxOf { it.first } + 1; val h = pts.maxOf { it.second } + 1   // ios: Data/BrickGame.swift:417
            if (!(topRow - 1 - h >= 0)) continue   // ios: Data/BrickGame.swift:418
            val c0 = rng.intInClosed(0, 40 - w); val r0 = rng.intInClosed(0, topRow - 1 - h)   // ios: Data/BrickGame.swift:419
            val cells = pts.map { (r0 + it.second) * 40 + c0 + it.first }   // ios: Data/BrickGame.swift:420
            if (!cells.all(hidden)) continue   // ios: Data/BrickGame.swift:421
            for (c in cells) { group[c] = bigs.size; hp[c] = 2 }   // ios: Data/BrickGame.swift:422
            bigs.add(Big(cells = cells, shape = shape, hp = cells.size))   // ios: Data/BrickGame.swift:423
        }
        // 특수 블럭 10개를 숨긴다 (폭탄이 가장 많다)
        val pool: List<Special> = listOf(Special.bomb, Special.bomb, Special.bomb, Special.row, Special.col, Special.multi, Special.gift)   // ios: Data/BrickGame.swift:426
        for (i in (0 until 1000).filter(hidden).swShuffled(rng).take(GameStage.specials)) special[i] = pool.swRandomElement(rng)   // ios: Data/BrickGame.swift:427
        if (stage.flicker) exitCell = (0 until 1000).lastOrNull { hp[it] > 0 && group[it] < 0 } ?: -1   // ios: Data/BrickGame.swift:428
        total = hp.count { it > 0 }   // ios: Data/BrickGame.swift:429
        balls = mutableListOf(Ball(x = W / 2, y = PY + PH + BrickGame.r, vx = 0.0, vy = 0.0))   // ios: Data/BrickGame.swift:430
        talk(GameLines.Event.start)   // ios: Data/BrickGame.swift:431
    }

    /** deinit 자리: 화면이 사라질 때 부른다 */
    fun dispose() { motion.stopDeviceMotionUpdates() }   // ios: Data/BrickGame.swift:434

    // MARK: 조작

    fun begin() {   // ios: Data/BrickGame.swift:438
        if (tilt && motion.isDeviceMotionAvailable) {   // ios: Data/BrickGame.swift:439
            motion.deviceMotionUpdateInterval = 1.0 / 60   // ios: Data/BrickGame.swift:440
            motion.startDeviceMotionUpdates()   // ios: Data/BrickGame.swift:441
        }
        fx.prepare(tap)   // ios: Data/BrickGame.swift:443
        fx.start()   // ios: Data/BrickGame.swift:444
    }
    fun end() { stopped = true; motion.stopDeviceMotionUpdates() }   // ios: Data/BrickGame.swift:446

    /** 화면이 그릴 때마다 (now: 초, 프레임 시각). 여기서는 지켜보는 값(phase)을 바로 바꾸지 않는다 */
    fun tick(now: Double) {   // ios: Data/BrickGame.swift:449
        val last = lastTick; lastTick = now   // ios: Data/BrickGame.swift:450 (defer: step 은 lastTick 을 읽지 않는다)
        if (last == null || stopped) return   // ios: Data/BrickGame.swift:451
        val dt = smin(1.0 / 20, smax(0.0, now - last))   // ios: Data/BrickGame.swift:452
        if (dt > 0) step(dt)   // ios: Data/BrickGame.swift:453
    }
    /** 판이 끝남: 그리는 중이라 다음 차례에 phase 를 바꾼다 */
    private fun finish(p: Phase) {   // ios: Data/BrickGame.swift:456
        if (stopped) return   // ios: Data/BrickGame.swift:457
        end()   // ios: Data/BrickGame.swift:458
        post { phase = p }   // ios: Data/BrickGame.swift:459
    }

    /** 시작 카드의 "눌러서 시작" */
    fun startPlay() {   // ios: Data/BrickGame.swift:463
        if (phase != Phase.ready) return   // ios: Data/BrickGame.swift:464
        phase = Phase.playing   // ios: Data/BrickGame.swift:465
        fx.play(if (lifeBonus) Kind.item else Kind.start)   // ios: Data/BrickGame.swift:466
        if (lifeBonus) say("생명 +1 · 연속 ${chain}단계")   // ios: Data/BrickGame.swift:467
        launch()   // ios: Data/BrickGame.swift:468
    }
    /** 화면을 누르면 붙은 공을 보낸다 (시작은 시작 카드 버튼으로만) */
    fun launch() {   // ios: Data/BrickGame.swift:471
        if (phase != Phase.playing) return   // ios: Data/BrickGame.swift:472
        val sp = speed   // ios: Data/BrickGame.swift:473
        for (i in balls.indices) if (balls[i].stuck) send(balls[i], sp)   // ios: Data/BrickGame.swift:474
    }
    fun pause() { if (phase == Phase.playing || phase == Phase.ready) phase = Phase.paused }   // ios: Data/BrickGame.swift:476
    fun resume() { if (phase == Phase.paused) phase = Phase.playing }   // ios: Data/BrickGame.swift:477

    /** 손가락으로 끌 때 (판 좌표) */
    fun drag(x: Double) { if (!tilt || !motion.isDeviceMotionActive) paddleX = clampPaddle(x) }   // ios: Data/BrickGame.swift:480

    // MARK: 값

    val paddleW: Double get() {   // ios: Data/BrickGame.swift:484
        var w = level.paddle * stage.paddleMul   // ios: Data/BrickGame.swift:485
        if (skill == "seri") w *= 1.3   // ios: Data/BrickGame.swift:486
        if (skill == "sua") w *= 1 + smin(0.5, (broken / 10).toDouble() * 0.02)   // ios: Data/BrickGame.swift:487
        if (on("wide")) w *= 1.4   // ios: Data/BrickGame.swift:488
        return smin(w, W * 0.6)   // ios: Data/BrickGame.swift:489
    }
    val speed: Double get() {   // ios: Data/BrickGame.swift:491
        var s = level.speed * stage.speedMul   // ios: Data/BrickGame.swift:492
        if (skill == "ir") s *= 1.2   // ios: Data/BrickGame.swift:493
        if (skill != "seri") s *= smin(1.2, 1 + t / 600)   // ios: Data/BrickGame.swift:494 (오래 버티면 조금씩 빨라진다, 세리는 그대로)
        if (assist) s *= 0.85   // ios: Data/BrickGame.swift:495
        if (on("slow")) s *= 0.7   // ios: Data/BrickGame.swift:496
        if (on("fast")) s *= 1.15   // ios: Data/BrickGame.swift:497
        return s   // ios: Data/BrickGame.swift:498
    }
    fun on(e: String): Boolean = (effects[e] ?: 0.0) > t   // ios: Data/BrickGame.swift:500
    val activeEffects: List<String> get() = effects.filter { it.value > t }.map { it.key }.sorted()   // ios: Data/BrickGame.swift:501
    /** 1권 형광등: 7초마다 1초쯤 어두워진다 (벽돌은 흐리게만, 완전히 사라지지 않음) */
    val dark: Boolean get() = stage.flicker && t % 7.0 > 6   // ios: Data/BrickGame.swift:503

    fun sway(row: Int): Double = if (stage.sway) sin(t * 1.2 + row.toDouble() * 0.5) * 0.8 else 0.0   // ios: Data/BrickGame.swift:505

    /** 판 좌표의 칸 번호 (1000칸 번호: 아래 줄부터 0) */
    fun cell(x: Double, y: Double): Int? {   // ios: Data/BrickGame.swift:508
        if (!(y >= top && y < H)) return null   // ios: Data/BrickGame.swift:509
        val row = 24 - (y - top).toInt()   // ios: Data/BrickGame.swift:510
        val xx = x - sway(row)   // ios: Data/BrickGame.swift:511
        if (!(xx >= 0 && xx < W)) return null   // ios: Data/BrickGame.swift:512
        return row * 40 + xx.toInt()   // ios: Data/BrickGame.swift:513
    }
    /** 칸 번호 → 판에서의 네모 */
    fun rect(i: Int): GameRect {   // ios: Data/BrickGame.swift:516
        val row = i / 40   // ios: Data/BrickGame.swift:517
        return GameRect(x = (i % 40).toDouble() + sway(row), y = top + (24 - row).toDouble(), width = 1.0, height = 1.0)   // ios: Data/BrickGame.swift:518
    }

    private fun clampPaddle(x: Double): Double {   // ios: Data/BrickGame.swift:521
        val h = paddleW / 2   // ios: Data/BrickGame.swift:522
        return smin(smax(x, h), W - h)   // ios: Data/BrickGame.swift:523
    }

    // MARK: 한 걸음

    private fun step(dt: Double) {   // ios: Data/BrickGame.swift:528
        if (!(phase == Phase.playing || phase == Phase.ready)) return   // ios: Data/BrickGame.swift:529
        if (phase == Phase.playing) t += dt   // ios: Data/BrickGame.swift:530
        movePaddle(dt)   // ios: Data/BrickGame.swift:531
        if (phase == Phase.ready) {   // ios: Data/BrickGame.swift:532
            for (i in balls.indices) if (balls[i].stuck) balls[i].x = paddleX + balls[i].off   // ios: Data/BrickGame.swift:533
            return   // ios: Data/BrickGame.swift:534
        }
        toast?.let { ts -> if (ts.second < t) toast = null }   // ios: Data/BrickGame.swift:536
        val sp = speed; val frozen = on("clock")   // ios: Data/BrickGame.swift:537
        val alive = mutableListOf<Ball>()   // ios: Data/BrickGame.swift:538
        for (b0 in balls.toList()) { val b = b0.copy()   // ios: Data/BrickGame.swift:539 (값 꼴: 복사본으로 움직인다)
            if (b.stuck) {   // ios: Data/BrickGame.swift:540
                b.x = smin(smax(paddleX + b.off, BrickGame.r), W - BrickGame.r)   // ios: Data/BrickGame.swift:541
                b.y = PY + PH + BrickGame.r   // ios: Data/BrickGame.swift:542
                if (t >= b.until) send(b, sp)   // ios: Data/BrickGame.swift:543
                alive.add(b); continue   // ios: Data/BrickGame.swift:544
            }
            if (frozen) { alive.add(b); continue }   // ios: Data/BrickGame.swift:546
            if (stage.gravity != 0.0) b.vy += stage.gravity * dt   // ios: Data/BrickGame.swift:547
            if (stage.well) {   // ios: Data/BrickGame.swift:548
                val dx = W / 2 - b.x; val dy = 13 - b.y; val d2 = smax(dx * dx + dy * dy, 6.0); val d = sqrt(d2)   // ios: Data/BrickGame.swift:549
                val a = 180 / d2   // ios: Data/BrickGame.swift:550
                b.vx += dx / d * a * dt; b.vy += dy / d * a * dt   // ios: Data/BrickGame.swift:551
            }
            normalize(b, sp)   // ios: Data/BrickGame.swift:553
            val n = max(1, ceil(sp * dt / 0.3).toInt()); val sdt = dt / n.toDouble()   // ios: Data/BrickGame.swift:554
            var lost = false   // ios: Data/BrickGame.swift:555
            for (k in 0 until n) { if (!move(b, sdt, sp)) { lost = true; break } }   // ios: Data/BrickGame.swift:556
            if (!lost) alive.add(b)   // ios: Data/BrickGame.swift:557
        }
        // 이번 걸음에 특수 블럭이 만든 공 (balls 에 바로 붙은 것)
        balls = (alive + balls.filter { it.spawned }.map { it.copy(spawned = false) }).toMutableList()   // ios: Data/BrickGame.swift:560
        blasts.removeAll { t - it.t0 > 0.45 }   // ios: Data/BrickGame.swift:561
        moveDrops(dt)   // ios: Data/BrickGame.swift:562
        if (broken >= target) { win(); return }   // ios: Data/BrickGame.swift:563
        if (!halfSaid && broken * 2 >= target) { halfSaid = true; talk(GameLines.Event.half) }   // ios: Data/BrickGame.swift:564
        if (balls.isEmpty()) loseBall()   // ios: Data/BrickGame.swift:565
    }

    private fun movePaddle(dt: Double) {   // ios: Data/BrickGame.swift:568
        val g = if (tilt) motion.gravity else null
        if (!tilt || g == null) { paddleX = clampPaddle(paddleX); return }   // ios: Data/BrickGame.swift:569
        if (t - axisCheck > 0.5 || axisCheck < 0) { axisCheck = t; axisSign = axis() }   // ios: Data/BrickGame.swift:570
        val a = if (axisSign.first) g.y * axisSign.second else g.x   // ios: Data/BrickGame.swift:571
        // 좌우로 약 24도 기울이면 끝까지
        val goal = W / 2 + smax(-1.0, smin(1.0, a / 0.4)) * W / 2   // ios: Data/BrickGame.swift:573
        paddleX = clampPaddle(paddleX + (goal - paddleX) * smin(1.0, dt * 14))   // ios: Data/BrickGame.swift:574
    }

    /** 화면 오른쪽이 기기의 어느 축인지 (세로: +x, 가로: ±y) */
    private fun axis(): Pair<Boolean, Double> {   // ios: Data/BrickGame.swift:578
        val o = motion.orientation()   // ios: Data/BrickGame.swift:579
        return when (o) {   // ios: Data/BrickGame.swift:580
            Orientation.landscapeRight -> Pair(true, -1.0)   // ios: Data/BrickGame.swift:581
            Orientation.landscapeLeft -> Pair(true, 1.0)   // ios: Data/BrickGame.swift:582
            else -> Pair(false, 1.0)   // ios: Data/BrickGame.swift:583
        }
    }

    private fun normalize(b: Ball, sp: Double) {   // ios: Data/BrickGame.swift:587
        val v = sqrt(b.vx * b.vx + b.vy * b.vy)   // ios: Data/BrickGame.swift:588
        if (v < 0.001) { b.vx = 0.0; b.vy = sp; return }   // ios: Data/BrickGame.swift:589
        b.vx *= sp / v; b.vy *= sp / v   // ios: Data/BrickGame.swift:590
        // 옆으로만 오가지 않게
        val minY = sp * 0.28   // ios: Data/BrickGame.swift:592
        if (abs(b.vy) < minY) {   // ios: Data/BrickGame.swift:593
            b.vy = if (b.vy < 0) -minY else minY   // ios: Data/BrickGame.swift:594
            b.vx = (if (b.vx < 0) -1 else 1) * sqrt(sp * sp - minY * minY)   // ios: Data/BrickGame.swift:595
        }
    }

    private fun send(b: Ball, sp: Double) {   // ios: Data/BrickGame.swift:599
        val off = smax(-1.0, smin(1.0, b.off / (paddleW / 2)))   // ios: Data/BrickGame.swift:600
        val a = off * PI / 3.5 + rng.doubleInClosed(-0.08, 0.08)   // ios: Data/BrickGame.swift:601
        b.stuck = false; b.vx = sp * sin(a); b.vy = sp * cos(a)   // ios: Data/BrickGame.swift:602
    }

    /** false = 위로 놓침 */
    private fun move(b: Ball, dt: Double, sp: Double): Boolean {   // ios: Data/BrickGame.swift:606
        val pierce = on("pierce")   // ios: Data/BrickGame.swift:607
        // 가로
        var nx = b.x + b.vx * dt   // ios: Data/BrickGame.swift:609
        if (stage.portal) {   // ios: Data/BrickGame.swift:610
            if (nx < 0) nx += W else if (nx >= W) nx -= W   // ios: Data/BrickGame.swift:611
        } else if (nx < BrickGame.r) { nx = BrickGame.r; b.vx = abs(b.vx) } else if (nx > W - BrickGame.r) { nx = W - BrickGame.r; b.vx = -abs(b.vx) }   // ios: Data/BrickGame.swift:612
        val i1 = cell(nx + (if (b.vx < 0) -BrickGame.r else BrickGame.r), b.y); if (i1 != null && hp[i1] != 0) {   // ios: Data/BrickGame.swift:613
            val wall = hp[i1] < 0   // ios: Data/BrickGame.swift:614
            hit(i1)   // ios: Data/BrickGame.swift:615
            if (wall || !pierce) { b.vx = -b.vx; nx = b.x }   // ios: Data/BrickGame.swift:616
        }
        b.x = nx   // ios: Data/BrickGame.swift:618
        // 세로
        var ny = b.y + b.vy * dt   // ios: Data/BrickGame.swift:620
        if (ny > H - BrickGame.r) { ny = H - BrickGame.r; b.vy = -abs(b.vy) }   // ios: Data/BrickGame.swift:621
        val i2 = cell(b.x, ny + (if (b.vy < 0) -BrickGame.r else BrickGame.r)); if (i2 != null && hp[i2] != 0) {   // ios: Data/BrickGame.swift:622
            val wall = hp[i2] < 0   // ios: Data/BrickGame.swift:623
            hit(i2)   // ios: Data/BrickGame.swift:624
            if (wall || !pierce) { b.vy = -b.vy; ny = b.y }   // ios: Data/BrickGame.swift:625
        }
        // 판: 아래에서 올라온 공이 판 아랫면에 닿으면
        val face = PY + PH   // ios: Data/BrickGame.swift:628
        if (b.vy < 0 && b.y - BrickGame.r >= face - 0.05 && ny - BrickGame.r < face && abs(b.x - paddleX) <= paddleW / 2 + BrickGame.r) {   // ios: Data/BrickGame.swift:629
            ny = face + BrickGame.r   // ios: Data/BrickGame.swift:630
            paddleHit(b, sp)   // ios: Data/BrickGame.swift:631
        }
        // 시오 안전망: 판을 지나 위로 간 공을 한 번 받아 준다
        if (net > 0 && b.vy < 0 && b.y >= netY && ny < netY) {   // ios: Data/BrickGame.swift:634
            net -= 1; ny = netY; b.vy = abs(b.vy)   // ios: Data/BrickGame.swift:635
            fx.play(Kind.paddle); say("안전망이 받았어요")   // ios: Data/BrickGame.swift:636
        }
        b.y = ny   // ios: Data/BrickGame.swift:638
        if (b.y < BrickGame.r && shield) { shield = false; b.y = BrickGame.r; b.vy = abs(b.vy); say("보호막이 막았어요") }   // ios: Data/BrickGame.swift:639
        return b.y > -BrickGame.r   // ios: Data/BrickGame.swift:640
    }

    private fun paddleHit(b: Ball, sp: Double) {   // ios: Data/BrickGame.swift:643
        buzz(); fx.play(Kind.paddle)   // ios: Data/BrickGame.swift:644
        combo = 0; comboSaid = 0   // ios: Data/BrickGame.swift:645
        var a = smax(-1.0, smin(1.0, (b.x - paddleX) / (paddleW / 2))) * PI / 3   // ios: Data/BrickGame.swift:646
        if (on("aim")) {   // ios: Data/BrickGame.swift:647
            // 남은 벽돌이 가장 많은 세로줄 쪽으로
            val cnt = IntArray(40) { 0 }   // ios: Data/BrickGame.swift:649
            for (i in 0 until 1000) if (hp[i] > 0) cnt[i % 40] += 1   // ios: Data/BrickGame.swift:650
            val c = cnt.indices.maxByOrNull { cnt[it] }; if (c != null && cnt[c] > 0) {   // ios: Data/BrickGame.swift:651 (max(by:) 처럼 같으면 앞의 것)
                a = smax(-PI / 3, smin(PI / 3, atan2(c.toDouble() + 0.5 - b.x, top - PY)))   // ios: Data/BrickGame.swift:652
            }
        }
        b.vx = sp * sin(a); b.vy = sp * cos(a)   // ios: Data/BrickGame.swift:655
    }

    private fun hit(i: Int) {   // ios: Data/BrickGame.swift:658
        if (!(hp[i] > 0)) return   // ios: Data/BrickGame.swift:659
        buzz()   // ios: Data/BrickGame.swift:660
        val row = i / 40; val col = i % 40; val before = broken   // ios: Data/BrickGame.swift:661
        val queue = mutableListOf<Int>()   // ios: Data/BrickGame.swift:662
        touched = mutableSetOf()   // ios: Data/BrickGame.swift:663
        // 맞은 곳 둘레를 한 번씩 깎는다 (두 번째에 깨진다)
        for ((dx, dy) in level.burst) {   // ios: Data/BrickGame.swift:665
            val c = col + dx; val r = row + dy   // ios: Data/BrickGame.swift:666
            if (!(c >= 0 && c < 40 && r >= 0 && r < 25)) continue   // ios: Data/BrickGame.swift:667
            crack(r * 40 + c, queue)   // ios: Data/BrickGame.swift:668
        }
        fx.play(if (broken > before) Kind.hit else Kind.tough)   // ios: Data/BrickGame.swift:670
        // 특수 블럭은 이어서 터진다 (폭탄이 폭탄을 터뜨리는 연쇄)
        var k = 0   // ios: Data/BrickGame.swift:672
        while (k < queue.size) { val j = queue[k]; trigger(j, queue); k += 1 }   // ios: Data/BrickGame.swift:673
        if (kind[i] == 3) effects["fast"] = t + 5   // ios: Data/BrickGame.swift:674
        if (broken > before && items.isNotEmpty() && rng.doubleIn(0.0, 1.0) < 0.14) { val it = items.swRandomElement(rng)!!   // ios: Data/BrickGame.swift:675
            drops.add(Drop(id = it, x = col.toDouble() + 0.5, y = rect(i).midY))   // ios: Data/BrickGame.swift:676
        }
        if (combo / 15 > comboSaid) { comboSaid = combo / 15; say("콤보 $combo!") }   // ios: Data/BrickGame.swift:678
    }

    /** 한 칸 깎기. 깨지면 점수, 특수 블럭이면 터질 차례에 넣는다 */
    private fun crack(j: Int, queue: MutableList<Int>, all: Boolean = false) {   // ios: Data/BrickGame.swift:682
        if (!(hp[j] > 0)) return   // ios: Data/BrickGame.swift:683
        if (group[j] >= 0) { crackBig(group[j], j, queue); return }   // ios: Data/BrickGame.swift:684
        hp[j] = if (all) 0 else hp[j] - 1   // ios: Data/BrickGame.swift:685
        revealed[j] = true   // ios: Data/BrickGame.swift:686
        if (hp[j] != 0) return   // ios: Data/BrickGame.swift:687
        broken += 1; combo += 1   // ios: Data/BrickGame.swift:688
        var mul = if (skill == "ir") 1.5 else 1.0   // ios: Data/BrickGame.swift:689
        if (on("double")) mul *= 2   // ios: Data/BrickGame.swift:690
        if (combo > 10) mul *= 1.5   // ios: Data/BrickGame.swift:691 (한 번에 많이 깨면 점수가 더)
        mul *= scoreMul   // ios: Data/BrickGame.swift:692
        val p: Double = when (kind[j]) { 2 -> 2.0; 3 -> 3.0; else -> 1.0 }   // ios: Data/BrickGame.swift:693
        score += swRound(p * mul).toInt()   // ios: Data/BrickGame.swift:694
        if (j == exitCell) { score += 100; say("출구 칸 +100") }   // ios: Data/BrickGame.swift:695
        if (special[j] != null) queue.add(j)   // ios: Data/BrickGame.swift:696
    }

    /** 테트리스 블럭: 숨어 있을 땐 칸마다 보통 두 번 맞는 벽돌과 똑같다. 그중 한 칸이 깨지면 조각 전체가 드러나고,
     *  그 뒤로 한 번 맞을 때(폭발 한 번도) 1씩, 칸 수만큼 더 맞으면 한꺼번에 없어진다 */
    private fun crackBig(g: Int, j: Int, queue: MutableList<Int>) {   // ios: Data/BrickGame.swift:701
        if (!(bigs[g].hp > 0)) return   // ios: Data/BrickGame.swift:702
        if (!bigs[g].revealed) {   // ios: Data/BrickGame.swift:703
            revealed[j] = true   // ios: Data/BrickGame.swift:704
            if (hp[j] > 1) { hp[j] -= 1; return }   // ios: Data/BrickGame.swift:705
            bigs[g].revealed = true   // ios: Data/BrickGame.swift:706
            for (c in bigs[g].cells) hp[c] = 1   // ios: Data/BrickGame.swift:707
            touched.add(g)   // ios: Data/BrickGame.swift:708
            say("테트리스 블럭! ${bigs[g].hp}번 더")   // ios: Data/BrickGame.swift:709
            fx.play(Kind.tetris(bigs[g].shape)); return   // ios: Data/BrickGame.swift:710
        }
        if (touched.contains(g)) return   // ios: Data/BrickGame.swift:712
        touched.add(g)   // ios: Data/BrickGame.swift:713
        bigs[g].hp -= 1   // ios: Data/BrickGame.swift:714
        if (bigs[g].hp != 0) { fx.play(Kind.tough); return }   // ios: Data/BrickGame.swift:715
        val b = bigs[g]; val bonus = swRound((b.cells.size * 10).toDouble() * scoreMul).toInt()   // ios: Data/BrickGame.swift:716
        for (c in b.cells) if (hp[c] > 0) { hp[c] = 0; broken += 1; combo += 1; score += swRound(2 * scoreMul).toInt() }   // ios: Data/BrickGame.swift:717
        score += bonus   // ios: Data/BrickGame.swift:718
        // 모양을 따라 바로 옆 한 칸씩 같이 깨진다 (위·아래·왼쪽·오른쪽)
        for (c in b.cells) {   // ios: Data/BrickGame.swift:720
            val r = c / 40; val col = c % 40   // ios: Data/BrickGame.swift:721
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {   // ios: Data/BrickGame.swift:722
                val cc = col + dx; val rr = r + dy   // ios: Data/BrickGame.swift:723
                if (!(cc >= 0 && cc < 40 && rr >= 0 && rr < 25)) continue   // ios: Data/BrickGame.swift:724
                val k = rr * 40 + cc   // ios: Data/BrickGame.swift:725
                if (group[k] != g) crack(k, queue, all = true)   // ios: Data/BrickGame.swift:726
            }
        }
        val xs = b.cells.map { rect(it).midX }; val ys = b.cells.map { rect(it).midY }   // ios: Data/BrickGame.swift:729
        blasts.add(Blast(x = xs.fold(0.0) { a, v -> a + v } / xs.size.toDouble(), y = ys.fold(0.0) { a, v -> a + v } / ys.size.toDouble(), kind = Special.bomb, t0 = t))   // ios: Data/BrickGame.swift:730
        fx.play(Kind.tetris(b.shape))   // ios: Data/BrickGame.swift:731
        say("테트리스 블럭 +$bonus")   // ios: Data/BrickGame.swift:732
    }

    private fun trigger(j: Int, queue: MutableList<Int>) {   // ios: Data/BrickGame.swift:735
        val sp = special[j] ?: return   // ios: Data/BrickGame.swift:736
        special[j] = null   // ios: Data/BrickGame.swift:737
        touched = mutableSetOf()   // ios: Data/BrickGame.swift:738
        val row = j / 40; val col = j % 40; val c = rect(j)   // ios: Data/BrickGame.swift:739
        blasts.add(Blast(x = c.midX, y = c.midY, kind = sp, t0 = t))   // ios: Data/BrickGame.swift:740
        when (sp) {   // ios: Data/BrickGame.swift:741
            Special.bomb -> {   // ios: Data/BrickGame.swift:742
                fx.play(Kind.bomb)   // ios: Data/BrickGame.swift:743
                fx.impact(Impact.heavy)   // ios: Data/BrickGame.swift:744
                for (dy in -2..2) { for (dx in -2..2) { if (!(abs(dx) + abs(dy) <= 3)) continue   // ios: Data/BrickGame.swift:745
                    val cc = col + dx; val r = row + dy   // ios: Data/BrickGame.swift:746
                    if (cc >= 0 && cc < 40 && r >= 0 && r < 25) crack(r * 40 + cc, queue, all = true)   // ios: Data/BrickGame.swift:747
                } }
            }
            Special.row -> {   // ios: Data/BrickGame.swift:749
                fx.play(Kind.row)   // ios: Data/BrickGame.swift:750
                for (cc in 0 until 40) crack(row * 40 + cc, queue, all = true)   // ios: Data/BrickGame.swift:751
            }
            Special.col -> {   // ios: Data/BrickGame.swift:752
                fx.play(Kind.col)   // ios: Data/BrickGame.swift:753
                for (r in 0 until 25) crack(r * 40 + col, queue, all = true)   // ios: Data/BrickGame.swift:754
            }
            Special.multi -> {   // ios: Data/BrickGame.swift:755
                fx.play(Kind.multi)   // ios: Data/BrickGame.swift:756
                val v = speed   // ios: Data/BrickGame.swift:757
                for (a in listOf(-0.6, 0.0, 0.6)) { if (!(balls.size < 12)) continue   // ios: Data/BrickGame.swift:758
                    balls.add(Ball(x = c.midX, y = c.minY - BrickGame.r - 0.05, vx = v * sin(a), vy = -v * cos(a), stuck = false, spawned = true))   // ios: Data/BrickGame.swift:759
                }
                say("공 셋!")   // ios: Data/BrickGame.swift:761
            }
            Special.gift -> {   // ios: Data/BrickGame.swift:762
                fx.play(Kind.gift)   // ios: Data/BrickGame.swift:763
                val it = items.swRandomElement(rng); if (it != null) drops.add(Drop(id = it, x = c.midX, y = c.midY))   // ios: Data/BrickGame.swift:764
                else { val p = swRound(50 * scoreMul).toInt(); score += p; say("선물 +$p") }   // ios: Data/BrickGame.swift:765
            }
        }
    }

    private fun moveDrops(dt: Double) {   // ios: Data/BrickGame.swift:769
        val keep = mutableListOf<Drop>()   // ios: Data/BrickGame.swift:770
        for (d0 in drops.toList()) { val d = d0.copy()   // ios: Data/BrickGame.swift:771
            d.y -= 9 * dt   // ios: Data/BrickGame.swift:772
            if (d.y <= PY + PH + 0.8 && d.y >= PY - 1 && abs(d.x - paddleX) <= paddleW / 2 + 0.8) { use(d.id); continue }   // ios: Data/BrickGame.swift:773
            if (d.y > -1) keep.add(d)   // ios: Data/BrickGame.swift:774
        }
        drops = keep   // ios: Data/BrickGame.swift:776
    }

    private fun use(id: String) {   // ios: Data/BrickGame.swift:779
        val name = Shelter.items.firstOrNull { it.id == id }?.name ?: id   // ios: Data/BrickGame.swift:780
        say("$name · ${GamePlayer.itemShort(id)}")   // ios: Data/BrickGame.swift:781
        picked[id] = (picked[id] ?: 0) + 1   // ios: Data/BrickGame.swift:782
        fx.play(Kind.item)   // ios: Data/BrickGame.swift:783
        fx.impact(Impact.medium)   // ios: Data/BrickGame.swift:784
        talk(GameLines.Event.item, item = id)   // ios: Data/BrickGame.swift:785
        when (id) {   // ios: Data/BrickGame.swift:786
            "barley_tea" -> effects["slow"] = t + 10   // ios: Data/BrickGame.swift:787
            "porch_light" -> effects["light"] = t + 10   // ios: Data/BrickGame.swift:788
            "wall_clock" -> effects["clock"] = t + 3   // ios: Data/BrickGame.swift:789
            "hair_tie" -> effects["wide"] = t + 15   // ios: Data/BrickGame.swift:790
            "table_chair" ->   // ios: Data/BrickGame.swift:791
                balls.add(Ball(x = paddleX, y = PY + PH + BrickGame.r, vx = 0.0, vy = 0.0, stuck = true, off = 0.0, until = t + 0.3))   // ios: Data/BrickGame.swift:792
            "asym_bowl" -> effects["aim"] = t + 10   // ios: Data/BrickGame.swift:793
            "unfired_bowl" -> shield = true   // ios: Data/BrickGame.swift:794
            "jujube_seed" -> effects["double"] = t + 10   // ios: Data/BrickGame.swift:795
            "elder_bead" -> effects["pierce"] = t + 5   // ios: Data/BrickGame.swift:796
            "rice_seeds" -> {   // ios: Data/BrickGame.swift:797
                val more = mutableListOf<Ball>()   // ios: Data/BrickGame.swift:798
                for (b in balls) { if (!(!b.stuck && balls.size + more.size < 12)) continue   // ios: Data/BrickGame.swift:799
                    for (a in listOf(-0.35, 0.35)) {   // ios: Data/BrickGame.swift:800
                        val c = b.copy()   // ios: Data/BrickGame.swift:801
                        c.vx = b.vx * cos(a) - b.vy * sin(a); c.vy = b.vx * sin(a) + b.vy * cos(a)   // ios: Data/BrickGame.swift:802
                        more.add(c)   // ios: Data/BrickGame.swift:803
                    }
                }
                balls.addAll(more)   // ios: Data/BrickGame.swift:806
            }
            else -> {}   // ios: Data/BrickGame.swift:807
        }
    }

    private fun loseBall() {   // ios: Data/BrickGame.swift:811
        combo = 0; comboSaid = 0   // ios: Data/BrickGame.swift:812 (공을 잃으면 콤보도 끊긴다)
        if (skill == "seonbae" && !rewindUsed) {   // ios: Data/BrickGame.swift:813
            rewindUsed = true   // ios: Data/BrickGame.swift:814
            balls = mutableListOf(Ball(x = paddleX, y = PY + PH + BrickGame.r, vx = 0.0, vy = 0.0, stuck = true, off = 0.0, until = t + 1.5))   // ios: Data/BrickGame.swift:815
            say("되감기 · 한 번 돌려받았어요"); talk(GameLines.Event.rewind); return   // ios: Data/BrickGame.swift:816
        }
        lives -= 1   // ios: Data/BrickGame.swift:818
        fx.play(Kind.lose)   // ios: Data/BrickGame.swift:819
        fx.notify(Notice.warning)   // ios: Data/BrickGame.swift:820
        if (lives <= 0) { talk(GameLines.Event.over); fx.play(Kind.over); finish(Phase.over); return }   // ios: Data/BrickGame.swift:821
        talk(if (lives == 1) GameLines.Event.last else GameLines.Event.lose)   // ios: Data/BrickGame.swift:822
        drops = mutableListOf()   // ios: Data/BrickGame.swift:823
        balls = mutableListOf(Ball(x = paddleX, y = PY + PH + BrickGame.r, vx = 0.0, vy = 0.0, stuck = true, off = 0.0, until = t + 2.5))   // ios: Data/BrickGame.swift:824
    }

    private fun win() {   // ios: Data/BrickGame.swift:827
        score += swRound((lives * 50).toDouble() * scoreMul).toInt()   // ios: Data/BrickGame.swift:828
        fx.play(Kind.clear)   // ios: Data/BrickGame.swift:829
        talk(GameLines.Event.clear)   // ios: Data/BrickGame.swift:830
        finish(Phase.cleared)   // ios: Data/BrickGame.swift:831
        fx.notify(Notice.success)   // ios: Data/BrickGame.swift:832
    }

    private fun say(s: String) { toast = Pair(s, t + 2.2) }   // ios: Data/BrickGame.swift:835

    /** 인물 말풍선 (시작 전에는 계속, 그 밖에는 3초) */
    fun talk(e: GameLines.Event, item: String? = null) {   // ios: Data/BrickGame.swift:838
        val vars = mapOf("stage" to stage.bg.volume + " " + stage.bg.name, "n" to "${max(0, lives)}", "score" to score.toDouble().swFmt(),   // ios: Data/BrickGame.swift:839
                    "broken" to "$broken", "left" to "$total")   // ios: Data/BrickGame.swift:840
        speech = GameLines.pick(e, player, item = item, vars = vars, rng = rng)   // ios: Data/BrickGame.swift:841
        speechUntil = t + 3.2   // ios: Data/BrickGame.swift:842
        mood = e; moodAt = t   // ios: Data/BrickGame.swift:843
        // 특정 상황에는 다른 인물이 오른쪽에 나와 한마디 (물건은 가끔)
        val chance: Double = when (e) { GameLines.Event.last, GameLines.Event.clear, GameLines.Event.over -> 1.0; GameLines.Event.half -> 0.7; GameLines.Event.item -> 0.3; else -> 0.0 }   // ios: Data/BrickGame.swift:845
        // 그 권의 보조 인물이 있으면 절반쯤은 그쪽이 나온다
        val samples = (GameLines.stageSamples[stage.n] ?: emptyList()).map { "s:" + it }   // ios: Data/BrickGame.swift:847
        val others = friends.filter { it != player && it != "me" }   // ios: Data/BrickGame.swift:848
        val pickFrom = if (samples.isNotEmpty() && (others.isEmpty() || rng.bool())) samples else others   // ios: Data/BrickGame.swift:849
        if (rng.doubleIn(0.0, 1.0) < chance) { val who = pickFrom.swRandomElement(rng)   // ios: Data/BrickGame.swift:850
            val line = if (who != null) GameLines.guestLine(e, who, rng) else null; if (who != null && line != null) {   // ios: Data/BrickGame.swift:851
                guest = who; guestLine = line; guestUntil = t + 3.6   // ios: Data/BrickGame.swift:852
            }
        }
    }
    val guestShown: Boolean get() = guest.isNotEmpty() && (phase == Phase.cleared || phase == Phase.over || t < guestUntil)   // ios: Data/BrickGame.swift:855
    val speaking: Boolean get() = phase == Phase.ready || phase == Phase.cleared || phase == Phase.over || t < speechUntil   // ios: Data/BrickGame.swift:856

    private fun buzz() {   // ios: Data/BrickGame.swift:858
        if (!(t - lastTap > 0.06)) return   // ios: Data/BrickGame.swift:859
        lastTap = t; fx.impact(tap, 0.6)   // ios: Data/BrickGame.swift:860
    }
}

/** 효과음: 짧은 소리를 코드로 만들어 둔다 (파일 없음). 다른 앱 소리와 섞인다 (미디어 음량. 안드로이드엔 무음 스위치가 없다) */
class GameSound {   // ios: Data/BrickGame.swift:865
    // ios: Data/BrickGame.swift:866
    sealed class Kind(private val key: String) {
        override fun toString(): String = key
        object hit : Kind("hit"); object tough : Kind("tough"); object paddle : Kind("paddle"); object item : Kind("item")
        object lose : Kind("lose"); object clear : Kind("clear"); object over : Kind("over"); object start : Kind("start")
        object bomb : Kind("bomb"); object row : Kind("row"); object col : Kind("col"); object multi : Kind("multi"); object gift : Kind("gift")
        data class tetris(val n: Int) : Kind("tetris") { override fun toString(): String = "tetris($n)" }
    }
    companion object {
        val shared: GameSound by lazy { GameSound() }   // ios: Data/BrickGame.swift:867
    }
    private val players = mutableListOf<AudioTrack>()   // ios: Data/BrickGame.swift:868,869 (AVAudioEngine + 재생기 여섯 → AudioTrack 여섯)
    private var next = 0   // ios: Data/BrickGame.swift:870
    private val buffers = HashMap<Kind, FloatArray>()   // ios: Data/BrickGame.swift:871
    private var lastHit = 0.0   // ios: Data/BrickGame.swift:872
    @Volatile private var ready = false   // ios: Data/BrickGame.swift:873

    @Synchronized fun start() {   // ios: Data/BrickGame.swift:875
        if (ready) return   // ios: Data/BrickGame.swift:876 (AudioTrack 은 멈추지 않아 다시 켤 일이 없다)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()   // ios: Data/BrickGame.swift:877,878 (ambient·mixWithOthers)
        val channels = 1; val fmt = AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO).build()   // ios: Data/BrickGame.swift:879
        val outputVolume = 0.5f   // ios: Data/BrickGame.swift:885
        // (주파수, 길이) 음표들
        val notes: Map<Kind, List<Pair<Double, Double>>> = mapOf(   // ios: Data/BrickGame.swift:887
            Kind.hit to listOf(880.0 to 0.035), Kind.tough to listOf(330.0 to 0.05), Kind.paddle to listOf(523.0 to 0.05),   // ios: Data/BrickGame.swift:888
            Kind.item to listOf(660.0 to 0.06, 880.0 to 0.06, 1320.0 to 0.09),   // ios: Data/BrickGame.swift:889
            Kind.lose to listOf(440.0 to 0.1, 330.0 to 0.1, 220.0 to 0.18),   // ios: Data/BrickGame.swift:890
            Kind.clear to listOf(523.0 to 0.1, 659.0 to 0.1, 784.0 to 0.1, 1047.0 to 0.25),   // ios: Data/BrickGame.swift:891
            Kind.over to listOf(392.0 to 0.14, 330.0 to 0.14, 262.0 to 0.14, 196.0 to 0.35),   // ios: Data/BrickGame.swift:892
            Kind.start to listOf(392.0 to 0.07, 523.0 to 0.07, 659.0 to 0.07, 784.0 to 0.16),   // ios: Data/BrickGame.swift:893
            // 특수 블럭마다 다른 소리: 폭탄 낮게 쿵, 주황 줄 가로는 위로·세로는 아래로 쓸기, 파랑 공 셋 통통통, 노랑 선물 반짝
            Kind.bomb to listOf(140.0 to 0.06, 95.0 to 0.08, 70.0 to 0.14),   // ios: Data/BrickGame.swift:895
            Kind.row to listOf(1047.0 to 0.04, 1397.0 to 0.04, 1760.0 to 0.07),   // ios: Data/BrickGame.swift:896
            Kind.col to listOf(1760.0 to 0.04, 1397.0 to 0.04, 1047.0 to 0.07),   // ios: Data/BrickGame.swift:897
            Kind.multi to listOf(784.0 to 0.05, 0.0 to 0.02, 784.0 to 0.05, 0.0 to 0.02, 1047.0 to 0.08),   // ios: Data/BrickGame.swift:898
            Kind.gift to listOf(1568.0 to 0.05, 2093.0 to 0.05, 1568.0 to 0.05, 2637.0 to 0.1),   // ios: Data/BrickGame.swift:899
            // 테트리스 일곱 조각 (I O T S Z J L) 마다 다른 가락
            Kind.tetris(0) to listOf(523.0 to 0.05, 659.0 to 0.05, 784.0 to 0.05, 1047.0 to 0.12),   // ios: Data/BrickGame.swift:901
            Kind.tetris(1) to listOf(587.0 to 0.08, 587.0 to 0.08, 880.0 to 0.14),   // ios: Data/BrickGame.swift:902
            Kind.tetris(2) to listOf(659.0 to 0.06, 880.0 to 0.06, 659.0 to 0.12),   // ios: Data/BrickGame.swift:903
            Kind.tetris(3) to listOf(440.0 to 0.06, 554.0 to 0.06, 659.0 to 0.06, 554.0 to 0.1),   // ios: Data/BrickGame.swift:904
            Kind.tetris(4) to listOf(698.0 to 0.06, 587.0 to 0.06, 466.0 to 0.06, 587.0 to 0.1),   // ios: Data/BrickGame.swift:905
            Kind.tetris(5) to listOf(330.0 to 0.06, 392.0 to 0.06, 494.0 to 0.06, 659.0 to 0.12),   // ios: Data/BrickGame.swift:906
            Kind.tetris(6) to listOf(988.0 to 0.06, 784.0 to 0.06, 659.0 to 0.06, 523.0 to 0.12),   // ios: Data/BrickGame.swift:907
        )
        for ((k, ns) in notes) buffers[k] = tone(ns, 44100.0)   // ios: Data/BrickGame.swift:909
        val cap = buffers.values.maxOf { it.size }
        try {   // ios: Data/BrickGame.swift:910
            for (i in 0 until 6) {   // ios: Data/BrickGame.swift:880
                val p = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(fmt).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(cap * 4).build()   // ios: Data/BrickGame.swift:881
                p.setVolume(outputVolume)   // ios: Data/BrickGame.swift:882 (mainMixerNode 에 잇기 → 트랙 음량)
                players.add(p)   // ios: Data/BrickGame.swift:883
            }
            ready = true
        } catch (e: Exception) { players.forEach { it.release() }; players.clear(); ready = false }
    }

    fun play(k: Kind) {   // ios: Data/BrickGame.swift:913
        val b = buffers[k]; if (!ready || b == null) return   // ios: Data/BrickGame.swift:914
        if (k == Kind.hit) {   // ios: Data/BrickGame.swift:915 (벽돌 소리는 너무 겹치지 않게)
            val now = SystemClock.uptimeMillis() / 1000.0   // ios: Data/BrickGame.swift:916
            if (!(now - lastHit > 0.045)) return   // ios: Data/BrickGame.swift:917
            lastHit = now   // ios: Data/BrickGame.swift:918
        }
        val p = players[next]; next = (next + 1) % players.size   // ios: Data/BrickGame.swift:921
        runCatching { p.pause(); p.flush(); p.write(b, 0, b.size, AudioTrack.WRITE_NON_BLOCKING); p.play(); p.stop() }   // ios: Data/BrickGame.swift:920,922 (멈추고 비운 뒤 새 소리를 넣고 재생. 스트림 트랙은 버퍼가 다 차야 소리가 나므로 stop 으로 넣은 만큼 끝까지 내보낸다)
    }

    private fun tone(notes: List<Pair<Double, Double>>, sr: Double): FloatArray {   // ios: Data/BrickGame.swift:925
        val total = notes.fold(0.0) { a, n -> a + n.second }   // ios: Data/BrickGame.swift:926
        val buf = arrayOf(FloatArray((total * sr).toInt() + 1)); val d = buf[0]   // ios: Data/BrickGame.swift:927
        var i = 0   // ios: Data/BrickGame.swift:928
        for ((f, len) in notes) {   // ios: Data/BrickGame.swift:929
            val n = (len * sr).toInt()   // ios: Data/BrickGame.swift:930
            for (j in 0 until n) {   // ios: Data/BrickGame.swift:931
                val t = j.toDouble() / sr; val env = min(1.0, j.toDouble() / 200) * (1 - j.toDouble() / n.toDouble())   // ios: Data/BrickGame.swift:932
                // 네모파에 가까운 8비트 소리 (홀수 배음 셋)
                val w = if (f == 0.0) 0.0 else sin(2 * PI * f * t) + sin(6 * PI * f * t) / 3 + sin(10 * PI * f * t) / 5   // ios: Data/BrickGame.swift:934
                d[i + j] = (w * env * 0.35).toFloat()   // ios: Data/BrickGame.swift:935
            }
            i += n   // ios: Data/BrickGame.swift:937
        }
        return d.copyOf(i)   // ios: Data/BrickGame.swift:939,940 (frameLength = i)
    }
}
