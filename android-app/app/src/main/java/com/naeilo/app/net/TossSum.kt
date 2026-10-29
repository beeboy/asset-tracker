package com.naeilo.app.net

import com.naeilo.app.AppModel
import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.calc.MC
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Lot
import com.naeilo.app.data.Lots
import com.naeilo.app.data.Period
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 종목 탭 '내 종목 합산' 그래프: 토스증권 날짜별 합산과 같은 줄 (docs/decisions.md 2026-10-29)
// 줄 D = 한국 D+1일 미국 장 마감 순간(05:00, 서머타임 끝나면 06:00)의 평가금. D 는 달력 날짜(주말 포함)
//   국내: 한국 D일 이하 마지막 종가 × 그날 수량
//   미국: 미국 D일 이하 마지막 정규장 종가 × 그날 수량 × 매매기준율(고시 날짜 ≤ D 중 마지막)
// 그래서 주말 줄은 금요일 줄과 원 단위까지 같다. 맨 끝은 지금 평가액(홈 평가액)
//
// 계산(line·points)이 읽는 입력은 아래뿐이다. 기기 시계·스위치 등 다른 입력을 읽지 않는다
//   items  종목별: 사이트 티커, 미국 종목인가, 날짜별 종가(PriceHistory, 국내 주식 합계는 krLog), 첫 기록 전 0인가, 기록이 없을 때 쓸 값, 지금 수량
//   lots   날짜별 수량 기록(Lots). 가장 이른 기록 전 날짜는 첫 기록 수량, 기록이 없으면 지금 수량
//   mar    날짜별 매매기준율(MarDaily). 가장 이른 고시 전 날짜는 가장 이른 값
//   last   마지막으로 끝난 줄의 날짜(lastLine), now 지금 평가액
// ios: Data/TossSum.swift:14
object TossSum {
    // ios: Data/TossSum.swift:15,16,17,18,19,20,21,22
    class Item(
        val key: String,              // 사이트 티커 (Lots 칸 이름)
        val usd: Boolean,
        val dates: List<String>,      // 오름차순 "yyyy-MM-dd" (그 시장 날짜)
        val closes: List<Double>,
        val zeroBefore: Boolean,      // 국내 주식 합계: 첫 기록 전은 0
        val flat: Double,             // 과거 시세가 없을 때 쓰는 값 (평평하게)
        val qty: Double,              // 지금 수량 (수량 기록이 없을 때)
    )
    class Point(val date: String?, val value: Double)   // ios: Data/TossSum.swift:24 date null = 지금

    /** 날짜별 매매기준율 (고시 날짜 오름차순) */
    // ios: Data/TossSum.swift:27,28,29,30,31,32
    class Rates(val d: List<String>, val c: List<Double>) {
        fun at(day: String): Double {
            if (c.isEmpty()) return 0.0
            val k = idx(d, day)
            return c[maxOf(0, k)]
        }
    }

    /** 날짜 목록에서 day 이하 마지막 자리 (없으면 -1) */
    // ios: Data/TossSum.swift:37,38,39,40
    fun idx(ds: List<String>, day: String): Int {
        var lo = 0; var hi = ds.size - 1; var k = -1
        while (lo <= hi) { val mid = (lo + hi) / 2; if (ds[mid] <= day) { k = mid; lo = mid + 1 } else hi = mid - 1 }
        return k
    }

    // ios: Data/TossSum.swift:43,44,45,46
    fun price(x: Item, day: String): Double {
        if (x.closes.isEmpty() || x.dates.size != x.closes.size) return x.flat
        val k = idx(x.dates, day)
        return if (k >= 0) x.closes[k] else if (x.zeroBefore) 0.0 else x.closes[0]
    }

    /** 줄 D (원). 수량은 그날 기록, 가장 이른 기록 전이면 첫 기록, 기록이 없으면 지금 수량 */
    // ios: Data/TossSum.swift:50,51,52,53,54
    fun line(items: List<Item>, lots: List<Lot>, mar: Rates, day: String): Double {
        val fx = mar.at(day); val h = Lots.on(lots, day) ?: lots.firstOrNull()?.h
        return items.fold(0.0) { s, x ->
            val v = price(x, day) * (h?.let { it[x.key] ?: 0.0 } ?: x.qty)
            s + (if (x.usd) v * fx else v)
        }
    }

