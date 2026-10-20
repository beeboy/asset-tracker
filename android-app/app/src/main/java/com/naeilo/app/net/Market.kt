package com.naeilo.app.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.AppModel
import com.naeilo.app.core.AlpacaFeed
import com.naeilo.app.core.AlpacaKeys
import com.naeilo.app.core.Config
import com.naeilo.app.core.Day
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.LongbridgeFeed
import com.naeilo.app.core.LongbridgeKeys
import com.naeilo.app.core.QuoteFeed
import com.naeilo.app.core.Relay
import com.naeilo.app.core.Store
import com.naeilo.app.core.TossDaily
import com.naeilo.app.core.TossFeed
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.USSession
import com.naeilo.app.core.calc.Parallel
import com.naeilo.app.core.feedJson
import com.naeilo.app.core.jArr
import com.naeilo.app.core.jDbls
import com.naeilo.app.core.jObj
import com.naeilo.app.core.jObjs
import com.naeilo.app.core.num
import com.naeilo.app.core.pctEncode
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.persists
import com.naeilo.app.data.recordDay
import com.naeilo.app.pending.LineClock
import com.naeilo.app.pending.WidgetBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 지금 시세. 사용자가 넣은 내 오픈 API 키(토스·Alpaca·Longbridge)로 받고, 없으면 naeilo 사이트 값.
// 받기는 모두 Dispatchers.IO (FeedReq·feedJson). 화면은 받아 둔 값(Compose 상태)만 읽는다.

private fun nowSec(): Double = System.currentTimeMillis() / 1000.0
/** Date.distantPast 자리 (0001-01-01) */
internal val distantPast: Instant = Instant.ofEpochSecond(-62_135_769_600L)
/** String(format:) 은 소수점이 늘 . 이다 */
private fun fmt(f: String, vararg a: Any): String = String.format(Locale.US, f, *a)
/** Locale(identifier:) 자리 ("en_US_POSIX" → en-US-POSIX) */
internal fun posix(id: String): Locale = Locale.forLanguageTag(id.replace('_', '-'))
/** TimeZone(identifier:) ?? .current */
internal fun zoneOr(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())

data class Quote(   // ios: Data/Market.swift:6
    val last: Double,        // 지금 가격 (실시간이 아니면 전일 종가)   // ios: Data/Market.swift:7
    val prevClose: Double,   // 전일 종가   // ios: Data/Market.swift:8
    val live: Boolean,   // ios: Data/Market.swift:9
) {
    val change: Double get() = if (prevClose > 0) last / prevClose - 1 else 0.0   // ios: Data/Market.swift:10
}

/** 같은 값 묶음 (Swift 튜플 (rate, date)) */
data class MarUse(val rate: Double, val date: String)

class Market {   // ios: Data/Market.swift:13,14
    companion object {
        val shared: Market by lazy { Market() }   // ios: Data/Market.swift:15
        // 샘플 종목 DRNK 의 하루 움직임 (샘플 기록의 마지막 날). 다른 종목은 받은 시세가 없으면 움직임을 만들지 않는다
        val todayMove: Map<String, Double> = mapOf("DRNK" to -0.0221)   // ios: Data/Market.swift:58
    }
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var updatedAt by mutableStateOf(Instant.now())   // ios: Data/Market.swift:17
    /** 사이트가 30분마다 모으는 시세 (앱·위젯이 같이 받는다). 없으면 빌드 때 넣은 테스트 자료 */
    var site by mutableStateOf(QuoteFeed.cached())   // ios: Data/Market.swift:19
    /** 토스증권 지금 가격 (개발자 전용, 종목 id → 값). 있으면 Yahoo 값보다 먼저 */
    var toss by mutableStateOf(TossFeed.cached())   // ios: Data/Market.swift:21
    /** 내 Alpaca 키로 받은 미국 종목 지금 가격 (종목 id → 값). 토스 값이 없을 때 사이트 값보다 먼저 */
    var alpaca by mutableStateOf(AlpacaFeed.cached())   // ios: Data/Market.swift:23
    /** Alpaca 받기를 마친 때. 설정 > 내 Alpaca 키 화면이 이걸 보고 다시 그린다 */
    var alpacaDone by mutableStateOf(distantPast)   // ios: Data/Market.swift:25
    /** 내 Longbridge 키로 받은 지금 가격 (종목 id → 값). 토스·Alpaca 값이 없을 때 사이트 값보다 먼저 */
    var longbridge by mutableStateOf(LongbridgeFeed.cached())   // ios: Data/Market.swift:27
    /** Longbridge 받기를 마친 때. 설정 > 내 Longbridge 키 화면이 이걸 보고 다시 그린다 */
    var longbridgeDone by mutableStateOf(distantPast)   // ios: Data/Market.swift:29
    /** 토스 시세를 받는 중인 일 (겹쳐 묻지 않게). 다시 받기는 이 일이 끝나길 기다렸다가 한 번 더 받는다 */
    private var tossTask: Deferred<Unit>? = null   // ios: Data/Market.swift:31
    /** 토스 받기를 마친 때 (성공·실패 모두). 설정 > 내 토스 키 화면이 이걸 보고 다시 그린다 */
    var tossDone by mutableStateOf(distantPast)   // ios: Data/Market.swift:33
    /** 매매기준율 (사이트 data/mar.json, 서울외국환중개). 웹 평가액과 같은 환율 */
    var mar by mutableStateOf(MarFeed.cached())   // ios: Data/Market.swift:35
    /** 원화 평가에 쓰는 원/달러: 웹 fxBase 와 같게 매매기준율, 없거나 5일 넘게 묵었으면 지금 환율
     *  평가 환율: 매매기준율 먼저 (토스 총자산이 이걸로 센다). 없으면 토스 원/달러, 그다음 지금 환율 */
    val valFx: Double get() = marRate ?: tossFx?.rate ?: fx.last   // ios: Data/Market.swift:38
    val marRate: Double? get() = marUse?.rate   // ios: Data/Market.swift:39
    /** 지금 쓰는 매매기준율 (값, 고시 날짜). 토스처럼 새 날짜 고시는 그날 오전 8시 59분(한국)부터, 그 전엔 앞 고시. 5일 넘게 묵었으면 null */
    val marUse: MarUse? get() {   // ios: Data/Market.swift:41
        val m = mar?.takeIf { it.rate > 0 } ?: return null   // ios: Data/Market.swift:42
        val u = if (MarFeed.started(m.date)) MarUse(m.rate, m.date) else (m.prev?.let { MarUse(it.rate, it.date) } ?: MarUse(m.rate, m.date))   // ios: Data/Market.swift:43
        return if (u.rate > 0 && Day.dday(u.date) >= -5) MarUse(u.rate, u.date) else null   // ios: Data/Market.swift:44
    }
    /** 종목별 전망 입력값 (사이트가 매일 만든다). 없으면 빌드 때 넣은 파일 */
    var stats by mutableStateOf(SiteStats.load())   // ios: Data/Market.swift:47
    /** 내 토스 키로 받은 지난 종가(일봉)·원/달러. 키를 넣은 기기만 */
    var tossHist by mutableStateOf(TossHistory.cached())   // ios: Data/Market.swift:49
    /** 토스 원/달러 (하루 안에 받은 값만) */
    val tossFx: TossHistory.FX? get() = tossHist?.fx?.let { if (nowSec() - it.got < 86400) it else null }   // ios: Data/Market.swift:51
    /** 사이트 지난 종가 (웹 자산 추이·목표 진행과 같은 data/prices 파일). 내 종목과 원/달러만.
     *  내 토스 키로 받은 종목은 토스 일봉으로 바꿔 둔다 (TossHistory.merged) */
    var prices by mutableStateOf(TossHistory.merged(SitePrices.cached(), TossHistory.cached()))   // ios: Data/Market.swift:54
    val source: String get() = if (site != null) "naeilo 사이트" else if (YahooSample.quotes.isEmpty()) "예시 값" else "Yahoo 중계 (테스트)"   // ios: Data/Market.swift:55

