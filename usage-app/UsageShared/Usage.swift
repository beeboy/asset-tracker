import Foundation
import UIKit

// Claude 사용량 위젯 (본인용 별도 앱). naeilo 앱 코드는 건드리지 않고, 같은 App Group 에서 '내 캐릭터'(me.json · me.png)만 읽는다.
// 사용량은 naeilo 중계(Cloudflare Worker)의 /cu 가 Claude 계정 토큰으로 받아 두고, 앱·위젯은 그 값만 받는다 (폰에는 토큰이 없다).

enum CU {
    static let appGroup = "group.com.naeilo.widget"
    static let feed = URL(string: "https://asset-ai.drinker.workers.dev/cu")!
    static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }
    static var group: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) }

    /// 앱에서 고른 캐릭터 (위젯 기본값이 따라간다)
    static var charId: String {
        get { defaults.string(forKey: "cu.char") ?? "seri" }
        set { defaults.set(newValue, forKey: "cu.char") }
    }
}

/// 중계가 주는 사용량. p 는 0~100 (%), reset 은 리셋 시각
struct Usage: Codable, Equatable {
    struct Limit: Codable, Equatable { var p: Double; var reset: Date? }
    var five: Limit
    var week: Limit
    var at: Date

    static let sample = Usage(five: .init(p: 62, reset: Date().addingTimeInterval(2 * 3600 + 13 * 60)),
                              week: .init(p: 31, reset: Date().addingTimeInterval(3 * 86400)), at: Date())

    /// 리셋 시각이 지난 한도는 0 으로 본다 (다음에 받을 때까지)
    func asOf(_ now: Date) -> Usage {
        var u = self
        if let r = u.five.reset, r <= now { u.five.p = 0; u.five.reset = nil }
        if let r = u.week.reset, r <= now { u.week.p = 0; u.week.reset = nil }
        return u
    }
    var p5: Int { Int(min(100, max(0, five.p)).rounded()) }
    var pw: Int { Int(min(100, max(0, week.p)).rounded()) }
    var weekFull: Bool { pw >= 100 }

    /// 대사 단계: 두 한도 중 더 많이 쓴 쪽 기준. 주간 100% 는 따로
    var level: String {
        if weekFull { return "wfull" }
        let p = max(p5, pw)
        return p >= 100 ? "full" : p >= 80 ? "hi" : p >= 50 ? "mid" : "ok"
    }

    // 저장 (App Group). 위젯과 앱이 같이 쓴다
    private static var file: URL? { CU.group?.appendingPathComponent("claude-usage.json") }
    static var saved: Usage? {
        guard let f = file, let d = try? Data(contentsOf: f) else { return nil }
        return try? JSONDecoder.cu.decode(Usage.self, from: d)
    }
    func save() { if let f = Self.file, let d = try? JSONEncoder.cu.encode(self) { try? d.write(to: f, options: .atomic) } }

    /// 중계에서 받는다. 실패하면 이유 문장
    static func fetch() async -> Result<Usage, FetchError> {
        var req = URLRequest(url: CU.feed, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 12)
        req.setValue("naeilo-usage", forHTTPHeaderField: "User-Agent")
        guard let (d, _) = try? await URLSession.shared.data(for: req) else { return .failure(.init(msg: String(localized: "중계에 닿지 못했어요"))) }
        if let u = try? JSONDecoder.cu.decode(Usage.self, from: d) { u.save(); return .success(u) }
        let e = (try? JSONDecoder().decode([String: String].self, from: d))?["error"]
        return .failure(.init(msg: e ?? String(localized: "사용량을 읽지 못했어요")))
    }
    struct FetchError: Error { let msg: String }
}

extension JSONDecoder {
    static let cu: JSONDecoder = { let d = JSONDecoder(); d.dateDecodingStrategy = .iso8601; return d }()
}
extension JSONEncoder {
    static let cu: JSONEncoder = { let e = JSONEncoder(); e.dateEncodingStrategy = .iso8601; return e }()
}

/// 남은 시간: 5시간은 "2:13", 주간은 "목 09:00"
enum CUTime {
    static func left(_ r: Date?, _ now: Date) -> String {
        guard let r else { return "–" }
        let m = max(0, Int(r.timeIntervalSince(now) / 60))
        return String(format: "%d:%02d", m / 60, m % 60)
    }
    static func weekday(_ r: Date?) -> String {
        guard let r else { return "–" }
        let f = DateFormatter(); f.locale = Locale(identifier: Bundle.main.preferredLocalizations.first == "en" ? "en_US" : "ko_KR"); f.dateFormat = "E HH:mm"
        return f.string(from: r)
    }
}

