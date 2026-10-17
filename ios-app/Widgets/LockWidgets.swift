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
        CharHeight()
        CharPuzzle()
        CharGarden()
        CharTug()
        StoryWidget()
        LockWidgets().body
        if #available(iOS 18.0, *) { ReloadControl() }
    }
}

/// 제어 센터 ↻ (iOS 18 부터): 누르면 위젯의 ↻ 와 같이 지금 가격을 받고 모든 위젯을 다시 그린다
@available(iOS 18.0, *)
struct ReloadControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "control.reload") {
            ControlWidgetButton(action: ReloadIntent()) {
                Label(WL.t("naeilo 다시 받기"), systemImage: "arrow.clockwise")
            }
        }
        .displayName(LocalizedStringResource(stringLiteral: WL.t("naeilo 다시 받기")))
        .description(LocalizedStringResource(stringLiteral: WL.t("위젯의 지금 가격을 바로 받아 다시 그려요")))
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
