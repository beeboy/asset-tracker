package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import com.naeilo.app.ui.analysis.ForecastView
import com.naeilo.app.ui.analysis.ScenarioChart
import com.naeilo.app.ui.analysis.manS
import com.naeilo.app.ui.analysis.won1
import com.naeilo.app.ui.analysis.wonRead
import com.naeilo.app.ui.analysis.myPathOf
import com.naeilo.app.core.calc.MC
import com.naeilo.app.core.calc.Route
import java.time.Instant
import kotlin.math.floor
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.naeilo.app.core.Store
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Symbol
import com.naeilo.app.core.calc.SiteCalc
import com.naeilo.app.net.Market
import com.naeilo.app.net.SitePrices
import com.naeilo.app.net.SiteStats
import com.naeilo.app.net.YahooSample
import com.naeilo.app.ui.analysis.DividendView
import com.naeilo.app.ui.analysis.ExternalView
import com.naeilo.app.ui.analysis.GlanceView
import com.naeilo.app.ui.analysis.MacroData
import com.naeilo.app.ui.analysis.RateScen
import com.naeilo.app.ui.analysis.RateScenarioView
import com.naeilo.app.ui.analysis.factorHuge
import com.naeilo.app.ui.analysis.factorLabel
import com.naeilo.app.ui.analysis.factorPresets
import com.naeilo.app.ui.analysis.factorRange
import com.naeilo.app.ui.analysis.factorRows
import com.naeilo.app.ui.analysis.factorText
import com.naeilo.app.ui.analysis.fxTicks
import com.naeilo.app.ui.analysis.lv
import com.naeilo.app.ui.analysis.monthInfo
import com.naeilo.app.ui.analysis.volWord
import com.naeilo.app.ui.analysis.ymDot