    fun quote(s: Symbol): Quote {   // ios: Data/Market.swift:60
        val b = yahooQuote(s)   // ios: Data/Market.swift:61
        tossLast(s)?.let { t -> return Quote(last = t, prevClose = tossPrev(s, b) ?: t, live = true) }   // ios: Data/Market.swift:62
        // Alpaca·Longbridge: 전일 종가는 사이트(공식 종가)가 먼저, 없으면 받은 전일 종가
        val a = alpacaQ(s) ?: lbQ(s) ?: return b   // ios: Data/Market.swift:64
        return Quote(last = a.last, prevClose = if (b.prevClose > 0) b.prevClose else if (a.prev > 0) a.prev else a.last, live = true)   // ios: Data/Market.swift:65
    }
    /** Alpaca 값을 쓰는 종목 (종목 화면에 "Alpaca(IEX) 시세" 표시) */
    fun isAlpaca(s: Symbol): Boolean = tossLast(s) == null && alpacaQ(s) != null   // ios: Data/Market.swift:68
    /** Longbridge 값을 쓰는 종목 (종목 화면에 "Longbridge 시세" 표시) */
    fun isLongbridge(s: Symbol): Boolean = tossLast(s) == null && alpacaQ(s) == null && lbQ(s) != null   // ios: Data/Market.swift:70
    private fun lbQ(s: Symbol): QuoteFeed.Q? {   // ios: Data/Market.swift:71
        if (s.id == "DRNK" || s.currency != Currency.usd || regClose(s) != null) return null   // ios: Data/Market.swift:72
        val q = longbridge?.q?.get(s.id)?.takeIf { it.last > 0 } ?: return null
        return q   // ios: Data/Market.swift:73
    }
    private fun alpacaQ(s: Symbol): QuoteFeed.Q? {   // ios: Data/Market.swift:75
        if (s.id == "DRNK" || s.currency != Currency.usd || regClose(s) != null) return null   // ios: Data/Market.swift:76
        val q = alpaca?.q?.get(s.id)?.takeIf { it.last > 0 } ?: return null
        return q   // ios: Data/Market.swift:77
    }
    /** 토스 값의 전일 종가. 사이트 시세가 토스 값보다 앞선 날 것이면 (주말·장중에 사이트가 아직 안 바뀜) 사이트 마지막 값이 전일 종가 */
    private fun tossPrev(s: Symbol, b: Quote): Double? {   // ios: Data/Market.swift:80
        // 토스 일봉이 있으면 그 값 (토스 값 거래일 앞 마지막 종가)
        toss?.q?.get(s.id)?.let { t -> TossDaily.prev(tossDaily(s), at = t.at, kr = s.currency == Currency.krw)?.let { p -> return p } }   // ios: Data/Market.swift:82
        val t = toss?.q?.get(s.id); val q = site?.q?.get(YahooSample.keyFor[s.id] ?: CustomSymbols.yahoo(s))   // ios: Data/Market.swift:83
        if (t != null && q != null && q.at > 0 && t.at > q.at) {
            val zone = ZoneId.of(if (s.currency == Currency.krw) "Asia/Seoul" else "America/New_York")   // ios: Data/Market.swift:84,85
            val day = { x: Double -> Instant.ofEpochMilli((x * 1000).toLong()).atZone(zone).toLocalDate() }
            if (day(q.at) != day(t.at)) return q.reg ?: q.last   // ios: Data/Market.swift:86
        }
        return if (b.prevClose > 0) b.prevClose else null   // ios: Data/Market.swift:88
    }
    /** 토스 값을 쓰는 종목 (종목 화면에 "토스 시세" 표시) */
    fun isToss(s: Symbol): Boolean = tossLast(s) != null   // ios: Data/Market.swift:91
    private fun tossLast(s: Symbol): Double? {   // ios: Data/Market.swift:92
        if (s.id == "DRNK") return null; val t = toss?.q?.get(s.id)?.takeIf { it.last > 0 } ?: return null   // ios: Data/Market.swift:93
        // 장이 끝난 때도 애프터마켓 뒤에 체결된 값(토스 주간거래)이면 그 값. 토스 총자산이 주간거래 가격으로 센다
        if (regClose(s) != null && !TossDaily.daytime(t.at)) return null   // ios: Data/Market.swift:95
        return t.last   // ios: Data/Market.swift:96
    }
    /** 내 토스 키로 받은 이 종목 일봉 */
    private fun tossDaily(s: Symbol): TossDaily.Hist? =   // ios: Data/Market.swift:99
        tossHist?.h?.get(SiteStats.key(s.id))?.let { TossDaily.Hist(dates = it.dates, closes = it.closes) }   // ios: Data/Market.swift:100
    /** 토스가 지금 가격과 일봉을 다 주는 종목 (Yahoo 를 부르지 않는다) */
    fun tossCovers(id: String): Boolean = toss?.q?.get(id) != null && tossHist?.h?.get(SiteStats.key(id)) != null   // ios: Data/Market.swift:103
    /** 미국 장이 다 끝났으면 사이트 정규장 종가. 토스·Alpaca 의 애프터마켓 가격 대신 이걸로 센다 (토스 총자산과 같은 기준) */
    private fun regClose(s: Symbol): Double? {   // ios: Data/Market.swift:105
        if (s.currency != Currency.usd || !USSession.closed()) return null   // ios: Data/Market.swift:106
        toss?.q?.get(s.id)?.let { t -> TossDaily.reg(tossDaily(s), at = t.at)?.let { r -> return r } }   // ios: Data/Market.swift:107
        if (tossCovers(s.id)) return null   // 이 종목은 Yahoo 를 안 불러서 사이트 값이 묵었다   // ios: Data/Market.swift:108
        val r = site?.q?.get(YahooSample.keyFor[s.id] ?: CustomSymbols.yahoo(s))?.reg?.takeIf { it > 0 } ?: return null   // ios: Data/Market.swift:109
        return r   // ios: Data/Market.swift:110
    }

