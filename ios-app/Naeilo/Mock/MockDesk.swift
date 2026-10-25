#if MOCK
import Foundation
import Observation

/// 모의투자 저장 상태. 기록 줄은 따로 journal.jsonl 에 차례로 쌓는다
struct MockState: Codable {
    var ledgers: [MockLedger]                 // 지금 장부 (규칙·AI 하나씩)
    var archived: [MockLedger] = []           // 처음부터 다시 한 옛 장부 (지우지 않는다)
    var bundles: [MockBundle]
    var current: UUID?                        // 관찰에 쓰는 묶음
    var exceptions = MockExceptions()
    var select = MockSelectRule()
    var sell = MockSellRule()
    var fees = MockFees()
    var scan: MockScan? = nil
    var scanEvent: UUID? = nil
    var picks: [MockCalc.Pick] = []           // 검토 대기 중인 선정 (검토 문이 열려 있으면 바로 주문해서 비어 있다)
    var pickEvents: [String: UUID] = [:]      // 종목 → 선정 기록 id
    var skips: [MockCalc.Skip] = []
    var pickedAt: Date? = nil
    var exceptHits: [String: String] = [:]    // 조건 예외에 걸린 종목 (마지막 훑기 기준)
    var runs: [String: MockRun] = [:]         // observe · select · order · ai
    var auto: Int = 0                         // 한 바퀴 자동 (분, 0 = 끔). 장중·앱이 열려 있을 때만
    var prices: [String: Double] = [:]        // 지켜보는 종목 지금 가격
    var pricesAt: Date? = nil
    var session: MockFeed.Session? = nil
    var shown: MockBook = .rule               // 요약 박스에 보이는 장부
    var ai = MockAISettings()
    var aiSeen: [String: Date] = [:]          // AI 부르는 때마다 마지막으로 부른 시각
    var aiWallet: MockAIWallet? = nil         // 서버가 알려 준 쓴 돈
    var aiViews: [String: MockAIView] = [:]   // 마지막 선정에 대한 AI 의견 (종목 → 의견)
    var aiNote: String? = nil                 // AI 장부 마지막 판단 한 줄
    var aiDaily: String? = nil                // 장 마감 뒤 하루 정리
    var aiDailyDay: String? = nil
    var reviewShut: Bool? = nil               // 검토 문 기본을 닫힘으로 바꾼 뒤 옛 상태를 한 번 닫았는지
    var conds: [MockCond]? = nil              // 관찰 조건 알약 (옛 묶음 조각을 옮겨 온다)
    var rconds: [MockRuleCond]? = nil         // 고르기 조건 알약 (select 값은 여기서 맞춘다)
    var sconds: [MockSellCond]? = nil         // 팔기 조건 알약 (sell 값은 여기서 맞춘다)
    var marks: [MockMark]? = nil              // 평가액 점 (내 수익률 꺾은선)
    var dropped: [MockCalc.Pick]? = nil       // 마지막 고르기에서 손으로 뺀 종목
    var aiPurged: Bool? = nil                 // AI 장부 매매 데이터를 한 번 지웠는지 (2026-10-25)
    var surgeAdded: Bool? = nil               // 폭등 거르기 기본 조건을 한 번 넣었는지 (2026-10-25)

    static func fresh(_ now: Date) -> MockState {
        let a = MockBundle(name: "A · 거래량 급증", pieces: [MockPiece(kind: .volume, lo: 2, days: 20), MockPiece(kind: .change, lo: 2, hi: 8), MockPiece(kind: .ma, days: 20)])
        let b = MockBundle(name: "B · 20일선 위", pieces: [MockPiece(kind: .ma, days: 20), MockPiece(kind: .amount, lo: 10)], sort: .amount)
        return MockState(ledgers: [MockLedger(book: .rule, started: now), MockLedger(book: .ai, started: now)], bundles: [a, b], current: a.id)
    }
}

/// 모의투자 실행기. 장부를 바꾸는 곳은 여기 하나뿐이다 (두 곳이 같은 장부를 고치지 않게). 화면은 읽기만 한다
@MainActor @Observable
final class MockDesk {
    static let shared = MockDesk()

    /// 투자 모드 (홈 평가액 박스 금액 글자 밖을 누르면 바뀐다)
    var on: Bool = UserDefaults.standard.bool(forKey: "mock.on") {
        didSet { UserDefaults.standard.set(on, forKey: "mock.on") }
    }
    /// 개발자 기기에서만 켜진다
    var active: Bool { on && Support.shared.dev }

    private(set) var s: MockState
    private(set) var events: [MockEvent]
    /// 지금 도는 일 ("observe" 등). 하나가 도는 동안 다른 일은 받지 않는다
    private(set) var busy: String? = nil
    /// 1분 주기가 도는 중 (busy 와 따로: 버튼을 잠그지 않는다)
    private(set) var ticking = false
    /// AI 부르는 중 (서버가 Claude 를 기다리는 동안 다른 일은 막지 않는다. AI 끼리는 하나씩)
    private(set) var aiBusy: String? = nil
    private(set) var progress: (done: Int, total: Int)? = nil
    /// 앱에 넣은 내 보유 종목 (예외 조건 '내 보유 종목 전부')
    var mine: Set<String> = []
    /// 마지막으로 고른 종목 (검토 문이 열려 있으면 picks 가 바로 비므로 AI 의견용으로 따로 둔다)
    private(set) var lastPicks: [MockCalc.Pick] = []

    private init() {
        let now = Date()
        s = Self.loadState() ?? MockState.fresh(now)
        events = Self.loadEvents()
        // 옛 상태는 검토 문이 열린 채 저장돼 있다. 기본이 닫힘으로 바뀌어 한 번만 닫는다
        if s.reviewShut == nil { s.select.review = true; s.reviewShut = true; save() }
        migrate()
        if s.aiPurged != true { purgeAI() }
    }

