import SwiftUI

// 분석 탭 상세 화면 (시안 31~35판): 내 길 · 금리 시나리오 · 외부 요인 · 배당·세금 · 환율 영향 · 종목 한눈에. (인사이트는 앱에서 뺐다)
// 숫자는 모두 계산값이고 사거나 팔라는 문구는 넣지 않는다. 외부 요인·배당·환율은 웹과 같은 자료(사이트 stats.json·calc.json)와 같은 식.

// MARK: 공통

extension AppModel {
    // 내 길: 회복 = 본전을 1년 안에(적립 없음), 목표 = 목표 금액·목표일·매달 적립. 시작은 실제로 길을 시작한 날
    /// 내 길 시작한 날: 목표 루트는 목표 시작일(사이트와 같은 값), 회복 루트는 인터미션 시작일과 내 기록 첫날 중 이른 날. 모르면 오늘
    var pathStartDate: Date {
        let rec = (isSample ? nil : myRecord.first).flatMap { Day.date($0.d) }
        if isGoal { return min(Date(), goalFromDate) }
        let rs = interStartAt.map { Date(timeIntervalSince1970: $0) }
        let d = [rs, rec].compactMap { $0 }.min()
        return min(Date(), d ?? Date())
    }
    var pathStartText: String { let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = String(localized: "M월 d일"); return f.string(from: pathStartDate) }
    var pathStartMonth: String { String(localized: "\(String(describing: Calendar.current.component(.month, from: pathStartDate)))월") }
    /// pts = 실제 평가액 (시작부터 년, 원). 거래일마다 하나, 끝은 오늘 지금 평가액
    struct MyPath { let start: Double; let end: Double; let key: Double; let monthly: Double; let pts: [(Double, Double)]; let act: (Double) -> Double; let need: (Double) -> Double
        /// 시작일 뒤에 처음 넣은 국내 주식 합계 (들어온 돈, 원). 시작 대비에서 뺀다
        var krIn = 0.0
    }
    /// 두 날짜("yyyy-MM-dd") 사이 년 (웹 yearsBetween 과 같은 식)
    static func yrs(_ a: String, _ b: String) -> Double {
        guard let x = Day.date(a), let y = Day.date(b) else { return 0 }
        return y.timeIntervalSince(x) / (365.25 * 86400)
    }
    /// 내 길 실제 평가액 (웹 목표 진행 actualSeries 와 같은 식): 시작일 무렵부터 거래일마다
    /// 그날 수량(수량 기록, 없으면 지금 수량) × 그날 종가 × 그날 원/달러 종가. 과거 시세가 없는 종목은 지금 평가액 그대로
    /// 현금은 그대로 있다고 보고 날마다 더한다 (웹 goalPath 와 같다)
    func pathSeries(from start: String) -> [(d: String, v: Double)] {
        let rs = rows, today = Day.today, fxH = PriceHistory.of("FX"), cash = cashKrw
        let hs = rs.map { (r: $0, h: PriceHistory.of($0.id), t: Sync.siteTicker($0.id)) }
        let from = Day.str(Calendar.current.date(byAdding: .day, value: -10, to: Day.date(start) ?? Date()) ?? Date())
        var ds = Set<String>()
        for x in hs { if let h = x.h { for d in h.dates.reversed() { if d < from { break }; if d < today { ds.insert(d) } } } }
        return ds.sorted().map { d in
            let q = Lots.on(lots, d)
            let v = hs.reduce(0.0) { a, x in
                guard let h = x.h else { return a + x.r.value }
                guard let px = h.on(d) else { return a }
                let n = q.map { $0[x.t] ?? 0 } ?? x.r.h.qty
                return a + px * n * (x.r.sym.currency == .usd ? (fxH?.on(d) ?? Market.shared.valFx) : 1)
            }
            return (d: d, v: v + cash)
        }
    }
    var myPath: MyPath {
        let mEnd = isGoal ? goalSpan : 1     // 목표: 시작일부터 목표일까지 (사이트와 같은 날짜)
        let sD = Day.str(pathStartDate), today = Day.today
        // 시작한 날부터 오늘까지 (년). 오늘 시작이면 하루로 둔다
        let mS = min(mEnd * 0.95, max(1.0 / 365.25, Self.yrs(sD, today))), mM = isGoal ? gM * 1e4 : 0, K = keyValue
        let now = trackValue
        var pts: [(Double, Double)]
        if bare {
            pts = (0...30).map { i in let t = mS * Double(i) / 30; return (t, max(0, now - mM * 12 * (mS - t))) }
        } else if isSample {
            // 샘플(가상 종목 DRNK)은 날짜별 시세가 없어 지난 3년 흐름에서 어림한다
            pts = (0...30).map { i in let t = mS * Double(i) / 30; return (t, i == 30 ? now : self.totalAt(1 - (mS - t) / 3)) }
        } else {
            // 웹처럼 시작일 또는 그 뒤 첫 거래일 값에서 출발 (시작일 뒤 거래일이 아직 없으면 그 전 마지막 거래일)
            let S = pathSeries(from: sD)
            let i0 = S.firstIndex { $0.d >= sD } ?? max(0, S.count - 1)
            pts = []
            if !S.isEmpty {
                pts.append((0.0, S[i0].v))
                for x in S[(i0 + 1)...] { pts.append((Self.yrs(sD, x.d), x.v)) }
            }
            pts.append((mS, now))
        }
        let P = pts
        let act: (Double) -> Double = { t in
            if t >= mS { return now }
            return (P.last { $0.0 <= t } ?? P.first)?.1 ?? now
        }
        let vS = P.first?.1 ?? now, kAdj = K - mM * 12 * mEnd
        // 시작일 뒤에 처음 넣은 국내 주식 합계는 들어온 돈: 그날부터 길에도 그 금액을 더한다 (앞선 것으로 보이지 않게. 웹 goalPath 와 같다)
        let KL = CustomSymbols.shared.krLog
        let kin = krSum > 0 && !bare && !isSample && MC.krAt(KL, sD) == 0 ? KL.first(where: { $0.d > sD && $0.v > 0 }) : nil
        let tIn = kin.map { Self.yrs(sD, $0.d) } ?? .infinity, vIn = kin?.v ?? 0
        let need: (Double) -> Double = { t in mM * 12 * t + (kAdj > 0 && vS > 0 ? vS * pow(kAdj / vS, t / mEnd) : vS + (K - vS) * t / mEnd) + (t >= tIn ? vIn : 0) }
        return MyPath(start: mS, end: mEnd, key: K, monthly: mM, pts: P, act: act, need: need, krIn: vIn)
    }
    var myPathGapToday: Double { let p = myPath; let n = p.need(p.start); return n > 0 ? p.act(p.start) / n - 1 : 0 }

    static func manS(_ krw: Double) -> String { (krw >= 0 ? "+" : "−") + man(abs(krw)) }
    static func won1(_ krw: Double) -> String { en ? krwC(krw) : krw > 0 && krw < 1e4 ? Int(krw.rounded()).formatted() + String(localized: "원") : man(krw) }
    /// 읽기 쉬운 금액: 4만 6천원 · 9천원 (천원 단위 반올림, 1천만원 넘으면 man)
    static func wonRead(_ krw: Double) -> String {
        if en { return krwC(krw) }
        let k = Int((krw / 1e3).rounded())
        if k <= 0 { return won1(krw) }
        if k >= 10_000 { return man(krw) }
        let w = k / 10, c = k % 10
        return w == 0 ? String(localized: "\(String(describing: c))천원") : c == 0 ? String(localized: "\(String(describing: w))만원") : String(localized: "\(String(describing: w))만 \(String(describing: c))천원")
    }
    /// x 시점(0 = 3년 전, 1 = 어제)의 전체 평가액 (원화)
    func totalAt(_ x: Double) -> Double {
        rows.reduce(0) { $0 + krw($1.sym, $1.h.qty * prices.price($1.sym, at: x)) }
    }
    func weight(_ id: String) -> Double { rows.first { $0.id == id }.map { $0.value / max(1, total) } ?? 0 }
}

/// 상세 화면 머리: 제목은 위 막대 가운데(뒤로 가기 옆)에 두고, 여기에는 한 줄 설명만
struct DetailHead: View {
    let title: String
    let sub: String
    var body: some View {
        // 설명이 없으면 빈 Group 이라 제목이 막대에 안 붙었다: 높이 0 인 뷰라도 둔다
        Text(sub).appFont(14).foregroundStyle(Theme.sub)
            .frame(maxWidth: .infinity, maxHeight: sub.isEmpty ? 0 : nil, alignment: .leading)
            .opacity(sub.isEmpty ? 0 : 1)
            .modifier(DetailTitle(title: title))
    }
}

private struct Kpi: View {
    let k: String, v: String, sub: String
    var color: Color = Theme.ink
    var dark = false
    func darkened(_ on: Bool) -> Kpi { var k = self; k.dark = on; return k }
    var body: some View {
        let box = VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v).appFont(18, .bold).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.7).roll(v)
            Text(sub).appFont(11).foregroundStyle(Theme.muted)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        if dark {
            box.darkBox(14)
        } else {
            box.background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
        }
    }
}

private func kpiGrid(_ items: [Kpi], dark: Bool = false) -> some View {
    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
        ForEach(items.indices, id: \.self) { items[$0].darkened(dark) }
    }
}

private func footnote(_ t: String) -> some View {
    Text(t).appFont(12).foregroundStyle(Theme.muted).lineSpacing(2).frame(maxWidth: .infinity, alignment: .leading)
}

private func pinnedBox<C: View>(@ViewBuilder _ c: () -> C) -> some View {
    VStack(alignment: .leading, spacing: 10) { c() }
        .fixedSize(horizontal: false, vertical: true)
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card).overlay(alignment: .bottom) { Divider() }
}

// 0~1 정규 좌표 선 그리기
private func linePath(_ pts: [(Double, Double)], in size: CGSize) -> Path {
    Path { p in
        for (i, q) in pts.enumerated() {
            let pt = CGPoint(x: q.0 * size.width, y: q.1 * size.height)
            i == 0 ? p.move(to: pt) : p.addLine(to: pt)
        }
    }
}

// MARK: 닿는 때 줄 — 분석 탭 '어떻게 올릴까' · 모의바구니 구성에서 쓴다

/// 도달 시점 한 줄: 점 = 보통(가운데) 닿는 때, 막대 = 10번 중 8번(10~90%) 범위, 세로 점선 = 목표일. 위치는 연도 축 a0~a1
struct EtaRow: View {
    let name: String
    let sub: String
    let sim: GoalSim
    let a0: Double, a1: Double, goalYear: Double
    var strong = true

    var body: some View {
        let x = { (y: Double) in min(1, max(0, (y - a0) / max(0.5, a1 - a0))) }
        let lo = x(AppModel.yearAt(sim.etaLo)), hi = sim.etaHi.isFinite ? x(AppModel.yearAt(sim.etaHi)) : 1
        let mid = sim.etaMonths.isFinite ? x(AppModel.yearAt(sim.etaMonths)) : 1, gx = x(goalYear)
        let c = strong ? Theme.teal : Theme.muted
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(name).appFont(14, .bold)
                Spacer()
                Text(AppModel.whenText(sim.etaMonths, cap: sim.capYears)).appFont(13, .semibold).foregroundStyle(c)
            }
            GeometryReader { g in
                let w = g.size.width
                ZStack(alignment: .topLeading) {
                    Capsule().fill(Theme.border.opacity(0.5)).frame(width: w, height: 2).offset(y: 9)
                    Capsule().fill(c.opacity(0.35)).frame(width: max(6, (hi - lo) * w), height: 6).offset(x: lo * w, y: 7)
                    Circle().fill(c).frame(width: 12, height: 12).offset(x: mid * w - 6, y: 4)
                    Path { $0.move(to: CGPoint(x: gx * w, y: 0)); $0.addLine(to: CGPoint(x: gx * w, y: 20)) }
                        .stroke(Theme.ink, style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                }
                .animation(.easeInOut(duration: 0.25), value: sim.etaMonths)
            }
            .frame(height: 20)
            if !sub.isEmpty { Text(sub).appFont(11).foregroundStyle(Theme.muted).lineLimit(2) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(name), 보통 \(AppModel.whenText(sim.etaMonths, cap: sim.capYears)), 10번 중 8번은 \(AppModel.whenText(sim.etaLo, cap: sim.capYears))에서 \(AppModel.whenText(sim.etaHi, cap: sim.capYears)) 사이")
    }

    /// 여러 줄을 감싸는 연도 축 (목표일 포함, 정수 해)
    static func axis(_ sims: [GoalSim], goalYear: Double) -> (Double, Double) {
        var lo = goalYear, hi = goalYear
        for s in sims {
            lo = min(lo, AppModel.yearAt(s.etaLo))
            hi = max(hi, AppModel.yearAt(s.etaHi.isFinite ? s.etaHi : Double(s.capYears * 12)))
        }
        return (floor(lo), max(floor(lo) + 2, ceil(hi)))
    }
}

/// 연도 눈금 (a0~a1, 2년마다)
struct EtaTicks: View {
    let a0: Double, a1: Double
    var body: some View {
        let step = a1 - a0 > 6 ? 2 : 1
        let ys = Array(stride(from: Int(a0), through: Int(a1), by: step))
        GeometryReader { g in
            ForEach(ys, id: \.self) { y in
                Text(String(y)).appFont(11).foregroundStyle(Theme.muted).fixedSize()
                    .position(x: min(g.size.width - 14, max(14, (Double(y) - a0) / max(0.5, a1 - a0) * g.size.width)), y: 7)
            }
        }
        .frame(height: 14)
        .accessibilityHidden(true)
    }
}

/// 비중 바꿔 비교 · 종목 추가: 빈칸이면 자주 쓰는 목록, 찾으면 미국·한국 종목 아무거나. 사이트 표에 없는 종목은 3년 종가를 받아 앱에서 계산한다 (ExtraStats)
struct CompareAddSheet: View {
    let have: Set<String>
    let add: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var remote: [StockHit] = []
    @State private var searching = false
    @State private var loading: String? = nil
    @State private var failed: String? = nil

    var body: some View {
        let q = query.trimmingCharacters(in: .whitespaces)
        let local = q.isEmpty ? [] : StockSearch.local(q)
        let hits = (local + remote.filter { r in !local.contains { $0.id == r.id } }).filter { !have.contains($0.id) }
        NavigationStack {
            List {
                if q.isEmpty {
                    ForEach(Parallel.universe.filter { !have.contains($0.id) }, id: \.id) { u in
                        Button { add(u.id); dismiss() } label: { line(u.name, u.id, busy: false) }
                    }
                } else {
                    ForEach(hits) { h in
                        Button { Task { await pick(h) } } label: { line(h.name, h.id + " · " + h.market, busy: loading == h.id) }
                            .disabled(loading != nil)
                    }
                    if searching { HStack(spacing: 8) { ProgressView(); Text("찾는 중").appFont(13).foregroundStyle(Theme.sub) } }
                    else if hits.isEmpty { Text("없음 · 이름·티커·종목 코드로").appFont(13).foregroundStyle(Theme.sub) }
                }
                if let f = failed { Text(f).appFont(13).foregroundStyle(Theme.orange) }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: String(localized: "이름·티커·종목 코드"))
            .task(id: query) { await search() }
            .navigationTitle("종목 추가").toolbarTitleDisplayMode(.inlineLarge)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }

    private func line(_ name: String, _ sub: String, busy: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(name).appFont(15, .bold).foregroundStyle(Theme.ink).lineLimit(1)
                Text(sub).appFont(12).foregroundStyle(Theme.sub)
            }
            Spacer()
            if busy { ProgressView() }
        }
    }

    private func search() async {
        remote = []
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty, q.unicodeScalars.allSatisfy({ $0.isASCII }) else { searching = false; return }
        searching = true
        try? await Task.sleep(for: .milliseconds(400))
        guard !Task.isCancelled else { return }
        let r = await StockSearch.remote(q)
        guard !Task.isCancelled else { return }
        remote = r; searching = false
    }

    /// 사이트 표에 있으면 바로, 없으면 3년 종가를 받은 뒤 넣는다
    private func pick(_ h: StockHit) async {
        failed = nil
        if Market.shared.stats?.tickers[SiteStats.mcKey(h.id)] != nil { add(h.id); dismiss(); return }
        loading = h.id
        defer { loading = nil }
        do { try await ExtraStats.shared.fetch(h); add(h.id); dismiss() }
        catch { failed = String(localized: "\(String(describing: h.name)) 시세 못 받음 · 다시 눌러 주세요") }
    }
}

// MARK: 모의바구니 구성 (B-2안). 저장하면 모의바구니(담아 본 나)가 된다

