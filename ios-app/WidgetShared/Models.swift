import Foundation
import Security

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
    var ty: [String: String]? = nil  // 토스로 받을 내 종목 → Yahoo 기호. 내 키가 있으면 위젯도 토스 지금 가격을 직접 받는다
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

// 국내 개별 종목 시세 (KoreaEstimate.popular). 사이트 quotes.json 에 없어서 위젯도 앱처럼 Yahoo 중계로 직접 받는다
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

// 토스증권 지금 가격. 두 길이 있다 (개발자 주소가 있으면 그쪽이 먼저):
// - 개발자: 키를 넣은 PC 의 server.py 를 Tailscale 주소로 부른다.
//   주소는 naeilo://toss?u=https://내PC.ts.net 을 한 번 열면 저장 (u 가 비면 지움). 저장소에 주소를 쓰지 않는다.
// - 일반 사용자: 자기 토스 키(TossKeys, 이 폰 키체인)로 공용 중계(Config.tossRelay)를 거쳐 토스를 직접 부른다.
//   중계는 키·토큰을 저장하지 않고 넘기기만 한다. 사용자는 중계 IP 를 토스 허용 IP 로 등록해야 한다.
// 국내·미국 종목만 오고, 3초를 기다린다. 실패하면 15분 안에 받은 값은 그대로 쓰고, 그보다 오래되면 Yahoo 값을 쓴다.
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
        guard base != nil || TossKeys.on, let f = Store.read(QuoteFeed.File.self, name), Date().timeIntervalSince1970 - f.got < 900 else { return nil }
        return f
    }

    /// syms: 종목 → Yahoo 기호. prev 는 모르므로 0 (전일 종가는 사이트·Yahoo 값을 쓴다)
    /// force: 다시 받기·당겨 갱신. 하루 빼 둔 종목도 다시 묻는다
    @discardableResult
    static func fetch(_ syms: [String: String], minAge: TimeInterval = 0, force: Bool = false) async -> QuoteFeed.File? {
        let want = syms.filter { !$0.value.contains("^") && !$0.value.contains("=") }
        guard !want.isEmpty else { return nil }
        if let o = cached(), Date().timeIntervalSince1970 - o.got < minAge { return o }
        guard let b = base else { return TossKeys.on ? await fetchUser(want, force: force) : nil }
        let list = Set(want.values).sorted().joined(separator: ",")
        guard let enc = list.addingPercentEncoding(withAllowedCharacters: .alphanumerics),
              let url = URL(string: b + "/api/toss/prices?symbols=" + enc) else { return nil }
        // 3초 넘으면 포기 (웹 tossQuotes 와 같다). 없는 종목은 server.py 가 하루 제외하므로 다음 번엔 빨라진다
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 3)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: req) }
        catch let e as URLError { return fail(e.code == .timedOut ? "3초 안에 응답 없음" : "연결 실패 (\(e.code.rawValue))") }
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
        return save(q)
    }

    private static func save(_ q: [String: QuoteFeed.Q]) -> QuoteFeed.File {
        let f = QuoteFeed.File(got: Date().timeIntervalSince1970, q: q)
        Store.write(f, name)
        Store.defaults.set(f.got, forKey: "toss_ok_at")
        Store.defaults.removeObject(forKey: "toss_err")
        return f
    }

    /// Yahoo 기호 → 토스 종목 코드 (server.py toss_code 와 같다). 토스에 없는 시장은 nil
    /// 국내 005930.KS·0101N0.KQ → 6자리, 미국 AAPL → AAPL, BRK-B → BRK.B
    static func code(_ sym: String) -> String? {
        let u = sym.trimmingCharacters(in: .whitespaces).uppercased()
        if u.hasSuffix(".KS") || u.hasSuffix(".KQ") {
            let c = String(u.dropLast(3))
            return c.count == 6 && c.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber) }) ? c : nil
        }
        // ^GSPC·KRW=X·GC=F·BTC-USD·7203.T 는 여기서 걸러진다
        let p = u.split(separator: "-", omittingEmptySubsequences: false)
        let az = { (x: Substring) in x.allSatisfy { $0 >= "A" && $0 <= "Z" } }
        guard (1...2).contains(p.count), (1...5).contains(p[0].count), az(p[0]) else { return nil }
        if p.count == 1 { return String(p[0]) }
        return p[1].count == 1 && az(p[1]) ? String(p[0]) + "." + String(p[1]) : nil
    }

    // MARK: 일반 사용자 (내 키 + 공용 중계)

    private static func relayCall(_ r: URLRequest) async -> (Int, [String: Any]?, String?) {
        do {
            let (d, resp) = try await URLSession.shared.data(for: r)
            return ((resp as? HTTPURLResponse)?.statusCode ?? 0, try? JSONSerialization.jsonObject(with: d) as? [String: Any], nil)
        } catch let e as URLError { return (0, nil, e.code == .timedOut ? "3초 안에 응답 없음" : "중계에 닿지 못함") }
        catch { return (0, nil, "중계에 닿지 못함") }
    }

    /// 토스 오류를 쉬운 말로 (허용 IP 미등록이 가장 흔하다)
    static func why(_ code: Int, _ o: [String: Any]?) -> String {
        let c = ((o?["error"] as? [String: Any])?["code"] as? String) ?? (o?["error"] as? String) ?? ""
        if code == 403 || c == "access_denied" { return "허용 IP에 중계 IP가 없어요" }
        if c == "invalid_client" { return "키가 맞지 않아요" }
        if code == 429 { return "잠시 뒤에 다시 받아요" }
        return "오픈 API 응답 \(code)" + (c.isEmpty ? "" : " (\(c))")
    }

    /// 토큰: 키체인에 둔 것을 만료 1분 전까지 쓴다. 토스는 키당 토큰 1개라 새로 받으면 예전 것은 끊긴다
    static func token(renew: Bool = false) async -> (String?, String?) {
        if !renew, let t = TossKeys.token, t.exp - 60 > Date().timeIntervalSince1970 { return (t.value, nil) }
        guard let k = TossKeys.load(), let url = URL(string: Config.tossRelay + "/api/toss/token") else { return (nil, "키 없음") }
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 5)
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let ok = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        let enc = { (v: String) in v.addingPercentEncoding(withAllowedCharacters: ok) ?? "" }
        r.httpBody = "client_id=\(enc(k.id))&client_secret=\(enc(k.secret))".data(using: .utf8)
        let (code, o, e) = await relayCall(r)
        if let e { return (nil, e) }
        guard code == 200, let o, let t = o["access_token"] as? String else { return (nil, why(code, o)) }
        TossKeys.token = .init(value: t, exp: Date().timeIntervalSince1970 + (num(o["expires_in"]) ?? 3600))
        return (t, nil)
    }

    private static func prices(_ codes: [String], _ tok: String) async -> (Int, [String: Any]?, String?) {
        guard let url = URL(string: Config.tossRelay + "/api/toss/v1/prices?symbols=" + codes.joined(separator: ",")) else { return (0, nil, "잘못된 종목") }
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 3)
        r.setValue("Bearer " + tok, forHTTPHeaderField: "Authorization")
        return await relayCall(r)
    }

    /// 내 키로 중계 GET (일봉·환율). 토큰이 끊겼으면(401) 한 번만 새로 받는다. path 예: "/api/toss/v1/candles?symbol=AAPL&…"
    static func userGet(_ path: String, timeout: TimeInterval = 5) async -> (Int, [String: Any]?, String?) {
        guard TossKeys.on, let url = URL(string: Config.tossRelay + path) else { return (0, nil, "키 없음") }
        var (tok, e) = await token()
        for renew in [false, true] {
            if renew { (tok, e) = await token(renew: true) }
            guard let t = tok else { return (0, nil, e ?? "토큰 실패") }
            var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeout)
            r.setValue("Bearer " + t, forHTTPHeaderField: "Authorization")
            let out = await relayCall(r)
            if out.0 != 401 { return out }
        }
        return (401, nil, "토큰 실패")
    }

    /// 토스에 없는 종목 코드 → 다시 물어볼 시각 (하루 동안 안 묻는다, server.py 와 같다). 현재가에서 404 난 것만
    static var miss: [String: Double] {
        get { Store.defaults.dictionary(forKey: "toss_miss") as? [String: Double] ?? [:] }
        set { Store.defaults.set(newValue, forKey: "toss_miss") }
    }
    /// 일봉에서 404 난 종목 (현재가 목록과 따로 둔다. 같이 쓰면 일봉 404 하나로 그 종목 현재가도 하루 못 받는다)
    static var histMiss: [String: Double] {
        get { Store.defaults.dictionary(forKey: "toss_hist_miss") as? [String: Double] ?? [:] }
        set { Store.defaults.set(newValue, forKey: "toss_hist_miss") }
    }

    /// 위젯: 앱이 feed.json 에 넘긴 내 종목을 위젯이 직접 토스에 묻는다 (앱을 안 열어도 위젯이 토스 값)
    @discardableResult
    static func widgetFetch(minAge: TimeInterval) async -> QuoteFeed.File? {
        guard base != nil || TossKeys.on, let ty = Store.read(WFeed.self, "feed.json")?.ty, !ty.isEmpty else { return nil }
        return await fetch(ty, minAge: minAge)
    }

    private static func fetchUser(_ want: [String: String], force: Bool) async -> QuoteFeed.File? {
        let now = Date().timeIntervalSince1970
        var ids: [String: [String]] = [:]   // 토스 코드 → 앱 종목들
        var skip: [String: Double] = force ? [:] : miss     // 다시 받기는 빼 둔 종목도 다시 묻는다 (없으면 아래에서 다시 뺀다)
        // 내 종목이 모두 빠져 있으면 그래도 묻는다 (한 번 404 가 나면 다시 받기 전까지 하루 내내 실패로 남던 일)
        if !skip.isEmpty, want.values.compactMap(code).allSatisfy({ (skip[$0] ?? 0) >= now }) { skip = [:] }
        for (id, y) in want { if let c = code(y), (skip[c] ?? 0) < now { ids[c, default: []].append(id) } }
        guard !ids.isEmpty else { return fail("오픈 API로 받을 종목 없음") }
        var (tok, e) = await token()
        guard var t = tok else { return fail(e ?? "토큰 실패") }
        var q: [String: QuoteFeed.Q] = [:]
        var got: Set<String> = []   // 받은 토스 코드 (빼 둔 목록에서 지운다)
        func take(_ o: [String: Any]?) {
            for v in o?["result"] as? [[String: Any]] ?? [] {
                guard let c = (v["symbol"] as? String)?.uppercased(), let last = num(v["lastPrice"]), last > 0 else { continue }
                got.insert(c)
                let at = (v["timestamp"] as? String).flatMap { try? Date($0, strategy: .iso8601) }?.timeIntervalSince1970 ?? now
                for id in ids[c] ?? [] { q[id] = .init(last: last, prev: 0, at: at) }
            }
        }
        let all = ids.keys.sorted()
        for i in stride(from: 0, to: all.count, by: 200) {   // 한 번에 200개까지
            let part = Array(all[i..<min(i + 200, all.count)])
            var (code, o, err) = await prices(part, t)
            if code == 401 {   // 만료·다른 곳에서 새로 받아 끊김 → 한 번만 다시 받는다
                (tok, e) = await token(renew: true)
                guard let n = tok else { return fail(e ?? "토큰 실패") }
                t = n
                (code, o, err) = await prices(part, t)
            }
            if let err { return fail(err) }
            if code == 404 && part.count > 1 {
                // 묶음 안에 토스에 없는 종목이 있으면 하나씩 동시에 묻고, 없는 것은 하루 뺀다
                var m = miss
                let tk = t
                await withTaskGroup(of: (String, Int, [String: Any]?).self) { g in
                    for c in part.prefix(15) { g.addTask { let (k, oo, _) = await prices([c], tk); return (c, k, oo) } }
                    for await (c, k, oo) in g { if k == 404 { m[c] = now + 86400 } else if k == 200 { take(oo) } }
                }
                miss = m
                continue
            }
            if code == 404 { var m = miss; m[part[0]] = now + 86400; miss = m; continue }
            guard code == 200 else { return fail(why(code, o)) }
            take(o)
        }
        if !got.isEmpty, !Set(miss.keys).isDisjoint(with: got) { var m = miss; for c in got { m[c] = nil }; miss = m }
        guard !q.isEmpty else { return fail("받은 종목 없음") }
        return save(q)
    }
}

