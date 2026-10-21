package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.core.calc.RandomNumberGenerator
import com.naeilo.app.core.calc.WaveEngine
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import com.naeilo.app.ui.wave.WaveDraw
import com.naeilo.app.ui.wave.WaveFeedback
import com.naeilo.app.ui.wave.WaveResult
import com.naeilo.app.ui.wave.WaveRun
import java.io.File
import kotlin.math.min
import kotlin.test.Test

// 숫자 맞추기 W (파도 타기 한 판 다시 돌리기): tools/num_parity/w/wsrc.py 가 꺼낸 Swift 와 같은 입력으로 kotlin.json 을 쓴다.
// 같은 시드(SplitMix64)·라인·프레임 간격·톡 프레임으로 끝까지 굴려 프레임마다 엔진·연출(WaveFX)·곡선 값(WaveDraw)·이벤트·소리·진동 기록을 남긴다.
// 화면 루프 순서는 WavePlayView 의 LaunchedEffect(run)·tap 과 같다 (아래 frame·tap). 비교는 parity.py check (차이 0)
class WParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/w")

    /** wsrc.py 의 SplitMix64 와 같은 생성기 */
    private class SplitMix64(var s: ULong) : RandomNumberGenerator {
        override fun next(): ULong {
            s += 0x9E3779B97F4A7C15uL
            var z = s
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
            z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
            return z xor (z shr 31)
        }
    }

    /** 소리·진동 기록 (Swift 대역과 같은 글자). asyncAfter 는 명령줄 Swift 에서 돌지 않으니 after 도 적지 않는다 */
    private class Rec : WaveFeedback.Out {
        val log = ArrayList<String>()
        override fun flag(key: String, def: Boolean) = def
        override fun session() {}
        override fun player(wav: ByteArray): WaveFeedback.Player = object : WaveFeedback.Player {
            override fun prepareToPlay() {}
            override var currentTime: Double = 0.0
            override fun play() { log.add("sound:${wav.size}") }
        }
        override fun impact(style: WaveFeedback.Style, intensity: Double) { log.add("impact:${style.name}:$intensity") }
        override fun notify(type: WaveFeedback.Note) { log.add("note:${type.name}") }
        override fun after(ms: Long, f: () -> Unit) {}
    }

    private fun enc(x: WaveEngine.Event): List<Any> = when (x) {
        is WaveEngine.Event.board -> listOf("board", x.day, x.perfect)
        is WaveEngine.Event.leave -> listOf("leave", x.day, x.gain, x.points)
        is WaveEngine.Event.splash -> listOf("splash", x.day, x.drop, x.hearts)
        is WaveEngine.Event.calm -> listOf("calm", x.day)
        is WaveEngine.Event.streak -> listOf("streak", x.n)
        is WaveEngine.Event.fever -> listOf("fever", x.on)
        is WaveEngine.Event.flip -> listOf("flip", x.n)
        is WaveEngine.Event.land -> listOf("land", x.flips, x.combo, x.points)
        is WaveEngine.Event.item -> listOf("item", x.item.name)
        is WaveEngine.Event.time -> listOf("time", x.s)
        is WaveEngine.Event.saved -> listOf("saved", x.day)
        is WaveEngine.Event.mission -> listOf("mission", x.m.name)
        is WaveEngine.Event.end -> listOf("end", x.why.name)
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun wStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity w: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val cases = ArrayList<Any?>()
        var frames = 0
        for (c in inp["cases"] as List<Map<String, Any?>>) {
            val rng = SplitMix64((c["seed"] as Double).toLong().toULong())
            val line = (c["line"] as List<List<String>>).map { WaveEngine.Pt(d = it[0], v = it[1].toDouble()) }
            val course = WaveEngine.course(from = line, length = (c["len"] as Double).toInt(), using = rng)
            if (course == null) { cases.add(mapOf("course" to "none")); continue }
            val rec = Rec()
            val run = WaveRun(WaveFeedback(rec))
            var ended: WaveEngine.EndReason? = null
            run.finish = { _, why -> ended = why }
            run.g = WaveEngine(course = course, using = rng)
            val e0 = run.g!!
            val o = LinkedHashMap<String, Any?>()
            o["island"] = course.island.name; o["level"] = course.level.name; o["start"] = course.startDate; o["count"] = course.count
            o["items"] = e0.items.keys.sorted().map { listOf(it, e0.items.getValue(it).name) }
            o["missions"] = e0.missions.map { it.name }; o["splashDrop"] = e0.splashDrop
            val evs = ArrayList<List<Any>>()
            // 한 걸음 (WavePlayView 루프: dt 자르기 → tick → 연출 → handle). 끝났으면 false
            fun frame(raw: Double): Boolean {
                val e = run.g; if (e == null || e.isOver) return false
                val dt = min(0.1, raw)
                val ev = e.tick(dt)
                evs.addAll(ev.map(::enc))
                run.fx.step(dt, riding = e.riding, rising = WaveDraw.rising(e))
                run.handle(ev)
                return true
            }
            // 톡 (규칙 카드는 이미 닫힘)
            fun tap() {
                val e = run.g ?: return; if (e.isOver) return
                val ev = if (e.riding) e.leave() else e.board()
                evs.addAll(ev.map(::enc))
                run.handle(ev)
            }
            val f = LinkedHashMap<String, ArrayList<Any?>>()
            val taps = (c["taps"] as List<Double>).map { it.toInt() }.toSet()
            for ((i, s) in (c["dts"] as List<String>).withIndex()) {
                rec.log.clear(); evs.clear()
                if (!frame(s.toDouble())) break
                if (i in taps) tap()
                val e = run.g!!; val fx = run.fx; val p = WaveDraw.pos(e)
                val row = listOf(
                    "day" to e.day, "score" to e.score, "hearts" to e.hearts, "streak" to e.streak, "boardedAt" to (e.boardedAt ?: -1),
                    "elapsed" to e.elapsed, "clock" to e.clock, "extra" to e.extra, "feverLeft" to e.feverLeft, "air" to e.air,
                    "flips" to e.flips, "combo" to e.combo, "beacon" to e.beacon, "slow" to e.slow, "boost" to e.boost, "vest" to e.vest, "dolphin" to e.dolphin,
                    "over" to e.isOver, "timeLeft" to e.timeLeft,
                    "fxFlip" to fx.flip, "fxDizzy" to fx.dizzy, "fxBoarded" to fx.boarded, "fxCalm" to fx.calm, "fxAngle" to fx.angle, "fxTurn" to fx.turn,
                    "fxTimeFlash" to fx.timeFlash, "fxTimeGot" to fx.timeGot, "fxRising" to fx.rising, "pose" to fx.pose(riding = e.riding).name,
                    "pop" to (fx.pop?.text ?: ""), "popLife" to (fx.pop?.life ?: -1.0), "dots" to fx.dots.size,
                    "pos" to p, "val" to WaveDraw.value(e, p), "rising" to WaveDraw.rising(e),
                    "ev" to evs.toList(), "fb" to rec.log.toList(),
                )
                for ((k, v) in row) f.getOrPut(k) { ArrayList() }.add(v)
                frames++
            }
            o["frames"] = f
            val e = run.g!!
            o["rides"] = run.rides.map { listOf(it.from, it.to, it.gain, it.splash) }
            o["ended"] = ended?.name ?: "none"
            val r = WaveResult(course = e.course, score = e.score, hearts = e.hearts, rides = run.rides.toList(), why = ended ?: WaveEngine.EndReason.time)
            r.missions = e.missions.map { WaveResult.MissionRow(it.title, e.missionsDone.contains(it)) }
            o["result"] = mapOf(
                "stars" to r.stars, "headline" to r.headline, "dateLine" to r.dateLine,
                "missions" to r.missions.map { listOf(it.title, it.done) },
                "counts" to listOf(e.perfectCount, e.maxStreak, e.flipCount, e.splashCount, e.feverCount, e.got.size),
            )
            cases.add(o)
        }
        dir.mkdirs()
        dir.resolve("kotlin.json").writeText(plainToJson(mapOf("cases" to cases)).toString())
        println("num_parity w: 판 ${cases.size} · 프레임 $frames")
    }
}
