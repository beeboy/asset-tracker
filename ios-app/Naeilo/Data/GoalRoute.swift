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

    // 목표 시작일·목표일: 사이트(goal.start_date·goal.date)와 같은 값을 쓴다 (기기 동기화로 양쪽이 맞춘다)
    var goalFromDate: Date { gFrom.flatMap(Day.date) ?? goalStartAt.map { Date(timeIntervalSince1970: $0) } ?? Date() }
    var goalToDate: Date { gTo.flatMap(Day.date) ?? Calendar.current.date(byAdding: .year, value: gY, to: goalFromDate)! }
    /// 목표 시작일부터 목표일까지 (년)
    var goalSpan: Double { max(1.0 / 12, goalToDate.timeIntervalSince(goalFromDate) / (365.25 * 86400)) }
    var goalToText: String { let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "yyyy년 M월 d일"; return f.string(from: goalToDate) }

    /// 사이트 목표(goal)를 그대로 받는다: 금액·목표일·시작일·매달 넣는 돈 (원 → 만원)
    func takeSiteGoal(_ g: [String: Any]) {
        let amt = Sync.num(g["amount"])
        guard amt > 0, let ds = g["date"] as? String, let to = Day.date(ds) else { return }
        let fs = (g["start_date"] as? String).flatMap { Day.date($0) != nil ? $0 : nil } ?? Day.today
        let from = Day.date(fs) ?? Date()
        gK = amt / 1e4
        gY = max(1, Int((to.timeIntervalSince(from) / (365.25 * 86400)).rounded()))   // 미션 계산용 햇수 (gTo 보다 먼저)
        gTo = ds; gFrom = fs
        gM = max(0, Sync.num(g["monthly_contribution"]) / 1e4)
        if isGoal { monthly = gM }      // 3년 전망의 '만약에 매달'도 같은 적립액으로
    }
    /// 사이트로 보낼 목표 (원)
    func siteGoal(_ base: [String: Any]?) -> [String: Any] {
        var g = base ?? [:]
        g["amount"] = (gK * 1e4).rounded()
        g["date"] = Day.str(goalToDate)
        g["start_date"] = Day.str(goalFromDate)
        g["monthly_contribution"] = (gM * 1e4).rounded()
        return g
    }

    static let mixes: [GoalMix] = [
        .init(id: "index", name: "지수 중심", tag: "단순하게", desc: "미국 전체 지수 80% · 현금 20%", mu: 0.075, sigma: 0.15, comp: ["I": 0.8, "C": 0.2], basket: nil),
        .init(id: "grow", name: "성장 더하기", tag: "더 빨리, 더 흔들림", desc: "지수 60% · 성장 지속 업종 30% · 현금 10%", mu: 0.085, sigma: 0.19, comp: ["I": 0.6, "G": 0.3, "C": 0.1], basket: .G),
        .init(id: "steady", name: "버팀목 더하기", tag: "덜 흔들림", desc: "지수 50% · 버팀목 업종 30% · 채권·현금 20%", mu: 0.06, sigma: 0.10, comp: ["I": 0.5, "B": 0.3, "C": 0.2], basket: .B),
    ]
    var mineMix: GoalMix {
        .init(id: "mine", name: "지금 내 구성", tag: "그대로", desc: "\(focusName) \(AppModel.pct(focusWeight))" + (restRows.isEmpty ? "" : " · \(restName) \(AppModel.pct(1 - focusWeight))") + " 그대로",
              mu: 0.09, sigma: keepPlan.sigma, comp: [:], basket: nil, own: true)
    }
    var goalMixes: [GoalMix] { route == .novice ? Self.mixes : [mineMix] + Self.mixes }
    var selectedMix: GoalMix { goalMixes.first { $0.id == gMix } ?? goalMixes[0] }

    /// k·years·start 를 주면 지금 목표 대신 그 값으로 (목표 자동 고르기에서 씀, cache: false 면 캐시에 넣지 않는다)
    func simulate(_ mx: GoalMix, monthly: Double? = nil, k: Double? = nil, years: Int? = nil, start: Double? = nil, cache: Bool = true) -> GoalSim {
        let mo = monthly ?? gM, gY = years ?? self.gY, K = k ?? gK, A = start ?? goalStart
        let ck = "\(mx.mu)|\(mx.sigma)|\(mo)|\(gY)|\(K)|\(A)"
        if cache, let c = simCache[ck] { return GoalSim(mix: mx, prob: c.prob, etaMonths: c.etaMonths, med: c.med, lo: c.lo, hi: c.hi, principal: c.principal) }
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
        let r = GoalSim(mix: mx, prob: prob, etaMonths: q(hits, 0.5),
                        med: byYear.map { q($0, 0.5) }, lo: byYear.map { q($0, 0.05) }, hi: byYear.map { q($0, 0.95) },
                        principal: (0...gY).map { A + mo * 12 * Double($0) })
        if cache {
            if simCache.count > 64 { simCache.removeAll() }
            simCache[ck] = r
        }
        return r
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

    /// 보기 좋은 금액 (만원): 앞 두 자리만 남긴다 (5억 4천 → 5.4억, 3,460만 → 3,500만)
    static func round2(_ x: Double) -> Double {
        guard x >= 100 else { return 100 }
        let u = pow(10, floor(log10(x)) - 1)
        return (x / u).rounded() * u
    }
    /// 목표 금액 자동 고르기: 지금 금액에서 매달 monthly(만원)씩 넣을 때 years 안에 닿을 확률이 50% 근처인 금액 (만원).
    /// 확률은 목표 화면과 같은 계산(simulate)이라 고른 뒤 화면에 보이는 확률도 50% 근처다
    func autoGoal(years: Int, monthly: Double? = nil, from start: Double? = nil) -> Double {
        let A = start ?? goalStart, mo = max(0, monthly ?? gM), mx = route == .novice ? Self.mixes[0] : mineMix
        guard A + mo > 0 else { return 1000 }
        let ck = "\(mx.mu)|\(mx.sigma)|\(mo)|\(years)|\(A)"
        if let c = autoCache[ck] { return c }
        func p(_ K: Double) -> Double { simulate(mx, monthly: mo, k: K, years: years, start: A, cache: false).prob }
        var lo = max(1, A), hi = max(lo * 2, A + mo * 12 * Double(years) * 2)
        var n = 0
        while p(hi) > 0.5 && n < 20 { lo = hi; hi *= 2; n += 1 }
        for _ in 0..<14 {
            let mid = (lo + hi) / 2
            if p(mid) > 0.5 { lo = mid } else { hi = mid }
        }
        let r = max(Self.round2(A + 1), Self.round2((lo + hi) / 2))
        if autoCache.count > 64 { autoCache.removeAll() }
        autoCache[ck] = r
        return r
    }

    struct GoalPreset: Identifiable { var id: String { name }; let k: Double; let y: Int; let name: String; let mean: String; let need: String }
    var goalPresets: [GoalPreset] {
        let A = goalStart
        func meaning(_ k: Double) -> String {
            k <= 1000 ? "첫 목표 · 적립 습관 만들기" : k <= 10000 ? "연 7%면 한 해 \(Self.wonK(k * 0.07))씩 불어나는 크기" : "연 4%씩 꺼내 쓰면 월 \(Self.wonK(k * 0.04 / 12))"
        }
        // 플러스: 기간마다 '매달 넣는 돈으로 닿을 확률 50% 근처' 금액 (배수 프리셋 대신, 2026-10-11 결정)
        if route != .novice {
            return [1, 3, 5, 10].map { y in
                let k = autoGoal(years: y)
                return GoalPreset(k: k, y: y, name: Self.wonK(k).replacingOccurrences(of: "원", with: ""),
                                  mean: (gM > 0 ? "월 \(Self.wonK(gM))씩 넣으면" : "적립 없이") + " 닿을 확률 50% 근처",
                                  need: A > 0 ? "지금의 \(String(format: "%.1f", k / A))배" : "")
            }
        }
        let raw: [(Double, Int, String, Int?)] = [(1000, 3, "첫 1,000만원", nil), (10000, 5, "1억", nil), (30000, 10, "3억", nil), (100000, 20, "10억", nil)]
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
        // 인터미션은 앞 미션을 모두 끝낸 뒤 (회복 루트에서 넘어와 '앱 시작 3단계'가 이미 끝난 경우에도)
        if s.inter { return goalSteps.filter { !$0.inter }.allSatisfy { gDone.contains($0.id) } }
        return i == 0 || gDone.contains(goalSteps[i - 1].id)
    }

    // 고른 구성으로 바꿀 비중 (플러스: 지금 비중 1위 종목·나머지에서 출발, 시작 전: 매달 나눠 넣기). 금액 대신 비율·%p로만 보인다
    // 나머지 종목은 '지수' 칸으로 옮겨 가는 것으로 본다 (시안의 QQQ 자리)
    struct Shift { var rows: [(String, String, String, Bool)] = []; var line = ""; var note = ""; var weights: [String: Double] = [:]; var sell = 0.0 }
    func shift(_ mx: GoalMix) -> Shift {
        let w = focusWeight, fn = focusName, rn = restName
        let restIndex = restRows.allSatisfy { $0.sym.sector == "지수" }
        let label = ["T": fn, "I": restIndex || w >= 1 ? "지수" : "\(rn)→지수", "G": "성장 지속", "B": "버팀목", "C": mx.id == "steady" ? "채권·현금" : "현금"]
        let ind = focusIndustry, hot = ind.map { Basket.of(pe: $0.pe, pe10: $0.pe10, growth: $0.growth) == "H" } ?? false
        if mx.own {
            let sec = ind.map { $0.ko + (hot ? "·과열" : "") } ?? ""
            var rs: [(String, String, String, Bool)] = [(fn, AppModel.pct(w), "그대로", true)]
            if !restRows.isEmpty { rs.append((rn, AppModel.pct(1 - w), "그대로", true)) }
            return Shift(rows: rs,
                         line: "\(fn) \(sec.isEmpty ? "" : sec + " ")\(AppModel.pct(w))" + (restRows.isEmpty ? "" : " · \(rn) \(AppModel.pct(1 - w))"),
                         note: (hot ? "\(fn) 업종(\(ind?.ko ?? ""))은 지금 과열이에요. " : "") + "다른 구성 카드에서 바꿀 비중을 볼 수 있어요.", weights: ["T": w, "I": 1 - w])
        }
        let keys = ["T", "I", "G", "B", "C"]
        if route == .novice {
            return Shift(rows: keys.compactMap { k in mx.comp[k].map { (label[k]!, "적립의", AppModel.pct($0), true) } },
                         line: mx.basket.map { b in "\(b.name): " + b.industries.prefix(3).map(\.ko).joined(separator: " · ") + " 외 \(b.industries.count - 3)개" } ?? "",
                         weights: mx.comp)
        }
        // 한 종목은 세 묶음에 들지 않아 10%까지만 둔다 (시안 T_CAP)
        let tT = min(w, 0.10)
        var tgt = mx.comp.mapValues { $0 * (1 - tT) }; tgt["T"] = tT
        let now = ["T": w, "I": 1 - w], tot = goalStart
        let used = keys.filter { (now[$0] ?? 0) > 0 || (tgt[$0] ?? 0) > 0 }
        let rows = used.map { k -> (String, String, String, Bool) in
            let a = tgt[k] ?? 0, b = now[k] ?? 0
            return (label[k]!, "\(Int((b * 100).rounded()))→\(AppModel.pct(a))", Mission3ResultView.pp(a - b), a >= b)
        }
        let short = ["T": fn, "I": label["I"]!, "G": "성장", "B": "버팀목", "C": "현금"]
        let line = used.map { k in short[k]! + " " + ((now[k] ?? 0) > 0 ? "\(Int(((now[k] ?? 0) * 100).rounded()))→\(AppModel.pct(tgt[k] ?? 0))" : "+" + AppModel.pct(tgt[k] ?? 0)) }.joined(separator: " · ")
        var note = hot ? "이 구성은 과열 업종 한 종목을 10%까지로 잡아요." : "이 구성은 한 종목을 10%까지로 잡아요."
        if tT < w && gM > 0 && tot > 0 {
            let mo = ceil((w * tot / tT - tot) / gM)
            note += " 적립만으로 비중이 맞춰지려면 약 \(Self.eta(mo).replacingOccurrences(of: " 뒤", with: "")) 걸려요. 매도로 맞추면 이익에 세금이 붙어요 (세금 화면)."
        }
        return Shift(rows: rows, line: line, note: note, weights: tgt, sell: max(0, w - tT))
    }

    // 3개월 인터미션
    static let goalMonthSteps: [(task: String, whereText: String, reward: String)] = [
        ("앱에서 이번 달 적립 기록하기", "앱", "세리"), ("PC에서 증권사 거래내역 파일 올리기", "PC 웹", "시오"), ("앱에서 배당·세금 요약 보기", "앱", "모두"),
    ]
    /// 체크인: 시작한 날부터 30일마다 한 달씩 (서버 시각, Me.swift monthGate). 회복 루트 주차와 같은 방식
    func confirmGoalMonth() {
        guard let c = gWeekCur, gWeeks.count < 3, monthGate == .open else { return }
        gWeeks.append(c); gWeekCur = nil
    }
    var goalSaved: Double { gA + (gWeeks + (gWeekCur.map { [$0] } ?? [])).reduce(0) { $0 + gM * $1.factor } }

    func switchRoute(_ r: Route) {
        route = r; boardPath = []; analysisPath = NavigationPath()
        goalStartAt = nil; gInterStartAt = nil; fixStarts()     // 목표 루트 4주는 루트를 고른 날부터 (내 캐릭터 1차 조건)
        switch r {
        case .recover: break
        case .plus:
            // 기본 목표: 3년 안에 닿을 확률 50% 근처 (매달 넣는 돈은 회복 루트에서 고른 값, 없으면 30만원)
            gMix = "mine"
            // 사이트와 동기화 중이고 사이트에 정한 목표가 아직 남아 있으면 그 목표를 그대로 쓴다
            if Sync.shared.isOn, gTo != nil, goalToDate > Date() { break }
            gM = recM > 0 ? Double(recM) : 30; gY = 3; gK = autoGoal(years: 3); gFrom = Day.today
        case .novice: gA = 0; gK = 1000; gY = 3; gM = 30; gMix = "index"; gFrom = Day.today
        }
        monthly = r == .recover ? 0 : gM     // 3년 전망의 '만약에 매달' 기본값 = 적립액

    }

    /// 본전을 넘은 회복 루트 → 목표 루트(플러스). 종목·위젯·만난 친구는 그대로 두고, 이미 한 미션(종목 넣기·앱 시작 3단계)은 끝낸 것으로
    func graduateToGoal() {
        let appDone = done.contains(5)
        switchRoute(.plus)
        gDone = ["hold"]
        if appDone { gDone.insert("link"); fixStarts() }
        boardPath = [.g1]
    }

    /// 목표에 닿았는지 (목표를 정한 뒤)
    var goalReached: Bool { isGoal && gDone.contains("goal") && gK > 0 && trackValue / 1e4 >= gK }
    /// 다음 목표 제안: 지금 금액에서 3년, 확률 50% 근처. 미션 진행은 그대로 둔다
    func suggestNextGoal() {
        gFrom = Day.today; gY = 3; gTo = nil; gK = autoGoal(years: 3, from: trackValue / 1e4)
    }
}