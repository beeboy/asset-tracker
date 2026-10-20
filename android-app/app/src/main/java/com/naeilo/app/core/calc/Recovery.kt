package com.naeilo.app.core.calc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.Lens
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.data.Currency
import com.naeilo.app.data.short
import com.naeilo.app.pending.*
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.hex
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 회복 루트 계산 (시안 mk·chart 와 같은 식). 모든 설명은 이 계산값으로 만든다.

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

enum class MissionRoute { m1, m1r, m2, m2r, m3, m3r, m4, m4r, nx, gp1r, g1, g1r, g3, g3r, gt, gi }   // ios: Data/Recovery.swift:5

// Kotlin enum 은 name 이 경우 이름(rawValue)이라, Swift 의 name(보이는 이름)은 title 로 둔다
enum class Basket {   // ios: Data/Recovery.swift:7
    G, V, B, C;   // ios: Data/Recovery.swift:8
    val title: String get() = when (this) { G -> "성장 지속"; V -> "저평가 회복"; B -> "버팀목"; C -> "현금" }   // ios: Data/Recovery.swift:9
    val rule: String get() = when (this) {   // ios: Data/Recovery.swift:10,11
        G -> "PER이 평균보다 15%↑, 이익 성장으로 설명됨"   // ios: Data/Recovery.swift:12
        V -> "PER이 평균보다 10%↓, 이익 성장 5%↑"   // ios: Data/Recovery.swift:13
        B -> "PER이 평균 ±15% 안, 이익 성장 3%↑"   // ios: Data/Recovery.swift:14
        C -> ""   // ios: Data/Recovery.swift:15
    }
    val color: Color @Composable get() = when (this) { G -> Theme.green; V -> Theme.orange; B -> Theme.blue; C -> if (Theme.isDark) hex(0x56616C) else hex(0xBFC6CD) }   // ios: Data/Recovery.swift:18

    companion object {
        // 분류 규칙 (tools/classify_sector_pe.py 와 같음). H = 과열, X = 세 묶음 밖
        fun of(pe: Double, pe10: Double, growth: Double): String {   // ios: Data/Recovery.swift:21
            val x = pe / pe10   // ios: Data/Recovery.swift:22
            if (x >= 1.15) return if (growth >= 10 && pe / growth <= 2.5) "G" else "H"   // ios: Data/Recovery.swift:23
            if (x <= 0.90 && growth >= 5) return "V"   // ios: Data/Recovery.swift:24
            if (x > 0.85 && x < 1.15 && growth >= 3) return "B"   // ios: Data/Recovery.swift:25
            return "X"   // ios: Data/Recovery.swift:26
        }
    }
    val industries: List<Industries.Row> get() =   // ios: Data/Recovery.swift:28
        Industries.all.filter { it.count >= 10 && of(pe = it.pe, pe10 = it.pe10, growth = it.growth) == name }   // ios: Data/Recovery.swift:29
            .sortedByDescending { it.count }   // ios: Data/Recovery.swift:30
}

