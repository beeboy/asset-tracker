package com.naeilo.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.Config
import com.naeilo.app.core.Day
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.cformat
import com.naeilo.app.core.getDouble
import com.naeilo.app.core.putDouble
import com.naeilo.app.net.Market
import com.naeilo.app.net.SiteStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URL
import kotlin.math.abs
import kotlin.math.floor

// 내 몫 실적 계산. 기준은 tools/earn_calc.mjs (아이폰 Data/Earnings.swift 와 같은 답). 검사: app/src/test EarnParityTest (tools/earn_fixtures.json)
// 입력: data/earnings/{종목}.json, 지금 수량, 오늘 환율(원/달러). 자료가 없으면(ETF) null → 화면은 줄표.
// 규칙 (docs/decisions.md 2026-10-22):
// - 주당 값 × 지금 수량. 모든 분기 같은 수량. 주당 = 그 분기 값 ÷ 그 분기 신고서 첫 장 주식 수
// - 갈래: 원가·연구비·판관비가 다 있으면 3갈래, 남는 돈 = 매출 − 셋 − 순이익 은 물안개. 하나라도 없으면 '쓴 돈' 1갈래
// - 적자: 순이익 < 0. 모자란 만큼 = −순이익
// - 작년 같은 분기 = 끝 날짜가 365±25일 앞인 분기
// ios: Data/Earnings.swift:11
object Earnings {
    @Serializable
    // ios: Data/Earnings.swift:12,13,14,15
    data class Quarter(
        val start: String, val end: String, val months: String, val label: String, val filed: String, val announced: String,
        val shares: Double? = null,
        val revenue: Double? = null, val cogs: Double? = null, val rnd: Double? = null, val sga: Double? = null, val net: Double? = null,
    )
    @Serializable data class Pending(val announced: String)   // ios: Data/Earnings.swift:17
    @Serializable
    // ios: Data/Earnings.swift:18,19,20,21,22,23
    data class File(val symbol: String, val name: String? = null, val currency: String, val pending: Pending? = null, val quarters: List<Quarter>)

    class Mine(val revenue: Double?, val cogs: Double?, val rnd: Double?, val sga: Double?, val net: Double?)   // ios: Data/Earnings.swift:26
    data class Branch(val k: String, val v: Double)   // ios: Data/Earnings.swift:27
    data class Gauge(val label: String, val value: Double?, val lastYear: Boolean, val co: Double? = null)   // co = 회사 전체 매출   // ios: Data/Earnings.swift:28
    /** 강 팝업·목록 막대·홈 카드가 쓰는 값 한 벌 (earn_calc.mjs view) */
    // ios: Data/Earnings.swift:30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46
    data class Summary(
        val symbol: String, val currency: String, val months: String, val label: String,
        val lastYearMonths: String?,
        val pending: String?,
        val announced: String,
        val revenue: Double?, val net: Double?,
        val branches: List<Branch>,
        val mist: Double,
        val loss: Boolean,
        val short: Double,
        val gauge: List<Gauge>,
        val revYoY: String?,
        val netYoY: String?,
        val lastYearNet: Double?,
        val netText: String,
        val netKrw: String?,
        val coRevenue: Double? = null,
    ) {
        /** 실적 나온 날: 숫자를 기다리는 중이거나 오늘 발표 (이름 옆 금색 점·홈 카드) */
        // ios: Views/EarningsRiver.swift:59,61,62,64
        val fresh: Boolean get() = pending != null || announced == Day.today
    }

    // ios: Data/Earnings.swift:49,50,51,52
    fun mine(q: Quarter, qty: Double): Mine {
        val k = qty / (q.shares ?: 0.0)
        fun m(v: Double?) = v?.let { it * k }
        return Mine(m(q.revenue), m(q.cogs), m(q.rnd), m(q.sga), m(q.net))
    }

