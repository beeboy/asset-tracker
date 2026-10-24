package com.naeilo.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.AlpacaFeed
import com.naeilo.app.core.AlpacaKeys
import com.naeilo.app.core.Day
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.Store
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.cformat
import com.naeilo.app.core.feedJson
import com.naeilo.app.core.jObj
import com.naeilo.app.core.num
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.Market
import com.naeilo.app.net.SiteStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 차트 탭 이름·아이콘. 이름이 아직 확정 전이라 여기 한 곳만 바꾸면 된다 */
object FlowTab {   // ios: Data/FlowData.swift:4
    const val name = "흐름"   // ios: Data/FlowData.swift:5
    const val icon = "point.bottomleft.forward.to.point.topright.scurvepath"   // ios: Data/FlowData.swift:6 (안드로이드는 R.drawable.tab_flow)
}

/** 일봉 하나 (시가·고가·저가·종가·거래량) */
data class Candle(var o: Double, var h: Double, var l: Double, var c: Double, var v: Double)   // ios: Data/FlowData.swift:10

/** 추세 점: 고점(high) 또는 저점 */
data class FlowPivot(val i: Int, val price: Double, val high: Boolean)   // ios: Data/FlowData.swift:13

/** 추세 띠 한 칸: from…to 구간의 추세 (1 상승, -1 하락, 0 횡보) */
data class FlowTrend(val from: Int, val to: Int, val dir: Int)   // ios: Data/FlowData.swift:16

/** 지지·저항 가로선: 여러 번 받치거나 튕긴 가격. touches 가 많을수록 진하다 */
data class FlowLevel(val id: Int, val price: Double, val from: Int, val touches: Int, val support: Boolean)   // ios: Data/FlowData.swift:19

/** 패턴 이름표. strong = 확정되고 거래량이 받쳐 준 것(진하게), 아니면 흐린 점선 */
data class FlowPattern(   // ios: Data/FlowData.swift:22
    val id: Int,   // ios: Data/FlowData.swift:24
    val kind: Kind,   // ios: Data/FlowData.swift:25
    val from: Int, val to: Int,   // ios: Data/FlowData.swift:26
    val price: Double,       // 이름표를 붙일 가격 (아래쪽 패턴은 저가, 위쪽은 고가)   // ios: Data/FlowData.swift:27
    val below: Boolean,      // 이름표를 캔들 아래에   // ios: Data/FlowData.swift:28
    val strong: Boolean,   // ios: Data/FlowData.swift:29
) {
    enum class Kind { doubleBottom, doubleTop, headShoulders, ascTriangle, descTriangle, bullFlag, bearFlag, hammer, shootingStar, bullEngulf, bearEngulf, insideBar }   // ios: Data/FlowData.swift:23
    val big: Boolean get() = kind in setOf(Kind.doubleBottom, Kind.doubleTop, Kind.headShoulders, Kind.ascTriangle, Kind.descTriangle, Kind.bullFlag, Kind.bearFlag)   // ios: Data/FlowData.swift:30
}

/** 차트 한 종목의 미리 계산해 둔 그림 재료 */
class FlowSeries(   // ios: Data/FlowData.swift:34
    val candles: List<Candle>,   // ios: Data/FlowData.swift:35,48
    val dates: List<String>? = null,           // 실데이터는 거래일 (DRNK 샘플은 없음: 어제까지 하루 한 칸)   // ios: Data/FlowData.swift:36,49
    val live: Boolean = false,                 // 마지막 칸이 장중 오늘 캔들 (흐리게 그리고, 거기 닿은 패턴은 흐린 점선)   // ios: Data/FlowData.swift:42,47,50
) {
    val pivots: List<FlowPivot>   // ios: Data/FlowData.swift:37
    val pivotSets: List<List<FlowPivot>>   // 추세 점: 일봉·주봉·월봉으로 볼 때 (작은 흔들림은 넓게 볼수록 뺀다)   // ios: Data/FlowData.swift:38
    val trends: List<List<FlowTrend>>      // 0 = 일(작은 흐름), 1 = 주, 2 = 월(큰 흐름)   // ios: Data/FlowData.swift:39
    val levels: List<FlowLevel>   // ios: Data/FlowData.swift:40
    val patterns: List<FlowPattern>   // ios: Data/FlowData.swift:41
    val base: Double get() = candles.firstOrNull()?.c ?: 1.0   // ios: Data/FlowData.swift:43
    /** '+x% 시작 대비' */
    fun pct(v: Double): Double = (v / base - 1) * 100   // ios: Data/FlowData.swift:45

    init {
        val c = candles.map { it.c }   // ios: Data/FlowData.swift:51
        val zz = listOf(0.06, 0.12, 0.22).map { FlowCalc.zigzag(c, it) }   // ios: Data/FlowData.swift:52
        pivotSets = zz.map { z -> z.map { FlowPivot(i = it.i, price = if (it.high) candles[it.i].h else candles[it.i].l, high = it.high) } }   // ios: Data/FlowData.swift:53
        pivots = pivotSets[0]   // ios: Data/FlowData.swift:54
        trends = zz.map { FlowCalc.trends(it, c.size) }   // ios: Data/FlowData.swift:55
        levels = FlowCalc.levels(pivots, last = c.lastOrNull() ?: 0.0)   // ios: Data/FlowData.swift:56
        val pts = FlowCalc.patterns(candles, pivots)   // ios: Data/FlowData.swift:57
        // 장중 오늘 캔들에 닿은 패턴은 아직 바뀔 수 있어 확정이 아니다 (마감 뒤 일봉으로 다시 정한다)
        patterns = if (!live) pts else pts.map { if (it.to >= candles.size - 1) it.copy(strong = false) else it }   // ios: Data/FlowData.swift:59
        // ios: Data/FlowData.swift:60
    }
}