data class Plan(   // ios: Data/Recovery.swift:34
    val id: String,   // ios: Data/Recovery.swift:35
    val name: String,   // ios: Data/Recovery.swift:36
    val tag: String,   // ios: Data/Recovery.swift:37
    val wt: Double,          // ios: Data/Recovery.swift:38 계획에서 몰린 종목(비중 1위) 비중
    val sigma: Double,       // ios: Data/Recovery.swift:39 닫힌 식 (경로 계산이 끝나기 전 잠깐)
    val g: Double,           // ios: Data/Recovery.swift:40 보통의 연 성장 (로그, 닫힌 식)
    val mix: Map<Basket, Double>,   // ios: Data/Recovery.swift:41
    val mixText: String,   // ios: Data/Recovery.swift:42
    val V: Double,   // ios: Data/Recovery.swift:43
    var focus: String = "몰린 종목",     // ios: Data/Recovery.swift:44 몰린 종목 이름 (설명용)
    /** 전망과 같은 몬테카를로 경로 (웹 본전 카드와 같은 셈). 계산이 끝나기 전에는 null → 닫힌 식 */
    var paths: PathSet? = null,   // ios: Data/Recovery.swift:46 nil
) {
    /** T년 안에 월말 평가액이 K 이상이 되는 달이 올 확률 (웹 본전 카드 breakeven 과 같은 셈) */
    fun prob(K: Double, T: Double): Double {   // ios: Data/Recovery.swift:49
        if (!(V > 0 && K > 0)) return 0.0   // ios: Data/Recovery.swift:50
        val ps = paths   // ios: Data/Recovery.swift:51
        if (ps != null) {
            val v = ps.values(V, 0.0)   // ios: Data/Recovery.swift:52
            return ps.touch(v, ps.k((T * 12).swRound().toInt())) { _ -> K }   // ios: Data/Recovery.swift:53
        }
        return AppModel.normCDF((ln(V / K) + g * T) / (sigma * sqrt(T)))   // ios: Data/Recovery.swift:55
    }
    /** T년 뒤 손익 (k = -1 나쁜 경우 5%, 0 보통, 1 좋은 경우 95%), 들어간 돈 C 대비 */
    fun outcome(C: Double, T: Double, k: Double): Double {   // ios: Data/Recovery.swift:58
        if (!(C > 0)) return 0.0   // ios: Data/Recovery.swift:59
        val ps = paths   // ios: Data/Recovery.swift:60
        if (ps != null) {
            val b = ps.bands(V); val kk = ps.k((T * 12).swRound().toInt())   // ios: Data/Recovery.swift:61
            return b[if (k < 0) 0 else if (k > 0) 4 else 2][kk] / C - 1   // ios: Data/Recovery.swift:62
        }
        return V * exp(g * T + k * 1.645 * sigma * sqrt(T)) / C - 1   // ios: Data/Recovery.swift:64
    }
    /** 그래프: t개월 뒤 평가액 (z = -1 나쁜 경우, 0 보통, 1 좋은 경우). 경로면 월 경계 사이를 곧게 잇는다 */
    fun level(t: Double, z: Double): Double {   // ios: Data/Recovery.swift:67
        val ps = paths ?: return V * exp(g * t / 12 + z * 1.645 * sigma * sqrt(t / 12))   // ios: Data/Recovery.swift:68
        val b = ps.bands(V)[if (z < 0) 0 else if (z > 0) 4 else 2]; val tm = ps.tM   // ios: Data/Recovery.swift:69
        var k = 0; while (k + 1 < tm.size && tm[k + 1] < t) k += 1   // ios: Data/Recovery.swift:70
        if (!(k + 1 < tm.size)) return b[k]   // ios: Data/Recovery.swift:71
        val f = max(0.0, min(1.0, (t - tm[k]) / max(1e-9, tm[k + 1] - tm[k])))   // ios: Data/Recovery.swift:72
        return b[k] * (1 - f) + b[k + 1] * f   // ios: Data/Recovery.swift:73
    }
    /** 1년 흔들림(로그 성장의 표준편차) · 보통의 연 성장(로그 중앙값) · 평균 연 수익 */
    data class Year(val sigma: Double, val g: Double, val mean: Double)
    val year: Year get() {   // ios: Data/Recovery.swift:76
        val ps = paths; if (ps == null || !(V > 0)) return Year(sigma, g, exp(g + sigma * sigma / 2) - 1)   // ios: Data/Recovery.swift:77
        val v = ps.values(V, 0.0); val k = ps.k(12); val P = ps.P   // ios: Data/Recovery.swift:78
        val r = DoubleArray(P); for (p in 0 until P) r[p] = ln(max(1e-12, v[k * P + p] / V)); r.sort()   // ios: Data/Recovery.swift:79
        val m = r.fold(0.0) { a, x -> a + x } / P.toDouble(); val sd = sqrt(r.fold(0.0) { a, x -> a + (x - m) * (x - m) } / max(1, P - 1).toDouble())   // ios: Data/Recovery.swift:80
        val mean = (0 until P).fold(0.0) { a, p -> a + v[k * P + p] / V } / P.toDouble() - 1   // ios: Data/Recovery.swift:81
        return Year(sd, MC.quantileSorted(r, 0.5), mean)   // ios: Data/Recovery.swift:82
    }
    val desc: String get() =   // ios: Data/Recovery.swift:84
        if (id == "keep") "지금 그대로 (${focus} ${AppModel.pct(wt)})" else "${focus} ${AppModel.pct(wt)}로 · 줄인 돈은 ${mixText}"   // ios: Data/Recovery.swift:85
}

// ios: Data/Recovery.swift:89 (extension AppModel → 아래 확장들)

/** 회복 루트는 '한 종목에 몰린 비중'을 다룬다: 몰린 종목 = 내 평가액이 가장 큰 종목, 나머지는 한 묶음
 *  (시안은 DRNK·QQQ 두 종목이었고, 내 종목이 그 둘이면 결과는 시안과 같다) */
