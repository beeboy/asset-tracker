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
    /// 위젯 8개: 고르기 칸 이름·아이콘, 카드 소개 한 줄, 인물 한마디
    private struct W { let name: String; let icon: String; let intro: String; let say: String }
    private static let list: [W] = [
        W(name: "언덕", icon: "chart.line.uptrend.xyaxis", intro: "최근 10일 자산이 픽셀 언덕이 돼요.", say: "언덕이 조금 높아졌네."),
        W(name: "사다리", icon: "stairs", intro: "본전까지 사다리를 한 칸씩 올라요.", say: "한 칸 더 올라왔어."),
        W(name: "블록 탑", icon: "square.stack.3d.up", intro: "100칸이 한 층, 10층 탑을 쌓아요.", say: "한 층씩 쌓고 있어."),
        W(name: "전광판", icon: "tv", intro: "오늘 내 자산이 전광판에 떠요.", say: "오늘도 불 켜졌어."),
        W(name: "키 재기", icon: "ruler", intro: "지난 날과 오늘, 키를 재 봐요.", say: "어제보다 조금 컸네."),
        W(name: "퍼즐", icon: "puzzlepiece", intro: "20조각 퍼즐, 다 맞추면 깃발.", say: "조각 하나 더 맞췄어."),
        W(name: "꽃밭", icon: "camera.macro", intro: "내 목표가 홈 화면에서 꽃으로 피어나요.", say: "다 피면 다음 밭이야."),
        W(name: "줄다리기", icon: "arrow.left.and.right", intro: "오른 종목과 내린 종목이 줄을 당겨요.", say: "오늘은 우리 편이 당겼어."),
    ]
    private static let modes = ["라이트", "다크", "투명", "틴트"]

    @State private var picked = 6          // 기본 꽃밭
    @State private var mode: Int?          // nil: 아이폰 설정 따라감
    @State private var showAmount = "hide"
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
                    if let img = rendered {
                        ShareLink(item: img, preview: SharePreview("naeilo 미션 위젯", image: img)) {
                            Label("이미지로 저장 · 공유", systemImage: "square.and.arrow.up").appFont(16, .bold)
                                .frame(maxWidth: .infinity, minHeight: 50).foregroundStyle(.white)
                                .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                        }
                    }
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle("미션 위젯 공유").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
        }
    }

    // 공유 이미지는 화면 모드와 상관없이 같은 어두운 카드
    private var card: some View {
        let w = Self.list[picked], f = m.homeFriendShown
        return VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text("귀여운 주식 위젯").font(.system(size: 24, weight: .bold))
                Text(w.intro).font(.system(size: 13)).foregroundStyle(Color(hex: 0xAAB3C2))
            }
            HStack(alignment: .center, spacing: 14) {
                widget(f)
                // 말풍선: 왼쪽 꼬리가 위젯 속 인물을 가리킨다
                VStack(alignment: .leading, spacing: 4) {
                    Text(m.friendName(f)).font(.system(size: 12, weight: .semibold)).foregroundStyle(Theme.mint)
                    Text(w.say).font(.system(size: 14)).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(Color(hex: 0x1D2430), in: RoundedRectangle(cornerRadius: 16))
                .overlay(alignment: .bottomLeading) {
                    BubbleTail().fill(Color(hex: 0x1D2430)).frame(width: 10, height: 16).offset(x: -9, y: -18)
                }
            }
            HStack {
                Text("매일 1분").foregroundStyle(Color(hex: 0x8B95A5))
                Spacer()
                Text("App Store에서 naeilo").fontWeight(.semibold)
            }
            .font(.system(size: 12))
        }
        .foregroundStyle(Color(hex: 0xF2F4F7))
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 20)
        .background(Color(hex: 0x10151F), in: RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).stroke(Color(hex: 0x222A37)))
        .frame(width: 358)
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

    @MainActor private var rendered: Image? {
        let r = ImageRenderer(content: card.environment(m))
        r.scale = 3
        return r.uiImage.map { Image(uiImage: $0) }
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
