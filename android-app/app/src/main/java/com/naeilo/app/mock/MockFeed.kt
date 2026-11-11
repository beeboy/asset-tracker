package com.naeilo.app.mock

import android.net.Uri
import com.naeilo.app.core.Config
import com.naeilo.app.core.NJson
import com.naeilo.app.core.TossFeed
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.feedJson
import com.naeilo.app.core.isoSec
import com.naeilo.app.core.jArr
import com.naeilo.app.core.jObj
import com.naeilo.app.core.jObjs
import com.naeilo.app.core.pctEncode
import com.naeilo.app.core.uriOrNull
import com.naeilo.app.data.Defaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

// 모의투자 시세 길 두 갈래 (2026-11-05):
// - 개발자 기기(열쇠·주소가 있음): 개발자 서버(server.py)의 /api/mock/…. 일봉은 서버가 묶어 준다.
// - 일반 사용자: 본인 토스 키 + 공용 중계 (아래 MockToss). 실전 주문·계좌·AI 는 개발자 서버에만 있다.
// 호가는 주문하는 순간에만.

/** URLSession 자리: (응답 코드, 본문, Retry-After). 닿지 못하면 IOException (시간 초과는 SocketTimeoutException) */
internal class MockReq(val url: String, val timeout: Double) {
    var method = "GET"
    var body: ByteArray? = null
    val headers = mutableMapOf<String, String>()
    data class Res(val code: Int, val data: ByteArray, val headers: Map<String, String?> = emptyMap()) { fun header(n: String): String? = headers[n] }
    suspend fun send(): Res = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            val ms = (timeout * 1000).toInt()
            c.connectTimeout = ms; c.readTimeout = ms; c.useCaches = false; c.requestMethod = method
            for ((k, v) in headers) c.setRequestProperty(k, v)
            body?.let { b -> c.doOutput = true; c.outputStream.use { it.write(b) } }
            val code = c.responseCode
            val s = if (code in 200..399) c.inputStream else c.errorStream
            Res(code, s?.use { it.readBytes() } ?: ByteArray(0), c.headerFields.keys.filterNotNull().associateWith { c.getHeaderField(it) })
        } finally { c.disconnect() }
    }
}

/** JSONSerialization.data(withJSONObject:) 자리: 글자·숫자·참거짓·사전만 */
internal fun jsonBody(o: Map<String, Any?>): ByteArray {
    fun el(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Map<*, *> -> kotlinx.serialization.json.JsonObject(v.entries.associate { it.key.toString() to el(it.value) })
        is List<*> -> kotlinx.serialization.json.JsonArray(v.map { el(it) })
        else -> JsonPrimitive(v.toString())
    }
    return NJson.encodeToString(JsonElement.serializer(), el(o)).toByteArray(Charsets.UTF_8)
}

object MockFeed {   // ios: Mock/MockFeed.swift:8
    class Fail(val why: String) : Exception(why)   // ios: Mock/MockFeed.swift:9

    /** 개발자 서버: Tailscale 주소(TossFeed.base)가 있으면 그쪽, 없으면 공용 중계 toss.naeilo.com 뒤 (Caddy 가 /api/mock 만 개발자 서버로 넘긴다) */
    val base: String? get() = TossFeed.base ?: (if (token == null) null else Config.tossRelay)   // ios: Mock/MockFeed.swift:12
    /** 일반 사용자 길: 개발자 서버가 없고 내 토스 키가 있을 때 */
    val user: Boolean get() = base == null && TossKeys.on   // ios: Mock/MockFeed.swift:14

    /** 개발자 서버 열쇠. naeilo://mock?t=… 로 이 기기에만 넣는다. 저장소·앱 코드에는 없다 */
    const val tokenKey = "mock_dev_token"   // ios: Mock/MockFeed.swift:17
    val token: String? get() = Defaults.prefs.getString(tokenKey, null)?.let { if (it.isEmpty()) null else it }   // ios: Mock/MockFeed.swift:18
    /** 쿼리 t 가 16자 이상 영숫자·-·_ 이면 저장, 아니면 지운다 */
    fun setToken(from: Uri) {   // ios: Mock/MockFeed.swift:20
        val t = runCatching { from.getQueryParameter("t") }.getOrNull() ?: ""   // ios: Mock/MockFeed.swift:21
        val ok = t.length >= 16 && t.all { (it.code < 128 && it.isLetterOrDigit()) || it.toString() == "-" || it.toString() == "_" }   // ios: Mock/MockFeed.swift:22
        if (ok) Defaults.prefs.edit().putString(tokenKey, t).apply() else Defaults.prefs.edit().remove(tokenKey).apply()   // ios: Mock/MockFeed.swift:23
    }

    /** 서버가 토스를 부르는 길(시세·호가·일정)은 모두 여기를 지난다. 호출 한도: 초당 N번으로 줄 세우고,
     *  한도 응답(429)이면 Retry-After(없으면 1·2·4초) 기다렸다 3번까지 다시 부른다 */
    private suspend fun get(path: String, timeout: Double): Map<String, Any?> {   // ios: Mock/MockFeed.swift:28
        val b = base; val url = b?.let { uriOrNull(it + path) } ?: throw Fail(why = "개발자 서버 주소·열쇠 없음 (naeilo://mock?t=…)")   // ios: Mock/MockFeed.swift:29
        val req = MockReq(url, timeout)   // ios: Mock/MockFeed.swift:30
        req.headers["User-Agent"] = "naeilo-ios-mock/0.1"   // ios: Mock/MockFeed.swift:31
        token?.let { req.headers["X-Naeilo-Dev"] = it }   // ios: Mock/MockFeed.swift:32
        var res = MockReq.Res(0, ByteArray(0)); var code = 0   // ios: Mock/MockFeed.swift:33
        for (attempt in 0..3) {   // ios: Mock/MockFeed.swift:34
            MockGate.wait()   // ios: Mock/MockFeed.swift:35
            try { res = req.send() }   // ios: Mock/MockFeed.swift:36
            catch (e: CancellationException) { throw e }   // ios: Mock/MockFeed.swift:39
            catch (e: IOException) {   // ios: Mock/MockFeed.swift:37
                // 취소는 서버 문제가 아니라 일이 취소된 것 (화면·앱 전환). 오류가 아닌 취소로 올린다
                throw Fail(why = if (e is SocketTimeoutException) "서버 응답 없음 (${timeout.toInt()}초)" else "서버 연결 실패 (${e.javaClass.simpleName})")   // ios: Mock/MockFeed.swift:40
            }
            code = res.code.coerceAtLeast(0)   // ios: Mock/MockFeed.swift:42
            if (code != 429) break   // ios: Mock/MockFeed.swift:43
            if (attempt == 3) throw Fail(why = "토스 호출 한도 · 3번 다시 불러도 막힘")   // ios: Mock/MockFeed.swift:44
            val after = res.header("Retry-After")?.toDoubleOrNull() ?: 2.0.pow(attempt.toDouble())   // ios: Mock/MockFeed.swift:45
            delay((min(after, 10.0) * 1000).toLong())   // ios: Mock/MockFeed.swift:46
        }
        if (code == 401 || code == 403) throw Fail(why = "개발자 서버 열쇠가 맞지 않아요")   // ios: Mock/MockFeed.swift:48
        if (code != 200) throw Fail(why = if (code == 404) "서버에 모의투자 경로 없음 (server.py 새 판 필요)" else "서버 응답 $code")   // ios: Mock/MockFeed.swift:49
        val o = jObj(feedJson(res.data)) ?: throw Fail(why = "응답을 읽지 못함")   // ios: Mock/MockFeed.swift:50
        (o["error"] as? String)?.let { throw Fail(why = "서버: " + it) }   // ios: Mock/MockFeed.swift:51
        return o   // ios: Mock/MockFeed.swift:52
    }

