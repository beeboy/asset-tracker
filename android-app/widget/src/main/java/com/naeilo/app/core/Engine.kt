package com.naeilo.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 위젯이 그리는 숫자. 요약(하루 1번 계산) + 시세 파일(30분마다)로 지금 값을 다시 맞춘다 */
// ios: WidgetShared/Engine.swift:4
data class Snapshot(
    // ios: WidgetShared/Engine.swift:10
    var total: Double,
    // ios: WidgetShared/Engine.swift:11
    var prevTotal: Double,
    // ios: WidgetShared/Engine.swift:12
    var goal: Double,
    // ios: WidgetShared/Engine.swift:13
    var goalDate: String,
    // ios: WidgetShared/Engine.swift:14
    var dday: Int,
    // ios: WidgetShared/Engine.swift:15
    var v0: Double? = null,
    // ios: WidgetShared/Engine.swift:16
    var start: String? = null,
    // ios: WidgetShared/Engine.swift:17
    var spark: List<Double>,          // 최근 3달 일별 (마지막 = 지금)
    // ios: WidgetShared/Engine.swift:18
    var fan: Fan? = null,
    // ios: WidgetShared/Engine.swift:19
    var pGoal: Double? = null,
    // ios: WidgetShared/Engine.swift:20
    var monthly: Double = 0.0,        // 매달 넣는 돈 (원). 필요 경로에 더한다
    // ios: WidgetShared/Engine.swift:21
    var term: Term? = null,
    // ios: WidgetShared/Engine.swift:22
    var fcAsOf: String? = null,
    // ios: WidgetShared/Engine.swift:24
    var hits: List<Hit>,              // 지난 주 적중 (retro = 예측을 적기 전이라 사후 계산)
    // ios: WidgetShared/Engine.swift:25
    var week: Week? = null,
    // ios: WidgetShared/Engine.swift:26
    var moves: List<Move>,
    // ios: WidgetShared/Engine.swift:27
    var events: List<Ev>,
    // ios: WidgetShared/Engine.swift:28
    var updated: Instant? = null,
    // ios: WidgetShared/Engine.swift:29
    var source: String? = null,
    // ios: WidgetShared/Engine.swift:30
    var placeholder: Boolean = false,
    /** 지금 값의 시세를 받은 시각과 출처 (평가액이 가장 큰 쪽: 토스·Alpaca·사이트·직접 입력·앱). 위젯 아래 '3분 전 · 토스' */
    // ios: WidgetShared/Engine.swift:32
    var pricedAt: Instant? = null,
    // ios: WidgetShared/Engine.swift:33
    var feed: String? = null,
) {
    // ios: WidgetShared/Engine.swift:5
    data class Move(var t: String, var pct: Double, var krw: Double, var w: Double) { val id: String get() = t }
    // ios: WidgetShared/Engine.swift:6
    data class Fan(var md: List<Instant>, var p5: List<Double>, var p25: List<Double>, var p50: List<Double>, var p75: List<Double>, var p95: List<Double>)
    // ios: WidgetShared/Engine.swift:7
    data class Week(var f: String, var p50: Double, var lo: Double, var hi: Double, var act: Double, var dday: Int)
    // ios: WidgetShared/Engine.swift:8
    data class Ev(var d: String, var t: String, var k: String, var dday: Int) { val id: String get() = d + t + k }
    // ios: WidgetShared/Engine.swift:23
    data class Hit(var hit: Boolean, var retro: Boolean)
    /** Swift 튜플 (p5, p50, p95) */
    data class Term(val p5: Double, val p50: Double, val p95: Double)
    /** Swift 튜플 (ahead, need, req) */
    data class Pace(val ahead: Double, val need: Double, val req: Double)

    /** "적중 1/1" (실제 예측만). 실제 예측이 아직 없으면 "사후 7/12" */
    // ios: WidgetShared/Engine.swift:36,37,38,39
    val hitText: String get() {
        val real = hits.filter { !it.retro }
        if (real.isEmpty() && hits.isNotEmpty()) return WL.t("사후 {a}/{b}", mapOf("a" to "${hits.count { it.hit }}", "b" to "${hits.size}"))
        return WL.t("적중 {a}/{b}", mapOf("a" to "${real.count { it.hit }}", "b" to "${real.size}"))
    }

    /** 전망 끝 이름: 목표 루트는 목표일까지, 회복 루트는 앱 전망처럼 3년 (페이스의 목표일과 다르다) */
    // ios: WidgetShared/Engine.swift:43,44,45
    val termLabel: String get() {
        val last = fan?.md?.lastOrNull() ?: return WL.t("3년 뒤")
        return WL.t(if (Day.str(last) == goalDate) "목표일" else "3년 뒤")
    }

    // ios: WidgetShared/Engine.swift:48
    val dayChg: Double get() = if (prevTotal > 0) total / prevTotal - 1 else 0.0
    // ios: WidgetShared/Engine.swift:49
    val dayAmt: Double get() = total - prevTotal
    // ios: WidgetShared/Engine.swift:50
    val progress: Double get() = if (goal > 0) total / goal else 0.0
    // ios: WidgetShared/Engine.swift:51
    val left: Double get() = max(0.0, goal - total)
    // ios: WidgetShared/Engine.swift:52
    val unit: Double get() = goal / 1000
    // ios: WidgetShared/Engine.swift:53
    val cells: Int get() = min(1000, (total / unit).toInt())
    // ios: WidgetShared/Engine.swift:54
    val cellsPrev: Int get() = min(1000, (prevTotal / unit).toInt())
    /** 시작일부터 목표일까지 (년) */
    // ios: WidgetShared/Engine.swift:56,57,58
    val span: Double? get() {
        val start = start ?: return null; val s = Day.date(start) ?: return null; val e = Day.date(goalDate) ?: return null
        val y = (e.toEpochMilli() - s.toEpochMilli()) / 1000.0 / (365.25 * 86400); return if (y > 0) y else null
    }
    /** 필요 경로의 금액 (f = 시작 0 → 목표일 1). 앱 내 길 need 와 같은 식: 적립 + 시작 금액이 같은 연 수익률로 (목표 − 적립 합)까지 */
    // ios: WidgetShared/Engine.swift:61,62,63,64
    fun need(f: Double): Double {
        val v0 = v0 ?: return goal; val span = span ?: return goal
        val t = f * span; val kAdj = goal - monthly * 12 * span
        return monthly * 12 * t + (if (kAdj > 0 && v0 > 0) v0 * (kAdj / v0).pow(f) else v0 + (goal - v0) * f)
    }
    /** 필요 경로 대비 지금 */
    // ios: WidgetShared/Engine.swift:67,68,69,70,71
    val pace: Pace? get() {
        val v0 = v0?.takeIf { it > 0 } ?: return null; val start = start ?: return null; val s = Day.date(start) ?: return null; val span = span ?: return null
        val el = max(0.0, (Instant.now().toEpochMilli() - s.toEpochMilli()) / 1000.0 / (365.25 * 86400))
        val n = need(min(1.0, el / span)); val kAdj = goal - monthly * 12 * span
        return Pace(total / n - 1, n, if (kAdj > 0) (kAdj / v0).pow(1 / span) - 1 else 0.0)
    }

    companion object
}

