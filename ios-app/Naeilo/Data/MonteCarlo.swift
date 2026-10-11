import Foundation

// 전망 몬테카를로: web/model.js 의 buildModel·simulate·reweight 를 그대로 옮겼다.
// 같은 시드 난수(xoshiro128** + Box-Muller), 같은 일별 단계(다변량 t 충격 + 사건 점프), 같은 월 경계·적립이라
// 같은 입력이면 웹과 같은 결과가 나온다. GitHub Actions(ios.yml)가 tools/mc_parity.mjs 로 웹과 비교한다.
// 종목 값은 사이트가 매일 만드는 stats.json (μ·σ·상관·추세 기울기·요인 민감도·기본 사건·설정).
// 이 파일은 Foundation 만 쓴다 (비교 도구 ios-app/tools/mc_parity 가 따로 컴파일한다).
enum MC {
    static let TD = 252.0

    // MARK: 사이트 통계 (data/stats.json)
    struct Stats: Codable {
        struct Ticker: Codable { var mu: Double?; var trend: Double?; var gs: Double?; var vol: Double?; var n: Int?; var cash: Bool?; var fx: Bool? }
        struct Defaults: Codable { var mu: Double; var vol: Double; var corr: Double }
        struct Corr: Codable { var keys: [String]; var m: [[Double]] }
        var date: String
        var defaults: Defaults
        var tickers: [String: Ticker]
        var corr: Corr
        /// 웹 첫 방문 전망 설정 · 기본 사건 · 요인 민감도 β[요인][종목] (예전 파일에는 없다)
        var model: Settings? = nil
        var events: [Event]? = nil
        var betas: [String: [String: Double]]? = nil
    }

    /// 전망 설정 (웹 state.model 과 같은 칸). 없는 칸은 웹 기본값
    struct Settings: Codable {
        var scenario: String? = nil
        var trust: Double? = nil
        var n_paths: Double? = nil
        var seed: Double? = nil
        var t_dof: Double? = nil
        var earnings_adjust: Bool? = nil
        var fx_drift: Double? = nil
        var fx_vol_mult: Double? = nil
        var prior_mu: Double? = nil
        var default_vol: Double? = nil
        var default_corr: Double? = nil
        init() {}
        init(from d: Decoder) throws {
            let c = try d.container(keyedBy: CodingKeys.self)
            scenario = try? c.decodeIfPresent(String.self, forKey: .scenario)
            trust = c.mcNum(.trust); n_paths = c.mcNum(.n_paths); seed = c.mcNum(.seed); t_dof = c.mcNum(.t_dof)
            earnings_adjust = c.mcNum(.earnings_adjust).map { $0 != 0 }
            fx_drift = c.mcNum(.fx_drift); fx_vol_mult = c.mcNum(.fx_vol_mult); prior_mu = c.mcNum(.prior_mu)
            default_vol = c.mcNum(.default_vol); default_corr = c.mcNum(.default_corr)
        }
        /// 사이트 입력값의 설정을 위에 덮는다 (기기 동기화를 켰을 때)
        func merged(_ o: Settings?) -> Settings {
            guard let o else { return self }
            var s = self
            s.scenario = o.scenario ?? s.scenario; s.trust = o.trust ?? s.trust; s.n_paths = o.n_paths ?? s.n_paths; s.seed = o.seed ?? s.seed
            s.t_dof = o.t_dof ?? s.t_dof; s.earnings_adjust = o.earnings_adjust ?? s.earnings_adjust; s.fx_drift = o.fx_drift ?? s.fx_drift
            s.fx_vol_mult = o.fx_vol_mult ?? s.fx_vol_mult; s.prior_mu = o.prior_mu ?? s.prior_mu; s.default_vol = o.default_vol ?? s.default_vol
            s.default_corr = o.default_corr ?? s.default_corr
            return s
        }
        var nPaths: Int { max(1, Int(n_paths ?? 3000)) }
        var seedU32: UInt32 { let s = seed ?? 1; return s == 0 || !s.isFinite ? 1 : UInt32(truncatingIfNeeded: Int64(s)) }   // 웹: Number(seed) || 1, >>> 0
        var dof: Double { t_dof.flatMap { $0 == 0 || !$0.isFinite ? nil : $0 } ?? 5 }
    }

