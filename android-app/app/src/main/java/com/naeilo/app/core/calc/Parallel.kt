package com.naeilo.app.core.calc

import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.WChar
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Lots
import com.naeilo.app.data.Sample
import com.naeilo.app.data.friendName
import com.naeilo.app.data.persists
import com.naeilo.app.pending.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.truncate

// 평행 세계의 나 (기획서 notes/평행세계-앱-기획서-2026-10-12.md, v1).
// 세 명의 나가 같은 날 같은 금액에서 출발해 사이트 지난 종가로 달린다: ① 진짜 나 ② 담아 본 나(모의 바구니) ③ 인물 세계의 나.
// 화면에는 순위·수익률·평가액과 비율 요약만 낸다. 인물 세계·모의 바구니의 매매 내역(종목+수량+시점)은 만들지도 보여 주지도 않는다.

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽). x - truncate(x) 는 정확한 값이라 경계에서도 Swift 와 같다 */
private fun Double.swiftRounded(): Double {
    val t = truncate(this)
    return if (abs(this - t) >= 0.5) t + (if (this < 0) -1.0 else 1.0) else t
}
/** Swift [Double].max(): 앞에서부터 보며 더 큰 값(<)일 때만 바꾼다 */
private fun swiftMax(xs: List<Double>): Double? {
    if (xs.isEmpty()) return null
    var m = xs[0]
    for (x in xs) if (m < x) m = x
    return m
}
/** sorted { a > b } 와 같은 비교 (같거나 비교 못 하면 0 → 안정 정렬로 원래 순서) */
private fun descCmp(x: Double, y: Double): Int = if (x > y) -1 else if (y > x) 1 else 0
/** Swift String(Double) (description): 1e-05, 1e+16, 10000000.0 처럼 */
private fun swiftDesc(x: Double): String {
    if (x.isNaN()) return "nan"
    if (x.isInfinite()) return if (x > 0) "inf" else "-inf"
    if (x == 0.0) return if (1.0 / x < 0) "-0.0" else "0.0"
    val a = abs(x)
    val bd = java.math.BigDecimal(x.toString()).stripTrailingZeros()
    if (a < 1e-4 || a >= 1e16) {
        val digits = bd.unscaledValue().abs().toString()
        val exp = digits.length - 1 - bd.scale()
        val mant = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
        val e = if (exp < 0) "-" + (if (-exp < 10) "0" else "") + (-exp) else "+" + (if (exp < 10) "0" else "") + exp
        return (if (x < 0) "-" else "") + mant + "e" + e
    }
    val plain = bd.toPlainString()
    return if (plain.contains('.')) plain else "$plain.0"
}

/// 모의 바구니 한 칸: 그날부터 이 비중 (티커 → 비중, 합 1). 고치면 그날부터 새 칸이 붙고 지난 칸은 그대로 남는다
// ios: Data/Parallel.swift:8
@Serializable
data class PBasketSeg(var from: String, var w: Map<String, Double>)
/// 본선 기록 한 줄: 그날 종가 기준 세 명의 수익률 (진짜·담아 본·인물). who = 그날 인물 세계의 인물, v = 그날 인물 규칙 판 (없으면 1)
// ios: Data/Parallel.swift:10
@Serializable
data class PDay(var d: String, var r: List<Double>, var who: String, var v: Int? = null)

// ios: Data/Parallel.swift:12
@Serializable
data class PWorld(
    var opened: String,              // ios: Data/Parallel.swift:13 평행 세계를 연 날 (본선 시작)
    var start: String,               // ios: Data/Parallel.swift:14 예선 출발일 (연 날 정해 두고 바꾸지 않는다)
    var segs: List<PBasketSeg>,      // ios: Data/Parallel.swift:15
    var who: String,                 // ios: Data/Parallel.swift:16 인물 세계의 인물
    var whoPick: String? = null,     // ios: Data/Parallel.swift:17 사용자가 마지막으로 직접 바꾼 때 (ISO 시각, 동기화에서 나중 것을 쓴다). 횟수 제한 없음
    var days: List<PDay> = emptyList(),   // ios: Data/Parallel.swift:18
    var ver: Int? = null,            // ios: Data/Parallel.swift:19 기록 형식 판. 2 = 인물 세부 규칙 (2026-10-12). 저장 키는 그대로 두고 지난 기록을 잇는다
) {
    companion object {
        const val key = "parallel.v1"   // ios: Data/Parallel.swift:21
        // ios: Data/Parallel.swift:22
        fun load(): PWorld? = Defaults.prefs.getString(key, null)?.let { runCatching { NJson.decodeFromString(serializer(), it) }.getOrNull() }
        // ios: Data/Parallel.swift:23,24
        fun save(w: PWorld?) {
            val d = w?.let { runCatching { NJson.encodeToString(serializer(), it) }.getOrNull() }
            if (d != null) Defaults.prefs.edit().putString(key, d).apply() else Defaults.prefs.edit().remove(key).apply()
        }
        /** 동기화: 기록은 날짜별로 합치고 (같은 날은 먼저 남은 값), 바구니는 더 나중에 고친 쪽 */
        // ios: Data/Parallel.swift:27,28
        fun merge(mine: PWorld?, remote: PWorld): PWorld {
            if (mine == null) return remote; val a = mine.copy()
            val byDay = HashMap<String, PDay>()   // ios: Data/Parallel.swift:29
            for (x in remote.days) byDay[x.d] = x   // ios: Data/Parallel.swift:30
            for (x in a.days) byDay[x.d] = x   // ios: Data/Parallel.swift:31
            a.days = byDay.values.sortedBy { it.d }   // ios: Data/Parallel.swift:32
            val la = a.segs.lastOrNull()?.from ?: ""; val lb = remote.segs.lastOrNull()?.from ?: ""   // ios: Data/Parallel.swift:33
            if (lb > la || (lb == la && remote.segs.size > a.segs.size)) a.segs = remote.segs   // ios: Data/Parallel.swift:34
            if ((remote.whoPick ?: "") > (a.whoPick ?: "")) { a.who = remote.who; a.whoPick = remote.whoPick }   // ios: Data/Parallel.swift:35
            if (remote.opened < a.opened) { a.opened = remote.opened; a.start = remote.start }   // ios: Data/Parallel.swift:36
            return a   // ios: Data/Parallel.swift:37
        }
    }
}

/// 규칙 일지 한 줄: 그날(화면에는 주 단위로 뭉갬) 어떤 세부 규칙이 움직였나(또는 움직이지 않기로 했나)와 비율 변화.
/// 종목 이름·수량은 담지 않는다. rule = Invest.habits 의 번호
// ios: Data/Parallel.swift:43
data class PLog(
    val t: Int,   // ios: Data/Parallel.swift:44
    val rule: Int,   // ios: Data/Parallel.swift:45
    val text: String,   // ios: Data/Parallel.swift:46
    val metric: String? = null,      // ios: Data/Parallel.swift:47 "현금 비중" · "가장 큰 종목 비중"
    val a: Double = 0.0,   // ios: Data/Parallel.swift:48
    val b: Double = 0.0,   // ios: Data/Parallel.swift:49
)

/// 경주 결과 (그래프·순위·카드가 같이 쓴다)
// ios: Data/Parallel.swift:53
class PRace(
    val dates: List<String>,   // ios: Data/Parallel.swift:54
    val r: List<DoubleArray>,          // ios: Data/Parallel.swift:55 [나][칸] 출발 대비 수익률 (출발 = 0)
    val open: Int,              // ios: Data/Parallel.swift:56 본선이 시작하는 칸 (연 날 이후 첫 거래일). 없으면 dates.count
    val V0: Double,             // ios: Data/Parallel.swift:57 출발 금액 (원, 진짜 나의 출발일 평가액)
    val cash: NowBefore,   // ios: Data/Parallel.swift:58 인물 세계 현금 비중 (지금, 약 한 달 전)
    val top: NowBefore,    // ios: Data/Parallel.swift:59 인물 세계 가장 큰 한 종목 비중
    val segRet: List<Double>,       // ios: Data/Parallel.swift:60 바구니 칸마다 그 칸 동안 수익률
    val contrib: Double,        // ios: Data/Parallel.swift:61 수아 세계: 넣은 적립 합 (원)
    val log: List<PLog>,            // ios: Data/Parallel.swift:62 인물 세계 규칙 일지 (오래된 것부터)
) {
    /** (now, before) 튜플 */
    data class NowBefore(val now: Double, val before: Double)   // ios: Data/Parallel.swift:58,59
    val last: Int get() = dates.size - 1   // ios: Data/Parallel.swift:63
    fun rank(at: Int): List<Int> = listOf(0, 1, 2).sortedWith { a, b -> descCmp(r[a][at], r[b][at]) }   // ios: Data/Parallel.swift:64
    fun place(k: Int, at: Int): Int = (rank(at = at).indexOf(k).let { if (it < 0) 0 else it }) + 1   // ios: Data/Parallel.swift:65
}

/** 인물 세계 (현금 비중, 가장 큰 한 종목 비중) */
private class Ratio(val cash: Double, val top: Double)

// ios: Data/Parallel.swift:68
object Parallel {
    val names = listOf("내 실제 투자", "모의 바구니", "인물 규칙대로")   // ios: Data/Parallel.swift:69

