#if MOCK
import SwiftUI

// 모의투자 주문 탭 · 손 주문 · 분석 탭 · 이유 사슬 · 설정

struct MockOrderView: View {
    @State private var hand: MockHandOrder.Start?
    @State private var resetAsk: MockBook?

    var body: some View {
        let d = MockDesk.shared
        let b = d.s.shown
        let l = d.ledger(b)
        MockScreen(tab: .order) {
            phaseBanner(d)
            MockSection(title: "돌리기", trailing: b.name) {
                MockRunButton(title: "지금 한 바퀴 (훑기 → 고르기 → 주문)") { await d.runCycle() }
                if !d.s.picks.isEmpty {
                    MockRunButton(title: "고른 \(d.s.picks.count)종목 주문") { await d.orderPicks() }
                }
                MockRunButton(title: "대기 주문·가격 다시 보기") { await d.tick() }
                HStack {
                    MockSmallButton(title: "손 주문") { hand = .init(side: .buy, code: nil) }
                    Spacer()
                    MockSmallButton(title: "장부 처음부터", color: Theme.up) { resetAsk = b }
                }
                MockRunLine(run: d.s.runs["order"])
                if b == .ai, let n = d.s.aiNote { mt("AI: " + n).appFont(12).foregroundStyle(Theme.sub) }
            }
            pendingCard(d, l)
            holdingsCard(d, l)
            fillsCard(d, l)
        }
        .sheet(item: $hand) { MockHandOrder(start: $0) }
        .confirmationDialog(Text(verbatim: "\(resetAsk?.name ?? "") 를 처음부터 할까요?"), isPresented: Binding(get: { resetAsk != nil }, set: { if !$0 { resetAsk = nil } }), titleVisibility: .visible) {
            Button(role: .destructive) { if let r = resetAsk { d.reset(r) }; resetAsk = nil } label: { mt("처음부터 (옛 장부는 보관)") }
            Button(role: .cancel) { resetAsk = nil } label: { mt("그만두기") }
        } message: {
            mt("현금 1,000만원으로 다시 시작해요. 옛 장부와 기록은 지우지 않아요.")
        }
    }

    private func phaseBanner(_ d: MockDesk) -> some View {
        let (t, c): (String, Color) = {
            switch d.phase {
            case .open: return ("장중 · 주문하는 순간의 토스 호가로 정식 체결", Theme.green)
            case .closed: return ("장 밖 · 마지막 호가로 시험 체결 (성적과 따로 셈)", Theme.orange)
            case .unknown: return ("장 일정을 확인하지 못했어요 · 새로 사지 않아요", Theme.up)
            }
        }()
        return mt(t).appFont(13, .semibold).foregroundStyle(c)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12).background(c.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
    }

