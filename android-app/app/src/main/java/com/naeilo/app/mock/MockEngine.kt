package com.naeilo.app.mock

import com.naeilo.app.core.cformat
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// 모의투자 판단·체결 계산. 화면·네트워크 없이 값만 다룬다 (판단 코드와 주문 코드를 나눈다).
// 상태를 바꾸는 곳은 MockDesk 하나뿐이고, 여기 함수는 장부를 받아 고친 장부와 기록 줄을 돌려준다.
// Swift inout 장부는 Kotlin 에서 MockLedger 객체를 그대로 고친다 (부르는 쪽이 사본을 넘긴다)

/** 하루 구간·훑기 박자 상수 (한국 시각, 하루 분). 경계는 여기 한곳에서만 바꾼다 */
object MockClock {   // ios: Mock/MockEngine.swift:8
    const val watchFrom = 9 * 60            // ios: Mock/MockEngine.swift:9
    const val pickFrom = 9 * 60 + 15        // ios: Mock/MockEngine.swift:10
    const val tidyFrom = 15 * 60            // ios: Mock/MockEngine.swift:11
    const val closeAt = 15 * 60 + 30        // ios: Mock/MockEngine.swift:12
    const val fullEvery: Double = 60.0 * 60   // ios: Mock/MockEngine.swift:13
    const val nearMin = 0.5                 // ios: Mock/MockEngine.swift:14
    const val amountFloor = 10.0            // ios: Mock/MockEngine.swift:15
    const val recheck: Double = 15.0 * 60     // ios: Mock/MockEngine.swift:16
    const val newVolDays = 5                // ios: Mock/MockEngine.swift:17
    /** 곡선이 쌓이기 전 기본 U자 누적 비율 (15분 27칸. 첫 1시간 29%·막판 30분 14%, 가운데 고르게. 2026-11-05 가정값) */
    val baseCurve: List<Double> = listOf(0.000, 0.120, 0.190, 0.245, 0.290, 0.318, 0.346, 0.374, 0.402, 0.429, 0.457, 0.485, 0.513, 0.541, 0.569, 0.597, 0.625, 0.653, 0.681, 0.708, 0.736, 0.764, 0.792, 0.820, 0.860, 0.910, 1.000)   // ios: Mock/MockEngine.swift:18
    const val curveUntil = 8 * 60 + 30      // ios: Mock/MockEngine.swift:19
    const val curveFrom = 15 * 60 + 45      // ios: Mock/MockEngine.swift:20

    enum class Seg {   // ios: Mock/MockEngine.swift:22
        pre, watch, pick, tidy, after, holiday;   // ios: Mock/MockEngine.swift:23
        val label: String get() = when (this) {   // ios: Mock/MockEngine.swift:24,25
            pre -> "장 시작 전"   // ios: Mock/MockEngine.swift:26
            watch -> "지켜보기"   // ios: Mock/MockEngine.swift:27
            pick -> "고르고 주문"   // ios: Mock/MockEngine.swift:28
            tidy -> "정리"   // ios: Mock/MockEngine.swift:29
            after -> "마감 후"   // ios: Mock/MockEngine.swift:30
            holiday -> "휴장"   // ios: Mock/MockEngine.swift:31
        }
        /** 자동 고르기·자동 매수가 도는 구간 */
        val buys: Boolean get() = this == pick   // ios: Mock/MockEngine.swift:35
    }

    fun minute(at: MockDate): Int {   // ios: Mock/MockEngine.swift:38
        val c = MockFmt.inst(at).atZone(MockFmt.kst)   // ios: Mock/MockEngine.swift:39,40
        return c.hour * 60 + c.minute
    }

    /** 시각만으로 본 구간 (휴장은 MockDesk.seg 가 장 일정으로 덮는다) */
    fun seg(at: MockDate): Seg {   // ios: Mock/MockEngine.swift:45
        val m = minute(at)   // ios: Mock/MockEngine.swift:46
        if (m < watchFrom) return Seg.pre   // ios: Mock/MockEngine.swift:47
        if (m < pickFrom) return Seg.watch   // ios: Mock/MockEngine.swift:48
        if (m < tidyFrom) return Seg.pick   // ios: Mock/MockEngine.swift:49
        if (m < closeAt) return Seg.tidy   // ios: Mock/MockEngine.swift:50
        return Seg.after   // ios: Mock/MockEngine.swift:51
    }

    fun hm(m: Int): String = "${m / 60}:" + cformat("%02d", m % 60)   // ios: Mock/MockEngine.swift:54
}

object MockCalc {   // ios: Mock/MockEngine.swift:57

    // 조건 조각 값

    /** 조각의 값 (화면 단위: 가격 원 · 등락률 % · 거래량 배 · 이동평균 위아래 비율 · 거래대금 억). 봉이 모자라면 null */
    fun value(p: MockPiece, bars: List<MockBar>): Double? {   // ios: Mock/MockEngine.swift:62
        val last = bars.lastOrNull() ?: return null; if (bars.size < p.need) return null   // ios: Mock/MockEngine.swift:63
        return when (p.kind) {   // ios: Mock/MockEngine.swift:64
            MockPiece.Kind.price -> last.c   // ios: Mock/MockEngine.swift:65
            MockPiece.Kind.change -> {   // ios: Mock/MockEngine.swift:66
                val prev = bars[bars.size - 2].c   // ios: Mock/MockEngine.swift:67
                if (prev > 0) (last.c / prev - 1) * 100 else null   // ios: Mock/MockEngine.swift:68
            }
            MockPiece.Kind.volume -> {   // ios: Mock/MockEngine.swift:69
                val past = bars.dropLast(1).takeLast(p.days)   // ios: Mock/MockEngine.swift:70
                val avg = past.fold(0.0) { a, x -> a + x.v } / max(1, past.size).toDouble()   // ios: Mock/MockEngine.swift:71
                if (avg > 0) last.v / avg else null   // ios: Mock/MockEngine.swift:72
            }
            MockPiece.Kind.ma -> {   // ios: Mock/MockEngine.swift:73
                val win = bars.takeLast(p.days)   // ios: Mock/MockEngine.swift:74
                val avg = win.fold(0.0) { a, x -> a + x.c } / max(1, win.size).toDouble()   // ios: Mock/MockEngine.swift:75
                if (avg > 0) last.c / avg - 1 else null   // ios: Mock/MockEngine.swift:76
            }
            MockPiece.Kind.amount -> last.c * last.v / 1e8   // ios: Mock/MockEngine.swift:77
            MockPiece.Kind.rise -> {   // ios: Mock/MockEngine.swift:78
                val base = bars[bars.size - 1 - p.days].c   // ios: Mock/MockEngine.swift:79
                if (base > 0) (last.c / base - 1) * 100 else null   // ios: Mock/MockEngine.swift:80
            }
        }
    }

    fun pass(p: MockPiece, v: Double): Boolean {   // ios: Mock/MockEngine.swift:84
        if (p.kind == MockPiece.Kind.ma) return if (p.above) v > 0 else v < 0   // ios: Mock/MockEngine.swift:85
        p.lo?.let { lo -> if (v < lo) return false }   // ios: Mock/MockEngine.swift:86
        p.hi?.let { hi -> if (v > hi) return false }   // ios: Mock/MockEngine.swift:87
        return true   // ios: Mock/MockEngine.swift:88
    }

    /** 걸린 이유 조각 글: "거래량 3.1배" */
    fun short(p: MockPiece, v: Double): String = when (p.kind) {   // ios: Mock/MockEngine.swift:92,93
        MockPiece.Kind.price -> MockFmt.won(v)   // ios: Mock/MockEngine.swift:94
        MockPiece.Kind.change -> MockFmt.signed((v * 10).swRound() / 10) + "%"   // ios: Mock/MockEngine.swift:95
        MockPiece.Kind.volume -> "거래량 " + cformat("%.1f", v) + "배"   // ios: Mock/MockEngine.swift:96
        MockPiece.Kind.ma -> "${p.days}일선 " + (if (v > 0) "위" else "아래")   // ios: Mock/MockEngine.swift:97
        MockPiece.Kind.amount -> "거래대금 " + MockFmt.num((v * 10).swRound() / 10) + "억"   // ios: Mock/MockEngine.swift:98
        MockPiece.Kind.rise -> "${p.days}일 " + MockFmt.signed((v * 10).swRound() / 10) + "%"   // ios: Mock/MockEngine.swift:99
    }

    // 훑기

