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


# ---------------------------------------------------------------- 모의투자 (개발자 전용, 앱 Mock/)
# 앱의 모의투자 모드가 부른다. 주문 경로는 없다: 시세·일봉·호가·장 일정만 읽는다. 공용 중계(proxy/toss_relay.py)에는 넣지 않는다.
# 토스 한도는 클라이언트 × 묶음 단위 초당 횟수 (공식 명세 2026-10: MARKET_DATA 15, MARKET_DATA_CHART 20, MARKET_INFO 따로).
# 응답 머리 X-RateLimit-Remaining·Reset 을 읽어 속도를 맞추고, 429 면 Retry-After 만큼 쉬고 다시 묻는다.

class TossGate:
    """묶음 하나의 초당 횟수 지키기. 처음엔 명세 값보다 조금 낮게, 응답 머리를 보면 그 값을 따른다"""

    def __init__(self, per_sec: float):
        self.lock = threading.Lock()
        self.rate = per_sec
        self.next = 0.0
        self.hold = 0.0  # 429 로 쉬라고 한 끝 시각

    def wait(self):
        with self.lock:
            now = time.time()
            t = max(now, self.next, self.hold)
            self.next = t + 1.0 / max(1.0, self.rate)
        if t > now:
            time.sleep(t - now)

    def seen(self, h):
        try:
            lim = float(h.get("X-RateLimit-Limit") or 0)
            rem = float(h.get("X-RateLimit-Remaining") if h.get("X-RateLimit-Remaining") is not None else -1)
            reset = float(h.get("X-RateLimit-Reset") or 0)
        except (TypeError, ValueError):
            return
        with self.lock:
            if lim > 0:
                self.rate = max(1.0, lim - 1)  # 한 칸 여유
            if rem == 0 and reset > 0:
                self.hold = max(self.hold, time.time() + reset)

    def told(self, secs: float):
        with self.lock:
            self.hold = max(self.hold, time.time() + secs)


TOSS_GATES = {"data": TossGate(13), "chart": TossGate(18), "info": TossGate(4)}