    private func pendingCard(_ d: MockDesk, _ l: MockLedger) -> some View {
        MockSection(title: "대기 주문 \(l.pending.count)", trailing: l.reserved > 0 ? "묶인 돈 " + MockFmt.won(l.reserved) : nil) {
            if l.pending.isEmpty { mt("없어요").appFont(13).foregroundStyle(Theme.muted) }
            ForEach(l.pending) { o in
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            MockTag.who(o.who)
                            mt(o.name).appFont(14, .semibold)
                            mt("\(o.side.name) \(o.qty)주 · \(o.type.name)" + (o.limit.map { " " + MockFmt.won($0) } ?? "")).appFont(12)
                        }
                        mt("미체결 · " + (o.waitWhy ?? "확인 중")).appFont(12).foregroundStyle(Theme.muted)
                    }
                    Spacer()
                    MockSmallButton(title: "취소", color: Theme.up) { d.cancel(l.book, orderId: o.id) }
                }
                Divider()
            }
        }
    }

    private func holdingsCard(_ d: MockDesk, _ l: MockLedger) -> some View {
        MockSection(title: "보유 \(l.positions.count)", trailing: "현금 " + MockFmt.won(l.cash)) {
            if l.positions.isEmpty { mt("없어요").appFont(13).foregroundStyle(Theme.muted) }
            ForEach(l.positions.values.sorted { $0.opened < $1.opened }) { p in
                let now = d.s.prices[p.code] ?? p.avg
                let r = p.avg > 0 ? now / p.avg - 1 : 0
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            MockTag.who(p.who)
                            if p.test { MockTag(text: "시험", color: Theme.orange) }
                            mt(p.name).appFont(14, .semibold).lineLimit(1)
                        }
                        mt("\(p.qty)주 · 평단 \(MockFmt.won(p.avg)) · 지금 \(MockFmt.won(now))").appFont(12).foregroundStyle(Theme.sub)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        mt(MockFmt.pct(r)).appFont(14, .bold).foregroundStyle(Theme.change(r))
                        if l.book == .rule {
                            MockSmallButton(title: "팔기") { hand = .init(side: .sell, code: p.code) }
                        }
                    }
                }
                Divider()
            }
        }
    }

    private func fillsCard(_ d: MockDesk, _ l: MockLedger) -> some View {
        let today = MockFmt.day(Date())
        let fills = d.events.reversed().filter { $0.ledger == l.id && ($0.kind == .fill || $0.kind == .cancel || $0.kind == .short) && MockFmt.day($0.at) == today }
        return MockSection(title: "오늘 체결 \(fills.filter { $0.kind == .fill }.count)", trailing: "수수료 \(MockFmt.won(l.fees)) · 세금 \(MockFmt.won(l.taxes))") {
            if fills.isEmpty { mt("없어요").appFont(13).foregroundStyle(Theme.muted) }
            ForEach(Array(fills.prefix(40))) { e in MockEventRow(e: e) }
        }
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
                    mt(MockFmt.time(e.at)).appFont(11).foregroundStyle(Theme.muted)
                    mt(e.kind.name).appFont(11, .bold).foregroundStyle(e.kind == .error ? Theme.up : Theme.sub)
                    if let w = e.who { MockTag.who(w) }
                    if e.test { MockTag(text: "시험", color: Theme.orange) }
                    if let n = e.name { mt(n).appFont(13, .semibold).lineLimit(1) }
                    Spacer()
                    if let p = e.pnl { mt(MockFmt.signed(p.rounded()) + "원").appFont(12, .bold).foregroundStyle(Theme.change(p)) }
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
        NavigationStack {
            List {
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
                        if let b = x.bundle { mt("조건 묶음 " + b + (x.rule.map { " · 선정 규칙 " + $0 } ?? "")).appFont(11).foregroundStyle(Theme.muted) }
                        if let a = x.ai { mt("AI \(a.model) · 입력 \(a.input) · 출력 \(a.output) · $\(String(format: "%.3f", a.cost))" + (a.cached ? " · 지난 답" : "")).appFont(11).foregroundStyle(Theme.muted) }
                    }
                }
                if chain.count == 1 { mt("앞 단계 기록이 없어요 (손 주문이거나 장부를 새로 했어요)").appFont(12).foregroundStyle(Theme.muted) }
            }
            .navigationTitle(Text(verbatim: "이유 따라가기"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } } }
        }
    }
}

// MARK: 손 주문 (규칙 장부에 '손'으로 적는다)

struct MockHandOrder: View {
    struct Start: Identifiable {
        var id = UUID()
        var side: MockSide
        var code: String?
    }
    let start: Start
    @Environment(\.dismiss) private var dismiss
    @State private var side = MockSide.buy
    @State private var stock: MockStock?
    @State private var type = MockOrderType.market
    @State private var qty = "1"
    @State private var limit = ""
    @State private var finding = false
    @State private var sending = false
    @State private var result: String?

