import SwiftUI
import CoreMotion

// 목표 달성 루트 화면 (시안 g1·g1r·g3·g3r·gt·gi·gp1r, 목표 미션 판). 설명은 계산값, 종목 추천 문구 없음.

private let mixColor: [String: Color] = ["mine": Theme.purple, "index": Color(hex: 0x1F3A7A, dark: 0x7F9BE0), "grow": Theme.green, "steady": Theme.blue]
private let compColor: [String: Color] = ["T": Theme.purple, "I": Color(hex: 0x1F3A7A, dark: 0x7F9BE0), "G": Theme.green, "B": Theme.blue, "C": Color(hex: 0xBFC6CD, dark: 0x56616C)]
private let compName: [String: String] = ["T": String(localized: "비중 1위 종목"), "I": String(localized: "지수"), "G": String(localized: "성장 지속"), "B": String(localized: "버팀목"), "C": String(localized: "현금")]

// 목표까지 길: 띠 = 100번 중 90번, 선 = 보통의 경우, 회색 점선 = 넣은 원금, 빨간 점선 = 목표
struct GoalPathChart: View {
    let sims: [(GoalSim, Color, Bool)]     // (결과, 색, 고른 것)
    let goal: Double
    var showBand = true
    var showPrincipal = true
    var body: some View {
        Canvas { ctx, size in
            guard let main = sims.first(where: { $0.2 })?.0 ?? sims.first?.0 else { return }
            let n = main.med.count - 1, ymax = max(goal * 1.3, (sims.map { $0.0.med.last ?? 0 }.max() ?? 0) * 1.25)
            let X = { (i: Int) in CGFloat(i) / CGFloat(max(1, n)) * size.width }
            let Y = { (v: Double) in size.height - CGFloat(min(v, ymax) / ymax) * size.height }
            func line(_ a: [Double]) -> Path { Path { p in for (i, v) in a.enumerated() { let pt = CGPoint(x: X(i), y: Y(v)); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } } }
            if showBand {
                var band = line(main.hi)
                for i in stride(from: n, through: 0, by: -1) { band.addLine(to: CGPoint(x: X(i), y: Y(main.lo[i]))) }
                band.closeSubpath()
                ctx.fill(band, with: .color(Theme.teal.opacity(0.15)))
            }
            ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: Y(goal))); $0.addLine(to: CGPoint(x: size.width, y: Y(goal))) },
                       with: .color(Color(hex: 0xC8352E)), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            if showPrincipal { ctx.stroke(line(main.principal), with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3])) }
            for (s, c, on) in sims { ctx.stroke(line(s.med), with: .color(c.opacity(on ? 1 : 0.55)), lineWidth: on ? 3 : 1.5) }
        }
        .accessibilityLabel("목표까지 평가액이 지날 길")
    }
}

private func legend(_ c: Color, _ t: String) -> some View {
    HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12); Text(t) }
        .appFont(12).foregroundStyle(Theme.sub)
}

private struct Page<Content: View>: View {
    let kicker: String
    let title: String
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(kicker: kicker, title: title)
                content
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct BoardLinks: View {
    @Environment(AppModel.self) private var m
    let next: String?
    let to: MissionRoute?
    var body: some View {
        VStack(spacing: 8) {
            if let next, let to { PrimaryButton(title: next) { m.boardPath.append(to) } }
            Button("미션 판으로") { m.boardPath = [] }
                .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 44)
        }
    }
}

private func inputField(_ label: String, _ text: Binding<String>) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(label).appFont(13, .semibold).foregroundStyle(Theme.sub)
        TextField("", text: text).keyboardType(.numberPad).appFont(18, .semibold)
            .padding(.horizontal, 12).frame(minHeight: 48)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
            .accessibilityLabel(label)
    }
}

/// 금액 끝의 "원"을 뗀다 (826만원 → 826만, 5.1억원 → 5.1억)
private func noWon(_ s: String) -> String { s.hasSuffix("원") ? String(s.dropLast()) : s }

private extension AppModel {
    /// 목표일 짧게 (2029.10.5)
    var goalToShort: String { let f = DateFormatter(); f.dateFormat = "yyyy.M.d"; return f.string(from: goalToDate) }
}

// MARK: 목표 미션 판

struct GoalBoardView: View {
    @Environment(AppModel.self) private var m
    @State private var openDone = false

