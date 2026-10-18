import WidgetKit
import SwiftUI

// 서재 위젯: 외전 『이종 공명』에서 읽던 자리의 한 문단. 위젯은 스크롤이 안 되니 한 문단만 보여 주고, 누르면 앱의 그 장 그 자리로 간다.
// 값은 앱이 story.json 으로 쓴다 (언어도 앱의 읽기 화면에서 고른 것). 서재가 아직 안 열렸으면 앱 시작 3단계로 간다.
// ↻: 시세는 받지 않고 서재 위젯만 다시 그린다 (StoryReloadIntent). 그동안 글·막대가 흐려지고 인물이 뛰어올랐다 내려앉는다 (Flip.swift)

struct StoryEntry: TimelineEntry {
    let date: Date
    let s: WStory
    var preview = false     // 위젯 갤러리 미리보기면 잠그지 않는다 (다른 위젯과 같이)
}

struct StoryProvider: TimelineProvider {
    func placeholder(in context: Context) -> StoryEntry { StoryEntry(date: Date(), s: .sample) }
    func getSnapshot(in context: Context, completion: @escaping (StoryEntry) -> Void) {
        let s = Store.read(WStory.self, "story.json")
        completion(StoryEntry(date: Date(), s: context.isPreview && !(s?.open ?? false) ? .sample : s ?? .sample, preview: context.isPreview))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<StoryEntry>) -> Void) {
        // 읽던 자리가 바뀔 때 앱이 다시 그리게 한다
        let s = Store.read(WStory.self, "story.json") ?? WStory(open: false, chapter: 0, label: "프롤로그 · 핵심 코어", line: "앱을 열면 서재가 준비돼요.", friend: "seri", progress: 0, lang: "ko")
        // 7일 무료가 끝나는 때에 한 번 더 그려 잠근다
        let end = Store.trialEnd
        completion(Timeline(entries: [StoryEntry(date: Date(), s: s)], policy: !Store.subOn && end > Date().timeIntervalSince1970 ? .after(Date(timeIntervalSince1970: end)) : .never))
    }
}

struct StoryWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "story", provider: StoryProvider()) { e in Gate(kind: "story", preview: e.preview) { StoryWidgetView(s: e.s, date: e.date) } }
            .catalog("story")
            .supportedFamilies([.systemSmall])
    }
}

/// 작은 서재 (시안): 머리 '서재 · 3분 전 ↻', 장 그림 띠, 장 제목, 읽던 문장 한두 줄을 인용처럼, 아래 진행 숫자·막대와 인물
struct StoryWidgetView: View {
    let s: WStory
    var date = Date()
    @Environment(\.widgetRenderingMode) private var mode
    private let mint = Color(red: 0x5F / 255, green: 0xD0 / 255, blue: 0xC4 / 255)

    var body: some View {
        let en = s.lang == "en"
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Text(en ? "Library" : "서재").font(.system(size: 11, weight: .semibold)).foregroundStyle(mint).widgetAccentable()
                Spacer(minLength: 0)
                Text(Fresh.ago(Self.written, now: date)).font(.system(size: 10, weight: .medium)).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                CharReload(storyOnly: true)
            }
            art.frame(height: 34).frame(maxWidth: .infinity).clipShape(RoundedRectangle(cornerRadius: 6)).flipFade()
            Text(s.label).font(.system(size: 11, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8).opacity(0.85).flipFade()
            Text("“\(s.line)”")
                .font(.system(size: 13, design: .serif).italic())
                .lineSpacing(1)
                .foregroundStyle(.white.opacity(s.open ? 0.92 : 0.7))
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .flipFade()
            Spacer(minLength: 0)
            HStack(alignment: .bottom, spacing: 6) {
                if s.open {
                    VStack(spacing: 3) {
                        HStack {
                            Text("\(Int((s.progress * 100).rounded()))%").foregroundStyle(mint).widgetAccentable()
                            Spacer(minLength: 0)
                            Text(where_(en)).opacity(0.7)
                        }
                        .font(.system(size: 10, weight: .semibold)).lineLimit(1)
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(.white.opacity(0.18))
                                Capsule().fill(mint).frame(width: max(4, g.size.width * s.progress)).widgetAccentable()
                            }
                        }
                        .frame(height: 3)
                    }
                    .flipFade()
                } else {
                    Label(en ? "Locked" : "잠김", systemImage: "lock.fill").font(.system(size: 11, weight: .semibold)).foregroundStyle(.white.opacity(0.7))
                    Spacer(minLength: 0)
                }
                CharSprite(name: "w_\(s.friend)", locked: !s.open, height: 30).offset(x: 4, y: 4)
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
        .accessibilityLabel(en ? (s.open ? "Library, \(s.label). \(s.line)" : "Library locked. \(s.line)")
                            : (s.open ? "서재, \(s.label). \(s.line)" : "서재 잠김. \(s.line)"))
    }

    /// "6장 중 2장" (프롤로그는 '프롤로그')
    private func where_(_ en: Bool) -> String {
        if s.chapter == 0 { return en ? "Prologue" : "프롤로그" }
        return en ? "Ch. \(s.chapter) of 6" : "6장 중 \(s.chapter)장"
    }

    /// 장 그림 띠: 투명·틴트에서도 그림 원래 색 그대로 (인물 그림과 같게). 그림이 아직 없으면 줄무늬 자리
    @ViewBuilder private var art: some View {
        if let img = UIImage(contentsOfFile: Store.url(WStory.artFile).path) {
            let pic = Image(uiImage: img).interpolation(.none).resizable()
            if mode == .accented, #available(iOS 18, *) { pic.widgetAccentedRenderingMode(.fullColor).aspectRatio(contentMode: .fill) } else { pic.aspectRatio(contentMode: .fill) }
        } else {
            RoundedRectangle(cornerRadius: 6).fill(mint.opacity(0.18))
        }
    }

    /// 앱이 story.json 을 마지막으로 쓴 때
    static var written: Date? { (try? FileManager.default.attributesOfItem(atPath: Store.url("story.json").path))?[.modificationDate] as? Date }
}
