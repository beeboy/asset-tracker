import SwiftUI

// 목표 달성 루트 화면 (시안 g1·g1r·g3·g3r·gt·gi·gp1r, 목표 미션 판). 설명은 계산값, 종목 추천 문구 없음.

private let mixColor: [String: Color] = ["mine": Theme.purple, "index": Color(hex: 0x1F3A7A, dark: 0x7F9BE0), "grow": Theme.green, "steady": Theme.blue]
private let compColor: [String: Color] = ["T": Theme.purple, "I": Color(hex: 0x1F3A7A, dark: 0x7F9BE0), "G": Theme.green, "B": Theme.blue, "C": Color(hex: 0xBFC6CD, dark: 0x56616C)]
private let compName: [String: String] = ["T": "비중 1위 종목", "I": "지수", "G": "성장 지속", "B": "버팀목", "C": "현금"]

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

// MARK: 목표 미션 판

struct GoalBoardView: View {
    @Environment(AppModel.self) private var m
    @State private var openDone = false

    var body: some View {
        let steps = m.goalSteps, done = steps.filter { m.gDone.contains($0.id) }
        let start = m.goalStart
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AppHeader().padding(.horizontal, -16)
                VStack(alignment: .leading, spacing: 4) {
                    ScreenTitle(kicker: "목표 달성 루트" + (m.route == .plus ? " · 플러스" : " · 시작 전"),
                                title: m.gDone.contains("goal") ? "\(m.gY)년 뒤 \(AppModel.wonK(m.gK))까지" : "목표까지 가는 길")
                        // 목표를 정한 뒤에는 제목(목표 금액)을 누르면 고친다
                        .overlay(alignment: .bottomTrailing) {
                            if m.gDone.contains("goal") { Image(systemName: "pencil").appFont(16, .semibold).foregroundStyle(Theme.teal).padding(.bottom, 6) }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { if m.gDone.contains("goal") { m.boardPath.append(.g1) } }
                        .accessibilityAddTraits(m.gDone.contains("goal") ? .isButton : [])
                        .accessibilityHint(m.gDone.contains("goal") ? "눌러서 목표 고치기" : "")
                    if m.gDone.contains("goal") {
                        Text("지금 \(AppModel.wonK(start)) · 목표까지 \(AppModel.wonK(max(0, m.gK - start))) 남았어요").appFont(15).foregroundStyle(Theme.sub)
                    }
                }
                if !done.isEmpty {
                    Button { withAnimation { openDone.toggle() } } label: {
                        HStack(spacing: 8) {
                            Text("✓ \(done.count)개 끝냄").fontWeight(.bold)
                            Text(done.map(\.title).joined(separator: " · ")).lineLimit(1)
                            Spacer(minLength: 0)
                            Text(openDone ? "접기 ▴" : "펼치기 ▾")
                        }
                        .appFont(14, .semibold).foregroundStyle(Theme.tealDark)
                        .padding(.horizontal, 12).frame(minHeight: 48)
                        .background(Theme.tealBg, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain)
                }
                let shown = steps.filter { openDone || !m.gDone.contains($0.id) }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                    ForEach(shown.filter { !$0.inter }) { block($0) }
                }
                ForEach(shown.filter(\.inter)) { block($0) }
                if let next = steps.first(where: { !m.gDone.contains($0.id) && m.goalAvailable($0) }), !next.inter {
                    Card(padding: 18) {
                        Text("다음 미션 \(next.num)").appFont(13, .semibold).foregroundStyle(Theme.teal)
                        Text(next.title).appFont(19, .bold)
                        (Text("끝내면 열려요: ") + Text(next.reward).bold().foregroundColor(Theme.ink)).appFont(14).foregroundStyle(Theme.sub)
                        PrimaryButton(title: "시작하기") { m.boardPath.append(next.to) }
                    }
                }
                if m.playUnlocked { GoalCellsCard(); WeekForecastCard() } else {
                    DashedCard {
                        Label("1000칸 · 100칸 선물 · 이번 주 예보", systemImage: "lock").appFont(15, .bold)
                        Text("앱 시작 3단계를 마치면 열려요. 인터미션 동안에도 칸을 채우고 매주 예보를 맞혀 볼 수 있어요.").appFont(13)
                    }
                }
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: MissionRoute.self) { MissionScreen(route: $0) }
    }

