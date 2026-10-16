import SwiftUI
import WidgetKit

// 7일 무료가 끝나고 구독하지 않은 위젯: 홈 화면에 놓을 수는 있지만 숫자 대신 잠긴 모습. 누르면 앱의 구독 화면으로 간다.
// 구독하면 앱이 구독 상태를 다시 쓰고 위젯을 새로 그리게 해서, 같은 자리에서 바로 열린다.
struct Gate<Content: View>: View {
    let kind: String
    let preview: Bool
    @ViewBuilder var content: Content
    var body: some View {
        if preview || Store.isUnlocked(kind) { content }
        else { LockedWidget(kind: kind).widgetBG().widgetURL(URL(string: "naeilo://subscribe")!) }
    }
}

struct LockedWidget: View {
    let kind: String
    @Environment(\.widgetFamily) private var family

    private var title: String {
        let c: [String: (String, String)] = ["asset.small": Catalog.asset, "future.small": Catalog.future, "block.small": Catalog.block,
                                            "pace.medium": Catalog.pace, "target.medium": Catalog.target, "moves.medium": Catalog.moves,
                                            "future.large": Catalog.futureL, "lock.asset": Catalog.lAsset, "lock.goal": Catalog.lGoal,
                                            "lock.future": Catalog.lFuture, "lock.target": Catalog.lTarget]
        let more = ["char.trend": "인물 · 자산 추이", "char.recover": "인물 · 본전 진행", "char.block": "인물 · 블록",
                    "char.moves": "인물 · 오늘의 움직임", "story": "서재 · 이어 읽기"]
        return c[kind]?.0 ?? more[kind] ?? "naeilo 위젯"
    }

    var body: some View {
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
                Text("눌러서 구독하기 ›").font(.system(size: 10, weight: .bold)).foregroundStyle(Palette.ink.opacity(0.5))
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}