object FlowCalc {   // ios: Data/FlowData.swift:64
    /** 지그재그: 종가가 마지막 극점에서 th 만큼 되돌면 그 극점을 고점·저점으로 확정 */
    fun zigzag(c: List<Double>, th: Double): List<FlowPivot> {   // ios: Data/FlowData.swift:66
        if (c.size <= 2) return emptyList()   // ios: Data/FlowData.swift:67
        val out = ArrayList<FlowPivot>(); var dir = 0; var ext = 0; var hi = 0; var lo = 0   // ios: Data/FlowData.swift:68
        for (i in 1 until c.size) {   // ios: Data/FlowData.swift:69
            when (dir) {   // ios: Data/FlowData.swift:70
                0 -> {   // ios: Data/FlowData.swift:71
                    if (c[i] > c[hi]) hi = i   // ios: Data/FlowData.swift:72
                    if (c[i] < c[lo]) lo = i   // ios: Data/FlowData.swift:73
                    if (c[i] >= c[lo] * (1 + th)) { out.add(FlowPivot(i = lo, price = c[lo], high = false)); dir = 1; ext = i }   // ios: Data/FlowData.swift:74
                    else if (c[i] <= c[hi] * (1 - th)) { out.add(FlowPivot(i = hi, price = c[hi], high = true)); dir = -1; ext = i }   // ios: Data/FlowData.swift:75
                }
                1 -> {   // ios: Data/FlowData.swift:76
                    if (c[i] > c[ext]) ext = i   // ios: Data/FlowData.swift:77
                    else if (c[i] <= c[ext] * (1 - th)) { out.add(FlowPivot(i = ext, price = c[ext], high = true)); dir = -1; ext = i }   // ios: Data/FlowData.swift:78
                }
                else -> {   // ios: Data/FlowData.swift:79
                    if (c[i] < c[ext]) ext = i   // ios: Data/FlowData.swift:80
                    else if (c[i] >= c[ext] * (1 + th)) { out.add(FlowPivot(i = ext, price = c[ext], high = false)); dir = 1; ext = i }   // ios: Data/FlowData.swift:81
                }
            }
        }
        return out   // ios: Data/FlowData.swift:84
    }

    /** 고점·저점이 앞의 같은 종류보다 높은지로 추세: 둘 다 높으면 상승, 둘 다 낮으면 하락, 섞이면 횡보 */
    fun trends(p: List<FlowPivot>, n: Int): List<FlowTrend> {   // ios: Data/FlowData.swift:88
        if (n <= 0) return emptyList()   // ios: Data/FlowData.swift:89
        val out = ArrayList<FlowTrend>(); val lastH = ArrayList<Double>(); val lastL = ArrayList<Double>(); var start = 0; var dir = 0   // ios: Data/FlowData.swift:90
        for (k in p.indices) {   // ios: Data/FlowData.swift:91
            if (p[k].high) lastH.add(p[k].price) else lastL.add(p[k].price)   // ios: Data/FlowData.swift:92
            var d = 0   // ios: Data/FlowData.swift:93
            if (lastH.size >= 2 && lastL.size >= 2) {   // ios: Data/FlowData.swift:94
                val hUp = lastH[lastH.size - 1] > lastH[lastH.size - 2] * 1.005   // ios: Data/FlowData.swift:95
                val hDn = lastH[lastH.size - 1] < lastH[lastH.size - 2] * 0.995   // ios: Data/FlowData.swift:96
                val lUp = lastL[lastL.size - 1] > lastL[lastL.size - 2] * 1.005   // ios: Data/FlowData.swift:97
                val lDn = lastL[lastL.size - 1] < lastL[lastL.size - 2] * 0.995   // ios: Data/FlowData.swift:98
                d = if (hUp && lUp) 1 else if (hDn && lDn) -1 else 0   // ios: Data/FlowData.swift:99
            }
            if (d != dir) { if (p[k].i > start) out.add(FlowTrend(from = start, to = p[k].i, dir = dir)); start = p[k].i; dir = d }   // ios: Data/FlowData.swift:101
        }
        out.add(FlowTrend(from = start, to = n - 1, dir = dir))   // ios: Data/FlowData.swift:103
        return out   // ios: Data/FlowData.swift:104
    }

    /** 지지·저항: 2% 안에 모인 고점·저점 묶음. 두 번 이상 닿은 것만, 많이 닿은 순 6개 */
    fun levels(p: List<FlowPivot>, last: Double): List<FlowLevel> {   // ios: Data/FlowData.swift:108
        val s = p.sortedBy { it.price }   // ios: Data/FlowData.swift:109
        val groups = ArrayList<ArrayList<FlowPivot>>()   // ios: Data/FlowData.swift:110
        for (x in s) {   // ios: Data/FlowData.swift:111
            val m = groups.lastOrNull()?.firstOrNull(); if (m != null && x.price <= m.price * 1.02) groups[groups.size - 1].add(x) else groups.add(arrayListOf(x))   // ios: Data/FlowData.swift:112
        }
        return groups.filter { it.size >= 2 }   // ios: Data/FlowData.swift:114
            .sortedWith { a, b -> if (a.size != b.size) b.size - a.size else (b.maxOfOrNull { it.i } ?: 0).compareTo(a.maxOfOrNull { it.i } ?: 0) }   // ios: Data/FlowData.swift:115
            .take(6).withIndex()   // ios: Data/FlowData.swift:116
            .map { (k, g) ->   // ios: Data/FlowData.swift:117
                val price = g.fold(0.0) { a, x -> a + x.price } / g.size.toDouble()   // ios: Data/FlowData.swift:118
                FlowLevel(id = k, price = price, from = g.minOfOrNull { it.i } ?: 0, touches = g.size, support = price < last)   // ios: Data/FlowData.swift:119
            }
    }

