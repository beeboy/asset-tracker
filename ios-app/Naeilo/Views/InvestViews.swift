import SwiftUI

// 인물 자세히 보기의 "규칙과 결과" 갈래 (데이터는 Data/Invest.swift, 사건 그래프는 Data/InvestEvents.swift).
// 순서: 한 줄 결론 → 규칙 세 줄(+더 자세한 규칙 접기) → 과거 폭락장에서 써봤다면(세로, 최신순) → 못 막는 하락 → 내 종목에 썼다면 → 반대로 간 사람 → 기준.
// 결과는 비율·수익률로만. 매매 내역, "따라 해 보세요" 같은 말은 넣지 않는다.

struct InvestBranch: View {
    let id: String
    var focus: Int? = nil
    var toStory: () -> Void = {}
    @Environment(AppModel.self) private var m
    @State private var habitsOpen = false

    private var friend: Friend? { Shelter.friends.first { $0.id == id } }
    private var name: String { friend?.name ?? "" }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let t = Invest.summary[id] {
                Text(t).appFont(17, .bold).foregroundStyle(.white).lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.myReal, in: RoundedRectangle(cornerRadius: 16))
            }
            habits
            cases
            facts
            mine
            foil
            Text(([Invest.holdNote[id], String(localized: "현금 연 2%"), Invest.eventsSource, Invest.disclaimer] as [String?]).compactMap { $0 }.joined(separator: " · "))
                .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
        }
        .onAppear { if focus != nil { habitsOpen = true } }
    }

    // MARK: 규칙 세 줄 + 더 자세한 규칙 (접어 둔다)
    private var habits: some View {
        let more = Invest.habits[id] ?? []
        return Card {
            Text("\(name) 규칙").appFont(15, .bold)
            ForEach(Array((friend?.ruleSteps ?? []).enumerated()), id: \.offset) { i, s in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(i + 1)").appFont(12, .bold).foregroundStyle(.white)
                        .frame(width: 20, height: 20).background(Theme.rule, in: Circle())
                    Text(ParallelView.md(bold: s)).appFont(14).fixedSize(horizontal: false, vertical: true)
                }
            }
            Button { withAnimation(.easeInOut(duration: 0.2)) { habitsOpen.toggle() } } label: {
                HStack {
                    Text("자세히 \(more.count)가지").appFont(13, .bold).foregroundStyle(Theme.sub)
                    Spacer()
                    Image(systemName: habitsOpen ? "chevron.up" : "chevron.down").foregroundStyle(Theme.muted)
                }
                .frame(minHeight: 36).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(habitsOpen ? String(localized: "자세한 규칙 접기") : String(localized: "자세한 규칙 펴기"))
            if habitsOpen {
                ForEach(Array(more.enumerated()), id: \.offset) { i, h in
                    HStack(alignment: .top, spacing: 8) {
                        Text("\(i + 1)").appFont(12, .bold).foregroundStyle(Theme.rule).frame(width: 16)
                        Text(h).appFont(14, i == focus ? .bold : .regular).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.vertical, i == focus ? 6 : 0).padding(.horizontal, i == focus ? 6 : 0)
                    .background(i == focus ? Theme.tealBg : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                }
                if let n = Invest.habitNote[id] { Text(n).appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true) }
            }
        }
    }

    // MARK: 과거 폭락장에서 써봤다면 (세로 한 목록, 연도 최신순)
    @ViewBuilder private var cases: some View {
        let list = Invest.cases(id)
        if !list.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("과거 폭락장이었다면").appFont(15, .bold)
                if let p = Invest.premise[id] {
                    Text(ParallelView.md(bold: p)).appFont(13).foregroundStyle(Theme.sub).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                }
                ForEach(list) { e in CaseCard(e: e, name: name) }
            }
        }
    }

    // MARK: 이 규칙이 못 막는 하락 (fact 있는 사건만)
    @ViewBuilder private var facts: some View {
        ForEach(Invest.facts(id)) { e in
            VStack(alignment: .leading, spacing: 6) {
                Text("못 막는 하락").appFont(13, .bold).foregroundStyle(Theme.against)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(e.fact ?? "").appFont(24, .bold).foregroundStyle(Theme.against)
                    Text("\(e.title) \(e.when)").appFont(14, .semibold)
                }
                Text(e.text).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.against.opacity(0.1), in: RoundedRectangle(cornerRadius: 14))
            .accessibilityElement(children: .combine)
        }
    }

    // MARK: 같은 날, 반대로 간 사람 (소설 조연, 컷 그림은 그대로)
    @ViewBuilder private var foil: some View {
        if let f = Invest.foils[id] {
            Card {
                Text("반대로 간 사람").appFont(15, .bold)
                Text(f.did + " →").appFont(14).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                Text(f.result).appFont(14, .semibold).fixedSize(horizontal: false, vertical: true)
                HStack(alignment: .center, spacing: 12) {
                    if let s = MeSample.all.first(where: { $0.name == f.name }) {
                        MeAvatar(me: MeChar(name: s.name, parts: s.parts), width: 36, height: 51)
                            .padding(5).background(Shelter.spriteBacking(""), in: RoundedRectangle(cornerRadius: 8))
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(f.name).appFont(14, .bold)
                        Text(f.line).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    }
                }
                // 컷 글자가 그림에 박혀 있어 영어는 따로 그린 event_<인물>_en (tools/event_en)
                let art = "event_" + id + (AppModel.en ? "_en" : "")
                if MeArt.has(art) {
                    Image(art).interpolation(.none).resizable().aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .accessibilityLabel("\(f.name)의 \(f.model) 컷과 같은 날의 \(name)")
                }
                Button(action: toStory) {
                    Text("\(name) 이야기 ›").appFont(13, .bold).foregroundStyle(Theme.rule).frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: 이 규칙을 내 종목에 썼다면 (평행 세계 엔진, 같은 출발일·금액)
    @ViewBuilder private var mine: some View {
        Card {
            Text("내 종목에 썼다면").appFont(15, .bold)
            if let w = m.pworld, let race = m.parallelRun(w, who: id) {
                let s = m.parallelSeries(race)
                let ret = (id == w.who ? s[2] : race.r[2])[race.last], real = s[0][race.last]
                Text(Self.since(race.dates.first ?? "")).appFont(13).foregroundStyle(Theme.sub)
                HStack(spacing: 8) {
                    cell(String(localized: "\(String(describing: name)) 규칙"), ret, Theme.rule)
                    cell(String(localized: "내 실제"), real, Theme.myReal)
                }
                Text(ParallelView.md(bold: Self.moneyLine(gap: (race.V0 + race.contrib) * (ret - real), ret: ret, idle: race.log.isEmpty)))
                    .appFont(14).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                Text("\(name): “\(Parallel.line(id, place: ret > real ? 1 : 3, day: Day.today))”").appFont(13).lineSpacing(3)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 12))
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Button { m.tab = .home; m.homePath = ["shelter", "parallel"] } label: {
                    Text("다른 방식이었다면? ›").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 36)
                }
                .buttonStyle(.plain)
            }
        }
    }
    private func cell(_ k: String, _ v: Double, _ c: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(AppModel.sgn(v)).appFont(20, .bold).foregroundStyle(c)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
    }
    /// 금액 문장: gap = 규칙대로 평가액 − 실제 평가액 (원). 1만원 미만이면 "거의 같음".
    /// 부호 맞추기: 규칙 쪽이 손실이면 "덜 잃었을 / 더 잃었을", 이익이면 "더 벌었을 / 덜 벌었을"
    static func moneyLine(gap: Double, ret: Double, idle: Bool) -> String {
        let why = idle ? String(localized: " · 규칙 실행 0회") : ""
        if abs(gap) < 5e3 { return String(localized: "**거의 같음**") + why }
        let verb = ret < 0 ? (gap > 0 ? String(localized: "덜 잃었을") : String(localized: "더 잃었을")) : (gap > 0 ? String(localized: "더 벌었을") : String(localized: "덜 벌었을"))
        return String(localized: "**\(String(describing: AppModel.aboutWon(gap).dropLast())) \(String(describing: verb)) 것**") + why
    }
    /// 출발일 "2026-09-11" → "26.9.11~"
    static func since(_ d: String) -> String {
        guard let x = Day.date(d) else { return d }
        let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "yy.M.d"
        return f.string(from: x) + "~"
    }
}

