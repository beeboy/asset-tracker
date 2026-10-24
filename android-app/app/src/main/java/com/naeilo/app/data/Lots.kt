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
