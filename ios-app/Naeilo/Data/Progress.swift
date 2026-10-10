import Foundation

// 미션 진행 저장: 앱을 다시 켜도 미션 판·인터미션·오늘의 1분·쉼터·목표 설정이 이어진다.
// 내 종목(holdings)과 알림 설정은 따로 저장한다. 화면 위치(어느 탭, 어느 화면)는 저장하지 않는다.
struct Progress: Codable, Equatable {
    var v = 1
    // 회복 루트
    var done: Set<Int>
    var weeks: [WeekPick]
    var weekCur: WeekPick?
    var planKey: String
    var horizon: Int
    var quizAnswer: String?
    var taxGain: Double
    var taxSellQty: Double
    var nxStep: Int
    // 목표 루트
    var route: Route
    var gK: Double, gY: Int, gA: Double, gM: Double
    var gMix: String
    var gDone: Set<String>
    var gWeeks: [GoalWeek]
    var gWeekCur: GoalWeek?
    var goalTaxPick: String
    // 오늘의 1분 (day = 시작한 날부터 며칠째, 0부터)
    var day: Int
    var dayLog: [Int: DayLog]
    var lastOpen: String          // 마지막으로 연 날 (yyyy-MM-dd). 다음에 열 때 지난 날수만큼 day 를 넘긴다
    var weekGuess: String?
    // 쉼터
    var homeFriend: String
    var readPos: Set<Int>
    var readLast: Int?
    // 분석·기기
    var lens: Lens
    var trust: Double
    var shock: Bool
    var monthly: Double
    var syncOn: Bool               // 기록용 (실제 상태는 이 기기 키체인)
    var widgetSel: [String]?

    private static let key = "progress.v1"
    static func load() -> Progress? {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(Progress.self, from: $0) }
    }
    func save() {
        if let d = try? JSONEncoder().encode(self) { UserDefaults.standard.set(d, forKey: Self.key) }
    }
}

extension AppModel {
    /// 캡처·확인용 실행 인자가 있으면 저장된 진행을 읽지도 쓰지도 않는다
    var persists: Bool {
        let d = UserDefaults.standard
        return !["demo", "goal", "route", "tab", "home", "hold", "set", "an", "addTest", "searchTest"].contains { d.object(forKey: $0) != nil }
    }

    var progress: Progress {
        Progress(done: done, weeks: weeks, weekCur: weekCur, planKey: planKey, horizon: horizon, quizAnswer: quizAnswer,
                 taxGain: taxGain, taxSellQty: taxSellQty, nxStep: nxStep,
                 route: route, gK: gK, gY: gY, gA: gA, gM: gM, gMix: gMix, gDone: gDone, gWeeks: gWeeks, gWeekCur: gWeekCur, goalTaxPick: goalTaxPick,
                 day: day, dayLog: dayLog, lastOpen: Day.today, weekGuess: weekGuess,
                 homeFriend: homeFriend, readPos: readPos, readLast: readLast,
                 lens: lens, trust: trust, shock: shock, monthly: monthly, syncOn: syncOn, widgetSel: widgetSel)
    }

    func apply(_ p: Progress) {
        done = p.done; weeks = p.weeks; weekCur = p.weekCur; planKey = p.planKey; horizon = p.horizon; quizAnswer = p.quizAnswer
        taxGain = p.taxGain; taxSellQty = p.taxSellQty; nxStep = p.nxStep
        route = p.route; gK = p.gK; gY = p.gY; gA = p.gA; gM = p.gM; gMix = p.gMix; gDone = p.gDone; gWeeks = p.gWeeks; gWeekCur = p.gWeekCur
        goalTaxPick = p.goalTaxPick
        dayLog = p.dayLog; weekGuess = p.weekGuess
        homeFriend = p.homeFriend; readPos = p.readPos; readLast = p.readLast
        lens = p.lens; trust = p.trust; shock = p.shock; monthly = p.monthly; widgetSel = p.widgetSel
        day = p.day
        catchUpDay(from: p.lastOpen)
    }

    /// 달력이 넘어간 만큼 오늘의 1분 날짜도 넘긴다 (안 연 날은 빈 칸으로 남아 연속 기록이 끊긴다).
    /// 앱을 열 때와, 켜 둔 채 날이 바뀐 뒤 다시 앞으로 올 때 부른다
    func catchUpDay(from last: String? = nil) {
        let from = last ?? UserDefaults.standard.string(forKey: "progress.openedOn") ?? Day.today
        let gap = Day.date(from).flatMap { Calendar.current.dateComponents([.day], from: $0, to: Day.date(Day.today) ?? Date()).day } ?? 0
        if gap > 0 { day += gap }
        UserDefaults.standard.set(Day.today, forKey: "progress.openedOn")
        saveProgress()
    }

    func saveProgress() { if persists { progress.save() } }
}