struct MyPathCompareView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var w0: [String: Int] = [:]
    @State private var w: [String: Int] = [:]
    @State private var order: [String] = []
    @State private var loaded = false
    @State private var adding = false

    var body: some View {
        let sum = w.values.reduce(0, +)
        let yrsE = m.etaYears
        let nowSim = m.simulate(m.nowMix, years: yrsE)
        let parts: [AppModel.Part] = order.compactMap { id in
            guard let x = w[id], x > 0 else { return nil }
            let usd = m.rows.first { $0.id == id }.map { $0.sym.currency == .usd } ?? ExtraStats.shared.usd(id) ?? true
            return (key: SiteStats.mcKey(id), ccy: usd ? "USD" : "KRW", w: Double(x) / Double(max(1, sum)))
        }
        let newSim = w == w0 || parts.isEmpty ? nowSim : m.simulate(m.nowMix, years: yrsE, parts: parts)
        let goalMo = max(0, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12)
        let goalYear = AppModel.yearAt(goalMo)
        let ax = EtaRow.axis([nowSim, newSim], goalYear: goalYear), a0 = ax.0, a1 = ax.1
        let gc = Calendar.current.dateComponents([.year, .month], from: m.goalToDate)
        let canSave = sum == 100 && m.pworld != nil && w.filter { $0.value > 0 }.keys.allSatisfy { Parallel.has($0) }
        // 결과 문구: 얼마나 달라졌는지를 숫자로 (지금 비중 · 목표일과 비교)
        let cap = newSim.capYears, eN = newSim.etaMonths, e0 = nowSim.etaMonths
        let when = AppModel.whenText(eN, cap: cap)
        let headline: String = {
            guard eN.isFinite else { return String(localized: "\(String(describing: cap))년 안에 닿기 어려워요") }
            guard e0.isFinite else { return String(localized: "지금보다 빨라요 · \(when)쯤") }
            let d = Int((eN - e0).rounded())
            if d == 0 { return String(localized: "지금과 거의 같아요 · \(when)쯤") }
            return d < 0 ? String(localized: "지금보다 \(Self.span(-d)) 빨라요 · \(when)쯤")
                : String(localized: "지금보다 \(Self.span(d)) 늦어요 · \(when)쯤")
        }()
        let goalLine: String? = {
            guard eN.isFinite else { return nil }
            let d = Int((eN - goalMo).rounded())
            if d == 0 { return String(localized: "목표일 즈음이에요") }
            return d < 0 ? String(localized: "목표일보다 \(Self.span(-d)) 앞서요") : String(localized: "목표일보다 \(Self.span(d)) 늦어요")
        }()
        let sprN = newSim.etaHi - newSim.etaLo, spr0 = nowSim.etaHi - nowSim.etaLo   // 무한대 = 기간 안에 못 닿는 경우가 섞임
        let spread: String = {
            let how = sprN.isFinite && spr0.isFinite && abs(sprN - spr0) < 6 || !sprN.isFinite && !spr0.isFinite ? String(localized: "흔들림은 지금과 비슷해요")
                : !sprN.isFinite || (spr0.isFinite && sprN > spr0) ? String(localized: "흔들림이 커져요") : String(localized: "흔들림이 줄어요")
            return how + " · " + String(localized: "빠르면 \(AppModel.whenText(newSim.etaLo, cap: cap)), 늦으면 \(AppModel.whenText(newSim.etaHi, cap: cap))")
        }()

        VStack(spacing: 0) {
        SheetTitleBar(title: "모의바구니 구성") { dismiss() }
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Card {
                    HStack {
                        Text("비중").appFont(15, .bold); Spacer()
                        Button("처음으로") { w = w0; order = order.filter { w0[$0] != nil } }.appFont(13, .semibold).foregroundStyle(Theme.teal)
                    }
                    GeometryReader { g in
                        HStack(spacing: 0) {
                            ForEach(order.indices, id: \.self) { i in
                                Rectangle().fill(Theme.holdColors[i % Theme.holdColors.count])
                                    .frame(width: g.size.width * Double(w[order[i]] ?? 0) / Double(max(100, sum)))
                            }
                            Spacer(minLength: 0)
                        }
                        .animation(.easeInOut(duration: 0.25), value: w)
                    }
                    .frame(height: 12).clipShape(Capsule())
                    ForEach(order.indices, id: \.self) { i in row(order[i], color: Theme.holdColors[i % Theme.holdColors.count]) }
                    Button { adding = true } label: {
                        Text("+ 종목 추가").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 40)
                    }
                    .sheet(isPresented: $adding) {
                        CompareAddSheet(have: Set(w.keys)) { id in w[id] = 0; order.append(id) }
                    }
                    Text(sum == 100 ? String(localized: "합계 100%") : String(localized: "합계 \(String(describing: sum))% · 100%로 맞추기"))
                        .appFont(13, .bold).foregroundStyle(sum == 100 ? Theme.teal : Color(hex: 0x8A4614, dark: 0xE8A060))
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background((sum == 100 ? Theme.teal : Theme.orange).opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                }

                Card {
                    Text("닿는 때").appFont(15, .bold)
                    EtaTicks(a0: a0, a1: a1)
                    EtaRow(name: String(localized: "지금 비중"), sub: "", sim: nowSim, a0: a0, a1: a1, goalYear: goalYear, strong: false)
                    EtaRow(name: String(localized: "바꾼 비중"), sub: "", sim: newSim, a0: a0, a1: a1, goalYear: goalYear, strong: true)
                    Text("점선 = 목표일 \(String(gc.year ?? 0)).\(gc.month ?? 0) · 점 보통 · 막대 10번 중 8번").appFont(11).foregroundStyle(Theme.muted)
                }

                Card {
                    Text(headline).appFont(16, .bold)
                    if let goalLine { Text(goalLine).appFont(13).foregroundStyle(Theme.sub) }
                    Text(spread).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    m.saveBasket(w.filter { $0.value > 0 }.mapValues { Double($0) })
                    dismiss()
                } label: {
                    Text("저장").appFont(16, .bold).foregroundStyle(canSave ? Color.white : Theme.sub)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(canSave ? Theme.teal : Theme.border, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain).disabled(!canSave)
                if m.pworld == nil {
                    Text("평행 세계를 열면 저장 가능").appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }
                Text("과거 수익률 기준 · 투자 권유 아님").appFont(12).foregroundStyle(Theme.muted)
            }
            .padding(16)
        }
        }
        .background(Theme.bg)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            let t = max(1, m.rows.reduce(0) { $0 + $1.value })
            var x: [String: Int] = [:]
            for r in m.rows { x[r.id, default: 0] += Int((r.value / t * 100).rounded()) }
            if let top = x.max(by: { $0.value < $1.value })?.key { x[top, default: 0] += 100 - x.values.reduce(0, +) }
            order = m.rows.map(\.id).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
            w0 = x; w = x
        }
    }

    /// 개월 수를 "8개월" · "1년 3개월"로
    static func span(_ mo: Int) -> String {
        mo < 12 ? String(localized: "\(String(describing: mo))개월")
            : mo % 12 == 0 ? String(localized: "\(String(describing: mo / 12))년")
            : String(localized: "\(String(describing: mo / 12))년 \(String(describing: mo % 12))개월")
    }

    private func row(_ id: String, color: Color) -> some View {
        let r = m.rows.first { $0.id == id }
        let name = r?.sym.name ?? Parallel.universe.first { $0.id == id }?.name ?? ExtraStats.shared.name(id) ?? id
        let kind = r?.sym.sectorText ?? String(localized: "새로 담음")
        let d = (w[id] ?? 0) - (w0[id] ?? 0)
        return HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 12, height: 12)
            // 종목 이름 옆에 분류·변화 (한 줄이라 줄 높이가 낮다)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(name).appFont(14, .bold).lineLimit(1)
                Text("\(kind) · \(d == 0 ? String(localized: "그대로") : d > 0 ? "+\(d)%p" : "\(d)%p")").appFont(11).foregroundStyle(Theme.muted).lineLimit(1)
                    .layoutPriority(-1)
            }
            Spacer(minLength: 4)
            Button { w[id] = max(0, (w[id] ?? 0) - 5) } label: { Image(systemName: "minus").frame(width: 30, height: 24) }
                .buttonStyle(.bordered).accessibilityLabel("\(name) 비중 5% 줄이기")
            Text("\(w[id] ?? 0)%").appFont(14, .bold).frame(width: 44)
            Button { w[id] = min(100, (w[id] ?? 0) + 5) } label: { Image(systemName: "plus").frame(width: 30, height: 24) }
                .buttonStyle(.bordered).accessibilityLabel("\(name) 비중 5% 늘리기")
        }
    }
}

private func legend(_ c: Color, _ t: String) -> some View {
    HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12); Text(t) }
        .appFont(12).foregroundStyle(Theme.sub)
}

// MARK: 외부 요인 — 시장·금리·환율이 움직이면

struct ExternalView: View {
    @Environment(AppModel.self) private var m

    /// 시장 민감도(베타): 사이트가 웹과 같은 식(factorBetas)으로 매일 낸 값. 사이트 자료가 없는 종목(직접 추가한 종목)은
    /// 같은 회귀를 앱이 가진 지난 3년 종가로 한다. 둘 다 없으면 nil
    static func mktBeta(_ id: String) -> Double? { SiteCalc.shared.beta("mkt", id) ?? estBeta(id) }
    /// (시장 베타, 미국 10년 금리 +1%p 일 때 %, 달러 노출). 금리 시나리오 화면이 시장 베타를 쓴다 (웹처럼 모르면 1)
    static func coef(_ s: Symbol) -> (Double, Double, Double) {
        ((mktBeta(s.id) ?? 1), (SiteCalc.shared.beta("rate", s.id).map { SiteCalc.factorMove("rate", beta: $0, shock: 100) * 100 } ?? 0), s.currency == .usd ? 1 : 0)
    }
    static func estBeta(_ id: String) -> Double? {
        guard let h = PriceHistory.of(id), let spy = PriceHistory.of("SPY") else { return nil }
        // 한국 장은 미국 장보다 먼저 끝나서, 한국 종목은 그 전날 밤 미국 장 움직임과 짝을 짓는다
        let kr = Sample.symbol(id)?.currency == .krw
        let usDays = spy.dates
        var spRet: [String: Double] = [:]
        for i in 1..<max(1, spy.closes.count) where spy.closes[i - 1] > 0 {
            let r: Double = log(spy.closes[i] / spy.closes[i - 1])
            spRet[usDays[i]] = r
        }
        func prevUS(_ d: String) -> String? {
            var lo = 0, hi = usDays.count - 1, ans: String? = nil
            while lo <= hi { let mid = (lo + hi) / 2; if usDays[mid] < d { ans = usDays[mid]; lo = mid + 1 } else { hi = mid - 1 } }
            return ans
        }
        var xs: [Double] = [], ys: [Double] = []
        for i in 1..<h.closes.count {
            guard h.closes[i - 1] > 0, let key = kr ? prevUS(h.dates[i]) : h.dates[i], let x = spRet[key] else { continue }
            if kr, let pk = prevUS(h.dates[i - 1]), pk == key { continue }     // 미국 장이 쉰 날은 건너뛴다
            xs.append(x); ys.append(log(h.closes[i] / h.closes[i - 1]))
        }
        guard xs.count >= 30 else { return nil }
        let mx = xs.reduce(0, +) / Double(xs.count), my = ys.reduce(0, +) / Double(ys.count)
        let cov = zip(xs, ys).reduce(0) { $0 + ($1.0 - mx) * ($1.1 - my) }, vx = xs.reduce(0) { $0 + ($1 - mx) * ($1 - mx) }
        guard vx > 0 else { return nil }
        let w = Double(xs.count) / Double(xs.count + 60)      // 웹과 같게 이력이 짧으면 1 쪽으로 당긴다
        return w * cov / vx + (1 - w)
    }

    /// 웹 renderSens 와 같은 식: 종목 가격 변화 = exp(β × 요인 변화) − 1, 원/달러는 달러 종목에 그대로
    static func move(_ factor: String, _ id: String, _ sym: Symbol, _ v: Double) -> Double? {
        switch factor {
        case "fx": return sym.currency == .usd ? v / Market.shared.fx.last : 0
        case "mkt": return mktBeta(id).map { SiteCalc.factorMove("mkt", beta: $0, shock: v) }
        case "rate": return SiteCalc.shared.beta("rate", id).map { SiteCalc.factorMove("rate", beta: $0, shock: v * 100) }
        default: return SiteCalc.shared.beta(factor, id).map { SiteCalc.factorMove(factor, beta: $0, shock: v) }
        }
    }

    /// 요인의 '보통 한 달 변화'를 요인 상세와 같은 단위로 (금리 %p, 원/달러 원, 나머지 %)
    static func monthShock(_ k: String) -> Double? {
        guard let sd = SiteCalc.shared.factorMonth(k)?.sd, sd > 0 else { return nil }
        switch k {
        case "rate": return sd / 100
        case "fx": return Market.shared.fx.last * (exp(sd) - 1)
        default: return (exp(sd) - 1) * 100
        }
    }
    private struct Rank { let k: String; let amt: Double; let gross: Double; let weak: Bool }

    private var ranks: [Rank] {
        let total = max(1, m.total)
        return ["mkt", "fx", "rate", "oil", "gold"].map { k -> Rank in
            guard let v = Self.monthShock(k) else { return Rank(k: k, amt: 0, gross: 0, weak: true) }
            let dvs = m.rows.map { r in r.value * (Self.move(k, r.id, r.sym, v) ?? 0) }
            let tot = dvs.reduce(0, +), gross = dvs.reduce(0) { $0 + abs($1) }
            let agreeWeak = k != "fx" && m.rows.allSatisfy { r in SiteCalc.shared.betaStat(k, r.id)?.agree.map { abs($0 - 0.5) < FactorDetailView.weakAgree } ?? true }
            return Rank(k: k, amt: abs(tot), gross: gross, weak: abs(tot) < total * FactorDetailView.tiny || agreeWeak)
        }.sorted { ($0.weak ? 0 : 1, $0.amt) > ($1.weak ? 0 : 1, $1.amt) }
    }

