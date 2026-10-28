import SwiftUI

// 목표일 전망 화면 조각: 선형 축 범위 그래프, 일정 한 줄 (외부 요인 화면도 같이 쓸 수 있게 따로 둔다)

/// 선형 축 범위 그래프: 고른 시나리오의 50%·90% 띠와 중앙값, 다른 시나리오 중앙값(회색 점선), 목표선, 지금 점
struct ScenarioChart: View {
    let sel: AppModel.Forecast
    /// 고른 시나리오 값에 더할 금액 (몬테카를로 경로에 없는 현금)
    let selShift: Double
    let selLabel: String
    let others: [(label: String, med: [Double])]
    let goal: Double
    let goalLabel: String
    let endLabel: String

    /// 눈금 간격: 위 끝까지 눈금이 3칸 이하가 되는 가장 작은 값 (5천만, 1억, 2.5억, 5억, 10억 …)
    /// 맨 위 눈금 글자가 반쯤 잘리지 않게 비우는 높이
    static let topPad: CGFloat = 8

    static func step(_ top: Double) -> Double {
        var s = 5e7
        let mult: [Double] = [2, 2.5, 2]
        var i = 0
        while top / s > 3 { s *= mult[i % 3]; i += 1 }
        return s
    }
    static func axis(_ v: Double) -> String {
        if v <= 0 { return "0" }
        let e = v / 1e8
        return e == e.rounded() ? String(localized: "\(String(describing: Int(e)))억") : String(format: String(localized: "%.1f억"), e)
    }

    /// 지금 점 자리 (박동 원을 그래프 위에 따로 얹는 데 쓴다). body 의 x·y 와 같은 셈
    func nowPoint(_ size: CGSize) -> CGPoint? {
        guard sel.q50.count > 1, let t0 = sel.tY.first else { return nil }
        let sh = selShift
        let top0 = max(goal, sel.q95.map { $0 + sh }.max() ?? 1, others.flatMap(\.med).max() ?? 0) * 1.05
        let st = Self.step(top0), top = max(st, (top0 / st).rounded(.up) * st)
        let left: CGFloat = 36, W = size.width - left, H = size.height - 20 - Self.topPad
        let span = max(1e-9, sel.tY.last ?? 1), v = sel.q50[0] + sh
        return CGPoint(x: left + CGFloat(t0 / span) * W, y: Self.topPad + H - CGFloat(min(max(v, 0), top) / top) * H)
    }

