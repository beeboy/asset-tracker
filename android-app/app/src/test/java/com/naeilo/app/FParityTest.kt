package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Symbol
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import com.naeilo.app.ui.hold.HoldingEdit
import com.naeilo.app.core.swFmt
import com.naeilo.app.core.swFmtFrac
import com.naeilo.app.core.swFmtNoGroup
import java.io.File
import kotlin.test.Test

// 숫자 맞추기 F (종목): tools/num_parity/f/fsrc.py 가 꺼낸 Swift 와 같은 입력·같은 차례로 돌려 kotlin.json 을 쓴다.
// 매매·고치기 셈과 글(HoldingEdit), 만원·가격 글, 숫자 글(.formatted 세 가지). 비교는 parity.py check (차이 0)
class FParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/f")

    @Suppress("UNCHECKED_CAST")
    @Test fun fStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity f: 입력 없음 (parity.py gen 먼저)"); return }
        // 앱 언어는 한국어로 (AppModel.en 은 Context 가 필요해 값만 넣는다)
        val lz = AppModel::class.java.getDeclaredField("en\$delegate").apply { isAccessible = true }.get(null)
        lz.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(lz, false)
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val syms = listOf(Symbol("AAPL", "애플", "NASDAQ", 187.25, Currency.usd, "", ""), Symbol("005930", "삼성전자", "KOSPI", 71400.0, Currency.krw, "", ""))
        val krw = { s: Symbol, x: Double -> if (s.currency == Currency.usd) x * 1380.5 else x }
        val out = LinkedHashMap<String, Any?>()
        out["edit"] = syms.flatMap { s -> (inp["cases"] as List<List<Any?>>).map { c ->
            val r = HoldingEdit.compute(krw, s, c[0] as String, c[1] as String, c[2] as String, c[3] as Double, c[4] as Double)
            mapOf("qi" to r.qi, "pi" to r.pi, "q1" to r.q1, "p1" to r.p1, "bad" to r.bad, "note" to r.note)
        } }
        out["initial"] = syms.flatMap { s -> (inp["holds"] as List<List<Double>>).flatMap { h -> listOf("fix", "buy").map { md -> val x = HoldingEdit.initial(md, Holding(s.id, h[0], h[1]), s); listOf(x.first, x.second) } } }
        val xs = inp["nums"] as List<Double>
        out["man"] = xs.map { AppModel.man(it) }
        out["price"] = syms.flatMap { s -> xs.map { AppModel.price(s, it) } }
        out["fmt"] = xs.map { listOf(it.swFmt(), it.swFmtNoGroup(), "$" + it.swFmtFrac(2)) }
        out["breakEven"] = (inp["needs"] as List<Double>).map { val (a, b) = com.naeilo.app.ui.hold.breakEven(it); listOf(a, b) }
        out["needText"] = (inp["needs"] as List<Double>).map { com.naeilo.app.ui.hold.needText(100.0 * (1 + it), 100.0) }
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity f: 고치기 ${(out["edit"] as List<*>).size}")
    }
}
