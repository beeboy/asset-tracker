import SwiftUI

enum AnalysisRoute: Hashable { case forecast, myPath, rateScen, external, dividend, fx, glance, calm }

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

struct AnalysisPager: View {
    @Environment(AppModel.self) private var m
    @State private var sel: AnalysisRoute
    static let order: [AnalysisRoute] = [.calm, .myPath, .forecast, .rateScen, .external, .dividend, .fx, .glance]

    init(start: AnalysisRoute) { _sel = State(initialValue: start) }

    private func title(_ r: AnalysisRoute) -> String {
        switch r {
        case .calm: return "평정 지수"
        case .myPath: return "내 길"
        case .forecast: return m.forecastTitle
        case .rateScen: return "금리 충격 테스트"
        case .external: return "외부 요인"
        case .dividend: return "배당·세금"
        case .fx: return "환율 영향"
        case .glance: return "종목 한눈에"
        }
    }

    @ViewBuilder private func page(_ r: AnalysisRoute) -> some View {
        switch r {
        case .forecast: ForecastView()
        case .myPath: MyPathView()
        case .rateScen: RateScenarioView()
        case .external: ExternalView()
        case .dividend: DividendView()
        case .fx: FxImpactView()
        case .glance: GlanceView()
        case .calm: CalmView()
        }
    }

