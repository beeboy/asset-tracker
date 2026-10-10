import WidgetKit
import AppIntents
import SwiftUI

struct Entry: TimelineEntry {
    let date: Date
    let snap: Snapshot
    var preview = false     // 위젯 갤러리 미리보기면 잠그지 않고 실제 모습을 보여 준다
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry { Entry(date: Date(), snap: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        // 위젯 추가 화면 미리보기도 내 데이터로 (연결 전이면 Engine 이 예시 값을 준다)
        completion(Entry(date: Date(), snap: Engine.snapshot(), preview: context.isPreview))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        Task {
            // 숫자는 naeilo 앱이 계산해서 App Group 폴더에 써 둔다. 위젯은 읽어서 그리기만 한다
            completion(Timeline(entries: [Entry(date: Date(), snap: Engine.snapshot())], policy: .after(MarketHours.nextRefresh())))
        }
    }
}
