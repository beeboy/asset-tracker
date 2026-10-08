import Foundation

/// 위젯이 그리는 숫자. 요약(하루 1번 계산) + 시세 파일(30분마다)로 지금 값을 다시 맞춘다
struct Snapshot {
    struct Move: Identifiable { var id: String { t }; var t: String; var pct: Double; var krw: Double; var w: Double }
    struct Fan { var md: [Date]; var p5: [Double]; var p25: [Double]; var p50: [Double]; var p75: [Double]; var p95: [Double] }
    struct Week { var f: String; var p50: Double; var lo: Double; var hi: Double; var act: Double; var dday: Int }
    struct Ev: Identifiable { var id: String { d + t + k }; var d: String; var t: String; var k: String; var dday: Int }

    var total: Double
    var prevTotal: Double
    var goal: Double
    var goalDate: String
    var dday: Int
    var v0: Double?
    var start: String?
    var spark: [Double]          // 최근 3달 일별 (마지막 = 지금)
    var fan: Fan?
    var pGoal: Double?
    var term: (p5: Double, p50: Double, p95: Double)?
    var fcAsOf: String?
    var hits: [Bool]
    var week: Week?
    var moves: [Move]
    var events: [Ev]
    var updated: Date?
    var source: String?
    var placeholder = false

    var dayChg: Double { prevTotal > 0 ? total / prevTotal - 1 : 0 }
    var dayAmt: Double { total - prevTotal }
    var progress: Double { goal > 0 ? total / goal : 0 }
    var left: Double { max(0, goal - total) }
    var unit: Double { goal / 1000 }
    var cells: Int { min(1000, Int(total / unit)) }
    var cellsPrev: Int { min(1000, Int(prevTotal / unit)) }
    /// 필요 경로(시작일 평가액 → 목표일 목표 금액, 같은 연 수익률) 대비 지금
    var pace: (ahead: Double, need: Double, req: Double)? {
        guard let v0, v0 > 0, let start, let s = Day.date(start), let e = Day.date(goalDate) else { return nil }
        let span = e.timeIntervalSince(s) / (365.25 * 86400); guard span > 0 else { return nil }
        let el = max(0, Date().timeIntervalSince(s) / (365.25 * 86400))
        let need = v0 * pow(goal / v0, el / span)
        return (total / need - 1, need, pow(goal / v0, 1 / span) - 1)
    }
}

enum Day {
    static let fmt: DateFormatter = { let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "Asia/Seoul"); f.dateFormat = "yyyy-MM-dd"; return f }()
    static func date(_ s: String) -> Date? { fmt.date(from: s) }
    static func str(_ d: Date) -> String { fmt.string(from: d) }
    static var today: String { str(Date()) }
    static func dday(_ s: String) -> Int {
        guard let d = date(s), let t = date(today) else { return 0 }
        return Int((d.timeIntervalSince(t) / 86400).rounded())
    }
    static func md(_ s: String) -> String { let p = s.split(separator: "-"); return p.count == 3 ? "\(Int(p[1]) ?? 0)/\(Int(p[2]) ?? 0)" : s }
}

enum Engine {
    static func snapshot() -> Snapshot {
        let s = Store.read(WSummary.self, "summary.json")
        let f = Store.read(WFeed.self, "feed.json")
        let live = Store.read(WLive.self, "live.json")
        guard s != nil || live != nil else { return .sample }
        return build(s, f, live)
    }

