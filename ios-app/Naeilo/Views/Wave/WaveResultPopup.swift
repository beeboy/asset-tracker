import SwiftUI

/// 한 판이 끝나면 게임 창 위에 뜨는 팝업: 별 · 점수 · 탄/내린 곳 · 진짜 날짜 · 한 판 더
struct WaveResultPopup: View {
    let result: WaveResult
    var again: () -> Void
    var close: () -> Void
    @State private var shown = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea()
            VStack(spacing: 10) {
                Text(result.course.island.name).appFont(13, .bold).foregroundStyle(Theme.sub)
                HStack(spacing: 6) {
                    ForEach(0..<3, id: \.self) { i in
                        Image(systemName: i < result.stars ? "star.fill" : "star")
                            .font(.system(size: 30, weight: .bold))
                            .foregroundStyle(i < result.stars ? Theme.yellow : Theme.dash)
                            .scaleEffect(shown ? 1 : 0.3)
                            .animation(.spring(response: 0.35, dampingFraction: 0.5).delay(0.15 * Double(i)), value: shown)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("별 \(result.stars)개")
                Text("\(result.score)점").appFont(34, .heavy).foregroundStyle(Theme.ink).monospacedDigit()
                Text(result.headline).appFont(13).foregroundStyle(Theme.sub)
                WaveRideChart(result: result).frame(height: 84)
                HStack(spacing: 12) {
                    Label("탄 곳", systemImage: "circle.fill")
                    Label("내린 곳", systemImage: "circle")
                }
                .appFont(11).foregroundStyle(Theme.muted)
                Text(result.dateLine).appFont(13, .bold).foregroundStyle(Theme.ink).multilineTextAlignment(.center)
                HStack(spacing: 8) {
                    Button(action: close) {
                        Text("닫기").appFont(15, .bold).foregroundStyle(Theme.ink)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Theme.line, in: RoundedRectangle(cornerRadius: 12))
                    }
                    Button(action: again) {
                        Text("한 판 더").appFont(15, .bold).foregroundStyle(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            .padding(20)
            .frame(maxWidth: 320)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 20))
            .padding(.horizontal, 24)
            .scaleEffect(shown ? 1 : 0.85)
            .opacity(shown ? 1 : 0)
        }
        .onAppear { withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) { shown = true } }
    }
}

/// 코스 전체 선 + 탄 구간 (번 곳 빨강 · 잃은 곳 파랑, 탄 곳 ● 내린 곳 ○)
struct WaveRideChart: View {
    let result: WaveResult
    var body: some View {
        Canvas { ctx, s in
            let v = result.course.values, n = v.count
            guard n > 1, let lo = v.min(), let hi = v.max() else { return }
            func pt(_ i: Int) -> CGPoint {
                CGPoint(x: s.width * CGFloat(i) / CGFloat(n - 1), y: s.height - 6 - CGFloat((v[i] - lo) / max(hi - lo, 1)) * (s.height - 12))
            }
            var all = Path()
            all.move(to: pt(0))
            for i in 1..<n { all.addLine(to: pt(i)) }
            ctx.stroke(all, with: .color(Theme.dash), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
            for r in result.rides where r.to > r.from {
                var seg = Path()
                seg.move(to: pt(r.from))
                for i in (r.from + 1)...r.to { seg.addLine(to: pt(i)) }
                let col = r.gain > 0 ? Theme.up : Theme.down
                ctx.stroke(seg, with: .color(col), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                let a = pt(r.from), b = pt(r.to)
                ctx.fill(Path(ellipseIn: CGRect(x: a.x - 3.5, y: a.y - 3.5, width: 7, height: 7)), with: .color(col))
                ctx.fill(Path(ellipseIn: CGRect(x: b.x - 3.5, y: b.y - 3.5, width: 7, height: 7)), with: .color(Theme.card))
                ctx.stroke(Path(ellipseIn: CGRect(x: b.x - 3.5, y: b.y - 3.5, width: 7, height: 7)), with: .color(col), lineWidth: 2)
            }
        }
        .accessibilityLabel(result.headline)
    }
}
