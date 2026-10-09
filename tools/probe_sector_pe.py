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

# 2) 다모다란 과거 파일 보관 페이지: 미국 업종 PER(pedataYY.xls)만
import urllib.parse
arch = []
try:
    st, html = get(BASE + "New_Home_Page/dataarchived.html")
    hrefs = re.findall(r'href="([^"]+)"', html.decode("latin-1"), re.I)
    arch = sorted({h for h in hrefs if re.search(r"(^|/)pedata\d*\.xlsx?$", h, re.I)} | {BASE + "pc/archives/pedata25.xls", BASE + "pc/datasets/pedata.xls"})
    report["archive_page"] = {"status": st, "us_pe_files": arch,
                              "other_pe_like": sorted({h for h in hrefs if re.search(r"/pe[a-z]*\d*\.xls", h, re.I)} - set(arch))[:40]}
except Exception as e:
    report["archive_page"] = {"error": repr(e)}

KEYS = r"^(semiconductor|software \(system|drugs? \(pharm|food processing|utility \(general\)|total market)$"
lines = []
for h in arch:
    url = urllib.parse.urljoin(BASE + "New_Home_Page/dataarchived.html", h)
    try:
        st, data = get(url)
        sheets = read_sheet(data)
        for sname, rows in sheets.items():
            hi = next((i for i, r in enumerate(rows) if r and str(r[0]).strip().lower().startswith("industry")), None)
            if hi is None:
                continue
            hdr = [str(c).strip() for c in rows[hi]]
            body = [r for r in rows[hi + 1:] if r and str(r[0]).strip()]
            def col(name):
                return next((j for j, c in enumerate(hdr) if re.search(name, c, re.I)), None)
            cur, trl, fwd, gro = col(r"aggregate.*(only money|trailing net)"), col(r"aggregate.*net income \(all|aggregate market cap/ aggregate net"), col(r"forward pe"), col(r"growth")
            vals = {}
            for r in body:
                if re.search(KEYS, str(r[0]).strip(), re.I):
                    f = lambda j: (round(float(r[j]), 2) if j is not None and str(r[j]).replace('.', '', 1).replace('-', '', 1).isdigit() else (str(r[j])[:8] if j is not None else None))
                    vals[str(r[0]).strip()[:14]] = [f(cur), f(trl), f(fwd), f(gro)]
            lines.append({"file": h, "n": len(body), "header": hdr, "aggPos/aggAll/fwd/growth": vals})
            break
    except Exception as e:
        lines.append({"file": h, "error": repr(e)})
report["archive_files"] = lines

# 3) Yahoo 업종 ETF PER (쿠키·crumb 방식)
etfs = ["XLK", "SMH", "XLV", "XLP", "XLU", "IJR"]
try:
    import http.cookiejar
    cj = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    op.addheaders = list(UA.items())
    try:
        op.open("https://fc.yahoo.com", timeout=20)
    except Exception:
        pass
    crumb = op.open("https://query2.finance.yahoo.com/v1/test/getcrumb", timeout=20).read().decode()
    pe = {}
    for t in etfs:
        u = f"https://query2.finance.yahoo.com/v10/finance/quoteSummary/{t}?modules=summaryDetail,defaultKeyStatistics&crumb={urllib.parse.quote(crumb)}"
        d = json.loads(op.open(u, timeout=20).read())["quoteSummary"]["result"][0]
        sd = d.get("summaryDetail", {})
        pe[t] = {"trailingPE": (sd.get("trailingPE") or {}).get("raw"), "forwardPE": (sd.get("forwardPE") or {}).get("raw")}
    report["yahoo"] = pe
except Exception as e:
    report["yahoo"] = {"error": repr(e)}

js = json.dumps(report, ensure_ascii=False, default=str)
open(os.path.join(OUT, "report.json"), "w").write(js)
print("=== REPORT ===")
for k, v in report.items():
    if k == "archive_files":
        for x in v:
            print("ARCH", json.dumps(x, ensure_ascii=False, default=str))
    else:
        print(k, json.dumps(v, ensure_ascii=False, default=str)[:2500])
