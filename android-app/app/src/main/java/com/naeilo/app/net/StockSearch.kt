package com.naeilo.app.net

import com.naeilo.app.core.cformat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.Config
import com.naeilo.app.core.Day
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Relay
import com.naeilo.app.core.Store
import com.naeilo.app.core.SwiftDate
import com.naeilo.app.core.calc.MC
import com.naeilo.app.core.jArr
import com.naeilo.app.core.jDbls
import com.naeilo.app.core.jObj
import com.naeilo.app.core.jObjs
import com.naeilo.app.core.pctEncode
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

// 종목 찾기·추가 (테스트: 사이트가 쓰는 Yahoo 중계).
// - 찾기: 앱에 넣어 둔 한글 이름 목록(StockCatalog) + Yahoo 검색(영문 이름·티커·종목 코드). 미국 주식·ETF 와 코스피·코스닥만.
// - 추가: 고르는 순간 3년 일별 종가를 받아 기기에 저장하고, 그 뒤로는 앱이 시세를 갱신할 때 같이 갱신한다.
// - 미국 종목은 지금 가격. 한국 종목은 시세를 받지 않고 사용자가 넣은 가격 하나로 계산한다 (KoreaEstimate).

private fun nowSec(): Double = System.currentTimeMillis() / 1000.0

/** custom_symbols.json 은 아이폰 JSONEncoder 기본 날짜(2001 기준 초). 백업 파일은 iso8601 (Backup.kt BackupJson) */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal val CsJson = Json(NJson) { serializersModule = SerializersModule { contextual(SwiftDate) } }

data class StockHit(   // ios: Data/StockSearch.swift:9
    val id: String,          // 앱 안의 이름: 미국은 티커, 한국은 6자리 코드   // ios: Data/StockSearch.swift:10
    val yahoo: String,       // Yahoo 기호 (005930.KS)   // ios: Data/StockSearch.swift:11
    val name: String,   // ios: Data/StockSearch.swift:12
    val alias: String = "",   // ios: Data/StockSearch.swift:13
    val en: String = "",   // ios: Data/StockSearch.swift:14
    val market: String,   // ios: Data/StockSearch.swift:15
    val currency: Currency,   // ios: Data/StockSearch.swift:16
)

/** 한국 종목 (웹 app.js synthKR · data/kr_proxy.json 과 같은 규칙): 한국 가격은 받지 않고 사용자가 넣은 가격 하나에 맞춘다
 *  - 미국 지수를 따르는 국내 상장 ETF: 그 미국 ETF(SPY·QQQ 등) × 원/달러 움직임으로 오늘 가격을 추정 (환헤지 (H)면 환율 빼고)
 *  - 그 밖의 국내 종목은 종목별로 넣지 않는다. 국내 주식 합계 (시세 기준 화면에서 켠 경우만): 증권사 앱의 합계 금액 그대로,
 *    지난 움직임·변동성은 미국 상장 EWY(MSCI Korea) × 원/달러로 대신 (웹 KOREA.KS 줄과 같은 종목 KOREA) */
object KoreaEstimate {   // ios: Data/StockSearch.swift:23
    const val market = "EWY"   // ios: Data/StockSearch.swift:24
    const val exclude = "레버리지|인버스|2X|커버드콜|액티브|Leverage|Inverse|Covered|Active"   // ios: Data/StockSearch.swift:25
    const val hedged = "\\(H\\)|Hedged"   // Data/StockSearch.swift:26 (검사 예외: 옮김)
    data class Rule(val match: String, val proxy: String, val label: String)
    val rules: List<Rule> = listOf(   // ios: Data/StockSearch.swift:27
        Rule("S&P\\s?500|에스앤피500", "SPY", "S&P500"),   // ios: Data/StockSearch.swift:28
        Rule("나스닥\\s?100|NASDAQ\\s?100", "QQQ", "나스닥100"),   // ios: Data/StockSearch.swift:29
        Rule("배당다우존스|Dividend\\s?(Equity|Dow)", "SCHD", "미국 배당다우존스"),   // ios: Data/StockSearch.swift:30
        Rule("필라델피아반도체|Philadelphia\\s?Semi|PHLX\\s?Semi", "SOXX", "필라델피아 반도체"),   // ios: Data/StockSearch.swift:31
    )
    data class Info(val proxy: String?, val hedged: Boolean) { val base: String get() = proxy ?: KoreaEstimate.market }   // ios: Data/StockSearch.swift:33