    /** 묶음으로 종목들을 거른다. bars: 코드 → 오래된 것부터. 기준 날짜(가장 늦은 봉)에 봉이 없는 종목은 자료 없음.
     *  frac: 이 시각 하루 거래량 누적 비율. useNew: 새 배수(시각 보정)로 걸림을 판단한다 */
    fun scan(b: MockBundle, stocks: List<MockStock>, bars: Map<String, List<MockBar>>, excluded: Set<String>, at: MockDate, seconds: Double = 0.0, frac: Double? = null, useNew: Boolean = false): MockScan {   // ios: Mock/MockEngine.swift:108
        val barDay = bars.values.mapNotNull { it.lastOrNull()?.d }.maxOrNull() ?: ""   // ios: Mock/MockEngine.swift:109
        val f = frac?.let { if (it > 0 && it < 1 && barDay == MockFmt.day(at)) it else null }   // ios: Mock/MockEngine.swift:110
        val hits = mutableListOf<MockHit>(); val dropped = mutableMapOf<Int, Int>(); val ex = mutableListOf<String>(); val newHits = mutableListOf<String>()   // ios: Mock/MockEngine.swift:111
        val list = stocks.filter { b.scope == MockBundle.Scope.all || (b.scope == MockBundle.Scope.kospi) == it.kospi }   // ios: Mock/MockEngine.swift:112
        for (s in list) {   // ios: Mock/MockEngine.swift:113
            if (s.code in excluded) { ex.add(s.code); continue }   // ios: Mock/MockEngine.swift:114
            val bs = bars[s.code]; val last = bs?.lastOrNull()   // ios: Mock/MockEngine.swift:115
            if (bs == null || last == null || last.d != barDay || bs.size < b.need) { dropped[-1] = (dropped[-1] ?: 0) + 1; continue }   // ios: Mock/MockEngine.swift:115
            val why = mutableListOf<String>(); var fail: Int? = null; var failNew = f == null   // ios: Mock/MockEngine.swift:116
            for ((i, p) in b.pieces.withIndex()) {   // ios: Mock/MockEngine.swift:117
                val v0 = value(p, bs); if (v0 == null) { fail = fail ?: -1; failNew = true; break }   // ios: Mock/MockEngine.swift:118
                val nv = f?.let { scaled(p, v0, it) }   // ios: Mock/MockEngine.swift:119
                if (nv != null && !pass(p, nv)) failNew = true   // ios: Mock/MockEngine.swift:120
                val v = if (useNew) (nv ?: v0) else v0   // ios: Mock/MockEngine.swift:121
                if (fail == null) { if (pass(p, v)) why.add(short(p, v)) else fail = i }   // ios: Mock/MockEngine.swift:122
                if (fail != null && failNew) break   // ios: Mock/MockEngine.swift:123
            }
            if (!failNew) newHits.add(s.code)   // ios: Mock/MockEngine.swift:125
            fail?.let { fl -> dropped[fl] = (dropped[fl] ?: 0) + 1 }; if (fail != null) continue   // ios: Mock/MockEngine.swift:126
            val score = value(b.sortPiece, bs) ?: 0.0   // ios: Mock/MockEngine.swift:127
            val ch = value(MockPiece(kind = MockPiece.Kind.change), bs)?.let { it / 100 }   // ios: Mock/MockEngine.swift:128
            hits.add(MockHit(code = s.code, name = s.name, price = last.c, change = ch, score = score,   // ios: Mock/MockEngine.swift:129
                why = if (why.isEmpty()) "조각 없음 · 전부" else MockFmt.uniq(why.joinToString(" · ")),   // ios: Mock/MockEngine.swift:130
                vol = value(MockPiece(kind = MockPiece.Kind.volume, days = 20), bs), rise5 = value(MockPiece(kind = MockPiece.Kind.rise, days = 5), bs),   // ios: Mock/MockEngine.swift:131
                volNew = f?.let { ff -> value(MockPiece(kind = MockPiece.Kind.volume, days = 20), bs)?.let { it / ff } },   // ios: Mock/MockEngine.swift:132
                amount = value(MockPiece(kind = MockPiece.Kind.amount), bs), amt20 = avgAmount(bs), range20 = avgRange(bs)))   // ios: Mock/MockEngine.swift:133
        }
        hits.sortWith { x, y -> if (x.score != y.score) y.score.compareTo(x.score) else x.code.compareTo(y.code) }   // ios: Mock/MockEngine.swift:135
        return MockScan(at = at, bundleId = b.id, bundleName = b.name, pieceTexts = b.pieces.map { it.text }, barDay = barDay,   // ios: Mock/MockEngine.swift:136
            total = list.size, hits = hits, dropped = dropped, excluded = ex, seconds = seconds,   // ios: Mock/MockEngine.swift:137
            frac = f, newHits = if (f == null) null else newHits)   // ios: Mock/MockEngine.swift:138
    }

    /** 지난 20일 하루 평균 거래대금 (억원). 오늘 봉은 빼서 시각과 상관없다. 봉이 없으면 null */
    fun avgAmount(bs: List<MockBar>): Double? {   // ios: Mock/MockEngine.swift:142
        val past = bs.dropLast(1).takeLast(20)   // ios: Mock/MockEngine.swift:143
        if (past.isEmpty()) return null   // ios: Mock/MockEngine.swift:144
        return past.fold(0.0) { a, x -> a + x.c * x.v } / past.size.toDouble() / 1e8   // ios: Mock/MockEngine.swift:145
    }

    /** 지난 20일 하루 폭 평균 ((고 − 저) ÷ 종가). 오늘 봉은 뺀다. 봉이 없으면 null */
    fun avgRange(bs: List<MockBar>): Double? {   // ios: Mock/MockEngine.swift:149
        val past = bs.dropLast(1).takeLast(20).filter { it.c > 0 }   // ios: Mock/MockEngine.swift:150
        if (past.isEmpty()) return null   // ios: Mock/MockEngine.swift:151
        return past.fold(0.0) { a, x -> a + (x.h - x.l) / x.c } / past.size.toDouble()   // ios: Mock/MockEngine.swift:152
    }

    /** 새 배수: 거래량·거래대금은 아직 쌓이는 중이라 이 시각 누적 비율로 나눈다. 가격 조각은 그대로 */
    fun scaled(p: MockPiece, v: Double, f: Double): Double =   // ios: Mock/MockEngine.swift:156
        if (p.kind == MockPiece.Kind.volume || p.kind == MockPiece.Kind.amount) v / f else v   // ios: Mock/MockEngine.swift:157

    /** 조각 문턱까지 얼마나 왔나 (1 = 맞음, 0 = 멀다). 이동평균은 10% 떨어지면 0 */
    fun close(p: MockPiece, v: Double): Double {   // ios: Mock/MockEngine.swift:161
        if (pass(p, v)) return 1.0   // ios: Mock/MockEngine.swift:162
        if (p.kind == MockPiece.Kind.ma) return max(0.0, 1 - abs(v) / 0.10)   // ios: Mock/MockEngine.swift:163
        p.lo?.let { lo -> if (v < lo) return if (lo > 0 && v >= 0) v / lo else max(0.0, 1 - (lo - v) / max(abs(lo), 1.0)) }   // ios: Mock/MockEngine.swift:164
        p.hi?.let { hi -> if (v > hi) return if (hi > 0) hi / v else max(0.0, 1 - (v - hi) / max(abs(hi), 1.0)) }   // ios: Mock/MockEngine.swift:165
        return 1.0   // ios: Mock/MockEngine.swift:166
    }

    /** 가까운 종목: 모든 조각 중 가장 먼 조각의 거리가 nearMin 이상인 종목을 가까운 순으로 (걸린 종목 포함, 예외 뺌) */
    fun near(b: MockBundle, stocks: List<MockStock>, bars: Map<String, List<MockBar>>, excluded: Set<String>, min: Double = MockClock.nearMin, use: Double? = null): List<String> {   // ios: Mock/MockEngine.swift:170
        val list = stocks.filter { b.scope == MockBundle.Scope.all || (b.scope == MockBundle.Scope.kospi) == it.kospi }   // ios: Mock/MockEngine.swift:171
        val out = mutableListOf<Pair<String, Double>>()   // ios: Mock/MockEngine.swift:172
        for (s in list) { if (s.code in excluded) continue   // ios: Mock/MockEngine.swift:173
            val bs = bars[s.code] ?: continue; if (bs.size < b.need) continue   // ios: Mock/MockEngine.swift:174
            var c = 1.0   // ios: Mock/MockEngine.swift:175
            for (p in b.pieces) {   // ios: Mock/MockEngine.swift:176
                val v0 = value(p, bs); if (v0 == null) { c = 0.0; break }   // ios: Mock/MockEngine.swift:177
                val v = use?.let { scaled(p, v0, it) } ?: v0   // ios: Mock/MockEngine.swift:178
                c = kotlin.math.min(c, close(p, v))   // ios: Mock/MockEngine.swift:179
                if (c < min) break   // ios: Mock/MockEngine.swift:180
            }
            if (c >= min) out.add(s.code to c)   // ios: Mock/MockEngine.swift:182
        }
        return out.sortedWith { x, y -> if (x.second != y.second) y.second.compareTo(x.second) else x.first.compareTo(y.first) }.map { it.first }   // ios: Mock/MockEngine.swift:184
    }

    // 하루 거래량 곡선

    /** 지난 20일 하루 평균 거래대금이 큰 종목 n개 (오늘 봉은 뺀다) */
    fun big(bars: Map<String, List<MockBar>>, n: Int): List<String> {   // ios: Mock/MockEngine.swift:190
        val amt: List<Pair<String, Double>> = bars.map { (c, bs) ->   // ios: Mock/MockEngine.swift:191
            c to bs.dropLast(1).takeLast(20).fold(0.0) { s, x -> s + x.c * x.v }   // ios: Mock/MockEngine.swift:192
        }
        return amt.sortedWith { x, y -> if (x.second != y.second) y.second.compareTo(x.second) else x.first.compareTo(y.first) }.take(n).map { it.first }   // ios: Mock/MockEngine.swift:194
    }

    /** 종목들의 하루 1분봉 ("09:01") → 15분마다 누적 비율 27칸 (종목 사이 가운데 값). 쓸 종목이 5개 안 되면 null */
    fun curve(days: List<List<MockBar>>): List<Double>? {   // ios: Mock/MockEngine.swift:198
        val cols = List(27) { mutableListOf<Double>() }   // ios: Mock/MockEngine.swift:199
        for (bs in days) {   // ios: Mock/MockEngine.swift:200
            val tot = bs.fold(0.0) { a, x -> a + x.v }   // ios: Mock/MockEngine.swift:201
            if (tot <= 0) continue   // ios: Mock/MockEngine.swift:202
            for (k in 0..26) {   // ios: Mock/MockEngine.swift:203
                val t = if (k == 26) Int.MAX_VALUE else 540 + 15 * k   // ios: Mock/MockEngine.swift:204
                cols[k].add(bs.filter { hm(it.d) <= t }.fold(0.0) { a, x -> a + x.v } / tot)   // ios: Mock/MockEngine.swift:205
            }
        }
        if (cols[0].size < 5) return null   // ios: Mock/MockEngine.swift:208
        return cols.map { median(it) }   // ios: Mock/MockEngine.swift:209
    }

