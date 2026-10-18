import Foundation

// 평행 세계의 나 (기획서 notes/평행세계-앱-기획서-2026-10-12.md, v1).
// 세 명의 나가 같은 날 같은 금액에서 출발해 사이트 지난 종가로 달린다: ① 진짜 나 ② 담아 본 나(모의 바구니) ③ 인물 세계의 나.
// 화면에는 순위·수익률·평가액과 비율 요약만 낸다. 인물 세계·모의 바구니의 매매 내역(종목+수량+시점)은 만들지도 보여 주지도 않는다.

/// 모의 바구니 한 칸: 그날부터 이 비중 (티커 → 비중, 합 1). 고치면 그날부터 새 칸이 붙고 지난 칸은 그대로 남는다
struct PBasketSeg: Codable, Equatable { var from: String; var w: [String: Double] }
/// 본선 기록 한 줄: 그날 종가 기준 세 명의 수익률 (진짜·담아 본·인물). who = 그날 인물 세계의 인물, v = 그날 인물 규칙 판 (없으면 1)
struct PDay: Codable, Equatable { var d: String; var r: [Double]; var who: String; var v: Int? = nil }

struct PWorld: Codable, Equatable {
    var opened: String              // 평행 세계를 연 날 (본선 시작)
    var start: String               // 예선 출발일 (연 날 정해 두고 바꾸지 않는다)
    var segs: [PBasketSeg]
    var who: String                 // 인물 세계의 인물
    var whoPick: String? = nil      // 사용자가 마지막으로 직접 바꾼 때 (ISO 시각, 동기화에서 나중 것을 쓴다). 횟수 제한 없음
    var days: [PDay] = []
    var ver: Int? = nil             // 기록 형식 판. 2 = 인물 세부 규칙 (2026-10-12). 저장 키는 그대로 두고 지난 기록을 잇는다

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

/// 규칙 일지 한 줄: 그날(화면에는 주 단위로 뭉갬) 어떤 세부 규칙이 움직였나(또는 움직이지 않기로 했나)와 비율 변화.
/// 종목 이름·수량은 담지 않는다. rule = Invest.habits 의 번호
struct PLog: Equatable {
    let t: Int
    let rule: Int
    let text: String
    var metric: String? = nil      // "현금 비중" · "가장 큰 종목 비중"
    var a: Double = 0
    var b: Double = 0
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
    let log: [PLog]            // 인물 세계 규칙 일지 (오래된 것부터)
    var last: Int { dates.count - 1 }
    func rank(at t: Int) -> [Int] { [0, 1, 2].sorted { r[$0][t] > r[$1][t] } }
    func place(_ k: Int, at t: Int) -> Int { (rank(at: t).firstIndex(of: k) ?? 0) + 1 }
}

enum Parallel {
    static let names = ["내 실제 투자", "모의 바구니", "인물 규칙대로"]

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

    // MARK: 인물 규칙 (하루 한 번 종가 기준). 세부 규칙은 Invest.habits, 엔진은 run 의 ③
    /// 인물 규칙 판: 1 = 대표 규칙만, 2 = 세부 규칙까지 (인물 자세히 보기와 같은 규칙). 바뀐 날부터 새 규칙으로 이어 간다
    static let rulesVersion = 2
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

