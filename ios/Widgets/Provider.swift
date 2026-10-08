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
        // 위젯 추가 화면 미리보기도 내 데이터로 (연결 전이면 Engine 이 예시 값을 준다)
        completion(Entry(date: Date(), snap: Engine.snapshot()))
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