    /// 옛 묶음 조각을 관찰 조건 알약으로, 선정 규칙 값을 고르기 조건 알약으로 옮긴다 (한 번만, 묶음 데이터는 그대로 둔다)
    private func migrate() {
        var changed = false
        if s.conds == nil {
            let cur = s.bundles.first { $0.id == s.current } ?? s.bundles.first
            var seen = Set<String>(), out: [MockCond] = []
            for b in (cur.map { [$0] } ?? []) + s.bundles.filter({ $0.id != cur?.id }) {
                for p in b.pieces where !seen.contains(p.text) {
                    seen.insert(p.text)
                    out.append(MockCond(piece: p, on: b.id == cur?.id))
                }
            }
            s.conds = out
            changed = true
        }
        if s.rconds == nil {
            let cur = s.bundles.first { $0.id == s.current } ?? s.bundles.first
            s.rconds = [MockRuleCond(kind: .top, sort: cur?.sort ?? .volume, n: s.select.count),
                        MockRuleCond(kind: .per, pct: s.select.maxPer),
                        MockRuleCond(kind: .cash, pct: s.select.keepCash)]
            changed = true
        }
        if s.sconds == nil {
            // 설정에 있던 팔기 규칙을 알약으로 옮긴다 (꺼져 있던 것은 꺼진 알약으로)
            s.sconds = [MockSellCond(kind: .loss, on: s.sell.stopLoss != nil, pct: s.sell.stopLoss ?? 0.05),
                        MockSellCond(kind: .gain, on: s.sell.takeProfit != nil, pct: s.sell.takeProfit ?? 0.10),
                        MockSellCond(kind: .days, on: s.sell.maxDays != nil, n: s.sell.maxDays ?? 5)]
            changed = true
        }
        // 비정상 폭등 거르기 (일봉 거래량·종가로만 셈). 관찰은 넓게, 고르기는 더 좁게. 켠 채로 한 번만 넣는다
        if s.surgeAdded != true {
            s.conds = (s.conds ?? []) + [MockCond(piece: MockPiece(kind: .volume, hi: 50, days: 20)),
                                         MockCond(piece: MockPiece(kind: .rise, hi: 30, days: 5))]
            s.rconds = (s.rconds ?? []) + [MockRuleCond(kind: .cap, capKind: .volume, lim: 20),
                                           MockRuleCond(kind: .cap, capKind: .rise, lim: 20)]
            s.surgeAdded = true
            changed = true
        }
        if changed { save() }
    }

    /// AI 장부의 매매 데이터와 AI 기록을 지운다 (규칙 장부는 그대로). 기록 파일도 그 줄만 빼고 다시 쓴다
    private func purgeAI() {
        let now = Date()
        let aiIds = Set(s.ledgers.filter { $0.book == .ai }.map(\.id) + s.archived.filter { $0.book == .ai }.map(\.id))
        if let i = s.ledgers.firstIndex(where: { $0.book == .ai }) { s.ledgers[i] = MockLedger(book: .ai, started: now) }
        s.archived.removeAll { $0.book == .ai }
        s.aiNote = nil; s.aiDaily = nil; s.aiDailyDay = nil; s.aiViews = [:]; s.aiSeen = [:]; s.aiWallet = nil
        s.runs["ai"] = nil
        s.shown = .rule
        let kept = events.filter { !aiIds.contains($0.ledger) && $0.book != .ai && $0.kind != .ai && $0.who != .ai }
        if kept.count != events.count {
            events = kept
            Self.rewriteLines(kept)
        }
        s.aiPurged = true
        save()
    }

    /// 상태 파일 꼴이 바뀌어 못 읽으면 그대로 두지 않고 옆에 옮겨 둔다 (새 상태가 옛 장부를 덮지 않게)
    private static func keepBroken() {
        let f = dir.appendingPathComponent("state.json")
        guard FileManager.default.fileExists(atPath: f.path) else { return }
        let to = dir.appendingPathComponent("state-unread-\(Int(Date().timeIntervalSince1970)).json")
        try? FileManager.default.moveItem(at: f, to: to)
    }

    // MARK: 읽기

    func ledger(_ b: MockBook) -> MockLedger { s.ledgers.first { $0.book == b } ?? MockLedger(book: b, started: Date()) }
    /// 관찰에 쓰는 묶음: 켠 조건 알약 모두 (줄 세우기는 고르기 조건의 순서)
    var bundle: MockBundle? {
        let on = conds.filter(\.on).map(\.piece)
        let top = rconds.first { $0.on && $0.kind == .top }
        return MockBundle(id: Self.obsId, name: "조건 \(on.count)개", pieces: on, scope: .all, sort: top?.sort ?? .volume, sortDays: 20)
    }
    static let obsId = UUID(uuidString: "00000000-0000-0000-0000-0000000000B5")!
    var conds: [MockCond] { s.conds ?? [] }
    var rconds: [MockRuleCond] { s.rconds ?? [] }
    var sconds: [MockSellCond] { s.sconds ?? [] }
    var dropped: [MockCalc.Pick] { s.dropped ?? [] }
    var marks: [MockMark] { let id = ledger(.rule).id; return (s.marks ?? []).filter { $0.ledger == id } }
    func equity(_ b: MockBook) -> Double { MockCalc.equity(ledger(b), prices: s.prices) }
    func ledgerEvents(_ b: MockBook) -> [MockEvent] { let id = ledger(b).id; return events.filter { $0.ledger == id } }

    /// 예외 종목 → 이유. 직접 넣은 것, 내 보유 종목 전부, 조건 묶음에 걸린 것
    func excluded(extra: [String: String]? = nil) -> [String: String] {
        var out = extra ?? s.exceptHits
        if s.exceptions.mine { for c in mine where out[c] == nil { out[c] = "내 보유 종목" } }
        for d in s.exceptions.direct { out[d.code] = "직접 · " + d.name }
        return out
    }

    static let universe: [MockStock] = KrxList.all.map { MockStock(code: $0.hit.id, name: $0.hit.name, kospi: $0.hit.market == "코스피") }
    static func name(_ code: String) -> String { KrxList.name(code) ?? code }

    // MARK: 장 상태

    enum Phase { case open, closed, unknown }
    private(set) var phase: Phase = .unknown

    /// 장 일정을 10분마다 다시 받는다. 못 받으면 '확인 못 함' (새로 사지 않는다)
    @discardableResult
    func refreshPhase(now: Date = Date()) async -> Phase {
        let today = MockFmt.day(now)
        let stale = s.session.map { $0.day != today || now.timeIntervalSince($0.got) > 600 } ?? true
        if stale, let ss = try? await MockFeed.calendar() { s.session = ss }
        // 받은 지 30분이 넘었는데 새로 못 받았으면 모른다고 둔다
        guard let ss = s.session, now.timeIntervalSince(ss.got) < 1800 else { phase = .unknown; return phase }
        if ss.day == today, let o = ss.open, let c = ss.close, now >= o, now < c { phase = .open } else { phase = .closed }
        return phase
    }

    // MARK: 관찰

