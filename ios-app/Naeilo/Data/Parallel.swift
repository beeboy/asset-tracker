import Foundation

// 평행 세계의 나 (기획서 notes/평행세계-앱-기획서-2026-10-12.md, v1).
// 세 명의 나가 같은 날 같은 금액에서 출발해 사이트 지난 종가로 달린다: ① 진짜 나 ② 담아 본 나(모의 바구니) ③ 인물 세계의 나.
// 화면에는 순위·수익률·평가액과 비율 요약만 낸다. 인물 세계·모의 바구니의 매매 내역(종목+수량+시점)은 만들지도 보여 주지도 않는다.

/// 모의 바구니 한 칸: 그날부터 이 비중 (티커 → 비중, 합 1). 고치면 그날부터 새 칸이 붙고 지난 칸은 그대로 남는다
struct PBasketSeg: Codable, Equatable { var from: String; var w: [String: Double] }
/// 본선 기록 한 줄: 그날 종가 기준 세 명의 수익률 (진짜·담아 본·인물). who = 그날 인물 세계의 인물
struct PDay: Codable, Equatable { var d: String; var r: [Double]; var who: String }

struct PWorld: Codable, Equatable {
    var opened: String              // 평행 세계를 연 날 (본선 시작)
    var start: String               // 예선 출발일 (연 날 정해 두고 바꾸지 않는다)
    var segs: [PBasketSeg]
    var who: String                 // 인물 세계의 인물
    var whoPick: String? = nil      // 사용자가 마지막으로 직접 바꾼 때 (ISO 시각, 동기화에서 나중 것을 쓴다). 횟수 제한 없음
    var days: [PDay] = []

    static let key = "parallel.v1"
    static func load() -> PWorld? { UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(PWorld.self, from: $0) } }
    static func save(_ w: PWorld?) {
        if let w, let d = try? JSONEncoder().encode(w) { UserDefaults.standard.set(d, forKey: key) } else { UserDefaults.standard.removeObject(forKey: key) }
    }
    /// 동기화: 기록은 날짜별로 합치고 (같은 날은 먼저 남은 값), 바구니는 더 나중에 고친 쪽
    static func merge(_ mine: PWorld?, _ remote: PWorld) -> PWorld {
        guard var a = mine else { return remote }
        var byDay: [String: PDay] = [:]
        for x in remote.days { byDay[x.d] = x }
        for x in a.days { byDay[x.d] = x }
        a.days = byDay.values.sorted { $0.d < $1.d }
        let la = a.segs.last?.from ?? "", lb = remote.segs.last?.from ?? ""
        if lb > la || (lb == la && remote.segs.count > a.segs.count) { a.segs = remote.segs }
        if (remote.whoPick ?? "") > (a.whoPick ?? "") { a.who = remote.who; a.whoPick = remote.whoPick }
        if remote.opened < a.opened { a.opened = remote.opened; a.start = remote.start }
        return a
    }
}

/// 경주 결과 (그래프·순위·카드가 같이 쓴다)
struct PRace {
    let dates: [String]
    let r: [[Double]]          // [나][칸] 출발 대비 수익률 (출발 = 0)
    let open: Int              // 본선이 시작하는 칸 (연 날 이후 첫 거래일). 없으면 dates.count
    let V0: Double             // 출발 금액 (원, 진짜 나의 출발일 평가액)
    let cash: (now: Double, before: Double)   // 인물 세계 현금 비중 (지금, 약 한 달 전)
    let top: (now: Double, before: Double)    // 인물 세계 가장 큰 한 종목 비중
    let segRet: [Double]       // 바구니 칸마다 그 칸 동안 수익률
    let contrib: Double        // 수아 세계: 넣은 적립 합 (원)
    var last: Int { dates.count - 1 }
    func rank(at t: Int) -> [Int] { [0, 1, 2].sorted { r[$0][t] > r[$1][t] } }
    func place(_ k: Int, at t: Int) -> Int { (rank(at: t).firstIndex(of: k) ?? 0) + 1 }
}