    /** 최근 곡선들(27칸)의 칸별 가운데 값. 곡선이 없으면 기본 U자 곡선 */
    fun curveNow(curves: List<MockCurve>): List<Double>? {   // ios: Mock/MockEngine.swift:213
        val cs = curves.filter { it.cum.size == 27 }   // ios: Mock/MockEngine.swift:214
        if (cs.isEmpty()) return MockClock.baseCurve   // ios: Mock/MockEngine.swift:215
        return (0..26).map { k -> median(cs.map { it.cum[k] }) }   // ios: Mock/MockEngine.swift:216
    }

    /** 이 시각 하루 거래량 누적 비율 (15분 사이는 곧게 잇는다). 장 전·곡선 없음은 null, 장 끝 뒤는 1 */
    fun frac(curves: List<MockCurve>, at: MockDate): Double? {   // ios: Mock/MockEngine.swift:220
        val mid = curveNow(curves) ?: return null   // ios: Mock/MockEngine.swift:221
        val c = MockFmt.inst(at).atZone(MockFmt.kst)   // ios: Mock/MockEngine.swift:222,223
        val m = c.hour * 60 + c.minute - 540
        if (m <= 0) return null   // ios: Mock/MockEngine.swift:225
        if (m >= 390) return 1.0   // ios: Mock/MockEngine.swift:226
        val k = m / 15; val r = (m % 15).toDouble() / 15   // ios: Mock/MockEngine.swift:227
        val a = mid[k]; val b = mid[k + 1]   // ios: Mock/MockEngine.swift:228
        val v = a + (b - a) * r   // ios: Mock/MockEngine.swift:229
        return if (v > 0) v else null   // ios: Mock/MockEngine.swift:230
    }

    fun hm(s: String): Int {   // ios: Mock/MockEngine.swift:233
        val p = s.split(":").mapNotNull { it.toIntOrNull() }   // ios: Mock/MockEngine.swift:234
        return if (p.size == 2) p[0] * 60 + p[1] else 0   // ios: Mock/MockEngine.swift:235
    }

    fun median(xs: List<Double>): Double {   // ios: Mock/MockEngine.swift:238
        val s = xs.sorted(); val n = s.size   // ios: Mock/MockEngine.swift:239
        if (n <= 0) return 0.0   // ios: Mock/MockEngine.swift:240
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2   // ios: Mock/MockEngine.swift:241
    }

    /** 예외 묶음에 걸리는 종목 (조건 추가). 조각이 없는 묶음은 아무것도 빼지 않는다 */
    fun exceptionHits(bs: List<MockBundle>, stocks: List<MockStock>, bars: Map<String, List<MockBar>>): Map<String, String> {   // ios: Mock/MockEngine.swift:245
        val out = mutableMapOf<String, String>()   // ios: Mock/MockEngine.swift:246
        for (b in bs) { if (b.pieces.isEmpty()) continue   // ios: Mock/MockEngine.swift:247
            for (h in scan(b, stocks = stocks, bars = bars, excluded = emptySet(), at = mockNow()).hits) if (out[h.code] == null) out[h.code] = "조건 · " + b.name   // ios: Mock/MockEngine.swift:248
        }
        return out   // ios: Mock/MockEngine.swift:250
    }

    // 평가

    /** 평가액 = 현금 + 묶인 돈 + 결제 대기 + 보유 × 지금 가격 (가격을 모르면 산 평균값) */
    fun equity(l: MockLedger, prices: Map<String, Double>): Double =   // ios: Mock/MockEngine.swift:256
        l.cash + l.reserved + l.settling + l.positions.values.fold(0.0) { a, p -> a + p.qty.toDouble() * (prices[p.code] ?: p.avg) }   // ios: Mock/MockEngine.swift:257

    /** 주문에 쓸 수 있는 현금: 현금 − 남길 현금 (0 아래로 내려가지 않는다) */
    fun usable(cash: Double, keep: Double): Double = max(0.0, cash - keep)   // ios: Mock/MockEngine.swift:261

    // 선정

    @Serializable
    data class Pick(   // ios: Mock/MockEngine.swift:265
        var code: String,   // ios: Mock/MockEngine.swift:267
        var name: String,   // ios: Mock/MockEngine.swift:268
        var price: Double,            // ios: Mock/MockEngine.swift:269
        var budget: Double,           // ios: Mock/MockEngine.swift:270
        var qty: Int,                 // ios: Mock/MockEngine.swift:271
        var rank: Int,                // ios: Mock/MockEngine.swift:272
        var why: String,   // ios: Mock/MockEngine.swift:273
        var weight: Double,           // ios: Mock/MockEngine.swift:274
        var who: MockWho = MockWho.rule,   // ios: Mock/MockEngine.swift:275
        var short: Boolean? = null,       // ios: Mock/MockEngine.swift:276
    ) { val id: String get() = code }   // ios: Mock/MockEngine.swift:266
    @Serializable
    data class Skip(   // ios: Mock/MockEngine.swift:278
        var code: String,   // ios: Mock/MockEngine.swift:279
        var name: String,   // ios: Mock/MockEngine.swift:280
        var why: String,   // ios: Mock/MockEngine.swift:281
        var short: Boolean = false,      // ios: Mock/MockEngine.swift:282
        var hit: MockHit? = null,      // ios: Mock/MockEngine.swift:283
    )

    private data class Cand(val rank: Int, val h: MockHit, val px: Double, val one: Double, val held: MockPosition?, val room: Double)

    /** 관찰 결과에서 고른다. 순위 차례대로, 종목당 최대 비중만큼. 현금이 모자라도 고르고 현금 부족으로 표시한다 */
    fun select(scan: MockScan, ledger: MockLedger, rule: MockSelectRule, fees: MockFees,   // ios: Mock/MockEngine.swift:287
               prices: Map<String, Double>, excluded: Map<String, String>, soldToday: Set<String> = emptySet()): Pair<List<Pick>, List<Skip>> {   // ios: Mock/MockEngine.swift:288
        val l = ledger
        val eq = equity(l, prices)   // ios: Mock/MockEngine.swift:289
        val pendingBuy = l.pending.filter { it.side == MockSide.buy }.map { it.code }.toSet()   // ios: Mock/MockEngine.swift:290
        // 그날 훑은 종목에서 새로 rule.count 종목을 고른다 (대기·오늘 판 종목은 건너뛰고 다음 순위로)
        val avail = l.cash - rule.keepCash * eq   // ios: Mock/MockEngine.swift:292
        // 한 번 고를 때 모두 합쳐 평가액의 roundMax 까지. 그 몫을 실제로 고른 종목 수로 나눈다
        val round = (rule.roundMax ?: 0.10) * eq   // ios: Mock/MockEngine.swift:294
        // 0주로 빠진 종목 (빠지면 고른 수가 줄어 남은 종목 몫이 커지므로 다시 나눈다)
        val zero = mutableMapOf<String, Double>()   // ios: Mock/MockEngine.swift:296
        while (true) {   // ios: Mock/MockEngine.swift:297
            val skips = mutableListOf<Skip>()   // ios: Mock/MockEngine.swift:298
            val cands = mutableListOf<Cand>()   // ios: Mock/MockEngine.swift:299
            for ((i, h) in scan.hits.withIndex()) {   // ios: Mock/MockEngine.swift:300
                excluded[h.code]?.let { why -> skips.add(Skip(code = h.code, name = h.name, why = "예외 · " + why)) }; if (excluded[h.code] != null) continue   // ios: Mock/MockEngine.swift:301
                if (h.code in pendingBuy) { skips.add(Skip(code = h.code, name = h.name, why = "같은 종목 주문 대기 중")); continue }   // ios: Mock/MockEngine.swift:302
                // 판 날은 다시 사지 않는다 (같은 날 사고팔기 되풀이로 수수료·세금만 내는 일 막음)
                if (h.code in soldToday) { skips.add(Skip(code = h.code, name = h.name, why = "오늘 판 종목 · 내일부터 다시 고름")); continue }   // ios: Mock/MockEngine.swift:304
                if (cands.size >= rule.count) break   // ios: Mock/MockEngine.swift:305
                val px = prices[h.code] ?: h.price   // ios: Mock/MockEngine.swift:306
                val one = px * (1 + fees.commission)   // ios: Mock/MockEngine.swift:307
                if (px <= 0) { skips.add(Skip(code = h.code, name = h.name, why = "가격 확인 못 함")); continue }   // ios: Mock/MockEngine.swift:308
                // 보유 종목도 조건에 맞으면 고른다 (추가 주문). 종목당 한도는 보유까지 합쳐서 본다
                val held = l.positions[h.code]   // ios: Mock/MockEngine.swift:310
                val heldVal = (held?.qty ?: 0).toDouble() * px   // ios: Mock/MockEngine.swift:311
                val room = rule.maxPer * eq - heldVal   // ios: Mock/MockEngine.swift:312
                // 나눠 사기: 이미 든 종목은 이익 중일 때만 더 담는다
                if (held != null && rule.split != null && px <= held.avg) {   // ios: Mock/MockEngine.swift:314
                    skips.add(Skip(code = h.code, name = h.name, why = "더 담기 안 함 · 산 값 " + MockFmt.won(held.avg) + " 아래"))   // ios: Mock/MockEngine.swift:315
                    continue   // ios: Mock/MockEngine.swift:316
                }
                if (held != null && room < one) {   // ios: Mock/MockEngine.swift:318
                    skips.add(Skip(code = h.code, name = h.name, why = "종목당 한도 참 · 보유 " + cformat("%.0f%%", if (eq > 0) heldVal / eq * 100 else 0.0) + " · ${held.qty}주"))   // ios: Mock/MockEngine.swift:319
                    continue   // ios: Mock/MockEngine.swift:320
                }
                val zb = zero[h.code]   // ios: Mock/MockEngine.swift:322
                if (zb != null) {
                    // 한 주가 쓸 돈보다 비싸면 0주. 오류가 아니라 정상 결과로 남긴다
                    skips.add(Skip(code = h.code, name = h.name, why = "0주 · 한 주 " + MockFmt.won(px) + " > 쓸 돈 " + MockFmt.won(zb)))   // ios: Mock/MockEngine.swift:324
                    continue   // ios: Mock/MockEngine.swift:325
                }
                cands.add(Cand(i + 1, h, px, one, held, room))   // ios: Mock/MockEngine.swift:327
            }
            // 고르기는 현금을 보지 않는다. 종목당 한도에 걸린 종목이 못 쓴 몫은 나머지에 준다. 현금은 주문할 때 본다
            val budget = mutableMapOf<String, Double>()   // ios: Mock/MockEngine.swift:331
            var left = round; var k = cands.size   // ios: Mock/MockEngine.swift:332
            for (c in cands.sortedBy { it.room }) {   // ios: Mock/MockEngine.swift:333
                val b = min(c.room, left / k.toDouble())   // ios: Mock/MockEngine.swift:334
                budget[c.h.code] = b   // ios: Mock/MockEngine.swift:335
                left -= b; k -= 1   // ios: Mock/MockEngine.swift:336
            }
            // 나눠 사기: 종목 몫 중 한 번에 split 만큼만 산다
            rule.split?.let { sp -> if (sp > 0 && sp < 1) for ((c, b) in budget.toMap()) budget[c] = b * sp }   // ios: Mock/MockEngine.swift:339
            val small = cands.filter { (budget[it.h.code] ?: 0.0) < it.one }   // ios: Mock/MockEngine.swift:340
            if (small.isNotEmpty()) {   // ios: Mock/MockEngine.swift:341
                for (c in small) zero[c.h.code] = budget[c.h.code] ?: 0.0   // ios: Mock/MockEngine.swift:342
                continue   // ios: Mock/MockEngine.swift:343
            }
            val picks = mutableListOf<Pick>()   // ios: Mock/MockEngine.swift:345
            var planned = 0.0   // ios: Mock/MockEngine.swift:346
            for (c in cands) {   // ios: Mock/MockEngine.swift:347
                val qty = floor((budget[c.h.code] ?: 0.0) / c.one).toInt()   // ios: Mock/MockEngine.swift:348
                val spend = qty.toDouble() * c.one   // ios: Mock/MockEngine.swift:349
                planned += spend   // ios: Mock/MockEngine.swift:350
                val short = planned > avail     // ios: Mock/MockEngine.swift:351
                picks.add(Pick(code = c.h.code, name = c.h.name, price = c.px, budget = spend, qty = qty, rank = c.rank,   // ios: Mock/MockEngine.swift:352
                    why = "${c.rank}위 · " + c.h.why + (c.held?.let { " · 추가 · 보유 ${it.qty}주" } ?: "") + (if (short) " · 현금 부족" else ""),   // ios: Mock/MockEngine.swift:353
                    weight = if (eq > 0) spend / eq else 0.0, short = if (short) true else null))   // ios: Mock/MockEngine.swift:354
            }
            return picks to skips   // ios: Mock/MockEngine.swift:356
        }
    }

