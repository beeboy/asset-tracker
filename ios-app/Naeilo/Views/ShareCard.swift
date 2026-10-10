import SwiftUI

// 1000칸 공유 카드 (시안 34판): 기본은 금액을 숨기고 칸 수와 %만, 종목 이름은 넣지 않는다.
struct ShareCardButton: View {
    let filled: Int, mine: Int, market: Int
    let kicker: String
    let amountLine: String
    @State private var show = false
    var body: some View {
        Button { show = true } label: {
            Label("1000칸 공유 카드 만들기", systemImage: "square.and.arrow.up")
                .appFont(14, .bold).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(Theme.teal)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $show) { ShareCardSheet(filled: filled, mine: mine, market: market, kicker: kicker, amountLine: amountLine) }
    }
}

struct ShareCardSheet: View {
    let filled: Int, mine: Int, market: Int
    let kicker: String
    let amountLine: String
    @State private var showAmount = "hide"
    @Environment(\.dismiss) private var dismiss
    @Environment(AppModel.self) private var m

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    card.padding(.horizontal, 16)
                    ChipRow(items: [("hide", "금액 숨기기"), ("show", "금액 보이기")], selection: $showAmount, fill: true)
                        .padding(.horizontal, 16)
                    if let img = rendered {
                        ShareLink(item: img, preview: SharePreview("naeilo 1000칸", image: img)) {
                            Label("이미지로 저장 · 공유", systemImage: "square.and.arrow.up").appFont(16, .bold)
                                .frame(maxWidth: .infinity, minHeight: 50).foregroundStyle(.white)
                                .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal, 16)
                    }
                    Text("기본은 금액을 숨기고 칸 수와 %만 보여 줘요. 종목 이름은 넣지 않아요.").appFont(12).foregroundStyle(Theme.muted).padding(.horizontal, 16)
                }
                .padding(.vertical, 16)
            }
            .background(Theme.bg)
            .navigationTitle("1000칸 공유 카드").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
        }
    }

    private var line: String {
        showAmount == "show" ? amountLine : String(format: "%.1f", Double(filled) / 10) + "% 채웠어요." + (filled >= 100 ? " \(filled / 100 * 100)칸 넘었어요!" : "")
    }

    // 공유 이미지는 화면 모드와 상관없이 같은 어두운 카드
    private var card: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack { Text("naeilo").font(.system(size: 16, weight: .bold)); Spacer(); Text(today).font(.system(size: 12)).foregroundStyle(Color(hex: 0xC9D0D6)) }
            Text(kicker).font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.mint)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(filled.formatted()).font(.system(size: 36, weight: .bold))
                Text("/ 1,000칸").font(.system(size: 14)).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            Canvas { ctx, size in
                let s = size.width / 40
                for i in 0..<1000 {
                    let r = CGRect(x: CGFloat(i % 40) * s + 0.5, y: CGFloat(24 - i / 40) * s + 0.5, width: s - 1, height: s - 1)
                    let c: Color = i < mine ? Theme.mint : i < mine + market ? Color(hex: 0xA8E8E1) : Theme.slate
                    ctx.fill(Path(r), with: .color(c))
                }
                let r = CGRect(x: 39 * s + 1, y: 1, width: s - 2, height: s - 2)
                ctx.fill(Path(ellipseIn: r), with: .color(Theme.yellow))
            }
            .aspectRatio(320.0 / 200.0, contentMode: .fit)
            Text(line).font(.system(size: 15, weight: .semibold))
            // 홈에 둔 인물이 한마디 (금액·종목 이름은 넣지 않는다)
            let f = m.homeFriendShown, name = Shelter.friends.first { $0.id == f }?.name ?? "세리"
            HStack(alignment: .bottom, spacing: 8) {
                MotionSprite(friend: f, pose: .cheer, height: 84).frame(width: 72)
                VStack(alignment: .leading, spacing: 3) {
                    Text(name).font(.system(size: 11, weight: .semibold)).foregroundStyle(Theme.mint)
                    Text(Self.say(f, filled)).font(.system(size: 14, weight: .medium)).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(Color.white.opacity(0.08), in: BubbleShape())
                .overlay(BubbleShape().stroke(Color.white.opacity(0.25), lineWidth: 1))
                .padding(.bottom, 18)
                Spacer(minLength: 0)
            }
            Text("매일 1분, naeilo로").font(.system(size: 12)).foregroundStyle(Color(hex: 0xC9D0D6))
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(Color(hex: 0x15202B), in: RoundedRectangle(cornerRadius: 20))
        .frame(width: 358)
        .environment(\.colorScheme, .dark)
    }

    private var today: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일"
        return f.string(from: Date())
    }

    /// 인물별 한마디 (미션 안내 말투와 같은 결)
    static func say(_ f: String, _ n: Int) -> String {
        let left = 100 - n % 100           // 다음 100칸까지
        if let l = Lines.pick(n >= 1000 ? "share.full.\(f)" : "share.\(f)", ["n": n.formatted(), "left": "\(left)"]) { return l }
        if n >= 1000 {
            switch f {
            case "sio": return "1,000칸. 다 셌어."
            case "seonbae": return "다 채웠네. 여기까지 온 이유, 기억나?"
            case "ir": return "다 채웠어. 서두르지 않았잖아."
            case "sua": return "제때 다 채웠어요. 1,000칸이에요."
            default: return "…… 1,000칸, 다 채웠어요."
            }
        }
        switch f {
        case "sio": return "\(n.formatted())칸. 다음 100칸까지 \(left)칸. 셌어."
        case "seonbae": return "\(n.formatted())칸까지 왔네. 남은 \(left)칸은 언제쯤 채울 것 같아?"
        case "ir": return "다음 100칸까지 \(left)칸. 서두르지 않아도 채워져."
        case "sua": return "오늘도 제때 왔어요. 다음 100칸까지 \(left)칸이에요."
        default: return "…… 한 칸씩 왔어요. 다음 100칸까지 \(left)칸이에요."
        }
    }

    @MainActor private var rendered: Image? {
        let r = ImageRenderer(content: card.environment(m))
        r.scale = 3
        return r.uiImage.map { Image(uiImage: $0) }
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