    /// 요인 한 줄 설명 (숫자 맞추기 G 가 부른다. 화면에는 이제 안 쓴다)
    private func desc(_ x: Rank) -> String {
        if x.weak { return "" }
        let tot = max(1, m.total)
        switch x.k {
        case "mkt":
            let b = m.rows.reduce(0) { $0 + $1.value / tot * (Self.mktBeta($1.id) ?? 1) }
            return b > 1.15 ? String(localized: "시장의 \(String(describing: String(format: "%.1f", b)))배로 움직임") : b < 0.85 ? String(localized: "시장보다 작게 움직임") : String(localized: "시장과 비슷하게 움직임")
        case "fx":
            let usd = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.value } / tot
            return String(localized: "달러 \(String(describing: AppModel.pct(usd))) · 급락 땐 방패")
        default:
            if x.gross > x.amt * 2 { return String(localized: "종목별 ±\(String(describing: AppModel.man(x.gross / 2))) · 서로 상쇄") }
            return String(localized: "움직이면 대부분 같은 쪽")
        }
    }

    private func summary(_ rs: [Rank]) -> String? {
        let strong = rs.filter { !$0.weak }
        guard let top = strong.first, top.amt > 0 else { return nil }
        let name = FactorDetailView.names[top.k] ?? ""
        let rest = strong.dropFirst().reduce(0) { $0 + $1.amt }
        if strong.count == 1 { return String(localized: "사실상 \(String(describing: name)) 하나") }
        if rest < top.amt / 3 { return String(localized: "거의 \(String(describing: name)) 때문 · 나머지 합쳐도 1/3 미만") }
        if rest < top.amt { return (TownLook.batchim(name) ? String(localized: "\(String(describing: name))이 가장 큼 · 나머지 합보다 큼") : String(localized: "\(String(describing: name))가 가장 큼 · 나머지 합보다 큼")) }
        return String(localized: "\(String(describing: name))·\(String(describing: FactorDetailView.names[strong[1].k] ?? "")) 비슷")
    }

    private static let short = ["mkt": String(localized: "시장"), "fx": String(localized: "환율"), "rate": String(localized: "금리"), "oil": String(localized: "유가"), "gold": String(localized: "금값")]

    /// 그래프 깃발로 들어올 때 맨 위에 띄울 일 ("id.day"). 화면이 열리며 읽고 비운다
    static var focus: String?
    @State private var focusKey: String? = ExternalView.focus

    /// 무엇이 흔드나: 뚜렷한 요인만 한 막대에 나눠 담는다 (한 달 기준). 이름을 누르면 요인 상세
    private func shakeCard(_ rs: [Rank]) -> some View {
        let strong = rs.filter { !$0.weak && $0.amt > 0 }
        let shade: (Int) -> Double = { [1, 0.6, 0.32, 0.18, 0.1][min($0, 4)] }
        let sum = max(1, strong.reduce(0) { $0 + $1.amt })
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("무엇이 흔드나").appFont(16, .bold)
                Spacer()
                Text("한 달").appFont(12).foregroundStyle(Theme.sub)
            }
            if strong.isEmpty {
                Text(SiteCalc.shared.factorMonth("mkt") == nil ? String(localized: "요인 자료 불러오는 중") : String(localized: "뚜렷한 요인 없음"))
                    .appFont(12).foregroundStyle(Theme.muted)
            } else {
                GeometryReader { g in
                    let w = g.size.width - 2 * CGFloat(strong.count - 1)
                    HStack(spacing: 2) {
                        ForEach(Array(strong.enumerated()), id: \.offset) { i, x in
                            Rectangle().fill(Theme.teal.opacity(shade(i))).frame(width: max(2, w * x.amt / sum))
                        }
                    }
                }
                .frame(height: 12).clipShape(Capsule())
                HStack(spacing: 12) {
                    ForEach(Array(strong.enumerated()), id: \.offset) { i, x in
                        NavigationLink { FactorDetailView(factor: x.k) } label: {
                            HStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 2).fill(Theme.teal.opacity(shade(i))).frame(width: 8, height: 8)
                                Text(Self.short[x.k] ?? "").appFont(12).foregroundStyle(Theme.sub)
                                Text(AppModel.man(x.amt)).appFont(13, .bold).foregroundStyle(Theme.ink)
                            }
                            .lineLimit(1)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(FactorDetailView.names[x.k] ?? ""), \(String(localized: "보통 한 달 ±") + AppModel.man(x.amt)). 자세히 보기")
                    }
                }
                .minimumScaleFactor(0.8)
            }
        }
    }

    /// 과거 재생: 그때 함께 움직인 요인 변화 × 지금 내 종목 민감도 (웹 xfReplays 와 같은 식). 일마다 막대 하나와 금액, 환율 몫은 한 줄로 모은다
    private var replayCard: some View {
        let total = max(1, m.total)
        let rows = SiteCalc.replays.map { rp -> (rp: SiteCalc.Replay, tot: Double, fx: Double) in
            let os = m.rows.map { r in (r.value, SiteCalc.replayMove(rp, usd: r.sym.currency == .usd) { k in k == "mkt" ? Self.mktBeta(r.id) : SiteCalc.shared.beta(k, r.id) }) }
            return (rp, os.reduce(0) { $0 + $1.0 * $1.1.total }, os.reduce(0) { $0 + $1.0 * $1.1.fx })
        }
        let top = max(1, rows.map { abs($0.tot) }.max() ?? 1)
        // 환율 한 줄: 같은 몫끼리 묶는다 (막아 줌·더 깎음·보탬·깎음). 작은 건 뺀다
        var groups: [(String, [String])] = []
        for x in rows where abs(x.fx) >= total * FactorDetailView.tiny {
            let g = x.fx > 0 ? (x.tot < 0 ? String(localized: "막아 줌") : String(localized: "보탬")) : (x.tot < 0 ? String(localized: "더 깎음") : String(localized: "깎음"))
            if let i = groups.firstIndex(where: { $0.0 == g }) { groups[i].1.append(x.rp.name) } else { groups.append((g, [x.rp.name])) }
        }
        let note = groups.isEmpty ? String(localized: "달러 적어 환율 영향 작음")
            : String(localized: "환율이 \(String(describing: groups.map { String(localized: "\(String(describing: $0.1.joined(separator: "·"))) 땐 \(String(describing: $0.0))") }.joined(separator: ", ")))")
        return Card {
            Text("그때 그 일이 지금 나면").appFont(16, .bold)
            ForEach(rows, id: \.rp.when) { x in
                let c = x.tot < 0 ? Theme.blue : Color.red
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(x.rp.name).appFont(13, .semibold).lineLimit(1)
                        Text(x.rp.when).appFont(11).foregroundStyle(Theme.muted)
                    }
                    .frame(width: 84, alignment: .leading)
                    GeometryReader { g in
                        ZStack(alignment: .trailing) {
                            Capsule().fill(Theme.border)
                            Capsule().fill(c).frame(width: max(3, g.size.width * abs(x.tot) / top))
                        }
                    }
                    .frame(height: 10)
                    Text(AppModel.eokS(x.tot)).appFont(14, .bold).monospacedDigit().foregroundStyle(c)
                        .lineLimit(1).minimumScaleFactor(0.8).frame(width: 76, alignment: .trailing)
                }
            }
            Text(note).appFont(11).foregroundStyle(Theme.sub).lineSpacing(2)
        }
    }

    /// 일정 하나: 요인, 미리 보기 값(나쁘게 나온 쪽, 요인 상세 단위), 내 자산 보통 움직임 (웹 xfEvents 와 같은 식)
    private struct Ev { let hit: MarketEvents.Hit; let k: String; let sd: Double; let v: Double; let amt: Double }
    private var evs: [Ev] { Self.events(m) }
    /// 분석 허브 줄: 가장 가까운 일정과 내 자산 보통 움직임 (아래 오늘의 이벤트 카드와 같은 값)
    static func hubNext(_ m: AppModel) -> (day: String, kind: String, amt: Double)? {
        events(m).first.map { ($0.hit.day, $0.hit.kind, $0.amt) }
    }
    private static func events(_ m: AppModel) -> [Ev] {
        MarketEvents.upcoming(held: m.rows.map(\.id), days: 45).compactMap { h -> Ev? in
            guard let em = SiteCalc.eventMoves[h.id] else { return nil }
            let v = em.factor == "fx" ? -Market.shared.fx.last * em.sd / 100 : -em.sd
            let amt = abs(m.rows.reduce(0) { $0 + $1.value * (Self.move(em.factor, $1.id, $1.sym, v) ?? (em.factor == "mkt" ? v / 100 : 0)) })
            return Ev(hit: h, k: em.factor, sd: em.sd, v: v, amt: amt)
        }.sorted { $0.hit.day < $1.hit.day }
    }

    /// 맨 위 칸: 들어온 그 일 (그래프 깃발로 왔으면 그 일, 아니면 가장 가까운 일). 흔들림 폭을 −/+ 막대로 (45일 안에 없으면 숨김)
    @ViewBuilder private func todayCard(_ x: Ev?) -> some View {
        if let x {
            let tot = max(1, m.total), amt = AppModel.man(x.amt)
            let key = "\(x.hit.id).\(x.hit.day)", on = asked.contains(key)
            Card(dark: true) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(MarketEvents.when(x.hit.day)) · \(x.hit.kind)").appFont(12, .semibold).foregroundStyle(Theme.teal)
                        Text("±" + amt).appFont(30, .bold).roll("±" + amt)
                        Text(String(localized: "\(String(describing: Self.short[x.k] ?? "")) \(String(describing: String(format: "%g", x.sd)))% 움직이면 내 돈 \(String(describing: String(format: "%.1f", x.amt / tot * 100)))%"))
                            .appFont(12).foregroundStyle(Theme.sub)
                    }
                    Spacer()
                    Button {
                        if on { asked.remove(key) } else { asked.insert(key) }
                        Task { await EventRetro.ask(id: x.hit.id, kind: x.hit.kind, day: x.hit.day, on: !on) }
                    } label: {
                        Image(systemName: on ? "bell.fill" : "bell").font(.system(size: 15)).foregroundStyle(.white)
                            .frame(width: 36, height: 36).background(Color.white.opacity(on ? 0.25 : 0.12), in: RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(on ? String(localized: "알림 켬") : String(localized: "결과 알림"))
                }
                // −/+ 막대: 가운데가 지금
                HStack(spacing: 2) {
                    Capsule().fill(Theme.blue)
                    Capsule().fill(Color.red)
                }
                .frame(height: 8).padding(.top, 6)
                HStack {
                    Text("−" + amt)
                    Spacer()
                    Text("지금")
                    Spacer()
                    Text("+" + amt)
                }
                .appFont(11).foregroundStyle(Theme.sub)
                NavigationLink { FactorDetailView(factor: x.k, start: x.v) } label: {
                    Text("미리 보기 ›").appFont(13, .semibold).foregroundStyle(Theme.teal).padding(.top, 4)
                }
                .buttonStyle(.plain)
            }
        }
    }
    @State private var asked: Set<String> = []
    @State private var retro: EventRetro.Snap?

    /// 지난 일 돌아보기: 가장 최근 지난 일정의 미리 본 범위 vs 실제 평가액 변화 (기기에 적어 둔 값, 없으면 숨김)
    @ViewBuilder private var retroCard: some View {
        if let r = retro, let after = r.after {
            let real = after - r.base
            let inside = abs(real) <= r.amt
            Card {
                Text("지난 일 돌아보기").appFont(16, .bold)
                Text("\(Int(r.day.dropFirst(5).prefix(2)) ?? 0)/\(Int(r.day.suffix(2)) ?? 0) · \(r.kind)").appFont(12).foregroundStyle(Theme.sub)
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("미리 본 범위").appFont(12).foregroundStyle(Theme.sub)
                        Text("±" + AppModel.man(r.amt)).appFont(18, .bold).roll("±" + AppModel.man(r.amt))
                    }
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading).background(Theme.border.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("실제").appFont(12).foregroundStyle(Theme.sub)
                        Text((real > 0 ? "+" : real < 0 ? "−" : "") + AppModel.man(abs(real))).appFont(18, .bold)
                            .foregroundStyle(real > 0 ? Color.red : real < 0 ? Theme.blue : Theme.ink).roll((real > 0 ? "+" : real < 0 ? "−" : "") + AppModel.man(abs(real)))
                    }
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading).background(Theme.border.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
                }
                Text(inside ? String(localized: "범위 안") : String(localized: "범위보다 크게 움직임 · 다른 일도 섞였을 수도"))
                    .appFont(13).foregroundStyle(inside ? Theme.teal : Color.orange)
                Text("실제 = 일정 전 평가액과 다음에 연 때의 차이")
                    .appFont(11).foregroundStyle(Theme.muted)
            }
        }
    }

    /// 다가오는 일: 시장 일정과 내 종목 일정을 날짜순 한 목록에. 막대 = 내 돈 흔들림, 내 종목은 이름 뒤 점
    private func upcomingCard(_ es: [Ev], _ mine: [MarketEvents.Hit]) -> some View {
        let near = Array(es.prefix(8))
        let items = (near.map { (hit: $0.hit, ev: Optional($0)) } + mine.map { (hit: $0, ev: Ev?.none) }).sorted { $0.hit.day < $1.hit.day }
        let top = max(1, near.map(\.amt).max() ?? 1)
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("다가오는 일").appFont(16, .bold)
                Spacer()
                Text("막대 = 내 돈 흔들림").appFont(12).foregroundStyle(Theme.sub)
            }
            ForEach(Array(items.enumerated()), id: \.offset) { i, it in
                if i > 0 { Divider() }
                if let x = it.ev {
                    NavigationLink { FactorDetailView(factor: x.k, start: x.v) } label: { evRow(it.hit, amt: x.amt, top: top) }
                        .buttonStyle(.plain)
                } else {
                    evRow(it.hit, amt: nil, top: top)
                }
            }
            if items.isEmpty { Text("45일 안 일정 없음").appFont(13).foregroundStyle(Theme.sub) }
        }
    }

    /// 다가오는 일 한 줄: 날짜 | 이름 | 막대 | 금액 | › (막대와 금액은 칸을 맞춘다). 내 종목 일정은 막대·금액 칸에 비중
    private func evRow(_ h: MarketEvents.Hit, amt: Double?, top: Double) -> some View {
        let n = Day.dday(h.day)
        return HStack(spacing: 8) {
            Text(n == 0 ? String(localized: "오늘") : n == 1 ? String(localized: "내일") : Day.md(h.day)).appFont(12, .semibold).foregroundStyle(Theme.teal)
                .lineLimit(1).frame(width: 44, alignment: .leading)
            HStack(spacing: 6) {
                Text(h.kind.replacingOccurrences(of: " 결정", with: "")).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.8)
                if amt == nil && h.held { Circle().fill(Theme.teal).frame(width: 6, height: 6) }
            }
            Spacer(minLength: 4)
            if let amt {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.border)
                        Capsule().fill(Theme.ink).frame(width: max(3, g.size.width * amt / top))
                    }
                }
                .frame(width: 64, height: 6)
                Text("±" + AppModel.man(amt)).appFont(13, .bold).monospacedDigit().lineLimit(1).minimumScaleFactor(0.8).frame(width: 64, alignment: .trailing)
                Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Theme.muted).frame(width: 8)
            } else {
                Text(h.held ? String(localized: "비중 \(String(describing: AppModel.pct(m.weight(h.target))))") : h.sub)
                    .appFont(12, .semibold).foregroundStyle(h.held ? Theme.teal : Theme.sub).lineLimit(1).frame(width: 136, alignment: .trailing)
                Color.clear.frame(width: 8, height: 1)
            }
        }
        .padding(.vertical, 6).contentShape(Rectangle())
    }

    var body: some View {
        let es = evs
        let mine = Array(MarketEvents.upcoming(held: m.rows.map(\.id), days: 45).filter { SiteCalc.eventMoves[$0.id] == nil }.sorted { $0.day < $1.day }.prefix(5))
        let hero = es.first { "\($0.hit.id).\($0.hit.day)" == focusKey } ?? es.first

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                todayCard(hero)
                retroCard
                shakeCard(ranks)
                upcomingCard(es, mine)
                replayCard
                Text("큰 변화일수록 오차 큼").appFont(12).foregroundStyle(Theme.muted).frame(maxWidth: .infinity)
            }
            .padding(16)
            .onAppear {
                EventRetro.record(es.map { (id: $0.hit.id, kind: $0.hit.kind, day: $0.hit.day, amt: $0.amt) }, total: m.total)
                asked = Set(es.filter { EventRetro.isAsked($0.hit.id, $0.hit.day) }.map { "\($0.hit.id).\($0.hit.day)" })
                retro = EventRetro.latest
                Self.focus = nil
            }
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .task { await SiteCalc.shared.refresh() }
    }
}

// MARK: 요인 상세 — 프리셋으로 "이렇게 되면" 내 돈이 얼마나 움직이나 (외부 요인 개편 2단계)

struct FactorDetailView: View {
    @Environment(AppModel.self) private var m
    let factor: String
    @State private var shock: Double? = nil
    @State private var custom = false

    /// start: 일정 미리 보기처럼 정해진 값으로 열 때 (직접 정하기 안에 보인다)
    init(factor: String, start: Double? = nil) {
        self.factor = factor
        _shock = State(initialValue: start)
        _custom = State(initialValue: start != nil)
    }

    static let names: [String: String] = ["mkt": String(localized: "미국 시장"), "rate": String(localized: "미국 금리"), "fx": String(localized: "원/달러"), "oil": String(localized: "유가"), "gold": String(localized: "금값")]
    /// 관계 없음·거의 그대로 기준: 평가액의 0.1%, 같은 방향 비율 50%±5%p
    static let tiny = 0.001, weakAgree = 0.05

    /// 프리셋 (이름, 값). 단위: 금리 %p, 원/달러 원 변화, 나머지 %
    private var presets: [(String, Double)] {
        let fx = Market.shared.fx.last
        switch factor {
        case "rate":
            let y = MacroData.shared.change1y.map { ($0 * 10).rounded() / 10 }
            return [(String(localized: "내린다"), -0.5), (String(localized: "오른다"), 0.5), (y.map { _ in String(localized: "작년만큼 또") } ?? String(localized: "크게 오른다"), (y.flatMap { $0 == 0 ? nil : $0 }) ?? 1.0), (String(localized: "2022년처럼"), 2.5)]
        case "fx": return [(String(localized: "1,300원"), 1300 - fx), (String(localized: "1,500원"), 1500 - fx), (String(localized: "10% 하락"), -fx * 0.1), (String(localized: "2022년처럼"), fx * 0.2)]
        case "mkt": return [(String(localized: "흔한 나쁜 날"), -2), (String(localized: "나쁜 한 달"), -8), (String(localized: "2022년처럼"), -25), (String(localized: "좋은 한 달"), 8)]
        default: return [(String(localized: "10% 하락"), -10), (String(localized: "10% 상승"), 10), (String(localized: "30% 하락"), -30), (String(localized: "30% 상승"), 30)]
        }
    }
    private var range: ClosedRange<Double> {
        switch factor { case "rate": -1.5...1.5; case "fx": -300...300; case "oil": -40...40; default: -30...30 }
    }
    private func text(_ v: Double) -> String {
        let s = v > 0 ? "+" : v < 0 ? "−" : ""
        switch factor {
        case "rate": return s + String(format: "%g", abs(v)) + "%p"
        case "fx": return String(localized: "\(String(describing: Int((Market.shared.fx.last + v).rounded()).formatted()))원 (\(String(describing: s))\(String(describing: Int(abs(v).rounded()).formatted()))원)")
        default: return s + String(format: "%g", abs(v)) + "%"
        }
    }
    /// 지난 3년 가장 큰 한 달 변화를 넘는지 (금리 bp, 나머지 로그 변화)
    private func huge(_ v: Double) -> Bool {
        guard let mx = SiteCalc.shared.factorMonth(factor)?.max, mx > 0 else { return false }
        switch factor {
        case "rate": return abs(v * 100) > mx
        case "fx": return abs(log((Market.shared.fx.last + v) / Market.shared.fx.last)) > mx
        default: return abs(log(1 + v / 100)) > mx
        }
    }

