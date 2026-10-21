package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.data.BlogFile
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.data.Remind
import com.naeilo.app.data.RemindLog
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import java.io.File
import java.time.Instant
import kotlin.test.Test

// 숫자 맞추기 H (미션·리마인드): tools/num_parity/h/hsrc.py 가 꺼낸 Swift 와 같은 입력으로 kotlin.json 을 쓴다.
// 주 이름(ISO 주)·끝난 주·예보 범위 판정·일정 날짜 반복·글 첫 문장. 비교는 parity.py check (차이 0)
class HParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/h")

    private fun at(s: String): Instant = Instant.ofEpochMilli(Math.round(s.toDouble() * 1000))

    @Suppress("UNCHECKED_CAST")
    @Test fun hStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity h: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val out = LinkedHashMap<String, Any?>()
        out["week"] = (inp["ts"] as List<String>).map { Remind.week(at(it)) }
        out["ended"] = (inp["ended"] as List<Map<String, Any?>>).map { c ->
            val w = (c["keys"] as List<String>).associateWith { RemindLog.Wk(lo = 1.0, hi = 2.0, last = 1.5) }
            Remind.endedWeeks(w, now = at(c["now"] as String))
        }
        out["pos"] = (inp["pos"] as List<List<String>>).map { p -> RemindLog.Wk(lo = p[0].toDouble(), hi = p[1].toDouble(), last = p[2].toDouble()).pos }
        out["dates"] = (inp["windows"] as List<List<String>>).map { w -> MarketEvents.rules.map { MarketEvents.dates(it, from = w[0], until = w[1]) } }
        out["lead"] = (inp["bodies"] as List<String>).map { BlogFile.Post(id = "", title = "", cat = "", created = null, body = it).lead }
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity h: 주 ${(inp["ts"] as List<*>).size}")
    }
}