    fun isKR(yahoo: String): Boolean { val u = yahoo.uppercase(); return u.endsWith(".KS") || u.endsWith(".KQ") }   // ios: Data/StockSearch.swift:35
    private val reCache = HashMap<String, Regex>()
    private fun has(re: String, s: String): Boolean = synchronized(reCache) { reCache.getOrPut(re) { Regex(re, RegexOption.IGNORE_CASE) } }.containsMatchIn(s)   // ios: Data/StockSearch.swift:36
    fun info(names: List<String>): Info {   // ios: Data/StockSearch.swift:37
        val t = names.joinToString(" ")   // ios: Data/StockSearch.swift:38
        val r = if (has(exclude, t)) null else rules.firstOrNull { has(it.match, t) }; if (r == null) return Info(proxy = null, hedged = false)   // ios: Data/StockSearch.swift:39
        return Info(proxy = r.proxy, hedged = has(hedged, t))   // ios: Data/StockSearch.swift:40
    }
    fun info(id: String, name: String, en: String = ""): Info {   // ios: Data/StockSearch.swift:42
        val ko = StockCatalog.all.firstOrNull { it.id == id }   // ios: Data/StockSearch.swift:43
        return info(listOf(name, en, ko?.name ?: "", ko?.en ?: ""))   // ios: Data/StockSearch.swift:44
    }
    /** 국내 주식 합계 한 줄 (사이트 KOREA.KS, 수량 1·가격 = 합계 금액) */
    const val sumId = "KOREA"   // ios: Data/StockSearch.swift:47
    /** 국내 개별 종목: 앱 한글 목록(StockCatalog)의 국내 주식 (ETF 빼고). 사이트가 매일 지금 가격·종가를 모은다
     *  (data/kr_stocks.json 과 같은 규칙, tools/gen_stock_catalog.py). 토스 시세를 받는 기기는 토스 값이 먼저 */
    val popular: Set<String> by lazy { StockCatalog.all.filter { it.currency == Currency.krw && !it.market.contains("ETF") }.map { it.id }.toSet() }   // ios: Data/StockSearch.swift:50
    /** 그 밖의 국내 종목(미국 지수 국내 ETF 빼고)은 추가하는 순간 Yahoo 중계(asset-ai 워커)로 지금 가격·1년 종가를 받고, 앱이 시세를 갱신할 때 같이 갱신한다.
     *  중계가 실패하면 넣은 가격 방식 */
    fun liveOK(id: String, name: String = ""): Boolean = id != sumId && (popular.contains(id) || info(id = id, name = name).proxy == null)   // ios: Data/StockSearch.swift:53
    fun allowed(h: StockHit): Boolean = h.currency == Currency.usd || liveOK(h.id, name = h.name) || info(id = h.id, name = h.name, en = h.en).proxy != null   // ios: Data/StockSearch.swift:54
    fun tag(h: StockHit): String = if (h.currency == Currency.usd) "" else if (liveOK(h.id, name = h.name)) " · 국내 시세" else " · 미국 지수로 추정"   // ios: Data/StockSearch.swift:55
    /** 내 한국 종목이 쓰는 사이트 티커 (SPY·EWY 등) */
    fun baseKeys(held: Set<String>): Set<String> =   // ios: Data/StockSearch.swift:57
        CustomSymbols.shared.items.filter { held.contains(it.id) && !it.usd }.map { info(id = it.id, name = it.name).base }.toSet()   // ios: Data/StockSearch.swift:58
    /** 넣은 가격(원)과 날짜로 날짜별 가격을 만든다 (웹 synthKR 과 같은 식: 기준 종가 × 원/달러, 넣은 날 값이 넣은 가격이 되게) */
    fun synth(price: Double, date: String, info: Info, P: Map<String, YahooSample.Hist>? = Market.shared.prices?.h): CustomSymbols.Hist? {   // ios: Data/StockSearch.swift:61
        if (!(price > 0) || P == null) return null; val base = P[info.base]?.takeIf { it.dates.isNotEmpty() } ?: return null   // ios: Data/StockSearch.swift:62
        val fx = P["KRW=X"]   // ios: Data/StockSearch.swift:63
        val fm = HashMap<String, Double>()   // ios: Data/StockSearch.swift:64
        if (fx != null) { for ((d, v) in fx.dates.zip(fx.closes)) fm[d] = v }   // ios: Data/StockSearch.swift:65
        var f = fx?.closes?.firstOrNull() ?: 1.0; val dates = ArrayList<String>(); val raw = ArrayList<Double>()   // ios: Data/StockSearch.swift:66
        for ((d, v) in base.dates.zip(base.closes)) {   // ios: Data/StockSearch.swift:67
            fm[d]?.let { x -> f = x }   // ios: Data/StockSearch.swift:68
            if (info.proxy == null && d > date) continue   // ios: Data/StockSearch.swift:69
            dates.add(d); raw.add(v * (if (info.hedged) 1.0 else f))   // ios: Data/StockSearch.swift:70
        }
        if (dates.isEmpty()) return null   // ios: Data/StockSearch.swift:72
        var j = dates.size - 1   // ios: Data/StockSearch.swift:73
        while (j > 0 && dates[j] > date) j -= 1   // ios: Data/StockSearch.swift:74
        if (!(raw[j] > 0)) return null   // ios: Data/StockSearch.swift:75
        val k = price / raw[j]   // ios: Data/StockSearch.swift:76
        return CustomSymbols.Hist(dates = dates, closes = raw.map { it * k })   // ios: Data/StockSearch.swift:77
    }
    /** 종목 화면 설명 */
    fun note(s: CustomSymbols.Saved): String {   // ios: Data/StockSearch.swift:80
        val i = info(id = s.id, name = s.name); val at = s.kd ?: ""   // ios: Data/StockSearch.swift:81
        if (CustomSymbols.shared.isLive(s.id)) return if (KoreaEstimate.popular.contains(s.id)) "사이트가 매일 모으는 가격이에요. 1년 기록은 모으기 시작한 날부터 쌓여요." else "시세를 갱신할 때 같이 갱신해요."   // ios: Data/StockSearch.swift:82
        val p = s.kp ?: return "한국 시세는 받지 않아요. 지금 가격을 한 번 넣으면 계산해요."   // ios: Data/StockSearch.swift:83
        val won = cformat("%,d", swRound(p).toInt()) + "원"   // ios: Data/StockSearch.swift:84
        if (s.id == sumId) return "${at}에 넣은 ${won} · 전망은 EWY×원/달러"   // ios: Data/StockSearch.swift:85
        return if (i.proxy != null) "${at}에 넣은 ${won}에서 ${i.base}${if (i.hedged) "" else " × 원/달러"} 움직임으로 추정한 값이에요."   // Data/StockSearch.swift:86 (검사 예외: 옮김)
            else "${at}에 넣은 ${won} 그대로예요. 움직임 예측은 한국 시장 평균(${i.base} × 원/달러)으로 해요."   // ios: Data/StockSearch.swift:87
    }
}

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
private fun swRound(x: Double): Double = if (x < 0) -Math.floor(-x + 0.5) else Math.floor(x + 0.5)

object StockSearch {   // ios: Data/StockSearch.swift:91
    private val usEx: Set<String> = setOf("NMS", "NYQ", "NGM", "NCM", "PCX", "ASE", "BTS", "NAS", "NYS")   // ios: Data/StockSearch.swift:92

    /** 찾는 말 정리: 공백 빼고 소문자 */
    fun norm(s: String): String = s.lowercase().replace(" ", "")   // ios: Data/StockSearch.swift:95

    /** 앱 안 목록에서 찾기 (예시 종목 + 넣어 둔 한글 이름 + 전에 추가한 종목) */
    fun local(q: String): List<StockHit> {   // ios: Data/StockSearch.swift:98
        val syms = (Sample.symbols + CustomSymbols.shared.symbols).map { s ->   // ios: Data/StockSearch.swift:103
            "${s.id}${s.name}${s.search}" to StockHit(id = s.id, yahoo = CustomSymbols.yahoo(s), name = s.name, market = s.market, currency = s.currency)   // ios: Data/StockSearch.swift:104,105
        }
        val mine = CustomSymbols.shared.items.map { it.id }.toSet()   // ios: Data/StockSearch.swift:113
        return local(q, syms, mine, KrxList.all)
    }
    /** 찾기 본체 (종목 목록을 받아서. 숫자 맞추기 시험이 아이폰과 같은 목록으로 부른다) */
    internal fun local(q: String, syms: List<Pair<String, StockHit>>, mine: Set<String>, krx: List<KrxList.Row>): List<StockHit> {
        val k = norm(q)   // ios: Data/StockSearch.swift:99
        val out = ArrayList<StockHit>()   // ios: Data/StockSearch.swift:100
        val seen = HashSet<String>()   // ios: Data/StockSearch.swift:101
        fun add(h: StockHit) { if (seen.add(h.id)) out.add(h) }   // ios: Data/StockSearch.swift:102
        for ((key, h) in syms) {   // ios: Data/StockSearch.swift:103
            if (!(k.isEmpty() || norm(key).contains(k))) continue   // ios: Data/StockSearch.swift:104
            add(h)   // ios: Data/StockSearch.swift:105
        }
        if (k.isNotEmpty()) {   // ios: Data/StockSearch.swift:107
            for (h in StockCatalog.all) if (norm("${h.id}${h.name}${h.alias}${h.en}").contains(k)) add(h)   // ios: Data/StockSearch.swift:108
            // 코스피·코스닥 전체 상장 종목 (이름이 찾는 말로 시작하면 먼저, 너무 많으면 앞의 40개만)
            val hits = krx.filter { it.key.contains(k) }   // ios: Data/StockSearch.swift:110
            for (h in (hits.filter { it.key.startsWith(k) || it.hit.id.startsWith(k) } + hits).take(40)) add(h.hit)   // ios: Data/StockSearch.swift:111
        }
        return out.filter { mine.contains(it.id) || KoreaEstimate.allowed(it) }   // ios: Data/StockSearch.swift:114
    }