    var body: some View {
        let steps = m.goalSteps, done = steps.filter { m.gDone.contains($0.id) }
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AppHeader(tab: String(localized: "미션")).padding(.horizontal, -16)
                SampleNote()
                // 맨 위는 인터미션 카드 (목표 카드·제목은 뺌)
                if let inter = steps.first(where: \.inter) { interCard(inter) }
                if !m.interDone && m.playOn { monthCheckIn }
                startList(steps.filter { !$0.inter }, done: done.filter { !$0.inter }.count)
                if m.goalReached {
                    Card(padding: 18) {
                        GuideBubble(pose: .cheer, text: String(localized: "목표 \(String(describing: AppModel.wonK(m.gK)))에 닿았어요! 여기까지 온 길, 축하해요."))
                        Text("지금 \(AppModel.wonK(m.trackValue / 1e4))에서 다음 목표 · 3년 확률 50% 근처").appFont(14).foregroundStyle(Theme.sub)
                        PrimaryButton(title: String(localized: "다음 목표 정하기")) { m.suggestNextGoal(); m.boardPath.append(.g1) }
                    }
                }
                if let next = steps.first(where: { !m.gDone.contains($0.id) && m.goalAvailable($0) }), !next.inter {
                    nextCard(next, steps: steps.filter { !$0.inter })
                }
                if m.playUnlocked { GoalCellsCard() } else { lockedPlay }
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: MissionRoute.self) { MissionScreen(route: $0) }
    }

    // 인터미션 체크인 (색 없는 박스): 첫 달은 바로, 지난 달은 다음 달이 열리기 전까지 다시 고를 수 있다
    private var monthCheckIn: some View {
        let edit = m.monthEditable, k = edit ? m.gWeeks.count - 1 : m.gWeeks.count, step = AppModel.goalMonthSteps[min(k, 2)]
        return VStack(alignment: .leading, spacing: 10) {
            Text("\(k + 1)달째 체크인").appFont(13, .bold).foregroundStyle(Theme.teal)
            HStack(spacing: 8) {
                Text(step.whereText).appFont(11, .bold).foregroundStyle(Theme.inkFixed)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                Text(step.task).appFont(14, .semibold).foregroundStyle(Theme.ink)
            }
            Text("이번 달 계획대로?").appFont(14).foregroundStyle(Theme.ink)
            HStack(spacing: 6) {
                ForEach(GoalWeek.allCases, id: \.self) { p in
                    let on = edit ? m.gWeeks[k] == p : m.gWeekCur == p
                    Button { if edit { m.editLastMonth(p) } else { m.gWeekCur = p } } label: {
                        VStack(spacing: 2) {
                            Text(p.label).appFont(15, .bold)
                            Text(String(localized: "월 ") + AppModel.wonK(m.gM * p.factor)).appFont(11).foregroundStyle(Theme.sub)
                        }
                        .foregroundStyle(Theme.ink).padding(.vertical, 8)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background(on ? p.color.opacity(0.15) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.color, lineWidth: on ? 3 : 2))
                    }.buttonStyle(.plain)
                }
            }
            if edit { Text("다음 달 열리기 전까지 수정 가능").appFont(12).foregroundStyle(Theme.muted) }
            if let wait = m.monthGateText {
                Label(wait, systemImage: "clock").appFont(13).foregroundStyle(Theme.sub)
                    .fixedSize(horizontal: false, vertical: true)
            } else if m.gWeekCur != nil {
                Button { withAnimation { m.confirmGoalMonth() } } label: {
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

    private var lockedPlay: some View {
        DashedCard {
            Label("목표 100칸", systemImage: "lock").appFont(15, .bold)
            Text("시작 3단계 뒤 열림 · 칸 채우기·블록·파도").appFont(13)
        }
    }

    /// 다음 미션: 진한 초록 블록
    private func nextCard(_ next: AppModel.GoalStep, steps: [AppModel.GoalStep]) -> some View {
        let idx = (steps.firstIndex { $0.id == next.id } ?? 0) + 1
        return VStack(alignment: .leading, spacing: 10) {
            Text("다음 미션 · \(idx)/\(steps.count)").appFont(13, .semibold).foregroundStyle(.white.opacity(0.8))
            Text(next.title).appFont(20, .bold)
            Text("열리는 것: \(next.reward)").appFont(14).foregroundStyle(.white.opacity(0.85))
            Button { m.boardPath.append(next.to) } label: {
                Text("시작").appFont(16, .bold).foregroundStyle(Theme.missionGreen)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14))
            }.buttonStyle(.plain).padding(.top, 4)
        }
        .foregroundStyle(.white)
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.missionGreen, in: RoundedRectangle(cornerRadius: 20))
    }

    /// 처음 석 달 체크인 카드: 달마다 계획대로 넣었는지와 받은 위젯
    private func interCard(_ b: AppModel.GoalStep) -> some View {
        let done = m.gDone.contains(b.id), avail = m.goalAvailable(b), n = m.gWeeks.count
        return Button { if done || avail { m.boardPath.append(b.to) } } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("석 달 체크인").appFont(12, .bold).foregroundStyle(Theme.mint)
                    Spacer()
                    if done { Text("완료 ✓").appFont(12, .bold).foregroundStyle(Theme.yellow) }
                    else if !avail { Image(systemName: "lock").appFont(12) }
                }
                if !(done || n >= 3) {
                    Text(avail ? String(localized: "월 1회 계획대로 체크 · \(String(describing: n))/3달") : String(localized: "시작 미션 뒤 열림"))
                    .appFont(16, .bold).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                }
                HStack(spacing: 6) {
                    ForEach(0..<3, id: \.self) { i in
                        let pick: GoalWeek? = i < n ? m.gWeeks[i] : nil
                        VStack(spacing: 2) {
                            Text("\(i + 1)달").appFont(11)
                            Text(pick?.label ?? (avail && i == n ? String(localized: "이번 달") : "–")).appFont(13, .bold)
                        }
                        .frame(maxWidth: .infinity, minHeight: 60)
                        .foregroundStyle(pick != nil ? .white : avail && i == n ? Theme.yellow : Theme.muted)
                        .background(pick?.color ?? .clear, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(pick?.color ?? (avail && i == n ? Theme.yellow : Theme.slate),
                                                                          style: StrokeStyle(lineWidth: 2, dash: pick == nil ? [5, 4] : [])))
                    }
                }
            }
            .foregroundStyle(done || avail ? .white : Theme.sub2)
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(done || avail ? Theme.night : Theme.track, in: RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain).disabled(!(done || avail))
        .accessibilityLabel("처음 석 달 체크인, \(n)/3달")
    }

    /// 시작 미션: 접힌 한 줄, 펼치면 번호 목록. 끝냈거나 열린 미션은 눌러서 들어간다
    private func startList(_ list: [AppModel.GoalStep], done: Int) -> some View {
        let all = done == list.count
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
                ForEach(Array(list.enumerated()), id: \.element.id) { i, b in
                    let ok = m.gDone.contains(b.id), avail = m.goalAvailable(b)
                    Divider().overlay(Theme.border)
                    Button { if ok || avail { m.boardPath.append(b.to) } } label: {
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
                    .buttonStyle(.plain).disabled(!(ok || avail))
                    .accessibilityLabel(String(localized: "시작 미션 \(String(describing: i + 1)): \(String(describing: b.title))") + (ok ? String(localized: ", 끝냄") : ""))
                }
            }
        }
        .background(all ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }
}

