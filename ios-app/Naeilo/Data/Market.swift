import Foundation
import Observation

// 지금 시세. 계약 전이라 테스트는 Yahoo 중계 자료(아래 YahooSample). Tiingo 회신(10월 10일): 월 $250 요금제로 미국 종목 실시간 참고 시세와 원/달러를 앱·위젯에 보여 줄 수 있다.
// 한국 종목은 Tiingo에 없어서 한국 시세 제공처(EODHD 또는 코스콤)가 정해질 때까지 전일 종가를 쓴다.
// 출처 표기 "Data powered by Tiingo.com"은 설정 화면에만 두면 된다.

struct Quote {
    let last: Double        // 지금 가격 (실시간이 아니면 전일 종가)
    let prevClose: Double   // 전일 종가
    let live: Bool
    var change: Double { prevClose > 0 ? last / prevClose - 1 : 0 }
}

@Observable
final class Market {
    static let shared = Market()

    var updatedAt = Date()
    /// Tiingo 에서 받은 값 (티커 → 지금 가격, 전일 종가). 비어 있으면 예시 값
    var live: [String: (Double, Double)] = [:]
    var liveFx: (Double, Double)? = nil
    /// 사이트가 30분마다 모으는 시세 (앱·위젯이 같이 받는다). 없으면 빌드 때 넣은 테스트 자료
    var site: QuoteFeed.File? = QuoteFeed.cached()
    /// 매매기준율 (사이트 data/mar.json, 서울외국환중개). 웹 평가액과 같은 환율
    var mar: MarFeed.File? = MarFeed.cached()
    /// 원화 평가에 쓰는 원/달러: 웹 fxBase 와 같게 매매기준율, 없거나 5일 넘게 묵었으면 지금 환율
    var valFx: Double { marRate ?? fx.last }
    var marRate: Double? { mar.flatMap { $0.rate > 0 && Day.dday($0.date) >= -5 ? $0.rate : nil } }
    /// 종목별 전망 입력값 (사이트가 매일 만든다). 없으면 빌드 때 넣은 파일
    var stats: SiteStats.File? = SiteStats.load()
    /// 사이트 지난 종가 (웹 자산 추이·목표 진행과 같은 data/prices 파일). 내 종목과 원/달러만
    var prices: SitePrices.File? = SitePrices.cached()
    var source: String { !live.isEmpty ? "Tiingo" : site != nil ? "naeilo 사이트" : YahooSample.quotes.isEmpty ? "예시 값" : "Yahoo 중계 (테스트)" }

    // 샘플 종목 DRNK 의 하루 움직임 (샘플 기록의 마지막 날). 다른 종목은 받은 시세가 없으면 움직임을 만들지 않는다
    static let todayMove: [String: Double] = ["DRNK": -0.0221]

    func quote(_ s: Symbol) -> Quote {
        if let q = live[s.id] { return Quote(last: q.0, prevClose: q.1, live: true) }
        if s.id != "DRNK" {
            // 사이트 시세와 추가한 종목(5분마다 Yahoo 중계) 중 나중에 받은 값
            let c = CustomSymbols.shared.items.first { $0.id == s.id }
            if let f = site, let q = f.q[YahooSample.keyFor[s.id] ?? CustomSymbols.yahoo(for: s)],
               c == nil || c!.at.timeIntervalSince1970 < f.got {
                return Quote(last: q.last, prevClose: q.prev, live: s.currency == .usd)
            }
            if let c { return Quote(last: c.last, prevClose: c.prev, live: c.usd) }
        }
        if let q = YahooSample.quotes[s.id] { return Quote(last: q.last, prevClose: q.prevClose, live: true) }
        if let m = Self.todayMove[s.id] { return Quote(last: s.close * (1 + m), prevClose: s.close, live: true) }
        return Quote(last: s.close, prevClose: s.close, live: false)
    }

    /// 원/달러 (지금, 전일)
    var fx: Quote {
        if let f = liveFx { return Quote(last: f.0, prevClose: f.1, live: true) }
        if let q = site?.q["KRW=X"] { return Quote(last: q.last, prevClose: q.prev, live: true) }
        if let q = YahooSample.quotes["FX"] { return Quote(last: q.last, prevClose: q.prevClose, live: true) }
        return Quote(last: Sample.fx, prevClose: Sample.fx, live: false)
    }

