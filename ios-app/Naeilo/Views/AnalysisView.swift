import SwiftUI

enum AnalysisRoute: Hashable, Identifiable {
    case forecast, rateScen, external, dividend, tax, fx, glance, calm, goal
    var id: Self { self }

    func title(_ m: AppModel) -> String {
        switch self {
        case .calm: return String(localized: "내 투자 습관")
        case .forecast: return m.forecastTitle
        case .rateScen: return String(localized: "금리 충격 테스트")
        case .external: return String(localized: "외부 요인")
        case .dividend: return String(localized: "배당")
        case .tax: return String(localized: "세금")
        case .goal: return String(localized: "목표 고치기")
        case .fx: return String(localized: "환율 영향")
        case .glance: return String(localized: "종목 한눈에")
        }
    }
}

/// 분석 상세 화면을 좌우로 넘겨 바로 옆 분석으로 간다 (분석 탭 목록 순서)
private struct DetailInPagerKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    var detailInPager: Bool {
        get { self[DetailInPagerKey.self] }
        set { self[DetailInPagerKey.self] = newValue }
    }
}

/// 상세 화면 제목: 넘기기 화면 안에서는 넘기기 화면이 지금 쪽 제목을 단다 (쪽마다 달면 서로 덮음)
struct DetailTitle: ViewModifier {
    @Environment(\.detailInPager) private var inPager
    let title: String
    func body(content: Content) -> some View {
        if inPager { content } else { content.navigationTitle(title) }
    }
}

/// 분석 상세 한 쪽
struct AnalysisPage: View {
    let route: AnalysisRoute
    var body: some View {
        switch route {
        // 7일 무료 뒤 구독하지 않으면 목표(내 길)·확률 전망만 잠근다 (Support.pro)
        case .forecast: if Support.shared.pro { ForecastView() } else { SubLocked(what: String(localized: "확률 전망")) }
        case .rateScen: RateScenarioView()
        case .external: ExternalView()
        case .dividend: DividendView(only: "div")
        case .tax: DividendView(only: "tax")
        case .goal: GoalSetView(fromSettings: true)
        case .fx: FxImpactView()
        case .glance: GlanceView()
        case .calm: CalmView()
        }
    }
}

/// 홈·알림에서 바로 들어오는 상세 (분석 탭 위에 쌓인다)
struct AnalysisPager: View {
    @Environment(AppModel.self) private var m
    @State private var sel: AnalysisRoute
    static let order: [AnalysisRoute] = [.calm, .forecast, .rateScen, .external, .fx, .glance, .dividend, .tax]

    init(start: AnalysisRoute) { _sel = State(initialValue: start) }

