import Foundation

// 웹·앱 전망 비교 도구 (앱 쪽): tools/mc_parity.mjs gen 이 만든 input.json 으로 앱의 MonteCarlo.swift 를 돌려 app.json 을 쓴다.
// 컴파일: swiftc -O ios-app/Naeilo/Data/MonteCarlo.swift ios-app/tools/mc_parity/main.swift -o mc_parity
// 사용: ./mc_parity <폴더>   (GitHub Actions ios.yml)
struct Input: Decodable {
    struct Case: Decodable {
        struct H: Decodable { var ticker: String; var ccy: String; var valueKrw: Double; var price0: Double }
        var name: String; var holdings: [H]; var events: [MC.Event]; var goal: Double; var goalDate: String; var monthly: Double; var nPaths: Int; var trust: Double
    }
    var stats: MC.Stats; var start: String; var cases: [Case]
}
struct R: Encodable {
    var p_goal, p_touch, mdd_median: Double
    var terminal: [String: Double]
    var bands: [String: [Double]]
    var byYear: [Y]
    struct Y: Encodable { var year: Int; var date: String; var p: Double }
    init(_ r: MC.Result) {
        p_goal = r.pGoal; p_touch = r.pTouch; mdd_median = r.mddMedian
        let t = r.terminal
        terminal = ["p5": t.p5, "p25": t.p25, "p50": t.p50, "p75": t.p75, "p95": t.p95, "mean": t.mean]
        bands = Dictionary(uniqueKeysWithValues: zip(["p5", "p25", "p50", "p75", "p95"], r.bands))
        byYear = r.byYear.map { Y(year: $0.year, date: $0.date, p: $0.p) }
    }
}
struct Out: Encodable {
    struct F: Encodable { var key: String; var vol, base, smooth, blend, evVar: Double }
    struct M: Encodable { var days: Int; var events: Int; var monthDates: [String]; var factors: [F] }
    struct B: Encodable { var all: [Double?]; var first: [Double?]; var req50: Double }
    struct C: Encodable { var name: String; var nPaths: Int; var trust: Int; var ms: Double; var model: M; var sim: R; var simNo: R; var views: [String: R]; var bep: B; var paths: R }
    var cases: [C] = []
}

