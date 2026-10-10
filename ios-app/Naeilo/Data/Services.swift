import Foundation

// 시세 제공. 계약(데이터 형식)은 다른 스레드에서 정하는 중이라, 지금은 시안과 같은 값을 만드는 스텁만 있다.
// 실제 연결은 이 프로토콜을 구현해 AppModel.prices 에 넣으면 된다.
protocol PriceProvider {
    /// 전일 종가
    func close(_ symbol: Symbol) -> Double
    /// 기간 가격 흐름 (마지막 값 = 지금 가격, 1일 = 전일 종가 → 지금)
    func series(_ symbol: Symbol, period: Period) -> [Double]
    /// x = 0 (3년 전) … 1 (어제) 시점의 종가
    func price(_ symbol: Symbol, at x: Double) -> Double
}

enum Period: String, CaseIterable, Hashable {
    case d1, w1, m1, m3, m6, y1, y3
    var label: String {
        switch self { case .d1: "1일"; case .w1: "1주"; case .m1: "1개월"; case .m3: "3개월"; case .m6: "6개월"; case .y1: "1년"; case .y3: "3년" }
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
        if PriceHistory.of(s.id) != nil { return exp(logPrice(s, x)) }   // 실제 종가 그대로
        return s.close * exp(logPrice(s, x) - logPrice(s, 1))
    }

    private func seed(_ s: Symbol) -> Double { Double(s.id.unicodeScalars.reduce(0) { $0 + Int($1.value) }) }

    /// 로그 가격. x = 0 (3년 전) … 1 (어제). 시안의 lfA 와 같은 식.
    func logPrice(_ s: Symbol, _ x: Double) -> Double {
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
        let sd = seed(s), u = 1 - x
        let amp = s.currency == .krw && s.sector == "반도체" ? 0.45 : 0.25
        let ph = sd.truncatingRemainder(dividingBy: 7)
        return amp * (sin(u * 5 + ph) - sin(ph)) * pow(u, 0.4) + 0.05 * sin(x * 60 + sd) * u
    }

    private func dayCloses(_ s: Symbol, _ n: Int) -> [Double] {
        if let h = PriceHistory.of(s.id) { return Array(h.closes.suffix(n + 1)) }
        if s.id == "DRNK" { return drnkRatio.suffix(n + 1).map { s.close * $0 } }
        let p0 = s.close
        let m1 = Sample.dayMove[s.id] ?? 0.012 * sin(seed(s))
        var out = [p0, p0 / (1 + m1)]
        if n >= 2 {
            for k in 2...n {
                out.append(s.close * exp(logPrice(s, 1 - Double(k) / 780) - logPrice(s, 1)) * (1 + 0.014 * sin(Double(k) * 2.3 + seed(s))))
            }
        }
        return out.reversed()
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

// 외전 원고는 앱에 넣지 않고 naeilo 서버에서 받는다. 지금은 문단 길이만 주는 스텁 (본문 자리는 회색 줄).
struct StoryParagraph { let lines: Int; let lastWidth: Double }

protocol StoryProvider {
    func chapter(_ index: Int) async -> [StoryParagraph]
}

struct StubStoryProvider: StoryProvider {
    func chapter(_ index: Int) async -> [StoryParagraph] {
        (0..<6).map { p in StoryParagraph(lines: 3 + (index + p) % 3, lastWidth: 0.4 + Double((p * 17 + index * 7) % 40) / 100) }
    }
}