// 숫자 맞추기 G (분석): tools/num_parity/g/gsrc.py 가 꺼낸 Swift 와 같은 입력으로 kotlin.json 을 쓴다.
// 전망 금액 글·햇달·빈도·그래프 눈금, 읽기 쉬운 금액. 비교는 parity.py check (차이 0)
class GParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/g")

    @Suppress("UNCHECKED_CAST")
    @Test fun gStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity g: 입력 없음 (parity.py gen 먼저)"); return }
        val lz = AppModel::class.java.getDeclaredField("en\$delegate").apply { isAccessible = true }.get(null)
        lz.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(lz, false)
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val won = inp["won"] as List<Double>; val ps = inp["p"] as List<Double>
        val out = LinkedHashMap<String, Any?>()
        out["amt"] = won.map { ForecastView.amt(it) }
        out["short"] = listOf(Lens.base, Lens.mine, Lens.smooth).map { ForecastView.short(it) }
        out["ym"] = (inp["dates"] as List<String>).map { val x = ForecastView.ym(it); listOf(x.first, x.second) }
        out["step"] = won.map { ScenarioChart.step(abs(it)) }
        out["axis"] = won.map { ScenarioChart.axis(it) }
        out["manS"] = won.map { AppModel.manS(it) }
        out["won1"] = won.map { AppModel.won1(it) }
        out["wonRead"] = won.map { AppModel.wonRead(it) }
        // 내 길: 지난 평가액(pathSeries)·3년 흐름(totalAt)은 Swift 와 같은 입력을 준다
        out["myPath"] = (inp["path"] as List<Map<String, Any?>>).map { c ->
            val tot = c["tot"] as List<Double>
            val p = myPathOf(isGoal = c["isGoal"] as Boolean, goalSpan = c["goalSpan"] as Double, pathStartDate = Instant.ofEpochMilli(((c["start"] as Double) * 1000).toLong()),
                today = c["today"] as String, gM = c["gM"] as Double, keyValue = c["K"] as Double, trackValue = c["now"] as Double,
                bare = c["route"] == "novice", isSample = c["isSample"] as Boolean, krSum = c["krSum"] as Double,
                KL = (c["KL"] as List<List<Any?>>).map { MC.KrDay(it[0] as String, it[1] as Double) },
                pathSeries = { (c["S"] as List<List<Any?>>).map { Pair(it[0] as String, it[1] as Double) } },
                totalAt = { x -> tot[maxOf(0, minOf(tot.size - 1, floor(x * (tot.size - 1) + 0.5).toInt()))] })
            val ts = (c["ts"] as List<String>).map { it.toDouble() }
            val n = p.need(p.start)
            mapOf("start" to p.start, "end" to p.end, "key" to p.key, "monthly" to p.monthly, "krIn" to p.krIn,
                  "pts" to p.pts.map { listOf(it.first, it.second) }, "act" to ts.map { p.act(it) }, "need" to ts.map { p.need(it) },
                  "gap" to (if (n > 0) p.act(p.start) / n - 1 else 0.0))
        }
        out["ext"] = runX(inp["ext"] as Map<String, Any?>)
        out["rate"] = runR(inp["rate"] as Map<String, Any?>)
        out["div"] = runD(inp["div"] as Map<String, Any?>)
        out["fxg"] = runF(inp["fxg"] as Map<String, Any?>)
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity g: 금액 ${won.size}")
    }
    // 글자로 받는다 (Swift 쪽 JSONSerialization 이 소수 끝자리를 틀리게 읽어 양쪽 다 글자에서 바꾼다)
    private fun D(x: Any?): Double = (x as String).toDouble()
    @Suppress("UNCHECKED_CAST") private fun DS(x: Any?): List<Double> = (x as List<String>).map { it.toDouble() }
    private fun ON(x: Any?): Double? = (x as String?)?.toDouble()

    /** 외부 요인·요인 상세: 시세(Market)·사이트 통계는 대역 Context 로 띄운 앱 싱글턴에 넣는다 (Swift 는 같은 이름 대역) */
    @Suppress("UNCHECKED_CAST")
    private fun runX(X: Map<String, Any?>): Map<String, Any?> {
        val ctx = FakeCtx.make(); Store.init(ctx); Defaults.init(ctx)
        val mk = Market.shared
        val hist = X["hist"] as Map<String, Map<String, Any?>>
        mk.prices = SitePrices.File(got = 0.0, updated = "", asked = emptyList(),
            h = hist.entries.associate { (id, h) -> SiteStats.key(id) to YahooSample.Hist(h["dates"] as List<String>, DS(h["closes"])) })
        val syms = (X["syms"] as List<List<String>>).associate { it[0] to Symbol(id = it[0], name = it[1], market = "", close = 0.0, currency = if (it[2] == "usd") Currency.usd else Currency.krw, sector = "", search = "") }
        val ids = X["ids"] as List<String>; val shocks = X["shocks"] as Map<String, List<String>>
        val facs = listOf("mkt", "fx", "rate", "oil", "gold", "cmdty")
        val o = LinkedHashMap<String, Any?>()
        o["cases"] = (X["cases"] as List<Map<String, Any?>>).map { c ->
            mk.stats = MC.Stats(date = "", defaults = MC.Stats.Defaults(0.0, 0.0, 0.0), tickers = emptyMap(), corr = MC.Stats.Corr(emptyList(), emptyList()),
                betas = (c["betas"] as Map<String, Map<String, String>>).mapValues { e -> e.value.entries.associate { (id, b) -> SiteStats.mcKey(id) to b.toDouble() } },
                betaStat = (c["stat"] as Map<String, Map<String, String>>).mapValues { e -> e.value.entries.associate { (id, a) -> SiteStats.mcKey(id) to MC.BetaStat(agree = a.toDouble()) } },
                factors = (c["factors"] as Map<String, List<String>>).mapValues { MC.FactorMove(sd = it.value[0].toDouble(), max = it.value[1].toDouble()) })
            val rows = (c["rows"] as List<List<String>>).map { AppModel.Row(id = it[0], sym = syms.getValue(it[0]), h = Holding(it[0], 0.0, 0.0), value = it[1].toDouble(), cost = 0.0, color = Color.Unspecified) }
            val total = rows.fold(0.0) { a, r -> a + r.value }
            val r = LinkedHashMap<String, Any?>()
            r["extBeta"] = ids.map { id -> val k = ExternalView.coef(syms.getValue(id)); listOf(ExternalView.estBeta(id), ExternalView.mktBeta(id), k.first, k.second, k.third) }
            r["extMove"] = facs.map { f -> ids.map { id -> DS(shocks[f] ?: shocks["etc"]).map { ExternalView.move(f, id, syms.getValue(id), it) } } }
            r["extShock"] = facs.map { ExternalView.monthShock(it) }
            val rs = ExternalView.ranks(rows, total)
            r["extRanks"] = rs.map { listOf(it.k, it.amt, it.gross, it.weak, ExternalView.desc(rows, total, it)) }
            r["summary"] = ExternalView.summary(rs)
            r["fd"] = facs.mapIndexed { i, f ->
                val vs = DS(shocks[f] ?: shocks["etc"]) + DS(X["texts"])
                fun sign(shock: Double?) = if ((shock ?: factorPresets(f)[1].second) >= 0) 1.0 else -1.0   // FactorDetailView 의 shockSign 과 같은 식
                val s1 = sign(if (i % 2 == 0) null else vs[0]); val s2 = sign(vs[2])
                val rg = factorRange(f)
                mapOf("range" to listOf(rg.start, rg.endInclusive), "text" to vs.map { factorText(f, it) }, "huge" to vs.map { factorHuge(f, it) },
                    "label" to listOf(Triple(0.0, false, true), Triple(-5.0, false, true), Triple(7.0, false, true), Triple(3.0, true, true), Triple(0.0, false, false), Triple(-2.0, false, true))
                        .map { factorLabel(f, it.first, it.second, it.third, s1) },
                    "label2" to listOf(1.0, -1.0, 0.0).map { factorLabel(f, it, false, true, s2) },
                    "extRows" to factorRows(rows, f, vs[1], maxOf(1.0, total)).map { listOf(it.id, it.label, it.dv, it.agree, it.weak, it.known) })
            }
            r
        }
        o["fx"] = mk.fx.last
        o["summary"] = (X["rankLists"] as List<List<List<Any?>>>).map { l -> ExternalView.summary(l.map { ExternalView.Rank(k = it[0] as String, amt = D(it[1]), gross = 0.0, weak = it[2] as Boolean) }) }
        o["presets"] = (X["change1y"] as List<Any?>).map { ch ->
            MacroData.shared.file = MacroData.File(now = MacroData.File.Now(change_1y = ON(ch)))
            (factorPresets("rate") + factorPresets("fx")).map { listOf(it.first, it.second) } + listOf(listOf(MacroData.shared.change1y, MacroData.shared.defaultDr))
        }
        return o
    }

    private fun scenOut(r: RateScen?): Any? = r?.let { mapOf("years" to it.years, "yr" to it.yr, "cum" to it.cum, "dropYr" to it.dropYr, "dropCum" to it.dropCum,
        "dropCumYr" to it.dropCumYr, "slope" to it.slope, "rateMkt" to it.mkt) }

    @Suppress("UNCHECKED_CAST")
    private fun runR(R: Map<String, Any?>): Map<String, Any?> {
        val o = LinkedHashMap<String, Any?>()
        val qs = R["q"] as List<List<Any?>>
        o["q"] = qs.map { RateScen.q(DS(it[0]), D(it[1])) }
        o["band"] = qs.map { RateScen.band(DS(it[0])) }
        o["files"] = (R["files"] as List<Map<String, Any?>>).map { f ->
            val coef = (f["coef"] as List<List<List<String>>>).map { a -> a.map { b -> b.map { it.toDouble() } } }
            val m = MacroData()
            m.file = MacroData.File(now = MacroData.File.Now(change_1y = ON(f["ch"])), eps_last = f["eps"] as String?,
                meals = if (f.containsKey("noMeals")) null else MacroData.File.Meals(g_now = D(f["g"]), coef = coef))
            mapOf("dr" to m.defaultDr, "ch" to m.change1y, "scen" to (R["calls"] as List<List<String>>).map { scenOut(m.scen(dr = it[0].toDouble(), m = it[1].toDouble())) })
        }
        o["closed"] = (R["closed"] as List<Map<String, Any?>>).map { c ->
            val v = DS(c["c"]); val ci = RateScenarioView.Closed(T = v[0], sg = v[1], mu = v[2], V0 = v[3], K = v[4], M = v[5]); val sh = DS(c["s"])
            val f = AppModel.Forecast(q5 = emptyList(), q25 = emptyList(), q50 = emptyList(), q75 = emptyList(), q95 = emptyList(), dates = emptyList(), tY = emptyList(),
                prob = { 0.0 }, pGoal = D(c["p"]), byYear = emptyList(), mu = 0.0, sigma = 0.0, muBase = 0.0, muTrend = 0.0, exact = true)
            mapOf("rateClosed" to sh.map { RateScenarioView.closed(ci, it) }, "rateProb" to sh.map { RateScenarioView.prob(f, ci, it) },
                "months" to sh.map { RateScenarioView.months(ci, it) ?: -1 }, "timing" to sh.map { RateScenarioView.timing(RateScenarioView.months(ci, 0.0), RateScenarioView.months(ci, it)) })
        }
        o["timing"] = (R["timing"] as List<List<Number>>).map { val a = it[0].toInt(); val b = it[1].toInt(); RateScenarioView.timing(if (a < 0) null else a, if (b < 0) null else b) }
        o["ym"] = (R["ym"] as List<String>).map { listOf(RateScenarioView.ym(it), ymDot(it), DividendView.ym(it)) }
        o["signed"] = DS(R["nums"]).map { v -> listOf(RateScenarioView.signed(v, "%p", 1), RateScenarioView.signed(v, "%", 0), RateScenarioView.signed(v, "%", 2), lv(v),
            DividendView.short(abs(v) * 1e4), DividendView.short(abs(v) * 1e3)) }
        return o
    }

    @Suppress("UNCHECKED_CAST")
    private fun runD(Dv: Map<String, Any?>): List<Any?> = (Dv["cases"] as List<Map<String, Any?>>).map { c ->
        val paid = (c["paid"] as List<Map<String, Any?>>).map { p ->
            DividendView.Paid(id = p["id"] as String, name = "", out = SiteCalc.DivOut(n = 0, y1 = 0.0, y3 = 0.0, gr = 0.0, yld = 0.0, byMonth = (p["by"] as Map<String, String>).mapValues { it.value.toDouble() }),
                tax = 0.0, krw = false, payMonths = (p["pm"] as List<Number>).map { it.toInt() })
        }
        val x = monthInfo(c["months"] as List<String>, DS(c["by"]), paid)
        listOf(x.mMax, x.hi.sorted(), x.years.map { listOf(it.first, it.second) }, x.label)
    }

    @Suppress("UNCHECKED_CAST")
    private fun runF(F: Map<String, Any?>): Map<String, Any?> = mapOf(
        "ticks" to (F["ticks"] as List<List<String>>).map { fxTicks(it[0].toDouble(), it[1].toDouble()) },
        "vol" to DS(F["vol"]).map { listOf(volWord(it), GlanceView.swing(it), GlanceView.swingName[GlanceView.swing(it)]) })
}

