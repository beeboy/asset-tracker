package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.data.LineClock
import com.naeilo.app.data.Lines
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import java.io.File
import kotlin.test.Test

// 숫자 맞추기 I (인물 대사 고르기): tools/num_parity/i/isrc.py 가 꺼낸 Swift 와 같은 입력으로 kotlin.json 을 쓴다.
// 키마다 첫 줄 번호(FNV-1a)와, 차례 올리기·고르기·미리 보기·넘기기를 섞은 같은 순서의 결과. 비교는 parity.py check (차이 0)
class IParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/i")

    private fun int(x: Any?): Int = (x as Double).toInt()

    @Suppress("UNCHECKED_CAST")
    @Test fun iStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity i: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val out = LinkedHashMap<String, Any?>()
        // 대사 표·차례는 저장소 대신 입력으로 (Swift 쪽도 같다). 차례의 절대값은 결과에 들지 않는다
        Lines.all = inp["lines"] as Map<String, List<String>>
        LineClock.pos = emptyMap()
        out["start"] = (inp["starts"] as List<List<Any?>>).map { Lines.start(it[0] as String, int(it[1])) }
        out["ops"] = (inp["ops"] as List<Map<String, Any?>>).map { o ->
            val key = o["key"] as? String ?: ""; val vars = o["vars"] as? Map<String, String> ?: emptyMap(); val n = o["n"]?.let { int(it) } ?: 0
            when (o["op"] as String) {
                "next" -> { LineClock.shared.next(); "" }
                "pick" -> Lines.pick(key, vars, salt = n)
                "peek" -> Lines.peek(key, vars, n)
                "skip" -> { Lines.skip(key, n); "" }
                else -> Lines.count
            }
        }
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity i: 첫 줄 ${(inp["starts"] as List<*>).size} · 대사 동작 ${(inp["ops"] as List<*>).size}")
    }
}
