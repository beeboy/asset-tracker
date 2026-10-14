import SwiftUI

// 내 길 개편 3단계 (B-2): 비중을 5%씩 바꿔 보며 목표에 처음 닿는 때를 지금 구성과 비교한다.
// 계산은 GoalRoute.simulate(parts:) — 같은 종목 묶음이면 경로를 다시 쓰고 비중만 바꾼다.
// 합계가 100%가 아니면 비율대로 맞춰 계산하고, 저장은 100%일 때만. 저장 = 모의 바구니(담아 본 나), 실제 수량은 그대로

struct MyPathCompareView: View {
    @Environment(AppModel.self) private var m
    @State private var w: [String: Int] = [:]
    @State private var start: [String: Int] = [:]
    @State private var saved = false

    var body: some View {
        let sum = w.values.reduce(0, +)
        let yl = max(1, Int(ceil(AppModel.yrs(Day.today, Day.str(m.goalToDate)))))
        let base = m.simulate(m.mineMix, years: yl)
        let alt = sum > 0 ? m.simulate(.init(id: "try", name: "바꾼 비중", tag: "", desc: "", comp: [:], basket: nil), parts: parts(sum), years: yl) : base
        let goalMo = max(0, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12)
        let cap = Double(max(base.capYears, alt.capYears) * 12)
        let ends = [base, alt].flatMap { [$0.etaLo, $0.etaHi] }.map { $0.isFinite ? $0 : cap } + [goalMo]
        let x0 = max(0, (ends.min() ?? 0) - 6), x1 = max(x0 + 12, (ends.max() ?? cap) + 6)
        let X = { (mo: Double) in (min(cap, mo.isFinite ? mo : cap) - x0) / (x1 - x0) }
        let y0 = Calendar.current.component(.year, from: Date())
        let canSave = m.pworld != nil && sum == 100
        let noHist = w.filter { $0.value > 0 && !Parallel.has($0.key) }.keys.sorted()

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("비중을 바꾸면 목표에 처음 닿는 때가 어떻게 달라지는지 봐요. 실제 수량은 바뀌지 않아요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                Card {
                    HStack {
                        Text("비중").appFont(15, .bold); Spacer()
                        Button("처음으로") { w = start }.appFont(13, .semibold).tint(Theme.teal).disabled(w == start)
                    }
                    ForEach(w.keys.sorted { (start[$0] ?? -1, $1) > (start[$1] ?? -1, $0) }, id: \.self) { k in row(k) }
                    Menu {
                        ForEach(Parallel.universe.filter { w[$0.id] == nil }, id: \.id) { u in
                            Button(u.id + " · " + u.name) { w[u.id] = 0 }
                        }
                    } label: {
                        Text("+ 종목 추가").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 40)
                    }
                    HStack {
                        Text("합계").appFont(14, .bold); Spacer()
                        Text("\(sum)%").appFont(14, .bold).foregroundStyle(sum == 100 ? Theme.green : Theme.orange)
                    }
                    if sum != 100 && sum > 0 { Text("합이 100%가 아니라 비율대로 맞춰 계산해요. 저장은 100%일 때만 돼요.").appFont(12).foregroundStyle(Theme.orange) }
                }
                Card {
                    Text("언제쯤 닿을까").appFont(15, .bold)
                    Text("점은 보통, 막대는 10번 중 8번이 닿는 때예요").appFont(12).foregroundStyle(Theme.sub)
                    line("지금", base, Theme.teal, X: X, y0: y0)
                    line("바꾼 비중", alt, Theme.purple, X: X, y0: y0)
                    HStack { Text("\(y0 + Int(x0 / 12))년"); Spacer(); Text("점선 목표일"); Spacer(); Text("\(y0 + Int(x1 / 12))년") }.appFont(11).foregroundStyle(Theme.muted)
                }
                .animation(.easeInOut(duration: 0.4), value: w)
                Card {
                    Text("어떻게 달라졌나").appFont(15, .bold)
                    Text(read(base, alt, goalMo: goalMo, y0: y0)).appFont(13).foregroundStyle(Theme.ink).fixedSize(horizontal: false, vertical: true)
                }
                Button {
                    m.saveBasket(w.filter { $0.value > 0 && Parallel.has($0.key) }.mapValues { Double($0) })
                    saved = true
                } label: {
                    Text(saved ? "모의 바구니에 담았어요" : "이 비중으로 계획 저장").appFont(15, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(canSave && !saved ? Theme.teal : Theme.muted, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain).disabled(!canSave || saved)
                Text(m.pworld == nil ? "저장은 '담아 본 나'를 시작한 뒤에 할 수 있어요."
                     : "저장하면 모의 바구니(담아 본 나)가 오늘부터 이 비중으로 새로 기록해요." + (noHist.isEmpty ? "" : " 지난 종가가 없는 \(noHist.joined(separator: ", "))은(는) 빼고 담아요."))
                    .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                Text("모의 계산이며 투자 권유가 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("비중 바꿔 보기").navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard start.isEmpty else { return }
            var x: [String: Int] = [:]
            for r in m.rows { x[r.id, default: 0] += Int((m.weight(r.id) * 100).rounded()) }
            start = x; w = x
        }
        .onChange(of: w) { saved = false }
    }

    /// 계산용 종목·비중 (합이 100%가 아니면 비율대로 맞춘다). 종목에 없는 추가 종목은 달러 ETF·주식
    private func parts(_ sum: Int) -> [AppModel.Part] {
        w.keys.sorted().compactMap { k in
            guard let v = w[k], v > 0 else { return nil }
            let ccy = m.rows.first { $0.id == k }.map { $0.sym.currency == .usd ? "USD" : "KRW" } ?? "USD"
            return (key: SiteStats.mcKey(k), ccy: ccy, w: Double(v) / Double(sum))
        }
    }

    private func row(_ k: String) -> some View {
        let v = w[k] ?? 0
        let name = m.rows.first { $0.id == k }?.sym.name ?? Parallel.name(k)
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(name).appFont(14, .bold).lineLimit(1)
                Text("지금 \(start[k] ?? 0)%").appFont(11).foregroundStyle(Theme.sub)
            }
            Spacer()
            Button { w[k] = max(0, v - 5) } label: { Image(systemName: "minus").frame(width: 36, height: 36) }
                .buttonStyle(.plain).accessibilityLabel("\(name) 5%p 줄이기")
            Text("\(v)%").appFont(15, .bold).frame(minWidth: 48).foregroundStyle(v == start[k] ?? 0 ? Theme.ink : Theme.purple)
            Button { w[k] = min(100, v + 5) } label: { Image(systemName: "plus").frame(width: 36, height: 36) }
                .buttonStyle(.plain).accessibilityLabel("\(name) 5%p 늘리기")
        }
    }

    private func line(_ t: String, _ s: GoalSim, _ c: Color, X: @escaping (Double) -> Double, y0: Int) -> some View {
        let open = !s.etaHi.isFinite
        let goalMo = max(0, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(t).appFont(13, .semibold); Spacer()
                Text(s.etaMonths.isFinite ? AppModel.whenText(s.etaMonths) : "\(y0 + s.capYears)년 이후").appFont(13, .bold).foregroundStyle(c)
            }
            GeometryReader { g in
                let wd = g.size.width, a = X(s.etaLo) * wd, b = X(s.etaHi) * wd
                ZStack(alignment: .leading) {
                    Path { $0.move(to: CGPoint(x: X(goalMo) * wd, y: 0)); $0.addLine(to: CGPoint(x: X(goalMo) * wd, y: 14)) }
                        .stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
                    Capsule().fill(c.opacity(0.3)).frame(width: max(4, b - a), height: 8).offset(x: a)
                    if s.etaMonths.isFinite { Circle().fill(c).frame(width: 10, height: 10).offset(x: X(s.etaMonths) * wd - 5) }
                }
                .frame(height: 14)
            }
            .frame(height: 14)
            Text(AppModel.whenText(s.etaLo) + " ~ " + (open ? "\(y0 + s.capYears)년 이후" : AppModel.whenText(s.etaHi))).appFont(11).foregroundStyle(Theme.muted)
        }
        .accessibilityElement(children: .combine)
    }

    /// 해석: 바꾼 보통 시점 vs 목표일·지금 구성, 막대 폭(10~90%)으로 흔들림
    private func read(_ b: GoalSim, _ a: GoalSim, goalMo: Double, y0: Int) -> String {
        guard a.etaMonths.isFinite else { return "바꾼 비중으로는 보통의 경우 \(y0 + a.capYears)년 안에 목표에 닿지 못해요." }
        let vsGoal = a.etaMonths <= goalMo ? "목표일보다 먼저 닿는 편이에요" : "목표일보다 늦게 닿는 편이에요"
        let d = b.etaMonths.isFinite ? a.etaMonths - b.etaMonths : -.infinity
        let vsNow = abs(d) < 3 ? "지금 구성과 거의 같아요" : d < 0 ? "지금 구성보다 \(AppModel.eta(-d).replacingOccurrences(of: " 뒤", with: "")) 빨라요" : "지금 구성보다 \(AppModel.eta(d).replacingOccurrences(of: " 뒤", with: "")) 늦어요"
        let wA = (a.etaHi.isFinite ? a.etaHi : Double(a.capYears * 12)) - a.etaLo, wB = (b.etaHi.isFinite ? b.etaHi : Double(b.capYears * 12)) - b.etaLo
        let spread = wA > wB * 1.2 ? "빠를 때와 늦을 때 차이가 커져요" : wA < wB * 0.8 ? "닿는 때가 더 고르게 모여요" : "흔들림은 비슷해요"
        return "보통 \(AppModel.whenText(a.etaMonths)), \(vsGoal). \(vsNow). \(spread)."
    }
}
