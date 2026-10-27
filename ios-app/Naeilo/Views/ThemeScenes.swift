import SwiftUI
import UIKit

// MARK: 새 테마 셋 (2026-10-24): 도리와 문어 · 피노키오와 새 · 철수와 영희(비튼 무궁화)
// 홈 장면(300×170 판) + 종목·흐름 띠·바탕 + 분석 막대 + 작은 등장인물. 기준 시안: 새 홈 3장, 종목·흐름·분석 다크 18장
// 공통: 등장인물 자리 = 목표 대비 지금, 25·50·75% 눈금, 끝점 = 목표 금액.
// 장면 그림은 값이 바뀔 때만 다시 그리고, 움직임은 작은 조각만 정해진 박자에 짧게 (화면 밖·동작 줄이기면 멈춘다)

/// 오른 날·떨어진 날 (오늘 등락이 없으면 오른 날처럼 차분하게)
enum ThemeMood {
    case up, down
    init(_ v: Double?) { self = (v ?? 0) < 0 ? .down : .up }
}

/// seconds 마다 n 을 하나 올린다 (첫 번은 delay + seconds 뒤). 화면에 있을 때만 돌고 동작 줄이기면 멈춘다
struct Every<V: View>: View {
    var seconds: Double
    var delay: Double = 0
    @ViewBuilder var content: (Int) -> V
    @State private var n = 0
    @Environment(\.accessibilityReduceMotion) private var still
    var body: some View {
        content(n).task(id: still) {
            guard !still else { return }
            try? await Task.sleep(for: .seconds(delay))
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(seconds))
                if !Task.isCancelled { n &+= 1 }
            }
        }
    }
}

/// 눈 깜빡임: period 마다 한 번 세로로 접혔다 편다 (가운데 기준)
private struct Blink: ViewModifier {
    let period: Double, delay: Double
    func body(content: Content) -> some View {
        Every(seconds: period, delay: delay) { n in
            content.keyframeAnimator(initialValue: 1.0, trigger: n) { c, v in
                c.scaleEffect(x: 1, y: v)
            } keyframes: { _ in
                KeyframeTrack {
                    LinearKeyframe(0.08, duration: 0.08)
                    LinearKeyframe(0.08, duration: 0.07)
                    LinearKeyframe(1.0, duration: 0.1)
                }
            }
        }
    }
}

extension View {
    fileprivate func blink(_ period: Double, delay: Double = 0) -> some View { modifier(Blink(period: period, delay: delay)) }
}

/// 장(한국 평일 9:00~15:30, 미국 평일 9:30~16:00 뉴욕) 중 하나라도 열렸는지. 휴장일은 따지지 않는다
enum MarketClock {
    static func open(_ d: Date = .now) -> Bool {
        func inside(_ tz: String, _ a: Int, _ b: Int) -> Bool {
            var c = Calendar(identifier: .gregorian)
            c.timeZone = TimeZone(identifier: tz) ?? .current
            guard (2...6).contains(c.component(.weekday, from: d)) else { return false }
            let m = c.component(.hour, from: d) * 60 + c.component(.minute, from: d)
            return m >= a && m < b
        }
        return inside("Asia/Seoul", 540, 930) || inside("America/New_York", 570, 960)
    }
}

/// 상단 박스 밝기 모드: 기기 따라가기 · 늘 밝게 · 늘 어둡게. 상단 박스에만 덮어씌우고 앱 전체 모드는 그대로
enum BoxScheme: String, CaseIterable {
    case system, light, dark
    static let key = "homeBoxScheme"
    var name: String {
        switch self {
        case .system: return String(localized: "자동")
        case .light: return String(localized: "밝게")
        case .dark: return String(localized: "어둡게")
        }
    }
}

private struct BoxSchemeMod: ViewModifier {
    @AppStorage(BoxScheme.key) private var raw = BoxScheme.system.rawValue
    @Environment(\.colorScheme) private var cs
    func body(content: Content) -> some View {
        let pick = BoxScheme(rawValue: raw) ?? .system
        content.environment(\.colorScheme, pick == .light ? .light : pick == .dark ? .dark : cs)
    }
}

extension View {
    /// 상단 박스(홈·종목·흐름·분석)에 고른 밝기 모드를 덮어씌운다
    func boxScheme() -> some View { modifier(BoxSchemeMod()) }
}

extension Color {
    /// 지금 환경(상단 박스 모드로 덮어쓴 colorScheme)대로 색을 굳힌다. Canvas 안에 넘기는 한 쌍 색이 기기 모드로 풀리지 않게
    func fixed(_ env: EnvironmentValues) -> Color { Color(resolve(in: env)) }
}

/// 상단 박스 색 한 쌍 (라이트, 다크). 모양·자리·움직임은 한 벌을 같이 쓰고 색만 고른다
func bx(_ light: UInt32, _ dark: UInt32) -> Color { Color(hex: light, dark: dark) }
func bx(_ light: UInt32, _ la: Double, _ dark: UInt32, _ da: Double) -> Color {
    func ui(_ h: UInt32, _ a: Double) -> UIColor {
        UIColor(red: CGFloat((h >> 16) & 0xFF) / 255, green: CGFloat((h >> 8) & 0xFF) / 255, blue: CGFloat(h & 0xFF) / 255, alpha: a)
    }
    return Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? ui(dark, da) : ui(light, la) })
}

/// 상단 박스 글자·띠 색 (라이트 시안 28장 · 다크 시안)
enum BoxInk {
    static let ink = bx(0x16181D, 0xFFFFFF)
    static let sub = bx(0x5F6670, 0xC9D0D6)
    static let dim = bx(0x5F6670, 0x8A93A6)
    static let up = bx(0xC0392B, 0xFF8A80), down = bx(0x1B5E96, 0x8CC4F2)
    static let gold = bx(0x9A6A00, 0xF2C14E), good = bx(0x1F8577, 0x3FB3A3)
    static let upF = bx(0xF08A7E, 0xFF8A80), downF = bx(0x6FB0E6, 0x8CC4F2), flatF = bx(0xC3CAD3, 0x4A5672)
    /// 진행 막대(무늬 없는) 박스 바탕 · 라이트 테두리
    static let card = bx(0xFFFFFF, 0x1E2A36), cardRim = bx(0xE1E5EA, 1, 0xFFFFFF, 0)
}

private func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }
private func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
private func hex(_ v: UInt32, _ o: Double = 1) -> GraphicsContext.Shading { .color(Color(hex: v, opacity: o)) }
/// 그림 안 색 한 쌍: dk 면 다크 값
private func hex(_ dk: Bool, _ l: UInt32, _ d: UInt32, _ o: Double = 1) -> GraphicsContext.Shading { .color(Color(hex: dk ? d : l, opacity: o)) }

