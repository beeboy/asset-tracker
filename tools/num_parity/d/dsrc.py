#!/usr/bin/env python3
"""숫자 맞추기 D 단계 (시세·동기화): 아이폰 원본 Swift 에서 함수·형을 그대로 꺼내 DSrc.swift 를 만들고, 고정 입력 input.json 을 쓴다.

parity.py gen 이 부른다. 꺼내는 것 (앱 원본 그대로, 네트워크·저장 쪽은 빈 자리로 바꾼다):
  Sync.derive, SiteStats.params·fx·corr, ExtraStats.augment, KoreaEstimate (info·synth 등), StockSearch.norm·local,
  KrxList, CustomSymbols.yahoo(for:)·chart 파싱, SitePrices.hist, MarFeed smbs 파싱, StockCatalog 전체.
"""
import json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
IOS = ROOT / "ios-app"
DATA = IOS / "Naeilo" / "Data"


def span(rel, first, last, nth=1, k=1):
    """원본 줄을 번호 대신 내용으로 찾는다 (아이폰 코드가 위아래로 밀려도 같은 덩어리): first 줄부터 그 뒤 처음 나오는 last 줄까지"""
    t = (DATA / rel).read_text().split("\n")
    hits = [k for k, l in enumerate(t) if l == first]
    if len(hits) < nth: raise SystemExit(f"{rel}: 시작 줄을 못 찾음: {first!r}")
    i = hits[nth - 1]
    j = [x for x in range(i, len(t)) if t[x] == last][k - 1]
    return t[i:j + 1]


def strip_strings(line):
    """중괄호 세기용: 문자열·주석을 지운다 (보간 속 따옴표는 짝이 맞으면 그대로 넘어간다)"""
    out, i, q = [], 0, False
    while i < len(line):
        c = line[i]
        if not q and line.startswith("//", i): break
        if q and c == "\\": i += 2; continue
        if c == '"': q = not q; i += 1; continue
        if not q: out.append(c)
        i += 1
    return "".join(out)


def block(path, start, opener="{", closer="}"):
    """start(정규식)로 시작하는 줄부터 짝 닫는 괄호 줄까지 (줄 목록)"""
    lines = (DATA / path if "/" not in path else IOS / path).read_text().split("\n")
    rx = re.compile(start)
    for i, l in enumerate(lines):
        if rx.search(l):
            d, seen = 0, False
            for j in range(i, len(lines)):
                s = strip_strings(lines[j])
                d += s.count(opener) - s.count(closer)
                seen = seen or opener in s
                if seen and d <= 0: return lines[i:j + 1]
            raise ValueError(f"{path}: {start} 닫는 괄호 없음")
    raise ValueError(f"{path}: {start} 없음")


def line(path, n):
    return (DATA / path).read_text().split("\n")[n - 1]


def lines(path, a, b):
    return (DATA / path).read_text().split("\n")[a - 1:b]


def j(ls): return "\n".join(ls)