enum Parallel {
    static let names = ["진짜 나", "담아 본 나", "인물 세계의 나"]

    /// v1에 담을 수 있는 종목: 사이트가 지난 종가를 모으는 종목 + 견본 ETF (VTV·USMV)
    struct Item { let id: String; let name: String }
    static let universe: [Item] = [
        .init(id: "SPY", name: "S&P500 ETF"), .init(id: "QQQ", name: "나스닥100 ETF"), .init(id: "VTV", name: "가치주 ETF"), .init(id: "USMV", name: "저변동 ETF"), .init(id: "SCHD", name: "배당 ETF"),
        .init(id: "SGOV", name: "단기 국채 ETF"), .init(id: "GLD", name: "금 ETF"), .init(id: "EWY", name: "한국 ETF"), .init(id: "SMH", name: "반도체 ETF"), .init(id: "SOXX", name: "반도체 ETF (SOXX)"),
        .init(id: "TSLA", name: "테슬라"), .init(id: "NVDA", name: "엔비디아"), .init(id: "AAPL", name: "애플"), .init(id: "SPCX", name: "스페이스X"), .init(id: "MANA", name: "MANA"),
    ]
    /// 바구니 한 칸을 글로 (계산 기억 키·지난 바구니 표시)
    static func segText(_ g: PBasketSeg) -> String {
        let ks: [String] = g.w.keys.sorted()
        return g.from + ":" + ks.map { k in k + "=" + String(g.w[k] ?? 0) }.joined(separator: ",")
    }
    static func name(_ id: String) -> String { universe.first { $0.id == id }?.name ?? id }
    /// 성장·개별 종목 (바구니가 공격적인지 볼 때)
    static let growth: Set<String> = ["QQQ", "SMH", "SOXX", "TSLA", "NVDA", "AAPL", "SPCX", "MANA"]

    /// 견본: 회복 루트 계획 고르기와 목표 루트 구성 비교의 묶음을 대표 ETF로 (종목 추천이 아니라 지수 묶음)
    struct Template { let id: String; let name: String; let w: [String: Double] }
    static let templates: [Template] = [
        .init(id: "push", name: "도전", w: ["QQQ": 0.7, "VTV": 0.3]),
        .init(id: "balance", name: "균형", w: ["QQQ": 1.0 / 3, "VTV": 1.0 / 3, "USMV": 1.0 / 3]),
        .init(id: "guard", name: "방어", w: ["VTV": 0.2, "USMV": 0.6, "SGOV": 0.2]),
        .init(id: "index", name: "지수 중심", w: ["SPY": 0.8, "SGOV": 0.2]),
        .init(id: "grow", name: "성장 더하기", w: ["SPY": 0.6, "QQQ": 0.3, "SGOV": 0.1]),
        .init(id: "steady", name: "버팀목 더하기", w: ["SPY": 0.5, "USMV": 0.3, "SGOV": 0.2]),
    ]
    /// 견본 ETF 시세가 아직 없으면 미국 전체 지수로 (AppModel.proxyKeys 와 같은 생각)
    static func resolve(_ w: [String: Double]) -> [String: Double] {
        var out: [String: Double] = [:]
        for (k, v) in w {
            let id = has(k) ? k : (k == "SGOV" ? k : "SPY")
            out[id, default: 0] += v
        }
        return out
    }

    /// 지난 종가 (사이트 파일 → 기기가 받은 값 → 빌드 때 넣은 파일). 가상 종목 DRNK 는 빌드 때 넣은 샘플 기록
    static func hist(_ id: String) -> YahooSample.Hist? {
        if id == "DRNK" { return YahooSample.bundled["DRNK"] }
        return PriceHistory.of(id)
    }
    static func has(_ id: String) -> Bool { (hist(id)?.closes.count ?? 0) > 1 }

