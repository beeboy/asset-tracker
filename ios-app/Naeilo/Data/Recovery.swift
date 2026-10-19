import SwiftUI

// 회복 루트 계산 (시안 mk·chart 와 같은 식). 모든 설명은 이 계산값으로 만든다.

enum MissionRoute: Hashable { case m1, m1r, m2, m2r, m3, m3r, m4, m4r, nx, gp1r, g1, g1r, g3, g3r, gt, gi }

enum Basket: String, CaseIterable {
    case G, V, B, C
    var name: String { switch self { case .G: "성장 지속"; case .V: "저평가 회복"; case .B: "버팀목"; case .C: "현금" } }
    var rule: String {
        switch self {
        case .G: "PER이 평균보다 15%↑, 이익 성장으로 설명됨"
        case .V: "PER이 평균보다 10%↓, 이익 성장 5%↑"
        case .B: "PER이 평균 ±15% 안, 이익 성장 3%↑"
        case .C: ""
        }
    }
    var color: Color { switch self { case .G: Theme.green; case .V: Theme.orange; case .B: Theme.blue; case .C: Color(hex: 0xBFC6CD, dark: 0x56616C) } }

    // 분류 규칙 (tools/classify_sector_pe.py 와 같음). H = 과열, X = 세 묶음 밖
    static func of(pe: Double, pe10: Double, growth: Double) -> String {
        let x = pe / pe10
        if x >= 1.15 { return growth >= 10 && pe / growth <= 2.5 ? "G" : "H" }
        if x <= 0.90 && growth >= 5 { return "V" }
        if x > 0.85 && x < 1.15 && growth >= 3 { return "B" }
        return "X"
    }
    var industries: [(key: String, ko: String, pe: Double, pe10: Double, growth: Double, count: Int)] {
        Industries.all.filter { $0.count >= 10 && Basket.of(pe: $0.pe, pe10: $0.pe10, growth: $0.growth) == rawValue }
            .sorted { $0.count > $1.count }
    }
}

struct Plan: Identifiable {
    let id: String
    let name: String
    let tag: String
    let wt: Double          // 계획에서 몰린 종목(비중 1위) 비중
    let sigma: Double       // 닫힌 식 (경로 계산이 끝나기 전 잠깐)
    let g: Double           // 보통의 연 성장 (로그, 닫힌 식)
    let mix: [Basket: Double]
    let mixText: String
    let V: Double
    var focus = "몰린 종목"     // 몰린 종목 이름 (설명용)
    /// 전망과 같은 몬테카를로 경로 (웹 본전 카드와 같은 셈). 계산이 끝나기 전에는 nil → 닫힌 식
    var paths: PathSet? = nil

