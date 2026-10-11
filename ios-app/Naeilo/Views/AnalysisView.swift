import SwiftUI

enum AnalysisRoute: Hashable { case forecast, myPath, rateScen, external, dividend, fx, glance, calm }

// 분석 탭: naeilo.com 분석·전략을 폰에 맞게. 카드 머리 숫자는 각 화면 첫 숫자와 같은 계산.
struct AnalysisView: View {
    @Environment(AppModel.self) private var m

    /// 금리 시나리오 카드 머리: 지난 1년 금리 변화가 이어질 때 S&P 실질 이익 3년 누적 중앙값
    private var rateTag: String {
        let md = MacroData.shared
        guard let M = md.file?.meals else { return md.failed ? "자료 없음" : "…" }
        let r = RateScen(coef: M.coef, g0: M.g_now, dr: md.defaultDr, m: 0)
        guard let c = r.cum.last else { return "…" }
        return "이익 \(r.years)년 " + AppModel.sgn0(exp(c[1] / 100) - 1)
    }

    var body: some View {
        let p3 = m.forecast.prob(3)
        let usd = m.rows.filter { $0.sym.currency == .usd || $0.id == "360750" }.reduce(0) { $0 + $1.value } / max(1, m.total)
        // 내 길: 시작(3개월 전)부터 본전까지 1년에 걸친 선과 오늘 비교
        let gap = m.myPathGapToday
        let mkt10 = m.rows.reduce(0) { $0 + $1.value * (ExternalView.beta[$1.id]?.0 ?? 1) * -0.1 } / max(1, m.total)
        let div = m.rows.reduce(0) { a, r in
            a + m.krw(r.sym, r.h.qty * (DividendView.divs[r.id]?.0 ?? 0)) * (1 - (r.sym.currency == .usd ? 0.15 : 0.154))
        }
        let cards: [(String, String, String, Color, AnalysisRoute)] = [
            ("내 길", "정한 목표대로 가고 있나 (자산 추이)", (gap >= 0 ? "앞섬 " : "뒤처짐 ") + "\(Int((abs(gap) * 100).rounded()))%",
             gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060), .myPath),
            ("3년 전망", "시장이 줄 수 있는 미래의 범위", AppModel.pct(p3), Theme.teal, .forecast),
            ("금리 시나리오", "금리가 움직이면 시장 이익과 내 목표는", rateTag, Theme.purple, .rateScen),
            ("외부 요인", "시장·금리·환율이 움직이면 내 자산은", "시장 −10%: " + AppModel.sgn(mkt10), Theme.down, .external),
            ("배당·세금", "앞으로 12개월 배당, 팔 때 세금", "연 " + AppModel.won1(div), Theme.teal, .dividend),
            ("환율 영향", "환율이 바뀌면 내 평가액은", "달러 " + AppModel.pct(usd), Theme.blue, .fx),
            ("종목 한눈에", "고점 대비, 비중, 흔들림을 숫자로", "\(m.rows.count)종목", Theme.teal, .glance),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                AppHeader(tab: "분석").padding(.horizontal, -16)
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
        .navigationDestination(for: AnalysisRoute.self) { r in
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
    }
}

// 3년 전망: 위에 확률·중앙값·나쁜 5%와 범위 그래프·렌즈 설명·렌즈 칩 고정, 아래 추세 믿음·외부 요인·적립
struct ForecastView: View {
    @Environment(AppModel.self) private var m

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
                                Text("연 기대 \(AppModel.sgn0(f.mu + (m.shock ? 0.02 : 0)))").appFont(14, .bold).foregroundStyle(Theme.teal)
                            }
                            Slider(value: $m.trust, in: 0...100, step: 5).tint(Theme.teal).accessibilityLabel("추세를 얼마나 믿나요")
                            HStack { Text("현재 정세"); Spacer(); Text("반반"); Spacer(); Text("과거 추세") }
                                .appFont(12).foregroundStyle(Theme.muted)
                        }
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Toggle(isOn: $m.shock) { Text("외부 요인 넣기").appFont(14, .bold) }.tint(Theme.orange)
                        Text("금리 결정, 실적 발표 등 반영 · 기대 −2% · 흔들림 +10%").appFont(12).foregroundStyle(Theme.muted)
                    }
                    Card {
                        Text("만약에 매달 더 넣는다면 · \(m.monthly > 0 ? AppModel.man(m.monthly * 1e4) : "없음")").appFont(14, .bold)
                        Slider(value: $m.monthly, in: 0...300, step: 10).tint(Theme.teal)
                    }
                    Card(dark: true) {
                        Text("그해 말에 \(m.keyName)을 넘을 확률").appFont(14, .bold)
                        ForEach(1...3, id: \.self) { y in
                            let p = f.prob(Double(y))
                            HStack(spacing: 10) {
                                Text("\(y)년").appFont(13).frame(width: 32, alignment: .leading)
                                ProgressBar(value: p, height: 10, fill: Theme.teal, track: Theme.track)
                                Text(AppModel.pct(p)).appFont(13, .bold).frame(width: 40, alignment: .trailing)
                            }
                        }
                    }
                    Text("예측이 아닌 \"만약\" 도구. 촐레스키 분해와 몬테카를로로 계산, 추세선은 칼만 필터 적용. 종목 추천이 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
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
        case .mine: return "현재 정세와 과거 추세에서 고른 값. 연 기대 \(AppModel.sgn0(f.mu + (m.shock ? 0.02 : 0)))."
        case .smooth: return "지난 3년 성장 속도가 이어진 값. 연 기대 \(AppModel.sgn0(f.muTrend))."
        }
    }

    private func pinned(_ f: AppModel.Forecast) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            DetailHead(title: "3년 전망", sub: "시장이 줄 수 있는 미래의 범위")
            HStack(spacing: 8) {
                kpi("\(m.keyName) 확률 (3년)", AppModel.pct(f.prob(3)), Theme.teal, 20)
                kpi("3년 뒤 중앙값", AppModel.man(f.q50[36]), Theme.ink, 17)
                kpi("나쁜 5%", AppModel.man(f.q5[36]), Theme.down, 17)
            }
            FanChart(f: f, goal: m.keyValue, goalLabel: "\(m.keyName) \(AppModel.man(m.keyValue))").frame(height: 150)
            Text(lensNote(f)).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
            ChipRow(items: Lens.allCases.map { ($0, $0.label) }, selection: Binding(get: { m.lens }, set: { m.lens = $0 }), fill: true)
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
            let x = { (i: Int) in CGFloat(i) / 36 * size.width }
            let y = { (v: Double) in CGFloat((hi - log(max(1, v))) / max(1e-9, hi - lo)) * size.height }
            func band(_ a: [Double], _ b: [Double]) -> Path {
                Path { p in
                    for i in 0...36 { let pt = CGPoint(x: x(i), y: y(a[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                    for i in stride(from: 36, through: 0, by: -1) { p.addLine(to: CGPoint(x: x(i), y: y(b[i]))) }
                    p.closeSubpath()
                }
            }
            ctx.fill(band(f.q95, f.q5), with: .color(Theme.green.opacity(0.16)))
            ctx.fill(band(f.q75, f.q25), with: .color(Theme.green.opacity(0.32)))
            ctx.stroke(Path { p in for i in 0...36 { let pt = CGPoint(x: x(i), y: y(f.q50[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } },
                       with: .color(Theme.green), lineWidth: 2)
            ctx.stroke(Path { p in p.move(to: CGPoint(x: 0, y: y(goal))); p.addLine(to: CGPoint(x: size.width, y: y(goal))) },
                       with: .color(Theme.orange), style: StrokeStyle(lineWidth: 1.6, dash: [5, 4]))
            for (i, t) in [(12, "1년"), (24, "2년"), (36, "3년")] {
                ctx.draw(Text(t).font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: x(i) - 12, y: size.height - 6))
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
        .accessibilityLabel("3년 범위 그래프")
    }
}

struct SoonView: View {
    let title: String
    let detail: String
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).appFont(22, .bold)
            Text(detail).appFont(15).lineSpacing(3)
            DashedCard {
                Text("이 화면은 시안에는 있고, 네이티브 앱에는 다음 빌드에서 옮겨요.").appFont(14)
            }
            Spacer()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}
