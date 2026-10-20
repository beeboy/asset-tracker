package com.naeilo.app.core.calc

import com.naeilo.app.core.cformat
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.getDouble
import com.naeilo.app.core.putDouble
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Sample
import com.naeilo.app.pending.*
import com.naeilo.app.net.*
import kotlinx.serialization.Serializable
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// Swift max/min 과 같은 비교식 (같으면 뒤 값: −0·NaN 까지 같게)
private fun scMax(x: Double, y: Double): Double = if (y >= x) y else x
private fun scMin(x: Double, y: Double): Double = if (y < x) y else x

// 배당·환율 계산 자료 (naeilo.com/data/calc.json). 사이트가 매일 웹과 같은 식으로 만든다 (tools/site_calc.mjs):
// 배당 = 수정종가/종가 비율이 바뀌는 날(배당락)과 금액, 환율 = web/model.js indicators(원/달러).
// 외부 요인 민감도는 stats.json(betas). 앱의 외부 요인, 배당·세금, 환율 영향 화면은 이 값에 웹 식(web/app.js renderCash · fxInfo)을 그대로 써서 웹과 같은 숫자를 낸다
// ios: Data/SiteCalc.swift:7,8
class SiteCalc {
    // ios: Data/SiteCalc.swift:11
    @Serializable data class Ev(var d: String, var a: Double)
    // ios: Data/SiteCalc.swift:12
    @Serializable data class Div(var ccy: String, var price: Double? = null, var events: List<Ev>)
    // ios: Data/SiteCalc.swift:13,14,15,16,17
    @Serializable data class FX(
        var date: String, var close: Double, var kalman_level: Double,
        var dev_from_kalman: Double? = null, var ret_1m: Double? = null, var ret_3m: Double? = null, var ret_1y: Double? = null,
        var vol_ewma: Double? = null, var vol_hist: Double? = null, var slope_ann: Double? = null, var trend: String? = null, var hi1: Double? = null, var lo1: Double? = null,
        var dates: List<String>, var closes: List<Double>, var level: List<Double>,
    )
    // ios: Data/SiteCalc.swift:19
    @Serializable data class File(var date: String, var divs: Map<String, Div>, var fx: FX? = null)

    // ios: Data/SiteCalc.swift:21
    var file: File? by mutableStateOf(SiteCalc.load())

