import Foundation
import Observation
import WidgetKit

// 서재 원고: 외전 『이종 공명』 프롤로그~5장 (한글 원고 + 영문 번역본). 사이트 공개 범위와 같아서 앱에 같이 넣는다.
// Resources/story_ko.json · story_en.json 은 tools/story.py 가 MyVault 원고에서 만든다. 두 언어는 블록이 하나씩 맞아서
// 언어를 바꿔도 읽던 자리(블록 번호)가 그대로다.
// 6장~코다(믹스커피 이상)와 본편 1권(프랜차이즈 이상, 한국어만)은 후원 서버에서 받아(StoreAPI) 이 기기에 저장해 둔다.

struct StoryBlock: Codable {
    let k: String           // h 절 제목, q 인용(화면·메시지), p 문단, s 장면 나눔, t 표
    let t: String
    var rows: [[String]]? = nil
}
struct StoryChapter: Codable { let title: String; let name: String; let blocks: [StoryBlock] }
private struct StoryFile: Codable { var lang: String; var from: Int?; let chapters: [StoryChapter] }

enum StoryLang: String, CaseIterable { case ko, en
    var label: String { self == .ko ? "한" : "EN" }
}

/// 외전(side) 또는 본편 1권(vol1)
enum StoryBook: String { case side, vol1
    var need: SupportTier { self == .side ? .mix : .franchise }
    var langs: [StoryLang] { self == .side ? [.ko, .en] : [.ko] }
    /// 앱에 들어 있는 장 수 (외전 프롤로그~5장)
    var bundled: Int { self == .side ? 6 : 0 }
    var route: String { self == .side ? "read:" : "vol1:" }
}

@MainActor @Observable
final class Story {
    static let shared = Story()

    /// 읽기 화면에서 고른 언어. nil 이면 기기 언어를 따른다
    var picked: StoryLang? = UserDefaults.standard.string(forKey: "storyLang").flatMap(StoryLang.init) {
        didSet { UserDefaults.standard.set(picked?.rawValue, forKey: "storyLang") }
    }
    var lang: StoryLang { picked ?? Self.deviceLang }
    /// 그 책에서 실제로 읽는 언어 (본편 1권은 한국어만)
    func lang(_ b: StoryBook) -> StoryLang { b.langs.contains(lang) ? lang : .ko }
    /// 기기 언어 목록의 첫 번째가 한국어면 한글, 아니면 영문
    static var deviceLang: StoryLang { (Locale.preferredLanguages.first ?? "ko").hasPrefix("ko") ? .ko : .en }
    /// 토글: 기기 언어를 고르면 다시 '기기 언어 따라가기'로
    func pick(_ l: StoryLang) { picked = l == Self.deviceLang ? nil : l }

    /// 장마다 읽던 블록 번호 (이 기기에만 둔다). 외전은 장 번호, 본편 1권은 1000 + 장 번호
    private(set) var pos: [Int: Int] = (UserDefaults.standard.dictionary(forKey: "storyPos") as? [String: Int] ?? [:])
        .reduce(into: [:]) { d, kv in if let i = Int(kv.key) { d[i] = kv.value } }
    static func key(_ b: StoryBook, _ i: Int) -> Int { b == .side ? i : 1000 + i }
    func setPos(_ b: StoryBook, _ chapter: Int, _ block: Int) {
        let k = Self.key(b, chapter)
        guard pos[k] != block else { return }
        pos[k] = block
        UserDefaults.standard.set(Dictionary(uniqueKeysWithValues: pos.map { (String($0.key), $0.value) }), forKey: "storyPos")
    }

    @ObservationIgnored private var bundledBooks: [StoryLang: [StoryChapter]] = [:]
    /// 서버에서 받은 장: "side_ko" → (첫 장 번호, 장들)
    private var remote: [String: (from: Int, chapters: [StoryChapter])] = Story.loadCache()
    /// 받는 중인 책 ("side_ko"), 받다가 난 오류
    private(set) var loading: Set<String> = []
    private(set) var failure: [String: String] = [:]

    nonisolated private static func id(_ b: StoryBook, _ l: StoryLang) -> String { "\(b.rawValue)_\(l.rawValue)" }

