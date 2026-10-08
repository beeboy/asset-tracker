import SwiftUI
import WidgetKit

/// 색은 기본(다크) 화면에서만. 투명·틴트(리퀴드 글라스)에서는 시스템이 단색으로 칠하므로 ▲▼ 기호·굵기·명암으로 구분한다
enum Palette {
    static let up = Color(red: 1.0, green: 0.42, blue: 0.42)
    static let dn = Color(red: 0.42, green: 0.61, blue: 1.0)
    static let goal = Color(red: 0.75, green: 0.55, blue: 0.94)
    static let good = Color(red: 0.30, green: 0.76, blue: 0.54)
    static let acc = Color(red: 0.42, green: 0.61, blue: 1.0)
    static let bg = LinearGradient(colors: [Color(red: 0.125, green: 0.145, blue: 0.184), Color(red: 0.082, green: 0.094, blue: 0.122)], startPoint: .topLeading, endPoint: .bottomTrailing)
}

struct Tint {
    let mode: WidgetRenderingMode
    var full: Bool { mode == .fullColor }
    func c(_ color: Color, _ o: Double = 1) -> Color { full ? color.opacity(o) : Color.white.opacity(o) }
    func chg(_ x: Double) -> Color { full ? (x >= 0 ? Palette.up : Palette.dn) : .white }
}

extension View {
    func widgetBG() -> some View { containerBackground(for: .widget) { Palette.bg } }
}

struct Label2: View { // 작은 회색 제목
    let text: String
    var body: some View { Text(text).font(.system(size: 12, weight: .bold)).foregroundStyle(Color.white.opacity(0.62)).lineLimit(1) }
}

struct Foot: View { // 아래 줄: 갱신 시각 + 다시 받기 버튼
    let text: String
    var body: some View {
        HStack(spacing: 4) {
            Text(text).font(.system(size: 11, weight: .medium)).foregroundStyle(Color.white.opacity(0.55)).lineLimit(1)
            Spacer(minLength: 0)
            ReloadButton()
        }
    }
}

struct ReloadButton: View { // ↻ 다시 받기
    var body: some View {
        Button(intent: ReloadIntent()) {
            Image(systemName: "arrow.clockwise").font(.system(size: 11, weight: .bold))
                .frame(width: 22, height: 22).background(Circle().fill(Color.white.opacity(0.14)))
        }
        .buttonStyle(.plain)
    }
}

func shortTime(_ d: Date?) -> String { Fmt.time(d) }
