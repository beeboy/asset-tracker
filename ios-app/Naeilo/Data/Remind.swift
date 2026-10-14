import SwiftUI
import Observation

// 홈 리마인드: 앱의 단계·달성, 일정, 내 종목 분석, 설정·후원·새 소식을 푸시 알림 없이 홈에서만 보여 준다.
// 쉼터 친구가 말풍선으로 하나씩 전하고 (홈을 열 때마다 하나, 하루 3개까지), 일정은 노란 띠, 나머지는 소식함에 30일 남는다.
// 항목표: project-files notes/홈-리마인드-항목표-2026-10-11.md

enum RemindCat: String, CaseIterable, Codable {
    case done, doing, event, analysis, news
    var label: String {
        switch self { case .done: "달성"; case .doing: "진행 중"; case .event: "일정"; case .analysis: "분석"; case .news: "설정·새 소식" }
    }
    var color: Color {
        switch self { case .done: Theme.green; case .doing: Theme.purple; case .event: Theme.orange; case .analysis: Theme.blue; case .news: Theme.teal }
    }
    var icon: String {
        switch self { case .done: "sparkles"; case .doing: "hourglass"; case .event: "calendar"; case .analysis: "chart.line.uptrend.xyaxis"; case .news: "gift" }
    }
}

/// 소식을 누르면 갈 곳
enum RemindGo: Hashable {
    case nowhere, shelter, char(String), me, reader(Int), board, calm, myPath, forecast, rateScen, external, hold(String), support, widgets, sync, alerts, krSum

    /// 사이트 소식 파일의 go 글자 ("shelter", "char:ir", "read:3", "hold:TSLA" …)
    init(_ s: String?) {
        let s = s ?? ""
        if s.hasPrefix("char:") { self = .char(String(s.dropFirst(5))); return }
        if s.hasPrefix("read:"), let i = Int(s.dropFirst(5)) { self = .reader(i); return }
        if s.hasPrefix("hold:") { self = .hold(String(s.dropFirst(5))); return }
        let m: [String: RemindGo] = ["shelter": .shelter, "me": .me, "board": .board, "calm": .calm, "myPath": .myPath, "forecast": .forecast,
                                     "rateScen": .rateScen, "external": .external, "support": .support, "widgets": .widgets, "sync": .sync, "alerts": .alerts]
        self = m[s] ?? .nowhere
    }
}

struct RemindItem: Identifiable, Equatable {
    let id: String           // 같은 소식이면 같은 id (본 기록에 쓴다)
    let cat: RemindCat
    let text: String
    var go: RemindGo = .nowhere
    var once = true          // 한 번: 보면 지난 소식으로. 아니면 조건이 풀릴 때까지 남는다
    var big = false          // 위젯에도 보여 줄 큰 달성 (내 캐릭터 열림, 새 친구, 1000칸 이정표)
    var day: String? = nil   // 일정 날짜 (yyyy-MM-dd)
    var sub: String? = nil   // 일정의 대상 (전 종목, 환율, 종목 이름)
}

/// 기기에 남는 기록 (다른 기기와 맞추지 않는다)
struct RemindLog: Codable {
    var since = 0.0                     // 기록을 시작한 때 (이보다 오래된 개발자 소식은 건너뛴다)
    var first: [String: Double] = [:]   // 소식이 처음 뜬 때 (30일 보관)
    var seen: [String: Double] = [:]    // 본 때
    var off: Set<RemindCat> = []        // 설정에서 끈 분류
    var bubbleDay: String? = nil        // 말풍선 하루 3개 세기
    var bubbleN = 0
    var bubble: String? = nil           // 지금 말풍선에 띄운 소식
    var tier: Int? = nil                // 지난번 후원 단계
    var cellsMax = 0                    // 1000칸 최고 기록
    var cellsWeek: String? = nil        // 최고 기록을 잰 주와 그 주가 시작될 때의 최고 기록
    var cellsWeekMax = 0
    var weeks: [String: Wk] = [:]       // 주간 예보: 주(yyyy-Www)마다 처음 연 날의 범위와 마지막으로 본 평가액
    var snap: [String: [Double]] = [:]  // 분석: 주마다 [목표(본전) 가능성, 내 길 앞섬]
    var analysisWeek: String? = nil     // 분석 소식이 마지막으로 뜬 주 (주 1회까지)
    var guessHits: Int? = nil           // 맞혀보기를 맞힌 횟수 (배지). 옛 기록에 없는 키라 옵셔널
    struct Wk: Codable {
        var lo: Double; var hi: Double; var last: Double
        var guess: String? = nil        // 그 주에 고른 답 (lo·in·hi)
        var scored: Bool? = nil         // 맞힌 횟수에 이미 셌는지
        /// 금요일 값이 범위의 어디서 끝났는지 (lo·in·hi)
        var pos: String { last < lo ? "lo" : last > hi ? "hi" : "in" }
    }
}

