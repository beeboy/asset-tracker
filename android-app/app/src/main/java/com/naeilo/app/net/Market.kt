package com.naeilo.app.net

import com.naeilo.app.core.cformat
import android.util.Log
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
import com.naeilo.app.data.FlowBars
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.persists
import com.naeilo.app.data.recordDay
import com.naeilo.app.data.LineClock
import com.naeilo.app.data.WidgetBridge
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
private fun fmt(f: String, vararg a: Any): String = cformat(f, *a)
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

        /** 받은 지 얼마나: 방금 · n분 전 · n시간 전 · n일 전 */
        // ios: Data/Market.swift:148
        fun agoText(sec: Double): String {
            val m = kotlin.math.max(0.0, sec).toInt() / 60   // ios: Data/Market.swift:149
            if (m < 1) return "방금"   // ios: Data/Market.swift:150
            if (m < 60) return "${m}분 전"   // ios: Data/Market.swift:151
            if (m < 24 * 60) return "${m / 60}시간 전"   // ios: Data/Market.swift:152
            return "${m / 1440}일 전"   // ios: Data/Market.swift:153
        }
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
    var prices by mutableStateOf(AlpacaHistory.merged(TossHistory.merged(SitePrices.cached(), TossHistory.cached())))   // ios: Data/Market.swift:54
    /** 공개 베타·출시 번들의 자료는 가상 샘플이라 '예시 값' */
    val source: String get() = if (site != null) "naeilo 사이트" else if (YahooSample.quotes.isEmpty() || !Config.usesCollectedData) "예시 값" else "Yahoo 중계 (테스트)"   // ios: Data/Market.swift:55

    fun quote(s: Symbol): Quote {   // ios: Data/Market.swift:60
        val b = yahooQuote(s)   // ios: Data/Market.swift:61
        tossLast(s)?.let { t -> return Quote(last = t, prevClose = tossPrev(s, b) ?: t, live = true) }   // ios: Data/Market.swift:62
        // Alpaca·Longbridge: 전일 종가는 사이트(공식 종가)가 먼저, 없으면 받은 전일 종가
        val a = alpacaQ(s) ?: lbQ(s) ?: return tossReg(s, b) ?: b   // ios: Data/Market.swift:64
        return Quote(last = a.last, prevClose = if (b.prevClose > 0) b.prevClose else if (a.prev > 0) a.prev else a.last, live = true)   // ios: Data/Market.swift:65
    }
    /** Alpaca 값을 쓰는 종목 (종목 화면에 "Alpaca(IEX) 시세" 표시) */
    /** 공개 베타·출시에서 내 키 시세가 없는 실제 미국 종목: 가격 칸에 키 안내를 띄우고 평가액에서 뺀다 (가짜 값·0원으로 세지 않는다) */
    fun needsKey(s: Symbol): Boolean {   // ios: Data/Market.swift:69
        if (Config.usesCollectedData || Sample.isVirtual(s.id) || s.currency != Currency.usd) return false   // ios: Data/Market.swift:70
        return toss?.q?.get(s.id) == null && alpaca?.q?.get(s.id) == null && longbridge?.q?.get(s.id) == null
    }
    fun isAlpaca(s: Symbol): Boolean = tossLast(s) == null && alpacaQ(s) != null   // ios: Data/Market.swift:68
    /** Longbridge 값을 쓰는 종목 (종목 화면에 "Longbridge 시세" 표시) */
    fun isLongbridge(s: Symbol): Boolean = tossLast(s) == null && alpacaQ(s) == null && lbQ(s) != null   // ios: Data/Market.swift:70
    private fun lbQ(s: Symbol): QuoteFeed.Q? {   // ios: Data/Market.swift:71
        if (Sample.isVirtual(s.id) || s.currency != Currency.usd || regClose(s) != null) return null   // ios: Data/Market.swift:78
        val q = longbridge?.q?.get(s.id)?.takeIf { it.last > 0 } ?: return null
        return q   // ios: Data/Market.swift:73
    }
    private fun alpacaQ(s: Symbol): QuoteFeed.Q? {   // ios: Data/Market.swift:75
        if (Sample.isVirtual(s.id) || s.currency != Currency.usd || regClose(s) != null) return null   // ios: Data/Market.swift:82
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
        if (Sample.isVirtual(s.id)) return null; val t = toss?.q?.get(s.id)?.takeIf { it.last > 0 } ?: return null   // ios: Data/Market.swift:93
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
    /** 미국 장이 다 끝났고 토스 값이 있으면 출처와 상관없이 정규장 종가로 센다 (추가한 종목의 묵은 Yahoo 값을 쓰던 일) */
    private fun tossReg(s: Symbol, b: Quote): Quote? {   // ios: Data/Market.swift:635
        if (toss?.q?.get(s.id) == null) return null
        val r = regClose(s) ?: return null
        return Quote(last = r, prevClose = tossPrev(s, b) ?: b.prevClose, live = true)
    }
    private fun regClose(s: Symbol): Double? {   // ios: Data/Market.swift:105
        if (s.currency != Currency.usd || !USSession.closed()) return null   // ios: Data/Market.swift:106
        toss?.q?.get(s.id)?.let { t -> TossDaily.reg(tossDaily(s), at = t.at)?.let { r -> return r } }   // ios: Data/Market.swift:107
        if (tossCovers(s.id)) return null   // 이 종목은 Yahoo 를 안 불러서 사이트 값이 묵었다   // ios: Data/Market.swift:108
        val r = site?.q?.get(YahooSample.keyFor[s.id] ?: CustomSymbols.yahoo(s))?.reg?.takeIf { it > 0 } ?: return null   // ios: Data/Market.swift:109
        return r   // ios: Data/Market.swift:110
    }

    private fun yahooQuote(s: Symbol): Quote {   // ios: Data/Market.swift:113
        // 시험용 Yahoo 끄기: 실제 종목은 내 키 시세만 (빌드 때 넣은 값·추가 종목 Yahoo 값도 쓰지 않는다)
        if (Config.yahooOff && !Sample.isVirtual(s.id)) return Quote(last = 0.0, prevClose = 0.0, live = false)   // ios: Data/Market.swift:121
        if (!Sample.isVirtual(s.id)) {   // ios: Data/Market.swift:122
            // 사이트 시세와 추가한 종목(5분마다 Yahoo 중계) 중 나중에 받은 값
            val c = CustomSymbols.shared.items.firstOrNull { it.id == s.id }   // ios: Data/Market.swift:116
            val f = site; val q = f?.q?.get(YahooSample.keyFor[s.id] ?: CustomSymbols.yahoo(s))   // ios: Data/Market.swift:117
            if (f != null && q != null && (c == null || c.at.toEpochMilli() / 1000.0 < f.got)) {   // ios: Data/Market.swift:118
                return Quote(last = regClose(s) ?: q.last, prevClose = q.prev, live = s.currency == Currency.usd)   // ios: Data/Market.swift:119
            }
            if (c != null) return Quote(last = c.last, prevClose = c.prev, live = c.usd)   // ios: Data/Market.swift:123
            // 한국 종목은 넣은 가격이 없으면 값이 없다 (시안 값을 쓰지 않는다)
            if (s.currency == Currency.krw) return Quote(last = 0.0, prevClose = 0.0, live = false)   // ios: Data/Market.swift:125
        }
        YahooSample.quotes[s.id]?.let { q -> return Quote(last = q.last, prevClose = q.prevClose, live = true) }   // ios: Data/Market.swift:127
        todayMove[s.id]?.let { m -> return Quote(last = s.close * (1 + m), prevClose = s.close, live = true) }   // ios: Data/Market.swift:128
        return Quote(last = s.close, prevClose = s.close, live = false)   // ios: Data/Market.swift:129
    }

    /** 원/달러 (지금, 전일) */
    val fx: Quote get() {   // ios: Data/Market.swift:133
        tossFx?.let { t -> return Quote(last = t.rate, prevClose = t.prev, live = true) }   // ios: Data/Market.swift:134
        site?.q?.get("KRW=X")?.let { q -> return Quote(last = q.last, prevClose = q.prev, live = true) }   // ios: Data/Market.swift:135
        if (!Config.yahooOff) YahooSample.quotes["FX"]?.let { q -> return Quote(last = q.last, prevClose = q.prevClose, live = true) }   // ios: Data/Market.swift:142
        return Quote(last = Sample.fx, prevClose = Sample.fx, live = false)   // ios: Data/Market.swift:137
    }

    /** 지금 쓰는 시세(사이트·토스·Alpaca·Longbridge) 중 가장 늦게 받은 때(got)와 그 값들의 가장 늦은 가격 시각(at), 초.
     *  토스가 내 종목을 다 받으면 사이트 시세는 다시 받지 않는다(QuoteFeed.fetch). 사이트 받은 때만 보면 시각이 멈춘다 */
    // ios: Data/Market.swift:140
    val quoteTimes: Pair<Double, Double>? get() {
        val fs = listOfNotNull(site, toss, alpaca, longbridge).filter { it.q.isNotEmpty() }   // ios: Data/Market.swift:143
        val got = fs.maxOfOrNull { it.got }?.takeIf { it > 0 } ?: return null   // ios: Data/Market.swift:144
        return Pair(got, fs.flatMap { f -> f.q.values.map { it.at } }.maxOrNull() ?: got)   // ios: Data/Market.swift:145
    }

    /** 평가액 박스 오른쪽 위 (위젯의 '1분 전'과 짝). 1시간 넘게 못 받았으면(서버 오류 등) 그 값의 시각 '10/21 23:50 기준'. 경고 없이 같은 모양 */
    // ios: Data/Market.swift:156
    fun freshText(now: Double = nowSec()): String? {
        val t = quoteTimes ?: return null   // ios: Data/Market.swift:157
        val sec = now - t.first   // ios: Data/Market.swift:158
        if (sec < 3600) return agoText(sec)   // ios: Data/Market.swift:159
        val f = DateTimeFormatter.ofPattern("M/d HH:mm", AppModel.dateLocale).withZone(ZoneId.systemDefault())   // ios: Data/Market.swift:160
        return "${f.format(Instant.ofEpochMilli((t.second * 1000).toLong()))} 기준"   // ios: Data/Market.swift:161
    }

    val asOfText: String get() {   // ios: Data/Market.swift:164
        val z = ZoneId.systemDefault(); val f = { p: String -> DateTimeFormatter.ofPattern(p, AppModel.dateLocale).withZone(z) }   // ios: Data/Market.swift:165
        val t = quoteTimes   // ios: Data/Market.swift:166
        if (t != null && t.second > 0) {
            val d = Instant.ofEpochMilli((t.second * 1000).toLong())   // ios: Data/Market.swift:167
            val p = if (d.atZone(z).toLocalDate() == LocalDate.now(z)) "a h:mm" else "M월 d일 a h:mm"   // ios: Data/Market.swift:168
            return "${f(p).format(d)} 시세 · ${agoText(nowSec() - t.first)} 받음"   // ios: Data/Market.swift:169
        }
        YahooSample.asOf?.let { t2 ->   // ios: Data/Market.swift:171
            val p = "M월 d일 a h:mm"   // ios: Data/Market.swift:172
            // 공개 베타·출시 번들은 가상 샘플이라 '예시 값'
            return if (Config.usesCollectedData) "${f(p).format(t2)} 시세 (테스트 자료)" else "${f(p).format(t2)} 예시 값"   // ios: Data/Market.swift:180
        }
        val p = "a h:mm"   // ios: Data/Market.swift:175
        return "지금 시세 · ${f(p).format(updatedAt)} 갱신"   // ios: Data/Market.swift:176
    }

    /** force: 아래로 당겨 갱신할 때. 받은 지 얼마 안 됐어도 다시 받는다 */
    suspend fun refresh(held: Set<String> = emptySet(), force: Boolean = false) = coroutineScope {   // ios: Data/Market.swift:180
        // 토스 시세 (개발자 주소를 넣었거나, 사용자가 설정 > 토스증권 시세에 내 키를 넣은 기기만): 국내·미국 종목.
        // 평소에는 기다리지 않고 따로 받아 도착하면 반영한다 (앱 시작을 늦추지 않게). 당겨 갱신·다시 받기(force)는 토스까지 기다린다.
        // 1분 안에 받았으면 그대로. 실패하면 15분 안에 받은 값, 그것도 없으면 null → Yahoo 값
        val tossWait = async { if (force) refreshToss(held = held, force = true) else Unit }   // ios: Data/Market.swift:184
        if (!force) bg.launch { refreshToss(held = held, force = false) }   // ios: Data/Market.swift:185
        // Alpaca 시세 (설정 > 내 Alpaca 키를 넣은 기기만): 미국 종목. 토스와 같은 방식
        val alpacaWait = async { if (force) refreshAlpaca(held = held, force = true) else Unit }   // ios: Data/Market.swift:187
        if (!force) bg.launch { refreshAlpaca(held = held, force = false) }   // ios: Data/Market.swift:188
        // Longbridge 시세 (설정 > 내 Longbridge 키를 넣은 기기만): 지금은 미국 종목. 같은 방식
        val lbWait = async { if (force) refreshLongbridge(held = held, force = true) else Unit }   // ios: Data/Market.swift:190
        if (!force) bg.launch { refreshLongbridge(held = held, force = false) }   // ios: Data/Market.swift:191
        // 나머지 시세는 한꺼번에 받는다 (차례로 기다리지 않게)
        // 지난 종가: 30분마다 사이트 index.json 만 보고, 새로 모았을 때만 내 종목 파일을 다시 받는다 (내 길·전망 기준값이 웹과 같은 종가)
        val keys = if (held.isEmpty()) (prices?.h?.keys ?: emptySet()).toSet() else held.filter { !Sample.isVirtual(it) }.map(SiteStats::key).toSet() + KoreaEstimate.baseKeys(held)   // ios: Data/Market.swift:194
        val oldPrices = withContext(Dispatchers.IO) { SitePrices.cached() }   // 토스 값을 섞지 않은 사이트 값   // ios: Data/Market.swift:195
        // 지금 가격: 미국 종목·국내 개별 종목·원/달러를 Yahoo 에서 직접 (못 받은 것만 사이트). 4분 안에 받았으면 그대로
        if (held.isNotEmpty()) {   // ios: Data/Market.swift:197
            val syms = held.mapNotNull { id ->   // ios: Data/Market.swift:198
                val s = if (!Sample.isVirtual(id) && id != KoreaEstimate.sumId) Sample.symbol(id) else null; if (s == null) return@mapNotNull null   // ios: Data/Market.swift:199
                if (!((s.currency == Currency.usd || KoreaEstimate.popular.contains(id)) && !tossCovers(id))) return@mapNotNull null   // ios: Data/Market.swift:200
                YahooSample.keyFor[id] ?: CustomSymbols.yahoo(s)   // ios: Data/Market.swift:201
            }
            Store.quoteSyms = (syms.toSet() + (if (tossFx == null) setOf("KRW=X") else emptySet())).sorted()   // ios: Data/Market.swift:203
        }
        val siteNew = async { QuoteFeed.fetch(minAge = if (force) 0.0 else 240.0) }   // ios: Data/Market.swift:205
        val marNew = async { MarFeed.fetch(minAge = if (force) 0.0 else 1800.0) }   // ios: Data/Market.swift:206
        // 토스 일봉이 있는 종목은 빼고 (원/달러·지수는 토스에 일봉이 없어 늘 Yahoo)
        val tossKeys = if (TossKeys.on) tossHist?.h?.keys?.toSet() ?: emptySet() else emptySet()   // ios: Data/Market.swift:208
        val pricesNew = async { SitePrices.fetch(keys = (keys - tossKeys) + setOf("KRW=X") + Parallel.wantKeys, old = oldPrices, minAge = if (force) 0.0 else 1800.0) }   // ios: Data/Market.swift:209
        // 전망 입력값: 하루 한 번 바뀌므로 6시간에 한 번 (당겨 갱신하면 바로)
        val statsNew = async { SiteStats.fetch(minAge = if (force) 0.0 else 6 * 3600.0) }   // ios: Data/Market.swift:211
        // 추가한 종목: 내 종목에 있는 것만 5분에 한 번 (당겨 갱신하면 바로)
        val customDone = async { CustomSymbols.shared.refresh(held = held, force = force) }   // ios: Data/Market.swift:213
        val extraDone = async { ExtraStats.shared.refresh(Parallel.wantKeys) }   // ios: Data/Market.swift:214
        val earnDone = async { com.naeilo.app.data.EarningsFeed.shared.refresh(held = held, force = force) }   // ios: Data/Market.swift:216 내 몫 실적 (보유 미국 종목, 하루 한 번)
        siteNew.await()?.let { site = it }   // ios: Data/Market.swift:215
        marNew.await()?.let { mar = it }   // ios: Data/Market.swift:216
        pricesNew.await()?.let { p -> prices = AlpacaHistory.merged(TossHistory.merged(p, tossHist)) }   // ios: Data/Market.swift:227
        statsNew.await()?.let { stats = it }   // ios: Data/Market.swift:218
        customDone.await()   // ios: Data/Market.swift:219
        extraDone.await()   // ios: Data/Market.swift:220
        earnDone.await()
        tossWait.await()   // ios: Data/Market.swift:221
        alpacaWait.await()   // ios: Data/Market.swift:222
        lbWait.await()   // ios: Data/Market.swift:223
        updatedAt = Instant.now()   // ios: Data/Market.swift:224
    }

    /** 토스 지금 가격과 지난 종가·원/달러를 받는다. 받는 중이면 평소에는 건너뛰고, force 면 그 일이 끝난 뒤 새로 한 번 더 받는다 */
    suspend fun refreshToss(held: Set<String>, force: Boolean) {   // ios: Data/Market.swift:228,229
        val ty = mutableMapOf<String, String>()   // ios: Data/Market.swift:230
        for (id in held) { if (Sample.isVirtual(id) || id == KoreaEstimate.sumId) continue; Sample.symbol(id)?.let { s -> ty[id] = CustomSymbols.yahoo(s) } }   // ios: Data/Market.swift:231
        if (ty.isEmpty() || !(TossFeed.base != null || TossKeys.on)) return   // ios: Data/Market.swift:232
        synchronized(this) { tossTask }?.let { t ->   // ios: Data/Market.swift:233
            if (!force) return   // ios: Data/Market.swift:234
            t.await()     // 받는 중인 일이 끝나길 기다렸다가 새로 받는다 (끝난 일을 거듭 기다리며 도는 일이 없게 한 번만)   // ios: Data/Market.swift:235
        }
        val t = bg.async {   // ios: Data/Market.swift:237
            val q = TossFeed.fetch(ty, minAge = if (force) 0.0 else 60.0, force = force)   // ios: Data/Market.swift:238
            toss = q   // ios: Data/Market.swift:239
            // 지난 종가·원/달러도 내 키로 (종목마다 하루 한 번, 환율은 10분에 한 번). '다른 방식' 바구니 종목(SPY·SGOV 등)도 같이
            val hk = ty.entries.groupBy({ SiteStats.key(it.key) }, { it.value }).mapValues { it.value.first() }.toMutableMap()   // ios: Data/Market.swift:252
            for (k in com.naeilo.app.core.calc.Parallel.wantKeys) if (hk[k] == null) hk[k] = k   // ios: Data/Market.swift:253
            TossHistory.fetch(hk, force = force)?.let { h ->   // ios: Data/Market.swift:254
                tossHist = h   // ios: Data/Market.swift:242
                prices = AlpacaHistory.merged(TossHistory.merged(SitePrices.cached(), h))   // ios: Data/Market.swift:254
            }
            com.naeilo.app.data.FlowStore.reload()   // ios: Data/Market.swift:245
            tossDone = Instant.now()   // ios: Data/Market.swift:246
        }
        synchronized(this) { tossTask = t }   // ios: Data/Market.swift:248
        t.await()   // ios: Data/Market.swift:249
        synchronized(this) { if (tossTask === t) tossTask = null }   // ios: Data/Market.swift:250
    }

    /** Alpaca 지금 가격을 받는다 (1분 안에 받았으면 그대로, 당겨 갱신은 바로) */
    suspend fun refreshAlpaca(held: Set<String>, force: Boolean) {   // ios: Data/Market.swift:255
        if (!AlpacaKeys.on) { if (alpaca != null) alpaca = null; return }   // ios: Data/Market.swift:256
        val ty = mutableMapOf<String, String>()   // ios: Data/Market.swift:257
        for (id in held) { if (Sample.isVirtual(id)) continue; Sample.symbol(id)?.takeIf { it.currency == Currency.usd }?.let { s -> ty[id] = CustomSymbols.yahoo(s) } }   // ios: Data/Market.swift:258
        if (ty.isEmpty()) return   // ios: Data/Market.swift:259
        alpaca = AlpacaFeed.fetch(ty, minAge = if (force) 0.0 else 60.0)   // ios: Data/Market.swift:260
        alpacaDone = Instant.now()   // ios: Data/Market.swift:261
        // 지난 종가도 내 Alpaca 키로: 사이트·토스 일봉이 없는 종목만, 종목마다 하루 한 번 (차트 탭과 같은 flow_alpaca.json)
        val site = SitePrices.cached(); val tossH = TossHistory.cached()   // ios: Data/Market.swift:274
        val keys = mutableMapOf<String, String>()   // ios: Data/Market.swift:275
        for ((id, y) in ty) {   // ios: Data/Market.swift:276
            val k = SiteStats.key(id)   // ios: Data/Market.swift:277
            if (site?.h?.get(k) == null && tossH?.h?.get(k) == null) AlpacaFeed.code(y)?.let { keys[k] = it }   // ios: Data/Market.swift:278
        }
        // '다른 방식' 바구니 종목(SPY·SGOV 등)도: 예전엔 사이트(Yahoo) 값만 써서 내 키만 켜면 화면이 비었다
        for (k in com.naeilo.app.core.calc.Parallel.wantKeys) if (keys[k] == null && site?.h?.get(k) == null && tossH?.h?.get(k) == null) AlpacaFeed.code(k)?.let { keys[k] = it }   // ios: Data/Market.swift:283
        if (keys.isNotEmpty() && com.naeilo.app.data.FlowAlpaca.fetch(keys)) {   // ios: Data/Market.swift:280
            prices = AlpacaHistory.merged(TossHistory.merged(site, tossH))   // ios: Data/Market.swift:281
            com.naeilo.app.data.FlowStore.reload()   // ios: Data/Market.swift:282
        }
    }

    /** Longbridge 지금 가격을 받는다 (1분 안에 받았으면 그대로, 당겨 갱신은 바로) */
    suspend fun refreshLongbridge(held: Set<String>, force: Boolean) {   // ios: Data/Market.swift:265,266
        if (!LongbridgeKeys.on) { if (longbridge != null) longbridge = null; return }   // ios: Data/Market.swift:267
        val ty = mutableMapOf<String, String>()   // ios: Data/Market.swift:268
        for (id in held) { if (Sample.isVirtual(id)) continue; Sample.symbol(id)?.takeIf { it.currency == Currency.usd }?.let { s -> ty[id] = CustomSymbols.yahoo(s) } }   // ios: Data/Market.swift:269
        if (ty.isEmpty()) return   // ios: Data/Market.swift:270
        longbridge = LongbridgeFeed.fetch(ty, minAge = if (force) 0.0 else 60.0)   // ios: Data/Market.swift:271
        longbridgeDone = Instant.now()   // ios: Data/Market.swift:272
        // 지난 종가도 내 Longbridge 키로: 사이트·토스·Alpaca 일봉이 없는 종목만, 종목마다 하루 한 번 (차트 탭과 같은 flow_longbridge_adj.json)
        val site = SitePrices.cached(); val tossH = TossHistory.cached()   // ios: Data/Market.swift:300
        val alp = if (AlpacaKeys.on) FlowBars.read(FlowBars.alpacaName) else FlowBars()   // ios: Data/Market.swift:301
        val keys = mutableMapOf<String, String>()   // ios: Data/Market.swift:302
        for ((id, y) in ty) {   // ios: Data/Market.swift:303
            val k = SiteStats.key(id)   // ios: Data/Market.swift:304
            if (site?.h?.get(k) == null && tossH?.h?.get(k) == null && (alp.b[k]?.d?.size ?: 0) < 30) LongbridgeFeed.code(y)?.let { keys[k] = it }   // ios: Data/Market.swift:305
        }
        // '다른 방식' 바구니 종목(SPY·SGOV 등)도
        for (k in com.naeilo.app.core.calc.Parallel.wantKeys) if (keys[k] == null && site?.h?.get(k) == null && tossH?.h?.get(k) == null && (alp.b[k]?.d?.size ?: 0) < 30) LongbridgeFeed.code(k)?.let { keys[k] = it }   // ios: Data/Market.swift:308,309
        if (keys.isNotEmpty() && com.naeilo.app.data.FlowLongbridge.fetch(keys)) {   // ios: Data/Market.swift:311
            prices = AlpacaHistory.merged(TossHistory.merged(site, tossH))   // ios: Data/Market.swift:312
            com.naeilo.app.data.FlowStore.reload()   // ios: Data/Market.swift:313
        }
    }

    /** 아래로 당겨 갱신: 내 종목을 바로 받고 위젯에도 넘긴다 */
    suspend fun pullRefresh(m: AppModel) {   // ios: Data/Market.swift:287,288
        refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Data/Market.swift:289
        if (m.persists) m.recordDay()     // 내 기록: 새로 받은 시세로 오늘 줄   // ios: Data/Market.swift:290
        LineClock.shared.next()     // 당기면 인물 대사도 다음 줄로   // ios: Data/Market.swift:291
        WidgetBridge.write(m)   // ios: Data/Market.swift:292
    }
}

