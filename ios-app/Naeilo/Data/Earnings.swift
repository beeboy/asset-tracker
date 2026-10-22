import Foundation

// 내 몫 실적 계산. 기준은 tools/earn_calc.mjs (웹 입구 해 보기·안드로이드도 같은 답). 검사: ios-app/tools/earn_parity (tools/earn_fixtures.json)
// 입력: data/earnings/{종목}.json (tools/earnings.py), 지금 수량, 오늘 환율(원/달러). 자료가 없으면(ETF) nil → 화면은 줄표.
// 규칙 (docs/decisions.md 2026-10-22):
// - 주당 값 × 지금 수량. 모든 분기 같은 수량. 주당 = 그 분기 값 ÷ 그 분기 신고서 첫 장 주식 수
// - 통화는 회사 신고 통화. 원화는 내 몫 순이익 옆 한 곳만
// - 갈래: 원가·연구비·판관비가 다 있으면 3갈래, 남는 돈(이자·세금·기타) = 매출 − 셋 − 순이익 은 물안개. 하나라도 없으면 '쓴 돈' 1갈래(물안개 없음)
// - 적자: 순이익 < 0. 모자란 만큼 = −순이익
// - 작년 같은 분기 = 끝 날짜가 365±25일 앞인 분기
enum Earnings {
    struct Quarter: Codable, Equatable {
        var start: String, end: String, months: String, label: String, filed: String, announced: String
        var shares: Double?
        var revenue: Double?, cogs: Double?, rnd: Double?, sga: Double?, net: Double?
    }
    struct Pending: Codable, Equatable { var announced: String }
    struct File: Codable, Equatable {
        var symbol: String
        var name: String?
        var currency: String
        var pending: Pending?
        var quarters: [Quarter]
    }

    struct Mine { var revenue: Double?, cogs: Double?, rnd: Double?, sga: Double?, net: Double? }
    struct Branch: Equatable { var k: String; var v: Double }
    struct Gauge: Equatable { var label: String; var value: Double?; var lastYear: Bool; var co: Double? = nil }   // co = 회사 전체 매출 (강 수위표)
    /// 강 팝업·목록 막대·홈 카드가 쓰는 값 한 벌 (earn_calc.mjs view)
    struct Summary {
        var symbol: String, currency: String, months: String, label: String
        var lastYearMonths: String?
        var pending: String?
        var announced: String               // 마지막 분기 발표일 (목록 금색 점: 오늘이면)
        var revenue: Double?, net: Double?   // 자료에 없으면 nil (계산에서는 JS 처럼 0)
        var branches: [Branch]
        var mist: Double
        var loss: Bool
        var short: Double
        var gauge: [Gauge]
        var revYoY: String?
        var netYoY: String?
        var lastYearNet: Double?
        var netText: String
        var netKrw: String?
        var coRevenue: Double? = nil   // 회사 전체 매출 (강 물높이·글)
    }

    static func mine(_ q: Quarter, _ qty: Double) -> Mine {
        let k = qty / (q.shares ?? 0)
        func m(_ v: Double?) -> Double? { v.map { $0 * k } }
        return Mine(revenue: m(q.revenue), cogs: m(q.cogs), rnd: m(q.rnd), sga: m(q.sga), net: m(q.net))
    }

    /// "2025-09-30" → 1970-01-01 부터 날 수 (UTC 자정, JS Date.parse 와 같은 기준)
    static func dayNum(_ s: String) -> Int {
        let p = s.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return 0 }
        // 그레고리력 날 수 (Howard Hinnant days_from_civil)
        let y = p[1] <= 2 ? p[0] - 1 : p[0], m = p[1], d = p[2]
        let era = (y >= 0 ? y : y - 399) / 400, yoe = y - era * 400
        let doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    static func sameLastYear(_ qs: [Quarter], _ q: Quarter) -> Int? {
        let e = dayNum(q.end)
        return qs.firstIndex { abs(e - dayNum($0.end) - 365) <= 25 }
    }