// 목표까지 100칸 (1칸 = 목표의 1/100): 진한 칸 = 내가 넣은 돈, 민트 = 투자로 불어난 돈, 주황 = 잠시 줄어든 돈
// 공유 카드·벽돌깨기는 1000칸 그대로 (값만 넘긴다)
struct GoalCellsCard: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let K = m.gK * 1e4, unit = K / 100
        let tkV = m.trackValue
        let tkP = m.route == .novice ? tkV / 1.02 : tkV * max(0.8, min(1.25, m.cost / max(1, m.total)))
        let fV = min(100, tkV / max(1, unit)), fP = min(100, tkP / max(1, unit))
        let mine = min(fV, fP), lost = max(0, fP - fV), gain = max(0, tkV - tkP)
        // 공유 카드·벽돌깨기용 1000칸
        let cl = { (v: Double) in max(0, min(1000, Int(floor(v / max(1, K / 1000))))) }
        let nV = cl(tkV), nP = cl(tkP), nMine = min(nV, nP), nY = cl(tkV / (1 + m.todayMove))
        let pct = Int(tkV / max(1, K) * 100), stone = [75, 50, 25].first { pct >= $0 }
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline) {
                    Text("\(AppModel.wonK(m.gK))까지 100칸").appFont(18, .bold)
                    Spacer()
                    Text(fV >= 100 ? String(localized: "100칸 완성!") : String(localized: "1칸 = \(String(describing: AppModel.man(unit)))")).appFont(14).foregroundStyle(Theme.sub)
                }
                // 박스는 60% 폭, 정보는 오른쪽
                SplitRow(ratio: 0.6, spacing: 14) {
                    // 100칸이 곧 벽돌. 아래 빈 곳을 누르면 공 발사, 끌면 패들
                    BrickBox(mine: mine, filled: fV, lost: lost, g: game, on: $playing) { clear in
                        if clear { party = true } else { showGame = true }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { if !playing && !party { startMini(fV, mine) } }   // 박스·패들을 눌러도 시작
                    .accessibilityLabel("100칸 중 \(Int(fV))칸, 목표의 \(pct)%")
                    VStack(alignment: .leading, spacing: 10) {
                        legend(Theme.teal, nil, String(localized: "넣은 돈"), AppModel.man(min(tkV, tkP)))
                        if lost > 0 { legend(Self.lostC, nil, String(localized: "줄어든 돈"), "−" + AppModel.man(tkP - tkV)) }
                        else { legend(Self.mintC, nil, String(localized: "불어난 돈"), "+" + AppModel.man(gain)) }
                        legend(Theme.card, Theme.gold, String(localized: "마지막 칸 = 목표"), nil)
                        action(String(localized: "공유하기"), "square.and.arrow.up", fill: false) { showShare = true }
                        HStack(spacing: 6) {
                            action(String(localized: "블록"), "square.grid.3x3.fill", fill: true) { showGame = true }.labelStyle(TightLabel())
                            action(String(localized: "파도"), "water.waves", fill: true) { showWave = true }.labelStyle(TightLabel())
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                // 패들 조작 칸: 손가락이 패들을 가리지 않게 박스 아래 빈 곳에서 끈다
                GeometryReader { pg in
                    // 평소엔 톡 눌러 시작(위아래 스크롤은 그대로), 게임 중에만 끌어서 패들
                    Color.clear.contentShape(Rectangle())
                        .onTapGesture { p in
                            game.px = max(0, min(1, p.x / max(1, pg.size.width)))
                            if !playing && !party { startMini(fV, mine) }
                        }
                        .gesture(playing ? DragGesture(minimumDistance: 0).onChanged { v in
                            game.px = max(0, min(1, v.location.x / max(1, pg.size.width)))
                        } : nil)
                }
                .frame(height: 44)
                // 캐릭터가 자랑하자고 권한다 (누르는 곳 아님, 공유는 오른쪽 버튼). 다 깨면 친구들이 이 자리를 채운다
                if party { MiniParty().frame(maxWidth: .infinity) } else {
                HStack(alignment: .bottom, spacing: 10) {
                        CharPixel(id: m.homeFriendShown, width: 42, height: 60).charIdle().accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(m.friendName(m.homeFriendShown)).appFont(12, .semibold).foregroundStyle(Theme.blue)
                            Text(Self.boast(pct, Int(fV))).appFont(14).fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.mintBg, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                    }
                }
            }
            .padding(18)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 20))
        }
        .onChange(of: scenePhase) { if scenePhase == .background { party = false } }   // 다른 앱으로 가면 친구들 퇴장
        .sheet(isPresented: $showShare) { ShareCardSheet(party: party) }
        .fullScreenCover(isPresented: $showGame) { BrickGameHub(filled: nV, floor: nMine, yesterday: nY) }
        .fullScreenCover(isPresented: $showWave) { WaveHub() }
    }
    @State private var showGame = false
    @State private var showWave = false
    @State private var showShare = false
    @State private var game = MiniBreakout()
    @State private var playing = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var party = false   // 안 죽고 다 깨면 친구들이 올라와 축하 (숨은 재미)

    private func startMini(_ fV: Double, _ mine: Double) {
        let n = Int(ceil(fV))
        if n == 0 { showGame = true; return }
        game.setup(n: n, mine: mine)
        playing = true
    }

    /// 진행만큼 자랑하자는 한마디
    static func boast(_ pct: Int, _ n: Int) -> String {
        switch pct {
        case 100...: return String(localized: "100칸 완성! 이건 무조건 자랑이야.")
        case 75...: return String(localized: "거의 다 왔어. 지금 자랑할 때야!")
        case 50...: return String(localized: "반 넘었어! 보여 주면 다들 놀랄걸.")
        case 25...: return String(localized: "\(String(describing: pct))%나 왔어. 이건 자랑해야지.")
        default: return n > 0 ? String(localized: "벌써 \(String(describing: n))칸 채웠어. 시작한 것도 자랑이야!") : String(localized: "첫 칸 채우면 바로 자랑하자!")
        }
    }

    private func action(_ t: String, _ icon: String, fill: Bool, _ go: @escaping () -> Void) -> some View {
        Button(action: go) {
            Label(t, systemImage: icon).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, minHeight: 40)
                .foregroundStyle(fill ? .white : Theme.teal)
                .background(fill ? Theme.teal : .clear, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.teal, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }

    static let mintC = Color(hex: 0x8FCFC3, dark: 0xA8E6DC)   // 다크: 넣은 돈(진한 청록)과 구분되게 밝게
    static let lostC = Color(hex: 0xFBE3CF, dark: 0x4A3020)

    // 좁은 오른쪽 칸: 색 + 이름, 값은 아래 줄
    private func legend(_ c: Color, _ stroke: Color?, _ t: String, _ v: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 3).fill(c)
                    .overlay(RoundedRectangle(cornerRadius: 3).stroke(stroke ?? c, lineWidth: 1.5))
                    .frame(width: 12, height: 12)
                Text(t).appFont(13).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
            }
            if let v { Text(v).appFont(15, .bold).lineLimit(1).minimumScaleFactor(0.7).padding(.leading, 18) }
        }
    }
}

/// 100칸 박스가 곧 미니 벽돌깨기. 패들은 카드 아래 빈 곳에서 조작. 끝나면 end(다 깼나)
/// 게임 중에만 화면을 매 프레임 그린다
struct BrickBox: View {
    let mine: Double, filled: Double, lost: Double
    let g: MiniBreakout
    @Binding var on: Bool
    let end: (Bool) -> Void
    var body: some View {
        ZStack(alignment: .top) {
            if !on { GoalCellGrid(mine: mine, filled: filled, lost: lost).aspectRatio(1, contentMode: .fit) }
            TimelineView(.animation(paused: !on)) { tl in
                Canvas { ctx, size in
                    if on {
                        let r = g.step(tl.date, size)
                        if r != 0 { DispatchQueue.main.async { on = false; end(r == 1) } }
                    }
                    g.draw(&ctx, size, cells: on)
                }
            }
        }
        .aspectRatio(0.9, contentMode: .fit)
        .pausesRefresh(on)
    }
}

/// 다 깨면 캐릭터와 친구들이 아래에서 차례로 튀어 올라 축하
struct MiniParty: View {
    @State private var up = false
    var body: some View {
        let ids = Shelter.friends.map(\.id) + ["me"]   // 친구 다섯 + 나, 전부 여섯
        // 원래 캐릭터 줄 높이(60)를 넘지 않게: 축하 글은 위 빈 칸에 띄운다 (글자 키워도 탭과 안 겹치게)
        HStack(alignment: .bottom, spacing: 4) {
            ForEach(Array(ids.enumerated()), id: \.offset) { i, id in
                CharPixel(id: id, width: 42, height: 60).charIdle(i)
                    .offset(y: up ? 0 : 160)
                    .animation(.spring(response: 0.45, dampingFraction: 0.55).delay(Double(i) * 0.08), value: up)
            }
        }
        .frame(height: 60)
        .overlay(alignment: .top) {
            Text("다 깼다! 대단해!").appFont(14, .bold).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Theme.teal, in: Capsule())
                .fixedSize(horizontal: false, vertical: true)
                .alignmentGuide(.top) { $0[.bottom] + 6 }
                .opacity(up ? 1 : 0)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { withAnimation(.easeOut(duration: 0.3)) { up = true } }
    }
}