    var body: some View {
        let d = MockDesk.shared
        let l = d.ledger(.rule)
        let held = stock.flatMap { l.positions[$0.code] }
        NavigationStack {
            Form {
                Section {
                    Picker(selection: $side) { mt("매수").tag(MockSide.buy); mt("매도").tag(MockSide.sell) } label: { mt("방향") }
                        .pickerStyle(.segmented)
                    Button { finding = true } label: {
                        HStack {
                            mt("종목")
                            Spacer()
                            mt(stock.map { $0.name + " · " + $0.code } ?? "고르기").foregroundStyle(stock == nil ? Theme.teal : Theme.ink)
                        }
                    }
                    if let h = held { mt("보유 \(h.qty)주 · 평단 \(MockFmt.won(h.avg))").appFont(12).foregroundStyle(Theme.muted) }
                    if let c = stock?.code, let px = d.s.prices[c] { mt("마지막 가격 " + MockFmt.won(px)).appFont(12).foregroundStyle(Theme.muted) }
                }
                Section {
                    Picker(selection: $type) { mt("시장가").tag(MockOrderType.market); mt("지정가").tag(MockOrderType.limit) } label: { mt("주문") }
                        .pickerStyle(.segmented)
                    HStack {
                        mt("수량")
                        TextField("주" as String, text: $qty).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                        mt("주").foregroundStyle(Theme.muted)
                    }
                    if type == .limit {
                        HStack {
                            mt("가격")
                            TextField("원" as String, text: $limit).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                            mt("원").foregroundStyle(Theme.muted)
                        }
                    }
                } footer: {
                    mt("주문하는 순간 토스 호가로 판단해요. 장 밖이면 시험 체결이에요. 지정가는 호가 단위에 맞춰요.")
                }
                Section {
                    Button {
                        guard let st = stock else { return }
                        sending = true
                        Task {
                            let ok = await d.runHand(side, code: st.code, name: st.name, qty: Int(qty), type: type, limit: type == .limit ? Double(limit) : nil)
                            sending = false
                            if ok { dismiss() } else { result = d.events.last.map { $0.kind.name + " · " + $0.text } ?? "주문하지 못했어요" }
                        }
                    } label: {
                        mt(sending ? "주문 중…" : "\(side.name) 주문").bold().frame(maxWidth: .infinity)
                    }
                    .disabled(!canSend(held) || sending || d.busy != nil)
                    if let r = result { mt(r).appFont(12).foregroundStyle(Theme.up) }
                    if d.busy != nil && !sending { mt(MockDesk.busyName(d.busy ?? "") + " 중이라 기다려요").appFont(12).foregroundStyle(Theme.muted) }
                }
            }
            .navigationTitle(Text(verbatim: "손 주문"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } } }
            .sheet(isPresented: $finding) { MockStockFinder(title: "종목 고르기") { stock = $0 } }
        }
        .onAppear {
            side = start.side
            if let c = start.code { stock = MockStock(code: c, name: MockDesk.name(c), kospi: false); qty = String(l.positions[c]?.qty ?? 1) }
        }
    }

    private func canSend(_ held: MockPosition?) -> Bool {
        guard stock != nil, let q = Int(qty), q >= 1 else { return false }
        if side == .sell && (held?.qty ?? 0) < q { return false }
        if type == .limit && (Double(limit) ?? 0) <= 0 { return false }
        return true
    }
}

// MARK: 분석

struct MockAnalysisView: View {
    @State private var unit = MockCalc.Unit.day
    @State private var settings = false

    var body: some View {
        let d = MockDesk.shared
        let l = d.ledger(d.s.shown)
        MockScreen(tab: .analysis) {
            HStack {
                Spacer()
                MockSmallButton(title: "설정") { settings = true }
            }
            nowCard(d)
            compareCard(d, l)
            periodCard(d, l)
            aiCard(d)
            logCard(d, l)
        }
        .sheet(isPresented: $settings) { MockSettingsView() }
        .task { await d.refreshWallet() }
    }

