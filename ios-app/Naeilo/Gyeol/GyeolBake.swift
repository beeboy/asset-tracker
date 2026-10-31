#if GYEOLPIP
import Foundation

// 결 PiP: 결의 자리 구운 장면(gyeol-bake.json) 읽기와 섞기.
// project-files gyeol-line/page/bake/player.js·stage.js를 그대로 옮겼다. 3D는 돌리지 않고 구운 점만 섞는다.
// 형식이 바뀌면 bake 쪽 enc 숫자가 오른다. 숫자가 다르면 읽지 않는다.

/// 구운 json 한 칸. 숫자 0은 채울 자리, false·true는 그대로 둔다(JS의 t === 0 과 같게)
indirect enum GyeolJV: Decodable {
    case num(Double), str(String), bool(Bool), arr([GyeolJV]), obj([String: GyeolJV]), null
    init(from d: Decoder) throws {
        let c = try d.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let b = try? c.decode(Bool.self) { self = .bool(b) }
        else if let n = try? c.decode(Double.self) { self = .num(n) }
        else if let s = try? c.decode(String.self) { self = .str(s) }
        else if let a = try? c.decode([GyeolJV].self) { self = .arr(a) }
        else { self = .obj(try c.decode([String: GyeolJV].self)) }
    }
    var int: Int? { if case .num(let n) = self { return Int(n) }; return nil }
    var double: Double? { if case .num(let n) = self { return n }; return nil }
}

/// 부품 하나의 점 숫자 (s: 모양 틀 번호, uid: 구운 번호 또는 굳힌 임시 번호)
final class GyeolPart {
    let s: Int, n: [Float], id: String, uid: Int
    init(s: Int, n: [Float], id: String, uid: Int) { self.s = s; self.n = n; self.id = id; self.uid = uid }
}

/// 장면 하나: 부품(앞뒤 순서대로)과 머리 옆 부호
final class GyeolFrame {
    let order: [String]
    let by: [String: GyeolPart]
    let fx: [GyeolFx]
    init(parts: [GyeolPart], fx: [GyeolFx]) {
        order = parts.map(\.id)
        var b: [String: GyeolPart] = [:]; for p in parts { b[p.id] = p }
        by = b; self.fx = fx
    }
}

/// 머리 옆 부호: [종류, x, y, 크기, 돌림, (투명도)…] — JS 배열을 그대로 따른다
struct GyeolFx { let kind: String; let v: [Double] }

final class GyeolBake {
    static let enc = 2
    struct Anim: Decodable {
        let k: [Int]; let mid: [GyeolJV]; let at: [Double]; let hold: [Double]
        let rep: [Int]?; let times: Int?; let back: Double?; let repMid: GyeolJV?
    }
    private struct File: Decodable {
        let q: Double; let enc: Int?
        let shapes: [GyeolJV]; let pool: [[GyeolJV]]
        let frames: [FrameJ]; let anims: [String: Anim]; let base: [String: Int]
    }
    private struct FrameJ: Decodable { let p: [Int]; let fx: [[GyeolJV]] }

    /// 모양 틀을 채울 자리 순서(키는 정렬)대로 편 것
    indirect enum T { case slot(Int), num(Double), str(String), bool(Bool), arr([T]), obj([(String, T)]), null }

    let qs: Double
    let anims: [String: Anim]
    let base: [String: Int]
    private let shapesJ: [GyeolJV]
    private let pool: [(s: Int, r: Int, b64: String)]
    private let framesJ: [FrameJ]
    private var tpl: [Int: (t: T, id: String)] = [:]
    private var raw: [Int: [Double]] = [:]
    private var parts: [Int: GyeolPart] = [:]
    private var frames: [Int: GyeolFrame] = [:]

