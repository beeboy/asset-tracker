package com.naeilo.app.core.calc

import com.naeilo.app.ui.board.Mission3ResultView
import com.naeilo.app.core.cformat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.data.Currency
import com.naeilo.app.data.WeekGate
import com.naeilo.app.data.fixStarts
import com.naeilo.app.data.monthGate
import com.naeilo.app.net.*
import com.naeilo.app.ui.Theme
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// 목표 달성 루트 (시안 목표·플러스): 목표 정하기 → 구성 고르기 → (플러스) 비중 조정 세금 → 앱 시작 3단계 → 3개월 인터미션.
// 금액 단위는 만원. 확률은 전망과 같은 몬테카를로 경로(웹 model.js 와 같은 계산, 사이트 통계·설정·사건)로 센다.
// 구성 비교의 지수·업종 묶음은 대표 ETF 시세로 (AppModel.proxyKeys). 경로 계산이 끝나기 전에는 닫힌 식 값을 잠깐 보인다

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
/** Swift Int.formatted() (천 단위 쉼표) */
private fun Int.fmtInt(): String = cformat("%,d", this)
/** Swift Double.formatted() (천 단위 쉼표, 필요한 소수만) */
private fun Double.fmtDbl(): String = DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.ROOT)).format(this)
/** b.timeIntervalSince(a) (초) */
private fun secs(a: Instant, b: Instant): Double = Duration.between(a, b).toNanos() / 1e9

@Serializable
enum class Route { recover, plus, novice }   // ios: Data/GoalRoute.swift:7

@Serializable
enum class GoalWeek { inPlan, less, more;   // ios: Data/GoalRoute.swift:9
    val label: String get() = when (this) { inPlan -> "계획대로"; less -> "덜 넣음"; more -> "더 넣음" }   // ios: Data/GoalRoute.swift:10
    val factor: Double get() = when (this) { inPlan -> 1.0; less -> 0.5; more -> 1.5 }   // ios: Data/GoalRoute.swift:11
    val color: Color @Composable get() = when (this) { inPlan -> Theme.green; less -> Theme.orange; more -> Theme.blue }   // ios: Data/GoalRoute.swift:12
}

data class GoalMix(   // ios: Data/GoalRoute.swift:15
    val id: String,   // ios: Data/GoalRoute.swift:16
    val name: String,   // ios: Data/GoalRoute.swift:17
    val tag: String,   // ios: Data/GoalRoute.swift:18
    val desc: String,   // ios: Data/GoalRoute.swift:19
    val comp: Map<String, Double>,     // ios: Data/GoalRoute.swift:20 I 지수 · G 성장 지속 · B 버팀목 · C 현금 (대표 ETF: AppModel.proxyKeys)
    val basket: Basket?,   // ios: Data/GoalRoute.swift:21
    val own: Boolean = false,   // ios: Data/GoalRoute.swift:22
)

data class GoalSim(   // ios: Data/GoalRoute.swift:25
    val mix: GoalMix,   // ios: Data/GoalRoute.swift:26
    val prob: Double,               // ios: Data/GoalRoute.swift:27 목표일(또는 그 햇수 뒤)에 목표 이상일 확률 (웹 '목표 확률'과 같은 셈)
    val etaMonths: Double,          // ios: Data/GoalRoute.swift:28 보통의 경우 처음 닿는 때 (개월, 경로 기간 안에 없으면 무한대)
    val med: List<Double>, val lo: List<Double>, val hi: List<Double>, val principal: List<Double>,   // ios: Data/GoalRoute.swift:29 해마다 (만원)
    val badYear: Double,            // ios: Data/GoalRoute.swift:30 한 해 손익 하위 5% (적립 없이)
    val capYears: Int,              // ios: Data/GoalRoute.swift:31 경로 기간 (햇수)
    val etaLo: Double = 0.0,          // ios: Data/GoalRoute.swift:32 처음 닿는 때 10% (개월, 10번 중 빠른 1번)
    val etaHi: Double = Double.POSITIVE_INFINITY,  // ios: Data/GoalRoute.swift:33 처음 닿는 때 90% (개월, 경로 기간 안에 없으면 무한대)
) {
    val etaText: String get() = AppModel.eta(etaMonths, cap = capYears)   // ios: Data/GoalRoute.swift:34
}

// ios: Data/GoalRoute.swift:37 (extension AppModel → 아래 확장들)

fun AppModel.Companion.wonK(m: Double): String {   // ios: Data/GoalRoute.swift:38
    if (en) return krwC(if (m < 0.5) 0.0 else m * 1e4)   // ios: Data/GoalRoute.swift:39
    if (m < 0.5) return "0원"   // ios: Data/GoalRoute.swift:40
    if (m >= 10000) return ((m / 1000).swRound() / 10).fmtDbl() + "억"   // ios: Data/GoalRoute.swift:41
    return m.swRound().toInt().fmtInt() + "만"   // ios: Data/GoalRoute.swift:42
}
/** cap: 경로 기간 (햇수). 그 안에 닿지 않으면 "N년 넘게" */
fun AppModel.Companion.eta(mo: Double, cap: Int = 40): String {   // ios: Data/GoalRoute.swift:45
    if (!mo.isFinite()) return "${cap}년 넘게"   // ios: Data/GoalRoute.swift:46
    if (mo <= 0) return "지금"   // ios: Data/GoalRoute.swift:47
    val m = mo.toInt(); val y = m / 12; val r = m % 12   // ios: Data/GoalRoute.swift:48
    return (if (y > 0) "${y}년 " else "") + (if (r > 0) "${r}개월 뒤" else "뒤")   // ios: Data/GoalRoute.swift:49
}

val AppModel.isGoal: Boolean get() = route != Route.recover   // ios: Data/GoalRoute.swift:52
/** 시작 전 루트에 내가 넣은 종목(샘플 아님)이 있으면 모아 둔 투자금 대신 그 평가액으로 본다 */
val AppModel.noviceOwn: Boolean get() = route == Route.novice && !isSample && total > 0   // ios: Data/GoalRoute.swift:54
/** 출발 금액 (만원): 플러스는 지금 평가액, 시작 전은 모아 둔 투자금 (넣은 종목이 있으면 평가액) */
val AppModel.goalStart: Double get() = if (route == Route.plus || noviceOwn) total / 1e4 else gA   // ios: Data/GoalRoute.swift:56
/** 분석 화면의 기준선 (원): 회복 = 들어간 돈(본전), 목표 = 목표 금액 */
val AppModel.keyValue: Double get() = if (isGoal) gK * 1e4 else cost   // ios: Data/GoalRoute.swift:59
val AppModel.keyName: String get() = if (isGoal) "목표" else "본전"   // ios: Data/GoalRoute.swift:60
/** 시작 전: 모아 둔 돈 + 인터미션에 넣은 돈. 목표 루트는 웹 총자산처럼 현금도 더한다 */
val AppModel.trackValue: Double get() = if (route == Route.novice) goalSaved * 1e4 else total + cashKrw   // ios: Data/GoalRoute.swift:62