def swift_src():
    p = []
    p.append("import Foundation\nimport CryptoKit\nimport CommonCrypto\n")
    p.append("// 아래는 tools/num_parity/d/dsrc.py 가 ios-app 원본에서 꺼낸 코드다. 직접 고치지 않는다.\n")
    p.append(line("SampleData.swift", 6))                                     # enum Currency
    p.append(j(block("StockSearch.swift", r"^struct StockHit")))
    p.append(j(block("StockCatalog.swift", r"^enum StockCatalog")))
    # 빈 자리: 앱 화면·저장 쪽
    p.append("struct Symbol { let id: String; let name: String; let market: String; let currency: Currency; let search: String }")
    p.append("enum Sample {\n    static var symbols: [Symbol] = []\n" + j(block("SampleData.swift", r"static let lensParams", "[", "]")) + "\n}")
    yahoo = j(block("StockSearch.swift", r"static func yahoo\(for s: Symbol\)"))
    p.append("final class CustomSymbols {\n    static let shared = CustomSymbols()\n"
             "    struct Saved { let id: String; let yahoo: String; let name: String; let usd: Bool; var kp: Double? = nil; var kd: String? = nil }\n"
             "    " + line("StockSearch.swift", 199).strip() + "\n"
             "    var items: [Saved] = []\n    var symbols: [Symbol] = []\n    func isLive(_ id: String) -> Bool { false }\n"
             + yahoo + "\n}")
    krx = j(block("StockSearch.swift", r"^enum KrxList"))
    krx = krx.replace('Bundle.main.url(forResource: "krx_stocks", withExtension: "json")', "Optional(URL(fileURLWithPath: KRX_PATH))")
    p.append("var KRX_PATH = \"\"\n" + krx)
    p.append("enum StockSearch {\n" + line("StockSearch.swift", 92) + "\n" + line("StockSearch.swift", 95) + "\n"
             + j(block("StockSearch.swift", r"static func local\(")) + "\n}")
    p.append(j(block("StockSearch.swift", r"^enum KoreaEstimate")))
    p.append("enum YahooSample { " + line("Market.swift", 268).strip() + " }")
    p.append("final class Market { static let shared = Market(); var stats: MC.Stats? = nil; var prices: SitePrices.File? = nil }")
    p.append("enum SitePrices {\n    struct File { var h: [String: YahooSample.Hist] }\n" + j(block("Market.swift", r"static func hist\(_ o")) + "\n}")
    p.append("enum SiteStats {\n" + j(span("SiteStats.swift", "    typealias T = MC.Stats.Ticker", "    typealias File = MC.Stats")) + "\n"
             "    static func key(_ id: String) -> String { id }\n    static func mcKey(_ id: String) -> String { id }\n"
             + j(block("SiteStats.swift", r"static func params\(")) + "\n" + j(block("SiteStats.swift", r"static func fx\("))
             + "\n" + j(block("SiteStats.swift", r"static func corr\(")) + "\n}")
    p.append("final class ExtraStats {\n" + line("SiteStats.swift", 76) + "\n    var items: [String: Item] = [:]\n"
             + j(block("SiteStats.swift", r"func augment\(")) + "\n}")
    p.append("enum Sync {\n" + line("Sync.swift", 23) + "\n" + j(block("Sync.swift", r"static func derive\(")) + "\n}")
    num = (IOS / "WidgetShared/Models.swift").read_text().split("\n")
    i = next(k for k, l in enumerate(num) if l.startswith("func num(_ v: Any?)"))
    p.append("\n".join(num[i:i + 7]))
    # 차트 파싱: CustomSymbols.chart 의 응답 읽는 줄 (424~450) 그대로. 오늘 날짜만 받은 시각으로
    chart = j(span("StockSearch.swift", "        guard let r = ((o[\"chart\"] as? [String: Any])?[\"result\"] as? [[String: Any]])?.first,", "        return ((price, prevClose), Hist(dates: dates, closes: cl))")).replace("f.string(from: Date())", "f.string(from: now)")
    p.append("extension CustomSymbols {\n    static func chartParse(_ o: [String: Any], keepToday: Bool, now: Date) throws -> ((last: Double, prev: Double), CustomSymbols.Hist) {\n"
             + chart + "\n    }\n}")
    # 매매기준율 표 파싱: MarFeed.smbs 의 정규식(532줄 글자)과 533~544줄 그대로. 받은 시각만 인자로
    pat = re.search(r'NSRegularExpression\(pattern: ("(?:[^"\\]|\\.)*")\)', line("Market.swift", 532)).group(1)
    sm = j(span("Market.swift", "        var days: [Prev] = []", "        return File(rate: last.rate, date: last.date, got: Date().timeIntervalSince1970, prev: pv)")).replace("Date().timeIntervalSince1970", "got")
    p.append("enum MarFeed {\n" + line("Market.swift", 488) + "\n" + line("Market.swift", 490) + "\n"
             "    static func smbsParse(_ t: String, got: Double) -> File? {\n"
             f"        guard let re = try? NSRegularExpression(pattern: {pat}) else {{ return nil }}\n" + sm + "\n    }\n}")
    return "\n\n".join(x for x in p if x) + "\n"


