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
        case .calm: return "내 투자 습관"
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

// 분석 탭 허브: 맨 위 습관 띠, 결론 카드, 그룹 3개 아래 한 줄 결과 행.
// 행의 답 문장은 그 상세 화면 첫 문장을 줄인 것 (허브 전용 숫자를 따로 계산하지 않는다)
struct AnalysisView: View {
    @Environment(AppModel.self) private var m
    /// 허브 색은 의미 3개만: 들어오는 돈·좋아짐 / 나가는 돈·늦어짐·주의 / 회색 정보
    static let good = Color(hex: 0x2C5A54, dark: 0x8FD0C4)
    static let warn = Color(hex: 0xA8521A, dark: 0xE8A060)

    struct Line { let icon: String; let head: String; let answer: Text?; let sub: String?; var badge: String? = nil; var extra: AnyView? = nil; let route: AnalysisRoute }

    /// 확률을 빈도로: 3번 중 1번처럼
    static func freq(_ p: Double) -> String {
        for (a, b) in [(1, 2), (1, 3), (2, 3), (1, 4), (3, 4)] where abs(p - Double(a) / Double(b)) < 0.03 { return "\(b)번 중 \(a)번" }
        let n = Int((p * 10).rounded())
        return n <= 0 ? "10번 중 1번도 안 되게" : n >= 10 ? "10번 중 거의 10번" : "10번 중 \(n)번"
    }

    private func name(_ r: AppModel.Row) -> String { r.sym.name.isEmpty ? r.id : r.sym.name }

