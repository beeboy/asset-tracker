package com.naeilo.app.pending

import com.naeilo.app.AppModel
import com.naeilo.app.data.Symbol
import kotlinx.serialization.Serializable
import java.time.Instant

// B 단계(모델·저장) 밖 이름의 자리. 컴파일될 만큼만 빈 값·기본값을 둔다.
// 진짜 이식이 오면 그 단계에서 이 파일의 해당 줄을 지운다. Route·GoalWeek 는 저장(Progress) JSON 에 들어가므로 경우 이름은 iOS 와 같게 둔다.

// TODO(SettingsView): 진짜 이식 때 지움
enum class SettingsRoute { alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence }
// TODO(AnalysisView): 진짜 이식 때 지움
enum class AnalysisRoute { forecast, myPath, rateScen, external, dividend, fx, glance, calm }

// TODO(Market): 진짜 이식 때 지움
class Quote(val last: Double, val prevClose: Double, val live: Boolean = false) {
    val change: Double get() = if (prevClose > 0) last / prevClose - 1 else 0.0
}
// TODO(SitePrices): 진짜 이식 때 지움
object SitePrices {
    class File(val got: Double, val updated: String, val asked: List<String>, val h: Map<String, YahooSample.Hist>)
    suspend fun fetch(keys: Set<String>, old: File?, minAge: Double): File? = null
}
// TODO(SiteStats): 진짜 이식 때 지움 (Swift 도 SiteStats.File = MC.Stats)
typealias SiteStatsFile = com.naeilo.app.core.calc.MC.Stats
// TODO(Market): 진짜 이식 때 지움
class Market {
    val valFx: Double = 1.0
    val marRate: Double? = null
    val fx: Quote = Quote(1.0, 1.0)
    var prices: SitePrices.File? = null
    val stats: SiteStatsFile? = null
    fun quote(s: Symbol): Quote = Quote(s.close, s.close)
    companion object { val shared = Market() }
}
// TODO(Market): 진짜 이식 때 지움
val Symbol.quote: Quote get() = Market.shared.quote(this)
// TODO(Market): 진짜 이식 때 지움
val Symbol.last: Double get() = quote.last
// TODO(YahooSample): 진짜 이식 때 지움
object YahooSample {
    class Quote(val prevClose: Double)
    class Hist(val dates: List<String>, val closes: List<Double>)
    val quotes: Map<String, Quote> = emptyMap()
    val bundled: Map<String, Hist> = emptyMap()
    val keyFor: Map<String, String> = mapOf("QQQ" to "QQQ", "AAPL" to "AAPL", "NVDA" to "NVDA", "SPY" to "SPY", "FX" to "KRW=X")
}
// TODO(YahooSample·Lots): 진짜 이식 때 지움. ios: Data/Lots.swift:56
fun YahooSample.Hist.on(d: String): Double? = null
// TODO(PriceHistory): 진짜 이식 때 지움
object PriceHistory { fun of(id: String): YahooSample.Hist? = null }
// TODO(StockSearch): 진짜 이식 때 지움
object KoreaEstimate { const val sumId: String = "" }
// TODO(StockSearch): 진짜 이식 때 지움
class CustomSymbols {
    fun symbol(id: String): Symbol? = null
    val krLog: List<com.naeilo.app.core.calc.MC.KrDay> = emptyList()
    val krNow: Double = 0.0
    companion object { val shared = CustomSymbols(); fun yahoo(s: Symbol): String = s.id }
}
// TODO(SiteStats): 진짜 이식 때 지움
object SiteStats {
    fun key(id: String): String = id
    fun mcKey(id: String): String = id
    fun params(id: String, st: SiteStatsFile?): Triple<Double, Double, Double> = Triple(0.07, 0.11, 0.16)
    fun fx(st: SiteStatsFile?): Pair<Double, Double> = Pair(0.0, 0.1)
    fun corr(a: String, b: String, st: SiteStatsFile?): Double = 0.5
}
// TODO(Sync): 진짜 이식 때 지움
class Sync {
    class Cash(val krw: Double, val usd: Double)
    val siteCash = Cash(0.0, 0.0)
    val isOn = false
    val isDev = false
    suspend fun turnOn(password: String, model: AppModel) {}
    suspend fun turnOnDev(token: String, model: AppModel) {}
    val siteForecast: SiteForecast get() = SiteForecast(null, null)
    companion object {
        val shared = Sync(); fun siteTicker(symbol: String): String = symbol
        fun appId(ticker: String): String? = null
        fun num(v: Any?): Double = (v as? Number)?.toDouble() ?: 0.0
    }
}
// TODO(Sync): 진짜 이식 때 지움
class SiteForecast(val events: List<com.naeilo.app.core.calc.MC.Event>?, val model: com.naeilo.app.core.calc.MC.Settings?)
// TODO(ExtraStats): 진짜 이식 때 지움
class ExtraStats { fun augment(st: com.naeilo.app.core.calc.MC.Stats, keys: List<String>): com.naeilo.app.core.calc.MC.Stats = st; companion object { val shared = ExtraStats() } }
// TODO(Mission3ResultView): 진짜 이식 때 지움
object Mission3ResultView { fun pp(d: Double): String = "" }
// TODO(Support): 진짜 이식 때 지움
class Support { val dayN: Int = 1; val testBuild: Boolean = false; companion object { val shared = Support() } }
// TODO(Story): 진짜 이식 때 지움
class Story { enum class Kind { side }; fun isDone(k: Kind, i: Int): Boolean = false; companion object { val shared = Story() } }
// TODO(Lines): 진짜 이식 때 지움
object Lines { fun pick(key: String, vars: Map<String, String> = emptyMap()): String? = null }
// TODO(Remind): 진짜 이식 때 지움
class Remind {
    class Week(val lo: Double, val hi: Double, val pos: String?)
    class Log(val weeks: Map<String, Week>, val guessHits: Int?)
    val log = Log(emptyMap(), null)
    companion object {
        val shared = Remind()
        fun week(at: Instant): String = ""
        fun endedWeeks(w: Map<String, Week>): List<String> = emptyList()
    }
}