    var asOfText: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR")
        if live.isEmpty, let s = site, let t = s.q.values.map(\.at).max(), t > 0 {
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm" : "M월 d일 a h:mm"
            let ago = Int(Date().timeIntervalSince1970 - s.got) / 60
            return "\(f.string(from: d)) 시세 · \(ago < 1 ? "방금" : "\(ago)분 전") 받음 · 한국 종목은 전일 종가"
        }
        if live.isEmpty, let t = YahooSample.asOf {
            f.dateFormat = "M월 d일 a h:mm"
            return "\(f.string(from: t)) 시세 (테스트 자료) · 한국 종목은 전일 종가"
        }
        f.dateFormat = "a h:mm"
        return "지금 시세 · \(f.string(from: updatedAt)) 갱신 · 한국 종목은 전일 종가"
    }

    /// force: 아래로 당겨 갱신할 때. 받은 지 얼마 안 됐어도 다시 받는다
    func refresh(held: Set<String> = [], force: Bool = false) async {
        if let p = TiingoProvider.fromConfig() {
            let tickers = Sample.symbols.filter { $0.currency == .usd && $0.id != "DRNK" }.map(\.id)
            if let q = try? await p.quotes(tickers) { live = q }
            if let f = try? await p.usdkrw() { liveFx = f }
        }
        // 사이트 시세: 4분 안에 받았으면 그대로 (사이트는 30분마다 새로 모은다)
        if let f = await QuoteFeed.fetch(minAge: force ? 0 : 240) { site = f }
        if let r = await MarFeed.fetch(minAge: force ? 0 : 1800) { mar = r }
        // 지난 종가: 30분마다 사이트 index.json 만 보고, 새로 모았을 때만 내 종목 파일을 다시 받는다 (내 길·전망 기준값이 웹과 같은 종가)
        let keys = held.isEmpty ? Set(prices?.h.keys.map { $0 } ?? []) : Set(held.filter { $0 != "DRNK" }.map(SiteStats.key))
        if let p = await SitePrices.fetch(keys: keys.union(["KRW=X"]), old: prices, minAge: force ? 0 : 1800) { prices = p }
        // 전망 입력값: 하루 한 번 바뀌므로 6시간에 한 번 (당겨 갱신하면 바로)
        if let s = await SiteStats.fetch(minAge: force ? 0 : 6 * 3600) { stats = s }
        // 추가한 종목: 내 종목에 있는 것만 5분에 한 번 (당겨 갱신하면 바로)
        await CustomSymbols.shared.refresh(held: held, force: force)
        updatedAt = Date()
    }

    /// 아래로 당겨 갱신: 내 종목을 바로 받고 위젯에도 넘긴다
    @MainActor
    func pullRefresh(_ m: AppModel) async {
        await refresh(held: Set(m.holdings.map(\.symbol)), force: true)
        if m.persists { m.recordDay() }     // 내 기록: 새로 받은 시세로 오늘 줄
        LineClock.shared.next()     // 당기면 인물 대사도 다음 줄로
        WidgetBridge.write(m)
    }
}

extension Symbol {
    var quote: Quote { Market.shared.quote(self) }
    /// 지금 가격
    var last: Double { quote.last }
}

// Tiingo REST. 출시 때는 토큰을 앱에 넣지 않고 naeilo 중계 서버가 받아 캐시해서 내려준다 (견적 협의의 계약 조건).
// 개발 중에는 Info.plist 의 TIINGO_TOKEN 에 개발용 토큰을 넣으면 시뮬레이터에서 실제 값을 볼 수 있다.
struct TiingoProvider {
    let token: String

    static func fromConfig() -> TiingoProvider? {
        guard let t = Bundle.main.object(forInfoDictionaryKey: "TIINGO_TOKEN") as? String, !t.isEmpty else { return nil }
        return TiingoProvider(token: t)
    }

    private func get(_ path: String) async throws -> Any {
        var c = URLComponents(string: "https://api.tiingo.com" + path)!
        c.queryItems = (c.queryItems ?? []) + [URLQueryItem(name: "token", value: token)]
        let (data, _) = try await URLSession.shared.data(for: URLRequest(url: c.url!, timeoutInterval: 15))   // 응답 없으면 15초 뒤 포기
        return try JSONSerialization.jsonObject(with: data)
    }

