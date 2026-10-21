package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.core.calc.MC
import com.naeilo.app.core.calc.PathSet
import com.naeilo.app.core.calc.SiteCalc
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import com.naeilo.app.data.Currency
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.ExtraStats
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.KrxList
import com.naeilo.app.net.MarFeed
import com.naeilo.app.net.SitePrices
import com.naeilo.app.net.SiteStats
import com.naeilo.app.net.StockCatalog
import com.naeilo.app.net.StockHit
import com.naeilo.app.net.StockSearch
import com.naeilo.app.net.Sync
import com.naeilo.app.net.YahooSample
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import kotlin.test.Test

// 숫자 맞추기 (Kotlin 쪽): tools/num_parity/parity.py gen 이 만든 입력으로 계산 엔진을 돌려 kotlin.json 들을 쓴다.
// Swift 쪽 ios-app/tools/mc_parity/main.swift · calc_parity/main.swift · tools/num_parity/sc/main.swift 와 같은 순서·같은 식.
// 비교는 parity.py check (차이 0). 입력이 없으면(gen 전) 아무것도 하지 않는다
class NumParityTest {
    private val work = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity")

    private fun nums(a: DoubleArray) = buildJsonArray { a.forEach { add(JsonPrimitive(it)) } }
    private fun nums(a: List<Double>) = buildJsonArray { a.forEach { add(JsonPrimitive(it)) } }
    private fun r(x: MC.Result): JsonObject = buildJsonObject {
        put("p_goal", x.pGoal); put("p_touch", x.pTouch); put("mdd_median", x.mddMedian)
        val t = x.terminal
        put("terminal", buildJsonObject { put("p5", t.p5); put("p25", t.p25); put("p50", t.p50); put("p75", t.p75); put("p95", t.p95); put("mean", t.mean) })
        put("bands", buildJsonObject { listOf("p5", "p25", "p50", "p75", "p95").forEachIndexed { i, k -> put(k, nums(x.bands[i])) } })
        put("byYear", buildJsonArray { x.byYear.forEach { y -> add(buildJsonObject { put("year", y.year); put("date", y.date); put("p", y.p) }) } })
    }

