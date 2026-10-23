import SwiftUI

// 시안(목업 49판)의 색. 오르면 빨강, 내리면 파랑 (국내 관례).
// 다크 모드: 같은 이름의 색을 어두운 화면용 값으로 바꿔 쓴다 (아이폰 설정 또는 앱 설정의 화면 모드).
extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: opacity)
    }
    init(hex: UInt32, dark: UInt32) {
        func ui(_ h: UInt32) -> UIColor {
            UIColor(red: CGFloat((h >> 16) & 0xFF) / 255, green: CGFloat((h >> 8) & 0xFF) / 255, blue: CGFloat(h & 0xFF) / 255, alpha: 1)
        }
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? ui(dark) : ui(hex) })
    }
}

enum Theme {
    static let bg = Color(hex: 0xF2F4F6, dark: 0x0E1318)
    static let card = Color(hex: 0xFFFFFF, dark: 0x1A222B)          // 흰 카드
    static let ink = Color(hex: 0x15202B, dark: 0xE8EDF2)           // 본문 글자
    static let inkFixed = Color(hex: 0x15202B)                       // 밝은 칩(민트·노랑) 위 글자
    static let night = Color(hex: 0x15202B, dark: 0x1E2A36)         // 어두운 요약 카드 바탕
    static let teal = Color(hex: 0x0B6B66, dark: 0x2A9D93)
    static let tealDark = Color(hex: 0x0B4F4B, dark: 0x8FD8CE)
    static let mint = Color(hex: 0x5FD0C4)
    static let mintBg = Color(hex: 0xF3FAF9, dark: 0x16302D)
    static let missionGreen = Color(hex: 0x2E6B63, dark: 0x24564F)   // 미션 탭 다음 미션 블록
    static let tealBg = Color(hex: 0xE3F1EF, dark: 0x173531)
    static let sub = Color(hex: 0x4A5560, dark: 0xAEB8C2)
    static let sub2 = Color(hex: 0x5B6670, dark: 0x9AA5AF)
    static let muted = Color(hex: 0x8A949E, dark: 0x7F8A95)
    static let border = Color(hex: 0xDCE1E6, dark: 0x2E3945)
    static let line = Color(hex: 0xEEF1F3, dark: 0x26303A)
    static let track = Color(hex: 0xE6EAEE, dark: 0x2C3742)
    static let dash = Color(hex: 0xC9D0D6, dark: 0x3E4A56)
    static let yellow = Color(hex: 0xF2C14E)
    static let gold = Color(hex: 0xF3CF6A)
    static let cream = Color(hex: 0xFFF7E0, dark: 0x3A321C)
    static let slate = Color(hex: 0x3A4651)
    static let shelter = Color(hex: 0x1E2436, dark: 0x1A2032)
    static let up = Color(hex: 0xC0392B, dark: 0xF07468)
    static let down = Color(hex: 0x1B5E96, dark: 0x6FB0EA)
    static let orange = Color(hex: 0xE8862A)
    static let purple = Color(hex: 0x7A4FC8, dark: 0x9B78E0)
    static let blue = Color(hex: 0x2F8FD8)
    static let green = Color(hex: 0x12A06E)
    static let forecast = Color(hex: 0x2C62A8, dark: 0x6FA6E8)      // 이번 주 예보 글자·고른 칸
    static let forecastBg = Color(hex: 0xEEF4FB, dark: 0x16263A)    // 예보 설명 상자
    static let brown = Color(hex: 0x8A5A1E, dark: 0xD9A86A)         // 미니 미션 작은 제목
    static let holdColors: [Color] = [Color(hex: 0x7A4FC8), Color(hex: 0x2F8FD8), Color(hex: 0x12A06E),
                                      Color(hex: 0xE8862A), Color(hex: 0xC2477A), Color(hex: 0x5B6670)]

