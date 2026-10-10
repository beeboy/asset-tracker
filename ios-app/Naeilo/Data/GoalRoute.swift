import SwiftUI

// 목표 달성 루트 (시안 목표·플러스): 목표 정하기 → 구성 고르기 → (플러스) 비중 조정 세금 → 앱 시작 3단계 → 3개월 인터미션.
// 금액 단위는 만원. 확률은 시안과 같은 씨앗(12345)의 500경로 몬테카를로라 숫자가 시안과 같다.

enum Route: String, Codable { case recover, plus, novice }

enum GoalWeek: String, CaseIterable, Codable { case inPlan, less, more
    var label: String { switch self { case .inPlan: "계획대로"; case .less: "덜 넣음"; case .more: "더 넣음" } }
    var factor: Double { switch self { case .inPlan: 1; case .less: 0.5; case .more: 1.5 } }
    var color: Color { switch self { case .inPlan: Theme.green; case .less: Theme.orange; case .more: Theme.blue } }
}

struct GoalMix: Identifiable, Hashable {
    let id: String
    let name: String
    let tag: String
    let desc: String
    let mu: Double
    let sigma: Double
    let comp: [String: Double]     // I 지수 · G 성장 지속 · B 버팀목 · C 현금
    let basket: Basket?
    var own = false
}

struct GoalSim {
    let mix: GoalMix
    let prob: Double
    let etaMonths: Double          // 보통의 경우 도착 (개월, 무한대 가능)
    let med: [Double], lo: [Double], hi: [Double], principal: [Double]   // 해마다 (만원)
    var badYear: Double { exp(mix.mu - mix.sigma * mix.sigma / 2 - 1.645 * mix.sigma) - 1 }
}

extension AppModel {
    static func wonK(_ m: Double) -> String {
        if m < 0.5 { return "0원" }
        if m >= 10000 { return ((m / 1000).rounded() / 10).formatted() + "억원" }
        return Int(m.rounded()).formatted() + "만원"
    }
    static func eta(_ mo: Double) -> String {
        guard mo.isFinite else { return "40년 넘게" }
        let m = Int(mo), y = m / 12, r = m % 12
        return (y > 0 ? "\(y)년 " : "") + (r > 0 ? "\(r)개월 뒤" : "뒤")
    }

    var isGoal: Bool { route != .recover }
    /// 출발 금액 (만원): 플러스는 지금 평가액, 시작 전은 모아 둔 투자금
    var goalStart: Double { route == .plus ? total / 1e4 : gA }
    /// 1000칸·주간 예보에서 쓰는 지금 금액 (원)
    /// 분석 화면의 기준선 (원): 회복 = 들어간 돈(본전), 목표 = 목표 금액
    var keyValue: Double { isGoal ? gK * 1e4 : cost }
    var keyName: String { isGoal ? "목표" : "본전" }
    var trackValue: Double { route == .novice ? goalSaved * 1e4 : total }   // 시작 전: 모아 둔 돈 + 인터미션에 넣은 돈

    static let mixes: [GoalMix] = [
        .init(id: "index", name: "지수 중심", tag: "단순하게", desc: "미국 전체 지수 80% · 현금 20%", mu: 0.075, sigma: 0.15, comp: ["I": 0.8, "C": 0.2], basket: nil),
        .init(id: "grow", name: "성장 더하기", tag: "더 빨리, 더 흔들림", desc: "지수 60% · 성장 지속 업종 30% · 현금 10%", mu: 0.085, sigma: 0.19, comp: ["I": 0.6, "G": 0.3, "C": 0.1], basket: .G),
        .init(id: "steady", name: "버팀목 더하기", tag: "덜 흔들림", desc: "지수 50% · 버팀목 업종 30% · 채권·현금 20%", mu: 0.06, sigma: 0.10, comp: ["I": 0.5, "B": 0.3, "C": 0.2], basket: .B),
    ]
    var mineMix: GoalMix {
        .init(id: "mine", name: "지금 내 구성", tag: "그대로", desc: "DRNK \(AppModel.pct(drnkWeight)) · QQQ \(AppModel.pct(1 - drnkWeight)) 그대로",
              mu: 0.09, sigma: keepPlan.sigma, comp: [:], basket: nil, own: true)
    }
    var goalMixes: [GoalMix] { route == .novice ? Self.mixes : [mineMix] + Self.mixes }
    var selectedMix: GoalMix { goalMixes.first { $0.id == gMix } ?? goalMixes[0] }

