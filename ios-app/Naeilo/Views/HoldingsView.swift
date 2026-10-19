import SwiftUI
import CoreLocation
import Observation

// 종목 탭: 내 종목 | 소식판. 내 종목은 내 돈이 얼마 있고 얼마 벌었나. 맨 위 평가액 → 1년 전부터 가졌다면 그래프 → 미국/국내 묶음 목록
struct HoldingsView: View {
    @Environment(AppModel.self) private var m
    @State private var period: Period = .m1        // 위 그래프에만 걸린다 (목록 %는 늘 산 가격 대비)
    @State private var said: String? = nil          // 지운 뒤 인물의 한 줄 (평정 지수)
    @State private var swiped: String? = nil        // 왼쪽으로 밀어 지우기 버튼이 보이는 종목
    @State private var askDel: Symbol? = nil        // 지울지 한 번 묻는 종목
    @AppStorage("holdUsd") private var inUsd = false // 목록 금액: 미국 종목을 달러로 볼지
    @Environment(\.dynamicTypeSize) private var dts
    @State private var page = 0                     // 0 내 종목, 1 소식판, 2 동네 (미션 탭처럼 위에서 고른다)
    @AppStorage("town.arrive") private var arrive = "" // 방금 추가한 종목: 동네로 가서 그 주민 대사창부터
    private static let periods: [Period] = [.m1, .m3, .m6, .y1, .y3]

