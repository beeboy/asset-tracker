#!/usr/bin/env python3
"""토스증권 공용 중계 (일반 사용자용). 고정 IP 서버에서 돌린다.

토스는 사용자가 등록한 허용 IP 에서만 호출을 받는다. 아이폰은 IP 가 계속 바뀌므로
사용자는 이 서버의 IP 를 토스 WTS > 설정 > Open API > 허용 IP 에 등록하고, 앱이 이 중계를 거쳐 부른다.

- 키(client_id·client_secret)와 토큰은 저장하지 않는다. 받은 그대로 토스에 넘기고 토스 응답을 그대로 돌려준다.
- 기록(로그)에 키·토큰·요청 본문을 남기지 않는다.
- 토큰 발급, 현재가(/api/v1/prices), 일봉·1분봉(/api/v1/candles, 1d·1m), 원/달러(/api/v1/exchange-rate)만 넘긴다. 주문·계좌·자산 경로는 없다 (계좌 헤더도 넘기지 않는다).
- 한 IP 가 1분에 60번 넘게 부르면 429 (열린 중계로 쓰이지 않게). 개발자 열쇠(머리글 X-Naeilo-Dev = 저장소 dev_token.json)가 맞으면 세지 않는다.

실행: NAEILO_TOSS_IP=<이 서버 공인 IP> python3 proxy/toss_relay.py [--port 8766]
앞에 HTTPS(예: Caddy toss.naeilo.com → 127.0.0.1:8766)를 둔다. 개발자 키를 쓰는 server.py 와는 따로 돈다.
"""
import argparse
import hmac
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOSS = "https://openapi.tossinvest.com"
SYMBOLS = re.compile(r"[A-Za-z0-9.,]{1,2400}")  # 6자리 국내 코드·미국 티커, 쉼표로 200개까지
SYMBOL = re.compile(r"[A-Za-z0-9.]{1,12}")  # 일봉은 한 종목씩
ISO = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}:\d{2})")
PER_MIN = 60

_hits: dict[str, list[float]] = {}
_lock = threading.Lock()

DEV_TOKEN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dev_token.json")  # 개발자 서버와 같은 열쇠 (.gitignore)
_dev = [0.0, ""]  # (읽은 시각, 열쇠). 1분마다 다시 읽는다


def dev_ok(h) -> bool:
    """개발자 폰: 한도를 세지 않는다 (2026-10-25 Drinker 결정)"""
    now = time.time()
    if now - _dev[0] > 60:
        try:
            with open(DEV_TOKEN, encoding="utf-8") as f:
                _dev[1] = str(json.load(f).get("token", "")).strip()
        except (OSError, ValueError):
            _dev[1] = ""
        _dev[0] = now
    return bool(_dev[1]) and hmac.compare_digest(_dev[1].encode(), str(h.get("X-Naeilo-Dev") or "").encode())


def too_many(ip: str) -> bool:
    now = time.time()
    with _lock:
        h = [t for t in _hits.get(ip, []) if now - t < 60]
        h.append(now)
        _hits[ip] = h
        if len(_hits) > 5000:  # 오래된 IP 정리
            for k in [k for k, v in _hits.items() if now - v[-1] > 60]:
                del _hits[k]
        return len(h) > PER_MIN


