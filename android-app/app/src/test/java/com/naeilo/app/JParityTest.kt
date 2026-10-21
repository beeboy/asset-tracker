package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.core.game.BrickGame
import com.naeilo.app.core.game.GameFx
import com.naeilo.app.core.game.GameLevel
import com.naeilo.app.core.game.GameLines
import com.naeilo.app.core.game.GameMotion
import com.naeilo.app.core.game.GameRecord
import com.naeilo.app.core.game.GameRng
import com.naeilo.app.core.game.GameSound
import com.naeilo.app.core.game.GameStage
import com.naeilo.app.core.game.Gravity
import com.naeilo.app.core.game.Impact
import com.naeilo.app.core.game.Notice
import com.naeilo.app.core.game.Orientation
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import java.io.File
import kotlin.test.Test

// 숫자 맞추기 J (판 깨기 게임 엔진): tools/num_parity/j/jsrc.py 가 꺼낸 Swift 와 같은 입력(시드·단계·난이도·인물·물건·판 움직임 대본)으로
// 프레임마다 공·판·물건 위치, 깨진 칸, 점수·생명, 소리·진동 차례, 말풍선, 기록(game.v1) 읽고 쓰기를 kotlin.json 에 쓴다.
// 비교는 parity.py check (위치 ballXY·padX·dropXY 만 상대 1e-12, 나머지 차이 0)
class JParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/j")

    /** jsrc.py 의 SplitMix64 와 같은 생성기 */
    private class SplitMix64(var s: Long) : GameRng {
        override fun next(): Long {
            s += 0x9E3779B97F4A7C15uL.toLong()
            var z = s
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
            return z xor (z ushr 31)
        }
    }

    /** 소리·진동 기록 (Swift 대역과 같은 글자) */
    private class Rec : GameFx {
        var frame = 0
        val sounds = mutableListOf<List<Any>>()
        val haptics = mutableListOf<List<Any>>()
        override fun start() {}
        override fun play(k: GameSound.Kind) { sounds.add(listOf(frame, k.toString())) }
        override fun prepare(style: Impact) { haptics.add(listOf(frame, "prepare:${style.name}")) }
        override fun impact(style: Impact, intensity: Double) { haptics.add(listOf(frame, if (intensity == 1.0) style.name else "${style.name}:$intensity")) }
        override fun notify(type: Notice) { haptics.add(listOf(frame, type.name)) }
    }

    /** CMMotionManager 대역과 같은 기울기 */
    private class FakeMotion : GameMotion {
        var g: Gravity? = null
        var orient = Orientation.portrait
        override val isDeviceMotionAvailable = true
        override var isDeviceMotionActive = false
        override var deviceMotionUpdateInterval = 0.0
        override fun startDeviceMotionUpdates() { isDeviceMotionActive = true }
        override fun stopDeviceMotionUpdates() { isDeviceMotionActive = false }
        override val gravity: Gravity? get() = if (isDeviceMotionActive) g else null
        override fun orientation() = orient
    }

    private fun Any?.i(): Int = (this as Double).toInt()
    private fun spec(s: BrickGame.Special?): Int = s?.ordinal ?: -1

    private fun aim(g: BrickGame, k: Int, lazy: Boolean): Double? {
        var best: BrickGame.Ball? = null
        for (b in g.balls) if (!b.stuck && b.vy < 0) { if (best == null || b.y < best.y) best = b }
        val b = best ?: return null
        return b.x + ((k / 40) % 5 - 2).toDouble() * (if (lazy) 2.7 else 0.9)
    }

    @Suppress("UNCHECKED_CAST")
    private fun game(sc: Map<String, Any?>): Map<String, Any?> {
        GameLines.bag.clear()
        GameLines.linePick = { _, _ -> null }
        val rec = Rec(); val mo = FakeMotion(); val q = mutableListOf<() -> Unit>()
        val g = BrickGame(stage = GameStage.all[sc["stage"].i()], level = GameLevel.valueOf(sc["level"] as String), player = sc["player"] as String,
            skill = sc["skill"] as String, items = sc["items"] as List<String>, tilt = sc["tilt"] as Boolean, assist = sc["assist"] as Boolean,
            filled = sc["filled"].i(), floor = sc["floor"].i(), yesterday = sc["yesterday"].i(), chain = sc["chain"].i(), lives = (sc["lives"] as Double?)?.toInt(),
            fx = rec, motion = mo, rng = SplitMix64(sc["seed"].i().toLong()), post = { q.add(it) })
        g.friends = sc["friends"] as List<String>
        val r = LinkedHashMap<String, Any?>()
        r["init"] = mapOf("hp" to g.hp.toList(), "kind" to g.kind.toList(), "special" to g.special.map { spec(it) }, "group" to g.group.toList(),
            "bigs" to g.bigs.map { listOf(it.cells, it.shape, it.hp) }, "exitCell" to g.exitCell, "total" to g.total, "target" to g.target,
            "stacked" to g.stacked, "lives" to g.lives, "net" to g.net, "speech" to g.speech, "guest" to g.guest, "guestLine" to g.guestLine)
        g.begin()
        var now = 800000000.0
        val step = 1.0 / sc["den"].i().toDouble()
        val ctrl = sc["ctrl"] as String
        val events = sc["events"] as List<List<Any?>>
        g.tick(now)
        val f = mutableListOf<List<Int>>(); val padX = mutableListOf<Double>(); val ballXY = mutableListOf<List<List<Double>>>()
        val dropXY = mutableListOf<List<List<Double>>>(); val dropId = mutableListOf<List<String>>()
        val cells = mutableListOf<List<Int>>(); val bigs = mutableListOf<List<Int>>()
        val speech = mutableListOf<List<Any>>(); val toast = mutableListOf<List<Any>>(); val guest = mutableListOf<List<Any>>()
        val hp = g.hp.toMutableList(); val rev = g.revealed.toMutableList(); val sp = g.special.map { spec(it) }.toMutableList()
        val bg = g.bigs.map { listOf(it.hp, if (it.revealed) 1 else 0) }.toMutableList()
        var sU = g.speechUntil; var tU = -1.0; var tS = ""; var gU = g.guestUntil
        var ended = 0
        for (k in 1..sc["frames"].i()) {
            rec.frame = k
            for (ev in events) {
                if (ev[0].i() != k) continue
                when (ev[1] as String) {
                    "start" -> g.startPlay()
                    "pause" -> g.pause()
                    "resume" -> g.resume()
                    "launch" -> g.launch()
                    "hitch" -> now += ev[2] as Double
                    "orient" -> mo.orient = listOf(Orientation.portrait, Orientation.landscapeLeft, Orientation.landscapeRight)[ev[2].i()]
                    "drag" -> g.drag(ev[2] as Double)
                }
            }
            if (ctrl == "track" || (ctrl == "lazy" && k % 3 == 0)) aim(g, k, ctrl == "lazy")?.let { g.drag(it) }
            if (ctrl == "tilt") aim(g, k, false)?.let { x -> mo.g = Gravity((x - 20) / 50, (x - 20) / 50) }
            now += step
            g.tick(now)
            val run = q.toList(); q.clear(); run.forEach { it() }
            f.add(listOf(g.phase.ordinal, g.score, g.broken, g.lives, g.combo, g.balls.size, g.drops.size, if (g.shield) 1 else 0, g.net, g.picked.values.sum()))
            padX.add(g.paddleX + 100)
            ballXY.add(g.balls.map { listOf(it.x + 100, it.y + 100, it.vx + 100, it.vy + 100) })
            dropXY.add(g.drops.map { listOf(it.x + 100, it.y + 100) })
            dropId.add(g.drops.map { it.id })
            for (i in 0 until 1000) {
                if (g.hp[i] == hp[i] && g.revealed[i] == rev[i] && spec(g.special[i]) == sp[i]) continue
                hp[i] = g.hp[i]; rev[i] = g.revealed[i]; sp[i] = spec(g.special[i])
                cells.add(listOf(k, i, hp[i], if (rev[i]) 1 else 0, sp[i]))
            }
            for ((i, b) in g.bigs.withIndex()) {
                val now2 = listOf(b.hp, if (b.revealed) 1 else 0)
                if (now2 != bg[i]) { bg[i] = now2; bigs.add(listOf(k, i, b.hp, now2[1])) }
            }
            if (g.speechUntil != sU) { sU = g.speechUntil; speech.add(listOf(k, g.speech, g.mood.rawValue)) }
            if ((g.toast?.first ?: "") != tS || (g.toast?.second ?: -1.0) != tU) { tS = g.toast?.first ?: ""; tU = g.toast?.second ?: -1.0; toast.add(listOf(k, tS)) }
            if (g.guestUntil != gU) { gU = g.guestUntil; guest.add(listOf(k, g.guest, g.guestLine)) }
            if (g.phase == BrickGame.Phase.cleared || g.phase == BrickGame.Phase.over) { ended += 1; if (ended > 3) break }
        }
        r["f"] = f; r["padX"] = padX; r["ballXY"] = ballXY; r["dropXY"] = dropXY; r["dropId"] = dropId
        r["cells"] = cells; r["bigs"] = bigs; r["speech"] = speech; r["toast"] = toast; r["guest"] = guest
        r["sounds"] = rec.sounds; r["haptics"] = rec.haptics
        r["end"] = mapOf("t" to g.t, "effects" to g.effects.keys.sorted().map { listOf(it, g.effects[it]!!) }, "picked" to g.picked.keys.sorted().map { listOf(it, g.picked[it]!!) },
            "active" to g.activeEffects, "rewindUsed" to g.rewindUsed, "paddleW" to g.paddleW, "speed" to g.speed, "dark" to g.dark,
            "guestShown" to g.guestShown, "speaking" to g.speaking)
        return r
    }

    private fun recOut(r: GameRecord): Map<String, Any?> = mapOf(
        "cleared" to r.cleared.mapValues { it.value.sorted() }, "best" to r.best, "losses" to r.losses, "level" to r.level.rawValue, "tilt" to r.tilt,
        "player" to r.player, "plays" to (r.plays ?: emptyMap()).mapValues { e -> e.value.map { listOf(it.score, it.p) } },
        "total" to GameLevel.entries.map { r.total(it) },
        "open" to GameLevel.entries.map { lv -> (0 until 12).map { r.open(it, lv) } },
        "loss" to GameLevel.entries.map { lv -> (0 until 12).map { r.lossCount(it, lv) } })

    @Suppress("UNCHECKED_CAST")
    @Test fun jStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity j: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val out = LinkedHashMap<String, Any?>()
        val games = (inp["games"] as List<Map<String, Any?>>).map { game(it) }
        out["games"] = games
        out["records"] = (inp["records"] as List<Map<String, Any?>>).map { rc ->
            val rec = NJson.decodeFromString(GameRecord.serializer(), rc["json"] as String)
            val o = LinkedHashMap<String, Any?>()
            o["read"] = recOut(rec)
            val enc = (jsonToPlain(NJson.parseToJsonElement(NJson.encodeToString(GameRecord.serializer(), rec))) as Map<String, Any?>).toMutableMap()
            enc["cleared"] = (enc["cleared"] as Map<String, List<Double>>).mapValues { e -> e.value.sorted() }
            o["enc"] = enc
            o["isBest"] = (rc["finishes"] as List<List<Any?>>).map { x ->
                rec.finish(x[0].i(), GameLevel.valueOf(x[1] as String), score = x[2].i(), won = x[3] as Boolean, player = x[4] as String)
            }
            o["after"] = recOut(rec)
            o
        }
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity j: 판 ${games.size} · 프레임 ${games.sumOf { (it["f"] as List<*>).size }}")
    }
}
