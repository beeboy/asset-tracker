import Foundation

// 파도 타기 엔진 (화면 없음). 내 자산 3년 흐름에서 40거래일 코스를 잘라 타기·내리기를 판정한다.
// 값 타입 하나로 두고 화면이 tick 을 불러 굴린다 (약 0.5초 = 1거래일, 20초 = 40거래일).

struct WaveEngine {
    // MARK: 섬 · 등급 · 이벤트

    enum Island: String, CaseIterable {
        case nap, climb, coaster, choppy
        var name: String {
            switch self {
            case .nap: return "낮잠 해변"
            case .climb: return "올라올라 언덕"
            case .coaster: return "롤러코스터 만"
            case .choppy: return "멀미 주의 바다"
            }
        }
        var line: String {
            switch self {
            case .nap: return "파도가 순해요. 보드 위에서 졸지만 마세요."
            case .climb: return "계속 오르막이에요. 너무 일찍 내리면 아까워요."
            case .coaster: return "한 번 크게 꺼져요. 바닥을 잘 노려 보세요."
            case .choppy: return "작은 파도가 쉴 새 없이 와요."
            }
        }
    }
    enum Level: String { case easy, hard
        var name: String { self == .easy ? "쉬움" : "어려움" }
    }
    enum EndReason: Equatable { case time, hearts }
    /// 2·3·4번(화면·소리·말풍선)이 붙일 이벤트
    enum Event: Equatable {
        /// 탑승. perfect = 바닥 그날 탔다 ('딱')
        case board(day: Int, perfect: Bool)
        /// 내림. gain = 탄 동안 수익률, points = 이번에 번 점수(보너스 포함)
        case leave(day: Int, gain: Double, points: Int)
        /// 탄 채 급락에 휘말림. hearts = 남은 하트
        case splash(day: Int, drop: Double, hearts: Int)
        /// 아주 잔잔한 하루
        case calm(day: Int)
        /// 번 채로 내린 게 n 번 이어짐 (2번부터)
        case streak(Int)
        case end(EndReason)
    }

    // MARK: 코스

    struct Course: Equatable {
        let dates: [String]      // "yyyy-MM-dd", 거래일
        let values: [Double]     // 그날 평가액 (원)
        let island: Island
        let level: Level
        var startDate: String { dates.first ?? "" }
        var count: Int { values.count }
    }

    static let days = 40
    static let secondsPerDay = 0.5
    static let maxHearts = 3
    static let perfectBonus = 50       // '딱' 보너스 점수
    static let streakBonus = 20        // 연속 성공 한 번 더할 때마다

    /// 3년 라인에서 무작위 시작 40거래일 코스. 라인이 짧거나 평평하면 nil
    static func course<G: RandomNumberGenerator>(from line: [(d: String, v: Double)], length: Int = days, using g: inout G) -> Course? {
        guard line.count >= length, length >= 2 else { return nil }
        let s = Int.random(in: 0...(line.count - length), using: &g)
        let part = line[s..<(s + length)]
        let vs = part.map(\.v)
        guard let lo = vs.min(), lo > 0, vs.max() != lo else { return nil }
        let (i, lv) = classify(vs)
        return Course(dates: part.map(\.d), values: vs, island: i, level: lv)
    }
    static func course(from line: [(d: String, v: Double)], length: Int = days) -> Course? {
        var g = SystemRandomNumberGenerator()
        return course(from: line, length: length, using: &g)
    }

    /// 변동성·모양으로 섬과 등급을 정한다
    static func classify(_ v: [Double]) -> (Island, Level) {
        let r = rets(v)
        let sd = stdev(r)
        let total = v.last! / v.first! - 1
        var peak = v[0], dd = 0.0
        for x in v { peak = max(peak, x); dd = max(dd, 1 - x / peak) }
        let flips = zip(r, r.dropFirst()).filter { $0 * $1 < 0 }.count
        let flipRate = r.count > 1 ? Double(flips) / Double(r.count - 1) : 0
        let island: Island
        if dd >= 0.06 { island = .coaster }
        else if total >= 0.04 && dd < 0.04 { island = .climb }
        else if sd >= 0.01 || flipRate >= 0.6 { island = .choppy }
        else { island = .nap }
        let hard = sd >= 0.012 || dd >= 0.08
        return (island, hard ? .hard : .easy)
    }

    static func rets(_ v: [Double]) -> [Double] { zip(v, v.dropFirst()).map { $1 / $0 - 1 } }
    static func stdev(_ r: [Double]) -> Double {
        guard r.count > 1 else { return 0 }
        let m = r.reduce(0, +) / Double(r.count)
        return (r.reduce(0) { $0 + ($1 - m) * ($1 - m) } / Double(r.count - 1)).squareRoot()
    }

    // MARK: 판 상태

