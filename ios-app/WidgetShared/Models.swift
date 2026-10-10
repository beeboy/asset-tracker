import Foundation

// 사이트(web/widget-core.js)가 만드는 위젯 요약. 금액 배열은 만원 단위 정수, 나머지 금액은 원
struct WSummary: Codable {
    struct Goal: Codable { var amount: Double; var date: String; var start: String; var v0: Double }
    struct Hold: Codable { var t: String; var sh: Double; var ccy: String; var manual: Double? }
    struct Term: Codable { var p5: Double?; var p50: Double?; var p95: Double? }
    struct Forecast: Codable {
        var scen: String
        var md: [String]
        var p5: [Double?]; var p25: [Double?]; var p50: [Double?]; var p75: [Double?]; var p95: [Double?]
        var pg: Double
        var t: Term?
    }
    struct Hit: Codable { var f: String; var h: Int; var r: Int? } // r = 1 사후 계산
    struct Week: Codable { var f: String; var p50: Double; var lo: Double; var hi: Double; var base: Double; var cash: Double; var t0: Double; var tent: Bool? }
    struct Event: Codable { var d: String; var t: String; var k: String }

    var v: Int
    var at: Double          // 계산한 시각 (ms)
    var asof: String        // 마지막 종가 날짜 (계산 기준)
    var today: String
    var cash: Double
    var goal: Goal
    var hold: [Hold]
    var spark: [Double?]
    var fc: Forecast?
    var hits: [Hit]
    var week: Week?
    var prev: Double
    var events: [Event]
}

// data/widget.json (30분마다 갱신되는 작은 시세 파일)
struct WFeed: Codable {
    struct Tail: Codable { var d: [String]; var c: [Double?]; var a: [Double?] }
    struct Mar: Codable { var rate: Double; var date: String? }
    var v: Int
    var u: String?
    var q: [String: [Double?]]      // [현재가, 전일 종가, 시각(초)]
    var c: [String: Tail]
    var ccy: [String: String]
    var mar: Mar?
}

// 동기화된 입력값에서 위젯이 바로 쓰는 부분 (전체 입력값은 계산용으로 따로 저장)
struct WLive: Codable {
    struct Hold: Codable { var t: String; var sh: Double; var price: Double? }
    var hold: [Hold]
    var cashKrw: Double
    var cashUsd: Double
    var goalAmount: Double
    var goalDate: String
    var goalStart: String?
    var manualPrice: Bool
    var stateAt: Double

    static func from(state: [String: Any], at: Double) -> WLive {
        let hs = (state["holdings"] as? [[String: Any]] ?? []).compactMap { h -> Hold? in
            guard let t = h["ticker"] as? String else { return nil }
            let sh = num(h["shares"]) ?? 0
            return sh > 0 ? Hold(t: t, sh: sh, price: num(h["price"])) : nil
        }
        let cash = state["cash"] as? [String: Any] ?? [:]
        let goal = state["goal"] as? [String: Any] ?? [:]
        let ui = state["ui"] as? [String: Any] ?? [:]
        return WLive(hold: hs, cashKrw: num(cash["krw"]) ?? 0, cashUsd: num(cash["usd"]) ?? 0,
                     goalAmount: num(goal["amount"]) ?? 1e9, goalDate: goal["date"] as? String ?? "",
                     goalStart: goal["start_date"] as? String, manualPrice: ui["manual_price"] as? Bool ?? false, stateAt: at)
    }
}

func num(_ v: Any?) -> Double? {
    if let d = v as? Double { return d }
    if let i = v as? Int { return Double(i) }
    if let s = v as? String { return Double(s.replacingOccurrences(of: ",", with: "")) }
    if let n = v as? NSNumber { return n.doubleValue }
    return nil
}
