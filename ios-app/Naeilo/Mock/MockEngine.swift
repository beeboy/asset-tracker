#if MOCK
import Foundation

// 모의투자 판단·체결 계산. 화면·네트워크 없이 값만 다룬다 (판단 코드와 주문 코드를 나눈다).
// 상태를 바꾸는 곳은 MockDesk 하나뿐이고, 여기 함수는 장부 사본을 받아 새 장부와 기록 줄을 돌려준다.

enum MockCalc {

    // MARK: 조건 조각 값

    /// 조각의 값 (화면 단위: 가격 원 · 등락률 % · 거래량 배 · 이동평균 위아래 비율 · 거래대금 억). 봉이 모자라면 nil
    static func value(_ p: MockPiece, _ bars: [MockBar]) -> Double? {
        guard let last = bars.last, bars.count >= p.need else { return nil }
        switch p.kind {
        case .price: return last.c
        case .change:
            let prev = bars[bars.count - 2].c
            return prev > 0 ? (last.c / prev - 1) * 100 : nil
        case .volume:
            let past = bars.dropLast().suffix(p.days)
            let avg = past.reduce(0) { $0 + $1.v } / Double(max(1, past.count))
            return avg > 0 ? last.v / avg : nil
        case .ma:
            let win = bars.suffix(p.days)
            let avg = win.reduce(0) { $0 + $1.c } / Double(max(1, win.count))
            return avg > 0 ? last.c / avg - 1 : nil
        case .amount: return last.c * last.v / 1e8
        case .rise:
            let base = bars[bars.count - 1 - p.days].c
            return base > 0 ? (last.c / base - 1) * 100 : nil
        }
    }

    static func pass(_ p: MockPiece, _ v: Double) -> Bool {
        if p.kind == .ma { return p.above ? v > 0 : v < 0 }
        if let lo = p.lo, v < lo { return false }
        if let hi = p.hi, v > hi { return false }
        return true
    }

    /// 걸린 이유 조각 글: "거래량 3.1배"
    static func short(_ p: MockPiece, _ v: Double) -> String {
        switch p.kind {
        case .price: return MockFmt.won(v)
        case .change: return MockFmt.signed((v * 10).rounded() / 10) + "%"
        case .volume: return "거래량 " + String(format: "%.1f", v) + "배"
        case .ma: return "\(p.days)일선 " + (v > 0 ? "위" : "아래")
        case .amount: return "거래대금 " + MockFmt.num((v * 10).rounded() / 10) + "억"
        case .rise: return "\(p.days)일 " + MockFmt.signed((v * 10).rounded() / 10) + "%"
        }
    }

    // MARK: 훑기

    /// 묶음으로 종목들을 거른다. bars: 코드 → 오래된 것부터. 기준 날짜(가장 늦은 봉)에 봉이 없는 종목은 자료 없음
    /// frac: 이 시각 하루 거래량 누적 비율. 오늘 봉이 장중일 때만 거래량·거래대금을 frac 으로 나눈 새 배수로도 따로 걸러 newHits 에 남긴다
    static func scan(_ b: MockBundle, stocks: [MockStock], bars: [String: [MockBar]], excluded: Set<String>, at: Date, seconds: Double = 0, frac: Double? = nil) -> MockScan {
        let barDay = bars.values.compactMap { $0.last?.d }.max() ?? ""
        let f = frac.flatMap { $0 > 0 && $0 < 1 && barDay == MockFmt.day(at) ? $0 : nil }
        var hits: [MockHit] = [], dropped: [Int: Int] = [:], ex: [String] = [], newHits: [String] = []
        let list = stocks.filter { b.scope == .all || (b.scope == .kospi) == $0.kospi }
        for s in list {
            if excluded.contains(s.code) { ex.append(s.code); continue }
            guard let bs = bars[s.code], let last = bs.last, last.d == barDay, bs.count >= b.need else { dropped[-1, default: 0] += 1; continue }
            var why: [String] = [], fail: Int? = nil, failNew = f == nil
            for (i, p) in b.pieces.enumerated() {
                guard let v = value(p, bs) else { fail = fail ?? -1; failNew = true; break }
                if let f, !pass(p, scaled(p, v, f)) { failNew = true }
                if fail == nil { if pass(p, v) { why.append(short(p, v)) } else { fail = i } }
                if fail != nil && failNew { break }
            }
            if !failNew { newHits.append(s.code) }
            if let f = fail { dropped[f, default: 0] += 1; continue }
            let score = value(b.sortPiece, bs) ?? 0
            let ch = value(MockPiece(kind: .change), bs).map { $0 / 100 }
            hits.append(MockHit(code: s.code, name: s.name, price: last.c, change: ch, score: score,
                                why: why.isEmpty ? "조각 없음 · 전부" : MockFmt.uniq(why.joined(separator: " · ")),
                                vol: value(MockPiece(kind: .volume, days: 20), bs), rise5: value(MockPiece(kind: .rise, days: 5), bs),
                                volNew: f.flatMap { f in value(MockPiece(kind: .volume, days: 20), bs).map { $0 / f } }))
        }
        hits.sort { $0.score != $1.score ? $0.score > $1.score : $0.code < $1.code }
        return MockScan(at: at, bundleId: b.id, bundleName: b.name, pieceTexts: b.pieces.map(\.text), barDay: barDay,
                        total: list.count, hits: hits, dropped: dropped, excluded: ex, seconds: seconds,
                        frac: f, newHits: f == nil ? nil : newHits)
    }