    /// v1에 담을 수 있는 종목: 사이트가 지난 종가를 모으는 종목 + 견본 ETF (VTV·USMV)
    data class Item(val id: String, val name: String)   // ios: Data/Parallel.swift:72
    // ios: Data/Parallel.swift:73
    val universe: List<Item> = listOf(
        Item(id = "SPY", name = "S&P500 ETF"), Item(id = "QQQ", name = "나스닥100 ETF"), Item(id = "VTV", name = "가치주 ETF"), Item(id = "USMV", name = "저변동 ETF"), Item(id = "SCHD", name = "배당 ETF"),   // ios: Data/Parallel.swift:74
        Item(id = "SGOV", name = "단기 국채 ETF"), Item(id = "GLD", name = "금 ETF"), Item(id = "EWY", name = "한국 ETF"), Item(id = "SMH", name = "반도체 ETF"), Item(id = "SOXX", name = "반도체 ETF (SOXX)"),   // ios: Data/Parallel.swift:75
        Item(id = "TSLA", name = "테슬라"), Item(id = "NVDA", name = "엔비디아"), Item(id = "AAPL", name = "애플"), Item(id = "SPCX", name = "스페이스X"), Item(id = "MANA", name = "MANA"),   // ios: Data/Parallel.swift:76
    )
    /** 바구니 한 칸을 글로 (계산 기억 키·지난 바구니 표시) */
    // ios: Data/Parallel.swift:79,80,81
    fun segText(g: PBasketSeg): String {
        val ks: List<String> = g.w.keys.sorted()
        return g.from + ":" + ks.joinToString(",") { k -> k + "=" + swiftDesc(g.w[k] ?: 0.0) }
    }
    fun name(id: String): String = universe.firstOrNull { it.id == id }?.name ?: id   // ios: Data/Parallel.swift:83
    /** 성장·개별 종목 (바구니가 공격적인지 볼 때) */
    val growth: Set<String> = setOf("QQQ", "SMH", "SOXX", "TSLA", "NVDA", "AAPL", "SPCX", "MANA")   // ios: Data/Parallel.swift:85

    /** 견본: 회복 루트 계획 고르기와 목표 루트 구성 비교의 묶음을 대표 ETF로 (종목 추천이 아니라 지수 묶음) */
    data class Template(val id: String, val name: String, val w: Map<String, Double>)   // ios: Data/Parallel.swift:88
    // ios: Data/Parallel.swift:89
    val templates: List<Template> = listOf(
        Template(id = "push", name = "도전", w = linkedMapOf("QQQ" to 0.7, "VTV" to 0.3)),   // ios: Data/Parallel.swift:90
        Template(id = "balance", name = "균형", w = linkedMapOf("QQQ" to 1.0 / 3, "VTV" to 1.0 / 3, "USMV" to 1.0 / 3)),   // ios: Data/Parallel.swift:91
        Template(id = "guard", name = "방어", w = linkedMapOf("VTV" to 0.2, "USMV" to 0.6, "SGOV" to 0.2)),   // ios: Data/Parallel.swift:92
        Template(id = "index", name = "지수 중심", w = linkedMapOf("SPY" to 0.8, "SGOV" to 0.2)),   // ios: Data/Parallel.swift:93
        Template(id = "grow", name = "성장 더하기", w = linkedMapOf("SPY" to 0.6, "QQQ" to 0.3, "SGOV" to 0.1)),   // ios: Data/Parallel.swift:94
        Template(id = "steady", name = "버팀목 더하기", w = linkedMapOf("SPY" to 0.5, "USMV" to 0.3, "SGOV" to 0.2)),   // ios: Data/Parallel.swift:95
    )
    /** 견본 ETF 시세가 아직 없으면 미국 전체 지수로 (AppModel.proxyKeys 와 같은 생각) */
    // ios: Data/Parallel.swift:98
    fun resolve(w: Map<String, Double>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:99
        for ((k, v) in w) {   // ios: Data/Parallel.swift:100
            val id = if (has(k)) k else (if (k == "SGOV") k else "SPY")   // ios: Data/Parallel.swift:101
            out[id] = (out[id] ?: 0.0) + v   // ios: Data/Parallel.swift:102
        }
        return out   // ios: Data/Parallel.swift:104
    }

    /** 지난 종가 (사이트 파일 → 기기가 받은 값 → 빌드 때 넣은 파일). 가상 종목 DRNK 는 빌드 때 넣은 샘플 기록 */
    // ios: Data/Parallel.swift:108
    fun hist(id: String): YahooSample.Hist? {
        if (id == "DRNK") return YahooSample.bundled["DRNK"]   // ios: Data/Parallel.swift:109
        return PriceHistory.of(id)   // ios: Data/Parallel.swift:110
    }
    fun has(id: String): Boolean = (hist(id)?.closes?.size ?: 0) > 1   // ios: Data/Parallel.swift:112

    /** 시세를 받아 둘 사이트 티커: 바구니 종목 + 인물 세계 현금(SGOV) + 달력(SPY) */
    // ios: Data/Parallel.swift:115
    val wantKeys: Set<String> get() {
        val s = LinkedHashSet<String>()   // ios: Data/Parallel.swift:116
        val w = PWorld.load() ?: return s   // ios: Data/Parallel.swift:117
        s.add("SGOV"); s.add("SPY")   // ios: Data/Parallel.swift:118
        for (g in w.segs) { for (k in g.w.keys) s.add(k) }   // ios: Data/Parallel.swift:119
        return s   // ios: Data/Parallel.swift:120
    }

    // 인물 규칙 (하루 한 번 종가 기준). 세부 규칙은 Invest.habits, 엔진은 run 의 ③
    /** 인물 규칙 판: 1 = 대표 규칙만, 2 = 세부 규칙까지 (인물 자세히 보기와 같은 규칙). 바뀐 날부터 새 규칙으로 이어 간다 */
    const val rulesVersion = 2   // ios: Data/Parallel.swift:125
    // ios: Data/Parallel.swift:126
    val rules: Map<String, String> = linkedMapOf(
        "seri" to "1년에 한 번 처음 비율로",   // ios: Data/Parallel.swift:127
        "sio" to "현금 20% · 고점 −20%면 절반 투입",   // ios: Data/Parallel.swift:128
        "seonbae" to "한 종목 +10%p 넘으면 덜어서 나눔",   // ios: Data/Parallel.swift:129
        "ir" to "200일선 위만 보유 · 아래면 현금",   // ios: Data/Parallel.swift:130
        "sua" to "매달 같은 금액 · 떨어진 달 1.5배",   // ios: Data/Parallel.swift:131
    )

    /** 매칭: 평정 사다리에서 내 다음 칸 인물 (이르 → 수아 → 선배·시오 → 세리). 맨 위(세리)면 결이 다른 시오.
     *  바구니가 공격적이면(성장·개별 종목 50% 이상) 반대 결(세리·선배)을 먼저. 언제나 지금 내 유형과 다른 인물 */
    // ios: Data/Parallel.swift:136
    fun match(type: String?, aggressive: Boolean): String {
        var who: String   // ios: Data/Parallel.swift:137
        when (type) {   // ios: Data/Parallel.swift:138
            "ir" -> who = "sua"   // ios: Data/Parallel.swift:139
            "sua" -> who = if (aggressive) "seonbae" else "sio"   // ios: Data/Parallel.swift:140
            "seonbae", "sio" -> who = "seri"   // ios: Data/Parallel.swift:141
            "seri" -> who = if (aggressive) "seonbae" else "sio"   // ios: Data/Parallel.swift:142
            else -> who = "seri"   // ios: Data/Parallel.swift:143
        }
        if (aggressive && !listOf("seri", "seonbae").contains(who)) who = if (type == "seri") "seonbae" else "seri"   // ios: Data/Parallel.swift:145
        if (who == type) who = if (who == "seri") "seonbae" else "seri"   // ios: Data/Parallel.swift:146
        return who   // ios: Data/Parallel.swift:147
    }
    fun aggressive(w: Map<String, Double>): Boolean = w.filter { growth.contains(it.key) }.values.fold(0.0) { a, x -> a + x } >= 0.5   // ios: Data/Parallel.swift:149

    // 계산
    class Asset(val id: String, val usd: Boolean, val h: YahooSample.Hist?, val last: Double)   // ios: Data/Parallel.swift:152
    /** 진짜 나의 종목 한 줄 (Swift 튜플 (id:, usd:, qty:, last:)) */
    data class Base(val id: String, val usd: Boolean, val qty: Double, val last: Double)   // ios: Data/Parallel.swift:156
    /** 본선 수량 기록 한 줄 (Swift 튜플 (d:, q:)) */
    data class LotQ(val d: String, val q: Map<String, Double>)   // ios: Data/Parallel.swift:157
    // ios: Data/Parallel.swift:153
    class Input(
        val start: String,   // ios: Data/Parallel.swift:154
        val opened: String,   // ios: Data/Parallel.swift:155
        val base: List<Base>,   // ios: Data/Parallel.swift:156 진짜 나: 예선 수량 (지금 종목)
        val lots: List<LotQ>,                      // ios: Data/Parallel.swift:157 진짜 나: 본선 수량 기록 (앱 종목 이름)
        val segs: List<PBasketSeg>,   // ios: Data/Parallel.swift:158
        val who: String,   // ios: Data/Parallel.swift:159
        val monthly: Double,                                               // ios: Data/Parallel.swift:160 수아 세계 적립 (원)
    )

