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
    /// ↻ 를 누른 시각. 받는 동안 위젯 버튼을 '받는 중'으로 그린다 (다 받으면 지운다)
    static var reloadingSince: Date? {
        get { defaults.object(forKey: "reloadingSince") as? Date }
        set { defaults.set(newValue, forKey: "reloadingSince") }
    }
    /// 받는 중인지 (12초가 지나면 끝난 것으로 본다: 받기는 8초 안에 끊긴다)
    static var isReloading: Bool {
        guard let t = reloadingSince else { return false }
        return Date().timeIntervalSince(t) < 12
    }
    /// 마지막 ↻ 에서 시세를 하나도 새로 받지 못했는지. 위젯 아래 줄에 '받기 실패'로 보인다 (다음에 하나라도 받으면 지운다)
    static var fetchFailed: Bool {
        get { defaults.bool(forKey: "fetchFailed") }
        set { defaults.set(newValue, forKey: "fetchFailed") }
    }
    /// 위젯·앱이 받아 둔 시세 파일(사이트·국내·토스·Alpaca) 중 가장 나중에 받은 시각 (초)
    static var newestGot: Double {
        ["site_quotes.json", "kr_quotes.json", "toss_quotes.json", "alpaca_quotes.json"].compactMap { read(QuoteFeed.File.self, $0)?.got }.max() ?? 0
    }
    /// 잠금화면 위젯의 금액을 폰이 잠겨 있을 때 가린다 (기본 켬). 얼굴·지문으로 풀면 보인다
    static var lockPrivate: Bool {
        get { defaults.object(forKey: "lockPrivate") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "lockPrivate") }
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

    /// 구독 중인지 (앱이 쓴다, Support.pushWidget)
    static var subOn: Bool {
        get { defaults.bool(forKey: "subOn") }
        set { defaults.set(newValue, forKey: "subOn") }
    }
    /// 7일 무료가 끝나는 시각 (1970년부터 초). 0 이면 아직 모름 = 열림
    static var trialEnd: Double {
        get { defaults.double(forKey: "trialEnd") }
        set { defaults.set(newValue, forKey: "trialEnd") }
    }
    /// 위젯 전부(기본·인물·서재)는 구독 중이거나 7일 무료 동안만 보인다 (2026-10-16 결정)
    static var widgetsOpen: Bool { subOn || trialEnd <= 0 || Date().timeIntervalSince1970 < trialEnd }
    static func isUnlocked(_ kind: String) -> Bool { widgetsOpen }
}

/// 잠긴 위젯 문구 (기본·잠금화면·인물·서재가 같이 쓴다). 예전에는 앱 시작 단계로 하나씩 열었는데, 이제는 처음부터 모두 열리고 7일 무료 뒤에는 구독으로 연다
enum WidgetUnlock {
    static func how(_ kind: String) -> String { WL.t("7일 무료가 끝났어요. 구독하면 다시 보여요") }
    static var cta: String { WL.t("눌러서 구독하기 ›") }
}
