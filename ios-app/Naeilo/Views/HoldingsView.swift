import SwiftUI

// 종목 탭: 위에 고정 그래프 + 기간 칩 하나로 아래 목록도 같이 바뀐다
struct HoldingsView: View {
    @Environment(AppModel.self) private var m
    @State private var period: Period = .y1
    @Environment(\.dynamicTypeSize) private var dts

    var body: some View {
        let rows = m.rows
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader().padding(.horizontal, -16)
                Text("내 종목").appFont(22, .bold)
                Text("\(rows.count)종목 · 평가액 \(AppModel.man(m.total)) · \(AppModel.sgn(m.ret))")
                    .appFont(14).foregroundStyle(Theme.sub)
                totalChart(rows)
                ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true)
                HStack {
                    Spacer()
                    Menu {
                        ForEach(HoldSort.allCases, id: \.self) { c in
                            Button { m.holdSort = c } label: {
                                if c == m.holdSort { Label(c.label, systemImage: "checkmark") } else { Text(c.label) }
                            }
                        }
                    } label: {
                        Label(m.holdSort.label, systemImage: "arrow.up.arrow.down").appFont(13, .bold)
                            .foregroundStyle(Theme.teal).frame(minHeight: 32)
                    }
                    .accessibilityLabel("정렬: \(m.holdSort.label)")
                }
                VStack(spacing: 0) {
                    ForEach(Array(m.sortedRows.enumerated()), id: \.element.id) { i, r in
                        if i > 0 { Divider().overlay(Theme.line) }
                        NavigationLink(value: r.sym) { row(r) }.buttonStyle(.plain)
                    }
                }
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
                NavigationLink(value: "add") {
                    Label("종목 추가", systemImage: "plus").appFont(15, .bold)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .foregroundStyle(Theme.teal)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                }.buttonStyle(.plain)
                Text("작은 그래프는 \(period == .d1 ? "전일 종가와 지금 가격" : period == .w1 ? "최근 5개 종가와 지금" : period.label + " 가격 흐름")이고, 회색 점선은 내 평균 단가예요(그 기간 가격 범위 안에 있을 때만). 오르면 빨강, 내리면 파랑이에요. 미국 종목은 지금 가격, 한국 종목은 전일 종가예요. 여러 종목 한 번에 넣기와 증권사 파일은 PC naeilo.com에서 해요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3)
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: period) }
        .navigationDestination(for: String.self) { _ in AddHoldingView() }
    }

    private func totalChart(_ rows: [AppModel.Row]) -> some View {
        let series = rows.map { r in m.prices.series(r.sym, period: period).map { m.krw(r.sym, $0 * r.h.qty) } }
        let n = series.map(\.count).min() ?? 0
        let tot = (0..<n).map { i in series.reduce(0) { $0 + $1[i] } }
        let chg = (tot.last ?? 0) / max(1, tot.first ?? 1) - 1
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("전체 평가액 · \(period == .d1 ? "전일 종가→지금" : period.label)").appFont(13, .semibold).foregroundStyle(Theme.sub)
                Spacer()
                Text(AppModel.sgn(chg)).appFont(15, .bold).foregroundStyle(Theme.change(chg))
            }
            Sparkline(points: tot, avg: m.cost, lineWidth: 2.2, showEndDot: true).frame(height: 120)
            Text("점선은 들어간 돈 \(AppModel.man(m.cost)) (그래프 범위 안일 때만)").appFont(12).foregroundStyle(Theme.muted)
        }
    }

    private func row(_ r: AppModel.Row) -> some View {
        let pts = m.prices.series(r.sym, period: period)
        let chg = (pts.last ?? 1) / (pts.first ?? 1) - 1
        let name = HStack(spacing: 10) {
            LogoTile(symbol: r.id, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(r.sym.name).appFont(15, .bold)
                Text("\(r.id) · \(AppModel.price(r.sym, r.sym.last)) · \(AppModel.pct(r.value / max(1, m.total)))")
                    .appFont(12).foregroundStyle(Theme.sub)
            }
        }
        let amount = VStack(alignment: .trailing, spacing: 2) {
            Text(AppModel.man(r.value)).appFont(15, .bold).monospacedDigit().lineLimit(1)
            Text(AppModel.sgn(chg)).appFont(13, .bold).foregroundStyle(Theme.change(chg))
        }
        return Group {
            if dts.isAccessibilitySize {
                // 아주 큰 글자: 이름 줄 아래에 그래프와 금액
                VStack(alignment: .leading, spacing: 8) {
                    name
                    HStack { Sparkline(points: pts, avg: r.h.avg).frame(height: 36); amount }
                }
                .padding(.vertical, 10)
            } else {
                HStack(spacing: 10) {
                    name
                    Spacer(minLength: 4)
                    Sparkline(points: pts, avg: r.h.avg).frame(width: 64, height: 30)
                    amount.frame(minWidth: 72, alignment: .trailing)
                }
            }
        }
        .padding(.horizontal, 14).frame(minHeight: 64)
        .contentShape(Rectangle())
    }
}