    var body: some View {
        TabView(selection: $sel) {
            ForEach(Self.order, id: \.self) { r in AnalysisPage(route: r).tag(r) }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(ContentPopBlock())
        .environment(\.detailInPager, true)
        .background(Theme.bg.ignoresSafeArea())
        .navigationTitle(sel.title(m))
        .toolbarTitleDisplayMode(.inlineLarge)
        .toolbar {
            // 제목 밑 점: 지금 몇 번째 분석인지, 좌우로 넘길 수 있다는 표시
            ToolbarItem(placement: .principal) {
                VStack(spacing: 3) {
                    Text(sel.title(m)).appFont(16, .bold).lineLimit(1)
                    HStack(spacing: 4) {
                        ForEach(Self.order, id: \.self) { r in
                            Circle().fill(r == sel ? Theme.teal : Theme.border).frame(width: 5, height: 5)
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(sel.title(m)), \((Self.order.firstIndex(of: sel) ?? 0) + 1) / \(Self.order.count), 좌우로 넘기면 다른 분석")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: sel)
        .detailSheet(period: .y1)
    }
}

/// 분석 재료 시트: 제목 왼쪽, X 오른쪽. 시트 안 링크는 같은 시트에서 넘기고(뒤로 화살표), X 는 몇 단계 들어갔어도 한 번에 닫는다
struct AnalysisSheet: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    let route: AnalysisRoute

    var body: some View {
        NavigationStack {
            // 첫 화면 제목은 글자만 (다른 팝업 GameHeader 와 같은 크기). 툴바에 두면 iOS 26 이 알약 바탕을 씌운다
            AnalysisPage(route: route)
                .environment(\.detailInPager, true)
                .toolbar(.hidden, for: .navigationBar)
                .safeAreaInset(edge: .top, spacing: 0) {
                    Text(route.title(m)).appFont(20, .bold).foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.8)
                        .padding(.leading, 16).padding(.trailing, 56)
                        .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                        .background(Theme.bg)
                }
                .navigationDestination(for: AnalysisRoute.self) { AnalysisPage(route: $0).environment(\.detailInPager, false) }
                .detailSheet(period: .y1)
        }
        .environment(\.inPopup, true)
        // 넘긴 화면에도 같은 자리에 (툴바는 화면마다 따로라 시트 위에 얹는다)
        .overlay(alignment: .topTrailing) {
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.ink)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.trailing, 8).padding(.top, 2)   // 첫 화면 제목 줄(48) 가운데
            .accessibilityLabel("닫기")
        }
    }
}

// 분석 탭 = 내 길 메인 (2026-10-23 확정 시안 '1차 시안'): 확률 박스 → 왜 낮을까(줄다리기) → 언제 흔들릴까(그래프 + 일정 깃발) → 어떻게 올릴까(구성별 닿는 때).
// 일곱 재료(전망·금리·외부 요인·배당·세금·환율·종목 한눈에·습관)는 시트로 연다. 숫자는 각 상세 화면과 같은 식 (허브 전용 숫자를 따로 계산하지 않는다)
struct AnalysisView: View {
    @Environment(AppModel.self) private var m
    @AppStorage(BoxLook.key) private var lookRaw = BoxLook.jump.rawValue
    @State private var sheet: AnalysisRoute?
    @State private var toGoal = true
    @State private var comparing = false
    /// 색은 의미 3개만: 초록 들어오는 돈·좋아짐 / 주황 나가는 돈·늦어짐 / 회색 정보
    static let good = Color(hex: 0x2C5A54, dark: 0x8FD0C4)
    static let warn = Color(hex: 0xA8521A, dark: 0xE8A060)
    private static let goodBg = Color(hex: 0xE3F0ED, dark: 0x173531), warnBg = Color(hex: 0xFBEBDD, dark: 0x3A2A1C)
    private static let goodInk = Color(hex: 0x2F4A46, dark: 0xB8DCD5), warnInk = Color(hex: 0x6B4A33, dark: 0xE6C6AA)

    private func name(_ r: AppModel.Row) -> String { r.sym.name.isEmpty ? r.id : r.sym.name }

    var body: some View {
        let p = m.myPath, f = m.forecast, pro = Support.shared.pro
        // 확률: 목표 = 목표일에 목표 이상, 회복 = 기간 안 본전 (내 길 판정 카드와 같은 값)
        let pPlan = m.isGoal ? f.pGoal : f.prob(min(3, p.end - p.start))
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AppHeader(tab: String(localized: "분석")).padding(.horizontal, -16)
                SampleNote()
                Button { sheet = .forecast } label: {
                    ChanceBox(look: BoxLook(rawValue: lookRaw) ?? .jump, d: chance(p, f, pPlan, pro: pro)).boxScheme()
                }
                .buttonStyle(.plain)
                tug(pPlan, known: pro && f.exact)
                shake(p, f, pro: pro)
                if m.isGoal && pro { raise }
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .task { await MacroData.shared.load() }
        .task { await SiteCalc.shared.refresh() }
        // 당기면 시세는 새로, 계산·금리 자료는 아직 신선하면 다시 받지 않는다
        .refreshable {
            await Market.shared.pullRefresh(m)
            await MacroData.shared.load()
            await SiteCalc.shared.refresh()
        }
        .sheet(item: $sheet) { AnalysisSheet(route: $0).environment(m) }
        // 알림·탭 화면 링크로 들어온 분석도 같은 분석 팝업으로 (예전엔 탭 안에서 넘겼다)
        .onAppear { openPath() }
        .onChange(of: m.analysisPath) { _, _ in openPath() }
    }

    private func openPath() {
        guard let r = m.analysisPath.first else { return }
        m.analysisPath = []
        sheet = r
    }

    /// 판 흰 카드 (시안: 테두리 없이 둥근 20)
    private func panel<C: View>(_ title: String, spacing: CGFloat = 12, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: spacing) {
            Text(title).appFont(17, .bold)
            c()
        }
        .padding(18).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 20))
    }

    // MARK: 확률 박스 값: 가로 자리 = 금액 ÷ 눈금 끝. 목표선 ≈ 83%, 좋을 때 ≤ 92% (시안 자리)
    private func chance(_ p: AppModel.MyPath, _ f: AppModel.Forecast, _ pPlan: Double, pro: Bool) -> ChanceBox.Data {
        let K = max(1, p.key), now = m.trackValue
        let left = AppModel.leftText(m.isGoal ? AppModel.yrs(Day.today, Day.str(m.goalToDate)) : p.end - p.start)
        let head = m.isGoal
            ? String(localized: "\(String(describing: AppModel.wonK(m.gK))) · \(String(describing: ForecastView.ym(Day.str(m.goalToDate)).short)) · \(String(describing: left)) 남음")
            : String(localized: "본전 \(String(describing: AppModel.eok(K))) · 1년 안 · \(String(describing: left)) 남음")
        let goalName = m.isGoal ? AppModel.wonK(m.gK) : String(localized: "본전")
        let nowText = String(localized: "지금 \(String(describing: AppModel.eok(now)))")
        guard pro else {
            return .init(head: head, verdict: nil, pct: "", status: String(localized: "구독 시 열림"), now: 0, goal: nil, dots: [], goalLabel: goalName, foot: String(localized: "7일 무료 끝"), nowText: nowText)
        }
        // 목표일(회복: 1년 뒤)의 나쁠 때·보통·좋을 때 = 전망 5%·가운데·95% (전망 화면 '10번 중 9번'과 같은 끝), 현금 더함
        let i = f.idx(years: p.end - p.start), add = f.exact ? m.cashKrw : 0
        let three: [Double] = f.exact && i < f.q50.count ? [f.q5[i], f.q50[i], f.q95[i]].map { $0 + add } : []
        let top = max(K / 0.83, (three.max() ?? 0) / 0.92, now / 0.9)
        let over = three.filter { $0 >= K }.count
        let foot = three.isEmpty ? "" : [String(localized: "셋 다 선 아래"), String(localized: "셋 중 하나만 선을 넘음"), String(localized: "셋 중 둘이 선을 넘음"), String(localized: "셋 다 선을 넘음")][over]
        return .init(head: head, verdict: f.exact ? AppModel.verdict(pPlan) : nil, pct: AppModel.pct(pPlan), status: String(localized: "가능성 계산 중"),
                     now: now / top, goal: K / top, dots: three.map { $0 / top }, goalLabel: goalName, foot: foot, nowText: nowText)
    }

    // MARK: 왜 낮을까? 줄다리기: 왼쪽 끌어내리는 힘, 오른쪽 끌어올리는 힘. 칸 값은 각 상세 화면 첫 숫자와 같은 식
    private struct Pull { let label: String; let value: String; let route: AnalysisRoute }

    private func tug(_ p: Double, known: Bool) -> some View {
        let tot = max(1, m.total)
        var down: [Pull] = [], up: [Pull] = []
        // 종목 한눈에: 비중 1위가 40% 넘으면 쏠림
        if let top = m.rows.max(by: { $0.value < $1.value }) {
            let w = top.value / tot
            if w >= 0.4 { down.append(Pull(label: String(localized: "\(String(describing: name(top))) 쏠림"), value: AppModel.pct(w), route: .glance)) }
            else { up.append(Pull(label: String(localized: "고루 나눔"), value: String(localized: "1위 \(String(describing: AppModel.pct(w)))"), route: .glance)) }
        }
        // 금리: 기본 프리셋("작년만큼 또 오른다")일 때 목표 확률 (목표 루트·구독). 그 밖엔 도착 시점 변화. 확률이 오르면 끌어올리는 쪽
        let rp = known && m.isGoal ? RateScenarioView.hubProb(m) : nil
        let rt = RateScenarioView.hubTiming(m)
        let rate = rp.map { AppModel.pct($0) }
            ?? rt?.replacingOccurrences(of: String(localized: "약 "), with: "")
            ?? (MacroData.shared.failed ? String(localized: "자료 없음") : String(localized: "계산 중"))
        let rateUp = rp.map { $0 > p + 0.005 } ?? (rt?.hasSuffix(String(localized: "빨리")) == true)
        let ratePull = Pull(label: String(localized: "금리 오르면"), value: rate, route: .rateScen)
        if rateUp { up.append(ratePull) } else { down.append(ratePull) }
        // 환율: 원화가 10원 강해지면 달러 평가액 × 10 / 기준 환율만큼 준다
        let usdV = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.value }
        down.append(Pull(label: String(localized: "환율 10원↓"), value: usdV > 0 ? "−" + AppModel.man(usdV * 10 / max(1, Market.shared.valFx)) : String(localized: "그대로"), route: .fx))
        let tax = DividendView.hubTax(m)
        if tax > 0 { down.append(Pull(label: String(localized: "5월 세금"), value: "−" + AppModel.man(tax), route: .tax)) }
        if m.isGoal && m.gM > 0 { up.append(Pull(label: String(localized: "매달 적립"), value: AppModel.wonK(m.gM), route: .goal)) }
        let mo = DividendView.hubMonthly(m)
        up.append(Pull(label: String(localized: "배당"), value: mo > 0 ? String(localized: "월 \(String(describing: Self.short(mo)))") : String(localized: "없음"), route: .dividend))
        let verdict = AppModel.verdict(p)
        let title = !known ? String(localized: "무엇이 당길까?") : verdict == AppModel.verdict(0) ? String(localized: "왜 낮을까?") : verdict == AppModel.verdict(1) ? String(localized: "왜 높을까?") : String(localized: "왜 반반일까?")
        // 줄 굵기: 지는 쪽이 굵다 (확률이 낮으면 끌어내리는 힘이 굵다)
        let hd: CGFloat = !known ? 6 : p < 0.5 ? 8 : 5, hu: CGFloat = !known ? 6 : p < 0.5 ? 5 : 8
        return panel(title, spacing: 14) {
            HStack(spacing: 0) {
                UnevenRoundedRectangle(topLeadingRadius: hd / 2, bottomLeadingRadius: hd / 2).fill(Self.warn).frame(height: hd)
                Circle().fill(Theme.ink).frame(width: 20, height: 20)
                UnevenRoundedRectangle(bottomTrailingRadius: hu / 2, topTrailingRadius: hu / 2).fill(Self.good).frame(height: hu)
            }
            .accessibilityHidden(true)
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("끌어내리는 힘").appFont(12, .bold).foregroundStyle(Self.warn)
                    ForEach(down.indices, id: \.self) { pullChip(down[$0], down: true) }
                }
                VStack(alignment: .trailing, spacing: 8) {
                    Text("끌어올리는 힘").appFont(12, .bold).foregroundStyle(Self.good)
                    ForEach(up.indices, id: \.self) { pullChip(up[$0], down: false) }
                    habitChip
                }
            }
        }
    }

    /// 월 4.8만 · 9천
    private static func short(_ krw: Double) -> String {
        if AppModel.en { return AppModel.wonRead(krw) }
        if krw >= 1e4 {
            let v = (krw / 1e3).rounded() / 10
            return (v == v.rounded() ? String(Int(v)) : String(format: "%.1f", v)) + String(localized: "만")
        }
        return String(localized: "\(String(describing: max(1, Int((krw / 1e3).rounded()))))천")
    }

    private func pullChip(_ x: Pull, down: Bool) -> some View {
        Button { sheet = x.route } label: {
            HStack(spacing: 4) {
                Text(x.label).appFont(13, .bold).foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.75)
                Spacer(minLength: 2)
                Text(x.value + " ›").appFont(13).foregroundStyle(down ? Self.warnInk : Self.goodInk).lineLimit(1).fixedSize()
            }
            .padding(.horizontal, 10).frame(maxWidth: .infinity, minHeight: 44)
            .background(down ? Self.warnBg : Self.goodBg, in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(x.label) \(x.value)")
    }

    // 내 투자 습관: 노란 꼬리표 칸 (홈 리마인드 띠와 같은 모양)
    private var habitChip: some View {
        let s = m.calm.stats(), done = s.n >= Calm.minEvents
        let v = done ? String(localized: "지수 \(String(describing: s.index))") : String(localized: "기록 \(String(describing: s.n))/\(String(describing: Calm.minEvents))")
        return Button { sheet = .calm } label: {
            HStack(spacing: 6) {
                Text("투자 습관").appFont(11, .bold).foregroundStyle(Theme.inkFixed).lineLimit(1).fixedSize()
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.yellow, in: Capsule())
                Spacer(minLength: 2)
                Text(v + " ›").appFont(13).foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.8)
            }
            .padding(.horizontal, 10).frame(maxWidth: .infinity, minHeight: 44)
            .background(Theme.cream, in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(String(localized: "내 투자 습관, \(String(describing: v))"))
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
    }

    // MARK: 언제 흔들릴까? 내 길 계획선 그래프 + 앞으로 올 일정 깃발 (앱 일정표 MarketEvents · 세금은 내년 5월)
    private struct Flag { let t: Double; let text: String; let warn: Bool; let route: AnalysisRoute }

    private func flags(_ p: AppModel.MyPath, until: Double) -> [Flag] {
        let days = Int(max(0, (until - p.start) * 365.25))
        let hits = MarketEvents.upcoming(held: m.rows.map(\.id), days: min(days, 400))
        let dday = { (d: String) -> String in let n = Day.dday(d); return n == 0 ? String(localized: "오늘") : "D-\(n)" }
        var out: [Flag] = []
        if let h = hits.first(where: { $0.id == "x_fomc" }) {
            out.append(Flag(t: p.start + Double(Day.dday(h.day)) / 365.25, text: dday(h.day) + " FOMC", warn: true, route: .external))
        }
        if let h = hits.first(where: { $0.id.hasPrefix("e_") && $0.held }) {
            out.append(Flag(t: p.start + Double(Day.dday(h.day)) / 365.25, text: dday(h.day) + " " + h.kind.replacingOccurrences(of: String(localized: " 실적"), with: ""), warn: false, route: .external))
        }
        if DividendView.hubTax(m) > 0 {
            let y = Calendar.current.component(.year, from: Date()) + 1
            let t = p.start + AppModel.yrs(Day.today, "\(y)-05-31")
            if t <= until { out.append(Flag(t: t, text: String(localized: "\(String(y)).5 세금"), warn: true, route: .tax)) }
        }
        return out.filter { $0.t <= until }.sorted { $0.t < $1.t }
    }

    private func shake(_ p: AppModel.MyPath, _ f: AppModel.Forecast, pro: Bool) -> some View {
        let goal = m.isGoal, mS = p.start, mEnd = p.end, need = p.need, K = p.key, now = m.trackValue
        // 그래프: 세로 눈금은 기간을 바꿔도 같게 (목표일까지 전체 값으로 정한다). 내 길 상세 그래프와 같은 식
        let x1 = toGoal ? mEnd : mS + 1.0 / 12
        let actPts = p.pts
        let needAll = (0...60).map { i -> (Double, Double) in let t = mEnd * Double(i) / 60; return (t, need(t)) }
        let needPts = (0...60).map { i -> (Double, Double) in let t = x1 * Double(i) / 60; return (t, need(t)) }
        let iE = f.idx(years: mEnd - mS)
        let bandAll: [(Double, Double, Double)] = !pro || f.tY.isEmpty ? []
            : (0...max(min(1, f.tY.count - 1), iE)).map { i in (mS + f.tY[i], f.q75[i] + m.cashKrw, f.q25[i] + m.cashKrw) }
        let bandPts = toGoal ? bandAll : []
        let vals = actPts.map(\.1) + needAll.map(\.1) + bandAll.flatMap { [$0.1, $0.2] } + [K]
        let hi = (vals.max() ?? 1) * 1.05, lo = (vals.filter { $0 > 0 }.min() ?? 0) * 0.9
        // 왼쪽 2% 비움: 시작점 동그라미가 그래프 왼쪽 끝에서 반쯤 잘리지 않게
        let X = { (t: Double) in 0.02 + 0.98 * t / max(1e-6, x1) }, Y = { (v: Double) in (hi - v) / max(1, hi - lo) }
        let fl = flags(p, until: x1)   // 지금까지 보기는 다음 달까지 일정만
        let fog = iE < f.q50.count ? f.q50[iE] + (f.exact ? m.cashKrw : 0) : 0
        let gc = Calendar.current.dateComponents([.year, .month], from: m.goalToDate)
        let chartH: CGFloat = 150, topH: CGFloat = 30
        return panel(String(localized: "언제 흔들릴까?")) {
            GeometryReader { g in
                let w = g.size.width
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, size in
                        if !bandPts.isEmpty {
                            var band = Self.line(bandPts.map { (X($0.0), Y($0.1)) }, in: size)
                            for q in bandPts.reversed() { band.addLine(to: CGPoint(x: X(q.0) * size.width, y: Y(q.2) * size.height)) }
                            band.closeSubpath()
                            ctx.fill(band, with: .color(Theme.teal.opacity(0.14)))
                        }
                        let gy = Y(K) * size.height
                        ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: gy)); $0.addLine(to: CGPoint(x: size.width, y: gy)) },
                                   with: .color(Theme.ink.opacity(0.6)), style: StrokeStyle(lineWidth: 1))
                        ctx.stroke(Self.line(needPts.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.purple), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                        ctx.stroke(Self.line(actPts.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.teal), style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
                        let tx = X(mS) * size.width, ty = Y(actPts.last?.1 ?? now) * size.height
                        ctx.fill(Path(ellipseIn: CGRect(x: tx - 4, y: ty - 4, width: 8, height: 8)), with: .color(Theme.teal))
                    }
                    .frame(width: w, height: chartH)
                    .pulse(Theme.teal, dot: 8) { s in CGPoint(x: X(mS) * s.width, y: Y(actPts.last?.1 ?? now) * s.height) }
                    .offset(y: topH)
                    // 깃발: 일정 날짜 자리에 세로 줄, 이름표는 겹치지 않게 한 줄씩 내려 단다
                    ForEach(fl.indices, id: \.self) { i in
                        let x = CGFloat(min(1, max(0, X(fl[i].t)))) * w, y = CGFloat(i) * 22
                        let c = fl[i].warn ? Color(hex: 0xA8521A) : Theme.muted
                        Rectangle().fill(c).frame(width: 1, height: max(0, topH + chartH - y - 18)).offset(x: x, y: y + 18)
                        Button { sheet = fl[i].route } label: {
                            Text(fl[i].text).appFont(11, .bold).foregroundStyle(fl[i].warn ? Color.white : Theme.ink).lineLimit(1).fixedSize()
                                .padding(.horizontal, 6).frame(height: 18)
                                .background(fl[i].warn ? Color(hex: 0xA8521A) : Theme.line, in: RoundedRectangle(cornerRadius: 6))
                        }
                        .buttonStyle(.plain)
                        .offset(x: min(max(0, x - 4), max(0, w - 110)), y: y)
                    }
                    // 오른쪽: 목표선 이름 + 안개(목표일 보통 값) → 전망 시트
                    Button { sheet = .forecast } label: {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text("\(m.keyName) \(goal ? AppModel.wonK(m.gK) : AppModel.eok(K))")
                            if pro && f.exact && fog > 0 { Text("안개: \(AppModel.eok(fog))쯤 ›") } else { Text("전망 ›") }
                        }
                        .appFont(11, .bold).foregroundStyle(Theme.teal).multilineTextAlignment(.trailing)
                        .padding(.horizontal, 4).background(Theme.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 6))
                    }
                    .buttonStyle(.plain)
                    .frame(width: w, alignment: .trailing)
                    .offset(y: max(0, topH + Y(K) * chartH - 34))
                }
            }
            .frame(height: topH + chartH)
            .accessibilityElement(children: .contain)
            HStack {
                Text(m.pathStartMonth); Spacer()
                Text(toGoal ? (goal ? "\(String(gc.year ?? 0)).\(gc.month ?? 0)" : String(localized: "1년 뒤")) : String(localized: "다음 달"))
            }
            .appFont(12).foregroundStyle(Theme.sub)
            HStack(spacing: 6) {
                HStack(spacing: 6) {
                    Text("─ 실제").foregroundStyle(Theme.teal)
                    Text("- - 계획").foregroundStyle(Theme.purple)
                    if toGoal && pro { Text("▒ 보통") }
                }
                .appFont(11).foregroundStyle(Theme.sub).lineLimit(1).fixedSize()
                Spacer(minLength: 4)
                smallPill(goal ? String(localized: "목표일까지") : String(localized: "1년 뒤까지"), on: toGoal) { toGoal = true }
                smallPill(String(localized: "지금까지"), on: !toGoal) { toGoal = false }
            }
        }
    }

    private func smallPill(_ t: String, on: Bool, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Text(t).appFont(12, .bold).foregroundStyle(on ? Color.white : Theme.ink).lineLimit(1).fixedSize()
                .padding(.horizontal, 9).frame(minHeight: 30)
                .background(on ? Theme.teal : Theme.card, in: Capsule())
                .overlay { if !on { Capsule().stroke(Theme.border) } }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    // 0~1 정규 좌표 선
    private static func line(_ pts: [(Double, Double)], in size: CGSize) -> Path {
        Path { p in
            for (i, q) in pts.enumerated() {
                let pt = CGPoint(x: q.0 * size.width, y: q.1 * size.height)
                i == 0 ? p.move(to: pt) : p.addLine(to: pt)
            }
        }
    }

    // MARK: 어떻게 올릴까? 구성마다 목표에 닿는 때: 금색 알약 이름 · 슬라이드(점 = 보통, 막대 = 10번 중 8번, 점선 = 목표일) · 연도
    private var raise: some View {
        let yrsE = m.etaYears
        let mixes = [(String(localized: "지금 구성"), m.nowMix), (String(localized: "성장 구성"), AppModel.mixes[1]), (String(localized: "방패 구성"), AppModel.mixes[2])]
        // 모의바구니: 평행 세계에 저장한 모의 바구니 (없으면 줄을 숨긴다)
        let mine = m.pworld?.segs.last?.w.filter { $0.value > 0 } ?? [:]
        let customSim: GoalSim? = mine.isEmpty ? nil : m.simulate(m.nowMix, years: yrsE, parts: mine.map { id, x in
            let usd = m.rows.first { $0.id == id }.map { $0.sym.currency == .usd } ?? ExtraStats.shared.usd(id) ?? true
            return (key: SiteStats.mcKey(id), ccy: usd ? "USD" : "KRW", w: x)
        })
        let customDesc = mine.sorted { $0.value > $1.value }.prefix(3).map { id, x in
            let name = m.rows.first { $0.id == id }?.sym.name ?? Parallel.universe.first { $0.id == id }?.name ?? ExtraStats.shared.name(id) ?? id
            return "\(name) \(Int((x * 100).rounded()))"
        }.joined(separator: " · ") + (mine.count > 3 ? " …" : "")
        let sims = mixes.map { m.simulate($0.1, years: yrsE) } + (customSim.map { [$0] } ?? [])
        let goalMo = max(0, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12)
        let ax = EtaRow.axis(sims, goalYear: AppModel.yearAt(goalMo)), a0 = ax.0, a1 = ax.1
        let x = { (y: Double) in min(1, max(0, (y - a0) / max(0.5, a1 - a0))) }
        let step = a1 - a0 > 6 ? 2 : 1
        let ys = Array(stride(from: Int(a0), through: Int(a1), by: step))
        return panel(String(localized: "어떻게 올릴까?")) {
            HStack(spacing: 8) {
                Color.clear.frame(width: 84, height: 1)
                GeometryReader { g in
                    ForEach(ys, id: \.self) { y in
                        Text(String(format: "'%02d", y % 100)).appFont(11).foregroundStyle(Theme.sub).fixedSize()
                            .position(x: min(g.size.width - 10, max(10, x(Double(y)) * g.size.width)), y: 7)
                    }
                }
                .frame(height: 14)
                Color.clear.frame(width: 52, height: 1)
            }
            .accessibilityHidden(true)
            ForEach(mixes.indices, id: \.self) { i in
                etaLine(mixes[i].0, mixes[i].1.desc, sims[i], x: x, gx: x(AppModel.yearAt(goalMo)), mine: i == 0)
            }
            if let customSim {
                etaLine(String(localized: "모의바구니"), customDesc, customSim, x: x, gx: x(AppModel.yearAt(goalMo)), mine: false)
            }
            HStack(spacing: 8) {
                Text("● 보통 · ─ 10번 중 8번 · ┊ 목표일").appFont(12).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 4)
                if !m.rows.isEmpty {   // 처음 루트도 종목을 담았으면 보인다
                    Button { comparing = true } label: {
                        Text("구성 변경").appFont(12, .bold).foregroundStyle(.white).lineLimit(1).fixedSize()
                            .padding(.horizontal, 12).frame(minHeight: 30).background(Theme.teal, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .sheet(isPresented: $comparing) { MyPathCompareView() }
                }
            }
        }
    }

    private func etaLine(_ name: String, _ desc: String, _ s: GoalSim, x: (Double) -> Double, gx: Double, mine: Bool) -> some View {
        let lo = x(AppModel.yearAt(s.etaLo)), hi = s.etaHi.isFinite ? x(AppModel.yearAt(s.etaHi)) : 1
        let mid = s.etaMonths.isFinite ? x(AppModel.yearAt(s.etaMonths)) : 1
        let range = mine ? Color(hex: 0xA9D6CB, dark: 0x2F6B62) : Color(hex: 0xA9B0BA, dark: 0x4A5260)
        let dot = mine ? Theme.teal : Color(hex: 0x4A5260, dark: 0xA9B0BA)
        let when = AppModel.whenText(s.etaMonths, cap: s.capYears)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Text(name).appFont(12, .bold).foregroundStyle(Theme.inkFixed).lineLimit(1).fixedSize()
                    .padding(.horizontal, 9).padding(.vertical, 3).background(Theme.yellow, in: Capsule())
                    .frame(width: 84, alignment: .leading)
                GeometryReader { g in
                    let w = g.size.width
                    ZStack(alignment: .topLeading) {
                        Rectangle().fill(Theme.dash).frame(width: w, height: 2).offset(y: 6)
                        Path { $0.move(to: CGPoint(x: gx * w, y: -2)); $0.addLine(to: CGPoint(x: gx * w, y: 16)) }
                            .stroke(Theme.sub, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
                        Capsule().fill(range).frame(width: max(6, (hi - lo) * w), height: 4).offset(x: lo * w, y: 5)
                        Circle().fill(dot).frame(width: 14, height: 14).offset(x: mid * w - 7)
                    }
                    .animation(.easeInOut(duration: 0.25), value: s.etaMonths)
                }
                .frame(height: 14)
                Text(when).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.6).frame(width: 52, alignment: .trailing)
            }
            Text(desc).appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(name), 보통 \(when), 10번 중 8번은 \(AppModel.whenText(s.etaLo, cap: s.capYears))에서 \(AppModel.whenText(s.etaHi, cap: s.capYears)) 사이")
    }
}