    private fun yahooQuote(s: Symbol): Quote {   // ios: Data/Market.swift:113
        if (s.id != "DRNK") {   // ios: Data/Market.swift:114
            // 사이트 시세와 추가한 종목(5분마다 Yahoo 중계) 중 나중에 받은 값
            val c = CustomSymbols.shared.items.firstOrNull { it.id == s.id }   // ios: Data/Market.swift:116
            val f = site; val q = f?.q?.get(YahooSample.keyFor[s.id] ?: CustomSymbols.yahoo(s))   // ios: Data/Market.swift:117
            if (f != null && q != null && (c == null || c.at.toEpochMilli() / 1000.0 < f.got)) {   // ios: Data/Market.swift:118
                return Quote(last = regClose(s) ?: q.last, prevClose = q.prev, live = s.currency == Currency.usd)   // ios: Data/Market.swift:119
            }
            if (c != null) return Quote(last = c.last, prevClose = c.prev, live = c.usd)   // ios: Data/Market.swift:121
            // 한국 종목은 넣은 가격이 없으면 값이 없다 (시안 값을 쓰지 않는다)
            if (s.currency == Currency.krw) return Quote(last = 0.0, prevClose = 0.0, live = false)   // ios: Data/Market.swift:123
        }
        YahooSample.quotes[s.id]?.let { q -> return Quote(last = q.last, prevClose = q.prevClose, live = true) }   // ios: Data/Market.swift:125
        todayMove[s.id]?.let { m -> return Quote(last = s.close * (1 + m), prevClose = s.close, live = true) }   // ios: Data/Market.swift:126
        return Quote(last = s.close, prevClose = s.close, live = false)   // ios: Data/Market.swift:127
    }

    /** 원/달러 (지금, 전일) */
    val fx: Quote get() {   // ios: Data/Market.swift:131
        tossFx?.let { t -> return Quote(last = t.rate, prevClose = t.prev, live = true) }   // ios: Data/Market.swift:132
        site?.q?.get("KRW=X")?.let { q -> return Quote(last = q.last, prevClose = q.prev, live = true) }   // ios: Data/Market.swift:133
        YahooSample.quotes["FX"]?.let { q -> return Quote(last = q.last, prevClose = q.prevClose, live = true) }   // ios: Data/Market.swift:134
        return Quote(last = Sample.fx, prevClose = Sample.fx, live = false)   // ios: Data/Market.swift:135
    }

    val asOfText: String get() {   // ios: Data/Market.swift:138
        val z = ZoneId.systemDefault(); val f = { p: String -> DateTimeFormatter.ofPattern(p, AppModel.dateLocale).withZone(z) }   // ios: Data/Market.swift:139
        val s = site; val t = s?.q?.values?.maxOfOrNull { it.at }   // ios: Data/Market.swift:140
        if (s != null && t != null && t > 0) {
            val d = Instant.ofEpochMilli((t * 1000).toLong())   // ios: Data/Market.swift:141
            val p = if (d.atZone(z).toLocalDate() == LocalDate.now(z)) "a h:mm" else "M월 d일 a h:mm"   // ios: Data/Market.swift:142
            val ago = ((nowSec() - s.got).toInt()) / 60   // ios: Data/Market.swift:143
            return "${f(p).format(d)} 시세 · ${if (ago < 1) "방금" else "${ago}분 전"} 받음"   // Data/Market.swift:144 (검사 예외: 옮김)
        }
        YahooSample.asOf?.let { t2 ->   // ios: Data/Market.swift:146
            val p = "M월 d일 a h:mm"   // ios: Data/Market.swift:147
            return "${f(p).format(t2)} 시세 (테스트 자료)"   // ios: Data/Market.swift:148
        }
        val p = "a h:mm"   // ios: Data/Market.swift:150
        return "지금 시세 · ${f(p).format(updatedAt)} 갱신"   // ios: Data/Market.swift:151
    }

    /** force: 아래로 당겨 갱신할 때. 받은 지 얼마 안 됐어도 다시 받는다 */
    suspend fun refresh(held: Set<String> = emptySet(), force: Boolean = false) = coroutineScope {   // ios: Data/Market.swift:155
        // 토스 시세 (개발자 주소를 넣었거나, 사용자가 설정 > 토스증권 시세에 내 키를 넣은 기기만): 국내·미국 종목.
        // 평소에는 기다리지 않고 따로 받아 도착하면 반영한다 (앱 시작을 늦추지 않게). 당겨 갱신·다시 받기(force)는 토스까지 기다린다.
        // 1분 안에 받았으면 그대로. 실패하면 15분 안에 받은 값, 그것도 없으면 null → Yahoo 값
        val tossWait = async { if (force) refreshToss(held = held, force = true) else Unit }   // ios: Data/Market.swift:159
        if (!force) bg.launch { refreshToss(held = held, force = false) }   // ios: Data/Market.swift:160
        // Alpaca 시세 (설정 > 내 Alpaca 키를 넣은 기기만): 미국 종목. 토스와 같은 방식
        val alpacaWait = async { if (force) refreshAlpaca(held = held, force = true) else Unit }   // ios: Data/Market.swift:162
        if (!force) bg.launch { refreshAlpaca(held = held, force = false) }   // ios: Data/Market.swift:163
        // Longbridge 시세 (설정 > 내 Longbridge 키를 넣은 기기만): 지금은 미국 종목. 같은 방식
        val lbWait = async { if (force) refreshLongbridge(held = held, force = true) else Unit }   // ios: Data/Market.swift:165
        if (!force) bg.launch { refreshLongbridge(held = held, force = false) }   // ios: Data/Market.swift:166
        // 나머지 시세는 한꺼번에 받는다 (차례로 기다리지 않게)
        // 지난 종가: 30분마다 사이트 index.json 만 보고, 새로 모았을 때만 내 종목 파일을 다시 받는다 (내 길·전망 기준값이 웹과 같은 종가)
        val keys = if (held.isEmpty()) (prices?.h?.keys ?: emptySet()).toSet() else held.filter { it != "DRNK" }.map(SiteStats::key).toSet() + KoreaEstimate.baseKeys(held)   // ios: Data/Market.swift:169
        val oldPrices = withContext(Dispatchers.IO) { SitePrices.cached() }   // 토스 값을 섞지 않은 사이트 값   // ios: Data/Market.swift:170
        // 지금 가격: 미국 종목·국내 개별 종목·원/달러를 Yahoo 에서 직접 (못 받은 것만 사이트). 4분 안에 받았으면 그대로
        if (held.isNotEmpty()) {   // ios: Data/Market.swift:172
            val syms = held.mapNotNull { id ->   // ios: Data/Market.swift:173
                val s = if (id != "DRNK" && id != KoreaEstimate.sumId) Sample.symbol(id) else null; if (s == null) return@mapNotNull null   // ios: Data/Market.swift:174
                if (!((s.currency == Currency.usd || KoreaEstimate.popular.contains(id)) && !tossCovers(id))) return@mapNotNull null   // ios: Data/Market.swift:175
                YahooSample.keyFor[id] ?: CustomSymbols.yahoo(s)   // ios: Data/Market.swift:176
            }
            Store.quoteSyms = (syms.toSet() + (if (tossFx == null) setOf("KRW=X") else emptySet())).sorted()   // ios: Data/Market.swift:178
        }
        val siteNew = async { QuoteFeed.fetch(minAge = if (force) 0.0 else 240.0) }   // ios: Data/Market.swift:180
        val marNew = async { MarFeed.fetch(minAge = if (force) 0.0 else 1800.0) }   // ios: Data/Market.swift:181
        // 토스 일봉이 있는 종목은 빼고 (원/달러·지수는 토스에 일봉이 없어 늘 Yahoo)
        val tossKeys = if (TossKeys.on) tossHist?.h?.keys?.toSet() ?: emptySet() else emptySet()   // ios: Data/Market.swift:183
        val pricesNew = async { SitePrices.fetch(keys = (keys - tossKeys) + setOf("KRW=X") + Parallel.wantKeys, old = oldPrices, minAge = if (force) 0.0 else 1800.0) }   // ios: Data/Market.swift:184
        // 전망 입력값: 하루 한 번 바뀌므로 6시간에 한 번 (당겨 갱신하면 바로)
        val statsNew = async { SiteStats.fetch(minAge = if (force) 0.0 else 6 * 3600.0) }   // ios: Data/Market.swift:186
        // 추가한 종목: 내 종목에 있는 것만 5분에 한 번 (당겨 갱신하면 바로)
        val customDone = async { CustomSymbols.shared.refresh(held = held, force = force) }   // ios: Data/Market.swift:188
        val extraDone = async { ExtraStats.shared.refresh(Parallel.wantKeys) }   // ios: Data/Market.swift:189
        siteNew.await()?.let { site = it }   // ios: Data/Market.swift:190
        marNew.await()?.let { mar = it }   // ios: Data/Market.swift:191
        pricesNew.await()?.let { p -> prices = TossHistory.merged(p, tossHist) }   // ios: Data/Market.swift:192
        statsNew.await()?.let { stats = it }   // ios: Data/Market.swift:193
        customDone.await()   // ios: Data/Market.swift:194
        extraDone.await()   // ios: Data/Market.swift:195
        tossWait.await()   // ios: Data/Market.swift:196
        alpacaWait.await()   // ios: Data/Market.swift:197
        lbWait.await()   // ios: Data/Market.swift:198
        updatedAt = Instant.now()   // ios: Data/Market.swift:199
    }