    /// 새 배수: 거래량·거래대금은 아직 쌓이는 중이라 이 시각 누적 비율로 나눈다. 가격 조각은 그대로
    static func scaled(_ p: MockPiece, _ v: Double, _ f: Double) -> Double {
        p.kind == .volume || p.kind == .amount ? v / f : v
    }

    // MARK: 하루 거래량 곡선

    /// 지난 20일 하루 평균 거래대금이 큰 종목 n개 (오늘 봉은 뺀다)
    static func big(_ bars: [String: [MockBar]], n: Int) -> [String] {
        let amt: [(String, Double)] = bars.map { (c, bs) in
            (c, bs.dropLast().suffix(20).reduce(0.0) { (s: Double, x: MockBar) in s + x.c * x.v })
        }
        return amt.sorted { $0.1 != $1.1 ? $0.1 > $1.1 : $0.0 < $1.0 }.prefix(n).map(\.0)
    }

    /// 종목들의 하루 1분봉 ("09:01") → 15분마다 누적 비율 27칸 (종목 사이 가운데 값). 쓸 종목이 5개 안 되면 nil
    static func curve(_ days: [[MockBar]]) -> [Double]? {
        var cols = Array(repeating: [Double](), count: 27)
        for bs in days {
            let tot = bs.reduce(0) { $0 + $1.v }
            guard tot > 0 else { continue }
            for k in 0...26 {
                let t = k == 26 ? Int.max : 540 + 15 * k
                cols[k].append(bs.filter { hm($0.d) <= t }.reduce(0) { $0 + $1.v } / tot)
            }
        }
        guard cols[0].count >= 5 else { return nil }
        return cols.map(median)
    }

    /// 최근 곡선들(27칸)의 칸별 가운데 값. 곡선이 없으면 nil
    static func curveNow(_ curves: [MockCurve]) -> [Double]? {
        let cs = curves.filter { $0.cum.count == 27 }
        guard !cs.isEmpty else { return nil }
        return (0...26).map { k in median(cs.map { $0.cum[k] }) }
    }

    /// 이 시각 하루 거래량 누적 비율 (최근 곡선들의 칸별 가운데 값, 15분 사이는 곧게 잇는다). 장 전·곡선 없음은 nil, 장 끝 뒤는 1
    static func frac(_ curves: [MockCurve], at: Date) -> Double? {
        guard let mid = curveNow(curves) else { return nil }
        var cal = Calendar(identifier: .gregorian); cal.timeZone = MockFmt.kst
        let c = cal.dateComponents([.hour, .minute], from: at)
        let m = (c.hour ?? 0) * 60 + (c.minute ?? 0) - 540
        guard m > 0 else { return nil }
        if m >= 390 { return 1 }
        let k = m / 15, r = Double(m % 15) / 15
        let a = mid[k], b = mid[k + 1]
        let v = a + (b - a) * r
        return v > 0 ? v : nil
    }

    static func hm(_ s: String) -> Int {
        let p = s.split(separator: ":").compactMap { Int($0) }
        return p.count == 2 ? p[0] * 60 + p[1] : 0
    }

    static func median(_ xs: [Double]) -> Double {
        let s = xs.sorted(), n = s.count
        guard n > 0 else { return 0 }
        return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2
    }

    /// 예외 묶음에 걸리는 종목 (조건 추가). 조각이 없는 묶음은 아무것도 빼지 않는다
    static func exceptionHits(_ bs: [MockBundle], stocks: [MockStock], bars: [String: [MockBar]]) -> [String: String] {
        var out: [String: String] = [:]
        for b in bs where !b.pieces.isEmpty {
            for h in scan(b, stocks: stocks, bars: bars, excluded: [], at: Date()).hits where out[h.code] == nil { out[h.code] = "조건 · " + b.name }
        }
        return out
    }