val Symbol.quote: Quote get() = Market.shared.quote(this)   // ios: Data/Market.swift:296,297
/** 지금 가격 */
val Symbol.last: Double get() = quote.last   // ios: Data/Market.swift:299

// 테스트 시세: 사이트가 모으는 Yahoo 중계 자료 (data/prices 의 종목별 json 3년 종가, data/quotes.json 전일 종가·현재가).
// DRNK 는 가상 종목이라 시안 값, 한국 종목과 MSFT 는 자료가 없어 시안 값을 쓴다.
// 빌드 때 넣은 파일(assets)은 처음 쓸 때 한 번만 읽는다 (lazy)
object YahooSample {   // ios: Data/Market.swift:304
    @Serializable data class Hist(val dates: List<String>, val closes: List<Double>) {   // ios: Data/Market.swift:305, Data/Lots.swift:54
        /** 날짜 d 의 종가 (그날이 없으면 그 전 마지막 거래일). d 가 첫날보다 앞이면 null */
        fun on(d: String): Double? {   // ios: Data/Lots.swift:56
            var lo = 0; var hi = dates.size - 1; var k = -1   // ios: Data/Lots.swift:57
            while (lo <= hi) { val mid = (lo + hi) / 2; if (dates[mid] <= d) { k = mid; lo = mid + 1 } else { hi = mid - 1 } }   // ios: Data/Lots.swift:58
            return if (k >= 0 && k < closes.size) closes[k] else null   // ios: Data/Lots.swift:59
        }
    }
    data class Q(val prevClose: Double, val last: Double, val time: Instant)   // ios: Data/Market.swift:306

