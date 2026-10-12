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
        case .rateScen: return "금리 시나리오"
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
                    HStack(spacing: 4) {
                        ForEach(Self.order, id: \.self) { r in
                            Circle().fill(r == sel ? Theme.teal : Theme.border).frame(width: 5, height: 5)
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
        let fxNow = Market.shared.fx.last, today = SiteCalc.today()
        let div = m.rows.reduce(0.0) { a, r in
            let krw = r.sym.currency == .krw
            let o = SiteCalc.shared.div(r.id).flatMap { SiteCalc.project($0.events, shares: r.h.qty, fx: krw ? 1 : fxNow, krw: krw, price: r.sym.last, today: today) }
            return a + (o?.y1 ?? 0)
        }
        let cards: [(String, String, String, Color, AnalysisRoute)] = [
            ("내 길", "정한 목표대로 가고 있나", (gap >= 0 ? "앞섬 " : "뒤처짐 ") + String(format: "%.1f%%", abs(gap) * 100),
             gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060), .myPath),
            (m.forecastTitle, "시장이 줄 수 있는 미래의 범위", fc.exact ? AppModel.pct(fc.pGoal) : "계산 중", Theme.teal, .forecast),
            ("금리 시나리오", "금리가 움직이면 시장 이익과 내 목표는", rateTag, Theme.purple, .rateScen),
            ("외부 요인", "시장·금리·환율이 움직이면 내 자산은", "시장 −10%: " + AppModel.sgn(mkt10), Theme.down, .external),
            ("배당·세금", "앞으로 12개월 배당, 팔 때 세금", "연 " + AppModel.won1(div), Theme.teal, .dividend),
            ("환율 영향", "환율이 바뀌면 내 평가액은", "달러 " + AppModel.pct(usd), Theme.blue, .fx),
            ("종목 한눈에", "고점 대비, 비중, 흔들림을 숫자로", "\(m.rows.count)종목", Theme.teal, .glance),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                AppHeader(tab: "분석").padding(.horizontal, -16)
                SampleNote()
                CalmCard()
                ForEach(cards, id: \.0) { t, sub, tag, color, route in
                    NavigationLink(value: route) {
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
                    }
                    .buttonStyle(.plain)
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

// 3년 전망: 위에 확률·중앙값·나쁜 5%와 범위 그래프·렌즈 설명·렌즈 칩 고정, 아래 추세 믿음·외부 요인·적립
struct ForecastView: View {
    @Environment(AppModel.self) private var m

    /// 개발자 줄: 실제 계산 시간 (사건 반영 + 사건 없이 두 번)과 웹 개발자 줄과 같은 입력 (기준 평가액·현금 뺀 목표·경로·사건·마지막 종가일)
    private func devLine(_ r: ForecastEngine.Run, _ f: AppModel.Forecast) -> String {
        let man = { (v: Double) in Int((v / 1e4).rounded()).formatted() + "만" }
        let px = Market.shared.prices?.h, keys = m.rows.map { SiteStats.key($0.id) } + ["KRW=X"]
        let last = keys.compactMap { px?[$0]?.dates.last }.max() ?? "-"
        let mu = r.factors.filter { !$0.isFx }.map { "\($0.key) \(Int(($0.base * 100).rounded()))/\(Int(($0.smooth * 100).rounded()))" }.joined(separator: " ")
        return "개발자 · 전망 \(String(format: "%.1f", r.ms / 1000))초 · 경로 \(r.ev.P.formatted())개 · 사건 \(r.events)건 · 다른 구성 \(PathRuns.shared.runs.count)개 \(String(format: "%.1f", PathRuns.shared.msToday / 1000))초"
            + " · 기준 \(man(r.ev.v0.reduce(0, +))) · 목표 \(man(m.mcGoal)) · 연 기대 \(AppModel.sgn0(f.mu)) · 확률 \(String(format: "%.1f", f.pGoal * 100))% · μ \(mu) · 종가일 \(last)"
    }

    var body: some View {
        @Bindable var m = m
        let f = m.forecast
        PinnedLayout {
            pinned(f)
        } content: {
                VStack(alignment: .leading, spacing: 12) {
                    if m.lens == .mine {
                        Card {
                            HStack(alignment: .firstTextBaseline) {
                                Text("추세를 얼마나 믿나요 · \(Int(m.trust))%").appFont(14, .bold)
                                Spacer()
                                Text("연 기대 \(AppModel.sgn0(f.mu))").appFont(14, .bold).foregroundStyle(Theme.teal)
                            }
                            Slider(value: $m.trust, in: 0...100, step: 5).tint(Theme.teal).accessibilityLabel("추세를 얼마나 믿나요")
                            HStack { Text("현재 정세"); Spacer(); Text("반반"); Spacer(); Text("과거 추세") }
                                .appFont(12).foregroundStyle(Theme.muted)
                        }
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Toggle(isOn: $m.shock) { Text("외부 요인 넣기").appFont(14, .bold) }.tint(Theme.orange)
                        Text("실적 발표·금리 결정·환율 같은 일정을 웹과 같은 목록으로 반영해요. 끄면 일정 없이 계산해요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                    Card {
                        Text("만약에 매달 더 넣는다면 · \(m.monthly > 0 ? AppModel.eok(m.monthly * 1e4) : "없음")").appFont(14, .bold)
                        Slider(value: $m.monthly, in: 0...300, step: 10).tint(Theme.teal)
                    }
                    Card(dark: true) {
                        Text("\(m.keyName)에 한 번이라도 닿을 확률").appFont(14, .bold)
                        ForEach(0..<f.byYear.count, id: \.self) { i in
                            let y = f.byYear[i].year, p = f.byYear[i].p
                            HStack(spacing: 10) {
                                Text("\(y)년 안").appFont(13).frame(width: 46, alignment: .leading)
                                ProgressBar(value: p, height: 10, fill: Theme.teal, track: Theme.track)
                                Text(AppModel.pct(p)).appFont(13, .bold).frame(width: 40, alignment: .trailing)
                            }
                        }
                    }
                    Text(m.isGoal ? "예측이 아닌 가능성의 범위예요. naeilo.com 3년 전망과 같은 계산(지난 3년 움직임으로 3,000갈래 길, 하루 한 번)이에요."
                         : "예측이 아닌 가능성의 범위예요. 회복 루트는 본전 기준이라 naeilo.com(목표 기준)과 달라요.").appFont(12).foregroundStyle(Theme.muted)
                }
                .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 그래프 바로 밑 렌즈 설명 (렌즈마다 연 기대)
    private func lensNote(_ f: AppModel.Forecast) -> String {
        switch m.lens {
        case .base: return "과거 수익률을 장기 평균 쪽으로 당긴 값. 연 기대 \(AppModel.sgn0(f.muBase))."
        case .mine: return "현재 정세와 과거 추세에서 고른 값. 연 기대 \(AppModel.sgn0(f.mu))."
        case .smooth: return "지난 3년 성장 속도가 이어진 값. 연 기대 \(AppModel.sgn0(f.muTrend))."
        }
    }

    private func pinned(_ f: AppModel.Forecast) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            DetailHead(title: m.forecastTitle, sub: f.exact ? "시장이 줄 수 있는 미래의 범위" : "시장이 줄 수 있는 미래의 범위 · 계산 중 (어림값)")
            HStack(spacing: 8) {
                kpi(m.isGoal ? "목표 달성 확률" : "\(m.keyName) 확률 (3년)", AppModel.pct(f.pGoal), Theme.teal, 20)
                kpi(m.isGoal ? "목표일 중앙값" : "3년 뒤 중앙값", AppModel.eok(f.q50.last ?? 0), Theme.ink, 17)
                kpi("나쁜 5%", AppModel.eok(f.q5.last ?? 0), Theme.down, 17)
            }
            FanChart(f: f, goal: m.keyValue, goalLabel: "\(m.keyName) \(AppModel.eok(m.keyValue))").frame(height: 150)
            Text(lensNote(f)).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
            ChipRow(items: Lens.allCases.map { ($0, $0.label) }, selection: Binding(get: { m.lens }, set: { m.lens = $0 }), fill: true)
            if Sync.shared.isDev, let r = ForecastEngine.shared.run, r.key == m.forecastJob?.key {
                Text(devLine(r, f))
                    .appFont(11).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Theme.card)
        .overlay(alignment: .bottom) { Divider() }
    }

    private func kpi(_ k: String, _ v: String, _ c: Color, _ size: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(11).foregroundStyle(Theme.sub)
            Text(v).font(.system(size: size, weight: .bold)).foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.7)
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
    }
}

// 로그 축 범위 그래프
struct FanChart: View {
    let f: AppModel.Forecast
    let goal: Double
    var goalLabel: String? = nil
    var body: some View {
        Canvas { ctx, size in
            let hi = log(max(goal, f.q95.max() ?? 1) * 1.02), lo = log(max(1, min(goal, f.q5.min() ?? 1) * 0.98))
            let n = f.q50.count - 1, span = max(1e-9, f.tY.last ?? 1)
            let x = { (i: Int) in CGFloat(f.tY[i] / span) * size.width }
            let y = { (v: Double) in CGFloat((hi - log(max(1, v))) / max(1e-9, hi - lo)) * size.height }
            func band(_ a: [Double], _ b: [Double]) -> Path {
                Path { p in
                    for i in 0...n { let pt = CGPoint(x: x(i), y: y(a[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                    for i in stride(from: n, through: 0, by: -1) { p.addLine(to: CGPoint(x: x(i), y: y(b[i]))) }
                    p.closeSubpath()
                }
            }
            ctx.fill(band(f.q95, f.q5), with: .color(Theme.green.opacity(0.16)))
            ctx.fill(band(f.q75, f.q25), with: .color(Theme.green.opacity(0.32)))
            ctx.stroke(Path { p in for i in 0...n { let pt = CGPoint(x: x(i), y: y(f.q50[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } },
                       with: .color(Theme.green), lineWidth: 2)
            ctx.stroke(Path { p in p.move(to: CGPoint(x: 0, y: y(goal))); p.addLine(to: CGPoint(x: size.width, y: y(goal))) },
                       with: .color(Theme.orange), style: StrokeStyle(lineWidth: 1.6, dash: [5, 4]))
            // 해마다 눈금 (5년이 넘으면 2년마다)
            let yrs = Int(span + 1.0 / 48), step = yrs > 5 ? 2 : 1
            for yr in stride(from: step, through: yrs, by: step) {
                let xx = CGFloat(Double(yr) / span) * size.width
                ctx.draw(Text("\(yr)년").font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: xx - 12, y: size.height - 6))
            }
            // 그래프 안 설명: 왼쪽 위 범위, 목표 점선 위 금액
            if let goalLabel {
                let gy = y(goal)
                ctx.draw(Text(goalLabel).font(.system(size: 10, weight: .semibold)).foregroundStyle(Theme.orange),
                         at: CGPoint(x: size.width - 2, y: gy < 14 ? gy + 8 : gy - 8), anchor: .trailing)
                ctx.draw(Text("50%·90% 범위").font(.system(size: 10)).foregroundStyle(Theme.green),
                         at: CGPoint(x: 2, y: 8), anchor: .leading)
            }
        }
        .accessibilityLabel("전망 범위 그래프")
    }
}