final class MiniBreakout {
    var alive = [Bool](repeating: false, count: 100)
    var bomb = [Bool](repeating: false, count: 100)   // 맞으면 둘레 8칸까지 함께 터진다 (찬 칸의 약 12%)
    var mineN = 0, left = 0
    var bx: CGFloat = 0, by: CGFloat = 0, vx: CGFloat = 0, vy: CGFloat = 0
    var px: CGFloat = 0.5         // 패들 가운데 (폭 비율)
    var held = true
    var last: Date?
    private let motion = CMMotionManager()   // 기울여서 패들 (본 게임과 같은 감도)

    func setup(n: Int, mine: Double) {
        for i in 0..<100 { alive[i] = i < n; bomb[i] = i < n && Double.random(in: 0..<1) < 0.12 }
        left = n; mineN = Int(mine); held = true; last = nil
        if motion.isDeviceMotionAvailable { motion.deviceMotionUpdateInterval = 1.0 / 60; motion.startDeviceMotionUpdates() }
    }
    deinit { motion.stopDeviceMotionUpdates() }

    /// 한 프레임 진행. 0 계속, 1 다 깸, 2 놓침
    func step(_ now: Date, _ size: CGSize) -> Int {
        let w = size.width, gap: CGFloat = 4, c = (w - gap * 9) / 10 + gap
        let py = size.height - 6, pw = w * 0.28
        let dt = CGFloat(min(1.0 / 30, last.map { now.timeIntervalSince($0) } ?? 0)); last = now
        // 좌우로 약 24도 기울이면 끝까지. 센서가 없으면 손가락으로
        if let g = motion.deviceMotion?.gravity {
            let goal = 0.5 + max(-1, min(1, CGFloat(g.x) / 0.4)) * 0.5
            px += (goal - px) * min(1, dt * 14)
        }
        let x = max(pw / 2, min(w - pw / 2, px * w))
        if held {
            held = false; bx = x; by = py - 7
            vx = w * 0.38 * (Bool.random() ? 1 : -1); vy = -w * 0.92
            return 0
        }
        var nx = bx + vx * dt, ny = by + vy * dt
        if nx < 4 || nx > w - 4 { vx = -vx; nx = max(4, min(w - 4, nx)) }
        if ny < 4 { vy = abs(vy); ny = 4 }
        let col = Int(nx / c), row = Int(ny / c)
        if ny < w, (0..<10).contains(col), (0..<10).contains(row), alive[row * 10 + col] {
            let hit = row * 10 + col
            alive[hit] = false; left -= 1
            if bomb[hit] {
                for r in max(0, row - 1)...min(9, row + 1) { for c2 in max(0, col - 1)...min(9, col + 1) where alive[r * 10 + c2] { alive[r * 10 + c2] = false; left -= 1 } }
            }
            if Int(bx / c) != col { vx = -vx } else { vy = -vy }
            if left == 0 { motion.stopDeviceMotionUpdates(); return 1 }
            return 0
        }
        if vy > 0, by < py - 4, ny >= py - 4, abs(nx - x) < pw / 2 + 4 {
            let sp = hypot(vx, vy), o = max(-1, min(1, (nx - x) / (pw / 2)))
            vx = sp * 0.75 * o; vy = -sqrt(sp * sp - vx * vx); ny = py - 4
        }
        if ny > size.height + 8 { motion.stopDeviceMotionUpdates(); return 2 }
        bx = nx; by = ny
        return 0
    }

    func draw(_ ctx: inout GraphicsContext, _ size: CGSize, cells: Bool) {
        let w = size.width, gap: CGFloat = 4, s = (w - gap * 9) / 10, pw = w * 0.28
        if cells {
            for i in 0..<100 {
                let r = CGRect(x: CGFloat(i % 10) * (s + gap), y: CGFloat(i / 10) * (s + gap), width: s, height: s)
                let path = Path(roundedRect: r, cornerRadius: 5)
                ctx.fill(path, with: .color(alive[i] ? (i < mineN ? Theme.teal : GoalCellsCard.mintC) : Theme.track))
                if alive[i] && bomb[i] { ctx.fill(Path(ellipseIn: r.insetBy(dx: s * 0.32, dy: s * 0.32)), with: .color(Theme.gold)) }
                if i == 99 && !alive[i] { ctx.stroke(Path(roundedRect: r.insetBy(dx: 1, dy: 1), cornerRadius: 4), with: .color(Theme.gold), lineWidth: 2) }
            }
        }
        let x = max(pw / 2, min(w - pw / 2, px * w)), py = size.height - 6
        ctx.fill(Path(roundedRect: CGRect(x: x - pw / 2, y: py - 2.5, width: pw, height: 5), cornerRadius: 2.5), with: .color(Theme.teal))
        let (ballX, ballY) = cells && !held ? (bx, by) : (x, py - 7)
        ctx.fill(Path(ellipseIn: CGRect(x: ballX - 4, y: ballY - 4, width: 8, height: 8)), with: .color(Theme.gold))
    }
}

/// 왼쪽 칸은 폭의 ratio만큼 정사각형, 오른쪽은 남은 폭에 세로 가운데
struct SplitRow: Layout {
    let ratio: CGFloat, spacing: CGFloat
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? 320, side = w * ratio
        let lh = subviews.first?.sizeThatFits(.init(width: side, height: nil)).height ?? 0
        let h = subviews.count > 1 ? subviews[1].sizeThatFits(.init(width: w - side - spacing, height: nil)).height : 0
        return CGSize(width: w, height: max(lh, h))
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let side = b.width * ratio, rw = b.width - side - spacing
        subviews.first?.place(at: CGPoint(x: b.minX, y: b.midY), anchor: .leading, proposal: .init(width: side, height: nil))
        if subviews.count > 1 { subviews[1].place(at: CGPoint(x: b.minX + side + spacing, y: b.midY), anchor: .leading, proposal: .init(width: rw, height: nil)) }
    }
}

/// 10×10 칸, 왼쪽 위부터 줄마다 채운다. 경계 칸은 비율만큼 가로로 나눠 칠한다. 마지막 칸(오른쪽 아래)은 금색 테두리 = 목표
struct GoalCellGrid: View {
    let mine: Double, filled: Double, lost: Double
    var body: some View {
        Canvas { ctx, size in
            let gap: CGFloat = 4, s = (size.width - gap * 9) / 10
            for i in 0..<100 {
                let r = CGRect(x: CGFloat(i % 10) * (s + gap), y: CGFloat(i / 10) * (s + gap), width: s, height: s)
                let path = Path(roundedRect: r, cornerRadius: 5)
                ctx.fill(path, with: .color(Theme.track))
                if i == 99 && filled < 100 {
                    ctx.fill(path, with: .color(Theme.card))
                    ctx.stroke(Path(roundedRect: r.insetBy(dx: 1, dy: 1), cornerRadius: 4), with: .color(Theme.gold), lineWidth: 2)
                    continue
                }
                // 이 칸 [i, i+1] 안에서 각 색이 차지하는 비율
                let lo = Double(i)
                let seg: [(Double, Double, Color)] = [(0, mine, Theme.teal), (mine, filled, GoalCellsCard.mintC), (filled, filled + lost, GoalCellsCard.lostC)]
                ctx.drawLayer { l in
                    l.clip(to: path)
                    for (a, b, c) in seg {
                        let x0 = max(0, min(1, a - lo)), x1 = max(0, min(1, b - lo))
                        if x1 > x0 { l.fill(Path(CGRect(x: r.minX + s * x0, y: r.minY, width: s * (x1 - x0), height: s)), with: .color(c)) }
                    }
                }
            }
        }
    }
}

