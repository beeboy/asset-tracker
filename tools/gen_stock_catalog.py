"""웹 종목 찾기 목록(web/stocks.js)을 iOS 앱 목록에서 만든다. 앱과 웹이 같은 한글 이름으로 찾게.
읽는 곳: ios-app/Naeilo/Data/StockCatalog.swift (한글 이름 목록), SampleData.swift (예시 종목의 영문 검색어)
만드는 것: web/stocks.js, data/kr_stocks.json (server.py --collect 가 매일 모으는 국내 개별 종목)
실행: python3 tools/gen_stock_catalog.py  (앱 목록을 고친 뒤 다시 실행하고 web/index.html 판 번호를 올린다)"""
import json, pathlib, re

root = pathlib.Path(__file__).resolve().parent.parent
data = root / "ios-app/Naeilo/Data"
out, seen, kr = [], set(), []
f = r'(\w+): "((?:[^"\\]|\\.)*)"'
for line in (data / "StockCatalog.swift").read_text().splitlines():
    if ".init(" not in line: continue
    d = dict(re.findall(f, line))
    out.append({"s": d["yahoo"], "k": d["name"], "a": d["alias"] if d["alias"] != d["name"] else "", "e": d["en"], "m": d["market"]})
    seen.add(d["id"])
    if "currency: .krw" in line and "ETF" not in d["market"]:   # 국내 개별 종목: 사이트가 매일 지금 가격·종가를 모은다 (KoreaEstimate.popular 와 같은 규칙)
        kr.append({"id": d["id"], "yahoo": d["yahoo"], "name": d["name"]})
sample = (data / "SampleData.swift").read_text().split("static let baseSymbols")[1].split("]")[0]
for line in sample.splitlines():
    if ".init(" not in line: continue
    d = dict(re.findall(f, line))
    if d["id"] in seen or d["id"] == "DRNK": continue  # DRNK 는 앱 안의 가상 종목
    krw = "currency: .krw" in line
    out.append({"s": d["id"] + ".KS" if krw else d["id"], "k": d["name"], "a": "", "e": d["search"], "m": d["market"]})
js = ("// 자동 생성 (tools/gen_stock_catalog.py): iOS 앱의 한글 이름 목록. 직접 고치지 말고 앱 목록을 고친 뒤 다시 만든다.\n"
      "// s: Yahoo 기호, k: 한글 이름, a: 다른 이름, e: 영문 이름, m: 시장\n"
      "window.STOCKS = [\n" + ",\n".join(json.dumps(o, ensure_ascii=False) for o in out) + "\n];\n")
(root / "web/stocks.js").write_text(js)
(root / "data/kr_stocks.json").write_text(json.dumps({"note": "자동 생성 (tools/gen_stock_catalog.py). 사이트가 매일 모으는 국내 개별 종목", "stocks": kr}, ensure_ascii=False, indent=1) + "\n")
print(len(out), "종목, 국내 개별", len(kr))
