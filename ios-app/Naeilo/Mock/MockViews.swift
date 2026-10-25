#if MOCK
import SwiftUI

// 모의투자 네 화면의 공통 부품 · 관찰 · 선정.
// 개발자 전용 화면이라 글은 번역 목록(Localizable)에 넣지 않는다: 모든 글은 mt(…) = Text(verbatim:)

func mt(_ s: String) -> Text { Text(verbatim: s) }

enum MockTab: String, CaseIterable, Identifiable {
    case observe, select, order, analysis
    var id: String { rawValue }
    var name: String {
        switch self {
        case .observe: return "관찰"
        case .select: return "선정"
        case .order: return "주문"
        case .analysis: return "분석"
        }
    }
    var icon: String {
        switch self {
        case .observe: return "binoculars"
        case .select: return "checklist"
        case .order: return "arrow.left.arrow.right"
        case .analysis: return "chart.xyaxis.line"
        }
    }
}

// MARK: 공통 요약 박스 (네 탭 위. 고른 평가액 박스 모양의 바탕·색, 밝기 모드)

struct MockSummaryBox: View {
    let tab: MockTab
    @AppStorage(BoxLook.key) private var lookRaw = BoxLook.jump.rawValue

    var body: some View {
        let d = MockDesk.shared
        let b = d.s.shown
        let l = d.ledger(b), eq = d.equity(b)
        let ret = eq / l.startCash - 1
        let retGross = (eq + l.fees + l.taxes) / l.startCash - 1
        let st = MockCalc.stat(d.events, ledger: l.id)
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                ForEach(MockBook.allCases, id: \.self) { k in
                    Button { d.setView { $0.shown = k } } label: {
                        mt(k == .rule ? "규칙 장부" : "AI 장부").appFont(12, k == b ? .bold : .regular)
                            .foregroundStyle(k == b ? BoxInk.ink : BoxInk.dim)
                            .padding(.horizontal, 9).padding(.vertical, 4)
                            .background(k == b ? BoxInk.card.opacity(0.7) : .clear, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                MockPill()
            }
            mt(MockFmt.won(eq)).font(.system(size: 30, weight: .heavy)).tracking(-0.5).foregroundStyle(BoxInk.ink)
                .lineLimit(1).minimumScaleFactor(0.5)
            (mt(MockFmt.pct(ret)).foregroundColor(ret >= 0 ? BoxInk.up : BoxInk.down)
             + mt(" 수수료·세금 뒤 · 전 " + MockFmt.pct(retGross)).foregroundColor(BoxInk.dim))
                .appFont(13).lineLimit(1).minimumScaleFactor(0.7)
            HStack(spacing: 14) {
                cell("현금", MockFmt.won(l.cash))
                cell("보유", "\(l.positions.count)종목")
                cell("매매", "\(st.trades)건" + (l.pending.isEmpty ? "" : " · 대기 \(l.pending.count)"))
            }
            mt(line(d)).appFont(12).foregroundStyle(BoxInk.sub).lineLimit(2).fixedSize(horizontal: false, vertical: true)
            if let w = d.s.aiWallet {
                mt("AI 쓴 돈 오늘 $\(String(format: "%.2f", w.day)) / $\(MockFmt.num(d.s.ai.day)) · 이번 달 $\(String(format: "%.2f", w.month)) / $\(MockFmt.num(d.s.ai.month))" + (w.key ? "" : " · 서버에 키 없음"))
                    .appFont(11).foregroundStyle(BoxInk.dim).lineLimit(1).minimumScaleFactor(0.7)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background { MoodBackdrop(look: BoxLook(rawValue: lookRaw) ?? .jump) }
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .boxScheme()
    }

    private func cell(_ k: String, _ v: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            mt(k).appFont(11).foregroundStyle(BoxInk.dim)
            mt(v).appFont(13, .semibold).foregroundStyle(BoxInk.ink).lineLimit(1).minimumScaleFactor(0.7)
        }
    }

    /// 탭마다 한 줄: 그 탭의 마지막 실행 상태
    private func line(_ d: MockDesk) -> String {
        switch tab {
        case .observe:
            guard let sc = d.s.scan else { return d.s.runs["observe"]?.text ?? "아직 훑지 않았어요 · 지금 훑기를 눌러 보세요" }
            return (d.s.runs["observe"]?.text ?? "") + " · 걸린 \(sc.hits.count)"
        case .select: return d.s.runs["select"]?.text ?? "아직 고르지 않았어요"
        case .order: return d.s.runs["order"]?.text ?? "아직 주문하지 않았어요"
        case .analysis: return d.s.runs["ai"]?.text ?? "AI 는 아직 부르지 않았어요"
        }
    }
}

/// 지금 상태: 도는 중 · 장중 · 장 밖(시험) · 장 확인 못 함
struct MockPill: View {
    var body: some View {
        let d = MockDesk.shared
        let (t, c): (String, Color) = {
            if let b = d.busy { return (MockDesk.busyName(b) + " 중", BoxInk.gold) }
            if let a = d.aiBusy { return ("AI " + MockDesk.aiName(a), BoxInk.gold) }
            switch d.phase {
            case .open: return ("장중", BoxInk.good)
            case .closed: return ("장 밖 · 시험", BoxInk.dim)
            case .unknown: return ("장 확인 못 함", BoxInk.up)
            }
        }()
        HStack(spacing: 4) {
            if d.busy != nil || d.aiBusy != nil { ProgressView().controlSize(.mini) }
            mt(t).appFont(11, .semibold)
        }
        .foregroundStyle(c)
        .padding(.horizontal, 8).padding(.vertical, 3)
        .overlay(Capsule().stroke(c.opacity(0.6)))
    }
}

extension MockDesk {
    static func busyName(_ b: String) -> String {
        switch b {
        case "observe": return "훑는"
        case "select": return "고르는"
        case "order": return "주문"
        case "cycle": return "한 바퀴"
        case "hand": return "손 주문"
        case "tick": return "확인"
        default: return b
        }
    }
}

// MARK: 공통 부품

/// 지금 돌리기 단추 (다른 일이 돌고 있으면 막힌다)
struct MockRunButton: View {
    let title: String
    var ai = false
    let action: () async -> Void
    var body: some View {
        let d = MockDesk.shared
        let blocked = ai ? d.aiBusy != nil : d.busy != nil
        Button { Task { await action() } } label: {
            mt(title).appFont(15, .bold).frame(maxWidth: .infinity).frame(minHeight: 44)
                .foregroundStyle(.white)
                .background(blocked ? Theme.muted : Theme.teal, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .disabled(blocked)
    }
}

/// 작은 테두리 단추
struct MockSmallButton: View {
    let title: String
    var color: Color = Theme.teal
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            mt(title).appFont(13, .semibold).foregroundStyle(color)
                .padding(.horizontal, 10).frame(minHeight: 32)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(color.opacity(0.5)))
        }
        .buttonStyle(.plain)
    }
}

/// 상태 한 줄 (성공은 회색, 실패는 빨강)
struct MockRunLine: View {
    let run: MockRun?
    var empty = "아직 돌리지 않았어요"
    var body: some View {
        mt(run?.text ?? empty).appFont(12).foregroundStyle(run?.ok == false ? Theme.up : Theme.muted)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// 누가 냈나 · 시험 표시
struct MockTag: View {
    let text: String
    var color: Color = Theme.sub
    var body: some View {
        mt(text).appFont(11, .semibold).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 5))
    }
    static func who(_ w: MockWho) -> MockTag {
        MockTag(text: w.name, color: w == .rule ? Theme.rule : w == .hand ? Theme.orange : Theme.blue)
    }
}

struct MockSection<Content: View>: View {
    let title: String
    var trailing: String? = nil
    @ViewBuilder var content: Content
    var body: some View {
        Card {
            HStack {
                mt(title).appFont(16, .bold)
                Spacer()
                if let trailing { mt(trailing).appFont(12).foregroundStyle(Theme.muted) }
            }
            content
        }
    }
}

/// 탭 화면 틀: 머리 · 요약 박스 · 본문
struct MockScreen<Content: View>: View {
    let tab: MockTab
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AppHeader(tab: "모의 " + tab.name).padding(.horizontal, -16)
                MockSummaryBox(tab: tab)
                content
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .task { await MockDesk.shared.refreshPhase() }
    }
}

