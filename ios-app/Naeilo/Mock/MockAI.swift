#if MOCK
import Foundation

// AI 조언 (Claude). AI 는 조언자다: 규칙 장부를 고치지 않고, 자기 장부(AI 장부, 1,000만원)를 따로 굴린다.
// 키·한도·쓴 돈은 개발자 서버(server.py /api/mock/ai)에 있다. 여기서는 물음을 만들고, 답을 받아 지킬 것을 다시 본다.

/// AI 부르는 때와 한도 (설정에서 드링커님이 정한다)
struct MockAISettings: Codable, Equatable {
    var ledgerEvery: Int = 10      // AI 장부: 장중 몇 분마다 (0 = 끔)
    var reviewEach: Bool = true    // 규칙이 고를 때마다 의견
    var daily: Bool = true         // 장 마감 뒤 하루 정리 (하루 한 번)
    // 말로 쓴 조건은 누를 때만 부른다 (설정 없음)
    var day: Double = 4            // 달러
    var week: Double = 18
    var month: Double = 80
    var limits: [String: Double] { ["day": day, "week": week, "month": month] }
}

/// 서버가 알려 준 쓴 돈 (달러)
struct MockAIWallet: Codable, Equatable {
    var day = 0.0, week = 0.0, month = 0.0
    var at: Date
    var key = true                 // 서버에 키가 있나
}

/// 규칙이 고른 종목 하나에 대한 AI 의견
struct MockAIView: Codable, Equatable {
    var code: String
    var view: String               // agree · doubt · drop
    var why: String
    var name: String {
        switch view {
        case "agree": return "찬성"
        case "drop": return "빼자"
        default: return "글쎄"
        }
    }
}

enum MockAI {
    /// AI 장부가 낼 주문 하나 (앱이 다시 확인한 뒤)
    struct Plan: Equatable {
        var code: String
        var name: String
        var side: MockSide
        var won: Double            // 매수에 쓸 돈
        var qty: Int?              // 매도 수량 (nil = 전부)
        var why: String
    }

    static let maxOrders = 3

    // MARK: 물음 만들기 (같은 장면이면 같은 글이 되게 시각은 날짜까지만)

    static func ledgerPrompt(_ l: MockLedger, prices: [String: Double], hits: [MockHit], bundleName: String?,
                             excluded: [String: String], day: String) -> String? {
        let cands = hits.filter { excluded[$0.code] == nil }.prefix(30)
        guard !cands.isEmpty || !l.positions.isEmpty else { return nil }
        let eq = MockCalc.equity(l, prices: prices)
        var t = "\(day) 한국 장중. 너의 장부(AI 장부): 시작 \(MockFmt.won(l.startCash)), 지금 평가 \(MockFmt.won(eq)), 쓸 수 있는 현금 \(MockFmt.won(l.cash)).\n"
        t += "보유:\n"
        if l.positions.isEmpty { t += "- 없음\n" }
        for p in l.positions.values.sorted(by: { $0.code < $1.code }) {
            let now = prices[p.code] ?? p.avg
            t += "- \(p.code) \(p.name) \(p.qty)주 · 평단 \(MockFmt.won(p.avg)) · 지금 \(MockFmt.won(now)) · \(MockFmt.pct(p.avg > 0 ? now / p.avg - 1 : 0)) · 산 날 \(p.openedDay)\n"
        }
        if !l.pending.isEmpty {
            t += "대기 주문:\n"
            for o in l.pending { t += "- \(o.code) \(o.name) \(o.side.name) \(o.qty)주\n" }
        }
        t += "후보 (관찰 결과 위쪽" + (bundleName.map { ", 조건 묶음 '\($0)'" } ?? "") + "):\n"
        if cands.isEmpty { t += "- 없음\n" }
        for h in cands {
            t += "- \(h.code) \(h.name) · \(MockFmt.won(prices[h.code] ?? h.price))" + (h.change.map { " · " + MockFmt.pct($0, digits: 1) } ?? "") + " · \(h.why)\n"
        }
        t += """
        할 일: 이 장부로 지금 살 것과 팔 것을 정해 orders 에 넣어라.
        - 매수: side buy, won 은 쓸 돈(원, 쓸 수 있는 현금 이하), qty 0. 후보 밖 종목, 이미 보유한 종목, 대기 주문이 있는 종목은 사지 않는다.
        - 매도: side sell, 보유 종목만, qty 는 팔 주 수(0 = 전부), won 0.
        - 한 번에 \(maxOrders)건 이하. 할 게 없으면 orders 를 비운다.
        note 에 지금 판단을 한 문장으로 쓴다.
        """
        return t
    }

