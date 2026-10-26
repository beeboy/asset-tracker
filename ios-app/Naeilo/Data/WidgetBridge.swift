import Foundation
import WidgetKit

// 앱이 계산한 숫자를 위젯에 넘긴다. 위젯(기존 ios/ 위젯 앱에서 옮김)은 App Group 폴더의
// summary.json(요약) · feed.json(지금 시세) · live.json(보유·목표)을 읽어 그린다.
// 예전 위젯 앱은 사이트 동기화 비밀번호로 이 파일들을 받았지만, 한 앱으로 합치면서 앱이 직접 쓴다.
enum WidgetBridge {
    /// 위젯 평가 환율: 앱 valFx 와 같게 매매기준율, 없으면 내 키 원/달러.
    /// 예전엔 늘 매매기준율이라 내 키를 쓰는 앱·증권사 화면과 위젯 금액이 달랐다
    @MainActor static func fxMar() -> WFeed.Mar? {
        if let m = Market.shared.marUse { return WFeed.Mar(rate: m.rate, date: m.date) }
        if let t = Market.shared.tossFx { return WFeed.Mar(rate: t.rate, date: Day.str(Date(timeIntervalSince1970: t.got))) }
        return nil
    }
    @MainActor
    static func write(_ m: AppModel) {
        let fx = Market.shared.fx
        let rows = m.rows
        let now = Date()
        let goal = m.keyValue
        // 위젯 페이스(시작·목표일·시작 금액)는 앱 내 길과 같은 값: 시작 = 내 길 시작일, 목표일 = 목표 루트는 목표일, 회복 루트는 시작 뒤 1년
        // (전망 부채꼴·확률은 앱 전망 그대로라 회복 루트는 3년. 위젯도 '3년 뒤'로 적는다)
        let path = m.myPath
        let start = m.pathStartDate
        let end = m.isGoal ? m.goalToDate : Calendar.current.date(byAdding: .year, value: 1, to: start)!
        let cash = m.cashKrw      // 목표 루트에서 사이트에 넣은 현금 (앱 홈·내 길처럼 총액에 더한다). 회복·시작 전·샘플은 0

        // 지금 시세: [지금 가격, 전일 종가, 시각]
        var q: [String: [Double?]] = [:], ccy: [String: String] = [:]
        for r in rows {
            let qt = r.sym.quote
            q[r.id] = [qt.last, qt.prevClose, now.timeIntervalSince1970]
            ccy[r.id] = r.sym.currency == .usd ? "USD" : "KRW"
        }
        q["KRW=X"] = [fx.last, fx.prevClose, now.timeIntervalSince1970]
        // 내부 테스트 국내 시세 종목: 위젯이 Yahoo 중계로 직접 받는다 (앱을 열지 않아도 오늘 값)
        var ky: [String: String] = [:]
        for r in rows where r.sym.currency == .krw && CustomSymbols.shared.isLive(r.id) && !Market.shared.tossCovers(r.id) { ky[r.id] = CustomSymbols.yahoo(for: r.sym) }
        var feed = WFeed(v: 1, u: ISO8601DateFormatter().string(from: now), q: q, c: [:], ccy: ccy, mar: Self.fxMar())   // 위젯 평가액도 앱과 같은 원/달러 (valFx)
        feed.ky = ky.isEmpty ? nil : ky
        // 토스로 받을 종목 (Market.refreshToss 와 같은 목록): 내 키가 있으면 위젯이 이걸로 토스 지금 가격을 받는다
        var ty: [String: String] = [:]
        for r in rows where !Sample.isVirtual(r.id) && r.id != KoreaEstimate.sumId { ty[r.id] = CustomSymbols.yahoo(for: r.sym) }
        feed.ty = ty.isEmpty ? nil : ty

        let live = WLive(hold: rows.map { .init(t: $0.id, sh: $0.h.qty, price: $0.sym.last) },
                         cashKrw: cash, cashUsd: 0, goalAmount: goal, goalDate: Day.str(end), goalStart: Day.str(start),
                         manualPrice: true, stateAt: now.timeIntervalSince1970 * 1000)

        // 요약: 최근 3달 평가액(만원), 3년 전망 부채꼴, 이번 주 예보
        // 최근 3달은 앱 내 길과 같은 식: 그날 수량(수량 기록) × 그날 종가 × 그날 원/달러 종가 + 현금. 마지막 점은 위젯이 지금 값으로 바꾼다
        let hist: [Double] = m.isSample || m.bare ? [] : m.pathSeries(from: Day.str(Calendar.current.date(byAdding: .month, value: -3, to: now)!)).map(\.v)
        let spark: [Double?] = hist.isEmpty
            ? (0..<63).map { i in Optional((m.totalAt(1 - Double(62 - i) / 756) + cash) / 1e4) }   // 샘플·시작 전·과거 시세가 없을 때: 지금 수량 × 과거 가격
            : (hist.suffix(62) + [m.total + cash]).map { Optional($0 / 1e4) }
        let f = m.forecast
        let man = { (a: [Double]) in a.map { Optional($0 / 1e4) } }
        // 앱 전망과 같은 값 (웹과 같은 몬테카를로, 목표일까지 월 경계마다)
        let fc = WSummary.Forecast(scen: m.lens.label, md: f.dates, p5: man(f.q5), p25: man(f.q25), p50: man(f.q50), p75: man(f.q75), p95: man(f.q95),
                                   pg: f.pGoal, t: .init(p5: (f.q5.last ?? 0) / 1e4, p50: (f.q50.last ?? 0) / 1e4, p95: (f.q95.last ?? 0) / 1e4))
        let wr = m.weekRange
        let friday = Calendar.current.nextDate(after: now, matching: DateComponents(weekday: 6), matchingPolicy: .nextTime) ?? now
        let week = WSummary.Week(f: Day.str(friday), p50: (wr.lo + wr.hi) / 2, lo: wr.lo, hi: wr.hi, base: m.total, cash: cash, t0: m.total, tent: nil)
        let hits = m.pastWeeks.enumerated().map { WSummary.Hit(f: Day.str(now.addingTimeInterval(-Double(8 - $0) * 7 * 86400)), h: $1, r: nil) }
        let summary = WSummary(v: 2, at: now.timeIntervalSince1970 * 1000, asof: Day.today, today: Day.today, cash: cash,
                               goal: .init(amount: goal, date: Day.str(end), start: Day.str(start), v0: path.pts.first?.1 ?? m.trackValue, m: path.monthly),
                               hold: rows.map { .init(t: $0.id, sh: $0.h.qty, ccy: ccy[$0.id] ?? "USD", manual: $0.sym.last) },
                               spark: spark, fc: fc, hits: hits, week: week,
                               prev: m.total / (1 + m.todayMove) + cash, events: [])

        // 인물 보상 위젯: 본전(목표)까지, 1000칸, 오늘의 움직임
        let key = max(1, goal), track = m.trackValue, yTrack = track / (1 + m.todayMove)
        let frac = { (v: Double) in max(0, min(1, v / key)) }
        // 위젯은 큰 3개를 칸으로, 나머지는 '외 n개' 줄로
        let tiles = rows.sorted { $0.value > $1.value }.prefix(30).map { r in
            WReward.Tile(t: r.sym.currency == .usd ? r.id : r.sym.name, w: m.total > 0 ? r.value / m.total : 0, c: r.sym.quote.change)
        }
        var reward = WReward(keyName: m.keyName, pct: frac(track), pctYesterday: frac(yTrack), remain: max(0, key - track),
                             cells: Int(frac(track) * 1000), cellsYesterday: Int(frac(yTrack) * 1000),
                             total: m.total, dayChg: m.todayMove, tiles: Array(tiles), next: MarketEvents.widgetNext(held: rows.map(\.id)),
                             friendsOn: Shelter.friends.enumerated().filter { m.friendOn($0.offset) }.map(\.element.id) + (m.meUnlocked ? ["me"] : []),
                             homeFriend: m.homeFriendShown, spark: spark.suffix(10).compactMap { $0.map { $0 - cash / 1e4 } },   // 인물 위젯 총액(m.total)처럼 현금은 뺀다
                             items: Shelter.items.prefix(m.itemsOn).map(\.id))
        var sy = says(reward)
        // 내 캐릭터는 투자 유형 인물의 대사를 같이 쓴다
        if m.meUnlocked { sy.now["me"] = sy.now[m.meVoice]; sy.next["me"] = sy.next[m.meVoice] }
        reward.says = sy.now; reward.saysNext = sy.next
        // 홈 리마인드: 오늘 생긴 큰 달성은 오늘의 움직임 말풍선에, 1000칸 이정표는 블록 위젯 머리에 (그날만)
        reward.news = Remind.shared.widgetNews; reward.badge = Remind.shared.widgetBadge; reward.newsDay = Day.today
        MeArt.push(m)
        Store.write(reward, "reward.json")
        // 워치 위젯: 앱 홈의 인물과 같은 값 (워치는 이 App Group 을 못 읽어 따로 보낸다)
        let wc = WChar.of(reward.homeFriend ?? "seri")
        WatchSync.shared.send(WWatch(at: now.timeIntervalSince1970 * 1000, char: wc.id, accent: wc.accent, g0: wc.g0, g1: wc.g1,
                                     total: reward.total, dayChg: reward.dayChg, spark: reward.spark ?? [], keyName: reward.keyName,
                                     pct: reward.pct, line: wc.line(reward)))
        Store.write(summary, "summary.json")
        Store.write(feed, "feed.json")
        Store.write(live, "live.json")
        Story.pushArt(m)
        Store.write(Story.shared.widget(m), "story.json")   // 서재 위젯: 읽던 자리 한 문단
        Store.write(small(m, path, cash: cash), WSmall.file)   // 작은 위젯 내 길·목표 페이스
        Store.lastCheck = now
        Store.summarySource = "app"
        Store.widgetStep = m.playOn ? 3 : max(1, m.nxStep)   // 위젯은 미션 4에서 실제로 한 단계에 맞춰 하나씩 열린다 (개발자 계정도 같게)
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// 작은 위젯 값: 내 길(실제는 40점 안쪽으로 줄인다, 계획선 41점, 앞섬)과 평행 세계 나·모의 바구니 수익률
    @MainActor static func small(_ m: AppModel, _ p: AppModel.MyPath, cash: Double) -> WSmall {
        let e = max(p.end, 1e-6), step = max(1, p.pts.count / 40)
        var pts = stride(from: 0, to: p.pts.count, by: step).map { [p.pts[$0].0 / e, p.pts[$0].1] }
        if let l = p.pts.last, pts.last?.first != l.0 / e { pts.append([l.0 / e, l.1]) }
        var me: Double?, bk: Double?
        if let race = m.parallelRace, race.last >= 0 {
            let r = m.parallelSeries(race)
            if r.count > 1, race.last < r[0].count, race.last < r[1].count { me = r[0][race.last]; bk = r[1][race.last] }
        }
        return WSmall(path: pts, need: (0...40).map { p.need(e * Double($0) / 40) }, gap: m.myPathGapToday, t0: m.total + cash, me: me, basket: bk)
    }

    /// 인물별 위젯 말풍선: 사흘에 한 번은 돌아온 물건 얘기, 나머지 날은 오늘 가장 크게 움직인 종목 (대사 데이터에서)
    /// 위젯은 갱신하거나 ↻를 누를 때마다 다음 줄을 쓴다 (saysNext). 앱이 다시 쓸 때는 위젯에서 본 만큼 넘기고 그 다음부터 준다
    static func says(_ r: WReward) -> (now: [String: String], next: [String: [String]]) {
        let top = r.tiles.max { abs($0.c) < abs($1.c) }
        let t = top?.t ?? WL.t(String(localized: "오늘")), c = top?.c ?? r.dayChg
        let seen = WidgetLine.index()
        var now: [String: String] = [:], next: [String: [String]] = [:]
        for w in WChar.all {
            var key = "widget.\(c >= 0 ? "up" : "down").\(w.id)"
            var vars = ["t": t, "c": String(format: "%.1f", abs(c) * 100)]
            var fallback: String? = nil
            if !WL.en, let it = WItem.today(r) { key = "item.\(it).\(w.id)"; vars = [:]; fallback = WItem.say(it, w.id) }   // 물건 대사는 아직 한국어만
            Lines.skip(key, seen)
            let list = Lines.peek(key, vars, 10)
            next[w.id] = list
            now[w.id] = list.first ?? fallback
        }
        WidgetLine.reset()
        return (now, next)
    }
}
