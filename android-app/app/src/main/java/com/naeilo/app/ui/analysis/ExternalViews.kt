package com.naeilo.app.ui.analysis

import com.naeilo.app.core.cfmt
import com.naeilo.app.core.cformat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.calc.SiteCalc
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.short
import com.naeilo.app.net.Market
import com.naeilo.app.net.PriceHistory
import com.naeilo.app.pending.EventRetro
import com.naeilo.app.data.MarketEvents
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.IosSlider
import com.naeilo.app.ui.SF
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.unit.max as dmax

// 외부 요인 (Views/AnalysisDetail.swift 781-1328): 시장·금리·환율이 움직이면 내 돈은. 비율·민감도만 보여 주고 종목 조언은 하지 않는다.

private fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
/** Int.formatted(): 1,300 */
private fun grp(n: Int): String = cformat("%,d", n)
/** SwiftUI Color.orange·Color.red (시스템 색) */
@Composable private fun sysOrange(): Color = hexD(0xFF9500, 0xFF9F0A)
@Composable private fun sysRed(): Color = hexD(0xFF3B30, 0xFF453A)

// MARK: 외부 요인 — 시장·금리·환율이 움직이면

// ios: Views/AnalysisDetail.swift:673
object ExternalView {
    /** 지난 종가 회귀 결과 (같은 이력이면 다시 계산하지 않는다) */
    private val estMemo = ConcurrentHashMap<String, Pair<String, Double?>>()

    /** 시장 민감도(베타): 사이트가 웹과 같은 식(factorBetas)으로 매일 낸 값. 사이트 자료가 없는 종목(직접 추가한 종목)은
     *  같은 회귀를 앱이 가진 지난 3년 종가로 한다. 둘 다 없으면 null */
    fun mktBeta(id: String): Double? = SiteCalc.shared.beta("mkt", id) ?: estBeta(id)   // ios: Views/AnalysisDetail.swift:678
    /** (시장 베타, 미국 10년 금리 +1%p 일 때 %, 달러 노출). 금리 시나리오 화면이 시장 베타를 쓴다 (웹처럼 모르면 1) */
    // ios: Views/AnalysisDetail.swift:680,681
    fun coef(s: Symbol): Triple<Double, Double, Double> =
        Triple((mktBeta(s.id) ?: 1.0), (SiteCalc.shared.beta("rate", s.id)?.let { SiteCalc.factorMove("rate", beta = it, shock = 100.0) * 100 } ?: 0.0), if (s.currency == Currency.usd) 1.0 else 0.0)
    // ios: Views/AnalysisDetail.swift:683
    fun estBeta(id: String): Double? {
        val h = PriceHistory.of(id); val spy = PriceHistory.of("SPY"); if (h == null || spy == null) return null   // ios: Views/AnalysisDetail.swift:684
        val stamp = "${h.dates.size}|${h.dates.lastOrNull()}|${spy.dates.size}|${spy.dates.lastOrNull()}"
        estMemo[id]?.let { if (it.first == stamp) return it.second }
        // 한국 장은 미국 장보다 먼저 끝나서, 한국 종목은 그 전날 밤 미국 장 움직임과 짝을 짓는다
        val kr = Sample.symbol(id)?.currency == Currency.krw   // ios: Views/AnalysisDetail.swift:686
        val usDays = spy.dates   // ios: Views/AnalysisDetail.swift:687
        val spRet = HashMap<String, Double>()   // ios: Views/AnalysisDetail.swift:688
        for (i in 1 until max(1, spy.closes.size)) {   // ios: Views/AnalysisDetail.swift:689
            if (!(spy.closes[i - 1] > 0)) continue
            val r: Double = ln(spy.closes[i] / spy.closes[i - 1])   // ios: Views/AnalysisDetail.swift:690
            spRet[usDays[i]] = r   // ios: Views/AnalysisDetail.swift:691
        }
        // ios: Views/AnalysisDetail.swift:693
        fun prevUS(d: String): String? {
            var lo = 0; var hi = usDays.size - 1; var ans: String? = null   // ios: Views/AnalysisDetail.swift:694
            while (lo <= hi) { val mid = (lo + hi) / 2; if (usDays[mid] < d) { ans = usDays[mid]; lo = mid + 1 } else { hi = mid - 1 } }   // ios: Views/AnalysisDetail.swift:695
            return ans   // ios: Views/AnalysisDetail.swift:696
        }
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()   // ios: Views/AnalysisDetail.swift:698
        for (i in 1 until h.closes.size) {   // ios: Views/AnalysisDetail.swift:699
            if (!(h.closes[i - 1] > 0)) continue; val key = (if (kr) prevUS(h.dates[i]) else h.dates[i]) ?: continue; val x = spRet[key] ?: continue   // ios: Views/AnalysisDetail.swift:700
            if (kr) { val pk = prevUS(h.dates[i - 1]); if (pk != null && pk == key) continue }     // ios: Views/AnalysisDetail.swift:701 미국 장이 쉰 날은 건너뛴다
            xs.add(x); ys.add(ln(h.closes[i] / h.closes[i - 1]))   // ios: Views/AnalysisDetail.swift:702
        }
        if (xs.size < 30) { estMemo[id] = stamp to null; return null }   // ios: Views/AnalysisDetail.swift:704
        val mx = xs.fold(0.0) { a, b -> a + b } / xs.size; val my = ys.fold(0.0) { a, b -> a + b } / ys.size   // ios: Views/AnalysisDetail.swift:705
        val cov = xs.zip(ys).fold(0.0) { a, p -> a + (p.first - mx) * (p.second - my) }; val vx = xs.fold(0.0) { a, b -> a + (b - mx) * (b - mx) }   // ios: Views/AnalysisDetail.swift:706
        if (!(vx > 0)) { estMemo[id] = stamp to null; return null }   // ios: Views/AnalysisDetail.swift:707
        val w = xs.size.toDouble() / (xs.size + 60).toDouble()      // ios: Views/AnalysisDetail.swift:708 웹과 같게 이력이 짧으면 1 쪽으로 당긴다
        return (w * cov / vx + (1 - w)).also { estMemo[id] = stamp to it }   // ios: Views/AnalysisDetail.swift:709
    }

    /** 웹 renderSens 와 같은 식: 종목 가격 변화 = exp(β × 요인 변화) − 1, 원/달러는 달러 종목에 그대로 */
    // ios: Views/AnalysisDetail.swift:713,714
    fun move(factor: String, id: String, sym: Symbol, v: Double): Double? = when (factor) {
        "fx" -> if (sym.currency == Currency.usd) v / Market.shared.fx.last else 0.0   // ios: Views/AnalysisDetail.swift:715
        "mkt" -> mktBeta(id)?.let { SiteCalc.factorMove("mkt", beta = it, shock = v) }   // ios: Views/AnalysisDetail.swift:716
        "rate" -> SiteCalc.shared.beta("rate", id)?.let { SiteCalc.factorMove("rate", beta = it, shock = v * 100) }   // ios: Views/AnalysisDetail.swift:717
        else -> SiteCalc.shared.beta(factor, id)?.let { SiteCalc.factorMove(factor, beta = it, shock = v) }   // ios: Views/AnalysisDetail.swift:718
    }