    /** 공개 베타·출시 번들은 가상 샘플 (data/sample): 종목 id 가 곧 파일 기호 */
    val keyFor: Map<String, String> = if (Config.isInternal) mapOf("QQQ" to "QQQ", "AAPL" to "AAPL", "NVDA" to "NVDA", "SPY" to "SPY", "FX" to "KRW=X")   // ios: Data/Market.swift:320
        else Sample.virtualSymbols.associate { it.id to it.id } + ("FX" to "KRW=X")
    private fun file(k: String): String = k.replace("=", "_").replace("^", "_")   // ios: Data/Market.swift:309
    /** Bundle.main.url(forResource:withExtension:subdirectory:) 자리: assets 파일 (없으면 null) */
    private fun asset(name: String, ext: String, dir: String? = null): ByteArray? =
        runCatching { Defaults.ctx.assets.open((dir?.let { "$it/" } ?: "") + "$name.$ext").use { it.readBytes() } }.getOrNull()

    val quotes: Map<String, Q> by lazy {   // ios: Data/Market.swift:311
        val d = asset("quotes", "json"); val o = d?.let { feedJson(it) }?.let { jObj(it) }   // ios: Data/Market.swift:312,313
        if (o == null || o.values.any { jObj(it) == null }) return@lazy emptyMap()   // ios: Data/Market.swift:314
        val out = mutableMapOf<String, Q>()   // ios: Data/Market.swift:315
        for ((id, k) in keyFor) {   // ios: Data/Market.swift:316
            val v = jObj(o[k]) ?: continue; val prev = v["prev_close"] as? Double ?: continue; val last = (v["last"] ?: v["regular"]) as? Double ?: continue   // ios: Data/Market.swift:317
            out[id] = Q(prevClose = prev, last = last, time = Instant.ofEpochMilli((((v["last_time"] as? Double) ?: 0.0) * 1000).toLong()))   // ios: Data/Market.swift:318
        }
        out   // ios: Data/Market.swift:307
    }   // ios: Data/Market.swift:321