struct HoldingDetailView: View {
    @Environment(AppModel.self) private var m
    let sym: Symbol
    @State var period: Period
    @State private var editing = false
    @State private var opened = false

    var body: some View {
        let h = m.holdings.first { $0.symbol == sym.id } ?? Holding(symbol: sym.id, qty: 0, avg: sym.close)
        let pts = m.prices.series(sym, period: period)
        let chg = (pts.last ?? 1) / (pts.first ?? 1) - 1
        let val = m.krw(sym, h.qty * sym.last), cost = m.costKrw(sym, h.qty * h.avg)
        let r = cost > 0 ? val / cost - 1 : 0, need = h.avg / sym.last - 1
        let avgIn = period == .y1 || period == .y3 || (h.avg >= (pts.min() ?? 0) && h.avg <= (pts.max() ?? 0))
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    LogoTile(symbol: sym.id, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(sym.name).appFont(22, .bold)
                        Text(([sym.id, sym.market] + (sym.sector.isEmpty ? [] : [sym.sector])).joined(separator: " · ")).appFont(13).foregroundStyle(Theme.sub)
                    }
                }
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        Text(AppModel.man(val)).appFont(26, .bold)
                        Text(AppModel.sgn(r)).appFont(15, .bold).foregroundStyle(Theme.change(r))
                        Spacer()
                        Text("\(period == .d1 ? "오늘" : period.label) \(AppModel.sgn(chg))")
                            .appFont(13, .bold).foregroundStyle(Theme.change(chg))
                    }
                    Sparkline(points: pts, avg: avgIn ? h.avg : nil, lineWidth: 2.2, showEndDot: true).frame(height: 116)
                    Text(avgIn ? "점선은 평균 단가 \(AppModel.price(sym, h.avg))" : "평균 단가 \(AppModel.price(sym, h.avg))는 이 범위 밖")
                        .appFont(12).foregroundStyle(Theme.muted)
                }
                ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true)
                Card(padding: 0) {
                    VStack(spacing: 0) {
                        kv("보유 수량", h.qty.formatted() + "주")
                        kv("평균 단가", AppModel.price(sym, h.avg))
                        kv(sym.quote.live ? "지금 가격" : "전일 종가", AppModel.price(sym, sym.last) + (sym.quote.live ? " (\(AppModel.sgn(sym.quote.change)))" : ""))
                        kv(need > 0 ? "본전까지" : "본전 대비", need > 0.0005 ? "+" + String(format: "%.1f", need * 100) + "% 올라야 해요"
                           : need > -0.0005 ? "본전과 같아요" : "본전보다 " + String(format: "%.1f", -need * 100) + "% 위")
                        kv("비중", m.total > 0 ? AppModel.pct(val / m.total) : "-", last: sym.sector.isEmpty)
                        if !sym.sector.isEmpty { kv("업종", sym.sector, last: true) }
                    }
                }
                if h.qty > 0 {
                    NavigationLink { HoldingEditView(sym: sym) } label: {
                        Label("수량·단가 고치기", systemImage: "pencil").appFont(15, .bold)
                            .frame(maxWidth: .infinity, minHeight: 48).foregroundStyle(Theme.teal)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                    }.buttonStyle(.plain)
                }
                if let p = Sample.profiles[sym.id] { profile(p) } else { noProfile }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle(sym.id).navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $editing) { HoldingEditView(sym: sym) }
        .onAppear { if UserDefaults.standard.string(forKey: "editMode") != nil && !opened { opened = true; editing = true } }
    }

    private func kv(_ k: String, _ v: String, last: Bool = false) -> some View {
        VStack(spacing: 0) {
            HStack { Text(k).foregroundStyle(Theme.sub); Spacer(); Text(v).fontWeight(.semibold) }
                .appFont(14).padding(.horizontal, 16).frame(minHeight: 44)
            if !last { Divider().overlay(Theme.line).padding(.horizontal, 16) }
        }
    }

    /// 소개를 아직 안 쓴 종목: 아는 것만 (이름·시장·업종) 보여 주고 비었다고 말한다
    private var noProfile: some View {
        Card {
            Text("이 종목은").appFont(15, .bold)
            ForEach([("이름", sym.name), ("시장", sym.market)] + (sym.sector.isEmpty ? [] : [("업종", sym.sector)]), id: \.0) { k, v in
                HStack(alignment: .top) { Text(k).foregroundStyle(Theme.sub).frame(width: 72, alignment: .leading); Text(v) }.appFont(13)
            }
            Text("대표·하는 일·비전 소개는 아직 준비하지 않은 종목이에요.").appFont(12).foregroundStyle(Theme.muted)
        }
    }

    private func profile(_ p: Profile) -> some View {
        Card {
            HStack {
                Text(p.etf ? "이 ETF는" : "이 회사는").appFont(15, .bold)
                Spacer()
                if p.virtual {
                    Text("가상 종목").appFont(11, .bold).foregroundStyle(Color(hex: 0x5A3E00, dark: 0xF0D28A))
                        .padding(.horizontal, 8).padding(.vertical, 2).background(Color(hex: 0xFFF1C9, dark: 0x4A3D16), in: Capsule())
                }
            }
            Text(p.what).appFont(14).lineSpacing(3)
            VStack(alignment: .leading, spacing: 2) {
                Text(p.etf ? "따라가는 지수" : "비전").appFont(12, .bold).foregroundStyle(Theme.teal)
                Text(p.vision).appFont(14, .semibold)
            }
            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
            let rows: [(String, String)] = [p.ceo, (p.hqKey ?? (p.etf ? "시장" : "본사"), p.hq), (p.etf ? "상장" : "설립", p.since)] + (p.extra.map { [$0] } ?? [])
            ForEach(rows, id: \.0) { k, v in
                HStack(alignment: .top) { Text(k).foregroundStyle(Theme.sub).frame(width: 72, alignment: .leading); Text(v) }
                    .appFont(13)
            }
            Text(p.virtual ? "드링커는 『중첩된 현실』 속 회사를 바탕으로 한 가상 종목이에요. 가격 흐름은 내 지난 자산 기록을 비율로 바꾼 값이고, 투자 권유가 아니에요."
                 : "소개는 공개 정보 기준(2026년 10월 확인)이에요. 실제 앱은 공시와 데이터 제공처 값을 받아 와요. 종목 추천이 아니에요.")
                .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
        }
    }
}

