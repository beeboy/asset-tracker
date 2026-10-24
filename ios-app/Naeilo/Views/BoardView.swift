import SwiftUI

// 미션 판: 목표·회복 루트가 같은 화면 하나를 쓴다 (한쪽만 고쳐지는 일이 없게).
// 인터미션 카드, 체크인, 접힌 시작 미션, 도달 카드, 초록 다음 미션 카드, 100칸 카드(GoalCellsCard)
// 루트마다 다른 것은 값뿐: 목표 = 석 달 체크인(달), 회복 = 처음 4주 체크인(주)
struct BoardView: View {
    @Environment(AppModel.self) private var m
    @State private var openDone = false

    var body: some View {
        let steps = m.boardSteps, starts = steps.filter { !$0.inter }
        GeometryReader { geo in
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AppHeader(tab: String(localized: "미션")).padding(.horizontal, -16)
                SampleNote()
                // 맨 위는 인터미션 카드 (목표 카드·제목은 뺌)
                if let inter = steps.first(where: \.inter) { interCard(inter) }
                if !m.interDone && m.playOn { checkIn }
                startList(starts)
                reachedCard
                if let next = steps.first(where: { !$0.ok && $0.avail }), !next.inter {
                    nextCard(next, steps: starts)
                }
                // 100칸 카드는 화면 아래(탭 위)까지 늘어난다
                if m.playUnlocked { GoalCellsCard().frame(maxHeight: .infinity) } else { lockedPlay }
            }
            .screen()
            .frame(minHeight: geo.size.height, alignment: .top)
        }
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: MissionRoute.self) { MissionScreen(route: $0) }
    }

    /// 목표 도달 / 본전 도달
    @ViewBuilder private var reachedCard: some View {
        if m.isGoal, m.goalReached {
            Card(padding: 18) {
                GuideBubble(pose: .cheer, text: String(localized: "목표 \(String(describing: AppModel.wonK(m.gK)))에 닿았어요! 여기까지 온 길, 축하해요."))
                Text("지금 \(AppModel.wonK(m.trackValue / 1e4))에서 다음 목표 · 3년 확률 50% 근처").appFont(14).foregroundStyle(Theme.sub)
                PrimaryButton(title: String(localized: "다음 목표 정하기")) { m.suggestNextGoal(); m.boardPath.append(.g1) }
            }
        } else if !m.isGoal, m.cost > 0, m.total >= m.cost {
            Card(padding: 18) {
                GuideBubble(pose: .cheer, text: String(localized: "본전에 닿았어요! 여기까지 온 길, 축하해요."))
                PrimaryButton(title: String(localized: "목표 루트로")) { m.graduateToGoal() }
            }
        }
    }

    private var lockedPlay: some View {
        DashedCard {
            Label(m.isGoal ? String(localized: "목표 100칸") : String(localized: "본전 100칸"), systemImage: "lock").appFont(15, .bold)
            Text("시작 3단계 뒤 열림 · 칸 채우기·블록·파도").appFont(13)
        }
    }

    /// 다음 미션: 진한 초록 블록
    private func nextCard(_ next: AppModel.BoardStep, steps: [AppModel.BoardStep]) -> some View {
        let idx = (steps.firstIndex { $0.key == next.key } ?? 0) + 1
        return VStack(alignment: .leading, spacing: 10) {
            Text("다음 미션 · \(idx)/\(steps.count)").appFont(13, .semibold).foregroundStyle(.white.opacity(0.8))
            Text(next.title).appFont(20, .bold)
            Text("열리는 것: \(next.reward)").appFont(14).foregroundStyle(.white.opacity(0.85))
            Button { if let to = next.to { m.boardPath.append(to) } } label: {
                Text("시작").appFont(16, .bold).foregroundStyle(Theme.missionGreen)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14))
            }.buttonStyle(.plain).padding(.top, 4)
        }
        .foregroundStyle(.white)
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.missionGreen, in: RoundedRectangle(cornerRadius: 20))
    }

    /// 인터미션 카드: 목표 = 달마다 계획대로 넣었나(3달), 회복 = 주마다 비중을 지켰나(4주)
    private func interCard(_ b: AppModel.BoardStep) -> some View {
        let goal = m.isGoal, done = b.ok, avail = b.avail
        let n = goal ? m.gWeeks.count : m.weeks.count, total = goal ? 3 : 4
        let picks: [(String, Color)] = goal ? m.gWeeks.map { ($0.label, $0.color) } : m.weeks.map { ($0.label, $0.color) }
        let tap = done || avail ? b.to : nil
        return Button { if let tap { m.boardPath.append(tap) } } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(goal ? String(localized: "석 달 체크인") : String(localized: "처음 4주 체크인")).appFont(12, .bold).foregroundStyle(Theme.mint)
                    Spacer()
                    if done { Text("완료 ✓").appFont(12, .bold).foregroundStyle(Theme.yellow) }
                    else if !avail { Image(systemName: "lock").appFont(12) }
                }
                if !(done || n >= total) {
                    Text(!avail ? String(localized: "시작 미션 뒤 열림")
                         : goal ? String(localized: "월 1회 계획대로 체크 · \(String(describing: n))/3달")
                         : String(localized: "주 1회 비중 체크 · \(String(describing: n))/4주"))
                        .appFont(16, .bold).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                }
                HStack(spacing: 6) {
                    ForEach(0..<total, id: \.self) { i in
                        let pick = i < picks.count ? picks[i] : nil, cur = avail && !done && i == n
                        VStack(spacing: 2) {
                            Text(goal ? String(localized: "\(String(describing: i + 1))달") : String(localized: "\(i + 1)주째")).appFont(11)
                            Text(pick?.0 ?? (cur ? (goal ? String(localized: "이번 달") : String(localized: "이번 주")) : "–")).appFont(13, .bold)
                        }
                        .frame(maxWidth: .infinity, minHeight: 60)
                        .foregroundStyle(pick != nil ? .white : cur ? Theme.yellow : Theme.muted)
                        .background(pick?.1 ?? .clear, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(pick?.1 ?? (cur ? Theme.yellow : Theme.slate),
                                                                          style: StrokeStyle(lineWidth: 2, dash: pick == nil ? [5, 4] : [])))
                    }
                }
            }
            .foregroundStyle(done || avail ? .white : Theme.sub2)
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(done || avail ? Theme.night : Theme.track, in: RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain).disabled(tap == nil)
        .accessibilityLabel(goal ? String(localized: "처음 석 달 체크인, \(n)/3달") : String(localized: "처음 4주 체크인, \(n)/4주"))
    }

    /// 시작 미션: 접힌 한 줄, 펼치면 번호 목록. 끝냈거나 열린 미션은 눌러서 들어간다
    private func startList(_ list: [AppModel.BoardStep]) -> some View {
        let done = list.filter(\.ok).count, all = done == list.count
        return VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation { openDone.toggle() } } label: {
                HStack(spacing: 8) {
                    Text(all ? String(localized: "✓ 시작 미션 \(String(describing: done))/\(String(describing: list.count))") : String(localized: "시작 미션 \(String(describing: done))/\(String(describing: list.count))")).fontWeight(.bold)
                    Spacer(minLength: 0)
                    Image(systemName: openDone ? "chevron.up" : "chevron.down")
                }
                .appFont(14, .semibold).foregroundStyle(all ? Theme.tealDark : Theme.ink)
                .padding(.horizontal, 14).frame(minHeight: 48)
            }.buttonStyle(.plain)
            if openDone {
                ForEach(Array(list.enumerated()), id: \.element.key) { i, b in
                    let ok = b.ok, avail = b.avail
                    Divider().overlay(Theme.border)
                    Button { if let to = b.to, ok || avail { m.boardPath.append(to) } } label: {
                        HStack(spacing: 10) {
                            Text("\(i + 1)").appFont(13, .bold).foregroundStyle(Theme.sub).frame(width: 18)
                            Text(b.title).appFont(14).foregroundStyle(ok || avail ? Theme.ink : Theme.sub2)
                            Spacer(minLength: 0)
                            if ok { Image(systemName: "checkmark").foregroundStyle(Theme.teal) }
                            else if !avail { Image(systemName: "lock").foregroundStyle(Theme.sub2) }
                            else { Image(systemName: "chevron.right").foregroundStyle(Theme.sub) }
                        }
                        .appFont(13, .bold).padding(.horizontal, 14).frame(minHeight: 44)
                    }
                    .buttonStyle(.plain).disabled(b.to == nil || !(ok || avail))
                    .accessibilityLabel(String(localized: "시작 미션 \(String(describing: i + 1)): \(String(describing: b.title))") + (ok ? String(localized: ", 끝냄") : ""))
                }
            }
        }
        .background(all ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }

    // 인터미션 체크인 (색 없는 박스): 고르면 칸이 채워지고 위젯·친구가 하나 열린다.
    // 다음 달(주차)이 열리기 전에는 방금 한 것을 다시 고를 수 있다
    private var checkIn: some View {
        let goal = m.isGoal
        let edit = goal ? m.monthEditable : m.weekEditable
        let k = goal ? (edit ? m.gWeeks.count - 1 : m.gWeeks.count) : (edit ? m.weeks.count - 1 : m.weeks.count)
        let step = stepText(k)
        let opts = options(edit: edit, k: k)
        let wait = goal ? m.monthGateText : m.weekGateText
        let picked = goal ? m.gWeekCur != nil : m.weekCur != nil
        return VStack(alignment: .leading, spacing: 10) {
            Text(goal ? String(localized: "\(k + 1)달째 체크인") : String(localized: "\(k + 1)주차 체크인")).appFont(13, .bold).foregroundStyle(Theme.teal)
            HStack(spacing: 8) {
                Text(step.whereText).appFont(11, .bold).foregroundStyle(Theme.inkFixed)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                Text(step.task).appFont(14, .semibold).foregroundStyle(Theme.ink)
            }
            Text(goal ? String(localized: "이번 달 계획대로?") : String(localized: "이번 주 \(String(describing: m.focusName)) 비중은?")).appFont(14).foregroundStyle(Theme.ink)
            HStack(spacing: 6) {
                ForEach(opts.indices, id: \.self) { i in
                    let o = opts[i]
                    Button(action: o.pick) {
                        VStack(spacing: 2) {
                            Text(o.label).appFont(15, .bold)
                            Text(o.sub).appFont(11).foregroundStyle(Theme.sub)
                        }
                        .foregroundStyle(Theme.ink).padding(.vertical, 8)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background(o.on ? o.color.opacity(0.15) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(o.color, lineWidth: o.on ? 3 : 2))
                    }.buttonStyle(.plain)
                }
            }
            if edit { Text(goal ? String(localized: "다음 달 열리기 전까지 수정 가능") : String(localized: "다음 주차 열리기 전까지 수정 가능")).appFont(12).foregroundStyle(Theme.muted) }
            if let wait {
                Label(wait, systemImage: "clock").appFont(13).foregroundStyle(Theme.sub)
                    .fixedSize(horizontal: false, vertical: true)
            } else if picked {
                Button { withAnimation { if goal { m.confirmGoalMonth() } else { m.confirmWeek() } } } label: {
                    Text("체크인").appFont(14, .semibold)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(.white)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                }.buttonStyle(.plain)
            }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }
}