    /// T년 안에 월말 평가액이 K 이상이 되는 달이 올 확률 (웹 본전 카드 breakeven 과 같은 셈)
    func prob(_ K: Double, _ T: Double) -> Double {
        guard V > 0, K > 0 else { return 0 }
        if let ps = paths {
            let v = ps.values(V)
            return ps.touch(v, upTo: ps.k(months: Int((T * 12).rounded()))) { _ in K }
        }
        return AppModel.normCDF((log(V / K) + g * T) / (sigma * sqrt(T)))
    }
    /// T년 뒤 손익 (k = -1 나쁜 경우 5%, 0 보통, 1 좋은 경우 95%), 들어간 돈 C 대비
    func outcome(_ C: Double, _ T: Double, _ k: Double) -> Double {
        guard C > 0 else { return 0 }
        if let ps = paths {
            let b = ps.bands(V), kk = ps.k(months: Int((T * 12).rounded()))
            return b[k < 0 ? 0 : k > 0 ? 4 : 2][kk] / C - 1
        }
        return V * exp(g * T + k * 1.645 * sigma * sqrt(T)) / C - 1
    }
    /// 그래프: t개월 뒤 평가액 (z = -1 나쁜 경우, 0 보통, 1 좋은 경우). 경로면 월 경계 사이를 곧게 잇는다
    func level(_ t: Double, _ z: Double) -> Double {
        guard let ps = paths else { return V * exp(g * t / 12 + z * 1.645 * sigma * sqrt(t / 12)) }
        let b = ps.bands(V)[z < 0 ? 0 : z > 0 ? 4 : 2], tm = ps.tM
        var k = 0; while k + 1 < tm.count && tm[k + 1] < t { k += 1 }
        guard k + 1 < tm.count else { return b[k] }
        let f = max(0, min(1, (t - tm[k]) / max(1e-9, tm[k + 1] - tm[k])))
        return b[k] * (1 - f) + b[k + 1] * f
    }
    /// 1년 흔들림(로그 성장의 표준편차) · 보통의 연 성장(로그 중앙값) · 평균 연 수익
    var year: (sigma: Double, g: Double, mean: Double) {
        guard let ps = paths, V > 0 else { return (sigma, g, exp(g + sigma * sigma / 2) - 1) }
        let v = ps.values(V), k = ps.k(months: 12), P = ps.P
        let r = (0..<P).map { log(max(1e-12, v[k * P + $0] / V)) }.sorted()
        let m = r.reduce(0, +) / Double(P), sd = (r.reduce(0) { $0 + ($1 - m) * ($1 - m) } / Double(max(1, P - 1))).squareRoot()
        let mean = (0..<P).reduce(0) { $0 + v[k * P + $1] / V } / Double(P) - 1
        return (sd, MC.quantileSorted(r, 0.5), mean)
    }
    var desc: String {
        id == "keep" ? "지금 그대로 (\(focus) \(AppModel.pct(wt)))" : "\(focus) \(AppModel.pct(wt))로 · 줄인 돈은 \(mixText)"
    }
}

extension AppModel {
    /// 회복 루트는 '한 종목에 몰린 비중'을 다룬다: 몰린 종목 = 내 평가액이 가장 큰 종목, 나머지는 한 묶음
    /// (시안은 DRNK·QQQ 두 종목이었고, 내 종목이 그 둘이면 결과는 시안과 같다)
    var focusRow: Row? { rows.max { $0.value < $1.value } }
    var restRows: [Row] { rows.filter { $0.id != focusRow?.id } }
    var focusName: String { focusRow?.sym.short ?? "몰린 종목" }
    /// 나머지 이름: 하나면 그 종목, 여럿이면 "나머지 n종목"
    var restName: String {
        let r = restRows
        return r.count == 1 ? r[0].sym.short : r.isEmpty ? "나머지" : "나머지 \(r.count)종목"
    }
    /// 나머지 묶음 수익률 (들어간 돈 대비)
    var restRet: Double {
        let c = restRows.reduce(0) { $0 + $1.cost }
        return c > 0 ? restRows.reduce(0) { $0 + $1.value } / c - 1 : 0
    }
    /// 몰린 종목의 업종 자료 (업종 이름이 다모다란 업종과 같을 때만)
    var focusIndustry: (key: String, ko: String, pe: Double, pe10: Double, growth: Double, count: Int)? {
        guard let s = focusRow?.sym, !s.sector.isEmpty else { return nil }
        return Industries.all.first { $0.ko == s.sector }
    }
    var halfway: Double { total < cost ? total + (cost - total) / 2 : cost }