    // MARK: 평가

    /// 평가액 = 현금 + 묶인 돈 + 결제 대기 + 보유 × 지금 가격 (가격을 모르면 산 평균값)
    static func equity(_ l: MockLedger, prices: [String: Double]) -> Double {
        l.cash + l.reserved + l.settling + l.positions.values.reduce(0) { $0 + Double($1.qty) * (prices[$1.code] ?? $1.avg) }
    }

    // MARK: 선정

    struct Pick: Codable, Equatable, Identifiable {
        var id: String { code }
        var code: String
        var name: String
        var price: Double            // 고를 때 본 가격
        var budget: Double           // 쓸 돈
        var qty: Int                 // 그 가격이면 몇 주
        var rank: Int                // 관찰 결과 순위 (1부터)
        var why: String
        var weight: Double           // 평가액 대비
        var who: MockWho = .rule
        var short: Bool? = nil       // 고를 때 현금이 모자랐음 (주문할 때 현금으로 줄이거나 거부)
    }
    struct Skip: Codable, Equatable {
        var code: String
        var name: String
        var why: String
        var short: Bool = false      // 현금 부족 (못 산 신호로 센다)
    }

    /// 관찰 결과에서 고른다. 순위 차례대로 (가나다순 같은 우연한 순서가 아니다), 남은 현금에서 하나씩 뺀다. 현금이 모자라도 고르고 현금 부족으로 표시한다
    static func select(_ scan: MockScan, ledger l: MockLedger, rule: MockSelectRule, fees: MockFees,
                       prices: [String: Double], excluded: [String: String], soldToday: Set<String> = []) -> (picks: [Pick], skips: [Skip]) {
        let eq = equity(l, prices: prices)
        let pendingBuy = Set(l.pending.filter { $0.side == .buy }.map(\.code))
        // 그날 훑은 종목에서 새로 rule.count 종목을 고른다 (어제 산 보유가 자리를 막지 않는다. 보유·대기 종목은 건너뛰고 다음 순위로)
        var slots = rule.count
        var avail = l.cash - rule.keepCash * eq
        var picks: [Pick] = [], skips: [Skip] = []
        for (i, h) in scan.hits.enumerated() {
            if let why = excluded[h.code] { skips.append(Skip(code: h.code, name: h.name, why: "예외 · " + why)); continue }
            if l.positions[h.code] != nil { skips.append(Skip(code: h.code, name: h.name, why: "이미 보유")); continue }
            if pendingBuy.contains(h.code) { skips.append(Skip(code: h.code, name: h.name, why: "같은 종목 주문 대기 중")); continue }
            // 판 날은 다시 사지 않는다 (같은 날 사고팔기 되풀이로 수수료·세금만 내는 일 막음)
            if soldToday.contains(h.code) { skips.append(Skip(code: h.code, name: h.name, why: "오늘 판 종목 · 내일부터 다시 고름")); continue }
            guard slots > 0 else { break }
            let px = prices[h.code] ?? h.price
            let one = px * (1 + fees.commission)
            guard px > 0 else { skips.append(Skip(code: h.code, name: h.name, why: "가격 확인 못 함")); continue }
            // 고르기는 현금을 보지 않는다. 현금은 주문할 때 본다 (모자라면 그때 줄이거나 거부)
            let share = avail / Double(slots)
            let short = share < one
            let budget = short ? rule.maxPer * eq : min(rule.maxPer * eq, share)
            let qty = Int((budget / one).rounded(.down))
            if qty < 1 {
                // 한 주가 쓸 돈보다 비싸면 0주. 오류가 아니라 정상 결과로 남긴다
                skips.append(Skip(code: h.code, name: h.name, why: "0주 · 한 주 " + MockFmt.won(px) + " > 쓸 돈 " + MockFmt.won(budget)))
                continue
            }
            let spend = Double(qty) * one
            picks.append(Pick(code: h.code, name: h.name, price: px, budget: spend, qty: qty, rank: i + 1,
                              why: "\(i + 1)위 · " + h.why + (short ? " · 현금 부족" : ""), weight: eq > 0 ? spend / eq : 0,
                              short: short ? true : nil))
            avail = max(0, avail - spend)
            slots -= 1
        }
        return (picks, skips)
    }

    // MARK: 호가와 체결

