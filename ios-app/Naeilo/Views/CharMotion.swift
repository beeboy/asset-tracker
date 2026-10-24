import SwiftUI
import UIKit

// 인물 움직임 (GBA 젤다처럼 살짝 위에서 본 3/4 시점).
// 그림 파일을 더 넣지 않고, 이미 있는 24×34 버퍼(내 캐릭터·주민·샘플)나 친구 포즈 그림(mo_*)에서
// 방향·걷기·숨쉬기 장면을 픽셀로 만들어 캐시한다. 화면은 작은 그림만 바꿔 끼우니 매 프레임 다시 그리지 않는다.

/// 보는 방향: 아래(정면)·위(뒷모습)·왼·오른
enum CharDir: Int, CaseIterable, Hashable {
    case down, up, left, right
    /// 움직인 쪽으로 돌아선다
    static func of(_ dx: CGFloat, _ dy: CGFloat) -> CharDir {
        abs(dx) >= abs(dy) ? (dx < 0 ? .left : .right) : (dy < 0 ? .up : .down)
    }
    /// 돌리기: 아래 → 왼 → 위 → 오른 (k = -1 이면 거꾸로)
    func turned(_ k: Int) -> CharDir {
        let ring: [CharDir] = [.down, .left, .up, .right]
        let i = ring.firstIndex(of: self) ?? 0
        return ring[(i + k + 4) % 4]
    }
}

/// 한 장면: 방향 + 다리(0 서기 · 1 왼발 · 2 오른발 1px 들기) + 윗몸 1px 들썩(걷기·숨쉬기)
struct CharScene: Hashable {
    var dir: CharDir = .down
    var leg = 0
    var lift = false
    /// 머리 위 빈 2줄: 들썩여도 머리 윗줄·테두리가 잘리지 않게 (SceneSprite 로 그리면 틀 위로 넘친다)
    var room = false
    /// 파도 타기 포즈 (기본은 그대로)
    var pose: CharPose = .none
    /// k 번째 칸: 걸을 땐 서기·왼발·서기·오른발, 서 있을 땐 숨 두 장
    static func at(_ k: Int, dir: CharDir, walking: Bool) -> CharScene {
        walking ? CharScene(dir: dir, leg: [0, 1, 0, 2][k % 4], lift: k % 2 == 1, room: true)
                : CharScene(dir: dir, leg: 0, lift: k % 2 == 1, room: true)
    }
}

/// 파도 타기 포즈: 정면 버퍼(테두리 전)에서 눈·팔·다리만 몇 칸 바꾼다. 새 그림 없음
enum CharPose: Int, Hashable {
    /// crouch 무릎 굽힘 · cheer 팔 벌리고 ^^ · dizzy 눈 ×× · yawn 보드에 앉아 하품
    case none, crouch, cheer, dizzy, yawn
    /// 다리를 접은 줄 수 (모자·왕관을 같이 내린다)
    var sink: Int { self == .crouch ? 2 : self == .yawn ? 4 : 0 }

    static func apply(_ pose: CharPose, _ p: inout [UInt32], _ w: Int, _ h: Int, skin: UInt32, legTop: Int) {
        let ink = PixBuf.rgb(MeArt.outline), sk = PixBuf.rgb(skin)
        func put(_ x: Int, _ y: Int, _ c: UInt32) { if x >= 0, x < w, y >= 0, y < h { p[y * w + x] = c } }
        /// 눈 두 개를 피부로 지우고 가운데 자리를 돌려준다 (새 눈은 거기 그린다)
        func eyes() -> [(Int, Int)] {
            var cs: [(Int, Int)] = []
            for xs in [6..<12, 12..<19] {
                var pts: [(Int, Int)] = []
                for y in 8...12 { for x in xs where PixBuf.on(p[y * w + x]) && PixBuf.luma(p[y * w + x]) < 70 { pts.append((x, y)) } }
                guard !pts.isEmpty else { continue }
                let x0 = pts.map(\.0).min()!, x1 = pts.map(\.0).max()!, y0 = pts.map(\.1).min()!, y1 = pts.map(\.1).max()!
                for y in y0...y1 { for x in x0...x1 where PixBuf.on(p[y * w + x]) { p[y * w + x] = sk } }
                cs.append(((x0 + x1) / 2, (y0 + y1 + 1) / 2))
            }
            return cs
        }
        /// 다리 n줄을 접어 몸을 n줄 내린다
        func fold(_ n: Int) {
            let r = legTop + 1
            for y in stride(from: r + n - 1, through: n, by: -1) { for x in 0..<w { p[y * w + x] = p[(y - n) * w + x] } }
            for i in 0..<(n * w) { p[i] = 0 }
        }
        switch pose {
        case .none: return
        case .crouch: fold(2)
        case .cheer:
            for (x, y) in eyes() { put(x - 1, y, ink); put(x, y - 1, ink); put(x + 1, y, ink) }
            // 팔: 어깨에서 바깥 위로 세 칸 + 손
            let y = 17, xs = (0..<w).filter { PixBuf.on(p[y * w + $0]) }
            if let l = xs.first, let r = xs.last {
                let shirt = p[y * w + l]
                for k in 1...3 { put(l - k, y - k, k < 3 ? shirt : sk); put(r + k, y - k, k < 3 ? shirt : sk) }
                put(l - 3, y - 4, sk); put(r + 3, y - 4, sk)
            }
        case .dizzy:
            for (x, y) in eyes() { for d in -1...1 { put(x + d, y + d, ink); put(x + d, y - d, ink) } }
        case .yawn:
            for (x, y) in eyes() { for d in -1...1 { put(x + d, y, ink) } }
            for x in 11...12 where PixBuf.on(p[13 * w + x]) { p[13 * w + x] = PixBuf.rgb(0x9A3B3B) }
            fold(4)
        }
    }
}

