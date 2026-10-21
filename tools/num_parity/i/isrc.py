#!/usr/bin/env python3
"""숫자 맞추기 I 단계 (인물 대사 고르기): 아이폰 원본에서 꺼내 한 파일(main.swift)로 만들고 고정 입력을 쓴다. parity.py gen 이 부른다.

꺼내는 것 (원본 그대로): Lines.pick·peek·skip·start·fill·count (Data/Lines.swift 21-58), LineClock.Pos (69).
대사 표(all)와 대사 차례(LineClock)는 저장소(UserDefaults·번들) 대신 입력으로 넣는다: 표는 lines_ko.json·lines_app_en.json 에서 고른 키 + 만든 키.
비교: 차례 올리기(next)·고르기(pick)·미리 보기(peek)·넘기기(skip)를 섞은 같은 순서의 결과와, 키마다 첫 줄 번호(start, FNV-1a).
"""
import json, random, sys
from pathlib import Path

V = Path(__file__).resolve().parents[3] / "ios-app" / "Naeilo"


def span(rel, first, last, nth=1, k=1):
    """원본 줄을 번호 대신 내용으로 찾는다 (아이폰 코드가 위아래로 밀려도 같은 덩어리): first 줄부터 그 뒤 처음 나오는 last 줄까지"""
    t = (V / rel).read_text().split("\n")
    hits = [k for k, l in enumerate(t) if l == first]
    if len(hits) < nth: raise SystemExit(f"{rel}: 시작 줄을 못 찾음: {first!r}")
    i = hits[nth - 1]
    j = [x for x in range(i, len(t)) if t[x] == last][k - 1]
    return "\n".join(t[i:j + 1])


def lines(rel, a, b):
    return "\n".join((V / rel).read_text().split("\n")[a - 1:b])


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    src = f'''import Foundation
// 꺼낸 원본 (tools/num_parity/i/isrc.py 가 만든다. 고치지 말 것)
enum Lines {{
    static var all: [String: [String]] = [:]
{span("Data/Lines.swift", "    static func pick(_ key: String, _ vars: [String: String] = [:], salt: Int = 0) -> String? {", "    static var count: Int { all.values.reduce(0) { $0 + $1.count } }")}
}}
extension Lines {{ static func startT(_ k: String, _ n: Int) -> Int {{ start(k, n) }} }}
final class LineClock {{
    static let shared = LineClock()
    private(set) var turn = 0
    func next() {{ turn += 1 }}
{span("Data/Lines.swift", "    struct Pos: Codable { var turn: Int; var i: Int }", "    struct Pos: Codable { var turn: Int; var i: Int }")}
    static var pos: [String: Pos] = [:]
    static func save() {{}}
}}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var out: [String: Any] = [:]
Lines.all = inp["lines"] as! [String: [String]]
out["start"] = (inp["starts"] as! [[Any]]).map {{ Lines.startT($0[0] as! String, $0[1] as! Int) }}
var res: [Any] = []
for o in inp["ops"] as! [[String: Any]] {{
    let key = o["key"] as? String ?? "", vars = o["vars"] as? [String: String] ?? [:], n = o["n"] as? Int ?? 0
    switch o["op"] as! String {{
    case "next": LineClock.shared.next(); res.append("")
    case "pick": res.append(Lines.pick(key, vars, salt: n) ?? NSNull())
    case "peek": res.append(Lines.peek(key, vars, n))
    case "skip": Lines.skip(key, n); res.append("")
    default: res.append(Lines.count)
    }}
}}
out["ops"] = res
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity i: 첫 줄 \\((inp["starts"] as! [Any]).count) · 대사 동작 \\((inp["ops"] as! [Any]).count)")
'''
    (out / "main.swift").write_text(src)
    r = random.Random(4)
    ko = json.loads((V / "Resources" / "lines_ko.json").read_text())["lines"]
    en = json.loads((V / "Resources" / "lines_app_en.json").read_text())["lines"]
    keys = sorted(set(ko) | set(en))
    # 첫 줄 번호: 실제 키 전부 × 여러 줄 수 (한글·영문 키, 아주 큰 줄 수까지)
    starts = [[k, n] for k in keys for n in (1, 7, 100)] + [[k, 1000003] for k in keys[::5]]
    starts += [[k, n] for k in ("", "a", "가", "fb.chase.ir#2", "item.barley_tea.sua", "🙂.seri", "x" * 300) for n in (1, 2, 3, 97, 2 ** 31 - 1)]
    # 대사 표: 실제 키 일부(한글 원고) + 만든 키 (줄 1개·빈 목록·자리 채우기)
    table = {k: ko[k] for k in sorted(ko)[::4]}
    table.update({"one": ["하나뿐 {n}"], "empty": [], "vars": ["{n}점 {d}일 {k}건 {min}건 {move}", "{move}{move} {left}", "없는 {zz} 자리"],
                  "en.key": [f"line {i} {{n}}" for i in range(5)]})
    tk = sorted(table) + ["nope", "nope.too"]
    vs = [{}, {"n": "72"}, {"n": "−3", "d": "14", "k": "9", "min": "10", "move": "+1.2%", "left": "3"}, {"move": "−0.4%"}]
    ops = []
    for _ in range(1500):
        x = r.random()
        if x < 0.15: ops.append({"op": "next"})
        elif x < 0.70: ops.append({"op": "pick", "key": r.choice(tk), "vars": r.choice(vs), "n": r.choice([0, 0, 0, 1, 2])})
        elif x < 0.85: ops.append({"op": "peek", "key": r.choice(tk), "vars": r.choice(vs), "n": r.randint(1, 12)})
        elif x < 0.97: ops.append({"op": "skip", "key": r.choice(tk), "n": r.randint(-1, 5)})
        else: ops.append({"op": "count"})
    (out / "input.json").write_text(json.dumps({"lines": table, "starts": starts, "ops": ops}, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1])