// MARK: 플러스 미션 1 결과 — 지금 평가액

struct GoalHoldResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        Page(kicker: String(localized: "미션 1 완료 · 평가액·수익률 열림"), title: String(localized: "지금 내 평가액")) {
            if m.ret >= 0 { GuideBubble(pose: .spread, text: String(localized: "지금 평가액이에요. 들어간 돈보다 \(String(describing: AppModel.sgn(m.ret))) 위에 있어요. 여기서 출발해 목표 금액까지 가는 길을 그려요.")) }
            VStack(alignment: .leading, spacing: 8) {
                HStack { Spacer(); Text("원금 \(noWon(AppModel.man(m.cost)))") }.appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(noWon(AppModel.man(m.total))).appFont(30, .bold).roll(noWon(AppModel.man(m.total)))
                    Text(AppModel.sgn(m.ret)).appFont(16, .bold).foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
                }
                Text("\(m.focusName) 비중 \(AppModel.pct(m.focusWeight))").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            if m.ret >= 0 {
                BoardLinks(next: String(localized: "다음: 목표 정하기"), to: .g1)
            } else {
                Text("지금 마이너스 · 회복 루트가 더 맞아요 · 종목은 그대로")
                    .appFont(15).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0xFDF0E1, dark: 0x3A2A17), in: RoundedRectangle(cornerRadius: 12))
                PrimaryButton(title: String(localized: "회복 루트로")) { m.switchRoute(.recover) }
                BoardLinks(next: nil, to: nil)
                Button("목표 루트로 계속") { m.boardPath.append(.g1) }
                    .appFont(15, .semibold).foregroundStyle(Theme.sub).frame(maxWidth: .infinity, minHeight: 44)
            }
        }
    }
}

// MARK: 목표 정하기 — 경로 그래프 고정 + 배수 카드

struct GoalSetView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    /// 설정에서 열었을 때: 저장하면 돌아가고, 미션 진행은 건드리지 않는다
    var fromSettings = false
    @FocusState private var focus: Bool
    @State private var k = ""
    @State private var y = ""
    @State private var a = ""
    @State private var mo = ""

    var body: some View {
        // 플러스: 내가 매달 넣을 돈(gM)으로 닿을 확률. 시작 전: 보통의 경우로 맞춘 월 적립으로
        let plus = m.route != .novice
        let need = m.neededMonthly(), path = m.simulate(plus ? m.mineMix : AppModel.mixes[0], monthly: plus ? m.gM : need)
        let start = m.goalStart, cagr = start > 0 ? pow(m.gK / start, 1 / Double(m.gY)) - 1 : 0
        let ok = m.gK > 0 && m.gY >= 1
        PinnedLayout {
            VStack(alignment: .leading, spacing: 8) {
                Text(fromSettings || m.gDone.contains("goal") ? String(localized: "목표 고치기") : String(localized: "미션 \(String(describing: m.goalNo("goal"))) / \(String(describing: m.goalTotal))")).appFont(14, .semibold).foregroundStyle(Theme.teal)
                Text("언제까지, 얼마나?").appFont(22, .bold)
                HStack(alignment: .firstTextBaseline) {
                    Text("\(m.gY)년 뒤 \(noWon(AppModel.wonK(m.gK)))").appFont(17, .bold)
                    Spacer()
                    (Text("닿을 확률 ") + Text(AppModel.pct(path.prob)).bold().foregroundColor(Theme.teal)).appFont(14)
                }
                if plus {
                    Text(String(localized: "월 \(String(describing: noWon(AppModel.wonK(m.gM)))) · ") + (need > 0 ? String(localized: "맞추려면 월 \(String(describing: noWon(AppModel.wonK(need))))") : String(localized: "이미 충분"))).appFont(14, .semibold)
                } else {
                    Text((need > 0 ? String(localized: "월 ") + noWon(AppModel.wonK(need)) : String(localized: "이미 충분")) + String(localized: " · 1년 \(String(describing: noWon(AppModel.wonK(need * 12))))")).appFont(14, .semibold)
                }
                Text(m.route == .novice ? (m.gK <= 1000 ? String(localized: "첫 목표 · 적립 습관") : String(localized: "연 7%면 해마다 +\(String(describing: noWon(AppModel.wonK(m.gK * 0.07))))"))
                     : String(localized: "지금의 \(String(describing: String(format: "%.1f", m.gK / max(1, start))))배 · 적립 없이 연 +\(String(describing: Int((cagr * 100).rounded())))%"))
                    .appFont(13).foregroundStyle(Theme.sub)
                GoalPathChart(sims: [(path, Theme.teal, true)], goal: m.gK).frame(height: 80)
                HStack { Text("지금"); Spacer(); Text("┅ 목표 · ┈ 원금"); Spacer(); Text("\(m.gY)년") }.appFont(11).foregroundStyle(Theme.muted)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if fromSettings || m.gDone.contains("goal") {
                    GuideBubble(pose: .point, text: String(localized: "카드를 누르거나 숫자를 고치면 위 그래프와 확률이 바로 바뀌어요. 저장하면 미션 진행은 그대로 두고 목표만 바꿔요."))
                } else if m.route == .novice {
                    GuideBubble(pose: .wave, text: String(localized: "처음 시작하시는군요. …… 첫 목표부터 정해요. 지금 모아 둔 투자금이 없으면 0을 넣으세요."))
                } else {
                    GuideBubble(pose: .think, text: String(localized: "언제까지, 얼마를 모으고 싶나요? …… 카드는 매달 넣는 돈으로 닿을 확률이 반반쯤인 금액이에요. 모두 지금 \(String(describing: AppModel.wonK(start)))에서 출발한 계산이에요."))
                }
                if m.route != .novice {
                    Text((m.gM > 0 ? String(localized: "월 \(String(describing: noWon(AppModel.wonK(m.gM))))씩 넣으면") : String(localized: "적립 없이")) + String(localized: " 확률 50%인 목표")).appFont(13, .semibold).foregroundStyle(Theme.sub)
                }
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    ForEach(m.goalPresets) { p in
                        let on = m.gK == p.k && m.gY == p.y
                        Button { m.gK = p.k; m.gY = p.y; k = String(Int(p.k)); y = String(p.y) } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(alignment: .firstTextBaseline) { Text(p.name).appFont(15, .bold); Text("\(p.y)년").appFont(12).foregroundStyle(Theme.sub) }
                                if !p.mean.isEmpty { Text(p.mean).appFont(12).foregroundStyle(Theme.sub).multilineTextAlignment(.leading) }
                                Text(p.need).appFont(13, .bold).foregroundStyle(Theme.teal)
                            }
                            .padding(12).frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
                            .background(on ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
                        }.buttonStyle(.plain)
                    }
                }
                inputField(String(localized: "목표 (만원)"), $k).focused($focus).onChange(of: k) { _, v in if let d = Double(v.replacingOccurrences(of: ",", with: "")), d > 0 { m.gK = d } }
                inputField(String(localized: "기간 (년)"), $y).focused($focus).onChange(of: y) { _, v in if let d = Int(v.replacingOccurrences(of: ",", with: "")) { m.gY = min(30, max(1, d)) } }
                if m.noviceOwn {
                    Text("출발 \(noWon(AppModel.wonK(start))) · 내 종목 기준").appFont(13).foregroundStyle(Theme.sub)
                } else if m.route == .novice {
                    inputField(String(localized: "모아 둔 돈 (만원)"), $a).focused($focus).onChange(of: a) { _, v in m.gA = max(0, Double(v) ?? 0) }
                } else {
                    inputField(String(localized: "월 적립 (만원)"), $mo).focused($focus).onChange(of: mo) { _, v in m.gM = max(0, Double(v.replacingOccurrences(of: ",", with: "")) ?? 0) }
                    Button("50%로 맞추기") { focus = false; m.gK = m.autoGoal(years: m.gY); k = String(Int(m.gK)) }
                        .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 44)
                    Text("출발 \(noWon(AppModel.wonK(start))) · 목표일 \(m.goalToShort)").appFont(13).foregroundStyle(Theme.sub)
                }
                PrimaryButton(title: fromSettings || m.gDone.contains("goal") ? String(localized: "저장") : String(localized: "길 보기"), color: ok ? Theme.teal : Theme.muted) {
                    guard ok else { return }
                    focus = false
                    if m.route == .novice { m.gM = max(5, (need / 5).rounded() * 5) }
                    if fromSettings { dismiss(); return }
                    if m.gDone.contains("goal") { m.boardPath.removeLast(); return }   // 고치기: 미션 판으로 돌아간다
                    m.gDone.insert("goal"); m.boardPath.append(.g1r)
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { k = String(Int(m.gK)); y = String(m.gY); a = String(Int(m.gA)); mo = String(Int(m.gM)) }
        .scrollDismissesKeyboard(.interactively)
        .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료") { focus = false } } }
    }
}

