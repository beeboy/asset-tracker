import SwiftUI

// 인물 자세히 보기의 "투자하는 ○○" 갈래 (데이터는 Data/Invest.swift, 사건 그래프는 Data/InvestEvents.swift).
// 순서: 대표 규칙 → 버릇(접기) → 강했던 때/약했던 때 (좌우로 넘김) → 반대로 간 사람 → 내 종목으로 돌려 보면 → 이 인물의 말 → 면책.
// 결과는 비율·수익률로만. 매매 내역, "따라 해 보세요" 같은 말은 넣지 않는다.

struct InvestBranch: View {
    let id: String
    var focus: Int? = nil
    @Environment(AppModel.self) private var m
    @State private var habitsOpen = false

    private var name: String { Shelter.friends.first { $0.id == id }?.name ?? "" }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Card {
                Text("대표 규칙").appFont(12, .bold).foregroundStyle(Theme.teal)
                Text(Parallel.rules[id] ?? "").appFont(20, .bold).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
            }
            habits
            events(strong: true)
            events(strong: false)
            foil
            mine
            quotes
            Text(Invest.disclaimer).appFont(12).foregroundStyle(Theme.muted)
        }
        .onAppear { if focus != nil { habitsOpen = true } }
    }

    // MARK: 버릇 (세부 규칙, 접어 둔다)
    private var habits: some View {
        Card {
            Button { withAnimation(.easeInOut(duration: 0.2)) { habitsOpen.toggle() } } label: {
                HStack {
                    Text("\(name)의 버릇").appFont(15, .bold)
                    Spacer()
                    Image(systemName: habitsOpen ? "chevron.up" : "chevron.down").foregroundStyle(Theme.muted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(habitsOpen ? "버릇 접기" : "버릇 펴기")
            if habitsOpen {
                ForEach(Array((Invest.habits[id] ?? []).enumerated()), id: \.offset) { i, h in
                    HStack(alignment: .top, spacing: 8) {
                        Text("\(i + 1)").appFont(12, .bold).foregroundStyle(Theme.teal).frame(width: 16)
                        Text(h).appFont(14, i == focus ? .bold : .regular).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.vertical, i == focus ? 6 : 0).padding(.horizontal, i == focus ? 6 : 0)
                    .background(i == focus ? Theme.tealBg : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                }
                if let n = Invest.habitNote[id] { Text(n).appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true) }
                Text("평행 세계의 \(name) 세계도 이 버릇대로 움직여요.").appFont(12).foregroundStyle(Theme.sub)
            } else {
                Text("대표 규칙 밑의 세부 규칙 \(Invest.habits[id]?.count ?? 0)가지").appFont(13).foregroundStyle(Theme.sub)
            }
        }
    }

    // MARK: 강했던 때 / 약했던 때 (같은 크기로)
    private func events(strong: Bool) -> some View {
        let list = Invest.events(id, strong: strong)
        return VStack(alignment: .leading, spacing: 8) {
            Text(strong ? "강했던 때" : "약했던 때").appFont(15, .bold)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 10) {
                    ForEach(list) { e in EventCard(e: e, color: strong ? Theme.teal : Theme.orange).frame(width: 290) }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
            .contentMargins(.horizontal, 0)
            if strong {
                Text((Invest.holdNote[id] ?? "") + ". 현금은 연 2%로 셈. " + Invest.eventsSource)
                    .appFont(11).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: 반대로 간 사람 (소설 조연, 4컷은 디자인 시안)
    @ViewBuilder private var foil: some View {
        if let f = Invest.foils[id] {
            Card {
                Text("같은 사건, 반대로 간 사람").appFont(15, .bold)
                HStack(alignment: .top, spacing: 12) {
                    if let s = MeSample.all.first(where: { $0.name == f.name }) {
                        MeAvatar(me: MeChar(name: s.name, parts: s.parts), width: 48, height: 68)
                            .padding(6).background(Shelter.spriteBacking(""), in: RoundedRectangle(cornerRadius: 10))
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(f.name).appFont(16, .bold)
                        Text(f.line).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        Text(f.model).appFont(12, .bold).foregroundStyle(Theme.orange)
                    }
                }
                Text(f.did).appFont(14).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                Text(f.result).appFont(14, .semibold).fixedSize(horizontal: false, vertical: true)
                if MeArt.has("event_" + id) {
                    Image("event_" + id).interpolation(.none).resizable().aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .accessibilityLabel("\(f.name)의 \(f.model) 네 컷과 같은 날의 \(name)")
                }
            }
        }
    }

    // MARK: 내 종목으로 돌려 보면 (평행 세계 엔진, 같은 출발일·금액)
    @ViewBuilder private var mine: some View {
        Card {
            Text("내 종목으로 돌려 보면").appFont(15, .bold)
            if let w = m.pworld, let race = m.parallelRun(w, who: id) {
                let r2 = id == w.who ? m.parallelSeries(race)[2] : race.r[2]
                let ret = r2[race.last], real = m.parallelSeries(race)[0][race.last]
                let amount = (race.V0 + race.contrib) * (1 + ret)
                Text("\(ParallelView.md(race.dates.first ?? ""))부터 지금 내 종목을 \(name) 규칙으로 굴렸어요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                stat("수익률", AppModel.sgn(ret), ret >= 0 ? Theme.up : Theme.down)
                stat("최대 낙폭", AppModel.pct(Invest.maxDrawdown(r2)), Theme.ink)
                stat(m.isGoal ? "목표 거리" : "본전 거리", m.parallelDistance(amount), Theme.ink)
                Text("같은 기간 진짜 나는 \(AppModel.sgn(real)).").appFont(12).foregroundStyle(Theme.muted)
            } else {
                Button { m.tab = .home; m.homePath = ["shelter", "parallel"] } label: {
                    Text("평행 세계에서 보기 ›").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 36)
                }
                .buttonStyle(.plain)
            }
        }
    }
    private func stat(_ k: String, _ v: String, _ c: Color) -> some View {
        HStack {
            Text(k).appFont(14).foregroundStyle(Theme.sub)
            Spacer()
            Text(v).appFont(15, .bold).foregroundStyle(c)
        }
    }

    // MARK: 이 인물의 말
    @ViewBuilder private var quotes: some View {
        let key = Invest.quoteKey[id] ?? ""
        let a = Lines.pick(key, salt: 31), b = Lines.pick(key, salt: 32)
        let lines = [a, b != a ? b : nil].compactMap { $0 }
        if !lines.isEmpty {
            Card {
                Text("\(name)의 말").appFont(15, .bold)
                ForEach(lines, id: \.self) { l in
                    Text(l).appFont(14).lineSpacing(3)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.mintBg, in: UnevenRoundedRectangle(topLeadingRadius: 4, bottomLeadingRadius: 14, bottomTrailingRadius: 14, topTrailingRadius: 14))
                }
            }
        }
    }
}

/// 사건 카드: 이름·시기·한두 줄 + 미니 그래프 (규칙대로 vs 그냥 둠). fact 카드는 큰 숫자 하나
struct EventCard: View {
    let e: Invest.Event
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(e.title).appFont(15, .bold).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 4)
                Text(e.when).appFont(12).foregroundStyle(Theme.sub)
            }
            if let f = e.fact {
                Text(f).appFont(30, .bold).foregroundStyle(color).frame(maxWidth: .infinity, minHeight: 90)
            } else {
                MiniLines(a: e.rule, b: e.hold, color: color).frame(height: 90).accessibilityHidden(true)
                HStack(spacing: 10) {
                    legend(color, "규칙대로 " + AppModel.sgn(e.ruleRet), dash: false)
                    legend(Theme.muted, "그냥 둠 " + AppModel.sgn(e.holdRet), dash: true)
                }
            }
            Text(e.text).appFont(13).lineSpacing(2).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
        .accessibilityElement(children: .combine)
    }
    private func legend(_ c: Color, _ t: String, dash: Bool) -> some View {
        HStack(spacing: 4) {
            Path { p in p.move(to: CGPoint(x: 0, y: 2)); p.addLine(to: CGPoint(x: 14, y: 2)) }
                .stroke(c, style: StrokeStyle(lineWidth: 2.5, dash: dash ? [3, 2] : [])).frame(width: 14, height: 4)
            Text(t).appFont(12, .bold).foregroundStyle(dash ? Theme.sub : c)
        }
    }
}

/// 두 줄 미니 그래프 (같은 눈금, 100 = 출발 점선)
struct MiniLines: View {
    let a: [Double]
    let b: [Double]
    let color: Color
    var body: some View {
        GeometryReader { g in
            let all = a + b + [100]
            let lo = all.min() ?? 0, hi = all.max() ?? 1, span = max(1e-6, hi - lo)
            let y = { (v: Double) in g.size.height * (1 - (v - lo) / span) }
            ZStack {
                Path { p in p.move(to: CGPoint(x: 0, y: y(100))); p.addLine(to: CGPoint(x: g.size.width, y: y(100))) }
                    .stroke(Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                line(b, g.size, y).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1.8, lineJoin: .round, dash: [3, 2]))
                line(a, g.size, y).stroke(color, style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
            }
        }
    }
    private func line(_ v: [Double], _ s: CGSize, _ y: (Double) -> CGFloat) -> Path {
        Path { p in
            guard v.count > 1 else { return }
            for (i, x) in v.enumerated() {
                let pt = CGPoint(x: s.width * CGFloat(i) / CGFloat(v.count - 1), y: y(x))
                if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
        }
    }
}