    private func nowCard(_ d: MockDesk) -> some View {
        MockSection(title: "지금 현황") {
            ForEach(MockBook.allCases, id: \.self) { b in
                let l = d.ledger(b), eq = d.equity(b)
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        mt(b.name).appFont(14, .semibold)
                        Spacer()
                        mt(MockFmt.won(eq)).appFont(14, .bold)
                    }
                    mt("수수료·세금 뒤 \(MockFmt.pct(eq / l.startCash - 1)) · 전 \(MockFmt.pct((eq + l.fees + l.taxes) / l.startCash - 1)) · 현금 \(MockFmt.won(l.cash)) · 보유 \(l.positions.count)")
                        .appFont(12).foregroundStyle(Theme.sub)
                    mt("낸 수수료 \(MockFmt.won(l.fees)) · 세금 \(MockFmt.won(l.taxes)) · 시작 \(MockFmt.day(l.started))").appFont(11).foregroundStyle(Theme.muted)
                }
                Divider()
            }
            if !d.s.archived.isEmpty { mt("보관한 옛 장부 \(d.s.archived.count)개").appFont(11).foregroundStyle(Theme.muted) }
        }
    }

    /// 규칙 · 손 · AI · 시험 (매도 체결만 한 건으로 센다. 손익은 그 몫을 산 쪽으로)
    private func compareCard(_ d: MockDesk, _ l: MockLedger) -> some View {
        let rows: [(String, MockCalc.Stat)] = [
            ("규칙", MockCalc.stat(d.events, ledger: d.ledger(.rule).id, who: .rule)),
            ("손", MockCalc.stat(d.events, ledger: d.ledger(.rule).id, who: .hand)),
            ("AI", MockCalc.stat(d.events, ledger: d.ledger(.ai).id, who: .ai)),
            ("시험 (" + d.s.shown.name + ")", MockCalc.stat(d.events, ledger: l.id, test: true)),
        ]
        let few = rows.prefix(3).map(\.1.trades).reduce(0, +) < 20
        return MockSection(title: "규칙 · 손 · AI", trailing: "판 매매만 셈") {
            ForEach(rows, id: \.0) { name, st in
                HStack {
                    mt(name).appFont(14, .semibold).frame(width: 96, alignment: .leading)
                    mt("\(st.trades)건 · 승률 " + (st.winRate.map { String(format: "%.0f%%", $0 * 100) } ?? "-")).appFont(12)
                    Spacer()
                    VStack(alignment: .trailing, spacing: 1) {
                        mt(MockFmt.signed(st.pnl.rounded()) + "원").appFont(13, .bold).foregroundStyle(Theme.change(st.pnl))
                        mt((st.ret.map { MockFmt.pct($0) } ?? "-") + " · 전 " + (st.retGross.map { MockFmt.pct($0) } ?? "-")).appFont(11).foregroundStyle(Theme.muted)
                    }
                }
                if st.trades > 0 {
                    mt("이긴 합 \(MockFmt.won(st.winSum)) · 진 합 \(MockFmt.won(st.lossSum))").appFont(11).foregroundStyle(Theme.muted)
                }
                Divider()
            }
            if few { mt("판 매매가 아직 적어요. 성적은 운으로 볼 수 있어요.").appFont(12).foregroundStyle(Theme.orange) }
        }
    }

    private func periodCard(_ d: MockDesk, _ l: MockLedger) -> some View {
        let ps = MockCalc.periods(d.events, ledger: l.id, unit: unit)
        return MockSection(title: "기간 통계", trailing: l.book.name) {
            Picker(selection: $unit) {
                ForEach(MockCalc.Unit.allCases) { mt($0.name).tag($0) }
            } label: { mt("단위") }
            .pickerStyle(.segmented)
            if ps.isEmpty { mt("판 매매가 쌓이면 보여요").appFont(13).foregroundStyle(Theme.muted) }
            ForEach(ps.prefix(30), id: \.key) { k, st in
                HStack {
                    mt(k).appFont(13)
                    Spacer()
                    mt("\(st.trades)건 · \(st.wins)승").appFont(12).foregroundStyle(Theme.sub)
                    mt(MockFmt.signed(st.pnl.rounded()) + "원").appFont(13, .bold).foregroundStyle(Theme.change(st.pnl)).frame(minWidth: 90, alignment: .trailing)
                }
            }
        }
    }

    private func aiCard(_ d: MockDesk) -> some View {
        let a = d.s.ai
        let spent = d.events.compactMap(\.ai).reduce(0) { $0 + $1.cost }
        return MockSection(title: "AI 조언", trailing: "규칙 장부는 고치지 않아요") {
            if let w = d.s.aiWallet {
                mt("쓴 돈 오늘 $\(String(format: "%.2f", w.day)) / $\(MockFmt.num(a.day)) · 이번 주 $\(String(format: "%.2f", w.week)) / $\(MockFmt.num(a.week)) · 이번 달 $\(String(format: "%.2f", w.month)) / $\(MockFmt.num(a.month))")
                    .appFont(12)
                if !w.key { mt("서버에 Claude 키가 없어요 (ai_keys.json)").appFont(12).foregroundStyle(Theme.up) }
            } else {
                mt("서버 지갑을 아직 못 읽었어요").appFont(12).foregroundStyle(Theme.muted)
            }
            mt("이 기기 기록의 AI 비용 합 $\(String(format: "%.2f", spent))").appFont(11).foregroundStyle(Theme.muted)
            if let n = d.s.aiNote { mt("AI 장부 판단: " + n).appFont(13) }
            if let t = d.s.aiDaily { mt("하루 정리 (\(d.s.aiDailyDay ?? ""))\n" + t).appFont(13) }
            MockRunButton(title: d.aiBusy == "ledger" ? "AI 장부 판단 중…" : "AI 장부 지금 한 번", ai: true) { await d.runAI("ledger") }
            MockRunButton(title: d.aiBusy == "daily" ? "하루 정리 중…" : "하루 정리 지금", ai: true) { await d.runAI("daily") }
            MockRunLine(run: d.s.runs["ai"], empty: "AI 는 아직 부르지 않았어요")
        }
    }

    private func logCard(_ d: MockDesk, _ l: MockLedger) -> some View {
        let rows = Array(d.events.reversed().filter { $0.ledger == l.id || $0.kind == .rules }.prefix(60))
        return MockSection(title: "기록", trailing: "누르면 이유 따라가기") {
            if rows.isEmpty { mt("아직 없어요").appFont(13).foregroundStyle(Theme.muted) }
            ForEach(rows) { e in MockEventRow(e: e); Divider() }
        }
    }
}

