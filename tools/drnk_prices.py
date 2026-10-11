"""가상 종목 DRNK(드링커) 시세 파일을 만든다 (data/prices/DRNK.json).

앱과 같은 흐름을 쓴다: ios-app/Naeilo/Resources/drnk_ratio.json (1010일, 마지막 값 1) × 종가 $250.
앱처럼 흐름의 끝을 '어제'(한국 시간, 주말이면 그 전 금요일)에 맞추고, 주말은 빼서 평일만 남긴다.
웹 첫 방문 샘플(DRNK 60주·QQQ 10주)이 이 파일을 쓴다. sample.yml 이 매일 샘플 계산 전에 돌린다.
"""
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RATIO = ROOT / "ios-app" / "Naeilo" / "Resources" / "drnk_ratio.json"
OUT = ROOT / "data" / "prices" / "DRNK.json"
INDEX = ROOT / "data" / "index.json"
CLOSE = 250.0

ratio = json.loads(RATIO.read_text())
end = (datetime.now(timezone.utc) + timedelta(hours=9)).date() - timedelta(days=1)
while end.weekday() >= 5:  # 마지막 값(1 = $250)이 평일 종가가 되게
    end -= timedelta(days=1)
days = [end - timedelta(days=len(ratio) - 1 - i) for i in range(len(ratio))]
dates, close = [], []
for d, r in zip(days, ratio):
    if d.weekday() < 5:
        dates.append(d.isoformat())
        close.append(round(CLOSE * r, 2))
updated = f"{dates[-1]}T00:00:00+00:00"
doc = {"symbol": "DRNK", "currency": "USD", "name": "드링커 (가상 종목)", "updated": updated, "virtual": True, "dates": dates, "close": close, "adj": close}
OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=1) + "\n")

idx = json.loads(INDEX.read_text())
idx.setdefault("prices", {})["DRNK"] = OUT.name
if "v" in idx:
    idx["v"]["DRNK"] = updated
INDEX.write_text(json.dumps(idx, ensure_ascii=False, indent=1) + "\n")
print("DRNK.json", dates[0], "~", dates[-1], len(dates), "days")
