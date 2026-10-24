package com.naeilo.app.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.SwiftDate
import com.naeilo.app.core.getDouble
import com.naeilo.app.core.putDouble
import com.naeilo.app.core.calc.MC
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException
import java.net.URL
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// 종목별 전망 입력값 (naeilo.com/data/stats.json). 사이트가 매일 web/model.js 와 같은 계산(지난 3년 종가, 사전값 수축)으로 만든다.
// 앱 전망은 이 값과 같이 들어 있는 기본 사건·요인 민감도·설정으로 웹과 같은 몬테카를로를 돌린다 (MonteCarlo.kt, ForecastEngine.kt).
// 닫힌 식(AppModel.forecast)은 계산이 끝나기 전 잠깐 보여 주는 값과 주간 예보 폭에 쓴다. 받은 적이 없으면 빌드 때 넣은 파일

private fun nowSec(): Double = System.currentTimeMillis() / 1000.0

object SiteStats {   // ios: Data/SiteStats.swift:6
    // T·D·C·File 는 MC.Stats.Ticker·Defaults·Corr·MC.Stats 그대로 쓴다 (Kotlin 은 객체 안 typealias 가 없다)

    const val name = "site_stats.json"   // ios: Data/SiteStats.swift:12
    private fun decode(d: ByteArray?): MC.Stats? = d?.let { runCatching { NJson.decodeFromString(MC.Stats.serializer(), it.decodeToString()) }.getOrNull() }   // ios: Data/SiteStats.swift:13
    /** 공개 베타·출시는 받아 둔 사이트 값을 읽지 않고 번들 값(가상 샘플)만 */
    fun load(): MC.Stats? =   // ios: Data/SiteStats.swift:15
        (if (Config.usesCollectedData) decode(Store.readData(name)) else null) ?: decode(Defaults.asset("stats", "json")?.encodeToByteArray())   // ios: Data/SiteStats.swift:15

    /** 받은 지 minAge 초가 안 됐으면 받지 않는다 (사이트는 하루 한 번 만든다). 실패하면 null */
    suspend fun fetch(minAge: Double = 6 * 3600.0): MC.Stats? = withContext(Dispatchers.IO) {   // ios: Data/SiteStats.swift:19
        if (!Config.usesCollectedData) return@withContext null   // ios: Data/SiteStats.swift:21
        val key = "statsGot"; val now = nowSec()   // ios: Data/SiteStats.swift:22
        if (now - Store.defaults.getDouble(key) < minAge) return@withContext null   // ios: Data/SiteStats.swift:21
        val (data, f) = download(now) ?: return@withContext null
        Store.writeData(data, name)   // ios: Data/SiteStats.swift:28
        Store.defaults.edit().putDouble(key, now).apply()   // ios: Data/SiteStats.swift:29
        f   // ios: Data/SiteStats.swift:30
    }
    /** 사이트 stats.json 받기 (저장하지 않는다. 시험에서도 부른다) */
    internal suspend fun download(now: Double = nowSec()): Pair<ByteArray, MC.Stats>? {
        val c = URL(Config.data, "stats.json").toString()   // ios: Data/SiteStats.swift:22
        val queryItems = listOf("t" to (now / 3600).toLong().toString()); val url = c + "?" + queryItems.joinToString("&") { "${it.first}=${it.second}" }   // ios: Data/SiteStats.swift:23
        val req = FeedReq(url, timeout = 10.0)   // ios: Data/SiteStats.swift:24
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: Data/SiteStats.swift:25
        val (code, data) = runCatching { req.send() }.getOrNull() ?: return null   // ios: Data/SiteStats.swift:26
        val f = if (code == 200) decode(data) else null; if (f == null) return null   // ios: Data/SiteStats.swift:27
        return data to f
    }

    /** 앱 종목 이름 → 사이트 티커 (stats.json·사건 대상과 같은 이름) */
    fun key(id: String): String {   // ios: Data/SiteStats.swift:34
        YahooSample.keyFor[id]?.let { k -> return k }   // ios: Data/SiteStats.swift:35
        return Sample.symbol(id)?.let { CustomSymbols.yahoo(it) } ?: id   // ios: Data/SiteStats.swift:36
    }

