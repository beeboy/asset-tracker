import Foundation

/// 서재 위젯이 읽는 값 (앱이 story.json 으로 쓴다). 위젯은 스크롤이 안 돼서 읽던 자리의 한 문단만 보여 주고, 누르면 앱의 그 장으로 간다
struct WStory: Codable {
    var open: Bool          // 서재가 열렸는지 (앱 시작 3단계)
    var chapter: Int        // 0 프롤로그 … 5 5장
    var label: String       // "2장 · 이름을 허락하는 사람" (고른 언어)
    var line: String        // 읽던 자리의 문단 (마크다운 기호는 뺀 글)
    var friend: String      // 그 장과 같이 열리는 인물 (위젯 그림)
    var progress: Double    // 이 장에서 읽은 만큼 0~1
    var lang: String        // ko | en

    var url: URL { URL(string: open ? "naeilo://read/\(chapter)" : "naeilo://unlock")! }

    static let sample = WStory(open: true, chapter: 1, label: "1장 · 무명의 인터페이스",
                               line: "그는 주식 앱을 닫고 대화형 인공지능 앱을 열었다. 질문을 길게 쓰는 편이 아니었다.",
                               friend: "seri", progress: 0.3, lang: "ko")
}