    /** 패턴 찾기. 캔들 패턴은 고점·저점 근처(±1일)에서만 본다 (아무 데나 붙이면 헛짚는 일이 많다) */
    fun patterns(k: List<Candle>, p0: List<FlowPivot>): List<FlowPattern> {   // ios: Data/FlowData.swift:124
        val out = ArrayList<FlowPattern>()   // ios: Data/FlowData.swift:125
        // 큰 패턴은 후보로 모았다가 확정된 것부터 구간이 겹치지 않게 고른다
        val cands = ArrayList<Pair<FlowPattern, Boolean>>()   // ios: Data/FlowData.swift:127
        fun add(kind: FlowPattern.Kind, a: Int, b: Int, price: Double, below: Boolean, strong: Boolean, conf: Boolean = true) {   // ios: Data/FlowData.swift:128
            val pt = FlowPattern(id = 0, kind = kind, from = a, to = b, price = price, below = below, strong = strong)   // ios: Data/FlowData.swift:129
            if (pt.big) cands.add(pt to conf) else out.add(FlowPattern(id = out.size, kind = kind, from = a, to = b, price = price, below = below, strong = strong))   // ios: Data/FlowData.swift:130
        }
        // 거래량이 직전 20일 평균의 1.2배 이상이면 받쳐 준 것
        fun volOK(i: Int): Boolean {   // ios: Data/FlowData.swift:133
            val a = max(0, i - 20); if (i <= a) return false   // ios: Data/FlowData.swift:134
            var sum = 0.0; for (j in a until i) sum += k[j].v; val avg = sum / (i - a).toDouble()   // ios: Data/FlowData.swift:135
            return k[i].v >= avg * 1.2   // ios: Data/FlowData.swift:136
        }
        // 몸통이 길다/짧다는 직전 10개 평균 몸통과 견준다 (TA-Lib 방식)
        fun avgBody(i: Int): Double {   // ios: Data/FlowData.swift:139
            val a = max(0, i - 10); if (i <= a) return abs(k[i].c - k[i].o)   // ios: Data/FlowData.swift:140
            var sum = 0.0; for (j in a until i) sum += abs(k[j].c - k[j].o); return sum / (i - a).toDouble()   // ios: Data/FlowData.swift:141
        }
        val n = k.size   // ios: Data/FlowData.swift:143
        val used = HashSet<Int>()   // ios: Data/FlowData.swift:144
        for (pv in p0) {   // ios: Data/FlowData.swift:145
            for (i in max(1, pv.i - 1)..min(n - 1, pv.i + 1)) { if (i in used) continue   // ios: Data/FlowData.swift:146
                val x = k[i]; val y = k[i - 1]   // ios: Data/FlowData.swift:147
                val body = abs(x.c - x.o); val top = max(x.o, x.c); val bot = min(x.o, x.c)   // ios: Data/FlowData.swift:148
                val upW = x.h - top; val dnW = bot - x.l; val range = max(1e-9, x.h - x.l)   // ios: Data/FlowData.swift:149
                var kind: FlowPattern.Kind? = null   // ios: Data/FlowData.swift:150
                if (!pv.high && dnW >= 2 * max(body, range * 0.05) && upW <= range * 0.15 && body <= avgBody(i) * 1.2) kind = FlowPattern.Kind.hammer   // ios: Data/FlowData.swift:151
                else if (pv.high && upW >= 2 * max(body, range * 0.05) && dnW <= range * 0.15 && body <= avgBody(i) * 1.2) kind = FlowPattern.Kind.shootingStar   // ios: Data/FlowData.swift:152
                else if (!pv.high && y.c < y.o && x.c > x.o && x.o <= y.c && x.c >= y.o && body > avgBody(i)) kind = FlowPattern.Kind.bullEngulf   // ios: Data/FlowData.swift:153
                else if (pv.high && y.c > y.o && x.c < x.o && x.o >= y.c && x.c <= y.o && body > avgBody(i)) kind = FlowPattern.Kind.bearEngulf   // ios: Data/FlowData.swift:154
                else if (i > pv.i && x.h < y.h && x.l > y.l && (y.h - y.l) > avgBody(i) * 1.5) kind = FlowPattern.Kind.insideBar   // ios: Data/FlowData.swift:155
                if (kind != null) { val kd = kind   // ios: Data/FlowData.swift:156
                    val below = !pv.high   // ios: Data/FlowData.swift:157
                    add(kd, if (kd == FlowPattern.Kind.bullEngulf || kd == FlowPattern.Kind.bearEngulf || kd == FlowPattern.Kind.insideBar) i - 1 else i, i, if (below) min(x.l, y.l) else max(x.h, y.h), below, volOK(i))   // ios: Data/FlowData.swift:158
                    used.add(i); break   // ios: Data/FlowData.swift:159
                }
            }
        }
        // 큰 패턴: 고점·저점 배치로. 확정(목선 돌파) 전이면 흐리게. 꼬리 끝이 아니라 종가로 견준다
        val c = k.map { it.c }   // ios: Data/FlowData.swift:164
        val p = p0.map { FlowPivot(i = it.i, price = c[it.i], high = it.high) }   // ios: Data/FlowData.swift:165
        fun firstClose(after: Int, within: Int, f: (Double) -> Boolean): Int? {   // ios: Data/FlowData.swift:166
            if (after + 1 >= n) return null   // ios: Data/FlowData.swift:167
            return (after + 1..min(n - 1, after + within)).firstOrNull { f(c[it]) }   // ios: Data/FlowData.swift:168
        }
        for (j in p.indices.drop(2)) {   // ios: Data/FlowData.swift:170
            val a = p[j - 2]; val m = p[j - 1]; val b = p[j]   // ios: Data/FlowData.swift:171
            if (!(a.high == b.high && a.high != m.high)) continue   // ios: Data/FlowData.swift:172
            val gap = b.i - a.i   // ios: Data/FlowData.swift:173
            if (!(gap >= 10 && gap <= 150 && abs(b.price / a.price - 1) <= 0.03)) continue   // ios: Data/FlowData.swift:174
            if (!a.high) {   // ios: Data/FlowData.swift:175
                val hit = firstClose(b.i, 60) { it > m.price }   // ios: Data/FlowData.swift:176
                if (!(hit != null || b.i > n - 60)) continue   // 확정 안 된 지난 것은 버린다   // ios: Data/FlowData.swift:177
                // 확정 전에 두 바닥 아래로 내려갔으면 깨진 모양
                if (hit == null && firstClose(b.i, n) { it < min(a.price, b.price) * 0.99 } != null) continue   // ios: Data/FlowData.swift:179
                val to = hit ?: (n - 1)   // ios: Data/FlowData.swift:180
                add(FlowPattern.Kind.doubleBottom, a.i, to, min(a.price, b.price), true, hit != null && volOK(hit), conf = hit != null)   // ios: Data/FlowData.swift:181
            } else {   // ios: Data/FlowData.swift:182
                val hit = firstClose(b.i, 60) { it < m.price }   // ios: Data/FlowData.swift:183
                if (!(hit != null || b.i > n - 60)) continue   // ios: Data/FlowData.swift:184
                if (hit == null && firstClose(b.i, n) { it > max(a.price, b.price) * 1.01 } != null) continue   // ios: Data/FlowData.swift:185
                val to = hit ?: (n - 1)   // ios: Data/FlowData.swift:186
                add(FlowPattern.Kind.doubleTop, a.i, to, max(a.price, b.price), false, hit != null && volOK(hit), conf = hit != null)   // ios: Data/FlowData.swift:187
            }
        }
        for (j in p.indices.drop(4)) {   // ios: Data/FlowData.swift:190
            val h1 = p[j - 4]; val l1 = p[j - 3]; val h2 = p[j - 2]; val l2 = p[j - 1]; val h3 = p[j]   // ios: Data/FlowData.swift:191
            if (!(h1.high && h2.high && h3.high)) continue   // ios: Data/FlowData.swift:192
            if (!(h2.price > max(h1.price, h3.price) * 1.03 && abs(h3.price / h1.price - 1) <= 0.05 && h3.i - h1.i <= 220)) continue   // ios: Data/FlowData.swift:193
            val neck = min(l1.price, l2.price)   // ios: Data/FlowData.swift:194
            val hit = firstClose(h3.i, 60) { it < neck }   // ios: Data/FlowData.swift:195
            if (!(hit != null || h3.i > n - 60)) continue   // ios: Data/FlowData.swift:196
            if (hit == null && firstClose(h3.i, n) { it > h2.price } != null) continue   // ios: Data/FlowData.swift:197
            val to = hit ?: (n - 1)   // ios: Data/FlowData.swift:198
            add(FlowPattern.Kind.headShoulders, h1.i, to, h2.price, false, hit != null && volOK(hit), conf = hit != null)   // ios: Data/FlowData.swift:199
        }
        // 삼각형: 고점 둘이 같은 높이(1.5% 안)인데 저점이 높아지면 상승삼각형, 반대면 하락삼각형. 평평한 쪽을 종가가 뚫으면 확정
        for (j in p.indices.drop(3)) {   // ios: Data/FlowData.swift:202
            val w = p.subList(j - 3, j + 1); val hs = w.filter { it.high }; val ls = w.filter { !it.high }   // ios: Data/FlowData.swift:203
            if (!(hs.size == 2 && ls.size == 2 && w.last().i - w.first().i >= 15 && w.last().i - w.first().i <= 150)) continue   // ios: Data/FlowData.swift:204
            val h1 = hs[0].price; val h2 = hs[1].price; val l1 = ls[0].price; val l2 = ls[1].price   // ios: Data/FlowData.swift:205
            if (abs(h2 / h1 - 1) <= 0.015 && l2 > l1 * 1.02) {   // ios: Data/FlowData.swift:206
                val top = max(h1, h2); val hit = firstClose(p[j].i, 40) { it > top * 1.005 }   // ios: Data/FlowData.swift:207
                if (!(hit != null || p[j].i > n - 40)) continue   // ios: Data/FlowData.swift:208
                val to = hit ?: (n - 1)   // ios: Data/FlowData.swift:209
                add(FlowPattern.Kind.ascTriangle, w.first().i, to, top, false, hit != null && volOK(hit), conf = hit != null)   // ios: Data/FlowData.swift:210
            } else if (abs(l2 / l1 - 1) <= 0.015 && h2 < h1 * 0.98) {   // ios: Data/FlowData.swift:211
                val bot = min(l1, l2); val hit = firstClose(p[j].i, 40) { it < bot * 0.995 }   // ios: Data/FlowData.swift:212
                if (!(hit != null || p[j].i > n - 40)) continue   // ios: Data/FlowData.swift:213
                val to = hit ?: (n - 1)   // ios: Data/FlowData.swift:214
                add(FlowPattern.Kind.descTriangle, w.first().i, to, bot, true, hit != null && volOK(hit), conf = hit != null)   // ios: Data/FlowData.swift:215
            }
        }
        // 플래그: 짧게 12% 넘게 오른(내린) 깃대 뒤, 그 절반 안에서 쉬다가 깃대 끝을 다시 넘으면 확정
        var i = 12   // ios: Data/FlowData.swift:219
        while (i < n - 5) {   // ios: Data/FlowData.swift:220
            for (up in listOf(true, false)) {   // ios: Data/FlowData.swift:222
                val m = (3..12).firstOrNull { if (up) c[i] / c[i - it] >= 1.12 else c[i] / c[i - it] <= 0.88 } ?: continue   // ios: Data/FlowData.swift:223
                val poleTop = c[i]; val half = abs(c[i] - c[i - m]) / 2   // ios: Data/FlowData.swift:224
                // 깃대 끝이 그 뒤 5일 안 최고(최저)여야 한다
                if (!(i + 1..min(n - 1, i + 5)).all { if (up) c[it] <= poleTop * 1.005 else c[it] >= poleTop * 0.995 }) continue   // ios: Data/FlowData.swift:226
                var t = i + 1; var broke: Int? = null   // ios: Data/FlowData.swift:227
                while (t < min(n, i + 26)) {   // ios: Data/FlowData.swift:228
                    if (if (up) c[t] > poleTop * 1.005 else c[t] < poleTop * 0.995) { broke = t; break }   // ios: Data/FlowData.swift:229
                    if (if (up) c[t] < poleTop - half else c[t] > poleTop + half) break   // ios: Data/FlowData.swift:230
                    t += 1   // ios: Data/FlowData.swift:231
                }
                val rest = (broke ?: t) - i   // ios: Data/FlowData.swift:233
                if (rest < 5) continue   // ios: Data/FlowData.swift:234
                if (broke == null && !(t >= n - 1 && i > n - 26)) continue   // 깨졌거나 시간이 다 된 건 버리고, 지금 쉬는 중이면 흐리게   // ios: Data/FlowData.swift:235
                val to = broke ?: (n - 1)   // ios: Data/FlowData.swift:236
                add(if (up) FlowPattern.Kind.bullFlag else FlowPattern.Kind.bearFlag, i - m, to, poleTop, !up, broke != null && volOK(broke), conf = broke != null)   // ios: Data/FlowData.swift:237
                i = to   // ios: Data/FlowData.swift:238
                break   // ios: Data/FlowData.swift:239
            }
            i += 1   // defer { i += 1 } 자리   // ios: Data/FlowData.swift:221
        }
        val spans = ArrayList<IntRange>()   // ios: Data/FlowData.swift:242
        for (pt in cands.withIndex().sortedWith { a, b -> val ka = if (a.value.second) 0 else 1; val kb = if (b.value.second) 0 else 1; if (ka != kb) ka - kb else a.index - b.index }.map { it.value.first }) {   // ios: Data/FlowData.swift:243
            if (spans.any { it.first <= pt.to && pt.from <= it.last }) continue   // ios: Data/FlowData.swift:244
            spans.add(pt.from..pt.to)   // ios: Data/FlowData.swift:245
            out.add(FlowPattern(id = out.size, kind = pt.kind, from = pt.from, to = pt.to, price = pt.price, below = pt.below, strong = pt.strong))   // ios: Data/FlowData.swift:246
        }
        return out   // ios: Data/FlowData.swift:248
    }
}