// 목표 시작일·목표일: 사이트(goal.start_date·goal.date)와 같은 값을 쓴다 (기기 동기화로 양쪽이 맞춘다)
val AppModel.goalFromDate: Instant get() = gFrom?.let { Day.date(it) } ?: goalStartAt?.let { Instant.ofEpochMilli((it * 1000).toLong()) } ?: Instant.now()   // ios: Data/GoalRoute.swift:65
val AppModel.goalToDate: Instant get() = gTo?.let { Day.date(it) } ?: goalFromDate.atZone(ZoneId.systemDefault()).plusYears(gY.toLong()).toInstant()   // ios: Data/GoalRoute.swift:66
/** 목표 시작일부터 목표일까지 (년) */
val AppModel.goalSpan: Double get() = max(1.0 / 12, secs(goalFromDate, goalToDate) / (365.25 * 86400))   // ios: Data/GoalRoute.swift:68
val AppModel.goalToText: String get() = DateTimeFormatter.ofPattern("yyyy.M.d", AppModel.dateLocale).withZone(ZoneId.systemDefault()).format(goalToDate)   // ios: Data/GoalRoute.swift:69

/** 사이트 목표(goal)를 그대로 받는다: 금액·목표일·시작일·매달 넣는 돈 (원 → 만원) */
fun AppModel.takeSiteGoal(g: Map<String, Any?>) {   // ios: Data/GoalRoute.swift:72
    val amt = Sync.num(g["amount"])   // ios: Data/GoalRoute.swift:73
    val ds = g["date"] as? String; val to = ds?.let { Day.date(it) }; if (!(amt > 0) || ds == null || to == null) return   // ios: Data/GoalRoute.swift:74
    val fs = (g["start_date"] as? String)?.let { if (Day.date(it) != null) it else null } ?: Day.today   // ios: Data/GoalRoute.swift:75
    val from = Day.date(fs) ?: Instant.now()   // ios: Data/GoalRoute.swift:76
    gK = amt / 1e4   // ios: Data/GoalRoute.swift:77
    gY = max(1, (secs(from, to) / (365.25 * 86400)).swRound().toInt())   // ios: Data/GoalRoute.swift:78 미션 계산용 햇수 (gTo 보다 먼저)
    gTo = ds; gFrom = fs   // ios: Data/GoalRoute.swift:79
    gM = max(0.0, Sync.num(g["monthly_contribution"]) / 1e4)   // ios: Data/GoalRoute.swift:80
    if (isGoal) monthly = gM   // ios: Data/GoalRoute.swift:81 3년 전망의 '만약에 매달'도 같은 적립액으로
}
/** 사이트로 보낼 목표 (원) */
fun AppModel.siteGoal(base: Map<String, Any?>?): Map<String, Any?> {   // ios: Data/GoalRoute.swift:84
    val g = (base ?: emptyMap()).toMutableMap()   // ios: Data/GoalRoute.swift:85
    g["amount"] = (gK * 1e4).swRound()   // ios: Data/GoalRoute.swift:86
    g["date"] = Day.str(goalToDate)   // ios: Data/GoalRoute.swift:87
    g["start_date"] = Day.str(goalFromDate)   // ios: Data/GoalRoute.swift:88
    g["monthly_contribution"] = (gM * 1e4).swRound()   // ios: Data/GoalRoute.swift:89
    return g   // ios: Data/GoalRoute.swift:90
}

private val goalMixList: List<GoalMix> = listOf(   // ios: Data/GoalRoute.swift:93
    GoalMix(id = "index", name = "지수 중심", tag = "단순하게", desc = "미국 지수 80 · 현금 20", comp = mapOf("I" to 0.8, "C" to 0.2), basket = null),   // ios: Data/GoalRoute.swift:94
    GoalMix(id = "grow", name = "성장 더하기", tag = "더 빨리, 더 흔들림", desc = "지수 60 · 성장 업종 30 · 현금 10", comp = mapOf("I" to 0.6, "G" to 0.3, "C" to 0.1), basket = Basket.G),   // ios: Data/GoalRoute.swift:95
    GoalMix(id = "steady", name = "버팀목 더하기", tag = "덜 흔들림", desc = "지수 50 · 버팀목 업종 30 · 채권·현금 20", comp = mapOf("I" to 0.5, "B" to 0.3, "C" to 0.2), basket = Basket.B),   // ios: Data/GoalRoute.swift:96
)
val AppModel.Companion.mixes: List<GoalMix> get() = goalMixList   // ios: Data/GoalRoute.swift:93
val AppModel.mineMix: GoalMix get() =   // ios: Data/GoalRoute.swift:98
    GoalMix(id = "mine", name = "지금 내 구성", tag = "그대로", desc = "${focusName} ${(focusWeight * 100).swRound().toInt()}" + (if (restRows.isEmpty()) "" else " · ${restName} ${((1 - focusWeight) * 100).swRound().toInt()}"),   // ios: Data/GoalRoute.swift:99
            comp = emptyMap(), basket = null, own = true)   // ios: Data/GoalRoute.swift:100
/** 내 길의 '지금 구성': 시작 전 루트는 지수 중심 (neededMonthly 와 같다) */
val AppModel.nowMix: GoalMix get() = if (route == Route.novice) AppModel.mixes[0] else mineMix   // ios: Data/GoalRoute.swift:103
/** 처음 닿는 때를 셀 긴 기간 (햇수): 목표일 뒤에 닿는 경우도 보이게 */
val AppModel.etaYears: Int get() = max(10, gY * 2)   // ios: Data/GoalRoute.swift:105
/** 목표 확률을 쉬운 말로 (반반은 40~60%, 30%를 반반이라 부르지 않게) */
fun AppModel.Companion.verdict(p: Double): String = if (p >= 0.6) "높음" else if (p >= 0.4) "반반" else "낮음"   // ios: Data/GoalRoute.swift:107
/** 오늘부터 mo 개월 뒤를 연도 소수로 (2031.25 = 2031년 4월 무렵) */
fun AppModel.Companion.yearAt(mo: Double): Double {   // ios: Data/GoalRoute.swift:109
    val c = LocalDate.now(); val y: Int? = c.year; val m: Int? = c.monthValue   // ios: Data/GoalRoute.swift:110
    return (y ?: 2026).toDouble() + (((m ?: 1) - 1).toDouble() + mo) / 12   // ios: Data/GoalRoute.swift:111
}
/** 오늘부터 mo 개월 뒤 "2031.4" (기간 안에 없으면 "N년 넘게") */
fun AppModel.Companion.whenText(mo: Double, cap: Int): String {   // ios: Data/GoalRoute.swift:114
    if (!mo.isFinite()) return "${cap}년 넘게"   // ios: Data/GoalRoute.swift:115
    if (!(mo > 0)) return "지금"   // ios: Data/GoalRoute.swift:116
    val d = ZonedDateTime.now().plusMonths(mo.swRound().toLong())   // ios: Data/GoalRoute.swift:117
    val y: Int? = d.year; val m: Int? = d.monthValue   // ios: Data/GoalRoute.swift:118
    return "${y ?: 0}.${m ?: 0}"   // ios: Data/GoalRoute.swift:119
}
val AppModel.goalMixes: List<GoalMix> get() = if (route == Route.novice) AppModel.mixes else listOf(mineMix) + AppModel.mixes   // ios: Data/GoalRoute.swift:121
val AppModel.selectedMix: GoalMix get() { val ms = goalMixes; return ms.firstOrNull { it.id == gMix } ?: ms[0] }   // ios: Data/GoalRoute.swift:122