    @Test fun monteCarlo() {
        val dir = work.resolve("mc"); val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = NJson.parseToJsonElement(inF.readText()).jsonObject
        val stats = NJson.decodeFromJsonElement(MC.Stats.serializer(), inp["stats"]!!)
        val start = inp["start"]!!.jsonPrimitive.content
        val settings = stats.model ?: MC.Settings()
        val cases = buildJsonArray {
            for (ce in inp["cases"]!!.jsonArray) {
                val c = ce.jsonObject
                val name = c["name"]!!.jsonPrimitive.content
                val hs = c["holdings"]!!.jsonArray.map { h -> h.jsonObject.let {
                    MC.Holding(key = it["ticker"]!!.jsonPrimitive.content, ccy = it["ccy"]!!.jsonPrimitive.content,
                        valueKrw = it["valueKrw"]!!.jsonPrimitive.double, price0 = it["price0"]!!.jsonPrimitive.double) } }
                val events = NJson.decodeFromJsonElement(ListSerializer(MC.Event.serializer()), c["events"]!!)
                val goal = c["goal"]!!.jsonPrimitive.double; val goalDate = c["goalDate"]!!.jsonPrimitive.content
                val monthly = c["monthly"]!!.jsonPrimitive.double; val nPaths = c["nPaths"]!!.jsonPrimitive.int; val trust = c["trust"]!!.jsonPrimitive.double
                val model = MC.build(holdings = hs, stats = stats, settings = settings, events = events, start = start, goal = goalDate)
                    ?: error("모형을 못 만듦: $name")
                val mu = MC.scenarioMu(model, settings.scenario)
                val t0 = System.nanoTime()
                val ev = MC.simulate(model, holdings = hs, mu = mu, nPaths = nPaths, seed = settings.seedU32, goal = goal, monthly = monthly, withEvents = true, dof = settings.dof)
                val no = MC.simulate(model, holdings = hs, mu = mu, nPaths = nPaths, seed = settings.seedU32, goal = goal, monthly = monthly, withEvents = false, dof = settings.dof)
                val simMs = (System.nanoTime() - t0) / 1_000_000
                val views = buildJsonObject {
                    for ((nm, s) in listOf("ev" to ev, "no" to no)) for (key in listOf("base", "smooth", trust.toInt().toString())) {
                        val m = MC.viewMu(model, A = hs.size, lens = key, trust = trust)
                        put("$nm:$key", r(MC.reweight(s, mu = m, goal = goal, monthly = monthly)))
                    }
                }
                val muT = MC.viewMu(model, A = hs.size, lens = trust.toInt().toString(), trust = trust)
                var V0 = 0.0; for (x in ev.v0) V0 += x
                val ps = PathSet(sim = ev, mu = muT, base = ev.v0, key = name)
                val vals = ps.values(V0, monthly = monthly); val M = ev.M; val P = ev.P
                val bands = MC.bandsOf(vals, M = M, P = P)
                val term = vals.copyOfRange((M - 1) * P, vals.size).also { it.sort() }
                val hit = IntArray(P) { -1 }
                for (p in 0 until P) { for (k in 1 until M) if (vals[k * P + p] >= goal) { hit[p] = k; break } }
                val cum = ArrayList<Double>(); var run = 0
                for (k in 0 until M) { run += hit.count { it == k }; cum.add(run.toDouble() / P.toDouble()) }
                val by = ArrayList<MC.Year>(); var y = 1
                while (true) { val k = MC.monthK(ev, months = 12 * y); by.add(MC.Year(year = y, date = ev.monthDates[k], p = cum[k], k = k)); if (k >= M - 1) break; y += 1 }
                val mdd = DoubleArray(P)
                for (p in 0 until P) { var peak = V0; var d = 0.0; for (k in 0 until M) { val v = vals[k * P + p]; if (v > peak) peak = v else d = minOf(d, v / peak - 1) }; mdd[p] = d }
                mdd.sort()
                val pr = MC.Result(V0 = V0, bands = bands, pGoal = ps.share(vals, k = M - 1, atLeast = goal), pTouch = hit.count { it >= 0 }.toDouble() / P.toDouble(),
                    terminal = MC.terminal(term), mddMedian = MC.quantileSorted(mdd, 0.5), byYear = by, cumHit = cum)
                fun opt(a: List<Double?>) = buildJsonArray { a.forEach { add(if (it == null) JsonNull else JsonPrimitive(it)) } }
                val bep = buildJsonObject {
                    put("all", opt(MC.breakeven(ev, idx = (0 until hs.size).toList(), basis = V0 * 1.15, mu = muT, months = listOf(6, 12, 36))))
                    put("first", opt(MC.breakeven(ev, idx = listOf(0), basis = hs[0].valueKrw * 1.1, mu = muT, months = listOf(6, 12, 36))))
                    put("req50", ps.req50(V0, goal = goal, kEnd = M - 1))
                }
                add(buildJsonObject {
                    put("name", name); put("nPaths", nPaths); put("trust", trust.toInt())
                    put("model", buildJsonObject {
                        put("days", model.days.size); put("events", model.eventCount)
                        put("monthDates", buildJsonArray { model.monthDates.forEach { add(JsonPrimitive(it)) } })
                        put("factors", buildJsonArray { model.factors.forEach { f -> add(buildJsonObject {
                            put("key", f.key); put("vol", f.vol); put("base", f.base); put("smooth", f.smooth); put("blend", f.blend); put("evVar", f.evVar) }) } })
                    })
                    put("sim", r(ev.summary)); put("simNo", r(no.summary)); put("views", views); put("bep", bep); put("paths", r(pr))
                })
                println("Kotlin $name: 경로 $nPaths · ${model.days.size}거래일 · 사건 ${model.eventCount}회 · 시뮬레이션 2번 ${simMs}ms")
            }
        }
        val kr = inp["kr"]!!.jsonObject
        val kn = MC.krNorm(NJson.decodeFromJsonElement(ListSerializer(MC.KrDay.serializer()), kr["log"]!!))
        val out = buildJsonObject {
            put("cases", cases)
            put("kr", buildJsonObject {
                put("norm", NJson.encodeToJsonElement(ListSerializer(MC.KrDay.serializer()), kn))
                put("at", nums(kr["dates"]!!.jsonArray.map { MC.krAt(kn, it.jsonPrimitive.content) }))
            })
        }
        dir.resolve("kotlin.json").writeText(out.toString())
    }