    func runObserve() async {
        await waitTick()
        guard busy == nil else { return }
        busy = "observe"
        await observe()
        busy = nil
        save()
    }

    private func observe() async {
        guard let b = bundle, !b.pieces.isEmpty else {
            s.runs["observe"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) 켠 조건이 없어요")
            return
        }
        defer { progress = nil }
        let t0 = Date(), stocks = Self.universe, codes = stocks.map(\.code)
        progress = (0, codes.count)
        let need = max(61, ([b] + s.exceptions.bundles).map(\.need).max() ?? 0)
        do {
            let got = try await MockFeed.daily(codes, count: min(200, need), progress: { [weak self] n in self?.progress = (n, codes.count) })
            let exHits = MockCalc.exceptionHits(s.exceptions.bundles, stocks: stocks, bars: got.bars)
            let ex = excluded(extra: exHits)
            let scan = MockCalc.scan(b, stocks: stocks, bars: got.bars, excluded: Set(ex.keys), at: Date(), seconds: Date().timeIntervalSince(t0))
            let dropText = scan.dropped.sorted { $0.key < $1.key }.map { (k, n) in (k < 0 ? "자료 없음" : (k < scan.pieceTexts.count ? scan.pieceTexts[k] : "?")) + " \(n)" }.joined(separator: " · ")
            let ev = log(.scan, book: .rule, text: "\(b.name) · 걸린 \(scan.hits.count) / \(scan.total) · 기준 \(scan.barDay)" + (dropText.isEmpty ? "" : " · 떨어진 " + dropText), bundle: b.name)
            if !scan.excluded.isEmpty {
                let why = Dictionary(grouping: scan.excluded, by: { (ex[$0] ?? "").components(separatedBy: " · ").first ?? "" }).map { "\($0.key) \($0.value.count)" }.joined(separator: " · ")
                log(.exclude, book: .rule, text: "예외로 뺀 \(scan.excluded.count) · " + why, bundle: b.name, ref: ev.id)
            }
            s.scan = scan; s.scanEvent = ev.id; s.exceptHits = exHits
            for h in scan.hits { s.prices[h.code] = h.price }
            let errN = got.errors.count
            s.runs["observe"] = MockRun(at: Date(), ok: errN < codes.count / 10,
                                        text: "\(MockFmt.time(Date())) 훑음 · \(Int(scan.seconds))초 · 일봉 기준 \(scan.barDay)" + (errN > 0 ? " · 못 받은 \(errN)" : ""))
        } catch {
            let why = (error as? MockFeed.Fail)?.why ?? error.localizedDescription
            s.runs["observe"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) 실패 · " + why)
            log(.error, book: .rule, text: "관찰 실패 · " + why)
        }
    }

    // MARK: 선정

    func runSelect() async {
        await waitTick()
        guard busy == nil else { return }
        busy = "select"
        await select()
        busy = nil
        save()
        startAIReview()
    }

    /// 지금 팔기: 내 종목을 지금 가격으로 팔기 조건에 대 본다 (지금 고르기의 반대)
    func runSell() async {
        await waitTick()
        guard busy == nil else { return }
        busy = "sell"
        await sellNow()
        busy = nil
        save()
    }

    private func sellNow() async {
        let l = ledger(.rule)
        guard s.sell.on else { s.runs["sell"] = MockRun(at: Date(), ok: false, text: "켠 팔기 조건이 없어요"); return }
        guard !l.positions.isEmpty else { s.runs["sell"] = MockRun(at: Date(), ok: true, text: "팔 종목이 없어요"); return }
        if let p = try? await MockFeed.last(Array(l.positions.keys).sorted()) { s.prices.merge(p) { _, b in b }; s.pricesAt = Date() }
        let sigs = MockCalc.sellSignals(l, rule: s.sell, prices: s.prices, excluded: excluded(), today: MockFmt.day(Date()))
        var n = 0
        for sig in sigs {
            let ref = log(.sell, book: .rule, who: .rule, code: sig.code, name: Self.name(sig.code), text: sig.why, ref: ledger(.rule).positions[sig.code]?.buyRef).id
            if await placeSell(.rule, code: sig.code, who: .rule, reason: sig.why, ref: ref) { n += 1 }
        }
        s.runs["sell"] = MockRun(at: Date(), ok: n == sigs.count, text: MockFmt.stamp(Date()) + (sigs.isEmpty ? " · 조건에 맞는 종목 없음" : " · \(n)종목 팔기 주문"))
    }

    /// 고르고, 검토 문이 열려 있으면(모의 기본) 바로 주문한다
    private func select() async {
        guard let scan = s.scan else { s.runs["select"] = MockRun(at: Date(), ok: false, text: "관찰 결과 없음 · 먼저 훑어 주세요"); return }
        // 고르기 직전 가격 (위쪽 200종목만)
        let top = Array(scan.hits.prefix(200).map(\.code))
        if let p = try? await MockFeed.last(top) { s.prices.merge(p) { _, b in b }; s.pricesAt = Date() }
        // 고르기 조건의 폭등 거르기: 넘는 종목은 순위에서 빼고 이유를 남긴다
        let caps = rconds.filter { $0.on && $0.kind == .cap }
        var sc = scan, capped: [MockCalc.Skip] = []
        sc.hits = scan.hits.filter { h in
            guard let c = caps.first(where: { $0.over(h) }) else { return true }
            capped.append(MockCalc.Skip(code: h.code, name: h.name, why: "폭등 거름 · " + c.text))
            return false
        }
        let (picks, rest) = MockCalc.select(sc, ledger: ledger(.rule), rule: s.select, fees: s.fees, prices: s.prices, excluded: excluded())
        let skips = capped + rest
        var pe: [String: UUID] = [:]
        for p in picks {
            let e = log(.select, book: .rule, who: .rule, code: p.code, name: p.name,
                        text: p.why + " · 비중 " + String(format: "%.0f%%", p.weight * 100) + " · \(p.qty)주 남짓", bundle: scan.bundleName, ref: s.scanEvent)
            pe[p.code] = e.id
        }
        for k in skips.prefix(30) where k.short || k.why.hasPrefix("예외") || k.why.hasPrefix("0주") || k.why.hasPrefix("폭등") {
            log(k.short ? .short : .exclude, book: .rule, who: .rule, code: k.code, name: k.name, text: k.why, bundle: scan.bundleName, ref: s.scanEvent)
        }
        s.picks = picks; s.pickEvents = pe; s.skips = skips; s.pickedAt = Date(); s.dropped = []
        s.aiViews = [:]
        lastPicks = picks
        let shortN = skips.filter(\.short).count
        s.runs["select"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) 고름 · \(picks.count)종목 · 현금 부족 \(shortN)건")
        if !s.select.review { await order() }
    }

