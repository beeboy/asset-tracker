import SwiftUI
import Observation

// 앱 상태 + 계산. 설명 문구는 모두 계산값으로 만든다 (종목 추천 문구 없음).

enum WeekPick: String, CaseIterable, Codable { case kept, changed, raised
    var label: String { switch self { case .kept: "지킴"; case .changed: "바꿈"; case .raised: "높임" } }
    var sub: String { switch self { case .kept: "계획한 비중 그대로"; case .changed: "계획 자체를 바꿈"; case .raised: "DRNK를 다시 늘림" } }
    var color: Color { switch self { case .kept: Theme.green; case .changed: Theme.orange; case .raised: Color(hex: 0xC8352E) } }
}

struct DayLog: Codable, Equatable { var seen = false; var answer: Int? = nil }

enum Lens: String, CaseIterable, Codable { case base, mine, smooth
    var label: String { switch self { case .base: "현재 정세"; case .mine: "내 관점"; case .smooth: "과거 추세" } }
}

@Observable
final class AppModel {
    let prices: PriceProvider = StubPriceProvider()
    let stories: StoryProvider = StubStoryProvider()

    init() {
        // 저장된 진행을 먼저 불러오고, 캡처용 실행 인자가 있으면 그 위에 시연 상태를 덮는다 (그때는 저장하지 않는다)
        if persists, let p = Progress.load() { apply(p) }
        // 캡처용: -demo fresh 로 미션 1부터 시작
        if UserDefaults.standard.string(forKey: "demo") == "fresh" { resetDemo(.fresh) }
        if UserDefaults.standard.string(forKey: "demo") == "all" { resetDemo(.all) }
        // 캡처용: -goal novice|plus (목표 루트, 미션은 모두 끝낸 상태로)
        if let g = UserDefaults.standard.string(forKey: "goal"), let r = Route(rawValue: g) {
            switchRoute(r); gDone = Set(goalSteps.filter { !$0.inter }.map(\.id)); nxStep = 3
        }
        // 확인용: -addTest 005930,10,84000 → 종목 추가와 같은 경로로 넣는다
        if let t = UserDefaults.standard.string(forKey: "addTest")?.split(separator: ","), t.count == 3 { addHolding(String(t[0]), String(t[1]), String(t[2])) }
        // 확인용: -searchTest tesla → 종목 추가 화면을 그 검색어로 연다
        if UserDefaults.standard.string(forKey: "searchTest") != nil { holdPath = NavigationPath(["add"]) }
        // 확인용: -syncTest <비밀번호> → 그 비밀번호로 동기화를 켠다 (사이트와 주고받기 확인)
        #if DEBUG
        if let pw = UserDefaults.standard.string(forKey: "syncTest"), !Sync.shared.isOn { Task { @MainActor in await Sync.shared.turnOn(password: pw, model: self) } }
        if let t = UserDefaults.standard.string(forKey: "syncDevTest"), !Sync.shared.isOn { Task { @MainActor in await Sync.shared.turnOnDev(token: t, model: self) } }
        #endif
        // 캡처용: -home shelter 또는 -home char:ir
        if let h = UserDefaults.standard.string(forKey: "home") { homePath = h == "shelter" ? ["shelter"] : ["shelter", h] }
        if let f = UserDefaults.standard.string(forKey: "friend") { shelterSel = f }
        if let k = UserDefaults.standard.string(forKey: "hold"), let sy = Sample.symbol(k) { holdPath = NavigationPath([sy]) }
        let st: [String: SettingsRoute] = ["alerts": .alerts, "sync": .sync, "widgets": .widgets, "tax": .tax, "price": .price, "howto": .howto, "route": .route, "goal": .goal, "charPreview": .charPreview]
        if let r = UserDefaults.standard.string(forKey: "set").flatMap({ st[$0] }) { settingsPath = [r] }
        // 캡처용: -route m3 처럼 미션 화면을 바로 연다
        let routes: [String: MissionRoute] = ["m1": .m1, "m1r": .m1r, "m2": .m2, "m2r": .m2r, "m3": .m3, "m3r": .m3r, "m4": .m4, "m4r": .m4r, "nx": .nx, "g1": .g1, "g1r": .g1r, "g3": .g3, "g3r": .g3r, "gt": .gt, "gi": .gi, "gp1r": .gp1r]
        if let r = UserDefaults.standard.string(forKey: "route").flatMap({ routes[$0] }) { boardPath = [r] }
        let an: [String: AnalysisRoute] = ["forecast": .forecast, "myPath": .myPath, "external": .external, "dividend": .dividend, "fx": .fx, "glance": .glance]
        if let r = UserDefaults.standard.string(forKey: "an").flatMap({ an[$0] }) { analysisPath = NavigationPath([r]) }
    }

