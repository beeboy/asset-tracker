import SwiftUI

// 평정 지수 (가위바위보 지수): 분석 탭 맨 위 카드 + 상세. 계산은 Data/Calm.swift
// 앱은 지수·내 인물·최근 기록만 짧게, 사건별 표와 추이 그래프는 두지 않는다.

struct CalmCard: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        HStack(alignment: .center, spacing: 12) {
            CharPixel(id: who, width: 42, height: 60).charIdle()
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text("내 투자 습관").appFont(13, .bold).foregroundStyle(Theme.mint)
                    if let k { Text("· \(k.title)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                    Spacer()
                    Text(s.n < Calm.minEvents ? "–" : "\(s.index)").appFont(28, .bold).tracking(-0.5).roll(s.n < Calm.minEvents ? "–" : "\(s.index)")
                }
                Text(m.calm.say(s, who: m.voice(who))).appFont(14).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
                    .foregroundStyle(Color(hex: 0xEEF0F3))
                Text(Calm.meaning(s)).appFont(12).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
                    .foregroundStyle(Color(hex: 0xC9D0D6))
                if let l = m.calm.ladder, let p = m.calm.progress() {
                    Text("걸음 \(l.step)/3 · \(p.text)").appFont(12).foregroundStyle(Theme.mint)
                }
            }
        }
        // 홈의 지금 평가액 카드처럼 진한 바탕
        .foregroundStyle(.white)
        .padding(.horizontal, 14).padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
        .modifier(OpenOnTap(route: .calm))
        .accessibilityLabel("내 투자 습관 보기")
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
    }
}

struct CalmView: View {
    @Environment(AppModel.self) private var m
    @State private var shown = 10   // 최근 기록을 몇 건까지 펼쳤는지
    @State private var recentOpen = false   // 최근 기록 목록은 접어 둔다 (2026-10-21)