    /// 사건 하나 (웹 state.events 한 줄). 숫자 칸은 웹처럼 너그럽게 읽는다 (문자열 숫자, 빈 칸)
    struct Event: Codable {
        var id: String?
        var on: Bool
        var date: String?
        var target: String?
        var factor: String?
        var cat: String?
        var rep: String?
        var prob: Double       // % (웹: 값이 없거나 숫자가 아니면 100)
        var mean: Double       // % 또는 bp
        var sd: Double
        var volMult: Double
        var volDays: Double
        enum CodingKeys: String, CodingKey { case id, on, date, target, factor, cat, rep = "repeat", prob, mean, sd, volMult = "vol_mult", volDays = "vol_days" }
        init(from d: Decoder) throws {
            let c = try d.container(keyedBy: CodingKeys.self)
            id = c.mcStr(.id); date = c.mcStr(.date); target = c.mcStr(.target); factor = c.mcStr(.factor); cat = c.mcStr(.cat); rep = c.mcStr(.rep)
            if let b = try? c.decodeIfPresent(Bool.self, forKey: .on) { on = b }
            else if let x = try? c.decodeIfPresent(Double.self, forKey: .on) { on = x != 0 && !x.isNaN }
            else { on = !(c.mcStr(.on) ?? "").isEmpty }
            // Number(e.prob): 없음·빈 문자열·숫자 아님 → 100, null → 0
            if !c.contains(.prob) { prob = 100 }
            else if (try? c.decodeNil(forKey: .prob)) == true { prob = 0 }
            else if let s = try? c.decode(String.self, forKey: .prob) { prob = s.isEmpty ? 100 : MC.jsNumber(s) ?? 100 }
            else { prob = c.mcNum(.prob) ?? 100 }
            mean = c.mcNum(.mean) ?? 0; sd = c.mcNum(.sd) ?? 0; volMult = c.mcNum(.volMult) ?? 0; volDays = c.mcNum(.volDays) ?? 0
        }
        func encode(to e: Encoder) throws {
            var c = e.container(keyedBy: CodingKeys.self)
            try c.encodeIfPresent(id, forKey: .id); try c.encode(on, forKey: .on); try c.encodeIfPresent(date, forKey: .date)
            try c.encodeIfPresent(target, forKey: .target); try c.encodeIfPresent(factor, forKey: .factor); try c.encodeIfPresent(cat, forKey: .cat)
            try c.encodeIfPresent(rep, forKey: .rep); try c.encode(prob, forKey: .prob); try c.encode(mean, forKey: .mean); try c.encode(sd, forKey: .sd)
            try c.encode(volMult, forKey: .volMult); try c.encode(volDays, forKey: .volDays)
        }
    }

    /// JS Number(문자열) 근사: 앞뒤 공백을 빼고 숫자로 읽는다
    static func jsNumber(_ s: String) -> Double? {
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? 0 : Double(t)
    }

