import Foundation

// 평정 지수 (가위바위보 지수): 직전 결과(수익·손실)에 끌려 매매하는지를 센다.
// 점수는 '반대로 하기'가 아니라 '결과와 상관없이 정해 둔 규칙대로 하기'에 준다. 물타기는 보상하지 않는다.
// 근거: project-files research/가위바위보-심리학과-투자.md 6절, notes/가위바위보-지수-캐릭터-매칭안-2026-10-10.md

struct CalmEvent: Codable, Equatable {
    enum Act: String, Codable { case rule, chase, switchOut, avgDown, trim, wait, fresh }   // fresh: 새 종목 매수
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
        case .rule: String(localized: "규칙대로")
        case .chase: String(localized: "수익 뒤 추가 매수")
        case .switchOut: String(localized: "손실 뒤 매도")
        case .avgDown: String(localized: "손실 뒤 추가 매수")
        case .trim: String(localized: "수익 뒤 덜어내기")
        case .wait: String(localized: "큰 변동일 기다림")
        case .fresh: String(localized: "새 종목 매수")
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
    var ladder: Ladder? = nil      // 습관 사다리: 지금 유형의 걸음 (예전 저장값엔 없어서 옵셔널)
    var notes: [CalmNote]? = nil   // 홈 리마인드가 가져갈 사다리 소식 (takeNotes)