    /** AI 조언 (서버가 Claude 를 부른다. 키·한도·쓴 돈은 서버에). 실패해도 쓴 돈이 있으면 함께 온다 */
    suspend fun ai(kind: String, prompt: String, limits: Map<String, Double>): Map<String, Any?> {   // ios: Mock/MockFeed.swift:56
        val b = base; val url = b?.let { uriOrNull(it + "/api/mock/ai") } ?: throw Fail(why = "개발자 서버 주소·열쇠 없음 (naeilo://mock?t=…)")   // ios: Mock/MockFeed.swift:57
        val req = MockReq(url, 130.0)   // ios: Mock/MockFeed.swift:58
        req.method = "POST"   // ios: Mock/MockFeed.swift:59
        req.headers["Content-Type"] = "application/json"   // ios: Mock/MockFeed.swift:60
        req.headers["User-Agent"] = "naeilo-ios-mock/0.1"   // ios: Mock/MockFeed.swift:61
        token?.let { req.headers["X-Naeilo-Dev"] = it }   // ios: Mock/MockFeed.swift:62
        req.body = jsonBody(mapOf("kind" to kind, "prompt" to prompt, "limits" to limits))   // ios: Mock/MockFeed.swift:63
        val res: MockReq.Res   // ios: Mock/MockFeed.swift:64
        try { res = req.send() }   // ios: Mock/MockFeed.swift:65
        catch (e: IOException) { throw Fail(why = if (e is SocketTimeoutException) "AI 응답 없음 (130초)" else "서버 연결 실패 (${e.javaClass.simpleName})") }   // ios: Mock/MockFeed.swift:66
        val code = res.code.coerceAtLeast(0)   // ios: Mock/MockFeed.swift:67
        if (code == 401 || code == 403) throw Fail(why = "개발자 서버 열쇠가 맞지 않아요")   // ios: Mock/MockFeed.swift:68
        if (!(code == 200 || code == 500)) throw Fail(why = if (code == 404) "서버에 AI 경로 없음 (server.py 새 판 필요)" else "서버 응답 $code")   // ios: Mock/MockFeed.swift:69
        return jObj(feedJson(res.data)) ?: throw Fail(why = "응답을 읽지 못함")   // ios: Mock/MockFeed.swift:70,71
    }