    var body: some View {
        Canvas { ctx, size in
            let n = sel.q50.count - 1
            guard n > 0 else { return }
            let sh = selShift
            let q5 = sel.q5.map { $0 + sh }, q25 = sel.q25.map { $0 + sh }, q50 = sel.q50.map { $0 + sh }
            let q75 = sel.q75.map { $0 + sh }, q95 = sel.q95.map { $0 + sh }
            let top0 = max(goal, q95.max() ?? 1, others.flatMap(\.med).max() ?? 0) * 1.05
            let st = Self.step(top0), top = max(st, (top0 / st).rounded(.up) * st)
            let left: CGFloat = 36, bottom: CGFloat = 20, W = size.width - left, H = size.height - bottom - Self.topPad
            let span = max(1e-9, sel.tY.last ?? 1)
            let x = { (i: Int) -> CGFloat in left + CGFloat(sel.tY[i] / span) * W }
            let y = { (v: Double) -> CGFloat in Self.topPad + H - CGFloat(min(max(v, 0), top) / top) * H }
            func line(_ a: [Double]) -> Path {
                Path { p in for i in 0...min(n, a.count - 1) { let pt = CGPoint(x: x(i), y: y(a[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } }
            }
            func band(_ a: [Double], _ b: [Double]) -> Path {
                Path { p in
                    for i in 0...n { let pt = CGPoint(x: x(i), y: y(a[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                    for i in stride(from: n, through: 0, by: -1) { p.addLine(to: CGPoint(x: x(i), y: y(b[i]))) }
                    p.closeSubpath()
                }
            }
            // 눈금 (가로선 + 왼쪽 값)
            var v = 0.0
            while v <= top + 1 {
                let yy = y(v)
                ctx.stroke(Path { $0.move(to: CGPoint(x: left, y: yy)); $0.addLine(to: CGPoint(x: size.width, y: yy)) }, with: .color(Theme.line), lineWidth: 1)
                ctx.draw(Text(Self.axis(v)).font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: left - 4, y: yy), anchor: .trailing)
                v += st
            }
            // 띠와 선
            ctx.fill(band(q95, q5), with: .color(Theme.teal.opacity(0.14)))
            ctx.fill(band(q75, q25), with: .color(Theme.teal.opacity(0.30)))
            var tags: [(text: String, y: CGFloat, sel: Bool)] = []
            for o in others where o.med.count > n {
                ctx.stroke(line(o.med), with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1.4, dash: [4, 3]))
                tags.append((o.label, y(o.med[n]) - 7, false))
            }
            ctx.stroke(line(q50), with: .color(Theme.tealDark), lineWidth: 2.4)
            tags.append((selLabel, y(q50[n]) - 8, true))
            // 끝값이 가까우면 이름표가 겹친다: 위에서부터 12pt 이상 벌린다
            tags.sort { $0.y < $1.y }
            for i in tags.indices.dropFirst() where tags[i].y - tags[i - 1].y < 12 { tags[i].y = tags[i - 1].y + 12 }
            for t in tags {
                ctx.draw(Text(t.text).font(.system(size: 10, weight: t.sel ? .bold : .regular)).foregroundStyle(t.sel ? Theme.tealDark : Theme.muted),
                         at: CGPoint(x: size.width - 2, y: t.y), anchor: .trailing)
            }
            // 목표선
            let gy = y(goal)
            ctx.stroke(Path { $0.move(to: CGPoint(x: left, y: gy)); $0.addLine(to: CGPoint(x: size.width, y: gy)) },
                       with: .color(Theme.orange), style: StrokeStyle(lineWidth: 1.6, dash: [5, 4]))
            ctx.draw(Text(goalLabel).font(.system(size: 10, weight: .semibold)).foregroundStyle(Theme.orange),
                     at: CGPoint(x: left + 4, y: gy < 12 ? gy + 8 : gy - 8), anchor: .leading)
            // 지금 점
            let p0 = CGPoint(x: x(0), y: y(q50[0]))
            ctx.fill(Path(ellipseIn: CGRect(x: p0.x - 4, y: p0.y - 4, width: 8, height: 8)), with: .color(Theme.ink))
            ctx.draw(Text("지금 \(ForecastView.amt(q50[0]))").font(.system(size: 10, weight: .semibold)).foregroundStyle(Theme.ink),
                     at: CGPoint(x: p0.x + 6, y: p0.y + 12), anchor: .leading)
            // X축: 지금 / 1년 뒤 / 2년 뒤 / 끝날
            let ly = size.height - 6
            ctx.draw(Text("지금").font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: left, y: ly), anchor: .leading)
            for yr in 1...max(1, Int(span)) where Double(yr) < span - 0.4 && yr <= 4 {
                let xx = left + CGFloat(Double(yr) / span) * W
                ctx.draw(Text("\(yr)년 뒤").font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: xx, y: ly))
            }
            ctx.draw(Text(endLabel).font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: size.width, y: ly), anchor: .trailing)
        }
        .accessibilityLabel("시나리오별 자산 범위 그래프")
        .pulse(Theme.ink, dot: 8) { s in nowPoint(s) }
    }
}

/// 일정 한 줄: 날짜(D-day) + 이름. 데이터는 MarketEvents (전망 계산과 같은 목록)
struct ScheduleRow: View {
    let hit: MarketEvents.Hit
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(MarketEvents.when(hit.day)).appFont(12, .bold).foregroundStyle(Theme.teal).frame(width: 92, alignment: .leading)
                Text(hit.kind).appFont(13).foregroundStyle(Theme.ink)
                Spacer(minLength: 0)
            }
            // 금리 결정 행 아래에만 금리 충격 테스트로 가는 버튼
            if MarketEvents.isRate(hit.id) {
                NavigationLink(value: AnalysisRoute.rateScen) {
                    Text("금리 오르면? ›").appFont(13, .bold).foregroundStyle(Theme.purple)
                        .padding(.horizontal, 12).padding(.vertical, 7)
                        .background(Theme.purple.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain).padding(.leading, 102)
            }
        }
        .padding(.vertical, 2)
    }
}