// MARK: 관찰

struct MockObserveView: View {
    @State private var editing: MockBundle?
    @State private var words = ""
    @State private var wordsNote: String?

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .observe) {
            bundleChips(d)
            if let b = d.bundle { bundleCard(d, b) }
            wordsCard(d)
            if let sc = d.s.scan { results(sc) }
        }
        .sheet(item: $editing) { b in
            MockBundleEditor(start: b, title: d.s.bundles.contains { $0.id == b.id } ? "조건 묶음 고치기" : "새 조건 묶음",
                             canDelete: d.s.bundles.count > 1 && d.s.bundles.contains { $0.id == b.id }) { out in
                if let out {
                    d.edit("조건 묶음 '\(out.name)' 저장 · " + out.pieces.map(\.text).joined(separator: " · ")) { st in
                        if let i = st.bundles.firstIndex(where: { $0.id == out.id }) { st.bundles[i] = out } else { st.bundles.append(out) }
                        st.current = out.id
                    }
                } else {
                    d.edit("조건 묶음 '\(b.name)' 지움") { st in
                        st.bundles.removeAll { $0.id == b.id }
                        if st.current == b.id { st.current = st.bundles.first?.id }
                    }
                }
            }
        }
    }

    private func bundleChips(_ d: MockDesk) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(d.s.bundles) { b in
                    let on = b.id == d.bundle?.id
                    Button { d.setView { $0.current = b.id } } label: {
                        mt(b.name).appFont(13, on ? .bold : .regular).foregroundStyle(on ? .white : Theme.ink)
                            .padding(.horizontal, 12).frame(minHeight: 34)
                            .background(on ? Theme.teal : Theme.card, in: Capsule())
                            .overlay(Capsule().stroke(on ? .clear : Theme.border))
                    }
                    .buttonStyle(.plain)
                }
                Button { editing = MockBundle(name: "새 묶음", pieces: [MockPiece(kind: .amount, lo: 10)]) } label: {
                    mt("+ 새 묶음").appFont(13, .semibold).foregroundStyle(Theme.teal)
                        .padding(.horizontal, 12).frame(minHeight: 34)
                        .overlay(Capsule().stroke(Theme.teal.opacity(0.5)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func bundleCard(_ d: MockDesk, _ b: MockBundle) -> some View {
        MockSection(title: b.name, trailing: b.scope.name + " · \(MockDesk.universe.count)종목") {
            ForEach(b.pieces) { p in mt("· " + p.text).appFont(14) }
            mt("줄 세우기: " + b.sortPiece.kind.name + " 큰 순").appFont(12).foregroundStyle(Theme.muted)
            if !d.s.exceptions.direct.isEmpty || !d.s.exceptions.bundles.isEmpty || d.s.exceptions.mine {
                mt("예외 종목은 빼고 훑어요 (선정 탭에서 바꿔요)").appFont(12).foregroundStyle(Theme.muted)
            }
            HStack {
                MockSmallButton(title: "고치기") { editing = b }
                Spacer()
            }
            if let p = d.progress, d.busy == "observe" || d.busy == "cycle" {
                ProgressView(value: Double(p.done), total: Double(max(1, p.total)))
                mt("일봉 받는 중 \(p.done.formatted()) / \(p.total.formatted())").appFont(12).foregroundStyle(Theme.muted)
            }
            MockRunButton(title: "지금 훑기") { await d.runObserve() }
            MockRunLine(run: d.s.runs["observe"])
        }
    }

    private func wordsCard(_ d: MockDesk) -> some View {
        MockSection(title: "말로 조건 만들기", trailing: "AI · 누를 때만") {
            TextField("예: 거래량이 평소 세 배 넘고 오늘 2% 넘게 오른 종목" as String, text: $words, axis: .vertical)
                .appFont(14).lineLimit(1...3)
                .padding(10).background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
            MockRunButton(title: d.aiBusy == "words" ? "AI 가 조각을 만드는 중" : "AI 로 조각 만들기", ai: true) {
                if let r = await d.aiWords(words) {
                    wordsNote = r.note.isEmpty ? nil : r.note
                    editing = r.bundle
                }
            }
            .disabled(words.trimmingCharacters(in: .whitespaces).isEmpty)
            if let n = wordsNote { mt("AI: " + n).appFont(12).foregroundStyle(Theme.muted) }
            if d.s.runs["ai"]?.text.contains("말로 조건") == true { MockRunLine(run: d.s.runs["ai"]) }
        }
    }

    private func results(_ sc: MockScan) -> some View {
        MockSection(title: "걸린 종목 \(sc.hits.count)", trailing: "\(sc.total.formatted())개 중 · 기준 \(sc.barDay)") {
            if !sc.dropped.isEmpty {
                let txt = sc.dropped.sorted { $0.key < $1.key }.map { k, n in (k < 0 ? "자료 없음" : (k < sc.pieceTexts.count ? sc.pieceTexts[k] : "?")) + " \(n)" }
                mt("떨어진 이유: " + txt.joined(separator: " · ")).appFont(12).foregroundStyle(Theme.muted)
            }
            if !sc.excluded.isEmpty { mt("예외로 뺀 \(sc.excluded.count)종목").appFont(12).foregroundStyle(Theme.muted) }
            ForEach(Array(sc.hits.prefix(60).enumerated()), id: \.element.code) { i, h in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    mt("\(i + 1)").appFont(12, .bold).foregroundStyle(Theme.muted).frame(width: 24, alignment: .trailing)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            mt(h.name).appFont(14, .semibold).lineLimit(1)
                            mt(h.code).appFont(11).foregroundStyle(Theme.muted)
                            Spacer()
                            mt(MockFmt.won(h.price)).appFont(13)
                            if let c = h.change { mt(MockFmt.pct(c, digits: 1)).appFont(12, .semibold).foregroundStyle(Theme.change(c)) }
                        }
                        mt(h.why).appFont(12).foregroundStyle(Theme.sub).lineLimit(2)
                    }
                }
                if i < min(59, sc.hits.count - 1) { Divider() }
            }
            if sc.hits.count > 60 { mt("위 60개만 보여요").appFont(12).foregroundStyle(Theme.muted) }
        }
    }
}

