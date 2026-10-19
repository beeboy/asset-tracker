import Foundation
import Observation

// 종목 찾기·추가 (테스트: 사이트가 쓰는 Yahoo 중계).
// - 찾기: 앱에 넣어 둔 한글 이름 목록(StockCatalog) + Yahoo 검색(영문 이름·티커·종목 코드). 미국 주식·ETF 와 코스피·코스닥만.
// - 추가: 고르는 순간 3년 일별 종가를 받아 기기에 저장하고, 그 뒤로는 앱이 시세를 갱신할 때 같이 갱신한다.
// - 미국 종목은 지금 가격. 한국 종목은 시세를 받지 않고 사용자가 넣은 가격 하나로 계산한다 (KoreaEstimate).

struct StockHit: Identifiable, Hashable {
    let id: String          // 앱 안의 이름: 미국은 티커, 한국은 6자리 코드
    let yahoo: String       // Yahoo 기호 (005930.KS)
    let name: String
    var alias: String = ""
    var en: String = ""
    let market: String
    let currency: Currency
}

/// 한국 종목 (웹 app.js synthKR · data/kr_proxy.json 과 같은 규칙): 한국 가격은 받지 않고 사용자가 넣은 가격 하나에 맞춘다
/// - 미국 지수를 따르는 국내 상장 ETF: 그 미국 ETF(SPY·QQQ 등) × 원/달러 움직임으로 오늘 가격을 추정 (환헤지 (H)면 환율 빼고)
/// - 그 밖의 국내 종목은 종목별로 넣지 않는다. 국내 주식 합계 (시세 기준 화면에서 켠 경우만): 증권사 앱의 합계 금액 그대로,
///   지난 움직임·변동성은 미국 상장 EWY(MSCI Korea) × 원/달러로 대신 (웹 KOREA.KS 줄과 같은 종목 KOREA)
enum KoreaEstimate {
    static let market = "EWY"
    static let exclude = "레버리지|인버스|2X|커버드콜|액티브|Leverage|Inverse|Covered|Active"
    static let hedged = "\\(H\\)|Hedged"
    static let rules: [(match: String, proxy: String, label: String)] = [
        ("S&P\\s?500|에스앤피500", "SPY", "S&P500"),
        ("나스닥\\s?100|NASDAQ\\s?100", "QQQ", "나스닥100"),
        ("배당다우존스|Dividend\\s?(Equity|Dow)", "SCHD", "미국 배당다우존스"),
        ("필라델피아반도체|Philadelphia\\s?Semi|PHLX\\s?Semi", "SOXX", "필라델피아 반도체"),
    ]
    struct Info { let proxy: String?; let hedged: Bool; var base: String { proxy ?? KoreaEstimate.market } }