    /** 사이트는 하루 한 번 만든다. 6시간 안에 받았으면 다시 받지 않는다 (네트워크는 FeedReq 가 Dispatchers.IO 에서) */
    // ios: Data/SiteCalc.swift:29
    suspend fun refresh() {
        // ios: Data/SiteCalc.swift:30
        val key = "calcGot"; val now = System.currentTimeMillis() / 1000.0
        // ios: Data/SiteCalc.swift:31
        if (now - Store.defaults.getDouble(key) < 6 * 3600) return
        // ios: Data/SiteCalc.swift:32,33
        val c = URL(Config.data, "calc.json").toString()
        val queryItems = listOf("t" to (now / 3600).toLong().toString()); val url = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }
        // ios: Data/SiteCalc.swift:34,35
        val req = FeedReq(url, timeout = 10.0)   // .reloadIgnoringLocalCacheData: FeedReq 는 캐시를 쓰지 않는다
        req.setValue("naeilo-ios/0.1", "User-Agent")
        // ios: Data/SiteCalc.swift:36,37
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return
        if (code != 200) return; val f = decode(data) ?: return
        // ios: Data/SiteCalc.swift:38
        Store.writeData(data, name)
        // ios: Data/SiteCalc.swift:39
        Store.defaults.edit().putDouble(key, now).apply()
        // ios: Data/SiteCalc.swift:40
        Handler(Looper.getMainLooper()).post { file = f }
    }

    // ios: Data/SiteCalc.swift:47
    fun div(id: String): Div? = file?.divs?.get(key(id))
    /** 요인 민감도 (웹 Model.factorBetas, 사이트 stats.json): mkt = S&P500 로그 변화당, rate = 10년 금리 1bp 당, oil·gold·cmdty = 그 지표 로그 변화당 (시장 움직임 뺀 값)
     *  한국 종목·국내 주식 합계는 전망과 같은 키(KR~기준 ETF, 합계는 KR~EWY. 웹과 같은 수익률)로 찾는다 */
    // ios: Data/SiteCalc.swift:50
    fun beta(factor: String, id: String): Double? = Market.shared.stats?.betas?.get(factor)?.get(SiteStats.mcKey(id))
    /** 민감도 신뢰도 (같은 방향 비율 agree 등). 외부 요인 화면용 */
    // ios: Data/SiteCalc.swift:52
    fun betaStat(factor: String, id: String): MC.BetaStat? = Market.shared.stats?.betaStat?.get(factor)?.get(SiteStats.mcKey(id))
    /** 요인 보통 한 달 변화 (fx = 원/달러 포함) */
    // ios: Data/SiteCalc.swift:54
    fun factorMonth(factor: String): MC.FactorMove? = Market.shared.stats?.factors?.get(factor)

    /** 일정별 발표일 '보통 움직임' 한 칸 (Swift 이름 붙은 튜플 (factor, sd) 자리) */
    data class EventMove(val factor: String, val sd: Double)
    // ios: Data/SiteCalc.swift:78
    data class DivOut(var n: Int, var y1: Double, var y3: Double, var gr: Double, var yld: Double, var byMonth: Map<String, Double>)
    // ios: Data/SiteCalc.swift:103
    data class Replay(val `when`: String, val name: String, val mv: Map<String, Double>)
    /** replayMove 결과 (Swift (total, fx) 튜플 자리) */
    data class ReplayMove(val total: Double, val fx: Double)

    // ios: Data/SiteCalc.swift:9
    companion object {
        val shared = SiteCalc()

        // ios: Data/SiteCalc.swift:23
        const val name = "site_calc.json"
        // ios: Data/SiteCalc.swift:24
        private fun decode(d: ByteArray?): File? = d?.let { runCatching { NJson.decodeFromString(File.serializer(), it.decodeToString()) }.getOrNull() }
        // ios: Data/SiteCalc.swift:25,26
        fun load(): File? =
            decode(Store.readData(name)) ?: decode(Defaults.asset("calc", "json")?.encodeToByteArray())

        // ios: Data/SiteCalc.swift:43,44,45
        fun key(id: String): String {
            YahooSample.keyFor[id]?.let { k -> return k }
            return Sample.symbol(id)?.let { CustomSymbols.yahoo(it) } ?: id
        }

        // MARK: 웹과 같은 식

        // ios: Data/SiteCalc.swift:58
        const val wht = 0.15; const val cgt = 0.22; const val cgtDed = 2_500_000.0; const val krDivTax = 0.154

        /** web/model.js addMonths: 월을 더하고, 그 달에 없는 날이면 그 달 마지막 날 */
        // ios: Data/SiteCalc.swift:61
        fun addMonths(s: String, n: Int): String {
            // ios: Data/SiteCalc.swift:62
            val p = s.split("-").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            // ios: Data/SiteCalc.swift:63
            if (p.size != 3) return s
            // ios: Data/SiteCalc.swift:64
            val m0 = p[0] * 12 + (p[1] - 1) + n; val y = floor(m0.toDouble() / 12).toInt(); val mo = m0 - y * 12 + 1
            // ios: Data/SiteCalc.swift:65,66 (그레고리력·UTC 의 그 달 1일이 든 달의 날수)
            val utc = ZoneId.of("UTC")
            val last = runCatching { LocalDate.of(y, mo, 1).atStartOfDay(utc).toLocalDate().lengthOfMonth() }.getOrNull() ?: 28
            // ios: Data/SiteCalc.swift:67
            return cformat("%04d-%02d-%02d", y, mo, minOf(p[2], last))
        }
        // ios: Data/SiteCalc.swift:69,70
        fun today(d: Instant = Instant.now()): String =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.forLanguageTag("en_US_POSIX".replace('_', '-'))).withZone(ZoneId.systemDefault()).format(d)
        /** web/app.js divGrowth: 지난 12개월 합 ÷ 24~36개월 전 12개월 합 의 제곱근 − 1 (0~15%) */
        // ios: Data/SiteCalc.swift:73
        fun divGrowth(ev: List<Ev>, today: String): Double {
            val t = today
            // ios: Data/SiteCalc.swift:74
            val a = addMonths(t, -12); val b = addMonths(t, -24); val c = addMonths(t, -36)
            // ios: Data/SiteCalc.swift:75
            val y1 = ev.filter { it.d > a }.fold(0.0) { s, e -> s + e.a }; val y3 = ev.filter { it.d > c && it.d <= b }.fold(0.0) { s, e -> s + e.a }
            // ios: Data/SiteCalc.swift:76
            return if (y1 > 0 && y3 > 0) scMax(0.0, scMin(0.15, sqrt(y1 / y3) - 1)) else 0.0
        }
        /** web/app.js renderCash 의 종목 하나: 지난 12개월 배당이 1·2·3년 뒤 같은 달에 (1+증가율)^(n−1) 로 다시 온다고 본 세후 원화 */
        // ios: Data/SiteCalc.swift:80
        fun project(ev: List<Ev>, shares: Double, fx: Double, krw: Boolean, price: Double, today: String = SiteCalc.today()): DivOut? {
            val t = today
            // ios: Data/SiteCalc.swift:81
            val yAgo = addMonths(t, -12); val ds = ev.filter { it.d > yAgo }
            // ios: Data/SiteCalc.swift:82
            if (ds.isEmpty()) return null
            // ios: Data/SiteCalc.swift:83
            val months = (1..36).map { addMonths(t, it).take(7) }.toSet(); val tax = if (krw) krDivTax else wht; val gr = divGrowth(ev, today = t)
            // ios: Data/SiteCalc.swift:84
            val by = LinkedHashMap<String, Double>(); var y1 = 0.0; var y3 = 0.0
            // ios: Data/SiteCalc.swift:85
            for (x in ds) {
                // ios: Data/SiteCalc.swift:86
                for (n in 1..3) {
                    // ios: Data/SiteCalc.swift:87
                    val m = addMonths(x.d, 12 * n).take(7); val v = x.a * shares * fx * (1 - tax) * (1 + gr).pow((n - 1).toDouble())
                    // ios: Data/SiteCalc.swift:88
                    if (months.contains(m)) { by[m] = (by[m] ?: 0.0) + v; y3 += v; if (n == 1) y1 += v }
                }
            }
            // ios: Data/SiteCalc.swift:91
            return DivOut(n = ds.size, y1 = y1, y3 = y3, gr = gr, yld = if (price > 0) ds.fold(0.0) { s, e -> s + e.a } / price else 0.0, byMonth = by)
        }
        /** web/app.js renderSens 의 한 종목: 요인이 shock 만큼 움직일 때 가격 변화율. rate 는 shock 이 bp, 나머지는 % (−10 = −10%) */
        // ios: Data/SiteCalc.swift:94,95
        fun factorMove(factor: String, beta: Double, shock: Double): Double {
            val b = beta
            return exp(b * (if (factor == "rate") shock else ln(1 + shock / 100))) - 1
        }
        /** 일정별 발표일 '보통 움직임' (web/model.js EXT_EVENTS 의 factor·sd 와 같은 고정 가정값). 단위 % */
        // ios: Data/SiteCalc.swift:98,99,100
        val eventMoves: Map<String, EventMove> = mapOf(
            "x_fomc" to EventMove("mkt", 1.0), "x_cpi" to EventMove("mkt", 0.8), "x_jobs" to EventMove("mkt", 0.6), "x_bok" to EventMove("fx", 0.4),
            "x_opec" to EventMove("oil", 4.0), "x_mid" to EventMove("mkt", 1.5), "x_witch" to EventMove("mkt", 0.5),
        )
        /** 과거 재생 기간 (web/model.js REPLAYS 와 같은 값). 단위: mkt·fx·oil·gold %, rate %p */
        // ios: Data/SiteCalc.swift:104
        val replays: List<Replay> = listOf(
            Replay(`when` = "2020.3", name = "코로나", mv = mapOf("mkt" to -34.0, "rate" to -0.8, "fx" to 8.0, "oil" to -56.0, "gold" to -3.0)),   // ios: Data/SiteCalc.swift:105
            Replay(`when` = "2022", name = "금리 급등", mv = mapOf("mkt" to -25.0, "rate" to 2.3, "fx" to 21.0, "oil" to 15.0, "gold" to -7.0)),   // ios: Data/SiteCalc.swift:106
            Replay(`when` = "2024.8", name = "엔캐리 청산", mv = mapOf("mkt" to -8.5, "rate" to -0.4, "fx" to -1.5, "oil" to -10.0, "gold" to -2.5)),   // ios: Data/SiteCalc.swift:107
        )
        /** web/model.js replayMove 와 같은 식: 원화 평가액 변화율과 그중 환율 몫. β 를 모르면 시장 1, 그 외 0 */
        // ios: Data/SiteCalc.swift:110
        fun replayMove(rp: Replay, usd: Boolean, beta: (String) -> Double?): ReplayMove {
            // ios: Data/SiteCalc.swift:111
            var lg = 0.0
            // ios: Data/SiteCalc.swift:112
            for (k in listOf("mkt", "rate", "oil", "gold")) {
                // ios: Data/SiteCalc.swift:113
                val b = beta(k) ?: (if (k == "mkt") 1.0 else 0.0); val x = rp.mv[k] ?: 0.0
                // ios: Data/SiteCalc.swift:114
                lg += b * (if (k == "rate") x * 100 else ln(1 + x / 100))
            }
            // ios: Data/SiteCalc.swift:116
            val p = exp(lg) - 1; val f = if (usd) (rp.mv["fx"] ?: 0.0) / 100 else 0.0
            // ios: Data/SiteCalc.swift:117
            return ReplayMove((1 + p) * (1 + f) - 1, (1 + p) * f)
        }
        /** 해외주식 양도세 (웹과 같은 식): 이익에서 공제 250만원을 빼고 22% */
        // ios: Data/SiteCalc.swift:120
        fun capGainTax(gain: Double): Double = scMax(0.0, gain - cgtDed) * cgt

        /** web/app.js fxInfo: 원/달러 앞날 분위수 now·exp((μ − σ²/2)t + zσ√t). μ·σ 는 웹 전망 모형의 환율 요인 값 (stats.json) */
        // ios: Data/SiteCalc.swift:123,124
        fun fxAt(now: Double, t: Double, z: Double, mu: Double, vol: Double): Double =
            now * exp((mu - vol * vol / 2) * t + z * vol * sqrt(t))
    }
}
