package com.naeilo.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

// 사이트(web/widget-core.js)가 만드는 위젯 요약. 금액 배열은 만원 단위 정수, 나머지 금액은 원
@Serializable
data class WSummary(   // ios: WidgetShared/Models.swift:6
    var v: Int,   // ios: WidgetShared/Models.swift:21
    var at: Double,          // 계산한 시각 (ms)   // ios: WidgetShared/Models.swift:22
    var asof: String,        // 마지막 종가 날짜 (계산 기준)   // ios: WidgetShared/Models.swift:23
    var today: String,   // ios: WidgetShared/Models.swift:24
    var cash: Double,   // ios: WidgetShared/Models.swift:25
    var goal: Goal,   // ios: WidgetShared/Models.swift:26
    var hold: List<Hold>,   // ios: WidgetShared/Models.swift:27
    var spark: List<Double?>,   // ios: WidgetShared/Models.swift:28
    var fc: Forecast? = null,   // ios: WidgetShared/Models.swift:29
    var hits: List<Hit>,   // ios: WidgetShared/Models.swift:30
    var week: Week? = null,   // ios: WidgetShared/Models.swift:31
    var prev: Double,   // ios: WidgetShared/Models.swift:32
    var events: List<Event>,   // ios: WidgetShared/Models.swift:33
) {
    @Serializable data class Goal(var amount: Double, var date: String, var start: String, var v0: Double, var m: Double? = null)   // m = 매달 넣는 돈 (원, 앱 내 길 필요 경로)   // ios: WidgetShared/Models.swift:7
    @Serializable data class Hold(var t: String, var sh: Double, var ccy: String, var manual: Double? = null)   // ios: WidgetShared/Models.swift:8
    @Serializable data class Term(var p5: Double? = null, var p50: Double? = null, var p95: Double? = null)   // ios: WidgetShared/Models.swift:9
    @Serializable data class Forecast(   // ios: WidgetShared/Models.swift:10
        var scen: String,   // ios: WidgetShared/Models.swift:11
        var md: List<String>,   // ios: WidgetShared/Models.swift:12
        var p5: List<Double?>, var p25: List<Double?>, var p50: List<Double?>, var p75: List<Double?>, var p95: List<Double?>,   // ios: WidgetShared/Models.swift:13
        var pg: Double,   // ios: WidgetShared/Models.swift:14
        var t: Term? = null,   // ios: WidgetShared/Models.swift:15
    )
    @Serializable data class Hit(var f: String, var h: Int, var r: Int? = null) // r = 1 사후 계산   // ios: WidgetShared/Models.swift:17
    @Serializable data class Week(var f: String, var p50: Double, var lo: Double, var hi: Double, var base: Double, var cash: Double, var t0: Double, var tent: Boolean? = null)   // ios: WidgetShared/Models.swift:18
    @Serializable data class Event(var d: String, var t: String, var k: String)   // ios: WidgetShared/Models.swift:19
}

// data/widget.json (30분마다 갱신되는 작은 시세 파일)
@Serializable
data class WFeed(   // ios: WidgetShared/Models.swift:37
    var v: Int,   // ios: WidgetShared/Models.swift:40
    var u: String? = null,   // ios: WidgetShared/Models.swift:41
    var q: Map<String, List<Double?>>,      // [현재가, 전일 종가, 시각(초)]   // ios: WidgetShared/Models.swift:42
    var c: Map<String, Tail>,   // ios: WidgetShared/Models.swift:43
    var ccy: Map<String, String>,   // ios: WidgetShared/Models.swift:44
    var mar: Mar? = null,   // ios: WidgetShared/Models.swift:45
    var ky: Map<String, String>? = null,  // 내부 테스트 국내 시세 종목 → Yahoo 기호. 위젯이 직접 받는다   // ios: WidgetShared/Models.swift:46
    var ty: Map<String, String>? = null,  // 토스로 받을 내 종목 → Yahoo 기호. 내 키가 있으면 위젯도 토스 지금 가격을 직접 받는다   // ios: WidgetShared/Models.swift:47
) {
    @Serializable data class Tail(var d: List<String>, var c: List<Double?>, var a: List<Double?>)   // ios: WidgetShared/Models.swift:38
    @Serializable data class Mar(var rate: Double, var date: String? = null)   // ios: WidgetShared/Models.swift:39
}