/// RGBA 버퍼(한 칸 UInt32, 메모리 순서 R·G·B·A, 위 줄부터) 다루기
enum PixBuf {
    static func on(_ v: UInt32) -> Bool { v >> 24 != 0 }
    static func rgb(_ hex: UInt32) -> UInt32 { 0xFF00_0000 | (hex & 0xFF) << 16 | (hex & 0xFF00) | (hex >> 16 & 0xFF) }
    private static func parts(_ v: UInt32) -> (Double, Double, Double) { (Double(v & 0xFF), Double(v >> 8 & 0xFF), Double(v >> 16 & 0xFF)) }
    private static func make(_ r: Double, _ g: Double, _ b: Double, _ a: UInt32) -> UInt32 {
        func c(_ x: Double) -> UInt32 { UInt32(max(0, min(255, x.rounded()))) }
        return a << 24 | c(b) << 16 | c(g) << 8 | c(r)
    }
    /// k배 어둡게 (k<1)
    static func dark(_ v: UInt32, _ k: Double) -> UInt32 { let (r, g, b) = parts(v); return make(r * k, g * k, b * k, v >> 24) }
    /// 색 쪽으로 t만큼 섞기
    static func mix(_ v: UInt32, _ hex: UInt32, _ t: Double) -> UInt32 {
        let (r, g, b) = parts(v), (R, G, B) = parts(rgb(hex))
        return make(r + (R - r) * t, g + (G - g) * t, b + (B - b) * t, v >> 24)
    }
    static func luma(_ v: UInt32) -> Double { let (r, g, b) = parts(v); return 0.3 * r + 0.59 * g + 0.11 * b }
    static func near(_ v: UInt32, _ hex: UInt32) -> Bool {
        let (r, g, b) = parts(v), (R, G, B) = parts(rgb(hex))
        return abs(r - R) + abs(g - G) + abs(b - B) < 30
    }

