import Foundation
import WidgetKit

// 앱이 계산한 숫자를 위젯에 넘긴다. 위젯(기존 ios/ 위젯 앱에서 옮김)은 App Group 폴더의
// summary.json(요약) · feed.json(지금 시세) · live.json(보유·목표)을 읽어 그린다.
// 예전 위젯 앱은 사이트 동기화 비밀번호로 이 파일들을 받았지만, 한 앱으로 합치면서 앱이 직접 쓴다.
enum WidgetBridge {
    @MainActor
    static func write(_ m: AppModel) {
        let fx = Market.shared.fx
        let rows = m.rows
        let now = Date()
        let goal = m.keyValue
        let start = Calendar.current.date(byAdding: .month, value: -3, to: now)!    // 시작은 3개월 전 (내 길과 같은 가정)
        let end = m.isGoal ? Calendar.current.date(byAdding: .year, value: m.gY, to: now)! : Calendar.current.date(byAdding: .month, value: 9, to: now)!

        // 지금 시세: [지금 가격, 전일 종가, 시각]
        var q: [String: [Double?]] = [:], ccy: [String: String] = [:]
        for r in rows {
            let qt = r.sym.quote
            q[r.id] = [qt.last, qt.prevClose, now.timeIntervalSince1970]
            ccy[r.id] = r.sym.currency == .usd ? "USD" : "KRW"
        }
        q["KRW=X"] = [fx.last, fx.prevClose, now.timeIntervalSince1970]
        let feed = WFeed(v: 1, u: ISO8601DateFormatter().string(from: now), q: q, c: [:], ccy: ccy, mar: nil)

        let live = WLive(hold: rows.map { .init(t: $0.id, sh: $0.h.qty, price: $0.sym.last) },
                         cashKrw: 0, cashUsd: 0, goalAmount: goal, goalDate: Day.str(end), goalStart: Day.str(start),
                         manualPrice: true, stateAt: now.timeIntervalSince1970 * 1000)

        // 요약: 최근 3달 평가액(만원), 3년 전망 부채꼴, 이번 주 예보
        let spark: [Double?] = (0..<63).map { i in m.totalAt(1 - Double(62 - i) / 756) / 1e4 }
        let f = m.forecast
        let md = (0...36).map { Day.str(Calendar.current.date(byAdding: .month, value: $0, to: now)!) }
        let man = { (a: [Double]) in a.map { Optional($0 / 1e4) } }
        let fc = WSummary.Forecast(scen: m.lens.label, md: md, p5: man(f.q5), p25: man(f.q25), p50: man(f.q50), p75: man(f.q75), p95: man(f.q95),
                                   pg: f.prob(3), t: .init(p5: f.q5[36] / 1e4, p50: f.q50[36] / 1e4, p95: f.q95[36] / 1e4))
        let wr = m.weekRange
        let friday = Calendar.current.nextDate(after: now, matching: DateComponents(weekday: 6), matchingPolicy: .nextTime) ?? now
        let week = WSummary.Week(f: Day.str(friday), p50: (wr.lo + wr.hi) / 2, lo: wr.lo, hi: wr.hi, base: m.total, cash: 0, t0: m.total, tent: nil)
        let hits = m.pastWeeks.enumerated().map { WSummary.Hit(f: Day.str(now.addingTimeInterval(-Double(8 - $0) * 7 * 86400)), h: $1, r: nil) }
        let summary = WSummary(v: 2, at: now.timeIntervalSince1970 * 1000, asof: Day.today, today: Day.today, cash: 0,
                               goal: .init(amount: goal, date: Day.str(end), start: Day.str(start), v0: m.totalAt(1 - 0.25 / 3)),
                               hold: rows.map { .init(t: $0.id, sh: $0.h.qty, ccy: ccy[$0.id] ?? "USD", manual: $0.sym.last) },
                               spark: spark, fc: fc, hits: hits, week: week,
                               prev: m.total / (1 + m.todayMove), events: [])

        // 인물 보상 위젯: 본전(목표)까지, 1000칸, 오늘의 움직임
        let key = max(1, goal), track = m.trackValue, yTrack = track / (1 + m.todayMove)
        let frac = { (v: Double) in max(0, min(1, v / key)) }
        // 위젯은 큰 3개를 칸으로, 나머지는 '외 n개' 줄로
        let tiles = rows.sorted { $0.value > $1.value }.prefix(30).map { r in
            WReward.Tile(t: r.sym.currency == .usd ? r.id : r.sym.name, w: m.total > 0 ? r.value / m.total : 0, c: r.sym.quote.change)
        }
        let reward = WReward(keyName: m.keyName, pct: frac(track), pctYesterday: frac(yTrack), remain: max(0, key - track),
                             cells: Int(frac(track) * 1000), cellsYesterday: Int(frac(yTrack) * 1000),
                             total: m.total, dayChg: m.todayMove, tiles: Array(tiles), next: nextEvent(rows.map(\.id), now),
                             friendsOn: Shelter.friends.enumerated().filter { m.friendOn($0.offset) }.map(\.element.id),
                             homeFriend: m.homeFriendShown, spark: spark.suffix(10).compactMap { $0 })
        Store.write(reward, "reward.json")
        Store.write(summary, "summary.json")
        Store.write(feed, "feed.json")
        Store.write(live, "live.json")
        Store.lastCheck = now
        Store.unlockedKinds = WidgetUnlock.kinds(doneSteps: m.devAll ? 3 : m.nxStep)   // 앱 시작 단계로 받은 위젯
        Store.summarySource = "app"
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// 다음 일정: 보유 종목의 실적 발표 (분석 탭 일정과 같은 값)
    static let events: [(day: String, sym: String, what: String)] = [
        ("2026-10-22", "DRNK", "실적"), ("2026-10-29", "005930", "실적"), ("2026-10-30", "AAPL", "실적"), ("2026-11-19", "NVDA", "실적"),
    ]
    static func nextEvent(_ held: [String], _ now: Date) -> String? {
        let cal = Calendar.current, today = cal.startOfDay(for: now)
        for e in events where held.contains(e.sym) {
            guard let d = Day.date(e.day) else { continue }
            let n = cal.dateComponents([.day], from: today, to: d).day ?? -1
            guard n >= 0 else { continue }
            let md = cal.dateComponents([.month, .day], from: d)
            let name = Sample.symbols.first { $0.id == e.sym }.map { $0.currency == .usd ? $0.id : $0.name } ?? e.sym
            return "\(name) \(e.what) " + (n == 0 ? "오늘" : "D-\(n)") + " (\(md.month!)/\(md.day!))"
        }
        return nil
    }
}