/// 개발자 소식: 앱 버전마다 적어 두는 목록 + 사이트 data/app_news.json (앱 업데이트 없이 올라온 글·일정)
struct RemindNews: Codable {
    let id: String
    let date: String
    let text: String
    var go: String? = nil

    static let app: [RemindNews] = [
        .init(id: "n1011.remind", date: "2026-10-11", text: "홈에 리마인드가 생겼어요. 소식은 친구가 전하고, 지난 소식은 ‘새 소식’에 모여요."),
        .init(id: "n1011.edit", date: "2026-10-11", text: "종목 목록에서 연필을 누르면 바로 수량·단가를 고칠 수 있어요.", go: "hold:"),
        .init(id: "n1011.items", date: "2026-10-11", text: "쉼터 물건을 누르면 배치에서 빼고 다시 놓을 수 있어요.", go: "shelter"),
    ]
}

@Observable
final class Remind {
    static let shared = Remind()
    private static let key = "remind.v1"
    private static let keep = 30 * 86400.0
    private static let bubbleMax = 3

    var log: RemindLog { didSet { save() } }
    /// 지금 소식 (refresh 가 채운다. 화면은 이 값만 읽는다)
    private(set) var items: [RemindItem] = []
    private(set) var site: [RemindNews] = []
    @ObservationIgnored private var siteAt = Date.distantPast

    init() {
        log = UserDefaults.standard.data(forKey: Self.key).flatMap { try? JSONDecoder().decode(RemindLog.self, from: $0) } ?? RemindLog()
        if let d = Store.readData("app_news.json") { site = (try? JSONDecoder().decode([RemindNews].self, from: d)) ?? [] }
    }
    private func save() { if let d = try? JSONEncoder().encode(log) { UserDefaults.standard.set(d, forKey: Self.key) } }

    // MARK: 보여 줄 것

    func isOn(_ c: RemindCat) -> Bool { !log.off.contains(c) }
    func setOn(_ c: RemindCat, _ on: Bool) { if on { log.off.remove(c) } else { log.off.insert(c) } }
    private var shown: [RemindItem] { items.filter { isOn($0.cat) } }
    func isSeen(_ i: RemindItem) -> Bool { log.seen[i.id] != nil }
    /// 안 본 소식 (말풍선·‘새 소식 n’)
    var fresh: [RemindItem] { shown.filter { $0.once && $0.cat != .event && !isSeen($0) } }
    var doing: [RemindItem] { shown.filter { !$0.once && $0.cat != .event } }
    var past: [RemindItem] {
        shown.filter { $0.once && $0.cat != .event && isSeen($0) }.sorted { (log.first[$0.id] ?? 0) > (log.first[$1.id] ?? 0) }
    }
    /// 이번 주 일정 (오늘부터 7일)
    var events: [RemindItem] { shown.filter { $0.cat == .event } }
    /// 홈 노란 띠: 오늘부터 3일 안의 일정 (내 종목 먼저)
    var strip: RemindItem? {
        let until = Day.str(Date().addingTimeInterval(3 * 86400))
        return events.first { ($0.day ?? "") <= until }
    }
    /// 홈 말풍선: 지금 띄운 소식
    var bubble: RemindItem? { log.bubble.flatMap { id in items.first { $0.id == id } }.flatMap { isOn($0.cat) ? $0 : nil } }

    /// 홈을 열 때: 다음 소식을 말풍선에 (하루 3개까지). 띄운 소식은 본 것으로 친다
    func nextBubble() {
        let today = Day.today
        if log.bubbleDay != today { log.bubbleDay = today; log.bubbleN = 0 }
        guard log.bubbleN < Self.bubbleMax else { log.bubble = nil; return }
        let order: [RemindCat] = [.done, .news, .analysis]
        let new = fresh
        let pick = order.lazy.compactMap { c in new.first { $0.cat == c } }.first
        // 서재: 수·일요일에만 (주 1~2회)
        let wd = Calendar.current.component(.weekday, from: Date())
        if pick == nil, wd == 4 || wd == 1, let r = doing.first(where: { $0.id.hasPrefix("read.") }), log.seen[r.id + "." + today] == nil {
            log.seen[r.id + "." + today] = Date().timeIntervalSince1970
            log.bubble = r.id; log.bubbleN += 1; return
        }
        guard let p = pick else { log.bubble = nil; return }
        log.bubble = p.id; log.bubbleN += 1
        log.seen[p.id] = Date().timeIntervalSince1970
    }
    func markAllSeen() {
        let t = Date().timeIntervalSince1970
        for i in fresh { log.seen[i.id] = t }
    }