// MARK: 그림 조각 (각자 제 원점 기준 좌표, 시안 SVG 그대로)
enum ThemeArt {
    /// 도리 (오른쪽을 본다). 꼬리·눈은 따로 움직일 때 뺀다
    static func dory(_ c: GraphicsContext, dk: Bool = true, tail: Bool = true, eye: Bool = true) {
        if tail { doryTail(c, dk: dk) }
        c.fill(Path(ellipseIn: CGRect(x: -15, y: -10, width: 30, height: 20)), with: hex(0x2F6FE0))
        var st = Path(); st.move(to: pt(-9, -7)); st.addQuadCurve(to: pt(-6, 4), control: pt(-2, -2)); st.addQuadCurve(to: pt(6, -6), control: pt(2, 2))
        c.stroke(st, with: hex(0x141B2B), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
        var fin = Path(); fin.move(to: pt(-3, -9)); fin.addQuadCurve(to: pt(9, -9), control: pt(2, -14))
        c.fill(fin, with: hex(0x2F6FE0)); c.stroke(fin, with: hex(0x141B2B), lineWidth: 1.2)
        var lf = Path(); lf.move(to: pt(2, 6)); lf.addQuadCurve(to: pt(2, 12), control: pt(6, 10))
        c.fill(lf, with: hex(dk, 0xFFC21F, 0xFFD23F))
        if eye { doryEye(c, at: pt(9, -2)) }
        var mo = Path(); mo.move(to: pt(12, 4)); mo.addQuadCurve(to: pt(15, 3), control: pt(14, 5))
        c.stroke(mo, with: hex(0x141B2B), style: StrokeStyle(lineWidth: 1, lineCap: .round))
    }
    static func doryTail(_ c: GraphicsContext, dk: Bool = true) {
        var t = Path(); t.move(to: pt(-14, 0)); t.addLine(to: pt(-22, -7)); t.addLine(to: pt(-20, 0)); t.addLine(to: pt(-22, 7)); t.closeSubpath()
        c.fill(t, with: hex(dk, 0xFFC21F, 0xFFD23F))
    }
    static func doryEye(_ c: GraphicsContext, at o: CGPoint) {
        c.fill(circle(o.x, o.y, 4), with: hex(0xFFFFFF))
        c.fill(circle(o.x + 1, o.y, 2.2), with: hex(0x1D2330))
        c.fill(circle(o.x + 1.8, o.y - 0.8, 0.7), with: hex(0xFFFFFF))
    }

    /// 수족관 문어 몸 (다리·눈 빼고). down = 빨개진 문어, 아니면 벽 색 점선으로 숨은 문어
    static func octoBody(_ c: GraphicsContext, down: Bool, dk: Bool = true) {
        let body = Path(ellipseIn: CGRect(x: -13, y: -16, width: 26, height: 24))
        c.fill(body, with: hex(down ? 0xFF5A4F : 0x9FD6EE))
        c.stroke(body, with: hex(down ? (dk ? 0xFF8A80 : 0xFF7A6E) : 0xBFE9FB), style: StrokeStyle(lineWidth: 1, dash: down ? [] : [2, 2]))
    }
    static func octoArms(_ c: GraphicsContext, down: Bool, dk: Bool = true) {
        var a = Path()
        a.move(to: pt(-11, 2)); a.addQuadCurve(to: pt(-20, 14), control: pt(-15, 12))
        a.move(to: pt(-6, 4)); a.addQuadCurve(to: pt(-11, 18), control: pt(-8, 14))
        a.move(to: pt(0, 5)); a.addQuadCurve(to: pt(1, 19), control: pt(0, 15))
        a.move(to: pt(6, 4)); a.addQuadCurve(to: pt(13, 16), control: pt(9, 14))
        a.move(to: pt(11, 2)); a.addQuadCurve(to: pt(20, 11), control: pt(16, 10))
        c.stroke(a, with: hex(down ? (dk ? 0xFF8A80 : 0xFF7A6E) : 0xBFE9FB), style: StrokeStyle(lineWidth: 3.5, lineCap: .round, dash: down ? [] : [2, 2]))
    }
    /// 문어 눈: 평소 작은 점 눈, 떨어진 날 놀란 흰 눈
    static func octoEyes(_ c: GraphicsContext, down: Bool) {
        if down {
            for x in [-5.0, 5.0] as [CGFloat] { c.fill(circle(x, -4, 4), with: hex(0xFFFFFF)); c.fill(circle(x, -4, 1.8), with: hex(0x1D2330)) }
        } else {
            for x in [-4.5, 4.5] as [CGFloat] { c.fill(circle(x, -3, 1.6), with: hex(0x1D2330)) }
        }
    }
    static func octoMouth(_ c: GraphicsContext, down: Bool) {
        if down {
            c.fill(Path(ellipseIn: CGRect(x: -2.5, y: 1, width: 5, height: 6)), with: hex(0x7A1A14))
        } else {
            var m = Path(); m.move(to: pt(-2, 3)); m.addQuadCurve(to: pt(2, 3), control: pt(0, 4.5))
            c.stroke(m, with: hex(0x1D2330), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
    }
    /// 시드니 오페라하우스 (왼쪽 위 원점, 폭 52)
    static func opera(_ c: GraphicsContext, dk: Bool = true) {
        c.fill(Path(CGRect(x: -4, y: 18, width: 52, height: 5)), with: hex(dk, 0x8A97B0, 0x6D7A94))
        for (a, b, col) in [((2.0, 18.0, 8.0, 2.0, 16.0, 0.0), (14.0, 10.0, 16.0, 18.0), 0xF4F1EA),
                            ((14, 18, 22, 0, 32, -3), (29, 9, 31, 18), 0xFBF9F4),
                            ((29, 18, 36, 6, 44, 5), (41, 12, 43, 18), 0xE9E4D8)] as [((CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat), (CGFloat, CGFloat, CGFloat, CGFloat), UInt32)] {
            var p = Path(); p.move(to: pt(a.0, a.1)); p.addQuadCurve(to: pt(a.4, a.5), control: pt(a.2, a.3)); p.addQuadCurve(to: pt(b.2, b.3), control: pt(b.0, b.1)); p.closeSubpath()
            c.fill(p, with: hex(dk ? col : col == 0xE9E4D8 ? 0xF3EFE6 : 0xFFFFFF))
            if !dk { c.stroke(p, with: hex(0x9AA6BD), lineWidth: 0.8) }   // 라이트: 옅은 외곽선
        }
    }

    /// 새 (발 = 원점, 왼쪽을 본다). laugh = 깔깔 웃는 얼굴. wing = 날개 각도 (음수면 위로 편다)
    static func bird(_ c: GraphicsContext, laugh: Bool, wing: Double = 0) {
        var legs = Path()
        legs.move(to: pt(-2, 0)); legs.addLine(to: pt(-2, -2.4)); legs.move(to: pt(1.6, 0)); legs.addLine(to: pt(1.6, -2.4))
        legs.move(to: pt(-3.4, 0)); legs.addLine(to: pt(-0.8, 0)); legs.move(to: pt(0.4, 0)); legs.addLine(to: pt(3, 0))
        c.stroke(legs, with: hex(0xFF9F2E), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        var tail = Path(); tail.move(to: pt(6, -7.5)); tail.addLine(to: pt(12, -11)); tail.addLine(to: pt(11, -6.8)); tail.addLine(to: pt(13, -4.4)); tail.addLine(to: pt(6.5, -4.5)); tail.closeSubpath()
        c.fill(tail, with: hex(0x2F6FB8))
        c.fill(Path(ellipseIn: CGRect(x: -7.8, y: -15.8, width: 15.6, height: 14.8)), with: hex(0x5FB2F2))
        c.fill(Path(ellipseIn: CGRect(x: -6.4, y: -10.2, width: 9.6, height: 8.4)), with: hex(0xEAF6FF))
        var w = Path(); w.move(to: pt(1.4, -10.4)); w.addQuadCurve(to: pt(7.6, -5.6), control: pt(7, -11.2)); w.addQuadCurve(to: pt(1.4, -7.4), control: pt(3.6, -5)); w.closeSubpath()
        var wc = c
        wc.translateBy(x: 1.4, y: -9); wc.rotate(by: .degrees(wing)); wc.translateBy(x: -1.4, y: 9)
        wc.fill(w, with: hex(0x3E8FD6))
        var crest = Path(); crest.move(to: pt(-2, -15.4)); crest.addQuadCurve(to: pt(1, -17.8), control: pt(-1, -18.4)); crest.addQuadCurve(to: pt(0, -15.4), control: pt(-0.6, -17)); crest.closeSubpath()
        c.fill(crest, with: hex(0x3E8FD6))
        c.fill(circle(-4.6, -7, 1.6), with: hex(0xFF8F9A, 0.6))
        if laugh {
            var e = Path(); e.move(to: pt(-5, -10.4)); e.addQuadCurve(to: pt(-1.8, -10.4), control: pt(-3.4, -12.8))
            c.stroke(e, with: hex(0x1D2330), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
            var b1 = Path(); b1.move(to: pt(-7.4, -10.4)); b1.addLine(to: pt(-11.4, -10.8)); b1.addLine(to: pt(-7.4, -8.8)); b1.closeSubpath()
            var b2 = Path(); b2.move(to: pt(-7.4, -8.2)); b2.addLine(to: pt(-10.6, -6.6)); b2.addLine(to: pt(-7.4, -6.8)); b2.closeSubpath()
            c.fill(b1, with: hex(0xFF9F2E)); c.fill(b2, with: hex(0xE8851E))
        } else {
            c.fill(circle(-3.2, -10.6, 1.5), with: hex(0x1D2330)); c.fill(circle(-3.7, -11.1, 0.5), with: hex(0xFFFFFF))
            var b = Path(); b.move(to: pt(-7.4, -9.6)); b.addLine(to: pt(-11, -8.3)); b.addLine(to: pt(-7.4, -7)); b.closeSubpath()
            c.fill(b, with: hex(0xFF9F2E))
        }
    }
    static func leaf(_ c: GraphicsContext, light: Bool = false, dk: Bool = true) {
        var p = Path(); p.move(to: .zero)
        if light { p.addQuadCurve(to: pt(4, -6), control: pt(-1, -5)); p.addQuadCurve(to: .zero, control: pt(4, -2)) }
        else { p.addQuadCurve(to: pt(3, -8), control: pt(-2, -6)); p.addQuadCurve(to: .zero, control: pt(4, -3)) }
        p.closeSubpath()
        c.fill(p, with: hex(light ? 0x74C06C : dk ? 0x5FAE5A : 0x4E9A49))
    }

    /// 옛 동화책 피노키오 (발끝 사이 = (0, 152) 기준 좌표). up = 새에게 손을 뻗는다, 아니면 배를 잡고 깔깔
    static func pino(_ c: GraphicsContext, up: Bool, dk: Bool = true) {
        func line(_ ps: [(CGFloat, CGFloat)], _ col: UInt32, _ w: CGFloat) {
            var p = Path(); p.move(to: pt(ps[0].0, ps[0].1)); for q in ps.dropFirst() { p.addLine(to: pt(q.0, q.1)) }
            c.stroke(p, with: hex(col), style: StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round))
        }
        // 뒷팔
        line([(-3.5, 120), (-6, 126), (-6.5, 131.5)], 0xB88450, 3)
        c.fill(circle(-6.6, 132.4, 2), with: hex(0xE8C193))
        // 다리
        c.fill(Path(roundedRect: CGRect(x: -5.2, y: 138, width: 3.4, height: 12), cornerRadius: 1.5), with: hex(0xD29A60))
        c.fill(Path(roundedRect: CGRect(x: 1.4, y: 138, width: 3.4, height: 12), cornerRadius: 1.5), with: hex(0xDCA66B))
        c.fill(circle(-3.5, 144, 1.5), with: hex(0xA8743F)); c.fill(circle(3.1, 144, 1.5), with: hex(0xA8743F))
        var s1 = Path(); s1.move(to: pt(-8, 152.6)); s1.addQuadCurve(to: pt(-3.4, 148.8), control: pt(-8, 148.6)); s1.addQuadCurve(to: pt(1.4, 152.6), control: pt(1, 149)); s1.closeSubpath()
        var s2 = Path(); s2.move(to: pt(-1.2, 152.6)); s2.addQuadCurve(to: pt(3.6, 148.8), control: pt(-1, 148.6)); s2.addQuadCurve(to: pt(9.4, 152.6), control: pt(8.6, 149.2)); s2.closeSubpath()
        c.fill(s1, with: hex(0x5A3A20)); c.fill(s2, with: hex(0x6B4728))
        // 반바지
        var sh = Path()
        for (i, q) in [(-8.0, 132.5), (8, 132.5), (8.6, 139.5), (1, 139.5), (0, 137), (-1, 139.5), (-8.6, 139.5)].enumerated() {
            i == 0 ? sh.move(to: pt(q.0, q.1)) : sh.addLine(to: pt(q.0, q.1))
        }
        sh.closeSubpath(); c.fill(sh, with: hex(0xC8453A))
        // 꽃무늬 웃옷
        var top = Path(); top.move(to: pt(-6.5, 117)); top.addQuadCurve(to: pt(-9, 134), control: pt(-9.5, 125)); top.addLine(to: pt(9, 134)); top.addQuadCurve(to: pt(6.5, 117), control: pt(9.5, 125)); top.closeSubpath()
        c.fill(top, with: hex(0xE9B949))
        for (x, y) in [(-4.0, 123.0), (1.5, 127), (-5, 130.5), (5.5, 121.5)] as [(CGFloat, CGFloat)] {
            c.fill(circle(x, y, 1.1), with: hex(0xD2523F)); c.fill(circle(x, y, 0.4), with: hex(0xFFF4D8))
        }
        c.fill(Path(roundedRect: CGRect(x: -9, y: 131.5, width: 18, height: 2.4), cornerRadius: 1), with: hex(0x7A4A24))
        // 주름 깃
        for (x, y) in [(-7.0, 117.4), (-3.5, 118.4), (0, 118.8), (3.5, 118.4), (7, 117.4)] as [(CGFloat, CGFloat)] {
            c.fill(circle(x, y, 2.6), with: hex(dk, 0xFFFFFF, 0xFBF4E4))
            if !dk { c.stroke(circle(x, y, 2.6), with: hex(0xE2D3B0), lineWidth: 0.5) }
        }
        // 앞팔
        if up {
            line([(2.5, 120.5), (8.5, 121.5), (14.5, 119.5)], 0xDCA66B, 3.2)
            c.fill(circle(8.5, 121.5, 1.4), with: hex(0xA8743F)); c.fill(circle(2.5, 120.5, 3.6), with: hex(0xE9B949)); c.fill(circle(15.8, 119.2, 2.3), with: hex(0xF2CFA0))
        } else {
            line([(2.5, 120.5), (6.5, 125.5), (1.5, 129)], 0xDCA66B, 3.2)
            c.fill(circle(6.5, 125.5, 1.4), with: hex(0xA8743F)); c.fill(circle(2.5, 120.5, 3.6), with: hex(0xE9B949)); c.fill(circle(0.8, 129.4, 2.3), with: hex(0xF2CFA0))
        }
        // 머리
        var hair = Path(); hair.move(to: pt(-12.5, 99)); hair.addQuadCurve(to: pt(-13.5, 109), control: pt(-16.5, 104)); hair.addQuadCurve(to: pt(-10.5, 105), control: pt(-11.5, 108))
        hair.addQuadCurve(to: pt(-8.9, 110), control: pt(-11.5, 108)); hair.addQuadCurve(to: pt(-7.5, 104), control: pt(-8.3, 106)); hair.closeSubpath()
        c.fill(hair, with: hex(0x4A2E18))
        c.fill(circle(0, 103, 12), with: hex(0xF2CFA0))
        if !dk { c.stroke(circle(0, 103, 12), with: hex(0xD9AE7C), lineWidth: 0.6) }
        var jaw = Path(); jaw.move(to: pt(-12, 104)); jaw.addQuadCurve(to: pt(-4, 114.6), control: pt(-11, 112)); jaw.addQuadCurve(to: pt(7, 112.6), control: pt(2, 116))
        c.stroke(jaw, with: hex(0xE2B17C), lineWidth: 1)
        c.fill(Path(ellipseIn: CGRect(x: -7.6, y: 100.9, width: 5.2, height: 7.2)), with: hex(0xE6B986))
        var ear = Path(); ear.move(to: pt(-5.6, 103)); ear.addQuadCurve(to: pt(-5.6, 106), control: pt(-4.2, 104.5))
        c.stroke(ear, with: hex(0xC99560), lineWidth: 0.7)
        c.fill(circle(4.5, 109, 3.2), with: hex(0xFF8F7A, 0.5))
        // 빳빳한 종이 고깔
        for (ps, col) in [([(-13.4, 98.6), (10.6, 90.4), (-23, 79.5)], dk ? 0xF1E6C8 : 0xFFFFFF), ([(-13.4, 98.6), (-1.4, 94.5), (-23, 79.5)], dk ? 0xD9CAA3 : 0xE6D9B6),
                          ([(-13.8, 98.4), (10.8, 90), (11.6, 92.6), (-13.2, 101)], 0xC9433A)] as [([(CGFloat, CGFloat)], UInt32)] {
            var p = Path(); p.move(to: pt(ps[0].0, ps[0].1)); for q in ps.dropFirst() { p.addLine(to: pt(q.0, q.1)) }; p.closeSubpath()
            c.fill(p, with: hex(col))
            if !dk && col == 0xFFFFFF { c.stroke(p, with: hex(0xD9CAA3), lineWidth: 0.6) }
        }
        // 얼굴
        var brow = Path()
        if up {
            brow.move(to: pt(2.4, 96.2)); brow.addQuadCurve(to: pt(7.8, 96.6), control: pt(5, 94.8))
            c.stroke(brow, with: hex(0x5A3A20), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            c.fill(Path(ellipseIn: CGRect(x: 2.6, y: 97.4, width: 5.2, height: 6.4)), with: hex(0xFFFFFF))
            c.fill(circle(6.4, 100.8, 1.8), with: hex(0x1D2330)); c.fill(circle(7, 100, 0.6), with: hex(0xFFFFFF))
            var m = Path(); m.move(to: pt(5, 110.6)); m.addQuadCurve(to: pt(10, 110.9), control: pt(7.6, 109.7))
            c.stroke(m, with: hex(0x7A3A24), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
        } else {
            brow.move(to: pt(2.4, 95.4)); brow.addQuadCurve(to: pt(7.8, 95.4), control: pt(5, 93.6))
            c.stroke(brow, with: hex(0x5A3A20), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            var e = Path(); e.move(to: pt(2.6, 101)); e.addQuadCurve(to: pt(7.8, 101), control: pt(5.2, 97.6))
            c.stroke(e, with: hex(0x1D2330), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            var m = Path(); m.move(to: pt(4, 108.6)); m.addQuadCurve(to: pt(11.6, 108.4), control: pt(7.5, 115)); m.closeSubpath()
            c.fill(m, with: hex(0x7A1F16))
            var tg = Path(); tg.move(to: pt(6, 111.6)); tg.addQuadCurve(to: pt(10.2, 111.6), control: pt(8.2, 113.4))
            c.fill(tg, with: hex(0xFF8F7A))
        }
    }

    /// EXIT 출구 문 (작은 것: 18×22)
    static func miniExit(_ c: GraphicsContext) {
        let door = Path(roundedRect: CGRect(x: 1.5, y: 6, width: 15, height: 16), cornerRadius: 1)
        c.fill(door, with: hex(0x1D6B45)); c.stroke(door, with: hex(0x0F3D27), lineWidth: 1)
        c.fill(Path(CGRect(x: 3.5, y: 8, width: 11, height: 14)), with: hex(0xC8FFD9))
        c.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 18, height: 5.5), cornerRadius: 1), with: hex(0x0F3D27))
        c.draw(Text("EXIT").font(.system(size: 4, weight: .heavy)).foregroundColor(Color(hex: 0x7DFFAE)), at: pt(9, 2.9))
    }
}

// MARK: 도트 인물: 영희(선배 얼굴 + 영희 옷) · 기훈(내 캐릭터 얼굴 + 녹색 체육복)
enum MugArt {
    enum Eyes { case open, closed, peek }
    /// 영희 30×35 도트 (뜬 눈 · 감은 눈 · 한쪽 눈)
    private static let yh: [Eyes: String] = [
        .open: "iVBORw0KGgoAAAANSUhEUgAAAB4AAAAjCAYAAACD1LrRAAABpklEQVR42mNgGCDASIpiLTm9//jkrz26xEh1i7Xk9P5vm6KDU/7GjUcMRVM+Ee0ARmpYCrMYBoqmfCJoORM1LGVgYGDQ0JAjKY6ZqGEpOujL4SOYHpiomVJJ8TULqYaLKYThlHt6/RYDA8Mj6lqMz0JyANNAFSAkWxySUUWU2PDx8ZoZbUSJ0czHl3efZri8+zTtKgnkguDMlia8ap9ev8XgX7qEqPKapEoCxt7YHYMiB7OMlBqKkVjLrm6TZGDYdoOBwUuD4eXDp3A14lc/o4g5ZTIR5QAWfJZe3SaJVU5cXhrBuXoDRW7f9H9QB+j9x2c52YkL2dfkAOLL6m03UGhxJN/emnKUgd9XgfrZaVvdl4EvQOhi8fd/mYOnyKRL81ZLTu//1ZyPJBl4695bBgYGBobAbUoMNMlONAtqcnzLwMDAoKYkzMDAwMCw3use3gbf4PKxlpze//Ve9zDijVg2DODzNSMp/SRYtWjiU4dXjJj+FAuxHTB0h1x7dIkRmxhN2tX/5K2IEqNab1FLTu+/oOAfFLH371kYsIlRpSFAy6AeMAAAiD63+nMEWf4AAAAASUVORK5CYII=",
        .closed: "iVBORw0KGgoAAAANSUhEUgAAAB4AAAAjCAYAAACD1LrRAAABoElEQVR42mNgGCDASIpiLTm9//jkrz26xEh1i7Xk9P5vm6KDU/7GjUcMRVM+Ee0ARmpYCrMYBoqmfCJoORM1LGVgYGDQ0JAjKY6ZqGEpOujL4SOYHpiomVJJ8TULqYaLKYThlHt6/RYDA8Mj6lqMz0JyANNAFSBDw2ITnzoGE586gmJUT1xntjQRJUbToL68+zTD5d2naVdJIBcEhHz39PotBv/SJUSV1yRVEjD2xu4YFDmYZaTUUIzEWnZ1myQDw7YbDAxeGgwvHz6FqxG/+hlFzCmTiSgHsOCz9Oo2Saxy4vLSCM7VGyhy+6b/gzpA7z8+y8lOXMi+JgcQn5223UChxZF8e2vKUQZ+XwXqZ6dtdV9GSJH5/V/m8KskCObjqzkfSTLw1r23DAwMDAyB25QYaJKdaBbU5PiWgYGBQU1JmIGBgYFhvdc9vA2+weVjLTm9/+u97mHEG7FsGMDna0ZS+kmwahG5xYFNjJj+FAuxHTB0h1x7dIkRmxhNmj7/5K2IEqNab1FLTu+/oOAfFLH371kYsIlRpSFAy6AeMAAAaDe5+Jl4bSgAAAAASUVORK5CYII=",
        .peek: "iVBORw0KGgoAAAANSUhEUgAAAB4AAAAjCAYAAACD1LrRAAABrElEQVR42mNgGCDASIpiLTm9//jkrz26xEh1i7Xk9P5vm6KDU/7GjUcMRVM+Ee0ARmpYCrMYBoqmfCJoORM1LGVgYGDQ0JAjKY6ZqGEpOujL4SOYHpiomVJJ8TULqYaLKYThlHt6/RYDA8Mj6lqMz0JyANNAFSBUsTgko4q2Fpv41DGY+NRRxcckJa4zW5qwiq+Z0Ua/oL68+zTD5d2naVdJIBcEuHyMnJ38S5cQVV6TVEnA2Bu7Y1DkYJaRUkMxEmvZ1W2SDAzbbjAweGkwvHz4FK5G/OpnFDGnTCaiHMCCz9Kr2ySxyonLSyM4V2+gyO2b/g/qAL3/+CwnO3Eh+5q22WnbDRRaHMm3t6YcZeD3VaB+dtpW92VwFplUt/j7v8zBXUlQveTSktP7fzXnI0kG3rr3loGBgYEhcJsSA02yE82CmhzfMjAwMKgpCTMwMDAwrPe6h7fBN7h8rCWn93+91z2MeCOWDQP4fM1ISj8JVi0it0KwiRHTn2IhtgOG7pBrjy4xYhOjSdPnn7wVUWJU6y1qyen9FxT8gyL2/j0LAzYxqjQEaBnUAwYADQ24+g/Tu+4AAAAASUVORK5CYII="
    ]
    /// 기훈 몸 (녹색 체육복, 24×36 · 위 2줄 빈 줄). 머리는 내 캐릭터 것을 쓴다
    private static let suit = "iVBORw0KGgoAAAANSUhEUgAAABgAAAAkCAYAAACTz/ouAAAB7klEQVR42mNgGOqAkZACLTm9//jkrz26hNcMFkKGu3uY4pR//uoVAwMDw398lrAQMvzkkcMMCjJSDA+ePGNgYGBAYTMwMDCoaSngtYSRkOGEwIfPXxjUtHUYbl17gTW4mHBpJMZwGLh4/hx5cYAO9qzoQuG7RJQR1MNCioHY5J9ev8XglodbHROt88HAWYAcPKYRDRjyyGJduRGDPIhOr2ggSoxoC649usRo4lPHwMDAwPD37ju4+KkdJxhO7TgB5/+9+47hwu1HDPWzDuEsk1jwFWImPnX/z2xpgouZeVjA2U+v36K8NEUuSTd2x8DFYa4mpkRlxGcwa4MfivjRiDIG3ZZcBj4VWbjYt7tPGVgXn8VpEQs2w9ENxge4lKUZGBqkGb4/eM6gtQCzVMUayVrCUnAXHyWivPn+4Dl5ZZE1gbJocBcVw9cC5CRKtgXztbQYjpuYEG1IJyc3QycnN+k++N2wCSWZoqeqoxFlDL8bNpFWZV57dInRyDkPXkQwMTGjWAjL4d8fPGc4f/4CAwMDA0P19APE52RkhYRadcSURQOfiv79+4tVnFNBkigLCLaLmO7sg6coEwZIHKzh5mNgYGBguHJoDeUW/FNxQuJBajlJ3wwGBgYGhhfnLzAwQCOYKs13bJFPqPk+9AEA5jTD+hEL1A8AAAAASUVORK5CYII="
    private static var eyeCache: [Eyes: UIImage] = [:]
    private struct Key: Hashable { let me: MeChar; let leg: Int; let lift: Bool }
    private static var cache: [Key: UIImage] = [:]

    private static func png(_ s: String) -> UIImage? { Data(base64Encoded: s).flatMap { UIImage(data: $0) } }

    static func younghi(_ e: Eyes) -> UIImage? {
        if let c = eyeCache[e] { return c }
        let img = yh[e].flatMap(png)
        eyeCache[e] = img
        return img
    }

    /// 기훈 한 장 (24×36, 위 2줄 빈 줄). 목 위는 내 캐릭터(소품 빼고), 어깨 아래는 체육복. 다리·들썩은 장면 도구 그대로
    static func gihun(_ me: MeChar, leg: Int = 0, lift: Bool = false) -> UIImage? {
        let key = Key(me: me, leg: leg, lift: lift)
        if let c = cache[key] { return c }
        var who = me
        who.parts["prop"] = 0
        guard let head = MeMotion.image(who)?.cgImage, let body = png(suit)?.cgImage else { return nil }
        let w = MeArt.W, h = MeArt.H, top = MeMotion.legTop
        var p = PixBuf.read(head, w, h)
        let s = PixBuf.read(body, w, h + 2)
        for y in 15..<h { for x in 0..<w { p[y * w + x] = s[(y + 2) * w + x] } }
        PixBuf.legs(&p, w, h, leg, top: top, split: w / 2)
        PixBuf.pad(&p, w, top: 2)
        if lift { PixBuf.lift(&p, w, until: top + 2) }
        let img = PixBuf.image(p, w, h + 2)
        if cache.count > 60 { cache.removeAll() }
        cache[key] = img
        return img
    }
}

/// 도트 그림 한 장 (끝 맞춤 없이 그대로 늘린다)
private struct Dot: View {
    let img: UIImage?
    let w: CGFloat, h: CGFloat
    var body: some View {
        if let img { Image(uiImage: img).interpolation(.none).resizable().frame(width: w, height: h) } else { Color.clear.frame(width: w, height: h) }
    }
}

/// 날아오르는 음표 하나: period 마다 1.6초 동안 떠올랐다 사라진다 (동작 줄이기면 그 자리에 그대로)
private struct FlyNote: View {
    struct KF { var o = 0.0; var x: CGFloat = 0; var y: CGFloat = 0; var r = -14.0; var s = 0.7 }
    let text: String
    var color: Color
    var size: CGFloat = 8
    var period = 7.0
    var delay = 0.0
    @Environment(\.accessibilityReduceMotion) private var still
    private var label: some View { Text(text).font(.system(size: size, weight: .bold)).foregroundStyle(color).fixedSize() }
    var body: some View {
        if still { label } else {
            Every(seconds: period, delay: delay) { n in
                label.keyframeAnimator(initialValue: KF(), trigger: n) { c, v in
                    c.scaleEffect(v.s).rotationEffect(.degrees(v.r)).offset(x: v.x, y: v.y).opacity(v.o)
                } keyframes: { _ in
                    KeyframeTrack(\.o) { MoveKeyframe(0); LinearKeyframe(1, duration: 0.28); LinearKeyframe(0.9, duration: 0.91); LinearKeyframe(0, duration: 0.42) }
                    KeyframeTrack(\.x) { MoveKeyframe(0); LinearKeyframe(5, duration: 0.7); LinearKeyframe(-2, duration: 0.49); LinearKeyframe(3, duration: 0.42) }
                    KeyframeTrack(\.y) { MoveKeyframe(0); LinearKeyframe(-9, duration: 0.7); LinearKeyframe(-16, duration: 0.49); LinearKeyframe(-24, duration: 0.42) }
                    KeyframeTrack(\.r) { MoveKeyframe(-14); LinearKeyframe(12, duration: 0.7); LinearKeyframe(-10, duration: 0.49); LinearKeyframe(8, duration: 0.42) }
                    KeyframeTrack(\.s) { MoveKeyframe(0.7); LinearKeyframe(1, duration: 0.7) }
                }
            }
        }
    }
}

// MARK: 홈 장면 1 도리와 문어: 왼쪽 수족관(문어)에서 오른쪽 시드니 오페라하우스(목표)까지 헤엄친다
struct DoryScene: View {
    let s: BoxScene
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let mood = ThemeMood(s.today), down = mood == .down, dk = cs == .dark
        let dx = 62 + 206 * CGFloat(min(1, max(0, s.f)))
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                let all = Path(CGRect(x: 0, y: 0, width: 300, height: 170))
                ctx.fill(all, with: .linearGradient(Gradient(colors: [Color(hex: dk ? 0x0F1A35 : 0xB3D9F4), Color(hex: dk ? 0x26406F : 0xF4F9FE)]), startPoint: .zero, endPoint: pt(0, 170)))
                if dk {
                    for (x, y, r) in [(190, 20, 0.8), (230, 44, 0.6), (160, 36, 0.5)] as [(CGFloat, CGFloat, CGFloat)] { ctx.fill(circle(x, y, r), with: hex(0xC9D3EA)) }
                } else {
                    ctx.fill(Path(ellipseIn: CGRect(x: 182, y: 25, width: 36, height: 10)), with: hex(0xFFFFFF))
                    ctx.fill(Path(ellipseIn: CGRect(x: 201, y: 21, width: 18, height: 10)), with: hex(0xFFFFFF))
                }
                var op = ctx; op.translateBy(x: 246, y: 60); ThemeArt.opera(op, dk: dk)
                // 바다
                let sea = Path(CGRect(x: 0, y: 82, width: 300, height: 88))
                ctx.fill(sea, with: .linearGradient(Gradient(colors: [Color(hex: dk ? 0x1F78B4 : 0x6FC0EA), Color(hex: dk ? 0x0A3560 : 0x2F7FBF)]), startPoint: pt(0, 82), endPoint: pt(0, 170)))
                let ray = dk ? Color(hex: 0xBFE9FF) : Color.white
                ctx.fill(sea, with: .radialGradient(Gradient(colors: [ray.opacity(dk ? 0.25 : 0.4), ray.opacity(0)]), center: pt(210, 82), startRadius: 0, endRadius: 200))
                var wave = Path(); wave.move(to: pt(0, 82))
                for i in 0..<15 { let x = CGFloat(i) * 20; wave.addQuadCurve(to: pt(x + 20, 82), control: pt(x + 10, i % 2 == 0 ? 79 : 85)) }
                ctx.stroke(wave, with: dk ? hex(0x9FD6EE, 0.7) : hex(0xFFFFFF, 0.8), lineWidth: 1.2)
                var sand = Path(); sand.move(to: pt(0, 160)); sand.addQuadCurve(to: pt(120, 160), control: pt(60, 154))
                sand.addQuadCurve(to: pt(240, 158), control: pt(180, 166)); sand.addQuadCurve(to: pt(300, 160), control: pt(300, 150))
                sand.addLine(to: pt(300, 170)); sand.addLine(to: pt(0, 170)); sand.closeSubpath()
                ctx.fill(sand, with: hex(dk, 0xE6CB94, 0xC7A46A))
                for (a, c1, b, c2, e) in [((210.0, 160.0), (207.0, 148.0), (211.0, 140.0), (214.0, 148.0), (212.0, 160.0)),
                                          ((216, 160), (219, 150), (215, 144), (213, 152), (216, 160)),
                                          ((120, 160), (118, 151), (122, 146), (123, 153), (120, 160))] as [((CGFloat, CGFloat), (CGFloat, CGFloat), (CGFloat, CGFloat), (CGFloat, CGFloat), (CGFloat, CGFloat))] {
                    var p = Path(); p.move(to: pt(a.0, a.1)); p.addQuadCurve(to: pt(b.0, b.1), control: pt(c1.0, c1.1)); p.addQuadCurve(to: pt(e.0, e.1), control: pt(c2.0, c2.1)); p.closeSubpath()
                    ctx.fill(p, with: hex(dk, 0x3A9A6A, 0x3FAE7A))
                }
                // 25·50·75% 눈금 (도리 자리와 같은 축)
                for (t, f) in Sk.pctTicks {
                    let x = 62 + 206 * CGFloat(f)
                    ctx.stroke(Sk.line(pt(x, 150), pt(x, 160)), with: hex(dk, 0x8A5A00, 0xFFD98A), style: StrokeStyle(lineWidth: 1.5, dash: [2, 2]))
                    Sk.text(ctx, t, x, 165.5, Color(hex: dk ? 0xFFD98A : 0x8A5A00), size: 8, bold: !dk, anchor: .center)
                }
                Sk.text(ctx, String(localized: "출발"), 62, 165.5, Color(hex: dk ? 0xFFD98A : 0x8A5A00), size: 8, bold: !dk, anchor: .center)
                Sk.text(ctx, s.target, 268, 165.5, Color(hex: dk ? 0xFFD98A : 0x8A5A00), size: 8.5, bold: true, anchor: .center)
            }
            // 수족관: 판 위쪽 30을 잘라 내므로 0.7배로 줄여 글 아래로 내린다 (바닥 왼쪽 모서리 기준, 2026-10-27)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    let tank = Path(roundedRect: CGRect(x: 6, y: 90, width: 44, height: 66), cornerRadius: 3)
                    ctx.fill(tank, with: dk ? hex(0x9FD6EE, 0.18) : hex(0xFFFFFF, 0.28)); ctx.stroke(tank, with: hex(dk, 0xFFFFFF, 0xCFEFFF), lineWidth: 1.5)
                    ctx.fill(Path(CGRect(x: 6, y: 90, width: 44, height: 6)), with: dk ? hex(0xCFEFFF, 0.35) : hex(0xFFFFFF, 0.5))
                    for (x, y, r) in [(14, 153, 2.5), (20, 154, 2), (40, 153, 3)] as [(CGFloat, CGFloat, CGFloat)] { ctx.fill(circle(x, y, r), with: hex(dk, 0x8A94A8, 0x7F8AA3)) }
                    var pl = Path(); pl.move(to: pt(44, 153)); pl.addQuadCurve(to: pt(46, 129), control: pt(42, 139)); pl.addQuadCurve(to: pt(44, 153), control: pt(48, 139)); pl.closeSubpath()
                    ctx.fill(pl, with: hex(dk, 0x3A9A6A, 0x3FAE7A))
                }
                Octopus(down: down).position(x: 25, y: 132)
            }
            .frame(width: 300, height: 170, alignment: .topLeading)
            .scaleEffect(0.7, anchor: UnitPoint(x: 6.0 / 300, y: 156.0 / 170))
            DoryFish(down: down)
                .position(x: dx, y: 130)
                .animation(.easeInOut(duration: 1.2), value: dx)
        }
    }
}

