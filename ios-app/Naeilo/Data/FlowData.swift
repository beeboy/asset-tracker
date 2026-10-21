import Foundation

/// 차트 탭 이름·아이콘. 이름이 아직 확정 전이라 여기 한 곳만 바꾸면 된다
enum FlowTab {
    static let name = "흐름"
    static let icon = "point.bottomleft.forward.to.point.topright.scurvepath"   // 축 없는 곡선 (2026-10-21)
}

/// 일봉 하나 (시가·고가·저가·종가·거래량)
struct Candle { var o, h, l, c, v: Double }

/// 추세 점: 고점(high) 또는 저점
struct FlowPivot { let i: Int; let price: Double; let high: Bool }

/// 추세 띠 한 칸: from…to 구간의 추세 (1 상승, -1 하락, 0 횡보)
struct FlowTrend { let from: Int; let to: Int; let dir: Int }

/// 지지·저항 가로선: 여러 번 받치거나 튕긴 가격. touches 가 많을수록 진하다
struct FlowLevel: Identifiable { let id: Int; let price: Double; let from: Int; let touches: Int; let support: Bool }

/// 패턴 이름표. strong = 확정되고 거래량이 받쳐 준 것(진하게), 아니면 흐린 점선
struct FlowPattern: Identifiable {
    enum Kind: String { case doubleBottom, doubleTop, headShoulders, ascTriangle, descTriangle, bullFlag, bearFlag, hammer, shootingStar, bullEngulf, bearEngulf, insideBar }
    let id: Int
    let kind: Kind
    let from: Int, to: Int
    let price: Double       // 이름표를 붙일 가격 (아래쪽 패턴은 저가, 위쪽은 고가)
    let below: Bool         // 이름표를 캔들 아래에
    let strong: Bool
    var big: Bool { [.doubleBottom, .doubleTop, .headShoulders, .ascTriangle, .descTriangle, .bullFlag, .bearFlag].contains(kind) }
}

/// 차트 한 종목의 미리 계산해 둔 그림 재료
struct FlowSeries {
    let candles: [Candle]
    let dates: [String]?           // 실데이터는 거래일 (DRNK 샘플은 없음: 어제까지 하루 한 칸)
    let pivots: [FlowPivot]
    let pivotSets: [[FlowPivot]]   // 추세 점: 일봉·주봉·월봉으로 볼 때 (작은 흔들림은 넓게 볼수록 뺀다)
    let trends: [[FlowTrend]]      // 0 = 일(작은 흐름), 1 = 주, 2 = 월(큰 흐름)
    let levels: [FlowLevel]
    let patterns: [FlowPattern]
    let live: Bool                 // 마지막 칸이 장중 오늘 캔들 (흐리게 그리고, 거기 닿은 패턴은 흐린 점선)
    var base: Double { candles.first?.c ?? 1 }
    /// '+x% 시작 대비'
    func pct(_ v: Double) -> Double { (v / base - 1) * 100 }

    init(candles: [Candle], dates: [String]? = nil, live: Bool = false) {
        self.candles = candles
        self.dates = dates
        self.live = live
        let c = candles.map(\.c)
        let zz = [0.06, 0.12, 0.22].map { FlowCalc.zigzag(c, $0) }
        pivotSets = zz.map { $0.map { FlowPivot(i: $0.i, price: $0.high ? candles[$0.i].h : candles[$0.i].l, high: $0.high) } }
        pivots = pivotSets[0]
        trends = zz.map { FlowCalc.trends($0, c.count) }
        levels = FlowCalc.levels(pivots, last: c.last ?? 0)
        let pts = FlowCalc.patterns(candles, pivots), n = candles.count
        // 장중 오늘 캔들에 닿은 패턴은 아직 바뀔 수 있어 확정이 아니다 (마감 뒤 일봉으로 다시 정한다)
        patterns = live ? pts.map { $0.to < n - 1 ? $0 : FlowPattern(id: $0.id, kind: $0.kind, from: $0.from, to: $0.to, price: $0.price, below: $0.below, strong: false) } : pts
    }
}