// 동기화된 입력값에서 위젯이 바로 쓰는 부분 (전체 입력값은 계산용으로 따로 저장)
@Serializable
data class WLive(   // ios: WidgetShared/Models.swift:51
    var hold: List<Hold>,   // ios: WidgetShared/Models.swift:53
    var cashKrw: Double,   // ios: WidgetShared/Models.swift:54
    var cashUsd: Double,   // ios: WidgetShared/Models.swift:55
    var goalAmount: Double,   // ios: WidgetShared/Models.swift:56
    var goalDate: String,   // ios: WidgetShared/Models.swift:57
    var goalStart: String? = null,   // ios: WidgetShared/Models.swift:58
    var manualPrice: Boolean,   // ios: WidgetShared/Models.swift:59
    var stateAt: Double,   // ios: WidgetShared/Models.swift:60
) {
    @Serializable data class Hold(var t: String, var sh: Double, var price: Double? = null)   // ios: WidgetShared/Models.swift:52
}

fun num(v: Any?): Double? {   // ios: WidgetShared/Models.swift:63
    if (v is Double) return v   // ios: WidgetShared/Models.swift:64
    if (v is Int) return v.toDouble()   // ios: WidgetShared/Models.swift:65
    if (v is String) return v.replace(",", "").toDoubleOrNull()   // ios: WidgetShared/Models.swift:66
    if (v is Number) return v.toDouble()   // ios: WidgetShared/Models.swift:67
    return null   // ios: WidgetShared/Models.swift:68
}

// Yahoo 받기. 앱·위젯이 Yahoo 를 직접 부르고, 안 되면 중계(naeilo Worker)로 한 번 더 (웹은 백업)
object Relay {   // ios: WidgetShared/Models.swift:72
    const val base = "https://asset-ai.drinker.workers.dev/?url="   // ios: WidgetShared/Models.swift:73
    const val host = "https://query1.finance.yahoo.com"   // ios: WidgetShared/Models.swift:74