/** 구성의 종목 (사이트 티커·통화·비중). 내 구성 = 지금 종목. 시작 전은 묶음만, 플러스는 비중 1위 종목 10%까지 + 묶음 (shift 와 같은 비중) */
fun AppModel.mixParts(mx: GoalMix): List<Part> {   // ios: Data/GoalRoute.swift:125
    if (mx.own) return mineParts   // ios: Data/GoalRoute.swift:126
    val out = mutableListOf<Part>()   // ios: Data/GoalRoute.swift:127
    var rest = 1.0   // ios: Data/GoalRoute.swift:128
    val f = focusRow; if (route != Route.novice && f != null) {   // ios: Data/GoalRoute.swift:129
        val tT = min(focusWeight, 0.10)   // ios: Data/GoalRoute.swift:130
        out.add(Part(key = SiteStats.mcKey(f.id), ccy = if (f.sym.currency == Currency.usd) "USD" else "KRW", w = tT))   // ios: Data/GoalRoute.swift:131
        rest = 1 - tT   // ios: Data/GoalRoute.swift:132
    }
    for (k in listOf("I", "G", "B", "C")) { val x = mx.comp[k]; if (x != null) out.add(Part(key = proxy(k), ccy = "USD", w = x * rest)) }   // ios: Data/GoalRoute.swift:134
    return out   // ios: Data/GoalRoute.swift:135
}
/** 지금 목표의 경로 끝 = 목표일 (전망과 같은 기간이라 내 구성은 전망 경로를 그대로 쓴다) */
val AppModel.goalHorizon: String get() = Day.str(goalToDate)   // ios: Data/GoalRoute.swift:138
/** 다른 햇수(목표 고르기 카드·자동 고르기·도착 시점)는 10년(그보다 길면 그 햇수) 경로 하나에서 센다 */
fun AppModel.longHorizon(y: Int): String = MC.addMonths(Day.today, 12 * max(10, y))   // ios: Data/GoalRoute.swift:140

/** k·years·start 를 주면 지금 목표 대신 그 값으로 (목표 자동 고르기에서 씀, cache: false 면 캐시에 넣지 않는다).
 *  years 를 주면 오늘부터 그 햇수, 안 주면 목표일까지. parts 를 주면 구성 대신 그 종목·비중으로 (내 길 비중 바꿔 비교).
 *  경로(PathSet)는 뒤에서 계산되고 여기서는 그 결과를 세기만 한다. 같은 입력은 simCache 로 다시 세지 않는다 */
fun AppModel.simulate(mx: GoalMix, monthly: Double? = null, k: Double? = null, years: Int? = null, start: Double? = null, cache: Boolean = true, parts: List<Part>? = null): GoalSim {   // ios: Data/GoalRoute.swift:145
    val mo = monthly ?: gM; val yrs = years ?: gY; val K = k ?: gK; val A = start ?: goalStart   // ios: Data/GoalRoute.swift:146
    val pts = parts ?: mixParts(mx)   // ios: Data/GoalRoute.swift:147
    val ps = paths(pts, horizon = if (years == null) goalHorizon else longHorizon(yrs))   // ios: Data/GoalRoute.swift:148
        ?: return closedSim(mx, parts = pts, monthly = mo, k = K, years = yrs, start = A, cache = cache)   // ios: Data/GoalRoute.swift:149
    val ck = "x|${ps.key}|${mo}|${yrs}|${K}|${A}|${years == null}"   // ios: Data/GoalRoute.swift:151
    if (cache) simCache[ck]?.let { c -> return c.copy(mix = mx) }   // ios: Data/GoalRoute.swift:152
    val V = A * 1e4; val Mo = mo * 1e4; val Kw = K * 1e4; val M = ps.M   // ios: Data/GoalRoute.swift:153
    val v = ps.values(V, Mo)   // ios: Data/GoalRoute.swift:154
    // 그 햇수 뒤 칸: 그 달 적립은 빼고 센다 (목표일 당일에는 넣지 않는다, 웹과 같게)
    val kEnd = if (years == null) M - 1 else ps.k(12 * yrs)   // ios: Data/GoalRoute.swift:156
    val prob = ps.share(v, kEnd, if (kEnd > 0 && kEnd < M - 1) Mo else 0.0, Kw)   // ios: Data/GoalRoute.swift:157
    val hits = if (V >= Kw) null else ps.firstHits(v, M - 1) { _ -> Kw }   // ios: Data/GoalRoute.swift:158
    val eta = if (hits == null) 0.0 else ps.medianMonths(hits)   // ios: Data/GoalRoute.swift:159
    val etaLo = if (hits == null) 0.0 else ps.quantileMonths(hits, 0.1); val etaHi = if (hits == null) 0.0 else ps.quantileMonths(hits, 0.9)   // ios: Data/GoalRoute.swift:160
    val ks = (0..yrs).map { y -> if (y == 0) 0 else min(kEnd, ps.k(12 * y)) }   // ios: Data/GoalRoute.swift:161
    val q = { f: Double -> ks.map { kk -> if (kk == 0) A else ps.quantile(v, kk, f) / 1e4 } }   // ios: Data/GoalRoute.swift:162
    val b = ps.bands(max(V, 1e6)); val k12 = ps.k(12)   // ios: Data/GoalRoute.swift:163
    val r = GoalSim(mix = mx, prob = prob, etaMonths = eta, med = q(0.5), lo = q(0.05), hi = q(0.95),   // ios: Data/GoalRoute.swift:164
                    principal = (0..yrs).map { A + mo * 12 * it.toDouble() }, badYear = b[0][k12] / max(V, 1e6) - 1,   // ios: Data/GoalRoute.swift:165
                    capYears = ((ps.tM.lastOrNull() ?: 12.0) / 12 + 0.5).toInt(), etaLo = etaLo, etaHi = etaHi)   // ios: Data/GoalRoute.swift:166
    if (cache) {   // ios: Data/GoalRoute.swift:167
        if (simCache.size > 64) simCache.clear()   // ios: Data/GoalRoute.swift:168
        simCache[ck] = r   // ios: Data/GoalRoute.swift:169
    }
    return r   // ios: Data/GoalRoute.swift:171
}

