package com.naeilo.app.ui.wave

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.naeilo.app.core.calc.WaveEngine
import com.naeilo.app.data.Defaults
import com.naeilo.app.ui.WaveFeedbackKeys
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Views/Wave/WaveFeedback.swift 1-125: 파도 타기 소리·진동. 소리 파일 없이 짧은 소리 4개를 처음 한 번 메모리에서 만들어 둔다 (각 10KB 안팎).
// 소리는 AudioTrack 정적 버퍼(만들어 둔 WAV 의 PCM)로 바로 낸다 (지연이 가장 짧다). 무음·진동 모드면 안 낸다 (iOS .ambient 가 무음 스위치를 따르듯).
// 오디오 포커스를 잡지 않아 다른 앱 음악을 끊지 않는다 (.mixWithOthers). 진동은 Vibrator 로 iOS 진동 세기·간격을 흉내 낸다.
// 설정 스위치 두 줄과 저장 키(WaveFeedbackKeys)는 ui/WaveFeedbackToggles.kt 에 있다.
// 플레이 화면은 엔진 이벤트를 그대로 넘기면 된다: WaveFeedback.shared.play(events)

// ios: Views/Wave/WaveFeedback.swift:7,8
class WaveFeedback internal constructor(private val out: Out) {
    /** 소리·진동을 실제로 내는 곳. 앱은 AndroidOut, 숫자 맞추기 시험(WParityTest)은 기록만 하는 것을 넣는다 */
    interface Out {
        fun flag(key: String, def: Boolean): Boolean
        fun session()
        fun player(wav: ByteArray): Player?
        fun impact(style: Style, intensity: Double)
        fun notify(type: Note)
        fun after(ms: Long, f: () -> Unit)
    }
    /** AVAudioPlayer 자리 */
    interface Player { fun prepareToPlay(); var currentTime: Double; fun play() }
    enum class Style { light, rigid }
    enum class Note { error, success }

    /** UIImpactFeedbackGenerator 자리 */
    inner class Impact(private val style: Style) {
        fun prepare() {}   // 안드로이드 진동은 미리 데울 것이 없다
        fun impactOccurred(intensity: Double = 1.0) = out.impact(style, intensity)
    }
    /** UINotificationFeedbackGenerator 자리 */
    inner class NoteGen { fun notificationOccurred(t: Note) = out.notify(t) }

    // ios: Views/Wave/WaveFeedback.swift:9
    companion object {
        val shared: WaveFeedback by lazy { WaveFeedback(AndroidOut(Defaults.ctx)) }

        // MARK: 소리 만들기 (22.05kHz 모노 16비트 WAV)

        private const val rate = 22050.0   // ios: Views/Wave/WaveFeedback.swift:82

        // ios: Views/Wave/WaveFeedback.swift:84
        internal fun wav(s: Sound): ByteArray {
            // ios: Views/Wave/WaveFeedback.swift:85,86
            val (dur, f) = when (s) {
                // 첨벙: 짧게 터지는 물소리
                Sound.splash -> 0.18 to { t: Double -> noise() * exp(-t * 28) * 0.5 }   // ios: Views/Wave/WaveFeedback.swift:87,88
                // 동전: 띵-딩 두 음
                // ios: Views/Wave/WaveFeedback.swift:89,90
                Sound.coin -> 0.22 to { t: Double ->
                    val hz = if (t < 0.07) 988.0 else 1319.0   // ios: Views/Wave/WaveFeedback.swift:91
                    val t0 = if (t < 0.07) t else t - 0.07   // ios: Views/Wave/WaveFeedback.swift:92
                    sin(2 * PI * hz * t) * exp(-t0 * 18) * 0.35   // ios: Views/Wave/WaveFeedback.swift:93
                }
                // 풍덩: 낮게 꺼지는 소리 + 물
                // ios: Views/Wave/WaveFeedback.swift:95,96
                Sound.plunge -> 0.32 to { t: Double ->
                    val hz = 220 * exp(-t * 4)   // ios: Views/Wave/WaveFeedback.swift:97
                    (sin(2 * PI * hz * t) * 0.45 + noise() * 0.15) * exp(-t * 9)   // ios: Views/Wave/WaveFeedback.swift:98
                }
                // 하품: 천천히 내려가는 음
                // ios: Views/Wave/WaveFeedback.swift:100,101
                Sound.yawn -> 0.45 to { t: Double ->
                    val hz = 420 - 220 * t / 0.45   // ios: Views/Wave/WaveFeedback.swift:102
                    val env = sin(PI * t / 0.45)   // ios: Views/Wave/WaveFeedback.swift:103
                    sin(2 * PI * hz * t) * env * 0.25   // ios: Views/Wave/WaveFeedback.swift:104
                }
            }   // ios: Views/Wave/WaveFeedback.swift:107
            val n = (dur * rate).toInt()   // ios: Views/Wave/WaveFeedback.swift:108
            val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)   // ios: Views/Wave/WaveFeedback.swift:109
            // ios: Views/Wave/WaveFeedback.swift:110
            for (i in 0 until n) {
                val v = (max(-1.0, min(1.0, f(i.toDouble() / rate))) * 32000).toInt().toShort()   // ios: Views/Wave/WaveFeedback.swift:111
                pcm.putShort(v)   // ios: Views/Wave/WaveFeedback.swift:112
            }
            val d = ByteBuffer.allocate(44 + pcm.capacity()).order(ByteOrder.LITTLE_ENDIAN)   // ios: Views/Wave/WaveFeedback.swift:114
            fun u32(v: Long) { d.putInt(v.toInt()) }   // ios: Views/Wave/WaveFeedback.swift:115
            fun u16(v: Int) { d.putShort(v.toShort()) }   // ios: Views/Wave/WaveFeedback.swift:116
            d.put("RIFF".toByteArray()); u32((36 + pcm.capacity()).toLong())   // ios: Views/Wave/WaveFeedback.swift:117
            d.put("WAVEfmt ".toByteArray()); u32(16); u16(1); u16(1)   // ios: Views/Wave/WaveFeedback.swift:118
            u32(rate.toLong()); u32(rate.toLong() * 2); u16(2); u16(16)   // ios: Views/Wave/WaveFeedback.swift:119
            d.put("data".toByteArray()); u32(pcm.capacity().toLong())   // ios: Views/Wave/WaveFeedback.swift:120
            d.put(pcm.array())   // ios: Views/Wave/WaveFeedback.swift:121
            return d.array()   // ios: Views/Wave/WaveFeedback.swift:122
        }

