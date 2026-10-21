#!/usr/bin/env python3
"""숫자 맞추기 흐름 탭 단계: 아이폰 원본 Data/FlowData.swift 의 계산 부분(FlowCalc·FlowSeries·DrnkSample)을 꺼내 main.swift 를 만든다. parity.py gen 이 부른다.

꺼내는 것 (원본 그대로): 맨 위부터 '/// 차트 탭 실데이터 캔들 저장' 앞까지 (저장·받기·화면은 빼고).
입력: drnk_ratio.json 그대로(DRNK 샘플) + 거꾸로 뒤집은 것(패턴 규칙을 더 많이 거치게).
비교: 캔들 시가·고가·저가·종가·거래량, 추세 점 세 벌, 추세 띠 세 벌, 지지·저항, 패턴 (종류·구간·이름표 가격·아래·진하게).
"""
import json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
V = ROOT / "ios-app" / "Naeilo"


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    t = (V / "Data/FlowData.swift").read_text()
    calc = t[:t.index("/// 차트 탭 실데이터 캔들 저장")]
    r = json.loads((V / "Resources/drnk_ratio.json").read_text())
    (out / "input.json").write_text(json.dumps({"drnk": r, "rev": r[::-1]}))
    src = calc + '''
// ── 아래는 시험 틀 (tools/num_parity/flow/flowsrc.py 가 만든다. 고치지 말 것) ──
struct StubPriceProvider { var drnkRatio: [Double] { [] } }
func dump(_ s: FlowSeries) -> [String: Any] {
    [
        "candles": s.candles.map { [$0.o, $0.h, $0.l, $0.c, $0.v] },
        "pivots": s.pivotSets.map { $0.map { [Double($0.i), $0.price, $0.high ? 1 : 0] } },
        "trends": s.trends.map { $0.map { [$0.from, $0.to, $0.dir] } },
        "levels": s.levels.map { [Double($0.id), $0.price, Double($0.from), Double($0.touches), $0.support ? 1 : 0] },
        "patterns": s.patterns.map { ["id": $0.id, "kind": $0.kind.rawValue, "from": $0.from, "to": $0.to, "price": $0.price, "below": $0.below, "strong": $0.strong] as [String: Any] },
    ]
}
let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: [Double]]
var out: [String: Any] = [:]
for (k, r) in inp { out[k] = dump(FlowSeries(candles: DrnkSample.candles(r))) }
try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys]).write(to: dir.appendingPathComponent("app.json"))
let p = (out["drnk"] as! [String: Any])["patterns"] as! [Any]
print("num_parity flow: DRNK 캔들 \\(inp["drnk"]!.count) · 패턴 \\(p.count) (거꾸로 판도 같이)")
'''
    (out / "main.swift").write_text(src)


if __name__ == "__main__":
    main(sys.argv[1])
