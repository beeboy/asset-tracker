import Foundation

/// 바뀐 것만 받는다. 매번 ETag 로 묻고, 그대로면 304 (몇백 바이트)로 끝난다
enum Net {
    static func get(_ url: URL, key: String, headers: [String: String] = [:]) async throws -> Data? {
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        headers.forEach { req.setValue($0.value, forHTTPHeaderField: $0.key) }
        var tags = Store.defaults.dictionary(forKey: "etags") as? [String: String] ?? [:]
        if let t = tags[key], Store.readData(key) != nil { req.setValue(t, forHTTPHeaderField: "If-None-Match") }
        let (d, r) = try await URLSession.shared.data(for: req)
        guard let h = r as? HTTPURLResponse else { throw SyncError.http(0) }
        if h.statusCode == 304 { return nil }
        guard h.statusCode == 200 else {
            if let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any], let m = j["error"] as? String { throw SyncError.server(m) }
            throw SyncError.http(h.statusCode)
        }
        Store.writeData(d, key)
        if let t = h.value(forHTTPHeaderField: "ETag") { tags[key] = t; Store.defaults.set(tags, forKey: "etags") }
        return d
    }
}

struct RefreshResult {
    var feedChanged = false
    var stateChanged = false
    var summaryFromSite = false
}

/// 위젯과 앱이 같이 쓰는 가벼운 갱신: 시세 파일 + 동기화 입력값(사이트 요약 포함)
enum Refresher {
    static func refresh() async throws -> RefreshResult {
        var out = RefreshResult()
        async let feed = Net.get(Config.data.appendingPathComponent("widget.json"), key: "feed.json")
        if try await feed != nil { out.feedChanged = true }
        if let login = Credentials.load() {
            let r = try await pullState(login)
            out.stateChanged = r.0; out.summaryFromSite = r.1
        }
        Store.lastCheck = Date()
        return out
    }

    /// 입력값을 받아 풀고, 사이트 요약(_w)이 더 새로우면 그것으로 바꾼다
    static func pullState(_ login: Login) async throws -> (Bool, Bool) {
        let raw: Data?
        switch login {
        case .password(let id, _):
            raw = try await Net.get(URL(string: "esync?id=\(id)", relativeTo: Config.relay)!, key: "sync.json")
        case .github(let token):
            raw = try await Net.get(Config.relay.appendingPathComponent("sync"), key: "sync.json", headers: ["Authorization": "Bearer \(token)"])
        }
        guard let raw else { return (false, false) }
        guard let j = try JSONSerialization.jsonObject(with: raw) as? [String: Any] else { throw SyncError.noData }
        let at = num(j["at"]) ?? 0
        var state: [String: Any]
        switch login {
        case .password(_, let key):
            guard let c = j["c"] as? String else { throw SyncError.noData }
            let plain: Data
            do { plain = try Credentials.decrypt(c, key: key) } catch { throw SyncError.wrongPassword }
            guard let s = try JSONSerialization.jsonObject(with: plain) as? [String: Any] else { throw SyncError.badCipher }
            state = s
        case .github:
            guard let s = j["state"] as? [String: Any] else { throw SyncError.noData }
            state = s
        }
        var fromSite = false
        if let w = state["_w"], let wd = try? JSONSerialization.data(withJSONObject: w), let cand = try? JSONDecoder().decode(WSummary.self, from: wd),
           cand.v == Config.summaryVersion, isNewer(cand, than: Store.read(WSummary.self, "summary.json")) {
            Store.writeData(wd, "summary.json"); Store.summarySource = "site"; fromSite = true
        }
        state.removeValue(forKey: "_w")
        if let sd = try? JSONSerialization.data(withJSONObject: state) { Store.writeData(sd, "state.json") }
        Store.write(WLive.from(state: state, at: at), "live.json")
        return (true, fromSite)
    }

    /// 종가 기준일이 늦은 쪽, 같으면 나중에 계산한 쪽
    static func isNewer(_ a: WSummary, than b: WSummary?) -> Bool {
        guard let b else { return true }
        if a.asof != b.asof { return a.asof > b.asof }
        if a.today != b.today { return a.today > b.today }
        return a.at > b.at
    }
}