// ios: WidgetShared/Engine.swift:75
object Day {
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    // ios: WidgetShared/Engine.swift:76
    val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.forLanguageTag("en_US_POSIX".replace('_', '-'))).withZone(ZoneId.of("Asia/Seoul")).withResolverStyle(ResolverStyle.SMART)   // 그레고리력
    // ios: WidgetShared/Engine.swift:77
    fun date(s: String): Instant? = try { LocalDate.parse(s, fmt).atStartOfDay(seoul).toInstant() } catch (_: Exception) { null }
    // ios: WidgetShared/Engine.swift:78
    fun str(d: Instant): String = fmt.format(d)
    // ios: WidgetShared/Engine.swift:79
    val today: String get() = str(Instant.now())
    // ios: WidgetShared/Engine.swift:80,81,82
    fun dday(s: String): Int {
        val d = date(s) ?: return 0; val t = date(today) ?: return 0
        return ((d.toEpochMilli() - t.toEpochMilli()) / 1000.0 / 86400).roundToInt()
    }
    // ios: WidgetShared/Engine.swift:84
    fun md(s: String): String { val p = s.split("-"); return if (p.size == 3) "${p[1].toIntOrNull() ?: 0}/${p[2].toIntOrNull() ?: 0}" else s }
}

// ios: WidgetShared/Engine.swift:87
object Engine {
    // ios: WidgetShared/Engine.swift:88,89,90,91,92
    fun snapshot(): Snapshot {
        val s = Store.read(WSummary.serializer(), "summary.json")
        val f = Store.read(WFeed.serializer(), "feed.json")
        val live = Store.read(WLive.serializer(), "live.json")
        if (s == null && live == null) return Snapshot.sample
        // 위젯이 직접 받은 사이트 시세가 앱이 마지막으로 넘긴 값보다 나중이면 그 값으로
        // ios: WidgetShared/Engine.swift:94,95
        val after = Store.lastCheck?.epochSec ?: 0.0
        val site = QuoteFeed.cached()?.let { if (it.got > after) it else null }
        // 국내 테스트 시세도 같게: 앱이 지금 넘긴 종목만 (스위치를 끄면 앱이 넘기지 않는다)
        // ios: WidgetShared/Engine.swift:97
        val kr = KrQuoteFeed.cached()?.let { k -> if (k.got > after) QuoteFeed.File(got = k.got, q = k.q.filter { f?.ky?.get(it.key) != null }) else null }
        // 토스 시세 (개발자 전용): 앱이 받아 둔 값이 앱이 넘긴 값보다 나중이면
        // ios: WidgetShared/Engine.swift:99
        val toss = TossFeed.cached()?.let { if (it.got > after) it else null }
        // Alpaca 시세 (내 키가 있을 때, 미국 종목): 토스 값이 없는 종목만
        // ios: WidgetShared/Engine.swift:101
        val alpaca = AlpacaFeed.cached()?.let { if (it.got > after) it else null }
        // Longbridge 시세 (내 키가 있을 때): 토스·Alpaca 값이 없는 종목만
        // ios: WidgetShared/Engine.swift:103,104
        val lb = LongbridgeFeed.cached()?.let { if (it.got > after) it else null }
        return build(s, f, live, site = site, kr = kr, toss = toss, alpaca = alpaca, lb = lb, th = TossDaily.cached())
    }