    var body: some View {
        let rows = m.rows
        let us = m.sortedRows.filter { $0.sym.currency == .usd }
        let kr = m.sortedRows.filter { $0.sym.currency != .usd }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader(tab: "종목").padding(.horizontal, -16)
                SampleNote()
                HoldPagePicker(page: $page)
                if page == 1 { HoldingsNews() } else if page == 2 { TownView() } else {
                totalCard(us: us.count, kr: kr.count)
                totalChart(rows)
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("종목 \(rows.count)").appFont(15, .bold)
                        Text("%는 산 가격 대비").appFont(12).foregroundStyle(Theme.sub)
                    }
                    Spacer()
                    unitToggle
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
                if !us.isEmpty { group("미국", us) }
                if !kr.isEmpty { group("국내", kr) }
                NavigationLink(value: "add") {
                    Label("종목 추가", systemImage: "plus").appFont(15, .bold)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .foregroundStyle(Theme.teal)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                }.buttonStyle(.plain)
                MyRecordCard()
                }
                Text(Market.shared.asOfText).appFont(13).foregroundStyle(Theme.sub2)
                Color.clear.frame(height: 24)   // 아래 탭바가 마지막 카드를 가리지 않게
            }
            .screen()
        }
        .ignoresSafeArea(.keyboard, edges: page == 2 ? .bottom : [])   // 동네는 키보드에 안 들썩이게
        .scrollDismissesKeyboard(.immediately)
        .background(Theme.bg)
        .refreshable { await Market.shared.pullRefresh(m) }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: period) }
        .navigationDestination(for: String.self) { _ in AddHoldingView() }
        .onAppear { if !arrive.isEmpty { page = 2 } }
        .onChange(of: arrive) { if !$1.isEmpty { page = 2 } }
        .alert("내 투자 습관", isPresented: Binding(get: { said != nil }, set: { if !$0 { said = nil } })) {
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

    // 손익 중심: 홈(평가액·목표)과 겹치지 않게 산 돈 대비 수익을 크게
    private func totalCard(us: Int, kr: Int) -> some View {
        let gain = m.total - m.cost
        return Card {
            Text("수익").appFont(13, .semibold).foregroundStyle(Theme.sub)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(gain >= 0 ? "+" : "−")\(AppModel.man(abs(gain)))원").appFont(28, .bold).monospacedDigit()
                    .foregroundStyle(Theme.change(gain)).roll(gain)
                Text(AppModel.sgn(m.ret)).appFont(14, .bold).foregroundStyle(Theme.change(gain))
            }
            Text("원금 \(AppModel.man(m.cost)) → 평가 \(AppModel.man(m.total))")
                .appFont(14).monospacedDigit().foregroundStyle(Theme.sub)
            Text("\(us + kr)종목" + (us > 0 ? " · $1 = \(Int(Market.shared.valFx.rounded()).formatted())원" : ""))
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
            Text("지금 구성, \(period.label) 전부터라면").appFont(15, .bold)
            HStack(alignment: .firstTextBaseline) {
                Text("\(AppModel.man(first)) → \(AppModel.man(last))").appFont(14, .semibold).monospacedDigit()
                    .lineLimit(1).minimumScaleFactor(0.8)
                Spacer()
                Text(AppModel.sgn(chg)).appFont(15, .bold).foregroundStyle(Theme.change(chg))
            }
            // 점선 = 시작 금액
            Sparkline(points: tot, avg: first, lineWidth: 2.2, showEndDot: true,
                      scrub: .period(period) { AppModel.man($0) + "원" }).frame(height: 120)
            HStack {
                Text(period.span < 1 ? "\(Calendar.current.component(.month, from: start))월" : "\(Calendar.current.component(.year, from: start).formatted(.number.grouping(.never))).\(Calendar.current.component(.month, from: start))")
                Spacer()
                Text("오늘")
            }
            .appFont(11).foregroundStyle(Theme.muted)
            ChipRow(items: Self.periods.map { ($0, $0.label) }, selection: $period, fill: true)
            let noHist = rows.filter { !m.prices.hasHistory($0.sym) }.map(\.sym.name)
            if !noHist.isEmpty {
                Text("과거 시세 없음: \(noHist.joined(separator: ", ")) · 평평하게 그림")
                    .appFont(12).foregroundStyle(Theme.muted)
            }
        }
    }

    // 미국 주식 / 국내 주식 묶음 카드
    // 목록 금액 단위: $ / 원 (미국 종목만 바뀐다)
    private var unitToggle: some View {
        HStack(spacing: 0) {
            ForEach([true, false], id: \.self) { u in
                Button { inUsd = u } label: {
                    Text(u ? "$" : "원").appFont(13, .bold)
                        .foregroundStyle(inUsd == u ? .white : Theme.sub)
                        .frame(width: 32, height: 28)
                        .background(inUsd == u ? Theme.teal : .clear, in: Capsule())
                }.buttonStyle(.plain)
            }
        }
        .padding(2).overlay(Capsule().stroke(Theme.border))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("금액 단위: \(inUsd ? "달러" : "원")")
        .accessibilityAction { inUsd.toggle() }
    }

    private func usd(_ r: AppModel.Row) -> Bool { inUsd && r.sym.currency == .usd }
    private func amountText(_ rs: [AppModel.Row]) -> String {
        if !rs.isEmpty, rs.allSatisfy(usd) {
            return "$" + rs.reduce(0) { $0 + $1.h.qty * $1.sym.last }.formatted(.number.precision(.fractionLength(2)))
        }
        return AppModel.man(rs.reduce(0) { $0 + $1.value })
    }

    private func group(_ title: String, _ rs: [AppModel.Row]) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(title) \(rs.count)").appFont(14, .bold)
                Spacer()
                Text(amountText(rs)).appFont(14, .semibold).foregroundStyle(Theme.sub).monospacedDigit()
                    .roll(rs.reduce(0) { $0 + $1.value })
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

    // 행: 로고 · "이름 수량" · "지금 가격 · 오늘 %" / 평가액 · 산 가격 대비 %
    private func row(_ r: AppModel.Row) -> some View {
        let chg = r.cost > 0 ? r.value / r.cost - 1 : 0
        let day = r.sym.quote.change
        let name = HStack(spacing: 10) {
            LogoTile(symbol: r.id, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                // 글자를 줄이지 않는다(종목마다 크기가 같게). 이름 옆 한 줄이 먼저: 안 맞으면 평균 단가를 정수로 줄여 보고,
                // 그래도 안 맞을 때(이름이 길 때)만 수량·평균을 다음 줄로
                let qty = Text("\(r.h.qty.formatted())주").appFont(12).foregroundStyle(Theme.sub).monospacedDigit().lineLimit(1)
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(r.sym.name).appFont(15, .bold).lineLimit(1).fixedSize()
                        qty.fixedSize()
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.sym.name).appFont(15, .bold).lineLimit(1)
                        qty
                    }
                }
                HStack(spacing: 4) {
                    Text("\(AppModel.price(r.sym, r.sym.last)) ·").foregroundStyle(Theme.sub)
                    Text((day >= 0 ? "+" : "−") + String(format: "%.2f%%", abs(day) * 100)).foregroundStyle(Theme.change(day))
                }
                .appFont(12).monospacedDigit().lineLimit(1)
            }
        }
        let amount = VStack(alignment: .trailing, spacing: 2) {
            Text(amountText([r])).appFont(15, .bold).monospacedDigit().lineLimit(1).fixedSize().roll(r.value)
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
                            Text(AppModel.man(val)).appFont(28, .bold).roll(val)
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
                                  avgLabel: h.qty > 0 ? "평균 단가 \(AppModel.price(sym, h.avg))" : nil, avgAlways: true,
                                  scrub: .period(period) { AppModel.price(sym, $0) }).frame(height: 140)
                    }
                    ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true)
                }
                Card(padding: 0) {
                    VStack(spacing: 0) {
                        kv("보유 수량", h.qty.formatted() + "주")
                        kv("평균 단가", AppModel.price(sym, h.avg))
                        kv(Market.shared.isToss(sym) ? "오픈 API 시세" : Market.shared.isAlpaca(sym) ? "Alpaca(IEX) 시세" : Market.shared.isLongbridge(sym) ? "Longbridge 시세" : sym.currency == .krw ? (CustomSymbols.shared.isLive(sym.id) ? "전일 종가 (테스트)" : KoreaEstimate.info(id: sym.id, name: sym.name).proxy != nil ? "추정 가격" : "넣은 가격") : sym.quote.live ? "지금 가격" : "전일 종가", AppModel.price(sym, sym.last))
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
        .refreshable { await Market.shared.pullRefresh(m) }
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
            Text("증권사 앱 합계 그대로 (0이면 지움)" + (held && ago > 0 ? " · \(ago)일 전 금액" : "") + " · 전망은 EWY×원/달러")
                .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
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
    @AppStorage("town.arrive") private var arrive = ""
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
    /// 다른 화면 안에 상자로 넣을 때 (미션 1): 스크롤·제목 없이, 추가하면 닫지 않고 빈칸으로
    var inline = false

    var body: some View {
        if inline {
            box.task(id: query) { await search() }
        } else {
            ScrollView { box.screen().padding(.top, 8) }
                .background(Theme.bg)
                .navigationBarTitleDisplayMode(.inline)
                .scrollDismissesKeyboard(.interactively)
                // 숫자 자판에는 닫기 키가 없어서 자판 위에 '완료'를 둔다
                .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료") { focus = nil } } }
                // 입력을 0.4초 멈추면 Yahoo 검색
                .task(id: query) { await search() }
        }
    }

    private var box: some View {
        let blank = query.trimmingCharacters(in: .whitespaces).isEmpty   // 빈칸이면 목록을 띄우지 않는다
        let local = blank ? [] : StockSearch.local(query)
        let more = remote.filter { r in !local.contains { $0.id == r.id } }
        return VStack(alignment: .leading, spacing: 12) {
                Text("종목 추가").appFont(inline ? 17 : 22, .bold)
                TextField("한글·영문 이름, 티커, 종목 코드", text: $query)
                    .autocorrectionDisabled().textInputAutocapitalization(.never)
                    .focused($focus, equals: 0)
                    // 종목을 고른 뒤에도 위 칸에 글자를 넣으면 바로 아래에 다시 찾는다 ('다른 종목' 버튼 대신)
                    .onChange(of: query) { _, _ in picked = nil }
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
                        Text("더 찾은 종목").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 4)
                        list(more)
                    }
                    if searching {
                        HStack(spacing: 8) { ProgressView(); Text("찾는 중").appFont(13).foregroundStyle(Theme.sub) }
                    } else if !blank && local.isEmpty && more.isEmpty {
                        Text("찾는 종목이 없어요. 이름, 티커, 종목 코드로 찾아 보세요.")
                            .appFont(14).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
    }

    private func search() async {
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
        if h.currency == .krw && KoreaEstimate.liveOK(h.id, name: h.name) && !CustomSymbols.shared.isLive(h.id) { return nil }   // 테스트 시세를 새로 받는다
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
        avg = s.currency == .usd ? String(format: "%.2f", s.last) : ""
        kp = s.currency == .krw && s.last > 0 ? String(Int(s.last.rounded())) : ""
    }

    private func form(_ p: Symbol) -> some View {
        Card {
            HStack {
                Text("\(p.name) (\(p.id))").appFont(16, .bold)
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
                let fresh = !m.holdings.contains { $0.symbol == p.id }
                if p.currency == .krw && !CustomSymbols.shared.isLive(p.id) {
                    guard let v = Double(kp.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)), v > 0 else { warn = true; return }
                    CustomSymbols.shared.setPrice(p.id, v)
                    if m.addHolding(p.id, qty, avg.trimmingCharacters(in: .whitespaces).isEmpty ? kp : avg) { added(fresh ? p.id : nil) } else { warn = true }
                    return
                }
                if m.addHolding(p.id, qty, avg) { added(fresh ? p.id : nil) } else { warn = true }
            }
        }
    }

    private func added(_ new: String?) {
        if inline { picked = nil; query = ""; qty = ""; avg = ""; kp = "" } else { if let new { arrive = new }; dismiss() }
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
                Text("거래가 생기면 바뀐 수량과 평균 단가를 넣어 주세요. 앱은 거래를 하나씩 기록하지 않아요.")
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
        .alert("내 투자 습관", isPresented: Binding(get: { said != nil }, set: { if !$0 { said = nil; dismiss() } })) {
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

    /// trade: 산 것(true)·판 것(false)이면 평정 지수에 기록하고 인물의 한 줄을 돌려준다. 직접 고치기(nil)는 수량이 바뀌었을 때만 늘면 산 것, 줄면 판 것으로 기록
    @discardableResult
    static func save(_ m: AppModel, _ sym: Symbol, _ qty: Double, _ avg: Double, trade buy: Bool? = nil) -> String? {
        let before = m.holdings.first(where: { $0.symbol == sym.id && $0.qty > 0 }), drift0 = m.calmDrift()
        if let i = m.holdings.firstIndex(where: { $0.symbol == sym.id }) {
            if qty > 0 { m.holdings[i] = Holding(symbol: sym.id, qty: qty, avg: (avg * 100).rounded() / 100) } else { m.holdings.remove(at: i) }
        }
        guard let before, let buy = buy ?? (qty == before.qty ? nil : qty > before.qty) else { return nil }
        let act = m.calmTrade(sym, buy: buy, avgBefore: before.avg, driftBefore: drift0)
        let pts = m.calm.events.last?.pts ?? 0, who = m.calmSpeaker
        return "\(m.friendName(who)): \(Calm.feedback(m.voice(who), act))" + (pts != 0 ? " (\(pts > 0 ? "+" : "")\(pts)점)" : "")
    }
}

/// 종목 탭 위 페이지 고르기 (미션 탭 MissionPagePicker와 같은 모양)
struct HoldPagePicker: View {
    @Binding var page: Int
    var body: some View {
        HStack(spacing: 4) {
            ForEach([(0, "내 종목"), (1, "소식판"), (2, "동네")], id: \.0) { i, t in
                let on = page == i
                Button { page = i } label: {   // 애니메이션 없이: 내 종목 화면이 커서 바꿀 때 버벅인다
                    Text(t).appFont(14, .bold).foregroundStyle(on ? Theme.ink : Theme.sub)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(on ? Theme.card : .clear, in: RoundedRectangle(cornerRadius: 10))
                        .contentShape(Rectangle())   // 고르지 않은 칸은 배경이 투명이라 글자만 눌리던 것
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Theme.track, in: RoundedRectangle(cornerRadius: 13))
    }
}

// 소식판 (시안): 나라와 상관없이 내 종목에서 바로 나오는 것만. 새로 받는 시세 없음 (이미 받은 시세·지난 종가로 계산)
// 시장과 비교 → 오늘 많이 움직인 종목 → 1년 고점·저점 가까운 종목
struct HoldingsNews: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let rows = m.rows
        let ks = rows.filter { m.prices.hasHistory($0.sym) }.map { ($0, m.metrics($0)) }
        VStack(alignment: .leading, spacing: 12) {
            if let c = compare(ks) { compareCard(c) }
            moversCard(rows)
            nearCard(ks)
        }
    }

    // MARK: 시장과 비교 (최근 1달). 시장 = 대표 ETF: 미국 종목이 절반 넘으면 S&P500(SPY), 아니면 전 세계(VT). 기록이 없으면 안 보인다
    private struct Cmp { let mine, mkt: Double; let name: String }
    private func compare(_ ks: [(AppModel.Row, AppModel.HoldMetrics)]) -> Cmp? {
        let w = ks.reduce(0.0) { $0 + $1.1.w }
        guard w > 0 else { return nil }
        // 지금 비중으로 4주 전부터 가졌다면 (yr: 1년을 52주로 나눈 가격)
        let mine = ks.reduce(0.0) { a, k in let y = k.1.yr; return a + k.1.w / w * (y[52] / max(1e-9, y[48]) - 1) }
        let usd = m.rows.filter { $0.sym.currency == .usd }.reduce(0.0) { $0 + $1.value } / max(1, m.rows.reduce(0.0) { $0 + $1.value })
        for (id, name) in usd >= 0.5 ? [("SPY", "S&P500 ETF")] : [("VT", "전 세계 ETF"), ("SPY", "S&P500 ETF")] {
            guard let c = PriceHistory.of(id)?.closes, c.count > 21 else { continue }
            return Cmp(mine: mine, mkt: c[c.count - 1] / c[c.count - 21] - 1, name: name)
        }
        return nil
    }

    private func compareCard(_ c: Cmp) -> some View {
        let gap = c.mine - c.mkt
        return VStack(alignment: .leading, spacing: 10) {
            Text("최근 1달, 시장과 비교").appFont(15, .bold)
            HStack(spacing: 10) {
                cell("내 종목", c.mine)
                cell(c.name, c.mkt)
            }
            Text(abs(gap) < 0.001 ? "시장과 거의 같게 움직였어요" : "시장보다 \(String(format: "%.1f", abs(gap) * 100))% \(gap > 0 ? "높아요" : "낮아요")")
                .appFont(13).foregroundStyle(Theme.sub)
        }
        .card()
    }

    private func cell(_ t: String, _ v: Double) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(t).appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
            Text(AppModel.sgn(v)).appFont(20, .bold).foregroundStyle(Theme.change(v)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: 오늘 많이 움직인 종목 (오늘 % 크기순 3개)
    private func moversCard(_ rows: [AppModel.Row]) -> some View {
        let top = rows.sorted { abs($0.sym.quote.change) > abs($1.sym.quote.change) }.prefix(3)
        return VStack(alignment: .leading, spacing: 0) {
            Text("오늘 많이 움직인 종목").appFont(15, .bold).padding(.bottom, 6)
            if top.isEmpty { Text("종목을 넣으면 보여요").appFont(13).foregroundStyle(Theme.sub) }
            ForEach(Array(top)) { r in
                let d = r.sym.quote.change
                line(r, Text((d >= 0 ? "+" : "−") + String(format: "%.2f%%", abs(d) * 100)).foregroundColor(Theme.change(d)))
            }
        }
        .card()
    }

    // MARK: 1년 고점·저점 가까운 종목 (5% 안)
    private func nearCard(_ ks: [(AppModel.Row, AppModel.HoldMetrics)]) -> some View {
        let hi = ks.filter { $0.1.dd > -0.05 }.sorted { $0.1.dd > $1.1.dd }
        let lo = ks.filter { $0.1.dd <= -0.05 && $0.0.sym.last / max(1e-9, $0.1.lo) - 1 < 0.05 }
        return VStack(alignment: .leading, spacing: 0) {
            Text("1년 고점·저점 가까이").appFont(15, .bold).padding(.bottom, 6)
            if hi.isEmpty && lo.isEmpty { Text("고점·저점 5% 안에 있는 종목은 없어요").appFont(13).foregroundStyle(Theme.sub) }
            ForEach(hi, id: \.0.id) { r, k in
                line(r, Text(k.dd > -0.005 ? "1년 고점" : "고점까지 \(String(format: "%.0f", abs(k.dd) * 100))%").foregroundColor(Theme.up))
            }
            ForEach(lo, id: \.0.id) { r, k in
                line(r, Text("저점 위 \(String(format: "%.0f", (r.sym.last / max(1e-9, k.lo) - 1) * 100))%").foregroundColor(Theme.down))
            }
        }
        .card()
    }

    private func line(_ r: AppModel.Row, _ right: Text) -> some View {
        NavigationLink(value: r.sym) {
            HStack(spacing: 10) {
                LogoTile(symbol: r.id, size: 28)
                Text(r.sym.name).appFont(14, .semibold).lineLimit(1)
                Spacer(minLength: 4)
                right.appFont(14, .bold).monospacedDigit()
            }
            .frame(minHeight: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private extension View {
    func card() -> some View {
        frame(maxWidth: .infinity, alignment: .leading).padding(16)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }
}

// 작은 세상 첫 조각: 보유 종목이 동네 주민으로 서 있고, 누르면 대사창에 오르내림 한 줄.
// 가볍게: 그림은 멈춰 있고(애니메이션 없음), 대사창이 열렸을 때만 글자를 찍는다. 새로 받는 것 없음 (이미 받은 시세만)
struct TownView: View {
    @Environment(AppModel.self) private var m
    @AppStorage("town.arrive") private var arrive = ""
    @State private var talk: TownLook.Resident? = nil
    @State private var dress: TownLook.Resident? = nil
    @State private var us: TownLook.Resident? = nil
    @State private var typing = false          // 내 답을 쓰는 중
    @State private var kbTop: CGFloat = .infinity   // 키보드 윗변 (화면 좌표)
    @State private var talkBottom: CGFloat = 0      // 대사창 제자리 아랫변 (화면 좌표)
    @State private var fresh: Set<String> = []      // 오늘 이사 온 주민 (첫 대사가 인사)
    @State private var tick = 0                     // 2초마다 한 칸: 살짝 걷기·잡담 (가볍게, 화면에 있을 때만)
    @State private var step: [String: CGFloat] = [:]   // 주민마다 옆으로 옮겨 선 만큼 (움직인 주민만 바뀜)
    @State private var hop: String? = nil              // 이번 칸에 폴짝 뛴 주민
    @State private var order = Array(0..<13)           // 주민 → 자리 번호 (앱에 다시 들어올 때마다 섞는다)
    @Environment(\.scenePhase) private var phase
    private var book: TownBook { TownBook.shared }
    static let maxRes = 10      // 손가락으로 셀 만큼 (평가액 큰 순)
    // 주민 자리 (동네 판 안의 비율 좌표). 앞 번호부터 채운다
    private static let spots: [CGPoint] = [
        .init(x: 0.28, y: 0.36), .init(x: 0.54, y: 0.32), .init(x: 0.76, y: 0.40), .init(x: 0.40, y: 0.54),
        .init(x: 0.66, y: 0.58), .init(x: 0.22, y: 0.66), .init(x: 0.50, y: 0.74), .init(x: 0.78, y: 0.72),
        .init(x: 0.34, y: 0.82), .init(x: 0.64, y: 0.84), .init(x: 0.84, y: 0.26), .init(x: 0.16, y: 0.30),
        .init(x: 0.14, y: 0.48),
    ]

    private static let mayorSpot = CGPoint(x: 0.84, y: 0.26)      // 이장은 늘 오른쪽 위
    private static let visitSpot = CGPoint(x: 0.14, y: 0.48)      // 옛 이웃은 길 어귀에
    private static let warshSpot = CGPoint(x: 0.16, y: 0.28)      // 워시는 왼쪽 위 나무 곁
    private static let stage = CGPoint(x: 0.50, y: 0.48)          // 어닝콜 날 그 주민이 서는 가운데
    private var sky: TownSky { TownSky.shared }

    var body: some View {
        let all = TownLook.residents(m.rows)
        let res = Array(all.prefix(Self.maxRes))
        // 동네에 서 있는 이들: 주민 + 이장 + (오늘 놀러 온 옛 이웃)
        let mayor = TownLook.Resident(id: TownLook.mayorID, rows: [])
        let guest = book.visitor(except: Set(all.map(\.id))).map { TownLook.Resident(id: $0, rows: [], old: book.note($0).co ?? $0) }
        // 행사: 누군가의 어닝콜 날이면 주민이 그 주민 둘레로 모여 듣는다
        let host = res.first { TownLook.earnDay($0) != nil }
        let others = res.filter { $0.id != host?.id }
        let place: [(TownLook.Resident, CGPoint)] = host.map { h in
            [(h, Self.stage)] + others.enumerated().map { i, r in
                let a = Double(i) / Double(max(1, others.count)) * 2 * .pi + .pi / 2
                return (r, CGPoint(x: Self.stage.x + 0.26 * cos(a), y: Self.stage.y + 0.26 * sin(a) + 0.04))
            }
        } ?? res.enumerated().map { ($1, Self.spots[order[$0]]) }
        // 워시는 FOMC 앞뒤 며칠 동안 동네에 머문다
        // 이장·워시·옛 이웃도 주민 다음 빈자리에 선다 (앱에 다시 들어오면 같이 섞임). 모임 날엔 가장자리 제자리
        let extra = [(mayor, Self.mayorSpot)] + (TownLook.fomc() != nil ? [(TownLook.Resident(id: TownLook.warshID, rows: []), Self.warshSpot)] : [])
            + (guest.map { [($0, Self.visitSpot)] } ?? [])
        let folk: [(TownLook.Resident, CGPoint)] = place + extra.enumerated().map { k, e in
            (e.0, host == nil && res.count + k < Self.spots.count ? Self.spots[order[res.count + k]] : e.1)
        }
        VStack(spacing: 10) {
        GeometryReader { g in
            ZStack {
                ground(g.size).clipShape(RoundedRectangle(cornerRadius: 18))
                    // 배경을 누르면 키보드를 내리고 대사창도 닫는다 (쓰던 답은 '그만'처럼)
                    .onTapGesture {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                        talk = nil; typing = false
                    }
                ForEach(folk.indices, id: \.self) { i in
                    let r = folk[i].0, p = folk[i].1
                    // 잡담: 4칸(8초)마다 한 명이 2칸 동안 말풍선
                    let chat = tick % 4 < 2 && Int(TownLook.seed("chat\(tick / 4)") % UInt64(max(1, folk.count))) == i && talk == nil
                    Button { talk = r } label: {
                        TownFace(id: r.id, note: book.note(r.id), width: 48, height: 68)
                            .overlay(alignment: .topTrailing) {
                                // 기분: 별사탕 받은 날은 하트, 자기 종목이 내리면 시무룩
                                if r.id == host?.id { Text("🎤").appFont(13) }
                                else if book.cheered(r.id) { Text("♥").appFont(13, .bold).foregroundStyle(Color(red: 0.95, green: 0.35, blue: 0.45)) }
                                else if TownLook.mood(r) < 0 { Text("…").appFont(13, .bold).foregroundStyle(.white) }
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(TownLook.shown(r, book.note(r.id)))
                    .overlay(alignment: .top) {
                        if chat {
                            Text(TownLook.chat(r, tick / 4)).appFont(11).foregroundStyle(.black).lineLimit(1).fixedSize()
                                .padding(.horizontal, 6).padding(.vertical, 3)
                                .background(.white, in: RoundedRectangle(cornerRadius: 6))
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.black.opacity(0.6), lineWidth: 1.5))
                                .offset(y: -24).transition(.opacity).allowsHitTesting(false)
                        }
                    }
                    .offset(x: step[r.id] ?? 0, y: hop == r.id ? -3 : 0)
                    .animation(.easeInOut(duration: 1.6), value: tick)
                    .position(x: p.x * g.size.width, y: p.y * g.size.height - 34)
                }
                TownWeather(kind: sky.kind).allowsHitTesting(false)
                // 행사 띠: 어닝콜 모임 · 세계 기념일 (그날만)
                if let t = host.flatMap({ TownLook.earnDay($0) }).map({ "\($0) 발표 날 · 다 같이 듣는 중" }) ?? TownLook.holiday().map({ "오늘은 " + $0 }) {
                    Text(t).appFont(12, .bold).foregroundStyle(.white)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Color.black.opacity(0.35), in: Capsule())
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top).padding(.top, 8)
                        .allowsHitTesting(false)
                }
                if res.isEmpty {
                    Text("아직 이사 온 주민이 없어요").appFont(14).foregroundStyle(.white)
                }
                if let r = talk {
                    // 답을 쓰는 동안 대사창만 제자리에서 키보드 바로 위까지 올라간다 (동네는 고정)
                    let lift = typing ? min(0, kbTop - 8 - talkBottom) : 0
                    VStack {
                        Spacer()
                        TownTalk(res: r, note: book.note(r.id), hello: fresh.contains(r.id), typing: $typing, dress: { dress = r }, us: { us = r }) { talk = nil; typing = false }
                            .offset(y: lift)
                            .animation(.easeOut(duration: 0.25), value: lift)
                    }
                    .padding(10)
                    .background(GeometryReader { p in
                        Color.clear
                            .onAppear { talkBottom = p.frame(in: .global).maxY - 10 }
                            .onChange(of: p.frame(in: .global).maxY) { _, v in talkBottom = v - 10 }
                    })
                }
            }
        }
        .frame(height: 440)
        // 지난 대화(어제까지)는 대사창 아래 빈 공간에 목록으로. 대사창엔 오늘 한마디만
        if let r = talk { TownPast(id: r.id) }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { n in
            if let f = n.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect { kbTop = f.minY }
        }

        .task { await sky.refresh() }      // 날씨는 하루 한 번만 받는다
        .onChange(of: phase) { _, p in
            // 다른 앱에 갔다 돌아오면 주민들이 자리를 바꿔 서 있다
            if p == .active { order.shuffle(); step = [:] }
        }
        .task {
            // 화면에 있을 때만 2초에 한 번 (사라지면 저절로 멈춤)
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                tick += 1
                // 10초(5칸)에 한 번: 아무도 안 움직이거나, 한 명만 옆으로 걷거나 폴짝
                hop = nil
                guard tick % 5 == 0 else { continue }
                let ids = TownLook.residents(m.rows).prefix(Self.maxRes).map(\.id) + [TownLook.mayorID] + (TownLook.fomc() != nil ? [TownLook.warshID] : [])
                let n = [0, 1, 1].randomElement()!
                for id in ids.shuffled().prefix(n) {
                    if Bool.random() && hop == nil { hop = id }
                    else { let x = step[id] ?? 0; step[id] = x != 0 ? 0 : [-6, 6].randomElement()! }   // 한 걸음 나갔으면 다음엔 제자리로
                }
            }
        }
        .task(id: all.map(\.id).joined(separator: ",")) {
            // 다 판 주민은 떠난 날을 적어 옛 이웃으로 (시세·보유를 아직 못 읽었으면 건너뜀)
            if !m.rows.isEmpty { book.sync(all) }
            TownLook.host = res.first { TownLook.earnDay($0) != nil }.map { ($0.id, TownLook.shown($0, book.note($0.id))) }
            // 처음 본 주민은 오늘을 이사 온 날로 적는다 (수첩이 비어 있던 첫 실행은 인사 없이)
            let first = book.isEmpty
            for r in res where book.welcome(r.id) && !first { fresh.insert(r.id) }
            if !arrive.isEmpty {
                if let r = res.first(where: { $0.rows.contains { $0.id == arrive } }) { fresh.insert(r.id); talk = r }
                arrive = ""
            }
        }
        .sheet(item: $dress) { TownDress(res: $0) }
        .sheet(item: $us) { TownUs(res: $0) }
    }

    // 바닥: 풀밭과 흙길 + 나무 몇 그루 (그림 파일 없이 색만). 계절은 실제 달력으로 풀·잎 색만 바뀐다
    private static let trees: [CGPoint] = [.init(x: 0.07, y: 0.13), .init(x: 0.68, y: 0.10), .init(x: 0.95, y: 0.58), .init(x: 0.05, y: 0.93)]
    private func ground(_ s: CGSize) -> some View {
        let se = TownLook.season()
        let grass = [Color(red: 0.55, green: 0.78, blue: 0.43), Color(red: 0.40, green: 0.68, blue: 0.35),
                     Color(red: 0.66, green: 0.67, blue: 0.37), Color(red: 0.84, green: 0.88, blue: 0.91)][se]
        let leaf = [Color(red: 0.98, green: 0.74, blue: 0.82), Color(red: 0.20, green: 0.50, blue: 0.24),
                    Color(red: 0.92, green: 0.52, blue: 0.20), Color(red: 0.97, green: 0.98, blue: 1.0)][se]
        return ZStack {
            grass
            Path { p in
                p.move(to: .init(x: 0, y: s.height * 0.62))
                p.addCurve(to: .init(x: s.width, y: s.height * 0.42),
                           control1: .init(x: s.width * 0.35, y: s.height * 0.40), control2: .init(x: s.width * 0.65, y: s.height * 0.66))
            }
            .stroke(Color(red: 0.84, green: 0.74, blue: 0.53), lineWidth: 26)
            ForEach(0..<Self.trees.count, id: \.self) { i in
                let t = Self.trees[i]
                VStack(spacing: 0) {
                    RoundedRectangle(cornerRadius: 6).fill(leaf).frame(width: 34, height: 30)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.black.opacity(0.25), lineWidth: 2))
                    Rectangle().fill(Color(red: 0.45, green: 0.30, blue: 0.18)).frame(width: 7, height: 12)
                }
                .position(x: t.x * s.width, y: t.y * s.height)
            }
        }
    }
}

/// 주민 얼굴: 종목 코드를 씨앗으로 캐릭터 생성 부품을 즉석 조합 (저장 없음, 같은 종목은 늘 같은 얼굴)
enum TownLook {
    /// 주민 = 사람. 대표가 같은 종목(테슬라·스페이스X → 머스크)은 한 주민이 같이 맡는다. 대표 정보가 없으면 종목마다 한 명
    struct Resident: Identifiable { let id: String; let rows: [AppModel.Row]; var old: String? = nil   // old: 옛 이웃이면 그때 회사 이름
        var name: String { id == TownLook.mayorID ? "트럼프" : id == TownLook.warshID ? "연준 의장 워시" : old ?? rows.map(\.sym.name).joined(separator: " · ") }
        var value: Double { rows.reduce(0) { $0 + $1.value } }
    }
    static func residents(_ rows: [AppModel.Row]) -> [Resident] {
        var keys: [String] = [], by: [String: [AppModel.Row]] = [:]
        for r in rows.sorted(by: { $0.value > $1.value }) {
            let k = Sample.profiles[r.id].map { "ceo:" + $0.ceo.1 } ?? r.id
            if by[k] == nil { keys.append(k) }
            by[k, default: []].append(r)
        }
        return keys.map { Resident(id: $0, rows: by[$0]!) }.sorted { $0.value > $1.value }
    }
    /// 기본 별명: 회사 이름 앞 글자 + 꼬리 (같은 주민은 늘 같은 별명)
    static func nick(_ res: Resident) -> String {
        if res.id == mayorID { return "이장님" }
        if res.id == warshID { return "워시" }
        let base = (res.rows.first?.sym.name ?? res.old ?? res.id).replacingOccurrences(of: " ", with: "")
        let ko = base.unicodeScalars.first.map { (0xAC00...0xD7A3).contains($0.value) } ?? false
        let tails = ko ? ["돌이", "순이", "찡", "뽀", "군"] : ["y", "o", "ie", "ster"]
        return String(base.prefix(ko ? 2 : 4)) + tails[Int(seed(res.id) % UInt64(tails.count))]
    }
    static func shown(_ res: Resident, _ n: TownBook.Note) -> String { n.name ?? nick(res) }
    /// 기기·실행마다 같은 값이 나오는 해시 (Swift hashValue 는 실행마다 바뀐다)
    static func seed(_ s: String) -> UInt64 {
        var h: UInt64 = 0xcbf29ce484222325
        for b in s.utf8 { h = (h ^ UInt64(b)) &* 0x100000001b3 }
        return h
    }
    static func char(_ id: String) -> MeChar {
        var x = seed(id) | 1
        func next(_ n: Int) -> Int {   // xorshift
            x ^= x << 13; x ^= x >> 7; x ^= x << 17
            return Int(x % UInt64(max(1, n)))
        }
        var c = MeChar(name: "", parts: [:], type: "seri")
        for g in [MeGroup.hair, .face, .eyes, .mouth, .top, .bottom] { c.parts[g.rawValue] = 1 + next(g.count) }
        c.parts[MeGroup.color.rawValue] = 1 + next(MeColor.hair.count)
        c.parts["skin"] = 1 + next(MeColor.skin.count)
        c.parts[MeGroup.glasses.rawValue] = next(4) == 0 ? 1 + next(MeGroup.glasses.count) : 0
        c.parts[MeGroup.mask.rawValue] = 0
        c.parts[MeGroup.prop.rawValue] = 0
        // 유명인: 머리는 따로 그리니 부품 머리는 빼고, 피부·안경·윗옷만 맞춘다
        if let st = TownStar.find(id) {
            c.parts[MeGroup.hair.rawValue] = 0
            c.parts["skin"] = st.skin
            c.parts[MeGroup.glasses.rawValue] = st.glasses
            if st.top > 0 { c.parts[MeGroup.top.rawValue] = st.top }
        }
        return c
    }
    /// 하루 한마디: 언어별 문장 틀 + 빈칸({move} 오르내림 · {season} 계절 · {days} 함께한 날).
    /// 틀은 주민·날짜 씨앗으로 골라 날마다 바뀐다. 외부 생성·번역 없음 (옛 내비처럼 앱 안에서 조합)
    static let frames: [String: [String]] = ["ko": [
        "{season}이네요. {move}",
        "같이 지낸 지 {days}일째예요. {move}",
        "{move}\n오늘 마음은 어때요?",
        "{move}\n내일은 어떨 것 같아요?",
        "{season} 바람이 좋네요. {move}",
        "벌써 {days}일이나 함께했네요. {move}",
        "안녕하세요! {move}",
        "{move}\n한 줄 남겨 줄래요?",
        "왔어요? {move}",
        "{move}\n오늘은 어떤 하루였어요?",
        "{weather} {move}",
        "{weather}\n{move}",
    ]]
    /// 이장 = 큰손. 동네에 늘 있고 원·달러 이야기를 한다 (이미 받은 환율만)
    static let mayorID = "mayor"
    /// 워시: FOMC 5일 전부터 3일 뒤까지 상주. 남은 날(음수면 지난 날), 없으면 nil
    static let warshID = "warsh"
    static func fomc(_ now: Date = Date()) -> Int? {
        let from = Day.str(now.addingTimeInterval(-3 * 86400)), until = Day.str(now.addingTimeInterval(5 * 86400))
        guard let r = MarketEvents.rules.first(where: { $0.id == "x_fomc" }), let d = MarketEvents.dates(r, from: from, until: until).first else { return nil }
        return Day.dday(d)
    }
    static func warshLine() -> String {
        let n = fomc() ?? 0
        if n > 1 { return "FOMC까지 \(n)일 남았어요. 금리 얘기하러 미리 왔어요." }
        if n == 1 { return "내일이 FOMC예요. 다들 긴장되죠?" }
        if n == 0 { return "오늘 FOMC예요. 결과가 나오면 알려 줄게요." }
        return "FOMC 끝났어요. 며칠 더 머물다 갈게요."
    }
    static let mayorFrames = ["동네는 오늘도 평화로워요. {fx}", "{weather} {fx}", "{fx}\n다들 잘 지내죠?", "이장이에요. {fx}", "{fx}\n바다 건너 소식도 챙겨요."]
    /// 옛 이웃: 다 팔아 떠난 주민이 가끔 놀러 와 함께한 시간을 얘기한다
    static let oldFrames = ["오랜만이에요! 놀러 왔어요.\n{days}일 함께했던 거 기억나요?", "지나가다 들렀어요. 다들 잘 지내죠?", "{weather} 옛날 생각나서 왔어요.",
                            "여기 그대로네요. 반가워요!", "함께한 {days}일, 가끔 생각나요."]
    /// 잡담 말풍선: 짧은 혼잣말 (기분·자리로 고름)
    static func chat(_ res: Resident, _ k: Int) -> String {
        let pool: [String]
        if res.id == mayorID { pool = ["환율 좀 봐!", "동네 최고!", "다들 잘하고 있어", "아주 훌륭해"] }
        else if res.id == warshID { pool = ["금리는…", "데이터를 봐야죠", "음, 물가가…", "곧 FOMC"] }
        else if res.old != nil { pool = ["그대로네", "반가워!", "놀러 왔어"] }
        else if mood(res) < 0 { pool = ["흑…", "괜찮아질 거야", "오늘은 쉬자", "…"] }
        else { pool = ["♪", "날씨 좋다", "차트 봤어?", "음냐", "산책 중", "기분 좋아!", "하암…"] }
        return pool[Int(seed(res.id + "\(k)") % UInt64(pool.count))]
    }
    /// 계절: 0 봄 · 1 여름 · 2 가을 · 3 겨울 (실제 달력)
    static func season(_ d: Date = Date()) -> Int {
        let mo = Calendar.current.component(.month, from: d)
        return (3...5).contains(mo) ? 0 : (6...8).contains(mo) ? 1 : (9...11).contains(mo) ? 2 : 3
    }
    static func fxLine() -> String {
        let q = Market.shared.fx
        guard q.last > 0 else { return "오늘 환율은 아직 못 봤어요." }
        let w = String(format: "%.0f", q.last), d = q.last - q.prevClose
        if !q.live || abs(d) < 0.5 { return "원·달러는 \(w)원이에요." }
        return String(format: "원·달러는 \(w)원, 어제보다 %.0f원 ", abs(d)) + (d > 0 ? "올랐어요. 달러가 힘이 세졌네요." : "내렸어요. 원화가 힘을 냈네요.")
    }
    static func daily(_ res: Resident, _ n: TownBook.Note, lang: String = "ko") -> String {
        let wx = TownSky.shared.phrase
        func pick(_ a: [String]) -> String {
            let pool = a.filter { wx != nil || !$0.contains("{weather}") }
            return pool[Int(seed(res.id + TownLog.day) % UInt64(pool.count))].replacingOccurrences(of: "{weather}", with: wx ?? "")
        }
        let h = holiday().map { "오늘은 " + $0 + (batchim($0) ? "이에요!\n" : "예요!\n") } ?? ""
        if res.id == warshID { return h + warshLine() + "\n" + fxLine() }
        if res.id == mayorID { return h + pick(mayorFrames).replacingOccurrences(of: "{fx}", with: fxLine()) }
        // 행사: 어닝콜 날은 모두 모여 그 주민의 한 줄을 듣는다. 전날엔 예측 한 줄을 부탁한다
        if res.old == nil, earnDay(res) != nil {
            let y = Day.str(Date().addingTimeInterval(-86400))
            let mine = TownLog.shared.log(res.id).last { $0.day == y }?.me
            return "오늘은 제 실적 발표 날이에요! 다들 들으러 모였네요.\n" + (mine.map { "어제 남긴 예측: “\($0)”\n" } ?? "") + line(res)
        }
        if res.old == nil, earnDay(res, 1) != nil { return "내일은 제 실적 발표 날이에요.\n어떨 것 같아요? 예측 한 줄 남겨 줄래요?" }
        if res.old == nil, let ho = host, ho.id != res.id { return h + "오늘은 \(ho.name) 실적 날이라 다 같이 듣고 있어요.\n" + line(res) }
        // 몇 달 전 내 답이 있으면 그날은 그걸 꺼내 얘기한다
        if let back = TownLog.shared.recall(res.id, price(res)) { return res.old != nil ? back : back + "\n" + line(res) }
        if res.old != nil {
            let together = n.moved.isEmpty ? 0 : max(1, TownLog.age(n.moved) - TownLog.age(n.left ?? TownLog.day) + 1)
            return pick(oldFrames.filter { together > 1 || !$0.contains("{days}") }).replacingOccurrences(of: "{days}", with: "\(together)")
        }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        let days = f.date(from: n.moved).map { (Calendar.current.dateComponents([.day], from: $0, to: Date()).day ?? 0) + 1 } ?? 0
        let mo = Calendar.current.component(.month, from: Date())
        let season = (3...5).contains(mo) ? "봄" : (6...8).contains(mo) ? "여름" : (9...11).contains(mo) ? "가을" : "겨울"
        let all = frames[lang] ?? frames["ko"]!
        // 계절은 석 달 내내 같아서 매일 말하면 지겹다. 계절이 바뀐 첫 주에만 꺼낸다
        let fresh = [3, 6, 9, 12].contains(mo) && Calendar.current.component(.day, from: Date()) <= 7
        let t = pick(all.filter { (days > 1 || !$0.contains("{days}")) && (fresh || !$0.contains("{season}")) })
        return h + t.replacingOccurrences(of: "{move}", with: line(res))
            .replacingOccurrences(of: "{season}", with: season)
            .replacingOccurrences(of: "{days}", with: "\(days)")
    }
    /// 행사: 보유 종목 실적 날 (앱 일정표 MarketEvents 의 실적만, 바깥에서 따로 안 받음). ahead 0 오늘 · 1 내일
    static func earnDay(_ res: Resident, _ ahead: Int = 0) -> String? {
        guard res.id != mayorID, res.id != warshID, res.old == nil else { return nil }
        let d = Day.str(Date().addingTimeInterval(Double(ahead) * 86400))
        return MarketEvents.rules.first { r in r.id.hasPrefix("e_") && res.rows.contains { $0.id == r.target } && !MarketEvents.dates(r, from: d, until: d).isEmpty }?.kind
    }
    /// 오늘 모임의 주인공 (동네가 정해 둔다)
    static var host: (id: String, name: String)? = nil
    /// 세계 기념일 (달력 고정 날만, 앱 안 표)
    static let holidays: [String: String] = [
        "01-01": "새해 첫날", "02-14": "밸런타인데이", "03-08": "세계 여성의 날", "03-20": "국제 행복의 날",
        "04-22": "지구의 날", "05-01": "노동절", "06-05": "세계 환경의 날", "09-21": "세계 평화의 날",
        "10-24": "유엔의 날", "10-31": "핼러윈", "12-25": "크리스마스", "12-31": "한 해 마지막 날",
    ]
    static func holiday(_ d: Date = Date()) -> String? {
        let f = DateFormatter(); f.dateFormat = "MM-dd"
        return holidays[f.string(from: d)]
    }
    static func batchim(_ s: String) -> Bool {
        guard let c = s.unicodeScalars.last?.value, (0xAC00...0xD7A3).contains(c) else { return false }
        return (c - 0xAC00) % 28 != 0
    }
    /// 오르내림 한 줄. 오늘 시세가 있으면 오늘, 없으면 산 가격 대비
    /// 주민 대사: 맡은 종목이 여럿이면 종목 이름을 붙여 한 줄씩
    static func line(_ res: Resident) -> String {
        res.rows.count == 1 ? line(res.rows[0]) : res.rows.map { $0.sym.name + ", " + line($0) }.joined(separator: "\n")
    }
    static func line(_ r: AppModel.Row) -> String {
        let live = r.sym.quote.live
        let v = move(r)
        let pct = String(format: "%.1f%%", abs(v) * 100)
        let when = live ? "오늘" : "산 뒤로"
        if abs(v) < 0.001 { return live ? "오늘은 그대로예요. 조용한 하루네요." : "산 뒤로 그대로예요." }
        return v > 0 ? "\(when) \(pct) 올랐어요!" : "\(when) \(pct) 내렸어요…"
    }
    static func move(_ r: AppModel.Row) -> Double {
        r.sym.quote.live ? r.sym.quote.change : (r.cost > 0 ? r.value / r.cost - 1 : 0)
    }
    /// 기분: 맡은 종목 오르내림을 평가액으로 묶은 값. 0 아래면 시무룩
    static func mood(_ res: Resident) -> Double {
        let v = res.value
        return v > 0 ? res.rows.reduce(0) { $0 + move($1) * $1.value } / v : 0
    }
    /// 예측을 매길 값: 대표 종목의 지금 가격
    static func price(_ res: Resident) -> Double? {
        guard let p = res.rows.first?.sym.quote.last, p > 0 else { return nil }
        return p
    }
    /// 내 답에서 방향 읽기: 오른다 +1, 내린다 -1, 모르겠으면 0
    static func guess(_ s: String) -> Int {
        let up = ["오를", "오르", "올라", "오른", "상승", "반등", "떡상", "↑"]
        let dn = ["내릴", "내려", "내린", "떨어", "하락", "빠질", "빠져", "떡락", "↓"]
        let u = up.contains { s.contains($0) }, d = dn.contains { s.contains($0) }
        return u == d ? 0 : (u ? 1 : -1)
    }
}

/// 고전 RPG 대사창: 얼굴 · 이름 · 한 글자씩 찍히는 하루 한마디 · 내 답 한 줄. 위로 넘기면 지난 대사.
/// 대사를 누르면 다 찍기 → 한 번 더 누르면 닫기
struct TownTalk: View {
    let res: TownLook.Resident
    let note: TownBook.Note
    var hello = false
    @Binding var typing: Bool
    let dress: () -> Void
    let us: () -> Void
    let close: () -> Void
    @State private var shown = 0
    @State private var me = ""
    @State private var reply = false          // 대사 박스를 누르면 답장 칸이 열린다
    @FocusState private var focus: Bool
    @State private var say = ""
    private var log: TownLog { TownLog.shared }
    private var full: String { (hello ? "\(TownLook.shown(res, note))예요. 오늘 이사 왔어요! 이름이랑 모자 골라 줄래요?\n" : "") + say }
    private let gold = Color(red: 1, green: 0.85, blue: 0.4)
    var body: some View {
        let full = self.full
        // 스크린샷처럼: 왼쪽에 큰 초상화가 박스 위로 솟고, 이름표는 초상화 아래, 대사는 오른쪽
        ZStack(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 6) {
                Text(String(full.prefix(shown))).appFont(16).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 58, alignment: .topLeading)
                if reply && !say.isEmpty {
                    HStack(spacing: 8) {
                        TextField("", text: $me, prompt: Text("한 줄 답 (예측·마음)").foregroundStyle(.white.opacity(0.4)))
                            .appFont(14).foregroundStyle(.white).submitLabel(.done)
                            .focused($focus)
                            .onSubmit { log.answer(res.id, me, price: TownLook.price(res)) }
                        if focus {
                            Button("그만") { focus = false }.appFont(13).foregroundStyle(.white.opacity(0.7))
                            Button("남기기") { log.answer(res.id, me, price: TownLook.price(res)); focus = false }
                                .appFont(13, .bold).foregroundStyle(gold)
                        }
                    }
                    .padding(.horizontal, 8).padding(.vertical, 6)
                    .background(Color.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 4))
                }
            }
            .padding(.leading, 128).padding(.trailing, 12).padding(.vertical, 12)
            .background(Color(red: 0.10, green: 0.11, blue: 0.20), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(.white, lineWidth: 2))
            .contentShape(Rectangle())
            .onTapGesture {
                // 글자가 다 안 나왔으면 다 보여 주고, 다 나왔으면 답장 칸을 연다
                if shown < full.count { shown = full.count }
                else if !say.isEmpty { reply = true; focus = true }
            }
            // 큰 초상화: 박스 왼쪽에서 위로 솟는다 (누르는 건 박스가 받는다)
            VStack(spacing: -10) {
                TownFace(id: res.id, note: note, width: 110, height: 155)
                Text(TownLook.shown(res, note)).appFont(13, .bold).foregroundStyle(gold).lineLimit(1)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .frame(maxWidth: 116)
                    .background(Color(red: 0.10, green: 0.11, blue: 0.20), in: RoundedRectangle(cornerRadius: 6))
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(.white, lineWidth: 2))
            }
            .padding(.leading, 6).padding(.bottom, 6)
            .allowsHitTesting(false)
        }
        .overlay(alignment: .topTrailing) {
            if res.id != TownLook.mayorID && res.id != TownLook.warshID {
                HStack(spacing: 6) {
                    chip("우리 사이", us)
                    chip("꾸미기", dress)
                }
                .offset(y: -34)
            }
        }
        .onChange(of: focus) { _, v in
            typing = v
            // 남기지 않고 닫으면 쓰던 글은 버리고 남겨 둔 답으로
            if !v { me = log.log(res.id).last.flatMap { $0.day == TownLog.day ? $0.me : nil } ?? "" }
        }
        .task(id: res.id) {
            // 오늘 처음 말을 걸 때 한마디를 정해 기록에 남긴다 (하루 동안 같은 말)
            let e = log.today(res.id) { TownLook.daily(res, note) }
            say = e.say; me = e.me ?? ""
            reply = !me.isEmpty      // 오늘 남긴 답이 있으면 칸을 열어 둔다
            shown = 0
            let n = self.full.count
            while shown < n {
                try? await Task.sleep(nanoseconds: 45_000_000)
                if Task.isCancelled { return }
                shown += 1
            }
        }
    }

    private func chip(_ t: String, _ a: @escaping () -> Void) -> some View {
        Button(t, action: a).appFont(12, .bold).foregroundStyle(.white)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Color(red: 0.10, green: 0.11, blue: 0.20).opacity(0.85), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(.white.opacity(0.6)))
    }
}