    /** 달력: 미국 거래일 (SPY), 없으면 원/달러 날짜 */
    fun calendar(): List<String> = (hist("SPY") ?: hist("KRW=X"))?.dates ?: emptyList()   // ios: Data/Parallel.swift:164

    /** 날짜별 원화 가격표 (앞 값으로 메움, 상장 전은 첫 종가) */
    // ios: Data/Parallel.swift:167
    private fun table(a: Asset, dates: List<String>, fx: DoubleArray): DoubleArray {
        if (a.id == "~CASH") return DoubleArray(dates.size) { 1.0 }   // ios: Data/Parallel.swift:168
        return DoubleArray(dates.size) { t ->   // ios: Data/Parallel.swift:169
            val p: Double = a.h?.on(dates[t]) ?: a.h?.closes?.firstOrNull() ?: a.last   // ios: Data/Parallel.swift:170
            if (a.usd) p * fx[t] else p   // ios: Data/Parallel.swift:171
        }
    }
    // ios: Data/Parallel.swift:174
    private fun fxTable(dates: List<String>): DoubleArray {
        val h = hist("KRW=X"); val now = Market.shared.fx.last   // ios: Data/Parallel.swift:175
        return DoubleArray(dates.size) { h?.on(dates[it]) ?: h?.closes?.firstOrNull() ?: now }   // ios: Data/Parallel.swift:176
    }

    /** 바닥일: 지금 종목·수량 × 지난 종가로 본 평가액이 가장 낮았던 날 */
    // ios: Data/Parallel.swift:180
    fun bottomDay(base: List<Base>): String? {
        val dates = calendar()   // ios: Data/Parallel.swift:181
        if (dates.size <= 1) return null   // ios: Data/Parallel.swift:182
        val fx = fxTable(dates)   // ios: Data/Parallel.swift:183
        val v = DoubleArray(dates.size) { 0.0 }   // ios: Data/Parallel.swift:184
        for (b in base) {   // ios: Data/Parallel.swift:185
            val p = table(Asset(id = b.id, usd = b.usd, h = hist(b.id), last = b.last), dates, fx)   // ios: Data/Parallel.swift:186
            for (t in dates.indices) v[t] += b.qty * p[t]   // ios: Data/Parallel.swift:187
        }
        if (v.isEmpty()) return null; var k = 0; for (i in v.indices) if (v[i] < v[k]) k = i   // ios: Data/Parallel.swift:189
        return dates[k]   // ios: Data/Parallel.swift:190
    }
    /** 예선 출발일을 달력 안으로: 3년보다 앞서면 첫날, 20거래일은 남게 */
    // ios: Data/Parallel.swift:193
    fun clampStart(s: String): String {
        val dates = calendar()   // ios: Data/Parallel.swift:194
        val first = dates.firstOrNull() ?: return s   // ios: Data/Parallel.swift:195
        val latest = dates[max(0, dates.size - 21)]   // ios: Data/Parallel.swift:196
        return minOf(maxOf(s, first), latest)   // ios: Data/Parallel.swift:197
    }

