import SwiftUI
import WidgetKit

/// 기본(밝은)·다크 화면은 색으로, 투명·틴트(리퀴드 글라스)에서는 시스템이 단색으로 칠하므로 ▲▼ 기호·굵기·명암으로 구분한다
enum Palette {
    static let up = dyn(0xff6b6b, 0xd63939)
    static let dn = dyn(0x6c9cff, 0x2f6fed)
    static let goal = dyn(0xc08cf0, 0x8e44ad)
    static let good = dyn(0x4cc38a, 0x1f9d55)
    static let acc = dyn(0x6c9cff, 0x2f6fed)
    /// 글자·선 기본색: 다크에선 흰색, 밝은 화면에선 짙은 남색
    static let ink = dyn(0xffffff, 0x1b2230)
    static let bgTop = dyn(0x20252f, 0xffffff)
    static let bgBottom = dyn(0x15181f, 0xeef1f6)

    static func dyn(_ dark: UInt32, _ light: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? rgb(dark) : rgb(light) })
    }
    private static func rgb(_ x: UInt32) -> UIColor {
        UIColor(red: CGFloat((x >> 16) & 0xff) / 255, green: CGFloat((x >> 8) & 0xff) / 255, blue: CGFloat(x & 0xff) / 255, alpha: 1)
    }
}

/// 위젯 바탕 (밝은·다크 화면 따라감)
struct WidgetBG: View {
    var body: some View { LinearGradient(colors: [Palette.bgTop, Palette.bgBottom], startPoint: .topLeading, endPoint: .bottomTrailing) }
}

/// 투명·틴트에서는 시스템이 단색으로 칠하므로 글자를 늘 밝게(다크 기준) 둔다
private struct SchemeFix: ViewModifier {
    @Environment(\.widgetRenderingMode) var mode
    @ViewBuilder func body(content: Content) -> some View {
        if mode == .fullColor { content } else { content.environment(\.colorScheme, .dark) }
    }
}

struct Tint {
    let mode: WidgetRenderingMode
    var full: Bool { mode == .fullColor }
    func c(_ color: Color, _ o: Double = 1) -> Color { full ? color.opacity(o) : Palette.ink.opacity(o) }
    func chg(_ x: Double) -> Color { full ? (x >= 0 ? Palette.up : Palette.dn) : Palette.ink }
}

extension View {
    func widgetBG() -> some View { modifier(SchemeFix()).containerBackground(for: .widget) { WidgetBG() } }
}

struct Label2: View { // 작은 회색 제목
    let text: String
    var body: some View { Text(text).font(.system(size: 12, weight: .bold)).foregroundStyle(Palette.ink.opacity(0.62)).lineLimit(1) }
}

struct Foot: View { // 아래 줄: 갱신 시각 + 다시 받기 버튼
    let text: String
    var body: some View {
        HStack(spacing: 4) {
            Text(text).font(.system(size: 11, weight: .medium)).foregroundStyle(Palette.ink.opacity(0.55)).lineLimit(1)
            Spacer(minLength: 0)
            ReloadButton()
        }
    }
}

struct ReloadButton: View { // ↻ 다시 받기
    var body: some View {
        let icon = Image(systemName: "arrow.clockwise").font(.system(size: 11, weight: .bold))
            .frame(width: 22, height: 22).background(Circle().fill(Palette.ink.opacity(0.14)))
        #if WIDGET_EXT
        Button(intent: ReloadIntent()) { icon }.buttonStyle(.plain)
        #else
        icon // 앱의 미리보기에서는 그림만 (버튼 동작은 위젯에만 둔다)
        #endif
    }
}

func shortTime(_ d: Date?) -> String { Fmt.time(d) }

// MARK: 금액 숨기기
private struct KindKey: EnvironmentKey { static let defaultValue = "" }
extension EnvironmentValues {
    /// 이 화면을 그리는 위젯 종류 (금액 숨기기를 위젯마다 따로 할 때 쓴다)
    var widgetKind: String { get { self[KindKey.self] } set { self[KindKey.self] = newValue } }
}

/// 숨긴 금액 자리
let hiddenAmount = "••••"

/// 큰 금액: 누르면 숨기고 한 번 더 누르면 보인다
struct Money<L: View>: View {
    @Environment(\.widgetKind) private var kind
    @ViewBuilder let label: (Bool) -> L
    var body: some View {
        #if WIDGET_EXT
        Button(intent: ToggleAmountIntent(kind: kind)) { label(Store.isHidden(kind)) }.buttonStyle(.plain)
        #else
        label(Store.isHidden(kind))
        #endif
    }
}

extension View {
    func kind(_ k: String) -> some View { environment(\.widgetKind, k) }
}