    func simulate(_ mx: GoalMix, monthly: Double? = nil) -> GoalSim {
        let mo = monthly ?? gM, gY = self.gY, K = gK, A = goalStart
        var seed = 12345
        func rnd() -> Double { seed = (seed &* 1103515245 &+ 12345) % 2147483648; return (Double(seed) + 0.5) / 2147483648 }
        let NP = 500, H = min(40, gY * 2 + 5) * 12
        let mm = (mx.mu - mx.sigma * mx.sigma / 2) / 12, ms = mx.sigma / sqrt(12)
        var byYear = Array(repeating: [Double](), count: gY + 1)
        var hits: [Double] = []
        for _ in 0..<NP {
            var v = A, hit = Double.infinity
            byYear[0].append(v)
            for k in 1...H {
                let z = sqrt(-2 * log(rnd())) * cos(6.283185307 * rnd())
                v = v * exp(mm + ms * z) + mo
                if hit == .infinity && v >= K { hit = Double(k) }
                if k % 12 == 0 && k / 12 <= gY { byYear[k / 12].append(v) }
                if hit != .infinity && k >= gY * 12 { break }
            }
            hits.append(hit)
        }
        func q(_ a: [Double], _ f: Double) -> Double { let s = a.sorted(); return s[min(s.count - 1, Int(f * Double(s.count)))] }
        let prob = Double(hits.filter { $0 <= Double(gY * 12) }.count) / Double(NP)
        return GoalSim(mix: mx, prob: prob, etaMonths: q(hits, 0.5),
                       med: byYear.map { q($0, 0.5) }, lo: byYear.map { q($0, 0.05) }, hi: byYear.map { q($0, 0.95) },
                       principal: (0...gY).map { A + mo * 12 * Double($0) })
    }

    /// 보통의 경우 기준으로 목표에 맞추려면 매달 넣을 돈 (만원)
    func neededMonthly(k: Double? = nil, years: Int? = nil) -> Double {
        let ix = route == .novice ? Self.mixes[0] : mineMix
        let gmm = exp((ix.mu - ix.sigma * ix.sigma / 2) / 12), fv = pow(gmm, Double((years ?? gY) * 12))
        return max(0, ((k ?? gK) - goalStart * fv) * (gmm - 1) / (fv - 1))
    }
    func monthsTo(from start: Double, monthly: Double) -> Double {
        let ix = route == .novice ? Self.mixes[0] : mineMix
        let gmm = exp((ix.mu - ix.sigma * ix.sigma / 2) / 12)
        var v = start
        for k in 1...480 { v = v * gmm + monthly; if v >= gK { return Double(k) } }
        return .infinity
    }

    struct GoalPreset: Identifiable { var id: String { name }; let k: Double; let y: Int; let name: String; let mean: String; let need: String }
    var goalPresets: [GoalPreset] {
        let A = goalStart
        func nice(_ x: Double) -> Double {
            x < 10000 ? max(1000, (x / 1000).rounded() * 1000) : x < 50000 ? (x / 5000).rounded() * 5000 : (x / 10000).rounded() * 10000
        }
        func meaning(_ k: Double) -> String {
            k <= 1000 ? "첫 목표 · 적립 습관 만들기" : k <= 10000 ? "연 7%면 한 해 \(Self.wonK(k * 0.07))씩 불어나는 크기" : "연 4%씩 꺼내 쓰면 월 \(Self.wonK(k * 0.04 / 12))"
        }
        let raw: [(Double, Int, String, Int?)] = route == .novice
            ? [(1000, 3, "첫 1,000만원", nil), (10000, 5, "1억", nil), (30000, 10, "3억", nil), (100000, 20, "10억", nil)]
            : [(2, 1), (3, 2), (4, 3), (5, 4)].map { x, y in let k = nice(A * Double(x)); return (k, y, Self.wonK(k).replacingOccurrences(of: "원", with: ""), x) }
        return raw.map { k, y, name, x in
            let nm = neededMonthly(k: k, years: y), cagr = A > 0 ? pow(k / A, 1 / Double(y)) - 1 : 0
            return GoalPreset(k: k, y: y, name: x.map { "\(name) (\($0)배)" } ?? name,
                              mean: x != nil ? "적립 없이 가려면 한 해 +\(Int((cagr * 100).rounded()))%씩" : meaning(k),
                              need: nm > 0 ? "월 " + Self.wonK(nm) : "이미 충분")
        }
    }

    // 미션 목록 (시작 전 / 플러스)
    struct GoalStep: Identifiable { let id: String; let num: String; let title: String; let reward: String; let to: MissionRoute; let inter: Bool }
    var goalSteps: [GoalStep] {
        let list: [(String, String, String, MissionRoute, Bool)] = route == .novice ? [
            ("goal", "목표 정하기", "목표까지 경로", .g1, false), ("mix", "투자 구성 고르기", "구성 비교", .g3, false),
            ("link", "앱 시작 3단계", "위젯 전부", .nx, false), ("inter", "3개월, 매달 1분", "캐릭터 위젯 3개", .gi, true),
        ] : [
            ("hold", "종목·단가 넣기", "평가액·수익률", .m1, false), ("goal", "목표 정하기", "목표까지 경로", .g1, false),
            ("mix", "내 구성과 비교", "구성 비교", .g3, false), ("tax", "비중 조정 세금", "절세 순서", .gt, false),
            ("link", "앱 시작 3단계", "위젯 전부", .nx, false), ("inter", "3개월, 매달 1분", "캐릭터 위젯 3개", .gi, true),
        ]
        return list.enumerated().map { i, x in GoalStep(id: x.0, num: x.4 ? "인터미션" : "\(i + 1)", title: x.1, reward: x.2, to: x.3, inter: x.4) }
    }
    var goalTotal: Int { goalSteps.filter { !$0.inter }.count }
    func goalNo(_ id: String) -> String { goalSteps.first { $0.id == id }?.num ?? "" }
    func goalAvailable(_ s: GoalStep) -> Bool {
        guard let i = goalSteps.firstIndex(where: { $0.id == s.id }) else { return false }
        return i == 0 || gDone.contains(goalSteps[i - 1].id)
    }

