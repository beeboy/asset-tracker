import Foundation
import Observation

// 지금 시세. Tiingo 회신(10월 10일): 월 $250 요금제로 미국 종목 실시간 참고 시세와 원/달러를 앱·위젯에 보여 줄 수 있다.
// 한국 종목은 Tiingo에 없어서 한국 시세 제공처(EODHD 또는 코스콤)가 정해질 때까지 전일 종가를 쓴다.
// 출처 표기 "Data powered by Tiingo.com"은 설정 화면에만 두면 된다.

struct Quote {
    let last: Double        // 지금 가격 (실시간이 아니면 전일 종가)
    let prevClose: Double   // 전일 종가
    let live: Bool
    var change: Double { prevClose > 0 ? last / prevClose - 1 : 0 }
}

@Observable
final class Market {
    static let shared = Market()

    var updatedAt = Date()
    /// Tiingo 에서 받은 값 (티커 → 지금 가격, 전일 종가). 비어 있으면 예시 값
    var live: [String: (Double, Double)] = [:]
    var liveFx: (Double, Double)? = nil
    var source: String { live.isEmpty ? "예시 값" : "Tiingo" }

    // 예시 값: 오늘 하루 움직임 (DRNK·QQQ 는 시안의 하루 움직임) + 분 단위로 조금씩 흔들림
    static let todayMove: [String: Double] = ["DRNK": -0.0221, "QQQ": 0.004]

    private func wobble(_ seed: Double) -> Double {
        let t = updatedAt.timeIntervalSinceReferenceDate / 60
        return 0.0015 * sin(t / 3 + seed)
    }
    private func seed(_ s: Symbol) -> Double { Double(s.id.unicodeScalars.reduce(0) { $0 + Int($1.value) }) }

    func quote(_ s: Symbol) -> Quote {
        if let q = live[s.id] { return Quote(last: q.0, prevClose: q.1, live: true) }
        guard s.currency == .usd else { return Quote(last: s.close, prevClose: s.close, live: false) }
        let m = (Self.todayMove[s.id] ?? 0.012 * sin(seed(s))) + wobble(seed(s))
        return Quote(last: s.close * (1 + m), prevClose: s.close, live: true)
    }

    /// 원/달러 (지금, 전일)
    var fx: Quote {
        if let f = liveFx { return Quote(last: f.0, prevClose: f.1, live: true) }
        return Quote(last: Sample.fx * (1 + 0.0021 + wobble(7)), prevClose: Sample.fx, live: true)
    }

    var asOfText: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "a h:mm"
        return "지금 시세 · \(f.string(from: updatedAt)) 갱신 · 한국 종목은 전일 종가"
    }

    func refresh() async {
        if let p = TiingoProvider.fromConfig() {
            let tickers = Sample.symbols.filter { $0.currency == .usd && $0.id != "DRNK" }.map(\.id)
            if let q = try? await p.quotes(tickers) { live = q }
            if let f = try? await p.usdkrw() { liveFx = f }
        }
        updatedAt = Date()
    }
}

extension Symbol {
    var quote: Quote { Market.shared.quote(self) }
    /// 지금 가격
    var last: Double { quote.last }
}

// Tiingo REST. 출시 때는 토큰을 앱에 넣지 않고 naeilo 중계 서버가 받아 캐시해서 내려준다 (견적 협의의 계약 조건).
// 개발 중에는 Info.plist 의 TIINGO_TOKEN 에 개발용 토큰을 넣으면 시뮬레이터에서 실제 값을 볼 수 있다.
struct TiingoProvider {
    let token: String

    static func fromConfig() -> TiingoProvider? {
        guard let t = Bundle.main.object(forInfoDictionaryKey: "TIINGO_TOKEN") as? String, !t.isEmpty else { return nil }
        return TiingoProvider(token: t)
    }

    private func get(_ path: String) async throws -> Any {
        var c = URLComponents(string: "https://api.tiingo.com" + path)!
        c.queryItems = (c.queryItems ?? []) + [URLQueryItem(name: "token", value: token)]
        let (data, _) = try await URLSession.shared.data(from: c.url!)
        return try JSONSerialization.jsonObject(with: data)
    }

    /// IEX 실시간 참고 시세: tngoLast(지금), prevClose(전일 종가)
    func quotes(_ tickers: [String]) async throws -> [String: (Double, Double)] {
        guard !tickers.isEmpty else { return [:] }
        let arr = try await get("/iex/?tickers=" + tickers.joined(separator: ",").lowercased()) as? [[String: Any]] ?? []
        var out: [String: (Double, Double)] = [:]
        for o in arr {
            guard let t = (o["ticker"] as? String)?.uppercased(), let last = (o["tngoLast"] ?? o["last"]) as? Double,
                  let prev = o["prevClose"] as? Double else { continue }
            out[t] = (last, prev)
        }
        return out
    }

    /// 원/달러: fx/top 의 midPrice, 전일 값은 일별 끝값
    func usdkrw() async throws -> (Double, Double)? {
        let arr = try await get("/tiingo/fx/top?tickers=usdkrw") as? [[String: Any]] ?? []
        guard let o = arr.first, let mid = (o["midPrice"] ?? o["bidPrice"]) as? Double else { return nil }
        let start = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-6 * 86400)).prefix(10)
        let hist = try await get("/tiingo/fx/usdkrw/prices?resampleFreq=1day&startDate=\(start)") as? [[String: Any]] ?? []
        let prev = hist.dropLast().last?["close"] as? Double ?? mid
        return (mid, prev)
    }
}