    static let weekCap = 5         // 한 주에 쌓을 수 있는 플러스 점수
    static let minEvents = 10      // 이보다 적으면 매칭하지 않는다
    static let keep = 400          // 남겨 두는 기록 수 (오래된 것부터 지운다)

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
        case .avgDown, .trim, .fresh: return 0
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
        if events.count > Self.keep { events.removeFirst(events.count - Self.keep) }
        refreshType()
        climb()
    }

    /// 앱을 연 날: 지금 인물과 함께한 날을 하나 센다
    mutating func touch(_ today: String = Day.today) {
        refreshType(today)
        climb(today)
        if type != nil, together.last != today { together.append(today) }
    }

    /// 유형 정하기: 처음 10건이 쌓이면 바로, 그 뒤로는 달이 바뀔 때만 다시 본다
    mutating func refreshType(_ today: String = Day.today) {
        let month = String(today.prefix(7))
        guard let c = Self.match(stats(today)) else { return }
        if type == nil { type = c; typeMonth = month; return }
        guard typeMonth != month else { return }
        typeMonth = month
        if c != type {
            let up: Bool? = Self.rank(c) == Self.rank(type) ? nil : Self.rank(c) > Self.rank(type)
            note(CalmNote(kind: "calm.type.changed", type: c, day: today, from: type, up: up))
            prevType = type; type = c; together = []
        }
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

    struct Kind { let id: String; let title: String; let when: String; let like: String }
    static let kinds: [Kind] = [
        .init(id: "ir", title: String(localized: "앞서 달리는 형"), when: String(localized: "수익 뒤 추가 매수나 손실 뒤 매도가 60% 이상"),
              like: String(localized: "너무 빨랐던 사람. 연결을 다섯, 스물, 백으로 넓히다 넘쳤다.")),
        .init(id: "sua", title: String(localized: "자라는 형"), when: String(localized: "최근 30일 지수가 그 전보다 10 이상 올랐을 때"),
              like: String(localized: "침묵을 고르는 법을 배운다. 제때 오는 것을 몸으로 느낀다.")),
        .init(id: "seonbae", title: String(localized: "되묻는 형"), when: String(localized: "수익 뒤 덜어내기·규칙 안의 역방향 매매가 절반 이상, 지수 60 이상"),
              like: String(localized: "질문으로 말한다. \"그게 정말 네 생각이야.\"")),
        .init(id: "sio", title: String(localized: "기다리는 형"), when: String(localized: "한 달 매매 2번 이하, 큰 변동일에 기다린 비율 60% 이상"),
              like: String(localized: "지연을 센다. 목표 100에 닿자 판다.")),
        .init(id: "seri", title: String(localized: "원칙형"), when: String(localized: "지수 80 이상, 매매의 절반 이상이 규칙대로"),
              like: String(localized: "한 박자 두고 관찰과 사실만 말한다.")),
    ]
    static func kind(_ id: String?) -> Kind? { kinds.first { $0.id == id } }

    /// 인물 행동 한 줄 (쉬운 말 요약. 정확한 기준은 kinds.when과 match)
    static let does: [String: String] = [
        "seri": String(localized: "대부분 정해 둔 규칙대로 사고판다"),
        "sio": String(localized: "거의 안 사고팔고, 크게 흔들리면 기다린다"),
        "seonbae": String(localized: "오르면 조금 덜어내며 거꾸로 생각한다"),
        "ir": String(localized: "오르면 더 사고, 내리면 바로 판다"),
        "sua": String(localized: "점수가 한 달 새 눈에 띄게 좋아지는 중"),
    ]
    /// 기록마다 왜 그 점수인지 한 줄 (점수표 points와 같은 뜻)
    static func reason(_ a: CalmEvent.Act) -> String {
        switch a {
        case .rule: return String(localized: "규칙대로")
        case .wait: return String(localized: "급변일 기다림")
        case .chase: return ""
        case .switchOut: return String(localized: "내린 뒤 매도")
        case .avgDown, .trim: return String(localized: "규칙 밖")
        case .fresh: return String(localized: "새 종목 · 기록만")
        }
    }

    /// 지수가 무슨 뜻인지 한 줄 (100 = 직전 수익·손실과 무관하게 매매)
    static func meaning(_ s: CalmStats) -> String {
        guard s.n >= minEvents else { return String(localized: "기록 \(String(describing: minEvents))건이 모이면 지수가 나와요.") }
        let i = s.index
        let t = i >= 80 ? String(localized: "직전 수익·손실에 거의 끌리지 않아요") : i >= 60 ? String(localized: "직전 결과에 조금 끌려요") : i >= 40 ? String(localized: "직전 결과에 꽤 끌려요") : String(localized: "직전 결과에 많이 끌려요")
        return String(localized: "\(String(describing: t)). 100이면 결과와 상관없이 매매해요. (매매 \(String(describing: s.trades))건 기준)")
    }

    /// 왜 이 유형인지: 매칭에 쓴 숫자를 그대로 보여 준다
    static func why(_ id: String?, _ s: CalmStats) -> String? {
        let p = { (x: Double) in "\(Int((x * 100).rounded()))%" }
        switch id {
        case "ir": return String(localized: "수익 뒤 추가 매수 \(String(describing: p(s.chase))), 손실 뒤 매도 \(String(describing: p(s.switchR))). 어느 한쪽이 60%를 넘으면 이 유형이에요.")
        case "sua": return String(localized: "최근 30일 지수가 그 전 30일보다 \(String(describing: s.trend.map { ($0 >= 0 ? "+" : "") + "\($0)" } ?? "–")) 움직였어요. 10 이상 오르면 이 유형이에요.")
        case "seonbae": return String(localized: "매매의 \(String(describing: p(s.contra)))가 수익 뒤 덜어내기처럼 결과와 반대로 한 매매예요. 절반 이상이고 지수 60 이상이면 이 유형이에요.")
        case "sio": return String(localized: "한 달 매매 \(String(describing: String(format: "%.1f", s.perMonth)))번, 큰 변동일에 기다린 비율 \(String(describing: p(s.waitR))). 매매가 적고 잘 기다리면 이 유형이에요.")
        case "seri": return String(localized: "매매의 \(String(describing: p(s.ruleR)))가 규칙대로예요. 지수가 높고 규칙대로가 많으면 이 유형이에요.")
        default: return nil
        }
    }

    /// 함께한 날과 말투 단계 (14일·60일에 말투가 가까워진다)
    var stageLine: String {
        let d = together.count
        return d < 14 ? String(localized: "함께한 날 \(String(describing: d))일 · \(String(describing: 14 - d))일 더 함께하면 말투가 가까워져요")
            : d < 60 ? String(localized: "함께한 날 \(String(describing: d))일 · \(String(describing: 60 - d))일 더 함께하면 말투가 한 번 더 가까워져요")
            : String(localized: "함께한 날 \(String(describing: d))일 · 말투가 가장 가까워졌어요")
    }

    /// 홈 카드 한 줄: 인물 말투가 애착 단계를 따라 바뀐다 (세리는 기계 문장 → 합쇼체 → 해요체)
    func say(_ s: CalmStats, who: String = "seri") -> String {
        let n = s.index, d = together.count
        // 대사 데이터(Lines)에서 시세가 갱신될 때마다 다른 줄. 없으면 아래 원래 문장
        let vars = ["n": "\(n)", "d": "\(d)", "k": "\(s.n)", "min": "\(Self.minEvents)"]
        if let t = type, let l = Lines.pick("calm.\(min(3, max(1, stage))).\(t)", vars) { return l }
        if type == nil, let l = Lines.pick("calm.none.\(who)", vars) { return l }
        switch (type ?? "", stage) {
        case ("", _): return String(localized: "아직 모르겠습니다. 기록 \(String(describing: s.n))/\(String(describing: Self.minEvents)).")
        case ("seri", 1): return String(localized: "관찰 중. 평정 지수 \(String(describing: n)).")
        case ("seri", 2): return String(localized: "평정 지수는 \(String(describing: n))입니다. 계속 보겠습니다.")
        case ("seri", _): return String(localized: "평정 지수 \(String(describing: n))이에요. 같이 봐 와서 알아요.")
        case ("sio", 1): return String(localized: "\(String(describing: n)). 세어 둘게.")
        case ("sio", 2): return String(localized: "\(String(describing: n))이야. 어제보다 하루 더 셌어.")
        case ("sio", _): return String(localized: "\(String(describing: n)). 같이 센 날이 \(String(describing: d))일이야.")
        case ("seonbae", 1): return String(localized: "평정 지수 \(String(describing: n)). 너는 이 숫자를 어떻게 생각해?")
        case ("seonbae", 2): return String(localized: "\(String(describing: n))이네. 지난달의 너라면 뭘 했을까?")
        case ("seonbae", _): return String(localized: "\(String(describing: n))까지 왔네. 그게 정말 네 생각이었지?")
        case ("ir", 1): return String(localized: "\(String(describing: n)). 나도 빨랐어.")
        case ("ir", 2): return String(localized: "\(String(describing: n)). 넘치기 전에 멈춘 날이 늘고 있어.")
        case ("ir", _): return String(localized: "\(String(describing: n)). 너는 나처럼 되지 않았어.")
        case ("sua", 1): return String(localized: "\(String(describing: n))이에요. 아직 고르는 중이에요.")
        case ("sua", 2): return String(localized: "\(String(describing: n))이에요. 제때 오는 게 느껴져요.")
        default: return String(localized: "\(String(describing: n))이에요. 또 올게요. 약속은 아니에요.")
        }
    }

    /// 매매 직후 한 줄 피드백 (말하는 사람 = 매칭된 인물, 없으면 홈 친구)
    static func feedback(_ who: String, _ a: CalmEvent.Act) -> String {
        if let l = Lines.pick("fb.\(a.rawValue).\(who)") { return l }
        let lines: [String: [CalmEvent.Act: String]] = [
            "seri": [.rule: String(localized: "계획 쪽으로 옮겼어요. 기록했어요."), .chase: String(localized: "오른 종목을 더 샀어요. 계획 신호는 없었어요."),
                     .switchOut: String(localized: "내린 종목을 팔았어요. 계획 신호는 없었어요."), .avgDown: String(localized: "내린 종목을 더 샀어요. 점수는 그대로예요."),
                     .trim: String(localized: "오른 종목을 덜었어요. 점수는 그대로예요."), .wait: String(localized: "크게 움직인 날, 아무것도 안 했어요.")],
            "sio": [.rule: String(localized: "계획대로. 됐어."), .chase: String(localized: "올라서 더 샀네. 계획엔 없었어."), .switchOut: String(localized: "내려서 팔았네. 하루만 더 셀 걸."),
                    .avgDown: String(localized: "내려서 더 샀네. 세어 둘게."), .trim: String(localized: "올라서 덜었네. 세어 둘게."), .wait: String(localized: "흔들린 날, 그냥 셌어. 그거면 돼.")],
            "seonbae": [.rule: String(localized: "계획대로 했네. 그 계획, 왜 그렇게 정했는지 기억나?"), .chase: String(localized: "올라서 더 산 거야, 계획이라서 산 거야?"),
                        .switchOut: String(localized: "내려서 판 거야, 아니면 정말 네 생각이야?"), .avgDown: String(localized: "더 산 이유가 가격이야, 계획이야?"),
                        .trim: String(localized: "덜어낸 이유, 한 줄로 말할 수 있어?"), .wait: String(localized: "아무것도 안 한 오늘, 어땠어?")],
            "ir": [.rule: String(localized: "멈출 데서 멈췄어. 넘치지 않았어."), .chase: String(localized: "빨랐어. 나도 그랬어."), .switchOut: String(localized: "급하게 돌아섰네. 나도 그랬어."),
                   .avgDown: String(localized: "더 붙잡았구나. 넘치기 전에 봐."), .trim: String(localized: "덜어냈구나. 무게가 가벼워졌어."), .wait: String(localized: "넘치기 전에 멈춘 한 번이야.")],
            "sua": [.rule: String(localized: "제때 했어요."), .chase: String(localized: "말이 먼저 왔네요. 다음엔 한 박자 쉬어요."), .switchOut: String(localized: "먼저 돌아섰네요. 다음엔 한 박자 쉬어요."),
                    .avgDown: String(localized: "더 샀네요. 점수는 그대로예요."), .trim: String(localized: "덜었네요. 점수는 그대로예요."), .wait: String(localized: "오늘은 침묵을 골랐어요.")],
        ]
        return lines[who]?[a] ?? lines["seri"]?[a] ?? String(localized: "기록했어요.")
    }
}

