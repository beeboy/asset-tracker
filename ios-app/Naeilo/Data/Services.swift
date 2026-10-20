import Foundation

// 시세 제공 스텁: 시안과 같은 값을 만든다.
// 실제 연결은 이 프로토콜을 구현해 AppModel.prices 에 넣으면 된다.
protocol PriceProvider {
    /// 전일 종가
    func close(_ symbol: Symbol) -> Double
    /// 기간 가격 흐름 (마지막 값 = 지금 가격, 1일 = 전일 종가 → 지금)
    func series(_ symbol: Symbol, period: Period) -> [Double]
    /// x = 0 (3년 전) … 1 (어제) 시점의 종가
    func price(_ symbol: Symbol, at x: Double) -> Double
    /// 실제 과거 시세가 있는지 (없으면 과거는 평평한 선)
    func hasHistory(_ symbol: Symbol) -> Bool
}

enum Period: String, CaseIterable, Hashable {
    case d1, w1, m1, m3, m6, y1, y3
    var label: String {
        // 영어는 칸이 좁아 짧게 (1D·1W·1M…)
        if AppModel.en { switch self { case .d1: return "1D"; case .w1: return "1W"; case .m1: return "1M"; case .m3: return "3M"; case .m6: return "6M"; case .y1: return "1Y"; case .y3: return "3Y" } }
        return switch self { case .d1: String(localized: "1일"); case .w1: String(localized: "1주"); case .m1: String(localized: "1개월"); case .m3: String(localized: "3개월"); case .m6: String(localized: "6개월"); case .y1: String(localized: "1년"); case .y3: String(localized: "3년") }
    }
    /// 3년(1095일)에 대한 비율
    var span: Double {
        switch self { case .d1, .w1: 0; case .m1: 1.0 / 36; case .m3: 1.0 / 12; case .m6: 1.0 / 6; case .y1: 1.0 / 3; case .y3: 1 }
    }
}

struct StubPriceProvider: PriceProvider {
    // DRNK 가격 흐름 = 내 지난 자산 기록(2024-01-01~어제, 1010일)을 마지막 값 1로 나눈 비율
    let drnkRatio: [Double] = {
        guard let url = Bundle.main.url(forResource: "drnk_ratio", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let arr = try? JSONDecoder().decode([Double].self, from: data) else { return [1] }
        return arr
    }()

    func close(_ symbol: Symbol) -> Double { symbol.close }
    func price(_ s: Symbol, at x: Double) -> Double {
        // 국내 주식 합계: 그날까지 넣은 마지막 금액 (첫 기록 전은 0). x 를 3년 날짜로 (0 = 3년 전, 1 = 오늘)
        if s.id == KoreaEstimate.sumId { return MC.krAt(CustomSymbols.shared.krLog, Day.str(Date().addingTimeInterval(-(1 - x) * 1095.75 * 86400))) }
        if PriceHistory.of(s.id) != nil { return exp(logPrice(s, x)) }   // 실제 종가 그대로
        return s.close * exp(logPrice(s, x) - logPrice(s, 1))
    }

    /// 로그 가격. x = 0 (3년 전) … 1 (어제). 시안의 lfA 와 같은 식.
    func logPrice(_ s: Symbol, _ x: Double) -> Double {
        if s.id == KoreaEstimate.sumId { return log(max(1, price(s, at: x))) }
        if let h = PriceHistory.of(s.id), h.closes.count > 1 {
            // 3년치 거래일을 0…1 로 펴서 사이를 잇는다
            let L = Double(h.closes.count - 1), t = max(0, min(L, x * L)), i = Int(t), f = t - Double(i)
            return log(h.closes[i] * (1 - f) + h.closes[min(Int(L), i + 1)] * f)
        }
        if s.id == "DRNK" {
            let L = Double(drnkRatio.count - 1)
            let t = max(0, min(L, L - (1 - x) * 1095)), i = Int(t), f = t - Double(i)
            let v = drnkRatio[i] * (1 - f) + drnkRatio[min(Int(L), i + 1)] * f
            return log(v)
        }
        // 과거 시세가 없으면 만들어 내지 않는다: 전일 종가 그대로 (평평한 선). 화면은 hasHistory 로 "과거 시세 없음"을 알린다
        return log(max(1e-9, s.close))
    }

    /// 실제 과거 시세(또는 샘플 DRNK 기록)가 있는지
    func hasHistory(_ s: Symbol) -> Bool { s.id == "DRNK" || PriceHistory.of(s.id) != nil }

    private func dayCloses(_ s: Symbol, _ n: Int) -> [Double] {
        if let h = PriceHistory.of(s.id) { return Array(h.closes.suffix(n + 1)) }
        if s.id == "DRNK" { return drnkRatio.suffix(n + 1).map { s.close * $0 } }
        return Array(repeating: s.close, count: n + 1)
    }

    func series(_ s: Symbol, period: Period) -> [Double] {
        let q = Market.shared.quote(s)
        switch period {
        case .d1: return [q.prevClose, q.last]
        case .w1: return Array(dayCloses(s, 5).dropFirst()) + [q.last]
        default:
            if PriceHistory.of(s.id) != nil {
                // 테스트 자료: 마지막 날이 이미 오늘 값이라 그대로 쓰고 끝만 지금 가격으로 맞춘다
                let sp = period.span
                return (0...60).map { i in i == 60 ? q.last : exp(logPrice(s, 1 - sp + sp * Double(i) / 60)) }
            }
            let sp = period.span, k = q.last / max(1e-9, s.close)
            return (0...60).map { i in s.close * exp(logPrice(s, 1 - sp + sp * Double(i) / 60) - logPrice(s, 1)) * (i == 60 ? k : 1) }
        }
    }
}