    /** 못 받으면 IOException */
    suspend fun yahoo(path: String, timeout: Double = 15.0): Map<String, Any?> {   // ios: WidgetShared/Models.swift:76
        if (!Config.usesCollectedData) throw java.io.IOException("collected data off")   // 공개 베타·출시는 Yahoo 를 부르지 않는다   // ios: WidgetShared/Models.swift:77
        val target = host + path   // ios: WidgetShared/Models.swift:78
        runCatching { get(target, timeout, ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)") }.getOrNull()?.let { return it }   // ios: WidgetShared/Models.swift:78
        val enc = pctEncode(target)   // ios: WidgetShared/Models.swift:79
        val url = uriOrNull(base + enc) ?: throw IOException("bad url")   // ios: WidgetShared/Models.swift:80
        return get(url, timeout, ua = "naeilo-ios/0.1")   // ios: WidgetShared/Models.swift:81
    }

    private suspend fun get(url: String, timeout: Double, ua: String): Map<String, Any?> {   // ios: WidgetShared/Models.swift:84
        val req = FeedReq(url, timeout)   // ios: WidgetShared/Models.swift:85
        req.setValue(ua, "User-Agent")   // ios: WidgetShared/Models.swift:86
        val (code, data) = req.send()   // ios: WidgetShared/Models.swift:87
        if (code != 200) throw IOException("bad server response")   // ios: WidgetShared/Models.swift:88
        val o = jObj(feedJson(data)) ?: throw IOException("bad server response")   // ios: WidgetShared/Models.swift:89
        return o   // ios: WidgetShared/Models.swift:90
    }

    /** 지금 가격 하나 (사이트 quotes.json 과 같은 식: 오늘 5분봉에서 정규장·프리·애프터 중 마지막 값, 전일 종가) */
    suspend fun quote(sym: String): QuoteFeed.Q? {   // ios: WidgetShared/Models.swift:94
        val enc = pctEncode(sym)   // ios: WidgetShared/Models.swift:95
        val o = runCatching { yahoo("/v8/finance/chart/$enc?range=1d&interval=5m&includePrePost=true", timeout = 8.0) }.getOrNull() ?: return null   // ios: WidgetShared/Models.swift:96
        val r = jObjs(jObj(o["chart"])?.get("result"))?.firstOrNull() ?: return null   // ios: WidgetShared/Models.swift:97
        val m = jObj(r["meta"]) ?: return null; val reg = num(m["regularMarketPrice"])?.takeIf { it > 0 } ?: return null   // ios: WidgetShared/Models.swift:98
        val prev = num(m["chartPreviousClose"]) ?: num(m["previousClose"]) ?: return null   // ios: WidgetShared/Models.swift:99
        var last = reg; var at = num(m["regularMarketTime"]) ?: 0.0   // ios: WidgetShared/Models.swift:100
        val ts = jDbls(r["timestamp"]) ?: emptyList()   // ios: WidgetShared/Models.swift:101
        val cl = jArr(jObjs(jObj(r["indicators"])?.get("quote"))?.firstOrNull()?.get("close")) ?: emptyList()   // ios: WidgetShared/Models.swift:102
        val per = jObj(m["currentTradingPeriod"]) ?: emptyMap()   // ios: WidgetShared/Models.swift:103
        fun inside(k: String, t: Double): Boolean {   // ios: WidgetShared/Models.swift:104
            val p = jObj(per[k]) ?: return false; val a = num(p["start"]) ?: return false; val b = num(p["end"]) ?: return false   // ios: WidgetShared/Models.swift:105
            return a <= t && t < b   // ios: WidgetShared/Models.swift:106
        }
        for ((t, c) in ts.zip(cl)) {   // ios: WidgetShared/Models.swift:108
            val v = num(c)?.takeIf { it > 0 } ?: continue; if (!(inside("pre", t) || inside("post", t) || inside("regular", t))) continue   // ios: WidgetShared/Models.swift:109
            last = v; at = t   // ios: WidgetShared/Models.swift:110
        }
        return QuoteFeed.Q(last = last, prev = prev, at = at, reg = reg)   // ios: WidgetShared/Models.swift:112
    }
}

// 국내 개별 종목 시세 (KoreaEstimate.popular). 사이트 quotes.json 에 없어서 위젯도 앱처럼 Yahoo 중계로 직접 받는다
object KrQuoteFeed {   // ios: WidgetShared/Models.swift:117
    const val name = "kr_quotes.json"   // ios: WidgetShared/Models.swift:118
    fun cached(): QuoteFeed.File? = if (Config.usesCollectedData) Store.read(QuoteFeed.File.serializer(), name) else null   // ios: WidgetShared/Models.swift:120