    static func reviewPrompt(_ picks: [MockCalc.Pick], bundleName: String?, day: String) -> String? {
        guard !picks.isEmpty else { return nil }
        var t = "\(day). 규칙이 관찰 결과에서 아래 종목을 골랐다" + (bundleName.map { " (조건 묶음 '\($0)')" } ?? "") + ".\n"
        for p in picks {
            t += "- \(p.code) \(p.name) · \(p.rank)위 · \(MockFmt.won(p.price)) · 비중 \(String(format: "%.0f%%", p.weight * 100)) · \(p.why)\n"
        }
        t += "할 일: 종목마다 view 를 agree(찬성)·doubt(글쎄)·drop(빼자) 중 하나로 정하고 why 를 쓴다. 주문은 바꾸지 않는다. note 에 고른 묶음 전체에 대한 한 문장."
        return t
    }

    static func dailyPrompt(day: String, lines: [String]) -> String {
        "\(day) 장 마감 뒤 모의투자 하루 기록이다.\n" + lines.joined(separator: "\n") +
        "\n할 일: summary 에 오늘을 두 문장으로 정리하고, points 에 규칙·손·AI 를 비교해 내일 볼 것을 세 개 이하로 쓴다."
    }

    static func wordsPrompt(_ words: String) -> String {
        """
        국내 주식 관찰 조건을 말로 받았다: "\(words)"
        이것을 조건 조각으로 바꿔라. 조각 종류:
        - price: 지금 가격(원). lo·hi 는 원
        - change: 오늘 등락률(%). 2 = +2%
        - volume: 오늘 거래량 ÷ 지난 days 일 평균 (배)
        - ma: 지금 가격이 days 일 이동평균 위(above true)·아래(false). lo·hi 는 쓰지 않는다
        - amount: 오늘 거래대금(억원)
        lo 를 쓰면 hasLo true, 안 쓰면 hasLo false 와 lo 0. hi 도 같다. days 는 volume·ma 만 뜻이 있고 나머지는 20.
        조각은 모두 맞아야 걸린다. 말에 없는 조건은 넣지 않는다. sort 는 결과를 줄 세울 기준(큰 순). name 은 짧은 이름, note 는 바꾸면서 버린 말이 있으면 한 문장.
        """
    }

    // MARK: 답 확인 (AI 가 정해진 꼴로 답해도 지킬 것은 앱이 다시 본다)

    static func ledgerPlans(_ answer: [String: Any], ledger l: MockLedger, candidates: [String: String],
                            excluded: [String: String]) -> (plans: [Plan], dropped: [String], note: String) {
        var plans: [Plan] = [], dropped: [String] = []
        var cash = l.cash
        for o in answer["orders"] as? [[String: Any]] ?? [] {
            let code = (o["code"] as? String ?? "").trimmingCharacters(in: .whitespaces)
            let why = String((o["why"] as? String ?? "").prefix(80))
            guard plans.count < maxOrders else { dropped.append("\(code) · \(maxOrders)건 넘음"); continue }
            switch o["side"] as? String {
            case "buy":
                guard let name = candidates[code] else { dropped.append("\(code) · 후보 밖"); continue }
                if let ex = excluded[code] { dropped.append("\(code) · 예외 · \(ex)"); continue }
                if l.positions[code] != nil || l.pending.contains(where: { $0.code == code }) || plans.contains(where: { $0.code == code }) {
                    dropped.append("\(code) · 이미 보유·대기"); continue
                }
                let won = min(MockFeed.num(o["won"]) ?? 0, cash)
                guard won > 0 else { dropped.append("\(code) · 쓸 돈 없음"); continue }
                cash -= won
                plans.append(Plan(code: code, name: name, side: .buy, won: won, qty: nil, why: why))
            case "sell":
                guard let p = l.positions[code] else { dropped.append("\(code) · 보유 아님"); continue }
                if plans.contains(where: { $0.code == code }) { dropped.append("\(code) · 겹침"); continue }
                let q = Int(MockFeed.num(o["qty"]) ?? 0)
                plans.append(Plan(code: code, name: p.name, side: .sell, won: 0, qty: q > 0 && q < p.qty ? q : nil, why: why))
            default:
                dropped.append("\(code) · 모르는 주문")
            }
        }
        return (plans, dropped, String((answer["note"] as? String ?? "").prefix(200)))
    }

