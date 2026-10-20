import Foundation
import Observation

/// 무거운 계산은 앱이 화면에 떠 있을 때만 (App.swift 가 scenePhase 로 켜고 끈다). 뒤로 가면 돌던 계산도 멈추고 다시 켜질 때 이어 간다
enum Compute {
    private static let lock = NSLock()
    private static var on = true
    static var active: Bool { lock.lock(); defer { lock.unlock() }; return on }
    static func setActive(_ a: Bool) {
        lock.lock(); let was = on; on = a; lock.unlock()
        if a && !was { ForecastEngine.shared.resume(); PathRuns.shared.resume() }
    }
    /// 계산 하나에 붙이는 멈춤 표시: 경로 32개마다 묻고, 멈췄으면 hit (그 결과는 버린다)
    final class Halt {
        var hit = false
        lazy var check: () -> Bool = { [unowned self] in if !Compute.active { self.hit = true }; return self.hit }
    }
}

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
        /// PathRuns: 같은 구성·기간 묶음 (비중만 뺀 키)과 비중. 시세가 조금 움직여 비중이 바뀌어도 같은 날엔 다시 계산하지 않는다
        var group: String = ""
        var w: [Double] = []
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
    /// 계산 중인 job 키 (한 번에 하나만 돈다)
    @ObservationIgnored private var pending: String?
    /// 계산 중에 들어온 다음 job (끝나면 이어서). 앱이 뒤로 가서 멈춘 job 도 여기서 기다린다
    @ObservationIgnored private var waiting: Job?
    /// 모형을 못 만든 job (같은 job 을 다시 돌리지 않는다)
    @ObservationIgnored private var failed: Set<String> = []
    @ObservationIgnored private var views: [String: MC.Result] = [:]
    private static let file = "forecast_mc.plist"

    private init() {
        // 어제 계산한 것이라도 불러 둔다 (키가 다르면 쓰지 않고 새로 계산)
        if let d = Store.readData(Self.file), let r = try? PropertyListDecoder().decode(Run.self, from: d) { run = r }
    }

    /// 이 job 의 경로가 없으면 화면 밖에서 계산을 시작한다. 이미 하나가 돌고 있으면 끝난 뒤에 (돌던 계산을 버리고 새로 시작하지 않는다)
    func ensure(_ job: Job) {
        if run?.key == job.key || pending == job.key || failed.contains(job.key) { return }
        guard pending == nil, Compute.active else { waiting = job; return }
        waiting = nil
        pending = job.key
        let halt = Compute.Halt()
        Task.detached(priority: .userInitiated) {
            let r = Self.compute(job, stop: halt.check)
            let halted = halt.hit
            await MainActor.run {
                self.pending = nil
                if let r, !halted {
                    self.views = [:]
                    self.run = r
                    if let d = try? PropertyListEncoder().encode(r) { DispatchQueue.global(qos: .utility).async { Store.writeData(d, Self.file) } }
                } else if halted {
                    if self.waiting == nil { self.waiting = job }     // 다시 켜지면 처음부터
                } else { self.failed.insert(job.key) }
                if let w = self.waiting { self.waiting = nil; self.ensure(w) }
            }
        }
    }
    /// 앱이 다시 화면에 뜨면 멈춘 계산을 이어 간다
    func resume() { if let w = waiting { waiting = nil; ensure(w) } }

    static func compute(_ j: Job, stop: (() -> Bool)? = nil) -> Run? {
        guard let model = MC.build(holdings: j.holdings, stats: j.stats, settings: j.settings, events: j.events, start: j.start, goal: j.horizon) else { return nil }
        let t0 = Date(), s = j.settings, mu = MC.scenarioMu(model, s.scenario)
        let sim = { (ev: Bool) in MC.simulate(model, holdings: j.holdings, mu: mu, nPaths: s.nPaths, seed: s.seedU32, goal: j.goal, monthly: j.monthly, withEvents: ev, dof: s.dof, stop: stop) }
        let ev = sim(true), no = model.eventCount > 0 && stop?() != true ? sim(false) : nil
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
        guard cg.count == A, cg.allSatisfy({ $0.count == M * P }) else { throw DecodingError.dataCorruptedError(forKey: .cg, in: c, debugDescription: String(localized: "크기")) }
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(A, forKey: .A); try c.encode(M, forKey: .M); try c.encode(P, forKey: .P)
        try c.encode(cg.map { a in a.withUnsafeBufferPointer { Data(buffer: $0) } }, forKey: .cg)
        try c.encode(muD, forKey: .muD); try c.encode(monthIdx, forKey: .monthIdx); try c.encode(monthDates, forKey: .monthDates)
        try c.encode(v0, forKey: .v0); try c.encode(start, forKey: .start); try c.encode(summary, forKey: .summary)
    }
}