        // ③ 인물 세계의 나: 진짜 나의 출발 비중에서 시작해 인물 규칙(대표 + 세부, Invest.habits)대로 스스로 움직인다
        var w0: [String: Double] = [:]
        for (k, x) in baseQ where p(k, 0) > 0 { w0[k] = x * p(k, 0) / V0 }
        var u = alloc(w0, inp.who == "sio" ? V0 * 0.8 : V0, 0)
        var cashU = inp.who == "sio" ? V0 * 0.2 / max(1e-9, p(cashId, 0)) : 0
        var parked: [String: Double] = [:]          // 이르: 평균선 아래로 내려가 현금으로 옮긴 몫 (현금 단위)
        var below: [String: Int] = [:]              // 이르: 평균선 아래로 이어진 거래일 수
        var invested = V0, contrib = 0.0
        var sIdx = 1.0, sPeak = 1.0, stage = 0, refill = false   // 시오: 주식 쪽 지수·고점·넣은 단계
        var nextYear = 1, monthStartV = V0
        var lastQ = Self.quarter(dates[0])
        var trades: [Substring: Int] = [:]          // 달마다 매매 횟수 (시오 2번·이르 4번까지)
        var r2 = [Double](repeating: 0, count: n)
        var cashW: [Int: Double] = [:], topW: [Int: Double] = [:]
        let sma = SMA(assets: assets)
        let spy = hist("SPY")
        let before = max(0, n - 22)
        func cashV(_ t: Int) -> Double { (cashU + parked.values.reduce(0, +)) * p(cashId, t) }
        func charV(_ t: Int) -> Double { value(u, t) + cashV(t) }
        var log: [PLog] = []
        /// (현금 비중, 가장 큰 한 종목 비중)
        func ratios(_ t: Int) -> (cash: Double, top: Double) {
            let V = charV(t)
            guard V > 0 else { return (0, 0) }
            let c = cashV(t) + (u[cashId] ?? 0) * p(cashId, t)
            return (c / V, (u.filter { $0.key != cashId }.map { $0.value * p($0.key, t) }.max() ?? 0) / V)
        }
        func note(_ t: Int, _ rule: Int, _ text: String, cash: (Double, Double)? = nil, top: (Double, Double)? = nil) {
            if let c = cash { log.append(PLog(t: t, rule: rule, text: text, metric: "현금 비중", a: c.0, b: c.1)) }
            else if let x = top { log.append(PLog(t: t, rule: rule, text: text, metric: "가장 큰 종목 비중", a: x.0, b: x.1)) }
            else { log.append(PLog(t: t, rule: rule, text: text)) }
        }
        var capNoted: Substring = ""                // 같은 달 "한도 다 씀"은 한 번만 적는다
        /// 지금 들고 있는 비중 (현금 칸 빼고)
        func weights(_ t: Int) -> [String: Double] {
            let V = value(u, t)
            guard V > 0 else { return w0 }
            var w: [String: Double] = [:]
            for (k, x) in u { w[k] = x * p(k, t) / V }
            return w
        }
        /// 그 종목을 돈만큼 사고팔기 (+ 사기, − 팔기)
        func trade(_ k: String, _ won: Double, _ t: Int) {
            guard p(k, t) > 0 else { return }
            u[k, default: 0] += won / p(k, t)
            if (u[k] ?? 0) <= 1e-12 { u[k] = nil }
        }
        func toCash(_ won: Double, _ t: Int) { cashU += won / max(1e-9, p(cashId, t)) }
        /// 시오: 지수가 하루 ±3% 넘게 움직인 날 (S&P500 ETF 기준)
        func bigDay(_ t: Int) -> Bool {
            guard t > 0, let a = spy?.on(dates[t]), let b = spy?.on(dates[t - 1]), b > 0 else { return false }
            return abs(a / b - 1) > 0.03
        }
        for t in 0..<n {
            let d = dates[t], mk = d.prefix(7)
            if t > 0 { let a = value(u, t), b = value(u, t - 1); if b > 0 && a > 0 { sIdx *= a / b } }
            switch inp.who {
            case "seri":
                // 기념일에만. 처음 비율(한 종목 40% 상한)에서 ±5%p 넘게 벗어난 종목이 있을 때만 되돌린다
                if t > 0, let anniv = addYears(dates[0], nextYear), d >= anniv {
                    nextYear += 1
                    let (target, cashT) = Self.capped(w0, 0.40)
                    let V = charV(t)
                    var off = abs(cashV(t) / V - cashT)
                    for k in Set(target.keys).union(u.keys) { off = max(off, abs((u[k] ?? 0) * p(k, t) / V - (target[k] ?? 0))) }
                    if V > 0 && off > 0.05 {
                        let q0 = ratios(t)
                        u = alloc(target, V * (1 - cashT), t)
                        cashU = V * cashT / max(1e-9, p(cashId, t))
                        let q1 = ratios(t)
                        let capped = w0.values.contains { $0 > 0.40 + 1e-9 }
                        note(t, capped ? 2 : 0, capped ? "기념일, 한 종목 40% 상한에 맞춰 되돌리기" : "기념일, 처음 비율로 되돌리기", top: (q0.top, q1.top))
                    } else if V > 0 {
                        note(t, 1, "기념일 점검, ±5%p 안이라 그해는 그대로")
                    }
                }
            case "seonbae":
                // 분기에 한 번 되묻는다: 처음보다 10%p 넘게 커진 종목은 덜고, 작아진 종목은 다른 데서 덜어 채운다
                let q = Self.quarter(d)
                guard t > 0, q != lastQ else { break }
                lastQ = q
                let V = charV(t)
                guard V > 0 else { break }
                func wt(_ k: String) -> Double { (u[k] ?? 0) * p(k, t) / V }
                // 수익 난 직후(최근 20거래일 오른 종목)에는 더 사지 않는다
                func gained(_ k: String) -> Bool { p(k, t) > p(k, max(0, t - 20)) }
                let q0 = ratios(t)
                var pool = 0.0, filled = false, held = false
                for k in w0.keys where wt(k) - (w0[k] ?? 0) > 0.10 {
                    let cut = (wt(k) - (w0[k] ?? 0)) * V
                    trade(k, -cut, t); pool += cut
                }
                if pool > 0 {
                    // 덜어낸 돈: 절반은 가장 작아진 종목에, 절반은 현금에
                    let small = w0.keys.filter { !gained($0) && wt($0) < (w0[$0] ?? 0) }.min { wt($0) - (w0[$0] ?? 0) < wt($1) - (w0[$1] ?? 0) }
                    if let s = small { trade(s, pool / 2, t); toCash(pool / 2, t) } else { toCash(pool, t) }
                }
                for k in w0.keys.sorted() where (w0[k] ?? 0) - wt(k) > 0.10 {
                    if gained(k) { held = true; continue }
                    filled = true
                    // "정말 버릴 거야?": 현금에서 먼저, 모자라면 다른 종목에서 고르게 덜어 채운다
                    var need = ((w0[k] ?? 0) - wt(k)) * V
                    let fromCash = min(need, cashU * p(cashId, t))
                    cashU -= fromCash / max(1e-9, p(cashId, t)); need -= fromCash
                    var got = fromCash
                    if need > 0 {
                        let others = u.filter { $0.key != k }
                        let ov = others.reduce(0.0) { $0 + $1.value * p($1.key, t) }
                        if ov > 0 { let f = min(1, need / ov); for (j, x) in others { trade(j, -x * p(j, t) * f, t) }; got += min(need, ov) }
                    }
                    trade(k, got, t)
                }
                let q1 = ratios(t)
                if pool > 0 { note(t, 1, "분기 점검, 10%p 넘게 커진 몫을 덜어 절반은 현금으로", top: (q0.top, q1.top)) }
                if filled { note(t, 2, "분기 점검, 10%p 넘게 작아진 몫을 다시 채우기", cash: (q0.cash, q1.cash)) }
                if held { note(t, 4, "작아졌지만 막 오른 참이라 이번엔 채우지 않음") }
                if pool == 0 && !filled && !held { note(t, 0, "분기 점검, 모두 ±10%p 안이라 그대로") }
            case "sio":
                // 주식 쪽 고점 대비 −20%에 현금 절반, −30%에 나머지. 넣은 뒤 고점을 다시 넘으면 현금 20%로 다시 채운다.
                // 지수가 하루 ±3% 넘게 움직인 날에는 매매하지 않고 다음 날 판단, 한 달에 2번까지
                if sIdx > sPeak { sPeak = sIdx; if stage > 0 { refill = true }; stage = 0 }
                let dd = sIdx / sPeak - 1
                let due = (stage == 0 && dd <= -0.20) || (stage == 1 && dd <= -0.30)
                if refill || (due && cashU > 0) {
                    if bigDay(t) { note(t, 3, "하루에 3% 넘게 움직인 날이라 하루 기다리기"); break }
                    if trades[mk, default: 0] >= 2 {
                        if capNoted != mk { capNoted = mk; note(t, 2, "이번 달 매매 두 번을 다 써서 다음 달로") }
                        break
                    }
                }
                let q0 = ratios(t)
                if refill {
                    refill = false
                    let V = charV(t), c = cashV(t)
                    if V > 0 && c < V * 0.2 {
                        let need = V * 0.2 - c, sv = value(u, t)
                        if sv > 0 { for (k, x) in u { trade(k, -need * x * p(k, t) / sv, t) }; toCash(need, t) }
                        trades[mk, default: 0] += 1
                        note(t, 1, "고점을 다시 넘어 현금을 20%까지 다시 채우기", cash: (q0.cash, ratios(t).cash))
                    }
                } else if due, cashU > 0 {
                    let first = stage == 0
                    let put = (first ? cashU / 2 : cashU) * p(cashId, t)
                    cashU -= put / max(1e-9, p(cashId, t))
                    for (k, x) in alloc(w0, put, t) { u[k, default: 0] += x }
                    stage += 1; trades[mk, default: 0] += 1
                    note(t, 0, first ? "고점 −20%, 현금 절반 넣기" : "고점 −30%, 남은 현금 넣기", cash: (q0.cash, ratios(t).cash))
                }
            case "ir":
                // 200일선보다 3% 넘게 내려가거나 5거래일 연속 아래면 판다. 3% 넘게 올라오면 다시 산다. 오르는 종목은 덜지 않는다. 한 달 4번까지
                let q0 = ratios(t)
                var outBy3 = false, outBy5 = false, back = false, capped = false
                for k in Set(u.keys).union(parked.keys).sorted() where k != cashId && k != "SGOV" {
                    guard let m = sma.at(k, d) else { continue }
                    let px = p(k, t)
                    below[k] = px < m ? (below[k] ?? 0) + 1 : 0
                    let wantIn = parked[k] != nil && px > m * 1.03
                    let wantOut = parked[k] == nil && (u[k] ?? 0) > 0 && (px < m * 0.97 || (below[k] ?? 0) >= 5)
                    guard wantIn || wantOut else { continue }
                    guard trades[mk, default: 0] < 4 else { capped = true; continue }
                    if wantIn, let c = parked[k] {
                        u[k, default: 0] += c * p(cashId, t) / max(1e-9, px); parked[k] = nil; trades[mk, default: 0] += 1; back = true
                    } else if wantOut, let x = u[k] {
                        if px < m * 0.97 { outBy3 = true } else { outBy5 = true }
                        parked[k] = x * px / max(1e-9, p(cashId, t)); u[k] = nil; trades[mk, default: 0] += 1
                    }
                }
                if outBy3 || outBy5 || back {
                    let q1 = ratios(t)
                    if outBy3 || outBy5 {
                        let all = q1.cash > 0.995
                        note(t, all ? 4 : 0, all ? "모두 200일선 아래, 전부 현금으로" : outBy3 ? "200일선보다 3% 넘게 내려가 현금으로 옮기기" : "5거래일 내내 200일선 아래라 현금으로 옮기기", cash: (q0.cash, q1.cash))
                    }
                    if back { note(t, 1, "200일선보다 3% 넘게 올라와 다시 들어가기", cash: (q0.cash, q1.cash)) }
                }
                if capped && capNoted != mk { capNoted = mk; note(t, 3, "이번 달 매매 네 번을 다 써서 다음 달로") }
            case "sua":
                // 매달 같은 금액. 지난달 바구니가 −5% 넘게 빠졌으면 1.5배, −15% 넘게 2배, +10% 넘게 올랐으면 0.7배.
                // 지금 바구니 비율대로 나눠 넣고(한 종목에 몰지 않음), 팔지 않고, 못 넣은 달을 몰아 넣지 않는다
                if t > 0 && inp.monthly > 0 && mk != dates[t - 1].prefix(7) {
                    let m = monthStartV > 0 ? charV(t - 1) / monthStartV - 1 : 0
                    let times = Self.suaTimes(m), c = inp.monthly * times
                    for (k, x) in alloc(weights(t), c, t) { u[k, default: 0] += x }
                    let pct = "\(Int((m * 100).rounded()))%"
                    switch times {
                    case 2: note(t, 1, "지난달 바구니 \(pct), 2배 넣기")
                    case 1.5: note(t, 0, "지난달 바구니 \(pct), 1.5배 넣기")
                    case 0.7: note(t, 2, "지난달 바구니 +\(pct), 0.7배만 넣기")
                    default: note(t, 3, "보통 달, 같은 금액을 지금 비율대로 넣기")
                    }
                    invested += c; contrib += c
                    monthStartV = charV(t)
                }
            default: break
            }
            let V = charV(t)
            r2[t] = V / invested - 1
            if t == n - 1 || t == before {
                let cv = cashV(t) + (u[cashId] ?? 0) * p(cashId, t)
                cashW[t] = V > 0 ? cv / V : 0
                topW[t] = V > 0 ? (u.filter { $0.key != cashId }.map { $0.value * p($0.key, t) }.max() ?? 0) / V : 0
            }
        }
        let open = dates.firstIndex(where: { $0 >= inp.opened }) ?? n
        return PRace(dates: dates, r: [r0, r1, r2], open: open, V0: V0,
                     cash: (cashW[n - 1] ?? 0, cashW[before] ?? 0), top: (topW[n - 1] ?? 0, topW[before] ?? 0),
                     segRet: segRet, contrib: contrib, log: log)
    }