    // MARK: 구성 경로 (전망과 같은 몬테카를로)
    typealias Part = (key: String, ccy: String, w: Double)
    /// 묶음·지수의 대표 종목 (사이트가 매일 시세를 받는 ETF). 사이트 통계에 아직 없으면 미국 전체 지수로
    static let proxyKeys: [String: [String]] = ["I": ["SPY"], "G": ["QQQ", "SPY"], "V": ["VTV", "SPY"], "B": ["USMV", "SPY"], "C": ["SGOV"]]
    func proxy(_ comp: String) -> String {
        let ks = Self.proxyKeys[comp] ?? ["SPY"], st = Market.shared.stats
        return ks.first { st?.tickers[$0] != nil } ?? ks.last!
    }
    /// 지금 내 종목 (사이트 티커·통화·평가액 비중)
    var mineParts: [Part] {
        let t = max(1, total)
        var out: [Part] = []
        for r in rows { out.append((key: SiteStats.mcKey(r.id), ccy: r.sym.currency == .usd ? "USD" : "KRW", w: r.value / t)) }
        return out
    }
    /// 같은 종목은 합친다 (같은 요인 둘은 상관 1 이라 모형이 흔들린다)
    static func merged(_ parts: [Part]) -> [Part] {
        var out: [Part] = []
        for p in parts where p.w > 1e-9 {
            if let i = out.firstIndex(where: { $0.key == p.key }) { out[i].w += p.w } else { out.append(p) }
        }
        return out
    }
    /// 전망과 같은 사이트 통계·설정·사건. 내 전망(forecastJob)은 동기화로 받은 웹 설정의 경로 수 그대로 돈다 (웹과 같은 숫자.
    /// 웹 선택지의 최대 10,000개). 다른 구성 비교(계획·묶음)는 앱에만 있는 화면이라 사이트 기본(3,000개)까지만
    var mcInputs: (st: MC.Stats, settings: MC.Settings, events: [MC.Event], sig: String)? { mcInputs(full: false) }
    func mcInputs(full: Bool) -> (st: MC.Stats, settings: MC.Settings, events: [MC.Event], sig: String)? {
        guard let st = Market.shared.stats else { return nil }
        let site = Sync.shared.siteForecast
        var settings = (st.model ?? MC.Settings()).merged(site.model)
        let cap = full ? Self.webPathMax : mcPathCap(st)
        if settings.nPaths > cap { settings.n_paths = Double(cap) }
        let events = site.events ?? st.events ?? []
        let enc = JSONEncoder(); enc.outputFormatting = .sortedKeys
        let evSig = ForecastEngine.fnv(String(data: (try? enc.encode(events)) ?? Data(), encoding: .utf8) ?? "")
        return (st, settings, events, [st.date, evSig, "\(settings.nPaths)|\(settings.seedU32)|\(settings.dof)|\(settings.earnings_adjust ?? true)"].joined(separator: "|"))
    }
    /// 웹 '경로' 선택지의 최대
    static let webPathMax = 10_000
    /// 다른 구성 비교가 도는 경로 수 상한 = 사이트 기본 설정의 경로 수 (없으면 3,000)
    func mcPathCap(_ st: MC.Stats?) -> Int { max(500, (st?.model ?? MC.Settings()).nPaths) }
    /// 종목별 연 기대수익: 전망 화면과 같은 관점(현재 정세·과거 추세·내 관점)
    func lensMu(_ fs: [MC.Factor], _ A: Int) -> [Double] {
        fs.prefix(A).map { lens == .base ? $0.base : lens == .smooth ? $0.smooth : MC.blendMu($0.base, $0.smooth, trust / 100) }
    }
    /// 구성 parts 의 오늘부터 H(yyyy-MM-dd)까지 경로. 지금 내 구성이고 기간이 전망과 같으면 전망 경로를 그대로 쓴다.
    /// 아직 계산 중이면 nil (끝나면 화면이 다시 그려진다)
    func paths(_ parts0: [Part], horizon H: String) -> PathSet? {
        let parts = Self.merged(parts0), today = Day.today
        guard !parts.isEmpty, H > today, let inp = mcInputs else { return nil }
        let lk = "\(lens)|\(trust)"
        let mp = Self.merged(mineParts)
        let mine = parts.count == mp.count && zip(parts, mp).allSatisfy { $0.key == $1.key && abs($0.w - $1.w) < 1e-9 }
        if mine, let job = forecastJob, job.horizon == H {
            let eng = ForecastEngine.shared
            eng.ensure(job)
            guard let run = eng.run, run.key == job.key else { return nil }
            let pk = "fc|\(job.key)|\(lk)"
            if let ps = pathSetCache[pk] { return ps }
            let ps = PathSet(sim: run.ev, mu: lensMu(run.factors, run.ev.A), base: run.ev.v0, key: pk)
            pathSetCache = pathSetCache.filter { $0.key.hasPrefix("fc|\(job.key)") || !$0.key.hasPrefix("fc|") }
            pathSetCache[pk] = ps
            return ps
        }
        let key = [today, H, inp.sig, parts.map { "\($0.key):\($0.ccy):\(String(format: "%.4f", $0.w))" }.joined(separator: ",")].joined(separator: "|")
        let hs = parts.map { MC.Holding(key: $0.key, ccy: $0.ccy, valueKrw: $0.w * 1e6, price0: 1) }
        let group = [today, H, inp.sig, parts.map { "\($0.key):\($0.ccy)" }.joined(separator: ",")].joined(separator: "|")
        let job = ForecastEngine.Job(key: key, holdings: hs, stats: ExtraStats.shared.augment(inp.st, keys: parts.map(\.key)), settings: inp.settings, events: inp.events, start: today, horizon: H,
                                     goal: .infinity, monthly: 0, group: group, w: parts.map { $0.w })
        guard let run = PathRuns.shared.get(job) else { return nil }
        let pk = "pr|\(run.key)|\(lk)"
        if let ps = pathSetCache[pk] { return ps }
        let ps = PathSet(sim: run.sim, mu: lensMu(run.factors, run.sim.A), base: run.sim.v0, key: pk)
        if pathSetCache.count > 24 { pathSetCache = [:] }
        pathSetCache[pk] = ps
        return ps
    }
    /// 닫힌 식 값 (경로 계산이 끝나기 전 잠깐): 사이트 통계의 μ(지금 관점)·σ·상관으로 구성의 연 기대·흔들림. 달러 몫은 환율 흔들림도
    func closedParams(_ parts0: [Part]) -> (mu: Double, sigma: Double) {
        let parts = Self.merged(parts0), st = Market.shared.stats.map { ExtraStats.shared.augment($0, keys: parts0.map(\.key)) }, t = trust / 100
        let d = st?.defaults ?? MC.Stats.Defaults(mu: 0.10, vol: 0.40, corr: 0.3)
        var xs: [(k: String, w: Double, mu: Double, vol: Double)] = parts.map { p in
            let tk = st?.tickers[p.key], b = tk?.mu ?? d.mu, s = tk?.trend ?? b
            let mu = lens == .base ? b : lens == .smooth ? s : MC.blendMu(b, s, t)
            return (k: p.key, w: p.w, mu: mu, vol: tk?.vol ?? d.vol)
        }
        let usdW = parts.filter { $0.ccy == "USD" }.reduce(0) { $0 + $1.w }
        if usdW > 0 { let fx = SiteStats.fx(st); xs.append((k: "KRW=X", w: usdW, mu: fx.0, vol: fx.1)) }
        let ci = Dictionary((st?.corr.keys ?? []).enumerated().map { ($1, $0) }, uniquingKeysWith: { a, _ in a })
        func corr(_ a: String, _ b: String) -> Double {
            if a == b { return 1 }
            if let i = ci[a], let j = ci[b], let m = st?.corr.m { return m[i][j] }
            let safe = { (k: String) in k == "KRW=X" || st?.tickers[k]?.cash == true }
            return safe(a) || safe(b) ? 0 : d.corr
        }
        let mu = xs.filter { $0.k != "KRW=X" }.reduce(0) { $0 + $1.w * $1.mu } + (usdW > 0 ? usdW * (xs.last?.mu ?? 0) : 0)
        var v = 0.0
        for a in xs { for b in xs { v += a.w * b.w * a.vol * b.vol * corr(a.k, b.k) } }
        return (mu, max(0.01, v.squareRoot()))
    }

