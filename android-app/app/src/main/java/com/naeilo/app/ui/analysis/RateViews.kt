package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cformat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.calc.SiteCalc
import com.naeilo.app.core.calc.keyName
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.net.Market
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.data.Notifier
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.Spinner
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.ToggleRow
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 금리 시나리오 (Views/AnalysisDetail.swift 1329-1819): 금리→이익 관계식 식단 1000벌 (naeilo.com 외부 요인 › 금융·통화와 같은 계산)

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

/** data/macro/rates_earnings.json (매달 5일 갱신, 출처 Robert J. Shiller). 받은 파일은 저장해 두고 다음에 먼저 쓴다 */
// ios: Views/AnalysisDetail.swift:1222,1223
class MacroData {
    // ios: Views/AnalysisDetail.swift:1225
    @Serializable class File(
        val now: Now? = null,   // ios: Views/AnalysisDetail.swift:1228
        val eps_last: String? = null,   // ios: Views/AnalysisDetail.swift:1229
        val meals: Meals? = null,   // ios: Views/AnalysisDetail.swift:1230
    ) {
        @Serializable class Now(val date: String? = null, val rate: Double? = null, val change_1y: Double? = null)   // ios: Views/AnalysisDetail.swift:1226
        @Serializable class Meals(val g_now: Double, val coef: List<List<List<Double>>>)   // ios: Views/AnalysisDetail.swift:1227
    }
    var file: File? by mutableStateOf<File?>(null)   // ios: Views/AnalysisDetail.swift:1232
    var failed by mutableStateOf(false)   // ios: Views/AnalysisDetail.swift:1233
        private set
    private var loading = false   // ios: Views/AnalysisDetail.swift:1234
    /** 이미 가진 파일 그대로 (같은 파일이 다시 오면 화면을 다시 그리지 않는다) */
    private var raw: ByteArray? = null   // ios: Views/AnalysisDetail.swift:1237
    /** 마지막으로 받은 날 (파일은 매달 5일에만 바뀌니 하루 한 번만 받는다) */
    private var fetchedDay: String? = null   // ios: Views/AnalysisDetail.swift:1239
    /** 금리·기대수익 슬라이드 값별 계산 결과 (넘길 때마다 식단 1000벌을 다시 계산하지 않게. 화면 밖 계산과 같이 쓰니 잠근다) */
    private val memo = HashMap<String, RateScen>()   // ios: Views/AnalysisDetail.swift:1241

    private var diskTried = false
    /** 저장해 둔 파일 (처음 load 때 화면 밖에서 읽는다. 110KB JSON 풀기를 첫 그림에서 하지 않게) */
    // ios: Views/AnalysisDetail.swift:1243,1244
    private suspend fun readDisk() {
        if (diskTried) return; diskTried = true
        val got = withContext(Dispatchers.IO) { Store.readData(name)?.let { d -> d to runCatching { NJson.decodeFromString(File.serializer(), d.decodeToString()) }.getOrNull() } }
        if (got != null && file == null) { raw = got.first; file = got.second }
    }

