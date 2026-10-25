#if MOCK
import SwiftUI

// 모의투자 일지 탭 · 설정 · 매매 기록 · 이유 사슬 · 탭 묶음

/// 종목 하나의 손익 (판 몫 + 지금 들고 있는 몫)
struct MockStockPnl {
    var code: String
    var name: String
    var pnl = 0.0
    var basis = 0.0
    var ret: Double { basis > 0 ? pnl / basis : 0 }
}

/// 지난 하루 (판 매매 기준)
struct MockDay {
    var day: String
    var trades = 0
    var pnl = 0.0
}

extension MockDesk {
    func perStock() -> [MockStockPnl] {
        let l = ledger(.rule)
        var by: [String: MockStockPnl] = [:]
        for e in events where e.ledger == l.id && e.kind == .fill && e.side == .sell {
            guard let c = e.code, let p = e.pnl else { continue }
            by[c, default: MockStockPnl(code: c, name: e.name ?? c)].pnl += p
            by[c]?.basis += e.basis ?? 0
        }
        for p in l.positions.values {
            let now = s.prices[p.code] ?? p.avg
            by[p.code, default: MockStockPnl(code: p.code, name: p.name)].pnl += Double(p.qty) * now - p.cost
            by[p.code]?.basis += p.cost
        }
        return Array(by.values)
    }

    /// 오늘 전 기록이 있는 날 (최근부터)
    func pastDays(before today: String) -> [MockDay] {
        let id = ledger(.rule).id
        var order: [String] = [], by: [String: MockDay] = [:]
        for e in events.reversed() where e.ledger == id {
            let k = MockFmt.day(e.at)
            guard k < today else { continue }
            if by[k] == nil { order.append(k); by[k] = MockDay(day: k) }
            if e.kind == .fill && e.side == .sell, let p = e.pnl { by[k]?.trades += 1; by[k]?.pnl += p }
        }
        return order.compactMap { by[$0] }
    }
}

// MARK: 일지 (분석 대신)

struct MockJournalView: View {
    @State private var unit = MockCalc.Unit.day
    @State private var downFirst = false
    @State private var settings = false
    @State private var pastOpen = false
    @State private var logOpen = false