enum FlowCalc {
    /// 지그재그: 종가가 마지막 극점에서 th 만큼 되돌면 그 극점을 고점·저점으로 확정
    static func zigzag(_ c: [Double], _ th: Double) -> [FlowPivot] {
        guard c.count > 2 else { return [] }
        var out: [FlowPivot] = [], dir = 0, ext = 0, hi = 0, lo = 0
        for i in 1..<c.count {
            switch dir {
            case 0:
                if c[i] > c[hi] { hi = i }
                if c[i] < c[lo] { lo = i }
                if c[i] >= c[lo] * (1 + th) { out.append(.init(i: lo, price: c[lo], high: false)); dir = 1; ext = i }
                else if c[i] <= c[hi] * (1 - th) { out.append(.init(i: hi, price: c[hi], high: true)); dir = -1; ext = i }
            case 1:
                if c[i] > c[ext] { ext = i }
                else if c[i] <= c[ext] * (1 - th) { out.append(.init(i: ext, price: c[ext], high: true)); dir = -1; ext = i }
            default:
                if c[i] < c[ext] { ext = i }
                else if c[i] >= c[ext] * (1 + th) { out.append(.init(i: ext, price: c[ext], high: false)); dir = 1; ext = i }
            }
        }
        return out
    }

    /// 고점·저점이 앞의 같은 종류보다 높은지로 추세: 둘 다 높으면 상승, 둘 다 낮으면 하락, 섞이면 횡보
    static func trends(_ p: [FlowPivot], _ n: Int) -> [FlowTrend] {
        guard n > 0 else { return [] }
        var out: [FlowTrend] = [], lastH: [Double] = [], lastL: [Double] = [], start = 0, dir = 0
        for k in p.indices {
            if p[k].high { lastH.append(p[k].price) } else { lastL.append(p[k].price) }
            var d = 0
            if lastH.count >= 2 && lastL.count >= 2 {
                let hUp = lastH[lastH.count - 1] > lastH[lastH.count - 2] * 1.005
                let hDn = lastH[lastH.count - 1] < lastH[lastH.count - 2] * 0.995
                let lUp = lastL[lastL.count - 1] > lastL[lastL.count - 2] * 1.005
                let lDn = lastL[lastL.count - 1] < lastL[lastL.count - 2] * 0.995
                d = hUp && lUp ? 1 : hDn && lDn ? -1 : 0
            }
            if d != dir { if p[k].i > start { out.append(.init(from: start, to: p[k].i, dir: dir)) }; start = p[k].i; dir = d }
        }
        out.append(.init(from: start, to: n - 1, dir: dir))
        return out
    }

    /// 지지·저항: 2% 안에 모인 고점·저점 묶음. 두 번 이상 닿은 것만, 많이 닿은 순 6개
    static func levels(_ p: [FlowPivot], last: Double) -> [FlowLevel] {
        let s = p.sorted { $0.price < $1.price }
        var groups: [[FlowPivot]] = []
        for x in s {
            if let g = groups.last, let m = g.first, x.price <= m.price * 1.02 { groups[groups.count - 1].append(x) } else { groups.append([x]) }
        }
        return groups.filter { $0.count >= 2 }
            .sorted { $0.count != $1.count ? $0.count > $1.count : ($0.map(\.i).max() ?? 0) > ($1.map(\.i).max() ?? 0) }
            .prefix(6).enumerated()
            .map { k, g in
                let price = g.map(\.price).reduce(0, +) / Double(g.count)
                return FlowLevel(id: k, price: price, from: g.map(\.i).min() ?? 0, touches: g.count, support: price < last)
            }
    }

