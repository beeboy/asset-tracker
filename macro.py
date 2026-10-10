"""거시 자료: 미 10년물 금리와 S&P500 이익(EPS)의 관계 (실러 데이터).

  python macro.py                # 실러 ie_data.xls 를 받아 data/macro/rates_earnings.json 을 만든다
  python macro.py --xls 파일.xls  # 받은 파일로 계산만 (오프라인 확인용)

출처: Robert J. Shiller, shillerdata.com (Irrational Exuberance 데이터). 화면에 출처를 표시한다.
GitHub Actions 가 한 달에 한 번 실행한다. 받기에 실패하면 지난 파일을 그대로 둔다.
필요한 라이브러리: xlrd (옛 .xls 형식 읽기).
"""
from __future__ import annotations

import json
import math
import re
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "data" / "macro" / "rates_earnings.json"
TNX = ROOT / "data" / "prices" / "_TNX.json"
PAGE = "https://shillerdata.com/"
FALLBACK_XLS = "https://img1.wsimg.com/blobby/go/e5e77e0b-59d1-44d9-ab25-4763ac982e53/downloads/ie_data.xls"
UA = {"User-Agent": "Mozilla/5.0 (naeilo macro collector)"}
START = 1960          # 통계 계산 시작 연도
LAGS = list(range(0, 37, 3))
MEALS = 1000          # 금리→이익 관계식 앙상블 ("식단") 개수
MEAL_STARTS = (1960, 1970, 1980, 1990, 2000)
MEAL_BLOCK = 24       # 재표본 묶음 (개월)
HORIZONS = (12, 24, 36)
BAND = 0.5            # 금리 1년 변화 구간 경계 (%p)


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


def xls_url() -> str:
    """실러 페이지에서 ie_data.xls 링크를 찾는다 (주소가 바뀌어도 따라가도록)."""
    try:
        html = fetch(PAGE).decode("utf-8", "replace")
        m = re.search(r"""(https?:)?//[^"'\s>]+ie_data\.xls[^"'\s>]*""", html)
        if m:
            u = m.group(0)
            return "https:" + u if u.startswith("//") else u
    except Exception as e:  # noqa: BLE001
        print("실러 페이지 읽기 실패:", e)
    return FALLBACK_XLS


def shiller_month(v) -> str | None:
    """실러 날짜 2023.01 → 2023-01, 2023.1 → 2023-10."""
    try:
        v = float(v)
    except (TypeError, ValueError):
        return None
    y = int(v)
    m = round((v - y) * 100)
    return f"{y:04d}-{m:02d}" if 1 <= m <= 12 and y > 1800 else None


def num(v) -> float | None:
    try:
        v = float(v)
    except (TypeError, ValueError):
        return None
    return v if v > 0 and math.isfinite(v) else None


def parse(path_or_bytes) -> list[dict]:
    import xlrd
    wb = xlrd.open_workbook(file_contents=path_or_bytes) if isinstance(path_or_bytes, bytes) \
        else xlrd.open_workbook(str(path_or_bytes))
    sh = wb.sheet_by_name("Data")
    # 열 위치: A 날짜, G 10년물 금리(GS10), K 실질 이익. 머리글이 바뀌었는지 확인한다.
    head = " ".join(str(sh.cell_value(r, c)) for r in range(min(8, sh.nrows)) for c in (6, 10))
    if ("GS10" not in head and "Interest" not in head) or "Earn" not in head:
        raise ValueError("실러 파일 형식이 바뀜: " + head[:200])
    rows = []
    for r in range(sh.nrows):
        d = shiller_month(sh.cell_value(r, 0))
        if not d:
            continue
        rows.append({"d": d, "r": num(sh.cell_value(r, 6)), "e": num(sh.cell_value(r, 10))})
    return rows


def corr(a: list[float], b: list[float]) -> float | None:
    n = len(a)
    if n < 24:
        return None
    ma, mb = sum(a) / n, sum(b) / n
    sa = math.sqrt(sum((x - ma) ** 2 for x in a))
    sb = math.sqrt(sum((y - mb) ** 2 for y in b))
    return round(sum((x - ma) * (y - mb) for x, y in zip(a, b)) / (sa * sb), 3) if sa and sb else None


