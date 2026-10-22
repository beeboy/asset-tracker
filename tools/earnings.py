#!/usr/bin/env python3
"""내 몫 실적 자료: SEC EDGAR companyfacts 에서 분기 실적을 받아 data/earnings/{종목}.json 에 쓴다.

  python3 tools/earnings.py            # data/tickers.json 의 회사 종목을 받아 쓴다 (ETF 는 CIK 가 없어 건너뛴다)
  python3 tools/earnings.py --check    # 받아 쓴 뒤 요약을 찍고, 자료가 이상하면 실패로 끝난다 (PR 검사용)
  python3 tools/earnings.py NVDA AAPL  # 몇 종목만

규칙 (docs/decisions.md 2026-10-22):
- 분기는 신고서의 시작·끝 날짜로 고른다(80~100일). 4분기 = 연간 − 1~3분기.
- 매출·원가·연구비·판관비·순이익은 아래 태그 순서표에서 처음 있는 것을 쓴다. 분기마다 따로 고른다(회사가 태그를 바꿔도 이어진다).
- 주식 수 = 그 분기를 처음 실은 신고서 첫 장의 발행 주식 수(dei:EntityCommonStockSharesOutstanding). 없으면 그 분기 가중평균 희석 주식 수.
- 발표일 = 그 분기 끝 뒤, 신고일까지 사이의 첫 8-K 2.02 공시일. 없으면 신고일.
- 새 8-K 2.02 가 있는데 숫자가 아직 없으면 pending 에 그 날을 적는다 (앱은 금색 점만 띄우고 강을 '차오르는 중'으로 둔다).
- 국내(DART)는 아직 없다 (docs/HANDOFF.md).
"""
import json, os, sys, time, urllib.request
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data/earnings"
# SEC 는 연락처(이메일)가 없는 User-Agent 를 403 으로 막는다. Actions 시크릿 SEC_UA 가 있으면 그 값
UA = os.environ.get("SEC_UA") or "naeilo earnings-bot admin@naeilo.com"
KEEP = 8   # 분기 수: 지난 4분기 + 작년 같은 분기를 넉넉히

# 태그 찾는 순서표 (앞이 먼저)
TAGS = {
    "revenue": ["RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues",
                "RevenueFromContractWithCustomerIncludingAssessedTax", "SalesRevenueNet", "SalesRevenueGoodsNet"],
    "cogs": ["CostOfRevenue", "CostOfGoodsAndServicesSold", "CostOfGoodsSold"],
    "rnd": ["ResearchAndDevelopmentExpense", "ResearchAndDevelopmentExpenseExcludingAcquiredInProcessCost"],
    "sga": ["SellingGeneralAndAdministrativeExpense"],
    "net": ["NetIncomeLoss", "ProfitLoss"],
}
FORMS = {"10-Q", "10-K", "10-Q/A", "10-K/A"}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    for i in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            if i == 2 or getattr(e, "code", None) == 404: raise
            print(f"  다시 ({e})", file=sys.stderr); time.sleep(2 * (i + 1))


def d(s): return date.fromisoformat(s)


def months(start, end):
    """분기 달 이름: 시작+15일의 달 ~ 끝−15일의 달 (52·53주 결산도 달로). ("7~9", "25.7~9")"""
    a, b = d(start) + timedelta(days=15), d(end) - timedelta(days=15)
    m = f"{a.month}~{b.month}"
    return m, f"{a.year % 100:02d}.{m}"   # 해를 넘는 분기(11~1)는 시작한 해


def durations(facts, tag, shares=False):
    """태그 하나의 기간 값: {(시작, 끝): (값, 처음 신고일, 처음 신고서 번호)}. 값은 가장 나중 신고서(정정 반영).
    돈 태그는 통화 단위, shares=True 면 주식 수 단위("shares")를 읽는다"""
    units = facts.get(tag, {}).get("units", {})
    unit = "shares" if shares else next((k for k in units if k != "shares"), None)
    rows, out = units.get(unit, []), {}
    for r in sorted(rows, key=lambda r: r["filed"]):
        if r.get("form") not in FORMS or "start" not in r: continue
        k = (r["start"], r["end"])
        if k in out: out[k] = (r["val"], out[k][1], out[k][2])
        else: out[k] = (r["val"], r["filed"], r["accn"])
    return out, unit


def quarters(dur):
    """기간 값에서 분기만: 80~100일은 그대로, 연간(350~380일)은 안의 1~3분기를 빼서 4분기를 만든다"""
    q = {k: v for k, v in dur.items() if 80 <= (d(k[1]) - d(k[0])).days <= 100}
    for (s, e), (val, filed, accn) in dur.items():
        if not 350 <= (d(e) - d(s)).days <= 380: continue
        ins = sorted(k for k in q if d(k[0]) >= d(s) - timedelta(days=3) and d(k[1]) <= d(e) - timedelta(days=60))
        if len(ins) != 3: continue
        q4s = (d(ins[-1][1]) + timedelta(days=1)).isoformat()
        if any(k[1] == e for k in q): continue     # 4분기가 이미 따로 있으면 그대로
        q[(q4s, e)] = (val - sum(q[k][0] for k in ins), filed, accn)
    return q


