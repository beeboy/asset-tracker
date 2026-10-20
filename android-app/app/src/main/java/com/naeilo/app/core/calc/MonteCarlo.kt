package com.naeilo.app.core.calc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.truncate

// 전망 몬테카를로: web/model.js 의 buildModel·simulate·reweight 를 그대로 옮겼다 (아이폰 MonteCarlo.swift 와 같은 줄·같은 순서).
// 같은 시드 난수(xoshiro128** + Box-Muller), 같은 일별 단계(다변량 t 충격 + 사건 점프), 같은 월 경계·적립이라
// 같은 입력이면 웹·아이폰과 같은 결과가 나온다. 덧셈 순서·괄호는 Swift 그대로 둔다 (비트 단위로 같게).
// 이 파일은 안드로이드 것을 쓰지 않는다 (비교 도구가 JVM 에서 따로 돌릴 수 있게).

// Swift Double.rounded(): .5 는 0 에서 먼 쪽 (Math.round·kotlin.math.round 와 다르다)
private fun mcRound(x: Double): Double { val t = truncate(x); return if (abs(x - t) >= 0.5) t + (if (x < 0) -1.0 else 1.0) else t }
// Swift min·max (y < x ? y : x / y >= x ? y : x): NaN 이 섞일 때도 Swift 와 같은 쪽을 고른다
private fun mcMin(x: Double, y: Double): Double = if (y < x) y else x
private fun mcMax(x: Double, y: Double): Double = if (y >= x) y else x
// Darwin pow(x, 2) 는 x*x 를 바르게 반올림한 값. 안드로이드 pow 는 마지막 자리가 다를 수 있어 곱으로
private fun mcPow(x: Double, n: Int): Double = if (n == 2) x * x else Math.pow(x, n.toDouble())

/** Swift Double(String): 자바만 받는 꼴(1d, 1f, Infinity 대소문자)을 걸러 낸다 */
private fun swiftDouble(t: String): Double? {
    val l = t.lowercase(Locale.ROOT)
    val neg = l.startsWith("-")
    val body = if (l.startsWith("+") || l.startsWith("-")) l.substring(1) else l
    if (body == "inf" || body == "infinity") return if (neg) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
    if (body == "nan") return Double.NaN
    if (body.isEmpty() || (body.last() in "df" && !body.startsWith("0x"))) return null
    if (body.contains("inf") || body.contains("nan")) return null
    return t.toDoubleOrNull()
}

/** Swift String(Double) 표기 (5.0 · 0.0001 · 1e-05 · 1e+16) */
private fun swiftDesc(d: Double): String {
    if (d.isNaN()) return "nan"
    if (d.isInfinite()) return if (d > 0) "inf" else "-inf"
    if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
    val u = BigDecimal(d.toString()).stripTrailingZeros()
    val a = abs(d)
    if (a >= 1e-4 && a < 9007199254740992.0) {
        val s = u.toPlainString()
        return if (s.contains('.')) s else "$s.0"
    }
    val digits = u.unscaledValue().abs().toString()
    val e = digits.length - 1 - u.scale()
    val mant = digits.take(1) + (if (digits.length > 1) "." + digits.drop(1) else "")
    return (if (d < 0) "-" else "") + mant + "e" + (if (e < 0) "-" else "+") + abs(e).toString().padStart(2, '0')
}

// ios: Data/MonteCarlo.swift:8
object MC {
    const val TD = 252.0   // ios: Data/MonteCarlo.swift:9

    // 국내 주식 합계 기록 (웹 model.js krNorm·krAt 과 같은 식, 사이트 kr_log 칸)
    @Serializable data class KrDay(var d: String, var v: Double)   // ios: Data/MonteCarlo.swift:12
    /** 하루 한 줄(같은 날은 뒤에 온 값), 날짜순, 최대 1,200줄. 금액은 원 단위로 반올림 */
    fun krNorm(log: List<KrDay>): List<KrDay> {   // ios: Data/MonteCarlo.swift:14
        val m = HashMap<String, Double>()   // ios: Data/MonteCarlo.swift:15
        for (x in log) if (x.d.length == 10 && x.v.isFinite() && x.v >= 0) m[x.d] = mcRound(x.v)   // ios: Data/MonteCarlo.swift:16
        return m.keys.sorted().takeLast(1200).map { KrDay(d = it, v = m[it]!!) }   // ios: Data/MonteCarlo.swift:17
    }
    /** 그날 금액: 그날까지 마지막으로 넣은 금액. 첫 기록 전은 0 (처음 넣은 금액은 들어온 돈) */
    fun krAt(log: List<KrDay>, d: String): Double {   // ios: Data/MonteCarlo.swift:20
        var v = 0.0   // ios: Data/MonteCarlo.swift:21
        for (x in log) { if (x.d > d) break; v = x.v }   // ios: Data/MonteCarlo.swift:22
        return v   // ios: Data/MonteCarlo.swift:23
    }

    // 사이트 통계 (data/stats.json)
    @Serializable data class Stats(   // ios: Data/MonteCarlo.swift:27
        var date: String,   // ios: Data/MonteCarlo.swift:31
        var defaults: Defaults,   // ios: Data/MonteCarlo.swift:32
        var tickers: Map<String, Ticker>,   // ios: Data/MonteCarlo.swift:33
        var corr: Corr,   // ios: Data/MonteCarlo.swift:34
        // 웹 첫 방문 전망 설정 · 기본 사건 · 요인 민감도 β[요인][종목] (예전 파일에는 없다)
        var model: Settings? = null,   // ios: Data/MonteCarlo.swift:36
        var events: List<Event>? = null,   // ios: Data/MonteCarlo.swift:37
        var betas: Map<String, Map<String, Double>>? = null,   // ios: Data/MonteCarlo.swift:38
        // 요인 민감도의 신뢰도 [요인][종목]: corr 상관, agree 같은 방향 비율("10번 중 N번"), n 날 수
        var betaStat: Map<String, Map<String, BetaStat>>? = null,   // ios: Data/MonteCarlo.swift:40
        // 요인별 보통 한 달 변화 (21거래일 변화 표준편차 sd · 가장 큰 변화 max. 금리는 bp, 나머지는 로그 변화)
        var factors: Map<String, FactorMove>? = null,   // ios: Data/MonteCarlo.swift:42
    ) {
        @Serializable data class Ticker(var mu: Double? = null, var trend: Double? = null, var gs: Double? = null, var vol: Double? = null, var n: Int? = null, var cash: Boolean? = null, var fx: Boolean? = null)   // ios: Data/MonteCarlo.swift:28
        @Serializable data class Defaults(var mu: Double, var vol: Double, var corr: Double)   // ios: Data/MonteCarlo.swift:29
        @Serializable data class Corr(var keys: List<String>, var m: List<List<Double>>)   // ios: Data/MonteCarlo.swift:30
    }
    @Serializable data class BetaStat(var corr: Double? = null, var agree: Double? = null, var n: Double? = null)   // ios: Data/MonteCarlo.swift:44
    @Serializable data class FactorMove(var sd: Double, var max: Double, var n: Double? = null)   // ios: Data/MonteCarlo.swift:45

    /** 전망 설정 (웹 state.model 과 같은 칸). 없는 칸은 웹 기본값. 읽기는 McSettingsSerializer (숫자 칸은 너그럽게) */
    @Serializable(with = McSettingsSerializer::class) data class Settings(   // ios: Data/MonteCarlo.swift:48
        var scenario: String? = null,   // ios: Data/MonteCarlo.swift:49
        var trust: Double? = null,   // ios: Data/MonteCarlo.swift:50
        var n_paths: Double? = null,   // ios: Data/MonteCarlo.swift:51
        var seed: Double? = null,   // ios: Data/MonteCarlo.swift:52
        var t_dof: Double? = null,   // ios: Data/MonteCarlo.swift:53
        var earnings_adjust: Boolean? = null,   // ios: Data/MonteCarlo.swift:54
        var fx_drift: Double? = null,   // ios: Data/MonteCarlo.swift:55
        var fx_vol_mult: Double? = null,   // ios: Data/MonteCarlo.swift:56
        var prior_mu: Double? = null,   // ios: Data/MonteCarlo.swift:57
        var default_vol: Double? = null,   // ios: Data/MonteCarlo.swift:58
        var default_corr: Double? = null,   // ios: Data/MonteCarlo.swift:59
    ) {   // ios: Data/MonteCarlo.swift:60 init() = 모든 칸 기본값(null) 인 Settings()
        /** 사이트 입력값의 설정을 위에 덮는다 (기기 동기화를 켰을 때) */
        fun merged(o: Settings?): Settings {   // ios: Data/MonteCarlo.swift:70
            if (o == null) return this   // ios: Data/MonteCarlo.swift:71
            val s = copy()   // ios: Data/MonteCarlo.swift:72
            s.scenario = o.scenario ?: s.scenario; s.trust = o.trust ?: s.trust; s.n_paths = o.n_paths ?: s.n_paths; s.seed = o.seed ?: s.seed   // ios: Data/MonteCarlo.swift:73
            s.t_dof = o.t_dof ?: s.t_dof; s.earnings_adjust = o.earnings_adjust ?: s.earnings_adjust; s.fx_drift = o.fx_drift ?: s.fx_drift   // ios: Data/MonteCarlo.swift:74
            s.fx_vol_mult = o.fx_vol_mult ?: s.fx_vol_mult; s.prior_mu = o.prior_mu ?: s.prior_mu; s.default_vol = o.default_vol ?: s.default_vol   // ios: Data/MonteCarlo.swift:75
            s.default_corr = o.default_corr ?: s.default_corr   // ios: Data/MonteCarlo.swift:76
            return s   // ios: Data/MonteCarlo.swift:77
        }
        val nPaths: Int get() = max(1, (n_paths ?: 3000.0).toInt())   // ios: Data/MonteCarlo.swift:79
        // 웹: Number(seed) || 1, >>> 0
        val seedU32: UInt get() { val s = seed ?: 1.0; return if (s == 0.0 || !s.isFinite()) 1.toUInt() else s.toLong().toUInt() }   // ios: Data/MonteCarlo.swift:80
        val dof: Double get() = t_dof?.let { if (it == 0.0 || !it.isFinite()) null else it } ?: 5.0   // ios: Data/MonteCarlo.swift:81
    }