    // ios: Data/Parallel.swift:200
    fun run(inp: Input): PRace? {
        val all = calendar()   // ios: Data/Parallel.swift:201
        val s0 = all.indexOfFirst { it >= inp.start }; if (s0 < 0) return null   // ios: Data/Parallel.swift:202
        val dates = all.subList(s0, all.size).toList()   // ios: Data/Parallel.swift:203
        val n = dates.size   // ios: Data/Parallel.swift:204
        if (n < 2) return null   // ios: Data/Parallel.swift:205
        val fx = fxTable(dates)   // ios: Data/Parallel.swift:206

        // 쓰는 종목 모두의 가격표
        val assets = LinkedHashMap<String, Asset>()   // ios: Data/Parallel.swift:209
        for (b in inp.base) assets[b.id] = Asset(id = b.id, usd = b.usd, h = hist(b.id), last = b.last)   // ios: Data/Parallel.swift:210
        for (l in inp.lots) { for (k in l.q.keys) { if (assets[k] != null) continue   // ios: Data/Parallel.swift:211
            val s = Sample.symbol(k)   // ios: Data/Parallel.swift:212
            assets[k] = Asset(id = k, usd = s?.currency != Currency.krw, h = hist(k), last = s?.last ?: 0.0)   // ios: Data/Parallel.swift:213
        } }
        for (g in inp.segs) { for (k in g.w.keys) { if (assets[k] == null) assets[k] = Asset(id = k, usd = true, h = hist(k), last = hist(k)?.closes?.lastOrNull() ?: 0.0) } }   // ios: Data/Parallel.swift:215
        val cashId = if (has("SGOV")) "SGOV" else "~CASH"   // ios: Data/Parallel.swift:216
        if (assets[cashId] == null) assets[cashId] = Asset(id = cashId, usd = cashId == "SGOV", h = hist(cashId), last = 1.0)   // ios: Data/Parallel.swift:217
        val P = HashMap<String, DoubleArray>()   // ios: Data/Parallel.swift:218
        for ((k, a) in assets) P[k] = table(a, dates, fx)   // ios: Data/Parallel.swift:219
        fun p(k: String, t: Int): Double = P[k]?.get(t) ?: 0.0   // ios: Data/Parallel.swift:220

        // ① 진짜 나: 전날 들고 있던 수량으로 하루 수익률을 잇는다 (수량을 고친 날 넣고 뺀 돈은 수익률에 넣지 않는다)
        val baseQ = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:223
        for (b in inp.base) if (b.qty > 0) baseQ[b.id] = (baseQ[b.id] ?: 0.0) + b.qty   // ios: Data/Parallel.swift:224
        // ios: Data/Parallel.swift:225,226,227
        fun qty(d: String): Map<String, Double> {
            val l = if (d >= inp.opened) inp.lots.lastOrNull { it.d <= d } else null
            return l?.q ?: baseQ
        }
        val V0 = baseQ.entries.fold(0.0) { a, e -> a + e.value * p(e.key, 0) }   // ios: Data/Parallel.swift:229
        if (!(V0 > 0)) return null   // ios: Data/Parallel.swift:230
        val r0 = DoubleArray(n) { 0.0 }   // ios: Data/Parallel.swift:231
        var idx = 1.0   // ios: Data/Parallel.swift:232
        for (t in 1 until n) {   // ios: Data/Parallel.swift:233
            val q = qty(dates[t - 1])   // ios: Data/Parallel.swift:234
            var num = 0.0; var den = 0.0   // ios: Data/Parallel.swift:235
            for ((k, x) in q) { num += x * p(k, t); den += x * p(k, t - 1) }   // ios: Data/Parallel.swift:236
            if (den > 0 && num > 0) idx *= num / den   // ios: Data/Parallel.swift:237
            r0[t] = idx - 1   // ios: Data/Parallel.swift:238
        }

        // ② 담아 본 나: 바구니 칸마다 그날 금액을 그 비중으로 나눠 들고 그대로 둔다
        val segs = inp.segs.sortedBy { it.from }   // ios: Data/Parallel.swift:242
        if (segs.isEmpty()) return null   // ios: Data/Parallel.swift:243
        val r1 = DoubleArray(n) { 0.0 }; val segRet = mutableListOf<Double>()   // ios: Data/Parallel.swift:244
        var units: Map<String, Double> = emptyMap(); var cur = -1; var segV = V0   // ios: Data/Parallel.swift:245
        fun value(u: Map<String, Double>, t: Int): Double = u.entries.fold(0.0) { a, e -> a + e.value * p(e.key, t) }   // ios: Data/Parallel.swift:246
        // ios: Data/Parallel.swift:247
        fun alloc(w: Map<String, Double>, V: Double, t: Int): MutableMap<String, Double> {
            val ok = w.filter { it.value > 0 && p(it.key, t) > 0 }   // ios: Data/Parallel.swift:248
            val tot = ok.values.fold(0.0) { a, x -> a + x }   // ios: Data/Parallel.swift:249
            if (!(tot > 0)) return linkedMapOf(cashId to V / max(1e-9, p(cashId, t)))   // ios: Data/Parallel.swift:250
            val u = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:251
            for ((k, x) in ok) u[k] = V * x / tot / p(k, t)   // ios: Data/Parallel.swift:252
            return u   // ios: Data/Parallel.swift:253
        }
        for (t in 0 until n) {   // ios: Data/Parallel.swift:255
            var k = cur   // ios: Data/Parallel.swift:256
            while (k + 1 < segs.size && (k + 1 == 0 || segs[k + 1].from <= dates[t])) k += 1   // ios: Data/Parallel.swift:257
            val V = if (t == 0) V0 else value(units, t)   // ios: Data/Parallel.swift:258
            if (k != cur) {   // ios: Data/Parallel.swift:259
                if (cur >= 0) segRet.add(if (segV > 0) V / segV - 1 else 0.0)   // ios: Data/Parallel.swift:260
                units = alloc(segs[max(0, k)].w, V, t); cur = k; segV = V   // ios: Data/Parallel.swift:261
            }
            r1[t] = V / V0 - 1   // ios: Data/Parallel.swift:263
        }
        if (cur >= 0) segRet.add(if (segV > 0) (V0 * (1 + r1[n - 1])) / segV - 1 else 0.0)   // ios: Data/Parallel.swift:265

        // ③ 인물 세계의 나: 진짜 나의 출발 비중에서 시작해 인물 규칙(대표 + 세부, Invest.habits)대로 스스로 움직인다
        val w0 = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:268
        for ((k, x) in baseQ) if (p(k, 0) > 0) w0[k] = x * p(k, 0) / V0   // ios: Data/Parallel.swift:269
        var u: MutableMap<String, Double> = alloc(w0, if (inp.who == "sio") V0 * 0.8 else V0, 0)   // ios: Data/Parallel.swift:270
        var cashU = if (inp.who == "sio") V0 * 0.2 / max(1e-9, p(cashId, 0)) else 0.0   // ios: Data/Parallel.swift:271
        val parked = LinkedHashMap<String, Double>()          // ios: Data/Parallel.swift:272 이르: 평균선 아래로 내려가 현금으로 옮긴 몫 (현금 단위)
        val below = HashMap<String, Int>()              // ios: Data/Parallel.swift:273 이르: 평균선 아래로 이어진 거래일 수
        var invested = V0; var contrib = 0.0   // ios: Data/Parallel.swift:274
        var sIdx = 1.0; var sPeak = 1.0; var stage = 0; var refill = false   // ios: Data/Parallel.swift:275 시오: 주식 쪽 지수·고점·넣은 단계
        var nextYear = 1; var monthStartV = V0   // ios: Data/Parallel.swift:276
        var lastQ = quarter(dates[0])   // ios: Data/Parallel.swift:277
        val trades = HashMap<String, Int>()          // ios: Data/Parallel.swift:278 달마다 매매 횟수 (시오 2번·이르 4번까지)
        val r2 = DoubleArray(n) { 0.0 }   // ios: Data/Parallel.swift:279
        val cashW = HashMap<Int, Double>(); val topW = HashMap<Int, Double>()   // ios: Data/Parallel.swift:280
        val sma = SMA(assets = assets)   // ios: Data/Parallel.swift:281
        val spy = hist("SPY")   // ios: Data/Parallel.swift:282
        val before = max(0, n - 22)   // ios: Data/Parallel.swift:283
        fun cashV(t: Int): Double = (cashU + parked.values.fold(0.0) { a, x -> a + x }) * p(cashId, t)   // ios: Data/Parallel.swift:284
        fun charV(t: Int): Double = value(u, t) + cashV(t)   // ios: Data/Parallel.swift:285
        val log = mutableListOf<PLog>()   // ios: Data/Parallel.swift:286
        /** (현금 비중, 가장 큰 한 종목 비중) */
        // ios: Data/Parallel.swift:288
        fun ratios(t: Int): Ratio {
            val V = charV(t)   // ios: Data/Parallel.swift:289
            if (!(V > 0)) return Ratio(0.0, 0.0)   // ios: Data/Parallel.swift:290
            val c = cashV(t) + (u[cashId] ?: 0.0) * p(cashId, t)   // ios: Data/Parallel.swift:291
            return Ratio(c / V, (swiftMax(u.filter { it.key != cashId }.map { it.value * p(it.key, t) }) ?: 0.0) / V)   // ios: Data/Parallel.swift:292
        }
        // ios: Data/Parallel.swift:294
        fun note(t: Int, rule: Int, text: String, cash: Pair<Double, Double>? = null, top: Pair<Double, Double>? = null) {
            if (cash != null) log.add(PLog(t = t, rule = rule, text = text, metric = "현금 비중", a = cash.first, b = cash.second))   // ios: Data/Parallel.swift:295
            else if (top != null) log.add(PLog(t = t, rule = rule, text = text, metric = "가장 큰 종목 비중", a = top.first, b = top.second))   // ios: Data/Parallel.swift:296
            else log.add(PLog(t = t, rule = rule, text = text))   // ios: Data/Parallel.swift:297
        }
        var capNoted = ""                // ios: Data/Parallel.swift:299 같은 달 "한도 다 씀"은 한 번만 적는다
        /** 지금 들고 있는 비중 (현금 칸 빼고) */
        // ios: Data/Parallel.swift:301
        fun weights(t: Int): Map<String, Double> {
            val V = value(u, t)   // ios: Data/Parallel.swift:302
            if (!(V > 0)) return w0   // ios: Data/Parallel.swift:303
            val w = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:304
            for ((k, x) in u) w[k] = x * p(k, t) / V   // ios: Data/Parallel.swift:305
            return w   // ios: Data/Parallel.swift:306
        }
        /** 그 종목을 돈만큼 사고팔기 (+ 사기, − 팔기) */
        // ios: Data/Parallel.swift:309
        fun trade(k: String, won: Double, t: Int) {
            if (!(p(k, t) > 0)) return   // ios: Data/Parallel.swift:310
            u[k] = (u[k] ?: 0.0) + won / p(k, t)   // ios: Data/Parallel.swift:311
            if ((u[k] ?: 0.0) <= 1e-12) u.remove(k)   // ios: Data/Parallel.swift:312
        }
        fun toCash(won: Double, t: Int) { cashU += won / max(1e-9, p(cashId, t)) }   // ios: Data/Parallel.swift:314
        /** 시오: 지수가 하루 ±3% 넘게 움직인 날 (S&P500 ETF 기준) */
        // ios: Data/Parallel.swift:316
        fun bigDay(t: Int): Boolean {
            if (t <= 0) return false; val s = spy ?: return false; val a = s.on(dates[t]) ?: return false; val b = s.on(dates[t - 1]) ?: return false; if (!(b > 0)) return false   // ios: Data/Parallel.swift:317
            return abs(a / b - 1) > 0.03   // ios: Data/Parallel.swift:318
        }
        for (t in 0 until n) {   // ios: Data/Parallel.swift:320
            val d = dates[t]; val mk = d.take(7)   // ios: Data/Parallel.swift:321
            if (t > 0) { val a = value(u, t); val b = value(u, t - 1); if (b > 0 && a > 0) sIdx *= a / b }   // ios: Data/Parallel.swift:322
            sw@ do {
            when (inp.who) {   // ios: Data/Parallel.swift:323
                "seri" -> {   // ios: Data/Parallel.swift:324
                    // 기념일에만. 처음 비율(한 종목 40% 상한)에서 ±5%p 넘게 벗어난 종목이 있을 때만 되돌린다
                    val anniv = if (t > 0) addYears(dates[0], nextYear) else null; if (anniv != null && d >= anniv) {   // ios: Data/Parallel.swift:326
                        nextYear += 1   // ios: Data/Parallel.swift:327
                        val (target, cashT) = capped(w0, 0.40)   // ios: Data/Parallel.swift:328
                        val V = charV(t)   // ios: Data/Parallel.swift:329
                        var off = abs(cashV(t) / V - cashT)   // ios: Data/Parallel.swift:330
                        for (k in target.keys.toSet().union(u.keys)) off = max(off, abs((u[k] ?: 0.0) * p(k, t) / V - (target[k] ?: 0.0)))   // ios: Data/Parallel.swift:331
                        if (V > 0 && off > 0.05) {   // ios: Data/Parallel.swift:332
                            val q0 = ratios(t)   // ios: Data/Parallel.swift:333
                            u = alloc(target, V * (1 - cashT), t)   // ios: Data/Parallel.swift:334
                            cashU = V * cashT / max(1e-9, p(cashId, t))   // ios: Data/Parallel.swift:335
                            val q1 = ratios(t)   // ios: Data/Parallel.swift:336
                            val wasCapped = w0.values.any { it > 0.40 + 1e-9 }   // ios: Data/Parallel.swift:337
                            note(t, if (wasCapped) 2 else 0, if (wasCapped) "기념일, 한 종목 40% 상한에 맞춰 되돌리기" else "기념일, 처음 비율로 되돌리기", top = Pair(q0.top, q1.top))   // ios: Data/Parallel.swift:338
                        } else if (V > 0) {   // ios: Data/Parallel.swift:339
                            note(t, 1, "기념일 점검, ±5%p 안이라 그해는 그대로")   // ios: Data/Parallel.swift:340
                        }
                    }
                }
                "seonbae" -> {   // ios: Data/Parallel.swift:343
                    // 분기에 한 번 되묻는다: 처음보다 10%p 넘게 커진 종목은 덜고, 작아진 종목은 다른 데서 덜어 채운다
                    val q = quarter(d)   // ios: Data/Parallel.swift:345
                    if (!(t > 0 && q != lastQ)) break@sw   // ios: Data/Parallel.swift:346
                    lastQ = q   // ios: Data/Parallel.swift:347
                    val V = charV(t)   // ios: Data/Parallel.swift:348
                    if (!(V > 0)) break@sw   // ios: Data/Parallel.swift:349
                    fun wt(k: String): Double = (u[k] ?: 0.0) * p(k, t) / V   // ios: Data/Parallel.swift:350
                    // 수익 난 직후(최근 20거래일 오른 종목)에는 더 사지 않는다
                    fun gained(k: String): Boolean = p(k, t) > p(k, max(0, t - 20))   // ios: Data/Parallel.swift:352
                    val q0 = ratios(t)   // ios: Data/Parallel.swift:353
                    var pool = 0.0; var filled = false; var held = false   // ios: Data/Parallel.swift:354
                    for (k in w0.keys) { if (!(wt(k) - (w0[k] ?: 0.0) > 0.10)) continue   // ios: Data/Parallel.swift:355
                        val cut = (wt(k) - (w0[k] ?: 0.0)) * V   // ios: Data/Parallel.swift:356
                        trade(k, -cut, t); pool += cut   // ios: Data/Parallel.swift:357
                    }
                    if (pool > 0) {   // ios: Data/Parallel.swift:359
                        // 덜어낸 돈: 절반은 가장 작아진 종목에, 절반은 현금에
                        var small: String? = null; for (k in w0.keys) { if (!(!gained(k) && wt(k) < (w0[k] ?: 0.0))) continue; val s = small; if (s == null || wt(k) - (w0[k] ?: 0.0) < wt(s) - (w0[s] ?: 0.0)) small = k }   // ios: Data/Parallel.swift:361
                        val s = small; if (s != null) { trade(s, pool / 2, t); toCash(pool / 2, t) } else toCash(pool, t)   // ios: Data/Parallel.swift:362
                    }
                    for (k in w0.keys.sorted()) { if (!((w0[k] ?: 0.0) - wt(k) > 0.10)) continue   // ios: Data/Parallel.swift:364
                        if (gained(k)) { held = true; continue }   // ios: Data/Parallel.swift:365
                        filled = true   // ios: Data/Parallel.swift:366
                        // "정말 버릴 거야?": 현금에서 먼저, 모자라면 다른 종목에서 고르게 덜어 채운다
                        var need = ((w0[k] ?: 0.0) - wt(k)) * V   // ios: Data/Parallel.swift:368
                        val fromCash = min(need, cashU * p(cashId, t))   // ios: Data/Parallel.swift:369
                        cashU -= fromCash / max(1e-9, p(cashId, t)); need -= fromCash   // ios: Data/Parallel.swift:370
                        var got = fromCash   // ios: Data/Parallel.swift:371
                        if (need > 0) {   // ios: Data/Parallel.swift:372
                            val others = u.filter { it.key != k }   // ios: Data/Parallel.swift:373
                            val ov = others.entries.fold(0.0) { a, e -> a + e.value * p(e.key, t) }   // ios: Data/Parallel.swift:374
                            if (ov > 0) { val f = min(1.0, need / ov); for ((j, x) in others) trade(j, -x * p(j, t) * f, t); got += min(need, ov) }   // ios: Data/Parallel.swift:375
                        }
                        trade(k, got, t)   // ios: Data/Parallel.swift:377
                    }
                    val q1 = ratios(t)   // ios: Data/Parallel.swift:379
                    if (pool > 0) note(t, 1, "분기 점검, 10%p 넘게 커진 몫을 덜어 절반은 현금으로", top = Pair(q0.top, q1.top))   // ios: Data/Parallel.swift:380
                    if (filled) note(t, 2, "분기 점검, 10%p 넘게 작아진 몫을 다시 채우기", cash = Pair(q0.cash, q1.cash))   // ios: Data/Parallel.swift:381
                    if (held) note(t, 4, "작아졌지만 막 오른 참이라 이번엔 채우지 않음")   // ios: Data/Parallel.swift:382
                    if (pool == 0.0 && !filled && !held) note(t, 0, "분기 점검, 모두 ±10%p 안이라 그대로")   // ios: Data/Parallel.swift:383
                }
                "sio" -> {   // ios: Data/Parallel.swift:384
                    // 주식 쪽 고점 대비 −20%에 현금 절반, −30%에 나머지. 넣은 뒤 고점을 다시 넘으면 현금 20%로 다시 채운다.
                    // 지수가 하루 ±3% 넘게 움직인 날에는 매매하지 않고 다음 날 판단, 한 달에 2번까지
                    if (sIdx > sPeak) { sPeak = sIdx; if (stage > 0) refill = true; stage = 0 }   // ios: Data/Parallel.swift:387
                    val dd = sIdx / sPeak - 1   // ios: Data/Parallel.swift:388
                    val due = (stage == 0 && dd <= -0.20) || (stage == 1 && dd <= -0.30)   // ios: Data/Parallel.swift:389
                    if (refill || (due && cashU > 0)) {   // ios: Data/Parallel.swift:390
                        if (bigDay(t)) { note(t, 3, "하루에 3% 넘게 움직인 날이라 하루 기다리기"); break@sw }   // ios: Data/Parallel.swift:391
                        if ((trades[mk] ?: 0) >= 2) {   // ios: Data/Parallel.swift:392
                            if (capNoted != mk) { capNoted = mk; note(t, 2, "이번 달 매매 두 번을 다 써서 다음 달로") }   // ios: Data/Parallel.swift:393
                            break@sw   // ios: Data/Parallel.swift:394
                        }
                    }
                    val q0 = ratios(t)   // ios: Data/Parallel.swift:397
                    if (refill) {   // ios: Data/Parallel.swift:398
                        refill = false   // ios: Data/Parallel.swift:399
                        val V = charV(t); val c = cashV(t)   // ios: Data/Parallel.swift:400
                        if (V > 0 && c < V * 0.2) {   // ios: Data/Parallel.swift:401
                            val need = V * 0.2 - c; val sv = value(u, t)   // ios: Data/Parallel.swift:402
                            if (sv > 0) { for ((k, x) in u.toList()) trade(k, -need * x * p(k, t) / sv, t); toCash(need, t) }   // ios: Data/Parallel.swift:403
                            trades[mk] = (trades[mk] ?: 0) + 1   // ios: Data/Parallel.swift:404
                            note(t, 1, "고점을 다시 넘어 현금을 20%까지 다시 채우기", cash = Pair(q0.cash, ratios(t).cash))   // ios: Data/Parallel.swift:405
                        }
                    } else if (due && cashU > 0) {   // ios: Data/Parallel.swift:407
                        val first = stage == 0   // ios: Data/Parallel.swift:408
                        val put = (if (first) cashU / 2 else cashU) * p(cashId, t)   // ios: Data/Parallel.swift:409
                        cashU -= put / max(1e-9, p(cashId, t))   // ios: Data/Parallel.swift:410
                        for ((k, x) in alloc(w0, put, t)) u[k] = (u[k] ?: 0.0) + x   // ios: Data/Parallel.swift:411
                        stage += 1; trades[mk] = (trades[mk] ?: 0) + 1   // ios: Data/Parallel.swift:412
                        note(t, 0, if (first) "고점 −20%, 현금 절반 넣기" else "고점 −30%, 남은 현금 넣기", cash = Pair(q0.cash, ratios(t).cash))   // ios: Data/Parallel.swift:413
                    }
                }
                "ir" -> {   // ios: Data/Parallel.swift:415
                    // 200일선보다 3% 넘게 내려가거나 5거래일 연속 아래면 판다. 3% 넘게 올라오면 다시 산다. 오르는 종목은 덜지 않는다. 한 달 4번까지
                    val q0 = ratios(t)   // ios: Data/Parallel.swift:417
                    var outBy3 = false; var outBy5 = false; var back = false; var capHit = false   // ios: Data/Parallel.swift:418
                    for (k in u.keys.toSet().union(parked.keys).sorted()) { if (!(k != cashId && k != "SGOV")) continue   // ios: Data/Parallel.swift:419
                        val m = sma.at(k, d) ?: continue   // ios: Data/Parallel.swift:420
                        val px = p(k, t)   // ios: Data/Parallel.swift:421
                        below[k] = if (px < m) (below[k] ?: 0) + 1 else 0   // ios: Data/Parallel.swift:422
                        val wantIn = parked[k] != null && px > m * 1.03   // ios: Data/Parallel.swift:423
                        val wantOut = parked[k] == null && (u[k] ?: 0.0) > 0 && (px < m * 0.97 || (below[k] ?: 0) >= 5)   // ios: Data/Parallel.swift:424
                        if (!(wantIn || wantOut)) continue   // ios: Data/Parallel.swift:425
                        if (!((trades[mk] ?: 0) < 4)) { capHit = true; continue }   // ios: Data/Parallel.swift:426
                        val c = parked[k]; val x = u[k]; if (wantIn && c != null) {   // ios: Data/Parallel.swift:427
                            u[k] = (u[k] ?: 0.0) + c * p(cashId, t) / max(1e-9, px); parked.remove(k); trades[mk] = (trades[mk] ?: 0) + 1; back = true   // ios: Data/Parallel.swift:428
                        } else if (wantOut && x != null) {   // ios: Data/Parallel.swift:429
                            if (px < m * 0.97) outBy3 = true else outBy5 = true   // ios: Data/Parallel.swift:430
                            parked[k] = x * px / max(1e-9, p(cashId, t)); u.remove(k); trades[mk] = (trades[mk] ?: 0) + 1   // ios: Data/Parallel.swift:431
                        }
                    }
                    if (outBy3 || outBy5 || back) {   // ios: Data/Parallel.swift:434
                        val q1 = ratios(t)   // ios: Data/Parallel.swift:435
                        if (outBy3 || outBy5) {   // ios: Data/Parallel.swift:436
                            val allCash = q1.cash > 0.995   // ios: Data/Parallel.swift:437
                            note(t, if (allCash) 4 else 0, if (allCash) "모두 200일선 아래, 전부 현금으로" else if (outBy3) "200일선보다 3% 넘게 내려가 현금으로 옮기기" else "5거래일 내내 200일선 아래라 현금으로 옮기기", cash = Pair(q0.cash, q1.cash))   // ios: Data/Parallel.swift:438
                        }
                        if (back) note(t, 1, "200일선보다 3% 넘게 올라와 다시 들어가기", cash = Pair(q0.cash, q1.cash))   // ios: Data/Parallel.swift:440
                    }
                    if (capHit && capNoted != mk) { capNoted = mk; note(t, 3, "이번 달 매매 네 번을 다 써서 다음 달로") }   // ios: Data/Parallel.swift:442
                }
                "sua" -> {   // ios: Data/Parallel.swift:443
                    // 매달 같은 금액. 지난달 바구니가 −5% 넘게 빠졌으면 1.5배, −15% 넘게 2배, +10% 넘게 올랐으면 0.7배.
                    // 지금 바구니 비율대로 나눠 넣고(한 종목에 몰지 않음), 팔지 않고, 못 넣은 달을 몰아 넣지 않는다
                    if (t > 0 && inp.monthly > 0 && mk != dates[t - 1].take(7)) {   // ios: Data/Parallel.swift:446
                        val m = if (monthStartV > 0) charV(t - 1) / monthStartV - 1 else 0.0   // ios: Data/Parallel.swift:447
                        val times = suaTimes(m); val c = inp.monthly * times   // ios: Data/Parallel.swift:448
                        for ((k, x) in alloc(weights(t), c, t)) u[k] = (u[k] ?: 0.0) + x   // ios: Data/Parallel.swift:449
                        val pct = "${(m * 100).swiftRounded().toInt()}%"   // ios: Data/Parallel.swift:450
                        when {   // ios: Data/Parallel.swift:451
                            times == 2.0 -> note(t, 1, "지난달 바구니 ${pct}, 2배 넣기")   // ios: Data/Parallel.swift:452
                            times == 1.5 -> note(t, 0, "지난달 바구니 ${pct}, 1.5배 넣기")   // ios: Data/Parallel.swift:453
                            times == 0.7 -> note(t, 2, "지난달 바구니 +${pct}, 0.7배만 넣기")   // ios: Data/Parallel.swift:454
                            else -> note(t, 3, "보통 달, 같은 금액을 지금 비율대로 넣기")   // ios: Data/Parallel.swift:455
                        }
                        invested += c; contrib += c   // ios: Data/Parallel.swift:457
                        monthStartV = charV(t)   // ios: Data/Parallel.swift:458
                    }
                }
                else -> {}   // ios: Data/Parallel.swift:460
            }
            } while (false)
            val V = charV(t)   // ios: Data/Parallel.swift:462
            r2[t] = V / invested - 1   // ios: Data/Parallel.swift:463
            if (t == n - 1 || t == before) {   // ios: Data/Parallel.swift:464
                val cv = cashV(t) + (u[cashId] ?: 0.0) * p(cashId, t)   // ios: Data/Parallel.swift:465
                cashW[t] = if (V > 0) cv / V else 0.0   // ios: Data/Parallel.swift:466
                topW[t] = if (V > 0) (swiftMax(u.filter { it.key != cashId }.map { it.value * p(it.key, t) }) ?: 0.0) / V else 0.0   // ios: Data/Parallel.swift:467
            }
        }
        val open = dates.indexOfFirst { it >= inp.opened }.let { if (it < 0) n else it }   // ios: Data/Parallel.swift:470
        return PRace(dates = dates, r = listOf(r0, r1, r2), open = open, V0 = V0,   // ios: Data/Parallel.swift:471
                     cash = PRace.NowBefore(cashW[n - 1] ?: 0.0, cashW[before] ?: 0.0), top = PRace.NowBefore(topW[n - 1] ?: 0.0, topW[before] ?: 0.0),   // ios: Data/Parallel.swift:472
                     segRet = segRet, contrib = contrib, log = log)   // ios: Data/Parallel.swift:473
    }

