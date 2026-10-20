package com.naeilo.app.pending

import com.naeilo.app.AppModel
import com.naeilo.app.data.Symbol
import kotlinx.serialization.Serializable
import java.time.Instant

// B 단계(모델·저장) 밖 이름의 자리. 컴파일될 만큼만 빈 값·기본값을 둔다.
// 진짜 이식이 오면 그 단계에서 이 파일의 해당 줄을 지운다. Route·GoalWeek 는 저장(Progress) JSON 에 들어가므로 경우 이름은 iOS 와 같게 둔다.

// TODO(GoalRoute): 진짜 이식 때 지움. ios: Data/GoalRoute.swift:7
@Serializable enum class Route { recover, plus, novice }
// TODO(GoalRoute): 진짜 이식 때 지움. ios: Data/GoalRoute.swift:9
@Serializable enum class GoalWeek { inPlan, less, more;
    val factor: Double get() = when (this) { inPlan -> 1.0; less -> 0.5; more -> 1.5 }
}
// TODO(GoalRoute): 진짜 이식 때 지움
class GoalSim
// TODO(GoalRoute): 진짜 이식 때 지움
class GoalStep(val id: String, val inter: Boolean)
// TODO(Recovery): 진짜 이식 때 지움
class Deposit(val prob: Double, val eta: Double)
// TODO(Recovery): 진짜 이식 때 지움
class Plan(val name: String, val wt: Double) { fun prob(k: Double, t: Double): Double = 0.0 }
// TODO(MonteCarlo): 진짜 이식 때 지움
class PathSet
// TODO(Parallel): 진짜 이식 때 지움
class PWorld { companion object { fun load(): PWorld? = null; fun save(p: PWorld?) {} } }
// TODO(Parallel): 진짜 이식 때 지움
class PRace
// TODO(Calm): 진짜 이식 때 지움
class Calm { val type: String? = null; fun save() {}; companion object { fun load(): Calm = Calm() } }

// TODO(Recovery): 진짜 이식 때 지움
enum class MissionRoute { m1, m1r, m2, m2r, m3, m3r, m4, m4r, nx, gp1r, g1, g1r, g3, g3r, gt, gi }
// TODO(SettingsView): 진짜 이식 때 지움
enum class SettingsRoute { alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence }
// TODO(AnalysisView): 진짜 이식 때 지움
enum class AnalysisRoute { forecast, myPath, rateScen, external, dividend, fx, glance, calm }

// TODO(Market): 진짜 이식 때 지움
class Quote(val last: Double, val prevClose: Double, val live: Boolean = false) {
    val change: Double get() = if (prevClose > 0) last / prevClose - 1 else 0.0
}
// TODO(Market): 진짜 이식 때 지움
class Hist(val closes: List<Double>)
// TODO(Market): 진짜 이식 때 지움
class SitePricesFile(val h: Map<String, Hist>)
// TODO(SiteStats): 진짜 이식 때 지움
class SiteStatsFile
// TODO(Market): 진짜 이식 때 지움
class Market {
    val valFx: Double = 1.0
    val marRate: Double? = null
    val fx: Quote = Quote(1.0, 1.0)
    val prices: SitePricesFile? = null
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
    val quotes: Map<String, Quote> = emptyMap()
}
// TODO(PriceHistory): 진짜 이식 때 지움
object PriceHistory { fun of(id: String): Hist? = null }
// TODO(StockSearch): 진짜 이식 때 지움
object KoreaEstimate { const val sumId: String = "" }
// TODO(StockSearch): 진짜 이식 때 지움
class CustomSymbols {
    fun symbol(id: String): Symbol? = null
    val krLog: Any? = null
    val krNow: Double = 0.0
    companion object { val shared = CustomSymbols() }
}
// TODO(SiteStats): 진짜 이식 때 지움
object SiteStats {
    fun key(id: String): String = id
    fun mcKey(id: String): String = id
    fun params(id: String, st: SiteStatsFile?): Triple<Double, Double, Double> = Triple(0.07, 0.11, 0.16)
    fun fx(st: SiteStatsFile?): Pair<Double, Double> = Pair(0.0, 0.1)
    fun corr(a: String, b: String, st: SiteStatsFile?): Double = 0.5
}
// TODO(MonteCarlo): 진짜 이식 때 지움
object MC {
    class Holding(val key: String, val ccy: String, val valueKrw: Double, val price0: Double)
    class Factor(val base: Double, val smooth: Double)
    class Stats
    class Settings
    class Event
    fun addMonths(s: String, n: Int): String = s
    fun dayNum(s: String): Int = 0
    fun blendMu(b: Double, s: Double, a: Double): Double = 0.0
    fun krAt(log: Any?, d: String): Double = 0.0
}
// TODO(Recovery): 진짜 이식 때 지움
class McInputs(val st: MC.Stats, val settings: MC.Settings, val events: List<MC.Event>, val sig: String)
// TODO(ForecastEngine): 진짜 이식 때 지움
class ForecastEngine {
    class Job(val key: String, val holdings: List<MC.Holding>, val stats: MC.Stats, val settings: MC.Settings, val events: List<MC.Event>,
              val start: String, val horizon: String, val goal: Double, val monthly: Double)
    class Sim(val start: String, val monthDates: List<String>, val A: Int, val v0: List<Double>)
    class Run(val key: String, val ev: Sim, val factors: List<MC.Factor>)
    class YearP(val year: Int, val p: Double)
    class View(val cumHit: List<Double>, val V0: Double, val bands: List<List<Double>>, val pGoal: Double, val byYear: List<YearP>)
    val run: Run? = null
    fun ensure(job: Job) {}
    fun view(key: String, lens: String, trust: Double, goal: Double, monthly: Double, withEvents: Boolean): View? = null
    companion object { val shared = ForecastEngine() }
}
// TODO(Sync): 진짜 이식 때 지움
class Sync {
    class Cash(val krw: Double, val usd: Double)
    val siteCash = Cash(0.0, 0.0)
    val isOn = false
    val isDev = false
    suspend fun turnOn(password: String, model: AppModel) {}
    suspend fun turnOnDev(token: String, model: AppModel) {}
    companion object { val shared = Sync(); fun siteTicker(symbol: String): String = symbol }
}
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

// AppModel 확장 (목표·회복 계산). TODO(GoalRoute·Recovery·Calm·Lots): 진짜 이식 때 지움
val AppModel.isGoal: Boolean get() = route != Route.recover
val AppModel.noviceOwn: Boolean get() = false
val AppModel.keyValue: Double get() = 0.0
val AppModel.trackValue: Double get() = 0.0
val AppModel.goalSaved: Double get() = 0.0
val AppModel.goalToDate: Instant get() = Instant.now()
val AppModel.pathStartDate: Instant get() = Instant.now()
val AppModel.goalSteps: List<GoalStep> get() = emptyList()
fun AppModel.switchRoute(r: Route) { route = r }
val AppModel.focusRow: AppModel.Row? get() = null
val AppModel.selectedPlan: Plan get() = Plan("", 0.0)
val AppModel.keepPlan: Plan get() = Plan("", 0.0)
val AppModel.horizonLabel: String get() = ""
fun AppModel.mcInputs(full: Boolean): McInputs? = null
fun AppModel.totalAt(at: Double): Double = total
fun AppModel.calmDrift(): Double? = null
fun AppModel.calmTrade(sym: Symbol, buy: Boolean, avgBefore: Double, driftBefore: Double?, fresh: Boolean = false) {}
fun AppModel.Companion.wonK(m: Double): String = ""
