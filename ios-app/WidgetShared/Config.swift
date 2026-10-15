import Foundation

enum Config {
    static let appGroup = "group.com.naeilo.widget"
    static let site = URL(string: "https://naeilo.com/")!
    static let data = URL(string: "https://naeilo.com/data/")!
    static let relay = URL(string: "https://asset-ai.drinker.workers.dev/")!
    /// 토스증권 공용 중계 (고정 IP 서버, proxy/toss_relay.py). 사용자는 이 서버 IP 를 토스 허용 IP 로 등록한다
    static let tossRelay = "https://toss.naeilo.com"

    /// 배포 단계. 내부 베타까지만 사이트가 Yahoo 에서 모은 시세·종가·환율·계산 자료를 쓴다 (2026-10-15 결정).
    /// 공개 베타·출시 빌드는 이 값을 바꾸고, 사용자 본인 키로 받은 시세와 가상 샘플만 쓴다 (아직 구현 안 함)
    static let channel: Channel = .internalBeta
    /// 사이트가 Yahoo 로 모은 자료(data/quotes.json·prices·stats·calc 등, 추가 종목 Yahoo 중계)를 써도 되는 빌드인가
    static var usesCollectedData: Bool { channel == .internalBeta }
}

enum Channel: String {
    case internalBeta = "내부 베타"
    case publicBeta = "공개 베타"
    case release = "출시"
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

    /// 금액 숨기기. 기본은 한 번 누르면 모든 위젯이 같이 (hideEach = false), 설정에서 위젯마다 따로로 바꿀 수 있다
    static var hideEach: Bool {
        get { defaults.bool(forKey: "hideEach") }
        set { defaults.set(newValue, forKey: "hideEach") }
    }
    static var hideAll: Bool {
        get { defaults.bool(forKey: "hideAll") }
        set { defaults.set(newValue, forKey: "hideAll") }
    }
    static var hiddenKinds: [String] {
        get { defaults.stringArray(forKey: "hiddenKinds") ?? [] }
        set { defaults.set(newValue, forKey: "hiddenKinds") }
    }
    static func isHidden(_ kind: String) -> Bool { hideEach ? hiddenKinds.contains(kind) : hideAll }
    static func toggleHidden(_ kind: String) {
        if hideEach {
            var k = hiddenKinds
            if let i = k.firstIndex(of: kind) { k.remove(at: i) } else { k.append(kind) }
            hiddenKinds = k
        } else { hideAll.toggle() }
    }

    /// 받은 위젯 (앱이 앱 시작 단계에 맞춰 쓴다). 한 번도 안 썼으면 nil = 모두 열림
    static var unlockedKinds: [String]? {
        get { defaults.stringArray(forKey: "unlockedKinds") }
        set { defaults.set(newValue, forKey: "unlockedKinds") }
    }
    static func isUnlocked(_ kind: String) -> Bool { unlockedKinds.map { $0.contains(kind) } ?? true }
}

/// 위젯 받는 조건: 앱 시작 1·2·3단계, 3단계를 마치면 특별 선물로 나머지 전부
enum WidgetUnlock {
    static let step: [String: Int] = [
        "asset.small": 1, "lock.asset": 1,
        "pace.medium": 2, "lock.goal": 2,
        "block.small": 3,
        "future.small": 4, "target.medium": 4, "moves.medium": 4, "future.large": 4, "lock.future": 4, "lock.target": 4,
    ]
    static func kinds(doneSteps n: Int) -> [String] { step.filter { $0.value <= (n >= 3 ? 4 : n) }.map(\.key).sorted() }
    static func how(_ kind: String) -> String {
        switch step[kind] ?? 4 {
        case 1: "앱 시작 1단계(종목 추가)를 하면 열려요"
        case 2: "앱 시작 2단계(알림 켜기)를 하면 열려요"
        case 3: "앱 시작 3단계(기기 동기화)를 하면 열려요"
        default: "앱 시작 3단계를 마치면 특별 선물로 열려요"
        }
    }
}