    static func addYears(_ d: String, _ y: Int) -> String? {
        guard let x = Day.date(d), let z = Calendar(identifier: .gregorian).date(byAdding: .year, value: y, to: x) else { return nil }
        return Day.str(z)
    }

    /// 분기 (yyyy-Qn)
    static func quarter(_ d: String) -> String {
        let m = Int(d.dropFirst(5).prefix(2)) ?? 1
        return String(d.prefix(4)) + "-Q\((m - 1) / 3 + 1)"
    }
    /// 수아: 지난달 바구니 변화에 따른 적립 배수
    static func suaTimes(_ m: Double) -> Double { m <= -0.15 ? 2 : m <= -0.05 ? 1.5 : m >= 0.10 ? 0.7 : 1 }
    /// 세리: 한 종목 상한. 넘친 몫은 다른 종목에 비율대로, 모두 상한이면 현금으로 (현금 비중을 같이 돌려준다)
    static func capped(_ w: [String: Double], _ cap: Double) -> (w: [String: Double], cash: Double) {
        var w = w, cash = 0.0
        for _ in 0..<10 {
            let over = w.filter { $0.value > cap + 1e-9 }
            if over.isEmpty { break }
            var ex = 0.0
            for (k, x) in over { ex += x - cap; w[k] = cap }
            let room = w.filter { $0.value < cap - 1e-9 }
            let tot = room.values.reduce(0, +)
            if tot <= 0 { cash += ex; break }
            for (k, x) in room { w[k] = x + ex * x / tot }
        }
        return (w, cash)
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
        let tid = gMix == "mine" ? "index" : gMix
        let tpl = Parallel.templates.first { $0.id == tid } ?? Parallel.templates[1]
        let w = isGoal ? Parallel.resolve(tpl.w) : planBasket(selectedPlan)
        return PWorld(opened: today, start: Parallel.clampStart(s0), segs: [PBasketSeg(from: today, w: w)],
                      who: Parallel.match(type: calm.type, aggressive: Parallel.aggressive(w)), ver: Parallel.rulesVersion)
    }
    /// 회복 루트: 미션 3에서 고른 계획대로 바꾼 내 종목 비중. 몰린 종목은 계획 비중까지, 줄인 몫은 묶음 대표 ETF로. 유지 = 지금 비중
    func planBasket(_ p: Plan) -> [String: Double] {
        let rs = rows.filter { $0.id != KoreaEstimate.sumId }
        let t = rs.reduce(0) { $0 + $1.value }
        guard t > 0 else { return Parallel.resolve(Parallel.templates[1].w) }
        var w: [String: Double] = [:]
        for r in rs { w[r.id, default: 0] += r.value / t }
        guard p.id != "keep", let f = focusRow, let fw = w[f.id] else { return w }
        let freed = max(0, fw - p.wt * max(1, total) / t), tot = p.mix.values.reduce(0, +)
        guard freed > 0, tot > 0 else { return w }
        w[f.id] = fw - freed
        let etf: [Basket: String] = [.G: "QQQ", .V: "VTV", .B: "USMV", .C: "SGOV"]
        var add: [String: Double] = [:]
        for (b, x) in p.mix where x > 0 { add[etf[b] ?? "SPY", default: 0] += freed * x / tot }
        for (k, v) in Parallel.resolve(add) { w[k, default: 0] += v }
        return w
    }
    /// 화면에 쓰는 세계: 저장된 것, 샘플·시안 실행이면 그때그때 만든 것 (남기지 않는다)
    var pworldShown: PWorld? { pworld ?? (isSample || !persists ? newParallel() : nil) }
    func openParallelIfNeeded() {
        guard pworld == nil, !isSample, persists, let w = newParallel() else { return }
        pworld = w
    }