    /// 검토하고 빼기 (손으로 고친 것으로 남긴다)
    func dropPick(_ code: String) {
        guard let i = s.picks.firstIndex(where: { $0.code == code }) else { return }
        let p = s.picks.remove(at: i)
        s.dropped = (s.dropped ?? []) + [p]
        log(.select, book: .rule, who: .hand, code: p.code, name: p.name, text: "검토에서 뺌", ref: s.pickEvents[p.code])
        save()
    }

    /// 검토 중인 종목의 주수를 손으로 고친다 (쓸 돈도 그 주수만큼)
    func setPickQty(_ code: String, _ qty: Int) {
        guard let i = s.picks.firstIndex(where: { $0.code == code }), qty >= 1, s.picks[i].qty != qty else { return }
        var p = s.picks[i]
        p.qty = qty
        p.budget = Double(qty) * p.price * (1 + s.fees.commission)
        p.who = .hand
        s.picks[i] = p
        log(.select, book: .rule, who: .hand, code: p.code, name: p.name, text: "검토에서 \(qty)주로 고침", ref: s.pickEvents[p.code])
        save()
    }

    /// 고른 종목을 주문한다 (검토가 끝났거나 검토 문이 열려 있을 때)
    func orderPicks() async {
        await waitTick()
        guard busy == nil else { return }
        busy = "order"
        await order()
        busy = nil
        save()
    }

    private func order() async {
        guard !s.picks.isEmpty else { return }
        let picks = s.picks
        s.picks = []
        var n = 0
        for p in picks where await placeBuy(.rule, code: p.code, name: p.name, budget: p.who == .hand ? nil : p.budget, qty: p.who == .hand ? p.qty : nil,
                                            who: p.who, reason: p.why, ref: s.pickEvents[p.code]) { n += 1 }
        s.runs["order"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) 주문 \(n)건 · 고른 \(picks.count)")
    }

    /// 한 바퀴: 훑기 → 고르기 (검토 문이 열려 있으면 주문까지)
    func runCycle() async {
        await waitTick()
        guard busy == nil else { return }
        busy = "cycle"
        await observe()
        if s.runs["observe"]?.ok == true { await select() }
        busy = nil
        save()
        startAIReview()
    }

    /// 손 주문 (규칙 장부에 '손'으로 적는다. 예외 종목도 막지 않는다)
    @discardableResult
    func runHand(_ side: MockSide, code: String, name: String, qty: Int?, type: MockOrderType, limit: Double?) async -> Bool {
        await waitTick()
        guard busy == nil else { return false }
        busy = "hand"
        let ok = await side == .buy
            ? placeBuy(.rule, code: code, name: name, qty: qty, type: type, limit: limit, who: .hand, reason: "손 주문", ref: nil)
            : placeSell(.rule, code: code, qty: qty, type: type, limit: limit, who: .hand, reason: "손 주문")
        busy = nil
        save()
        return ok
    }

    // MARK: 주문

    /// 매수 한 건. budget 이나 qty 중 하나. 성공하면 참 (대기로 들어가도 참)
    @discardableResult
    func placeBuy(_ b: MockBook, code: String, name: String, budget: Double? = nil, qty: Int? = nil,
                  type: MockOrderType = .market, limit: Double? = nil, who: MockWho, reason: String, ref: UUID?) async -> Bool {
        let ph = await refreshPhase()
        guard ph != .unknown else { log(.error, book: b, who: who, code: code, name: name, text: "장 일정 확인 못 함 · 새로 사지 않음", ref: ref); return false }
        let test = ph == .closed
        // 주문 직전에 예외를 또 한 번 막는다 (손 주문은 막지 않는다)
        if who != .hand, let why = excluded()[code] { log(.exclude, book: b, who: who, code: code, name: name, text: "주문 직전 예외 · " + why, ref: ref); return false }
        if ledger(b).pending.contains(where: { $0.code == code }) { log(.order, book: b, who: who, code: code, name: name, text: "같은 종목 주문 대기 중 · 안 냄", ref: ref); return false }
        if who != .hand && ledger(b).positions[code] != nil { log(.order, book: b, who: who, code: code, name: name, text: "이미 보유 · 안 냄", ref: ref); return false }
        var book: MockCalc.Book
        do { book = try await MockFeed.book(code) }
        catch { log(.error, book: b, who: who, code: code, name: name, text: "호가 확인 못 함 · " + ((error as? MockFeed.Fail)?.why ?? ""), ref: ref); return false }
        if test && book.asks.isEmpty, let px = s.prices[code] { book = MockCalc.Book(asks: [.init(p: px, v: 1e12)], bids: book.bids, at: book.at) }
        guard let ask = book.bestAsk else { log(.error, book: b, who: who, code: code, name: name, text: "매도 호가 없음 · 안 냄", ref: ref); return false }
        // 기다리는 동안 장부가 바뀌었을 수 있으니 호가를 받은 뒤에 다시 읽는다
        guard var l = s.ledgers.first(where: { $0.book == b }), !l.pending.contains(where: { $0.code == code }) else { return false }
        let lim = limit.map { MockCalc.snap($0, side: .buy) }
        let px = type == .limit ? (lim ?? ask) : ask
        var q = qty ?? Int(((budget ?? 0) / (px * (1 + s.fees.commission))).rounded(.down))
        // 묶을 돈이 현금보다 크면 살 수 있는 만큼으로 줄인다
        let mult = type == .market ? 1.02 : 1
        q = min(q, Int((l.cash / (px * mult * (1 + s.fees.commission))).rounded(.down)))
        guard q >= 1 else {
            log(l.cash < px ? .short : .order, book: b, who: who, code: code, name: name,
                text: l.cash < px ? "현금 부족 · 남은 " + MockFmt.won(l.cash) : "0주 · 주문 안 냄 (정상)", test: test, ref: ref)
            return false
        }
        let r = MockCalc.reserve(type: type, qty: q, limit: lim, bestAsk: ask, fees: s.fees, cash: l.cash)
        let o = MockOrder(code: code, name: name, side: .buy, type: type, qty: q, limit: lim, at: Date(), who: who, reason: reason, reserved: r, ref: ref)
        l.cash -= r
        l.pending.append(o)
        set(l)
        log(.order, id: o.id, book: b, who: who, code: code, name: name,
            text: "\(type.name) \(q)주" + (lim.map { " · " + MockFmt.won($0) } ?? "") + " · 묶은 돈 " + MockFmt.won(r) + (reason.isEmpty ? "" : " · " + reason), test: test, side: .buy, qty: q, ref: ref)
        fill(b, orderId: o.id, book: book, test: test)
        return true
    }