    /// 패턴 찾기. 캔들 패턴은 고점·저점 근처(±1일)에서만 본다 (아무 데나 붙이면 헛짚는 일이 많다)
    static func patterns(_ k: [Candle], _ p: [FlowPivot]) -> [FlowPattern] {
        var out: [FlowPattern] = []
        // 큰 패턴은 후보로 모았다가 확정된 것부터 구간이 겹치지 않게 고른다
        var cands: [(FlowPattern, Bool)] = []
        func add(_ kind: FlowPattern.Kind, _ a: Int, _ b: Int, _ price: Double, _ below: Bool, _ strong: Bool, conf: Bool = true) {
            let pt = FlowPattern(id: 0, kind: kind, from: a, to: b, price: price, below: below, strong: strong)
            if pt.big { cands.append((pt, conf)) } else { out.append(.init(id: out.count, kind: kind, from: a, to: b, price: price, below: below, strong: strong)) }
        }
        // 거래량이 직전 20일 평균의 1.2배 이상이면 받쳐 준 것
        func volOK(_ i: Int) -> Bool {
            let a = max(0, i - 20); guard i > a else { return false }
            let avg = k[a..<i].map(\.v).reduce(0, +) / Double(i - a)
            return k[i].v >= avg * 1.2
        }
        // 몸통이 길다/짧다는 직전 10개 평균 몸통과 견준다 (TA-Lib 방식)
        func avgBody(_ i: Int) -> Double {
            let a = max(0, i - 10); guard i > a else { return abs(k[i].c - k[i].o) }
            return k[a..<i].map { abs($0.c - $0.o) }.reduce(0, +) / Double(i - a)
        }
        let n = k.count
        var used = Set<Int>()
        for pv in p {
            for i in max(1, pv.i - 1)...min(n - 1, pv.i + 1) where !used.contains(i) {
                let x = k[i], y = k[i - 1]
                let body = abs(x.c - x.o), top = max(x.o, x.c), bot = min(x.o, x.c)
                let upW = x.h - top, dnW = bot - x.l, range = max(1e-9, x.h - x.l)
                var kind: FlowPattern.Kind? = nil
                if !pv.high && dnW >= 2 * max(body, range * 0.05) && upW <= range * 0.15 && body <= avgBody(i) * 1.2 { kind = .hammer }
                else if pv.high && upW >= 2 * max(body, range * 0.05) && dnW <= range * 0.15 && body <= avgBody(i) * 1.2 { kind = .shootingStar }
                else if !pv.high && y.c < y.o && x.c > x.o && x.o <= y.c && x.c >= y.o && body > avgBody(i) { kind = .bullEngulf }
                else if pv.high && y.c > y.o && x.c < x.o && x.o >= y.c && x.c <= y.o && body > avgBody(i) { kind = .bearEngulf }
                else if i > pv.i && x.h < y.h && x.l > y.l && (y.h - y.l) > avgBody(i) * 1.5 { kind = .insideBar }
                if let kd = kind {
                    let below = !pv.high
                    add(kd, kd == .bullEngulf || kd == .bearEngulf || kd == .insideBar ? i - 1 : i, i, below ? min(x.l, y.l) : max(x.h, y.h), below, volOK(i))
                    used.insert(i); break
                }
            }
        }
        // 큰 패턴: 고점·저점 배치로. 확정(목선 돌파) 전이면 흐리게. 꼬리 끝이 아니라 종가로 견준다
        let c = k.map(\.c)
        let p = p.map { FlowPivot(i: $0.i, price: c[$0.i], high: $0.high) }
        func firstClose(after a: Int, within w: Int, _ f: (Double) -> Bool) -> Int? {
            guard a + 1 < n else { return nil }
            return (a + 1...min(n - 1, a + w)).first { f(c[$0]) }
        }
        for j in p.indices.dropFirst(2) {
            let a = p[j - 2], m = p[j - 1], b = p[j]
            guard a.high == b.high, a.high != m.high else { continue }
            let gap = b.i - a.i
            guard gap >= 10 && gap <= 150, abs(b.price / a.price - 1) <= 0.03 else { continue }
            if !a.high {
                let hit = firstClose(after: b.i, within: 60) { $0 > m.price }
                guard hit != nil || b.i > n - 60 else { continue }   // 확정 안 된 지난 것은 버린다
                // 확정 전에 두 바닥 아래로 내려갔으면 깨진 모양
                if hit == nil, firstClose(after: b.i, within: n) { $0 < min(a.price, b.price) * 0.99 } != nil { continue }
                let to = hit ?? n - 1
                add(.doubleBottom, a.i, to, min(a.price, b.price), true, hit != nil && volOK(hit!), conf: hit != nil)
            } else {
                let hit = firstClose(after: b.i, within: 60) { $0 < m.price }
                guard hit != nil || b.i > n - 60 else { continue }
                if hit == nil, firstClose(after: b.i, within: n) { $0 > max(a.price, b.price) * 1.01 } != nil { continue }
                let to = hit ?? n - 1
                add(.doubleTop, a.i, to, max(a.price, b.price), false, hit != nil && volOK(hit!), conf: hit != nil)
            }
        }
        for j in p.indices.dropFirst(4) {
            let h1 = p[j - 4], l1 = p[j - 3], h2 = p[j - 2], l2 = p[j - 1], h3 = p[j]
            guard h1.high, h2.high, h3.high else { continue }
            guard h2.price > max(h1.price, h3.price) * 1.03, abs(h3.price / h1.price - 1) <= 0.05, h3.i - h1.i <= 220 else { continue }
            let neck = min(l1.price, l2.price)
            let hit = firstClose(after: h3.i, within: 60) { $0 < neck }
            guard hit != nil || h3.i > n - 60 else { continue }
            if hit == nil, firstClose(after: h3.i, within: n) { $0 > h2.price } != nil { continue }
            let to = hit ?? n - 1
            add(.headShoulders, h1.i, to, h2.price, false, hit != nil && volOK(hit!), conf: hit != nil)
        }
        // 삼각형: 고점 둘이 같은 높이(1.5% 안)인데 저점이 높아지면 상승삼각형, 반대면 하락삼각형. 평평한 쪽을 종가가 뚫으면 확정
        for j in p.indices.dropFirst(3) {
            let w = p[(j - 3)...j], hs = w.filter(\.high), ls = w.filter { !$0.high }
            guard hs.count == 2, ls.count == 2, w.last!.i - w.first!.i >= 15, w.last!.i - w.first!.i <= 150 else { continue }
            let (h1, h2, l1, l2) = (hs[0].price, hs[1].price, ls[0].price, ls[1].price)
            if abs(h2 / h1 - 1) <= 0.015 && l2 > l1 * 1.02 {
                let top = max(h1, h2), hit = firstClose(after: p[j].i, within: 40) { $0 > top * 1.005 }
                guard hit != nil || p[j].i > n - 40 else { continue }
                let to = hit ?? n - 1
                add(.ascTriangle, w.first!.i, to, top, false, hit != nil && volOK(hit!), conf: hit != nil)
            } else if abs(l2 / l1 - 1) <= 0.015 && h2 < h1 * 0.98 {
                let bot = min(l1, l2), hit = firstClose(after: p[j].i, within: 40) { $0 < bot * 0.995 }
                guard hit != nil || p[j].i > n - 40 else { continue }
                let to = hit ?? n - 1
                add(.descTriangle, w.first!.i, to, bot, true, hit != nil && volOK(hit!), conf: hit != nil)
            }
        }
        // 플래그: 짧게 12% 넘게 오른(내린) 깃대 뒤, 그 절반 안에서 쉬다가 깃대 끝을 다시 넘으면 확정
        var i = 12
        while i < n - 5 {
            defer { i += 1 }
            for up in [true, false] {
                guard let m = (3...12).first(where: { up ? c[i] / c[i - $0] >= 1.12 : c[i] / c[i - $0] <= 0.88 }) else { continue }
                let poleTop = c[i], half = abs(c[i] - c[i - m]) / 2
                // 깃대 끝이 그 뒤 5일 안 최고(최저)여야 한다
                guard (i + 1...min(n - 1, i + 5)).allSatisfy({ up ? c[$0] <= poleTop * 1.005 : c[$0] >= poleTop * 0.995 }) else { continue }
                var t = i + 1, broke: Int? = nil
                while t < min(n, i + 26) {
                    if up ? c[t] > poleTop * 1.005 : c[t] < poleTop * 0.995 { broke = t; break }
                    if up ? c[t] < poleTop - half : c[t] > poleTop + half { break }
                    t += 1
                }
                let rest = (broke ?? t) - i
                guard rest >= 5 else { continue }
                if broke == nil && !(t >= n - 1 && i > n - 26) { continue }   // 깨졌거나 시간이 다 된 건 버리고, 지금 쉬는 중이면 흐리게
                let to = broke ?? n - 1
                add(up ? .bullFlag : .bearFlag, i - m, to, poleTop, !up, broke != nil && volOK(broke!), conf: broke != nil)
                i = to
                break
            }
        }
        var spans: [ClosedRange<Int>] = []
        for (pt, _) in cands.enumerated().sorted(by: { ($0.element.1 ? 0 : 1, $0.offset) < ($1.element.1 ? 0 : 1, $1.offset) }).map({ $0.element })
        where !spans.contains(where: { $0.overlaps(pt.from...pt.to) }) {
            spans.append(pt.from...pt.to)
            out.append(.init(id: out.count, kind: pt.kind, from: pt.from, to: pt.to, price: pt.price, below: pt.below, strong: pt.strong))
        }
        return out
    }
}