    /// 시세를 받아 둘 사이트 티커: 바구니 종목 + 인물 세계 현금(SGOV) + 달력(SPY)
    static var wantKeys: Set<String> {
        var s: Set<String> = []
        guard let w = PWorld.load() else { return s }
        s.insert("SGOV"); s.insert("SPY")
        for g in w.segs { for k in g.w.keys { s.insert(k) } }
        return s
    }

    // MARK: 인물 규칙 (하루 한 번 종가 기준)
    static let rules: [String: String] = [
        "seri": "1년에 한 번, 처음 비율로 되돌려요.",
        "sio": "20%를 현금으로 들고 있다가, 고점보다 20% 빠지면 그 현금의 절반을 넣어요.",
        "seonbae": "한 종목 비중이 처음보다 10%p 넘게 커지면 그만큼 덜어 나머지에 나눠요.",
        "ir": "200일 평균선 위인 종목만 들고, 아래로 가면 그 몫을 현금(단기 국채)으로 옮겨요.",
        "sua": "매달 같은 금액을 넣고, 떨어진 달에는 1.5배를 넣어요.",
    ]

    /// 매칭: 평정 사다리에서 내 다음 칸 인물 (이르 → 수아 → 선배·시오 → 세리). 맨 위(세리)면 결이 다른 시오.
    /// 바구니가 공격적이면(성장·개별 종목 50% 이상) 반대 결(세리·선배)을 먼저. 언제나 지금 내 유형과 다른 인물
    static func match(type: String?, aggressive: Bool) -> String {
        var who: String
        switch type {
        case "ir": who = "sua"
        case "sua": who = aggressive ? "seonbae" : "sio"
        case "seonbae", "sio": who = "seri"
        case "seri": who = aggressive ? "seonbae" : "sio"
        default: who = "seri"
        }
        if aggressive && !["seri", "seonbae"].contains(who) { who = type == "seri" ? "seonbae" : "seri" }
        if who == type { who = who == "seri" ? "seonbae" : "seri" }
        return who
    }
    static func aggressive(_ w: [String: Double]) -> Bool { w.filter { growth.contains($0.key) }.values.reduce(0, +) >= 0.5 }

    // MARK: 계산
    struct Asset { let id: String; let usd: Bool; let h: YahooSample.Hist?; let last: Double }
    struct Input {
        let start: String
        let opened: String
        let base: [(id: String, usd: Bool, qty: Double, last: Double)]   // 진짜 나: 예선 수량 (지금 종목)
        let lots: [(d: String, q: [String: Double])]                      // 진짜 나: 본선 수량 기록 (앱 종목 이름)
        let segs: [PBasketSeg]
        let who: String
        let monthly: Double                                               // 수아 세계 적립 (원)
    }

    /// 달력: 미국 거래일 (SPY), 없으면 원/달러 날짜
    static func calendar() -> [String] { (hist("SPY") ?? hist("KRW=X"))?.dates ?? [] }

    /// 날짜별 원화 가격표 (앞 값으로 메움, 상장 전은 첫 종가)
    private static func table(_ a: Asset, _ dates: [String], _ fx: [Double]) -> [Double] {
        if a.id == "~CASH" { return dates.map { _ in 1 } }
        return dates.indices.map { t in
            let p: Double = a.h?.on(dates[t]) ?? a.h?.closes.first ?? a.last
            return a.usd ? p * fx[t] : p
        }
    }
    private static func fxTable(_ dates: [String]) -> [Double] {
        let h = hist("KRW=X"), now = Market.shared.fx.last
        return dates.map { h?.on($0) ?? h?.closes.first ?? now }
    }