    /** Swift 튜플 (total, dayChg) */
    data class LiveHoldings(val total: Double, val dayChg: Double)

    /** 인물 위젯용: 위젯이 앱보다 나중에 직접 받은 시세가 있으면 종목 평가액(현금 뺌)과 오늘 등락을 그 값으로 다시 낸다.
     *  없으면 null (앱이 reward.json 에 써 둔 값을 그대로 쓴다) */
    // ios: WidgetShared/Engine.swift:109,110,111,112,113,114,115,116,117,118
    fun liveHoldings(): LiveHoldings? {
        val after = Store.lastCheck?.epochSec ?: 0.0
        val fresh = listOf(QuoteFeed.cached()?.got, KrQuoteFeed.cached()?.got, TossFeed.cached()?.got, AlpacaFeed.cached()?.got, LongbridgeFeed.cached()?.got).any { (it ?: 0.0) > after }
        if (!fresh) return null
        val s = snapshot()
        if (s.placeholder || s.moves.isEmpty()) return null
        val tot = s.moves.fold(0.0) { a, m -> a + m.krw }
        val prev = s.moves.fold(0.0) { a, m -> a + m.krw / (1 + m.pct) }
        if (tot <= 0) return null
        return LiveHoldings(tot, if (prev > 0) tot / prev - 1 else 0.0)
    }