    // MARK: 날짜 (yyyy-MM-dd, UTC 날짜 수)
    static func dayNum(_ s: String) -> Int {
        let p = s.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return 0 }
        return civilDays(p[0], p[1], p[2])
    }
    static func civilDays(_ y0: Int, _ m: Int, _ d: Int) -> Int {
        let y = m <= 2 ? y0 - 1 : y0
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400, mp = (m + 9) % 12
        let doy = (153 * mp + 2) / 5 + d - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
    static func civil(_ z0: Int) -> (Int, Int, Int) {
        let z = z0 + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1, m = mp < 10 ? mp + 3 : mp - 9
        return (yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }
    static func dayStr(_ z: Int) -> String {
        let (y, m, d) = civil(z)
        return String(format: "%04d-%02d-%02d", y, m, d)
    }
    /// 웹 addMonths: 같은 날짜, 그 달에 없으면 말일
    static func addMonths(_ s: String, _ n: Int) -> String {
        let p = s.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return s }
        let t = p[0] * 12 + (p[1] - 1) + n
        let y = Int((Double(t) / 12).rounded(.down)), m = t - y * 12 + 1
        let last = civilDays(m == 12 ? y + 1 : y, m == 12 ? 1 : m + 1, 1) - civilDays(y, m, 1)
        return dayStr(civilDays(y, m, min(p[2], last)))
    }
    /// 0 = 일요일 … 6 = 토요일
    static func weekday(_ z: Int) -> Int { ((z % 7) + 7 + 4) % 7 }

    static let perYear: [String: Double] = ["monthly": 12, "6w": 365.25 / 42, "quarterly": 4, "semi": 2, "yearly": 1]
    static let stepMonths: [String: Int] = ["monthly": 1, "quarterly": 3, "semi": 6, "yearly": 12]
    /// 사건 반복 날짜 (startDate 초과 ~ goalDate 이하)
    static func occurrences(_ e: Event, _ start: String, _ goal: String) -> [String] {
        guard let date = e.date else { return [] }
        var out: [String] = []
        for k in 0..<600 {
            let d = e.rep == "6w" ? dayStr(dayNum(date) + 42 * k) : addMonths(date, k * (stepMonths[e.rep ?? ""] ?? 0))
            if d > goal { break }
            if d > start { out.append(d) }
            if perYear[e.rep ?? ""] == nil { break }
        }
        return out
    }

    // MARK: 난수 (웹 makeRng 와 같은 순서)
    struct Rng {
        var a: UInt32, b: UInt32 = 0x9e3779b9, c: UInt32 = 0x243f6a88, d: UInt32 = 0xb7e15162
        var spare = 0.0, hasSpare = false
        init(_ seed: UInt32) { a = seed; for _ in 0..<20 { _ = next() } }
        @inline(__always) static func rotl(_ x: UInt32, _ k: UInt32) -> UInt32 { (x << k) | (x >> (32 - k)) }
        @inline(__always) mutating func next() -> Double {
            let r = Self.rotl(b &* 5, 7) &* 9
            let t = b << 9
            c ^= a; d ^= b; b ^= c; a ^= d; c ^= t; d = Self.rotl(d, 11)
            return Double(r) / 4294967296
        }
        @inline(__always) mutating func normal() -> Double {
            if hasSpare { hasSpare = false; return spare }
            var u = 0.0
            while u == 0 { u = next() }
            let v = next(), r = (-2 * log(u)).squareRoot(), th = 2 * Double.pi * v
            spare = r * sin(th); hasSpare = true
            return r * cos(th)
        }
    }

    static func cholesky(_ A: [[Double]]) -> [[Double]]? {
        let n = A.count
        var L = Array(repeating: Array(repeating: 0.0, count: n), count: n)
        for i in 0..<n {
            for j in 0...i {
                var s = A[i][j]
                for k in 0..<j { s -= L[i][k] * L[j][k] }
                if i == j { if s <= 1e-10 { return nil }; L[i][i] = s.squareRoot() }
                else { L[i][j] = s / L[j][j] }
            }
        }
        return L
    }

    // MARK: 모형 만들기 (웹 buildModel, 종목 값은 stats.json)
    struct Holding: Codable { var key: String; var ccy: String; var valueKrw: Double; var price0: Double }
    struct Factor: Codable {
        var key: String
        var isFx: Bool
        var cash: Bool
        var vol: Double
        var base: Double
        var smooth: Double
        var blend: Double
        var evVar: Double
        var evVarCat: [String: Double]
        var evCats: [String]          // evVarCat 을 더한 순서 (웹 객체 순서)
    }
    struct Ev { let prob, jm, js, vm: Double; let vd: Int; let targets: [Int]; let scales: [Double]; let cat: String }
    struct Model {
        var factors: [Factor]
        var L: [[Double]]
        var days: [String]
        var monthIdx: [Int]
        var monthDates: [String]
        var byDay: [Int: [Ev]]
        var eventCount: Int
        var start: String
        var goal: String
        var trust: Double
    }

    static func fxOf(_ ccy: String) -> String? { ccy.isEmpty || ccy == "KRW" ? nil : ccy == "USD" ? "KRW=X" : ccy + "KRW=X" }
    static func blendMu(_ b: Double, _ s: Double, _ a: Double) -> Double { exp((1 - a) * log(1 + b) + a * log(1 + s)) - 1 }

    /// trust: 추세 신뢰 % (내 관점 섞기). 사건은 설정 events (없으면 stats 기본 사건), β 는 stats
    static func build(holdings: [Holding], stats st: Stats, settings m: Settings, events: [Event], start: String, goal: String) -> Model? {
        guard !holdings.isEmpty, goal > start else { return nil }
        let prior = (m.prior_mu ?? 10) / 100, defVol = (m.default_vol ?? 40) / 100, defCorr = m.default_corr ?? 0.3
        let fxDrift = (m.fx_drift ?? 0) / 100, fxMult = m.fx_vol_mult.flatMap { $0 == 0 ? nil : $0 } ?? 1
        let newFormat = st.model != nil
        // 요인: 보유 종목 + 쓰이는 환율
        var fs: [Factor] = [], known: [Bool] = [], ns: [Int] = [], gs: [Double?] = [], trend: [Double?] = []
        for h in holdings {
            if let t = st.tickers[h.key], t.fx != true, let mu = t.mu, let vol = t.vol {
                fs.append(Factor(key: h.key, isFx: false, cash: t.cash ?? false, vol: vol, base: mu, smooth: mu, blend: mu, evVar: 0, evVarCat: [:], evCats: []))
                known.append(true); ns.append(t.n ?? Int(TD)); gs.append(t.gs); trend.append(t.trend)
            } else {
                fs.append(Factor(key: h.key, isFx: false, cash: false, vol: defVol, base: prior, smooth: prior, blend: prior, evVar: 0, evVarCat: [:], evCats: []))
                known.append(false); ns.append(0); gs.append(nil); trend.append(nil)
            }
        }
        var fxKeys: [String] = []
        for h in holdings { if let k = fxOf(h.ccy), !fxKeys.contains(k) { fxKeys.append(k) } }
        for k in fxKeys {
            let vol = st.tickers[k]?.vol ?? 0.10 * fxMult
            fs.append(Factor(key: k, isFx: true, cash: false, vol: vol, base: fxDrift, smooth: fxDrift, blend: fxDrift, evVar: 0, evVarCat: [:], evCats: []))
            known.append(st.tickers[k] != nil); ns.append(0); gs.append(nil); trend.append(nil)
        }
        let F = fs.count
        // 상관: 사이트 표 (같은 계산), 표에 없으면 웹 사전값 (위험 종목끼리 기본 상관, 현금성·환율이 끼면 0)
        let ci = Dictionary(st.corr.keys.enumerated().map { ($1, $0) }, uniquingKeysWith: { a, _ in a })
        var C = Array(repeating: Array(repeating: 0.0, count: F), count: F)
        for i in 0..<F {
            C[i][i] = 1
            for j in 0..<i {
                var c: Double
                if known[i], known[j], let a = ci[fs[i].key], let b = ci[fs[j].key], a < st.corr.m.count, b < st.corr.m[a].count { c = st.corr.m[a][b] }
                else { c = !(fs[i].cash || fs[j].cash || fs[i].isFx || fs[j].isFx) ? defCorr : 0 }
                c = max(-0.95, min(0.95, c))
                C[i][j] = c; C[j][i] = c
            }
        }
        var lam = 0.0, L: [[Double]]? = nil
        while L == nil {
            var S = C
            for i in 0..<F { for j in 0..<F where i != j { S[i][j] = C[i][j] * (1 - lam) } }
            L = cholesky(S)
            if L == nil { lam += 0.05 }
        }

        // 미래 거래일(평일)과 월 경계. monthIdx 는 경로 배열 기준 (0 = 시작, i+1 = days[i] 장마감)
        var days: [String] = []
        let g0 = dayNum(goal)
        var z = dayNum(start) + 1
        while z <= g0 { let w = weekday(z); if w != 0 && w != 6 { days.append(dayStr(z)) }; z += 1 }
        guard !days.isEmpty else { return nil }
        var monthIdx = [0], monthDates = [start]
        for i in 1..<days.count where days[i].prefix(7) != days[i - 1].prefix(7) { monthIdx.append(i + 1); monthDates.append(days[i]) }
        if monthIdx.last != days.count { monthIdx.append(days.count); monthDates.append(days[days.count - 1]) }

        // 사건 → 거래일 번호. 요인 사건은 종목별 민감도만큼 나눠 준다 (금리는 bp)
        func dayOf(_ s: String) -> Int {
            var lo = 0, hi = days.count
            while lo < hi { let mid = (lo + hi) >> 1; if days[mid] < s { lo = mid + 1 } else { hi = mid } }
            return lo
        }
        let keyIdx = Dictionary(fs.enumerated().map { ($1.key, $0) }, uniquingKeysWith: { _, b in b })   // 웹 Map 처럼 같은 키는 뒤의 것
        let betas = st.betas ?? [:]
        var byDay: [Int: [Ev]] = [:], count = 0
        for e in events {
            guard e.on, let date = e.date, !date.isEmpty else { continue }
            var targets: [Int] = [], scales: [Double] = []
            if let fk = e.factor, !fk.isEmpty, fk != "none" {
                let bt = betas[fk] ?? [:]
                for (i, f) in fs.enumerated() where !f.isFx && !f.cash { if let b = bt[f.key], abs(b) > 1e-6 { targets.append(i); scales.append(b) } }
            } else {
                if e.target == "ALL" { targets = fs.indices.filter { !fs[$0].isFx && !fs[$0].cash } }
                else if e.target == "FX" { if let i = keyIdx[fxOf("USD")!] { targets = [i] } }
                else if let t = e.target, let i = keyIdx[t], !fs[i].isFx { targets = [i] }
                scales = targets.map { _ in 1 }
            }
            if targets.isEmpty { continue }
            let lin = e.factor == "rate"
            let sd = max(0, e.sd) / (lin ? 1 : 100), mn = e.mean / (lin ? 1 : 100)
            let prob = min(1, max(0, e.prob / 100))
            let ev = Ev(prob: prob, jm: lin ? mn : log(max(0.01, 1 + mn)) - 0.5 * sd * sd, js: sd,
                        vm: max(0.1, e.volMult == 0 || e.volMult.isNaN ? 1 : e.volMult),
                        vd: max(0, Int((e.volDays.isNaN ? 0 : e.volDays + 0.5).rounded(.down))), targets: targets, scales: scales,
                        cat: (e.cat ?? "").isEmpty ? "corp" : e.cat!)
            for d in occurrences(e, start, goal) {
                let di = dayOf(d); if di >= days.count { break }
                byDay[di, default: []].append(ev); count += 1
            }
            // 반복 사건은 과거 변동성에 이미 들어 있으므로 평소 변동성에서 그만큼 뺀다 (요인 묶음별로 기록)
            let fr = perYear[e.rep ?? ""] ?? 0
            if (m.earnings_adjust ?? true) && fr != 0 {
                for (k, t) in targets.enumerated() {
                    let v = fr * ev.prob * pow(scales[k] * sd, 2)
                    if fs[t].evVarCat[ev.cat] == nil { fs[t].evCats.append(ev.cat) }
                    fs[t].evVarCat[ev.cat, default: 0] += v
                }
            }
        }
        let a = (m.trust ?? 50) / 100
        for i in 0..<F {
            var f = fs[i]
            let cats = f.evVarCat
            f.evVar = f.evCats.reduce(0.0) { $0 + cats[$1]! }
            let volDiff = f.evVar != 0 ? max(f.vol * f.vol - f.evVar, 0.5 * f.vol * f.vol).squareRoot() : f.vol
            // 추종 시나리오: 3년 직선 기울기 gs 를 중앙값 성장률로 이어 가는 산술 기대수익 (이력이 1년보다 짧으면 기준과 섞는다)
            if !(f.isFx || f.cash || ns[i] < 5 || !known[i]) {
                let w = min(1, Double(ns[i]) / TD)
                if newFormat { if let g = gs[i] { f.smooth = w * (exp(max(-0.9, min(1.2, g)) + 0.5 * volDiff * volDiff) - 1) + (1 - w) * f.base } }
                else if let t = trend[i] { f.smooth = t }
            }
            f.blend = blendMu(f.base, f.smooth, a)
            fs[i] = f
        }
        return Model(factors: fs, L: L!, days: days, monthIdx: monthIdx, monthDates: monthDates, byDay: byDay, eventCount: count,
                     start: start, goal: goal, trust: a * 100)
    }

    // MARK: 몬테카를로 (웹 simulate)
    struct Terminal: Codable { var p5, p25, p50, p75, p95, mean: Double }
    struct Year: Codable { var year: Int; var date: String; var p: Double; var k: Int }
    /// 결과 요약: 월 경계마다 평가액 백분위(5·25·50·75·95), 목표 확률 등
    struct Result: Codable {
        var V0: Double
        var bands: [[Double]]
        var pGoal: Double
        var pTouch: Double
        var terminal: Terminal
        var mddMedian: Double
        var byYear: [Year]
        /// cumHit[k]: k번째 월 경계까지 한 번이라도 목표 이상이었던 경로 비율 (reweight 만)
        var cumHit: [Double]
    }
    /// 다시 무게 주기(reweight)에 쓰는 경로 (재조정 없음). cg[a][k*P+p] = 그 달까지 종목+환율 누적 로그수익 (Float32, 웹과 같다)
    struct Sim {
        var A: Int, M: Int, P: Int
        var cg: [[Float]]
        var muD: [Double]
        var monthIdx: [Int]
        var monthDates: [String]
        var v0: [Double]
        var start: String
        var summary: Result
    }

    static let qs = [0.05, 0.25, 0.5, 0.75, 0.95]
    static func quantileSorted(_ s: UnsafeBufferPointer<Double>, _ q: Double) -> Double {
        let pos = Double(s.count - 1) * q, lo = Int(pos.rounded(.down)), hi = Int(pos.rounded(.up))
        return s[lo] + (s[hi] - s[lo]) * (pos - Double(lo))
    }
    static func quantileSorted(_ s: [Double], _ q: Double) -> Double { s.withUnsafeBufferPointer { quantileSorted($0, q) } }
    static func bandsOf(_ arr: [Double], M: Int, P: Int) -> [[Double]] {
        var out = Array(repeating: [Double](), count: qs.count)
        var tmp = [Double](repeating: 0, count: P)
        for k in 0..<M {
            for p in 0..<P { tmp[p] = arr[k * P + p] }
            tmp.sort()
            tmp.withUnsafeBufferPointer { b in for (i, q) in qs.enumerated() { out[i].append(quantileSorted(b, q)) } }
        }
        return out
    }
    static func terminal(_ sorted: [Double]) -> Terminal {
        var sum = 0.0; for x in sorted { sum += x }
        return Terminal(p5: quantileSorted(sorted, 0.05), p25: quantileSorted(sorted, 0.25), p50: quantileSorted(sorted, 0.5),
                        p75: quantileSorted(sorted, 0.75), p95: quantileSorted(sorted, 0.95), mean: sum / Double(sorted.count))
    }

    /// mu: 요인별 연 기대수익 (시나리오), withEvents: 사건 점프를 넣을지
    static func simulate(_ model: Model, holdings: [Holding], mu: [Double], nPaths P: Int, seed: UInt32, goal: Double, monthly: Double,
                         withEvents: Bool, dof: Double) -> Sim {
        let fs = model.factors, F = fs.count, A = holdings.count, D = model.days.count, M = model.monthIdx.count
        let nu = max(3, dof), tAdj = ((nu - 2) / nu).squareRoot()
        var rng = Rng(seed)
        // 사건을 빼고 계산할 때는 실적 몫을 뺀 변동성 대신 과거 변동성 그대로
        let sig: [Double] = fs.map { f in
            if !withEvents { return f.vol / TD.squareRoot() }
            let ded = f.evCats.reduce(0.0) { $0 + f.evVarCat[$1]! }
            return (ded != 0 ? max(f.vol * f.vol - ded, 0.5 * f.vol * f.vol).squareRoot() : f.vol) / TD.squareRoot()
        }
        let muD = mu.map { log(1 + $0) / TD }
        let fxCol: [Int] = holdings.map { h in fxOf(h.ccy).flatMap { k in fs.firstIndex { $0.isFx && $0.key == k } } ?? -1 }
        let v0 = holdings.map(\.valueKrw), V0 = v0.reduce(0, +)
        let w = v0.map { $0 / V0 }
        var isMonth = [Int](repeating: -1, count: D + 1)
        for (k, d) in model.monthIdx.enumerated() { isMonth[d] = k }
        let Lf = model.L.flatMap { $0 }            // F×F
        let evDay: [[Ev]] = withEvents ? (0..<D).map { model.byDay[$0] ?? [] } : []
        var nTrials = 0; while Double(nTrials) < nu { nTrials += 1 }   // 웹: for (k = 0; k < nu; k++)

        var port = [Double](repeating: 0, count: M * P)
        var cg = Array(repeating: [Float](repeating: 0, count: M * P), count: A)
        var firstHit = [Int](repeating: -1, count: P), mddArr = [Double](repeating: 0, count: P)
        var touched = 0
        var z = [Double](repeating: 0, count: F), lg = [Double](repeating: 0, count: F), lr = [Double](repeating: 0, count: F)
        var boostUntil = [Int](repeating: -1, count: F), boostMult = [Double](repeating: 1, count: F)
        var h = [Double](repeating: 0, count: A), G = [Double](repeating: 0, count: A)

        for p in 0..<P {
            for f in 0..<F { lg[f] = 0; boostUntil[f] = -1; boostMult[f] = 1 }
            for a in 0..<A { h[a] = v0[a]; G[a] = 1 }
            var peak = V0, mdd = 0.0, hit = false
            port[p] = V0
            for d in 0..<D {
                for f in 0..<F { z[f] = rng.normal() }
                var chi = 0.0
                for _ in 0..<nTrials { let g = rng.normal(); chi += g * g }
                let ts = tAdj / (chi / nu).squareRoot()
                for f in 0..<F {
                    var s = 0.0
                    let row = f * F
                    for k in 0...f { s += Lf[row + k] * z[k] }
                    let sd = sig[f] * (d <= boostUntil[f] ? boostMult[f] : 1)
                    lr[f] = muD[f] - 0.5 * sd * sd + sd * s * ts
                }
                if withEvents {
                    for ev in evDay[d] {
                        let u = rng.next(), zz = rng.normal()   // 걸러 낸 사건도 난수는 똑같이 쓴다
                        if u >= ev.prob { continue }
                        let j = ev.jm + ev.js * zz
                        for k in 0..<ev.targets.count {
                            let t = ev.targets[k]
                            lr[t] += j * ev.scales[k]
                            if ev.vd > 0 { boostUntil[t] = d + ev.vd; boostMult[t] = ev.vm }
                        }
                    }
                }
                var V = 0.0
                for a in 0..<A {
                    let g = lr[a] + (fxCol[a] >= 0 ? lr[fxCol[a]] : 0)
                    let e = exp(g); h[a] *= e; G[a] *= e; V += h[a]
                }
                for f in 0..<F { lg[f] += lr[f] }
                let k = isMonth[d + 1]
                if k > 0 && k < M - 1, monthly > 0 { for a in 0..<A { h[a] += monthly * w[a] }; V += monthly }   // 월초 적립 (마지막 날 제외)
                if V > peak { peak = V } else { mdd = min(mdd, V / peak - 1) }
                if V >= goal { hit = true; if firstHit[p] < 0 { firstHit[p] = d + 1 } }
                if k > 0 {
                    port[k * P + p] = V
                    for a in 0..<A { cg[a][k * P + p] = Float(lg[a] + (fxCol[a] >= 0 ? lg[fxCol[a]] : 0)) }
                }
            }
            mddArr[p] = mdd
            if hit { touched += 1 }
        }

        let bands = bandsOf(port, M: M, P: P)
        let term = Array(port[((M - 1) * P)...]).sorted()
        var byYear: [Year] = []
        var y = 1
        while D > 0 {
            let target = addMonths(model.start, 12 * y)
            var lim = 0; while lim < D && model.days[lim] <= target { lim += 1 }
            if lim > 0 {
                let n = firstHit.reduce(0) { $0 + ($1 >= 0 && $1 <= lim ? 1 : 0) }
                byYear.append(Year(year: y, date: model.days[lim - 1], p: Double(n) / Double(P), k: -1))
                if lim >= D { break }
            }
            y += 1
        }
        let summary = Result(V0: V0, bands: bands, pGoal: Double(term.filter { $0 >= goal }.count) / Double(P), pTouch: Double(touched) / Double(P),
                             terminal: terminal(term), mddMedian: quantileSorted(mddArr.sorted(), 0.5), byYear: byYear, cumHit: [])
        return Sim(A: A, M: M, P: P, cg: cg, muD: Array(muD.prefix(A)), monthIdx: model.monthIdx, monthDates: model.monthDates, v0: v0,
                   start: model.start, summary: summary)
    }

    // MARK: 다시 무게 주기 (웹 reweight, 재시뮬레이션 없이)
    /// 같은 충격 경로에 기대수익(드리프트)·월 적립·목표만 바꿔 다시 계산한다. mu: 종목별 연 기대수익
    static func reweight(_ X: Sim, mu: [Double], goal: Double, monthly: Double) -> Result {
        let A = X.A, M = X.M, P = X.P, mi = X.monthIdx
        let dd = (0..<A).map { a in log(1 + mu[a]) / TD - X.muD[a] }
        let v = X.v0, V0 = v.reduce(0, +), wN = v.map { $0 / V0 }
        var port = [Double](repeating: 0, count: M * P)
        var firstHitK = [Int](repeating: -1, count: P), mddArr = [Double](repeating: 0, count: P)
        var accU = [Double](repeating: 0, count: A)
        for p in 0..<P {
            for a in 0..<A { accU[a] = 0 }
            var peak = V0, mdd = 0.0
            for k in 0..<M {
                let t = Double(mi[k]), contrib = k > 0 && k < M - 1
                var V = 0.0
                for a in 0..<A {
                    let U = exp((k > 0 ? Double(X.cg[a][k * P + p]) : 0) + dd[a] * t)
                    if contrib { accU[a] += wN[a] / U }
                    V += U * (v[a] + monthly * accU[a])
                }
                port[k * P + p] = V
                if V > peak { peak = V } else { mdd = min(mdd, V / peak - 1) }
                if V >= goal && firstHitK[p] < 0 && k > 0 { firstHitK[p] = k }
            }
            mddArr[p] = mdd
        }
        let term = Array(port[((M - 1) * P)...]).sorted()
        var hist = [Int](repeating: 0, count: M)
        for k in firstHitK where k >= 0 { hist[k] += 1 }
        var cum = [Double](repeating: 0, count: M), run = 0
        for k in 0..<M { run += hist[k]; cum[k] = Double(run) / Double(P) }
        var byYear: [Year] = []
        var y = 1
        while true {
            let target = addMonths(X.start, 12 * y)
            var k = 0; while k + 1 < M && X.monthDates[k + 1] <= target { k += 1 }
            byYear.append(Year(year: y, date: X.monthDates[k], p: cum[k], k: k))
            if k >= M - 1 { break }
            y += 1
        }
        return Result(V0: V0, bands: bandsOf(port, M: M, P: P), pGoal: Double(term.filter { $0 >= goal }.count) / Double(P),
                      pTouch: Double(firstHitK.filter { $0 >= 0 }.count) / Double(P), terminal: terminal(term),
                      mddMedian: quantileSorted(mddArr.sorted(), 0.5), byYear: byYear, cumHit: cum)
    }

    /// 월 경계 번호: 시작일에서 months 개월 뒤 날짜 이하인 마지막 칸 (웹 breakeven·reweight 의 byYear 와 같은 셈)
    static func monthK(_ X: Sim, months: Int) -> Int {
        let target = addMonths(X.start, months)
        var k = 0; while k + 1 < X.M && X.monthDates[k + 1] <= target { k += 1 }
        return k
    }

    // MARK: 본전 확률 (웹 breakeven, 재시뮬레이션 없이)
    /// 같은 경로에서 종목 묶음(idx)만 적립·매매 없이 들고 있을 때, 월말 평가액이 basis 이상이 되는 첫 달이 months 안에 올 확률.
    /// mu: 종목별 연 기대수익 (reweight 와 같은 드리프트 보정). 경로가 기한까지 없으면 nil
    static func breakeven(_ X: Sim, idx: [Int], basis: Double, mu: [Double], months: [Int]) -> [Double?] {
        let M = X.M, P = X.P
        let dd = idx.map { a in log(1 + mu[a]) / TD - X.muD[a] }
        var first = [Int](repeating: -1, count: P)
        for p in 0..<P {
            for k in 1..<max(1, M) {
                var V = 0.0
                for (j, a) in idx.enumerated() { V += X.v0[a] * exp(Double(X.cg[a][k * P + p]) + dd[j] * Double(X.monthIdx[k])) }
                if V >= basis { first[p] = k; break }
            }
        }
        let last = dayNum(X.monthDates[M - 1])
        return months.map { m in
            if dayNum(addMonths(X.start, m)) - last > 15 { return nil }
            let k = monthK(X, months: m)
            return Double(first.reduce(0) { $0 + ($1 >= 0 && $1 <= k ? 1 : 0) }) / Double(P)
        }
    }

    // MARK: 월 경계마다 평가액 (재조정 없음, 웹 reweight 와 같은 식)
    /// 처음 금액 start[a], 매달 monthly 를 wC[a] 비율로 나눠 넣는다 (월 경계 1 ~ M-2). 반환 [k*P+p].
    /// start = v0, wC = v0 비중이면 reweight 의 경로와 같다. 구성 비교·계획처럼 처음 금액이 0 이어도 쓸 수 있다
    static func values(_ X: Sim, mu: [Double], start: [Double], monthly: Double, wC: [Double]) -> [Double] {
        let A = X.A, M = X.M, P = X.P, mi = X.monthIdx
        let dd = (0..<A).map { a in log(1 + mu[a]) / TD - X.muD[a] }
        var out = [Double](repeating: 0, count: M * P), accU = [Double](repeating: 0, count: A)
        for p in 0..<P {
            for a in 0..<A { accU[a] = 0 }
            for k in 0..<M {
                let t = Double(mi[k]), contrib = k > 0 && k < M - 1
                var V = 0.0
                for a in 0..<A {
                    let U = exp((k > 0 ? Double(X.cg[a][k * P + p]) : 0) + dd[a] * t)
                    if contrib { accU[a] += wC[a] / U }
                    V += U * (start[a] + monthly * accU[a])
                }
                out[k * P + p] = V
            }
        }
        return out
    }

    /// 렌즈별 종목 연 기대수익 (웹 viewMu): "base" · "smooth" · 그 밖 = 내 관점 (추세 신뢰 trust%)
    static func viewMu(_ model: Model, A: Int, lens: String, trust: Double) -> [Double] {
        model.factors.prefix(A).map { f in lens == "base" ? f.base : lens == "smooth" ? f.smooth : blendMu(f.base, f.smooth, trust / 100) }
    }
    /// 시뮬레이션에 쓰는 요인별 기대수익 (웹: 설정 시나리오, 기본 내 관점)
    static func scenarioMu(_ model: Model, _ scenario: String?) -> [Double] {
        model.factors.map { f in scenario == "base" ? f.base : scenario == "smooth" ? f.smooth : f.blend }
    }
}