    func chapter(_ i: Int, _ b: StoryBook = .side, _ l: StoryLang? = nil) -> StoryChapter? {
        let l = l ?? lang(b)
        if b == .side && i < b.bundled {
            if bundledBooks[l] == nil,
               let url = Bundle.main.url(forResource: "story_\(l.rawValue)", withExtension: "json"),
               let d = try? Data(contentsOf: url), let f = try? JSONDecoder().decode(StoryFile.self, from: d) {
                bundledBooks[l] = f.chapters
            }
            return bundledBooks[l].flatMap { i < $0.count ? $0[i] : nil }
        }
        guard let r = remote[Self.id(b, l)], i >= r.from, i - r.from < r.chapters.count else { return nil }
        return r.chapters[i - r.from]
    }
    /// 지금 읽을 수 있는 장 수 (앱에 든 것 + 받아 둔 것)
    func count(_ b: StoryBook, _ l: StoryLang? = nil) -> Int {
        let l = l ?? lang(b)
        guard let r = remote[Self.id(b, l)] else { return b.bundled }
        return max(b.bundled, r.from + r.chapters.count)
    }
    func hasRemote(_ b: StoryBook, _ l: StoryLang? = nil) -> Bool { remote[Self.id(b, l ?? lang(b))] != nil }
    func isLoading(_ b: StoryBook) -> Bool { loading.contains(Self.id(b, lang(b))) }
    func failText(_ b: StoryBook) -> String? { failure[Self.id(b, lang(b))] }

    /// 서버에서 받기 (등급이 되면). 받은 것은 이 기기에 저장해서 다음부터는 바로 연다
    func fetch(_ b: StoryBook, force: Bool = false) async {
        let l = lang(b), k = Self.id(b, l)
        guard Support.shared.has(b.need), !loading.contains(k), force || remote[k] == nil else { return }
        loading.insert(k); failure[k] = nil
        defer { loading.remove(k) }
        do {
            let r = try await StoreAPI.book(b, l)
            let got = StoryLang(rawValue: r.lang) ?? l
            remote[Self.id(b, got)] = (r.from, r.chapters)
            Self.saveCache(Self.id(b, got), StoryFile(lang: r.lang, from: r.from, chapters: r.chapters))
        } catch {
            failure[k] = error.localizedDescription
        }
    }

    // 받은 원고 저장: Application Support/story/side_ko.json
    nonisolated private static var cacheDir: URL {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("story", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }
    nonisolated private static func saveCache(_ id: String, _ f: StoryFile) {
        if let d = try? JSONEncoder().encode(f) { try? d.write(to: cacheDir.appendingPathComponent(id + ".json"), options: .completeFileProtection) }
    }
    nonisolated private static func loadCache() -> [String: (from: Int, chapters: [StoryChapter])] {
        var out: [String: (from: Int, chapters: [StoryChapter])] = [:]
        for b in [StoryBook.side, .vol1] {
            for l in b.langs {
                let id = Self.id(b, l)
                if let d = try? Data(contentsOf: cacheDir.appendingPathComponent(id + ".json")),
                   let f = try? JSONDecoder().decode(StoryFile.self, from: d) {
                    out[id] = (f.from ?? b.bundled, f.chapters)
                }
            }
        }
        return out
    }

    /// 머리글 "외전 『이종 공명』 · 1장" / "Resonance Across Kinds · Chapter 1" / "본편 1권 · 1장"
    func heading(_ i: Int, _ b: StoryBook = .side) -> String {
        let t = chapter(i, b)?.title ?? (b == .side && i < 6 ? Shelter.chapters[i].title : "")
        if b == .vol1 { return "『중첩된 현실』 1권 · \(t)" }
        return lang == .ko ? "외전 『이종 공명』 · \(t)" : "Resonance Across Kinds · \(t)"
    }

    /// 위젯에 넘길 값: 마지막으로 연 장의 읽던 자리 (앱에 든 외전 프롤로그~5장)
    func widget(_ m: AppModel) -> WStory {
        let i = min(m.readLast ?? 0, 5), open = m.chapterOn(i)
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
