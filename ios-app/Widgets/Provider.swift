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
            // 숫자는 naeilo 앱이 계산해서 App Group 폴더에 써 둔다. 지금 가격·환율만 위젯이 사이트에서 직접 받는다 (10분 안에 받았으면 그대로)
            await QuoteFeed.fetch(minAge: 600)
            // 내부 테스트 국내 시세 종목이 있으면 그것도 (한국 장중에도 갱신)
            let ky = Store.read(WFeed.self, "feed.json")?.ky ?? [:]
            await KrQuoteFeed.fetch(ky, minAge: 600)
            completion(Timeline(entries: [Entry(date: Date(), snap: Engine.snapshot())], policy: .after(MarketHours.nextRefresh(kr: !ky.isEmpty))))
        }
    }
}
