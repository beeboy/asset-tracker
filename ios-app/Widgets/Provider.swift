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
            // ↻ 직후 '받는 중' 그리기: 받지 않고 바로 그린다 (받기는 ReloadIntent 가 하고, 끝나면 다시 그린다)
            if Store.isReloading {
                completion(Timeline(entries: [Entry(date: Date(), snap: Engine.snapshot())], policy: .after(Date().addingTimeInterval(15))))
                return
            }
            // 지금 가격·환율 받기와 다음 갱신 시각은 인물 위젯과 같이 쓴다 (WidgetShared/Format.swift 의 WidgetRefresh)
            let next = await WidgetRefresh.fetch()
            completion(Timeline(entries: [Entry(date: Date(), snap: Engine.snapshot())], policy: .after(next)))
        }
    }
}