/** 샘플 종목 DRNK: 종가 1010일(drnk_ratio)에 시가·고가·저가·거래량을 시드 고정으로 만들어 붙인다 */
object DrnkSample {   // ios: Data/FlowData.swift:253
    val series: FlowSeries by lazy { FlowSeries(candles = candles(StubPriceProvider().drnkRatio)) }   // ios: Data/FlowData.swift:254

    /** 같은 시드면 늘 같은 값 (SplitMix64) */
    class RNG(var s: ULong) {   // ios: Data/FlowData.swift:257,258
        fun next(): Double {   // ios: Data/FlowData.swift:259
            s += 0x9E3779B97F4A7C15uL   // (아이폰 255줄, 예외 목록)
            var z = s   // ios: Data/FlowData.swift:261
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL   // (아이폰 257줄, 예외 목록)
            z = (z xor (z shr 27)) * 0x94D049BB133111EBuL   // (아이폰 258줄, 예외 목록)
            return ((z xor (z shr 31)) shr 11).toDouble() / (1L shl 53).toDouble()   // ios: Data/FlowData.swift:264
        }
        fun gauss(): Double { val a = next(); val b = next(); return sqrt(-2 * ln(max(1e-12, a))) * cos(2 * Math.PI * b) }   // ios: Data/FlowData.swift:266
    }