        private fun noise(): Double = Random.nextDouble(-1.0, 1.0)   // ios: Views/Wave/WaveFeedback.swift:125
    }

    enum class Sound { splash, coin, plunge, yawn }   // ios: Views/Wave/WaveFeedback.swift:12

    private val players = HashMap<Sound, Player>()   // ios: Views/Wave/WaveFeedback.swift:14
    private val light = Impact(Style.light)   // ios: Views/Wave/WaveFeedback.swift:15
    private val rigid = Impact(Style.rigid)   // ios: Views/Wave/WaveFeedback.swift:16
    private val note = NoteGen()   // ios: Views/Wave/WaveFeedback.swift:17

    private val soundOn: Boolean get() = out.flag(WaveFeedbackKeys.soundKey, true)   // ios: Views/Wave/WaveFeedback.swift:19
    private val hapticOn: Boolean get() = out.flag(WaveFeedbackKeys.hapticKey, true)   // ios: Views/Wave/WaveFeedback.swift:20

    /** 게임 화면이 뜰 때 부르면 첫 소리가 늦지 않다 (소리 만들기는 몇 ms: 화면은 Dispatchers.Default 에서 부른다) */
    // ios: Views/Wave/WaveFeedback.swift:23
    @Synchronized fun prepare() {
        light.prepare(); rigid.prepare()   // ios: Views/Wave/WaveFeedback.swift:24
        if (!(soundOn && players.isEmpty())) return   // ios: Views/Wave/WaveFeedback.swift:25
        out.session()   // ios: Views/Wave/WaveFeedback.swift:26
        // ios: Views/Wave/WaveFeedback.swift:27
        for (s in Sound.entries) {
            out.player(wav(s))?.let { p -> p.prepareToPlay(); players[s] = p }   // ios: Views/Wave/WaveFeedback.swift:28
        }
    }

    fun play(events: List<WaveEngine.Event>) { events.forEach { play(it) } }   // ios: Views/Wave/WaveFeedback.swift:32

    // ios: Views/Wave/WaveFeedback.swift:34
    fun play(e: WaveEngine.Event) {
        // ios: Views/Wave/WaveFeedback.swift:35
        when (e) {
            is WaveEngine.Event.board -> {   // ios: Views/Wave/WaveFeedback.swift:36
                buzz { if (e.perfect) rigid.impactOccurred() else light.impactOccurred() }; sound(Sound.splash)   // ios: Views/Wave/WaveFeedback.swift:37
            }
            is WaveEngine.Event.leave -> {   // ios: Views/Wave/WaveFeedback.swift:38
                buzz { rigid.impactOccurred() }   // ios: Views/Wave/WaveFeedback.swift:39
                if (e.points > 0) sound(Sound.coin)   // ios: Views/Wave/WaveFeedback.swift:40
            }
            is WaveEngine.Event.splash -> {   // ios: Views/Wave/WaveFeedback.swift:41
                buzz { note.notificationOccurred(Note.error) }; sound(Sound.plunge)   // ios: Views/Wave/WaveFeedback.swift:42
            }
            is WaveEngine.Event.calm ->   // ios: Views/Wave/WaveFeedback.swift:43
                sound(Sound.yawn)   // ios: Views/Wave/WaveFeedback.swift:44
            is WaveEngine.Event.streak ->   // ios: Views/Wave/WaveFeedback.swift:45
                crown()   // ios: Views/Wave/WaveFeedback.swift:46
            is WaveEngine.Event.fever ->   // ios: Views/Wave/WaveFeedback.swift:47
                if (e.on) crown()   // ios: Views/Wave/WaveFeedback.swift:48
            is WaveEngine.Event.flip ->   // ios: Views/Wave/WaveFeedback.swift:49
                buzz { light.impactOccurred(intensity = 0.7) }   // ios: Views/Wave/WaveFeedback.swift:50
            is WaveEngine.Event.land, is WaveEngine.Event.item, is WaveEngine.Event.mission -> {   // ios: Views/Wave/WaveFeedback.swift:51
                buzz { note.notificationOccurred(Note.success) }; sound(Sound.coin)   // ios: Views/Wave/WaveFeedback.swift:52
            }
            is WaveEngine.Event.time ->   // ios: Views/Wave/WaveFeedback.swift:53
                Unit   // ios: Views/Wave/WaveFeedback.swift:54
            is WaveEngine.Event.saved ->   // ios: Views/Wave/WaveFeedback.swift:55
                buzz { rigid.impactOccurred() }   // ios: Views/Wave/WaveFeedback.swift:56
            is WaveEngine.Event.end ->   // ios: Views/Wave/WaveFeedback.swift:57
                Unit   // ios: Views/Wave/WaveFeedback.swift:58
        }
    }

    /** 왕관: 톡 톡 두 번 */
    // ios: Views/Wave/WaveFeedback.swift:63
    fun crown() {
        // ios: Views/Wave/WaveFeedback.swift:64
        buzz {
            rigid.impactOccurred(intensity = 0.8)   // ios: Views/Wave/WaveFeedback.swift:65
            out.after(120) { rigid.impactOccurred() }   // ios: Views/Wave/WaveFeedback.swift:66
        }
        sound(Sound.coin)   // ios: Views/Wave/WaveFeedback.swift:68
    }

    private inline fun buzz(f: () -> Unit) { if (hapticOn) f() }   // ios: Views/Wave/WaveFeedback.swift:71

    // ios: Views/Wave/WaveFeedback.swift:73
    private fun sound(s: Sound) {
        if (!soundOn) return   // ios: Views/Wave/WaveFeedback.swift:74
        if (players.isEmpty()) prepare()   // ios: Views/Wave/WaveFeedback.swift:75
        val p = synchronized(this) { players[s] } ?: return   // ios: Views/Wave/WaveFeedback.swift:76
        p.currentTime = 0.0; p.play()   // ios: Views/Wave/WaveFeedback.swift:77
    }
}

