#if MOCK
import Foundation

// 모의투자 시세 길: 개발자 서버(server.py)의 /api/mock/… 만 부른다.
// 일봉은 서버가 한도 안에서 동시에 받아 묶어 준다. 호가는 주문하는 순간에만.
enum MockFeed {
    struct Fail: Error { let why: String }

    /// 개발자 서버: Tailscale 주소(TossFeed.base)가 있으면 그쪽, 없으면 공용 중계 toss.naeilo.com 뒤 (Caddy 가 /api/mock 만 개발자 서버로 넘긴다)
    static var base: String? { TossFeed.base ?? (token == nil ? nil : Config.tossRelay) }

    /// 개발자 서버 열쇠. naeilo://mock?t=… 로 이 기기에만 넣는다. 저장소·앱 코드에는 없다
    static let tokenKey = "mock_dev_token"
    static var token: String? { UserDefaults.standard.string(forKey: tokenKey).flatMap { $0.isEmpty ? nil : $0 } }
    /// 쿼리 t 가 16자 이상 영숫자·-·_ 이면 저장, 아니면 지운다
    static func setToken(from url: URL) {
        let t = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "t" }?.value ?? ""
        let ok = t.count >= 16 && t.unicodeScalars.allSatisfy { ($0.isASCII && CharacterSet.alphanumerics.contains($0)) || $0 == "-" || $0 == "_" }
        if ok { UserDefaults.standard.set(t, forKey: tokenKey) } else { UserDefaults.standard.removeObject(forKey: tokenKey) }
    }

    /// 서버가 토스를 부르는 길(시세·호가·일정)은 모두 여기를 지난다. 호출 한도: 초당 N번으로 줄 세우고,
    /// 한도 응답(429)이면 Retry-After(없으면 1·2·4초) 기다렸다 3번까지 다시 부른다
    private static func get(_ path: String, timeout: TimeInterval) async throws -> [String: Any] {
        guard let b = base, let url = URL(string: b + path) else { throw Fail(why: "개발자 서버 주소·열쇠 없음 (naeilo://mock?t=…)") }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeout)
        req.setValue("naeilo-ios-mock/0.1", forHTTPHeaderField: "User-Agent")
        if let t = token { req.setValue(t, forHTTPHeaderField: "X-Naeilo-Dev") }
        var data = Data(), resp: URLResponse = URLResponse(), code = 0
        for attempt in 0...3 {
            try await MockGate.shared.wait()
            do { (data, resp) = try await URLSession.shared.data(for: req) }
            catch let e as URLError {
                // -999 는 서버 문제가 아니라 일이 취소된 것 (화면·앱 전환). 오류가 아닌 취소로 올린다
                if e.code == .cancelled { throw CancellationError() }
                throw Fail(why: e.code == .timedOut ? "서버 응답 없음 (\(Int(timeout))초)" : "서버 연결 실패 (\(e.code.rawValue))")
            }
            code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            guard code == 429 else { break }
            if attempt == 3 { throw Fail(why: "토스 호출 한도 · 3번 다시 불러도 막힘") }
            let after = ((resp as? HTTPURLResponse)?.value(forHTTPHeaderField: "Retry-After")).flatMap { Double($0) } ?? pow(2, Double(attempt))
            try await Task.sleep(for: .seconds(min(after, 10)))
        }
        if code == 401 || code == 403 { throw Fail(why: "개발자 서버 열쇠가 맞지 않아요") }
        guard code == 200 else { throw Fail(why: code == 404 ? "서버에 모의투자 경로 없음 (server.py 새 판 필요)" : "서버 응답 \(code)") }
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Fail(why: "응답을 읽지 못함") }
        if let e = o["error"] as? String { throw Fail(why: "서버: " + e) }
        return o
    }

    /// AI 조언 (서버가 Claude 를 부른다. 키·한도·쓴 돈은 서버에). 실패해도 쓴 돈이 있으면 함께 온다
    static func ai(kind: String, prompt: String, limits: [String: Double]) async throws -> [String: Any] {
        guard let b = base, let url = URL(string: b + "/api/mock/ai") else { throw Fail(why: "개발자 서버 주소·열쇠 없음 (naeilo://mock?t=…)") }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 130)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("naeilo-ios-mock/0.1", forHTTPHeaderField: "User-Agent")
        if let t = token { req.setValue(t, forHTTPHeaderField: "X-Naeilo-Dev") }
        req.httpBody = try JSONSerialization.data(withJSONObject: ["kind": kind, "prompt": prompt, "limits": limits])
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: req) }
        catch let e as URLError { throw Fail(why: e.code == .timedOut ? "AI 응답 없음 (130초)" : "서버 연결 실패 (\(e.code.rawValue))") }
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code == 401 || code == 403 { throw Fail(why: "개발자 서버 열쇠가 맞지 않아요") }
        guard code == 200 || code == 500 else { throw Fail(why: code == 404 ? "서버에 AI 경로 없음 (server.py 새 판 필요)" : "서버 응답 \(code)") }
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Fail(why: "응답을 읽지 못함") }
        return o
    }

    /// AI 쓴 돈·한도
    static func wallet(limits: [String: Double]) async throws -> [String: Any] {
        let q = limits.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        return try await get("/api/mock/wallet?" + q, timeout: 6)
    }

    static func num(_ v: Any?) -> Double? {
        if let d = v as? Double { return d }
        if let s = v as? String { return Double(s) }
        if let n = v as? NSNumber { return n.doubleValue }
        return nil
    }

    static func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? s }

    /// 일봉: 100종목씩 나눠 묻는다. progress(받은 종목 수). 실패한 종목은 errors 에 이유.
    /// skip: 이미 받은 묶음 (시작 번호) — 멈췄다 이어 받을 때. batch: 묶음 하나를 받을 때마다 (시작 번호, 일봉, 실패)
    static func daily(_ codes: [String], count: Int, skip: Set<Int> = [], progress: @MainActor (Int) -> Void,
                      batch: (@MainActor (Int, [String: [MockBar]], [String: String]) -> Void)? = nil) async throws -> (bars: [String: [MockBar]], errors: [String: String]) {
        var bars: [String: [MockBar]] = [:], errors: [String: String] = [:]
        var done = 0
        for i in stride(from: 0, to: codes.count, by: 100) {
            let part = Array(codes[i..<min(codes.count, i + 100)])
            if skip.contains(i) { done += part.count; continue }
            try Task.checkCancellation()
            let o: [String: Any]
            do { o = try await get("/api/mock/daily?count=\(count)&symbols=" + enc(part.joined(separator: ",")), timeout: 70) }
            catch let f as Fail {
                // 처음 묶음부터 실패하면 그만 (서버 문제). 중간이면 그 묶음만 이유를 남기고 이어 간다
                if bars.isEmpty && i == 0 { throw f }
                var errs: [String: String] = [:]
                for c in part { errs[c] = f.why }
                errors.merge(errs) { _, b in b }
                await batch?(i, [:], errs)
                done += part.count; await progress(done); continue
            }
            var got: [String: [MockBar]] = [:], errs: [String: String] = [:]
            for (c, rows) in o["bars"] as? [String: Any] ?? [:] {
                got[c] = (rows as? [[Any]] ?? []).compactMap { r in
                    guard r.count >= 6, let d = r[0] as? String, let op = num(r[1]), let h = num(r[2]), let l = num(r[3]), let cl = num(r[4]) else { return nil }
                    return MockBar(d: d, o: op, h: h, l: l, c: cl, v: num(r[5]) ?? 0)
                }
            }
            for (c, e) in o["errors"] as? [String: String] ?? [:] { errs[c] = e }
            bars.merge(got) { _, b in b }; errors.merge(errs) { _, b in b }
            await batch?(i, got, errs)
            done += part.count
            await progress(done)
        }
        return (bars, errors)
    }

    /// 종목 상세: 마지막 거래일 1분봉 (d = "09:01") 과 일봉 200개. 같은 날 한 번 받은 일봉은 다시 쓴다
    @MainActor static func detail(_ code: String) async throws -> (day: String, minutes: [MockBar], daily: [MockBar]) {
        let (day, mins) = try await minutes(code)
        let today = MockFmt.day(Date())
        var days = dayCache[code].flatMap { $0.day == today ? $0.bars : nil }
        if days == nil {
            days = try await daily([code], count: 200, progress: { _ in }).bars[code] ?? []
            dayCache[code] = (today, days!)
        }
        return (day, mins, days!)
    }

    /// 마지막 거래일 정규장 1분봉 (d = "09:01")
    static func minutes(_ code: String) async throws -> (day: String, bars: [MockBar]) {
        let o = try await get("/api/mock/minutes?symbol=" + enc(code), timeout: 12)
        let mins = (o["bars"] as? [[Any]] ?? []).compactMap { r -> MockBar? in
            guard r.count >= 6, let d = r[0] as? String, let op = num(r[1]), let h = num(r[2]), let l = num(r[3]), let cl = num(r[4]) else { return nil }
            return MockBar(d: d, o: op, h: h, l: l, c: cl, v: num(r[5]) ?? 0)
        }
        return (o["day"] as? String ?? "", mins)
    }
    @MainActor private static var dayCache: [String: (day: String, bars: [MockBar])] = [:]

    /// 지금 가격 (토스 코드 → 가격)
    static func last(_ codes: [String]) async throws -> [String: Double] {
        guard !codes.isEmpty else { return [:] }
        var out: [String: Double] = [:]
        for i in stride(from: 0, to: codes.count, by: 200) {
            let part = Array(codes[i..<min(codes.count, i + 200)])
            let o = try await get("/api/mock/last?symbols=" + enc(part.joined(separator: ",")), timeout: 8)
            for (c, v) in o["quotes"] as? [String: Any] ?? [:] {
                if let x = num((v as? [String: Any])?["last"]), x > 0 { out[c] = x }
            }
        }
        return out
    }

    /// 호가 (주문 순간)
    static func book(_ code: String) async throws -> MockCalc.Book {
        let o = try await get("/api/mock/book?symbol=" + enc(code), timeout: 6)
        func levels(_ k: String) -> [MockCalc.Level] {
            (o[k] as? [[String: Any]] ?? []).compactMap { e in
                guard let p = num(e["price"]), let v = num(e["volume"]) else { return nil }
                return MockCalc.Level(p: p, v: v)
            }
        }
        return MockCalc.Book(asks: levels("asks"), bids: levels("bids"), at: (o["timestamp"] as? String).flatMap(isoDate))
    }

    /// 유의사항 (최대 30종목): 코드 → 지금 걸린 종류. 거래정지·정리매매는 종목 정보로 따로 물어 같이 넣는다 (SUSPENDED · LIQUIDATION_TRADING)
    static func warnings(_ codes: [String]) async throws -> [String: [String]] {
        guard !codes.isEmpty else { return [:] }
        let top = Array(codes.prefix(30))
        let w = try await get("/api/mock/warnings?symbols=" + enc(top.joined(separator: ",")), timeout: 20)
        var out: [String: [String]] = [:]
        for (c, v) in w["warnings"] as? [String: Any] ?? [:] { out[c] = v as? [String] ?? [] }
        let sp = try await get("/api/mock/suspended?symbols=" + enc(top.joined(separator: ",")), timeout: 8)
        for (c, v) in sp["info"] as? [String: Any] ?? [:] {
            let x = v as? [String: Any] ?? [:]
            if x["suspended"] as? Bool == true { out[c, default: []].append("SUSPENDED") }
            if x["liquidation"] as? Bool == true && !(out[c] ?? []).contains("LIQUIDATION_TRADING") { out[c, default: []].append("LIQUIDATION_TRADING") }
        }
        return out
    }

    /// 오늘 상한가·하한가 (서버가 하루 한 번 받는다)
    static func limits(_ code: String) async throws -> (upper: Double, lower: Double)? {
        let o = try await get("/api/mock/limits?symbol=" + enc(code), timeout: 6)
        guard let u = num(o["upper"]), let l = num(o["lower"]) else { return nil }
        return (u, l)
    }

    /// 국내 장 일정: 오늘 정규장 시작·끝 (휴장이면 nil)
    struct Session: Codable, Equatable {
        var day: String
        var open: Date?
        var close: Date?
        var got: Date
    }
    static func calendar() async throws -> Session {
        let o = try await get("/api/mock/calendar?market=KR", timeout: 6)
        guard let t = o["today"] as? [String: Any], let d = t["date"] as? String else { throw Fail(why: "장 일정을 읽지 못함") }
        let reg = (t["integrated"] as? [String: Any])?["regularMarket"] as? [String: Any]
        return Session(day: d, open: (reg?["startTime"] as? String).flatMap(isoDate), close: (reg?["endTime"] as? String).flatMap(isoDate), got: Date())
    }

    static func isoDate(_ s: String) -> Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }
}
/// 토스 시세 호출 한도: 부르기 사이를 1/N초 이상 띄운다 (N 은 설정 안전장치, 기본 5)
actor MockGate {
    static let shared = MockGate()
    private var gap: Double = 0.2
    private var next = Date.distantPast
    func set(_ perSec: Double) { gap = 1 / max(1, perSec) }
    func wait() async throws {
        let now = Date(), at = max(now, next)
        next = at.addingTimeInterval(gap)
        let d = at.timeIntervalSince(now)
        if d > 0 { try await Task.sleep(for: .seconds(d)) }
    }
}
// MARK: 토스 주문 API (문서: github.com/beeboy/toss-api docs 의 주문 · 주문 내역 · 인증 · API 소개, 2026-10-26 읽음)
// 요청을 만들고 응답을 읽는 곳. 보내는 일은 MockTossBroker 가 실전 스위치가 켜졌을 때만 한다.
// 토스는 허용 IP 에서만 받고 키는 서버에만 있으므로 개발자 서버(server.py /api/mock/order…)가 중계한다. 앱에는 토큰·계좌가 없다