    fun candles(r: List<Double>): List<Candle> {   // ios: Data/FlowData.swift:269
        if (r.size <= 2) return r.map { Candle(o = it, h = it, l = it, c = it, v = 1.0) }   // ios: Data/FlowData.swift:270
        val g = RNG(0xD81C.toULong())   // ios: Data/FlowData.swift:271
        val k: MutableList<Candle> = r.indices.map { i ->   // ios: Data/FlowData.swift:272
            val c = r[i]; val prev = if (i > 0) r[i - 1] else c   // ios: Data/FlowData.swift:273
            val o = prev * (1 + g.gauss() * 0.004)   // ios: Data/FlowData.swift:274
            val h = max(o, c) * (1 + abs(g.gauss()) * 0.007)   // ios: Data/FlowData.swift:275
            val l = min(o, c) * (1 - abs(g.gauss()) * 0.007)   // ios: Data/FlowData.swift:276
            val ret = abs(c / prev - 1)   // ios: Data/FlowData.swift:277
            val v = (0.7 + ret * 40) * exp(g.gauss() * 0.25)   // ios: Data/FlowData.swift:278
            Candle(o = o, h = h, l = l, c = c, v = v)   // ios: Data/FlowData.swift:279
        }.toMutableList()
        // 패턴 심기: 6% 고점·저점 자리에 해머·유성·포위형·인사이드바를 번갈아. 거래량은 셋 중 하나만 적게 (흐린 이름표)
        val p = FlowCalc.zigzag(r, 0.06)   // ios: Data/FlowData.swift:282
        for ((j, pv) in p.withIndex()) { if (!(pv.i > 0 && pv.i < r.size - 2)) continue   // ios: Data/FlowData.swift:283
            val i = pv.i; val c = r[i]; val weak = j % 3 == 2   // ios: Data/FlowData.swift:284
            val sw = j / 2 % 3   // ios: Data/FlowData.swift:285
            if (!pv.high && sw == 0) {   // 해머: 위쪽 작은 몸통 + 긴 아래꼬리   // ios: Data/FlowData.swift:286
                val o = c * 0.997   // ios: Data/FlowData.swift:287
                k[i].o = o; k[i].h = c * 1.001; k[i].l = o - c * 0.03   // ios: Data/FlowData.swift:288
            } else if (pv.high && sw == 0) {    // 유성: 아래쪽 작은 몸통 + 긴 위꼬리   // ios: Data/FlowData.swift:289
                val o = c * 1.003   // ios: Data/FlowData.swift:290
                k[i].o = o; k[i].l = c * 0.999; k[i].h = o + c * 0.03   // ios: Data/FlowData.swift:291
            } else if (!pv.high && sw == 1 && r[i + 1] > r[i]) {   // 강세 포위형: 음봉 다음 그 몸통을 덮는 양봉   // ios: Data/FlowData.swift:292
                k[i].o = c + (r[i + 1] - c) * 0.6; k[i].h = max(k[i].h, k[i].o * 1.002)   // ios: Data/FlowData.swift:293
                k[i + 1].o = c * 0.997; k[i + 1].l = min(k[i + 1].l, k[i + 1].o * 0.998)   // ios: Data/FlowData.swift:294
                k[i + 1].h = max(k[i + 1].h, r[i + 1] * 1.002)   // ios: Data/FlowData.swift:295
            } else if (pv.high && sw == 1 && r[i + 1] < r[i]) {    // 약세 포위형   // ios: Data/FlowData.swift:296
                k[i].o = c - (c - r[i + 1]) * 0.6; k[i].l = min(k[i].l, k[i].o * 0.998)   // ios: Data/FlowData.swift:297
                k[i + 1].o = c * 1.003; k[i + 1].h = max(k[i + 1].h, k[i + 1].o * 1.002)   // ios: Data/FlowData.swift:298
                k[i + 1].l = min(k[i + 1].l, r[i + 1] * 0.998)   // ios: Data/FlowData.swift:299
            } else {           // 인사이드바: 극점 날 크게, 다음 날 그 안에   // ios: Data/FlowData.swift:300
                k[i].h = max(k[i].o, c) * 1.02; k[i].l = min(k[i].o, c) * 0.98   // ios: Data/FlowData.swift:301
                val x = k[i + 1].copy()   // ios: Data/FlowData.swift:302
                k[i + 1].h = min(max(x.o, x.c) * 1.003, k[i].h * 0.999)   // ios: Data/FlowData.swift:303
                k[i + 1].l = max(min(x.o, x.c) * 0.997, k[i].l * 1.001)   // ios: Data/FlowData.swift:304
                if (k[i + 1].o > k[i].h * 0.999 || k[i + 1].o < k[i].l * 1.001) k[i + 1].o = (k[i].h + k[i].l) / 2   // ios: Data/FlowData.swift:305
                k[i + 1].h = max(k[i + 1].h, max(k[i + 1].o, x.c)); k[i + 1].l = min(k[i + 1].l, min(k[i + 1].o, x.c))   // ios: Data/FlowData.swift:306
            }
            val d = if (pv.high || j / 2 % 3 == 0) i else i + 1   // ios: Data/FlowData.swift:308
            k[d].v *= if (weak) 0.5 else 2.2   // ios: Data/FlowData.swift:309
        }
        return k   // ios: Data/FlowData.swift:311
    }
}

/** 차트 탭 실데이터 캔들 저장: 종목(사이트 티커) → 거래일별 시가·고가·저가·종가·거래량.
 *  토스는 TossHistory.fetch 가 같은 응답에서 같이 남기고, 알파카는 FlowAlpaca 가 받는다. 위젯이 읽는 toss_history.json 과는 따로 둔다 */