    /// 바닥일: 지금 종목·수량 × 지난 종가로 본 평가액이 가장 낮았던 날
    static func bottomDay(_ base: [(id: String, usd: Bool, qty: Double, last: Double)]) -> String? {
        let dates = calendar()
        guard dates.count > 1 else { return nil }
        let fx = fxTable(dates)
        var v = [Double](repeating: 0, count: dates.count)
        for b in base {
            let p = table(Asset(id: b.id, usd: b.usd, h: hist(b.id), last: b.last), dates, fx)
            for t in dates.indices { v[t] += b.qty * p[t] }
        }
        guard let k = v.indices.min(by: { v[$0] < v[$1] }) else { return nil }
        return dates[k]
    }
    /// 예선 출발일을 달력 안으로: 3년보다 앞서면 첫날, 20거래일은 남게
    static func clampStart(_ s: String) -> String {
        let dates = calendar()
        guard let first = dates.first else { return s }
        let latest = dates[max(0, dates.count - 21)]
        return min(max(s, first), latest)
    }

    static func run(_ inp: Input) -> PRace? {
        let all = calendar()
        guard let s0 = all.firstIndex(where: { $0 >= inp.start }) else { return nil }
        let dates = Array(all[s0...])
        let n = dates.count
        guard n >= 2 else { return nil }
        let fx = fxTable(dates)

        // 쓰는 종목 모두의 가격표
        var assets: [String: Asset] = [:]
        for b in inp.base { assets[b.id] = Asset(id: b.id, usd: b.usd, h: hist(b.id), last: b.last) }
        for l in inp.lots { for k in l.q.keys where assets[k] == nil {
            let s = Sample.symbol(k)
            assets[k] = Asset(id: k, usd: s?.currency != .krw, h: hist(k), last: s?.last ?? 0)
        } }
        for g in inp.segs { for k in g.w.keys where assets[k] == nil { assets[k] = Asset(id: k, usd: true, h: hist(k), last: hist(k)?.closes.last ?? 0) } }
        let cashId = has("SGOV") ? "SGOV" : "~CASH"
        if assets[cashId] == nil { assets[cashId] = Asset(id: cashId, usd: cashId == "SGOV", h: hist(cashId), last: 1) }
        var P: [String: [Double]] = [:]
        for (k, a) in assets { P[k] = table(a, dates, fx) }
        func p(_ k: String, _ t: Int) -> Double { P[k]?[t] ?? 0 }

        // ① 진짜 나: 전날 들고 있던 수량으로 하루 수익률을 잇는다 (수량을 고친 날 넣고 뺀 돈은 수익률에 넣지 않는다)
        var baseQ: [String: Double] = [:]
        for b in inp.base where b.qty > 0 { baseQ[b.id, default: 0] += b.qty }
        func qty(_ d: String) -> [String: Double] {
            guard d >= inp.opened, let l = inp.lots.last(where: { $0.d <= d }) else { return baseQ }
            return l.q
        }
        let V0 = baseQ.reduce(0.0) { $0 + $1.value * p($1.key, 0) }
        guard V0 > 0 else { return nil }
        var r0 = [Double](repeating: 0, count: n)
        var idx = 1.0
        for t in 1..<n {
            let q = qty(dates[t - 1])
            var num = 0.0, den = 0.0
            for (k, x) in q { num += x * p(k, t); den += x * p(k, t - 1) }
            if den > 0 && num > 0 { idx *= num / den }
            r0[t] = idx - 1
        }

        // ② 담아 본 나: 바구니 칸마다 그날 금액을 그 비중으로 나눠 들고 그대로 둔다
        let segs = inp.segs.sorted { $0.from < $1.from }
        guard !segs.isEmpty else { return nil }
        var r1 = [Double](repeating: 0, count: n), segRet: [Double] = []
        var units: [String: Double] = [:], cur = -1, segV = V0
        func value(_ u: [String: Double], _ t: Int) -> Double { u.reduce(0.0) { $0 + $1.value * p($1.key, t) } }
        func alloc(_ w: [String: Double], _ V: Double, _ t: Int) -> [String: Double] {
            let ok = w.filter { $0.value > 0 && p($0.key, t) > 0 }
            let tot = ok.values.reduce(0, +)
            guard tot > 0 else { return [cashId: V / max(1e-9, p(cashId, t))] }
            var u: [String: Double] = [:]
            for (k, x) in ok { u[k] = V * x / tot / p(k, t) }
            return u
        }
        for t in 0..<n {
            var k = cur
            while k + 1 < segs.count && (k + 1 == 0 || segs[k + 1].from <= dates[t]) { k += 1 }
            let V = t == 0 ? V0 : value(units, t)
            if k != cur {
                if cur >= 0 { segRet.append(segV > 0 ? V / segV - 1 : 0) }
                units = alloc(segs[max(0, k)].w, V, t); cur = k; segV = V
            }
            r1[t] = V / V0 - 1
        }
        if cur >= 0 { segRet.append(segV > 0 ? (V0 * (1 + r1[n - 1])) / segV - 1 : 0) }

        // ③ 인물 세계의 나: 진짜 나의 출발 비중에서 시작해 인물 규칙대로 스스로 움직인다
        var w0: [String: Double] = [:]
        for (k, x) in baseQ where p(k, 0) > 0 { w0[k] = x * p(k, 0) / V0 }
        var u = alloc(w0, inp.who == "sio" ? V0 * 0.8 : V0, 0)
        var cashU = inp.who == "sio" ? V0 * 0.2 / max(1e-9, p(cashId, 0)) : 0
        var parked: [String: Double] = [:]          // 이르: 평균선 아래로 내려가 현금으로 옮긴 몫 (현금 단위)
        var invested = V0, contrib = 0.0
        var peak = V0, armed = true
        var nextYear = 1, monthStartV = V0
        var r2 = [Double](repeating: 0, count: n)
        var cashW: [Int: Double] = [:], topW: [Int: Double] = [:]
        let sma = SMA(assets: assets)
        let before = max(0, n - 22)
        func charV(_ t: Int) -> Double { value(u, t) + (cashU + parked.values.reduce(0, +)) * p(cashId, t) }
        for t in 0..<n {
            let d = dates[t]
            switch inp.who {
            case "seri":
                if t > 0, let anniv = addYears(dates[0], nextYear), d >= anniv {
                    u = alloc(w0, value(u, t), t); nextYear += 1
                }
            case "seonbae":
                let V = value(u, t)
                var bestK = "", bestEx = 0.0
                if V > 0 { for (k, x) in u { let ex = x * p(k, t) / V - (w0[k] ?? 0); if ex > bestEx { bestEx = ex; bestK = k } } }
                if bestEx > 0.10 {
                    let cut = bestEx * V
                    u[bestK, default: 0] -= cut / max(1e-9, p(bestK, t))
                    let others = u.filter { $0.key != bestK && $0.value * p($0.key, t) > 0 }
                    let ov = others.reduce(0.0) { $0 + $1.value * p($1.key, t) }
                    if ov > 0 { for (j, x) in others { u[j] = x + cut * (x * p(j, t) / ov) / p(j, t) } }
                    else { cashU += cut / max(1e-9, p(cashId, t)) }
                }
            case "sio":
                let V = charV(t)
                if V > peak { peak = V; armed = true }
                else if armed && cashU > 0 && V <= peak * 0.8 {
                    let put = cashU / 2 * p(cashId, t)
                    cashU /= 2
                    for (k, x) in alloc(w0, put, t) { u[k, default: 0] += x }
                    armed = false
                }
            case "ir":
                for k in Array(u.keys) + Array(parked.keys) where k != cashId && k != "SGOV" {
                    guard let m = sma.at(k, d) else { continue }
                    let px = p(k, t)
                    if let c = parked[k] {
                        if px >= m { u[k, default: 0] += c * p(cashId, t) / max(1e-9, px); parked[k] = nil }
                    } else if px < m, let x = u[k], x > 0 {
                        parked[k] = x * px / max(1e-9, p(cashId, t)); u[k] = nil
                    }
                }
            case "sua":
                if t > 0 && inp.monthly > 0 && d.prefix(7) != dates[t - 1].prefix(7) {
                    let fell = charV(t - 1) < monthStartV
                    let c = inp.monthly * (fell ? 1.5 : 1)
                    for (k, x) in alloc(w0, c, t) { u[k, default: 0] += x }
                    invested += c; contrib += c
                    monthStartV = charV(t)
                }
            default: break
            }
            let V = charV(t)
            r2[t] = V / invested - 1
            if t == n - 1 || t == before {
                let cv = (cashU + parked.values.reduce(0, +)) * p(cashId, t) + (u[cashId] ?? 0) * p(cashId, t)
                cashW[t] = V > 0 ? cv / V : 0
                topW[t] = V > 0 ? (u.filter { $0.key != cashId }.map { $0.value * p($0.key, t) }.max() ?? 0) / V : 0
            }
        }
        let open = dates.firstIndex(where: { $0 >= inp.opened }) ?? n
        return PRace(dates: dates, r: [r0, r1, r2], open: open, V0: V0,
                     cash: (cashW[n - 1] ?? 0, cashW[before] ?? 0), top: (topW[n - 1] ?? 0, topW[before] ?? 0),
                     segRet: segRet, contrib: contrib)
    }

