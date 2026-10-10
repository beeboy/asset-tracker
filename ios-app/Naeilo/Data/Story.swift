import Foundation
import Observation
import WidgetKit

// 서재 원고: 외전 『이종 공명』 프롤로그~5장 (한글 원고 + 영문 번역본). 사이트 공개 범위와 같아서 앱에 같이 넣는다.
// Resources/story_ko.json · story_en.json 은 tools/story.py 가 MyVault 원고에서 만든다. 두 언어는 블록이 하나씩 맞아서
// 언어를 바꿔도 읽던 자리(블록 번호)가 그대로다. 6장부터는 나중에 서버에서 받는다 (구매 확인 후).

struct StoryBlock: Decodable {
    let k: String           // h 절 제목, q 인용(화면·메시지), p 문단, s 장면 나눔, t 표
    let t: String
    var rows: [[String]]? = nil
}
struct StoryChapter: Decodable { let title: String; let name: String; let blocks: [StoryBlock] }
private struct StoryBook: Decodable { let lang: String; let chapters: [StoryChapter] }

enum StoryLang: String, CaseIterable { case ko, en
    var label: String { self == .ko ? "한" : "EN" }
}

@MainActor @Observable
final class Story {
    static let shared = Story()

    /// 읽기 화면에서 고른 언어. nil 이면 기기 언어를 따른다
    var picked: StoryLang? = UserDefaults.standard.string(forKey: "storyLang").flatMap(StoryLang.init) {
        didSet { UserDefaults.standard.set(picked?.rawValue, forKey: "storyLang") }
    }
    var lang: StoryLang { picked ?? Self.deviceLang }
    /// 기기 언어 목록의 첫 번째가 한국어면 한글, 아니면 영문
    static var deviceLang: StoryLang { (Locale.preferredLanguages.first ?? "ko").hasPrefix("ko") ? .ko : .en }
    /// 토글: 기기 언어를 고르면 다시 '기기 언어 따라가기'로
    func pick(_ l: StoryLang) { picked = l == Self.deviceLang ? nil : l }

    /// 장마다 읽던 블록 번호 (이 기기에만 둔다)
    private(set) var pos: [Int: Int] = (UserDefaults.standard.dictionary(forKey: "storyPos") as? [String: Int] ?? [:])
        .reduce(into: [:]) { d, kv in if let i = Int(kv.key) { d[i] = kv.value } }
    func setPos(_ chapter: Int, _ block: Int) {
        guard pos[chapter] != block else { return }
        pos[chapter] = block
        UserDefaults.standard.set(Dictionary(uniqueKeysWithValues: pos.map { (String($0.key), $0.value) }), forKey: "storyPos")
    }

    @ObservationIgnored private var books: [StoryLang: [StoryChapter]] = [:]
    func chapter(_ i: Int, _ l: StoryLang? = nil) -> StoryChapter? {
        let l = l ?? lang
        if books[l] == nil,
           let url = Bundle.main.url(forResource: "story_\(l.rawValue)", withExtension: "json"),
           let d = try? Data(contentsOf: url), let b = try? JSONDecoder().decode(StoryBook.self, from: d) {
            books[l] = b.chapters
        }
        return books[l].flatMap { i < $0.count ? $0[i] : nil }
    }

    /// 머리글 "외전 『이종 공명』 · 1장" / "Resonance Across Kinds · Chapter 1"
    func heading(_ i: Int) -> String {
        let t = chapter(i)?.title ?? Shelter.chapters[i].title
        return lang == .ko ? "외전 『이종 공명』 · \(t)" : "Resonance Across Kinds · \(t)"
    }

    /// 위젯에 넘길 값: 마지막으로 연 장의 읽던 자리
    func widget(_ m: AppModel) -> WStory {
        let i = m.readLast ?? 0, open = m.chapterOn(i)
        let ch = chapter(i), blocks = ch?.blocks ?? []
        let at = min(pos[i] ?? 0, max(0, blocks.count - 1))
        // 읽던 자리부터 처음 나오는 문단·인용 (절 제목·표는 건너뛴다)
        let text = blocks.indices.dropFirst(at).first { ["p", "q"].contains(blocks[$0].k) }.map { blocks[$0].t } ?? ""
        return WStory(open: open, chapter: i,
                      label: (ch?.title ?? Shelter.chapters[i].title) + " · " + (ch?.name ?? Shelter.chapters[i].name),
                      line: open ? Self.plain(text) : "앱 시작 3단계를 마치면 서재가 열려요.",
                      friend: Shelter.chapters[i].friend,
                      progress: blocks.isEmpty ? 0 : Double(at) / Double(blocks.count),
                      lang: lang.rawValue)
    }

    /// 서재 위젯만 다시 그린다 (읽던 자리·언어가 바뀌었을 때)
    func pushWidget(_ m: AppModel) {
        Store.write(widget(m), "story.json")
        WidgetCenter.shared.reloadTimelines(ofKind: "story")
    }

    /// **굵게**·_기울임_·~~지움~~ 기호를 뺀 글 (위젯은 짧게 한 문단만)
    static func plain(_ s: String) -> String {
        let a = (try? AttributedString(markdown: s, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(s)
        return String(a.characters).replacingOccurrences(of: "\n", with: " ")
    }
}