// MARK: 경로 묶음: 전망과 같은 몬테카를로 경로로 계획·구성의 확률을 센다 (웹 breakeven·reweight 와 같은 식)
/// 경로는 종목별 처음 금액 base 로 계산해 두고, 다른 금액이면 비율로 늘이거나 줄인다 (적립도 같은 비율로)
final class PathSet {
    let sim: MC.Sim
    let mu: [Double]          // 종목별 연 기대수익 (지금 관점)
    let base: [Double]        // 종목별 처음 금액 (원)
    let wC: [Double]          // 매달 넣는 돈을 나누는 비율 (구성 비중)
    let tM: [Double]          // 월 경계마다 오늘부터 개월
    let key: String
    private var cache: [String: [Double]] = [:]
    private var bandCache: [String: [[Double]]] = [:]

    init(sim: MC.Sim, mu: [Double], base: [Double], key: String) {
        self.sim = sim; self.mu = mu; self.base = base; self.key = key
        let s = base.reduce(0, +)
        wC = base.map { s > 0 ? $0 / s : 1 / Double(max(1, base.count)) }
        let s0 = MC.dayNum(sim.start)
        tM = sim.monthDates.map { Double(MC.dayNum($0) - s0) / 365.25 * 12 }
    }
    var P: Int { sim.P }
    var M: Int { sim.M }