    var body: some View {
        TabView(selection: $sel) {
            ForEach(Self.order, id: \.self) { r in page(r).tag(r) }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(ContentPopBlock())
        .environment(\.detailInPager, true)
        .background(Theme.bg.ignoresSafeArea())
        .navigationTitle(title(sel))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 제목 밑 점: 지금 몇 번째 분석인지, 좌우로 넘길 수 있다는 표시
            ToolbarItem(placement: .principal) {
                VStack(spacing: 3) {
                    Text(title(sel)).appFont(16, .bold).lineLimit(1)
                    if sel != .external {      // 외부 요인은 점 표시 없이 (개편안)
                        HStack(spacing: 4) {
                            ForEach(Self.order, id: \.self) { r in
                                Circle().fill(r == sel ? Theme.teal : Theme.border).frame(width: 5, height: 5)
                            }
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(title(sel)), \((Self.order.firstIndex(of: sel) ?? 0) + 1) / \(Self.order.count), 좌우로 넘기면 다른 분석")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: sel)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: .y1) }
    }
}

// 분석 탭: naeilo.com 분석·전략을 폰에 맞게. 카드 머리 숫자는 각 화면 첫 숫자와 같은 계산.
struct AnalysisView: View {
    @Environment(AppModel.self) private var m

    /// 금리 시나리오 카드 머리: 지난 1년 금리 변화가 이어질 때 S&P 실질 이익 3년 누적 중앙값
    private var rateTag: String {
        let md = MacroData.shared
        guard let r = md.scen(dr: md.defaultDr, m: 0) else { return md.failed ? "자료 없음" : "…" }
        guard let c = r.cum.last else { return "…" }
        return "이익 \(r.years)년 " + AppModel.sgn0(exp(c[1] / 100) - 1)
    }

    var body: some View {
        let fc = m.forecast
        let usd = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.value } / max(1, m.total)
        // 내 길: 실제로 시작한 날부터 본전까지 1년에 걸친 선과 오늘 비교
        let gap = m.myPathGapToday
        // 외부 요인·배당은 상세 화면과 같은 값 (웹과 같은 사이트 자료·식)
        let mkt10 = m.rows.reduce(0.0) { a, r in
            a + r.value * (ExternalView.mktBeta(r.id).map { SiteCalc.factorMove("mkt", beta: $0, shock: -10) } ?? 0)
        } / max(1, m.total)
        let fxNow = Market.shared.valFx, today = SiteCalc.today()   // 배당은 웹 fxBase 와 같게 매매기준율
        let div = m.rows.reduce(0.0) { a, r in
            let krw = r.sym.currency == .krw
            let o = SiteCalc.shared.div(r.id).flatMap { SiteCalc.project($0.events, shares: r.h.qty, fx: krw ? 1 : fxNow, krw: krw, price: r.sym.last, today: today) }
            return a + (o?.y1 ?? 0)
        }
        let cards: [(String, String, String, Color, AnalysisRoute)] = [
            m.isGoal
                ? ("내 길", "목표에 언제 닿을까", fc.exact ? "가능성 " + AppModel.verdict(fc.pGoal) : "계산 중", Theme.teal, AnalysisRoute.myPath)
                : ("내 길", "정한 목표대로 가고 있나", (gap >= 0 ? "앞섬 " : "뒤처짐 ") + String(format: "%.1f%%", abs(gap) * 100),
                   gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060), AnalysisRoute.myPath),
            (m.forecastTitle, "시장이 줄 수 있는 미래의 범위", fc.exact ? AppModel.pct(fc.pGoal) : "계산 중", Theme.teal, .forecast),
            ("금리 충격 테스트", "금리가 움직이면 내 목표는 얼마나 흔들릴까", rateTag, Theme.purple, .rateScen),
            ("외부 요인", "시장·금리·환율이 움직이면 내 자산은", "시장 −10%: " + AppModel.sgn(mkt10), Theme.down, .external),
            ("배당·세금", "앞으로 1년 배당, 내년 5월 양도세", "연 " + AppModel.won1(div), Theme.teal, .dividend),
            ("환율 영향", "환율이 바뀌면 내 평가액은", "달러 " + AppModel.pct(usd), Theme.blue, .fx),
            ("종목 한눈에", "고점 대비, 비중, 흔들림을 숫자로", "\(m.rows.count)종목", Theme.teal, .glance),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                AppHeader(tab: "분석").padding(.horizontal, -16)
                SampleNote()
                CalmCard()
                ForEach(cards, id: \.0) { t, sub, tag, color, route in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(t).appFont(16, .bold)
                            Text(sub).appFont(13).foregroundStyle(Theme.sub)
                        }
                        Spacer()
                        Text(tag).appFont(15, .bold).foregroundStyle(color)
                    }
                    .padding(.horizontal, 16).frame(minHeight: 68)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
                    .modifier(OpenOnTap(route: route))
                }
                Text("계산 옵션과 상세 표는 PC naeilo.com에서 크게 봐요.").appFont(13).foregroundStyle(Theme.sub).padding(.top, 4)
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .task { await MacroData.shared.load() }
        .task { await SiteCalc.shared.refresh() }
        .navigationDestination(for: AnalysisRoute.self) { AnalysisPager(start: $0) }
    }
}

/// 분석 목록 카드: 누르면 살짝 눌리고 탭하면 연다. 카드가 화면 너비라 옆으로 밀고 카드 안에서 손을 떼도
/// 버튼은 탭으로 쳐서 세부 분석이 열렸다: 옆으로 움직였으면 열지 않는다 (좌우 넘기기는 세부 분석 안에서만)
struct OpenOnTap: ViewModifier {
    @Environment(AppModel.self) private var m
    @State private var movedAt = Date.distantPast
    let route: AnalysisRoute
    func body(content: Content) -> some View {
        Button {
            if Date().timeIntervalSince(movedAt) < 0.3 { return }
            m.analysisPath.append(route)
        } label: { content }
        .buttonStyle(.plain)
        .simultaneousGesture(DragGesture(minimumDistance: 12)
            // 손을 떼기 직전까지 움직였으면 탭이 아님 (스크롤에 밀려 끝이 안 와도 시간이 지나면 풀린다)
            .onChanged { _ in movedAt = Date() })
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
                DetailHead(title: m.forecastTitle, sub: f.exact ? "" : "계산 중이에요 (어림값)")
                Text("시장이 어떻게 움직이느냐에 따라\n\(m.isGoal ? "\(end.long) 내 자산은" : "3년 뒤 내 자산은") 이만큼 달라져요")
                    .appFont(20, .bold).foregroundStyle(Theme.ink).fixedSize(horizontal: false, vertical: true)

                Card {
                    segments
                    trustSlider
                    Text(note(sel, f)).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("가장 그럴듯한 금액").appFont(13).foregroundStyle(Theme.sub)
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(Self.amt(med) + " 원").font(.system(size: 28, weight: .bold)).foregroundStyle(Theme.ink)
                                .lineLimit(1).minimumScaleFactor(0.7)
                            Text("지금보다 \(AppModel.sgn0(chg))").appFont(13, .bold).foregroundStyle(chg >= 0 ? Theme.green : Theme.up)
                        }
                        Text("10번 중 9번은 \(Self.amt(lo)) ~ \(Self.amt(hi)) 사이").appFont(13).foregroundStyle(Theme.sub)
                    }
                    ScenarioChart(sel: f, selShift: add(f), selLabel: Self.short(sel), others: others,
                                  goal: m.keyValue, goalLabel: m.keyName, endLabel: m.isGoal ? end.short : "3년 뒤")
                        .frame(height: 190)
                    legend
                    if f.mu > 0.30 {
                        Text("연 \(AppModel.sgn0(f.mu))는 흔치 않은 속도예요. 지난 3년이 유난히 좋았다면 실제로는 이보다 낮을 수 있어요.")
                            .appFont(12).foregroundStyle(Theme.ink).padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.cream, in: RoundedRectangle(cornerRadius: 10))
                    }
                }

