import AppIntents
import WidgetKit

/// 위젯의 ↻ 버튼: 새 값이 있으면 받고, 버튼이 없는 위젯까지 모두 다시 그린다
struct ReloadIntent: AppIntent {
    static var title: LocalizedStringResource = "다시 받기"
    func perform() async throws -> some IntentResult {
        _ = try? await withTimeout(15) { try await Refresher.refresh() }
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