    // 호가와 체결

    @Serializable
    data class Level(var p: Double, var v: Double)   // ios: Mock/MockEngine.swift:362
    @Serializable
    class Book private constructor(   // ios: Mock/MockEngine.swift:363
        val asks: List<Level>,            // ios: Mock/MockEngine.swift:364
        val bids: List<Level>,            // ios: Mock/MockEngine.swift:365
        val at: MockDate?,   // ios: Mock/MockEngine.swift:366
    ) {
        companion object {
            operator fun invoke(asks: List<Level>, bids: List<Level>, at: MockDate?): Book = Book(   // ios: Mock/MockEngine.swift:367
                asks.filter { it.p > 0 && it.v > 0 }.sortedBy { it.p },   // ios: Mock/MockEngine.swift:368
                bids.filter { it.p > 0 && it.v > 0 }.sortedByDescending { it.p },   // ios: Mock/MockEngine.swift:369
                at)   // ios: Mock/MockEngine.swift:370
        }
        val bestAsk: Double? get() = asks.firstOrNull()?.p   // ios: Mock/MockEngine.swift:372
        val bestBid: Double? get() = bids.firstOrNull()?.p   // ios: Mock/MockEngine.swift:373
        override fun equals(other: Any?): Boolean = other is Book && other.asks == asks && other.bids == bids && other.at == at
        override fun hashCode(): Int = asks.hashCode() * 31 + bids.hashCode()
    }

    data class Fill(   // ios: Mock/MockEngine.swift:376
        var qty: Int = 0,   // ios: Mock/MockEngine.swift:377
        var amount: Double = 0.0,       // ios: Mock/MockEngine.swift:378
        var levels: Int = 0,          // ios: Mock/MockEngine.swift:379
        var taken: List<Level> = emptyList(),      // ios: Mock/MockEngine.swift:380
    ) { val avg: Double get() = if (qty > 0) amount / qty.toDouble() else 0.0 }   // ios: Mock/MockEngine.swift:381

    /** 호가를 차례로 채운다. 매수는 매도 1호가부터, 매도는 매수 1호가부터. 지정가는 그 값을 넘는 호가는 쓰지 않는다.
     *  cap: 매수에 쓸 수 있는 돈 (수수료 포함). 모자라면 그만큼만 */
    fun walk(side: MockSide, type: MockOrderType, qty: Int, limit: Double?, book: Book, fees: MockFees, cap: Double? = null): Fill {   // ios: Mock/MockEngine.swift:386
        val f = Fill(); var left = qty   // ios: Mock/MockEngine.swift:387
        val levels = if (side == MockSide.buy) book.asks else book.bids   // ios: Mock/MockEngine.swift:388
        val taken = mutableListOf<Level>()
        for (lv in levels) { if (left <= 0) break   // ios: Mock/MockEngine.swift:389
            if (type == MockOrderType.limit && limit != null) {   // ios: Mock/MockEngine.swift:390
                if (side == MockSide.buy && lv.p > limit) break   // ios: Mock/MockEngine.swift:391
                if (side == MockSide.sell && lv.p < limit) break   // ios: Mock/MockEngine.swift:392
            }
            var take = min(left, floor(lv.v).toInt())   // ios: Mock/MockEngine.swift:394
            if (side == MockSide.buy && cap != null) {   // ios: Mock/MockEngine.swift:395
                val room = cap - (f.amount + fees.fee(f.amount))   // ios: Mock/MockEngine.swift:396
                take = min(take, floor(room / (lv.p * (1 + fees.commission))).toInt())   // ios: Mock/MockEngine.swift:397
            }
            if (take <= 0) break   // ios: Mock/MockEngine.swift:399
            f.qty += take; f.amount += take.toDouble() * lv.p; f.levels += 1; left -= take   // ios: Mock/MockEngine.swift:400
            taken.add(Level(p = lv.p, v = take.toDouble()))   // ios: Mock/MockEngine.swift:401
        }
        f.taken = taken
        return f   // ios: Mock/MockEngine.swift:403
    }

    /** 지정가가 바로 닿는지 못 닿는지 한 줄 (미체결 이유) */
    fun waitWhy(side: MockSide, limit: Double, book: Book): String {   // ios: Mock/MockEngine.swift:407
        if (side == MockSide.buy) {   // ios: Mock/MockEngine.swift:408
            val a = book.bestAsk ?: return "매도 호가 없음"   // ios: Mock/MockEngine.swift:409
            return "매도 1호가 " + MockFmt.won(a) + "이 더 높음"   // ios: Mock/MockEngine.swift:410
        }
        val b = book.bestBid ?: return "매수 호가 없음"   // ios: Mock/MockEngine.swift:412
        return "매수 1호가 " + MockFmt.won(b) + "이 더 낮음"   // ios: Mock/MockEngine.swift:413
    }

    /** 매수 주문에 묶을 돈: 지정가면 지정가 × 수량, 시장가면 매도 1호가 × 수량에 2% 여유. 수수료 포함 */
    fun reserve(type: MockOrderType, qty: Int, limit: Double?, bestAsk: Double, fees: MockFees, cash: Double): Double {   // ios: Mock/MockEngine.swift:417
        val px = if (type == MockOrderType.limit) (limit ?: bestAsk) else bestAsk * 1.02   // ios: Mock/MockEngine.swift:418
        return min(cash, ceil(qty.toDouble() * px * (1 + fees.commission)))   // ios: Mock/MockEngine.swift:419
    }

    // 실전 판단 (주문 직전 확인 · 정정 · 유의사항 · 보유)

    /** 주문 순간 예상 평균가: 지금 호가로 끝까지 채운 값 (지정가는 그 값까지만). 한 주도 못 채우면 null */
    fun expect(side: MockSide, type: MockOrderType, qty: Int, limit: Double?, book: Book, fees: MockFees): Double? {   // ios: Mock/MockEngine.swift:425
        val f = walk(side = side, type = type, qty = qty, limit = limit, book = book, fees = fees)   // ios: Mock/MockEngine.swift:426
        return if (f.qty > 0) f.avg else null   // ios: Mock/MockEngine.swift:427
    }

