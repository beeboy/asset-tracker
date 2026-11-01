#if GYEOLPIP
import Foundation
#if canImport(CoreGraphics)
import CoreGraphics
#endif

// 결 PiP 그리기: player.js partSvg·FX와 같은 그림을 그리기 목록(GyeolOp)으로 만들고, CoreGraphics가 그대로 그린다.
// SVG 규칙을 따른다: 테두리를 먼저 긋고 그 위에 칠한다, 묶음 투명도는 묶음째로, 여러 길 오려내기는 합집합.

struct GPath {
    enum C { case m(Double, Double), l(Double, Double), c(Double, Double, Double, Double, Double, Double), q(Double, Double, Double, Double), z, oval(Double, Double, Double, Double) }
    var c: [C] = []
    var isEmpty: Bool { c.isEmpty }
    mutating func add(_ p: GPath) { c += p.c }
}

enum GyeolOp {
    case save, restore
    case clip(GPath)
    case group(Double), groupEnd                 // 묶음 투명도
    case fill(GPath, String, Double)             // 길, 색, 투명도
    case stroke(GPath, String, Double, Bool, [Double]?, Double)   // 길, 색, 굵기, 끝 둥글게, 점선, 투명도
    case move(Double, Double), turn(Double), scale(Double, Double)
    case rect(Double, Double, Double, Double, Double, String)    // x y w h 모서리 색
}

enum GyeolDraw {
    typealias T = GyeolBake.T
    static let ink = "#2b2233"

    // ---------- 채운 틀 읽기 (fillTpl 없이 자리 번호로 바로 읽는다) ----------
    static func get(_ t: T?, _ k: String) -> T? { if case .obj(let kv)? = t { return kv.first(where: { $0.0 == k })?.1 }; return nil }
    static func num(_ t: T?, _ n: [Float]) -> Double? {
        switch t { case .slot(let i)?: return i < n.count ? Double(n[i]) : 0; case .num(let v)?: return v; case .bool(let b)?: return b ? 1 : 0; default: return nil }
    }
    static func truthy(_ t: T?, _ n: [Float]) -> Bool {
        switch t { case nil, .null?: return false; case .bool(let b)?: return b; case .str(let s)?: return !s.isEmpty
        case .slot?, .num?: return (num(t, n) ?? 0) != 0; default: return true }
    }
    static func str(_ t: T?) -> String? { if case .str(let s)? = t, !s.isEmpty { return s }; return nil }
    static func arr(_ t: T?) -> [T] { if case .arr(let a)? = t { return a }; return [] }
    static func pts(_ t: T?, _ n: [Float]) -> [(Double, Double)] { arr(t).map { let a = arr($0); return (num(a.first, n) ?? 0, num(a.count > 1 ? a[1] : nil, n) ?? 0) } }