/// 수족관 문어 (원점 = 몸 가운데 아래쪽, 50×56 칸의 (25, 30))
private struct Octopus: View {
    let down: Bool
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let dk = cs == .dark
        ZStack {
            // 다리: 오른 날엔 가끔 흔들어 배웅한다
            Every(seconds: 3.2, delay: 0.6) { n in
                Canvas { c, _ in var c = c; c.translateBy(x: 25, y: 30); ThemeArt.octoArms(c, down: down, dk: dk) }
                    .keyframeAnimator(initialValue: 0.0, trigger: down ? 0 : n) { c, v in
                        c.rotationEffect(.degrees(v), anchor: UnitPoint(x: 0.5, y: 32.0 / 56))
                    } keyframes: { _ in
                        KeyframeTrack { CubicKeyframe(8, duration: 0.45); CubicKeyframe(-2, duration: 0.45); CubicKeyframe(8, duration: 0.45); CubicKeyframe(0, duration: 0.45) }
                    }
            }
            Canvas { c, _ in var c = c; c.translateBy(x: 25, y: 30); ThemeArt.octoBody(c, down: down, dk: dk); ThemeArt.octoMouth(c, down: down) }
            Canvas { c, _ in var c = c; c.translateBy(x: 12, y: 7); ThemeArt.octoEyes(c, down: down) }
                .frame(width: 24, height: 14).blink(5.6, delay: 1.3)
                .position(x: 25, y: 23)
            if down {
                // 느낌표: 10초에 한 번 깜빡
                Every(seconds: 10, delay: 0) { n in
                    Text("!").font(.system(size: 12, weight: .heavy)).foregroundStyle(bx(0xE09A00, 0xFFD23F))
                        .keyframeAnimator(initialValue: 1.0, trigger: n) { c, v in c.opacity(v) } keyframes: { _ in
                            KeyframeTrack { LinearKeyframe(0.1, duration: 0.2); LinearKeyframe(0.1, duration: 0.4); LinearKeyframe(1, duration: 0.2) }
                        }
                }
                .position(x: 40.5, y: 11.5)
            }
        }
        .frame(width: 50, height: 56)
        .animation(.easeInOut(duration: 0.5), value: down)
    }
}