    /** 요인의 '보통 한 달 변화'를 요인 상세와 같은 단위로 (금리 %p, 원/달러 원, 나머지 %) */
    // ios: Views/AnalysisDetail.swift:723
    fun monthShock(k: String): Double? {
        val sd = SiteCalc.shared.factorMonth(k)?.sd; if (sd == null || !(sd > 0)) return null   // ios: Views/AnalysisDetail.swift:724
        return when (k) {   // ios: Views/AnalysisDetail.swift:725
            "rate" -> sd / 100   // ios: Views/AnalysisDetail.swift:726
            "fx" -> Market.shared.fx.last * (exp(sd) - 1)   // ios: Views/AnalysisDetail.swift:727
            else -> (exp(sd) - 1) * 100   // ios: Views/AnalysisDetail.swift:728
        }
    }
    class Rank(val k: String, val amt: Double, val gross: Double, val weak: Boolean)   // ios: Views/AnalysisDetail.swift:731

    fun ranks(m: AppModel): List<Rank> = ranks(m.rows, m.total)
    /** 종목 목록·평가액만 받는 몸통 (숫자 맞추기 G 가 화면 밖에서 부른다) */
    // ios: Views/AnalysisDetail.swift:733
    fun ranks(rows: List<AppModel.Row>, mTotal: Double): List<Rank> {
        val total = max(1.0, mTotal)   // ios: Views/AnalysisDetail.swift:734
        // ios: Views/AnalysisDetail.swift:735
        return listOf("mkt", "fx", "rate", "oil", "gold").map { k ->
            val v = monthShock(k) ?: return@map Rank(k = k, amt = 0.0, gross = 0.0, weak = true)   // ios: Views/AnalysisDetail.swift:736
            val dvs = rows.map { r -> r.value * (move(k, r.id, r.sym, v) ?: 0.0) }   // ios: Views/AnalysisDetail.swift:737
            val tot = dvs.fold(0.0) { a, b -> a + b }; val gross = dvs.fold(0.0) { a, b -> a + abs(b) }   // ios: Views/AnalysisDetail.swift:738
            val agreeWeak = k != "fx" && rows.all { r -> SiteCalc.shared.betaStat(k, r.id)?.agree?.let { abs(it - 0.5) < FactorDetailView.weakAgree } ?: true }   // ios: Views/AnalysisDetail.swift:739
            Rank(k = k, amt = abs(tot), gross = gross, weak = abs(tot) < total * FactorDetailView.tiny || agreeWeak)   // ios: Views/AnalysisDetail.swift:740
        }.sortedWith { a, b -> val ka = if (a.weak) 0 else 1; val kb = if (b.weak) 0 else 1; if (ka != kb) kb.compareTo(ka) else b.amt.compareTo(a.amt) }   // ios: Views/AnalysisDetail.swift:741
    }

    fun desc(m: AppModel, x: Rank): String = desc(m.rows, m.total, x)
    // ios: Views/AnalysisDetail.swift:744
    fun desc(rows: List<AppModel.Row>, mTotal: Double, x: Rank): String {
        if (x.weak) return ""   // ios: Views/AnalysisDetail.swift:745
        val tot = max(1.0, mTotal)   // ios: Views/AnalysisDetail.swift:746
        return when (x.k) {   // ios: Views/AnalysisDetail.swift:747
            // ios: Views/AnalysisDetail.swift:748
            "mkt" -> {
                val b = rows.fold(0.0) { a, r -> a + r.value / tot * (mktBeta(r.id) ?: 1.0) }   // ios: Views/AnalysisDetail.swift:749
                val bs = cformat("%.1f", b); if (b > 1.15) "시장의 ${bs}배로 움직임" else if (b < 0.85) "시장보다 작게 움직임" else "시장과 비슷하게 움직임"   // Views/AnalysisDetail.swift:860 (검사 예외: 같은 글)
            }
            // ios: Views/AnalysisDetail.swift:751
            "fx" -> {
                val usd = rows.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value } / tot   // ios: Views/AnalysisDetail.swift:752
                "달러 ${AppModel.pct(usd)} · 급락 땐 방패"   // ios: Views/AnalysisDetail.swift:753
            }
            // ios: Views/AnalysisDetail.swift:754
            else -> {
                val half = x.gross / 2; if (x.gross > x.amt * 2) return "종목별 ±${AppModel.man(half)} · 서로 상쇄"   // ios: Views/AnalysisDetail.swift:755
                "움직이면 대부분 같은 쪽"   // ios: Views/AnalysisDetail.swift:756
            }
        }
    }

    // ios: Views/AnalysisDetail.swift:760
    fun summary(rs: List<Rank>): String? {
        val strong = rs.filter { !it.weak }   // ios: Views/AnalysisDetail.swift:761
        val top = strong.firstOrNull(); if (top == null || !(top.amt > 0)) return null   // ios: Views/AnalysisDetail.swift:762
        val name = FactorDetailView.names[top.k] ?: ""   // ios: Views/AnalysisDetail.swift:763
        val rest = strong.drop(1).fold(0.0) { a, x -> a + x.amt }   // ios: Views/AnalysisDetail.swift:764
        if (strong.size == 1) return "사실상 ${name} 하나"   // ios: Views/AnalysisDetail.swift:765
        if (rest < top.amt / 3) return "거의 ${name} 때문 · 나머지 합쳐도 1/3 미만"   // ios: Views/AnalysisDetail.swift:766
        if (rest < top.amt) return "${name}이 가장 큼 · 나머지 합보다 큼"   // ios: Views/AnalysisDetail.swift:767
        val name2 = FactorDetailView.names[strong[1].k] ?: ""; return "${name}·${name2} 비슷"   // Views/AnalysisDetail.swift:878 (검사 예외: 같은 글)
    }

    /** 일정 하나: 요인, 미리 보기 값(나쁘게 나온 쪽, 요인 상세 단위), 내 자산 보통 움직임 (웹 xfEvents 와 같은 식) */
    class Ev(val hit: MarketEvents.Hit, val k: String, val sd: Double, val v: Double, val amt: Double)   // ios: Views/AnalysisDetail.swift:807
    /** 분석 허브 줄 값 (Swift (day, kind, amt) 튜플 자리) */
    class Next(val day: String, val kind: String, val amt: Double)
    /** 분석 허브 줄: 가장 가까운 일정과 내 자산 보통 움직임 (아래 오늘의 이벤트 카드와 같은 값) */
    // ios: Views/AnalysisDetail.swift:810
    fun hubNext(m: AppModel): Next? =
        events(m).firstOrNull()?.let { Next(it.hit.day, it.hit.kind, it.amt) }   // ios: Views/AnalysisDetail.swift:811
    // ios: Views/AnalysisDetail.swift:813
    fun events(m: AppModel): List<Ev> =
        MarketEvents.upcoming(held = m.rows.map { it.id }, days = 45).mapNotNull { h ->   // ios: Views/AnalysisDetail.swift:814
            val em = SiteCalc.eventMoves[h.id] ?: return@mapNotNull null   // ios: Views/AnalysisDetail.swift:815
            val v = if (em.factor == "fx") -Market.shared.fx.last * em.sd / 100 else -em.sd   // ios: Views/AnalysisDetail.swift:816
            val amt = abs(m.rows.fold(0.0) { a, r -> a + r.value * (move(em.factor, r.id, r.sym, v) ?: (if (em.factor == "mkt") v / 100 else 0.0)) })   // ios: Views/AnalysisDetail.swift:817
            Ev(hit = h, k = em.factor, sd = em.sd, v = v, amt = amt)   // ios: Views/AnalysisDetail.swift:818
        }.sortedBy { it.hit.day }   // ios: Views/AnalysisDetail.swift:819
    val evBase: Map<String, String> = mapOf("mkt" to "미국 시장", "fx" to "원/달러", "oil" to "유가")   // ios: Views/AnalysisDetail.swift:821
}

