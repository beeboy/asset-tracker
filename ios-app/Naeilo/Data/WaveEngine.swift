import Foundation

// 파도 타기 엔진 (화면 없음). 내 자산 3년 흐름에서 40거래일 코스를 잘라 타기·내리기를 판정한다.
// 값 타입 하나로 두고 화면이 tick 을 불러 굴린다 (약 0.5초 = 1거래일, 20초 = 40거래일).

struct WaveEngine {
    // MARK: 섬 · 등급 · 이벤트

    enum Island: String, CaseIterable {
        case nap, climb, coaster, choppy
        var name: String {
            switch self {
            case .nap: return String(localized: "낮잠 해변")
            case .climb: return String(localized: "올라올라 언덕")
            case .coaster: return String(localized: "롤러코스터 만")
            case .choppy: return String(localized: "멀미 주의 바다")
            }
        }
        var line: String {
            switch self {
            case .nap: return String(localized: "파도가 순해요. 보드 위에서 졸지만 마세요.")
            case .climb: return String(localized: "계속 오르막이에요. 너무 일찍 내리면 아까워요.")
            case .coaster: return String(localized: "한 번 크게 꺼져요. 바닥을 잘 노려 보세요.")
            case .choppy: return String(localized: "작은 파도가 쉴 새 없이 와요.")
            }
        }
    }
    enum Level: String { case easy, hard
        var name: String { self == .easy ? String(localized: "쉬움") : String(localized: "어려움") }
    }
    enum EndReason: Equatable { case time, hearts }
    /// 파도 위에 떠 있는 아이템 (한 판에 1~2개, 탄 채 지나가면 먹는다)
    enum Item: CaseIterable {
        case lighthouse, turtle, vest, dolphin, rocket, snail, clock
        var icon: String {
            switch self {
            case .lighthouse: return "🗼"; case .turtle: return "🐢"; case .vest: return "🛟"; case .dolphin: return "🐬"
            case .rocket: return "🚀"; case .snail: return "🐌"; case .clock: return "⏰"
            }
        }
        var name: String {
            switch self {
            case .lighthouse: return String(localized: "등대! 바닥이 보여요")
            case .turtle: return String(localized: "거북이! 느릿느릿")
            case .vest: return String(localized: "구명조끼!")
            case .dolphin: return String(localized: "돌고래! 다음 점프 2배")
            case .rocket: return String(localized: "로켓! 빨라지고 점수 1.5배")
            case .snail: return String(localized: "달팽이! 아주 느릿느릿")
            case .clock: return String(localized: "시계! +3초")
            }
        }
    }
    /// 판 미션 (한 판에 3개, 하나 깰 때마다 +50)
    enum Mission: CaseIterable {
        case bottoms, streak3, flips3, dry, item, fever, score500
        var title: String {
            switch self {
            case .bottoms: return String(localized: "바닥 3번 잡기")
            case .streak3: return String(localized: "3번 연속 벌기")
            case .flips3: return String(localized: "공중제비 3번")
            case .dry: return String(localized: "풍덩 없이 끝내기")
            case .item: return String(localized: "아이템 먹기")
            case .fever: return String(localized: "피버 켜기")
            case .score500: return String(localized: "500점 넘기")
            }
        }
    }
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
        /// 바닥 3번 연속 '딱' → 피버 켜짐(true) / 꺼짐(false)
        case fever(Bool)
        /// 공중에서 톡: n 바퀴째
        case flip(Int)
        /// 공중제비 착지. combo = 연속으로 돈 점프 수
        case land(flips: Int, combo: Int, points: Int)
        case item(Item)
        /// 남은 시간 늘어남 (초)
        case time(Double)
        /// 구명조끼가 풍덩을 막음 (하트 그대로)
        case saved(day: Int)
        case mission(Mission)
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

