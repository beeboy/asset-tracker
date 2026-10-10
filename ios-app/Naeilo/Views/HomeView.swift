import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AppHeader().padding(.horizontal, -16)
                Text(Sample.asOfText).appFont(13).foregroundStyle(Theme.sub2)
                summary
                shelterBox
                if m.playOn && !m.interDone { reminder }
                if m.playOn { RoutineCard() } else { lockedRoutine }
                if !m.playOn { yesterdayCard }
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: String.self) { route in
            if route == "shelter" { ShelterView() }
            else if route.hasPrefix("read:"), let i = Int(route.dropFirst(5)) { ReaderView(index: i) }
        }
    }

    // 어제 종가 기준 평가액 + 본전 진행 막대
    private var summary: some View {
        let need = m.cost / max(1, m.total) - 1
        let needY = m.cost / (m.total / (1 + m.yesterdayMove)) - 1
        return VStack(alignment: .leading, spacing: 10) {
            Text("지금 평가액 · 들어간 돈 \(AppModel.man(m.cost))").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(AppModel.man(m.total)).appFont(32, .bold).tracking(-0.5)
                Text(AppModel.sgn(m.ret)).appFont(16, .bold)
                    .foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
            }
            HStack {
                Text("본전까지")
                Spacer()
                Text(need > 0 ? "+" + String(format: "%.1f", need * 100) + "% 남음" : "본전 도달")
                    .fontWeight(.bold).foregroundStyle(Theme.yellow)
            }
            .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            ProgressBar(value: m.total / max(1, m.cost))
            Text(need > 0
                 ? "본전까지 남은 오름폭이 \(String(format: "%.1f", needY * 100))%에서 \(String(format: "%.1f", need * 100))%로 " + (need <= needY ? "줄었어요." : "늘었어요. 하루 숫자는 흔들려요.")
                 : "본전을 넘었어요. 이제 목표 루트로 갈 수 있어요.")
                .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(.white)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))
    }

    // 쉼터 박스: 홈에 둔 친구 + 말풍선 + 다른 친구 칸
    private var shelterBox: some View {
        let homeI = Shelter.friends.firstIndex { $0.id == m.homeFriend } ?? 0
        let on = (0..<5).filter { m.friendOn($0) }.count
        let next = (0..<5).first { !m.friendOn($0) }
        return NavigationLink(value: "shelter") {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .bottom, spacing: 10) {
                    Pixel(name: "spr_" + m.homeFriend, width: 56, height: 80)
                        .accessibilityLabel(Shelter.friends[homeI].name)
                    Text(m.homeSay)
                        .appFont(14).lineSpacing(3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.mintBg, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                }
                Divider().overlay(Theme.line)
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach(Array(Shelter.friends.enumerated()).filter { $0.element.id != m.homeFriend }, id: \.element.id) { i, f in
                        Pixel(name: (m.friendOn(i) ? "spr_" : "sil_") + f.id, width: 20, height: 28).opacity(m.friendOn(i) ? 1 : 0.5)
                    }
                    Spacer()
                    Text("친구 \(on)/5" + (next.map { " · 다음: \(Shelter.friends[$0].name), \(m.friendWhen($0))" } ?? " · 외전 5장까지 열림") + " ›")
                        .appFont(12).foregroundStyle(Theme.sub).lineLimit(2).minimumScaleFactor(0.8).multilineTextAlignment(.trailing)
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(.white, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("쉼터 보기")
    }

    private var reminder: some View {
        let step = Shelter.weekSteps[min(m.weeks.count, 3)]
        return HStack(spacing: 8) {
            Text("리마인드").appFont(11, .bold).padding(.horizontal, 8).padding(.vertical, 2)
                .background(Theme.yellow, in: Capsule())
            Text("인터미션 \(m.weeks.count)/4주 · 이번 주: \(step.task)").appFont(13).lineLimit(2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12).frame(minHeight: 44)
        .background(Theme.cream, in: RoundedRectangle(cornerRadius: 12))
    }

    private var lockedRoutine: some View {
        DashedCard {
            Label("오늘의 1분", systemImage: "lock").appFont(15, .bold)
            Text("앱 시작 3단계를 마치면 바로 열려요. 매일 1분, 어제 숫자 하나와 질문 하나예요.").appFont(13)
        }
    }

    private var yesterdayCard: some View {
        Card {
            HStack(alignment: .firstTextBaseline) {
                Text("어제의 움직임").appFont(15, .bold)
                Spacer()
                Text(AppModel.sgn(m.yesterdayMove)).appFont(20, .bold).foregroundStyle(Theme.teal)
            }
            Text("DRNK \(AppModel.sgn(-0.0221)) · QQQ \(AppModel.sgn(0.004))").appFont(13).foregroundStyle(Theme.sub)
        }
    }
}

// 오늘의 1분: 어제 숫자 확인 + 날마다 바뀌는 질문 하나. 설명은 계산값으로.
struct RoutineCard: View {
    @Environment(AppModel.self) private var m
    private let days = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        let t = m.today, q = m.question
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("오늘의 1분").appFont(13, .bold).tracking(1).foregroundStyle(Theme.mint)
                Spacer()
                Text("\(m.day + 1)일째 · 연속 \(m.streak)일").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            HStack(spacing: 4) {
                ForEach(0..<7, id: \.self) { i in
                    let d = m.day - m.day % 7 + i
                    let ok = m.dayLog[d].map { $0.answer != nil } ?? (d < 5 && d < m.day)
                    Text(days[i]).appFont(12, .bold)
                        .frame(maxWidth: .infinity, minHeight: 28)
                        .foregroundStyle(ok ? Theme.ink : d == m.day ? Theme.yellow : Theme.muted)
                        .background(ok ? Theme.mint : d == m.day ? .clear : Theme.slate, in: RoundedRectangle(cornerRadius: 8))
                        .overlay { if d == m.day && !ok { RoundedRectangle(cornerRadius: 8).stroke(Theme.yellow, lineWidth: 2) } }
                }
            }
            step(tag: "1 · 어제 숫자") {
                Text(m.routineFact).appFont(15).lineSpacing(3)
                if !t.seen {
                    Button("봤어요") { withAnimation { m.markSeen() } }
                        .appFont(14, .bold).foregroundStyle(.white)
                        .padding(.horizontal, 16).frame(minHeight: 40)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                }
            }
            if t.seen {
                step(tag: "2 · \(q.tag)") {
                    Text(q.q).appFont(15, .semibold).lineSpacing(3)
                    FlowRow(spacing: 6) {
                        ForEach(q.opts.indices, id: \.self) { i in
                            let ans = t.answer
                            let right = q.right >= 0 && i == q.right
                            Button(q.opts[i]) { withAnimation { m.answer(i) } }
                                .disabled(ans != nil)
                                .appFont(14, .bold)
                                .padding(.horizontal, 14).frame(minHeight: 40)
                                .foregroundStyle(ans == nil ? Theme.ink : right ? Theme.tealDark : i == ans ? .white : Theme.muted)
                                .background(ans == nil ? .white : right ? Theme.tealBg : i == ans ? Theme.ink : .white, in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(ans == nil ? Theme.dash : right ? Theme.teal : i == ans ? Theme.ink : Theme.track, lineWidth: 2))
                        }
                    }
                    if let a = t.answer {
                        Text((q.right >= 0 ? (a == q.right ? "맞아요! " : "아쉬워요. ") : "") + q.fb(a))
                            .appFont(13).lineSpacing(3)
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            if t.answer != nil {
                Text(m.routineDoneText).appFont(14, .semibold).foregroundStyle(Theme.yellow)
            }
            Button("다음 날로 넘기기 (시안)") { withAnimation { m.day += 1 } }
                .appFont(13).underline().foregroundStyle(Theme.muted).frame(minHeight: 40)
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))
    }

    private func step<C: View>(tag: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(tag).appFont(12, .bold).foregroundStyle(Theme.teal)
            c()
        }
        .foregroundStyle(Theme.ink)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 14))
    }
}

// 줄바꿈되는 가로 배치
struct FlowRow: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > w { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing; rowH = max(rowH, sz.height); maxX = max(maxX, x - spacing)
        }
        return CGSize(width: min(w, maxX), height: y + rowH)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > b.minX && x + sz.width > b.maxX { x = b.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
    }
}