    val history: Map<String, Hist> by lazy {   // ios: Data/Market.swift:323
        val out = mutableMapOf<String, Hist>()   // ios: Data/Market.swift:324
        for ((id, k) in keyFor) {   // ios: Data/Market.swift:325
            val d = asset(file(k), "json", "prices") ?: continue   // ios: Data/Market.swift:326,327
            val o = jObj(feedJson(d)) ?: continue   // ios: Data/Market.swift:328
            val dates = jArr(o["dates"])?.let { l -> l.map { it as? String ?: return@let null } } ?: continue; val c = jArr(o["close"]) ?: continue   // ios: Data/Market.swift:329
            val closes = c.map { (it as? Double) ?: Double.NaN }   // ios: Data/Market.swift:330
            // 빈 값은 앞 값으로 메운다
            val filled = ArrayList<Double>(closes.size); var prev = closes.firstOrNull { !it.isNaN() } ?: 1.0   // ios: Data/Market.swift:332
            for (v in closes) { if (!v.isNaN()) prev = v; filled.add(prev) }   // ios: Data/Market.swift:333
            out[id] = Hist(dates = dates, closes = filled)   // ios: Data/Market.swift:334
        }
        out   // ios: Data/Market.swift:336
    }   // ios: Data/Market.swift:337

    /** 자료가 언제 값인지 (현재가 시각) */
    val asOf: Instant? get() = quotes.values.map { it.time }.maxOrNull()   // ios: Data/Market.swift:340

    /** 빌드 때 넣은 지난 종가 전부 (사이트 티커 → 종가). 사이트에서 아직 못 받았을 때만 쓴다 */
    val bundled: Map<String, Hist> by lazy {   // ios: Data/Market.swift:343
        val out = mutableMapOf<String, Hist>()   // ios: Data/Market.swift:344
        for (f in runCatching { Defaults.ctx.assets.list("prices") }.getOrNull()?.filter { it.endsWith("." + "json") } ?: emptyList()) {   // ios: Data/Market.swift:345
            val o = asset(f.removeSuffix(".json"), "json", "prices")?.let { jObj(feedJson(it)) } ?: continue   // ios: Data/Market.swift:346
            val k = o["symbol"] as? String ?: continue; val h = SitePrices.hist(o) ?: continue   // ios: Data/Market.swift:347
            out[k] = h   // ios: Data/Market.swift:348
        }
        out   // ios: Data/Market.swift:350
    }   // ios: Data/Market.swift:351
}

/** 사이트 지난 종가 (data/prices 의 종목별 json): 웹이 자산 추이·목표 진행(내 길)에 쓰는 것과 같은 파일.
 *  빌드 때 넣은 파일은 맥에서 받은 날에 멈춰 있어서, 앱은 내 종목과 원/달러만 사이트에서 다시 받아 둔다 */
object SitePrices {   // ios: Data/Market.swift:356
    /** h 키 = 사이트 티커 (KRW=X 등). asked = 그때 받으려던 티커 (사이트에 없는 종목은 h 에 없다) */
    @Serializable data class File(var got: Double, var updated: String, var asked: List<String>, var h: Map<String, YahooSample.Hist>)   // ios: Data/Market.swift:358
    const val name = "site_prices.json"   // ios: Data/Market.swift:359
    /** 공개 베타·출시는 사이트·Yahoo 종가를 쓰지 않는다 (번들의 가상 샘플만) */
    fun cached(): File? = if (Config.usesCollectedData) Store.read(File.serializer(), name) else null   // ios: Data/Market.swift:364

    /** 빈 값은 앞 값으로 메운다 (웹 history 와 같다) */
    fun hist(o: Map<String, Any?>): YahooSample.Hist? {   // ios: Data/Market.swift:363
        val ds = jArr(o["dates"])?.map { it as? String ?: return null } ?: return null; val c = jArr(o["close"]) ?: return null; if (ds.size != c.size || ds.isEmpty()) return null   // ios: Data/Market.swift:364
        val raw: List<Double> = c.map { (it as? Double) ?: Double.NaN }   // ios: Data/Market.swift:365
        var prev: Double = raw.firstOrNull { !it.isNaN() } ?: 1.0   // ios: Data/Market.swift:366
        val out = ArrayList<Double>(raw.size)   // ios: Data/Market.swift:367
        for (v in raw) { if (!v.isNaN()) prev = v; out.add(prev) }   // ios: Data/Market.swift:368
        return YahooSample.Hist(dates = ds, closes = out)   // ios: Data/Market.swift:369
    }

    internal suspend fun get(path: String): Map<String, Any?>? {   // ios: Data/Market.swift:372
        val c = URL(Config.data, path).toString()   // ios: Data/Market.swift:373
        val queryItems = listOf("t" to (nowSec() / 600).toLong().toString()); val url = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // ios: Data/Market.swift:374
        val req = FeedReq(url, timeout = 15.0)   // ios: Data/Market.swift:375
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: Data/Market.swift:376
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null; if (code != 200) return null   // ios: Data/Market.swift:377
        return jObj(feedJson(data))   // ios: Data/Market.swift:378
    }

    /** Yahoo 일봉 종가 (사이트 prices 의 close 와 같은 기준). 날짜는 그 거래소 날짜 */
    suspend fun daily(sym: String, range: String): YahooSample.Hist? {   // ios: Data/Market.swift:382
        val enc = pctEncode(sym)   // ios: Data/Market.swift:383
        val o = runCatching { Relay.yahoo("/v8/finance/chart/$enc?range=$range&interval=1d", timeout = 10.0) }.getOrNull() ?: return null   // ios: Data/Market.swift:384
        val r = jObjs(jObj(o["chart"])?.get("result"))?.firstOrNull() ?: return null   // ios: Data/Market.swift:385
        val m = jObj(r["meta"]) ?: return null; val ts = jDbls(r["timestamp"]) ?: return null   // ios: Data/Market.swift:386
        val cl = jArr(jObjs(jObj(r["indicators"])?.get("quote"))?.firstOrNull()?.get("close")) ?: return null   // ios: Data/Market.swift:387
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", posix("en_US_POSIX"))   // ios: Data/Market.swift:388,389
            .withZone(zoneOr((m["exchangeTimezoneName"] as? String) ?: "America/New_York"))
        val ds = ArrayList<String>(); val cs = ArrayList<Double>()   // ios: Data/Market.swift:390
        for ((t, c) in ts.zip(cl)) {   // ios: Data/Market.swift:391
            val v = num(c)?.takeIf { it > 0 } ?: continue   // ios: Data/Market.swift:392
            val d = f.format(Instant.ofEpochMilli((t * 1000).toLong()))   // ios: Data/Market.swift:393
            if (ds.lastOrNull() == d) cs[cs.size - 1] = v else { ds.add(d); cs.add(v) }   // ios: Data/Market.swift:394
        }
        return if (ds.isEmpty()) null else YahooSample.Hist(dates = ds, closes = cs)   // ios: Data/Market.swift:396
    }

    /** 한 종목: 받아 둔 3년 종가가 있으면 최근 한 달만 받아 끝을 바꾸고, 없으면 3년을 받는다 */
    private suspend fun direct(k: String, had: YahooSample.Hist?): YahooSample.Hist? {   // ios: Data/Market.swift:400
        val last = had?.dates?.lastOrNull()   // ios: Data/Market.swift:401
        if (had != null && last != null && had.dates.size > 200 && Day.dday(last) >= -20) {
            val n = daily(k, range = "1mo"); val first = n?.dates?.firstOrNull()   // ios: Data/Market.swift:402
            if (n != null && first != null) {
                val keep = had.dates.indexOfFirst { it >= first }.takeIf { it >= 0 } ?: had.dates.size   // ios: Data/Market.swift:403
                val ds = had.dates.subList(0, keep) + n.dates; val cs = had.closes.subList(0, keep) + n.closes   // ios: Data/Market.swift:404
                val cut = maxOf(0, ds.size - 800)   // 3년 남짓만 둔다   // ios: Data/Market.swift:405
                return YahooSample.Hist(dates = ds.drop(cut), closes = cs.drop(cut))   // ios: Data/Market.swift:406
            }
        }
        return daily(k, range = "3y")   // ios: Data/Market.swift:408
    }

