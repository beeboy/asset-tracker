import Foundation

// 평정 지수 (가위바위보 지수): 직전 결과(수익·손실)에 끌려 매매하는지를 센다.
// 점수는 '반대로 하기'가 아니라 '결과와 상관없이 정해 둔 규칙대로 하기'에 준다. 물타기는 보상하지 않는다.
// 근거: project-files research/가위바위보-심리학과-투자.md 6절, notes/가위바위보-지수-캐릭터-매칭안-2026-10-10.md

struct CalmEvent: Codable, Equatable {
    enum Act: String, Codable { case rule, chase, switchOut, avgDown, trim, wait }
    var day: String        // yyyy-MM-dd
    var sym: String
    var buy: Bool          // 산 것인지 (판 것·기다림은 false)
    var win: Bool          // 그때 그 종목이 평균 단가 위(수익)였는지
    var act: Act
    var big: Bool          // 그 종목이 하루 ±5% 넘게 움직인 날인지
    var pts: Int
    var isTrade: Bool { act != .wait }

    var label: String {
        switch act {
        case .rule: "규칙대로"
        case .chase: "수익 뒤 추가 매수"
        case .switchOut: "손실 뒤 매도"
        case .avgDown: "손실 뒤 추가 매수"
        case .trim: "수익 뒤 덜어내기"
        case .wait: "큰 변동일 기다림"
        }
    }
}

struct CalmStats {
    var n = 0              // 기록 수 (매매 + 기다림)
    var trades = 0
    var index = 50         // 0~100, 결과와 무관할수록 높다
    var chase = 0.0        // 수익 직후 추가 매수 비율
    var switchR = 0.0      // 손실 직후 매도 비율
    var waitR = 0.0        // 큰 변동일에 기다린 비율
    var ruleR = 0.0        // 매매 중 규칙대로 한 비율
    var contra = 0.0       // 수익 뒤 덜어내기 + 규칙 안의 역방향 매매 비율
    var perMonth = 0.0     // 최근 3달 한 달 평균 매매 수
    var trend: Int? = nil  // 최근 30일 지수 − 그 전 30일 지수 (양쪽 5건 이상일 때)
    var weekPts = 0
}

struct Calm: Codable, Equatable {
    var events: [CalmEvent] = []
    var type: String? = nil        // 지금 매칭된 인물 id
    var typeMonth: String? = nil   // 유형을 마지막으로 정한 달 (yyyy-MM). 한 달에 한 번만 바뀐다
    var prevType: String? = nil
    var together: [String] = []    // 지금 인물과 함께한 날 (앱을 연 날)

    static let weekCap = 5         // 한 주에 쌓을 수 있는 플러스 점수
    static let minEvents = 10      // 이보다 적으면 매칭하지 않는다