// MARK: 확률 박스 (상단 박스 네 테마 · 2026-10-23 확정). 글자·배치는 같고, 바탕과 막대 모양만 홈에서 고른 박스 모양을 따른다
// 막대: 노란 채움 = 지금, 흰 세로줄 = 목표(결승선), 세 점 = 목표일의 나쁠 때·보통·좋을 때
struct ChanceBox: View {
    struct Data {
        var head: String
        var verdict: String?        // 낮음 · 반반 · 높음 (nil = 계산 중·잠김)
        var pct: String
        var status: String          // verdict 가 없을 때 큰 글
        var now: Double             // 0~1
        var goal: Double?
        var dots: [Double]          // 나쁠 때 · 보통 · 좋을 때 (0~1)
        var goalLabel: String
        var foot: String
        var nowText: String
    }
    let look: BoxLook
    let d: Data
    private static let dim = BoxInk.sub
    private static let dotC = [bx(0x7D8794, 0x8A96A3), bx(0xB5BDC7, 0xC9D0D6), bx(0x2E9C8C, 0x3FB3A3)]
    private static let labC = [bx(0x5F6670, 0x8A96A3), bx(0x6B7480, 0xC9D0D6), BoxInk.good]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(d.head).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 8)
                FreshLabel(color: Self.dim)
            }
            .font(.system(size: 14)).foregroundStyle(Self.dim)
            title.padding(.top, 6)
            bar.frame(height: 62).padding(.top, 10)
            // 맨 아래 '셋 중 … 선을 넘음 · 지금 …' 줄은 뺀다. 박스 높이를 종목 탭 박스에 맞춘다 (2026-10-27)
        }
        .padding(.horizontal, 18).padding(.top, 14).padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background { MoodBackdrop(look: look) }
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(d.head), \(d.verdict.map { String(localized: "가능성 \(String(describing: $0)) \(String(describing: d.pct))") } ?? d.status), \(d.foot), \(d.nowText)")
    }

    private var title: some View {
        Group {
            if let v = d.verdict {
                Text(String(localized: "가능성 \(String(describing: v)) · ")) + Text(d.pct).foregroundColor(BoxInk.gold)
            } else {
                Text(d.status)
            }
        }
        .font(.system(size: 30, weight: .heavy)).tracking(-0.5).foregroundStyle(BoxInk.ink)   // 흐름 탭 큰 흐름 박스와 같은 크기
        .lineLimit(1).minimumScaleFactor(0.6)
    }

    @Environment(\.colorScheme) private var cs
    private var dk: Bool { cs == .dark }

    private var bar: some View {
        GeometryReader { g in
            let w = g.size.width
            let X = { (v: Double) -> CGFloat in CGFloat(min(1, max(0, v))) * w }
            let xs = d.dots.map(X), lx = Self.spread(xs, gap: 38, w: w)
            let names = [String(localized: "나쁠 때"), String(localized: "보통"), String(localized: "좋을 때")]
            ZStack(alignment: .topLeading) {
                ProgressTrack(look: look, fill: d.now).frame(width: w, height: 14).offset(y: 30)
                if let gx = d.goal {
                    // 목표선 (점프 직전 라이트는 어두운 도로 위라 흰 선 + 옅은 그림자)
                    Rectangle().fill(look == .jump ? bx(0xFFFFFF, 0xFFFFFF) : BoxInk.ink).frame(width: 2, height: 44)
                        .shadow(color: look == .jump ? bx(0x16181D, 0.45, 0x000000, 0) : .clear, radius: 0.5)
                        .offset(x: X(gx) - 1, y: 4)
                    Text(d.goalLabel).font(.system(size: 10, weight: dk ? .regular : .bold)).foregroundStyle(BoxInk.ink).fixedSize()
                        .position(x: min(w - 16, max(16, X(gx))), y: 56)
                    // 목표선 옆에 서는 것 (오페라하우스·출구·일렁임·해·거품)
                    TrackBuddy(look: look, goal: true)
                        .frame(width: 60, height: 40, alignment: .bottomTrailing)
                        .position(x: X(gx) - 3 - 30, y: 44 - 20)
                }
                ForEach(xs.indices, id: \.self) { i in
                    let r: CGFloat = i == 2 ? 8 : 7
                    Circle().fill(Self.dotC[i]).frame(width: r * 2, height: r * 2)
                        .overlay { if i == 1 && !dk { Circle().stroke(.white, lineWidth: 1.5) } }
                        .position(x: xs[i], y: 19)
                    Text(names[i]).font(.system(size: 10)).foregroundStyle(Self.labC[i]).fixedSize().position(x: lx[i], y: 0)
                }
                // 지금 자리에 서는 작은 인물
                TrackBuddy(look: look)
                    .frame(width: 60, height: 40, alignment: .bottom)
                    .position(x: min(w - 14, max(14, X(d.now))), y: 30 - 20)
            }
        }
    }

    /// 이름표 자리: 점 바로 위, 서로 gap 보다 가까우면 밀어 낸다 (박스 밖으로 나가지 않게)
    static func spread(_ xs: [CGFloat], gap: CGFloat, w: CGFloat) -> [CGFloat] {
        guard !xs.isEmpty else { return [] }
        var l = xs.map { min(w - gap / 2, max(gap / 2, $0)) }
        for i in 1..<l.count where l[i] - l[i - 1] < gap { l[i] = l[i - 1] + gap }
        if l[l.count - 1] > w - gap / 2 {
            l[l.count - 1] = w - gap / 2
            for i in stride(from: l.count - 2, through: 0, by: -1) where l[i + 1] - l[i] < gap { l[i] = l[i + 1] - gap }
        }
        return l
    }
}