enum TossOrderAPI {
    /// 호출 한도 (초당, 문서 표). 피크 09:00~09:10 은 ORDER_INFO 만 3회로 줄어든다
    static let perSecond: [String: Double] = ["AUTH": 5, "ORDER": 10, "ORDER_HISTORY": 5, "ORDER_INFO": 6]

    /// 주문 세부 상태 → 앱 상태. 정정·취소 거부는 원래 주문이 그대로라 nil (상태 유지)
    static func state(_ s: String) -> MockOrderState? {
        switch s {
        case "PENDING", "PENDING_CANCEL", "PENDING_REPLACE": return .accepted
        case "PARTIAL_FILLED": return .partial
        case "FILLED": return .filled
        case "CANCELED", "REPLACED": return .canceled
        case "REJECTED": return .rejected
        default: return nil   // CANCEL_REJECTED · REPLACE_REJECTED · 모르는 값 (문서: 모르는 enum 은 받아 넘겨야 함)
        }
    }

    /// 주문 한 건 (목록·상세·웹소켓 data.order 가 같은 모양)
    struct Order {
        var id: String
        var status: String
        var filledQty: Int
        var avg: Double?
        var filledAmount: Double?
        var commission: Double?
        var tax: Double?
    }

    /// 주문 생성 본문. 국내는 정수 수량·정수 가격(호가 단위). clientOrderId = 앱 주문 id (멱등성 키, 10분)
    static func createBody(_ o: MockOrder) -> [String: Any] {
        var b: [String: Any] = ["symbol": o.code, "side": o.side == .buy ? "BUY" : "SELL",
                                "orderType": o.type == .limit ? "LIMIT" : "MARKET", "quantity": String(o.qty),
                                "clientOrderId": o.id.uuidString, "timeInForce": "DAY"]
        if o.type == .limit, let p = o.limit { b["price"] = String(Int(p.rounded())) }
        return b
    }