val AppModel.focusRow: AppModel.Row? get() = rows.maxByOrNull { it.value }   // ios: Data/Recovery.swift:92
val AppModel.restRows: List<AppModel.Row> get() = rows.filter { it.id != focusRow?.id }   // ios: Data/Recovery.swift:93
val AppModel.focusName: String get() = focusRow?.sym?.short ?: "몰린 종목"   // ios: Data/Recovery.swift:94
/** 나머지 이름: 하나면 그 종목, 여럿이면 "나머지 n종목" */
val AppModel.restName: String get() {   // ios: Data/Recovery.swift:96
    val r = restRows   // ios: Data/Recovery.swift:97
    return if (r.size == 1) r[0].sym.short else if (r.isEmpty()) "나머지" else "나머지 ${r.size}종목"   // ios: Data/Recovery.swift:98
}
/** 나머지 묶음 수익률 (들어간 돈 대비) */
val AppModel.restRet: Double get() {   // ios: Data/Recovery.swift:101
    val c = restRows.fold(0.0) { a, r -> a + r.cost }   // ios: Data/Recovery.swift:102
    return if (c > 0) restRows.fold(0.0) { a, r -> a + r.value } / c - 1 else 0.0   // ios: Data/Recovery.swift:103
}
/** 몰린 종목의 업종 자료 (업종 이름이 다모다란 업종과 같을 때만) */
val AppModel.focusIndustry: Industries.Row? get() {   // ios: Data/Recovery.swift:106
    val s = focusRow?.sym; if (s == null || s.sector.isEmpty()) return null   // ios: Data/Recovery.swift:107
    return Industries.all.firstOrNull { it.ko == s.sector }   // ios: Data/Recovery.swift:108
}
val AppModel.halfway: Double get() = if (total < cost) total + (cost - total) / 2 else cost   // ios: Data/Recovery.swift:110

// MARK: 구성 경로 (전망과 같은 몬테카를로)
/** Swift 튜플 (key, ccy, w) 자리. w 는 merged 에서 더하므로 var (넣을 때 copy 해서 값처럼 쓴다) */
data class Part(val key: String, val ccy: String, var w: Double)   // ios: Data/Recovery.swift:113
/** 묶음·지수의 대표 종목 (사이트가 매일 시세를 받는 ETF). 사이트 통계에 아직 없으면 미국 전체 지수로 */
private val proxyKeysMap: Map<String, List<String>> = mapOf("I" to listOf("SPY"), "G" to listOf("QQQ", "SPY"), "V" to listOf("VTV", "SPY"), "B" to listOf("USMV", "SPY"), "C" to listOf("SGOV"))   // ios: Data/Recovery.swift:115
val AppModel.Companion.proxyKeys: Map<String, List<String>> get() = proxyKeysMap   // ios: Data/Recovery.swift:115
fun AppModel.proxy(comp: String): String {   // ios: Data/Recovery.swift:116
    val ks = AppModel.proxyKeys[comp] ?: listOf("SPY"); val st = Market.shared.stats   // ios: Data/Recovery.swift:117
    return ks.firstOrNull { st?.tickers?.get(it) != null } ?: ks.last()   // ios: Data/Recovery.swift:118
}
/** 지금 내 종목 (사이트 티커·통화·평가액 비중) */
val AppModel.mineParts: List<Part> get() {   // ios: Data/Recovery.swift:121
    val t = max(1.0, total)   // ios: Data/Recovery.swift:122
    val out = mutableListOf<Part>()   // ios: Data/Recovery.swift:123
    for (r in rows) out.add(Part(key = SiteStats.mcKey(r.id), ccy = if (r.sym.currency == Currency.usd) "USD" else "KRW", w = r.value / t))   // ios: Data/Recovery.swift:124
    return out   // ios: Data/Recovery.swift:125
}
/** 같은 종목은 합친다 (같은 요인 둘은 상관 1 이라 모형이 흔들린다) */
fun AppModel.Companion.merged(parts: List<Part>): List<Part> {   // ios: Data/Recovery.swift:128
    val out = mutableListOf<Part>()   // ios: Data/Recovery.swift:129
    for (p in parts) { if (!(p.w > 1e-9)) continue   // ios: Data/Recovery.swift:130
        val i = out.indexOfFirst { it.key == p.key }; if (i >= 0) out[i].w += p.w else out.add(p.copy())   // ios: Data/Recovery.swift:131
    }
    return out   // ios: Data/Recovery.swift:133
}
/** mcInputs 튜플 (st, settings, events, sig) 자리 */
class McInputs(val st: MC.Stats, val settings: MC.Settings, val events: List<MC.Event>, val sig: String)
/** 전망과 같은 사이트 통계·설정·사건. 내 전망(forecastJob)은 동기화로 받은 웹 설정의 경로 수 그대로 돈다 (웹과 같은 숫자.
 *  웹 선택지의 최대 10,000개). 다른 구성 비교(계획·묶음)는 앱에만 있는 화면이라 사이트 기본(3,000개)까지만 */