    var body: some View {
        let name = Self.names[factor] ?? factor
        let ps = presets
        let v = shock ?? ps[1].1
        let total = max(1, m.total)
        let rows = m.rows.map { r -> (id: String, label: String, dv: Double, agree: Double?, weak: Bool, known: Bool) in
            let mv = ExternalView.move(factor, r.id, r.sym, v)
            let dv = r.value * (mv ?? 0)
            let ag = factor == "fx" ? nil : SiteCalc.shared.betaStat(factor, r.id)?.agree
            let weak = mv == nil || abs(dv) < total * Self.tiny || (ag.map { abs($0 - 0.5) < Self.weakAgree } ?? false)
            return (r.id, r.sym.short, dv, ag, weak, mv != nil)
        }.sorted { abs($0.dv) > abs($1.dv) }
        let tot = rows.reduce(0) { $0 + $1.dv }
        let upSum = rows.filter { $0.dv > 0 }.reduce(0) { $0 + $1.dv }, dnSum = rows.filter { $0.dv < 0 }.reduce(0) { $0 + $1.dv }
        let r = tot / total
        let verdict = abs(r) < Self.tiny ? String(localized: "거의 그대로예요") : abs(r) < 0.02 ? (r > 0 ? String(localized: "조금 늘어요") : String(localized: "조금 줄어요")) : (r > 0 ? String(localized: "많이 늘어요") : String(localized: "많이 줄어요"))
        let allWeak = rows.allSatisfy(\.weak)
        let offset = upSum > 0 && dnSum < 0 && abs(tot) < min(upSum, -dnSum)
        let strong = rows.filter { !$0.weak }, weak = rows.filter(\.weak)
        let scale = max(1, rows.map { abs($0.dv) }.max() ?? 1)
        let evIds: [String] = factor == "rate" ? ["x_fomc", "x_cpi", "x_jobs"] : factor == "fx" ? ["x_bok", "x_fomc"] : factor == "oil" ? ["x_opec"] : factor == "gold" ? ["x_fomc", "x_cpi"] : ["x_fomc", "x_cpi", "x_jobs", "x_mid", "x_witch"]
        let events = MarketEvents.upcoming(held: m.rows.map(\.id), days: 45).filter { evIds.contains($0.id) }.sorted { $0.day < $1.day }.prefix(5)

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(name)\(factor == "mkt" || factor == "gold" ? String(localized: "이") : String(localized: "가")) 바뀌면 내 돈은?").appFont(20, .bold)
                    Text(factor == "rate" ? String(localized: "미국 10년 금리 기준 · 시장 몫 빼고 · 지난 3년 실제 움직임")
                         : factor == "fx" ? String(localized: "달러 자산은 환율만큼 · 지금 \(String(describing: Int(Market.shared.fx.last).formatted()))원 기준")
                         : factor == "mkt" ? String(localized: "S&P500 기준 · 지난 3년 실제 움직임")
                         : String(localized: "시장 몫 빼고 · 지난 3년 실제 움직임"))
                        .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                }
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    ForEach(Array(ps.enumerated()), id: \.offset) { _, p in
                        let on = !custom && abs(v - p.1) < 1e-9
                        Button { shock = p.1; custom = false } label: {
                            VStack(spacing: 2) {
                                Text(p.0).appFont(14, .bold)
                                Text(factor == "fx" ? text(p.1).components(separatedBy: " ").last?.trimmingCharacters(in: CharacterSet(charactersIn: "()")) ?? "" : text(p.1)).appFont(12)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .foregroundStyle(on ? Color.white : Theme.ink)
                            .background(on ? Theme.teal : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Theme.teal : Theme.border))
                        }
                        .buttonStyle(.plain)
                    }
                }
                Card(dark: true) {
                    Text("\(name) \(text(v))이면").appFont(13).foregroundStyle(Theme.sub)
                    Text(allWeak && factor != "fx" ? String(localized: "뚜렷한 관계 없음") : verdict).appFont(24, .bold)
                    Text("\(AppModel.manS(tot)) · \(AppModel.sgn(r))").appFont(15, .semibold).foregroundStyle(Theme.change(tot))
                    if upSum > 0 || dnSum < 0 {
                        sideBar(String(localized: "오르는 쪽"), upSum, max(upSum, -dnSum, 1), Theme.up)
                        sideBar(String(localized: "내리는 쪽"), dnSum, max(upSum, -dnSum, 1), Theme.down)
                    }
                    if offset { Text("종목끼리 반대로 움직여 상쇄").appFont(13).foregroundStyle(Theme.sub) }
                }
                if huge(v) {
                    Text("지난 3년엔 없던 큰 변화 · 실제와 많이 다를 수도")
                        .appFont(13).foregroundStyle(Theme.inkFixed).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.orange.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
                }
                Card {
                    Text("종목별").appFont(15, .bold)
                    ForEach(weak.count > 3 ? strong : rows, id: \.id) { x in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(x.label).appFont(14, .semibold)
                                Spacer()
                                Text(x.weak ? String(localized: "뚜렷한 관계 없음") : AppModel.manS(x.dv)).appFont(14, .semibold).foregroundStyle(x.weak ? Theme.muted : Theme.change(x.dv))
                            }
                            centerBar(x.dv, scale)
                            let note = label(x.dv, x.weak, x.known) + (x.agree.map { x.weak ? "" : String(localized: " · 10번 중 \(String(describing: Int(($0 * 10).rounded())))번") } ?? "")
                            if !note.isEmpty { Text(note).appFont(12).foregroundStyle(Theme.sub) }
                        }
                        .opacity(x.weak ? 0.55 : 1)
                    }
                    if weak.count > 3 {
                        Text("나머지 \(weak.count)개 · 뚜렷한 관계 없음").appFont(13).foregroundStyle(Theme.muted)
                    }
                    if rows.isEmpty { Text("보유 종목이 없어요.").appFont(13).foregroundStyle(Theme.sub) }
                }
                Card {
                    Text(factor == "rate" ? String(localized: "금리 시나리오와 다른 이유") : String(localized: "\(String(describing: m.forecastTitle))과 다른 이유")).appFont(15, .bold)
                    HStack(alignment: .top, spacing: 8) {
                        compare(String(localized: "이 화면"), String(localized: "그날그날 함께 움직인 만큼 · 한 달 이내"))
                        compare(factor == "rate" ? String(localized: "금리 시나리오") : m.forecastTitle, factor == "rate" ? String(localized: "회사 이익 경로 · 3년 영향") : String(localized: "모든 요인 · 3년 뒤 분포"))
                    }
                    Text("기간·방법이 달라 방향이 다를 수도").appFont(12).foregroundStyle(Theme.sub)
                    NavigationLink(value: factor == "rate" ? AnalysisRoute.rateScen : AnalysisRoute.forecast) {
                        Text("목표 영향 보기").appFont(14, .semibold).foregroundStyle(Theme.teal)
                    }
                }
                if !events.isEmpty {
                    Card {
                        Text("다가오는 일정").appFont(15, .bold)
                        ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                            HStack(alignment: .top, spacing: 10) {
                                Text(MarketEvents.when(e.day)).appFont(12, .semibold).foregroundStyle(Theme.teal).frame(minWidth: 64, alignment: .leading)
                                Text(e.kind).appFont(14)
                            }
                        }
                    }
                }
                Card {
                    DisclosureGroup(isExpanded: $custom) {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack { Text("값").appFont(13).foregroundStyle(Theme.sub); Spacer(); Text(text(v)).appFont(14, .bold) }
                            Slider(value: Binding(get: { min(max(v, range.lowerBound), range.upperBound) }, set: { shock = $0 }),
                                   in: range, step: factor == "rate" ? 0.25 : factor == "fx" ? 20 : 5).tint(Theme.teal)
                                .accessibilityLabel("\(name) 변화 직접 정하기")
                        }
                        .padding(.top, 8)
                    } label: { Text("직접 정하기").appFont(15, .bold).foregroundStyle(Theme.ink) }
                }
                Text("예측 아님 · '만약에' 화면 · 요인 하나만 바꾼 값")
                    .appFont(12).foregroundStyle(Theme.muted)
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationTitle("외부 요인 · \(name)")
        .toolbarTitleDisplayMode(.inlineLarge)
        .task { await SiteCalc.shared.refresh(); if factor == "rate" { await MacroData.shared.load() } }
    }

    private func label(_ dv: Double, _ weak: Bool, _ known: Bool) -> String {
        if !known { return String(localized: "3년 시세 부족 · 0으로 봄") }
        if factor == "fx" { return dv == 0 ? String(localized: "원화 자산 · 영향 없음") : String(localized: "달러 자산 · 환율만큼") }
        if weak { return "" }
        let sameDir = (dv > 0) == (shockSign > 0)
        return sameDir ? String(localized: "같은 방향") : String(localized: "반대 방향")
    }
    private var shockSign: Double { (shock ?? presets[1].1) >= 0 ? 1 : -1 }
    private func sideBar(_ t: String, _ x: Double, _ s: Double, _ c: Color) -> some View {
        HStack(spacing: 8) {
            Text(t).appFont(12).frame(width: 60, alignment: .leading)
            GeometryReader { g in
                RoundedRectangle(cornerRadius: 3).fill(c).frame(width: max(2, g.size.width * abs(x) / s))
            }
            .frame(height: 10)
            Text(AppModel.manS(x)).appFont(12, .semibold).frame(minWidth: 70, alignment: .trailing)
        }
    }
    private func centerBar(_ x: Double, _ s: Double) -> some View {
        GeometryReader { g in
            let half = g.size.width / 2, w = half * min(1, abs(x) / s)
            ZStack(alignment: .leading) {
                Rectangle().fill(Theme.line).frame(height: 6)
                Rectangle().fill(Theme.dash).frame(width: 1, height: 10).offset(x: half)
                RoundedRectangle(cornerRadius: 3).fill(x >= 0 ? Theme.up : Theme.down)
                    .frame(width: max(1, w), height: 6).offset(x: x >= 0 ? half : half - w)
            }
        }
        .frame(height: 10)
    }
    private func compare(_ t: String, _ d: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(t).appFont(13, .bold)
            Text(d).appFont(12).foregroundStyle(Theme.sub)
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .topLeading)
        .background(Theme.tealBg, in: RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: 금리 시나리오 — 금리→이익 관계식 식단 1000벌 (naeilo.com 외부 요인 › 금융·통화와 같은 계산)

/// data/macro/rates_earnings.json (매달 5일 갱신, 출처 Robert J. Shiller). 받은 파일은 저장해 두고 다음에 먼저 쓴다
@MainActor @Observable
final class MacroData {
    static let shared = MacroData()
    struct File: Decodable {
        struct Now: Decodable { let date: String?; let rate: Double?; let change_1y: Double? }
        struct Meals: Decodable { let g_now: Double; let coef: [[[Double]]] }
        let now: Now?
        let eps_last: String?
        let meals: Meals?
    }
    var file: File?
    var failed = false
    private var loading = false
    private static let name = "macro.json"
    /// 이미 가진 파일 그대로 (같은 파일이 다시 오면 화면을 다시 그리지 않는다)
    @ObservationIgnored private var raw: Data?
    /// 마지막으로 받은 날 (파일은 매달 5일에만 바뀌니 하루 한 번만 받는다)
    @ObservationIgnored private var fetchedDay: String?
    /// 금리·기대수익 슬라이드 값별 계산 결과 (넘길 때마다 식단 1000벌을 다시 계산하지 않게)
    @ObservationIgnored private var memo: [String: RateScen] = [:]

    init() {
        if let d = Store.readData(Self.name) { raw = d; file = try? JSONDecoder().decode(File.self, from: d) }
    }
    func load() async {
        let day = SiteCalc.today()
        guard !loading, fetchedDay != day || file == nil else { return }
        loading = true; defer { loading = false }
        var req = URLRequest(url: Config.data.appendingPathComponent("macro/rates_earnings.json"))
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 20   // 응답 없으면 20초 뒤 포기 (기본 60초)
        do {
            let (d, r) = try await URLSession.shared.data(for: req)
            guard (r as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            fetchedDay = day
            if d == raw, file != nil { failed = false; return }
            // 110KB JSON 풀기는 화면 밖에서
            let f = try await Task.detached(priority: .utility) { try JSONDecoder().decode(File.self, from: d) }.value
            guard f.meals?.coef.isEmpty == false else { throw URLError(.cannotParseResponse) }
            raw = d; memo = [:]
            file = f; failed = false
            Store.writeData(d, Self.name)
        } catch {
            if file == nil { failed = true }
        }
    }
    /// 슬라이드 값 (dr %p, m 연) 에서의 계산. 같은 값이면 저장해 둔 결과
    func scen(dr: Double, m: Double) -> RateScen? {
        guard let M = file?.meals else { return nil }
        let k = String(format: "%.2f|%.3f", dr, m)
        if let r = memo[k] { return r }
        let r = RateScen(coef: M.coef, g0: M.g_now, dr: dr, m: m)
        if memo.count > 64 { memo = [:] }
        memo[k] = r
        return r
    }
    /// 지난 1년 10년물 금리 변화 (%p)
    var change1y: Double? { file?.now?.change_1y }
    /// 슬라이드 처음 값: 지난 1년 실제 변화 (0.1 단위)
    var defaultDr: Double { ((change1y ?? 0) * 10).rounded() / 10 }
}

/// 식단 j, y년째 S&P 실질 EPS 변화(로그 %) = a + b·Δ금리 + c·지금 EPS 증가율. 무작위 경로 없이 식단마다 답 하나
struct RateScen {
    let years: Int
    let yr: [[Double]]          // y년째 변화 [하위10, 중앙, 상위10] (%)
    let cum: [[Double]]         // y년까지 누적
    let dropYr: [Double]        // y년째 감소 비율
    let dropCum: Double         // 끝 해 누적 감소 비율
    let dropCumYr: [Double]     // y년까지 누적으로 오늘보다 줄어 있을 비율 (근거 표)
    let mkt: [Double]           // 시장 연 기대수익 [하위10, 중앙, 상위10]
    let slope: [Double]         // 식단마다 3년 동안의 b 합 (금리 1%p 당 이익 변화 %)

    static func q(_ sorted: [Double], _ p: Double) -> Double {
        guard !sorted.isEmpty else { return 0 }
        let x = p * Double(sorted.count - 1), i = Int(x.rounded(.down)), j = min(sorted.count - 1, i + 1)
        let w = x - Double(i)
        return sorted[i] * (1 - w) + sorted[j] * w
    }
    static func band(_ a: [Double]) -> [Double] {
        let s = a.sorted()
        return [q(s, 0.1), q(s, 0.5), q(s, 0.9)]
    }

    init(coef: [[[Double]]], g0: Double, dr: Double, m: Double) {
        let H = coef.first?.count ?? 0
        var tot: [[Double]] = [], acc: [[Double]] = [], sl: [Double] = []
        for c in coef {
            var t: [Double] = [], s: [Double] = [], run = 0.0, b = 0.0
            for abc in c where abc.count >= 3 {
                let v: Double = abc[0] + abc[1] * dr + abc[2] * g0
                run += v; t.append(v); s.append(run); b += abc[1]
            }
            if t.count == H { tot.append(t); acc.append(s); sl.append(b) }
        }
        let n = Double(max(1, tot.count))
        years = H
        yr = (0..<H).map { y in Self.band(tot.map { $0[y] }) }
        cum = (0..<H).map { y in Self.band(acc.map { $0[y] }) }
        dropYr = (0..<H).map { y in Double(tot.filter { $0[y] < 0 }.count) / n }
        dropCumYr = (0..<H).map { y in Double(acc.filter { $0[y] < 0 }.count) / n }
        dropCum = dropCumYr.last ?? 0
        // 시장 연 수익 ≈ 3년 누적 이익 변화(연) + 배당 1.5% + 금리 외 공통 기대수익 (PER 그대로)
        let Hd = Double(max(1, H))
        mkt = Self.band(acc.map { a in exp((a.last ?? 0) / 100 / Hd) - 1 + 0.015 + m })
        slope = sl
    }
}

/// 금리 충격 테스트: 목표일 전망(내 설정)을 기준선으로 두고 금리만 바꿨을 때 달라지는 만큼 (2026-10-14 시안)
struct RateScenarioView: View {
    @Environment(AppModel.self) private var m
    @State private var pick = 2              // 프리셋 번호. 기본 "작년만큼 또 오른다"
    private var macro: MacroData { MacroData.shared }

    var body: some View {
        Group {
            if let f = macro.file, let M = f.meals {
                main(f, M)
            } else {
                VStack(spacing: 12) {
                    if macro.failed { Text("금리 자료 못 불러옴 · 인터넷 확인").appFont(14).foregroundStyle(Theme.sub) }
                    else { ProgressView(); Text("금리 자료 불러오는 중").appFont(13).foregroundStyle(Theme.sub) }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .task { await macro.load() }
    }

    /// 프리셋 4개: 이름, 설명, 10년물 변화(%p). "작년만큼"은 지난 1년 실제 변화
    private var presets: [(label: String, sub: String, dr: Double)] {
        let y = macro.defaultDr
        return [(String(localized: "내린다"), "−0.5%p", -0.5), (String(localized: "그대로"), "0", 0),
                (String(localized: "작년만큼"), Self.signed(y, "%p", 1), y), (String(localized: "2022년처럼"), "+2.5%p", 2.5)]
    }

    /// 포트폴리오 시장 민감도 β (외부 요인 화면과 같은 값)
    private var beta: Double {
        let tot = max(1, m.total)
        return m.rows.reduce(0) { $0 + $1.value / tot * ExternalView.coef($1.sym).0 }
    }

    /// 닫힌 식에 드는 값. 평가액·원금은 종목 목록을 매번 다시 만드니 한 번만 읽는다 (식단 1000벌 × 2번 부르면 넘길 때 멈칫)
    private struct Closed { let T, sg, mu, V0, K, M: Double }
    private func closedInput(_ f: AppModel.Forecast) -> Closed {
        Closed(T: max(1.0 / 12, f.tY.last ?? 3), sg: max(0.01, f.sigma), mu: f.mu, V0: m.trackValue, K: m.keyValue, M: m.fcMonthly)   // 전망과 같게 월 적립 포함 (웹 ci.M)
    }
    /// 연 기대를 shift 만큼 옮겼을 때 목표 확률. 웹처럼 전망(몬테카를로) 목표 확률에 닫힌 식의 차이만 더한다
    private static func prob(_ f: AppModel.Forecast, _ c: Closed, _ shift: Double) -> Double {
        max(0, min(1, f.pGoal + closed(c, shift) - closed(c, 0)))
    }
    private static func closed(_ c: Closed, _ shift: Double) -> Double {
        let T = c.T, sg = c.sg, g: Double = c.mu - sg * sg / 2 + shift
        let eff: Double = c.V0 + c.M * 12 * T * exp(-g * T / 2)
        guard eff > 0, c.K > 0 else { return 0 }
        let num: Double = log(eff / c.K) + g * T
        return AppModel.normCDF(num / (sg * sqrt(T)))
    }
    /// 보통의 경우(로그 기대 g + shift) 매달 M씩 넣으며 K에 처음 닿는 개월. 50년 안에 못 닿으면 nil
    private static func months(_ c: Closed, _ shift: Double) -> Int? {
        guard c.K > 0 else { return nil }
        if c.V0 >= c.K { return 0 }
        let gm = exp((c.mu - c.sg * c.sg / 2 + shift) / 12)
        var v = c.V0
        for n in 1...600 { v = v * gm + c.M; if v >= c.K { return n } }
        return nil
    }

    @ViewBuilder private func main(_ file: MacroData.File, _ M: MacroData.File.Meals) -> some View {
        let d = presets[pick].dr, mm = 0.0   // 시장 공통 기대수익은 이 화면에서 뺐다 (목표일 전망의 추세 믿음과 같은 레버)
        let r = macro.scen(dr: d, m: mm) ?? RateScen(coef: M.coef, g0: M.g_now, dr: d, m: mm)
        let f = m.forecast, kb = beta, ci = closedInput(f)
        let base = Self.prob(f, ci, 0)
        let shifts: [Double] = r.slope.map { b in kb * (b * d / 3 / 100 + mm) }
        let pb = RateScen.band(shifts.map { Self.prob(f, ci, $0) })
        let mid = RateScen.band(shifts)[1]
        let delta = Int(((pb[1] - base) * 100).rounded())
        let H = r.years
        let eps = H > 0 ? exp(r.cum[H - 1][1] / 100) - 1 : 0
        let m0 = Self.months(ci, 0), m1 = Self.months(ci, mid)
        let endAmt = f.q50.last ?? 0, T = ci.T
        let when = pick == 0 ? String(localized: "내리면") : pick == 1 ? String(localized: "그대로면") : pick == 2 ? String(localized: "작년만큼 오르면") : String(localized: "2022년처럼 오르면")

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                DetailHead(title: String(localized: "금리가 바뀌면 내 목표는?"), sub: "")
                HStack(spacing: 6) {
                    ForEach(presets.indices, id: \.self) { i in
                        let on = pick == i
                        Button { pick = i } label: {
                            VStack(spacing: 2) {
                                Text(presets[i].label).appFont(13, .bold).lineLimit(1).minimumScaleFactor(0.7)
                                Text(presets[i].sub).appFont(11).foregroundStyle(on ? Theme.tealDark : Theme.sub).lineLimit(1).minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 10).padding(.horizontal, 4)
                            .background(on ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
                            .overlay { RoundedRectangle(cornerRadius: 14).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 2 : 1) }
                        }
                        .buttonStyle(.plain).foregroundStyle(Theme.ink)
                    }
                }
                Card(dark: true) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("\(m.keyName) 가능성").appFont(14)
                        Text(AppModel.pct(base)).appFont(18).foregroundStyle(Theme.sub)
                        Text("→").appFont(16).foregroundStyle(Theme.sub)
                        Text(AppModel.pct(pb[1])).appFont(28, .bold).roll(AppModel.pct(pb[1]))
                        Spacer()
                        Text(delta == 0 ? String(localized: "그대로") : (delta > 0 ? "+" : "−") + "\(abs(delta))%p").appFont(13, .bold)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(delta < 0 ? Theme.orange : delta > 0 ? Theme.teal : Theme.muted, in: Capsule()).foregroundStyle(.white)
                    }
                    bar(String(localized: "지금"), base, Theme.muted)
                    bar(String(localized: "이 경우"), pb[1], delta < 0 ? Theme.orange : Theme.teal)
                    Divider()
                    infoRow(String(localized: "\(String(describing: AppModel.eok(m.keyValue))) 도달"), Self.timing(m0, m1))
                    if endAmt > 0, let ds = f.dates.last {
                        infoRow(String(localized: "\(String(describing: Self.ym(ds))) 금액"), "\(AppModel.eok(endAmt)) → \(AppModel.eok(endAmt * exp(mid * T)))")
                    }
                }
                Card {
                    Text("어떻게 번지나?").appFont(16, .bold)
                    HStack(spacing: 6) {
                        cell(String(localized: "금리"), Self.signed(d, "%p", 1), String(localized: "미국 10년물"))
                        cell(String(localized: "기업 이익"), abs(eps) < 0.005 ? String(localized: "그대로") : (eps < 0 ? "−" : "+") + "\(Int((abs(eps) * 100).rounded()))%", String(localized: "\(String(describing: H))년간"))
                        cell(String(localized: "내 기대수익"), abs(mid) < 0.0005 ? String(localized: "그대로") : (mid < 0 ? "−" : "+") + String(format: "%.1f%%p", abs(mid) * 100), String(localized: "1년에"))
                    }
                    NavigationLink { RateWhyView(r: r, when: when, epsLast: file.eps_last) } label: {
                        Text("근거 ›").appFont(13, .bold).foregroundStyle(Theme.teal)
                    }
                }
                weakCard
                rateEventsCard
                footnote(String(localized: "'만약에' 화면 · 추천 아님"))
            }
            .padding(16)
        }
    }

    /// 다가오는 금리 일정 (FOMC·한국은행) + 결정 하루 전 알림 토글. 날짜는 고정 규칙이라 근사일 수 있다
    @ViewBuilder private var rateEventsCard: some View {
        let events = MarketEvents.rateUpcoming(days: 60).prefix(4)
        Card {
            Text("금리 일정").appFont(16, .bold)
            if !events.isEmpty {
                HStack(spacing: 6) {
                    ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                        VStack(spacing: 2) {
                            Text(MarketEvents.when(e.day)).appFont(12, .semibold).foregroundStyle(Theme.teal).lineLimit(1).minimumScaleFactor(0.7)
                            Text(e.kind.replacingOccurrences(of: " 금리 결정", with: "")).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.7)
                        }
                        .padding(.vertical, 8).padding(.horizontal, 4)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 1))
                    }
                }
            }
            if events.isEmpty { Text("60일 안 금리 일정 없음").appFont(13).foregroundStyle(Theme.sub) }
            Toggle(isOn: Binding(get: { m.alerts["rate"] == true }, set: { on in
                if on { Task { if await Notifier.request() { m.alerts["rate"] = true } } } else { m.alerts["rate"] = false }
            })) { Text("전날 다시 보기").appFont(14) }.tint(Theme.purple)
            Text("날짜는 추정 · ±1~2일").appFont(12).foregroundStyle(Theme.muted)
        }
    }

    /// 내 종목 중 금리에 약한 곳: 10년물 +1%p 때 움직임(coef.1)이 낮은 순. 비율·민감도로만 보여 준다 (종목 조언 아님)
    @ViewBuilder private var weakCard: some View {
        let list = m.rows.filter { $0.value > 0 }
            .map { (r: $0, c: ExternalView.coef($0.sym).1) }
            .sorted { $0.c < $1.c }
        if !list.isEmpty {
            let lo = list.map { $0.c }.min() ?? 0, hi = list.map { $0.c }.max() ?? 0
            let span = max(0.0001, hi - lo)
            Card {
                Text("금리에 약한 순").appFont(16, .bold)
                // 가장 약한 쪽 1/3 은 주황, 가장 강한 쪽 1/3 은 초록, 나머지는 회색
                FlowRow(spacing: 6) {
                    ForEach(Array(list.enumerated()), id: \.offset) { _, e in
                        let t = (e.c - lo) / span
                        let tier = list.count < 2 ? 1 : (t < 1.0 / 3 ? 0 : t > 2.0 / 3 ? 2 : 1)
                        let col = tier == 0 ? Theme.orange : tier == 1 ? Theme.muted : Theme.teal
                        Text(e.r.sym.name).appFont(13, .bold).lineLimit(1)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(col.opacity(0.15), in: Capsule())
                            .overlay(Capsule().stroke(col, lineWidth: 1))
                    }
                }
                Text("약함 → 강함").appFont(12).foregroundStyle(Theme.muted)
                compareLink
            }
        }
    }

    /// "구성 바꿔서 다시 테스트하기": 내 길 개편의 비중 비교 화면(MyPathCompareView, 개편 PR에서 추가)으로 간다.
    /// 그 화면이 들어오기 전에는 버튼을 숨긴다. 들어오면 아래 EmptyView 를 NavigationLink { MyPathCompareView() } 로 바꾼다
    @ViewBuilder private var compareLink: some View {
        EmptyView()
    }

    /// 분석 허브 줄: 기본 프리셋("작년만큼 또 오른다")에서 목표에 닿는 시점 변화 (이 화면 첫 카드와 같은 값)
    static func hubTiming(_ m: AppModel) -> String? {
        let macro = MacroData.shared
        guard let file = macro.file, let M = file.meals else { return nil }
        let d = macro.defaultDr
        let r = macro.scen(dr: d, m: 0) ?? RateScen(coef: M.coef, g0: M.g_now, dr: d, m: 0)
        let f = m.forecast, tot = max(1, m.total)
        let kb = m.rows.reduce(0) { $0 + $1.value / tot * ExternalView.coef($1.sym).0 }
        let ci = Closed(T: max(1.0 / 12, f.tY.last ?? 3), sg: max(0.01, f.sigma), mu: f.mu, V0: m.trackValue, K: m.keyValue, M: m.fcMonthly)
        let mid = RateScen.band(r.slope.map { b in kb * (b * d / 3 / 100) })[1]
        return timing(months(ci, 0), months(ci, mid))
    }
    /// 분석 탭 줄다리기: 같은 기본 프리셋일 때 목표 확률 (화면 첫 막대와 같은 식)
    static func hubProb(_ m: AppModel) -> Double? {
        let macro = MacroData.shared
        guard m.forecast.exact, let file = macro.file, let M = file.meals else { return nil }
        let d = macro.defaultDr
        let r = macro.scen(dr: d, m: 0) ?? RateScen(coef: M.coef, g0: M.g_now, dr: d, m: 0)
        let f = m.forecast, tot = max(1, m.total)
        let kb = m.rows.reduce(0) { $0 + $1.value / tot * ExternalView.coef($1.sym).0 }
        let ci = Closed(T: max(1.0 / 12, f.tY.last ?? 3), sg: max(0.01, f.sigma), mu: f.mu, V0: m.trackValue, K: m.keyValue, M: m.fcMonthly)
        return prob(f, ci, RateScen.band(r.slope.map { b in kb * (b * d / 3 / 100) })[1])
    }

    private static func timing(_ a: Int?, _ b: Int?) -> String {
        guard let a, let b else { return b == nil && a != nil ? String(localized: "50년 안엔 어려움") : String(localized: "계산 불가") }
        if a == 0 { return String(localized: "이미 도달") }
        let n = b - a
        if n == 0 { return String(localized: "거의 같음") }
        let y = abs(n) / 12, mo = abs(n) % 12
        let span = (y > 0 ? String(localized: "\(String(describing: y))년") : "") + (y > 0 && mo > 0 ? " " : "") + (mo > 0 ? String(localized: "\(String(describing: mo))개월") : "")
        return "\(span) " + (n > 0 ? String(localized: "늦게") : String(localized: "빨리"))
    }

    private func bar(_ k: String, _ p: Double, _ c: Color) -> some View {
        HStack(spacing: 8) {
            Text(k).appFont(12).foregroundStyle(Theme.sub).frame(width: 44, alignment: .leading)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.muted.opacity(0.25))
                    Capsule().fill(c).frame(width: g.size.width * max(0, min(1, p)))
                }
            }
            .frame(height: 8)
        }
    }

    private func infoRow(_ k: String, _ v: String) -> some View {
        HStack { Text(k).appFont(13).foregroundStyle(Theme.sub); Spacer(); Text(v).appFont(14, .bold) }
    }

    /// 작은 칸 하나 (미션 체크인 칸 모양): 이름, 값, 단위
    private func cell(_ k: String, _ v: String, _ note: String) -> some View {
        VStack(spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
            Text(v).appFont(15, .bold).lineLimit(1).minimumScaleFactor(0.7)
            if !note.isEmpty { Text(note).appFont(11).foregroundStyle(Theme.muted).lineLimit(1) }
        }
        .padding(.vertical, 8).padding(.horizontal, 4)
        .frame(maxWidth: .infinity, minHeight: 64)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 1))
    }

    /// "2029-10-01" → "2029.10"
    static func ym(_ s: String) -> String {
        let p = s.split(separator: "-")
        guard p.count >= 2, let mo = Int(p[1]) else { return s }
        return "\(p[0]).\(mo)"
    }

    static func signed(_ v: Double, _ unit: String, _ digits: Int) -> String {
        (v >= 0 ? "+" : "−") + String(format: "%.\(digits)f", abs(v)) + unit
    }
}

