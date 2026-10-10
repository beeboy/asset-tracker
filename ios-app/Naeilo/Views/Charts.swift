import SwiftUI

// 작은 가격 그래프: 오르면 빨강, 내리면 파랑. 평균 단가 점선은 범위 안일 때만.
struct Sparkline: View {
    let points: [Double]
    var avg: Double? = nil
    var lineWidth: CGFloat = 1.6
    var showEndDot = false

    var body: some View {
        GeometryReader { g in
            let lo0 = points.min() ?? 0, hi0 = points.max() ?? 1
            let avgIn = avg.map { $0 >= lo0 && $0 <= hi0 } ?? false
            let lo = avgIn ? min(lo0, avg!) : lo0, hi = avgIn ? max(hi0, avg!) : hi0
            let n = max(1, points.count - 1)
            let x = { (i: Int) in CGFloat(i) / CGFloat(n) * g.size.width }
            let y = { (v: Double) in (1 - CGFloat((v - lo) / max(1e-9, hi - lo))) * (g.size.height - 4) + 2 }
            let up = (points.last ?? 0) >= (points.first ?? 0)
            ZStack {
                if avgIn, let a = avg {
                    Path { p in p.move(to: CGPoint(x: 0, y: y(a))); p.addLine(to: CGPoint(x: g.size.width, y: y(a))) }
                        .stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                }
                Path { p in
                    for (i, v) in points.enumerated() {
                        let pt = CGPoint(x: x(i), y: y(v))
                        i == 0 ? p.move(to: pt) : p.addLine(to: pt)
                    }
                }
                .stroke(Theme.change(up ? 1 : -1), style: StrokeStyle(lineWidth: lineWidth, lineJoin: .round))
                if showEndDot, let l = points.last {
                    Circle().fill(Theme.change(up ? 1 : -1)).frame(width: 7, height: 7).position(x: x(points.count - 1), y: y(l))
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