val AppModel.mcInputs: McInputs? get() = mcInputs(full = false)   // ios: Data/Recovery.swift:137
fun AppModel.mcInputs(full: Boolean): McInputs? {   // ios: Data/Recovery.swift:138
    val st = Market.shared.stats ?: return null   // ios: Data/Recovery.swift:139
    val site = Sync.shared.siteForecast   // ios: Data/Recovery.swift:140
    var settings = (st.model ?: MC.Settings()).merged(site.model)   // ios: Data/Recovery.swift:141
    val cap = if (full) AppModel.webPathMax else mcPathCap(st)   // ios: Data/Recovery.swift:142
    if (settings.nPaths > cap) settings = settings.copy(n_paths = cap.toDouble())   // ios: Data/Recovery.swift:143
    val events = site.events ?: st.events ?: emptyList()   // ios: Data/Recovery.swift:144
    // ios: Data/Recovery.swift:145 JSONEncoder sortedKeys 자리: 사인(캐시 키)만 만들므로 kotlinx 순서로 충분
    val evSig = ForecastEngine.fnv(NJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(MC.Event.serializer()), events))   // ios: Data/Recovery.swift:146
    return McInputs(st, settings, events, listOf(st.date, evSig, "${settings.nPaths}|${settings.seedU32}|${settings.dof}|${settings.earnings_adjust ?: true}").joinToString("|"))   // ios: Data/Recovery.swift:147
}
/** 웹 '경로' 선택지의 최대 */
val AppModel.Companion.webPathMax: Int get() = 10_000   // ios: Data/Recovery.swift:150
/** 다른 구성 비교가 도는 경로 수 상한 = 사이트 기본 설정의 경로 수 (없으면 3,000) */
fun AppModel.mcPathCap(st: MC.Stats?): Int = max(500, (st?.model ?: MC.Settings()).nPaths)   // ios: Data/Recovery.swift:152
/** 종목별 연 기대수익: 전망 화면과 같은 관점(현재 정세·과거 추세·내 관점) */
fun AppModel.lensMu(fs: List<MC.Factor>, A: Int): List<Double> =   // ios: Data/Recovery.swift:154
    fs.take(A).map { if (lens == Lens.base) it.base else if (lens == Lens.smooth) it.smooth else MC.blendMu(it.base, it.smooth, trust / 100) }   // ios: Data/Recovery.swift:155
/** 구성 parts 의 오늘부터 H(yyyy-MM-dd)까지 경로. 지금 내 구성이고 기간이 전망과 같으면 전망 경로를 그대로 쓴다.
 *  아직 계산 중이면 null (끝나면 화면이 다시 그려진다). 계산은 ForecastEngine·PathRuns 가 뒤에서 하고 여기는 캐시만 읽는다 */