    /** keys = 사이트 티커(= Yahoo 기호). Yahoo 에서 직접 받고, 못 받은 것만 사이트 파일로 채운다. 받은 지 minAge 안이고 다 있으면 그대로 */
    suspend fun fetch(keys: Set<String>, old: File?, minAge: Double): File? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:417
        if (!Config.usesCollectedData) return@withContext null   // ios: Data/Market.swift:418
        val now = nowSec()   // ios: Data/Market.swift:413
        if (old != null && now - old.got < minAge && old.asked.toSet().containsAll(keys)) return@withContext old   // ios: Data/Market.swift:414
        val h = mutableMapOf<String, YahooSample.Hist>()   // ios: Data/Market.swift:415
        coroutineScope {   // ios: Data/Market.swift:416
            val g = keys.map { k -> val had = old?.h?.get(k); async { k to direct(k, had) } }   // ios: Data/Market.swift:417
            for ((k, v) in g.awaitAll()) { if (v != null) h[k] = v }   // ios: Data/Market.swift:418
        }
        val miss = keys.filter { h[it] == null }   // ios: Data/Market.swift:420
        var updated = old?.updated ?: ""   // ios: Data/Market.swift:421
        val idx = if (miss.isNotEmpty()) get("index.json") else null; val files = jObj(idx?.get("prices"))   // ios: Data/Market.swift:422
        if (idx != null && files != null) {
            updated = idx["updated"] as? String ?: ""   // ios: Data/Market.swift:423
            for (k in miss) {   // ios: Data/Market.swift:424
                val f = files[k] as? String ?: continue   // ios: Data/Market.swift:425
                get("prices/" + f)?.let { o -> hist(o)?.let { x -> h[k] = x } }   // ios: Data/Market.swift:426
            }
        }
        for (k in keys) { if (h[k] == null) old?.h?.get(k)?.let { x -> h[k] = x } }   // ios: Data/Market.swift:429
        val out = File(got = now, updated = updated, asked = keys.sorted(), h = h)   // ios: Data/Market.swift:430
        Store.write(File.serializer(), out, name)   // ios: Data/Market.swift:431
        out   // ios: Data/Market.swift:432
    }
}

/** 내 토스 키로 받는 지난 종가(일봉)와 원/달러. 키를 넣은 기기만 (공개 버전은 사용자 키로만 시세를 보여 준다).
 *  일봉은 수정 안 한 종가(adjusted=false): 사이트 data/prices 의 close 와 같은 기준. 받은 종목은 사이트 값 대신 쓰고, 못 받은 종목은 사이트 값 그대로.
 *  원/달러 지난 흐름(KRW=X)은 토스에 일봉이 없어 아직 사이트 값이다. */
object TossHistory {   // ios: Data/Market.swift:439
    @Serializable data class FX(var rate: Double, var prev: Double, var got: Double)   // 지금, 24시간 전, 받은 시각(초)   // ios: Data/Market.swift:440
    /** h 키 = 사이트 티커, day = 그 종목을 마지막으로 받은 날 */
    @Serializable data class File(var h: Map<String, YahooSample.Hist>, var day: Map<String, String>, var fx: FX? = null)   // ios: Data/Market.swift:442
    const val name = "toss_history.json"   // ios: Data/Market.swift:443
    fun cached(): File? = if (TossKeys.on) Store.read(File.serializer(), name) else null   // ios: Data/Market.swift:444

    fun merged(p: SitePrices.File?, t: File?): SitePrices.File? {   // ios: Data/Market.swift:446
        if (t == null || !TossKeys.on || t.h.isEmpty()) return p   // ios: Data/Market.swift:447
        val out = p?.copy() ?: SitePrices.File(got = 0.0, updated = "", asked = emptyList(), h = emptyMap())   // ios: Data/Market.swift:448
        out.h = out.h + t.h.mapValues { (k, v) -> TossSplit.fit(v, p?.h?.get(k)) }   // ios: Data/Market.swift:449 분할 전 날은 사이트 기준으로 맞춘다
        return out   // ios: Data/Market.swift:450
    }

    private fun enc(v: String): String =   // ios: Data/Market.swift:453
        pctEncode(v, "-._~")   // ios: Data/Market.swift:454
    /** 그 시장의 오늘 날짜와, 오늘 장이 끝났는지 (끝나기 전 오늘 일봉은 종가가 아니라 뺀다) */
    private fun marketDay(kr: Boolean): Pair<String, Boolean> {   // ios: Data/Market.swift:457
        val c = Instant.now().atZone(ZoneId.of(if (kr) "Asia/Seoul" else "America/New_York"))   // ios: Data/Market.swift:458,459,460
        val d = fmt("%04d-%02d-%02d", c.year, c.monthValue, c.dayOfMonth)   // ios: Data/Market.swift:461
        val m = c.hour * 60 + c.minute   // ios: Data/Market.swift:462
        return d to (m >= (if (kr) 15 * 60 + 40 else 16 * 60 + 10))   // ios: Data/Market.swift:463
    }

