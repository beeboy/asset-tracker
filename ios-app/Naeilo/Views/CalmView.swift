import SwiftUI

// 평정 지수 (가위바위보 지수): 분석 탭 맨 위 카드 + 상세. 계산은 Data/Calm.swift
// 앱은 지수·내 인물·최근 기록만 짧게, 사건별 표와 추이 그래프는 PC naeilo.com 몫.

struct CalmCard: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        NavigationLink(value: AnalysisRoute.calm) {
            HStack(alignment: .center, spacing: 12) {
                Pixel(name: "spr_" + who, width: 42, height: 60)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("평정 지수").appFont(13, .bold).foregroundStyle(Theme.teal)
                        if let k { Text("· \(k.title)").appFont(13).foregroundStyle(Theme.sub) }
                        Spacer()
                        Text(s.n < Calm.minEvents ? "–" : "\(s.index)").appFont(22, .bold)
                    }
                    Text(m.calm.say(s)).appFont(14).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("평정 지수 보기")
    }
}

struct CalmView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let s = m.calm.stats(), who = m.calmSpeaker, k = Calm.kind(m.calm.type)
        let name = Shelter.friends.first { $0.id == who }?.name ?? ""
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 내 인물
                Card {
                    HStack(alignment: .bottom, spacing: 12) {
                        Pixel(name: "spr_" + who, width: 56, height: 80)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(k.map { "\(name) · \($0.title)" } ?? "아직 매칭 전").appFont(17, .bold)
                            if let k { Text(k.like).appFont(13).foregroundStyle(Theme.sub) }
                            if m.calm.type != nil {
                                Text("함께한 날 \(m.calm.together.count)일 · 단계 \(m.calm.stage)/3").appFont(12).foregroundStyle(Theme.muted)
                            }
                        }
                    }
                    Text(m.calm.say(s)).appFont(15).lineSpacing(3)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 12))
                    if let k { Text("다음 걸음: " + k.next).appFont(13, .semibold).foregroundStyle(Theme.teal) }
                    if let p = m.calm.prevType, let pk = Calm.kind(p), let pn = Shelter.friends.first(where: { $0.id == p })?.name {
                        Text("지난달 \(pn)(\(pk.title))에서 옮겨 왔어요.").appFont(12).foregroundStyle(Theme.muted)
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
                        Text("최근 기록").appFont(15, .bold)
                        ForEach(Array(m.calm.events.suffix(10).reversed().enumerated()), id: \.offset) { _, e in
                            HStack {
                                Text(Day.md(e.day)).appFont(12).foregroundStyle(Theme.muted).frame(width: 40, alignment: .leading)
                                Text("\(e.sym) · \(e.label)").appFont(13)
                                Spacer()
                                Text(e.pts == 0 ? "0" : (e.pts > 0 ? "+" : "") + "\(e.pts)").appFont(13, .bold)
                                    .foregroundStyle(e.pts > 0 ? Theme.teal : e.pts < 0 ? Theme.up : Theme.muted)
                            }
                        }
                        Text("사건별 표와 추이 그래프는 PC naeilo.com에서 볼 수 있어요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                }

                Text("점수는 직전 결과와 상관없이 정해 둔 규칙(비중 이탈 되돌리기, 그달 첫 적립)대로 했을 때 올라요. 손실 뒤 추가 매수(물타기)에는 점수를 주지 않아요. 유형은 기록이 \(Calm.minEvents)건 쌓이면 정해지고, 그 뒤로는 한 달에 한 번만 바뀌어요. 투자 권고가 아니에요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
            }
            .padding(16)
            .foregroundStyle(Theme.ink)
        }
        .background(Theme.bg)
        .navigationTitle("평정 지수").navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ k: String, _ v: String) -> some View {
        HStack {
            Text(k).appFont(14).foregroundStyle(Theme.sub)
            Spacer()
            Text(v).appFont(14, .semibold)
        }
    }
}