    /** Calendar(identifier: .gregorian) 는 기기 시간대 → 같게 systemDefault */
    // ios: Data/Parallel.swift:476,477,478
    fun addYears(d: String, y: Int): String? {
        val x = Day.date(d) ?: return null
        val z = x.atZone(ZoneId.systemDefault()).plusYears(y.toLong()).toInstant()
        return Day.str(z)
    }

    /** 분기 (yyyy-Qn) */
    // ios: Data/Parallel.swift:482
    fun quarter(d: String): String {
        val m = d.drop(5).take(2).toIntOrNull() ?: 1   // ios: Data/Parallel.swift:483
        return d.take(4) + "-Q${(m - 1) / 3 + 1}"   // ios: Data/Parallel.swift:484
    }
    /** 수아: 지난달 바구니 변화에 따른 적립 배수 */
    fun suaTimes(m: Double): Double = if (m <= -0.15) 2.0 else if (m <= -0.05) 1.5 else if (m >= 0.10) 0.7 else 1.0   // ios: Data/Parallel.swift:487
    /** 세리: 한 종목 상한. 넘친 몫은 다른 종목에 비율대로, 모두 상한이면 현금으로 (현금 비중을 같이 돌려준다) */
    data class Capped(val w: Map<String, Double>, val cash: Double)   // ios: Data/Parallel.swift:489
    // ios: Data/Parallel.swift:489
    fun capped(src: Map<String, Double>, cap: Double): Capped {
        val w = LinkedHashMap(src); var cash = 0.0   // ios: Data/Parallel.swift:490
        for (i in 0 until 10) {   // ios: Data/Parallel.swift:491
            val over = w.filter { it.value > cap + 1e-9 }   // ios: Data/Parallel.swift:492
            if (over.isEmpty()) break   // ios: Data/Parallel.swift:493
            var ex = 0.0   // ios: Data/Parallel.swift:494
            for ((k, x) in over) { ex += x - cap; w[k] = cap }   // ios: Data/Parallel.swift:495
            val room = w.filter { it.value < cap - 1e-9 }   // ios: Data/Parallel.swift:496
            val tot = room.values.fold(0.0) { a, x -> a + x }   // ios: Data/Parallel.swift:497
            if (tot <= 0) { cash += ex; break }   // ios: Data/Parallel.swift:498
            for ((k, x) in room) w[k] = x + ex * x / tot   // ios: Data/Parallel.swift:499
        }
        return Capped(w, cash)   // ios: Data/Parallel.swift:501
    }

