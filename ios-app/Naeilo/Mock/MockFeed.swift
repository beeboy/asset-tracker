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
        let o = try await get("/api/mock/minutes?symbol=" + enc(code), timeout: 12)
        let mins = (o["bars"] as? [[Any]] ?? []).compactMap { r -> MockBar? in
            guard r.count >= 6, let d = r[0] as? String, let op = num(r[1]), let h = num(r[2]), let l = num(r[3]), let cl = num(r[4]) else { return nil }
            return MockBar(d: d, o: op, h: h, l: l, c: cl, v: num(r[5]) ?? 0)
        }
        let today = MockFmt.day(Date())
        var days = dayCache[code].flatMap { $0.day == today ? $0.bars : nil }
        if days == nil {
            days = try await daily([code], count: 200, progress: { _ in }).bars[code] ?? []
            dayCache[code] = (today, days!)
        }
        return (o["day"] as? String ?? "", mins, days!)
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
#endif