    /** syms: 종목 → Yahoo 기호. 받은 지 minAge 초가 안 됐거나 받을 종목이 같으면 저장해 둔 값 */
    suspend fun fetch(syms: Map<String, String>, minAge: Double = 0.0): QuoteFeed.File? = withContext(Dispatchers.IO) { fetchIO(syms, minAge) }   // ios: WidgetShared/Models.swift:122,123
    private suspend fun fetchIO(syms: Map<String, String>, minAge: Double): QuoteFeed.File? {
        val old = cached()   // ios: WidgetShared/Models.swift:124
        if (syms.isEmpty()) return old   // ios: WidgetShared/Models.swift:125
        if (old != null && nowSec() - old.got < minAge && old.q.keys == syms.keys) return old   // ios: WidgetShared/Models.swift:126
        val q = mutableMapOf<String, QuoteFeed.Q>()   // ios: WidgetShared/Models.swift:127
        for ((id, y) in syms) {   // ios: WidgetShared/Models.swift:128
            val sym = pctEncode(y)   // ios: WidgetShared/Models.swift:129
            val o = runCatching { Relay.yahoo("/v8/finance/chart/$sym?range=5d&interval=1d") }.getOrNull() ?: continue   // ios: WidgetShared/Models.swift:130
            val r = jObjs(jObj(o["chart"])?.get("result"))?.firstOrNull() ?: continue   // ios: WidgetShared/Models.swift:131
            val meta = jObj(r["meta"]) ?: continue   // ios: WidgetShared/Models.swift:132
            val raw = jArr(jObjs(jObj(r["indicators"])?.get("quote"))?.firstOrNull()?.get("close"))   // ios: WidgetShared/Models.swift:133
                ?: continue   // ios: WidgetShared/Models.swift:134
            val cl = raw.mapNotNull { (it as? Double)?.takeIf { v -> v > 0 } }   // ios: WidgetShared/Models.swift:135
            val lc = cl.lastOrNull() ?: continue   // ios: WidgetShared/Models.swift:136
            // 앱(CustomSymbols.refresh)과 같게: 지금 가격 = 오늘 장중 값, 전일 = 그 전 거래일 종가
            val last = meta["regularMarketPrice"] as? Double ?: lc   // ios: WidgetShared/Models.swift:138
            q[id] = QuoteFeed.Q(last = last, prev = if (cl.size > 1) cl[cl.size - 2] else last, at = meta["regularMarketTime"] as? Double ?: nowSec())   // ios: WidgetShared/Models.swift:139
        }
        if (q.isEmpty()) return old   // ios: WidgetShared/Models.swift:141
        val f = QuoteFeed.File(got = nowSec(), q = q)   // ios: WidgetShared/Models.swift:142
        Store.write(QuoteFeed.File.serializer(), f, name)   // ios: WidgetShared/Models.swift:143
        return f   // ios: WidgetShared/Models.swift:144
    }
}

/// 내 토스 키로 받아 둔 일봉 (앱 TossHistory 가 쓰는 toss_history.json, 키 = Yahoo 기호). 토스 값의 전일 종가·정규장 종가를 여기서 센다
object TossDaily {   // ios: WidgetShared/Models.swift:563
    @Serializable data class Hist(val dates: List<String>, val closes: List<Double>)   // ios: WidgetShared/Models.swift:564
    @Serializable data class File(var h: Map<String, Hist>)   // ios: WidgetShared/Models.swift:565
    fun cached(): File? = if (TossKeys.on) Store.read(File.serializer(), "toss_history.json") else null   // ios: WidgetShared/Models.swift:566