    /// 작년 대비 비율 글: "+3%" / "−43%" / "작년과 같음". 작년이나 올해가 손실이면 nil (비율이 뜻이 없다)
    static func yoyText(_ now: Double?, _ before: Double?) -> String? {
        guard let now, let before, before > 0, now >= 0 else { return nil }
        let r = now / before - 1
        if abs(r) < 0.005 { return "작년과 같음" }
        return (r > 0 ? "+" : "−") + String(Int((abs(r) * 100).rounded())) + "%"
    }

    /// 돈 글: 10 이상 정수, 1 이상 소수 한 자리, 그 아래 두 자리. 음수는 "−"
    static func money(_ v: Double, _ currency: String) -> String {
        let a = abs(v)
        let n = a >= 10 ? String(format: "%.0f", a.rounded()) : a >= 1 ? String(format: "%.1f", a) : String(format: "%.2f", a)
        return (v < 0 ? "−" : "") + n + (currency == "USD" ? "달러" : " " + currency)
    }

    /// 원화 어림: "약 21만" / "약 8천" / "약 300원"
    static func krw(_ v: Double) -> String {
        let a = abs(v), s = v < 0 ? "−" : ""
        if a >= 10000 { return "약 " + s + grouped(Int((a / 10000).rounded())) + "만" }
        if a >= 1000 { return "약 " + s + String(Int((a / 1000).rounded())) + "천" }
        return "약 " + s + String(Int((a / 10).rounded()) * 10) + "원"
    }
    /// 1234567 → "1,234,567" (JS toLocaleString("en-US"))
    static func grouped(_ n: Int) -> String {
        var s = String(abs(n)), out = ""
        while s.count > 3 { out = "," + s.suffix(3) + out; s = String(s.dropLast(3)) }
        return (n < 0 ? "-" : "") + s + out
    }

    static func summary(_ j: File?, qty: Double, fx: Double?) -> Summary? {
        guard let j, let q = j.quarters.last else { return nil }
        let qs = j.quarters, ly = sameLastYear(qs, q).map { qs[$0] }
        let me = mine(q, qty), lm = ly.map { mine($0, qty) }
        let rev = me.revenue ?? 0, net = me.net ?? 0   // JS 에서 null 은 빼기·곱하기에서 0
        let three = q.cogs != nil && q.rnd != nil && q.sga != nil
        let branches = three
            ? [Branch(k: "cogs", v: me.cogs ?? 0), Branch(k: "sga", v: me.sga ?? 0), Branch(k: "rnd", v: me.rnd ?? 0)]
            : [Branch(k: "spent", v: rev - net)]
        let mist = three ? rev - (me.cogs ?? 0) - (me.rnd ?? 0) - (me.sga ?? 0) - net : 0
        let loss = net < 0
        let lyIndex = sameLastYear(qs, q)
        let gauge = qs.indices.dropLast().suffix(4).map { i in
            Gauge(label: qs[i].label, value: mine(qs[i], qty).revenue, lastYear: i == lyIndex, co: qs[i].revenue)
        }
        return Summary(
            symbol: j.symbol, currency: j.currency, months: q.months, label: q.label,
            lastYearMonths: ly.map { "작년 " + $0.months },
            pending: j.pending?.announced, announced: q.announced,
            revenue: me.revenue, net: me.net, branches: branches, mist: mist, loss: loss, short: loss ? -net : 0,
            gauge: gauge,
            revYoY: yoyText(me.revenue, lm?.revenue),
            netYoY: yoyText(me.net, lm?.net),
            lastYearNet: lm?.net,
            netText: money(net, j.currency),
            netKrw: !loss && j.currency == "USD" && (fx ?? 0) > 0 ? krw(net * fx!) : nil,
            coRevenue: q.revenue)
    }

    // MARK: 자료 받기·캐시

    static func cacheName(_ sym: String) -> String { "earn_\(sym).json" }
    static func decode(_ d: Data?) -> File? { d.flatMap { try? JSONDecoder().decode(File.self, from: $0) } }
    static func cached(_ sym: String) -> File? { sym == "DRNK" ? drnk : decode(Store.readData(cacheName(sym))) }