@Serializable
data class FlowBars(   // ios: Data/FlowData.swift:317
    var b: Map<String, Series> = emptyMap(),   // ios: Data/FlowData.swift:323
    var day: Map<String, String> = emptyMap(),     // 마지막으로 받은 날 표시 (알파카: 하루 한 번)   // ios: Data/FlowData.swift:324
) {
    data class Row(val o: Double, val h: Double, val l: Double, val c: Double, val v: Double)   // ios: Data/FlowData.swift:318
    @Serializable
    data class Series(   // ios: Data/FlowData.swift:319
        val d: List<String> = emptyList(), val o: List<Double> = emptyList(), val h: List<Double> = emptyList(), val l: List<Double> = emptyList(), val c: List<Double> = emptyList(), val v: List<Double> = emptyList(),   // ios: Data/FlowData.swift:320
    ) {
        val candles: List<Candle> get() = d.indices.map { Candle(o = o[it], h = h[it], l = l[it], c = c[it], v = v[it]) }   // ios: Data/FlowData.swift:321
    }

    companion object {
        const val tossName = "flow_toss.json"; const val alpacaName = "flow_alpaca.json"   // ios: Data/FlowData.swift:326
        fun read(name: String): FlowBars = Store.read(serializer(), name) ?: FlowBars()   // ios: Data/FlowData.swift:327
        fun write(f: FlowBars, name: String) = Store.write(serializer(), f, name)   // ios: Data/FlowData.swift:328
    }

    /** 새로 받은 줄을 잇는다. full 이면 갈아 끼우고, keep 에 맞는 날만 남긴다 (끝나기 전 오늘 일봉·3년 넘은 날 빼기) */
    fun merge(k: String, rows: Map<String, Row>, full: Boolean, keep: (String) -> Boolean) {   // ios: Data/FlowData.swift:331
        val all = HashMap<String, Row>()   // ios: Data/FlowData.swift:332
        val s0 = b[k]; if (!full && s0 != null) { for (i in s0.d.indices) all[s0.d[i]] = Row(o = s0.o[i], h = s0.h[i], l = s0.l[i], c = s0.c[i], v = s0.v[i]) }   // ios: Data/FlowData.swift:333
        for ((d, r) in rows) all[d] = r   // ios: Data/FlowData.swift:334
        val ds = all.keys.filter(keep).sorted()   // ios: Data/FlowData.swift:335
        val rs = ds.map { all[it]!! }   // ios: Data/FlowData.swift:336
        val s = Series(d = ds, o = rs.map { it.o }, h = rs.map { it.h }, l = rs.map { it.l }, c = rs.map { it.c }, v = rs.map { it.v })   // ios: Data/FlowData.swift:337
        b = b + (k to s)   // ios: Data/FlowData.swift:338
    }
}

/** 알파카 일봉 (미국 종목, 사용자 본인 키). /v2/stocks/bars 한 번에 여러 종목, feed=sip (거래량이 전체 시장 기준. IEX 는 일부라 안 씀).
 *  종목마다 하루 한 번(장 끝난 뒤 한 번 더), 처음엔 3년치, 그다음엔 최근 10일만. 부르는 사이를 0.35초 넘게 띄워 분당 200회 한도 아래로 */
object FlowAlpaca {   // ios: Data/FlowData.swift:344
    const val base = "https://data.alpaca.markets/v2/stocks/bars"   // ios: Data/FlowData.swift:345
    @Volatile private var last = 0L   // ms   // ios: Data/FlowData.swift:346