    static func addYears(_ d: String, _ y: Int) -> String? {
        guard let x = Day.date(d), let z = Calendar(identifier: .gregorian).date(byAdding: .year, value: y, to: x) else { return nil }
        return Day.str(z)
    }

    /// 200일 평균선 (그날까지의 종가 200개, 100개 안 되면 판단하지 않는다)
    struct SMA {
        var sums: [String: (dates: [String], pre: [Double])] = [:]
        init(assets: [String: Asset]) {
            for (k, a) in assets {
                guard let h = a.h else { continue }
                var pre = [0.0]; pre.reserveCapacity(h.closes.count + 1)
                for c in h.closes { pre.append(pre[pre.count - 1] + c) }
                sums[k] = (h.dates, pre)
            }
        }
        func at(_ k: String, _ d: String) -> Double? {
            guard let s = sums[k] else { return nil }
            var lo = 0, hi = s.dates.count - 1, i = -1
            while lo <= hi { let mid = (lo + hi) / 2; if s.dates[mid] <= d { i = mid; lo = mid + 1 } else { hi = mid - 1 } }
            guard i >= 99 else { return nil }
            let from = max(0, i - 199)
            return (s.pre[i + 1] - s.pre[from]) / Double(i + 1 - from)
        }
    }

    // MARK: 인물 말풍선 (위로만. 갈아타라·따라 하라는 말은 하지 않는다)
    static func line(_ who: String, place: Int, day: String) -> String {
        let lead = ["오늘은 이쪽이 앞섰네. 내일은 또 몰라.", "앞선 날도 있는 거지. 너는 너대로 가.", "숫자는 바뀌어. 같이 가는 건 그대로야."]
        let mid = ["다른 세계의 너도 버티고 있어.", "가운데쯤이야. 서두를 것 없어.", "세 명 다 너야. 오늘은 숨 한 번 쉬고 가."]
        let back = ["오늘은 저쪽이 앞섰네, 너는 너대로 가.", "뒤에 있는 날도 기록이야. 꾸며낼 수 없는 기록.", "괜찮아. 이 경주는 지는 쪽이 없어."]
        let own: [String: String] = [
            "seri": "저는 일 년에 한 번만 움직여요. 거기까지만요.",
            "sio": "기다리는 것도 하는 거야. 현금은 그래서 들고 있어.",
            "seonbae": "그게 정말 네 생각이야? 나는 커진 만큼만 덜어.",
            "ir": "나는 빨리 움직여. 그게 늘 맞는 건 아니고.",
            "sua": "매달 조금씩. 떨어진 달엔 조금 더.",
        ]
        let pool = (place == 1 ? lead : place == 3 ? back : mid) + [own[who] ?? ""].filter { !$0.isEmpty }
        let h = day.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0xFFFF }
        return pool[h % pool.count]
    }
}

