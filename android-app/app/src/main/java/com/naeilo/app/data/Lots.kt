package com.naeilo.app.data

import com.naeilo.app.net.*
import com.naeilo.app.core.calc.*
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

// 수량이 바뀐 날만 남기는 기록 (사이트 web/app.js recordLot 과 같은 모양·같은 규칙). 내 길의 실제 평가액을 그날 수량으로 계산한다.
// d 날짜, h {사이트 티커: 수량}. 기기 동기화에서는 사이트 입력값의 lots 칸에 날짜별로 합친다.
// ios: Data/Lots.swift:5,6,7
@Serializable
data class Lot(var d: String, var h: Map<String, Double>)

// ios: Data/Lots.swift:10,11
object Lots {
    const val key = "lots"; const val cap = 400

    // ios: Data/Lots.swift:13,14
    fun load(): List<Lot> =
        Defaults.prefs.getString(key, null)?.let { runCatching { NJson.decodeFromString<List<Lot>>(it) }.getOrNull() } ?: emptyList()
    // ios: Data/Lots.swift:16,17
    fun save(l: List<Lot>) {
        runCatching { NJson.encodeToString(l) }.getOrNull()?.let { data -> Defaults.prefs.edit().putString(key, data).apply() }
    }

    /** 웹 recordLot: 마지막 기록과 수량이 다르면 오늘 줄(같은 날이면 고친다). 첫 줄은 내 길 시작일이 더 이르면 그 날짜로 */
    // ios: Data/Lots.swift:21,22,23,24,25,26,28
    fun record(L: MutableList<Lot>, h: Map<String, Double>, start: String?, today: String = Day.today) {
        val last = L.lastOrNull()
        if (last != null) {
            if (last.h == h || last.d > today) return      // 같거나, 기기 시계가 거꾸로 가면 그대로
            if (last.d == today) L[L.size - 1] = last.copy(h = h) else L.add(Lot(d = today, h = h))
        } else {
            L.add(Lot(d = start?.let { minOf(it, today) } ?: today, h = h))
        }
        if (L.size > cap) L.subList(0, L.size - cap).clear()
    }

    /** 날짜 d 에 들고 있던 수량 (그날 또는 그 전 마지막 기록). 기록이 없으면 null */
    // ios: Data/Lots.swift:32
    fun on(L: List<Lot>, d: String): Map<String, Double>? = L.lastOrNull { it.d <= d }?.h

    /** 동기화: 지난 날은 사이트 기록만 쓰고 (앱에만 남은 예전 줄이 웹과 다른 시작값을 만들지 않게), 오늘 줄은 이 기기 값. 사이트에 기록이 없으면 이 기기 기록 */
    // ios: Data/Lots.swift:35,36
    fun withSite(site: List<Lot>, mine: List<Lot>): List<Lot> =
        if (site.isEmpty()) mine else merge(site, mine.filter { it.d >= Day.today })
    /** 동기화: 날짜별로 합친다 (같은 날은 받은 쪽) */
    // ios: Data/Lots.swift:39,40,41,42,43
    fun merge(mine: List<Lot>, remote: List<Lot>): List<Lot> {
        val m = HashMap<String, Lot>()
        for (x in mine) m[x.d] = x
        for (x in remote) m[x.d] = x
        return m.values.sortedBy { it.d }.takeLast(cap)
    }
    // ios: Data/Lots.swift:45,46,47
    fun fromJSON(any: JsonElement?): List<Lot> {
        if (any == null) return emptyList()
        return runCatching { NJson.decodeFromJsonElement<List<Lot>>(any) }.getOrNull() ?: emptyList()
    }
    // ios: Data/Lots.swift:49,50
    fun toJSON(l: List<Lot>): JsonElement? = runCatching { NJson.encodeToJsonElement(l) }.getOrNull()
}

/** 지금 수량을 수량 기록에 남긴다 (바뀐 날만). 샘플·시안 실행에서는 남기지 않는다 */
// ios: Data/Lots.swift:63,65,66,67,68,69,70,71,72
fun AppModel.recordLot() {
    if (isSample || !persists) return
    val h = mutableMapOf<String, Double>()
    for (x in holdings.filter { it.qty > 0 && !Sample.isVirtual(it.symbol) }) h[Sync.siteTicker(x.symbol)] = x.qty
    if (h.isEmpty()) return
    val L = lots.toMutableList()
    Lots.record(L, h = h, start = Day.str(pathStartDate))
    if (L != lots) lots = L
}

/** 오늘 손익(원화)과 등락: 어제 수량 × 전일 종가에서 지금까지. 오늘 더 사고판 것도 넣는다
 *  손익 = 지금 평가액 − 어제 수량 × 전일 종가 − 오늘 산 돈 + 오늘 판 돈. 산·판 돈은 내 기록(그때 가격), 그 밖에 늘어난 수량(종목 추가·직접 고치기)은 구매가(평균 단가)
 *  다 판 종목은 내 종목에서 빠져 여기 오지 않는다 */
fun AppModel.todayPL(r: AppModel.Row): Pair<Double, Double> {   // ios: Data/Lots.swift:80
    val s = r.sym; val t = Sync.siteTicker(r.h.symbol)   // ios: Data/Lots.swift:81
    if (isSample || Sample.isVirtual(r.h.symbol) || s.last <= 0) return Pair(krw(s, r.h.qty * s.quote.prevClose * s.quote.change), s.quote.change)   // ios: Data/Lots.swift:82
    val q0 = lots.lastOrNull { it.d < Day.today }?.h?.get(t) ?: 0.0   // ios: Data/Lots.swift:83 어제 수량
    val f = myRecord.lastOrNull()?.takeIf { it.d == Day.today }?.f?.get(t) ?: listOf(0.0, 0.0)   // ios: Data/Lots.swift:84 오늘 산·판 [수량 ±, 원화 금액 ±]
    val dx = r.h.qty - q0 - f[0]   // ios: Data/Lots.swift:85 기록 없이 바뀐 수량
    val w = krw(s, r.h.qty * s.last - q0 * s.quote.prevClose - dx * r.h.avg) - f[1]   // ios: Data/Lots.swift:86
    val base = krw(s, q0 * s.quote.prevClose + maxOf(0.0, dx) * r.h.avg) + maxOf(0.0, f[1])   // ios: Data/Lots.swift:87
    return Pair(w, if (base > 0) w / base else 0.0)   // ios: Data/Lots.swift:88
}
