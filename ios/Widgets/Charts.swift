import SwiftUI

/// 선 그래프 (최근 값 흐름)
struct Spark: View {
    let values: [Double]
    var color: Color
    var width: CGFloat = 2.2
    var body: some View {
        GeometryReader { g in
            let v = values, lo = v.min() ?? 0, hi = v.max() ?? 1, r = max(hi - lo, 1)
            Path { p in
                for (i, x) in v.enumerated() {
                    let pt = CGPoint(x: g.size.width * CGFloat(i) / CGFloat(max(1, v.count - 1)), y: g.size.height * (1 - CGFloat((x - lo) / r)))
                    i == 0 ? p.move(to: pt) : p.addLine(to: pt)
                }
            }
            .stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
        }
    }
}

/// 부채꼴 전망: 지난 값(흰 선) → 오늘 → 전망 띠(5~95%, 25~75%) + 중앙선 + 목표선
struct FanChart: View {
    let hist: [Double]
    let fan: Snapshot.Fan
    let goal: Double
    var log = false
    var tint: Tint
    var labels = true

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height - (labels ? 14 : 0)
            let n = hist.count, m = fan.p50.count, days = Double(n + (m - 1) * 21)
            let all = hist + fan.p5 + fan.p95 + [goal]
            let lo = max(1, log ? (all.min() ?? 1) * 0.9 : 0.95 * (all.min() ?? 0)), hi = (fan.p95.max() ?? goal) * 1.02
            let y: (Double) -> CGFloat = { v in
                let c = min(max(v, lo), hi)
                let t = log ? (Foundation.log(c) - Foundation.log(lo)) / (Foundation.log(hi) - Foundation.log(lo)) : (c - lo) / (hi - lo)
                return h * (1 - CGFloat(t))
            }
            let xh: (Int) -> CGFloat = { i in w * CGFloat(Double(i) / days) }
            let xf: (Int) -> CGFloat = { k in w * CGFloat(Double(n - 1 + k * 21) / days) }
            let band = { (a: [Double], b: [Double]) -> Path in
                Path { p in
                    for k in 0..<m { let pt = CGPoint(x: xf(k), y: y(a[k])); k == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                    for k in (0..<m).reversed() { p.addLine(to: CGPoint(x: xf(k), y: y(b[k]))) }
                    p.closeSubpath()
                }
            }
            ZStack(alignment: .topLeading) {
                band(fan.p5, fan.p95).fill(tint.c(Palette.acc, 0.18))
                band(fan.p25, fan.p75).fill(tint.c(Palette.acc, 0.38))
                Path { p in p.move(to: CGPoint(x: 0, y: y(goal))); p.addLine(to: CGPoint(x: w, y: y(goal))) }
                    .stroke(tint.c(Palette.goal, 0.9), style: StrokeStyle(lineWidth: 1.4, dash: [4, 3]))
                Text(labels ? "목표 \(Fmt.eok(goal, 0))" : Fmt.eok(goal, 0)).font(.system(size: 10, weight: .bold)).foregroundStyle(tint.c(Palette.goal))
                    .position(x: w - 24, y: y(goal) - 7)
                Path { p in for k in 0..<m { let pt = CGPoint(x: xf(k), y: y(fan.p50[k])); k == 0 ? p.move(to: pt) : p.addLine(to: pt) } }
                    .stroke(Color.white.opacity(0.95), lineWidth: 1.8)
                Path { p in for i in 0..<n { let pt = CGPoint(x: xh(i), y: y(hist[i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } }
                    .stroke(.white, style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
                Path { p in p.move(to: CGPoint(x: xh(n - 1), y: 0)); p.addLine(to: CGPoint(x: xh(n - 1), y: h)) }.stroke(Color.white.opacity(0.35), lineWidth: 1)
                if labels {
                    Text("오늘").font(.system(size: 9.5)).foregroundStyle(Color.white.opacity(0.6)).position(x: xh(n - 1), y: h + 8)
                    Text(fan.md.last.map { yymm($0) } ?? "").font(.system(size: 9.5)).foregroundStyle(Color.white.opacity(0.6)).position(x: w - 14, y: h + 8)
                }
            }
        }
    }
    private func yymm(_ d: Date) -> String { let f = DateFormatter(); f.dateFormat = "yy.MM"; return f.string(from: d) }
}

/// 원형 진행 링
struct Ring: View {
    let progress: Double
    var color: Color
    var line: CGFloat = 11
    var body: some View {
        ZStack {
            Circle().stroke(Color.white.opacity(0.14), lineWidth: line)
            Circle().trim(from: 0, to: min(1, max(0, progress))).stroke(color, style: StrokeStyle(lineWidth: line, lineCap: .round)).rotationEffect(.degrees(-90))
        }
    }
}

/// 범위 막대: lo~hi 진한 구간, 중앙 눈금, 지금 위치 ▼
struct RangeBar: View {
    let lo: Double, hi: Double, mid: Double, now: Double
    var tint: Tint
    var height: CGFloat = 12
    var body: some View {
        GeometryReader { g in
            let pad = (hi - lo) * 0.35, a = lo - pad, b = hi + pad, w = g.size.width
            let x: (Double) -> CGFloat = { w * CGFloat((min(max($0, a), b) - a) / (b - a)) }
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.12)).frame(height: height)
                Capsule().fill(tint.c(Palette.good, 0.55)).frame(width: x(hi) - x(lo), height: height).offset(x: x(lo))
                Rectangle().fill(.white).frame(width: 2, height: height + 8).offset(x: x(mid) - 1)
                Image(systemName: "arrowtriangle.down.fill").font(.system(size: 13)).foregroundStyle(.white)
                    .offset(x: x(now) - 6.5, y: -height - 2)
            }
            .frame(height: g.size.height)
        }
    }
}