/** 경로 계산이 끝나기 전 잠깐: 닫힌 식 μ·σ(사이트 통계)로 500번 월 단위 */
private fun AppModel.closedSim(mx: GoalMix, parts: List<Part>, monthly: Double, k: Double, years: Int, start: Double, cache: Boolean): GoalSim {   // ios: Data/GoalRoute.swift:175
    val mo = monthly; val K = k; val gY = years; val A = start
    val c = closedParams(parts)   // ios: Data/GoalRoute.swift:176
    val ck = "c|${c.mu}|${c.sigma}|${mo}|${gY}|${K}|${A}"   // ios: Data/GoalRoute.swift:177
    if (cache) simCache[ck]?.let { x -> return x.copy(mix = mx) }   // ios: Data/GoalRoute.swift:178
    var seed = 12345L   // ios: Data/GoalRoute.swift:179
    fun rnd(): Double { seed = (seed * 1103515245L + 12345L) % 2147483648L; return (seed.toDouble() + 0.5) / 2147483648.0 }   // ios: Data/GoalRoute.swift:180
    val NP = 500; val H = min(40, gY * 2 + 5) * 12   // ios: Data/GoalRoute.swift:181
    val mm = (c.mu - c.sigma * c.sigma / 2) / 12; val ms = c.sigma / sqrt(12.0)   // ios: Data/GoalRoute.swift:182
    val byYear = List(gY + 1) { ArrayList<Double>() }   // ios: Data/GoalRoute.swift:183
    val hits = ArrayList<Double>()   // ios: Data/GoalRoute.swift:184
    for (n in 0 until NP) {   // ios: Data/GoalRoute.swift:185
        var v = A; var hit = Double.POSITIVE_INFINITY   // ios: Data/GoalRoute.swift:186
        byYear[0].add(v)   // ios: Data/GoalRoute.swift:187
        for (kk in 1..H) {   // ios: Data/GoalRoute.swift:188
            val z = sqrt(-2 * ln(rnd())) * cos(6.283185307 * rnd())   // ios: Data/GoalRoute.swift:189
            v = v * exp(mm + ms * z) + mo   // ios: Data/GoalRoute.swift:190
            if (hit == Double.POSITIVE_INFINITY && v >= K) hit = kk.toDouble()   // ios: Data/GoalRoute.swift:191
            if (kk % 12 == 0 && kk / 12 <= gY) byYear[kk / 12].add(v)   // ios: Data/GoalRoute.swift:192
            if (hit != Double.POSITIVE_INFINITY && kk >= gY * 12) break   // ios: Data/GoalRoute.swift:193
        }
        hits.add(hit)   // ios: Data/GoalRoute.swift:195
    }
    fun q(a: List<Double>, f: Double): Double { val s = a.sorted(); return if (s.isEmpty()) A else s[min(s.size - 1, (f * s.size.toDouble()).toInt())] }   // ios: Data/GoalRoute.swift:197
    val r = GoalSim(mix = mx, prob = byYear[gY].count { it >= K }.toDouble() / max(1, byYear[gY].size).toDouble(), etaMonths = q(hits, 0.5),   // ios: Data/GoalRoute.swift:198
                    med = byYear.map { q(it, 0.5) }, lo = byYear.map { q(it, 0.05) }, hi = byYear.map { q(it, 0.95) },   // ios: Data/GoalRoute.swift:199
                    principal = (0..gY).map { A + mo * 12 * it.toDouble() },   // ios: Data/GoalRoute.swift:200
                    badYear = exp(c.mu - c.sigma * c.sigma / 2 - 1.645 * c.sigma) - 1, capYears = H / 12,   // ios: Data/GoalRoute.swift:201
                    etaLo = q(hits, 0.1), etaHi = q(hits, 0.9))   // ios: Data/GoalRoute.swift:202
    if (cache) {   // ios: Data/GoalRoute.swift:203
        if (simCache.size > 64) simCache.clear()   // ios: Data/GoalRoute.swift:204
        simCache[ck] = r   // ios: Data/GoalRoute.swift:205
    }
    return r   // ios: Data/GoalRoute.swift:207
}

/** 목표에 맞추려면 매달 넣을 돈 (만원): 목표일(years 를 주면 그 햇수 뒤)에 목표 이상인 경우가 절반이 되는 적립 (웹 req50 과 같은 셈) */
fun AppModel.neededMonthly(k: Double? = null, years: Int? = null): Double {   // ios: Data/GoalRoute.swift:211
    val mx = if (route == Route.novice) AppModel.mixes[0] else mineMix; val K = k ?: gK; val yrs = years ?: gY   // ios: Data/GoalRoute.swift:212
    val parts = mixParts(mx)   // ios: Data/GoalRoute.swift:213
    val ps = paths(parts, horizon = if (years == null) goalHorizon else longHorizon(yrs))   // ios: Data/GoalRoute.swift:214
    if (ps != null) {
        return ps.req50(goalStart * 1e4, K * 1e4, if (years == null) ps.M - 1 else ps.k(12 * yrs)) / 1e4   // ios: Data/GoalRoute.swift:215
    }
    val c = closedParams(parts)   // ios: Data/GoalRoute.swift:217
    val gmm = exp((c.mu - c.sigma * c.sigma / 2) / 12); val fv = gmm.pow((yrs * 12).toDouble())   // ios: Data/GoalRoute.swift:218
    return max(0.0, (K - goalStart * fv) * (gmm - 1) / (fv - 1))   // ios: Data/GoalRoute.swift:219
}
/** 지금 start(만원)에서 매달 monthly(만원)씩 넣을 때 보통의 경우 목표에 처음 닿는 개월 */
fun AppModel.monthsTo(from: Double, monthly: Double): Double {   // ios: Data/GoalRoute.swift:222
    val start = from
    if (start >= gK) return 0.0   // ios: Data/GoalRoute.swift:223
    val parts = mixParts(if (route == Route.novice) AppModel.mixes[0] else mineMix)   // ios: Data/GoalRoute.swift:224
    val ps = paths(parts, horizon = longHorizon(gY))   // ios: Data/GoalRoute.swift:225
    if (ps != null) {
        val v = ps.values(start * 1e4, monthly * 1e4); val K = gK * 1e4   // ios: Data/GoalRoute.swift:226
        return ps.medianMonths(ps.firstHits(v, ps.M - 1) { _ -> K })   // ios: Data/GoalRoute.swift:227
    }
    val c = closedParams(parts); val gmm = exp((c.mu - c.sigma * c.sigma / 2) / 12)   // ios: Data/GoalRoute.swift:229
    var v = start   // ios: Data/GoalRoute.swift:230
    for (k in 1..480) { v = v * gmm + monthly; if (v >= gK) return k.toDouble() }   // ios: Data/GoalRoute.swift:231
    return Double.POSITIVE_INFINITY   // ios: Data/GoalRoute.swift:232
}