    /** Yahoo 검색 (한글은 못 찾는다) */
    suspend fun remote(q: String): List<StockHit> = withContext(Dispatchers.IO) {   // ios: Data/StockSearch.swift:118
        val k = q.trim(' ', '\t')   // ios: Data/StockSearch.swift:119
        if (!(k.length >= 1 && k.all { it.code < 128 })) return@withContext emptyList()   // ios: Data/StockSearch.swift:120
        val qs = pctEncode(k)   // ios: Data/StockSearch.swift:121
        val o = runCatching { Relay.yahoo("/v1/finance/search?q=$qs&quotesCount=12&newsCount=0") }.getOrNull()   // ios: Data/StockSearch.swift:122
        val quotes = jObjs(o?.get("quotes")) ?: return@withContext emptyList()   // ios: Data/StockSearch.swift:123
        quotes.mapNotNull { q -> remoteHit(q) }.filter(KoreaEstimate::allowed)   // ios: Data/StockSearch.swift:124,138
    }
    /** 검색 결과 한 줄 → 종목 (Yahoo 없이 시험할 수 있게 따로 둔다) */
    internal fun remoteHit(q: Map<String, Any?>): StockHit? {
        val sym = q["symbol"] as? String; val type = q["quoteType"] as? String; if (sym == null || type == null || !(type == "EQUITY" || type == "ETF")) return null   // ios: Data/StockSearch.swift:125
        val ex = q["exchange"] as? String ?: ""   // ios: Data/StockSearch.swift:126
        val name = (q["longname"] as? String) ?: (q["shortname"] as? String) ?: sym   // ios: Data/StockSearch.swift:127
        if (sym.endsWith(".KS") || sym.endsWith(".KQ")) {   // ios: Data/StockSearch.swift:128
            val code = sym.take(6)   // ios: Data/StockSearch.swift:129
            // 한글 이름을 아는 종목이면 한글로
            val ko = StockCatalog.all.firstOrNull { it.id == code }?.name ?: KrxList.name(code)   // ios: Data/StockSearch.swift:131
            return StockHit(id = code, yahoo = sym, name = ko ?: name, en = name,   // ios: Data/StockSearch.swift:132
                market = (if (sym.endsWith(".KS")) "코스피" else "코스닥") + (if (type == "ETF") " ETF" else ""), currency = Currency.krw)   // ios: Data/StockSearch.swift:133
        }
        if (!usEx.contains(ex) || sym.contains(".")) return null   // ios: Data/StockSearch.swift:135
        val ko = StockCatalog.all.firstOrNull { it.id == sym }   // ios: Data/StockSearch.swift:136
        return StockHit(id = sym, yahoo = sym, name = ko?.name ?: name, en = name, market = if (type == "ETF") "미국 ETF" else "미국 주식", currency = Currency.usd)   // ios: Data/StockSearch.swift:137
    }
}

/** 코스피·코스닥 전체 상장 종목 (assets/krx_stocks.json, tools/gen_krx_list.py 가 KRX KIND 목록으로 만든다).
 *  한글 이름으로 찾기용. [[코드, 이름, "K"|"Q"]], K = 코스피(.KS), Q = 코스닥(.KQ). 처음 찾을 때 한 번만 읽는다 */
object KrxList {   // ios: Data/StockSearch.swift:144
    data class Row(val key: String, val hit: StockHit)   // ios: Data/StockSearch.swift:145
    val all: List<Row> by lazy {   // ios: Data/StockSearch.swift:146
        val d = Defaults.asset("krx_stocks", "json")   // ios: Data/StockSearch.swift:147,148
        val rows = d?.let { runCatching { NJson.decodeFromString(ListSerializer(ListSerializer(String.serializer())), it) }.getOrNull() } ?: return@lazy emptyList()   // ios: Data/StockSearch.swift:149
        rows.mapNotNull { r -> parse(r) }   // ios: Data/StockSearch.swift:150
    }   // ios: Data/StockSearch.swift:156
    internal fun parse(r: List<String>): Row? {
        if (r.size < 3) return null   // ios: Data/StockSearch.swift:151
        val ks = r[2] == "K"   // ios: Data/StockSearch.swift:152
        val h = StockHit(id = r[0], yahoo = r[0] + (if (ks) ".KS" else ".KQ"), name = r[1], market = if (ks) "코스피" else "코스닥", currency = Currency.krw)   // ios: Data/StockSearch.swift:153
        return Row(key = StockSearch.norm(r[0] + r[1]), hit = h)   // ios: Data/StockSearch.swift:154
    }
    private val byId: Map<String, StockHit> by lazy { val m = HashMap<String, StockHit>(); for (r in all) m.putIfAbsent(r.hit.id, r.hit); m }   // ios: Data/StockSearch.swift:157
    fun name(code: String): String? = byId[code]?.name   // ios: Data/StockSearch.swift:158
    fun hit(code: String): StockHit? = byId[code]   // ios: Data/StockSearch.swift:159
}