    private func block(_ b: AppModel.GoalStep) -> some View {
        let done = m.gDone.contains(b.id), avail = m.goalAvailable(b)
        return Button { if done || avail { m.boardPath.append(b.to) } } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(b.num).appFont(13, .bold)
                    Spacer()
                    if done { Image(systemName: "checkmark").appFont(13, .bold) } else if !avail { Image(systemName: "lock").appFont(12) }
                }
                Text(b.title).appFont(13, .semibold).lineLimit(2).multilineTextAlignment(.leading)
                if b.inter && !done && avail {
                    HStack(spacing: 4) {
                        ForEach(0..<3, id: \.self) { i in
                            let pick = i < m.gWeeks.count ? m.gWeeks[i] : nil
                            Text("\(i + 1)달").appFont(11, .bold).frame(maxWidth: .infinity, minHeight: 20)
                                .foregroundStyle(pick != nil ? .white : i == m.gWeeks.count ? Theme.yellow : Theme.muted)
                                .background(pick?.color ?? (i == m.gWeeks.count ? .clear : Theme.slate), in: RoundedRectangle(cornerRadius: 6))
                                .overlay { if pick == nil && i == m.gWeeks.count { RoundedRectangle(cornerRadius: 6).stroke(Theme.yellow, lineWidth: 2) } }
                        }
                    }
                    Text("\(m.gWeeks.count)/3달 · 이번 달: \(AppModel.goalMonthSteps[min(m.gWeeks.count, 2)].task)").appFont(12).foregroundStyle(Color(hex: 0xC9D0D6))
                }
            }
            .padding(b.inter ? 12 : 10)
            .frame(maxWidth: .infinity, minHeight: b.inter ? 72 : 88, alignment: .topLeading)
            .foregroundStyle(b.inter ? (done ? Theme.yellow : avail ? .white : Theme.sub2) : (done ? .white : avail ? Theme.ink : Theme.sub2))
            .background(b.inter ? (done || avail ? Theme.night : Theme.track) : (done ? Theme.teal : avail ? Theme.card : Theme.track), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.inter ? (done ? Theme.ink : avail ? Theme.yellow : Theme.dash) : (done || avail ? Theme.teal : Theme.track),
                                                               style: StrokeStyle(lineWidth: 2, dash: b.inter && !done ? [6, 4] : [])))
        }
        .buttonStyle(.plain).disabled(!(done || avail))
        .accessibilityLabel("미션 \(b.num): \(b.title)")
    }
}