extension AppModel {
    /// 진짜 나의 지금 종목 (국내 주식 합계는 날짜별 금액이라 경주에서 뺀다)
    var parallelBase: [(id: String, usd: Bool, qty: Double, last: Double)] {
        rows.filter { $0.id != KoreaEstimate.sumId }.map { (id: $0.id, usd: $0.sym.currency == .usd, qty: $0.h.qty, last: $0.sym.last) }
    }

    /// 예선 수량: 연 날의 수량 기록 (없으면 지금 종목). 연 뒤에 수량을 고쳐도 예선은 그대로
    func parallelBaseAt(_ opened: String) -> [(id: String, usd: Bool, qty: Double, last: Double)] {
        guard let h = Lots.on(lots, opened) else { return parallelBase }
        var out: [(id: String, usd: Bool, qty: Double, last: Double)] = []
        for (t, x) in h.sorted(by: { $0.key < $1.key }) {
            guard x > 0, let id = Sync.appId(t), id != KoreaEstimate.sumId, let s = Sample.symbol(id) else { continue }
            out.append((id: id, usd: s.currency == .usd, qty: x, last: s.last))
        }
        return out.isEmpty ? parallelBase : out
    }

    /// 처음 열 때 정하는 값: 출발일(회복 = 바닥일, 목표 = 시작일), 견본 바구니(고른 계획·구성), 인물(사다리 다음 칸)
    func newParallel(today: String = Day.today) -> PWorld? {
        let base = parallelBase
        guard !base.isEmpty else { return nil }
        let s0 = isGoal ? Day.str(goalFromDate) : (Parallel.bottomDay(base) ?? today)
        let tid = isGoal ? (gMix == "mine" ? "index" : gMix) : (planKey == "keep" ? "balance" : planKey)
        let tpl = Parallel.templates.first { $0.id == tid } ?? Parallel.templates[1]
        let w = Parallel.resolve(tpl.w)
        return PWorld(opened: today, start: Parallel.clampStart(s0), segs: [PBasketSeg(from: today, w: w)],
                      who: Parallel.match(type: calm.type, aggressive: Parallel.aggressive(w)))
    }
    /// 화면에 쓰는 세계: 저장된 것, 샘플·시안 실행이면 그때그때 만든 것 (남기지 않는다)
    var pworldShown: PWorld? { pworld ?? (isSample || !persists ? newParallel() : nil) }
    func openParallelIfNeeded() {
        guard pworld == nil, !isSample, persists, let w = newParallel() else { return }
        pworld = w
    }