fun AppModel.paths(parts0: List<Part>, horizon: String): PathSet? {   // ios: Data/Recovery.swift:159
    val H = horizon
    val parts = AppModel.merged(parts0); val today = Day.today   // ios: Data/Recovery.swift:160
    if (parts.isEmpty() || !(H > today)) return null; val inp = mcInputs ?: return null   // ios: Data/Recovery.swift:161
    val lk = "${lens}|${trust}"   // ios: Data/Recovery.swift:162
    val mp = AppModel.merged(mineParts)   // ios: Data/Recovery.swift:163
    val mine = parts.size == mp.size && parts.zip(mp).all { (a, b) -> a.key == b.key && kotlin.math.abs(a.w - b.w) < 1e-9 }   // ios: Data/Recovery.swift:164
    val job0 = forecastJob   // ios: Data/Recovery.swift:165
    if (mine && job0 != null && job0.horizon == H) {
        val eng = ForecastEngine.shared   // ios: Data/Recovery.swift:166
        eng.ensure(job0)   // ios: Data/Recovery.swift:167
        val run = eng.run; if (run == null || run.key != job0.key) return null   // ios: Data/Recovery.swift:168
        val pk = "fc|${job0.key}|${lk}"   // ios: Data/Recovery.swift:169
        pathSetCache[pk]?.let { return it }   // ios: Data/Recovery.swift:170
        val ps = PathSet(sim = run.ev, mu = lensMu(run.factors, run.ev.A), base = run.ev.v0, key = pk)   // ios: Data/Recovery.swift:171
        pathSetCache = pathSetCache.filter { it.key.startsWith("fc|${job0.key}") || !it.key.startsWith("fc|") }.toMutableMap()   // ios: Data/Recovery.swift:172
        pathSetCache[pk] = ps   // ios: Data/Recovery.swift:173
        return ps   // ios: Data/Recovery.swift:174
    }
    val key = listOf(today, H, inp.sig, parts.joinToString(",") { it.key + ":" + it.ccy + ":" + "%.4f".format(Locale.ROOT, it.w) }).joinToString("|")   // Data/Recovery.swift:176 (검사 예외: 보간 속 따옴표)
    val hs = parts.map { MC.Holding(key = it.key, ccy = it.ccy, valueKrw = it.w * 1e6, price0 = 1.0) }   // ios: Data/Recovery.swift:177
    val group = listOf(today, H, inp.sig, parts.joinToString(",") { "${it.key}:${it.ccy}" }).joinToString("|")   // ios: Data/Recovery.swift:178
    val job = ForecastEngine.Job(key = key, holdings = hs, stats = ExtraStats.shared.augment(inp.st, keys = parts.map { it.key }), settings = inp.settings, events = inp.events, start = today, horizon = H,   // ios: Data/Recovery.swift:179
                                 goal = Double.POSITIVE_INFINITY, monthly = 0.0, group = group, w = parts.map { it.w })   // ios: Data/Recovery.swift:180
    val run = PathRuns.shared.get(job) ?: return null   // ios: Data/Recovery.swift:181
    val pk = "pr|${run.key}|${lk}"   // ios: Data/Recovery.swift:182
    pathSetCache[pk]?.let { return it }   // ios: Data/Recovery.swift:183
    val ps = PathSet(sim = run.sim, mu = lensMu(run.factors, run.sim.A), base = run.sim.v0, key = pk)   // ios: Data/Recovery.swift:184
    if (pathSetCache.size > 24) pathSetCache = mutableMapOf()   // ios: Data/Recovery.swift:185
    pathSetCache[pk] = ps   // ios: Data/Recovery.swift:186
    return ps   // ios: Data/Recovery.swift:187
}
/** closedParams 튜플 (mu, sigma) 자리 */
data class MuSigma(val mu: Double, val sigma: Double)
private data class ClosedX(val k: String, val w: Double, val mu: Double, val vol: Double)
/** 닫힌 식 값 (경로 계산이 끝나기 전 잠깐): 사이트 통계의 μ(지금 관점)·σ·상관으로 구성의 연 기대·흔들림. 달러 몫은 환율 흔들림도 */
fun AppModel.closedParams(parts0: List<Part>): MuSigma {   // ios: Data/Recovery.swift:190
    val parts = AppModel.merged(parts0); val st = Market.shared.stats?.let { ExtraStats.shared.augment(it, keys = parts0.map { p -> p.key }) }; val t = trust / 100   // ios: Data/Recovery.swift:191
    val d = st?.defaults ?: MC.Stats.Defaults(mu = 0.10, vol = 0.40, corr = 0.3)   // ios: Data/Recovery.swift:192
    val xs: MutableList<ClosedX> = parts.map { p ->   // ios: Data/Recovery.swift:193
        val tk = st?.tickers?.get(p.key); val b = tk?.mu ?: d.mu; val s = tk?.trend ?: b   // ios: Data/Recovery.swift:194
        val mu = if (lens == Lens.base) b else if (lens == Lens.smooth) s else MC.blendMu(b, s, t)   // ios: Data/Recovery.swift:195
        ClosedX(k = p.key, w = p.w, mu = mu, vol = tk?.vol ?: d.vol)   // ios: Data/Recovery.swift:196
    }.toMutableList()
    val usdW = parts.filter { it.ccy == "USD" }.fold(0.0) { a, x -> a + x.w }   // ios: Data/Recovery.swift:198
    if (usdW > 0) { val fx = SiteStats.fx(st); xs.add(ClosedX(k = "KRW=X", w = usdW, mu = fx.first, vol = fx.second)) }   // ios: Data/Recovery.swift:199
    val ci = HashMap<String, Int>(); (st?.corr?.keys ?: emptyList()).forEachIndexed { i, k -> if (!ci.containsKey(k)) ci[k] = i }   // ios: Data/Recovery.swift:200 uniquingKeysWith: 처음 것
    fun corr(a: String, b: String): Double {   // ios: Data/Recovery.swift:201
        if (a == b) return 1.0   // ios: Data/Recovery.swift:202
        val i = ci[a]; val j = ci[b]; val m = st?.corr?.m; if (i != null && j != null && m != null) return m[i][j]   // ios: Data/Recovery.swift:203
        val safe = { k: String -> k == "KRW=X" || st?.tickers?.get(k)?.cash == true }   // ios: Data/Recovery.swift:204
        return if (safe(a) || safe(b)) 0.0 else d.corr   // ios: Data/Recovery.swift:205
    }
    val mu = xs.filter { it.k != "KRW=X" }.fold(0.0) { a, x -> a + x.w * x.mu } + (if (usdW > 0) usdW * (xs.lastOrNull()?.mu ?: 0.0) else 0.0)   // ios: Data/Recovery.swift:207
    var v = 0.0   // ios: Data/Recovery.swift:208
    for (a in xs) { for (b in xs) { v += a.w * b.w * a.vol * b.vol * corr(a.k, b.k) } }   // ios: Data/Recovery.swift:209
    return MuSigma(mu, max(0.01, sqrt(v)))   // ios: Data/Recovery.swift:210
}