    // 쉬운 말 개편 색 규칙: 화면 사이에서 같은 뜻엔 같은 색 (유리/불리는 색맹을 고려해 파랑/주황)
    static let myReal = Color(hex: 0x2E6B63, dark: 0x5FB3A6)     // 내 실제 투자
    static let basket = Color(hex: 0xD9873A, dark: 0xE9A564)     // 모의 바구니
    static let rule = Color(hex: 0x6847B8, dark: 0x9B78E0)       // 인물 규칙 (점선과 함께)
    static let hold = Color(hex: 0x9AA1AC, dark: 0x7F8792)       // 그냥 둠
    static let favor = Color(hex: 0x2F6FB8, dark: 0x6FA6E6)      // 유리
    static let against = Color(hex: 0xD9873A, dark: 0xE9A564)    // 불리

    static func change(_ r: Double) -> Color { r >= 0 ? up : down }
    /// 가로 화면에서 내용 폭 상한
    static let maxWidth: CGFloat = 720
}

// 흰 카드 (시안의 border 1px · radius 18)
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    var bg: Color = Theme.card
    var stroke: Color? = Theme.border
    /// 요약 박스: 홈의 지금 평가액처럼 진한 바탕 (안의 글자색은 어두운 모드 색을 쓴다)
    var dark = false
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .modifier(DarkScheme(on: dark))
            .background(dark ? Theme.night : bg, in: RoundedRectangle(cornerRadius: 18))
            .overlay {
                if let stroke, !dark { RoundedRectangle(cornerRadius: 18).stroke(stroke, lineWidth: 1) }
            }
    }
}

