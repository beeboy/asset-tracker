#!/usr/bin/env python3
"""숫자 맞추기 F 단계 (종목): 아이폰 원본에서 꺼내 한 파일(main.swift)로 만들고 고정 입력을 쓴다. parity.py gen 이 부른다.

꺼내는 것 (원본 그대로): HoldingEdit.compute·initial (HoldingsView.swift 728-755), AppModel.man·price (영어 아님),
목록 금액 글 amountText 의 '$' 줄 (163), 수량 글 r.h.qty.formatted() (223), Double.formatted 세 가지.
m.krw 는 고정 환율 1,380.5 로 (앱은 Market.valFx).
"""
import json, sys
from pathlib import Path

V = Path(__file__).resolve().parents[3] / "ios-app" / "Naeilo"


def lines(rel, a, b):
    return "\n".join((V / rel).read_text().split("\n")[a - 1:b])


def find(rel, start, n):
    t = (V / rel).read_text().split("\n")
    i = next(k for k, l in enumerate(t) if start in l)
    return "\n".join(t[i:i + n])


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    src = f'''import Foundation
// 꺼낸 원본 (tools/num_parity/f/fsrc.py 가 만든다. 고치지 말 것)
enum Currency {{ case usd, krw }}
struct Symbol {{ let id: String; let currency: Currency; let close: Double }}
struct Holding {{ let symbol: String; let qty: Double; let avg: Double }}
final class AppModel {{
    static let en = false
    static func krwC(_ x: Double) -> String {{ "" }}
    func krw(_ s: Symbol, _ x: Double) -> Double {{ s.currency == .usd ? x * 1380.5 : x }}
{find("Data/AppModel.swift", "static func man(_ krw: Double)", 8)}
{find("Data/AppModel.swift", "static func price(_ s: Symbol", 3)}
}}
{lines("Views/HoldingsView.swift", 728, 755)}
}}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
let m = AppModel()
var out: [String: Any] = [:]
let syms = [Symbol(id: "AAPL", currency: .usd, close: 187.25), Symbol(id: "005930", currency: .krw, close: 71400)]
var ed: [[String: Any]] = []
for s in syms {{ for c in inp["cases"] as! [[Any]] {{
    let r = HoldingEdit.compute(m, s, c[0] as! String, c[1] as! String, c[2] as! String, c[3] as! Double, c[4] as! Double)
    ed.append(["qi": r.qi, "pi": r.pi, "q1": r.q1, "p1": r.p1, "bad": r.bad, "note": r.note])
}} }}
out["edit"] = ed
out["initial"] = syms.flatMap {{ s in (inp["holds"] as! [[Double]]).flatMap {{ h in ["fix", "buy"].map {{ md in let x = HoldingEdit.initial(md, Holding(symbol: s.id, qty: h[0], avg: h[1]), s); return [x.0, x.1] }} }} }}
let xs = inp["nums"] as! [Double]
out["man"] = xs.map {{ AppModel.man($0) }}
out["price"] = syms.flatMap {{ s in xs.map {{ AppModel.price(s, $0) }} }}
out["fmt"] = xs.map {{ [$0.formatted(), $0.formatted(.number.grouping(.never)), "$" + $0.formatted(.number.precision(.fractionLength(2)))] }}
// 종목 상세 '본전까지' 줄 (318-319) · 고치기 '본전까지' 칸 (650): 원본 줄을 그대로 감싼다
func kvStub(_ k: String, _ v: String) -> [String] {{ [k, v] }}
out["breakEven"] = (inp["needs"] as! [Double]).map {{ need -> [String] in
    let kv = kvStub
{lines("Views/HoldingsView.swift", 318, 319).replace("kv(", "return kv(")}
}}
out["needText"] = (inp["needs"] as! [Double]).map {{ x -> String in
    let px = 100.0
{lines("Views/HoldingsView.swift", 650, 650)}
    return need(px * (1 + x))
}}
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity f: 고치기 \\(ed.count) · 숫자 \\(xs.count)")
'''
    (out / "main.swift").write_text(src)
    inp = {
        "cases": [[md, q, p, q0, p0] for md in ["fix", "buy", "sell"]
                  for q, p in [("3", "190.5"), ("0", "100"), ("10", "150"), ("2.5", "71400"), ("abc", "1"), ("1", "-3"), ("12", "70100"), ("", "")]
                  for q0, p0 in [(10, 182.33), (5, 72000), (0, 0)]],
        "holds": [[10, 182.333], [1234.5, 71400], [0.125, 0.5], [3, 1e6]],
        "needs": [0, 0.0005, 0.00051, -0.0005, -0.00051, 0.123456, -0.5, 2.5, 0.04999, -0.04999],
        "nums": [0, 0.5, 1, 1.005, 2.675, 12.3456, 187.255, 999.995, 1234.5678, 71400, 99995, 1e4, 12345678, 1.23456789e8, 9.9999e8, 1.5e9, -5e7, -0.5, 0.123456789, 3.333333333],
    }
    (out / "input.json").write_text(json.dumps(inp, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1])