    /// 다섯 인물을 가로 한 줄에 둘 때 짧은 이름
    static let short: [String: String] = [
        "ir": String(localized: "앞서 달림"), "sua": String(localized: "자람"), "seonbae": String(localized: "되물음"),
        "sio": String(localized: "기다림"), "seri": String(localized: "원칙"),
    ]

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        let name = m.friendName(who)
        let ready = s.n >= Calm.minEvents
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 지수 하나를 크게 (진한 바탕). 모으는 중이면 기록 수 (기록이 적으면 지수는 50 근처라 막대로 보이지 않는다)
                Card(dark: true) {
                    HStack(alignment: .center, spacing: 14) {
                        CharPixel(id: who, width: 56, height: 80).charIdle(3)
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(alignment: .firstTextBaseline, spacing: 6) {
                                Text(ready ? "\(s.index)" : "\(s.n)").appFont(40, .heavy).tracking(-1).roll(ready ? "\(s.index)" : "\(s.n)")
                                Text(ready ? "/ 100" : "/ \(Calm.minEvents)").appFont(15, .semibold).foregroundStyle(Theme.muted)
                            }
                            ProgressBar(value: ready ? Double(s.index) / 100 : Double(s.n) / Double(Calm.minEvents), fill: Theme.mint, track: Theme.track)
                            HStack {
                                Text(ready ? String(localized: "끌림") : String(localized: "매매 기록")).appFont(11)
                                Spacer()
                                Text(ready ? String(localized: "규칙대로") : String(localized: "모이면 공개")).appFont(11)
                            }
                            .foregroundStyle(Theme.muted)
                        }
                    }
                    if let k, let f = Shelter.friends.first(where: { $0.id == m.calm.type }) {
                        NavigationLink { CharacterDetailView(friend: f, invest: true) } label: {
                            Text("\(name) · \(k.title) ›").appFont(15, .bold)
                        }
                        .buttonStyle(.plain)
                    }
                    Text("“\(m.calm.say(s, who: m.voice(who)))”").appFont(13).lineSpacing(2).foregroundStyle(Theme.mint)
                        .fixedSize(horizontal: false, vertical: true)
                }

                // 네 가지 습관을 막대로. 앞의 둘은 60% 점선을 넘으면 앞서 달리는 형 (10건 전에는 빈 막대와 —)
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        Text("무엇이 끌렸나").appFont(15, .bold)
                        Spacer()
                        Text(ready ? String(localized: "매매 \(s.trades)건") : String(localized: "\(Calm.minEvents)건부터")).appFont(12).foregroundStyle(Theme.muted)
                    }
                    bar(String(localized: "오르면 더 삼"), s.chase, ready, Theme.orange, line: true)
                    bar(String(localized: "내리면 팖"), s.switchR, ready, Theme.orange.opacity(0.45), line: true)
                    bar(String(localized: "급변일 기다림"), s.waitR, ready, Theme.teal)
                    bar(String(localized: "규칙대로"), s.ruleR, ready, Theme.teal)
                    Text("┆ 60% 넘으면 \(Calm.kind("ir")?.title ?? "")").appFont(11).foregroundStyle(Theme.orange)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }

                // 다음 걸음 (잠겼으면 점선 상자)
                if let l = m.calm.ladder, let steps = Calm.steps[l.type] { ladder(l, steps) }
                else {
                    Text("인물 정해지면 다음 걸음 3개")
                        .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [5, 4])))
                }

                // 최근 기록: 칸 색으로 한눈에, 머리를 누르면 목록을 펼친다
                Card {
                    Button { recentOpen.toggle() } label: {
                        HStack(alignment: .firstTextBaseline) {
                            Text("최근 기록").appFont(15, .bold)
                            Spacer()
                            Text("\(m.calm.events.count)건").appFont(12).foregroundStyle(Theme.muted)
                            Image(systemName: recentOpen ? "chevron.up" : "chevron.down").appFont(11).foregroundStyle(Theme.muted)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain).foregroundStyle(Theme.ink)
                    if !m.calm.events.isEmpty {
                        let ev = Array(m.calm.events.suffix(28))
                        VStack(alignment: .leading, spacing: 5) {
                            ForEach(Array(stride(from: 0, to: ev.count, by: 14)), id: \.self) { i in
                                HStack(spacing: 5) {
                                    ForEach(i..<min(i + 14, ev.count), id: \.self) { j in
                                        RoundedRectangle(cornerRadius: 4).fill(Self.tint(ev[j].act)).frame(width: 16, height: 16)
                                    }
                                }
                            }
                        }
                        .accessibilityHidden(true)
                        HStack(spacing: 10) {
                            legend(Self.tint(.chase), String(localized: "더 삼"))
                            legend(Self.tint(.switchOut), String(localized: "팖"))
                            legend(Self.tint(.wait), String(localized: "기다림·규칙"))
                            legend(Self.tint(.fresh), String(localized: "새 종목"))
                        }
                    }
                    if recentOpen {
                    ForEach(Array(m.calm.events.suffix(shown).reversed().enumerated()), id: \.offset) { _, e in
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(e.sym) · \(e.label)").appFont(13)
                                Text(Calm.reason(e.act).isEmpty ? Day.md(e.day) : "\(Day.md(e.day)) · \(Calm.reason(e.act))").appFont(12).foregroundStyle(Theme.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer()
                            Text(e.pts == 0 ? "0" : (e.pts > 0 ? "+" : "") + "\(e.pts)").appFont(13, .bold)
                                .foregroundStyle(e.pts > 0 ? Theme.teal : e.pts < 0 ? Theme.up : Theme.muted)
                        }
                    }
                    if m.calm.events.count > shown {
                        Button("더 보기 (\(m.calm.events.count - shown)건 남음)") { shown += 20 }
                            .appFont(13, .semibold).foregroundStyle(Theme.teal)
                    }
                    // 손으로 남기는 기록은 없다: 수량 고치기(더 샀어요·팔았어요)와 크게 움직인 날 앱 열기로 생긴다
                    Text("수량 바꾸면 기록").appFont(12).foregroundStyle(Theme.muted)
                    }
                }

                MyRecordCard()   // 종목 탭 맨 아래에서 옮겼다 (2026-10-21)

                // 다섯 인물: 가로 한 줄, 지금 인물은 테두리
                Card {
                    Text("닮을 인물").appFont(15, .bold)
                    HStack(alignment: .top, spacing: 0) {
                        ForEach(Calm.kinds, id: \.id) { kd in
                            let on = kd.id == m.calm.type
                            NavigationLink {
                                if let f = Shelter.friends.first(where: { $0.id == kd.id }) { CharacterDetailView(friend: f, invest: true) }
                            } label: {
                                VStack(spacing: 4) {
                                    Pixel(name: "spr_" + kd.id, width: 21, height: 30)
                                        .frame(width: 44, height: 44)
                                        .background(on ? Theme.teal.opacity(0.12) : Theme.line, in: Circle())
                                        .overlay(Circle().stroke(on ? Theme.teal : .clear, lineWidth: 2))
                                    Text(Self.short[kd.id] ?? kd.title).appFont(11, on ? .bold : .regular)
                                        .foregroundStyle(on ? Theme.teal : Theme.sub).lineLimit(1)
                                }
                                .frame(maxWidth: .infinity)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel((Shelter.friends.first { $0.id == kd.id }?.name ?? "") + " · " + kd.title)
                        }
                    }
                }
            }
            .padding(16)
            .foregroundStyle(Theme.ink)
        }
        .background(Theme.bg)
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
        .modifier(DetailTitle(title: String(localized: "결과에 끌려 사고팔까?"))).toolbarTitleDisplayMode(.inlineLarge)
    }

    /// 지금 유형의 걸음 3개를 가로 한 줄 칸으로: 완료한 날, 지금 걸음 진행. 인물 순위는 보여 주지 않는다
    private func ladder(_ l: Ladder, _ steps: [Calm.Step]) -> some View {
        let p = m.calm.progress()
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("다음 걸음은?").appFont(15, .bold)
                Spacer()
                Text(l.step > 3 ? String(localized: "다 올랐어요") : "\(l.step)/3").appFont(13, .semibold).foregroundStyle(Theme.teal)
            }
            HStack(alignment: .top, spacing: 6) {
                ForEach(Array(steps.enumerated()), id: \.offset) { i, st in
                    let n = i + 1, on = n == l.step, did = n < l.step
                    let day = l.done.last { $0.type == l.type && $0.step == n }?.day
                    VStack(alignment: .leading, spacing: 4) {
                        Text(did ? "✓ " + st.name : st.name).appFont(13, .bold).foregroundStyle(did ? Theme.sub : Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        if did, let day { Text(Day.md(day)).appFont(11).foregroundStyle(Theme.muted) }
                        if on, let p {
                            Text(p.text).appFont(11).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                            ProgressBar(value: min(1, Double(p.cur) / Double(max(1, p.goal))), fill: Theme.teal, track: Theme.track)
                        }
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .background(on ? Theme.teal.opacity(0.1) : Theme.line, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : .clear, lineWidth: 2))
                    .accessibilityElement(children: .combine).accessibilityHint(st.what)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            if l.step > 3 {
                Text(l.type == "seri" ? String(localized: "지금처럼 지켜요. 여섯 달마다 기록해 둘게요.") : String(localized: "유형은 한 달에 한 번 다시 봐요. 그때 새 걸음이 열려요."))
                    .appFont(12).foregroundStyle(Theme.muted)
            }
        }
    }

    /// 습관 하나를 막대로. line이면 60% 자리(앞서 달리는 형 기준)에 점선
    private func bar(_ k: String, _ v: Double, _ ready: Bool, _ c: Color, line: Bool = false) -> some View {
        let txt = AppModel.dash(AppModel.pct(v), ready: ready)
        return HStack(spacing: 8) {
            Text(k).appFont(13).foregroundStyle(Theme.sub).lineLimit(1).frame(width: 92, alignment: .leading)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(Theme.line)
                    if ready { RoundedRectangle(cornerRadius: 4).fill(c).frame(width: g.size.width * min(1, max(0, v))) }
                    if line {
                        Path { p in
                            p.move(to: CGPoint(x: g.size.width * 0.6, y: -3))
                            p.addLine(to: CGPoint(x: g.size.width * 0.6, y: g.size.height + 3))
                        }
                        .stroke(Theme.orange, style: StrokeStyle(lineWidth: 2, dash: [3, 2]))
                    }
                }
            }
            .frame(height: 14)
            Text(txt).appFont(13, .bold).foregroundStyle(ready && line && v >= 0.6 ? Theme.orange : Theme.ink)
                .frame(width: 40, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore).accessibilityLabel(k + " " + txt)
    }

    /// 최근 기록 칸 색: 결과 뒤 산 것·판 것·기다림과 규칙·새 종목
    static func tint(_ a: CalmEvent.Act) -> Color {
        switch a {
        case .chase, .avgDown: return Theme.orange
        case .switchOut, .trim: return Theme.orange.opacity(0.45)
        case .rule, .wait: return Theme.teal
        case .fresh: return Theme.muted.opacity(0.4)
        }
    }

    private func legend(_ c: Color, _ t: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 9, height: 9)
            Text(t).appFont(11).foregroundStyle(Theme.muted)
        }
    }
}