    /** 기간별 점: 날마다 줄(1일 = 어제 줄, 1주 = 7줄, 1·3개월 = 그 기간 날마다), 6개월부터는 주(월~일)마다 마지막 날 줄. 끝에 지금 */
    // ios: Data/TossSum.swift:59,60,61,62,63,65
    fun points(items: List<Item>, lots: List<Lot>, mar: Rates, period: Period, last: String, now: Double): List<Point> {
        val end = MC.dayNum(last); val from = end - period.days + 1
        val days = ArrayList<Int>()
        for (n in from..end) {
            val p = days.lastOrNull()
            if (period.weekly && p != null && (p + 3) / 7 == (n + 3) / 7) days[days.size - 1] = n else days.add(n)   // 1970-01-01 = 목, 월요일에 시작하는 주
        }
        return days.map { n -> val d = MC.dayStr(n); Point(d, line(items, lots, mar, d)) } + Point(null, now)
    }

    /** 마지막으로 끝난 줄의 날짜: 뉴욕 오후 4시(정규장 마감)가 지난 가장 늦은 미국 날짜 */
    // ios: Data/TossSum.swift:69,70,71 (72·73 은 검사 예외)
    fun lastLine(t: Instant): String {
        val z = t.atZone(ZoneId.of("America/New_York"))
        val n = MC.dayNum(String.format(Locale.ROOT, "%04d-%02d-%02d", z.year, z.monthValue, z.dayOfMonth))
        return MC.dayStr(if (z.hour >= 16) n else n - 1)
    }
}

/** 종목 탭 합산 그래프 입력: 지금 종목 + 수량 기록에만 남은(판) 종목. 평가에서 빠지는 종목(Market.needsKey)·가상 종목은 뺀다 */
fun AppModel.tossSumItems(): List<TossSum.Item> {   // ios: Data/TossSum.swift:77,79
    val out = ArrayList<TossSum.Item>(); val seen = HashSet<String>()   // ios: Data/TossSum.swift:80
    fun add(s: Symbol, q: Double) {   // ios: Data/TossSum.swift:81
        val key = Sync.siteTicker(s.id)   // ios: Data/TossSum.swift:82
        if (key in seen || Market.shared.needsKey(s)) return   // ios: Data/TossSum.swift:83
        seen.add(key)   // ios: Data/TossSum.swift:84
        if (s.id == KoreaEstimate.sumId) {   // ios: Data/TossSum.swift:85
            val L = CustomSymbols.shared.krLog   // ios: Data/TossSum.swift:86
            out.add(TossSum.Item(key, false, L.map { it.d }, L.map { it.v }, zeroBefore = true, flat = s.close, qty = q))   // ios: Data/TossSum.swift:87
        } else {   // ios: Data/TossSum.swift:88
            val h = if (Sample.isVirtual(s.id)) null else PriceHistory.of(s.id)   // ios: Data/TossSum.swift:89
            out.add(TossSum.Item(key, s.currency == Currency.usd, h?.dates ?: emptyList(), h?.closes ?: emptyList(), zeroBefore = false, flat = s.close, qty = q))   // ios: Data/TossSum.swift:90
        }
    }
    for (r in rows) add(r.sym, r.h.qty)   // ios: Data/TossSum.swift:93
    for (k in lots.flatMap { it.h.keys }.toSet()) if (k !in seen) {   // ios: Data/TossSum.swift:94
        val s = Sync.appId(k)?.let { Sample.symbol(it) }; if (s != null) add(s, 0.0)   // ios: Data/TossSum.swift:95
    }
    return out   // ios: Data/TossSum.swift:97
}

/** 날짜별 매매기준율 3년 (서울외국환중개). 서울외국환중개에서 직접 받고, 못 받으면 사이트 data/mar_daily.json ({"src","updated","d","c"}, 사이트 자료를 쓰는 내부 베타만). 하루 한 번 */
// ios: Data/TossSum.swift:102
object MarDaily {
    @Serializable data class File(var got: Double? = null, var d: List<String>, var c: List<Double>)   // ios: Data/TossSum.swift:103 got = 이 기기가 받은 때 (사이트 파일엔 없다)
    const val name = "mar_daily.json"   // ios: Data/TossSum.swift:104
    fun cached(): File? = Store.read(File.serializer(), name)   // ios: Data/TossSum.swift:105