    init(data: Data) throws {
        let f = try JSONDecoder().decode(File.self, from: data)
        guard (f.enc ?? GyeolBake.enc) == GyeolBake.enc else { throw NSError(domain: "gyeol.bake", code: 1) }
        qs = 1 / f.q; anims = f.anims; base = f.base; shapesJ = f.shapes; framesJ = f.frames
        pool = f.pool.map { e in
            var b = ""; if e.count > 2, case .str(let s) = e[2] { b = s }
            return (e[0].int ?? 0, e.count > 1 ? (e[1].int ?? -1) : -1, b)
        }
    }

    // ---------- 모양 틀 ----------
    func template(_ s: Int) -> (t: T, id: String) {
        if let t = tpl[s] { return t }
        var i = 0
        func walk(_ j: GyeolJV) -> T {
            switch j {
            case .num(let n): if n == 0 { defer { i += 1 }; return .slot(i) } else { return .num(n) }
            case .str(let s): return .str(s)
            case .bool(let b): return .bool(b)
            case .null: return .null
            case .arr(let a): return .arr(a.map(walk))
            case .obj(let o):
                // {"$":n} → [[0,0]…n개]
                if let c = o["$"], o.count == 1, let n = c.int { return .arr((0..<n).map { _ in let a = T.slot(i); let b = T.slot(i + 1); i += 2; return .arr([a, b]) }) }
                return .obj(o.keys.sorted().map { ($0, walk(o[$0]!)) })
            }
        }
        let t = walk(shapesJ[s])
        var id = ""; if case .obj(let kv) = t, let v = kv.first(where: { $0.0 == "id" })?.1, case .str(let s) = v { id = s }
        tpl[s] = (t, id); return (t, id)
    }

    // ---------- 숫자 줄 풀기 (pack.js): 같은 틀 첫 줄과의 차이, x·y 따로 앞 값과의 차이, 0은 개수로 ----------
    private func rawNums(_ ix: Int) -> [Double] {
        if let r = raw[ix] { return r }
        let e = pool[ix]
        let bin = [UInt8](Data(base64Encoded: e.b64) ?? Data())
        var d: [Double] = []; d.reserveCapacity(bin.count)
        var i = 0
        func rd() -> Double {
            var z = 0.0, sh = 0.0, c: UInt8 = 0
            repeat { c = i < bin.count ? bin[i] : 0; i += 1; z += Double(c & 127) * pow(2, sh); sh += 7 } while c & 128 != 0
            return z.truncatingRemainder(dividingBy: 2) != 0 ? -(z + 1) / 2 : z / 2
        }
        while i < bin.count {
            let v = rd()
            if v == 0 { let n = Int(rd()) + 1; for _ in 0..<max(0, n) { d.append(d.count >= 2 ? d[d.count - 2] : 0) } }
            else { d.append(v + (d.count >= 2 ? d[d.count - 2] : 0)) }
        }
        if e.r >= 0 { let ref = rawNums(e.r); for j in 0..<min(d.count, ref.count) { d[j] += ref[j] } }
        raw[ix] = d
        return d
    }

    func part(_ ix: Int) -> GyeolPart {
        if let p = parts[ix] { return p }
        let s = pool[ix].s, n = rawNums(ix).map { Float($0 * qs) }
        let p = GyeolPart(s: s, n: n, id: template(s).id, uid: ix)
        parts[ix] = p; return p
    }

    func frame(_ fi: Int) -> GyeolFrame {
        if let f = frames[fi] { return f }
        let j = framesJ[fi]
        let fx: [GyeolFx] = j.fx.compactMap { a in
            guard case .str(let k)? = a.first else { return nil }
            return GyeolFx(kind: k, v: a.dropFirst().map { $0.double ?? 0 })
        }
        let f = GyeolFrame(parts: j.p.map(part), fx: fx)
        frames[fi] = f; return f
    }
    func mid(_ j: GyeolJV?) -> GyeolSeg.Mid {
        switch j {
        case .num(let n)?: return .one(frame(Int(n)))
        case .arr(let a)?: let fs = a.compactMap(\.int).map(frame); return fs.count == 3 ? .three(fs) : .none
        default: return .none
        }
    }
}