    // ios: WidgetShared/Engine.swift:121
    fun build(s: WSummary?, f: WFeed?, live: WLive?, site: QuoteFeed.File? = null, kr: QuoteFeed.File? = null, toss: QuoteFeed.File? = null, alpaca: QuoteFeed.File? = null, lb: QuoteFeed.File? = null, th: TossDaily.File? = null): Snapshot {
        // 수량·현금·목표는 최신 입력값, 없으면 요약의 것
        // ios: WidgetShared/Engine.swift:123,124,125,126
        data class H(val t: String, val sh: Double, val manual: Double?)
        val hold: List<H>
        if (live != null) { val l = live; hold = l.hold.map { H(t = it.t, sh = it.sh, manual = if (l.manualPrice) it.price else null) } }
        else { hold = (s?.hold ?: emptyList()).map { H(t = it.t, sh = it.sh, manual = it.manual) } }
        // ios: WidgetShared/Engine.swift:127,128,129,130,131,132,133
        fun fx(ccy: String): Double {
            if (ccy == "KRW") return 1.0
            val m = f?.mar; val d = m?.date
            if (ccy == "USD" && m != null && d != null && Day.dday(d) >= -5) return m.rate
            val sym = if (ccy == "USD") "KRW=X" else ccy + "KRW=X"
            site?.q?.get(sym)?.last?.let { return it }
            f?.q?.get(sym)?.firstOrNull()?.let { return it }
            return f?.c?.get(sym)?.c?.filterNotNull()?.lastOrNull() ?: 1350.0
        }
        // ios: WidgetShared/Engine.swift:135,136,137,138
        val cash = live?.let { it.cashKrw + it.cashUsd * fx("USD") } ?: (s?.cash ?: 0.0)
        var moves: MutableList<Snapshot.Move> = mutableListOf()
        var tot = 0.0; var prev = 0.0
        val feedVal = mutableMapOf<String, Double>(); val feedAt = mutableMapOf<String, Double>()
        // ios: WidgetShared/Engine.swift:139,140,141,142
        for (h in hold) {
            val ccy = f?.ccy?.get(h.t) ?: s?.hold?.firstOrNull { it.t == h.t }?.ccy ?: "USD"
            val q = f?.q?.get(h.t); val sq = site?.q?.get(h.t) ?: kr?.q?.get(h.t)
            val aq = alpaca?.q?.get(h.t) ?: lb?.q?.get(h.t)   // Alpaca 가 없으면 Longbridge (둘 다 미국 종목 지금 가격)
            // 미국 장이 다 끝났으면 애프터마켓 가격 대신 정규장 종가 (앱 Market.regClose 와 같다)
            // 토스 값이면 전일·정규장 종가도 토스 일봉에서 (앱 Market.tossPrev·regClose 와 같다)
            // ios: WidgetShared/Engine.swift:145,146,147,148,149,151
            val tq = toss?.q?.get(h.t); val hist = th?.h?.get(f?.ty?.get(h.t) ?: h.t)
            var reg: Double? = null
            if (ccy == "USD" && USSession.closed()) {
                if (tq != null) reg = TossDaily.reg(hist, at = tq.at)
                if (reg == null) { val r = sq?.reg; if (r != null && r > 0) reg = r }
                // 토스 주간거래 값(애프터마켓 뒤에 체결)이면 정규장 종가 대신 그 값 (앱 Market.tossLast 와 같다)
                if (reg != null && tq != null && TossDaily.daytime(tq.at)) reg = null
            }
            // ios: WidgetShared/Engine.swift:153,154,155,156,157
            val quoted: Double? = reg ?: tq?.last ?: aq?.last
            val last = quoted ?: sq?.last ?: h.manual ?: q?.firstOrNull() ?: f?.c?.get(h.t)?.c?.filterNotNull()?.lastOrNull()
            val tp = tq?.let { TossDaily.prev(hist, at = it.at, kr = ccy == "KRW") }
            val pc = tp ?: sq?.prev ?: (if ((q?.size ?: 0) > 1) q!![1] else null) ?: aq?.let { if (it.prev > 0) it.prev else null }
            val p = last ?: continue
            // ios: WidgetShared/Engine.swift:158,159
            val v = h.sh * p * fx(ccy); val pv = h.sh * (pc ?: p) * fx(ccy)
            tot += v; prev += pv
            // 이 종목 값이 어디서 왔는지 (위의 last 와 같은 순서)
            // ios: WidgetShared/Engine.swift:161,162,163,164,165,166,167,168,169
            var tag = "앱"; var at: Double? = null
            if (toss?.q?.get(h.t) != null) { tag = "토스"; at = toss?.got }
            else if (aq != null) { tag = "Alpaca"; at = alpaca?.got }
            else if (site?.q?.get(h.t) != null) { tag = "사이트"; at = site?.got }
            else if (kr?.q?.get(h.t) != null) { tag = "사이트"; at = kr?.got }
            else if (h.manual != null) { tag = "직접 입력" }
            feedVal[tag] = (feedVal[tag] ?: 0.0) + v
            if (at != null) feedAt[tag] = max(feedAt[tag] ?: 0.0, at)
            moves.add(Snapshot.Move(t = h.t, pct = pc?.let { p / it - 1 } ?: 0.0, krw = v, w = 0.0))
        }
        // ios: WidgetShared/Engine.swift:171
        if (tot <= 0 && s != null) { tot = (s.spark.filterNotNull().lastOrNull() ?: 0.0) * 1e4 - cash; prev = s.prev - cash } // 시세 파일이 없으면 요약 값
        // ios: WidgetShared/Engine.swift:172,173
        val total = tot + cash; val prevTotal = prev + cash
        moves = moves.map { it.copy(w = if (tot > 0) it.krw / tot else 0.0) }.sortedByDescending { it.krw }.toMutableList()

        // ios: WidgetShared/Engine.swift:175,176,177
        val goal = live?.goalAmount ?: s?.goal?.amount ?: 1e9
        val goalDate = (if (live?.goalDate?.isEmpty() == false) live?.goalDate else null) ?: s?.goal?.date ?: ""
        val spark = (s?.spark?.filterNotNull()?.map { it * 1e4 })?.toMutableList() ?: mutableListOf()
        // 요약의 마지막 점은 마지막 종가. 오늘 값은 지금 값으로 붙인다
        // ios: WidgetShared/Engine.swift:179,180
        if (spark.isEmpty() || (s?.asof ?: "") < Day.today) spark.add(total) else spark[spark.size - 1] = total
        if (spark.size > 64) spark.subList(0, spark.size - 64).clear()

        // ios: WidgetShared/Engine.swift:182,183,184,185
        var fan: Snapshot.Fan? = null
        val fc = s?.fc
        if (fc != null) {
            val w = { a: List<Double?> -> a.map { (it ?: 0.0) * 1e4 } }
            fan = Snapshot.Fan(md = fc.md.mapNotNull { Day.date(it) }, p5 = w(fc.p5), p25 = w(fc.p25), p50 = w(fc.p50), p75 = w(fc.p75), p95 = w(fc.p95))
        }
        // ios: WidgetShared/Engine.swift:187,188,189,190
        var week: Snapshot.Week? = null
        val wk = s?.week
        if (wk != null && Day.dday(wk.f) >= 0) {
            val hNow = tot; val act = if (wk.t0 > 0) wk.base * (hNow / wk.t0) + wk.cash else total
            week = Snapshot.Week(f = wk.f, p50 = wk.p50, lo = wk.lo, hi = wk.hi, act = act, dday = Day.dday(wk.f))
        }
        // ios: WidgetShared/Engine.swift:192,193,194
        val evs = (s?.events ?: emptyList()).filter { Day.dday(it.d) >= 0 }.take(4).map { Snapshot.Ev(d = it.d, t = it.t, k = it.k, dday = Day.dday(it.d)) }
        val t = s?.fc?.t
        val feed = feedVal.maxByOrNull { it.value }?.key
        // ios: WidgetShared/Engine.swift:195,196,197,198,199,200
        return Snapshot(total = total, prevTotal = prevTotal, goal = goal, goalDate = goalDate, dday = Day.dday(goalDate),
            v0 = s?.goal?.v0, start = live?.goalStart ?: s?.goal?.start, spark = spark, fan = fan, pGoal = s?.fc?.pg,
            monthly = s?.goal?.m ?: 0.0, term = termOf(t),
            fcAsOf = s?.asof, hits = (s?.hits ?: emptyList()).map { Snapshot.Hit(hit = it.h == 1, retro = it.r == 1) }, week = week, moves = moves, events = evs.toList(),
            updated = Store.lastCheck, source = Store.summarySource,
            pricedAt = feedAt[feed ?: ""]?.toInstant() ?: Store.lastCheck, feed = feed)
    }
}