    /// 위젯 말풍선: 오늘 처음 뜬 큰 달성 하나
    var widgetNews: String? {
        let today = Day.today
        return items.first { $0.big && log.first[$0.id].map { Day.str(Date(timeIntervalSince1970: $0)) == today } == true && isOn($0.cat) }?.text
    }
    /// 인물 · 블록 위젯 표시: 오늘 넘은 1000칸 이정표
    var widgetBadge: String? {
        let today = Day.today
        guard let i = items.first(where: { $0.id.hasPrefix("cells.m") }), let t = log.first[i.id], Day.str(Date(timeIntervalSince1970: t)) == today else { return nil }
        return "★ \(i.id.dropFirst(7))칸 돌파"
    }

    // MARK: 만들기

    /// 지금 상태로 소식을 다시 만든다 (홈을 열 때, 1분 갱신 때)
    @MainActor func refresh(_ m: AppModel) {
        let now = Date().timeIntervalSince1970
        if log.since == 0 { log.since = now }
        var out: [RemindItem] = []
        out += achievements(m)
        out += progress(m)
        out += MarketEvents.upcoming(held: m.rows.map(\.id), days: 7).map { e in
            RemindItem(id: "ev.\(e.id).\(e.day)", cat: .event, text: e.kind, go: .external, once: false, day: e.day, sub: e.sub)
        }
        out += analysis(m)
        out += news()
        // 한 번 소식: 처음 뜬 때를 적고, 30일 지나면 뺀다
        // (지금도 조건이 맞는 소식의 기록은 지우지 않는다. 지우면 다시 새 소식으로 뜬다)
        var first = log.first
        for i in out where i.once && first[i.id] == nil { first[i.id] = now }
        let live = Set(out.map(\.id))
        out = out.filter { !$0.once || now - (first[$0.id] ?? now) < Self.keep }
        first = first.filter { live.contains($0.key) || now - $0.value < Self.keep * 2 }
        if first != log.first { log.first = first }
        let seen = log.seen.filter { first[$0.key] != nil || now - $0.value < Self.keep * 2 }
        if seen.count != log.seen.count { log.seen = seen }
        if out != items { items = out }
        Task { await loadSite() }
    }

    /// 앱 단계·달성 (같은 종류는 가장 최근 단계 하나만)
    @MainActor private func achievements(_ m: AppModel) -> [RemindItem] {
        var a: [RemindItem] = []
        if m.playOn { a.append(.init(id: "start", cat: .done, text: "앱 시작 3단계를 마쳤어요. 오늘의 1분이 열렸어요. 매일 1분이면 돼요.")) }
        // 인터미션
        if m.interDone {
            a.append(.init(id: "inter.done.\(m.route.rawValue)", cat: .done, text: "인터미션을 다 마쳤어요. 친구가 모두 모였어요.", go: .shelter))
        } else if m.route == .recover, (1...3).contains(m.weeks.count) {
            let n = m.weeks.count
            a.append(.init(id: "inter.r.\(n)", cat: .done, text: "인터미션 \(n)/4주 끝! 다음 주는 ‘\(Shelter.weekSteps[n].task)’예요.", go: .board))
        } else if m.isGoal, (1...2).contains(m.gWeeks.count) {
            let n = m.gWeeks.count
            a.append(.init(id: "inter.g.\(n)", cat: .done, text: "인터미션 \(n)/3달 끝! 다음 달은 ‘\(AppModel.goalMonthSteps[n].task)’예요.", go: .board))
        }
        // 새 친구와 그 인물 위젯
        let k = m.friendsOpen
        if k >= 1 && !m.devAll {
            let f = Shelter.friends[min(k, Shelter.friends.count - 1)]
            a.append(.init(id: "friend.\(k)", cat: .done, text: "\(f.name)가 쉼터에 왔어요. 인사해 볼래요?", go: .char(f.id), big: true))
            a.append(.init(id: "widget.\(k)", cat: .done, text: "\(f.name) 인물 위젯을 홈 화면에 둘 수 있어요.", go: .widgets))
        }
        // 돌아온 물건
        let n = m.itemsOn
        if n >= 1 && !m.devAll {
            a.append(.init(id: "items.\(n)", cat: .done, text: "물건이 하나 돌아왔어요: \(Shelter.items[n - 1].name) (\(n)/10).", go: .shelter))
        }
        // 평정 지수
        let c = m.calm
        if c.events.count >= Calm.minEvents {
            a.append(.init(id: "calm.first", cat: .done, text: "기록 \(Calm.minEvents)건을 채워 평정 지수가 처음 나왔어요: \(c.stats().index)점.", go: .calm))
        }
        if let t = c.type, let kd = Calm.kind(t) {
            let who = m.friendName(t)
            let text = c.prevType == nil ? "투자 유형이 정해졌어요: ‘\(kd.title)’. \(who)와 닮았어요." : "이번 달은 ‘\(kd.title)’에 더 가까워요. \(who)와 함께해요."
            a.append(.init(id: "calm.type.\(t).\(c.typeMonth ?? "")", cat: .done, text: text, go: .calm))
            if c.stage >= 2 {
                let d = c.stage == 2 ? 14 : 60
                a.append(.init(id: "calm.stage.\(t).\(c.stage)", cat: .done, text: "\(who)와 함께한 지 \(d)일. 말투가 조금 바뀔 거예요.", go: .char(t + ":invest")))
            }
        }
        if let ch = calmChange(c) {
            a.append(.init(id: "calm.chg.\(ch.month)", cat: .done, text: "평정 지수가 지난달 \(ch.a)점에서 \(ch.b)점으로 \(ch.b > ch.a ? "올랐어요" : "내렸어요").", go: .calm))
        }
        // 다음 걸음: 습관 사다리에서 마친 걸음 (최근 3개), 세리가 지킨 반년
        if let l = c.ladder {
            let who = m.voice(m.calmSpeaker)
            for d in l.done.suffix(3) {
                var text = Calm.stepLine(d.type, d.step, who: who)
                if d == l.done.last, d.type == l.type, (1...3).contains(l.step), let nx = Calm.steps[l.type]?[l.step - 1] {
                    text += " 다음 걸음은 ‘\(nx.name)’."
                }
                a.append(.init(id: "calm.step.\(d.type).\(d.step).\(d.day)", cat: .done, text: text, go: .calm))
            }
            if l.keeps > 0 {
                a.append(.init(id: "calm.keep.\(l.keeps)", cat: .done, text: "평정 지수 80 이상을 \(6 * (l.keeps + 1))달 지켰어요.", go: .calm))
            }
        }
        // 내 캐릭터 조건
        let g = [m.meGate1, m.meGate2, m.meGate3].filter { $0 }.count
        if g == 3 {
            let text = m.me == nil ? "이제 내 캐릭터가 홈과 위젯에서 함께할 수 있어요. 만들어 볼래요?" : "이제 내 캐릭터가 홈과 위젯에서 함께해요."
            a.append(.init(id: "me.open", cat: .done, text: text, go: .me, big: true))
        } else if g >= 1 {
            a.append(.init(id: "me.gate.\(g)", cat: .done, text: "내 캐릭터 조건 \(g)/3 달성.", go: .me))
        }
        if m.bgUnlocked { a.append(.init(id: "bg.open", cat: .done, text: "쉼터 배경을 바꿀 수 있어요. 12곳 중 골라 봐요.", go: .shelter)) }
        a += cells(m)
        a += week(m)
        return a
    }

