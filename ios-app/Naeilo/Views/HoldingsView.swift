import SwiftUI

// 종목 탭: 내 돈이 얼마 있고 얼마 벌었나. 맨 위 평가액 → 1년 전부터 가졌다면 그래프 → 미국/국내 묶음 목록
struct HoldingsView: View {
    @Environment(AppModel.self) private var m
    @State private var period: Period = .y1        // 위 그래프에만 걸린다 (목록 %는 늘 산 가격 대비)
    @State private var said: String? = nil          // 지운 뒤 인물의 한 줄 (평정 지수)
    @State private var swiped: String? = nil        // 왼쪽으로 밀어 지우기 버튼이 보이는 종목
    @State private var askDel: Symbol? = nil        // 지울지 한 번 묻는 종목
    @Environment(\.dynamicTypeSize) private var dts
    private static let periods: [Period] = [.m1, .m3, .m6, .y1, .y3]

    var body: some View {
        let rows = m.rows
        let us = m.sortedRows.filter { $0.sym.currency == .usd }
        let kr = m.sortedRows.filter { $0.sym.currency != .usd }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader(tab: "종목").padding(.horizontal, -16)
                SampleNote()
                Text("내 종목").appFont(22, .bold)
                totalCard(us: us.count, kr: kr.count)
                totalChart(rows)
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("종목 \(rows.count)").appFont(15, .bold)
                        Text("%는 내가 산 가격 대비예요").appFont(12).foregroundStyle(Theme.sub)
                    }
                    Spacer()
                    Menu {
                        ForEach(HoldSort.allCases, id: \.self) { c in
                            Button { m.holdSort = c } label: {
                                if c == m.holdSort { Label(c.label, systemImage: "checkmark") } else { Text(c.label) }
                            }
                        }
                    } label: {
                        Label(m.holdSort.label, systemImage: "chevron.down").appFont(13, .bold)
                            .foregroundStyle(Theme.teal).frame(minHeight: 32)
                    }
                    .accessibilityLabel("정렬: \(m.holdSort.label)")
                }
                if !us.isEmpty { group("미국 주식", us) }
                if !kr.isEmpty { group("국내 주식", kr) }
                NavigationLink(value: "add") {
                    Label("종목 추가", systemImage: "plus").appFont(15, .bold)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .foregroundStyle(Theme.teal)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                }.buttonStyle(.plain)
                Text("미국 종목은 달러 가격 × 원/달러로, 국내 종목은 원화 가격 그대로 계산해요")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3)
                if KoreaEstimate.direct || m.holdings.contains(where: { $0.symbol == KoreaEstimate.sumId }) { KoreaSumCard() }
                MyRecordCard()
                Text(Market.shared.asOfText).appFont(13).foregroundStyle(Theme.sub2)
                Color.clear.frame(height: 24)   // 아래 탭바가 마지막 카드를 가리지 않게
            }
            .screen()
        }
        .scrollDismissesKeyboard(.immediately)
        .background(Theme.bg)
        .refreshable { await Market.shared.pullRefresh(m) }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: period) }
        .navigationDestination(for: String.self) { _ in AddHoldingView() }
        .alert("평정 지수", isPresented: Binding(get: { said != nil }, set: { if !$0 { said = nil } })) {
            Button("확인") {}
        } message: { Text(said ?? "") }
        .confirmationDialog("\(askDel?.name ?? "") 종목을 지울까요?", isPresented: Binding(get: { askDel != nil }, set: { if !$0 { askDel = nil } }), titleVisibility: .visible) {
            Button("지우기", role: .destructive) {
                guard let sym = askDel else { return }
                let avg = m.holdings.first { $0.symbol == sym.id }?.avg ?? sym.close
                withAnimation(.snappy) { swiped = nil }
                // 세부정보의 지우기와 같이: 판 것으로 평정 지수에 남긴다
                said = HoldingEdit.save(m, sym, 0, avg, trade: false)
                askDel = nil
            }
            Button("취소", role: .cancel) { swiped = nil }
        } message: { Text("목록과 평가액에서 빠져요. 다시 넣으려면 종목 추가에서 넣으면 돼요.") }
    }

    // 지금 평가액 + 산 돈 대비 수익(원·%)
    private func totalCard(us: Int, kr: Int) -> some View {
        let gain = m.total - m.cost
        return Card {
            Text("지금 평가액").appFont(13, .semibold).foregroundStyle(Theme.sub)
            Text(AppModel.man(m.total)).appFont(28, .bold).monospacedDigit()
            HStack(spacing: 6) {
                Text("산 돈보다").appFont(14).foregroundStyle(Theme.sub)
                Text("\(gain >= 0 ? "+" : "−")\(AppModel.man(abs(gain))) (\(AppModel.sgn(m.ret)))")
                    .appFont(14, .bold).foregroundStyle(Theme.change(gain))
            }
            Text("\(us + kr)종목 · 미국 \(us) · 국내 \(kr) · 원/달러 \(Int(Market.shared.valFx.rounded()).formatted())원")
                .appFont(12).foregroundStyle(Theme.muted)
        }
    }

    // 지금 비중을 1년(기간) 전부터 가졌다면: 백테스트 평가액
    private func totalChart(_ rows: [AppModel.Row]) -> some View {
        let series = rows.map { r in m.prices.series(r.sym, period: period).map { m.krw(r.sym, $0 * r.h.qty) } }
        let n = series.map(\.count).min() ?? 0
        let tot = (0..<n).map { i in series.reduce(0) { $0 + $1[i] } }
        let first = tot.first ?? 0, last = tot.last ?? 0
        let chg = last / max(1, first) - 1
        let start = Calendar.current.date(byAdding: .day, value: -Int(period.span * 1095), to: Date()) ?? Date()
        return Card {
            Text("지금 종목을 \(period.label) 전부터 가졌다면").appFont(15, .bold)
            Text("비중을 그대로 두고 지난 \(period.label) 가격으로 다시 계산했어요").appFont(12).foregroundStyle(Theme.sub)
            HStack(alignment: .firstTextBaseline) {
                Text("\(AppModel.man(first)) → \(AppModel.man(last))").appFont(14, .semibold).monospacedDigit()
                    .lineLimit(1).minimumScaleFactor(0.8)
                Spacer()
                Text(AppModel.sgn(chg)).appFont(15, .bold).foregroundStyle(Theme.change(chg))
            }
            // 점선 = 시작 금액
            Sparkline(points: tot, avg: first, lineWidth: 2.2, showEndDot: true).frame(height: 120)
            HStack {
                Text("\(Calendar.current.component(.year, from: start).formatted(.number.grouping(.never)))년 \(Calendar.current.component(.month, from: start))월 · 점선은 시작 금액")
                Spacer()
                Text("오늘")
            }
            .appFont(11).foregroundStyle(Theme.muted)
            ChipRow(items: Self.periods.map { ($0, $0.label) }, selection: $period, fill: true)
            let noHist = rows.filter { !m.prices.hasHistory($0.sym) }.map(\.sym.name)
            if !noHist.isEmpty {
                Text("과거 시세 없음: \(noHist.joined(separator: ", ")) (지금 가격으로 평평하게 그렸어요)")
                    .appFont(12).foregroundStyle(Theme.muted)
            }
        }
    }

    // 미국 주식 / 국내 주식 묶음 카드
    private func group(_ title: String, _ rs: [AppModel.Row]) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(title) \(rs.count)").appFont(14, .bold)
                Spacer()
                Text(AppModel.man(rs.reduce(0) { $0 + $1.value })).appFont(14, .semibold).foregroundStyle(Theme.sub).monospacedDigit()
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            ForEach(rs) { r in
                Divider().overlay(Theme.line)
                NavigationLink(value: r.sym) { row(r) }.buttonStyle(.plain)
                    .background(Theme.card)
                    .offset(x: swiped == r.id ? -84 : 0)
                    // 왼쪽으로 밀면 뒤에 숨은 지우기 버튼이 보인다
                    .background(alignment: .trailing) {
                        if swiped == r.id {
                            Button { askDel = r.sym } label: {
                                VStack(spacing: 2) {
                                    Image(systemName: "trash").font(.system(size: 18, weight: .semibold))
                                    Text("지우기").appFont(12, .bold)
                                }
                                .foregroundStyle(.white).frame(width: 84).frame(maxHeight: .infinity)
                                .background(Theme.up).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(r.sym.name) 지우기")
                        }
                    }
                    .clipped()
                    .simultaneousGesture(DragGesture(minimumDistance: 24).onEnded { v in
                        let dx = v.translation.width
                        guard abs(dx) > abs(v.translation.height) * 1.5 else { return }
                        withAnimation(.snappy) { swiped = dx < -40 ? r.id : nil }
                    })
                    .contextMenu {
                        Button(role: .destructive) { askDel = r.sym } label: { Label("이 종목 지우기", systemImage: "trash") }
                    }
                    .accessibilityAction(named: "지우기") { askDel = r.sym }
            }
        }
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    // 행: 로고 · 이름 · "수량 · 평균 단가" / 평가액 · 산 가격 대비 %
    private func row(_ r: AppModel.Row) -> some View {
        let chg = r.cost > 0 ? r.value / r.cost - 1 : 0
        let name = HStack(spacing: 10) {
            LogoTile(symbol: r.id, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(r.sym.name).appFont(15, .bold).lineLimit(1)
                Text("\(r.h.qty.formatted())주 · 평균 \(AppModel.price(r.sym, r.h.avg))")
                    .appFont(12).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
            }
        }
        let amount = VStack(alignment: .trailing, spacing: 2) {
            Text(AppModel.man(r.value)).appFont(15, .bold).monospacedDigit().lineLimit(1)
            Text(AppModel.sgn(chg)).appFont(13, .bold).foregroundStyle(Theme.change(chg))
        }
        return Group {
            if dts.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 6) { name; amount }.padding(.vertical, 10)
            } else {
                HStack(spacing: 10) {
                    name
                    Spacer(minLength: 4)
                    amount.frame(minWidth: 72, alignment: .trailing)
                    Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.muted)
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
        let row = m.rows.first { $0.id == sym.id }
        let k = row.map { m.metrics($0) }
        let val = row?.value ?? 0, cost = row?.cost ?? 0, gain = val - cost
        let r = k?.vc ?? 0, need = h.avg / sym.last - 1
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // 이름은 위 제목줄에 있으니 여기선 로고와 시장·업종만
                HStack(spacing: 10) {
                    LogoTile(symbol: sym.id, size: 36)
                    Text(([sym.id, sym.market] + (sym.sector.isEmpty ? [] : [sym.sector])).joined(separator: " · ")).appFont(13).foregroundStyle(Theme.sub)
                }
                if h.qty > 0 {
                    Card {
                        Text("내 평가액").appFont(13).foregroundStyle(Theme.sub)
                        HStack(alignment: .firstTextBaseline) {
                            Text(AppModel.man(val)).appFont(28, .bold)
                            Text(AppModel.sgn(r)).appFont(15, .bold).foregroundStyle(Theme.change(r))
                        }
                        Text(summary(gain: gain, r: r, k: k)).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    }
                }
                Card {
                    HStack {
                        Text("주가").appFont(15, .bold)
                        Spacer()
                        Text("\(period == .d1 ? "오늘" : period.label) \(AppModel.sgn(chg))")
                            .appFont(13, .bold).foregroundStyle(Theme.change(chg))
                    }
                    if period != .d1 && !m.prices.hasHistory(sym) {
                        Text("기록 모으는 중").appFont(15, .bold).foregroundStyle(Theme.sub)
                            .frame(maxWidth: .infinity, minHeight: 140)
                    } else {
                        Sparkline(points: pts, avg: h.qty > 0 ? h.avg : nil, lineWidth: 2.2, showEndDot: true,
                                  avgLabel: h.qty > 0 ? "평균 단가 \(AppModel.price(sym, h.avg))" : nil, avgAlways: true).frame(height: 140)
                    }
                    ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true)
                }
                Card(padding: 0) {
                    VStack(spacing: 0) {
                        kv("보유 수량", h.qty.formatted() + "주")
                        kv("평균 단가", AppModel.price(sym, h.avg))
                        kv(Market.shared.isToss(sym) ? "토스 시세" : sym.currency == .krw ? (CustomSymbols.shared.isLive(sym.id) ? "전일 종가 (테스트)" : KoreaEstimate.info(id: sym.id, name: sym.name).proxy != nil ? "추정 가격" : "넣은 가격") : sym.quote.live ? "지금 가격" : "전일 종가", AppModel.price(sym, sym.last))
                        if sym.quote.live { kv("오늘 등락", AppModel.sgn(sym.quote.change)) }
                        if sym.currency == .usd { kv("환율", "1달러 " + Int(Market.shared.valFx.rounded()).formatted() + "원") }
                        kv(need > 0.0005 ? "본전까지" : "본전 대비", need > 0.0005 ? "주가가 " + String(format: "%.1f", need * 100) + "% 오르면 본전"
                           : need > -0.0005 ? "본전과 같아요" : "본전보다 " + String(format: "%.1f", -need * 100) + "% 위")
                        kv("비중", k.map { AppModel.pct($0.w) } ?? "-", last: true)
                    }
                }
                if sym.currency == .krw, let c = CustomSymbols.shared.saved(sym.id), !CustomSymbols.shared.isLive(sym.id) { KRPriceCard(saved: c) }
                if h.qty > 0 {
                    NavigationLink { HoldingEditView(sym: sym) } label: {
                        Label("수량·평균 단가 고치기", systemImage: "pencil").appFont(15, .bold)
                            .frame(maxWidth: .infinity, minHeight: 48).foregroundStyle(Theme.teal)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                    }.buttonStyle(.plain)
                }
                if let p = Sample.profiles[sym.id] { profile(p) } else { noProfile }
            }
            .screen().padding(.top, 8)
        }
        .scrollDismissesKeyboard(.immediately)
        .background(Theme.bg)
        .navigationTitle(sym.name).navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $editing) { HoldingEditView(sym: sym) }
        .onAppear { if UserDefaults.standard.string(forKey: "editMode") != nil && !opened { opened = true; editing = true } }
    }

    /// 평가액 아래 한 줄: 원화로 얼마 벌었/잃었는지 + 1년 고점 대비 (환율은 지금 기준)
    private func summary(gain: Double, r: Double, k: AppModel.HoldMetrics?) -> String {
        let g = abs(r) < 0.0005 ? "산 가격과 같아요" : "산 가격보다 \(AppModel.won(gain)) \(gain >= 0 ? "벌었어요" : "줄었어요")"
        guard let k, m.prices.hasHistory(sym) else { return g }
        return g + " · 1년 고점보다 " + String(format: "%.0f", abs(k.dd) * 100) + "% " + (k.dd > -0.005 ? "가까이" : "아래")
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
            Text(sym.currency == .krw ? "한국 시장 \(sym.sector.isEmpty ? "종목" : sym.sector + " 종목")이에요." : "\(sym.market) \(sym.sector.isEmpty ? "종목" : sym.sector + " 종목")이에요.")
                .appFont(14)
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
                 : "공개 정보 기준(2026년 10월)이에요. 종목 추천이 아니에요.")
                .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
        }
    }
}

