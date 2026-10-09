"""업종 PER 데이터 출처 확인용 (일회성). GitHub Actions 에서 실행해 결과를 로그와 probe_out/ 에 남긴다.
다모다란 업종별 PER 파일(현재·과거)과 Yahoo 업종 ETF PER 이 실제로 받아지는지, 어떤 열이 있는지 본다."""
import io, json, os, re, sys, urllib.request

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
BASE = "https://pages.stern.nyu.edu/~adamodar/"
OUT = "probe_out"
os.makedirs(OUT, exist_ok=True)
report = {}


def get(url, timeout=30):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()


def read_sheet(data):
    """xls(BIFF) 또는 xlsx 를 행 목록으로. 시트별 앞부분만."""
    out = {}
    if data[:2] == b"PK":
        import openpyxl
        wb = openpyxl.load_workbook(io.BytesIO(data), read_only=True, data_only=True)
        for ws in wb.worksheets:
            out[ws.title] = [[c for c in row] for row in ws.iter_rows(values_only=True)]
    else:
        import xlrd
        wb = xlrd.open_workbook(file_contents=data)
        for sh in wb.sheets():
            out[sh.name] = [sh.row_values(i) for i in range(sh.nrows)]
    return out


def summarize(name, data):
    info = {"bytes": len(data)}
    try:
        sheets = read_sheet(data)
    except Exception as e:
        info["error"] = repr(e)
        return info
    info["sheets"] = list(sheets)
    for sname, rows in sheets.items():
        hdr_i = next((i for i, r in enumerate(rows) if r and str(r[0]).strip().lower().startswith("industry")), None)
        if hdr_i is None:
            continue
        hdr = [str(c).strip() for c in rows[hdr_i]]
        body = [r for r in rows[hdr_i + 1:] if r and str(r[0]).strip()]
        pick = [r for r in body if re.search(r"semiconductor|software \(system|drugs? \(pharm|food processing|utility \(general\)|total market", str(r[0]), re.I)]
        info["data_sheet"] = sname
        info["header"] = hdr
        info["n_rows"] = len(body)
        info["date_hint"] = [str(c) for r in rows[:hdr_i] for c in r if str(c).strip()][:6]
        info["sample"] = [[str(c)[:40] for c in r] for r in pick]
        break
    return info


# 1) 다모다란 현재 파일
for fn in ["pedata.xls", "pedata.xlsx"]:
    url = BASE + "pc/datasets/" + fn
    try:
        st, data = get(url)
        report["current_" + fn] = {"url": url, "status": st, **summarize(fn, data)}
    except Exception as e:
        report["current_" + fn] = {"url": url, "error": repr(e)}

# 2) 다모다란 과거 파일 보관 페이지
arch_links = []
for page in ["New_Home_Page/dataarchived.html", "New_Home_Page/datacurrent.html"]:
    url = BASE + page
    try:
        st, html = get(url)
        hrefs = re.findall(r'href="([^"]+)"', html.decode("latin-1"))
        pe = [h for h in hrefs if re.search(r"pe(data)?\d*\.xls", h, re.I) or re.search(r"/pe[^/]*\.xls", h, re.I)]
        report["page_" + page] = {"status": st, "n_links": len(hrefs), "pe_links": pe[:80],
                                  "archive_dirs": sorted({h.rsplit("/", 1)[0] for h in hrefs if h.lower().endswith((".xls", ".xlsx"))})[:30]}
        if "archived" in page:
            arch_links = pe
    except Exception as e:
        report["page_" + page] = {"url": url, "error": repr(e)}

# 3) 과거 파일 몇 개 실제로 열어 보기 (최근, 중간, 가장 오래된)
def absurl(h):
    if h.startswith("http"):
        return h
    return urllib.parse.urljoin(BASE + "New_Home_Page/dataarchived.html", h)

import urllib.parse
tries = arch_links[:3] + arch_links[len(arch_links) // 2:len(arch_links) // 2 + 1] + arch_links[-2:]
for h in dict.fromkeys(tries):
    url = absurl(h)
    try:
        st, data = get(url)
        report["archive " + h] = {"url": url, "status": st, **summarize(h, data)}
    except Exception as e:
        report["archive " + h] = {"url": url, "error": repr(e)}

# 4) Yahoo 업종 ETF PER (현재값만)
etfs = ["XLK", "SMH", "XLV", "XLP", "XLU", "XLF", "XLE", "IJR"]
for host in ["query1", "query2"]:
    url = f"https://{host}.finance.yahoo.com/v7/finance/quote?symbols=" + ",".join(etfs)
    try:
        st, data = get(url)
        res = json.loads(data).get("quoteResponse", {}).get("result", [])
        report["yahoo_" + host] = {"status": st, "pe": {q["symbol"]: [q.get("trailingPE"), q.get("forwardPE")] for q in res}}
        break
    except Exception as e:
        report["yahoo_" + host] = {"url": url, "error": repr(e)}

js = json.dumps(report, ensure_ascii=False, indent=1, default=str)
open(os.path.join(OUT, "report.json"), "w").write(js)
print(js)