    struct Level: Codable, Equatable { var p: Double; var v: Double }
    struct Book: Codable, Equatable {
        var asks: [Level]            // 매도 호가 (싼 것부터)
        var bids: [Level]            // 매수 호가 (비싼 것부터)
        var at: Date?
        init(asks: [Level], bids: [Level], at: Date?) {
            self.asks = asks.filter { $0.p > 0 && $0.v > 0 }.sorted { $0.p < $1.p }
            self.bids = bids.filter { $0.p > 0 && $0.v > 0 }.sorted { $0.p > $1.p }
            self.at = at
        }
        var bestAsk: Double? { asks.first?.p }
        var bestBid: Double? { bids.first?.p }
    }

    struct Fill: Equatable {
        var qty: Int = 0
        var amount: Double = 0       // 체결 금액 (수수료 전)
        var levels: Int = 0          // 몇 단계 호가를 채웠나
        var taken: [Level] = []      // 단계마다 채운 수량 (모의 브로커가 그만큼 호가 잔량을 줄인다)
        var avg: Double { qty > 0 ? amount / Double(qty) : 0 }
    }

    /// 호가를 차례로 채운다. 매수는 매도 1호가부터, 매도는 매수 1호가부터. 지정가는 그 값을 넘는 호가는 쓰지 않는다.
    /// cap: 매수에 쓸 수 있는 돈 (수수료 포함). 모자라면 그만큼만
    static func walk(side: MockSide, type: MockOrderType, qty: Int, limit: Double?, book: Book, fees: MockFees, cap: Double? = nil) -> Fill {
        var f = Fill(), left = qty
        let levels = side == .buy ? book.asks : book.bids
        for lv in levels where left > 0 {
            if type == .limit, let lim = limit {
                if side == .buy && lv.p > lim { break }
                if side == .sell && lv.p < lim { break }
            }
            var take = min(left, Int(lv.v.rounded(.down)))
            if side == .buy, let cap {
                let room = cap - (f.amount + fees.fee(f.amount))
                take = min(take, Int((room / (lv.p * (1 + fees.commission))).rounded(.down)))
            }
            guard take > 0 else { break }
            f.qty += take; f.amount += Double(take) * lv.p; f.levels += 1; left -= take
            f.taken.append(Level(p: lv.p, v: Double(take)))
        }
        return f
    }

    /// 지정가가 바로 닿는지 못 닿는지 한 줄 (미체결 이유)
    static func waitWhy(side: MockSide, limit: Double, book: Book) -> String {
        if side == .buy {
            guard let a = book.bestAsk else { return "매도 호가 없음" }
            return "매도 1호가 " + MockFmt.won(a) + "이 더 높음"
        }
        guard let b = book.bestBid else { return "매수 호가 없음" }
        return "매수 1호가 " + MockFmt.won(b) + "이 더 낮음"
    }

    /// 매수 주문에 묶을 돈: 지정가면 지정가 × 수량, 시장가면 매도 1호가 × 수량에 2% 여유 (깊은 호가까지 채울 때). 수수료 포함
    static func reserve(type: MockOrderType, qty: Int, limit: Double?, bestAsk: Double, fees: MockFees, cash: Double) -> Double {
        let px = type == .limit ? (limit ?? bestAsk) : bestAsk * 1.02
        return min(cash, (Double(qty) * px * (1 + fees.commission)).rounded(.up))
    }

    // MARK: 실전 판단 (주문 직전 확인 · 정정 · 유의사항 · 보유)

    /// 주문 순간 예상 평균가: 지금 호가로 끝까지 채운 값 (지정가는 그 값까지만). 한 주도 못 채우면 nil
    static func expect(side: MockSide, type: MockOrderType, qty: Int, limit: Double?, book: Book, fees: MockFees) -> Double? {
        let f = walk(side: side, type: type, qty: qty, limit: limit, book: book, fees: fees)
        return f.qty > 0 ? f.avg : nil
    }

    /// 예상가 대비 실제 체결가 차이 (불리하면 +). 매수: 실제/예상 − 1, 매도: 1 − 실제/예상
    static func slipGap(side: MockSide, expect: Double, actual: Double) -> Double {
        guard expect > 0 else { return 0 }
        return side == .buy ? actual / expect - 1 : 1 - actual / expect
    }