    private fun day(t: Double, kr: Boolean): Pair<String, Int> {   // ios: WidgetShared/Models.swift:572
        val z = Instant.ofEpochMilli((t * 1000).toLong())   // ios: WidgetShared/Models.swift:573,574,575
            .atZone(ZoneId.of(if (kr) "Asia/Seoul" else "America/New_York"))
        return cformat("%04d-%02d-%02d", z.year, z.monthValue, z.dayOfMonth) to z.hour   // ios: WidgetShared/Models.swift:576
    }
    /** 토스 주간거래 시간(미국 동부 일요일 20시~금요일 4시 중 20시~4시)에 체결된 값인가. 금요일 20시 뒤·주말은 장이 없어 금요일 종가로 센다 (토스와 같게) */
    fun daytime(at: Double): Boolean { val h = day(at, kr = false).second; val w = Instant.ofEpochMilli((at * 1000).toLong()).atZone(ZoneId.of("America/New_York")).dayOfWeek.value % 7 + 1; return (h >= 20 && w <= 5) || (h < 4 && w >= 2 && w <= 6) }   // ios: WidgetShared/Models.swift:579
    /** 토스 값(at 시각)의 전일 종가: 그 거래일 앞 마지막 종가.
     *  미국 16시 뒤(애프터마켓·주간거래)는 다음 거래일 몫이라 그날 종가까지 넣는다 (4시 전은 이미 다음 날짜). 토스가 애프터마켓부터 오늘로 센다 (2026-10-27)
     *  국내는 그대로 9시 장이 열려 체결이 나야 오늘 */
    fun prev(h: Hist?, at: Double, kr: Boolean): Double? {   // ios: WidgetShared/Models.swift:583
        if (h == null || at <= 0) return null   // ios: WidgetShared/Models.swift:584
        val (d, hr) = day(at, kr)   // ios: WidgetShared/Models.swift:585
        val late = !kr && hr >= 16   // ios: WidgetShared/Models.swift:586
        val i = h.dates.indexOfLast { if (late) it <= d else it < d }.takeIf { it >= 0 } ?: return null   // ios: WidgetShared/Models.swift:587
        return h.closes.getOrNull(i)   // ios: WidgetShared/Models.swift:588
    }
    /** 미국 정규장 종가: 토스 값이 주간거래가 아니면 그날 일봉 종가 (그날 일봉을 아직 못 받았으면 null) */
    fun reg(h: Hist?, at: Double): Double? {   // ios: WidgetShared/Models.swift:591
        if (h == null || at <= 0 || daytime(at)) return null   // ios: WidgetShared/Models.swift:592
        val d = day(at, kr = false).first   // ios: WidgetShared/Models.swift:593
        val i = h.dates.lastIndexOf(d).takeIf { it >= 0 } ?: return null   // ios: WidgetShared/Models.swift:594
        return h.closes.getOrNull(i)   // ios: WidgetShared/Models.swift:595
    }
}

/** 미국 장이 다 끝난 때: 뉴욕 시각 평일 20시(애프터마켓 끝)부터 다음 날 4시(프리마켓 시작)까지와 주말.
 *  이때는 애프터마켓 가격 대신 정규장 종가로 센다. 토스 총자산·오늘 손익이 이렇게 센다 (2026-10-17 토스 화면과 맞춰 봄) */
object USSession {   // ios: WidgetShared/Models.swift:594
    fun closed(d: Instant = Instant.now()): Boolean {   // ios: WidgetShared/Models.swift:595
        val c = d.atZone(ZoneId.of("America/New_York")); val weekday = c.dayOfWeek.value % 7 + 1   // 1 일요일 … 7 토요일 (Calendar.weekday 와 같게)   // ios: WidgetShared/Models.swift:596,597,598
        return weekday == 7 || weekday == 1 || c.hour >= 20 || c.hour < 4   // 7 토요일, 1 일요일   // ios: WidgetShared/Models.swift:599
    }
}

/** 종목마다 마지막으로 받은 0보다 큰 시세 (앱·위젯 공용). 종목 id → [지금 가격, 전일 종가, 실시간 1/0, 받은 시각(초)].
 *  어느 시세에서 왔든 같은 규칙: 한 번이라도 받은 종목은 받기 실패·0·앱 재시작에도 이 값 밑으로 (0원으로) 내려가지 않는다 */
object GoodQuotes {   // ios: WidgetShared/Models.swift:778
    const val name = "good_quotes.json"   // ios: WidgetShared/Models.swift:779
    fun read(): Map<String, List<Double>> = (Store.read<Map<String, List<Double>>>(name) ?: emptyMap()).filter { it.value.size == 4 && it.value[0] > 0 }   // ios: WidgetShared/Models.swift:780
    fun write(d: Map<String, List<Double>>) = Store.write(d, name)   // ios: WidgetShared/Models.swift:781
}

// 지금 가격·원/달러 백업: 토스가 못 받는 내 종목(Store.quoteSyms)을 Yahoo 에서 직접 받는다. 그것도 못 받은 종목만 사이트(naeilo.com/data/quotes.json, 평일 30분마다 모음)로 채운다.
object QuoteFeed {   // ios: WidgetShared/Models.swift:772
    @Serializable data class Q(   // ios: WidgetShared/Models.swift:773
        var last: Double, var prev: Double, var at: Double,   // 지금 가격, 전일 종가, 가격 시각(초)   // ios: WidgetShared/Models.swift:774
        var reg: Double? = null,                              // 정규장 가격 (사이트 시세만. 미국 장이 다 끝나면 이 값으로 센다)   // ios: WidgetShared/Models.swift:775
    )
    @Serializable data class File(var got: Double, var q: Map<String, Q>)               // got = 받은 시각(초)   // ios: WidgetShared/Models.swift:777