/// 캐릭터: 색은 naeilo 위젯과 같다 (ios-app/WidgetShared/Reward.swift WChar)
struct UChar: Identifiable, Equatable {
    let id: String, name: String
    let accent: UInt32, g0: UInt32, g1: UInt32
    var voice: String

    static let all: [UChar] = [
        .init(id: "seri", name: String(localized: "세리"), accent: 0x5ab8ff, g0: 0x16203f, g1: 0x2c3e8a, voice: "seri"),
        .init(id: "sio", name: String(localized: "시오"), accent: 0x7fd6ff, g0: 0x122838, g1: 0x265c76, voice: "sio"),
        .init(id: "seonbae", name: String(localized: "선배"), accent: 0xe0b25a, g0: 0x301e2c, g1: 0x744658, voice: "seonbae"),
        .init(id: "ir", name: String(localized: "이르"), accent: 0xffd36b, g0: 0x1a1830, g1: 0x4a3f72, voice: "ir"),
        .init(id: "sua", name: String(localized: "수아"), accent: 0xf2a070, g0: 0x3a2228, g1: 0xb06448, voice: "sua"),
    ]
    /// naeilo 의 내 캐릭터 (만들었을 때만)
    static var me: UChar? {
        guard let m = UMe.saved, UMe.image != nil else { return nil }
        return .init(id: "me", name: m.name, accent: m.accent, g0: m.g0, g1: m.g1, voice: lines[m.voice] == nil ? "seri" : m.voice)
    }
    static var choices: [UChar] { all + (me.map { [$0] } ?? []) }
    static func of(_ id: String) -> UChar { id == "me" ? (me ?? all[0]) : (all.first { $0.id == id } ?? all[0]) }
    static var current: UChar { of(CU.charId) }

    /// 서 있는 그림 (내 캐릭터는 me.png 한 장)
    var sprite: UIImage? { id == "me" ? UMe.image : UIImage(named: "u_\(id)_spread") }
    var face: UIImage? { id == "me" ? UMe.image : UIImage(named: "u_\(id)_face") }

    /// 단계별 대사 (숫자는 블록이 보여 주니 말하지 않는다)
    static let lines: [String: [String: String]] = [
        "seri": ["ok": String(localized: "오늘은 마음껏 물어봐요."), "mid": String(localized: "중요한 것부터 해요."), "hi": String(localized: "큰 건 리셋 뒤로 미뤄요."), "full": String(localized: "잠깐 산책 다녀와요."), "wfull": String(localized: "이번 주는 여기까지예요.")],
        "sio": ["ok": String(localized: "써도 돼."), "mid": String(localized: "하나씩."), "hi": String(localized: "짧게 물어."), "full": String(localized: "쉬어."), "wfull": String(localized: "이번 주 끝.")],
        "seonbae": ["ok": String(localized: "오늘 뭘 만들고 싶어?"), "mid": String(localized: "지금 이게 제일 중요해?"), "hi": String(localized: "남은 건 어디에 쓸래?"), "full": String(localized: "커피 한 잔 하고 와."), "wfull": String(localized: "이번 주는 푹 쉬자.")],
        "ir": ["ok": String(localized: "그거면 돼."), "mid": String(localized: "나도 빨랐어."), "hi": String(localized: "천천히."), "full": String(localized: "멈출 때야."), "wfull": String(localized: "다음 주에 봐.")],
        "sua": ["ok": String(localized: "뭐부터 해 볼까요?"), "mid": String(localized: "아직 고르는 중이에요."), "hi": String(localized: "조금 아껴 볼까요?"), "full": String(localized: "기다리는 것도 괜찮아요."), "wfull": String(localized: "이번 주는 쉬어 가요.")],
    ]
    func line(_ u: Usage) -> String { Self.lines[voice]?[u.level] ?? Self.lines["seri"]![u.level]! }
}

/// naeilo 앱이 App Group 에 쓰는 내 캐릭터 (ios-app/WidgetShared/Reward.swift WMe 와 같은 형식)
struct UMe: Codable {
    var name: String
    var voice: String
    var accent: UInt32, g0: UInt32, g1: UInt32
    private static func data(_ n: String) -> Data? {
        CU.group.flatMap { try? Data(contentsOf: $0.appendingPathComponent("naeilo", isDirectory: true).appendingPathComponent(n)) }
    }
    static var saved: UMe? { data("me.json").flatMap { try? JSONDecoder().decode(UMe.self, from: $0) } }
    static var image: UIImage? { data("me.png").flatMap { UIImage(data: $0) } }
}