/// 근거 화면 (2단계 · 기업이 버는 돈): 해마다 변화 표 + 해마다 누적 수준·감소 확률 표, y축·오늘선 있는 띠 그래프
private struct RateWhyView: View {
    let r: RateScen
    let when: String
    let epsLast: String?

    var body: some View {
        let H = r.years
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                DetailHead(title: String(localized: "근거"), sub: "")
                Text("2단계 · 기업 이익").appFont(12, .bold).foregroundStyle(Theme.teal)
                Text("\(when), S&P500 이익은?").appFont(18, .bold)
                Card {
                    RateBandChart(cum: r.cum).frame(height: 150)
                    Text("선 보통 · 띠 10번 중 8번 · 오늘 = 100 · 물가 뺀 값").appFont(12).foregroundStyle(Theme.sub)
                }
                Card(dark: true) {
                    tableRow(String(localized: "해마다"), String(localized: "하위"), String(localized: "중앙"), String(localized: "상위"), String(localized: "줄 확률"), head: true)
                    ForEach(0..<H, id: \.self) { y in
                        tableRow(String(localized: "\(String(describing: y + 1))년째"), s(r.yr[y][0]), s(r.yr[y][1]), s(r.yr[y][2]), AppModel.pct(r.dropYr[y]))
                    }
                }
                Card(dark: true) {
                    tableRow(String(localized: "오늘 = 100"), String(localized: "하위"), String(localized: "중앙"), String(localized: "상위"), String(localized: "줄 확률"), head: true)
                    ForEach(0..<H, id: \.self) { y in
                        tableRow(String(localized: "\(String(describing: y + 1))년 뒤"), lv(r.cum[y][0]), lv(r.cum[y][1]), lv(r.cum[y][2]), AppModel.pct(r.dropCumYr[y]))
                    }
                }
                footnote(String(localized: "하위·상위 = 10% 경계 · 1960~\(String(describing: Self.ymDot(epsLast ?? ""))) · 출처 Robert J. Shiller"))
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }

    private func s(_ v: Double) -> String { RateScenarioView.signed(v, "%", 1) }
    /// "2026-06" → "2026.6"
    static func ymDot(_ d: String) -> String {
        let p = d.split(separator: "-")
        guard p.count >= 2, let mo = Int(p[1]) else { return d }
        return "\(p[0]).\(mo)"
    }
    private func lv(_ v: Double) -> String { String(format: "%.0f", 100 * exp(v / 100)) }

    private func tableRow(_ k: String, _ a: String, _ b: String, _ c: String, _ e: String, head: Bool = false) -> some View {
        HStack(spacing: 4) {
            Text(k).frame(maxWidth: .infinity, alignment: .leading)
            Text(a).frame(width: 58, alignment: .trailing)
            Text(b).fontWeight(head ? .regular : .bold).frame(width: 58, alignment: .trailing)
            Text(c).frame(width: 58, alignment: .trailing)
            Text(e).frame(width: 48, alignment: .trailing)
        }
        .appFont(12, head ? .regular : .semibold).foregroundStyle(head ? Theme.sub : Theme.ink).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
    }
}