def toss_call(path: str, gate: str, timeout: float = 6) -> dict:
    """토큰을 붙여 GET. 401 이면 토큰을 한 번 새로, 429 면 Retry-After 만큼 쉬고 두 번까지 다시"""
    keys = toss_keys()
    if not keys:
        raise RuntimeError("서버에 토스 키 없음")
    g = TOSS_GATES[gate]
    renew = False
    for attempt in range(4):
        g.wait()
        req = urllib.request.Request(TOSS + path, headers={"Authorization": "Bearer " + toss_token(keys, renew), "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                g.seen(r.headers)
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            g.seen(e.headers or {})
            if e.code == 401 and not renew:
                renew = True
                continue
            if e.code == 429 and attempt < 3:
                try:
                    secs = float((e.headers or {}).get("Retry-After") or 1)
                except ValueError:
                    secs = 1.0
                g.told(secs)
                continue
            raise
    raise RuntimeError("토스 한도에 계속 걸림")


def mock_err(e: Exception) -> str:
    code = getattr(e, "code", None)
    if code == 403:
        return "허용 IP 가 아니에요"
    if code in (400, 401):
        return "키를 확인하세요"
    if code == 404:
        return "없는 종목"
    if code == 429:
        return "토스 한도"
    return str(e)[:120]


MOCK_CODE = re.compile(r"[A-Za-z0-9.\-]{1,12}")
_mock_daily: dict[str, tuple[float, int, list]] = {}  # 코드 → (받은 시각, 봉 수, 봉)
MOCK_LOCK = threading.Lock()


def mock_kr_busy() -> bool:
    """한국 평일 8시~20시 (프리마켓~애프터마켓). 이때는 일봉 마지막 칸이 바뀐다"""
    k = datetime.now(timezone.utc) + timedelta(hours=9)
    return k.weekday() < 5 and 8 <= k.hour < 20


def mock_daily_one(code: str, count: int) -> list:
    """한 종목 일봉 (오래된 것부터 [날짜, 시가, 고가, 저가, 종가, 거래량]). 장중 5분, 장 밖 6시간 동안 다시 쓰기"""
    ttl = 300 if mock_kr_busy() else 6 * 3600
    with MOCK_LOCK:
        c = _mock_daily.get(code)
        if c and c[1] >= count and time.time() - c[0] < ttl:
            return c[2][-count:]
    q = urllib.parse.urlencode({"symbol": code, "interval": "1d", "count": str(count), "adjusted": "false"})
    j = toss_call("/api/v1/candles?" + q, "chart")
    rows = []
    for v in (j.get("result") or {}).get("candles") or []:
        t = str(v.get("timestamp") or "")
        try:
            row = [t[:10], float(v["openPrice"]), float(v["highPrice"]), float(v["lowPrice"]), float(v["closePrice"]), float(v.get("volume") or 0)]
        except (KeyError, TypeError, ValueError):
            continue
        if len(t) >= 10 and row[4] > 0:
            rows.append(row)
    rows.sort(key=lambda r: r[0])
    with MOCK_LOCK:
        _mock_daily[code] = (time.time(), count, rows)
    return rows


def mock_daily(codes: list[str], count: int) -> dict:
    """여러 종목 일봉을 한 번에. 종목마다 따로 부르되 한도 안에서 동시에 (앱은 100종목씩 나눠 부른다)"""
    count = max(2, min(200, count))
    out, errs = {}, {}
    pool = ThreadPoolExecutor(max_workers=8)
    fs = {pool.submit(mock_daily_one, c, count): c for c in codes[:120]}
    done, _ = wait(fs, timeout=55)
    pool.shutdown(wait=False, cancel_futures=True)
    for f, c in fs.items():
        if f not in done:
            errs[c] = "느림"
        elif f.exception():
            errs[c] = mock_err(f.exception())
        else:
            out[c] = f.result()
    return {"bars": out, "errors": errs, "rate": TOSS_GATES["chart"].rate}


def mock_last(codes: list[str]) -> dict:
    """지금 가격 (토스 코드 그대로, 200개씩)"""
    out, err = {}, None
    for i in range(0, len(codes), 200):
        part = codes[i:i + 200]
        try:
            j = toss_call("/api/v1/prices?symbols=" + urllib.parse.quote(",".join(part), safe=","), "data")
        except Exception as e:  # noqa: BLE001
            err = mock_err(e)
            continue
        for q in j.get("result") or []:
            try:
                v = float(q.get("lastPrice") or 0)
            except ValueError:
                continue
            if v > 0:
                out[str(q.get("symbol"))] = {"last": v, "at": q.get("timestamp")}
    r = {"quotes": out}
    if err:
        r["error"] = err
    return r


def mock_book(code: str) -> dict:
    """호가 그대로 (주문하는 순간에만 부른다)"""
    j = toss_call("/api/v1/orderbook?symbol=" + urllib.parse.quote(code), "data", timeout=4)
    return j.get("result") or {}


_mock_cal: dict[str, tuple[float, dict]] = {}


def mock_calendar(market: str) -> dict:
    """장 운영 정보 (오늘·전·다음 영업일). 10분 동안 다시 쓰기"""
    m = "US" if market.upper() == "US" else "KR"
    c = _mock_cal.get(m)
    if c and time.time() - c[0] < 600:
        return c[1]
    j = toss_call("/api/v1/market-calendar/" + m, "info").get("result") or {}
    _mock_cal[m] = (time.time(), j)
    return j


def mock_get(path: str) -> dict | None:
    """모의투자 GET 경로. 없으면 None"""
    u = urllib.parse.urlparse(path)
    q = urllib.parse.parse_qs(u.query)
    one = lambda k: (q.get(k) or [""])[0]  # noqa: E731
    codes = [x for x in one("symbols").split(",") if MOCK_CODE.fullmatch(x)]
    try:
        if u.path == "/api/mock/daily":
            return mock_daily(codes, int(one("count") or 60))
        if u.path == "/api/mock/last":
            return mock_last(codes)
        if u.path == "/api/mock/book":
            c = one("symbol")
            return mock_book(c) if MOCK_CODE.fullmatch(c) else {"error": "잘못된 종목"}
        if u.path == "/api/mock/calendar":
            return mock_calendar(one("market") or "KR")
        if u.path == "/api/mock/wallet":  # AI 쓴 돈·한도 (앱 요약 박스)
            return ai_wallet({k: one(k) for k in ("day", "week", "month") if one(k)})
    except Exception as e:  # noqa: BLE001
        return {"error": mock_err(e)}
    return None


# ---- 모의투자 AI 조언 (Claude API). 키는 이 서버의 ai_keys.json 에만 둔다 (.gitignore, 앱·저장소에 안 올림)
# 표준 라이브러리만 쓰는 서버라 SDK 대신 HTTP 로 부른다. 쓴 돈은 mock_wallet.json 에 한국 날짜별로 쌓고, 한도를 넘기 전에 막는다.
AI_KEYS = ROOT / "ai_keys.json"
AI_WALLET = ROOT / "mock_wallet.json"
AI_MODEL = "claude-opus-5-5"
AI_PRICE = {"claude-opus-5-5": (4.0, 20.0), "claude-opus-5": (5.0, 25.0), "claude-opus-4-8": (5.0, 25.0)}  # 달러 / 백만 토큰 (입력, 출력)
AI_CAP = {"day": 10.0, "week": 40.0, "month": 100.0}  # 앱이 보낸 한도도 이 위로는 못 올린다 (월 $100 예산)
AI_LOCK = threading.Lock()
_ai_cache: dict[str, tuple[float, dict]] = {}  # 같은 물음 → (받은 시각, 답). 같은 장면에 두 번 돈을 쓰지 않는다

AI_SYSTEM = (
    "너는 개발자 혼자 쓰는 모의투자(가상 돈, 실제 주문 없음)의 조언자다. 한국 주식만 다룬다. "
    "받은 자료만 보고 판단한다. 자료에 없는 가격·뉴스·실적은 지어내지 않는다. "
    "수수료(매수·매도 0.015%)와 매도 세금(0.20%)을 생각한다. 이유는 한국어로 한 문장, 40자 안쪽으로 쓴다. "
    "확신이 없으면 하지 않는 쪽을 고른다."
)

AI_KINDS = {
    # 자기 장부를 굴린다 (장중 몇 분마다). 후보·보유·현금을 받고 주문 몇 개를 낸다
    "ledger": {"effort": "low", "max_tokens": 6000, "ttl": 600, "schema": {
        "type": "object", "additionalProperties": False, "required": ["orders", "note"],
        "properties": {
            "orders": {"type": "array", "items": {
                "type": "object", "additionalProperties": False, "required": ["code", "side", "won", "qty", "why"],
                "properties": {"code": {"type": "string"}, "side": {"type": "string", "enum": ["buy", "sell"]},
                               "won": {"type": "number"}, "qty": {"type": "integer"}, "why": {"type": "string"}}}},
            "note": {"type": "string"}}}},
    # 규칙이 고른 종목에 의견 (주문은 바꾸지 않는다)
    "review": {"effort": "medium", "max_tokens": 6000, "ttl": 3600, "schema": {
        "type": "object", "additionalProperties": False, "required": ["items", "note"],
        "properties": {
            "items": {"type": "array", "items": {
                "type": "object", "additionalProperties": False, "required": ["code", "view", "why"],
                "properties": {"code": {"type": "string"}, "view": {"type": "string", "enum": ["agree", "doubt", "drop"]},
                               "why": {"type": "string"}}}},
            "note": {"type": "string"}}}},
    # 장 마감 뒤 하루 정리
    "daily": {"effort": "medium", "max_tokens": 8000, "ttl": 6 * 3600, "schema": {
        "type": "object", "additionalProperties": False, "required": ["summary", "points"],
        "properties": {"summary": {"type": "string"}, "points": {"type": "array", "items": {"type": "string"}}}}},
    # 말로 쓴 조건 → 조건 묶음 (누를 때만)
    "words": {"effort": "medium", "max_tokens": 6000, "ttl": 24 * 3600, "schema": {
        "type": "object", "additionalProperties": False, "required": ["name", "pieces", "sort", "note"],
        "properties": {
            "name": {"type": "string"},
            "pieces": {"type": "array", "items": {
                "type": "object", "additionalProperties": False, "required": ["kind", "hasLo", "lo", "hasHi", "hi", "days", "above"],
                "properties": {"kind": {"type": "string", "enum": ["price", "change", "volume", "ma", "amount"]},
                               "hasLo": {"type": "boolean"}, "lo": {"type": "number"},
                               "hasHi": {"type": "boolean"}, "hi": {"type": "number"},
                               "days": {"type": "integer"}, "above": {"type": "boolean"}}}},
            "sort": {"type": "string", "enum": ["price", "change", "volume", "ma", "amount"]},
            "note": {"type": "string"}}}},
}


def ai_key() -> str:
    return str(read_json(AI_KEYS, {}).get("anthropic_api_key", "")).strip()


def ai_kst() -> datetime:
    return datetime.now(timezone.utc) + timedelta(hours=9)


def ai_spent(w: dict) -> dict:
    """오늘·이번 주(월요일부터)·이번 달 쓴 돈 (한국 날짜)"""
    k = ai_kst().date()
    mon = k - timedelta(days=k.weekday())
    out = {"day": 0.0, "week": 0.0, "month": 0.0}
    for d, v in (w.get("days") or {}).items():
        try:
            x = date.fromisoformat(d)
        except ValueError:
            continue
        v = float(v or 0)
        if x == k:
            out["day"] += v
        if mon <= x <= k:
            out["week"] += v
        if (x.year, x.month) == (k.year, k.month):
            out["month"] += v
    return {a: round(b, 4) for a, b in out.items()}


def ai_limits(want: dict | None) -> dict:
    base = {"day": 4.0, "week": 18.0, "month": 80.0}
    for k in base:
        try:
            v = float((want or {}).get(k, base[k]))
        except (TypeError, ValueError):
            v = base[k]
        base[k] = max(0.0, min(AI_CAP[k], v))
    return base


def ai_wallet(want: dict | None = None) -> dict:
    with AI_LOCK:
        w = read_json(AI_WALLET, {})
    return {"spent": ai_spent(w), "limits": ai_limits(want), "key": bool(ai_key())}


def ai_cost(model: str, u: dict) -> float:
    pin, pout = AI_PRICE.get(model, AI_PRICE["claude-opus-5"])
    i = float(u.get("input_tokens") or 0)
    cw = float(u.get("cache_creation_input_tokens") or 0)
    cr = float(u.get("cache_read_input_tokens") or 0)
    o = float(u.get("output_tokens") or 0)
    return (i * pin + cw * pin * 1.25 + cr * pin * 0.05 + o * pout) / 1e6


def mock_ai(b: dict) -> dict:
    """AI 한 번. b = {kind, prompt, limits}. 한도·같은 물음·거절·잘림을 여기서 막고, 답은 정해진 꼴(JSON)로만 돌려준다"""
    kind = str(b.get("kind") or "")
    spec = AI_KINDS.get(kind)
    if not spec:
        return {"error": "모르는 AI 일"}
    prompt = str(b.get("prompt") or "")[:24000]
    if not prompt:
        return {"error": "물음이 비었어요"}
    key = ai_key()
    if not key:
        return {"error": "서버에 Claude 키 없음 (ai_keys.json)"}
    lim = ai_limits(b.get("limits"))
    ck = kind + "\n" + prompt
    hit = _ai_cache.get(ck)
    if hit and time.time() - hit[0] < spec["ttl"]:
        r = dict(hit[1])
        r.update({"cached": True, "cost": 0.0, "input": 0, "output": 0})
        r.update(ai_wallet(b.get("limits")))
        return r
    # 가장 비싸게 들 값을 먼저 셈해 한도를 넘으면 부르지 않는다 (한글은 글자당 1토큰 남짓으로 넉넉히)
    worst = (len(prompt) + len(AI_SYSTEM)) * 1.2 * 4.0 / 1e6 + spec["max_tokens"] * 20.0 / 1e6
    with AI_LOCK:
        sp = ai_spent(read_json(AI_WALLET, {}))
    for k, name in (("day", "오늘"), ("week", "이번 주"), ("month", "이번 달")):
        if sp[k] + worst > lim[k]:
            return {"error": f"AI 한도 ({name} ${lim[k]:g} 중 ${sp[k]:.2f} 씀)", "limit": k, "spent": sp, "limits": lim}
    body = {
        "model": AI_MODEL, "max_tokens": spec["max_tokens"],
        "fallbacks": "default",
        "output_config": {"effort": spec["effort"], "format": {"type": "json_schema", "schema": spec["schema"]}},
        "system": AI_SYSTEM,
        "messages": [{"role": "user", "content": prompt}],
    }
    req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(body, ensure_ascii=False).encode("utf-8"), headers={
        "Content-Type": "application/json", "x-api-key": key, "anthropic-version": "2026-10-21",
        "anthropic-beta": "server-side-fallback-2026-07-01"})
    try:
        with urllib.request.urlopen(req, timeout=110) as r:
            j = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            msg = json.loads(e.read().decode("utf-8")).get("error", {}).get("message", "")
        except Exception:  # noqa: BLE001
            msg = ""
        why = {401: "Claude 키를 확인하세요", 429: "Claude 한도 (잠시 뒤)", 529: "Claude 붐빔 (잠시 뒤)"}.get(e.code, f"Claude 응답 {e.code}")
        return {"error": why + (" · " + msg[:80] if msg and e.code not in (401,) else "")}
    except Exception as e:  # noqa: BLE001
        return {"error": "Claude 연결 실패 · " + str(e)[:80]}
    model = str(j.get("model") or AI_MODEL)
    u = j.get("usage") or {}
    cost = ai_cost(model, u)
    with AI_LOCK:  # 실패한 답도 돈은 든다 → 먼저 적는다
        w = read_json(AI_WALLET, {})
        days = w.setdefault("days", {})
        d = ai_kst().date().isoformat()
        days[d] = round(float(days.get(d) or 0) + cost, 6)
        write_json(AI_WALLET, w)
    base = {"model": model, "input": int(u.get("input_tokens") or 0), "output": int(u.get("output_tokens") or 0), "cost": round(cost, 6)}
    stop = j.get("stop_reason")
    if stop == "refusal":
        return {**base, "error": "AI 가 답하지 않음 (거절)", **ai_wallet(b.get("limits"))}
    if stop == "max_tokens":
        return {**base, "error": "AI 답이 잘림", **ai_wallet(b.get("limits"))}
    text = "".join(c.get("text", "") for c in j.get("content") or [] if c.get("type") == "text")
    try:
        ans = json.loads(text)
    except ValueError:
        return {**base, "error": "AI 답을 읽지 못함", **ai_wallet(b.get("limits"))}
    r = {**base, "answer": ans, "cached": False}
    _ai_cache[ck] = (time.time(), r)
    for k in [k for k, v in _ai_cache.items() if time.time() - v[0] > 24 * 3600]:
        _ai_cache.pop(k, None)
    r = dict(r)
    r.update(ai_wallet(b.get("limits")))
    return r


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
                        out = {"USD": {"rate": rate, "date": d, "src": "서울외국환중개", "fetched": int(time.time())}}
                        # 앞 날짜 고시: 토스는 새 고시를 그날 오전 9시부터 쓴다 (앱이 9시 전엔 이 값)
                        if len(sets) > 1:
                            pl, pvv = sets[-2]
                            pm = re.findall(r"\d+", pl)
                            pr = float(pvv.replace(",", ""))
                            if len(pm) >= 3 and 500 < pr < 5000:
                                py = int(pm[0]) + (2000 if int(pm[0]) < 100 else 0)
                                pd = f"{py:04d}-{int(pm[1]):02d}-{int(pm[2]):02d}"
                                if pd < d:
                                    out["USD_prev"] = {"rate": pr, "date": pd}
                        return out
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
        if self.path.startswith("/api/mock/"):  # 모의투자 (개발자 전용, 앱 Mock/)
            o = self.headers.get("Origin") or ""
            self.cors = o if toss_origin_ok(o) else None
            r = mock_get(self.path)
            if r is not None:
                return self.send_json(r)
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
            if self.path == "/api/mock/ai":  # 모의투자 AI 조언 (개발자 전용. 키는 ai_keys.json, 한도·쓴 돈은 mock_wallet.json)
                return self.send_json(mock_ai(b))
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
