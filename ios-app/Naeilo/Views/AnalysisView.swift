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
    static let order: [AnalysisRoute] = [.calm, .myPath, .forecast, .rateScen, .external, .fx, .glance, .dividend]   // 허브 순서와 같게

    init(start: AnalysisRoute) { _sel = State(initialValue: start) }

    private func title(_ r: AnalysisRoute) -> String {
        switch r {
        case .calm: return String(localized: "내 투자 습관")
        case .myPath: return String(localized: "내 길")
        case .forecast: return m.forecastTitle
        case .rateScen: return String(localized: "금리 충격 테스트")
        case .external: return String(localized: "외부 요인")
        case .dividend: return String(localized: "배당·세금")
        case .fx: return String(localized: "환율 영향")
        case .glance: return String(localized: "종목 한눈에")
        }
    }

    @ViewBuilder private func page(_ r: AnalysisRoute) -> some View {
        switch r {
        // 7일 무료 뒤 구독하지 않으면 목표(내 길)·확률 전망만 잠근다 (Support.pro)
        case .forecast: if Support.shared.pro { ForecastView() } else { SubLocked(what: String(localized: "확률 전망")) }
        case .myPath: if Support.shared.pro { MyPathView() } else { SubLocked(what: String(localized: "내 길(목표까지 확률)")) }
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

// 분석 탭 허브 (시안 B · 아이콘 + 한 줄 결과): 습관 띠, 결론 카드, 결과 행 8개.
// 행의 값은 그 상세 화면 첫 문장의 숫자와 같은 식 (허브 전용 숫자를 따로 계산하지 않는다)
struct AnalysisView: View {
    @Environment(AppModel.self) private var m
    /// 허브 색은 의미 3개만: 초록 들어오는 돈·좋아짐 / 주황 나가는 돈·늦어짐 / 회색 정보
    static let good = Color(hex: 0x2C5A54, dark: 0x8FD0C4)
    static let warn = Color(hex: 0xA8521A, dark: 0xE8A060)
    enum Tone { case good, warn, info }

    struct Line { let icon: String; let tone: Tone; let label: String; let value: String; var valueTone: Tone? = nil; var donut: Double? = nil; let route: AnalysisRoute }

    /// 확률을 빈도로: 3번 중 1번처럼
    static func freq(_ p: Double) -> String {
        for (a, b) in [(1, 2), (1, 3), (2, 3), (1, 4), (3, 4)] where abs(p - Double(a) / Double(b)) < 0.03 { return String(localized: "\(String(describing: b))번 중 \(String(describing: a))번") }
        let n = Int((p * 10).rounded())
        return n <= 0 ? String(localized: "10번 중 1번 미만") : n >= 10 ? String(localized: "10번 중 거의 10번") : String(localized: "10번 중 \(String(describing: n))번")
    }

    private func name(_ r: AppModel.Row) -> String { r.sym.name.isEmpty ? r.id : r.sym.name }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                AppHeader(tab: String(localized: "분석")).padding(.horizontal, -16)
                SampleNote()
                habitBand
                summary.padding(.top, 6)
                ForEach(lines.indices, id: \.self) { i in row(lines[i]) }
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
        .navigationDestination(for: AnalysisRoute.self) { AnalysisPager(start: $0) }
    }

    // MARK: 내 투자 습관 띠 (위젯처럼 성격이 다른 영역, 대사는 상세에만)
    private var habitBand: some View {
        let s = m.calm.stats(), done = s.n >= Calm.minEvents
        return HStack(spacing: 12) {
            CharHead(id: m.calmSpeaker, size: 32)
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline) {
                    Text("투자 습관").appFont(14, .bold).foregroundStyle(Theme.mint)
                    Spacer()
                    Text(done ? String(localized: "지수 \(String(describing: s.index))") : "\(s.n)/\(Calm.minEvents)").appFont(13).foregroundStyle(Color(hex: 0xC3C8D0))
                }
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color(hex: 0x343A47))
                        Capsule().fill(Theme.mint).frame(width: g.size.width * min(1, Double(s.n) / Double(Calm.minEvents)))
                    }
                }
                .frame(height: 4)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10).frame(minHeight: 44)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 16))
        .modifier(OpenOnTap(route: .calm))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(done ? String(localized: "내 투자 습관, 지수 \(String(describing: s.index))") : String(localized: "내 투자 습관, 기록 \(String(describing: s.n)) / \(String(describing: Calm.minEvents))"))
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
    }

    // MARK: 결론 카드
    private var summary: some View {
        let K = m.keyValue, now = m.trackValue, r = K > 0 ? now / K : 0, pct = Int((r * 100).rounded())
        let head = r >= 1 ? String(localized: "\(String(describing: m.keyName)) 도달") : (45...55).contains(pct) ? String(localized: "\(String(describing: m.keyName))까지 절반") : String(localized: "\(String(describing: m.keyName))까지 \(String(describing: pct))%")
        let end = ForecastView.ym(m.forecastHorizon).short
        return VStack(spacing: 14) {
            Text(head).appFont(24, .bold).foregroundStyle(.white).roll(head)
            VStack(spacing: 8) {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color(hex: 0x46736C))
                        Capsule().fill(Color(hex: 0xA9D6CB)).frame(width: g.size.width * min(1, max(0, r)))
                    }
                }
                .frame(height: 10)
                HStack { Text(AppModel.eok(now)); Spacer(); Text("\(AppModel.eok(K)) · \(end)") }
                    .appFont(14).foregroundStyle(Color(hex: 0xDCEDE9))
            }
        }
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 20).frame(maxWidth: .infinity)
        .background(Color(hex: 0x2C5A54), in: RoundedRectangle(cornerRadius: 22))
        .accessibilityElement(children: .combine)
    }

    // MARK: 결과 행
    private func color(_ t: Tone) -> Color { t == .good ? Self.good : t == .warn ? Self.warn : Theme.ink }

    private func row(_ l: Line) -> some View {
        let bg: Color = l.tone == .good ? Color(hex: 0xE3F0ED, dark: 0x173531) : l.tone == .warn ? Color(hex: 0xFBEBDD, dark: 0x3A2A1C) : Theme.line
        let fg: Color = l.tone == .good ? Self.good : l.tone == .warn ? Self.warn : Theme.sub
        return HStack(spacing: 14) {
            Group {
                if let d = l.donut {
                    ZStack {
                        Circle().stroke(Theme.border, lineWidth: 5)
                        Circle().trim(from: 0, to: d).stroke(Self.good, lineWidth: 5).rotationEffect(.degrees(-90))
                    }
                    .frame(width: 22, height: 22)
                } else {
                    Image(systemName: l.icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(fg)
                }
            }
            .frame(width: 44, height: 44).background(bg, in: RoundedRectangle(cornerRadius: 14))
            Text(l.label).appFont(15).foregroundStyle(Theme.sub).lineLimit(1)
            Spacer(minLength: 6)
            Text(l.value).appFont(18, .bold).foregroundStyle(color(l.valueTone ?? .info)).lineLimit(1).minimumScaleFactor(0.7).roll(l.value)
        }
        .padding(.leading, 14).padding(.trailing, 18).padding(.vertical, 14).frame(minHeight: 72)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .modifier(OpenOnTap(route: l.route))
        .accessibilityElement(children: .combine)
    }

    private var lines: [Line] {
        let fc = m.forecast, tot = max(1, m.total)
        var out: [Line] = []
        // 내 길: 목표일에 목표 이상일 확률을 빈도로 (7일 무료 뒤 구독하지 않으면 잠금)
        let pro = Support.shared.pro
        if !pro {
            out.append(Line(icon: "lock.fill", tone: .info, label: String(localized: "내 길"), value: String(localized: "구독 시 열림"), route: .myPath))
        } else if m.isGoal {
            out.append(Line(icon: "flag", tone: .good, label: String(localized: "내 길"), value: fc.exact ? Self.freq(fc.pGoal) : String(localized: "계산 중"), valueTone: .good, route: .myPath))
        } else {
            // 회복 루트도 내 길 판정 카드와 같은 값: 기간 안 본전 확률
            let p = m.myPath
            out.append(Line(icon: "flag", tone: .good, label: String(localized: "내 길"), value: fc.exact ? Self.freq(fc.prob(min(3, p.end - p.start))) : String(localized: "계산 중"), valueTone: .good, route: .myPath))
        }
        // 전망: 상세 첫 카드처럼 경로 값에 현금을 더한다
        let med = (fc.q50.last ?? 0) + (fc.exact ? m.cashKrw : 0)
        out.append(Line(icon: pro ? "calendar" : "lock.fill", tone: pro ? .good : .info, label: m.isGoal ? String(localized: "\(String(describing: ForecastView.ym(m.forecastHorizon).short)) 전망") : String(localized: "3년 뒤 전망"),
                        value: pro ? AppModel.eok(med) : String(localized: "구독 시 열림"), route: .forecast))
        // 금리 충격: 기본 프리셋("작년만큼 또 오른다")의 도착 시점 변화
        let t = RateScenarioView.hubTiming(m)
        let late = t?.hasSuffix(String(localized: "늦게")) == true, early = t?.hasSuffix(String(localized: "빨리")) == true
        out.append(Line(icon: "chart.line.uptrend.xyaxis", tone: .warn, label: String(localized: "금리 오르면"),
                        value: t.map { $0.replacingOccurrences(of: String(localized: "약 "), with: "") } ?? (MacroData.shared.failed ? String(localized: "자료 없음") : String(localized: "계산 중")),
                        valueTone: late ? .warn : early ? .good : .info, route: .rateScen))
        // 외부 요인: 가장 가까운 발표 일정 (없으면 시장 −10% 일 때)
        if let e = ExternalView.hubNext(m) {
            let n = Day.dday(e.day)
            out.append(Line(icon: "building.columns", tone: .info, label: e.kind.replacingOccurrences(of: " 결정", with: "") + " " + (n == 0 ? String(localized: "오늘") : "D-\(n)"), value: "±" + AppModel.man(e.amt), route: .external))
        } else {
            let mkt10 = m.rows.reduce(0.0) { a, r in a + r.value * (ExternalView.mktBeta(r.id).map { SiteCalc.factorMove("mkt", beta: $0, shock: -10) } ?? 0) }
            out.append(Line(icon: "building.columns", tone: .info, label: String(localized: "시장 −10%면"), value: AppModel.man(mkt10), valueTone: .warn, route: .external))
        }
        // 환율: 상세 첫 문장과 같은 per10 = 달러 평가액 × 10 / 기준 환율
        let usdV = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.value }
        out.append(Line(icon: "arrow.left.arrow.right", tone: .info, label: String(localized: "환율 +10원"),
                        value: usdV > 0 ? "+" + AppModel.man(usdV * 10 / max(1, Market.shared.valFx)) : String(localized: "그대로"), route: .fx))
        // 종목 한눈에: 비중 1위
        let top = m.rows.max { $0.value < $1.value }
        out.append(Line(icon: "chart.pie", tone: .info, label: String(localized: "종목 한눈에"), value: top.map { "\(name($0)) \(AppModel.pct($0.value / tot))" } ?? String(localized: "종목 없음"),
                        donut: top.map { $0.value / tot }, route: .glance))
        // 배당·세금: 두 줄로 따로, 한 숫자로 합치지 않는다
        let mo = DividendView.hubMonthly(m), tax = DividendView.hubTax(m)
        out.append(Line(icon: "arrow.down.to.line", tone: .good, label: String(localized: "배당 · 월"), value: mo > 0 ? "+" + AppModel.wonRead(mo) : String(localized: "없음"), valueTone: mo > 0 ? .good : .info, route: .dividend))
        out.append(Line(icon: "arrow.up.to.line", tone: .warn, label: String(localized: "세금 · 내년 5월"), value: tax > 0 ? "−" + AppModel.man(tax) : String(localized: "없음"), valueTone: tax > 0 ? .warn : .info, route: .dividend))
        return out
    }
}

/// 분석 허브 행: 앱 다른 화면처럼 기본 이동 링크 그대로 (예전엔 옆으로 밀기 막는 끌기 제스처를 겹쳐 탭이 씹혔다)
struct OpenOnTap: ViewModifier {
    let route: AnalysisRoute
    func body(content: Content) -> some View {
        NavigationLink(value: route) { content }.buttonStyle(.plain)
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