    /** 토스 지금 가격과 지난 종가·원/달러를 받는다. 받는 중이면 평소에는 건너뛰고, force 면 그 일이 끝난 뒤 새로 한 번 더 받는다 */
    suspend fun refreshToss(held: Set<String>, force: Boolean) {   // ios: Data/Market.swift:203,204
        val ty = mutableMapOf<String, String>()   // ios: Data/Market.swift:205
        for (id in held) { if (id == "DRNK" || id == KoreaEstimate.sumId) continue; Sample.symbol(id)?.let { s -> ty[id] = CustomSymbols.yahoo(s) } }   // ios: Data/Market.swift:206
        if (ty.isEmpty() || !(TossFeed.base != null || TossKeys.on)) return   // ios: Data/Market.swift:207
        synchronized(this) { tossTask }?.let { t ->   // ios: Data/Market.swift:208
            if (!force) return   // ios: Data/Market.swift:209
            t.await()     // 받는 중인 일이 끝나길 기다렸다가 새로 받는다 (끝난 일을 거듭 기다리며 도는 일이 없게 한 번만)   // ios: Data/Market.swift:210
        }
        val t = bg.async {   // ios: Data/Market.swift:212
            val q = TossFeed.fetch(ty, minAge = if (force) 0.0 else 60.0, force = force)   // ios: Data/Market.swift:213
            toss = q   // ios: Data/Market.swift:214
            // 지난 종가·원/달러도 내 키로 (종목마다 하루 한 번, 환율은 10분에 한 번)
            TossHistory.fetch(ty.entries.groupBy({ SiteStats.key(it.key) }, { it.value }).mapValues { it.value.first() }, force = force)?.let { h ->   // ios: Data/Market.swift:216
                tossHist = h   // ios: Data/Market.swift:217
                prices = TossHistory.merged(SitePrices.cached(), h)   // ios: Data/Market.swift:218
            }
            tossDone = Instant.now()   // ios: Data/Market.swift:220
        }
        synchronized(this) { tossTask = t }   // ios: Data/Market.swift:222
        t.await()   // ios: Data/Market.swift:223
        synchronized(this) { if (tossTask === t) tossTask = null }   // ios: Data/Market.swift:224
    }

    /** Alpaca 지금 가격을 받는다 (1분 안에 받았으면 그대로, 당겨 갱신은 바로) */
    suspend fun refreshAlpaca(held: Set<String>, force: Boolean) {   // ios: Data/Market.swift:228,229
        if (!AlpacaKeys.on) { if (alpaca != null) alpaca = null; return }   // ios: Data/Market.swift:230
        val ty = mutableMapOf<String, String>()   // ios: Data/Market.swift:231
        for (id in held) { if (id == "DRNK") continue; Sample.symbol(id)?.takeIf { it.currency == Currency.usd }?.let { s -> ty[id] = CustomSymbols.yahoo(s) } }   // ios: Data/Market.swift:232
        if (ty.isEmpty()) return   // ios: Data/Market.swift:233
        alpaca = AlpacaFeed.fetch(ty, minAge = if (force) 0.0 else 60.0)   // ios: Data/Market.swift:234
        alpacaDone = Instant.now()   // ios: Data/Market.swift:235
    }

    /** Longbridge 지금 가격을 받는다 (1분 안에 받았으면 그대로, 당겨 갱신은 바로) */
    suspend fun refreshLongbridge(held: Set<String>, force: Boolean) {   // ios: Data/Market.swift:239,240
        if (!LongbridgeKeys.on) { if (longbridge != null) longbridge = null; return }   // ios: Data/Market.swift:241
        val ty = mutableMapOf<String, String>()   // ios: Data/Market.swift:242
        for (id in held) { if (id == "DRNK") continue; Sample.symbol(id)?.takeIf { it.currency == Currency.usd }?.let { s -> ty[id] = CustomSymbols.yahoo(s) } }   // ios: Data/Market.swift:243
        if (ty.isEmpty()) return   // ios: Data/Market.swift:244
        longbridge = LongbridgeFeed.fetch(ty, minAge = if (force) 0.0 else 60.0)   // ios: Data/Market.swift:245
        longbridgeDone = Instant.now()   // ios: Data/Market.swift:246
    }

    /** 아래로 당겨 갱신: 내 종목을 바로 받고 위젯에도 넘긴다 */
    suspend fun pullRefresh(m: AppModel) {   // ios: Data/Market.swift:250,251
        refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Data/Market.swift:252
        if (m.persists) m.recordDay()     // 내 기록: 새로 받은 시세로 오늘 줄   // ios: Data/Market.swift:253
        LineClock.shared.next()     // 당기면 인물 대사도 다음 줄로   // ios: Data/Market.swift:254
        WidgetBridge.write(m)   // ios: Data/Market.swift:255
    }
}

val Symbol.quote: Quote get() = Market.shared.quote(this)   // ios: Data/Market.swift:259,260
/** 지금 가격 */
val Symbol.last: Double get() = quote.last   // ios: Data/Market.swift:262

