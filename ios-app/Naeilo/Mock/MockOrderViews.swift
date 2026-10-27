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
        let l = ledger(shown)
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

    /// 오늘 판 종목 (보이는 칸 장부의 매도 체결을 종목마다 모은다, 처음 판 때 순)
    func soldToday() -> [(code: String, name: String, qty: Int, pnl: Double, basis: Double)] {
        let id = ledger(shown).id, today = MockFmt.day(Date())
        var order: [String] = [], by: [String: (name: String, qty: Int, pnl: Double, basis: Double)] = [:]
        for e in events where e.ledger == id && e.kind == .fill && e.side == .sell && MockFmt.day(e.at) == today {
            guard let c = e.code else { continue }
            if by[c] == nil { order.append(c) }
            let x = by[c] ?? (e.name ?? c, 0, 0, 0)
            by[c] = (x.name, x.qty + (e.qty ?? 0), x.pnl + (e.pnl ?? 0), x.basis + (e.basis ?? 0))
        }
        return order.map { (code: $0, name: by[$0]!.name, qty: by[$0]!.qty, pnl: by[$0]!.pnl, basis: by[$0]!.basis) }
    }

    /// 오늘 전 기록이 있는 날 (최근부터)
    func pastDays(before today: String) -> [MockDay] {
        let id = ledger(shown).id
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
    @State private var unit = MockCalc.Unit.hour   // 그래프 기본은 시간 (2026-10-26)
    @State private var downFirst = false
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
        var outside = false       // [실전] 칸: 앱이 사지 않은 토스 보유 (보기만)
        var id: String { code + (outside ? ".out" : "") }
    }

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .journal) {
            retCard(d)
            sellCard(d)
            mineCard(d)
            soldCard(d)
            bestWorst(d)
            pastCard(d)
            noteCard(d)
        }
        .onPreferenceChange(HoldColW.self) { colW = $0 }
        .sheet(item: $info) { MockStockSheet(code: $0.code, name: $0.name, fills: true) }
    }

    /// 오늘 판 종목 (최고·최저 위 따로 박스, 없으면 숨김). 누르면 매수·매도 기록
    @ViewBuilder private func soldCard(_ d: MockDesk) -> some View {
        let sold = d.soldToday()
        if !sold.isEmpty {
            let sum = sold.reduce(0) { $0 + $1.pnl }
            MockListCard {
                mt("오늘 판 종목 \(sold.count)").appFont(14, .bold)
                Spacer()
                mt(MockFmt.signedWon(sum)).appFont(14, .semibold).monospacedDigit().lineLimit(1).foregroundStyle(Theme.change(sum))
            } rows: {
                ForEach(sold, id: \.code) { x in
                    Button { info = MockStockRef(code: x.code, name: x.name) } label: {
                        MockStockRow(code: x.code, name: x.name, mid: "\(x.qty)주", pct: x.basis > 0 ? x.pnl / x.basis : 0,
                                     price: MockFmt.signedWon(x.pnl), colW: colW)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // 팔기 조건: 켠 알약에 맞는 내 종목을 판다 (선정의 고르기 반대). 돌리기가 켜져 있고 팔기 알약이 켜져 있으면 1분 주기도 같은 조건으로 장중 가격을 본다
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
            HStack {
                Spacer()
                MockGoPill(title: "지금 팔기", enabled: !d.ledger(d.shown).positions.isEmpty) { await d.runSell() }
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

    // 평가액 그래프: 종목 탭 그래프와 같은 모양 (오른쪽 세로 축 꼬리표 = 지금 평가액 · 달라진 금액 · 시작 평가액, 아래 가로 축 = 처음 칸 · 지금, 기간 단추는 아래).
    // 왼쪽 위 = 그 기간 수익률 (첫 칸 평가액 대비라 시간·일·주·월·년마다 다르다). 손가락으로 훑으면 시각 · 평가액 · 수익률
    private func retCard(_ d: MockDesk) -> some View {
        let (left, pts, dates) = series(d)
        let S = pts.first ?? 0, E = pts.last ?? 0, up = E >= S
        let r = S > 0 ? E / S - 1 : 0
        return Card {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                mt(MockFmt.pct(r)).appFont(18, .heavy).monospacedDigit().foregroundStyle(r > 0 ? Theme.up : r < 0 ? Theme.down : Theme.sub)
                mt(unit.name + " 기준").appFont(12).foregroundStyle(Theme.muted)
                Spacer()
            }
            if pts.count < 2 {
                mt("평가액 점이 쌓이면 그려요").appFont(14, .bold).foregroundStyle(Theme.sub)
                    .frame(maxWidth: .infinity, minHeight: 120)
            } else {
                TagChart(points: pts, height: 120, tags: [
                    .init(at: E, text: MockFmt.man(E), bg: Theme.ink, fg: Theme.card),
                    .init(at: E, dy: 20, text: (up ? "+" : "−") + MockFmt.man(abs(E - S)), bg: up ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2)),
                    .init(at: S, text: MockFmt.man(S), bg: TagChart.gray),
                ], left: left, right: "지금",
                         scrub: Scrub(date: { x in dates[min(dates.count - 1, max(0, Int((x * Double(dates.count - 1)).rounded())))] },
                                      value: { v in MockFmt.man(v) + " · " + MockFmt.pct(S > 0 ? v / S - 1 : 0) }))
            }
            ChipRow(items: MockCalc.Unit.allCases.map { ($0, $0.name) }, selection: $unit, fill: true)
        }
    }

    /// 칸마다 마지막 평가액 (지금 칸은 지금 평가액). 기록이 다 들어가면 장부 시작 현금부터. 첫 칸 이름 · 칸마다 시각도 함께
    private func series(_ d: MockDesk) -> (String, [Double], [Date]) {
        let l = d.ledger(d.shown)
        let take: Int
        switch unit {
        case .hour: take = 24
        case .day: take = 30
        case .week: take = 26
        case .month: take = 24
        case .year: take = 10
        }
        var order: [String] = [], last: [String: Double] = [:], at: [String: Date] = [:]
        for m in d.marks.sorted(by: { $0.at < $1.at }) {
            let k = MockCalc.bucket(m.at, unit)
            if last[k] == nil { order.append(k) }
            last[k] = m.eq; at[k] = m.at
        }
        let nowKey = MockCalc.bucket(Date(), unit)
        if last[nowKey] == nil { order.append(nowKey) }
        last[nowKey] = d.equity(d.shown); at[nowKey] = Date()
        let keys = Array(order.suffix(take))
        var eqs = keys.compactMap { last[$0] }, dates = keys.compactMap { at[$0] }
        var left = keys.first ?? ""
        // 기록이 다 들어가면 맨 앞은 시작: [모의]는 시작 현금, [실전]은 첫 평가액 점(토스 계좌, 이미 점 안에 있다)
        if order.count < take {
            if d.shown != .real { eqs.insert(l.startCash, at: 0); dates.insert(l.started, at: 0) }
            left = "시작"
        }
        return (left, eqs, dates)
    }

    private func mineCard(_ d: MockDesk) -> some View {
        let l = d.ledger(d.shown)
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
        // [실전] 칸: 실전 장부에 없는 토스 보유도 '외부'로 보기만 (팔기 조건·주문을 붙이지 않는다)
        rows += d.outside.map { x in
            let avg = x.avg ?? 0, now = d.s.prices[x.code] ?? avg
            return Held(code: x.code, name: MockDesk.name(x.code), avg: avg, value: Double(x.qty) * now, r: avg > 0 ? now / avg - 1 : 0, outside: true)
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
                if h.outside {
                    MockStockRow(code: h.code, name: h.name, mid: MockFmt.small(h.avg), pct: h.r,
                                 price: MockFmt.man(h.value), band: mx > 0 ? abs(h.r) / mx : 0, filled: false, colW: colW) {
                        MockTag(text: "외부", color: Theme.muted)
                    }
                } else {
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
    }

    private func bestWorst(_ d: MockDesk) -> some View {
        let all = d.perStock()
        let best = all.filter { $0.pnl > 0 }.max { $0.pnl < $1.pnl }
        let worst = all.filter { $0.pnl < 0 }.min { $0.pnl < $1.pnl }
        return HStack(spacing: 0) {
            Button { if let x = best { info = MockStockRef(code: x.code, name: x.name) } } label: { cell("최고", best) }
                .buttonStyle(.plain).disabled(best == nil)
            Divider().overlay(Theme.line)
            Button { if let x = worst { info = MockStockRef(code: x.code, name: x.name) } } label: { cell("최저", worst) }
                .buttonStyle(.plain).disabled(worst == nil)
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
        .contentShape(Rectangle())
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

    /// 오류 · 규칙 바뀜: 한 줄씩 (최근부터, 처음엔 접어 둔다. 규칙 줄 앞엔 작게 '규칙')
    private func noteCard(_ d: MockDesk) -> some View {
        let all = d.events.reversed().filter { ($0.book == d.shown && $0.kind == .error) || $0.kind == .rules }
        let errN = all.filter { $0.kind == .error }.count
        let shown = noteOpen ? Array(all.prefix(noteMore ? 60 : 8)) : []
        return MockListCard {
            Button { withAnimation(.snappy) { noteOpen.toggle() } } label: {
                HStack {
                    mt("오류 \(errN)").appFont(14, .bold)
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
                        if e.kind == .rules { mt("규칙").appFont(10, .bold).foregroundStyle(Theme.rule).fixedSize() }
                        mt(e.text).appFont(12).foregroundStyle(Theme.sub).lineLimit(1).truncationMode(.tail)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14).frame(minHeight: 40)
                    .mockSwipe("지우기") { d.deleteNotes([e.id]) }
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

// MARK: 계좌 (투자 모드 5번째 칸. [모의]·[실전]을 따라 그 장부를 그림으로)

struct MockAccountView: View {
    @AppStorage("mockAutoLast") private var autoLast = 10   // 돌리기 멈춤을 풀 때 쓸 지난 간격 (분)
    @State private var settings = false
    @State private var liveAsk = false
    @State private var note: String?
    @State private var editRow: MockPickRow?
    @State private var sel: UUID?

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .account, gear: { settings = true }) {
            ordersCard(d)
            gapCard(d)
            lights(d)
        }
        .task { await d.refreshAccount() }
        .sheet(item: $editRow) { MockRowEdit(row: $0).presentationDetents([.large]) }
        .sheet(isPresented: $settings) { MockSettingsView() }
        .confirmationDialog(Text(verbatim: "실전을 켤까요?"), isPresented: $liveAsk, titleVisibility: .visible) {
            Button(role: .destructive) { note = d.setLive(true) } label: { mt("실전 켜기 (실제 주문)") }
        }
    }

    private func head(_ t: String, _ r: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            mt(t).appFont(15, .bold)
            Spacer()
            mt(r).appFont(12).foregroundStyle(Theme.sub).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }
    }

    // MARK: 오늘 주문 — 끝 모습 띠 + 장 시간줄 점

    /// 주문 한 건의 끝 모습
    enum End: Int, CaseIterable { case fill, wait, cancel, reject
        var name: String { ["체결", "대기", "취소", "거부"][rawValue] }
    }
    struct Dot: Identifiable { var id: UUID; var at: Date; var side: MockSide; var won: Double; var end: End }

    /// 오늘 주문을 점으로: 접수 기록 하나가 점 하나, 접수 전에 거부된 것도 점 하나
    static func dots(_ d: MockDesk) -> [Dot] {
        let l = d.ledger(d.shown), today = MockFmt.day(Date())
        let es = d.ledgerEvents(d.shown).filter { MockFmt.day($0.at) == today && !$0.test }
        let wait = Set(l.pending.map(\.id))
        var by: [UUID: [MockEvent]] = [:]
        for e in es where e.kind != .order { if let r = e.ref { by[r, default: []].append(e) } }
        let orders = es.filter { $0.kind == .order && $0.qty != nil && !$0.text.hasPrefix("정정") }
        let ids = Set(orders.map(\.id))
        var out = orders.map { o -> Dot in
            let after = by[o.id] ?? []
            let px = after.first { $0.kind == .fill }?.price ?? o.code.flatMap { d.s.prices[$0] } ?? 0
            let end: End = wait.contains(o.id) ? .wait : after.contains { $0.kind == .fill } ? .fill
                : after.contains { $0.kind == .cancel } ? .cancel : after.contains { $0.kind == .reject } ? .reject : .fill
            return Dot(id: o.id, at: o.at, side: o.side ?? .buy, won: px * Double(o.qty ?? 0), end: end)
        }
        out += es.filter { $0.kind == .reject && !($0.ref.map { ids.contains($0) } ?? false) }.map {
            Dot(id: $0.id, at: $0.at, side: $0.side ?? .buy, won: ($0.code.flatMap { d.s.prices[$0] } ?? 0) * Double($0.qty ?? 0), end: .reject)
        }
        return out.sorted { $0.at < $1.at }
    }

    private func ordersCard(_ d: MockDesk) -> some View {
        let ds = Self.dots(d)
        let n = End.allCases.map { e in ds.filter { $0.end == e }.count }
        let o = sel.flatMap { id in d.ledger(d.shown).pending.first { $0.id == id } }
        return Card {
            head("오늘 주문", "\(ds.count)건")
            if ds.isEmpty {
                mt("아직 없음").appFont(13).foregroundStyle(Theme.muted)
            } else {
                // 끝 모습 띠
                GeometryReader { g in
                    let on = End.allCases.filter { n[$0.rawValue] > 0 }
                    let w = g.size.width - 3 * CGFloat(on.count - 1)
                    HStack(spacing: 3) {
                        ForEach(on, id: \.self) { e in Self.seg(e).frame(width: w * CGFloat(n[e.rawValue]) / CGFloat(ds.count)) }
                    }
                }
                .frame(height: 14)
                HStack(spacing: 12) {
                    ForEach(End.allCases, id: \.self) { e in
                        HStack(spacing: 4) { Self.mark(e, side: .buy, size: 10, gray: true); mt(e.name + " \(n[e.rawValue])") }
                            .appFont(12).foregroundStyle(Theme.sub)
                    }
                }
            }
            line(ds)
            if let o {
                HStack(spacing: 8) {
                    Self.mark(.wait, side: o.side, size: 12)
                    mt(o.name + " · " + o.side.name + " \(o.qty)주 · 대기").appFont(13, .semibold).lineLimit(1).minimumScaleFactor(0.8)
                    Spacer(minLength: 4)
                    Button { editRow = MockPickRow(code: o.code, name: o.name, qty: o.qty, kind: .wait, price: d.s.prices[o.code] ?? o.limit ?? 0, pct: 0, side: o.side, orderId: o.id) } label: {
                        MockTag(text: "정정", color: Theme.teal)
                    }
                    .buttonStyle(.plain)
                    Button { d.cancel(d.shown, orderId: o.id); sel = nil } label: { MockTag(text: "취소", color: Theme.up) }
                        .buttonStyle(.plain)
                }
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Theme.line, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    /// 띠 한 칸
    @ViewBuilder static func seg(_ e: End) -> some View {
        let r = RoundedRectangle(cornerRadius: 4)
        switch e {
        case .fill: r.fill(Theme.ink)
        case .wait: r.strokeBorder(Theme.ink, lineWidth: 2)
        case .cancel: r.fill(Theme.muted.opacity(0.35))
        case .reject: r.fill(Theme.orange)
        }
    }

    /// 점 하나: 체결 꽉 참 · 대기 빈 동그라미 · 취소 ✕ · 거부 주황 테두리 !
    @ViewBuilder static func mark(_ e: End, side: MockSide, size: CGFloat, gray: Bool = false) -> some View {
        let c = gray ? Theme.ink : side == .buy ? Theme.up : Theme.down
        switch e {
        case .fill: Circle().fill(c).frame(width: size, height: size)
        case .wait: Circle().strokeBorder(c, lineWidth: max(1.5, size / 9)).frame(width: size, height: size)
        case .cancel: Image(systemName: "xmark").font(.system(size: size * 0.8, weight: .bold)).foregroundStyle(Theme.muted).frame(width: size, height: size)
        case .reject:
            Circle().strokeBorder(Theme.orange, lineWidth: max(1.5, size / 9)).frame(width: size, height: size)
                .overlay { mt("!").font(.system(size: size * 0.6, weight: .heavy)).foregroundStyle(Theme.orange) }
        }
    }

    /// 9:00~15:30 시간줄. 산 주문은 위, 판 주문은 아래. 점 크기 = 주문 금액
    private func line(_ ds: [Dot]) -> some View {
        let cal = Calendar.current, now = Date()
        let open = cal.date(bySettingHour: 9, minute: 0, second: 0, of: now) ?? now
        let span = 390.0 * 60
        let x = { (t: Date) in min(1, max(0, t.timeIntervalSince(open) / span)) }
        let top = max(1, ds.map(\.won).max() ?? 1)
        return GeometryReader { g in
            let w = g.size.width - 16, mid = 52.0
            let px = { (t: Date) in 8 + w * x(t) }
            ZStack(alignment: .topLeading) {
                Rectangle().fill(Theme.line).frame(width: w, height: 2).position(x: 8 + w / 2, y: mid)
                Rectangle().fill(Theme.muted).frame(width: w * x(now), height: 2).position(x: 8 + w * x(now) / 2, y: mid)
                if x(now) > 0 && x(now) < 1 {
                    Rectangle().fill(Theme.orange).frame(width: 1.5, height: 70).position(x: px(now), y: mid)
                }
                ForEach(ds) { dot in
                    let s = 10 + 12 * sqrt(dot.won / top)
                    Button { sel = dot.end == .wait ? (sel == dot.id ? nil : dot.id) : nil } label: {
                        Self.mark(dot.end, side: dot.side, size: s)
                            .padding(6).contentShape(Rectangle())
                            .overlay { if sel == dot.id { Circle().stroke((dot.side == .buy ? Theme.up : Theme.down).opacity(0.25), lineWidth: 4).frame(width: s + 8, height: s + 8) } }
                    }
                    .buttonStyle(.plain)
                    .position(x: px(dot.at), y: dot.side == .buy ? mid - 22 : mid + 22)
                }
                ForEach(Array(["9시", "11시", "13시", "15:30"].enumerated()), id: \.offset) { i, t in
                    mt(t).appFont(10).foregroundStyle(Theme.muted)
                        .position(x: 8 + w * [0, 120, 240, 390][i] / 390, y: 112)
                }
            }
        }
        .frame(height: 120)
    }

    // MARK: 체결 차이 — 체결 한 건이 막대 하나. 비싸게 사면(불리) 위, 싸게 사면 아래

    private func gapCard(_ d: MockDesk) -> some View {
        let today = MockFmt.day(Date())
        let fs = d.ledgerEvents(d.shown).filter { $0.kind == .fill && $0.expect != nil && $0.price != nil && !$0.test }
        let gap = { (e: MockEvent) in MockCalc.slipGap(side: e.side ?? .buy, expect: e.expect ?? 0, actual: e.price ?? 0) }
        let now = fs.filter { MockFmt.day($0.at) == today }
        let past = Array(fs.filter { MockFmt.day($0.at) != today }.suffix(12))
        let nowBars = Array(now.suffix(20))   // 막대는 오늘 최근 20건까지 (평균·금액은 오늘 모두)
        let sp: CGFloat = nowBars.count + past.count > 10 ? 3 : 8   // 막대가 많으면 붙여서 카드 폭 안에
        let won = now.reduce(0.0) { $0 + (($1.price ?? 0) - ($1.expect ?? 0)) * Double($1.qty ?? 0) * ($1.side == .sell ? -1 : 1) }
        let avg = now.isEmpty ? 0 : now.map(gap).reduce(0, +) / Double(now.count)
        let top = max(0.002, (now + past).map { abs(gap($0)) }.max() ?? 0)
        return Card {
            head("체결 차이", now.isEmpty ? "오늘 없음" : "평균 " + MockFmt.pct(avg) + " · " + MockFmt.signedWon(-won))
            if fs.isEmpty {
                mt("아직 체결 없음").appFont(13).foregroundStyle(Theme.muted)
            } else {
                HStack(alignment: .center, spacing: sp) {
                    VStack(alignment: .leading) {
                        mt("비싸게").foregroundStyle(Theme.orange)
                        Spacer()
                        mt("싸게").foregroundStyle(Theme.teal)
                    }
                    .appFont(11, .semibold).frame(height: 96).fixedSize(horizontal: true, vertical: false).padding(.trailing, 8 - sp)
                    ForEach(nowBars) { bar(gap($0), top) }
                    if !now.isEmpty && !past.isEmpty { Rectangle().fill(Theme.line).frame(width: 1, height: 70).padding(.horizontal, 4) }
                    ForEach(past) { bar(gap($0), top).opacity(0.45) }
                    Spacer(minLength: 0)
                }
                .background { Rectangle().fill(Theme.line).frame(height: 1.5) }
                HStack(spacing: 0) {
                    if !now.isEmpty { mt("오늘").lineLimit(1).frame(maxWidth: CGFloat(nowBars.count) * (18 + sp), alignment: .leading) }
                    if !past.isEmpty { mt("지난 날") }
                    Spacer()
                }
                .appFont(11).foregroundStyle(Theme.muted).padding(.leading, 46)
            }
        }
    }

    private func bar(_ g: Double, _ top: Double) -> some View {
        let h = max(3, 46 * min(1, abs(g) / top))
        let r = g > 0 ? UnevenRoundedRectangle(topLeadingRadius: 4, topTrailingRadius: 4) : UnevenRoundedRectangle(bottomLeadingRadius: 4, bottomTrailingRadius: 4)
        return r.fill(g > 0 ? Theme.orange : Theme.teal).frame(maxWidth: 18).frame(height: h)
            .offset(y: g > 0 ? -h / 2 : h / 2).frame(maxWidth: 18).frame(height: 96)
    }

    // MARK: 불빛 둘 — [실전] 실전 자동주문 · 모두 멈춤 / [모의] 돌리기 주문 · 돌리기 멈춤

    /// 자동주문 불빛은 '고르고 주문' 구간 밖이면 정지로 보인다 (저장된 켬·끔은 그대로, 누르면 켬·끔이 바뀐다)
    private func lights(_ d: MockDesk) -> some View {
        TimelineView(.everyMinute) { ctx in lightsBody(d, seg: d.seg(ctx.date)) }
    }

    private func lightsBody(_ d: MockDesk, seg: MockClock.Seg) -> some View {
        let stop = !seg.buys, stopText = "자동 정지 · " + seg.name
        return VStack(spacing: 8) {
            HStack(spacing: 12) {
                if d.shown == .real {
                    light("실전 자동주문", d.live ? (stop ? stopText : "켬") : "끔", icon: "power", on: d.live && !stop, color: Theme.orange) {
                        if d.live { note = d.setLive(false) } else { await d.checkLive(); liveAsk = true }
                    }
                    light("모두 멈춤", d.halted ? "멈춤" : "도는 중", icon: "pause.fill", on: d.halted, color: Theme.up) {
                        if d.halted { d.resumeAll() } else { d.haltAll() }
                    }
                } else {
                    // 모의: 돌리기(한 바퀴 자동)에 주문까지 · 돌리기 자체를 멈춤 (풀면 지난 간격으로)
                    light("모의 자동주문", d.autoOrder ? (stop ? stopText : "켬") : "끔", icon: "power", on: d.autoOrder && !stop, color: Theme.teal) {
                        let v = !d.autoOrder
                        d.edit(v ? "돌리기 주문 켬" : "돌리기 주문 끔") { $0.autoOrder = v }
                    }
                    light("돌리기 멈춤", d.s.auto == 0 ? "멈춤" : "\(d.s.auto)분마다 도는 중", icon: "pause.fill", on: d.s.auto == 0, color: Theme.down) {
                        if d.s.auto == 0 {
                            let m = autoLast > 0 ? autoLast : 10
                            d.edit("자동 \(m)분") { $0.auto = m }
                        } else {
                            autoLast = d.s.auto
                            d.edit("자동 끔") { $0.auto = 0 }
                        }
                    }
                }
            }
            if let note { mt(note).appFont(12).foregroundStyle(Theme.up) }
        }
    }

    private func light(_ t: String, _ state: String, icon: String, on: Bool, color: Color, _ act: @escaping () async -> Void) -> some View {
        Button { Task { await act() } } label: {
            VStack(spacing: 10) {
                ZStack {
                    Circle().fill(on ? color : Theme.line).frame(width: 64, height: 64)
                        .shadow(color: on ? color.opacity(0.55) : .clear, radius: 12)
                    if !on { Circle().strokeBorder(Theme.muted.opacity(0.4), lineWidth: 2).frame(width: 64, height: 64) }
                    Image(systemName: icon).font(.system(size: 24, weight: .bold)).foregroundStyle(on ? .white : Theme.muted)
                }
                .padding(4)
                mt(t).appFont(15, .bold).foregroundStyle(Theme.ink)
                mt(state).appFont(12, .semibold).foregroundStyle(on ? color : Theme.sub)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 18)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay { RoundedRectangle(cornerRadius: 16).stroke(Theme.border, lineWidth: 1) }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: t + " · " + state))
    }
}

// MARK: 모의투자 설정 (계좌 머리 톱니)

struct MockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var findOpen = false
    @State private var draft = MockPieceDraft()
    @State private var adding = false
    @State private var resetAsk = false
    @State private var fees = MockDesk.shared.s.fees
    @State private var auto = MockDesk.shared.s.auto
    @State private var guards = MockDesk.shared.guards
    @State private var liveAsk = false
    @State private var liveNote: String?

    var body: some View {
        let d = MockDesk.shared
        VStack(spacing: 0) {
            MockSheetBar(title: "모의투자 설정") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    exceptCard(d)
                    ledgerCard(d)
                    runCard
                    guardCard(d)
                    liveCard(d)
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
            Button(role: .destructive) { d.reset(d.shown) } label: { mt("처음부터 (옛 장부는 보관)") }
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
        let l = d.ledger(d.shown)
        return Card {
            HStack {
                mt("장부").appFont(16, .bold)
                Spacer()
                mt(d.shown.name + " · 시작 \(MockFmt.day(l.started)) · 평가 " + MockFmt.man(d.equity(d.shown))).appFont(12).foregroundStyle(Theme.muted)
            }
            // 장부(state.json)와 기록(journal.jsonl) 파일을 공유 시트로 내보낸다
            ShareLink(items: MockDesk.exportFiles) {
                HStack {
                    mt("내보내기").appFont(15, .semibold).foregroundStyle(Theme.ink)
                    Spacer()
                    mt("장부 · 기록 파일").appFont(12).foregroundStyle(Theme.muted)
                }
                .frame(minHeight: 40).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
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
    /// 실전 스위치: 기본 끔. 켤 때는 확인을 한 번 더 받는다 (개발자 서버의 실전 스위치·계좌·키가 준비돼야 켜진다)
    private func liveCard(_ d: MockDesk) -> some View {
        Card {
            Toggle(isOn: Binding(get: { d.live }, set: { v in if v { liveAsk = true } else { liveNote = d.setLive(false) } })) {
                VStack(alignment: .leading, spacing: 1) {
                    mt("실전 (토스 주문)").appFont(16, .bold)
                    mt(d.live ? "켜짐 · 실제 돈으로 주문해요" : "꺼짐 · 모의 브로커").appFont(12).foregroundStyle(d.live ? Theme.up : Theme.muted)
                }
            }
            .tint(Theme.up)
            // 앱을 켤 때 시작 칸 (기본 모의)
            Toggle(isOn: Binding(get: { d.s.startReal == true }, set: { v in d.setView { $0.startReal = v ? true : nil } })) {
                mt("시작 실전").appFont(14)
            }
            .tint(Theme.up)
            mt("서버 " + (d.liveBlock ?? "실전 준비됨")).appFont(12).foregroundStyle(Theme.muted)
            if let liveNote { mt(liveNote).appFont(12).foregroundStyle(Theme.up) }
        }
        .task { await d.checkLive() }
        .confirmationDialog(Text(verbatim: "실전을 켤까요?"), isPresented: $liveAsk, titleVisibility: .visible) {
            Button(role: .destructive) { liveNote = d.setLive(true) } label: { mt("실전 켜기 (실제 주문)") }
            Button(role: .cancel) {} label: { mt("그만두기") }
        } message: {
            mt("토스 계좌로 실제 돈이 오가는 주문이 나가요. 장중에만, 안전장치 안에서 주문해요.")
        }
    }

    /// 안전장치: 하루 손실 한도 · 한 번 주문 상한 · 모두 멈춤
    private func guardCard(_ d: MockDesk) -> some View {
        Card {
            HStack {
                mt("안전장치").appFont(16, .bold)
                Spacer()
                if d.dayLossHit { mt("오늘 손실 한도 넘음 · 새로 사지 않음").appFont(12, .semibold).foregroundStyle(Theme.up) }
            }
            Stepper(value: $guards.dayLoss, in: 0.01...0.10, step: 0.005) {
                mt("하루 손실 한도 −" + MockFmt.num(guards.dayLoss * 100) + "%").appFont(14)
            }
            Stepper(value: $guards.orderCap, in: 500_000...10_000_000, step: 500_000) {
                mt("한 번 매수 주문 ≤ " + MockFmt.man(guards.orderCap)).appFont(14)
            }
            Stepper(value: Binding(get: { guards.perSec }, set: { guards.apiPerSec = $0 }), in: 1...20, step: 1) {
                mt("토스 시세 호출 초당 " + MockFmt.num(guards.perSec) + "번까지").appFont(14)
            }
            Toggle(isOn: Binding(get: { d.fillAlert }, set: { d.setFillAlert($0) })) {
                mt("체결 알림 (체결·부분 체결·거부)").appFont(14)
            }
            .tint(Theme.teal)
            ForEach(MockBook.allCases, id: \.self) { b in
                if let c = d.check(b) {
                    mt("잔고 맞추기 · " + b.name + " · " + MockFmt.stamp(c.at) + " · " + c.text).appFont(12)
                        .foregroundStyle(c.ok ? Theme.muted : Theme.up)
                }
            }
            Button { d.runCheck() } label: { mt("지금 잔고 맞춰 보기").appFont(13, .semibold).foregroundStyle(Theme.teal) }
                .buttonStyle(.plain)
            Divider().overlay(Theme.line)
            Button { if d.halted { d.resumeAll() } else { d.haltAll() } } label: {
                mt(d.halted ? "다시 시작" : "모두 멈춤").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(d.halted ? Theme.teal : Theme.up, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            mt(d.halted ? "멈춤 중: 한 바퀴·돌리기·자동 팔기와 규칙 주문을 막아요. 손 주문은 돼요."
                        : "누르면 모든 대기 주문을 취소하고 자동매매를 멈춰요.").appFont(12).foregroundStyle(Theme.muted)
        }
    }

    private var runCard: some View {
        Card {
            mt("돌리기").appFont(16, .bold)
            MockCycleLine(order: MockDesk.shared.autoOrder, setOrder: { v in MockDesk.shared.edit(v ? "돌리기 주문 켬" : "돌리기 주문 끔") { $0.autoOrder = v } },
                          sell: MockDesk.shared.autoSell) { v in MockDesk.shared.edit(v ? "돌리기 팔기 켬" : "돌리기 팔기 끔") { $0.autoSell = v } }
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
        if guards != d.guards {
            what.append("하루 손실 한도 −" + MockFmt.num(guards.dayLoss * 100) + "% · 한 번 주문 " + MockFmt.man(guards.orderCap) + " · 시세 호출 초당 " + MockFmt.num(guards.perSec) + "번")
            let n = guards.perSec
            Task { await MockGate.shared.set(n) }
        }
        guard !what.isEmpty else { return }
        d.edit("설정 · " + what.joined(separator: " · ")) { s in
            s.fees = fees; s.auto = auto; s.guards = guards
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
        // 투자 모드에서는 미션 칸 자리 (미션은 투자 모드를 끄면 돌아온다). 계좌도 [모의]·[실전]을 따른다
        NavigationStack { MockAccountView() }
            .tabItem { Label { mt(MockTab.account.name) } icon: { Image(systemName: MockTab.account.icon) } }.tag(Tab.board)
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
            // 멈춘 훑기는 앱이 다시 앞에 오면 이어 받는다
            .onChange(of: phase) { _, p in if p == .active && d.active { Task { await d.resume() } } }
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