/// 사용자가 넣은 토스증권 키와 토큰. 이 폰 키체인에만 둔다 (백업·동기화·서버에 안 들어감)
enum TossKeys {
    struct Keys: Codable { var id: String; var secret: String }
    struct Token: Codable { var value: String; var exp: Double }
    private static func q(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.toss", kSecAttrAccount as String: account]
    }
    /// 앱 그룹을 키체인 그룹으로 써서 위젯도 같은 키·토큰을 읽는다 (토스는 키당 토큰 1개라 위젯이 따로 받으면 앱 토큰이 끊긴다)
    static let group = "group.com.naeilo.widget"
    private static func put<T: Encodable>(_ v: T?, _ account: String) {
        SecItemDelete(q(account) as CFDictionary)   // 예전 앱 전용 자리와 그룹 자리를 같이 지운다
        guard let v, let d = try? JSONEncoder().encode(v) else { return }
        var a = q(account)
        a[kSecAttrAccessGroup as String] = group
        a[kSecValueData as String] = d
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(a as CFDictionary, nil)
    }
    private static func get<T: Decodable>(_ t: T.Type, _ account: String) -> T? {
        var a = q(account)
        a[kSecReturnData as String] = true
        a[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(a as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return try? JSONDecoder().decode(T.self, from: d)
    }
    /// 키를 넣었는지 (위젯도 보므로 키 자체가 아니라 표시만 공유 저장소에 둔다)
    static var on: Bool { Store.defaults.bool(forKey: "toss_user") }
    static func load() -> Keys? { get(Keys.self, "keys") }
    /// 예전 판은 키를 앱 전용 자리에 두어 위젯이 못 읽었다. 앱이 열릴 때 한 번 그룹 자리로 옮긴다
    static func share() {
        guard on, !Store.defaults.bool(forKey: "toss_kc_group") else { return }
        if let k = load() { let t = token; put(k, "keys"); put(t, "token") }
        Store.defaults.set(true, forKey: "toss_kc_group")
    }
    static func save(_ k: Keys) {
        put(k, "keys"); put(Token?.none, "token")
        Store.defaults.set(true, forKey: "toss_user")
        Store.defaults.removeObject(forKey: "toss_miss")
        Store.defaults.removeObject(forKey: "toss_hist_miss")
    }
    static func clear() {
        put(Keys?.none, "keys"); put(Token?.none, "token")
        Store.defaults.set(false, forKey: "toss_user")
        Store.defaults.removeObject(forKey: "toss_err")
        Store.defaults.removeObject(forKey: "toss_ok_at")
        Store.remove(TossFeed.name)
        Store.remove("toss_history.json")   // 내 키로 받은 지난 종가·환율 (앱 TossHistory)
    }
    static var token: Token? {
        get { get(Token.self, "token") }
        set { put(newValue, "token") }
    }
    /// 사용자가 토스에 등록할 중계 IP (중계가 알려 준다)
    static func relayIP() async -> String? {
        guard let url = URL(string: Config.tossRelay + "/api/toss/ip"),
              let (d, _) = try? await URLSession.shared.data(for: URLRequest(url: url, timeoutInterval: 5)),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any], let ip = o["ip"] as? String, !ip.isEmpty else { return nil }
        return ip
    }
}

// Alpaca 지금 가격 (미국 종목만). 사용자가 app.alpaca.markets 모의 계정에서 무료로 받은 키로 Alpaca 를 직접 부른다 (중계 없음).
// 무료 키는 IEX 거래소 실시간. 우선순위: 토스 키 > Alpaca 키 > 사이트 시세. 한국 종목·원/달러는 그대로.
// 전일 종가는 사이트(공식 종가) 값을 먼저 쓰고, 없을 때만 Alpaca prevDailyBar(IEX 종가)를 쓴다.
enum AlpacaFeed {
    static let name = "alpaca_quotes.json"
    static let base = "https://data.alpaca.markets/v2/stocks/snapshots"
    /// 설정 > 내 Alpaca 키에 보이는 상태: 마지막 성공 시각(초), 마지막 실패 이유 (성공하면 지움)
    static var okAt: Double { Store.defaults.double(forKey: "alpaca_ok_at") }
    static var lastError: String? { Store.defaults.string(forKey: "alpaca_err") }
    private static func fail(_ why: String) -> QuoteFeed.File? {
        Store.defaults.set(why, forKey: "alpaca_err")
        return cached()
    }
    /// 15분 안에 받은 값만 (토스와 같다)
    static func cached() -> QuoteFeed.File? {
        guard AlpacaKeys.on, let f = Store.read(QuoteFeed.File.self, name), Date().timeIntervalSince1970 - f.got < 900 else { return nil }
        return f
    }
    /// Yahoo 기호 → Alpaca 기호. 미국 종목만 (BRK-B → BRK.B, 토스 코드와 같은 꼴). 국내·지수·환율은 nil
    static func code(_ sym: String) -> String? {
        let u = sym.uppercased()
        guard !u.hasSuffix(".KS"), !u.hasSuffix(".KQ") else { return nil }
        return TossFeed.code(u)
    }

