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
                    Text("평정 지수").appFont(13, .bold).foregroundStyle(Theme.mint)
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
        .accessibilityElement(children: .combine)
        .accessibilityLabel("평정 지수 보기")
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
    }
}

struct CalmView: View {
    @Environment(AppModel.self) private var m
    @State private var shown = 10   // 최근 기록을 몇 건까지 펼쳤는지

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        let name = m.friendName(who)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 내 인물
                Card {
                    HStack(alignment: .bottom, spacing: 12) {
                        CharPixel(id: who, width: 56, height: 80)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(k.map { "\(name) · \($0.title)" } ?? "아직 매칭 전").appFont(17, .bold)
                            if let k { Text(k.like).appFont(13).foregroundStyle(Theme.sub) }
                            if m.calm.type != nil {
                                Text(m.calm.stageLine).appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    Text(m.calm.say(s, who: m.voice(who))).appFont(15).lineSpacing(3)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 12))
                    if let w = Calm.why(m.calm.type, s) {
                        Text("왜 이 유형인가요").appFont(13, .bold)
                        Text(w).appFont(13).lineSpacing(2).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    }
                    if let p = m.calm.prevType, let pk = Calm.kind(p), let pn = Shelter.friends.first(where: { $0.id == p })?.name {
                        Text("지난달 \(pn)(\(pk.title))에서 옮겨 왔어요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                }

                // 습관 사다리
                if let l = m.calm.ladder, let steps = Calm.steps[l.type] { ladder(l, steps) }
                else {
                    Card {
                        Text("다음 걸음").appFont(15, .bold)
                        Text("기록 \(Calm.minEvents)건이 모여 유형이 정해지면, 그 유형에 맞는 걸음 3개가 여기에 열려요.")
                            .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    }
                }

                // 숫자
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        Text("평정 지수").appFont(15, .bold)
                        Spacer()
                        Text(s.n < Calm.minEvents ? "기록 \(s.n)/\(Calm.minEvents)" : "\(s.index) / 100").appFont(20, .bold).foregroundStyle(Theme.teal)
                    }
                    ProgressBar(value: Double(s.index) / 100, fill: Theme.teal, track: Theme.track)
                    Text(Calm.meaning(s)).appFont(13).lineSpacing(2).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    row("수익 뒤 추가 매수", AppModel.pct(s.chase))
                    row("손실 뒤 매도", AppModel.pct(s.switchR))
                    row("큰 변동일에 기다림", AppModel.pct(s.waitR))
                    row("규칙대로 한 매매", AppModel.pct(s.ruleR))
                    row("이번 주 점수", "\(s.weekPts)점 (플러스는 주 \(Calm.weekCap)점까지)")
                    if let t = s.trend { row("최근 30일 변화", (t >= 0 ? "+" : "") + "\(t)") }
                }

                // 다섯 유형
                Card {
                    Text("다섯 가지 유형").appFont(15, .bold)
                    ForEach(Calm.kinds, id: \.id) { kd in
                        let on = kd.id == m.calm.type
                        HStack(alignment: .top, spacing: 10) {
                            Pixel(name: "spr_" + kd.id, width: 21, height: 30)
                            VStack(alignment: .leading, spacing: 2) {
                                Text((Shelter.friends.first { $0.id == kd.id }?.name ?? "") + " · " + kd.title).appFont(14, on ? .bold : .semibold)
                                    .foregroundStyle(on ? Theme.teal : Theme.ink)
                                Text(kd.when).appFont(12).foregroundStyle(Theme.sub)
                            }
                        }
                    }
                }

                // 최근 기록
                if !m.calm.events.isEmpty {
                    Card {
                        HStack(alignment: .firstTextBaseline) {
                            Text("최근 기록").appFont(15, .bold)
                            Spacer()
                            Text("모두 \(m.calm.events.count)건").appFont(12).foregroundStyle(Theme.muted)
                        }
                        ForEach(Array(m.calm.events.suffix(shown).reversed().enumerated()), id: \.offset) { _, e in
                            HStack {
                                Text(Day.md(e.day)).appFont(12).foregroundStyle(Theme.muted).frame(width: 40, alignment: .leading)
                                Text("\(e.sym) · \(e.label)").appFont(13)
                                Spacer()
                                Text(e.pts == 0 ? "0" : (e.pts > 0 ? "+" : "") + "\(e.pts)").appFont(13, .bold)
                                    .foregroundStyle(e.pts > 0 ? Theme.teal : e.pts < 0 ? Theme.up : Theme.muted)
                            }
                        }
                        if m.calm.events.count > shown {
                            Button("더 보기 (\(m.calm.events.count - shown)건 남음)") { shown += 20 }
                                .appFont(13, .semibold).foregroundStyle(Theme.teal)
                        }
                        Text("기록은 최근 \(Calm.keep)건까지 남아요. 그보다 오래된 것은 지수 계산에서도 빠져요.")
                            .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                    }
                }

                Text("정해 둔 규칙대로 하면 점수가 올라요. 물타기는 점수가 없어요. 유형은 기록 \(Calm.minEvents)건부터, 한 달에 한 번 바뀌어요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
            }
            .padding(16)
            .foregroundStyle(Theme.ink)
        }
        .background(Theme.bg)
        .onAppear { if m.calm.type != nil && m.calm.ladder?.type != m.calm.type { m.calm.climb() } }
        .modifier(DetailTitle(title: "평정 지수")).navigationBarTitleDisplayMode(.inline)
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