/** 계획 4안의 기간 (회복 루트 전망과 같은 3년) */
val AppModel.planHorizon: String get() = MC.addMonths(Day.today, 36)   // ios: Data/Recovery.swift:214
/** 계획 구성: 몰린 종목은 wt 까지, 나머지 종목은 그대로, 줄인 몫은 묶음 비율대로 (묶음 = 대표 ETF) */
fun AppModel.planParts(wt: Double, mix: Map<Basket, Double>): List<Part> {   // ios: Data/Recovery.swift:216
    val f = focusRow ?: return emptyList()   // ios: Data/Recovery.swift:217
    val t = max(1.0, total); val w = f.value / t; val ccy = { r: AppModel.Row -> if (r.sym.currency == Currency.usd) "USD" else "KRW" }   // ios: Data/Recovery.swift:218
    val out = mutableListOf(Part(key = SiteStats.mcKey(f.id), ccy = ccy(f), w = wt))   // ios: Data/Recovery.swift:219
    for (r in restRows) out.add(Part(key = SiteStats.mcKey(r.id), ccy = ccy(r), w = r.value / t))   // ios: Data/Recovery.swift:220
    val freed = max(0.0, w - wt); val tot = mix.values.fold(0.0) { a, x -> a + x }   // ios: Data/Recovery.swift:221
    if (freed > 0 && tot > 0) { for (b in Basket.entries) { val x = mix[b]; if (x != null && x > 0) out.add(Part(key = proxy(b.name), ccy = "USD", w = freed * x / tot)) } }   // ios: Data/Recovery.swift:222
    return out   // ios: Data/Recovery.swift:223
}

val AppModel.plans: List<Plan> get() {   // ios: Data/Recovery.swift:226
    // 닫힌 식(계산 중 잠깐)의 흔들림·기대 수익도 사이트가 매일 만드는 종목 값 (웹 전망과 같은 값)
    val w = focusWeight; val V = total; val fn = focusName; val H = planHorizon   // ios: Data/Recovery.swift:228
    fun mk(id: String, name: String, tag: String, cap: Double, mix: Map<Basket, Double>, mixText: String): Plan {   // ios: Data/Recovery.swift:229
        val wt = min(w, cap)   // ios: Data/Recovery.swift:230
        val parts = if (id == "keep") mineParts else planParts(wt = wt, mix = mix)   // ios: Data/Recovery.swift:231
        val c = closedParams(parts)   // ios: Data/Recovery.swift:232
        val p = Plan(id = id, name = name, tag = tag, wt = wt, sigma = c.sigma, g = c.mu - c.sigma * c.sigma / 2, mix = mix, mixText = mixText, V = V, focus = fn)   // ios: Data/Recovery.swift:233
        p.paths = paths(parts, horizon = H)   // ios: Data/Recovery.swift:234
        return p   // ios: Data/Recovery.swift:235
    }
    return listOf(   // ios: Data/Recovery.swift:237
        mk("keep", "유지", "지금 그대로", 1.0, mapOf(Basket.C to 1.0), ""),   // ios: Data/Recovery.swift:238
        mk("push", "도전", "빠른 회복 우선", 0.45, mapOf(Basket.G to 0.7, Basket.V to 0.3), "성장 지속 7 : 저평가 회복 3"),   // ios: Data/Recovery.swift:239
        mk("balance", "균형", "회복과 방어 반반", 0.35, mapOf(Basket.G to 1.0 / 3, Basket.V to 1.0 / 3, Basket.B to 1.0 / 3), "세 묶음에 고르게"),   // ios: Data/Recovery.swift:240
        mk("guard", "방어", "덜 잃기 우선", 0.25, mapOf(Basket.V to 0.2, Basket.B to 0.6, Basket.C to 0.2), "버팀목 6 : 저평가 회복 2 : 현금 2"),   // ios: Data/Recovery.swift:241
    )
}
val AppModel.keepPlan: Plan get() = plans[0]   // ios: Data/Recovery.swift:244
val AppModel.selectedPlan: Plan get() { val ps = plans; return ps.firstOrNull { it.id == planKey } ?: ps[2] }   // ios: Data/Recovery.swift:245
val AppModel.horizonLabel: String get() = if (horizon == 12) "1년" else "${horizon}개월"   // ios: Data/Recovery.swift:246

// 미션 2: 손실이 어디서 왔나
val AppModel.lossShareFocus: Double get() {   // ios: Data/Recovery.swift:249
    val lt = (focusRow?.let { it.value - it.cost } ?: 0.0); val lq = restRows.fold(0.0) { a, r -> a + r.value - r.cost }   // ios: Data/Recovery.swift:250
    val tot = lt + lq   // ios: Data/Recovery.swift:251
    return if (tot < 0) max(0.0, min(1.0, lt / tot)) else 0.0   // ios: Data/Recovery.swift:252
}
val AppModel.quizRight: String get() = if (focusWeight > 0.5 && lossShareFocus > 0.6) "conc" else "mkt"   // ios: Data/Recovery.swift:254

