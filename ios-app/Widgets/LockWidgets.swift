import WidgetKit
import SwiftUI

struct LockAsset: Widget { var body: some WidgetConfiguration { lockWidget("lock.asset", .accessoryRectangular) { LockAssetView(s: $0) } } }
struct LockGoal: Widget { var body: some WidgetConfiguration { lockWidget("lock.goal", .accessoryCircular) { LockGoalView(s: $0) } } }
struct LockHill: Widget { var body: some WidgetConfiguration { charLockWidget("lock.hill") { LockHillView(e: $0) } } }
struct LockSay: Widget { var body: some WidgetConfiguration { charLockWidget("lock.say") { LockSayView(e: $0) } } }
struct LockLadder: Widget { var body: some WidgetConfiguration { charLockWidget("lock.ladder") { LockLadderView(e: $0) } } }

/// 인물 잠금화면 위젯: 인물 위젯과 같은 값·인물 고르기(CharProvider, 기본은 앱 홈의 인물), 바탕 없음, 누르면 앱 첫 탭
private func charLockWidget<V: View>(_ kind: String, @ViewBuilder _ v: @escaping (CharEntry) -> V) -> some WidgetConfiguration {
    AppIntentConfiguration(kind: kind, intent: WhoIntent.self, provider: CharProvider()) { e in
        Gate(kind: kind, preview: e.preview) {
            LockCharSpeak(kind: kind, e: e) { v(e).kind(kind) }.containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL)
        }
    }
    .catalog(kind).supportedFamilies([.accessoryRectangular])
}

/// 인물 잠금화면 위젯의 VoiceOver 한 문장. 폰이 잠겨 금액이 가려졌거나 숨겼으면 금액은 '숨김'
struct LockCharSpeak<Content: View>: View {
    let kind: String
    let e: CharEntry
    @ViewBuilder var content: Content
    @Environment(\.redactionReasons) private var reasons
    var body: some View {
        let hide = (Store.lockPrivate && reasons.contains(.privacy)) || Store.isHidden(kind), r = e.r
        let money = { (v: Double) in hide ? WL.t("숨김") : Fmt.eok(v) }
        let day = WL.t("총자산 {v}, 오늘 {p} {dir}", ["v": money(r.total), "p": String(format: "%.1f%%", abs(r.dayChg) * 100), "dir": WL.dir(r.dayChg)])
        let line: String
        switch kind {
        case "lock.say": line = e.c.line(r) + ". " + day
        case "lock.ladder": line = WL.t("{key}까지 {p}%, 남은 금액 {v}", ["key": WL.t(r.keyName), "p": "\(Int((r.pct * 100).rounded()))", "v": money(r.remain)]) + ". " + day
        default: line = day
        }
        return content.speak("\(Catalog.name(kind)). \(e.c.title), " + line, kind: kind, money: true, reload: false)
    }
}

@main
struct NaeiloWidgets: WidgetBundle {
    var body: some Widget {
        CharHill()
        CharLadder()
        CharTower()
        CharBoard()
        CharHeight()
        CharPuzzle()
        CharGarden()
        CharTug()
        AssetSmall()
        PathSmall()
        FutureSmall()
        BlockSmall()
        PaceSmall()
        TargetSmall()
        MovesSmall()
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
        LockHill()
        LockSay()
        LockLadder()
    }
}