    var parallelRace: PRace? {
        guard let w = pworldShown else { return nil }
        let base = parallelBaseAt(w.opened)
        let lotsIn: [(d: String, q: [String: Double])] = lots.filter { $0.d >= w.opened }.map { l -> (d: String, q: [String: Double]) in
            var q: [String: Double] = [:]
            for (t, x) in l.h { if let id = Sync.appId(t), id != KoreaEstimate.sumId { q[id] = x } }
            return (d: l.d, q: q)
        }
        let monthly = gM * 1e4
        let pk = Market.shared.prices.map { "\($0.got)" } ?? "-"
        let segKey = w.segs.map(Parallel.segText).joined(separator: ";")
        let key = [w.start, w.opened, w.who, segKey, base.map { "\($0.id):\($0.qty)" }.joined(separator: ","),
                   "\(lotsIn.count):\(lotsIn.last?.d ?? "")", pk, "\(monthly)", Day.today].joined(separator: "|")
        if let c = raceCache, c.key == key { return c.race }
        let race = Parallel.run(.init(start: w.start, opened: w.opened, base: base, lots: lotsIn, segs: w.segs, who: w.who, monthly: monthly))
        raceCache = (key, race)
        return race
    }

    /// 본선 기록: 연 날 뒤 마감된 거래일(마지막 칸 앞)을 하루 한 줄씩 남긴다. 남긴 줄은 다시 고치지 않는다
    func parallelTick() {
        guard var w = pworld, !isSample, persists, let race = parallelRace, race.open < race.last else { return }
        let have = Set(w.days.map(\.d))
        var add: [PDay] = []
        for t in race.open..<race.last where !have.contains(race.dates[t]) {
            add.append(PDay(d: race.dates[t], r: (0..<3).map { (race.r[$0][t] * 1e5).rounded() / 1e5 }, who: w.who))
        }
        guard !add.isEmpty else { return }
        w.days = (w.days + add).sorted { $0.d < $1.d }
        if w.days.count > 1200 { w.days.removeFirst(w.days.count - 1200) }
        pworld = w
    }
    /// 그래프·순위에 쓰는 수익률: 본선에 남긴 날은 남긴 값 (같은 인물일 때), 나머지는 계산값
    func parallelSeries(_ race: PRace) -> [[Double]] {
        guard let w = pworldShown, !w.days.isEmpty else { return race.r }
        var byDay: [String: PDay] = [:]
        for x in w.days { byDay[x.d] = x }
        var r = race.r
        for t in race.dates.indices {
            guard let x = byDay[race.dates[t]], x.r.count == 3 else { continue }
            r[0][t] = x.r[0]; r[1][t] = x.r[1]
            if x.who == w.who { r[2][t] = x.r[2] }
        }
        return r
    }