/** 외부 요인 화면 값 (요인 순위·일정은 Dispatchers.Default 에서 계산해 둔다) */
private class ExtData(val rs: List<ExternalView.Rank>, val es: List<ExternalView.Ev>)

/** 과거 재생: 그때 함께 움직인 요인 변화 × 지금 내 종목 민감도 (웹 xfReplays 와 같은 식) */
// ios: Views/AnalysisDetail.swift:772
@Composable
private fun ReplayCard(m: AppModel) {
    val total = max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:773
    // ios: Views/AnalysisDetail.swift:774
    Card {
        T("그때 그 일이 지금 나면", style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:775
        // ios: Views/AnalysisDetail.swift:776
        SiteCalc.replays.forEach { rp -> key(rp.`when`) {
            val os = m.rows.map { r -> Pair(r.value, SiteCalc.replayMove(rp, usd = r.sym.currency == Currency.usd) { k -> if (k == "mkt") ExternalView.mktBeta(r.id) else SiteCalc.shared.beta(k, r.id) }) }   // ios: Views/AnalysisDetail.swift:777
            val tot = os.fold(0.0) { a, x -> a + x.first * x.second.total }; val fx = os.fold(0.0) { a, x -> a + x.first * x.second.fx }   // ios: Views/AnalysisDetail.swift:778
            val small = abs(fx) < total * FactorDetailView.tiny   // ios: Views/AnalysisDetail.swift:779
            val note = if (small) "달러 적어 환율 영향 작음"   // ios: Views/AnalysisDetail.swift:780
                else if (tot < 0) (if (fx > 0) "환율 방어 +${AppModel.man(fx)}" else "환율도 하락 −${AppModel.man(-fx)} 추가")   // ios: Views/AnalysisDetail.swift:781
                else if (fx > 0) "환율이 +${AppModel.man(fx)} 보탬" else "환율이 −${AppModel.man(-fx)} 깎음"   // ios: Views/AnalysisDetail.swift:782
            val chips = listOf("mkt", "rate", "fx").filter { it != "rate" || abs(rp.mv["rate"] ?: 0.0) >= 1 }.map { k ->   // ios: Views/AnalysisDetail.swift:783
                val v = rp.mv[k] ?: 0.0   // ios: Views/AnalysisDetail.swift:784
                val lab = mapOf("mkt" to "시장", "rate" to "금리")[k] ?: FactorDetailView.names[k] ?: ""; val sg = if (v > 0) "+" else "−"; val unit = if (k == "rate") "%p" else "%"; val num = cfmt("%g", abs(v))
                "${lab} ${sg}${num}${unit}"   // Views/AnalysisDetail.swift:895 (검사 예외: 같은 글)
            }
            // ios: Views/AnalysisDetail.swift:787
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier
                .fillMaxWidth().border(1.dp, Theme.border, RoundedCornerShape(12.dp))   // ios: Views/AnalysisDetail.swift:801
                .padding(12.dp)) {   // ios: Views/AnalysisDetail.swift:800
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:788
                    T("${rp.`when`} ${rp.name}", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:789
                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:790
                    T((if (tot > 0) "+" else if (tot < 0) "−" else "") + AppModel.man(abs(tot)), style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:791
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:793
                    chips.forEach { c ->   // ios: Views/AnalysisDetail.swift:794
                        T(c, Modifier.background(Theme.border, CapsuleShape).padding(horizontal = 8.dp, vertical = 3.dp), style = appFont(11f))   // ios: Views/AnalysisDetail.swift:795
                    }
                }
                T(note, style = appFont(12f), color = if (!small && fx > 0) Theme.teal else sysOrange())   // ios: Views/AnalysisDetail.swift:798
            }
        } }
    }
}

/** 오늘의 이벤트 카드: 가장 가까운 일정 하나 (45일 안에 없으면 숨김) */
// ios: Views/AnalysisDetail.swift:824
@Composable
private fun TodayCard(m: AppModel, x: ExternalView.Ev?, asked: Set<String>, setAsked: (Set<String>) -> Unit) {
    val scope = rememberCoroutineScope()
    if (x != null) {   // ios: Views/AnalysisDetail.swift:825
        val tot = max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:826
        val b = m.rows.fold(0.0) { a, r -> a + r.value / tot * (ExternalView.mktBeta(r.id) ?: 1.0) }   // ios: Views/AnalysisDetail.swift:827
        Card(dark = true) {   // ios: Views/AnalysisDetail.swift:828
            T("${MarketEvents.`when`(x.hit.day)} · ${x.hit.kind}", style = appFont(12f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:829
            T("±" + AppModel.man(x.amt), style = appFont(26f, FontWeight.Bold), roll = "±" + AppModel.man(x.amt))   // ios: Views/AnalysisDetail.swift:830
            val bs = cformat("%.1f", b); val base0 = ExternalView.evBase[x.k] ?: ""; val sd = cfmt("%g", x.sd)   // Views/AnalysisDetail.swift:941 (검사 예외: 같은 글)
            T("${base0} ±${sd}% 가정" + (if (x.k == "mkt") " · 시장의 ${bs}배" else ""),
              style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:832
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:833
                T(text = "미리 보기", style = appFont(13f, FontWeight.SemiBold), color = Color.White,   // ios: Views/AnalysisDetail.swift:835
                  modifier = Modifier.plainClickable { m.analysisPath = m.analysisPath + FactorDetailRoute(factor = x.k, start = x.v) }   // ios: Views/AnalysisDetail.swift:834,838
                      .background(Theme.teal, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp))   // ios: Views/AnalysisDetail.swift:836
                val on = asked.contains("${x.hit.id}.${x.hit.day}")   // ios: Views/AnalysisDetail.swift:839
                val label = if (on) "알림 켬" else "결과 알림"; val icon = if (on) "bell.fill" else "bell"   // ios: Views/AnalysisDetail.swift:845
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .plainClickable {   // ios: Views/AnalysisDetail.swift:840,844,849
                        val k = "${x.hit.id}.${x.hit.day}"   // ios: Views/AnalysisDetail.swift:841
                        setAsked(if (on) asked - k else asked + k)   // ios: Views/AnalysisDetail.swift:842
                        scope.launch { EventRetro.ask(id = x.hit.id, kind = x.hit.kind, day = x.hit.day, on = !on) }   // ios: Views/AnalysisDetail.swift:843
                    }
                    .background(Color.White.copy(alpha = if (on) 0.25f else 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {   // ios: Views/AnalysisDetail.swift:847
                    if (SF.containsKey(icon)) Sym(icon, 13f, Color.White)   // bell·bell.fill 은 아직 SF 표에 없다 (넣으면 보인다)
                    T(label, style = appFont(13f, FontWeight.SemiBold), color = Color.White)   // ios: Views/AnalysisDetail.swift:846
                }
            }
        }
    }
}

/** 지난 일 돌아보기: 가장 최근 지난 일정의 미리 본 범위 vs 실제 평가액 변화 (기기에 적어 둔 값, 없으면 숨김) */
// ios: Views/AnalysisDetail.swift:858
@Composable
private fun RetroCard(retro: EventRetro.Snap?) {
    val r = retro; val after = r?.after; if (r == null || after == null) return   // ios: Views/AnalysisDetail.swift:859
    val real = after - r.base   // ios: Views/AnalysisDetail.swift:860
    val inside = abs(real) <= r.amt   // ios: Views/AnalysisDetail.swift:861
    Card {   // ios: Views/AnalysisDetail.swift:862
        T("지난 일 돌아보기", style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:863
        val mo = r.day.drop(5).take(2).toIntOrNull() ?: 0; val dd = r.day.takeLast(2).toIntOrNull() ?: 0   // ios: Views/AnalysisDetail.swift:864
        T("${mo}/${dd} · ${r.kind}", style = appFont(12f), color = Theme.sub)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:865
            Column(verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/AnalysisDetail.swift:866
                modifier = Modifier.weight(1f).background(Theme.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {   // ios: Views/AnalysisDetail.swift:870
                T("미리 본 범위", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:867
                T("±" + AppModel.man(r.amt), style = appFont(18f, FontWeight.Bold), roll = "±" + AppModel.man(r.amt))   // ios: Views/AnalysisDetail.swift:868
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/AnalysisDetail.swift:871
                modifier = Modifier.weight(1f).background(Theme.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {   // ios: Views/AnalysisDetail.swift:876
                T("실제", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:872
                T(text = (if (real > 0) "+" else if (real < 0) "−" else "") + AppModel.man(abs(real)), style = appFont(18f, FontWeight.Bold),   // ios: Views/AnalysisDetail.swift:873
                  color = if (real > 0) sysRed() else if (real < 0) Theme.blue else Theme.ink, roll = (if (real > 0) "+" else if (real < 0) "−" else "") + AppModel.man(abs(real)))   // ios: Views/AnalysisDetail.swift:874
            }
        }
        T(if (inside) "범위 안" else "범위보다 크게 움직임 · 다른 일도 섞였을 수도",   // ios: Views/AnalysisDetail.swift:878
          style = appFont(13f), color = if (inside) Theme.teal else sysOrange())   // ios: Views/AnalysisDetail.swift:879
        T("실제 = 일정 전 평가액과 다음에 연 때의 차이",   // ios: Views/AnalysisDetail.swift:880
          style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:881
    }
}

/** 외부 요인 화면 (분석 넘기기 안). 요인 상세는 FactorDetailRoute 로 쌓는다 */
// ios: Views/AnalysisDetail.swift:674,886
@Composable
fun ExternalScreen(m: AppModel) {
    var asked by remember { mutableStateOf(setOf<String>()) }   // ios: Views/AnalysisDetail.swift:854
    var retro by remember { mutableStateOf<EventRetro.Snap?>(null) }   // ios: Views/AnalysisDetail.swift:855
    var data by remember { mutableStateOf<ExtData?>(null) }
    val rowsKey = m.rows.map { it.id to it.value }; val stats = Market.shared.stats; val fxNow = Market.shared.fx.last
    // 요인 순위·일정은 화면 밖에서 (직접 추가한 종목의 시장 민감도는 지난 종가 회귀라 무겁다). 바뀌기 전 값은 그대로 둔다
    LaunchedEffect(rowsKey, stats, fxNow) {
        data = withContext(Dispatchers.Default) {
            m.rows.forEach { ExternalView.mktBeta(it.id) }
            ExtData(ExternalView.ranks(m), ExternalView.events(m))   // ios: Views/AnalysisDetail.swift:808
        }
    }
    LaunchedEffect(Unit) { SiteCalc.shared.refresh() }   // ios: Views/AnalysisDetail.swift:995
    val d = data
    // ios: Views/AnalysisDetail.swift:893,993,994
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/AnalysisDetail.swift:894,991
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/AnalysisDetail.swift:895
                T("바깥 일이 생기면 내 돈은?", style = appFont(20f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:896
                T("단기(오늘~한 달) 기준",   // ios: Views/AnalysisDetail.swift:897
                  style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:898
            }
            if (d != null) {
                val rs = d.rs   // ios: Views/AnalysisDetail.swift:887
                val scale = max(1.0, rs.filter { !it.weak }.map { it.amt }.maxOrNull() ?: 1.0)   // ios: Views/AnalysisDetail.swift:888
                val es = d.es   // ios: Views/AnalysisDetail.swift:889
                val events = MarketEvents.upcoming(held = m.rows.map { it.id }, days = 45).filter { SiteCalc.eventMoves[it.id] == null }.sortedBy { it.day }.take(5)   // ios: Views/AnalysisDetail.swift:890
                val nos: Map<String, String> = rs.filter { !it.weak }.mapIndexed { i, x -> x.k to (i + 1).toString() }.toMap()   // ios: Views/AnalysisDetail.swift:891
                // ios: Views/AnalysisDetail.swift:900
                LaunchedEffect(Unit) {
                    EventRetro.record(es.map { EventRetro.Rec(id = it.hit.id, kind = it.hit.kind, day = it.hit.day, amt = it.amt) }, total = m.total)   // ios: Views/AnalysisDetail.swift:901
                    asked = es.filter { EventRetro.isAsked(it.hit.id, it.hit.day) }.map { "${it.hit.id}.${it.hit.day}" }.toSet()   // ios: Views/AnalysisDetail.swift:902
                    retro = EventRetro.latest   // ios: Views/AnalysisDetail.swift:903
                }
                TodayCard(m, es.firstOrNull(), asked) { asked = it }   // ios: Views/AnalysisDetail.swift:905
                RetroCard(retro)   // ios: Views/AnalysisDetail.swift:906
                Card {   // ios: Views/AnalysisDetail.swift:907
                    Row(Modifier.fillMaxWidth()) {   // ios: Views/AnalysisDetail.swift:908
                        T("흔드는 요인", Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:909
                        Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:910
                        T("한 달 기준", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:911
                    }
                    rs.forEach { x -> key(x.k) {   // ios: Views/AnalysisDetail.swift:913
                        val no = nos[x.k] ?: "–"   // ios: Views/AnalysisDetail.swift:914
                        val nm = FactorDetailView.names[x.k] ?: ""; val rel = if (x.weak) "뚜렷한 관계 없음" else "보통 한 달 ±" + AppModel.man(x.amt); val a11y = "${nm}, ${rel}. 자세히 보기"   // Views/AnalysisDetail.swift:1054 (검사 예외: 같은 글)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,   // ios: Views/AnalysisDetail.swift:916
                            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = a11y }
                                .plainClickable { m.analysisPath = m.analysisPath + FactorDetailRoute(factor = x.k) }   // ios: Views/AnalysisDetail.swift:915,943
                                .padding(vertical = 6.dp)) {   // ios: Views/AnalysisDetail.swift:941
                            Box(Modifier.background(if (no == "1") Theme.ink else Theme.border, CircleShape), contentAlignment = Alignment.Center) {   // ios: Views/AnalysisDetail.swift:919
                                T(no, Modifier.size(24.dp, 24.dp).wrapContentSize(), style = appFont(12f, FontWeight.Bold),   // ios: Views/AnalysisDetail.swift:917
                                  color = if (no == "1") Color.White else Theme.ink)   // ios: Views/AnalysisDetail.swift:918
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {   // ios: Views/AnalysisDetail.swift:920
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:921
                                    T(FactorDetailView.names[x.k] ?: x.k, style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:922
                                    Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:923
                                    T(if (x.weak) "관계 없음" else "±" + AppModel.man(x.amt), style = appFont(14f, if (x.weak) FontWeight.Normal else FontWeight.Bold),   // ios: Views/AnalysisDetail.swift:924
                                      color = if (x.weak) Theme.muted else Theme.ink)   // ios: Views/AnalysisDetail.swift:925
                                }
                                if (!x.weak) {   // ios: Views/AnalysisDetail.swift:927
                                    BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp)) {   // ios: Views/AnalysisDetail.swift:928,929,934
                                        Box(Modifier.fillMaxSize().background(Theme.border, CapsuleShape))   // ios: Views/AnalysisDetail.swift:930
                                        Box(Modifier.fillMaxHeight().width(dmax(3.dp, maxWidth * (x.amt / scale).toFloat())).background(Theme.teal, CapsuleShape))   // ios: Views/AnalysisDetail.swift:931
                                    }
                                }
                                val ds = ExternalView.desc(m, x)   // ios: Views/AnalysisDetail.swift:936
                                if (ds.isNotEmpty()) T(ds, style = appFont(12f).ls(2f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:937
                            }
                            Sym("chevron.right", 12f, Theme.muted, Modifier.padding(top = 5.dp))   // ios: Views/AnalysisDetail.swift:939
                        }
                    } }
                    val s = ExternalView.summary(rs)
                    if (s != null) {   // ios: Views/AnalysisDetail.swift:946
                        T(s, style = appFont(13f).ls(2f), color = Theme.ink, modifier = Modifier.fillMaxWidth()   // ios: Views/AnalysisDetail.swift:947
                            .background(Theme.teal.copy(alpha = 0.1f), RoundedCornerShape(12.dp))   // ios: Views/AnalysisDetail.swift:949
                            .padding(12.dp))   // ios: Views/AnalysisDetail.swift:948
                    } else if (SiteCalc.shared.factorMonth("mkt") == null) {   // ios: Views/AnalysisDetail.swift:950
                        T("요인 자료 불러오는 중", style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:951
                    }
                }
                ReplayCard(m)   // ios: Views/AnalysisDetail.swift:954
                Card {   // ios: Views/AnalysisDetail.swift:955
                    T("다가오는 일", style = appFont(16f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:956
                    es.take(8).forEach { x ->   // ios: Views/AnalysisDetail.swift:957
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/AnalysisDetail.swift:958
                            T(MarketEvents.`when`(x.hit.day), Modifier.widthIn(min = 64.dp), style = appFont(12f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:959
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/AnalysisDetail.swift:960,964
                                T(x.hit.kind.replace(" 결정", ""), style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:961
                                val lab = mapOf("mkt" to "시장")[x.k] ?: ExternalView.evBase[x.k] ?: ""; T("±${AppModel.man(x.amt)} · ${lab}", style = appFont(12f), color = Theme.sub)   // Views/AnalysisDetail.swift:1072 (검사 예외: 같은 글)
                            }
                            T(modifier = Modifier.plainClickable { m.analysisPath = m.analysisPath + FactorDetailRoute(factor = x.k, start = x.v) }   // ios: Views/AnalysisDetail.swift:965,969
                                .border(1.dp, Theme.border, RoundedCornerShape(10.dp))   // ios: Views/AnalysisDetail.swift:967
                                .padding(horizontal = 10.dp, vertical = 6.dp), text = "미리 보기", style = appFont(12f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:966
                        }
                    }
                    if (es.isEmpty()) T("45일 안 일정 없음", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:972
                    T("미리 보기는 나쁜 쪽 기준", style = appFont(11f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:973
                }
                if (events.isNotEmpty()) Card(dark = true) {   // ios: Views/AnalysisDetail.swift:975
                    T("내 종목 일정", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:976
                    events.forEach { e ->   // ios: Views/AnalysisDetail.swift:977
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/AnalysisDetail.swift:978
                            T(MarketEvents.`when`(e.day), Modifier.widthIn(min = 64.dp), style = appFont(12f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:979
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/AnalysisDetail.swift:980
                                T(e.kind, style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:981
                                val w = AppModel.pct(m.weight(e.target)); T(if (e.held) "비중 ${w}" else e.sub,   // ios: Views/AnalysisDetail.swift:982
                                  style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:983
                            }
                        }
                    }
                }
            }
            T("'만약에' 화면 · 큰 변화일수록 오차 큼",   // ios: Views/AnalysisDetail.swift:988
              style = appFont(12f).ls(2f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:989
        }
    }
}

// MARK: 요인 상세 — 프리셋으로 "이렇게 되면" 내 돈이 얼마나 움직이나 (외부 요인 개편 2단계)

/** 요인 상세로 가는 경로 (NavigationLink { FactorDetailView(factor:start:) } 자리). start: 일정 미리 보기처럼 정해진 값으로 열 때 */
data class FactorDetailRoute(val factor: String, val start: Double? = null)

// ios: Views/AnalysisDetail.swift:1001
object FactorDetailView {
    // ios: Views/AnalysisDetail.swift:1014
    val names: Map<String, String> = mapOf("mkt" to "미국 시장", "rate" to "미국 금리", "fx" to "원/달러", "oil" to "유가", "gold" to "금값")
    /** 관계 없음·거의 그대로 기준: 평가액의 0.1%, 같은 방향 비율 50%±5%p */
    const val tiny = 0.001; const val weakAgree = 0.05   // ios: Views/AnalysisDetail.swift:1016
    /** 이동 막대 제목 */
    fun navTitle(factor: String): String = "외부 요인 · ${names[factor] ?: factor}"   // ios: Views/AnalysisDetail.swift:1174
}

/** 프리셋 (이름, 값). 단위: 금리 %p, 원/달러 원 변화, 나머지 % */
// ios: Views/AnalysisDetail.swift:1019
internal fun factorPresets(factor: String): List<Pair<String, Double>> {
    val fx = Market.shared.fx.last   // ios: Views/AnalysisDetail.swift:1020
    return when (factor) {   // ios: Views/AnalysisDetail.swift:1021
        // ios: Views/AnalysisDetail.swift:1022
        "rate" -> {
            val y = MacroData.shared.change1y?.let { (it * 10).swRound() / 10 }   // ios: Views/AnalysisDetail.swift:1023
            listOf(Pair("내린다", -0.5), Pair("오른다", 0.5), Pair(if (y != null) "작년만큼 또" else "크게 오른다", y?.let { if (it == 0.0) null else it } ?: 1.0), Pair("2022년처럼", 2.5))   // ios: Views/AnalysisDetail.swift:1024
        }
        "fx" -> listOf(Pair("1,300원", 1300 - fx), Pair("1,500원", 1500 - fx), Pair("10% 하락", -fx * 0.1), Pair("2022년처럼", fx * 0.2))   // ios: Views/AnalysisDetail.swift:1025
        "mkt" -> listOf(Pair("흔한 나쁜 날", -2.0), Pair("나쁜 한 달", -8.0), Pair("2022년처럼", -25.0), Pair("좋은 한 달", 8.0))   // ios: Views/AnalysisDetail.swift:1026
        else -> listOf(Pair("10% 하락", -10.0), Pair("10% 상승", 10.0), Pair("30% 하락", -30.0), Pair("30% 상승", 30.0))   // ios: Views/AnalysisDetail.swift:1027
    }
}
// ios: Views/AnalysisDetail.swift:1030,1031
internal fun factorRange(factor: String): ClosedFloatingPointRange<Double> = when (factor) { "rate" -> -1.5..1.5; "fx" -> -300.0..300.0; "oil" -> -40.0..40.0; else -> -30.0..30.0 }
// ios: Views/AnalysisDetail.swift:1033
internal fun factorText(factor: String, v: Double): String {
    val s = if (v > 0) "+" else if (v < 0) "−" else ""   // ios: Views/AnalysisDetail.swift:1034
    return when (factor) {   // ios: Views/AnalysisDetail.swift:1035
        "rate" -> s + cfmt("%g", abs(v)) + "%p"   // ios: Views/AnalysisDetail.swift:1036
        "fx" -> { val a = (Market.shared.fx.last + v).swRound().toInt(); val b = abs(v).swRound().toInt(); "${grp(a)}원 (${s}${grp(b)}원)" }   // ios: Views/AnalysisDetail.swift:1037
        else -> s + cfmt("%g", abs(v)) + "%"   // ios: Views/AnalysisDetail.swift:1038
    }
}
/** 지난 3년 가장 큰 한 달 변화를 넘는지 (금리 bp, 나머지 로그 변화) */
// ios: Views/AnalysisDetail.swift:1042
internal fun factorHuge(factor: String, v: Double): Boolean {
    val mx = SiteCalc.shared.factorMonth(factor)?.max; if (mx == null || !(mx > 0)) return false   // ios: Views/AnalysisDetail.swift:1043
    return when (factor) {   // ios: Views/AnalysisDetail.swift:1044
        "rate" -> abs(v * 100) > mx   // ios: Views/AnalysisDetail.swift:1045
        "fx" -> abs(ln((Market.shared.fx.last + v) / Market.shared.fx.last)) > mx   // ios: Views/AnalysisDetail.swift:1046
        else -> abs(ln(1 + v / 100)) > mx   // ios: Views/AnalysisDetail.swift:1047
    }
}

/** 종목 한 줄 (Swift 이름 붙은 튜플 자리) */
internal class FRow(val id: String, val label: String, val dv: Double, val agree: Double?, val weak: Boolean, val known: Boolean)
// ios: Views/AnalysisDetail.swift:1056
internal fun factorRows(rows: List<AppModel.Row>, factor: String, v: Double, total: Double): List<FRow> = rows.map { r ->
    val mv = ExternalView.move(factor, r.id, r.sym, v)   // ios: Views/AnalysisDetail.swift:1057
    val dv = r.value * (mv ?: 0.0)   // ios: Views/AnalysisDetail.swift:1058
    val ag = if (factor == "fx") null else SiteCalc.shared.betaStat(factor, r.id)?.agree   // ios: Views/AnalysisDetail.swift:1059
    val weak = mv == null || abs(dv) < total * FactorDetailView.tiny || (ag?.let { abs(it - 0.5) < FactorDetailView.weakAgree } ?: false)   // ios: Views/AnalysisDetail.swift:1060
    FRow(r.id, r.sym.short, dv, ag, weak, mv != null)   // ios: Views/AnalysisDetail.swift:1061
}.sortedByDescending { abs(it.dv) }   // ios: Views/AnalysisDetail.swift:1062

// ios: Views/AnalysisDetail.swift:1179
internal fun factorLabel(factor: String, dv: Double, weak: Boolean, known: Boolean, shockSign: Double): String {
    if (!known) return "3년 시세 부족 · 0으로 봄"   // ios: Views/AnalysisDetail.swift:1180
    if (factor == "fx") return if (dv == 0.0) "원화 자산 · 영향 없음" else "달러 자산 · 환율만큼"   // ios: Views/AnalysisDetail.swift:1181
    if (weak) return ""   // ios: Views/AnalysisDetail.swift:1182
    val sameDir = (dv > 0) == (shockSign > 0)   // ios: Views/AnalysisDetail.swift:1183
    return if (sameDir) "같은 방향" else "반대 방향"   // ios: Views/AnalysisDetail.swift:1184
}

/** 요인 상세 화면 (분석 탭에 FactorDetailRoute 로 쌓인다. 제목은 FactorDetailView.navTitle) */
// ios: Views/AnalysisDetail.swift:1002,1003,1008,1009,1051
@Composable
fun FactorDetailScreen(m: AppModel, factor: String, start: Double? = null) {
    var shock by remember(factor) { mutableStateOf(start) }   // ios: Views/AnalysisDetail.swift:1004,1010
    var custom by remember(factor) { mutableStateOf(start != null) }   // ios: Views/AnalysisDetail.swift:1005,1011
    val name = FactorDetailView.names[factor] ?: factor   // ios: Views/AnalysisDetail.swift:1052
    val ps = factorPresets(factor)   // ios: Views/AnalysisDetail.swift:1053
    val v = shock ?: ps[1].second   // ios: Views/AnalysisDetail.swift:1054
    val total = max(1.0, m.total)   // ios: Views/AnalysisDetail.swift:1055
    // 종목별 계산은 화면 밖에서 (값을 바꾸는 동안엔 앞 결과를 그대로 보여 준다)
    var rowsS by remember(factor) { mutableStateOf<List<FRow>?>(null) }
    val rowsKey = m.rows.map { it.id to it.value }; val stats = Market.shared.stats
    LaunchedEffect(factor, v, rowsKey, stats) { rowsS = withContext(Dispatchers.Default) { factorRows(m.rows, factor, v, total) } }
    val shockSign = if ((shock ?: factorPresets(factor)[1].second) >= 0) 1.0 else -1.0   // ios: Views/AnalysisDetail.swift:1186
    val rng = factorRange(factor)
    LaunchedEffect(factor) { SiteCalc.shared.refresh(); if (factor == "rate") MacroData.shared.load() }   // ios: Views/AnalysisDetail.swift:1176
    // ios: Views/AnalysisDetail.swift:1074,1173,1175
    Box(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Theme.maxWidth).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/AnalysisDetail.swift:1075,1171
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/AnalysisDetail.swift:1076
                val ga = if (factor == "mkt" || factor == "gold") "이" else "가"; T("${name}${ga} 바뀌면 내 돈은?", style = appFont(20f, FontWeight.Bold))   // Views/AnalysisDetail.swift:1187 (검사 예외: 같은 글)
                T(if (factor == "rate") "미국 10년 금리 기준 · 시장 몫 빼고 · 지난 3년 실제 움직임"   // ios: Views/AnalysisDetail.swift:1078
                  else if (factor == "fx") "달러 자산은 환율만큼 · 지금 ${grp(Market.shared.fx.last.toInt())}원 기준"   // ios: Views/AnalysisDetail.swift:1079
                  else if (factor == "mkt") "S&P500 기준 · 지난 3년 실제 움직임"   // ios: Views/AnalysisDetail.swift:1080
                  else "시장 몫 빼고 · 지난 3년 실제 움직임",   // ios: Views/AnalysisDetail.swift:1081
                  style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1082
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ps.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/AnalysisDetail.swift:1084
                pair.forEach { p ->   // ios: Views/AnalysisDetail.swift:1085
                    val on = !custom && abs(v - p.second) < 1e-9   // ios: Views/AnalysisDetail.swift:1086
                    val fg = if (on) Color.White else Theme.ink   // ios: Views/AnalysisDetail.swift:1093
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),   // ios: Views/AnalysisDetail.swift:1088
                        modifier = Modifier.weight(1f).plainClickable { shock = p.second; custom = false }   // ios: Views/AnalysisDetail.swift:1087,1097
                            .background(if (on) Theme.teal else Theme.card, RoundedCornerShape(12.dp))   // ios: Views/AnalysisDetail.swift:1094
                            .border(1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(12.dp))   // ios: Views/AnalysisDetail.swift:1095
                            .padding(vertical = 10.dp)) {   // ios: Views/AnalysisDetail.swift:1092
                        Fg(fg) {
                            T(p.first, style = appFont(14f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1089
                            T(if (factor == "fx") factorText(factor, p.second).split(" ").last().trim { it in "()" } else factorText(factor, p.second), style = appFont(12f))   // ios: Views/AnalysisDetail.swift:1090
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            } } }
            val rows = rowsS
            if (rows != null) {
                val tot = rows.fold(0.0) { a, x -> a + x.dv }   // ios: Views/AnalysisDetail.swift:1063
                val upSum = rows.filter { it.dv > 0 }.fold(0.0) { a, x -> a + x.dv }; val dnSum = rows.filter { it.dv < 0 }.fold(0.0) { a, x -> a + x.dv }   // ios: Views/AnalysisDetail.swift:1064
                val r = tot / total   // ios: Views/AnalysisDetail.swift:1065
                val verdict = if (abs(r) < FactorDetailView.tiny) "거의 그대로예요" else if (abs(r) < 0.02) (if (r > 0) "조금 늘어요" else "조금 줄어요") else (if (r > 0) "많이 늘어요" else "많이 줄어요")   // ios: Views/AnalysisDetail.swift:1066
                val allWeak = rows.all { it.weak }   // ios: Views/AnalysisDetail.swift:1067
                val offset = upSum > 0 && dnSum < 0 && abs(tot) < min(upSum, -dnSum)   // ios: Views/AnalysisDetail.swift:1068
                val strong = rows.filter { !it.weak }; val weak = rows.filter { it.weak }   // ios: Views/AnalysisDetail.swift:1069
                val scale = max(1.0, rows.map { abs(it.dv) }.maxOrNull() ?: 1.0)   // ios: Views/AnalysisDetail.swift:1070
                val evIds: List<String> = if (factor == "rate") listOf("x_fomc", "x_cpi", "x_jobs") else if (factor == "fx") listOf("x_bok", "x_fomc") else if (factor == "oil") listOf("x_opec") else if (factor == "gold") listOf("x_fomc", "x_cpi") else listOf("x_fomc", "x_cpi", "x_jobs", "x_mid", "x_witch")   // ios: Views/AnalysisDetail.swift:1071
                val events = MarketEvents.upcoming(held = m.rows.map { it.id }, days = 45).filter { evIds.contains(it.id) }.sortedBy { it.day }.take(5)   // ios: Views/AnalysisDetail.swift:1072
                Card(dark = true) {   // ios: Views/AnalysisDetail.swift:1100
                    T("${name} ${factorText(factor, v)}이면", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1101
                    T(if (allWeak && factor != "fx") "뚜렷한 관계 없음" else verdict, style = appFont(24f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1102
                    T("${AppModel.manS(tot)} · ${AppModel.sgn(r)}", style = appFont(15f, FontWeight.SemiBold), color = Theme.change(tot))   // ios: Views/AnalysisDetail.swift:1103
                    if (upSum > 0 || dnSum < 0) {   // ios: Views/AnalysisDetail.swift:1104
                        SideBar("오르는 쪽", upSum, maxOf(upSum, -dnSum, 1.0), Theme.up)   // ios: Views/AnalysisDetail.swift:1105
                        SideBar("내리는 쪽", dnSum, maxOf(upSum, -dnSum, 1.0), Theme.down)   // ios: Views/AnalysisDetail.swift:1106
                    }
                    if (offset) T("종목끼리 반대로 움직여 상쇄", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1108
                }
                if (factorHuge(factor, v)) {   // ios: Views/AnalysisDetail.swift:1110
                    T("지난 3년엔 없던 큰 변화 · 실제와 많이 다를 수도",   // ios: Views/AnalysisDetail.swift:1111
                      modifier = Modifier.fillMaxWidth().background(Theme.orange.copy(alpha = 0.18f), RoundedCornerShape(12.dp))   // ios: Views/AnalysisDetail.swift:1113
                          .padding(12.dp), style = appFont(13f), color = Theme.inkFixed)   // ios: Views/AnalysisDetail.swift:1112
                }
                Card {   // ios: Views/AnalysisDetail.swift:1115
                    T("종목별", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1116
                    (if (weak.size > 3) strong else rows).forEach { x -> key(x.id) {   // ios: Views/AnalysisDetail.swift:1117
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/AnalysisDetail.swift:1118
                            modifier = Modifier.alpha(if (x.weak) 0.55f else 1f)) {   // ios: Views/AnalysisDetail.swift:1128
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:1119
                                T(x.label, style = appFont(14f, FontWeight.SemiBold))   // ios: Views/AnalysisDetail.swift:1120
                                Spacer(Modifier.weight(1f))   // ios: Views/AnalysisDetail.swift:1121
                                T(if (x.weak) "뚜렷한 관계 없음" else AppModel.manS(x.dv), style = appFont(14f, FontWeight.SemiBold), color = if (x.weak) Theme.muted else Theme.change(x.dv))   // ios: Views/AnalysisDetail.swift:1122
                            }
                            CenterBar(x.dv, scale)   // ios: Views/AnalysisDetail.swift:1124
                            val note = factorLabel(factor, x.dv, x.weak, x.known, shockSign) + (x.agree?.let { a -> val n10 = (a * 10).swRound().toInt(); if (x.weak) "" else " · 10번 중 ${n10}번" } ?: "")   // ios: Views/AnalysisDetail.swift:1125
                            if (note.isNotEmpty()) T(note, style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1126
                        }
                    } }
                    if (weak.size > 3) {   // ios: Views/AnalysisDetail.swift:1130
                        T("나머지 ${weak.size}개 · 뚜렷한 관계 없음", style = appFont(13f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:1131
                    }
                    if (rows.isEmpty()) T("보유 종목이 없어요.", style = appFont(13f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1133
                }
                Card {   // ios: Views/AnalysisDetail.swift:1135
                    T(if (factor == "rate") "금리 시나리오와 다른 이유" else "${m.forecastTitle}과 다른 이유", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1136
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/AnalysisDetail.swift:1137
                        Compare("이 화면", "그날그날 함께 움직인 만큼 · 한 달 이내")   // ios: Views/AnalysisDetail.swift:1138
                        Compare(if (factor == "rate") "금리 시나리오" else m.forecastTitle, if (factor == "rate") "회사 이익 경로 · 3년 영향" else "모든 요인 · 3년 뒤 분포")   // ios: Views/AnalysisDetail.swift:1139
                    }
                    T("기간·방법이 달라 방향이 다를 수도", style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1141
                    T(text = "목표 영향 보기", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal,   // ios: Views/AnalysisDetail.swift:1143
                      modifier = Modifier.plainClickable { m.analysisPath = m.analysisPath + (if (factor == "rate") AnalysisRoute.rateScen else AnalysisRoute.forecast) })   // ios: Views/AnalysisDetail.swift:1142
                }
                if (events.isNotEmpty()) {   // ios: Views/AnalysisDetail.swift:1146
                    Card {   // ios: Views/AnalysisDetail.swift:1147
                        T("다가오는 일정", style = appFont(15f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1148
                        events.forEach { e ->   // ios: Views/AnalysisDetail.swift:1149
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {   // ios: Views/AnalysisDetail.swift:1150
                                T(MarketEvents.`when`(e.day), Modifier.widthIn(min = 64.dp), style = appFont(12f, FontWeight.SemiBold), color = Theme.teal)   // ios: Views/AnalysisDetail.swift:1151
                                T(e.kind, style = appFont(14f))   // ios: Views/AnalysisDetail.swift:1152
                            }
                        }
                    }
                }
            }
            Card {   // ios: Views/AnalysisDetail.swift:1157
                // DisclosureGroup: 제목 줄을 누르면 펼친다 (오른쪽 꺾쇠가 돈다)
                val rot by animateFloatAsState(if (custom) 90f else 0f, label = "dg")   // ios: Views/AnalysisDetail.swift:1158
                Column {
                    Row(Modifier.fillMaxWidth().plainClickable { custom = !custom }, verticalAlignment = Alignment.CenterVertically) {
                        T("직접 정하기", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold), color = Theme.ink)   // ios: Views/AnalysisDetail.swift:1166
                        Sym("chevron.right", 15f, Theme.teal, Modifier.rotate(rot))
                    }
                    AnimatedVisibility(custom, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/AnalysisDetail.swift:1159,1165
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { T("값", style = appFont(13f), color = Theme.sub); Spacer(Modifier.weight(1f)); T(factorText(factor, v), style = appFont(14f, FontWeight.Bold)) }   // ios: Views/AnalysisDetail.swift:1160
                            IosSlider(value = min(max(v, rng.start), rng.endInclusive), onChange = { shock = it },   // ios: Views/AnalysisDetail.swift:1161
                                range = rng, step = if (factor == "rate") 0.25 else if (factor == "fx") 20.0 else 5.0, tint = Theme.teal,   // ios: Views/AnalysisDetail.swift:1162
                                modifier = Modifier.semantics { contentDescription = "${name} 변화 직접 정하기" })   // ios: Views/AnalysisDetail.swift:1163
                        }
                    }
                }
            }
            T("예측 아님 · '만약에' 화면 · 요인 하나만 바꾼 값",   // ios: Views/AnalysisDetail.swift:1168
              style = appFont(12f), color = Theme.muted)   // ios: Views/AnalysisDetail.swift:1169
        }
    }
}

// ios: Views/AnalysisDetail.swift:1187
@Composable
private fun SideBar(t: String, x: Double, s: Double, c: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/AnalysisDetail.swift:1188
        T(t, Modifier.width(60.dp), style = appFont(12f), maxLines = 1)   // ios: Views/AnalysisDetail.swift:1189
        BoxWithConstraints(Modifier.weight(1f).height(10.dp)) {   // ios: Views/AnalysisDetail.swift:1190,1193
            Box(Modifier.fillMaxHeight().width(dmax(2.dp, maxWidth * (abs(x) / s).toFloat())).background(c, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:1191
        }
        Box(Modifier.widthIn(min = 70.dp), contentAlignment = Alignment.CenterEnd) { T(AppModel.manS(x), style = appFont(12f, FontWeight.SemiBold)) }   // ios: Views/AnalysisDetail.swift:1194
    }
}
// ios: Views/AnalysisDetail.swift:1197
@Composable
private fun CenterBar(x: Double, s: Double) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp)) {   // ios: Views/AnalysisDetail.swift:1198,1200,1207
        val half = maxWidth / 2; val w = half * min(1.0, abs(x) / s).toFloat()   // ios: Views/AnalysisDetail.swift:1199
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).background(Theme.line))   // ios: Views/AnalysisDetail.swift:1201
        Box(Modifier.align(Alignment.CenterStart).offset(x = half).size(1.dp, 10.dp).background(Theme.dash))   // ios: Views/AnalysisDetail.swift:1202
        Box(Modifier.align(Alignment.CenterStart).offset(x = if (x >= 0) half else half - w).size(dmax(1.dp, w), 6.dp)   // ios: Views/AnalysisDetail.swift:1204
            .background(if (x >= 0) Theme.up else Theme.down, RoundedCornerShape(3.dp)))   // ios: Views/AnalysisDetail.swift:1203
    }
}
// ios: Views/AnalysisDetail.swift:1209
@Composable
private fun RowScope.Compare(t: String, d: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/AnalysisDetail.swift:1210
        modifier = Modifier.weight(1f).background(Theme.tealBg, RoundedCornerShape(10.dp))   // ios: Views/AnalysisDetail.swift:1215
            .padding(10.dp)) {   // ios: Views/AnalysisDetail.swift:1214
        T(t, style = appFont(13f, FontWeight.Bold))   // ios: Views/AnalysisDetail.swift:1211
        T(d, style = appFont(12f), color = Theme.sub)   // ios: Views/AnalysisDetail.swift:1212
    }
}