struct GoalSetResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let need = m.neededMonthly(), base = m.route == .novice ? AppModel.mixes[0] : m.mineMix
        let path = m.simulate(base, monthly: need)
        Page(kicker: String(localized: "미션 \(String(describing: m.goalNo("goal"))) 완료 · 경로 열림"), title: String(localized: "\(String(describing: m.gY))년 뒤 \(String(describing: noWon(AppModel.wonK(m.gK))))까지")) {
            VStack(alignment: .leading, spacing: 6) {
                Text("보통의 경우 매달").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Text(need > 0 ? noWon(AppModel.wonK(need)) : String(localized: "0 · 이미 충분")).appFont(30, .bold).roll(need > 0 ? noWon(AppModel.wonK(need)) : String(localized: "0 · 이미 충분"))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            GuideBubble(pose: .back, text: m.route == .novice
                        ? String(localized: "같이 봐요. 매달 \(String(describing: AppModel.wonK(need)))씩 넣으면 지날 길이에요. 점선은 내가 넣은 원금이에요.")
                        : String(localized: "같이 봐요. 지금 금액이 보통의 경우처럼 자란다고 보고, 모자란 만큼을 매달로 나눈 금액이에요."), note: String(localized: "그래프 쪽을 돌아봄"))
            Card {
                (Text("월 \(noWon(AppModel.wonK(need)))씩 ") + Text("· \(base.name)").foregroundColor(Theme.sub)).appFont(15, .bold)
                GoalPathChart(sims: [(path, Theme.teal, true)], goal: m.gK).frame(height: 150)
                HStack { Text("지금"); Spacer(); Text("\(String(format: "%g", Double(m.gY) / 2))년"); Spacer(); Text("\(m.gY)년") }.appFont(11).foregroundStyle(Theme.muted)
                FlowRow(spacing: 10) {
                    legend(Theme.teal, String(localized: "보통의 경우")); legend(Theme.teal.opacity(0.2), String(localized: "100번 중 90번"))
                    legend(Theme.muted, String(localized: "원금")); legend(Color(hex: 0xC8352E), String(localized: "목표"))
                }
                (Text("목표일 달성 ") + Text(AppModel.pct(path.prob)).bold() + Text(" · 보통 \(path.etaText)")).appFont(14)
            }
            BoardLinks(next: String(localized: "다음: 구성 고르기"), to: .g3)
        }
    }
}

// MARK: 구성 고르기 — 구성별 길 비교 고정 + 칩

private func compositionBar(_ weights: [String: Double]) -> some View {
    let keys = ["T", "I", "G", "B", "C"].filter { (weights[$0] ?? 0) > 0.001 }
    return GeometryReader { g in
        HStack(spacing: 0) {
            ForEach(keys, id: \.self) { k in
                Rectangle().fill(compColor[k]!).frame(width: g.size.width * (weights[k] ?? 0))
                    .overlay(alignment: .trailing) { Rectangle().fill(Theme.card).frame(width: 2) }
            }
        }
    }
    .frame(height: 10).clipShape(Capsule()).background(Theme.track, in: Capsule())
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(keys.map { "\(compName[$0]!) \(AppModel.pct(weights[$0] ?? 0))" }.joined(separator: ", "))
}

struct GoalMixView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []

    var body: some View {
        @Bindable var m = m
        let sims = m.goalMixes.map { m.simulate($0) }
        let sel = sims.first { $0.mix.id == m.gMix } ?? sims[0]
        let sh = m.shift(sel.mix)
        PinnedLayout {
            VStack(alignment: .leading, spacing: 8) {
                Text("미션 \(m.goalNo("mix")) / \(m.goalTotal)").appFont(14, .semibold).foregroundStyle(Theme.teal)
                Text("어떤 구성으로?").appFont(20, .bold)
                Text("지수·업종 묶음 · 월 \(noWon(AppModel.wonK(m.gM))) · \(m.gY)년").appFont(12).foregroundStyle(Theme.sub)
                GoalPathChart(sims: sims.map { ($0, mixColor[$0.mix.id] ?? Theme.teal, $0.mix.id == sel.mix.id) }, goal: m.gK, showPrincipal: false).frame(height: 100)
                FlowRow(spacing: 10) {
                    ForEach(sims, id: \.mix.id) { s in legend(mixColor[s.mix.id] ?? Theme.teal, s.mix.name) }
                    legend(Color(hex: 0xC8352E), String(localized: "목표"))
                }
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 3).fill(mixColor[sel.mix.id] ?? Theme.teal).frame(width: 10, height: 10)
                    Text(sel.mix.name).appFont(15, .bold)
                    Text(sel.mix.tag).appFont(12).foregroundStyle(Theme.sub)
                }
                compositionBar(sh.weights)
                HStack(spacing: 6) {
                    pill(String(localized: "목표일 달성"), AppModel.pct(sel.prob))
                    pill(String(localized: "보통 도착"), sel.etaText)
                    pill(String(localized: "나쁜 해 한 번"), AppModel.sgn0(sel.badYear))
                }
                ChipRow(items: m.goalMixes.map { ($0.id, $0.name) }, selection: $m.gMix, fill: true)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if m.route == .novice {
                    GuideBubble(pose: .think, text: String(localized: "어떤 구성이 덜 흔들리는지 보세요. …… 처음이라면 단순한 구성부터 비교해 봐도 돼요."))
                } else {
                    GuideBubble(pose: .side, text: String(localized: "구성을 하나씩 눌러 지금 내 구성과 비교해 보세요. 빨리 가는 구성일수록 더 흔들려요. 나쁜 해 한 번의 크기도 같이 보세요."))
                }
                if !sh.line.isEmpty { Text(sh.line).appFont(13).foregroundStyle(Theme.sub) }
                Text(sel.mix.desc).appFont(15).lineSpacing(3)
                BasketList(baskets: [.G, .B], open: $open, title: String(localized: "업종 묶음"))
                Text("나쁜 해 한 번: 20년에 한 번쯤 오는 해의 1년 변화 · 가정값 · 투자 권유 아님")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
                PrimaryButton(title: String(localized: "\"\(String(describing: sel.mix.name))\"으로 정하기")) { m.gDone.insert("mix"); m.boardPath.append(.g3r) }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func pill(_ k: String, _ v: String) -> some View {
        VStack(spacing: 2) { Text(k).appFont(11).foregroundStyle(Theme.sub); Text(v).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.7) }
            .frame(maxWidth: .infinity).padding(.vertical, 6)
            .background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
    }
}

