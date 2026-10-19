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

extension Path {
    /// 점들을 지나는 매끈한 곡선 (Catmull-Rom)
    mutating func smooth(_ p: [CGPoint]) {
        guard let f = p.first else { return }
        move(to: f)
        for i in 0..<max(0, p.count - 1) {
            let p0 = p[max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[min(p.count - 1, i + 2)]
            addCurve(to: p2, control1: CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6),
                     control2: CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6))
        }
    }
}

/// 자산 추이: 최근 며칠을 매끈한 선 하나로, 밑은 연한 채움, 첫날 값 높이에 점선.
/// 최고 날은 꽉 찬 점, 최저 날은 (투명·틴트에서는 빈) 점, 오늘은 테두리 있는 큰 점
struct TrendLine: View {
    let values: [Double]
    let color: Color
    let tint: Tint
    var body: some View {
        GeometryReader { g in
            let v = values, lo = v.min() ?? 0, hi = v.max() ?? 1, rg = max(hi - lo, 1)
            let pad: CGFloat = 4, w = g.size.width - pad * 2, h = g.size.height - pad * 2
            let pts = v.indices.map { i in CGPoint(x: pad + w * CGFloat(i) / CGFloat(max(1, v.count - 1)), y: pad + h * (1 - CGFloat((v[i] - lo) / rg))) }
            if let a = pts.first, let z = pts.last, pts.count > 1 {
                let iHi = v.lastIndex(of: hi) ?? 0, iLo = v.lastIndex(of: lo) ?? 0
                Path { p in p.smooth(pts); p.addLine(to: CGPoint(x: z.x, y: g.size.height)); p.addLine(to: CGPoint(x: a.x, y: g.size.height)); p.closeSubpath() }
                    .fill(color.opacity(tint.full ? 0.16 : 0.22))
                Path { p in p.move(to: CGPoint(x: 0, y: a.y)); p.addLine(to: CGPoint(x: g.size.width, y: a.y)) }
                    .stroke(Palette.ink.opacity(tint.full ? 0.35 : 0.5), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                Path { $0.smooth(pts) }.stroke(color, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                if hi > lo, iHi != pts.count - 1 { Circle().fill(tint.c(Palette.up)).frame(width: 5, height: 5).position(pts[iHi]) }
                if hi > lo, iLo != pts.count - 1 {
                    if tint.full { Circle().fill(Palette.dn).frame(width: 5, height: 5).position(pts[iLo]) }
                    else { Circle().stroke(Palette.ink, lineWidth: 1.4).frame(width: 5, height: 5).position(pts[iLo]) }
                }
                Circle().fill(color).overlay(Circle().stroke(Palette.ink, lineWidth: 1.5)).frame(width: 7, height: 7).position(z)
            }
        }
    }
}

/// 목표일 전망: 지금에서 출발해 전망 끝(목표일)까지 청록 띠 두 겹(90%·50%)과 중앙선, 주황 점선 목표선, 0부터 시작하는 눈금
struct GoalFan: View {
    let now: Double
    let fan: Snapshot.Fan
    let goal: Double
    let end: String        // 오른쪽 끝 이름 (목표일 / 3년 뒤)
    let tint: Tint
    let hide: Bool         // 금액을 숨겼으면 눈금 금액도 뺀다
    var body: some View {
        GeometryReader { g in
            let axisW: CGFloat = hide ? 0 : 22, w = g.size.width - axisW, h = g.size.height - 12
            let d0 = Date(), dEnd = fan.md.last ?? d0, span = max(86400, dEnd.timeIntervalSince(d0))
            let n = min(fan.md.count, fan.p5.count, fan.p25.count, fan.p50.count, fan.p75.count, fan.p95.count)
            let xs = [axisW] + fan.md.prefix(n).map { axisW + w * CGFloat(min(1, max(0, $0.timeIntervalSince(d0) / span))) }
            let top = Self.top(max(goal * 1.1, (fan.p75.prefix(n).max() ?? goal) * 1.1)), step = top / 3
            let y = { (v: Double) in h * (1 - CGFloat(min(max(v, 0), top) / top)) }
            let line = { (a: [Double]) in Path { p in for (k, v) in ([now] + a.prefix(n)).enumerated() { let q = CGPoint(x: xs[k], y: y(v)); k == 0 ? p.move(to: q) : p.addLine(to: q) } } }
            let band = { (a: [Double], b: [Double]) in Path { p in
                let A = [now] + a.prefix(n), B = [now] + b.prefix(n)
                for k in A.indices { let q = CGPoint(x: xs[k], y: y(A[k])); k == 0 ? p.move(to: q) : p.addLine(to: q) }
                for k in B.indices.reversed() { p.addLine(to: CGPoint(x: xs[k], y: y(B[k]))) }
                p.closeSubpath()
            } }
            let years = Int(span / (365.25 * 86400))
            ZStack(alignment: .topLeading) {
                ForEach(0..<4, id: \.self) { i in
                    Path { p in p.move(to: CGPoint(x: axisW, y: y(step * Double(i)))); p.addLine(to: CGPoint(x: g.size.width, y: y(step * Double(i)))) }.stroke(Palette.ink.opacity(0.14), lineWidth: 1)
                    if !hide { Text(Self.lab(step * Double(i))).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).lineLimit(1).frame(width: axisW - 3, alignment: .trailing).position(x: (axisW - 3) / 2, y: y(step * Double(i))) }
                }
                band(fan.p5, fan.p95).fill(tint.c(Palette.band, tint.full ? 0.14 : 0.16))
                band(fan.p25, fan.p75).fill(tint.c(Palette.band, tint.full ? 0.30 : 0.32))
                line(fan.p50).stroke(tint.c(Palette.bandLine), lineWidth: 2.4)
                Path { p in p.move(to: CGPoint(x: axisW, y: y(goal))); p.addLine(to: CGPoint(x: g.size.width, y: y(goal))) }
                    .stroke(tint.c(Palette.orange), style: StrokeStyle(lineWidth: 1.6, dash: [5, 4]))
                Text(hide ? WL.t("목표") : WL.t("목표 {v}", ["v": Self.lab(goal)])).font(.system(size: 8, weight: .semibold)).foregroundStyle(tint.c(Palette.orange)).fixedSize().offset(x: axisW + 3, y: max(0, y(goal) - 11))
                Circle().fill(Palette.ink).frame(width: 7, height: 7).position(x: axisW, y: y(now))
                Text(WL.t("지금")).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).fixedSize().offset(x: axisW, y: h + 2)
                if years >= 1 {
                    ForEach(1...years, id: \.self) { k in
                        let x = axisW + w * CGFloat(Double(k) * 365.25 * 86400 / span)
                        if x < g.size.width - 30 && x > axisW + 24 { Text(WL.t("{n}년 뒤", ["n": "\(k)"])).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).fixedSize().position(x: x, y: h + 7) }
                    }
                }
                Text(end).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).fixedSize().frame(width: g.size.width, alignment: .trailing).offset(y: h + 2)
            }
        }
    }
    /// 눈금 위 끝: 세 칸이 1·2·2.5·5 의 10배수로 떨어지게
    static func top(_ x: Double) -> Double {
        let raw = max(x, 1) / 3, mag = pow(10, floor(log10(raw)))
        let m = [1, 2, 2.5, 5, 10].first { $0 * mag >= raw } ?? 10
        return 3 * m * mag
    }
    /// 0 · 2.5억 · 5천만
    static func lab(_ v: Double) -> String {
        if v <= 0 { return "0" }
        if v < 1e8 { return Fmt.man(v) }
        return String(format: "%g억", (v / 1e8 * 10).rounded() / 10)
    }
}