    /// 음영: 위에서 오는 빛. 머리 윗면 밝게·오른쪽 어둡게, 윗옷 왼쪽 1열 밝게·오른쪽 2열 어둡게, 다리 오른쪽 1열 어둡게
    static func shade(_ p: inout [UInt32], _ w: Int, _ h: Int, head: Int = 16, legTop: Int = 28) {
        for x in 0..<w {
            if let y = (0..<min(head, h)).first(where: { on(p[$0 * w + x]) }) { p[y * w + x] = mix(p[y * w + x], 0xFFFFFF, 0.28) }
        }
        for y in 0..<h {
            let xs = (0..<w).filter { on(p[y * w + $0]) }
            guard let l = xs.first, let r = xs.last, r - l >= 3 else { continue }
            if y < head {
                p[y * w + r] = dark(p[y * w + r], 0.78)
            } else if y > head && y < legTop {
                p[y * w + l] = mix(p[y * w + l], 0xFFFFFF, 0.2)
                for x in [r, r - 1] where on(p[y * w + x]) { p[y * w + x] = dark(p[y * w + x], 0.8) }
            } else if y >= legTop {
                p[y * w + r] = dark(p[y * w + r], 0.8)
            }
        }
    }
    /// 다리 한쪽(1 왼쪽 반 · 2 오른쪽 반)을 1px 들어 올린다 (발이 올라오고 다리는 1px 짧아짐)
    static func legs(_ p: inout [UInt32], _ w: Int, _ h: Int, _ leg: Int, top: Int, split: Int) {
        guard leg > 0 else { return }
        let xs = leg == 1 ? 0..<split : split..<w
        for x in xs {
            for y in (top + 1)..<(h - 1) { p[y * w + x] = p[(y + 1) * w + x] }
            p[(h - 1) * w + x] = 0
        }
    }
    /// 맨 위에 빈 줄 n개를 붙인다
    static func pad(_ p: inout [UInt32], _ w: Int, top n: Int) { p.insert(contentsOf: repeatElement(0, count: n * w), at: 0) }
    /// 윗몸(허리 위)을 1px 올린다. 허리 줄은 한 줄 늘어난다
    static func lift(_ p: inout [UInt32], _ w: Int, until waist: Int) {
        for y in 0..<(waist - 1) { for x in 0..<w { p[y * w + x] = p[(y + 1) * w + x] } }
    }
    static func mirror(_ p: inout [UInt32], _ w: Int, _ h: Int) {
        for y in 0..<h { for x in 0..<(w / 2) { p.swapAt(y * w + x, y * w + w - 1 - x) } }
    }
    /// 바깥 테두리 한 줄 (MeArt 와 같은 규칙: 빈 칸 중 상하좌우에 칠한 칸이 있으면 테두리색)
    static func outline(_ p: inout [UInt32], _ w: Int, _ h: Int) {
        let line = rgb(MeArt.outline), f = p.map(on)
        for y in 0..<h {
            for x in 0..<w where !f[y * w + x] {
                if (x > 0 && f[y * w + x - 1]) || (x < w - 1 && f[y * w + x + 1]) || (y > 0 && f[(y - 1) * w + x]) || (y < h - 1 && f[(y + 1) * w + x]) {
                    p[y * w + x] = line
                }
            }
        }
    }
    static func read(_ cg: CGImage, _ w: Int, _ h: Int, draw: ((CGContext) -> Void)? = nil) -> [UInt32] {
        var p = [UInt32](repeating: 0, count: w * h)
        p.withUnsafeMutableBytes { b in
            guard let ctx = CGContext(data: b.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return }
            ctx.interpolationQuality = .none
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        return p
    }
    static func image(_ p: [UInt32], _ w: Int, _ h: Int) -> UIImage? {
        var p = p
        let cg: CGImage? = p.withUnsafeMutableBytes { b in
            CGContext(data: b.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)?.makeImage()
        }
        return cg.map { UIImage(cgImage: $0) }
    }
}

/// 내 캐릭터·주민·샘플: 부품을 24×34 에 겹친 뒤 방향·음영·볼·눈 반짝·다리·숨을 입힌다
enum MeMotion {
    private struct Key: Hashable { let me: MeChar; let f: CharScene }
    private static var cache: [Key: UIImage] = [:]
    /// 얼굴 부품: 뒷모습에선 뺀다. 옆모습은 부품마다 옆모습 그림(_side, tools/gen_me_side.py)을 쓴다 (없으면 보는 쪽으로 1px)
    private static let facial: Set<String> = ["eyes", "mouth", "mask", "glasses"]
    static let legTop = 28