extension BoardView {
    struct Opt { let label: String; let sub: String; let color: Color; let on: Bool; let pick: () -> Void }

    /// 이번 체크인 할 일 (목표 = 달, 회복 = 주)
    func stepText(_ k: Int) -> (task: String, whereText: String) {
        if m.isGoal { let s = AppModel.goalMonthSteps[min(k, 2)]; return (s.task, s.whereText) }
        return Shelter.weekSteps[min(k, 3)]
    }

    /// 체크인 고르기 칸 (목표 = 계획대로·덜·더, 회복 = 지킴·바꿈·높임)
    func options(edit: Bool, k: Int) -> [Opt] {
        let m = m
        if m.isGoal {
            return GoalWeek.allCases.map { p in
                Opt(label: p.label, sub: String(localized: "월 ") + AppModel.wonK(m.gM * p.factor), color: p.color,
                    on: edit ? m.gWeeks[k] == p : m.gWeekCur == p, pick: { if edit { m.editLastMonth(p) } else { m.gWeekCur = p } })
            }
        }
        return WeekPick.allCases.map { p in
            Opt(label: p.label, sub: p.sub, color: p.color,
                on: edit ? m.weeks[k] == p : m.weekCur == p, pick: { if edit { m.editLastWeek(p) } else { m.weekCur = p } })
        }
    }
}

