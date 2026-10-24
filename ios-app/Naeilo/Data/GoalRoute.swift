import SwiftUI

// 목표 달성 루트 (시안 목표·플러스): 목표 정하기 → 구성 고르기 → (플러스) 비중 조정 세금 → 앱 시작 3단계 → 3개월 인터미션.
// 금액 단위는 만원. 확률은 전망과 같은 몬테카를로 경로(웹 model.js 와 같은 계산, 사이트 통계·설정·사건)로 센다.
// 구성 비교의 지수·업종 묶음은 대표 ETF 시세로 (AppModel.proxyKeys). 경로 계산이 끝나기 전에는 닫힌 식 값을 잠깐 보인다

enum Route: String, Codable { case recover, plus, novice }

enum GoalWeek: String, CaseIterable, Codable { case inPlan, less, more
    var label: String { switch self { case .inPlan: String(localized: "계획대로"); case .less: String(localized: "덜 넣음"); case .more: String(localized: "더 넣음") } }
    var factor: Double { switch self { case .inPlan: 1; case .less: 0.5; case .more: 1.5 } }
    var color: Color { switch self { case .inPlan: Theme.green; case .less: Theme.orange; case .more: Theme.blue } }
}

struct GoalMix: Identifiable, Hashable {
    let id: String
    let name: String
    let tag: String
    let desc: String
    let comp: [String: Double]     // I 지수 · G 성장 지속 · B 버팀목 · C 현금 (대표 ETF: AppModel.proxyKeys)
    let basket: Basket?
    var own = false
}

struct GoalSim {
    let mix: GoalMix
    let prob: Double               // 목표일(또는 그 햇수 뒤)에 목표 이상일 확률 (웹 '목표 확률'과 같은 셈)
    let etaMonths: Double          // 보통의 경우 처음 닿는 때 (개월, 경로 기간 안에 없으면 무한대)
    let med: [Double], lo: [Double], hi: [Double], principal: [Double]   // 해마다 (만원)
    let badYear: Double            // 한 해 손익 하위 5% (적립 없이)
    let capYears: Int              // 경로 기간 (햇수)
    var etaLo: Double = 0          // 처음 닿는 때 10% (개월, 10번 중 빠른 1번)
    var etaHi: Double = .infinity  // 처음 닿는 때 90% (개월, 경로 기간 안에 없으면 무한대)
    var etaText: String { AppModel.eta(etaMonths, cap: capYears) }
}

extension AppModel {
    static func wonK(_ m: Double) -> String {
        if en { return krwC(m < 0.5 ? 0 : m * 1e4) }
        if m < 0.5 { return String(localized: "0원") }
        if m >= 10000 { return ((m / 1000).rounded() / 10).formatted() + String(localized: "억") }
        return Int(m.rounded()).formatted() + String(localized: "만")
    }
    /// cap: 경로 기간 (햇수). 그 안에 닿지 않으면 "N년 넘게"
    static func eta(_ mo: Double, cap: Int = 40) -> String {
        guard mo.isFinite else { return String(localized: "\(String(describing: cap))년 넘게") }
        if mo <= 0 { return String(localized: "지금") }
        let m = Int(mo), y = m / 12, r = m % 12
        return (y > 0 ? String(localized: "\(String(describing: y))년 ") : "") + (r > 0 ? String(localized: "\(String(describing: r))개월 뒤") : String(localized: "뒤"))
    }

    var isGoal: Bool { route != .recover }
    /// 시작 전 루트에 내가 넣은 종목(샘플 아님)이 있으면 모아 둔 투자금 대신 그 평가액으로 본다
    var noviceOwn: Bool { route == .novice && !isSample && total > 0 }
    /// 내 종목이 없는 시작 전 (샘플 포함): 이때만 지수 구성으로 적립 예시를 그린다. 계산·버튼·그래프는 루트가 아니라 이 값으로 가른다 (루트는 기준선·시작 미션만)
    var bare: Bool { route == .novice && !noviceOwn }
    /// 출발 금액 (만원): 플러스는 지금 평가액, 시작 전은 모아 둔 투자금 (넣은 종목이 있으면 평가액)
    var goalStart: Double { isGoal && !bare ? total / 1e4 : gA }
    /// 1000칸·주간 예보에서 쓰는 지금 금액 (원)
    /// 분석 화면의 기준선 (원): 회복 = 들어간 돈(본전), 목표 = 목표 금액
    var keyValue: Double { isGoal ? gK * 1e4 : cost }
    var keyName: String { isGoal ? String(localized: "목표") : String(localized: "본전") }
    /// 시작 전: 모아 둔 돈 + 인터미션에 넣은 돈. 목표 루트는 웹 총자산처럼 현금도 더한다
    var trackValue: Double { bare ? goalSaved * 1e4 : total + cashKrw }

