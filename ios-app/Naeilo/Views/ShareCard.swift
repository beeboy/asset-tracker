import SwiftUI

// 1000칸 공유 카드 (시안 34판): 기본은 금액을 숨기고 칸 수와 %만, 종목 이름은 넣지 않는다.
struct ShareCardButton: View {
    let filled: Int, mine: Int, market: Int
    let kicker: String
    let amountLine: String
    @State private var show = false
    var body: some View {
        let passed = filled / 100
        Button { show = true } label: {
            Label(passed > 0 ? "\(passed * 100)칸 공유 카드 만들기" : "공유 카드 만들기", systemImage: "square.and.arrow.up")
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
            HStack { Text("naeilo").font(.system(size: 16, weight: .bold)); Spacer(); Text("10월 9일").font(.system(size: 12)).foregroundStyle(Color(hex: 0xC9D0D6)) }
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
            Text("매일 1분, naeilo로").font(.system(size: 12)).foregroundStyle(Color(hex: 0xC9D0D6))
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(Color(hex: 0x15202B), in: RoundedRectangle(cornerRadius: 20))
        .frame(width: 358)
        .environment(\.colorScheme, .dark)
    }

    @MainActor private var rendered: Image? {
        let r = ImageRenderer(content: card)
        r.scale = 3
        return r.uiImage.map { Image(uiImage: $0) }
    }
}