/// 내 길: 시작부터 지금까지 실제(민트 선), 시작부터 목표일까지 계획선(보라 점선), 지금부터 목표일까지 보통 범위 띠(25~75%)
struct PathChart: View {
    let d: WSmall
    let now: Double
    let fan: Snapshot.Fan?
    let goalDate: Date?
    let tint: Tint
    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height - 12
            let pts = d.path.filter { $0.count == 2 }
            let fNow = min(1, max(0, pts.last?[0] ?? 0))
            let act: [(f: Double, v: Double)] = pts.dropLast().map { (f: $0[0], v: $0[1]) } + [(f: fNow, v: now)]
            let band = bandPts(fNow)
            let all = act.map { $0.v } + d.need + band.flatMap { [$0.a, $0.b] }
            let lo = (all.min() ?? 0) * 0.97, hi = max((all.max() ?? 1) * 1.03, lo + 1)
            let X = { (f: Double) in w * CGFloat(f) }
            let Y = { (v: Double) in h * (1 - CGFloat((v - lo) / (hi - lo))) }
            ZStack(alignment: .topLeading) {
                if band.count > 1 {
                    Path { p in
                        for (k, b) in band.enumerated() { let q = CGPoint(x: X(b.f), y: Y(b.b)); k == 0 ? p.move(to: q) : p.addLine(to: q) }
                        for b in band.reversed() { p.addLine(to: CGPoint(x: X(b.f), y: Y(b.a))) }
                        p.closeSubpath()
                    }
                    .fill(tint.c(Palette.band, tint.full ? 0.14 : 0.2))
                }
                Path { p in for (k, v) in d.need.enumerated() { let q = CGPoint(x: X(Double(k) / Double(max(1, d.need.count - 1))), y: Y(v)); k == 0 ? p.move(to: q) : p.addLine(to: q) } }
                    .stroke(tint.c(Palette.plan), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                Path { p in for (k, a) in act.enumerated() { let q = CGPoint(x: X(a.f), y: Y(a.v)); k == 0 ? p.move(to: q) : p.addLine(to: q) } }
                    .stroke(tint.c(Palette.teal), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                Circle().fill(tint.c(Palette.teal)).frame(width: 6, height: 6).position(x: X(fNow), y: Y(now))
                if X(fNow) > 26 { Text(WL.t("시작")).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).fixedSize().offset(y: h + 2) }
                Text(WL.t("지금")).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).fixedSize().position(x: min(max(X(fNow), 10), w - 34), y: h + 7)
                Text(WL.t("목표일")).font(.system(size: 8)).foregroundStyle(Palette.ink.opacity(0.65)).fixedSize().frame(width: w, alignment: .trailing).offset(y: h + 2)
            }
        }
    }

    /// 보통 범위 띠: 전망 날짜를 지금(fNow) ~ 목표일(1) 사이로 옮긴다. 목표일 뒤는 버린다
    private func bandPts(_ fNow: Double) -> [(f: Double, a: Double, b: Double)] {
        var out: [(f: Double, a: Double, b: Double)] = [(f: fNow, a: now, b: now)]
        guard let fan else { return out }
        let left = max(86400, (goalDate ?? Date()).timeIntervalSinceNow)
        for k in fan.md.indices where k < fan.p25.count && k < fan.p75.count {
            let x = fNow + (1 - fNow) * fan.md[k].timeIntervalSinceNow / left
            if x > fNow && x <= 1.0001 { out.append((f: min(1, x), a: fan.p25[k], b: fan.p75[k])) }
        }
        return out
    }
}