    /// 중계 경로: 만들기 /api/mock/order · 상세 /api/mock/order/{id} · 취소 /api/mock/order/{id}/cancel. 서버 주소·열쇠가 없으면 nil
    static func request(_ method: String, _ path: String, body: [String: Any]?) -> URLRequest? {
        guard let b = MockFeed.base, let t = MockFeed.token, let url = URL(string: b + path) else { return nil }
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        r.httpMethod = method
        r.setValue(t, forHTTPHeaderField: "X-Naeilo-Dev")
        if let body {
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        return r
    }

    static func orderId(_ o: [String: Any]) -> String? { (o["result"] as? [String: Any])?["orderId"] as? String }

    static func order(_ o: [String: Any]) -> Order? {
        guard let r = o["result"] as? [String: Any], let id = r["orderId"] as? String, let st = r["status"] as? String else { return nil }
        let e = r["execution"] as? [String: Any] ?? [:]
        return Order(id: id, status: st, filledQty: Int(MockFeed.num(e["filledQuantity"]) ?? 0), avg: MockFeed.num(e["averageFilledPrice"]),
                     filledAmount: MockFeed.num(e["filledAmount"]), commission: MockFeed.num(e["commission"]), tax: MockFeed.num(e["tax"]))
    }

    /// 오류 봉투 {"error":{"code","message"}} → 화면 글
    static func reason(_ o: [String: Any]?, status: Int) -> String {
        let e = o?["error"] as? [String: Any]
        let code = e?["code"] as? String ?? "http-\(status)"
        return (reasons[code] ?? (e?["message"] as? String) ?? "토스 오류") + " (\(code))"
    }

    /// 문서의 오류 코드 (주문에 쓰는 것만)
    static let reasons: [String: String] = [
        "invalid-request": "요청이 올바르지 않음", "confirm-high-value-required": "1억원 이상 주문 확인 필요",
        "account-header-required": "계좌 머리글 없음", "invalid-token": "토큰이 맞지 않음", "expired-token": "토큰 만료",
        "token-revoked": "새 토큰으로 바뀜", "ip-not-allowed": "허용되지 않은 IP", "forbidden": "권한 없음",
        "account-not-found": "계좌 없음", "order-not-found": "주문 없음", "request-in-progress": "같은 주문 처리 중",
        "already-filled": "이미 체결됨", "already-canceled": "이미 취소됨", "already-modified": "이미 정정됨",
        "already-rejected": "이미 거부됨", "already-processing": "정정·취소 처리 중", "opposite-pending-order-exists": "같은 종목 반대 방향 대기 주문 있음",
        "insufficient-buying-power": "주문 가능 금액 부족", "order-hours-closed": "주문 받는 시간 아님", "stock-restricted": "거래 제한 종목",
        "price-out-of-range": "상·하한가 밖 가격", "order-type-not-allowed": "지금 못 쓰는 호가 유형", "prerequisite-required": "약관·교육 등 자격 필요",
        "market-not-supported-for-stock": "이 시장에서 거래 못 하는 종목", "investor-exchange-not-integrated": "거래소 설정이 통합(SOR)이 아님",
        "insufficient-sellable-quantity": "매도 가능 수량 부족", "order-limit-exceeded": "주문 설정 한도 넘음",
        "idempotency-key-conflict": "같은 주문 번호로 다른 주문", "account-restricted": "계좌가 이 주문을 허용하지 않음",
        "max-order-amount-exceeded": "30억원 이상 주문", "rate-limit-exceeded": "호출 한도 넘음", "edge-rate-limit-exceeded": "호출 한도 넘음",
        "internal-error": "토스 일시 장애", "maintenance": "토스 점검 중",
        // 중계 서버가 막은 것 (토스에 안 보냄)
        "relay-off": "서버 실전 꺼짐", "relay-no-keys": "서버에 토스 키 없음", "relay-no-account": "서버에 계좌 없음",
        "relay-over-cap": "서버 한 번 주문 상한 넘음", "relay-no-price": "지금 가격을 몰라 시장가 안 냄", "relay-unreachable": "서버가 토스에 닿지 못함",
    ]

    /// 정정 본문 (국내: 수량 필수, 지정가 가격 정수). 서버가 상한을 보려고 종목도 같이 보낸다
    static func modifyBody(_ o: MockOrder, qty: Int, limit: Double) -> [String: Any] {
        ["symbol": o.code, "orderType": "LIMIT", "quantity": String(qty), "price": String(Int(limit.rounded()))]
    }

    /// 서버 실전 상태 (비밀값 없이): 스위치·계좌·키·한 번 주문 상한
    struct Ready: Equatable { var armed = false, account = false, keys = false, maxKrw: Double = 0 }
    static func readyInfo() async -> Ready? {
        guard let req = request("GET", "/api/mock/order/ready", body: nil),
              let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let j = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return Ready(armed: j["armed"] as? Bool == true, account: j["account"] as? Bool == true, keys: j["keys"] as? Bool == true,
                     maxKrw: MockFeed.num(j["max_krw"]) ?? 0)
    }

    /// 토스 계좌 (서버가 계좌 머리글을 붙여 묻는다): 주문가능금액(원) · 국내 보유
    struct Account: Equatable {
        struct Item: Equatable { var code: String; var qty: Int; var avg: Double?; var plAfterCost: Double? }
        var cash: Double?
        var items: [Item]
        var plAfterCost: Double?
        var dayPl: Double? = nil      // 토스 오늘 손익 (국내 합). 옛 서버는 없음
        var at: Date
    }
    static func account() async -> (Account?, String?) {
        guard let req = request("GET", "/api/mock/account", body: nil) else { return (nil, "개발자 서버 주소·열쇠 없음") }
        guard let (data, resp) = try? await URLSession.shared.data(for: req), let h = resp as? HTTPURLResponse else { return (nil, "서버에 닿지 못함") }
        let j = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard h.statusCode == 200, let j else { return (nil, reason(j, status: h.statusCode)) }
        let items = (j["holdings"] as? [[String: Any]] ?? []).compactMap { x -> Account.Item? in
            guard let c = x["code"] as? String else { return nil }
            return Account.Item(code: c, qty: Int(MockFeed.num(x["qty"]) ?? 0), avg: MockFeed.num(x["avg"]), plAfterCost: MockFeed.num(x["plAfterCost"]))
        }
        return (Account(cash: MockFeed.num(j["cash"]), items: items, plAfterCost: MockFeed.num(j["plAfterCost"]), dayPl: MockFeed.num(j["dayPl"]), at: Date()), nil)
    }

    /// 서버 실전 준비 (toss_live.json 켜짐·계좌·키). 준비가 덜 됐으면 이유, 다 됐으면 nil
    static func ready() async -> String? {
        guard let req = request("GET", "/api/mock/order/ready", body: nil) else { return "개발자 서버 주소·열쇠 없음" }
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let j = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return "서버에 닿지 못함" }
        if j["keys"] as? Bool != true { return "서버에 토스 키 없음" }
        if j["account"] as? Bool != true { return "서버에 계좌 없음" }
        if j["armed"] as? Bool != true { return "서버 실전 꺼짐" }
        return nil
    }
}

#endif