// 테스트 시세: 사이트가 모으는 Yahoo 중계 자료 (data/prices 의 종목별 json 3년 종가, data/quotes.json 전일 종가·현재가).
// DRNK 는 가상 종목이라 시안 값, 한국 종목과 MSFT 는 자료가 없어 시안 값을 쓴다.
// 빌드 때 넣은 파일(assets)은 처음 쓸 때 한 번만 읽는다 (lazy)
object YahooSample {   // ios: Data/Market.swift:267
    @Serializable data class Hist(val dates: List<String>, val closes: List<Double>) {   // ios: Data/Market.swift:268, Data/Lots.swift:54
        /** 날짜 d 의 종가 (그날이 없으면 그 전 마지막 거래일). d 가 첫날보다 앞이면 null */
        fun on(d: String): Double? {   // ios: Data/Lots.swift:56
            var lo = 0; var hi = dates.size - 1; var k = -1   // ios: Data/Lots.swift:57
            while (lo <= hi) { val mid = (lo + hi) / 2; if (dates[mid] <= d) { k = mid; lo = mid + 1 } else { hi = mid - 1 } }   // ios: Data/Lots.swift:58
            return if (k >= 0 && k < closes.size) closes[k] else null   // ios: Data/Lots.swift:59
        }
    }
    data class Q(val prevClose: Double, val last: Double, val time: Instant)   // ios: Data/Market.swift:269

    val keyFor: Map<String, String> = mapOf("QQQ" to "QQQ", "AAPL" to "AAPL", "NVDA" to "NVDA", "SPY" to "SPY", "FX" to "KRW=X")   // ios: Data/Market.swift:271
    private fun file(k: String): String = k.replace("=", "_").replace("^", "_")   // ios: Data/Market.swift:272
    /** Bundle.main.url(forResource:withExtension:subdirectory:) 자리: assets 파일 (없으면 null) */
    private fun asset(name: String, ext: String, dir: String? = null): ByteArray? =
        runCatching { Defaults.ctx.assets.open((dir?.let { "$it/" } ?: "") + "$name.$ext").use { it.readBytes() } }.getOrNull()

    val quotes: Map<String, Q> by lazy {   // ios: Data/Market.swift:274
        val d = asset("quotes", "json"); val o = d?.let { feedJson(it) }?.let { jObj(it) }   // ios: Data/Market.swift:275,276
        if (o == null || o.values.any { jObj(it) == null }) return@lazy emptyMap()   // ios: Data/Market.swift:277
        val out = mutableMapOf<String, Q>()   // ios: Data/Market.swift:278
        for ((id, k) in keyFor) {   // ios: Data/Market.swift:279
            val v = jObj(o[k]) ?: continue; val prev = v["prev_close"] as? Double ?: continue; val last = (v["last"] ?: v["regular"]) as? Double ?: continue   // ios: Data/Market.swift:280
            out[id] = Q(prevClose = prev, last = last, time = Instant.ofEpochMilli((((v["last_time"] as? Double) ?: 0.0) * 1000).toLong()))   // ios: Data/Market.swift:281
        }
        out   // ios: Data/Market.swift:283
    }   // ios: Data/Market.swift:284

    val history: Map<String, Hist> by lazy {   // ios: Data/Market.swift:286
        val out = mutableMapOf<String, Hist>()   // ios: Data/Market.swift:287
        for ((id, k) in keyFor) {   // ios: Data/Market.swift:288
            val d = asset(file(k), "json", "prices") ?: continue   // ios: Data/Market.swift:289,290
            val o = jObj(feedJson(d)) ?: continue   // ios: Data/Market.swift:291
            val dates = jArr(o["dates"])?.let { l -> l.map { it as? String ?: return@let null } } ?: continue; val c = jArr(o["close"]) ?: continue   // ios: Data/Market.swift:292
            val closes = c.map { (it as? Double) ?: Double.NaN }   // ios: Data/Market.swift:293
            // 빈 값은 앞 값으로 메운다
            val filled = ArrayList<Double>(closes.size); var prev = closes.firstOrNull { !it.isNaN() } ?: 1.0   // ios: Data/Market.swift:295
            for (v in closes) { if (!v.isNaN()) prev = v; filled.add(prev) }   // ios: Data/Market.swift:296
            out[id] = Hist(dates = dates, closes = filled)   // ios: Data/Market.swift:297
        }
        out   // ios: Data/Market.swift:299
    }   // ios: Data/Market.swift:300

    /** 자료가 언제 값인지 (현재가 시각) */
    val asOf: Instant? get() = quotes.values.map { it.time }.maxOrNull()   // ios: Data/Market.swift:303

    /** 빌드 때 넣은 지난 종가 전부 (사이트 티커 → 종가). 사이트에서 아직 못 받았을 때만 쓴다 */
    val bundled: Map<String, Hist> by lazy {   // ios: Data/Market.swift:306
        val out = mutableMapOf<String, Hist>()   // ios: Data/Market.swift:307
        for (f in runCatching { Defaults.ctx.assets.list("prices") }.getOrNull()?.filter { it.endsWith("." + "json") } ?: emptyList()) {   // ios: Data/Market.swift:308
            val o = asset(f.removeSuffix(".json"), "json", "prices")?.let { jObj(feedJson(it)) } ?: continue   // ios: Data/Market.swift:309
            val k = o["symbol"] as? String ?: continue; val h = SitePrices.hist(o) ?: continue   // ios: Data/Market.swift:310
            out[k] = h   // ios: Data/Market.swift:311
        }
        out   // ios: Data/Market.swift:313
    }   // ios: Data/Market.swift:314
}

/** 사이트 지난 종가 (data/prices 의 종목별 json): 웹이 자산 추이·목표 진행(내 길)에 쓰는 것과 같은 파일.
 *  빌드 때 넣은 파일은 맥에서 받은 날에 멈춰 있어서, 앱은 내 종목과 원/달러만 사이트에서 다시 받아 둔다 */
object SitePrices {   // ios: Data/Market.swift:319
    /** h 키 = 사이트 티커 (KRW=X 등). asked = 그때 받으려던 티커 (사이트에 없는 종목은 h 에 없다) */
    @Serializable data class File(var got: Double, var updated: String, var asked: List<String>, var h: Map<String, YahooSample.Hist>)   // ios: Data/Market.swift:321
    const val name = "site_prices.json"   // ios: Data/Market.swift:322
    fun cached(): File? = Store.read(File.serializer(), name)   // ios: Data/Market.swift:323

    /** 빈 값은 앞 값으로 메운다 (웹 history 와 같다) */
    fun hist(o: Map<String, Any?>): YahooSample.Hist? {   // ios: Data/Market.swift:326
        val ds = jArr(o["dates"])?.map { it as? String ?: return null } ?: return null; val c = jArr(o["close"]) ?: return null; if (ds.size != c.size || ds.isEmpty()) return null   // ios: Data/Market.swift:327
        val raw: List<Double> = c.map { (it as? Double) ?: Double.NaN }   // ios: Data/Market.swift:328
        var prev: Double = raw.firstOrNull { !it.isNaN() } ?: 1.0   // ios: Data/Market.swift:329
        val out = ArrayList<Double>(raw.size)   // ios: Data/Market.swift:330
        for (v in raw) { if (!v.isNaN()) prev = v; out.add(prev) }   // ios: Data/Market.swift:331
        return YahooSample.Hist(dates = ds, closes = out)   // ios: Data/Market.swift:332
    }

    internal suspend fun get(path: String): Map<String, Any?>? {   // ios: Data/Market.swift:335
        val c = URL(Config.data, path).toString()   // ios: Data/Market.swift:336
        val queryItems = listOf("t" to (nowSec() / 600).toLong().toString()); val url = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // ios: Data/Market.swift:337
        val req = FeedReq(url, timeout = 15.0)   // ios: Data/Market.swift:338
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: Data/Market.swift:339
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null; if (code != 200) return null   // ios: Data/Market.swift:340
        return jObj(feedJson(data))   // ios: Data/Market.swift:341
    }