    /** 전망 계산용 사이트 티커: 한국 종목은 기준 미국 ETF 의 원화 환산 묶음 KR~SPY (환헤지면 KR~SPY~H, 직접 입력 종목은 KR~EWY).
     *  tools/site_stats.mjs 가 만들고, 웹 synthKR 의 종목 시세와 같은 수익률이다 (KoreaEstimate) */
    fun mcKey(id: String): String {   // ios: Data/SiteStats.swift:41
        val c = CustomSymbols.shared.saved(id)   // ios: Data/SiteStats.swift:42
        if (c != null && !c.usd) {
            val i = KoreaEstimate.info(id = id, name = c.name)   // ios: Data/SiteStats.swift:43
            return "KR~" + i.base + (if (i.hedged) "~H" else "")   // ios: Data/SiteStats.swift:44
        }
        return key(id)   // ios: Data/SiteStats.swift:46
    }

    /** (현재 정세 연 기대, 과거 추세 연 기대, 연 변동성). 사이트 값 → 앱에 넣은 예시 표 → 웹이 모르는 종목에 쓰는 기본값 */
    fun params(id: String, f: MC.Stats? = Market.shared.stats, mk: (String) -> String = ::mcKey): Triple<Double, Double, Double> {   // ios: Data/SiteStats.swift:50
        val t = f?.tickers?.get(mk(id)); val mu = t?.mu; val vol = t?.vol; if (t != null && mu != null && vol != null) return Triple(mu, t.trend ?: mu, vol)   // ios: Data/SiteStats.swift:51
        Sample.lensParams[id]?.let { p -> return p }   // ios: Data/SiteStats.swift:52
        val d = f?.defaults ?: MC.Stats.Defaults(mu = 0.10, vol = 0.40, corr = 0.3)   // ios: Data/SiteStats.swift:53
        return Triple(d.mu, d.mu, d.vol)   // ios: Data/SiteStats.swift:54
    }
    /** 원/달러 환율 (기대, 변동성). 달러 종목의 원화 평가액에 더해지는 흔들림 */
    fun fx(f: MC.Stats? = Market.shared.stats): Pair<Double, Double> {   // ios: Data/SiteStats.swift:57
        val t = f?.tickers?.get("KRW=X")   // ios: Data/SiteStats.swift:58
        return Pair(t?.mu ?: 0.0, t?.vol ?: 0.10)   // ios: Data/SiteStats.swift:59
    }
    /** 두 종목(또는 "KRW=X")의 상관. 표에 없으면 웹과 같게 위험 종목끼리 기본 상관, 현금성·환율이 끼면 0 */
    fun corr(a: String, b: String, f: MC.Stats? = Market.shared.stats, k: (String) -> String = ::key): Double {   // ios: Data/SiteStats.swift:62
        if (a == b) return 1.0   // ios: Data/SiteStats.swift:63
        val ka = if (a == "KRW=X") a else k(a); val kb = if (b == "KRW=X") b else k(b)   // ios: Data/SiteStats.swift:64
        if (f != null) { val i = f.corr.keys.indexOf(ka); val j = f.corr.keys.indexOf(kb); if (i >= 0 && j >= 0) return f.corr.m[i][j] }   // ios: Data/SiteStats.swift:65
        val safe = { x: String -> x == "KRW=X" || f?.tickers?.get(x)?.cash == true }   // ios: Data/SiteStats.swift:66
        return if (safe(ka) || safe(kb)) 0.0 else f?.defaults?.corr ?: 0.3   // ios: Data/SiteStats.swift:67
    }
}

/** 비중 바꿔 비교에서 사이트 표(stats.json)에 없는 종목: 받은 3년 종가로 web/model.js buildModel 과 같은 계산(사전값 수축·상관)을 앱에서 한다.
 *  키는 앱 종목 이름 (미국 티커, 한국 6자리 코드). 종가는 기기에 저장해 하루 안에는 다시 받지 않는다 */
