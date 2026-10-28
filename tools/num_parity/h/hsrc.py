#!/usr/bin/env python3
"""숫자 맞추기 H 단계 (미션·리마인드): 아이폰 원본에서 꺼내 한 파일(main.swift)로 만들고 고정 입력을 쓴다. parity.py gen 이 부른다.

꺼내는 것 (원본 그대로): Day (WidgetShared/Engine.swift 76-78), RemindLog.Wk (Data/Remind.swift 71-77),
Remind.endedWeeks·week (311-314, 504-507), MarketEvents 규칙·날짜 반복 (514-559), BlogFile.lead (81-96).
시각(초)·요일은 맥의 지역 시간대로 두 쪽이 같게 계산한다.
"""
import json, sys
from datetime import datetime, timedelta, timezone
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
// 꺼낸 원본 (tools/num_parity/h/hsrc.py 가 만든다. 고치지 말 것)
enum Day {{
{span("../WidgetShared/Engine.swift", "    static let fmt: DateFormatter = { let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: \"en_US_POSIX\"); f.timeZone = TimeZone(identifier: \"Asia/Seoul\"); f.dateFormat = \"yyyy-MM-dd\"; return f }()", "    static func str(_ d: Date) -> String { fmt.string(from: d) }")}
}}
enum RemindLog {{
{span("Data/Remind.swift", "    struct Wk: Codable {", "    }")}
}}
enum Remind {{
{span("Data/Remind.swift", "    static func endedWeeks(_ w: [String: RemindLog.Wk], now: Date = Date()) -> [String] {", "    }")}
{span("Data/Remind.swift", "    static func week(_ d: Date) -> String {", "    }")}
}}
enum MarketEvents {{
{span("Data/Remind.swift", "    struct Rule { let id: String; let target: String; let kind: String; let date: String; let rep: String }", "    ]")}
{span("Data/Remind.swift", "    static func dates(_ r: Rule, from: String, until: String) -> [String] {", "    }", 1, 2)}
}}
{span("Data/Remind.swift", "struct BlogFile: Codable {", "}")}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var out: [String: Any] = [:]
let ts = (inp["ts"] as! [String]).map {{ Double($0)! }}
out["week"] = ts.map {{ Remind.week(Date(timeIntervalSince1970: $0)) }}
out["ended"] = (inp["ended"] as! [[String: Any]]).map {{ c -> [String] in
    var w: [String: RemindLog.Wk] = [:]
    for k in c["keys"] as! [String] {{ w[k] = .init(lo: 1, hi: 2, last: 1.5) }}
    return Remind.endedWeeks(w, now: Date(timeIntervalSince1970: Double(c["now"] as! String)!))
}}
out["pos"] = (inp["pos"] as! [[String]]).map {{ p in RemindLog.Wk(lo: Double(p[0])!, hi: Double(p[1])!, last: Double(p[2])!).pos }}
out["dates"] = (inp["windows"] as! [[String]]).map {{ w in MarketEvents.rules.map {{ MarketEvents.dates($0, from: w[0], until: w[1]) }} }}
out["lead"] = (inp["bodies"] as! [String]).map {{ BlogFile.Post(id: "", title: "", cat: "", created: nil, body: $0).lead }}
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity h: 주 \\(ts.count) · 끝난 주 \\((inp["ended"] as! [Any]).count) · 일정 창 \\((inp["windows"] as! [Any]).count) · 첫 문장 \\((inp["bodies"] as! [Any]).count)")
'''
    (out / "main.swift").write_text(src)
    # 연말·연초(ISO 53주), 일요일 자정 앞뒤, 서머타임과 무관한 맥 지역 시각
    base = [datetime(2026, 10, 21, 12, tzinfo=timezone.utc), datetime(2026, 12, 27, 14, 59, tzinfo=timezone.utc), datetime(2026, 12, 27, 15, 1, tzinfo=timezone.utc),
            datetime(2020, 12, 31, 23, tzinfo=timezone.utc), datetime(2021, 1, 3, 15, tzinfo=timezone.utc), datetime(2027, 1, 1, 0, tzinfo=timezone.utc),
            datetime(2015, 12, 31, 12, tzinfo=timezone.utc), datetime(2032, 12, 31, 12, tzinfo=timezone.utc)]
    ts = [b.timestamp() + h * 3600 for b in base for h in (0, 7, 13, 30, 70, 150)]
    ended = []
    for b in base[:6]:
        for h in (0, 30, 80, 130):
            now = b.timestamp() + h * 3600
            ended.append({"now": repr(now), "keys": ["2026-W40", "2026-W43", "2026-W44", "2026-W52", "2026-W53", "2027-W01", "2020-W53", "2021-W01", "2021-W02"]})
    pos = [[repr(a), repr(b), repr(c)] for a, b, c in [(1, 2, 0.5), (1, 2, 1), (1, 2, 1.5), (1, 2, 2), (1, 2, 2.0000001), (3.3e7, 3.6e7, 3.6e7 + 1)]]
    windows = [["2026-10-01", "2026-10-31"], ["2026-10-21", "2026-11-20"], ["2026-10-21", "2027-12-31"], ["2027-01-31", "2028-03-31"],
               ["2029-02-28", "2030-06-30"], ["2026-01-01", "2026-10-21"], ["2031-08-30", "2032-03-01"]]
    bodies = ["# 제목\n\n첫 문장입니다. 둘째 문장.", "금리가 3.5% 올랐다. 그래서", "물음표로 끝? 다음", "끝에 마침표.", "", "  > 인용 - 점 없음",
              "1.2배 커졌다! 놀랍다", "줄 끝 마침표.\n다음 줄", "*** \n\n- 목록 첫 줄. 두번째"]
    (out / "input.json").write_text(json.dumps({"ts": [repr(t) for t in ts], "ended": ended, "pos": pos, "windows": windows, "bodies": bodies}, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1])
