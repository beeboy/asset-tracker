import WidgetKit
import SwiftUI

// MARK: 위젯 정의
struct AssetSmall: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "asset.small", provider: Provider()) { e in Gate(kind: "asset.small", preview: e.preview) { AssetSmallView(s: e.snap).kind("asset.small").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.asset.0).description(Catalog.asset.1).supportedFamilies([.systemSmall])
    }
}
struct FutureSmall: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "future.small", provider: Provider()) { e in Gate(kind: "future.small", preview: e.preview) { FutureSmallView(s: e.snap).kind("future.small").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.future.0).description(Catalog.future.1).supportedFamilies([.systemSmall])
    }
}
struct BlockSmall: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "block.small", provider: Provider()) { e in Gate(kind: "block.small", preview: e.preview) { BlockSmallView(s: e.snap).kind("block.small").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.block.0).description(Catalog.block.1).supportedFamilies([.systemSmall])
    }
}
struct PaceMedium: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "pace.medium", provider: Provider()) { e in Gate(kind: "pace.medium", preview: e.preview) { PaceMediumView(s: e.snap).kind("pace.medium").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.pace.0).description(Catalog.pace.1).supportedFamilies([.systemMedium])
    }
}
struct TargetMedium: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "target.medium", provider: Provider()) { e in Gate(kind: "target.medium", preview: e.preview) { TargetMediumView(s: e.snap).kind("target.medium").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.target.0).description(Catalog.target.1).supportedFamilies([.systemMedium])
    }
}
struct MovesMedium: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "moves.medium", provider: Provider()) { e in Gate(kind: "moves.medium", preview: e.preview) { MovesMediumView(s: e.snap).kind("moves.medium").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.moves.0).description(Catalog.moves.1).supportedFamilies([.systemMedium])
    }
}
struct FutureLarge: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "future.large", provider: Provider()) { e in Gate(kind: "future.large", preview: e.preview) { FutureLargeView(s: e.snap).kind("future.large").environment(\.isSample, e.snap.placeholder).widgetBG().widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.futureL.0).description(Catalog.futureL.1).supportedFamilies([.systemLarge])
    }
}
