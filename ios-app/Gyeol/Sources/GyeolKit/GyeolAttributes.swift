import ActivityKit
import Foundation

/// 결 Live Activity. 상태는 작게 둔다 (푸시 한 번 4KB): 자세·말·숫자 하나·말풍선 줄·순번
public struct GyeolAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// 자세 (GyeolPose 이름). 모르는 이름은 가만히로 그린다
        public var pose: String
        /// 클로드 답 몸짓 때 결이 하는 말. 없으면 말풍선은 위젯 줄을 차례로 보인다
        public var say: String?
        /// 작은 섬 오른쪽 숫자 (오늘 +1.2%). 맥 푸시는 빼고 보내도 된다
        public var chip: String?
        /// 말풍선 줄. 맥 푸시는 빼고 보낸다 (그때는 앱이 마지막으로 남긴 줄)
        public var lines: [String]?
        /// 갱신 순번. 말풍선이 이 값으로 다음 줄로 넘어간다
        public var n: Int

        public init(pose: String, say: String? = nil, chip: String? = nil, lines: [String]? = nil, n: Int) {
            self.pose = pose; self.say = say; self.chip = chip; self.lines = lines; self.n = n
        }
    }
    public init() {}
}

/// 앱이 마지막으로 남긴 말풍선 줄·숫자 (App Group). 맥 푸시가 줄 없이 와도 이걸로 그린다
public enum GyeolShelf {
    static let d = UserDefaults(suiteName: "group.com.naeilo.widget")
    /// 앱 언어 (앱이 App Group 에 적어 둔 appLang). 위젯 확장은 앱별 언어를 못 봐서 이걸 따른다
    static var en: Bool { d?.string(forKey: "appLang") == "en" }
    public static var lines: [String] {
        get { d?.stringArray(forKey: "gyeol.lines") ?? [] }
        set { d?.set(newValue, forKey: "gyeol.lines") }
    }
    public static var chip: String? {
        get { d?.string(forKey: "gyeol.chip") }
        set { d?.set(newValue, forKey: "gyeol.chip") }
    }
}

extension GyeolAttributes.ContentState {
    /// 말풍선에 띄울 한 줄: 결의 말이 먼저, 없으면 위젯 줄을 순번대로
    public var bubble: String {
        if let say, !say.isEmpty { return say }
        let ls = lines ?? GyeolShelf.lines
        return ls.isEmpty ? (GyeolShelf.en ? "Open the app and I'll tell you the numbers" : "앱을 열면 숫자를 알려 줄게") : ls[abs(n) % ls.count]
    }
    public var chipText: String { chip ?? GyeolShelf.chip ?? "" }
}