    /// 1000칸 이정표·최고 기록
    @MainActor private func cells(_ m: AppModel) -> [RemindItem] {
        guard m.playUnlocked, m.keyValue > 0, !m.rows.isEmpty else { return [] }
        let c = Int(max(0, min(1, m.trackValue / m.keyValue)) * 1000)
        let wk = Self.week(Date())
        if log.cellsWeek != wk { log.cellsWeek = wk; log.cellsWeekMax = log.cellsMax }
        if c > log.cellsMax { log.cellsMax = c }
        var a: [RemindItem] = []
        if let ms = [1000, 750, 500, 250, 100].first(where: { log.cellsMax >= $0 }) {
            let text = ms == 1000 ? "1000칸을 다 채웠어요!" : "1000칸 중 \(ms)칸을 넘었어요." + (ms == 250 ? " 4분의 1이에요!" : ms == 500 ? " 절반이에요!" : "")
            a.append(.init(id: "cells.m\(ms)", cat: .done, text: text, go: .board, big: true))
        }
        if log.cellsWeekMax > 0, log.cellsMax > log.cellsWeekMax {
            a.append(.init(id: "cells.best.\(wk)", cat: .done, text: "1000칸 최고 기록 \(log.cellsMax)칸을 새로 썼어요.", go: .board))
        }
        return a
    }

    /// 맞혀보기 배지: 맞힌 횟수가 이만큼 되면 받는다
    static let badges: [(n: Int, name: String, icon: String)] = [
        (1, "첫 적중", "star.fill"), (3, "세 번 적중", "sparkles"), (5, "다섯 번 적중", "medal.fill"),
        (10, "열 번 적중", "trophy.fill"), (20, "예보 달인", "crown.fill"),
    ]

    /// 끝난 주: 지난주들, 또는 토·일요일의 이번 주 (오래된 순)
    static func endedWeeks(_ w: [String: RemindLog.Wk], now: Date = Date()) -> [String] {
        let wk = week(now), wd = Calendar.current.component(.weekday, from: now)
        return w.keys.filter { $0 < wk || ($0 == wk && (wd == 7 || wd == 1)) }.sorted()
    }

