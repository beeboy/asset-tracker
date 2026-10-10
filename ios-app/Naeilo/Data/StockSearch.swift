import Foundation
import Observation

// 종목 찾기·추가 (테스트: 사이트가 쓰는 Yahoo 중계). 시세 계약(Tiingo 등)이 정해지면 Relay 만 바꾸면 된다.
// - 찾기: 앱에 넣어 둔 한글 이름 목록(StockCatalog) + Yahoo 검색(영문 이름·티커·종목 코드). 미국 주식·ETF 와 코스피·코스닥만.
// - 추가: 고르는 순간 3년 일별 종가를 받아 기기에 저장하고, 그 뒤로는 앱이 시세를 갱신할 때 같이 갱신한다.
// - 미국 종목은 지금 가격, 한국 종목은 전일 종가 (시세 기준과 같음).

struct StockHit: Identifiable, Hashable {
    let id: String          // 앱 안의 이름: 미국은 티커, 한국은 6자리 코드
    let yahoo: String       // Yahoo 기호 (005930.KS)
    let name: String
    var alias: String = ""
    var en: String = ""
    let market: String
    let currency: Currency
}

enum Relay {
    static let base = "https://asset-ai.drinker.workers.dev/?url="

    static func yahoo(_ path: String) async throws -> [String: Any] {
        let target = "https://query1.finance.yahoo.com" + path
        guard let enc = target.addingPercentEncoding(withAllowedCharacters: .alphanumerics),
              let url = URL(string: base + enc) else { throw URLError(.badURL) }
        var req = URLRequest(url: url, timeoutInterval: 15)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
}

enum StockSearch {
    private static let usEx: Set<String> = ["NMS", "NYQ", "NGM", "NCM", "PCX", "ASE", "BTS", "NAS", "NYS"]

    /// 찾는 말 정리: 공백 빼고 소문자
    static func norm(_ s: String) -> String { s.lowercased().replacingOccurrences(of: " ", with: "") }

    /// 앱 안 목록에서 찾기 (예시 종목 + 넣어 둔 한글 이름 + 전에 추가한 종목)
    static func local(_ q: String) -> [StockHit] {
        let k = norm(q)
        var out: [StockHit] = []
        var seen = Set<String>()
        func add(_ h: StockHit) { if seen.insert(h.id).inserted { out.append(h) } }
        for s in Sample.symbols + CustomSymbols.shared.symbols
        where k.isEmpty || norm("\(s.id)\(s.name)\(s.search)").contains(k) {
            add(StockHit(id: s.id, yahoo: CustomSymbols.yahoo(for: s), name: s.name, market: s.market, currency: s.currency))
        }
        if !k.isEmpty {
            for h in StockCatalog.all where norm("\(h.id)\(h.name)\(h.alias)\(h.en)").contains(k) { add(h) }
        }
        return out
    }

    /// Yahoo 검색 (한글은 못 찾는다)
    static func remote(_ q: String) async -> [StockHit] {
        let k = q.trimmingCharacters(in: .whitespaces)
        guard k.count >= 1, k.unicodeScalars.allSatisfy({ $0.isASCII }) else { return [] }
        let qs = k.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? k
        guard let o = try? await Relay.yahoo("/v1/finance/search?q=\(qs)&quotesCount=12&newsCount=0"),
              let quotes = o["quotes"] as? [[String: Any]] else { return [] }
        return quotes.compactMap { q -> StockHit? in
            guard let sym = q["symbol"] as? String, let type = q["quoteType"] as? String, type == "EQUITY" || type == "ETF" else { return nil }
            let ex = q["exchange"] as? String ?? ""
            let name = (q["longname"] as? String) ?? (q["shortname"] as? String) ?? sym
            if sym.hasSuffix(".KS") || sym.hasSuffix(".KQ") {
                let code = String(sym.prefix(6))
                // 한글 이름을 아는 종목이면 한글로
                let ko = StockCatalog.all.first { $0.id == code }
                return StockHit(id: code, yahoo: sym, name: ko?.name ?? name, en: name,
                                market: (sym.hasSuffix(".KS") ? "코스피" : "코스닥") + (type == "ETF" ? " ETF" : ""), currency: .krw)
            }
            guard usEx.contains(ex), !sym.contains(".") else { return nil }
            let ko = StockCatalog.all.first { $0.id == sym }
            return StockHit(id: sym, yahoo: sym, name: ko?.name ?? name, en: name, market: type == "ETF" ? "미국 ETF" : "미국 주식", currency: .usd)
        }
    }
}

/// 사용자가 추가한 종목 (예시 목록 밖). 이름·시세·3년 종가를 기기에 저장한다
@Observable
final class CustomSymbols {
    static let shared = CustomSymbols()

    struct Saved: Codable, Hashable {
        let id: String, yahoo: String, name: String, market: String, usd: Bool
        var prev: Double, last: Double, at: Date
    }
    struct Hist: Codable { let dates: [String]; let closes: [Double] }

    private(set) var items: [Saved] = []
    private(set) var hist: [String: Hist] = [:]
    private var refreshedAt = Date.distantPast

    private static var file: URL {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d.appendingPathComponent("custom_symbols.json")
    }
    private struct Disk: Codable { var items: [Saved]; var hist: [String: Hist] }

    init() {
        if let d = try? Data(contentsOf: Self.file), let disk = try? JSONDecoder().decode(Disk.self, from: d) {
            items = disk.items; hist = disk.hist
        }
    }
    private func save() {
        if let d = try? JSONEncoder().encode(Disk(items: items, hist: hist)) { try? d.write(to: Self.file, options: .atomic) }
    }