// 목표까지 1000칸: 진한 칸 = 내가 넣은 돈(안 사라짐), 연한 칸 = 시장이 준 칸, 주황 = 시장이 잠시 가져간 칸
struct GoalCellsCard: View {
    @Environment(AppModel.self) private var m
    @State private var mode = "mine"
    var body: some View {
        let unit = m.gK * 1e4 / 1000
        let cl = { (v: Double) in max(0, min(1000, Int(floor(v / max(1, unit))))) }
        let tkV = m.trackValue
        let tkP = m.route == .novice ? tkV / 1.02 : tkV * max(0.8, min(1.25, m.cost / max(1, m.total)))
        let nV = cl(tkV), nP = cl(tkP), nMine = min(nV, nP), nMkt = max(0, nV - nP), nLost = max(0, nP - nV)
        let nY = cl(tkV / (1 + m.todayMove)), chg = nV - nY, passed = nV / 100
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("목표까지 1000칸").appFont(13, .bold).foregroundStyle(Theme.sub)
                Spacer()
                Text("1칸 = \(AppModel.man(unit).replacingOccurrences(of: "만원", with: ""))만원").appFont(12).foregroundStyle(Theme.sub)
            }
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(nV.formatted())칸").appFont(26, .bold)
                Text("/ 1,000칸").appFont(14).foregroundStyle(Theme.sub)
                Text(chg == 0 ? "어제와 같음" : "어제 종가보다 \(chg > 0 ? "+" : "")\(chg)칸").appFont(14, .bold).foregroundStyle(chg == 0 ? Theme.sub : Theme.change(Double(chg)))
            }
            ChipRow(items: [("mine", "내 칸 / 시장 칸"), ("chg", "오늘 바뀐 칸")], selection: $mode)
            GoalCellGrid(mine: nMine, market: nMkt, lost: nLost, yesterday: nY, showChange: true)
                .aspectRatio(320.0 / 200.0, contentMode: .fit)
                .accessibilityLabel("1000칸 중 \(nV)칸")
            FlowRow(spacing: 10) {
                legend(Theme.teal, "내가 넣은 돈 \(nMine)칸")
                legend(Color(hex: 0x7FD3C9, dark: 0x3C8F86), "시장이 준 칸 \(nMkt)칸")
                if nLost > 0 { legend(Color(hex: 0xFBE3CF, dark: 0x4A3020), "시장이 잠시 가져간 칸 \(nLost)칸") }
                legend(Theme.yellow, "마지막 칸 = 목표")
            }
            Text("진한 칸은 내가 넣은 돈이라 시장이 내려도 사라지지 않아요. 연한 칸은 시장이 준 몫이라 오르내림에 따라 늘었다 줄었다 해요. 이번 달 \(AppModel.wonK(m.gM))을 넣으면 진한 칸이 \(Int(m.gM * 1e4 / unit))칸 늘어요.")
                .appFont(12).foregroundStyle(Theme.sub).lineSpacing(2)
            HStack {
                Text("100칸 선물").appFont(13, .bold); Spacer()
                Text(passed >= 10 ? "1000칸 완성!" : "\((passed + 1) * 100)칸까지 \((passed + 1) * 100 - nV)칸").appFont(13).foregroundStyle(Theme.sub)
            }
            ShareCardButton(filled: nV, mine: nMine, market: nMkt, kicker: "목표까지 가는 중",
                            amountLine: "목표 \(AppModel.wonK(m.gK)) 중 \(AppModel.man(tkV)) 모았어요.")
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.ink, lineWidth: 2))
    }
}

struct GoalCellGrid: View {
    let mine: Int, market: Int, lost: Int, yesterday: Int
    var showChange = false
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 40
            func rect(_ i: Int) -> CGRect { CGRect(x: CGFloat(i % 40) * s + 0.5, y: CGFloat(24 - i / 40) * s + 0.5, width: s - 1, height: s - 1) }
            let now = mine + market
            for i in 0..<1000 {
                let c: Color = i < mine ? Theme.teal : i < now ? Color(hex: 0x7FD3C9, dark: 0x3C8F86) : i < now + lost ? Color(hex: 0xFBE3CF, dark: 0x4A3020) : Theme.track
                ctx.fill(Path(rect(i)), with: .color(c))
            }
            if showChange {
                for i in min(now, yesterday)..<max(now, yesterday) { ctx.stroke(Path(rect(i)), with: .color(now >= yesterday ? Theme.up : Theme.down), lineWidth: 1.2) }
            }
            ctx.fill(Path(ellipseIn: rect(999).insetBy(dx: 0.5, dy: 0.5)), with: .color(Theme.yellow))
        }
    }
}

// MARK: 플러스 미션 1 결과 — 지금 평가액