// ios: WidgetShared/Engine.swift:204,205,206
private fun termOf(t: WSummary.Term?): Snapshot.Term? {
    if (t == null) return null
    val m = t.p50 ?: return null
    return Snapshot.Term(p5 = (t.p5 ?: 0.0) * 1e4, p50 = m * 1e4, p95 = (t.p95 ?: 0.0) * 1e4)
}

/** 로그인 전·위젯 갤러리 미리보기용 */
// ios: WidgetShared/Engine.swift:209,211,212,213,214,215
val Snapshot.Companion.sample: Snapshot get() {
    val spark: List<Double> = (0 until 63).map { i ->
        val x = i.toDouble()
        val wave: Double = sin(x / 6) * 1.5e7
        5.3e8 + wave - x * 1e5
    }
    // ios: WidgetShared/Engine.swift:217,218
    val md: List<Instant> = (0..36).map { ZonedDateTime.now().plusMonths(it.toLong()).toInstant() }
    val p50: List<Double> = md.indices.map { i -> 5.27e8 * 1.12.pow(i.toDouble() / 12) }
    // ios: WidgetShared/Engine.swift:219,220,221,222
    val sp = { z: Double ->
        md.indices.map { i ->
            val spread: Double = z * 0.45 * sqrt(i.toDouble() / 12)
            p50[i] * exp(spread)
        }
    }
    // ios: WidgetShared/Engine.swift:225,226,227,228,229,230,231
    return Snapshot(total = 5.27e8, prevTotal = 5.32e8, goal = 1e9, goalDate = Day.str(md.last()), dday = 1092, v0 = 5.30e8, start = Day.today,
        spark = spark, fan = Snapshot.Fan(md = md, p5 = sp(-1.645), p25 = sp(-0.674), p50 = p50, p75 = sp(0.674), p95 = sp(1.645)),
        pGoal = 0.38, term = Snapshot.Term(1.7e8, 7.7e8, 2.2e9), fcAsOf = Day.today, hits = listOf(true, false, true, true, false, true, true, true).mapIndexed { i, h -> Snapshot.Hit(hit = h, retro = i < 6) },
        week = Snapshot.Week(f = Day.today, p50 = 5.17e8, lo = 4.96e8, hi = 5.39e8, act = 5.27e8, dday = 1),
        moves = listOf(Snapshot.Move(t = "TSLA", pct = -0.007, krw = 3.48e8, w = 0.66), Snapshot.Move(t = "NVDA", pct = -0.006, krw = 0.95e8, w = 0.18),
            Snapshot.Move(t = "SPCX", pct = -0.02, krw = 0.71e8, w = 0.14), Snapshot.Move(t = "SGOV", pct = 0.0, krw = 0.12e8, w = 0.02)),
        events = listOf(Snapshot.Ev(d = Day.today, t = "TSLA", k = "실적", dday = 13)), updated = Instant.now(), source = null, placeholder = true)
}