// 종목 추가: 한글·영문·티커·종목 코드 검색 → 수량·평균 단가
// 앱 안 목록(예시 종목 + 한글 이름 목록)은 바로, Yahoo 검색은 입력을 멈추면 아래에 더 붙는다 (StockSearch)
struct AddHoldingView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var query = UserDefaults.standard.string(forKey: "searchTest") ?? ""
    @State private var remote: [StockHit] = []
    @State private var searching = false
    @State private var picked: Symbol? = nil
    @State private var loading: String? = nil        // 시세 받는 중인 종목
    @State private var failed: String? = nil
    @State private var qty = ""
    @State private var avg = ""
    @State private var warn = false
    @FocusState private var focus: Int?

    var body: some View {
        let local = StockSearch.local(query)
        let more = remote.filter { r in !local.contains { $0.id == r.id } }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("종목 추가").appFont(22, .bold)
                TextField("한글·영문 이름, 티커, 종목 코드", text: $query)
                    .autocorrectionDisabled().textInputAutocapitalization(.never)
                    .focused($focus, equals: 0)
                    .padding(.horizontal, 14).frame(minHeight: 48)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
                if let p = picked {
                    form(p)
                } else {
                    if let f = failed {
                        Text(f).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true)
                    }
                    if !local.isEmpty { list(local) }
                    if !more.isEmpty {
                        Text("Yahoo에서 더 찾은 종목").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 4)
                        list(more)
                    }
                    if searching {
                        HStack(spacing: 8) { ProgressView(); Text("Yahoo에서 찾는 중").appFont(13).foregroundStyle(Theme.sub) }
                    } else if local.isEmpty && more.isEmpty {
                        Text("찾는 종목이 없어요. 미국 주식·ETF와 코스피·코스닥 종목을 한글 이름, 영문 이름, 티커, 종목 코드로 찾을 수 있어요. 한글 이름은 자주 찾는 종목만 알아요.")
                            .appFont(14).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    }
                    Text("시세는 테스트용 Yahoo 중계 자료예요. 미국 종목은 지금 가격, 한국 종목은 전일 종가예요.")
                        .appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        // 숫자 자판에는 닫기 키가 없어서 자판 위에 '완료'를 둔다
        .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료") { focus = nil } } }
        // 입력을 0.4초 멈추면 Yahoo 검색
        .task(id: query) {
            remote = []; failed = nil
            let q = query.trimmingCharacters(in: .whitespaces)
            guard !q.isEmpty, q.unicodeScalars.allSatisfy({ $0.isASCII }) else { searching = false; return }
            searching = true
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            let r = await StockSearch.remote(q)
            guard !Task.isCancelled else { return }
            remote = r; searching = false
        }
    }

    private func list(_ hits: [StockHit]) -> some View {
        VStack(spacing: 0) {
            ForEach(hits) { h in
                let held = m.holdings.contains { $0.symbol == h.id }
                Button { Task { await pick(h) } } label: {
                    HStack(spacing: 10) {
                        LogoTile(symbol: h.id, size: 32)
                        VStack(alignment: .leading) {
                            Text(h.name).appFont(15, .bold).lineLimit(1)
                            Text("\(h.id) · \(h.market)").appFont(12).foregroundStyle(Theme.sub)
                        }
                        Spacer()
                        if loading == h.id { ProgressView() }
                        else if held { Text("보유 중").appFont(13, .semibold).foregroundStyle(Theme.muted) }
                        else if let s = known(h) { Text(AppModel.price(s, s.last)).appFont(13, .semibold) }
                    }
                    .padding(.horizontal, 14).frame(minHeight: 56).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(loading != nil)
                Divider().overlay(Theme.line)
            }
        }
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
    }

    /// 이미 시세를 아는 종목 (예시 종목 중 앱에 자료가 있는 것, 전에 추가한 것)
    private func known(_ h: StockHit) -> Symbol? {
        if h.id == "DRNK" || YahooSample.quotes[h.id] != nil || CustomSymbols.shared.symbol(h.id) != nil { return Sample.symbol(h.id) }
        return nil
    }

    private func pick(_ h: StockHit) async {
        failed = nil; focus = nil
        if let s = known(h) { open(s); return }
        loading = h.id
        defer { loading = nil }
        do { open(try await CustomSymbols.shared.fetch(h)) }
        catch {
            // 예시 종목은 시세를 못 받아도 예시 값으로 넣을 수 있다
            if let s = Sample.symbols.first(where: { $0.id == h.id }) { open(s); failed = nil }
            else { failed = "\(h.name) 시세를 받지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요." }
        }
    }
    private func open(_ s: Symbol) {
        picked = s; qty = ""; warn = false
        avg = s.currency == .usd ? String(format: "%.2f", s.last) : String(Int(s.last.rounded()))
    }

    private func form(_ p: Symbol) -> some View {
        Card {
            HStack {
                Text("\(p.name) (\(p.id))").appFont(16, .bold)
                Spacer()
                Button("다른 종목") { picked = nil }.appFont(13, .semibold).foregroundStyle(Theme.teal)
            }
            Text((p.quote.live ? "지금 " : "전일 종가 ") + AppModel.price(p, p.last)).appFont(13).foregroundStyle(Theme.sub)
            field("수량 (주)", $qty).focused($focus, equals: 1)
            field("평균 단가 (\(p.currency == .usd ? "달러" : "원"))", $avg).focused($focus, equals: 2)
            if warn { Text("수량과 평균 단가를 0보다 큰 숫자로 넣어 주세요.").appFont(13).foregroundStyle(Theme.up) }
            PrimaryButton(title: "추가하기") {
                focus = nil
                if m.addHolding(p.id, qty, avg) { dismiss() } else { warn = true }
            }
        }
    }

    private func field(_ label: String, _ b: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).appFont(13, .semibold).foregroundStyle(Theme.sub)
            TextField("", text: b).keyboardType(.decimalPad)
                .padding(.horizontal, 12).frame(minHeight: 44)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
        }
    }
}