    /** 사건 하나 (웹 state.events 한 줄). 숫자 칸은 웹처럼 너그럽게 읽는다 (문자열 숫자, 빈 칸): McEventSerializer */
    @Serializable(with = McEventSerializer::class) data class Event(   // ios: Data/MonteCarlo.swift:85
        var id: String? = null,   // ios: Data/MonteCarlo.swift:86
        var on: Boolean = false,   // ios: Data/MonteCarlo.swift:87
        var date: String? = null,   // ios: Data/MonteCarlo.swift:88
        var target: String? = null,   // ios: Data/MonteCarlo.swift:89
        var factor: String? = null,   // ios: Data/MonteCarlo.swift:90
        var cat: String? = null,   // ios: Data/MonteCarlo.swift:91
        var rep: String? = null,   // ios: Data/MonteCarlo.swift:92
        var prob: Double = 100.0,   // ios: Data/MonteCarlo.swift:93 % (웹: 값이 없거나 숫자가 아니면 100)
        var mean: Double = 0.0,   // ios: Data/MonteCarlo.swift:94 % 또는 bp
        var sd: Double = 0.0,   // ios: Data/MonteCarlo.swift:95
        var volMult: Double = 0.0,   // ios: Data/MonteCarlo.swift:96
        var volDays: Double = 0.0,   // ios: Data/MonteCarlo.swift:97
    )

    /** JS Number(문자열) 근사: 앞뒤 공백을 빼고 숫자로 읽는다 */
    fun jsNumber(s: String): Double? {   // ios: Data/MonteCarlo.swift:122
        val t = s.trim()   // ios: Data/MonteCarlo.swift:123
        return if (t.isEmpty()) 0.0 else swiftDouble(t)   // ios: Data/MonteCarlo.swift:124
    }

