#if MOCK
import Foundation

// 개발자 전용 모의투자 (docs/plans 구조 문서: naeilo 개발자 모의투자 구조, 2026-10-25).
// 이 폴더(Mock/)는 빌드 플래그 MOCK 으로 감싼다. 내부 베타 빌드에만 MOCK 이 켜지고, 공개 베타·출시에는 코드가 아예 들어가지 않는다.
// 주문 API 는 쓰지 않는다. 토스 시세·호가로 판단하고 체결은 기기 안 가상 장부에 적는다.

/// 시장. 지금은 국내만 돌지만 장부·기록은 처음부터 미국도 받게 둔다
enum MockMarket: String, Codable { case kr, us }

/// 누가 냈나: 규칙 · 손 · AI
enum MockWho: String, Codable, CaseIterable {
    case rule, hand, ai
    var name: String {
        switch self {
        case .rule: return "규칙"
        case .hand: return "손"
        case .ai: return "AI"
        }
    }
}

/// 장부 둘: 규칙 장부(손 주문도 여기에 적고 손으로 표시) · AI 장부
enum MockBook: String, Codable, CaseIterable {
    case rule, ai
    var name: String { self == .rule ? "규칙 장부" : "AI 장부" }
}

/// 일봉 한 칸 (토스 캔들). d = 한국 날짜 yyyy-MM-dd
struct MockBar: Codable, Equatable {
    var d: String
    var o: Double, h: Double, l: Double, c: Double, v: Double
}

// MARK: 관찰 조건

/// 조건 조각 하나. 값이 없는 쪽은 열어 둔다 (lo 만 있으면 '이상', hi 만 있으면 '이하')
struct MockPiece: Codable, Identifiable, Equatable, Hashable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case price      // 지금 가격 (원)
        case change     // 오늘 등락률 (%, 2 = +2%)
        case volume     // 오늘 거래량 ÷ 지난 N일 평균 (배)
        case ma         // 지금 가격과 N일 이동평균 (위·아래)
        case amount     // 오늘 거래대금 (억원)
        case rise       // 최근 N일 상승률 (%, 30 = +30%). 비정상 폭등 거르기
        var id: String { rawValue }
        var name: String {
            switch self {
            case .price: return "가격"
            case .change: return "등락률"
            case .volume: return "거래량"
            case .ma: return "이동평균"
            case .amount: return "거래대금"
            case .rise: return "최근 상승"
            }
        }
    }
    var id = UUID()
    var kind: Kind
    var lo: Double? = nil
    var hi: Double? = nil
    var days: Int = 20         // 거래량 평균·이동평균 날 수
    var above: Bool = true     // 이동평균: 가격이 위(참)·아래(거짓)

    /// 조각에 필요한 지난 봉 수 (오늘 봉 포함)
    var need: Int {
        switch kind {
        case .price, .amount: return 1
        case .change: return 2
        case .volume: return days + 1
        case .ma: return days
        case .rise: return days + 1
        }
    }

    /// 화면·기록 글: "거래량 ≥ 20일 평균 × 2"
    var text: String {
        func n(_ x: Double) -> String { MockFmt.num(x) }
        switch kind {
        case .price: return "가격 " + range(lo.map { n($0) + "원" }, hi.map { n($0) + "원" })
        case .change: return "등락률 " + range(lo.map { MockFmt.signed($0) + "%" }, hi.map { MockFmt.signed($0) + "%" })
        case .volume:
            let d = days == 20 ? "" : " (\(days)일)"
            return "거래량 " + range(lo.map { n($0) + "배" }, hi.map { n($0) + "배" }) + d
        case .rise: return "최근 \(days)일 " + range(lo.map { MockFmt.signed($0) + "%" }, hi.map { MockFmt.signed($0) + "%" })
        case .ma: return "가격 " + (above ? ">" : "<") + " \(days)일선"
        case .amount: return "거래대금 " + range(lo.map { n($0) + "억" }, hi.map { n($0) + "억" })
        }
    }
    private func range(_ a: String?, _ b: String?) -> String {
        switch (a, b) {
        case let (a?, b?): return a + " ~ " + b
        case let (a?, nil): return "≥ " + a
        case let (nil, b?): return "≤ " + b
        default: return "아무거나"
        }
    }
}