    /** 예상가 대비 실제 체결가 차이 (불리하면 +). 매수: 실제/예상 − 1, 매도: 1 − 실제/예상 */
    fun slipGap(side: MockSide, expect: Double, actual: Double): Double {   // ios: Mock/MockEngine.swift:431
        if (expect <= 0) return 0.0   // ios: Mock/MockEngine.swift:432
        return if (side == MockSide.buy) actual / expect - 1 else 1 - actual / expect   // ios: Mock/MockEngine.swift:433
    }

    /** 주문 직전 확인: 지금 호가로 다 채우면 나갈 돈·수수료·세금·1호가 대비 미끄러짐. 못 채운 수량은 남는다 */
    data class Preview(   // ios: Mock/MockEngine.swift:437
        var qty: Int = 0,                  // ios: Mock/MockEngine.swift:438
        var avg: Double = 0.0,          // ios: Mock/MockEngine.swift:439
        var amount: Double = 0.0,       // ios: Mock/MockEngine.swift:440
        var fee: Double = 0.0,   // ios: Mock/MockEngine.swift:441
        var tax: Double = 0.0,   // ios: Mock/MockEngine.swift:442
        var slip: Double = 0.0,         // ios: Mock/MockEngine.swift:443
    ) { val total: Double get() = amount + fee + tax }   // ios: Mock/MockEngine.swift:444
    fun preview(side: MockSide, type: MockOrderType, qty: Int, limit: Double?, book: Book, fees: MockFees): Preview {   // ios: Mock/MockEngine.swift:446
        val f = walk(side = side, type = type, qty = qty, limit = limit, book = book, fees = fees)   // ios: Mock/MockEngine.swift:447
        if (f.qty <= 0) return Preview()   // ios: Mock/MockEngine.swift:448
        val best = (if (side == MockSide.buy) book.bestAsk else book.bestBid) ?: f.avg   // ios: Mock/MockEngine.swift:449
        return Preview(qty = f.qty, avg = f.avg, amount = f.amount, fee = fees.fee(f.amount), tax = if (side == MockSide.sell) fees.tax(f.amount) else 0.0,   // ios: Mock/MockEngine.swift:450
            slip = slipGap(side = side, expect = best, actual = f.avg))   // ios: Mock/MockEngine.swift:451
    }

    /** 대기 주문 정정: 남은 수량·지정가를 바꾼다 (정정하면 지정가). 매수는 묶은 돈을 새로 셈하고 모자라면 이유를 돌려준다 */
    fun modify(l: MockLedger, orderIndex: Int, qty: Int, limit: Double, fees: MockFees): String? {   // ios: Mock/MockEngine.swift:455
        val i = orderIndex
        if (i !in l.pending.indices || qty < 1 || limit <= 0) return "수량·가격을 확인해 주세요"   // ios: Mock/MockEngine.swift:456
        val o = l.pending[i].copy()   // ios: Mock/MockEngine.swift:457
        val lim = snap(limit, side = o.side)   // ios: Mock/MockEngine.swift:458
        if (o.side == MockSide.buy) {   // ios: Mock/MockEngine.swift:459
            val need = ceil(qty.toDouble() * lim * (1 + fees.commission))   // ios: Mock/MockEngine.swift:460
            if (need - o.reserved > l.cash) return "현금 부족 · 더 필요 " + MockFmt.won(need - o.reserved)   // ios: Mock/MockEngine.swift:461
            l.cash -= need - o.reserved   // ios: Mock/MockEngine.swift:462
            o.reserved = need   // ios: Mock/MockEngine.swift:463
        } else {   // ios: Mock/MockEngine.swift:464
            val other = l.pending.withIndex().filter { it.index != i && it.value.code == o.code && it.value.side == MockSide.sell }.sumOf { it.value.qty }
            if (qty + other > (l.positions[o.code]?.qty ?: 0)) return "보유보다 많이 팔 수 없어요"   // ios: Mock/MockEngine.swift:466
        }
        o.qty = qty; o.type = MockOrderType.limit; o.limit = lim; o.until = null; o.waitWhy = null   // ios: Mock/MockEngine.swift:468
        l.pending = l.pending.toMutableList().also { it[i] = o }   // ios: Mock/MockEngine.swift:469
        return null   // ios: Mock/MockEngine.swift:470
    }

    /** 유의사항 종류 → 짧은 이름 (모르는 값은 그대로) */
    fun warnName(t: String): String = when (t) {   // ios: Mock/MockEngine.swift:474,475
        "SUSPENDED" -> "거래정지"   // ios: Mock/MockEngine.swift:476
        "LIQUIDATION_TRADING" -> "정리매매"   // ios: Mock/MockEngine.swift:477
        "INVESTMENT_RISK" -> "투자위험"   // ios: Mock/MockEngine.swift:478
        "INVESTMENT_WARNING" -> "투자경고"   // ios: Mock/MockEngine.swift:479
        "OVERHEATED" -> "단기과열"   // ios: Mock/MockEngine.swift:480
        "VI_STATIC", "VI_DYNAMIC", "VI_STATIC_AND_DYNAMIC" -> "VI"   // ios: Mock/MockEngine.swift:481
        else -> t   // ios: Mock/MockEngine.swift:482
    }

    /** 고르기에서 뺄 유의사항 (거래정지·정리매매·투자위험). 빼면 이유, 아니면 null */
    fun warnBlock(ts: List<String>): String? {   // ios: Mock/MockEngine.swift:487
        val hard = ts.filter { it in listOf("SUSPENDED", "LIQUIDATION_TRADING", "INVESTMENT_RISK") }   // ios: Mock/MockEngine.swift:488
        return if (hard.isEmpty()) null else "유의 거름 · " + hard.joinToString("·") { warnName(it) }   // ios: Mock/MockEngine.swift:489
    }

    /** 지금 가격에 다 팔면 남는 손익 (수수료·세금 뒤) */
    fun afterTax(p: MockPosition, price: Double, fees: MockFees): Double {   // ios: Mock/MockEngine.swift:493
        val amt = p.qty.toDouble() * price   // ios: Mock/MockEngine.swift:494
        return amt - fees.fee(amt) - fees.tax(amt) - p.cost   // ios: Mock/MockEngine.swift:495
    }

    /** 손절선까지 남은 비율: 지금 가격이 손절 가격(평균가 × (1 − stop))보다 몇 % 위인가. 음수면 이미 넘음 */
    fun stopRoom(p: MockPosition, price: Double, stop: Double): Double {   // ios: Mock/MockEngine.swift:499
        val line = p.avg * (1 - stop)   // ios: Mock/MockEngine.swift:500
        return if (line > 0) price / line - 1 else 0.0   // ios: Mock/MockEngine.swift:501
    }

    // 장부에 적기

    /** 매수 체결: 묶은 돈에서 쓰고, 다 채우면 남은 돈을 현금으로 돌린다 */
    fun applyBuy(l: MockLedger, orderIndex: Int, fill: Fill, fees: MockFees, at: MockDate, test: Boolean): MockEvent {   // ios: Mock/MockEngine.swift:507
        val i = orderIndex
        val o = l.pending[i].copy()   // ios: Mock/MockEngine.swift:508
        val fee = fees.fee(fill.amount)   // ios: Mock/MockEngine.swift:509
        o.reserved -= fill.amount + fee   // ios: Mock/MockEngine.swift:510
        o.qty -= fill.qty   // ios: Mock/MockEngine.swift:511
        val day = MockFmt.day(at)   // ios: Mock/MockEngine.swift:512
        val pos = l.positions.toMutableMap()
        val p0 = pos[o.code]
        if (p0 != null) {   // ios: Mock/MockEngine.swift:513
            val p = p0.copy()
            if (test) { p.tQty = (p.tQty ?: 0) + fill.qty; p.tCost = (p.tCost ?: 0.0) + fill.amount + fee; p.tGross = (p.tGross ?: 0.0) + fill.amount }   // ios: Mock/MockEngine.swift:514
            p.qty += fill.qty; p.cost += fill.amount + fee; p.gross += fill.amount; p.test = p.test || test   // ios: Mock/MockEngine.swift:515
            pos[o.code] = p   // ios: Mock/MockEngine.swift:516
        } else {   // ios: Mock/MockEngine.swift:517
            pos[o.code] = MockPosition(code = o.code, name = o.name, market = o.market, qty = fill.qty, cost = fill.amount + fee, gross = fill.amount,   // ios: Mock/MockEngine.swift:518
                opened = at, openedDay = day, who = o.who, test = test, buyRef = o.id,   // ios: Mock/MockEngine.swift:519
                tQty = if (test) fill.qty else null, tCost = if (test) fill.amount + fee else null, tGross = if (test) fill.amount else null,   // ios: Mock/MockEngine.swift:520
                why = if (o.reason.isEmpty()) null else o.reason, conds = o.conds, peak = fill.avg, low = fill.avg, entry = o.entry)   // ios: Mock/MockEngine.swift:521
        }
        l.positions = pos
        l.fees += fee   // ios: Mock/MockEngine.swift:523
        val pend = l.pending.toMutableList()
        if (o.qty <= 0) {   // ios: Mock/MockEngine.swift:524
            l.cash += max(0.0, o.reserved)   // ios: Mock/MockEngine.swift:525
            pend.removeAt(i)   // ios: Mock/MockEngine.swift:526
        } else {   // ios: Mock/MockEngine.swift:527
            pend[i] = o   // ios: Mock/MockEngine.swift:528
        }
        l.pending = pend
        return MockEvent(at = at, ledger = l.id, book = l.book, kind = MockEvent.Kind.fill, who = o.who, code = o.code, name = o.name,   // ios: Mock/MockEngine.swift:530
            text = "${o.type.label} ${fill.qty}주 · 평균 ${MockFmt.won(fill.avg)}" + (if (fill.levels > 1) " · 호가 ${fill.levels}단 채움" else "") + " · 수수료 ${MockFmt.won(fee)}",   // ios: Mock/MockEngine.swift:531
            test = test, side = MockSide.buy, qty = fill.qty, price = fill.avg, amount = fill.amount, fee = fee, ref = o.id)   // ios: Mock/MockEngine.swift:532
    }