    // 보유
    // 내 종목은 앱을 다시 켜도 남는다 (다른 시안 상태는 켤 때마다 시연 상태로 시작)
    var holdings: [Holding] = AppModel.savedHoldings ?? Sample.startHoldings {
        didSet { if let d = try? JSONEncoder().encode(holdings) { UserDefaults.standard.set(d, forKey: "holdings") } }
    }
    /// 종목 추가: 수량·평균 단가가 0보다 큰 숫자여야 한다 (쉼표·공백 허용). 같은 종목은 새 값으로 바꾼다
    @discardableResult
    func addHolding(_ id: String, _ qty: String, _ avg: String) -> Bool {
        let num = { (t: String) in Double(t.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)) }
        guard Sample.symbol(id) != nil, let q = num(qty), q > 0, let a = num(avg), a > 0 else { return false }
        holdings.removeAll { $0.symbol == id }
        holdings.append(Holding(symbol: id, qty: q, avg: a))
        return true
    }
    static var savedHoldings: [Holding]? {
        UserDefaults.standard.data(forKey: "holdings").flatMap { try? JSONDecoder().decode([Holding].self, from: $0) }
    }

    // 미션 (회복 루트): 1 종목·단가, 2 원인 진단, 3 내 계획, 5 앱 시작 3단계, 6 인터미션
    // 시안 시연용 시작 상태: 미션 1~3과 앱 시작 3단계를 마치고 인터미션 1주차를 고른 뒤
    var done: Set<Int> = [1, 2, 3, 5]
    var weeks: [WeekPick] = [.kept]
    var weekCur: WeekPick? = nil

    // 오늘의 1분
    var day = 5
    var dayLog: [Int: DayLog] = [:]

    // 쉼터
    var homeFriend = "seri"
    var guideTick = 0          // 미션 안내 인물을 바꾸면 다시 그리게
    /// 홈·위젯에 실제로 보이는 인물: 아직 안 열린 인물이면 세리 (개발자 연결을 끊었을 때 등)
    var homeFriendShown: String {
        let i = Shelter.friends.firstIndex { $0.id == homeFriend } ?? 0
        return friendOn(i) ? homeFriend : "seri"
    }
    var shelterSel: String? = nil
    var readPos: Set<Int> = []
    var readLast: Int? = nil

    // 주간 예보
    var weekGuess: String? = nil
    var weekFriday = false

    // 3년 전망
    var lens: Lens = .mine
    var trust = 50.0
    var shock = true
    var monthly = 0.0

    // MARK: 형식
    static func man(_ krw: Double) -> String {
        let n = Int((krw / 1e4).rounded())
        return n.formatted(.number.grouping(.automatic)) + "만원"
    }
    static func pct(_ p: Double) -> String { "\(Int((p * 100).rounded()))%" }
    static func sgn(_ p: Double) -> String { (p >= 0 ? "+" : "") + String(format: "%.1f%%", p * 100) }
    static func sgn0(_ p: Double) -> String { (p >= 0 ? "+" : "") + "\(Int((p * 100).rounded()))%" }
    static func price(_ s: Symbol, _ x: Double) -> String {
        s.currency == .usd ? "$" + x.formatted(.number.precision(.fractionLength(0...2))) : Int(x.rounded()).formatted() + "원"
    }

    // MARK: 보유 계산 (원화)
    func krw(_ s: Symbol, _ x: Double) -> Double { s.currency == .usd ? x * Market.shared.fx.last : x }
    /// 들어간 돈은 환율이 움직여도 바뀌지 않게 고정 환율로 (실제 앱은 산 날 환율)
    func costKrw(_ s: Symbol, _ x: Double) -> Double { s.currency == .usd ? x * Sample.fx : x }
    struct Row: Identifiable { let id: String; let sym: Symbol; let h: Holding; let value: Double; let cost: Double; let color: Color }
    var rows: [Row] {
        holdings.compactMap { h -> (Symbol, Holding)? in
            guard h.qty > 0, let s = Sample.symbol(h.symbol) else { return nil }
            return (s, h)
        }.enumerated().map { i, p in
            Row(id: p.0.id, sym: p.0, h: p.1, value: krw(p.0, p.1.qty * p.0.last), cost: costKrw(p.0, p.1.qty * p.1.avg),
                color: Theme.holdColors[i % Theme.holdColors.count])
        }
    }
    var total: Double { rows.reduce(0) { $0 + $1.value } }
    var cost: Double { rows.reduce(0) { $0 + $1.cost } }
    var ret: Double { cost > 0 ? total / cost - 1 : 0 }
    var drnkWeight: Double { total > 0 ? (rows.first { $0.id == "DRNK" }?.value ?? 0) / total : 0 }
    /// 오늘의 움직임: 원화 평가액 기준 (전일 종가·전일 환율 → 지금)
    var todayMove: Double {
        let fx = Market.shared.fx
        let prev = rows.reduce(0) { a, r in a + r.h.qty * r.sym.quote.prevClose * (r.sym.currency == .usd ? fx.prevClose : 1) }
        return prev > 0 ? total / prev - 1 : 0
    }

    // MARK: 회복 계획 (계산은 Recovery.swift)
    static func normCDF(_ x: Double) -> Double {
        let t = 1 / (1 + 0.2316419 * abs(x)), d = 0.3989423 * exp(-x * x / 2)
        let p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return x > 0 ? 1 - p : p
    }
    var planKey = "balance"      // 미션 3에서 고른 계획
    // 목표 루트 (만원)
    var route: Route = .recover
    var gK = 10000.0
    var gY = 5
    var gA = 2000.0
    var gM = 100.0
    var gMix = "index"
    var gDone: Set<String> = []
    var gWeeks: [GoalWeek] = []
    var gWeekCur: GoalWeek? = nil
    var goalTaxPick = "split"
    var horizon = 6              // 미션 1·3의 기간 칩 (3·6·12개월)
    var quizAnswer: String? = nil
    var taxGain = 600.0          // 올해 실현 이익 (만원)
    var taxSellQty = 0.0
    var nxStep = 3               // 앱 시작 3단계 중 끝낸 단계 수
    var boardPath: [MissionRoute] = []
    var analysisPath = NavigationPath()
    var homePath: [String] = []
    var tab: Tab = RootView.launchTab
    // 처음 실행이면 첫 질문(플러스·마이너스·시작 전)부터. 캡처·시연용 실행 인자가 있으면 건너뛴다
    var onboarded = UserDefaults.standard.bool(forKey: "onboarded") || UserDefaults.standard.string(forKey: "tab") != nil
        || UserDefaults.standard.string(forKey: "goal") != nil || UserDefaults.standard.string(forKey: "demo") != nil
    var appearance = UserDefaults.standard.string(forKey: "appearance") ?? "system"   // system · light · dark
    // 종목 탭은 종목(Symbol)과 '종목 추가'(String)를, 분석 탭은 분석 화면과 종목을 같이 쌓으므로 NavigationPath
    var holdPath = NavigationPath()

    // 설정
    // 알림 설정은 기기에 남는다 (실제 예약은 Notifier)
    var alerts: [String: Bool] = (UserDefaults.standard.dictionary(forKey: "alerts") as? [String: Bool]) ?? ["be": true, "drift": false, "dep": false, "morn": false] {
        didSet { UserDefaults.standard.set(alerts, forKey: "alerts") }
    }
    var alertLast: String? = nil
    var alertTh = UserDefaults.standard.object(forKey: "alertTh") as? Int ?? 5 { didSet { UserDefaults.standard.set(alertTh, forKey: "alertTh") } }
    var alertHr = UserDefaults.standard.object(forKey: "alertHr") as? Int ?? 8 { didSet { UserDefaults.standard.set(alertHr, forKey: "alertHr") } }
    /// 기기 자동 동기화 (Sync). 시안 때의 가짜 연결 대신 실제 상태
    var syncOn: Bool { Sync.shared.isOn }
    var widgetSel: [String]? = nil
    var settingsPath: [SettingsRoute] = []

    var planWeight: Double { selectedPlan.wt }
    func planBreakEven(years T: Double) -> Double { selectedPlan.prob(cost, T) }

    // MARK: 미션 판
    struct Block: Identifiable { let id: Int; let num: String; let title: String; let reward: String; let inter: Bool }
    let blocks: [Block] = [
        .init(id: 1, num: "1", title: "종목·단가 넣기", reward: "회복 확률", inter: false),
        .init(id: 2, num: "2", title: "원인 진단", reward: "회복 경로", inter: false),
        .init(id: 3, num: "3", title: "내 계획 정하기", reward: "비중 비교", inter: false),
        .init(id: 5, num: "4", title: "앱 시작 3단계", reward: "위젯 전부", inter: false),
        .init(id: 6, num: "인터미션", title: "4주, 매주 1분", reward: "캐릭터 위젯 4개, 1년 전망", inter: true),
    ]
    func available(_ b: Block) -> Bool {
        guard let i = blocks.firstIndex(where: { $0.id == b.id }) else { return false }
        return i == 0 || done.contains(blocks[i - 1].id)
    }
    var playOn: Bool { route == .recover ? done.contains(5) : gDone.contains("link") }
    var interDone: Bool { route == .recover ? done.contains(6) : gDone.contains("inter") }
    func finishAppStart() {
        if route == .recover { done.insert(5) } else { gDone.insert("link") }
        boardPath = []
    }

    func confirmWeek() {
        guard let c = weekCur, weeks.count < 4 else { return }
        weeks.append(c); weekCur = nil
        if weeks.count == 4 { done.insert(6) }
    }

    // MARK: 친구·장
    // 세리 외에 열린 친구 수. 목표 루트는 미션 없이 첫 달 1~4주차에 같은 순서로 열림 (시안 39판)
    /// 개발자 계정(개발자 동기화로 연결한 기기)은 모든 보상이 열린다. 미션 진행 자체는 그대로 둔다
    var devAll: Bool {
        #if DEBUG
        if UserDefaults.standard.bool(forKey: "devAllTest") { return true }   // 확인용: -devAllTest YES
        #endif
        return Sync.shared.isDev
    }
    /// 앱 시작 3단계 보상(오늘의 1분·1000칸·주간 예보)이 열렸는지
    var playUnlocked: Bool { playOn || devAll }
    var friendsOpen: Int {
        if devAll { return 4 }
        if interDone { return 4 }
        if route == .recover { return weeks.count }
        return !gWeeks.isEmpty ? 4 : playOn ? min(4, day / 7) : 0
    }
    func friendOn(_ i: Int) -> Bool { i == 0 || i <= friendsOpen }
    func chapterOn(_ i: Int) -> Bool {
        if devAll { return true }
        if i < 2 { return playOn }
        let fid = Shelter.chapters[i].friend
        return friendOn(Shelter.friends.firstIndex { $0.id == fid } ?? 0)
    }
    func friendWhen(_ i: Int) -> String { i == 0 ? "처음부터 · 홈" : "인터미션 \(i)주차" }

    // MARK: 오늘의 1분
    var today: DayLog { dayLog[day] ?? DayLog() }
    var streak: Int {
        var k = 0, d = day - (today.answer != nil ? 0 : 1)
        while d >= 0 {
            let ok = dayLog[d].map { $0.answer != nil } ?? (d < 5)
            if !ok { break }
            k += 1; d -= 1
        }
        return k
    }
    var itemsOn: Int { playOn ? min(10, streak / 7) : 0 }

    var dayMoves: (Double, Double) {
        let mv: [(Double, Double)] = [(1.9, 0.4), (-1.2, -0.5), (0.6, 0.2), (-2.8, -1.1), (1.1, 0.8), (-2.2, 0.4), (-0.7, 0.1)]
        return mv[day % 7]
    }
    var routineFact: String {
        let (a, b) = dayMoves, w = drnkWeight
        let tot = (w * a + (1 - w) * b) / 100
        let vY = total / (1 + tot), distNow = cost / total - 1, distY = cost / vY - 1
        let s2 = { (x: Double) in (x >= 0 ? "+" : "") + String(format: "%.1f%%", x) }
        if route == .novice {
            return "오늘 \(s2(tot * 100)). 모은 돈은 목표의 \(AppModel.pct(gA / max(1, gK)))예요. 다음 적립일에 \(AppModel.wonK(gM))이 더해져요."
        }
        if route == .plus {
            let k = gK * 1e4
            return "오늘 \(s2(tot * 100)) (DRNK \(s2(a)) · QQQ \(s2(b))). 목표까지 \(String(format: "%.1f", vY / k * 100))% → \(String(format: "%.1f", total / k * 100))%."
        }
        return "오늘 \(s2(tot * 100)) (DRNK \(s2(a)) · QQQ \(s2(b))). 본전까지 \(String(format: "%.1f", distY * 100))% → \(String(format: "%.1f", distNow * 100))%" + (distNow < distY ? ", 가까워졌어요." : ", 조금 멀어졌어요.")
    }

    struct Question { let tag: String; let q: String; let opts: [String]; let right: Int; let fb: (Int) -> String }
    var question: Question {
        let p12 = Int((planBreakEven(years: 1) * 100).rounded()), p6 = Int((planBreakEven(years: 0.5) * 100).rounded())
        let near = max(30, min(70, Int((Double(p12) / 10).rounded()) * 10))
        let opts = [near - 20, near, near + 20]
        let nearR = opts.indices.min { abs(opts[$0] - p12) < abs(opts[$1] - p12) } ?? 1
        let (a, b) = dayMoves
        let s2 = { (x: Double) in (x >= 0 ? "+" : "") + String(format: "%.1f%%", x) }
        let w = Self.pct(drnkWeight), pw = Self.pct(planWeight)
        if route != .recover {
            let principal = gM * 12 * Double(gY)
            let goalQs: [Question] = [
                .init(tag: "계산 퀴즈", q: "매달 \(Self.wonK(gM))씩 \(gY)년 넣으면 넣은 원금만 얼마일까요?", opts: [Self.wonK(principal * 0.5), Self.wonK(principal), Self.wonK(principal * 1.5)], right: 1,
                      fb: { _ in "\(Self.wonK(self.gM)) × 12달 × \(self.gY)년 = \(Self.wonK(principal)). 그 위에 수익이 더해져 목표로 가요." }),
                .init(tag: "이번 달 적립", q: "이번 달 \(Self.wonK(gM)), 넣었나요?", opts: ["넣었어요", "아직이요"], right: -1,
                      fb: { $0 == 0 ? "좋아요. 3개월 블록에 반영해 둘게요." : "괜찮아요. 적립일 알림이 한 번 더 알려 드릴게요." }),
                .init(tag: "오늘 마음", q: "오늘 목표까지의 길, 어떻게 느껴져요?", opts: ["멀어요", "보통이에요", "가까워요"], right: -1,
                      fb: { $0 == 0 ? "먼 길은 매달 넣는 돈이 끌고 가요. 1년 뒤 그래프를 한 번 보세요." : "그 느낌 그대로 가요. 내일도 1분이면 돼요." }),
            ]
            return goalQs[day % goalQs.count]
        }
        let qs: [Question] = [
            .init(tag: "확률 퀴즈", q: "지금 계획(\(selectedPlan.name))으로 1년 안에 본전에 닿을 확률은 어느 쪽에 가까울까요?", opts: opts.map { "약 \($0)%" }, right: nearR,
                  fb: { _ in "모형 계산으로 약 \(p12)%예요. 6개월 안이면 \(p6)%. 기간이 길수록 높아져요." }),
            .init(tag: "오늘 움직임", q: "오늘 더 많이 움직인 종목은?", opts: ["DRNK", "QQQ"], right: abs(a) >= abs(b) ? 0 : 1,
                  fb: { _ in "DRNK \(s2(a)), QQQ \(s2(b)). DRNK 비중이 \(w)라 전체도 DRNK를 많이 따라가요." }),
            .init(tag: "비중 확인", q: "DRNK 비중 \(w), 계획은 \(pw)예요. 이번 주에 맞춰 볼까요?", opts: ["이번 주에 할게요", "아직이요"], right: -1,
                  fb: { $0 == 0 ? "미션 판의 내 계획 화면에서 옮길 금액을 볼 수 있어요." : "괜찮아요. 비중 이탈 알림이 대신 지켜볼게요." }),
            .init(tag: "오늘 마음", q: "오늘 내 투자, 어떻게 느껴져요?", opts: ["불안해요", "괜찮아요", "기대돼요"], right: -1,
                  fb: { $0 == 0 ? "불안한 날엔 하루 숫자 말고 종목 상세의 1년 칩을 보세요. 길이 더 잘 보여요." : "좋아요. 내일도 1분이면 돼요." }),
        ]
        return qs[day % qs.count]
    }
    func markSeen() { var t = today; t.seen = true; dayLog[day] = t }
    func answer(_ i: Int) { var t = today; t.seen = true; t.answer = i; dayLog[day] = t }
    var routineDoneText: String {
        let s = streak
        if s > 0 && s % 7 == 0 && s / 7 <= 10 { return "오늘 루틴 끝! \(s)일 연속이라 쉼터에 물건(\(Shelter.items[s / 7 - 1].name))이 돌아왔어요." }
        if s / 7 < 10 { return "오늘 루틴 끝! \(7 - s % 7)일 더 하면 쉼터에 물건(\(Shelter.items[s / 7].name))이 돌아와요." }
        return "오늘 루틴 끝! 물건이 모두 돌아왔어요."
    }
    var homeSay: String {
        if !playUnlocked { return "앱 시작 3단계를 마치면 매일 오늘 숫자를 하나 가져올게요." + (homeFriend == "seri" ? " 거기까지만요." : "") }
        if today.answer != nil {
            let s = streak
            return "오늘은 여기까지예요. " + (s / 7 < 10 ? "\(7 - s % 7)일 더 오면 쉼터에 물건(\(Shelter.items[s / 7].name))이 돌아와요." : "내일 또 숫자 하나 가져올게요.")
        }
        return "오늘 숫자 가져왔어요. 한 번만 보고 가요."
    }

    // MARK: 3년 전망 (로그정규 근사, 50%·90% 범위)
    struct Forecast { let q5, q25, q50, q75, q95: [Double]; let prob: (Double) -> Double; let mu, sigma, muBase, muTrend: Double }
    var forecast: Forecast {
        let rs = rows, tot = max(1, total)
        let parts: [(Double, (Double, Double, Double))] = rs.isEmpty || route == .novice ? [(1, (0.07, 0.11, 0.16))]
            : rs.map { ($0.value / tot, Sample.lensParams[$0.id] ?? (0.07, 0.08, 0.25)) }
        let muB = parts.reduce(0) { $0 + $1.0 * $1.1.0 }, muS = parts.reduce(0) { $0 + $1.0 * $1.1.1 }
        var v = 0.0
        for a in parts.indices { for b in parts.indices {
            v += parts[a].0 * parts[b].0 * parts[a].1.2 * parts[b].1.2 * (a == b ? 1 : 0.5)
        } }
        var sg = sqrt(v); let t = trust / 100
        var mu = lens == .base ? muB : lens == .smooth ? muS : muB * (1 - t) + muS * t
        if shock { mu -= 0.02; sg *= 1.1 }
        let g = mu - sg * sg / 2, V0 = trackValue, K = keyValue, M = monthly * 1e4
        let qAt = { (T: Double, z: Double) -> Double in
            V0 * exp(g * T + z * sg * sqrt(T)) + M * 12 * T * exp(g * T / 2 + z * sg * sqrt(T) / 2)
        }
        let pAt = { (T: Double) -> Double in
            if T <= 0 { return V0 >= K ? 1 : 0 }
            let eff = V0 + M * 12 * T * exp(-g * T / 2)
            return eff <= 0 ? 0 : Self.normCDF((log(eff / K) + g * T) / (sg * sqrt(T)))
        }
        let q = { (z: Double) in (0...36).map { i in i == 0 ? V0 : qAt(Double(i) / 12, z) } }
        return Forecast(q5: q(-1.645), q25: q(-0.674), q50: q(0), q75: q(0.674), q95: q(1.645), prob: pAt, mu: mu, sigma: sg, muBase: muB, muTrend: muS)
    }

    // MARK: 1000칸 (회복: 본전 = 1000칸)
    var cellUnit: Double { cost / 1000 }
    func cells(_ v: Double) -> Int { max(0, min(1000, Int(floor(v / max(1, cellUnit))))) }
    var cellsNow: Int { cells(total) }
    var cellsFloor: Int { cells(total / 1.064) }      // 시작 뒤 가장 낮았던 날 (시안 가정: 지금보다 6% 낮음)
    var cellsYesterday: Int { cells(total / (1 + todayMove)) }

    // MARK: 주간 예보 (월요일에 적은 금요일 평가액 50% 범위)
    var weekRange: (lo: Double, hi: Double, actual: Double) {
        let now = trackValue
        let wSg = forecast.sigma / sqrt(52), base = now / 1.004
        let actual = weekFriday ? now * (1 - 0.006) : now
        return (base * exp(-0.674 * wSg), base * exp(0.674 * wSg), actual)
    }
    let pastWeeks = [1, 0, 1, 1, 0, 1, 0, 1]

    // MARK: 시안 조작
    enum Demo { case fresh, week1, all }
    /// 첫 질문 답: 마이너스 = 회복 루트 미션 1부터, 플러스 = 목표 루트(샘플은 이익 난 단가), 시작 전 = 목표 루트 적립
    func startRoute(_ r: Route) {
        // 사용자가 넣은 종목이 있으면 루트를 다시 골라도 그대로 불러온다 (예시 종목은 처음일 때만)
        let mine: [Holding]? = holdings.isEmpty || holdings == Sample.startHoldings ? nil : holdings
        resetDemo(.fresh)
        if let mine { holdings = mine }
        else if r == .plus { holdings = [Holding(symbol: "DRNK", qty: 60, avg: 180), Holding(symbol: "QQQ", qty: 10, avg: 400)] }
        if r == .plus { taxGain = 0 }
        if r != .recover { switchRoute(r); gDone = [] }
        day = 0                  // 실제로 시작하는 날이 1일째 (시연 상태는 6일째)
        onboarded = true; UserDefaults.standard.set(true, forKey: "onboarded")
        tab = .board
    }

    func resetDemo(_ d: Demo) {
        holdings = Sample.startHoldings
        switch d {
        case .fresh: done = []; weeks = []; nxStep = 0
        case .week1: done = [1, 2, 3, 5]; weeks = [.kept]; nxStep = 3
        case .all: done = [1, 2, 3, 5, 6]; weeks = [.kept, .kept, .changed, .kept]; nxStep = 3
        }
        route = .recover; gDone = []; gWeeks = []; gWeekCur = nil
        weekCur = nil; day = 5; dayLog = [:]; homeFriend = "seri"; readPos = []; readLast = nil
        weekGuess = nil; weekFriday = false; planKey = "balance"; horizon = 6; quizAnswer = nil
        taxGain = 600; taxSellQty = 0; boardPath = []
    }
}