/// 조건 묶음: 이름 붙여 저장한다. 조각은 모두 맞아야(그리고) 걸린다
struct MockBundle: Codable, Identifiable, Equatable, Hashable {
    enum Scope: String, Codable, CaseIterable {
        case all, kospi, kosdaq
        var name: String {
            switch self {
            case .all: return "국내 전체"
            case .kospi: return "코스피"
            case .kosdaq: return "코스닥"
            }
        }
    }
    var id = UUID()
    var name: String
    var pieces: [MockPiece]
    var scope: Scope = .all
    /// 결과 줄 세우기 (큰 순). 선정 규칙이 따로 정하지 않으면 이 순서
    var sort: MockPiece.Kind = .volume
    var sortDays: Int = 20

    /// 이 묶음이 봐야 하는 지난 봉 수
    var need: Int { max(([sortPiece] + pieces).map(\.need).max() ?? 1, 2) }
    var sortPiece: MockPiece { MockPiece(kind: sort, days: sortDays) }
}

/// 훑을 종목 한 줄 (코드 · 이름 · 코스피 여부)
struct MockStock: Codable, Equatable, Hashable {
    var code: String
    var name: String
    var kospi: Bool
}

/// 훑기 결과의 걸린 종목
struct MockHit: Codable, Equatable, Identifiable {
    var id: String { code }
    var code: String
    var name: String
    var price: Double
    var change: Double?          // 오늘 등락률 (비율, 0.042 = +4.2%)
    var score: Double            // 줄 세우기 값 (묶음의 sort 조각)
    var why: String              // "거래량 3.1배 · +4.2% · 20일선 위"
    var vol: Double? = nil       // 거래량 배수 (지난 20일 평균 대비)
    var rise5: Double? = nil     // 최근 5일 상승률 (%)
    var volNew: Double? = nil    // 시각 보정 거래량 배수 (이 시각까지 보통 쌓이는 양 대비). 장중 훑기만
}

/// 관찰에 한 번 걸린 기록 (종목별 관찰 기록)
struct MockSeen: Codable, Equatable {
    var at: Date
    var price: Double
    var change: Double?
    var why: String
    var vol: Double? = nil       // 그때 거래량 배수 (옛 방식)
    var volNew: Double? = nil    // 그때 시각 보정 배수 (새 방식, 나란히 견주기용)
}

/// 하루 거래량 곡선: 9:00부터 15분마다 하루 거래량이 얼마나 쌓였는지 (27칸, 0~1). 큰 종목들의 가운데 값
struct MockCurve: Codable, Equatable {
    var day: String
    var cum: [Double]
}

/// 한 번 훑은 결과. 떨어진 종목은 이유(처음 걸린 조각 번호)별 수만 남긴다 (-1 = 자료 없음)
struct MockScan: Codable, Equatable, Identifiable {
    var id = UUID()
    var at: Date
    var bundleId: UUID
    var bundleName: String
    var pieceTexts: [String]
    var barDay: String           // 기준 일봉 날짜 (가장 늦은 봉)
    var total: Int               // 훑은 종목 수
    var hits: [MockHit]
    var dropped: [Int: Int]      // 조각 번호 → 떨어진 수
    var excluded: [String] = []  // 예외로 뺀 종목 코드
    var seconds: Double = 0
    var frac: Double? = nil      // 이 시각 하루 거래량 누적 비율 (곡선 기준). 있으면 새 배수도 쟀다
    var newHits: [String]? = nil // 새 배수로 걸린 종목 코드 (옛 결과와 나란히 견주기용, 고르기에는 안 쓴다)
}

/// 관찰 조건 하나 (알약). 켠 조건을 모두 맞는 종목이 걸린다
struct MockCond: Codable, Equatable, Identifiable {
    var piece: MockPiece
    var on: Bool = true
    var id: UUID { piece.id }
}