    static func reviewViews(_ answer: [String: Any], picks: [MockCalc.Pick]) -> [String: MockAIView] {
        let codes = Set(picks.map(\.code))
        var out: [String: MockAIView] = [:]
        for i in answer["items"] as? [[String: Any]] ?? [] {
            guard let c = i["code"] as? String, codes.contains(c) else { continue }
            let v = i["view"] as? String ?? "doubt"
            out[c] = MockAIView(code: c, view: ["agree", "doubt", "drop"].contains(v) ? v : "doubt", why: String((i["why"] as? String ?? "").prefix(80)))
        }
        return out
    }

    /// 말 → 조건 묶음. 조각이 하나도 없으면 nil
    static func wordsBundle(_ answer: [String: Any], words: String) -> (bundle: MockBundle, note: String)? {
        var pieces: [MockPiece] = []
        for p in answer["pieces"] as? [[String: Any]] ?? [] {
            guard let k = MockPiece.Kind(rawValue: p["kind"] as? String ?? "") else { continue }
            var x = MockPiece(kind: k)
            if p["hasLo"] as? Bool == true { x.lo = MockFeed.num(p["lo"]) }
            if p["hasHi"] as? Bool == true { x.hi = MockFeed.num(p["hi"]) }
            if k == .volume || k == .ma { x.days = min(120, max(2, Int(MockFeed.num(p["days"]) ?? 20))) }
            x.above = p["above"] as? Bool ?? true
            if k == .ma { x.lo = nil; x.hi = nil }
            if let lo = x.lo, let hi = x.hi, lo > hi { (x.lo, x.hi) = (hi, lo) }
            pieces.append(x)
        }
        guard !pieces.isEmpty else { return nil }
        let name = (answer["name"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let sort = MockPiece.Kind(rawValue: answer["sort"] as? String ?? "") ?? .volume
        var b = MockBundle(name: name.isEmpty ? String(words.prefix(16)) : String(name.prefix(24)), pieces: pieces, sort: sort)
        if let d = pieces.first(where: { $0.kind == sort })?.days { b.sortDays = d }
        return (b, answer["note"] as? String ?? "")
    }

    /// 서버 답에서 쓴 기록
    static func use(_ o: [String: Any], kind: String, summary: String) -> MockAIUse {
        MockAIUse(model: o["model"] as? String ?? "?", input: Int(MockFeed.num(o["input"]) ?? 0), output: Int(MockFeed.num(o["output"]) ?? 0),
                  cost: MockFeed.num(o["cost"]) ?? 0, summary: kind + " · " + String(summary.prefix(60)), cached: o["cached"] as? Bool ?? false)
    }

    static func wallet(_ o: [String: Any], at: Date) -> MockAIWallet? {
        guard let sp = o["spent"] as? [String: Any] else { return nil }
        return MockAIWallet(day: MockFeed.num(sp["day"]) ?? 0, week: MockFeed.num(sp["week"]) ?? 0, month: MockFeed.num(sp["month"]) ?? 0,
                            at: at, key: o["key"] as? Bool ?? true)
    }
}
#endif