/// 누적 이익 띠 (오늘 = 100, 0~H년)
private struct RateBandChart: View {
    let cum: [[Double]]
    var body: some View {
        Canvas { ctx, size in
            let lv: [[Double]] = (0..<3).map { k in [100.0] + cum.map { 100 * exp($0[k] / 100) } }
            let n = lv[1].count
            let all = lv.flatMap { $0 }
            let hi = (all.max() ?? 110) * 1.03, lo = (all.min() ?? 90) * 0.97
            let x0: CGFloat = 30   // 왼쪽 y축 눈금 자리
            let x = { (i: Int) -> CGFloat in CGFloat(i) / CGFloat(max(1, n - 1)) * (size.width - x0 - 8) + x0 }
            let y = { (v: Double) -> CGFloat in CGFloat((hi - v) / max(1e-9, hi - lo)) * (size.height - 14) }
            let area = Path { p in
                for i in 0..<n { let pt = CGPoint(x: x(i), y: y(lv[2][i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                for i in stride(from: n - 1, through: 0, by: -1) { p.addLine(to: CGPoint(x: x(i), y: y(lv[0][i]))) }
                p.closeSubpath()
            }
            ctx.fill(area, with: .color(Theme.teal.opacity(0.16)))
            // y축 눈금 (오늘 = 100 기준 10 간격)
            var t = (lo / 10).rounded(.up) * 10
            while t <= hi {
                ctx.stroke(Path { p in p.move(to: CGPoint(x: x0, y: y(t))); p.addLine(to: CGPoint(x: size.width, y: y(t))) },
                           with: .color(Theme.muted.opacity(0.25)), lineWidth: 0.5)
                ctx.draw(Text(String(format: "%.0f", t)).font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: x0 - 4, y: y(t)), anchor: .trailing)
                t += 10
            }
            ctx.stroke(Path { p in p.move(to: CGPoint(x: x0, y: y(100))); p.addLine(to: CGPoint(x: size.width, y: y(100))) },
                       with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            // 오늘 세로선
            ctx.stroke(Path { p in p.move(to: CGPoint(x: x(0), y: 0)); p.addLine(to: CGPoint(x: x(0), y: size.height - 14)) },
                       with: .color(Theme.ink.opacity(0.5)), lineWidth: 1)
            ctx.draw(Text("오늘").font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: x(0), y: size.height - 6), anchor: .leading)
            for k in [0, 2] {
                ctx.stroke(Path { p in for i in 0..<n { let pt = CGPoint(x: x(i), y: y(lv[k][i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } },
                           with: .color(Theme.teal.opacity(0.6)), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            }
            ctx.stroke(Path { p in for i in 0..<n { let pt = CGPoint(x: x(i), y: y(lv[1][i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } },
                       with: .color(Theme.teal), lineWidth: 2)
            for i in 1..<max(2, n) where i < n {
                ctx.draw(Text("\(i)년").font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: x(i) - 12, y: size.height - 6))
            }
        }
        .accessibilityLabel("S&P500 이익 띠 그래프")
    }
}

// MARK: 배당·세금

struct DividendView: View {
    @Environment(AppModel.self) private var m
    /// 분석 탭 시트: "div" 배당만 · "tax" 세금만 (나눈 두 페이지). nil 이면 예전처럼 둘을 전환
    var only: String? = nil
    @State private var tab = "div"
    @State private var extra = 0.0
    /// 배당 계산은 화면을 열 때·자료가 바뀔 때만 한 번 (전환·슬라이더마다 다시 하지 않는다)
    @State private var dd: DivData?
    private var calc: SiteCalc { SiteCalc.shared }

    /// 종목 하나의 앞으로 1년 배당 (화면용)
    struct Paid { let id: String; let name: String; let out: SiteCalc.DivOut; let tax: Double; let krw: Bool; let payMonths: [Int] }
    struct DivData { let months: [String]; let byMonth: [Double]; let paid: [Paid]; let net: Double; let gross: Double; let zero: [String]; let unknown: [String] }

    /// 분석 허브 줄: 한 달 평균 세후 배당(원)과 내년 5월 양도세(원). 이 화면 첫 문장과 같은 식
    static func hubMonthly(_ m: AppModel) -> Double {
        let t = SiteCalc.today(), fx = Market.shared.valFx
        return m.rows.reduce(0.0) { a, r in
            let krw = r.sym.currency == .krw
            let o = SiteCalc.shared.div(r.id).flatMap { SiteCalc.project($0.events, shares: r.h.qty, fx: krw ? 1 : fx, krw: krw, price: r.sym.last, today: t) }
            return a + (o?.y1 ?? 0)
        } / 12
    }
    static func hubTax(_ m: AppModel) -> Double { m.taxGain > 0 ? max(0, SiteCalc.capGainTax(m.taxGain * 1e4)) : 0 }

    private func build() -> DivData {
        // 웹(종목 › 배당·세금)과 같은 식: 지난 12개월 배당락(사이트 시세에서 뽑은 날짜·금액)이 앞으로 같은 달에 (1+증가율)로 다시 온다고 본 세후 원화
        // 환율은 웹 fxBase 와 같게 매매기준율 (없거나 5일 넘게 묵었으면 지금 환율)
        let t = SiteCalc.today(), fx = Market.shared.valFx
        let per = m.rows.map { r -> (id: String, out: SiteCalc.DivOut?, known: Bool, tax: Double) in
            let d = calc.div(r.id), krw = r.sym.currency == .krw
            let o = d.flatMap { SiteCalc.project($0.events, shares: r.h.qty, fx: krw ? 1 : fx, krw: krw, price: r.sym.last, today: t) }
            return (r.id, o, d != nil, krw ? SiteCalc.krDivTax : SiteCalc.wht)
        }
        let months = (1...12).map { String(SiteCalc.addMonths(t, $0).prefix(7)) }
        let names = Dictionary(m.rows.map { ($0.id, $0.sym.name) }, uniquingKeysWith: { a, _ in a })
        let paid = per.compactMap { p -> Paid? in
            guard let o = p.out else { return nil }
            let pm = months.filter { (o.byMonth[$0] ?? 0) > 0 }.map { Int($0.suffix(2)) ?? 0 }
            let nm = names[p.id] ?? ""
            return Paid(id: p.id, name: nm == p.id ? "" : nm, out: o, tax: p.tax, krw: p.tax == SiteCalc.krDivTax, payMonths: pm)
        }
        let net = paid.reduce(0.0) { $0 + $1.out.y1 }, gross = paid.reduce(0.0) { $0 + $1.out.y1 / (1 - $1.tax) }
        let byMonth = months.map { mo in paid.reduce(0.0) { $0 + ($1.out.byMonth[mo] ?? 0) } }
        let zero = per.filter { $0.known && $0.out == nil }.map(\.id)
        let unknown = per.filter { !$0.known }.map(\.id)
        return DivData(months: months, byMonth: byMonth, paid: paid, net: net, gross: gross, zero: zero, unknown: unknown)
    }

    var body: some View {
        let fx = Market.shared.valFx
        // 해외주식 평가 이익: 웹과 같게 (지금 가격 − 매수 단가) × 수량 × 매매기준율. 매수 단가를 넣은 종목만
        let us = m.rows.filter { $0.sym.currency == .usd && $0.h.avg > 0 }
        let usU = us.reduce(0.0) { $0 + ($1.sym.last - $1.h.avg) * $1.h.qty * fx }
        let G = m.taxGain * 1e4

        let inner = VStack(alignment: .leading, spacing: 14) {
            if (only ?? tab) == "div" {
                if let dd { income(months: dd.months, byMonth: dd.byMonth, paid: dd.paid, net: dd.net, gross: dd.gross, zero: dd.zero, unknown: dd.unknown) }
            } else {
                capTax(G: G, usU: usU, usIds: us.map(\.id))
            }
        }
        .padding(16)
        Group {
            // 나눈 한 페이지: 위에 고정할 전환 칸이 없으니 그냥 넘긴다
            if let only {
                ScrollView { inner }
                    .modifier(DetailTitle(title: only == "div" ? String(localized: "배당") : String(localized: "세금")))
            } else {
                PinnedLayout {
                    pinnedBox {
                        DetailHead(title: String(localized: "배당·세금"), sub: "")
                        SegTabs(items: [("div", String(localized: "받는 배당")), ("tax", String(localized: "낼 양도세"))], selection: $tab)
                    }
                } content: { inner }
            }
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .onAppear { if dd == nil { dd = build() } }
        .onChange(of: fx) { dd = build() }
        .onChange(of: m.rows.count) { dd = build() }
        .task { await calc.refresh(); dd = build() }
    }

    // MARK: ① 받는 배당

    @ViewBuilder
    private func income(months: [String], byMonth: [Double], paid: [Paid], net: Double, gross: Double, zero: [String], unknown: [String]) -> some View {
        Card {
            Text("\(Self.ym(months.first ?? "")) ~ \(Self.ym(months.last ?? ""))").appFont(12).foregroundStyle(Theme.sub)
            if paid.isEmpty {
                Text("1년 배당 없음").appFont(20, .bold)
            } else {
                (Text("월 평균\n") + Text(AppModel.wonRead(net / 12)).foregroundColor(Theme.teal))
                    .appFont(22, .bold).lineSpacing(2).roll(AppModel.wonRead(net / 12))
                (Text("1년 ") + Text(AppModel.won1(net)).bold() + Text(" · 세후"))
                    .appFont(13).foregroundStyle(Theme.sub)
            }
        }
        if !paid.isEmpty {
            Card { monthChart(months: months, byMonth: byMonth, paid: paid) }
        }
        let none = (zero.isEmpty ? "" : String(localized: "\(String(describing: zero.joined(separator: "·"))) 무배당"))
            + (unknown.isEmpty ? "" : (zero.isEmpty ? "" : " · ") + String(localized: "\(String(describing: unknown.joined(separator: "·"))) 자료 없음(0원)"))
        if !none.isEmpty {
            Text(none).appFont(13).foregroundStyle(Theme.sub).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.line, in: RoundedRectangle(cornerRadius: 12))
        }
        // 내 키로만 받는 기기: 토스에는 배당 자료가 없다 (SiteCalc.alpacaDivs)
        if Config.yahooOff && !AlpacaKeys.on {
            Button { m.openSettings(.alpaca) } label: {
                (Text("배당 자료는 Alpaca 키로 받아요 ") + Text("키 넣기 ›").foregroundColor(Theme.teal).bold()).appFont(12).foregroundStyle(Theme.sub)
            }.buttonStyle(.plain)
        }
        if !paid.isEmpty {
            let usP = paid.filter { !$0.krw }, krP = paid.filter(\.krw)
            let usTax = usP.reduce(0.0) { $0 + $1.out.y1 / (1 - $1.tax) - $1.out.y1 }, krTax = krP.reduce(0.0) { $0 + $1.out.y1 / (1 - $1.tax) - $1.out.y1 }
            Card {
                Text("세금 뺀 금액").appFont(15, .bold)
                receiptRow(String(localized: "세전"), AppModel.won1(gross))
                if !usP.isEmpty { receiptRow(String(localized: "미국 세금 15%"), "−" + AppModel.won1(usTax)) }
                if !krP.isEmpty { receiptRow(String(localized: "국내 세금 15.4%"), "−" + AppModel.won1(krTax)) }
                Divider()
                receiptRow(String(localized: "받는 돈"), AppModel.won1(net), bold: true)
                if !usP.isEmpty { Text("한국 추가 세금 없음").appFont(12).foregroundStyle(Theme.sub) }
            }
            Card {
                Text("금융소득 2,000만 기준").appFont(15, .bold)
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.track)
                        Capsule().fill(Theme.teal).frame(width: max(4, g.size.width * min(1, gross / 2000e4)))
                    }
                }.frame(height: 8)
                HStack {
                    (Text("지금 ") + Text(AppModel.won1(gross)).bold()).appFont(13)
                    Spacer()
                    Text(gross >= 2000e4 ? String(localized: "기준 넘음") : String(localized: "\(String(describing: AppModel.man(2000e4 - gross))) 남음")).appFont(13).foregroundStyle(Theme.sub)
                }
                Text(gross >= 2000e4
                     ? String(localized: "넘는 부분은 다른 소득과 합산 · 다음 해 5월 신고")
                     : String(localized: "아직 여유 · 예금 이자도 포함"))
                    .appFont(13).padding(10).frame(maxWidth: .infinity, alignment: .leading)
                    .background(gross >= 2000e4 ? Theme.cream : Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
            }
        }
        howCalc([String(localized: "지난 12개월 배당이 같은 달에 들어옴"), String(localized: "지난 3년 늘어난 속도만큼 증가"), String(localized: "회사 결정에 따라 달라짐 · 세무 상담 아님")])
    }

    /// 쌓는 한 칸: 종목 하나(많으면 뒤쪽은 '그 외'로 묶음)
    private struct Series { let id: String; let sched: String; let y1: Double; let byMonth: [Double]; let color: Color }

    /// 막대를 종목 색으로 쌓는다: 매달 주는 종목이 바닥, 그 위로 1년 합이 큰 순
    private func series(months: [String], paid: [Paid]) -> [Series] {
        let colors = [Theme.teal.opacity(0.4), Theme.teal, Theme.orange.opacity(0.8), Theme.blue.opacity(0.75), Theme.purple.opacity(0.6)]
        let sorted = paid.sorted { ($0.out.n >= 12 ? 1 : 0, $0.out.y1) > ($1.out.n >= 12 ? 1 : 0, $1.out.y1) }
        let sched = { (x: Paid) -> String in
            x.out.n >= 12 ? String(localized: "매달")
                : x.payMonths.isEmpty ? String(localized: "1년 \(String(describing: x.out.n))번")
                : String(localized: "\(String(describing: x.payMonths.sorted().map { "\($0)" }.joined(separator: "·")))월")
        }
        let main = sorted.count > colors.count ? Array(sorted.prefix(colors.count - 1)) : sorted
        var out = main.enumerated().map { k, x in
            Series(id: x.id, sched: sched(x), y1: x.out.y1, byMonth: months.map { x.out.byMonth[$0] ?? 0 }, color: colors[k])
        }
        let rest = sorted.dropFirst(main.count)
        if !rest.isEmpty {
            out.append(Series(id: String(localized: "그 외"), sched: rest.map(\.id).joined(separator: "·"), y1: rest.reduce(0) { $0 + $1.out.y1 },
                              byMonth: months.map { mo in rest.reduce(0) { $0 + ($1.out.byMonth[mo] ?? 0) } }, color: Theme.muted.opacity(0.5)))
        }
        return out
    }

    private func monthChart(months: [String], byMonth: [Double], paid: [Paid]) -> some View {
        let mMax = max(1, byMonth.max() ?? 1)
        let pos = byMonth.filter { $0 > 0 }.sorted()
        let med = pos.isEmpty ? 0 : pos[pos.count / 2]
        // 큰 달: 배당이 있는 달 중앙값의 1.5배 이상인 달, 큰 순으로 3개까지
        let hi = Set(byMonth.indices.filter { byMonth[$0] > 0 && byMonth[$0] >= med * 1.5 }.sorted { byMonth[$0] > byMonth[$1] }.prefix(3))
        let mo = { (i: Int) in Int(months[i].suffix(2)) ?? 0 }
        // 연도 구간: 같은 해 달끼리 묶음
        var years: [(y: String, n: Int)] = []
        for s in months { let y = String(s.prefix(4)); if years.last?.y == y { years[years.count - 1].n += 1 } else { years.append((y, 1)) } }
        let ser = series(months: months, paid: paid)

        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(months.indices, id: \.self) { i in
                    VStack(spacing: 3) {
                        Text(hi.contains(i) ? Self.short(byMonth[i]) : " ").appFont(9, .semibold).foregroundStyle(Theme.tealDark)
                            .lineLimit(1).fixedSize()
                        if byMonth[i] <= 0 {
                            RoundedRectangle(cornerRadius: 1).fill(Theme.track).frame(height: 2)
                        } else {
                            // 위 칸부터 그린다 (바닥 = 첫 종목)
                            VStack(spacing: 1) {
                                ForEach(ser.indices.reversed().filter { ser[$0].byMonth[i] > 0 }, id: \.self) { k in
                                    Rectangle().fill(ser[k].color).frame(height: max(1, ser[k].byMonth[i] / mMax * 80))
                                }
                            }
                            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 3, topTrailingRadius: 3))
                        }
                        Text("\(mo(i))").appFont(10).foregroundStyle(Theme.muted)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 118, alignment: .bottom)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(localized: "달마다 배당: ") + months.indices.filter { byMonth[$0] > 0 }.map { String(localized: "\(String(describing: mo($0)))월 \(String(describing: AppModel.won1(byMonth[$0])))") }.joined(separator: ", "))
            // 연도 구간: 막대 칸 폭(12칸, 사이 4)에 맞춰 해마다 선 + 연도
            GeometryReader { g in
                let cell = (g.size.width - 44) / 12
                HStack(spacing: 4) {
                    ForEach(years.indices, id: \.self) { k in
                        Text(years[k].y + String(localized: "년")).appFont(10).foregroundStyle(Theme.muted).lineLimit(1)
                            .frame(width: cell * CGFloat(years[k].n) + 4 * CGFloat(years[k].n - 1)).padding(.top, 3)
                            .overlay(alignment: .top) { Rectangle().fill(Theme.border).frame(height: 1) }
                    }
                }
            }.frame(height: 18)
            // 색 풀이 = 종목별 1년 합 (큰 순)
            VStack(alignment: .leading, spacing: 10) {
                ForEach(ser.sorted { $0.y1 > $1.y1 }, id: \.id) { x in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        RoundedRectangle(cornerRadius: 3).fill(x.color).frame(width: 10, height: 10)
                        Text(x.id).appFont(14, .bold)
                        Text(x.sched).appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
                        Spacer(minLength: 8)
                        Text(AppModel.won1(x.y1)).appFont(14, .bold)
                    }
                }
            }
            .padding(.top, 6)
        }
    }

    // MARK: ② 낼 양도세

    @ViewBuilder
    private func capTax(G: Double, usU: Double, usIds: [String]) -> some View {
        let tax = SiteCalc.capGainTax(G), ded = SiteCalc.cgtDed
        let year = (Int(SiteCalc.today().prefix(4)) ?? 2026) + 1
        Card {
            Text(G > 0 ? String(localized: "올해 판 미국 주식 이익") + " " + AppModel.man(G) : String(localized: "올해 판 미국 주식")).appFont(12).foregroundStyle(Theme.sub)
            if G <= 0 {
                Text("올해 판 이익 없음\n낼 양도세 없음").appFont(20, .bold).lineSpacing(2)
            } else if tax <= 0 {
                Text("\(AppModel.man(ded)) 공제 안\n낼 세금 없음").appFont(20, .bold).lineSpacing(2).roll(String(localized: "\(String(describing: AppModel.man(ded))) 공제 안\n낼 세금 없음"))
            } else {
                (Text("5월에") + Text(" ") + Text(String(localized: "약 ") + AppModel.man(tax)).foregroundColor(Theme.orange))
                    .appFont(26, .heavy)
                // 판 이익 막대: 공제 · 세금 · 내 몫
                GeometryReader { g in
                    let w = g.size.width - 4
                    HStack(spacing: 2) {
                        Rectangle().fill(Theme.green.opacity(0.3)).frame(width: w * min(1, ded / G))
                        Rectangle().fill(Theme.orange).frame(width: w * tax / G)
                        Rectangle().fill(Theme.line)
                    }.clipShape(RoundedRectangle(cornerRadius: 6))
                }.frame(height: 22)
                HStack(alignment: .top, spacing: 14) {
                    taxPart(String(localized: "공제"), AppModel.man(ded))
                    taxPart(String(localized: "세금"), AppModel.man(tax), Theme.orange)
                    Spacer(minLength: 8)
                    taxPart(String(localized: "내 몫"), AppModel.man(G - ded - tax), trailing: true)
                }
                HStack(spacing: 6) {
                    Image(systemName: "calendar").foregroundStyle(Theme.muted)
                    (Text("신고·납부 ") + Text("\(String(year)).5.1~31").bold())
                }
                .appFont(12).padding(.horizontal, 10).padding(.vertical, 6)
                .background(Theme.line, in: RoundedRectangle(cornerRadius: 8))
            }
        }
        Card {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("더 팔면?").appFont(15, .bold)
                if usU > 0 && min(extra, usU) > 0 {
                    Text(AppModel.man(min(extra, usU)) + " " + String(localized: "더 팔 때")).appFont(12).foregroundStyle(Theme.sub)
                }
            }
            if usIds.isEmpty {
                Text("매수 단가 넣은 미국 주식 없음").appFont(13).foregroundStyle(Theme.sub)
            } else if usU <= 0 {
                Text(String(localized: "안 판 이익 없음") + (usU < 0 ? String(localized: " · 손실은 판 해 이익에서 빠짐") : "")).appFont(13).foregroundStyle(Theme.sub)
            } else {
                // 막대 눈금은 전부 팔 때 세금에 고정: 밀면 아래 막대가 자란다
                let x = min(extra, usU), after = SiteCalc.capGainTax(G + x), top = max(1, SiteCalc.capGainTax(G + usU))
                taxBar(String(localized: "지금"), tax, top, Theme.orange.opacity(0.45), Theme.ink)
                taxBar(String(localized: "더 팔면"), after, top, Theme.orange, Theme.orange)
                Slider(value: $extra, in: 0...usU, step: max(1e4, (usU / 200 / 1e4).rounded() * 1e4)).tint(Theme.orange)
                    .accessibilityLabel("추가로 팔 이익")
                    .accessibilityValue(x <= 0 ? String(localized: "0원") : AppModel.man(x))
                HStack { Text("0"); Spacer(); Text(String(localized: "전부") + " " + AppModel.man(usU)) }.appFont(11).foregroundStyle(Theme.muted)
            }
        }
        FlowRow(spacing: 6) {
            ForEach([String(localized: "손실은 이익에서 빠져요"), String(localized: "공제는 해마다 새로")], id: \.self) { t in
                Text(t).appFont(12).foregroundStyle(Theme.teal)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Theme.teal.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        // 이익을 넣고 고치는 곳: 설정의 세금 규칙
        NavigationLink { TaxRulesView() } label: {
            HStack(spacing: 4) {
                Text("세금 규칙").appFont(13, .semibold)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(Theme.sub).padding(.horizontal, 4)
        }
        .buttonStyle(.plain)
    }

    // MARK: 작은 조각

    private func taxPart(_ k: String, _ v: String, _ c: Color = Theme.ink, trailing: Bool = false) -> some View {
        VStack(alignment: trailing ? .trailing : .leading, spacing: 2) {
            Text(k).appFont(11).foregroundStyle(Theme.sub)
            Text(v).appFont(13, .bold).foregroundStyle(c)
        }
    }

    private func taxBar(_ k: String, _ v: Double, _ top: Double, _ fill: Color, _ vc: Color) -> some View {
        HStack(spacing: 8) {
            Text(k).appFont(12).foregroundStyle(Theme.sub).frame(width: 48, alignment: .leading)
            GeometryReader { g in
                RoundedRectangle(cornerRadius: 4).fill(fill).frame(width: max(4, g.size.width * min(1, v / top)))
            }.frame(height: 16)
            Text(AppModel.man(v)).appFont(13, .bold).foregroundStyle(vc).roll(AppModel.man(v)).frame(minWidth: 64, alignment: .trailing)
        }
    }


    private func receiptRow(_ k: String, _ v: String, bold: Bool = false, color: Color = Theme.ink) -> some View {
        HStack {
            Text(k).appFont(13, bold ? .bold : .regular).foregroundStyle(bold ? Theme.ink : Theme.sub)
            Spacer(minLength: 8)
            Text(v).appFont(bold ? 15 : 13, bold ? .bold : .semibold).foregroundStyle(color)
        }
    }

    private func howCalc(_ lines: [String]) -> some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(lines, id: \.self) { footnote($0) }
            }.padding(.top, 6)
        } label: {
            Text("계산 방법").appFont(13, .semibold).foregroundStyle(Theme.sub)
        }
        .tint(Theme.sub)
        .padding(.horizontal, 4)
    }

    /// "2026-11" → "2026.11"
    static func ym(_ s: String) -> String { s.count >= 7 ? "\(s.prefix(4)).\(Int(s.suffix(2)) ?? 0)" : "" }
    /// 막대 위 짧은 금액: 11.4만 · 6천
    static func short(_ v: Double) -> String { v >= 1e4 ? String(format: String(localized: "%.1f만"), v / 1e4) : String(localized: "\(String(describing: Int((v / 1e3).rounded())))천") }
}

/// 흰 배경 + 굵게가 선택된 쪽인 전환 칸 (화면 전환·보기 전환)
private struct SegTabs: View {
    let items: [(String, String)]
    @Binding var selection: String
    var small = false
    var body: some View {
        HStack(spacing: 2) {
            ForEach(items, id: \.0) { it in
                let on = it.0 == selection
                Button { selection = it.0 } label: {
                    Text(it.1).appFont(small ? 12 : 14, on ? .bold : .regular).lineLimit(1)
                        .foregroundStyle(on ? Theme.ink : Theme.sub)
                        .frame(maxWidth: .infinity, minHeight: small ? 32 : 40)
                        .background(on ? Theme.card : .clear, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Theme.track, in: RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: 환율 영향

struct FxImpactView: View {
    @Environment(AppModel.self) private var m
    @State private var period = "1y"
    /// 지금 환율(BASE)에서 옮긴 폭(원). 입구 '환율 10원↓'로 들어오니 −10 에서 시작. 폭으로 두어 시세가 늦게 와도 변화가 같게
    @State private var off: Double = -10

    var body: some View {
        // 웹(전망 › 환율 영향)과 같은 자료·식: 지난 원/달러는 사이트 값(calc.json), 앞날 범위는 웹 전망 모형의 환율 μ·σ(stats.json)로
        // 지금·exp((μ − σ²/2)t + zσ√t)
        // 차트와 앞날 범위는 웹처럼 지금 환율(FX), 화면의 "지금 환율"·평가액 계산은 웹 fxBase 와 같게 매매기준율(BASE) 하나로
        let FX = Market.shared.fx.last, BASE = Market.shared.valFx, c = SiteCalc.shared.file?.fx, fxp = SiteStats.fx(), mu = fxp.0, vol = fxp.1
        let usd = m.rows.filter { $0.sym.currency == .usd }
        let usdV = usd.reduce(0) { $0 + $1.value }, usdW = usdV / max(1, m.total)
        let n = ["1m": 22, "3m": 64, "1y": 253, "3y": 780][period] ?? 253
        let label = ["1m": String(localized: "1개월"), "3m": String(localized: "3개월"), "1y": String(localized: "1년"), "3y": String(localized: "3년")][period] ?? String(localized: "1년")
        let yh = YahooSample.history["FX"]
        let closesAll = c?.closes ?? yh?.closes ?? []
        let k0 = max(0, closesAll.count - n), closes = Array(closesAll[k0...])
        let past = closes.enumerated().map { (i, v) in (closes.count > 1 ? Double(i) / Double(closes.count - 1) - 1 : 0, v) }
        let q = { (z: Double) in (0...20).map { i -> (Double, Double) in let t = Double(i) / 20; return (t, SiteCalc.fxAt(now: FX, t: t, z: z, mu: mu, vol: vol)) } }
        let end = [1.645, 0.674, -0.674, -1.645].map { SiteCalc.fxAt(now: FX, t: 1, z: $0, mu: mu, vol: vol) }
        let vals = past.map(\.1) + [FX] + end
        let ticks = fxTicks(lo: vals.min() ?? FX, hi: vals.max() ?? FX)
        let hi = ticks.last ?? FX + 10, lo = ticks.first ?? FX - 10
        let X = { (t: Double) in t <= 0 ? (t + 1) * 0.7 : 0.7 + t * 0.3 }
        let Y = { (v: Double) in max(0, min(1, (hi - v) / max(1, hi - lo))) }
        let valAt = { (r: Double) in m.total + usdV * (r / BASE - 1) }
        let r = BASE + off, dv = valAt(r) - m.total
        let T = max(0.01, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400))
        let goalD = { let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "yyyy.M"; return f.string(from: m.goalToDate) }()
        let at = { (z: Double) in Int(SiteCalc.fxAt(now: FX, t: T, z: z, mu: mu, vol: vol).rounded()).formatted() }
        let won = { (v: Double) in String(localized: "\(String(describing: Int(v.rounded()).formatted()))원") }
        let eff1y = c?.ret_1y.map { usdW * $0 }
        let baseDay: String = {
            let s = Market.shared.marUse?.date ?? ""
            let p = s.split(separator: "-").compactMap { Int($0) }
            if p.count == 3 { return "\(p[1])/\(p[2])" }
            let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "M/d"; return f.string(from: Date())
        }()
        let lo1 = c?.lo1, hi1 = c?.hi1
        // 지금을 가운데 두고 좌우 같은 폭: 슬라이더 손잡이가 '지금' 칩 아래에 온다
        let span = 150.0

        PinnedLayout {
            // 설명 줄이 없어 흰 띠만 남았다: 띠 없이 제목만 건다
            DetailHead(title: String(localized: "환율이 바뀌면?"), sub: "")
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                // 1. 환율이 바뀌면 내 자산은? (입구 숫자와 같은 −10원에서 시작)
                if usdV > 0 {
                    Card {
                        // 제목은 화면 머리('환율이 바뀌면?')로 올렸다
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .firstTextBaseline) {
                                Text("\(won(r))이면").appFont(13).foregroundStyle(Theme.sub)
                                Spacer()
                                Text("주가 고정").appFont(12).foregroundStyle(Theme.muted)
                            }
                            Text(AppModel.man(valAt(r))).appFont(24, .bold).monospacedDigit().roll(AppModel.man(valAt(r)))
                            Text(abs(dv) < 0.5 ? String(localized: "지금과 같음") : (dv > 0 ? "+" : "−") + AppModel.man(abs(dv)))
                                .appFont(13, .bold).foregroundStyle(abs(dv) < 0.5 ? Theme.muted : Theme.change(dv))
                        }
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
                        Slider(value: Binding(get: { off }, set: { off = $0.rounded() }), in: -span...span, step: 1).tint(Theme.blue).accessibilityLabel("환율")
                        // 내린 쪽 · 지금 · 오른 쪽을 같은 폭으로 (입구 '10원↓'가 바로 눌린 칸)
                        HStack(spacing: 6) {
                            ForEach([-50.0, -10, 0, 10, 50], id: \.self) { v in
                                let on = off == v
                                Button { off = v } label: {
                                    Text(v == 0 ? String(localized: "지금") : Int((BASE + v).rounded()).formatted()).appFont(12, .bold).lineLimit(1)
                                        .frame(maxWidth: .infinity, minHeight: 34)
                                        .foregroundStyle(on ? .white : Theme.ink)
                                        .background(on ? Theme.blue : Theme.card, in: Capsule())
                                        .overlay(Capsule().stroke(on ? Theme.blue : Theme.border, lineWidth: 1.5))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                // 2. 환율 카드
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("원/달러").appFont(13).foregroundStyle(Theme.sub)
                            Text(verbatim: BASE.formatted(.number.precision(.fractionLength(0...1))) + String(localized: "원"))
                                .appFont(26, .bold).monospacedDigit()
                        }
                        Spacer()
                        Text(baseDay).appFont(12).foregroundStyle(Theme.muted)
                    }
                    HStack(alignment: .top, spacing: 4) {
                        // y축 눈금
                        GeometryReader { g in
                            ForEach(ticks, id: \.self) { t in
                                Text(Int(t).formatted()).appFont(10).foregroundStyle(Theme.muted).monospacedDigit()
                                    .position(x: 18, y: Y(t) * g.size.height)
                            }
                        }
                        .frame(width: 36, height: 160)
                        Canvas { ctx, size in
                            func band(_ a: [(Double, Double)], _ b: [(Double, Double)]) -> Path {
                                var p = linePath(a.map { (X($0.0), Y($0.1)) }, in: size)
                                for x in b.reversed() { p.addLine(to: CGPoint(x: X(x.0) * size.width, y: Y(x.1) * size.height)) }
                                p.closeSubpath(); return p
                            }
                            for t in ticks {
                                let y = Y(t) * size.height
                                ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: y)); $0.addLine(to: CGPoint(x: size.width, y: y)) }, with: .color(Theme.line), lineWidth: 1)
                            }
                            ctx.fill(band(q(1.645), q(-1.645)), with: .color(Theme.blue.opacity(0.12)))
                            ctx.fill(band(q(0.674), q(-0.674)), with: .color(Theme.blue.opacity(0.26)))
                            let nx = X(0) * size.width
                            ctx.stroke(Path { $0.move(to: CGPoint(x: nx, y: 0)); $0.addLine(to: CGPoint(x: nx, y: size.height)) }, with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                            if past.count > 1 { ctx.stroke(linePath(past.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round)) }
                            let ny = Y(FX) * size.height
                            ctx.fill(Path(ellipseIn: CGRect(x: nx - 4, y: ny - 4, width: 8, height: 8)), with: .color(Theme.blue))
                            // 띠 오른쪽 끝 값 4개 (겹치면 위아래로 띄움)
                            var ys = end.map { Y($0) * size.height }
                            for i in 1..<ys.count where ys[i] - ys[i - 1] < 11 { ys[i] = ys[i - 1] + 11 }
                            for (i, v) in end.enumerated() {
                                ctx.draw(Text(verbatim: Int(v.rounded()).formatted()).font(.system(size: 9, weight: .semibold)).foregroundColor(Theme.sub),
                                         at: CGPoint(x: size.width - 2, y: min(size.height - 5, max(5, ys[i]))), anchor: .trailing)
                            }
                        }
                        .frame(height: 160)
                        .pulse(Theme.blue, dot: 8) { s in CGPoint(x: X(0) * s.width, y: Y(FX) * s.height) }
                        .accessibilityLabel("원/달러 지난 흐름과 앞으로 1년 범위")
                    }
                    HStack { Text("\(label) 전"); Spacer(); Text("오늘"); Spacer().frame(maxWidth: 60); Text("1년 뒤") }
                        .appFont(11).foregroundStyle(Theme.muted).padding(.leading, 40)
                    if past.count < 2 { Text("지난 자료 없음 · 지금 값만").appFont(11).foregroundStyle(Theme.muted) }
                    VStack(alignment: .leading, spacing: 4) {
                        legend(Theme.ink, 2, String(localized: "실제"))
                        legend(Theme.blue.opacity(0.4), 10, String(localized: "2번 중 1번 · \(String(describing: Int(end[2].rounded()).formatted()))~\(String(describing: Int(end[1].rounded()).formatted()))"))
                        legend(Theme.blue.opacity(0.15), 10, String(localized: "10번 중 9번 · \(String(describing: Int(end[3].rounded()).formatted()))~\(String(describing: Int(end[0].rounded()).formatted()))"))
                    }
                    ChipRow(items: [("1m", String(localized: "1개월")), ("3m", String(localized: "3개월")), ("1y", String(localized: "1년")), ("3y", String(localized: "3년"))], selection: $period, accent: Theme.blue, fill: true)
                }
                // 3. 숫자 칸 4개 (금리 일정 칸 모양)
                Card {
                    HStack(spacing: 6) {
                        cell(String(localized: "1년 범위"), lo1.flatMap { l in hi1.map { "\(Int(l.rounded()).formatted())~\(Int($0.rounded()).formatted())" } } ?? "-", "")
                        cell(String(localized: "출렁임"), volWord(vol), String(localized: "연 \(String(describing: String(format: "%.1f", vol * 100)))%"))
                        cell(String(localized: "\(String(describing: goalD)) 예상"), at(0), "\(at(-1.645))~\(at(1.645))")
                        cell(String(localized: "환율 손익 · 1년"), eff1y.map { AppModel.sgn($0) } ?? "-", "", color: Theme.change(eff1y ?? 0))
                    }
                    Text("지난 흐름 기준 예상").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .task { await SiteCalc.shared.refresh() }
    }

    /// 작은 칸 하나 (금리 일정 칸 모양): 이름, 값, 아래 작은 글
    private func cell(_ k: String, _ v: String, _ note: String, color: Color = Theme.ink) -> some View {
        VStack(spacing: 2) {
            Text(verbatim: k).appFont(11).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.7)
            Text(verbatim: v).appFont(14, .bold).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6).monospacedDigit()
            if !note.isEmpty { Text(verbatim: note).appFont(10).foregroundStyle(Theme.muted).lineLimit(1).minimumScaleFactor(0.7) }
        }
        .padding(.vertical, 8).padding(.horizontal, 4)
        .frame(maxWidth: .infinity, minHeight: 64)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 1))
    }

    private func legend(_ c: Color, _ h: CGFloat, _ s: String) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 16, height: h)
            // ~ 두 개가 마크다운 취소선이 되지 않게 verbatim
            Text(verbatim: s).appFont(11).foregroundStyle(Theme.sub)
        }
    }
}