    /// 계획 4안의 기간 (회복 루트 전망과 같은 3년)
    var planHorizon: String { MC.addMonths(Day.today, 36) }
    /// 계획 구성: 몰린 종목은 wt 까지, 나머지 종목은 그대로, 줄인 몫은 묶음 비율대로 (묶음 = 대표 ETF)
    func planParts(wt: Double, mix: [Basket: Double]) -> [Part] {
        guard let f = focusRow else { return [] }
        let t = max(1, total), w = f.value / t, ccy = { (r: Row) in r.sym.currency == .usd ? "USD" : "KRW" }
        var out: [Part] = [(key: SiteStats.mcKey(f.id), ccy: ccy(f), w: wt)]
        for r in restRows { out.append((key: SiteStats.mcKey(r.id), ccy: ccy(r), w: r.value / t)) }
        let freed = max(0, w - wt), tot = mix.values.reduce(0, +)
        if freed > 0, tot > 0 { for b in Basket.allCases { if let x = mix[b], x > 0 { out.append((key: proxy(b.rawValue), ccy: "USD", w: freed * x / tot)) } } }
        return out
    }

    var plans: [Plan] {
        // 닫힌 식(계산 중 잠깐)의 흔들림·기대 수익도 사이트가 매일 만드는 종목 값 (웹 전망과 같은 값)
        let w = focusWeight, V = total, fn = focusName, H = planHorizon
        func mk(_ id: String, _ name: String, _ tag: String, _ cap: Double, _ mix: [Basket: Double], _ mixText: String) -> Plan {
            let wt = min(w, cap)
            let parts = id == "keep" ? mineParts : planParts(wt: wt, mix: mix)
            let c = closedParams(parts)
            var p = Plan(id: id, name: name, tag: tag, wt: wt, sigma: c.sigma, g: c.mu - c.sigma * c.sigma / 2, mix: mix, mixText: mixText, V: V, focus: fn)
            p.paths = paths(parts, horizon: H)
            return p
        }
        return [
            mk("keep", "유지", "지금 그대로", 1, [.C: 1], ""),
            mk("push", "도전", "빠른 회복 우선", 0.45, [.G: 0.7, .V: 0.3], "성장 지속 7 : 저평가 회복 3"),
            mk("balance", "균형", "회복과 방어 반반", 0.35, [.G: 1.0 / 3, .V: 1.0 / 3, .B: 1.0 / 3], "세 묶음에 고르게"),
            mk("guard", "방어", "덜 잃기 우선", 0.25, [.V: 0.2, .B: 0.6, .C: 0.2], "버팀목 6 : 저평가 회복 2 : 현금 2"),
        ]
    }
    var keepPlan: Plan { plans[0] }
    var selectedPlan: Plan { plans.first { $0.id == planKey } ?? plans[2] }
    var horizonLabel: String { horizon == 12 ? "1년" : "\(horizon)개월" }