    /// 주문 직전 확인: 지금 호가로 다 채우면 나갈 돈·수수료·세금·1호가 대비 미끄러짐. 못 채운 수량은 남는다
    struct Preview: Equatable {
        var qty = 0                  // 지금 채워지는 수량
        var avg: Double = 0          // 예상 평균가
        var amount: Double = 0       // 체결 금액
        var fee: Double = 0
        var tax: Double = 0
        var slip: Double = 0         // 1호가 대비 (불리하면 +)
        var total: Double { amount + fee + tax }
    }
    static func preview(side: MockSide, type: MockOrderType, qty: Int, limit: Double?, book: Book, fees: MockFees) -> Preview {
        let f = walk(side: side, type: type, qty: qty, limit: limit, book: book, fees: fees)
        guard f.qty > 0 else { return Preview() }
        let best = (side == .buy ? book.bestAsk : book.bestBid) ?? f.avg
        return Preview(qty: f.qty, avg: f.avg, amount: f.amount, fee: fees.fee(f.amount), tax: side == .sell ? fees.tax(f.amount) : 0,
                       slip: slipGap(side: side, expect: best, actual: f.avg))
    }

    /// 대기 주문 정정: 남은 수량·지정가를 바꾼다 (정정하면 지정가). 매수는 묶은 돈을 새로 셈하고 모자라면 이유를 돌려준다
    static func modify(_ l: inout MockLedger, orderIndex i: Int, qty: Int, limit: Double, fees: MockFees) -> String? {
        guard l.pending.indices.contains(i), qty >= 1, limit > 0 else { return "수량·가격을 확인해 주세요" }
        var o = l.pending[i]
        let lim = snap(limit, side: o.side)
        if o.side == .buy {
            let need = (Double(qty) * lim * (1 + fees.commission)).rounded(.up)
            if need - o.reserved > l.cash { return "현금 부족 · 더 필요 " + MockFmt.won(need - o.reserved) }
            l.cash -= need - o.reserved
            o.reserved = need
        } else {
            let other = l.pending.enumerated().filter { $0.offset != i && $0.element.code == o.code && $0.element.side == .sell }.reduce(0) { $0 + $1.element.qty }
            if qty + other > (l.positions[o.code]?.qty ?? 0) { return "보유보다 많이 팔 수 없어요" }
        }
        o.qty = qty; o.type = .limit; o.limit = lim; o.until = nil; o.waitWhy = nil
        l.pending[i] = o
        return nil
    }

    /// 유의사항 종류 → 짧은 이름 (모르는 값은 그대로)
    static func warnName(_ t: String) -> String {
        switch t {
        case "SUSPENDED": return "거래정지"
        case "LIQUIDATION_TRADING": return "정리매매"
        case "INVESTMENT_RISK": return "투자위험"
        case "INVESTMENT_WARNING": return "투자경고"
        case "OVERHEATED": return "단기과열"
        case "VI_STATIC", "VI_DYNAMIC", "VI_STATIC_AND_DYNAMIC": return "VI"
        default: return t
        }
    }

    /// 고르기에서 뺄 유의사항 (거래정지·정리매매·투자위험). 빼면 이유, 아니면 nil
    static func warnBlock(_ ts: [String]) -> String? {
        let hard = ts.filter { ["SUSPENDED", "LIQUIDATION_TRADING", "INVESTMENT_RISK"].contains($0) }
        return hard.isEmpty ? nil : "유의 거름 · " + hard.map(warnName).joined(separator: "·")
    }

    /// 지금 가격에 다 팔면 남는 손익 (수수료·세금 뒤)
    static func afterTax(_ p: MockPosition, price: Double, fees: MockFees) -> Double {
        let amt = Double(p.qty) * price
        return amt - fees.fee(amt) - fees.tax(amt) - p.cost
    }

    /// 손절선까지 남은 비율: 지금 가격이 손절 가격(평균가 × (1 − stop))보다 몇 % 위인가. 음수면 이미 넘음
    static func stopRoom(_ p: MockPosition, price: Double, stop: Double) -> Double {
        let line = p.avg * (1 - stop)
        return line > 0 ? price / line - 1 : 0
    }

    // MARK: 장부에 적기

