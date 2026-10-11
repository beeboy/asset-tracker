import Foundation

// 종목별 전망 입력값 (naeilo.com/data/stats.json). 사이트가 매일 web/model.js 와 같은 계산(지난 3년 종가, 사전값 수축)으로 만든다.
// 앱은 이 값으로 닫힌 식 전망을 내서 웹(몬테카를로)과 같은 종목 값을 쓴다. 받은 적이 없으면 빌드 때 넣은 파일
enum SiteStats {
    struct T: Codable { var mu: Double?; var trend: Double?; var vol: Double?; var cash: Bool?; var fx: Bool? }
    struct D: Codable { var mu: Double; var vol: Double; var corr: Double }
    struct C: Codable { var keys: [String]; var m: [[Double]] }
    struct File: Codable { var date: String; var defaults: D; var tickers: [String: T]; var corr: C }

    static let name = "site_stats.json"
    private static func decode(_ d: Data?) -> File? { d.flatMap { try? JSONDecoder().decode(File.self, from: $0) } }
    static func load() -> File? {
        decode(Store.readData(name)) ?? decode(Bundle.main.url(forResource: "stats", withExtension: "json").flatMap { try? Data(contentsOf: $0) })
    }

    /// 받은 지 minAge 초가 안 됐으면 받지 않는다 (사이트는 하루 한 번 만든다). 실패하면 nil
    static func fetch(minAge: TimeInterval = 6 * 3600) async -> File? {
        let key = "statsGot", now = Date().timeIntervalSince1970
        if now - Store.defaults.double(forKey: key) < minAge { return nil }
        var c = URLComponents(url: Config.data.appendingPathComponent("stats.json"), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(now / 3600)))]
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200, let f = decode(data) else { return nil }
        Store.writeData(data, name)
        Store.defaults.set(now, forKey: key)
        return f
    }

    private static func key(_ id: String) -> String {
        if let k = YahooSample.keyFor[id] { return k }
        return Sample.symbol(id).map { CustomSymbols.yahoo(for: $0) } ?? id
    }

    /// (현재 정세 연 기대, 과거 추세 연 기대, 연 변동성). 사이트 값 → 앱에 넣은 예시 표 → 웹이 모르는 종목에 쓰는 기본값
    static func params(_ id: String, _ f: File? = Market.shared.stats) -> (Double, Double, Double) {
        if let t = f?.tickers[key(id)], let mu = t.mu, let vol = t.vol { return (mu, t.trend ?? mu, vol) }
        if let p = Sample.lensParams[id] { return p }
        let d = f?.defaults ?? D(mu: 0.10, vol: 0.40, corr: 0.3)
        return (d.mu, d.mu, d.vol)
    }
    /// 원/달러 환율 (기대, 변동성). 달러 종목의 원화 평가액에 더해지는 흔들림
    static func fx(_ f: File? = Market.shared.stats) -> (Double, Double) {
        let t = f?.tickers["KRW=X"]
        return (t?.mu ?? 0, t?.vol ?? 0.10)
    }
    /// 두 종목(또는 "KRW=X")의 상관. 표에 없으면 웹과 같게 위험 종목끼리 기본 상관, 현금성·환율이 끼면 0
    static func corr(_ a: String, _ b: String, _ f: File? = Market.shared.stats) -> Double {
        if a == b { return 1 }
        let ka = a == "KRW=X" ? a : key(a), kb = b == "KRW=X" ? b : key(b)
        if let f, let i = f.corr.keys.firstIndex(of: ka), let j = f.corr.keys.firstIndex(of: kb) { return f.corr.m[i][j] }
        let safe = { (k: String) in k == "KRW=X" || f?.tickers[k]?.cash == true }
        return safe(ka) || safe(kb) ? 0 : f?.defaults.corr ?? 0.3
    }
}