/** 페이스 판정: 목표일까지 남은 개월과 지금 금액·적립으로 처음 닿는 개월(보통의 경우)을 비교한다. ±1개월은 예정대로 */
sealed class GoalPace {   // ios: Data/GoalRoute.swift:236
    object onTrack : GoalPace()
    data class ahead(val n: Int) : GoalPace()
    data class behind(val n: Int) : GoalPace()
    object unreachable : GoalPace()
}
fun AppModel.goalPace(): GoalPace {   // ios: Data/GoalRoute.swift:237
    val left = secs(Instant.now(), goalToDate) / (365.25 * 86400) * 12   // ios: Data/GoalRoute.swift:238
    val eta = monthsTo(from = trackValue / 1e4, monthly = gM)   // ios: Data/GoalRoute.swift:239
    if (!eta.isFinite()) return GoalPace.unreachable   // ios: Data/GoalRoute.swift:240
    val d = left - eta   // ios: Data/GoalRoute.swift:241
    if (abs(d) <= 1) return GoalPace.onTrack   // ios: Data/GoalRoute.swift:242
    return if (d > 0) GoalPace.ahead(d.swRound().toInt()) else GoalPace.behind((-d).swRound().toInt())   // ios: Data/GoalRoute.swift:243
}
/** 홈 카드 페이스 문장 */
fun AppModel.Companion.paceText(p: GoalPace): String {   // ios: Data/GoalRoute.swift:246
    return when (p) {   // ios: Data/GoalRoute.swift:247
        GoalPace.onTrack -> "계획대로"   // ios: Data/GoalRoute.swift:248
        is GoalPace.ahead -> if (p.n > 6) "많이 빨라요" else "조금 빨라요"   // ios: Data/GoalRoute.swift:249
        is GoalPace.behind -> if (p.n > 6) "많이 늦어요" else "조금 늦어요"   // ios: Data/GoalRoute.swift:250
        GoalPace.unreachable -> "지금 속도론 어려워요"   // ios: Data/GoalRoute.swift:251
    }
}

/** 보기 좋은 금액 (만원): 앞 두 자리만 남긴다 (5억 4천 → 5.4억, 3,460만 → 3,500만) */
fun AppModel.Companion.round2(x: Double): Double {   // ios: Data/GoalRoute.swift:256
    if (!(x >= 100)) return 100.0   // ios: Data/GoalRoute.swift:257
    val u = 10.0.pow(floor(log10(x)) - 1)   // ios: Data/GoalRoute.swift:258
    return (x / u).swRound() * u   // ios: Data/GoalRoute.swift:259
}
/** 목표 금액 자동 고르기: 지금 금액에서 매달 monthly(만원)씩 넣을 때 years 뒤에 목표 이상일 확률이 50% 근처인 금액 (만원)
 *  = 그 햇수 뒤 평가액의 가운데 값 (전망과 같은 경로). 경로 계산 전에는 닫힌 식으로 확률 50% 를 찾는다 */
fun AppModel.autoGoal(years: Int, monthly: Double? = null, from: Double? = null): Double = autoGoalX(years = years, monthly = monthly, from = from).first   // ios: Data/GoalRoute.swift:263
fun AppModel.autoGoalX(years: Int, monthly: Double? = null, from: Double? = null): Pair<Double, Boolean> {   // ios: Data/GoalRoute.swift:264
    val A = from ?: goalStart; val mo = max(0.0, monthly ?: gM); val mx = if (route == Route.novice) AppModel.mixes[0] else mineMix   // ios: Data/GoalRoute.swift:265
    if (!(A + mo > 0)) return Pair(1000.0, true)   // ios: Data/GoalRoute.swift:266
    val parts = mixParts(mx)   // ios: Data/GoalRoute.swift:267
    val ps = paths(parts, horizon = longHorizon(years))   // ios: Data/GoalRoute.swift:268
    if (ps != null) {
        val ck = "x|${ps.key}|${mo}|${years}|${A}"   // ios: Data/GoalRoute.swift:269
        autoCache[ck]?.let { return Pair(it, true) }   // ios: Data/GoalRoute.swift:270
        val v = ps.values(A * 1e4, mo * 1e4); val kEnd = ps.k(12 * years)   // ios: Data/GoalRoute.swift:271
        val K = ps.quantile(v, kEnd, 0.5, if (kEnd > 0 && kEnd < ps.M - 1) mo * 1e4 else 0.0) / 1e4   // ios: Data/GoalRoute.swift:272
        val r = max(AppModel.round2(A + 1), AppModel.round2(K))   // ios: Data/GoalRoute.swift:273
        if (autoCache.size > 64) autoCache.clear()   // ios: Data/GoalRoute.swift:274
        autoCache[ck] = r   // ios: Data/GoalRoute.swift:275
        return Pair(r, true)   // ios: Data/GoalRoute.swift:276
    }
    val c = closedParams(parts); val ck = "c|${c.mu}|${c.sigma}|${mo}|${years}|${A}"   // ios: Data/GoalRoute.swift:278
    autoCache[ck]?.let { return Pair(it, false) }   // ios: Data/GoalRoute.swift:279
    fun p(K: Double): Double = simulate(mx, monthly = mo, k = K, years = years, start = A, cache = false).prob   // ios: Data/GoalRoute.swift:280
    var lo = max(1.0, A); var hi = max(lo * 2, A + mo * 12 * years.toDouble() * 2)   // ios: Data/GoalRoute.swift:281
    var n = 0   // ios: Data/GoalRoute.swift:282
    while (p(hi) > 0.5 && n < 20) { lo = hi; hi *= 2; n += 1 }   // ios: Data/GoalRoute.swift:283
    for (i in 0 until 14) {   // ios: Data/GoalRoute.swift:284
        val mid = (lo + hi) / 2   // ios: Data/GoalRoute.swift:285
        if (p(mid) > 0.5) lo = mid else hi = mid   // ios: Data/GoalRoute.swift:286
    }
    val r = max(AppModel.round2(A + 1), AppModel.round2((lo + hi) / 2))   // ios: Data/GoalRoute.swift:288
    if (autoCache.size > 64) autoCache.clear()   // ios: Data/GoalRoute.swift:289
    autoCache[ck] = r   // ios: Data/GoalRoute.swift:290
    return Pair(r, false)   // ios: Data/GoalRoute.swift:291
}
/** 목표를 자동으로 정한다. 경로가 아직이면 닫힌 식 값으로 두고, 경로가 나오면 (그사이 목표를 고치지 않았으면) 다시 고른다 */
fun AppModel.setAutoGoal(years: Int, monthly: Double? = null, from: Double? = null) {   // ios: Data/GoalRoute.swift:294
    val (v, exact) = autoGoalX(years = years, monthly = monthly, from = from)   // ios: Data/GoalRoute.swift:295
    gK = v   // ios: Data/GoalRoute.swift:296
    autoPending = if (exact) null else AppModel.AutoPending(years, monthly, from, v)   // ios: Data/GoalRoute.swift:297
    if (!exact) { val me = WeakReference(this); PathRuns.shared.onRun = { me.get()?.refreshAutoGoal() } }   // ios: Data/GoalRoute.swift:298 [weak self]
}
fun AppModel.refreshAutoGoal() {   // ios: Data/GoalRoute.swift:300
    val a = autoPending ?: return   // ios: Data/GoalRoute.swift:301
    if (gK != a.value) { autoPending = null; return }   // ios: Data/GoalRoute.swift:302
    val (v, exact) = autoGoalX(years = a.years, monthly = a.monthly, from = a.from)   // ios: Data/GoalRoute.swift:303
    if (exact) { gK = v; autoPending = null }   // ios: Data/GoalRoute.swift:304
}