/// 도리: 하락장엔 문어 쪽(왼쪽), 상승장엔 오페라하우스 쪽(오른쪽)을 본다. 헤엄·꼬리·거품·눈 깜빡임
private struct DoryFish: View {
    let down: Bool
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let dk = cs == .dark
        ZStack {
            // 거품 셋 (입 앞에서 떠오른다)
            ForEach(0..<3, id: \.self) { i in
                let (x, y, r): (CGFloat, CGFloat, CGFloat) = [(14, -8, 1.6), (16, -10, 1.1), (13, -7, 1.3)][i]
                Bubble(r: r, delay: Double(i) * 0.8).position(x: 25 + x, y: 22 + y)
            }
            Loop(duration: 0.8) { on in
                ZStack {
                    Loop(duration: 0.35) { wag in
                        Canvas { c, _ in var c = c; c.translateBy(x: 22, y: 7); ThemeArt.doryTail(c, dk: dk) }
                            .frame(width: 8, height: 14)
                            .scaleEffect(x: 1, y: wag ? 0.6 : 1, anchor: .trailing)
                    }
                    .position(x: 7, y: 22)
                    Canvas { c, _ in var c = c; c.translateBy(x: 25, y: 22); ThemeArt.dory(c, dk: dk, tail: false, eye: false) }
                    Canvas { c, _ in ThemeArt.doryEye(c, at: pt(5, 5)) }
                        .frame(width: 10, height: 10).blink(4.2)
                        .position(x: 29, y: 15)
                }
                .rotationEffect(.degrees(on ? -3 : 0)).offset(y: on ? -2 : 0)
            }
        }
        .frame(width: 50, height: 44)
        .scaleEffect(x: down ? -1 : 1, y: 1)
        .animation(.easeInOut(duration: 0.4), value: down)
    }
}

