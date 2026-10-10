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
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { i, r in
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
                Text("작은 그래프는 \(period == .d1 ? "전일 종가와 지금 가격" : period == .w1 ? "최근 5개 종가와 지금" : period.label + " 가격 흐름")이고, 회색 점선은 내 평균 단가예요(그 기간 가격 범위 안에 있을 때만). 오르면 빨강, 내리면 파랑이에요. 미국 종목은 실시간, 한국 종목은 전일 종가예요. 여러 종목 한 번에 넣기와 증권사 파일은 PC naeilo.com에서 해요.")
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
                        Text("\(sym.id) · \(sym.market) · \(sym.sector)").appFont(13).foregroundStyle(Theme.sub)
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
                        kv("비중", m.total > 0 ? AppModel.pct(val / m.total) : "-")
                        kv("업종", sym.sector, last: true)
                    }
                }
                if h.qty > 0 {
                    NavigationLink { HoldingEditView(sym: sym) } label: {
                        Label("수량·단가 고치기", systemImage: "pencil").appFont(15, .bold)
                            .frame(maxWidth: .infinity, minHeight: 48).foregroundStyle(Theme.teal)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                    }.buttonStyle(.plain)
                }
                if let p = Sample.profiles[sym.id] { profile(p) }
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
struct AddHoldingView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var picked: Symbol? = nil
    @State private var qty = ""
    @State private var avg = ""

    var body: some View {
        let q = query.lowercased().replacingOccurrences(of: " ", with: "")
        let found = Sample.symbols.filter { q.isEmpty || "\($0.id)\($0.name)\($0.search)".lowercased().replacingOccurrences(of: " ", with: "").contains(q) }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("종목 추가").appFont(22, .bold)
                TextField("한글·영문 이름, 티커, 종목 코드", text: $query)
                    .padding(.horizontal, 14).frame(minHeight: 48)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
                if let p = picked {
                    Card {
                        Text("\(p.name) (\(p.id))").appFont(16, .bold)
                        Text((p.quote.live ? "지금 " : "전일 종가 ") + AppModel.price(p, p.last)).appFont(13).foregroundStyle(Theme.sub)
                        field("수량 (주)", $qty)
                        field("평균 단가 (\(p.currency == .usd ? "달러" : "원"))", $avg)
                        PrimaryButton(title: "추가하기") {
                            guard let qn = Double(qty), qn > 0, let an = Double(avg), an > 0 else { return }
                            m.holdings.removeAll { $0.symbol == p.id }
                            m.holdings.append(Holding(symbol: p.id, qty: qn, avg: an))
                            dismiss()
                        }
                    }
                } else if found.isEmpty {
                    Text("찾는 종목이 없어요. 한글 이름, 영문 이름, 티커, 종목 코드로 찾을 수 있어요.").appFont(14).foregroundStyle(Theme.sub)
                } else {
                    VStack(spacing: 0) {
                        ForEach(found) { s in
                            let held = m.holdings.contains { $0.symbol == s.id }
                            Button { picked = s; qty = ""; avg = String(Int(s.last.rounded())) } label: {
                                HStack(spacing: 10) {
                                    LogoTile(symbol: s.id, size: 32)
                                    VStack(alignment: .leading) {
                                        Text(s.name).appFont(15, .bold)
                                        Text("\(s.id) · \(s.market)").appFont(12).foregroundStyle(Theme.sub)
                                    }
                                    Spacer()
                                    Text(held ? "보유 중" : AppModel.price(s, s.last)).appFont(13, .semibold)
                                        .foregroundStyle(held ? Theme.muted : Theme.ink)
                                }
                                .padding(.horizontal, 14).frame(minHeight: 56).contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Theme.line)
                        }
                    }
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
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
                    save(q1, p1)
                }
                Button("이 종목 지우기") { save(0, p0) }
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

    private func save(_ qty: Double, _ avg: Double) {
        if let i = m.holdings.firstIndex(where: { $0.symbol == sym.id }) {
            if qty > 0 { m.holdings[i] = Holding(symbol: sym.id, qty: qty, avg: (avg * 100).rounded() / 100) } else { m.holdings.remove(at: i) }
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