/// 지난 대화: 어제까지의 주민 말·내 답을 대사창 아래 빈 공간에 목록으로 (최근 것이 위)
struct TownPast: View {
    let id: String
    private var log: TownLog { TownLog.shared }
    var body: some View {
        let today = TownLog.day
        let past = log.log(id).filter { $0.day != today }.reversed()
        if !past.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("지난 대화").appFont(13, .bold).foregroundStyle(Theme.sub)
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(past), id: \.day) { e in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(e.day.dropFirst(5).replacingOccurrences(of: "-", with: ".")).appFont(11).foregroundStyle(Theme.sub2)
                                Text(e.say).appFont(14)
                                if let a = e.me { Text("나: " + a).appFont(14).foregroundStyle(Theme.teal) }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(12)
                }
                .frame(maxHeight: 260)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

/// 주민 수첩: 별명 · 모자 · 모자 색 · 이사 온 날. 주민마다 몇 글자라 UserDefaults 한 칸에 JSON (떠나도 지우지 않는다: 옛 이웃)
@Observable final class TownBook {
    static let shared = TownBook()
    struct Note: Codable, Equatable { var name: String? = nil; var hat = 0; var tint = 0; var moved = ""; var gifts: Int? = nil; var cheer: String? = nil
        var co: String? = nil; var left: String? = nil }   // co: 회사 이름(떠나도 부르려고), left: 다 팔고 떠난 날
    private(set) var notes: [String: Note]
    private static let key = "town.book"
    private init() {
        notes = UserDefaults.standard.data(forKey: Self.key).flatMap { try? JSONDecoder().decode([String: Note].self, from: $0) } ?? [:]
    }
    /// 백업에서 불러온 뒤 다시 읽는다
    func reload() {
        notes = UserDefaults.standard.data(forKey: Self.key).flatMap { try? JSONDecoder().decode([String: Note].self, from: $0) } ?? [:]
        candy = UserDefaults.standard.integer(forKey: "town.candy")
    }
    var isEmpty: Bool { notes.isEmpty }
    func note(_ id: String) -> Note { notes[id] ?? Note() }
    func set(_ id: String, _ n: Note) {
        notes[id] = n
        if let d = try? JSONEncoder().encode(notes) { UserDefaults.standard.set(d, forKey: Self.key) }
    }
    /// 처음 본 주민이면 오늘을 이사 온 날로 적는다. 새로 왔으면 true
    func welcome(_ id: String) -> Bool {
        guard notes[id] == nil else { return false }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        set(id, Note(moved: f.string(from: Date())))
        return true
    }
    /// 별사탕: 답을 남긴 날마다 하나 (하루 하나). 시무룩한 주민에게 주면 그날 기운 차림
    private(set) var candy = UserDefaults.standard.integer(forKey: "town.candy")
    func earn() {
        let d = TownLog.day
        guard UserDefaults.standard.string(forKey: "town.candyDay") != d else { return }
        UserDefaults.standard.set(d, forKey: "town.candyDay")
        candy += 1; UserDefaults.standard.set(candy, forKey: "town.candy")
    }
    func give(_ id: String) {
        guard candy > 0 else { return }
        candy -= 1; UserDefaults.standard.set(candy, forKey: "town.candy")
        var n = note(id); n.gifts = (n.gifts ?? 0) + 1; n.cheer = TownLog.day
        set(id, n)
    }
    func cheered(_ id: String) -> Bool { notes[id]?.cheer == TownLog.day }
    /// 지금 주민과 수첩 맞추기: 있는 주민은 회사 이름을 적고 떠난 표시를 지운다, 없어진 주민은 오늘을 떠난 날로
    func sync(_ res: [TownLook.Resident]) {
        let now = Set(res.map(\.id))
        for r in res {
            guard var n = notes[r.id] else { continue }
            if n.co != r.name || n.left != nil { n.co = r.name; n.left = nil; set(r.id, n) }
        }
        for (id, var n) in notes where !now.contains(id) && n.left == nil && id != TownLook.mayorID {
            n.left = TownLog.day; set(id, n)
        }
    }
    /// 오늘 놀러 온 옛 이웃 (사흘에 한 번쯤, 날마다 한 명). 지금 주민은 빼고
    func visitor(except now: Set<String>) -> String? {
        let olds = notes.filter { $0.value.left != nil && !now.contains($0.key) }.keys.sorted()
        let s = TownLook.seed("visit" + TownLog.day)
        guard !olds.isEmpty, s % 3 == 0 else { return nil }
        return olds[Int((s / 3) % UInt64(olds.count))]
    }
}

/// 동네 날씨: 내 지역 실제 날씨를 하루 한 번만 받는다 (Open-Meteo, 키 없음). 위치는 대략(소수 한 자리)만 쓰고, 못 쓰면 서울
@Observable final class TownSky: NSObject, CLLocationManagerDelegate {
    static let shared = TownSky()
    enum Kind: Int { case clear, cloud, fog, rain, snow, storm }
    private(set) var kind: Kind? = nil
    @ObservationIgnored private let lm = CLLocationManager()
    @ObservationIgnored private var busy = false
    private override init() {
        super.init()
        let d = UserDefaults.standard
        if d.string(forKey: "town.skyDay") == TownLog.day { kind = Kind(rawValue: d.integer(forKey: "town.sky")) }
    }
    var phrase: String? {
        switch kind {
        case .clear: return "오늘은 맑네요."
        case .cloud: return "구름이 꼈네요."
        case .fog: return "안개가 자욱해요."
        case .rain: return "비가 오네요."
        case .snow: return "눈이 와요!"
        case .storm: return "천둥이 쳐요!"
        case nil: return nil
        }
    }
    @MainActor func refresh() async {
        let d = UserDefaults.standard
        guard d.string(forKey: "town.skyDay") != TownLog.day, !busy else { return }
        busy = true; defer { busy = false }
        if lm.authorizationStatus == .notDetermined {
            lm.delegate = self
            lm.desiredAccuracy = kCLLocationAccuracyReduced
            lm.requestWhenInUseAuthorization()      // 답이 오면 위치 → 다시 refresh
            return
        }
        if [.authorizedWhenInUse, .authorizedAlways].contains(lm.authorizationStatus), d.object(forKey: "town.lat") == nil {
            lm.delegate = self
            lm.desiredAccuracy = kCLLocationAccuracyReduced
            lm.requestLocation()                    // 처음 한 번만. 받으면 다시 refresh
            return
        }
        let lat = d.object(forKey: "town.lat") as? Double ?? 37.6, lon = d.object(forKey: "town.lon") as? Double ?? 127.0
        guard let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(lat)&longitude=\(lon)&current=weather_code"),
              let got = try? await URLSession.shared.data(from: url),
              let j = try? JSONSerialization.jsonObject(with: got.0) as? [String: Any],
              let c = (j["current"] as? [String: Any])?["weather_code"] as? Int else { return }
        let k: Kind = c <= 1 ? .clear : c <= 3 ? .cloud : c <= 48 ? .fog : (71...77).contains(c) || (85...86).contains(c) ? .snow : c >= 95 ? .storm : .rain
        kind = k
        d.set(k.rawValue, forKey: "town.sky"); d.set(TownLog.day, forKey: "town.skyDay")
    }
    func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        Task { @MainActor in if m.authorizationStatus != .notDetermined { await self.refresh() } }
    }
    func locationManager(_ m: CLLocationManager, didUpdateLocations l: [CLLocation]) {
        guard let c = l.last?.coordinate else { return }
        let lat = (c.latitude * 10).rounded() / 10, lon = (c.longitude * 10).rounded() / 10
        Task { @MainActor in
            UserDefaults.standard.set(lat, forKey: "town.lat"); UserDefaults.standard.set(lon, forKey: "town.lon")
            await self.refresh()
        }
    }
    func locationManager(_ m: CLLocationManager, didFailWithError e: Error) {
        Task { @MainActor in
            UserDefaults.standard.set(37.6, forKey: "town.lat"); UserDefaults.standard.set(127.0, forKey: "town.lon")   // 못 찾으면 서울
            await self.refresh()
        }
    }
}