private struct Bubble: View {
    let r: CGFloat, delay: Double
    var body: some View {
        Every(seconds: 2.4, delay: delay) { n in
            Circle().stroke(Color(hex: 0xCFEFFF), lineWidth: 0.8).frame(width: r * 2, height: r * 2)
                .keyframeAnimator(initialValue: CGSize(width: 0, height: 0), trigger: n) { c, v in
                    c.offset(y: v.height).opacity(v.width)
                } keyframes: { _ in
                    KeyframeTrack(\.width) { MoveKeyframe(0); LinearKeyframe(0.8, duration: 0.48); LinearKeyframe(0, duration: 1.92) }
                    KeyframeTrack(\.height) { MoveKeyframe(0); LinearKeyframe(-26, duration: 2.4) }
                }
        }
    }
}

// MARK: 홈 장면 2 피노키오와 새: 제페토 공방 벽. 코가 오른쪽으로 자라고(길이 = 진행), 새는 늘 코끝
struct PinoScene: View {
    let s: BoxScene
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let up = ThemeMood(s.today) == .up, dk = cs == .dark
        let tip = 64 + 226 * CGFloat(min(1, max(0, s.f)))
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                let all = Path(CGRect(x: 0, y: 0, width: 300, height: 170))
                ctx.fill(all, with: .linearGradient(Gradient(colors: [Color(hex: dk ? 0x22170E : 0xEBD6B4), Color(hex: dk ? 0x4A331D : 0xFBF2E3)]), startPoint: .zero, endPoint: pt(0, 170)))
                var planks = Path()
                for x in [70.0, 140, 210, 280] as [CGFloat] { planks.move(to: pt(x, 0)); planks.addLine(to: pt(x, 152)) }
                ctx.stroke(planks, with: dk ? hex(0x3A2816, 0.8) : hex(0xD7BC94), lineWidth: 1)
                // 벽에 걸린 연장 그림자
                let tool = Color(hex: dk ? 0x1A110A : 0xC9A877, opacity: 0.55)
                ctx.fill(Path(roundedRect: CGRect(x: 226, y: 40, width: 3, height: 22), cornerRadius: 1), with: .color(tool))
                ctx.fill(Path(roundedRect: CGRect(x: 221, y: 58, width: 13, height: 6), cornerRadius: 2), with: .color(tool))
                ctx.fill(Sk.p([(252, 40), (255, 40), (255, 56), (259, 62), (248, 62), (252, 56)]), with: .color(tool))
                ctx.stroke(circle(198, 48, 6), with: .color(tool), lineWidth: 2)
                let lamp = Color(hex: dk ? 0xFFD58A : 0xFFCE78)
                ctx.fill(all, with: .radialGradient(Gradient(colors: [lamp.opacity(dk ? 0.34 : 0.45), lamp.opacity(0)]), center: pt(42, 119), startRadius: 0, endRadius: 150))
                ctx.fill(Path(CGRect(x: 0, y: 152, width: 300, height: 18)), with: hex(dk, 0xD2AE7E, 0x2C1D10))
                ctx.fill(Path(CGRect(x: 0, y: 152, width: 300, height: 1.5)), with: hex(dk, 0xB58D5C, 0x5A3E22))
                // 25·50·75% 눈금 (코끝 자리와 같은 축) · 끝 = 목표
                let mark = dk ? 0xFFD98A : 0x8A5A00 as UInt32, label = Color(hex: dk ? 0xFFD98A : 0x6E4600)
                for (t, f) in Sk.pctTicks {
                    let x = 64 + 226 * CGFloat(f)
                    ctx.stroke(Sk.line(pt(x, 146), pt(x, 158)), with: hex(mark), style: StrokeStyle(lineWidth: 1.5, dash: [2, 2]))
                    Sk.text(ctx, t, x, 164.5, label, size: 8, bold: !dk, anchor: .center)
                }
                Sk.text(ctx, String(localized: "출발"), 64, 164.5, label, size: 8, bold: !dk, anchor: .center)
                ctx.stroke(Sk.line(pt(290, 146), pt(290, 158)), with: hex(mark), lineWidth: 1.5)
                Sk.text(ctx, s.target, 290, 164, label, size: 8.5, bold: true, anchor: .trailing)
            }
            PinoRig(up: up, tip: tip)
        }
    }
}

/// 몸·코·새 한 덩어리. 가끔 발끝(42, 152)을 축으로 짧게 들썩이고, 새는 날개로 균형을 잡는다
private struct PinoRig: View {
    let up: Bool
    let tip: CGFloat
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let len = tip - 52, dk = cs == .dark
        let note = Color(hex: dk ? 0xFFD98A : 0xB07A00)
        Every(seconds: up ? 4.2 : 3.6, delay: 1) { n in
            ZStack(alignment: .topLeading) {
                Nose(tip: tip).stroke(Color(hex: 0x9C6A38).opacity(dk ? 0 : 1), lineWidth: 0.4)
                Nose(tip: tip).fill(LinearGradient(stops: [.init(color: Color(hex: 0xECBC82), location: 0), .init(color: Color(hex: 0xD49A5E), location: 0.55), .init(color: Color(hex: 0xB47B44), location: 1)],
                                                    startPoint: UnitPoint(x: 0, y: 101.8 / 170), endPoint: UnitPoint(x: 0, y: 105.4 / 170)))
                Nose(tip: tip, hi: true).stroke(Color(hex: 0xF6D3A3), style: StrokeStyle(lineWidth: 0.7, lineCap: .round))
                // 코가 길어지면 잎이 돋는다
                Canvas { c, _ in var c = c; c.translateBy(x: 5, y: 10); ThemeArt.leaf(c, dk: dk) }
                    .frame(width: 12, height: 12).position(x: 52 + len * 0.42 + 1, y: 102.8 - 4)
                    .opacity(len > 95 ? 1 : 0)
                Canvas { c, _ in var c = c; c.translateBy(x: 5, y: 2); c.scaleBy(x: 1, y: -1); ThemeArt.leaf(c, light: true) }
                    .frame(width: 12, height: 12).position(x: 52 + len * 0.7 + 1, y: 103 + 4)
                    .opacity(len > 170 ? 1 : 0)
                Canvas { c, _ in var c = c; c.translateBy(x: 42, y: 0); ThemeArt.pino(c, up: up, dk: dk) }
                    .frame(width: 300, height: 170)
                if !up {
                    ForEach(0..<3, id: \.self) { i in
                        FlyNote(text: ["♪", "♫", "♪"][i], color: note, period: 7, delay: 2 + Double(i) * 0.35)
                            .position(x: [55, 59, 53][i], y: [89, 91, 92][i])
                    }
                }
                PinoBird(laugh: !up, n: n).position(x: tip - 5, y: 93.6)
                Group {
                    if up {
                        FlyNote(text: "♪", color: note, period: 9, delay: 4)
                            .position(x: tip - 14, y: 83.6)
                    } else {
                        ForEach(0..<3, id: \.self) { i in
                            FlyNote(text: ["♪", "♫", "♬"][i], color: dk ? Color(hex: 0xFFE6A8) : note, size: 8.5, period: 7, delay: 2 + Double(i) * 0.35)
                                .position(x: tip - 5 + [9, 13, 6][i], y: 102.6 - [17, 20, 20][i])
                        }
                    }
                }
            }
            .frame(width: 300, height: 170, alignment: .topLeading)
            .animation(.easeOut(duration: 0.9), value: tip)
            .keyframeAnimator(initialValue: 0.0, trigger: n) { c, v in
                c.rotationEffect(.degrees(v), anchor: UnitPoint(x: 42.0 / 300, y: 152.0 / 170))
            } keyframes: { _ in
                // 오른 날 1.2초 한 번 살짝 기울고, 떨어진 날 0.9초 깔깔 들썩
                let k: [Double] = up ? [0.2, 0.4, 0.45, 0.32, 0.12, 0] : [0.4, -0.4, 0.35, -0.3, 0.2, 0]
                let d = up ? 0.2 : 0.15
                KeyframeTrack { CubicKeyframe(k[0], duration: d); CubicKeyframe(k[1], duration: d); CubicKeyframe(k[2], duration: d); CubicKeyframe(k[3], duration: d); CubicKeyframe(k[4], duration: d); CubicKeyframe(k[5], duration: d) }
            }
        }
    }
}

/// 피노키오 코: 밑동 52 에서 tip 까지 (hi = 위쪽 밝은 줄)
private struct Nose: Shape {
    var tip: CGFloat
    var hi = false
    var animatableData: CGFloat { get { tip } set { tip = newValue } }
    func path(in r: CGRect) -> Path {
        var p = Path()
        if hi { p.move(to: pt(53, 102.7)); p.addLine(to: pt(max(53, tip - 3), 103.3)); return p }
        p.move(to: pt(52, 101.8)); p.addLine(to: pt(tip, 103)); p.addQuadCurve(to: pt(tip, 104.1), control: pt(tip + 1.8, 103.5))
        p.addLine(to: pt(52, 105.4)); p.closeSubpath()
        return p
    }
}