    static func image(_ me: MeChar, _ f: CharScene = CharScene()) -> UIImage? {
        let key = Key(me: me, f: f)
        if let c = cache[key] { return c }
        let w = MeArt.W, h = MeArt.H
        let hair = MeColor.hair[MeColor.idx(me)], skin = MeColor.skin[MeColor.skinIdx(me)]
        let side = f.dir == .left || f.dir == .right
        var p = [UInt32](repeating: 0, count: w * h)
        p.withUnsafeMutableBytes { b in
            guard let ctx = CGContext(data: b.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return }
            ctx.interpolationQuality = .none
            for layer in MeGroup.layers {
                let n = MeArt.option(me, layer)
                guard n > 0, !(f.dir == .up && facial.contains(layer)) else { continue }
                let name = MeArt.asset(layer, n, me), sideArt = side && MeArt.has(name + "_side")
                let dx = side && !sideArt && facial.contains(layer) ? 1 : 0
                if let img = UIImage(named: sideArt ? name + "_side" : name)?.cgImage {
                    ctx.draw(img, in: CGRect(x: dx, y: 0, width: w, height: h))
                } else {
                    MePlaceholder.draw(layer, n, hair, skin) { x, y, pw, ph, c in
                        ctx.setFillColor(red: CGFloat((c >> 16) & 0xFF) / 255, green: CGFloat((c >> 8) & 0xFF) / 255, blue: CGFloat(c & 0xFF) / 255, alpha: 1)
                        ctx.fill(CGRect(x: x + dx, y: h - y - ph, width: pw, height: ph))
                    }
                }
            }
        }
        if f.dir == .up {
            // 뒷모습: 머리(0~13줄)만 머리색으로 (머리 부품이 없는 유명인은 피부를 살짝 어둡게). 목(14줄)·윗옷(15줄~)은 그대로
            let back = me.part(.hair) > 0 ? hair : skin, c = PixBuf.rgb(back)
            // 긴 머리는 등 뒤로 내려오니 그 줄의 머리 사이(윗옷 위)도 머리색으로 메운다
            for y in 14..<min(h, 24) {
                let xs = (0..<w).filter { PixBuf.near(p[y * w + $0], hair) }
                guard me.part(.hair) > 0, let a = xs.first, let b = xs.last, b - a > 3 else { continue }
                for x in a...b where PixBuf.on(p[y * w + x]) { p[y * w + x] = c }
            }
            for y in 0...13 { for x in 0..<w where PixBuf.on(p[y * w + x]) {
                p[y * w + x] = y >= 12 ? PixBuf.dark(c, 0.85) : c    // 목덜미 쪽 두 줄은 한 단 어둡게
            } }
            // 귀 살짝 (머리 양옆 9~10줄), 정수리 밝은 점 몇 개
            for y in 9...10 { for x in [5, 18] where PixBuf.on(p[y * w + x]) { p[y * w + x] = PixBuf.dark(PixBuf.rgb(skin), 0.9) } }
            for (x, y) in [(8, 3), (9, 3), (10, 3), (8, 5)] where PixBuf.on(p[y * w + x]) { p[y * w + x] = PixBuf.mix(c, 0xFFFFFF, 0.3) }
        } else {
            // 볼 분홍 1px (옆모습은 앞쪽 볼 하나)
            for x in side ? [16] : [8, 15] where PixBuf.near(p[12 * w + x], skin) { p[12 * w + x] = PixBuf.mix(p[12 * w + x], 0xF08C8C, 0.5) }
            // 눈 흰 반짝 1px: 눈동자가 3칸 이상이고 아직 흰 점이 없을 때 왼쪽 위 칸
            for xs in [6..<12, 12..<19] {
                var dots: [Int] = [], white = false
                for y in 8...12 { for x in xs {
                    let v = p[y * w + x]
                    guard PixBuf.on(v) else { continue }
                    if PixBuf.luma(v) < 70 { dots.append(y * w + x) } else if PixBuf.luma(v) > 238 { white = true }
                } }
                if dots.count >= 3 && !white, let i = dots.first { p[i] = PixBuf.rgb(0xF8F9FA) }
            }
        }
        if f.pose != .none { CharPose.apply(f.pose, &p, w, h, skin: skin, legTop: legTop) }
        PixBuf.shade(&p, w, h, legTop: legTop)
        PixBuf.legs(&p, w, h, f.leg, top: legTop, split: w / 2)
        let top = f.room ? 2 : 0
        PixBuf.pad(&p, w, top: top)
        if f.lift { PixBuf.lift(&p, w, until: legTop + top) }
        if f.dir == .left { PixBuf.mirror(&p, w, h + top) }
        PixBuf.outline(&p, w, h + top)
        let img = PixBuf.image(p, w, h + top)
        if cache.count > 400 { cache.removeAll() }
        cache[key] = img
        return img
    }
}

/// 친구 다섯: 이미 있는 포즈 그림 mo_<id>_stand·back·side_l·side (30×35 도트를 8배)에서 걷기·숨 장면을 만든다
enum FriendMotion {
    static let W = 30, H = 35, legTop = 27
    private struct Key: Hashable { let id: String; let f: CharScene }
    private static var cache: [Key: UIImage] = [:]
    static func has(_ id: String) -> Bool { MeArt.has("mo_\(id)_stand") }
    static func image(_ id: String, _ f: CharScene) -> UIImage? {
        let key = Key(id: id, f: f)
        if let c = cache[key] { return c }
        let name = "mo_\(id)_" + ["stand", "back", "side_l", "side"][f.dir.rawValue]
        guard let cg = UIImage(named: name)?.cgImage else { return nil }
        var p = PixBuf.read(cg, W, H)
        // 테두리까지 그려진 그림이라 다리·윗몸만 옮긴다
        PixBuf.legs(&p, W, H, f.leg, top: legTop, split: W / 2)
        let top = f.room ? 2 : 0
        PixBuf.pad(&p, W, top: top)
        if f.lift { PixBuf.lift(&p, W, until: legTop + top) }
        let img = PixBuf.image(p, W, H + top)
        if cache.count > 200 { cache.removeAll() }
        cache[key] = img
        return img
    }
}

/// 장면 그림을 틀(원래 칸수 기준 width×height)에 그린다. 머리 위 여유 줄은 틀 위로 넘친다
struct SceneSprite: View {
    let img: UIImage
    var width: CGFloat
    var height: CGFloat
    var rows: Int
    var body: some View {
        Image(uiImage: img).interpolation(.none).resizable()
            .frame(width: width, height: height * img.size.height / CGFloat(rows))
            .frame(width: width, height: height, alignment: .bottom)
    }
}

/// 장면 시계: 걸을 땐 0.15초, 서 있을 땐 인물마다 다른 박자(0.55~0.95초)로 한 칸 (화면에 있을 때만 돈다)
struct CharClock<Content: View>: View {
    var walking: Bool
    var breathe = true
    var seed = 0
    @ViewBuilder var content: (Int) -> Content
    @State private var k = 0
    var body: some View {
        content(k)
            .task(id: walking ? 1 : breathe ? 2 : 0) {
                guard walking || breathe else { k = 0; return }
                // 숨은 인물마다 빠르기·시작을 다르게, 숨마다 조금씩 흔들어 다 같이 들썩이지 않게
                let beat = Double.random(in: 0.55...0.95)
                if !walking { try? await Task.sleep(for: .seconds(Double.random(in: 0...beat))) }
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(walking ? 0.15 : beat * Double.random(in: 0.8...1.25)))
                    k &+= 1
                }
            }
    }
}