def stats(rows: list[dict]) -> dict:
    rows = [x for x in rows if int(x["d"][:4]) >= START - 1]
    # 연속 월 시계열에서 1년 변화: 금리 %p, 실질 이익 로그 %
    dr, g = [], []
    for i, x in enumerate(rows):
        p = rows[i - 12] if i >= 12 else None
        dr.append(x["r"] - p["r"] if p and x["r"] and p["r"] else None)
        g.append(100 * math.log(x["e"] / p["e"]) if p and x["e"] and p["e"] else None)
    keep = [i for i, x in enumerate(rows) if int(x["d"][:4]) >= START]
    keep_set = set(keep)
    lag = {}
    for k in LAGS:
        pairs = [(dr[i], g[i + k]) for i in keep if i + k < len(rows) and dr[i] is not None and g[i + k] is not None]
        lag[str(k)] = corr([p[0] for p in pairs], [p[1] for p in pairs])
    lv = [(x["r"], math.log(x["e"])) for i, x in enumerate(rows) if i in keep_set and x["r"] and x["e"]]
    level = corr([p[0] for p in lv], [p[1] for p in lv])
    bands = []
    for key, lab, ok in (("up", f"금리 +{BAND}%p 이상", lambda v: v >= BAND),
                         ("flat", "금리 보합", lambda v: abs(v) < BAND),
                         ("down", f"금리 −{BAND}%p 이하", lambda v: v <= -BAND)):
        fut = sorted(g[i + 24] for i in keep if i + 24 < len(rows) and dr[i] is not None
                     and g[i + 24] is not None and ok(dr[i]))
        if fut:
            bands.append({"key": key, "label": lab, "n": len(fut),
                          "p_drop": round(100 * sum(v < 0 for v in fut) / len(fut)),
                          "median_growth": round(fut[len(fut) // 2], 1)})
    last_e = next((x["d"] for x in reversed(rows) if x["e"]), None)
    return {"level_corr": level, "lag_corr": lag, "bands": bands, "eps_last": last_e,
            "series": [[x["d"], x["r"] and round(x["r"], 2), x["e"] and round(x["e"], 2)]
                       for i, x in enumerate(rows) if i in keep_set]}


def ols(X: list[list[float]], y: list[float]) -> list[float]:
    k = len(X[0])
    A = [[sum(x[i] * x[j] for x in X) for j in range(k)] for i in range(k)]
    b = [sum(x[i] * v for x, v in zip(X, y)) for i in range(k)]
    for i in range(k):
        for j in range(i + 1, k):
            f = A[j][i] / A[i][i]; A[j] = [a - f * c for a, c in zip(A[j], A[i])]; b[j] -= f * b[i]
    w = [0.0] * k
    for i in reversed(range(k)):
        w[i] = (b[i] - sum(A[i][j] * w[j] for j in range(i + 1, k))) / A[i][i]
    return w


def meals(series: list, dr_now: float | None) -> dict:
    """식단 앙상블: 관계식 EPS성장(t+h) = a + b·Δ금리(t) + c·EPS성장(t) 을 시작 연도·24개월 묶음 재표본을 바꿔 1000벌 맞춘다.
    식단마다 확정적인 답 하나 (무작위 경로 없음). b 는 화면이 오늘 금리 변화를 곱해 금리 몫을 다시 계산하도록 남긴다."""
    import random
    n = len(series); r = [x[1] for x in series]; e = [x[2] for x in series]
    dr = [r[i] - r[i - 12] if i >= 12 and r[i] and r[i - 12] else None for i in range(n)]
    g = [100 * math.log(e[i] / e[i - 12]) if i >= 12 and e[i] and e[i - 12] else None for i in range(n)]
    last = max(i for i in range(n) if g[i] is not None); g_now = g[last]
    rnd = random.Random(20261010); out = []
    for _ in range(MEALS):
        y0 = rnd.choice(MEAL_STARTS); lo = next(i for i in range(n) if int(series[i][0][:4]) >= y0); idx = []
        while len(idx) < n - lo:
            s0 = rnd.randrange(lo, n - MEAL_BLOCK); idx += range(s0, s0 + MEAL_BLOCK)
        m = []
        for h in HORIZONS:
            rows = [(dr[i], g[i], g[i + h]) for i in idx if i + h < n and None not in (dr[i], g[i], g[i + h])]
            m.append([round(v, 4) for v in ols([[1, x[0], x[1]] for x in rows], [x[2] for x in rows])])
        out.append(m)
    def band(vals):
        v = sorted(vals); return {"p10": round(v[len(v) // 10], 1), "p50": round(v[len(v) // 2], 1), "p90": round(v[9 * len(v) // 10], 1),
                                  "p_neg": round(100 * sum(x < 0 for x in v) / len(v))}
    fc = None
    if dr_now is not None:
        fc = {"dr_now": dr_now, "g_now": round(g_now, 1), "g_now_month": series[last][0],
              "total": [band([m[k][0] + m[k][1] * dr_now + m[k][2] * g_now for m in out]) for k in range(len(HORIZONS))],
              "rate_part": [band([m[k][1] * dr_now for m in out]) for k in range(len(HORIZONS))]}
    return {"horizons": list(HORIZONS), "b": [[m[k][1] for k in range(len(HORIZONS))] for m in out], "forecast": fc}


def tnx_now() -> dict | None:
    """저장소의 ^TNX 일별 시세로 지금 1년 금리 변화를 구한다 (화면이 매일 다시 계산해도 된다)."""
    try:
        d = json.loads(TNX.read_text(encoding="utf-8"))
        s = [(t, c) for t, c in zip(d["dates"], d["close"]) if c]
        t1, c1 = s[-1]
        y0 = str(int(t1[:4]) - 1) + t1[4:]
        c0 = next(c for t, c in reversed(s) if t <= y0)
        return {"date": t1, "rate": round(c1, 2), "change_1y": round(c1 - c0, 2)}
    except Exception:  # noqa: BLE001
        return None


def main(argv: list[str]) -> int:
    if "--xls" in argv:
        src = Path(argv[argv.index("--xls") + 1]); data = src.read_bytes(); url = str(src)
    else:
        url = xls_url()
        try:
            data = fetch(url)
        except Exception as e:  # noqa: BLE001
            print("실러 파일 받기 실패, 지난 결과 유지:", url, e)
            return 0
    try:
        st = stats(parse(data))
    except Exception as e:  # noqa: BLE001
        print("실러 파일 해석 실패, 지난 결과 유지:", e)
        return 0
    out = {"source": "Robert J. Shiller, shillerdata.com", "source_url": url,
           "updated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
           "note": "미 10년물 금리(GS10)와 S&P500 실질 EPS(12개월). 1년 변화끼리의 상관, k개월 뒤 이익 반응.",
           "now": tnx_now(), **st}
    out["meals"] = meals(st["series"], (out["now"] or {}).get("change_1y"))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("저장:", OUT, "이익 마지막 달", st["eps_last"], "시차상관", st["lag_corr"], st["bands"])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
