import WidgetKit
import SwiftUI

// MARK: 위젯 정의 (이름표는 Catalog, 잠금은 Gate, 값은 Provider. 새 위젯은 Catalog 에 한 줄, 여기 한 줄, NaeiloWidgets 묶음에 한 줄)
struct AssetSmall: Widget { var body: some WidgetConfiguration { homeWidget("asset.small", .systemSmall) { AssetSmallView(s: $0) } } }
struct FutureSmall: Widget { var body: some WidgetConfiguration { homeWidget("future.small", .systemSmall) { FutureSmallView(s: $0) } } }
struct BlockSmall: Widget { var body: some WidgetConfiguration { homeWidget("block.small", .systemSmall) { BlockSmallView(s: $0) } } }
struct PaceMedium: Widget { var body: some WidgetConfiguration { homeWidget("pace.medium", .systemMedium) { PaceMediumView(s: $0) } } }
struct TargetMedium: Widget { var body: some WidgetConfiguration { homeWidget("target.medium", .systemMedium) { TargetMediumView(s: $0) } } }
struct MovesMedium: Widget { var body: some WidgetConfiguration { homeWidget("moves.medium", .systemMedium) { MovesMediumView(s: $0) } } }
struct FutureLarge: Widget { var body: some WidgetConfiguration { homeWidget("future.large", .systemLarge) { FutureLargeView(s: $0) } } }

/// 기본 위젯 하나: 앱 색 바탕, 누르면 앱 첫 탭
func homeWidget<V: View>(_ kind: String, _ family: WidgetFamily, @ViewBuilder view: @escaping (Snapshot) -> V) -> some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: Provider()) { e in
        Gate(kind: kind, preview: e.preview) { view(e.snap).kind(kind).environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) }
    }
    .catalog(kind).supportedFamilies([family])
}

/// 잠금화면 위젯 하나: 바탕 없음, 누르면 앱 첫 탭
func lockWidget<V: View>(_ kind: String, _ family: WidgetFamily, @ViewBuilder view: @escaping (Snapshot) -> V) -> some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: Provider()) { e in
        Gate(kind: kind, preview: e.preview) { view(e.snap).kind(kind).containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL) }
    }
    .catalog(kind).supportedFamilies([family])
}
