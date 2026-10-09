"""다모다란 업종 PER 로 96개 업종을 세 묶음(성장 지속 / 저평가 회복 / 버팀목)으로 나눈다 (확인용).
PER = 시가총액 합 / 흑자 기업 순이익 합. 10년 평균 = pedata15~pedata24 (2016~2025년 1월). 현재 = pedata.xls (2026년 1월)."""
import io, json, os, re, statistics, urllib.parse, urllib.request

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
BASE = "https://pages.stern.nyu.edu/~adamodar/"
OUT = "probe_out"
os.makedirs(OUT, exist_ok=True)


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


def rows_of(data):
    if data[:2] == b"PK":
        import openpyxl
        wb = openpyxl.load_workbook(io.BytesIO(data), read_only=True, data_only=True)
        return [[list(r) for r in ws.iter_rows(values_only=True)] for ws in wb.worksheets]
    import xlrd
    wb = xlrd.open_workbook(file_contents=data)
    return [[sh.row_values(i) for i in range(sh.nrows)] for sh in wb.sheets()]


def key(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def table(data):
    for rows in rows_of(data):
        hi = next((i for i, r in enumerate(rows) if r and str(r[0]).strip().lower().startswith("industry")), None)
        if hi is None:
            continue
        hdr = [str(c).strip() for c in rows[hi]]
        pe = next((j for j, c in enumerate(hdr) if re.search(r"only money|money.?making|aggregate.*trailing net", c, re.I)), None)
        gr = next((j for j, c in enumerate(hdr) if re.search(r"growth", c, re.I)), None)
        nf = next((j for j, c in enumerate(hdr) if re.search(r"number of firms", c, re.I)), None)
        print("  hdr pe:", hdr[pe] if pe is not None else None, "| growth:", hdr[gr] if gr is not None else None)
        out = {}
        for r in rows[hi + 1:]:
            if not r or not str(r[0]).strip():
                continue
            def num(j):
                try:
                    return float(r[j])
                except (TypeError, ValueError, IndexError):
                    return None
            out[key(str(r[0]))] = {"name": str(r[0]).strip(), "pe": num(pe) if pe is not None else None,
                                   "g": num(gr) if gr is not None else None, "n": num(nf) if nf is not None else None}
        return out
    return {}


html = get(BASE + "New_Home_Page/dataarchived.html").decode("latin-1")
hrefs = {re.search(r"pedata(\d+)\.xls", h).group(1): urllib.parse.urljoin(BASE + "New_Home_Page/dataarchived.html", h)
         for h in re.findall(r'href="([^"]+)"', html, re.I) if re.search(r"(^|/)pedata\d+\.xls$", h, re.I)}
years = [f"{y:02d}" for y in range(15, 25)]
hist = {}
for y in years:
    u = hrefs.get(y, BASE + f"pc/archives/pedata{y}.xls")
    try:
        hist[y] = table(get(u))
        print("got", y, len(hist[y]), "pe ok", sum(1 for v in hist[y].values() if v["pe"]), "semi", hist[y].get("semiconductor"))
    except Exception as e:
        print("fail", y, u, repr(e))
cur = table(get(BASE + "pc/datasets/pedata.xls"))
print("current", len(cur))

res = []
for k, c in cur.items():
    past = [hist[y][k]["pe"] for y in years if y in hist and k in hist[y] and hist[y][k]["pe"] and 0 < hist[y][k]["pe"] < 200]
    if not c["pe"] or len(past) < 7:
        res.append({**c, "avg": None, "yrs": len(past), "bucket": "자료 부족"})
        continue
    avg = statistics.mean(past)
    ratio = c["pe"] / avg
    g = c["g"] if c["g"] is not None else 0
    if k.startswith("totalmarket"):
        b = "시장 전체"
    elif ratio >= 1.15 and g >= 0.10 and c["pe"] / (g * 100) <= 2.5:
        b = "성장 지속"
    elif ratio >= 1.15:
        b = "과열"  # 이익 성장에 비해 너무 비싸짐 (PER / 성장률 > 2.5)
    elif ratio <= 0.90 and g >= 0.05:
        b = "저평가 회복"
    elif 0.85 < ratio < 1.15 and 0.03 <= g < 0.15:
        b = "버팀목"
    else:
        b = "해당 없음"
    res.append({**c, "avg": round(avg, 1), "yrs": len(past), "ratio": round(ratio, 2), "bucket": b})

json.dump(res, open(os.path.join(OUT, "classified.json"), "w"), ensure_ascii=False, indent=1)
print("=== CLASSIFIED ===")
for b in ["성장 지속", "저평가 회복", "버팀목", "과열", "해당 없음", "시장 전체", "자료 부족"]:
    grp = sorted([r for r in res if r["bucket"] == b], key=lambda r: -(r.get("ratio") or 0))
    print(f"## {b} ({len(grp)})")
    for r in grp:
        print(f"ROW|{b}|{r['name']}|n={r['n']}|pe={r['pe'] and round(r['pe'],1)}|avg={r['avg']}|yrs={r['yrs']}|g={r['g'] and round(r['g']*100,1)}")