struct GoalMixResultView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []
    var body: some View {
        let sel = m.simulate(m.selectedMix), sh = m.shift(m.selectedMix)
        let next = m.goalSteps.first { !m.gDone.contains($0.id) && m.goalAvailable($0) }
        Page(kicker: String(localized: "미션 \(String(describing: m.goalNo("mix"))) 완료 · 구성 비교 열림"), title: String(localized: "내 계획: \(String(describing: sel.mix.name))")) {
            GuideBubble(pose: .cheer, text: m.route == .novice ? String(localized: "정했어요. 매달 적립을 나누는 비율이에요. 실제 매매 판단은 직접 하셔야 해요.") : String(localized: "정했어요. 계획만 기록해요. 실제 매매 판단은 직접 하셔야 해요."))
            Text(sel.mix.desc).appFont(15).foregroundStyle(Theme.sub)
            Card {
                (Text("목표일 달성 ") + Text(AppModel.pct(sel.prob)).bold() + Text(" · 보통 ") + Text(sel.etaText).bold()).appFont(15)
            }
            if !sh.rows.isEmpty {
                Card {
                    Text(m.route == .novice ? String(localized: "적립 나누기") : String(localized: "비중이 바뀌면")).appFont(15, .bold)
                    ForEach(sh.rows, id: \.0) { label, sub, amt, up in
                        HStack {
                            Text(label).appFont(15, .semibold)
                            Text(sub).appFont(13).foregroundStyle(Theme.sub)
                            Spacer()
                            Text(amt).appFont(15, .bold).foregroundStyle(up ? Theme.teal : Color(hex: 0x2450C8, dark: 0x7FA2F0))
                        }
                        .frame(minHeight: 34)
                    }
                    if !sh.note.isEmpty { Text(sh.note).appFont(12).foregroundStyle(Theme.sub).lineSpacing(2) }
                }
            }
            if let b = sel.mix.basket { BasketList(baskets: [b], open: $open, title: String(localized: "담을 업종")) }
            Text("계획만 기록 · 매매는 직접").appFont(14).foregroundStyle(Theme.sub)
            BoardLinks(next: next.map { String(localized: "다음: \(String(describing: $0.title))") }, to: next?.to)
        }
    }
}

// MARK: 비중 조정 세금 (플러스)

struct GoalTaxView: View {
    @Environment(AppModel.self) private var m
    @State private var gain = ""
    var body: some View {
        @Bindable var m = m
        let sh = m.shift(m.selectedMix), sale = sh.sell * m.goalStart
        // 양도세는 해외(미국) 주식만. 가장 큰 종목이 국내 주식이면 계산하지 않는다
        let drnk = m.focusRow, us = drnk?.sym.currency == .usd, gainRate = us ? drnk.map { 1 - $0.h.avg / $0.sym.last } ?? 0 : 0
        let gainSale = sale * gainRate, G0 = m.taxGain
        let tx = { (x: Double) in max(0, x - 250) * 0.22 }
        let tOne = tx(G0 + gainSale), tSplit = tx(G0 + gainSale / 2) + tx(gainSale / 2), tNo = tx(G0)
        let rows: [(String, String, String, Double)] = [
            ("one", String(localized: "한 해에 모두"), String(localized: "이익 \(String(describing: noWon(AppModel.wonK(max(0, G0 + gainSale))))) − 공제 250만"), tOne),
            ("split", String(localized: "두 해에 반씩"), String(localized: "공제 250만 × 2"), tSplit),
            ("none", String(localized: "적립만"), String(localized: "오래 걸림"), tNo),
        ]
        let pick = rows.first { $0.0 == m.goalTaxPick } ?? rows[1]
        Page(kicker: String(localized: "미션 \(String(describing: m.goalNo("tax"))) / \(String(describing: m.goalTotal))"), title: String(localized: "비중 맞추려 팔면 세금은?")) {
            GuideBubble(pose: .point, text: String(localized: "비중을 매도로 맞추면 이익에 세금이 붙어요. 공제는 해마다 있어서, 나누는 방식에 따라 세금이 달라져요. 계산 예시예요."))
            Text("한국 거주자 · 미국 주식만").appFont(13, .semibold).foregroundStyle(Theme.teal)
            inputField(String(localized: "올해 해외주식 이익 (만원)"), $gain).onChange(of: gain) { _, v in m.taxGain = max(0, Double(v) ?? 0) }
            if sh.sell > 0 && gainSale > 0 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("내년 5월 세금 · \(pick.1)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        if pick.0 != "one" { Text(noWon(AppModel.wonK(tOne))).appFont(18).strikethrough().foregroundStyle(Theme.muted) }
                        Text(noWon(AppModel.wonK(pick.3))).appFont(30, .bold).roll(noWon(AppModel.wonK(pick.3)))
                        Text(pick.0 == "one" ? String(localized: "가장 많음") : "−\(noWon(AppModel.wonK(max(0, tOne - pick.3))))").appFont(13, .bold).foregroundStyle(Theme.mint)
                    }
                    Text(pick.2).appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
                ChipRow(items: [("one", String(localized: "한 해에")), ("split", String(localized: "두 해에")), ("none", String(localized: "적립만"))], selection: $m.goalTaxPick, fill: true)
                Text("가장 큰 종목 \(AppModel.pct(m.focusWeight))→\(AppModel.pct(m.focusWeight - sh.sell)) · 두 해에 나누면 세금 −\(noWon(AppModel.wonK(max(0, tOne - tSplit))))")
                    .appFont(14).foregroundStyle(Theme.sub).lineSpacing(2)
            } else {
                Text(!us ? String(localized: "가장 큰 종목이 국내 주식 · 양도세 계산 제외")
                     : sh.sell > 0 ? String(localized: "줄이는 부분이 손실 · 낼 세금 없음 · 같은 해 이익과 합산")
                     : String(localized: "\"\(String(describing: m.selectedMix.name))\"은 팔 게 없음 · 미션 \(String(describing: m.goalNo("mix")))에서 다른 구성으로 비교"))
                    .appFont(15).padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
            Text("결제일 기준 · 계산 예시 · 세무 상담·매매 권유 아님").appFont(12).foregroundStyle(Theme.muted)
            PrimaryButton(title: String(localized: "확인")) { m.gDone.insert("tax"); m.boardPath = [] }
        }
        .onAppear { gain = String(Int(m.taxGain)) }
    }
}

