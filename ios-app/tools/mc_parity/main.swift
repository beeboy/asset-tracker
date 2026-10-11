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
    struct C: Encodable { var name: String; var nPaths: Int; var ms: Double; var model: M; var sim: R; var simNo: R; var views: [String: R] }
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
    let f = model.factors.map { Out.F(key: $0.key, vol: $0.vol, base: $0.base, smooth: $0.smooth, blend: $0.blend, evVar: $0.evVar) }
    out.cases.append(.init(name: c.name, nPaths: c.nPaths, ms: ms, model: .init(days: model.days.count, events: model.eventCount, monthDates: model.monthDates, factors: f),
                           sim: R(ev.summary), simNo: R(no.summary), views: views))
    print("앱 \(c.name): 경로 \(c.nPaths) · \(model.days.count)거래일 · 사건 \(model.eventCount)회 · 시뮬레이션 2번 \(Int(ms))ms · 다시 무게 주기 1번 \(Int(rw))ms · 목표 확률 \(String(format: "%.1f", ev.summary.pGoal * 100))%")
}
try JSONEncoder().encode(out).write(to: URL(fileURLWithPath: dir + "/app.json"))