    // 고른 구성으로 바꿀 비중과 금액 (플러스: 지금 DRNK·QQQ 에서 출발, 시작 전: 매달 나눠 넣기)
    struct Shift { var rows: [(String, String, String, Bool)] = []; var line = ""; var note = ""; var weights: [String: Double] = [:]; var sell = 0.0 }
    func shift(_ mx: GoalMix) -> Shift {
        let w = drnkWeight, label = ["T": "DRNK", "I": "지수", "G": "성장 지속", "B": "버팀목", "C": mx.id == "steady" ? "채권·현금" : "현금"]
        if mx.own {
            return Shift(rows: [("DRNK", AppModel.pct(w), "그대로", true), ("QQQ", AppModel.pct(1 - w), "그대로", true)],
                         line: "DRNK 우주항공·궤도 통신·과열 \(AppModel.pct(w)) · QQQ 지수 \(AppModel.pct(1 - w))",
                         note: "DRNK 업종(우주항공·궤도 통신)은 지금 과열이에요. 다른 구성 카드에서 바꿀 비중을 볼 수 있어요.", weights: ["T": w, "I": 1 - w])
        }
        let keys = ["T", "I", "G", "B", "C"]
        if route == .novice {
            return Shift(rows: keys.compactMap { k in mx.comp[k].map { (label[k]!, AppModel.pct($0), "월 " + Self.wonK(gM * $0), true) } },
                         line: mx.basket.map { b in "\(b.name): " + b.industries.prefix(3).map(\.ko).joined(separator: " · ") + " 외 \(b.industries.count - 3)개" } ?? "",
                         weights: mx.comp)
        }
        // DRNK 업종은 과열이라 세 묶음에 들지 않아 10%까지만 둔다 (시안 T_CAP)
        let tT = min(w, 0.10)
        var tgt = mx.comp.mapValues { $0 * (1 - tT) }; tgt["T"] = tT
        let now = ["T": w, "I": 1 - w], tot = goalStart
        let used = keys.filter { (now[$0] ?? 0) > 0 || (tgt[$0] ?? 0) > 0 }
        let rows = used.map { k -> (String, String, String, Bool) in
            let a = tgt[k] ?? 0, b = now[k] ?? 0, d = (a - b) * tot
            return (label[k]!, "\(Int((b * 100).rounded()))→\(AppModel.pct(a))", abs(d) < 0.5 ? "그대로" : (d > 0 ? "+" : "-") + Self.wonK(abs(d)), d >= 0)
        }
        let short = ["T": "DRNK", "I": "지수", "G": "성장", "B": "버팀목", "C": "현금"]
        let line = used.map { k in short[k]! + " " + ((now[k] ?? 0) > 0 ? "\(Int(((now[k] ?? 0) * 100).rounded()))→\(AppModel.pct(tgt[k] ?? 0))" : "+" + AppModel.pct(tgt[k] ?? 0)) }.joined(separator: " · ")
        var note = "DRNK는 과열 업종이라 10%까지만 둬요."
        if tT < w && gM > 0 && tot > 0 {
            let mo = ceil((w * tot / tT - tot) / gM)
            note += " 팔지 않고 적립만으로 맞추면 약 \(Self.eta(mo).replacingOccurrences(of: " 뒤", with: "")), 팔면 250만원 넘는 이익에 22% 세금."
        }
        return Shift(rows: rows, line: line, note: note, weights: tgt, sell: max(0, w - tT))
    }

    // 3개월 인터미션
    static let goalMonthSteps: [(task: String, whereText: String, reward: String)] = [
        ("앱에서 이번 달 적립 기록하기", "앱", "세리"), ("PC에서 증권사 거래내역 파일 올리기", "PC 웹", "시오"), ("앱에서 배당·세금 요약 보기", "앱", "모두"),
    ]
    func confirmGoalMonth() {
        guard let c = gWeekCur, gWeeks.count < 3 else { return }
        gWeeks.append(c); gWeekCur = nil
    }
    var goalSaved: Double { gA + (gWeeks + (gWeekCur.map { [$0] } ?? [])).reduce(0) { $0 + gM * $1.factor } }

    func switchRoute(_ r: Route) {
        route = r; boardPath = []; analysisPath = NavigationPath()
        switch r {
        case .recover: break
        case .plus: gMix = "mine"; gK = (goalStart * 2 / 1000).rounded() * 1000; gY = 1
        case .novice: gA = 0; gK = 1000; gY = 3; gM = 30; gMix = "index"
        }
        monthly = r == .recover ? 0 : gM     // 3년 전망의 '만약에 매달' 기본값 = 적립액

    }
}