// MARK: 3개월 인터미션

struct GoalIntermissionView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        @Bindable var m = m
        let fixed = m.gWeeks, i = min(fixed.count, 2), step = AppModel.goalMonthSteps[i]
        let now = m.goalSaved, eta = m.monthsTo(from: now, monthly: m.gM), plan = Double(m.gY * 12 - fixed.count - (m.gWeekCur == nil ? 0 : 1))
        let last = m.gWeekCur ?? fixed.last
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack { Text("석 달 체크인").appFont(12, .bold).foregroundStyle(Theme.mint); Spacer(); Text("\(fixed.count)/3").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                    Text("이번 달, 계획대로 넣었나요?").appFont(20, .bold)
                    HStack(spacing: 6) {
                        ForEach(0..<3, id: \.self) { j in
                            let p: GoalWeek? = j < fixed.count ? fixed[j] : (j == fixed.count ? m.gWeekCur : nil)
                            VStack(spacing: 2) {
                                Text(String(localized: "\(String(describing: j + 1))달") + (j == fixed.count ? String(localized: " · 이번 달") : "")).appFont(12)
                                Text(p?.label ?? (j == fixed.count ? String(localized: "고르는 중") : "")).appFont(12, .bold)
                            }
                            .frame(maxWidth: .infinity, minHeight: 64)
                            .foregroundStyle(p != nil ? .white : j == fixed.count ? Theme.yellow : Theme.muted)
                            .background(p?.color ?? .clear, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(j == fixed.count ? Theme.yellow : (p?.color ?? Theme.slate), style: StrokeStyle(lineWidth: 2, dash: p == nil ? [5, 4] : [])))
                        }
                    }
                    if let last {
                        Text((last == .more ? String(localized: "더 넣음") : last == .less ? String(localized: "덜 넣음") : String(localized: "계획대로"))
                             + (eta.isFinite ? String(localized: " · \(String(describing: AppModel.eta(eta))) 도착 (") + (eta < plan ? String(localized: "\(String(describing: Int(plan - eta)))개월 빠름") : eta > plan ? String(localized: "\(String(describing: Int(eta - plan)))개월 늦음") : String(localized: "목표 시점")) + ")" : ""))
                            .appFont(14, .semibold).foregroundStyle(Theme.ink)
                            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(last == .less ? Color(hex: 0xFDF0E1, dark: 0x3A2A17) : last == .more ? Color(hex: 0xE4F0FB, dark: 0x16283A) : Color(hex: 0xE3F4EC, dark: 0x163226), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .foregroundStyle(.white).padding(16)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))

                GuideBubble(pose: .stand, text: String(localized: "3개월 동안 매달 1분이면 돼요. 이번 달 할 일을 하고 나서 표시해 주세요. 체크인할 때마다 캐릭터 위젯이 하나씩 열려요."))

                if fixed.count < 3 {
                    // 다음 달이 열리기 전에는 방금 한 달을 다시 고를 수 있다
                    let edit = m.monthEditable, k = edit ? fixed.count - 1 : fixed.count, step = AppModel.goalMonthSteps[k]
                    Card {
                        Text("\(k + 1)달째 체크인").appFont(13, .bold).foregroundStyle(Theme.teal)
                        HStack(spacing: 8) {
                            Text(step.whereText).appFont(11, .bold).padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                            Text(step.task).appFont(15, .bold)
                        }
                        HStack(spacing: 6) {
                            ForEach(GoalWeek.allCases, id: \.self) { p in
                                let on = edit ? fixed[k] == p : m.gWeekCur == p
                                Button { if edit { m.editLastMonth(p) } else { m.gWeekCur = p } } label: {
                                    VStack(spacing: 2) {
                                        Text(p.label).appFont(15, .bold)
                                        Text(String(localized: "월 ") + AppModel.wonK(m.gM * p.factor)).appFont(11).foregroundStyle(Theme.sub)
                                    }
                                    .foregroundStyle(Theme.ink).padding(.vertical, 8)
                                    .frame(maxWidth: .infinity, minHeight: 64)
                                    .background(on ? p.color.opacity(0.15) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.color, lineWidth: on ? 3 : 2))
                                }.buttonStyle(.plain)
                            }
                        }
                        Text("다음 달 전까지 바꿀 수 있음").appFont(12).foregroundStyle(Theme.muted)
                        // 달은 시작한 날부터 30일마다 열린다 (서버 시각). 열리기 전에는 남은 날을 보여 준다
                        if let wait = m.monthGateText {
                            Label(wait, systemImage: "clock").appFont(13).foregroundStyle(Theme.sub)
                                .fixedSize(horizontal: false, vertical: true)
                        } else if m.gWeekCur != nil {
                            Button("체크인") { withAnimation { m.confirmGoalMonth() } }
                                .appFont(14, .semibold).foregroundStyle(Theme.ink).frame(maxWidth: .infinity, minHeight: 44)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                        }
                    }
                }
                Card {
                    Text("체크인 보상 위젯").appFont(15, .bold)
                    Text("오늘의 움직임 · 1달 세리 · 2달 시오 · 3달 모두").appFont(13).foregroundStyle(Theme.sub)
                    ForEach(0..<3, id: \.self) { j in
                        let on = j < fixed.count
                        HStack(spacing: 12) {
                            if j < 2 {
                                Pixel(name: (on ? "spr_" : "sil_") + ["seri", "sio"][j], width: 42, height: 60)
                            } else {
                                HStack(spacing: 0) { ForEach(Shelter.friends) { f in Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 18, height: 26) } }
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack { Text("오늘의 움직임").appFont(11, .bold); Spacer(); Text(on ? String(localized: "받음") : j == fixed.count ? String(localized: "이번 달") : String(localized: "\(String(describing: j + 1))달 뒤")).appFont(11).foregroundStyle(Theme.sub) }
                                Text(AppModel.sgn(m.todayMove)).appFont(16, .bold)
                                Text(AppModel.goalMonthSteps[j].reward).appFont(12).foregroundStyle(Theme.sub)
                            }
                        }
                        .padding(12)
                        .background(on ? Theme.card : Color(hex: 0xF0F2F4, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.border : Theme.dash, style: StrokeStyle(lineWidth: 1, dash: on ? [] : [4, 3])))
                        .opacity(on ? 1 : 0.8)
                    }
                }
                if fixed.count >= 3 {
                    PrimaryButton(title: m.interDone ? String(localized: "미션 판으로") : String(localized: "인터미션 끝")) { m.gDone.insert("inter"); m.boardPath = [] }
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

