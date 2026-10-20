import Foundation
import StoreKit

// 구독 원고 서버 (naeilo 중계 워커의 /store/*). 앱은 구독의 서명된 거래(JWS)를 그대로 보내고,
// 서버가 Apple 서명과 구독을 확인한 뒤 유료 원고를 건넨다. 개발자 빌드(TestFlight·Xcode)에서 개발자 동기화 토큰이 있으면 구매 없이 받는다.
//   POST /store/book {jws, book:"side"|"vol1", lang}   → { book:{lang, from, chapters} }   (구독 모자람 403, 원고 없음 404)
enum StoreAPI {
    struct Book: Decodable { let lang: String; let from: Int; let chapters: [StoryChapter] }

    enum Fail: LocalizedError {
        case tier, missing, server(String)
        var errorDescription: String? {
            switch self {
            case .tier: String(localized: "구독하면 열려요.")
            case .missing: String(localized: "원고가 아직 올라오지 않았어요. 준비되면 여기서 바로 받을 수 있어요.")
            case .server(let s): s
            }
        }
    }

    private struct BookReply: Decodable { let book: Book }
    private struct ErrorReply: Decodable { let error: String? }

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
            throw Fail.server((try? JSONDecoder().decode(ErrorReply.self, from: d))?.error ?? String(localized: "서버에 닿지 못했어요 (\(String(describing: code)))."))
        }
        return try JSONDecoder().decode(T.self, from: d)
    }

    @MainActor static func book(_ id: StoryBook, _ lang: StoryLang) async throws -> Book {
        let r: BookReply = try await post("book", ["book": id.rawValue, "lang": lang.rawValue])
        return r.book
    }
}