data class GoalPreset(val k: Double, val y: Int, val name: String, val mean: String, val need: String) { val id: String get() = name }   // ios: Data/GoalRoute.swift:307
private data class PresetRaw(val k: Double, val y: Int, val name: String, val x: Int?)
val AppModel.goalPresets: List<GoalPreset> get() {   // ios: Data/GoalRoute.swift:308
    val A = goalStart   // ios: Data/GoalRoute.swift:309
    fun meaning(k: Double): String {   // ios: Data/GoalRoute.swift:310
        val a = AppModel.wonK(k * 0.07).replace("원", ""); val b = AppModel.wonK(k * 0.04 / 12).replace("원", "")   // Data/GoalRoute.swift:311 (검사 예외: 보간 속 따옴표)
        return if (k <= 1000) "첫 목표 · 적립 습관" else if (k <= 10000) "연 7%면 해마다 +${a}" else "연 4% 꺼내면 월 ${b}"
    }
    // 플러스: 기간마다 '매달 넣는 돈으로 닿을 확률 50% 근처' 금액 (배수 프리셋 대신, 2026-10-11 결정)
    if (route != Route.novice) {   // ios: Data/GoalRoute.swift:314
        return listOf(1, 3, 5, 10).map { y ->   // ios: Data/GoalRoute.swift:315
            val k = autoGoal(years = y)   // ios: Data/GoalRoute.swift:316
            GoalPreset(k = k, y = y, name = AppModel.wonK(k).replace("원", ""),   // ios: Data/GoalRoute.swift:317
                       mean = "",   // ios: Data/GoalRoute.swift:318 '적립 없이 확률 50%' 는 카드 위에 한 번만
                       need = if (A > 0) "×" + cformat("%.1f", k / A) else "")   // Data/GoalRoute.swift:319 (검사 예외: 보간 속 따옴표)
        }
    }
    val raw: List<PresetRaw> = listOf(PresetRaw(1000.0, 3, "첫 1,000만", null), PresetRaw(10000.0, 5, "1억", null), PresetRaw(30000.0, 10, "3억", null), PresetRaw(100000.0, 20, "10억", null))   // ios: Data/GoalRoute.swift:322
    return raw.map { (k, y, name, x) ->   // ios: Data/GoalRoute.swift:323
        val nm = neededMonthly(k = k, years = y); val cagr = if (A > 0) (k / A).pow(1 / y.toDouble()) - 1 else 0.0   // ios: Data/GoalRoute.swift:324
        GoalPreset(k = k, y = y, name = x?.let { "${name} (${it}배)" } ?: name,   // ios: Data/GoalRoute.swift:325
                   mean = if (x != null) "적립 없이 연 +${(cagr * 100).swRound().toInt()}%" else meaning(k),   // ios: Data/GoalRoute.swift:326
                   need = if (nm > 0) "월 " + AppModel.wonK(nm).replace("원", "") else "이미 충분")   // ios: Data/GoalRoute.swift:327
    }
}

// 미션 목록 (시작 전 / 플러스)
data class GoalStep(val id: String, val num: String, val title: String, val reward: String, val to: MissionRoute, val inter: Boolean)   // ios: Data/GoalRoute.swift:332
private data class StepRaw(val id: String, val title: String, val reward: String, val to: MissionRoute, val inter: Boolean)
val AppModel.goalSteps: List<GoalStep> get() {   // ios: Data/GoalRoute.swift:333
    val list: List<StepRaw> = if (route == Route.novice) listOf(   // ios: Data/GoalRoute.swift:334
        StepRaw("goal", "목표 정하기", "목표까지 경로", MissionRoute.g1, false), StepRaw("mix", "투자 구성 고르기", "구성 비교", MissionRoute.g3, false),   // ios: Data/GoalRoute.swift:335
        StepRaw("link", "앱 시작 3단계", "위젯 전부", MissionRoute.nx, false), StepRaw("inter", "처음 석 달 체크인", "캐릭터 위젯 3개", MissionRoute.gi, true),   // ios: Data/GoalRoute.swift:336
    ) else listOf(   // ios: Data/GoalRoute.swift:337
        StepRaw("hold", "종목과 산 가격 넣기", "평가액·수익률", MissionRoute.m1, false), StepRaw("goal", "목표 정하기", "목표까지 경로", MissionRoute.g1, false),   // ios: Data/GoalRoute.swift:338
        StepRaw("mix", "다른 방식과 비교", "구성 비교", MissionRoute.g3, false), StepRaw("tax", "세금 알아보기", "절세 순서", MissionRoute.gt, false),   // ios: Data/GoalRoute.swift:339
        StepRaw("link", "앱 시작 3단계", "위젯 전부", MissionRoute.nx, false), StepRaw("inter", "처음 석 달 체크인", "캐릭터 위젯 3개", MissionRoute.gi, true),   // ios: Data/GoalRoute.swift:340
    )
    return list.mapIndexed { i, x -> GoalStep(id = x.id, num = if (x.inter) "인터미션" else "${i + 1}", title = x.title, reward = x.reward, to = x.to, inter = x.inter) }   // ios: Data/GoalRoute.swift:342
}
val AppModel.goalTotal: Int get() = goalSteps.count { !it.inter }   // ios: Data/GoalRoute.swift:344
fun AppModel.goalNo(id: String): String = goalSteps.firstOrNull { it.id == id }?.num ?: ""   // ios: Data/GoalRoute.swift:345
fun AppModel.goalAvailable(s: GoalStep): Boolean {   // ios: Data/GoalRoute.swift:346
    val steps = goalSteps; val i = steps.indexOfFirst { it.id == s.id }; if (i < 0) return false   // ios: Data/GoalRoute.swift:347
    // 인터미션은 앞 미션을 모두 끝낸 뒤 (회복 루트에서 넘어와 '앱 시작 3단계'가 이미 끝난 경우에도)
    if (s.inter) return steps.filter { !it.inter }.all { gDone.contains(it.id) }   // ios: Data/GoalRoute.swift:349
    return i == 0 || gDone.contains(steps[i - 1].id)   // ios: Data/GoalRoute.swift:350
}

