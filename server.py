"""내 자산 관찰·전망 도구 - 로컬 서버.

파이썬 3.8 이상 표준 라이브러리만 사용한다 (pip 설치 필요 없음).
  python server.py            # http://127.0.0.1:8765 를 브라우저로 연다
  python server.py --port 9000 --no-browser
  python server.py --collect  # 화면 없이 수집만 (GitHub Actions 용, data/tickers.json 의 종목)

하는 일
  1) web/ 폴더의 화면(HTML/JS)을 내 PC 안에서만 보여 준다 (127.0.0.1, 외부 접속 불가).
  2) 브라우저가 직접 부를 수 없는 Yahoo Finance 시세를 대신 받아 data/prices/ 에 저장한다.
  3) 화면에서 입력한 종목·목표·사건 목록을 data/state.json 에 저장한다.
계산(칼만 필터, 몬테카를로 전망)은 모두 브라우저에서 한다.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from concurrent.futures import ThreadPoolExecutor, wait
from datetime import date, datetime, timedelta, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WEB = ROOT / "web"
DATA = ROOT / "data"
PRICES = DATA / "prices"
STATE = DATA / "state.json"
STATE_DEFAULT = DATA / "state.default.json"
TICKERS = DATA / "tickers.json"
QUOTES = DATA / "quotes.json"
KR_STOCKS = DATA / "kr_stocks.json"  # 매일 모으는 국내 개별 종목 (tools/gen_stock_catalog.py 가 만든다)
KR_DAILY = DATA / "kr_daily.json"  # 국내 개별 종목 날짜별 종가 (모으기 시작한 날부터 쌓는다, 최근 1년치)
MAR = DATA / "mar.json"  # 매매기준율 (서울외국환중개 시장평균환율)
SMBS = "http://www.smbs.biz/ExRate/"
BACKUP = DATA / "backup"
YAHOO = "https://query1.finance.yahoo.com/v8/finance/chart/"
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Accept": "application/json"}
FX_SYMBOLS = {"USD": "KRW=X"}  # 통화 -> 원화 환율 심볼
# 외부 요인 분석용 대리 지표: 시장(S&P500), 미국 10년 금리, WTI 유가, 금, 원자재 지수
FACTOR_SYMBOLS = ["SPY", "^TNX", "CL=F", "GC=F", "DBC"]
# 앱 계획·구성 비교의 묶음 대표 ETF (성장 지속 = QQQ, 지수 = SPY, 현금 = SGOV 는 이미 받는다): 저평가 회복 = VTV, 버팀목 = USMV
BASKET_SYMBOLS = ["QQQ", "SGOV", "VTV", "USMV"]
LOCK = threading.Lock()


# ---------------------------------------------------------------- 토스증권 시세 (개발자 전용)
# 키가 있는 PC 에서만 켜진다: toss_keys.json {"client_id","client_secret"} (.gitignore, 저장소에 안 올림).
# 아이폰·naeilo.com 은 Tailscale 주소(https://…ts.net)로 이 서버의 /api/toss/prices 를 부른다 (CORS 허용: TOSS_ORIGINS).
# 토스는 허용 IP 에서만 받으므로 그 IP 를 등록한 PC 의 이 서버에서만 부른다. 키·토큰은 브라우저로 보내지 않는다.
TOSS = "https://openapi.tossinvest.com"
TOSS_KEYS = ROOT / "toss_keys.json"
TOSS_ORIGINS = ("https://naeilo.com", "https://www.naeilo.com")


def toss_origin_ok(o: str) -> bool:
    # naeilo.com 과 Tailscale 주소(내 기기끼리만 닿음)만 허용
    return o in TOSS_ORIGINS or (o.startswith("https://") and o.endswith(".ts.net"))


_toss = {"token": None, "exp": 0.0}
TOSS_LOCK = threading.Lock()


def toss_keys() -> tuple[str, str] | None:
    k = read_json(TOSS_KEYS, {})
    i, s = str(k.get("client_id", "")).strip(), str(k.get("client_secret", "")).strip()
    return (i, s) if i and s else None


def toss_token(keys: tuple[str, str], renew: bool = False) -> str:
    # 클라이언트당 토큰은 1개 (새로 받으면 예전 것은 바로 무효) → 한 번 받아 만료 전까지 같이 쓴다
    with TOSS_LOCK:
        if not renew and _toss["token"] and time.time() < _toss["exp"] - 60:
            return _toss["token"]
        body = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": keys[0], "client_secret": keys[1]}).encode()
        req = urllib.request.Request(TOSS + "/oauth2/token", data=body, headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=4) as r:  # 앱은 8초까지만 기다린다
            j = json.loads(r.read().decode("utf-8"))
        _toss["token"], _toss["exp"] = j["access_token"], time.time() + float(j.get("expires_in") or 3600)
        return _toss["token"]


def toss_get(keys: tuple[str, str], path: str) -> dict:
    for renew in (False, True):
        req = urllib.request.Request(TOSS + path, headers={"Authorization": "Bearer " + toss_token(keys, renew), "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=3) as r:  # 앱·웹은 3초 넘으면 Yahoo 로 넘어간다
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code != 401 or renew:
                raise
    return {}


_toss_miss: dict[str, float] = {}  # 토스에 없는 종목 코드 → 다시 물어볼 시각 (하루 동안 안 묻는다)


def toss_code(sym: str) -> str | None:
    """Yahoo 기호 → 토스 종목 코드. 토스에 없는 시장(지수·환율·코인·일본 등)은 None → 그 종목은 Yahoo 값 그대로
    - 국내: 005930.KS·0101N0.KQ → 005930·0101N0 (6자리)
    - 미국: AAPL → AAPL, 클래스 주식 BRK-B → BRK.B (토스는 '.' 로 쓴다고 보고 맞춤. 없으면 하루 동안 안 묻는다)"""
    u = sym.strip().upper()
    if u.endswith((".KS", ".KQ")):
        c = u[:-3]
        return c if len(c) == 6 and c.isalnum() else None
    m = re.fullmatch(r"([A-Z]{1,5})(?:-([A-Z]))?", u)  # ^GSPC·KRW=X·GC=F·BTC-USD·7203.T 는 여기서 걸러진다
    return (m.group(1) + ("." + m.group(2) if m.group(2) else "")) if m else None


def toss_prices(symbols: list[str]) -> dict:
    """국내(.KS·.KQ)·미국 종목의 지금 가격. 사이트 quotes 와 같은 모양으로, 통화는 토스 응답 그대로 (KRW·USD)"""
    keys = toss_keys()
    if not keys:
        return {"enabled": False, "quotes": {}}
    now = time.time()
    codes: dict[str, list[str]] = {}  # 토스 코드 → Yahoo 기호들
    for s in symbols:
        c = toss_code(s)
        if c and _toss_miss.get(c, 0) < now:
            codes.setdefault(c, []).append(s)
    out = {}

    def ask(cs: list[str]) -> None:
        j = toss_get(keys, "/api/v1/prices?symbols=" + urllib.parse.quote(",".join(cs)))
        for q in j.get("result") or []:
            v = float(q.get("lastPrice") or 0)
            if v <= 0:
                continue
            try:
                t = int(datetime.fromisoformat(str(q.get("timestamp"))).timestamp())
            except ValueError:
                t = int(now)
            ccy = q.get("currency") or ("KRW" if str(q.get("symbol", "")).isdigit() else "USD")
            for sym in codes.get(str(q.get("symbol")).upper(), []):
                out[sym] = {"symbol": sym, "last": v, "last_time": t, "last_session": "toss", "currency": ccy}

    def one(c: str) -> None:
        try:
            ask([c])
        except urllib.error.HTTPError as e:
            if e.code != 404:
                raise
            _toss_miss[c] = now + 86400

    err = None
    try:
        cs = list(codes)
        for i in range(0, len(cs), 200):  # 한 번에 200개까지
            part = cs[i:i + 200]
            try:
                ask(part)
            except urllib.error.HTTPError as e:
                if e.code != 404:
                    raise
                # 묶음에 없는 종목이 끼면 하나씩 다시 (없는 종목은 하루 동안 빼고 Yahoo).
                # 하나씩 차례로 물으면 종목이 많을 때 앱의 8초를 넘긴다 → 동시에 묻고 4초까지만 기다린다
                pool = ThreadPoolExecutor(max_workers=8)
                fs = [pool.submit(one, c) for c in part[:30]]
                done, _ = wait(fs, timeout=max(0.5, now + 4 - time.time()))
                pool.shutdown(wait=False, cancel_futures=True)
                for f in done:
                    if f.exception():
                        raise f.exception()
                if len(done) < len(fs):
                    err = "토스 응답이 느려 일부만 받음"
    except (urllib.error.URLError, OSError, ValueError, KeyError) as e:
        code = getattr(e, "code", None)
        msg = "허용 IP 가 아니에요" if code == 403 else "키를 확인하세요" if code in (400, 401) else str(e)
        return {"enabled": True, "quotes": out, "error": msg}
    if err:
        return {"enabled": True, "quotes": out, "error": err}
    return {"enabled": True, "quotes": out}


# ---------------------------------------------------------------- 파일
def read_json(p: Path, default):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return default


def write_json(p: Path, obj, compact: bool = False):
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    # 시세 파일은 빈칸 없이 (받는 양이 줄어든다). 사람이 열어 보는 파일은 들여쓰기
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":")) if compact else json.dumps(obj, ensure_ascii=False, indent=1)
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, p)


def price_file(sym: str) -> Path:
    safe = "".join(c if c.isalnum() or c in "-_." else "_" for c in sym.upper())
    return PRICES / f"{safe}.json"


# ---------------------------------------------------------------- Yahoo
def yahoo(sym: str, params: dict) -> dict:
    url = YAHOO + urllib.parse.quote(sym) + "?" + urllib.parse.urlencode(params)
    last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=20) as r:
                d = json.load(r)
            res = (d.get("chart") or {}).get("result")
            if not res:
                raise ValueError("시세 없음")
            return res[0]
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise ValueError("티커를 찾지 못함(상장폐지 또는 오타)") from None
            last = e
        except (urllib.error.URLError, TimeoutError, ValueError) as e:
            last = e
        if attempt < 2:  # 마지막 실패 뒤에는 기다리지 않는다
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Yahoo 연결 실패: {last}")


def day_of(ts: int, gmtoffset: int) -> str:
    return datetime.fromtimestamp(ts + gmtoffset, tz=timezone.utc).date().isoformat()


def update_history(sym: str, years: float) -> tuple[dict, str]:
    """일봉을 받아 캐시에 합친다. 이미 있으면 마지막 날짜 근처부터만 받는다."""
    f = price_file(sym)
    cache = read_json(f, None)
    now = int(time.time())
    want_start = date.today() - timedelta(days=int(365.25 * years) + 7)
    if cache and cache.get("dates") and cache["dates"][0] <= want_start.isoformat():
        p1 = int(datetime.fromisoformat(cache["dates"][-1]).replace(tzinfo=timezone.utc).timestamp()) - 10 * 86400
    else:
        p1 = int(datetime(want_start.year, want_start.month, want_start.day, tzinfo=timezone.utc).timestamp())
        cache = cache if cache and cache.get("dates") else None
    r = yahoo(sym, {"period1": p1, "period2": now + 86400, "interval": "1d",
                    "includeAdjustedClose": "true", "events": "div,split"})
    meta = r.get("meta", {})
    off = int(meta.get("gmtoffset") or 0)
    ts = r.get("timestamp") or []
    q = (r.get("indicators", {}).get("quote") or [{}])[0]
    adj = (r.get("indicators", {}).get("adjclose") or [{}])[0].get("adjclose") or q.get("close") or []
    rows = {}
    if cache:
        for d, c, a in zip(cache["dates"], cache["close"], cache["adj"]):
            rows[d] = (c, a)
    new = 0
    for t, c, a in zip(ts, q.get("close") or [], adj):
        if c is None:
            continue
        d = day_of(t, off)
        if d not in rows:
            new += 1
        rows[d] = (round(float(c), 6), round(float(a if a is not None else c), 6))
    # 분할·배당으로 과거 값이 바뀌었으면 겹친 첫날의 비율로 그 이전 캐시 값을 맞춘다
    # (Yahoo 종가는 분할 반영, 수정종가는 분할+배당 반영)
    if cache and ts:
        overlap = [day_of(t, off) for t in ts if day_of(t, off) in cache["dates"]]
        if overlap:
            d0 = overlap[0]
            i = cache["dates"].index(d0)
            kc = rows[d0][0] / cache["close"][i] if cache["close"][i] else 1.0
            ka = rows[d0][1] / cache["adj"][i] if cache["adj"][i] else 1.0
            if abs(kc - 1) > 1e-4 or abs(ka - 1) > 1e-4:
                for d in cache["dates"][:i]:
                    c, a = rows[d]
                    rows[d] = (round(c * kc, 6), round(a * ka, 6))
    dates = sorted(rows)
    out = {"symbol": sym.upper(), "currency": meta.get("currency") or (cache or {}).get("currency") or "USD",
           "name": meta.get("longName") or meta.get("shortName") or (cache or {}).get("name") or sym.upper(),
           "updated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
           "dates": dates, "close": [rows[d][0] for d in dates], "adj": [rows[d][1] for d in dates]}
    # 일봉이 그대로면 파일을 다시 쓰지 않는다 (판 번호가 그대로라 브라우저가 다시 받지 않음)
    if not cache or (cache.get("dates"), cache.get("close"), cache.get("adj")) != (out["dates"], out["close"], out["adj"]) or cache.get("name") != out["name"] or "\n" in f.read_text(encoding="utf-8")[:40]:
        write_json(f, out, compact=True)
    else:
        out["updated"] = cache.get("updated", out["updated"])
    return out, f"{sym}: 일봉 {len(dates)}일 (새 {new}일, {dates[0] if dates else '-'} ~ {dates[-1] if dates else '-'})"


def get_quote(sym: str) -> dict:
    """최근 1분봉(프리·애프터 포함)으로 세션별 마지막 가격을 만든다."""
    r = yahoo(sym, {"range": "1d", "interval": "1m", "includePrePost": "true"})
    m = r.get("meta", {})
    ts = r.get("timestamp") or []
    cl = ((r.get("indicators", {}).get("quote") or [{}])[0].get("close")) or []
    per = m.get("currentTradingPeriod") or {}
    out = {"symbol": sym.upper(), "currency": m.get("currency"), "name": m.get("longName") or m.get("shortName"),
           "regular": m.get("regularMarketPrice"), "regular_time": m.get("regularMarketTime"),
           "prev_close": m.get("chartPreviousClose") or m.get("previousClose"),
           "pre": None, "post": None, "last": m.get("regularMarketPrice"), "last_time": m.get("regularMarketTime"),
           "last_session": "regular", "fetched": int(time.time())}
    for t, c in zip(ts, cl):
        if c is None:
            continue
        for s in ("pre", "post"):
            p = per.get(s) or {}
            if p.get("start", 0) <= t < p.get("end", 0):
                out[s] = round(float(c), 4)
                out["last"], out["last_time"], out["last_session"] = round(float(c), 4), t, s
        reg = per.get("regular") or {}
        if reg.get("start", 0) <= t < reg.get("end", 0):
            out["last"], out["last_time"], out["last_session"] = round(float(c), 4), t, "regular"
    return out


# ---------------------------------------------------------------- 매매기준율
def fetch_mar() -> dict | None:
    """서울외국환중개가 고시하는 미국 달러 매매기준율(MAR). 못 받으면 None (화면은 기존 환율로 대체)."""
    import re
    end = datetime.now(timezone(timedelta(hours=9))).date()  # 한국 날짜 (GitHub 러너는 UTC라 아침 9시 전엔 어제가 됨)
    tries = [
        SMBS + f"StdExRate_xml.jsp?arr_value=USD_{(end - timedelta(days=10)).isoformat()}_{end.isoformat()}",
        SMBS + "StdExRate.jsp",
    ]
    for url in tries:
        try:
            req = urllib.request.Request(url, headers={**UA, "Accept": "*/*", "Referer": SMBS + "StdExRate.jsp"})
            with urllib.request.urlopen(req, timeout=20) as r:
                raw = r.read()
            txt = raw.decode("euc-kr", "ignore") if b"euc-kr" in raw[:400].lower() else raw.decode("utf-8", "ignore")
            if "xml" in url:
                sets = re.findall(r"label=['\"]([^'\"]+)['\"][^>]*?value=['\"]([\d,.]+)['\"]", txt)
                if sets:
                    lab, val = sets[-1]
                    m = re.findall(r"\d+", lab)
                    d = end.isoformat()
                    if len(m) >= 3:
                        y = int(m[0]) + (2000 if int(m[0]) < 100 else 0)
                        d = f"{y:04d}-{int(m[1]):02d}-{int(m[2]):02d}"
                    rate = float(val.replace(",", ""))
                    if 500 < rate < 5000:
                        return {"USD": {"rate": rate, "date": d, "src": "서울외국환중개", "fetched": int(time.time())}}
            else:
                m = re.search(r"USD.{0,400}?(\d,\d{3}\.\d{1,2})", txt, re.S)
                if m:
                    rate = float(m.group(1).replace(",", ""))
                    if 500 < rate < 5000:
                        return {"USD": {"rate": rate, "date": end.isoformat(), "src": "서울외국환중개", "fetched": int(time.time())}}
        except Exception:  # noqa: BLE001 - 다음 방법 시도
            continue
    return None


def update_mar(log: list | None = None):
    m = fetch_mar()
    if m:
        write_json(MAR, m)
    if log is not None:
        log.append({"ok": bool(m), "msg": f"매매기준율: {m['USD']['rate']}원 ({m['USD']['date']})" if m else "매매기준율: 받지 못함 (기존 환율로 대체)"})


def collect(symbols: list[str], years: float, quotes_only: bool) -> dict:
    log, quotes = [], read_json(QUOTES, {})
    t0 = time.time()
    for sym in symbols:
        sym = sym.strip().upper()
        if not sym:
            continue
        try:
            if not quotes_only:
                _, msg = update_history(sym, years)
                log.append({"ok": True, "msg": msg})
            q = get_quote(sym)
            quotes[sym] = q
            sess = {"regular": "정규장", "pre": "프리마켓", "post": "애프터마켓"}[q["last_session"]]
            when = datetime.fromtimestamp(q["last_time"] or time.time()).strftime("%m-%d %H:%M")
            log.append({"ok": True, "msg": f"{sym}: 현재가 {q['last']} {q['currency'] or ''} ({sess}, {when})"})
        except Exception as e:  # noqa: BLE001 - 종목 하나 실패해도 나머지는 계속
            log.append({"ok": False, "msg": f"{sym}: {e}"})
    write_json(QUOTES, quotes)
    if any(s.strip().upper() == "KRW=X" for s in symbols):
        update_mar(log)
    write_index()
    log.append({"ok": True, "msg": f"완료 ({time.time() - t0:.1f}초)"})
    return {"log": log}


def collect_kr(quotes: dict, log: list):
    """국내 개별 종목: 지금 가격은 quotes.json 에 (앱이 Yahoo 기호로 읽는다), 그날 종가는 kr_daily.json 에 하루 한 칸씩 쌓는다.
    지난 기록은 한꺼번에 받지 않고 모으기 시작한 날부터 매일 쌓는다 (쌓이기 전에는 앱이 "기록 모으는 중")."""
    stocks = (read_json(KR_STOCKS, {}) or {}).get("stocks") or []
    if not stocks:
        return
    daily = read_json(KR_DAILY, None) or {"s": {}}
    ok = 0
    for st in stocks:
        y = st["yahoo"]
        try:
            q = get_quote(y)
        except Exception as e:  # noqa: BLE001 - 종목 하나 실패해도 나머지는 계속
            log.append({"ok": False, "msg": f"국내 {y}: {e}"})
            continue
        quotes[y.upper()] = q
        ok += 1
        v, t = q.get("regular") or q.get("last"), q.get("regular_time") or q.get("last_time")
        if not v or not t:
            continue
        d = datetime.fromtimestamp(int(t), tz=timezone(timedelta(hours=9))).date().isoformat()   # 한국 날짜
        r = daily["s"].setdefault(st["id"], {"d": [], "c": []})
        if r["d"] and r["d"][-1] == d:
            r["c"][-1] = round(float(v), 2)   # 같은 날은 마지막 값 (장이 끝나면 종가)
        elif not r["d"] or r["d"][-1] < d:
            r["d"].append(d); r["c"].append(round(float(v), 2))
        r["d"], r["c"] = r["d"][-260:], r["c"][-260:]   # 1년 (거래일 약 250일)
    daily["updated"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    write_json(KR_DAILY, daily, compact=True)
    log.append({"ok": ok > 0, "msg": f"국내 개별 종목: {ok}/{len(stocks)}개 받음"})


def write_index():
    """정적 사이트(GitHub Pages)가 읽을 시세 파일 목록."""
    files, ver = {}, {}
    for f in sorted(PRICES.glob("*.json")):
        d = read_json(f, None)
        if d:
            files[d["symbol"]] = f.name
            ver[d["symbol"]] = d.get("updated") or ""  # 종목별 판: 그 종목 일봉이 바뀔 때만 바뀐다
    write_json(DATA / "index.json", {"updated": datetime.now(timezone.utc).isoformat(timespec="seconds"), "prices": files, "v": ver})


def all_data() -> dict:
    prices = {}
    for f in sorted(PRICES.glob("*.json")):
        d = read_json(f, None)
        if d:
            prices[d["symbol"]] = d
    return {"state": read_json(STATE, None) or read_json(STATE_DEFAULT, {}), "prices": prices, "quotes": read_json(QUOTES, {}), "mar": read_json(MAR, {}),
            "config": read_json(STATE_DEFAULT.parent / "config.json", {}), "server_time": datetime.now().isoformat(timespec="seconds")}


def save_state(state: dict):
    if STATE.exists():  # 하루 한 번 백업 (최근 30개 유지)
        BACKUP.mkdir(parents=True, exist_ok=True)
        b = BACKUP / f"state-{date.today().isoformat()}.json"
        if not b.exists():
            shutil.copy(STATE, b)
        for old in sorted(BACKUP.glob("state-*.json"))[:-30]:
            old.unlink()
    write_json(STATE, state)


# ---------------------------------------------------------------- HTTP
class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(WEB), **kw)

    def log_message(self, fmt, *args):  # 조용히
        pass

    cors = None

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        if self.cors:
            self.send_header("Access-Control-Allow-Origin", self.cors)
            self.send_header("Vary", "Origin")
        super().end_headers()

    def send_json(self, obj, code=200):
        b = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n).decode("utf-8") or "{}")

    def do_GET(self):
        if self.path.startswith("/api/data"):
            with LOCK:
                return self.send_json(all_data())
        if self.path.startswith("/api/macro"):  # 금리→이익 관계 (macro.py 가 매달 만든다)
            return self.send_json(read_json(DATA / "macro" / "rates_earnings.json", {}))
        if self.path.startswith("/api/blog"):
            return self.send_json(read_json(DATA / "blog.json", {"cats": [], "posts": []}))
        if self.path.startswith("/api/toss/prices"):  # 토스증권 국내·미국 시세 (키 있는 개발자 PC 만)
            o = self.headers.get("Origin") or ""
            self.cors = o if toss_origin_ok(o) else None
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            return self.send_json(toss_prices([x for x in (q.get("symbols") or [""])[0].split(",") if x]))
        return super().do_GET()

    def do_OPTIONS(self):  # 토스 시세 사전 요청 (CORS)
        o = self.headers.get("Origin") or ""
        if not (self.path.startswith("/api/toss/") and toss_origin_ok(o)):
            return self.send_json({"error": "forbidden"}, 403)
        self.cors = o
        self.send_response(204)
        self.send_header("Access-Control-Allow-Methods", "GET")
        self.send_header("Access-Control-Max-Age", "86400")
        self.end_headers()

    def do_POST(self):
        # 같은 PC 의 다른 웹사이트가 몰래 부르는 것을 막는다
        origin = self.headers.get("Origin")
        host = self.headers.get("Host", "")
        if origin and urllib.parse.urlparse(origin).netloc != host:
            return self.send_json({"error": "forbidden"}, 403)
        try:
            b = self.body()
            if self.path == "/api/state":
                with LOCK:
                    save_state(b)
                return self.send_json({"ok": True})
            if self.path == "/api/collect":
                with LOCK:
                    return self.send_json(collect(b.get("symbols", []), float(b.get("years", 3)),
                                                  bool(b.get("quotes_only"))))
            if self.path == "/api/blog":
                with LOCK:
                    write_json(DATA / "blog.json", {"cats": b.get("cats") or [], "posts": b.get("posts") or []})
                return self.send_json({"ok": True})
            if self.path == "/api/delete_prices":
                with LOCK:
                    f = price_file(b["symbol"])
                    if f.exists():
                        f.unlink()
                    q = read_json(QUOTES, {})
                    q.pop(b["symbol"].upper(), None)
                    write_json(QUOTES, q)
                return self.send_json({"ok": True})
        except Exception as e:  # noqa: BLE001
            return self.send_json({"error": str(e)}, 500)
        self.send_json({"error": "not found"}, 404)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--no-browser", action="store_true")
    ap.add_argument("--collect", action="store_true", help="수집만 하고 끝냄 (data/tickers.json)")
    ap.add_argument("--add", default="", help="--collect 와 함께: tickers.json 에 더할 티커 (쉼표 구분)")
    ap.add_argument("--quotes-only", action="store_true")
    a = ap.parse_args()
    PRICES.mkdir(parents=True, exist_ok=True)
    if a.collect:
        return cli_collect(a.add, a.quotes_only)
    srv = None
    for port in range(a.port, a.port + 20):
        try:
            srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            continue
    if srv is None:
        sys.exit("사용할 수 있는 포트가 없습니다.")
    url = f"http://127.0.0.1:{srv.server_address[1]}/"
    print(f"자산 도구 실행 중: {url}")
    print("이 창을 닫으면 종료됩니다. (Ctrl+C)")
    if not a.no_browser:
        threading.Timer(0.8, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("종료")


def cli_collect(add: str, quotes_only: bool):
    cfg = read_json(TICKERS, {"tickers": [], "history_years": 3})
    tickers = [t.strip().upper() for t in cfg.get("tickers", []) if t.strip()]
    for t in add.replace(" ", ",").split(","):
        t = t.strip().upper()
        if t and t not in tickers:
            tickers.append(t)
    cfg["tickers"] = tickers
    write_json(TICKERS, cfg)
    syms = list(tickers)
    for t in tickers:  # 종목 통화에 맞는 환율도 받는다
        ccy = (read_json(price_file(t), {}) or {}).get("currency") or "USD"
        fx = FX_SYMBOLS.get(ccy) or (None if ccy == "KRW" else f"{ccy}KRW=X")
        if fx and fx not in syms:
            syms.append(fx)
    for s in ["KRW=X", *FACTOR_SYMBOLS, *BASKET_SYMBOLS]:
        if s not in syms:
            syms.append(s)
    r = collect(syms, float(cfg.get("history_years", 3)), quotes_only)
    bad = sum(not line["ok"] for line in r["log"])
    quotes, krlog = read_json(QUOTES, {}), []   # 국내 개별 종목 (Actions 수집만. 로컬 화면의 수집 단추는 건너뛴다)
    collect_kr(quotes, krlog)
    if krlog:
        write_json(QUOTES, quotes)
    for line in r["log"] + krlog:
        print(("  " if line["ok"] else "! ") + line["msg"])
    if bad == len(syms):
        sys.exit(1)


if __name__ == "__main__":
    main()
