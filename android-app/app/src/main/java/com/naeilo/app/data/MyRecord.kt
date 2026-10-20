package com.naeilo.app.data

import com.naeilo.app.pending.*
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.math.floor

// naeilo를 쓴 날부터의 내 기록: 하루 한 줄 (사이트 web/app.js recordDay 와 같은 모양·같은 계산).
// d 날짜, v 평가액(원), c 넣은 돈(원), q {사이트 티커: [수량, 원화 주가]}, f {사이트 티커: [산·판 수량, 원화 금액]}
// 넣은 돈 = 전날 넣은 돈 + 그날 '더 샀어요·팔았어요' 금액(그때 가격) + 그 밖에 바뀐 수량(직접 고치기·추가·삭제)×그날 가격. 첫날은 그날 평가액.
// 이 폰에만 쌓이고 '내 폰에 저장하기' 파일과 기기 동기화(daily 칸)에 같이 들어간다.
// ios: Data/MyRecord.swift:7,8,9,10,11,12
@Serializable
data class DayRec(
    var d: String,
    var v: Double,
    var c: Double,
    var q: Map<String, List<Double>>,
    var f: Map<String, List<Double>>? = null,
)

/** Swift Double.rounded(): 반은 0에서 먼 쪽으로 */
private fun rounded(x: Double): Double = if (x < 0) -floor(-x + 0.5) else floor(x + 0.5)

// ios: Data/MyRecord.swift:15,16
object MyRecord {
    const val key = "myRecord"; const val cap = 1200

    // ios: Data/MyRecord.swift:18,19
    fun load(): List<DayRec> =
        Defaults.prefs.getString(key, null)?.let { runCatching { NJson.decodeFromString<List<DayRec>>(it) }.getOrNull() } ?: emptyList()
    // ios: Data/MyRecord.swift:21,22
    fun save(d: List<DayRec>) {
        runCatching { NJson.encodeToString(d) }.getOrNull()?.let { data -> Defaults.prefs.edit().putString(key, data).apply() }
    }

    /** 오늘 줄을 새로 쓰거나 고친다. q = 지금 보유 {티커: [수량, 원화 주가]}, trade = 방금 저장한 매매 (티커, 수량 ±, 원화 금액 ±) */
    // ios: Data/MyRecord.swift:26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,42,43,44
    fun update(D: MutableList<DayRec>, q: Map<String, List<Double>>, trade: Triple<String, Double, Double>?, today: String = Day.today) {
        D.lastOrNull()?.let { l -> if (l.d > today) return }           // 기기 시계가 거꾸로 가면 건드리지 않는다
        val cur = if (D.lastOrNull()?.d == today) D.last() else null
        val back = if (cur == null) 1 else 2
        val prev: DayRec? = if (D.size >= back) D[D.size - back] else null
        if (prev == null && q.isEmpty()) return
        val f = (cur?.f ?: emptyMap()).toMutableMap()
        trade?.let { tr -> val a = f[tr.first] ?: listOf(0.0, 0.0); f[tr.first] = listOf(a[0] + tr.second, a[1] + tr.third) }
        val pq = prev?.q ?: emptyMap()
        var c = prev?.c ?: 0.0; var v = 0.0
        for (t in q.keys + pq.keys + f.keys) {
            val now = q[t]?.get(0) ?: 0.0; val fl = f[t] ?: listOf(0.0, 0.0)
            val px = q[t]?.get(1) ?: pq[t]?.get(1) ?: (if (fl[0] != 0.0) fl[1] / fl[0] else 0.0)
            c += fl[1] + (now - (pq[t]?.get(0) ?: 0.0) - fl[0]) * px
            v += now * (q[t]?.get(1) ?: 0.0)
        }
        val rec = DayRec(d = today, v = rounded(v), c = rounded(c), q = q, f = if (f.isEmpty()) null else f)
        if (cur != null) D[D.size - 1] = rec else D.add(rec)
        if (D.size > cap) D.subList(0, D.size - cap).clear()
    }

    /** 동기화: 날짜별로 합친다 (같은 날은 받은 쪽) */
    // ios: Data/MyRecord.swift:48,49,50,51,52
    fun merge(mine: List<DayRec>, remote: List<DayRec>): List<DayRec> {
        val m = HashMap<String, DayRec>()
        for (x in mine) m[x.d] = x
        for (x in remote) m[x.d] = x
        return m.values.sortedBy { it.d }.takeLast(cap)
    }
    // ios: Data/MyRecord.swift:54,55,56
    fun fromJSON(any: JsonElement?): List<DayRec> {
        if (any == null) return emptyList()
        return runCatching { NJson.decodeFromJsonElement<List<DayRec>>(any) }.getOrNull() ?: emptyList()
    }
    // ios: Data/MyRecord.swift:58,59
    fun toJSON(d: List<DayRec>): JsonElement? = runCatching { NJson.encodeToJsonElement(d) }.getOrNull()
}

/** 지금 보유로 오늘 기록을 고친다. 샘플(DRNK·QQQ)이면 남기지 않는다. 앱 시작·앞으로 올 때·홈을 열 때·시세를 받을 때·수량이 바뀔 때
 *  가격을 아직 못 받은 종목은 매수 단가로 어림하고, 종목 정보도 없으면 그 종목만 뺀다 (한 종목 때문에 하루가 통째로 빠지지 않게) */
// ios: Data/MyRecord.swift:63,66,67,68,69,70,71,72,73,74,76,77,78
fun AppModel.recordDay(trade: Triple<String, Double, Double>? = null) {
    if (isSample || !persists) return   // 시안 확인용 실행에서는 남기지 않는다
    recordLot()     // 수량 기록 (바뀐 날만)
    val q = mutableMapOf<String, List<Double>>()
    for (h in holdings.filter { it.qty > 0 }) {
        val s = Sample.symbol(h.symbol) ?: continue
        val px = if (s.last > 0) s.last else h.avg
        if (px <= 0) continue
        q[Sync.siteTicker(h.symbol)] = listOf(h.qty, rounded(krw(s, px) * 100) / 100)
    }
    val d = myRecord.toMutableList()
    MyRecord.update(d, q = q, trade = trade)
    if (d != myRecord) myRecord = d
}