    /// 매도 한 건. qty 없으면 팔 수 있는 만큼 전부
    @discardableResult
    func placeSell(_ b: MockBook, code: String, qty: Int? = nil, type: MockOrderType = .market, limit: Double? = nil,
                   who: MockWho, reason: String, ref: UUID? = nil) async -> Bool {
        guard let p = ledger(b).positions[code] else { return false }
        let selling = ledger(b).pending.filter { $0.code == code && $0.side == .sell }.reduce(0) { $0 + $1.qty }
        let q = min(qty ?? p.qty, p.qty - selling)
        guard q >= 1 else { return false }
        let ph = await refreshPhase()
        let test = ph != .open
        var book: MockCalc.Book
        do { book = try await MockFeed.book(code) }
        catch { log(.error, book: b, who: who, code: code, name: p.name, text: "호가 확인 못 함 · 팔기 미룸 · " + ((error as? MockFeed.Fail)?.why ?? "")); return false }
        if test && book.bids.isEmpty, let px = s.prices[code] { book = MockCalc.Book(asks: book.asks, bids: [.init(p: px, v: 1e12)], at: book.at) }
        let lim = limit.map { MockCalc.snap($0, side: .sell) }
        guard var l = s.ledgers.first(where: { $0.book == b }), let p2 = l.positions[code],
              p2.qty - l.pending.filter({ $0.code == code && $0.side == .sell }).reduce(0, { $0 + $1.qty }) >= q else { return false }
        let o = MockOrder(code: code, name: p.name, side: .sell, type: type, qty: q, limit: lim, at: Date(), who: who, reason: reason, ref: ref ?? p.buyRef)
        l.pending.append(o)
        set(l)
        log(.order, id: o.id, book: b, who: who, code: code, name: p.name,
            text: "\(type.name) 매도 \(q)주" + (lim.map { " · " + MockFmt.won($0) } ?? "") + (reason.isEmpty ? "" : " · " + reason), test: test, side: .sell, qty: q, ref: o.ref)
        fill(b, orderId: o.id, book: book, test: test)
        return true
    }

    /// 대기 주문 하나를 호가로 채워 본다. 남으면 왜 기다리는지 (바뀌었을 때만) 남긴다
    private func fill(_ b: MockBook, orderId: UUID, book: MockCalc.Book, test: Bool) {
        guard var l = s.ledgers.first(where: { $0.book == b }), let i = l.pending.firstIndex(where: { $0.id == orderId }) else { return }
        let o = l.pending[i]
        let f = MockCalc.walk(side: o.side, type: o.type, qty: o.qty, limit: o.limit, book: book, fees: s.fees, cap: o.side == .buy ? o.reserved : nil)
        if f.qty > 0 {
            let e = o.side == .buy ? MockCalc.applyBuy(&l, orderIndex: i, fill: f, fees: s.fees, at: Date(), test: test)
                                   : MockCalc.applySell(&l, orderIndex: i, fill: f, fees: s.fees, at: Date(), test: test)
            if let e { append(e) }
        }
        // 다 채우지 못했으면 대기 이유
        if let j = l.pending.firstIndex(where: { $0.id == orderId }) {
            var w = l.pending[j]
            let why: String
            if w.side == .buy && w.reserved < (book.bestAsk ?? 0) * (1 + s.fees.commission) {
                // 묶은 돈으로 한 주도 못 사면 남은 몫은 취소
                l.cash += max(0, w.reserved)
                l.pending.remove(at: j)
                set(l)
                log(.cancel, book: b, who: w.who, code: w.code, name: w.name, text: "남은 \(w.qty)주 취소 · 묶은 돈 모자람", test: test, ref: w.id)
                return
            }
            if w.type == .limit, let lim = w.limit { why = MockCalc.waitWhy(side: w.side, limit: lim, book: book) }
            else { why = "호가 잔량 모자람 · 남은 \(w.qty)주" }
            if w.waitWhy != why {
                w.waitWhy = why
                l.pending[j] = w
                log(.wait, book: b, who: w.who, code: w.code, name: w.name, text: why, test: test, ref: w.id)
            }
        }
        set(l)
    }

    func cancel(_ b: MockBook, orderId: UUID) {
        guard var l = s.ledgers.first(where: { $0.book == b }), let i = l.pending.firstIndex(where: { $0.id == orderId }) else { return }
        let o = l.pending.remove(at: i)
        l.cash += max(0, o.reserved)
        set(l)
        log(.cancel, book: b, who: .hand, code: o.code, name: o.name, text: "손으로 취소 · 남은 \(o.qty)주", ref: o.id)
        save()
    }

    // MARK: 주기 (앱이 열려 있는 동안 1분마다)

    /// 지켜보는 종목 가격 받기 → 대기 주문 다시 보기 → 팔기 규칙 → 한 바퀴 자동 → AI 차례
    func tick() async {
        guard active, busy == nil, !ticking, MockFeed.base != nil else { return }
        ticking = true
        let ph = await refreshPhase()
        var watch = Set<String>()
        for l in s.ledgers { watch.formUnion(l.positions.keys); watch.formUnion(l.pending.map(\.code)) }
        for h in (s.scan?.hits ?? []).prefix(150) { watch.insert(h.code) }
        if !watch.isEmpty, let p = try? await MockFeed.last(Array(watch).sorted()) {
            s.prices.merge(p) { _, b in b }; s.pricesAt = Date()
        }
        // 대기 주문: 지금 가격이 지정가를 넘었거나 시장가 남은 몫일 때만 호가를 묻는다
        for l in s.ledgers {
            for o in l.pending {
                let px = s.prices[o.code]
                let cross: Bool = o.type == .market || px == nil || (o.side == .buy ? px! <= (o.limit ?? 0) : px! >= (o.limit ?? .infinity))
                guard cross, let book = try? await MockFeed.book(o.code) else { continue }
                fill(l.book, orderId: o.id, book: book, test: ph != .open)
            }
        }
        // 팔기 규칙 (규칙 장부, 장중 가격)
        if ph != .unknown && s.sell.on {
            let today = MockFmt.day(Date())
            for sig in MockCalc.sellSignals(ledger(.rule), rule: s.sell, prices: s.prices, excluded: excluded(), today: today) {
                let ref = log(.sell, book: .rule, who: .rule, code: sig.code, name: Self.name(sig.code), text: sig.why, ref: ledger(.rule).positions[sig.code]?.buyRef).id
                await placeSell(.rule, code: sig.code, who: .rule, reason: sig.why, ref: ref)
            }
        }
        // 한 바퀴 자동 (장중만)
        var picked = false
        if s.auto > 0 && ph == .open, Date().timeIntervalSince(s.runs["observe"]?.at ?? .distantPast) >= Double(s.auto * 60) {
            await observe()
            await select()
            picked = true
        }
        addMark()
        ticking = false
        save()
        if picked { startAIReview() }
        startAITick(phase: ph)
    }