// MARK: 습관 사다리
// 유형마다 잴 수 있는 걸음 3개. 걸음은 시작한 날부터 세고, 완료한 걸음은 내려가지 않는다.
// 매매를 더 해야 오르는 걸음은 두지 않는다 (기다림·규칙 신호·지켜 온 날로 오른다).
// 순서는 지수 기준일 뿐, 화면에서 인물 순위로 보여 주지 않는다. 근거: notes/평정-습관-사다리-2026-10-11.md

struct Ladder: Codable, Equatable {
    struct Done: Codable, Equatable { var type: String; var step: Int; var day: String }
    var type: String
    var step = 1               // 지금 걸음 (1~3). 4 = 이 유형의 걸음을 다 오름
    var since: String          // 지금 걸음을 시작한 날
    var hold: String? = nil    // 지수가 기준 이상으로 처음 올라선 날 (내려가면 비운다)
    var keeps = 0              // 세리: 여섯 달 걸음 뒤 더 지킨 반년 수
    var progDay: String? = nil // 진행 소식을 마지막으로 낸 날 (하루 한 번)
    var progCur = 0
    var done: [Done] = []      // 유형이 바뀌어도 남는다
}

/// 홈 리마인드가 받는 사다리 소식. kind: calm.step.done · calm.step.progress · calm.type.changed · calm.top.keep
struct CalmNote: Codable, Equatable {
    var kind: String
    var type: String
    var day: String
    var step: Int? = nil
    var name: String? = nil
    var cur: Int? = nil
    var goal: Int? = nil
    var from: String? = nil
    var up: Bool? = nil        // 유형이 지수 기준으로 위로 옮겼는지 (같은 칸이면 nil)
    var months: Int? = nil
}