    /// 지금 금액 V(원)에서, 매달 monthly(원)씩 구성 비중대로 넣을 때 월 경계마다 평가액 [k*P+p]
    func values(_ V: Double, monthly: Double = 0) -> [Double] {
        let ck = "\(V)|\(monthly)"
        if let v = cache[ck] { return v }
        let S = base.reduce(0, +)
        let v: [Double]
        if V > 0 && S > 0 {
            let f = V / S
            let raw = MC.values(sim, mu: mu, start: base, monthly: monthly / f, wC: wC)
            v = f == 1 ? raw : raw.map { $0 * f }
        } else {
            v = MC.values(sim, mu: mu, start: base.map { _ in 0 }, monthly: monthly, wC: wC)
        }
        if cache.count > 24 { cache = [:] }
        cache[ck] = v
        return v
    }
    /// 월 경계마다 5·25·50·75·95% (적립 없이)
    func bands(_ V: Double) -> [[Double]] {
        let ck = "\(V)"
        if let b = bandCache[ck] { return b }
        let b = MC.bandsOf(values(V), M: M, P: P)
        if bandCache.count > 8 { bandCache = [:] }
        bandCache[ck] = b
        return b
    }
    func k(months: Int) -> Int { MC.monthK(sim, months: months) }
    /// 기준선 line(k) 이상이 되는 첫 칸 (1부터, kMax 칸까지 없으면 -1)
    func firstHits(_ v: [Double], upTo kMax: Int, line: (Int) -> Double) -> [Int] {
        var out = [Int](repeating: -1, count: P)
        guard kMax >= 1 else { return out }
        let lines = (0...kMax).map(line)
        for p in 0..<P { for k in 1...kMax where v[k * P + p] >= lines[k] { out[p] = k; break } }
        return out
    }
    /// kMax 칸까지 한 번이라도 기준선 이상인 경로 비율
    func touch(_ v: [Double], upTo kMax: Int, line: (Int) -> Double) -> Double {
        Double(firstHits(v, upTo: kMax, line: line).filter { $0 >= 0 }.count) / Double(P)
    }
    /// k칸 평가액(minus 를 뺀 값)이 K 이상인 경로 비율
    func share(_ v: [Double], k: Int, minus: Double = 0, atLeast K: Double) -> Double {
        var n = 0; for p in 0..<P where v[k * P + p] - minus >= K { n += 1 }
        return Double(n) / Double(P)
    }
    func quantile(_ v: [Double], k: Int, _ q: Double, minus: Double = 0) -> Double {
        MC.quantileSorted((0..<P).map { v[k * P + $0] - minus }.sorted(), q)
    }
    /// 첫 도달 칸들의 가운데 (개월, 없으면 무한대)
    func medianMonths(_ hits: [Int]) -> Double {
        let ms = hits.map { $0 >= 0 ? (tM[$0]).rounded() : Double.infinity }.sorted()
        return ms[ms.count / 2]
    }
    /// 웹 req50 (reweight 와 같은 50번 이분법): 마지막 칸(kEnd) 평가액이 K 이상인 경로가 절반이 되는 월 적립 (원). 적립은 kEnd 앞 달까지
    func req50(_ V: Double, goal K: Double, kEnd: Int) -> Double {
        let v0 = values(V), v1 = values(V, monthly: 1e6), last = kEnd >= M - 1
        let nc = (0..<P).map { v0[kEnd * P + $0] }
        let an = (0..<P).map { (v1[kEnd * P + $0] - v0[kEnd * P + $0]) / 1e6 - (last || kEnd == 0 ? 0 : 1) }
        func ok(_ c: Double) -> Bool { var n = 0; for p in 0..<P where nc[p] + c * an[p] >= K { n += 1 }; return Double(n) / Double(P) >= 0.5 }
        if ok(0) { return 0 }
        var lo = 0.0, hi = 1e9
        for _ in 0..<50 { let c = (lo + hi) / 2; if ok(c) { hi = c } else { lo = c } }
        return hi
    }
}

extension KeyedDecodingContainer {
    /// 숫자 칸을 너그럽게: 숫자, 숫자 문자열, 참·거짓
    func mcNum(_ k: Key) -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: k) { return d }
        if let s = try? decodeIfPresent(String.self, forKey: k) { return MC.jsNumber(s) }
        if let b = try? decodeIfPresent(Bool.self, forKey: k) { return b ? 1 : 0 }
        return nil
    }
    func mcStr(_ k: Key) -> String? {
        if let s = try? decodeIfPresent(String.self, forKey: k) { return s }
        if let d = try? decodeIfPresent(Double.self, forKey: k) { return String(d) }
        return nil
    }
}
