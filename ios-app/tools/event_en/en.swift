import AppKit
// 사용: en <원본.png> <spec.json> <출력.png>   (색 변환 없이 원본 바이트를 직접 고친다)
// spec: [{"r":[x,y,w,h] (495 폭 기준 찾을 칸), "lines":[["글","RRGGBB"]...], "bg":"RRGGBB"(선택), "size":11(선택), "at":[x,y](선택, 글 시작점 495 기준)}]
let a = CommandLine.arguments
let rep = NSBitmapImageRep(data: try! Data(contentsOf: URL(fileURLWithPath: a[1])))!
let W = rep.pixelsWide, H = rep.pixelsHigh, F = Double(W) / 495.0
let spp = rep.samplesPerPixel, bpr = rep.bytesPerRow, buf = rep.bitmapData!
struct Item: Decodable { let r: [Double]; let lines: [[String]]; let bg: String?; let size: Double?; let at: [Double]? }
let items = try! JSONDecoder().decode([Item].self, from: Data(contentsOf: URL(fileURLWithPath: a[2])))
typealias C = (Int, Int, Int, Int)
func get(_ x: Int, _ y: Int) -> C { let i = y * bpr + x * spp; return (Int(buf[i]), Int(buf[i+1]), Int(buf[i+2]), spp == 4 ? Int(buf[i+3]) : 255) }
func set(_ x: Int, _ y: Int, _ c: C) { guard x >= 0, y >= 0, x < W, y < H else { return }; let i = y * bpr + x * spp; buf[i] = UInt8(c.0); buf[i+1] = UInt8(c.1); buf[i+2] = UInt8(c.2); if spp == 4 { buf[i+3] = UInt8(c.3) } }
func hex(_ s: String) -> C { let v = Int(s, radix: 16)!; return (v >> 16 & 255, v >> 8 & 255, v & 255, 255) }
func dist(_ p: C, _ q: C) -> Int { abs(p.0 - q.0) + abs(p.1 - q.1) + abs(p.2 - q.2) + abs(p.3 - q.3) }
let cs = CGColorSpaceCreateDeviceRGB()
for it in items {
    let x0 = Int(it.r[0] * F), y0 = Int(it.r[1] * F), x1 = min(W - 1, Int((it.r[0] + it.r[2]) * F)), y1 = min(H - 1, Int((it.r[1] + it.r[3]) * F))
    var bg: C
    if let b = it.bg { bg = hex(b) } else {
        var cnt: [Int: Int] = [:]
        for y in y0...y1 { for x in x0...x1 { let p = get(x, y); cnt[p.3 << 24 | p.0 << 16 | p.1 << 8 | p.2, default: 0] += 1 } }
        let m = cnt.max { $0.value < $1.value }!.key; bg = (m >> 16 & 255, m >> 8 & 255, m & 255, m >> 24 & 255)
    }
    var bx0 = x1, by0 = y1, bx1 = x0, by1 = y0
    for y in y0...y1 { for x in x0...x1 where dist(get(x, y), bg) > 60 { bx0 = min(bx0, x); bx1 = max(bx1, x); by0 = min(by0, y); by1 = max(by1, y) } }
    if bx1 < bx0 { bx0 = x0; bx1 = x0; by0 = y0; by1 = y1 }
    for y in max(y0, by0 - 2)...min(y1, by1 + 2) { for x in max(x0, bx0 - 2)...min(x1, bx1 + 2) { set(x, y, bg) } }
    // 글: 1배 판에 안티에일리어스 없이 그린 뒤 2×2 칸으로 찍는다 (원본 도트와 같은 크기)
    let size = it.size ?? 12
    let font = NSFont(name: "Silom", size: size)!
    let lh = Int(ceil(size * 1.3)), n = it.lines.count
    let tw = 400, th = lh * n + 4
    let t = CGContext(data: nil, width: tw, height: th, bitsPerComponent: 8, bytesPerRow: tw * 4, space: cs, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    t.setShouldAntialias(false); t.setAllowsFontSmoothing(false)
    var maxw = 0.0
    for (k, l) in it.lines.enumerated() {
        let s = NSAttributedString(string: l[0], attributes: [.font: font, .foregroundColor: NSColor(red: CGFloat(k) / 255, green: 0, blue: 0, alpha: 1)])
        let line = CTLineCreateWithAttributedString(s)
        maxw = max(maxw, CTLineGetTypographicBounds(line, nil, nil, nil))
        t.textPosition = CGPoint(x: 0, y: Double(th - lh * (k + 1)) + 3); CTLineDraw(line, t)
    }
    let sx = it.at.map { Int($0[0] * F) } ?? bx0
    let room = Double(x1 - sx) / 2
    if maxw > room { FileHandle.standardError.write("넘침: \(it.lines[0][0]) \(Int(maxw)) > \(Int(room))\n".data(using: .utf8)!) }
    let tb = t.data!.bindMemory(to: UInt8.self, capacity: tw * th * 4)
    let dh = th * 2
    let top = it.at.map { Int($0[1] * F) } ?? (n == 1 ? (by0 + by1) / 2 - dh / 2 : by0 - 4)
    for ty in 0..<th { for tx in 0..<tw {
        let i = (ty * tw + tx) * 4
        guard tb[i+3] > 128 else { continue }
        let c = hex(it.lines[min(n - 1, Int(tb[i]))][1])
        for dy in 0..<2 { for dx in 0..<2 { set(sx + tx * 2 + dx, top + ty * 2 + dy, c) } }
    } }
}
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: a[3]))
