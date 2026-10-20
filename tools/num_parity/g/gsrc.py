#!/usr/bin/env python3
"""숫자 맞추기 G 단계 (분석): 아이폰 원본에서 꺼내 한 파일(main.swift)로 만들고 고정 입력을 쓴다. parity.py gen 이 부른다.

꺼내는 것 (원본 그대로): ForecastView.amt·short·ym (AnalysisView.swift 507-519), AnalysisView.freq (99-103),
ScenarioChart.step·axis (ForecastParts.swift 17-28), AppModel.manS·won1·wonRead (AnalysisDetail.swift 88-98), AppModel.man (영어 아님).
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
// 꺼낸 원본 (tools/num_parity/g/gsrc.py 가 만든다. 고치지 말 것)
enum Lens {{ case base, mine, smooth }}
enum AppModel {{
    static let en = false
    static func krwC(_ x: Double) -> String {{ "" }}
{find("Data/AppModel.swift", "static func man(_ krw: Double)", 8)}
{lines("Views/AnalysisDetail.swift", 88, 98)}
}}
enum ForecastView {{
{lines("Views/AnalysisView.swift", 507, 519)}
}}
enum AnalysisView {{
{lines("Views/AnalysisView.swift", 99, 103)}
}}
enum ScenarioChart {{
{lines("Views/ForecastParts.swift", 17, 28)}
}}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var out: [String: Any] = [:]
let won = inp["won"] as! [Double], ps = inp["p"] as! [Double]
out["amt"] = won.map {{ ForecastView.amt($0) }}
out["short"] = [Lens.base, .mine, .smooth].map {{ ForecastView.short($0) }}
out["ym"] = (inp["dates"] as! [String]).map {{ let x = ForecastView.ym($0); return [x.long, x.short] }}
out["freq"] = ps.map {{ AnalysisView.freq($0) }}
out["step"] = won.map {{ ScenarioChart.step(abs($0)) }}
out["axis"] = won.map {{ ScenarioChart.axis($0) }}
out["manS"] = won.map {{ AppModel.manS($0) }}
out["won1"] = won.map {{ AppModel.won1($0) }}
out["wonRead"] = won.map {{ AppModel.wonRead($0) }}
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity g: 금액 \\(won.count) · 확률 \\(ps.count)")
'''
    (out / "main.swift").write_text(src)
    inp = {
        "won": [0, 1, 4999, 5000, 9999.5, 12345, 46000, 46499, 99500, 1e5, 3.3e7, 9.99e7, 1e8, 1.5e8, 2.5e8, 3.30e8, 9.99e8, 1e9, 1.15e9, 2.6e9, 1.2345e10, -5e7, -3.3e8, 5e7, 2.5e8 + 1],
        "p": [0, 0.01, 0.05, 0.25, 0.26, 0.28, 0.3, 0.33, 0.36, 0.5, 0.52, 0.6, 0.66, 0.7, 0.75, 0.77, 0.9, 0.94, 0.96, 1],
        "dates": ["2029-10-14", "2026-01-05", "2030-12-31", "bad", "2031-7"],
    }
    (out / "input.json").write_text(json.dumps(inp))


if __name__ == "__main__":
    main(sys.argv[1])
