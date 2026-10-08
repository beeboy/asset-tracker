import WidgetKit
import SwiftUI

// MARK: 위젯 정의
struct AssetSmall: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "asset.small", provider: Provider()) { e in AssetSmallView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.asset.0).description(Catalog.asset.1).supportedFamilies([.systemSmall])
    }
}
struct FutureSmall: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "future.small", provider: Provider()) { e in FutureSmallView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.future.0).description(Catalog.future.1).supportedFamilies([.systemSmall])
    }
}
struct BlockSmall: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "block.small", provider: Provider()) { e in BlockSmallView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.block.0).description(Catalog.block.1).supportedFamilies([.systemSmall])
    }
}
struct PaceMedium: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "pace.medium", provider: Provider()) { e in PaceMediumView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.pace.0).description(Catalog.pace.1).supportedFamilies([.systemMedium])
    }
}
struct TargetMedium: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "target.medium", provider: Provider()) { e in TargetMediumView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.target.0).description(Catalog.target.1).supportedFamilies([.systemMedium])
    }
}
struct MovesMedium: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "moves.medium", provider: Provider()) { e in MovesMediumView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.moves.0).description(Catalog.moves.1).supportedFamilies([.systemMedium])
    }
}
struct FutureLarge: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "future.large", provider: Provider()) { e in FutureLargeView(s: e.snap).widgetBG().widgetURL(Catalog.openURL) }
            .configurationDisplayName(Catalog.futureL.0).description(Catalog.futureL.1).supportedFamilies([.systemLarge])
    }
}
