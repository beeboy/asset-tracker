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
    static func load() -> File? {
        decode(Store.readData(name)) ?? decode(Bundle.main.url(forResource: "calc", withExtension: "json").flatMap { try? Data(contentsOf: $0) })
    }
    /// 사이트는 하루 한 번 만든다. 6시간 안에 받았으면 다시 받지 않는다
    func refresh() async {
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
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        let last = cal.range(of: .day, in: .month, for: cal.date(from: DateComponents(year: y, month: mo, day: 1))!)?.count ?? 28
        return String(format: "%04d-%02d-%02d", y, mo, min(p[2], last))
    }
    static func today(_ d: Date = Date()) -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: d)
    }
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
    /// 해외주식 양도세 (웹과 같은 식): 이익에서 공제 250만원을 빼고 22%
    static func capGainTax(_ gain: Double) -> Double { max(0, gain - cgtDed) * cgt }

    /// web/app.js fxInfo: 원/달러 앞날 분위수 now·exp((μ − σ²/2)t + zσ√t). μ·σ 는 웹 전망 모형의 환율 요인 값 (stats.json)
    static func fxAt(now: Double, t: Double, z: Double, mu: Double, vol: Double) -> Double {
        now * exp((mu - vol * vol / 2) * t + z * vol * t.squareRoot())
    }
}