    /** 뉴욕 오늘 날짜와 장 마감(16:10) 지났는지 */
    fun nyDay(): Pair<String, Boolean> {   // ios: Data/FlowData.swift:349
        val c = Instant.now().atZone(ZoneId.of("America/New_York"))   // ios: Data/FlowData.swift:350,351,352
        return cformat("%04d-%02d-%02d", c.year, c.monthValue, c.dayOfMonth) to (c.hour * 60 + c.minute >= 16 * 60 + 10)   // ios: Data/FlowData.swift:353
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    /** keys: 사이트 티커 → 알파카 기호. 받은 게 있으면 true */
    suspend fun fetch(keys: Map<String, String>): Boolean = withContext(Dispatchers.IO) {   // ios: Data/FlowData.swift:357,358
        val k = AlpacaKeys.load(); if (k == null || keys.isEmpty()) return@withContext false   // ios: Data/FlowData.swift:359
        val f = FlowBars.read(FlowBars.alpacaName)   // ios: Data/FlowData.swift:360
        val (md, closed) = nyDay(); val stamp = if (closed) Day.today + "c" else Day.today   // ios: Data/FlowData.swift:361
        val from = Day.str(Instant.now().minusSeconds((3 * 365 + 15) * 86400L))   // ios: Data/FlowData.swift:362
        val need = keys.filter { f.day[it.key] != stamp || f.b[it.key] == null }   // ios: Data/FlowData.swift:363
        if (need.isEmpty()) return@withContext false   // ios: Data/FlowData.swift:364
        // 처음 받는 종목이 하나라도 있으면 3년치, 아니면 마지막 날 10일 전부터
        val fresh = need.keys.any { (f.b[it]?.d?.size ?: 0) < 30 }   // ios: Data/FlowData.swift:366
        val start = if (fresh) from else Day.str(Instant.now().minusSeconds(14 * 86400L))   // ios: Data/FlowData.swift:367
        // 무료 키는 최근 15분 SIP 를 못 묻는다: 끝은 20분 전
        val end = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(Instant.now().epochSecond - 20 * 60))   // ios: Data/FlowData.swift:369
        val bySym = HashMap<String, String>()   // 알파카 기호 → 사이트 티커   // ios: Data/FlowData.swift:370
        for ((key, sym) in need) bySym[sym.uppercase()] = key   // ios: Data/FlowData.swift:371
        val rows = HashMap<String, HashMap<String, FlowBars.Row>>(); var token: String? = null; var got = false   // ios: Data/FlowData.swift:372
        for (page in 0 until 20) {   // ios: Data/FlowData.swift:373
            val q = mutableListOf("symbols" to bySym.keys.sorted().joinToString(","),   // ios: Data/FlowData.swift:374,375
                "timeframe" to "1Day", "start" to start, "end" to end,   // ios: Data/FlowData.swift:376
                "adjustment" to "raw", "feed" to "sip", "limit" to "10000")   // ios: Data/FlowData.swift:377
            token?.let { q.add("page_token" to it) }   // ios: Data/FlowData.swift:378
            val url = base + "?" + q.joinToString("&") { it.first + "=" + enc(it.second) }
            val wait = 350 - (System.currentTimeMillis() - last)   // ios: Data/FlowData.swift:379
            if (wait > 0) delay(wait)   // ios: Data/FlowData.swift:380
            last = System.currentTimeMillis()   // ios: Data/FlowData.swift:381
            val r = FeedReq(url, timeout = 15.0)   // ios: Data/FlowData.swift:382
            r.setValue(k.id, "APCA-API-KEY-ID")   // ios: Data/FlowData.swift:383
            r.setValue(k.secret, "APCA-API-SECRET-KEY")   // ios: Data/FlowData.swift:384
            val (code, d) = runCatching { r.send() }.getOrNull() ?: break; if (code != 200) break   // ios: Data/FlowData.swift:385
            val o = jObj(feedJson(d)) ?: break   // ios: Data/FlowData.swift:386
            got = true   // ios: Data/FlowData.swift:387
            for ((sym, arr) in jObj(o["bars"]) ?: emptyMap()) {   // ios: Data/FlowData.swift:388
                val key = bySym[sym.uppercase()] ?: continue   // ios: Data/FlowData.swift:389
                for (v in (arr as? List<*>) ?: emptyList<Any?>()) { val m = jObj(v) ?: continue   // ios: Data/FlowData.swift:390
                    val t = (m["t"] as? String)?.takeIf { it.length >= 10 } ?: continue; val cl = num(m["c"]) ?: continue; val op = num(m["o"]) ?: continue; val h = num(m["h"]) ?: continue; val l = num(m["l"]) ?: continue; if (!(cl > 0 && op > 0)) continue   // ios: Data/FlowData.swift:391
                    rows.getOrPut(key) { HashMap() }[t.take(10)] = FlowBars.Row(o = op, h = maxOf(h, op, cl), l = minOf(l, op, cl), c = cl, v = num(m["v"]) ?: 0.0)   // ios: Data/FlowData.swift:392
                }
            }
            token = o["next_page_token"] as? String   // ios: Data/FlowData.swift:395
            if (token == null) break   // ios: Data/FlowData.swift:396
        }
        if (!got) return@withContext false   // ios: Data/FlowData.swift:398
        val day = f.day.toMutableMap()
        for (key in need.keys) {   // ios: Data/FlowData.swift:399
            f.merge(key, rows[key] ?: emptyMap(), full = fresh && (f.b[key]?.d?.size ?: 0) < 30, keep = { it >= from && (it < md || (it == md && closed)) })   // ios: Data/FlowData.swift:400
            day[key] = stamp   // ios: Data/FlowData.swift:401
        }
        f.day = day
        FlowBars.write(f, FlowBars.alpacaName)   // ios: Data/FlowData.swift:403
        true   // ios: Data/FlowData.swift:404
    }
}

/** 차트 탭이 보는 실데이터: 토스 캔들을 먼저, 없으면 알파카. 종목마다 분석(추세·패턴)은 한 번만 하고 마지막 날이 바뀔 때만 다시 */
object FlowStore {   // ios: Data/FlowData.swift:409,410,411
    val shared: FlowStore get() = this
    var toss by mutableStateOf(FlowBars())   // ios: Data/FlowData.swift:412
        private set
    var alpaca by mutableStateOf(FlowBars())   // ios: Data/FlowData.swift:413
        private set
    var loading by mutableStateOf(setOf<String>())   // ios: Data/FlowData.swift:414
        private set
    var failed by mutableStateOf(setOf<String>())    // 받아 보았는데 일봉이 없는 종목 (빈 칸 대신 '못 받았어요')   // ios: Data/FlowData.swift:415
        private set
    private val memo = HashMap<String, Triple<String, String, FlowSeries>>()   // 종목 → (일봉 표시, 오늘 캔들까지 표시, 계산)
    private val today = HashMap<String, Pair<String, Candle>>()   // 장중 오늘 캔들: 시가는 전일 종가, 고가·저가는 앱이 본 지금 가격으로 넓힌다   // ios: Data/FlowData.swift:417   // ios: Data/FlowData.swift:416
    /** 계산해 둔 것이 늘면 화면이 다시 꺼내 보게 */
    private var ver by mutableStateOf(0)
    @Volatile private var loaded = false

    fun reload() {   // ios: Data/FlowData.swift:419
        toss = if (TossKeys.on) FlowBars.read(FlowBars.tossName) else FlowBars()   // ios: Data/FlowData.swift:420
        alpaca = if (AlpacaKeys.on) FlowBars.read(FlowBars.alpacaName) else FlowBars()   // ios: Data/FlowData.swift:421
        loaded = true
    }
    /** 처음 한 번 파일 읽기 (iOS 는 만들 때 읽는다. 화면 그리는 중 파일을 읽지 않게 ensure 에서 부른다) */
    suspend fun load() { if (!loaded) withContext(Dispatchers.IO) { reload() } }

    fun isKR(id: String): Boolean = Sample.symbol(id)?.currency == Currency.krw   // ios: Data/FlowData.swift:424
    /** 이 종목을 받을 키가 있는지: 한국은 토스, 미국은 토스나 알파카 */
    fun canFetch(id: String): Boolean {   // ios: Data/FlowData.swift:426
        if (Sample.isVirtual(id) || id == KoreaEstimate.sumId) return false   // ios: Data/FlowData.swift:427
        return TossKeys.on || (!isKR(id) && AlpacaKeys.on)   // ios: Data/FlowData.swift:428
    }

    fun bars(id: String): FlowBars.Series? {   // ios: Data/FlowData.swift:431
        val k = SiteStats.key(id)   // ios: Data/FlowData.swift:432
        if (TossKeys.on) toss.b[k]?.takeIf { it.d.size >= 30 }?.let { return it }   // ios: Data/FlowData.swift:433
        if (AlpacaKeys.on && !isKR(id)) alpaca.b[k]?.takeIf { it.d.size >= 30 }?.let { return it }   // ios: Data/FlowData.swift:434
        return null   // ios: Data/FlowData.swift:435
    }