    // 날짜 (yyyy-MM-dd, UTC 날짜 수)
    fun dayNum(s: String): Int {   // ios: Data/MonteCarlo.swift:128
        val p = s.split("-").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }   // ios: Data/MonteCarlo.swift:129 (Swift split 은 빈 조각을 뺀다)
        if (p.size != 3) return 0   // ios: Data/MonteCarlo.swift:130
        return civilDays(p[0], p[1], p[2])   // ios: Data/MonteCarlo.swift:131
    }
    fun civilDays(y0: Int, m: Int, d: Int): Int {   // ios: Data/MonteCarlo.swift:133
        val y = if (m <= 2) y0 - 1 else y0   // ios: Data/MonteCarlo.swift:134
        val era = (if (y >= 0) y else y - 399) / 400   // ios: Data/MonteCarlo.swift:135
        val yoe = y - era * 400; val mp = (m + 9) % 12   // ios: Data/MonteCarlo.swift:136
        val doy = (153 * mp + 2) / 5 + d - 1   // ios: Data/MonteCarlo.swift:137
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy   // ios: Data/MonteCarlo.swift:138
        return era * 146097 + doe - 719468   // ios: Data/MonteCarlo.swift:139
    }
    fun civil(z0: Int): Triple<Int, Int, Int> {   // ios: Data/MonteCarlo.swift:141
        val z = z0 + 719468   // ios: Data/MonteCarlo.swift:142
        val era = (if (z >= 0) z else z - 146096) / 146097   // ios: Data/MonteCarlo.swift:143
        val doe = z - era * 146097   // ios: Data/MonteCarlo.swift:144
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365   // ios: Data/MonteCarlo.swift:145
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)   // ios: Data/MonteCarlo.swift:146
        val mp = (5 * doy + 2) / 153   // ios: Data/MonteCarlo.swift:147
        val d = doy - (153 * mp + 2) / 5 + 1; val m = if (mp < 10) mp + 3 else mp - 9   // ios: Data/MonteCarlo.swift:148
        return Triple(yoe + era * 400 + (if (m <= 2) 1 else 0), m, d)   // ios: Data/MonteCarlo.swift:149
    }
    fun dayStr(z: Int): String {   // ios: Data/MonteCarlo.swift:151
        val (y, m, d) = civil(z)   // ios: Data/MonteCarlo.swift:152
        return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)   // ios: Data/MonteCarlo.swift:153
    }
    /** 웹 addMonths: 같은 날짜, 그 달에 없으면 말일 */
    fun addMonths(s: String, n: Int): String {   // ios: Data/MonteCarlo.swift:156
        val p = s.split("-").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }   // ios: Data/MonteCarlo.swift:157
        if (p.size != 3) return s   // ios: Data/MonteCarlo.swift:158
        val t = p[0] * 12 + (p[1] - 1) + n   // ios: Data/MonteCarlo.swift:159
        val y = floor(t.toDouble() / 12).toInt(); val m = t - y * 12 + 1   // ios: Data/MonteCarlo.swift:160
        val last = civilDays(if (m == 12) y + 1 else y, if (m == 12) 1 else m + 1, 1) - civilDays(y, m, 1)   // ios: Data/MonteCarlo.swift:161
        return dayStr(civilDays(y, m, min(p[2], last)))   // ios: Data/MonteCarlo.swift:162
    }
    /** 0 = 일요일 … 6 = 토요일 */
    fun weekday(z: Int): Int = ((z % 7) + 7 + 4) % 7   // ios: Data/MonteCarlo.swift:165

    val perYear: Map<String, Double> = mapOf("monthly" to 12.0, "6w" to 365.25 / 42, "quarterly" to 4.0, "semi" to 2.0, "yearly" to 1.0)   // ios: Data/MonteCarlo.swift:167
    val stepMonths: Map<String, Int> = mapOf("monthly" to 1, "quarterly" to 3, "semi" to 6, "yearly" to 12)   // ios: Data/MonteCarlo.swift:168
    /** 사건 반복 날짜 (startDate 초과 ~ goalDate 이하) */
    fun occurrences(e: Event, start: String, goal: String): List<String> {   // ios: Data/MonteCarlo.swift:170
        val date = e.date ?: return emptyList()   // ios: Data/MonteCarlo.swift:171
        val out = ArrayList<String>()   // ios: Data/MonteCarlo.swift:172
        for (k in 0 until 600) {   // ios: Data/MonteCarlo.swift:173
            val d = if (e.rep == "6w") dayStr(dayNum(date) + 42 * k) else addMonths(date, k * (stepMonths[e.rep ?: ""] ?: 0))   // ios: Data/MonteCarlo.swift:174
            if (d > goal) break   // ios: Data/MonteCarlo.swift:175
            if (d > start) out.add(d)   // ios: Data/MonteCarlo.swift:176
            if (perYear[e.rep ?: ""] == null) break   // ios: Data/MonteCarlo.swift:177
        }
        return out   // ios: Data/MonteCarlo.swift:179
    }

    // 난수 (웹 makeRng 와 같은 순서). UInt 곱·자리 옮김은 32비트에서 넘침을 버린다 (Swift &* 와 같다)
    class Rng(seed: UInt) {   // ios: Data/MonteCarlo.swift:183
        var a: UInt = seed; var b: UInt = 0x9e3779b9.toUInt(); var c: UInt = 0x243f6a88.toUInt(); var d: UInt = 0xb7e15162.toUInt()   // ios: Data/MonteCarlo.swift:184
        var spare = 0.0; var hasSpare = false   // ios: Data/MonteCarlo.swift:185
        init { a = seed; for (i in 0 until 20) next() }   // ios: Data/MonteCarlo.swift:186
        companion object { fun rotl(x: UInt, k: Int): UInt = (x shl k) or (x shr (32 - k)) }   // ios: Data/MonteCarlo.swift:187
        fun next(): Double {   // ios: Data/MonteCarlo.swift:188
            val r = rotl(b * 5.toUInt(), 7) * 9.toUInt()   // ios: Data/MonteCarlo.swift:189
            val t = b shl 9   // ios: Data/MonteCarlo.swift:190
            c = c xor a; d = d xor b; b = b xor c; a = a xor d; c = c xor t; d = rotl(d, 11)   // ios: Data/MonteCarlo.swift:191
            return r.toDouble() / 4294967296.0   // ios: Data/MonteCarlo.swift:192
        }
        fun normal(): Double {   // ios: Data/MonteCarlo.swift:194
            if (hasSpare) { hasSpare = false; return spare }   // ios: Data/MonteCarlo.swift:195
            var u = 0.0   // ios: Data/MonteCarlo.swift:196
            while (u == 0.0) u = next()   // ios: Data/MonteCarlo.swift:197
            val v = next(); val r = sqrt(-2 * ln(u)); val th = 2 * PI * v   // ios: Data/MonteCarlo.swift:198
            spare = r * sin(th); hasSpare = true   // ios: Data/MonteCarlo.swift:199
            return r * cos(th)   // ios: Data/MonteCarlo.swift:200
        }
    }

    fun cholesky(A: Array<DoubleArray>): Array<DoubleArray>? {   // ios: Data/MonteCarlo.swift:204
        val n = A.size   // ios: Data/MonteCarlo.swift:205
        val L = Array(n) { DoubleArray(n) { 0.0 } }   // ios: Data/MonteCarlo.swift:206
        for (i in 0 until n) {   // ios: Data/MonteCarlo.swift:207
            for (j in 0..i) {   // ios: Data/MonteCarlo.swift:208
                var s = A[i][j]   // ios: Data/MonteCarlo.swift:209
                for (k in 0 until j) s -= L[i][k] * L[j][k]   // ios: Data/MonteCarlo.swift:210
                if (i == j) { if (s <= 1e-10) return null; L[i][i] = sqrt(s) }   // ios: Data/MonteCarlo.swift:211
                else L[i][j] = s / L[j][j]   // ios: Data/MonteCarlo.swift:212
            }
        }
        return L   // ios: Data/MonteCarlo.swift:215
    }

    // 모형 만들기 (웹 buildModel, 종목 값은 stats.json)
    @Serializable data class Holding(var key: String, var ccy: String, var valueKrw: Double, var price0: Double)   // ios: Data/MonteCarlo.swift:219
    @Serializable data class Factor(   // ios: Data/MonteCarlo.swift:220
        var key: String,   // ios: Data/MonteCarlo.swift:221
        var isFx: Boolean,   // ios: Data/MonteCarlo.swift:222
        var cash: Boolean,   // ios: Data/MonteCarlo.swift:223
        var vol: Double,   // ios: Data/MonteCarlo.swift:224
        var base: Double,   // ios: Data/MonteCarlo.swift:225
        var smooth: Double,   // ios: Data/MonteCarlo.swift:226
        var blend: Double,   // ios: Data/MonteCarlo.swift:227
        var evVar: Double,   // ios: Data/MonteCarlo.swift:228
        var evVarCat: Map<String, Double>,   // ios: Data/MonteCarlo.swift:229
        var evCats: List<String>,   // ios: Data/MonteCarlo.swift:230 evVarCat 을 더한 순서 (웹 객체 순서)
    )
    class Ev(val prob: Double, val jm: Double, val js: Double, val vm: Double, val vd: Int, val targets: IntArray, val scales: DoubleArray, val cat: String)   // ios: Data/MonteCarlo.swift:232
    class Model(   // ios: Data/MonteCarlo.swift:233
        var factors: List<Factor>,   // ios: Data/MonteCarlo.swift:234
        var L: Array<DoubleArray>,   // ios: Data/MonteCarlo.swift:235
        var days: List<String>,   // ios: Data/MonteCarlo.swift:236
        var monthIdx: List<Int>,   // ios: Data/MonteCarlo.swift:237
        var monthDates: List<String>,   // ios: Data/MonteCarlo.swift:238
        var byDay: Map<Int, List<Ev>>,   // ios: Data/MonteCarlo.swift:239
        var eventCount: Int,   // ios: Data/MonteCarlo.swift:240
        var start: String,   // ios: Data/MonteCarlo.swift:241
        var goal: String,   // ios: Data/MonteCarlo.swift:242
        var trust: Double,   // ios: Data/MonteCarlo.swift:243
    )

    fun fxOf(ccy: String): String? = if (ccy.isEmpty() || ccy == "KRW") null else if (ccy == "USD") "KRW=X" else ccy + "KRW=X"   // ios: Data/MonteCarlo.swift:246
    fun blendMu(b: Double, s: Double, a: Double): Double = exp((1 - a) * ln(1 + b) + a * ln(1 + s)) - 1   // ios: Data/MonteCarlo.swift:247

    /** trust: 추세 신뢰 % (내 관점 섞기). 사건은 설정 events (없으면 stats 기본 사건), β 는 stats */
    fun build(holdings: List<Holding>, stats: Stats, settings: Settings, events: List<Event>, start: String, goal: String): Model? {   // ios: Data/MonteCarlo.swift:250
        val st = stats; val m = settings
        if (holdings.isEmpty() || goal <= start) return null   // ios: Data/MonteCarlo.swift:251
        val prior = (m.prior_mu ?: 10.0) / 100; val defVol = (m.default_vol ?: 40.0) / 100; val defCorr = m.default_corr ?: 0.3   // ios: Data/MonteCarlo.swift:252
        val fxDrift = (m.fx_drift ?: 0.0) / 100; val fxMult = m.fx_vol_mult?.let { if (it == 0.0) null else it } ?: 1.0   // ios: Data/MonteCarlo.swift:253
        val newFormat = st.model != null   // ios: Data/MonteCarlo.swift:254
        // 요인: 보유 종목 + 쓰이는 환율
        val fs = ArrayList<Factor>(); val known = ArrayList<Boolean>(); val ns = ArrayList<Int>(); val gs = ArrayList<Double?>(); val trend = ArrayList<Double?>()   // ios: Data/MonteCarlo.swift:256
        for (h in holdings) {   // ios: Data/MonteCarlo.swift:257
            val t = st.tickers[h.key]; val mu = t?.mu; val vol = t?.vol   // ios: Data/MonteCarlo.swift:258
            if (t != null && t.fx != true && mu != null && vol != null) {
                fs.add(Factor(key = h.key, isFx = false, cash = t.cash ?: false, vol = vol, base = mu, smooth = mu, blend = mu, evVar = 0.0, evVarCat = emptyMap(), evCats = emptyList()))   // ios: Data/MonteCarlo.swift:259
                known.add(true); ns.add(t.n ?: TD.toInt()); gs.add(t.gs); trend.add(t.trend)   // ios: Data/MonteCarlo.swift:260
            } else {   // ios: Data/MonteCarlo.swift:261
                fs.add(Factor(key = h.key, isFx = false, cash = false, vol = defVol, base = prior, smooth = prior, blend = prior, evVar = 0.0, evVarCat = emptyMap(), evCats = emptyList()))   // ios: Data/MonteCarlo.swift:262
                known.add(false); ns.add(0); gs.add(null); trend.add(null)   // ios: Data/MonteCarlo.swift:263
            }
        }
        val fxKeys = ArrayList<String>()   // ios: Data/MonteCarlo.swift:266
        for (h in holdings) { val k = fxOf(h.ccy); if (k != null && !fxKeys.contains(k)) fxKeys.add(k) }   // ios: Data/MonteCarlo.swift:267
        for (k in fxKeys) {   // ios: Data/MonteCarlo.swift:268
            val vol = st.tickers[k]?.vol ?: (0.10 * fxMult)   // ios: Data/MonteCarlo.swift:269
            fs.add(Factor(key = k, isFx = true, cash = false, vol = vol, base = fxDrift, smooth = fxDrift, blend = fxDrift, evVar = 0.0, evVarCat = emptyMap(), evCats = emptyList()))   // ios: Data/MonteCarlo.swift:270
            known.add(st.tickers[k] != null); ns.add(0); gs.add(null); trend.add(null)   // ios: Data/MonteCarlo.swift:271
        }
        val F = fs.size   // ios: Data/MonteCarlo.swift:273
        // 상관: 사이트 표 (같은 계산), 표에 없으면 웹 사전값 (위험 종목끼리 기본 상관, 현금성·환율이 끼면 0)
        val ci = HashMap<String, Int>(); st.corr.keys.forEachIndexed { i, k -> if (!ci.containsKey(k)) ci[k] = i }   // ios: Data/MonteCarlo.swift:275 같은 키는 앞의 것
        val C = Array(F) { DoubleArray(F) { 0.0 } }   // ios: Data/MonteCarlo.swift:276
        for (i in 0 until F) {   // ios: Data/MonteCarlo.swift:277
            C[i][i] = 1.0   // ios: Data/MonteCarlo.swift:278
            for (j in 0 until i) {   // ios: Data/MonteCarlo.swift:279
                var c: Double   // ios: Data/MonteCarlo.swift:280
                val a = ci[fs[i].key]; val b = ci[fs[j].key]   // ios: Data/MonteCarlo.swift:281
                if (known[i] && known[j] && a != null && b != null && a < st.corr.m.size && b < st.corr.m[a].size) c = st.corr.m[a][b]
                else c = if (!(fs[i].cash || fs[j].cash || fs[i].isFx || fs[j].isFx)) defCorr else 0.0   // ios: Data/MonteCarlo.swift:282
                c = mcMax(-0.95, mcMin(0.95, c))   // ios: Data/MonteCarlo.swift:283
                C[i][j] = c; C[j][i] = c   // ios: Data/MonteCarlo.swift:284
            }
        }
        var lam = 0.0; var L: Array<DoubleArray>? = null   // ios: Data/MonteCarlo.swift:287
        while (L == null) {   // ios: Data/MonteCarlo.swift:288
            val S = Array(F) { C[it].copyOf() }   // ios: Data/MonteCarlo.swift:289
            for (i in 0 until F) { for (j in 0 until F) if (i != j) S[i][j] = C[i][j] * (1 - lam) }   // ios: Data/MonteCarlo.swift:290
            L = cholesky(S)   // ios: Data/MonteCarlo.swift:291
            if (L == null) lam += 0.05   // ios: Data/MonteCarlo.swift:292
        }

        // 미래 거래일(평일)과 월 경계. monthIdx 는 경로 배열 기준 (0 = 시작, i+1 = days[i] 장마감)
        val days = ArrayList<String>()   // ios: Data/MonteCarlo.swift:296
        val g0 = dayNum(goal)   // ios: Data/MonteCarlo.swift:297
        var z = dayNum(start) + 1   // ios: Data/MonteCarlo.swift:298
        while (z <= g0) { val w = weekday(z); if (w != 0 && w != 6) days.add(dayStr(z)); z += 1 }   // ios: Data/MonteCarlo.swift:299
        if (days.isEmpty()) return null   // ios: Data/MonteCarlo.swift:300
        val monthIdx = arrayListOf(0); val monthDates = arrayListOf(start)   // ios: Data/MonteCarlo.swift:301
        for (i in 1 until days.size) if (days[i].take(7) != days[i - 1].take(7)) { monthIdx.add(i + 1); monthDates.add(days[i]) }   // ios: Data/MonteCarlo.swift:302
        if (monthIdx.last() != days.size) { monthIdx.add(days.size); monthDates.add(days[days.size - 1]) }   // ios: Data/MonteCarlo.swift:303

        // 사건 → 거래일 번호. 요인 사건은 종목별 민감도만큼 나눠 준다 (금리는 bp)
        fun dayOf(s: String): Int {   // ios: Data/MonteCarlo.swift:306
            var lo = 0; var hi = days.size   // ios: Data/MonteCarlo.swift:307
            while (lo < hi) { val mid = (lo + hi) shr 1; if (days[mid] < s) lo = mid + 1 else hi = mid }   // ios: Data/MonteCarlo.swift:308
            return lo   // ios: Data/MonteCarlo.swift:309
        }
        val keyIdx = HashMap<String, Int>(); fs.forEachIndexed { i, f -> keyIdx[f.key] = i }   // ios: Data/MonteCarlo.swift:311 웹 Map 처럼 같은 키는 뒤의 것
        val betas = st.betas ?: emptyMap()   // ios: Data/MonteCarlo.swift:312
        val byDay = HashMap<Int, MutableList<Ev>>(); var count = 0   // ios: Data/MonteCarlo.swift:313
        for (e in events) {   // ios: Data/MonteCarlo.swift:314
            val date = e.date; if (!e.on || date == null || date.isEmpty()) continue   // ios: Data/MonteCarlo.swift:315
            var targets = ArrayList<Int>(); var scales = ArrayList<Double>()   // ios: Data/MonteCarlo.swift:316
            val fk = e.factor; if (fk != null && fk.isNotEmpty() && fk != "none") {   // ios: Data/MonteCarlo.swift:317
                val bt = betas[fk] ?: emptyMap()   // ios: Data/MonteCarlo.swift:318
                for ((i, f) in fs.withIndex()) if (!f.isFx && !f.cash) { val b = bt[f.key]; if (b != null && abs(b) > 1e-6) { targets.add(i); scales.add(b) } }   // ios: Data/MonteCarlo.swift:319
            } else {   // ios: Data/MonteCarlo.swift:320
                if (e.target == "ALL") targets = ArrayList(fs.indices.filter { !fs[it].isFx && !fs[it].cash })   // ios: Data/MonteCarlo.swift:321
                else if (e.target == "FX") { val i = keyIdx[fxOf("USD")!!]; if (i != null) targets = arrayListOf(i) }   // ios: Data/MonteCarlo.swift:322
                else { val t = e.target; val i = if (t != null) keyIdx[t] else null; if (i != null && !fs[i].isFx) targets = arrayListOf(i) }   // ios: Data/MonteCarlo.swift:323
                scales = ArrayList(targets.map { 1.0 })   // ios: Data/MonteCarlo.swift:324
            }
            if (targets.isEmpty()) continue   // ios: Data/MonteCarlo.swift:326
            val lin = e.factor == "rate"   // ios: Data/MonteCarlo.swift:327
            val sd = mcMax(0.0, e.sd) / (if (lin) 1 else 100); val mn = e.mean / (if (lin) 1 else 100)   // ios: Data/MonteCarlo.swift:328
            val prob = mcMin(1.0, mcMax(0.0, e.prob / 100))   // ios: Data/MonteCarlo.swift:329
            val ev = Ev(prob = prob, jm = if (lin) mn else ln(mcMax(0.01, 1 + mn)) - 0.5 * sd * sd, js = sd,   // ios: Data/MonteCarlo.swift:330
                        vm = mcMax(0.1, if (e.volMult == 0.0 || e.volMult.isNaN()) 1.0 else e.volMult),   // ios: Data/MonteCarlo.swift:331
                        vd = max(0, floor(if (e.volDays.isNaN()) 0.0 else e.volDays + 0.5).toInt()), targets = targets.toIntArray(), scales = scales.toDoubleArray(),   // ios: Data/MonteCarlo.swift:332
                        cat = if ((e.cat ?: "").isEmpty()) "corp" else e.cat!!)   // ios: Data/MonteCarlo.swift:333
            for (d in occurrences(e, start, goal)) {   // ios: Data/MonteCarlo.swift:334
                val di = dayOf(d); if (di >= days.size) break   // ios: Data/MonteCarlo.swift:335
                byDay.getOrPut(di) { ArrayList() }.add(ev); count += 1   // ios: Data/MonteCarlo.swift:336
            }
            // 반복 사건은 과거 변동성에 이미 들어 있으므로 평소 변동성에서 그만큼 뺀다 (요인 묶음별로 기록)
            val fr = perYear[e.rep ?: ""] ?: 0.0   // ios: Data/MonteCarlo.swift:339
            if ((m.earnings_adjust ?: true) && fr != 0.0) {   // ios: Data/MonteCarlo.swift:340
                for ((k, t) in targets.withIndex()) {   // ios: Data/MonteCarlo.swift:341
                    val v = fr * ev.prob * mcPow(scales[k] * sd, 2)   // ios: Data/MonteCarlo.swift:342
                    if (fs[t].evVarCat[ev.cat] == null) fs[t].evCats = fs[t].evCats + ev.cat   // ios: Data/MonteCarlo.swift:343
                    fs[t].evVarCat = fs[t].evVarCat + (ev.cat to ((fs[t].evVarCat[ev.cat] ?: 0.0) + v))   // ios: Data/MonteCarlo.swift:344
                }
            }
        }
        val a = (m.trust ?: 50.0) / 100   // ios: Data/MonteCarlo.swift:348
        for (i in 0 until F) {   // ios: Data/MonteCarlo.swift:349
            val f = fs[i]   // ios: Data/MonteCarlo.swift:350
            val cats = f.evVarCat   // ios: Data/MonteCarlo.swift:351
            f.evVar = f.evCats.fold(0.0) { s, c -> s + cats[c]!! }   // ios: Data/MonteCarlo.swift:352
            val volDiff = if (f.evVar != 0.0) sqrt(mcMax(f.vol * f.vol - f.evVar, 0.5 * f.vol * f.vol)) else f.vol   // ios: Data/MonteCarlo.swift:353
            // 추종 시나리오: 3년 직선 기울기 gs 를 중앙값 성장률로 이어 가는 산술 기대수익 (이력이 1년보다 짧으면 기준과 섞는다)
            if (!(f.isFx || f.cash || ns[i] < 5 || !known[i])) {   // ios: Data/MonteCarlo.swift:355
                val w = mcMin(1.0, ns[i].toDouble() / TD)   // ios: Data/MonteCarlo.swift:356
                if (newFormat) { val g = gs[i]; if (g != null) f.smooth = w * (exp(mcMax(-0.9, mcMin(1.2, g)) + 0.5 * volDiff * volDiff) - 1) + (1 - w) * f.base }   // ios: Data/MonteCarlo.swift:357
                else { val t = trend[i]; if (t != null) f.smooth = t }   // ios: Data/MonteCarlo.swift:358
            }
            f.blend = blendMu(f.base, f.smooth, a)   // ios: Data/MonteCarlo.swift:360
            fs[i] = f   // ios: Data/MonteCarlo.swift:361
        }
        return Model(factors = fs, L = L!!, days = days, monthIdx = monthIdx, monthDates = monthDates, byDay = byDay, eventCount = count,   // ios: Data/MonteCarlo.swift:363
                     start = start, goal = goal, trust = a * 100)   // ios: Data/MonteCarlo.swift:364
    }

    // 몬테카를로 (웹 simulate)
    @Serializable data class Terminal(var p5: Double, var p25: Double, var p50: Double, var p75: Double, var p95: Double, var mean: Double)   // ios: Data/MonteCarlo.swift:368
    @Serializable data class Year(var year: Int, var date: String, var p: Double, var k: Int)   // ios: Data/MonteCarlo.swift:369
    /** 결과 요약: 월 경계마다 평가액 백분위(5·25·50·75·95), 목표 확률 등 */
    @Serializable data class Result(   // ios: Data/MonteCarlo.swift:371
        var V0: Double,   // ios: Data/MonteCarlo.swift:372
        var bands: List<List<Double>>,   // ios: Data/MonteCarlo.swift:373
        var pGoal: Double,   // ios: Data/MonteCarlo.swift:374
        var pTouch: Double,   // ios: Data/MonteCarlo.swift:375
        var terminal: Terminal,   // ios: Data/MonteCarlo.swift:376
        var mddMedian: Double,   // ios: Data/MonteCarlo.swift:377
        var byYear: List<Year>,   // ios: Data/MonteCarlo.swift:378
        // cumHit[k]: k번째 월 경계까지 한 번이라도 목표 이상이었던 경로 비율 (reweight 만)
        var cumHit: List<Double>,   // ios: Data/MonteCarlo.swift:380
    )
    /** 다시 무게 주기(reweight)에 쓰는 경로 (재조정 없음). cg[a][k*P+p] = 그 달까지 종목+환율 누적 로그수익 (Float32, 웹과 같다).
     *  저장(파일 캐시)은 ForecastEngine.kt 의 SimSerializer */
    @Serializable(with = SimSerializer::class) class Sim(   // ios: Data/MonteCarlo.swift:383
        val A: Int, val M: Int, val P: Int,   // ios: Data/MonteCarlo.swift:384
        val cg: List<FloatArray>,   // ios: Data/MonteCarlo.swift:385
        val muD: List<Double>,   // ios: Data/MonteCarlo.swift:386
        val monthIdx: List<Int>,   // ios: Data/MonteCarlo.swift:387
        val monthDates: List<String>,   // ios: Data/MonteCarlo.swift:388
        val v0: List<Double>,   // ios: Data/MonteCarlo.swift:389
        val start: String,   // ios: Data/MonteCarlo.swift:390
        val summary: Result,   // ios: Data/MonteCarlo.swift:391
    )

    val qs = doubleArrayOf(0.05, 0.25, 0.5, 0.75, 0.95)   // ios: Data/MonteCarlo.swift:394
    fun quantileSorted(s: DoubleArray, q: Double): Double {   // ios: Data/MonteCarlo.swift:395
        val pos = (s.size - 1).toDouble() * q; val lo = floor(pos).toInt(); val hi = ceil(pos).toInt()   // ios: Data/MonteCarlo.swift:396
        return s[lo] + (s[hi] - s[lo]) * (pos - lo.toDouble())   // ios: Data/MonteCarlo.swift:397
    }
    fun quantileSorted(s: List<Double>, q: Double): Double = quantileSorted(s.toDoubleArray(), q)   // ios: Data/MonteCarlo.swift:399
    fun bandsOf(arr: DoubleArray, M: Int, P: Int): List<List<Double>> {   // ios: Data/MonteCarlo.swift:400
        val out = List(qs.size) { ArrayList<Double>(M) }   // ios: Data/MonteCarlo.swift:401
        val tmp = DoubleArray(P) { 0.0 }   // ios: Data/MonteCarlo.swift:402
        for (k in 0 until M) {   // ios: Data/MonteCarlo.swift:403
            for (p in 0 until P) tmp[p] = arr[k * P + p]   // ios: Data/MonteCarlo.swift:404
            tmp.sort()   // ios: Data/MonteCarlo.swift:405
            for (i in qs.indices) out[i].add(quantileSorted(tmp, qs[i]))   // ios: Data/MonteCarlo.swift:406
        }
        return out   // ios: Data/MonteCarlo.swift:408
    }
    fun bandsOf(arr: List<Double>, M: Int, P: Int): List<List<Double>> = bandsOf(arr.toDoubleArray(), M, P)

    fun terminal(sorted: DoubleArray): Terminal {   // ios: Data/MonteCarlo.swift:410
        var sum = 0.0; for (x in sorted) sum += x   // ios: Data/MonteCarlo.swift:411
        return Terminal(p5 = quantileSorted(sorted, 0.05), p25 = quantileSorted(sorted, 0.25), p50 = quantileSorted(sorted, 0.5),   // ios: Data/MonteCarlo.swift:412
                        p75 = quantileSorted(sorted, 0.75), p95 = quantileSorted(sorted, 0.95), mean = sum / sorted.size.toDouble())   // ios: Data/MonteCarlo.swift:413
    }
    fun terminal(sorted: List<Double>): Terminal = terminal(sorted.toDoubleArray())

    /** mu: 요인별 연 기대수익 (시나리오), withEvents: 사건 점프를 넣을지.
     *  stop: 앱이 뒤로 가면 true (경로 32개마다 보고 멈춘다. 멈춘 결과는 버린다) */
    fun simulate(model: Model, holdings: List<Holding>, mu: List<Double>, nPaths: Int, seed: UInt, goal: Double, monthly: Double,   // ios: Data/MonteCarlo.swift:418
                 withEvents: Boolean, dof: Double, stop: (() -> Boolean)? = null): Sim {   // ios: Data/MonteCarlo.swift:419
        val P = nPaths
        val fs = model.factors; val F = fs.size; val A = holdings.size; val D = model.days.size; val M = model.monthIdx.size   // ios: Data/MonteCarlo.swift:420
        val nu = mcMax(3.0, dof); val tAdj = sqrt((nu - 2) / nu)   // ios: Data/MonteCarlo.swift:421
        val rng = Rng(seed)   // ios: Data/MonteCarlo.swift:422
        // 사건을 빼고 계산할 때는 실적 몫을 뺀 변동성 대신 과거 변동성 그대로
        val sig = DoubleArray(F) { i -> val f = fs[i]   // ios: Data/MonteCarlo.swift:424
            if (!withEvents) f.vol / sqrt(TD) else {   // ios: Data/MonteCarlo.swift:425
                val ded = f.evCats.fold(0.0) { s, c -> s + f.evVarCat[c]!! }   // ios: Data/MonteCarlo.swift:426
                (if (ded != 0.0) sqrt(mcMax(f.vol * f.vol - ded, 0.5 * f.vol * f.vol)) else f.vol) / sqrt(TD)   // ios: Data/MonteCarlo.swift:427
            }
        }
        val muD = DoubleArray(mu.size) { ln(1 + mu[it]) / TD }   // ios: Data/MonteCarlo.swift:429
        val fxCol = IntArray(A) { a -> fxOf(holdings[a].ccy)?.let { k -> fs.indexOfFirst { it.isFx && it.key == k }.takeIf { i -> i >= 0 } } ?: -1 }   // ios: Data/MonteCarlo.swift:430
        val v0 = DoubleArray(A) { holdings[it].valueKrw }; var V0 = 0.0; for (x in v0) V0 += x   // ios: Data/MonteCarlo.swift:431
        val w = DoubleArray(A) { v0[it] / V0 }   // ios: Data/MonteCarlo.swift:432
        val isMonth = IntArray(D + 1) { -1 }   // ios: Data/MonteCarlo.swift:433
        for ((k, d) in model.monthIdx.withIndex()) isMonth[d] = k   // ios: Data/MonteCarlo.swift:434
        val Lf = DoubleArray(F * F); for (i in 0 until F) for (j in 0 until F) Lf[i * F + j] = model.L[i][j]   // ios: Data/MonteCarlo.swift:435 F×F
        val evDay: List<List<Ev>> = if (withEvents) (0 until D).map { model.byDay[it] ?: emptyList() } else emptyList()   // ios: Data/MonteCarlo.swift:436
        var nTrials = 0; while (nTrials.toDouble() < nu) nTrials += 1   // ios: Data/MonteCarlo.swift:437 웹: for (k = 0; k < nu; k++)

        val port = DoubleArray(M * P) { 0.0 }   // ios: Data/MonteCarlo.swift:439
        val cg = List(A) { FloatArray(M * P) { 0f } }   // ios: Data/MonteCarlo.swift:440
        val firstHit = IntArray(P) { -1 }; val mddArr = DoubleArray(P) { 0.0 }   // ios: Data/MonteCarlo.swift:441
        var touched = 0   // ios: Data/MonteCarlo.swift:442
        val z = DoubleArray(F) { 0.0 }; val lg = DoubleArray(F) { 0.0 }; val lr = DoubleArray(F) { 0.0 }   // ios: Data/MonteCarlo.swift:443
        val boostUntil = IntArray(F) { -1 }; val boostMult = DoubleArray(F) { 1.0 }   // ios: Data/MonteCarlo.swift:444
        val h = DoubleArray(A) { 0.0 }; val G = DoubleArray(A) { 0.0 }   // ios: Data/MonteCarlo.swift:445

        for (p in 0 until P) {   // ios: Data/MonteCarlo.swift:447
            if (stop != null && (p and 31) == 0 && stop()) break   // ios: Data/MonteCarlo.swift:448
            for (f in 0 until F) { lg[f] = 0.0; boostUntil[f] = -1; boostMult[f] = 1.0 }   // ios: Data/MonteCarlo.swift:449
            for (a in 0 until A) { h[a] = v0[a]; G[a] = 1.0 }   // ios: Data/MonteCarlo.swift:450
            var peak = V0; var mdd = 0.0; var hit = false   // ios: Data/MonteCarlo.swift:451
            port[p] = V0   // ios: Data/MonteCarlo.swift:452
            for (d in 0 until D) {   // ios: Data/MonteCarlo.swift:453
                for (f in 0 until F) z[f] = rng.normal()   // ios: Data/MonteCarlo.swift:454
                var chi = 0.0   // ios: Data/MonteCarlo.swift:455
                for (i in 0 until nTrials) { val g = rng.normal(); chi += g * g }   // ios: Data/MonteCarlo.swift:456
                val ts = tAdj / sqrt(chi / nu)   // ios: Data/MonteCarlo.swift:457
                for (f in 0 until F) {   // ios: Data/MonteCarlo.swift:458
                    var s = 0.0   // ios: Data/MonteCarlo.swift:459
                    val row = f * F   // ios: Data/MonteCarlo.swift:460
                    for (k in 0..f) s += Lf[row + k] * z[k]   // ios: Data/MonteCarlo.swift:461
                    val sd = sig[f] * (if (d <= boostUntil[f]) boostMult[f] else 1.0)   // ios: Data/MonteCarlo.swift:462
                    lr[f] = muD[f] - 0.5 * sd * sd + sd * s * ts   // ios: Data/MonteCarlo.swift:463
                }
                if (withEvents) {   // ios: Data/MonteCarlo.swift:465
                    for (ev in evDay[d]) {   // ios: Data/MonteCarlo.swift:466
                        val u = rng.next(); val zz = rng.normal()   // ios: Data/MonteCarlo.swift:467 걸러 낸 사건도 난수는 똑같이 쓴다
                        if (u >= ev.prob) continue   // ios: Data/MonteCarlo.swift:468
                        val j = ev.jm + ev.js * zz   // ios: Data/MonteCarlo.swift:469
                        for (k in 0 until ev.targets.size) {   // ios: Data/MonteCarlo.swift:470
                            val t = ev.targets[k]   // ios: Data/MonteCarlo.swift:471
                            lr[t] += j * ev.scales[k]   // ios: Data/MonteCarlo.swift:472
                            if (ev.vd > 0) { boostUntil[t] = d + ev.vd; boostMult[t] = ev.vm }   // ios: Data/MonteCarlo.swift:473
                        }
                    }
                }
                var V = 0.0   // ios: Data/MonteCarlo.swift:477
                for (a in 0 until A) {   // ios: Data/MonteCarlo.swift:478
                    val g = lr[a] + (if (fxCol[a] >= 0) lr[fxCol[a]] else 0.0)   // ios: Data/MonteCarlo.swift:479
                    val e = exp(g); h[a] *= e; G[a] *= e; V += h[a]   // ios: Data/MonteCarlo.swift:480
                }
                for (f in 0 until F) lg[f] += lr[f]   // ios: Data/MonteCarlo.swift:482
                val k = isMonth[d + 1]   // ios: Data/MonteCarlo.swift:483
                if (k > 0 && k < M - 1 && monthly > 0) { for (a in 0 until A) h[a] += monthly * w[a]; V += monthly }   // ios: Data/MonteCarlo.swift:484 월초 적립 (마지막 날 제외)
                if (V > peak) peak = V else mdd = mcMin(mdd, V / peak - 1)   // ios: Data/MonteCarlo.swift:485
                if (V >= goal) { hit = true; if (firstHit[p] < 0) firstHit[p] = d + 1 }   // ios: Data/MonteCarlo.swift:486
                if (k > 0) {   // ios: Data/MonteCarlo.swift:487
                    port[k * P + p] = V   // ios: Data/MonteCarlo.swift:488
                    for (a in 0 until A) cg[a][k * P + p] = (lg[a] + (if (fxCol[a] >= 0) lg[fxCol[a]] else 0.0)).toFloat()   // ios: Data/MonteCarlo.swift:489
                }
            }
            mddArr[p] = mdd   // ios: Data/MonteCarlo.swift:492
            if (hit) touched += 1   // ios: Data/MonteCarlo.swift:493
        }

        val bands = bandsOf(port, M = M, P = P)   // ios: Data/MonteCarlo.swift:496
        val term = port.copyOfRange((M - 1) * P, port.size).also { it.sort() }   // ios: Data/MonteCarlo.swift:497
        val byYear = ArrayList<Year>()   // ios: Data/MonteCarlo.swift:498
        var y = 1   // ios: Data/MonteCarlo.swift:499
        while (D > 0) {   // ios: Data/MonteCarlo.swift:500
            val target = addMonths(model.start, 12 * y)   // ios: Data/MonteCarlo.swift:501
            var lim = 0; while (lim < D && model.days[lim] <= target) lim += 1   // ios: Data/MonteCarlo.swift:502
            if (lim > 0) {   // ios: Data/MonteCarlo.swift:503
                var n = 0; for (x in firstHit) n += if (x >= 0 && x <= lim) 1 else 0   // ios: Data/MonteCarlo.swift:504
                byYear.add(Year(year = y, date = model.days[lim - 1], p = n.toDouble() / P.toDouble(), k = -1))   // ios: Data/MonteCarlo.swift:505
                if (lim >= D) break   // ios: Data/MonteCarlo.swift:506
            }
            y += 1   // ios: Data/MonteCarlo.swift:508
        }
        val summary = Result(V0 = V0, bands = bands, pGoal = term.count { it >= goal }.toDouble() / P.toDouble(), pTouch = touched.toDouble() / P.toDouble(),   // ios: Data/MonteCarlo.swift:510
                             terminal = terminal(term), mddMedian = quantileSorted(mddArr.sortedArray(), 0.5), byYear = byYear, cumHit = emptyList())   // ios: Data/MonteCarlo.swift:511
        return Sim(A = A, M = M, P = P, cg = cg, muD = muD.take(A), monthIdx = model.monthIdx, monthDates = model.monthDates, v0 = v0.toList(),   // ios: Data/MonteCarlo.swift:512
                   start = model.start, summary = summary)   // ios: Data/MonteCarlo.swift:513
    }

    // 다시 무게 주기 (웹 reweight, 재시뮬레이션 없이)
    /** 같은 충격 경로에 기대수익(드리프트)·월 적립·목표만 바꿔 다시 계산한다. mu: 종목별 연 기대수익 */
    fun reweight(X: Sim, mu: List<Double>, goal: Double, monthly: Double): Result {   // ios: Data/MonteCarlo.swift:518
        val A = X.A; val M = X.M; val P = X.P; val mi = X.monthIdx.toIntArray(); val cgX = X.cg   // ios: Data/MonteCarlo.swift:519
        val dd = (0 until A).map { a -> ln(1 + mu[a]) / TD - X.muD[a] }.toDoubleArray()   // ios: Data/MonteCarlo.swift:520
        val v = X.v0.toDoubleArray(); var V0 = 0.0; for (x in v) V0 += x; val wN = DoubleArray(v.size) { v[it] / V0 }   // ios: Data/MonteCarlo.swift:521
        val port = DoubleArray(M * P) { 0.0 }   // ios: Data/MonteCarlo.swift:522
        val firstHitK = IntArray(P) { -1 }; val mddArr = DoubleArray(P) { 0.0 }   // ios: Data/MonteCarlo.swift:523
        val accU = DoubleArray(A) { 0.0 }   // ios: Data/MonteCarlo.swift:524
        for (p in 0 until P) {   // ios: Data/MonteCarlo.swift:525
            for (a in 0 until A) accU[a] = 0.0   // ios: Data/MonteCarlo.swift:526
            var peak = V0; var mdd = 0.0   // ios: Data/MonteCarlo.swift:527
            for (k in 0 until M) {   // ios: Data/MonteCarlo.swift:528
                val t = mi[k].toDouble(); val contrib = k > 0 && k < M - 1   // ios: Data/MonteCarlo.swift:529
                var V = 0.0   // ios: Data/MonteCarlo.swift:530
                for (a in 0 until A) {   // ios: Data/MonteCarlo.swift:531
                    val U = exp((if (k > 0) cgX[a][k * P + p].toDouble() else 0.0) + dd[a] * t)   // ios: Data/MonteCarlo.swift:532
                    if (contrib) accU[a] += wN[a] / U   // ios: Data/MonteCarlo.swift:533
                    V += U * (v[a] + monthly * accU[a])   // ios: Data/MonteCarlo.swift:534
                }
                port[k * P + p] = V   // ios: Data/MonteCarlo.swift:536
                if (V > peak) peak = V else mdd = mcMin(mdd, V / peak - 1)   // ios: Data/MonteCarlo.swift:537
                if (V >= goal && firstHitK[p] < 0 && k > 0) firstHitK[p] = k   // ios: Data/MonteCarlo.swift:538
            }
            mddArr[p] = mdd   // ios: Data/MonteCarlo.swift:540
        }
        val term = port.copyOfRange((M - 1) * P, port.size).also { it.sort() }   // ios: Data/MonteCarlo.swift:542
        val hist = IntArray(M) { 0 }   // ios: Data/MonteCarlo.swift:543
        for (k in firstHitK) if (k >= 0) hist[k] += 1   // ios: Data/MonteCarlo.swift:544
        val cum = DoubleArray(M) { 0.0 }; var run = 0   // ios: Data/MonteCarlo.swift:545
        for (k in 0 until M) { run += hist[k]; cum[k] = run.toDouble() / P.toDouble() }   // ios: Data/MonteCarlo.swift:546
        val byYear = ArrayList<Year>()   // ios: Data/MonteCarlo.swift:547
        var y = 1   // ios: Data/MonteCarlo.swift:548
        while (true) {   // ios: Data/MonteCarlo.swift:549
            val target = addMonths(X.start, 12 * y)   // ios: Data/MonteCarlo.swift:550
            var k = 0; while (k + 1 < M && X.monthDates[k + 1] <= target) k += 1   // ios: Data/MonteCarlo.swift:551
            byYear.add(Year(year = y, date = X.monthDates[k], p = cum[k], k = k))   // ios: Data/MonteCarlo.swift:552
            if (k >= M - 1) break   // ios: Data/MonteCarlo.swift:553
            y += 1   // ios: Data/MonteCarlo.swift:554
        }
        return Result(V0 = V0, bands = bandsOf(port, M = M, P = P), pGoal = term.count { it >= goal }.toDouble() / P.toDouble(),   // ios: Data/MonteCarlo.swift:556
                      pTouch = firstHitK.count { it >= 0 }.toDouble() / P.toDouble(), terminal = terminal(term),   // ios: Data/MonteCarlo.swift:557
                      mddMedian = quantileSorted(mddArr.sortedArray(), 0.5), byYear = byYear, cumHit = cum.toList())   // ios: Data/MonteCarlo.swift:558
    }

    /** 월 경계 번호: 시작일에서 months 개월 뒤 날짜 이하인 마지막 칸 (웹 breakeven·reweight 의 byYear 와 같은 셈) */
    fun monthK(X: Sim, months: Int): Int {   // ios: Data/MonteCarlo.swift:562
        val target = addMonths(X.start, months)   // ios: Data/MonteCarlo.swift:563
        var k = 0; while (k + 1 < X.M && X.monthDates[k + 1] <= target) k += 1   // ios: Data/MonteCarlo.swift:564
        return k   // ios: Data/MonteCarlo.swift:565
    }

    // 본전 확률 (웹 breakeven, 재시뮬레이션 없이)
    /** 같은 경로에서 종목 묶음(idx)만 적립·매매 없이 들고 있을 때, 월말 평가액이 basis 이상이 되는 첫 달이 months 안에 올 확률.
     *  mu: 종목별 연 기대수익 (reweight 와 같은 드리프트 보정). 경로가 기한까지 없으면 null */
    fun breakeven(X: Sim, idx: List<Int>, basis: Double, mu: List<Double>, months: List<Int>): List<Double?> {   // ios: Data/MonteCarlo.swift:571
        val M = X.M; val P = X.P   // ios: Data/MonteCarlo.swift:572
        val dd = idx.map { a -> ln(1 + mu[a]) / TD - X.muD[a] }.toDoubleArray()   // ios: Data/MonteCarlo.swift:573
        val first = IntArray(P) { -1 }   // ios: Data/MonteCarlo.swift:574
        for (p in 0 until P) {   // ios: Data/MonteCarlo.swift:575
            for (k in 1 until max(1, M)) {   // ios: Data/MonteCarlo.swift:576
                var V = 0.0   // ios: Data/MonteCarlo.swift:577
                for ((j, a) in idx.withIndex()) V += X.v0[a] * exp(X.cg[a][k * P + p].toDouble() + dd[j] * X.monthIdx[k].toDouble())   // ios: Data/MonteCarlo.swift:578
                if (V >= basis) { first[p] = k; break }   // ios: Data/MonteCarlo.swift:579
            }
        }
        val last = dayNum(X.monthDates[M - 1])   // ios: Data/MonteCarlo.swift:582
        return months.map { m ->   // ios: Data/MonteCarlo.swift:583
            if (dayNum(addMonths(X.start, m)) - last > 15) return@map null   // ios: Data/MonteCarlo.swift:584
            val k = monthK(X, months = m)   // ios: Data/MonteCarlo.swift:585
            var n = 0; for (x in first) n += if (x >= 0 && x <= k) 1 else 0; n.toDouble() / P.toDouble()   // ios: Data/MonteCarlo.swift:586
        }
    }

    // 월 경계마다 평가액 (재조정 없음, 웹 reweight 와 같은 식)
    /** 처음 금액 start[a], 매달 monthly 를 wC[a] 비율로 나눠 넣는다 (월 경계 1 ~ M-2). 반환 [k*P+p].
     *  start = v0, wC = v0 비중이면 reweight 의 경로와 같다. 구성 비교·계획처럼 처음 금액이 0 이어도 쓸 수 있다 */
    fun values(X: Sim, mu: List<Double>, start: List<Double>, monthly: Double, wC: List<Double>): DoubleArray {   // ios: Data/MonteCarlo.swift:593
        val A = X.A; val M = X.M; val P = X.P; val mi = X.monthIdx.toIntArray(); val cgX = X.cg; val st = start.toDoubleArray(); val wCa = wC.toDoubleArray()   // ios: Data/MonteCarlo.swift:594
        val dd = (0 until A).map { a -> ln(1 + mu[a]) / TD - X.muD[a] }.toDoubleArray()   // ios: Data/MonteCarlo.swift:595
        val out = DoubleArray(M * P) { 0.0 }; val accU = DoubleArray(A) { 0.0 }   // ios: Data/MonteCarlo.swift:596
        for (p in 0 until P) {   // ios: Data/MonteCarlo.swift:597
            for (a in 0 until A) accU[a] = 0.0   // ios: Data/MonteCarlo.swift:598
            for (k in 0 until M) {   // ios: Data/MonteCarlo.swift:599
                val t = mi[k].toDouble(); val contrib = k > 0 && k < M - 1   // ios: Data/MonteCarlo.swift:600
                var V = 0.0   // ios: Data/MonteCarlo.swift:601
                for (a in 0 until A) {   // ios: Data/MonteCarlo.swift:602
                    val U = exp((if (k > 0) cgX[a][k * P + p].toDouble() else 0.0) + dd[a] * t)   // ios: Data/MonteCarlo.swift:603
                    if (contrib) accU[a] += wCa[a] / U   // ios: Data/MonteCarlo.swift:604
                    V += U * (st[a] + monthly * accU[a])   // ios: Data/MonteCarlo.swift:605
                }
                out[k * P + p] = V   // ios: Data/MonteCarlo.swift:607
            }
        }
        return out   // ios: Data/MonteCarlo.swift:610
    }

    /** 렌즈별 종목 연 기대수익 (웹 viewMu): "base" · "smooth" · 그 밖 = 내 관점 (추세 신뢰 trust%) */
    fun viewMu(model: Model, A: Int, lens: String, trust: Double): List<Double> =   // ios: Data/MonteCarlo.swift:614
        model.factors.take(A).map { f -> if (lens == "base") f.base else if (lens == "smooth") f.smooth else blendMu(f.base, f.smooth, trust / 100) }   // ios: Data/MonteCarlo.swift:615
    /** 시뮬레이션에 쓰는 요인별 기대수익 (웹: 설정 시나리오, 기본 내 관점) */
    fun scenarioMu(model: Model, scenario: String?): List<Double> =   // ios: Data/MonteCarlo.swift:618
        model.factors.map { f -> if (scenario == "base") f.base else if (scenario == "smooth") f.smooth else f.blend }   // ios: Data/MonteCarlo.swift:619
}

