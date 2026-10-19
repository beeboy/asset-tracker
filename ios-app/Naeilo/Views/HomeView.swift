import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var m
    @State private var inbox = false
    @AppStorage("hideAmt") private var hideAmt = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AppHeader(tab: "홈").padding(.horizontal, -16)
                SampleNote()
                summary
                // 회복 루트: 매달 더 넣으면 본전이 언제인지 (원인 진단·내 계획을 마친 뒤)
                if !m.isGoal, m.done.contains(3), m.cost > m.total, !m.rows.isEmpty { DepositLeverCard(plan: m.keepPlan) }
                shelterBox
                ParallelHomeCard()
                reminder
                if m.playUnlocked { RoutineCard() } else { lockedRoutine }
                if !m.playUnlocked, !m.rows.isEmpty { yesterdayCard }
                Text(Market.shared.asOfText).appFont(13).foregroundStyle(Theme.sub2)
            }
            .screen()
        }
        .background(Theme.bg)
        .refreshable { await Market.shared.pullRefresh(m) }
        .onAppear { m.calmOpen(); Remind.shared.refresh(m); Remind.shared.nextBubble() }
        .sheet(isPresented: $inbox, onDismiss: { Remind.shared.markAllSeen() }) { RemindSheet().environment(m) }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: String.self) { route in
            if route == "shelter" { ShelterView() }
            else if route == "library" { ShelterView(onlyLibrary: true) }
            else if route == "parallel" { ParallelView() }
            else if route == "parallel:basket" { ParallelBasketView() }
            else if route == "goal" { GoalSetView(fromSettings: true) }
            else if route.hasPrefix("read:"), let i = Int(route.dropFirst(5)) { ReaderView(index: i) }
            else if route.hasPrefix("vol1:"), let i = Int(route.dropFirst(5)) { ReaderView(index: i, book: .vol1) }
            else if route == "me" { MeEditView() }
            else if route.hasPrefix("char:"), let f = Shelter.friends.first(where: { $0.id == route.dropFirst(5).split(separator: ":").first.map(String.init) }) {
                // char:<인물> = 소설 속 갈래로, char:<인물>:invest = 투자하는 갈래로, …:invest:h<번호> = 그 버릇을 펴서
                let parts = route.split(separator: ":")
                CharacterDetailView(friend: f, invest: parts.contains("invest"),
                                    focus: parts.last.flatMap { $0.hasPrefix("h") ? Int($0.dropFirst()) : nil })
            }
        }
    }

    // 어제 종가 기준 평가액 + 본전 진행 막대
    @ViewBuilder private var summary: some View {
        if m.isGoal { goalSummary } else { recoverSummary }
    }

    // 목표 루트: 지금 평가액(플러스) 또는 모은 돈(시작 전)과 목표 진행
    private var goalSummary: some View {
        let now = m.route == .novice ? m.goalSaved : m.total / 1e4, value = m.route == .plus || m.noviceOwn, prog = min(1, now / max(1, m.gK))
        return VStack(alignment: .leading, spacing: 10) {
            Text(value ? "평가액" : "모은 돈").foregroundStyle(Color(hex: 0xC9D0D6))
                .appFont(13)
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                // 금액은 길어도 꺾지 않고 한 줄로, 자리가 모자라면 글자를 줄인다
                // 누르면 금액 가리기(다시 누르면 보이기)
                Text(hideAmt ? "••••••" : AppModel.man(now * 1e4) + "원").appFont(32, .bold).tracking(-0.5).roll(now)
                    .lineLimit(1).minimumScaleFactor(0.4).layoutPriority(1)
                    .contentShape(Rectangle()).onTapGesture { hideAmt.toggle() }
                if value && m.cost > 0 {
                    Text(AppModel.sgn(m.ret)).appFont(14, .bold).foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
                        .lineLimit(1).minimumScaleFactor(0.6)
                }
            }
            if value && m.route == .plus && m.total > 0 { todayLine }
            HStack {
                Text("목표 \(AppModel.wonK(m.gK)) · \(m.gY)년"); Spacer()
                Text(AppModel.pct(prog)).fontWeight(.bold).foregroundStyle(Theme.yellow)
            }
            .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            ProgressBar(value: prog)
            HStack {
                Text(m.goalReached ? "목표 \(AppModel.wonK(m.gK)) 도달!"
                     : m.route == .novice ? "매달 \(AppModel.wonK(m.gM)) 적립 · 다음 적립일 +\(AppModel.wonK(m.gM))"
                     : AppModel.paceText(m.goalPace()) + (m.gM > 0 ? " · 다음달 \(AppModel.wonK(m.gM)) 적립" : " · 추가 적립 미정"))
                    .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
                if !m.goalReached && m.route != .novice && m.gM <= 0 {
                    Spacer(minLength: 8)
                    NavigationLink(value: "goal") { Text("정하기 ›").appFont(13, .bold).foregroundStyle(Theme.yellow).frame(minHeight: 32) }
                        .buttonStyle(.plain).accessibilityLabel("다음달 적립 정하기")
                }
            }
            if m.goalReached { darkButton("다음 목표 정하기") { m.suggestNextGoal(); m.homePath.append("goal") } }
        }
        .foregroundStyle(.white)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    private var recoverSummary: some View {
        let need = m.cost / max(1, m.total) - 1
        let needY = m.cost / (m.total / (1 + m.todayMove)) - 1
        return VStack(alignment: .leading, spacing: 10) {
            Text("평가액 · 넣은 돈 \(AppModel.man(m.cost))").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(AppModel.man(m.total)).appFont(32, .bold).tracking(-0.5).roll(m.total)
                    .lineLimit(1).minimumScaleFactor(0.4).layoutPriority(1)
                Text(AppModel.sgn(m.ret)).appFont(16, .bold).lineLimit(1).fixedSize()
                    .foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
            }
            if m.total > 0 { todayLine }
            HStack {
                Text("본전까지")
                Spacer()
                Text(need > 0 ? "+" + String(format: "%.1f", need * 100) + "% 남음" : "본전 도달")
                    .fontWeight(.bold).foregroundStyle(Theme.yellow)
            }
            .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            ProgressBar(value: m.total / max(1, m.cost))
            Text(need > 0
                 ? "어제 \(String(format: "%.1f", needY * 100))% → 오늘 \(String(format: "%.1f", need * 100))%"
                 : "본전 넘음 · 종목·친구 그대로")
                .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
            if need <= 0 { darkButton("목표 루트로") { m.graduateToGoal(); m.tab = .board } }
        }
        .foregroundStyle(.white)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    // 오늘 등락: 부호는 한 번만 (▲/▼ 뒤 퍼센트·금액은 부호 없이). 목표·회복 카드 공통
    private var todayLine: some View {
        let mv = m.todayMove, diff = m.stockTotal - m.stockTotal / (1 + mv)
        return Text("오늘 " + (mv >= 0 ? "▲ " : "▼ ") + String(format: "%.1f%%", abs(mv) * 100) + " · " + AppModel.won(abs(diff)))
            .appFont(13).foregroundStyle(mv >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
    }

    // 어두운 요약 카드 안의 노란 버튼 (목표 루트로 · 다음 목표)
    private func darkButton(_ t: String, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Text(t + " ›").appFont(15, .bold).foregroundStyle(Theme.inkFixed)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }

    // 쉼터 박스: 홈에 둔 친구 + 말풍선 (리마인드 소식이 있으면 그 소식) + 새 소식 + 다른 친구 칸
    private var shelterBox: some View {
        let on = (0..<5).filter { m.friendOn($0) }.count
        let next = (0..<5).first { !m.friendOn($0) }
        let r = Remind.shared, b = r.bubble, n = r.fresh.count
        let line: String = b.map { $0.text + ($0.go != .nowhere ? " ›" : "") } ?? m.homeSay
        // 정보 문장(오늘 움직인 종목)을 먼저, 대사는 그 아래
        let say = m.todayMoversText.map { $0 + "\n" + m.friendName(m.homeFriendShown) + ": “" + line + "”" } ?? line
        let tint: Color = b.map { $0.cat.color.opacity(0.14) } ?? Theme.mintBg
        return VStack(alignment: .leading, spacing: 10) {
            Button {
                if let b, b.go != .nowhere { m.go(b.go) } else { m.homePath.append("shelter") }
            } label: {
                HStack(alignment: .bottom, spacing: 10) {
                    CharPixel(id: m.homeFriendShown, width: 56, height: 80).charIdle()
                        .accessibilityLabel(m.friendName(m.homeFriendShown))
                    Text(say)
                        .appFont(14).lineSpacing(3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(tint, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(b.map { "\($0.cat.label) 소식: \($0.text)" } ?? "쉼터 보기")
            if !r.items.isEmpty {
                HStack {
                    Spacer()
                    Button { inbox = true } label: {
                        Text(n > 0 ? "새 소식 \(n) ›" : "소식함 ›").appFont(12, .bold)
                            .foregroundStyle(n > 0 ? Theme.inkFixed : Theme.sub)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(n > 0 ? Theme.yellow : Theme.line, in: Capsule())
                            .frame(minHeight: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(n > 0 ? "새 소식 \(n)개 보기" : "소식함 보기")
                }
                .padding(.top, -4)
            }
            Divider().overlay(Theme.line)
            NavigationLink(value: "shelter") {
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach(Array(Shelter.friends.enumerated()).filter { $0.element.id != m.homeFriendShown }, id: \.element.id) { i, f in
                        Pixel(name: (m.friendOn(i) ? "spr_" : "sil_") + f.id, width: 20, height: 28).opacity(m.friendOn(i) ? 1 : 0.5)
                    }
                    Spacer()
                    Text("친구 \(on)/5" + (next.map { " · 다음 \(Shelter.friends[$0].name) \(m.friendWhen($0))" } ?? " · 외전 5장") + " ›")
                        .appFont(12).foregroundStyle(Theme.sub).lineLimit(2).minimumScaleFactor(0.8).multilineTextAlignment(.trailing)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("쉼터 보기")
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    /// 노란 띠: 3일 안의 일정 (내 종목 먼저). 없으면 인터미션 이번 할 일. 누르면 소식함
    @ViewBuilder private var reminder: some View {
        let r = Remind.shared
        if let e = r.strip {
            stripRow(tag: "일정", text: "\(MarketEvents.when(e.day ?? "")) \(e.text) · \(e.sub ?? "")")
        } else if r.isOn(.doing), let d = r.doing.first(where: { $0.id == "do.inter" }) {
            stripRow(tag: "리마인드", text: d.text)
        }
    }
    private func stripRow(tag: String, text: String) -> some View {
        Button { inbox = true } label: {
            HStack(spacing: 8) {
                Text(tag).appFont(11, .bold).foregroundStyle(Theme.inkFixed).padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Theme.yellow, in: Capsule())
                Text(text).appFont(13).lineLimit(2).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").appFont(11).foregroundStyle(Theme.muted)
            }
            .padding(.horizontal, 12).frame(minHeight: 44)
            .background(Theme.cream, in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tag): \(text). 소식함 열기")
    }

    private var lockedRoutine: some View {
        DashedCard {
            Label("오늘의 1분", systemImage: "lock").appFont(15, .bold)
            Text("시작 3단계 뒤 열림 · 숫자 하나, 질문 하나").appFont(13)
        }
    }

    private var yesterdayCard: some View {
        Card {
            HStack(alignment: .firstTextBaseline) {
                Text("오늘의 움직임").appFont(15, .bold)
                Spacer()
                Text(AppModel.sgn(m.todayMove)).appFont(20, .bold).foregroundStyle(Theme.teal).roll(AppModel.sgn(m.todayMove))
            }
            Text(m.rows.map { "\($0.sym.short) \(AppModel.sgn($0.sym.quote.change))" }.joined(separator: " · ") + " · 원/달러 \(AppModel.sgn(Market.shared.fx.change))").appFont(13).foregroundStyle(Theme.sub)
        }
    }
}

// 오늘의 1분: 내 종목 오늘 숫자 확인 + 날마다 바뀌는 질문 하나 (지난 7일과 안 겹치게). 설명은 계산값으로.
struct RoutineCard: View {
    @Environment(AppModel.self) private var m
    private let days = ["월", "화", "수", "목", "금", "토", "일"]
    /// 이번 주 칸 중 채운 날 수 (요일 7칸과 같은 기준)
    private var weekDone: Int {
        (0..<7).filter { i in let d = m.day - m.day % 7 + i; return m.dayLog[d].map { $0.answer != nil } ?? (d < 5 && d < m.day) }.count
    }

    var body: some View {
        let t = m.today, q = m.question
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("오늘의 1분").appFont(13, .bold).tracking(1).foregroundStyle(Theme.mint)
                Spacer()
                Text("이번 주 \(weekDone)일").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            HStack(spacing: 4) {
                ForEach(0..<7, id: \.self) { i in
                    let d = m.day - m.day % 7 + i
                    let ok = m.dayLog[d].map { $0.answer != nil } ?? (d < 5 && d < m.day)
                    Text(days[i]).appFont(12, .bold)
                        .frame(maxWidth: .infinity, minHeight: 28)
                        .foregroundStyle(ok ? Theme.inkFixed : d == m.day ? Theme.yellow : Theme.muted)
                        .background(ok ? Theme.mint : d == m.day ? .clear : Theme.slate, in: RoundedRectangle(cornerRadius: 8))
                        .overlay { if d == m.day && !ok { RoundedRectangle(cornerRadius: 8).stroke(Theme.yellow, lineWidth: 2) } }
                }
            }
            step(tag: "1 · 오늘 숫자") {
                Text((try? AttributedString(markdown: m.routineFact, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(m.routineFact)).appFont(15).lineSpacing(3)
                if !t.seen {
                    Button("확인했어요") { withAnimation { m.markSeen() } }
                        .appFont(14, .bold).foregroundStyle(.white)
                        .padding(.horizontal, 16).frame(minHeight: 40)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                }
            }
            if t.seen {
                step(tag: "2 · \(q.tag)") {
                    Text(q.q).appFont(15, .semibold).lineSpacing(3)
                    FlowRow(spacing: 6) {
                        ForEach(q.opts.indices, id: \.self) { i in
                            let ans = t.answer
                            let right = q.right >= 0 && i == q.right
                            Button(q.opts[i]) { withAnimation { m.answer(i) } }
                                .disabled(ans != nil)
                                .appFont(14, .bold)
                                .padding(.horizontal, 14).frame(minHeight: 40)
                                .foregroundStyle(ans == nil ? Theme.ink : right ? Theme.tealDark : i == ans ? .white : Theme.muted)
                                .background(ans == nil ? Theme.card : right ? Theme.tealBg : i == ans ? Theme.night : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(ans == nil ? Theme.dash : right ? Theme.teal : i == ans ? Theme.ink : Theme.track, lineWidth: 2))
                        }
                    }
                    if let a = t.answer {
                        Text((t.ok.map { $0 ? "맞아요! " : "아쉬워요. " } ?? "") + q.fb(a))
                            .appFont(13).lineSpacing(3)
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            if t.answer != nil {
                let sc = m.quizScore
                HStack(alignment: .firstTextBaseline) {
                    Text(m.routineDoneText).appFont(14, .semibold).foregroundStyle(Theme.yellow)
                    Spacer(minLength: 8)
                    if sc.all > 0 { Text("퀴즈 \(sc.right)/\(sc.all)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                }
            }
            // 날짜 넘기기는 개발자 확인용 (일반 사용자 화면에는 안 보인다)
            if m.devAll {
                Button("다음 날로 넘기기 (개발자)") { withAnimation { m.day += 1 } }
                    .appFont(13).underline().foregroundStyle(Theme.muted).frame(minHeight: 40)
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    private func step<C: View>(tag: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(tag).appFont(12, .bold).foregroundStyle(Theme.teal)
            c()
        }
        .foregroundStyle(Theme.ink)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
    }
}

// 지금 평가액 카드 오른쪽 작은 그래프: 내 길 '시작부터' (실제 = 민트 선, 내 길 = 보라 점선). 누르면 분석 > 내 길
struct MiniPath: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        // 분석 > 내 길 '지금까지' 그래프와 같은 점·같은 세로 범위 (목표 금액 포함), 눈금·범위 띠만 뺀다
        let p = m.myPath, x1 = p.start + 1.0 / 12, gap = m.myPathGapToday, K = m.gK * 1e4
        let act = p.pts
        let need: [(Double, Double)] = (0...30).map { i in let t = x1 * Double(i) / 30; return (t, p.need(t)) }
        let needAll = (0...30).map { i in p.need(p.end * Double(i) / 30) }
        let vals = act.map(\.1) + needAll + [K]
        let hi = (vals.max() ?? 1) * 1.05, lo = (vals.filter { $0 > 0 }.min() ?? 0) * 0.9
        Button {
            m.tab = .analysis
            m.analysisPath = NavigationPath([AnalysisRoute.myPath])
        } label: {
            Canvas { ctx, size in
                let pt = { (q: (Double, Double)) -> CGPoint in
                    CGPoint(x: CGFloat(q.0 / max(1e-6, x1)) * size.width, y: CGFloat((hi - q.1) / max(1, hi - lo)) * size.height)
                }
                func line(_ qs: [(Double, Double)]) -> Path {
                    var path = Path()
                    for (i, q) in qs.enumerated() { i == 0 ? path.move(to: pt(q)) : path.addLine(to: pt(q)) }
                    return path
                }
                ctx.stroke(line(need), with: .color(Color(hex: 0xB79CF0)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                ctx.stroke(line(act), with: .color(Theme.mint), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                if let last = act.last {
                    let end = pt((p.start, last.1))
                    ctx.fill(Path(ellipseIn: CGRect(x: end.x - 3, y: end.y - 3, width: 6, height: 6)), with: .color(Theme.mint))
                }
            }
            .frame(width: 84, height: 40)
            .pulse(Theme.mint, dot: 6) { s in act.last.map { CGPoint(x: CGFloat(p.start / max(1e-6, x1)) * s.width, y: CGFloat((hi - $0.1) / max(1, hi - lo)) * s.height) } }
            .overlay(alignment: .bottom) {
                Text(gap >= 0 ? "계획대로" : "조금 늦어요").appFont(10, .bold)
                    .foregroundStyle(gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060))
                    .fixedSize().offset(y: 16)
            }
            .padding(.bottom, 15)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("내 길 그래프, \(gap >= 0 ? "계획대로" : "계획보다 조금 늦어요"), 눌러서 분석의 내 길 보기")
    }
}

// 줄바꿈되는 가로 배치
struct FlowRow: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > w { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing; rowH = max(rowH, sz.height); maxX = max(maxX, x - spacing)
        }
        return CGSize(width: min(w, maxX), height: y + rowH)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > b.minX && x + sz.width > b.maxX { x = b.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
    }
}

/// 샘플 종목 안내: 새 사용자는 가상 종목 DRNK·QQQ 샘플로 시작한다. 내 종목을 넣기 전까지 탭마다 보인다
struct SampleNote: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        if m.isSample {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("샘플").appFont(12, .bold).foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.orange, in: Capsule())
                Text("지금 숫자는 샘플(가상 종목 DRNK·QQQ)이에요. 종목 탭에서 내 종목을 넣으면 바뀌어요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.orange.opacity(0.6)))
        }
    }
}