// 종목 추가: 한글·영문·티커·종목 코드 검색 → 수량·평균 단가
// 앱 안 목록(예시 종목 + 한글 이름 목록)은 바로, Yahoo 검색은 입력을 멈추면 아래에 더 붙는다 (StockSearch)
/// 국내 주식 합계 (숨긴 선택 항목): 증권사 앱의 국내 주식 평가액 합계를 한 줄로. 0 이면 지운다 (웹 보유 종목 아래 칸과 같다)
struct KoreaSumCard: View {
    @Environment(AppModel.self) private var m
    var title = "국내 주식 합계"
    var done: (() -> Void)? = nil
    @State private var t = ""
    @FocusState private var focus: Bool
    var body: some View {
        let held = m.holdings.contains { $0.symbol == KoreaEstimate.sumId }
        let cur: Double? = held ? CustomSymbols.shared.krNow : nil, lastD = CustomSymbols.shared.krLog.last?.d
        let ago = lastD.flatMap(Day.date).map { Int((Date().timeIntervalSince($0) / 86400).rounded(.down)) } ?? 0
        Card {
            Text(title).appFont(15, .bold)
            // 웹 입력 칸 밑 안내와 같은 문구. '기록 그래프'를 누르면 내 기록의 '국내 주식 합계' 그래프로 간다
            (Text("증권사 앱 합계 그대로 (0이면 지움)" + (held && ago > 0 ? " · \(ago)일 전 금액" : "") + " · 전망은 EWY×원/달러 · ") + Text("기록 그래프").foregroundColor(Theme.teal))
                .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                .onTapGesture { focus = false; m.recKr = true; m.tab = .hold; m.holdPath = NavigationPath(); done?() }
            HStack(spacing: 8) {
                TextField(cur.map { Int($0.rounded()).formatted() + "원" } ?? "금액 (원)", text: $t).keyboardType(.decimalPad).focused($focus)
                    .padding(.horizontal, 12).frame(minHeight: 44)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                Button("저장") {
                    guard let v = Double(t.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)), v >= 0 else { return }
                    let id = KoreaEstimate.sumId
                    CustomSymbols.shared.setKoreaSum(v)     // 날짜별 기록 (0 = 다 팔았어요, 지난 기록은 남는다)
                    if v == 0 { m.holdings.removeAll { $0.symbol == id } }
                    else if !m.holdings.contains(where: { $0.symbol == id }) { _ = m.addHolding(id, "1", String(v)) }
                    else { m.recordDay() }
                    t = ""; focus = false; done?()
                }.appFont(15, .bold).foregroundStyle(Theme.teal).frame(minHeight: 44)
            }
        }
        .toolbar { ToolbarItemGroup(placement: .keyboard) { if focus { Spacer(); Button("완료") { focus = false } } } }
    }
}