    static func build(_ s: WSummary?, _ f: WFeed?, _ live: WLive?) -> Snapshot {
        // 수량·현금·목표는 최신 입력값, 없으면 요약의 것
        struct H { let t: String; let sh: Double; let manual: Double? }
        let hold: [H]
        if let l = live { hold = l.hold.map { H(t: $0.t, sh: $0.sh, manual: l.manualPrice ? $0.price : nil) } }
        else { hold = (s?.hold ?? []).map { H(t: $0.t, sh: $0.sh, manual: $0.manual) } }
        let fx: (String) -> Double = { ccy in
            if ccy == "KRW" { return 1 }
            if ccy == "USD", let m = f?.mar, let d = m.date, Day.dday(d) >= -5 { return m.rate }
            let sym = ccy == "USD" ? "KRW=X" : ccy + "KRW=X"
            if let v = f?.q[sym]?.first ?? nil { return v }
            return f?.c[sym]?.c.compactMap { $0 }.last ?? 1350
        }
        let cash = live.map { $0.cashKrw + $0.cashUsd * fx("USD") } ?? (s?.cash ?? 0)
        var moves: [Snapshot.Move] = []
        var tot = 0.0, prev = 0.0
        for h in hold {
            let ccy = f?.ccy[h.t] ?? s?.hold.first(where: { $0.t == h.t })?.ccy ?? "USD"
            let q = f?.q[h.t]
            let last = h.manual ?? (q?.first ?? nil) ?? f?.c[h.t]?.c.compactMap { $0 }.last
            let pc = (q?.count ?? 0) > 1 ? q![1] : nil
            guard let p = last else { continue }
            let v = h.sh * p * fx(ccy), pv = h.sh * (pc ?? p) * fx(ccy)
            tot += v; prev += pv
            moves.append(.init(t: h.t, pct: pc.map { p / $0 - 1 } ?? 0, krw: v, w: 0))
        }
        if tot <= 0, let s { tot = (s.spark.compactMap { $0 }.last ?? 0) * 1e4 - cash; prev = s.prev - cash } // 시세 파일이 없으면 요약 값
        let total = tot + cash, prevTotal = prev + cash
        moves = moves.map { var m = $0; m.w = tot > 0 ? m.krw / tot : 0; return m }.sorted { $0.krw > $1.krw }

        let goal = live?.goalAmount ?? s?.goal.amount ?? 1e9
        let goalDate = (live?.goalDate.isEmpty == false ? live?.goalDate : nil) ?? s?.goal.date ?? ""
        var spark = (s?.spark.compactMap { $0 }.map { $0 * 1e4 }) ?? []
        // 요약의 마지막 점은 마지막 종가. 오늘 값은 지금 값으로 붙인다
        if spark.isEmpty || (s?.asof ?? "") < Day.today { spark.append(total) } else { spark[spark.count - 1] = total }
        if spark.count > 64 { spark.removeFirst(spark.count - 64) }

        var fan: Snapshot.Fan?
        if let fc = s?.fc {
            let w = { (a: [Double?]) in a.map { ($0 ?? 0) * 1e4 } }
            fan = .init(md: fc.md.compactMap { Day.date($0) }, p5: w(fc.p5), p25: w(fc.p25), p50: w(fc.p50), p75: w(fc.p75), p95: w(fc.p95))
        }
        var week: Snapshot.Week?
        if let wk = s?.week, Day.dday(wk.f) >= 0 {
            let hNow = tot, act = wk.t0 > 0 ? wk.base * (hNow / wk.t0) + wk.cash : total
            week = .init(f: wk.f, p50: wk.p50, lo: wk.lo, hi: wk.hi, act: act, dday: Day.dday(wk.f))
        }
        let evs = (s?.events ?? []).filter { Day.dday($0.d) >= 0 }.prefix(4).map { Snapshot.Ev(d: $0.d, t: $0.t, k: $0.k, dday: Day.dday($0.d)) }
        let t = s?.fc?.t
        return Snapshot(total: total, prevTotal: prevTotal, goal: goal, goalDate: goalDate, dday: Day.dday(goalDate),
                        v0: s?.goal.v0, start: live?.goalStart ?? s?.goal.start, spark: spark, fan: fan, pGoal: s?.fc?.pg,
                        term: termOf(t),
                        fcAsOf: s?.asof, hits: (s?.hits ?? []).map { $0.h == 1 }, week: week, moves: moves, events: Array(evs),
                        updated: Store.lastCheck, source: Store.summarySource)
    }
}

private func termOf(_ t: WSummary.Term?) -> (p5: Double, p50: Double, p95: Double)? {
    guard let t, let m = t.p50 else { return nil }
    return (p5: (t.p5 ?? 0) * 1e4, p50: m * 1e4, p95: (t.p95 ?? 0) * 1e4)
}

extension Snapshot {
    /// 로그인 전·위젯 갤러리 미리보기용
    static var sample: Snapshot {
        let spark = (0..<63).map { i in 5.3e8 + sin(Double(i) / 6) * 1.5e7 - Double(i) * 1e5 }
        let md = (0...36).compactMap { Calendar.current.date(byAdding: .month, value: $0, to: Date()) }
        let p50 = md.indices.map { 5.27e8 * pow(1.12, Double($0) / 12) }
        let sp = { (z: Double) in md.indices.map { p50[$0] * exp(z * 0.45 * sqrt(Double($0) / 12)) } }
        return Snapshot(total: 5.27e8, prevTotal: 5.32e8, goal: 1e9, goalDate: Day.str(md.last!), dday: 1092, v0: 5.30e8, start: Day.today,
                        spark: spark, fan: .init(md: md, p5: sp(-1.645), p25: sp(-0.674), p50: p50, p75: sp(0.674), p95: sp(1.645)),
                        pGoal: 0.38, term: (1.7e8, 7.7e8, 2.2e9), fcAsOf: Day.today, hits: [true, false, true, true, false, true, true, true],
                        week: .init(f: Day.today, p50: 5.17e8, lo: 4.96e8, hi: 5.39e8, act: 5.27e8, dday: 1),
                        moves: [.init(t: "TSLA", pct: -0.007, krw: 3.48e8, w: 0.66), .init(t: "NVDA", pct: -0.006, krw: 0.95e8, w: 0.18),
                                .init(t: "SPCX", pct: -0.02, krw: 0.71e8, w: 0.14), .init(t: "SGOV", pct: 0, krw: 0.12e8, w: 0.02)],
                        events: [.init(d: Day.today, t: "TSLA", k: "실적", dday: 13)], updated: Date(), source: nil, placeholder: true)
    }
}
