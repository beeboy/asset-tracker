import WidgetKit
import SwiftUI

struct LockAsset: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.asset", provider: Provider()) { e in Gate(kind: "lock.asset", preview: e.preview) { LockAssetView(s: e.snap).kind("lock.asset").containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.lAsset.0).description(Catalog.lAsset.1).supportedFamilies([.accessoryRectangular])
    }
}
struct LockGoal: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.goal", provider: Provider()) { e in Gate(kind: "lock.goal", preview: e.preview) { LockGoalView(s: e.snap).kind("lock.goal").containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.lGoal.0).description(Catalog.lGoal.1).supportedFamilies([.accessoryCircular])
    }
}
struct LockFuture: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.future", provider: Provider()) { e in Gate(kind: "lock.future", preview: e.preview) { LockFutureView(s: e.snap).kind("lock.future").containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.lFuture.0).description(Catalog.lFuture.1).supportedFamilies([.accessoryRectangular])
    }
}
struct LockTarget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.target", provider: Provider()) { e in Gate(kind: "lock.target", preview: e.preview) { LockTargetView(s: e.snap).kind("lock.target").containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL) } }
            .configurationDisplayName(Catalog.lTarget.0).description(Catalog.lTarget.1).supportedFamilies([.accessoryRectangular])
    }
}

@main
struct NaeiloWidgets: WidgetBundle {
    var body: some Widget {
        AssetSmall()
        FutureSmall()
        BlockSmall()
        PaceMedium()
        TargetMedium()
        MovesMedium()
        FutureLarge()
        LockWidgets().body
    }
}

struct LockWidgets: WidgetBundle {
    var body: some Widget {
        LockAsset()
        LockGoal()
        LockFuture()
        LockTarget()
    }
}