    // 미션 2: 손실이 어디서 왔나
    var lossShareFocus: Double {
        let lt = (focusRow.map { $0.value - $0.cost } ?? 0), lq = restRows.reduce(0) { $0 + $1.value - $1.cost }
        let tot = lt + lq
        return tot < 0 ? max(0, min(1, lt / tot)) : 0
    }
    var quizRight: String { focusWeight > 0.5 && lossShareFocus > 0.6 ? "conc" : "mkt" }

    // 미션 3 결과: 옮길 금액 (나머지는 그대로, 줄인 몰린 종목 금액을 묶음에 나눔)
    var freedAmount: Double { max(0, (focusRow?.value ?? 0) - selectedPlan.wt * total) }

    // 적립 지렛대: 매달 같은 돈을 지금 구성에 더 넣을 때. 본전 = 들어간 돈 + 그동안 넣은 돈 (넣은 만큼 같이 오른다)
    /// prob = months 안에 본전에 닿을 확률, eta = 보통의 경우 본전에 처음 닿는 개월 (3년 안에 없으면 무한대)
    struct Deposit { let prob: Double; let eta: Double }
    func deposit(_ p: Plan, monthly mo: Int, months T: Int) -> Deposit {
        let D = Double(mo) * 1e4, C = cost
        if let ps = p.paths {
            if total >= C { return Deposit(prob: 1, eta: 0) }
            let v = ps.values(p.V, monthly: D), M = ps.M
            let hits = ps.firstHits(v, upTo: M - 1) { k in C + D * Double(min(k, M - 2)) }
            let kT = ps.k(months: T)
            return Deposit(prob: Double(hits.filter { $0 >= 0 && $0 <= kT }.count) / Double(ps.P), eta: ps.medianMonths(hits))
        }
        // 계산 중: 닫힌 식 흔들림으로 잠깐 (1,000번 월 단위)
        let ck = "\(p.id)|\(p.V)|\(p.sigma)|\(C)|\(mo)|\(T)"
        if let c = depCache[ck] { return c }
        var seed = 24680
        func rnd() -> Double { seed = (seed &* 1103515245 &+ 12345) % 2147483648; return (Double(seed) + 0.5) / 2147483648 }
        let NP = 1000, H = 36, mm = p.g / 12, ms = p.sigma / sqrt(12)
        var ok = 0, hits: [Double] = []
        for _ in 0..<NP {
            var v = p.V, c = C, hit = Double.infinity
            if v >= c { hit = 0 }
            for k in 1...H {
                let z = sqrt(-2 * log(rnd())) * cos(6.283185307 * rnd())
                v = v * exp(mm + ms * z) + D; c += D
                if hit == .infinity && v >= c { hit = Double(k) }
            }
            if hit <= Double(T) { ok += 1 }
            hits.append(hit)
        }
        let r = Deposit(prob: Double(ok) / Double(NP), eta: hits.sorted()[NP / 2])
        if depCache.count > 32 { depCache.removeAll() }
        depCache[ck] = r
        return r
    }