    /// 앱 안 견본 (Resources/earnings_seed.json, 맥에서 tools/earnings.py 를 돌린 것). 사이트 자료를 못 받았을 때만 쓴다.
    /// none = 회사 실적이 없는 종목(ETF·원자재 신탁)
    struct Seed: Decodable { var files: [String: File]; var none: [String] }
    static let seed: Seed? = Bundle.main.url(forResource: "earnings_seed", withExtension: "json")
        .flatMap { try? Data(contentsOf: $0) }.flatMap { try? JSONDecoder().decode(Seed.self, from: $0) }

    enum Got { case file(File), none, failed }
    /// naeilo.com/data/earnings/{종목}.json. 404 는 자료 없음(ETF·국내)
    static func fetch(_ sym: String) async -> Got {
        var c = URLComponents(url: Config.data.appendingPathComponent("earnings/\(sym).json"), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 3600)))]
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), let code = (resp as? HTTPURLResponse)?.statusCode else { return .failed }
        if code == 404 { return .none }
        guard code == 200, let f = decode(data) else { return .failed }
        Store.writeData(data, cacheName(sym))
        return .file(f)
    }

    /// DRNK 견본: data/earnings/DRNK.json 과 같은 내용 (가상 종목이라 받지 않는다. 같은지는 earn_parity 가 본다)
    static let drnk: File? = decode(Data(drnkJSON.utf8))
    static let drnkJSON = #"""
{"symbol":"DRNK","name":"드링커 (가상 종목)","currency":"USD","pending":null,"quarters":[
{"start":"2024-10-01","end":"2024-12-31","months":"10~12","label":"24.10~12","filed":"2025-01-23","announced":"2025-01-23","shares":505000000,"revenue":7000000000,"cogs":3150000000,"rnd":1820000000,"sga":700000000,"net":550000000},
{"start":"2025-01-01","end":"2025-03-31","months":"1~3","label":"25.1~3","filed":"2025-04-24","announced":"2025-04-24","shares":504000000,"revenue":7400000000,"cogs":3330000000,"rnd":1924000000,"sga":740000000,"net":600000000},
{"start":"2025-04-01","end":"2025-06-30","months":"4~6","label":"25.4~6","filed":"2025-07-24","announced":"2025-07-24","shares":503000000,"revenue":7900000000,"cogs":3555000000,"rnd":2054000000,"sga":790000000,"net":620000000},
{"start":"2025-07-01","end":"2025-09-30","months":"7~9","label":"25.7~9","filed":"2025-10-23","announced":"2025-10-23","shares":503000000,"revenue":7600000000,"cogs":4712000000,"rnd":1976000000,"sga":760000000,"net":-1200000000},
{"start":"2025-10-01","end":"2025-12-31","months":"10~12","label":"25.10~12","filed":"2026-01-22","announced":"2026-01-22","shares":502000000,"revenue":8300000000,"cogs":3735000000,"rnd":2158000000,"sga":830000000,"net":660000000},
{"start":"2026-01-01","end":"2026-03-31","months":"1~3","label":"26.1~3","filed":"2026-04-23","announced":"2026-04-23","shares":501000000,"revenue":8800000000,"cogs":3960000000,"rnd":2288000000,"sga":880000000,"net":700000000},
{"start":"2026-04-01","end":"2026-06-30","months":"4~6","label":"26.4~6","filed":"2026-07-23","announced":"2026-07-23","shares":500000000,"revenue":9200000000,"cogs":4140000000,"rnd":2392000000,"sga":920000000,"net":720000000},
{"start":"2026-07-01","end":"2026-09-30","months":"7~9","label":"26.7~9","filed":"2026-10-22","announced":"2026-10-22","shares":498000000,"revenue":9600000000,"cogs":4320000000,"rnd":2496000000,"sga":960000000,"net":750000000}
]}
"""#
}