// 경로 묶음: 전망과 같은 몬테카를로 경로로 계획·구성의 확률을 센다 (웹 breakeven·reweight 와 같은 식)
// 경로는 종목별 처음 금액 base 로 계산해 두고, 다른 금액이면 비율로 늘이거나 줄인다 (적립도 같은 비율로).
// 화면 스레드에서만 쓴다 (캐시가 잠금 없는 HashMap). values 가 돌려주는 DoubleArray 는 캐시와 같은 것이라 고치지 않는다.
class PathSet(   // ios: Data/MonteCarlo.swift:625
    val sim: MC.Sim,   // ios: Data/MonteCarlo.swift:626
    val mu: List<Double>,   // ios: Data/MonteCarlo.swift:627 종목별 연 기대수익 (지금 관점)
    val base: List<Double>,   // ios: Data/MonteCarlo.swift:628 종목별 처음 금액 (원)
    val key: String,   // ios: Data/MonteCarlo.swift:631
) {   // ios: Data/MonteCarlo.swift:635,636 init(sim:mu:base:key:) = 위 칸 그대로
    val wC: List<Double>   // ios: Data/MonteCarlo.swift:629 매달 넣는 돈을 나누는 비율 (구성 비중)
    val tM: List<Double>   // ios: Data/MonteCarlo.swift:630 월 경계마다 오늘부터 개월
    private val cache = HashMap<String, DoubleArray>()   // ios: Data/MonteCarlo.swift:632
    private val bandCache = HashMap<String, List<List<Double>>>()   // ios: Data/MonteCarlo.swift:633

    init {
        var s = 0.0; for (x in base) s += x   // ios: Data/MonteCarlo.swift:637
        wC = base.map { if (s > 0) it / s else 1 / max(1, base.size).toDouble() }   // ios: Data/MonteCarlo.swift:638
        val s0 = MC.dayNum(sim.start)   // ios: Data/MonteCarlo.swift:639
        tM = sim.monthDates.map { (MC.dayNum(it) - s0).toDouble() / 365.25 * 12 }   // ios: Data/MonteCarlo.swift:640
    }
    val P: Int get() = sim.P   // ios: Data/MonteCarlo.swift:642
    val M: Int get() = sim.M   // ios: Data/MonteCarlo.swift:643

    /** 지금 금액 V(원)에서, 매달 monthly(원)씩 구성 비중대로 넣을 때 월 경계마다 평가액 [k*P+p] */
    fun values(V: Double, monthly: Double = 0.0): DoubleArray {   // ios: Data/MonteCarlo.swift:646
        val ck = "$V|$monthly"   // ios: Data/MonteCarlo.swift:647
        cache[ck]?.let { return it }   // ios: Data/MonteCarlo.swift:648
        var S = 0.0; for (x in base) S += x   // ios: Data/MonteCarlo.swift:649
        val v: DoubleArray   // ios: Data/MonteCarlo.swift:650
        if (V > 0 && S > 0) {   // ios: Data/MonteCarlo.swift:651
            val f = V / S   // ios: Data/MonteCarlo.swift:652
            val raw = MC.values(sim, mu = mu, start = base, monthly = monthly / f, wC = wC)   // ios: Data/MonteCarlo.swift:653
            v = if (f == 1.0) raw else DoubleArray(raw.size) { raw[it] * f }   // ios: Data/MonteCarlo.swift:654
        } else {   // ios: Data/MonteCarlo.swift:655
            v = MC.values(sim, mu = mu, start = base.map { 0.0 }, monthly = monthly, wC = wC)   // ios: Data/MonteCarlo.swift:656
        }
        if (cache.size > 24) cache.clear()   // ios: Data/MonteCarlo.swift:658
        cache[ck] = v   // ios: Data/MonteCarlo.swift:659
        return v   // ios: Data/MonteCarlo.swift:660
    }
    /** 월 경계마다 5·25·50·75·95% (적립 없이) */
    fun bands(V: Double): List<List<Double>> {   // ios: Data/MonteCarlo.swift:663
        val ck = "$V"   // ios: Data/MonteCarlo.swift:664
        bandCache[ck]?.let { return it }   // ios: Data/MonteCarlo.swift:665
        val b = MC.bandsOf(values(V), M = M, P = P)   // ios: Data/MonteCarlo.swift:666
        if (bandCache.size > 8) bandCache.clear()   // ios: Data/MonteCarlo.swift:667
        bandCache[ck] = b   // ios: Data/MonteCarlo.swift:668
        return b   // ios: Data/MonteCarlo.swift:669
    }
    fun k(months: Int): Int = MC.monthK(sim, months = months)   // ios: Data/MonteCarlo.swift:671
    /** 기준선 line(k) 이상이 되는 첫 칸 (1부터, upTo 칸까지 없으면 -1) */
    fun firstHits(v: DoubleArray, upTo: Int, line: (Int) -> Double): List<Int> {   // ios: Data/MonteCarlo.swift:673
        val kMax = upTo
        val out = IntArray(P) { -1 }   // ios: Data/MonteCarlo.swift:674
        if (kMax < 1) return out.toList()   // ios: Data/MonteCarlo.swift:675
        val lines = (0..kMax).map(line)   // ios: Data/MonteCarlo.swift:676
        for (p in 0 until P) { for (k in 1..kMax) if (v[k * P + p] >= lines[k]) { out[p] = k; break } }   // ios: Data/MonteCarlo.swift:677
        return out.toList()   // ios: Data/MonteCarlo.swift:678
    }
    fun firstHits(v: List<Double>, upTo: Int, line: (Int) -> Double): List<Int> = firstHits(v.toDoubleArray(), upTo, line)
    /** upTo 칸까지 한 번이라도 기준선 이상인 경로 비율 */
    fun touch(v: DoubleArray, upTo: Int, line: (Int) -> Double): Double =   // ios: Data/MonteCarlo.swift:681
        firstHits(v, upTo = upTo, line = line).count { it >= 0 }.toDouble() / P.toDouble()   // ios: Data/MonteCarlo.swift:682
    fun touch(v: List<Double>, upTo: Int, line: (Int) -> Double): Double = touch(v.toDoubleArray(), upTo, line)
    /** k칸 평가액(minus 를 뺀 값)이 atLeast 이상인 경로 비율 */
    fun share(v: DoubleArray, k: Int, minus: Double = 0.0, atLeast: Double): Double {   // ios: Data/MonteCarlo.swift:685
        var n = 0; for (p in 0 until P) if (v[k * P + p] - minus >= atLeast) n += 1   // ios: Data/MonteCarlo.swift:686
        return n.toDouble() / P.toDouble()   // ios: Data/MonteCarlo.swift:687
    }
    fun share(v: List<Double>, k: Int, minus: Double = 0.0, atLeast: Double): Double = share(v.toDoubleArray(), k, minus, atLeast)
    fun quantile(v: DoubleArray, k: Int, q: Double, minus: Double = 0.0): Double =   // ios: Data/MonteCarlo.swift:689
        MC.quantileSorted((0 until P).map { v[k * P + it] - minus }.toDoubleArray().also { it.sort() }, q)   // ios: Data/MonteCarlo.swift:690
    fun quantile(v: List<Double>, k: Int, q: Double, minus: Double = 0.0): Double = quantile(v.toDoubleArray(), k, q, minus)
    /** 첫 도달 칸들의 가운데 (개월, 없으면 무한대) */
    fun medianMonths(hits: List<Int>): Double {   // ios: Data/MonteCarlo.swift:693
        val ms = hits.map { if (it >= 0) mcRound(tM[it]) else Double.POSITIVE_INFINITY }.sorted()   // ios: Data/MonteCarlo.swift:694
        return ms[ms.size / 2]   // ios: Data/MonteCarlo.swift:695
    }
    /** 첫 도달 칸들의 q 분위 (개월, 그 분위까지 못 닿으면 무한대). 0.1·0.5·0.9 로 '보통'과 '10번 중 8번' 범위 */
    fun quantileMonths(hits: List<Int>, q: Double): Double {   // ios: Data/MonteCarlo.swift:698
        val ms = hits.map { if (it >= 0) mcRound(tM[it]) else Double.POSITIVE_INFINITY }.sorted()   // ios: Data/MonteCarlo.swift:699
        return ms[min(ms.size - 1, (q * ms.size.toDouble()).toInt())]   // ios: Data/MonteCarlo.swift:700
    }
    /** 웹 req50 (reweight 와 같은 50번 이분법): 마지막 칸(kEnd) 평가액이 goal 이상인 경로가 절반이 되는 월 적립 (원). 적립은 kEnd 앞 달까지 */
    fun req50(V: Double, goal: Double, kEnd: Int): Double {   // ios: Data/MonteCarlo.swift:703
        val K = goal
        val v0 = values(V); val v1 = values(V, monthly = 1e6); val last = kEnd >= M - 1   // ios: Data/MonteCarlo.swift:704
        val nc = (0 until P).map { v0[kEnd * P + it] }.toDoubleArray()   // ios: Data/MonteCarlo.swift:705
        val an = (0 until P).map { (v1[kEnd * P + it] - v0[kEnd * P + it]) / 1e6 - (if (last || kEnd == 0) 0 else 1) }.toDoubleArray()   // ios: Data/MonteCarlo.swift:706
        fun ok(c: Double): Boolean { var n = 0; for (p in 0 until P) if (nc[p] + c * an[p] >= K) n += 1; return n.toDouble() / P.toDouble() >= 0.5 }   // ios: Data/MonteCarlo.swift:707
        if (ok(0.0)) return 0.0   // ios: Data/MonteCarlo.swift:708
        var lo = 0.0; var hi = 1e9   // ios: Data/MonteCarlo.swift:709
        for (i in 0 until 50) { val c = (lo + hi) / 2; if (ok(c)) hi = c else lo = c }   // ios: Data/MonteCarlo.swift:710
        return hi   // ios: Data/MonteCarlo.swift:711
    }
}