/** 사이트가 매일 모으는 국내 개별 종목 종가 (data/kr_daily.json, server.py collect_kr). 모으기 시작한 날부터 하루 한 칸, 최근 1년 */
object KrDaily {   // ios: Data/StockSearch.swift:163
    @Serializable data class Row(val d: List<String>, val c: List<Double>)   // ios: Data/StockSearch.swift:164
    /** got 은 기본값이 있어도 아이폰 Codable 처럼 키가 있어야 읽힌다 (사이트 파일에는 없다: 아이폰과 같게 둔다) */
    @Serializable data class File(var got: Double, var s: Map<String, Row>) { constructor(s: Map<String, Row>) : this(got = 0.0, s = s) }   // ios: Data/StockSearch.swift:165
    const val name = "kr_daily.json"   // ios: Data/StockSearch.swift:166
    /** 이만큼(약 한 달) 쌓이기 전에는 "기록 모으는 중" */
    const val enough = 20   // ios: Data/StockSearch.swift:168
    /** 사이트가 Yahoo 로 모은 자료라 내부 베타만 */
    fun cached(): File? = if (Config.usesCollectedData) Store.read(File.serializer(), name) else null   // ios: Data/StockSearch.swift:170

    /** 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값 */
    suspend fun fetch(minAge: Double = 0.0): File? = withContext(Dispatchers.IO) {   // ios: Data/StockSearch.swift:172
        if (!Config.usesCollectedData) return@withContext null   // ios: Data/StockSearch.swift:174
        val old = cached()   // ios: Data/StockSearch.swift:175
        if (old != null && nowSec() - old.got < minAge) return@withContext old   // ios: Data/StockSearch.swift:174
        val c = URL(Config.data, name).toString()   // ios: Data/StockSearch.swift:175
        val queryItems = listOf("t" to (nowSec() / 60).toLong().toString()); val url = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // ios: Data/StockSearch.swift:176
        val req = FeedReq(url, timeout = 10.0)   // ios: Data/StockSearch.swift:177
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: Data/StockSearch.swift:178
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return@withContext old   // ios: Data/StockSearch.swift:179
        val f = if (code == 200) runCatching { NJson.decodeFromString(File.serializer(), data.decodeToString()) }.getOrNull() else null; if (f == null) return@withContext old   // ios: Data/StockSearch.swift:180,181
        f.got = nowSec()   // ios: Data/StockSearch.swift:182
        Store.write(File.serializer(), f, name)   // ios: Data/StockSearch.swift:183
        f   // ios: Data/StockSearch.swift:184
    }
}

/** 사용자가 추가한 종목 (예시 목록 밖). 이름·시세·3년 종가를 기기에 저장한다 */
class CustomSymbols {   // ios: Data/StockSearch.swift:189,190
    companion object {
        val shared: CustomSymbols by lazy { CustomSymbols() }   // ios: Data/StockSearch.swift:191
        private val file: File get() = File(Defaults.ctx.filesDir, "custom_symbols.json")   // ios: Data/StockSearch.swift:209,211,212
        fun yahoo(s: Symbol): String {   // ios: Data/StockSearch.swift:243
            shared.items.firstOrNull { it.id == s.id }?.let { return it.yahoo }   // ios: Data/StockSearch.swift:244
            StockCatalog.all.firstOrNull { it.id == s.id }?.let { return it.yahoo }   // ios: Data/StockSearch.swift:245
            KrxList.hit(s.id)?.let { return it.yahoo }   // ios: Data/StockSearch.swift:246
            return if (s.currency == Currency.krw) s.id + ".KS" else s.id   // ios: Data/StockSearch.swift:247
        }

        /** Yahoo 일별 차트: (지금 가격·전일 종가, 날짜별 종가)
         *  today: 한국 종목도 오늘 장중 값(Yahoo 지연 시세)을 쓴다 (내부 테스트 시세) */
        suspend fun chart(yahoo: String, range: String, today: Boolean = false): Pair<LastPrev, Hist> = withContext(Dispatchers.IO) {   // ios: Data/StockSearch.swift:422
            val sym = pctEncode(yahoo)   // ios: Data/StockSearch.swift:423
            val o = Relay.yahoo("/v8/finance/chart/$sym?range=$range&interval=1d")   // ios: Data/StockSearch.swift:424
            chartParse(o, today, Instant.now())
        }
        /** 차트 응답 → 값 (Yahoo 없이 시험할 수 있게 따로 둔다). 못 읽으면 IOException (URLError.cannotParseResponse) */
        internal fun chartParse(o: Map<String, Any?>, keepToday: Boolean, now: Instant): Pair<LastPrev, Hist> {
            val r = jObjs(jObj(o["chart"])?.get("result"))?.firstOrNull()   // ios: Data/StockSearch.swift:425
            val meta = jObj(r?.get("meta"))   // ios: Data/StockSearch.swift:426
            val ts = jDbls(r?.get("timestamp"))   // ios: Data/StockSearch.swift:427
            val closes = jArr(jObjs(jObj(r?.get("indicators"))?.get("quote"))?.firstOrNull()?.get("close"))   // ios: Data/StockSearch.swift:428
            if (meta == null || ts == null || closes == null) throw IOException("cannotParseResponse")   // ios: Data/StockSearch.swift:429
            val cur = meta["currency"] as? String ?: ""   // ios: Data/StockSearch.swift:430
            if (!(cur == "USD" || cur == "KRW")) throw IOException("cannotParseResponse")   // ios: Data/StockSearch.swift:431
            val tz = zoneOr((meta["exchangeTimezoneName"] as? String) ?: "America/New_York")   // ios: Data/StockSearch.swift:432
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", posix("en_US_POSIX")).withZone(tz)   // ios: Data/StockSearch.swift:433
            val dates = ArrayList<String>(); val cl = ArrayList<Double>()   // ios: Data/StockSearch.swift:434
            for ((t, c) in ts.zip(closes)) {   // ios: Data/StockSearch.swift:435
                val v = (c as? Double)?.takeIf { it > 0 } ?: continue   // ios: Data/StockSearch.swift:436
                dates.add(f.format(Instant.ofEpochMilli((t * 1000).toLong()))); cl.add(v)   // ios: Data/StockSearch.swift:437
            }
            val lastClose = cl.lastOrNull() ?: throw IOException("cannotParseResponse")   // ios: Data/StockSearch.swift:439
            val price = meta["regularMarketPrice"] as? Double ?: lastClose   // ios: Data/StockSearch.swift:440
            val today = f.format(now)   // ios: Data/StockSearch.swift:441
            // 마지막 막대가 오늘이면 그 전날이 전일 종가
            val prevClose = if (dates.lastOrNull() == today && cl.size > 1) cl[cl.size - 2] else lastClose   // ios: Data/StockSearch.swift:443
            if (cur == "KRW" && !keepToday) {   // ios: Data/StockSearch.swift:444
                // 한국 종목은 전일 종가 기준 (오늘 장중 값은 쓰지 않는다)
                val done = if (dates.lastOrNull() == today && cl.size > 1) cl.dropLast(1) else cl   // ios: Data/StockSearch.swift:446
                val dd = if (dates.lastOrNull() == today && cl.size > 1) dates.dropLast(1) else dates   // ios: Data/StockSearch.swift:447
                val p = done.lastOrNull() ?: lastClose   // ios: Data/StockSearch.swift:448
                return LastPrev(p, p) to Hist(dates = dd, closes = done)   // ios: Data/StockSearch.swift:449
            }
            return LastPrev(price, prevClose) to Hist(dates = dates, closes = cl)   // ios: Data/StockSearch.swift:451
        }
    }
    /** Swift 튜플 (last, prev) */
    data class LastPrev(val last: Double, val prev: Double)

