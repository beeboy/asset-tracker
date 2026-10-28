import Foundation
import Observation

// 배당·환율 계산 자료 (naeilo.com/data/calc.json). 사이트가 매일 웹과 같은 식으로 만든다 (tools/site_calc.mjs):
// 배당 = 수정종가/종가 비율이 바뀌는 날(배당락)과 금액, 환율 = web/model.js indicators(원/달러).
// 외부 요인 민감도는 stats.json(betas). 앱의 외부 요인, 배당·세금, 환율 영향 화면은 이 값에 웹 식(web/app.js renderCash · fxInfo)을 그대로 써서 웹과 같은 숫자를 낸다
@Observable
final class SiteCalc {
    static let shared = SiteCalc()

    struct Ev: Codable { var d: String; var a: Double }
    struct Div: Codable { var ccy: String; var price: Double?; var events: [Ev] }
    struct FX: Codable {
        var date: String; var close: Double; var kalman_level: Double
        var dev_from_kalman: Double?; var ret_1m: Double?; var ret_3m: Double?; var ret_1y: Double?
        var vol_ewma: Double?; var vol_hist: Double?; var slope_ann: Double?; var trend: String?; var hi1: Double?; var lo1: Double?
        var dates: [String]; var closes: [Double]; var level: [Double]
    }
    struct File: Codable { var date: String; var divs: [String: Div]; var fx: FX? }

    var file: File? = SiteCalc.load()

