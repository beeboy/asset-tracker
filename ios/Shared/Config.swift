import Foundation

enum Config {
    static let appGroup = "group.com.naeilo.widget"
    static let site = URL(string: "https://naeilo.com/")!
    static let data = URL(string: "https://naeilo.com/data/")!
    static let relay = URL(string: "https://asset-ai.drinker.workers.dev/")!
    /// 사이트가 계산 요약 형식을 바꾸면 web/widget-core.js 의 VERSION 과 같이 올린다
    static let summaryVersion = 1
    /// 전망 계산에 쓰는 요인 종목 (web/widget-core.js FACTORS 와 같음) + 환율
    static let extraSymbols = ["KRW=X", "SPY", "^TNX", "CL=F", "GC=F", "DBC"]
}

/// 앱과 위젯이 같이 쓰는 저장소 (App Group 폴더의 JSON 파일)
enum Store {
    static var dir: URL {
        let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Config.appGroup)
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("naeilo", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }
    static func url(_ name: String) -> URL { dir.appendingPathComponent(name) }

    static func read<T: Decodable>(_ t: T.Type, _ name: String) -> T? {
        guard let d = try? Data(contentsOf: url(name)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: d)
    }
    static func write<T: Encodable>(_ v: T, _ name: String) {
        if let d = try? JSONEncoder().encode(v) { writeData(d, name) }
    }
    static func readData(_ name: String) -> Data? { try? Data(contentsOf: url(name)) }
    static func writeData(_ d: Data, _ name: String) {
        try? d.write(to: url(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    static func remove(_ name: String) { try? FileManager.default.removeItem(at: url(name)) }

    static var defaults: UserDefaults { UserDefaults(suiteName: Config.appGroup) ?? .standard }

    /// 위젯 갱신 주기 (분). 장중에만 이 주기, 장 밖에서는 3시간
    static var intervalMin: Int {
        get { let v = defaults.integer(forKey: "intervalMin"); return v > 0 ? v : 30 }
        set { defaults.set(newValue, forKey: "intervalMin") }
    }
    static var lastCheck: Date? {
        get { defaults.object(forKey: "lastCheck") as? Date }
        set { defaults.set(newValue, forKey: "lastCheck") }
    }
    /// 지금 쓰는 요약이 어디서 왔는지 ("site" / "app")
    static var summarySource: String? {
        get { defaults.string(forKey: "summarySource") }
        set { defaults.set(newValue, forKey: "summarySource") }
    }

    static func wipe() {
        try? FileManager.default.removeItem(at: dir)
        for k in ["lastCheck", "summarySource", "etags"] { defaults.removeObject(forKey: k) }
    }
}