    static let days = 100              // 30초 + 보너스 시간보다 넉넉하게
    static let secondsPerDay = 0.42   // 점점 빨라져 30초 끝에 1.4배
    static let timeLimit = 30.0        // 한 판 기본 30초. '딱'·아이템·미션·피버로 늘어난다
    static let maxHearts = 3
    static let perfectBonus = 50       // '딱' 보너스 점수
    static let streakBonus = 20        // 연속 성공 한 번 더할 때마다
    static let feverNeed = 3, feverTime = 6.0   // 바닥 3연속이면 6초간 점수 2배
    static let airTime = 0.6, flipBonus = 15    // 번 채로 내리면 0.6초 점프, 그동안 톡마다 한 바퀴
    static let missionBonus = 50
    /// 아이템 뽑기 주머니: 시간·속도 아이템이 자주 나온다
    static let itemBag: [Item] = [.clock, .clock, .clock, .rocket, .rocket, .turtle, .snail, .lighthouse, .vest, .dolphin]

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
    private(set) var elapsed = 0.0        // 코스 시간 (속도 반영, 거래일 = elapsed / secondsPerDay)
    private(set) var clock = 0.0          // 실제 지난 초
    private(set) var extra = 0.0          // 보너스로 늘어난 초
    private(set) var boost = 0.0          // 로켓 남은 초
    private(set) var hearts = maxHearts
    private(set) var score = 0
    private(set) var boardedAt: Int?      // 탄 날 (안 타고 있으면 nil)
    private(set) var boardedPerfect = false
    private(set) var streak = 0
    private(set) var ended: EndReason?
    private(set) var items: [Int: Item]   // 거래일 → 떠 있는 아이템
    let missions: [Mission]
    private(set) var missionsDone: Set<Mission> = []
    private(set) var got: [Item] = []
    private(set) var perfectRun = 0, perfectCount = 0, maxStreak = 0, flipCount = 0, splashCount = 0, feverCount = 0
    private(set) var feverLeft = 0.0, beacon = 0.0, slow = 0.0, air = 0.0
    private(set) var vest = false, dolphin = false
    private(set) var flips = 0, combo = 0
    /// 탄 채 하루 낙폭이 이보다 크면 풍덩 (코스 변동성 2배, 최소 1.5%)
    let splashDrop: Double

    init<G: RandomNumberGenerator>(course: Course, using g: inout G) {
        self.course = course
        splashDrop = max(0.015, 2 * Self.stdev(Self.rets(course.values)))
        var its: [Int: Item] = [:]
        if course.count >= 16 {
            for _ in 0..<max(1, course.count / 12) {
                let d = Int.random(in: 6...(course.count - 6), using: &g)
                if its.keys.allSatisfy({ abs($0 - d) >= 5 }) { its[d] = Self.itemBag.randomElement(using: &g) }
            }
        }
        items = its
        missions = Array(Mission.allCases.shuffled(using: &g).prefix(3))
    }
    init(course: Course) {
        var g = SystemRandomNumberGenerator()
        self.init(course: course, using: &g)
    }

