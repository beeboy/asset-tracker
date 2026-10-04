"""내 자산 관찰·전망 도구 - 로컬 서버.

파이썬 3.8 이상 표준 라이브러리만 사용한다 (pip 설치 필요 없음).
  python server.py            # http://127.0.0.1:8765 를 브라우저로 연다
  python server.py --port 9000 --no-browser
  python server.py --collect  # 화면 없이 수집만 (GitHub Actions 용, data/tickers.json 의 종목)

하는 일
  1) web/ 폴더의 화면(HTML/JS)을 내 PC 안에서만 보여 준다 (127.0.0.1, 외부 접속 불가).
  2) 브라우저가 직접 부를 수 없는 Yahoo Finance 시세를 대신 받아 data/prices/ 에 저장한다.
  3) 화면에서 입력한 종목·목표·사건 목록을 data/state.json 에 저장한다.
계산(칼만 필터, 몬테카를로 전망)은 모두 브라우저에서 한다. 인공지능 호출은 전혀 없다.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
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
BACKUP = DATA / "backup"
YAHOO = "https://query1.finance.yahoo.com/v8/finance/chart/"
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Accept": "application/json"}
FX_SYMBOLS = {"USD": "KRW=X"}  # 통화 -> 원화 환율 심볼
LOCK = threading.Lock()


# ---------------------------------------------------------------- 파일
def read_json(p: Path, default):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return default


def write_json(p: Path, obj):
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(obj, ensure_ascii=False, indent=1), encoding="utf-8")
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
    write_json(f, out)
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
    write_index()
    log.append({"ok": True, "msg": f"완료 ({time.time() - t0:.1f}초)"})
    return {"log": log}


def write_index():
    """정적 사이트(GitHub Pages)가 읽을 시세 파일 목록."""
    files = {}
    for f in sorted(PRICES.glob("*.json")):
        d = read_json(f, None)
        if d:
            files[d["symbol"]] = f.name
    write_json(DATA / "index.json", {"updated": datetime.now(timezone.utc).isoformat(timespec="seconds"), "prices": files})


def all_data() -> dict:
    prices = {}
    for f in sorted(PRICES.glob("*.json")):
        d = read_json(f, None)
        if d:
            prices[d["symbol"]] = d
    return {"state": read_json(STATE, None) or read_json(STATE_DEFAULT, {}), "prices": prices, "quotes": read_json(QUOTES, {}),
            "server_time": datetime.now().isoformat(timespec="seconds")}


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

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
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
        return super().do_GET()

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
    if "KRW=X" not in syms:
        syms.append("KRW=X")
    r = collect(syms, float(cfg.get("history_years", 3)), quotes_only)
    bad = 0
    for line in r["log"]:
        print(("  " if line["ok"] else "! ") + line["msg"])
        bad += not line["ok"]
    if bad == len(syms):
        sys.exit(1)


if __name__ == "__main__":
    main()