    var symbols: [Symbol] { items.filter { s in !Sample.baseSymbols.contains { $0.id == s.id } }.map(symbol) }
    private func symbol(_ s: Saved) -> Symbol {
        Symbol(id: s.id, name: s.name, market: s.market, close: s.prev, currency: s.usd ? .usd : .krw, sector: "", search: s.yahoo)
    }
    func symbol(_ id: String) -> Symbol? { items.first { $0.id == id }.map(symbol) }
    func quote(_ id: String) -> Quote? {
        items.first { $0.id == id }.map { Quote(last: $0.last, prevClose: $0.prev, live: $0.usd) }
    }
    static func yahoo(for s: Symbol) -> String {
        if let c = shared.items.first(where: { $0.id == s.id }) { return c.yahoo }
        if let c = StockCatalog.all.first(where: { $0.id == s.id }) { return c.yahoo }
        return s.currency == .krw ? s.id + ".KS" : s.id
    }

    /// 3년 종가를 받아 저장 (추가할 때). 실패하면 이유를 던진다
    @discardableResult
    func fetch(_ h: StockHit) async throws -> Symbol {
        let (q, hs) = try await Self.chart(h.yahoo, range: "3y")
        let s = Saved(id: h.id, yahoo: h.yahoo, name: h.name, market: h.market, usd: h.currency == .usd, prev: q.prev, last: q.last, at: Date())
        await MainActor.run {
            items.removeAll { $0.id == h.id }
            items.append(s)
            hist[h.id] = hs
            save()
        }
        return symbol(s)
    }

    /// 앱이 시세를 갱신할 때 (5분에 한 번까지): 추가한 종목의 지금 가격·전일 종가
    func refresh(held: Set<String>) async {
        guard Date().timeIntervalSince(refreshedAt) > 300 else { return }
        refreshedAt = Date()
        for s in items where held.contains(s.id) {
            guard let (q, recent) = try? await Self.chart(s.yahoo, range: "5d") else { continue }
            await MainActor.run {
                guard let i = items.firstIndex(where: { $0.id == s.id }) else { return }
                items[i].prev = q.prev; items[i].last = q.last; items[i].at = Date()
                // 3년 종가 끝에 새 날을 이어 붙인다
                if var h = hist[s.id], let lastDay = h.dates.last {
                    var d = h.dates, c = h.closes
                    for (day, v) in zip(recent.dates, recent.closes) where day > lastDay { d.append(day); c.append(v) }
                    if let ld = recent.dates.last, ld == lastDay, let lv = recent.closes.last { c[c.count - 1] = lv }
                    h = Hist(dates: d, closes: c); hist[s.id] = h
                }
                save()
            }
        }
    }

    /// Yahoo 일별 차트: (지금 가격·전일 종가, 날짜별 종가)
    static func chart(_ yahoo: String, range: String) async throws -> ((last: Double, prev: Double), Hist) {
        let sym = yahoo.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? yahoo
        let o = try await Relay.yahoo("/v8/finance/chart/\(sym)?range=\(range)&interval=1d")
        guard let r = ((o["chart"] as? [String: Any])?["result"] as? [[String: Any]])?.first,
              let meta = r["meta"] as? [String: Any],
              let ts = r["timestamp"] as? [Double],
              let closes = ((((r["indicators"] as? [String: Any])?["quote"] as? [[String: Any]])?.first)?["close"] as? [Any])
        else { throw URLError(.cannotParseResponse) }
        let cur = meta["currency"] as? String ?? ""
        guard cur == "USD" || cur == "KRW" else { throw URLError(.cannotParseResponse) }
        let tz = TimeZone(identifier: (meta["exchangeTimezoneName"] as? String) ?? "America/New_York") ?? .current
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = tz; f.dateFormat = "yyyy-MM-dd"
        var dates: [String] = [], cl: [Double] = []
        for (t, c) in zip(ts, closes) {
            guard let v = c as? Double, v > 0 else { continue }
            dates.append(f.string(from: Date(timeIntervalSince1970: t))); cl.append(v)
        }
        guard let lastClose = cl.last else { throw URLError(.cannotParseResponse) }
        let price = meta["regularMarketPrice"] as? Double ?? lastClose
        let today = f.string(from: Date())
        // 마지막 막대가 오늘이면 그 전날이 전일 종가
        let prevClose = dates.last == today && cl.count > 1 ? cl[cl.count - 2] : lastClose
        if cur == "KRW" {
            // 한국 종목은 전일 종가 기준 (오늘 장중 값은 쓰지 않는다)
            let done = dates.last == today && cl.count > 1 ? Array(cl.dropLast()) : cl
            let dd = dates.last == today && cl.count > 1 ? Array(dates.dropLast()) : dates
            let p = done.last ?? lastClose
            return ((p, p), Hist(dates: dd, closes: done))
        }
        return ((price, prevClose), Hist(dates: dates, closes: cl))
    }
}

/// 가격 흐름: 앱에 넣은 테스트 자료가 먼저, 없으면 추가한 종목의 저장값
enum PriceHistory {
    static func of(_ id: String) -> YahooSample.Hist? {
        if let h = YahooSample.history[id] { return h }
        if let h = CustomSymbols.shared.hist[id], h.closes.count > 1 { return YahooSample.Hist(dates: h.dates, closes: h.closes) }
        return nil
    }
}