    /** AI 쓴 돈·한도 */
    suspend fun wallet(limits: Map<String, Double>): Map<String, Any?> {   // ios: Mock/MockFeed.swift:75
        val q = limits.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }   // ios: Mock/MockFeed.swift:76
        return get("/api/mock/wallet?" + q, timeout = 6.0)   // ios: Mock/MockFeed.swift:77
    }

    fun num(v: Any?): Double? = when (v) {   // ios: Mock/MockFeed.swift:80
        is Double -> v   // ios: Mock/MockFeed.swift:81
        is String -> v.toDoubleOrNull()   // ios: Mock/MockFeed.swift:82
        is Number -> v.toDouble()   // ios: Mock/MockFeed.swift:83
        else -> null   // ios: Mock/MockFeed.swift:84
    }

    fun enc(s: String): String = pctEncode(s)   // ios: Mock/MockFeed.swift:87

    private fun barRow(r: List<Any?>): MockBar? {
        if (r.size < 6) return null; val d = r[0] as? String; val op = num(r[1]); val h = num(r[2]); val l = num(r[3]); val cl = num(r[4])   // ios: Mock/MockFeed.swift:121,151
        if (d == null || op == null || h == null || l == null || cl == null) return null   // ios: Mock/MockFeed.swift:121,151
        return MockBar(d = d, o = op, h = h, l = l, c = cl, v = num(r[5]) ?: 0.0)   // ios: Mock/MockFeed.swift:122,152
    }

    data class Daily(val bars: Map<String, List<MockBar>>, val errors: Map<String, String>)

    /** 일봉: 100종목씩 나눠 묻는다. progress(받은 종목 수). 실패한 종목은 errors 에 이유.
     *  skip: 이미 받은 묶음 (시작 번호) — 멈췄다 이어 받을 때. batch: 묶음 하나를 받을 때마다 (시작 번호, 일봉, 실패) */
    suspend fun daily(codes: List<String>, count: Int, skip: Set<Int> = emptySet(), progress: suspend (Int) -> Unit,   // ios: Mock/MockFeed.swift:91
                      batch: (suspend (Int, Map<String, List<MockBar>>, Map<String, String>) -> Unit)? = null): Daily {   // ios: Mock/MockFeed.swift:92
        val bars = mutableMapOf<String, List<MockBar>>(); val errors = mutableMapOf<String, String>()   // ios: Mock/MockFeed.swift:93
        var done = 0   // ios: Mock/MockFeed.swift:94
        for (i in 0 until codes.size step 100) {   // ios: Mock/MockFeed.swift:95
            val part = codes.subList(i, min(codes.size, i + 100))   // ios: Mock/MockFeed.swift:96
            if (i in skip) { done += part.size; continue }   // ios: Mock/MockFeed.swift:97
            coroutineContext.ensureActive()   // ios: Mock/MockFeed.swift:98
            if (user) {   // ios: Mock/MockFeed.swift:99
                val (got, errs) = MockToss.daily(part, count = count)   // ios: Mock/MockFeed.swift:100
                // 처음 묶음이 모두 실패면 그만 (키·허용 IP 문제)
                if (got.isEmpty() && i == 0) errs.values.firstOrNull()?.let { throw Fail(why = it) }   // ios: Mock/MockFeed.swift:102
                bars.putAll(got); errors.putAll(errs)   // ios: Mock/MockFeed.swift:103
                batch?.invoke(i, got, errs)   // ios: Mock/MockFeed.swift:104
                done += part.size; progress(done); continue   // ios: Mock/MockFeed.swift:105
            }
            val o: Map<String, Any?>   // ios: Mock/MockFeed.swift:107
            try { o = get("/api/mock/daily?count=$count&symbols=" + enc(part.joinToString(",")), timeout = 70.0) }   // ios: Mock/MockFeed.swift:108
            catch (f: Fail) {   // ios: Mock/MockFeed.swift:109
                // 처음 묶음부터 실패하면 그만 (서버 문제). 중간이면 그 묶음만 이유를 남기고 이어 간다
                if (bars.isEmpty() && i == 0) throw f   // ios: Mock/MockFeed.swift:111
                val errs = mutableMapOf<String, String>()   // ios: Mock/MockFeed.swift:112
                for (c in part) errs[c] = f.why   // ios: Mock/MockFeed.swift:113
                errors.putAll(errs)   // ios: Mock/MockFeed.swift:114
                batch?.invoke(i, emptyMap(), errs)   // ios: Mock/MockFeed.swift:115
                done += part.size; progress(done); continue   // ios: Mock/MockFeed.swift:116
            }
            val got = mutableMapOf<String, List<MockBar>>(); val errs = mutableMapOf<String, String>()   // ios: Mock/MockFeed.swift:118
            for ((c, rows) in jObj(o["bars"]) ?: emptyMap()) {   // ios: Mock/MockFeed.swift:119
                got[c] = (jArr(rows) ?: emptyList()).mapNotNull { r -> jArr(r)?.let { barRow(it) } }   // ios: Mock/MockFeed.swift:120
            }
            for ((c, e) in jObj(o["errors"]) ?: emptyMap()) (e as? String)?.let { errs[c] = it }   // ios: Mock/MockFeed.swift:125
            bars.putAll(got); errors.putAll(errs)   // ios: Mock/MockFeed.swift:126
            batch?.invoke(i, got, errs)   // ios: Mock/MockFeed.swift:127
            done += part.size   // ios: Mock/MockFeed.swift:128
            progress(done)   // ios: Mock/MockFeed.swift:129
        }
        return Daily(bars, errors)   // ios: Mock/MockFeed.swift:131
    }

    data class Detail(val day: String, val minutes: List<MockBar>, val daily: List<MockBar>)

    /** 종목 상세: 마지막 거래일 1분봉 (d = "09:01") 과 일봉 200개. 같은 날 한 번 받은 일봉은 다시 쓴다 */
    suspend fun detail(code: String): Detail {   // ios: Mock/MockFeed.swift:135
        val (day, mins) = minutes(code)   // ios: Mock/MockFeed.swift:136
        val today = MockFmt.day(mockNow())   // ios: Mock/MockFeed.swift:137
        var days = dayCache[code]?.let { if (it.first == today) it.second else null }   // ios: Mock/MockFeed.swift:138
        if (days == null) {   // ios: Mock/MockFeed.swift:139
            days = daily(listOf(code), count = 200, progress = { }).bars[code] ?: emptyList()   // ios: Mock/MockFeed.swift:140
            dayCache[code] = today to days   // ios: Mock/MockFeed.swift:141
        }
        return Detail(day, mins, days)   // ios: Mock/MockFeed.swift:143
    }

    /** 마지막 거래일 정규장 1분봉 (d = "09:01") */
    suspend fun minutes(code: String): Pair<String, List<MockBar>> {   // ios: Mock/MockFeed.swift:147
        if (user) return MockToss.minutes(code)   // ios: Mock/MockFeed.swift:148
        val o = get("/api/mock/minutes?symbol=" + enc(code), timeout = 12.0)   // ios: Mock/MockFeed.swift:149
        val mins = (jArr(o["bars"]) ?: emptyList()).mapNotNull { r -> jArr(r)?.let { barRow(it) } }   // ios: Mock/MockFeed.swift:150
        return ((o["day"] as? String) ?: "") to mins   // ios: Mock/MockFeed.swift:154
    }
    private val dayCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, List<MockBar>>>()   // ios: Mock/MockFeed.swift:156

    /** 지금 가격 (토스 코드 → 가격) */
    suspend fun last(codes: List<String>): Map<String, Double> {   // ios: Mock/MockFeed.swift:159
        if (codes.isEmpty()) return emptyMap()   // ios: Mock/MockFeed.swift:160
        if (user) return MockToss.last(codes)   // ios: Mock/MockFeed.swift:161
        val out = mutableMapOf<String, Double>()   // ios: Mock/MockFeed.swift:162
        for (i in 0 until codes.size step 200) {   // ios: Mock/MockFeed.swift:163
            val part = codes.subList(i, min(codes.size, i + 200))   // ios: Mock/MockFeed.swift:164
            val o = get("/api/mock/last?symbols=" + enc(part.joinToString(",")), timeout = 8.0)   // ios: Mock/MockFeed.swift:165
            for ((c, v) in jObj(o["quotes"]) ?: emptyMap()) {   // ios: Mock/MockFeed.swift:166
                num(jObj(v)?.get("last"))?.let { x -> if (x > 0) out[c] = x }   // ios: Mock/MockFeed.swift:167
            }
        }
        return out   // ios: Mock/MockFeed.swift:170
    }

    /** 호가 (주문 순간) */
    suspend fun book(code: String): MockCalc.Book {   // ios: Mock/MockFeed.swift:174
        val o = if (user) MockToss.book(code) else get("/api/mock/book?symbol=" + enc(code), timeout = 6.0)   // ios: Mock/MockFeed.swift:175
        fun levels(k: String): List<MockCalc.Level> =   // ios: Mock/MockFeed.swift:176
            (jObjs(o[k]) ?: emptyList()).mapNotNull { e ->   // ios: Mock/MockFeed.swift:177
                val p = num(e["price"]); val v = num(e["volume"]); if (p == null || v == null) null   // ios: Mock/MockFeed.swift:178
                else MockCalc.Level(p = p, v = v)   // ios: Mock/MockFeed.swift:179
            }
        return MockCalc.Book(asks = levels("asks"), bids = levels("bids"), at = (o["timestamp"] as? String)?.let { isoDate(it) })   // ios: Mock/MockFeed.swift:182
    }

    /** 유의사항 (최대 30종목): 코드 → 지금 걸린 종류. 거래정지·정리매매는 종목 정보로 따로 물어 같이 넣는다 (SUSPENDED · LIQUIDATION_TRADING) */
    suspend fun warnings(codes: List<String>): Map<String, List<String>> {   // ios: Mock/MockFeed.swift:186
        if (codes.isEmpty()) return emptyMap()   // ios: Mock/MockFeed.swift:187
        if (user) return MockToss.warnings(codes)   // ios: Mock/MockFeed.swift:188
        val top = codes.take(30)   // ios: Mock/MockFeed.swift:189
        val w = get("/api/mock/warnings?symbols=" + enc(top.joinToString(",")), timeout = 20.0)   // ios: Mock/MockFeed.swift:190
        val out = mutableMapOf<String, List<String>>()   // ios: Mock/MockFeed.swift:191
        for ((c, v) in jObj(w["warnings"]) ?: emptyMap()) out[c] = (jArr(v) ?: emptyList()).mapNotNull { it as? String }   // ios: Mock/MockFeed.swift:192
        val sp = get("/api/mock/suspended?symbols=" + enc(top.joinToString(",")), timeout = 8.0)   // ios: Mock/MockFeed.swift:193
        for ((c, v) in jObj(sp["info"]) ?: emptyMap()) {   // ios: Mock/MockFeed.swift:194
            val x = jObj(v) ?: emptyMap()   // ios: Mock/MockFeed.swift:195
            if (x["suspended"] as? Boolean == true) out[c] = (out[c] ?: emptyList()) + "SUSPENDED"   // ios: Mock/MockFeed.swift:196
            if (x["liquidation"] as? Boolean == true && !(out[c] ?: emptyList()).contains("LIQUIDATION_TRADING")) out[c] = (out[c] ?: emptyList()) + "LIQUIDATION_TRADING"   // ios: Mock/MockFeed.swift:197
        }
        return out   // ios: Mock/MockFeed.swift:199
    }

    /** 오늘 상한가·하한가 (서버가 하루 한 번 받는다) */
    suspend fun limits(code: String): Pair<Double, Double>? {   // ios: Mock/MockFeed.swift:203
        if (user) return MockToss.limits(code)   // ios: Mock/MockFeed.swift:204
        val o = get("/api/mock/limits?symbol=" + enc(code), timeout = 6.0)   // ios: Mock/MockFeed.swift:205
        val u = num(o["upper"]); val l = num(o["lower"]); if (u == null || l == null) return null   // ios: Mock/MockFeed.swift:206
        return u to l   // ios: Mock/MockFeed.swift:207
    }

    /** 국내 장 일정: 오늘 정규장 시작·끝 (휴장이면 null) */
    @Serializable
    data class Session(   // ios: Mock/MockFeed.swift:211
        var day: String,   // ios: Mock/MockFeed.swift:212
        var open: MockDate?,   // ios: Mock/MockFeed.swift:213
        var close: MockDate?,   // ios: Mock/MockFeed.swift:214
        var got: MockDate,   // ios: Mock/MockFeed.swift:215
    )
    suspend fun calendar(): Session {   // ios: Mock/MockFeed.swift:217
        val o = if (user) MockToss.calendar() else get("/api/mock/calendar?market=KR", timeout = 6.0)   // ios: Mock/MockFeed.swift:218
        val t = jObj(o["today"]); val d = t?.get("date") as? String; if (t == null || d == null) throw Fail(why = "장 일정을 읽지 못함")   // ios: Mock/MockFeed.swift:219
        val reg = jObj(jObj(t["integrated"])?.get("regularMarket"))   // ios: Mock/MockFeed.swift:220
        return Session(day = d, open = (reg?.get("startTime") as? String)?.let { isoDate(it) }, close = (reg?.get("endTime") as? String)?.let { isoDate(it) }, got = mockNow())   // ios: Mock/MockFeed.swift:221
    }

    fun isoDate(s: String): MockDate? = isoSec(s)   // ios: Mock/MockFeed.swift:224,225,226,227,228,229
}
/** 토스 시세 호출 한도: 부르기 사이를 1/N초 이상 띄운다 (N 은 설정 안전장치, 기본 5) */
object MockGate {   // ios: Mock/MockFeed.swift:233
    private val lock = Mutex()
    private var gap: Double = 0.2   // ios: Mock/MockFeed.swift:235
    private var next = 0.0   // ios: Mock/MockFeed.swift:236
    fun set(perSec: Double) { gap = 1 / max(1.0, perSec) }   // ios: Mock/MockFeed.swift:237
    suspend fun wait() {   // ios: Mock/MockFeed.swift:238
        val d = lock.withLock {
            val now = mockNow(); val at = max(now, next)   // ios: Mock/MockFeed.swift:239
            next = at + gap   // ios: Mock/MockFeed.swift:240
            at - now   // ios: Mock/MockFeed.swift:241
        }
        if (d > 0) delay((d * 1000).toLong())   // ios: Mock/MockFeed.swift:242
    }
}
// 토스 주문 API (문서: github.com/beeboy/toss-api docs 의 주문 · 주문 내역 · 인증 · API 소개, 2026-10-26 읽음)
// 요청을 만들고 응답을 읽는 곳. 보내는 일은 MockTossBroker 가 실전 스위치가 켜졌을 때만 한다.
// 토스는 허용 IP 에서만 받고 키는 서버에만 있으므로 개발자 서버(server.py /api/mock/order…)가 중계한다. 앱에는 토큰·계좌가 없다

