import Foundation

// 내 몫 실적: 아이폰 Earnings.swift 가 tools/earn_fixtures.json 의 기대값(= tools/earn_calc.mjs 의 답)과 같은지 본다 (금액은 1e-9 이내).
// DRNK 견본(Earnings.drnkJSON)이 data/earnings/DRNK.json 과 같은지도 본다.
//   swiftc ios-app/Naeilo/Data/Earnings.swift ios-app/tools/earn_parity/stub.swift ios-app/tools/earn_parity/main.swift -o ~/Library/Caches/naeilo-earn/earn && ~/Library/Caches/naeilo-earn/earn .

let root = URL(fileURLWithPath: CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : ".")
let F = try! JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("tools/earn_fixtures.json"))) as! [String: Any]

func same(_ a: Any?, _ b: Any?) -> Bool {
    if b == nil || b is NSNull { return a == nil || a is NSNull }
    if let b = b as? NSNumber, CFGetTypeID(b) == CFBooleanGetTypeID() { return (a as? Bool) == b.boolValue }
    if let b = b as? Double { guard let a = a as? Double else { return false }; return abs(a - b) < 1e-9 }
    if let b = b as? String { return (a as? String) == b }
    if let b = b as? [Any] { guard let a = a as? [Any], a.count == b.count else { return false }; return zip(a, b).allSatisfy { same($0, $1) } }
    if let b = b as? [String: Any] { guard let a = a as? [String: Any] else { return false }; return b.allSatisfy { same(a[$0.key], $0.value) } }
    return false
}

func dict(_ s: Earnings.Summary) -> [String: Any] {
    func o<T>(_ v: T?) -> Any { v.map { $0 as Any } ?? NSNull() }
    return ["symbol": s.symbol, "currency": s.currency, "months": s.months, "label": s.label,
            "lastYearMonths": o(s.lastYearMonths), "pending": o(s.pending),
            "revenue": o(s.revenue), "net": o(s.net),
            "branches": s.branches.map { ["k": $0.k, "v": $0.v] as [String: Any] },
            "mist": s.mist, "loss": s.loss, "short": s.short,
            "gauge": s.gauge.map { ["label": $0.label, "value": o($0.value), "lastYear": $0.lastYear] as [String: Any] },
            "revYoY": o(s.revYoY), "netYoY": o(s.netYoY), "lastYearNet": o(s.lastYearNet),
            "netText": s.netText, "netKrw": o(s.netKrw)]
}

var bad = 0
for c in F["cases"] as! [[String: Any]] {
    let name = c["name"] as! String
    let input = c["input"].flatMap { $0 is NSNull ? nil : try? JSONDecoder().decode(Earnings.File.self, from: JSONSerialization.data(withJSONObject: $0)) }
    let got = Earnings.summary(input, qty: c["qty"] as! Double, fx: c["fx"] as? Double, at: c["at"] as? Int)
    var miss: [String] = []
    if let exp = c["expect"] as? [String: Any] {
        if let got {
            let d = dict(got)
            for (k, v) in exp where !same(d[k], v) { miss.append("\(k) 기대 \(v) 받음 \(d[k] ?? "없음")") }
        } else { miss.append("nil 이면 안 됨") }
    } else if got != nil { miss.append("nil 이어야 함") }
    if miss.isEmpty { print("통과 \(name)") } else { bad += 1; print("실패 \(name): " + miss.joined(separator: " / ")) }
}

// DRNK 견본이 저장소 파일과 같은지 (앱 안 견본을 고치면 파일도 같이)
let file = try! JSONDecoder().decode(Earnings.File.self, from: Data(contentsOf: root.appendingPathComponent("data/earnings/DRNK.json")))
if Earnings.drnk == file { print("통과 DRNK 견본 = data/earnings/DRNK.json") } else { bad += 1; print("실패 DRNK 견본이 data/earnings/DRNK.json 과 다름") }
exit(bad == 0 ? 0 : 1)
