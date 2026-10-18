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
    /// 내 길: 실제 선·앞섬 (앱 내 길 화면의 민트·청록), 계획선 보라, 뒤처짐·모의 바구니 주황
    static let teal = dyn(0x5fd0c4, 0x2a9d93)
    static let plan = dyn(0xb79cf0, 0x8e6bd8)
    static let orange = dyn(0xe9a564, 0xd9873a)
    /// 목표일 전망 띠·중앙선 (분석 화면 목표일 전망과 같은 청록)
    static let band = dyn(0x2a9d93, 0x2a9d93)
    static let bandLine = dyn(0x8fd8ce, 0x1f7f76)
    /// 오늘의 움직임 히트맵: 바탕 칸 위에 등락색을 진하기만큼 덮는다
    static let heatBase = dyn(0x38425a, 0xdfe3ea)
    static let heatUp = dyn(0xe5484d, 0xe5484d)
    static let heatDn = dyn(0x3d63c9, 0x3d63c9)
    /// 밝은·다크 화면에서 같은 색 (과녁판 고리)
    static func fixed(_ x: UInt32) -> Color { dyn(x, x) }

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

struct Foot: View { // 아래 줄: 앞말 + '3분 전 · 토스' + 다시 받기 버튼
    var text = ""
    let s: Snapshot
    @Environment(\.entryDate) private var now
    var body: some View {
        let fresh = Fresh.label(s.pricedAt, s.feed, now: now)
        HStack(spacing: 4) {
            Text(text.isEmpty ? fresh : text + " · " + fresh).font(.system(size: 11, weight: .medium)).foregroundStyle(Palette.ink.opacity(0.7)).lineLimit(1)
            Spacer(minLength: 0)
            ReloadButton()
        }
    }
}

struct Stamp: View { // 갱신 시간 + ↻ (자리가 좁은 위젯의 머리·꼬리). 투명 바탕에서도 읽히게 글자를 너무 옅게 하지 않는다
    let s: Snapshot
    var button = true
    var time = true
    @Environment(\.entryDate) private var now
    var body: some View {
        HStack(spacing: 4) {
            if time { Text(Store.fetchFailed ? WL.t("받기 실패") : Fresh.ago(s.pricedAt, now: now)).font(.system(size: 10, weight: .medium)).foregroundStyle(Palette.ink.opacity(0.7)).lineLimit(1).fixedSize() }
            if button { ReloadButton() }
        }
    }
}

struct ReloadButton: View { // ↻ 다시 받기 (예시 값이면 앞에 '예시' 배지)
    @Environment(\.isSample) private var isSample
    var body: some View {
        HStack(spacing: 4) {
            if isSample { SampleBadge() }
            button
        }
    }

    @ViewBuilder private var button: some View {
        let busy = Store.isReloading
        let icon = Image(systemName: busy ? "ellipsis" : "arrow.clockwise").font(.system(size: 11, weight: .bold))
            .frame(width: 22, height: 22).background(Circle().fill(Palette.ink.opacity(busy ? 0.3 : 0.14)))
            .accessibilityLabel(WL.t(busy ? "받는 중" : "다시 받기"))
        #if WIDGET_EXT
        Button(intent: ReloadIntent()) { icon }.buttonStyle(.plain)
        #else
        icon // 앱의 미리보기에서는 그림만 (버튼 동작은 위젯에만 둔다)
        #endif
    }
}

struct SampleBadge: View { // 체험 모드·연결 전: 실제 값이 아님을 표시
    var body: some View {
        Text(WL.t("예시")).font(.system(size: 10, weight: .heavy)).foregroundStyle(.white)
            .padding(.horizontal, 6).frame(height: 16).background(Capsule().fill(Color.orange))
    }
}


// MARK: 금액 숨기기
private struct KindKey: EnvironmentKey { static let defaultValue = "" }
extension EnvironmentValues {
    /// 이 화면을 그리는 위젯 종류 (금액 숨기기를 위젯마다 따로 할 때 쓴다)
    var widgetKind: String { get { self[KindKey.self] } set { self[KindKey.self] = newValue } }
    /// 예시 값으로 그리는 중 (체험 모드이거나 아직 연결 전)
    var isSample: Bool { get { self[SampleKey.self] } set { self[SampleKey.self] = newValue } }
    /// 이 그림의 시각 (타임라인 항목 시각). '3분 전'을 여기서부터 센다
    var entryDate: Date { get { self[EntryDateKey.self] } set { self[EntryDateKey.self] = newValue } }
}
private struct EntryDateKey: EnvironmentKey { static var defaultValue: Date { Date() } }
private struct SampleKey: EnvironmentKey { static let defaultValue = false }

/// 숨긴 금액 자리
let hiddenAmount = "••••"

/// 큰 금액: 누르면 숨기고 한 번 더 누르면 보인다
struct Money<L: View>: View {
    @Environment(\.widgetKind) private var kind
    @ViewBuilder let label: (Bool) -> L
    var body: some View {
        // 잠금화면 위젯의 금액은 폰이 잠겨 있으면 시스템이 가린다 (설정에서 끌 수 있다)
        let lockPrivate = kind.hasPrefix("lock.") && Store.lockPrivate
        #if WIDGET_EXT
        Button(intent: ToggleAmountIntent(kind: kind)) { label(Store.isHidden(kind)).privacySensitive(lockPrivate) }.buttonStyle(.plain)
        #else
        label(Store.isHidden(kind)).privacySensitive(lockPrivate)
        #endif
    }
}

extension View {
    func kind(_ k: String) -> some View { environment(\.widgetKind, k) }
}
