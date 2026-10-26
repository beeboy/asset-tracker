// 모의투자 계산 시험 (앱 밖에서 돈다). ios-app 에서:
//   swiftc -D MOCK Naeilo/Mock/MockModels.swift Naeilo/Mock/MockEngine.swift Naeilo/Mock/MockFeed.swift Naeilo/Mock/MockAI.swift tools/mock_test/Stub.swift tools/mock_test/main.swift -o /tmp/mock_test && /tmp/mock_test
import Foundation

var fails = 0
func check(_ c: Bool, _ msg: String, line: Int = #line) { if !c { fails += 1; print("FAIL \(line): \(msg)") } }
func near(_ a: Double, _ b: Double, _ eps: Double = 1e-6) -> Bool { abs(a - b) < eps }

let fees = MockFees()
let t0 = Date(timeIntervalSince1970: 1_792_000_000)

// 일봉 만들기: 21일, 마지막 날 거래량 3배·+4%
func bars(_ base: Double, lastUp: Double, volMul: Double, n: Int = 22) -> [MockBar] {
    var out: [MockBar] = []
    for i in 0..<n {
        let d = String(format: "2026-09-%02d", i + 1)
        let c = i == n - 1 ? base * (1 + lastUp) : base
        let v = i == n - 1 ? 1000 * volMul : 1000
        out.append(MockBar(d: d, o: base, h: c, l: base, c: c, v: v))
    }
    return out
}

// 조각 값
do {
    let b = bars(10_000, lastUp: 0.04, volMul: 3)
    check(near(MockCalc.value(MockPiece(kind: .change), b)!, 4), "등락률 4%")
    check(near(MockCalc.value(MockPiece(kind: .volume, days: 20), b)!, 3), "거래량 3배")
    check(MockCalc.value(MockPiece(kind: .ma, days: 20), b)! > 0, "20일선 위")
    check(MockCalc.value(MockPiece(kind: .volume, days: 30), b) == nil, "봉 모자람 → nil")
    let p = MockPiece(kind: .volume, lo: 2, days: 20)
    check(p.text == "거래량 ≥ 2배", "조각 글: \(p.text)")
    check(MockPiece(kind: .rise, hi: 30, days: 5).text == "최근 5일 ≤ +30%", "최근 상승 글")
    check(MockRuleCond(kind: .cap, capKind: .volume, lim: 20).over(MockHit(code: "1", name: "a", price: 1, change: nil, score: 0, why: "", vol: 438.9)), "폭등 거르기: 거래량 배수")
    check(!MockRuleCond(kind: .cap, capKind: .rise, lim: 20).over(MockHit(code: "1", name: "a", price: 1, change: nil, score: 0, why: "", rise5: 12)), "폭등 거르기: 5일 상승 아래")
    check(MockPiece(kind: .change, lo: 2, hi: 8).text == "등락률 +2% ~ +8%", "등락률 글")
}

// 훑기
let stocks = [MockStock(code: "000001", name: "가", kospi: true), MockStock(code: "000002", name: "나", kospi: false),
              MockStock(code: "000003", name: "다", kospi: true), MockStock(code: "000004", name: "라", kospi: true)]
let bundle = MockBundle(name: "A", pieces: [MockPiece(kind: .volume, lo: 2, days: 20), MockPiece(kind: .change, lo: 2, hi: 8), MockPiece(kind: .ma, days: 20)])
var allBars: [String: [MockBar]] = [
    "000001": bars(8_000, lastUp: 0.042, volMul: 3.1),
    "000002": bars(12_000, lastUp: 0.068, volMul: 2.7),
    "000003": bars(40_000, lastUp: 0.01, volMul: 5),          // 등락률에서 떨어짐
]
allBars["000004"] = Array(bars(5_000, lastUp: 0.03, volMul: 3).dropLast())   // 오늘 봉 없음 → 자료 없음
let scan = MockCalc.scan(bundle, stocks: stocks, bars: allBars, excluded: [], at: t0)
check(scan.hits.map(\.code) == ["000001", "000002"], "걸린 순서(거래량 배수 큰 순): \(scan.hits.map(\.code))")
check(scan.dropped[1] == 1 && scan.dropped[-1] == 1, "떨어진 이유: \(scan.dropped)")
check(scan.hits[0].why.contains("거래량 3.1배"), "이유: \(scan.hits[0].why)")
let scanEx = MockCalc.scan(bundle, stocks: stocks, bars: allBars, excluded: ["000001"], at: t0)
check(scanEx.hits.map(\.code) == ["000002"] && scanEx.excluded == ["000001"], "예외 빼기")
let kospiOnly = MockCalc.scan(MockBundle(name: "K", pieces: [], scope: .kospi), stocks: stocks, bars: allBars, excluded: [], at: t0)
check(kospiOnly.total == 3, "코스피만 \(kospiOnly.total)")

// 선정: 1,000만원, 5종목, 최대 20%, 현금 20% 남김
var L = MockLedger(book: .rule, started: t0)
let rule = MockSelectRule()
let sel = MockCalc.select(scan, ledger: L, rule: rule, fees: fees, prices: [:], excluded: [:])
check(sel.picks.count == 2, "두 종목 고름")
// 쓸 돈 = min(20% × 1천만 = 200만, (1천만 − 200만)/5 = 160만) = 160만
check(sel.picks[0].qty == Int((1_600_000 / (8_336 * 1.00015)).rounded(.down)), "수량: \(sel.picks[0].qty)")
check(sel.picks.allSatisfy { $0.budget <= 1_600_000 }, "예산 안")
// 현금 부족
var poor = L; poor.cash = 9_000
let selPoor = MockCalc.select(scan, ledger: poor, rule: rule, fees: fees, prices: [:], excluded: [:])
check(selPoor.picks.isEmpty && selPoor.skips.contains { $0.short }, "현금 부족: \(selPoor.skips)")
// 0주
let pricey = MockScan(at: t0, bundleId: bundle.id, bundleName: "A", pieceTexts: [], barDay: "", total: 1,
                      hits: [MockHit(code: "9", name: "비싼", price: 3_000_000, change: nil, score: 1, why: "")], dropped: [:])
let sel0 = MockCalc.select(pricey, ledger: L, rule: rule, fees: fees, prices: [:], excluded: [:])
check(sel0.picks.isEmpty && sel0.skips.first?.why.hasPrefix("0주") == true, "0주 정상: \(sel0.skips)")
// 예외
let selEx = MockCalc.select(scan, ledger: L, rule: rule, fees: fees, prices: [:], excluded: ["000001": "직접"])
check(selEx.picks.map(\.code) == ["000002"], "선정에서 예외 막음")
// 오늘 판 종목은 다시 고르지 않음
let selSold = MockCalc.select(scan, ledger: L, rule: rule, fees: fees, prices: [:], excluded: [:], soldToday: ["000001"])
check(selSold.picks.map(\.code) == ["000002"] && selSold.skips.contains { $0.code == "000001" && $0.why.hasPrefix("오늘 판") }, "오늘 판 종목 막음: \(selSold.skips)")

// 호가 채우기
let book = MockCalc.Book(asks: [.init(p: 8_440, v: 100), .init(p: 8_420, v: 50), .init(p: 8_460, v: 1000)],
                         bids: [.init(p: 8_400, v: 80), .init(p: 8_410, v: 30)], at: t0)
check(book.bestAsk == 8_420 && book.bestBid == 8_410, "호가 정렬")
let f1 = MockCalc.walk(side: .buy, type: .market, qty: 120, limit: nil, book: book, fees: fees)
check(f1.qty == 120 && near(f1.amount, 50 * 8_420 + 70 * 8_440) && f1.levels == 2, "시장가 2단: \(f1)")
let f2 = MockCalc.walk(side: .buy, type: .limit, qty: 120, limit: 8_430, book: book, fees: fees)
check(f2.qty == 50, "지정가는 그 값까지만: \(f2.qty)")
let f3 = MockCalc.walk(side: .buy, type: .limit, qty: 10, limit: 8_400, book: book, fees: fees)
check(f3.qty == 0, "지정가 미달 = 미체결")
let f4 = MockCalc.walk(side: .buy, type: .market, qty: 1000, limit: nil, book: book, fees: fees, cap: 500_000)
check(f4.qty > 0 && f4.amount + fees.fee(f4.amount) <= 500_000, "돈 한도: \(f4.qty) \(f4.amount)")
let f5 = MockCalc.walk(side: .sell, type: .market, qty: 100, limit: nil, book: book, fees: fees)
check(f5.qty == 100 && near(f5.amount, 30 * 8_410 + 70 * 8_400), "매도는 매수 호가부터")
let f6 = MockCalc.walk(side: .buy, type: .market, qty: 5000, limit: nil, book: book, fees: fees)
check(f6.qty == 1150, "호가가 모자라면 있는 만큼: \(f6.qty)")

// 장부에 적기: 매수 → 매도
let r = MockCalc.reserve(type: .market, qty: 120, limit: nil, bestAsk: 8_420, fees: fees, cash: L.cash)
L.cash -= r
L.pending.append(MockOrder(code: "000001", name: "가", side: .buy, type: .market, qty: 120, at: t0, who: .rule, reason: "1위", reserved: r))
let eBuy = MockCalc.applyBuy(&L, orderIndex: 0, fill: f1, fees: fees, at: t0, test: false)
check(L.pending.isEmpty && L.positions["000001"]?.qty == 120, "매수 체결 반영")
check(near(L.cash, 10_000_000 - f1.amount - fees.fee(f1.amount)), "현금 = 시작 − 금액 − 수수료: \(L.cash)")
check(eBuy.fee == fees.fee(f1.amount) && eBuy.side == .buy, "매수 기록")
let eq0 = MockCalc.equity(L, prices: ["000001": f1.avg])
check(near(eq0, 10_000_000 - fees.fee(f1.amount)), "평가액 = 시작 − 수수료")
L.pending.append(MockOrder(code: "000001", name: "가", side: .sell, type: .market, qty: 120, at: t0, who: .rule, reason: "익절"))
let sellBook = MockCalc.Book(asks: [], bids: [.init(p: 9_000, v: 1000)], at: t0)
let fs = MockCalc.walk(side: .sell, type: .market, qty: 120, limit: nil, book: sellBook, fees: fees)
let eSell = MockCalc.applySell(&L, orderIndex: 0, fill: fs, fees: fees, at: t0.addingTimeInterval(3600), test: false)!
let amt = 120 * 9_000.0
check(L.positions.isEmpty && L.pending.isEmpty, "다 팔림")
check(near(eSell.pnl!, amt - fees.fee(amt) - fees.tax(amt) - (f1.amount + fees.fee(f1.amount))), "손익 = 받은 돈 − 들어간 돈")
check(near(eSell.pnlGross!, amt - f1.amount), "수수료 전 손익")
check(near(L.cash + L.settling, 10_000_000 + eSell.pnl!), "현금 + 결제 대기 = 시작 + 손익: \(L.cash)")
// 결제 T+2: 판 돈은 결제 대기, 2영업일 뒤 현금
let sd = MockCalc.settleDay(t0.addingTimeInterval(3600))
check(near(L.settling, amt - fees.fee(amt) - fees.tax(amt)) && L.unsettled?.first?.day == sd, "판 돈은 결제 대기 (\(sd))")
check(MockCalc.reconcile(L, events: [eBuy, eSell]) == nil, "잔고 맞추기: 기록과 맞음")
check(MockCalc.settle(&L, today: MockFmt.day(t0)).isEmpty, "결제일 전엔 그대로")
check(MockCalc.settle(&L, today: sd).count == 1 && near(L.cash, 10_000_000 + eSell.pnl!) && L.unsettled == nil, "결제일에 현금으로")
var Lbad = L; Lbad.cash += 5_000
check(MockCalc.reconcile(Lbad, events: [eBuy, eSell])?.hasPrefix("현금") == true, "잔고 맞추기: 어긋남 찾음")
let kf = DateFormatter(); kf.timeZone = MockFmt.kst; kf.dateFormat = "yyyy-MM-dd HH:mm"
check(MockCalc.settleDay(kf.date(from: "2026-10-30 10:00")!) == "2026-11-03" && MockCalc.settleDay(kf.date(from: "2026-10-26 10:00")!) == "2026-10-28", "T+2 주말 건너뜀")
check(near(L.fees, fees.fee(f1.amount) + fees.fee(amt)) && near(L.taxes, fees.tax(amt)), "수수료·세금 합")

// 부분 매도: 들어간 돈 비율
var L2 = MockLedger(book: .rule, started: t0)
L2.positions["X"] = MockPosition(code: "X", name: "X", qty: 10, cost: 100_015, gross: 100_000, opened: t0, openedDay: "2026-10-01", who: .hand, test: false)
L2.pending.append(MockOrder(code: "X", name: "X", side: .sell, type: .limit, qty: 4, limit: 11_000, at: t0, who: .rule, reason: ""))
let fp = MockCalc.walk(side: .sell, type: .limit, qty: 4, limit: 11_000, book: MockCalc.Book(asks: [], bids: [.init(p: 11_000, v: 10)], at: nil), fees: fees)
let ep = MockCalc.applySell(&L2, orderIndex: 0, fill: fp, fees: fees, at: t0, test: true)!
check(L2.positions["X"]?.qty == 6 && near(L2.positions["X"]!.cost, 100_015 * 0.6), "부분 매도 남은 원가")
check(ep.opener == .hand && ep.test, "성적은 산 쪽(손) · 시험")

// 성적
let evs = [eBuy, eSell, ep]
let st = MockCalc.stat(evs, ledger: L.id)
check(st.trades == 1 && st.wins == 1, "정식 1건")
check(MockCalc.stat(evs, ledger: L2.id, test: true).trades == 1, "시험은 따로")
check(MockCalc.stat(evs, ledger: L.id, who: .hand).trades == 0, "규칙/손 나눔")
check(MockCalc.periods(evs, ledger: L.id, unit: .day).count == 1, "기간 칸")

// 팔기 신호
var L3 = MockLedger(book: .rule, started: t0)
L3.positions["A"] = MockPosition(code: "A", name: "A", qty: 10, cost: 100_015, gross: 100_000, opened: t0, openedDay: "2026-10-19", who: .rule, test: false)
L3.positions["B"] = MockPosition(code: "B", name: "B", qty: 10, cost: 100_015, gross: 100_000, opened: t0, openedDay: "2026-10-19", who: .rule, test: false)
let sig = MockCalc.sellSignals(L3, rule: MockSellRule(stopLoss: 0.05, takeProfit: 0.1), prices: ["A": 9_400, "B": 11_100], excluded: [:], today: "2026-10-23")
check(sig.map(\.code) == ["A", "B"] && sig[0].why.hasPrefix("손절") && sig[1].why.hasPrefix("익절"), "손절·익절: \(sig)")
check(MockCalc.sellSignals(L3, rule: MockSellRule(stopLoss: 0.05), prices: ["A": 9_400], excluded: ["A": "직접"], today: "2026-10-23").isEmpty, "예외는 안 팜")
check(MockCalc.weekdays(from: "2026-10-23", to: "2026-10-26") == 1, "금→월 평일 1일")
check(MockCalc.sellSignals(L3, rule: MockSellRule(maxDays: 3), prices: ["A": 10_000, "B": 10_000], excluded: [:], today: "2026-10-23").count == 2, "보유 일수")

// 호가 단위
check(MockCalc.tick(1_999) == 1 && MockCalc.tick(2_000) == 5 && MockCalc.tick(49_950) == 50 && MockCalc.tick(500_000) == 1_000, "호가 단위")
check(MockCalc.snap(8_423, side: .buy) == 8_420 && MockCalc.snap(8_423, side: .sell) == 8_430, "호가 맞춤")

// 이유 따라가기
let scanE = MockEvent(at: t0, ledger: L.id, book: .rule, kind: .scan, text: "훑기")
let selE = MockEvent(at: t0, ledger: L.id, book: .rule, kind: .select, code: "000001", text: "1위", ref: scanE.id)
let oid = UUID()
let ordE = MockEvent(id: oid, at: t0, ledger: L.id, book: .rule, kind: .order, code: "000001", text: "시장가", ref: selE.id)
var fillE = eBuy; fillE.ref = oid
let ch = MockCalc.chain(fillE, in: [scanE, selE, ordE, fillE])
check(ch.map(\.kind) == [.scan, .select, .order, .fill], "이유 사슬: \(ch.map(\.kind))")

// AI 답 확인: 후보 밖·예외·현금 넘김·보유 아님·3건 넘음
do {
    var l = MockLedger(book: .ai, started: t0)
    l.cash = 1_000_000
    l.positions["000003"] = MockPosition(code: "000003", name: "셋", qty: 10, cost: 100_000, gross: 100_000, opened: t0, openedDay: "2026-10-01", who: .ai, test: false)
    let ans: [String: Any] = ["note": "판단", "orders": [
        ["code": "000001", "side": "buy", "won": 700_000.0, "qty": 0, "why": "a"],
        ["code": "000009", "side": "buy", "won": 100_000.0, "qty": 0, "why": "후보 밖"],
        ["code": "000002", "side": "buy", "won": 900_000.0, "qty": 0, "why": "현금 넘김"],
        ["code": "000004", "side": "sell", "won": 0, "qty": 0, "why": "보유 아님"],
        ["code": "000003", "side": "sell", "won": 0, "qty": 4, "why": "일부"],
        ["code": "000005", "side": "buy", "won": 1.0, "qty": 0, "why": "넷째"]]]
    let r = MockAI.ledgerPlans(ans, ledger: l, candidates: ["000001": "하나", "000002": "둘", "000005": "다섯"], excluded: ["000005": "직접"])
    check(r.plans.map(\.code) == ["000001", "000002", "000003"], "AI 주문 고르기: \(r.plans.map(\.code)) \(r.dropped)")
    check(near(r.plans[1].won, 300_000), "남은 현금까지만: \(r.plans[1].won)")
    check(r.plans[2].qty == 4 && r.plans[2].side == .sell, "일부 매도")
    check(r.dropped.count == 3, "버린 셋: \(r.dropped)")
    // 전부 팔기 (qty 0 또는 보유보다 많으면 nil)
    let r2 = MockAI.ledgerPlans(["orders": [["code": "000003", "side": "sell", "won": 0, "qty": 99, "why": ""]]], ledger: l, candidates: [:], excluded: [:])
    check(r2.plans.first?.qty == nil, "전부 매도")
    check(MockAI.ledgerPrompt(MockLedger(book: .ai, started: t0), prices: [:], hits: [], bundleName: nil, excluded: [:], day: "2026-10-26") == nil, "후보·보유 없으면 안 부름")
    let words: [String: Any] = ["name": "거래량", "sort": "volume", "note": "", "pieces": [
        ["kind": "volume", "hasLo": true, "lo": 3.0, "hasHi": false, "hi": 0.0, "days": 500, "above": true],
        ["kind": "change", "hasLo": true, "lo": 8.0, "hasHi": true, "hi": 2.0, "days": 20, "above": true],
        ["kind": "ma", "hasLo": true, "lo": 5.0, "hasHi": false, "hi": 0.0, "days": 60, "above": false],
        ["kind": "nope", "hasLo": false, "lo": 0.0, "hasHi": false, "hi": 0.0, "days": 20, "above": true]]]
    let wb = MockAI.wordsBundle(words, words: "거래량 세 배")!
    check(wb.bundle.pieces.count == 3, "모르는 조각은 버림")
    check(wb.bundle.pieces[0].days == 120 && wb.bundle.pieces[0].hi == nil, "날 수 120까지")
    check(wb.bundle.pieces[1].lo == 2 && wb.bundle.pieces[1].hi == 8, "lo·hi 뒤바뀜 바로잡기")
    check(wb.bundle.pieces[2].lo == nil && !wb.bundle.pieces[2].above, "이동평균은 위·아래만")
    check(MockAI.wordsBundle(["pieces": []], words: "x") == nil, "조각 없으면 nil")
    let v = MockAI.reviewViews(["items": [["code": "000001", "view": "drop", "why": "w"], ["code": "999999", "view": "agree", "why": ""], ["code": "000002", "view": "??", "why": ""]]],
                               picks: [MockCalc.Pick(code: "000001", name: "하나", price: 1, budget: 1, qty: 1, rank: 1, why: "", weight: 0),
                                       MockCalc.Pick(code: "000002", name: "둘", price: 1, budget: 1, qty: 1, rank: 2, why: "", weight: 0)])
    check(v.count == 2 && v["000001"]?.view == "drop" && v["000002"]?.view == "doubt", "선정 의견 거르기")
    let w = MockAI.wallet(["spent": ["day": 0.5, "week": 1.0, "month": 2.0], "key": true], at: t0)
    check(w?.day == 0.5 && w?.month == 2, "쓴 돈 읽기")
}

// 기록 저장 왕복
let enc = JSONEncoder(); enc.dateEncodingStrategy = .secondsSince1970
let dec = JSONDecoder(); dec.dateDecodingStrategy = .secondsSince1970
let back = try! dec.decode(MockLedger.self, from: enc.encode(L2))
check(back == L2, "장부 저장 왕복")
let sBack = try! dec.decode(MockScan.self, from: enc.encode(scan))
check(sBack == scan, "훑기 저장 왕복")

// 시각 보정 거래량 (새 배수): 옛 결과는 그대로, 새 배수로 걸린 종목은 따로
do {
    let f = DateFormatter(); f.timeZone = MockFmt.kst; f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd HH:mm"
    let at10 = f.date(from: "2026-09-22 10:00")!
    // 1분봉 390개가 같은 양이면 10시(60분)에 60/390 쌓인다
    let flat = (1...390).map { i in MockBar(d: String(format: "%02d:%02d", (540 + i) / 60, (540 + i) % 60), o: 1, h: 1, l: 1, c: 1, v: 10) }
    let cum = MockCalc.curve(Array(repeating: flat, count: 5))!
    check(cum.count == 27 && near(cum[4], 60.0 / 390) && near(cum[26], 1), "곡선 칸: \(cum[4]) \(cum[26])")
    check(MockCalc.curve([flat]) == nil, "종목 5개 안 되면 곡선 없음")
    let curves = [MockCurve(day: "2026-09-21", cum: cum)]
    let fr = MockCalc.frac(curves, at: at10)!
    check(near(fr, 60.0 / 390), "10시 쌓임 비율 \(fr)")
    check(MockCalc.frac(curves, at: f.date(from: "2026-09-22 08:50")!) == nil, "장 전은 nil")
    check(MockCalc.frac(curves, at: f.date(from: "2026-09-22 16:00")!) == 1, "장 끝 뒤는 1")
    check(MockCalc.frac([], at: at10) == nil, "곡선 없으면 nil")
    check(MockCalc.curveNow([]) == nil && MockCalc.curveNow([MockCurve(day: "x", cum: [0.5])]) == nil, "27칸 곡선 없으면 curveNow nil")
    check(MockCalc.curveNow(curves + [MockCurve(day: "2026-09-22", cum: cum.map { min(1, $0 + 0.1) }), MockCurve(day: "2026-09-23", cum: cum)]) == cum, "curveNow 는 칸별 가운데 값")
    // 거래량 0.5배라도 10시 기준이면 새 배수 3.25배 → 새로 걸림
    var b2 = allBars; b2["000005"] = bars(9_000, lastUp: 0.03, volMul: 0.5)
    let st2 = stocks + [MockStock(code: "000005", name: "마", kospi: true)]
    let sc = MockCalc.scan(bundle, stocks: st2, bars: b2, excluded: [], at: at10, frac: fr)
    check(sc.hits.map(\.code) == ["000001", "000002"], "옛 결과는 그대로: \(sc.hits.map(\.code))")
    check(Set(sc.newHits ?? []) == ["000001", "000002", "000005"], "새 배수 걸린: \(sc.newHits ?? [])")
    check(near(sc.hits[0].volNew ?? 0, 3.1 / fr), "새 배수 값")
    let notToday = MockCalc.scan(bundle, stocks: st2, bars: b2, excluded: [], at: t0, frac: fr)
    check(notToday.frac == nil && notToday.newHits == nil && notToday.hits[0].volNew == nil, "오늘 봉이 아니면 새 배수 안 잼")
    check(MockCalc.big(b2, n: 2) == ["000003", "000002"], "큰 종목: \(MockCalc.big(b2, n: 2))")
}

// 실전 판단: 예상가·차이·확인 카드·정정·유의사항·보유
do {
    let fz = MockFees()
    let bk = MockCalc.Book(asks: [.init(p: 10_000, v: 5), .init(p: 10_100, v: 5)], bids: [.init(p: 9_900, v: 10)], at: nil)
    check(MockCalc.expect(side: .buy, type: .market, qty: 10, limit: nil, book: bk, fees: fz) == 10_050, "예상가 = 호가 끝까지 평균")
    check(MockCalc.expect(side: .buy, type: .limit, qty: 10, limit: 9_000, book: bk, fees: fz) == nil, "못 닿는 지정가는 예상가 없음")
    check(near(MockCalc.slipGap(side: .buy, expect: 10_000, actual: 10_100), 0.01), "매수 비싸게 = +")
    check(near(MockCalc.slipGap(side: .sell, expect: 10_000, actual: 9_900), 0.01), "매도 싸게 = +")
    let pv = MockCalc.preview(side: .buy, type: .market, qty: 10, limit: nil, book: bk, fees: fz)
    check(pv.qty == 10 && pv.amount == 100_500 && pv.fee == fz.fee(100_500) && pv.tax == 0 && near(pv.slip, 0.005), "확인 카드: \(pv)")
    let ps = MockCalc.preview(side: .sell, type: .market, qty: 10, limit: nil, book: bk, fees: fz)
    check(ps.tax == fz.tax(99_000) && ps.slip == 0, "매도 확인 카드 세금")

    var lg = MockLedger(book: .rule, started: t0)
    lg.cash = 60_000
    lg.pending.append(MockOrder(code: "Q", name: "Q", side: .buy, type: .limit, qty: 5, limit: 10_000, at: t0, who: .hand, reason: "", reserved: 50_008))
    check(MockCalc.modify(&lg, orderIndex: 0, qty: 10, limit: 10_000, fees: fz) == nil, "정정 매수 늘림")
    check(lg.pending[0].qty == 10 && lg.pending[0].reserved == (100_000 * (1 + fz.commission)).rounded(.up) && near(lg.cash, 60_000 - (lg.pending[0].reserved - 50_008)), "정정 묶은 돈: \(lg.cash)")
    check(MockCalc.modify(&lg, orderIndex: 0, qty: 100, limit: 10_000, fees: fz)?.hasPrefix("현금 부족") == true, "정정 현금 모자람")
    let c0 = lg.cash
    check(MockCalc.modify(&lg, orderIndex: 0, qty: 3, limit: 9_000, fees: fz) == nil && lg.cash > c0 && lg.pending[0].limit == 9_000, "정정 줄이면 돈 돌려줌")
    lg.positions["S"] = MockPosition(code: "S", name: "S", qty: 5, cost: 50_000, gross: 50_000, opened: t0, openedDay: "2026-10-01", who: .hand, test: false)
    lg.pending.append(MockOrder(code: "S", name: "S", side: .sell, type: .market, qty: 5, at: t0, who: .hand, reason: ""))
    check(MockCalc.modify(&lg, orderIndex: 1, qty: 6, limit: 11_000, fees: fz) != nil, "보유보다 많이 정정 못 함")
    check(MockCalc.modify(&lg, orderIndex: 1, qty: 4, limit: 11_000, fees: fz) == nil && lg.pending[1].type == .limit, "매도 정정 → 지정가")

    check(MockCalc.warnBlock(["VI_STATIC", "OVERHEATED"]) == nil, "VI·단기과열은 고르기 그대로")
    check(MockCalc.warnBlock(["INVESTMENT_RISK", "SUSPENDED"]) == "유의 거름 · 투자위험·거래정지", "투자위험·거래정지는 거름")
    let hp = MockPosition(code: "H", name: "H", qty: 10, cost: 100_015, gross: 100_000, opened: t0, openedDay: "2026-10-01", who: .hand, test: false)
    check(MockCalc.afterTax(hp, price: 11_000, fees: fz) == 110_000 - fz.fee(110_000) - fz.tax(110_000) - 100_015, "세후 손익")
    check(near(MockCalc.stopRoom(hp, price: 10_000, stop: 0.05), 10_000 / 9_500 - 1), "손절선까지")
}

print(fails == 0 ? "ALL OK" : "\(fails) FAILED")
exit(fails == 0 ? 0 : 1)