    /** Yahoo 일봉 종가 (사이트 prices 의 close 와 같은 기준). 날짜는 그 거래소 날짜 */
    suspend fun daily(sym: String, range: String): YahooSample.Hist? {   // ios: Data/Market.swift:345
        val enc = pctEncode(sym)   // ios: Data/Market.swift:346
        val o = runCatching { Relay.yahoo("/v8/finance/chart/$enc?range=$range&interval=1d", timeout = 10.0) }.getOrNull() ?: return null   // ios: Data/Market.swift:347
        val r = jObjs(jObj(o["chart"])?.get("result"))?.firstOrNull() ?: return null   // ios: Data/Market.swift:348
        val m = jObj(r["meta"]) ?: return null; val ts = jDbls(r["timestamp"]) ?: return null   // ios: Data/Market.swift:349
        val cl = jArr(jObjs(jObj(r["indicators"])?.get("quote"))?.firstOrNull()?.get("close")) ?: return null   // ios: Data/Market.swift:350
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", posix("en_US_POSIX"))   // ios: Data/Market.swift:351,352
            .withZone(zoneOr((m["exchangeTimezoneName"] as? String) ?: "America/New_York"))
        val ds = ArrayList<String>(); val cs = ArrayList<Double>()   // ios: Data/Market.swift:353
        for ((t, c) in ts.zip(cl)) {   // ios: Data/Market.swift:354
            val v = num(c)?.takeIf { it > 0 } ?: continue   // ios: Data/Market.swift:355
            val d = f.format(Instant.ofEpochMilli((t * 1000).toLong()))   // ios: Data/Market.swift:356
            if (ds.lastOrNull() == d) cs[cs.size - 1] = v else { ds.add(d); cs.add(v) }   // ios: Data/Market.swift:357
        }
        return if (ds.isEmpty()) null else YahooSample.Hist(dates = ds, closes = cs)   // ios: Data/Market.swift:359
    }

    /** 한 종목: 받아 둔 3년 종가가 있으면 최근 한 달만 받아 끝을 바꾸고, 없으면 3년을 받는다 */
    private suspend fun direct(k: String, had: YahooSample.Hist?): YahooSample.Hist? {   // ios: Data/Market.swift:363
        val last = had?.dates?.lastOrNull()   // ios: Data/Market.swift:364
        if (had != null && last != null && had.dates.size > 200 && Day.dday(last) >= -20) {
            val n = daily(k, range = "1mo"); val first = n?.dates?.firstOrNull()   // ios: Data/Market.swift:365
            if (n != null && first != null) {
                val keep = had.dates.indexOfFirst { it >= first }.takeIf { it >= 0 } ?: had.dates.size   // ios: Data/Market.swift:366
                val ds = had.dates.subList(0, keep) + n.dates; val cs = had.closes.subList(0, keep) + n.closes   // ios: Data/Market.swift:367
                val cut = maxOf(0, ds.size - 800)   // 3년 남짓만 둔다   // ios: Data/Market.swift:368
                return YahooSample.Hist(dates = ds.drop(cut), closes = cs.drop(cut))   // ios: Data/Market.swift:369
            }
        }
        return daily(k, range = "3y")   // ios: Data/Market.swift:371
    }

    /** keys = 사이트 티커(= Yahoo 기호). Yahoo 에서 직접 받고, 못 받은 것만 사이트 파일로 채운다. 받은 지 minAge 안이고 다 있으면 그대로 */
    suspend fun fetch(keys: Set<String>, old: File?, minAge: Double): File? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:375
        val now = nowSec()   // ios: Data/Market.swift:376
        if (old != null && now - old.got < minAge && old.asked.toSet().containsAll(keys)) return@withContext old   // ios: Data/Market.swift:377
        val h = mutableMapOf<String, YahooSample.Hist>()   // ios: Data/Market.swift:378
        coroutineScope {   // ios: Data/Market.swift:379
            val g = keys.map { k -> val had = old?.h?.get(k); async { k to direct(k, had) } }   // ios: Data/Market.swift:380
            for ((k, v) in g.awaitAll()) { if (v != null) h[k] = v }   // ios: Data/Market.swift:381
        }
        val miss = keys.filter { h[it] == null }   // ios: Data/Market.swift:383
        var updated = old?.updated ?: ""   // ios: Data/Market.swift:384
        val idx = if (miss.isNotEmpty()) get("index.json") else null; val files = jObj(idx?.get("prices"))   // ios: Data/Market.swift:385
        if (idx != null && files != null) {
            updated = idx["updated"] as? String ?: ""   // ios: Data/Market.swift:386
            for (k in miss) {   // ios: Data/Market.swift:387
                val f = files[k] as? String ?: continue   // ios: Data/Market.swift:388
                get("prices/" + f)?.let { o -> hist(o)?.let { x -> h[k] = x } }   // ios: Data/Market.swift:389
            }
        }
        for (k in keys) { if (h[k] == null) old?.h?.get(k)?.let { x -> h[k] = x } }   // ios: Data/Market.swift:392
        val out = File(got = now, updated = updated, asked = keys.sorted(), h = h)   // ios: Data/Market.swift:393
        Store.write(File.serializer(), out, name)   // ios: Data/Market.swift:394
        out   // ios: Data/Market.swift:395
    }
}

/** 내 토스 키로 받는 지난 종가(일봉)와 원/달러. 키를 넣은 기기만 (공개 버전은 사용자 키로만 시세를 보여 준다).
 *  일봉은 수정 안 한 종가(adjusted=false): 사이트 data/prices 의 close 와 같은 기준. 받은 종목은 사이트 값 대신 쓰고, 못 받은 종목은 사이트 값 그대로.
 *  원/달러 지난 흐름(KRW=X)은 토스에 일봉이 없어 아직 사이트 값이다. */
object TossHistory {   // ios: Data/Market.swift:402
    @Serializable data class FX(var rate: Double, var prev: Double, var got: Double)   // 지금, 24시간 전, 받은 시각(초)   // ios: Data/Market.swift:403
    /** h 키 = 사이트 티커, day = 그 종목을 마지막으로 받은 날 */
    @Serializable data class File(var h: Map<String, YahooSample.Hist>, var day: Map<String, String>, var fx: FX? = null)   // ios: Data/Market.swift:405
    const val name = "toss_history.json"   // ios: Data/Market.swift:406
    fun cached(): File? = if (TossKeys.on) Store.read(File.serializer(), name) else null   // ios: Data/Market.swift:407

    fun merged(p: SitePrices.File?, t: File?): SitePrices.File? {   // ios: Data/Market.swift:409
        if (t == null || !TossKeys.on || t.h.isEmpty()) return p   // ios: Data/Market.swift:410
        val out = p?.copy() ?: SitePrices.File(got = 0.0, updated = "", asked = emptyList(), h = emptyMap())   // ios: Data/Market.swift:411
        out.h = out.h + t.h   // ios: Data/Market.swift:412
        return out   // ios: Data/Market.swift:413
    }