/// 깔끔한 y축 눈금: 20·50·100·200원 단위 중 눈금 3~5개가 되는 것
private func fxTicks(lo: Double, hi: Double) -> [Double] {
    for step in [20.0, 50, 100, 200, 500] {
        let a = (lo / step).rounded(.down) * step, b = (hi / step).rounded(.up) * step
        let k = Int(((b - a) / step).rounded())
        if k <= 4 { return (0...max(1, k)).map { a + Double($0) * step } }
    }
    let a = (lo / 500).rounded(.down) * 500
    return [a, (hi / 500).rounded(.up) * 500]
}

/// 연 변동성을 말로
private func volWord(_ v: Double) -> String { v < 0.07 ? String(localized: "낮음") : v <= 0.12 ? String(localized: "보통") : String(localized: "큼") }

// MARK: 종목 한눈에 — 숫자만, 추천 문구 없음

/// 종목 한눈에: 비중만큼 나눈 지도. 칸 색은 고른 기간 등락, 누르면 아래에 세 줄 상세 (2026-10-28 결정)
struct GlanceView: View {
    @Environment(AppModel.self) private var m
    @State private var pick: String?
    @State private var period: Period = .d1
    @State private var inUsd = false
    /// 종목 탭 아래에 넣는 넓은 지도: 기간 · 지도 · 누르면 상세만 (2026-10-29)
    var compact = false
    /// 종목 탭의 $|원 선택을 따른다 (2026-10-29). nil이면 이 화면의 선택
    var usd: Bool? = nil
    private var unitUsd: Bool { usd ?? inUsd }