    var parallelRace: PRace? {
        guard let w = pworldShown else { return nil }
        return parallelRun(w, who: w.who)
    }
    /// 인물 세계를 그 인물 규칙으로 굴린 경주 (평행 세계 화면은 지금 인물, 인물 자세히 보기는 그 인물)
    func parallelRun(_ w: PWorld, who: String) -> PRace? {
        let base = parallelBaseAt(w.opened)
        let lotsIn: [(d: String, q: [String: Double])] = lots.filter { $0.d >= w.opened }.map { l -> (d: String, q: [String: Double]) in
            var q: [String: Double] = [:]
            for (t, x) in l.h { if let id = Sync.appId(t), id != KoreaEstimate.sumId { q[id] = x } }
            return (d: l.d, q: q)
        }
        let monthly = gM * 1e4
        let pk = Market.shared.prices.map { "\($0.got)" } ?? "-"
        let segKey = w.segs.map(Parallel.segText).joined(separator: ";")
        let key = [w.start, w.opened, who, segKey, base.map { "\($0.id):\($0.qty)" }.joined(separator: ","),
                   "\(lotsIn.count):\(lotsIn.last?.d ?? "")", pk, "\(monthly)", Day.today].joined(separator: "|")
        let mine = who == w.who
        if let c = mine ? raceCache : raceCacheOther, c.key == key { return c.race }
        let race = Parallel.run(.init(start: w.start, opened: w.opened, base: base, lots: lotsIn, segs: w.segs, who: who, monthly: monthly))
        if mine { raceCache = (key, race) } else { raceCacheOther = (key, race) }
        return race
    }