    static func isKR(_ yahoo: String) -> Bool { let u = yahoo.uppercased(); return u.hasSuffix(".KS") || u.hasSuffix(".KQ") }
    private static func has(_ re: String, _ s: String) -> Bool { s.range(of: re, options: [.regularExpression, .caseInsensitive]) != nil }
    static func info(_ names: [String]) -> Info {
        let t = names.joined(separator: " ")
        guard !has(exclude, t), let r = rules.first(where: { has($0.match, t) }) else { return Info(proxy: nil, hedged: false) }
        return Info(proxy: r.proxy, hedged: has(hedged, t))
    }
    static func info(id: String, name: String, en: String = "") -> Info {
        let ko = StockCatalog.all.first { $0.id == id }
        return info([name, en, ko?.name ?? "", ko?.en ?? ""])
    }
    /// 국내 주식 합계 한 줄 (사이트 KOREA.KS, 수량 1·가격 = 합계 금액)
    static let sumId = "KOREA"
    /// 국내 개별 종목: 앱 한글 목록(StockCatalog)의 국내 주식 (ETF 빼고). 사이트가 매일 지금 가격·종가를 모은다
    /// (data/kr_stocks.json 과 같은 규칙, tools/gen_stock_catalog.py). 토스 시세를 받는 기기는 토스 값이 먼저
    static let popular: Set<String> = Set(StockCatalog.all.filter { $0.currency == .krw && !$0.market.contains("ETF") }.map(\.id))
    /// 그 밖의 국내 종목(미국 지수 국내 ETF 빼고)은 추가하는 순간 Yahoo 중계(asset-ai 워커)로 지금 가격·1년 종가를 받고, 앱이 시세를 갱신할 때 같이 갱신한다.
    /// 중계가 실패하면 넣은 가격 방식
    static func liveOK(_ id: String, name: String = "") -> Bool { id != sumId && (popular.contains(id) || info(id: id, name: name).proxy == nil) }
    static func allowed(_ h: StockHit) -> Bool { h.currency == .usd || liveOK(h.id, name: h.name) || info(id: h.id, name: h.name, en: h.en).proxy != nil }
    static func tag(_ h: StockHit) -> String { h.currency == .usd ? "" : liveOK(h.id, name: h.name) ? " · 국내 시세" : " · 미국 지수로 추정" }
    /// 내 한국 종목이 쓰는 사이트 티커 (SPY·EWY 등)
    static func baseKeys(_ held: Set<String>) -> Set<String> {
        Set(CustomSymbols.shared.items.filter { held.contains($0.id) && !$0.usd }.map { info(id: $0.id, name: $0.name).base })
    }
    /// 넣은 가격(원)과 날짜로 날짜별 가격을 만든다 (웹 synthKR 과 같은 식: 기준 종가 × 원/달러, 넣은 날 값이 넣은 가격이 되게)
    static func synth(price: Double, date: String, info: Info) -> CustomSymbols.Hist? {
        guard price > 0, let P = Market.shared.prices?.h, let base = P[info.base], !base.dates.isEmpty else { return nil }
        let fx = P["KRW=X"]
        var fm: [String: Double] = [:]
        if let fx { for (d, v) in zip(fx.dates, fx.closes) { fm[d] = v } }
        var f = fx?.closes.first ?? 1, dates: [String] = [], raw: [Double] = []
        for (d, v) in zip(base.dates, base.closes) {
            if let x = fm[d] { f = x }
            if info.proxy == nil && d > date { continue }
            dates.append(d); raw.append(v * (info.hedged ? 1 : f))
        }
        guard !dates.isEmpty else { return nil }
        var j = dates.count - 1
        while j > 0 && dates[j] > date { j -= 1 }
        guard raw[j] > 0 else { return nil }
        let k = price / raw[j]
        return .init(dates: dates, closes: raw.map { $0 * k })
    }
    /// 종목 화면 설명
    static func note(_ s: CustomSymbols.Saved) -> String {
        let i = info(id: s.id, name: s.name), at = s.kd ?? ""
        if CustomSymbols.shared.isLive(s.id) { return KoreaEstimate.popular.contains(s.id) ? "사이트가 매일 모으는 가격이에요. 1년 기록은 모으기 시작한 날부터 쌓여요." : "시세를 갱신할 때 같이 갱신해요." }
        guard let p = s.kp else { return "한국 시세는 받지 않아요. 지금 가격을 한 번 넣으면 계산해요." }
        let won = Int(p.rounded()).formatted() + "원"
        if s.id == sumId { return "\(at)에 넣은 \(won) · 전망은 EWY×원/달러" }
        return i.proxy != nil ? "\(at)에 넣은 \(won)에서 \(i.base)\(i.hedged ? "" : " × 원/달러") 움직임으로 추정한 값이에요."
            : "\(at)에 넣은 \(won) 그대로예요. 움직임 예측은 한국 시장 평균(\(i.base) × 원/달러)으로 해요."
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
            // 코스피·코스닥 전체 상장 종목 (이름이 찾는 말로 시작하면 먼저, 너무 많으면 앞의 40개만)
            let hits = KrxList.all.filter { $0.key.contains(k) }
            for h in (hits.filter { $0.key.hasPrefix(k) || $0.hit.id.hasPrefix(k) } + hits).prefix(40) { add(h.hit) }
        }
        let mine = Set(CustomSymbols.shared.items.map(\.id))
        return out.filter { mine.contains($0.id) || KoreaEstimate.allowed($0) }
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
                let ko = StockCatalog.all.first { $0.id == code }?.name ?? KrxList.name(code)
                return StockHit(id: code, yahoo: sym, name: ko ?? name, en: name,
                                market: (sym.hasSuffix(".KS") ? "코스피" : "코스닥") + (type == "ETF" ? " ETF" : ""), currency: .krw)
            }
            guard usEx.contains(ex), !sym.contains(".") else { return nil }
            let ko = StockCatalog.all.first { $0.id == sym }
            return StockHit(id: sym, yahoo: sym, name: ko?.name ?? name, en: name, market: type == "ETF" ? "미국 ETF" : "미국 주식", currency: .usd)
        }.filter(KoreaEstimate.allowed)
    }
}