struct StepProgress { let cur: Int; let goal: Int; let text: String; var done: Bool { cur >= goal } }

extension Calm {
    struct Step { let name: String; let what: String }
    static let steps: [String: [Step]] = [
        "ir": [.init(name: String(localized: "넘치기 전 멈춤"), what: String(localized: "큰 변동일(±5%)에 매매 없이 지나간 날 3번")),
               .init(name: String(localized: "쫓기 줄이기"), what: String(localized: "수익 뒤 추가 매수·손실 뒤 매도가 매매의 40% 미만, 또는 30일 동안 0번")),
               .init(name: String(localized: "지수 +10"), what: String(localized: "최근 30일 지수가 그 전 30일보다 10 이상"))],
        "sua": [.init(name: String(localized: "오른 지수 지키기"), what: String(localized: "30일 동안 수익 뒤 추가 매수·손실 뒤 매도 0번")),
                .init(name: String(localized: "규칙대로 3번"), what: String(localized: "규칙 신호(비중 이탈·그달 첫 적립)에 맞춘 매매 3번")),
                .init(name: String(localized: "지수 70 두 달"), what: String(localized: "평정 지수 70 이상을 60일 지키기"))],
        "seonbae": [.init(name: String(localized: "역발상을 규칙 안에서"), what: String(localized: "덜어내기·물타기 같은 역방향 매매 중 절반 이상을 규칙 신호에 맞추기 (2건 이상)")),
                    .init(name: String(localized: "물타기 쉬기"), what: String(localized: "30일 동안 규칙 밖 물타기 0번")),
                    .init(name: String(localized: "규칙대로 절반"), what: String(localized: "걸음 시작 뒤 매매 절반 이상이 규칙대로, 지수 80 이상"))],
        "sio": [.init(name: String(localized: "흔들림 지키기"), what: String(localized: "30일 동안 큰 변동일에 기다린 비율 60% 이상")),
                .init(name: String(localized: "기다리다 놓치지 않기"), what: String(localized: "규칙 신호가 왔을 때 규칙대로 1번 실행")),
                .init(name: String(localized: "규칙대로 절반"), what: String(localized: "걸음 시작 뒤 매매 절반 이상이 규칙대로, 지수 80 이상"))],
        "seri": [.init(name: String(localized: "한 달 유지"), what: String(localized: "평정 지수 80 이상을 30일")),
                 .init(name: String(localized: "석 달 유지"), what: String(localized: "평정 지수 80 이상을 90일")),
                 .init(name: String(localized: "여섯 달 유지"), what: String(localized: "평정 지수 80 이상을 180일"))],
    ]