/// 코끝 새 (34×26 칸, 발 = (17, 22)). 몸이 들썩일 때 날개를 펴고 몸이 살짝 밀렸다 돌아온다
private struct PinoBird: View {
    struct KF { var wing = 0.0; var bob: CGFloat = 0 }
    let laugh: Bool
    let n: Int
    var body: some View {
        Color.clear.frame(width: 34, height: 26)
            .keyframeAnimator(initialValue: KF(), trigger: n) { _, v in
                Canvas { c, _ in var c = c; c.translateBy(x: 17, y: 22 + v.bob); ThemeArt.bird(c, laugh: laugh, wing: v.wing) }
                    .frame(width: 34, height: 26)
            } keyframes: { _ in
                KeyframeTrack(\.wing) { LinearKeyframe(-40, duration: 0.12); LinearKeyframe(-12, duration: 0.12); LinearKeyframe(-34, duration: 0.12); LinearKeyframe(-6, duration: 0.12); LinearKeyframe(0, duration: 0.15) }
                KeyframeTrack(\.bob) { CubicKeyframe(-1.5, duration: 0.15); CubicKeyframe(0.8, duration: 0.2); CubicKeyframe(-0.5, duration: 0.2); CubicKeyframe(0, duration: 0.2) }
            }
    }
}

// MARK: 홈 장면 3 철수와 영희 (비튼 무궁화): 왼쪽 영희 눈을 피해 오른쪽 EXIT 로 간다
struct MugScene: View {
    let s: BoxScene
    @State private var eyes: MugArt.Eyes = .open
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let down = ThemeMood(s.today) == .down, dk = cs == .dark
        // 다크: 도트 인물·출구를 남색으로 살짝 눌러 어두운 하늘에 녹인다 (도트 원본은 그대로, 얼굴이 묻히지 않을 만큼)
        let dim = dk ? Color(hex: 0xC8D0E0) : .white
        let gx = 68 + 212 * CGFloat(min(1, max(0, s.f)))
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                // 다크 하늘은 종목·흐름·분석 바탕과 같은 밝기 (2026-10-24)
                ctx.fill(Path(CGRect(x: 0, y: 0, width: 300, height: 170)), with: .linearGradient(Gradient(colors: [Color(hex: dk ? 0x14304F : 0xA9D6F6), Color(hex: dk ? 0x2C5F8F : 0xE4F3FD)]), startPoint: .zero, endPoint: pt(0, 170)))
                // 땅: 다크는 종목·흐름·분석 바탕의 흙색과 같게 (2026-10-24)
                ctx.fill(Path(CGRect(x: 0, y: 136, width: 300, height: 34)), with: hex(dk, 0xE2C595, 0x8F7550))
                ctx.fill(Path(CGRect(x: 0, y: 136, width: 300, height: 2.5)), with: hex(dk, 0xC9A673, 0x7A6243))
            }
            // 사물: 다크에선 인물과 같은 비율(dim)로 눌러 어두운 하늘·흙에 녹인다
            Canvas { ctx, _ in
                // 구름은 위쪽 (기훈 머리와 겹치지 않게)
                for (x, y, rx, ry) in [(210, 30, 22, 7), (224, 25, 12, 7), (168, 62, 18, 5), (178, 58, 9, 5)] as [(CGFloat, CGFloat, CGFloat, CGFloat)] {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - rx, y: y - ry, width: rx * 2, height: ry * 2)), with: dk ? hex(0xE9F3FB, 0.6) : hex(0xFFFFFF))
                }
                for r in [CGRect(x: 96, y: 150, width: 3, height: 1.5), CGRect(x: 180, y: 158, width: 4, height: 1.5), CGRect(x: 236, y: 147, width: 2, height: 1.5)] { ctx.fill(Path(r), with: hex(dk, 0xC9A673, 0x6E583A, 0.8)) }
                // 25·50·75% 눈금 (기훈 자리와 같은 축). 다크 흙 위에선 금색으로 (다른 새 테마 다크와 같은 색)
                for (t, f) in Sk.pctTicks {
                    let x = 68 + 212 * CGFloat(f)
                    ctx.stroke(Sk.line(pt(x, 140), pt(x, 152)), with: hex(dk, 0x7A4A10, 0xFFD98A), style: StrokeStyle(lineWidth: 1.5, dash: [2, 2]))
                    Sk.text(ctx, t, x, 159.5, Color(hex: dk ? 0xFFD98A : 0x7A4A10), size: 8, bold: !dk, anchor: .center)
                }
                Sk.text(ctx, String(localized: "출발"), 68, 159.5, Color(hex: dk ? 0xFFD98A : 0x7A4A10), size: 8, bold: !dk, anchor: .center)
                Sk.text(ctx, s.target, 280, 159.5, Color(hex: dk ? 0xFFD98A : 0x7A4A10), size: 8.5, bold: true, anchor: .center)
                // 영희 앞 분필 선 (사선)
                ctx.stroke(Sk.line(pt(64, 137.5), pt(50, 170)), with: dk ? hex(0xFBF6EA, 0.9) : hex(0xFFFFFF), style: StrokeStyle(lineWidth: 2.6, lineCap: .square))
                // 왼쪽 끝 나무
                ctx.fill(Path(CGRect(x: -1, y: 94, width: 6, height: 44)), with: hex(dk, 0x7A5634, 0x6B4A2B))
                ctx.fill(circle(0, 90, 13), with: hex(dk, 0x3A8048, 0x2F6B3A)); ctx.fill(circle(8, 98, 6), with: hex(dk, 0x4A9356, 0x3A7A45))
            }
            .colorMultiply(dim)
            // 출구 빛 (오른 날엔 조금 빨리)
            Loop(duration: down ? 1.2 : 0.7) { on in
                Ellipse().fill(RadialGradient(colors: [Color(hex: dk ? 0xB8FFCF : 0x5FE08F, opacity: dk ? 0.6 : 0.55), Color(hex: dk ? 0xB8FFCF : 0x5FE08F, opacity: 0)], center: .center, startRadius: 0, endRadius: 30))
                    .frame(width: 52, height: 64).opacity(on ? 0.55 : 1)
            }
            .colorMultiply(dim)
            .position(x: 282, y: 124)
            Canvas { ctx, _ in
                let door = Path(roundedRect: CGRect(x: 268, y: 96, width: 28, height: 42), cornerRadius: 2)
                ctx.fill(door, with: hex(0x1D6B45)); ctx.stroke(door, with: hex(0x0F3D27), lineWidth: 2)
                ctx.fill(Path(CGRect(x: 271, y: 99, width: 22, height: 39)), with: hex(0xC8FFD9))
                ctx.fill(Path(roundedRect: CGRect(x: 267, y: 84, width: 30, height: 9), cornerRadius: 2), with: hex(0x0F3D27))
                Sk.text(ctx, "EXIT", 282, 88.5, Color(hex: 0x7DFFAE), size: 7, bold: true, anchor: .center)
            }
            .colorMultiply(dim)
            Dot(img: MugArt.younghi(eyes), w: 45, h: 52.5).colorMultiply(dim).position(x: 30.5, y: 122.25)
            if eyes == .closed && !still {
                // 눈 감은 동안 노래한다 (무궁화 꽃이 피었습니다)
                ForEach(0..<3, id: \.self) { i in
                    FlyNote(text: ["♪", "♫", "♪"][i], color: Color(hex: 0xFFF4C2), size: 8.5, period: 1.5, delay: Double(i) * 0.5)
                        .position(x: [53, 58, 50][i], y: [101, 98, 97][i])
                }
                .colorMultiply(dim)
            }
            Gihun(to: gx, down: down, eyes: eyes).colorMultiply(dim)
        }
        .task(id: still) {
            // 장중엔 눈 뜨고 가끔 감고, 장 마감·주말엔 감고 가끔 한쪽 눈
            while !Task.isCancelled {
                let shut = !MarketClock.open()
                eyes = shut ? .closed : .open
                if still { try? await Task.sleep(for: .seconds(60)); continue }
                try? await Task.sleep(for: .seconds(shut ? 5.2 : 4.2))
                if Task.isCancelled { break }
                eyes = shut ? .peek : .closed
                try? await Task.sleep(for: .seconds(shut ? 1.3 : 1.8))
            }
        }
    }
}

/// 기훈: 내 캐릭터 얼굴 + 녹색 체육복. 자리가 바뀌면 옆걸음, 서 있으면 영희 눈에 맞춰 차분히 숨,
/// 영희가 눈 감으면 크게 숨 (오른 날엔 한 번 폴짝). 떨어진 날 영희가 보고 있으면 식은땀
private struct Gihun: View {
    let to: CGFloat
    let down: Bool
    let eyes: MugArt.Eyes
    @State private var x: CGFloat?
    @State private var k = 0
    @State private var shutAt = -99
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.colorScheme) private var cs

    var body: some View {
        let dk = cs == .dark
        let me = MeChar.current ?? MeChar()
        let cx = x ?? to, d = to - cx, walking = abs(d) > 0.5
        var img: UIImage?, dy: CGFloat = 0, sy: CGFloat = 1
        let s = k - shutAt
        if walking {
            let i = (k / 2) % 4
            img = MugArt.gihun(me, leg: [0, 1, 0, 2][i], lift: i % 2 == 1)
        } else if eyes == .open {
            img = MugArt.gihun(me); sy = k % 24 < 21 ? 1 : 1.012
        } else {
            img = MugArt.gihun(me); sy = k % 8 < 4 ? 1.035 : 1
            if !down && s >= 3 && s < 5 { dy = -3 }
        }
        return ZStack(alignment: .topLeading) {
            Dot(img: img, w: 36, h: 54)
                .scaleEffect(x: walking && d < 0 ? -1 : 1, y: sy, anchor: .bottom)
                .position(x: cx, y: 123 + dy)
            if down && !walking && eyes != .closed {
                Canvas { c, _ in
                    var p = Path(); p.move(to: pt(3, 1)); p.addQuadCurve(to: pt(3, 6.6), control: pt(5.4, 4.4)); p.addQuadCurve(to: pt(3, 1), control: pt(0.6, 4.4)); p.closeSubpath()
                    c.fill(p, with: hex(dk, 0x7CCBFF, 0x9FDCFF)); c.stroke(p, with: hex(0x2A1E2E), lineWidth: 0.6)
                }
                .frame(width: 6, height: 8).position(x: cx + 14, y: 107.5 + dy)
            }
        }
        .frame(width: 300, height: 170, alignment: .topLeading)
        .onChange(of: eyes) { _, e in if e != .open { shutAt = k } }
        .task(id: still) {
            guard !still else { x = to; return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(MoodScript.tick))
                let now = x ?? to, gap = to - now
                if abs(gap) > 0.5 { x = now + (gap > 0 ? 1 : -1) * min(1.4, abs(gap)) } else if x == nil { x = to }
                k &+= 1
            }
        }
    }
}

// MARK: 종목·흐름·분석 작은 등장인물 (시안 18장 · 라이트 28장). 오른 날·떨어진 날에 따라 바뀐다