/// 시간 줄 마디 하나: a → (m) → b 를 d(ms) 동안. stop이면 멈출 때 살짝 지나쳤다 돌아온다
struct GyeolSeg {
    enum Mid { case none, one(GyeolFrame), three([GyeolFrame]) }
    var a: GyeolFrame; var m: Mid; var b: GyeolFrame; var d: Double; var stop: Bool
}
struct GyeolPlan { var segs: [GyeolSeg]; var total: Double; var stay = false }

/// 무대: player.js 시간 줄과 stage.js 섞기. 한 줄(큐)에서만 부른다
final class GyeolStage {
    struct Opt { var speed: Double? = nil; var holdLast: Double? = nil; var lead: Double? = nil; var times: Int? = nil; var stay = false; var back: Double? = nil; var done: (() -> Void)? = nil }
    /// 그릴 부품: 구운 그대로이거나 섞은 숫자
    struct Drawn { let s: Int; let n: [Float] }
    struct Sample { let order: [String]; let parts: [String: Drawn]; let fx: [GyeolFx] }

    let bk: GyeolBake
    private(set) var pose = "sit"
    private(set) var plan: GyeolPlan?
    private var p0 = 0.0
    private(set) var playing = ""
    private var onDone: (() -> Void)?
    private var tmpUid = -1

    // 부품마다 박자를 조금씩 어긋나게 한다: 팔이 먼저 가고, 머리카락 끝은 조금 늦게 따라온다
    static let lag: [String: Double] = ["armL": -45, "armR": -45, "tail": 70, "lockL": 55, "lockR": 55, "hairBack": 45, "backStrands": 45]
    static let over: [String: Double] = ["tail": 1.5, "lockL": 1.25, "lockR": 1.25, "hairBack": 1.1, "backStrands": 1.1]

    init(_ bk: GyeolBake, pose: String = "sit") { self.bk = bk; self.pose = pose }

    var baseFrame: GyeolFrame { bk.frame(bk.base[pose] ?? 0) }
    func animId(_ n: String, _ p: String? = nil) -> String? {
        let p = p ?? pose
        return ["c:\(n):\(p)", "g:\(n):\(p)", "d:\(n):\(p)", "m:\(n):\(p)", "p:\(n)", "idle:\(n):\(p)"].first { bk.anims[$0] != nil }
    }
    var busy: Bool { plan != nil }

    // ---------- 시간 줄 ----------
    static func easeIO(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }
    static func easeBack(_ x: Double, _ k: Double) -> Double { let c = 0.9 * k, c3 = c + 1; return 1 + c3 * pow(x - 1, 3) + c * pow(x - 1, 2) }