/** 앱에서 쓰는 소리·진동: AudioTrack 정적 버퍼 + Vibrator */
private class AndroidOut(private val ctx: Context) : WaveFeedback.Out {
    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    @Suppress("DEPRECATION")
    private val vib: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun flag(key: String, def: Boolean): Boolean = Defaults.prefs.getBoolean(key, def)
    /** .ambient + .mixWithOthers: 게임 소리(USAGE_GAME)로 내고 오디오 포커스를 잡지 않는다 (아래 player 가 속성을 단다) */
    override fun session() {}

    override fun player(wav: ByteArray): WaveFeedback.Player? = runCatching {
        val n = wav.size - 44   // WAV 머리 44바이트 뒤가 PCM
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(22050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        if (t.write(wav, 44, n) != n) { t.release(); null } else Track(t)
    }.getOrNull()

    private inner class Track(private val t: AudioTrack) : WaveFeedback.Player {
        override fun prepareToPlay() {}
        override var currentTime: Double = 0.0
            set(v) { field = v; runCatching { if (t.playState != AudioTrack.PLAYSTATE_STOPPED) t.pause(); t.setPlaybackHeadPosition((v * 22050).toInt()) } }
        override fun play() {
            // 무음·진동 모드면 내지 않는다 (iOS 무음 스위치)
            if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            runCatching { t.play() }
        }
    }

    /** iOS 진동 세기: light 약하고 짧게, rigid 단단하고 짧게 (intensity 로 세기) */
    override fun impact(style: WaveFeedback.Style, intensity: Double) {
        val v = vib ?: return
        val (ms, amp) = if (style == WaveFeedback.Style.light) 10L to 110 else 14L to 255
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, (amp * intensity).roundToInt().coerceIn(1, 255))) }
    }

    /** 알림 진동: success 톡-톡, error 톡-톡-톡 (iOS 알림 진동 간격) */
    override fun notify(type: WaveFeedback.Note) {
        val v = vib ?: return
        val e = if (type == WaveFeedback.Note.success) VibrationEffect.createWaveform(longArrayOf(0, 14, 80, 18), intArrayOf(0, 170, 0, 255), -1)
        else VibrationEffect.createWaveform(longArrayOf(0, 16, 60, 16, 60, 24), intArrayOf(0, 220, 0, 220, 0, 255), -1)
        runCatching { v.vibrate(e) }
    }

    override fun after(ms: Long, f: () -> Unit) { main.postDelayed(f, ms) }
}
