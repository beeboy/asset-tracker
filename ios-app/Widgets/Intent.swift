import AppIntents
import WidgetKit

/// 위젯의 ↻ 버튼: 사이트 시세와 (내 키가 있으면) 토스 지금 가격을 바로 받고 모든 위젯을 다시 그린다
struct ReloadIntent: AppIntent {
    static var title: LocalizedStringResource = "다시 받기"
    func perform() async throws -> some IntentResult {
        _ = try? await withTimeout(8) {
            async let site: Void = { _ = await QuoteFeed.fetch() }()
            async let toss: Void = { _ = await TossFeed.widgetFetch(minAge: 0) }()
            _ = await (site, toss)
        }
        WidgetLine.bump(minGap: 0)   // ↻: 인물 대사도 다음 줄로 (바로 이어지는 다시 그리기는 세지 않는다)
        WidgetCenter.shared.reloadAllTimelines()
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
