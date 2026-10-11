import Foundation
import Observation

// 앱 전망: 웹과 같은 몬테카를로(MonteCarlo.swift)를 하루 한 번 화면 밖에서 돌리고 경로를 저장해 둔다 (웹 fcdSig 처럼
// 오늘 날짜·종목·수량·목표일·사이트 통계·사건이 그대로면 다시 계산하지 않음). 렌즈·추세 신뢰·월 적립·목표 금액은
// 같은 경로에 기대수익만 바꿔 다시 계산한다 (웹 reweight). 계산이 끝나기 전에는 AppModel 의 닫힌 식 값을 잠깐 보여 준다.
@Observable
final class ForecastEngine {
    static let shared = ForecastEngine()

    struct Job {
        var key: String
        var holdings: [MC.Holding]
        var stats: MC.Stats
        var settings: MC.Settings
        var events: [MC.Event]
        var start: String
        var horizon: String
        var goal: Double
        var monthly: Double
    }
    /// 계산해 둔 경로. ev = 사건 반영, no = 사건 없이 (사건이 없으면 nil)
    struct Run: Codable {
        var key: String
        var factors: [MC.Factor]
        var events: Int
        var ev: MC.Sim
        var no: MC.Sim?
        var ms: Double
    }

    private(set) var run: Run?
    /// 계산 중인 job 키 (같은 계산을 두 번 시작하지 않게)
    @ObservationIgnored private var pending: String?
    @ObservationIgnored private var views: [String: MC.Result] = [:]
    private static let file = "forecast_mc.plist"

    private init() {
        // 어제 계산한 것이라도 불러 둔다 (키가 다르면 쓰지 않고 새로 계산)
        if let d = Store.readData(Self.file), let r = try? PropertyListDecoder().decode(Run.self, from: d) { run = r }
    }

    /// 이 job 의 경로가 없으면 화면 밖에서 계산을 시작한다
    func ensure(_ job: Job) {
        if run?.key == job.key || pending == job.key { return }
        pending = job.key
        Task.detached(priority: .userInitiated) {
            let r = Self.compute(job)
            await MainActor.run {
                guard self.pending == job.key else { return }   // 그사이 다른 계산이 시작됐으면 버린다
                self.pending = nil
                guard let r else { return }
                self.views = [:]
                self.run = r
                if let d = try? PropertyListEncoder().encode(r) { DispatchQueue.global(qos: .utility).async { Store.writeData(d, Self.file) } }
            }
        }
    }

    static func compute(_ j: Job) -> Run? {
        guard let model = MC.build(holdings: j.holdings, stats: j.stats, settings: j.settings, events: j.events, start: j.start, goal: j.horizon) else { return nil }
        let t0 = Date(), s = j.settings, mu = MC.scenarioMu(model, s.scenario)
        let sim = { (ev: Bool) in MC.simulate(model, holdings: j.holdings, mu: mu, nPaths: s.nPaths, seed: s.seedU32, goal: j.goal, monthly: j.monthly, withEvents: ev, dof: s.dof) }
        let ev = sim(true), no = model.eventCount > 0 ? sim(false) : nil
        return Run(key: j.key, factors: model.factors, events: model.eventCount, ev: ev, no: no, ms: Date().timeIntervalSince(t0) * 1000)
    }

    /// 렌즈(base·smooth·mine)와 추세 신뢰로 본 결과. withEvents 가 거짓이면 사건 없는 경로 (웹 '미반영')
    func view(key: String, lens: String, trust: Double, goal: Double, monthly: Double, withEvents: Bool) -> MC.Result? {
        guard let r = run, r.key == key else { return nil }
        let sim = withEvents ? r.ev : (r.no ?? r.ev)
        let vk = "\(lens)|\(trust)|\(goal)|\(monthly)|\(withEvents)"
        if let v = views[vk] { return v }
        let A = sim.A
        let mu = r.factors.prefix(A).map { f in lens == "base" ? f.base : lens == "smooth" ? f.smooth : MC.blendMu(f.base, f.smooth, trust / 100) }
        let v = MC.reweight(sim, mu: Array(mu), goal: goal, monthly: monthly)
        if views.count > 40 { views = [:] }
        views[vk] = v
        return v
    }

    /// 짧은 지문 (FNV-1a). 앱을 다시 켜도 같은 값이어야 해서 Hasher 대신
    static func fnv(_ s: String) -> String {
        var h: UInt64 = 0xcbf29ce484222325
        for b in s.utf8 { h = (h ^ UInt64(b)) &* 0x100000001b3 }
        return String(h, radix: 36)
    }
}

// 경로 저장: cg(종목별 누적 로그수익, Float32)는 바이트 그대로
extension MC.Sim: Codable {
    enum CodingKeys: String, CodingKey { case A, M, P, cg, muD, monthIdx, monthDates, v0, start, summary }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        let blobs = try c.decode([Data].self, forKey: .cg)
        self.init(A: try c.decode(Int.self, forKey: .A), M: try c.decode(Int.self, forKey: .M), P: try c.decode(Int.self, forKey: .P),
                  cg: blobs.map { b in var a = [Float](repeating: 0, count: b.count / 4); _ = a.withUnsafeMutableBytes { b.copyBytes(to: $0) }; return a },
                  muD: try c.decode([Double].self, forKey: .muD), monthIdx: try c.decode([Int].self, forKey: .monthIdx),
                  monthDates: try c.decode([String].self, forKey: .monthDates), v0: try c.decode([Double].self, forKey: .v0),
                  start: try c.decode(String.self, forKey: .start), summary: try c.decode(MC.Result.self, forKey: .summary))
        guard cg.count == A, cg.allSatisfy({ $0.count == M * P }) else { throw DecodingError.dataCorruptedError(forKey: .cg, in: c, debugDescription: "크기") }
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(A, forKey: .A); try c.encode(M, forKey: .M); try c.encode(P, forKey: .P)
        try c.encode(cg.map { a in a.withUnsafeBufferPointer { Data(buffer: $0) } }, forKey: .cg)
        try c.encode(muD, forKey: .muD); try c.encode(monthIdx, forKey: .monthIdx); try c.encode(monthDates, forKey: .monthDates)
        try c.encode(v0, forKey: .v0); try c.encode(start, forKey: .start); try c.encode(summary, forKey: .summary)
    }
}