/// 고르기 조건 하나 (알약): 순서·상위 · 종목당 한도 · 현금 남김
struct MockRuleCond: Codable, Equatable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case top, per, cash, cap, slip
        var id: String { rawValue }
        var name: String {
            switch self {
            case .top: return "순서·상위"
            case .per: return "종목당 한도"
            case .cash: return "현금 남김"
            case .cap: return "폭등 거르기"
            case .slip: return "미끄러짐 한도"
            }
        }
    }
    var id = UUID()
    var kind: Kind
    var on: Bool = true
    var sort: MockPiece.Kind = .volume   // top: 줄 세우기 (큰 순)
    var n: Int = 5                       // top: 몇 종목
    var pct: Double = 0.2                // per · cash
    var capKind: MockPiece.Kind = .volume // cap: 거래량 배수(.volume) · 최근 5일 상승(.rise)
    var lim: Double = 50                 // cap: 이 값을 넘으면 고르지 않는다 (배 · %)
    var text: String {
        switch kind {
        case .top: return "\(sort.name) 순 상위 \(n)"
        case .per: return "종목당 ≤\(Int((pct * 100).rounded()))%"
        case .cash: return "현금 \(Int((pct * 100).rounded()))% 남김"
        case .cap: return capKind == .rise ? "최근 5일 ≤ +\(MockFmt.num(lim))%" : "거래량 ≤ \(MockFmt.num(lim))배"
        case .slip: return "미끄러짐 ≤\(MockFmt.num(pct * 100))%"
        }
    }
    /// cap: 걸린 종목이 이 조건을 넘나 (값이 없으면 넘지 않은 것으로 본다)
    func over(_ h: MockHit) -> Bool {
        guard kind == .cap else { return false }
        if capKind == .rise { return (h.rise5 ?? -.infinity) > lim }
        return (h.vol ?? 0) > lim
    }
}

/// 팔기 조건 알약 하나 (켠 알약으로 sell 값을 맞춘다)
struct MockSellCond: Codable, Equatable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case loss, gain, days
        var id: String { rawValue }
        var name: String {
            switch self {
            case .loss: return "손절"
            case .gain: return "익절"
            case .days: return "오래 들면"
            }
        }
    }
    var id = UUID()
    var kind: Kind
    var on: Bool = true
    var pct: Double = 0.05   // loss · gain: 산 값 대비
    var n: Int = 5           // days: 산 뒤 거래일
    var text: String {
        switch kind {
        case .loss: return "손절 −\(MockFmt.num((pct * 100).rounded()))%"
        case .gain: return "익절 +\(MockFmt.num((pct * 100).rounded()))%"
        case .days: return "보유 \(n)일 넘음"
        }
    }
}

/// 평가액 한 점 (내 수익률 꺾은선)
struct MockMark: Codable, Equatable {
    var at: Date
    var eq: Double
    var ledger: UUID
}

// MARK: 예외 종목

/// 규칙이 사지도 팔지도 않는 종목. 직접 넣은 것 + 조건 묶음 + 앱의 내 보유 종목 전부
struct MockExceptions: Codable, Equatable {
    var direct: [MockStock] = []
    var bundles: [MockBundle] = []
    var mine: Bool = true
}

// MARK: 선정·팔기 규칙

/// 선정 규칙: 관찰 결과에서 몇 개를, 무슨 순서로, 얼마씩
struct MockSelectRule: Codable, Equatable {
    var name: String = "상위 5"
    var count: Int = 5            // 장부에 둘 최대 종목 수
    var maxPer: Double = 0.20     // 한 종목 최대 (평가액 대비)
    var keepCash: Double = 0.20   // 남길 현금 (평가액 대비)
    /// 검토 문: 기본은 닫는다(켬 = 고른 뒤 내가 보고 주문, 끔 = 고르면 바로 주문)
    var review: Bool = true
    /// 미끄러짐 한도 (0.005 = 0.5%). 시장가를 끝까지 채우면 평균 단가가 1호가에서 이만큼 넘게 벗어날 때 나눠 산다·판다 (없으면 끔)
    var slip: Double? = nil
}

/// 팔기 규칙 (값이 없으면 끔). 관찰 주기마다 장중 가격으로 판단한다
struct MockSellRule: Codable, Equatable {
    var stopLoss: Double? = nil   // 0.05 = 산 값보다 5% 내리면
    var takeProfit: Double? = nil // 0.10 = 10% 오르면
    var maxDays: Int? = nil       // 산 뒤 거래일 N일이 지나면
    var on: Bool { stopLoss != nil || takeProfit != nil || maxDays != nil }
}