    /** 매도 체결: 판 몫의 들어간 돈을 비율로 덜고 손익을 남긴다. 성적은 그 몫을 산 쪽(규칙·손·AI)으로 센다 */
    fun applySell(l: MockLedger, orderIndex: Int, fill: Fill, fees: MockFees, at: MockDate, test: Boolean): MockEvent? {   // ios: Mock/MockEngine.swift:536
        val i = orderIndex
        val o = l.pending[i].copy()   // ios: Mock/MockEngine.swift:537
        val p0 = l.positions[o.code]   // ios: Mock/MockEngine.swift:538
        if (p0 == null || p0.qty <= 0) { l.pending = l.pending.toMutableList().also { it.removeAt(i) }; return null }   // ios: Mock/MockEngine.swift:538
        val p = p0.copy()
        val n = min(fill.qty, p.qty)   // ios: Mock/MockEngine.swift:539
        val amount = fill.avg * n.toDouble()   // ios: Mock/MockEngine.swift:540
        val fee = fees.fee(amount); val tax = fees.tax(amount)   // ios: Mock/MockEngine.swift:541
        val share = n.toDouble() / p.qty.toDouble()   // ios: Mock/MockEngine.swift:542
        val basis = p.cost * share; val gross = p.gross * share   // ios: Mock/MockEngine.swift:543
        val proceeds = amount - fee - tax   // ios: Mock/MockEngine.swift:544
        p.qty -= n; p.cost -= basis; p.gross -= gross   // ios: Mock/MockEngine.swift:545
        p.tQty?.let { tq -> p.tQty = tq - (tq.toDouble() * share).swRound().toInt(); p.tCost = p.tCost?.let { it * (1 - share) }; p.tGross = p.tGross?.let { it * (1 - share) } }   // ios: Mock/MockEngine.swift:546
        val opener = p.who; val wasTest = p.test; val buyWhy = p.why; val conds = p.conds; val entry = p.entry; val low = p.low   // ios: Mock/MockEngine.swift:547
        val pos = l.positions.toMutableMap()
        if (p.qty <= 0) pos.remove(o.code) else pos[o.code] = p   // ios: Mock/MockEngine.swift:548
        l.positions = pos
        // 결제 T+2: 판 돈은 2영업일 뒤 현금이 된다 (장 밖 시험은 바로)
        if (test) l.cash += proceeds   // ios: Mock/MockEngine.swift:550
        else l.unsettled = (l.unsettled ?: emptyList()) + listOf(MockSettle(amount = proceeds, day = settleDay(at), code = o.code))   // ios: Mock/MockEngine.swift:551
        l.fees += fee; l.taxes += tax   // ios: Mock/MockEngine.swift:552
        o.qty -= n   // ios: Mock/MockEngine.swift:553
        l.pending = l.pending.toMutableList().also { if (o.qty <= 0 || l.positions[o.code] == null) it.removeAt(i) else it[i] = o }   // ios: Mock/MockEngine.swift:554
        return MockEvent(at = at, ledger = l.id, book = l.book, kind = MockEvent.Kind.fill, who = o.who, code = o.code, name = o.name,   // ios: Mock/MockEngine.swift:555
            text = "${o.type.label} ${n}주 · 평균 ${MockFmt.won(fill.avg)} · 수수료 ${MockFmt.won(fee)} · 세금 ${MockFmt.won(tax)}",   // ios: Mock/MockEngine.swift:556
            test = test || wasTest, side = MockSide.sell, qty = n, price = fill.avg, amount = amount, fee = fee, tax = tax,   // ios: Mock/MockEngine.swift:557
            pnl = proceeds - basis, pnlGross = amount - gross, basis = basis, opener = opener, ref = o.id,   // ios: Mock/MockEngine.swift:558
            conds = conds, buyWhy = buyWhy, exit = exitName(o.reason), exitWhy = if (o.reason.isEmpty()) null else o.reason, entry = entry, low = low)   // ios: Mock/MockEngine.swift:559
    }

    /** 판 이유 이름: 주문 이유의 첫 토막 ("손절 · 산 값 대비 −5.1%" → "손절") */
    fun exitName(reason: String): String? {   // ios: Mock/MockEngine.swift:563
        val h = reason.split(" · ").firstOrNull()?.trim() ?: ""   // ios: Mock/MockEngine.swift:564
        return if (h.isEmpty()) null else h   // ios: Mock/MockEngine.swift:565
    }

    // 팔기 신호

    data class SellSig(   // ios: Mock/MockEngine.swift:570
        var code: String,   // ios: Mock/MockEngine.swift:571
        var why: String,   // ios: Mock/MockEngine.swift:572
        var qty: Int? = null,          // ios: Mock/MockEngine.swift:573
    ) { val half: Boolean get() = qty != null }   // ios: Mock/MockEngine.swift:574

    /** 보유 종목마다 팔기 규칙을 지금 가격으로 본다. 예외 종목은 규칙이 팔지 않는다.
     *  차례: 손절·흔들림 손절 → 마감 정리(15:00) → 이유 꺼짐 → 익절(첫 번은 반) → 되밀림(반 판 뒤) → 움직임 없음 (2026-10-29) */
    fun sellSignals(l: MockLedger, rule: MockSellRule, prices: Map<String, Double>, excluded: Map<String, String>, today: String,   // ios: Mock/MockEngine.swift:580
                    seg: MockClock.Seg = MockClock.Seg.pick, gone: Map<String, String> = emptyMap(), now: MockDate = mockNow()): List<SellSig> {   // ios: Mock/MockEngine.swift:581
        if (!rule.on) return emptyList()   // ios: Mock/MockEngine.swift:582
        val selling = l.pending.filter { it.side == MockSide.sell }.map { it.code }.toSet()   // ios: Mock/MockEngine.swift:583
        val out = mutableListOf<SellSig>()   // ios: Mock/MockEngine.swift:584
        for (p in l.positions.values.sortedBy { it.code }) { if (p.code in selling || excluded[p.code] != null || p.who == MockWho.hand) continue   // ios: Mock/MockEngine.swift:585
            val px = prices[p.code] ?: continue; if (px <= 0 || p.avg <= 0) continue   // ios: Mock/MockEngine.swift:586
            val r = px / p.avg - 1   // ios: Mock/MockEngine.swift:587
            // 규칙이 서기 전에 산 몫은 손절·익절만 (산 때 기준을 지킨다)
            val old = rule.since?.let { p.openedDay < it } ?: false   // ios: Mock/MockEngine.swift:589
            rule.stopLoss?.let { s -> if (r <= -s) { out.add(SellSig(code = p.code, why = "손절 · 산 값 대비 " + MockFmt.pct(r, digits = 1))) } }; if (rule.stopLoss != null && r <= -rule.stopLoss!!) continue   // ios: Mock/MockEngine.swift:590
            val k = rule.volK; val rg = p.entry?.range20   // ios: Mock/MockEngine.swift:591
            if (k != null && rg != null && rg > 0 && r <= -k * rg) {   // ios: Mock/MockEngine.swift:591
                out.add(SellSig(code = p.code, why = "손절 · 흔들림 · 하루 폭 " + MockFmt.pct(rg, digits = 1) + " × " + MockFmt.num(k) + " · 산 값 대비 " + MockFmt.pct(r, digits = 1))); continue   // ios: Mock/MockEngine.swift:592
            }
            // 정리 구간(15:00)에만. 앱이 꺼져 마감을 놓친 몫도 장중에 바로 팔지 않고 그날 산 몫과 함께 정리한다 (2026-10-29 드링커님 결정)
            if (!old && rule.closeOut == true && seg == MockClock.Seg.tidy) {   // ios: Mock/MockEngine.swift:595
                val wh = if (p.openedDay < today) md(p.openedDay) + "에 산 몫 · 지난 마감을 놓쳐 오늘 15:00에" else "15:00 정리 구간"   // ios: Mock/MockEngine.swift:596
                out.add(SellSig(code = p.code, why = "마감 정리 · " + wh + " · 산 값 대비 " + MockFmt.pct(r, digits = 1))); continue   // ios: Mock/MockEngine.swift:597
            }
            val g = gone[p.code]; if (!old && rule.gone == true && g != null) { out.add(SellSig(code = p.code, why = "이유 꺼짐 · " + g)); continue }   // ios: Mock/MockEngine.swift:599
            val t = rule.takeProfit   // ios: Mock/MockEngine.swift:600
            if (t != null && r >= t && p.took != true) {   // ios: Mock/MockEngine.swift:600
                val q = if (p.qty >= 2) p.qty / 2 else null   // ios: Mock/MockEngine.swift:601
                out.add(SellSig(code = p.code, why = "익절 · 산 값 대비 " + MockFmt.pct(r, digits = 1) + (if (q != null) " · 반" else ""), qty = q)); continue   // ios: Mock/MockEngine.swift:602
            }
            val tr = rule.trail; val pk = p.peak   // ios: Mock/MockEngine.swift:604
            if (!old && tr != null && p.took == true && pk != null && pk > 0 && px <= pk * (1 - tr)) {   // ios: Mock/MockEngine.swift:604
                out.add(SellSig(code = p.code, why = "되밀림 · 고점 " + MockFmt.won(pk) + " 대비 " + MockFmt.pct(px / pk - 1, digits = 1))); continue   // ios: Mock/MockEngine.swift:605
            }
            val m = rule.idleMin; val b = rule.idleBand   // ios: Mock/MockEngine.swift:607
            if (!old && m != null && b != null && now - p.opened >= (m * 60).toDouble() && abs(r) <= b) {   // ios: Mock/MockEngine.swift:607
                out.add(SellSig(code = p.code, why = "움직임 없음 · ${m}분 · 산 값 대비 " + MockFmt.pct(r, digits = 1))); continue   // ios: Mock/MockEngine.swift:608
            }
            val d = rule.maxDays; if (!old && d != null && weekdays(from = p.openedDay, to = today) >= d) { out.add(SellSig(code = p.code, why = "보유 ${d}일 넘음")); continue }   // ios: Mock/MockEngine.swift:610
        }
        return out   // ios: Mock/MockEngine.swift:612
    }