/// 날씨 그림: 멈춘 빗줄기·눈송이·구름 그늘 (애니메이션 없음)
struct TownWeather: View {
    let kind: TownSky.Kind?
    var body: some View {
        Canvas { ctx, s in
            switch kind {
            case .rain?, .storm?:
                if kind == .storm { ctx.fill(Path(CGRect(origin: .zero, size: s)), with: .color(.black.opacity(0.18))) }
                for i in 0..<40 {
                    let x = CGFloat(TownLook.seed("r\(i)") % 1000) / 1000 * s.width, y = CGFloat(TownLook.seed("ry\(i)") % 1000) / 1000 * s.height
                    var p = Path(); p.move(to: .init(x: x, y: y)); p.addLine(to: .init(x: x - 4, y: y + 12))
                    ctx.stroke(p, with: .color(Color(red: 0.75, green: 0.85, blue: 1).opacity(0.7)), lineWidth: 1.5)
                }
            case .snow?:
                for i in 0..<36 {
                    let x = CGFloat(TownLook.seed("s\(i)") % 1000) / 1000 * s.width, y = CGFloat(TownLook.seed("sy\(i)") % 1000) / 1000 * s.height
                    ctx.fill(Path(CGRect(x: x, y: y, width: 4, height: 4)), with: .color(.white.opacity(0.9)))
                }
            case .cloud?, .fog?:
                ctx.fill(Path(CGRect(origin: .zero, size: s)), with: .color(Color.white.opacity(kind == .fog ? 0.28 : 0.0)))
                ctx.fill(Path(CGRect(origin: .zero, size: s)), with: .color(Color.black.opacity(kind == .cloud ? 0.10 : 0.0)))
            case .clear?:
                ctx.fill(Path(ellipseIn: CGRect(x: s.width * 0.42, y: 8, width: 22, height: 22)), with: .color(Color(red: 1, green: 0.86, blue: 0.35)))
            case nil: break
            }
        }
    }
}