    var body: some View {
        let fc = m.forecast, K = m.keyValue, now = m.trackValue
        let tot = max(1, m.total)
        let ranked = m.rows.sorted { $0.value > $1.value }
        let usdV = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.value }
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                AppHeader(tab: "분석").padding(.horizontal, -16)
                SampleNote()
                habitBand
                Divider().padding(.vertical, 14)
                summary(fc: fc, K: K, now: now, top: ranked.first, tot: tot)
                group("목표에 닿을까?", goalLines(fc: fc))
                group("지금 내 돈은 얼마나 흔들릴까?", shakeLines(ranked: ranked, tot: tot, usdV: usdV))
                group("들어오고 나갈 돈", [moneyLine])
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .task { await MacroData.shared.load() }
        .task { await SiteCalc.shared.refresh() }
        .navigationDestination(for: AnalysisRoute.self) { AnalysisPager(start: $0) }
    }

    // MARK: 내 투자 습관 띠 (위젯처럼 성격이 다른 영역, 대사는 상세에만)
    private var habitBand: some View {
        let s = m.calm.stats(), k = Calm.kind(m.calm.type), need = max(0, Calm.minEvents - s.n)
        return HStack(spacing: 12) {
            CharPixel(id: m.calmSpeaker, width: 25, height: 36)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("내 투자 습관").appFont(14, .bold)
                    Spacer()
                    Text(need > 0 ? "기록 \(s.n)/\(Calm.minEvents)" : "지수 \(s.index)").appFont(12).foregroundStyle(Theme.muted)
                }
                Text(need > 0 ? "\(need)건 더 모으면 나와 닮은 인물을 알려드려요" : (k.map { "나는 \($0.title)에 가까워요" } ?? "기록이 모였어요"))
                    .appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
                if need > 0 {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Theme.track)
                            Capsule().fill(Theme.muted).frame(width: g.size.width * Double(s.n) / Double(Calm.minEvents))
                        }
                    }
                    .frame(height: 4)
                }
            }
            chevron
        }
        .padding(.vertical, 4)
        .modifier(OpenOnTap(route: .calm))
        .accessibilityElement(children: .combine)
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
    }

    // MARK: 지금 내 상황 (결론)
    @ViewBuilder
    private func summary(fc: AppModel.Forecast, K: Double, now: Double, top: AppModel.Row?, tot: Double) -> some View {
        let r = K > 0 ? now / K : 0
        let pct = Int((r * 100).rounded())
        let first = r >= 1 ? "\(m.keyName)에 닿았어요." : (40...60).contains(pct) ? "\(m.keyName)까지 절반 왔어요." : "\(m.keyName)까지 \(pct)% 왔어요."
        let when = m.isGoal ? "목표일에" : "3년 뒤에"
        let second = fc.exact ? "지금대로면 \(Self.freq(fc.pGoal))은 \(when) \(AppModel.eok(K))에 닿아요." : "가능성을 계산하고 있어요."
        let end = ForecastView.ym(m.forecastHorizon).short
        VStack(alignment: .leading, spacing: 10) {
            Text("지금 내 상황").appFont(12, .semibold).foregroundStyle(.white.opacity(0.75))
            Text(first + "\n" + second).appFont(19, .bold).foregroundStyle(.white).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 5) {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.2))
                        Capsule().fill(.white).frame(width: g.size.width * min(1, max(0, r)))
                    }
                }
                .frame(height: 6)
                HStack {
                    Text("지금 \(AppModel.eok(now))"); Spacer(); Text("\(end) · \(m.keyName) \(AppModel.eok(K))")
                }
                .appFont(12).foregroundStyle(.white.opacity(0.8))
            }
            if let t = top, t.value / tot >= 0.2 {
                let w = Int((t.value / tot * 100).rounded())
                Text(w >= 40 ? "자산의 \(w)%가 \(name(t)) 한 종목이라, 이 종목이 움직이면 내 자산도 거의 같이 움직여요."
                             : "가장 큰 종목 \(name(t))이 자산의 \(w)%예요.")
                    .appFont(13).foregroundStyle(.white.opacity(0.9)).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(18).frame(maxWidth: .infinity, alignment: .leading)
        .background(Self.good, in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }

    // MARK: 그룹
    private func group(_ title: String, _ lines: [Line]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).appFont(15, .bold).padding(.top, 22)
            VStack(spacing: 0) {
                ForEach(lines.indices, id: \.self) { i in
                    if i > 0 { Divider().padding(.leading, 56) }
                    row(lines[i])
                }
            }
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
        }
    }

    private var chevron: some View { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.muted) }

    private func row(_ l: Line) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: l.icon).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.sub)
                .frame(width: 32, height: 32).background(Theme.line, in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    if let b = l.badge {
                        Text(b).appFont(11, .bold).foregroundStyle(.white).padding(.horizontal, 6).padding(.vertical, 1).background(Self.warn, in: Capsule())
                    }
                    Text(l.head).appFont(12).foregroundStyle(Theme.muted).lineLimit(1)
                }
                if let a = l.answer { a.appFont(16, .bold).foregroundStyle(Theme.ink).fixedSize(horizontal: false, vertical: true) }
                if let x = l.extra { x }
                if let s = l.sub { Text(s).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
            }
            Spacer(minLength: 4)
            chevron
        }
        .padding(.horizontal, 12).padding(.vertical, 14)
        .modifier(OpenOnTap(route: l.route))
        .accessibilityElement(children: .combine)
    }

    // MARK: 줄 문구
    private func goalLines(fc: AppModel.Forecast) -> [Line] {
        let gap = m.myPathGapToday
        let my: Line = m.isGoal
            ? Line(icon: "point.topleft.down.to.point.bottomright.curvepath", head: "내 길 · 구성 바꾸면 언제 닿을까",
                   answer: fc.exact ? Text("지금 구성은 ") + Text(Self.freq(fc.pGoal)).foregroundColor(Self.good) + Text(" 닿아요") : Text("가능성을 계산하고 있어요"),
                   sub: "다른 구성 2가지와 비교해 볼 수 있어요", route: .myPath)
            : Line(icon: "point.topleft.down.to.point.bottomright.curvepath", head: "내 길 · 정한 길대로 가고 있나",
                   answer: Text("정한 길보다 ") + Text(String(format: "%.1f%%", abs(gap) * 100) + (gap >= 0 ? " 앞서요" : " 뒤처져요")).foregroundColor(gap >= 0 ? Self.good : Self.warn),
                   sub: nil, route: .myPath)
        // 목표일 전망: 상세 화면 첫 카드와 같게 경로 값에 현금을 더한다
        let add = fc.exact ? m.cashKrw : 0
        let med = (fc.q50.last ?? 0) + add, lo = (fc.q5.last ?? 0) + add, hi = (fc.q95.last ?? 0) + add
        let end = ForecastView.ym(m.forecastHorizon).long
        let fcLine = Line(icon: "chart.line.uptrend.xyaxis", head: "\(m.forecastTitle) · \(m.isGoal ? end : "3년 뒤") 내 자산은",
                          answer: Text("보통 ") + Text(AppModel.eok(med)) + Text(" 정도예요"),
                          sub: "10번 중 9번은 \(AppModel.eok(lo)) ~ \(AppModel.eok(hi)) 사이", route: .forecast)
        let t = RateScenarioView.hubTiming(m)
        let late = t?.contains("늦어져요") == true, early = t?.contains("빨라져요") == true
        let tShort = (t ?? "").replacingOccurrences(of: "약 ", with: "")
        let rate = Line(icon: "percent", head: "금리 충격 테스트 · 금리가 작년만큼 또 오르면",
                        answer: t == nil ? Text(MacroData.shared.failed ? "금리 자료를 불러오지 못했어요" : "금리 자료를 불러오는 중이에요")
                            : late || early ? Text(m.keyName + (m.keyName == "목표" ? "가 " : "이 ")) + Text(tShort).foregroundColor(late ? Self.warn : Self.good)
                            : Text("\(m.keyName)에 닿는 시점: ") + Text(tShort),
                        sub: "내릴 때, 2022년처럼 급등할 때도 볼 수 있어요", route: .rateScen)
        return [my, fcLine, rate]
    }

    private func shakeLines(ranked: [AppModel.Row], tot: Double, usdV: Double) -> [Line] {
        // 외부 요인: 가장 가까운 발표 일정 (없으면 시장 −10% 일 때)
        let ext: Line
        if let e = ExternalView.hubNext(m) {
            let n = Day.dday(e.day)
            ext = Line(icon: "globe", head: "외부 요인 · \(Day.md(e.day)) \(e.kind)", answer: Text("발표날 보통 ±\(AppModel.man(e.amt)) 움직여요"),
                       sub: "하루하루 흔들림 기준이에요", badge: n == 0 ? "오늘" : "D-\(n)", route: .external)
        } else {
            let mkt10 = m.rows.reduce(0.0) { a, r in
                a + r.value * (ExternalView.mktBeta(r.id).map { SiteCalc.factorMove("mkt", beta: $0, shock: -10) } ?? 0)
            }
            ext = Line(icon: "globe", head: "외부 요인 · 시장이 10% 내리면",
                       answer: Text("내 자산은 약 ") + Text(AppModel.man(abs(mkt10)) + " 줄어요").foregroundColor(Self.warn),
                       sub: "하루하루 흔들림 기준이에요", route: .external)
        }
        // 환율 영향: 상세 첫 문장과 같은 per10 = 달러 평가액 × 10 / 기준 환율
        let usdW = usdV / tot, per10 = usdV * 10 / max(1, Market.shared.valFx)
        let fx = Line(icon: "dollarsign.arrow.circlepath",
                      head: "환율 영향 · " + (usdV <= 0 ? "달러 자산이 없어요" : usdW > 0.995 ? "내 자산은 모두 달러예요" : "달러 자산이 \(AppModel.pct(usdW))예요"),
                      answer: usdV <= 0 ? Text("환율이 바뀌어도 그대로예요") : Text("환율 10원 오르면 약 ") + Text("+" + AppModel.man(per10)).foregroundColor(Self.good),
                      sub: usdV <= 0 ? nil : "내리면 그만큼 줄어요", route: .fx)
        // 종목 한눈에: 비중 막대 (1·2위와 나머지)
        let top = Array(ranked.prefix(2)), w = top.map { $0.value / tot }, rest = max(0, 1 - w.reduce(0, +))
        let parts: [(String, Double, Color)] = zip(top, w).enumerated().map { i, x in (name(x.0), x.1, i == 0 ? Theme.sub : Theme.muted) }
            + (rest > 0.005 ? [("나머지", rest, Theme.dash)] : [])
        let bar = VStack(alignment: .leading, spacing: 4) {
            GeometryReader { g in
                HStack(spacing: 2) { ForEach(parts.indices, id: \.self) { i in Rectangle().fill(parts[i].2).frame(width: max(2, g.size.width * parts[i].1 - 2)) } }
            }
            .frame(height: 6).clipShape(Capsule())
            HStack(spacing: 10) { ForEach(parts.indices, id: \.self) { i in Text("\(parts[i].0) \(AppModel.pct(parts[i].1))").lineLimit(1) } }
                .appFont(11).foregroundStyle(Theme.muted)
        }.padding(.top, 2)
        let gl = Line(icon: "square.grid.2x2", head: "종목 한눈에 · \(m.rows.count)종목",
                      answer: ranked.first.map { Text("\(name($0))가 내 자산의 \(AppModel.pct($0.value / tot))") } ?? Text("종목이 없어요"),
                      sub: nil, extra: ranked.isEmpty ? nil : AnyView(bar), route: .glance)
        return [ext, fx, gl]
    }

    /// 배당·세금: 들어올 배당(초록)과 낼 양도세(주황)를 두 줄로, 한 숫자로 합치지 않는다
    private var moneyLine: Line {
        let mo = DividendView.hubMonthly(m), tax = DividendView.hubTax(m)
        let year = (Int(SiteCalc.today().prefix(4)) ?? 2026) + 1
        let two = VStack(alignment: .leading, spacing: 6) {
            HStack { Text("들어올 배당").foregroundStyle(Theme.sub); Spacer()
                Text(mo > 0 ? "한 달 평균 \(AppModel.wonRead(mo))" : "없어요").bold().foregroundStyle(mo > 0 ? Self.good : Theme.muted) }
            Divider()
            HStack { Text("낼 양도세").foregroundStyle(Theme.sub); Spacer()
                Text(tax > 0 ? "\(String(year % 100))년 5월 약 \(AppModel.man(tax))" : "없어요").bold().foregroundStyle(tax > 0 ? Self.warn : Theme.muted) }
        }.appFont(14).padding(.top, 4)
        return Line(icon: "wonsign.circle", head: "배당·세금", answer: nil, sub: nil, extra: AnyView(two), route: .dividend)
    }
}

/// 분석 허브 행: 탭하면 연다. 버튼 + 끌기 제스처를 겹치면 스크롤 안에서 눌림이 늦게 잡혀 터치가 밀렸다.
/// 탭 제스처는 손가락이 움직이면 저절로 취소되므로 옆으로 밀다 떼도 열리지 않는다
struct OpenOnTap: ViewModifier {
    @Environment(AppModel.self) private var m
    let route: AnalysisRoute
    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .onTapGesture { m.analysisPath.append(route) }
            .accessibilityAddTraits(.isButton)
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