def prices(*names):
    out = {}
    for n in names:
        o = json.loads((ROOT / "data/prices" / (n.replace("=", "_").replace("^", "_") + ".json")).read_text())
        out[o["symbol"]] = {"dates": o["dates"], "close": o["close"]}
    return out


def hist(o):
    """사이트 파일 → Hist (빈 값은 앞 값). 입력 만들기용"""
    c = o["close"]; prev = next((x for x in c if x is not None), 1); cl = []
    for x in c:
        if x is not None: prev = x
        cl.append(prev)
    return {"dates": o["dates"], "closes": cl}


def chart_obj(cur, tz, days, closes, price=None, extra_meta=None):
    base = {"USD": 1_760_000_000, "KRW": 1_760_000_000}[cur] if cur in ("USD", "KRW") else 1_760_000_000
    ts = [base + 86400 * i + 3600 * 15 for i in range(days)]
    meta = {"currency": cur, "exchangeTimezoneName": tz}
    if price is not None: meta["regularMarketPrice"] = price
    if extra_meta: meta.update(extra_meta)
    return {"chart": {"result": [{"meta": meta, "timestamp": ts, "indicators": {"quote": [{"close": closes}]}}]}}


def make_input(work):
    stats = json.loads((ROOT / "data/stats.json").read_text())
    P = prices("SPY", "QQQ", "EWY", "KRW=X", "SOXX", "SCHD", "AAPL", "SGOV")
    tick = list(stats["tickers"].keys())
    corr_keys = stats["corr"]["keys"]
    # 전망 입력값: 표에 있는 종목, 표 밖 종목(예시 표·기본값), 원/달러
    ids = tick[:12] + ["DRNK", "QQQ", "005930", "ZZZ", "KRW=X", "KR~SPY", "KR~EWY~H"]
    pairs = []
    ck = corr_keys[:6] + ["KRW=X", "ZZZ", "YYY"] + [k for k, v in stats["tickers"].items() if v.get("cash")][:2]
    for a in ck:
        for b in ck: pairs.append([a, b])
    # 비중 바꿔 비교: 표 밖 종목을 받은 종가로 (사이트 종가를 이름만 바꿔 넣는다)
    def item(name, h, usd=True):
        return {"name": name, "hist": h, "at": 700_000_000.0, "usd": usd, "yahoo": name}
    aapl, sgov, qqq = hist(P["AAPL"]), hist(P["SGOV"]), hist(P["QQQ"])
    short = {"dates": aapl["dates"][-120:], "closes": aapl["closes"][-120:]}
    tiny = {"dates": aapl["dates"][-30:], "closes": aapl["closes"][-30:]}
    items = {"XAAPL": item("XAAPL", aapl), "XSGOV": item("XSGOV", sgov), "XSHORT": item("XSHORT", short), "XTINY": item("XTINY", tiny), "XQQQ": item("XQQQ", qqq)}
    site_prices = {k: hist(v) for k, v in P.items()}
    names = [["TIGER 미국S&P500"], ["KODEX 미국나스닥100(H)"], ["TIGER 미국배당다우존스"], ["KODEX 미국S&P500 레버리지"],
             ["ACE 미국필라델피아반도체"], ["삼성전자"], ["SOL 미국 S&P 500"], ["Nasdaq 100 Hedged", "x"], ["", "Schwab Dividend Equity"],
             ["TIGER 미국나스닥100커버드콜(합성)"], ["kodex s&p500"], ["PHLX semi (h)"]]
    ids_info = [["360750", "TIGER 미국S&P500", ""], ["005930", "삼성전자", ""], ["133690", "TIGER 미국나스닥100", ""], ["999999", "모르는 종목", "Unknown"],
                ["069500", "KODEX 200", ""], ["379800", "KODEX 미국S&P500TR", ""]]
    synth = [[23000, "2026-10-10", "SPY", False], [23000, "2026-10-10", "SPY", True], [15000, "2025-01-02", "QQQ", False],
             [52000, "2026-10-21", None, False], [1_000_000, "2024-06-03", None, False], [100, "2020-01-01", "SOXX", False],
             [0, "2026-10-10", "SPY", False], [9000, "2030-01-01", "SCHD", True]]
    syms = [["DRNK", "드링커", "미국 주식", "usd", "drinker"], ["QQQ", "나스닥100 ETF", "미국 ETF", "usd", "invesco nasdaq"],
            ["AAPL", "애플", "미국 주식", "usd", "apple"], ["005930", "삼성전자", "코스피", "krw", "samsung"],
            ["000660", "SK하이닉스", "코스피", "krw", "sk hynix"], ["035720", "카카오", "코스피", "krw", "kakao"],
            ["069500", "KODEX 200", "코스피 ETF", "krw", "kodex"], ["360750", "TIGER 미국S&P500", "코스피 ETF", "krw", "tiger s&p"]]
    queries = ["", "삼성", "Sam", "005", "kodex", "S&P", "하이닉스", "에너지", "tiger", "NVDA", "카카오", "LG", "현대", "바이오", "  삼 성 ", "q", "미국", "코스닥", "00", "레버리지"]
    real = (work / "smbs_real.xml").read_text(encoding="latin-1") if (work / "smbs_real.xml").exists() else ""
    smbs = [real,
            "<set label='26.10.20' value='1,385.50' /><set label='26.10.21' value='1,391.2'/><set label='26.10.17' value='1,380'/>",
            '<set label="2026-10-20" value="1385.5"/><set label="2026/10/21" value="1390"/>',
            "<set label='26.10.20' value='450' /><set label='26.10.21' value='5100' />",
            "<set label='10.21' value='1390' />", "", "<set label='26.10.21' toolText='x' value='1,389.9' />"]
    hists = [{"dates": ["a", "b", "c"], "close": [None, 2, None]}, {"dates": ["a", "b"], "close": [1.5, 2.5]}, {"dates": ["a"], "close": [1, 2]},
             {"dates": [], "close": []}, {"dates": ["a", "b"], "close": [None, None]}, {"dates": ["a", "b", "c"], "close": [3, None, 4.25]}, {"close": [1]}]
    now = 1_760_000_000 + 86400 * 4 + 3600 * 16
    charts = [[chart_obj("USD", "America/New_York", 5, [10, None, 11.5, 0, 12], 12.4), False],
              [chart_obj("USD", "America/New_York", 5, [10, 10.5, 11.5, 11, 12]), False],
              [chart_obj("KRW", "Asia/Seoul", 5, [70000, 71000, None, 72000, 73000], 73500), False],
              [chart_obj("KRW", "Asia/Seoul", 5, [70000, 71000, None, 72000, 73000], 73500), True],
              [chart_obj("JPY", "Asia/Tokyo", 3, [1, 2, 3]), False],
              [chart_obj("USD", "America/New_York", 3, [None, None, None]), False],
              [{"chart": {"result": []}}, False],
              [chart_obj("KRW", "Asia/Seoul", 1, [70000], 70500), False]]
    pws = ["", "a", "naeilo-test-1234", "한글 비밀번호", "éclair", "éclair", "🔐 emoji pw", "x" * 80]
    plains = ['{"holdings":[{"ticker":"QQQ","shares":3}],"version":1}', "", "한글 상태 " * 20]
    return {"stats": stats, "ids": ids, "pairs": pairs, "items": items, "augKeys": ["XAAPL", "XSGOV", "QQQ", "XSHORT", "XTINY", "XQQQ", "NOPE"],
            "sitePrices": site_prices, "names": names, "idsInfo": ids_info, "synth": synth, "syms": syms, "queries": queries,
            "krx": str(IOS / "Naeilo/Resources/krx_stocks.json"), "smbs": smbs, "smbsGot": 1_760_000_000.5, "hists": hists,
            "charts": charts, "now": now, "pws": pws, "aesPw": "naeilo-test-1234", "aesIv": list(range(12)), "plains": plains}


if __name__ == "__main__":
    import sys
    w = Path(sys.argv[1]); w.mkdir(parents=True, exist_ok=True)
    (w / "DSrc.swift").write_text(swift_src())
    (w / "input.json").write_text(json.dumps(make_input(w), ensure_ascii=False))
    print("D 입력·Swift 원본 →", w)