extension AppModel {
    /// 미션 판 한 칸 (두 루트 공통 모양)
    struct BoardStep { let key: String; let title: String; let reward: String; let to: MissionRoute?; let inter: Bool; let ok: Bool; let avail: Bool }

    var boardSteps: [BoardStep] {
        if isGoal {
            return goalSteps.map { BoardStep(key: $0.id, title: $0.title, reward: $0.reward, to: $0.to, inter: $0.inter, ok: gDone.contains($0.id), avail: goalAvailable($0)) }
        }
        let to: [Int: MissionRoute] = [1: .m1, 2: .m2, 3: .m3, 5: .nx]   // 회복 인터미션은 판 위 체크인에서 바로
        return blocks.map { BoardStep(key: String($0.id), title: $0.title, reward: $0.reward, to: to[$0.id], inter: $0.inter, ok: done.contains($0.id), avail: available($0)) }
    }
}

struct WeekForecastCard: View {
    @Environment(AppModel.self) private var m
    @State private var whyOpen = false

    static let names = ["lo": String(localized: "더 낮게"), "in": String(localized: "예보대로"), "hi": String(localized: "더 높게")]

    /// 범위 숫자: 억 단위 소수 한 자리 (lo·hi가 같게 반올림되면 두 자리), 1억 아래는 만원 단위
    static func eok(_ v: Double, _ d: Int) -> String {
        if AppModel.en { return AppModel.krwC(v) }
        return v < 1e8 ? AppModel.man((v / 1e5).rounded() * 1e5) : String(format: "%.\(d)f억", v / 1e8)
    }

