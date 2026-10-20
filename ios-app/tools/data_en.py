#!/usr/bin/env python3
"""영어 화면용 종목 자료 (Naeilo/Resources/data_en.json) 만들기. 앱은 앱 언어가 영어일 때만 이 파일을 읽는다.
- names: 종목 코드 → 영어 이름 (StockCatalog.swift 의 en 에서 회사 꼬리 Inc.·Co., Ltd. 등을 뗀 것 + 아래 SAMPLE)
- words: 한국어 → 영어 (시장·업종·기업 소개 글). tools/data_en_words.json 을 그대로 쓴다
    python3 tools/data_en.py   # ios-app 에서 실행
"""
import json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SAMPLE = {"DRNK": "Drinker", "QQQ": "Nasdaq-100 ETF", "AAPL": "Apple", "NVDA": "NVIDIA", "MSFT": "Microsoft", "SPY": "S&P 500 ETF",
          "005930": "Samsung Electronics", "000660": "SK hynix", "035720": "Kakao", "360750": "TIGER US S&P500"}
TAIL = re.compile(r"[,.]?\s+(Inc|Corp|Corporation|Company|Co|Ltd|Limited|Holdings Inc|plc|PLC|N\.V|S\.A|AG|SE|Incorporated|Group Inc)\.?$")


def short(n):
    n = n.strip()
    for _ in range(3):
        n = re.sub(r",?\s*(Co\.,\s*Ltd\.?|Co\.,Ltd\.?|Co\.,\s*Ltd)$", "", n)
        n = TAIL.sub("", n).strip().rstrip(",")
    return n


def main():
    src = (ROOT / "Naeilo/Data/StockCatalog.swift").read_text(encoding="utf-8")
    names = {i: short(e) for i, e in re.findall(r'id: "([^"]+)", yahoo: "[^"]*", name: "[^"]*", alias: "[^"]*", en: "([^"]+)"', src)}
    names.update(SAMPLE)
    words = json.loads((ROOT / "tools/data_en_words.json").read_text(encoding="utf-8"))
    words.setdefault("국내 주식", "KR stock")
    out = ROOT / "Naeilo/Resources/data_en.json"
    out.write_text(json.dumps({"names": names, "words": words}, ensure_ascii=False, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    print(f"{len(names)} names, {len(words)} words -> {out}")


if __name__ == "__main__":
    main()