/// 사례 카드: 이름·기간, 유리/불리 배지(끝값 비교), 막대 두 줄, 한 줄 설명
struct CaseCard: View {
    let e: Invest.Event
    let name: String

    var body: some View {
        let a = e.ruleRet, b = e.holdRet, top = max(abs(a), abs(b), 0.001)
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(e.title).appFont(15, .bold).lineLimit(1).minimumScaleFactor(0.8)
                Text(e.when).appFont(12).foregroundStyle(Theme.sub)
                Spacer(minLength: 4)
                Text(e.ruleWins ? String(localized: "유리") : String(localized: "불리")).appFont(11, .bold).foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(e.ruleWins ? Theme.favor : Theme.against, in: Capsule())
            }
            bar(name, a, a / top, Theme.rule)
            bar(String(localized: "그냥 둠"), b, b / top, Theme.hold)
            Text(e.text).appFont(13).lineSpacing(2).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
        .accessibilityElement(children: .combine)
    }
    private func bar(_ k: String, _ v: Double, _ w: Double, _ c: Color) -> some View {
        HStack(spacing: 8) {
            Text(k).appFont(12).foregroundStyle(Theme.sub).frame(width: 64, alignment: .leading)
            GeometryReader { g in
                RoundedRectangle(cornerRadius: 3).fill(c).frame(width: max(3, g.size.width * CGFloat(abs(w))))
            }
            .frame(height: 10)
            Text(AppModel.sgn(v)).appFont(13, .bold).foregroundStyle(v >= 0 ? Theme.up : Theme.down).frame(width: 62, alignment: .trailing)
        }
    }
}