/// 몫 띠 위 오른쪽 끝에 서는 인물
struct BandBuddy: View {
    let look: BoxLook
    let up: Bool
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let m = Mini(dk: cs == .dark)
        HStack(alignment: .bottom, spacing: 2) {
            switch look {
            case .dory: if up { m.dory } else { m.octo }
            case .pino: if up { m.leaf; m.bird(false) } else { m.note; m.bird(true) }
            case .mug: m.sphere(ripple: !up)
            case .jump: if up { m.fire; m.car } else { m.car }
            case .horizon: if up { m.glow } else { m.wave; m.glow }
            case .tub: if up { m.duck } else { m.splash; m.duck }
            case .bar: EmptyView()
            }
        }
        .allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// 분석 막대: 지금 자리에 서는 인물 · 목표선 옆에 서는 것
struct TrackBuddy: View {
    let look: BoxLook
    var goal = false
    @Environment(\.colorScheme) private var cs
    var body: some View {
        let m = Mini(dk: cs == .dark)
        Group {
            if goal {
                switch look {
                case .dory: m.opera
                case .mug: m.sphere(inside: true)
                case .jump: m.portal
                case .horizon: m.sun
                case .tub: m.foam
                default: EmptyView()
                }
            } else {
                switch look {
                case .dory: m.dory
                case .pino: m.bird(false)
                case .mug: m.sphere()
                case .jump: m.car
                case .horizon: m.glow
                case .tub: m.duck
                case .bar: EmptyView()
                }
            }
        }
        .allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// 작은 그림들 (시안 크기 그대로). dk = 다크 색
private struct Mini {
    let dk: Bool
    var dory: some View {
        Canvas { [dk] c, _ in var c = c; c.translateBy(x: 14, y: 9.5); c.scaleBy(x: 0.72, y: 0.72); c.translateBy(x: 3.5, y: 1); ThemeArt.dory(c, dk: dk) }.frame(width: 28, height: 19)
    }
    var octo: some View {
        Canvas { [dk] c, _ in
            var c = c; c.translateBy(x: 13, y: 15); c.scaleBy(x: 0.55, y: 0.55)
            ThemeArt.octoArms(c, down: true, dk: dk); ThemeArt.octoBody(c, down: true, dk: dk); ThemeArt.octoEyes(c, down: true); ThemeArt.octoMouth(c, down: true)
            c.draw(Text("!").font(.system(size: 12, weight: .heavy)).foregroundColor(Color(hex: dk ? 0xFFD23F : 0xE09A00)), at: pt(15.5, -18))
        }
        .frame(width: 26, height: 26)
    }
    var opera: some View {
        Canvas { [dk] c, _ in var c = c; c.translateBy(x: 2, y: 2); c.scaleBy(x: 0.5, y: 0.5); ThemeArt.opera(c, dk: dk) }.frame(width: 26, height: 14)
    }
    func bird(_ laugh: Bool) -> some View {
        Canvas { c, _ in var c = c; c.translateBy(x: 10.6, y: 18); c.scaleBy(x: 0.8, y: 0.8); ThemeArt.bird(c, laugh: laugh) }.frame(width: 20, height: 19)
    }
    var leaf: some View {
        Canvas { [dk] c, _ in var c = c; c.translateBy(x: 4, y: 9); ThemeArt.leaf(c, dk: dk) }.frame(width: 10, height: 10)
    }
    var note: some View {
        Text("♪").font(.system(size: 10, weight: .bold)).foregroundStyle(Color(hex: dk ? 0xFFE6A8 : 0xB07A00)).frame(width: 10, height: 18, alignment: .top)
    }
    var exit: some View { Canvas { c, _ in ThemeArt.miniExit(c) }.frame(width: 18, height: 22) }
    var gihun: some View { Dot(img: MugArt.gihun(MeChar.current ?? MeChar()), w: 16, h: 24) }
    /// 구면 뒤집기: 작은 구. ripple = 떨어진 날 테두리 물결, inside = 안쪽 면 색(목표 자리)
    func sphere(ripple: Bool = false, inside: Bool = false) -> some View {
        Canvas { [dk] c, _ in
            let col: UInt32 = inside ? (dk ? 0xE9C46A : 0xE2B44A) : (dk ? 0x4FB3A5 : 0x3E9C8F)
            var p = Path()
            for i in 0..<48 {
                let t = 2 * Double.pi * Double(i) / 48, r = 8 * (1 + (ripple ? 0.1 * sin(6 * t) : 0))
                let q = pt(9 + CGFloat(r * cos(t)), 9 + CGFloat(r * sin(t)))
                i == 0 ? p.move(to: q) : p.addLine(to: q)
            }
            p.closeSubpath()
            c.fill(p, with: hex(col))
            c.fill(circle(6.5, 6, 2.2), with: .color(.white.opacity(0.25)))
        }
        .frame(width: 18, height: 18)
    }
    var car: some View { Car().frame(width: 95, height: 35).scaleEffect(36.0 / 95).frame(width: 36, height: 13.3) }
    var fire: some View {
        VStack(alignment: .trailing, spacing: 1.6) {
            Capsule().fill(Color(hex: 0xFF7A1F, opacity: dk ? 0.85 : 0.9)).frame(width: 19.3, height: 2.1)
            Capsule().fill(Color(hex: 0xFFB84A, opacity: dk ? 0.85 : 0.9)).frame(width: 16.7, height: 2.1)
        }
        .frame(width: 20, height: 8)
    }
    var portal: some View {
        Ellipse().fill(EllipticalGradient(colors: [Color(hex: dk ? 0xE8F7FF : 0xFFFFFF), Color(hex: 0x5FC8FF, opacity: dk ? 0.8 : 0.85), Color(hex: 0x2A6CFF, opacity: 0)], center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5))
            .frame(width: 16, height: 30)
    }
    var glow: some View {
        let g = Color(hex: dk ? 0xF2C14E : 0xF2B42E)
        return ZStack {
            Circle().fill(g.opacity(dk ? 0.22 : 0.3)).frame(width: 20, height: 20)
            Circle().fill(g.opacity(dk ? 0.35 : 0.45)).frame(width: 13, height: 13)
            Circle().fill(Color(hex: dk ? 0xFFF4D6 : 0xE8A417)).frame(width: 8, height: 8)
                .overlay { if !dk { Circle().stroke(.white, lineWidth: 1.2) } }
        }
        .frame(width: 22, height: 22)
    }
    var wave: some View {
        Canvas { [dk] c, _ in
            var p = Path(); p.move(to: pt(1, 6))
            for i in 0..<3 { let x = 1 + CGFloat(i) * 6; p.addQuadCurve(to: pt(x + 6, 6), control: pt(x + 3, i % 2 == 0 ? 2 : 10)) }
            c.stroke(p, with: hex(dk, 0x1B5E96, 0x8CC4F2), lineWidth: 1.6)
        }
        .frame(width: 20, height: 10)
    }
    var sun: some View {
        Canvas { [dk] c, _ in
            c.fill(circle(12, 12, 11), with: dk ? hex(0xFFD98A, 0.22) : hex(0xF2B42E, 0.3))
            var h = Path(); h.addArc(center: pt(12, 12), radius: 6, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false); h.closeSubpath()
            c.fill(h, with: hex(dk, 0xE8A417, 0xFFD98A))
        }
        .frame(width: 24, height: 14)
    }
    var duck: some View { Duck().scaleEffect(0.56).frame(width: 26, height: 21) }
    var splash: some View {
        Canvas { [dk] c, _ in
            var a = Path(); a.move(to: pt(3, 4)); a.addQuadCurve(to: pt(3, 9), control: pt(5, 7)); a.addQuadCurve(to: pt(3, 4), control: pt(1, 7)); a.closeSubpath()
            var b = Path(); b.move(to: pt(9, 1)); b.addQuadCurve(to: pt(9, 5), control: pt(10.6, 3.4)); b.addQuadCurve(to: pt(9, 1), control: pt(7.4, 3.4)); b.closeSubpath()
            let col = hex(dk, 0x4C8CCB, 0x9EC1FF)
            c.fill(a, with: col); c.fill(b, with: col); c.fill(circle(7, 11, 1.4), with: col)
        }
        .frame(width: 12, height: 18)
    }
    var foam: some View {
        Canvas { [dk] c, _ in
            for (x, y, r) in [(5, 9, 4), (17, 9.5, 3.6), (11, 7, 5)] as [(CGFloat, CGFloat, CGFloat)] {
                c.fill(circle(x, y, r), with: hex(0xFFFFFF))
                if !dk { c.stroke(circle(x, y, r), with: hex(0x9FB7D8), lineWidth: 0.8) }
            }
            if dk { c.stroke(circle(11, 7, 5), with: hex(0xCFE0F5), lineWidth: 0.8) }
        }
        .frame(width: 22, height: 14)
    }
}

// MARK: 종목·흐름 바탕 · 몫 띠 · 분석 막대 (새 테마). 색은 라이트·다크 한 쌍

/// 박스 바탕 (새 테마 셋)
struct ThemeBackdrop: View {
    let look: BoxLook
    @Environment(\.self) private var env
    var body: some View {
        switch look {
        case .dory:
            LinearGradient(stops: [.init(color: bx(0x93CBEC, 0x0F1A35), location: 0), .init(color: bx(0xC4E3F6, 0x1B3866), location: 0.6), .init(color: bx(0xE6F3FC, 0x1F6AA3), location: 1)], startPoint: .top, endPoint: .bottom)
                .overlay { RadialGradient(colors: [bx(0xFFFFFF, 0.55, 0xBFE9FF, 0.22), bx(0xFFFFFF, 0, 0xBFE9FF, 0)], center: UnitPoint(x: 0.7, y: 1.2), startRadius: 0, endRadius: 200) }
        case .pino:
            LinearGradient(colors: [bx(0xEFDDC0, 0x22170E), bx(0xFBF2E3, 0x4A331D)], startPoint: .top, endPoint: .bottom)
                .overlay {
                    let plank = bx(0xB9915F, 0.4, 0x3A2816, 0.8).fixed(env)
                    Canvas { c, size in
                        var x: CGFloat = 69
                        while x < size.width { c.fill(Path(CGRect(x: x, y: 0, width: 1, height: size.height)), with: .color(plank)); x += 70 }
                    }
                }
                .overlay { RadialGradient(colors: [bx(0xFFCE78, 0.4, 0xFFD58A, 0.28), bx(0xFFCE78, 0, 0xFFD58A, 0)], center: UnitPoint(x: 0.1, y: 0.8), startRadius: 0, endRadius: 200) }
        case .mug:
            // 구면 뒤집기: 홈 박스와 같은 바탕 (소품 없이 고요하게)
            LinearGradient(colors: [bx(0xC9E3DF, 0x123A44), bx(0xF1E7CC, 0x4A3F24)], startPoint: .top, endPoint: .bottom)
        default:
            LinearGradient(colors: [bx(0xBCE0F8, 0x14304F), bx(0xE8F5FD, 0x2C5F8F)], startPoint: .top, endPoint: .bottom)
                .overlay(alignment: .bottom) { bx(0xE2C595, 0x8F7550).frame(height: 12) }
        }
    }
}

/// 새 테마 몫 띠·막대 테두리·바탕 색
enum ThemeBand {
    static let pinoShape = UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 3, bottomTrailingRadius: 6, topTrailingRadius: 6)
    static let wood = LinearGradient(stops: [.init(color: Color(hex: 0xECBC82), location: 0), .init(color: Color(hex: 0xD49A5E), location: 0.55), .init(color: Color(hex: 0xB47B44), location: 1)], startPoint: .top, endPoint: .bottom)
    static let woodFlat = bx(0x7A5634, 0x6B4A2B)
    static let glass = bx(0xFFFFFF, 0.6, 0x9FD6EE, 0.16), glassRim = bx(0x6FAFD8, 0xCFEFFF)
    static let chalk = bx(0xFFFFFF, 0xFBF6EA), dirt = bx(0xD9BB8C, 0x8F7550)
    static let dirtRim = bx(0xC4A374, 1, 0xC4A374, 0)
    static let noseDash = bx(0xB47B44, 0.6, 0xECBC82, 0.45)
    static let sphereTrack = bx(0xFFFFFF, 0.7, 0xFFFFFF, 0.16)
}

/// 분석 막대 (새 테마). fill = 지금 자리 0~1, 채운 만큼 노랑 (피노키오는 나무 코)
struct ThemeTrack: View {
    let look: BoxLook
    let f: CGFloat
    let on: Color
    var body: some View {
        GeometryReader { g in
            let w = g.size.width
            switch look {
            case .dory:
                ZStack(alignment: .leading) {
                    Capsule().fill(ThemeBand.glass)
                    Capsule().fill(on).frame(width: max(0, (w - 4) * f), height: 10).padding(.leading, 2)
                }
                .overlay(Capsule().strokeBorder(ThemeBand.glassRim, lineWidth: 1.5))
            case .pino:
                ZStack(alignment: .leading) {
                    ThemeBand.pinoShape.strokeBorder(ThemeBand.noseDash, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    ThemeBand.pinoShape.fill(ThemeBand.wood).frame(width: max(0, w * f))
                }
                .frame(height: 10).padding(.vertical, 2)
            case .mug:
                ZStack(alignment: .leading) {
                    Capsule().fill(ThemeBand.sphereTrack)
                    Capsule().fill(on).frame(width: max(0, w * f))
                }
                .frame(height: 8).padding(.vertical, 3)
            default:
                VStack(spacing: 0) {
                    ThemeBand.chalk.frame(height: 2)
                    ZStack(alignment: .leading) { ThemeBand.dirt; on.frame(width: max(0, w * f)) }
                        .frame(height: 6).padding(.vertical, 2).background(ThemeBand.dirt)
                    ThemeBand.chalk.frame(height: 2)
                }
                .overlay(Rectangle().stroke(ThemeBand.dirtRim, lineWidth: 1))
            }
        }
    }
}

// MARK: 홈 장면 3 구면 뒤집기 (2026-10-27, 철수와 영희 자리): 10권 P6. 큰 구 하나가 진행률만큼 안과 밖이 바뀐다
// 바깥 면 = 청록, 안쪽 면 = 호박색. 단계(E4): ① 굽이 ② 두 면이 서로를 통과 ③ 반환점(안도 밖도 아닌 네 잎) ④ 비틀리며 돌아 나옴 ⑤ 같은 구, 다른 면
// 0~5% 는 매끈한 구, 그 뒤 구간은 임시값 (나머지의 20 · 45 · 55 · 100%). 단계 안에서도 진행률 따라 조금씩 바뀐다. 움직임: 몇 초에 한 번 천천히, 지금 단계 안에서 뒤집기가 살짝 나아갔다 돌아온다 (회전 없음)
struct SphereScene: View {
    let s: BoxScene
    @State private var start: Date?
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.colorScheme) private var cs
    private static let cx: CGFloat = 225, cy: CGFloat = 86, R: CGFloat = 40