    /// 주간 예보 기록: 그 주 처음 연 날(월~금)의 범위를 적어 두고, 고른 답과 마지막 평가액을 이어 적는다.
    /// 끝난 주의 고른 답은 한 번만 맞힌 횟수에 센다. 홈 리마인드와 미션 탭 예보 카드가 함께 부른다.
    @MainActor func recordWeek(_ m: AppModel) {
        guard m.playUnlocked, !m.rows.isEmpty else { return }
        let now = Date(), wk = Self.week(now), wd = Calendar.current.component(.weekday, from: now)
        var w = log.weeks
        if w[wk] == nil, (2...6).contains(wd) {
            let r = m.weekRange
            if m.weekGuess != nil { m.weekGuess = nil }   // 새 주: 지난주에 고른 답은 지난주 기록에만 남는다
            w[wk] = .init(lo: r.lo, hi: r.hi, last: m.trackValue)
        } else if w[wk] != nil {
            w[wk]?.last = m.trackValue
            if (2...6).contains(wd) { w[wk]?.guess = m.weekGuess }   // 금요일 마감 전까지만 고칠 수 있다
        }
        var hits = log.guessHits ?? 0
        for k in Self.endedWeeks(w, now: now) {
            guard let x = w[k], let g = x.guess, x.scored != true else { continue }
            w[k]?.scored = true
            if g == x.pos { hits += 1 }
        }
        if w.count > 12 { for k in w.keys.sorted().prefix(w.count - 12) { w.removeValue(forKey: k) } }
        if hits != (log.guessHits ?? 0) { log.guessHits = hits }
        if w.count != log.weeks.count || w[wk]?.last != log.weeks[wk]?.last || w[wk]?.guess != log.weeks[wk]?.guess
            || w.contains(where: { $0.value.scored != log.weeks[$0.key]?.scored }) { log.weeks = w }
    }

    /// 주간 예보 결과: 그 주 처음 연 날의 범위와 금요일 뒤 평가액을 견준다
    @MainActor private func week(_ m: AppModel) -> [RemindItem] {
        guard m.playUnlocked, !m.rows.isEmpty else { return [] }
        recordWeek(m)
        let w = log.weeks, ended = Self.endedWeeks(w)
        var a: [RemindItem] = []
        if let b = Self.badges.last(where: { $0.n <= (log.guessHits ?? 0) }) {
            a.append(.init(id: "badge.\(b.n)", cat: .done, text: "맞혀보기 배지 ‘\(b.name)’를 받았어요. 예보를 \(b.n)번 맞혔어요.", go: .board))
        }
        guard let last = ended.last, let r = w[last] else { return a }
        let inside = r.last >= r.lo && r.last <= r.hi
        var run = 0
        for k in ended.reversed() { guard let x = w[k], x.last >= x.lo && x.last <= x.hi else { break }; run += 1 }
        let text = inside ? "이번 주 예보 적중! 범위 안에서 마쳤어요." + (run >= 2 ? " \(run)주 연속이에요." : "")
            : "이번 주는 예보 범위 \(r.last > r.hi ? "위" : "아래")로 나갔어요. 이런 주도 절반쯤 있어요."
        return a + [.init(id: "wk.\(last)", cat: .done, text: text, go: .board)]
    }

    /// 평정 지수 달 변화: 이번 달 전까지의 기록과 지금을 견준다 (양쪽 10건 이상, 5점 넘게 바뀌었을 때)
    private func calmChange(_ c: Calm) -> (month: String, a: Int, b: Int)? {
        let month = String(Day.today.prefix(7))
        let before = c.events.filter { $0.day < month + "-01" }
        guard before.count >= Calm.minEvents, before.count < c.events.count else { return nil }
        let a = Int(Calm.index(before).rounded()), b = Int(Calm.index(c.events).rounded())
        return abs(b - a) >= 5 ? (month, a, b) : nil
    }