    static let name = "site_calc.json"
    private static func decode(_ d: Data?) -> File? { d.flatMap { try? JSONDecoder().decode(File.self, from: $0) } }
    /// 공개 베타·출시는 받아 둔 사이트 값을 읽지 않고 번들 값(가상 샘플)만
    static func load() -> File? {
        (Config.usesCollectedData ? decode(Store.readData(name)) : nil) ?? (Config.yahooOff ? nil : decode(Bundle.main.url(forResource: "calc", withExtension: "json").flatMap { try? Data(contentsOf: $0) }))   // 번들(내부 베타 Yahoo 자료)도 내 키가 있으면 안 쓴다
    }
    /// 사이트는 하루 한 번 만든다. 6시간 안에 받았으면 다시 받지 않는다
    func refresh() async {
        // 내 키가 있으면 사이트 대신 키로 받은 값으로 (원/달러는 주 값으로 채운 날짜별 값. 배당은 토스에 없다)
        if Config.yahooOff {
            let p = Market.shared.prices
            var f = Self.local(p)
            if AlpacaKeys.on {
                let us = (p?.h.keys).map { Array($0) }?.filter { !$0.contains(".K") && !$0.contains("=") && !$0.hasPrefix("^") && !$0.hasPrefix("KR~") } ?? []
                let d = await Self.alpacaDivs(us, prices: p)
                if !d.isEmpty { if f == nil { f = File(date: Day.today, divs: d, fx: nil) } else { f?.divs = d } }
            }
            file = f
            return
        }
        guard Config.usesCollectedData else { return }
        let key = "calcGot", now = Date().timeIntervalSince1970
        if now - Store.defaults.double(forKey: key) < 6 * 3600 { return }
        var c = URLComponents(url: Config.data.appendingPathComponent("calc.json"), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(now / 3600)))]
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200, let f = Self.decode(data) else { return }
        Store.writeData(data, Self.name)
        Store.defaults.set(now, forKey: key)
        file = f
    }

    /// 키로 받은 날짜별 원/달러(TossHistory.fxDaily)로 환율 칸을 만든다. 화면이 쓰는 칸(지난 값·1년 변화·1년 고저)만 채운다
    static func local(_ p: SitePrices.File?) -> File? {
        guard let o = p?.h["KRW=X"], let last = o.closes.last, o.closes.count > 2 else { return nil }
        let n = o.closes.count, k0 = max(0, n - 780), i0 = max(0, n - 253)
        let y1 = Array(o.closes[i0...])
        let fx = FX(date: o.dates.last ?? Day.today, close: last, kalman_level: last, dev_from_kalman: nil, ret_1m: last / o.closes[max(0, n - 22)] - 1,
                    ret_3m: last / o.closes[max(0, n - 64)] - 1, ret_1y: n > 253 ? last / o.closes[n - 253] - 1 : nil, vol_ewma: nil, vol_hist: nil, slope_ann: nil,
                    trend: nil, hi1: y1.max(), lo1: y1.min(), dates: Array(o.dates[k0...]), closes: Array(o.closes[k0...]), level: Array(o.closes[k0...]))
        return File(date: Day.today, divs: [:], fx: fx)
    }

    /// 내 Alpaca 키로 받은 현금 배당 (배당락일·주당 금액, 지난 3년). 미국 종목만, 하루 한 번 (alpaca_divs.json). 토스에는 배당 자료가 없다
    static func alpacaDivs(_ keys: [String], prices: SitePrices.File?) async -> [String: Div] {
        struct Disk: Codable { var got: Double; var keys: [String]; var divs: [String: Div] }
        let name = "alpaca_divs.json", now = Date().timeIntervalSince1970
        let want = Set(keys), old = Store.read(Disk.self, name)
        if let o = old, now - o.got < 86400, want.isSubset(of: Set(o.keys)) { return o.divs }
        let code = Dictionary(want.compactMap { k in AlpacaFeed.code(k).map { ($0, k) } }, uniquingKeysWith: { a, _ in a })
        guard let k = AlpacaKeys.load(), !code.isEmpty else { return old?.divs ?? [:] }
        var ev: [String: [Ev]] = [:], token: String? = nil
        for _ in 0..<10 {
            var c = URLComponents(string: "https://data.alpaca.markets/v1/corporate-actions")!
            c.queryItems = [.init(name: "symbols", value: code.keys.sorted().joined(separator: ",")), .init(name: "types", value: "cash_dividend"),
                            .init(name: "start", value: Day.str(Date().addingTimeInterval(-3 * 365.25 * 86400))), .init(name: "end", value: Day.today),
                            .init(name: "limit", value: "1000")] + (token.map { [URLQueryItem(name: "page_token", value: $0)] } ?? [])
            var r = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
            r.setValue(k.id, forHTTPHeaderField: "APCA-API-KEY-ID")
            r.setValue(k.secret, forHTTPHeaderField: "APCA-API-SECRET-KEY")
            guard let (d, resp) = try? await URLSession.shared.data(for: r), (resp as? HTTPURLResponse)?.statusCode == 200,
                  let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return old?.divs ?? [:] }
            for v in ((o["corporate_actions"] as? [String: Any])?["cash_dividends"] as? [[String: Any]]) ?? [] {
                guard let s = (v["symbol"] as? String).flatMap({ code[$0] }), let dd = v["ex_date"] as? String, let a = num(v["rate"]), a > 0 else { continue }
                ev[s, default: []].append(Ev(d: dd, a: a))
            }
            token = (o["next_page_token"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            if token == nil { break }
        }
        var out: [String: Div] = [:]
        for s in want where code.values.contains(s) { out[s] = Div(ccy: "USD", price: prices?.h[s]?.closes.last, events: (ev[s] ?? []).sorted { $0.d < $1.d }) }
        Store.write(Disk(got: now, keys: Array(want), divs: out), name)
        return out
    }

    static func key(_ id: String) -> String {
        if let k = YahooSample.keyFor[id] { return k }
        return Sample.symbol(id).map { CustomSymbols.yahoo(for: $0) } ?? id
    }
    func div(_ id: String) -> Div? { file?.divs[Self.key(id)] }
    /// 요인 민감도 (웹 Model.factorBetas, 사이트 stats.json): mkt = S&P500 로그 변화당, rate = 10년 금리 1bp 당, oil·gold·cmdty = 그 지표 로그 변화당 (시장 움직임 뺀 값)
    /// 한국 종목·국내 주식 합계는 전망과 같은 키(KR~기준 ETF, 합계는 KR~EWY. 웹과 같은 수익률)로 찾는다
    func beta(_ factor: String, _ id: String) -> Double? { Market.shared.stats?.betas?[factor]?[SiteStats.mcKey(id)] }
    /// 민감도 신뢰도 (같은 방향 비율 agree 등). 외부 요인 화면용
    func betaStat(_ factor: String, _ id: String) -> MC.BetaStat? { Market.shared.stats?.betaStat?[factor]?[SiteStats.mcKey(id)] }
    /// 요인 보통 한 달 변화 (fx = 원/달러 포함)
    func factorMonth(_ factor: String) -> MC.FactorMove? { Market.shared.stats?.factors?[factor] }

    // MARK: 웹과 같은 식

    static let wht = 0.15, cgt = 0.22, cgtDed = 2_500_000.0, krDivTax = 0.154

    /// web/model.js addMonths: 월을 더하고, 그 달에 없는 날이면 그 달 마지막 날
    static func addMonths(_ s: String, _ n: Int) -> String {
        let p = s.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return s }
        let m0 = p[0] * 12 + (p[1] - 1) + n, y = Int((Double(m0) / 12).rounded(.down)), mo = m0 - y * 12 + 1
        let last = utcCal.range(of: .day, in: .month, for: utcCal.date(from: DateComponents(year: y, month: mo, day: 1))!)?.count ?? 28
        return String(format: "%04d-%02d-%02d", y, mo, min(p[2], last))
    }
    static func today(_ d: Date = Date()) -> String { dayFmt.string(from: d) }
    /// 달력·날짜 틀은 만들 때 비싸서 한 번만 만든다 (배당 계산이 종목마다 수십 번 부른다)
    private static let utcCal: Calendar = { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "UTC")!; return c }()
    private static let dayFmt: DateFormatter = { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f }()
    /// web/app.js divGrowth: 지난 12개월 합 ÷ 24~36개월 전 12개월 합 의 제곱근 − 1 (0~15%)
    static func divGrowth(_ ev: [Ev], today t: String) -> Double {
        let a = addMonths(t, -12), b = addMonths(t, -24), c = addMonths(t, -36)
        let y1 = ev.filter { $0.d > a }.reduce(0) { $0 + $1.a }, y3 = ev.filter { $0.d > c && $0.d <= b }.reduce(0) { $0 + $1.a }
        return y1 > 0 && y3 > 0 ? max(0, min(0.15, (y1 / y3).squareRoot() - 1)) : 0
    }
    struct DivOut { var n: Int; var y1: Double; var y3: Double; var gr: Double; var yld: Double; var byMonth: [String: Double] }
    /// web/app.js renderCash 의 종목 하나: 지난 12개월 배당이 1·2·3년 뒤 같은 달에 (1+증가율)^(n−1) 로 다시 온다고 본 세후 원화
    static func project(_ ev: [Ev], shares: Double, fx: Double, krw: Bool, price: Double, today t: String = today()) -> DivOut? {
        let yAgo = addMonths(t, -12), ds = ev.filter { $0.d > yAgo }
        guard !ds.isEmpty else { return nil }
        let months = Set((1...36).map { String(addMonths(t, $0).prefix(7)) }), tax = krw ? krDivTax : wht, gr = divGrowth(ev, today: t)
        var by: [String: Double] = [:], y1 = 0.0, y3 = 0.0
        for x in ds {
            for n in 1...3 {
                let m = String(addMonths(x.d, 12 * n).prefix(7)), v = x.a * shares * fx * (1 - tax) * pow(1 + gr, Double(n - 1))
                if months.contains(m) { by[m, default: 0] += v; y3 += v; if n == 1 { y1 += v } }
            }
        }
        return DivOut(n: ds.count, y1: y1, y3: y3, gr: gr, yld: price > 0 ? ds.reduce(0) { $0 + $1.a } / price : 0, byMonth: by)
    }
    /// web/app.js renderSens 의 한 종목: 요인이 shock 만큼 움직일 때 가격 변화율. rate 는 shock 이 bp, 나머지는 % (−10 = −10%)
    static func factorMove(_ factor: String, beta b: Double, shock: Double) -> Double {
        exp(b * (factor == "rate" ? shock : log(1 + shock / 100))) - 1
    }
    /// 일정별 발표일 '보통 움직임' (web/model.js EXT_EVENTS 의 factor·sd 와 같은 고정 가정값). 단위 %
    static let eventMoves: [String: (factor: String, sd: Double)] = [
        "x_fomc": ("mkt", 1.0), "x_cpi": ("mkt", 0.8), "x_jobs": ("mkt", 0.6), "x_bok": ("fx", 0.4),
        "x_opec": ("oil", 4), "x_mid": ("mkt", 1.5), "x_witch": ("mkt", 0.5),
    ]
    /// 과거 재생 기간 (web/model.js REPLAYS 와 같은 값). 단위: mkt·fx·oil·gold %, rate %p
    struct Replay { let when: String; let name: String; let mv: [String: Double] }
    static let replays: [Replay] = [
        Replay(when: "2020.3", name: String(localized: "코로나"), mv: ["mkt": -34, "rate": -0.8, "fx": 8, "oil": -56, "gold": -3]),
        Replay(when: "2022", name: String(localized: "금리 급등"), mv: ["mkt": -25, "rate": 2.3, "fx": 21, "oil": 15, "gold": -7]),
        Replay(when: "2024.8", name: String(localized: "엔캐리 청산"), mv: ["mkt": -8.5, "rate": -0.4, "fx": -1.5, "oil": -10, "gold": -2.5]),
    ]
    /// web/model.js replayMove 와 같은 식: 원화 평가액 변화율과 그중 환율 몫. β 를 모르면 시장 1, 그 외 0
    static func replayMove(_ rp: Replay, usd: Bool, beta: (String) -> Double?) -> (total: Double, fx: Double) {
        var lg = 0.0
        for k in ["mkt", "rate", "oil", "gold"] {
            let b = beta(k) ?? (k == "mkt" ? 1 : 0), x = rp.mv[k] ?? 0
            lg += b * (k == "rate" ? x * 100 : log(1 + x / 100))
        }
        let p = exp(lg) - 1, f = usd ? (rp.mv["fx"] ?? 0) / 100 : 0
        return ((1 + p) * (1 + f) - 1, (1 + p) * f)
    }
    /// 해외주식 양도세 (웹과 같은 식): 이익에서 공제 250만원을 빼고 22%
    static func capGainTax(_ gain: Double) -> Double { max(0, gain - cgtDed) * cgt }

    /// web/app.js fxInfo: 원/달러 앞날 분위수 now·exp((μ − σ²/2)t + zσ√t). μ·σ 는 웹 전망 모형의 환율 요인 값 (stats.json)
    static func fxAt(now: Double, t: Double, z: Double, mu: Double, vol: Double) -> Double {
        now * exp((mu - vol * vol / 2) * t + z * vol * t.squareRoot())
    }
}
