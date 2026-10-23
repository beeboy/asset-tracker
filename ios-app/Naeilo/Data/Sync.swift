import Foundation
import CryptoKit
import CommonCrypto
import Observation
import UIKit
import Security

// 기기 자동 동기화: 사이트(naeilo.com 설정 > 기기 자동 동기화)와 같은 방식이다 (web/app.js esDerive·syncCall).
// - 동기화 비밀번호 → PBKDF2-SHA256(소금 "naeilo-sync-v1", 30만 번) 64바이트. 앞 32바이트의 SHA-256 앞 20바이트 = 저장 위치(id),
//   뒤 32바이트 = AES-GCM 키. 비밀번호는 저장하지 않고, 키만 키체인에 둔다.
// - 중계(/esync?id=)에는 기기에서 암호화한 값 {c: "iv.암호문", at}만 둔다. 중계는 내용을 읽을 수 없다.
// - 나중에 고친 쪽이 이긴다 (at). 처음 켤 때 양쪽 다 값이 있으면 어느 쪽에 맞출지 묻는다.
// - 맞추는 것: 종목·수량·평균 단가(사이트 holdings 형식), 앱의 미션 진행(app 칸, 사이트는 건드리지 않고 그대로 둔다).
//   동네(주민 수첩·대화 기록·배경·산 물건·별사탕)도 app 칸에 (app.town).
//   목표(금액·목표일·시작일·매달 넣는 돈)는 양쪽이 같은 값을 쓴다 (나중에 고친 쪽이 이긴다. 회복 루트에서는 받기만 한다).
//   사이트에만 있는 칸(사건·모형 설정)과 앱이 못 다루는 종목(미국·한국 밖)은 받은 그대로 다시 올린다.
@Observable
final class Sync {
    static let shared = Sync()
    static let relay = "https://asset-ai.drinker.workers.dev/esync?id="
    /// 개발자 동기화: 저장소 쓰기 권한이 있는 GitHub 토큰으로 계정별 칸 (사이트에 토큰이 있으면 사이트도 이 칸을 쓴다)
    static let devRelay = "https://asset-ai.drinker.workers.dev/sync"