    private static let key = "calm.v1"
    static func load() -> Calm {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(Calm.self, from: $0) } ?? Calm()
    }
    func save() { if let d = try? JSONEncoder().encode(self) { UserDefaults.standard.set(d, forKey: Self.key) } }

    static func points(_ a: CalmEvent.Act) -> Int {
        switch a {
        case .rule: return 2
        case .wait: return 1
        case .chase, .switchOut: return -1
        case .avgDown, .trim: return 0
        }
    }

    private static func week(_ day: String) -> String {
        guard let d = Day.date(day) else { return day }
        let c = Calendar(identifier: .iso8601).dateComponents([.yearForWeekOfYear, .weekOfYear], from: d)
        return "\(c.yearForWeekOfYear ?? 0)-\(c.weekOfYear ?? 0)"
    }

    /// 기록 하나 더하기: 플러스 점수는 주당 상한까지만 (과잉 매매로 점수를 쌓지 못하게)
    mutating func record(_ e0: CalmEvent) {
        var e = e0
        e.pts = Self.points(e.act)
        if e.pts > 0 {
            let w = Self.week(e.day)
            let got = events.filter { Self.week($0.day) == w && $0.pts > 0 }.reduce(0) { $0 + $1.pts }
            e.pts = max(0, min(e.pts, Self.weekCap - got))
        }
        // 같은 날 같은 종목을 기다렸다고 적어 뒀다가 매매했으면 기다림은 지운다
        if e.isTrade { events.removeAll { $0.act == .wait && $0.day == e.day && $0.sym == e.sym } }
        events.append(e)
        if events.count > 400 { events.removeFirst(events.count - 400) }
        refreshType()
    }

    /// 앱을 연 날: 지금 인물과 함께한 날을 하나 센다
    mutating func touch(_ today: String = Day.today) {
        refreshType()
        if type != nil, together.last != today { together.append(today) }
    }

    /// 유형 정하기: 처음 10건이 쌓이면 바로, 그 뒤로는 달이 바뀔 때만 다시 본다
    mutating func refreshType(_ today: String = Day.today) {
        let month = String(today.prefix(7))
        guard let c = Self.match(stats(today)) else { return }
        if type == nil { type = c; typeMonth = month; return }
        guard typeMonth != month else { return }
        typeMonth = month
        if c != type { prevType = type; type = c; together = [] }
    }

    /// 애착 단계: 함께한 날 14일 미만 1, 60일 미만 2, 그 뒤 3
    var stage: Int { together.count < 14 ? 1 : together.count < 60 ? 2 : 3 }

    static func index(_ ev: [CalmEvent]) -> Double {
        let t = ev.filter(\.isTrade)
        let w = t.filter(\.win), l = t.filter { !$0.win }
        // 한쪽이 비어도 튀지 않게 (1, 2)를 더한 비율로 본다
        let pw = Double(w.filter(\.buy).count + 1) / Double(w.count + 2)
        let pl = Double(l.filter(\.buy).count + 1) / Double(l.count + 2)
        let raw = 100 * (1 - abs(pw - pl))
        // 매매가 10건보다 적으면 50 쪽으로 당긴다 (적은 기록으로 과장하지 않게)
        let k = min(1, Double(t.count) / 10)
        return 50 + (raw - 50) * k
    }

    func stats(_ today: String = Day.today) -> CalmStats {
        var s = CalmStats()
        let t = events.filter(\.isTrade)
        s.n = events.count; s.trades = t.count
        s.index = Int(Self.index(events).rounded())
        let w = t.filter(\.win), l = t.filter { !$0.win }
        s.chase = w.isEmpty ? 0 : Double(w.filter { $0.buy && $0.act != .rule }.count) / Double(w.count)
        s.switchR = l.isEmpty ? 0 : Double(l.filter { !$0.buy && $0.act != .rule }.count) / Double(l.count)
        let waits = events.filter { $0.act == .wait }.count, bigTrades = t.filter(\.big).count
        s.waitR = waits + bigTrades == 0 ? 0 : Double(waits) / Double(waits + bigTrades)
        if !t.isEmpty {
            s.ruleR = Double(t.filter { $0.act == .rule }.count) / Double(t.count)
            s.contra = Double(t.filter { $0.act == .trim || ($0.act == .rule && $0.buy != $0.win) }.count) / Double(t.count)
        }
        let ago = { (d: Int) in Day.str((Day.date(today) ?? Date()).addingTimeInterval(-Double(d) * 86400)) }
        let d30 = ago(30), d60 = ago(60), d90 = ago(90)
        s.perMonth = Double(t.filter { $0.day > d90 }.count) / 3
        let recent = events.filter { $0.day > d30 }, before = events.filter { $0.day > d60 && $0.day <= d30 }
        if recent.filter(\.isTrade).count >= 5, before.filter(\.isTrade).count >= 5 {
            s.trend = Int((Self.index(recent) - Self.index(before)).rounded())
        }
        let wk = Self.week(today)
        s.weekPts = events.filter { Self.week($0.day) == wk }.reduce(0) { $0 + $1.pts }
        return s
    }

    /// 다섯 인물 매칭 (위에서부터 먼저 맞는 것)
    static func match(_ s: CalmStats) -> String? {
        guard s.n >= minEvents else { return nil }
        if s.chase >= 0.6 || s.switchR >= 0.6 { return "ir" }
        if let t = s.trend, t >= 10 { return "sua" }
        if s.contra >= 0.5 && s.index >= 60 { return "seonbae" }
        if s.perMonth <= 2 && s.waitR >= 0.6 { return "sio" }
        if s.index >= 80 && s.ruleR >= 0.5 { return "seri" }
        return s.index >= 70 ? "seri" : "sua"
    }

    // MARK: 인물 문구

    struct Kind { let id: String; let title: String; let when: String; let like: String; let next: String }
    static let kinds: [Kind] = [
        .init(id: "ir", title: "앞서 달리는 형", when: "수익 뒤 추가 매수나 손실 뒤 매도가 60% 이상",
              like: "너무 빨랐던 사람. 연결을 다섯, 스물, 백으로 넓히다 넘쳤다.", next: "넘치기 전에 멈춘 한 번을 모아요."),
        .init(id: "sua", title: "자라는 형", when: "최근 30일 지수가 그 전보다 10 이상 올랐을 때",
              like: "침묵을 고르는 법을 배운다. 제때 오는 것을 몸으로 느낀다.", next: "올라온 지수를 지켜요."),
        .init(id: "seonbae", title: "되묻는 형", when: "수익 뒤 덜어내기·규칙 안의 역방향 매매가 절반 이상, 지수 60 이상",
              like: "질문으로 말한다. \"그게 정말 네 생각이야.\"", next: "역발상도 규칙으로 남겨요."),
        .init(id: "sio", title: "기다리는 형", when: "한 달 매매 2번 이하, 큰 변동일에 기다린 비율 60% 이상",
              like: "지연을 센다. 목표 100에 닿자 판다.", next: "기다림 끝에 정한 목표를 지켜요."),
        .init(id: "seri", title: "원칙형", when: "지수 80 이상, 매매의 절반 이상이 규칙대로",
              like: "한 박자 두고 관찰과 사실만 말한다.", next: "지금처럼 유지해요."),
    ]
    static func kind(_ id: String?) -> Kind? { kinds.first { $0.id == id } }

    /// 홈 카드 한 줄: 인물 말투가 애착 단계를 따라 바뀐다 (세리는 기계 문장 → 합쇼체 → 해요체)
    func say(_ s: CalmStats) -> String {
        let n = s.index, d = together.count
        switch (type ?? "", stage) {
        case ("", _): return "아직 모르겠습니다. 기록 \(s.n)/\(Self.minEvents)."
        case ("seri", 1): return "관찰 중. 평정 지수 \(n)."
        case ("seri", 2): return "평정 지수는 \(n)입니다. 계속 보겠습니다."
        case ("seri", _): return "평정 지수 \(n)이에요. 같이 봐 와서 알아요."
        case ("sio", 1): return "\(n). 세어 둘게."
        case ("sio", 2): return "\(n)이야. 어제보다 하루 더 셌어."
        case ("sio", _): return "\(n). 같이 센 날이 \(d)일이야."
        case ("seonbae", 1): return "평정 지수 \(n). 너는 이 숫자를 어떻게 생각해?"
        case ("seonbae", 2): return "\(n)이네. 지난달의 너라면 뭘 했을까?"
        case ("seonbae", _): return "\(n)까지 왔네. 그게 정말 네 생각이었지?"
        case ("ir", 1): return "\(n). 나도 빨랐어."
        case ("ir", 2): return "\(n). 넘치기 전에 멈춘 날이 늘고 있어."
        case ("ir", _): return "\(n). 너는 나처럼 되지 않았어."
        case ("sua", 1): return "\(n)이에요. 아직 고르는 중이에요."
        case ("sua", 2): return "\(n)이에요. 제때 오는 게 느껴져요."
        default: return "\(n)이에요. 또 올게요. 약속은 아니에요."
        }
    }

    /// 매매 직후 한 줄 피드백 (말하는 사람 = 매칭된 인물, 없으면 홈 친구)
    static func feedback(_ who: String, _ a: CalmEvent.Act) -> String {
        let lines: [String: [CalmEvent.Act: String]] = [
            "seri": [.rule: "계획 쪽으로 옮겼어요. 기록했어요.", .chase: "오른 종목을 더 샀어요. 계획 신호는 없었어요.",
                     .switchOut: "내린 종목을 팔았어요. 계획 신호는 없었어요.", .avgDown: "내린 종목을 더 샀어요. 점수는 그대로예요.",
                     .trim: "오른 종목을 덜었어요. 점수는 그대로예요.", .wait: "크게 움직인 날, 아무것도 안 했어요."],
            "sio": [.rule: "계획대로. 됐어.", .chase: "올라서 더 샀네. 계획엔 없었어.", .switchOut: "내려서 팔았네. 하루만 더 셀 걸.",
                    .avgDown: "내려서 더 샀네. 세어 둘게.", .trim: "올라서 덜었네. 세어 둘게.", .wait: "흔들린 날, 그냥 셌어. 그거면 돼."],
            "seonbae": [.rule: "계획대로 했네. 그 계획, 왜 그렇게 정했는지 기억나?", .chase: "올라서 더 산 거야, 계획이라서 산 거야?",
                        .switchOut: "내려서 판 거야, 아니면 정말 네 생각이야?", .avgDown: "더 산 이유가 가격이야, 계획이야?",
                        .trim: "덜어낸 이유, 한 줄로 말할 수 있어?", .wait: "아무것도 안 한 오늘, 어땠어?"],
            "ir": [.rule: "멈출 데서 멈췄어. 넘치지 않았어.", .chase: "빨랐어. 나도 그랬어.", .switchOut: "급하게 돌아섰네. 나도 그랬어.",
                   .avgDown: "더 붙잡았구나. 넘치기 전에 봐.", .trim: "덜어냈구나. 무게가 가벼워졌어.", .wait: "넘치기 전에 멈춘 한 번이야."],
            "sua": [.rule: "제때 했어요.", .chase: "말이 먼저 왔네요. 다음엔 한 박자 쉬어요.", .switchOut: "먼저 돌아섰네요. 다음엔 한 박자 쉬어요.",
                    .avgDown: "더 샀네요. 점수는 그대로예요.", .trim: "덜었네요. 점수는 그대로예요.", .wait: "오늘은 침묵을 골랐어요."],
        ]
        return lines[who]?[a] ?? lines["seri"]![a]!
    }
}