    const val name = "site_quotes.json"   // ios: WidgetShared/Models.swift:779
    /** 공개 베타·출시는 사이트·Yahoo 시세를 쓰지 않는다 (내부 베타 때 받아 둔 파일이 남아 있어도 읽지 않는다) */
    fun cached(): File? = if (Config.usesCollectedData) Store.read(File.serializer(), name) else null   // ios: WidgetShared/Models.swift:783

    /** 받은 지 minAge 초가 안 됐으면 저장해 둔 값을 그대로 쓴다. 실패하면 저장해 둔 값 */
    suspend fun fetch(minAge: Double = 0.0): File? = withContext(Dispatchers.IO) { fetchIO(minAge) }   // ios: WidgetShared/Models.swift:783,784
    private suspend fun fetchIO(minAge: Double): File? {
        if (!Config.usesCollectedData) return null   // ios: WidgetShared/Models.swift:788
        val old = cached()   // ios: WidgetShared/Models.swift:789
        val list = Store.quoteSyms   // ios: WidgetShared/Models.swift:786
        if (list?.isEmpty() == true) return old   // 토스가 다 받는다   // ios: WidgetShared/Models.swift:787
        val want = (list ?: emptyList()).toSet()   // ios: WidgetShared/Models.swift:788
        if (old != null && nowSec() - old.got < minAge && old.q.keys.containsAll(want)) return old   // ios: WidgetShared/Models.swift:789
        val q = mutableMapOf<String, Q>()   // ios: WidgetShared/Models.swift:790
        coroutineScope {   // ios: WidgetShared/Models.swift:791
            val g = want.map { k -> async { k to Relay.quote(k) } }   // ios: WidgetShared/Models.swift:792
            for ((k, v) in g.awaitAll()) { if (v != null) q[k] = v }   // ios: WidgetShared/Models.swift:793
        }
        if (want.isNotEmpty() && q.keys.containsAll(want)) return save(q)   // ios: WidgetShared/Models.swift:795
        // 못 받은 종목은 사이트 값 (사이트도 안 되면 받아 둔 값)
        siteFetch()?.let { site -> for ((k, v) in site) if (q[k] == null) q[k] = v }   // ios: WidgetShared/Models.swift:797
        for ((k, v) in old?.q ?: emptyMap()) if (q[k] == null && (want.contains(k) || want.isEmpty())) q[k] = v   // ios: WidgetShared/Models.swift:798
        if (q.isEmpty()) return old   // ios: WidgetShared/Models.swift:799
        return save(q)   // ios: WidgetShared/Models.swift:800
    }

    private fun save(q: Map<String, Q>): File {   // ios: WidgetShared/Models.swift:803
        val f = File(got = nowSec(), q = q)   // ios: WidgetShared/Models.swift:804
        Store.write(File.serializer(), f, name)   // ios: WidgetShared/Models.swift:805
        return f   // ios: WidgetShared/Models.swift:806
    }

    private suspend fun siteFetch(): Map<String, Q>? {   // ios: WidgetShared/Models.swift:809
        val c = URL(Config.data, "quotes.json").toString()   // ios: WidgetShared/Models.swift:810
        val queryItems = listOf("t" to (nowSec() / 60).toLong().toString()); val url = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // 사이트 캐시를 피한다 (1분 단위)   // ios: WidgetShared/Models.swift:811
        val req = FeedReq(url, timeout = 10.0)   // ios: WidgetShared/Models.swift:812
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: WidgetShared/Models.swift:813
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null   // ios: WidgetShared/Models.swift:814
        if (code != 200) return null   // ios: WidgetShared/Models.swift:815
        val o = jObj(feedJson(data)) ?: return null   // ios: WidgetShared/Models.swift:816
        val q = mutableMapOf<String, Q>()   // ios: WidgetShared/Models.swift:817
        for ((k, any) in o) {   // 종목이 아닌 칸(예: 갱신 시각)이 생겨도 나머지는 읽는다   // ios: WidgetShared/Models.swift:818
            val v = jObj(any) ?: continue; val prev = num(v["prev_close"]) ?: continue; val last = num(v["last"]) ?: num(v["regular"]) ?: continue   // ios: WidgetShared/Models.swift:819
            q[k] = Q(last = last, prev = prev, at = num(v["last_time"]) ?: num(v["regular_time"]) ?: 0.0, reg = num(v["regular"]))   // ios: WidgetShared/Models.swift:820
        }
        return if (q.isEmpty()) null else q   // ios: WidgetShared/Models.swift:822
    }
}

// ── 시세 피드가 같이 쓰는 도구 (URLSession·JSONSerialization 자리) ──

private fun nowSec(): Double = System.currentTimeMillis() / 1000.0

/** URLRequest 자리: 주소·기다릴 초·방법·헤더·본문. send() 는 Dispatchers.IO 에서 보내고 (응답 코드, 본문) 을 준다.
 *  닿지 못하면 IOException (시간 초과는 SocketTimeoutException). 캐시는 쓰지 않는다 (.reloadIgnoringLocalCacheData) */
class FeedReq(val url: String, val timeout: Double) {
    var httpMethod = "GET"
    var httpBody: ByteArray? = null
    val headers = mutableMapOf<String, String>()
    fun setValue(value: String, field: String) { headers[field] = value }

