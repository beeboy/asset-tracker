package com.naeilo.app

import com.naeilo.app.core.AlpacaKeys
import com.naeilo.app.core.LongbridgeKeys
import com.naeilo.app.core.NJson
import com.naeilo.app.core.QuoteFeed
import com.naeilo.app.core.TossDaily
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.WFeed
import com.naeilo.app.core.WLive
import com.naeilo.app.core.WMe
import com.naeilo.app.core.WReward
import com.naeilo.app.core.WSmall
import com.naeilo.app.core.WStory
import com.naeilo.app.core.WSummary
import com.naeilo.app.data.DayRec
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Lot
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.Progress
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

// 아이폰이 쓴 JSON(tools/json_parity/fixtures, Swift JSONEncoder)을 Kotlin 이 읽고 다시 써서 값이 같은지 본다.
// 다시 쓴 JSON 은 parity.out 폴더에 남겨 tools/json_parity/parity.py check 가 Swift 로 다시 읽는다.
class JsonParityTest {
    private val types: Map<String, KSerializer<*>> = mapOf(
        "WSummary" to WSummary.serializer(), "WFeed" to WFeed.serializer(), "WLive" to WLive.serializer(),
        "TossKeys.Keys" to TossKeys.Keys.serializer(), "TossKeys.Token" to TossKeys.Token.serializer(),
        "AlpacaKeys.Keys" to AlpacaKeys.Keys.serializer(), "LongbridgeKeys.Keys" to LongbridgeKeys.Keys.serializer(),
        "TossDaily.File" to TossDaily.File.serializer(), "QuoteFeed.File" to QuoteFeed.File.serializer(),
        "WReward" to WReward.serializer(), "WMe" to WMe.serializer(), "WSmall" to WSmall.serializer(), "WStory" to WStory.serializer(),
        "Progress" to Progress.serializer(), "DayLog" to DayLog.serializer(), "Holding" to Holding.serializer(),
        "Lot" to Lot.serializer(), "DayRec" to DayRec.serializer(), "MeChar" to MeChar.serializer(),
    )

    /** 숫자는 실수로 비교 (Swift 는 2, Kotlin 은 2.0 으로 쓴다) */
    private fun same(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { same(a[it]!!, b[it]!!) }
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        a is JsonNull || b is JsonNull -> a == b
        a is JsonPrimitive && b is JsonPrimitive ->
            if (!a.isString && !b.isString && a.doubleOrNull != null && b.doubleOrNull != null) a.doubleOrNull == b.doubleOrNull else a == b
        else -> false
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun swiftJsonRoundTrip() {
        val fix = File("../../tools/json_parity/fixtures")
        val out = File(System.getProperty("parity.out") ?: "build/json_parity").resolve("kotlin").apply { mkdirs() }
        val files = fix.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        assertTrue(files.size == types.size * 2, "fixtures ${files.size}개 (형 ${types.size} × 2 이어야 함). parity.py gen 을 먼저")
        val bad = mutableListOf<String>()
        for (f in files) {
            val ser = types[f.name.substringBeforeLast(".").substringBeforeLast(".")] as KSerializer<Any?>?
            if (ser == null) { bad += "${f.name}: 형 없음"; continue }
            val text = f.readText()
            val v = try { NJson.decodeFromString(ser, text) } catch (e: Exception) { bad += "${f.name}: 못 읽음 ${e.message?.lineSequence()?.first()}"; continue }
            val back = NJson.encodeToString(ser, v)
            out.resolve(f.name).writeText(back)
            val a = NJson.parseToJsonElement(text); val b = NJson.parseToJsonElement(back)
            if (!same(a, b)) {
                val ka = (a as? JsonObject)?.keys ?: emptySet(); val kb = (b as? JsonObject)?.keys ?: emptySet()
                bad += "${f.name}: 다시 쓴 값이 다름 (Swift 에만 ${ka - kb}, Kotlin 에만 ${kb - ka})"
            }
        }
        if (bad.isNotEmpty()) fail("JSON 서로 읽기 실패 ${bad.size}/${files.size}\n" + bad.joinToString("\n"))
    }
}