    /// 1분 주기는 버튼 잠금(busy)과 따로 돈다 (버튼이 잠겼다 풀리며 깜빡이지 않게). 단추 일은 주기가 끝날 때까지 기다렸다 돈다
    private func waitTick() async {
        var n = 0
        while ticking && n < 300 { try? await Task.sleep(for: .milliseconds(200)); n += 1 }
    }

    /// 평가액 점: 5분에 하나. 이틀 지난 점은 하루 마지막 하나만 남긴다
    private func addMark() {
        let l = ledger(.rule), now = Date()
        var ms = s.marks ?? []
        if let last = ms.last(where: { $0.ledger == l.id }), now.timeIntervalSince(last.at) < 300 { return }
        ms.append(MockMark(at: now, eq: equity(.rule), ledger: l.id))
        let cut = now.addingTimeInterval(-2 * 86_400)
        var lastOfDay: [String: Int] = [:]
        for (i, m) in ms.enumerated() where m.at < cut { lastOfDay[MockFmt.day(m.at) + m.ledger.uuidString] = i }
        let keep = Set(lastOfDay.values)
        ms = ms.enumerated().filter { $0.element.at >= cut || keep.contains($0.offset) }.map(\.element)
        s.marks = ms
    }

    // MARK: 장부·설정 바꾸기

    /// 장 밖 시험으로 산 몫만 장부에서 빼고 들어간 돈을 현금으로 돌린다 (기록은 남긴다)
    func dropTest(_ b: MockBook) {
        guard let i = s.ledgers.firstIndex(where: { $0.book == b }) else { return }
        var l = s.ledgers[i]
        for (code, p0) in l.positions where p0.test {
            var p = p0
            let q = min(p.qty, p0.tQty ?? p.qty), c = p0.tCost ?? p.cost, g = p0.tGross ?? p.gross
            p.qty -= q; p.cost -= c; p.gross -= g
            p.test = false; p.tQty = nil; p.tCost = nil; p.tGross = nil
            l.cash += c; l.fees -= c - g
            l.positions[code] = p.qty > 0 ? p : nil
            log(.cancel, book: b, who: p.who, code: code, name: p.name, text: "시험 몫 뺌 · \(q)주 · 돌린 돈 " + MockFmt.won(c), test: true, side: .buy, qty: q, ref: p.buyRef)
        }
        s.ledgers[i] = l
        save()
    }

    /// 장부를 처음부터. 옛 장부는 닫아서 보관한다 (기록도 그대로)
    func reset(_ b: MockBook) {
        guard let i = s.ledgers.firstIndex(where: { $0.book == b }) else { return }
        var old = s.ledgers[i]
        old.ended = Date()
        s.archived.append(old)
        let n = MockLedger(book: b, started: Date())
        s.ledgers[i] = n
        if b == .rule { s.picks = []; s.skips = []; s.aiViews = [:]; s.dropped = []; lastPicks = []; s.marks = (s.marks ?? []).filter { $0.ledger != old.id } }
        if b == .ai { s.aiNote = nil }
        log(.reset, book: b, text: "\(b.name) 처음부터 · 옛 장부 평가 \(MockFmt.won(MockCalc.equity(old, prices: s.prices))) 보관")
        save()
    }

    /// 조건·규칙이 바뀐 때를 기록한다 (구성 변화가 성과처럼 보이지 않게)
    func edit(_ what: String, _ change: (inout MockState) -> Void) {
        change(&s)
        log(.rules, book: .rule, text: what)
        save()
    }
    /// 관찰 조건 알약을 바꾼다 (켜기·끄기·넣기·고치기·지우기)
    func setConds(_ what: String, _ new: [MockCond]) {
        edit("관찰 조건 · " + what) { $0.conds = new }
    }

    /// 고르기 조건 알약을 바꾸고 선정 규칙 값을 맞춘다 (끈 조건은 막지 않는 값: 상위 10 · 한도 없음 · 현금 남김 없음)
    func setRuleConds(_ what: String, _ new: [MockRuleCond]) {
        edit("고르기 조건 · " + what) { st in
            st.rconds = new
            let on = new.filter(\.on)
            st.select.count = on.first { $0.kind == .top }?.n ?? 10
            st.select.maxPer = on.filter { $0.kind == .per }.map(\.pct).min() ?? 1
            st.select.keepCash = on.filter { $0.kind == .cash }.map(\.pct).max() ?? 0
            st.select.name = on.map(\.text).joined(separator: " · ").isEmpty ? "조건 없음" : on.map(\.text).joined(separator: " · ")
        }
    }

    func setSellConds(_ what: String, _ new: [MockSellCond]) {
        edit("팔기 조건 · " + what) { st in
            st.sconds = new
            let on = new.filter(\.on)
            st.sell.stopLoss = on.filter { $0.kind == .loss }.map(\.pct).min()
            st.sell.takeProfit = on.filter { $0.kind == .gain }.map(\.pct).min()
            st.sell.maxDays = on.filter { $0.kind == .days }.map(\.n).min()
        }
    }

    /// 기록 없이 바꾸는 화면 값 (보이는 장부·지금 쓰는 묶음 고르기 등)
    func setView(_ change: (inout MockState) -> Void) { change(&s); save() }

    // MARK: 기록