    suspend fun send(): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            val ms = (timeout * 1000).toInt()
            c.connectTimeout = ms; c.readTimeout = ms
            c.useCaches = false
            c.requestMethod = httpMethod
            for ((k, v) in headers) c.setRequestProperty(k, v)
            httpBody?.let { b -> c.doOutput = true; c.outputStream.use { it.write(b) } }
            val code = c.responseCode
            val s = if (code in 200..399) c.inputStream else c.errorStream
            code to (s?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            c.disconnect()
        }
    }
}

/** String.toDoubleOrNull() 과 같은 값. 코틀린 것은 숫자마다 정규식(안드로이드는 ICU 네이티브)을 돌려 시세 JSON 을 읽을 때 네이티브 메모리가 수백 MB 튄다. 정규식 없이 바로 바꾼다 */
fun String.fastDouble(): Double? = try { java.lang.Double.parseDouble(this) } catch (_: NumberFormatException) { null }

/** JSON 본문 → Map<String, Any?> / List<Any?> / String / Double / Boolean / null (JSONSerialization 과 같은 꼴, 숫자는 모두 Double) */
fun feedJson(b: ByteArray): Any? = runCatching { plainJson(NJson.parseToJsonElement(b.decodeToString())) }.getOrNull()

private fun plainJson(e: JsonElement): Any? = when (e) {
    is JsonObject -> e.mapValues { plainJson(it.value) }
    is JsonArray -> e.map { plainJson(it) }
    is JsonNull -> null
    is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.content.fastDouble()
}

/** `as? [String: Any]` */
@Suppress("UNCHECKED_CAST")
fun jObj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

/** `as? [Any]` */
fun jArr(v: Any?): List<Any?>? = v as? List<Any?>

/** `as? [[String: Any]]` (하나라도 사전이 아니면 null) */
fun jObjs(v: Any?): List<Map<String, Any?>>? = jArr(v)?.let { l -> l.map { jObj(it) ?: return null } }

/** `as? [Double]` (하나라도 숫자가 아니면 null) */
fun jDbls(v: Any?): List<Double>? = jArr(v)?.let { l -> l.map { it as? Double ?: return null } }

/** addingPercentEncoding(withAllowedCharacters: .alphanumerics + keep) */
fun pctEncode(s: String, keep: String = ""): String {
    val sb = StringBuilder()
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val ch = (b.toInt() and 0xFF).toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || (b >= 0 && keep.indexOf(ch) >= 0)) sb.append(ch)
        else sb.append('%').append(String.format("%02X", b.toInt() and 0xFF))
    }
    return sb.toString()
}

/** URL(string:) 이 받는 주소면 그대로, 아니면 null */
fun uriOrNull(s: String): String? = runCatching { URI(s); s }.getOrNull()

/** Date(_, strategy: .iso8601) → 1970 기준 초 */
fun isoSec(s: String): Double? = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() / 1000.0 }.getOrNull()
