import SwiftUI

// 소식함: 홈 말풍선의 '새 소식 n' 또는 노란 띠를 누르면 열린다.
// 분류 칩 → 안 본 소식 → 진행 중 → 이번 주 일정 → 지난 소식(흐리게, 30일)
struct RemindSheet: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var cat: RemindCat? = nil

    var body: some View {
        let r = Remind.shared
        let keep = { (i: RemindItem) in cat == nil || i.cat == cat }
        let fresh = r.fresh.filter(keep), doing = r.doing.filter(keep), events = r.events.filter(keep), past = r.past.filter(keep)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            chip(nil, "전체")
                            ForEach(RemindCat.allCases.filter { r.isOn($0) }, id: \.self) { c in chip(c, c.label) }
                        }
                    }
                    if !fresh.isEmpty { section("안 본 소식 \(fresh.count)", fresh) }
                    if !doing.isEmpty { section("진행 중", doing) }
                    if !events.isEmpty {
                        section("이번 주 일정 (내 종목 먼저)", events)
                        Text("naeilo.com 외부 요인의 날짜가 정해진 일정이에요. 날짜는 근사일 수 있어요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                    if !past.isEmpty { section("지난 소식 (30일)", past, faded: true) }
                    if fresh.isEmpty && doing.isEmpty && events.isEmpty && past.isEmpty {
                        Text("아직 소식이 없어요.").appFont(14).foregroundStyle(Theme.sub).padding(.vertical, 20)
                    }
                    if (cat == nil || cat == .analysis) && (r.items.contains { $0.cat == .analysis }) {
                        Text("분석 소식은 내 숫자로 계산한 값이에요. 종목 추천이 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
                    }
                }
                .padding(16)
                .frame(maxWidth: Theme.maxWidth)
                .frame(maxWidth: .infinity)
            }
            .background(Theme.bg)
            .navigationTitle("새 소식")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
        }
        .presentationDetents([.large])
    }

    private func chip(_ c: RemindCat?, _ t: String) -> some View {
        let sel = cat == c
        return Button { cat = c } label: {
            Text(t).appFont(13, .semibold)
                .foregroundStyle(sel ? .white : Theme.sub)
                .padding(.horizontal, 12).frame(minHeight: 34)
                .background(sel ? (c?.color ?? Theme.teal) : Theme.card, in: Capsule())
                .overlay(Capsule().stroke(sel ? .clear : Theme.border))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(sel ? .isSelected : [])
    }

    private func section(_ title: String, _ list: [RemindItem], faded: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).appFont(13, .bold).foregroundStyle(Theme.sub)
            ForEach(list) { i in row(i).opacity(faded ? 0.55 : 1) }
        }
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