    /// 진행 중 (조건이 풀릴 때까지 소식함에 남는다)
    @MainActor private func progress(_ m: AppModel) -> [RemindItem] {
        var a: [RemindItem] = []
        if m.playOn && !m.interDone {
            var t = m.isGoal
                ? "인터미션 \(m.gWeeks.count)/3달 · 이번 달: \(AppModel.goalMonthSteps[min(m.gWeeks.count, 2)].task)"
                : "인터미션 \(m.weeks.count)/4주 · 이번 주: \(Shelter.weekSteps[min(m.weeks.count, 3)].task)"
            if !m.isGoal, case .wait(let d) = m.weekGate { t += " · \(m.weeks.count + 1)주차는 \(d)일 뒤 열려요" }
            a.append(.init(id: "do.inter", cat: .doing, text: t, go: .board, once: false))
        }
        let n = m.calm.events.count
        if (1..<Calm.minEvents).contains(n) {
            a.append(.init(id: "do.calm", cat: .doing, text: "평정 지수까지 기록 \(Calm.minEvents - n)건 남았어요.", go: .calm, once: false))
        }
        if let l = m.calm.ladder, (1...3).contains(l.step), let st = Calm.steps[l.type]?[l.step - 1], let p = m.calm.progress() {
            a.append(.init(id: "do.ladder", cat: .doing, text: "다음 걸음 \(l.step)/3 ‘\(st.name)’ · \(p.text)", go: .calm, once: false))
        }
        let left = [(m.meGate1, m.meGate1Text), (m.meGate2, "평정 지수 유형 매칭"), (m.meGate3, "다방커피 후원")].filter { !$0.0 }.map { $0.1 }
        if (1...2).contains(left.count) {
            a.append(.init(id: "do.me", cat: .doing, text: "내 캐릭터 조건 \(3 - left.count)/3 · 남은 조건: " + left.joined(separator: ", "), go: .me, once: false))
        }
        if let i = (0..<Shelter.friends.count).first(where: { !m.friendOn($0) }) {
            a.append(.init(id: "do.friend", cat: .doing, text: "다음 친구 \(Shelter.friends[i].name)는 \(m.friendWhen(i))에 와요.", go: .shelter, once: false))
        }
        if m.playOn, m.itemsOn < 10 {
            a.append(.init(id: "do.items", cat: .doing, text: "오늘의 1분을 하면 물건(\(Shelter.items[m.itemsOn].name))이 돌아와요.", go: .shelter, once: false))
        }
        if m.playUnlocked && m.weekGuess == nil {
            a.append(.init(id: "do.week", cat: .doing, text: "이번 주 금요일 평가액은 어디쯤일까요? 예보를 골라 봐요.", go: .board, once: false))
        }
        // 서재: 열렸지만 다 안 읽은 장 하나
        let story = Story.shared
        if let i = (0..<Shelter.chapters.count).first(where: { m.chapterOn($0) && !story.isDone(.side, $0) }) {
            let ch = Shelter.chapters[i], started = m.readPos.contains(i)
            a.append(.init(id: "read.\(i)", cat: .doing, text: "서재에 외전 \(ch.title) ‘\(ch.name)’이 기다리고 있어요." + (started ? " 이어 읽을래요?" : ""),
                           go: .reader(i), once: false))
        }
        return a
    }

    /// 분석 소식 (내 종목 기준, 주 1회까지). 숫자는 매일 흔들려서 주마다 한 번 견준다
    @MainActor private func analysis(_ m: AppModel) -> [RemindItem] {
        guard !m.rows.isEmpty else { return [] }
        let wk = Self.week(Date())
        let prob = m.isGoal ? m.forecast.prob(Double(m.gY)) : m.planBreakEven(years: 1)
        let gap = m.myPathGapToday
        var snap = log.snap
        if snap[wk] == nil { snap[wk] = [prob, gap] }
        if snap.count > 10 { for k in snap.keys.sorted().prefix(snap.count - 10) { snap.removeValue(forKey: k) } }
        if snap.count != log.snap.count { log.snap = snap }
        let tail = " 종목 추천이 아닙니다."
        var cand: [RemindItem] = []
        if let pk = snap.keys.filter({ $0 < wk }).max(), let p = snap[pk], p.count == 2 {
            if abs(prob - p[0]) >= 0.05 {
                let what = m.isGoal ? "목표에 닿을 가능성" : "1년 안 본전 가능성"
                cand.append(.init(id: "an.prob.\(wk)", cat: .analysis, text: "\(what)이 \(AppModel.pct(p[0]))에서 \(AppModel.pct(prob))로 \(prob > p[0] ? "올랐어요" : "내렸어요")." + tail, go: m.isGoal ? .forecast : .myPath))
            }
            if (p[1] >= 0) != (gap >= 0) {
                cand.append(.init(id: "an.gap.\(wk)", cat: .analysis, text: "내 길: 기준선보다 \(String(format: "%.1f", abs(gap) * 100))% \(gap >= 0 ? "앞서" : "뒤처져") 있어요. 지난주와 반대쪽이에요." + tail, go: .myPath))
            }
        }
        let tot = max(1, m.total)
        if m.rows.count >= 2, let top = m.rows.max(by: { $0.value < $1.value }), top.value / tot >= 0.5 {
            cand.append(.init(id: "an.conc.\(top.id).\(Day.today.prefix(7))", cat: .analysis,
                              text: "\(top.sym.short) 비중이 \(AppModel.pct(top.value / tot))예요. 한쪽으로 기울었어요." + tail, go: .forecast))
        }
        if let n = MacroData.shared.file?.now, let d = n.date, let ch = n.change_1y, abs(ch) >= 0.5 {
            let s = (ch > 0 ? "+" : "") + String(format: "%.1f", ch)
            cand.append(.init(id: "an.rate.\(d.prefix(7))", cat: .analysis,
                              text: "미국 10년 금리가 1년 새 \(s)%p 움직였어요. 지난 자료로는 2년 뒤 이익에 \(ch > 0 ? "부담" : "도움") 쪽이 많았어요." + tail, go: .rateScen))
        }
        // 이미 뜬 것은 그대로 두고, 새 것은 이번 주에 아직 안 떴을 때 하나만
        var out = cand.filter { log.first[$0.id] != nil }
        if log.analysisWeek != wk, let c = cand.first(where: { log.first[$0.id] == nil }) {
            out.append(c); log.analysisWeek = wk
        }
        return out
    }