    /** "2026-10-27" → "10/27" */
    private fun md(day: String): String {   // ios: Mock/MockEngine.swift:616
        val p = day.split("-")   // ios: Mock/MockEngine.swift:617
        val m = p.getOrNull(1)?.toIntOrNull(); val d = p.getOrNull(2)?.toIntOrNull(); if (p.size != 3 || m == null || d == null) return day   // ios: Mock/MockEngine.swift:618
        return "$m/$d"   // ios: Mock/MockEngine.swift:619
    }

    /** 산 이유가 약해졌나: 켠 관찰 조각 중 '약한 쪽'으로 벗어난 조각 글. 위 문턱을 넘는 것(더 세짐)은 꺼짐이 아니다 */
    fun weak(b: MockBundle, bars: List<MockBar>, use: Double? = null): String? {   // ios: Mock/MockEngine.swift:624
        for (p in b.pieces) {   // ios: Mock/MockEngine.swift:625
            val v0 = value(p, bars) ?: continue   // ios: Mock/MockEngine.swift:626
            val v = use?.let { scaled(p, v0, it) } ?: v0   // ios: Mock/MockEngine.swift:627
            if (p.kind == MockPiece.Kind.ma) { if (if (p.above) v <= 0 else v >= 0) return short(p, v); continue }   // ios: Mock/MockEngine.swift:628
            p.lo?.let { lo -> if (v < lo) return short(p, v) + " (문턱 " + MockFmt.num(lo) + ")" }   // ios: Mock/MockEngine.swift:629
        }
        return null   // ios: Mock/MockEngine.swift:631
    }

    // 결제 (T+2)

    private fun weekend(d: LocalDate): Boolean = d.dayOfWeek == DayOfWeek.SUNDAY || d.dayOfWeek == DayOfWeek.SATURDAY

    /** 판 날부터 2영업일 뒤 (주말만 뺀다. 공휴일은 모른다) */
    fun settleDay(at: MockDate): String {   // ios: Mock/MockEngine.swift:637
        var d = MockFmt.inst(at).atZone(MockFmt.kst).toLocalDate(); var n = 0   // ios: Mock/MockEngine.swift:638,639
        while (n < 2) {   // ios: Mock/MockEngine.swift:640
            d = d.plusDays(1)   // ios: Mock/MockEngine.swift:641
            if (!weekend(d)) n += 1   // ios: Mock/MockEngine.swift:642
        }
        return d.toString()   // ios: Mock/MockEngine.swift:645
    }

    /** 결제일이 된 돈을 현금으로 옮긴다. 옮긴 건들을 돌려준다 */
    fun settle(l: MockLedger, today: String): List<MockSettle> {   // ios: Mock/MockEngine.swift:649
        val due = (l.unsettled ?: emptyList()).filter { it.day <= today }   // ios: Mock/MockEngine.swift:650
        if (due.isEmpty()) return emptyList()   // ios: Mock/MockEngine.swift:651
        l.cash += due.fold(0.0) { a, s -> a + s.amount }   // ios: Mock/MockEngine.swift:652
        val rest = (l.unsettled ?: emptyList()).filter { it.day > today }   // ios: Mock/MockEngine.swift:653
        l.unsettled = if (rest.isEmpty()) null else rest   // ios: Mock/MockEngine.swift:654
        return due   // ios: Mock/MockEngine.swift:655
    }

    // 잔고 맞추기

    /** 체결 기록을 처음부터 다시 더해 장부와 견준다. 현금(+묶인 돈+결제 대기)과 종목별 수량. 어긋난 곳을 한 줄로 (맞으면 null) */
    fun reconcile(l: MockLedger, events: List<MockEvent>): String? {   // ios: Mock/MockEngine.swift:662
        var cash = l.startCash; val qty = mutableMapOf<String, Int>(); var n = 0   // ios: Mock/MockEngine.swift:663
        for (e in events) { if (e.ledger != l.id) continue   // ios: Mock/MockEngine.swift:664
            val q = e.qty; val a = e.amount; val code = e.code
            if (e.kind == MockEvent.Kind.fill && q != null && a != null && code != null) {   // ios: Mock/MockEngine.swift:665
                n += 1   // ios: Mock/MockEngine.swift:666
                if (e.side == MockSide.buy) { cash -= a + (e.fee ?: 0.0); qty[code] = (qty[code] ?: 0) + q }   // ios: Mock/MockEngine.swift:667
                else if (e.side == MockSide.sell) { cash += a - (e.fee ?: 0.0) - (e.tax ?: 0.0); qty[code] = (qty[code] ?: 0) - q }   // ios: Mock/MockEngine.swift:668
            } else if (e.kind == MockEvent.Kind.cancel && e.side == MockSide.buy && e.text.startsWith("시험 몫 뺌") && q != null && code != null) {   // ios: Mock/MockEngine.swift:669
                n += 1   // ios: Mock/MockEngine.swift:670
                qty[code] = (qty[code] ?: 0) - q   // ios: Mock/MockEngine.swift:671
                cash += e.amount ?: refund(e.text)   // ios: Mock/MockEngine.swift:672
            }
        }
        val out = mutableListOf<String>()   // ios: Mock/MockEngine.swift:675
        val have = l.cash + l.reserved + l.settling   // ios: Mock/MockEngine.swift:676
        // 원 아래 버림이 체결마다 쌓일 수 있어 체결 수만큼 원을 봐준다
        if (abs(have - cash) > max(1, n).toDouble()) out.add("현금 장부 " + MockFmt.won(have) + " · 기록 " + MockFmt.won(cash))   // ios: Mock/MockEngine.swift:678
        for (code in (qty.keys + l.positions.keys).toSortedSet()) {   // ios: Mock/MockEngine.swift:679
            val a = l.positions[code]?.qty ?: 0; val b = qty[code] ?: 0   // ios: Mock/MockEngine.swift:680
            if (a != b) out.add((l.positions[code]?.name ?: code) + " 장부 ${a}주 · 기록 ${b}주")   // ios: Mock/MockEngine.swift:681
        }
        return if (out.isEmpty()) null else out.joinToString(" · ")   // ios: Mock/MockEngine.swift:683
    }
    /** 옛 '시험 몫 뺌' 줄의 글에서 돌린 돈을 읽는다 ("… · 돌린 돈 12,345원") */
    private fun refund(t: String): Double {   // ios: Mock/MockEngine.swift:686
        val r = t.indexOf("돌린 돈 "); if (r < 0) return 0.0   // ios: Mock/MockEngine.swift:687
        return t.substring(r + "돌린 돈 ".length).filter { it.isDigit() }.toDoubleOrNull() ?: 0.0   // ios: Mock/MockEngine.swift:688
    }

    /** 두 날짜 사이 평일 수 (시작 날 빼고 끝 날 넣음). 공휴일은 모른다 */
    fun weekdays(from: String, to: String): Int {   // ios: Mock/MockEngine.swift:692
        var d = runCatching { LocalDate.parse(from) }.getOrNull(); val e = runCatching { LocalDate.parse(to) }.getOrNull()
        if (d == null || e == null || d >= e) return 0   // ios: Mock/MockEngine.swift:694
        var n = 0   // ios: Mock/MockEngine.swift:695,696
        while (d!! < e) {   // ios: Mock/MockEngine.swift:697
            d = d.plusDays(1)   // ios: Mock/MockEngine.swift:698
            if (!weekend(d)) n += 1   // ios: Mock/MockEngine.swift:699
        }
        return n   // ios: Mock/MockEngine.swift:702
    }

    // 국내 호가 단위 (2023-01 이후 코스피·코스닥 같은 표)

    fun tick(p: Double): Double = when {   // ios: Mock/MockEngine.swift:707,708
        p < 2_000 -> 1.0   // ios: Mock/MockEngine.swift:709
        p < 5_000 -> 5.0   // ios: Mock/MockEngine.swift:710
        p < 20_000 -> 10.0   // ios: Mock/MockEngine.swift:711
        p < 50_000 -> 50.0   // ios: Mock/MockEngine.swift:712
        p < 200_000 -> 100.0   // ios: Mock/MockEngine.swift:713
        p < 500_000 -> 500.0   // ios: Mock/MockEngine.swift:714
        else -> 1_000.0   // ios: Mock/MockEngine.swift:715
    }
    /** 지정가를 호가 단위에 맞춘다 (매수는 내림, 매도는 올림: 덜 유리한 쪽으로 붙지 않게) */
    fun snap(p: Double, side: MockSide): Double {   // ios: Mock/MockEngine.swift:719
        val t = tick(p)   // ios: Mock/MockEngine.swift:720
        return if (side == MockSide.buy) floor(p / t) * t else ceil(p / t) * t   // ios: Mock/MockEngine.swift:721
    }

    // 성적