    // ---------- 길 ----------
    static func path(_ p: [(Double, Double)], closed: Bool = true, smooth: Bool = false) -> GPath {
        var g = GPath(); guard let f = p.first else { return g }
        g.c.append(.m(f.0, f.1))
        if !smooth { for q in p.dropFirst() { g.c.append(.l(q.0, q.1)) }; if closed { g.c.append(.z) }; return g }
        let n = p.count
        func at(_ i: Int) -> (Double, Double) { closed ? p[((i % n) + n) % n] : p[max(0, min(n - 1, i))] }
        for i in 0..<(closed ? n : n - 1) {
            let p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2)
            g.c.append(.c(p1.0 + (p2.0 - p0.0) / 6, p1.1 + (p2.1 - p0.1) / 6, p2.0 - (p3.0 - p1.0) / 6, p2.1 - (p3.1 - p1.1) / 6, p2.0, p2.1))
        }
        if closed { g.c.append(.z) }
        return g
    }
    static func lens(_ p: [(Double, Double)], _ w: Double) -> GPath {
        guard p.count >= 3 else { return GPath() }
        let (p0, c, p1) = (p[0], p[1], p[2])
        var nx = -(p1.1 - p0.1), ny = p1.0 - p0.0; let L = hypot(nx, ny) == 0 ? 1 : hypot(nx, ny); nx = nx / L * w; ny = ny / L * w
        return GPath(c: [.m(p0.0, p0.1), .q(c.0 + nx, c.1 + ny, p1.0, p1.1), .q(c.0 - nx, c.1 - ny, p0.0, p0.1), .z])
    }
    /// 오려내기 합집합: 도는 방향을 맞춰야 겹친 곳이 빠지지 않는다
    static func union(_ ps: [[(Double, Double)]], smooth: Bool) -> GPath {
        var g = GPath()
        for p in ps where !p.isEmpty {
            var a = 0.0; for i in 0..<p.count { let u = p[i], v = p[(i + 1) % p.count]; a += u.0 * v.1 - v.0 * u.1 }
            g.add(path(a < 0 ? p.reversed() : p, closed: true, smooth: smooth))
        }
        return g
    }

    // ---------- 부품 하나 (partSvg) ----------
    static func part(_ t: T, _ n: [Float], into o: inout [GyeolOp]) {
        let pop = num(get(t, "op"), n)
        let po = pop.map { $0 < 1 } ?? false
        if po { if pop! <= 0.01 { return }; o.append(.group(pop!)) }
        var clip0: GPath?
        for (li, L) in arr(get(t, "layers")).enumerated() {
            let op = num(get(L, "op"), n) ?? 1
            if op <= 0.01 { continue }
            let smooth = truthy(get(L, "smooth"), n)
            let polys = arr(get(L, "polys")).map { pts($0, n) }
            let ds = polys.map { path($0, closed: true, smooth: smooth) }
            var cl: GPath?
            if let c = get(L, "clip"), num(c, n) == 0, li > 0, let c0 = clip0 { cl = c0 }
            if let cp = get(L, "clipPoly") { cl = union([pts(cp, n)], smooth: false) }
            if let cps = get(L, "clipPolys") { cl = union(arr(cps).map { pts($0, n) }, smooth: false) }
            if li == 0, !polys.isEmpty { clip0 = union(polys, smooth: smooth) }
            o.append(.save)
            if let cl { o.append(.clip(cl)) }
            if op < 1 { o.append(.group(op)) }
            if let w = num(get(L, "ink"), n), w != 0 { for d in ds { o.append(.stroke(d, ink, w * 2, false, nil, 1)) } }
            let fill = str(get(L, "fill"))
            if let fill {
                if truthy(get(L, "clipLast"), n), let first = ds.first, let last = ds.last {
                    o.append(.save); o.append(.clip(last)); o.append(.fill(first, fill, 1)); o.append(.restore)
                } else { for d in ds { o.append(.fill(d, fill, 1)) } }
            }
            if let shade = str(get(L, "shade")) {
                o.append(.save); o.append(.clip(union(polys, smooth: smooth)))
                o.append(.rect(-40, -40, 140, 160, 0, shade))
                o.append(.move(-0.95, -0.8)); for d in ds { o.append(.fill(d, fill ?? "#000000", 1)) }
                o.append(.restore)
            }
            for k in arr(get(L, "inks")) {
                let kop = num(get(k, "op"), n) ?? 1
                if kop <= 0.01 { continue }
                let w = num(get(k, "w"), n) ?? 1
                if case .arr? = get(k, "lens") { o.append(.fill(lens(pts(get(k, "lens"), n), w), ink, kop)) }
                else { o.append(.stroke(path(pts(get(k, "pts"), n), closed: truthy(get(k, "closed"), n), smooth: truthy(get(k, "smooth"), n)), ink, w, true, nil, kop)) }
            }
            if op < 1 { o.append(.groupEnd) }
            o.append(.restore)
        }
        if po { o.append(.groupEnd) }
    }

    // ---------- 머리 옆 부호 (FX) ----------
    enum FxOp { case ink(GPath, Double, [Double]?), fill(String, GPath) }
    static let heartD = "M0,1.6C-2.6,-.2 -2.4,-2.6 -1.1,-2.6C-.4,-2.6 0,-2.1 0,-1.6C0,-2.1 .4,-2.6 1.1,-2.6C2.4,-2.6 2.6,-.2 0,1.6Z", dropD = "M0,-1.8Q1.1,-.2 1.1,.6Q1.1,1.6 0,1.6Q-1.1,1.6 -1.1,.6Q-1.1,-.2 0,-1.8Z"
    static let fxShapes: [String: [FxOp]] = {
        let P = GyeolDraw.svg, ink = GyeolDraw.ink, c = { (x: Double, y: Double, r: Double) in GPath(c: [.oval(x, y, r, r)]) }
        return [
            "q": [.ink(P("M-1.3,-1.6Q-1.3,-3.2 0,-3.2Q1.5,-3.2 1.5,-1.8Q1.5,-.9 .5,-.4Q0,-.1 0,.7"), 0.55, nil), .fill(ink, c(0, 2, 0.42))],
            "ex": [.fill(ink, P("M-.55,-3.4L.55,-3.4L.3,.6L-.3,.6Z")), .fill(ink, c(0, 1.9, 0.5))],
            "note": [.fill(ink, P("M-.2,-3.2L.35,-3.2L.35,.9A1.1,.85 -20 1 1 -.2,.5Z")), .ink(P("M.35,-3.2Q1.9,-2.6 1.6,-1.2"), 0.4, nil)],
            "heart": [.fill("#f06b8a", P(heartD)), .ink(P(heartD), 0.32, nil)],
            "sweat": [.fill("#bfe3f7", P(dropD)), .ink(P(dropD), 0.3, nil), .fill("#ffffff", GPath(c: [.oval(-0.35, 0.55, 0.22, 0.4)]))],
            "spark": [.fill(ink, P("M0,-1.6Q.18,-.18 1.6,0Q.18,.18 0,1.6Q-.18,.18 -1.6,0Q-.18,-.18 0,-1.6Z"))],
            "up": [.ink(P("M0,1.5L0,-1.5M-1,-.5L0,-1.5L1,-.5"), 0.42, nil)],
            "pop": [.ink(P("M-1.4,-.8L-2.6,-1.6M0,-1.3L0,-2.7M1.4,-.8L2.6,-1.6"), 0.4, nil)],
            "gloom": [.ink(P("M-1.6,-1.6L-1.6,1.4M0,-1.9L0,1.6M1.6,-1.6L1.6,1.4"), 0.3, [0.9, 0.5])],
            "puff": [.ink(P("M-1.8,0Q-1,-1 0,-.4Q1,.2 1.8,-.6"), 0.38, nil), .ink(P("M-1,1.3Q-.3,.6 .5,1Q1.1,1.4 1.6,1"), 0.3, nil)],
            "zz": [.ink(P("M-1,-1H1L-1,1H1"), 0.38, nil)],
            "speed": [.ink(P("M-2,0L2,0M-1.4,1.4L1.4,1.4M-1.4,-1.4L1.4,-1.4"), 0.3, nil)],
        ]
    }()
    static func fx(_ list: [GyeolFx], into o: inout [GyeolOp]) {
        for f in list {
            guard f.v.count >= 5, let shapes = fxShapes[f.kind] else { continue }
            let (x, y, s, rot, op) = (f.v[0], f.v[1], f.v[2], f.v[3], f.v[4])
            if op <= 0.02 { continue }
            o.append(.save); o.append(.move(x, y)); o.append(.turn(rot)); let k = s * (0.75 + 0.25 * op); o.append(.scale(k, k))
            if op < 1 { o.append(.group(op)) }
            for sh in shapes {
                switch sh { case .ink(let p, let w, let dash): o.append(.stroke(p, ink, w, true, dash, 1)); case .fill(let col, let p): o.append(.fill(p, col, 1)) }
            }
            if op < 1 { o.append(.groupEnd) }
            o.append(.restore)
        }
    }

    /// 작은 SVG 길 읽기 (M L H V C Q A Z, 절대 좌표만)
    static func svg(_ d: String) -> GPath {
        var toks: [String] = [], cur = ""
        for ch in d {
            if ch.isLetter { if !cur.isEmpty { toks.append(cur); cur = "" }; toks.append(String(ch)) }
            else if ch == "," || ch == " " { if !cur.isEmpty { toks.append(cur); cur = "" } }
            else if ch == "-" { if !cur.isEmpty && cur.last != "e" { toks.append(cur); cur = "" }; cur.append(ch) }
            else if ch == "." && cur.contains(".") { toks.append(cur); cur = "."; }
            else { cur.append(ch) }
        }
        if !cur.isEmpty { toks.append(cur) }
        var g = GPath(), i = 0, cmd = "M", x = 0.0, y = 0.0
        func n() -> Double { defer { i += 1 }; return i < toks.count ? Double(toks[i]) ?? 0 : 0 }
        while i < toks.count {
            if let c = toks[i].first, c.isLetter { cmd = String(c); i += 1; if cmd == "Z" { g.c.append(.z); continue } }
            switch cmd {
            case "M": x = n(); y = n(); g.c.append(.m(x, y)); cmd = "L"
            case "L": x = n(); y = n(); g.c.append(.l(x, y))
            case "H": x = n(); g.c.append(.l(x, y))
            case "V": y = n(); g.c.append(.l(x, y))
            case "C": let a = n(), b = n(), c = n(), d = n(); x = n(); y = n(); g.c.append(.c(a, b, c, d, x, y))
            case "Q": let a = n(), b = n(); x = n(); y = n(); g.c.append(.q(a, b, x, y))
            case "A": let rx = n(), ry = n(), rot = n(), la = n(), sw = n(), x2 = n(), y2 = n()
                g.c += arc(x, y, rx, ry, rot, la != 0, sw != 0, x2, y2); x = x2; y = y2
            default: i += 1
            }
        }
        return g
    }
    /// SVG 호 → 3차 곡선
    static func arc(_ x1: Double, _ y1: Double, _ rx0: Double, _ ry0: Double, _ deg: Double, _ large: Bool, _ sweep: Bool, _ x2: Double, _ y2: Double) -> [GPath.C] {
        var rx = abs(rx0), ry = abs(ry0); if rx == 0 || ry == 0 { return [.l(x2, y2)] }
        let ph = deg * .pi / 180, cp = cos(ph), sp = sin(ph)
        let dx = (x1 - x2) / 2, dy = (y1 - y2) / 2, x1p = cp * dx + sp * dy, y1p = -sp * dx + cp * dy
        let lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry); if lam > 1 { rx *= sqrt(lam); ry *= sqrt(lam) }
        let num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p, den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        let co = (large == sweep ? -1.0 : 1.0) * sqrt(max(0, num / den))
        let cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx
        let cx = cp * cxp - sp * cyp + (x1 + x2) / 2, cy = sp * cxp + cp * cyp + (y1 + y2) / 2
        func ang(_ ux: Double, _ uy: Double, _ vx: Double, _ vy: Double) -> Double { atan2(ux * vy - uy * vx, ux * vx + uy * vy) }
        let t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && dt > 0 { dt -= 2 * .pi } else if sweep && dt < 0 { dt += 2 * .pi }
        let segs = max(1, Int(ceil(abs(dt) / (.pi / 2)))), h = dt / Double(segs), k = 4.0 / 3 * tan(h / 4)
        var out: [GPath.C] = [], t = t1
        func pt(_ a: Double) -> (Double, Double) { (cx + rx * cos(a) * cp - ry * sin(a) * sp, cy + rx * cos(a) * sp + ry * sin(a) * cp) }
        func dv(_ a: Double) -> (Double, Double) { (-rx * sin(a) * cp - ry * cos(a) * sp, -rx * sin(a) * sp + ry * cos(a) * cp) }
        for _ in 0..<segs {
            let a = pt(t), b = pt(t + h), da = dv(t), db = dv(t + h)
            out.append(.c(a.0 + k * da.0, a.1 + k * da.1, b.0 - k * db.0, b.1 - k * db.1, b.0, b.1)); t += h
        }
        return out
    }

    // ---------- 장면 하나 ----------
    /// 결 창 한 장. 단위는 결의 자리 viewBox(60×64)와 같고, 창은 W×H 단위다
    struct Look {
        var w = 120.0, h = 64.0
        var breath = 0.0         // 숨 (0~1)
        var sit = true
        var bg = "#f3f5f1"
        var ledge = "#d7ddd6"
        var figX: Double { w - 62 }   // 결은 늘 오른쪽, 말풍선은 그 왼쪽
    }
    static func scene(_ s: GyeolStage.Sample, _ bk: GyeolBake, _ look: Look) -> [GyeolOp] {
        var o: [GyeolOp] = [.rect(0, 0, look.w, look.h, 0, look.bg)]
        let fx = look.figX
        if look.sit { o.append(.rect(fx + 30 - 37.5, 46.4, 75, 1.67, 0.83, look.ledge)) }   // 걸터앉은 턱
        o.append(.save); o.append(.move(fx, 0))
        // 숨: 앉으면 엉덩이, 서면 발끝을 축으로 아주 조금 부푼다
        let ax = 30.0, ay = look.sit ? 47.0 : 61.5
        o.append(.move(ax, ay)); o.append(.scale(1 + 0.004 * look.breath, 1 + 0.012 * look.breath)); o.append(.move(-ax, -ay))
        for id in s.order { if let p = s.parts[id] { part(bk.template(p.s).t, p.n, into: &o) } }
        Self.fx(s.fx, into: &o)
        o.append(.restore)
        return o
    }

    #if canImport(CoreGraphics)
    // ---------- CoreGraphics로 그리기 ----------
    nonisolated(unsafe) private static var colors: [String: CGColor] = [:]
    static func color(_ s: String) -> CGColor {
        if let c = colors[s] { return c }
        var h = s.hasPrefix("#") ? String(s.dropFirst()) : s
        if h.count == 3 { h = h.map { "\($0)\($0)" }.joined() }
        let v = UInt32(h.prefix(6), radix: 16) ?? 0
        let c = CGColor(srgbRed: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: 1)
        colors[s] = c; return c
    }
    static func cg(_ g: GPath) -> CGPath {
        let p = CGMutablePath()
        for c in g.c {
            switch c {
            case .m(let x, let y): p.move(to: CGPoint(x: x, y: y))
            case .l(let x, let y): p.addLine(to: CGPoint(x: x, y: y))
            case .c(let a, let b, let c, let d, let x, let y): p.addCurve(to: CGPoint(x: x, y: y), control1: CGPoint(x: a, y: b), control2: CGPoint(x: c, y: d))
            case .q(let a, let b, let x, let y): p.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: a, y: b))
            case .z: p.closeSubpath()
            case .oval(let x, let y, let rx, let ry): p.addEllipse(in: CGRect(x: x - rx, y: y - ry, width: rx * 2, height: ry * 2))
            }
        }
        return p
    }
    static func run(_ ops: [GyeolOp], in cx: CGContext) {
        for op in ops {
            switch op {
            case .save: cx.saveGState()
            case .restore: cx.restoreGState()
            case .clip(let g): cx.addPath(cg(g)); cx.clip()
            case .group(let a): cx.saveGState(); cx.setAlpha(a); cx.beginTransparencyLayer(auxiliaryInfo: nil)
            case .groupEnd: cx.endTransparencyLayer(); cx.restoreGState()
            case .fill(let g, let col, let a):
                cx.saveGState(); if a < 1 { cx.setAlpha(a) }
                cx.setFillColor(color(col)); cx.addPath(cg(g)); cx.fillPath(); cx.restoreGState()
            case .stroke(let g, let col, let w, let round, let dash, let a):
                cx.saveGState(); if a < 1 { cx.setAlpha(a) }
                cx.setStrokeColor(color(col)); cx.setLineWidth(w); cx.setLineJoin(.round); cx.setLineCap(round ? .round : .butt)
                if let dash { cx.setLineDash(phase: 0, lengths: dash.map { CGFloat($0) }) }
                cx.addPath(cg(g)); cx.strokePath(); cx.restoreGState()
            case .move(let x, let y): cx.translateBy(x: x, y: y)
            case .turn(let d): cx.rotate(by: d * .pi / 180)
            case .scale(let x, let y): cx.scaleBy(x: x, y: y)
            case .rect(let x, let y, let w, let h, let r, let col):
                cx.setFillColor(color(col))
                if r > 0 { cx.addPath(CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil)); cx.fillPath() }
                else { cx.fill(CGRect(x: x, y: y, width: w, height: h)) }
            }
        }
    }
    #endif

    #if DEBUG
    /// 시험용: 그리기 목록을 SVG로 (결의 자리 그림과 나란히 보려고)
    static func svgText(_ ops: [GyeolOp]) -> String {
        func d(_ g: GPath) -> String { g.c.map { c -> String in switch c {
            case .m(let x, let y): return "M\(x),\(y)"; case .l(let x, let y): return "L\(x),\(y)"
            case .c(let a, let b, let c, let e, let x, let y): return "C\(a),\(b) \(c),\(e) \(x),\(y)"
            case .q(let a, let b, let x, let y): return "Q\(a),\(b) \(x),\(y)"; case .z: return "Z"
            case .oval(let x, let y, let rx, let ry): return "M\(x - rx),\(y)A\(rx),\(ry) 0 1 0 \(x + rx),\(y)A\(rx),\(ry) 0 1 0 \(x - rx),\(y)Z" } }.joined() }
        var s = "", cid = 0
        // 상태 쌓기: save는 <g>를 열고, clip·move 같은 것은 새 <g>로 감싼다
        var opened: [Int] = [0]
        func open(_ attr: String) { s += "<g \(attr)>"; opened[opened.count - 1] += 1 }
        for op in ops {
            switch op {
            case .save: opened.append(0)
            case .restore: s += String(repeating: "</g>", count: opened.removeLast())
            case .clip(let g): cid += 1; s += "<clipPath id=\"c\(cid)\"><path d=\"\(d(g))\"/></clipPath>"; open("clip-path=\"url(#c\(cid))\"")
            case .group(let a): opened.append(0); open("opacity=\"\(a)\"")
            case .groupEnd: s += String(repeating: "</g>", count: opened.removeLast())
            case .fill(let g, let c, let a): s += "<path d=\"\(d(g))\" fill=\"\(c)\" opacity=\"\(a)\"/>"
            case .stroke(let g, let c, let w, let r, let dash, let a): s += "<path d=\"\(d(g))\" fill=\"none\" stroke=\"\(c)\" stroke-width=\"\(w)\" stroke-linejoin=\"round\" stroke-linecap=\"\(r ? "round" : "butt")\"\(dash.map { " stroke-dasharray=\"\($0.map { "\($0)" }.joined(separator: " "))\"" } ?? "") opacity=\"\(a)\"/>"
            case .move(let x, let y): open("transform=\"translate(\(x) \(y))\"")
            case .turn(let r): open("transform=\"rotate(\(r))\"")
            case .scale(let x, let y): open("transform=\"scale(\(x) \(y))\"")
            case .rect(let x, let y, let w, let h, let r, let c): s += "<rect x=\"\(x)\" y=\"\(y)\" width=\"\(w)\" height=\"\(h)\" rx=\"\(r)\" fill=\"\(c)\"/>"
            }
        }
        return s + String(repeating: "</g>", count: opened.reduce(0, +))
    }
    #endif
}
#endif