    /// 지수 기준 칸 (소식의 위·아래 판단용. 화면에 순위로 쓰지 않는다)
    static func rank(_ id: String?) -> Int { ["ir": 0, "sua": 1, "seonbae": 2, "sio": 2, "seri": 3][id ?? ""] ?? -1 }
    static func holdLine(_ id: String) -> Int { id == "sua" ? 70 : 80 }

    private static func days(_ from: String, _ to: String) -> Int {
        guard let a = Day.date(from), let b = Day.date(to) else { return 0 }
        return max(0, Int((b.timeIntervalSince(a) / 86400).rounded()))
    }
    private static func pct(_ x: Double) -> String { "\(Int((x * 100).rounded()))%" }

    /// 지금 걸음의 진행
    func progress(_ today: String = Day.today) -> StepProgress? {
        guard let l = ladder, (1...3).contains(l.step) else { return nil }
        let ev = events.filter { $0.day >= l.since }, t = ev.filter(\.isTrade)
        let bad = t.filter { $0.act == .chase || $0.act == .switchOut }
        let s = stats(today), held = l.hold.map { Self.days($0, today) } ?? 0
        // 마지막으로 어긴 날(없으면 걸음 시작일)부터 지켜 온 날
        let streak = { (broke: [CalmEvent]) in Self.days(broke.map(\.day).max() ?? l.since, today) }
        let half = { () -> StepProgress in
            let r = t.isEmpty ? 0 : Double(t.filter { $0.act == .rule }.count) / Double(t.count)
            let ok = t.count >= 2 && r >= 0.5 && s.index >= 80
            return .init(cur: ok ? 1 : 0, goal: 1, text: String(localized: "규칙대로 \(String(describing: Self.pct(r))) · 지수 \(String(describing: s.index))"))
        }
        switch (l.type, l.step) {
        case ("ir", 1):
            let n = ev.filter { $0.act == .wait }.count
            return .init(cur: n, goal: 3, text: String(localized: "멈춤 \(String(describing: min(n, 3)))/3"))
        case ("ir", 2):
            if t.count >= 3 {
                let r = Double(bad.count) / Double(t.count)
                return .init(cur: r < 0.4 ? 1 : 0, goal: 1, text: String(localized: "쫓은 매매 \(String(describing: Self.pct(r))) (40% 미만이면 완료)"))
            }
            let d = bad.isEmpty ? Self.days(l.since, today) : 0
            return .init(cur: d, goal: 30, text: String(localized: "쫓지 않은 날 \(String(describing: min(d, 30)))/30"))
        case ("ir", 3):
            let tr = s.trend ?? 0
            return .init(cur: max(0, tr), goal: 10, text: s.trend == nil ? String(localized: "30일씩 매매 5건이 모이면 재요") : String(localized: "지난 30일보다 \(String(describing: tr >= 0 ? "+" : ""))\(String(describing: tr)) (목표 +10)"))
        case ("sua", 1):
            let d = streak(bad)
            return .init(cur: d, goal: 30, text: String(localized: "쫓지 않은 날 \(String(describing: min(d, 30)))/30"))
        case ("sua", 2):
            let n = t.filter { $0.act == .rule }.count
            return .init(cur: n, goal: 3, text: String(localized: "규칙대로 \(String(describing: min(n, 3)))/3"))
        case ("sua", 3):
            return .init(cur: held, goal: 60, text: String(localized: "지수 70 이상 \(String(describing: min(held, 60)))/60일"))
        case ("seonbae", 1):
            let c = t.filter { $0.act == .trim || $0.act == .avgDown || ($0.act == .rule && $0.buy != $0.win) }
            let r = c.isEmpty ? 0 : Double(c.filter { $0.act == .rule }.count) / Double(c.count)
            return .init(cur: c.count >= 2 && r >= 0.5 ? 1 : 0, goal: 1, text: String(localized: "역방향 매매 \(String(describing: c.count))건 중 규칙대로 \(String(describing: Self.pct(r)))"))
        case ("seonbae", 2):
            let d = streak(t.filter { $0.act == .avgDown })
            return .init(cur: d, goal: 30, text: String(localized: "물타기 없이 \(String(describing: min(d, 30)))/30일"))
        case ("sio", 1):
            let w = ev.filter { $0.act == .wait }.count, b = t.filter(\.big).count
            let r = w + b == 0 ? 1 : Double(w) / Double(w + b)
            let d = r >= 0.6 ? Self.days(l.since, today) : 0
            return .init(cur: d, goal: 30, text: String(localized: "기다림 \(String(describing: Self.pct(r))) · \(String(describing: min(d, 30)))/30일"))
        case ("sio", 2):
            let n = t.filter { $0.act == .rule }.count
            return .init(cur: n, goal: 1, text: n > 0 ? String(localized: "규칙대로 1번 했어요") : String(localized: "규칙 신호가 오면 그때 한 번"))
        case ("seonbae", 3), ("sio", 3):
            return half()
        case ("seri", let k):
            let g = [30, 90, 180][k - 1]
            return .init(cur: held, goal: g, text: String(localized: "지수 80 이상 \(String(describing: min(held, g)))/\(String(describing: g))일"))
        default: return nil
        }
    }