    /// IEX 실시간 참고 시세: tngoLast(지금), prevClose(전일 종가)
    func quotes(_ tickers: [String]) async throws -> [String: (Double, Double)] {
        guard !tickers.isEmpty else { return [:] }
        let arr = try await get("/iex/?tickers=" + tickers.joined(separator: ",").lowercased()) as? [[String: Any]] ?? []
        var out: [String: (Double, Double)] = [:]
        for o in arr {
            guard let t = (o["ticker"] as? String)?.uppercased(), let last = (o["tngoLast"] ?? o["last"]) as? Double,
                  let prev = o["prevClose"] as? Double else { continue }
            out[t] = (last, prev)
        }
        return out
    }

    /// 원/달러: fx/top 의 midPrice, 전일 값은 일별 끝값
    func usdkrw() async throws -> (Double, Double)? {
        let arr = try await get("/tiingo/fx/top?tickers=usdkrw") as? [[String: Any]] ?? []
        guard let o = arr.first, let mid = (o["midPrice"] ?? o["bidPrice"]) as? Double else { return nil }
        let start = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-6 * 86400)).prefix(10)
        let hist = try await get("/tiingo/fx/usdkrw/prices?resampleFreq=1day&startDate=\(start)") as? [[String: Any]] ?? []
        let prev = hist.dropLast().last?["close"] as? Double ?? mid
        return (mid, prev)
    }
}

// 테스트 시세: 사이트가 모으는 Yahoo 중계 자료 (data/prices/*.json 3년 종가, data/quotes.json 전일 종가·현재가).
// DRNK 는 가상 종목이라 시안 값, 한국 종목과 MSFT 는 자료가 없어 시안 값을 쓴다.
enum YahooSample {
    struct Hist: Codable { let dates: [String]; let closes: [Double] }
    struct Q { let prevClose: Double; let last: Double; let time: Date }

    static let keyFor: [String: String] = ["QQQ": "QQQ", "AAPL": "AAPL", "NVDA": "NVDA", "SPY": "SPY", "FX": "KRW=X"]
    private static func file(_ k: String) -> String { k.replacingOccurrences(of: "=", with: "_").replacingOccurrences(of: "^", with: "_") }

    static let quotes: [String: Q] = {
        guard let url = Bundle.main.url(forResource: "quotes", withExtension: "json"),
              let d = try? Data(contentsOf: url),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: [String: Any]] else { return [:] }
        var out: [String: Q] = [:]
        for (id, k) in keyFor {
            guard let v = o[k], let prev = v["prev_close"] as? Double, let last = (v["last"] ?? v["regular"]) as? Double else { continue }
            out[id] = Q(prevClose: prev, last: last, time: Date(timeIntervalSince1970: (v["last_time"] as? Double) ?? 0))
        }
        return out
    }()

    static let history: [String: Hist] = {
        var out: [String: Hist] = [:]
        for (id, k) in keyFor {
            guard let url = Bundle.main.url(forResource: file(k), withExtension: "json", subdirectory: "prices"),
                  let d = try? Data(contentsOf: url),
                  let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                  let dates = o["dates"] as? [String], let c = o["close"] as? [Any] else { continue }
            let closes = c.map { ($0 as? Double) ?? .nan }
            // 빈 값은 앞 값으로 메운다
            var filled: [Double] = []; var prev = closes.first { !$0.isNaN } ?? 1
            for v in closes { if !v.isNaN { prev = v }; filled.append(prev) }
            out[id] = Hist(dates: dates, closes: filled)
        }
        return out
    }()

    /// 자료가 언제 값인지 (현재가 시각)
    static var asOf: Date? { quotes.values.map(\.time).max() }

    /// 빌드 때 넣은 지난 종가 전부 (사이트 티커 → 종가). 사이트에서 아직 못 받았을 때만 쓴다
    static let bundled: [String: Hist] = {
        var out: [String: Hist] = [:]
        for url in Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "prices") ?? [] {
            guard let d = try? Data(contentsOf: url), let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                  let k = o["symbol"] as? String, let h = SitePrices.hist(o) else { continue }
            out[k] = h
        }
        return out
    }()
}

