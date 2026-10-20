#!/usr/bin/env python3
"""숫자 맞추기 E 단계 (홈·설정): 아이폰 원본에서 계산하는 줄을 그대로 꺼내 한 파일(main.swift)로 만들고, 고정 입력을 쓴다.

parity.py gen 이 부른다. 꺼내는 것 (앱 원본 그대로):
  StartView.swift 의 StartForecastCard 계산 (mu·sigma·g·Ex·ex·value·fanStart),
  AppModel.normCDF·pct, GoalRoute.wonK·eta (영어 아님), Guide.swift 의 Pose·Voice,
  SettingsDetail 세금 줄 (tax·won0), HomeView 의 '%.1f' 글 (본전까지 남은 %·오늘 등락)
private 접근이 파일 안에서만 되므로 꺼낸 것과 돌리는 코드를 한 파일에 둔다.
"""
import json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
V = ROOT / "ios-app" / "Naeilo"


def lines(rel, a, b):
    return "\n".join((V / rel).read_text().split("\n")[a - 1:b])


def find(rel, start, n):
    t = (V / rel).read_text().split("\n")
    i = next(k for k, l in enumerate(t) if start in l)
    return "\n".join(t[i:i + n])


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    src = f'''import Foundation
// 꺼낸 원본 (tools/num_parity/e/esrc.py 가 만든다. 고치지 말 것)
enum Route {{ case recover, plus, novice }}
enum AppModel {{
    static let en = false
    static func krwC(_ x: Double) -> String {{ "" }}
{find("Data/AppModel.swift", "static func normCDF", 5)}
{find("Data/AppModel.swift", "static func pct(_ p: Double)", 1)}
{find("Data/GoalRoute.swift", "static func wonK", 6)}
{find("Data/GoalRoute.swift", "static func eta", 6)}
}}
{lines("Views/Guide.swift", 7, 7)}
{lines("Views/Guide.swift", 74, 111)}
struct SFC {{
    let route: Route?
{lines("Views/StartView.swift", 72, 98)}
{lines("Views/StartView.swift", 129, 134)}
    func dump(_ sizes: [CGSize]) -> [String: Any] {{
        let e = ex
        return ["v0": e.v0, "monthly": e.monthly, "line": e.line as Any? ?? NSNull(), "lineName": e.lineName, "big": e.big, "sub": e.sub,
                "fanStart": sizes.map {{ s in let p = Self.fanStart(e, s); return [Double(p.x), Double(p.y)] }}]
    }}
    static func grid(_ v0s: [Double], _ ms: [Double], _ ts: [Double], _ zs: [Double]) -> [Double] {{
        var r: [Double] = []
        for v in v0s {{ for m in ms {{ for t in ts {{ for z in zs {{ r.append(value(v, m, t, z)) }} }} }} }}
        return r
    }}
}}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var out: [String: Any] = [:]
let ts = stride(from: 0.0, through: 3, by: 0.1).map {{ $0 }}
out["ts"] = ts
out["value"] = SFC.grid(inp["v0s"] as! [Double], inp["ms"] as! [Double], ts, inp["zs"] as! [Double])
let sizes = (inp["sizes"] as! [[Double]]).map {{ CGSize(width: $0[0], height: $0[1]) }}
out["ex"] = [nil, Route.recover, .plus, .novice].map {{ SFC(route: $0).dump(sizes) }}
let poses: [Pose] = [.stand, .spread, .point, .wave, .think, .cheer, .side, .back, .sideL]
out["voice"] = (inp["says"] as! [String]).map {{ t in (inp["friends"] as! [String]).map {{ f in poses.map {{ Voice.say(t, f, $0) }} }} }}
out["tax"] = (inp["gains"] as! [Double]).map {{ G0 -> [String: Any] in
{lines("Views/SettingsDetail.swift", 542, 543).replace("m.taxGain", "G0")}
    return ["tax": tax, "won0": won0(tax), "G": won0(G)]
}}
out["need"] = (inp["fracs"] as! [Double]).map {{ x in ["+" + String(format: "%.1f", x * 100), String(format: "%.1f%%", abs(x) * 100)] }}
out["etc"] = (inp["fracs"] as! [Double]).map {{ x in [AppModel.pct(x), AppModel.wonK(x * 1e4), AppModel.eta(x * 120)] }}
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity e: 값 \\((out["value"] as! [Double]).count) · 말투 \\((inp["says"] as! [String]).count)줄")
'''
    (out / "main.swift").write_text(src)
    inp = {
        "v0s": [0, 4350, 5000, 123.456], "ms": [0, 50, 7.5], "zs": [0, 0.674, -0.674, 1.645, -1.645],
        "sizes": [[300, 140], [343, 140], [358.5, 140]],
        "says": ["…… 하나만 물어볼게요.", "정했어요. 이 계획으로 가요.", "같이 봐요. 그래프를 보세요.", "목표를 정해요.", "이건 권유가 아니에요", "주세요, 넣으세요, 보세요",
                 "하시는군요. 드리지 않아요. 하셔야 해요.", "싶나요? 이에요 예요", "바꿔요. 써요! 돼요, 가요", "본전까지 12.3% 남았어요"],
        "friends": ["seri", "sio", "seonbae", "ir", "sua", "me"],
        "gains": [0, 100, 250, 250.4, 251, 1234.5, 99999, -5],
        "fracs": [0, 0.0005, 0.04999, 0.05, 0.12345, -0.0221, 1.5, 0.995, -0.995, 0.3333333],
    }
    (out / "input.json").write_text(json.dumps(inp, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1])