    /// 유형이 정해지면 사다리를 맞추고, 걸음이 끝났으면 다음 걸음으로. 소식은 notes에 쌓는다
    mutating func climb(_ today: String = Day.today) {
        guard let t = type, Self.steps[t] != nil else { return }
        if ladder?.type != t { ladder = Ladder(type: t, since: today, done: ladder?.done ?? []) }
        let s = stats(today)
        let h0 = ladder?.hold
        ladder?.hold = s.n >= Self.minEvents && s.index >= Self.holdLine(t) ? (h0 ?? today) : nil
        for _ in 0..<3 {
            guard var l = ladder, (1...3).contains(l.step), let p = progress(today), p.done else { break }
            let name = Self.steps[t]![l.step - 1].name
            l.done.append(.init(type: t, step: l.step, day: today))
            note(CalmNote(kind: "calm.step.done", type: t, day: today, step: l.step, name: name))
            l.step += 1; l.since = today; l.progCur = 0
            ladder = l
        }
        guard var l = ladder else { return }
        if t == "seri", l.step > 3, let h = l.hold, Self.days(h, today) >= 180 * (l.keeps + 2) {
            l.keeps += 1
            note(CalmNote(kind: "calm.top.keep", type: t, day: today, months: 6 * (l.keeps + 1)))
        }
        if let p = progress(today), p.cur > l.progCur, l.progDay != today {
            l.progCur = p.cur; l.progDay = today
            note(CalmNote(kind: "calm.step.progress", type: t, day: today, step: l.step, name: Self.steps[t]![l.step - 1].name, cur: min(p.cur, p.goal), goal: p.goal))
        }
        ladder = l
    }

