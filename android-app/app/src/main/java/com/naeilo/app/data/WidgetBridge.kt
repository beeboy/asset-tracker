package com.naeilo.app.data

import com.naeilo.app.core.calc.bare
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.Store
import com.naeilo.app.core.WChar
import com.naeilo.app.core.WFeed
import com.naeilo.app.core.WItem
import com.naeilo.app.core.WL
import com.naeilo.app.core.WLive
import com.naeilo.app.core.WReward
import com.naeilo.app.core.WSmall
import com.naeilo.app.core.WSummary
import com.naeilo.app.core.WidgetLine
import com.naeilo.app.core.calc.goalToDate
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keyName
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.calc.parallelRace
import com.naeilo.app.core.calc.parallelSeries
import com.naeilo.app.core.calc.pathStartDate
import com.naeilo.app.core.calc.totalAt
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.cformat
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.Market
import com.naeilo.app.ui.analysis.MyPath
import com.naeilo.app.ui.analysis.myPath
import com.naeilo.app.ui.analysis.myPathGapToday
import com.naeilo.app.ui.analysis.pathSeries
import com.naeilo.app.ui.me.MeArt
import com.naeilo.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// 앱이 계산한 숫자를 위젯에 넘긴다. 위젯은 앱 내부 폴더의
// summary.json(요약) · feed.json(지금 시세) · live.json(보유·목표)을 읽어 그린다.
// ios: Data/WidgetBridge.swift:7
object WidgetBridge {
    private val io = CoroutineScope(Dispatchers.IO)

    /** 위젯 평가 환율: 앱 valFx 와 같게 매매기준율, 없으면 내 키 원/달러.
     *  예전엔 늘 매매기준율이라 내 키를 쓰는 앱·증권사 화면과 위젯 금액이 달랐다 */
    // ios: Data/WidgetBridge.swift:10
    fun fxMar(): WFeed.Mar? {
        // ios: Data/WidgetBridge.swift:11
        Market.shared.marUse?.let { m -> return WFeed.Mar(rate = m.rate, date = m.date) }
        // ios: Data/WidgetBridge.swift:12
        Market.shared.tossFx?.let { t -> return WFeed.Mar(rate = t.rate, date = Day.str(Instant.ofEpochMilli((t.got * 1000).toLong()))) }
        // ios: Data/WidgetBridge.swift:13
        return null
    }