    /// 본선 기록: 연 날 뒤 마감된 거래일(마지막 칸 앞)을 하루 한 줄씩 남긴다. 남긴 줄은 다시 고치지 않는다
    func parallelTick() {
        guard var w = pworld, !isSample, persists, let race = parallelRace, race.open < race.last else { return }
        let have = Set(w.days.map(\.d))
        var add: [PDay] = []
        for t in race.open..<race.last where !have.contains(race.dates[t]) {
            add.append(PDay(d: race.dates[t], r: (0..<3).map { (race.r[$0][t] * 1e5).rounded() / 1e5 }, who: w.who, v: Parallel.rulesVersion))
        }
        if w.ver != Parallel.rulesVersion { w.ver = Parallel.rulesVersion; if add.isEmpty { pworld = w; return } }
        guard !add.isEmpty else { return }
        w.days = (w.days + add).sorted { $0.d < $1.d }
        if w.days.count > 1200 { w.days.removeFirst(w.days.count - 1200) }
        pworld = w
    }
    /// 그래프·순위에 쓰는 수익률: 본선에 남긴 날은 남긴 값 (같은 인물일 때), 나머지는 계산값.
    /// 인물 세계는 마지막으로 남긴 날 값에서 계산값의 하루하루 변화를 이어 붙인다 (규칙 판이 바뀌어도 지난 기록을 덮지 않고 그날부터 새 규칙으로)
    func parallelSeries(_ race: PRace) -> [[Double]] {
        guard let w = pworldShown, !w.days.isEmpty else { return race.r }
        var byDay: [String: PDay] = [:]
        for x in w.days { byDay[x.d] = x }
        var r = race.r
        var lastChar = -1
        for t in race.dates.indices {
            guard let x = byDay[race.dates[t]], x.r.count == 3 else { continue }
            r[0][t] = x.r[0]; r[1][t] = x.r[1]
            if x.who == w.who { r[2][t] = x.r[2]; lastChar = t }
        }
        if lastChar >= 0 && lastChar < race.last {
            let base = 1 + r[2][lastChar], from = 1 + race.r[2][lastChar]
            if from > 0 { for t in (lastChar + 1)...race.last { r[2][t] = base * (1 + race.r[2][t]) / from - 1 } }
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
    /// 마이너스족은 본전까지 남은 거리, 장투족은 목표까지 온 거리 (평행 세계 카드·인물 자세히 보기)
    func parallelDistance(_ amount: Double) -> String {
        if isGoal {
            let k = gK * 1e4
            return "목표 \(AppModel.wonK(gK))까지 \(AppModel.pct(min(1, amount / max(1, k)))) 왔어요"
        }
        let need = cost / max(1, amount) - 1
        return need > 0 ? "본전까지 +\(String(format: "%.1f", need * 100))% 남음" : "본전을 넘었어요"
    }
    /// 세 방식 이름 (인물 규칙은 인물 이름이 붙는다)
    func parallelTitle(_ k: Int, who: String) -> String { k == 2 ? "\(friendName(who)) 규칙대로" : Parallel.names[k] }
    /// 홈 비교 카드: 지금 결론 한 줄. 아직 열지 않았으면 처음 열 때 정할 세계로 미리 계산
    var parallelVerdictLine: String? {
        guard let w = pworldShown ?? newParallel(), let race = parallelRun(w, who: w.who) else { return nil }
        let v = Parallel.verdict(pworldShown == nil ? race.r : parallelSeries(race), at: race.last)
        let gap = String(format: "%.1f%%p", v.gap * 100)
        return v.similar ? "지금은 셋 다 비슷해요 (차이 \(gap))" : "지금은 \(parallelTitle(v.order[0], who: w.who)) 쪽이 앞서요 (차이 \(gap))"
    }
}

/// 결론 규칙: 가장 앞선 쪽과 뒤진 쪽 차이가 1.0%p 미만이면 "비슷해요"
struct PVerdict { let order: [Int]; let gap: Double; var similar: Bool { gap < Parallel.similarGap } }

extension Parallel {
    static let similarGap = 0.01
    static func verdict(_ r: [[Double]], at t: Int) -> PVerdict {
        let order = [0, 1, 2].sorted { r[$0][t] > r[$1][t] }
        return PVerdict(order: order, gap: r[order[0]][t] - r[order[2]][t])
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
