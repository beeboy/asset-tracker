import WidgetKit
import SwiftUI

// 서재 위젯: 외전 『이종 공명』에서 읽던 자리의 한 문단. 위젯은 스크롤이 안 되니 한 문단만 보여 주고, 누르면 앱의 그 장 그 자리로 간다.
// 값은 앱이 story.json 으로 쓴다 (언어도 앱의 읽기 화면에서 고른 것). 서재가 아직 안 열렸으면 앱 시작 3단계로 간다.

struct StoryEntry: TimelineEntry {
    let date: Date
    let s: WStory
}

struct StoryProvider: TimelineProvider {
    func placeholder(in context: Context) -> StoryEntry { StoryEntry(date: Date(), s: .sample) }
    func getSnapshot(in context: Context, completion: @escaping (StoryEntry) -> Void) {
        let s = Store.read(WStory.self, "story.json")
        completion(StoryEntry(date: Date(), s: context.isPreview && !(s?.open ?? false) ? .sample : s ?? .sample))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<StoryEntry>) -> Void) {
        // 읽던 자리가 바뀔 때 앱이 다시 그리게 한다
        let s = Store.read(WStory.self, "story.json") ?? WStory(open: false, chapter: 0, label: "프롤로그 · 핵심 코어", line: "앱을 열면 서재가 준비돼요.", friend: "seri", progress: 0, lang: "ko")
        completion(Timeline(entries: [StoryEntry(date: Date(), s: s)], policy: .never))
    }
}

struct StoryWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "story", provider: StoryProvider()) { e in StoryWidgetView(s: e.s) }
            .configurationDisplayName("서재 · 이어 읽기")
            .description("외전 『이종 공명』에서 읽던 자리의 한 문단. 누르면 그 장으로 가요")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct StoryWidgetView: View {
    let s: WStory
    @Environment(\.widgetFamily) private var family
    private let mint = Color(red: 0x5F / 255, green: 0xD0 / 255, blue: 0xC4 / 255)

    var body: some View {
        let small = family == .systemSmall
        HStack(alignment: .bottom, spacing: 8) {
            VStack(alignment: .leading, spacing: small ? 4 : 6) {
                Text(s.lang == "en" ? "Library" : "서재").font(.system(size: 11, weight: .semibold)).foregroundStyle(mint).widgetAccentable()
                Text(s.label).font(.system(size: small ? 12 : 13, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
                Text(s.line)
                    .font(.system(size: small ? 12 : 14, design: .serif))
                    .lineSpacing(2)
                    .foregroundStyle(.white.opacity(s.open ? 0.88 : 0.7))
                    .lineLimit(small ? 5 : 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                if s.open {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(.white.opacity(0.18))
                            Capsule().fill(mint).frame(width: max(4, g.size.width * s.progress)).widgetAccentable()
                        }
                    }
                    .frame(height: 3)
                } else {
                    Label("잠김", systemImage: "lock.fill").font(.system(size: 11, weight: .semibold)).foregroundStyle(.white.opacity(0.7))
                }
            }
            if !small {
                CharSprite(name: "w_\(s.friend)", locked: !s.open, height: 86).offset(x: 6, y: 6)
            }
        }
        .foregroundStyle(.white)
        .environment(\.colorScheme, .dark)
        .containerBackground(for: .widget) {
            LinearGradient(colors: [Color(red: 0x1E / 255, green: 0x24 / 255, blue: 0x36 / 255), Color(red: 0x2A / 255, green: 0x33 / 255, blue: 0x50 / 255)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        .widgetURL(s.url)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(s.open ? "서재, \(s.label). \(s.line)" : "서재 잠김. \(s.line)")
    }
}