    // 절세 (대한민국 거주자: 해외주식 이익-손실 합계에서 250만원 공제 뒤 22%)
    var taxLossMan: Double {
        guard let d = focusRow else { return 0 }
        return krw(d.sym, taxSellQty * (d.sym.last - d.h.avg)) / 1e4
    }
    var taxBefore: Double { max(0, taxGain - 250) * 0.22 }
    var taxAfter: Double { max(0, taxGain + taxLossMan - 250) * 0.22 }
}

// 1년 범위 그래프: 띠 = 100번 중 90번, 선 = 보통의 경우, 빨간 점선 = 본전, 회색 점선 = 손실 절반
struct PathChart: View {
    let plans: [(Plan, Color, Bool)]   // (계획, 색, 점선 여부) 뒤에 올수록 위에 그림
    let V: Double
    let C: Double
    var half: Double? = nil
    var marker: Int? = nil             // 기간 칩 위치 (개월)

    var body: some View {
        Canvas { ctx, size in
            let W = size.width, H = size.height, ymax = max(C * 1.2, V * 1.9)
            let X = { (t: Double) in t / 12 * W }
            let Y = { (v: Double) in H - min(v, ymax) / ymax * H }
            func val(_ p: Plan, _ t: Double, _ k: Double) -> Double { p.level(t, k) }
            for x in [3.0, 6.0] {
                ctx.stroke(Path { $0.move(to: CGPoint(x: X(x), y: 0)); $0.addLine(to: CGPoint(x: X(x), y: H)) }, with: .color(Theme.border), lineWidth: 1)
            }
            let ts = stride(from: 0.0, through: 12, by: 0.5).map { $0 }
            for (p, c, dashed) in plans {
                var band = Path()
                for (i, t) in ts.enumerated() { let pt = CGPoint(x: X(t), y: Y(val(p, t, 1))); i == 0 ? band.move(to: pt) : band.addLine(to: pt) }
                for t in ts.reversed() { band.addLine(to: CGPoint(x: X(t), y: Y(val(p, t, -1)))) }
                band.closeSubpath()
                ctx.fill(band, with: .color(c.opacity(dashed ? 0.12 : 0.18)))
            }
            ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: Y(C))); $0.addLine(to: CGPoint(x: W, y: Y(C))) },
                       with: .color(Color(hex: 0xC8352E)), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            if let half {
                ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: Y(half))); $0.addLine(to: CGPoint(x: W, y: Y(half))) },
                           with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1, dash: [2, 4]))
            }
            if let marker {
                ctx.stroke(Path { $0.move(to: CGPoint(x: X(Double(marker)), y: 0)); $0.addLine(to: CGPoint(x: X(Double(marker)), y: H)) },
                           with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
            }
            for (p, c, dashed) in plans {
                var med = Path()
                for (i, t) in ts.enumerated() { let pt = CGPoint(x: X(t), y: Y(val(p, t, 0))); i == 0 ? med.move(to: pt) : med.addLine(to: pt) }
                ctx.stroke(med, with: .color(c), style: StrokeStyle(lineWidth: dashed ? 2 : 2.5, dash: dashed ? [3, 3] : []))
            }
        }
        .accessibilityLabel("1년 평가액 범위와 본전선")
    }
}