    /// 매수 체결: 묶은 돈에서 쓰고, 다 채우면 남은 돈을 현금으로 돌린다
    static func applyBuy(_ l: inout MockLedger, orderIndex i: Int, fill: Fill, fees: MockFees, at: Date, test: Bool) -> MockEvent {
        var o = l.pending[i]
        let fee = fees.fee(fill.amount)
        o.reserved -= fill.amount + fee
        o.qty -= fill.qty
        let day = MockFmt.day(at)
        if var p = l.positions[o.code] {
            if test { p.tQty = (p.tQty ?? 0) + fill.qty; p.tCost = (p.tCost ?? 0) + fill.amount + fee; p.tGross = (p.tGross ?? 0) + fill.amount }
            p.qty += fill.qty; p.cost += fill.amount + fee; p.gross += fill.amount; p.test = p.test || test
            l.positions[o.code] = p
        } else {
            l.positions[o.code] = MockPosition(code: o.code, name: o.name, market: o.market, qty: fill.qty, cost: fill.amount + fee, gross: fill.amount,
                                               opened: at, openedDay: day, who: o.who, test: test, buyRef: o.id,
                                               tQty: test ? fill.qty : nil, tCost: test ? fill.amount + fee : nil, tGross: test ? fill.amount : nil)
        }
        l.fees += fee
        if o.qty <= 0 {
            l.cash += max(0, o.reserved)
            l.pending.remove(at: i)
        } else {
            l.pending[i] = o
        }
        return MockEvent(at: at, ledger: l.id, book: l.book, kind: .fill, who: o.who, code: o.code, name: o.name,
                         text: "\(o.type.name) \(fill.qty)주 · 평균 \(MockFmt.won(fill.avg))" + (fill.levels > 1 ? " · 호가 \(fill.levels)단 채움" : "") + " · 수수료 \(MockFmt.won(fee))",
                         test: test, side: .buy, qty: fill.qty, price: fill.avg, amount: fill.amount, fee: fee, ref: o.id)
    }

    /// 매도 체결: 판 몫의 들어간 돈을 비율로 덜고 손익을 남긴다. 성적은 그 몫을 산 쪽(규칙·손·AI)으로 센다
    static func applySell(_ l: inout MockLedger, orderIndex i: Int, fill: Fill, fees: MockFees, at: Date, test: Bool) -> MockEvent? {
        var o = l.pending[i]
        guard var p = l.positions[o.code], p.qty > 0 else { l.pending.remove(at: i); return nil }
        let n = min(fill.qty, p.qty)
        let amount = fill.avg * Double(n)
        let fee = fees.fee(amount), tax = fees.tax(amount)
        let share = Double(n) / Double(p.qty)
        let basis = p.cost * share, gross = p.gross * share
        let proceeds = amount - fee - tax
        p.qty -= n; p.cost -= basis; p.gross -= gross
        if let tq = p.tQty { p.tQty = tq - Int((Double(tq) * share).rounded()); p.tCost = p.tCost.map { $0 * (1 - share) }; p.tGross = p.tGross.map { $0 * (1 - share) } }
        let opener = p.who, wasTest = p.test
        if p.qty <= 0 { l.positions[o.code] = nil } else { l.positions[o.code] = p }
        // 결제 T+2: 판 돈은 2영업일 뒤 현금이 된다 (장 밖 시험은 바로)
        if test { l.cash += proceeds }
        else { l.unsettled = (l.unsettled ?? []) + [MockSettle(amount: proceeds, day: settleDay(at), code: o.code)] }
        l.fees += fee; l.taxes += tax
        o.qty -= n
        if o.qty <= 0 || l.positions[o.code] == nil { l.pending.remove(at: i) } else { l.pending[i] = o }
        return MockEvent(at: at, ledger: l.id, book: l.book, kind: .fill, who: o.who, code: o.code, name: o.name,
                         text: "\(o.type.name) \(n)주 · 평균 \(MockFmt.won(fill.avg)) · 수수료 \(MockFmt.won(fee)) · 세금 \(MockFmt.won(tax))",
                         test: test || wasTest, side: .sell, qty: n, price: fill.avg, amount: amount, fee: fee, tax: tax,
                         pnl: proceeds - basis, pnlGross: amount - gross, basis: basis, opener: opener, ref: o.id)
    }

    // MARK: 팔기 신호

    /// 보유 종목마다 팔기 규칙을 지금 가격으로 본다. 예외 종목은 규칙이 팔지 않는다
    static func sellSignals(_ l: MockLedger, rule: MockSellRule, prices: [String: Double], excluded: [String: String], today: String) -> [(code: String, why: String)] {
        guard rule.on else { return [] }
        let selling = Set(l.pending.filter { $0.side == .sell }.map(\.code))
        var out: [(String, String)] = []
        for p in l.positions.values.sorted(by: { $0.code < $1.code }) where !selling.contains(p.code) && excluded[p.code] == nil && p.who != .hand {
            guard let px = prices[p.code], px > 0, p.avg > 0 else { continue }
            let r = px / p.avg - 1
            if let s = rule.stopLoss, r <= -s { out.append((p.code, "손절 · 산 값 대비 " + MockFmt.pct(r, digits: 1))); continue }
            if let t = rule.takeProfit, r >= t { out.append((p.code, "익절 · 산 값 대비 " + MockFmt.pct(r, digits: 1))); continue }
            if let d = rule.maxDays, weekdays(from: p.openedDay, to: today) >= d { out.append((p.code, "보유 \(d)일 넘음")); continue }
        }
        return out
    }

