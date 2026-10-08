"""아이폰 위젯 앱이 30분마다 받는 작은 시세 파일 data/widget.json 을 만든다 (시세 수집 뒤 GitHub Actions 가 실행).

앱은 3년치 가격 파일(data/prices/*.json)을 처음 한 번만 받고, 그 뒤로는 이 파일의 최근 10거래일 종가로 이어 붙인다.
q: 종목별 [현재가, 전일 종가, 현재가 시각(초)], c: 종목별 최근 10거래일 {d 날짜, c 종가, a 수정종가}, ccy: 통화, mar: 매매기준율
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")
N = 10


def load(name, default=None):
    try:
        with open(os.path.join(ROOT, name), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def main():
    idx = load("index.json", {}) or {}
    quotes = load("quotes.json", {}) or {}
    out = {"v": 1, "u": idx.get("updated"), "q": {}, "c": {}, "ccy": {}}
    for sym, q in quotes.items():
        if isinstance(q, dict) and q.get("last"):
            out["q"][sym] = [q.get("last"), q.get("prev_close"), q.get("last_time")]
    for sym, fname in (idx.get("prices") or {}).items():
        p = load(os.path.join("prices", fname))
        if not p or not p.get("dates"):
            continue
        out["c"][sym] = {"d": p["dates"][-N:], "c": p["close"][-N:], "a": p["adj"][-N:]}
        out["ccy"][sym] = p.get("currency") or "USD"
    m = (load("mar.json", {}) or {}).get("USD")
    if m and m.get("rate"):
        out["mar"] = {"rate": m["rate"], "date": m.get("date")}
    with open(os.path.join(ROOT, "widget.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, separators=(",", ":"), ensure_ascii=False)


if __name__ == "__main__":
    main()