    /** 200일 평균선 (그날까지의 종가 200개, 100개 안 되면 판단하지 않는다) */
    // ios: Data/Parallel.swift:505
    class SMA(assets: Map<String, Asset>) {
        val sums = HashMap<String, Pair<List<String>, DoubleArray>>()   // ios: Data/Parallel.swift:506
        init {   // ios: Data/Parallel.swift:507
            for ((k, a) in assets) {   // ios: Data/Parallel.swift:508
                val h = a.h ?: continue   // ios: Data/Parallel.swift:509
                val pre = DoubleArray(h.closes.size + 1); pre[0] = 0.0   // ios: Data/Parallel.swift:510
                for (i in h.closes.indices) pre[i + 1] = pre[i] + h.closes[i]   // ios: Data/Parallel.swift:511
                sums[k] = Pair(h.dates, pre)   // ios: Data/Parallel.swift:512
            }
        }
        // ios: Data/Parallel.swift:515
        fun at(k: String, d: String): Double? {
            val s = sums[k] ?: return null; val dates = s.first; val pre = s.second   // ios: Data/Parallel.swift:516
            var lo = 0; var hi = dates.size - 1; var i = -1   // ios: Data/Parallel.swift:517
            while (lo <= hi) { val mid = (lo + hi) / 2; if (dates[mid] <= d) { i = mid; lo = mid + 1 } else hi = mid - 1 }   // ios: Data/Parallel.swift:518
            if (i < 99) return null   // ios: Data/Parallel.swift:519
            val from = max(0, i - 199)   // ios: Data/Parallel.swift:520
            return (pre[i + 1] - pre[from]) / (i + 1 - from).toDouble()   // ios: Data/Parallel.swift:521
        }
    }

