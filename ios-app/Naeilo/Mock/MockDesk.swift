#if MOCK
import Foundation
import Observation
import UIKit

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
    var seen: [String: [MockSeen]]? = nil     // 종목별 관찰 기록 (7일 · 종목당 20번까지)
    var hidden: [UUID]? = nil                 // 화면에서 숨긴 기록 줄 (손익·장부 계산 원본은 남긴다)
    var cycleSell: Bool? = nil                // 지금 한 바퀴에 팔기까지 (없으면 켬)
    var cycleOrder: Bool? = nil               // 지금 한 바퀴에 주문까지 (없으면 켬)
    var autoOrder: Bool? = nil                // 돌리기에 주문까지 (처음 한 번 옛 직접 검토 값에서 옮긴다: 검토 켬 → 끔)
    var slipAdded: Bool? = nil                // 미끄러짐 한도 알약을 한 번 넣었는지 (2026-10-26)
    var autoSell: Bool? = nil                 // 돌리기(1분 주기)에 팔기까지 (없으면 켬)

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
    /// 주문층. 판단 코드는 이것만 부른다 (지금은 모의 브로커, 실전은 토스 브로커로 갈아 끼운다)
    let broker: MockBroker = MockPaperBroker()

    /// 받는 중인 관찰 일봉 (멈췄다 이어 받기). 기기 메모리에만 둔다: 기준일·봉 수·종목 수가 같아야 이어 쓴다
    private struct Partial {
        var key: String
        var bars: [String: [MockBar]] = [:]
        var errors: [String: String] = [:]
        var done: Set<Int> = []
        var n: Int { bars.count + errors.count }
    }
    private var partial: Partial?
    /// 멈춘 관찰 (어디서 시작했나: observe · cycle · auto). 앱이 다시 앞에 오면 이어 받는다
    private(set) var paused: String? = nil
    private var obsFrom = "observe"
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
        // 미끄러짐 한도 0.5% (켠 채로 한 번만 넣는다)
        if s.slipAdded != true {
            s.rconds = (s.rconds ?? []) + [MockRuleCond(kind: .slip, pct: 0.005)]
            s.select.slip = 0.005
            s.slipAdded = true
            changed = true
        }
        // 직접 검토 설정을 뺐다 (2026-10-26). 옛 검토 켬 사용자가 돌리기에서 갑자기 자동 주문되지 않게 한 번 옮긴다
        if s.autoOrder == nil {
            s.autoOrder = !s.select.review
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
        await keepAlive("observe")
        busy = nil
        save()
    }

    /// 단추로 시작한 훑기: 앱을 나가도 iOS 가 주는 남은 시간 동안 계속 받는다. 시간이 다 되면 멈춤으로 두고 돌아오면 이어 받는다
    private func keepAlive(_ from: String) async {
        obsFrom = from
        let t = Task { await self.observe() }
        let box = BgId()
        box.id = UIApplication.shared.beginBackgroundTask(withName: "mock.observe") {
            t.cancel()
            if box.id != .invalid { UIApplication.shared.endBackgroundTask(box.id); box.id = .invalid }
        }
        await t.value
        if box.id != .invalid { UIApplication.shared.endBackgroundTask(box.id); box.id = .invalid }
    }
    private final class BgId { var id = UIBackgroundTaskIdentifier.invalid }

    /// 앱이 다시 앞에 왔을 때: 멈춘 훑기를 남은 묶음부터 이어 받는다 (자동은 다음 1분 주기가 잇는다)
    func resume() async {
        guard let p = paused, busy == nil else { return }
        switch p {
        case "observe": await runObserve()
        case "cycle": await runCycle()
        default: break
        }
    }

    private func observe() async {
        guard let b = bundle, !b.pieces.isEmpty else {
            s.runs["observe"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) 켠 조건이 없어요")
            return
        }
        defer { progress = nil }
        let t0 = Date(), stocks = Self.universe, codes = stocks.map(\.code)
        let need = max(61, ([b] + s.exceptions.bundles).map(\.need).max() ?? 0), count = min(200, need)
        // 같은 날·같은 봉 수면 멈춘 데서 이어 받는다 (기준일이 바뀌면 처음부터)
        let key = "\(MockFmt.day(Date()))|\(count)|\(codes.count)"
        if partial?.key != key { partial = Partial(key: key) }
        progress = (partial?.n ?? 0, codes.count)
        do {
            _ = try await MockFeed.daily(codes, count: count, skip: partial?.done ?? [],
                                         progress: { [weak self] n in self?.progress = (n, codes.count) },
                                         batch: { [weak self] i, bars, errs in
                                             self?.partial?.bars.merge(bars) { _, b in b }
                                             self?.partial?.errors.merge(errs) { _, b in b }
                                             self?.partial?.done.insert(i)
                                         })
            let got = partial ?? Partial(key: key)
            partial = nil; paused = nil
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
            addSeen(scan)
            for h in scan.hits { s.prices[h.code] = h.price }
            let errN = got.errors.count
            s.runs["observe"] = MockRun(at: Date(), ok: errN < codes.count / 10,
                                        text: "\(MockFmt.time(Date())) 훑음 · \(Int(scan.seconds))초 · 일봉 기준 \(scan.barDay)" + (errN > 0 ? " · 못 받은 \(errN)" : ""))
        } catch is CancellationError {
            // 취소(앱 나감·화면 전환)는 오류가 아니다. 받은 묶음은 두고 멈춤으로
            paused = obsFrom
            s.runs["observe"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) 멈춤 · 돌아오면 이어 받아요 (\((partial?.n ?? 0).formatted()) / \(codes.count.formatted()))")
        } catch {
            let why = (error as? MockFeed.Fail)?.why ?? error.localizedDescription
            s.runs["observe"] = MockRun(at: Date(), ok: false, text: "\(MockFmt.time(Date())) 실패 · " + why)
            log(.error, book: .rule, text: "관찰 실패 · " + why)
        }
    }

    /// 종목별 관찰 기록: 걸린 종목마다 한 줄. 7일 지난 것은 버리고 종목당 20번까지
    private func addSeen(_ sc: MockScan) {
        var all = s.seen ?? [:]
        let cut = sc.at.addingTimeInterval(-7 * 86_400)
        for h in sc.hits { all[h.code, default: []].append(MockSeen(at: sc.at, price: h.price, change: h.change, why: h.why)) }
        for (c, xs) in all {
            let keep = xs.filter { $0.at >= cut }.suffix(20)
            all[c] = keep.isEmpty ? nil : Array(keep)
        }
        s.seen = all
    }

    func seen(_ code: String) -> [MockSeen] { s.seen?[code] ?? [] }

    /// 오늘 관찰에 2번 넘게 걸린 종목
    var seenTwice: Set<String> {
        let today = MockFmt.day(Date())
        return Set((s.seen ?? [:]).filter { $0.value.filter { MockFmt.day($0.at) == today }.count >= 2 }.keys)
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

    /// 고른다. buy = 참이면 확인 없이 바로 주문 (한 바퀴·돌리기의 주문 알약). 지금 고르기는 고르기까지만, 주문은 '주문' 단추
    private func select(buy: Bool = false) async {
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
        if buy { await order() }
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

    var cycleSell: Bool { s.cycleSell ?? true }
    var cycleOrder: Bool { s.cycleOrder ?? true }
    var autoOrder: Bool { s.autoOrder ?? true }
    var autoSell: Bool { s.autoSell ?? true }

    /// 한 바퀴: 훑기 → 고르기 → 주문 → 팔기 (주문·팔기 알약을 끄면 건너뜀)
    func runCycle() async {
        await waitTick()
        guard busy == nil else { return }
        busy = "cycle"
        await keepAlive("cycle")
        if s.runs["observe"]?.ok == true { await select(buy: cycleOrder) }
        if cycleSell && paused == nil { await sellNow() }
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
        do { book = try await broker.book(code) }
        catch is CancellationError { return false }
        catch { log(.error, book: b, who: who, code: code, name: name, text: "호가 확인 못 함 · " + ((error as? MockFeed.Fail)?.why ?? ""), ref: ref); return false }
        if test && book.asks.isEmpty, let px = s.prices[code] { book = MockCalc.Book(asks: [.init(p: px, v: 1e12)], bids: book.bids, at: book.at) }
        guard let ask = book.bestAsk else { log(.reject, book: b, who: who, code: code, name: name, text: "매도 호가 없음 (거래 정지·VI 의심) · 거부", test: test, side: .buy, ref: ref); return false }
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
        // 미끄러짐 한도: 다 채우면 평균 단가가 1호가 + 한도를 넘을 때, 한도 값까지만 지금 채우고 남은 몫은 지정가로 30분 기다린다
        let (oType, oLim, until) = split(.buy, type: type, limit: lim, qty: q, book: book, reason: reason)
        let r = MockCalc.reserve(type: oType, qty: q, limit: oLim, bestAsk: ask, fees: s.fees, cash: l.cash)
        let auc = !test && inCloseAuction()
        let o = MockOrder(code: code, name: name, side: .buy, type: oType, qty: q, limit: oLim, at: Date(), who: who, reason: reason, reserved: r, ref: ref,
                          until: auc ? s.session?.close?.addingTimeInterval(1200) : until, state: .accepted, prev: prevClose(code), auction: auc ? true : nil)
        if let why = await broker.submit(o, book: book) { reject(b, o, why, test: test); return false }
        // 접수를 기다리는 동안 장부가 바뀌었을 수 있다
        guard let l2 = s.ledgers.first(where: { $0.book == b }), !l2.pending.contains(where: { $0.code == code }), l2.cash >= r else { return false }
        l = l2
        l.cash -= r
        l.pending.append(o)
        set(l)
        log(.order, id: o.id, book: b, who: who, code: code, name: name,
            text: "접수 · \(oType.name) \(q)주" + (oLim.map { " · " + MockFmt.won($0) } ?? "") + (until != nil ? " · 나눔 (미끄러짐 한도)" : "") + " · 묶은 돈 " + MockFmt.won(r) + (reason.isEmpty ? "" : " · " + reason), test: test, side: .buy, qty: q, ref: ref)
        await settle(b, o, book: book, test: test)
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
        do { book = try await broker.book(code) }
        catch is CancellationError { return false }
        catch { log(.error, book: b, who: who, code: code, name: p.name, text: "호가 확인 못 함 · 팔기 미룸 · " + ((error as? MockFeed.Fail)?.why ?? "")); return false }
        if test && book.bids.isEmpty, let px = s.prices[code] { book = MockCalc.Book(asks: book.asks, bids: [.init(p: px, v: 1e12)], at: book.at) }
        let lim = limit.map { MockCalc.snap($0, side: .sell) }
        guard var l = s.ledgers.first(where: { $0.book == b }), let p2 = l.positions[code],
              p2.qty - l.pending.filter({ $0.code == code && $0.side == .sell }).reduce(0, { $0 + $1.qty }) >= q else { return false }
        let (oType, oLim, until) = split(.sell, type: type, limit: lim, qty: q, book: book, reason: reason)
        let auc = !test && inCloseAuction()
        let o = MockOrder(code: code, name: p.name, side: .sell, type: oType, qty: q, limit: oLim, at: Date(), who: who, reason: reason, ref: ref ?? p.buyRef,
                          until: auc ? s.session?.close?.addingTimeInterval(1200) : until, state: .accepted, prev: prevClose(code), auction: auc ? true : nil)
        if let why = await broker.submit(o, book: book) { reject(b, o, why, test: test); return false }
        guard let l2 = s.ledgers.first(where: { $0.book == b }), let p3 = l2.positions[code],
              p3.qty - l2.pending.filter({ $0.code == code && $0.side == .sell }).reduce(0, { $0 + $1.qty }) >= q else { return false }
        l = l2
        l.pending.append(o)
        set(l)
        log(.order, id: o.id, book: b, who: who, code: code, name: p.name,
            text: "접수 · \(oType.name) 매도 \(q)주" + (oLim.map { " · " + MockFmt.won($0) } ?? "") + (until != nil ? " · 나눔 (미끄러짐 한도)" : "") + (reason.isEmpty ? "" : " · " + reason), test: test, side: .sell, qty: q, ref: o.ref)
        await settle(b, o, book: book, test: test)
        return true
    }

    /// 미끄러짐 한도로 나눌지: 시장가를 지금 호가로 끝까지 채운 평균 단가가 1호가에서 한도보다 더 벗어나면
    /// 한도 값의 지정가로 바꾸고 30분 기한을 둔다 (한도까지는 지금 채우고, 남은 몫은 1분 주기가 새 호가로 채워 본다). 손절 매도는 한 번에
    private func split(_ side: MockSide, type: MockOrderType, limit: Double?, qty: Int, book: MockCalc.Book, reason: String) -> (MockOrderType, Double?, Date?) {
        guard type == .market, let sl = s.select.slip, sl > 0, !reason.hasPrefix("손절") else { return (type, limit, nil) }
        let f = MockCalc.walk(side: side, type: .market, qty: qty, limit: nil, book: book, fees: s.fees)
        guard f.qty > 0 else { return (type, limit, nil) }
        if side == .buy, let a = book.bestAsk, f.avg > a * (1 + sl) {
            return (.limit, max(a, MockCalc.snap(a * (1 + sl), side: .buy)), Date().addingTimeInterval(1800))
        }
        if side == .sell, let b = book.bestBid, f.avg < b * (1 - sl) {
            return (.limit, min(b, MockCalc.snap(b * (1 - sl), side: .sell)), Date().addingTimeInterval(1800))
        }
        return (type, limit, nil)
    }

    /// 나눈 남은 몫이 기한까지 다 못 채워졌으면 취소하고 묶은 돈을 돌려준다
    private func expire(_ b: MockBook, orderId: UUID) {
        guard var l = s.ledgers.first(where: { $0.book == b }), let i = l.pending.firstIndex(where: { $0.id == orderId }) else { return }
        let o = l.pending.remove(at: i)
        l.cash += max(0, o.reserved)
        set(l)
        log(.cancel, book: b, who: o.who, code: o.code, name: o.name, text: "남은 \(o.qty)주 취소 · 30분 안에 다 못 채움", ref: o.id)
        Task { await broker.cancel(o) }
    }

    /// 마감 동시호가 (정규장 끝 10분 전부터 끝까지): 접수만 하고 끝나는 시각에 단일가로 체결
    func inCloseAuction(_ now: Date = Date()) -> Bool {
        guard let ss = s.session, ss.day == MockFmt.day(now), let c = ss.close else { return false }
        return now >= c.addingTimeInterval(-600) && now < c
    }

    /// 전일 종가: 마지막 훑기의 가격·등락으로 거꾸로 셈한다 (훑기에 없으면 모름)
    func prevClose(_ code: String) -> Double? {
        guard let h = s.scan?.hits.first(where: { $0.code == code }), let c = h.change, c > -1 else { return nil }
        return h.price / (1 + c)
    }

    /// 거부: 장부는 그대로, 이유만 기록 (실전 주문 API 의 거부와 같은 자리)
    private func reject(_ b: MockBook, _ o: MockOrder, _ why: String, test: Bool) {
        log(.reject, book: b, who: o.who, code: o.code, name: o.name, text: why, test: test, side: o.side, qty: o.qty, ref: o.ref)
    }

    /// 접수 뒤 체결: 브로커가 주는 호가로 채운다 (모의는 1~2초 지연 뒤 새 호가, 장 밖 시험은 받은 호가 그대로)
    private func settle(_ b: MockBook, _ o: MockOrder, book: MockCalc.Book, test: Bool) async {
        let nb = test ? book : (await broker.settle(o) ?? book)
        fill(b, orderId: o.id, book: nb, test: test)
    }

    /// 대기 주문 하나를 호가로 채워 본다. 남으면 왜 기다리는지 (바뀌었을 때만) 남긴다
    private func fill(_ b: MockBook, orderId: UUID, book: MockCalc.Book, test: Bool) {
        guard var l = s.ledgers.first(where: { $0.book == b }), let i = l.pending.firstIndex(where: { $0.id == orderId }) else { return }
        let o = l.pending[i]
        if o.auction == true, inCloseAuction() {
            if o.waitWhy == nil {
                l.pending[i].waitWhy = "마감 동시호가 · 15:30 단일가 체결 대기"
                set(l)
                log(.wait, book: b, who: o.who, code: o.code, name: o.name, text: "마감 동시호가 · 15:30 단일가 체결 대기", test: test, ref: o.id)
            }
            return
        }
        let f = broker.match(o, book: book, fees: s.fees)
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
            if f.qty > 0 { w.state = .partial }
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
        Task { await broker.cancel(o) }
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
                if let u = o.until, Date() >= u { expire(l.book, orderId: o.id); continue }
                if o.auction == true {
                    // 마감이 지나면 단일가(종가)로 한 번에. 지정가가 안 닿으면 남는다
                    guard !inCloseAuction(), let px = s.prices[o.code], px > 0 else { continue }
                    let single = o.side == .buy ? MockCalc.Book(asks: [.init(p: px, v: 1e12)], bids: [], at: Date())
                                                : MockCalc.Book(asks: [], bids: [.init(p: px, v: 1e12)], at: Date())
                    if let j = s.ledgers.firstIndex(where: { $0.id == l.id }), let k = s.ledgers[j].pending.firstIndex(where: { $0.id == o.id }) {
                        s.ledgers[j].pending[k].auction = false
                    }
                    fill(l.book, orderId: o.id, book: single, test: false)
                    continue
                }
                let px = s.prices[o.code]
                let cross: Bool = o.type == .market || px == nil || (o.side == .buy ? px! <= (o.limit ?? 0) : px! >= (o.limit ?? .infinity))
                guard cross, let book = try? await broker.book(o.code) else { continue }
                fill(l.book, orderId: o.id, book: book, test: ph != .open)
            }
        }
        // 팔기 규칙 (규칙 장부, 장중 가격)
        // 자동 팔기는 돌리기가 켜져 있고(간격 > 0) 돌리기 팔기 알약이 켜져 있을 때만 (2026-10-26)
        if ph != .unknown && s.sell.on && s.auto > 0 && autoSell {
            let today = MockFmt.day(Date())
            let sigs = MockCalc.sellSignals(ledger(.rule), rule: s.sell, prices: s.prices, excluded: excluded(), today: today)
            var n = 0
            for sig in sigs {
                let ref = log(.sell, book: .rule, who: .rule, code: sig.code, name: Self.name(sig.code), text: sig.why, ref: ledger(.rule).positions[sig.code]?.buyRef).id
                if await placeSell(.rule, code: sig.code, who: .rule, reason: sig.why, ref: ref) { n += 1 }
            }
            // 1분 주기가 판 것도 팔기 칸 상태에 남긴다 ('지금 팔기'만 적어 자동으로 판 뒤에도 '없음'으로 보이던 일)
            if !sigs.isEmpty { s.runs["sell"] = MockRun(at: Date(), ok: n == sigs.count, text: MockFmt.stamp(Date()) + " · 자동 · \(n)종목 팔기 주문") }
        }
        // 한 바퀴 자동 (장중만)
        var picked = false
        if s.auto > 0 && ph == .open, paused == "auto" || Date().timeIntervalSince(s.runs["observe"]?.at ?? .distantPast) >= Double(s.auto * 60) {
            obsFrom = "auto"
            await observe()
            // 훑기가 끝났을 때만 고른다 (멈춤·실패면 옛 결과로 고르지 않는다)
            if s.runs["observe"]?.ok == true { await select(buy: autoOrder) }
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
            st.select.slip = on.filter { $0.kind == .slip }.map(\.pct).min()
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

    // MARK: 기록 줄 지우기·숨기기 (줄을 왼쪽으로 밀어서)

    var hiddenIds: Set<UUID> { Set(s.hidden ?? []) }

    /// 체결·주문 등 계산에 쓰는 기록은 화면에서만 숨긴다
    func hide(_ ids: [UUID]) {
        s.hidden = Array(hiddenIds.union(ids))
        save()
    }

    /// 오류·규칙 바뀜 줄은 계산에 쓰지 않아 기록 파일에서 실제로 지운다
    func deleteNotes(_ ids: Set<UUID>) {
        let n = events.count
        events.removeAll { ($0.kind == .error || $0.kind == .rules) && ids.contains($0.id) }
        if events.count != n { Self.rewriteLines(events) }
    }

    /// 관찰 기록 한 줄 지우기 (빨간 점 셈에서도 빠진다)
    func deleteSeen(_ code: String, at: Date) {
        s.seen?[code]?.removeAll { $0.at == at }
        if s.seen?[code]?.isEmpty == true { s.seen?[code] = nil }
        save()
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
    /// 기록 파일을 통째로 다시 쓴다 (AI 기록 지우기 · 오류·규칙 줄 지우기)
    private static func rewriteLines(_ es: [MockEvent]) {
        var d = Data()
        for e in es { if let x = try? enc.encode(e) { d.append(x); d.append(UInt8(ascii: "\n")) } }
        try? d.write(to: dir.appendingPathComponent("journal.jsonl"), options: .atomic)
    }
    /// 내보낼 파일: 장부 상태와 기록 (있는 것만)
    static var exportFiles: [URL] {
        ["state.json", "journal.jsonl"].map { dir.appendingPathComponent($0) }.filter { FileManager.default.fileExists(atPath: $0.path) }
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

// MARK: 주문층 (브로커)

/// 주문층: 모의와 실전(토스 주문 API)이 같은 모양. 관찰·선정·팔기 판단 코드는 이것만 부른다
@MainActor protocol MockBroker {
    /// 실전 주문을 보내는 브로커인가
    var live: Bool { get }
    var name: String { get }
    /// 지금 호가 (주문 판단·대기 주문 다시 보기)
    func book(_ code: String) async throws -> MockCalc.Book
    /// 주문 넣기: 접수면 nil, 거부면 이유
    func submit(_ o: MockOrder, book: MockCalc.Book) async -> String?
    /// 상태 보기: 접수 뒤 체결을 셈할 호가 (모의는 지연 뒤 새 호가). 못 받으면 nil
    func settle(_ o: MockOrder) async -> MockCalc.Book?
    /// 호가로 채울 몫 (모의는 같은 종목에 1분 안에 먹은 잔량을 빼고 셈한다)
    func match(_ o: MockOrder, book: MockCalc.Book, fees: MockFees) -> MockCalc.Fill
    /// 주문 취소
    func cancel(_ o: MockOrder) async
}

/// 모의 브로커: 거래소 대신 받은 호가로 체결을 셈한다
@MainActor final class MockPaperBroker: MockBroker {
    let live = false
    let name = "모의 브로커"
    /// 접수에서 체결까지 지연 (초). 실전처럼 그사이 호가가 바뀐다
    var latency = 1.5
    /// 종목 → 최근에 먹은 호가 (매수는 매도 호가, 매도는 매수 호가). 1분이 지나면 잔량이 다시 찬 것으로 본다
    private var used: [String: (at: Date, asks: [Double: Double], bids: [Double: Double])] = [:]

    func book(_ code: String) async throws -> MockCalc.Book { trim(code, try await MockFeed.book(code)) }

    /// 접수: 거래소가 거부할 주문을 여기서 거부한다 (실전은 토스가 같은 이유로 거부)
    func submit(_ o: MockOrder, book: MockCalc.Book) async -> String? { Self.exchangeReject(o, book: book) }

    /// 거래소 규칙 (데이터로 판별 가능한 만큼)
    static func exchangeReject(_ o: MockOrder, book: MockCalc.Book) -> String? {
        // 거래 정지·VI 단일가: 호가가 양쪽 다 비어 있으면 (둘을 가를 신호는 없다)
        if book.asks.isEmpty && book.bids.isEmpty && o.auction != true { return "호가 없음 (거래 정지·VI 의심) · 거부" }
        // 가격제한폭 ±30%: 넘는 지정가는 거래소가 받지 않는다 (전일 종가를 알 때만)
        if o.type == .limit, let lim = o.limit, let p = o.prev, p > 0 {
            if lim > p * 1.3 { return "상한가(전일 종가 +30%) 넘는 지정가 · 거부" }
            if lim < p * 0.7 { return "하한가(전일 종가 −30%) 밑 지정가 · 거부" }
        }
        return nil
    }

    func settle(_ o: MockOrder) async -> MockCalc.Book? {
        try? await Task.sleep(for: .milliseconds(Int(latency * 1000)))
        return try? await book(o.code)
    }

    func match(_ o: MockOrder, book: MockCalc.Book, fees: MockFees) -> MockCalc.Fill {
        let b = trim(o.code, book)
        let f = MockCalc.walk(side: o.side, type: o.type, qty: o.qty, limit: o.limit, book: b, fees: fees, cap: o.side == .buy ? o.reserved : nil)
        guard f.qty > 0 else { return f }
        var u = used[o.code].flatMap { Date().timeIntervalSince($0.at) < 60 ? $0 : nil } ?? (at: Date(), asks: [:], bids: [:])
        for lv in f.taken {
            if o.side == .buy { u.asks[lv.p, default: 0] += lv.v } else { u.bids[lv.p, default: 0] += lv.v }
        }
        u.at = Date()
        used[o.code] = u
        return f
    }

    func cancel(_ o: MockOrder) async {}

    /// 1분 안에 먹은 잔량을 호가에서 뺀다 (같은 순간에 나눠 내도 같은 잔량을 두 번 먹지 않게)
    private func trim(_ code: String, _ b: MockCalc.Book) -> MockCalc.Book {
        guard let u = used[code], Date().timeIntervalSince(u.at) < 60 else { used[code] = nil; return b }
        return MockCalc.Book(asks: b.asks.map { .init(p: $0.p, v: $0.v - (u.asks[$0.p] ?? 0)) },
                             bids: b.bids.map { .init(p: $0.p, v: $0.v - (u.bids[$0.p] ?? 0)) }, at: b.at)
    }
}

/// 토스 실주문 브로커 자리 (껍데기). 실제 주문을 보내는 코드·키·네트워크 호출은 없다. 연결 전까지 모든 주문을 거부한다
@MainActor final class MockTossBroker: MockBroker {
    let live = true
    let name = "토스 브로커 (연결 전)"
    func book(_ code: String) async throws -> MockCalc.Book { throw MockFeed.Fail(why: "토스 주문 API 연결 전") }
    func submit(_ o: MockOrder, book: MockCalc.Book) async -> String? { "토스 주문 API 연결 전 · 주문 안 냄" }
    func settle(_ o: MockOrder) async -> MockCalc.Book? { nil }
    func match(_ o: MockOrder, book: MockCalc.Book, fees: MockFees) -> MockCalc.Fill { MockCalc.Fill() }
    func cancel(_ o: MockOrder) async {}
}

#endif