    /// 바구니 고치기: 오늘부터 새 칸. 오늘 이미 고친 칸(또는 오늘 연 첫 칸)이면 그 칸을 바꾼다
    func saveBasket(_ w: [String: Double]) {
        guard var p = pworld else { return }
        let tot = w.values.reduce(0, +)
        guard tot > 0 else { return }
        let norm = w.filter { $0.value > 0 }.mapValues { $0 / tot }
        let today = Day.today
        if let l = p.segs.last, l.from >= today || (p.segs.count == 1 && p.opened >= today) { p.segs[p.segs.count - 1].w = norm }
        else { p.segs.append(PBasketSeg(from: today, w: norm)) }
        pworld = p
    }
    /// 인물 바꾸기: 횟수 제한 없이 (비교해 보는 재미, 사용자 결정 2026-10-12). 바꾸면 인물 세계는 출발일부터 새 규칙으로 다시 계산
    var canPickWho: Bool { pworld != nil }
    func pickWho(_ id: String) {
        guard var p = pworld, canPickWho, id != p.who else { return }
        p.who = id; p.whoPick = ISO8601DateFormatter().string(from: Date())
        pworld = p
    }
    /// 홈 한 줄: 오늘 앞선 나
    var parallelLeader: String? {
        guard pworld != nil, let race = parallelRace else { return nil }
        let r = parallelSeries(race)
        let k = [0, 1, 2].max { r[$0][race.last] < r[$1][race.last] } ?? 0
        return k == 2 ? "\(friendName(pworldShown?.who ?? "seri")) 세계의 나" : Parallel.names[k]
    }
}

extension Market {
    /// 바구니 고치기 화면: 담을 수 있는 종목의 지난 종가를 한 번 받아 둔다 (받은 지 30분 안이면 그대로)
    func fetchParallelUniverse() async {
        var keys = Set(prices?.asked ?? [])
        for u in Parallel.universe { keys.insert(u.id) }
        keys.insert("KRW=X")
        if let p = await SitePrices.fetch(keys: keys, old: prices, minAge: 1800) { prices = p }
    }
}