    @Test fun siteCalc() {
        val dir = work.resolve("calc"); val inF = dir.resolve("input.json"); val appF = dir.resolve("app.json")
        if (!inF.exists() || !appF.exists()) { println("num_parity: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = NJson.parseToJsonElement(inF.readText()).jsonObject
        val app = NJson.parseToJsonElement(appF.readText()).jsonObject
        val today = inp["today"]!!.jsonPrimitive.content
        fun byName(list: JsonElement?, name: String) = list!!.jsonArray.first { it.jsonObject["name"]!!.jsonPrimitive.content == name }.jsonObject
        val out = buildJsonObject {
            put("consts", buildJsonObject { put("WHT", SiteCalc.wht); put("CGT", SiteCalc.cgt); put("CGT_DED", SiteCalc.cgtDed) })
            // 배당: 환율(fx)은 Market 쪽 값이라 Swift 결과의 것을 받아 SiteCalc.project 만 돌린다
            put("divs", buildJsonArray {
                for (de in inp["divs"]!!.jsonArray) {
                    val d = de.jsonObject; val name = d["name"]!!.jsonPrimitive.content
                    val evs = NJson.decodeFromJsonElement(ListSerializer(SiteCalc.Ev.serializer()), d["events"]!!)
                    val fx = byName(app["divs"], name)["fx"]!!.jsonPrimitive.double
                    val o = SiteCalc.project(evs, shares = d["shares"]!!.jsonPrimitive.double, fx = fx, krw = d["ccy"]!!.jsonPrimitive.content == "KRW",
                        price = d["price"]!!.jsonPrimitive.double, today = today)
                    add(buildJsonObject {
                        put("name", name)
                        put("out", if (o == null) JsonNull else buildJsonObject {
                            put("n", o.n); put("y1", o.y1); put("y3", o.y3); put("gr", o.gr); put("yld", o.yld)
                            put("byMonth", buildJsonObject { o.byMonth.forEach { (k, v) -> put(k, v) } })
                        })
                    })
                }
            })
            // 세금: 평가 이익(gain)은 화면 쪽 값이라 Swift 결과의 것을 받는다
            put("tax", buildJsonArray {
                for (te in app["tax"]!!.jsonArray) { val t = te.jsonObject
                    add(buildJsonObject { put("name", t["name"]!!.jsonPrimitive.content); put("tax", SiteCalc.capGainTax(0 + t["gain"]!!.jsonPrimitive.double)) }) }
            })
        }
        dir.resolve("kotlin.json").writeText(out.toString())

        // tools/num_parity/sc/main.swift 와 같은 고정 입력
        val sc = buildJsonObject {
            put("consts", buildJsonObject { put("wht", SiteCalc.wht); put("cgt", SiteCalc.cgt); put("cgtDed", SiteCalc.cgtDed); put("krDivTax", SiteCalc.krDivTax) })
            put("addMonths", buildJsonArray { for (d in listOf("2024-01-31", "2024-02-29", "2023-12-15", "2026-10-21", "2025-03-31")) for (n in listOf(-36, -24, -13, -12, -1, 0, 1, 11, 12, 25)) add(JsonPrimitive(SiteCalc.addMonths(d, n))) })
            val evs = (0 until 16).map { i -> SiteCalc.Ev(d = SiteCalc.addMonths("2022-11-15", 3 * i), a = 0.21 + 0.013 * i.toDouble()) }
            put("growth", nums(listOf("2026-10-21", "2025-12-31", "2024-06-01", "2023-01-01").map { SiteCalc.divGrowth(evs, today = it) }))
            put("factorMove", nums(listOf("mkt", "rate", "oil", "gold", "fx").flatMap { f -> listOf(-1.3, -0.2, 0.0, 0.7, 1.9).flatMap { b ->
                listOf(-30.0, -5.0, 0.0, 0.25, 12.0, 40.0).map { s -> SiteCalc.factorMove(f, beta = b, shock = s) } } }))
            val betas = mapOf("mkt" to 1.27, "rate" to -0.031, "oil" to 0.12)
            put("replay", buildJsonArray { for (rp in SiteCalc.replays) for (usd in listOf(true, false)) {
                val a = SiteCalc.replayMove(rp, usd = usd, beta = { betas[it] }); val b = SiteCalc.replayMove(rp, usd = usd, beta = { null })
                add(nums(listOf(a.total, a.fx, b.total, b.fx)))
            } })
            put("capGainTax", nums(listOf(-1e6, 0.0, 2_499_999.0, 2_500_000.0, 2_500_001.0, 13_370_000.5, 9.9e9).map { SiteCalc.capGainTax(it) }))
            put("fxAt", nums(listOf(0.0, 0.25, 1.0, 3.5).flatMap { t -> listOf(-1.645, -0.674, 0.0, 0.674, 1.645).map { z -> SiteCalc.fxAt(now = 1391.5, t = t, z = z, mu = 0.012, vol = 0.093) } }))
        }
        dir.resolve("kotlin_sc.json").writeText(sc.toString())
    }

    // D 단계 (시세·동기화): tools/num_parity/d/main.swift 와 같은 순서·같은 식으로 d/kotlin.json 을 쓴다
    @Suppress("UNCHECKED_CAST")
    @Test fun dStage() {
        val dir = work.resolve("d"); val inF = dir.resolve("input.json"); val appF = dir.resolve("app.json")
        if (!inF.exists() || !appF.exists()) { println("num_parity d: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val app = jsonToPlain(NJson.parseToJsonElement(appF.readText())) as Map<String, Any?>
        val out = LinkedHashMap<String, Any?>()
        fun strs(v: Any?) = (v as List<*>).map { it as String }
        fun hist(v: Any?): YahooSample.Hist = NJson.decodeFromJsonElement(YahooSample.Hist.serializer(), plainToJson(v))

        // 1. 비밀번호 → 저장 위치·키, AES-GCM (같은 키·iv). back = 아이폰이 만든 암호문을 Kotlin 이 푼 글자
        out["derive"] = strs(inp["pws"]).map { pw -> val c = Sync.derive(pw); mapOf("id" to c.id, "k" to c.k) }
        val key = java.util.Base64.getDecoder().decode(Sync.derive(inp["aesPw"] as String).k)
        val iv = (inp["aesIv"] as List<*>).map { (it as Double).toInt().toByte() }.toByteArray()
        val swiftAes = app["aes"] as List<Map<String, Any?>>
        out["aes"] = strs(inp["plains"]).mapIndexed { i, p ->
            val (v, ct) = Sync.seal(p.encodeToByteArray(), key, iv)
            val b = java.util.Base64.getEncoder()
            val parts = (swiftAes[i]["c"] as String).split(".")
            val back = Sync.open(java.util.Base64.getDecoder().decode(parts[0]), java.util.Base64.getDecoder().decode(parts[1]), key).decodeToString()
            mapOf("c" to b.encodeToString(v) + "." + b.encodeToString(ct), "back" to back)
        }

        // 2. 전망 입력값
        val stats = NJson.decodeFromJsonElement(MC.Stats.serializer(), plainToJson(inp["stats"]))
        val id = { x: String -> x }
        out["params"] = strs(inp["ids"]).map { i -> val a = SiteStats.params(i, stats, id); val b = SiteStats.params(i, null, id); listOf(a.first, a.second, a.third, b.first, b.second, b.third) }
        out["fx"] = listOf(SiteStats.fx(stats).first, SiteStats.fx(stats).second, SiteStats.fx(null).first, SiteStats.fx(null).second)
        out["corr"] = (inp["pairs"] as List<*>).map { p -> val q = strs(p); listOf(SiteStats.corr(q[0], q[1], stats, id), SiteStats.corr(q[0], q[1], null, id)) }

        // 3. 표 밖 종목 계산
        val sp = (inp["sitePrices"] as Map<String, Any?>).mapValues { hist(it.value) }
        val items = NJson.decodeFromJsonElement(kotlinx.serialization.builtins.MapSerializer(String.serializer(), ExtraStats.Item.serializer()), plainToJson(inp["items"]))
        val aug = ExtraStats(items).augment(stats, strs(inp["augKeys"]), sp)
        val n0 = stats.corr.keys.size; val n1 = aug.corr.keys.size
        out["augment"] = mapOf(
            "tickers" to aug.tickers.filterKeys { stats.tickers[it] == null }.mapValues { (_, t) -> mapOf("mu" to t.mu, "trend" to t.trend, "gs" to t.gs, "vol" to t.vol, "n" to t.n, "cash" to t.cash) },
            "keys" to aug.corr.keys, "m" to aug.corr.m.takeLast(n1 - n0), "same" to aug.corr.m.take(n0).map { it.takeLast(n1 - n0) })

        // 4. 한국 종목
        fun infoJ(i: KoreaEstimate.Info): MutableMap<String, Any?> = mutableMapOf("proxy" to i.proxy, "hedged" to i.hedged, "base" to i.base)
        out["info"] = (inp["names"] as List<*>).map { infoJ(KoreaEstimate.info(strs(it))) }
        out["infoId"] = (inp["idsInfo"] as List<*>).map { a -> val x = strs(a); infoJ(KoreaEstimate.info(id = x[0], name = x[1], en = x[2])).also { it["liveOK"] = KoreaEstimate.liveOK(x[0], name = x[1]) } }
        out["synth"] = (inp["synth"] as List<*>).map { a0 -> val a = a0 as List<*>
            KoreaEstimate.synth(price = a[0] as Double, date = a[1] as String, info = KoreaEstimate.Info(proxy = a[2] as String?, hedged = a[3] as Boolean), P = sp)?.let { mapOf("dates" to it.dates, "closes" to it.closes) } }

        // 5. 종목 찾기: 아이폰과 같은 예시 종목·전체 상장 목록
        val krx = NJson.decodeFromString<List<List<String>>>(java.io.File(inp["krx"] as String).readText()).mapNotNull { KrxList.parse(it) }
        val krxById = LinkedHashMap<String, StockHit>().also { m -> krx.forEach { m.putIfAbsent(it.hit.id, it.hit) } }
        val syms = (inp["syms"] as List<*>).map { r -> val x = strs(r); val cur = if (x[3] == "usd") Currency.usd else Currency.krw
            // CustomSymbols.yahoo(for:) 와 같은 차례 (추가한 종목 없음)
            val y = StockCatalog.all.firstOrNull { it.id == x[0] }?.yahoo ?: krxById[x[0]]?.yahoo ?: if (cur == Currency.krw) x[0] + ".KS" else x[0]
            "${x[0]}${x[1]}${x[4]}" to StockHit(id = x[0], yahoo = y, name = x[1], market = x[2], currency = cur) }
        out["symYahoo"] = syms.map { it.second.yahoo }
        out["local"] = strs(inp["queries"]).map { q -> StockSearch.local(q, syms, emptySet(), krx).map { "${it.id}|${it.yahoo}|${it.name}|${it.market}" } }
        out["krxCount"] = krx.size

        // 6. 매매기준율 표, 사이트 지난 종가, Yahoo 차트
        out["smbs"] = strs(inp["smbs"]).map { t -> MarFeed.smbsParse(t, got = inp["smbsGot"] as Double)?.let { f -> mapOf("rate" to f.rate, "date" to f.date, "got" to f.got, "prev" to f.prev?.let { mapOf("rate" to it.rate, "date" to it.date) }) } }
        out["hist"] = (inp["hists"] as List<*>).map { o -> SitePrices.hist(o as Map<String, Any?>)?.let { mapOf("dates" to it.dates, "closes" to it.closes) } }
        val now = java.time.Instant.ofEpochMilli(((inp["now"] as Double) * 1000).toLong())
        out["chart"] = (inp["charts"] as List<*>).map { a0 -> val a = a0 as List<*>
            try { val (q, h) = CustomSymbols.chartParse(a[0] as Map<String, Any?>, a[1] as Boolean, now); mapOf("last" to q.last, "prev" to q.prev, "dates" to h.dates, "closes" to h.closes) }
            catch (e: java.io.IOException) { mapOf("err" to true) } }

        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity d: 비밀번호 ${(out["derive"] as List<*>).size} · 찾기 ${(out["local"] as List<*>).size} · 상장 ${krx.size}")
    }
}