    /** keys: 사이트 티커 → Yahoo 기호. 종목마다 하루 한 번: 처음엔 3년치(200개씩), 그다음엔 최근 30개만 받아 잇는다.
     *  너무 많이 부르면(429)·중계에 못 닿으면 거기서 멈추고 다음 갱신 때 이어 받는다 */
    suspend fun fetch(keys: Map<String, String>, force: Boolean): File? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:468
        if (!TossKeys.on) return@withContext null   // ios: Data/Market.swift:469
        val f = Store.read(File.serializer(), name) ?: File(h = emptyMap(), day = emptyMap(), fx = null)   // ios: Data/Market.swift:470
        val fh = f.h.toMutableMap(); val fday = f.day.toMutableMap()
        val now = nowSec(); val today = Day.today   // ios: Data/Market.swift:471
        val from = Day.str(Instant.now().minusSeconds((3 * 365 + 15) * 86400L))   // 사이트 파일과 같은 3년 남짓   // ios: Data/Market.swift:472
        val miss = TossFeed.histMiss.toMutableMap()   // ios: Data/Market.swift:473
        // 한 번만: 중계에 일봉 경로가 없던 때(404) 모든 종목에 하루 막음이 걸린 기기가 있다. 이 판에서 한 번 푼다
        if (!Store.defaults.getBoolean("toss_hist_miss_reset_relay", false)) { miss.clear(); Store.defaults.edit().putBoolean("toss_hist_miss_reset_relay", true).apply() }   // ios: Data/Market.swift:475
        var stop = false   // ios: Data/Market.swift:476
        val ohlc = FlowBars.read(FlowBars.tossName); var ohlcDirty = false   // 차트 탭 캔들: 같은 응답에서 시가·고가·저가·거래량도 남긴다   // ios: Data/Market.swift:477
        Log.i("naeilo.flow", "토스 일봉 받기: ${keys.size}종목")
        for ((k, y) in keys.entries.sortedBy { it.key }) {   // ios: Data/Market.swift:478
            if (stop) break
            // 하루 한 번, 장이 끝난 뒤 한 번 더 (그날 종가가 정규장 종가·다음 날 전일 종가가 된다)
            val (md, closed) = marketDay(kr = y.endsWith(".KS") || y.endsWith(".KQ"))   // ios: Data/Market.swift:480
            val stamp = if (closed) today + "c" else today   // ios: Data/Market.swift:481
            // 캔들이 30일이 안 되면 그날 한 번(장 끝난 뒤 한 번 더) 다시 3년치를 묻는다 (한 번 비면 영영 비는 일이 없게)
            val short = (ohlc.b[k]?.d?.size ?: 0) < 30   // ios: Data/Market.swift:483
            val c = TossFeed.code(y); if (c == null) { Log.i("naeilo.flow", "토스 일봉 $k: 토스 코드 없음 ($y)"); continue }
            if (!((miss[c] ?: 0.0) < now && (fday[k] != stamp || fh[k] == null || (short && ohlc.day[k] != stamp)))) { Log.i("naeilo.flow", "토스 일봉 $c: 건너뜀 (404 막음 ${((miss[c] ?: 0.0) - now).toInt()}초 남음, 오늘 받음 ${fday[k] == stamp}, 캔들 ${ohlc.b[k]?.d?.size ?: -1})"); continue }   // ios: Data/Market.swift:484
            val have = fh[k]   // ios: Data/Market.swift:485
            // 캔들을 아직 안 받은 종목도 한 번은 3년치를 받는다 (예전 판은 종가만 남겼다)
            val full = (have?.dates?.firstOrNull() ?: "9999") > Day.str(Instant.now().minusSeconds((3 * 365 - 15) * 86400L)) || short   // ios: Data/Market.swift:487
            val bars = mutableMapOf<String, Double>(); var before: String? = null; var ok = false   // ios: Data/Market.swift:488
            val rows = mutableMapOf<String, FlowBars.Row>()   // ios: Data/Market.swift:489
            repeat(if (full) 5 else 1) {   // ios: Data/Market.swift:490
                if (it > 0 && (before == null || !ok)) return@repeat
                var path = "/api/toss/v1/candles?symbol=${enc(c)}&interval=1d&adjusted=false&count=${if (full) 200 else 30}"   // ios: Data/Market.swift:491
                before?.let { b -> path += "&before=" + enc(b) }   // ios: Data/Market.swift:492
                val (code, o, e) = TossFeed.userGet(path)   // ios: Data/Market.swift:493
                // 404 가 중계 쪽(없는 경로)이면 종목 탓이 아니다: 막지 않고 이번엔 그만 (중계를 고치면 다음 갱신에 받는다)
                if (code == 404 && jObj(o?.get("error"))?.get("code") == "relay") { Log.i("naeilo.flow", "토스 일봉: 중계에 일봉 경로가 없음 (404 relay)"); stop = true; before = null; return@repeat }   // ios: Data/Market.swift:495
                if (code == 404) { Log.i("naeilo.flow", "토스 일봉 $c: 404 (하루 막음)"); miss[c] = now + 86400; before = null; return@repeat }   // ios: Data/Market.swift:496
                // 뒤쪽 쪽이 실패해도 앞에서 받은 날은 남긴다 (첫 쪽 실패만 못 받은 것)
                val r = jObj(o?.get("result")); if (code != 200 || r == null) { Log.i("naeilo.flow", "토스 일봉 $c: 응답 $code ${e ?: ""} before=$before ${o?.toString()?.take(160)}"); if (e != null || code == 429 || code == 401 || code == 403) stop = true; if (before == null) ok = false; before = null; return@repeat }   // ios: Data/Market.swift:498
                ok = true   // ios: Data/Market.swift:499
                var oldest = "9999"   // ios: Data/Market.swift:500
                for (v in jObjs(r["candles"]) ?: emptyList()) {   // ios: Data/Market.swift:501
                    val t = (v["timestamp"] as? String)?.takeIf { s -> s.length >= 10 } ?: continue; val cl = num(v["closePrice"])?.takeIf { x -> x > 0 } ?: continue   // ios: Data/Market.swift:502
                    val d = t.take(10); bars[d] = cl; oldest = minOf(oldest, d)   // ios: Data/Market.swift:503
                    val op = num(v["openPrice"]); val hp = num(v["highPrice"]); val lp = num(v["lowPrice"]); if (op != null && hp != null && lp != null && op > 0 && hp > 0 && lp > 0) {   // ios: Data/Market.swift:504
                        rows[d] = FlowBars.Row(o = op, h = maxOf(hp, op, cl), l = minOf(lp, op, cl), c = cl, v = num(v["volume"]) ?: 0.0)   // ios: Data/Market.swift:505
                    }
                }
                // 토스는 1000분의 1초를 붙여 준다(…00.000+09:00). 중계는 초까지만 받으니 떼어 낸다
                before = (r["nextBefore"] as? String)?.replace(Regex("""\.\d+(?=Z|[+-]\d{2}:\d{2}$)"""), "")   // ios: Data/Market.swift:509
                if (before == null || oldest <= from) before = null   // ios: Data/Market.swift:510
            }
            if (!ok || bars.isEmpty()) continue   // ios: Data/Market.swift:512
            val all: MutableMap<String, Double> = if (full) mutableMapOf() else have!!.dates.zip(have.closes).toMap().toMutableMap()   // ios: Data/Market.swift:513
            for ((d, v) in bars) all[d] = v   // ios: Data/Market.swift:514
            val ds = all.keys.filter { it >= from && (it < md || (it == md && closed)) }.sorted()   // ios: Data/Market.swift:515
            if (ds.isEmpty()) continue   // ios: Data/Market.swift:516
            fh[k] = YahooSample.Hist(dates = ds, closes = ds.map { all[it]!! })   // ios: Data/Market.swift:517
            fday[k] = stamp   // ios: Data/Market.swift:518
            ohlc.day = ohlc.day + (k to stamp); ohlcDirty = true   // 캔들을 물어본 날 (짧아도 그날은 다시 안 묻는다)   // ios: Data/Market.swift:519
            if (rows.isEmpty()) Log.i("naeilo.flow", "토스 일봉 $c: 종가 ${bars.size}개는 받았는데 시가·고가·저가가 없음")
            if (rows.isNotEmpty()) { ohlc.merge(k, rows, full = full, keep = { it >= from && (it < md || (it == md && closed)) }); ohlcDirty = true }   // ios: Data/Market.swift:520
        }
        TossFeed.histMiss = miss   // ios: Data/Market.swift:522
        if (ohlcDirty) { FlowBars.write(ohlc, FlowBars.tossName); Log.i("naeilo.flow", "토스 캔들 저장: " + ohlc.b.entries.joinToString { "${it.key} ${it.value.d.size}" }) }   // ios: Data/Market.swift:523
        f.h = fh; f.day = fday
        // 원/달러: 10분에 한 번 (당겨 갱신하면 바로). 전일 값은 24시간 전 시각의 값
        // 일봉 받기가 막혀도(429 등) 환율은 따로 묻는다. 안 그러면 하루 묵은 환율로 평가액이 토스와 달라진다
        if (force || now - (f.fx?.got ?: 0.0) > 600) {   // ios: Data/Market.swift:526
            val (code, o, _) = TossFeed.userGet("/api/toss/v1/exchange-rate")   // ios: Data/Market.swift:527
            val rate = if (code == 200) num(jObj(o?.get("result"))?.get("rate")) else null   // ios: Data/Market.swift:528
            if (rate != null && rate > 0) {
                val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"))   // ios: Data/Market.swift:529
                val (c2, o2, _) = TossFeed.userGet("/api/toss/v1/exchange-rate?dateTime=" + enc(iso.format(Instant.ofEpochSecond(Instant.now().epochSecond - 86400))))   // ios: Data/Market.swift:530
                val prev = if (c2 == 200) num(jObj(o2?.get("result"))?.get("rate")) else null   // ios: Data/Market.swift:531
                f.fx = FX(rate = rate, prev = if ((prev ?: 0.0) > 0) prev!! else (f.fx?.prev ?: rate), got = now)   // ios: Data/Market.swift:532
            }
        }
        Store.write(File.serializer(), f, name)   // ios: Data/Market.swift:535
        f   // ios: Data/Market.swift:536
    }
}

/** 매매기준율: 사이트가 하루 한 번 저장하는 data/mar.json ({"USD": {"rate", "date"}}). 웹 평가액과 같은 값 */
object MarFeed {   // ios: Data/Market.swift:541
    @Serializable data class Prev(var rate: Double, var date: String)   // ios: Data/Market.swift:542
    /** prev = 바로 앞 날짜 고시 (새 고시는 전날 오후에 나오지만 토스는 그날 오전 8시 59분부터 쓴다) */
    @Serializable data class File(var rate: Double, var date: String, var got: Double, var prev: Prev? = null)   // ios: Data/Market.swift:544
    const val name = "site_mar.json"   // ios: Data/Market.swift:545
    /** 그 날짜 고시를 쓰기 시작했는지: 한국 시간 그날 오전 8시 59분부터 */
    fun started(date: String): Boolean {   // ios: Data/Market.swift:547
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", posix("en_US_POSIX")).withZone(ZoneId.of("Asia/Seoul"))   // ios: Data/Market.swift:548
        return f.format(Instant.now()) >= date + " 08:59"   // ios: Data/Market.swift:549
    }
    fun cached(): File? = Store.read(File.serializer(), name)   // ios: Data/Market.swift:551