let dir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let inp = try JSONDecoder().decode(Input.self, from: Data(contentsOf: URL(fileURLWithPath: dir + "/input.json")))
let settings = inp.stats.model ?? MC.Settings()
var out = Out()
for c in inp.cases {
    let hs = c.holdings.map { MC.Holding(key: $0.ticker, ccy: $0.ccy, valueKrw: $0.valueKrw, price0: $0.price0) }
    guard let model = MC.build(holdings: hs, stats: inp.stats, settings: settings, events: c.events, start: inp.start, goal: c.goalDate) else {
        print("모형을 못 만듦: \(c.name)"); exit(1)
    }
    let t0 = Date()
    let mu = MC.scenarioMu(model, settings.scenario)
    let ev = MC.simulate(model, holdings: hs, mu: mu, nPaths: c.nPaths, seed: settings.seedU32, goal: c.goal, monthly: c.monthly, withEvents: true, dof: settings.dof)
    let no = MC.simulate(model, holdings: hs, mu: mu, nPaths: c.nPaths, seed: settings.seedU32, goal: c.goal, monthly: c.monthly, withEvents: false, dof: settings.dof)
    let ms = Date().timeIntervalSince(t0) * 1000
    var views: [String: R] = [:]
    let t1 = Date()
    for (nm, s) in [("ev", ev), ("no", no)] {
        for key in ["base", "smooth", String(Int(c.trust))] {
            let m = MC.viewMu(model, A: hs.count, lens: key, trust: c.trust)
            views[nm + ":" + key] = R(MC.reweight(s, mu: m, goal: c.goal, monthly: c.monthly))
        }
    }
    let rw = Date().timeIntervalSince(t1) * 1000 / 6
    // 앱 회복 확률 (MC.breakeven) · 계획·목표에 쓰는 PathSet (values·req50) 을 웹 breakeven·reweight 와 비교
    let muT = MC.viewMu(model, A: hs.count, lens: String(Int(c.trust)), trust: c.trust), V0 = ev.v0.reduce(0, +)
    let ps = PathSet(sim: ev, mu: muT, base: ev.v0, key: c.name)
    let vals = ps.values(V0, monthly: c.monthly), M = ev.M, P = ev.P
    let bands = MC.bandsOf(vals, M: M, P: P), term = Array(vals[((M - 1) * P)...]).sorted()
    var hit = [Int](repeating: -1, count: P)
    for p in 0..<P { for k in 1..<M where vals[k * P + p] >= c.goal { hit[p] = k; break } }
    var cum: [Double] = [], run = 0
    for k in 0..<M { run += hit.filter { $0 == k }.count; cum.append(Double(run) / Double(P)) }
    var by: [MC.Year] = [], y = 1
    while true { let k = MC.monthK(ev, months: 12 * y); by.append(MC.Year(year: y, date: ev.monthDates[k], p: cum[k], k: k)); if k >= M - 1 { break }; y += 1 }
    var mdd = [Double](repeating: 0, count: P)
    for p in 0..<P { var peak = V0, d = 0.0; for k in 0..<M { let v = vals[k * P + p]; if v > peak { peak = v } else { d = min(d, v / peak - 1) } }; mdd[p] = d }
    let pr = MC.Result(V0: V0, bands: bands, pGoal: ps.share(vals, k: M - 1, atLeast: c.goal), pTouch: Double(hit.filter { $0 >= 0 }.count) / Double(P),
                       terminal: MC.terminal(term), mddMedian: MC.quantileSorted(mdd.sorted(), 0.5), byYear: by, cumHit: cum)
    let bep = Out.B(all: MC.breakeven(ev, idx: Array(0..<hs.count), basis: V0 * 1.15, mu: muT, months: [6, 12, 36]),
                    first: MC.breakeven(ev, idx: [0], basis: hs[0].valueKrw * 1.1, mu: muT, months: [6, 12, 36]),
                    req50: ps.req50(V0, goal: c.goal, kEnd: M - 1))
    let f = model.factors.map { Out.F(key: $0.key, vol: $0.vol, base: $0.base, smooth: $0.smooth, blend: $0.blend, evVar: $0.evVar) }
    out.cases.append(.init(name: c.name, nPaths: c.nPaths, trust: Int(c.trust), ms: ms, model: .init(days: model.days.count, events: model.eventCount, monthDates: model.monthDates, factors: f),
                           sim: R(ev.summary), simNo: R(no.summary), views: views, bep: bep, paths: R(pr)))
    print("앱 \(c.name): 경로 \(c.nPaths) · \(model.days.count)거래일 · 사건 \(model.eventCount)회 · 시뮬레이션 2번 \(Int(ms))ms · 다시 무게 주기 1번 \(Int(rw))ms · 목표 확률 \(String(format: "%.1f", ev.summary.pGoal * 100))%")
}
try JSONEncoder().encode(out).write(to: URL(fileURLWithPath: dir + "/app.json"))

// 시간 재기 (폰 발열 점검): 개발자 구성으로 목표일 전망(3년, 사건 반영 + 사건 없이)과 목표 자동 고르기용 10년 경로를
// 경로 3,000개(사이트 기본)·10,000개로. 폰은 이 맥 러너보다 2~4배 느리다고 보면 된다
if let c = inp.cases.first(where: { $0.name == "dev" }) {
    let hs = c.holdings.map { MC.Holding(key: $0.ticker, ccy: $0.ccy, valueKrw: $0.valueKrw, price0: $0.price0) }
    for (label, goalDate) in [("3년", c.goalDate), ("10년", MC.addMonths(inp.start, 120))] {
        guard let model = MC.build(holdings: hs, stats: inp.stats, settings: settings, events: c.events, start: inp.start, goal: goalDate) else { continue }
        let mu = MC.scenarioMu(model, settings.scenario)
        for P in [3000, 10000] {
            let t0 = Date()
            _ = MC.simulate(model, holdings: hs, mu: mu, nPaths: P, seed: settings.seedU32, goal: c.goal, monthly: 0, withEvents: true, dof: settings.dof)
            let ms = Date().timeIntervalSince(t0) * 1000
            print("시간 \(label): 경로 \(P) · 사건 \(model.eventCount)회 · 사건 반영 1번 \(Int(ms))ms")
        }
        // 멈춤: 바로 멈추라고 하면 경로 하나도 돌지 않고 끝나야 한다
        let t1 = Date()
        _ = MC.simulate(model, holdings: hs, mu: mu, nPaths: 10000, seed: settings.seedU32, goal: c.goal, monthly: 0, withEvents: true, dof: settings.dof, stop: { true })
        let stopMs = Date().timeIntervalSince(t1) * 1000
        print("멈춤 \(label): \(Int(stopMs))ms")
        if stopMs > 500 { print("멈춤이 늦음"); exit(1) }
    }
}