/// 대화 기록(일기장): 주민마다 날짜 · 하루 한마디 · 내 답 한 줄. 몇 글자라 UserDefaults 한 칸에 JSON, 주민마다 최근 400일만
@Observable final class TownLog {
    static let shared = TownLog()
    struct Entry: Codable, Equatable { var day: String; var say: String; var me: String? = nil; var p: Double? = nil; var back: Bool? = nil }   // p: 답할 때 가격, back: 꺼내 봤음
    private(set) var logs: [String: [Entry]]
    private static let key = "town.log"
    private static let keep = 400
    private init() {
        logs = UserDefaults.standard.data(forKey: Self.key).flatMap { try? JSONDecoder().decode([String: [Entry]].self, from: $0) } ?? [:]
    }
    func reload() {
        logs = UserDefaults.standard.data(forKey: Self.key).flatMap { try? JSONDecoder().decode([String: [Entry]].self, from: $0) } ?? [:]
    }
    static var day: String { let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: Date()) }
    func log(_ id: String) -> [Entry] { logs[id] ?? [] }
    /// 오늘 한마디. 오늘 처음이면 make 로 정해 적는다
    func today(_ id: String, _ make: () -> String) -> Entry {
        let d = Self.day
        if let e = logs[id]?.last, e.day == d { return e }
        let e = Entry(day: d, say: make())
        var l = logs[id] ?? []
        l.append(e)
        if l.count > Self.keep { l.removeFirst(l.count - Self.keep) }
        logs[id] = l
        save()
        return e
    }
    /// 오늘 내 답 한 줄 (빈 칸이면 지움)
    func answer(_ id: String, _ me: String, price: Double? = nil) {
        guard var l = logs[id], let last = l.last, last.day == Self.day else { return }
        let t = me.trimmingCharacters(in: .whitespacesAndNewlines)
        let v: String? = t.isEmpty ? nil : String(t.prefix(60))
        guard v != last.me else { return }
        l[l.count - 1].me = v
        l[l.count - 1].p = v == nil ? nil : (last.p ?? price)
        logs[id] = l
        save()
        if v != nil { TownBook.shared.earn() }
    }
    static let ripe = 90    // 내 답을 꺼내고 예측을 매기는 날 수 (석 달)
    static func age(_ day: String) -> Int {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        return f.date(from: day).map { Calendar.current.dateComponents([.day], from: $0, to: Date()).day ?? 0 } ?? 0
    }
    /// 몇 달 전 내 답 하나 꺼내기 (오래된 것부터 한 번씩). 그때 가격과 견줘 맞혔는지
    func recall(_ id: String, _ price: Double?) -> String? {
        guard var l = logs[id], let i = l.firstIndex(where: { $0.me != nil && $0.back != true && Self.age($0.day) >= Self.ripe }),
              let me = l[i].me else { return nil }
        l[i].back = true
        logs[id] = l
        save()
        var s = "\(max(3, Self.age(l[i].day) / 30))달 전에 \"\(me)\"라고 했죠."
        if let p = l[i].p, let now = price, p > 0 {
            let v = now / p - 1
            s += String(format: " 그때보다 %.1f%% ", abs(v) * 100) + (v >= 0 ? "올랐어요." : "내렸어요.")
            let g = TownLook.guess(me)
            if g != 0 { s += (g > 0) == (v >= 0) ? " 맞혔네요!" : " 빗나갔네요." }
        }
        return s
    }
    /// 우리 사이: 남긴 답 · 매긴 예측(석 달 지난 것) · 맞힌 예측
    func score(_ id: String, _ price: Double?) -> (said: Int, judged: Int, hit: Int) {
        let mine = log(id).filter { $0.me != nil }
        var j = 0, h = 0
        if let now = price {
            for e in mine where Self.age(e.day) >= Self.ripe {
                guard let p = e.p, p > 0, let me = e.me else { continue }
                let g = TownLook.guess(me)
                if g == 0 { continue }
                j += 1
                if (g > 0) == (now >= p) { h += 1 }
            }
        }
        return (mine.count, j, h)
    }
    private func save() {
        if let d = try? JSONEncoder().encode(logs) { UserDefaults.standard.set(d, forKey: Self.key) }
    }
}