/// 발밑 그림자: 반투명 타원 (도트 16×5 비율). dot = 한 도트 크기(pt)
struct CharShadow: View {
    var dot: CGFloat
    var body: some View {
        Ellipse().fill(Color.black.opacity(0.22))
            .frame(width: dot * 16, height: dot * 5)
            .offset(y: dot * 2)
            .allowsHitTesting(false)
    }
}

/// 동네 주민 한 명: 방향·걷기·숨 + 그림자. 모자·유명인 머리는 TownFace 가 같이 그린다
struct TownWalker: View {
    let id: String
    let note: TownBook.Note
    var dir: CharDir
    var walking: Bool
    var width: CGFloat = 48
    var height: CGFloat = 68
    var body: some View {
        CharClock(walking: walking, seed: Int(TownLook.seed(id) % 97)) { k in
            // 왼쪽은 오른쪽 장면을 뒤집어 모자까지 같이 돌린다
            let f = CharScene.at(k, dir: dir == .left ? .right : dir, walking: walking)
            TownFace(id: id, note: note, width: width, height: height, frame: f)
                .scaleEffect(x: dir == .left ? -1 : 1, y: 1)
        }
        .background(alignment: .bottom) { CharShadow(dot: width / CGFloat(MeArt.W)) }
    }
}

/// 크게 보이는 인물(쉼터·만들기 화면): 서 있을 땐 숨쉬기, 누르면 한 바퀴 돌며 걷는다.
/// me 가 있으면 부품 합성, 없으면 친구 id 의 포즈 그림. dot = 한 도트 크기(pt)
struct CharFigure: View {
    var me: MeChar? = nil
    var friend: String? = nil
    var dot: CGFloat
    var dir: CharDir = .down
    var walking = false
    /// 누르면 아래·왼·위·오른으로 걸으며 한 바퀴 (바깥에서 방향을 정하면 끈다)
    var tapToTurn = true
    @State private var spin: CharDir? = nil
    @State private var spinning = false
    var body: some View {
        let d = spin ?? dir, walk = walking || spinning
        let w = me != nil ? MeArt.W : FriendMotion.W, h = me != nil ? MeArt.H : FriendMotion.H
        CharClock(walking: walk, seed: (friend ?? "me").count) { k in
            let f = CharScene.at(k, dir: d, walking: walk)
            let img = me.flatMap { MeMotion.image($0, f) } ?? friend.flatMap { FriendMotion.image($0, f) }
            if let img { SceneSprite(img: img, width: dot * CGFloat(w), height: dot * CGFloat(h), rows: h) }
            else { Color.clear.frame(width: dot * CGFloat(w), height: dot * CGFloat(h)) }
        }
        .background(alignment: .bottom) { CharShadow(dot: dot) }
        .contentShape(Rectangle())
        .onTapGesture {
            guard tapToTurn, !spinning else { return }
            spinning = true
            Task { @MainActor in
                for k in 1...4 {
                    spin = (spin ?? dir).turned(1)
                    try? await Task.sleep(nanoseconds: 600_000_000)
                    if k == 4 { spin = nil }
                }
                spinning = false
            }
        }
    }
}