    /// syms: 종목 → Yahoo 기호. 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 5초를 기다린다
    @discardableResult
    static func fetch(_ syms: [String: String], minAge: TimeInterval = 0) async -> QuoteFeed.File? {
        guard let k = AlpacaKeys.load() else { return nil }
        var ids: [String: [String]] = [:]   // Alpaca 기호 → 앱 종목들
        for (id, y) in syms { if let c = code(y) { ids[c, default: []].append(id) } }
        guard !ids.isEmpty else { return nil }
        if let o = cached(), Date().timeIntervalSince1970 - o.got < minAge { return o }
        var q: [String: QuoteFeed.Q] = [:]
        let all = ids.keys.sorted()
        for i in stride(from: 0, to: all.count, by: 100) {
            let part = all[i..<min(i + 100, all.count)].joined(separator: ",")
            guard let enc = part.addingPercentEncoding(withAllowedCharacters: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: ".,"))),
                  let url = URL(string: base + "?symbols=" + enc + "&feed=iex") else { return fail("잘못된 종목") }
            let (code, o, e) = await get(url, k)
            if let e { return fail(e) }
            guard code == 200, let o else { return fail(why(code)) }
            for (c, v) in o {
                guard let s = v as? [String: Any], let t = s["latestTrade"] as? [String: Any], let last = num(t["p"]), last > 0 else { continue }
                let prev = num((s["prevDailyBar"] as? [String: Any])?["c"]) ?? 0
                let at = (t["t"] as? String).flatMap { try? Date($0, strategy: .iso8601) }?.timeIntervalSince1970 ?? Date().timeIntervalSince1970
                for id in ids[c.uppercased()] ?? [] { q[id] = .init(last: last, prev: prev, at: at) }
            }
        }
        guard !q.isEmpty else { return fail("받은 종목 없음") }
        let f = QuoteFeed.File(got: Date().timeIntervalSince1970, q: q)
        Store.write(f, name)
        Store.defaults.set(f.got, forKey: "alpaca_ok_at")
        Store.defaults.removeObject(forKey: "alpaca_err")
        return f
    }

    private static func get(_ url: URL, _ k: AlpacaKeys.Keys) async -> (Int, [String: Any]?, String?) {
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 5)
        r.setValue(k.id, forHTTPHeaderField: "APCA-API-KEY-ID")
        r.setValue(k.secret, forHTTPHeaderField: "APCA-API-SECRET-KEY")
        do {
            let (d, resp) = try await URLSession.shared.data(for: r)
            return ((resp as? HTTPURLResponse)?.statusCode ?? 0, try? JSONSerialization.jsonObject(with: d) as? [String: Any], nil)
        } catch let e as URLError { return (0, nil, e.code == .timedOut ? "5초 안에 응답 없음" : "Alpaca에 닿지 못함") }
        catch { return (0, nil, "Alpaca에 닿지 못함") }
    }

    static func why(_ code: Int) -> String {
        if code == 401 || code == 403 { return "키가 맞지 않아요" }
        if code == 429 { return "잠시 뒤에 다시 받아요" }
        return "Alpaca 응답 \(code)"
    }

    /// 키 확인: AAPL 하나를 물어 본다 (설정 화면 '연결하기')
    static func check() async -> String? {
        guard let k = AlpacaKeys.load(), let url = URL(string: base + "?symbols=AAPL&feed=iex") else { return "키 없음" }
        let (code, _, e) = await get(url, k)
        return e ?? (code == 200 ? nil : why(code))
    }

    /// 위젯: 앱이 feed.json 에 넘긴 내 종목을 위젯이 직접 Alpaca 에 묻는다
    @discardableResult
    static func widgetFetch(minAge: TimeInterval) async -> QuoteFeed.File? {
        guard AlpacaKeys.on, let ty = Store.read(WFeed.self, "feed.json")?.ty, !ty.isEmpty else { return nil }
        return await fetch(ty, minAge: minAge)
    }
}