/// 코스피·코스닥 전체 상장 종목 (Resources/krx_stocks.json, tools/gen_krx_list.py 가 KRX KIND 목록으로 만든다).
/// 한글 이름으로 찾기용. [[코드, 이름, "K"|"Q"]], K = 코스피(.KS), Q = 코스닥(.KQ)
enum KrxList {
    struct Row { let key: String; let hit: StockHit }
    static let all: [Row] = {
        guard let url = Bundle.main.url(forResource: "krx_stocks", withExtension: "json"),
              let d = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([[String]].self, from: d) else { return [] }
        return rows.compactMap { r in
            guard r.count >= 3 else { return nil }
            let ks = r[2] == "K"
            let h = StockHit(id: r[0], yahoo: r[0] + (ks ? ".KS" : ".KQ"), name: r[1], market: ks ? "코스피" : "코스닥", currency: .krw)
            return Row(key: StockSearch.norm(r[0] + r[1]), hit: h)
        }
    }()
    private static let byId: [String: StockHit] = Dictionary(all.map { ($0.hit.id, $0.hit) }, uniquingKeysWith: { a, _ in a })
    static func name(_ code: String) -> String? { byId[code]?.name }
    static func hit(_ code: String) -> StockHit? { byId[code] }
}

/// 사이트가 매일 모으는 국내 개별 종목 종가 (data/kr_daily.json, server.py collect_kr). 모으기 시작한 날부터 하루 한 칸, 최근 1년
enum KrDaily {
    struct Row: Codable { let d: [String]; let c: [Double] }
    struct File: Codable { var got: Double = 0; var s: [String: Row] }
    static let name = "kr_daily.json"
    /// 이만큼(약 한 달) 쌓이기 전에는 "기록 모으는 중"
    static let enough = 20
    static func cached() -> File? { Store.read(File.self, name) }

    /// 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값
    static func fetch(minAge: TimeInterval = 0) async -> File? {
        let old = cached()
        if let o = old, Date().timeIntervalSince1970 - o.got < minAge { return o }
        var c = URLComponents(url: Config.data.appendingPathComponent(name), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 60)))]
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              var f = try? JSONDecoder().decode(File.self, from: data) else { return old }
        f.got = Date().timeIntervalSince1970
        Store.write(f, name)
        return f
    }
}

/// 사용자가 추가한 종목 (예시 목록 밖). 이름·시세·3년 종가를 기기에 저장한다
@Observable
final class CustomSymbols {
    static let shared = CustomSymbols()