/// 샘플 종목 DRNK: 종가 1010일(drnk_ratio)에 시가·고가·저가·거래량을 시드 고정으로 만들어 붙인다
enum DrnkSample {
    static let series: FlowSeries = FlowSeries(candles: candles(StubPriceProvider().drnkRatio))

    /// 같은 시드면 늘 같은 값 (SplitMix64)
    struct RNG {
        var s: UInt64
        mutating func next() -> Double {
            s &+= 0x9E3779B97F4A7C15
            var z = s
            z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
            z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
            return Double((z ^ (z >> 31)) >> 11) / Double(1 << 53)
        }
        mutating func gauss() -> Double { sqrt(-2 * log(max(1e-12, next()))) * cos(2 * .pi * next()) }
    }

    static func candles(_ r: [Double]) -> [Candle] {
        guard r.count > 2 else { return r.map { Candle(o: $0, h: $0, l: $0, c: $0, v: 1) } }
        var g = RNG(s: 0xD81C)
        var k: [Candle] = r.indices.map { i in
            let c = r[i], prev = i > 0 ? r[i - 1] : c
            let o = prev * (1 + g.gauss() * 0.004)
            let h = max(o, c) * (1 + abs(g.gauss()) * 0.007)
            let l = min(o, c) * (1 - abs(g.gauss()) * 0.007)
            let ret = abs(c / prev - 1)
            let v = (0.7 + ret * 40) * exp(g.gauss() * 0.25)
            return Candle(o: o, h: h, l: l, c: c, v: v)
        }
        // 패턴 심기: 6% 고점·저점 자리에 해머·유성·포위형·인사이드바를 번갈아. 거래량은 셋 중 하나만 적게 (흐린 이름표)
        let p = FlowCalc.zigzag(r, 0.06)
        for (j, pv) in p.enumerated() where pv.i > 0 && pv.i < r.count - 2 {
            let i = pv.i, c = r[i], weak = j % 3 == 2
            switch (pv.high, j / 2 % 3) {
            case (false, 0):   // 해머: 위쪽 작은 몸통 + 긴 아래꼬리
                let o = c * 0.997
                k[i].o = o; k[i].h = c * 1.001; k[i].l = o - c * 0.03
            case (true, 0):    // 유성: 아래쪽 작은 몸통 + 긴 위꼬리
                let o = c * 1.003
                k[i].o = o; k[i].l = c * 0.999; k[i].h = o + c * 0.03
            case (false, 1) where r[i + 1] > r[i]:   // 강세 포위형: 음봉 다음 그 몸통을 덮는 양봉
                k[i].o = c + (r[i + 1] - c) * 0.6; k[i].h = max(k[i].h, k[i].o * 1.002)
                k[i + 1].o = c * 0.997; k[i + 1].l = min(k[i + 1].l, k[i + 1].o * 0.998)
                k[i + 1].h = max(k[i + 1].h, r[i + 1] * 1.002)
            case (true, 1) where r[i + 1] < r[i]:    // 약세 포위형
                k[i].o = c - (c - r[i + 1]) * 0.6; k[i].l = min(k[i].l, k[i].o * 0.998)
                k[i + 1].o = c * 1.003; k[i + 1].h = max(k[i + 1].h, k[i + 1].o * 1.002)
                k[i + 1].l = min(k[i + 1].l, r[i + 1] * 0.998)
            default:           // 인사이드바: 극점 날 크게, 다음 날 그 안에
                k[i].h = max(k[i].o, c) * 1.02; k[i].l = min(k[i].o, c) * 0.98
                let x = k[i + 1]
                k[i + 1].h = min(max(x.o, x.c) * 1.003, k[i].h * 0.999)
                k[i + 1].l = max(min(x.o, x.c) * 0.997, k[i].l * 1.001)
                if k[i + 1].o > k[i].h * 0.999 || k[i + 1].o < k[i].l * 1.001 { k[i + 1].o = (k[i].h + k[i].l) / 2 }
                k[i + 1].h = max(k[i + 1].h, max(k[i + 1].o, x.c)); k[i + 1].l = min(k[i + 1].l, min(k[i + 1].o, x.c))
            }
            let d = pv.high || j / 2 % 3 == 0 ? i : i + 1
            k[d].v *= weak ? 0.5 : 2.2
        }
        return k
    }
}