// 숫자 칸을 너그럽게 읽기 (Swift KeyedDecodingContainer.mcNum·mcStr): JSON 원소를 직접 본다
// ios: Data/MonteCarlo.swift:715
// 숫자, 숫자 문자열, 참·거짓
private fun mcNum(o: JsonObject, k: String): Double? {   // ios: Data/MonteCarlo.swift:717
    val e = o[k] as? JsonPrimitive ?: return null
    if (e !is JsonNull && !e.isString) e.content.toDoubleOrNull()?.let { return it }   // ios: Data/MonteCarlo.swift:718
    if (e.isString) return MC.jsNumber(e.content)   // ios: Data/MonteCarlo.swift:719
    if (e !is JsonNull && (e.content == "true" || e.content == "false")) return if (e.content == "true") 1.0 else 0.0   // ios: Data/MonteCarlo.swift:720
    return null   // ios: Data/MonteCarlo.swift:721
}
private fun mcStr(o: JsonObject, k: String): String? {   // ios: Data/MonteCarlo.swift:723
    val e = o[k] as? JsonPrimitive ?: return null
    if (e.isString) return e.content   // ios: Data/MonteCarlo.swift:724
    if (e !is JsonNull) e.content.toDoubleOrNull()?.let { return swiftDesc(it) }   // ios: Data/MonteCarlo.swift:725
    return null   // ios: Data/MonteCarlo.swift:726
}