    /** "2025-09-30" → 1970-01-01 부터 날 수 (Howard Hinnant days_from_civil) */
    // ios: Data/Earnings.swift:56,57,58,60,61,62,63,64
    fun dayNum(s: String): Int {
        val p = s.split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return 0
        val y = if (p[1] <= 2) p[0] - 1 else p[0]; val m = p[1]; val d = p[2]
        val era = (if (y >= 0) y else y - 399) / 400; val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    // ios: Data/Earnings.swift:67,68,69
    fun sameLastYear(qs: List<Quarter>, q: Quarter): Int? {
        val e = dayNum(q.end)
        return qs.indexOfFirst { abs(e - dayNum(it.end) - 365) <= 25 }.takeIf { it >= 0 }
    }

    /** Swift .rounded(): 반은 0 에서 먼 쪽 */
    private fun rnd(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

    /** 작년 대비 비율 글: "+3%" / "−43%" / "작년과 같음". 작년이나 올해가 손실이면 null */
    // ios: Data/Earnings.swift:73,74,75,76,77
    fun yoyText(now: Double?, before: Double?): String? {
        if (now == null || before == null || before <= 0 || now < 0) return null
        val r = now / before - 1
        if (abs(r) < 0.005) return "작년과 같음"
        return (if (r > 0) "+" else "−") + rnd(abs(r) * 100).toLong() + "%"
    }

    /** 돈 글: 10 이상 정수, 1 이상 소수 한 자리, 그 아래 두 자리. 음수는 "−" */
    // ios: Data/Earnings.swift:81,82,83,84
    fun money(v: Double, currency: String): String {
        val a = abs(v)
        val n = if (a >= 10) cformat("%.0f", rnd(a)) else if (a >= 1) cformat("%.1f", a) else cformat("%.2f", a)
        return (if (v < 0) "−" else "") + n + (if (currency == "USD") "달러" else " " + currency)
    }

    /** 원화 어림: "약 21만" / "약 8천" / "약 300원" */
    // ios: Data/Earnings.swift:88,89,90,91,92
    fun krw(v: Double): String {
        val a = abs(v); val s = if (v < 0) "−" else ""
        if (a >= 10000) return "약 " + s + grouped(rnd(a / 10000).toLong()) + "만"
        if (a >= 1000) return "약 " + s + rnd(a / 1000).toLong() + "천"
        return "약 " + s + (rnd(a / 10).toLong() * 10) + "원"
    }
    /** 1234567 → "1,234,567" */
    // ios: Data/Earnings.swift:95,96,97,98
    fun grouped(n: Long): String {
        var s = abs(n).toString(); var out = ""
        while (s.length > 3) { out = "," + s.takeLast(3) + out; s = s.dropLast(3) }
        return (if (n < 0) "-" else "") + s + out
    }

    // ios: Data/Earnings.swift:101,102,103,104,105,106,107,108,109,110,111,112,113,114,116,117,118,119,120,121,122,123,124,125,126,127
    fun summary(j: File?, qty: Double, fx: Double?): Summary? {
        if (j == null) return null
        val q = j.quarters.lastOrNull() ?: return null
        val qs = j.quarters; val lyIndex = sameLastYear(qs, q); val ly = lyIndex?.let { qs[it] }
        val me = mine(q, qty); val lm = ly?.let { mine(it, qty) }
        val rev = me.revenue ?: 0.0; val net = me.net ?: 0.0   // JS 에서 null 은 빼기·곱하기에서 0
        val three = q.cogs != null && q.rnd != null && q.sga != null
        val branches = if (three) listOf(Branch("cogs", me.cogs ?: 0.0), Branch("sga", me.sga ?: 0.0), Branch("rnd", me.rnd ?: 0.0))
            else listOf(Branch("spent", rev - net))
        val mist = if (three) rev - (me.cogs ?: 0.0) - (me.rnd ?: 0.0) - (me.sga ?: 0.0) - net else 0.0
        val loss = net < 0
        val gauge = qs.indices.toList().dropLast(1).takeLast(4).map { i ->
            Gauge(qs[i].label, mine(qs[i], qty).revenue, i == lyIndex, qs[i].revenue)
        }
        return Summary(
            symbol = j.symbol, currency = j.currency, months = q.months, label = q.label,
            lastYearMonths = ly?.let { "작년 " + it.months },
            pending = j.pending?.announced, announced = q.announced,
            revenue = me.revenue, net = me.net, branches = branches, mist = mist, loss = loss, short = if (loss) -net else 0.0,
            gauge = gauge,
            revYoY = yoyText(me.revenue, lm?.revenue),
            netYoY = yoyText(me.net, lm?.net),
            lastYearNet = lm?.net,
            netText = money(net, j.currency),
            netKrw = if (!loss && j.currency == "USD" && (fx ?: 0.0) > 0) krw(net * fx!!) else null,
            coRevenue = q.revenue,
        )
    }

    // ── 자료 받기·캐시 ──

    fun cacheName(sym: String) = "earn_$sym.json"   // ios: Data/Earnings.swift:132
    fun decode(d: String?): File? = d?.let { runCatching { NJson.decodeFromString(File.serializer(), it) }.getOrNull() }   // ios: Data/Earnings.swift:133
    fun cached(sym: String): File? = if (sym == "DRNK") drnk else decode(Store.readData(cacheName(sym))?.decodeToString())   // ios: Data/Earnings.swift:134

    /** 앱 안 견본 (아이폰 Resources/earnings_seed.json 을 빌드 때 복사). none = 회사 실적이 없는 종목 */
    // ios: Data/Earnings.swift:138,139,140
    @Serializable data class Seed(val files: Map<String, File>, val none: List<String>)
    val seed: Seed? by lazy { Defaults.asset("earnings_seed", "json")?.let { runCatching { NJson.decodeFromString(Seed.serializer(), it) }.getOrNull() } }

    sealed class Got { class Ok(val f: File) : Got(); object None : Got(); object Failed : Got() }   // ios: Data/Earnings.swift:142
    /** naeilo.com/data/earnings/{종목}.json. 404 는 자료 없음(ETF·국내) */
    // ios: Data/Earnings.swift:144,145,146,147,148,149,150,151,152,153
    suspend fun fetch(sym: String): Got = withContext(Dispatchers.IO) {
        val (qn, qv) = "t" to (System.currentTimeMillis() / 1000 / 3600).toString()
        val url = URL(Config.data, "earnings/$sym.json").toString() + "?$qn=$qv"
        val req = FeedReq(url, timeout = 10.0)
        req.setValue("naeilo-ios/0.1", "User-Agent")
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return@withContext Got.Failed
        if (code == 404) return@withContext Got.None
        val f = if (code == 200) decode(data.decodeToString()) else null
        if (f == null) return@withContext Got.Failed
        Store.writeData(data, cacheName(sym))
        Got.Ok(f)
    }

    /** DRNK 견본: data/earnings/DRNK.json 과 같은 내용 (가상 종목이라 받지 않는다) */
    // ios: Data/Earnings.swift:157,158,169
    val drnk: File? by lazy { decode(drnkJSON) }
    const val drnkJSON = """
{"symbol":"DRNK","name":"드링커 (가상 종목)","currency":"USD","pending":null,"quarters":[
{"start":"2024-10-01","end":"2024-12-31","months":"10~12","label":"24.10~12","filed":"2025-01-23","announced":"2025-01-23","shares":505000000,"revenue":7000000000,"cogs":3150000000,"rnd":1820000000,"sga":700000000,"net":550000000},
{"start":"2025-01-01","end":"2025-03-31","months":"1~3","label":"25.1~3","filed":"2025-04-24","announced":"2025-04-24","shares":504000000,"revenue":7400000000,"cogs":3330000000,"rnd":1924000000,"sga":740000000,"net":600000000},
{"start":"2025-04-01","end":"2025-06-30","months":"4~6","label":"25.4~6","filed":"2025-07-24","announced":"2025-07-24","shares":503000000,"revenue":7900000000,"cogs":3555000000,"rnd":2054000000,"sga":790000000,"net":620000000},
{"start":"2025-07-01","end":"2025-09-30","months":"7~9","label":"25.7~9","filed":"2025-10-23","announced":"2025-10-23","shares":503000000,"revenue":7600000000,"cogs":4712000000,"rnd":1976000000,"sga":760000000,"net":-1200000000},
{"start":"2025-10-01","end":"2025-12-31","months":"10~12","label":"25.10~12","filed":"2026-01-22","announced":"2026-01-22","shares":502000000,"revenue":8300000000,"cogs":3735000000,"rnd":2158000000,"sga":830000000,"net":660000000},
{"start":"2026-01-01","end":"2026-03-31","months":"1~3","label":"26.1~3","filed":"2026-04-23","announced":"2026-04-23","shares":501000000,"revenue":8800000000,"cogs":3960000000,"rnd":2288000000,"sga":880000000,"net":700000000},
{"start":"2026-04-01","end":"2026-06-30","months":"4~6","label":"26.4~6","filed":"2026-07-23","announced":"2026-07-23","shares":500000000,"revenue":9200000000,"cogs":4140000000,"rnd":2392000000,"sga":920000000,"net":720000000},
{"start":"2026-07-01","end":"2026-09-30","months":"7~9","label":"26.7~9","filed":"2026-10-22","announced":"2026-10-22","shares":498000000,"revenue":9600000000,"cogs":4320000000,"rnd":2496000000,"sga":960000000,"net":750000000}
]}
"""
}

/** 보유 종목 실적 파일 (키 = 사이트 티커). 받은 것은 기기에 두고 다음에 먼저 쓴다 */
// ios: Views/EarningsRiver.swift:9,10,11
class EarningsFeed {
    companion object {
        val shared = EarningsFeed()
        /** 실적을 볼 수 있는 종목의 사이트 티커. 미국 종목만, DRNK 는 앱 안 견본 */
        // ios: Views/EarningsRiver.swift:15,16,17
        fun key(sym: Symbol): String? {
            if (sym.id == "DRNK") return "DRNK"
            return if (sym.currency == Currency.usd) SiteStats.key(sym.id) else null
        }
    }
    var files by mutableStateOf(mapOf<String, Earnings.File>())   // ios: Views/EarningsRiver.swift:12
        private set

    /** 받은 자료 → 앱 안 견본 순 */
    fun file(k: String): Earnings.File? = if (k == "DRNK") Earnings.drnk else files[k] ?: Earnings.seed?.files?.get(k)   // ios: Views/EarningsRiver.swift:21

    // ios: Views/EarningsRiver.swift:23,24,25
    fun summary(sym: Symbol, qty: Double): Earnings.Summary? {
        val k = key(sym) ?: return null
        return Earnings.summary(file(k), qty, Market.shared.fx.last)
    }

    /** 받은 지 20시간이 안 됐으면 받지 않는다 (봇이 하루 한 번 만든다). force(당겨 갱신)면 바로 */
    // ios: Views/EarningsRiver.swift:29,30,31,32,33,34,35,36,37,38,39,40
    suspend fun refresh(held: Set<String>, force: Boolean) {
        val keys = held.mapNotNull { id -> Sample.symbol(id)?.let(::key) }.toSet() - "DRNK"
        val c = withContext(Dispatchers.IO) { keys.filter { files[it] == null }.mapNotNull { k -> Earnings.cached(k)?.let { k to it } } }
        if (c.isNotEmpty()) files = files + c
        val now = System.currentTimeMillis() / 1000.0
        for (k in keys.sorted()) {
            val got = "earnGot.$k"
            if (!force && now - Store.defaults.getDouble(got) < 20 * 3600) continue
            when (val g = Earnings.fetch(k)) {
                is Earnings.Got.Ok -> { files = files + (k to g.f); Store.defaults.edit().putDouble(got, now).apply() }
                Earnings.Got.None -> { files = files - k; Store.remove(Earnings.cacheName(k)); Store.defaults.edit().putDouble(got, now).apply() }
                Earnings.Got.Failed -> {}
            }
        }
    }
}