    data class Stat(   // ios: Mock/MockEngine.swift:726
        var trades: Int = 0, var wins: Int = 0,   // ios: Mock/MockEngine.swift:727
        var pnl: Double = 0.0, var pnlGross: Double = 0.0, var winSum: Double = 0.0, var lossSum: Double = 0.0, var basis: Double = 0.0,   // ios: Mock/MockEngine.swift:728
    ) {
        val winRate: Double? get() = if (trades > 0) wins.toDouble() / trades.toDouble() else null   // ios: Mock/MockEngine.swift:729
        val ret: Double? get() = if (basis > 0) pnl / basis else null   // ios: Mock/MockEngine.swift:730
        val retGross: Double? get() = if (basis > 0) pnlGross / basis else null   // ios: Mock/MockEngine.swift:731
        fun add(e: MockEvent) {   // ios: Mock/MockEngine.swift:732
            val p = e.pnl ?: return   // ios: Mock/MockEngine.swift:733
            trades += 1; pnl += p; pnlGross += e.pnlGross ?: p; basis += e.basis ?: 0.0   // ios: Mock/MockEngine.swift:734
            if (p > 0) { wins += 1; winSum += p } else { lossSum += p }   // ios: Mock/MockEngine.swift:735
        }
    }

    // 꺼 둔 조건 저절로 켜기 (2026-10-29)

    /** 켜기 기준: 정식 규칙 매매 30건 · 5일 넘게. 켰다면 손익 합이 나아지고, 걸린 매매가 3건 넘을 때만 */
    const val armTrades = 30; const val armDays = 5; const val armHits = 3   // ios: Mock/MockEngine.swift:742

    data class Arm(   // ios: Mock/MockEngine.swift:744
        var key: String,              // ios: Mock/MockEngine.swift:745
        var hits: Int,                // ios: Mock/MockEngine.swift:746
        var gain: Double,             // ios: Mock/MockEngine.swift:747
    )
    data class Arms(val trades: Int, val days: Int, val arms: List<Arm>)

    /** 지난 매매로 '켰다면'을 센다. vol: 들고 있던 중 가장 낮은 값이 흔들림 손절선에 닿았으면 그 선에서 판 것으로.
     *  streak · wall: 걸렸을 매매는 사지 않은 것으로 (손익 0) */
    fun arms(events: List<MockEvent>, ledger: String, fees: MockFees, volK: Double, streak: Int, wall: Double): Arms {   // ios: Mock/MockEngine.swift:752
        val ts = events.filter { it.ledger == ledger && it.kind == MockEvent.Kind.fill && it.side == MockSide.sell && !it.test && (it.opener ?: it.who) == MockWho.rule && it.entry != null }   // ios: Mock/MockEngine.swift:753
        val v = Arm(key = "vol", hits = 0, gain = 0.0); val s = Arm(key = "streak", hits = 0, gain = 0.0); val w = Arm(key = "wall", hits = 0, gain = 0.0)   // ios: Mock/MockEngine.swift:754
        for (e in ts) {   // ios: Mock/MockEngine.swift:755
            val pnl = e.pnl ?: continue; val en = e.entry ?: continue   // ios: Mock/MockEngine.swift:756
            en.streak?.let { n -> if (n >= streak) { s.hits += 1; s.gain -= pnl } }   // ios: Mock/MockEngine.swift:757
            en.wall?.let { x -> if (x > wall) { w.hits += 1; w.gain -= pnl } }   // ios: Mock/MockEngine.swift:758
            val rg = en.range20; val lo = e.low; val q = e.qty; val amt = e.amount; val basis = e.basis   // ios: Mock/MockEngine.swift:759
            if (rg != null && rg > 0 && lo != null && q != null && q > 0 && amt != null && basis != null) {   // ios: Mock/MockEngine.swift:759
                val buyAvg = (amt - (e.pnlGross ?: 0.0)) / q.toDouble()   // ios: Mock/MockEngine.swift:760
                val line = buyAvg * (1 - volK * rg)   // ios: Mock/MockEngine.swift:761
                if (lo <= line) {   // ios: Mock/MockEngine.swift:762
                    val got = q.toDouble() * line   // ios: Mock/MockEngine.swift:763
                    v.hits += 1; v.gain += got - fees.fee(got) - fees.tax(got) - basis - pnl   // ios: Mock/MockEngine.swift:764
                }
            }
        }
        return Arms(ts.size, ts.map { MockFmt.day(it.at) }.toSet().size, listOf(v, s, w))   // ios: Mock/MockEngine.swift:768
    }

    /** 매도 체결만 매매 한 건으로 센다. test: 시험 체결만(참)·정식만(거짓) */
    fun stat(events: List<MockEvent>, ledger: String, who: MockWho? = null, test: Boolean = false, since: MockDate? = null): Stat {   // ios: Mock/MockEngine.swift:772
        val s = Stat()   // ios: Mock/MockEngine.swift:773
        for (e in events) { if (!(e.ledger == ledger && e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && e.test == test)) continue   // ios: Mock/MockEngine.swift:774
            if (who != null && (e.opener ?: e.who) != who) continue   // ios: Mock/MockEngine.swift:775
            if (since != null && e.at < since) continue   // ios: Mock/MockEngine.swift:776
            s.add(e)   // ios: Mock/MockEngine.swift:777
        }
        return s   // ios: Mock/MockEngine.swift:779
    }

    enum class Unit {   // ios: Mock/MockEngine.swift:782
        hour, day, week, month, year;   // ios: Mock/MockEngine.swift:783
        val id: String get() = name   // ios: Mock/MockEngine.swift:784
        val label: String get() = when (this) {   // ios: Mock/MockEngine.swift:785,786
            hour -> "시간"   // ios: Mock/MockEngine.swift:787
            day -> "일"   // ios: Mock/MockEngine.swift:788
            week -> "주"   // ios: Mock/MockEngine.swift:789
            month -> "월"   // ios: Mock/MockEngine.swift:790
            year -> "년"   // ios: Mock/MockEngine.swift:791
        }
    }

    /** 기간 한 칸의 이름 (한국 시각) */
    fun bucket(d: MockDate, u: Unit): String {   // ios: Mock/MockEngine.swift:797
        val c = MockFmt.inst(d).atZone(MockFmt.kst)   // ios: Mock/MockEngine.swift:798,799
        return when (u) {   // ios: Mock/MockEngine.swift:800
            Unit.hour -> cformat("%d/%d %02d시", c.monthValue, c.dayOfMonth, c.hour)   // ios: Mock/MockEngine.swift:801
            Unit.day -> cformat("%d/%d", c.monthValue, c.dayOfMonth)   // ios: Mock/MockEngine.swift:802
            Unit.week -> cformat("%d년 %d주", c.get(IsoFields.WEEK_BASED_YEAR), c.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))   // ios: Mock/MockEngine.swift:803
            Unit.month -> cformat("%d년 %d월", c.year, c.monthValue)   // ios: Mock/MockEngine.swift:804
            Unit.year -> "${c.year}년"   // ios: Mock/MockEngine.swift:805
        }
    }

    /** 기간별 성적 (최근 칸부터). 정식 체결만 */
    fun periods(events: List<MockEvent>, ledger: String, unit: Unit): List<Pair<String, Stat>> {   // ios: Mock/MockEngine.swift:810
        val order = mutableListOf<String>(); val by = mutableMapOf<String, Stat>()   // ios: Mock/MockEngine.swift:811
        for (e in events.sortedByDescending { it.at }) { if (!(e.ledger == ledger && e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && !e.test)) continue   // ios: Mock/MockEngine.swift:812
            val k = bucket(e.at, unit)   // ios: Mock/MockEngine.swift:813
            if (by[k] == null) order.add(k)   // ios: Mock/MockEngine.swift:814
            by.getOrPut(k) { Stat() }.add(e)   // ios: Mock/MockEngine.swift:815
        }
        return order.map { it to by[it]!! }   // ios: Mock/MockEngine.swift:817
    }

    /** 이유 따라가기: 체결 하나에서 주문 → 선정 → 관찰로 거슬러 간다 (오래된 단계부터).
     *  체결·미체결 줄의 ref = 주문 id = 주문 줄의 id, 주문 줄의 ref = 선정 줄, 선정 줄의 ref = 관찰 줄 */
    fun chain(e: MockEvent, events: List<MockEvent>): List<MockEvent> {   // ios: Mock/MockEngine.swift:822
        val byId = mutableMapOf<String, MockEvent>()   // ios: Mock/MockEngine.swift:823
        for (x in events) byId[x.id] = x   // ios: Mock/MockEngine.swift:824
        val out = mutableListOf(e)   // ios: Mock/MockEngine.swift:825
        var cur = e.ref?.let { byId[it] }   // ios: Mock/MockEngine.swift:826
        var n = 0   // ios: Mock/MockEngine.swift:827
        while (cur != null && n < 6) {   // ios: Mock/MockEngine.swift:828
            out.add(cur)   // ios: Mock/MockEngine.swift:829
            cur = cur.ref?.let { byId[it] }   // ios: Mock/MockEngine.swift:830
            n += 1   // ios: Mock/MockEngine.swift:831
        }
        // 같은 주문의 미체결 줄도 함께 (왜 기다렸나). 체결이 여러 번이면 앞 체결도
        val same = events.filter { (it.kind == MockEvent.Kind.wait || it.kind == MockEvent.Kind.fill) && it.ref == e.ref && it.id != e.id && e.ref != null }   // ios: Mock/MockEngine.swift:834
        val rank: Map<MockEvent.Kind, Int> = mapOf(MockEvent.Kind.scan to 0, MockEvent.Kind.exclude to 1, MockEvent.Kind.select to 2, MockEvent.Kind.short to 2, MockEvent.Kind.order to 3, MockEvent.Kind.wait to 4, MockEvent.Kind.cancel to 5, MockEvent.Kind.fill to 6)   // ios: Mock/MockEngine.swift:835
        return (out + same).sortedWith { x, y -> if (x.at != y.at) x.at.compareTo(y.at) else (rank[x.kind] ?: 9).compareTo(rank[y.kind] ?: 9) }   // ios: Mock/MockEngine.swift:836
    }
}