    /// 일반: id + k (비밀번호로 만든 칸과 키). 개발자: token (암호화 없음, 중계가 토큰으로 계정을 확인)
    struct Creds: Codable { var id: String = ""; var k: String = ""; var token: String? = nil }
    var isDev: Bool { creds?.token != nil }
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
        set { d.set(try? JSONSerialization.data(withJSONObject: newValue), forKey: "sync.site"); siteFc = nil; cashC = nil }
    }
    /// 사이트 입력값의 사건 목록·전망 설정. 앱 전망(ForecastEngine)이 그 사람의 웹과 같은 사건으로 계산한다. 동기화를 안 켰으면 nil
    @ObservationIgnored private var siteFc: (events: [MC.Event]?, model: MC.Settings?)? = nil
    var siteForecast: (events: [MC.Event]?, model: MC.Settings?) {
        guard isOn else { return (nil, nil) }
        if let c = siteFc { return c }
        let st = siteState
        func dec<T: Decodable>(_ v: Any?, _ t: T.Type) -> T? {
            guard let v, JSONSerialization.isValidJSONObject(v), let data = try? JSONSerialization.data(withJSONObject: v) else { return nil }
            return try? JSONDecoder().decode(t, from: data)
        }
        let c = (dec(st["events"], [MC.Event].self), dec(st["model"], MC.Settings.self))
        siteFc = c
        return c
    }
    /// 사이트 입력값의 현금 (원화, 달러). 앱은 입력 칸 없이 받은 값만 쓴다 (내 길·전망에 웹과 같은 식으로). 동기화를 안 켰으면 0
    @ObservationIgnored private var cashC: (krw: Double, usd: Double)? = nil
    var siteCash: (krw: Double, usd: Double) {
        guard isOn else { return (0, 0) }
        if let c = cashC { return c }
        let o = siteState["cash"] as? [String: Any]
        let c = (krw: Self.num(o?["krw"]), usd: Self.num(o?["usd"]))
        cashC = c
        return c
    }
    /// 받을 때 들여오지 않은 사이트 줄 (보낼 때 그대로 돌려보낸다)
    private var keepTickers: Set<String> {
        get { Set(d.stringArray(forKey: "sync.keep") ?? []) }
        set { d.set(Array(newValue), forKey: "sync.keep") }
    }
    /// 사이트에 단가가 없어서 지금 가격으로 채운 종목 (그대로면 사이트에는 비워 둔 채로 보낸다)
    private var subAvg: [String: Double] {
        get { (d.dictionary(forKey: "sync.subAvg") as? [String: Double]) ?? [:] }
        set { d.set(newValue, forKey: "sync.subAvg") }
    }
    static func krFromJSON(_ any: Any?) -> [MC.KrDay] {
        ((any as? [[String: Any]]) ?? []).compactMap { x in (x["d"] as? String).map { MC.KrDay(d: $0, v: num(x["v"])) } }
    }
    static func num(_ v: Any?) -> Double {
        if let x = v as? Double { return x }
        if let x = v as? Int { return Double(x) }
        if let x = v as? String { return Double(x.replacingOccurrences(of: ",", with: "")) ?? 0 }
        return 0
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

    /// 개발자 토큰으로 켠다. 중계가 저장소 쓰기 권한을 확인하고, 아니면 켜지지 않는다
    @MainActor
    func turnOnDev(token: String, model: AppModel) async {
        let t = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, t.unicodeScalars.allSatisfy({ $0.value > 0x20 && $0.value < 0x7f }) else {
            state = .failed(String(localized: "토큰에 다른 글자가 섞여 있어요. 다시 붙여넣어 주세요")); return
        }
        state = .working
        creds = Creds(token: t)
        do { _ = try await call() } catch {
            creds = nil
            state = .failed(error.localizedDescription)
            return
        }
        Keychain.save(creds!)
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
        dirty = false
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
        guard let c = creds, c.token == nil, let k = Data(base64Encoded: c.k) else { throw SyncError.off }
        return SymmetricKey(data: k)
    }

    enum SyncError: LocalizedError {
        case off, wrongPassword, server(String)
        var errorDescription: String? {
            switch self {
            case .off: String(localized: "동기화가 꺼져 있어요")
            case .wrongPassword: String(localized: "풀 수 없어요 (다른 기기와 같은 비밀번호인지 확인해 주세요)")
            case .server(let s): s
            }
        }
    }

    // MARK: 중계와 주고받기 (사이트 syncCall 과 같음)

    private func call(post: [String: Any]? = nil) async throws -> (state: [String: Any]?, at: Double, stale: Bool) {
        guard let c = creds, let url = URL(string: c.token != nil ? Self.devRelay : Self.relay + c.id) else { throw SyncError.off }
        var req = URLRequest(url: url, timeoutInterval: 20)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        if let t = c.token {
            // 개발자 칸: 암호화 없이 {state, at} (사이트 개발자 동기화와 같음)
            req.setValue("Bearer " + t, forHTTPHeaderField: "Authorization")
            if let post, let st = post["state"] {
                req.httpMethod = "POST"
                req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                req.httpBody = try JSONSerialization.data(withJSONObject: ["state": st, "at": post["at"] ?? Date().timeIntervalSince1970 * 1000])
            }
        } else if let post, let st = post["state"] {
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
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw SyncError.server((j["error"] as? String) ?? String(localized: "중계 응답 \(String(describing: (resp as? HTTPURLResponse)?.statusCode ?? 0))")) }
        let at = (j["at"] as? Double) ?? 0
        if c.token != nil, var st = j["state"] as? [String: Any] {
            st.removeValue(forKey: "_w")
            return (st, at, false)
        }
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
            // 지난 날 수량 기록은 언제나 사이트 값으로 (이 기기 값이 더 새로워 받지 않을 때도: 앱에만 남은 예전 줄이 내 길 시작값을 웹과 다르게 만든다)
            if let st = r.state, !(localAt == 0 && hasLocalData(m)) {
                let L = Lots.withSite(Lots.fromJSON(st["lots"]), m.lots)
                if L != m.lots { m.lots = L }
            }
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

    /// 이 기기 값이 바뀌었을 때: 표시만 해 두고 올리기는 앱을 닫을 때 한 번 (flush)
    private var dirty = false
    private var pulledOnce = false
    @MainActor
    func changed(_ m: AppModel) {
        // 이번에 앱을 켠 뒤 한 번은 받아 본 다음에만 보낸다 (먼저 보내면 다른 기기의 새 값을 덮을 수 있다)
        guard isOn, conflict == nil, pulledOnce else { return }
        let sig = signature(m)
        guard sig != lastSig else { return }
        localAt = Date().timeIntervalSince1970 * 1000
        dirty = true
    }

    /// 앱이 백그라운드로 갈 때 (App.swift): 바뀐 값이 있으면 한 번만 올린다. iOS가 끝낼 시간을 조금 준다
    @MainActor
    func flush(_ m: AppModel) {
        changed(m)      // 동네는 진행·종목 밖이라 닫을 때 한 번 더 본다
        guard dirty, isOn, conflict == nil, pulledOnce else { return }
        dirty = false
        // 끝날 때 자기 번호로 닫는다. 번호는 상자에 담아 넘긴다 (잡은 뒤 바꾼 변수 경고를 피하려고)
        let bg = BGTaskBox()
        bg.id = UIApplication.shared.beginBackgroundTask { UIApplication.shared.endBackgroundTask(bg.id) }
        Task { @MainActor in
            do { try await push(m) } catch { dirty = true; state = .failed(error.localizedDescription) }
            UIApplication.shared.endBackgroundTask(bg.id)
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
        let held = Set(m.holdings.map(\.symbol)), keep = keepTickers, sub = subAvg
        // 사이트 줄 순서를 지킨다: 그대로 두는 줄(앱이 못 다루는 종목, 받을 때 들여오지 않은 줄)은 그대로,
        // 앱에 있는 종목은 바뀐 칸만 고치고, 앱에서 지운 종목은 뺀다. 앱에서 새로 넣은 종목은 끝에 붙인다
        func updated(_ h: Holding, _ base: [String: Any]?) -> [String: Any] {
            var row = base ?? ["note": "", "price": NSNull()]
            if Self.num(row["shares"]) != h.qty { row["shares"] = h.qty }
            let oldAvg = Self.num(row["avg_cost"])
            if oldAvg == 0, let s = sub[h.symbol], abs(s - h.avg) < 1e-9 { /* 사이트에 단가가 없던 줄: 비워 둔 그대로 */ }
            else if oldAvg != h.avg { row["avg_cost"] = h.avg }
            row["ticker"] = (row["ticker"] as? String) ?? Self.siteTicker(h.symbol)
            // 한국 종목: 넣은 가격과 그날 (웹 표의 가격 칸과 같은 값)
            if let c = CustomSymbols.shared.saved(h.symbol), !c.usd, let p = c.kp {
                row["price"] = p; row["price_date"] = c.kd ?? Day.today
                if (row["name"] as? String ?? "").isEmpty { row["name"] = c.name }
            }
            return row
        }
        var hs: [[String: Any]] = [], used = Set<String>()
        for row in old {
            guard let t = row["ticker"] as? String, let id = Self.appId(t) else { hs.append(row); continue }
            if let h = m.holdings.first(where: { $0.symbol == id }), !used.contains(id) { hs.append(updated(h, row)); used.insert(id) }
            else if keep.contains(t.uppercased()) && !held.contains(id) { hs.append(row) }
        }
        for h in m.holdings where h.symbol != "DRNK" && !used.contains(h.symbol) {
            var row = updated(h, nil); row["ticker"] = Self.siteTicker(h.symbol); hs.append(row)
        }
        st["holdings"] = hs
        // 앱만 쓰는 칸: 미션 진행과 가상 종목(DRNK). 사이트는 이 칸을 읽지 않고 그대로 둔다
        var app: [String: Any] = ["v": 1]
        if let p = try? JSONSerialization.jsonObject(with: JSONEncoder().encode(m.progress)) { app["progress"] = p }
        app["virtual"] = m.holdings.filter { $0.symbol == "DRNK" }.map { ["t": $0.symbol, "q": $0.qty, "a": $0.avg] }
        // 동네: 주민 수첩(이름·모자·모습)·대화 기록(한 줄 답)·배경·산 물건·별사탕 (앱만 쓴다)
        if let t = Self.townOut((st["app"] as? [String: Any])?["town"]) { app["town"] = t }
        // 평행 세계의 나: 연 날·바구니·인물·본선 기록 (앱만 쓴다)
        if let w = m.pworld, let d = try? JSONEncoder().encode(w), let j = try? JSONSerialization.jsonObject(with: d) { app["parallel"] = j }
        st["app"] = app
        // 목표는 사이트와 같은 칸(goal)에. 회복 루트(본전 기준)와 사이트 샘플 중에는 사이트 목표를 그대로 둔다
        if m.isGoal, (st["sample"] as? Bool) != true { st["goal"] = m.siteGoal(st["goal"] as? [String: Any]) }
        // 내 길(회복·목표)은 웹 홈 첫 질문과 같은 칸(route)에. 목표 루트(플러스·처음)는 웹에서 "goal"
        if (st["sample"] as? Bool) != true { st["route"] = m.isGoal ? "goal" : "recover" }
        // 전망 관점(현재 정세·내 관점·과거 추세)과 추세 신뢰는 웹 전망 설정과 같은 칸(model.scenario·trust)에. 웹이 다른 시나리오(보수 등)면 그대로 둔다
        var md = st["model"] as? [String: Any] ?? [:]
        if ["blend", "base", "smooth", nil].contains(md["scenario"] as? String) {
            md["scenario"] = m.lens == .base ? "base" : m.lens == .smooth ? "smooth" : "blend"
            md["trust"] = m.trust
            st["model"] = md
        }
        // 내 기록은 사이트와 같은 칸(daily)에 날짜별로 합쳐 둔다
        let daily = MyRecord.merge(MyRecord.fromJSON(st["daily"]), m.myRecord)
        if !daily.isEmpty, let j = MyRecord.toJSON(daily) { st["daily"] = j }
        // 수량 기록은 사이트와 같은 칸(lots)에 날짜별로 합쳐 둔다 (내 길 실제 평가액을 웹과 같은 수량으로)
        // 지난 날은 사이트 값만 (앱에만 남은 예전 줄이 웹과 다른 시작값을 만들지 않게), 오늘 줄은 이 기기 값
        let lots = Lots.withSite(Lots.fromJSON(st["lots"]), m.lots)
        if !lots.isEmpty, let j = Lots.toJSON(lots) { st["lots"] = j }
        // 국내 주식 합계 기록도 같은 칸(kr_log)에 날짜별로 합친다 (같은 날은 이 기기 값)
        let kr = MC.krNorm(Self.krFromJSON(st["kr_log"]) + CustomSymbols.shared.krLog)
        if !kr.isEmpty { st["kr_log"] = kr.map { ["d": $0.d, "v": $0.v] as [String: Any] } }
        st.removeValue(forKey: "ui")
        st.removeValue(forKey: "_w")
        return st
    }

    @MainActor
    private func apply(_ st: [String: Any], at: Double, to m: AppModel) async throws {
        var hs: [Holding] = []
        var unknown: [StockHit] = []
        var keep = Set<String>(), sub: [String: Double] = [:], krPrice: [String: (Double, String?)] = [:]
        for h in (st["holdings"] as? [[String: Any]]) ?? [] {
            guard let t = h["ticker"] as? String, let id = Self.appId(t) else { continue }
            let q = Self.num(h["shares"]), a = Self.num(h["avg_cost"])
            guard q > 0 else { keep.insert(t.uppercased()); continue }
            let kr = t.uppercased().hasSuffix(".KS") || t.uppercased().hasSuffix(".KQ")
            if kr, Self.num(h["price"]) > 0 { krPrice[id] = (Self.num(h["price"]), h["price_date"] as? String) }
            if CustomSymbols.shared.symbol(id) == nil && YahooSample.quotes[id] == nil {
                let ko = StockCatalog.all.first { $0.id == id }
                unknown.append(StockHit(id: id, yahoo: kr ? t.uppercased() : id, name: ko?.name ?? Sample.symbols.first { $0.id == id }?.name ?? (h["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? id,
                                        market: ko?.market ?? (kr ? (t.uppercased().hasSuffix(".KQ") ? String(localized: "코스닥") : String(localized: "코스피")) : String(localized: "미국 주식")),
                                        currency: kr ? .krw : .usd))
            }
            // 평균 단가가 없으면(사이트는 선택 항목) 지금 가격으로 둔다
            hs.append(Holding(symbol: id, qty: q, avg: a))
        }
        // 처음 보는 종목은 시세·3년 종가를 받는다 (못 받으면 그 종목은 빼고 알린다)
        var skipped: [String] = []
        for u in unknown {
            do { try await CustomSymbols.shared.fetch(u) } catch {
                skipped.append(u.id); hs.removeAll { $0.symbol == u.id }
                keep.insert(Self.siteTicker(u.id).uppercased()); keep.insert(u.yahoo.uppercased())
            }
        }
        // 한국 종목: 웹에 넣은 가격을 그대로 (가격이 없으면 종목 화면에서 넣게 둔다)
        for (id, v) in krPrice { CustomSymbols.shared.setPrice(id, v.0, date: v.1) }
        CustomSymbols.shared.mergeKr(Self.krFromJSON(st["kr_log"]))     // 국내 주식 합계 날짜별 기록 (같은 날은 받은 쪽)
        hs = hs.map { h in
            guard h.avg <= 0, let s = Sample.symbol(h.symbol) else { return h }
            sub[h.symbol] = s.last
            return Holding(symbol: h.symbol, qty: h.qty, avg: s.last)
        }
        keepTickers = keep; subAvg = sub
        let app = st["app"] as? [String: Any]
        if !m.onboarded && app?["progress"] == nil {
            // 사이트에서만 쓰던 값: 미션은 처음부터. 이익이면 목표 루트, 손실이면 회복 루트
            let cost = hs.reduce(0.0) { a, h in a + (Sample.symbol(h.symbol).map { m.krw($0, h.qty * h.avg) } ?? 0) }
            let val = hs.reduce(0.0) { a, h in a + (Sample.symbol(h.symbol).map { m.krw($0, h.qty * $0.last) } ?? 0) }
            // 웹 홈에서 길을 이미 골랐으면 그 길로
            let web = (st["sample"] as? Bool) == true ? nil : st["route"] as? String
            m.startRoute(web == "recover" ? .recover : web == "goal" ? .plus : cost > 0 && val >= cost ? .plus : .recover)
        }
        if let app {
            for v in (app["virtual"] as? [[String: Any]]) ?? [] {
                if let t = v["t"] as? String, let q = v["q"] as? Double, let a = v["a"] as? Double { hs.insert(Holding(symbol: t, qty: q, avg: a), at: 0) }
            }
            Self.townIn(app["town"])
            if let p = app["parallel"], let data = try? JSONSerialization.data(withJSONObject: p),
               let w = try? JSONDecoder().decode(PWorld.self, from: data) {
                m.pworld = PWorld.merge(m.pworld, w)
            }
            if let p = app["progress"], let data = try? JSONSerialization.data(withJSONObject: p),
               let prog = try? JSONDecoder().decode(Progress.self, from: data) {
                m.apply(prog)
            }
        }
        // 웹 홈에서 고른 길(route)을 따른다 (진행보다 뒤에: 웹에서 바꾼 길이 진행에 든 예전 길을 덮는다). 목표는 플러스·처음 중 지금 것을 그대로
        if (st["sample"] as? Bool) != true, let r = st["route"] as? String {
            if r == "recover", m.isGoal { m.switchRoute(.recover) }
            else if r == "goal", !m.isGoal { m.switchRoute(.plus) }
        }
        // 목표(금액·목표일·시작일·매달 넣는 돈)는 받을 때마다 사이트 값으로 맞춘다 (진행보다 뒤에: 진행에 든 예전 목표를 덮는다).
        // 회복 루트는 본전이 기준이라 화면에는 안 쓰지만, 목표 루트로 넘어갈 때 이 목표를 그대로 쓴다. 사이트 샘플 목표는 받지 않는다
        if (st["sample"] as? Bool) != true, let g = st["goal"] as? [String: Any] { m.takeSiteGoal(g) }
        // 전망 관점·추세 신뢰도 웹 설정을 그대로 (진행보다 뒤에: 앱에 따로 남은 값이 웹과 다른 연 기대·확률을 만들지 않게)
        if let md = st["model"] as? [String: Any] {
            switch md["scenario"] as? String { case "base": m.lens = .base; case "smooth": m.lens = .smooth; case "blend": m.lens = .mine; default: break }
            if md["trust"] != nil { let t = Self.num(md["trust"]); if t.isFinite { m.trust = min(100, max(0, t)) } }
        }
        m.myRecord = MyRecord.merge(m.myRecord, MyRecord.fromJSON(st["daily"]))
        m.lots = Lots.withSite(Lots.fromJSON(st["lots"]), m.lots)    // 종목보다 먼저: 종목을 바꾸면 수량 기록이 오늘 줄을 남긴다
        m.holdings = hs
        // 새 폰에서 첫 질문 전에 불러왔으면 첫 질문은 건너뛴다 (진행이 없으면 회복 루트 미션 1부터)
        if !m.onboarded { m.onboarded = true; UserDefaults.standard.set(true, forKey: "onboarded"); m.tab = .board }
        siteState = st
        localAt = at
        lastSig = signature(m)
        if !skipped.isEmpty { state = .failed(String(localized: "시세를 못 받은 종목 \(String(describing: skipped.joined(separator: ", ")))는 사이트에만 있어요")) }
    }

    // MARK: 동네 (app.town)
    private static let townData = [("town.book", "book"), ("town.log", "log")]

    /// 이 기기 동네 값. 하나도 없으면 받은 값(old)을 그대로 다시 올린다 (새 폰이 빈 동네로 덮지 않게)
    static func townOut(_ old: Any?) -> Any? {
        let d = UserDefaults.standard
        var t: [String: Any] = [:]
        for (k, n) in townData { if let x = d.data(forKey: k), let j = try? JSONSerialization.jsonObject(with: x) { t[n] = j } }
        let bg = d.integer(forKey: "town.bg"), owned = d.string(forKey: "town.owned") ?? "", candy = d.integer(forKey: "town.candy")
        if t.isEmpty && bg == 0 && owned.isEmpty && candy == 0 { return old }
        t["bg"] = bg; t["owned"] = owned; t["candy"] = candy
        return t
    }
    /// 받은 동네 값을 이 기기에 (나중에 고친 쪽이 이긴다)
    @MainActor static func townIn(_ v: Any?) {
        guard let t = v as? [String: Any] else { return }
        let d = UserDefaults.standard
        for (k, n) in townData { if let j = t[n], let x = try? JSONSerialization.data(withJSONObject: j) { d.set(x, forKey: k) } }
        if t["bg"] != nil { d.set(Int(num(t["bg"])), forKey: "town.bg") }
        if let o = t["owned"] as? String { d.set(o, forKey: "town.owned") }
        if t["candy"] != nil { d.set(Int(num(t["candy"])), forKey: "town.candy") }
        TownBook.shared.reload(); TownLog.shared.reload()
    }
    /// 바뀜 감지용 동네 값 (수첩·기록 원문 + 배경·물건·별사탕)
    static func townStamp() -> Data {
        let d = UserDefaults.standard
        return townData.reduce(Data()) { $0 + (d.data(forKey: $1.0) ?? Data()) }
            + Data("|\(d.integer(forKey: "town.bg"))|\(d.string(forKey: "town.owned") ?? "")|\(d.integer(forKey: "town.candy"))".utf8)
    }

    /// 동기화할 값이 바뀌었는지 (종목 + 진행 + 내 기록 날짜 수. 날짜 칸은 빼고)
    func signature(_ m: AppModel) -> String {
        var p = m.progress; p.lastOpen = ""
        let a = (try? JSONEncoder().encode(p)) ?? Data(), b = (try? JSONEncoder().encode(m.holdings)) ?? Data()
        let pw = m.pworld.map { "\($0.days.count)|\($0.segs.count)|\($0.segs.last?.from ?? "")|\($0.who)|\($0.opened)" } ?? "-"
        let c = Data("\(m.myRecord.count)|\(m.myRecord.last?.d ?? "")|\(CustomSymbols.shared.krStamp)|\(pw)".utf8) + Self.townStamp()   // 내 기록은 새 날이 생길 때만 보낸다 (같은 날 시세 변화로는 안 보냄)
        return SHA256.hash(data: a + b + c).map { String(format: "%02x", $0) }.joined()
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

/// 백그라운드 작업 번호 상자 (Sync.flush). 메인에서만 읽고 쓴다
final class BGTaskBox: @unchecked Sendable { var id = UIBackgroundTaskIdentifier.invalid }