    @Serializable
    data class Saved(   // ios: Data/StockSearch.swift:193
        val id: String, val yahoo: String, val name: String, val market: String, val usd: Boolean,   // ios: Data/StockSearch.swift:194
        var prev: Double, var last: Double, @Contextual var at: Instant,   // ios: Data/StockSearch.swift:195
        var kp: Double? = null, var kd: String? = null,     // 한국 종목: 사용자가 넣은 가격(원)과 그날   // ios: Data/StockSearch.swift:196
        var lv: Boolean? = null,                          // 한국 종목: 사이트가 모으는 국내 개별 종목 (KoreaEstimate.live)   // ios: Data/StockSearch.swift:197
    )
    @Serializable data class Hist(val dates: List<String>, val closes: List<Double>)   // ios: Data/StockSearch.swift:199

    var items: List<Saved> by mutableStateOf(emptyList())   // ios: Data/StockSearch.swift:201
        private set
    var hist: Map<String, Hist> by mutableStateOf(emptyMap())   // ios: Data/StockSearch.swift:202
        private set
    /** 국내 주식 합계 날짜별 금액 (사이트 kr_log 와 같은 칸, MC.krNorm). 지난 기록은 현금처럼: 첫 기록 전은 0. 전망은 KR~EWY 움직임으로 */
    private var _krLog by mutableStateOf(Defaults.prefs.getString("krLog", null)?.let { runCatching { NJson.decodeFromString(ListSerializer(MC.KrDay.serializer()), it) }.getOrNull() } ?: emptyList())   // ios: Data/StockSearch.swift:204
    var krLog: List<MC.KrDay>
        get() = _krLog
        private set(v) { _krLog = v; runCatching { NJson.encodeToString(ListSerializer(MC.KrDay.serializer()), v) }.getOrNull()?.let { Defaults.prefs.edit().putString("krLog", it).apply() } }   // ios: Data/StockSearch.swift:205
    private var refreshedAt = distantPast   // ios: Data/StockSearch.swift:207

    @Serializable private data class Disk(var items: List<Saved>, var hist: Map<String, Hist>)   // ios: Data/StockSearch.swift:214

    init {   // ios: Data/StockSearch.swift:216
        val disk = runCatching { CsJson.decodeFromString(Disk.serializer(), file.readText()) }.getOrNull()   // ios: Data/StockSearch.swift:217
        if (disk != null) {
            items = disk.items; val h = disk.hist.toMutableMap()   // ios: Data/StockSearch.swift:218
            // 한국 종목은 시세를 받지 않는다: 예전에 Yahoo 에서 받아 둔 종가는 버리고, 넣은 가격으로 다시 만든다 (rebuildKR)
            for (s in items) if (!s.usd && !(s.lv == true && KoreaEstimate.liveOK(s.id, name = s.name))) h.remove(s.id)   // ios: Data/StockSearch.swift:220
            hist = h
        }
        // 기록 없이 금액 하나만 있던 예전 저장값은 그 금액·날짜를 첫 줄로
        val s = items.firstOrNull { it.id == KoreaEstimate.sumId }; val p = s?.kp   // ios: Data/StockSearch.swift:223
        if (krLog.isEmpty() && s != null && p != null && p > 0) krLog = listOf(MC.KrDay(d = s.kd ?: Day.today, v = p))
    }
    /** 내 폰에 저장한 파일에서 불러오기: 같은 종목은 파일 값으로 */
    fun restore(new: List<Saved>, h: Map<String, Hist>) {   // ios: Data/StockSearch.swift:226
        items = items.filter { s -> new.none { it.id == s.id } } + new   // ios: Data/StockSearch.swift:227
        hist = hist + h   // ios: Data/StockSearch.swift:228
        save()   // ios: Data/StockSearch.swift:229
    }
    private fun save() {   // ios: Data/StockSearch.swift:231
        val d = runCatching { CsJson.encodeToString(Disk.serializer(), Disk(items = items, hist = hist)) }.getOrNull() ?: return   // ios: Data/StockSearch.swift:232
        runCatching { val tmp = File(file.path + ".tmp"); tmp.writeText(d); if (!tmp.renameTo(file)) { file.writeText(d); tmp.delete() } }   // .atomic
    }

    val symbols: List<Symbol> get() = items.filter { s -> Sample.baseSymbols.none { it.id == s.id } }.map(::symbol)   // ios: Data/StockSearch.swift:235
    private fun symbol(s: Saved): Symbol =   // ios: Data/StockSearch.swift:236
        Symbol(id = s.id, name = s.name, market = s.market, close = s.prev, currency = if (s.usd) Currency.usd else Currency.krw, sector = "", search = s.yahoo)   // ios: Data/StockSearch.swift:237
    fun symbol(id: String): Symbol? = items.firstOrNull { it.id == id }?.let(::symbol)   // ios: Data/StockSearch.swift:239
    fun quote(id: String): Quote? =   // ios: Data/StockSearch.swift:240
        items.firstOrNull { it.id == id }?.let { Quote(last = it.last, prevClose = it.prev, live = it.usd) }   // ios: Data/StockSearch.swift:241

    private fun replace(id: String, s: Saved) { items = items.filter { it.id != id } + s }
    private fun update(id: String, f: (Saved) -> Saved) { items = items.map { if (it.id == id) f(it.copy()) else it } }