    func makePlan(_ id: String, _ o: Opt) -> GyeolPlan? {
        guard let A = bk.anims[id], A.k.count >= 2 else { return nil }
        let K = A.k.map(bk.frame), at = A.at, sp = o.speed ?? 1
        var hold = A.hold
        if let h = o.holdLast, !hold.isEmpty { hold[hold.count - 1] = h }
        func H(_ i: Int) -> Double { i >= 0 && i < hold.count ? hold[i] : 0 }
        func M(_ i: Int) -> GyeolSeg.Mid { bk.mid(i >= 0 && i < A.mid.count ? A.mid[i] : nil) }
        func Kf(_ i: Int) -> GyeolFrame { K[max(0, min(K.count - 1, i))] }
        var segs: [GyeolSeg] = []
        segs.append(GyeolSeg(a: K[0], m: M(0), b: K[1], d: o.lead ?? 380, stop: H(0) > 0))
        if H(0) != 0 { segs.append(GyeolSeg(a: K[1], m: .none, b: K[1], d: H(0), stop: false)) }
        func go(_ i: Int) {
            let d = max(160, at[i] - at[i - 1] - H(i - 1)) / sp
            segs.append(GyeolSeg(a: Kf(i), m: M(i), b: Kf(i + 1), d: d, stop: H(i) > 0))
            if H(i) != 0 { segs.append(GyeolSeg(a: Kf(i + 1), m: .none, b: Kf(i + 1), d: H(i) / sp, stop: false)) }
        }
        if at.count > 1 { for i in 1..<at.count { go(i) } }
        if let rep = A.rep, rep.count == 2 {
            let r0 = rep[0], r1 = rep[1], times = o.times ?? (A.times ?? 1)
            let back = max(160, r0 + 1 < at.count ? at[r0 + 1] - at[r0] - H(r0) : 400) / sp
            if times > 1 { for _ in 1..<times {
                segs.append(GyeolSeg(a: Kf(r1 + 1), m: bk.mid(A.repMid), b: Kf(r0 + 1), d: back, stop: H(r0) > 0))
                if H(r0) != 0 { segs.append(GyeolSeg(a: Kf(r0 + 1), m: .none, b: Kf(r0 + 1), d: H(r0) / sp, stop: false)) }
                if r0 + 1 <= r1 { for i in (r0 + 1)...r1 { go(i) } }
            } }
        }
        if !o.stay { let L = K.count - 2; segs.append(GyeolSeg(a: Kf(L), m: M(L), b: Kf(L + 1), d: (o.back ?? A.back ?? 400) / sp, stop: true)) }
        return GyeolPlan(segs: segs, total: segs.reduce(0) { $0 + $1.d })
    }

    /// 시각 → 섞을 장면 둘과 비율
    func at(_ P: GyeolPlan, _ t: Double, _ over: Double) -> (GyeolFrame, GyeolFrame, Double) {
        if t <= 0 { let g = P.segs[0]; return (g.a, g.a, 0) }
        var s = 0.0, i = 0
        while i < P.segs.count { let g = P.segs[i]; if t < s + g.d || i == P.segs.count - 1 { break }; s += g.d; i += 1 }
        let g = P.segs[i], x = min(1, (t - s) / g.d)
        let e = g.stop ? Self.easeBack(x * x * (3 - 2 * x), over) : Self.easeIO(x)
        if g.a === g.b { return (g.a, g.a, 0) }
        switch g.m {
        case .none: return (g.a, g.b, e)
        case .three(let ms):
            let q = [g.a] + ms + [g.b], j = max(0, min(3, Int((e * 4).rounded(.down))))
            return (q[j], q[j + 1], e * 4 - Double(j))
        case .one(let m): return e < 0.5 ? (g.a, m, e * 2) : (m, g.b, (e - 0.5) * 2)
        }
    }

    /// 부품 하나 섞기: 모양이 같으면 점을 섞고, 다르면 가운데서 바꾼다
    static func mix(_ A: GyeolPart?, _ B: GyeolPart?, _ t: Double) -> Drawn? {
        guard let A else { return t < 0.5 ? nil : B.map { Drawn(s: $0.s, n: $0.n) } }
        guard let B else { return t < 0.5 ? Drawn(s: A.s, n: A.n) : nil }
        if A === B { return Drawn(s: A.s, n: A.n) }
        if A.s != B.s { let p = t < 0.5 ? A : B; return Drawn(s: p.s, n: p.n) }
        if t <= 0 { return Drawn(s: A.s, n: A.n) }
        if t == 1 { return Drawn(s: B.s, n: B.n) }
        let k = Float(t), c = min(A.n.count, B.n.count)
        var n = [Float](repeating: 0, count: c)
        for i in 0..<c { n[i] = A.n[i] + (B.n[i] - A.n[i]) * k }
        return Drawn(s: A.s, n: n)
    }

    static func mixFx(_ A: [GyeolFx], _ B: [GyeolFx], _ t: Double) -> [GyeolFx] {
        var out: [GyeolFx] = []
        for i in 0..<max(A.count, B.count) {
            let a = i < A.count ? A[i] : nil, b = i < B.count ? B[i] : nil
            if let a, let b, a.kind == b.kind {
                out.append(GyeolFx(kind: a.kind, v: a.v.enumerated().map { j, v in v + ((j < b.v.count ? b.v[j] : v) - v) * t } + [1]))
            } else {
                if let a { out.append(GyeolFx(kind: a.kind, v: a.v + [1 - t])) }
                if let b { out.append(GyeolFx(kind: b.kind, v: b.v + [t])) }
            }
        }
        return out
    }