    // 이번 주 예보: 그 주 처음 연 날에 금요일 평가액 범위를 적어 두고, 금요일 종가로 판정한다.
    // 예보 · 맞혀보기 · 예보 성적 세 카드로 나눈다 (예보는 앱의 것, 맞혀보기는 사용자의 것).
    var body: some View {
        let r = m.weekRange
        let span = r.hi - r.lo, x0 = r.lo - span * 0.6, x1 = r.hi + span * 0.6
        let px = { (v: Double) in max(0, min(1, (v - x0) / (x1 - x0))) }
        let inside = r.actual >= r.lo && r.actual <= r.hi
        let pos = r.actual < r.lo ? "lo" : r.actual > r.hi ? "hi" : "in"
        let d = Self.eok(r.lo, 1) == Self.eok(r.hi, 1) ? 2 : 1
        let lo = Self.eok(r.lo, d), hi = Self.eok(r.hi, d)
        // 개발자 넘기기는 기록에 없으니 이번 주 결과를 붙여 본다
        let stamps = m.weekFridaySkip ? Array((m.pastWeeks + [inside ? 1 : 0]).suffix(8)) : m.pastWeeks
        return VStack(spacing: 12) {
            forecastCard(r: r, px: px, inside: inside, lo: lo, hi: hi)
            guessCard(pos: pos, inside: inside, lo: lo, hi: hi)
            recordCard(stamps)
        }
        .onAppear { Remind.shared.recordWeek(m) }
    }