// 고른 구성으로 바꿀 비중 (플러스: 지금 비중 1위 종목·나머지에서 출발, 시작 전: 매달 나눠 넣기). 금액 대신 비율·%p로만 보인다
// 나머지 종목은 '지수' 칸으로 옮겨 가는 것으로 본다 (시안의 QQQ 자리)
data class Shift(var rows: List<Row> = emptyList(), var line: String = "", var note: String = "", var weights: Map<String, Double> = emptyMap(), var sell: Double = 0.0) {   // ios: Data/GoalRoute.swift:355
    /** Swift 튜플 (이름, 지금→목표, 바뀌는 %p, 늘어남) 자리 */
    data class Row(val name: String, val now: String, val change: String, val up: Boolean)
}
fun AppModel.shift(mx: GoalMix): Shift {   // ios: Data/GoalRoute.swift:356
    val w = focusWeight; val fn = focusName; val rn = restName   // ios: Data/GoalRoute.swift:357
    val restIndex = restRows.all { it.sym.sector == "지수" }   // ios: Data/GoalRoute.swift:358
    val label = mapOf("T" to fn, "I" to (if (restIndex || w >= 1) "지수" else "${rn}→지수"), "G" to "성장 지속", "B" to "버팀목", "C" to (if (mx.id == "steady") "채권·현금" else "현금"))   // ios: Data/GoalRoute.swift:359
    val ind = focusIndustry; val hot = ind?.let { Basket.of(pe = it.pe, pe10 = it.pe10, growth = it.growth) == "H" } ?: false   // ios: Data/GoalRoute.swift:360
    if (mx.own) {   // ios: Data/GoalRoute.swift:361
        val sec = ind?.let { it.ko + (if (hot) "·과열" else "") } ?: ""   // ios: Data/GoalRoute.swift:362
        val rs = mutableListOf(Shift.Row(fn, AppModel.pct(w), "그대로", true))   // ios: Data/GoalRoute.swift:363
        if (restRows.isNotEmpty()) rs.add(Shift.Row(rn, AppModel.pct(1 - w), "그대로", true))   // ios: Data/GoalRoute.swift:364
        val secSp = if (sec.isEmpty()) "" else sec + " "; val indKo = ind?.ko ?: ""   // Data/GoalRoute.swift:366,367 (검사 예외: 보간 속 따옴표)
        return Shift(rows = rs,   // ios: Data/GoalRoute.swift:365
                     line = "${fn} ${secSp}${AppModel.pct(w)}" + (if (restRows.isEmpty()) "" else " · ${rn} ${AppModel.pct(1 - w)}"),   // Data/GoalRoute.swift:366
                     note = (if (hot) "${fn} 업종(${indKo}) 과열 · " else "") + "바꿀 비중은 다른 구성에서", weights = mapOf("T" to w, "I" to 1 - w))   // Data/GoalRoute.swift:367
    }
    val keys = listOf("T", "I", "G", "B", "C")   // ios: Data/GoalRoute.swift:369
    if (route == Route.novice) {   // ios: Data/GoalRoute.swift:370
        return Shift(rows = keys.mapNotNull { k -> mx.comp[k]?.let { Shift.Row(label[k]!!, "적립의", AppModel.pct(it), true) } },   // ios: Data/GoalRoute.swift:371
                     line = mx.basket?.let { b -> "${b.title}: " + b.industries.take(3).joinToString(" · ") { it.ko } + " 외 ${b.industries.size - 3}개" } ?: "",   // ios: Data/GoalRoute.swift:372
                     weights = mx.comp)   // ios: Data/GoalRoute.swift:373
    }
    // 한 종목은 세 묶음에 들지 않아 10%까지만 둔다 (시안 T_CAP)
    val tT = min(w, 0.10)   // ios: Data/GoalRoute.swift:376
    val tgt = mx.comp.mapValues { it.value * (1 - tT) }.toMutableMap(); tgt["T"] = tT   // ios: Data/GoalRoute.swift:377
    val now = mapOf("T" to w, "I" to 1 - w); val tot = goalStart   // ios: Data/GoalRoute.swift:378
    val used = keys.filter { (now[it] ?: 0.0) > 0 || (tgt[it] ?: 0.0) > 0 }   // ios: Data/GoalRoute.swift:379
    val rows = used.map { k ->   // ios: Data/GoalRoute.swift:380
        val a = tgt[k] ?: 0.0; val b = now[k] ?: 0.0   // ios: Data/GoalRoute.swift:381
        Shift.Row(label[k]!!, "${(b * 100).swRound().toInt()}→${AppModel.pct(a)}", Mission3ResultView.pp(a - b), a >= b)   // ios: Data/GoalRoute.swift:382
    }
    val short = mapOf("T" to fn, "I" to label["I"]!!, "G" to "성장", "B" to "버팀목", "C" to "현금")   // ios: Data/GoalRoute.swift:384
    val line = used.map { k -> short[k]!! + " " + (if ((now[k] ?: 0.0) > 0) "${((now[k] ?: 0.0) * 100).swRound().toInt()}→${AppModel.pct(tgt[k] ?: 0.0)}" else "+" + AppModel.pct(tgt[k] ?: 0.0)) }.joinToString(" · ")   // ios: Data/GoalRoute.swift:385
    var note = if (hot) "과열 업종 한 종목 최대 10%" else "한 종목 최대 10%"   // ios: Data/GoalRoute.swift:386
    if (tT < w && gM > 0 && tot > 0) {   // ios: Data/GoalRoute.swift:387
        val mo = ceil((w * tot / tT - tot) / gM)   // ios: Data/GoalRoute.swift:388
        val etaS = AppModel.eta(mo).replace(" 뒤", ""); note += " · 적립만으로 약 ${etaS} · 팔면 세금 (세금 화면)"   // Data/GoalRoute.swift:389 (검사 예외: 보간 속 따옴표)
    }
    return Shift(rows = rows, line = line, note = note, weights = tgt, sell = max(0.0, w - tT))   // ios: Data/GoalRoute.swift:391
}

