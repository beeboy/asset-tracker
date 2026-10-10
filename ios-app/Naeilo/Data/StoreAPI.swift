import Foundation
import StoreKit

// 커피 후원 서버 (naeilo 중계 워커의 /store/*). 앱은 가진 구매의 서명된 거래(JWS)를 그대로 보내고,
// 서버가 Apple 서명과 등급을 확인한 뒤 유료 원고를 건넨다. 개발자 빌드(TestFlight·Xcode)에서 개발자 동기화 토큰이 있으면 구매 없이 받는다.
//   POST /store/book {jws, book:"side"|"vol1", lang}   → { tier, book:{lang, from, chapters} }   (등급 모자람 403, 원고 없음 404)
//   POST /store/sponsor {jws, name, logo?}            → { ok, status:"pending" }   (스페셜티만)
//   POST /store/sponsor/me {jws}                      → { pending, approved }
//   GET  /store/sponsors                              → { sponsors:[{name, logo}] }
enum StoreAPI {
    struct Book: Decodable { let lang: String; let from: Int; let chapters: [StoryChapter] }
    struct Sponsor: Decodable, Hashable { let name: String; let logo: String? }
    struct Named: Decodable { let name: String }
    struct Mine: Decodable { let pending: Named?; let approved: Named? }

    enum Fail: LocalizedError {
        case tier, missing, server(String)
        var errorDescription: String? {
            switch self {
            case .tier: "이 등급으로는 아직 열리지 않아요."
            case .missing: "원고가 아직 올라오지 않았어요. 준비되면 여기서 바로 받을 수 있어요."
            case .server(let s): s
            }
        }
    }

    private struct BookReply: Decodable { let book: Book }
    private struct ErrorReply: Decodable { let error: String? }
    private struct SponsorsReply: Decodable { let sponsors: [Sponsor] }

    static func url(_ path: String) -> URL { Config.relay.appendingPathComponent("store").appendingPathComponent(path) }

    @MainActor
    private static func post<T: Decodable>(_ path: String, _ body: [String: Any]) async throws -> T {
        var b = body
        b["jws"] = Support.shared.jws
        var req = URLRequest(url: url(path), timeoutInterval: 30)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        // 개발자 빌드만: 개발자 동기화 토큰이면 구매 없이 (App Store 빌드에서는 보내지 않는다)
        if Support.shared.testBuild, let t = Sync.shared.creds?.token { req.setValue("Bearer " + t, forHTTPHeaderField: "Authorization") }
        req.httpBody = try JSONSerialization.data(withJSONObject: b)
        let (d, r) = try await URLSession.shared.data(for: req)
        let code = (r as? HTTPURLResponse)?.statusCode ?? 0
        if code == 403 { throw Fail.tier }
        if code == 404 { throw Fail.missing }
        guard (200..<300).contains(code) else {
            throw Fail.server((try? JSONDecoder().decode(ErrorReply.self, from: d))?.error ?? "서버에 닿지 못했어요 (\(code)).")
        }
        return try JSONDecoder().decode(T.self, from: d)
    }

    @MainActor static func book(_ id: StoryBook, _ lang: StoryLang) async throws -> Book {
        let r: BookReply = try await post("book", ["book": id.rawValue, "lang": lang.rawValue])
        return r.book
    }

    struct Ok: Decodable { let ok: Bool? }
    @MainActor static func sponsor(name: String, logo: String?) async throws {
        var b: [String: Any] = ["name": name]
        if let logo { b["logo"] = logo }
        let _: Ok = try await post("sponsor", b)
    }
    @MainActor static func mine() async throws -> Mine { try await post("sponsor/me", [:]) }

    static func sponsors() async -> [Sponsor] {
        guard let (d, _) = try? await URLSession.shared.data(from: url("sponsors")),
              let r = try? JSONDecoder().decode(SponsorsReply.self, from: d) else { return [] }
        return r.sponsors
    }
}
