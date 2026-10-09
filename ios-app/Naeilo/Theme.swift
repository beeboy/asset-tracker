import SwiftUI

// 시안(목업 49판)의 색. 오르면 빨강, 내리면 파랑 (국내 관례).
extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: opacity)
    }
}

enum Theme {
    static let bg = Color(hex: 0xF2F4F6)
    static let ink = Color(hex: 0x15202B)
    static let teal = Color(hex: 0x0B6B66)
    static let tealDark = Color(hex: 0x0B4F4B)
    static let mint = Color(hex: 0x5FD0C4)
    static let mintBg = Color(hex: 0xF3FAF9)
    static let tealBg = Color(hex: 0xE3F1EF)
    static let sub = Color(hex: 0x4A5560)
    static let sub2 = Color(hex: 0x5B6670)
    static let muted = Color(hex: 0x8A949E)
    static let border = Color(hex: 0xDCE1E6)
    static let line = Color(hex: 0xEEF1F3)
    static let track = Color(hex: 0xE6EAEE)
    static let dash = Color(hex: 0xC9D0D6)
    static let yellow = Color(hex: 0xF2C14E)
    static let gold = Color(hex: 0xF3CF6A)
    static let cream = Color(hex: 0xFFF7E0)
    static let slate = Color(hex: 0x3A4651)
    static let shelter = Color(hex: 0x1E2436)
    static let up = Color(hex: 0xC0392B)
    static let down = Color(hex: 0x1B5E96)
    static let orange = Color(hex: 0xE8862A)
    static let purple = Color(hex: 0x7A4FC8)
    static let blue = Color(hex: 0x2F8FD8)
    static let green = Color(hex: 0x12A06E)
    static let holdColors: [Color] = [Color(hex: 0x7A4FC8), Color(hex: 0x2F8FD8), Color(hex: 0x12A06E),
                                      Color(hex: 0xE8862A), Color(hex: 0xC2477A), Color(hex: 0x5B6670)]

    static func change(_ r: Double) -> Color { r >= 0 ? up : down }
}

// 흰 카드 (시안의 border 1px · radius 18)
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    var bg: Color = .white
    var stroke: Color? = Theme.border
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background(bg, in: RoundedRectangle(cornerRadius: 18))
            .overlay {
                if let stroke { RoundedRectangle(cornerRadius: 18).stroke(stroke, lineWidth: 1) }
            }
    }
}

struct DashedCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.dash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
            .foregroundStyle(Theme.sub)
    }
}

// 칩 한 줄: 누르면 같은 자리의 그래프·숫자가 바뀐다
struct ChipRow<T: Hashable>: View {
    let items: [(T, String)]
    @Binding var selection: T
    var accent: Color = Theme.teal
    var fill = false
    var body: some View {
        let row = HStack(spacing: 6) {
            ForEach(items, id: \.0) { item in
                let on = item.0 == selection
                Button { selection = item.0 } label: {
                    Text(item.1)
                        .font(.system(size: 13, weight: .bold))
                        .lineLimit(1)
                        .padding(.horizontal, fill ? 2 : 14)
                        .frame(maxWidth: fill ? .infinity : nil, minHeight: 40)
                        .foregroundStyle(on ? .white : Theme.ink)
                        .background(on ? accent : .white, in: Capsule())
                        .overlay(Capsule().stroke(on ? accent : Theme.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        if fill { row } else { ScrollView(.horizontal, showsIndicators: false) { row.padding(.vertical, 1) } }
    }
}

struct PrimaryButton: View {
    let title: String
    var color: Color = Theme.teal
    var fg: Color = .white
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.system(size: 16, weight: .bold))
                .frame(maxWidth: .infinity, minHeight: 50)
                .foregroundStyle(fg)
                .background(color, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

// 도트 그림은 보간 없이 키운다
struct Pixel: View {
    let name: String
    var width: CGFloat
    var height: CGFloat
    var body: some View {
        Image(name).interpolation(.none).resizable().frame(width: width, height: height)
    }
}

struct ProgressBar: View {
    let value: Double
    var height: CGFloat = 10
    var fill: Color = Theme.yellow
    var track: Color = Theme.slate
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(fill).frame(width: max(0, min(1, value)) * g.size.width)
            }
        }
        .frame(height: height)
    }
}

struct ScreenTitle: View {
    let kicker: String?
    let title: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let kicker { Text(kicker).font(.system(size: 14, weight: .semibold)).foregroundStyle(Theme.teal) }
            Text(title).font(.system(size: 24, weight: .bold)).tracking(-0.4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension View {
    // 탭 화면 공통 바탕
    func screen() -> some View {
        self.padding(.horizontal, 16).padding(.bottom, 24)
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(Theme.ink)
    }
}
