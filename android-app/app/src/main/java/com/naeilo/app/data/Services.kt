package com.naeilo.app.data

import com.naeilo.app.pending.*
import com.naeilo.app.net.*
import com.naeilo.app.core.calc.*
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import kotlinx.serialization.decodeFromString
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// 시세 제공 스텁: 시안과 같은 값을 만든다.
// 실제 연결은 이 인터페이스를 구현해 AppModel.prices 에 넣으면 된다.
// ios: Data/Services.swift:5
interface PriceProvider {
    /** 전일 종가 */
    // ios: Data/Services.swift:7
    fun close(symbol: Symbol): Double
    /** 기간 가격 흐름 (마지막 값 = 지금 가격, 1일 = 전일 종가 → 지금) */
    // ios: Data/Services.swift:9
    fun series(symbol: Symbol, period: Period): List<Double>
    /** x = 0 (3년 전) … 1 (어제) 시점의 종가 */
    // ios: Data/Services.swift:11
    fun price(symbol: Symbol, at: Double): Double
    /** 실제 과거 시세가 있는지 (없으면 과거는 평평한 선) */
    // ios: Data/Services.swift:13
    fun hasHistory(symbol: Symbol): Boolean
}

// ios: Data/Services.swift:16,17
enum class Period(val raw: String) {
    d1("d1"), w1("w1"), m1("m1"), m3("m3"), m6("m6"), y1("y1"), y3("y3");

    // ios: Data/Services.swift:18,20,21
    val label: String get() {
        // 영어는 칸이 좁아 짧게 (1D·1W·1M…)
        if (AppModel.en) return when (this) { d1 -> "1D"; w1 -> "1W"; m1 -> "1M"; m3 -> "3M"; m6 -> "6M"; y1 -> "1Y"; y3 -> "3Y" }
        return when (this) { d1 -> "1일"; w1 -> "1주"; m1 -> "1개월"; m3 -> "3개월"; m6 -> "6개월"; y1 -> "1년"; y3 -> "3년" }
    }
    /** 3년(1095일)에 대한 비율 */
    // ios: Data/Services.swift:24,25
    val span: Double get() =
        when (this) { d1, w1 -> 0.0; m1 -> 1.0 / 36; m3 -> 1.0 / 12; m6 -> 1.0 / 6; y1 -> 1.0 / 3; y3 -> 1.0 }
}

// ios: Data/Services.swift:29
class StubPriceProvider : PriceProvider {
    // DRNK 가격 흐름 = 내 지난 자산 기록(2024-01-01~어제, 1010일)을 마지막 값 1로 나눈 비율
    // ios: Data/Services.swift:31,32,33,34,35,36
    val drnkRatio: List<Double> by lazy {
        Defaults.asset("drnk_ratio", "json")
            ?.let { data -> runCatching { NJson.decodeFromString<List<Double>>(data) }.getOrNull() } ?: listOf(1.0)
    }

    // ios: Data/Services.swift:38
    override fun close(symbol: Symbol): Double = symbol.close
    // ios: Data/Services.swift:39
    override fun price(symbol: Symbol, at: Double): Double {
        val s = symbol; val x = at
        // 국내 주식 합계: 그날까지 넣은 마지막 금액 (첫 기록 전은 0). x 를 3년 날짜로 (0 = 3년 전, 1 = 오늘)
        // ios: Data/Services.swift:41
        if (s.id == KoreaEstimate.sumId) return MC.krAt(CustomSymbols.shared.krLog, Day.str(Instant.now().plusMillis((-(1 - x) * 1095.75 * 86400 * 1000).toLong())))
        // ios: Data/Services.swift:42
        if (PriceHistory.of(s.id) != null) return exp(logPrice(s, x))   // 실제 종가 그대로
        // ios: Data/Services.swift:43
        return s.close * exp(logPrice(s, x) - logPrice(s, 1.0))
    }

    /** 로그 가격. x = 0 (3년 전) … 1 (어제). 시안의 lfA 와 같은 식. */
    // ios: Data/Services.swift:47
    fun logPrice(s: Symbol, x: Double): Double {
        // ios: Data/Services.swift:48
        if (s.id == KoreaEstimate.sumId) return ln(max(1.0, price(s, at = x)))
        // ios: Data/Services.swift:49,51,52
        PriceHistory.of(s.id)?.takeIf { it.closes.size > 1 }?.let { h ->
            // 3년치 거래일을 0…1 로 펴서 사이를 잇는다
            val L = (h.closes.size - 1).toDouble(); val t = max(0.0, min(L, x * L)); val i = t.toInt(); val f = t - i
            return ln(h.closes[i] * (1 - f) + h.closes[min(L.toInt(), i + 1)] * f)
        }
        // ios: Data/Services.swift:54,55,56,57,58
        if (s.id == "DRNK") {
            val L = (drnkRatio.size - 1).toDouble()
            val t = max(0.0, min(L, L - (1 - x) * 1095)); val i = t.toInt(); val f = t - i
            val v = drnkRatio[i] * (1 - f) + drnkRatio[min(L.toInt(), i + 1)] * f
            return ln(v)
        }
        // 과거 시세가 없으면 만들어 내지 않는다: 전일 종가 그대로 (평평한 선). 화면은 hasHistory 로 "과거 시세 없음"을 알린다
        // ios: Data/Services.swift:61
        return ln(max(1e-9, s.close))
    }

    /** 실제 과거 시세(또는 샘플 DRNK 기록)가 있는지 */
    // ios: Data/Services.swift:65
    override fun hasHistory(symbol: Symbol): Boolean = symbol.id == "DRNK" || PriceHistory.of(symbol.id) != null

    // ios: Data/Services.swift:67,68,69,70
    private fun dayCloses(s: Symbol, n: Int): List<Double> {
        PriceHistory.of(s.id)?.let { h -> return h.closes.takeLast(n + 1) }
        if (s.id == "DRNK") return drnkRatio.takeLast(n + 1).map { s.close * it }
        return List(n + 1) { s.close }
    }

    // ios: Data/Services.swift:73,74,75,76,77,78
    override fun series(symbol: Symbol, period: Period): List<Double> {
        val s = symbol
        val q = Market.shared.quote(s)
        return when (period) {
            Period.d1 -> listOf(q.prevClose, q.last)
            Period.w1 -> dayCloses(s, 5).drop(1) + q.last
            else -> {
                // ios: Data/Services.swift:79,81,82
                if (PriceHistory.of(s.id) != null) {
                    // 테스트 자료: 마지막 날이 이미 오늘 값이라 그대로 쓰고 끝만 지금 가격으로 맞춘다
                    val sp = period.span
                    return (0..60).map { i -> if (i == 60) q.last else exp(logPrice(s, 1 - sp + sp * i.toDouble() / 60)) }
                }
                // ios: Data/Services.swift:84,85
                val sp = period.span; val k = q.last / max(1e-9, s.close)
                (0..60).map { i -> s.close * exp(logPrice(s, 1 - sp + sp * i.toDouble() / 60) - logPrice(s, 1.0)) * (if (i == 60) k else 1.0) }
            }
        }
    }
}