// MARK: 조건 묶음 고치기 (관찰 묶음 · 예외 조건 묶음 같이 쓴다)

struct MockBundleEditor: View {
    let start: MockBundle
    var title = "조건 묶음"
    var canDelete = false
    var forException = false
    /// nil = 지우기
    let done: (MockBundle?) -> Void
    @Environment(\.dismiss) private var dismiss

    struct Draft: Identifiable {
        var id = UUID()
        var kind: MockPiece.Kind
        var lo: String
        var hi: String
        var days: Int
        var above: Bool
    }
    @State private var name = ""
    @State private var scope = MockBundle.Scope.all
    @State private var sort = MockPiece.Kind.volume
    @State private var sortDays = 20
    @State private var drafts: [Draft] = []
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("이름" as String, text: $name)
                    if !forException {
                        Picker(selection: $scope) {
                            ForEach(MockBundle.Scope.allCases, id: \.self) { mt($0.name).tag($0) }
                        } label: { mt("시장") }
                    }
                }
                ForEach($drafts) { $p in
                    Section {
                        Picker(selection: $p.kind) {
                            ForEach(MockPiece.Kind.allCases) { mt($0.name).tag($0) }
                        } label: { mt("조각") }
                        if p.kind == .ma {
                            Stepper(value: $p.days, in: 2...120) { mt("\(p.days)일선") }
                            Picker(selection: $p.above) { mt("위").tag(true); mt("아래").tag(false) } label: { mt("가격이") }
                                .pickerStyle(.segmented)
                        } else {
                            if p.kind == .volume { Stepper(value: $p.days, in: 2...120) { mt("\(p.days)일 평균과 견줘") } }
                            HStack {
                                mt("이상")
                                TextField("비움" as String, text: $p.lo).keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing)
                                mt(Self.unit(p.kind)).foregroundStyle(Theme.muted)
                            }
                            HStack {
                                mt("이하")
                                TextField("비움" as String, text: $p.hi).keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing)
                                mt(Self.unit(p.kind)).foregroundStyle(Theme.muted)
                            }
                        }
                        Button(role: .destructive) { drafts.removeAll { $0.id == p.id } } label: { mt("이 조각 빼기") }
                    } footer: { mt(Self.piece(p).text) }
                }
                Section {
                    Button { drafts.append(Draft(kind: .change, lo: "", hi: "", days: 20, above: true)) } label: { mt("+ 조각 더하기") }
                    if !forException {
                        Picker(selection: $sort) {
                            ForEach(MockPiece.Kind.allCases) { mt($0.name).tag($0) }
                        } label: { mt("줄 세우기 (큰 순)") }
                        if sort == .volume || sort == .ma { Stepper(value: $sortDays, in: 2...120) { mt("줄 세우기 날 수 \(sortDays)") } }
                    }
                } footer: { mt("조각은 모두 맞아야 걸려요. 칸을 비우면 그쪽은 열어 둬요.") }
                if canDelete {
                    Section { Button(role: .destructive) { done(nil); dismiss() } label: { mt("이 묶음 지우기") } }
                }
            }
            .navigationTitle(Text(verbatim: title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } }
                ToolbarItem(placement: .confirmationAction) {
                    Button { done(result); dismiss() } label: { mt("저장").bold() }
                        .disabled(drafts.isEmpty || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            name = start.name; scope = start.scope; sort = start.sort; sortDays = start.sortDays
            drafts = start.pieces.map { Draft(id: $0.id, kind: $0.kind, lo: $0.lo.map(MockFmt.num) ?? "", hi: $0.hi.map(MockFmt.num) ?? "", days: $0.days, above: $0.above) }
        }
    }

    private var result: MockBundle {
        var b = start
        b.name = name.trimmingCharacters(in: .whitespaces)
        b.scope = scope; b.sort = sort; b.sortDays = sortDays
        b.pieces = drafts.map(Self.piece)
        return b
    }

    static func unit(_ k: MockPiece.Kind) -> String {
        switch k {
        case .price: return "원"
        case .change: return "%"
        case .volume: return "배"
        case .ma: return ""
        case .amount: return "억"
        }
    }

    static func num(_ s: String) -> Double? {
        let t = s.replacingOccurrences(of: ",", with: "").replacingOccurrences(of: "−", with: "-").trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : Double(t)
    }

    static func piece(_ p: Draft) -> MockPiece {
        var x = MockPiece(id: p.id, kind: p.kind, days: p.days, above: p.above)
        if p.kind != .ma { x.lo = num(p.lo); x.hi = num(p.hi) }
        if let lo = x.lo, let hi = x.hi, lo > hi { (x.lo, x.hi) = (hi, lo) }
        return x
    }
}