struct GoalHoldResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        Page(kicker: "미션 1 완료 · 새로 열림: 평가액·수익률", title: "지금 내 평가액") {
            if m.ret >= 0 { GuideBubble(pose: .spread, text: "지금 평가액이에요. 들어간 돈보다 \(AppModel.sgn(m.ret)) 위에 있어요. 여기서 출발해 목표 금액까지 가는 길을 그려요.") }
            VStack(alignment: .leading, spacing: 8) {
                HStack { Text("지금 평가액"); Spacer(); Text("들어간 돈 \(AppModel.man(m.cost))") }.appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(AppModel.man(m.total)).appFont(30, .bold)
                    Text(AppModel.sgn(m.ret)).appFont(16, .bold).foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
                }
                Text("\(m.focusName) 비중 \(AppModel.pct(m.focusWeight))").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            if m.ret >= 0 {
                BoardLinks(next: "다음 미션: 목표 정하기", to: .g1)
            } else {
                Text("숫자로 보면 지금은 마이너스예요. 본전까지 가는 길부터 보는 회복 루트가 더 맞아요. 넣은 종목은 그대로 가져가요.")
                    .appFont(15).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0xFDF0E1, dark: 0x3A2A17), in: RoundedRectangle(cornerRadius: 12))
                PrimaryButton(title: "회복 루트로 옮기기") { m.switchRoute(.recover) }
                BoardLinks(next: nil, to: nil)
                Button("그래도 목표 루트로 계속") { m.boardPath.append(.g1) }
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

    var body: some View {
        let need = m.neededMonthly(), path = m.simulate(m.route == .novice ? AppModel.mixes[0] : m.mineMix, monthly: need)
        let start = m.goalStart, cagr = start > 0 ? pow(m.gK / start, 1 / Double(m.gY)) - 1 : 0
        let ok = m.gK > 0 && m.gY >= 1
        PinnedLayout {
            VStack(alignment: .leading, spacing: 8) {
                Text(fromSettings || m.gDone.contains("goal") ? "목표 고치기" : "미션 \(m.goalNo("goal")) / \(m.goalTotal)").appFont(14, .semibold).foregroundStyle(Theme.teal)
                Text("언제까지, 얼마를 모으고 싶나요?").appFont(22, .bold)
                HStack(alignment: .firstTextBaseline) {
                    Text("\(m.gY)년 뒤 \(AppModel.wonK(m.gK))").appFont(17, .bold)
                    Spacer()
                    (Text("닿을 확률 ") + Text(AppModel.pct(path.prob)).bold().foregroundColor(Theme.teal)).appFont(14)
                }
                Text((need > 0 ? "월 " + AppModel.wonK(need) : "이미 충분") + " (1년 \(AppModel.wonK(need * 12)))").appFont(14, .semibold)
                Text(m.route == .novice ? (m.gK <= 1000 ? "첫 목표 · 적립 습관 만들기" : "연 7%면 한 해 \(AppModel.wonK(m.gK * 0.07))씩 불어나는 크기")
                     : "지금 \(AppModel.wonK(start))의 \(String(format: "%.1f", m.gK / max(1, start)))배 · 적립 없이 가려면 한 해 +\(Int((cagr * 100).rounded()))%씩")
                    .appFont(13).foregroundStyle(Theme.sub)
                GoalPathChart(sims: [(path, Theme.teal, true)], goal: m.gK).frame(height: 80)
                HStack { Text("지금"); Spacer(); Text("빨간 선 = 목표 · 점선 = 넣은 원금"); Spacer(); Text("\(m.gY)년") }.appFont(11).foregroundStyle(Theme.muted)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if fromSettings || m.gDone.contains("goal") {
                    GuideBubble(pose: .point, text: "카드를 누르거나 숫자를 고치면 위 그래프와 확률이 바로 바뀌어요. 저장하면 미션 진행은 그대로 두고 목표만 바꿔요.")
                } else if m.route == .novice {
                    GuideBubble(pose: .wave, text: "처음 시작하시는군요. …… 첫 목표부터 정해요. 지금 모아 둔 투자금이 없으면 0을 넣으세요.")
                } else {
                    GuideBubble(pose: .think, text: "언제까지, 얼마를 모으고 싶나요? …… 카드를 누르면 위 그래프와 확률이 바로 바뀌어요. 모두 지금 \(AppModel.wonK(start))에서 출발한 계산이에요.")
                }
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    ForEach(m.goalPresets) { p in
                        let on = m.gK == p.k && m.gY == p.y
                        Button { m.gK = p.k; m.gY = p.y; k = String(Int(p.k)); y = String(p.y) } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(alignment: .firstTextBaseline) { Text(p.name).appFont(15, .bold); Text("\(p.y)년").appFont(12).foregroundStyle(Theme.sub) }
                                Text(p.mean).appFont(12).foregroundStyle(Theme.sub).multilineTextAlignment(.leading)
                                Text(p.need).appFont(13, .bold).foregroundStyle(Theme.teal)
                            }
                            .padding(12).frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
                            .background(on ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
                        }.buttonStyle(.plain)
                    }
                }
                inputField("목표 금액 (만원)", $k).focused($focus).onChange(of: k) { _, v in if let d = Double(v.replacingOccurrences(of: ",", with: "")), d > 0 { m.gK = d } }
                inputField("몇 년 뒤", $y).focused($focus).onChange(of: y) { _, v in if let d = Int(v.replacingOccurrences(of: ",", with: "")) { m.gY = min(30, max(1, d)) } }
                if m.route == .novice {
                    inputField("지금 모아 둔 투자금 (만원, 없으면 0)", $a).focused($focus).onChange(of: a) { _, v in m.gA = max(0, Double(v) ?? 0) }
                } else {
                    Text("출발점: 지금 평가액 \(AppModel.wonK(start)) (미션 1에서 넣은 종목 기준)").appFont(13).foregroundStyle(Theme.sub)
                }
                PrimaryButton(title: fromSettings || m.gDone.contains("goal") ? "이 목표로 저장" : "목표까지 가는 길 보기", color: ok ? Theme.teal : Theme.muted) {
                    guard ok else { return }
                    focus = false
                    if m.gM <= 0 || m.route == .novice { m.gM = max(5, (need / 5).rounded() * 5) }
                    if fromSettings { dismiss(); return }
                    if m.gDone.contains("goal") { m.boardPath.removeLast(); return }   // 고치기: 미션 판으로 돌아간다
                    m.gDone.insert("goal"); m.boardPath.append(.g1r)
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { k = String(Int(m.gK)); y = String(m.gY); a = String(Int(m.gA)) }
        .scrollDismissesKeyboard(.interactively)
        .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료") { focus = false } } }
    }
}