    /** 저장해 둔 값 + 지금 쓰는 고시(MarFeed 의 오늘·앞 고시)를 합친 표 */
    // ios: Data/TossSum.swift:108,109,110,111,112,113,114
    fun rates(f: File?, m: MarFeed.File?): TossSum.Rates {
        val map = HashMap<String, Double>()
        if (f != null) for ((d, c) in f.d.zip(f.c)) if (c > 0) map[d] = c
        m?.prev?.let { if (it.rate > 0) map[it.date] = it.rate }
        if (m != null && m.rate > 0) map[m.date] = m.rate
        val ds = map.keys.sorted()
        return TossSum.Rates(ds, ds.map { map[it]!! })
    }

    /** 받은 지 하루가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값 */
    suspend fun fetch(): File? = withContext(Dispatchers.IO) {   // ios: Data/TossSum.swift:118
        val old = cached()   // ios: Data/TossSum.swift:119
        if (old != null && System.currentTimeMillis() / 1000.0 - (old.got ?: 0.0) < 86400) return@withContext old   // ios: Data/TossSum.swift:120
        var f: File? = smbs()   // ios: Data/TossSum.swift:121
        if (f == null && Config.usesCollectedData) {   // ios: Data/TossSum.swift:122
            val c = URL(Config.data, name).toString()   // ios: Data/TossSum.swift:123
            val url = c + "?" + "t" + "=" + (System.currentTimeMillis() / 1000 / 3600)   // ios: Data/TossSum.swift:124
            val r = runCatching { FeedReq(url, timeout = 10.0).send() }.getOrNull()   // ios: Data/TossSum.swift:125
            if (r != null && r.first == 200) f = runCatching { NJson.decodeFromString(File.serializer(), r.second.decodeToString()) }.getOrNull()?.takeIf { it.d.size == it.c.size && it.d.isNotEmpty() }   // ios: Data/TossSum.swift:126
        }
        val x = f ?: return@withContext old   // ios: Data/TossSum.swift:128
        x.got = System.currentTimeMillis() / 1000.0   // ios: Data/TossSum.swift:129
        Store.write(File.serializer(), x, name)   // ios: Data/TossSum.swift:130
        x   // ios: Data/TossSum.swift:131
    }

    /** 서울외국환중개 매매기준율 표 3년 (MarFeed.smbs 와 같은 파싱, 두 자리 연도) */
    private suspend fun smbs(): File? {   // ios: Data/TossSum.swift:135
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneId.of("Asia/Seoul"))
        val st = f.format(Instant.now().minusSeconds(1096 * 86400L)); val end = f.format(Instant.now())   // ios: Data/TossSum.swift:137
        val req = FeedReq("http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_${st}_${end}", timeout = 15.0)   // ios: Data/TossSum.swift:138,139
        req.setValue("Mozilla/5.0", "User-Agent")   // ios: Data/TossSum.swift:140
        req.setValue("http://www.smbs.biz/ExRate/StdExRate.jsp", "Referer")   // ios: Data/TossSum.swift:141
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null; if (code != 200) return null   // ios: Data/TossSum.swift:142
        return smbsParse(String(data, Charsets.ISO_8859_1))   // ios: Data/TossSum.swift:143
    }
    /** smbs 응답 글자 → 날짜별 표 (서버 없이 시험할 수 있게 따로 둔다) */
    internal fun smbsParse(t: String): File? {
        val re = Regex("label=['\"]([^'\"]+)['\"][^>]*?value=['\"]([\\d,.]+)['\"]")   // ios: Data/TossSum.swift:144
        val map = HashMap<String, Double>()   // ios: Data/TossSum.swift:145
        for (m in re.findAll(t)) {   // ios: Data/TossSum.swift:146
            val lr = m.groupValues[1]; val vr = m.groupValues[2]   // ios: Data/TossSum.swift:147
            val rate = vr.replace(",", "").toDoubleOrNull()?.takeIf { it > 500 && it < 5000 } ?: continue   // ios: Data/TossSum.swift:148
            val n = lr.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }   // ios: Data/TossSum.swift:149
            if (n.size < 3) continue   // ios: Data/TossSum.swift:150
            map[String.format(Locale.ROOT, "%04d-%02d-%02d", if (n[0] < 100) 2000 + n[0] else n[0], n[1], n[2])] = rate   // ios: Data/TossSum.swift:151
        }
        if (map.isEmpty()) return null   // ios: Data/TossSum.swift:153
        val ds = map.keys.sorted()   // ios: Data/TossSum.swift:154
        return File(d = ds, c = ds.map { map[it]!! })   // ios: Data/TossSum.swift:155
    }
}