/// 만들기 화면 큰 미리보기: 좌우 돌리기 버튼 · 걷기 토글 · 서 있을 땐 숨쉬기
struct MeTurntable: View {
    let me: MeChar
    let scale: Int
    @State private var dir: CharDir = .down
    @State private var walking = false
    var body: some View {
        CharFigure(me: me, dot: CGFloat(scale), dir: dir, walking: walking, tapToTurn: false)
            .overlay(alignment: .bottom) {
                HStack(spacing: 6) {
                    pill("↺", String(localized: "왼쪽으로 돌리기")) { dir = dir.turned(-1) }
                    pill(walking ? String(localized: "멈춤") : String(localized: "걷기"), walking ? String(localized: "걷기 멈추기") : String(localized: "걷기")) { walking.toggle() }
                    pill("↻", String(localized: "오른쪽으로 돌리기")) { dir = dir.turned(1) }
                }
                .offset(y: 26)
            }
            .padding(.bottom, 26)
    }
    private func pill(_ t: String, _ label: String, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).appFont(12, .bold).padding(.horizontal, 9).padding(.vertical, 4)
                .background(Theme.card, in: Capsule()).overlay(Capsule().stroke(Theme.border))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: 홈 쉼터 박스 인물: 오늘 장 흐름에 따라 짧은 장면을 되풀이한다 (2026-10-23)
// 기본 장면(FriendMotion·MeMotion)에 눈·팔·머리·다리만 몇 칸 바꿔 입힌다. 새 그림 없음.
// 눈을 못 찾는 인물(세리·이르처럼 눈이 밝은 그림)은 눈만 그대로 두고 나머지 동작은 한다

/// 오늘 장 흐름: ±0.3% 안은 보합, ±2%를 넘으면 크게
enum DayMood {
    case bigUp, up, flat, down, bigDown
    static func of(_ mv: Double) -> DayMood {
        mv >= 0.02 ? .bigUp : mv >= 0.003 ? .up : mv > -0.003 ? .flat : mv > -0.02 ? .down : .bigDown
    }
}

/// 기본 장면 위에 입히는 동작
enum MoodAct: Hashable {
    /// blink 눈 감기 · smile ^^ · look 눈 옆으로 · phone 휴대폰 들어 보기 · sigh 고개 숙이고 눈 감기 · nod 고개 한 칸 · crouch 쪼그려 앉기 · cheer 만세 · spark 만세 + 반짝
    case stand, breath, blink, smile, lookL, lookR, phone, phoneShut, phoneSweat, sweat, sigh, nod, crouch, cheer, spark
}

/// 대본 한 칸: 방향·다리 + 동작 + 0.15초 칸 수 + 옮김(도트, 위가 -)
struct MoodStep {
    var act: MoodAct = .stand
    var dir: CharDir = .down
    var leg = 0
    var n: Int
    var dx = 0
    var dy = 0
}

enum MoodScript {
    static let tick = 0.15
    private static func s(_ a: MoodAct, _ n: Int, dy: Int = 0) -> MoodStep { MoodStep(act: a, n: n, dy: dy) }
    private static func w(_ d: CharDir, _ leg: Int, _ dx: Int) -> MoodStep { MoodStep(dir: d, leg: leg, n: 2, dx: dx) }
    static func steps(_ m: DayMood) -> [MoodStep] {
        switch m {
        // 휴대폰을 보고 → 만세하며 두 번 뛰고 → 반짝 → 신나서 옆으로 몇 걸음 갔다가 돌아온다
        case .bigUp: return [s(.stand, 4), s(.breath, 4), s(.phone, 12), s(.cheer, 3), s(.cheer, 2, dy: -2), s(.cheer, 2), s(.cheer, 2, dy: -2), s(.spark, 6), s(.smile, 4),
                             w(.right, 1, 1), w(.right, 0, 2), w(.right, 2, 3), w(.up, 0, 3), w(.left, 1, 2), w(.left, 0, 1), w(.left, 2, 0), s(.stand, 4)]
        // 느긋하게 숨 쉬다 휴대폰을 한 번 보고, 빙긋 웃으며 두 번 끄덕인다
        case .up: return [s(.stand, 4), s(.breath, 4), s(.stand, 4), s(.breath, 4), s(.phone, 8), s(.smile, 6), s(.nod, 2), s(.stand, 2), s(.nod, 2),
                          s(.stand, 4), s(.breath, 4), s(.blink, 1), s(.stand, 4)]
        // 심심하게 두리번거리고, 꾸벅 졸다가, 휴대폰을 슬쩍 본다
        case .flat: return [s(.stand, 4), s(.breath, 4), s(.blink, 1), s(.stand, 4), s(.lookL, 6), s(.lookR, 6), s(.stand, 3), s(.blink, 6), s(.nod, 2), s(.stand, 4),
                            s(.breath, 4), s(.phone, 10), s(.stand, 4)]
        // 휴대폰을 보다 땀 한 방울, 옆을 보고 짧게 한숨, 끄덕여 마음을 다잡는다
        case .down: return [s(.stand, 4), s(.phone, 8), s(.phoneSweat, 8), s(.sweat, 4), s(.lookL, 4), s(.sigh, 6), s(.stand, 4), s(.breath, 6), s(.nod, 2), s(.stand, 4)]
        // 휴대폰을 보고 질끈, 쪼그려 앉았다 일어나 잠깐 먼 곳을 보고, 돌아서서 두 번 끄덕인다
        case .bigDown: return [s(.phone, 6), s(.phoneShut, 8), s(.sweat, 6), s(.crouch, 14), s(.sigh, 6), s(.stand, 4), MoodStep(dir: .up, n: 8), s(.stand, 4),
                               s(.nod, 2), s(.stand, 2), s(.nod, 2), s(.breath, 6), s(.stand, 4)]
        }
    }
    static func at(_ m: DayMood, _ k: Int) -> MoodStep {
        let st = steps(m)
        var i = k % max(1, st.reduce(0) { $0 + $1.n })
        for s in st { if i < s.n { return s }; i -= s.n }
        return st[0]
    }
}

/// 동작을 입힌 장면 그림 (인물·방향·다리·동작마다 한 번 만들어 둔다)
enum MoodArt {
    private struct Key: Hashable { let who: String; let me: MeChar?; let dir: CharDir; let leg: Int; let act: MoodAct }
    private static var cache: [Key: UIImage] = [:]
    /// 장면 위에 둔 빈 줄 (CharScene.room)
    private static let T = 2