// 수량·단가 고치기 (시안 29판): 위에 '저장하면 이렇게 바뀌어요' 전후 카드 고정, 직접 고치기 · 더 샀어요 · 팔았어요
struct HoldingEditView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    let sym: Symbol
    @State private var mode = "fix"
    @State private var q = ""
    @State private var p = ""
    @State private var said: String? = nil     // 매매를 저장한 뒤 인물의 한 줄 (평정 지수)

    var body: some View {
        let h = m.holdings.first { $0.symbol == sym.id } ?? Holding(symbol: sym.id, qty: 0, avg: sym.close)
        let q0 = h.qty, p0 = h.avg, px = sym.last
        let (qi, pi, q1, p1, bad, note) = compute(q0, p0)
        let v0 = m.krw(sym, q0 * px), v1 = m.krw(sym, (bad ? q0 : q1) * px)
        let need = { (avg: Double) in avg / px - 1 > 0.0005 ? "+" + String(format: "%.1f", (avg / px - 1) * 100) + "% 남음" : "본전 넘음" }
        let rows: [(String, String, String)] = [
            ("수량", "\(q0.formatted())주", "\((bad ? q0 : q1).formatted())주"),
            ("평균 단가", AppModel.price(sym, p0), AppModel.price(sym, bad ? p0 : p1)),
            ("평가액", AppModel.man(v0), AppModel.man(v1)),
            ("본전까지", need(p0), need(bad ? p0 : p1)),
        ]
        PinnedLayout {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("저장하면 이렇게 바뀌어요").appFont(14, .bold)
                    Spacer()
                    Text("\(sym.id) · 지금 \(AppModel.price(sym, px))").appFont(12).foregroundStyle(Theme.sub)
                }
                ForEach(rows, id: \.0) { k, a, b in
                    HStack {
                        Text(k).appFont(14).foregroundStyle(Theme.sub)
                        Spacer()
                        if a != b { Text(a).appFont(13).strikethrough().foregroundStyle(Theme.muted) }
                        Text(b).appFont(16, .bold).foregroundStyle(a == b ? Theme.ink : Theme.teal)
                    }
                }
                ChipRow(items: [("fix", "직접 고치기"), ("buy", "더 샀어요"), ("sell", "팔았어요")], selection: $mode, fill: true)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                field(mode == "fix" ? "수량 (주)" : mode == "buy" ? "더 산 수량 (주)" : "판 수량 (주)", $q)
                field(mode == "fix" ? "평균 단가" : mode == "buy" ? "산 가격" : "판 가격", $p)
                if !note.isEmpty {
                    Text(note).appFont(13).padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
                }
                PrimaryButton(title: "저장", color: bad ? Theme.muted : Theme.teal) {
                    guard !bad else { return }
                    if mode == "sell" && sym.currency == .usd { m.taxGain += (m.krw(sym, qi * (pi - p0)) / 1e4).rounded() }
                    save(q1, p1, trade: mode == "fix" ? nil : mode == "buy")
                }
                Button("이 종목 지우기") { save(0, p0, trade: false) }
                    .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                Text("거래를 하나씩 기록하거나 증권사 파일로 맞추는 건 PC naeilo.com에서 할 수 있어요. 앱에서는 수량과 평균 단가만 바꿔요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationTitle(sym.name).navigationBarTitleDisplayMode(.inline)
        .onAppear {
            reset(h)
            // 캡처용: -editMode buy -editQ 5 처럼 입력을 채워 둔다
            if let md = UserDefaults.standard.string(forKey: "editMode") { mode = md; DispatchQueue.main.async { q = UserDefaults.standard.string(forKey: "editQ") ?? q } }
        }
        .onChange(of: mode) { _, _ in reset(h) }
        .alert("평정 지수", isPresented: Binding(get: { said != nil }, set: { if !$0 { said = nil; dismiss() } })) {
            Button("확인") {}
        } message: { Text(said ?? "") }
    }


    private func compute(_ q0: Double, _ p0: Double) -> (Double, Double, Double, Double, Bool, String) {
        let qi = Double(q) ?? -1, pi = Double(p) ?? -1
        var q1 = q0, p1 = p0, bad = false, note = ""
        switch mode {
        case "fix":
            bad = !(qi >= 0 && pi > 0); if !bad { q1 = qi; p1 = pi }
        case "buy":
            bad = !(qi > 0 && pi > 0)
            if !bad { q1 = q0 + qi; p1 = (q0 * p0 + qi * pi) / q1; note = "\(AppModel.price(sym, pi))에 \(qi.formatted())주 더 사서 평균 단가가 \(AppModel.price(sym, p1))가 돼요." }
        default:
            bad = !(qi > 0 && qi <= q0 && pi > 0)
            if !bad {
                q1 = q0 - qi
                let g = m.krw(sym, qi * (pi - p0))
                note = "판 부분의 \(g >= 0 ? "이익" : "손실")은 \(AppModel.man(abs(g)))이에요." + (sym.currency == .usd ? " 저장하면 올해 해외주식 실현 이익에 더해져 세금 계산에 반영돼요." : " 국내 상장주식은 대주주가 아니면 양도세가 없어요.")
            } else if qi > q0 { note = "가진 수량(\(q0.formatted())주)보다 많이 팔 수는 없어요." }
        }
        return (qi, pi, q1, p1, bad, note)
    }

    private func reset(_ h: Holding) {
        if mode == "fix" { q = h.qty.formatted(.number.grouping(.never)); p = h.avg.formatted(.number.grouping(.never)) } else { q = ""; p = sym.close.formatted(.number.grouping(.never)) }
    }

    /// trade: 산 것(true)·판 것(false)이면 평정 지수에 기록하고 인물의 한 줄을 보여 준 뒤 닫는다. 직접 고치기(nil)는 기록하지 않는다
    private func save(_ qty: Double, _ avg: Double, trade buy: Bool? = nil) {
        let before = m.holdings.first(where: { $0.symbol == sym.id && $0.qty > 0 }), drift0 = m.calmDrift()
        if let i = m.holdings.firstIndex(where: { $0.symbol == sym.id }) {
            if qty > 0 { m.holdings[i] = Holding(symbol: sym.id, qty: qty, avg: (avg * 100).rounded() / 100) } else { m.holdings.remove(at: i) }
        }
        if let buy, let before {
            let act = m.calmTrade(sym, buy: buy, avgBefore: before.avg, driftBefore: drift0)
            let pts = m.calm.events.last?.pts ?? 0, who = m.calmSpeaker
            let name = Shelter.friends.first { $0.id == who }?.name ?? ""
            said = "\(name): \(Calm.feedback(who, act))" + (pts != 0 ? " (\(pts > 0 ? "+" : "")\(pts)점)" : "")
            return
        }
        dismiss()
    }

    private func field(_ label: String, _ b: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label + (label.contains("주") ? "" : sym.currency == .usd ? " (달러)" : " (원)")).appFont(13, .semibold).foregroundStyle(Theme.sub)
            TextField("", text: b).keyboardType(.decimalPad).appFont(18, .semibold)
                .padding(.horizontal, 12).frame(minHeight: 48)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
                .accessibilityLabel(label)
        }
    }
}