    @discardableResult
    func log(_ k: MockEvent.Kind, id: UUID = UUID(), book: MockBook, who: MockWho? = nil, code: String? = nil, name: String? = nil, text: String,
             test: Bool = false, bundle: String? = nil, side: MockSide? = nil, qty: Int? = nil, ref: UUID? = nil, ai: MockAIUse? = nil) -> MockEvent {
        let e = MockEvent(id: id, at: Date(), ledger: ledger(book).id, book: book, kind: k, who: who, code: code, name: name, text: text, test: test,
                          bundle: bundle ?? (book == .rule && [.select, .order].contains(k) ? self.bundle?.name : nil),
                          rule: book == .rule && [.select, .order, .fill].contains(k) ? s.select.name : nil,
                          side: side, qty: qty, ref: ref, ai: ai)
        append(e)
        return e
    }

    private func append(_ e: MockEvent) {
        var e = e
        if e.rule == nil && e.book == .rule && e.kind == .fill { e.rule = s.select.name }
        events.append(e)
        Self.appendLine(e)
    }

    private func set(_ l: MockLedger) {
        if let i = s.ledgers.firstIndex(where: { $0.id == l.id }) { s.ledgers[i] = l }
    }

    // MARK: 파일 (기기 안, 앱 전용 폴더. 위젯·동기화와 나누지 않는다)

    private static var dir: URL {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("mock", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }
    private static let enc: JSONEncoder = { let e = JSONEncoder(); e.dateEncodingStrategy = .secondsSince1970; return e }()
    private static let dec: JSONDecoder = { let d = JSONDecoder(); d.dateDecodingStrategy = .secondsSince1970; return d }()

    private static func loadState() -> MockState? {
        guard let d = try? Data(contentsOf: dir.appendingPathComponent("state.json")) else { return nil }
        if let st = try? dec.decode(MockState.self, from: d) { return st }
        keepBroken()
        return nil
    }
    func save() {
        guard let d = try? Self.enc.encode(s) else { return }
        try? d.write(to: Self.dir.appendingPathComponent("state.json"), options: .atomic)
    }
    private static func loadEvents() -> [MockEvent] {
        guard let d = try? Data(contentsOf: dir.appendingPathComponent("journal.jsonl")) else { return [] }
        return d.split(separator: UInt8(ascii: "\n")).compactMap { try? dec.decode(MockEvent.self, from: Data($0)) }
    }
    /// 기록 파일을 통째로 다시 쓴다 (AI 기록 지우기에만 쓴다)
    private static func rewriteLines(_ es: [MockEvent]) {
        var d = Data()
        for e in es { if let x = try? enc.encode(e) { d.append(x); d.append(UInt8(ascii: "\n")) } }
        try? d.write(to: dir.appendingPathComponent("journal.jsonl"), options: .atomic)
    }
    private static func appendLine(_ e: MockEvent) {
        guard var d = try? enc.encode(e) else { return }
        d.append(UInt8(ascii: "\n"))
        let url = dir.appendingPathComponent("journal.jsonl")
        if let h = try? FileHandle(forWritingTo: url) {
            defer { try? h.close() }
            _ = try? h.seekToEnd()
            try? h.write(contentsOf: d)
        } else {
            try? d.write(to: url)
        }
    }
}

// MARK: AI (조언자. 규칙 장부는 고치지 않고 자기 장부만 굴린다)

extension MockDesk {
    static func aiName(_ kind: String) -> String {
        switch kind {
        case "ledger": return "장부"
        case "review": return "선정 의견"
        case "daily": return "하루 정리"
        case "words": return "말로 조건"
        default: return kind
        }
    }

    /// 1분 주기 끝에 부른다. 때가 된 AI 일 하나만 (AI 끼리는 하나씩, 다른 일은 막지 않는다)
    func startAITick(phase ph: Phase) {
        guard MockAI.on, aiBusy == nil else { return }
        let now = Date(), a = s.ai
        if a.ledgerEvery > 0 && ph == .open && now.timeIntervalSince(s.aiSeen["ledger"] ?? .distantPast) >= Double(a.ledgerEvery * 60) {
            Task { await runAI("ledger") }
        } else if a.daily && ph == .closed, let ss = s.session, ss.day == MockFmt.day(now), let c = ss.close, now >= c, s.aiDailyDay != ss.day {
            Task { await runAI("daily") }
        }
    }

    /// 규칙이 고른 뒤 의견을 묻는다. 다른 AI 일이 돌고 있으면 끝날 때까지 (2분 반까지) 기다린다
    func startAIReview() {
        guard MockAI.on, s.ai.reviewEach, !lastPicks.isEmpty else { return }
        Task {
            var n = 0
            while aiBusy != nil && n < 75 { try? await Task.sleep(nanoseconds: 2_000_000_000); n += 1 }
            await runAI("review")
        }
    }

