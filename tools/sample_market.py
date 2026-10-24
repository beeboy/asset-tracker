"""키 등록 전 화면용 가상 시장 샘플을 만든다 (data/sample/).

공개 버전은 사용자 본인 증권사 키로만 시세를 받으므로, 키를 넣기 전에는 이 가상 값만 보여 준다 (2026-10-15 결정).
실제 시세에서 따온 숫자가 하나도 없다: 종목·이름·가격·환율 모두 고정 씨앗의 난수로 만든 가상 값이다.
내부 베타는 계속 Yahoo 자료를 쓰므로 아직 앱·웹에 연결하지 않았다.

만드는 파일 (기존 data/ 파일과 같은 모양이라 그대로 바꿔 끼울 수 있다)
- data/sample/symbols.json : 가상 종목 목록과 소개
- data/sample/quotes.json  : data/quotes.json 과 같은 키 (prev_close·last·last_time 등)
- data/sample/prices/*.json : data/prices/*.json 과 같은 모양 (3년 평일 종가)

날짜는 끝을 '어제'(한국 시간, 주말이면 그 전 금요일)에 맞춘다. 가격 흐름은 씨앗이 같으면 늘 같다.
실행: python3 tools/sample_market.py
"""
import json
import math
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "sample"
DRNK_RATIO = ROOT / "ios-app" / "Naeilo" / "Resources" / "drnk_ratio.json"
DAYS = 783  # 약 3년 평일

# id, 이름, 시장, 통화, 업종, 검색어, 마지막 종가, 연 기대 수익, 연 변동성, 씨앗, 소개
# 씨앗은 흐름이 너무 튀지 않는 것으로 골랐다. 한국 종목 코드는 실제 코드와 겹치지 않게 V 로 시작한다
SYMBOLS = [
    ("DRNK", "드링커", "미국 주식", "USD", "우주항공·궤도 통신", "drinker", 250.0, 0, 0, 0,
     "하늘의 깜빡이지 않는 별, 궤도 정거장을 설계하고 지었어요."),
    ("NXIX", "내일100 지수 ETF", "미국 ETF", "USD", "지수", "naeilo index", 412.30, 0.10, 0.18, 9,
     "가상 미국 대형주 100개를 담는 가상 ETF예요."),
    ("SERI", "세리 로보틱스", "미국 주식", "USD", "로봇·AI", "seri robotics", 87.45, 0.16, 0.45, 52,
     "집안일을 돕는 안드로이드와 그 위에서 도는 AI를 만드는 가상 회사예요."),
    ("NLBD", "내일 단기채 ETF", "미국 ETF", "USD", "채권", "naeilo bond", 100.42, 0.0, 0.01, 303,
     "만기가 짧은 가상 국채만 담는 가상 ETF예요. 가격이 거의 그대로예요."),
    ("V00001", "한빛반도체", "코스피", "KRW", "반도체", "hanbit semi", 71200, 0.09, 0.32, 6,
     "메모리 반도체를 만드는 가상 회사예요."),
    ("V00002", "내일 코스피 ETF", "코스피 ETF", "KRW", "지수", "naeilo kospi", 38450, 0.06, 0.19, 505,
     "가상 한국 대형주 200개를 담는 가상 ETF예요."),
]
# 원/달러는 data/quotes.json·prices 와 같은 기호(KRW=X, 파일 KRW_X.json)로 써서 앱이 그대로 바꿔 끼운다
FX = ("KRW=X", "원/달러 (가상)", 1365.0, 0.0, 0.07, 17)


def walk(last, mu, sigma, seed, n=DAYS):
    """끝 값이 last 인 기하 브라운 운동 (평일 n 개)."""
    rng = random.Random(seed)
    dt = 1 / 252
    logs = [0.0]
    for _ in range(n - 1):
        logs.append(logs[-1] + (mu - sigma * sigma / 2) * dt + sigma * math.sqrt(dt) * rng.gauss(0, 1))
    end = logs[-1]
    return [last * math.exp(v - end) for v in logs]