    private struct Held: Identifiable {
        let p: MockPosition
        let now: Double
        let r: Double
        var id: String { p.code }
    }

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .journal, gear: { settings = true }) {
            retCard(d)
            mineCard(d)
            bestWorst(d)
            pastCard(d)
            errLine(d)
        }
        .sheet(isPresented: $settings) { MockSettingsView() }
        .sheet(isPresented: $logOpen) { MockLogSheet() }
    }

    // 내 수익률: 칸마다 마지막 평가액으로 누적 수익률 (출발점은 속 빈 점, 오늘 점은 숨쉬기)
    private func retCard(_ d: MockDesk) -> some View {
        let pts = series(d)
        let last = pts.last ?? 0
        return Card {
            HStack {
                mt("내 수익률").appFont(16, .bold)
                Spacer()
                mt(MockFmt.pct(last)).appFont(14, .bold).monospacedDigit().foregroundStyle(last > 0 ? Theme.up : last < 0 ? Theme.down : Theme.sub)
            }
            HoldSeg(items: MockCalc.Unit.allCases.map { ($0, $0.name) }, selection: $unit)
            MockRetChart(points: pts).frame(height: 120)
        }
    }

    private func series(_ d: MockDesk) -> [Double] {
        let l = d.ledger(.rule)
        let take: Int
        switch unit {
        case .hour: take = 24
        case .day: take = 30
        case .week: take = 26
        case .month: take = 24
        case .year: take = 10
        }
        var order: [String] = [], last: [String: Double] = [:]
        for m in d.marks.sorted(by: { $0.at < $1.at }) {
            let k = MockCalc.bucket(m.at, unit)
            if last[k] == nil { order.append(k) }
            last[k] = m.eq
        }
        // 지금 칸은 지금 평가액으로
        let nowKey = MockCalc.bucket(Date(), unit)
        if last[nowKey] == nil { order.append(nowKey) }
        last[nowKey] = d.equity(.rule)
        var eqs = order.suffix(take).compactMap { last[$0] }
        // 기록이 다 들어가면 장부 시작(0%)부터
        if order.count < take { eqs.insert(l.startCash, at: 0) }
        return eqs.map { l.startCash > 0 ? $0 / l.startCash - 1 : 0 }
    }

    private func mineCard(_ d: MockDesk) -> some View {
        let l = d.ledger(.rule)
        let rows = l.positions.values.map { p -> Held in
            let now = d.s.prices[p.code] ?? p.avg
            return Held(p: p, now: now, r: p.avg > 0 ? now / p.avg - 1 : 0)
        }
        .sorted { downFirst ? $0.r < $1.r : $0.r > $1.r }
        let pnl = rows.reduce(0) { $0 + Double($1.p.qty) * $1.now - $1.p.cost }
        let mx = rows.map { abs($0.r) }.max() ?? 0
        return MockListCard {
            mt("내 종목 \(rows.count)").appFont(14, .bold)
            Button { downFirst.toggle() } label: {
                mt(downFirst ? "내린 순 ⇅" : "오른 순 ⇅").appFont(13, .bold).foregroundStyle(Theme.teal)
                    .frame(minHeight: 32).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Spacer()
            mt(MockFmt.signedWon(pnl)).appFont(14, .semibold).monospacedDigit().lineLimit(1)
                .foregroundStyle(pnl > 0 ? Theme.up : pnl < 0 ? Theme.down : Theme.sub)
        } rows: {
            if rows.isEmpty { MockEmptyRow(text: "아직 산 종목이 없어요") }
            ForEach(rows) { h in
                MockStockRow(code: h.p.code, name: h.p.name, mid: MockFmt.won(h.p.avg), pct: h.r,
                             price: MockFmt.won(h.now), band: mx > 0 ? abs(h.r) / mx : 0)
            }
        }
    }

    private func bestWorst(_ d: MockDesk) -> some View {
        let all = d.perStock()
        let best = all.filter { $0.pnl > 0 }.max { $0.pnl < $1.pnl }
        let worst = all.filter { $0.pnl < 0 }.min { $0.pnl < $1.pnl }
        return HStack(spacing: 0) {
            cell("가장 많이 번", best)
            Divider().overlay(Theme.line)
            cell("가장 많이 잃은", worst)
        }
        .fixedSize(horizontal: false, vertical: true)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private func cell(_ title: String, _ x: MockStockPnl?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            mt(title).appFont(12).foregroundStyle(Theme.muted)
            if let x {
                let c = x.pnl > 0 ? Theme.up : Theme.down
                mt(x.name).appFont(15, .bold).lineLimit(1)
                mt(MockFmt.signedWon(x.pnl)).appFont(14, .bold).monospacedDigit().foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.7)
                mt(MockFmt.pct(x.ret)).appFont(12, .semibold).monospacedDigit().foregroundStyle(c)
            } else {
                mt("없어요").appFont(14).foregroundStyle(Theme.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
    }

    private func pastCard(_ d: MockDesk) -> some View {
        let days = d.pastDays(before: MockFmt.day(Date()))
        return MockListCard {
            Button { withAnimation(.snappy) { pastOpen.toggle() } } label: {
                HStack {
                    mt("지난 일지 \(days.count)일").appFont(14, .bold)
                    Spacer()
                    Image(systemName: pastOpen ? "chevron.down" : "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.muted)
                }
                .frame(minHeight: 28).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(days.isEmpty)
        } rows: {
            if pastOpen {
                ForEach(days, id: \.day) { x in
                    VStack(spacing: 0) {
                        Divider().overlay(Theme.line)
                        HStack(spacing: 8) {
                            mt(MockFmt.md(x.day)).appFont(14, .semibold)
                            Spacer()
                            mt("판 \(x.trades)건").appFont(12).foregroundStyle(Theme.sub)
                            mt(MockFmt.signedWon(x.pnl)).appFont(14, .bold).monospacedDigit()
                                .foregroundStyle(x.pnl > 0 ? Theme.up : x.pnl < 0 ? Theme.down : Theme.sub)
                        }
                        .padding(.horizontal, 14).frame(minHeight: 44)
                    }
                }
            }
        }
    }

    /// 오늘 오류가 있을 때만: 주황 줄 → 매매 기록
    @ViewBuilder private func errLine(_ d: MockDesk) -> some View {
        let id = d.ledger(.rule).id, today = MockFmt.day(Date())
        let n = d.events.filter { $0.ledger == id && $0.kind == .error && MockFmt.day($0.at) == today }.count
        if n > 0 {
            Button { logOpen = true } label: {
                HStack(spacing: 6) {
                    mt("오류 \(n)").appFont(14, .bold)
                    mt("· 매매 기록 보기").appFont(14)
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(Theme.orange)
                .padding(.horizontal, 14).frame(minHeight: 48)
                .background(Theme.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 14))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}

/// 누적 수익률 꺾은선: 0% 점선, 출발점은 속 빈 점, 오늘 점은 숨쉬기 동심원
struct MockRetChart: View {
    let points: [Double]
    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height, pad: CGFloat = 10
            let lo = min(points.min() ?? 0, 0), hi = max(points.max() ?? 0, 0)
            let span = max(hi - lo, 0.002)
            let n = points.count
            let xy: (Int) -> CGPoint = { i in
                CGPoint(x: n > 1 ? pad + (w - pad * 2) * CGFloat(i) / CGFloat(n - 1) : w / 2,
                        y: pad + (h - pad * 2) * CGFloat((hi - points[i]) / span))
            }
            let zero = pad + (h - pad * 2) * CGFloat(hi / span)
            let c = (points.last ?? 0) > 0 ? Theme.up : (points.last ?? 0) < 0 ? Theme.down : Theme.sub
            ZStack(alignment: .topLeading) {
                Path { p in
                    p.move(to: CGPoint(x: 0, y: zero))
                    p.addLine(to: CGPoint(x: w, y: zero))
                }
                .stroke(Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                if n > 1 {
                    Path { p in
                        p.move(to: xy(0))
                        for i in 1..<n { p.addLine(to: xy(i)) }
                    }
                    .stroke(c, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                    Circle().fill(Theme.card).overlay(Circle().stroke(c, lineWidth: 2))
                        .frame(width: 8, height: 8).position(xy(0))
                }
                if n > 0 { PulseDot(color: c, dot: 8).position(xy(n - 1)) }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "누적 수익률 " + MockFmt.pct(points.last ?? 0)))
    }
}

/// 매매 기록 (오류 줄에서 연다)
struct MockLogSheet: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let d = MockDesk.shared
        let id = d.ledger(.rule).id
        let rows = Array(d.events.reversed().filter { $0.ledger == id || $0.kind == .rules }.prefix(120))
        VStack(spacing: 0) {
            MockSheetBar(title: "매매 기록") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    if rows.isEmpty { mt("아직 없어요").appFont(13).foregroundStyle(Theme.muted) }
                    ForEach(rows) { e in
                        MockEventRow(e: e)
                        Divider().overlay(Theme.line)
                    }
                }
                .padding(16)
            }
        }
        .background(Theme.bg)
    }
}

/// 기록 한 줄 (누르면 이유 사슬)
struct MockEventRow: View {
    let e: MockEvent
    @State private var open = false
    var body: some View {
        Button { open = true } label: {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    mt(MockFmt.stamp(e.at)).appFont(11).foregroundStyle(Theme.muted)
                    mt(e.kind.name).appFont(11, .bold).foregroundStyle(e.kind == .error ? Theme.orange : Theme.sub)
                    if let w = e.who { MockTag.who(w) }
                    if e.test { MockTag(text: "시험", color: Theme.orange) }
                    if let n = e.name { mt(n).appFont(13, .semibold).lineLimit(1) }
                    Spacer()
                    if let p = e.pnl { mt(MockFmt.signedWon(p)).appFont(12, .bold).foregroundStyle(Theme.change(p)) }
                }
                mt(e.text).appFont(12).foregroundStyle(Theme.sub).lineLimit(2).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $open) { MockChainView(e: e) }
    }
}

/// 이유 따라가기: 관찰 → 선정 → 주문 → 체결
struct MockChainView: View {
    let e: MockEvent
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let chain = MockCalc.chain(e, in: MockDesk.shared.events)
        VStack(spacing: 0) {
            MockSheetBar(title: "이유 따라가기") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(chain) { x in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                mt(x.kind.name).appFont(13, .bold)
                                if let w = x.who { MockTag.who(w) }
                                if x.test { MockTag(text: "시험", color: Theme.orange) }
                                Spacer()
                                mt(MockFmt.stamp(x.at)).appFont(11).foregroundStyle(Theme.muted)
                            }
                            if let n = x.name { mt(n + (x.code.map { " · " + $0 } ?? "")).appFont(13) }
                            mt(x.text).appFont(13).foregroundStyle(Theme.sub)
                            if x.kind == .fill {
                                mt("\(x.side?.name ?? "") \(x.qty ?? 0)주 · 평균 \(MockFmt.won(x.price ?? 0)) · 금액 \(MockFmt.won(x.amount ?? 0)) · 수수료 \(MockFmt.won(x.fee ?? 0))" + (x.tax.map { " · 세금 " + MockFmt.won($0) } ?? ""))
                                    .appFont(12).foregroundStyle(Theme.muted)
                            }
                            if let b = x.bundle { mt("조건 " + b + (x.rule.map { " · 고르기 " + $0 } ?? "")).appFont(11).foregroundStyle(Theme.muted) }
                        }
                        Divider().overlay(Theme.line)
                    }
                    if chain.count == 1 { mt("앞 단계 기록이 없어요 (손 주문이거나 장부를 새로 했어요)").appFont(12).foregroundStyle(Theme.muted) }
                }
                .padding(16)
            }
        }
        .background(Theme.bg)
    }
}

