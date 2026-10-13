import SwiftUI
import WidgetKit

// 본인용 앱: 캐릭터를 고르고 지금 사용량과 위젯 모습을 본다. 설정할 것은 캐릭터뿐이다.

@main
struct UsageApp: App {
    var body: some Scene { WindowGroup { HomeScreen() } }
}

struct HomeScreen: View {
    @State private var u = Usage.saved
    @State private var err: String?
    @State private var busy = false
    @State private var cid = CU.charId
    @Environment(\.scenePhase) private var phase

    private var c: UChar { .of(cid) }
    private var shown: Usage { (u ?? .sample).asOf(Date()) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(spacing: 18) {
                        BlockView(u: shown, c: c, now: Date())
                            .frame(width: 170, height: 170)
                            .background(CharBackground(c: c))
                            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                        RingView(u: shown, c: c)
                            .frame(width: 72, height: 72)
                            .padding(10)
                            .background(Circle().fill(Color(white: 0.2)))
                    }
                    .frame(maxWidth: .infinity)

                    status

                    VStack(alignment: .leading, spacing: 10) {
                        Text("캐릭터").font(.subheadline).foregroundStyle(.secondary)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 10)], spacing: 10) {
                            ForEach(UChar.choices) { ch in chip(ch) }
                        }
                        Text("위젯을 길게 눌러 '위젯 편집'에서 위젯마다 다른 캐릭터를 고를 수도 있어요.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Claude 사용량")
            .refreshable { await load() }
        }
        .task { await load() }
        .onChange(of: phase) { _, p in if p == .active { Task { await load() } } }
    }

    private var status: some View {
        VStack(alignment: .leading, spacing: 6) {
            row("5시간", shown.p5, shown.weekFull ? "주간 리셋 뒤 열려요" : "리셋까지 " + CUTime.left(shown.five.reset, Date()))
            row("주간", shown.pw, "리셋 " + CUTime.weekday(shown.week.reset))
            HStack {
                if let err { Text(err).foregroundStyle(.orange) }
                else if let u { Text("받은 시각 " + u.at.formatted(date: .omitted, time: .shortened)) }
                else { Text("예시 숫자예요") }
                Spacer()
                Button { Task { await load() } } label: { busy ? AnyView(ProgressView()) : AnyView(Image(systemName: "arrow.clockwise")) }
                    .disabled(busy)
            }
            .font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func row(_ name: String, _ p: Int, _ note: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(name).frame(width: 52, alignment: .leading)
            Text("\(p)%").font(.title3.monospacedDigit()).foregroundStyle(p >= 90 ? Color.cuHot : .primary)
            Spacer()
            Text(note).font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func chip(_ ch: UChar) -> some View {
        Button {
            cid = ch.id; CU.charId = ch.id
            WidgetCenter.shared.reloadAllTimelines()
        } label: {
            VStack(spacing: 4) {
                if let img = ch.face {
                    Image(uiImage: img).interpolation(ch.id == "me" ? .medium : .none).resizable().scaledToFill()
                        .frame(width: 44, height: 44).clipShape(Circle())
                }
                Text(ch.name).font(.footnote)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: ch.g1).opacity(0.25)))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(cid == ch.id ? Color(hex: ch.accent) : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        busy = true
        switch await Usage.fetch() {
        case .success(let v): u = v; err = nil; WidgetCenter.shared.reloadAllTimelines()
        case .failure(let e): err = e.msg
        }
        busy = false
    }
}