object TossOrderAPI {   // ios: Mock/MockFeed.swift:249
    /** 호출 한도 (초당, 문서 표). 피크 09:00~09:10 은 ORDER_INFO 만 3회로 줄어든다 */
    val perSecond: Map<String, Double> = mapOf("AUTH" to 5.0, "ORDER" to 10.0, "ORDER_HISTORY" to 5.0, "ORDER_INFO" to 6.0)   // ios: Mock/MockFeed.swift:251

    /** 주문 세부 상태 → 앱 상태. 정정·취소 거부는 원래 주문이 그대로라 null (상태 유지) */
    fun state(s: String): MockOrderState? = when (s) {   // ios: Mock/MockFeed.swift:254,255
        "PENDING", "PENDING_CANCEL", "PENDING_REPLACE" -> MockOrderState.accepted   // ios: Mock/MockFeed.swift:256
        "PARTIAL_FILLED" -> MockOrderState.partial   // ios: Mock/MockFeed.swift:257
        "FILLED" -> MockOrderState.filled   // ios: Mock/MockFeed.swift:258
        "CANCELED", "REPLACED" -> MockOrderState.canceled   // ios: Mock/MockFeed.swift:259
        "REJECTED" -> MockOrderState.rejected   // ios: Mock/MockFeed.swift:260
        else -> null   // ios: Mock/MockFeed.swift:261
    }

    /** 주문 한 건 (목록·상세·웹소켓 data.order 가 같은 모양) */
    data class Order(   // ios: Mock/MockFeed.swift:266
        var id: String,   // ios: Mock/MockFeed.swift:267
        var status: String,   // ios: Mock/MockFeed.swift:268
        var filledQty: Int,   // ios: Mock/MockFeed.swift:269
        var avg: Double?,   // ios: Mock/MockFeed.swift:270
        var filledAmount: Double?,   // ios: Mock/MockFeed.swift:271
        var commission: Double?,   // ios: Mock/MockFeed.swift:272
        var tax: Double?,   // ios: Mock/MockFeed.swift:273
    )

    /** 주문 생성 본문. 국내는 정수 수량·정수 가격(호가 단위). clientOrderId = 앱 주문 id (멱등성 키, 10분) */
    fun createBody(o: MockOrder): Map<String, Any?> {   // ios: Mock/MockFeed.swift:277
        val b = mutableMapOf<String, Any?>("symbol" to o.code, "side" to (if (o.side == MockSide.buy) "BUY" else "SELL"),   // ios: Mock/MockFeed.swift:278
            "orderType" to (if (o.type == MockOrderType.limit) "LIMIT" else "MARKET"), "quantity" to o.qty.toString(),   // ios: Mock/MockFeed.swift:279
            "clientOrderId" to o.id, "timeInForce" to "DAY")   // ios: Mock/MockFeed.swift:280
        val p = o.limit; if (o.type == MockOrderType.limit && p != null) b["price"] = p.swRound().toLong().toString()   // ios: Mock/MockFeed.swift:281
        return b   // ios: Mock/MockFeed.swift:282
    }

