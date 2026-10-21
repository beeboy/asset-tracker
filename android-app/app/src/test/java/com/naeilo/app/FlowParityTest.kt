package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.data.DrnkSample
import com.naeilo.app.data.FlowSeries
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import java.io.File
import kotlin.test.Test

// 숫자 맞추기 흐름 탭: tools/num_parity/flow/flowsrc.py 가 꺼낸 Swift 와 같은 입력으로 kotlin.json 을 쓴다.
// DRNK 샘플 캔들(시드 고정)·추세 점·추세 띠·지지저항·패턴. 비교는 parity.py check
class FlowParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/flow")

    private fun dump(s: FlowSeries): Map<String, Any?> = linkedMapOf(
        "candles" to s.candles.map { listOf(it.o, it.h, it.l, it.c, it.v) },
        "pivots" to s.pivotSets.map { ps -> ps.map { listOf(it.i.toDouble(), it.price, if (it.high) 1.0 else 0.0) } },
        "trends" to s.trends.map { ts -> ts.map { listOf(it.from.toDouble(), it.to.toDouble(), it.dir.toDouble()) } },
        "levels" to s.levels.map { listOf(it.id.toDouble(), it.price, it.from.toDouble(), it.touches.toDouble(), if (it.support) 1.0 else 0.0) },
        "patterns" to s.patterns.map { linkedMapOf("id" to it.id.toDouble(), "kind" to it.kind.name, "from" to it.from.toDouble(), "to" to it.to.toDouble(), "price" to it.price, "below" to it.below, "strong" to it.strong) },
    )

    @Suppress("UNCHECKED_CAST")
    @Test fun flowStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity flow: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, List<Double>>
        val out = LinkedHashMap<String, Any?>()
        for ((k, r) in inp) out[k] = dump(FlowSeries(candles = DrnkSample.candles(r)))
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity flow: ${inp.keys}")
    }
}
