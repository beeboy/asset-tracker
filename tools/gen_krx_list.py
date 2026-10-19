#!/usr/bin/env python3
"""코스피·코스닥 전체 상장 종목 목록을 만든다 (앱 '종목 추가'의 한글 이름 찾기).

KRX KIND 상장법인목록(EUC-KR HTML 표)에서 종목 코드·한글 이름·시장을 꺼내
ios-app/Naeilo/Resources/krx_stocks.json 에 [[코드, 이름, "K"|"Q"], ...] 로 쓴다.
K = 코스피(.KS), Q = 코스닥(.KQ). 코넥스는 뺀다. ETF 는 KIND 목록에 없다.

쓰는 법: python3 tools/gen_krx_list.py
"""
import html, json, os, re, urllib.request

URL = "https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&searchType=13"
OUT = os.path.join(os.path.dirname(__file__), "..", "ios-app", "Naeilo", "Resources", "krx_stocks.json")
MARKET = {"유가": "K", "유가증권": "K", "코스피": "K", "코스닥": "Q"}


def main():
    req = urllib.request.Request(URL, headers={"User-Agent": "Mozilla/5.0"})
    raw = urllib.request.urlopen(req, timeout=60).read().decode("euc-kr", errors="replace")
    rows = []
    for tr in re.findall(r"<tr>(.*?)</tr>", raw, re.S):
        tds = [html.unescape(re.sub(r"<[^>]+>", "", t)).strip() for t in re.findall(r"<td[^>]*>(.*?)</td>", tr, re.S)]
        if len(tds) < 3:
            continue
        name, market, code = tds[0], tds[1], tds[2]
        m = MARKET.get(market)
        if m and re.fullmatch(r"[0-9A-Z]{6}", code):
            rows.append([code, name, m])
    rows.sort()
    if len(rows) < 2000:
        raise SystemExit(f"종목이 너무 적어요 ({len(rows)}개). 쓰지 않아요.")
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(rows)}개 → {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