/** MC.Settings 읽기: Swift init(from:) 그대로 (숫자 칸은 숫자·숫자 문자열·참거짓). 쓰기는 Swift 기본 (없는 칸은 안 씀) */
object McSettingsSerializer : KSerializer<MC.Settings> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.naeilo.app.core.calc.MC.Settings")
    override fun deserialize(decoder: Decoder): MC.Settings {   // ios: Data/MonteCarlo.swift:61
        val c = (decoder as JsonDecoder).decodeJsonElement() as? JsonObject ?: throw SerializationException("MC.Settings")   // ios: Data/MonteCarlo.swift:62
        val s = MC.Settings()
        s.scenario = (c["scenario"] as? JsonPrimitive)?.takeIf { it.isString }?.content   // ios: Data/MonteCarlo.swift:63
        s.trust = mcNum(c, "trust"); s.n_paths = mcNum(c, "n_paths"); s.seed = mcNum(c, "seed"); s.t_dof = mcNum(c, "t_dof")   // ios: Data/MonteCarlo.swift:64
        s.earnings_adjust = mcNum(c, "earnings_adjust")?.let { it != 0.0 }   // ios: Data/MonteCarlo.swift:65
        s.fx_drift = mcNum(c, "fx_drift"); s.fx_vol_mult = mcNum(c, "fx_vol_mult"); s.prior_mu = mcNum(c, "prior_mu")   // ios: Data/MonteCarlo.swift:66
        s.default_vol = mcNum(c, "default_vol"); s.default_corr = mcNum(c, "default_corr")   // ios: Data/MonteCarlo.swift:67
        return s
    }
    override fun serialize(encoder: Encoder, value: MC.Settings) {
        val o = buildJsonObject {
            value.scenario?.let { put("scenario", it) }; value.trust?.let { put("trust", it) }; value.n_paths?.let { put("n_paths", it) }
            value.seed?.let { put("seed", it) }; value.t_dof?.let { put("t_dof", it) }; value.earnings_adjust?.let { put("earnings_adjust", it) }
            value.fx_drift?.let { put("fx_drift", it) }; value.fx_vol_mult?.let { put("fx_vol_mult", it) }; value.prior_mu?.let { put("prior_mu", it) }
            value.default_vol?.let { put("default_vol", it) }; value.default_corr?.let { put("default_corr", it) }
        }
        (encoder as JsonEncoder).encodeJsonElement(o)
    }
}