    /** 중계 경로: 만들기 /api/mock/order · 상세 /api/mock/order/{id} · 취소 /api/mock/order/{id}/cancel. 서버 주소·열쇠가 없으면 null */
    internal fun request(method: String, path: String, body: Map<String, Any?>?): MockReq? {   // ios: Mock/MockFeed.swift:286
        val b = MockFeed.base; val t = MockFeed.token; val url = b?.let { uriOrNull(it + path) }; if (t == null || url == null) return null   // ios: Mock/MockFeed.swift:287
        val r = MockReq(url, 10.0)   // ios: Mock/MockFeed.swift:288
        r.method = method   // ios: Mock/MockFeed.swift:289
        r.headers["X-Naeilo-Dev"] = t   // ios: Mock/MockFeed.swift:290
        if (body != null) {   // ios: Mock/MockFeed.swift:291
            r.headers["Content-Type"] = "application/json"   // ios: Mock/MockFeed.swift:292
            r.body = jsonBody(body)   // ios: Mock/MockFeed.swift:293
        }
        return r   // ios: Mock/MockFeed.swift:295
    }
    /** 보내고 (코드, 본문 사전). 닿지 못하면 null */
    internal suspend fun send(r: MockReq): Pair<Int, Map<String, Any?>?>? =
        try { val x = r.send(); x.code to jObj(feedJson(x.data)) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    fun orderId(o: Map<String, Any?>): String? = jObj(o["result"])?.get("orderId") as? String   // ios: Mock/MockFeed.swift:298

    fun order(o: Map<String, Any?>): Order? {   // ios: Mock/MockFeed.swift:300
        val r = jObj(o["result"]); val id = r?.get("orderId") as? String; val st = r?.get("status") as? String; if (r == null || id == null || st == null) return null   // ios: Mock/MockFeed.swift:301
        val e = jObj(r["execution"]) ?: emptyMap()   // ios: Mock/MockFeed.swift:302
        return Order(id = id, status = st, filledQty = (MockFeed.num(e["filledQuantity"]) ?: 0.0).toInt(), avg = MockFeed.num(e["averageFilledPrice"]),   // ios: Mock/MockFeed.swift:303
            filledAmount = MockFeed.num(e["filledAmount"]), commission = MockFeed.num(e["commission"]), tax = MockFeed.num(e["tax"]))   // ios: Mock/MockFeed.swift:304
    }

    /** 오류 봉투 {"error":{"code","message"}} → 화면 글 */
    fun reason(o: Map<String, Any?>?, status: Int): String {   // ios: Mock/MockFeed.swift:308
        val e = jObj(o?.get("error"))   // ios: Mock/MockFeed.swift:309
        val code = (e?.get("code") as? String) ?: "http-$status"   // ios: Mock/MockFeed.swift:310
        return (reasons[code] ?: (e?.get("message") as? String) ?: "토스 오류") + " ($code)"   // ios: Mock/MockFeed.swift:311
    }

    /** 문서의 오류 코드 (주문에 쓰는 것만) */
    val reasons: Map<String, String> = mapOf(   // ios: Mock/MockFeed.swift:315
        "invalid-request" to "요청이 올바르지 않음", "confirm-high-value-required" to "1억원 이상 주문 확인 필요",   // ios: Mock/MockFeed.swift:316
        "account-header-required" to "계좌 머리글 없음", "invalid-token" to "토큰이 맞지 않음", "expired-token" to "토큰 만료",   // ios: Mock/MockFeed.swift:317
        "token-revoked" to "새 토큰으로 바뀜", "ip-not-allowed" to "허용되지 않은 IP", "forbidden" to "권한 없음",   // ios: Mock/MockFeed.swift:318
        "account-not-found" to "계좌 없음", "order-not-found" to "주문 없음", "request-in-progress" to "같은 주문 처리 중",   // ios: Mock/MockFeed.swift:319
        "already-filled" to "이미 체결됨", "already-canceled" to "이미 취소됨", "already-modified" to "이미 정정됨",   // ios: Mock/MockFeed.swift:320
        "already-rejected" to "이미 거부됨", "already-processing" to "정정·취소 처리 중", "opposite-pending-order-exists" to "같은 종목 반대 방향 대기 주문 있음",   // ios: Mock/MockFeed.swift:321
        "insufficient-buying-power" to "주문 가능 금액 부족", "order-hours-closed" to "주문 받는 시간 아님", "stock-restricted" to "거래 제한 종목",   // ios: Mock/MockFeed.swift:322
        "price-out-of-range" to "상·하한가 밖 가격", "order-type-not-allowed" to "지금 못 쓰는 호가 유형", "prerequisite-required" to "약관·교육 등 자격 필요",   // ios: Mock/MockFeed.swift:323
        "market-not-supported-for-stock" to "이 시장에서 거래 못 하는 종목", "investor-exchange-not-integrated" to "거래소 설정이 통합(SOR)이 아님",   // ios: Mock/MockFeed.swift:324
        "insufficient-sellable-quantity" to "매도 가능 수량 부족", "order-limit-exceeded" to "주문 설정 한도 넘음",   // ios: Mock/MockFeed.swift:325
        "idempotency-key-conflict" to "같은 주문 번호로 다른 주문", "account-restricted" to "계좌가 이 주문을 허용하지 않음",   // ios: Mock/MockFeed.swift:326
        "max-order-amount-exceeded" to "30억원 이상 주문", "rate-limit-exceeded" to "호출 한도 넘음", "edge-rate-limit-exceeded" to "호출 한도 넘음",   // ios: Mock/MockFeed.swift:327
        "internal-error" to "토스 일시 장애", "maintenance" to "토스 점검 중",   // ios: Mock/MockFeed.swift:328
        // 중계 서버가 막은 것 (토스에 안 보냄)
        "relay-off" to "서버 실전 꺼짐", "relay-no-keys" to "서버에 토스 키 없음", "relay-no-account" to "서버에 계좌 없음",   // ios: Mock/MockFeed.swift:330
        "relay-over-cap" to "서버 한 번 주문 상한 넘음", "relay-no-price" to "지금 가격을 몰라 시장가 안 냄", "relay-unreachable" to "서버가 토스에 닿지 못함",   // ios: Mock/MockFeed.swift:331
    )

    /** 정정 본문 (국내: 수량 필수, 지정가 가격 정수). 서버가 상한을 보려고 종목도 같이 보낸다 */
    fun modifyBody(o: MockOrder, qty: Int, limit: Double): Map<String, Any?> =   // ios: Mock/MockFeed.swift:335
        mapOf("symbol" to o.code, "orderType" to "LIMIT", "quantity" to qty.toString(), "price" to limit.swRound().toLong().toString())   // ios: Mock/MockFeed.swift:336

    /** 서버 실전 상태 (비밀값 없이): 스위치·계좌·키·한 번 주문 상한 */
    data class Ready(var armed: Boolean = false, var account: Boolean = false, var keys: Boolean = false, var maxKrw: Double = 0.0)   // ios: Mock/MockFeed.swift:340
    suspend fun readyInfo(): Ready? {   // ios: Mock/MockFeed.swift:341
        val req = request("GET", "/api/mock/order/ready", body = null) ?: return null   // ios: Mock/MockFeed.swift:342
        val (code, j) = send(req) ?: return null; if (code != 200 || j == null) return null   // ios: Mock/MockFeed.swift:343,344
        return Ready(armed = j["armed"] as? Boolean == true, account = j["account"] as? Boolean == true, keys = j["keys"] as? Boolean == true,   // ios: Mock/MockFeed.swift:345
            maxKrw = MockFeed.num(j["max_krw"]) ?: 0.0)   // ios: Mock/MockFeed.swift:346
    }

    /** 토스 계좌 (서버가 계좌 머리글을 붙여 묻는다): 주문가능금액(원) · 국내 보유 */
    data class Account(   // ios: Mock/MockFeed.swift:350
        var cash: Double?,   // ios: Mock/MockFeed.swift:352
        var items: List<Item>,   // ios: Mock/MockFeed.swift:353
        var plAfterCost: Double?,   // ios: Mock/MockFeed.swift:354
        var dayPl: Double? = null,      // ios: Mock/MockFeed.swift:355
        var at: MockDate,   // ios: Mock/MockFeed.swift:356
    ) {
        data class Item(var code: String, var qty: Int, var avg: Double?, var plAfterCost: Double?)   // ios: Mock/MockFeed.swift:351
    }
    suspend fun account(): Pair<Account?, String?> {   // ios: Mock/MockFeed.swift:358
        val req = request("GET", "/api/mock/account", body = null) ?: return null to "개발자 서버 주소·열쇠 없음"   // ios: Mock/MockFeed.swift:359
        val (code, j) = send(req) ?: return null to "서버에 닿지 못함"   // ios: Mock/MockFeed.swift:360,361
        if (code != 200 || j == null) return null to reason(j, status = code)   // ios: Mock/MockFeed.swift:362
        val items = (jObjs(j["holdings"]) ?: emptyList()).mapNotNull { x ->   // ios: Mock/MockFeed.swift:363
            val c = x["code"] as? String ?: return@mapNotNull null   // ios: Mock/MockFeed.swift:364
            Account.Item(code = c, qty = (MockFeed.num(x["qty"]) ?: 0.0).toInt(), avg = MockFeed.num(x["avg"]), plAfterCost = MockFeed.num(x["plAfterCost"]))   // ios: Mock/MockFeed.swift:365
        }
        return Account(cash = MockFeed.num(j["cash"]), items = items, plAfterCost = MockFeed.num(j["plAfterCost"]), dayPl = MockFeed.num(j["dayPl"]), at = mockNow()) to null   // ios: Mock/MockFeed.swift:367
    }

    /** 서버 실전 준비 (toss_live.json 켜짐·계좌·키). 준비가 덜 됐으면 이유, 다 됐으면 null */
    suspend fun ready(): String? {   // ios: Mock/MockFeed.swift:371
        val req = request("GET", "/api/mock/order/ready", body = null) ?: return "개발자 서버 주소·열쇠 없음"   // ios: Mock/MockFeed.swift:372
        val (code, j) = send(req) ?: return "서버에 닿지 못함"; if (code != 200 || j == null) return "서버에 닿지 못함"   // ios: Mock/MockFeed.swift:373,374
        if (j["keys"] as? Boolean != true) return "서버에 토스 키 없음"   // ios: Mock/MockFeed.swift:375
        if (j["account"] as? Boolean != true) return "서버에 계좌 없음"   // ios: Mock/MockFeed.swift:376
        if (j["armed"] as? Boolean != true) return "서버 실전 꺼짐"   // ios: Mock/MockFeed.swift:377
        return null   // ios: Mock/MockFeed.swift:378
    }
}

// 일반 사용자 시세 길
// 모의투자 시세 길 (일반 사용자, 2026-11-05): 사용자 본인 토스 키 + 공용 중계(toss.naeilo.com/api/toss/v1/…).
// 개발자 서버(server.py /api/mock/…)가 하던 일을 폰에서 한다. 주문·계좌 경로는 없다 (실전은 개발자 기기만).
// 토스 응답 모양·다시 쓰기 시간은 server.py mock_* 와 같게 둔다.
object MockToss {   // ios: Mock/MockFeed.swift:387
    private fun Fail(why: String) = MockFeed.Fail(why)   // ios: Mock/MockFeed.swift:388

