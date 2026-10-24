import Foundation

// 종목별 전망 입력값 (naeilo.com/data/stats.json). 사이트가 매일 web/model.js 와 같은 계산(지난 3년 종가, 사전값 수축)으로 만든다.
// 앱 전망은 이 값과 같이 들어 있는 기본 사건·요인 민감도·설정으로 웹과 같은 몬테카를로를 돌린다 (MonteCarlo.swift, ForecastEngine.swift).
// 닫힌 식(AppModel.forecast)은 계산이 끝나기 전 잠깐 보여 주는 값과 주간 예보 폭에 쓴다. 받은 적이 없으면 빌드 때 넣은 파일
enum SiteStats {
    typealias T = MC.Stats.Ticker
    typealias D = MC.Stats.Defaults
    typealias C = MC.Stats.Corr
    typealias File = MC.Stats

    static let name = "site_stats.json"
    private static func decode(_ d: Data?) -> File? { d.flatMap { try? JSONDecoder().decode(File.self, from: $0) } }
    /// 공개 베타·출시는 받아 둔 사이트 값을 읽지 않고 번들 값(가상 샘플)만
    static func load() -> File? {
        (Config.usesCollectedData ? decode(Store.readData(name)) : nil) ?? decode(Bundle.main.url(forResource: "stats", withExtension: "json").flatMap { try? Data(contentsOf: $0) })
    }