    func sample(_ now: Double) -> Sample {
        guard let P = plan else {
            let b = baseFrame
            return Sample(order: b.order, parts: b.by.mapValues { Drawn(s: $0.s, n: $0.n) }, fx: b.fx)
        }
        let t = now - p0, (a0, b0, k0) = at(P, t, 1)
        var order = (k0 < 0.5 ? a0 : b0).order
        for id in (k0 < 0.5 ? b0 : a0).order where !order.contains(id) { order.append(id) }
        var parts: [String: Drawn] = [:]
        for id in order {
            let lag = Self.lag[id] ?? 0
            let (a, b, k): (GyeolFrame, GyeolFrame, Double)
            if lag != 0 {
                let tl = lag < 0 ? t - lag * min(1, t / 160) : t - lag   // 앞서는 팔은 처음 0.16초 동안 서서히 앞선다
                (a, b, k) = at(P, tl, Self.over[id] ?? 1)
            } else { (a, b, k) = (a0, b0, k0) }
            if let d = Self.mix(a.by[id], b.by[id], k) { parts[id] = d }
        }
        return Sample(order: order, parts: parts, fx: Self.mixFx(a0.fx, b0.fx, k0))
    }

    /// 지금 보이는 모습을 장면 하나로 굳힌다(몸짓 도중에 다른 몸짓이 와도 뚝 끊기지 않게)
    private func freeze(_ now: Double) -> GyeolFrame {
        let cur = sample(now)
        let ps: [GyeolPart] = cur.order.compactMap { id in
            guard let r = cur.parts[id] else { return nil }
            tmpUid -= 1
            return GyeolPart(s: r.s, n: r.n, id: id, uid: tmpUid)
        }
        return GyeolFrame(parts: ps, fx: cur.fx)
    }

    // ---------- 몸짓 ----------
    @discardableResult
    func play(_ name: String, _ o: Opt = Opt(), now: Double) -> Bool {
        guard let id = animId(name) else { return false }
        let from = plan != nil ? freeze(now) : nil
        guard var p = makePlan(id, o) else { return false }
        if let from { p.segs[0].a = from; p.segs[0].m = .none; p.segs[0].d = max(260, p.segs[0].d * 0.8) }
        p.stay = o.stay
        plan = p; p0 = now; playing = name; onDone = o.done
        return true
    }
    func release(now: Double) {
        guard let P = plan, P.stay else { return }
        let from = freeze(now)
        plan = GyeolPlan(segs: [GyeolSeg(a: from, m: .none, b: baseFrame, d: 420, stop: true)], total: 420)
        p0 = now; playing = "back"; onDone = nil
    }
    /// 앉기·서기: 바탕을 바꾸고 일어나기·앉기 장면으로 잇는다
    func setPose(_ p: String, now: Double) {
        guard p != pose else { return }
        let from = freeze(now)
        pose = p
        let via = bk.anims[p == "stand" ? "p:up" : "p:down"]
        plan = GyeolPlan(segs: [GyeolSeg(a: from, m: bk.mid(via?.mid.first), b: baseFrame, d: 520, stop: true)], total: 520)
        p0 = now; playing = "pose"; onDone = nil
    }
    /// 그릴 때마다 부른다. 끝난 몸짓을 거두고, 더 그릴 게 있으면 true
    func tick(_ now: Double) -> Bool {
        guard let P = plan else { return false }
        let end = P.total + 120
        if now - p0 > end {
            if P.stay { return false }   // 머무는 동안은 그리기를 쉰다
            plan = nil; playing = ""
            let d = onDone; onDone = nil; d?()
            return plan != nil
        }
        return true
    }
}
#endif