    /** 중계 GET. 토큰이 끊겼으면(401) 한 번 새로, 한도(429)면 Retry-After(없으면 1·2·4초) 뒤 3번까지 다시 */
    suspend fun call(path: String, timeout: Double = 6.0): Map<String, Any?> {   // ios: Mock/MockFeed.swift:391
        val url = uriOrNull(Config.tossRelay + "/api/toss/v1" + path); if (!TossKeys.on || url == null) throw Fail(why = "토스 키를 넣으면 모의투자를 쓸 수 있어요")   // ios: Mock/MockFeed.swift:392
        var renewed = false   // ios: Mock/MockFeed.swift:393
        var attempt = 0   // ios: Mock/MockFeed.swift:394
        while (true) {   // ios: Mock/MockFeed.swift:395
            val (tok, e) = TossFeed.token(renew = renewed)   // ios: Mock/MockFeed.swift:396
            if (tok == null) throw Fail(why = e ?: "토큰 실패")   // ios: Mock/MockFeed.swift:397
            MockGate.wait()   // ios: Mock/MockFeed.swift:398
            val req = MockReq(url, timeout)   // ios: Mock/MockFeed.swift:399
            req.headers["Authorization"] = "Bearer " + tok   // ios: Mock/MockFeed.swift:400
            req.headers["User-Agent"] = "naeilo-ios-mock/0.2"   // ios: Mock/MockFeed.swift:401
            val res: MockReq.Res   // ios: Mock/MockFeed.swift:402
            try { res = req.send() }   // ios: Mock/MockFeed.swift:403
            catch (x: CancellationException) { throw x }   // ios: Mock/MockFeed.swift:405
            catch (x: IOException) {   // ios: Mock/MockFeed.swift:404
                throw Fail(why = if (x is SocketTimeoutException) "중계 응답 없음 (${timeout.toInt()}초)" else "중계에 닿지 못함 (${x.javaClass.simpleName})")   // ios: Mock/MockFeed.swift:406
            }
            val code = res.code.coerceAtLeast(0)   // ios: Mock/MockFeed.swift:408
            val o = jObj(feedJson(res.data))   // ios: Mock/MockFeed.swift:409
            if (code == 401 && !renewed) { renewed = true; continue }   // ios: Mock/MockFeed.swift:410
            if (code == 429 && attempt < 3) {   // ios: Mock/MockFeed.swift:411
                val after = res.header("Retry-After")?.toDoubleOrNull() ?: 2.0.pow(attempt.toDouble())   // ios: Mock/MockFeed.swift:412
                attempt += 1   // ios: Mock/MockFeed.swift:413
                delay((min(after, 10.0) * 1000).toLong())   // ios: Mock/MockFeed.swift:414
                continue   // ios: Mock/MockFeed.swift:415
            }
            if (code == 429) throw Fail(why = "토스 호출 한도 · 3번 다시 불러도 막힘")   // ios: Mock/MockFeed.swift:417
            if (code == 404) throw Fail(why = "없는 종목")   // ios: Mock/MockFeed.swift:418
            if (code != 200 || o == null) throw Fail(why = TossFeed.why(code, o))   // ios: Mock/MockFeed.swift:419
            return o   // ios: Mock/MockFeed.swift:420
        }
    }

    /** 다시 쓰기 (server.py 와 같은 시간) */
    object Cache {   // ios: Mock/MockFeed.swift:425
        private val lock = Any()
        private val dailyM = HashMap<String, Triple<Double, Int, List<MockBar>>>()   // ios: Mock/MockFeed.swift:427
        private val minsM = HashMap<String, Triple<Double, String, List<MockBar>>>()   // ios: Mock/MockFeed.swift:428
        private val warnM = HashMap<String, Pair<Double, List<String>>>()   // ios: Mock/MockFeed.swift:429
        private val suspM = HashMap<String, Triple<Double, Boolean, Boolean>>()   // ios: Mock/MockFeed.swift:430
        private val limM = HashMap<String, Triple<String, Double, Double>>()   // ios: Mock/MockFeed.swift:431
        private var calM: Pair<Double, Map<String, Any?>>? = null   // ios: Mock/MockFeed.swift:432

        fun daily(c: String, count: Int, ttl: Double): List<MockBar>? = synchronized(lock) {   // ios: Mock/MockFeed.swift:434
            val x = dailyM[c]; if (x == null || x.second < count || mockNow() - x.first >= ttl) null   // ios: Mock/MockFeed.swift:435
            else x.third.takeLast(count)   // ios: Mock/MockFeed.swift:436
        }
        fun putDaily(c: String, count: Int, b: List<MockBar>) = synchronized(lock) { dailyM[c] = Triple(mockNow(), count, b) }   // ios: Mock/MockFeed.swift:438
        fun mins(c: String): Pair<String, List<MockBar>>? = synchronized(lock) {   // ios: Mock/MockFeed.swift:439
            val x = minsM[c]; if (x == null || mockNow() - x.first >= 60) null   // ios: Mock/MockFeed.swift:440
            else x.second to x.third   // ios: Mock/MockFeed.swift:441
        }
        fun putMins(c: String, day: String, b: List<MockBar>) = synchronized(lock) { minsM[c] = Triple(mockNow(), day, b) }   // ios: Mock/MockFeed.swift:443
        fun warn(c: String, ttl: Double): List<String>? = synchronized(lock) {   // ios: Mock/MockFeed.swift:444
            val x = warnM[c]; if (x == null || mockNow() - x.first >= ttl) null   // ios: Mock/MockFeed.swift:445
            else x.second   // ios: Mock/MockFeed.swift:446
        }
        fun putWarn(c: String, v: List<String>) = synchronized(lock) { warnM[c] = mockNow() to v }   // ios: Mock/MockFeed.swift:448
        fun needSusp(cs: List<String>): List<String> = synchronized(lock) { cs.filter { c -> suspM[c]?.let { mockNow() - it.first >= 600 } ?: true } }   // ios: Mock/MockFeed.swift:449
        fun putSusp(c: String, s: Boolean, l: Boolean) = synchronized(lock) { suspM[c] = Triple(mockNow(), s, l) }   // ios: Mock/MockFeed.swift:450
        fun susp(c: String): Pair<Boolean, Boolean>? = synchronized(lock) { suspM[c]?.let { it.second to it.third } }   // ios: Mock/MockFeed.swift:451
        fun lim(c: String, day: String): Pair<Double, Double>? = synchronized(lock) { limM[c]?.let { if (it.first == day) it.second to it.third else null } }   // ios: Mock/MockFeed.swift:452
        fun putLim(c: String, day: String, u: Double, l: Double) = synchronized(lock) { limM[c] = Triple(day, u, l) }   // ios: Mock/MockFeed.swift:453
        fun cal(): Map<String, Any?>? = synchronized(lock) { calM?.let { if (mockNow() - it.first < 600) it.second else null } }   // ios: Mock/MockFeed.swift:454
        fun putCal(o: Map<String, Any?>) = synchronized(lock) { calM = mockNow() to o }   // ios: Mock/MockFeed.swift:455
    }