// 계획 4안·구성 비교·목표 자동 고르기용 경로: 전망과 같은 몬테카를로(같은 사이트 통계·설정·사건, 사건 반영)를
// 구성마다 한 번씩 화면 밖에서 돌려 둔다. 지금 내 구성은 전망 경로(ForecastEngine)를 그대로 쓰고, 여기서는 그 밖의 구성만.
// 하루 한 번 (키에 오늘 날짜), 차례로 하나씩 계산한다. 시세가 움직여 비중이 조금 바뀐 구성은 그날 계산해 둔 경로를 그대로 쓴다
// (전에는 1분마다 시세가 바뀔 때마다 새 구성으로 줄을 세워 하루 종일 계산이 이어졌다)
@Observable
final class PathRuns {
    static let shared = PathRuns()
    struct Run: Codable {
        var key: String; var factors: [MC.Factor]; var sim: MC.Sim
        var group: String?; var w: [Double]?; var ms: Double?; var at: Double?
    }
    /// 비중이 이만큼 안에서 움직이면 같은 구성으로 본다 (각 종목 2%p)
    static let near = 0.02

    private(set) var runs: [String: Run] = [:]
    /// 오늘 다른 구성 경로 계산에 쓴 시간 (ms, 개발자 줄)
    var msToday: Double { runs.values.reduce(0) { $0 + ($1.ms ?? 0) } }
    /// 경로 하나가 끝날 때마다 (목표 자동 고르기 다시 하기)
    @ObservationIgnored var onRun: (() -> Void)?
    @ObservationIgnored private var queue: [ForecastEngine.Job] = []
    @ObservationIgnored private var busy = false
    @ObservationIgnored private var failed: Set<String> = []     // 모형을 못 만든 job (다시 줄 세우지 않는다)
    private static let file = "paths_mc.plist"

    private init() {
        if let d = Store.readData(Self.file), let r = try? PropertyListDecoder().decode([String: Run].self, from: d) {
            let today = Day.today
            runs = r.filter { $0.key.hasPrefix(today) }
        }
    }

    static func close(_ a: [Double]?, _ b: [Double]) -> Bool {
        guard let a, a.count == b.count else { return false }
        return zip(a, b).allSatisfy { abs($0 - $1) <= near }
    }

    /// 계산해 둔 경로 (같은 구성·기간이면 비중이 조금 달라도). 없으면 줄에 세우고 nil (끝나면 runs 가 바뀌어 화면이 다시 그려진다)
    func get(_ job: ForecastEngine.Job) -> Run? {
        if let r = runs[job.key] { return r }
        if !job.group.isEmpty, let r = runs.values.first(where: { $0.group == job.group && Self.close($0.w, job.w) }) { return r }
        if failed.contains(job.key) { return nil }
        if let i = queue.firstIndex(where: { $0.key == job.key || (!job.group.isEmpty && $0.group == job.group) }) {
            if !Self.close(queue[i].w, job.w) { queue[i] = job }   // 같은 묶음은 줄에 하나만 (최신 비중으로)
        } else {
            queue.append(job)
        }
        next()
        return nil
    }

    static func compute(_ job: ForecastEngine.Job, stop: (() -> Bool)? = nil) -> Run? {
        guard let model = MC.build(holdings: job.holdings, stats: job.stats, settings: job.settings, events: job.events, start: job.start, goal: job.horizon) else { return nil }
        let s = job.settings, t0 = Date()
        let sim = MC.simulate(model, holdings: job.holdings, mu: MC.scenarioMu(model, s.scenario), nPaths: s.nPaths, seed: s.seedU32,
                              goal: .infinity, monthly: 0, withEvents: true, dof: s.dof, stop: stop)
        return Run(key: job.key, factors: model.factors, sim: sim, group: job.group, w: job.w,
                   ms: Date().timeIntervalSince(t0) * 1000, at: Date().timeIntervalSince1970)
    }

    func resume() { next() }

    private func next() {
        guard !busy, !queue.isEmpty, Compute.active else { return }
        busy = true
        let job = queue.removeFirst()
        let halt = Compute.Halt()
        Task.detached(priority: .utility) {
            let r = Self.compute(job, stop: halt.check)
            let halted = halt.hit
            await MainActor.run {
                self.busy = false
                if halted {
                    self.queue.insert(job, at: 0)     // 다시 켜지면 이어서
                    return
                }
                if let r {
                    let today = Day.today
                    var all = self.runs.filter { $0.key.hasPrefix(today) }
                    // 하루에 쌓이는 경로는 16개까지: 가장 오래된 것부터 뺀다 (화면이 쓰는 구성은 8개 안팎)
                    while all.count >= 16, let k = all.min(by: { ($0.value.at ?? 0) < ($1.value.at ?? 0) })?.key { all[k] = nil }
                    all[r.key] = r
                    self.runs = all
                    if let d = try? PropertyListEncoder().encode(all) { DispatchQueue.global(qos: .utility).async { Store.writeData(d, Self.file) } }
                    self.onRun?()
                } else { self.failed.insert(job.key) }
                self.next()
            }
        }
    }
}