    /** 3년 종가를 받아 저장 (추가할 때). 실패하면 이유를 던진다 */
    suspend fun fetch(h: StockHit): Symbol {   // ios: Data/StockSearch.swift:251,252
        val kr = if (h.currency == Currency.krw && KoreaEstimate.liveOK(h.id, name = h.name)) runCatching { chart(h.yahoo, range = "1y", today = true) }.getOrNull() else null   // ios: Data/StockSearch.swift:253
        if (kr != null) {
            val (q, hs) = kr
            // 국내 종목: 추가하는 순간 Yahoo 에서 직접 지금 가격·1년 종가 (안 되면 중계)
            val pc = if (hs.closes.size > 1) hs.closes[hs.closes.size - 2] else q.prev   // ios: Data/StockSearch.swift:255
            val s = Saved(id = h.id, yahoo = h.yahoo, name = h.name, market = h.market, usd = false, prev = pc, last = q.last, at = Instant.now())   // ios: Data/StockSearch.swift:256
            s.kp = q.last; s.kd = hs.dates.lastOrNull(); s.lv = true   // ios: Data/StockSearch.swift:257
            val done = s   // ios: Data/StockSearch.swift:258
            synchronized(this) {   // ios: Data/StockSearch.swift:259
                items = items.filter { it.id != h.id }   // ios: Data/StockSearch.swift:260
                items = items + done   // ios: Data/StockSearch.swift:261
                hist = hist + (h.id to hs)   // ios: Data/StockSearch.swift:262
                save()   // ios: Data/StockSearch.swift:263
            }
            return symbol(s)   // ios: Data/StockSearch.swift:265
        }
        if (h.currency == Currency.krw && KoreaEstimate.popular.contains(h.id)) {   // ios: Data/StockSearch.swift:267
            // 국내 개별 종목인데 Yahoo 를 못 받았을 때만: 사이트가 모은 지금 가격(quotes.json)과 날짜별 종가(kr_daily.json)
            val k = KrDaily.fetch(minAge = 1800.0)   // ios: Data/StockSearch.swift:269
            synchronized(this) {   // ios: Data/StockSearch.swift:270
                val s = Saved(id = h.id, yahoo = h.yahoo, name = h.name, market = h.market, usd = false, prev = 0.0, last = 0.0, at = distantPast)   // ios: Data/StockSearch.swift:271
                s.lv = true   // ios: Data/StockSearch.swift:272
                items = items.filter { it.id != h.id }   // ios: Data/StockSearch.swift:273
                items = items + s   // ios: Data/StockSearch.swift:274
                applyKr(k, only = setOf(h.id))   // ios: Data/StockSearch.swift:275
                save()   // ios: Data/StockSearch.swift:276
                return symbol(items.firstOrNull { it.id == h.id } ?: s)   // ios: Data/StockSearch.swift:277
            }
        }
        if (h.currency == Currency.krw) {   // ios: Data/StockSearch.swift:280
            // 한국 종목 (중계 실패 또는 미국 지수 국내 ETF): 받지 않는다. 넣은 가격이 있으면 그 값으로 계산
            synchronized(this) {   // ios: Data/StockSearch.swift:282
                val old = items.firstOrNull { it.id == h.id }   // ios: Data/StockSearch.swift:283
                val s = Saved(id = h.id, yahoo = h.yahoo, name = h.name, market = h.market, usd = false, prev = 0.0, last = 0.0, at = Instant.now())   // ios: Data/StockSearch.swift:284
                s.kp = old?.kp; s.kd = old?.kd   // ios: Data/StockSearch.swift:285
                items = items.filter { it.id != h.id }   // ios: Data/StockSearch.swift:286
                items = items + s   // ios: Data/StockSearch.swift:287
                rebuildKR(); save()   // ios: Data/StockSearch.swift:288
                return symbol(items.firstOrNull { it.id == h.id } ?: s)   // ios: Data/StockSearch.swift:289
            }
        }
        val (q, hs) = chart(h.yahoo, range = "3y")   // ios: Data/StockSearch.swift:292
        val s = Saved(id = h.id, yahoo = h.yahoo, name = h.name, market = h.market, usd = h.currency == Currency.usd, prev = q.prev, last = q.last, at = Instant.now())   // ios: Data/StockSearch.swift:293
        synchronized(this) {   // ios: Data/StockSearch.swift:294
            items = items.filter { it.id != h.id }   // ios: Data/StockSearch.swift:295
            items = items + s   // ios: Data/StockSearch.swift:296
            hist = hist + (h.id to hs)   // ios: Data/StockSearch.swift:297
            save()   // ios: Data/StockSearch.swift:298
        }
        return symbol(s)   // ios: Data/StockSearch.swift:300
    }

    /** 앱이 시세를 갱신할 때 (5분에 한 번까지): 추가한 종목의 지금 가격·전일 종가 */
    suspend fun refresh(held: Set<String>, force: Boolean = false) {   // ios: Data/StockSearch.swift:304
        if (!(force || Instant.now().epochSecond - refreshedAt.epochSecond > 300)) return   // ios: Data/StockSearch.swift:305
        refreshedAt = Instant.now()   // ios: Data/StockSearch.swift:306
        for (s in items.filter { s -> held.contains(s.id) && !s.usd && KoreaEstimate.liveOK(s.id, name = s.name)   // ios: Data/StockSearch.swift:307
            && (s.lv != true || (KoreaEstimate.popular.contains(s.id) && (hist[s.id]?.dates?.size ?: 0) < 200 && !Market.shared.tossCovers(s.id))) }) {   // ios: Data/StockSearch.swift:308
            // 스위치를 켜기 전에 넣은 국내 종목, 사이트 기록(kr_daily)만 있던 국내 개별 종목: 1년 종가를 Yahoo 에서 처음 받는다
            runCatching { fetch(StockHit(id = s.id, yahoo = s.yahoo, name = s.name, market = s.market, currency = Currency.krw)) }   // ios: Data/StockSearch.swift:310
        }
        val missKr = HashSet<String>()   // ios: Data/StockSearch.swift:312
        // 토스가 지금 가격·일봉을 다 주는 종목은 Yahoo 를 부르지 않는다 (토스가 안 될 때만 Yahoo)
        for (s in items.filter { s -> held.contains(s.id) && (s.usd || isLive(s.id)) && !Market.shared.tossCovers(s.id) }) {   // ios: Data/StockSearch.swift:314
            val got = runCatching { chart(s.yahoo, range = "5d", today = !s.usd) }.getOrNull()   // ios: Data/StockSearch.swift:315
            if (got == null) {
                if (!s.usd && KoreaEstimate.popular.contains(s.id)) missKr.add(s.id)   // ios: Data/StockSearch.swift:316
                continue   // ios: Data/StockSearch.swift:317
            }
            val (q, recent) = got
            synchronized(this) {   // ios: Data/StockSearch.swift:319
                if (items.none { it.id == s.id }) return@synchronized   // ios: Data/StockSearch.swift:320
                update(s.id) { it.prev = q.prev; it.last = q.last; it.at = Instant.now(); it }   // ios: Data/StockSearch.swift:321
                if (!s.usd) update(s.id) { it.kp = q.last; it.kd = recent.dates.lastOrNull() ?: it.kd; it }   // ios: Data/StockSearch.swift:322
                // 3년 종가 끝에 새 날을 이어 붙인다
                val h = hist[s.id]; val lastDay = h?.dates?.lastOrNull()   // ios: Data/StockSearch.swift:324
                if (h != null && lastDay != null) {
                    val d = h.dates.toMutableList(); val c = h.closes.toMutableList()   // ios: Data/StockSearch.swift:325
                    for ((day, v) in recent.dates.zip(recent.closes)) if (day > lastDay) { d.add(day); c.add(v) }   // ios: Data/StockSearch.swift:326
                    val ld = recent.dates.lastOrNull(); val lv = recent.closes.lastOrNull(); if (ld != null && ld == lastDay && lv != null) c[c.size - 1] = lv   // ios: Data/StockSearch.swift:327
                    hist = hist + (s.id to Hist(dates = d, closes = c))   // ios: Data/StockSearch.swift:328
                }
                val c = hist[s.id]?.closes; if (!s.usd && c != null && c.size > 1) update(s.id) { it.prev = c[c.size - 2]; it }   // ios: Data/StockSearch.swift:330
            }
        }
        // Yahoo 를 못 받은 국내 개별 종목만 사이트 기록(kr_daily.json)으로
        if (missKr.isNotEmpty()) {   // ios: Data/StockSearch.swift:334
            val k = KrDaily.fetch(minAge = if (force) 0.0 else 1800.0); val miss = missKr   // ios: Data/StockSearch.swift:335
            synchronized(this) { applyKr(k, only = miss) }   // ios: Data/StockSearch.swift:336
        }
        synchronized(this) { rebuildKR(); save() }   // 종목마다 전체를 다시 쓰지 않고 끝에 한 번만 저장   // ios: Data/StockSearch.swift:338
    }