    /// mv: 고른 기간 등락 (기록이 없으면 nil)
    struct Item { let row: AppModel.Row; let k: AppModel.HoldMetrics; let vol: Double; let has: Bool; var mv: Double? = nil }

    /// 가장 짙은 색이 되는 등락 폭. 기간이 길수록 넓게 (2026-10-28 결정)
    static func scale(_ p: Period) -> Double {
        switch p { case .d1: 0.03; case .w1: 0.07; case .m1: 0.12; case .m3: 0.2; case .m6: 0.28; case .y1: 0.4; case .y3: 0.7 }
    }

    /// 고른 기간 원/달러 변화 (지금 ÷ 기간 첫날). 기록이 없으면 1
    static func fxRatio(_ p: Period) -> Double {
        let now = Market.shared.valFx
        var start = 0.0
        if p == .d1 { start = Market.shared.fx.prevClose }
        else if let h = Market.shared.prices?.h["KRW=X"] {
            let c = Calendar.current.dateComponents([.year, .month, .day], from: Date())
            let from = StubPriceProvider.dayNum(c.year ?? 1970, c.month ?? 1, c.day ?? 1) - p.days
            for (d, v) in zip(h.dates, h.closes) {
                if let n = StubPriceProvider.dayNum(d), n >= from { start = v; break }
            }
        }
        return start > 0 && now > 0 ? now / start : 1
    }

    /// 출렁임 단계: 연 변동성 20% 미만 작은 편 / 40% 미만 보통 / 그 위 큰 편 (2026-10-14 결정)
    static func swing(_ v: Double) -> Int { v < 0.2 ? 0 : v < 0.4 ? 1 : 2 }
    static let swingName = [String(localized: "작음"), String(localized: "보통"), String(localized: "큼")]

    var body: some View {
        let all = m.rows.map { r in Item(row: r, k: m.metrics(r), vol: SiteStats.params(r.id).2, has: m.prices.hasHistory(r.sym)) }
        // 원으로 보면 달러 종목은 기간 환율 변화까지 곱한다 (2026-10-28 결정)
        let fx = unitUsd ? 1 : Self.fxRatio(period)
        let sorted = all.sorted { $0.k.w > $1.k.w }.map { (d0: Item) -> Item in
            var d = d0
            let pts = m.prices.series(d.row.sym, period: period)
            let k = d.row.sym.currency == .usd ? fx : 1
            if period == .d1 || d.has, let a = pts.first, let b = pts.last, a > 0 { d.mv = b / a * k - 1 }
            return d
        }

        if compact {
            VStack(alignment: .leading, spacing: 10) {
                ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true, pad: 4)
                treemap(sorted, height: 180)
                if let d = sorted.first(where: { $0.row.id == pick }) {
                    card(d).id(d.row.id).transition(.opacity)
                }
            }
        } else {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                DetailHead(title: String(localized: "종목 한눈에"), sub: "")
                HStack {
                    Spacer()
                    HoldSeg(items: [(true, "$"), (false, String(localized: "원"))], selection: $inUsd)
                        .accessibilityLabel("금액 단위: \(inUsd ? String(localized: "달러") : String(localized: "원"))")
                }
                ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true, pad: 4)
                if let top = sorted.first {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(summary(top)).appFont(14).foregroundStyle(Theme.ink).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 14))
                }
                treemap(sorted)
                Text("빨강은 오름, 파랑은 내림 · 짙을수록 크게").appFont(11).foregroundStyle(Theme.sub)
                if let d = sorted.first(where: { $0.row.id == pick }) {
                    card(d).id(d.row.id).transition(.opacity)
                } else {
                    Text("칸을 누르면 자세히 보여요").appFont(13).foregroundStyle(Theme.sub)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                }
                footnote(String(localized: "매매 추천 아님"))
            }
            .padding(16).padding(.bottom, 40)
            .frame(maxWidth: Theme.maxWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        }
    }

    private func summary(_ t: Item) -> String {
        let n = t.row.sym.name, w = AppModel.pct(t.k.w)
        if t.k.w >= 0.5 { return String(localized: "자산의 \(String(describing: w))가 \(String(describing: n)) · 함께 움직여요") }
        if t.k.w >= 0.3 { return String(localized: "자산의 \(String(describing: w))가 \(String(describing: n)) · 크게 따라 움직여요") }
        return String(localized: "1위 \(String(describing: n)) \(String(describing: w)) · 여러 종목에 나뉨")
    }

    /// 거의 안 움직이는 자산(SGOV 등)
    private static func calm(_ d: Item) -> Bool { d.has && d.vol < 0.02 }

    /// 칸 글: 기간 등락, 현금성, 기록 없음
    private func tileText(_ d: Item) -> String {
        if Self.calm(d) { return String(localized: "현금성") }
        guard let mv = d.mv else { return String(localized: "기록 모으는 중") }
        return AppModel.sgn(mv)
    }

    /// 칸 금액: 그 기간 평가금 변화. 달러로 보면 달러 종목만 $ (종목 탭과 같다)
    private func amountText(_ d: Item) -> String? {
        guard !Self.calm(d), let mv = d.mv, mv > -1 else { return nil }
        let usd = unitUsd && d.row.sym.currency == .usd
        let v = usd ? d.row.value / Market.shared.valFx : d.row.value
        let ch = v - v / (1 + mv)
        return (ch < 0 ? "−" : "+") + (usd ? "$" + Int(abs(ch).rounded()).formatted() : HoldFmt.won(abs(ch)))
    }

    /// 칸 색: 오르면 빨강, 내리면 파랑. 기간마다 정한 폭(scale)에서 가장 짙다. 현금성·기록 없음은 회색
    private func tone(_ d: Item) -> (Color, Color) {
        guard !Self.calm(d), let mv = d.mv else { return (Theme.line, Theme.ink) }
        let t = min(1, abs(mv) / Self.scale(period))
        return (Theme.change(mv).opacity(0.15 + 0.75 * t), t > 0.45 ? .white : Theme.ink)
    }

    private func treemap(_ list: [Item], height: CGFloat = 340) -> some View {
        GeometryReader { g in
            // 0%인 종목도 누를 수 있게 아주 작은 칸은 남긴다
            let rs = Self.squarify(list.map { max($0.k.w, 0.006) }, in: CGRect(origin: .zero, size: g.size))
            ZStack(alignment: .topLeading) {
                ForEach(Array(list.enumerated()), id: \.element.row.id) { i, d in
                    let r = rs[i].insetBy(dx: 1.5, dy: 1.5)
                    tile(d, CGSize(width: max(0, r.width), height: max(0, r.height)))
                        .position(x: r.midX, y: r.midY)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
        }
        .frame(height: height)
    }

    private func tile(_ d: Item, _ sz: CGSize) -> some View {
        let (bg, fg) = tone(d)
        let on = pick == d.row.id
        let big = sz.width > 120 && sz.height > 80
        return Button {
            withAnimation(.easeOut(duration: 0.2)) { pick = on ? nil : d.row.id }
        } label: {
            VStack(spacing: 2) {
                if sz.width > 40 && sz.height > 28 {
                    Text(d.row.sym.name).appFont(big ? 15 : 11, .bold).lineLimit(1).minimumScaleFactor(0.6)
                    if sz.height > 46 {
                        Text(tileText(d)).appFont(big ? 22 : 12, .bold).lineLimit(1).minimumScaleFactor(0.6)
                    }
                    if sz.height > 64, let a = amountText(d) {
                        Text(a).appFont(big ? 13 : 10, .semibold).lineLimit(1).minimumScaleFactor(0.6)
                    }
                }
            }
            .padding(4)
            .foregroundStyle(fg)
            .frame(width: sz.width, height: sz.height)
            .background(bg, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Theme.ink, lineWidth: on ? 2 : 0))
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }

    /// 비중 지도 칸 나누기 (squarified treemap). 값은 큰 순서로 받는다
    static func squarify(_ vals: [Double], in box: CGRect) -> [CGRect] {
        let total = vals.reduce(0, +)
        guard total > 0, box.width > 0, box.height > 0 else { return vals.map { _ in .zero } }
        let scale = Double(box.width * box.height) / total
        var areas = ArraySlice(vals.map { $0 * scale })
        var rect = box, out: [CGRect] = []
        while !areas.isEmpty {
            let side = Double(min(rect.width, rect.height))
            func worst(_ row: [Double]) -> Double {
                let s = row.reduce(0, +), mx = row.max() ?? 0, mn = row.min() ?? 0
                guard s > 0, mn > 0 else { return .infinity }
                return max(side * side * mx / (s * s), s * s / (side * side * mn))
            }
            var row: [Double] = []
            while let a = areas.first, row.isEmpty || worst(row + [a]) <= worst(row) {
                row.append(a); areas = areas.dropFirst()
            }
            let s = row.reduce(0, +)
            if rect.width >= rect.height {
                // 왼쪽에 세로 한 줄
                let w = CGFloat(s / Double(rect.height))
                var y = rect.minY
                for a in row {
                    let h = CGFloat(a) / w
                    out.append(CGRect(x: rect.minX, y: y, width: w, height: h)); y += h
                }
                rect = CGRect(x: rect.minX + w, y: rect.minY, width: max(0, rect.width - w), height: rect.height)
            } else {
                // 위에 가로 한 줄
                let h = CGFloat(s / Double(rect.width))
                var x = rect.minX
                for a in row {
                    let w = CGFloat(a) / h
                    out.append(CGRect(x: x, y: rect.minY, width: w, height: h)); x += w
                }
                rect = CGRect(x: rect.minX, y: rect.minY + h, width: rect.width, height: max(0, rect.height - h))
            }
        }
        return out
    }

    /// 세 줄 상세: 이름·비중·최고가 대비 / 1년 범위 막대 / 1년·출렁임·내 보유
    private func card(_ d: Item) -> some View {
        let s = d.row.sym
        var tail: [String] = d.has
            ? [String(localized: "1년 \(String(describing: AppModel.sgn(d.k.r1)))"), String(localized: "출렁임 \(String(describing: Self.swingName[Self.swing(d.vol)]))")]
            : [String(localized: "1년 기록 모으는 중")]
        if d.row.cost > 0 && s.id != KoreaEstimate.sumId { tail.append(String(localized: "내 보유 \(String(describing: AppModel.sgn(d.k.vc)))")) }
        return DetailLink(sym: s) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    Text(s.name).appFont(15, .bold).lineLimit(1)
                    Text(AppModel.pct(d.k.w)).appFont(12, .semibold).foregroundStyle(Theme.tealDark)
                    Spacer(minLength: 4)
                    if d.has {
                        Text(d.k.dd > -0.0005 ? String(localized: "1년 최고가") : String(localized: "최고가 대비 \(String(describing: AppModel.sgn(d.k.dd)))"))
                            .appFont(13, .semibold).foregroundStyle(Theme.change(d.k.dd > -0.0005 ? 1 : -1))
                    }
                    Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Theme.teal)
                }
                if d.has { RangeBar(lo: d.k.lo, hi: d.k.hi, now: s.last) } else { RangeBar(lo: 0, hi: 0, now: 0) }
                Text(tail.joined(separator: " · ")).appFont(12).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

/// 1년 가격 범위 막대 (최저~최고, 지금 위치에 점). 기록이 없으면 점선만
private struct RangeBar: View {
    let lo, hi, now: Double
    var body: some View {
        GeometryReader { g in
            let ok = hi > lo && hi > 0
            let x = ok ? min(1, max(0, (now - lo) / (hi - lo))) : 0
            ZStack(alignment: .leading) {
                if ok {
                    Capsule().fill(Theme.track).frame(height: 6)
                    Capsule().fill(Theme.dash).frame(width: max(6, g.size.width * x), height: 6)
                    Circle().fill(Theme.ink).frame(width: 12, height: 12)
                        .overlay(Circle().stroke(Theme.card, lineWidth: 2))
                        .offset(x: max(0, g.size.width * x - 6))
                } else {
                    Path { p in p.move(to: CGPoint(x: 0, y: 6)); p.addLine(to: CGPoint(x: g.size.width, y: 6)) }
                        .stroke(Theme.dash, style: StrokeStyle(lineWidth: 2, dash: [4, 4]))
                }
            }.frame(maxHeight: .infinity)
        }.frame(height: 12)
    }
}

struct GlanceTip: Identifiable { let topic: String; let id: String }

/// ⓘ 설명 시트 ("숫자 풀이" 화면을 대신한다)
private struct GlanceSheet: View {
    let tip: GlanceTip
    let items: [GlanceView.Item]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let d = items.first { $0.row.id == tip.id }
        let n = d?.row.sym.name ?? ""
        VStack(alignment: .leading, spacing: 14) {
            switch tip.topic {
            case "vol":
                head(String(localized: "가격 출렁임"), String(localized: "지난 1년 동안 가격이 하루하루 얼마나 크게 오르내렸는지예요. 크면 크게 오를 수도, 크게 내릴 수도 있어요."))
                let lv = GlanceView.swing(d?.vol ?? 0)
                HStack(spacing: 4) {
                    ForEach(0..<3, id: \.self) { i in
                        VStack(spacing: 4) {
                            RoundedRectangle(cornerRadius: 3).fill(i == lv ? Theme.teal : Theme.track).frame(height: 8)
                            Text(GlanceView.swingName[i] + (i == lv ? " · \(n)" : "")).appFont(11, i == lv ? .bold : .regular)
                                .foregroundStyle(i == lv ? Theme.ink : Theme.muted)
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("내 종목으로 보면").appFont(13, .bold)
                    ForEach([2, 1, 0], id: \.self) { i in
                        let names = items.filter { GlanceView.swing($0.vol) == i }.map(\.row.sym.name)
                        if !names.isEmpty {
                            HStack(alignment: .top) {
                                Text(GlanceView.swingName[i]).appFont(12, .semibold).frame(width: 52, alignment: .leading)
                                Text(names.joined(separator: ", ")).appFont(12).foregroundStyle(Theme.sub)
                            }
                        }
                    }
                }
                small(String(localized: "숫자로 보고 싶다면: \(String(describing: n))의 1년 변동성은 연 \(String(describing: AppModel.pct(d?.vol ?? 0)))예요."))
            case "dd":
                head(String(localized: "1년 최고가 대비"), String(localized: "지난 1년 동안 가장 높았던 종가와 지금 가격을 비교한 값이에요. 0%에 가까우면 1년 중 가장 비싼 쪽이에요."))
                if let d { small(String(localized: "\(String(describing: n)): 가장 비쌀 때 \(String(describing: AppModel.price(d.row.sym, d.k.hi))), 지금 \(String(describing: AppModel.price(d.row.sym, d.row.sym.last))) (\(String(describing: AppModel.sgn(d.k.dd))))")) }
            case "r1":
                head(String(localized: "1년 전보다"), String(localized: "1년 전 종가와 지금 가격을 비교한 값이에요. 환율은 빼고 그 나라 돈으로 계산해요."))
                if let d { small("\(n): \(AppModel.sgn(d.k.r1))") }
            default:
                head(String(localized: "내 자산에서 차지하는 몫"), String(localized: "내 종목 평가액을 모두 더한 것 중 이 종목이 차지하는 몫이에요. 크면 이 종목 가격이 내 자산을 크게 움직여요."))
                if let d { small(String(localized: "\(String(describing: n)): 내 자산의 \(String(describing: AppModel.pct(d.k.w)))")) }
            }
            Spacer(minLength: 0)
            Button { dismiss() } label: {
                Text("알겠어요").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 13)
                    .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(.plain)
        }
        .padding(20).padding(.top, 8)
        .presentationDragIndicator(.visible)
    }

    private func head(_ t: String, _ s: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(t).appFont(18, .bold)
            Text(s).appFont(14).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
        }
    }
    private func small(_ t: String) -> some View { Text(t).appFont(12).foregroundStyle(Theme.muted) }
}