/// 차트 탭 실데이터 캔들 저장: 종목(사이트 티커) → 거래일별 시가·고가·저가·종가·거래량.
/// 토스는 TossHistory.fetch 가 같은 응답에서 같이 남기고, 알파카는 FlowAlpaca 가 받는다. 위젯이 읽는 toss_history.json 과는 따로 둔다
struct FlowBars: Codable {
    struct Row { var o, h, l, c, v: Double }
    struct Series: Codable {
        var d: [String] = [], o: [Double] = [], h: [Double] = [], l: [Double] = [], c: [Double] = [], v: [Double] = []
        var candles: [Candle] { d.indices.map { Candle(o: o[$0], h: h[$0], l: l[$0], c: c[$0], v: v[$0]) } }
    }
    var b: [String: Series] = [:]
    var day: [String: String] = [:]     // 마지막으로 받은 날 표시 (알파카: 하루 한 번)

    static let tossName = "flow_toss.json", alpacaName = "flow_alpaca.json"
    static func read(_ name: String) -> FlowBars { Store.read(FlowBars.self, name) ?? FlowBars() }
    static func write(_ f: FlowBars, _ name: String) { Store.write(f, name) }

    /// 새로 받은 줄을 잇는다. full 이면 갈아 끼우고, keep 에 맞는 날만 남긴다 (끝나기 전 오늘 일봉·3년 넘은 날 빼기)
    mutating func merge(_ k: String, _ rows: [String: Row], full: Bool, keep: (String) -> Bool) {
        var all: [String: Row] = [:]
        if !full, let s = b[k] { for i in s.d.indices { all[s.d[i]] = Row(o: s.o[i], h: s.h[i], l: s.l[i], c: s.c[i], v: s.v[i]) } }
        for (d, r) in rows { all[d] = r }
        let ds = all.keys.filter(keep).sorted()
        var s = Series()
        for d in ds { let r = all[d]!; s.d.append(d); s.o.append(r.o); s.h.append(r.h); s.l.append(r.l); s.c.append(r.c); s.v.append(r.v) }
        b[k] = s
    }
}