    // 1. 예보
    private func forecastCard(r: (lo: Double, hi: Double, actual: Double), px: @escaping (Double) -> Double, inside: Bool, lo: String, hi: String) -> some View {
            Card {
                Label("\(m.weekFridayText) 금 마감 예보", systemImage: "sun.max.fill").appFont(13, .bold).foregroundStyle(Theme.forecast)
                Text("\(lo) ~ \(hi)").appFont(28, .bold).lineLimit(1).minimumScaleFactor(0.7).roll("\(lo) ~ \(hi)")
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.track).frame(height: 12)
                        Capsule().fill(Theme.blue.opacity(0.55)).frame(width: (px(r.hi) - px(r.lo)) * g.size.width, height: 12).offset(x: px(r.lo) * g.size.width)
                        Circle().fill(m.weekFriday ? (inside ? Theme.green : Theme.orange) : Theme.ink)
                            .overlay(Circle().stroke(Theme.card, lineWidth: 3))
                            .frame(width: 18, height: 18).offset(x: px(r.actual) * g.size.width - 9)
                    }
                    .frame(maxHeight: .infinity)
                }
                .frame(height: 26)
                HStack {
                    Text(lo); Spacer()
                    Text((m.weekFriday ? String(localized: "금 ") : String(localized: "지금 ")) + Self.eok(r.actual, 2)).fontWeight(.bold); Spacer()
                    Text(hi)
                }
                .appFont(12).foregroundStyle(Theme.sub)
                (Text("2주 중 1주").fontWeight(.bold) + Text("는 이 범위 안"))
                    .appFont(13).foregroundStyle(Theme.ink).lineSpacing(2)
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.forecastBg, in: RoundedRectangle(cornerRadius: 12))
            }
            .accessibilityElement(children: .combine)
    }

    // 2. 맞혀보기
    private func guessCard(pos: String, inside: Bool, lo: String, hi: String) -> some View {
            Card {
                HStack {
                    Text("맞혀보기").appFont(12, .bold).foregroundStyle(Theme.brown)
                    Spacer()
                    Text("결과는 토요일").appFont(12).foregroundStyle(Theme.sub)
                }
                Text("금요일엔?").appFont(17, .bold)
                HStack(spacing: 8) {
                    ForEach([("lo", "\(lo) ↓"), ("in", String(localized: "범위 안")), ("hi", "\(hi) ↑")], id: \.0) { o in
                        let on = m.weekGuess == o.0
                        Button { m.weekGuess = o.0; Remind.shared.recordWeek(m) } label: {
                            VStack(spacing: 2) {
                                Text(Self.names[o.0] ?? "").appFont(15, .bold)
                                Text(o.1).appFont(11).opacity(0.85).lineLimit(1).minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity, minHeight: 64)
                            .foregroundStyle(on ? .white : Theme.ink)
                            .background(on ? Theme.forecast : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Theme.forecast : Theme.border, lineWidth: 2))
                        }
                        .buttonStyle(.plain)
                        .disabled(m.weekFriday)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
                if m.weekFriday {
                    let out = pos == "in" ? String(localized: "범위 안") : String(localized: "범위보다 \(String(describing: pos == "hi" ? String(localized: "높게") : String(localized: "낮게")))")
                    Text((m.weekGuess.map { $0 == pos ? String(localized: "맞혔어요! ") : String(localized: "아쉬워요. 고른 답 ‘\(String(describing: Self.names[$0] ?? ""))’ · ") } ?? "") + out)
                        .appFont(14, .semibold)
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background(inside ? Color(hex: 0xE3F4EC, dark: 0x163226) : Color(hex: 0xFDF0E1, dark: 0x3A2A17), in: RoundedRectangle(cornerRadius: 12))
                } else {
                    if let g = m.weekGuess { Text("‘\(Self.names[g] ?? "")’ 고름 · 금요일 마감 뒤 확인").appFont(13, .semibold).foregroundStyle(Theme.forecast) }
                    if m.devAll {
                        Button("금요일로 넘기기 (개발자)") { withAnimation { m.weekFridaySkip = true } }
                            .appFont(13).underline().foregroundStyle(Theme.sub).frame(minHeight: 40)
                    }
                }
            }
    }

    // 3. 예보 성적 + 배지
    private func recordCard(_ stamps: [Int]) -> some View {
            Card {
                HStack {
                    Text(stamps.isEmpty ? String(localized: "예보 성적") : String(localized: "지난 \(String(describing: stamps.count))주")).appFont(15, .bold)
                    Spacer()
                    if !stamps.isEmpty { Text("\(stamps.reduce(0, +))/\(stamps.count) 범위 안").appFont(13).foregroundStyle(Theme.sub) }
                }
                if stamps.isEmpty {
                    Text("첫 주 끝나면 쌓여요").appFont(13).foregroundStyle(Theme.sub)
                } else {
                    HStack(spacing: 6) {
                        ForEach(stamps.indices, id: \.self) { i in
                            VStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(stamps[i] == 1 ? Theme.forecast : Theme.card)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(stamps[i] == 1 ? Theme.forecast : Theme.dash, lineWidth: 2))
                                    .overlay { if stamps[i] == 1 { Image(systemName: "checkmark").appFont(13, .bold).foregroundStyle(.white) } }
                                    .frame(height: 32)
                                Text(i == stamps.count - 1 ? String(localized: "지난주") : String(localized: "\(String(describing: stamps.count - i))주 전")).appFont(10).foregroundStyle(Theme.muted)
                                    .lineLimit(1).minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(stamps.count)주 중 \(stamps.reduce(0, +))주 범위 안")
                    HStack(spacing: 14) {
                        legend(Theme.forecast, nil, String(localized: "범위 안"))
                        legend(Theme.card, Theme.dash, String(localized: "범위 밖"))
                    }
                }
                Divider()
                HStack {
                    Text("배지").appFont(14, .bold)
                    Spacer()
                    Text("\(Remind.badges.filter { m.guessHits >= $0.n }.count)개").appFont(13).foregroundStyle(Theme.sub)
                }
                HStack(spacing: 6) {
                    ForEach(Remind.badges, id: \.n) { b in
                        let got = m.guessHits >= b.n
                        VStack(spacing: 4) {
                            Image(systemName: b.icon).appFont(16, .bold)
                                .foregroundStyle(got ? Theme.inkFixed : Theme.muted)
                                .frame(width: 40, height: 40)
                                .background(got ? Theme.gold : Theme.track, in: Circle())
                            Text(b.n >= 20 ? String(localized: "달인") : String(localized: "\(String(describing: b.n))회")).appFont(10).foregroundStyle(got ? Theme.ink : Theme.muted).lineLimit(1).minimumScaleFactor(0.7)
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(b.name) 배지, \(got ? String(localized: "받음") : String(localized: "\(String(describing: b.n))번 맞히면 받아요"))")
                    }
                }
                Button { withAnimation { whyOpen.toggle() } } label: {
                    HStack {
                        Text("반만 맞아도 괜찮은 이유").appFont(13, .semibold)
                        Spacer()
                        Image(systemName: whyOpen ? "chevron.up" : "chevron.down").appFont(12)
                    }
                    .foregroundStyle(Theme.forecast).frame(minHeight: 40).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if whyOpen {
                    Text("범위는 2번 중 1번 맞게 잡아요.\n넓으면 쓸모없고, 좁으면 자주 틀려요.")
                        .appFont(13).foregroundStyle(Theme.ink).lineSpacing(2)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.forecastBg, in: RoundedRectangle(cornerRadius: 12))
                }
            }
    }

    private func legend(_ c: Color, _ stroke: Color?, _ t: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 4).fill(c)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(stroke ?? c, lineWidth: 2))
                .frame(width: 14, height: 14)
            Text(t).appFont(12).foregroundStyle(Theme.sub)
        }
    }
}
