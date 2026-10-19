import WidgetKit
import SwiftUI

// MARK: 위젯 정의 (이름표는 Catalog, 잠금은 Gate, 값은 Provider. 새 위젯은 Catalog 에 한 줄, 여기 한 줄, NaeiloWidgets 묶음에 한 줄)
struct AssetSmall: Widget { var body: some WidgetConfiguration { homeWidget("asset.small", .systemSmall) { AssetSmallView(s: $0) } } }
struct PathSmall: Widget { var body: some WidgetConfiguration { homeWidget("path.small", .systemSmall) { PathSmallView(s: $0) } } }
struct FutureSmall: Widget { var body: some WidgetConfiguration { homeWidget("future.small", .systemSmall) { FutureSmallView(s: $0) } } }
struct BlockSmall: Widget { var body: some WidgetConfiguration { homeWidget("block.small", .systemSmall) { BlockSmallView(s: $0) } } }
struct PaceSmall: Widget { var body: some WidgetConfiguration { homeWidget("pace.small", .systemSmall) { PaceSmallView(s: $0) } } }
struct TargetSmall: Widget { var body: some WidgetConfiguration { homeWidget("target.small", .systemSmall) { TargetSmallView(s: $0) } } }
struct MovesSmall: Widget { var body: some WidgetConfiguration { homeWidget("moves.small", .systemSmall) { MovesSmallView(s: $0) } } }

/// 기본 위젯 하나: 앱 색 바탕, 누르면 앱 첫 탭
func homeWidget<V: View>(_ kind: String, _ family: WidgetFamily, @ViewBuilder view: @escaping (Snapshot) -> V) -> some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: Provider()) { e in
        Gate(kind: kind, preview: e.preview) { view(e.snap).kind(kind).environment(\.isSample, e.snap.placeholder).environment(\.entryDate, e.date).speak(Speech.of(kind, e.snap), kind: kind, money: kind == "asset.small" || kind == "future.small").widgetBG().widgetURL(Catalog.openURL) }
    }
    .catalog(kind).supportedFamilies([family])
}

/// 잠금화면 위젯 하나: 바탕 없음, 누르면 앱 첫 탭
func lockWidget<V: View>(_ kind: String, _ family: WidgetFamily, @ViewBuilder view: @escaping (Snapshot) -> V) -> some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: Provider()) { e in
        Gate(kind: kind, preview: e.preview) { LockSpeak(kind: kind, s: e.snap) { view(e.snap).kind(kind).environment(\.entryDate, e.date) }.containerBackground(for: .widget) { Color.clear }.widgetURL(Catalog.openURL) }
    }
    .catalog(kind).supportedFamilies([family])
}

/// 잠금화면 위젯의 VoiceOver 문장: 폰이 잠겨 금액이 가려졌으면 금액도 '숨김'으로 읽는다. ↻(직사각형만)·금액 숨기기는 동작으로
struct LockSpeak<Content: View>: View {
    let kind: String
    let s: Snapshot
    @ViewBuilder var content: Content
    @Environment(\.redactionReasons) private var reasons
    var body: some View {
        let hide = Store.lockPrivate && reasons.contains(.privacy)
        content.speak(Speech.of(kind, s, hide: hide), kind: kind, money: kind != "lock.goal", reload: kind != "lock.goal")
    }
}

/// VoiceOver 가 위젯마다 읽는 한 문장 (숫자 조각을 따로 읽지 않게). 금액을 숨겼으면 '숨김'으로 읽는다
enum Speech {
    static func of(_ kind: String, _ s: Snapshot, hide forced: Bool = false) -> String {
        let hide = forced || Store.isHidden(kind), name = Catalog.name(kind)
        let money: (Double) -> String = { hide ? WL.t("숨김") : Fmt.eok($0) }
        let pct: (Double?) -> String = { $0.map { "\(Int(($0 * 100).rounded()))%" } ?? "-" }
        let mag: (Double) -> String = { String(format: "%.1f%%", abs($0) * 100) }   // 부호 없이, 방향은 상승·하락으로
        let chg = ["p": mag(s.dayChg), "dir": WL.dir(s.dayChg)]
        var line: String
        switch kind {
        case "asset.small", "lock.asset":
            line = WL.t("총자산 {v}, 오늘 {p} {dir}", ["v": money(s.total)].merging(chg) { a, _ in a })
        case "future.small":
            line = s.term == nil ? WL.t("앱을 열면 계산합니다")
                : WL.t("{term} 예상 {v}, 지금보다 {p}", ["term": s.termLabel, "v": s.term.map { money($0.p50) } ?? "-", "p": s.term.map { Fmt.pct(s.total > 0 ? $0.p50 / s.total - 1 : 0, 0) } ?? "-"])
        case "lock.future":
            line = s.term == nil && s.fan == nil ? WL.t("앱을 열면 계산합니다")
                : WL.t("{term} 예상 {v}, 목표 확률 {p}", ["term": s.termLabel, "v": s.term.map { money($0.p50) } ?? "-", "p": pct(s.pGoal)])
        case "path.small":
            if let d = WSmall.load() ?? (s.placeholder ? .sample : nil) {
                let g = d.gapNow(s.total)
                line = WL.t(g >= 0 ? "계획선보다 {p} 앞섬, 달성 확률 {q}" : "계획선보다 {p} 뒤처짐, 달성 확률 {q}", ["p": mag(g), "q": pct(s.pGoal)])
            } else { line = WL.t("앱을 열면 보여요") }
        case "block.small":
            let d = s.cells - s.cellsPrev
            line = WL.t("{b}번째 블록 100칸 중 {n}칸, 어제보다 {d}칸", ["b": "\(min(s.cells / 100 + 1, 10))", "n": "\(s.cells % 100)", "d": (d >= 0 ? "+" : "") + "\(d)"])
        case "pace.small":
            if let d = WSmall.load() ?? (s.placeholder ? .sample : nil), let me = d.me, let bk = d.basket {
                line = WL.t(me >= bk ? "내가 {p} 빨라요" : "내가 {p} 느려요", ["p": mag(me - bk)]) + ". " + WL.t("나 {a}, 모의 바구니 {b}", ["a": Fmt.pct(me), "b": Fmt.pct(bk)])
            } else { line = WL.t("앱에서 모의 바구니를 담으면 보여요") }
        case "target.small", "lock.target":
            if let w = s.week {
                line = WL.t("지금 {v}, 예측 {m}, 예측보다 {p}, {hit}", ["v": money(w.act), "m": money(w.p50), "p": Fmt.pct(w.act / w.p50 - 1), "hit": s.hitText])
            } else { line = WL.t("이번 주 예측 대기") }
        case "moves.small":
            line = WL.t("총자산 {v}, 오늘 {p} {dir}", ["v": money(s.total)].merging(chg) { a, _ in a })
            let ms = s.moves.prefix(4).map { "\($0.t) \(mag($0.pct)) \(WL.dir($0.pct))" }
            if !ms.isEmpty { line += ". " + ms.joined(separator: ", ") }
        case "lock.goal":
            line = WL.t("목표 {p} 진행", ["p": pct(s.progress)])
        default:
            line = ""
        }
        if s.placeholder { line += ". " + WL.t("예시 값") }
        return line.isEmpty ? name : "\(name). \(line)"
    }
}