/// 안전장치 (실전 전에 꼭 필요한 막음). 기본: 하루 손실 −3%, 한 번 주문 300만원
struct MockGuards: Codable, Equatable {
    var dayLoss: Double = 0.03        // 오늘 시작 평가액보다 이만큼 내리면 새로 사지 않는다
    var orderCap: Double = 3_000_000  // 한 번 매수 주문 금액 상한 (원, 수수료 포함)
    var apiPerSec: Double? = nil      // 토스 시세 호출 한도 (초당 번, 없으면 5)
    var perSec: Double { apiPerSec ?? 5 }
}

/// 수수료·세금 (설정값). 2026-10 확인: 토스증권 국내 0.015%, 증권거래세 매도 0.20%(코스피 거래세 0.05%+농특세 0.15%, 코스닥 0.20%)
struct MockFees: Codable, Equatable {
    var commission: Double = 0.00015
    var sellTax: Double = 0.0020
    /// 한 번 체결의 수수료 (원 아래 버림)
    func fee(_ amount: Double) -> Double { (amount * commission).rounded(.down) }
    func tax(_ amount: Double) -> Double { (amount * sellTax).rounded(.down) }
}

// MARK: 장부

enum MockSide: String, Codable { case buy, sell
    var name: String { self == .buy ? "매수" : "매도" }
}
/// 주문 상태: 접수 → 부분 체결 → 체결 · 취소 · 거부 (실전 주문 API 와 같은 단계)
enum MockOrderState: String, Codable { case accepted, partial, filled, canceled, rejected }

enum MockOrderType: String, Codable { case market, limit
    var name: String { self == .market ? "시장가" : "지정가" }
}

/// 보유 한 종목
struct MockPosition: Codable, Equatable, Identifiable {
    var id: String { code }
    var code: String
    var name: String
    var market: MockMarket = .kr
    var qty: Int
    var cost: Double             // 들어간 돈 (체결 금액 + 수수료)
    var gross: Double            // 체결 금액만 (수수료 전)
    var opened: Date
    var openedDay: String        // 산 날 (거래일 셈)
    var who: MockWho
    var test: Bool               // 시험 체결로 산 몫이 섞였나
    var buyRef: UUID? = nil      // 처음 산 주문 (이유 따라가기)
    var tQty: Int? = nil         // 그중 시험 몫 (없으면 옛 상태라 전부 시험으로 본다)
    var tCost: Double? = nil
    var tGross: Double? = nil
    var avg: Double { qty > 0 ? gross / Double(qty) : 0 }
}

/// 주문 (미체결 대기도 이것). 매수는 넣을 때 현금에서 묶어 둔다(reserved)
struct MockOrder: Codable, Equatable, Identifiable {
    var id = UUID()
    var code: String
    var name: String
    var market: MockMarket = .kr
    var side: MockSide
    var type: MockOrderType
    var qty: Int                 // 남은 수량
    var limit: Double? = nil
    var at: Date
    var who: MockWho
    var reason: String
    var reserved: Double = 0     // 묶어 둔 현금 (매수)
    var ref: UUID? = nil         // 이 주문을 낸 선정 기록
    var waitWhy: String? = nil   // 왜 대기 중인가 (마지막 확인)
    var until: Date? = nil       // 나눈 남은 몫: 이때까지 다 못 채우면 취소
    var state: MockOrderState? = nil   // 접수 · 부분 체결 (대기 목록에 있는 동안)
    var prev: Double? = nil      // 전일 종가 (가격제한폭 ±30% 확인. 모르면 nil)
    var auction: Bool? = nil     // 마감 동시호가에 접수: 15:30 단일가로 체결
    var expect: Double? = nil    // 주문 순간 호가로 잰 예상 평균가 (체결가와 견주기)
    var tossId: String? = nil    // 실전: 토스 주문번호
}

/// 결제 대기 돈 한 건: 판 날부터 2영업일 뒤(T+2)에 현금이 된다
struct MockSettle: Codable, Equatable {
    var amount: Double
    var day: String              // 결제일 (yyyy-MM-dd)
    var code: String
}