/** 시험용 SharedPreferences (메모리) */
private class MemPrefs : SharedPreferences {
    val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = m
    override fun getString(k: String?, d: String?) = m[k] as? String ?: d
    @Suppress("UNCHECKED_CAST") override fun getStringSet(k: String?, d: MutableSet<String>?) = m[k] as? MutableSet<String> ?: d
    override fun getInt(k: String?, d: Int) = m[k] as? Int ?: d
    override fun getLong(k: String?, d: Long) = m[k] as? Long ?: d
    override fun getFloat(k: String?, d: Float) = m[k] as? Float ?: d
    override fun getBoolean(k: String?, d: Boolean) = m[k] as? Boolean ?: d
    override fun contains(k: String?) = m.containsKey(k)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(k: String?, v: String?) = apply { m[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { m[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { m[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { m[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { m[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { m[k!!] = v }
        override fun remove(k: String?) = apply { m.remove(k) }
        override fun clear() = apply { m.clear() }
        override fun commit() = true
        override fun apply() {}
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
private val memPrefs = MemPrefs()
private val fakeDir: java.io.File = kotlin.io.path.createTempDirectory("naeilo-g").toFile().apply { deleteOnExit() }

/** 시험용 Context: 안드로이드 몸통 없이 Store·Defaults 만 띄운다 (생성자는 건너뛰고 만든다. 파일은 임시 폴더, 앱 자산은 없음) */
private class FakeCtx : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): java.io.File = fakeDir
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = memPrefs
    override fun getAssets(): android.content.res.AssetManager = throw java.io.IOException("no assets")
    companion object {
        fun make(): FakeCtx {
            val uc = Class.forName("sun.misc.Unsafe"); val u = uc.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            return uc.getMethod("allocateInstance", Class::class.java).invoke(u, FakeCtx::class.java) as FakeCtx
        }
    }
}