    val kst: ZoneId = ZoneId.of("Asia/Seoul")   // ios: Mock/MockFeed.swift:458
    fun kstDay(d: MockDate = mockNow()): String = MockFmt.day(d)   // ios: Mock/MockFeed.swift:460,462
    /** 한국 평일 8시~20시: 일봉 마지막 칸이 바뀌는 때 */
    val krBusy: Boolean get() {   // ios: Mock/MockFeed.swift:465
        val c = Instant.now().atZone(kst); val wd = c.dayOfWeek; val h = c.hour   // ios: Mock/MockFeed.swift:466
        return wd != DayOfWeek.SATURDAY && wd != DayOfWeek.SUNDAY && h in 8 until 20
    }
    private fun num(v: Any?): Double? = MockFeed.num(v)   // ios: Mock/MockFeed.swift:469
    private fun enc(s: String): String = MockFeed.enc(s)   // ios: Mock/MockFeed.swift:470

    // 일봉

    /** 한 종목 일봉 (오래된 것부터). 수정주가. 장중 5분, 장 밖 6시간 다시 쓰기 */
    suspend fun dailyOne(c: String, count: Int): List<MockBar> {   // ios: Mock/MockFeed.swift:475
        Cache.daily(c, count = count, ttl = if (krBusy) 300.0 else 6.0 * 3600)?.let { return it }   // ios: Mock/MockFeed.swift:476
        val o = call("/candles?symbol=${enc(c)}&interval=1d&count=$count&adjusted=true")   // ios: Mock/MockFeed.swift:477
        val rows = (jObjs(jObj(o["result"])?.get("candles")) ?: emptyList()).mapNotNull { v ->   // ios: Mock/MockFeed.swift:478
            val t = v["timestamp"] as? String; val op = num(v["openPrice"]); val h = num(v["highPrice"])   // ios: Mock/MockFeed.swift:479
            val l = num(v["lowPrice"]); val cl = num(v["closePrice"])?.takeIf { it > 0 }   // ios: Mock/MockFeed.swift:480
            if (t == null || t.length < 10 || op == null || h == null || l == null || cl == null || cl <= 0) null   // ios: Mock/MockFeed.swift:479
            else MockBar(d = t.take(10), o = op, h = h, l = l, c = cl, v = num(v["volume"]) ?: 0.0)   // ios: Mock/MockFeed.swift:481
        }.sortedBy { it.d }   // ios: Mock/MockFeed.swift:482
        Cache.putDaily(c, count = count, b = rows)   // ios: Mock/MockFeed.swift:483
        return rows   // ios: Mock/MockFeed.swift:484
    }

    /** 여러 종목 일봉: 8개씩 같이 부른다 (한도는 MockGate 가 줄 세운다). 실패는 종목마다 이유 */
    suspend fun daily(codes: List<String>, count: Int): Pair<Map<String, List<MockBar>>, Map<String, String>> {   // ios: Mock/MockFeed.swift:488
        val n = max(2, min(200, count))   // ios: Mock/MockFeed.swift:489
        val bars = mutableMapOf<String, List<MockBar>>(); val errs = mutableMapOf<String, String>()   // ios: Mock/MockFeed.swift:490
        for (i in 0 until codes.size step 8) {   // ios: Mock/MockFeed.swift:491
            coroutineContext.ensureActive()   // ios: Mock/MockFeed.swift:492
            val part = codes.subList(i, min(codes.size, i + 8))   // ios: Mock/MockFeed.swift:493
            coroutineScope {   // ios: Mock/MockFeed.swift:494
                val jobs = part.map { c -> c to async { runCatching { dailyOne(c, count = n) } } }   // ios: Mock/MockFeed.swift:495
                for ((c, j) in jobs) {   // ios: Mock/MockFeed.swift:496
                    val r = j.await()
                    r.onSuccess { b -> bars[c] = b }   // ios: Mock/MockFeed.swift:497,498
                        .onFailure { e -> errs[c] = (e as? MockFeed.Fail)?.why ?: (if (e is CancellationException) "취소" else "실패") }   // ios: Mock/MockFeed.swift:499
                }
            }
            coroutineContext.ensureActive()   // ios: Mock/MockFeed.swift:503
        }
        return bars to errs   // ios: Mock/MockFeed.swift:505
    }

    // 1분봉

    /** 마지막 거래일 정규장(9:00~15:30) 1분봉 (d = "09:01"). 장 끝부터 거꾸로 200개씩. 1분 다시 쓰기 */
    suspend fun minutes(c: String): Pair<String, List<MockBar>> {   // ios: Mock/MockFeed.swift:511
        Cache.mins(c)?.let { return it }   // ios: Mock/MockFeed.swift:512
        fun sess(d: MockDate): Pair<MockDate, MockDate> {   // ios: Mock/MockFeed.swift:514
            val z = MockFmt.inst(d).atZone(kst).toLocalDate()
            return z.atTime(9, 0, 0).atZone(kst).toEpochSecond().toDouble() to z.atTime(15, 30, 0).atZone(kst).toEpochSecond().toDouble()   // ios: Mock/MockFeed.swift:515
        }
        val now = mockNow(); val (o0, c0) = sess(now)   // ios: Mock/MockFeed.swift:517
        var before = if (now < o0) sess(now - 86400).second else min(now, c0)   // ios: Mock/MockFeed.swift:518
        val got = mutableMapOf<MockDate, MockBar>(); var win: Pair<MockDate, MockDate>? = null   // ios: Mock/MockFeed.swift:519
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(kst)   // ios: Mock/MockFeed.swift:520
        val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(kst)
        for (k in 0 until 4) {   // ios: Mock/MockFeed.swift:522
            val o = call("/candles?symbol=${enc(c)}&interval=1m&count=200&adjusted=false&before=" + enc(iso.format(MockFmt.inst(before + 60)).replace(Regex("""\.\d+"""), "")), timeout = 8.0)   // ios: Mock/MockFeed.swift:523
            val page = mutableMapOf<MockDate, MockBar>()   // ios: Mock/MockFeed.swift:524
            for (v in jObjs(jObj(o["result"])?.get("candles")) ?: emptyList()) {   // ios: Mock/MockFeed.swift:525
                val ts = v["timestamp"] as? String; val d = ts?.let { MockFeed.isoDate(it) }; val op = num(v["openPrice"]); val h = num(v["highPrice"])   // ios: Mock/MockFeed.swift:526
                val l = num(v["lowPrice"]); val cl = num(v["closePrice"]); if (d == null || op == null || h == null || l == null || cl == null || cl <= 0) continue   // ios: Mock/MockFeed.swift:527
                page[d] = MockBar(d = hm.format(MockFmt.inst(d)), o = op, h = h, l = l, c = cl, v = num(v["volume"]) ?: 0.0)   // ios: Mock/MockFeed.swift:528
            }
            val newest = page.keys.maxOrNull(); val oldest = page.keys.minOrNull(); if (newest == null || oldest == null) break   // ios: Mock/MockFeed.swift:530
            if (win == null) {   // ios: Mock/MockFeed.swift:531
                win = sess(newest)   // ios: Mock/MockFeed.swift:532
                // 마지막 봉이 장 끝 뒤(애프터)면 장 끝부터 다시
                if (newest > win.second + 60) { before = win.second; continue }   // ios: Mock/MockFeed.swift:534
            }
            for ((kk, vv) in page) if (kk !in got) got[kk] = vv   // ios: Mock/MockFeed.swift:536
            if (oldest <= win.first) break   // ios: Mock/MockFeed.swift:537
            before = oldest - 60   // ios: Mock/MockFeed.swift:538
        }
        val w = win ?: return "" to emptyList()   // ios: Mock/MockFeed.swift:540
        val out = got.keys.sorted().filter { it > w.first && it <= w.second + 60 }.mapNotNull { got[it] }   // ios: Mock/MockFeed.swift:541
        val day = kstDay(w.first)   // ios: Mock/MockFeed.swift:542
        Cache.putMins(c, day, out)   // ios: Mock/MockFeed.swift:543
        return day to out   // ios: Mock/MockFeed.swift:544
    }