    /** 모델 값은 지금(메인) 읽고, 파일 쓰기와 위젯 다시 그리기는 뒤에서 (화면이 멈추지 않게) */
    // ios: Data/WidgetBridge.swift:15,16
    fun write(m: AppModel) {
        // ios: Data/WidgetBridge.swift:17
        val fx = Market.shared.fx
        // ios: Data/WidgetBridge.swift:18
        val rows = m.rows
        // ios: Data/WidgetBridge.swift:19
        val now = Instant.now()
        // ios: Data/WidgetBridge.swift:20
        val goal = m.keyValue
        // 위젯 페이스(시작·목표일·시작 금액)는 앱 내 길과 같은 값: 시작 = 내 길 시작일, 목표일 = 목표 루트는 목표일, 회복 루트는 시작 뒤 1년
        // (전망 부채꼴·확률은 앱 전망 그대로라 회복 루트는 3년. 위젯도 '3년 뒤'로 적는다)
        // ios: Data/WidgetBridge.swift:23
        val path = m.myPath
        // ios: Data/WidgetBridge.swift:24
        val start = m.pathStartDate
        // ios: Data/WidgetBridge.swift:25
        val end = if (m.isGoal) m.goalToDate else start.atZone(ZoneId.systemDefault()).plusYears(1).toInstant()
        // ios: Data/WidgetBridge.swift:26
        val cash = m.cashKrw      // 목표 루트에서 사이트에 넣은 현금 (앱 홈·내 길처럼 총액에 더한다). 회복·시작 전·샘플은 0

        // 지금 시세: [지금 가격, 전일 종가, 시각]
        // ios: Data/WidgetBridge.swift:29
        val q = mutableMapOf<String, List<Double?>>(); val ccy = mutableMapOf<String, String>()
        // ios: Data/WidgetBridge.swift:30
        for (r in rows) {
            // ios: Data/WidgetBridge.swift:31
            val qt = Market.shared.quote(r.sym)
            // ios: Data/WidgetBridge.swift:32
            q[r.id] = listOf(qt.last, qt.prevClose, now.toEpochMilli() / 1000.0)
            // ios: Data/WidgetBridge.swift:33
            ccy[r.id] = if (r.sym.currency == com.naeilo.app.data.Currency.usd) "USD" else "KRW"
        }
        // ios: Data/WidgetBridge.swift:35
        q["KRW=X"] = listOf(fx.last, fx.prevClose, now.toEpochMilli() / 1000.0)
        // 내부 테스트 국내 시세 종목: 위젯이 Yahoo 중계로 직접 받는다 (앱을 열지 않아도 오늘 값)
        // ios: Data/WidgetBridge.swift:37
        val ky = mutableMapOf<String, String>()
        // ios: Data/WidgetBridge.swift:38
        for (r in rows) if (r.sym.currency == com.naeilo.app.data.Currency.krw && CustomSymbols.shared.isLive(r.id) && !Market.shared.tossCovers(r.id)) ky[r.id] = CustomSymbols.yahoo(r.sym)
        // ios: Data/WidgetBridge.swift:39
        val feed = WFeed(v = 1, u = DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)), q = q, c = emptyMap(), ccy = ccy, mar = fxMar())   // 위젯 평가액도 앱과 같은 원/달러 (valFx)
        // ios: Data/WidgetBridge.swift:40
        feed.ky = ky.ifEmpty { null }
        // 토스로 받을 종목 (Market.refreshToss 와 같은 목록): 내 키가 있으면 위젯이 이걸로 토스 지금 가격을 받는다
        // ios: Data/WidgetBridge.swift:42
        val ty = mutableMapOf<String, String>()
        // ios: Data/WidgetBridge.swift:43
        for (r in rows) if (!Sample.isVirtual(r.id) && r.id != KoreaEstimate.sumId) ty[r.id] = CustomSymbols.yahoo(r.sym)
        // ios: Data/WidgetBridge.swift:44
        feed.ty = ty.ifEmpty { null }

        // ios: Data/WidgetBridge.swift:46,47,48
        val live = WLive(hold = rows.map { WLive.Hold(t = it.id, sh = it.h.qty, price = Market.shared.quote(it.sym).last) },
            cashKrw = cash, cashUsd = 0.0, goalAmount = goal, goalDate = Day.str(end), goalStart = Day.str(start),
            manualPrice = true, stateAt = now.toEpochMilli() / 1000.0 * 1000)

        // 요약: 최근 3달 평가액(만원), 3년 전망 부채꼴, 이번 주 예보
        // 최근 3달은 앱 내 길과 같은 식: 그날 수량(수량 기록) × 그날 종가 × 그날 원/달러 종가 + 현금. 마지막 점은 위젯이 지금 값으로 바꾼다
        // ios: Data/WidgetBridge.swift:52
        val hist: List<Double> = if (m.isSample || m.bare) emptyList() else m.pathSeries(Day.str(now.atZone(ZoneId.systemDefault()).minusMonths(3).toInstant())).map { it.second }
        // ios: Data/WidgetBridge.swift:53,54,55
        val spark: List<Double?> = if (hist.isEmpty())
            (0 until 63).map { i -> (m.totalAt(1 - (62 - i).toDouble() / 756) + cash) / 1e4 }   // 샘플·시작 전·과거 시세가 없을 때: 지금 수량 × 과거 가격
            else (hist.takeLast(62) + (m.total + cash)).map { it / 1e4 }
        // ios: Data/WidgetBridge.swift:56
        val f = m.forecast
        // ios: Data/WidgetBridge.swift:57
        val man = { a: List<Double> -> a.map { it / 1e4 } }
        // 앱 전망과 같은 값 (웹과 같은 몬테카를로, 목표일까지 월 경계마다)
        // ios: Data/WidgetBridge.swift:59,60
        val fc = WSummary.Forecast(scen = m.lens.label, md = f.dates, p5 = man(f.q5), p25 = man(f.q25), p50 = man(f.q50), p75 = man(f.q75), p95 = man(f.q95),
            pg = f.pGoal, t = WSummary.Term(p5 = (f.q5.lastOrNull() ?: 0.0) / 1e4, p50 = (f.q50.lastOrNull() ?: 0.0) / 1e4, p95 = (f.q95.lastOrNull() ?: 0.0) / 1e4))
        // ios: Data/WidgetBridge.swift:61
        val wr = m.weekRange
        // ios: Data/WidgetBridge.swift:62
        val friday = now.atZone(ZoneId.systemDefault()).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(6 - 1))).toInstant()   // 아이폰 weekday 6 (일=1) = 금요일
        // ios: Data/WidgetBridge.swift:63
        val week = WSummary.Week(f = Day.str(friday), p50 = (wr.lo + wr.hi) / 2, lo = wr.lo, hi = wr.hi, base = m.total, cash = cash, t0 = m.total, tent = null)
        // ios: Data/WidgetBridge.swift:64
        val hits = m.pastWeeks.mapIndexed { i, h -> WSummary.Hit(f = Day.str(now.minusSeconds(((8 - i) * 7 * 86400).toLong())), h = h, r = null) }
        // ios: Data/WidgetBridge.swift:65,66,67,68,69
        val summary = WSummary(v = 2, at = now.toEpochMilli() / 1000.0 * 1000, asof = Day.today, today = Day.today, cash = cash,
            goal = WSummary.Goal(amount = goal, date = Day.str(end), start = Day.str(start), v0 = path.pts.firstOrNull()?.second ?: m.trackValue, m = path.monthly),
            hold = rows.map { WSummary.Hold(t = it.id, sh = it.h.qty, ccy = ccy[it.id] ?: "USD", manual = Market.shared.quote(it.sym).last) },
            spark = spark, fc = fc, hits = hits, week = week,
            prev = m.total / (1 + m.todayMove) + cash, events = emptyList())

        // 인물 보상 위젯: 본전(목표)까지, 1000칸, 오늘의 움직임
        // ios: Data/WidgetBridge.swift:72
        val key = max(1.0, goal); val track = m.trackValue; val yTrack = track / (1 + m.todayMove)
        // ios: Data/WidgetBridge.swift:73
        val frac = { v: Double -> max(0.0, min(1.0, v / key)) }
        // 위젯은 큰 3개를 칸으로, 나머지는 '외 n개' 줄로
        // ios: Data/WidgetBridge.swift:75,76
        val tiles = rows.sortedByDescending { it.value }.take(30).map { r ->
            WReward.Tile(t = if (r.sym.currency == com.naeilo.app.data.Currency.usd) r.id else r.sym.name, w = if (m.total > 0) r.value / m.total else 0.0, c = Market.shared.quote(r.sym).change)
        }
        // ios: Data/WidgetBridge.swift:78,79,80,81,82,83
        val reward = WReward(keyName = m.keyName, pct = frac(track), pctYesterday = frac(yTrack), remain = max(0.0, key - track),
            cells = (frac(track) * 1000).toInt(), cellsYesterday = (frac(yTrack) * 1000).toInt(),
            total = m.total, dayChg = m.todayMove, tiles = tiles, next = MarketEvents.widgetNext(held = rows.map { it.id }),
            friendsOn = Shelter.friends.withIndex().filter { m.friendOn(it.index) }.map { it.value.id } + (if (m.meUnlocked) listOf("me") else emptyList()),
            homeFriend = m.homeFriendShown, spark = spark.takeLast(10).mapNotNull { it?.let { v -> v - cash / 1e4 } },   // 인물 위젯 총액(m.total)처럼 현금은 뺀다
            items = Shelter.items.take(m.itemsOn).map { it.id })
        // ios: Data/WidgetBridge.swift:84
        val sy = says(reward)
        // 내 캐릭터는 투자 유형 인물의 대사를 같이 쓴다
        // ios: Data/WidgetBridge.swift:86
        if (m.meUnlocked) { sy.first["me"] = sy.first[m.meVoice] ?: ""; sy.second["me"] = sy.second[m.meVoice] ?: emptyList() }
        // ios: Data/WidgetBridge.swift:87
        reward.says = sy.first; reward.saysNext = sy.second
        // 홈 리마인드: 오늘 생긴 큰 달성은 오늘의 움직임 말풍선에, 1000칸 이정표는 블록 위젯 머리에 (그날만)
        // ios: Data/WidgetBridge.swift:89
        reward.news = Remind.shared.widgetNews; reward.badge = Remind.shared.widgetBadge; reward.newsDay = Day.today
        // ios: Data/WidgetBridge.swift:90
        MeArt.push(m)
        val small = small(m, path, cash = cash)
        // ios: Data/WidgetBridge.swift:100
        val step = if (m.playOn) 3 else max(1, m.nxStep)
        io.launch {
            // ios: Data/WidgetBridge.swift:91
            Store.write(WReward.serializer(), reward, "reward.json")
            // ios: Data/WidgetBridge.swift:92
            Store.write(WSummary.serializer(), summary, "summary.json")
            // ios: Data/WidgetBridge.swift:93
            Store.write(WFeed.serializer(), feed, "feed.json")
            // ios: Data/WidgetBridge.swift:94
            Store.write(WLive.serializer(), live, "live.json")
            // ios: Data/WidgetBridge.swift:95
            Story.pushArt(m)
            // ios: Data/WidgetBridge.swift:96
            Store.write(com.naeilo.app.core.WStory.serializer(), Story.shared.widget(m), "story.json")   // 서재 위젯: 읽던 자리 한 문단
            // ios: Data/WidgetBridge.swift:97
            Store.write(WSmall.serializer(), small, WSmall.file)   // 작은 위젯 내 길·목표 페이스
            // ios: Data/WidgetBridge.swift:98
            Store.lastCheck = now
            // ios: Data/WidgetBridge.swift:99
            Store.summarySource = "app"
            // 위젯은 미션 4에서 실제로 한 단계에 맞춰 하나씩 열린다 (위 step)
            Store.widgetStep = step   // 위젯은 미션 4에서 실제로 한 단계에 맞춰 하나씩 열린다 (개발자 계정도 같게)
            // ios: Data/WidgetBridge.swift:101
            Widgets.reloadAllTimelinesAsync(Store.ctx)
        }
    }

    /** 작은 위젯 값: 내 길(실제는 40점 안쪽으로 줄인다, 계획선 41점, 앞섬)과 평행 세계 나·모의 바구니 수익률 */
    // ios: Data/WidgetBridge.swift:105
    fun small(m: AppModel, p: MyPath, cash: Double): WSmall {
        // ios: Data/WidgetBridge.swift:106
        val e = max(p.end, 1e-6); val step = max(1, p.pts.size / 40)
        // ios: Data/WidgetBridge.swift:107
        val pts = (0 until p.pts.size step step).map { listOf(p.pts[it].first / e, p.pts[it].second) }.toMutableList()
        // ios: Data/WidgetBridge.swift:108
        p.pts.lastOrNull()?.let { l -> if (pts.lastOrNull()?.firstOrNull() != l.first / e) pts.add(listOf(l.first / e, l.second)) }
        // ios: Data/WidgetBridge.swift:109
        var me: Double? = null; var bk: Double? = null
        // ios: Data/WidgetBridge.swift:110
        m.parallelRace?.takeIf { it.last >= 0 }?.let { race ->
            // ios: Data/WidgetBridge.swift:111
            val r = m.parallelSeries(race)
            // ios: Data/WidgetBridge.swift:112
            if (r.size > 1 && race.last < r[0].size && race.last < r[1].size) { me = r[0][race.last]; bk = r[1][race.last] }
        }
        // ios: Data/WidgetBridge.swift:114
        return WSmall(path = pts, need = (0..40).map { p.need(e * it / 40) }, gap = m.myPathGapToday, t0 = m.total + cash, me = me, basket = bk)
    }

    /** 인물별 위젯 말풍선: 사흘에 한 번은 돌아온 물건 얘기, 나머지 날은 오늘 가장 크게 움직인 종목 (대사 데이터에서)
     *  위젯은 갱신하거나 ↻를 누를 때마다 다음 줄을 쓴다 (saysNext). 앱이 다시 쓸 때는 위젯에서 본 만큼 넘기고 그 다음부터 준다 */
    // ios: Data/WidgetBridge.swift:119
    fun says(r: WReward): Pair<MutableMap<String, String>, MutableMap<String, List<String>>> {
        // ios: Data/WidgetBridge.swift:120
        val top = r.tiles.maxByOrNull { abs(it.c) }
        // ios: Data/WidgetBridge.swift:121
        val t = top?.t ?: WL.t("오늘"); val c = top?.c ?: r.dayChg
        // ios: Data/WidgetBridge.swift:122
        val seen = WidgetLine.index()
        // ios: Data/WidgetBridge.swift:123
        val now = mutableMapOf<String, String>(); val next = mutableMapOf<String, List<String>>()
        // ios: Data/WidgetBridge.swift:124
        for (w in WChar.all) {
            // Data/WidgetBridge.swift:125 (검사기 한계: 보간 속 따옴표, 예외 목록 참고)
            var key = "widget.${if (c >= 0) "up" else "down"}.${w.id}"
            // ios: Data/WidgetBridge.swift:126
            var vars = mapOf("t" to t, "c" to cformat("%.1f", abs(c) * 100))
            // ios: Data/WidgetBridge.swift:127
            var fallback: String? = null
            // ios: Data/WidgetBridge.swift:128
            if (!WL.en) WItem.today(r)?.let { it -> key = "item.$it.${w.id}"; vars = emptyMap(); fallback = WItem.say(it, w.id) }   // 물건 대사는 아직 한국어만
            // ios: Data/WidgetBridge.swift:129
            Lines.skip(key, seen)
            // ios: Data/WidgetBridge.swift:130
            val list = Lines.peek(key, vars, 10)
            // ios: Data/WidgetBridge.swift:131
            next[w.id] = list
            // ios: Data/WidgetBridge.swift:132
            (list.firstOrNull() ?: fallback)?.let { now[w.id] = it }
        }
        // ios: Data/WidgetBridge.swift:134
        WidgetLine.reset()
        // ios: Data/WidgetBridge.swift:135
        return now to next
    }
}