    mutating func note(_ n: CalmNote) {
        var a = notes ?? []
        // 같은 걸음의 진행 소식은 마지막 것만
        if n.kind == "calm.step.progress" { a.removeAll { $0.kind == n.kind && $0.type == n.type && $0.step == n.step } }
        a.append(n)
        notes = Array(a.suffix(30))
    }

    /// 홈 리마인드가 소식을 가져가면 비운다
    mutating func takeNotes() -> [CalmNote] { defer { notes = nil }; return notes ?? [] }

    /// 걸음 완료 한 줄 (인물 말투, 대사 데이터 ladder.<유형>.<걸음> 이 있으면 그것)
    static func stepLine(_ type: String, _ step: Int, who: String) -> String {
        if let l = Lines.pick("ladder.\(type).\(step).\(who)") ?? Lines.pick("ladder.\(type).\(step)") { return l }
        return String(localized: "걸음 \(String(describing: step))/3 ‘\(String(describing: steps[type]?[step - 1].name ?? ""))’ 완료!")
    }
}

extension AppModel {
    /// 규칙 신호: 회복 루트는 몰린 종목 비중이 계획에서 5%p 넘게 벗어났을 때, 목표 루트는 그달 첫 적립 매수
    func calmDrift() -> Double? { isGoal ? nil : abs(focusWeight - planWeight) }

