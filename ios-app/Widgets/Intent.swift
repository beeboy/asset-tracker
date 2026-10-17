import AppIntents
import WidgetKit

/// 위젯의 ↻ 버튼: 사이트 시세와 (내 키가 있으면) 토스 지금 가격을 바로 받고 모든 위젯을 다시 그린다
struct ReloadIntent: AppIntent {
    static var title: LocalizedStringResource = "다시 받기"
    func perform() async throws -> some IntentResult {
        // 누르자마자 버튼을 '받는 중'으로 다시 그린다 (받기가 끝날 때까지 기다리면 반응이 없어 보인다)
        Store.reloadingSince = Date()
        WidgetLine.bump(minGap: 0)   // ↻: 인물 대사도 다음 줄로 (바로 이어지는 다시 그리기는 세지 않는다)
        WidgetCenter.shared.reloadAllTimelines()
        let before = Store.newestGot
        _ = try? await withTimeout(8) {
            async let site: Void = { _ = await QuoteFeed.fetch() }()
            async let toss: Void = { _ = await TossFeed.widgetFetch(minAge: 0) }()
            async let alpaca: Void = { _ = await AlpacaFeed.widgetFetch(minAge: 0) }()
            // 국내 테스트 시세 종목도 ↻ 때 바로 (전에는 정기 갱신 때만 받아 ↻로는 안 바뀌었다)
            async let kr: Void = { _ = await KrQuoteFeed.fetch(Store.read(WFeed.self, "feed.json")?.ky ?? [:], minAge: 0) }()
            _ = await (site, toss, kr, alpaca)
        }
        // 하나도 새로 받지 못했으면 위젯 아래 줄에 '받기 실패'
        Store.fetchFailed = Store.newestGot <= before
        Store.reloadingSince = nil
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// 서재 위젯의 ↻: 시세는 받지 않고 읽던 자리(story.json)로 서재 위젯만 다시 그린다.
/// 잠깐 '받는 중'으로 그렸다가 되돌려, 다른 위젯과 같은 흐림·뛰어오름 효과를 낸다
struct StoryReloadIntent: AppIntent {
    static var title: LocalizedStringResource = "서재 다시 그리기"
    func perform() async throws -> some IntentResult {
        Store.reloadingSince = Date()
        WidgetCenter.shared.reloadTimelines(ofKind: "story")
        try? await Task.sleep(nanoseconds: 800_000_000)
        Store.reloadingSince = nil
        WidgetCenter.shared.reloadTimelines(ofKind: "story")
        return .result()
    }
}

func withTimeout<T: Sendable>(_ sec: Double, _ op: @escaping @Sendable () async throws -> T) async throws -> T {
    try await withThrowingTaskGroup(of: T.self) { g in
        g.addTask { try await op() }
        g.addTask { try await Task.sleep(nanoseconds: UInt64(sec * 1e9)); throw CancellationError() }
        let r = try await g.next()!
        g.cancelAll()
        return r
    }
}

/// 큰 금액을 누르면 숨기기·다시 보이기 (기본은 모든 위젯이 같이)
struct ToggleAmountIntent: AppIntent {
    static var title: LocalizedStringResource = "금액 숨기기"
    @Parameter(title: "위젯") var kind: String
    init() {}
    init(kind: String) { self.kind = kind }
    func perform() async throws -> some IntentResult {
        Store.toggleHidden(kind)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