    // MARK: 결제 (T+2)

    /// 판 날부터 2영업일 뒤 (주말만 뺀다. 공휴일은 모른다)
    static func settleDay(_ at: Date) -> String {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = MockFmt.kst
        var d = at, n = 0
        while n < 2 {
            d = cal.date(byAdding: .day, value: 1, to: d)!
            let w = cal.component(.weekday, from: d)
            if w != 1 && w != 7 { n += 1 }
        }
        return MockFmt.day(d)
    }

    /// 결제일이 된 돈을 현금으로 옮긴다. 옮긴 건들을 돌려준다
    static func settle(_ l: inout MockLedger, today: String) -> [MockSettle] {
        let due = (l.unsettled ?? []).filter { $0.day <= today }
        guard !due.isEmpty else { return [] }
        l.cash += due.reduce(0) { $0 + $1.amount }
        let rest = (l.unsettled ?? []).filter { $0.day > today }
        l.unsettled = rest.isEmpty ? nil : rest
        return due
    }

    // MARK: 잔고 맞추기

    /// 체결 기록을 처음부터 다시 더해 장부와 견준다. 현금(+묶인 돈+결제 대기)과 종목별 수량.
    /// 어긋난 곳을 한 줄로 (맞으면 nil). 시험 몫 뺌은 돌린 돈(amount, 옛 줄은 글의 '돌린 돈')을 더한다
    static func reconcile(_ l: MockLedger, events: [MockEvent]) -> String? {
        var cash = l.startCash, qty: [String: Int] = [:], n = 0
        for e in events where e.ledger == l.id {
            if e.kind == .fill, let q = e.qty, let a = e.amount, let code = e.code {
                n += 1
                if e.side == .buy { cash -= a + (e.fee ?? 0); qty[code, default: 0] += q }
                else if e.side == .sell { cash += a - (e.fee ?? 0) - (e.tax ?? 0); qty[code, default: 0] -= q }
            } else if e.kind == .cancel, e.side == .buy, e.text.hasPrefix("시험 몫 뺌"), let q = e.qty, let code = e.code {
                n += 1
                qty[code, default: 0] -= q
                cash += e.amount ?? refund(e.text)
            }
        }
        var out: [String] = []
        let have = l.cash + l.reserved + l.settling
        // 원 아래 버림이 체결마다 쌓일 수 있어 체결 수만큼 원을 봐준다
        if abs(have - cash) > Double(max(1, n)) { out.append("현금 장부 " + MockFmt.won(have) + " · 기록 " + MockFmt.won(cash)) }
        for code in Set(qty.keys).union(l.positions.keys).sorted() {
            let a = l.positions[code]?.qty ?? 0, b = qty[code] ?? 0
            if a != b { out.append((l.positions[code]?.name ?? code) + " 장부 \(a)주 · 기록 \(b)주") }
        }
        return out.isEmpty ? nil : out.joined(separator: " · ")
    }
    /// 옛 '시험 몫 뺌' 줄의 글에서 돌린 돈을 읽는다 ("… · 돌린 돈 12,345원")
    private static func refund(_ t: String) -> Double {
        guard let r = t.range(of: "돌린 돈 ") else { return 0 }
        return Double(t[r.upperBound...].filter(\.isNumber)) ?? 0
    }

    /// 두 날짜 사이 평일 수 (시작 날 빼고 끝 날 넣음). 공휴일은 모른다
    static func weekdays(from a: String, to b: String) -> Int {
        let f = DateFormatter(); f.timeZone = MockFmt.kst; f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
        guard var d = f.date(from: a), let e = f.date(from: b), d < e else { return 0 }
        var cal = Calendar(identifier: .gregorian); cal.timeZone = MockFmt.kst
        var n = 0
        while d < e {
            d = cal.date(byAdding: .day, value: 1, to: d)!
            let w = cal.component(.weekday, from: d)
            if w != 1 && w != 7 { n += 1 }
        }
        return n
    }

    // MARK: 국내 호가 단위 (2023-01 이후 코스피·코스닥 같은 표)

    static func tick(_ p: Double) -> Double {
        switch p {
        case ..<2_000: return 1
        case ..<5_000: return 5
        case ..<20_000: return 10
        case ..<50_000: return 50
        case ..<200_000: return 100
        case ..<500_000: return 500
        default: return 1_000
        }
    }
    /// 지정가를 호가 단위에 맞춘다 (매수는 내림, 매도는 올림: 덜 유리한 쪽으로 붙지 않게)
    static func snap(_ p: Double, side: MockSide) -> Double {
        let t = tick(p)
        return side == .buy ? (p / t).rounded(.down) * t : (p / t).rounded(.up) * t
    }