struct GoalSetResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let need = m.neededMonthly(), base = m.route == .novice ? AppModel.mixes[0] : m.mineMix
        let path = m.simulate(base, monthly: need)
        Page(kicker: "미션 \(m.goalNo("goal")) 완료 · 새로 열림: 목표까지 경로", title: "\(m.gY)년 뒤 \(AppModel.wonK(m.gK))까지 가는 길") {
            VStack(alignment: .leading, spacing: 6) {
                Text("\(m.gY)년 뒤 \(AppModel.wonK(m.gK))을 모으려면").appFont(14).foregroundStyle(Color(hex: 0xC9D0D6))
                Text("보통의 경우 기준으로 매달 약").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Text(need > 0 ? AppModel.wonK(need) : "0원 (이미 충분해요)").appFont(30, .bold)
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            GuideBubble(pose: .back, text: m.route == .novice
                        ? "같이 봐요. 매달 \(AppModel.wonK(need))씩 넣으면 지날 길이에요. 점선은 내가 넣은 원금이에요."
                        : "같이 봐요. 지금 금액이 보통의 경우처럼 자란다고 보고, 모자란 만큼을 매달로 나눈 금액이에요.", note: "그래프 쪽을 돌아봄")
            Card {
                (Text("매달 \(AppModel.wonK(need))씩 넣으면 지날 길 ") + Text("(\(base.name) 기준)").foregroundColor(Theme.sub)).appFont(15, .bold)
                GoalPathChart(sims: [(path, Theme.teal, true)], goal: m.gK).frame(height: 150)
                HStack { Text("지금"); Spacer(); Text("\(String(format: "%g", Double(m.gY) / 2))년"); Spacer(); Text("\(m.gY)년") }.appFont(11).foregroundStyle(Theme.muted)
                FlowRow(spacing: 10) {
                    legend(Theme.teal, "보통의 경우"); legend(Theme.teal.opacity(0.2), "100번 중 90번")
                    legend(Theme.muted, "넣은 원금"); legend(Color(hex: 0xC8352E), "목표")
                }
                (Text("\(m.gY)년 안에 닿을 확률 ") + Text(AppModel.pct(path.prob)).bold() + Text(" · 보통 \(AppModel.eta(path.etaMonths))")).appFont(14)
            }
            BoardLinks(next: "다음 미션: 투자 구성 고르기", to: .g3)
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
                Text("어떤 구성으로 모을지 비교해 보세요").appFont(20, .bold)
                Text("지수와 업종 묶음으로만 · 매달 \(AppModel.wonK(m.gM)) · \(m.gY)년 기준").appFont(12).foregroundStyle(Theme.sub)
                GoalPathChart(sims: sims.map { ($0, mixColor[$0.mix.id] ?? Theme.teal, $0.mix.id == sel.mix.id) }, goal: m.gK, showPrincipal: false).frame(height: 100)
                FlowRow(spacing: 10) {
                    ForEach(sims, id: \.mix.id) { s in legend(mixColor[s.mix.id] ?? Theme.teal, s.mix.name) }
                    legend(Color(hex: 0xC8352E), "목표")
                }
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 3).fill(mixColor[sel.mix.id] ?? Theme.teal).frame(width: 10, height: 10)
                    Text(sel.mix.name).appFont(15, .bold)
                    Text(sel.mix.tag).appFont(12).foregroundStyle(Theme.sub)
                }
                compositionBar(sh.weights)
                HStack(spacing: 6) {
                    pill("기간 안 도달", AppModel.pct(sel.prob))
                    pill("보통 도착", AppModel.eta(sel.etaMonths))
                    pill("나쁜 해 한 번", AppModel.sgn0(sel.badYear))
                }
                ChipRow(items: m.goalMixes.map { ($0.id, $0.name) }, selection: $m.gMix, fill: true)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if m.route == .novice {
                    GuideBubble(pose: .think, text: "어떤 구성이 덜 흔들리는지 보세요. …… 처음이라면 단순한 구성부터 비교해 봐도 돼요.")
                } else {
                    GuideBubble(pose: .side, text: "구성을 하나씩 눌러 지금 내 구성과 비교해 보세요. 빨리 가는 구성일수록 더 흔들려요. 나쁜 해 한 번의 크기도 같이 보세요.")
                }
                if !sh.line.isEmpty { Text(sh.line).appFont(13).foregroundStyle(Theme.sub) }
                Text(sel.mix.desc).appFont(15).lineSpacing(3)
                BasketList(baskets: [.G, .B], open: $open, title: "구성에 쓰는 업종 묶음 · 눌러서 펼치기")
                Text("나쁜 해 한 번: 20년에 한 번쯤 오는 나쁜 해의 1년 변화. 구성별 연 기대수익률과 흔들림은 시안용 가정값이고, 확률 모형의 결과예요. 투자 권유가 아니에요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
                PrimaryButton(title: "\"\(sel.mix.name)\"으로 정하기") { m.gDone.insert("mix"); m.boardPath.append(.g3r) }
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
        Page(kicker: "미션 \(m.goalNo("mix")) 완료 · 새로 열림: 구성 비교", title: "내 계획: \(sel.mix.name)") {
            GuideBubble(pose: .cheer, text: m.route == .novice ? "정했어요. 매달 나눠 넣을 금액이에요. 실제 매매는 증권사 앱에서 직접 하셔야 해요." : "정했어요. 계획만 기록해요. 실제 매매는 증권사 앱에서 직접 하셔야 해요.")
            Text(sel.mix.desc).appFont(15).foregroundStyle(Theme.sub)
            Card {
                (Text("\(m.gY)년 안 도달 ") + Text(AppModel.pct(sel.prob)).bold() + Text(" · 보통 ") + Text(AppModel.eta(sel.etaMonths)).bold()).appFont(15)
            }
            if !sh.rows.isEmpty {
                Card {
                    Text(m.route == .novice ? "매달 나눠 넣기" : "옮길 금액").appFont(15, .bold)
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
            if let b = sel.mix.basket { BasketList(baskets: [b], open: $open, title: "담을 업종 · 눌러서 펼치기") }
            Text("계획만 기록해요. 매매는 증권사 앱에서 직접 하세요.").appFont(14).foregroundStyle(Theme.sub)
            BoardLinks(next: next.map { "다음 미션: \($0.title)" }, to: next?.to)
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
        let drnk = m.focusRow, gainRate = drnk.map { 1 - $0.h.avg / $0.sym.last } ?? 0
        let gainSale = sale * gainRate, G0 = m.taxGain
        let tx = { (x: Double) in max(0, x - 250) * 0.22 }
        let tOne = tx(G0 + gainSale), tSplit = tx(G0 + gainSale / 2) + tx(gainSale / 2), tNo = tx(G0)
        let rows: [(String, String, String, Double)] = [
            ("one", "올해 한 번에 팔기", "이익 \(AppModel.wonK(max(0, G0 + gainSale))) − 공제 250만원", tOne),
            ("split", "12월·1월에 반씩 나눠 팔기", "두 해에 공제 250만원씩 두 번", tSplit),
            ("none", "팔지 않고 적립으로만 맞추기", "대신 오래 걸림", tNo),
        ]
        let pick = rows.first { $0.0 == m.goalTaxPick } ?? rows[1]
        Page(kicker: "미션 \(m.goalNo("tax")) / \(m.goalTotal)", title: "비중을 맞추려 팔면 세금이 얼마나 나올까요?") {
            GuideBubble(pose: .point, text: "비중을 맞추려고 팔면 이익에 세금이 붙어요. 한 해에 몰아 팔지 않고 나눠 팔면 덜 내요. 계산 예시예요.")
            Text("세금 규칙: 대한민국 거주자").appFont(13, .semibold).foregroundStyle(Theme.teal)
            inputField("올해 이미 판 이익 (만원)", $gain).onChange(of: gain) { _, v in m.taxGain = max(0, Double(v) ?? 0) }
            if sh.sell > 0 && gainSale > 0 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("내년 5월에 낼 세금 · \(pick.1)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        if pick.0 != "one" { Text(AppModel.wonK(tOne)).appFont(18).strikethrough().foregroundStyle(Theme.muted) }
                        Text(AppModel.wonK(pick.3)).appFont(30, .bold)
                        Text(pick.0 == "one" ? "가장 많이 내는 방법" : "\(AppModel.wonK(max(0, tOne - pick.3))) 줄어요").appFont(13, .bold).foregroundStyle(Theme.mint)
                    }
                    Text(pick.2).appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
                ChipRow(items: [("one", "한 번에"), ("split", "나눠 팔기"), ("none", "적립만")], selection: $m.goalTaxPick, fill: true)
                Text("\"\(m.selectedMix.name)\"으로 맞추면 \(m.focusName) \(AppModel.wonK(sale))를 팔고, 그중 이익이 \(AppModel.wonK(gainSale))예요. 나눠 팔면 \(AppModel.wonK(max(0, tOne - tSplit))) 덜 내요.")
                    .appFont(14).foregroundStyle(Theme.sub).lineSpacing(2)
            } else {
                Text(sh.sell > 0 ? "파는 부분이 손실이라 낼 세금이 없어요. 올해 판 다른 이익과 상계돼요. (\(m.focusName) 평균 단가가 지금 가격보다 높아요)"
                     : "지금 고른 구성(\"\(m.selectedMix.name)\")은 팔 게 없어요. 미션 \(m.goalNo("mix"))에서 다른 구성을 고르면 세금을 비교할 수 있어요.")
                    .appFont(15).padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
            Text("결제일 기준으로 그해 손익에 들어가요. 계산 예시이며 세무 상담이 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
            PrimaryButton(title: "확인했어요") { m.gDone.insert("tax"); m.boardPath = [] }
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
                    HStack { Text("INTERMISSION · 3 MONTHS").appFont(12, .bold).tracking(1).foregroundStyle(Theme.mint); Spacer(); Text("\(fixed.count)/3").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                    Text("매달 1분, 블록 하나씩 채워요").appFont(20, .bold)
                    HStack(spacing: 6) {
                        ForEach(0..<3, id: \.self) { j in
                            let p: GoalWeek? = j < fixed.count ? fixed[j] : (j == fixed.count ? m.gWeekCur : nil)
                            VStack(spacing: 2) {
                                Text("\(j + 1)달" + (j == fixed.count ? " · 이번 달" : "")).appFont(12)
                                Text(p?.label ?? (j == fixed.count ? "고르는 중" : "")).appFont(12, .bold)
                            }
                            .frame(maxWidth: .infinity, minHeight: 64)
                            .foregroundStyle(p != nil ? .white : j == fixed.count ? Theme.yellow : Theme.muted)
                            .background(p?.color ?? .clear, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(j == fixed.count ? Theme.yellow : (p?.color ?? Theme.slate), style: StrokeStyle(lineWidth: 2, dash: p == nil ? [5, 4] : [])))
                        }
                    }
                    HStack { Text("목표까지"); Spacer(); Text("\(AppModel.wonK(now)) / \(AppModel.wonK(m.gK))") }.appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    ProgressBar(value: now / max(1, m.gK))
                    if let last {
                        Text((last == .more ? "더 넣었어요! " : last == .less ? "이번 달은 덜 넣었어요. " : "계획대로 넣었어요. ")
                             + (eta.isFinite ? "이 속도면 \(AppModel.eta(eta)) 도착 (" + (eta < plan ? "\(Int(plan - eta))개월 빠름" : eta > plan ? "\(Int(eta - plan))개월 늦음" : "목표 시점") + ")" : ""))
                            .appFont(14, .semibold).foregroundStyle(Theme.ink)
                            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(last == .less ? Color(hex: 0xFDF0E1, dark: 0x3A2A17) : last == .more ? Color(hex: 0xE4F0FB, dark: 0x16283A) : Color(hex: 0xE3F4EC, dark: 0x163226), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .foregroundStyle(.white).padding(16)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))

                GuideBubble(pose: .stand, text: "3개월 동안 매달 1분이면 돼요. 이번 달 할 일을 하고 나서 표시해 주세요. 체크인할 때마다 캐릭터 위젯이 하나씩 열려요.")

                if fixed.count < 3 {
                    Card {
                        Text("\(fixed.count + 1)달째 할 일").appFont(13, .bold).foregroundStyle(Theme.teal)
                        HStack(spacing: 8) {
                            Text(step.whereText).appFont(11, .bold).padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                            Text(step.task).appFont(15, .bold)
                        }
                        Text("하고 나서 표시해 주세요").appFont(13).foregroundStyle(Theme.sub)
                        HStack(spacing: 6) {
                            ForEach(GoalWeek.allCases, id: \.self) { p in
                                let on = m.gWeekCur == p
                                Button { m.gWeekCur = p } label: {
                                    VStack(spacing: 2) {
                                        Text(p.label).appFont(15, .bold)
                                        Text("월 " + AppModel.wonK(m.gM * p.factor)).appFont(11).foregroundStyle(Theme.sub)
                                    }
                                    .foregroundStyle(Theme.ink).padding(.vertical, 8)
                                    .frame(maxWidth: .infinity, minHeight: 64)
                                    .background(on ? p.color.opacity(0.15) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.color, lineWidth: on ? 3 : 2))
                                }.buttonStyle(.plain)
                            }
                        }
                        Text("이번 달이 끝나기 전까지는 다시 고를 수 있어요. 지난 달은 바꿀 수 없어요.").appFont(12).foregroundStyle(Theme.muted)
                        if m.gWeekCur != nil {
                            Button(fixed.count == 2 ? "3달째 마치기 (시안)" : "다음 달로 넘기기 (시안)") { withAnimation { m.confirmGoalMonth() } }
                                .appFont(14, .semibold).foregroundStyle(Theme.ink).frame(maxWidth: .infinity, minHeight: 44)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                        }
                    }
                }
                Card {
                    Text("체크인하면 받는 캐릭터 위젯").appFont(15, .bold)
                    Text("중간 크기 오늘의 움직임 위젯에 1달째 세리, 2달째 시오가 들어오고, 3달째에는 모두 열려요.").appFont(13).foregroundStyle(Theme.sub)
                    ForEach(0..<3, id: \.self) { j in
                        let on = j < fixed.count
                        HStack(spacing: 12) {
                            if j < 2 {
                                Pixel(name: (on ? "spr_" : "sil_") + ["seri", "sio"][j], width: 42, height: 60)
                            } else {
                                HStack(spacing: 0) { ForEach(Shelter.friends) { f in Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 18, height: 26) } }
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack { Text("오늘의 움직임").appFont(11, .bold); Spacer(); Text(on ? "받음" : j == fixed.count ? "이번 달" : "\(j + 1)달 뒤").appFont(11).foregroundStyle(Theme.sub) }
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
                    PrimaryButton(title: m.interDone ? "미션 판으로" : "인터미션 끝") { m.gDone.insert("inter"); m.boardPath = [] }
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}