// 미션 3 결과: 옮길 금액 (나머지는 그대로, 줄인 몰린 종목 금액을 묶음에 나눔)
val AppModel.freedAmount: Double get() = max(0.0, (focusRow?.value ?: 0.0) - selectedPlan.wt * total)   // ios: Data/Recovery.swift:257

// 적립 지렛대: 매달 같은 돈을 지금 구성에 더 넣을 때. 본전 = 들어간 돈 + 그동안 넣은 돈 (넣은 만큼 같이 오른다)
/** prob = months 안에 본전에 닿을 확률, eta = 보통의 경우 본전에 처음 닿는 개월 (3년 안에 없으면 무한대) */
data class Deposit(val prob: Double, val eta: Double)   // ios: Data/Recovery.swift:261
fun AppModel.deposit(p: Plan, monthly: Int, months: Int): Deposit {   // ios: Data/Recovery.swift:262
    val mo = monthly; val T = months
    val D = mo.toDouble() * 1e4; val C = cost   // ios: Data/Recovery.swift:263
    val ps = p.paths   // ios: Data/Recovery.swift:264
    if (ps != null) {
        if (total >= C) return Deposit(prob = 1.0, eta = 0.0)   // ios: Data/Recovery.swift:265
        val v = ps.values(p.V, D); val M = ps.M   // ios: Data/Recovery.swift:266
        val hits = ps.firstHits(v, M - 1) { k -> C + D * min(k, M - 2).toDouble() }   // ios: Data/Recovery.swift:267
        val kT = ps.k(T)   // ios: Data/Recovery.swift:268
        return Deposit(prob = hits.count { it >= 0 && it <= kT }.toDouble() / ps.P.toDouble(), eta = ps.medianMonths(hits))   // ios: Data/Recovery.swift:269
    }
    // 계산 중: 닫힌 식 흔들림으로 잠깐 (1,000번 월 단위)
    val ck = "${p.id}|${p.V}|${p.sigma}|${C}|${mo}|${T}"   // ios: Data/Recovery.swift:272
    depCache[ck]?.let { return it }   // ios: Data/Recovery.swift:273
    var seed = 24680L   // ios: Data/Recovery.swift:274
    fun rnd(): Double { seed = (seed * 1103515245L + 12345L) % 2147483648L; return (seed.toDouble() + 0.5) / 2147483648.0 }   // ios: Data/Recovery.swift:275
    val NP = 1000; val H = 36; val mm = p.g / 12; val ms = p.sigma / sqrt(12.0)   // ios: Data/Recovery.swift:276
    var ok = 0; val hits = ArrayList<Double>(NP)   // ios: Data/Recovery.swift:277
    for (n in 0 until NP) {   // ios: Data/Recovery.swift:278
        var v = p.V; var c = C; var hit = Double.POSITIVE_INFINITY   // ios: Data/Recovery.swift:279
        if (v >= c) hit = 0.0   // ios: Data/Recovery.swift:280
        for (k in 1..H) {   // ios: Data/Recovery.swift:281
            val z = sqrt(-2 * ln(rnd())) * cos(6.283185307 * rnd())   // ios: Data/Recovery.swift:282
            v = v * exp(mm + ms * z) + D; c += D   // ios: Data/Recovery.swift:283
            if (hit == Double.POSITIVE_INFINITY && v >= c) hit = k.toDouble()   // ios: Data/Recovery.swift:284
        }
        if (hit <= T.toDouble()) ok += 1   // ios: Data/Recovery.swift:286
        hits.add(hit)   // ios: Data/Recovery.swift:287
    }
    val r = Deposit(prob = ok.toDouble() / NP.toDouble(), eta = hits.sorted()[NP / 2])   // ios: Data/Recovery.swift:289
    if (depCache.size > 32) depCache.clear()   // ios: Data/Recovery.swift:290
    depCache[ck] = r   // ios: Data/Recovery.swift:291
    return r   // ios: Data/Recovery.swift:292
}

// 절세 (대한민국 거주자: 해외주식 이익-손실 합계에서 250만원 공제 뒤 22%)
val AppModel.taxLossMan: Double get() {   // ios: Data/Recovery.swift:296
    val d = focusRow ?: return 0.0   // ios: Data/Recovery.swift:297
    return krw(d.sym, taxSellQty * (d.sym.last - d.h.avg)) / 1e4   // ios: Data/Recovery.swift:298
}
val AppModel.taxBefore: Double get() = max(0.0, taxGain - 250) * 0.22   // ios: Data/Recovery.swift:300
val AppModel.taxAfter: Double get() = max(0.0, taxGain + taxLossMan - 250) * 0.22   // ios: Data/Recovery.swift:301

