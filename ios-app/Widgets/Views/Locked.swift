import SwiftUI
import WidgetKit

// 잠긴 위젯의 기본 모습 (잠글지는 Catalog.swift 의 Gate 가 정한다)
extension Gate where Locked == LockedWidget {
    init(kind: String, preview: Bool, @ViewBuilder content: () -> Content) {
        self.init(kind: kind, preview: preview, content: content) { LockedWidget(kind: kind) }
    }
}

struct LockedWidget: View {
    let kind: String
    @Environment(\.widgetFamily) private var family

    var body: some View { face.widgetBG() }

    @ViewBuilder private var face: some View {
        let title = Catalog.name(kind)
        switch family {
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Label(title, systemImage: "lock.fill").font(.system(size: 12, weight: .bold))
                Text(WidgetUnlock.how(kind)).font(.system(size: 11)).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .accessoryCircular, .accessoryInline:
            Image(systemName: "lock.fill").font(.system(size: 16, weight: .bold))
        default:
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: "lock.fill").font(.system(size: 12, weight: .bold))
                    Text(title).font(.system(size: 13, weight: .bold)).lineLimit(1)
                }
                Spacer(minLength: 0)
                // 흐린 칸: 열리면 이 자리에 숫자가 온다
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(0..<(family == .systemSmall ? 6 : 12), id: \.self) { i in
                        RoundedRectangle(cornerRadius: 3).fill(Palette.ink.opacity(0.12))
                            .frame(height: CGFloat(10 + (i * 7) % 26))
                    }
                }
                Spacer(minLength: 0)
                Text(WidgetUnlock.how(kind)).font(.system(size: 11, weight: .semibold)).foregroundStyle(Palette.ink.opacity(0.7))
                    .lineLimit(3).fixedSize(horizontal: false, vertical: true)
                Text(WidgetUnlock.cta).font(.system(size: 10, weight: .bold)).foregroundStyle(Palette.ink.opacity(0.5))
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}
