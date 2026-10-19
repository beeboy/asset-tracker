import SwiftUI

// 작은 가격 그래프: 오르면 빨강, 내리면 파랑. 평균 단가 점선은 범위 안일 때만.
struct Sparkline: View {
    let points: [Double]
    var avg: Double? = nil
    var lineWidth: CGFloat = 1.6
    var showEndDot = false
    var avgLabel: String? = nil      // 점선 바로 밑, 오른쪽에 붙이는 글자
    var avgAlways = false            // 평균 단가가 범위 밖이어도 세로 범위를 넓혀 점선을 늘 보인다

    var body: some View {
        GeometryReader { g in
            let lo0 = points.min() ?? 0, hi0 = points.max() ?? 1
            let avgIn = avg.map { avgAlways || ($0 >= lo0 && $0 <= hi0) } ?? false
            let lo = avgIn ? min(lo0, avg!) : lo0, hi = avgIn ? max(hi0, avg!) : hi0
            let n = max(1, points.count - 1)
            let x = { (i: Int) in CGFloat(i) / CGFloat(n) * g.size.width }
            // 점선 금액을 붙일 때는 아래에 글자 한 줄 자리를 남겨 두어 늘 점선 밑에 들어가게 한다
            let bot: CGFloat = avgIn && avgLabel != nil ? 18 : 2
            let y = { (v: Double) in (1 - CGFloat((v - lo) / max(1e-9, hi - lo))) * (g.size.height - 2 - bot) + 2 }
            let up = (points.last ?? 0) >= (points.first ?? 0)
            ZStack {
                if avgIn, let a = avg {
                    Path { p in p.move(to: CGPoint(x: 0, y: y(a))); p.addLine(to: CGPoint(x: g.size.width, y: y(a))) }
                        .stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                    if let t = avgLabel {
                        Text(t).appFont(11, .semibold).foregroundStyle(Theme.muted).lineLimit(1)
                            .padding(.horizontal, 3).background(Theme.card.opacity(0.85), in: Capsule())
                            .frame(width: g.size.width, alignment: .trailing)
                            .position(x: g.size.width / 2, y: y(a) + 9)
                    }
                }
                Path { p in
                    for (i, v) in points.enumerated() {
                        let pt = CGPoint(x: x(i), y: y(v))
                        i == 0 ? p.move(to: pt) : p.addLine(to: pt)
                    }
                }
                .stroke(Theme.change(up ? 1 : -1), style: StrokeStyle(lineWidth: lineWidth, lineJoin: .round))
                if showEndDot, let l = points.last {
                    PulseDot(color: Theme.change(up ? 1 : -1)).position(x: x(points.count - 1), y: y(l))
                }
            }
        }
        .accessibilityHidden(true)
    }
}

// 글자 마크 로고 (상표 로고 아님). DRNK 는 드링커 도트 로고.
struct LogoTile: View {
    let symbol: String
    var size: CGFloat = 36
    var body: some View {
        let p = Sample.profiles[symbol]
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28).fill(Color(hex: p?.color ?? 0x5B6670))
            if let img = p?.logoImage {
                Image(img).interpolation(.none).resizable().frame(width: size * 0.86, height: size * 0.86)
            } else {
                // 추가한 종목은 이름 첫 글자
                let mono = p?.mono ?? String((Sample.symbol(symbol)?.name ?? StockCatalog.all.first { $0.id == symbol }?.name ?? symbol).prefix(1)).uppercased()
                Text(mono).font(.system(size: size * (mono.count > 1 ? 0.36 : 0.46), weight: .heavy))
                    .foregroundStyle(Color(hex: p?.fg ?? 0xFFFFFF))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

// 현재 점 심장 박동: 점에서 점 색 동심원이 두 번(두근) 살짝 퍼지다 사라지고 잠깐 쉰다.
// 원만 따로 작은 층에 그려서, 움직이는 동안 그래프 자체는 다시 그리지 않는다
enum Pulse {
    static let period = 1.6, spread = 0.9, gap = 0.2
}

/// 퍼지는 원만 (가운데 = 점 자리). 동작 줄이기를 켜면 안 그린다
struct PulseRing: View {
    let color: Color
    var dot: CGFloat = 7
    @Environment(\.accessibilityReduceMotion) private var still
    var body: some View {
        if !still {
            TimelineView(.animation(minimumInterval: 1 / 30)) { tl in
                Canvas { ctx, s in
                    let c = CGPoint(x: s.width / 2, y: s.height / 2)
                    let ph = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: Pulse.period)
                    for k in 0..<2 {
                        let a = (ph - Double(k) * Pulse.gap) / Pulse.spread
                        guard a > 0, a < 1 else { continue }
                        let r = dot / 2 + CGFloat(a) * dot * 1.5
                        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                                   with: .color(color.opacity(0.6 * (1 - a))), lineWidth: 1.5)
                    }
                }
            }
            .frame(width: dot * 4.2, height: dot * 4.2)
            .allowsHitTesting(false)
        }
    }
}

/// SwiftUI 쪽에서 쓰는 박동 점 (원 + 점)
struct PulseDot: View {
    let color: Color
    var dot: CGFloat = 7
    var body: some View {
        ZStack {
            PulseRing(color: color, dot: dot)
            Circle().fill(color).frame(width: dot, height: dot)
        }
    }
}

extension View {
    /// 그래프(Canvas) 위에 박동 원만 얹는다. at: 그래프 크기 → 점 자리 (없으면 nil)
    func pulse(_ color: Color, dot: CGFloat, at p: @escaping (CGSize) -> CGPoint?) -> some View {
        overlay {
            GeometryReader { g in
                if let q = p(g.size) { PulseRing(color: color, dot: dot).position(q) }
            }
            .allowsHitTesting(false)
        }
    }
}

// 금액이 바뀔 때 숫자가 한 자리씩 위아래로 넘어가며 바뀐다 (플립)
extension View {
    func roll(_ v: Double) -> some View {
        contentTransition(.numericText(value: v)).animation(.snappy, value: v)
    }
}

extension View {
    /// 글로 된 숫자(분석 카드 값 등)가 바뀔 때 플립
    func roll(_ s: String) -> some View {
        contentTransition(.numericText()).animation(.snappy, value: s)
    }
}