/// 알파카 일봉 (미국 종목, 사용자 본인 키). /v2/stocks/bars 한 번에 여러 종목, feed=sip (거래량이 전체 시장 기준. IEX 는 일부라 안 씀).
/// 종목마다 하루 한 번(장 끝난 뒤 한 번 더), 처음엔 3년치, 그다음엔 최근 10일만. 부르는 사이를 0.35초 넘게 띄워 분당 200회 한도 아래로
enum FlowAlpaca {
    static let base = "https://data.alpaca.markets/v2/stocks/bars"
    @MainActor private static var last = Date.distantPast

    /// 뉴욕 오늘 날짜와 장 마감(16:10) 지났는지
    static func nyDay() -> (String, Bool) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/New_York")!
        let c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: Date())
        return (String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!), c.hour! * 60 + c.minute! >= 16 * 60 + 10)
    }

    /// keys: 사이트 티커 → 알파카 기호. 받은 게 있으면 true
    @MainActor
    static func fetch(_ keys: [String: String]) async -> Bool {
        guard let k = AlpacaKeys.load(), !keys.isEmpty else { return false }
        var f = FlowBars.read(FlowBars.alpacaName)
        let (md, closed) = nyDay(), stamp = closed ? Day.today + "c" : Day.today
        let from = Day.str(Date().addingTimeInterval(-(3 * 365 + 15) * 86400))
        let need = keys.filter { f.day[$0.key] != stamp || f.b[$0.key] == nil }
        guard !need.isEmpty else { return false }
        // 처음 받는 종목이 하나라도 있으면 3년치, 아니면 마지막 날 10일 전부터
        let fresh = need.keys.contains { (f.b[$0]?.d.count ?? 0) < 30 }
        let start = fresh ? from : Day.str(Date().addingTimeInterval(-14 * 86400))
        // 무료 키는 최근 15분 SIP 를 못 묻는다: 끝은 20분 전
        let end = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-20 * 60))
        var bySym: [String: String] = [:]   // 알파카 기호 → 사이트 티커
        for (key, sym) in need { bySym[sym.uppercased()] = key }
        var rows: [String: [String: FlowBars.Row]] = [:], token: String? = nil, got = false
        for _ in 0..<20 {
            var c = URLComponents(string: base)!
            c.queryItems = [.init(name: "symbols", value: bySym.keys.sorted().joined(separator: ",")),
                            .init(name: "timeframe", value: "1Day"), .init(name: "start", value: start), .init(name: "end", value: end),
                            .init(name: "adjustment", value: "raw"), .init(name: "feed", value: "sip"), .init(name: "limit", value: "10000")]
            if let t = token { c.queryItems!.append(.init(name: "page_token", value: t)) }
            let wait = 0.35 - Date().timeIntervalSince(last)
            if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
            last = Date()
            var r = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
            r.setValue(k.id, forHTTPHeaderField: "APCA-API-KEY-ID")
            r.setValue(k.secret, forHTTPHeaderField: "APCA-API-SECRET-KEY")
            guard let (d, resp) = try? await URLSession.shared.data(for: r), (resp as? HTTPURLResponse)?.statusCode == 200,
                  let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { break }
            got = true
            for (sym, arr) in o["bars"] as? [String: Any] ?? [:] {
                guard let key = bySym[sym.uppercased()] else { continue }
                for v in arr as? [[String: Any]] ?? [] {
                    guard let t = v["t"] as? String, t.count >= 10, let cl = num(v["c"]), let op = num(v["o"]), let h = num(v["h"]), let l = num(v["l"]), cl > 0, op > 0 else { continue }
                    rows[key, default: [:]][String(t.prefix(10))] = .init(o: op, h: max(h, op, cl), l: min(l, op, cl), c: cl, v: num(v["v"]) ?? 0)
                }
            }
            token = o["next_page_token"] as? String
            if token == nil { break }
        }
        guard got else { return false }
        for key in need.keys {
            f.merge(key, rows[key] ?? [:], full: fresh && (f.b[key]?.d.count ?? 0) < 30, keep: { $0 >= from && ($0 < md || ($0 == md && closed)) })
            f.day[key] = stamp
        }
        FlowBars.write(f, FlowBars.alpacaName)
        return true
    }
}