    /** 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값 */
    suspend fun fetch(minAge: Double = 0.0): File? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:554
        val old = cached()   // ios: Data/Market.swift:555
        // 오늘 고시가 아닌 값은 10분만 믿는다 (앱을 새로 깔거나 빌드한 직후 며칠 묵은 값을 30분 넘게 쓰지 않게)
        // 단, 오전 8시 59분 전인데 앞 고시가 없으면 기다리지 않고 바로 받는다 (앞 고시를 써야 하니까)
        if (old != null && (started(old.date) || old.prev != null) && nowSec() - old.got < (if (old.date >= Day.today) minAge else minOf(minAge, 600.0))) return@withContext old   // ios: Data/Market.swift:558
        // 1순위: 서울외국환중개에서 직접 (최신 고시 + 앞 날짜 고시). 못 받을 때만 웹(data/mar.json → 중계 /mar)
        var best: File? = smbs()   // ios: Data/Market.swift:560
        if (best == null && Config.usesCollectedData) {   // 사이트가 모은 자료·중계는 내부 베타만   // ios: Data/Market.swift:567
            val c = URL(Config.data, "mar.json").toString()   // ios: Data/Market.swift:562
            val queryItems = listOf("t" to (nowSec() / 600).toLong().toString()); val u = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // ios: Data/Market.swift:563
            best = get(FeedReq(u, timeout = 10.0))   // ios: Data/Market.swift:564
            // 웹 marFetch 와 같게: 저장소 값이 오늘 고시가 아니면 중계(/mar)에서 한 번 더 (더 새 날짜일 때만 바꾼다)
            val have = maxOf(best?.date ?: "", old?.date ?: "")   // ios: Data/Market.swift:566
            if (have < Day.today) { val x = get(FeedReq(URL(Config.relay, "mar").toString(), timeout = 10.0)); if (x != null && x.date > (best?.date ?: "")) best = x }   // ios: Data/Market.swift:567
        }
        val f = best ?: return@withContext old   // ios: Data/Market.swift:569
        var out = f
        if (old != null && old.date > f.date) out = File(rate = old.rate, date = old.date, got = f.got, prev = old.prev)   // 중계에서 받아 둔 더 새 값은 지킨다   // ios: Data/Market.swift:570
        else if (old != null && old.date < f.date && f.prev == null) f.prev = Prev(rate = old.rate, date = old.date)   // 날짜가 바뀌면 앞 고시를 남긴다   // ios: Data/Market.swift:571
        else if (old != null && f.prev == null) f.prev = old.prev   // ios: Data/Market.swift:572
        Store.write(File.serializer(), out, name)   // ios: Data/Market.swift:573
        out   // ios: Data/Market.swift:574
    }
    /** 서울외국환중개 매매기준율 표 (최근 2주): 마지막 = 최신 고시, 그 앞 = prev. 중계 worker·server.py 와 같은 파싱 */
    internal suspend fun smbs(): File? {   // ios: Data/Market.swift:577
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", posix("en_US_POSIX")).withZone(ZoneId.of("Asia/Seoul"))   // ios: Data/Market.swift:578
        val st = f.format(Instant.now().minusSeconds(14 * 86400L)); val end = f.format(Instant.now())   // ios: Data/Market.swift:579
        val url = "http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_${st}_${end}"   // ios: Data/Market.swift:580
        val req = FeedReq(url, timeout = 8.0)   // ios: Data/Market.swift:581
        req.setValue("Mozilla/5.0", "User-Agent")   // ios: Data/Market.swift:582
        req.setValue("http://www.smbs.biz/ExRate/StdExRate.jsp", "Referer")   // ios: Data/Market.swift:583
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null; if (code != 200) return null   // ios: Data/Market.swift:584
        return smbsParse(String(data, Charsets.ISO_8859_1))   // ios: Data/Market.swift:585
    }
    /** smbs 응답 글자 → File (서버 없이 시험할 수 있게 따로 둔다) */
    internal fun smbsParse(t: String, got: Double = nowSec()): File? {
        val re = Regex("label=['\"]([^'\"]+)['\"][^>]*?value=['\"]([\\d,.]+)['\"]")   // ios: Data/Market.swift:586
        val days = ArrayList<Prev>()   // ios: Data/Market.swift:587
        for (m in re.findAll(t)) {   // ios: Data/Market.swift:588
            val lr = m.groupValues[1]; val vr = m.groupValues[2]   // ios: Data/Market.swift:589
            val rate = vr.replace(",", "").toDoubleOrNull()?.takeIf { it > 500 && it < 5000 } ?: continue   // ios: Data/Market.swift:590
            val n = lr.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }   // ios: Data/Market.swift:591
            if (n.size < 3) continue   // ios: Data/Market.swift:592
            days.add(Prev(rate = rate, date = fmt("%04d-%02d-%02d", if (n[0] < 100) 2000 + n[0] else n[0], n[1], n[2])))   // ios: Data/Market.swift:593
        }
        days.sortBy { it.date }   // ios: Data/Market.swift:595
        val last = days.lastOrNull() ?: return null   // ios: Data/Market.swift:596
        val pv = days.dropLast(1).lastOrNull()   // ios: Data/Market.swift:597
        return File(rate = last.rate, date = last.date, got = got, prev = pv)   // ios: Data/Market.swift:598
    }
    internal suspend fun get(req: FeedReq): File? {   // ios: Data/Market.swift:600
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null   // ios: Data/Market.swift:601
        if (code != 200) return null   // ios: Data/Market.swift:602
        val o = jObj(feedJson(data)) ?: return null   // ios: Data/Market.swift:603
        val u = jObj(o["USD"]) ?: return null; val rate = num(u["rate"])?.takeIf { it > 0 } ?: return null; val date = u["date"] as? String ?: return null   // ios: Data/Market.swift:604
        // 사이트·중계가 앞 날짜 고시도 주면 같이 둔다 (앱을 처음 깔았거나 저녁에 새 고시만 받은 때도 오전 8시 59분 전엔 앞 고시)
        val pv = jObj(o["USD_prev"])?.let { p -> num(p["rate"])?.let { r -> (p["date"] as? String)?.let { Prev(rate = r, date = it) } } }   // ios: Data/Market.swift:606
        return File(rate = rate, date = date, got = nowSec(), prev = pv?.let { if (it.rate > 0 && it.date < date) it else null })   // ios: Data/Market.swift:607
    }
}

/** 토스 일봉(adjusted=false)의 분할 전 날을 사이트(분할 반영) 비로 맞춘다. ios: Data/Market.swift TossSplit */
object TossSplit {
    fun fit(t: YahooSample.Hist, site: YahooSample.Hist?): YahooSample.Hist {
        if (site == null || site.dates.isEmpty()) return t
        val ref = site.dates.zip(site.closes).toMap()
        var f = 1.0
        val out = t.closes.toMutableList()
        for (i in out.indices.reversed()) {
            val c = out[i]
            val s = ref[t.dates[i]]
            if (c > 0 && s != null && s > 0 && kotlin.math.abs(s / c / f - 1) > 0.3) f = s / c
            out[i] = c * f
        }
        return YahooSample.Hist(t.dates, out)
    }
}

/** 내 Alpaca·Longbridge 키로 받은 미국 종목 일봉(flow_alpaca_split.json, flow_longbridge_adj.json)을 지난 종가로도 쓴다.
 *  사이트·토스 값이 없는 종목만, Alpaca 가 먼저 (분할 반영: Alpaca 는 split, Longbridge 는 수정주가) */
object AlpacaHistory {   // ios: Data/Market.swift:673
    fun merged(p: SitePrices.File?): SitePrices.File? {   // ios: Data/Market.swift:674
        var out = p   // ios: Data/Market.swift:675
        for ((on, name) in listOf(AlpacaKeys.on to FlowBars.alpacaName, LongbridgeKeys.on to FlowBars.longbridgeName)) {   // ios: Data/Market.swift:676
            if (!on) continue
            val f = FlowBars.read(name)   // ios: Data/Market.swift:677
            if (f.b.isEmpty()) continue   // ios: Data/Market.swift:678
            val o = out?.copy() ?: SitePrices.File(got = 0.0, updated = "", asked = emptyList(), h = emptyMap())   // ios: Data/Market.swift:679
            val h = o.h.toMutableMap()
            for ((k, s) in f.b) if (h[k] == null && s.d.size >= 30) h[k] = YahooSample.Hist(dates = s.d, closes = s.c)   // ios: Data/Market.swift:680
            o.h = h
            out = o   // ios: Data/Market.swift:681
        }
        return out   // ios: Data/Market.swift:683
    }
}

/** 종목 상세 1일 차트: 내 토스 키로 중계에서 1분봉을 받는다 (토스 봉은 1분·일봉뿐, 1h 는 400 확인).
 *  마지막 거래일 정규장만 (국내 9:00~15:30, 미국 뉴욕 9:30~16:00). 장 끝 시각부터 거꾸로 200개씩 두 번이면 찬다.
 *  같은 종목은 1분 동안 다시 쓴다. 키가 없거나 못 받으면 null (그래프는 예전처럼 전일 종가 → 지금) */
// ios: Data/Market.swift:666
object TossMinutes {
    class Day(val at: List<java.time.Instant>, val closes: List<Double>, val first: String)   // ios: Data/Market.swift:667
    private val cache = mutableMapOf<String, Pair<Long, Day>>()   // ios: Data/Market.swift:668

    // ios: Data/Market.swift:670
    suspend fun day(yahoo: String): Day? {
        val code = TossFeed.code(yahoo); if (!TossKeys.on || code == null) return null   // ios: Data/Market.swift:671
        cache[code]?.let { if (System.currentTimeMillis() - it.first < 60_000) return it.second }   // ios: Data/Market.swift:672
        val kr = yahoo.uppercase().let { it.endsWith(".KS") || it.endsWith(".KQ") }   // ios: Data/Market.swift:673
        val zone = java.time.ZoneId.of(if (kr) "Asia/Seoul" else "America/New_York")   // ios: Data/Market.swift:675
        val oh = if (kr) 9 to 0 else 9 to 30; val ch = if (kr) 15 to 30 else 16 to 0   // ios: Data/Market.swift:676
        // 그 날(장 시간대 기준)의 정규장 시작·끝
        // ios: Data/Market.swift:678
        fun session(d: java.time.Instant): Pair<java.time.Instant, java.time.Instant> {
            val day = d.atZone(zone).toLocalDate()
            return day.atTime(oh.first, oh.second).atZone(zone).toInstant() to day.atTime(ch.first, ch.second).atZone(zone).toInstant()
        }
        val now = java.time.Instant.now(); val today = session(now)   // ios: Data/Market.swift:682
        // 장 전이면 어제 장 끝부터. 주말·휴일이면 받은 마지막 봉의 날로 다시 맞춘다
        var before = if (now < today.first) session(now.minusSeconds(86400)).second else minOf(now, today.second)   // ios: Data/Market.swift:684
        val bars = mutableMapOf<java.time.Instant, Double>(); var win: Pair<java.time.Instant, java.time.Instant>? = null   // ios: Data/Market.swift:687
        for (i in 0 until 4) {   // ios: Data/Market.swift:688
            val b = before.plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
            val (st, o, _) = TossFeed.userGet("/api/toss/v1/candles?symbol=${pctEncode(code)}&interval=1m&adjusted=false&count=200&before=${pctEncode(b)}", 8.0)
            val r = jObj(o?.get("result")); if (st != 200 || r == null) break
            val page = mutableMapOf<java.time.Instant, Double>()
            for (v in jObjs(r["candles"]) ?: emptyList()) {
                val t = v["timestamp"] as? String ?: continue; val cl = num(v["closePrice"])?.takeIf { it > 0 } ?: continue
                val d = runCatching { java.time.OffsetDateTime.parse(t).toInstant() }.getOrNull() ?: continue
                page[d] = cl
            }
            val newest = page.keys.maxOrNull() ?: break; val oldest = page.keys.minOrNull() ?: break
            if (win == null) {
                val w = session(newest); win = w
                // 마지막 봉이 그 날 장 끝 뒤(애프터·주말 앞 금요일 저녁)면 장 끝부터 다시 받는다
                if (newest > w.second.plusSeconds(60)) { before = w.second; continue }   // ios: Data/Market.swift:703
            }
            for ((k, v) in page) bars.putIfAbsent(k, v)
            if (oldest <= win!!.first) break
            before = oldest.minusSeconds(60)
        }
        val w = win ?: return null
        val keep = bars.keys.filter { it > w.first && it <= w.second.plusSeconds(60) }.sorted()   // ios: Data/Market.swift:711
        if (keep.size < 2) return null
        val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
        val day = Day(keep, keep.map { bars[it]!! }, f.format(keep[0]))
        cache[code] = System.currentTimeMillis() to day
        return day
    }
}

