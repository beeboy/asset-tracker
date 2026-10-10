import WidgetKit
import SwiftUI
import AppIntents

// 인물 보상 위젯 (캐릭터 설정 스레드 시안 reward-widgets): 본전 진행(작은) · 블록(작은) · 오늘의 움직임(중간)
// 위젯마다 인물을 고른다. 아직 못 만난 인물은 실루엣으로 보이고, 누르면 앱의 쉼터로 간다.
// 앱 타깃에도 같이 넣어 설정의 시안 조작 > 인물 위젯 미리보기에서 다섯 인물을 한 번에 본다.
// 기본은 인물 색 그라데이션 바탕, 다크 모드는 #1c1c1f 바탕에 인물 색은 빛·제목·막대·블록·말풍선 테두리에만.

enum WhoEnum: String, AppEnum {
    case seri, sio, seonbae, ir, sua
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "인물"
    static var caseDisplayRepresentations: [WhoEnum: DisplayRepresentation] = [
        .seri: "세리", .sio: "시오", .seonbae: "선배", .ir: "이르", .sua: "수아",
    ]
}

struct WhoIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "인물 고르기"
    static var description = IntentDescription("위젯에 나올 인물을 골라요. 아직 못 만난 인물은 실루엣으로 보여요.")
    @Parameter(title: "인물", default: .seri) var who: WhoEnum
    init() {}
    init(_ w: WhoEnum) { who = w }
}

struct CharEntry: TimelineEntry {
    let date: Date
    let r: WReward
    let c: WChar
    var locked: Bool
}

struct CharProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> CharEntry { CharEntry(date: Date(), r: .sample, c: WChar.all[0], locked: false) }
    func snapshot(for cfg: WhoIntent, in context: Context) async -> CharEntry { entry(cfg, preview: context.isPreview) }
    func timeline(for cfg: WhoIntent, in context: Context) async -> Timeline<CharEntry> {
        Timeline(entries: [entry(cfg, preview: false)], policy: .after(MarketHours.nextRefresh()))
    }
    func recommendations() -> [AppIntentRecommendation<WhoIntent>] {
        WhoEnum.allCases.map { AppIntentRecommendation(intent: WhoIntent($0), description: WChar.of($0.rawValue).name) }
    }
    private func entry(_ cfg: WhoIntent, preview: Bool) -> CharEntry {
        let r = Store.read(WReward.self, "reward.json") ?? .sample
        let c = WChar.of(cfg.who.rawValue)
        // 갤러리 미리보기는 실제 모습, 홈 화면에서는 만난 인물만
        return CharEntry(date: Date(), r: r, c: c, locked: !preview && !(c.id == "seri" || r.friendsOn.contains(c.id)))
    }
}

struct CharRecover: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "char.recover", intent: WhoIntent.self, provider: CharProvider()) { e in
            CharFrame(e: e) { RecoverCharView(e: e) }
        }
        .configurationDisplayName("인물 · 본전 진행").description("본전(목표)까지 몇 %인지, 인물이 같이 봐요")
        .supportedFamilies([.systemSmall])
    }
}
struct CharBlock: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "char.block", intent: WhoIntent.self, provider: CharProvider()) { e in
            CharFrame(e: e) { BlockCharView(e: e) }
        }
        .configurationDisplayName("인물 · 블록").description("1000칸 중 지금 채우는 100칸과 10층 탑")
        .supportedFamilies([.systemSmall])
    }
}
struct CharMoves: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "char.moves", intent: WhoIntent.self, provider: CharProvider()) { e in
            CharFrame(e: e) { MovesCharView(e: e) }
        }
        .configurationDisplayName("인물 · 오늘의 움직임").description("종목별 오늘 등락과 인물의 한마디")
        .supportedFamilies([.systemMedium])
    }
}

// MARK: 공통

private func rgb(_ x: UInt32) -> Color {
    Color(red: Double((x >> 16) & 0xff) / 255, green: Double((x >> 8) & 0xff) / 255, blue: Double(x & 0xff) / 255)
}

