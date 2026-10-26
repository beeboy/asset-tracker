import SwiftUI

/// 워치 앱: 아이폰이 보낸 위젯 값을 받아 둔다. 화면은 임시로 금액·오늘 등락·한마디만
@main
struct NaeiloWatchApp: App {
    init() { WatchSync.shared.start() }
    var body: some Scene { WindowGroup { WatchHome() } }
}

struct WatchHome: View {
    @State private var w = WWatch.read()
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let w {
                Text(w.moneyText).font(.system(size: 24, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
                Text(w.dayText + " 오늘").font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(w.dayChg >= 0 ? Color(red: 1, green: 0.42, blue: 0.42) : Color(red: 0.42, green: 0.61, blue: 1))
                Text(w.line).font(.system(size: 13)).foregroundStyle(.secondary)
            } else {
                Text("아이폰에서 naeilo를 열면 값이 들어와요.").font(.system(size: 14))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .onReceive(NotificationCenter.default.publisher(for: WatchSync.got)) { _ in w = WWatch.read() }
    }
}
