import Foundation
import JavaScriptCore

/// 2차: 사이트와 같은 계산 파일(model.js + widget-core.js)을 아이폰에서 직접 돌려 위젯 요약을 만든다.
/// 가격 이력은 처음 한 번만 받고, 이후엔 data/widget.json 의 최근 10거래일로 이어 붙인다 (PriceCache)
enum Calculator {
    enum CalcError: LocalizedError {
        case noState, js(String)
        var errorDescription: String? {
            switch self { case .noState: return "입력값이 아직 없습니다"; case .js(let m): return "계산 오류: \(m)" }
        }
    }

    /// 사이트 요약보다 새 계산이 필요한가 (종가 기준일이 바뀌었거나 입력값이 요약 뒤에 바뀜)
    static func needed() -> Bool {
        guard let live = Store.read(WLive.self, "live.json") else { return false }
        guard let s = Store.read(WSummary.self, "summary.json") else { return true }
        if s.v != Config.summaryVersion { return true }
        if live.stateAt > s.at { return true }
        if s.today < Day.today { return true }
        if let f = Store.read(WFeed.self, "feed.json"), let d = f.c["SPY"]?.d.last ?? f.c.values.first?.d.last, d > s.asof { return true }
        return false
    }

    @discardableResult
    static func run(force: Bool = false) async throws -> Bool {
        guard force || needed() else { return false }
        guard let stateData = Store.readData("state.json"), let live = Store.read(WLive.self, "live.json") else { throw CalcError.noState }
        let feed = Store.read(WFeed.self, "feed.json")
        let syms = Set(live.hold.map(\.t) + Config.extraSymbols)
        let prices = try await PriceCache.ensure(Array(syms), feed: feed)
        let scripts = try await Scripts.load()

        // 사이트 S 와 같은 모양: { state, prices, quotes, mar, today, now }
        var quotes: [String: Any] = [:]
        for (sym, q) in feed?.q ?? [:] {
            var o: [String: Any] = ["currency": feed?.ccy[sym] ?? (sym.hasSuffix("=X") ? "KRW" : "USD")]
            if let v = q.first ?? nil { o["last"] = v }
            if q.count > 1, let v = q[1] { o["prev_close"] = v }
            if q.count > 2, let v = q[2] { o["last_time"] = v }
            quotes[sym] = o
        }
        var mar: [String: Any] = [:]
        if let m = feed?.mar { mar["USD"] = ["rate": m.rate, "date": m.date ?? ""] }
        let state = try JSONSerialization.jsonObject(with: stateData)
        let input: [String: Any] = ["state": state, "quotes": quotes, "mar": mar, "today": Day.today, "now": Date().timeIntervalSince1970 * 1000]
        let inputJSON = String(data: try JSONSerialization.data(withJSONObject: input), encoding: .utf8)!
        let pricesJSON = "{" + prices.map { "\(jsonString($0.key)):\(String(data: $0.value, encoding: .utf8) ?? "null")" }.joined(separator: ",") + "}"

        let out: String = try await withCheckedThrowingContinuation { cont in
            DispatchQueue.global(qos: .utility).async {
                let ctx = JSContext()!
                var err: String?
                ctx.exceptionHandler = { _, e in err = e?.toString() }
                ctx.evaluateScript("var window = this; var console = { log: function(){}, error: function(){} };")
                ctx.evaluateScript(scripts.model)
                ctx.evaluateScript(scripts.core)
                ctx.evaluateScript("var S = \(inputJSON); S.prices = \(pricesJSON);")
                let r = ctx.evaluateScript("JSON.stringify(WidgetCore.summary(S))")?.toString()
                if let err { cont.resume(throwing: CalcError.js(err)) } else { cont.resume(returning: r ?? "null") }
            }
        }
        guard let d = out.data(using: .utf8), let sum = try? JSONDecoder().decode(WSummary.self, from: d) else { throw CalcError.js("요약을 만들지 못했습니다") }
        // 그 사이 사이트가 더 새 요약을 올렸으면 그것을 둔다
        if Refresher.isNewer(sum, than: Store.read(WSummary.self, "summary.json")) || force {
            Store.writeData(d, "summary.json"); Store.summarySource = "app"
        }
        return true
    }

