import Foundation

// 사이트(web/widget-core.js)가 만드는 위젯 요약. 금액 배열은 만원 단위 정수, 나머지 금액은 원
struct WSummary: Codable {
    struct Goal: Codable { var amount: Double; var date: String; var start: String; var v0: Double; var m: Double? = nil }   // m = 매달 넣는 돈 (원, 앱 내 길 필요 경로)
    struct Hold: Codable { var t: String; var sh: Double; var ccy: String; var manual: Double? }
    struct Term: Codable { var p5: Double?; var p50: Double?; var p95: Double? }
    struct Forecast: Codable {
        var scen: String
        var md: [String]
        var p5: [Double?]; var p25: [Double?]; var p50: [Double?]; var p75: [Double?]; var p95: [Double?]
        var pg: Double
        var t: Term?
    }
    struct Hit: Codable { var f: String; var h: Int; var r: Int? } // r = 1 사후 계산
    struct Week: Codable { var f: String; var p50: Double; var lo: Double; var hi: Double; var base: Double; var cash: Double; var t0: Double; var tent: Bool? }
    struct Event: Codable { var d: String; var t: String; var k: String }

    var v: Int
    var at: Double          // 계산한 시각 (ms)
    var asof: String        // 마지막 종가 날짜 (계산 기준)
    var today: String
    var cash: Double
    var goal: Goal
    var hold: [Hold]
    var spark: [Double?]
    var fc: Forecast?
    var hits: [Hit]
    var week: Week?
    var prev: Double
    var events: [Event]
}

// data/widget.json (30분마다 갱신되는 작은 시세 파일)
struct WFeed: Codable {
    struct Tail: Codable { var d: [String]; var c: [Double?]; var a: [Double?] }
    struct Mar: Codable { var rate: Double; var date: String? }
    var v: Int
    var u: String?
    var q: [String: [Double?]]      // [현재가, 전일 종가, 시각(초)]
    var c: [String: Tail]
    var ccy: [String: String]
    var mar: Mar?
    var ky: [String: String]? = nil  // 내부 테스트 국내 시세 종목 → Yahoo 기호. 위젯이 직접 받는다
}

// 동기화된 입력값에서 위젯이 바로 쓰는 부분 (전체 입력값은 계산용으로 따로 저장)
struct WLive: Codable {
    struct Hold: Codable { var t: String; var sh: Double; var price: Double? }
    var hold: [Hold]
    var cashKrw: Double
    var cashUsd: Double
    var goalAmount: Double
    var goalDate: String
    var goalStart: String?
    var manualPrice: Bool
    var stateAt: Double
}

func num(_ v: Any?) -> Double? {
    if let d = v as? Double { return d }
    if let i = v as? Int { return Double(i) }
    if let s = v as? String { return Double(s.replacingOccurrences(of: ",", with: "")) }
    if let n = v as? NSNumber { return n.doubleValue }
    return nil
}

// Yahoo 중계 (naeilo Worker). 앱의 종목 검색·추가한 종목 시세와 위젯의 국내 테스트 시세가 같이 쓴다
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

// 내부 테스트 국내 시세 (KoreaEstimate.liveIds). 사이트 quotes.json 에 없어서 위젯도 앱처럼 Yahoo 중계로 직접 받는다
enum KrQuoteFeed {
    static let name = "kr_quotes.json"
    static func cached() -> QuoteFeed.File? { Store.read(QuoteFeed.File.self, name) }

    /// syms: 종목 → Yahoo 기호. 받은 지 minAge 초가 안 됐거나 받을 종목이 같으면 저장해 둔 값
    @discardableResult
    static func fetch(_ syms: [String: String], minAge: TimeInterval = 0) async -> QuoteFeed.File? {
        let old = cached()
        guard !syms.isEmpty else { return old }
        if let o = old, Date().timeIntervalSince1970 - o.got < minAge, Set(o.q.keys) == Set(syms.keys) { return o }
        var q: [String: QuoteFeed.Q] = [:]
        for (id, y) in syms {
            let sym = y.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? y
            guard let o = try? await Relay.yahoo("/v8/finance/chart/\(sym)?range=5d&interval=1d"),
                  let r = ((o["chart"] as? [String: Any])?["result"] as? [[String: Any]])?.first,
                  let meta = r["meta"] as? [String: Any],
                  let raw = ((((r["indicators"] as? [String: Any])?["quote"] as? [[String: Any]])?.first)?["close"] as? [Any])
            else { continue }
            let cl = raw.compactMap { ($0 as? Double).flatMap { $0 > 0 ? $0 : nil } }
            guard let lc = cl.last else { continue }
            // 앱(CustomSymbols.refresh)과 같게: 지금 가격 = 오늘 장중 값, 전일 = 그 전 거래일 종가
            let last = meta["regularMarketPrice"] as? Double ?? lc
            q[id] = .init(last: last, prev: cl.count > 1 ? cl[cl.count - 2] : last, at: meta["regularMarketTime"] as? Double ?? Date().timeIntervalSince1970)
        }
        guard !q.isEmpty else { return old }
        let f = QuoteFeed.File(got: Date().timeIntervalSince1970, q: q)
        Store.write(f, name)
        return f
    }
}

