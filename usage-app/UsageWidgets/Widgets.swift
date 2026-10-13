import WidgetKit
import SwiftUI
import AppIntents

// Claude 사용량 위젯: 홈 화면 작은 블록 + 잠금 화면 작은 원형.
// 캐릭터는 기본으로 앱에서 고른 캐릭터를 따라가고, 위젯 편집에서 위젯마다 따로 고를 수 있다.

enum CharEnum: String, AppEnum {
    case app, seri, sio, seonbae, ir, sua, me
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "캐릭터"
    static var caseDisplayRepresentations: [CharEnum: DisplayRepresentation] = [
        .app: "앱에서 고른 캐릭터", .seri: "세리", .sio: "시오", .seonbae: "선배", .ir: "이르", .sua: "수아", .me: "내 캐릭터",
    ]
}

/// 내 캐릭터는 naeilo 에서 만들었을 때만 목록에 나온다
struct CharOptions: DynamicOptionsProvider {
    func results() async throws -> [CharEnum] {
        [.app, .seri, .sio, .seonbae, .ir, .sua] + (UChar.me != nil ? [.me] : [])
    }
}

struct CharIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "캐릭터 고르기"
    static var description = IntentDescription("위젯에 나올 캐릭터를 골라요.")
    @Parameter(title: "캐릭터", default: .app, optionsProvider: CharOptions()) var who: CharEnum
    init() {}
    init(_ w: CharEnum) { who = w }
    var char: UChar { who == .app ? .current : .of(who.rawValue) }
}

struct UsageEntry: TimelineEntry {
    let date: Date
    let u: Usage
    let c: UChar
}

struct UsageProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> UsageEntry { UsageEntry(date: Date(), u: .sample, c: UChar.all[0]) }
    func snapshot(for cfg: CharIntent, in context: Context) async -> UsageEntry {
        let u = context.isPreview ? (Usage.saved ?? .sample) : await latest()
        return UsageEntry(date: Date(), u: u.asOf(Date()), c: cfg.char)
    }
    func timeline(for cfg: CharIntent, in context: Context) async -> Timeline<UsageEntry> {
        let u = await latest(), now = Date(), c = cfg.char
        // 남은 시간 글자가 맞게 5분마다 다시 그리고, 15분마다 새로 받는다
        let entries = (0..<4).map { i -> UsageEntry in
            let d = now.addingTimeInterval(Double(i) * 300)
            return UsageEntry(date: d, u: u.asOf(d), c: c)
        }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(15 * 60)))
    }

    private func latest() async -> Usage {
        if case .success(let u) = await Usage.fetch() { return u }
        return Usage.saved ?? .sample
    }
}

struct UsageBlockWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "cu.block", intent: CharIntent.self, provider: UsageProvider()) { e in
            BlockView(u: e.u, c: e.c, now: e.date)
                .containerBackground(for: .widget) { CharBackground(c: e.c) }
        }
        .configurationDisplayName("Claude 사용량")
        .description("5시간 사용량은 칸, 주간 사용량은 탑으로 보여요.")
        .supportedFamilies([.systemSmall])
        .contentMarginsDisabled()
    }
}

struct UsageRingWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "cu.ring", intent: CharIntent.self, provider: UsageProvider()) { e in
            RingView(u: e.u, c: e.c)
                .containerBackground(for: .widget) { Color.clear }
        }
        .configurationDisplayName("Claude 사용량 링")
        .description("바깥 링은 5시간, 안쪽 링은 주간 사용량이에요.")
        .supportedFamilies([.accessoryCircular])
    }
}

@main
struct UsageWidgets: WidgetBundle {
    var body: some Widget {
        UsageBlockWidget()
        UsageRingWidget()
    }
}