    // 지금 가격 · 호가 · 유의사항 · 상하한가 · 장 일정

    suspend fun last(codes: List<String>): Map<String, Double> {   // ios: Mock/MockFeed.swift:549
        val out = mutableMapOf<String, Double>()   // ios: Mock/MockFeed.swift:550
        for (i in 0 until codes.size step 200) {   // ios: Mock/MockFeed.swift:551
            val o = call("/prices?symbols=" + codes.subList(i, min(codes.size, i + 200)).joinToString(","), timeout = 8.0)   // ios: Mock/MockFeed.swift:552
            for (q in jObjs(o["result"]) ?: emptyList()) {   // ios: Mock/MockFeed.swift:553
                val s = q["symbol"] as? String; val v = num(q["lastPrice"]); if (s != null && v != null && v > 0) out[s] = v   // ios: Mock/MockFeed.swift:554
            }
        }
        return out   // ios: Mock/MockFeed.swift:557
    }

    suspend fun book(c: String): Map<String, Any?> =   // ios: Mock/MockFeed.swift:560
        jObj(call("/orderbook?symbol=" + enc(c), timeout = 4.0)["result"]) ?: emptyMap()   // ios: Mock/MockFeed.swift:561

    /** 지금 걸린 유의사항 (VI·투자경고…). 장중 15초, 장 밖 1시간 */
    suspend fun warnOne(c: String): List<String> {   // ios: Mock/MockFeed.swift:565
        Cache.warn(c, ttl = if (krBusy) 15.0 else 3600.0)?.let { return it }   // ios: Mock/MockFeed.swift:566
        val o = call("/stocks/" + enc(c) + "/warnings")   // ios: Mock/MockFeed.swift:567
        val today = kstDay()   // ios: Mock/MockFeed.swift:568
        val out = (jObjs(o["result"]) ?: emptyList()).mapNotNull { w ->   // ios: Mock/MockFeed.swift:569
            val t = w["warningType"] as? String ?: return@mapNotNull null   // ios: Mock/MockFeed.swift:570
            (w["endDate"] as? String)?.let { e -> if (e < today) return@mapNotNull null }   // ios: Mock/MockFeed.swift:571
            (w["startDate"] as? String)?.let { s -> if (s > today) return@mapNotNull null }   // ios: Mock/MockFeed.swift:572
            t   // ios: Mock/MockFeed.swift:573
        }.toSet().sorted()   // ios: Mock/MockFeed.swift:574
        Cache.putWarn(c, out)   // ios: Mock/MockFeed.swift:575
        return out   // ios: Mock/MockFeed.swift:576
    }

    /** 유의사항 + 거래정지·정리매매 (최대 30종목, 걸린 것만) */
    suspend fun warnings(codes: List<String>): Map<String, List<String>> {   // ios: Mock/MockFeed.swift:580
        val top = codes.take(30)   // ios: Mock/MockFeed.swift:581
        val out = mutableMapOf<String, List<String>>()   // ios: Mock/MockFeed.swift:582
        for (c in top) { val w = try { warnOne(c) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (w != null && w.isNotEmpty()) out[c] = w }   // ios: Mock/MockFeed.swift:583
        val need = Cache.needSusp(top)   // ios: Mock/MockFeed.swift:584
        for (i in 0 until need.size step 200) {   // ios: Mock/MockFeed.swift:585
            val o = call("/stocks?symbols=" + need.subList(i, min(need.size, i + 200)).joinToString(","), timeout = 8.0)   // ios: Mock/MockFeed.swift:586
            for (x in jObjs(o["result"]) ?: emptyList()) {   // ios: Mock/MockFeed.swift:587
                val s = x["symbol"] as? String ?: continue   // ios: Mock/MockFeed.swift:588
                val d = jObj(x["koreanMarketDetail"]) ?: emptyMap()   // ios: Mock/MockFeed.swift:589
                Cache.putSusp(s, d["krxTradingSuspended"] as? Boolean == true, d["liquidationTrading"] as? Boolean == true)   // ios: Mock/MockFeed.swift:590
            }
        }
        for (c in top) {   // ios: Mock/MockFeed.swift:593
            val x = Cache.susp(c) ?: continue   // ios: Mock/MockFeed.swift:594
            val (s, l) = x   // ios: Mock/MockFeed.swift:595
            if (s) out[c] = (out[c] ?: emptyList()) + "SUSPENDED"   // ios: Mock/MockFeed.swift:596
            if (l && !(out[c] ?: emptyList()).contains("LIQUIDATION_TRADING")) out[c] = (out[c] ?: emptyList()) + "LIQUIDATION_TRADING"   // ios: Mock/MockFeed.swift:597
        }
        return out   // ios: Mock/MockFeed.swift:599
    }

    /** 오늘 상한가·하한가 (하루 한 번) */
    suspend fun limits(c: String): Pair<Double, Double>? {   // ios: Mock/MockFeed.swift:603
        val day = kstDay()   // ios: Mock/MockFeed.swift:604
        Cache.lim(c, day = day)?.let { return it }   // ios: Mock/MockFeed.swift:605
        val r = jObj(call("/price-limits?symbol=" + enc(c))["result"]) ?: emptyMap()   // ios: Mock/MockFeed.swift:606
        val u = num(r["upperLimitPrice"]); val l = num(r["lowerLimitPrice"]); if (u == null || l == null) return null   // ios: Mock/MockFeed.swift:607
        Cache.putLim(c, day = day, u = u, l = l)   // ios: Mock/MockFeed.swift:608
        return u to l   // ios: Mock/MockFeed.swift:609
    }

    /** 국내 장 운영 정보 (10분 다시 쓰기) */
    suspend fun calendar(): Map<String, Any?> {   // ios: Mock/MockFeed.swift:613
        Cache.cal()?.let { return it }   // ios: Mock/MockFeed.swift:614
        val o = jObj(call("/market-calendar/KR")["result"]) ?: emptyMap()   // ios: Mock/MockFeed.swift:615
        Cache.putCal(o)   // ios: Mock/MockFeed.swift:616
        return o   // ios: Mock/MockFeed.swift:617
    }
}