    /// 한 판 제한 시간 (보너스 포함)
    var duration: Double { Self.timeLimit + extra }
    var timeLeft: Double { max(0, duration - clock) }
    var courseTime: Double { Double(course.count - 1) * Self.secondsPerDay }
    var riding: Bool { boardedAt != nil }
    var isOver: Bool { ended != nil }
    var fever: Bool { feverLeft > 0 }
    var airborne: Bool { air > 0 }
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
        if airborne { return flipTap() }
        boardedAt = day
        boardedPerfect = isBottom(day)
        var ev: [Event] = [.board(day: day, perfect: boardedPerfect)]
        if boardedPerfect {
            perfectCount += 1; perfectRun += 1
            ev += addTime(1)
            if perfectRun >= Self.feverNeed {
                if !fever { feverCount += 1; ev.append(.fever(true)); ev += addTime(2) }
                feverLeft = Self.feverTime; perfectRun = 0
            }
        } else { perfectRun = 0 }
        return ev + checkMissions()
    }

    /// 점프 중 톡: 한 바퀴 더 (점프 끝 0.15초 전까지, 최대 3바퀴)
    mutating func flipTap() -> [Event] {
        guard airborne, air > 0.15, flips < 3 else { return [] }
        flips += 1; flipCount += 1
        return [.flip(flips)] + checkMissions()
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
        maxStreak = max(maxStreak, streak)
        if gain > 0 {
            if dolphin { pts *= 2; dolphin = false }
            if fever { pts *= 2 }
            if boost > 0 { pts = pts * 3 / 2 }
            air = Self.airTime; flips = 0
        }
        score += pts
        boardedAt = nil; boardedPerfect = false
        return [.leave(day: day, gain: gain, points: pts)] + ev + checkMissions()
    }

    /// 화면이 매 프레임 부른다. dt 초만큼 시간을 흘리고 넘어간 거래일마다 판정한다
    mutating func tick(_ dt: Double) -> [Event] {
        guard !isOver else { return [] }
        let real = max(0, dt)
        clock += real
        var speed = 1 + 0.4 * min(1, clock / Self.timeLimit)    // 뒤로 갈수록 빨라져 30초에 1.4배
        if boost > 0 { speed *= 1.6 }                           // 로켓
        if slow > 0 { speed *= 0.5 }                            // 거북이·달팽이
        elapsed += real * speed
        slow = max(0, slow - real); beacon = max(0, beacon - real); boost = max(0, boost - real)
        var ev: [Event] = []
        if feverLeft > 0 { feverLeft -= real; if feverLeft <= 0 { feverLeft = 0; ev.append(.fever(false)) } }
        if air > 0 { air -= real; if air <= 0 { air = 0; ev += land() } }
        let target = min(course.count - 1, Int(elapsed / Self.secondsPerDay))
        while day < target && !isOver {
            day += 1
            let r = course.values[day] / course.values[day - 1] - 1
            if riding, let it = items[day] { items[day] = nil; ev += use(it) }
            if riding && r <= -splashDrop {
                // 탄 채 휘말리면 그날 값으로 강제로 내려지고 하트 하나를 잃는다
                let b = boardedAt!
                score += Int(((value / course.values[b] - 1) * 10000).rounded())
                boardedAt = nil; boardedPerfect = false; streak = 0; perfectRun = 0
                splashCount += 1
                if !vest { hearts -= 1 }
                ev.append(.splash(day: day, drop: r, hearts: hearts))
                if vest { vest = false; ev.append(.saved(day: day)) }
                if hearts <= 0 { ev += finish(.hearts) }
            } else if abs(r) < 0.0015 {
                ev.append(.calm(day: day))
            }
        }
        if !isOver { ev += checkMissions() }
        if !isOver && (clock >= duration || elapsed >= courseTime) { ev += finish(.time) }
        return ev
    }

    private mutating func use(_ it: Item) -> [Event] {
        switch it {
        case .lighthouse: beacon = 5
        case .turtle: slow = 3
        case .vest: vest = true
        case .dolphin: dolphin = true
        case .rocket: boost = 3
        case .snail: slow = 4
        case .clock: break
        }
        got.append(it)
        return [.item(it)] + addTime(it == .clock ? 3 : 1)
    }

    /// 남은 시간 늘리기: '딱' 1초 · 아이템 1초(시계 3초) · 피버 2초 · 미션 3초
    private mutating func addTime(_ s: Double) -> [Event] {
        extra += s
        return [.time(s)]
    }

    /// 점프 끝. 한 바퀴라도 돌았으면 바퀴 × 콤보 점수, 안 돌았으면 콤보가 끊긴다
    private mutating func land() -> [Event] {
        guard flips > 0 else { combo = 0; return [] }
        combo += 1
        let pts = Self.flipBonus * flips * combo * (fever ? 2 : 1)
        score += pts
        return [.land(flips: flips, combo: combo, points: pts)]
    }

    func met(_ m: Mission) -> Bool {
        switch m {
        case .bottoms: return perfectCount >= 3
        case .streak3: return maxStreak >= 3
        case .flips3: return flipCount >= 3
        case .dry: return ended == .time && splashCount == 0
        case .item: return !got.isEmpty
        case .fever: return feverCount > 0
        case .score500: return score >= 500
        }
    }

    private mutating func checkMissions() -> [Event] {
        var ev: [Event] = []
        for m in missions where !missionsDone.contains(m) && met(m) {
            missionsDone.insert(m); score += Self.missionBonus; ev.append(.mission(m))
            if !isOver { ev += addTime(3) }
        }
        return ev
    }

    private mutating func finish(_ why: EndReason) -> [Event] {
        var ev: [Event] = []
        if riding && why == .time { ev += leave() }   // 시간 끝: 타고 있던 건 그날 값으로 내린다
        if air > 0 { air = 0; ev += land() }
        ended = why
        ev += checkMissions()
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