    private fun enc(v: String): String =   // ios: Data/Market.swift:416
        pctEncode(v, "-._~")   // ios: Data/Market.swift:417
    /** 그 시장의 오늘 날짜와, 오늘 장이 끝났는지 (끝나기 전 오늘 일봉은 종가가 아니라 뺀다) */
    private fun marketDay(kr: Boolean): Pair<String, Boolean> {   // ios: Data/Market.swift:420
        val c = Instant.now().atZone(ZoneId.of(if (kr) "Asia/Seoul" else "America/New_York"))   // ios: Data/Market.swift:421,422,423
        val d = fmt("%04d-%02d-%02d", c.year, c.monthValue, c.dayOfMonth)   // ios: Data/Market.swift:424
        val m = c.hour * 60 + c.minute   // ios: Data/Market.swift:425
        return d to (m >= (if (kr) 15 * 60 + 40 else 16 * 60 + 10))   // ios: Data/Market.swift:426
    }

    /** keys: 사이트 티커 → Yahoo 기호. 종목마다 하루 한 번: 처음엔 3년치(200개씩), 그다음엔 최근 30개만 받아 잇는다.
     *  너무 많이 부르면(429)·중계에 못 닿으면 거기서 멈추고 다음 갱신 때 이어 받는다 */
    suspend fun fetch(keys: Map<String, String>, force: Boolean): File? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:431
        if (!TossKeys.on) return@withContext null   // ios: Data/Market.swift:432
        val f = Store.read(File.serializer(), name) ?: File(h = emptyMap(), day = emptyMap(), fx = null)   // ios: Data/Market.swift:433
        val fh = f.h.toMutableMap(); val fday = f.day.toMutableMap()
        val now = nowSec(); val today = Day.today   // ios: Data/Market.swift:434
        val from = Day.str(Instant.now().minusSeconds((3 * 365 + 15) * 86400L))   // 사이트 파일과 같은 3년 남짓   // ios: Data/Market.swift:435
        val miss = TossFeed.histMiss.toMutableMap()   // ios: Data/Market.swift:436
        var stop = false   // ios: Data/Market.swift:437
        for ((k, y) in keys.entries.sortedBy { it.key }) {   // ios: Data/Market.swift:438
            if (stop) break
            // 하루 한 번, 장이 끝난 뒤 한 번 더 (그날 종가가 정규장 종가·다음 날 전일 종가가 된다)
            val (md, closed) = marketDay(kr = y.endsWith(".KS") || y.endsWith(".KQ"))   // ios: Data/Market.swift:440
            val stamp = if (closed) today + "c" else today   // ios: Data/Market.swift:441
            val c = TossFeed.code(y) ?: continue; if (!((miss[c] ?: 0.0) < now && (fday[k] != stamp || fh[k] == null))) continue   // ios: Data/Market.swift:442
            val have = fh[k]   // ios: Data/Market.swift:443
            val full = (have?.dates?.firstOrNull() ?: "9999") > Day.str(Instant.now().minusSeconds((3 * 365 - 15) * 86400L))   // ios: Data/Market.swift:444
            val bars = mutableMapOf<String, Double>(); var before: String? = null; var ok = false   // ios: Data/Market.swift:445
            repeat(if (full) 5 else 1) {   // ios: Data/Market.swift:446
                if (it > 0 && (before == null || !ok)) return@repeat
                var path = "/api/toss/v1/candles?symbol=${enc(c)}&interval=1d&adjusted=false&count=${if (full) 200 else 30}"   // ios: Data/Market.swift:447
                before?.let { b -> path += "&before=" + enc(b) }   // ios: Data/Market.swift:448
                val (code, o, e) = TossFeed.userGet(path)   // ios: Data/Market.swift:449
                if (code == 404) { miss[c] = now + 86400; before = null; return@repeat }   // ios: Data/Market.swift:450
                val r = jObj(o?.get("result")); if (code != 200 || r == null) { if (e != null || code == 429 || code == 401 || code == 403) stop = true; ok = false; before = null; return@repeat }   // ios: Data/Market.swift:451
                ok = true   // ios: Data/Market.swift:452
                var oldest = "9999"   // ios: Data/Market.swift:453
                for (v in jObjs(r["candles"]) ?: emptyList()) {   // ios: Data/Market.swift:454
                    val t = (v["timestamp"] as? String)?.takeIf { s -> s.length >= 10 } ?: continue; val cl = num(v["closePrice"])?.takeIf { x -> x > 0 } ?: continue   // ios: Data/Market.swift:455
                    val d = t.take(10); bars[d] = cl; oldest = minOf(oldest, d)   // ios: Data/Market.swift:456
                }
                before = r["nextBefore"] as? String   // ios: Data/Market.swift:458
                if (before == null || oldest <= from) before = null   // ios: Data/Market.swift:459
            }
            if (!ok || bars.isEmpty()) continue   // ios: Data/Market.swift:461
            val all: MutableMap<String, Double> = if (full) mutableMapOf() else have!!.dates.zip(have.closes).toMap().toMutableMap()   // ios: Data/Market.swift:462
            for ((d, v) in bars) all[d] = v   // ios: Data/Market.swift:463
            val ds = all.keys.filter { it >= from && (it < md || (it == md && closed)) }.sorted()   // ios: Data/Market.swift:464
            if (ds.isEmpty()) continue   // ios: Data/Market.swift:465
            fh[k] = YahooSample.Hist(dates = ds, closes = ds.map { all[it]!! })   // ios: Data/Market.swift:466
            fday[k] = stamp   // ios: Data/Market.swift:467
        }
        TossFeed.histMiss = miss   // ios: Data/Market.swift:469
        f.h = fh; f.day = fday
        // 원/달러: 10분에 한 번 (당겨 갱신하면 바로). 전일 값은 24시간 전 시각의 값
        // 일봉 받기가 막혀도(429 등) 환율은 따로 묻는다. 안 그러면 하루 묵은 환율로 평가액이 토스와 달라진다
        if (force || now - (f.fx?.got ?: 0.0) > 600) {   // ios: Data/Market.swift:472
            val (code, o, _) = TossFeed.userGet("/api/toss/v1/exchange-rate")   // ios: Data/Market.swift:473
            val rate = if (code == 200) num(jObj(o?.get("result"))?.get("rate")) else null   // ios: Data/Market.swift:474
            if (rate != null && rate > 0) {
                val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"))   // ios: Data/Market.swift:475
                val (c2, o2, _) = TossFeed.userGet("/api/toss/v1/exchange-rate?dateTime=" + enc(iso.format(Instant.ofEpochSecond(Instant.now().epochSecond - 86400))))   // ios: Data/Market.swift:476
                val prev = if (c2 == 200) num(jObj(o2?.get("result"))?.get("rate")) else null   // ios: Data/Market.swift:477
                f.fx = FX(rate = rate, prev = if ((prev ?: 0.0) > 0) prev!! else (f.fx?.prev ?: rate), got = now)   // ios: Data/Market.swift:478
            }
        }
        Store.write(File.serializer(), f, name)   // ios: Data/Market.swift:481
        f   // ios: Data/Market.swift:482
    }
}