    private fun stamp(b: FlowBars.Series) = "${b.d.size}" + (b.d.lastOrNull() ?: "")   // 442 의 앞 조각
    fun series(id: String): FlowSeries? {   // ios: Data/FlowData.swift:438
        if (id == "DRNK") return DrnkSample.series   // ios: Data/FlowData.swift:439
        val b = bars(id) ?: return null   // ios: Data/FlowData.swift:440
        val lv = live(id, b)   // ios: Data/FlowData.swift:441
        val base = stamp(b); val stamp = base + (lv?.let { "L${it.second.c},${it.second.h},${it.second.l}" } ?: "")   // ios: Data/FlowData.swift:442
        synchronized(memo) { memo[id]?.takeIf { it.second == stamp }?.let { return it.third } }   // ios: Data/FlowData.swift:443
        val s = lv?.let { FlowSeries(candles = b.candles + it.second, dates = b.d + it.first, live = true) } ?: FlowSeries(candles = b.candles, dates = b.d)   // ios: Data/FlowData.swift:444
        synchronized(memo) { memo[id] = Triple(base, stamp, s) }   // ios: Data/FlowData.swift:445
        return s   // ios: Data/FlowData.swift:446
    }

    /** 그 시장의 오늘 날짜와, 장이 열린 평일인지 (한국 9:00, 미국 9:30 부터. 마감 뒤에도 일봉을 받기 전까지는 연 것으로) */
    fun marketNow(kr: Boolean): Pair<String, Boolean> {   // ios: Data/FlowData.swift:450
        val c = Instant.now().atZone(ZoneId.of(if (kr) "Asia/Seoul" else "America/New_York"))   // ios: Data/FlowData.swift:451,452,453
        val d = cformat("%04d-%02d-%02d", c.year, c.monthValue, c.dayOfMonth)   // ios: Data/FlowData.swift:454
        return d to ((c.dayOfWeek.value % 7 + 1) in 2..6 && c.hour * 60 + c.minute >= (if (kr) 9 * 60 else 9 * 60 + 30))   // ios: Data/FlowData.swift:455
    }

    /** 장중 오늘 캔들 (날짜, 캔들). 지금 가격이 없거나, 일봉에 오늘이 이미 있거나, 전일 종가 그대로면(휴장) 없음 */
    private fun live(id: String, b: FlowBars.Series): Pair<String, Candle>? {   // ios: Data/FlowData.swift:459
        val s = Sample.symbol(id) ?: return null; val prev = b.c.lastOrNull() ?: return null; val last = b.d.lastOrNull() ?: return null   // ios: Data/FlowData.swift:460
        val q = Market.shared.quote(s)   // ios: Data/FlowData.swift:461
        val (md, open) = marketNow(isKR(id))   // ios: Data/FlowData.swift:462
        if (!q.live || q.last <= 0 || !open || last >= md || abs(q.last - prev) <= 1e-9) return null   // ios: Data/FlowData.swift:463
        return synchronized(today) {
            val k = today[id]?.takeIf { it.first == md }?.second?.copy() ?: Candle(o = prev, h = max(prev, q.last), l = min(prev, q.last), c = q.last, v = 0.0)   // ios: Data/FlowData.swift:464
            k.c = q.last; k.h = max(k.h, q.last); k.l = min(k.l, q.last)   // ios: Data/FlowData.swift:465
            today[id] = md to k   // ios: Data/FlowData.swift:466
            md to k   // ios: Data/FlowData.swift:467
        }
    }

    /** 화면용: 계산해 둔 것만 (없으면 null, warm 이 끝나면 다시 그린다). DRNK 는 처음 계산이 끝났을 때부터 */
    fun peek(id: String): FlowSeries? {
        ver
        if (id == "DRNK") return if (drnkReady) DrnkSample.series else null
        val b = bars(id) ?: return null
        // 오늘 캔들 값이 바뀌어 warm 이 다시 계산하는 동안에도 일봉이 같으면 앞 계산을 그대로 보인다
        return synchronized(memo) { memo[id]?.takeIf { it.first == stamp(b) }?.third }
    }
    @Volatile private var drnkReady = false
    /** 칩 줄 종목의 추세·패턴을 뒤에서 미리 계산한다 */
    suspend fun warm(ids: List<String>) {
        load()
        withContext(Dispatchers.Default) { ids.forEach { series(it) } }
        if ("DRNK" in ids) drnkReady = true
        ver += 1
    }

    /** 차트 탭을 열 때: 아직 없는 담은 종목을 받는다. 토스는 기존 시세 갱신 흐름으로, 알파카는 여기서 한 번에 */
    suspend fun ensure(ids: List<String>) {   // ios: Data/FlowData.swift:471
        load()
        val want = ids.filter { canFetch(it) }   // ios: Data/FlowData.swift:472
        if (want.isEmpty()) return   // ios: Data/FlowData.swift:473
        if (TossKeys.on && want.any { bars(it) == null }) {   // ios: Data/FlowData.swift:474
            loading = loading + want.filter { bars(it) == null }   // ios: Data/FlowData.swift:475
            // 지금 값도 같이 받으니 담은 종목 전부로. 이미 받는 중이면 끝나길 기다렸다가 한 번 더 (그냥 건너뛰면 탭을 열어도 안 받는다)
            Market.shared.refreshToss(held = ids.toSet(), force = true)   // ios: Data/FlowData.swift:477
        }
        if (AlpacaKeys.on) {   // ios: Data/FlowData.swift:479
            val keys = HashMap<String, String>()   // ios: Data/FlowData.swift:480
            for (id in want) { if (!(!isKR(id) && (!TossKeys.on || (toss.b[SiteStats.key(id)]?.let { it.d.size < 30 } ?: true)))) continue   // ios: Data/FlowData.swift:481
                val s = Sample.symbol(id) ?: continue; val c = AlpacaFeed.code(CustomSymbols.yahoo(s)) ?: continue; keys[SiteStats.key(id)] = c   // ios: Data/FlowData.swift:482
            }
            if (keys.isNotEmpty()) {   // ios: Data/FlowData.swift:484
                loading = loading + want.filter { bars(it) == null }   // ios: Data/FlowData.swift:485
                if (FlowAlpaca.fetch(keys)) withContext(Dispatchers.IO) { reload() }   // ios: Data/FlowData.swift:486
            }
        }
        loading = emptySet()   // ios: Data/FlowData.swift:489
        failed = want.filter { bars(it) == null }.toSet()   // ios: Data/FlowData.swift:490
        if (failed.isNotEmpty()) android.util.Log.i("naeilo.flow", "일봉 없음: " + failed.joinToString { "$it(${SiteStats.key(it)}) 토스 ${toss.b[SiteStats.key(it)]?.d?.size ?: -1} 알파카 ${alpaca.b[SiteStats.key(it)]?.d?.size ?: -1}" })
    }
}