    /** 한국 종목 가격 넣기 (date 없으면 오늘) */
    fun setPrice(id: String, price: Double, date: String? = null) {   // ios: Data/StockSearch.swift:342,343
        if (!(price > 0) || items.none { it.id == id } || isLive(id)) return   // ios: Data/StockSearch.swift:344
        if (id == KoreaEstimate.sumId) { setKoreaSum(price, date = date ?: Day.today); return }   // ios: Data/StockSearch.swift:345
        update(id) { it.kp = price; it.kd = date ?: Day.today; it }   // ios: Data/StockSearch.swift:346
        rebuildKR(); save()   // ios: Data/StockSearch.swift:347
    }
    fun saved(id: String): Saved? = items.firstOrNull { it.id == id }   // ios: Data/StockSearch.swift:349
    /** 국내 개별 종목에 사이트 값을 넣는다: 지금 가격·전일 종가는 quotes.json, 날짜별 종가는 kr_daily.json
     *  (at 은 그대로 두어 Market 이 늘 사이트 시세를 먼저 쓴다) */
    private fun applyKr(k: KrDaily.File?, only: Set<String>? = null) {   // ios: Data/StockSearch.swift:352,353
        for (s in items.filter { KoreaEstimate.popular.contains(it.id) && isLive(it.id) && only?.contains(it.id) != false }) {   // ios: Data/StockSearch.swift:354,355
            val r = k?.s?.get(s.id); if (r != null && r.d.isNotEmpty()) { hist = hist + (s.id to Hist(dates = r.d, closes = r.c)); update(s.id) { it.kd = r.d.lastOrNull(); it } }   // ios: Data/StockSearch.swift:356
            val q = Market.shared.site?.q?.get(s.yahoo.uppercase())?.takeIf { it.last > 0 }   // ios: Data/StockSearch.swift:357
            val c = hist[s.id]?.closes; val l = c?.lastOrNull()
            if (q != null) {
                update(s.id) { it.last = q.last; it.prev = if (q.prev > 0) q.prev else q.last; it.kp = q.last; it }   // ios: Data/StockSearch.swift:358
            } else if (c != null && l != null) {   // ios: Data/StockSearch.swift:359
                update(s.id) { it.last = l; it.prev = if (c.size > 1) c[c.size - 2] else l; it.kp = l; it }   // ios: Data/StockSearch.swift:360
            }
        }
    }
    /** 1년 기록을 모으는 중인 국내 종목이면 쌓인 날 수 (차트·1년 범위·출렁임 대신 "기록 모으는 중"). 충분하면 null */
    fun collectingDays(id: String): Int? {   // ios: Data/StockSearch.swift:365
        if (!KoreaEstimate.popular.contains(id) || !isLive(id)) return null   // ios: Data/StockSearch.swift:366
        val n = hist[id]?.dates?.size ?: 0   // ios: Data/StockSearch.swift:367
        return if (n < KrDaily.enough) n else null   // ios: Data/StockSearch.swift:368
    }
    /** 테스트 시세를 받는 국내 종목인가 (스위치를 끄면 다시 넣은 가격 방식) */
    fun isLive(id: String): Boolean { val s = saved(id) ?: return false; return s.lv == true && KoreaEstimate.liveOK(id, name = s.name) }   // ios: Data/StockSearch.swift:371
    /** 한국 종목에 넣은 가격이 바뀌면 바뀌는 값 (기기 동기화가 이것도 보낸다. 이 기기 안에서만 비교한다) */
    val krStamp: String get() = items.filter { !it.usd }.joinToString(",") { "${it.id}=${it.kp ?: 0.0}@${it.kd ?: ""}" } + "|kr${krLog.size}=${krLog.lastOrNull()?.v ?: 0.0}@${krLog.lastOrNull()?.d ?: ""}"   // Data/StockSearch.swift:372 (검사 예외: 옮김)
    /** 국내 주식 합계 금액 넣기 (날짜별 기록에 남긴다. 0 = 다 팔았어요, 지난 기록은 그대로) */
    fun setKoreaSum(won: Double, date: String = Day.today) {   // ios: Data/StockSearch.swift:375,376
        val id = KoreaEstimate.sumId   // ios: Data/StockSearch.swift:377
        krLog = MC.krNorm(krLog + MC.KrDay(d = date, v = maxOf(0.0, won)))   // ios: Data/StockSearch.swift:378
        if (won > 0 && items.none { it.id == id }) {   // ios: Data/StockSearch.swift:379
            items = items + Saved(id = id, yahoo = id + ".KS", name = "국내 주식 합계", market = "국내 주식", usd = false, prev = 0.0, last = 0.0, at = Instant.now())   // ios: Data/StockSearch.swift:380
        }
        val l = krLog.lastOrNull(); if (items.any { it.id == id } && l != null) update(id) { it.kp = if (l.v > 0) l.v else it.kp; it.kd = l.d; it }   // ios: Data/StockSearch.swift:382
        rebuildKR(); save()   // ios: Data/StockSearch.swift:383
    }
    /** 동기화: 날짜별로 합친다 (같은 날은 받은 쪽) */
    fun mergeKr(remote: List<MC.KrDay>) {   // ios: Data/StockSearch.swift:386,387
        if (remote.isEmpty()) return   // ios: Data/StockSearch.swift:388
        val L = MC.krNorm(krLog + remote)   // ios: Data/StockSearch.swift:389
        if (L == krLog) return   // ios: Data/StockSearch.swift:390
        krLog = L   // ios: Data/StockSearch.swift:391
        val l = L.lastOrNull(); if (items.any { it.id == KoreaEstimate.sumId } && l != null && l.v > 0) update(KoreaEstimate.sumId) { it.kp = l.v; it.kd = l.d; it }   // ios: Data/StockSearch.swift:392
        rebuildKR(); save()   // ios: Data/StockSearch.swift:393
    }
    /** 지금 국내 주식 합계 (기록의 오늘 값) */
    val krNow: Double get() = MC.krAt(krLog, Day.today)   // ios: Data/StockSearch.swift:396
    /** 한국 종목의 날짜별 가격·지금 값을 넣은 가격과 사이트 미국 종가로 다시 만든다 */
    fun rebuildKR() {   // ios: Data/StockSearch.swift:398
        val h = hist.toMutableMap()
        items = items.map { s0 ->   // ios: Data/StockSearch.swift:399
            if (s0.usd) return@map s0; val s = s0.copy()   // ios: Data/StockSearch.swift:400
            if (s.lv == true && KoreaEstimate.liveOK(s.id, name = s.name)) return@map s   // 테스트 시세: 받은 종가 그대로   // ios: Data/StockSearch.swift:401
            if (s.id == KoreaEstimate.sumId) {   // ios: Data/StockSearch.swift:402
                // 국내 주식 합계: 첫 기록 날부터 원/달러 거래일마다 그날까지 마지막 금액 (웹 krSynth 와 같다). 지금 값은 기록의 오늘 값
                val L = krLog; val fx = Market.shared.prices?.h?.get("KRW=X")?.dates ?: emptyList()   // ios: Data/StockSearch.swift:404
                val d0 = L.firstOrNull()?.d   // ios: Data/StockSearch.swift:405
                if (d0 != null) {
                    val ds = if (fx.isEmpty()) L.map { it.d } else fx.filter { it >= d0 }   // ios: Data/StockSearch.swift:406
                    if (ds.isEmpty()) h.remove(s.id) else h[s.id] = Hist(dates = ds, closes = ds.map { MC.krAt(L, it) })   // ios: Data/StockSearch.swift:407
                } else { h.remove(s.id) }   // ios: Data/StockSearch.swift:408
                s.last = if (L.isEmpty()) (s.kp ?: 0.0) else MC.krAt(L, Day.today); s.prev = s.last   // ios: Data/StockSearch.swift:409
                return@map s   // ios: Data/StockSearch.swift:410
            }
            val p = s.kp; val sh = p?.let { KoreaEstimate.synth(price = it, date = s.kd ?: Day.today, info = KoreaEstimate.info(id = s.id, name = s.name)) }   // ios: Data/StockSearch.swift:412
            if (p != null && sh != null) {
                h[s.id] = sh; s.last = sh.closes.lastOrNull() ?: p; s.prev = s.last   // 전일 대비는 없음 (웹과 같다)   // ios: Data/StockSearch.swift:413
            } else {   // ios: Data/StockSearch.swift:414
                h.remove(s.id); s.last = s.kp ?: 0.0; s.prev = s.last   // ios: Data/StockSearch.swift:415
            }
            s
        }
        hist = h
    }
}