    /// who: 친구 id 또는 "me"
    static func image(_ who: String, me: MeChar?, _ st: MoodStep) -> UIImage? {
        let key = Key(who: who, me: who == "me" ? me : nil, dir: st.dir, leg: st.leg, act: st.act)
        if let c = cache[key] { return c }
        let f = CharScene(dir: st.dir, leg: st.leg, lift: st.act == .breath, room: true)
        let base = who == "me" ? me.flatMap { MeMotion.image($0, f) } : FriendMotion.image(who, f)
        guard let cg = base?.cgImage else { return nil }
        let w = cg.width, h = cg.height
        var p = PixBuf.read(cg, w, h)
        apply(st.act, &p, w, h, legTop: (who == "me" ? MeMotion.legTop : FriendMotion.legTop) + T)
        let img = PixBuf.image(p, w, h)
        if cache.count > 300 { cache.removeAll() }
        cache[key] = img
        return img
    }

    private static func apply(_ a: MoodAct, _ p: inout [UInt32], _ w: Int, _ h: Int, legTop: Int) {
        guard a != .stand, a != .breath else { return }
        let ink = PixBuf.rgb(MeArt.outline), orig = p
        func at(_ x: Int, _ y: Int) -> UInt32 { x >= 0 && x < w && y >= 0 && y < h ? p[y * w + x] : 0 }
        func put(_ x: Int, _ y: Int, _ c: UInt32) { if x >= 0, x < w, y >= 0, y < h { p[y * w + x] = c } }
        func light(_ v: UInt32) -> Bool { PixBuf.on(v) && PixBuf.luma(v) >= 70 }

        // 눈 두 개: 얼굴 줄에서 양옆이 밝은 칸인 짧은 짙은 줄(1~2칸)을 모아 왼쪽·오른쪽 무리로
        struct Eye { var x0: Int, x1: Int, y0: Int, y1: Int, skin: UInt32 }
        func eyes() -> [Eye] {
            var l: Eye?, r: Eye?
            for y in (T + 8)...(T + 12) {
                var x = w / 4
                while x < w * 3 / 4 {
                    guard PixBuf.on(at(x, y)), !light(at(x, y)), light(at(x - 1, y)) else { x += 1; continue }
                    var e = x
                    while e + 1 < w, PixBuf.on(at(e + 1, y)), !light(at(e + 1, y)) { e += 1 }
                    if e - x <= 1, light(at(e + 1, y)) {
                        let sk = at(x - 1, y)
                        if (x + e) / 2 < w / 2 {
                            l = l.map { Eye(x0: min($0.x0, x), x1: max($0.x1, e), y0: $0.y0, y1: y, skin: $0.skin) } ?? Eye(x0: x, x1: e, y0: y, y1: y, skin: sk)
                        } else {
                            r = r.map { Eye(x0: min($0.x0, x), x1: max($0.x1, e), y0: $0.y0, y1: y, skin: $0.skin) } ?? Eye(x0: x, x1: e, y0: y, y1: y, skin: sk)
                        }
                    }
                    x = e + 1
                }
            }
            guard let l, let r else { return [] }
            return [l, r]
        }
        func clear(_ e: Eye) {
            for y in e.y0...e.y1 { for x in max(0, e.x0 - 1)...min(w - 1, e.x1 + 1) where PixBuf.near(p[y * w + x], 0xF8F9FA) || !light(p[y * w + x]) { p[y * w + x] = e.skin } }
        }
        func shut() { for e in eyes() { clear(e); for x in (e.x0 - 1)...(e.x1 + 1) { put(x, e.y1, ink) } } }
        func smile() { for e in eyes() { clear(e); put(e.x0 - 1, e.y1, ink); for x in e.x0...e.x1 { put(x, e.y1 - 1, ink) }; put(e.x1 + 1, e.y1, ink) } }
        func look(_ d: Int) {
            for e in eyes() {
                let old = (e.y0...e.y1).map { y in (e.x0...e.x1).map { at($0, y) } }
                clear(e)
                for (i, y) in (e.y0...e.y1).enumerated() { for (j, x) in (e.x0...e.x1).enumerated() where !light(old[i][j]) { put(x + d, y, old[i][j]) } }
            }
        }
        /// 머리(목 위)를 한 칸 내린다
        func bow() {
            let neck = T + 14
            for y in stride(from: neck, through: 1, by: -1) { for x in 0..<w { p[y * w + x] = p[(y - 1) * w + x] } }
            for x in 0..<w { p[x] = 0 }
        }
        /// 다리 세 줄을 접어 몸을 내린다 (CharPose.crouch 와 같은 방식)
        func fold() {
            let n = 3
            for y in stride(from: legTop + n, through: n, by: -1) { for x in 0..<w { p[y * w + x] = p[(y - n) * w + x] } }
            for i in 0..<(n * w) { p[i] = 0 }
        }
        func faceSkin() -> UInt32 { eyes().first?.skin ?? at(w / 2, T + 12) }
        /// 휴대폰을 얼굴 왼쪽으로 들어 본다. 원래 손에 든 휴대폰(화면 하늘색)은 지운다
        func phone() {
            let scr = PixBuf.rgb(0x7FD6FF)
            let held = (0..<h).flatMap { y in (0..<w).filter { y > T + 18 && p[y * w + $0] == scr }.map { ($0, y) } }
            if let x0 = held.map(\.0).min(), let x1 = held.map(\.0).max(), let y0 = held.map(\.1).min(), let y1 = held.map(\.1).max() {
                for y in (y0 - 2)...(y1 + 2) { for x in (x0 - 2)...(x1 + 1) { put(x, y, 0) } }
            }
            let y = T + 11
            guard let fl = (0..<w).first(where: { PixBuf.on(at($0, y)) }) else { return }
            let sk = faceSkin(), shirt = at((0..<w).first(where: { PixBuf.on(at($0, T + 18)) }).map { $0 + 1 } ?? w / 2, T + 18)
            for yy in (T + 15)...(T + 17) { put(fl - 2, yy, shirt) }
            put(fl - 2, T + 15, sk); put(fl - 1, T + 15, sk)
            for yy in (T + 10)...(T + 14) { for x in (fl - 3)...(fl - 1) { put(x, yy, PixBuf.rgb(0x1D1D24)) } }
            for yy in (T + 11)...(T + 13) { put(fl - 2, yy, scr) }
            look(-1)
        }
        func cheer() {
            let y = T + 17
            let xs = (0..<w).filter { PixBuf.on(at($0, y)) }
            guard let l = xs.first, let r = xs.last else { return }
            let shirt = at(l + 1, y), sk = faceSkin()
            for k in 1...3 { put(l - k, y - k, shirt); put(r + k, y - k, shirt) }
            put(l - 3, y - 4, sk); put(r + 3, y - 4, sk)
            smile()
        }
        func sweat() {
            let y = T + 4
            guard let r = (0..<w).last(where: { PixBuf.on(at($0, y)) }) else { return }
            let c = PixBuf.rgb(0x9FD8FF)
            put(r + 2, y - 1, c); put(r + 2, y, c); put(r + 1, y, c)
        }
        switch a {
        case .stand, .breath: break
        case .blink: shut()
        case .smile: smile()
        case .lookL: look(-1)
        case .lookR: look(1)
        case .phone: phone()
        case .phoneShut: phone(); shut()
        case .phoneSweat: phone(); sweat()
        case .sweat: sweat()
        case .sigh: shut(); bow()
        case .nod: bow()
        case .crouch: shut(); fold()
        case .cheer, .spark: cheer()
        }
        // 테두리는 새로 그린 팔·휴대폰·땀 둘레에만 (머리·몸을 옮긴 장면은 테두리째 옮겼고, 이르의 빛 알갱이처럼 테두리 없는 조각은 그대로)
        let drawn = [.phone, .phoneShut, .phoneSweat, .sweat, .cheer, .spark].contains(a)
            ? (0..<(w * h)).filter { PixBuf.on(p[$0]) && p[$0] != orig[$0] && p[$0] != ink } : []
        for i in drawn {
            let x = i % w, y = i / w
            for (nx, ny) in [(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)] where nx >= 0 && nx < w && ny >= 0 && ny < h && !PixBuf.on(p[ny * w + nx]) {
                p[ny * w + nx] = ink
            }
        }
        if a == .spark {
            // 반짝이는 테두리 없이 맨 나중에
            let c = PixBuf.rgb(0xFFD84A)
            for (cx, cy) in [(1, T + 6), (w - 2, T + 5)] { for (dx, dy) in [(0, -1), (-1, 0), (1, 0), (0, 1)] { put(cx + dx, cy + dy, c) } }
        }
    }
}

/// 홈 쉼터 박스 인물: 오늘 장 흐름 대본을 0.15초 칸으로 되풀이한다. 화면에 있을 때만 돌고, 동작 줄이기를 켜면 서 있기만 한다
struct MoodFigure: View {
    let who: String
    var me: MeChar?
    let mood: DayMood
    var width: CGFloat
    var height: CGFloat
    /// 한 도트 크기(pt): 정수 배라야 도트 줄이 고르게 보인다
    var dot: CGFloat = 2
    @Environment(\.accessibilityReduceMotion) private var reduce
    @State private var k = 0
    var body: some View {
        let st = MoodScript.at(mood, reduce ? 0 : k)
        Group {
            if let img = MoodArt.image(who, me: me, st) {
                Image(uiImage: img).interpolation(.none).resizable()
                    .frame(width: img.size.width * dot, height: img.size.height * dot)
                    .offset(x: CGFloat(st.dx) * dot, y: CGFloat(st.dy) * dot)
            }
        }
        .frame(width: width, height: height, alignment: .bottom)
        .task(id: reduce) {
            guard !reduce else { k = 0; return }
            k = Int.random(in: 0..<40)   // 열 때마다 대본 같은 자리에서 시작하지 않게
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(MoodScript.tick))
                k &+= 1
            }
        }
    }
}