class Relay(BaseHTTPRequestHandler):
    server_version = "naeilo-toss-relay"

    def log_message(self, *a):  # 키·토큰이 섞일 수 있는 기록은 남기지 않는다
        pass

    def client_ip(self) -> str:
        # 앞단(Caddy)이 붙인 X-Forwarded-For 의 맨 앞, 없으면 접속 주소
        return (self.headers.get("X-Forwarded-For") or self.client_address[0]).split(",")[0].strip()

    def reply(self, code: int, body: bytes, ctype: str = "application/json; charset=utf-8", extra: dict | None = None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def err(self, code: int, msg: str):
        self.reply(code, json.dumps({"error": {"code": "relay", "message": msg}}, ensure_ascii=False).encode("utf-8"))

    def forward(self, req: urllib.request.Request):
        try:
            with urllib.request.urlopen(req, timeout=4) as r:
                body, code, h = r.read(), r.status, r.headers
        except urllib.error.HTTPError as e:
            body, code, h = e.read(), e.code, e.headers
        except Exception:
            return self.err(502, "토스에 닿지 못함")
        extra = {k: h[k] for k in ("Retry-After", "X-RateLimit-Remaining") if h and h.get(k)}
        self.reply(code, body, h.get("Content-Type") or "application/json", extra)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        if u.path == "/api/toss/ip":  # 사용자가 토스에 등록할 IP (앱 설정 화면에 보여 준다)
            return self.reply(200, json.dumps({"ip": os.environ.get("NAEILO_TOSS_IP", "")}).encode())
        if u.path not in ("/api/toss/v1/prices", "/api/toss/v1/candles", "/api/toss/v1/exchange-rate"):
            return self.err(404, "없는 경로")
        if not dev_ok(self.headers) and too_many(self.client_ip()):
            return self.err(429, "잠시 뒤에 다시")
        q = {k: v[0] for k, v in urllib.parse.parse_qs(u.query).items()}
        auth = self.headers.get("Authorization") or ""
        if not auth.startswith("Bearer "):
            return self.err(400, "잘못된 요청")
        if u.path == "/api/toss/v1/prices":
            syms = q.get("symbols", "")
            if not SYMBOLS.fullmatch(syms):
                return self.err(400, "잘못된 요청")
            out = "/api/v1/prices?symbols=" + urllib.parse.quote(syms, safe=",")
        elif u.path == "/api/toss/v1/candles":
            # 일봉(지난 종가)과 1분봉(종목 상세 1일 차트). 한 번에 200개까지, before 로 더 지난 쪽을 받는다
            sym, before = q.get("symbol", ""), q.get("before", "")
            cnt = q.get("count", "200")
            if not SYMBOL.fullmatch(sym) or q.get("interval", "1d") not in ("1d", "1m") or not cnt.isdigit() or not 1 <= int(cnt) <= 200 \
                    or (before and not ISO.fullmatch(before)) or q.get("adjusted", "false") not in ("true", "false"):
                return self.err(400, "잘못된 요청")
            p = {"symbol": sym, "interval": q.get("interval", "1d"), "count": cnt, "adjusted": q.get("adjusted", "false")}
            if before:
                p["before"] = before
            out = "/api/v1/candles?" + urllib.parse.urlencode(p)
        else:
            # 원/달러만 (dateTime 을 주면 그 시각 값)
            dt = q.get("dateTime", "")
            if dt and not ISO.fullmatch(dt):
                return self.err(400, "잘못된 요청")
            p = {"baseCurrency": "USD", "quoteCurrency": "KRW"}
            if dt:
                p["dateTime"] = dt
            out = "/api/v1/exchange-rate?" + urllib.parse.urlencode(p)
        self.forward(urllib.request.Request(TOSS + out, headers={"Authorization": auth, "Accept": "application/json"}))

    def do_POST(self):
        if urllib.parse.urlparse(self.path).path != "/api/toss/token":
            return self.err(404, "없는 경로")
        if not dev_ok(self.headers) and too_many(self.client_ip()):
            return self.err(429, "잠시 뒤에 다시")
        n = int(self.headers.get("Content-Length") or 0)
        if not 0 < n <= 2048:
            return self.err(400, "잘못된 요청")
        f = urllib.parse.parse_qs(self.rfile.read(n).decode("utf-8", "replace"))
        cid, sec = (f.get("client_id") or [""])[0], (f.get("client_secret") or [""])[0]
        if not cid or not sec:
            return self.err(400, "키가 비었음")
        body = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": cid, "client_secret": sec}).encode()
        req = urllib.request.Request(TOSS + "/oauth2/token", data=body,
                                     headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"})
        self.forward(req)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8766)
    p.add_argument("--host", default="127.0.0.1")
    a = p.parse_args()
    ThreadingHTTPServer((a.host, a.port), Relay).serve_forever()


if __name__ == "__main__":
    main()
