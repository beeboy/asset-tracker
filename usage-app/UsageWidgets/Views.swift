import SwiftUI

// 위젯 그림 (시안 /mnt/project-files/designs/claude-usage-widget src-home.html · src-lock.html).
// 앱 화면의 미리보기도 같은 뷰를 쓴다. 크기는 시안의 170pt 작은 위젯 기준 값을 폭에 맞춰 늘이고 줄인다.

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB, red: Double(hex >> 16 & 0xff) / 255, green: Double(hex >> 8 & 0xff) / 255, blue: Double(hex & 0xff) / 255, opacity: opacity)
    }
    static let cuHot = Color(hex: 0xff8a7a)
}

/// 홈 화면 작은 위젯: 위 말풍선(크기 고정 칸), 왼쪽 10×5 칸 = 5시간(한 칸 2%), 오른쪽 10층 탑 = 주간(한 층 10%)
struct BlockView: View {
    let u: Usage
    let c: UChar
    let now: Date

    var body: some View {
        GeometryReader { g in
            let s = g.size.width / 170
            content(s).padding(12 * s).frame(width: g.size.width, height: g.size.height, alignment: .topLeading)
        }
    }

    private func content(_ s: CGFloat) -> some View {
        let cell = 7 * s, gap = 1.6 * s, gridW = cell * 10 + gap * 9
        let ac = Color(hex: c.accent)
        return VStack(alignment: .leading, spacing: 0) {
            // 말풍선 칸: 높이 고정, 대사 길이만 바뀐다
            bubble(s, width: gridW + 36 * s).frame(height: 42 * s, alignment: .top)
            HStack(alignment: .bottom, spacing: 0) {
                grid(cell: cell, gap: gap, ac: ac).frame(width: gridW, alignment: .bottomLeading)
                sprite(s).zIndex(3)
                Spacer(minLength: 0)
                tower(s, cell: cell, gap: gap, ac: ac)
            }
            .frame(height: 62 * s, alignment: .bottom)
            .padding(.top, 9 * s)
            Spacer(minLength: 0)
            footer(s)
        }
    }

    private func bubble(_ s: CGFloat, width: CGFloat) -> some View {
        Text(c.line(u))
            .font(.system(size: 12 * s, weight: .medium))
            .lineSpacing(0)
            .lineLimit(2)
            .minimumScaleFactor(0.8)
            .foregroundStyle(Color(hex: 0x1b1d27))
            .padding(.vertical, 5 * s).padding(.horizontal, 8 * s)
            .frame(width: width, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 9 * s).fill(.white))
            .overlay(alignment: .bottomTrailing) {
                Tail().fill(.white).frame(width: 10 * s, height: 5 * s).offset(x: -30 * s, y: 5 * s)
            }
    }

    private func grid(cell: CGFloat, gap: CGFloat, ac: Color) -> some View {
        let n = Int((Double(u.p5) / 2).rounded()), locked = u.weekFull, hot = u.p5 >= 90
        return VStack(spacing: gap) {
            ForEach(0..<5, id: \.self) { r in
                HStack(spacing: gap) {
                    ForEach(0..<10, id: \.self) { col in
                        let i = r * 10 + col
                        RoundedRectangle(cornerRadius: 1.2 * cell / 7)
                            .fill(locked ? Color.cuHot.opacity(0.35) : i < n ? (hot ? .cuHot : ac) : .white.opacity(0.13))
                            .frame(width: cell, height: cell)
                    }
                }
            }
        }
    }

    @ViewBuilder private func sprite(_ s: CGFloat) -> some View {
        if let img = c.sprite {
            Image(uiImage: img).interpolation(.none).resizable().scaledToFit()
                .frame(height: 62 * s)
                .padding(.horizontal, -4 * s)
                .offset(y: 10 * s)
        }
    }

    private func tower(_ s: CGFloat, cell: CGFloat, gap: CGFloat, ac: Color) -> some View {
        let fl = u.pw / 10, h = (cell * 5 + gap * 4 + 12 * s) / 10, hot = u.pw >= 90
        return VStack(spacing: 2 * s) {
            ForEach((0..<10).reversed(), id: \.self) { i in
                let shape = RoundedRectangle(cornerRadius: 1.5 * s)
                Group {
                    if i < fl { shape.fill(hot ? Color.cuHot : ac) }
                    else if i == fl && !u.weekFull { shape.fill(ac.opacity(0.45)).overlay(shape.stroke(ac, lineWidth: 1 * s)) }
                    else { shape.fill(.white.opacity(0.13)) }
                }
                .frame(width: 16 * s, height: h)
            }
        }
    }

    private func footer(_ s: CGFloat) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 2 * s) {
            Text("\(u.p5)").font(.system(size: 20 * s)).foregroundStyle(.white)
            Text("% · 주간 \(u.pw)%").font(.system(size: 10 * s)).foregroundStyle(.white.opacity(0.75))
            Spacer(minLength: 2 * s)
            Text("↻ " + (u.weekFull ? CUTime.weekday(u.week.reset) : CUTime.left(u.five.reset, now)))
                .font(.system(size: 9.5 * s)).foregroundStyle(.white.opacity(0.8))
        }
        .lineLimit(1)
    }
}

/// 말풍선 꼬리 (아래로)
struct Tail: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.midX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}

/// 위젯 바탕: 캐릭터 색 그라데이션 (150도)
struct CharBackground: View {
    let c: UChar
    var body: some View {
        LinearGradient(colors: [Color(hex: c.g0), Color(hex: c.g1)], startPoint: UnitPoint(x: 0.25, y: 0.07), endPoint: UnitPoint(x: 0.75, y: 0.93))
    }
}

/// 잠금 화면 작은 원형: 바깥 링 5시간, 안쪽 링 주간 (둘 다 닫힌 원, 간격 유지) + 가운데 캐릭터 얼굴
struct RingView: View {
    let u: Usage
    let c: UChar

    var body: some View {
        GeometryReader { g in
            let side = min(g.size.width, g.size.height), s = side / 72
            ZStack {
                ring(r: 30 * s, w: 5 * s, p: u.weekFull ? 100 : Double(u.p5))
                ring(r: 20 * s, w: 5 * s, p: Double(u.pw))
                face(s)
            }
            .frame(width: side, height: side)
            .frame(width: g.size.width, height: g.size.height)
        }
    }

    private func ring(r: CGFloat, w: CGFloat, p: Double) -> some View {
        ZStack {
            Circle().stroke(.white.opacity(0.25), lineWidth: w)
            Circle().trim(from: 0, to: min(1, max(0, p / 100)))
                .stroke(.white, style: StrokeStyle(lineWidth: w, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: r * 2, height: r * 2)
    }

    @ViewBuilder private func face(_ s: CGFloat) -> some View {
        if let img = c.face {
            Image(uiImage: img).interpolation(c.id == "me" ? .medium : .none).resizable()
                .aspectRatio(contentMode: c.id == "me" ? .fill : .fit)
                .frame(width: 34 * s, height: 34 * s, alignment: .top)
                .offset(y: 2 * s)
                .frame(width: 26 * s, height: 26 * s)
                .clipShape(Circle())
        }
    }
}