// 토스증권 지금 가격 (개발자 전용). 키를 넣은 PC 의 server.py 를 Tailscale 주소로 부른다.
// 주소는 naeilo://toss?u=https://내PC.ts.net 을 한 번 열면 저장 (u 가 비면 지움). 저장소에 주소를 쓰지 않는다.
// 국내·미국 종목만 오고, 8초를 기다린다. 실패하면 15분 안에 받은 값은 그대로 쓰고, 그보다 오래되면 Yahoo 값을 쓴다.
enum TossFeed {
    static let name = "toss_quotes.json"
    static let key = "toss_base"
    static var base: String? {
        get { Store.defaults.string(forKey: key).flatMap { $0.isEmpty ? nil : $0 } }
        set { Store.defaults.set(newValue, forKey: key) }
    }
    /// 설정 > 개발자용에 보이는 상태: 마지막 성공 시각(초), 마지막 실패 이유 (성공하면 지움)
    static var okAt: Double { Store.defaults.double(forKey: "toss_ok_at") }
    static var lastError: String? { Store.defaults.string(forKey: "toss_err") }
    private static func fail(_ why: String) -> QuoteFeed.File? {
        Store.defaults.set(why, forKey: "toss_err")
        return cached()
    }
    /// naeilo://toss?u=… 로 주소를 넣거나 지운다. ts.net 주소만 받는다
    static func setBase(from url: URL) {
        let u = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "u" }?.value ?? ""
        if let h = URL(string: u)?.host, u.hasPrefix("https://"), h.hasSuffix(".ts.net") {
            base = u.hasSuffix("/") ? String(u.dropLast()) : u
        } else { base = nil; Store.remove(name) }
    }
    /// 15분 안에 받은 값만 (오래된 토스 값보다 Yahoo 값이 낫다)
    static func cached() -> QuoteFeed.File? {
        guard base != nil, let f = Store.read(QuoteFeed.File.self, name), Date().timeIntervalSince1970 - f.got < 900 else { return nil }
        return f
    }

    /// syms: 종목 → Yahoo 기호. prev 는 모르므로 0 (전일 종가는 사이트·Yahoo 값을 쓴다)
    @discardableResult
    static func fetch(_ syms: [String: String], minAge: TimeInterval = 0) async -> QuoteFeed.File? {
        guard let b = base else { return nil }
        let want = syms.filter { !$0.value.contains("^") && !$0.value.contains("=") }
        guard !want.isEmpty else { return nil }
        if let o = cached(), Date().timeIntervalSince1970 - o.got < minAge { return o }
        let list = Set(want.values).sorted().joined(separator: ",")
        guard let enc = list.addingPercentEncoding(withAllowedCharacters: .alphanumerics),
              let url = URL(string: b + "/api/toss/prices?symbols=" + enc) else { return nil }
        // 없는 종목이 섞이면 server.py 가 하나씩 다시 물어 3초를 넘기기도 한다 → 8초
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: req) }
        catch let e as URLError { return fail(e.code == .timedOut ? "8초 안에 응답 없음" : "연결 실패 (\(e.code.rawValue))") }
        catch { return fail("연결 실패") }
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200 else { return fail("서버 응답 \(code)") }
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let qs = o["quotes"] as? [String: Any] else { return fail("응답을 읽지 못함") }
        var q: [String: QuoteFeed.Q] = [:]
        for (id, y) in want {
            guard let v = qs[y] as? [String: Any], let last = num(v["last"]), last > 0 else { continue }
            q[id] = .init(last: last, prev: 0, at: num(v["last_time"]) ?? Date().timeIntervalSince1970)
        }
        guard !q.isEmpty else { return fail((o["error"] as? String).map { "서버: " + $0 } ?? "받은 종목 없음") }
        let f = QuoteFeed.File(got: Date().timeIntervalSince1970, q: q)
        Store.write(f, name)
        Store.defaults.set(f.got, forKey: "toss_ok_at")
        Store.defaults.removeObject(forKey: "toss_err")
        return f
    }
}

// 사이트 시세 (naeilo.com/data/quotes.json). 사이트가 평일 30분마다 모으는 Yahoo 중계 값을 앱과 위젯이 직접 받는다.
// 계약 뒤에는 중계 서버 쪽 출처만 바꾸면 앱·위젯은 그대로 쓴다.
enum QuoteFeed {
    struct Q: Codable { var last: Double; var prev: Double; var at: Double }   // 지금 가격, 전일 종가, 가격 시각(초)
    struct File: Codable { var got: Double; var q: [String: Q] }               // got = 받은 시각(초)

    static let name = "site_quotes.json"
    static func cached() -> File? { Store.read(File.self, name) }

    /// 받은 지 minAge 초가 안 됐으면 저장해 둔 값을 그대로 쓴다. 실패하면 저장해 둔 값
    @discardableResult
    static func fetch(minAge: TimeInterval = 0) async -> File? {
        let old = cached()
        if let o = old, Date().timeIntervalSince1970 - o.got < minAge { return o }
        var c = URLComponents(url: Config.data.appendingPathComponent("quotes.json"), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 60)))]   // 사이트 캐시를 피한다 (1분 단위)
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return old }
        var q: [String: Q] = [:]
        for (k, any) in o {   // 종목이 아닌 칸(예: 갱신 시각)이 생겨도 나머지는 읽는다
            guard let v = any as? [String: Any], let prev = num(v["prev_close"]), let last = num(v["last"]) ?? num(v["regular"]) else { continue }
            q[k] = Q(last: last, prev: prev, at: num(v["last_time"]) ?? num(v["regular_time"]) ?? 0)
        }
        guard !q.isEmpty else { return old }
        let f = File(got: Date().timeIntervalSince1970, q: q)
        Store.write(f, name)
        return f
    }
}