/// 사이트 지난 종가 (data/prices/*.json): 웹이 자산 추이·목표 진행(내 길)에 쓰는 것과 같은 파일.
/// 빌드 때 넣은 파일은 맥에서 받은 날에 멈춰 있어서, 앱은 내 종목과 원/달러만 사이트에서 다시 받아 둔다
enum SitePrices {
    /// h 키 = 사이트 티커 (KRW=X 등). asked = 그때 받으려던 티커 (사이트에 없는 종목은 h 에 없다)
    struct File: Codable { var got: Double; var updated: String; var asked: [String]; var h: [String: YahooSample.Hist] }
    static let name = "site_prices.json"
    static func cached() -> File? { Store.read(File.self, name) }

    /// 빈 값은 앞 값으로 메운다 (웹 history 와 같다)
    static func hist(_ o: [String: Any]) -> YahooSample.Hist? {
        guard let ds = o["dates"] as? [String], let c = o["close"] as? [Any], ds.count == c.count, !ds.isEmpty else { return nil }
        let raw: [Double] = c.map { ($0 as? Double) ?? .nan }
        var prev: Double = raw.first(where: { !$0.isNaN }) ?? 1
        var out: [Double] = []
        for v in raw { if !v.isNaN { prev = v }; out.append(prev) }
        return .init(dates: ds, closes: out)
    }

    private static func get(_ path: String) async -> [String: Any]? {
        var c = URLComponents(url: Config.data.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 600)))]
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    /// keys = 사이트 티커. 받은 지 minAge 안이고 다 있으면 그대로. 사이트가 새로 모은 게 없으면(index.json updated 같음) 받은 것을 다시 쓴다
    static func fetch(keys: Set<String>, old: File?, minAge: TimeInterval) async -> File? {
        let now = Date().timeIntervalSince1970
        if let o = old, now - o.got < minAge, keys.isSubset(of: Set(o.asked)) { return o }
        guard let idx = await get("index.json"), let files = idx["prices"] as? [String: String] else { return old }
        let updated = idx["updated"] as? String ?? ""
        var h: [String: YahooSample.Hist] = [:]
        for k in keys {
            if old?.updated == updated, let x = old?.h[k] { h[k] = x; continue }
            guard let f = files[k] else { continue }
            if let o = await get("prices/" + f), let x = hist(o) { h[k] = x } else if let x = old?.h[k] { h[k] = x }
        }
        let out = File(got: now, updated: updated, asked: keys.sorted(), h: h)
        Store.write(out, name)
        return out
    }
}

/// 매매기준율: 사이트가 하루 한 번 저장하는 data/mar.json ({"USD": {"rate", "date"}}). 웹 평가액과 같은 값
enum MarFeed {
    struct File: Codable { var rate: Double; var date: String; var got: Double }
    static let name = "site_mar.json"
    static func cached() -> File? { Store.read(File.self, name) }

    /// 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값
    static func fetch(minAge: TimeInterval = 0) async -> File? {
        let old = cached()
        if let o = old, Date().timeIntervalSince1970 - o.got < minAge { return o }
        var c = URLComponents(url: Config.data.appendingPathComponent("mar.json"), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 600)))]
        var best: File? = await get(URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10))
        // 웹 marFetch 와 같게: 저장소 값이 오늘 고시가 아니면 중계(/mar)에서 한 번 더 (더 새 날짜일 때만 바꾼다)
        let have = max(best?.date ?? "", old?.date ?? "")
        if have < Day.today, let x = await get(URLRequest(url: Config.relay.appendingPathComponent("mar"), timeoutInterval: 10)), x.date > (best?.date ?? "") { best = x }
        guard var f = best else { return old }
        if let o = old, o.date > f.date { f = File(rate: o.rate, date: o.date, got: f.got) }   // 중계에서 받아 둔 더 새 값은 지킨다
        Store.write(f, name)
        return f
    }
    private static func get(_ req: URLRequest) async -> File? {
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let u = o["USD"] as? [String: Any], let rate = num(u["rate"]), rate > 0, let date = u["date"] as? String else { return nil }
        return File(rate: rate, date: date, got: Date().timeIntervalSince1970)
    }
}