    /// 수량 고치기(직접 고치기로 수량이 바뀐 것 포함)·종목 추가를 저장한 뒤 부른다. 기록한 행동을 돌려준다
    @discardableResult
    func calmTrade(_ sym: Symbol, buy: Bool, avgBefore: Double, driftBefore: Double?, fresh: Bool = false) -> CalmEvent.Act {
        let today = Day.today, win = sym.last >= avgBefore
        var rule = false
        if let d0 = driftBefore, d0 >= 0.05, let d1 = calmDrift() { rule = d1 < d0 }
        if isGoal, buy, gM > 0 {
            let month = String(today.prefix(7))
            rule = !calm.events.contains { $0.buy && $0.day.hasPrefix(month) }
        }
        let act: CalmEvent.Act = rule ? .rule : fresh ? .fresh : win ? (buy ? .chase : .trim) : (buy ? .avgDown : .switchOut)
        calm.record(CalmEvent(day: today, sym: sym.id, buy: buy, win: win, act: act, big: abs(sym.quote.change) >= 0.05, pts: 0))
        return act
    }

    /// 한 번만: 이 기록이 생기기 전 수량 기록(lots)에서 날짜별 수량 변화를 찾아 내 투자 습관에 채운다.
    /// 첫 줄은 시작 수량이라 빼고, 이미 있는 날·종목은 건너뛴다. 그날 종가와 지금 평균 단가로 수익·손실을 보고, 종가를 모르면 점수 없는 기록으로
    func calmBackfill() {
        let flag = "calm.backfill.v1", ud = UserDefaults.standard
        guard !isSample, persists, !ud.bool(forKey: flag) else { return }
        ud.set(true, forKey: flag)
        var add: [CalmEvent] = []
        for (a, b) in zip(lots, lots.dropFirst()) where b.d < Day.today {
            for t in Set(a.h.keys).union(b.h.keys) {
                let q0 = a.h[t] ?? 0, q1 = b.h[t] ?? 0
                guard q0 != q1, let id = Sync.appId(t), id != "DRNK",
                      !calm.events.contains(where: { $0.day == b.d && $0.sym == id }) else { continue }
                let buy = q1 > q0, h = PriceHistory.of(id), close = h?.on(b.d)
                let prev = h.flatMap { h in h.dates.lastIndex { $0 < b.d }.map { h.closes[$0] } }
                let big = close.flatMap { c in prev.map { abs(c / $0 - 1) >= 0.05 } } ?? false
                let avg = holdings.first { $0.symbol == id }?.avg
                let win = close.flatMap { c in avg.map { c >= $0 } }
                let act: CalmEvent.Act = q0 == 0 ? .fresh : win.map { $0 ? (buy ? .chase : .trim) : (buy ? .avgDown : .switchOut) } ?? (buy ? .avgDown : .trim)
                add.append(CalmEvent(day: b.d, sym: id, buy: buy, win: win ?? false, act: act, big: big, pts: Calm.points(act)))
            }
        }
        guard !add.isEmpty else { return }
        var c = calm
        c.events = (add + c.events).sorted { $0.day < $1.day }.suffix(Calm.keep).map { $0 }
        c.refreshType(); c.climb()
        calm = c
    }

    /// 홈을 열 때: 함께한 날을 세고, 내 종목이 ±5% 넘게 움직였는데 오늘 매매가 없으면 기다림 +1 (하루 한 번)
    func calmOpen() {
        let today = Day.today
        calmBackfill()
        recordDay()   // 내 기록: 홈을 연 날 한 줄 (같은 날은 지금 값으로 고침)
        if !calm.events.contains(where: { $0.day == today }),
           let r = rows.filter({ abs($0.sym.quote.change) >= 0.05 }).max(by: { abs($0.sym.quote.change) < abs($1.sym.quote.change) }) {
            calm.record(CalmEvent(day: today, sym: r.sym.id, buy: false, win: r.value >= r.cost, act: .wait, big: true, pts: 0))
        }
        calm.touch(today)
    }

    /// 말하는 인물: 매칭된 인물, 없으면 홈 친구. 홈에 내 캐릭터를 두면 내 캐릭터가 (말투는 유형 인물)
    var calmSpeaker: String { homeFriendShown == "me" ? "me" : calm.type ?? homeFriendShown }
}