    var body: some View {
        let dk = cs == .dark, down = ThemeMood(s.today) == .down
        let dur = down ? 6.0 : 4.5
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: start == nil)) { tl in
            let p = start.map { min(1, max(0, tl.date.timeIntervalSince($0) / dur)) } ?? 0
            Canvas { ctx, _ in draw(ctx, dk: dk, down: down, p: p) }
        }
        .task(id: still) {
            start = nil
            guard !still else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(down ? 5 : 3.5))
                if Task.isCancelled { break }
                start = .now
                try? await Task.sleep(for: .seconds(dur))
                start = nil
            }
        }
    }

    /// p: 이번 움직임의 0~1 (쉬는 동안 0)
    private func draw(_ ctx: GraphicsContext, dk: Bool, down: Bool, p: Double) {
        let cx = Self.cx, cy = Self.cy, R = Self.R
        let f = min(1, max(0, s.f))
        let e = p * p * (3 - 2 * p)                 // 부드럽게 출발·정지
        let phi = 0.0                               // 돌리지 않는다. 움직임은 뒤집기 자체 (아래 g)
        let k: Double = down ? 0.85 : 1             // 떨어진 날은 덜 출렁인다
        let outC = Color(hex: dk ? 0x4FB3A5 : 0x3E9C8F), inC = Color(hex: dk ? 0xE9C46A : 0xE2B44A)
        let bg0 = Color(hex: dk ? 0x123A44 : 0xC9E3DF), bg1 = Color(hex: dk ? 0x4A3F24 : 0xF1E7CC)
        ctx.fill(Path(CGRect(x: 0, y: 0, width: 300, height: 170)), with: .linearGradient(Gradient(colors: [bg0, bg1]), startPoint: .zero, endPoint: pt(0, 170)))

        func blob(_ r: (Double) -> Double) -> Path {
            var path = Path()
            for i in 0..<96 {
                let t = 2 * Double.pi * Double(i) / 96, rr = R * CGFloat(r(t))
                let q = pt(cx + rr * CGFloat(cos(t)), cy + rr * CGFloat(sin(t)))
                i == 0 ? path.move(to: q) : path.addLine(to: q)
            }
            path.closeSubpath()
            return path
        }
        func shine(_ o: Double = 0.22) { ctx.fill(circle(cx - 0.3 * R, cy - 0.34 * R, 0.24 * R), with: .color(.white.opacity(o))) }
        // 처음 무늬 'ㄴ'. 다 뒤집히면 거울로 보인다 (바깥에서 뒷면을 보는 셈)
        func mark(_ mirror: Bool, _ o: Double = 1) {
            let sx: CGFloat = mirror ? -1 : 1, x0 = cx + sx * 0.14 * R, y0 = cy - 0.32 * R
            var m = Path(); m.move(to: pt(x0, y0)); m.addLine(to: pt(x0, y0 + 0.24 * R)); m.addLine(to: pt(x0 + sx * 0.2 * R, y0 + 0.24 * R))
            ctx.stroke(m, with: hex(0x14303A, o), style: StrokeStyle(lineWidth: 3.2, lineCap: .round, lineJoin: .round))
        }

        // 0~5% 는 매끈한 구. 그 뒤로는 진행률(g)에 따라 조금씩 이어서 바뀐다 (단계 사이 끊김 없음)
        // 움직일 때는 지금 단계 안에서만 뒤집기가 살짝 나아갔다가 돌아온다 (단계 폭의 15%, 다음 단계로 넘어가지 않음. 0~5% 매끈한 구는 그대로)
        let g0 = max(0, (f - 0.05) / 0.95)
        let hi = [0.2, 0.45, 0.55, 1].first { g0 < $0 } ?? 1, lo = [0, 0.2, 0.45, 0.55].last { g0 >= $0 } ?? 0
        let g = g0 > 0 ? min(hi - 0.001, g0 + 0.15 * (hi - lo) * sin(Double.pi * e)) : 0
        func sm(_ x: Double) -> Double { let c = min(1, max(0, x)); return c * c * (3 - 2 * c) }
        func mix(_ a: Double, _ b: Double, _ m: Double) -> Double { a + (b - a) * m }
        func wavy(_ t: Double, _ amp: Double, _ off: Double) -> Double { 1 + amp * k * sin(6 * t + phi + off) }
        func core(_ t: Double, _ base: Double, _ amp: Double, _ off: Double) -> Double { base + amp * k * sin(6 * t + Double.pi + off - phi) }
        func lobe(_ t: Double, _ sg: Double) -> Double { 0.4 + 0.6 * k * max(0, sg * cos(2 * t)) }

        if f >= 1 {
            // ⑤ 같은 구: 모양은 처음과 같고 향하는 면만 다르다
            ctx.fill(circle(cx, cy, R), with: .color(inC))
            shine(0.22 + 0.12 * sin(Double.pi * p)); mark(true)
        } else if g < 0.2 {
            // ① 굽이: 물결이 0에서 점점 커지고 조금씩 비틀린다. 안쪽 색이 스쳤다가 겹치기 직전 사라진다 (E5)
            let u = g / 0.2, off = u * Double.pi / 3
            ctx.fill(blob { wavy($0, 0.12 * u, off) }, with: .color(outC))
            if p > 0 && g > 0 {
                var gh = Path(); gh.move(to: pt(cx - 0.4 * R, cy - 0.56 * R)); gh.addQuadCurve(to: pt(cx - 0.12 * R, cy + 0.64 * R), control: pt(cx + 0.16 * R, cy - 0.2 * R))
                ctx.stroke(gh, with: .color(inC.opacity((0.15 + 0.3 * u) * sin(Double.pi * p))), style: StrokeStyle(lineWidth: 0.2 * R, lineCap: .round))
            }
            shine(0.22 * (1 - u)); mark(false, 1 - u)
        } else if g < 0.45 {
            // ② 통과: 안쪽 면이 가운데서 자라며 바깥 면과 반대로 돌아 지나간다
            let u = (g - 0.2) / 0.25, off = Double.pi / 3 + u * Double.pi / 6
            ctx.fill(blob { wavy($0, 0.12, off) }, with: .color(outC))
            ctx.fill(blob { core($0, 0.72 * u, 0.18 * u, off) }, with: .color(inC.opacity(0.8 * u)))
        } else if g < 0.55 {
            // ③ 반환점: ②의 끝 → 안도 밖도 아닌 네 잎 → ④의 시작(색만 바뀐 같은 꼴)
            let u = (g - 0.45) / 0.1, off = Double.pi / 2
            if u < 0.5 {
                let m = sm(u * 2)
                ctx.fill(blob { mix(wavy($0, 0.12, off), lobe($0, 1), m) }, with: .color(outC))
                ctx.fill(blob { mix(core($0, 0.72, 0.18, off), lobe($0, -1), m) }, with: .color(inC.opacity(mix(0.8, 1, m))))
            } else {
                let m = sm((u - 0.5) * 2)
                ctx.fill(blob { mix(lobe($0, -1), wavy($0, 0.12, off), m) }, with: .color(inC))
                ctx.fill(blob { mix(lobe($0, 1), core($0, 0.72, 0.18, off), m) }, with: .color(outC.opacity(mix(1, 0.8, m))))
            }
            let hole = 0.25 * R * CGFloat(sin(Double.pi * u))
            ctx.fill(circle(cx, cy, hole), with: .linearGradient(Gradient(colors: [bg0, bg1]), startPoint: pt(0, cy - hole), endPoint: pt(0, cy + hole)))
        } else {
            // ④ 돌아 나옴: 겉은 안쪽 색. 바깥 색 굽이가 비틀리며 줄어 100%에서 사라진다
            let u = (g - 0.55) / 0.45, off = Double.pi / 2 + u * Double.pi / 3
            ctx.fill(blob { wavy($0, 0.12 * (1 - u), off) }, with: .color(inC))
            ctx.fill(blob { core($0, 0.72 * (1 - u), 0.18 * (1 - u), off) }, with: .color(outC.opacity(0.8 * (1 - u))))
        }

        // 진행률: 구 아래 짧은 막대 + 오른쪽 숫자 (둘을 묶어 구 가운데에 맞춤, 반환점을 지나면 안쪽 색)
        let barC = f < 0.5 ? outC : inC, x0: CGFloat = 182, w: CGFloat = 56
        ctx.fill(Path(roundedRect: CGRect(x: x0, y: 147, width: w, height: 5), cornerRadius: 2.5), with: hex(0xFFFFFF, dk ? 0.16 : 0.7))
        ctx.fill(Path(roundedRect: CGRect(x: x0, y: 147, width: w * CGFloat(f), height: 5), cornerRadius: 2.5), with: .color(barC))
        Sk.text(ctx, AppModel.pct(f), x0 + w + 6, 149.5, Color(hex: dk ? 0xFFD98A : 0x2F5F59), size: 12, bold: true)
    }
}