/// 인물 색: 잠겨 있으면 회색
struct CharStyle {
    let c: WChar
    let locked: Bool
    let dark: Bool
    var accent: Color { locked ? Color(white: 0.55) : rgb(c.accent) }
    /// 제목 색: 다크에선 인물 색, 기본에선 흰색 조금 흐리게
    var heading: Color { dark ? accent : .white.opacity(0.78) }
    var bubbleFill: Color { dark ? .clear : .white.opacity(0.14) }
    var bubbleStroke: Color { dark ? accent.opacity(0.7) : .clear }
}

struct CharFrame<Content: View>: View {
    let e: CharEntry
    @ViewBuilder var content: Content
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        content
            .foregroundStyle(.white)
            .environment(\.colorScheme, .dark)
            .environment(\.charDark, scheme == .dark)
            .containerBackground(for: .widget) { CharBG(c: e.c, locked: e.locked, dark: scheme == .dark) }
            .widgetURL(URL(string: e.locked ? "naeilo://shelter" : "naeilo://guide")!)
    }
}

struct CharBG: View {
    let c: WChar, locked: Bool, dark: Bool
    var body: some View {
        if dark {
            ZStack {
                Color(red: 0x1c / 255, green: 0x1c / 255, blue: 0x1f / 255)
                RadialGradient(colors: [(locked ? Color.gray : rgb(c.accent)).opacity(0.22), .clear], center: .bottomLeading, startRadius: 0, endRadius: 220)
            }
        } else if locked {
            LinearGradient(colors: [Color(white: 0.16), Color(white: 0.30)], startPoint: .topLeading, endPoint: .bottomTrailing)
        } else {
            LinearGradient(colors: [rgb(c.g0), rgb(c.g1)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }
}

/// 도트 그림: 최근접 확대. 못 만난 인물은 실루엣
struct CharSprite: View {
    let name: String
    let locked: Bool
    var height: CGFloat
    @Environment(\.widgetRenderingMode) private var mode
    var body: some View {
        Group {
            if locked { Image(name).interpolation(.none).resizable().renderingMode(.template).foregroundStyle(Color.white.opacity(0.22)) }
            else if mode == .accented, #available(iOS 18, *) {
                // 투명·틴트 홈 화면: 그대로 두면 시스템이 그림 전체를 한 색(흰색)으로 칠해 흰 덩어리가 된다.
                // 캐릭터 설정 스레드가 만든 회색 단계 그림(_tint)을 쓰고, 밝기만 살려 틴트 색으로 칠하게 한다
                Image(name + "_tint").interpolation(.none).resizable().widgetAccentedRenderingMode(.desaturated)
            } else { Image(name).interpolation(.none).resizable() }
        }
        .aspectRatio(contentMode: .fit)
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

private func lockLine(_ c: WChar) -> String { "인터미션 \(c.week)주차에 만나요" }

/// 1억 2,340만
private func eokMan(_ v: Double) -> String {
    let man = Int((abs(v) / 1e4).rounded()), eok = man / 10000, rest = man % 10000
    if eok == 0 { return Fmt.comma(man) + "만" }
    return "\(eok)억" + (rest > 0 ? " \(Fmt.comma(rest))만" : "")
}
private let upC = Color(red: 1, green: 0.42, blue: 0.42)
private let dnC = Color(red: 0.42, green: 0.61, blue: 1)

// MARK: 본전 진행 (작은)

struct RecoverCharView: View {
    let e: CharEntry
    var body: some View {
        StyleReader { dark in
            let s = CharStyle(c: e.c, locked: e.locked, dark: dark), r = e.r
            ZStack(alignment: .bottomTrailing) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(r.keyName)까지").font(.system(size: 12, weight: .semibold)).foregroundStyle(s.heading)
                    if e.locked {
                        Image(systemName: "lock.fill").font(.system(size: 22, weight: .semibold)).foregroundStyle(.white.opacity(0.7)).padding(.vertical, 4)
                        Text("인터미션\n\(e.c.week)주차에 만나요").font(.system(size: 12, weight: .semibold)).foregroundStyle(.white.opacity(0.8))
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        Text("\(Int((r.pct * 100).rounded()))%").font(.system(size: 34, weight: .regular)).minimumScaleFactor(0.6).lineLimit(1)
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(.white.opacity(0.18))
                                Capsule().fill(s.accent).frame(width: g.size.width * r.pct)
                            }
                        }
                        .frame(height: 6).padding(.trailing, 46)
                    }
                    Spacer(minLength: 0)
                    if !e.locked {
                        let d = (r.pct - r.pctYesterday) * 100
                        (Text("남은 \(eokMan(r.remain)) · 어제 ").foregroundStyle(.white.opacity(0.75))
                         + Text((d >= 0 ? "+" : "") + String(format: "%.1f%%p", d)).foregroundStyle(d >= 0 ? upC : dnC))
                            .font(.system(size: 10, weight: .medium)).lineLimit(1).minimumScaleFactor(0.8)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                CharSprite(name: "w_\(e.c.id)_raise", locked: e.locked, height: 72)
                    .offset(x: 8, y: -10)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(e.locked ? "\(e.c.name), \(lockLine(e.c))" : "\(e.c.name), \(e.r.keyName)까지 \(Int((e.r.pct * 100).rounded()))%")
    }
}

// MARK: 블록 (작은): 10×10 칸 + 10층 탑, 인물이 둘 사이에서 팔을 벌려 잇는다

struct BlockCharView: View {
    let e: CharEntry
    var body: some View {
        StyleReader { dark in
            let s = CharStyle(c: e.c, locked: e.locked, dark: dark), r = e.r
            let block = min(10, r.cells / 100 + 1), fill = r.cells >= 1000 ? 100 : r.cells % 100
            let cell: CGFloat = 7, gap: CGFloat = 1.6, grid = cell * 10 + gap * 9
            VStack(alignment: .leading, spacing: 6) {
                Text(e.locked ? lockLine(e.c) : "\(block)번째 블록 채우는 중").font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(s.heading).lineLimit(1).minimumScaleFactor(0.8)
                ZStack(alignment: .bottomLeading) {
                    HStack(alignment: .bottom, spacing: 0) {
                        // 10×10: 위에서부터 왼→오로 채운다
                        VStack(spacing: gap) {
                            ForEach(0..<10, id: \.self) { row in
                                HStack(spacing: gap) {
                                    ForEach(0..<10, id: \.self) { col in
                                        RoundedRectangle(cornerRadius: 1.2)
                                            .fill(row * 10 + col < (e.locked ? 0 : fill) ? s.accent : Color.white.opacity(0.13))
                                            .frame(width: cell, height: cell)
                                    }
                                }
                            }
                        }
                        Spacer(minLength: 0)
                        // 10층 탑: 아래에서부터, 다 채운 블록은 진하게, 지금 블록은 테두리
                        VStack(spacing: 2) {
                            ForEach((0..<10).reversed(), id: \.self) { f in
                                let done = !e.locked && f < block - 1, cur = !e.locked && f == block - 1
                                RoundedRectangle(cornerRadius: 1.5)
                                    .fill(done ? s.accent : cur ? s.accent.opacity(0.45) : Color.white.opacity(0.13))
                                    .overlay(RoundedRectangle(cornerRadius: 1.5).stroke(cur ? s.accent : .clear, lineWidth: 1))
                                    .frame(width: 16, height: (grid - 18) / 10)
                            }
                        }
                    }
                    .frame(height: grid)
                    // 발은 칸의 맨 아랫줄, 두 팔은 칸과 탑에 닿게
                    CharSprite(name: "w_\(e.c.id)_spread", locked: e.locked, height: 62)
                        .offset(y: 9)    // 그림 아래 빈 줄만큼 내려 발을 칸 맨 아랫줄에
                        .frame(maxWidth: .infinity)
                        .padding(.leading, grid - 6).padding(.trailing, 10)
                }
                Spacer(minLength: 0)
                if !e.locked {
                    let d = r.cells - r.cellsYesterday
                    (Text("\(fill)").font(.system(size: 20)) + Text("/100 · 어제 ").font(.system(size: 10)).foregroundColor(.white.opacity(0.75))
                     + Text((d >= 0 ? "+" : "") + "\(d)").font(.system(size: 10)).foregroundColor(d >= 0 ? upC : dnC))
                        .lineLimit(1)
                } else {
                    Text("앱의 쉼터에서 봐요 ›").font(.system(size: 10, weight: .semibold)).foregroundStyle(.white.opacity(0.6))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(e.locked ? "\(e.c.name), \(lockLine(e.c))" : "\(e.c.name), 1000칸 중 \(e.r.cells)칸")
    }
}

// MARK: 오늘의 움직임 (중간)

struct MovesCharView: View {
    let e: CharEntry
    var body: some View {
        StyleReader { dark in
            let s = CharStyle(c: e.c, locked: e.locked, dark: dark), r = e.r
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("오늘의 움직임").font(.system(size: 12, weight: .semibold)).foregroundStyle(s.heading)
                    HStack(alignment: .top, spacing: 4) {
                        CharSprite(name: "w_\(e.c.id)", locked: e.locked, height: 92).frame(width: 66)
                        Text(e.locked ? "인터미션 \(e.c.week)주차에\n만나요" : e.c.line(r))
                            .font(.system(size: 11, weight: .medium)).lineLimit(3).fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 8).padding(.vertical, 6)
                            .background(s.bubbleFill, in: RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(s.bubbleStroke, lineWidth: 1))
                            .padding(.top, 4)
                    }
                    Spacer(minLength: 0)
                    Text("다음: " + (r.next ?? "예정된 일정 없음")).font(.system(size: 10, weight: .medium))
                        .foregroundStyle(.white.opacity(0.8)).lineLimit(1).minimumScaleFactor(0.8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 6) {
                    (Text(e.locked ? "" : eokMan(r.total) + " ").foregroundColor(.white)
                     + Text(e.locked ? " " : Fmt.arrow(r.dayChg) + String(format: "%.1f%%", abs(r.dayChg) * 100)).foregroundColor(r.dayChg >= 0 ? upC : dnC).bold())
                        .font(.system(size: 12, weight: .medium)).lineLimit(1).minimumScaleFactor(0.8)
                    tiles(r, dark: dark)
                }
                .frame(width: 138)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(e.locked ? "\(e.c.name), \(lockLine(e.c))" : "오늘의 움직임, \(e.c.name): \(e.c.line(e.r))")
    }

    /// 종목 칸: 넓이는 비중, 오르면 빨강, 내리면 파랑
    private func tiles(_ r: WReward, dark: Bool) -> some View {
        GeometryReader { g in
            let ts = r.tiles.isEmpty ? [WReward.Tile(t: "-", w: 1, c: 0)] : r.tiles
            let sumW = ts.reduce(0) { $0 + max(0.2, $1.w) }, gap: CGFloat = 5
            let avail = g.size.width - gap * CGFloat(ts.count - 1)
            HStack(spacing: gap) {
                ForEach(Array(ts.enumerated()), id: \.offset) { _, t in
                    let up = t.c >= 0
                    let fillC: Color = e.locked ? .white.opacity(0.1)
                        : dark ? (up ? Color(red: 0.49, green: 0.21, blue: 0.21) : Color(red: 0.2, green: 0.26, blue: 0.36))
                        : (up ? Color(red: 0.95, green: 0.35, blue: 0.38).opacity(0.55) : Color(red: 0.42, green: 0.58, blue: 0.95).opacity(0.45))
                    VStack(alignment: .leading) {
                        Text(e.locked ? "" : t.t).font(.system(size: 11, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.7)
                        Spacer(minLength: 0)
                        if !e.locked {
                            Text(Fmt.arrow(t.c) + String(format: "%.1f%%", abs(t.c) * 100))
                                .font(.system(size: max(10, min(20, 26 * max(0.2, t.w) / sumW * 1.6)), weight: .regular))
                                .lineLimit(1).minimumScaleFactor(0.6)
                        }
                    }
                    .padding(6)
                    .frame(width: avail * max(0.2, t.w) / sumW, height: g.size.height, alignment: .leading)
                    .background(fillC, in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }
}

/// CharFrame 이 글자를 늘 밝게 쓰려고 colorScheme 을 .dark 로 덮으므로, 기본·다크 구분은 원래 값을 따로 읽는다
struct StyleReader<Content: View>: View {
    @ViewBuilder var content: (Bool) -> Content
    @Environment(\.charDark) private var dark
    var body: some View { content(dark) }
}

private struct CharDarkKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    var charDark: Bool {
        get { self[CharDarkKey.self] }
        set { self[CharDarkKey.self] = newValue }
    }
}
