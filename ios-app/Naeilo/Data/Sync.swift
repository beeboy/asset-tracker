import Foundation
import CryptoKit
import CommonCrypto
import Observation
import Security

// 기기 자동 동기화: 사이트(naeilo.com 설정 > 기기 자동 동기화)와 같은 방식이다 (web/app.js esDerive·syncCall).
// - 동기화 비밀번호 → PBKDF2-SHA256(소금 "naeilo-sync-v1", 30만 번) 64바이트. 앞 32바이트의 SHA-256 앞 20바이트 = 저장 위치(id),
//   뒤 32바이트 = AES-GCM 키. 비밀번호는 저장하지 않고, 키만 키체인에 둔다.
// - 중계(/esync?id=)에는 기기에서 암호화한 값 {c: "iv.암호문", at}만 둔다. 중계는 내용을 읽을 수 없다.
// - 나중에 고친 쪽이 이긴다 (at). 처음 켤 때 양쪽 다 값이 있으면 어느 쪽에 맞출지 묻는다.
// - 맞추는 것: 종목·수량·평균 단가(사이트 holdings 형식), 앱의 미션 진행(app 칸, 사이트는 건드리지 않고 그대로 둔다).
//   사이트에만 있는 칸(목표·사건·모형 설정)과 앱이 못 다루는 종목(미국·한국 밖)은 받은 그대로 다시 올린다.
@Observable
final class Sync {
    static let shared = Sync()
    static let relay = "https://asset-ai.drinker.workers.dev/esync?id="

    struct Creds: Codable { let id: String; let k: String }
    enum State: Equatable { case off, idle, working, ok(Date), failed(String) }

    private(set) var creds: Creds? = Keychain.load()
    var state: State = .off
    var isOn: Bool { creds != nil }
    /// 처음 켤 때 양쪽 다 값이 있으면 고르게 한다
    var conflict: Remote? = nil

    struct Remote: Equatable { let state: [String: AnyCodable]; let at: Double }