/** 매매기준율: 사이트가 하루 한 번 저장하는 data/mar.json ({"USD": {"rate", "date"}}). 웹 평가액과 같은 값 */
object MarFeed {   // ios: Data/Market.swift:487
    @Serializable data class Prev(var rate: Double, var date: String)   // ios: Data/Market.swift:488
    /** prev = 바로 앞 날짜 고시 (새 고시는 전날 오후에 나오지만 토스는 그날 오전 8시 59분부터 쓴다) */
    @Serializable data class File(var rate: Double, var date: String, var got: Double, var prev: Prev? = null)   // ios: Data/Market.swift:490
    const val name = "site_mar.json"   // ios: Data/Market.swift:491
    /** 그 날짜 고시를 쓰기 시작했는지: 한국 시간 그날 오전 8시 59분부터 */
    fun started(date: String): Boolean {   // ios: Data/Market.swift:493
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", posix("en_US_POSIX")).withZone(ZoneId.of("Asia/Seoul"))   // ios: Data/Market.swift:494
        return f.format(Instant.now()) >= date + " 08:59"   // ios: Data/Market.swift:495
    }
    fun cached(): File? = Store.read(File.serializer(), name)   // ios: Data/Market.swift:497

    /** 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값 */
    suspend fun fetch(minAge: Double = 0.0): File? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:500
        val old = cached()   // ios: Data/Market.swift:501
        // 오늘 고시가 아닌 값은 10분만 믿는다 (앱을 새로 깔거나 빌드한 직후 며칠 묵은 값을 30분 넘게 쓰지 않게)
        // 단, 오전 8시 59분 전인데 앞 고시가 없으면 기다리지 않고 바로 받는다 (앞 고시를 써야 하니까)
        if (old != null && (started(old.date) || old.prev != null) && nowSec() - old.got < (if (old.date >= Day.today) minAge else minOf(minAge, 600.0))) return@withContext old   // ios: Data/Market.swift:504
        // 1순위: 서울외국환중개에서 직접 (최신 고시 + 앞 날짜 고시). 못 받을 때만 웹(data/mar.json → 중계 /mar)
        var best: File? = smbs()   // ios: Data/Market.swift:506
        if (best == null) {   // ios: Data/Market.swift:507
            val c = URL(Config.data, "mar.json").toString()   // ios: Data/Market.swift:508
            val queryItems = listOf("t" to (nowSec() / 600).toLong().toString()); val u = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // ios: Data/Market.swift:509
            best = get(FeedReq(u, timeout = 10.0))   // ios: Data/Market.swift:510
            // 웹 marFetch 와 같게: 저장소 값이 오늘 고시가 아니면 중계(/mar)에서 한 번 더 (더 새 날짜일 때만 바꾼다)
            val have = maxOf(best?.date ?: "", old?.date ?: "")   // ios: Data/Market.swift:512
            if (have < Day.today) { val x = get(FeedReq(URL(Config.relay, "mar").toString(), timeout = 10.0)); if (x != null && x.date > (best?.date ?: "")) best = x }   // ios: Data/Market.swift:513
        }
        val f = best ?: return@withContext old   // ios: Data/Market.swift:515
        var out = f
        if (old != null && old.date > f.date) out = File(rate = old.rate, date = old.date, got = f.got, prev = old.prev)   // 중계에서 받아 둔 더 새 값은 지킨다   // ios: Data/Market.swift:516
        else if (old != null && old.date < f.date && f.prev == null) f.prev = Prev(rate = old.rate, date = old.date)   // 날짜가 바뀌면 앞 고시를 남긴다   // ios: Data/Market.swift:517
        else if (old != null && f.prev == null) f.prev = old.prev   // ios: Data/Market.swift:518
        Store.write(File.serializer(), out, name)   // ios: Data/Market.swift:519
        out   // ios: Data/Market.swift:520
    }
    /** 서울외국환중개 매매기준율 표 (최근 2주): 마지막 = 최신 고시, 그 앞 = prev. 중계 worker·server.py 와 같은 파싱 */
    internal suspend fun smbs(): File? {   // ios: Data/Market.swift:523
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", posix("en_US_POSIX")).withZone(ZoneId.of("Asia/Seoul"))   // ios: Data/Market.swift:524
        val st = f.format(Instant.now().minusSeconds(14 * 86400L)); val end = f.format(Instant.now())   // ios: Data/Market.swift:525
        val url = "http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_${st}_${end}"   // ios: Data/Market.swift:526
        val req = FeedReq(url, timeout = 8.0)   // ios: Data/Market.swift:527
        req.setValue("Mozilla/5.0", "User-Agent")   // ios: Data/Market.swift:528
        req.setValue("http://www.smbs.biz/ExRate/StdExRate.jsp", "Referer")   // ios: Data/Market.swift:529
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null; if (code != 200) return null   // ios: Data/Market.swift:530
        return smbsParse(String(data, Charsets.ISO_8859_1))   // ios: Data/Market.swift:531
    }
    /** smbs 응답 글자 → File (서버 없이 시험할 수 있게 따로 둔다) */
    internal fun smbsParse(t: String, got: Double = nowSec()): File? {
        val re = Regex("label=['\"]([^'\"]+)['\"][^>]*?value=['\"]([\\d,.]+)['\"]")   // ios: Data/Market.swift:532
        val days = ArrayList<Prev>()   // ios: Data/Market.swift:533
        for (m in re.findAll(t)) {   // ios: Data/Market.swift:534
            val lr = m.groupValues[1]; val vr = m.groupValues[2]   // ios: Data/Market.swift:535
            val rate = vr.replace(",", "").toDoubleOrNull()?.takeIf { it > 500 && it < 5000 } ?: continue   // ios: Data/Market.swift:536
            val n = lr.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }   // ios: Data/Market.swift:537
            if (n.size < 3) continue   // ios: Data/Market.swift:538
            days.add(Prev(rate = rate, date = fmt("%04d-%02d-%02d", if (n[0] < 100) 2000 + n[0] else n[0], n[1], n[2])))   // ios: Data/Market.swift:539
        }
        days.sortBy { it.date }   // ios: Data/Market.swift:541
        val last = days.lastOrNull() ?: return null   // ios: Data/Market.swift:542
        val pv = days.dropLast(1).lastOrNull()   // ios: Data/Market.swift:543
        return File(rate = last.rate, date = last.date, got = got, prev = pv)   // ios: Data/Market.swift:544
    }
    internal suspend fun get(req: FeedReq): File? {   // ios: Data/Market.swift:546
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null   // ios: Data/Market.swift:547
        if (code != 200) return null   // ios: Data/Market.swift:548
        val o = jObj(feedJson(data)) ?: return null   // ios: Data/Market.swift:549
        val u = jObj(o["USD"]) ?: return null; val rate = num(u["rate"])?.takeIf { it > 0 } ?: return null; val date = u["date"] as? String ?: return null   // ios: Data/Market.swift:550
        // 사이트·중계가 앞 날짜 고시도 주면 같이 둔다 (앱을 처음 깔았거나 저녁에 새 고시만 받은 때도 오전 8시 59분 전엔 앞 고시)
        val pv = jObj(o["USD_prev"])?.let { p -> num(p["rate"])?.let { r -> (p["date"] as? String)?.let { Prev(rate = r, date = it) } } }   // ios: Data/Market.swift:552
        return File(rate = rate, date = date, got = nowSec(), prev = pv?.let { if (it.rate > 0 && it.date < date) it else null })   // ios: Data/Market.swift:553
    }
}