extension AppModel {
    /// 규칙 신호: 회복 루트는 몰린 종목 비중이 계획에서 5%p 넘게 벗어났을 때, 목표 루트는 그달 첫 적립 매수
    func calmDrift() -> Double? { isGoal ? nil : abs(focusWeight - planWeight) }

    /// 수량 고치기에서 '더 샀어요'·'팔았어요'·'지우기'를 저장한 뒤 부른다. 기록한 행동을 돌려준다
    @discardableResult
    func calmTrade(_ sym: Symbol, buy: Bool, avgBefore: Double, driftBefore: Double?) -> CalmEvent.Act {
        let today = Day.today, win = sym.last >= avgBefore
        var rule = false
        if let d0 = driftBefore, d0 >= 0.05, let d1 = calmDrift() { rule = d1 < d0 }
        if isGoal, buy, gM > 0 {
            let month = String(today.prefix(7))
            rule = !calm.events.contains { $0.buy && $0.day.hasPrefix(month) }
        }
        let act: CalmEvent.Act = rule ? .rule : win ? (buy ? .chase : .trim) : (buy ? .avgDown : .switchOut)
        calm.record(CalmEvent(day: today, sym: sym.id, buy: buy, win: win, act: act, big: abs(sym.quote.change) >= 0.05, pts: 0))
        return act
    }

    /// 홈을 열 때: 함께한 날을 세고, 내 종목이 ±5% 넘게 움직였는데 오늘 매매가 없으면 기다림 +1 (하루 한 번)
    func calmOpen() {
        let today = Day.today
        if !calm.events.contains(where: { $0.day == today }),
           let r = rows.filter({ abs($0.sym.quote.change) >= 0.05 }).max(by: { abs($0.sym.quote.change) < abs($1.sym.quote.change) }) {
            calm.record(CalmEvent(day: today, sym: r.sym.id, buy: false, win: r.value >= r.cost, act: .wait, big: true, pts: 0))
        }
        calm.touch(today)
    }

    /// 말하는 인물: 매칭된 인물, 없으면 홈 친구
    var calmSpeaker: String { calm.type ?? homeFriendShown }
}