/// 차트 탭이 보는 실데이터: 토스 캔들을 먼저, 없으면 알파카. 종목마다 분석(추세·패턴)은 한 번만 하고 마지막 날이 바뀔 때만 다시
@MainActor @Observable
final class FlowStore {
    static let shared = FlowStore()
    private(set) var toss = FlowBars.read(FlowBars.tossName)
    private(set) var alpaca = FlowBars.read(FlowBars.alpacaName)
    private(set) var loading: Set<String> = []
    private(set) var failed: Set<String> = []    // 받아 보았는데 일봉이 없는 종목 (빈 칸 대신 '못 받았어요')
    @ObservationIgnored private var memo: [String: (String, FlowSeries)] = [:]
    @ObservationIgnored private var today: [String: (String, Candle)] = [:]   // 장중 오늘 캔들: 시가는 전일 종가, 고가·저가는 앱이 본 지금 가격으로 넓힌다

    func reload() {
        toss = TossKeys.on ? FlowBars.read(FlowBars.tossName) : FlowBars()
        alpaca = AlpacaKeys.on ? FlowBars.read(FlowBars.alpacaName) : FlowBars()
    }

    static func isKR(_ id: String) -> Bool { Sample.symbol(id)?.currency == .krw }
    /// 이 종목을 받을 키가 있는지: 한국은 토스, 미국은 토스나 알파카
    static func canFetch(_ id: String) -> Bool {
        guard id != "DRNK", id != KoreaEstimate.sumId else { return false }
        return TossKeys.on || (!isKR(id) && AlpacaKeys.on)
    }