// 3개월 인터미션
/** Swift 튜플 (task, whereText, reward) 자리 */
data class MonthStep(val task: String, val whereText: String, val reward: String)
private val goalMonthStepList: List<MonthStep> = listOf(   // ios: Data/GoalRoute.swift:395
    MonthStep("앱에서 이번 달 적립 기록하기", "앱", "세리"), MonthStep("앱에서 내 종목 수량·평균 단가 맞춰 보기", "앱", "시오"), MonthStep("앱에서 배당·세금 요약 보기", "앱", "모두"),   // ios: Data/GoalRoute.swift:396
)
val AppModel.Companion.goalMonthSteps: List<MonthStep> get() = goalMonthStepList   // ios: Data/GoalRoute.swift:395
/** 체크인: 시작한 날부터 30일마다 한 달씩 (서버 시각, Me monthGate). 회복 루트 주차와 같은 방식 */
fun AppModel.confirmGoalMonth() {   // ios: Data/GoalRoute.swift:399
    val c = gWeekCur; if (c == null || gWeeks.size >= 3 || monthGate != WeekGate.open) return   // ios: Data/GoalRoute.swift:400
    gWeeks = gWeeks + c; gWeekCur = null   // ios: Data/GoalRoute.swift:401
    if (gWeeks.size == 3) gDone = gDone + "inter"   // ios: Data/GoalRoute.swift:402
}
/** 지난 달 체크인은 다음 달이 열리기 전까지 바꿀 수 있다 */
val AppModel.monthEditable: Boolean get() = gWeeks.isNotEmpty() && gWeeks.size < 3 && monthGate != WeekGate.open   // ios: Data/GoalRoute.swift:405
fun AppModel.editLastMonth(p: GoalWeek) { if (monthEditable) gWeeks = gWeeks.toMutableList().also { it[it.size - 1] = p } }   // ios: Data/GoalRoute.swift:406
/** 넣은 종목이 있으면 그 평가액 (적립도 종목 수량으로 들어가니 따로 더하지 않는다) */
val AppModel.goalSaved: Double get() = if (noviceOwn) total / 1e4 else gA + (gWeeks + listOfNotNull(gWeekCur)).fold(0.0) { a, x -> a + gM * x.factor }   // ios: Data/GoalRoute.swift:408

fun AppModel.switchRoute(r: Route) {   // ios: Data/GoalRoute.swift:410
    route = r; boardPath = emptyList(); analysisPath = emptyList()   // ios: Data/GoalRoute.swift:411
    goalStartAt = null; gInterStartAt = null; fixStarts()     // ios: Data/GoalRoute.swift:412 목표 루트 4주는 루트를 고른 날부터 (내 캐릭터 1차 조건)
    when (r) {   // ios: Data/GoalRoute.swift:413
        Route.recover -> {}   // ios: Data/GoalRoute.swift:414
        Route.plus -> {   // ios: Data/GoalRoute.swift:415
            // 기본 목표: 3년 안에 닿을 확률 50% 근처 (매달 넣는 돈은 회복 루트에서 고른 값, 없으면 0)
            gMix = "mine"   // ios: Data/GoalRoute.swift:417
            // 사이트와 동기화 중이고 사이트에 정한 목표가 아직 남아 있으면 그 목표를 그대로 쓴다
            val keep = Sync.shared.isOn && gTo != null && goalToDate > Instant.now()   // ios: Data/GoalRoute.swift:419
            if (!keep) { gM = if (recM > 0) recM.toDouble() else 0.0; gY = 3; gFrom = Day.today; setAutoGoal(years = 3) }   // ios: Data/GoalRoute.swift:420
        }
        Route.novice -> { gA = 0.0; gK = 1000.0; gY = 3; gM = 30.0; gMix = "index"; gFrom = Day.today }   // ios: Data/GoalRoute.swift:421
    }
    monthly = if (r == Route.recover) 0.0 else gM     // ios: Data/GoalRoute.swift:423 3년 전망의 '만약에 매달' 기본값 = 적립액
}

/** 본전을 넘은 회복 루트 → 목표 루트(플러스). 종목·위젯·만난 친구는 그대로 두고, 이미 한 미션(종목 넣기·앱 시작 3단계)은 끝낸 것으로 */
fun AppModel.graduateToGoal() {   // ios: Data/GoalRoute.swift:428
    val appDone = done.contains(5)   // ios: Data/GoalRoute.swift:429
    switchRoute(Route.plus)   // ios: Data/GoalRoute.swift:430
    gDone = setOf("hold")   // ios: Data/GoalRoute.swift:431
    if (appDone) { gDone = gDone + "link"; fixStarts() }   // ios: Data/GoalRoute.swift:432
    boardPath = listOf(MissionRoute.g1)   // ios: Data/GoalRoute.swift:433
}

/** 목표에 닿았는지 (목표를 정한 뒤) */
val AppModel.goalReached: Boolean get() = isGoal && gDone.contains("goal") && gK > 0 && trackValue / 1e4 >= gK   // ios: Data/GoalRoute.swift:437
/** 다음 목표 제안: 지금 금액에서 3년, 확률 50% 근처. 미션 진행은 그대로 둔다 */
fun AppModel.suggestNextGoal() {   // ios: Data/GoalRoute.swift:439
    gFrom = Day.today; gY = 3; gTo = null; setAutoGoal(years = 3, from = trackValue / 1e4)   // ios: Data/GoalRoute.swift:440
}

// ── AnalysisDetail.swift 의 AppModel 확장 중 Pending 자리에 있던 두 개 (내 길 시작일·과거 평가액). 화면 이식 때 이 자리를 그대로 쓴다
/** 내 길 시작한 날: 목표 루트는 목표 시작일(사이트와 같은 값), 회복 루트는 인터미션 시작일과 내 기록 첫날 중 이른 날. 모르면 오늘 */
val AppModel.pathStartDate: Instant get() {   // ios: Views/AnalysisDetail.swift:11
    val rec = (if (isSample) null else myRecord.firstOrNull())?.let { Day.date(it.d) }   // ios: Views/AnalysisDetail.swift:12
    if (isGoal) return minOf(Instant.now(), goalFromDate)   // ios: Views/AnalysisDetail.swift:13
    val rs = interStartAt?.let { Instant.ofEpochMilli((it * 1000).toLong()) }   // ios: Views/AnalysisDetail.swift:14
    val d = listOfNotNull(rs, rec).minOrNull()   // ios: Views/AnalysisDetail.swift:15
    return minOf(Instant.now(), d ?: Instant.now())   // ios: Views/AnalysisDetail.swift:16
}
/** x 시점(0 = 3년 전, 1 = 어제)의 전체 평가액 (원화) */
fun AppModel.totalAt(x: Double): Double =   // ios: Views/AnalysisDetail.swift:100
    rows.fold(0.0) { a, r -> a + krw(r.sym, r.h.qty * prices.price(r.sym, at = x)) }   // ios: Views/AnalysisDetail.swift:101