def weekdays_to(end, n):
    out = []
    d = end
    while len(out) < n:
        if d.weekday() < 5:
            out.append(d)
        d -= timedelta(days=1)
    return out[::-1]


def rnd(v, cur):
    return round(v) if cur == "KRW" else round(v, 2)


def main():
    end = (datetime.now(timezone.utc) + timedelta(hours=9)).date() - timedelta(days=1)
    while end.weekday() >= 5:
        end -= timedelta(days=1)
    dates = [d.isoformat() for d in weekdays_to(end, DAYS)]
    updated = f"{dates[-1]}T00:00:00+00:00"
    # 지금 가격 시각: 마지막 종가 다음 평일 장중 (미국 동부 11시 = UTC 15시)
    nxt = end + timedelta(days=1)
    while nxt.weekday() >= 5:
        nxt += timedelta(days=1)
    now_t = int(datetime(nxt.year, nxt.month, nxt.day, 15, tzinfo=timezone.utc).timestamp())

    (OUT / "prices").mkdir(parents=True, exist_ok=True)
    symbols, quotes = [], {}
    rows = [s[:10] for s in SYMBOLS] + [(FX[0], FX[1], "환율", "KRW", "환율", "usdkrw", FX[2], FX[3], FX[4], FX[5])]
    for sid, name, market, cur, sector, search, last, mu, sigma, seed in rows:
        if sid == "DRNK":  # 앱·웹이 이미 쓰는 가상 흐름을 그대로
            ratio = json.loads(DRNK_RATIO.read_text())
            days = [end - timedelta(days=len(ratio) - 1 - i) for i in range(len(ratio))]
            pairs = [(d.isoformat(), last * r) for d, r in zip(days, ratio) if d.weekday() < 5][-DAYS:]
            ds, path = [p[0] for p in pairs], [p[1] for p in pairs]
        else:
            ds, path = dates, walk(last, mu, sigma, seed)
        close = [rnd(v, cur) for v in path]
        doc = {"symbol": sid, "currency": cur, "name": f"{name} (가상 종목)" if sid != FX[0] else name,
               "updated": updated, "virtual": True, "dates": ds, "close": close, "adj": close}
        (OUT / "prices" / f"{sid.replace('=', '_')}.json").write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")) + "\n")
        # 지금 가격: 마지막 종가에서 씨앗으로 정한 작은 움직임
        move = -0.0221 if sid == "DRNK" else random.Random(seed + 1).gauss(0, sigma / math.sqrt(252))
        now = rnd(close[-1] * (1 + move), cur)
        quotes[sid] = {"symbol": sid, "currency": cur, "name": doc["name"], "regular": now, "regular_time": now_t,
                       "prev_close": close[-1], "pre": None, "post": None, "last": now, "last_time": now_t,
                       "last_session": "regular", "fetched": now_t, "virtual": True}
    for sid, name, market, cur, sector, search, *_, about in SYMBOLS:
        symbols.append({"id": sid, "name": name, "market": market, "currency": cur, "sector": sector,
                        "search": search, "about": about, "virtual": True})
    meta = {"note": "가상 종목·가상 가격 (tools/sample_market.py). 실제 시세가 아니다. 키 등록 전 화면용",
            "updated": updated, "start_holdings": [{"symbol": "DRNK", "qty": 4, "avg": 400}, {"symbol": "NXIX", "qty": 1, "avg": 430}]}
    (OUT / "symbols.json").write_text(json.dumps({**meta, "symbols": symbols}, ensure_ascii=False, indent=1) + "\n")
    (OUT / "quotes.json").write_text(json.dumps(quotes, ensure_ascii=False, indent=1) + "\n")
    print("data/sample:", len(rows), "종목", dates[0], "~", dates[-1])


if __name__ == "__main__":
    main()