    // MARK: 성적

    struct Stat: Equatable {
        var trades = 0, wins = 0
        var pnl = 0.0, pnlGross = 0.0, winSum = 0.0, lossSum = 0.0, basis = 0.0
        var winRate: Double? { trades > 0 ? Double(wins) / Double(trades) : nil }
        var ret: Double? { basis > 0 ? pnl / basis : nil }
        var retGross: Double? { basis > 0 ? pnlGross / basis : nil }
        mutating func add(_ e: MockEvent) {
            guard let p = e.pnl else { return }
            trades += 1; pnl += p; pnlGross += e.pnlGross ?? p; basis += e.basis ?? 0
            if p > 0 { wins += 1; winSum += p } else { lossSum += p }
        }
    }

    /// 매도 체결만 매매 한 건으로 센다. test: 시험 체결만(참)·정식만(거짓)
    static func stat(_ events: [MockEvent], ledger: UUID, who: MockWho? = nil, test: Bool = false, since: Date? = nil) -> Stat {
        var s = Stat()
        for e in events where e.ledger == ledger && e.kind == .fill && e.side == .sell && e.test == test {
            if let who, (e.opener ?? e.who) != who { continue }
            if let since, e.at < since { continue }
            s.add(e)
        }
        return s
    }

    enum Unit: String, CaseIterable, Identifiable {
        case hour, day, week, month, year
        var id: String { rawValue }
        var name: String {
            switch self {
            case .hour: return "시간"
            case .day: return "일"
            case .week: return "주"
            case .month: return "월"
            case .year: return "년"
            }
        }
    }

    /// 기간 한 칸의 이름 (한국 시각)
    static func bucket(_ d: Date, _ u: Unit) -> String {
        var cal = Calendar(identifier: .iso8601); cal.timeZone = MockFmt.kst
        let c = cal.dateComponents([.year, .month, .day, .hour, .weekOfYear, .yearForWeekOfYear], from: d)
        switch u {
        case .hour: return String(format: "%d/%d %02d시", c.month!, c.day!, c.hour!)
        case .day: return String(format: "%d/%d", c.month!, c.day!)
        case .week: return String(format: "%d년 %d주", c.yearForWeekOfYear!, c.weekOfYear!)
        case .month: return String(format: "%d년 %d월", c.year!, c.month!)
        case .year: return "\(c.year!)년"
        }
    }

    /// 기간별 성적 (최근 칸부터). 정식 체결만
    static func periods(_ events: [MockEvent], ledger: UUID, unit: Unit) -> [(key: String, stat: Stat)] {
        var order: [String] = [], by: [String: Stat] = [:]
        for e in events.sorted(by: { $0.at > $1.at }) where e.ledger == ledger && e.kind == .fill && e.side == .sell && !e.test {
            let k = bucket(e.at, unit)
            if by[k] == nil { order.append(k) }
            by[k, default: Stat()].add(e)
        }
        return order.map { ($0, by[$0]!) }
    }

    /// 이유 따라가기: 체결 하나에서 주문 → 선정 → 관찰로 거슬러 간다 (오래된 단계부터).
    /// 체결·미체결 줄의 ref = 주문 id = 주문 줄의 id, 주문 줄의 ref = 선정 줄, 선정 줄의 ref = 관찰 줄
    static func chain(_ e: MockEvent, in events: [MockEvent]) -> [MockEvent] {
        var byId: [UUID: MockEvent] = [:]
        for x in events { byId[x.id] = x }
        var out: [MockEvent] = [e]
        var cur = e.ref.flatMap { byId[$0] }
        var n = 0
        while let c = cur, n < 6 {
            out.append(c)
            cur = c.ref.flatMap { byId[$0] }
            n += 1
        }
        // 같은 주문의 미체결 줄도 함께 (왜 기다렸나). 체결이 여러 번이면 앞 체결도
        let same = events.filter { ($0.kind == .wait || $0.kind == .fill) && $0.ref == e.ref && $0.id != e.id && e.ref != nil }
        let rank: [MockEvent.Kind: Int] = [.scan: 0, .exclude: 1, .select: 2, .short: 2, .order: 3, .wait: 4, .cancel: 5, .fill: 6]
        return (out + same).sorted { $0.at != $1.at ? $0.at < $1.at : (rank[$0.kind] ?? 9) < (rank[$1.kind] ?? 9) }
    }
}
#endif