// MARK: 모의투자 설정 (일지 머리 톱니)

struct MockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var findOpen = false
    @State private var draft = MockPieceDraft()
    @State private var adding = false
    @State private var resetAsk = false
    @State private var fees = MockDesk.shared.s.fees
    @State private var sell = MockDesk.shared.s.sell
    @State private var auto = MockDesk.shared.s.auto

    var body: some View {
        let d = MockDesk.shared
        VStack(spacing: 0) {
            MockSheetBar(title: "모의투자 설정") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    exceptCard(d)
                    ledgerCard(d)
                    runCard
                }
                .padding(16)
            }
        }
        .background(Theme.bg)
        .onDisappear { save() }
        .sheet(isPresented: $findOpen) {
            MockStockFinder(title: "예외 종목 넣기") { st in
                d.edit("예외 종목 넣음 · \(st.name)") { s in
                    if !s.exceptions.direct.contains(where: { $0.code == st.code }) { s.exceptions.direct.append(st) }
                }
            }
        }
        .confirmationDialog(Text(verbatim: "장부를 처음부터 할까요?"), isPresented: $resetAsk, titleVisibility: .visible) {
            Button(role: .destructive) { d.reset(.rule) } label: { mt("처음부터 (옛 장부는 보관)") }
            Button(role: .cancel) {} label: { mt("그만두기") }
        } message: {
            mt("현금 1,000만원으로 다시 시작해요. 옛 장부와 기록은 지우지 않아요.")
        }
    }

    private func exceptCard(_ d: MockDesk) -> some View {
        let ex = d.s.exceptions
        return Card {
            HStack {
                mt("예외 종목").appFont(16, .bold)
                Spacer()
                mt("사지도 팔지도 않음").appFont(12).foregroundStyle(Theme.muted)
            }
            Toggle(isOn: Binding(get: { ex.mine }, set: { v in d.edit(v ? "예외: 내 보유 종목 전부 켬" : "예외: 내 보유 종목 전부 끔") { $0.exceptions.mine = v } })) {
                VStack(alignment: .leading, spacing: 1) {
                    mt("내 보유 종목 전부").appFont(14, .semibold)
                    mt("지금 \(d.mine.count)종목").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .tint(Theme.teal)
            Divider().overlay(Theme.line)
            mt("직접").appFont(13, .semibold).foregroundStyle(Theme.sub)
            FlowRow(spacing: 6) {
                ForEach(ex.direct, id: \.code) { st in
                    MockCondPill(text: st.name, delete: { d.edit("예외 종목 뺌 · \(st.name)") { $0.exceptions.direct.removeAll { $0.code == st.code } } })
                }
                MockAddPill(title: "+ 추가") { findOpen = true }
            }
            mt("조건").appFont(13, .semibold).foregroundStyle(Theme.sub)
            FlowRow(spacing: 6) {
                ForEach(ex.bundles) { b in
                    MockCondPill(text: b.pieces.map(\.text).joined(separator: " · "),
                                 edit: editAction(b),
                                 delete: { d.edit("예외 조건 지움 · " + b.pieces.map(\.text).joined(separator: " · ")) { $0.exceptions.bundles.removeAll { $0.id == b.id } } })
                }
                MockAddPill(title: "+ 추가") { draft = MockPieceDraft(); draft.kind = .price; adding = true }
            }
            if adding {
                MockPieceForm(draft: $draft, close: { adding = false }) { p in commitException(d, p) }
            }
            if !ex.bundles.isEmpty {
                mt("마지막 훑기에서 조건에 걸린 \(d.s.exceptHits.count)종목 (다음 훑기 때 다시 셈)").appFont(12).foregroundStyle(Theme.muted)
            }
        }
    }

    /// 예외 묶음 고치기 (첫 조건을 펼침에 채운다)
    private func editAction(_ b: MockBundle) -> (() -> Void)? {
        guard let p = b.pieces.first else { return nil }
        return {
            draft = MockPieceDraft(p)
            adding = true
        }
    }

    /// 예외 조건은 조건 하나가 묶음 하나 (어느 하나라도 걸리면 뺀다)
    private func commitException(_ d: MockDesk, _ p: MockPiece) {
        d.edit("예외 조건 · " + p.text) { s in
            if let i = s.exceptions.bundles.firstIndex(where: { $0.pieces.contains { $0.id == p.id } }) {
                var b = s.exceptions.bundles[i]
                if let j = b.pieces.firstIndex(where: { $0.id == p.id }) { b.pieces[j] = p }
                b.name = b.pieces.map(\.text).joined(separator: " · ")
                s.exceptions.bundles[i] = b
            } else {
                s.exceptions.bundles.append(MockBundle(name: p.text, pieces: [p]))
            }
        }
        adding = false
    }

    private func ledgerCard(_ d: MockDesk) -> some View {
        let l = d.ledger(.rule)
        let hasTest = l.positions.values.contains(where: \.test)
        return Card {
            HStack {
                mt("장부").appFont(16, .bold)
                Spacer()
                mt("시작 \(MockFmt.day(l.started)) · 평가 " + MockFmt.man(d.equity(.rule))).appFont(12).foregroundStyle(Theme.muted)
            }
            Button { d.dropTest(.rule) } label: {
                HStack {
                    mt("시험 몫 빼기").appFont(15, .semibold).foregroundStyle(hasTest ? Theme.ink : Theme.muted)
                    Spacer()
                    mt(hasTest ? "장 밖 시험 체결만" : "없어요").appFont(12).foregroundStyle(Theme.muted)
                }
                .frame(minHeight: 40).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!hasTest)
            Divider().overlay(Theme.line)
            Button { resetAsk = true } label: {
                mt("장부 처음부터").appFont(15, .semibold).foregroundStyle(Theme.up)
                    .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if !d.s.archived.isEmpty { mt("보관한 옛 장부 \(d.s.archived.count)개").appFont(11).foregroundStyle(Theme.muted) }
        }
    }

    // 예전 설정 화면의 돌리기 값 (AI 칸은 뺐다)
    private var runCard: some View {
        Card {
            mt("돌리기").appFont(16, .bold)
            Picker(selection: $auto) {
                ForEach([0, 5, 10, 15, 30, 60], id: \.self) { mt($0 == 0 ? "끔" : "\($0)분마다").tag($0) }
            } label: { mt("한 바퀴 자동 (장중)").appFont(14) }
            .tint(Theme.teal)
            Divider().overlay(Theme.line)
            VStack(alignment: .leading, spacing: 10) {
                pctRow("손절", value: $sell.stopLoss, base: 0.05)
                pctRow("익절", value: $sell.takeProfit, base: 0.10)
                Toggle(isOn: Binding(get: { sell.maxDays != nil }, set: { sell.maxDays = $0 ? (sell.maxDays ?? 5) : nil })) { mt("오래 들면 팔기").appFont(14) }.tint(Theme.teal)
                if let n = sell.maxDays {
                    Stepper(value: Binding(get: { n }, set: { sell.maxDays = $0 }), in: 1...60) { mt("산 뒤 거래일 \(n)일").appFont(14) }
                }
            }
            Divider().overlay(Theme.line)
            VStack(alignment: .leading, spacing: 10) {
                Stepper(value: $fees.commission, in: 0...0.005, step: 0.00001) { mt("수수료 (한쪽) " + String(format: "%.3f%%", fees.commission * 100)).appFont(14) }
                Stepper(value: $fees.sellTax, in: 0...0.01, step: 0.0005) { mt("매도 세금 " + String(format: "%.2f%%", fees.sellTax * 100)).appFont(14) }
            }
            mt("앱이 열려 있을 때만 돌아요. 팔기는 1분마다 장중 가격으로 봐요.").appFont(12).foregroundStyle(Theme.muted)
        }
    }

    private func pctRow(_ name: String, value: Binding<Double?>, base: Double) -> some View {
        Group {
            Toggle(isOn: Binding(get: { value.wrappedValue != nil }, set: { value.wrappedValue = $0 ? (value.wrappedValue ?? base) : nil })) { mt(name).appFont(14) }.tint(Theme.teal)
            if let v = value.wrappedValue {
                Stepper(value: Binding(get: { v }, set: { value.wrappedValue = $0 }), in: 0.01...0.5, step: 0.01) { mt(name + " " + String(format: "%.0f%%", v * 100)).appFont(14) }
            }
        }
    }

    private func save() {
        let d = MockDesk.shared
        var what: [String] = []
        if fees != d.s.fees { what.append("수수료 " + String(format: "%.3f%%", fees.commission * 100) + " · 세금 " + String(format: "%.2f%%", fees.sellTax * 100)) }
        if sell != d.s.sell {
            let t = [sell.stopLoss.map { "손절 \(Int(($0 * 100).rounded()))%" }, sell.takeProfit.map { "익절 \(Int(($0 * 100).rounded()))%" },
                     sell.maxDays.map { "\($0)일" }].compactMap { $0 }.joined(separator: " · ")
            what.append("팔기 " + (t.isEmpty ? "끔" : t))
        }
        if auto != d.s.auto { what.append(auto == 0 ? "자동 끔" : "자동 \(auto)분") }
        guard !what.isEmpty else { return }
        d.edit("설정 · " + what.joined(separator: " · ")) { s in
            s.fees = fees; s.sell = sell; s.auto = auto
        }
    }
}

/// 투자 모드의 세 탭 (홈 다음 자리, 태그는 원래 탭 그대로. 미션은 App 이 그대로 붙인다)
struct MockTabs: View {
    var body: some View {
        NavigationStack { MockObserveView() }
            .tabItem { Label { mt(MockTab.observe.name) } icon: { Image(systemName: MockTab.observe.icon) } }.tag(Tab.hold)
        NavigationStack { MockSelectView() }
            .tabItem { Label { mt(MockTab.select.name) } icon: { Image(systemName: MockTab.select.icon) } }.tag(Tab.flow)
        NavigationStack { MockJournalView() }
            .tabItem { Label { mt(MockTab.journal.name) } icon: { Image(systemName: MockTab.journal.icon) } }.tag(Tab.analysis)
    }
}

/// 홈 평가액 박스: 개발자 기기에서 금액 글자 밖을 누르면 투자 모드를 켜고 끈다 (금액 글자 탭 = 가리기, 길게 = 모양 고르기는 그대로)
struct MockEntry: ViewModifier {
    func body(content: Content) -> some View {
        let d = MockDesk.shared
        content
            .contentShape(Rectangle())
            .onTapGesture { if Support.shared.dev { d.on.toggle() } }
            .sensoryFeedback(.selection, trigger: d.on)
    }
}

// MARK: 탭 묶음 (RootView 가 투자 모드에서 쓴다)

struct MockTabsHooks: ViewModifier {
    let mine: Set<String>
    let phase: ScenePhase
    func body(content: Content) -> some View {
        let d = MockDesk.shared
        content
            .onAppear { d.mine = mine }
            .onChange(of: mine) { _, v in d.mine = v }
            // 투자 모드이고 앱이 앞에 있는 동안만 1분마다: 가격·대기 주문·팔기 규칙·자동 (AI 는 꺼 둠)
            .task(id: d.active && phase == .active) {
                guard d.active && phase == .active else { return }
                await d.refreshWallet()
                while !Task.isCancelled {
                    await d.tick()
                    try? await Task.sleep(for: .seconds(60))
                }
            }
    }
}
#endif