                table(fs, now: now, add: add)
                schedule
                details(f)

                Text("예측이 아니라 가능한 범위예요. 지난 3년 움직임을 바탕으로 3,000가지 경우를 계산했어요.")
                    .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                NavigationLink(value: AnalysisRoute.myPath) {
                    Text("납입이나 종목 구성을 바꿔 보려면 내 목표로 →").appFont(14, .bold).foregroundStyle(Theme.teal)
                }
            }
            .padding(16)
            .frame(maxWidth: Theme.maxWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
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
                Text("지난 3년 추세를 얼마나 믿나요").appFont(13, .bold)
                Spacer()
                Text("\(Int(shownTrust))%").appFont(13, .bold).foregroundStyle(Theme.teal)
            }
            Slider(value: b, in: 0...100, step: 5).tint(Theme.teal).accessibilityLabel("지난 3년 추세를 얼마나 믿나요")
            HStack { Text("0% 신중하게"); Spacer(); Text("50% 반반"); Spacer(); Text("100% 지난 3년처럼") }
                .appFont(11).foregroundStyle(Theme.muted)
        }
    }

    private func note(_ l: Lens, _ f: AppModel.Forecast) -> String {
        switch l {
        case .base: return "높았던 수익률이 장기 평균으로 돌아온다고 볼 때 · 연 \(AppModel.sgn0(f.mu))"
        case .mine: return "지난 3년 추세를 \(Int(m.trust))% 믿는다고 볼 때 · 연 \(AppModel.sgn0(f.mu))"
        case .smooth: return "지난 3년 속도가 그대로 이어진다고 볼 때 · 연 \(AppModel.sgn0(f.mu))"
        }
    }

    private var legend: some View {
        HStack(spacing: 12) {
            sw(Theme.teal.opacity(0.35), "10번 중 5번")
            sw(Theme.teal.opacity(0.14), "10번 중 9번")
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
        let lineName = m.isGoal ? Self.amt(m.keyValue) : "본전"
        let bads = Lens.allCases.map { l in fs[l].map { ($0.q5.last ?? 0) + add($0) } ?? now }
        let below = bads.filter { $0 < now }.count
        let msg = below == bads.count ? "어느 시나리오든 운이 나쁘면 지금(\(Self.amt(now)))보다 줄어들 수 있어요."
            : below > 0 ? "신중하게 보면 운이 나쁠 때 지금(\(Self.amt(now)))보다 줄어들 수 있어요."
            : "운이 나빠도 지금(\(Self.amt(now)))보다는 많을 가능성이 커요."
        return Card {
            Text("세 가지 미래 한눈에").appFont(15, .bold)
            HStack {
                Text("시나리오").frame(maxWidth: .infinity, alignment: .leading)
                Text("그럴듯한 값").frame(width: 70, alignment: .trailing)
                Text("나쁘면").frame(width: 58, alignment: .trailing)
                Text("\(lineName) 넘을 확률").frame(width: 78, alignment: .trailing)
            }
            .appFont(11).foregroundStyle(Theme.muted)
            ForEach(Lens.allCases, id: \.self) { l in
                if let x = fs[l] {
                    let on = m.lens == l
                    Button { m.lens = l } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(l.label).appFont(13, on ? .bold : .regular)
                                Text("연 \(AppModel.sgn0(x.mu))").appFont(11).foregroundStyle(Theme.muted)
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
            Toggle(isOn: $m.shock) { Text("전망을 흔들 수 있는 일정").appFont(15, .bold) }.tint(Theme.teal)
            Text("켜면 이런 날에 더 크게 흔들린다고 보고 계산해요.").appFont(12).foregroundStyle(Theme.sub)
            ForEach(hits.indices, id: \.self) { i in ScheduleRow(hit: hits[i]) }
        }
    }

    // MARK: 자세히 보기 (기본 접힘)
    private func details(_ f: AppModel.Forecast) -> some View {
        let what = m.isGoal ? Self.amt(m.keyValue) : "본전"
        return VStack(alignment: .leading, spacing: 10) {
            Button { withAnimation { open.toggle() } } label: {
                HStack {
                    Text("자세히 보기").appFont(15, .bold)
                    Spacer()
                    Text(open ? "접기" : "펼치기").appFont(13).foregroundStyle(Theme.muted)
                    Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 12)).foregroundStyle(Theme.muted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open {
                Card(dark: true) {
                    Text("중간에 한 번이라도 \(what)을 넘을 확률").appFont(14, .bold)
                    Text("잠깐 넘었다가 다시 내려오는 경우도 세요. 그래서 표의 확률보다 높아요.").appFont(12).foregroundStyle(.secondary)
                    ForEach(0..<f.byYear.count, id: \.self) { i in
                        let y = f.byYear[i].year, p = f.byYear[i].p
                        HStack(spacing: 10) {
                            Text("\(y)년 안").appFont(13).frame(width: 46, alignment: .leading)
                            ProgressBar(value: p, height: 10, fill: Theme.teal, track: Theme.track)
                            Text(AppModel.pct(p)).appFont(13, .bold).frame(width: 40, alignment: .trailing)
                        }
                    }
                }
                Text("버튼은 슬라이더를 0%·내 설정·100%로 옮기는 지름길이에요. 슬라이더를 직접 움직이면 그 값이 '내 설정'으로 기억돼요.")
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
        let a = abs(v), s = v < 0 ? "-" : ""
        if a >= 1e9 { return s + String(format: "%.1f억", a / 1e8) }
        if a >= 1e8 { return s + String(format: "%.2f억", a / 1e8) }
        return s + Int((a / 1e4).rounded()).formatted() + "만"
    }
    static func short(_ l: Lens) -> String { l == .base ? "신중" : l == .smooth ? "3년처럼" : "내 설정" }
    /// "2029-10-14" → ("2029년 10월", "2029.10")
    static func ym(_ d: String) -> (long: String, short: String) {
        let p = d.split(separator: "-")
        guard p.count >= 2, let y = Int(p[0]), let mo = Int(p[1]) else { return (d, d) }
        return ("\(y)년 \(mo)월", String(format: "%d.%02d", y, mo))
    }
}
