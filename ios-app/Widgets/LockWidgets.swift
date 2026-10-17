import WidgetKit
import SwiftUI

struct LockAsset: Widget { var body: some WidgetConfiguration { lockWidget("lock.asset", .accessoryRectangular) { LockAssetView(s: $0) } } }
struct LockGoal: Widget { var body: some WidgetConfiguration { lockWidget("lock.goal", .accessoryCircular) { LockGoalView(s: $0) } } }
struct LockFuture: Widget { var body: some WidgetConfiguration { lockWidget("lock.future", .accessoryRectangular) { LockFutureView(s: $0) } } }
struct LockTarget: Widget { var body: some WidgetConfiguration { lockWidget("lock.target", .accessoryRectangular) { LockTargetView(s: $0) } } }

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
        CharTrend()
        CharRecover()
        CharBlock()
        CharMoves()
        StoryWidget()
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
