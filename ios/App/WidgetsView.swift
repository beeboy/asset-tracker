import SwiftUI

/// 위젯 탭: 11개를 작게 줄여 내 데이터로 보여준다
struct WidgetsView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        let s = m.login == nil ? Snapshot.sample : m.snap
        List {
            Section("홈 화면 · 작은") {
                Row(info: Catalog.asset) { WidgetPreview(family: .small) { AssetSmallView(s: s) } }
                Row(info: Catalog.future) { WidgetPreview(family: .small) { FutureSmallView(s: s) } }
                Row(info: Catalog.block) { WidgetPreview(family: .small) { BlockSmallView(s: s) } }
            }
            Section("홈 화면 · 중간") {
                Row(info: Catalog.pace) { WidgetPreview(family: .medium) { PaceMediumView(s: s) } }
                Row(info: Catalog.target) { WidgetPreview(family: .medium) { TargetMediumView(s: s) } }
                Row(info: Catalog.moves) { WidgetPreview(family: .medium) { MovesMediumView(s: s) } }
            }
            Section("홈 화면 · 큰") {
                Row(info: Catalog.futureL) { WidgetPreview(family: .large) { FutureLargeView(s: s) } }
            }
            Section {
                Row(info: Catalog.lAsset) { WidgetPreview(family: .rect) { LockAssetView(s: s) } }
                Row(info: Catalog.lGoal) { WidgetPreview(family: .circle) { LockGoalView(s: s) } }
                Row(info: Catalog.lFuture) { WidgetPreview(family: .rect) { LockFutureView(s: s) } }
                Row(info: Catalog.lTarget) { WidgetPreview(family: .rect) { LockTargetView(s: s) } }
            } header: { Text("잠금 화면") } footer: {
                Text("홈 화면이나 잠금 화면을 길게 눌러 + (잠금 화면은 사용자화 → 위젯) → naeilo 에서 추가합니다." + (s.placeholder ? " 연결 전이나 체험 중에는 예시 값으로 보입니다." : ""))
            }
        }
        .environment(\.isSample, s.placeholder)
    }
}

private struct Row<P: View>: View {
    let info: (String, String)
    @ViewBuilder let preview: () -> P
    var body: some View {
        HStack(spacing: 14) {
            preview()
            VStack(alignment: .leading, spacing: 3) {
                Text(info.0).font(.subheadline.weight(.bold))
                Text(info.1).font(.footnote).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }
}

/// 위젯 화면을 실제 크기로 그린 뒤 줄여서 보여준다 (누름은 막는다)
struct WidgetPreview<V: View>: View {
    enum Family { case small, medium, large, rect, circle }
    let family: Family
    var scale: CGFloat? = nil
    @ViewBuilder let content: () -> V

    private var size: CGSize {
        switch family {
        case .small: return CGSize(width: 170, height: 170)
        case .medium: return CGSize(width: 364, height: 170)
        case .large: return CGSize(width: 364, height: 382)
        case .rect: return CGSize(width: 172, height: 76)
        case .circle: return CGSize(width: 76, height: 76)
        }
    }
    private var lock: Bool { family == .rect || family == .circle }
    private var k: CGFloat { scale ?? (lock ? 0.8 : 0.5) }

    var body: some View {
        content()
            .padding(lock ? 6 : 16)
            .frame(width: size.width, height: size.height)
            .foregroundStyle(lock ? Color.white : Palette.ink)
            .environment(\.colorScheme, lock ? .dark : colorScheme)
            .background { if lock { Color(white: 0.22) } else { WidgetBG() } }
            .clipShape(RoundedRectangle(cornerRadius: lock ? (family == .circle ? 38 : 16) : 22, style: .continuous))
            .allowsHitTesting(false)
            .scaleEffect(k, anchor: .topLeading)
            .frame(width: size.width * k, height: size.height * k, alignment: .topLeading)
            .shadow(color: .black.opacity(0.12), radius: 3, y: 1)
    }
    @Environment(\.colorScheme) private var colorScheme
}
