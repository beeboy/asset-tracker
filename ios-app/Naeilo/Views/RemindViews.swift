import SwiftUI

// 소식함: 홈 말풍선의 '새 소식 n' 또는 노란 띠를 누르면 열린다.
// 칩 4개(전체·할 일·일정·지난 소식) → 지금 해 주면 좋아요 → 진행 중 → 다가오는 일정 → 지난 30일
struct RemindSheet: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    private enum Tab { case all, todo, event, past }
    @State private var tab: Tab = .all

    var body: some View {
        let r = Remind.shared
        let todo = (r.fresh + r.doing + r.past).filter(\.isTodo)
        let top = todo.first { $0.action != nil }
        let doing = todo.filter { $0.id != top?.id }
        let events = r.events
        let past = (r.fresh + r.past).filter { !$0.isTodo }
        let showTodo = tab == .all || tab == .todo, showEv = tab == .all || tab == .event, showPast = tab == .all || tab == .past
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            chip(.all, "전체")
                            chip(.todo, todo.isEmpty ? "할 일" : "할 일 \(todo.count)")
                            chip(.event, "일정")
                            chip(.past, "지난 소식")
                        }
                    }
                    if showTodo, let t = top { actionCard(t) }
                    if showTodo && !doing.isEmpty {
                        section("진행 중") { ForEach(doing) { i in i.checks == nil ? AnyView(row(i)) : AnyView(checkCard(i)) } }
                    }
                    if showEv && !events.isEmpty {
                        section("다가오는 일정") { ForEach(events) { eventRow($0) } }
                        Text("날짜는 하루 이틀 바뀔 수 있어요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                    if showPast && !past.isEmpty {
                        section("지난 30일") { ForEach(past) { pastRow($0) } }
                    }
                    if (showTodo ? todo.count : 0) + (showEv ? events.count : 0) + (showPast ? past.count : 0) == 0 {
                        Text("아직 소식이 없어요.").appFont(14).foregroundStyle(Theme.sub).padding(.vertical, 20)
                    }
                    if showPast && past.contains(where: { $0.cat == .analysis }) {
                        Text("분석 소식은 내 숫자로 계산한 값이에요. 종목 추천이 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
                    }
                }
                .padding(16)
                .frame(maxWidth: Theme.maxWidth)
                .frame(maxWidth: .infinity)
            }
            .background(Theme.bg)
            .navigationTitle("소식")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() }.frame(minWidth: 44, minHeight: 44) } }
        }
        .presentationDetents([.large])
    }

    private func chip(_ t: Tab, _ label: String) -> some View {
        let sel = tab == t
        return Button { tab = t } label: {
            Text(label).appFont(13, .semibold)
                .foregroundStyle(sel ? .white : Theme.sub)
                .padding(.horizontal, 12).frame(minHeight: 34)
                .background(sel ? Theme.teal : Theme.card, in: Capsule())
                .overlay(Capsule().stroke(sel ? .clear : Theme.border))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(sel ? .isSelected : [])
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).appFont(13, .bold).foregroundStyle(Theme.sub)
            content()
        }
    }

    private func open(_ i: RemindItem) {
        guard i.go != .nowhere else { return }
        dismiss()
        m.go(i.go)
    }

    /// 지금 해 주면 좋아요: 진한 초록 테두리, 이유 한 줄, 버튼
    private func actionCard(_ i: RemindItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("지금 해 주면 좋아요").appFont(12, .bold).foregroundStyle(Theme.green)
            Text(i.text).appFont(16, .bold).foregroundStyle(Theme.ink)
            if let w = i.why { Text(w).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
            Button { open(i) } label: {
                Text(i.action ?? "열기").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(Theme.green, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.green, lineWidth: 2))
    }

    /// 체크리스트 카드 (내 투자 캐릭터 찾기)
    private func checkCard(_ i: RemindItem) -> some View {
        let c = i.checks ?? [], n = c.filter(\.ok).count
        return Button { open(i) } label: {
            VStack(alignment: .leading, spacing: 8) {
                Text(i.text).appFont(15, .bold).foregroundStyle(Theme.ink)
                ProgressView(value: Double(n), total: Double(max(c.count, 1))).tint(Theme.purple)
                ForEach(c.indices, id: \.self) { k in
                    HStack(spacing: 8) {
                        Image(systemName: c[k].ok ? "checkmark.circle.fill" : "circle").appFont(13)
                            .foregroundStyle(c[k].ok ? Theme.green : Theme.muted)
                        Text(c[k].text).appFont(13).foregroundStyle(c[k].ok ? Theme.sub : Theme.ink)
                    }
                }
                if let w = i.why { Text(w).appFont(12).foregroundStyle(Theme.muted) }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 일정: 날짜 블록 + 제목 + 왜 중요한지
    private func eventRow(_ i: RemindItem) -> some View {
        let d = i.day ?? "", n = Day.dday(d)
        return Button { open(i) } label: {
            HStack(alignment: .top, spacing: 12) {
                VStack(spacing: 0) {
                    Text(Day.md(d)).appFont(13, .bold).foregroundStyle(Theme.orange)
                    Text(n == 0 ? "오늘" : "D-\(n)").appFont(11).foregroundStyle(Theme.sub)
                }
                .frame(width: 52, height: 48)
                .background(Theme.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(i.text + (i.sub.map { " · \($0)" } ?? "")).appFont(14, .semibold).foregroundStyle(Theme.ink)
                    if let w = i.why { Text(w).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 지난 30일: 아이콘 없는 한 줄과 날짜
    private func pastRow(_ i: RemindItem) -> some View {
        Button { open(i) } label: {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(i.text).appFont(14).foregroundStyle(Remind.shared.isSeen(i) ? Theme.sub : Theme.ink)
                    .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if let t = Remind.shared.log.first[i.id] { Text(Self.ago(t)).appFont(11).foregroundStyle(Theme.muted) }
            }
            .padding(.vertical, 8)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func row(_ i: RemindItem) -> some View {
        Button {
            guard i.go != .nowhere else { return }
            dismiss()
            m.go(i.go)
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: i.cat.icon).appFont(13, .bold).foregroundStyle(i.cat.color)
                    .frame(width: 28, height: 28).background(i.cat.color.opacity(0.14), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    if let d = i.day {
                        Text("\(MarketEvents.when(d)) · \(i.sub ?? "")").appFont(12, .semibold).foregroundStyle(i.cat.color)
                    }
                    Text(i.text).appFont(14).foregroundStyle(Theme.ink).multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    if let t = Remind.shared.log.first[i.id], i.once {
                        Text(Self.ago(t)).appFont(11).foregroundStyle(Theme.muted)
                    }
                }
                Spacer(minLength: 0)
                if i.go != .nowhere { Image(systemName: "chevron.right").appFont(11).foregroundStyle(Theme.muted).padding(.top, 8) }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    static func ago(_ t: Double) -> String {
        let d = Day.str(Date(timeIntervalSince1970: t)), n = -Day.dday(d)
        return n <= 0 ? "오늘" : n == 1 ? "어제" : "\(Day.md(d))"
    }
}

/// 설정 › 알림 아래: 홈 리마인드 분류별 끄기 (기본은 모두 켜짐, 펼쳐야 보인다)
struct RemindOptions: View {
    @State private var open = false
    var body: some View {
        let r = Remind.shared
        DisclosureGroup(isExpanded: $open) {
            VStack(alignment: .leading, spacing: 4) {
                Text("푸시 알림이 아니라 홈 말풍선·일정 띠·소식함에만 떠요. 끈 분류는 홈과 위젯에서 안 보여요.")
                    .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                ForEach(RemindCat.allCases, id: \.self) { c in
                    Toggle(isOn: Binding(get: { r.isOn(c) }, set: { r.setOn(c, $0) })) { Text(c.label).appFont(15) }
                        .tint(Theme.teal).frame(minHeight: 44)
                }
            }
            .padding(.top, 6)
        } label: {
            Text("홈 리마인드").appFont(15, .bold)
        }
        .tint(Theme.sub)
    }
}