extension View {
    /// 진한 요약 박스 (Card 가 아닌 묶음에)
    func darkBox(_ radius: CGFloat = 18) -> some View {
        modifier(DarkBox(radius: radius))
    }
}
private struct DarkScheme: ViewModifier {
    let on: Bool
    @ViewBuilder func body(content: Content) -> some View {
        if on { content.environment(\.colorScheme, .dark) } else { content }
    }
}
private struct DarkBox: ViewModifier {
    let radius: CGFloat
    func body(content: Content) -> some View {
        content.environment(\.colorScheme, .dark).background(Theme.night, in: RoundedRectangle(cornerRadius: radius))
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
    var pad: CGFloat? = nil          // 칩 안 가로 여백 (칸이 많은 줄은 좁게)
    var body: some View {
        let row = HStack(spacing: 6) {
            ForEach(items, id: \.0) { item in
                let on = item.0 == selection
                Button { selection = item.0 } label: {
                    Text(item.1)
                        .appFont(13, .bold)
                        .lineLimit(1)
                        .fixedSize()   // 글자를 자르지 않는다: 큰 글자로 안 맞으면 아래 ViewThatFits 가 가로 밀기로 바꾼다
                        .padding(.horizontal, pad ?? (fill ? 8 : 14))
                        .frame(maxWidth: fill ? .infinity : nil, minHeight: 40)
                        .foregroundStyle(on ? .white : Theme.ink)
                        .background(on ? accent : Theme.card, in: Capsule())
                        .overlay(Capsule().stroke(on ? accent : Theme.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        // 칸에 다 들어가면 같은 폭으로 채우고, 큰 글자라 넘치면 가로로 밀어 보게 한다
        let scroll = ScrollView(.horizontal, showsIndicators: false) { row.padding(.vertical, 1) }
        if fill { ViewThatFits(in: .horizontal) { row; scroll } } else { scroll }
    }
}

struct PrimaryButton: View {
    let title: String
    var color: Color = Theme.teal
    var fg: Color = .white
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).appFont(16, .bold)
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

/// 서 있는 인물 숨쉬기: 1pt 들썩였다 내려오고, 가끔 움찔한다. 늘리지 않아 픽셀이 뭉개지지 않는다.
/// 인물마다 박자·시작이 달라 다 같이 숨쉬지 않는다.
/// 화면에 있을 때만 돌고('동작 줄이기'면 끔), 걷기·숨 장면이 있는 인물(CharFigure·TownWalker)엔 붙이지 않는다
struct CharIdle: ViewModifier {
    var seed = 0
    @Environment(\.accessibilityReduceMotion) private var reduce
    @State private var up = false
    @State private var hop: CGFloat = 0
    func body(content: Content) -> some View {
        content
            .offset(y: (up ? -1 : 0) + hop)
            .task(id: reduce) {
                guard !reduce else { still(); return }
                let beat = Double.random(in: 1.2...2.0)
                try? await Task.sleep(for: .seconds(Double.random(in: 0...beat)))
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(beat * Double.random(in: 0.8...1.25)))
                    guard !Task.isCancelled else { break }
                    up.toggle()
                    guard !up, Int.random(in: 0..<5) == 0 else { continue }
                    withAnimation(.easeOut(duration: 0.09)) { hop = -1.5 }
                    try? await Task.sleep(for: .milliseconds(110))
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.5)) { hop = 0 }
                }
            }
            .onDisappear(perform: still)
    }
    /// 화면 밖·동작 줄이기: 반복 애니메이션을 끊고 제자리
    private func still() {
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) { up = false; hop = 0 }
    }
}

extension View {
    func charIdle(_ seed: Int = 0) -> some View { modifier(CharIdle(seed: seed)) }
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
            if let kicker { Text(kicker).appFont(14, .semibold).foregroundStyle(Theme.teal) }
            Text(title).appFont(24, .bold).tracking(-0.4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension View {
    // 탭 화면 공통 바탕
    func screen() -> some View {
        self.padding(.horizontal, 16).padding(.bottom, 24)
            .frame(maxWidth: Theme.maxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)          // 가로 화면: 가운데 한 줄로
            .foregroundStyle(Theme.ink)
    }
}

// 아이폰 설정 > 디스플레이 및 밝기 > 텍스트 크기(손쉬운 사용의 더 큰 텍스트 포함)를 따른다.
// 시안의 px 크기를 '기본 크기(큰)' 기준으로 두고, 설정에 맞춰 본문 글자와 같은 비율로 키운다.
struct AppFont: ViewModifier {
    let size: CGFloat
    let weight: Font.Weight
    @Environment(\.dynamicTypeSize) private var dts

    func body(content: Content) -> some View {
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dts))
        let scaled = UIFontMetrics(forTextStyle: .body).scaledValue(for: size, compatibleWith: traits)
        content.font(.system(size: scaled, weight: weight))
    }
}

extension View {
    func appFont(_ size: CGFloat, _ weight: Font.Weight = .regular) -> some View {
        modifier(AppFont(size: size, weight: weight))
    }
}

// 위 그래프를 고정하고 아래만 스크롤. 손쉬운 사용의 아주 큰 글자에서는 고정 영역이 화면을 다 차지하므로 함께 스크롤한다.
struct PinnedLayout<Header: View, Content: View>: View {
    @Environment(\.dynamicTypeSize) private var dts
    @Environment(\.verticalSizeClass) private var vsc
    @ViewBuilder var header: Header
    @ViewBuilder var content: Content
    var body: some View {
        // 아주 큰 글자나 가로 화면(세로 공간이 좁음)에서는 머리를 고정하지 않고 같이 스크롤
        if dts.isAccessibilitySize || vsc == .compact {
            ScrollView { VStack(spacing: 0) { header; content }.frame(maxWidth: Theme.maxWidth).frame(maxWidth: .infinity) }
        } else {
            VStack(spacing: 0) { header; ScrollView { content } }
        }
    }
}

/// 팝업 머리: 제목은 왼쪽, 닫기(X)는 오른쪽 (흐름 읽는 법과 같은 모양)
struct SheetTitleBar: View {
    let title: LocalizedStringKey
    let close: () -> Void
    var body: some View {
        HStack(alignment: .center) {
            Text(title).appFont(22, .bold).foregroundStyle(Theme.ink)
            Spacer(minLength: 8)
            Button(action: close) {
                Image(systemName: "xmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.sub)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain).accessibilityLabel("닫기")
        }
        .padding(.leading, 20).padding(.trailing, 8).padding(.top, 10)
    }
}
