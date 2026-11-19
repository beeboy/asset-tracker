import SwiftUI

/// 종목 상세 아래: 이 종목 매매 기록 (내 투자 습관 기록 중 이 종목 매매만, 최근 것부터). 줄을 누르면 고치기·지우기
struct TradeLogCard: View {
    @Environment(AppModel.self) private var m
    let sym: Symbol
    @State private var shown = 5
    @State private var pick: CalmEvent?

    var body: some View {
        let ev = Array(m.calm.events.filter { $0.sym == sym.id && $0.isTrade }.reversed())
        if !ev.isEmpty {
            Card {
                HStack(alignment: .firstTextBaseline) {
                    Text("매매 기록").appFont(15, .bold)
                    Spacer()
                    Text("\(ev.count)건").appFont(12).foregroundStyle(Theme.muted)
                }
                ForEach(Array(ev.prefix(shown).enumerated()), id: \.offset) { _, e in
                    Button { pick = e } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(e.buy ? String(localized: "샀어요") : String(localized: "팔았어요")) · \(e.label)").appFont(13)
                                Text(Day.md(e.day)).appFont(12).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").appFont(11).foregroundStyle(Theme.muted)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain).foregroundStyle(Theme.ink)
                }
                if ev.count > shown {
                    Button("더 보기 (\(ev.count - shown)건 남음)") { shown += 10 }
                        .appFont(13, .semibold).foregroundStyle(Theme.teal)
                }
            }
            .sheet(isPresented: Binding(get: { pick != nil }, set: { if !$0 { pick = nil } })) {
                if let e = pick { TradeEditSheet(old: e).environment(m) }
            }
        }
    }
}

/// 매매 기록 하나 고치기: 날짜·샀어요/팔았어요·분류, 지우기
struct TradeEditSheet: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    let old: CalmEvent
    @State private var day = Date()
    @State private var buy = true
    @State private var act: CalmEvent.Act = .rule
    @State private var askDel = false

    /// 산/판에 맞는 분류 (기다림은 매매가 아니라 뺀다)
    static func acts(buy: Bool) -> [CalmEvent.Act] { buy ? [.rule, .fresh, .chase, .avgDown] : [.rule, .trim, .switchOut] }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SheetTitleBar(title: "매매 기록 고치기") { dismiss() }
            DatePicker(selection: $day, in: ...Date(), displayedComponents: .date) { Text("날짜").appFont(15) }
            Picker("", selection: $buy) {
                Text("샀어요").tag(true)
                Text("팔았어요").tag(false)
            }
            .pickerStyle(.segmented)
            .onChange(of: buy) { if !Self.acts(buy: buy).contains(act) { act = .rule } }
            HStack {
                Text("분류").appFont(15)
                Spacer()
                Picker("", selection: $act) {
                    ForEach(Self.acts(buy: buy), id: \.self) { Text(CalmEvent.label($0)).tag($0) }
                }
                .pickerStyle(.menu).tint(Theme.teal)
            }
            PrimaryButton(title: String(localized: "저장")) {
                m.calm.edit(old, day: Day.str(day), buy: buy, act: act)
                if m.persists { Sync.shared.changed(m) }
                dismiss()
            }
            Button(role: .destructive) { askDel = true } label: {
                Text("지우기").appFont(15, .semibold).frame(maxWidth: .infinity)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(Theme.bg)
        .presentationDetents([.medium])
        .onAppear {
            day = Day.date(old.day) ?? Date()
            buy = old.buy
            act = Self.acts(buy: old.buy).contains(old.act) ? old.act : .rule
        }
        .confirmationDialog("이 기록을 지울까요?", isPresented: $askDel, titleVisibility: .visible) {
            Button("지우기", role: .destructive) {
                m.calm.remove(old)
                if m.persists { Sync.shared.changed(m) }
                dismiss()
            }
            Button("취소", role: .cancel) {}
        }
    }
}