/// 사용자가 넣은 Alpaca 키. 토스 키처럼 이 폰 키체인(위젯과 같이 쓰는 그룹)에만 둔다
enum AlpacaKeys {
    struct Keys: Codable { var id: String; var secret: String }
    private static func q() -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.alpaca", kSecAttrAccount as String: "keys"]
    }
    private static func put(_ v: Keys?) {
        SecItemDelete(q() as CFDictionary)
        guard let v, let d = try? JSONEncoder().encode(v) else { return }
        var a = q()
        a[kSecAttrAccessGroup as String] = TossKeys.group
        a[kSecValueData as String] = d
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(a as CFDictionary, nil)
    }
    /// 키를 넣었는지 (위젯도 보므로 표시만 공유 저장소에 둔다)
    static var on: Bool { Store.defaults.bool(forKey: "alpaca_user") }
    static func load() -> Keys? {
        guard on else { return nil }
        var a = q()
        a[kSecReturnData as String] = true
        a[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(a as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return try? JSONDecoder().decode(Keys.self, from: d)
    }
    static func save(_ k: Keys) {
        put(k)
        Store.defaults.set(true, forKey: "alpaca_user")
    }
    static func clear() {
        put(nil)
        Store.defaults.set(false, forKey: "alpaca_user")
        Store.defaults.removeObject(forKey: "alpaca_err")
        Store.defaults.removeObject(forKey: "alpaca_ok_at")
        Store.remove(AlpacaFeed.name)
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