    struct Saved: Codable, Hashable {
        let id: String, yahoo: String, name: String, market: String, usd: Bool
        var prev: Double, last: Double, at: Date
        var kp: Double? = nil, kd: String? = nil     // 한국 종목: 사용자가 넣은 가격(원)과 그날
        var lv: Bool? = nil                          // 한국 종목: 사이트가 모으는 국내 개별 종목 (KoreaEstimate.live)
    }
    struct Hist: Codable { let dates: [String]; let closes: [Double] }

    private(set) var items: [Saved] = []
    private(set) var hist: [String: Hist] = [:]
    /// 국내 주식 합계 날짜별 금액 (사이트 kr_log 와 같은 칸, MC.krNorm). 지난 기록은 현금처럼: 첫 기록 전은 0. 전망은 KR~EWY 움직임으로
    private(set) var krLog: [MC.KrDay] = (UserDefaults.standard.data(forKey: "krLog").flatMap { try? JSONDecoder().decode([MC.KrDay].self, from: $0) }) ?? [] {
        didSet { if let d = try? JSONEncoder().encode(krLog) { UserDefaults.standard.set(d, forKey: "krLog") } }
    }
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
            // 한국 종목은 시세를 받지 않는다: 예전에 Yahoo 에서 받아 둔 종가는 버리고, 넣은 가격으로 다시 만든다 (rebuildKR)
            for s in items where !s.usd && !(s.lv == true && KoreaEstimate.liveOK(s.id, name: s.name)) { hist[s.id] = nil }
        }
        // 기록 없이 금액 하나만 있던 예전 저장값은 그 금액·날짜를 첫 줄로
        if krLog.isEmpty, let s = items.first(where: { $0.id == KoreaEstimate.sumId }), let p = s.kp, p > 0 { krLog = [.init(d: s.kd ?? Day.today, v: p)] }
    }
    /// 내 폰에 저장한 파일에서 불러오기: 같은 종목은 파일 값으로
    func restore(_ new: [Saved], _ h: [String: Hist]) {
        items = items.filter { s in !new.contains { $0.id == s.id } } + new
        hist.merge(h) { _, b in b }
        save()
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
        if let c = KrxList.hit(s.id) { return c.yahoo }
        return s.currency == .krw ? s.id + ".KS" : s.id
    }

    /// 3년 종가를 받아 저장 (추가할 때). 실패하면 이유를 던진다
    @discardableResult
    func fetch(_ h: StockHit) async throws -> Symbol {
        if h.currency == .krw && KoreaEstimate.liveOK(h.id, name: h.name), let (q, hs) = try? await Self.chart(h.yahoo, range: "1y", today: true) {
            // 국내 종목: 추가하는 순간 Yahoo 에서 직접 지금 가격·1년 종가 (안 되면 중계)
            let pc = hs.closes.count > 1 ? hs.closes[hs.closes.count - 2] : q.prev
            var s = Saved(id: h.id, yahoo: h.yahoo, name: h.name, market: h.market, usd: false, prev: pc, last: q.last, at: Date())
            s.kp = q.last; s.kd = hs.dates.last; s.lv = true
            await MainActor.run {
                items.removeAll { $0.id == h.id }
                items.append(s)
                hist[h.id] = hs
                save()
            }
            return symbol(s)
        }
        if h.currency == .krw && KoreaEstimate.popular.contains(h.id) {
            // 국내 개별 종목인데 Yahoo 를 못 받았을 때만: 사이트가 모은 지금 가격(quotes.json)과 날짜별 종가(kr_daily.json)
            let k = await KrDaily.fetch(minAge: 1800)
            return await MainActor.run {
                var s = Saved(id: h.id, yahoo: h.yahoo, name: h.name, market: h.market, usd: false, prev: 0, last: 0, at: .distantPast)
                s.lv = true
                items.removeAll { $0.id == h.id }
                items.append(s)
                applyKr(k, only: [h.id])
                save()
                return symbol(items.first { $0.id == h.id } ?? s)
            }
        }
        if h.currency == .krw {
            // 한국 종목 (중계 실패 또는 미국 지수 국내 ETF): 받지 않는다. 넣은 가격이 있으면 그 값으로 계산
            return await MainActor.run {
                let old = items.first { $0.id == h.id }
                var s = Saved(id: h.id, yahoo: h.yahoo, name: h.name, market: h.market, usd: false, prev: 0, last: 0, at: Date())
                s.kp = old?.kp; s.kd = old?.kd
                items.removeAll { $0.id == h.id }
                items.append(s)
                rebuildKR(); save()
                return symbol(items.first { $0.id == h.id } ?? s)
            }
        }
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
    func refresh(held: Set<String>, force: Bool = false) async {
        guard force || Date().timeIntervalSince(refreshedAt) > 300 else { return }
        refreshedAt = Date()
        for s in items where held.contains(s.id) && !s.usd && KoreaEstimate.liveOK(s.id, name: s.name)
            && (s.lv != true || (KoreaEstimate.popular.contains(s.id) && (hist[s.id]?.dates.count ?? 0) < 200 && !Market.shared.tossCovers(s.id))) {
            // 스위치를 켜기 전에 넣은 국내 종목, 사이트 기록(kr_daily)만 있던 국내 개별 종목: 1년 종가를 Yahoo 에서 처음 받는다
            _ = try? await fetch(StockHit(id: s.id, yahoo: s.yahoo, name: s.name, market: s.market, currency: .krw))
        }
        var missKr: Set<String> = []
        // 토스가 지금 가격·일봉을 다 주는 종목은 Yahoo 를 부르지 않는다 (토스가 안 될 때만 Yahoo)
        for s in items where held.contains(s.id) && (s.usd || isLive(s.id)) && !Market.shared.tossCovers(s.id) {
            guard let (q, recent) = try? await Self.chart(s.yahoo, range: "5d", today: !s.usd) else {
                if !s.usd && KoreaEstimate.popular.contains(s.id) { missKr.insert(s.id) }
                continue
            }
            await MainActor.run {
                guard let i = items.firstIndex(where: { $0.id == s.id }) else { return }
                items[i].prev = q.prev; items[i].last = q.last; items[i].at = Date()
                if !s.usd { items[i].kp = q.last; items[i].kd = recent.dates.last ?? items[i].kd }
                // 3년 종가 끝에 새 날을 이어 붙인다
                if var h = hist[s.id], let lastDay = h.dates.last {
                    var d = h.dates, c = h.closes
                    for (day, v) in zip(recent.dates, recent.closes) where day > lastDay { d.append(day); c.append(v) }
                    if let ld = recent.dates.last, ld == lastDay, let lv = recent.closes.last { c[c.count - 1] = lv }
                    h = Hist(dates: d, closes: c); hist[s.id] = h
                }
                if !s.usd, let c = hist[s.id]?.closes, c.count > 1 { items[i].prev = c[c.count - 2] }
            }
        }
        // Yahoo 를 못 받은 국내 개별 종목만 사이트 기록(kr_daily.json)으로
        if !missKr.isEmpty {
            let k = await KrDaily.fetch(minAge: force ? 0 : 1800), miss = missKr
            await MainActor.run { applyKr(k, only: miss) }
        }
        await MainActor.run { rebuildKR(); save() }   // 종목마다 전체를 다시 쓰지 않고 끝에 한 번만 저장
    }

    /// 한국 종목 가격 넣기 (date 없으면 오늘)
    @MainActor
    func setPrice(_ id: String, _ price: Double, date: String? = nil) {
        guard price > 0, let i = items.firstIndex(where: { $0.id == id }), !isLive(id) else { return }
        if id == KoreaEstimate.sumId { setKoreaSum(price, date: date ?? Day.today); return }
        items[i].kp = price; items[i].kd = date ?? Day.today
        rebuildKR(); save()
    }
    func saved(_ id: String) -> Saved? { items.first { $0.id == id } }
    /// 국내 개별 종목에 사이트 값을 넣는다: 지금 가격·전일 종가는 quotes.json, 날짜별 종가는 kr_daily.json
    /// (at 은 그대로 두어 Market 이 늘 사이트 시세를 먼저 쓴다)
    @MainActor
    private func applyKr(_ k: KrDaily.File?, only: Set<String>? = nil) {
        for i in items.indices where KoreaEstimate.popular.contains(items[i].id) && isLive(items[i].id) && only?.contains(items[i].id) != false {
            let s = items[i]
            if let r = k?.s[s.id], !r.d.isEmpty { hist[s.id] = Hist(dates: r.d, closes: r.c); items[i].kd = r.d.last }
            if let q = Market.shared.site?.q[s.yahoo.uppercased()], q.last > 0 {
                items[i].last = q.last; items[i].prev = q.prev > 0 ? q.prev : q.last; items[i].kp = q.last
            } else if let c = hist[s.id]?.closes, let l = c.last {
                items[i].last = l; items[i].prev = c.count > 1 ? c[c.count - 2] : l; items[i].kp = l
            }
        }
    }
    /// 1년 기록을 모으는 중인 국내 종목이면 쌓인 날 수 (차트·1년 범위·출렁임 대신 "기록 모으는 중"). 충분하면 nil
    func collectingDays(_ id: String) -> Int? {
        guard KoreaEstimate.popular.contains(id), isLive(id) else { return nil }
        let n = hist[id]?.dates.count ?? 0
        return n < KrDaily.enough ? n : nil
    }
    /// 테스트 시세를 받는 국내 종목인가 (스위치를 끄면 다시 넣은 가격 방식)
    func isLive(_ id: String) -> Bool { guard let s = saved(id) else { return false }; return s.lv == true && KoreaEstimate.liveOK(id, name: s.name) }
    /// 한국 종목에 넣은 가격이 바뀌면 바뀌는 값 (기기 동기화가 이것도 보낸다)
    var krStamp: String { items.filter { !$0.usd }.map { "\($0.id)=\($0.kp ?? 0)@\($0.kd ?? "")" }.joined(separator: ",") + "|kr\(krLog.count)=\(krLog.last?.v ?? 0)@\(krLog.last?.d ?? "")" }
    /// 국내 주식 합계 금액 넣기 (날짜별 기록에 남긴다. 0 = 다 팔았어요, 지난 기록은 그대로)
    @MainActor
    func setKoreaSum(_ won: Double, date: String = Day.today) {
        let id = KoreaEstimate.sumId
        krLog = MC.krNorm(krLog + [.init(d: date, v: max(0, won))])
        if won > 0 && !items.contains(where: { $0.id == id }) {
            items.append(Saved(id: id, yahoo: id + ".KS", name: "국내 주식 합계", market: "국내 주식", usd: false, prev: 0, last: 0, at: Date()))
        }
        if let i = items.firstIndex(where: { $0.id == id }), let l = krLog.last { items[i].kp = l.v > 0 ? l.v : items[i].kp; items[i].kd = l.d }
        rebuildKR(); save()
    }
    /// 동기화: 날짜별로 합친다 (같은 날은 받은 쪽)
    @MainActor
    func mergeKr(_ remote: [MC.KrDay]) {
        guard !remote.isEmpty else { return }
        let L = MC.krNorm(krLog + remote)
        guard L != krLog else { return }
        krLog = L
        if let i = items.firstIndex(where: { $0.id == KoreaEstimate.sumId }), let l = L.last, l.v > 0 { items[i].kp = l.v; items[i].kd = l.d }
        rebuildKR(); save()
    }
    /// 지금 국내 주식 합계 (기록의 오늘 값)
    var krNow: Double { MC.krAt(krLog, Day.today) }
    /// 한국 종목의 날짜별 가격·지금 값을 넣은 가격과 사이트 미국 종가로 다시 만든다
    func rebuildKR() {
        for i in items.indices where !items[i].usd {
            let s = items[i]
            if s.lv == true && KoreaEstimate.liveOK(s.id, name: s.name) { continue }   // 테스트 시세: 받은 종가 그대로
            if s.id == KoreaEstimate.sumId {
                // 국내 주식 합계: 첫 기록 날부터 원/달러 거래일마다 그날까지 마지막 금액 (웹 krSynth 와 같다). 지금 값은 기록의 오늘 값
                let L = krLog, fx = Market.shared.prices?.h["KRW=X"]?.dates ?? []
                if let d0 = L.first?.d {
                    let ds = fx.isEmpty ? L.map(\.d) : fx.filter { $0 >= d0 }
                    hist[s.id] = ds.isEmpty ? nil : Hist(dates: ds, closes: ds.map { MC.krAt(L, $0) })
                } else { hist[s.id] = nil }
                items[i].last = L.isEmpty ? (s.kp ?? 0) : MC.krAt(L, Day.today); items[i].prev = items[i].last
                continue
            }
            if let p = s.kp, let h = KoreaEstimate.synth(price: p, date: s.kd ?? Day.today, info: KoreaEstimate.info(id: s.id, name: s.name)) {
                hist[s.id] = h; items[i].last = h.closes.last ?? p; items[i].prev = items[i].last   // 전일 대비는 없음 (웹과 같다)
            } else {
                hist[s.id] = nil; items[i].last = s.kp ?? 0; items[i].prev = items[i].last
            }
        }
    }

    /// Yahoo 일별 차트: (지금 가격·전일 종가, 날짜별 종가)
    /// today: 한국 종목도 오늘 장중 값(Yahoo 지연 시세)을 쓴다 (내부 테스트 시세)
    static func chart(_ yahoo: String, range: String, today keepToday: Bool = false) async throws -> ((last: Double, prev: Double), Hist) {
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
        if cur == "KRW" && !keepToday {
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
    /// 지난 종가: 사이트 파일(웹과 같은 자료). 이 기기가 직접 받은 값은 사이트보다 늦은 날만 덧붙인다.
    /// 둘 다 없으면 빌드 때 넣은 파일. 가상 종목 DRNK 는 샘플 흐름을 따로 쓴다
    static func of(_ id: String) -> YahooSample.Hist? {
        let key = SiteStats.key(id)
        let site = id == "DRNK" ? nil : Market.shared.prices?.h[key]
        // 국내 주식 합계는 넣은 기록만 (하루치여도. 첫 기록 전은 없음 = 0)
        if id == KoreaEstimate.sumId { return CustomSymbols.shared.hist[id].map { YahooSample.Hist(dates: $0.dates, closes: $0.closes) } }
        let mine = CustomSymbols.shared.hist[id].flatMap { $0.closes.count > 1 ? YahooSample.Hist(dates: $0.dates, closes: $0.closes) : nil }
        // 둘 다 있으면 사이트 값을 쓰고, 이 기기가 받은 값은 사이트 마지막 날 뒤의 날만 잇는다
        // (겹치는 날을 기기 값으로 쓰면 수정주가·출처 차이로 웹과 지난 종가가 갈린다: 내 길 시작값 차이)
        if let s = site, let c = mine {
            let sl = s.dates.last ?? ""
            guard let i = c.dates.firstIndex(where: { $0 > sl }) else { return s }
            return YahooSample.Hist(dates: s.dates + c.dates[i...], closes: s.closes + c.closes[i...])
        }
        if let h = site ?? mine { return h }
        // 비중 바꿔 비교에서 담은 종목 (모의 바구니)
        if let e = ExtraStats.shared.items[id]?.hist { return e }
        return YahooSample.history[id] ?? (id == "DRNK" ? nil : YahooSample.bundled[key])
    }
}
