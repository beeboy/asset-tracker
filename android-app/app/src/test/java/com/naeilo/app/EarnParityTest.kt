package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.data.Earnings
import com.naeilo.app.net.jsonToPlain
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

// 내 몫 실적: Earnings.kt 가 tools/earn_fixtures.json 기대값(= tools/earn_calc.mjs, 아이폰 earn_parity 와 같은 기준)과 같은지 (금액 1e-9 이내).
// DRNK 견본이 data/earnings/DRNK.json 과 같은지도 본다.
class EarnParityTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/earn_fixtures.json").exists() }

    private fun same(a: Any?, b: Any?): Boolean = when (b) {
        null -> a == null
        is Boolean -> a == b
        is Number -> a is Number && abs(a.toDouble() - b.toDouble()) < 1e-9
        is String -> a == b
        is List<*> -> a is List<*> && a.size == b.size && a.indices.all { same(a[it], b[it]) }
        is Map<*, *> -> a is Map<*, *> && b.all { (k, v) -> same(a[k], v) }
        else -> false
    }

    private fun dict(s: Earnings.Summary): Map<String, Any?> = mapOf(
        "symbol" to s.symbol, "currency" to s.currency, "months" to s.months, "label" to s.label,
        "lastYearMonths" to s.lastYearMonths, "pending" to s.pending, "revenue" to s.revenue, "net" to s.net,
        "branches" to s.branches.map { mapOf("k" to it.k, "v" to it.v) },
        "mist" to s.mist, "loss" to s.loss, "short" to s.short,
        "gauge" to s.gauge.map { mapOf("label" to it.label, "value" to it.value, "lastYear" to it.lastYear) },
        "revYoY" to s.revYoY, "netYoY" to s.netYoY, "lastYearNet" to s.lastYearNet, "netText" to s.netText, "netKrw" to s.netKrw,
    )

    @Test fun earnFixtures() {
        val f = NJson.parseToJsonElement(File(root, "tools/earn_fixtures.json").readText()) as JsonObject
        var bad = 0
        for (c in (f["cases"] as kotlinx.serialization.json.JsonArray)) {
            c as JsonObject
            val name = (jsonToPlain(c["name"]) as String)
            val inp = c["input"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.let { NJson.decodeFromJsonElement(Earnings.File.serializer(), it) }
            val got = Earnings.summary(inp, (jsonToPlain(c["qty"]) as Number).toDouble(), (jsonToPlain(c["fx"]) as? Number)?.toDouble(), (jsonToPlain(c["at"]) as? Number)?.toInt())
            val exp = jsonToPlain(c["expect"]) as? Map<*, *>
            val miss = mutableListOf<String>()
            if (exp != null) {
                if (got == null) miss += "null 이면 안 됨"
                else { val d = dict(got); for ((k, v) in exp) if (!same(d[k], v)) miss += "$k 기대 $v 받음 ${d[k]}" }
            } else if (got != null) miss += "null 이어야 함"
            if (miss.isEmpty()) println("통과 $name") else { bad++; println("실패 $name: " + miss.joinToString(" / ")) }
        }
        val drnk = NJson.decodeFromString(Earnings.File.serializer(), File(root, "data/earnings/DRNK.json").readText())
        if (Earnings.drnk != drnk) { bad++; println("실패 DRNK 견본이 data/earnings/DRNK.json 과 다름") } else println("통과 DRNK 견본")
        assertEquals(0, bad)
    }
}
