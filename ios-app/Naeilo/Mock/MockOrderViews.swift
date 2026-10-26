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

    /// 오늘 판 종목 (규칙 장부 매도 체결을 종목마다 모은다, 처음 판 때 순)
    func soldToday() -> [(code: String, name: String, qty: Int, pnl: Double)] {
        let id = ledger(.rule).id, today = MockFmt.day(Date())
        var order: [String] = [], by: [String: (name: String, qty: Int, pnl: Double)] = [:]
        for e in events where e.ledger == id && e.kind == .fill && e.side == .sell && MockFmt.day(e.at) == today {
            guard let c = e.code else { continue }
            if by[c] == nil { order.append(c) }
            let x = by[c] ?? (e.name ?? c, 0, 0)
            by[c] = (x.name, x.qty + (e.qty ?? 0), x.pnl + (e.pnl ?? 0))
        }
        return order.map { (code: $0, name: by[$0]!.name, qty: by[$0]!.qty, pnl: by[$0]!.pnl) }
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
    @State private var noteOpen = false
    @State private var noteMore = false
    @State private var colW: [Int: CGFloat] = [:]
    @State private var sdraft = MockSellDraft()
    @State private var sadding = false
    @State private var info: MockStockRef?

    private struct Held: Identifiable {
        let code: String
        let name: String
        let avg: Double
        let value: Double         // 들고 있으면 지금 평가액, 다 팔았으면 판 금액
        let r: Double
        var wait = 0              // 팔기 대기 주수
        var sold = 0              // 오늘 팔린 주수
        var id: String { code }
    }

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .journal, gear: { settings = true }) {
            retCard(d)
            sellCard(d)
            mineCard(d)
            bestWorst(d)
            pastCard(d)
            noteCard(d)
        }
        .onPreferenceChange(HoldColW.self) { colW = $0 }
        .sheet(isPresented: $settings) { MockSettingsView() }
        .sheet(item: $info) { MockStockSheet(code: $0.code, name: $0.name, fills: true) }
    }

    // 팔기 조건: 켠 알약에 맞는 내 종목을 판다 (선정의 고르기 반대). 1분 주기도 같은 조건으로 장중 가격을 본다
    private func sellCard(_ d: MockDesk) -> some View {
        Card {
            FlowRow(spacing: 6) {
                ForEach(d.sconds) { c in
                    MockCondPill(text: c.text, on: c.on,
                                 tap: { sToggle(d, c) },
                                 edit: { sdraft = MockSellDraft(c); sadding = true },
                                 delete: { sRemove(d, c) })
                }
                MockAddPill { sdraft = MockSellDraft(); sadding = true }
            }
            if sadding {
                MockSellForm(draft: $sdraft, close: { sadding = false }) { sCommit(d, $0) }
            }
            if let r = d.s.runs["sell"] { MockRunLine(run: r) }
            // 오늘 판 종목은 내 종목이 아니라 여기 (다 판 종목이 내 종목에 남아 보이던 일)
            let sold = d.soldToday()
            if !sold.isEmpty {
                let sum = sold.reduce(0) { $0 + $1.pnl }
                HStack {
                    mt("오늘 판 \(sold.count)종목").appFont(13, .semibold)
                    Spacer()
                    mt(MockFmt.signedWon(sum)).appFont(13, .bold).monospacedDigit().foregroundStyle(Theme.change(sum))
                }
                ForEach(sold, id: \.code) { x in
                    Button { info = MockStockRef(code: x.code, name: x.name) } label: {
                        HStack(spacing: 8) {
                            mt(x.name).appFont(13).lineLimit(1)
                            Spacer(minLength: 4)
                            mt("\(x.qty)주").appFont(12).foregroundStyle(Theme.sub).monospacedDigit()
                            mt(MockFmt.signedWon(x.pnl)).appFont(12, .bold).monospacedDigit().foregroundStyle(Theme.change(x.pnl))
                            Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold)).foregroundStyle(Theme.muted)
                        }
                        .frame(minHeight: 32).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            HStack {
                Spacer()
                MockGoPill(title: "지금 팔기", enabled: !d.ledger(.rule).positions.isEmpty) { await d.runSell() }
            }
        }
    }

    private func sToggle(_ d: MockDesk, _ c: MockSellCond) {
        var cs = d.sconds
        guard let i = cs.firstIndex(where: { $0.id == c.id }) else { return }
        cs[i].on.toggle()
        d.setSellConds((cs[i].on ? "켬 · " : "끔 · ") + c.text, cs)
    }

    private func sRemove(_ d: MockDesk, _ c: MockSellCond) {
        d.setSellConds("지움 · " + c.text, d.sconds.filter { $0.id != c.id })
        if sdraft.edit == c.id { sadding = false }
    }

    private func sCommit(_ d: MockDesk, _ c: MockSellCond) {
        var cs = d.sconds
        if let i = cs.firstIndex(where: { $0.id == c.id }) {
            var x = c
            x.on = cs[i].on
            cs[i] = x
            d.setSellConds("고침 · " + c.text, cs)
        } else {
            cs.append(c)
            d.setSellConds("넣음 · " + c.text, cs)
        }
        sadding = false
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
            HStack {
                Spacer()
                HoldSeg(items: MockCalc.Unit.allCases.map { ($0, $0.name) }, selection: $unit)
            }
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
        let today = MockFmt.day(Date())
        var wait: [String: Int] = [:], sold: [String: Int] = [:]
        for o in l.pending where o.side == .sell { wait[o.code, default: 0] += o.qty }
        for e in d.events where e.ledger == l.id && e.kind == .fill && e.side == .sell && MockFmt.day(e.at) == today {
            guard let c = e.code else { continue }
            sold[c, default: 0] += e.qty ?? 0
        }
        // 들고 있는 종목만. 다 판 종목은 팔기 칸 '오늘 판'에 (금색 알약 = 오늘 일부 판 주수)
        var rows = l.positions.values.map { p -> Held in
            let now = d.s.prices[p.code] ?? p.avg
            return Held(code: p.code, name: p.name, avg: p.avg, value: Double(p.qty) * now, r: p.avg > 0 ? now / p.avg - 1 : 0,
                        wait: wait[p.code] ?? 0, sold: sold[p.code] ?? 0)
        }
        rows.sort { downFirst ? $0.r < $1.r : $0.r > $1.r }
        let pnl = l.positions.values.reduce(0) { $0 + Double($1.qty) * (d.s.prices[$1.code] ?? $1.avg) - $1.cost }
        let mx = rows.map { abs($0.r) }.max() ?? 0
        let anyPill = rows.contains { $0.wait > 0 }
        return MockListCard {
            mt("내 종목 \(rows.count)").appFont(14, .bold)
            MockSortToggle(downFirst: $downFirst)
            Spacer()
            mt(MockFmt.signedWon(pnl)).appFont(14, .semibold).monospacedDigit().lineLimit(1)
                .foregroundStyle(pnl > 0 ? Theme.up : pnl < 0 ? Theme.down : Theme.sub)
        } rows: {
            if rows.isEmpty { MockEmptyRow(text: "아직 산 종목이 없어요") }
            ForEach(rows) { h in
                Button { info = MockStockRef(code: h.code, name: h.name) } label: {
                    MockStockRow(code: h.code, name: h.name, mid: MockFmt.small(h.avg), pct: h.r,
                                 price: MockFmt.man(h.value), band: mx > 0 ? abs(h.r) / mx : 0, filled: h.sold > 0, colW: colW) {
                        if h.wait > 0 { MockQtyPill(text: "팔기 \(h.wait)주 대기", done: false).holdCol(3, colW) }
                        else if anyPill { Color.clear.frame(width: colW[3] ?? 0, height: 1) }
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func bestWorst(_ d: MockDesk) -> some View {
        let all = d.perStock()
        let best = all.filter { $0.pnl > 0 }.max { $0.pnl < $1.pnl }
        let worst = all.filter { $0.pnl < 0 }.min { $0.pnl < $1.pnl }
        return HStack(spacing: 0) {
            cell("최고", best)
            Divider().overlay(Theme.line)
            cell("최저", worst)
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
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    mt(MockFmt.signedWon(x.pnl)).appFont(14, .bold)
                    mt(MockFmt.pct(x.ret)).appFont(12, .semibold)
                }
                .monospacedDigit().foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.7)
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
                    mt("지난 \(days.count)일").appFont(14, .bold)
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

    /// 오류: 한 줄씩 (최근부터, 처음엔 접어 둔다)
    private func noteCard(_ d: MockDesk) -> some View {
        let all = d.events.reversed().filter { $0.book == .rule && $0.kind == .error }
        let shown = noteOpen ? Array(all.prefix(noteMore ? 60 : 8)) : []
        return MockListCard {
            Button { withAnimation(.snappy) { noteOpen.toggle() } } label: {
                HStack {
                    mt("오류 \(all.count)").appFont(14, .bold)
                    Spacer()
                    Image(systemName: noteOpen ? "chevron.down" : "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.muted)
                }
                .frame(minHeight: 28).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(all.isEmpty)
        } rows: {
            ForEach(shown) { e in
                VStack(spacing: 0) {
                    Divider().overlay(Theme.line)
                    HStack(spacing: 6) {
                        mt(MockFmt.stamp(e.at)).appFont(11).foregroundStyle(Theme.muted).monospacedDigit().fixedSize()
                        mt(e.text).appFont(12).foregroundStyle(Theme.sub).lineLimit(1).truncationMode(.tail)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14).frame(minHeight: 40)
                }
            }
            if noteOpen && all.count > 8 {
                VStack(spacing: 0) {
                    Divider().overlay(Theme.line)
                    Button { withAnimation(.snappy) { noteMore.toggle() } } label: {
                        mt(noteMore ? "접기" : "\(min(all.count, 60) - 8)줄 더 보기").appFont(13, .semibold).foregroundStyle(Theme.teal)
                            .frame(maxWidth: .infinity).frame(minHeight: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
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

// MARK: 모의투자 설정 (일지 머리 톱니)

struct MockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var findOpen = false
    @State private var draft = MockPieceDraft()
    @State private var adding = false
    @State private var resetAsk = false
    @State private var fees = MockDesk.shared.s.fees
    @State private var auto = MockDesk.shared.s.auto

    var body: some View {
        let d = MockDesk.shared
        VStack(spacing: 0) {
            MockSheetBar(title: "모의투자 설정") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    reviewCard(d)
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

    /// 직접 검토 (옛 '검토 문'): 켜면 고른 뒤 내가 보고 주문, 끄면 고르자마자 주문
    private func reviewCard(_ d: MockDesk) -> some View {
        let on = d.s.select.review
        return Card {
            Toggle(isOn: Binding(get: { on }, set: { v in d.edit(v ? "직접 검토 켬" : "직접 검토 끔 · 바로 주문") { $0.select.review = v } })) {
                VStack(alignment: .leading, spacing: 1) {
                    mt("직접 검토").appFont(16, .bold)
                    mt(on ? "고른 뒤 보고 주문" : "고르면 바로 주문").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .tint(Theme.teal)
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
                Stepper(value: $fees.commission, in: 0...0.005, step: 0.00001) { mt("수수료 (한쪽) " + String(format: "%.3f%%", fees.commission * 100)).appFont(14) }
                Stepper(value: $fees.sellTax, in: 0...0.01, step: 0.0005) { mt("매도 세금 " + String(format: "%.2f%%", fees.sellTax * 100)).appFont(14) }
            }
            mt("앱이 열려 있을 때만 돌아요.").appFont(12).foregroundStyle(Theme.muted)
        }
    }

    private func save() {
        let d = MockDesk.shared
        var what: [String] = []
        if fees != d.s.fees { what.append("수수료 " + String(format: "%.3f%%", fees.commission * 100) + " · 세금 " + String(format: "%.2f%%", fees.sellTax * 100)) }
        if auto != d.s.auto { what.append(auto == 0 ? "자동 끔" : "자동 \(auto)분") }
        guard !what.isEmpty else { return }
        d.edit("설정 · " + what.joined(separator: " · ")) { s in
            s.fees = fees; s.auto = auto
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