    /// AI 일 하나. 설정·분석 화면의 '지금 돌리기'도 이것을 부른다 (장 밖이면 AI 장부 주문은 시험 체결)
    func runAI(_ kind: String) async {
        guard MockAI.on, aiBusy == nil, active else { return }
        aiBusy = kind
        defer { aiBusy = nil; save() }
        s.aiSeen[kind] = Date()
        let day = MockFmt.day(Date())
        switch kind {
        case "ledger":
            let ex = excluded()
            let hits = Array((s.scan?.hits ?? []).filter { ex[$0.code] == nil }.prefix(30))
            guard let prompt = MockAI.ledgerPrompt(ledger(.ai), prices: s.prices, hits: hits, bundleName: s.scan?.bundleName, excluded: ex, day: day) else {
                s.runs["ai"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) AI 장부 · 후보·보유 없음 · 안 부름")
                return
            }
            guard let o = await ask(kind, prompt, book: .ai) else { return }
            let cands = Dictionary(hits.map { ($0.code, $0.name) }, uniquingKeysWith: { a, _ in a })
            // 기다리는 동안 장부가 바뀌었을 수 있으니 답을 받은 뒤의 장부로 확인한다
            let r = MockAI.ledgerPlans(o["answer"] as? [String: Any] ?? [:], ledger: ledger(.ai), candidates: cands, excluded: excluded())
            s.aiNote = r.note.isEmpty ? nil : r.note
            let ev = log(.ai, book: .ai, who: .ai,
                         text: "AI 장부 · " + (r.note.isEmpty ? "판단 글 없음" : r.note) + " · 주문 \(r.plans.count)" + (r.dropped.isEmpty ? "" : " · 버린 " + r.dropped.joined(separator: ", ")),
                         ai: MockAI.use(o, kind: kind, summary: "후보 \(hits.count) · 보유 \(ledger(.ai).positions.count)"))
            var n = 0
            for p in r.plans {
                var ok: Bool
                if p.side == .buy { ok = await placeBuy(.ai, code: p.code, name: p.name, budget: p.won, who: .ai, reason: "AI · " + p.why, ref: ev.id) }
                else { ok = await placeSell(.ai, code: p.code, qty: p.qty, who: .ai, reason: "AI · " + p.why, ref: ev.id) }
                if ok { n += 1 }
            }
            s.runs["ai"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) AI 장부 · 주문 \(n)건" + ((o["cached"] as? Bool ?? false) ? " · 지난 답" : ""))
        case "review":
            let picks = lastPicks
            guard let prompt = MockAI.reviewPrompt(picks, bundleName: s.scan?.bundleName, day: day),
                  let o = await ask(kind, prompt, book: .rule) else { return }
            let ans = o["answer"] as? [String: Any] ?? [:]
            let v = MockAI.reviewViews(ans, picks: picks)
            s.aiViews = v
            let c = { (k: String) in v.values.filter { $0.view == k }.count }
            let note = String((ans["note"] as? String ?? "").prefix(120))
            log(.ai, book: .rule, who: .ai, text: "선정 의견 · 찬성 \(c("agree")) · 글쎄 \(c("doubt")) · 빼자 \(c("drop"))" + (note.isEmpty ? "" : " · " + note),
                ref: s.scanEvent, ai: MockAI.use(o, kind: kind, summary: "고른 \(picks.count)"))
            s.runs["ai"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) AI 선정 의견 · \(v.count)종목")
        case "daily":
            s.aiDailyDay = day
            guard let o = await ask(kind, MockAI.dailyPrompt(day: day, lines: dailyLines(day)), book: .rule) else { return }
            let ans = o["answer"] as? [String: Any] ?? [:]
            let sum = String((ans["summary"] as? String ?? "").prefix(300))
            let pts = (ans["points"] as? [String] ?? []).prefix(3).map { "· " + String($0.prefix(120)) }
            s.aiDaily = ([sum] + pts).filter { !$0.isEmpty }.joined(separator: "\n")
            log(.ai, book: .rule, who: .ai, text: "하루 정리 · " + sum, ai: MockAI.use(o, kind: kind, summary: day))
            s.runs["ai"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) AI 하루 정리")
        default:
            break
        }
    }

    /// 말로 쓴 조건 → 조건 묶음 (저장은 화면에서 보고 정한다)
    func aiWords(_ words: String) async -> (bundle: MockBundle, note: String)? {
        let w = words.trimmingCharacters(in: .whitespacesAndNewlines)
        guard MockAI.on, !w.isEmpty, aiBusy == nil, active else { return nil }
        aiBusy = "words"
        defer { aiBusy = nil; save() }
        guard let o = await ask("words", MockAI.wordsPrompt(String(w.prefix(300))), book: .rule) else { return nil }
        guard let r = MockAI.wordsBundle(o["answer"] as? [String: Any] ?? [:], words: w) else {
            s.runs["ai"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) 말로 조건 · 조각으로 못 바꿈")
            log(.error, book: .rule, who: .ai, text: "말로 조건 · 조각으로 못 바꿈 · \"\(w.prefix(40))\"", ai: MockAI.use(o, kind: "words", summary: w))
            return nil
        }
        log(.ai, book: .rule, who: .ai, text: "말로 조건 · \"\(w.prefix(40))\" → " + r.bundle.pieces.map(\.text).joined(separator: " · "),
            ai: MockAI.use(o, kind: "words", summary: w))
        s.runs["ai"] = MockRun(at: Date(), ok: true, text: "\(MockFmt.time(Date())) 말로 조건 · 조각 \(r.bundle.pieces.count)")
        return r
    }

    func refreshWallet() async {
        guard MockAI.on else { return }
        if let o = try? await MockFeed.wallet(limits: s.ai.limits), let w = MockAI.wallet(o, at: Date()) { s.aiWallet = w }
    }

    /// 서버에 묻는다. 실패면 이유를 기록하고 nil (AI 장부는 그 차례만 건너뛴다). 실패해도 쓴 돈이 있으면 남긴다
    private func ask(_ kind: String, _ prompt: String, book: MockBook) async -> [String: Any]? {
        let name = Self.aiName(kind)
        let o: [String: Any]
        do { o = try await MockFeed.ai(kind: kind, prompt: prompt, limits: s.ai.limits) }
        catch {
            let why = (error as? MockFeed.Fail)?.why ?? error.localizedDescription
            log(.error, book: book, who: .ai, text: "AI \(name) 실패 · " + why)
            s.runs["ai"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) AI \(name) 실패 · " + why)
            return nil
        }
        if let w = MockAI.wallet(o, at: Date()) { s.aiWallet = w }
        if let e = o["error"] as? String {
            let paid = (MockFeed.num(o["cost"]) ?? 0) > 0
            log(.error, book: book, who: .ai, text: "AI \(name) 실패 · " + e, ai: paid ? MockAI.use(o, kind: kind, summary: e) : nil)
            s.runs["ai"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) AI \(name) 실패 · " + e)
            return nil
        }
        return o
    }

    /// 하루 정리에 보낼 줄: 장부마다 평가·현금·오늘 체결, 규칙·손·AI 성적
    private func dailyLines(_ day: String) -> [String] {
        var out: [String] = []
        for b in MockBook.allCases {
            let l = ledger(b)
            let eq = equity(b)
            out.append("[\(b.name)] 평가 \(MockFmt.won(eq)) (\(MockFmt.pct(eq / l.startCash - 1))) · 현금 \(MockFmt.won(l.cash)) · 보유 \(l.positions.count)종목 · 대기 \(l.pending.count)")
            let today = ledgerEvents(b).filter { $0.kind == .fill && MockFmt.day($0.at) == day }
            for w in MockWho.allCases {
                let st = MockCalc.stat(today, ledger: l.id, who: w)
                if st.trades > 0 { out.append("  \(w.name): 판 \(st.trades)건 · 이긴 \(st.wins) · 손익 \(MockFmt.won(st.pnl))") }
            }
            for e in today.prefix(30) {
                out.append("  \(MockFmt.time(e.at)) \(e.name ?? e.code ?? "") \(e.side?.name ?? "") \(e.qty ?? 0)주 \(MockFmt.won(e.price ?? 0))" +
                           (e.pnl.map { " 손익 " + MockFmt.won($0) } ?? "") + " · \(e.who?.name ?? "")" + (e.test ? " · 시험" : ""))
            }
        }
        return out
    }
}

#endif