/// 과녁판: 가운데부터 노랑·빨강·파랑·검정 (양궁 과녁, 흰 고리 없음). off = −1 범위 아래 끝 … 0 예측 중앙값 … 1 위 끝.
/// 투명·틴트에서는 고리를 흰색 진하기로, 점은 가장 밝게
struct TargetBoard: View {
    let off: Double
    let tint: Tint
    private let rings: [(r: CGFloat, fill: UInt32, line: UInt32, o: Double)] = [(36, 0x1b1b1b, 0x555555, 0.1), (27, 0x0096dc, 0x0a6fa8, 0.18), (18, 0xe3262f, 0xa81c22, 0.28), (9, 0xffd83a, 0xc9a400, 0.4)]
    var body: some View {
        ZStack {
            ForEach(rings.indices, id: \.self) { i in
                let r = rings[i]
                Circle().fill(tint.full ? Palette.fixed(r.fill) : Palette.ink.opacity(r.o))
                    .overlay(Circle().stroke(tint.full ? Palette.fixed(r.line) : Palette.ink.opacity(0.45), lineWidth: 0.8))
                    .frame(width: r.r * 2, height: r.r * 2)
            }
            Circle().fill(tint.full ? Palette.fixed(0x15181f) : Palette.ink).overlay(Circle().stroke(Color.white, lineWidth: tint.full ? 2 : 0))
                .frame(width: 9, height: 9).offset(y: -CGFloat(off) * 31.5)
        }
        .frame(width: 76, height: 76)
        .widgetAccentable()
        .accessibilityHidden(true)
    }
}