/// 확률 박스 막대: 홈에서 고른 박스 모양 (몫 띠 MoodBand 와 같은 결). fill = 지금 자리 0~1, 채운 만큼 노랑
struct ProgressTrack: View {
    let look: BoxLook
    let fill: Double
    private static let on = Theme.yellow
    @Environment(\.self) private var env
    private var f: CGFloat { CGFloat(min(1, max(0, fill))) }

    var body: some View {
        switch look {
        case .jump: road
        case .horizon: wave
        case .tub:
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    bx(0xCFDDF0, 0x3C5F9C)
                    Self.on.frame(width: g.size.width * f)
                }
            }
            .frame(height: 8).clipShape(RoundedRectangle(cornerRadius: 4))
            .padding(3).background(bx(0xFFFFFF, 0xEEF2F8), in: RoundedRectangle(cornerRadius: 7))
            .overlay(RoundedRectangle(cornerRadius: 7).strokeBorder(bx(0xB7CAE3, 1, 0xB7CAE3, 0), lineWidth: 1))
        case .dory, .pino, .mug:
            ThemeTrack(look: look, f: f, on: Self.on)
        case .bar:
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(bx(0xE6E9EE, 0x2E3B48))
                    Capsule().fill(Self.on).frame(width: g.size.width * f)
                }
            }
            .frame(height: 6)
        }
    }

    /// 점프 직전: 박스 폭 끝까지 이어진 밤 도로, 가운데 점선. 지금까지 온 칸은 노랗게 빛난다
    private var road: some View {
        let bg = bx(0x2F3650, 0x151925).fixed(env), top = bx(0x2F3650, 0x232A3E).fixed(env), lane = bx(0x5A6585, 0x3A4466).fixed(env)
        return Canvas { ctx, size in
            let pad: CGFloat = 18, w = size.width - pad * 2, cut = pad + w * f
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(bg))
            ctx.fill(Path(CGRect(x: 0, y: 0, width: size.width, height: 1)), with: .color(top))
            let dash: CGFloat = 14, gap: CGFloat = 7, h: CGFloat = 3, y = size.height / 2 - h / 2
            var x: CGFloat = 0
            while x < w {
                let a = pad + x, b = pad + min(w, x + dash)
                if cut > a {
                    var l = ctx
                    l.addFilter(.shadow(color: Self.on, radius: 2))
                    l.fill(Path(CGRect(x: a, y: y, width: min(b, cut) - a, height: h)), with: .color(Self.on))
                }
                if cut < b { ctx.fill(Path(CGRect(x: max(a, cut), y: y, width: b - max(a, cut), height: h)), with: .color(lane)) }
                x += dash + gap
            }
        }
        .padding(.horizontal, -18)
    }

    /// 지평선: 물결 한 줄, 지금까지는 노랑
    private var wave: some View {
        let base = bx(0xC9D0DA, 0x3A4656).fixed(env)
        return Canvas { ctx, size in
            var p = Path(), x: CGFloat = 0, up = true
            let mid = size.height / 2
            p.move(to: CGPoint(x: 0, y: mid))
            while x < size.width {
                p.addQuadCurve(to: CGPoint(x: x + 12, y: mid), control: CGPoint(x: x + 6, y: up ? mid - 6 : mid + 6))
                x += 12; up.toggle()
            }
            ctx.stroke(p, with: .color(base), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            var l = ctx
            l.clip(to: Path(CGRect(x: -4, y: -4, width: size.width * f + 4, height: size.height + 8)))
            l.addFilter(.shadow(color: Self.on, radius: 3))
            l.stroke(p, with: .color(Self.on), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
    }
}

/// 분석 허브 행: 팝업 안에서는 기본 이동 링크 그대로, 탭 화면에서는 분석 팝업을 연다
/// (예전엔 옆으로 밀기 막는 끌기 제스처를 겹쳐 탭이 씹혔다)
struct OpenOnTap: ViewModifier {
    let route: AnalysisRoute
    @Environment(\.inPopup) private var inPopup
    @Environment(AppModel.self) private var m
    func body(content: Content) -> some View {
        if inPopup { NavigationLink(value: route) { content }.buttonStyle(.plain) }
        else { Button { m.analysisPath = [route] } label: { content }.buttonStyle(.plain) }
    }
}

/// iOS 26 은 화면 아무 데서나 오른쪽으로 밀어도 뒤로 간다. 넘기기 화면에서 그게 켜져 있으면
/// 첫 분석에서 오른쪽으로 밀 때 분석 목록으로 나가 헷갈린다: 넘기기 화면이 보일 때만 끈다 (가장자리 밀기·뒤로 버튼은 그대로)
private struct ContentPopBlock: UIViewRepresentable {
    final class V: UIView {
        weak var nav: UINavigationController?
        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window == nil { set(nav, true); return }
            var r: UIResponder? = self
            while let n = r {
                if let vc = n as? UIViewController, let nc = vc.navigationController { nav = nc; break }
                r = n.next
            }
            set(nav, false)
        }
        private func set(_ nc: UINavigationController?, _ on: Bool) {
            if #available(iOS 26.0, *) { nc?.interactiveContentPopGestureRecognizer?.isEnabled = on }
        }
    }
    func makeUIView(context: Context) -> V { let v = V(); v.isUserInteractionEnabled = false; return v }
    func updateUIView(_ v: V, context: Context) {}
}

// 목표일 전망: 시장이 어떻게 가느냐에 따라 목표일 자산이 얼마나 갈리나. 목표 판정은 내 목표(내 길) 화면 몫
struct ForecastView: View {
    @Environment(AppModel.self) private var m
    @State private var open = false

    var body: some View {
        @Bindable var m = m
        let fs = Dictionary(uniqueKeysWithValues: Lens.allCases.map { ($0, m.forecast($0)) })
        let sel = m.lens, f = fs[sel] ?? m.forecast
        // 몬테카를로 경로는 종목만이라 현금을 더해 지금 평가액과 같은 기준으로 (닫힌 식은 이미 포함)
        let add = { (x: AppModel.Forecast) -> Double in x.exact ? m.cashKrw : 0 }
        let now = (f.q50.first ?? 0) + add(f)
        let med = (f.q50.last ?? 0) + add(f), lo = (f.q5.last ?? 0) + add(f), hi = (f.q95.last ?? 0) + add(f)
        let chg = now > 0 ? med / now - 1 : 0
        let end = Self.ym(m.forecastHorizon)
        let others: [(label: String, med: [Double])] = Self.others(fs, sel: sel, add: add)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                DetailHead(title: m.forecastTitle, sub: f.exact ? "" : String(localized: "계산 중 (어림값)"))
                Text(m.isGoal ? String(localized: "\(String(describing: end.short)) 내 자산은?") : String(localized: "3년 뒤 내 자산은?"))
                    .appFont(20, .bold).foregroundStyle(Theme.ink).fixedSize(horizontal: false, vertical: true)

                Card {
                    segments
                    trustSlider
                    VStack(alignment: .leading, spacing: 4) {
                        Text("그럴듯한 금액 · 연 \(AppModel.sgn0(f.mu))").appFont(13).foregroundStyle(Theme.sub)
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(Self.amt(med)).font(.system(size: 28, weight: .bold)).foregroundStyle(Theme.ink)
                                .lineLimit(1).minimumScaleFactor(0.7).roll(Self.amt(med))
                            Text(AppModel.sgn0(chg)).appFont(13, .bold).foregroundStyle(chg >= 0 ? Theme.green : Theme.up)
                        }
                        Text("10번 중 9번 \(Self.amt(lo)) ~ \(Self.amt(hi))").appFont(13).foregroundStyle(Theme.sub)
                    }
                    ScenarioChart(sel: f, selShift: add(f), selLabel: Self.short(sel), others: others,
                                  goal: m.keyValue, goalLabel: m.keyName, endLabel: m.isGoal ? end.short : String(localized: "3년 뒤"))
                        .frame(height: 190)
                    legend
                    if f.mu > 0.30 {
                        Text("연 \(AppModel.sgn0(f.mu))는 드문 속도 · 실제론 더 낮을 수도")
                            .appFont(12).foregroundStyle(Theme.ink).padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.cream, in: RoundedRectangle(cornerRadius: 10))
                    }
                }

                table(fs, now: now, add: add)
                schedule
                details(f)

                Text("지난 3년 기준 3,000가지 경우")
                    .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                NavigationLink { GoalSetView(fromSettings: true) } label: {
                    Text("구성 바꾸기 → 목표 고치기").appFont(14, .bold).foregroundStyle(Theme.teal)
                }
            }
            .padding(16)
            .frame(maxWidth: Theme.maxWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }

    // MARK: 시나리오 버튼 3개 + 추세 슬라이더 (같은 축: 신중하게 = 0%, 지난 3년처럼 = 100%)
    private var segments: some View {
        HStack(spacing: 4) {
            ForEach(Lens.allCases, id: \.self) { l in
                let on = m.lens == l
                Button { m.lens = l } label: {
                    Text(l.label).appFont(13, on ? .bold : .regular).foregroundStyle(on ? Color.white : Theme.sub)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background(on ? Theme.teal : Color.clear, in: RoundedRectangle(cornerRadius: 9))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
    }

    /// 슬라이더에 보이는 값. 버튼을 골라도 '내 설정' 값(m.trust)은 그대로 기억한다
    private var shownTrust: Double { m.lens == .base ? 0 : m.lens == .smooth ? 100 : m.trust }

    private var trustSlider: some View {
        let b = Binding<Double>(get: { shownTrust }, set: { v in
            if v <= 0 { m.lens = .base } else if v >= 100 { m.lens = .smooth } else { m.lens = .mine; m.trust = v }
        })
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("지난 3년 추세 반영").appFont(13, .bold)
                Spacer()
                Text("\(Int(shownTrust))%").appFont(13, .bold).foregroundStyle(Theme.teal)
            }
            Slider(value: b, in: 0...100, step: 5).tint(Theme.teal).accessibilityLabel("지난 3년 추세를 얼마나 믿나요")
            HStack { Text("신중"); Spacer(); Text("반반"); Spacer(); Text("3년처럼") }
                .appFont(11).foregroundStyle(Theme.muted)
        }
    }

    private var legend: some View {
        HStack(spacing: 12) {
            sw(Theme.teal.opacity(0.35), String(localized: "10번 중 5번"))
            sw(Theme.teal.opacity(0.14), String(localized: "10번 중 9번"))
            HStack(spacing: 4) {
                Path { $0.move(to: .init(x: 0, y: 5)); $0.addLine(to: .init(x: 14, y: 5)) }
                    .stroke(Theme.muted, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3])).frame(width: 14, height: 10)
                Text("다른 시나리오")
            }
        }
        .appFont(11).foregroundStyle(Theme.sub)
    }
    private func sw(_ c: Color, _ t: String) -> some View {
        HStack(spacing: 4) { RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 14, height: 10); Text(t) }
    }

    // MARK: 세 가지 미래 한눈에
    private func table(_ fs: [Lens: AppModel.Forecast], now: Double, add: @escaping (AppModel.Forecast) -> Double) -> some View {
        let lineName = m.isGoal ? Self.amt(m.keyValue) : String(localized: "본전")
        let bads = Lens.allCases.map { l in fs[l].map { ($0.q5.last ?? 0) + add($0) } ?? now }
        let below = bads.filter { $0 < now }.count
        let msg = below == bads.count ? String(localized: "운 나쁘면 지금(\(String(describing: Self.amt(now))))보다 줄 수도")
            : below > 0 ? String(localized: "신중하게 보면 지금(\(String(describing: Self.amt(now))))보다 줄 수도")
            : String(localized: "운 나빠도 지금(\(String(describing: Self.amt(now))))보다 많을 가능성 큼")
        return Card {
            Text("세 가지 미래").appFont(15, .bold)
            HStack {
                Text("").frame(maxWidth: .infinity, alignment: .leading)
                Text("보통").frame(width: 70, alignment: .trailing)
                Text("나쁘면").frame(width: 58, alignment: .trailing)
                Text("\(lineName)↑").frame(width: 78, alignment: .trailing)
            }
            .appFont(11).foregroundStyle(Theme.muted)
            ForEach(Lens.allCases, id: \.self) { l in
                if let x = fs[l] {
                    let on = m.lens == l
                    Button { m.lens = l } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(Self.short(l)).appFont(13, on ? .bold : .regular)
                                Text(AppModel.sgn0(x.mu)).appFont(11).foregroundStyle(Theme.muted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            Text(Self.amt((x.q50.last ?? 0) + add(x))).frame(width: 70, alignment: .trailing)
                            Text(Self.amt((x.q5.last ?? 0) + add(x))).frame(width: 58, alignment: .trailing)
                            Text(AppModel.pct(x.pGoal)).frame(width: 78, alignment: .trailing)
                        }
                        .appFont(13, on ? .bold : .regular).foregroundStyle(Theme.ink)
                        .padding(.vertical, 8).padding(.horizontal, 8)
                        .background(on ? Theme.tealBg : Color.clear, in: RoundedRectangle(cornerRadius: 10))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            Text(msg).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: 전망을 흔들 수 있는 일정 (외부 요인 토글 자리)
    private var schedule: some View {
        @Bindable var m = m
        let hits = Array(MarketEvents.upcoming(held: m.rows.map(\.id), days: 60).prefix(4))
        return Card {
            Toggle(isOn: $m.shock) { Text("흔들 일정").appFont(15, .bold) }.tint(Theme.teal)
            Text("켜면 계산에 반영").appFont(12).foregroundStyle(Theme.sub)
            ForEach(hits.indices, id: \.self) { i in ScheduleRow(hit: hits[i]) }
        }
    }

    // MARK: 자세히 보기 (기본 접힘)
    private func details(_ f: AppModel.Forecast) -> some View {
        let what = m.isGoal ? Self.amt(m.keyValue) : String(localized: "본전")
        return VStack(alignment: .leading, spacing: 10) {
            Button { withAnimation { open.toggle() } } label: {
                HStack {
                    Text("자세히").appFont(15, .bold)
                    Spacer()
                    Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 12)).foregroundStyle(Theme.muted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open {
                Card(dark: true) {
                    Text("한 번이라도 \(what) 넘을 확률").appFont(14, .bold)
                    Text("잠깐 넘는 경우 포함").appFont(12).foregroundStyle(.secondary)
                    ForEach(0..<f.byYear.count, id: \.self) { i in
                        let y = f.byYear[i].year, p = f.byYear[i].p
                        HStack(spacing: 10) {
                            Text("\(y)년").appFont(13).frame(width: 46, alignment: .leading)
                            ProgressBar(value: p, height: 10, fill: Theme.teal, track: Theme.track)
                            Text(AppModel.pct(p)).appFont(13, .bold).frame(width: 40, alignment: .trailing)
                        }
                    }
                }
                Text("직접 옮긴 슬라이더 값은 '내 설정'에 저장")
                    .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay { RoundedRectangle(cornerRadius: 18).stroke(Theme.border, lineWidth: 1) }
    }

    /// 비교선: 고르지 않은 두 시나리오의 중앙값 (현금 더함)
    static func others(_ fs: [Lens: AppModel.Forecast], sel: Lens, add: (AppModel.Forecast) -> Double) -> [(label: String, med: [Double])] {
        var out: [(label: String, med: [Double])] = []
        for l in Lens.allCases where l != sel {
            if let x = fs[l] { let c = add(x); out.append((short(l), x.q50.map { $0 + c })) }
        }
        return out
    }

    // MARK: 표시
    /// 3.30억 · 11.5억 · 826만 (목업 표기)
    static func amt(_ v: Double) -> String {
        let a = abs(v), s = v < 0 ? "−" : ""
        if a >= 1e9 { return s + String(format: String(localized: "%.1f억"), a / 1e8) }
        if a >= 1e8 { return s + String(format: String(localized: "%.2f억"), a / 1e8) }
        return s + Int((a / 1e4).rounded()).formatted() + String(localized: "만")
    }
    static func short(_ l: Lens) -> String { l == .base ? String(localized: "신중") : l == .smooth ? String(localized: "3년처럼") : String(localized: "내 설정") }
    /// "2029-10-14" → ("2029년 10월", "2029.10")
    static func ym(_ d: String) -> (long: String, short: String) {
        let p = d.split(separator: "-")
        guard p.count >= 2, let y = Int(p[0]), let mo = Int(p[1]) else { return (d, d) }
        return (String(localized: "\(String(describing: y))년 \(String(describing: mo))월"), String(format: "%d.%02d", y, mo))
    }
}