/// 한국 종목 가격 다시 넣기 (시세를 받지 않으므로 사용자가 넣은 가격으로 계산)
struct KRPriceCard: View {
    let saved: CustomSymbols.Saved
    @State private var t = ""
    @FocusState private var focus: Bool
    var body: some View {
        Card {
            Text(saved.id == KoreaEstimate.sumId ? "국내 주식 합계" : "한국 종목 가격").appFont(15, .bold)
            Text(KoreaEstimate.note(saved)).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                TextField(saved.id == KoreaEstimate.sumId ? "합계 금액 (원)" : "지금 가격 (원)", text: $t).keyboardType(.decimalPad).focused($focus)
                    .padding(.horizontal, 12).frame(minHeight: 44)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                Button("저장") {
                    if let v = Double(t.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)), v > 0 {
                        CustomSymbols.shared.setPrice(saved.id, v); t = ""
                    }
                    focus = false
                }.appFont(15, .bold).foregroundStyle(Theme.teal).frame(minHeight: 44)
            }
        }
        .toolbar { ToolbarItemGroup(placement: .keyboard) { if focus { Spacer(); Button("완료") { focus = false } } } }
    }
}

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
    @State private var kp = ""                        // 한국 종목: 지금 가격 (시세를 받지 않는다)
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
                    Text("시세는 테스트용 Yahoo 중계 자료예요. 미국 종목은 지금 가격이에요. 한국 종목은 시세를 받지 않아 가격을 한 번 넣어요. 미국 지수를 따르는 국내 ETF는 그 뒤로 미국 ETF × 원/달러로 추정해요.")
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
                            Text("\(h.id) · \(h.market)\(KoreaEstimate.tag(h))").appFont(12).foregroundStyle(Theme.sub)
                        }
                        Spacer()
                        if loading == h.id { ProgressView() }
                        else if held { Text("보유 중").appFont(13, .semibold).foregroundStyle(Theme.muted) }
                        else if let s = known(h), s.last > 0 { Text(AppModel.price(s, s.last)).appFont(13, .semibold) }
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
        if h.currency == .krw && KoreaEstimate.liveOK(h.id) && !CustomSymbols.shared.isLive(h.id) { return nil }   // 테스트 시세를 새로 받는다
        if h.id == "DRNK" || YahooSample.quotes[h.id] != nil || CustomSymbols.shared.symbol(h.id) != nil { return Sample.symbol(h.id) }
        return nil
    }

    private func pick(_ h: StockHit) async {
        failed = nil; focus = nil
        // 내부 테스트 시세는 국내 종목 5개까지
        if h.currency == .krw && KoreaEstimate.liveOK(h.id),
           m.holdings.filter({ $0.symbol != h.id && CustomSymbols.shared.isLive($0.symbol) }).count >= KoreaEstimate.liveMax {
            failed = "테스트 시세는 국내 종목 \(KoreaEstimate.liveMax)개까지 받을 수 있어요."; return
        }
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
        avg = s.currency == .usd ? String(format: "%.2f", s.last) : ""
        kp = s.currency == .krw && s.last > 0 ? String(Int(s.last.rounded())) : ""
    }

    private func form(_ p: Symbol) -> some View {
        Card {
            HStack {
                Text("\(p.name) (\(p.id))").appFont(16, .bold)
                Spacer()
                Button("다른 종목") { picked = nil }.appFont(13, .semibold).foregroundStyle(Theme.teal)
            }
            if p.currency == .krw && !CustomSymbols.shared.isLive(p.id) {
                let pk = KoreaEstimate.info(id: p.id, name: p.name).proxy != nil
                Text(pk ? "한국 시세는 받지 않아요. 지금 가격을 한 번 넣으면 그 뒤로는 같은 미국 ETF × 원/달러 움직임으로 추정해요."
                        : "한국 시세는 받지 않아요. 넣은 가격 그대로 계산하고, 움직임 예측은 한국 시장 평균(EWY × 원/달러)으로 해요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                field("지금 가격 (원)", $kp).focused($focus, equals: 3)
            } else {
                Text((p.quote.live ? "지금 " : "전일 종가 ") + AppModel.price(p, p.last)).appFont(13).foregroundStyle(Theme.sub)
            }
            field("수량 (주)", $qty).focused($focus, equals: 1)
            field("평균 단가 (\(p.currency == .usd ? "달러" : "원, 비우면 지금 가격"))", $avg).focused($focus, equals: 2)
            if warn { Text(p.currency == .krw ? "지금 가격과 수량을 0보다 큰 숫자로 넣어 주세요." : "수량과 평균 단가를 0보다 큰 숫자로 넣어 주세요.").appFont(13).foregroundStyle(Theme.up) }
            PrimaryButton(title: "추가하기") {
                focus = nil
                if p.currency == .krw && !CustomSymbols.shared.isLive(p.id) {
                    guard let v = Double(kp.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)), v > 0 else { warn = true; return }
                    CustomSymbols.shared.setPrice(p.id, v)
                    if m.addHolding(p.id, qty, avg.trimmingCharacters(in: .whitespaces).isEmpty ? kp : avg) { dismiss() } else { warn = true }
                    return
                }
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
    @State private var askDel = false         // 지우기 전에 한 번 묻는다

    var body: some View {
        let h = m.holdings.first { $0.symbol == sym.id } ?? Holding(symbol: sym.id, qty: 0, avg: sym.close)
        let q0 = h.qty, p0 = h.avg, px = sym.last
        let r = HoldingEdit.compute(m, sym, mode, q, p, q0, p0)
        let (q1, p1, bad, note) = (r.q1, r.p1, r.bad, r.note)
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
                    if let s = HoldingEdit.commit(m, sym, mode, r, p0) { said = s } else { dismiss() }
                }
                Button("이 종목 지우기") { askDel = true }
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
        .confirmationDialog("\(sym.name) 종목을 지울까요?", isPresented: $askDel, titleVisibility: .visible) {
            Button("지우기", role: .destructive) { if let s = HoldingEdit.save(m, sym, 0, p0, trade: false) { said = s } else { dismiss() } }
            Button("취소", role: .cancel) {}
        } message: { Text("목록과 평가액에서 빠져요. 다시 넣으려면 종목 추가에서 넣으면 돼요.") }
        .alert("평정 지수", isPresented: Binding(get: { said != nil }, set: { if !$0 { said = nil; dismiss() } })) {
            Button("확인") {}
        } message: { Text(said ?? "") }
    }


    private func reset(_ h: Holding) { (q, p) = HoldingEdit.initial(mode, h, sym) }

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

/// 수량·단가 고치기 계산과 저장 (세부정보 HoldingEditView가 쓴다)
enum HoldingEdit {
    struct Result { let qi, pi, q1, p1: Double; let bad: Bool; let note: String }

    static func compute(_ m: AppModel, _ sym: Symbol, _ mode: String, _ q: String, _ p: String, _ q0: Double, _ p0: Double) -> Result {
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
        return Result(qi: qi, pi: pi, q1: q1, p1: p1, bad: bad, note: note)
    }

    /// 모드를 바꿀 때 입력칸 처음 값: 직접 고치기는 지금 값, 매매는 수량 비우고 가격은 종가
    static func initial(_ mode: String, _ h: Holding, _ sym: Symbol) -> (String, String) {
        mode == "fix" ? (h.qty.formatted(.number.grouping(.never)), h.avg.formatted(.number.grouping(.never)))
                      : ("", sym.close.formatted(.number.grouping(.never)))
    }

    /// 저장 버튼: 판 해외주식은 실현 이익에 더하고 저장. 매매면 인물의 한 줄을 돌려준다
    static func commit(_ m: AppModel, _ sym: Symbol, _ mode: String, _ r: Result, _ p0: Double) -> String? {
        if mode == "sell" && sym.currency == .usd { m.taxGain += (m.krw(sym, r.qi * (r.pi - p0)) / 1e4).rounded() }
        let said = save(m, sym, r.q1, r.p1, trade: mode == "fix" ? nil : mode == "buy")
        // 내 기록의 넣은 돈: 산 것·판 것은 그때 가격으로 (직접 고치기는 그날 가격으로 자동 계산)
        if mode != "fix" { let dq = mode == "buy" ? r.qi : -r.qi; m.recordDay(trade: (Sync.siteTicker(sym.id), dq, m.krw(sym, dq * r.pi))) }
        return said
    }

    /// trade: 산 것(true)·판 것(false)이면 평정 지수에 기록하고 인물의 한 줄을 돌려준다. 직접 고치기(nil)는 기록하지 않는다
    @discardableResult
    static func save(_ m: AppModel, _ sym: Symbol, _ qty: Double, _ avg: Double, trade buy: Bool? = nil) -> String? {
        let before = m.holdings.first(where: { $0.symbol == sym.id && $0.qty > 0 }), drift0 = m.calmDrift()
        if let i = m.holdings.firstIndex(where: { $0.symbol == sym.id }) {
            if qty > 0 { m.holdings[i] = Holding(symbol: sym.id, qty: qty, avg: (avg * 100).rounded() / 100) } else { m.holdings.remove(at: i) }
        }
        guard let buy, let before else { return nil }
        let act = m.calmTrade(sym, buy: buy, avgBefore: before.avg, driftBefore: drift0)
        let pts = m.calm.events.last?.pts ?? 0, who = m.calmSpeaker
        return "\(m.friendName(who)): \(Calm.feedback(m.voice(who), act))" + (pts != 0 ? " (\(pts > 0 ? "+" : "")\(pts)점)" : "")
    }
}
