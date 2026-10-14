import SwiftUI

// 평정 지수 (가위바위보 지수): 분석 탭 맨 위 카드 + 상세. 계산은 Data/Calm.swift
// 앱은 지수·내 인물·최근 기록만 짧게, 사건별 표와 추이 그래프는 PC naeilo.com 몫.

struct CalmCard: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        HStack(alignment: .center, spacing: 12) {
            CharPixel(id: who, width: 42, height: 60)
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text("내 투자 습관").appFont(13, .bold).foregroundStyle(Theme.mint)
                    if let k { Text("· \(k.title)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                    Spacer()
                    Text(s.n < Calm.minEvents ? "–" : "\(s.index)").appFont(28, .bold).tracking(-0.5)
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

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        let name = m.friendName(who)
        let ready = s.n >= Calm.minEvents
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 상태 카드 하나 (진한 바탕): 모으는 중이면 진행, 정해졌으면 내 인물
                Card(dark: true) {
                    HStack(alignment: .bottom, spacing: 12) {
                        CharPixel(id: who, width: 56, height: 80)
                        VStack(alignment: .leading, spacing: 4) {
                            if let k, let f = Shelter.friends.first(where: { $0.id == m.calm.type }) {
                                NavigationLink { CharacterDetailView(friend: f, invest: true) } label: {
                                    Text("\(name) · \(k.title) ›").appFont(17, .bold)
                                }
                                .buttonStyle(.plain)
                                Text(Calm.does[k.id] ?? k.like).appFont(13).foregroundStyle(Theme.sub)
                                Text(m.calm.stageLine).appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                            } else {
                                Text("아직 모으는 중").appFont(17, .bold)
                                Text("사고판 기록이 \(Calm.minEvents)건 모이면, 나와 닮은 인물을 알려드려요")
                                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    if !ready {
                        // 진행 바는 기록 수 (지수가 아니다: 기록이 적으면 지수는 50 근처라 막대가 절반 넘게 찼다)
                        ProgressBar(value: Double(s.n) / Double(Calm.minEvents), fill: Theme.mint, track: Theme.track)
                        Text("매매 기록 \(s.n) / \(Calm.minEvents)건").appFont(12, .semibold).foregroundStyle(Theme.mint)
                    } else {
                        ProgressBar(value: Double(s.index) / 100, fill: Theme.mint, track: Theme.track)
                        Text("평정 지수 \(s.index) / 100 · \(Calm.meaning(s))").appFont(12).foregroundStyle(Theme.sub)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Divider().overlay(Theme.border)
                    Text("\(name): “\(m.calm.say(s, who: m.voice(who)))”").appFont(14).lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                    if let w = Calm.why(m.calm.type, s) {
                        Text(w).appFont(12).lineSpacing(2).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                    }
                    if let p = m.calm.prevType, let pk = Calm.kind(p), let pn = Shelter.friends.first(where: { $0.id == p })?.name {
                        Text("지난달 \(pn)(\(pk.title))에서 옮겨 왔어요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                }

                // 뭐예요
                Card {
                    Text("평정 지수가 뭐예요?").appFont(15, .bold)
                    (Text("사고팔 때 기분보다 ") + Text("정해 둔 규칙").bold() + Text("을 따랐는지 보는 점수예요. 규칙대로 하면 오르고, 물타기는 점수가 없어요."))
                        .appFont(13).lineSpacing(2).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    Text("이번 주 \(s.weekPts)점 · 한 주에 최대 \(Calm.weekCap)점까지 받을 수 있어요.").appFont(12).foregroundStyle(Theme.muted)
                }

                // 네 가지 (10건 전에는 0% 대신 —)
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        Text("내 매매 습관 네 가지").appFont(15, .bold)
                        Spacer()
                        if !ready { Text("\(Calm.minEvents)건부터 보여요").appFont(12).foregroundStyle(Theme.muted) }
                    }
                    row("오른 뒤 더 산 비율", AppModel.dash(AppModel.pct(s.chase), ready: ready))
                    row("내린 뒤 판 비율", AppModel.dash(AppModel.pct(s.switchR), ready: ready))
                    row("크게 움직인 날 기다린 비율", AppModel.dash(AppModel.pct(s.waitR), ready: ready))
                    row("규칙대로 한 매매", AppModel.dash(AppModel.pct(s.ruleR), ready: ready))
                    if ready, let t = s.trend { row("최근 30일 변화", (t >= 0 ? "+" : "") + "\(t)") }
                }

                // 최근 기록
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        Text("최근 기록").appFont(15, .bold)
                        Spacer()
                        Text("모두 \(m.calm.events.count)건").appFont(12).foregroundStyle(Theme.muted)
                    }
                    ForEach(Array(m.calm.events.suffix(shown).reversed().enumerated()), id: \.offset) { _, e in
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(e.sym) · \(e.label)").appFont(13)
                                Text("\(Day.md(e.day)) · \(Calm.reason(e.act))").appFont(12).foregroundStyle(Theme.muted)
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
                    Text("종목 수량을 고치면 기록이 생겨요.").appFont(12).foregroundStyle(Theme.muted)
                }

                // 다섯 인물 (행동으로 설명)
                Card {
                    Text("닮을 수 있는 다섯 인물").appFont(15, .bold)
                    ForEach(Calm.kinds, id: \.id) { kd in
                        let on = kd.id == m.calm.type
                        NavigationLink {
                            if let f = Shelter.friends.first(where: { $0.id == kd.id }) { CharacterDetailView(friend: f, invest: true) }
                        } label: {
                            HStack(alignment: .top, spacing: 10) {
                                Pixel(name: "spr_" + kd.id, width: 21, height: 30)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text((Shelter.friends.first { $0.id == kd.id }?.name ?? "") + " · " + kd.title).appFont(14, on ? .bold : .semibold)
                                        .foregroundStyle(on ? Theme.teal : Theme.ink)
                                    Text(Calm.does[kd.id] ?? kd.when).appFont(12).foregroundStyle(Theme.sub).multilineTextAlignment(.leading)
                                }
                                Spacer(minLength: 0)
                                Text("›").appFont(14, .bold).foregroundStyle(Theme.muted)
                            }
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    Text("정확한 기준은 인물을 누르면 볼 수 있어요.").appFont(12).foregroundStyle(Theme.muted)
                }

                // 다음 걸음 (잠겼으면 점선 상자)
                if let l = m.calm.ladder, let steps = Calm.steps[l.type] { ladder(l, steps) }
                else {
                    Text("닮은 인물이 정해지면, 나에게 맞는 다음 걸음 3개가 여기에 열려요.")
                        .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [5, 4])))
                }

                Text("한 달에 한 번 다시 정해져요. 최근 \(Calm.keep)건까지만 계산에 써요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
            }
            .padding(16)
            .foregroundStyle(Theme.ink)
        }
        .background(Theme.bg)
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
        .modifier(DetailTitle(title: "내 투자 습관")).navigationBarTitleDisplayMode(.inline)
    }

    /// 지금 유형의 걸음 3개: 완료한 날, 지금 걸음 진행. 인물 순위는 보여 주지 않는다
    private func ladder(_ l: Ladder, _ steps: [Calm.Step]) -> some View {
        let p = m.calm.progress()
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("다음 걸음").appFont(15, .bold)
                Spacer()
                Text(l.step > 3 ? "다 올랐어요" : "\(l.step)/3").appFont(13, .semibold).foregroundStyle(Theme.teal)
            }
            ForEach(Array(steps.enumerated()), id: \.offset) { i, st in
                let n = i + 1, on = n == l.step, did = n < l.step
                let day = l.done.last { $0.type == l.type && $0.step == n }?.day
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: did ? "checkmark.circle.fill" : on ? "circle.dotted" : "circle")
                        .foregroundStyle(did || on ? Theme.teal : Theme.muted)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(st.name).appFont(14, on ? .bold : .semibold).foregroundStyle(did ? Theme.sub : Theme.ink)
                            Spacer()
                            if did, let day { Text(Day.md(day)).appFont(12).foregroundStyle(Theme.muted) }
                        }
                        Text(st.what).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        if on, let p {
                            ProgressBar(value: min(1, Double(p.cur) / Double(max(1, p.goal))), fill: Theme.teal, track: Theme.track)
                            Text(p.text).appFont(12, .semibold).foregroundStyle(Theme.teal)
                        }
                    }
                }
            }
            if l.step > 3 {
                Text(l.type == "seri" ? "지금처럼 지켜요. 여섯 달마다 기록해 둘게요." : "유형은 한 달에 한 번 다시 봐요. 그때 새 걸음이 열려요.")
                    .appFont(12).foregroundStyle(Theme.muted)
            }
        }
    }

    private func row(_ k: String, _ v: String) -> some View {
        HStack {
            Text(k).appFont(14).foregroundStyle(Theme.sub)
            Spacer()
            Text(v).appFont(14, .semibold)
        }
    }
}