    /// 받은 지 minAge 초가 안 됐으면 받지 않는다 (사이트는 하루 한 번 만든다). 실패하면 nil
    static func fetch(minAge: TimeInterval = 6 * 3600) async -> File? {
        guard Config.usesCollectedData else { return nil }
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

    /// 앱 종목 이름 → 사이트 티커 (stats.json·사건 대상과 같은 이름)
    static func key(_ id: String) -> String {
        if let k = YahooSample.keyFor[id] { return k }
        return Sample.symbol(id).map { CustomSymbols.yahoo(for: $0) } ?? id
    }

    /// 전망 계산용 사이트 티커: 한국 종목은 기준 미국 ETF 의 원화 환산 묶음 KR~SPY (환헤지면 KR~SPY~H, 직접 입력 종목은 KR~EWY).
    /// tools/site_stats.mjs 가 만들고, 웹 synthKR 의 종목 시세와 같은 수익률이다 (KoreaEstimate)
    static func mcKey(_ id: String) -> String {
        if let c = CustomSymbols.shared.saved(id), !c.usd {
            let i = KoreaEstimate.info(id: id, name: c.name)
            return "KR~" + i.base + (i.hedged ? "~H" : "")
        }
        return key(id)
    }

    /// (현재 정세 연 기대, 과거 추세 연 기대, 연 변동성). 사이트 값 → 앱에 넣은 예시 표 → 웹이 모르는 종목에 쓰는 기본값
    static func params(_ id: String, _ f: File? = Market.shared.stats) -> (Double, Double, Double) {
        if let t = f?.tickers[mcKey(id)], let mu = t.mu, let vol = t.vol { return (mu, t.trend ?? mu, vol) }
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

/// 비중 바꿔 비교에서 사이트 표(stats.json)에 없는 종목: 받은 3년 종가로 web/model.js buildModel 과 같은 계산(사전값 수축·상관)을 앱에서 한다.
/// 키는 앱 종목 이름 (미국 티커, 한국 6자리 코드). 종가는 기기에 저장해 하루 안에는 다시 받지 않는다
@Observable
final class ExtraStats {
    static let shared = ExtraStats()
    struct Item: Codable { var name: String; var hist: YahooSample.Hist; var at: Date; var usd: Bool?; var yahoo: String? }
    private(set) var items: [String: Item] = (Store.readData(Config.usesCollectedData ? "extra_stats.json" : "").flatMap { try? JSONDecoder().decode([String: Item].self, from: $0) }) ?? [:]

    func name(_ k: String) -> String? { items[k]?.name }
    func usd(_ k: String) -> Bool? { items[k].map { $0.usd ?? true } }

    /// 3년 종가를 받는다 (하루 안에 받은 게 있으면 그대로)
    func fetch(_ h: StockHit) async throws {
        if let it = items[h.id], Date().timeIntervalSince(it.at) < 86400 { return }
        let (_, hs) = try await CustomSymbols.chart(h.yahoo, range: "3y")
        guard hs.closes.count > 5 else { throw URLError(.cannotParseResponse) }
        await MainActor.run {
            items[h.id] = Item(name: h.name, hist: YahooSample.Hist(dates: hs.dates, closes: hs.closes), at: Date(), usd: h.currency == .usd, yahoo: h.yahoo)
            if let d = try? JSONEncoder().encode(items) { Store.writeData(d, "extra_stats.json") }
        }
    }

    /// 모의 바구니에 담은 종목은 하루 한 번 종가를 새로 받는다 (바구니 평가액이 이어지게)
    func refresh(_ keys: Set<String>) async {
        for k in keys {
            guard let it = items[k], Date().timeIntervalSince(it.at) > 86400 else { continue }
            guard let (_, hs) = try? await CustomSymbols.chart(it.yahoo ?? k, range: "3y"), hs.closes.count > 5 else { continue }
            await MainActor.run {
                items[k]?.hist = YahooSample.Hist(dates: hs.dates, closes: hs.closes); items[k]?.at = Date()
                if let d = try? JSONEncoder().encode(items) { Store.writeData(d, "extra_stats.json") }
            }
        }
    }

    /// st 에 없는 keys 를 받은 종가로 계산해 더한 사본. 계산할 게 없으면 st 그대로
    func augment(_ st: MC.Stats, keys: [String]) -> MC.Stats {
        let add = keys.filter { st.tickers[$0] == nil && items[$0] != nil }
        guard !add.isEmpty else { return st }
        let TD = MC.TD, m = st.model
        let prior = (m?.prior_mu ?? 10) / 100, tau = 0.15, newVol = 0.60, defCorr = m?.default_corr ?? st.defaults.corr
        // 지난 3년 로그수익률 (날짜 → 값)
        func rets(_ h: YahooSample.Hist) -> [String: Double] {
            guard let last = h.dates.last else { return [:] }
            let cut = MC.dayStr(MC.dayNum(last) - 1096)
            var out: [String: Double] = [:], prev: Double? = nil
            for (d, v) in zip(h.dates, h.closes) where d >= cut && v > 0 {
                if let p = prev { out[d] = log(v / p) }
                prev = v
            }
            return out
        }
        func mean(_ x: [Double]) -> Double { x.isEmpty ? 0 : x.reduce(0, +) / Double(x.count) }
        func sd(_ x: [Double]) -> Double { let mu = mean(x); return (x.reduce(0) { $0 + ($1 - mu) * ($1 - mu) } / Double(max(1, x.count - 1))).squareRoot() }
        var out = st
        var keysAll = st.corr.keys, M = st.corr.m
        var cash: [String: Bool] = Dictionary(uniqueKeysWithValues: st.tickers.map { ($0.key, $0.value.cash == true || $0.value.fx == true) })
        let site = { (k: String) -> [String: Double]? in (Market.shared.prices?.h[k]).map { rets($0) } }
        for k in add {
            guard let it = items[k] else { continue }
            let rs = rets(it.hist), r = rs.values.map { $0 }, n = r.count
            let volRaw = n > 1 ? sd(r) * TD.squareRoot() : nil
            let isCash = volRaw.map { n > 20 && $0 < 0.03 } ?? false
            var vol = st.defaults.vol
            if let vr = volRaw, n >= 5 {
                if n < Int(TD) && !isCash { let w = Double(n) / (Double(n) + TD); vol = (w * vr * vr + (1 - w) * newVol * newVol).squareRoot() } else { vol = vr }
            }
            var mu = prior
            if isCash {
                let c = it.hist.closes, j = max(0, c.count - 253)
                mu = c.count > 1 ? pow(c[c.count - 1] / c[j], TD / Double(max(1, c.count - 1 - j))) - 1 : 0.04
            } else if n >= 5, let vr = volRaw {
                let arith = mean(r) * TD + 0.5 * vr * vr, se2 = vol * vol / (Double(n) / TD), kk = tau * tau / (tau * tau + se2)
                mu = kk * arith + (1 - kk) * prior
            }
            // 과거 추세: 3년 로그 종가 직선 기울기 (연)
            var gs: Double? = nil, trend = mu
            if !isCash, n >= 5 {
                let cut = MC.dayStr(MC.dayNum(it.hist.dates.last ?? "") - 1096)
                let pts = zip(it.hist.dates, it.hist.closes).filter { $0.0 >= cut && $0.1 > 0 }.map { (Double(MC.dayNum($0.0)) / 365.25, log($0.1)) }
                if pts.count >= 20 {
                    let mt = mean(pts.map { $0.0 }), my = mean(pts.map { $0.1 })
                    let sxy = pts.reduce(0) { $0 + ($1.0 - mt) * ($1.1 - my) }, sxx = pts.reduce(0) { $0 + ($1.0 - mt) * ($1.0 - mt) }
                    gs = sxx > 0 ? sxy / sxx : 0
                }
                if let g = gs { let w = min(1, Double(n) / TD); trend = w * (exp(max(-0.9, min(1.2, g)) + 0.5 * vol * vol) - 1) + (1 - w) * mu }
            }
            out.tickers[k] = MC.Stats.Ticker(mu: mu, trend: trend, gs: gs, vol: vol, n: n, cash: isCash ? true : nil, fx: nil)
            cash[k] = isCash
            // 상관: 표에 있는 종목과 겹치는 날로 (40일 미만이면 사전값, 1년 미만이면 사전값과 섞기)
            var row: [Double] = []
            for o in keysAll {
                let prior0 = isCash || cash[o] == true ? 0 : defCorr
                var c = prior0
                if let orr = site(o) ?? items[o].map({ rets($0.hist) }) {
                    var a: [Double] = [], b: [Double] = []
                    for (d, x) in rs { if let y = orr[d] { a.append(x); b.append(y) } }
                    if a.count >= 40 {
                        let ma = mean(a), mb = mean(b)
                        var sab = 0.0, saa = 0.0, sbb = 0.0
                        for t in a.indices { sab += (a[t] - ma) * (b[t] - mb); saa += (a[t] - ma) * (a[t] - ma); sbb += (b[t] - mb) * (b[t] - mb) }
                        c = saa > 0 && sbb > 0 ? sab / (saa * sbb).squareRoot() : 0
                        if Double(a.count) < TD { let w = Double(a.count) / (Double(a.count) + TD); c = w * c + (1 - w) * prior0 }
                    }
                }
                row.append(max(-0.95, min(0.95, c)))
            }
            for i in M.indices { M[i].append(row[i]) }
            M.append(row + [1])
            keysAll.append(k)
        }
        out.corr = MC.Stats.Corr(keys: keysAll, m: M)
        return out
    }
}