    let course: Course
    private(set) var day = 0              // 지금 거래일 (0...count-1)
    private(set) var elapsed = 0.0        // 지난 초
    private(set) var hearts = maxHearts
    private(set) var score = 0
    private(set) var boardedAt: Int?      // 탄 날 (안 타고 있으면 nil)
    private(set) var boardedPerfect = false
    private(set) var streak = 0
    private(set) var ended: EndReason?
    /// 탄 채 하루 낙폭이 이보다 크면 풍덩 (코스 변동성 2배, 최소 1.5%)
    let splashDrop: Double

    init(course: Course) {
        self.course = course
        splashDrop = max(0.015, 2 * Self.stdev(Self.rets(course.values)))
    }

    var duration: Double { Double(course.count) * Self.secondsPerDay }
    var timeLeft: Double { max(0, duration - elapsed) }
    var riding: Bool { boardedAt != nil }
    var isOver: Bool { ended != nil }
    var date: String { course.dates[day] }
    var value: Double { course.values[day] }
    /// 탄 뒤 지금까지 수익률 (안 타면 0)
    var rideGain: Double { boardedAt.map { value / course.values[$0] - 1 } ?? 0 }

    /// 그날이 앞뒤 3거래일 안에서 가장 낮으면 바닥
    func isBottom(_ i: Int) -> Bool {
        let a = max(0, i - 3), b = min(course.count - 1, i + 3)
        return course.values[a...b].allSatisfy { $0 >= course.values[i] }
    }

    // MARK: 조작

    mutating func board() -> [Event] {
        guard !isOver, !riding else { return [] }
        boardedAt = day
        boardedPerfect = isBottom(day)
        return [.board(day: day, perfect: boardedPerfect)]
    }

    mutating func leave() -> [Event] {
        guard !isOver, let b = boardedAt else { return [] }
        let gain = value / course.values[b] - 1
        var pts = Int((gain * 10000).rounded())        // 0.01% = 1점
        if boardedPerfect && gain > 0 { pts += Self.perfectBonus }
        var ev: [Event] = []
        if gain > 0 {
            streak += 1
            if streak >= 2 { pts += Self.streakBonus * (streak - 1); ev.append(.streak(streak)) }
        } else { streak = 0 }
        score += pts
        boardedAt = nil; boardedPerfect = false
        return [.leave(day: day, gain: gain, points: pts)] + ev
    }

    /// 화면이 매 프레임 부른다. dt 초만큼 시간을 흘리고 넘어간 거래일마다 판정한다
    mutating func tick(_ dt: Double) -> [Event] {
        guard !isOver else { return [] }
        elapsed += max(0, dt)
        var ev: [Event] = []
        let target = min(course.count - 1, Int(elapsed / Self.secondsPerDay))
        while day < target && !isOver {
            day += 1
            let r = course.values[day] / course.values[day - 1] - 1
            if riding && r <= -splashDrop {
                // 탄 채 휘말리면 그날 값으로 강제로 내려지고 하트 하나를 잃는다
                let b = boardedAt!
                score += Int(((value / course.values[b] - 1) * 10000).rounded())
                boardedAt = nil; boardedPerfect = false; streak = 0
                hearts -= 1
                ev.append(.splash(day: day, drop: r, hearts: hearts))
                if hearts <= 0 { ev += finish(.hearts) }
            } else if abs(r) < 0.0015 {
                ev.append(.calm(day: day))
            }
        }
        if !isOver && elapsed >= duration { ev += finish(.time) }
        return ev
    }

    private mutating func finish(_ why: EndReason) -> [Event] {
        var ev: [Event] = []
        if riding && why == .time { ev += leave() }   // 시간 끝: 타고 있던 건 그날 값으로 내린다
        ended = why
        return ev + [.end(why)]
    }
}

extension AppModel {
    /// 파도 타기용 3년 자산 라인: 지금 수량 × 지난 3년 거래일 종가 (달러 종목은 그날 원/달러), 현금은 그대로 더한다.
    /// 분석 화면 pathSeries 와 같은 자료·식이고 수량만 지금 수량으로 고정한다
    var waveLine: [(d: String, v: Double)] {
        let today = Day.today, fxH = PriceHistory.of("FX"), cash = cashKrw
        let from = Day.str(Calendar.current.date(byAdding: .year, value: -3, to: Date()) ?? Date())
        let hs = rows.map { (r: $0, h: PriceHistory.of($0.id)) }
        var ds = Set<String>()
        for x in hs { if let h = x.h { for d in h.dates.reversed() { if d < from { break }; if d < today { ds.insert(d) } } } }
        return ds.sorted().map { d in
            let v = hs.reduce(0.0) { a, x in
                guard let h = x.h else { return a + x.r.value }
                guard let px = h.on(d) else { return a }
                return a + px * x.r.h.qty * (x.r.sym.currency == .usd ? (fxH?.on(d) ?? Market.shared.valFx) : 1)
            }
            return (d: d, v: v + cash)
        }
    }
    /// 새 파도 코스 (라인이 짧으면 nil)
    func waveCourse() -> WaveEngine.Course? { WaveEngine.course(from: waveLine) }
}