/// 주민 얼굴 + 모자. 모자는 그림 파일 없이 24×34 칸 위에 도트로 (테두리 한 칸 포함)
struct TownFace: View {
    let id: String
    let note: TownBook.Note
    var width: CGFloat
    var height: CGFloat
    static let hats = ["없음", "밀짚모자", "비니", "왕관", "야구모자", "꽃"]
    static let tints: [UInt32] = [0xE8B04B, 0xD94F4F, 0x4F7FD9, 0x4FB06A, 0x8A5FD0, 0xF2F2F2, 0x303030]
    var body: some View {
        MeAvatar(me: TownLook.char(id), width: width, height: height)
            .overlay {
                let star = TownStar.find(id)?.px ?? []
                if note.hat > 0 || !star.isEmpty {
                    Canvas { ctx, size in
                        let u = size.width / CGFloat(MeArt.W)
                        let c = Self.tints[min(note.tint, Self.tints.count - 1)]
                        let px = star + Self.pixels(note.hat, c)
                        func col(_ v: UInt32) -> Color { Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255) }
                        for r in px { ctx.fill(Path(CGRect(x: CGFloat(r.0 - 1) * u, y: CGFloat(r.1 - 1) * u, width: CGFloat(r.2 + 2) * u, height: CGFloat(r.3 + 2) * u)), with: .color(col(MeArt.outline))) }
                        for r in px { ctx.fill(Path(CGRect(x: CGFloat(r.0) * u, y: CGFloat(r.1) * u, width: CGFloat(r.2) * u, height: CGFloat(r.3) * u)), with: .color(col(r.4))) }
                    }
                    .allowsHitTesting(false)
                }
            }
    }
    /// (x, y, 너비, 높이, 색). y 는 위에서부터
    static func pixels(_ hat: Int, _ c: UInt32) -> [(Int, Int, Int, Int, UInt32)] {
        let dark = ((c >> 16 & 0xFF) * 7 / 10) << 16 | ((c >> 8 & 0xFF) * 7 / 10) << 8 | (c & 0xFF) * 7 / 10
        switch hat {
        case 1: return [(3, 5, 18, 1, c), (7, 2, 10, 3, c), (7, 4, 10, 1, dark)]
        case 2: return [(6, 2, 12, 4, c), (6, 5, 12, 1, dark), (11, 0, 2, 2, 0xFFFFFF)]
        case 3: return [(7, 3, 10, 2, c), (7, 1, 2, 2, c), (11, 0, 2, 3, c), (15, 1, 2, 2, c), (11, 3, 2, 1, 0xD94F4F)]
        case 4: return [(6, 2, 12, 3, c), (12, 5, 9, 1, dark)]
        case 5: return [(15, 2, 3, 3, c), (16, 3, 1, 1, 0xFFD54A)]
        default: return []
        }
    }
}

