import SwiftUI

// 미션 위젯 공유 (확정 시안): 인물 위젯 8개 중 하나와 위젯 모양(라이트·다크·투명·틴트)을 골라 이미지로 보낸다.
// 기본은 꽃밭, 모양은 아이폰 라이트/다크 설정, 금액은 숨김. 종목 이름은 위젯에 원래 없다.
struct ShareCardButton: View {
    var title = "미션 위젯 공유"
    var plain = false   // 흰 바탕 · 회색 테두리 (미션 탭 보조 버튼)
    @State private var show = false
    var body: some View {
        Button { show = true } label: {
            Label(title, systemImage: "square.and.arrow.up")
                .appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.8).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(plain ? Theme.ink : Theme.teal)
                .background(plain ? Theme.card : .clear, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(plain ? Theme.border : Theme.teal, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $show) { ShareCardSheet() }
    }
}

struct ShareCardSheet: View {
    /// 위젯 8개: 고르기 칸 이름·아이콘, 인물 한마디
    private struct W { let name: String; let icon: String; let say: String }
    private static let list: [W] = [
        W(name: "언덕", icon: "chart.line.uptrend.xyaxis", say: "언덕이 조금 높아졌네."),
        W(name: "사다리", icon: "stairs", say: "한 칸 더 올라왔어."),
        W(name: "블록 탑", icon: "square.stack.3d.up", say: "한 층씩 쌓고 있어."),
        W(name: "전광판", icon: "tv", say: "오늘도 불 켜졌어."),
        W(name: "키 재기", icon: "ruler", say: "어제보다 조금 컸네."),
        W(name: "퍼즐", icon: "puzzlepiece", say: "조각 하나 더 맞췄어."),
        W(name: "꽃밭", icon: "camera.macro", say: "다 피면 다음 밭이야."),
        W(name: "줄다리기", icon: "arrow.left.and.right", say: "오늘은 우리 편이 당겼어."),
    ]
    private static let modes = ["라이트", "다크", "투명", "틴트"]

    @State private var picked = 6          // 기본 꽃밭
    @State private var mode: Int?          // nil: 아이폰 설정 따라감
    @State private var showAmount = "hide"
    @State private var shareURL: URL?      // 버튼 누를 때만 그린 이미지
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    @Environment(AppModel.self) private var m

    private var curMode: Int { mode ?? (scheme == .dark ? 1 : 0) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("보낼 위젯 고르기").appFont(13).foregroundStyle(Theme.sub)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(Self.list.indices, id: \.self) { i in
                            let on = i == picked
                            Button { picked = i } label: {
                                VStack(spacing: 4) {
                                    Image(systemName: Self.list[i].icon).font(.system(size: 20))
                                    Text(Self.list[i].name).appFont(11, .semibold).lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, minHeight: 64)
                                .foregroundStyle(on ? .white : Theme.ink)
                                .background(on ? Theme.teal : Theme.card, in: RoundedRectangle(cornerRadius: 16))
                                .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.teal : Theme.border))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                    HStack {
                        Text("위젯 모양").appFont(13).foregroundStyle(Theme.sub)
                        Spacer()
                        Text("기본: 아이폰 설정 (\(Self.modes[scheme == .dark ? 1 : 0]))").appFont(11).foregroundStyle(Theme.muted)
                    }
                    ChipRow(items: Self.modes.indices.map { ($0, Self.modes[$0]) },
                            selection: Binding(get: { curMode }, set: { mode = $0 }), fill: true)

                    card.frame(maxWidth: .infinity)

                    ChipRow(items: [("hide", "금액 숨기기"), ("show", "금액 보이기")], selection: $showAmount, fill: true)
                    Button { shareURL = rendered } label: {
                            Label("이미지로 저장 · 공유", systemImage: "square.and.arrow.up").appFont(16, .bold)
                                .frame(maxWidth: .infinity, minHeight: 50).foregroundStyle(.white)
                                .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle("미션 위젯 공유").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
            .sheet(item: $shareURL) { ActivitySheet(url: $0).ignoresSafeArea() }
        }
    }

    /// 카드 색: 라이트는 흰 카드, 다크는 어두운 카드, 투명은 배경화면 같은 하늘색 그라데이션(유리 위젯이 비쳐 보이게), 틴트는 깊은 청록 그라데이션
    private struct Look { let bg: [UInt32]; let stroke: Color; let fg: Color; let sub: Color; let bubble: Color; let name: Color }
    private var look: Look {
        switch curMode {
        case 0: return Look(bg: [0xFFFFFF], stroke: Color(hex: 0xE3E7EC), fg: Color(hex: 0x15202B), sub: Color(hex: 0x5D6673), bubble: Color(hex: 0xF0F2F5), name: Color(hex: 0x0B6B66))
        case 2: return Look(bg: [0x3A6EA5, 0x7A6BBF, 0xD98FA8], stroke: .clear, fg: .white, sub: Color.white.opacity(0.8), bubble: Color.white.opacity(0.2), name: .white)
        case 3: return Look(bg: [0x0C2622, 0x1F4A42], stroke: .clear, fg: Color(hex: 0xF2F4F7), sub: Color(hex: 0x9CC9BF), bubble: Color(hex: 0x1E3A35), name: Theme.mint)
        default: return Look(bg: [0x10151F], stroke: Color(hex: 0x222A37), fg: Color(hex: 0xF2F4F7), sub: Color(hex: 0xAAB3C2), bubble: Color(hex: 0x1D2430), name: Theme.mint)
        }
    }