// MARK: 선정

struct MockSelectView: View {
    @State private var ruleOpen = false
    @State private var findOpen = false
    @State private var exBundle: MockBundle?

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .select) {
            ruleCard(d)
            picksCard(d)
            exceptionCard(d)
        }
        .sheet(isPresented: $ruleOpen) { MockRuleEditor() }
        .sheet(isPresented: $findOpen) {
            MockStockFinder(title: "예외 종목 넣기") { st in
                d.edit("예외 종목 넣음 · \(st.name)") { s in
                    if !s.exceptions.direct.contains(where: { $0.code == st.code }) { s.exceptions.direct.append(st) }
                }
            }
        }
        .sheet(item: $exBundle) { b in
            MockBundleEditor(start: b, title: "예외 조건", canDelete: d.s.exceptions.bundles.contains { $0.id == b.id }, forException: true) { out in
                d.edit(out.map { "예외 조건 '\($0.name)' 저장 · " + $0.pieces.map(\.text).joined(separator: " · ") } ?? "예외 조건 '\(b.name)' 지움") { s in
                    s.exceptions.bundles.removeAll { $0.id == b.id }
                    if let out { s.exceptions.bundles.append(out) }
                }
            }
        }
    }

    private func ruleCard(_ d: MockDesk) -> some View {
        let r = d.s.select
        return MockSection(title: "선정 규칙 · " + r.name, trailing: r.review ? "검토 문 닫힘" : "검토 문 열림 · 바로 주문") {
            mt("관찰 순위대로 최대 \(r.count)종목 · 한 종목 평가액의 \(Int(r.maxPer * 100))%까지 · 현금 \(Int(r.keepCash * 100))% 남김").appFont(14)
            if d.s.scan == nil { mt("관찰 결과가 없어요. 관찰 탭에서 먼저 훑어 주세요.").appFont(12).foregroundStyle(Theme.up) }
            HStack {
                MockSmallButton(title: "규칙 고치기") { ruleOpen = true }
                Spacer()
            }
            MockRunButton(title: "지금 고르기") { await d.runSelect() }
            if r.review && !d.s.picks.isEmpty {
                MockRunButton(title: "검토 끝 · 고른 \(d.s.picks.count)종목 주문") { await d.orderPicks() }
            }
            MockRunLine(run: d.s.runs["select"])
        }
    }

    private func picksCard(_ d: MockDesk) -> some View {
        let waiting = !d.s.picks.isEmpty
        let list = waiting ? d.s.picks : d.lastPicks
        return MockSection(title: waiting ? "검토할 종목 \(list.count)" : "마지막으로 고른 종목 \(list.count)",
                           trailing: d.s.pickedAt.map { MockFmt.stamp($0) }) {
            if list.isEmpty && d.s.skips.isEmpty { mt("아직 없어요").appFont(13).foregroundStyle(Theme.muted) }
            ForEach(list) { p in
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        MockTag.who(p.who)
                        mt(p.name).appFont(14, .semibold).lineLimit(1)
                        Spacer()
                        mt("\(p.qty)주 · \(MockFmt.won(p.budget))").appFont(13)
                    }
                    mt(p.why + " · 비중 " + String(format: "%.0f%%", p.weight * 100)).appFont(12).foregroundStyle(Theme.sub).lineLimit(2)
                    if let v = d.s.aiViews[p.code] {
                        mt("AI " + v.name + " · " + v.why).appFont(12).foregroundStyle(v.view == "drop" ? Theme.up : v.view == "agree" ? Theme.green : Theme.sub)
                    }
                    if waiting {
                        MockSmallButton(title: "검토에서 빼기", color: Theme.up) { d.dropPick(p.code) }
                    }
                }
                Divider()
            }
            if d.aiBusy == "review" { mt("AI 의견 받는 중…").appFont(12).foregroundStyle(Theme.muted) }
            let skips = d.s.skips.prefix(20)
            if !skips.isEmpty {
                mt("고르지 않은 종목").appFont(13, .semibold).padding(.top, 4)
                ForEach(Array(skips.enumerated()), id: \.offset) { _, k in
                    mt(k.name + " · " + k.why).appFont(12).foregroundStyle(k.short ? Theme.up : Theme.muted).lineLimit(1)
                }
            }
        }
    }

    private func exceptionCard(_ d: MockDesk) -> some View {
        let ex = d.s.exceptions
        return MockSection(title: "예외 종목", trailing: "규칙·AI 가 사지도 팔지도 않음") {
            Toggle(isOn: Binding(get: { ex.mine }, set: { v in d.edit(v ? "예외: 내 보유 종목 전부 켬" : "예외: 내 보유 종목 전부 끔") { $0.exceptions.mine = v } })) {
                VStack(alignment: .leading, spacing: 2) {
                    mt("앱의 내 보유 종목 전부").appFont(14)
                    mt("지금 \(d.mine.count)종목").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .tint(Theme.teal)
            Divider()
            HStack {
                mt("직접 넣은 종목 \(ex.direct.count)").appFont(14, .semibold)
                Spacer()
                MockSmallButton(title: "+ 직접 추가") { findOpen = true }
            }
            ForEach(ex.direct, id: \.code) { st in
                HStack {
                    mt(st.name).appFont(14); mt(st.code).appFont(11).foregroundStyle(Theme.muted)
                    Spacer()
                    Button { d.edit("예외 종목 뺌 · \(st.name)") { $0.exceptions.direct.removeAll { $0.code == st.code } } } label: {
                        Image(systemName: "minus.circle").foregroundStyle(Theme.up).frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                }
            }
            Divider()
            HStack {
                mt("조건으로 뺄 종목 \(ex.bundles.count)묶음").appFont(14, .semibold)
                Spacer()
                MockSmallButton(title: "+ 조건 추가") { exBundle = MockBundle(name: "예외 조건", pieces: [MockPiece(kind: .price, hi: 1000)]) }
            }
            ForEach(ex.bundles) { b in
                Button { exBundle = b } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        mt(b.name).appFont(14)
                        mt(b.pieces.map(\.text).joined(separator: " · ")).appFont(12).foregroundStyle(Theme.muted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            }
            if !ex.bundles.isEmpty {
                mt("마지막 훑기에서 조건에 걸린 \(d.s.exceptHits.count)종목 (다음 훑기 때 다시 셈)").appFont(12).foregroundStyle(Theme.muted)
            }
        }
    }
}

/// 선정 규칙 고치기
struct MockRuleEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State private var r = MockDesk.shared.s.select
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("규칙 이름" as String, text: $r.name)
                    Stepper(value: $r.count, in: 1...30) { mt("최대 \(r.count)종목") }
                    Stepper(value: $r.maxPer, in: 0.02...1, step: 0.01) { mt("한 종목 최대 \(Int((r.maxPer * 100).rounded()))%") }
                    Stepper(value: $r.keepCash, in: 0...0.9, step: 0.05) { mt("남길 현금 \(Int((r.keepCash * 100).rounded()))%") }
                } footer: { mt("관찰 결과 순위대로 고르고, 남은 현금에서 하나씩 빼요. 현금이 모자라면 '현금 부족'으로 남겨요.") }
                Section {
                    Toggle(isOn: $r.review) { mt("검토 문 닫기 (고른 뒤 내가 보고 주문)") }.tint(Theme.teal)
                } footer: { mt("모의에서는 열어 둬요(끔). 열려 있으면 고르자마자 주문해요.") }
            }
            .navigationTitle(Text(verbatim: "선정 규칙"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        let old = MockDesk.shared.s.select
                        if old != r {
                            MockDesk.shared.edit("선정 규칙 '\(r.name)' · \(r.count)종목 · 최대 \(Int((r.maxPer * 100).rounded()))% · 현금 \(Int((r.keepCash * 100).rounded()))%" + (r.review ? " · 검토 문 닫힘" : "")) { $0.select = r }
                        }
                        dismiss()
                    } label: { mt("저장").bold() }
                    .disabled(r.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

/// 국내 종목 찾기 (KRX 전체 목록)
struct MockStockFinder: View {
    let title: String
    let pick: (MockStock) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var q = ""
    var body: some View {
        let key = StockSearch.norm(q)
        let rows = key.isEmpty ? [] : Array(KrxList.all.lazy.filter { $0.key.contains(key) }.prefix(40))
        NavigationStack {
            List {
                ForEach(rows, id: \.hit.id) { r in
                    Button {
                        pick(MockStock(code: r.hit.id, name: r.hit.name, kospi: r.hit.market == "코스피"))
                        dismiss()
                    } label: {
                        HStack {
                            mt(r.hit.name).foregroundStyle(Theme.ink)
                            Spacer()
                            mt(r.hit.id + " · " + r.hit.market).appFont(12).foregroundStyle(Theme.muted)
                        }
                    }
                }
            }
            .searchable(text: $q, placement: .navigationBarDrawer(displayMode: .always), prompt: Text(verbatim: "이름이나 코드"))
            .navigationTitle(Text(verbatim: title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } } }
        }
    }
}
#endif