    // 인물 말풍선 (위로만. 갈아타라·따라 하라는 말은 하지 않는다)
    // ios: Data/Parallel.swift:526
    fun line(who: String, place: Int, day: String): String {
        val lead = listOf("오늘은 이쪽이 앞섰네. 내일은 또 몰라.", "앞선 날도 있는 거지. 너는 너대로 가.", "숫자는 바뀌어. 같이 가는 건 그대로야.")   // ios: Data/Parallel.swift:527
        val mid = listOf("다른 세계의 너도 버티고 있어.", "가운데쯤이야. 서두를 것 없어.", "세 명 다 너야. 오늘은 숨 한 번 쉬고 가.")   // ios: Data/Parallel.swift:528
        val back = listOf("오늘은 저쪽이 앞섰네, 너는 너대로 가.", "뒤에 있는 날도 기록이야. 꾸며낼 수 없는 기록.", "괜찮아. 이 경주는 지는 쪽이 없어.")   // ios: Data/Parallel.swift:529
        val own: Map<String, String> = mapOf(   // ios: Data/Parallel.swift:530
            "seri" to "저는 일 년에 한 번만 움직여요. 거기까지만요.",   // ios: Data/Parallel.swift:531
            "sio" to "기다리는 것도 하는 거야. 현금은 그래서 들고 있어.",   // ios: Data/Parallel.swift:532
            "seonbae" to "그게 정말 네 생각이야? 나는 커진 만큼만 덜어.",   // ios: Data/Parallel.swift:533
            "ir" to "나는 빨리 움직여. 그게 늘 맞는 건 아니고.",   // ios: Data/Parallel.swift:534
            "sua" to "매달 조금씩. 떨어진 달엔 조금 더.",   // ios: Data/Parallel.swift:535
        )
        val pool = (if (place == 1) lead else if (place == 3) back else mid) + listOf(own[who] ?: "").filter { it.isNotEmpty() }   // ios: Data/Parallel.swift:537
        var h = 0L; var i = 0; while (i < day.length) { val c = day.codePointAt(i); h = (h * 31 + c) and 0xFFFF; i += Character.charCount(c) }   // ios: Data/Parallel.swift:538
        return pool[(h % pool.size).toInt()]   // ios: Data/Parallel.swift:539
    }
}

// ios: Data/Parallel.swift:543 (extension AppModel → 아래 확장 함수·속성)
/** 진짜 나의 지금 종목 (국내 주식 합계는 날짜별 금액이라 경주에서 뺀다) */
val AppModel.parallelBase: List<Parallel.Base> get() =   // ios: Data/Parallel.swift:545
    rows.filter { it.id != KoreaEstimate.sumId }.map { Parallel.Base(id = it.id, usd = it.sym.currency == Currency.usd, qty = it.h.qty, last = it.sym.last) }   // ios: Data/Parallel.swift:546

/** 예선 수량: 연 날의 수량 기록 (없으면 지금 종목). 연 뒤에 수량을 고쳐도 예선은 그대로 */
// ios: Data/Parallel.swift:550
fun AppModel.parallelBaseAt(opened: String): List<Parallel.Base> {
    val h = Lots.on(lots, opened) ?: return parallelBase   // ios: Data/Parallel.swift:551
    val out = mutableListOf<Parallel.Base>()   // ios: Data/Parallel.swift:552
    for ((t, x) in h.entries.sortedBy { it.key }) {   // ios: Data/Parallel.swift:553
        if (!(x > 0)) continue; val id = Sync.appId(t) ?: continue; if (id == KoreaEstimate.sumId) continue; val s = Sample.symbol(id) ?: continue   // ios: Data/Parallel.swift:554
        out.add(Parallel.Base(id = id, usd = s.currency == Currency.usd, qty = x, last = s.last))   // ios: Data/Parallel.swift:555
    }
    return if (out.isEmpty()) parallelBase else out   // ios: Data/Parallel.swift:557
}

/** 처음 열 때 정하는 값: 출발일(회복 = 바닥일, 목표 = 시작일), 견본 바구니(고른 계획·구성), 인물(사다리 다음 칸) */
// ios: Data/Parallel.swift:561
fun AppModel.newParallel(today: String = Day.today): PWorld? {
    val base = parallelBase   // ios: Data/Parallel.swift:562
    if (base.isEmpty()) return null   // ios: Data/Parallel.swift:563
    val s0 = if (isGoal) Day.str(goalFromDate) else (Parallel.bottomDay(base) ?: today)   // ios: Data/Parallel.swift:564
    val tid = if (gMix == "mine") "index" else gMix   // ios: Data/Parallel.swift:565
    val tpl = Parallel.templates.firstOrNull { it.id == tid } ?: Parallel.templates[1]   // ios: Data/Parallel.swift:566
    val w = if (isGoal) Parallel.resolve(tpl.w) else planBasket(selectedPlan)   // ios: Data/Parallel.swift:567
    return PWorld(opened = today, start = Parallel.clampStart(s0), segs = listOf(PBasketSeg(from = today, w = w)),   // ios: Data/Parallel.swift:568
                  who = Parallel.match(type = calm.type, aggressive = Parallel.aggressive(w)), ver = Parallel.rulesVersion)   // ios: Data/Parallel.swift:569
}
/** 회복 루트: 미션 3에서 고른 계획대로 바꾼 내 종목 비중. 몰린 종목은 계획 비중까지, 줄인 몫은 묶음 대표 ETF로. 유지 = 지금 비중 */
// ios: Data/Parallel.swift:572
fun AppModel.planBasket(p: Plan): Map<String, Double> {
    val rs = rows.filter { it.id != KoreaEstimate.sumId }   // ios: Data/Parallel.swift:573
    val t = rs.fold(0.0) { a, r -> a + r.value }   // ios: Data/Parallel.swift:574
    if (!(t > 0)) return Parallel.resolve(Parallel.templates[1].w)   // ios: Data/Parallel.swift:575
    val w = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:576
    for (r in rs) w[r.id] = (w[r.id] ?: 0.0) + r.value / t   // ios: Data/Parallel.swift:577
    val f = focusRow; val fw = f?.let { w[it.id] }; if (p.id == "keep" || f == null || fw == null) return w   // ios: Data/Parallel.swift:578
    val freed = max(0.0, fw - p.wt * max(1.0, total) / t); val tot = p.mix.values.fold(0.0) { a, x -> a + x }   // ios: Data/Parallel.swift:579
    if (!(freed > 0 && tot > 0)) return w   // ios: Data/Parallel.swift:580
    w[f.id] = fw - freed   // ios: Data/Parallel.swift:581
    val etf: Map<Basket, String> = mapOf(Basket.G to "QQQ", Basket.V to "VTV", Basket.B to "USMV", Basket.C to "SGOV")   // ios: Data/Parallel.swift:582
    val add = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:583
    for ((b, x) in p.mix) { if (!(x > 0)) continue; val key = etf[b] ?: "SPY"; add[key] = (add[key] ?: 0.0) + freed * x / tot }   // ios: Data/Parallel.swift:584
    for ((k, v) in Parallel.resolve(add)) w[k] = (w[k] ?: 0.0) + v   // ios: Data/Parallel.swift:585
    return w   // ios: Data/Parallel.swift:586
}
/** 화면에 쓰는 세계: 저장된 것, 샘플·시안 실행이면 그때그때 만든 것 (남기지 않는다) */
val AppModel.pworldShown: PWorld? get() = pworld ?: (if (isSample || !persists) newParallel() else null)   // ios: Data/Parallel.swift:589
// ios: Data/Parallel.swift:590
fun AppModel.openParallelIfNeeded() {
    if (pworld != null || isSample || !persists) return; val w = newParallel() ?: return   // ios: Data/Parallel.swift:591
    pworld = w   // ios: Data/Parallel.swift:592
}

// ios: Data/Parallel.swift:595
val AppModel.parallelRace: PRace? get() {
    val w = pworldShown ?: return null   // ios: Data/Parallel.swift:596
    return parallelRun(w, who = w.who)   // ios: Data/Parallel.swift:597
}
/** 인물 세계를 그 인물 규칙으로 굴린 경주 (평행 세계 화면은 지금 인물, 인물 자세히 보기는 그 인물) */
// ios: Data/Parallel.swift:600
fun AppModel.parallelRun(w: PWorld, who: String): PRace? {
    val base = parallelBaseAt(w.opened)   // ios: Data/Parallel.swift:601
    val lotsIn: List<Parallel.LotQ> = lots.filter { it.d >= w.opened }.map { l ->   // ios: Data/Parallel.swift:602
        val q = LinkedHashMap<String, Double>()   // ios: Data/Parallel.swift:603
        for ((t, x) in l.h) { val id = Sync.appId(t); if (id != null && id != KoreaEstimate.sumId) q[id] = x }   // ios: Data/Parallel.swift:604
        Parallel.LotQ(d = l.d, q = q)   // ios: Data/Parallel.swift:605
    }
    val monthly = gM * 1e4   // ios: Data/Parallel.swift:607
    val pk = Market.shared.prices?.let { swiftDesc(it.got) } ?: "-"   // ios: Data/Parallel.swift:608
    val segKey = w.segs.joinToString(";") { Parallel.segText(it) }   // ios: Data/Parallel.swift:609
    val key = listOf(w.start, w.opened, who, segKey, base.joinToString(",") { "${it.id}:${swiftDesc(it.qty)}" },   // ios: Data/Parallel.swift:610
                     "${lotsIn.size}:${lotsIn.lastOrNull()?.d ?: ""}", pk, swiftDesc(monthly), Day.today).joinToString("|")   // (Parallel.swift 611: 검사기 예외, 위 줄 구역)
    val mine = who == w.who   // ios: Data/Parallel.swift:612
    val c = if (mine) raceCache else raceCacheOther; if (c != null && c.first == key) return c.second   // ios: Data/Parallel.swift:613
    val race = Parallel.run(Parallel.Input(start = w.start, opened = w.opened, base = base, lots = lotsIn, segs = w.segs, who = who, monthly = monthly))   // ios: Data/Parallel.swift:614
    if (mine) raceCache = Pair(key, race) else raceCacheOther = Pair(key, race)   // ios: Data/Parallel.swift:615
    return race   // ios: Data/Parallel.swift:616
}