/** 가격 흐름: 앱에 넣은 테스트 자료가 먼저, 없으면 추가한 종목의 저장값 */
object PriceHistory {   // ios: Data/StockSearch.swift:456
    /** 지난 종가: 사이트 파일(웹과 같은 자료). 이 기기가 직접 받은 값은 사이트보다 늦은 날만 덧붙인다.
     *  둘 다 없으면 빌드 때 넣은 파일. 가상 종목 DRNK 는 샘플 흐름을 따로 쓴다 */
    fun of(id: String): YahooSample.Hist? {   // ios: Data/StockSearch.swift:459
        val key = SiteStats.key(id)   // ios: Data/StockSearch.swift:460
        val site = if (Sample.isVirtual(id)) null else Market.shared.prices?.h?.get(key)   // ios: Data/StockSearch.swift:461
        // 국내 주식 합계는 넣은 기록만 (하루치여도. 첫 기록 전은 없음 = 0)
        if (id == KoreaEstimate.sumId) return CustomSymbols.shared.hist[id]?.let { YahooSample.Hist(dates = it.dates, closes = it.closes) }   // ios: Data/StockSearch.swift:463
        // 시험용 Yahoo 끄기: 내 키로 받은 지난 종가만 (Market.prices 에 토스·Alpaca 일봉이 섞여 있다)
        if (Config.yahooOff && !Sample.isVirtual(id)) return site   // ios: Data/StockSearch.swift:467
        val mine = CustomSymbols.shared.hist[id]?.let { if (it.closes.size > 1) YahooSample.Hist(dates = it.dates, closes = it.closes) else null }   // ios: Data/StockSearch.swift:464
        // 둘 다 있으면 사이트 값을 쓰고, 이 기기가 받은 값은 사이트 마지막 날 뒤의 날만 잇는다
        // (겹치는 날을 기기 값으로 쓰면 수정주가·출처 차이로 웹과 지난 종가가 갈린다: 내 길 시작값 차이)
        if (site != null && mine != null) {   // ios: Data/StockSearch.swift:469
            val sl = site.dates.lastOrNull() ?: ""   // ios: Data/StockSearch.swift:470
            val i = mine.dates.indexOfFirst { it > sl }.takeIf { it >= 0 } ?: return site   // ios: Data/StockSearch.swift:471
            return YahooSample.Hist(dates = site.dates + mine.dates.drop(i), closes = site.closes + mine.closes.drop(i))   // ios: Data/StockSearch.swift:472
        }
        (site ?: mine)?.let { return it }   // ios: Data/StockSearch.swift:474
        // 비중 바꿔 비교에서 담은 종목 (모의 바구니)
        ExtraStats.shared.items[id]?.hist?.let { return it }   // ios: Data/StockSearch.swift:476
        return YahooSample.history[id] ?: (if (Sample.isVirtual(id)) null else YahooSample.bundled[key])   // ios: Data/StockSearch.swift:477
    }
}