/** 종목 상세 1일 차트 (토스 키가 없을 때): 내 Alpaca 키로 미국 종목 1분봉 (IEX, 무료 키). 마지막 거래일 뉴욕 정규장 9:30~16:00 만.
 *  최근 5일을 새것부터 2000개 받으면 장 전·장 뒤 봉을 넘어 마지막 정규장이 다 들어온다. 같은 종목은 1분 동안 다시 쓴다 */
object AlpacaMinutes {   // ios: Data/Market.swift:729
    private val cache = mutableMapOf<String, Pair<Long, TossMinutes.Day>>()   // ios: Data/Market.swift:730

    suspend fun day(yahoo: String): TossMinutes.Day? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:732
        val k = AlpacaKeys.load(); val code = AlpacaFeed.code(yahoo); if (k == null || code == null) return@withContext null   // ios: Data/Market.swift:733
        synchronized(cache) { cache[code] }?.let { if (System.currentTimeMillis() - it.first < 60000) return@withContext it.second }   // ios: Data/Market.swift:734
        val q = listOf("symbols" to code, "timeframe" to "1Min",   // ios: Data/Market.swift:735,736
            "start" to java.time.Instant.now().minusSeconds(5 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),   // ios: Data/Market.swift:737
            "feed" to "iex", "sort" to "desc", "limit" to "2000")   // ios: Data/Market.swift:738
        val url = com.naeilo.app.data.FlowAlpaca.base + "?" + q.joinToString("&") { it.first + "=" + pctEncode(it.second) }
        val r = FeedReq(url, timeout = 8.0)   // ios: Data/Market.swift:739
        r.setValue(k.id, "APCA-API-KEY-ID")   // ios: Data/Market.swift:740
        r.setValue(k.secret, "APCA-API-SECRET-KEY")   // ios: Data/Market.swift:741
        val (st, d) = runCatching { r.send() }.getOrNull() ?: return@withContext null; if (st != 200) return@withContext null   // ios: Data/Market.swift:742
        val arr = jObj(jObj(feedJson(d))?.get("bars"))?.values?.firstOrNull() as? List<*> ?: return@withContext null   // ios: Data/Market.swift:743,744
        val zone = java.time.ZoneId.of("America/New_York")   // ios: Data/Market.swift:745,746,747
        // 봉 시각(시작)이 그 날 9:30 이상 16:00 미만인 것만, 날짜별로
        val byDay = mutableMapOf<java.time.LocalDate, MutableMap<java.time.Instant, Double>>()   // ios: Data/Market.swift:749
        for (v in arr) {   // ios: Data/Market.swift:750
            // ios: Data/Market.swift:751,764,765,766,767 (num 은 공용 core/num)
            val o = jObj(v) ?: continue; val t = o["t"] as? String ?: continue; val cl = num(o["c"])?.takeIf { it > 0 } ?: continue
            val at = runCatching { java.time.Instant.parse(t) }.getOrNull() ?: continue
            val z = at.atZone(zone); val m = z.hour * 60 + z.minute   // ios: Data/Market.swift:752
            if (m < 9 * 60 + 30 || m >= 16 * 60) continue   // ios: Data/Market.swift:753
            byDay.getOrPut(z.toLocalDate()) { mutableMapOf() }[at] = cl   // ios: Data/Market.swift:754
        }
        val bars = byDay.keys.maxOrNull()?.let { byDay[it] }?.takeIf { it.size > 1 } ?: return@withContext null   // ios: Data/Market.swift:756
        val keep = bars.keys.sorted()   // ios: Data/Market.swift:757
        val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())   // ios: Data/Market.swift:758
        val day = TossMinutes.Day(keep, keep.map { bars[it]!! }, f.format(keep[0]))   // ios: Data/Market.swift:759
        synchronized(cache) { cache[code] = System.currentTimeMillis() to day }   // ios: Data/Market.swift:760
        day   // ios: Data/Market.swift:761
    }
}

/** 종목 상세 1일 차트: 토스·Alpaca 가 없으면 내 Longbridge 키로 1분봉 (POST /quote/history-candlesticks, period 1).
 *  정규장만(trade_session 0), 마지막 거래일 400개를 거꾸로 받아 그 날만 남긴다. 같은 종목은 1분 동안 다시 쓴다 */
object LongbridgeMinutes {   // ios: Data/Market.swift:797
    private val cache = mutableMapOf<String, Pair<Long, TossMinutes.Day>>()   // ios: Data/Market.swift:798

    suspend fun day(yahoo: String): TossMinutes.Day? = withContext(Dispatchers.IO) {   // ios: Data/Market.swift:800
        val k = LongbridgeKeys.load(); val code = LongbridgeFeed.code(yahoo); if (k == null || code == null) return@withContext null   // ios: Data/Market.swift:801
        synchronized(cache) { cache[code] }?.let { if (System.currentTimeMillis() - it.first < 60000) return@withContext it.second }   // ios: Data/Market.swift:802
        val body = kotlinx.serialization.json.buildJsonObject {   // ios: Data/Market.swift:803,804
            put("symbol", kotlinx.serialization.json.JsonPrimitive(code)); put("period", kotlinx.serialization.json.JsonPrimitive(1))
            put("adjust_type", kotlinx.serialization.json.JsonPrimitive(0)); put("query_type", kotlinx.serialization.json.JsonPrimitive(1)); put("trade_session", kotlinx.serialization.json.JsonPrimitive(0))
            put("offset_request", kotlinx.serialization.json.buildJsonObject { put("direction", kotlinx.serialization.json.JsonPrimitive(0)); put("date", kotlinx.serialization.json.JsonPrimitive("")); put("count", kotlinx.serialization.json.JsonPrimitive(400)) })
        }
        val (o, e) = LongbridgeFeed.postJson("/quote/history-candlesticks", body, k)   // ios: Data/Market.swift:805
        if (e != null) return@withContext null
        val arr = o?.get("candlesticks") as? List<*> ?: return@withContext null   // ios: Data/Market.swift:806
        val zone = java.time.ZoneId.of("America/New_York")   // ios: Data/Market.swift:807,808
        // 봉 시각이 시작인지 끝인지 문서에 없어 9:30~16:00 양 끝을 다 받는다
        val byDay = mutableMapOf<java.time.LocalDate, MutableMap<java.time.Instant, Double>>()   // ios: Data/Market.swift:810
        for (v in arr) {   // ios: Data/Market.swift:811
            val m = jObj(v) ?: continue; val t = num(m["timestamp"])?.takeIf { it > 0 } ?: continue; val cl = num(m["close"])?.takeIf { it > 0 } ?: continue   // ios: Data/Market.swift:812
            val at = java.time.Instant.ofEpochSecond(t.toLong())   // ios: Data/Market.swift:813
            val z = at.atZone(zone); val mm = z.hour * 60 + z.minute   // ios: Data/Market.swift:814
            if (mm < 9 * 60 + 30 || mm > 16 * 60) continue   // ios: Data/Market.swift:815
            byDay.getOrPut(z.toLocalDate()) { mutableMapOf() }[at] = cl   // ios: Data/Market.swift:816
        }
        val bars = byDay.keys.maxOrNull()?.let { byDay[it] }?.takeIf { it.size > 1 } ?: return@withContext null   // ios: Data/Market.swift:818
        val keep = bars.keys.sorted()   // ios: Data/Market.swift:819
        val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())   // ios: Data/Market.swift:820
        val day = TossMinutes.Day(keep, keep.map { bars[it]!! }, f.format(keep[0]))   // ios: Data/Market.swift:821
        synchronized(cache) { cache[code] = System.currentTimeMillis() to day }   // ios: Data/Market.swift:822
        day   // ios: Data/Market.swift:823
    }
}
