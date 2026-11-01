import SwiftUI
import WidgetKit
import GyeolKit

/// 결 전용 위젯 확장: Live Activity 하나뿐이다 (화면은 GyeolKit)
@main
struct GyeolWidgets: WidgetBundle {
    var body: some Widget { GyeolLiveActivity() }
}