    // 목표 시작일·목표일: 사이트(goal.start_date·goal.date)와 같은 값을 쓴다 (기기 동기화로 양쪽이 맞춘다)
    var goalFromDate: Date { gFrom.flatMap(Day.date) ?? goalStartAt.map { Date(timeIntervalSince1970: $0) } ?? Date() }
    var goalToDate: Date { gTo.flatMap(Day.date) ?? Calendar.current.date(byAdding: .year, value: gY, to: goalFromDate)! }
    /// 목표 시작일부터 목표일까지 (년)
    var goalSpan: Double { max(1.0 / 12, goalToDate.timeIntervalSince(goalFromDate) / (365.25 * 86400)) }
    var goalToText: String { let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "yyyy.M.d"; return f.string(from: goalToDate) }

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
        .init(id: "index", name: String(localized: "지수 중심"), tag: String(localized: "단순하게"), desc: String(localized: "미국 지수 80 · 현금 20"), comp: ["I": 0.8, "C": 0.2], basket: nil),
        .init(id: "grow", name: String(localized: "성장 더하기"), tag: String(localized: "더 빨리, 더 흔들림"), desc: String(localized: "지수 60 · 성장 업종 30 · 현금 10"), comp: ["I": 0.6, "G": 0.3, "C": 0.1], basket: .G),
        .init(id: "steady", name: String(localized: "버팀목 더하기"), tag: String(localized: "덜 흔들림"), desc: String(localized: "지수 50 · 버팀목 업종 30 · 채권·현금 20"), comp: ["I": 0.5, "B": 0.3, "C": 0.2], basket: .B),
    ]
    var mineMix: GoalMix {
        .init(id: "mine", name: String(localized: "지금 내 구성"), tag: String(localized: "그대로"), desc: "\(focusName) \(Int((focusWeight * 100).rounded()))" + (restRows.isEmpty ? "" : " · \(restName) \(Int(((1 - focusWeight) * 100).rounded()))"),
              comp: [:], basket: nil, own: true)
    }
    /// 내 길의 '지금 구성': 시작 전 루트는 지수 중심 (neededMonthly 와 같다)
    var nowMix: GoalMix { bare ? Self.mixes[0] : mineMix }
    /// 처음 닿는 때를 셀 긴 기간 (햇수): 목표일 뒤에 닿는 경우도 보이게
    var etaYears: Int { max(10, gY * 2) }
    /// 목표 확률을 쉬운 말로
    static func verdict(_ p: Double) -> String { p >= 0.6 ? String(localized: "높음") : p >= 0.4 ? String(localized: "반반") : String(localized: "낮음") }   // 반반은 40~60% (30%를 반반이라 부르지 않게)
    /// 오늘부터 mo 개월 뒤를 연도 소수로 (2031.25 = 2031년 4월 무렵)
    static func yearAt(_ mo: Double) -> Double {
        let c = Calendar.current.dateComponents([.year, .month], from: Date())
        return Double(c.year ?? 2026) + (Double((c.month ?? 1) - 1) + mo) / 12
    }
    /// 오늘부터 mo 개월 뒤 "2031.4" (기간 안에 없으면 "N년 넘게")
    static func whenText(_ mo: Double, cap: Int) -> String {
        guard mo.isFinite else { return String(localized: "\(String(describing: cap))년 넘게") }
        guard mo > 0 else { return String(localized: "지금") }
        let d = Calendar.current.date(byAdding: .month, value: Int(mo.rounded()), to: Date()) ?? Date()
        let c = Calendar.current.dateComponents([.year, .month], from: d)
        return "\(c.year ?? 0).\(c.month ?? 0)"
    }
    var goalMixes: [GoalMix] { bare ? Self.mixes : [mineMix] + Self.mixes }
    var selectedMix: GoalMix { goalMixes.first { $0.id == gMix } ?? goalMixes[0] }

    /// 구성의 종목 (사이트 티커·통화·비중). 내 구성 = 지금 종목. 시작 전은 묶음만, 플러스는 비중 1위 종목 10%까지 + 묶음 (shift 와 같은 비중)
    func mixParts(_ mx: GoalMix) -> [Part] {
        if mx.own { return mineParts }
        var out: [Part] = []
        var rest = 1.0
        if !bare, let f = focusRow {
            let tT = min(focusWeight, 0.10)
            out.append((key: SiteStats.mcKey(f.id), ccy: f.sym.currency == .usd ? "USD" : "KRW", w: tT))
            rest = 1 - tT
        }
        for k in ["I", "G", "B", "C"] { if let x = mx.comp[k] { out.append((key: proxy(k), ccy: "USD", w: x * rest)) } }
        return out
    }
    /// 지금 목표의 경로 끝 = 목표일 (전망과 같은 기간이라 내 구성은 전망 경로를 그대로 쓴다)
    var goalHorizon: String { Day.str(goalToDate) }
    /// 다른 햇수(목표 고르기 카드·자동 고르기·도착 시점)는 10년(그보다 길면 그 햇수) 경로 하나에서 센다
    func longHorizon(_ y: Int) -> String { MC.addMonths(Day.today, 12 * max(10, y)) }

    /// k·years·start 를 주면 지금 목표 대신 그 값으로 (목표 자동 고르기에서 씀, cache: false 면 캐시에 넣지 않는다).
    /// years 를 주면 오늘부터 그 햇수, 안 주면 목표일까지
    /// parts 를 주면 구성 대신 그 종목·비중으로 (내 길 비중 바꿔 비교)
    func simulate(_ mx: GoalMix, monthly: Double? = nil, k: Double? = nil, years: Int? = nil, start: Double? = nil, cache: Bool = true, parts over: [Part]? = nil) -> GoalSim {
        let mo = monthly ?? gM, yrs = years ?? gY, K = k ?? gK, A = start ?? goalStart
        let parts = over ?? mixParts(mx)
        guard let ps = paths(parts, horizon: years == nil ? goalHorizon : longHorizon(yrs)) else {
            return closedSim(mx, parts: parts, monthly: mo, k: K, years: yrs, start: A, cache: cache)
        }
        let ck = "x|\(ps.key)|\(mo)|\(yrs)|\(K)|\(A)|\(years == nil)"
        if cache, let c = simCache[ck] { return GoalSim(mix: mx, prob: c.prob, etaMonths: c.etaMonths, med: c.med, lo: c.lo, hi: c.hi, principal: c.principal, badYear: c.badYear, capYears: c.capYears, etaLo: c.etaLo, etaHi: c.etaHi) }
        let V = A * 1e4, Mo = mo * 1e4, Kw = K * 1e4, M = ps.M
        let v = ps.values(V, monthly: Mo)
        // 그 햇수 뒤 칸: 그 달 적립은 빼고 센다 (목표일 당일에는 넣지 않는다, 웹과 같게)
        let kEnd = years == nil ? M - 1 : ps.k(months: 12 * yrs)
        let prob = ps.share(v, k: kEnd, minus: kEnd > 0 && kEnd < M - 1 ? Mo : 0, atLeast: Kw)
        let hits = V >= Kw ? [] : ps.firstHits(v, upTo: M - 1) { _ in Kw }
        let eta = V >= Kw ? 0 : ps.medianMonths(hits)
        let etaLo = V >= Kw ? 0 : ps.quantileMonths(hits, 0.1), etaHi = V >= Kw ? 0 : ps.quantileMonths(hits, 0.9)
        let ks = (0...yrs).map { y in y == 0 ? 0 : min(kEnd, ps.k(months: 12 * y)) }
        let q = { (f: Double) in ks.map { k in k == 0 ? A : ps.quantile(v, k: k, f) / 1e4 } }
        let b = ps.bands(max(V, 1e6)), k12 = ps.k(months: 12)
        let r = GoalSim(mix: mx, prob: prob, etaMonths: eta, med: q(0.5), lo: q(0.05), hi: q(0.95),
                        principal: (0...yrs).map { A + mo * 12 * Double($0) }, badYear: b[0][k12] / max(V, 1e6) - 1,
                        capYears: Int((ps.tM.last ?? 12) / 12 + 0.5), etaLo: etaLo, etaHi: etaHi)
        if cache {
            if simCache.count > 64 { simCache.removeAll() }
            simCache[ck] = r
        }
        return r
    }

    /// 경로 계산이 끝나기 전 잠깐: 닫힌 식 μ·σ(사이트 통계)로 500번 월 단위
    private func closedSim(_ mx: GoalMix, parts: [Part], monthly mo: Double, k K: Double, years gY: Int, start A: Double, cache: Bool) -> GoalSim {
        let c = closedParams(parts)
        let ck = "c|\(c.mu)|\(c.sigma)|\(mo)|\(gY)|\(K)|\(A)"
        if cache, let x = simCache[ck] { return GoalSim(mix: mx, prob: x.prob, etaMonths: x.etaMonths, med: x.med, lo: x.lo, hi: x.hi, principal: x.principal, badYear: x.badYear, capYears: x.capYears, etaLo: x.etaLo, etaHi: x.etaHi) }
        var seed = 12345
        func rnd() -> Double { seed = (seed &* 1103515245 &+ 12345) % 2147483648; return (Double(seed) + 0.5) / 2147483648 }
        let NP = 500, H = min(40, gY * 2 + 5) * 12
        let mm = (c.mu - c.sigma * c.sigma / 2) / 12, ms = c.sigma / sqrt(12)
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
        func q(_ a: [Double], _ f: Double) -> Double { let s = a.sorted(); return s.isEmpty ? A : s[min(s.count - 1, Int(f * Double(s.count)))] }
        let r = GoalSim(mix: mx, prob: Double(byYear[gY].filter { $0 >= K }.count) / Double(max(1, byYear[gY].count)), etaMonths: q(hits, 0.5),
                        med: byYear.map { q($0, 0.5) }, lo: byYear.map { q($0, 0.05) }, hi: byYear.map { q($0, 0.95) },
                        principal: (0...gY).map { A + mo * 12 * Double($0) },
                        badYear: exp(c.mu - c.sigma * c.sigma / 2 - 1.645 * c.sigma) - 1, capYears: H / 12,
                        etaLo: q(hits, 0.1), etaHi: q(hits, 0.9))
        if cache {
            if simCache.count > 64 { simCache.removeAll() }
            simCache[ck] = r
        }
        return r
    }

    /// 목표에 맞추려면 매달 넣을 돈 (만원): 목표일(years 를 주면 그 햇수 뒤)에 목표 이상인 경우가 절반이 되는 적립 (웹 req50 과 같은 셈)
    func neededMonthly(k: Double? = nil, years: Int? = nil) -> Double {
        let mx = bare ? Self.mixes[0] : mineMix, K = k ?? gK, yrs = years ?? gY
        let parts = mixParts(mx)
        if let ps = paths(parts, horizon: years == nil ? goalHorizon : longHorizon(yrs)) {
            return ps.req50(goalStart * 1e4, goal: K * 1e4, kEnd: years == nil ? ps.M - 1 : ps.k(months: 12 * yrs)) / 1e4
        }
        let c = closedParams(parts)
        let gmm = exp((c.mu - c.sigma * c.sigma / 2) / 12), fv = pow(gmm, Double(yrs * 12))
        return max(0, (K - goalStart * fv) * (gmm - 1) / (fv - 1))
    }
    /// 지금 start(만원)에서 매달 monthly(만원)씩 넣을 때 보통의 경우 목표에 처음 닿는 개월
    func monthsTo(from start: Double, monthly: Double) -> Double {
        if start >= gK { return 0 }
        let parts = mixParts(bare ? Self.mixes[0] : mineMix)
        if let ps = paths(parts, horizon: longHorizon(gY)) {
            let v = ps.values(start * 1e4, monthly: monthly * 1e4), K = gK * 1e4
            return ps.medianMonths(ps.firstHits(v, upTo: ps.M - 1) { _ in K })
        }
        let c = closedParams(parts), gmm = exp((c.mu - c.sigma * c.sigma / 2) / 12)
        var v = start
        for k in 1...480 { v = v * gmm + monthly; if v >= gK { return Double(k) } }
        return .infinity
    }

    /// 페이스 판정: 목표일까지 남은 개월과 지금 금액·적립으로 처음 닿는 개월(보통의 경우)을 비교한다. ±1개월은 예정대로
    enum GoalPace: Equatable { case onTrack, ahead(Int), behind(Int), unreachable }
    func goalPace() -> GoalPace {
        let left = goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12
        let eta = monthsTo(from: trackValue / 1e4, monthly: gM)
        guard eta.isFinite else { return .unreachable }
        let d = left - eta
        if abs(d) <= 1 { return .onTrack }
        return d > 0 ? .ahead(Int(d.rounded())) : .behind(Int((-d).rounded()))
    }
    /// 홈 카드 페이스 문장
    static func paceText(_ p: GoalPace) -> String {
        switch p {
        case .onTrack: return String(localized: "계획대로")
        case .ahead(let n): return n > 6 ? String(localized: "많이 빨라요") : String(localized: "조금 빨라요")
        case .behind(let n): return n > 6 ? String(localized: "많이 늦어요") : String(localized: "조금 늦어요")
        case .unreachable: return String(localized: "지금 속도론 어려워요")
        }
    }

    /// 보기 좋은 금액 (만원): 앞 두 자리만 남긴다 (5억 4천 → 5.4억, 3,460만 → 3,500만)
    static func round2(_ x: Double) -> Double {
        guard x >= 100 else { return 100 }
        let u = pow(10, floor(log10(x)) - 1)
        return (x / u).rounded() * u
    }
    /// 목표 금액 자동 고르기: 지금 금액에서 매달 monthly(만원)씩 넣을 때 years 뒤에 목표 이상일 확률이 50% 근처인 금액 (만원)
    /// = 그 햇수 뒤 평가액의 가운데 값 (전망과 같은 경로). 경로 계산 전에는 닫힌 식으로 확률 50% 를 찾는다
    func autoGoal(years: Int, monthly: Double? = nil, from start: Double? = nil) -> Double { autoGoalX(years: years, monthly: monthly, from: start).0 }
    func autoGoalX(years: Int, monthly: Double? = nil, from start: Double? = nil) -> (Double, Bool) {
        let A = start ?? goalStart, mo = max(0, monthly ?? gM), mx = bare ? Self.mixes[0] : mineMix
        guard A + mo > 0 else { return (1000, true) }
        let parts = mixParts(mx)
        if let ps = paths(parts, horizon: longHorizon(years)) {
            let ck = "x|\(ps.key)|\(mo)|\(years)|\(A)"
            if let c = autoCache[ck] { return (c, true) }
            let v = ps.values(A * 1e4, monthly: mo * 1e4), kEnd = ps.k(months: 12 * years)
            let K = ps.quantile(v, k: kEnd, 0.5, minus: kEnd > 0 && kEnd < ps.M - 1 ? mo * 1e4 : 0) / 1e4
            let r = max(Self.round2(A + 1), Self.round2(K))
            if autoCache.count > 64 { autoCache.removeAll() }
            autoCache[ck] = r
            return (r, true)
        }
        let c = closedParams(parts), ck = "c|\(c.mu)|\(c.sigma)|\(mo)|\(years)|\(A)"
        if let x = autoCache[ck] { return (x, false) }
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
        return (r, false)
    }
    /// 목표를 자동으로 정한다. 경로가 아직이면 닫힌 식 값으로 두고, 경로가 나오면 (그사이 목표를 고치지 않았으면) 다시 고른다
    func setAutoGoal(years: Int, monthly: Double? = nil, from start: Double? = nil) {
        let (v, exact) = autoGoalX(years: years, monthly: monthly, from: start)
        gK = v
        autoPending = exact ? nil : (years, monthly, start, v)
        if !exact { PathRuns.shared.onRun = { [weak self] in self?.refreshAutoGoal() } }
    }
    func refreshAutoGoal() {
        guard let a = autoPending else { return }
        guard gK == a.value else { autoPending = nil; return }
        let (v, exact) = autoGoalX(years: a.years, monthly: a.monthly, from: a.from)
        if exact { gK = v; autoPending = nil }
    }

    struct GoalPreset: Identifiable { var id: String { name }; let k: Double; let y: Int; let name: String; let mean: String; let need: String }
    var goalPresets: [GoalPreset] {
        let A = goalStart
        func meaning(_ k: Double) -> String {
            k <= 1000 ? String(localized: "첫 목표 · 적립 습관") : k <= 10000 ? String(localized: "연 7%면 해마다 +\(String(describing: Self.wonK(k * 0.07).replacingOccurrences(of: "원", with: "")))") : String(localized: "연 4% 꺼내면 월 \(String(describing: Self.wonK(k * 0.04 / 12).replacingOccurrences(of: "원", with: "")))")
        }
        // 플러스: 기간마다 '매달 넣는 돈으로 닿을 확률 50% 근처' 금액 (배수 프리셋 대신, 2026-10-11 결정)
        if !bare {
            return [1, 3, 5, 10].map { y in
                let k = autoGoal(years: y)
                return GoalPreset(k: k, y: y, name: Self.wonK(k).replacingOccurrences(of: "원", with: ""),
                                  mean: "",   // '적립 없이 확률 50%' 는 카드 위에 한 번만
                                  need: A > 0 ? "×\(String(format: "%.1f", k / A))" : "")
            }
        }
        let raw: [(Double, Int, String, Int?)] = [(1000, 3, String(localized: "첫 1,000만"), nil), (10000, 5, String(localized: "1억"), nil), (30000, 10, String(localized: "3억"), nil), (100000, 20, String(localized: "10억"), nil)]
        return raw.map { k, y, name, x in
            let nm = neededMonthly(k: k, years: y), cagr = A > 0 ? pow(k / A, 1 / Double(y)) - 1 : 0
            return GoalPreset(k: k, y: y, name: x.map { String(localized: "\(String(describing: name)) (\(String(describing: $0))배)") } ?? name,
                              mean: x != nil ? String(localized: "적립 없이 연 +\(String(describing: Int((cagr * 100).rounded())))%") : meaning(k),
                              need: nm > 0 ? String(localized: "월 ") + Self.wonK(nm).replacingOccurrences(of: "원", with: "") : String(localized: "이미 충분"))
        }
    }

    // 미션 목록 (시작 전 / 플러스)
    struct GoalStep: Identifiable { let id: String; let num: String; let title: String; let reward: String; let to: MissionRoute; let inter: Bool }
    var goalSteps: [GoalStep] {
        let list: [(String, String, String, MissionRoute, Bool)] = route == .novice ? [
            ("goal", String(localized: "목표 정하기"), String(localized: "목표까지 경로"), .g1, false), ("mix", String(localized: "투자 구성 고르기"), String(localized: "구성 비교"), .g3, false),
            ("link", String(localized: "앱 시작 3단계"), String(localized: "위젯 전부"), .nx, false), ("inter", String(localized: "처음 석 달 체크인"), String(localized: "캐릭터 위젯 3개"), .gi, true),
        ] : [
            ("hold", String(localized: "종목과 산 가격 넣기"), String(localized: "평가액·수익률"), .m1, false), ("goal", String(localized: "목표 정하기"), String(localized: "목표까지 경로"), .g1, false),
            ("mix", String(localized: "다른 방식과 비교"), String(localized: "구성 비교"), .g3, false), ("tax", String(localized: "세금 알아보기"), String(localized: "절세 순서"), .gt, false),
            ("link", String(localized: "앱 시작 3단계"), String(localized: "위젯 전부"), .nx, false), ("inter", String(localized: "처음 석 달 체크인"), String(localized: "캐릭터 위젯 3개"), .gi, true),
        ]
        return list.enumerated().map { i, x in GoalStep(id: x.0, num: x.4 ? String(localized: "인터미션") : "\(i + 1)", title: x.1, reward: x.2, to: x.3, inter: x.4) }
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
        let label = ["T": fn, "I": restIndex || w >= 1 ? String(localized: "지수") : String(localized: "\(String(describing: rn))→지수"), "G": String(localized: "성장 지속"), "B": String(localized: "버팀목"), "C": mx.id == "steady" ? String(localized: "채권·현금") : String(localized: "현금")]
        let ind = focusIndustry, hot = ind.map { Basket.of(pe: $0.pe, pe10: $0.pe10, growth: $0.growth) == "H" } ?? false
        if mx.own {
            let sec = ind.map { $0.ko + (hot ? String(localized: "·과열") : "") } ?? ""
            var rs: [(String, String, String, Bool)] = [(fn, AppModel.pct(w), String(localized: "그대로"), true)]
            if !restRows.isEmpty { rs.append((rn, AppModel.pct(1 - w), String(localized: "그대로"), true)) }
            return Shift(rows: rs,
                         line: "\(fn) \(sec.isEmpty ? "" : sec + " ")\(AppModel.pct(w))" + (restRows.isEmpty ? "" : " · \(rn) \(AppModel.pct(1 - w))"),
                         note: (hot ? String(localized: "\(String(describing: fn)) 업종(\(String(describing: ind?.ko ?? ""))) 과열 · ") : "") + String(localized: "바꿀 비중은 다른 구성에서"), weights: ["T": w, "I": 1 - w])
        }
        let keys = ["T", "I", "G", "B", "C"]
        if bare {
            return Shift(rows: keys.compactMap { k in mx.comp[k].map { (label[k]!, String(localized: "적립의"), AppModel.pct($0), true) } },
                         line: mx.basket.map { b in "\(b.name): " + b.industries.prefix(3).map(\.ko).joined(separator: " · ") + String(localized: " 외 \(String(describing: b.industries.count - 3))개") } ?? "",
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
        let short = ["T": fn, "I": label["I"]!, "G": String(localized: "성장"), "B": String(localized: "버팀목"), "C": String(localized: "현금")]
        let line = used.map { k in short[k]! + " " + ((now[k] ?? 0) > 0 ? "\(Int(((now[k] ?? 0) * 100).rounded()))→\(AppModel.pct(tgt[k] ?? 0))" : "+" + AppModel.pct(tgt[k] ?? 0)) }.joined(separator: " · ")
        var note = hot ? String(localized: "과열 업종 한 종목 최대 10%") : String(localized: "한 종목 최대 10%")
        if tT < w && gM > 0 && tot > 0 {
            let mo = ceil((w * tot / tT - tot) / gM)
            note += String(localized: " · 적립만으로 약 \(String(describing: Self.eta(mo).replacingOccurrences(of: " 뒤", with: ""))) · 팔면 세금 (세금 화면)")
        }
        return Shift(rows: rows, line: line, note: note, weights: tgt, sell: max(0, w - tT))
    }

    // 3개월 인터미션
    static let goalMonthSteps: [(task: String, whereText: String, reward: String)] = [
        (String(localized: "앱에서 이번 달 적립 기록하기"), String(localized: "앱"), String(localized: "세리")), (String(localized: "앱에서 내 종목 수량·평균 단가 맞춰 보기"), String(localized: "앱"), String(localized: "시오")), (String(localized: "앱에서 배당·세금 요약 보기"), String(localized: "앱"), String(localized: "모두")),
    ]
    /// 체크인: 시작한 날부터 30일마다 한 달씩 (서버 시각, Me.swift monthGate). 회복 루트 주차와 같은 방식
    func confirmGoalMonth() {
        guard let c = gWeekCur, gWeeks.count < 3, monthGate == .open else { return }
        gWeeks.append(c); gWeekCur = nil
        if gWeeks.count == 3 { gDone.insert("inter") }
    }
    /// 지난 달 체크인은 다음 달이 열리기 전까지 바꿀 수 있다
    var monthEditable: Bool { !gWeeks.isEmpty && gWeeks.count < 3 && monthGate != .open }
    func editLastMonth(_ p: GoalWeek) { if monthEditable { gWeeks[gWeeks.count - 1] = p } }
    /// 넣은 종목이 있으면 그 평가액 (적립도 종목 수량으로 들어가니 따로 더하지 않는다)
    var goalSaved: Double { noviceOwn ? total / 1e4 : gA + (gWeeks + (gWeekCur.map { [$0] } ?? [])).reduce(0) { $0 + gM * $1.factor } }

    func switchRoute(_ r: Route) {
        route = r; boardPath = []; analysisPath = NavigationPath()
        goalStartAt = nil; gInterStartAt = nil; fixStarts()     // 목표 루트 4주는 루트를 고른 날부터 (내 캐릭터 1차 조건)
        switch r {
        case .recover: break
        case .plus:
            // 기본 목표: 3년 안에 닿을 확률 50% 근처 (매달 넣는 돈은 회복 루트에서 고른 값, 없으면 0)
            gMix = "mine"
            // 사이트와 동기화 중이고 사이트에 정한 목표가 아직 남아 있으면 그 목표를 그대로 쓴다
            if Sync.shared.isOn, gTo != nil, goalToDate > Date() { break }
            gM = recM > 0 ? Double(recM) : 0; gY = 3; gFrom = Day.today; setAutoGoal(years: 3)
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
        gFrom = Day.today; gY = 3; gTo = nil; setAutoGoal(years: 3, from: trackValue / 1e4)
    }
}