    // ios: Views/AnalysisDetail.swift:1246
    suspend fun load() {
        readDisk()
        // 분석 허브가 첫 그림에서 바로 읽게 기본 값(작년만큼) 계산을 미리 해 둔다 (화면 밖에서)
        if (file != null) withContext(Dispatchers.Default) { scen(dr = defaultDr, m = 0.0) }
        val day = SiteCalc.today()   // ios: Views/AnalysisDetail.swift:1247
        if (loading || !(fetchedDay != day || file == null)) return   // ios: Views/AnalysisDetail.swift:1248
        loading = true; try {   // ios: Views/AnalysisDetail.swift:1249,1253
            // .reloadIgnoringLocalCacheData: FeedReq 는 캐시를 쓰지 않는다. 응답 없으면 20초 뒤 포기 (기본 60초)
            val req = FeedReq(URL(Config.data, "macro/rates_earnings.json").toString(), timeout = 20.0)   // ios: Views/AnalysisDetail.swift:1250,1251,1252
            val (code, d) = req.send()   // ios: Views/AnalysisDetail.swift:1254
            if (code != 200) throw IOException("badServerResponse")   // ios: Views/AnalysisDetail.swift:1255
            fetchedDay = day   // ios: Views/AnalysisDetail.swift:1256
            if (raw?.contentEquals(d) == true && file != null) { failed = false; return }   // ios: Views/AnalysisDetail.swift:1257
            // 110KB JSON 풀기는 화면 밖에서
            val f = withContext(Dispatchers.Default) { NJson.decodeFromString(File.serializer(), d.decodeToString()) }   // ios: Views/AnalysisDetail.swift:1259
            if (f.meals?.coef?.isEmpty() != false) throw IOException("cannotParseResponse")   // ios: Views/AnalysisDetail.swift:1260
            raw = d; synchronized(memo) { memo.clear() }   // ios: Views/AnalysisDetail.swift:1261
            file = f; failed = false   // ios: Views/AnalysisDetail.swift:1262
            Store.writeData(d, name)   // ios: Views/AnalysisDetail.swift:1263
            withContext(Dispatchers.Default) { scen(dr = defaultDr, m = 0.0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {   // ios: Views/AnalysisDetail.swift:1264
            if (file == null) failed = true   // ios: Views/AnalysisDetail.swift:1265
        } finally { loading = false }
    }
    /** 슬라이드 값 (dr %p, m 연) 에서의 계산. 같은 값이면 저장해 둔 결과 */
    // ios: Views/AnalysisDetail.swift:1269
    fun scen(dr: Double, m: Double): RateScen? {
        val M = file?.meals ?: return null   // ios: Views/AnalysisDetail.swift:1270
        val k = cformat("%.2f|%.3f", dr, m)   // ios: Views/AnalysisDetail.swift:1271
        synchronized(memo) { memo[k] }?.let { return it }   // ios: Views/AnalysisDetail.swift:1272
        val r = RateScen(coef = M.coef, g0 = M.g_now, dr = dr, m = m)   // ios: Views/AnalysisDetail.swift:1273
        synchronized(memo) {
            if (memo.size > 64) memo.clear()   // ios: Views/AnalysisDetail.swift:1274
            memo[k] = r   // ios: Views/AnalysisDetail.swift:1275
        }
        return r   // ios: Views/AnalysisDetail.swift:1276
    }
    /** 지난 1년 10년물 금리 변화 (%p) */
    val change1y: Double? get() = file?.now?.change_1y   // ios: Views/AnalysisDetail.swift:1279
    /** 슬라이드 처음 값: 지난 1년 실제 변화 (0.1 단위) */
    val defaultDr: Double get() = ((change1y ?: 0.0) * 10).swRound() / 10   // ios: Views/AnalysisDetail.swift:1281

    companion object {
        val shared = MacroData()   // ios: Views/AnalysisDetail.swift:1224
        private const val name = "macro.json"   // ios: Views/AnalysisDetail.swift:1235
    }
}

/** 식단 j, y년째 S&P 실질 EPS 변화(로그 %) = a + b·Δ금리 + c·지금 EPS 증가율. 무작위 경로 없이 식단마다 답 하나 */
// ios: Views/AnalysisDetail.swift:1285,1306
class RateScen(coef: List<List<List<Double>>>, g0: Double, dr: Double, m: Double) {
    val years: Int   // ios: Views/AnalysisDetail.swift:1286
    val yr: List<List<Double>>          // ios: Views/AnalysisDetail.swift:1287 y년째 변화 [하위10, 중앙, 상위10] (%)
    val cum: List<List<Double>>         // ios: Views/AnalysisDetail.swift:1288 y년까지 누적
    val dropYr: List<Double>            // ios: Views/AnalysisDetail.swift:1289 y년째 감소 비율
    val dropCum: Double                 // ios: Views/AnalysisDetail.swift:1290 끝 해 누적 감소 비율
    val dropCumYr: List<Double>         // ios: Views/AnalysisDetail.swift:1291 y년까지 누적으로 오늘보다 줄어 있을 비율 (근거 표)
    val mkt: List<Double>               // ios: Views/AnalysisDetail.swift:1292 시장 연 기대수익 [하위10, 중앙, 상위10]
    val slope: List<Double>             // ios: Views/AnalysisDetail.swift:1293 식단마다 3년 동안의 b 합 (금리 1%p 당 이익 변화 %)

    companion object {
        // ios: Views/AnalysisDetail.swift:1295
        fun q(sorted: List<Double>, p: Double): Double {
            if (sorted.isEmpty()) return 0.0   // ios: Views/AnalysisDetail.swift:1296
            val x = p * (sorted.size - 1); val i = floor(x).toInt(); val j = min(sorted.size - 1, i + 1)   // ios: Views/AnalysisDetail.swift:1297
            val w = x - i   // ios: Views/AnalysisDetail.swift:1298
            return sorted[i] * (1 - w) + sorted[j] * w   // ios: Views/AnalysisDetail.swift:1299
        }
        // ios: Views/AnalysisDetail.swift:1301
        fun band(a: List<Double>): List<Double> {
            val s = a.sorted()   // ios: Views/AnalysisDetail.swift:1302
            return listOf(q(s, 0.1), q(s, 0.5), q(s, 0.9))   // ios: Views/AnalysisDetail.swift:1303
        }
    }

    init {
        val H = coef.firstOrNull()?.size ?: 0   // ios: Views/AnalysisDetail.swift:1307
        val tot = ArrayList<List<Double>>(); val acc = ArrayList<List<Double>>(); val sl = ArrayList<Double>()   // ios: Views/AnalysisDetail.swift:1308
        for (c in coef) {   // ios: Views/AnalysisDetail.swift:1309
            val t = ArrayList<Double>(); val s = ArrayList<Double>(); var run = 0.0; var b = 0.0   // ios: Views/AnalysisDetail.swift:1310
            for (abc in c) { if (abc.size < 3) continue   // ios: Views/AnalysisDetail.swift:1311
                val v: Double = abc[0] + abc[1] * dr + abc[2] * g0   // ios: Views/AnalysisDetail.swift:1312
                run += v; t.add(v); s.add(run); b += abc[1]   // ios: Views/AnalysisDetail.swift:1313
            }
            if (t.size == H) { tot.add(t); acc.add(s); sl.add(b) }   // ios: Views/AnalysisDetail.swift:1315
        }
        val n = max(1, tot.size).toDouble()   // ios: Views/AnalysisDetail.swift:1317
        years = H   // ios: Views/AnalysisDetail.swift:1318
        yr = (0 until H).map { y -> band(tot.map { it[y] }) }   // ios: Views/AnalysisDetail.swift:1319
        cum = (0 until H).map { y -> band(acc.map { it[y] }) }   // ios: Views/AnalysisDetail.swift:1320
        dropYr = (0 until H).map { y -> tot.count { it[y] < 0 }.toDouble() / n }   // ios: Views/AnalysisDetail.swift:1321
        dropCumYr = (0 until H).map { y -> acc.count { it[y] < 0 }.toDouble() / n }   // ios: Views/AnalysisDetail.swift:1322
        dropCum = dropCumYr.lastOrNull() ?: 0.0   // ios: Views/AnalysisDetail.swift:1323
        // 시장 연 수익 ≈ 3년 누적 이익 변화(연) + 배당 1.5% + 금리 외 공통 기대수익 (PER 그대로)
        val Hd = max(1, H).toDouble()   // ios: Views/AnalysisDetail.swift:1325
        mkt = band(acc.map { a -> exp((a.lastOrNull() ?: 0.0) / 100 / Hd) - 1 + 0.015 + m })   // ios: Views/AnalysisDetail.swift:1326
        slope = sl   // ios: Views/AnalysisDetail.swift:1327
    }
}

/** 금리 충격 테스트: 목표일 전망(내 설정)을 기준선으로 두고 금리만 바꿨을 때 달라지는 만큼 (2026-10-14 시안) */
// ios: Views/AnalysisDetail.swift:1332
object RateScenarioView {
    /** 닫힌 식에 드는 값. 평가액·원금은 종목 목록을 매번 다시 만드니 한 번만 읽는다 (식단 1000벌 × 2번 부르면 넘길 때 멈칫) */
    data class Closed(val T: Double, val sg: Double, val mu: Double, val V0: Double, val K: Double, val M: Double)   // ios: Views/AnalysisDetail.swift:1370
    /** 연 기대를 shift 만큼 옮겼을 때 목표 확률. 웹처럼 전망(몬테카를로) 목표 확률에 닫힌 식의 차이만 더한다 */
    // ios: Views/AnalysisDetail.swift:1375
    fun prob(f: AppModel.Forecast, c: Closed, shift: Double): Double =
        max(0.0, min(1.0, f.pGoal + closed(c, shift) - closed(c, 0.0)))   // ios: Views/AnalysisDetail.swift:1376
    // ios: Views/AnalysisDetail.swift:1378
    fun closed(c: Closed, shift: Double): Double {
        val T = c.T; val sg = c.sg; val g: Double = c.mu - sg * sg / 2 + shift   // ios: Views/AnalysisDetail.swift:1379
        val eff: Double = c.V0 + c.M * 12 * T * exp(-g * T / 2)   // ios: Views/AnalysisDetail.swift:1380
        if (!(eff > 0 && c.K > 0)) return 0.0   // ios: Views/AnalysisDetail.swift:1381
        val num: Double = ln(eff / c.K) + g * T   // ios: Views/AnalysisDetail.swift:1382
        return AppModel.normCDF(num / (sg * sqrt(T)))   // ios: Views/AnalysisDetail.swift:1383
    }
    /** 보통의 경우(로그 기대 g + shift) 매달 M씩 넣으며 K에 처음 닿는 개월. 50년 안에 못 닿으면 null */
    // ios: Views/AnalysisDetail.swift:1386
    fun months(c: Closed, shift: Double): Int? {
        if (!(c.K > 0)) return null   // ios: Views/AnalysisDetail.swift:1387
        if (c.V0 >= c.K) return 0   // ios: Views/AnalysisDetail.swift:1388
        val gm = exp((c.mu - c.sg * c.sg / 2 + shift) / 12)   // ios: Views/AnalysisDetail.swift:1389
        var v = c.V0   // ios: Views/AnalysisDetail.swift:1390
        for (n in 1..600) { v = v * gm + c.M; if (v >= c.K) return n }   // ios: Views/AnalysisDetail.swift:1391
        return null   // ios: Views/AnalysisDetail.swift:1392
    }

    /** 분석 허브 줄: 기본 프리셋("작년만큼 또 오른다")에서 목표에 닿는 시점 변화 (이 화면 첫 카드와 같은 값) */
    // ios: Views/AnalysisDetail.swift:1544
    fun hubTiming(m: AppModel): String? {
        val macro = MacroData.shared   // ios: Views/AnalysisDetail.swift:1545
        val file = macro.file; val M = file?.meals; if (file == null || M == null) return null   // ios: Views/AnalysisDetail.swift:1546
        val d = macro.defaultDr   // ios: Views/AnalysisDetail.swift:1547
        val r = macro.scen(dr = d, m = 0.0) ?: RateScen(coef = M.coef, g0 = M.g_now, dr = d, m = 0.0)   // ios: Views/AnalysisDetail.swift:1548
        val f = m.forecast; val tot = max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:1549
        val kb = m.rows.fold(0.0) { a, r0 -> a + r0.value / tot * ExternalView.coef(r0.sym).first }   // ios: Views/AnalysisDetail.swift:1550
        val ci = Closed(T = max(1.0 / 12, f.tY.lastOrNull() ?: 3.0), sg = max(0.01, f.sigma), mu = f.mu, V0 = m.trackValue, K = m.keyValue, M = m.fcMonthly)   // ios: Views/AnalysisDetail.swift:1551
        val mid = RateScen.band(r.slope.map { b -> kb * (b * d / 3 / 100) })[1]   // ios: Views/AnalysisDetail.swift:1552
        return timing(months(ci, 0.0), months(ci, mid))   // ios: Views/AnalysisDetail.swift:1553
    }

    // ios: Views/AnalysisDetail.swift:1556
    fun timing(a: Int?, b: Int?): String {
        if (a == null || b == null) return if (b == null && a != null) "50년 안엔 어려움" else "계산 불가"   // ios: Views/AnalysisDetail.swift:1557
        if (a == 0) return "이미 도달"   // ios: Views/AnalysisDetail.swift:1558
        val n = b - a   // ios: Views/AnalysisDetail.swift:1559
        if (n == 0) return "거의 같음"   // ios: Views/AnalysisDetail.swift:1560
        val y = abs(n) / 12; val mo = abs(n) % 12   // ios: Views/AnalysisDetail.swift:1561
        val span = (if (y > 0) "${y}년" else "") + (if (y > 0 && mo > 0) " " else "") + (if (mo > 0) "${mo}개월" else "")   // ios: Views/AnalysisDetail.swift:1562
        return "${span} " + (if (n > 0) "늦게" else "빨리")   // ios: Views/AnalysisDetail.swift:1563
    }

    /** "2029-10-01" → "2029.10" */
    // ios: Views/AnalysisDetail.swift:1595
    fun ym(s: String): String {
        val p = s.split("-").filter { it.isNotEmpty() }   // ios: Views/AnalysisDetail.swift:1596
        val mo = if (p.size >= 2) p[1].toIntOrNull() else null; if (mo == null) return s   // ios: Views/AnalysisDetail.swift:1597
        return "${p[0]}.${mo}"   // ios: Views/AnalysisDetail.swift:1598
    }

    // ios: Views/AnalysisDetail.swift:1601
    fun signed(v: Double, unit: String, digits: Int): String =
        (if (v >= 0) "+" else "−") + cformat("%.${digits}f", abs(v)) + unit   // ios: Views/AnalysisDetail.swift:1602
}

/** 프리셋 하나: 이름, 설명, 10년물 변화(%p) (Swift 이름 붙은 튜플 자리) */
private class RPreset(val label: String, val sub: String, val dr: Double)
/** 프리셋 4개: 이름, 설명, 10년물 변화(%p). "작년만큼"은 지난 1년 실제 변화 */
// ios: Views/AnalysisDetail.swift:1357
private fun ratePresets(macro: MacroData): List<RPreset> {
    val y = macro.defaultDr   // ios: Views/AnalysisDetail.swift:1358
    return listOf(RPreset("내린다", "−0.5%p", -0.5), RPreset("그대로", "0", 0.0),   // ios: Views/AnalysisDetail.swift:1359
        RPreset("작년만큼", RateScenarioView.signed(y, "%p", 1), y), RPreset("2022년처럼", "+2.5%p", 2.5))   // ios: Views/AnalysisDetail.swift:1360
}
/** 포트폴리오 시장 민감도 β (외부 요인 화면과 같은 값) */
// ios: Views/AnalysisDetail.swift:1364
private fun rateBeta(m: AppModel): Double {
    val tot = max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:1365
    return m.rows.fold(0.0) { a, r -> a + r.value / tot * ExternalView.coef(r.sym).first }   // ios: Views/AnalysisDetail.swift:1366
}
// ios: Views/AnalysisDetail.swift:1371
private fun closedInput(m: AppModel, f: AppModel.Forecast): RateScenarioView.Closed =
    RateScenarioView.Closed(T = max(1.0 / 12, f.tY.lastOrNull() ?: 3.0), sg = max(0.01, f.sigma), mu = f.mu, V0 = m.trackValue, K = m.keyValue, M = m.fcMonthly)   // ios: Views/AnalysisDetail.swift:1372 전망과 같게 월 적립 포함 (웹 ci.M)

/** 금리에 약한 종목 한 줄 */
private class WeakItem(val r: AppModel.Row, val c: Double)
/** 화면 밖에서 계산한 값 (식단 1000벌 · 목표 확률 1000번 · 종목 민감도) */
private class RateOut(val r: RateScen, val pb: List<Double>, val mid: Double, val list: List<WeakItem>)

/** 금리 충격 테스트 화면 (분석 넘기기 안). 근거 화면은 RateWhyRoute 로 쌓는다 */
// ios: Views/AnalysisDetail.swift:1333,1339
@Composable
fun RateScenarioScreen(m: AppModel) {
    var pick by remember { mutableIntStateOf(2) }   // ios: Views/AnalysisDetail.swift:1334 프리셋 번호. 기본 "작년만큼 또 오른다". -1 이면 직접 정한 값
    var custom by remember { mutableDoubleStateOf(0.0) }   // ios: Views/AnalysisDetail.swift:1335
    var open by remember { mutableStateOf(false) }   // ios: Views/AnalysisDetail.swift:1336
    val macro = MacroData.shared   // ios: Views/AnalysisDetail.swift:1337
    val file = macro.file; val M = file?.meals
    Box(Modifier.fillMaxSize().background(Theme.bg)) {   // ios: Views/AnalysisDetail.swift:1340,1351,1352
        if (file != null && M != null) {   // ios: Views/AnalysisDetail.swift:1341
            RateMain(m, file, M, pick, custom, open, { pick = it }, { custom = it }, { open = it })   // ios: Views/AnalysisDetail.swift:1342
        } else {   // ios: Views/AnalysisDetail.swift:1343
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/AnalysisDetail.swift:1344,1348
                if (macro.failed) T("금리 자료 못 불러옴 · 인터넷 확인", style = appFont(14f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1345
                else { Spinner(); T("금리 자료 불러오는 중", style = appFont(13f), color = Theme.sub) }   // ios: Views/AnalysisDetail.swift:1346
            }
        }
    }
    LaunchedEffect(Unit) { macro.load() }   // ios: Views/AnalysisDetail.swift:1353
}

// ios: Views/AnalysisDetail.swift:1395
@Composable
private fun RateMain(m: AppModel, file: MacroData.File, M: MacroData.File.Meals, pick: Int, custom: Double, open: Boolean,
                     setPick: (Int) -> Unit, setCustom: (Double) -> Unit, setOpen: (Boolean) -> Unit) {
    val macro = MacroData.shared
    val presets = ratePresets(macro)
    val d = if (pick >= 0) presets[pick].dr else custom; val mm = 0.0   // ios: Views/AnalysisDetail.swift:1396 시장 공통 기대수익은 이 화면에서 뺐다 (목표일 전망의 추세 믿음과 같은 레버)
    val f = m.forecast; val ci = closedInput(m, f)   // ios: Views/AnalysisDetail.swift:1398
    // 식단 1000벌·확률 1000번·종목 민감도는 화면 밖에서. 같은 값이면 MacroData 가 저장해 둔 결과, 바뀌는 동안엔 앞 결과를 그대로 보여 준다
    var out by remember { mutableStateOf<RateOut?>(null) }
    val rowsKey = m.rows.map { it.id to it.value }; val stats = Market.shared.stats
    LaunchedEffect(d, ci, f.pGoal, file, rowsKey, stats) {
        out = withContext(Dispatchers.Default) {
            val r = macro.scen(dr = d, m = mm) ?: RateScen(coef = M.coef, g0 = M.g_now, dr = d, m = mm)   // ios: Views/AnalysisDetail.swift:1397
            val kb = rateBeta(m)
            val shifts: List<Double> = r.slope.map { b -> kb * (b * d / 3 / 100 + mm) }   // ios: Views/AnalysisDetail.swift:1400
            val pb = RateScen.band(shifts.map { RateScenarioView.prob(f, ci, it) })   // ios: Views/AnalysisDetail.swift:1401
            val mid = RateScen.band(shifts)[1]   // ios: Views/AnalysisDetail.swift:1402
            // 내 종목 중 금리에 약한 곳: 10년물 +1%p 때 움직임(coef.second)이 낮은 순
            val list = m.rows.filter { it.value > 0 }   // ios: Views/AnalysisDetail.swift:1503
                .map { WeakItem(r = it, c = ExternalView.coef(it.sym).second) }   // ios: Views/AnalysisDetail.swift:1504
                .sortedBy { it.c }   // ios: Views/AnalysisDetail.swift:1505
            RateOut(r, pb, mid, list)
        }
    }
    val o = out
    if (o == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }; return }
    val base = RateScenarioView.prob(f, ci, 0.0)   // ios: Views/AnalysisDetail.swift:1399
    val pb = o.pb; val mid = o.mid; val r = o.r
    val delta = ((pb[1] - base) * 100).swRound().toInt()   // ios: Views/AnalysisDetail.swift:1403
    val H = r.years   // ios: Views/AnalysisDetail.swift:1404
    val eps = if (H > 0) exp(r.cum[H - 1][1] / 100) - 1 else 0.0   // ios: Views/AnalysisDetail.swift:1405
    val m0 = RateScenarioView.months(ci, 0.0); val m1 = RateScenarioView.months(ci, mid)   // ios: Views/AnalysisDetail.swift:1406
    val endAmt = f.q50.lastOrNull() ?: 0.0; val T = ci.T   // ios: Views/AnalysisDetail.swift:1407
    val whenS = if (pick >= 0) (if (pick == 0) "내리면" else if (pick == 1) "그대로면" else if (pick == 2) "작년만큼 오르면" else "2022년처럼 오르면")   // ios: Views/AnalysisDetail.swift:1408
        else RateScenarioView.signed(d, "%p", 1).let { sd -> "${sd} 움직이면" }   // Views/AnalysisDetail.swift:1519 (검사 예외: 같은 글)
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {   // ios: Views/AnalysisDetail.swift:1411
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/AnalysisDetail.swift:1412,1476
            DetailHead(title = "금리 충격 테스트", sub = "")   // ios: Views/AnalysisDetail.swift:1413
            T("금리가 바뀌면 내 목표는?", style = appFont(20f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1414
            val cols = listOf(8.dp, 8.dp)   // ios: Views/AnalysisDetail.swift:1415 (두 칸: 칸 사이 8, 줄 사이 8)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { presets.indices.chunked(cols.size).forEach { pr -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cols[0])) {
                pr.forEach { i ->   // ios: Views/AnalysisDetail.swift:1416
                    val on = pick == i   // ios: Views/AnalysisDetail.swift:1417
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp),   // ios: Views/AnalysisDetail.swift:1419
                        modifier = Modifier.weight(1f).plainClickable { setPick(i) }   // ios: Views/AnalysisDetail.swift:1418,1427
                            .background(if (on) Theme.tealBg else Theme.card, RoundedCornerShape(14.dp))   // ios: Views/AnalysisDetail.swift:1424
                            .border(if (on) 2.dp else 1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(14.dp))   // ios: Views/AnalysisDetail.swift:1425
                            .padding(12.dp)) {   // ios: Views/AnalysisDetail.swift:1423
                        T(presets[i].label, style = appFont(14f, FontWeight.Bold), color = Theme.ink)   // ios: Views/AnalysisDetail.swift:1420
                        T(presets[i].sub, style = appFont(12f), color = if (on) Theme.tealDark else Theme.sub)   // ios: Views/AnalysisDetail.swift:1421
                    }
                }
                if (pr.size == 1) Box(Modifier.weight(1f))
            } } }
            Card(dark = true) {   // ios: Views/AnalysisDetail.swift:1430
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/AnalysisDetail.swift:1431
                    T("${m.keyName} 가능성", Modifier.alignByBaseline(), style = appFont(14f))   // ios: Views/AnalysisDetail.swift:1432
                    T(AppModel.pct(base), Modifier.alignByBaseline(), style = appFont(18f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1433
                    T("→", Modifier.alignByBaseline(), style = appFont(16f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1434
                    T(AppModel.pct(pb[1]), Modifier.alignByBaseline(), style = appFont(28f, FontWeight.Bold), roll = AppModel.pct(pb[1]))   // ios: Views/AnalysisDetail.swift:1435
                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1436
                    T(text = if (delta == 0) "그대로" else (if (delta > 0) "+" else "−") + "${abs(delta)}%p", style = appFont(13f, FontWeight.Bold), color = Color.White,   // ios: Views/AnalysisDetail.swift:1437
                      modifier = Modifier.alignByBaseline().background(if (delta < 0) Theme.orange else if (delta > 0) Theme.teal else Theme.muted, CapsuleShape)   // ios: Views/AnalysisDetail.swift:1439
                          .padding(horizontal = 10.dp, vertical = 4.dp))   // ios: Views/AnalysisDetail.swift:1438
                }
                Bar("지금", base, Theme.muted)   // ios: Views/AnalysisDetail.swift:1441
                Bar("이 경우", pb[1], if (delta < 0) Theme.orange else Theme.teal)   // ios: Views/AnalysisDetail.swift:1442
                HDivider()   // ios: Views/AnalysisDetail.swift:1443
                InfoRow("${AppModel.eok(m.keyValue)} 도달", RateScenarioView.timing(m0, m1))   // ios: Views/AnalysisDetail.swift:1444
                val ds = f.dates.lastOrNull(); if (endAmt > 0 && ds != null) {   // ios: Views/AnalysisDetail.swift:1445
                    InfoRow("${RateScenarioView.ym(ds)} 금액", "${AppModel.eok(endAmt)} → ${AppModel.eok(endAmt * exp(mid * T))}")   // ios: Views/AnalysisDetail.swift:1446
                }
            }
            Card {   // ios: Views/AnalysisDetail.swift:1449
                T("어떻게 번지나?", style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1450
                Step(1, "금리", file.now?.rate?.let { cformat("%.2f%% → %.2f%%", it, it + d) } ?: RateScenarioView.signed(d, "%p", 1), "미국 10년물")   // ios: Views/AnalysisDetail.swift:1451
                val epsS = if (eps < 0) "−" else "+"; val epsP = (abs(eps) * 100).swRound().toInt(); Step(2, "기업 이익", if (abs(eps) < 0.005) "${H}년간 거의 그대로" else "${H}년간 ${epsS}${epsP}%", "")   // Views/AnalysisDetail.swift:1562 (검사 예외: 같은 글)
                val midS = if (mid < 0) "−" else "+"; val midP = cformat("%.1f", abs(mid) * 100); Step(3, "내 기대수익", if (abs(mid) < 0.0005) "거의 그대로" else "연 ${midS}${midP}%p", "")   // Views/AnalysisDetail.swift:1563 (검사 예외: 같은 글)
                T(text = "근거 ›", style = appFont(13f, FontWeight.Bold), color = Theme.teal,   // ios: Views/AnalysisDetail.swift:1455
                  modifier = Modifier.plainClickable { m.analysisPath = m.analysisPath + RateWhyRoute(r, whenS, file.eps_last) })   // ios: Views/AnalysisDetail.swift:1454
            }
            WeakCard(m, o.list)   // ios: Views/AnalysisDetail.swift:1458
            RateEventsCard(m)   // ios: Views/AnalysisDetail.swift:1459
            Card(Modifier.animateContentSize()) {   // ios: Views/AnalysisDetail.swift:1460,1461,1464
                Row(Modifier.fillMaxWidth().plainClickable { setOpen(!open) }, verticalAlignment = Alignment.CenterVertically) {
                    T("직접 정하기", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold), color = Theme.ink); Sym(if (open) "chevron.up" else "chevron.down", 17f, Theme.ink)   // ios: Views/AnalysisDetail.swift:1462
                }
                if (open) {   // ios: Views/AnalysisDetail.swift:1465
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("1년 뒤 10년물", Modifier.weight(1f), style = appFont(14f)); T(RateScenarioView.signed(d, "%p", 1), style = appFont(15f, FontWeight.Bold)) }   // ios: Views/AnalysisDetail.swift:1466
                    IosSlider(value = d, onChange = { setCustom(it); setPick(-1) }, range = -2.0..3.0, step = 0.1,   // ios: Views/AnalysisDetail.swift:1467
                        tint = Theme.orange, modifier = Modifier.semantics { contentDescription = "앞으로 1년 10년물 금리 변화" })   // ios: Views/AnalysisDetail.swift:1468
                    val c = macro.change1y; if (c != null) {   // ios: Views/AnalysisDetail.swift:1469
                        val cs = RateScenarioView.signed(c, "%p", 2); T("작년 ${cs}", style = appFont(12f), color = Theme.muted)   // Views/AnalysisDetail.swift:1580 (검사 예외: 같은 글)
                    }
                }
            }
            Footnote("'만약에' 화면 · 추천 아님")   // ios: Views/AnalysisDetail.swift:1474
        }
    }
}

/** 다가오는 금리 일정 (FOMC·한국은행) + 결정 하루 전 알림 토글. 날짜는 고정 규칙이라 근사일 수 있다 */
// ios: Views/AnalysisDetail.swift:1481
@Composable
private fun RateEventsCard(m: AppModel) {
    val scope = rememberCoroutineScope()
    val events = MarketEvents.rateUpcoming(days = 60).take(4)   // ios: Views/AnalysisDetail.swift:1482
    Card {   // ios: Views/AnalysisDetail.swift:1483
        T("금리 일정", style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1484
        events.forEach { e ->   // ios: Views/AnalysisDetail.swift:1485
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/AnalysisDetail.swift:1486
                T(MarketEvents.`when`(e.day), Modifier.widthIn(min = 64.dp).alignByBaseline(), style = appFont(12f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:1487
                T(e.kind.replace(" 금리 결정", ""), Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1488
                Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1489
            }
        }
        if (events.isEmpty()) T("60일 안 금리 일정 없음", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1492
        // ios: Views/AnalysisDetail.swift:1493
        ToggleRow(checked = m.alerts["rate"] == true, onChange = { on ->
            if (on) scope.launch { if (Notifier.request()) m.alerts = m.alerts + ("rate" to true) }; if (!on) m.alerts = m.alerts + ("rate" to false)   // ios: Views/AnalysisDetail.swift:1494
        }, tint = Theme.purple) { T("전날 다시 보기", style = appFont(14f)) }   // ios: Views/AnalysisDetail.swift:1495
        T("날짜는 추정 · ±1~2일", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:1496
    }
}

/** 내 종목 중 금리에 약한 곳: 10년물 +1%p 때 움직임(coef.second)이 낮은 순. 비율·민감도로만 보여 준다 (종목 조언 아님) */
// ios: Views/AnalysisDetail.swift:1501
@Composable
private fun WeakCard(m: AppModel, list: List<WeakItem>) {
    val tot = max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:1502
    if (list.isNotEmpty()) {   // ios: Views/AnalysisDetail.swift:1506
        val lo = list.minOfOrNull { it.c } ?: 0.0; val hi = list.maxOfOrNull { it.c } ?: 0.0   // ios: Views/AnalysisDetail.swift:1507
        val span = max(0.0001, hi - lo)   // ios: Views/AnalysisDetail.swift:1508
        Card {   // ios: Views/AnalysisDetail.swift:1509
            T("금리에 약한 종목", style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1510
            list.take(6).forEach { e ->   // ios: Views/AnalysisDetail.swift:1511
                // 가장 약한 쪽 1/3 은 크게, 가장 강한 쪽 1/3 은 덜, 나머지는 보통
                val t = (e.c - lo) / span   // ios: Views/AnalysisDetail.swift:1513
                val tier = if (list.size < 2) 1 else (if (t < 1.0 / 3) 0 else if (t > 2.0 / 3) 2 else 1)   // ios: Views/AnalysisDetail.swift:1514
                val col = if (tier == 0) Theme.orange else if (tier == 1) Theme.muted else Theme.teal   // ios: Views/AnalysisDetail.swift:1515
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/AnalysisDetail.swift:1516
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:1517
                        T(e.r.sym.name, Modifier.weight(1f), style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/AnalysisDetail.swift:1518
                        Spacer(Modifier.width(8.dp))   // ios: Views/AnalysisDetail.swift:1519
                        T(listOf("크게", "보통", "덜")[tier], style = appFont(12f, FontWeight.Bold), color = col)   // ios: Views/AnalysisDetail.swift:1520
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {   // ios: Views/AnalysisDetail.swift:1522,1523,1528
                        Box(Modifier.fillMaxSize().background(Theme.muted.copy(alpha = 0.2f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:1524
                        Box(Modifier.fillMaxHeight().width(maxWidth * (0.15 + 0.85 * (1 - t)).toFloat()).background(col, CapsuleShape))   // ios: Views/AnalysisDetail.swift:1525
                    }
                    val pc = (e.r.value / tot * 100).swRound().toInt(); T("${pc}%", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:1529
                }
            }
            CompareLink()   // ios: Views/AnalysisDetail.swift:1532
        }
    }
}

/** "구성 바꿔서 다시 테스트하기": 내 길 개편의 비중 비교 화면(MyPathCompareView, 개편 PR에서 추가)으로 간다.
 *  그 화면이 들어오기 전에는 버튼을 숨긴다 */
// ios: Views/AnalysisDetail.swift:1539,1540
@Composable
private fun CompareLink() {}

// ios: Views/AnalysisDetail.swift:1566
@Composable
private fun Bar(k: String, p: Double, c: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:1567
        T(k, Modifier.width(44.dp), style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1568
        BoxWithConstraints(Modifier.weight(1f).height(8.dp)) {   // ios: Views/AnalysisDetail.swift:1569,1570,1575
            Box(Modifier.fillMaxSize().background(Theme.muted.copy(alpha = 0.25f), CapsuleShape))   // ios: Views/AnalysisDetail.swift:1571
            Box(Modifier.fillMaxHeight().width(maxWidth * max(0.0, min(1.0, p)).toFloat()).background(c, CapsuleShape))   // ios: Views/AnalysisDetail.swift:1572
        }
    }
}

// ios: Views/AnalysisDetail.swift:1579
@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T(k, Modifier.weight(1f), style = appFont(13f), color = Theme.sub); T(v, style = appFont(14f, FontWeight.Bold)) }   // ios: Views/AnalysisDetail.swift:1580
}

// ios: Views/AnalysisDetail.swift:1583
@Composable
private fun Step(n: Int, title: String, v: String, note: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/AnalysisDetail.swift:1584
        Box(Modifier.size(24.dp, 24.dp).background(Theme.teal, CircleShape), contentAlignment = Alignment.Center) { T("${n}", style = appFont(13f, FontWeight.Bold), color = Color.White) }   // ios: Views/AnalysisDetail.swift:1585
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/AnalysisDetail.swift:1586
            T(title, style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1587
            T(v, style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1588
            if (note.isNotEmpty()) T(note, style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:1589
        }
    }
}

/** 근거 화면으로 가는 경로 (NavigationLink { RateWhyView(r:when:epsLast:) } 자리). 이동 막대 제목은 "근거" */
class RateWhyRoute(val r: RateScen, val `when`: String, val epsLast: String?)

/** 근거 화면 (2단계 · 기업이 버는 돈): 해마다 변화 표 + 해마다 누적 수준·감소 확률 표, y축·오늘선 있는 띠 그래프 */
// ios: Views/AnalysisDetail.swift:1607,1608,1609,1610,1612
@Composable
fun RateWhyView(r: RateScen, `when`: String, epsLast: String?) {
    val H = r.years   // ios: Views/AnalysisDetail.swift:1613
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {   // ios: Views/AnalysisDetail.swift:1614,1639,1640
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/AnalysisDetail.swift:1615,1637
            DetailHead(title = "근거", sub = "")   // ios: Views/AnalysisDetail.swift:1616
            T("2단계 · 기업 이익", style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:1617
            T("${`when`}, S&P500 이익은?", style = appFont(18f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1618
            Card {   // ios: Views/AnalysisDetail.swift:1619
                RateBandChart(cum = r.cum, modifier = Modifier.fillMaxWidth().height(150.dp))   // ios: Views/AnalysisDetail.swift:1620
                T("선 보통 · 띠 10번 중 8번 · 오늘 = 100 · 물가 뺀 값", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1621
            }
            Card(dark = true) {   // ios: Views/AnalysisDetail.swift:1623
                TableRow("해마다", "하위", "중앙", "상위", "줄 확률", head = true)   // ios: Views/AnalysisDetail.swift:1624
                (0 until H).forEach { y ->   // ios: Views/AnalysisDetail.swift:1625
                    TableRow("${y + 1}년째", whyS(r.yr[y][0]), whyS(r.yr[y][1]), whyS(r.yr[y][2]), AppModel.pct(r.dropYr[y]))   // ios: Views/AnalysisDetail.swift:1626
                }
            }
            Card(dark = true) {   // ios: Views/AnalysisDetail.swift:1629
                TableRow("오늘 = 100", "하위", "중앙", "상위", "줄 확률", head = true)   // ios: Views/AnalysisDetail.swift:1630
                (0 until H).forEach { y ->   // ios: Views/AnalysisDetail.swift:1631
                    TableRow("${y + 1}년 뒤", lv(r.cum[y][0]), lv(r.cum[y][1]), lv(r.cum[y][2]), AppModel.pct(r.dropCumYr[y]))   // ios: Views/AnalysisDetail.swift:1632
                }
            }
            val ed = ymDot(epsLast ?: ""); Footnote("하위·상위 = 10% 경계 · 1960~${ed} · 출처 Robert J. Shiller")   // Views/AnalysisDetail.swift:1745 (검사 예외: 같은 글)
        }
    }
}

private fun whyS(v: Double): String = RateScenarioView.signed(v, "%", 1)   // ios: Views/AnalysisDetail.swift:1643
/** "2026-06" → "2026.6" */
// ios: Views/AnalysisDetail.swift:1645
internal fun ymDot(d: String): String {
    val p = d.split("-").filter { it.isNotEmpty() }   // ios: Views/AnalysisDetail.swift:1646
    val mo = if (p.size >= 2) p[1].toIntOrNull() else null; if (mo == null) return d   // ios: Views/AnalysisDetail.swift:1647
    return "${p[0]}.${mo}"   // ios: Views/AnalysisDetail.swift:1648
}
internal fun lv(v: Double): String = cformat("%.0f", 100 * exp(v / 100))   // ios: Views/AnalysisDetail.swift:1650

// ios: Views/AnalysisDetail.swift:1652
@Composable
private fun TableRow(k: String, a: String, b: String, c: String, e: String, head: Boolean = false) {
    val st = appFont(12f, if (head) FontWeight.Normal else FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"); val col = if (head) Theme.sub else Theme.ink   // ios: Views/AnalysisDetail.swift:1660
    @Composable fun Cell(t: String, md: Modifier, s: TextStyle = st) = FitText(t, s, 0.7f, md, maxLines = 1, color = col)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:1653
        Cell(k, Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1654
        Box(Modifier.width(58.dp), contentAlignment = Alignment.CenterEnd) { Cell(a, Modifier) }   // ios: Views/AnalysisDetail.swift:1655
        Box(Modifier.width(58.dp), contentAlignment = Alignment.CenterEnd) { Cell(b, Modifier, st.copy(fontWeight = if (head) FontWeight.Normal else FontWeight.Bold)) }   // ios: Views/AnalysisDetail.swift:1656
        Box(Modifier.width(58.dp), contentAlignment = Alignment.CenterEnd) { Cell(c, Modifier) }   // ios: Views/AnalysisDetail.swift:1657
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) { Cell(e, Modifier) }   // ios: Views/AnalysisDetail.swift:1658
    }
}

/** 누적 이익 띠 (오늘 = 100, 0~H년). 선·글자는 drawWithCache 에서 크기가 바뀔 때만 다시 만든다 */
// ios: Views/AnalysisDetail.swift:1665,1666,1667
@Composable
fun RateBandChart(cum: List<List<Double>>, modifier: Modifier = Modifier) {
    val teal = Theme.teal; val muted = Theme.muted; val ink = Theme.ink
    val tm = rememberTextMeasurer()
    Box(modifier.semantics { contentDescription = "S&P500 이익 띠 그래프" }   // ios: Views/AnalysisDetail.swift:1706
        .drawWithCache {   // ios: Views/AnalysisDetail.swift:1668
            val lv: List<List<Double>> = (0 until 3).map { k -> listOf(100.0) + cum.map { 100 * exp(it[k] / 100) } }   // ios: Views/AnalysisDetail.swift:1669
            val n = lv[1].size   // ios: Views/AnalysisDetail.swift:1670
            val all = lv.flatten()   // ios: Views/AnalysisDetail.swift:1671
            val hi = (all.maxOrNull() ?: 110.0) * 1.03; val lo = (all.minOrNull() ?: 90.0) * 0.97   // ios: Views/AnalysisDetail.swift:1672
            val x0 = 30.dp.toPx()   // ios: Views/AnalysisDetail.swift:1673 왼쪽 y축 눈금 자리
            val x = { i: Int -> i.toFloat() / max(1, n - 1) * (size.width - x0 - 8.dp.toPx()) + x0 }   // ios: Views/AnalysisDetail.swift:1674
            val y = { v: Double -> ((hi - v) / max(1e-9, hi - lo)).toFloat() * (size.height - 14.dp.toPx()) }   // ios: Views/AnalysisDetail.swift:1675
            // ios: Views/AnalysisDetail.swift:1676
            val area = Path().apply {
                for (i in 0 until n) { val pt = Offset(x(i), y(lv[2][i])); if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }   // ios: Views/AnalysisDetail.swift:1677
                for (i in (n - 1 downTo 0 step 1)) lineTo(x(i), y(lv[0][i]))   // ios: Views/AnalysisDetail.swift:1678
                close()   // ios: Views/AnalysisDetail.swift:1679
            }
            // y축 눈금 (오늘 = 100 기준 10 간격)
            val grid = Path(); val labels = ArrayList<Pair<Offset, TextLayoutResult>>()
            var t = ceil(lo / 10) * 10   // ios: Views/AnalysisDetail.swift:1683
            while (t <= hi) {   // ios: Views/AnalysisDetail.swift:1684
                grid.moveTo(x0, y(t)); grid.lineTo(size.width, y(t))   // ios: Views/AnalysisDetail.swift:1685
                // ios: Views/AnalysisDetail.swift:1687 (오른쪽 끝을 x0 − 4 에, 세로 가운데)
                val lay = tm.measure(cformat("%.0f", t), TextStyle(fontSize = 10.sp, color = muted)); labels.add(Offset(x0 - 4.dp.toPx() - lay.size.width, y(t) - lay.size.height / 2f) to lay)
                t += 10   // ios: Views/AnalysisDetail.swift:1688
            }
            val base = Path().apply { moveTo(x0, y(100.0)); lineTo(size.width, y(100.0)) }   // ios: Views/AnalysisDetail.swift:1690
            // 오늘 세로선
            val today = Path().apply { moveTo(x(0), 0f); lineTo(x(0), size.height - 14.dp.toPx()) }   // ios: Views/AnalysisDetail.swift:1693
            val todayL = tm.measure("오늘", TextStyle(fontSize = 10.sp, color = muted)); val todayAt = Offset(x(0), size.height - 6.dp.toPx() - todayL.size.height / 2f)   // ios: Views/AnalysisDetail.swift:1695
            val dashes = listOf(0, 2).map { k ->   // ios: Views/AnalysisDetail.swift:1696
                Path().apply { for (i in 0 until n) { val pt = Offset(x(i), y(lv[k][i])); if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) } }   // ios: Views/AnalysisDetail.swift:1697
            }
            val midP = Path().apply { for (i in 0 until n) { val pt = Offset(x(i), y(lv[1][i])); if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) } }   // ios: Views/AnalysisDetail.swift:1700
            val years = (1 until max(2, n)).filter { it < n }.map { i ->   // ios: Views/AnalysisDetail.swift:1702
                val l = tm.measure("${i}년", TextStyle(fontSize = 10.sp, color = muted)); Offset(x(i) - 12.dp.toPx() - l.size.width / 2f, size.height - 6.dp.toPx() - l.size.height / 2f) to l   // ios: Views/AnalysisDetail.swift:1703
            }
            onDrawBehind {
                drawPath(area, teal.copy(alpha = 0.16f))   // ios: Views/AnalysisDetail.swift:1681
                drawPath(grid, muted.copy(alpha = 0.25f), style = Stroke(0.5.dp.toPx()))   // ios: Views/AnalysisDetail.swift:1686
                labels.forEach { (at, lay) -> drawText(lay, topLeft = at) }
                drawPath(base, muted, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))   // ios: Views/AnalysisDetail.swift:1691
                drawPath(today, ink.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))   // ios: Views/AnalysisDetail.swift:1694
                drawText(todayL, topLeft = todayAt)
                dashes.forEach { drawPath(it, teal.copy(alpha = 0.6f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))) }   // ios: Views/AnalysisDetail.swift:1698
                drawPath(midP, teal, style = Stroke(2.dp.toPx()))   // ios: Views/AnalysisDetail.swift:1701
                years.forEach { (at, l) -> drawText(l, topLeft = at) }
            }
        })
}