    /// 설정·후원·개발자 소식
    @MainActor private func news() -> [RemindItem] {
        var a: [RemindItem] = []
        let t = Support.shared.tier
        if let old = log.tier, t.rawValue > old {
            let adds = SupportTier.allCases.filter { $0.rawValue > old && $0.rawValue <= t.rawValue }.flatMap(\.adds)
            a.append(.init(id: "tier.\(t.rawValue)", cat: .news, text: "\(t.name) 후원 고마워요! " + adds.joined(separator: ", ") + "이 열렸어요.", go: .support))
        } else if let tid = log.first.keys.first(where: { $0 == "tier.\(t.rawValue)" }) {
            a.append(.init(id: tid, cat: .news, text: "\(t.name) 후원 고마워요! " + t.adds.joined(separator: ", ") + "이 열렸어요.", go: .support))
        }
        if log.tier != t.rawValue { log.tier = t.rawValue }
        if case .failed = Sync.shared.state {
            a.append(.init(id: "sync.fail", cat: .news, text: "기기 동기화가 멈췄어요. 한번 확인해 볼래요?", go: .sync, once: false))
        }
        // 기록을 시작하기 사흘 전부터의 개발자 소식만 (처음 깐 사람에게 지난 소식을 쏟지 않게)
        let from = Day.str(Date(timeIntervalSince1970: log.since - 3 * 86400))
        var ids = Set<String>()
        for n in RemindNews.app + site where n.date >= from && n.date <= Day.today && ids.insert(n.id).inserted {
            a.append(.init(id: "news.\(n.id)", cat: .news, text: n.text, go: RemindGo(n.go)))
        }
        return a
    }

    /// 사이트 소식 파일 (6시간에 한 번)
    @MainActor private func loadSite() async {
        guard Date().timeIntervalSince(siteAt) > 6 * 3600 else { return }
        siteAt = Date()
        var req = URLRequest(url: Config.data.appendingPathComponent("app_news.json"))
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 20
        guard let (d, r) = try? await URLSession.shared.data(for: req), (r as? HTTPURLResponse)?.statusCode == 200,
              let list = try? JSONDecoder().decode([RemindNews].self, from: d) else { return }
        Store.writeData(d, "app_news.json")
        site = list
    }

    /// 주 이름 (yyyy-Www, 월요일 시작)
    static func week(_ d: Date) -> String {
        let c = Calendar(identifier: .iso8601), y = c.component(.yearForWeekOfYear, from: d), w = c.component(.weekOfYear, from: d)
        return String(format: "%04d-W%02d", y, w)
    }
}

// MARK: 일정 (naeilo.com 외부 요인의 날짜가 정해진 사건과 같은 규칙)

/// 웹 app.js EXT_EVENTS 중 확률 100%이고 날짜가 정해진 것 + 보유 종목 실적. 날짜 반복은 web/model.js occurrences 와 같은 방식
enum MarketEvents {
    struct Rule { let id: String; let target: String; let kind: String; let date: String; let rep: String }
    static let rules: [Rule] = [
        .init(id: "x_fomc", target: "ALL", kind: "미국 금리 결정(FOMC)", date: "2026-10-28", rep: "6w"),
        .init(id: "x_cpi", target: "ALL", kind: "미국 물가(CPI) 발표", date: "2026-10-14", rep: "monthly"),
        .init(id: "x_jobs", target: "ALL", kind: "미국 고용 지표", date: "2026-11-06", rep: "monthly"),
        .init(id: "x_bok", target: "FX", kind: "한국은행 금리 결정", date: "2026-10-22", rep: "6w"),
        .init(id: "x_dlv", target: "TSLA", kind: "테슬라 차량 인도량 발표", date: "2027-01-04", rep: "quarterly"),
        .init(id: "x_gtc", target: "NVDA", kind: "엔비디아 GTC 신제품 발표", date: "2027-03-16", rep: "yearly"),
        .init(id: "x_opec", target: "ALL", kind: "OPEC+ 회의", date: "2026-11-30", rep: "semi"),
        .init(id: "x_mid", target: "ALL", kind: "미국 중간선거 결과", date: "2026-11-04", rep: "none"),
        .init(id: "x_witch", target: "ALL", kind: "선물·옵션 동시 만기", date: "2026-12-18", rep: "quarterly"),
        // 보유 종목 실적 (위젯 '다음' 줄이 쓰던 값)
        .init(id: "e_drnk", target: "DRNK", kind: "드링커 실적 발표", date: "2026-10-22", rep: "none"),
        .init(id: "e_005930", target: "005930", kind: "삼성전자 실적 발표", date: "2026-10-29", rep: "none"),
        .init(id: "e_aapl", target: "AAPL", kind: "애플 실적 발표", date: "2026-10-30", rep: "none"),
        .init(id: "e_nvda", target: "NVDA", kind: "엔비디아 실적 발표", date: "2026-11-19", rep: "none"),
    ]
    struct Hit { let id: String; let target: String; let day: String; let kind: String; let sub: String; let held: Bool }