/// 유명인 주민: 부품 얼굴 위에 머리 모양만 따로 그린 도트를 얹는다 (풍자 캐리커처).
/// 주민 id 는 대표 이름("ceo:젠슨 황") 또는 종목 코드. 이장은 트럼프
enum TownStar {
    struct Look { var skin = 1; var glasses = 0; var top = 0; var px: [(Int, Int, Int, Int, UInt32)] }
    typealias R = (Int, Int, Int, Int, UInt32)
    /// 짧은 머리 기본꼴: 정수리 + 옆머리
    private static func crop(_ c: UInt32, _ d: UInt32, side: Int = 3) -> [R] { [(7, 3, 10, 1, d), (6, 4, 12, 2, c), (5, 5, 2, side, c), (17, 5, 2, side, c)] }
    static let looks: [String: Look] = {
        let brown: UInt32 = 0x6B4A2E, dbrown: UInt32 = 0x4A3220, black: UInt32 = 0x26201E, grey: UInt32 = 0xA9ADB4, silver: UInt32 = 0xD4D7DC
        var m: [String: Look] = [:]
        // 빅7
        m["musk"] = Look(px: crop(0x4A3426, 0x33241A) + [(6, 6, 5, 1, 0x4A3426)])
        m["jensen"] = Look(top: 5, px: crop(black, 0x111111, side: 4) + [(8, 4, 4, 1, grey), (14, 4, 2, 1, grey)])
        m["nadella"] = Look(skin: 2, glasses: 1, px: [(8, 4, 8, 1, grey), (5, 6, 2, 4, grey), (17, 6, 2, 4, grey)])
        m["zuck"] = Look(top: 5, px: [(7, 3, 10, 1, dbrown), (6, 4, 12, 2, brown), (6, 6, 12, 1, brown), (5, 5, 1, 3, brown), (18, 5, 1, 3, brown)])
        m["pichai"] = Look(skin: 2, px: [(8, 3, 8, 1, 0x111111), (6, 4, 12, 1, black), (5, 5, 2, 3, black), (17, 5, 2, 3, black)])
        m["jassy"] = Look(px: [(5, 7, 2, 3, 0x5A4A40), (17, 7, 2, 3, 0x5A4A40), (9, 5, 2, 1, 0xFFF4E6)])     // 민머리 + 반짝
        m["ternus"] = Look(px: crop(0x7A5134, 0x5A3A24) + [(12, 6, 5, 1, 0x7A5134)])
        // AI
        m["altman"] = Look(px: crop(brown, dbrown) + [(8, 2, 2, 1, brown), (12, 2, 2, 1, brown), (15, 2, 2, 1, brown)])
        m["amodei"] = Look(glasses: 1, px: [(6, 2, 12, 2, black), (5, 4, 14, 2, black), (4, 5, 2, 5, black), (18, 5, 2, 5, black), (7, 6, 3, 1, black), (13, 6, 3, 1, black)])
        m["hassabis"] = Look(px: [(8, 3, 8, 1, black), (6, 4, 12, 1, black), (5, 5, 2, 3, black), (17, 5, 2, 3, black)])
        // 금리·정치
        m["warsh"] = Look(top: 8, px: crop(silver, grey) + [(6, 6, 4, 1, silver), (11, 4, 1, 2, grey)])
        m["trump"] = Look(top: 8, px: [(6, 2, 12, 2, 0xE8C060), (4, 4, 16, 2, 0xE8C060), (4, 6, 6, 1, 0xE8C060), (4, 5, 2, 4, 0xE8C060), (18, 5, 2, 4, 0xE8C060),
                                       (12, 3, 6, 1, 0xC9A040), (11, 17, 2, 6, 0xD32F2F)])
        return m
    }()
    /// 주민 id → 얼굴 열쇠 (대표 이름이나 종목 코드로 찾는다)
    static let keys: [String: String] = [
        "ceo:일론 머스크": "musk", "TSLA": "musk", "ceo:젠슨 황": "jensen", "NVDA": "jensen", "ceo:사티아 나델라": "nadella", "MSFT": "nadella",
        "ceo:마크 저커버그": "zuck", "META": "zuck", "ceo:순다르 피차이": "pichai", "GOOGL": "pichai", "GOOG": "pichai",
        "ceo:앤디 재시": "jassy", "AMZN": "jassy", "ceo:존 터너스": "ternus", "ceo:팀 쿡": "ternus", "AAPL": "ternus",
        "ceo:샘 올트먼": "altman", "ceo:다리오 아모데이": "amodei", "ceo:데미스 허사비스": "hassabis",
        TownLook.warshID: "warsh", TownLook.mayorID: "trump",
    ]
    static func find(_ id: String) -> Look? { keys[id].flatMap { looks[$0] } }
}