/// 가상 계좌 하나
struct MockLedger: Codable, Equatable, Identifiable {
    var id = UUID()
    var book: MockBook
    var startCash: Double = 10_000_000
    var cash: Double = 10_000_000          // 묶이지 않은 현금
    var positions: [String: MockPosition] = [:]
    var pending: [MockOrder] = []
    var started: Date
    var ended: Date? = nil                 // 처음부터 다시 하면 닫고 보관
    var fees: Double = 0                   // 낸 수수료 합
    var taxes: Double = 0                  // 낸 세금 합
    var unsettled: [MockSettle]? = nil     // 판 돈 중 결제 대기 (T+2). 이 돈으로는 사지 못한다
    var reserved: Double { pending.reduce(0) { $0 + $1.reserved } }
    var settling: Double { (unsettled ?? []).reduce(0) { $0 + $1.amount } }
}

// MARK: 기록

/// 한 기록 파일에 차례로 쌓는 줄. 지난 줄은 고치지 않는다
struct MockEvent: Codable, Equatable, Identifiable {
    enum Kind: String, Codable {
        case scan, exclude, select, short, order, fill, wait, cancel, sell, reset, rules, ai, error, reject, settle, check
        var name: String {
            switch self {
            case .scan: return "관찰"
            case .exclude: return "예외"
            case .select: return "선정"
            case .short: return "현금 부족"
            case .order: return "주문"
            case .fill: return "체결"
            case .wait: return "미체결"
            case .cancel: return "취소"
            case .sell: return "팔기 신호"
            case .reset: return "장부 새로"
            case .rules: return "규칙 바뀜"
            case .ai: return "AI"
            case .error: return "오류"
            case .reject: return "거부"
            case .settle: return "결제"
            case .check: return "잔고 맞추기"
            }
        }
    }
    var id = UUID()
    var at: Date
    var ledger: UUID
    var book: MockBook
    var kind: Kind
    var who: MockWho? = nil
    var code: String? = nil
    var name: String? = nil
    var text: String                       // 이유 한 줄
    var test: Bool = false                 // 장 밖 시험
    var bundle: String? = nil              // 그때 조건 묶음 이름
    var rule: String? = nil                // 그때 선정 규칙 이름
    var side: MockSide? = nil
    var qty: Int? = nil
    var price: Double? = nil               // 평균 체결가
    var amount: Double? = nil              // 체결 금액
    var fee: Double? = nil
    var tax: Double? = nil
    var pnl: Double? = nil                 // 매도 체결: 판 몫의 손익 (수수료·세금 뒤)
    var pnlGross: Double? = nil            // 매도 체결: 수수료·세금 전
    var basis: Double? = nil               // 매도 체결: 판 몫의 들어간 돈
    var opener: MockWho? = nil             // 매도 체결: 그 몫을 산 쪽 (성적은 산 쪽으로 센다)
    var ref: UUID? = nil                   // 앞 단계 기록 (선정 → 주문 → 체결)
    var ai: MockAIUse? = nil
    var expect: Double? = nil              // 주문 · 체결: 주문 순간 예상 평균가
    var tossId: String? = nil              // 실전 주문 · 체결: 토스 주문번호
}

/// AI 한 번 쓴 기록
struct MockAIUse: Codable, Equatable {
    var model: String
    var input: Int
    var output: Int
    var cost: Double                       // 달러
    var summary: String                    // 보낸 요약 (짧게)
    var cached: Bool = false               // 같은 입력이라 지난 답을 다시 씀
}

// MARK: 화면 상태 한 줄

/// 탭마다 상태 한 줄: 마지막으로 돈 시각, 성공 여부, 실패 이유
struct MockRun: Codable, Equatable {
    var at: Date
    var ok: Bool
    var text: String
}

// MARK: 숫자 글