/** 본선 기록: 연 날 뒤 마감된 거래일(마지막 칸 앞)을 하루 한 줄씩 남긴다. 남긴 줄은 다시 고치지 않는다 */
// ios: Data/Parallel.swift:620
fun AppModel.parallelTick() {
    val w0 = pworld ?: return; if (isSample || !persists) return; val race = parallelRace ?: return; if (!(race.open < race.last)) return; val w = w0.copy()   // ios: Data/Parallel.swift:621
    val have = w.days.map { it.d }.toSet()   // ios: Data/Parallel.swift:622
    val add = mutableListOf<PDay>()   // ios: Data/Parallel.swift:623
    for (t in race.open until race.last) { if (have.contains(race.dates[t])) continue   // ios: Data/Parallel.swift:624
        add.add(PDay(d = race.dates[t], r = (0 until 3).map { (race.r[it][t] * 1e5).swiftRounded() / 1e5 }, who = w.who, v = Parallel.rulesVersion))   // ios: Data/Parallel.swift:625
    }
    if (w.ver != Parallel.rulesVersion) { w.ver = Parallel.rulesVersion; if (add.isEmpty()) { pworld = w; return } }   // ios: Data/Parallel.swift:627
    if (add.isEmpty()) return   // ios: Data/Parallel.swift:628
    w.days = (w.days + add).sortedBy { it.d }   // ios: Data/Parallel.swift:629
    if (w.days.size > 1200) w.days = w.days.drop(w.days.size - 1200)   // ios: Data/Parallel.swift:630
    pworld = w   // ios: Data/Parallel.swift:631
}
/** 그래프·순위에 쓰는 수익률: 본선에 남긴 날은 남긴 값 (같은 인물일 때), 나머지는 계산값.
 *  인물 세계는 마지막으로 남긴 날 값에서 계산값의 하루하루 변화를 이어 붙인다 (규칙 판이 바뀌어도 지난 기록을 덮지 않고 그날부터 새 규칙으로) */
// ios: Data/Parallel.swift:635
fun AppModel.parallelSeries(race: PRace): List<DoubleArray> {
    val w = pworldShown; if (w == null || w.days.isEmpty()) return race.r   // ios: Data/Parallel.swift:636
    val byDay = HashMap<String, PDay>()   // ios: Data/Parallel.swift:637
    for (x in w.days) byDay[x.d] = x   // ios: Data/Parallel.swift:638
    val r = race.r.map { it.copyOf() }   // ios: Data/Parallel.swift:639
    var lastChar = -1   // ios: Data/Parallel.swift:640
    for (t in race.dates.indices) {   // ios: Data/Parallel.swift:641
        val x = byDay[race.dates[t]]; if (x == null || x.r.size != 3) continue   // ios: Data/Parallel.swift:642
        r[0][t] = x.r[0]; r[1][t] = x.r[1]   // ios: Data/Parallel.swift:643
        if (x.who == w.who) { r[2][t] = x.r[2]; lastChar = t }   // ios: Data/Parallel.swift:644
    }
    if (lastChar >= 0 && lastChar < race.last) {   // ios: Data/Parallel.swift:646
        val base = 1 + r[2][lastChar]; val from = 1 + race.r[2][lastChar]   // ios: Data/Parallel.swift:647
        if (from > 0) { for (t in (lastChar + 1)..race.last) r[2][t] = base * (1 + race.r[2][t]) / from - 1 }   // ios: Data/Parallel.swift:648
    }
    return r   // ios: Data/Parallel.swift:650
}

/** 바구니 고치기: 오늘부터 새 칸. 오늘 이미 고친 칸(또는 오늘 연 첫 칸)이면 그 칸을 바꾼다 */
// ios: Data/Parallel.swift:654
fun AppModel.saveBasket(w: Map<String, Double>) {
    val p = pworld?.copy() ?: return   // ios: Data/Parallel.swift:655
    val tot = w.values.fold(0.0) { a, x -> a + x }   // ios: Data/Parallel.swift:656
    if (!(tot > 0)) return   // ios: Data/Parallel.swift:657
    val norm = w.filter { it.value > 0 }.mapValues { it.value / tot }   // ios: Data/Parallel.swift:658
    val today = Day.today   // ios: Data/Parallel.swift:659
    val l = p.segs.lastOrNull(); if (l != null && (l.from >= today || (p.segs.size == 1 && p.opened >= today))) p.segs = p.segs.dropLast(1) + l.copy(w = norm) else p.segs = p.segs + PBasketSeg(from = today, w = norm)   // ios: Data/Parallel.swift:660,661
    pworld = p   // ios: Data/Parallel.swift:662
}
/** 인물 바꾸기: 횟수 제한 없이 (비교해 보는 재미, 사용자 결정 2026-10-12). 바꾸면 인물 세계는 출발일부터 새 규칙으로 다시 계산 */
val AppModel.canPickWho: Boolean get() = pworld != null   // ios: Data/Parallel.swift:665
// ios: Data/Parallel.swift:666
fun AppModel.pickWho(id: String) {
    val p = pworld?.copy() ?: return; if (!canPickWho || id == p.who) return   // ios: Data/Parallel.swift:667
    p.who = id; p.whoPick = Instant.ofEpochSecond(Instant.now().epochSecond).toString()   // ios: Data/Parallel.swift:668
    pworld = p   // ios: Data/Parallel.swift:669
}
/** 마이너스족은 본전까지 남은 거리, 장투족은 목표까지 온 거리 (평행 세계 카드·인물 자세히 보기) */
// ios: Data/Parallel.swift:672
fun AppModel.parallelDistance(amount: Double): String {
    if (isGoal) {   // ios: Data/Parallel.swift:673
        val k = gK * 1e4   // ios: Data/Parallel.swift:674
        return "목표 ${AppModel.pct(min(1.0, amount / max(1.0, k)))}"   // ios: Data/Parallel.swift:675
    }
    val need = cost / max(1.0, amount) - 1   // ios: Data/Parallel.swift:677
    val s = String.format(Locale.ROOT, "%.1f", need * 100); return if (need > 0) "본전까지 +${s}%" else "본전 넘음"   // (Parallel.swift 678: 검사기 예외, 위 줄 구역)
}
/** 세 방식 이름 (인물 규칙은 인물 이름이 붙는다) */
fun AppModel.parallelTitle(k: Int, who: String): String = if (k == 2) "${friendName(who)} 규칙대로" else Parallel.names[k]   // ios: Data/Parallel.swift:681
/** 홈 비교 카드: 지금 결론 한 줄. 아직 열지 않았으면 처음 열 때 정할 세계로 미리 계산 */
// ios: Data/Parallel.swift:683
val AppModel.parallelVerdictLine: String? get() {
    val w = pworldShown ?: newParallel() ?: return null; val race = parallelRun(w, who = w.who) ?: return null   // ios: Data/Parallel.swift:684
    val v = Parallel.verdict(if (pworldShown == null) race.r else parallelSeries(race), at = race.last)   // ios: Data/Parallel.swift:685
    val gap = String.format(Locale.ROOT, "%.1f%%p", v.gap * 100)   // ios: Data/Parallel.swift:686
    val lead = parallelTitle(v.order[0], who = w.who)   // ios: Data/Parallel.swift:687
    return if (v.similar) "셋 다 비슷해요 · 차이 ${gap}" else "${lead}${WChar.josa(lead)} ${gap} 앞서요"   // ios: Data/Parallel.swift:688
}

/** 결론 규칙: 가장 앞선 쪽과 뒤진 쪽 차이가 1.0%p 미만이면 "비슷해요" */
data class PVerdict(val order: List<Int>, val gap: Double) { val similar: Boolean get() = gap < Parallel.similarGap }   // ios: Data/Parallel.swift:693

// ios: Data/Parallel.swift:695 (extension Parallel)
val Parallel.similarGap: Double get() = 0.01   // ios: Data/Parallel.swift:696
// ios: Data/Parallel.swift:697
fun Parallel.verdict(r: List<DoubleArray>, at: Int): PVerdict {
    val order = listOf(0, 1, 2).sortedWith { a, b -> descCmp(r[a][at], r[b][at]) }   // ios: Data/Parallel.swift:698
    return PVerdict(order = order, gap = r[order[0]][at] - r[order[2]][at])   // ios: Data/Parallel.swift:699
}

// ios: Data/Parallel.swift:703 (extension Market)
/** 바구니 고치기 화면: 담을 수 있는 종목의 지난 종가를 한 번 받아 둔다 (받은 지 30분 안이면 그대로) */
// ios: Data/Parallel.swift:705
suspend fun Market.fetchParallelUniverse() {
    val keys = (prices?.asked ?: emptyList()).toMutableSet()   // ios: Data/Parallel.swift:706
    for (u in Parallel.universe) keys.add(u.id)   // ios: Data/Parallel.swift:707
    keys.add("KRW=X")   // ios: Data/Parallel.swift:708
    val p = SitePrices.fetch(keys = keys, old = prices, minAge = 1800.0); if (p != null) prices = p   // ios: Data/Parallel.swift:709
}