    func bars(_ id: String) -> FlowBars.Series? {
        let k = SiteStats.key(id)
        if TossKeys.on, let s = toss.b[k], s.d.count >= 30 { return s }
        if AlpacaKeys.on, !Self.isKR(id), let s = alpaca.b[k], s.d.count >= 30 { return s }
        return nil
    }

    func series(_ id: String) -> FlowSeries? {
        if id == "DRNK" { return DrnkSample.series }
        guard let b = bars(id) else { return nil }
        let lv = live(id, b)
        let stamp = "\(b.d.count)" + (b.d.last ?? "") + (lv.map { "L\($0.1.c),\($0.1.h),\($0.1.l)" } ?? "")
        if let m = memo[id], m.0 == stamp { return m.1 }
        let s = lv.map { FlowSeries(candles: b.candles + [$0.1], dates: b.d + [$0.0], live: true) } ?? FlowSeries(candles: b.candles, dates: b.d)
        memo[id] = (stamp, s)
        return s
    }

    /// 그 시장의 오늘 날짜와, 장이 열린 평일인지 (한국 9:00, 미국 9:30 부터. 마감 뒤에도 일봉을 받기 전까지는 연 것으로)
    static func marketNow(kr: Bool) -> (String, Bool) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: kr ? "Asia/Seoul" : "America/New_York")!
        let c = cal.dateComponents([.year, .month, .day, .hour, .minute, .weekday], from: Date())
        let d = String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
        return (d, (2...6).contains(c.weekday!) && c.hour! * 60 + c.minute! >= (kr ? 9 * 60 : 9 * 60 + 30))
    }

    /// 장중 오늘 캔들 (날짜, 캔들). 지금 가격이 없거나, 일봉에 오늘이 이미 있거나, 전일 종가 그대로면(휴장) 없음
    private func live(_ id: String, _ b: FlowBars.Series) -> (String, Candle)? {
        guard let s = Sample.symbol(id), let prev = b.c.last, let last = b.d.last else { return nil }
        let q = Market.shared.quote(s)
        let (md, open) = Self.marketNow(kr: Self.isKR(id))
        guard q.live, q.last > 0, open, last < md, abs(q.last - prev) > 1e-9 else { return nil }
        var k = today[id].flatMap { $0.0 == md ? $0.1 : nil } ?? Candle(o: prev, h: max(prev, q.last), l: min(prev, q.last), c: q.last, v: 0)
        k.c = q.last; k.h = max(k.h, q.last); k.l = min(k.l, q.last)
        today[id] = (md, k)
        return (md, k)
    }

    /// 차트 탭을 열 때: 아직 없는 담은 종목을 받는다. 토스는 기존 시세 갱신 흐름으로, 알파카는 여기서 한 번에
    func ensure(_ ids: [String]) async {
        let want = ids.filter { Self.canFetch($0) }
        guard !want.isEmpty else { return }
        if TossKeys.on, want.contains(where: { bars($0) == nil }) {
            loading.formUnion(want.filter { bars($0) == nil })
            // 지금 값도 같이 받으니 담은 종목 전부로. 이미 받는 중이면 끝나길 기다렸다가 한 번 더 (그냥 건너뛰면 탭을 열어도 안 받는다)
            await Market.shared.refreshToss(held: Set(ids), force: true)
        }
        if AlpacaKeys.on {
            var keys: [String: String] = [:]
            for id in want where !Self.isKR(id) && (!TossKeys.on || toss.b[SiteStats.key(id)].map { $0.d.count < 30 } ?? true) {
                if let s = Sample.symbol(id), let c = AlpacaFeed.code(CustomSymbols.yahoo(for: s)) { keys[SiteStats.key(id)] = c }
            }
            if !keys.isEmpty {
                loading.formUnion(want.filter { bars($0) == nil })
                if await FlowAlpaca.fetch(keys) { reload() }
            }
        }
        loading = []
        failed = Set(want.filter { bars($0) == nil })
    }
}
