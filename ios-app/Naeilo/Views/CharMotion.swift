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
    /// k 번째 칸: 걸을 땐 서기·왼발·서기·오른발, 서 있을 땐 숨 두 장
    static func at(_ k: Int, dir: CharDir, walking: Bool) -> CharScene {
        walking ? CharScene(dir: dir, leg: [0, 1, 0, 2][k % 4], lift: k % 2 == 1, room: true)
                : CharScene(dir: dir, leg: 0, lift: k % 2 == 1, room: true)
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
                    pill("↺", "왼쪽으로 돌리기") { dir = dir.turned(-1) }
                    pill(walking ? "멈춤" : "걷기", walking ? "걷기 멈추기" : "걷기") { walking.toggle() }
                    pill("↻", "오른쪽으로 돌리기") { dir = dir.turned(1) }
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