// MARK: 설정 (수수료·팔기 규칙·자동·AI)

struct MockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var fees = MockDesk.shared.s.fees
    @State private var sell = MockDesk.shared.s.sell
    @State private var auto = MockDesk.shared.s.auto
    @State private var ai = MockDesk.shared.s.ai

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Stepper(value: $fees.commission, in: 0...0.005, step: 0.00001) { mt("수수료 (한쪽) " + String(format: "%.3f%%", fees.commission * 100)) }
                    Stepper(value: $fees.sellTax, in: 0...0.01, step: 0.0005) { mt("매도 세금 " + String(format: "%.2f%%", fees.sellTax * 100)) }
                } header: { mt("수수료·세금") } footer: { mt("2026-10 확인: 토스증권 국내 0.015%, 증권거래세 매도 0.20%. 원 아래는 버려요.") }
                Section {
                    pctRow("손절", value: $sell.stopLoss, base: 0.05)
                    pctRow("익절", value: $sell.takeProfit, base: 0.10)
                    Toggle(isOn: Binding(get: { sell.maxDays != nil }, set: { sell.maxDays = $0 ? (sell.maxDays ?? 5) : nil })) { mt("오래 들면 팔기") }.tint(Theme.teal)
                    if let n = sell.maxDays {
                        Stepper(value: Binding(get: { n }, set: { sell.maxDays = $0 }), in: 1...60) { mt("산 뒤 거래일 \(n)일") }
                    }
                } header: { mt("팔기 규칙 (규칙 장부)") } footer: { mt("1분마다 장중 가격으로 봐요. 예외 종목·손으로 산 종목은 팔지 않아요.") }
                Section {
                    Picker(selection: $auto) {
                        ForEach([0, 5, 10, 15, 30, 60], id: \.self) { mt($0 == 0 ? "끔" : "\($0)분마다").tag($0) }
                    } label: { mt("한 바퀴 자동 (장중)") }
                } footer: { mt("앱이 열려 있을 때만 돌아요. 훑기는 2~3분 걸려요.") }
                Section {
                    Picker(selection: $ai.ledgerEvery) {
                        ForEach([0, 5, 10, 15, 30, 60], id: \.self) { mt($0 == 0 ? "끔" : "\($0)분마다").tag($0) }
                    } label: { mt("AI 장부 (장중)") }
                    Toggle(isOn: $ai.reviewEach) { mt("고를 때마다 AI 의견") }.tint(Theme.teal)
                    Toggle(isOn: $ai.daily) { mt("장 마감 뒤 하루 정리") }.tint(Theme.teal)
                    Stepper(value: $ai.day, in: 0...10, step: 0.5) { mt("하루 한도 $" + MockFmt.num(ai.day)) }
                    Stepper(value: $ai.week, in: 0...40, step: 1) { mt("주 한도 $" + MockFmt.num(ai.week)) }
                    Stepper(value: $ai.month, in: 0...100, step: 5) { mt("월 한도 $" + MockFmt.num(ai.month)) }
                } header: { mt("AI") } footer: { mt("말로 조건은 누를 때만 불러요. 한도에 닿으면 AI 만 쉬고 규칙은 그대로 돌아요. 서버는 월 $100 위로 받지 않아요.") }
            }
            .navigationTitle(Text(verbatim: "모의투자 설정"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } }
                ToolbarItem(placement: .confirmationAction) { Button { save(); dismiss() } label: { mt("저장").bold() } }
            }
        }
    }

    private func pctRow(_ name: String, value: Binding<Double?>, base: Double) -> some View {
        Group {
            Toggle(isOn: Binding(get: { value.wrappedValue != nil }, set: { value.wrappedValue = $0 ? (value.wrappedValue ?? base) : nil })) { mt(name) }.tint(Theme.teal)
            if let v = value.wrappedValue {
                Stepper(value: Binding(get: { v }, set: { value.wrappedValue = $0 }), in: 0.01...0.5, step: 0.01) { mt(name + " " + String(format: "%.0f%%", v * 100)) }
            }
        }
    }

    private func save() {
        let d = MockDesk.shared
        var what: [String] = []
        if fees != d.s.fees { what.append("수수료 " + String(format: "%.3f%%", fees.commission * 100) + " · 세금 " + String(format: "%.2f%%", fees.sellTax * 100)) }
        if sell != d.s.sell {
            what.append("팔기 " + [sell.stopLoss.map { "손절 \(Int(($0 * 100).rounded()))%" }, sell.takeProfit.map { "익절 \(Int(($0 * 100).rounded()))%" },
                                  sell.maxDays.map { "\($0)일" }].compactMap { $0 }.joined(separator: " · ").ifEmpty("끔"))
        }
        if auto != d.s.auto { what.append(auto == 0 ? "자동 끔" : "자동 \(auto)분") }
        if ai != d.s.ai { what.append("AI 장부 \(ai.ledgerEvery)분 · 의견 \(ai.reviewEach ? "켬" : "끔") · 정리 \(ai.daily ? "켬" : "끔") · 한도 $\(MockFmt.num(ai.day))/$\(MockFmt.num(ai.week))/$\(MockFmt.num(ai.month))") }
        guard !what.isEmpty else { return }
        d.edit("설정 · " + what.joined(separator: " · ")) { s in
            s.fees = fees; s.sell = sell; s.auto = auto; s.ai = ai
        }
    }
}