    /// from 날짜부터 days 일 안의 날짜 (from 포함)
    static func dates(_ r: Rule, from: String, until: String) -> [String] {
        let step: Int? = ["monthly": 1, "quarterly": 3, "semi": 6, "yearly": 12][r.rep]
        var out: [String] = []
        guard let start = Day.date(r.date) else { return [] }
        for k in 0..<600 {
            let d: Date?
            if r.rep == "6w" { d = start.addingTimeInterval(Double(42 * k) * 86400) }
            else { d = addMonths(r.date, k * (step ?? 0)) }
            guard let dd = d else { break }
            let s = Day.str(dd)
            if s > until { break }
            if s >= from { out.append(s) }
            if r.rep == "none" || (r.rep != "6w" && step == nil) { break }
        }
        return out
    }
    /// 월 더하기: 말일을 넘으면 그 달 말일 (web addMonths 와 같다)
    private static func addMonths(_ s: String, _ n: Int) -> Date? {
        guard let d = Day.date(s) else { return nil }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Day.fmt.timeZone
        let day = cal.component(.day, from: d)
        guard let first = cal.date(from: cal.dateComponents([.year, .month], from: d)), let m = cal.date(byAdding: .month, value: n, to: first),
              let len = cal.range(of: .day, in: .month, for: m)?.count else { return nil }
        return cal.date(byAdding: .day, value: min(day, len) - 1, to: m)
    }

    /// 오늘부터 days 일 안의 일정. 내 종목(환율은 달러 종목) 관련이 먼저, 그다음 날짜순
    static func upcoming(held: [String], days: Int) -> [Hit] {
        let from = Day.today, until = Day.str(Date().addingTimeInterval(Double(days) * 86400))
        let usd = held.contains { Sample.symbol($0)?.currency == .usd }
        var out: [Hit] = []
        for r in rules {
            let mine = held.contains(r.target) || (r.target == "FX" && usd)
            guard r.target == "ALL" || r.target == "FX" || mine else { continue }
            let sub = r.target == "ALL" ? "전 종목" : r.target == "FX" ? "환율" : (Sample.symbol(r.target)?.short ?? r.target)
            for d in dates(r, from: from, until: until) { out.append(.init(id: r.id, target: r.target, day: d, kind: r.kind, sub: sub, held: held.contains(r.target))) }
        }
        return out.sorted { ($0.held ? 0 : 1, $0.day) < ($1.held ? 0 : 1, $1.day) }
    }

    /// "내일", "10/22 (D-11)" 처럼
    static func when(_ day: String) -> String {
        let n = Day.dday(day)
        return n == 0 ? "오늘" : n == 1 ? "내일" : "\(Day.md(day)) (D-\(n))"
    }
    /// 위젯 '다음' 줄: 30일 안의 첫 일정 (내 종목 먼저)
    static func widgetNext(held: [String]) -> String? {
        guard let h = upcoming(held: held, days: 30).first else { return nil }
        let n = Day.dday(h.day)
        return "\(h.kind) " + (n == 0 ? "오늘" : "D-\(n)") + " (\(Day.md(h.day)))"
    }
}

extension AppModel {
    /// 소식을 눌렀을 때 그 화면으로
    func go(_ g: RemindGo) {
        switch g {
        case .nowhere: return
        case .shelter: tab = .home; homePath = ["shelter"]
        case .char(let id): tab = .home; homePath = ["shelter", "char:\(id)"]
        case .me: tab = .home; homePath = ["me"]
        case .reader(let i): tab = .home; homePath = ["shelter", "read:\(i)"]
        case .board: tab = .board; boardPath = []
        case .calm: tab = .analysis; analysisPath = NavigationPath([AnalysisRoute.calm])
        case .myPath: tab = .analysis; analysisPath = NavigationPath([AnalysisRoute.myPath])
        case .forecast: tab = .analysis; analysisPath = NavigationPath([AnalysisRoute.forecast])
        case .rateScen: tab = .analysis; analysisPath = NavigationPath([AnalysisRoute.rateScen])
        case .external: tab = .analysis; analysisPath = NavigationPath([AnalysisRoute.external])
        case .hold(let s):
            tab = .hold
            holdPath = Sample.symbol(s).map { NavigationPath([$0]) } ?? NavigationPath()
        case .support: tab = .settings; settingsPath = [.support]
        case .widgets: tab = .settings; settingsPath = [.widgets]
        case .sync: tab = .settings; settingsPath = [.sync]
        case .alerts: tab = .settings; settingsPath = [.alerts]
        case .krSum: krSumAsk = true
        }
    }
}