    private let d = UserDefaults.standard
    /// 이 기기 값이 마지막으로 바뀐 때 (ms). 0 = 이번에 처음 켬
    var localAt: Double {
        get { d.double(forKey: "sync.localAt") }
        set { d.set(newValue, forKey: "sync.localAt") }
    }
    /// 사이트 쪽 전체 입력값 (앱이 안 쓰는 칸을 지키려고 받은 그대로 둔다)
    private var siteState: [String: Any] {
        get { (d.data(forKey: "sync.site")).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:] }
        set { d.set(try? JSONSerialization.data(withJSONObject: newValue), forKey: "sync.site") }
    }
    private var lastSig: String? {
        get { d.string(forKey: "sync.sig") }
        set { d.set(newValue, forKey: "sync.sig") }
    }

    init() { state = creds == nil ? .off : .idle }

    // MARK: 켜기·끄기

    /// 비밀번호로 켠다. 30만 번 계산이라 1초쯤 걸린다
    @MainActor
    func turnOn(password: String, model: AppModel) async {
        state = .working
        let c = await Task.detached { Self.derive(password) }.value
        Keychain.save(c)
        creds = c
        localAt = 0
        lastSig = nil
        await pull(model)
    }

    @MainActor
    func turnOff() {
        Keychain.delete()
        creds = nil
        conflict = nil
        pulledOnce = false
        pushTask?.cancel()
        state = .off
        localAt = 0
        lastSig = nil
    }

    // MARK: 비밀번호 → 저장 위치 + 키 (사이트 esDerive 와 같음)

    static func derive(_ password: String) -> Creds {
        let pw = Array(password.precomposedStringWithCanonicalMapping.utf8)
        let salt = Array("naeilo-sync-v1".utf8)
        var out = [UInt8](repeating: 0, count: 64)
        _ = pw.withUnsafeBufferPointer { p in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), p.baseAddress.map { UnsafeRawPointer($0).assumingMemoryBound(to: Int8.self) }, pw.count,
                                 salt, salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 300_000, &out, 64)
        }
        let id = SHA256.hash(data: Data(out[0..<32])).prefix(20).map { String(format: "%02x", $0) }.joined()
        return Creds(id: id, k: Data(out[32..<64]).base64EncodedString())
    }

    private func key() throws -> SymmetricKey {
        guard let c = creds, let k = Data(base64Encoded: c.k) else { throw SyncError.off }
        return SymmetricKey(data: k)
    }

    enum SyncError: LocalizedError {
        case off, wrongPassword, server(String)
        var errorDescription: String? {
            switch self {
            case .off: "동기화가 꺼져 있어요"
            case .wrongPassword: "풀 수 없어요 (다른 기기와 같은 비밀번호인지 확인해 주세요)"
            case .server(let s): s
            }
        }
    }

    // MARK: 중계와 주고받기 (사이트 syncCall 과 같음)

    private func call(post: [String: Any]? = nil) async throws -> (state: [String: Any]?, at: Double, stale: Bool) {
        guard let c = creds, let url = URL(string: Self.relay + c.id) else { throw SyncError.off }
        var req = URLRequest(url: url, timeoutInterval: 20)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        if let post, let st = post["state"] {
            let plain = try JSONSerialization.data(withJSONObject: st)
            let box = try AES.GCM.seal(plain, using: try key(), nonce: AES.GCM.Nonce())
            let iv = Data(box.nonce), ct = box.ciphertext + box.tag
            let body: [String: Any] = ["c": iv.base64EncodedString() + "." + ct.base64EncodedString(), "at": post["at"] ?? Date().timeIntervalSince1970 * 1000]
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let j = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw SyncError.server((j["error"] as? String) ?? "중계 응답 \((resp as? HTTPURLResponse)?.statusCode ?? 0)") }
        let at = (j["at"] as? Double) ?? 0
        if let cs = j["c"] as? String {
            let parts = cs.split(separator: ".").map(String.init)
            guard parts.count == 2, let iv = Data(base64Encoded: parts[0]), let all = Data(base64Encoded: parts[1]), all.count > 16 else { throw SyncError.wrongPassword }
            do {
                let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: all.dropLast(16), tag: all.suffix(16))
                let plain = try AES.GCM.open(box, using: try key())
                var st = (try JSONSerialization.jsonObject(with: plain) as? [String: Any]) ?? [:]
                st.removeValue(forKey: "_w")      // 사이트가 올린 위젯 요약은 입력값이 아니다
                return (st, at, false)
            } catch { throw SyncError.wrongPassword }
        }
        return (nil, at, (j["stale"] as? Bool) ?? false)
    }

    // MARK: 받기·보내기

    /// 앱을 열 때·켤 때: 중계 값이 더 새것이면 이 기기를 맞추고, 이 기기가 더 새것이면 보낸다
    @MainActor
    func pull(_ m: AppModel) async {
        guard isOn else { return }
        state = .working
        do {
            let r = try await call()
            if let st = r.state, r.at > localAt {
                if localAt == 0 && hasLocalData(m) {
                    // 처음 켤 때 양쪽 다 값이 있으면 묻는다 (사이트와 같은 동작)
                    conflict = Remote(state: st.mapValues(AnyCodable.init), at: r.at)
                    state = .idle
                    return
                }
                try await apply(st, at: r.at, to: m)
                pulledOnce = true
            } else if r.state == nil || r.at < localAt || localAt == 0 {
                if localAt == 0 { localAt = Date().timeIntervalSince1970 * 1000 }
                pulledOnce = true
                try await push(m)
                return
            }
            pulledOnce = true
            state = .ok(Date())
        } catch { state = .failed(error.localizedDescription) }
    }

    /// 처음 켤 때 고른 쪽으로
    @MainActor
    func resolve(useRemote: Bool, _ m: AppModel) async {
        guard let c = conflict else { return }
        conflict = nil
        state = .working
        do {
            pulledOnce = true
            if useRemote { try await apply(c.state.mapValues(\.value), at: c.at, to: m); state = .ok(Date()) }
            else {
                siteState = c.state.mapValues(\.value)      // 사이트 칸은 지키고 종목·진행만 이 기기 값으로
                localAt = Date().timeIntervalSince1970 * 1000
                try await push(m)
            }
        } catch { state = .failed(error.localizedDescription) }
    }

    /// 이 기기 값이 바뀌었을 때 (4초 모아서)
    private var pushTask: Task<Void, Never>?
    private var pulledOnce = false
    @MainActor
    func changed(_ m: AppModel) {
        // 이번에 앱을 켠 뒤 한 번은 받아 본 다음에만 보낸다 (먼저 보내면 다른 기기의 새 값을 덮을 수 있다)
        guard isOn, conflict == nil, pulledOnce else { return }
        let sig = signature(m)
        guard sig != lastSig else { return }
        localAt = Date().timeIntervalSince1970 * 1000
        pushTask?.cancel()
        pushTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            do { try await push(m) } catch { state = .failed(error.localizedDescription) }
        }
    }

    @MainActor
    private func push(_ m: AppModel) async throws {
        state = .working
        let st = outgoing(m)
        let r = try await call(post: ["state": st, "at": localAt])
        if r.stale { await pull(m); return }      // 다른 기기가 더 새 값을 올렸다
        siteState = st
        lastSig = signature(m)
        state = .ok(Date())
    }

    // MARK: 앱 ↔ 사이트 형식

    /// 이 기기에 지킬 값이 있는지: 첫 질문을 마쳤고 종목이나 미션 진행이 있으면
    private func hasLocalData(_ m: AppModel) -> Bool {
        m.onboarded && (!m.holdings.filter { $0.symbol != "DRNK" }.isEmpty || !m.done.isEmpty || !m.gDone.isEmpty)
    }

    /// 사이트 티커 → 앱 이름 (005930.KS → 005930). 미국·한국 밖이면 nil
    static func appId(_ ticker: String) -> String? {
        let t = ticker.uppercased()
        if t.hasSuffix(".KS") || t.hasSuffix(".KQ") { return String(t.dropLast(3)) }
        if t.contains(".") || t.contains("=") || t.hasPrefix("^") { return nil }
        return t
    }
    /// 앱 이름 → 사이트 티커
    static func siteTicker(_ id: String) -> String {
        guard let s = Sample.symbol(id) else { return id }
        return s.currency == .krw ? CustomSymbols.yahoo(for: s) : id
    }

    private func outgoing(_ m: AppModel) -> [String: Any] {
        var st = siteState
        if st["version"] == nil { st["version"] = 1 }
        let old = (st["holdings"] as? [[String: Any]]) ?? []
        // 앱이 못 다루는 사이트 종목은 그대로 두고, 나머지는 앱 값으로
        var hs = old.filter { h in (h["ticker"] as? String).flatMap(Self.appId) == nil }
        for h in m.holdings where h.symbol != "DRNK" {
            let t = Self.siteTicker(h.symbol)
            var row = old.first { ($0["ticker"] as? String)?.uppercased() == t.uppercased() } ?? ["note": "", "price": NSNull()]
            row["ticker"] = t; row["shares"] = h.qty; row["avg_cost"] = h.avg
            hs.append(row)
        }
        st["holdings"] = hs
        // 앱만 쓰는 칸: 미션 진행과 가상 종목(DRNK). 사이트는 이 칸을 읽지 않고 그대로 둔다
        var app: [String: Any] = ["v": 1]
        if let p = try? JSONSerialization.jsonObject(with: JSONEncoder().encode(m.progress)) { app["progress"] = p }
        app["virtual"] = m.holdings.filter { $0.symbol == "DRNK" }.map { ["t": $0.symbol, "q": $0.qty, "a": $0.avg] }
        st["app"] = app
        st.removeValue(forKey: "ui")
        st.removeValue(forKey: "_w")
        return st
    }

    @MainActor
    private func apply(_ st: [String: Any], at: Double, to m: AppModel) async throws {
        var hs: [Holding] = []
        var unknown: [StockHit] = []
        for h in (st["holdings"] as? [[String: Any]]) ?? [] {
            guard let t = h["ticker"] as? String, let id = Self.appId(t) else { continue }
            let q = (h["shares"] as? Double) ?? Double(h["shares"] as? String ?? "") ?? 0
            let a = (h["avg_cost"] as? Double) ?? Double(h["avg_cost"] as? String ?? "") ?? 0
            guard q > 0 else { continue }
            let kr = t.uppercased().hasSuffix(".KS") || t.uppercased().hasSuffix(".KQ")
            if CustomSymbols.shared.symbol(id) == nil && YahooSample.quotes[id] == nil {
                let ko = StockCatalog.all.first { $0.id == id }
                unknown.append(StockHit(id: id, yahoo: kr ? t.uppercased() : id, name: ko?.name ?? Sample.symbols.first { $0.id == id }?.name ?? id,
                                        market: ko?.market ?? (kr ? (t.uppercased().hasSuffix(".KQ") ? "코스닥" : "코스피") : "미국 주식"),
                                        currency: kr ? .krw : .usd))
            }
            // 평균 단가가 없으면(사이트는 선택 항목) 지금 가격으로 둔다
            hs.append(Holding(symbol: id, qty: q, avg: a))
        }
        // 처음 보는 종목은 시세·3년 종가를 받는다 (못 받으면 그 종목은 빼고 알린다)
        var skipped: [String] = []
        for u in unknown {
            do { try await CustomSymbols.shared.fetch(u) } catch { skipped.append(u.id); hs.removeAll { $0.symbol == u.id } }
        }
        hs = hs.map { h in
            guard h.avg <= 0, let s = Sample.symbol(h.symbol) else { return h }
            return Holding(symbol: h.symbol, qty: h.qty, avg: s.last)
        }
        let app = st["app"] as? [String: Any]
        if !m.onboarded && app?["progress"] == nil {
            // 사이트에서만 쓰던 값: 미션은 처음부터. 이익이면 목표 루트, 손실이면 회복 루트
            let cost = hs.reduce(0.0) { a, h in a + (Sample.symbol(h.symbol).map { m.krw($0, h.qty * h.avg) } ?? 0) }
            let val = hs.reduce(0.0) { a, h in a + (Sample.symbol(h.symbol).map { m.krw($0, h.qty * $0.last) } ?? 0) }
            m.startRoute(cost > 0 && val >= cost ? .plus : .recover)
            // 사이트에 정한 목표가 있으면 목표 루트의 목표 금액·기간으로
            if m.isGoal, let g = st["goal"] as? [String: Any], let amt = g["amount"] as? Double, amt > 0 {
                m.gK = (amt / 1e4).rounded()
                if let ds = g["date"] as? String, let dd = Day.date(ds) {
                    m.gY = max(1, Int((dd.timeIntervalSinceNow / (365.25 * 86400)).rounded()))
                }
            }
        }
        if let app {
            for v in (app["virtual"] as? [[String: Any]]) ?? [] {
                if let t = v["t"] as? String, let q = v["q"] as? Double, let a = v["a"] as? Double { hs.insert(Holding(symbol: t, qty: q, avg: a), at: 0) }
            }
            if let p = app["progress"], let data = try? JSONSerialization.data(withJSONObject: p),
               let prog = try? JSONDecoder().decode(Progress.self, from: data) {
                m.apply(prog)
            }
        }
        m.holdings = hs
        // 새 폰에서 첫 질문 전에 불러왔으면 첫 질문은 건너뛴다 (진행이 없으면 회복 루트 미션 1부터)
        if !m.onboarded { m.onboarded = true; UserDefaults.standard.set(true, forKey: "onboarded"); m.tab = .board }
        siteState = st
        localAt = at
        lastSig = signature(m)
        if !skipped.isEmpty { state = .failed("시세를 못 받은 종목 \(skipped.joined(separator: ", "))는 사이트에만 있어요") }
    }

    /// 동기화할 값이 바뀌었는지 (종목 + 진행. 날짜 칸은 빼고)
    func signature(_ m: AppModel) -> String {
        var p = m.progress; p.lastOpen = ""
        let a = (try? JSONEncoder().encode(p)) ?? Data(), b = (try? JSONEncoder().encode(m.holdings)) ?? Data()
        return SHA256.hash(data: a + b).map { String(format: "%02x", $0) }.joined()
    }
}

/// JSON 값 하나를 Equatable 로 들고 다니기 위한 상자
struct AnyCodable: Equatable {
    let value: Any
    init(_ v: Any) { value = v }
    static func == (a: AnyCodable, b: AnyCodable) -> Bool {
        (try? JSONSerialization.data(withJSONObject: [a.value], options: .sortedKeys)) == (try? JSONSerialization.data(withJSONObject: [b.value], options: .sortedKeys))
    }
}

/// 동기화 키는 키체인에 (이 기기에서만, 백업에 안 들어감)
enum Keychain {
    private static let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.sync", kSecAttrAccount as String: "esync"]
    static func save(_ c: Sync.Creds) {
        delete()
        var a = q
        a[kSecValueData as String] = try? JSONEncoder().encode(c)
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(a as CFDictionary, nil)
    }
    static func load() -> Sync.Creds? {
        var a = q
        a[kSecReturnData as String] = true
        a[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(a as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return try? JSONDecoder().decode(Sync.Creds.self, from: d)
    }
    static func delete() { SecItemDelete(q as CFDictionary) }
}