def build(sym, cik, name):
    cikp = f"{int(cik):010d}"
    facts = get(f"https://data.sec.gov/api/xbrl/companyfacts/CIK{cikp}.json").get("facts", {})
    time.sleep(0.15)
    subs = get(f"https://data.sec.gov/submissions/CIK{cikp}.json")
    gaap = facts.get("us-gaap", {})
    if not gaap: return None   # 해외 기업(IFRS)·자료 없음: 1차에선 줄표

    per, currency = {}, None
    for key, tags in TAGS.items():
        per[key] = {}
        for t in tags:
            dur, unit = durations(gaap, t)
            for k, v in quarters(dur).items():
                per[key].setdefault(k, v)
            if key == "revenue" and unit and dur and not currency: currency = unit
    base = per["revenue"] or per["net"]
    if not base: return None

    # 주식 수: 신고서 첫 장 (같은 신고서의 여러 종류 주식은 더한다), 없으면 가중평균 희석
    cover = {}
    for r in facts.get("dei", {}).get("EntityCommonStockSharesOutstanding", {}).get("units", {}).get("shares", []):
        cover.setdefault(r["accn"], set()).add(r["val"])
    wavg = quarters(durations(gaap, "WeightedAverageNumberOfDilutedSharesOutstanding", shares=True)[0])

    # 8-K 2.02 (실적 발표) 날짜
    rec = subs.get("filings", {}).get("recent", {})
    ann = sorted(dt for f, dt, it in zip(rec.get("form", []), rec.get("filingDate", []), rec.get("items", []))
                 if f == "8-K" and "2.02" in (it or ""))

    rows = []
    for k in sorted(base, key=lambda k: k[1])[-KEEP:]:
        s, e = k
        _, filed, accn = base[k]
        sh = sum(cover[accn]) if accn in cover else (wavg.get(k) or (None,))[0]
        a = next((x for x in ann if e < x <= filed), filed)
        m, label = months(s, e)
        row = {"start": s, "end": e, "months": m, "label": label, "filed": filed, "announced": a, "shares": sh}
        for key in TAGS: row[key] = per[key].get(k, (None,))[0]
        rows.append(row)
    last = rows[-1]
    newer = [x for x in ann if x > last["filed"]]
    return {"symbol": sym, "name": name, "cik": int(cik), "currency": currency or "USD",
            "pending": {"announced": newer[-1]} if newer else None, "quarters": rows}


def check(sym, j):
    """자료가 말이 되는지. 실패: 분기 겹침, 마지막 분기 순이익·주식 수 없음. 알림만: 분기 4개 미만, 매출 없음(매출 전 회사)"""
    q, hard, soft = j["quarters"], [], []
    if len(q) < 4: soft.append(f"분기 {len(q)}개")
    for r in q[-4:]:
        if r["revenue"] is None: soft.append(f"{r['label']} 매출 없음")
    if q[-1]["net"] is None: hard.append(f"{q[-1]['label']} 순이익 없음")
    if not q[-1]["shares"]: hard.append(f"{q[-1]['label']} 주식 수 없음")
    for a, b in zip(q, q[1:]):
        if a["end"] >= b["start"]: hard.append(f"{a['label']}·{b['label']} 겹침")
    return hard, soft


def main(argv):
    chk = "--check" in argv
    syms = [a for a in argv if not a.startswith("--")] or json.loads((ROOT / "data/tickers.json").read_text())["tickers"]
    table = {v["ticker"].upper(): (v["cik_str"], v["title"]) for v in get("https://www.sec.gov/files/company_tickers.json").values()}
    OUT.mkdir(parents=True, exist_ok=True)
    idx_path = OUT / "index.json"
    idx = json.loads(idx_path.read_text()) if idx_path.exists() else {"symbols": {}}
    problems = []
    for sym in syms:
        if sym.upper() not in table:
            print(f"{sym}: CIK 없음 (ETF·해외) 건너뜀"); continue
        cik, name = table[sym.upper()]
        time.sleep(0.15)
        try:
            j = build(sym, cik, name)
        except Exception as e:
            # 404 = SEC 에 회사 자료가 없음 (신탁형 ETF 등). 실패가 아니라 건너뜀
            if getattr(e, "code", None) == 404:
                print(f"{sym}: SEC 회사 자료 없음 (ETF 등) 건너뜀"); continue
            problems.append(f"{sym}: 받기 실패 {e}"); continue
        if not j:
            print(f"{sym}: us-gaap 분기 자료 없음 건너뜀"); continue
        last = j["quarters"][-1]
        three = all(last[k] is not None for k in ("cogs", "rnd", "sga"))
        print(f"{sym}: {len(j['quarters'])}분기, 마지막 {last['label']} 매출 {last['revenue']} 순이익 {last['net']} "
              f"주식 {last['shares']} 발표 {last['announced']} 갈래 {3 if three else 1}"
              + (f" 대기 {j['pending']['announced']}" if j["pending"] else ""))
        if chk:
            hard, soft = check(sym, j)
            problems += [f"{sym}: {b}" for b in hard]
            for b in soft: print(f"  알림 {sym}: {b}")
        (OUT / f"{sym}.json").write_text(json.dumps(j, ensure_ascii=False, indent=1) + "\n")
        idx["symbols"][sym] = {"end": last["end"], "label": last["label"], "announced": last["announced"],
                               "pending": j["pending"]["announced"] if j["pending"] else None}
    idx_path.write_text(json.dumps(idx, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
    done = [k for k in idx["symbols"] if k != "DRNK"]
    if chk and not done: problems.append("받은 종목이 하나도 없음")
    if problems:
        print("\n".join(problems)); sys.exit(1 if chk else 0)


if __name__ == "__main__":
    main(sys.argv[1:])
