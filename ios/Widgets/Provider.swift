import WidgetKit
import AppIntents
import SwiftUI

struct Entry: TimelineEntry {
    let date: Date
    let snap: Snapshot
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry { Entry(date: Date(), snap: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(Entry(date: Date(), snap: context.isPreview ? .sample : Engine.snapshot()))
    }

    /// 바뀐 것만 받고(304면 몇백 바이트) 지금 값으로 다시 그린다. 무거운 전망 계산은 앱이 한다
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        Task {
            // ↻ 버튼이나 다른 위젯이 1분 안에 받았으면 그 파일로 그리기만 한다
            if Date().timeIntervalSince(Store.lastCheck ?? .distantPast) > 60 {
                _ = try? await withTimeout(15) { try await Refresher.refresh() }
            }
            completion(Timeline(entries: [Entry(date: Date(), snap: Engine.snapshot())], policy: .after(Market.nextRefresh())))
        }
    }
}

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