class ExtraStats(initial: Map<String, Item>? = null) {   // ios: Data/SiteStats.swift:73,74
    companion object {
        val shared: ExtraStats by lazy { ExtraStats() }   // ios: Data/SiteStats.swift:75
        private val ser = MapSerializer(String.serializer(), Item.serializer())
    }
    @Serializable data class Item(var name: String, var hist: YahooSample.Hist, @Serializable(with = SwiftDate::class) var at: Instant, var usd: Boolean? = null, var yahoo: String? = null)   // ios: Data/SiteStats.swift:76
    var items: Map<String, Item> by mutableStateOf(initial ?: (Store.readData(if (Config.usesCollectedData) "extra_stats.json" else "")?.let { runCatching { NJson.decodeFromString(ser, it.decodeToString()) }.getOrNull() }) ?: emptyMap())   // ios: Data/SiteStats.swift:77
        private set

    fun name(k: String): String? = items[k]?.name   // ios: Data/SiteStats.swift:79
    fun usd(k: String): Boolean? = items[k]?.let { it.usd ?: true }   // ios: Data/SiteStats.swift:80

    /** 3년 종가를 받는다 (하루 안에 받은 게 있으면 그대로) */
    suspend fun fetch(h: StockHit) {   // ios: Data/SiteStats.swift:83
        val it0 = items[h.id]; if (it0 != null && Instant.now().epochSecond - it0.at.epochSecond < 86400) return   // ios: Data/SiteStats.swift:84
        val (_, hs) = CustomSymbols.chart(h.yahoo, range = "3y")   // ios: Data/SiteStats.swift:85
        if (!(hs.closes.size > 5)) throw IOException("cannotParseResponse")   // ios: Data/SiteStats.swift:86
        synchronized(this) {   // ios: Data/SiteStats.swift:87
            items = items + (h.id to Item(name = h.name, hist = YahooSample.Hist(dates = hs.dates, closes = hs.closes), at = Instant.now(), usd = h.currency == Currency.usd, yahoo = h.yahoo))   // ios: Data/SiteStats.swift:88
            runCatching { NJson.encodeToString(ser, items) }.getOrNull()?.let { Store.writeData(it.encodeToByteArray(), "extra_stats.json") }   // ios: Data/SiteStats.swift:89
        }
    }

    /** 모의 바구니에 담은 종목은 하루 한 번 종가를 새로 받는다 (바구니 평가액이 이어지게) */
    suspend fun refresh(keys: Set<String>) {   // ios: Data/SiteStats.swift:94
        for (k in keys) {   // ios: Data/SiteStats.swift:95
            val it0 = items[k]; if (it0 == null || !(Instant.now().epochSecond - it0.at.epochSecond > 86400)) continue   // ios: Data/SiteStats.swift:96
            val hs = runCatching { CustomSymbols.chart(it0.yahoo ?: k, range = "3y") }.getOrNull()?.second?.takeIf { it.closes.size > 5 } ?: continue   // ios: Data/SiteStats.swift:97
            synchronized(this) {   // ios: Data/SiteStats.swift:98
                items[k]?.let { old -> items = items + (k to old.copy(hist = YahooSample.Hist(dates = hs.dates, closes = hs.closes), at = Instant.now())) }   // ios: Data/SiteStats.swift:99
                runCatching { NJson.encodeToString(ser, items) }.getOrNull()?.let { Store.writeData(it.encodeToByteArray(), "extra_stats.json") }   // ios: Data/SiteStats.swift:100
            }
        }
    }