private extension String {
    func ifEmpty(_ s: String) -> String { isEmpty ? s : self }
}

/// 투자 모드의 네 탭 (홈 다음 자리, 태그는 원래 탭 그대로)
struct MockTabs: View {
    var body: some View {
        NavigationStack { MockObserveView() }
            .tabItem { Label { mt(MockTab.observe.name) } icon: { Image(systemName: MockTab.observe.icon) } }.tag(Tab.hold)
        NavigationStack { MockSelectView() }
            .tabItem { Label { mt(MockTab.select.name) } icon: { Image(systemName: MockTab.select.icon) } }.tag(Tab.flow)
        NavigationStack { MockOrderView() }
            .tabItem { Label { mt(MockTab.order.name) } icon: { Image(systemName: MockTab.order.icon) } }.tag(Tab.analysis)
        NavigationStack { MockAnalysisView() }
            .tabItem { Label { mt(MockTab.analysis.name) } icon: { Image(systemName: MockTab.analysis.icon) } }.tag(Tab.board)
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
            .overlay(alignment: .top) {
                if d.active {
                    mt("모의투자 모드").appFont(11, .bold).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(Theme.teal, in: Capsule())
                        .offset(y: -8)
                        .allowsHitTesting(false)
                }
            }
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
            // 투자 모드이고 앱이 앞에 있는 동안만 1분마다: 가격·대기 주문·팔기 규칙·자동·AI
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