/** MC.Event 읽기·쓰기: Swift init(from:)·encode(to:) 그대로 (repeat·vol_mult·vol_days 키) */
object McEventSerializer : KSerializer<MC.Event> {
    private object CodingKeys { const val id = "id"; const val on = "on"; const val date = "date"; const val target = "target"; const val factor = "factor"; const val cat = "cat"; const val rep = "repeat"; const val prob = "prob"; const val mean = "mean"; const val sd = "sd"; const val volMult = "vol_mult"; const val volDays = "vol_days" }   // ios: Data/MonteCarlo.swift:98
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.naeilo.app.core.calc.MC.Event")
    override fun deserialize(decoder: Decoder): MC.Event {   // ios: Data/MonteCarlo.swift:99
        val c = (decoder as JsonDecoder).decodeJsonElement() as? JsonObject ?: throw SerializationException("MC.Event")   // ios: Data/MonteCarlo.swift:100
        val e = MC.Event()
        e.id = mcStr(c, CodingKeys.id); e.date = mcStr(c, CodingKeys.date); e.target = mcStr(c, CodingKeys.target); e.factor = mcStr(c, CodingKeys.factor); e.cat = mcStr(c, CodingKeys.cat); e.rep = mcStr(c, CodingKeys.rep)   // ios: Data/MonteCarlo.swift:101
        val on = (c[CodingKeys.on] as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }
        e.on = if (on != null && (on.content == "true" || on.content == "false")) on.content == "true"   // ios: Data/MonteCarlo.swift:102
            else if (on != null && on.content.toDoubleOrNull() != null) on.content.toDouble().let { x -> x != 0.0 && !x.isNaN() }   // ios: Data/MonteCarlo.swift:103
            else !(mcStr(c, CodingKeys.on) ?: "").isEmpty()   // ios: Data/MonteCarlo.swift:104
        // Number(e.prob): 없음·빈 문자열·숫자 아님 → 100, null → 0
        val pr = c[CodingKeys.prob]
        if (!c.containsKey(CodingKeys.prob)) e.prob = 100.0   // ios: Data/MonteCarlo.swift:106
        else if (pr is JsonNull) e.prob = 0.0   // ios: Data/MonteCarlo.swift:107
        else if (pr is JsonPrimitive && pr.isString) { val s = pr.content; e.prob = if (s.isEmpty()) 100.0 else MC.jsNumber(s) ?: 100.0 }   // ios: Data/MonteCarlo.swift:108
        else e.prob = mcNum(c, CodingKeys.prob) ?: 100.0   // ios: Data/MonteCarlo.swift:109
        e.mean = mcNum(c, CodingKeys.mean) ?: 0.0; e.sd = mcNum(c, CodingKeys.sd) ?: 0.0; e.volMult = mcNum(c, CodingKeys.volMult) ?: 0.0; e.volDays = mcNum(c, CodingKeys.volDays) ?: 0.0   // ios: Data/MonteCarlo.swift:110
        return e
    }
    override fun serialize(encoder: Encoder, value: MC.Event) {   // ios: Data/MonteCarlo.swift:112
        val o = buildJsonObject {   // ios: Data/MonteCarlo.swift:113
            value.id?.let { put(CodingKeys.id, it) }; put(CodingKeys.on, value.on); value.date?.let { put(CodingKeys.date, it) }   // ios: Data/MonteCarlo.swift:114
            value.target?.let { put(CodingKeys.target, it) }; value.factor?.let { put(CodingKeys.factor, it) }; value.cat?.let { put(CodingKeys.cat, it) }   // ios: Data/MonteCarlo.swift:115
            value.rep?.let { put(CodingKeys.rep, it) }; put(CodingKeys.prob, value.prob); put(CodingKeys.mean, value.mean); put(CodingKeys.sd, value.sd)   // ios: Data/MonteCarlo.swift:116
            put(CodingKeys.volMult, value.volMult); put(CodingKeys.volDays, value.volDays)   // ios: Data/MonteCarlo.swift:117
        }
        (encoder as JsonEncoder).encodeJsonElement(o)
    }
}