/// 꾸미기: 이름 · 모자 · 모자 색. 저장하면 수첩에 적는다
struct TownDress: View {
    let res: TownLook.Resident
    @Environment(\.dismiss) private var dismiss
    @State private var n: TownBook.Note
    init(res: TownLook.Resident) { self.res = res; _n = State(initialValue: TownBook.shared.note(res.id)) }
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                TownFace(id: res.id, note: n, width: 96, height: 136)
                TextField(TownLook.nick(res), text: Binding(get: { n.name ?? "" }, set: { n.name = $0.isEmpty ? nil : String($0.prefix(10)) }))
                    .appFont(16).multilineTextAlignment(.center)
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(TownFace.hats.enumerated()), id: \.offset) { i, t in
                            Button { n.hat = i } label: {
                                Text(t).appFont(13, n.hat == i ? .bold : .regular)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(n.hat == i ? Theme.card : .clear, in: Capsule())
                                    .overlay(Capsule().stroke(Theme.border))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if n.hat > 0 {
                    HStack(spacing: 12) {
                        ForEach(Array(TownFace.tints.enumerated()), id: \.offset) { i, c in
                            Button { n.tint = i } label: {
                                Circle().fill(Color(red: Double(c >> 16 & 0xFF) / 255, green: Double(c >> 8 & 0xFF) / 255, blue: Double(c & 0xFF) / 255))
                                    .frame(width: 28, height: 28)
                                    .overlay(Circle().stroke(n.tint == i ? Color.primary : Theme.border, lineWidth: n.tint == i ? 2 : 1))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                Spacer()
            }
            .padding(20)
            .navigationTitle(res.name).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("저장") { TownBook.shared.set(res.id, n); dismiss() } }
            }
        }
        .presentationDetents([.medium])
    }
}

/// 우리 사이: 이사 온 날 · 함께한 날 · 내 답과 예측 적중 · 별사탕 주기 (시장 정보 아님)
struct TownUs: View {
    let res: TownLook.Resident
    @Environment(\.dismiss) private var dismiss
    private var book: TownBook { TownBook.shared }
    var body: some View {
        let n = book.note(res.id)
        let s = TownLog.shared.score(res.id, TownLook.price(res))
        let days = n.moved.isEmpty ? 0 : TownLog.age(n.moved) - TownLog.age(n.left ?? TownLog.day) + 1
        let sad = TownLook.mood(res) < 0
        let cheer = book.cheered(res.id)
        NavigationStack {
            VStack(spacing: 14) {
                TownFace(id: res.id, note: n, width: 72, height: 102)
                VStack(spacing: 0) {
                    row("이사 온 날", n.moved.replacingOccurrences(of: "-", with: "."))
                    row("함께한 날", "\(days)일")
                    if let l = n.left { row("떠난 날", l.replacingOccurrences(of: "-", with: ".")) }
                    row("남긴 답", "\(s.said)줄")
                    row("예측 적중", s.judged == 0 ? "석 달 뒤부터" : "\(s.hit) / \(s.judged)")
                    row("받은 별사탕", "\(n.gifts ?? 0)개")
                }
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                Button { book.give(res.id) } label: {
                    Text(cheer ? "기운 났어요! ♥" : sad ? "별사탕 주기 (\(book.candy)개)" : "지금은 기운이 넘쳐요")
                        .appFont(15, .bold).frame(maxWidth: .infinity).padding(12)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(cheer || !sad || book.candy == 0)
                Text("별사탕은 답을 남긴 날마다 하나씩 생겨요").appFont(12).foregroundStyle(.secondary)
                Spacer()
            }
            .padding(20)
            .navigationTitle(TownLook.shown(res, n)).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
    private func row(_ k: String, _ v: String) -> some View {
        HStack { Text(k).appFont(14).foregroundStyle(.secondary); Spacer(); Text(v).appFont(14, .bold) }
            .padding(.horizontal, 14).padding(.vertical, 10)
    }
}