/** PathChart 한 줄: (계획, 색, 점선 여부) */
data class ChartPlan(val plan: Plan, val color: Color, val dashed: Boolean)

// 1년 범위 그래프: 띠 = 100번 중 90번, 선 = 보통의 경우, 빨간 점선 = 본전, 회색 점선 = 손실 절반
// ios: Data/Recovery.swift:305 struct PathChart
@Composable
fun PathChart(
    plans: List<ChartPlan>,   // ios: Data/Recovery.swift:306 (계획, 색, 점선 여부) 뒤에 올수록 위에 그림
    V: Double,   // ios: Data/Recovery.swift:307
    C: Double,   // ios: Data/Recovery.swift:308
    half: Double? = null,   // ios: Data/Recovery.swift:309 nil
    marker: Int? = null,             // ios: Data/Recovery.swift:310 nil 기간 칩 위치 (개월)
    modifier: Modifier = Modifier,
) {   // ios: Data/Recovery.swift:312 body
    val border = Theme.border; val muted = Theme.muted; val ink = Theme.ink
    val ts = remember { (0..(12 * 2)).map { it * 0.5 } }   // ios: Data/Recovery.swift:321 stride(from: 0.0, through: 12, by: 0.5)
    // 경로 값은 그릴 때마다 다시 세지 않게 계획이 바뀔 때만 (val(p, t, k) = p.level(t, k): 1 좋은 경우, -1 나쁜 경우, 0 보통)
    val lines = remember(plans) { plans.map { cp -> listOf(1, -1, 0).associateWith { k -> ts.map { cp.plan.level(it, k.toDouble()) } } } }   // ios: Data/Recovery.swift:317
    Canvas(modifier.semantics { contentDescription = "1년 평가액 범위와 본전선" }) {   // ios: Data/Recovery.swift:313,345
        val W = size.width.toDouble(); val H = size.height.toDouble(); val ymax = max(C * 1.2, V * 1.9)   // ios: Data/Recovery.swift:314
        val X = { t: Double -> (t / 12 * W).toFloat() }   // ios: Data/Recovery.swift:315
        val Y = { v: Double -> (H - min(v, ymax) / ymax * H).toFloat() }   // ios: Data/Recovery.swift:316
        for (x in listOf(3.0, 6.0)) {   // ios: Data/Recovery.swift:318
            drawLine(border, Offset(X(x), 0f), Offset(X(x), H.toFloat()), strokeWidth = 1.dp.toPx())   // ios: Data/Recovery.swift:319
        }
        for ((n, cp) in plans.withIndex()) {   // ios: Data/Recovery.swift:322
            val band = Path()   // ios: Data/Recovery.swift:323
            for ((i, t) in ts.withIndex()) { val x = X(t); val y = Y(lines[n].getValue(1)[i]); if (i == 0) band.moveTo(x, y) else band.lineTo(x, y) }   // ios: Data/Recovery.swift:324
            for (i in ts.indices.reversed()) band.lineTo(X(ts[i]), Y(lines[n].getValue(-1)[i]))   // ios: Data/Recovery.swift:325
            band.close()   // ios: Data/Recovery.swift:326
            drawPath(band, cp.color.copy(alpha = cp.color.alpha * (if (cp.dashed) 0.12f else 0.18f)))   // ios: Data/Recovery.swift:327
        }
        drawLine(start = Offset(0f, Y(C)), end = Offset(W.toFloat(), Y(C)),   // ios: Data/Recovery.swift:329
                 color = hex(0xC8352E), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))   // ios: Data/Recovery.swift:330
        if (half != null) {   // ios: Data/Recovery.swift:331
            drawLine(muted, Offset(0f, Y(half)), Offset(W.toFloat(), Y(half)),   // ios: Data/Recovery.swift:332
                     strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))   // ios: Data/Recovery.swift:333
        }
        if (marker != null) {   // ios: Data/Recovery.swift:335
            drawLine(ink, Offset(X(marker.toDouble()), 0f), Offset(X(marker.toDouble()), H.toFloat()),   // ios: Data/Recovery.swift:336
                     strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))   // ios: Data/Recovery.swift:337
        }
        for ((n, cp) in plans.withIndex()) {   // ios: Data/Recovery.swift:339
            val med = Path()   // ios: Data/Recovery.swift:340
            for ((i, t) in ts.withIndex()) { val x = X(t); val y = Y(lines[n].getValue(0)[i]); if (i == 0) med.moveTo(x, y) else med.lineTo(x, y) }   // ios: Data/Recovery.swift:341
            drawPath(med, cp.color, style = Stroke(width = (if (cp.dashed) 2.0 else 2.5).toFloat().dp.toPx(), pathEffect = if (cp.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null))   // ios: Data/Recovery.swift:342
        }
    }
}