    private static func jsonString(_ s: String) -> String {
        String(data: try! JSONSerialization.data(withJSONObject: [s]), encoding: .utf8)!.dropFirst().dropLast().description
    }
}

/// 계산 파일: 앱에 넣은 것을 쓰되, 사이트에 더 새 파일이 있으면 받아 쓴다 (사이트와 같은 버전 유지)
enum Scripts {
    struct Pair { var model: String; var core: String }
    static func load() async throws -> Pair {
        func one(_ name: String) async -> String {
            let key = "js-" + name
            _ = try? await Net.get(Config.site.appendingPathComponent("web/" + name), key: key)
            if let d = Store.readData(key), let s = String(data: d, encoding: .utf8), s.contains("window.") { return s }
            if let u = Bundle.main.url(forResource: (name as NSString).deletingPathExtension, withExtension: "js"), let s = try? String(contentsOf: u) { return s }
            return ""
        }
        async let m = one("model.js")
        async let c = one("widget-core.js")
        return Pair(model: await m, core: await c)
    }
}

/// 3년치 가격 파일 캐시. 처음 한 번 받고, 이후엔 widget.json 의 최근 10거래일로 이어 붙인다
enum PriceCache {
    static func ensure(_ syms: [String], feed: WFeed?) async throws -> [String: Data] {
        var index: [String: String] = [:]
        _ = try? await Net.get(Config.data.appendingPathComponent("index.json"), key: "index.json")
        if let d = Store.readData("index.json"), let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any] {
            index = j["prices"] as? [String: String] ?? [:]
        }
        var out: [String: Data] = [:]
        for sym in syms {
            guard let file = index[sym] else { continue }
            let key = "p-" + file
            var obj = Store.readData(key).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
            if let o = obj, let tail = feed?.c[sym] {
                if let merged = merge(o, tail) { obj = merged } else { obj = nil } // 이어 붙일 수 없으면 (빈 날, 배당 조정) 새로 받는다
            }
            if obj == nil {
                var req = URLRequest(url: Config.data.appendingPathComponent("prices/" + file), cachePolicy: .reloadIgnoringLocalCacheData)
                req.timeoutInterval = 30
                let (d, r) = try await URLSession.shared.data(for: req)
                guard (r as? HTTPURLResponse)?.statusCode == 200 else { continue }
                obj = try JSONSerialization.jsonObject(with: d) as? [String: Any]
            }
            if let o = obj, let d = try? JSONSerialization.data(withJSONObject: o) { Store.writeData(d, key); out[sym] = d }
        }
        return out
    }

    /// 캐시 끝과 최근 10거래일이 겹치면 겹친 날부터 바꿔 넣는다. 겹친 날 수정종가가 다르면(배당 등으로 전체 조정) nil
    static func merge(_ o: [String: Any], _ t: WFeed.Tail) -> [String: Any]? {
        guard var dates = o["dates"] as? [String], var close = o["close"] as? [Any], var adj = o["adj"] as? [Any],
              let first = t.d.first, let at = dates.firstIndex(where: { $0 >= first }) else { return t.d.isEmpty ? o : nil }
        if dates[at] == first, let a0 = num(adj[at]), let b0 = t.a.first ?? nil, abs(a0 / b0 - 1) > 1e-6 { return nil }
        if dates[at] != first && at > 0 { return nil }
        dates.removeSubrange(at...); close.removeSubrange(at...); adj.removeSubrange(at...)
        let box: (Double?) -> Any = { v in if let v { return v } else { return NSNull() } }
        dates += t.d; close += t.c.map(box); adj += t.a.map(box)
        var r = o; r["dates"] = dates; r["close"] = close; r["adj"] = adj
        return r
    }
}