enum MockFmt {
    static func num(_ x: Double) -> String {
        if x == x.rounded() && abs(x) < 1e15 { return Int(x).formatted() }
        return String(format: "%.2f", x).replacingOccurrences(of: #"\.?0+$"#, with: "", options: .regularExpression)
    }
    static func won(_ x: Double) -> String { Int(x.rounded()).formatted() + "원" }
    /// " · " 로 이은 글에서 같은 조각은 한 번만 ("거래량 3.1배 · +4% · 거래량 3.1배" → "거래량 3.1배 · +4%")
    static func uniq(_ s: String) -> String {
        var seen = Set<String>()
        return s.components(separatedBy: " · ").filter { seen.insert($0).inserted }.joined(separator: " · ")
    }
    static func signed(_ x: Double) -> String { (x > 0 ? "+" : x < 0 ? "−" : "") + num(abs(x)) }
    /// 비율 → "+1.24%"
    static func pct(_ r: Double, digits: Int = 2) -> String {
        (r > 0 ? "+" : r < 0 ? "−" : "") + String(format: "%.\(digits)f%%", abs(r) * 100)
    }
    static let kst = TimeZone(identifier: "Asia/Seoul")!
    static func day(_ d: Date) -> String {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.timeZone = kst
        f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
        return f.string(from: d)
    }
    static func time(_ d: Date) -> String {
        let f = DateFormatter(); f.timeZone = kst; f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "HH:mm"
        return f.string(from: d)
    }
    static func stamp(_ d: Date) -> String {
        let f = DateFormatter(); f.timeZone = kst; f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "M/d HH:mm"
        return f.string(from: d)
    }
}
// MARK: 실전 규칙 목록 (탭 우상단 '실전' 아이콘)

/// 모의투자에 실전 매매 규칙을 어떻게 반영했나. 규칙을 넣거나 바꾸면 여기도 함께 고친다 (화면은 이 목록만 보여 준다)
enum MockLive {
    struct Rule: Identifiable {
        let name: String
        let how: String          // 반영 방식
        let gap: String?         // 아직 실전과 다른 점
        var at: Set<Tab> = common  // 이 규칙이 보이는 탭
        var id: String { name }
    }
    /// 실전 목록이 보이는 곳: 관찰 = 실전에 더 볼 조건, 선정 = 매수 규칙, 일지 = 매도·수수료·세금·모두 멈춤
    enum Tab { case observe, select, journal }
    /// 모든 주문에 걸리는 공통 규칙: 선정 · 일지 둘 다
    static let common: Set<Tab> = [.select, .journal]
    static func rules(_ t: Tab) -> [Rule] { rules.filter { $0.at.contains(t) } }
    static let rules: [Rule] = [
        Rule(name: "주문층", how: "관찰·선정·팔기 판단은 브로커(주문 넣기·취소·상태 보기)만 부른다. 기본은 모의 브로커, 설정의 실전 스위치(기본 끔, 확인 한 번 더)를 켜면 토스 브로커",
             gap: nil),
        Rule(name: "토스 주문 API", how: "문서대로 주문 생성 POST /api/v1/orders(clientOrderId = 앱 주문 번호, 10분 멱등), 상세 GET /api/v1/orders/{id}, 취소 POST …/cancel. 세부 상태를 접수·부분 체결·체결·취소·거부로 바꾸고 오류 코드를 한국어로 기록",
             gap: "키를 넣는 화면이 없다(키체인 자리만). 토큰 발급(client_secret)은 문서상 서버에서만 하고 허용 IP 가 고정이어야 해서, 폰에서 바로 부르면 403 이 난다 → 고정 IP 개발자 서버를 거쳐야 한다"),
        Rule(name: "호출 한도", how: "그룹별 초당 한도(ORDER 10 · ORDER_HISTORY 5 · ORDER_INFO 6 · AUTH 5) 사이를 두고, 429 면 Retry-After 만큼 기다려 한 번 더",
             gap: "09:00~09:10 피크 한도(ORDER_INFO 3회)는 따로 맞추지 않는다"),
        Rule(name: "실전 장부", how: "실전에서도 앱 장부로 예산을 셈하고, 체결은 토스가 알려 준 수량·금액으로 적는다",
             gap: "토스 계좌 잔고·매수 가능 금액(buying-power)·매도 가능 수량(sellable-quantity)으로 장부를 맞추지 않는다. 수수료·세금은 앱 설정값으로 셈한다(토스 commission·tax 는 읽기만)"),
        Rule(name: "주문 상태", how: "접수 → 부분 체결 → 체결 · 취소 · 거부를 기록에 남긴다",
             gap: "모의는 앱이 호가로 셈한다. 실전은 토스 주문 상세를 1분마다 다시 읽는다(웹소켓 주문 알림은 아직 안 씀)"),
        Rule(name: "체결 지연", how: "접수 1.5초 뒤 새로 받은 호가로 체결한다 (그사이 호가가 바뀐다)",
             gap: "실제 지연은 망·거래소 상황마다 다르다"),
        Rule(name: "호가 잔량", how: "1호가부터 차례로 채우고, 먹은 잔량은 1분 동안 빼고 셈한다",
             gap: "다른 사람 주문으로 잔량이 다시 차는 것은 모른다"),
        Rule(name: "시장가 · 지정가", how: "지정가는 호가 단위(2023년 표)에 맞춘다. 매수는 내림, 매도는 올림", gap: nil),
        Rule(name: "미끄러짐 한도", how: "다 채운 평균 단가가 1호가에서 0.5% 넘게 벗어나면 그 값의 지정가로 나누고, 남은 몫은 30분 뒤 취소. 손절 매도는 한 번에",
             gap: nil, at: [.select]),
        Rule(name: "수수료 · 세금", how: "수수료 0.015% (사고팔 때), 매도 세금 0.20%. 설정에서 고친다",
             gap: "증권사 우대 수수료·세율이 바뀌면 손으로 맞춘다", at: [.journal]),
        Rule(name: "가격제한폭 ±30%", how: "전일 종가 ±30% 밖의 지정가는 거부한다",
             gap: "전일 종가를 아는 종목(마지막 훑기에 걸린 종목)만 확인한다", at: [.observe, .select, .journal]),
        Rule(name: "마감 동시호가", how: "15:20~15:30 에는 접수만 하고 15:30 단일가(종가)로 체결한다. 20분 안에 못 채우면 취소",
             gap: "장 시작 동시호가(08:30~09:00)는 장 밖 시험으로 처리한다"),
        Rule(name: "VI · 거래 정지", how: "호가가 양쪽 다 비어 있으면 거부한다 (거래 정지·VI 의심)",
             gap: "VI·정지 신호를 받지 못해 둘을 가르지 못한다. VI 2분 단일가는 흉내 내지 못한다", at: [.observe, .select, .journal]),
        Rule(name: "하루 손실 한도", how: "오늘 시작 평가액보다 −3%(설정) 넘게 내리면 새로 사지 않는다. 팔기·손 주문은 된다", gap: nil, at: [.select]),
        Rule(name: "한 번 주문 상한", how: "한 번 매수 주문은 300만원(설정)까지. 손 주문도", gap: nil, at: [.select]),
        Rule(name: "모두 멈춤", how: "설정의 단추. 모든 대기 주문을 취소하고 한 바퀴·돌리기·자동 팔기와 규칙 주문을 막는다. 손 주문은 된다", gap: nil, at: [.journal]),
        Rule(name: "결제 T+2", how: "판 돈은 2영업일 뒤에 현금이 된다. 그 전엔 '결제 대기'로 따로 두고 평가액에는 넣는다",
             gap: "공휴일은 몰라 주말만 뺀다. 장 밖 시험으로 판 돈은 바로 현금", at: [.journal]),
        Rule(name: "증거금 · 미수", how: "미수를 쓰지 않는다. 현금보다 큰 손 주문은 거부하고, 결제 대기 돈이면 '이틀 뒤 들어올 돈'이라 알린다",
             gap: "실전 증거금률(종목별 40~100%)은 흉내 내지 않는다. 늘 현금 100%", at: [.select]),
        Rule(name: "계좌 잔고 맞추기", how: "하루 한 번, 체결 기록을 다시 더해 장부 현금·보유 수량과 견준다. 어긋나면 설정 안전장치 칸에 표시만 한다",
             gap: "토스 계좌 잔고와는 견주지 못한다 (토스 브로커 연결 전)", at: [.journal]),
        Rule(name: "토스 API 호출 한도", how: "시세·호가 부르기를 초당 5번(설정)으로 줄 세우고, 한도 응답(429)이면 기다렸다 3번까지 다시 부른다",
             gap: "토스 실제 한도 숫자는 확인 못 했다. 서버 쪽 한도는 따로"),
        Rule(name: "실전 장 밖", how: "실전이 켜져 있으면 장 밖에는 주문하지 않는다", gap: "장 개시 단일가(OPG)·장 마감 지정가(CLS) 주문, 주문 정정, 웹소켓 체결 알림은 아직 쓰지 않는다"),
        Rule(name: "체결 알림", how: "체결 · 부분 체결 · 거부 때 휴대폰 알림. 앱을 보고 있으면 소리 없이 위쪽 띠로만. 설정에서 끈다",
             gap: "실전은 토스 체결 통지로 알린다. 지금은 앱이 셈한 체결로 알린다"),
    ]
}
#endif