    // 공유 이미지 카드: 고른 위젯 모양에 맞춰 바탕색이 바뀐다
    private var card: some View {
        let w = Self.list[picked], f = m.homeFriendShown, l = look
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 14) {
                widget(f)
                // 말풍선: 왼쪽 꼬리가 위젯 속 인물을 가리킨다
                VStack(alignment: .leading, spacing: 4) {
                    Text(m.friendName(f)).font(.system(size: 12, weight: .semibold)).foregroundStyle(l.name)
                    Text(w.say).font(.system(size: 14)).fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)   // 풍선이 오른쪽 여백 20 까지 찬다
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(l.bubble, in: RoundedRectangle(cornerRadius: 16))
                .overlay(alignment: .bottomLeading) {
                    BubbleTail().fill(l.bubble).frame(width: 10, height: 16).offset(x: -9, y: -18)
                }
            }
            Text("naeilo").font(.system(size: 12, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .foregroundStyle(l.fg)
        .padding(20)
        .frame(width: 380)   // 와이드 카드, 사방 여백 20
        .background(LinearGradient(colors: l.bg.map { Color(hex: $0) }, startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).stroke(l.stroke))
        .environment(\.colorScheme, .dark)
    }

    /// 실제 인물 위젯을 고른 모양으로 그린다 (앱 '인물 위젯' 미리보기와 같은 크기 158)
    private func widget(_ f: String) -> some View {
        let e = CharEntry(date: Date(), r: Store.read(WReward.self, "reward.json") ?? .sample, c: WChar.of(f), locked: false, preview: true)
        let dark = curMode != 0
        let content = Group {
            switch picked {
            case 0: HillCharView(e: e)
            case 1: LadderCharView(e: e)
            case 2: TowerCharView(e: e)
            case 3: BoardCharView(e: e)
            case 4: HeightCharView(e: e)
            case 5: PuzzleCharView(e: e)
            case 6: GardenCharView(e: e)
            default: TugCharView(e: e)
            }
        }
        .padding(16).foregroundStyle(.white)
        .environment(\.colorScheme, .dark).environment(\.charDark, dark)
        .environment(\.charHide, showAmount == "hide")
        .frame(width: 158, height: 158)
        return ZStack {
            switch curMode {
            case 0, 1: CharBG(c: e.c, locked: false, dark: dark)
            case 2: Color.white.opacity(0.10)
            default: Color(hex: 0x15201E)
            }
            if curMode == 3 { content.grayscale(1).colorMultiply(Theme.mint) } else { content }
        }
        .frame(width: 158, height: 158)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(curMode == 2 ? Color.white.opacity(0.25) : .clear))
    }

    /// 저장 파일 이름: 날짜-위젯명-naeilo.png ('PNG 이미지' 대신)
    @MainActor private var rendered: URL? {
        let r = ImageRenderer(content: card.environment(m))
        r.scale = 3
        guard let data = r.uiImage?.pngData() else { return nil }
        let d = DateFormatter(); d.locale = Locale(identifier: "ko_KR"); d.dateFormat = "yyyy-MM-dd"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(d.string(from: Date()))-\(Self.list[picked].name)-naeilo.png")
        try? data.write(to: url)
        return url
    }
}

/// 말풍선 꼬리: 오른쪽 변이 풍선에 붙고 왼쪽 끝이 뾰족하다
struct BubbleTail: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.maxX, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.addLine(to: CGPoint(x: r.minX, y: r.maxY - r.height / 4))
        p.closeSubpath()
        return p
    }
}

/// 말풍선: 왼쪽 아래 꼬리가 인물 쪽을 가리킨다
struct BubbleShape: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path(roundedRect: r, cornerRadius: 12)
        p.move(to: CGPoint(x: r.minX + 6, y: r.maxY - 14))
        p.addLine(to: CGPoint(x: r.minX - 7, y: r.maxY - 4))
        p.addLine(to: CGPoint(x: r.minX + 12, y: r.maxY - 6))
        p.closeSubpath()
        return p
    }
}

extension URL: @retroactive Identifiable { public var id: String { absoluteString } }

/// 공유 시트: 버튼을 누른 뒤에만 뜬다
struct ActivitySheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: [url], applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