    /** st 에 없는 keys 를 받은 종가로 계산해 더한 사본. 계산할 게 없으면 st 그대로 */
    fun augment(st: MC.Stats, keys: List<String>, prices: Map<String, YahooSample.Hist>? = Market.shared.prices?.h): MC.Stats {   // ios: Data/SiteStats.swift:106
        val add = keys.filter { st.tickers[it] == null && items[it] != null }   // ios: Data/SiteStats.swift:107
        if (add.isEmpty()) return st   // ios: Data/SiteStats.swift:108
        val TD = MC.TD; val m = st.model   // ios: Data/SiteStats.swift:109
        val prior = (m?.prior_mu ?: 10.0) / 100; val tau = 0.15; val newVol = 0.60; val defCorr = m?.default_corr ?: st.defaults.corr   // ios: Data/SiteStats.swift:110
        // 지난 3년 로그수익률 (날짜 → 값). 날짜 순서를 지킨다 (아이폰 사전은 순서가 없어 합의 끝자리가 실행마다 다를 수 있다)
        fun rets(h: YahooSample.Hist): Map<String, Double> {   // ios: Data/SiteStats.swift:112
            val last = h.dates.lastOrNull() ?: return emptyMap()   // ios: Data/SiteStats.swift:113
            val cut = MC.dayStr(MC.dayNum(last) - 1096)   // ios: Data/SiteStats.swift:114
            val out = LinkedHashMap<String, Double>(); var prev: Double? = null   // ios: Data/SiteStats.swift:115
            for ((d, v) in h.dates.zip(h.closes)) { if (!(d >= cut && v > 0)) continue   // ios: Data/SiteStats.swift:116
                prev?.let { p -> out[d] = ln(v / p) }   // ios: Data/SiteStats.swift:117
                prev = v   // ios: Data/SiteStats.swift:118
            }
            return out   // ios: Data/SiteStats.swift:120
        }
        fun mean(x: List<Double>): Double = if (x.isEmpty()) 0.0 else x.fold(0.0) { a, b -> a + b } / x.size   // ios: Data/SiteStats.swift:122
        fun sd(x: List<Double>): Double { val mu = mean(x); return sqrt(x.fold(0.0) { a, b -> a + (b - mu) * (b - mu) } / max(1, x.size - 1)) }   // ios: Data/SiteStats.swift:123
        val out = st.copy()   // ios: Data/SiteStats.swift:124
        val tickers = st.tickers.toMutableMap()
        val keysAll = st.corr.keys.toMutableList(); val M = st.corr.m.map { it.toMutableList() }.toMutableList()   // ios: Data/SiteStats.swift:125
        val cash = st.tickers.mapValues { it.value.cash == true || it.value.fx == true }.toMutableMap()   // ios: Data/SiteStats.swift:126
        val site = { k: String -> prices?.get(k)?.let { rets(it) } }   // ios: Data/SiteStats.swift:127
        for (k in add) {   // ios: Data/SiteStats.swift:128
            val it0 = items[k] ?: continue   // ios: Data/SiteStats.swift:129
            val rs = rets(it0.hist); val r = rs.values.toList(); val n = r.size   // ios: Data/SiteStats.swift:130
            val volRaw = if (n > 1) sd(r) * sqrt(TD) else null   // ios: Data/SiteStats.swift:131
            val isCash = volRaw?.let { n > 20 && it < 0.03 } ?: false   // ios: Data/SiteStats.swift:132
            var vol = st.defaults.vol   // ios: Data/SiteStats.swift:133
            if (volRaw != null && n >= 5) {   // ios: Data/SiteStats.swift:134
                vol = if (n < TD.toInt() && !isCash) { val w = n.toDouble() / (n.toDouble() + TD); sqrt(w * volRaw * volRaw + (1 - w) * newVol * newVol) } else volRaw   // ios: Data/SiteStats.swift:135
            }
            var mu = prior   // ios: Data/SiteStats.swift:137
            if (isCash) {   // ios: Data/SiteStats.swift:138
                val c = it0.hist.closes; val j = max(0, c.size - 253)   // ios: Data/SiteStats.swift:139
                mu = if (c.size > 1) (c[c.size - 1] / c[j]).pow(TD / max(1, c.size - 1 - j).toDouble()) - 1 else 0.04   // ios: Data/SiteStats.swift:140
            } else if (n >= 5 && volRaw != null) {   // ios: Data/SiteStats.swift:141
                val arith = mean(r) * TD + 0.5 * volRaw * volRaw; val se2 = vol * vol / (n.toDouble() / TD); val kk = tau * tau / (tau * tau + se2)   // ios: Data/SiteStats.swift:142
                mu = kk * arith + (1 - kk) * prior   // ios: Data/SiteStats.swift:143
            }
            // 과거 추세: 3년 로그 종가 직선 기울기 (연)
            var gs: Double? = null; var trend = mu   // ios: Data/SiteStats.swift:146
            if (!isCash && n >= 5) {   // ios: Data/SiteStats.swift:147
                val cut = MC.dayStr(MC.dayNum(it0.hist.dates.lastOrNull() ?: "") - 1096)   // ios: Data/SiteStats.swift:148
                val pts = it0.hist.dates.zip(it0.hist.closes).filter { it.first >= cut && it.second > 0 }.map { Pair(MC.dayNum(it.first).toDouble() / 365.25, ln(it.second)) }   // ios: Data/SiteStats.swift:149
                if (pts.size >= 20) {   // ios: Data/SiteStats.swift:150
                    val mt = mean(pts.map { it.first }); val my = mean(pts.map { it.second })   // ios: Data/SiteStats.swift:151
                    val sxy = pts.fold(0.0) { a, p -> a + (p.first - mt) * (p.second - my) }; val sxx = pts.fold(0.0) { a, p -> a + (p.first - mt) * (p.first - mt) }   // ios: Data/SiteStats.swift:152
                    gs = if (sxx > 0) sxy / sxx else 0.0   // ios: Data/SiteStats.swift:153
                }
                gs?.let { g -> val w = min(1.0, n.toDouble() / TD); trend = w * (exp(max(-0.9, min(1.2, g)) + 0.5 * vol * vol) - 1) + (1 - w) * mu }   // ios: Data/SiteStats.swift:155
            }
            tickers[k] = MC.Stats.Ticker(mu = mu, trend = trend, gs = gs, vol = vol, n = n, cash = if (isCash) true else null, fx = null)   // ios: Data/SiteStats.swift:157
            cash[k] = isCash   // ios: Data/SiteStats.swift:158
            // 상관: 표에 있는 종목과 겹치는 날로 (40일 미만이면 사전값, 1년 미만이면 사전값과 섞기)
            val row = ArrayList<Double>()   // ios: Data/SiteStats.swift:160
            for (o in keysAll) {   // ios: Data/SiteStats.swift:161
                val prior0 = if (isCash || cash[o] == true) 0.0 else defCorr   // ios: Data/SiteStats.swift:162
                var c = prior0   // ios: Data/SiteStats.swift:163
                val orr = site(o) ?: items[o]?.let { rets(it.hist) }   // ios: Data/SiteStats.swift:164
                if (orr != null) {
                    val a = ArrayList<Double>(); val b = ArrayList<Double>()   // ios: Data/SiteStats.swift:165
                    for ((d, x) in rs) { orr[d]?.let { y -> a.add(x); b.add(y) } }   // ios: Data/SiteStats.swift:166
                    if (a.size >= 40) {   // ios: Data/SiteStats.swift:167
                        val ma = mean(a); val mb = mean(b)   // ios: Data/SiteStats.swift:168
                        var sab = 0.0; var saa = 0.0; var sbb = 0.0   // ios: Data/SiteStats.swift:169
                        for (t in a.indices) { sab += (a[t] - ma) * (b[t] - mb); saa += (a[t] - ma) * (a[t] - ma); sbb += (b[t] - mb) * (b[t] - mb) }   // ios: Data/SiteStats.swift:170
                        c = if (saa > 0 && sbb > 0) sab / sqrt(saa * sbb) else 0.0   // ios: Data/SiteStats.swift:171
                        if (a.size.toDouble() < TD) { val w = a.size.toDouble() / (a.size.toDouble() + TD); c = w * c + (1 - w) * prior0 }   // ios: Data/SiteStats.swift:172
                    }
                }
                row.add(max(-0.95, min(0.95, c)))   // ios: Data/SiteStats.swift:175
            }
            for (i in M.indices) M[i].add(row[i])   // ios: Data/SiteStats.swift:177
            M.add((row + 1.0).toMutableList())   // ios: Data/SiteStats.swift:178
            keysAll.add(k)   // ios: Data/SiteStats.swift:179
        }
        out.tickers = tickers
        out.corr = MC.Stats.Corr(keys = keysAll, m = M)   // ios: Data/SiteStats.swift:181
        return out   // ios: Data/SiteStats.swift:182
    }
}
