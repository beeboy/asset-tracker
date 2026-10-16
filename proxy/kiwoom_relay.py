#!/usr/bin/env python3
"""키움증권 REST API 공용 중계 (일반 사용자용). 토스 중계와 같은 고정 IP 서버에서 돌린다.

키움 REST API 도 사용자가 등록한 허용 IP(최대 10개)에서만 토큰을 내준다. 아이폰은 IP 가 계속 바뀌므로
사용자는 이 서버의 IP 를 키움 REST API 사이트 > API 사용신청 > 계좌 APP Key 관리에 등록하고, 앱이 이 중계를 거쳐 부른다.

- 키(appkey·secretkey)와 토큰은 저장하지 않는다. 받은 그대로 키움에 넘기고 키움 응답을 그대로 돌려준다.
- 기록(로그)에 키·토큰·요청 본문을 남기지 않는다.
- 토큰 발급(au10001), 주식기본정보(ka10001, 지금 가격), 일봉(ka10081, 지난 종가)만 넘긴다. 주문·계좌 경로는 없다.
- 키움 REST 는 국내 주식만 있다 (해외·환율 없음).
- 한 IP 가 1분에 60번 넘게 부르면 429 (열린 중계로 쓰이지 않게).

실행: NAEILO_TOSS_IP=<이 서버 공인 IP> python3 proxy/kiwoom_relay.py [--port 8767]
앞단 Caddy 가 toss.naeilo.com/api/kiwoom/* 를 127.0.0.1:8767 로 넘긴다 (docs/broker-apis.md).
"""
import argparse
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
from http.server import ThreadingHTTPServer

from toss_relay import Relay, too_many

KIWOOM = "https://api.kiwoom.com"
CODE = re.compile(r"\d{6}(_NX|_AL)?")  # 국내 6자리, NXT·SOR 접미사
DATE = re.compile(r"\d{8}")
KEY = re.compile(r"[A-Za-z0-9+/=_.\-]{1,512}")
NEXT = re.compile(r"[A-Za-z0-9+/=_.\-]{0,200}")


class KiwoomRelay(Relay):
    server_version = "naeilo-kiwoom-relay"

    def body(self) -> dict | None:
        n = int(self.headers.get("Content-Length") or 0)
        if not 0 < n <= 2048:
            return None
        try:
            d = json.loads(self.rfile.read(n).decode("utf-8"))
        except Exception:
            return None
        return d if isinstance(d, dict) else None

    def forward(self, path: str, payload: dict, headers: dict):
        req = urllib.request.Request(KIWOOM + path, data=json.dumps(payload).encode("utf-8"),
                                     headers={"Content-Type": "application/json;charset=UTF-8", **headers})
        try:
            with urllib.request.urlopen(req, timeout=4) as r:
                body, code, h = r.read(), r.status, r.headers
        except urllib.error.HTTPError as e:
            body, code, h = e.read(), e.code, e.headers
        except Exception:
            return self.err(502, "키움에 닿지 못함")
        # 일봉 연속조회에 쓰는 헤더는 그대로 돌려준다
        extra = {k: h[k] for k in ("cont-yn", "next-key", "api-id", "Retry-After") if h and h.get(k)}
        self.reply(code, body, h.get("Content-Type") or "application/json", extra)

    def do_GET(self):
        if urllib.parse.urlparse(self.path).path == "/api/kiwoom/ip":  # 사용자가 키움에 등록할 IP
            return self.reply(200, json.dumps({"ip": os.environ.get("NAEILO_TOSS_IP", "")}).encode())
        self.err(404, "없는 경로")

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        if path not in ("/api/kiwoom/token", "/api/kiwoom/price", "/api/kiwoom/daily"):
            return self.err(404, "없는 경로")
        if too_many(self.client_ip()):
            return self.err(429, "잠시 뒤에 다시")
        d = self.body()
        if d is None:
            return self.err(400, "잘못된 요청")
        if path == "/api/kiwoom/token":
            ak, sk = str(d.get("appkey") or ""), str(d.get("secretkey") or "")
            if not KEY.fullmatch(ak) or not KEY.fullmatch(sk):
                return self.err(400, "키가 비었음")
            return self.forward("/oauth2/token", {"grant_type": "client_credentials", "appkey": ak, "secretkey": sk}, {})
        auth = self.headers.get("Authorization") or ""
        if not auth.startswith("Bearer ") or "\n" in auth or "\r" in auth:
            return self.err(400, "잘못된 요청")
        code = str(d.get("stk_cd") or "")
        if not CODE.fullmatch(code):
            return self.err(400, "잘못된 요청")
        if path == "/api/kiwoom/price":
            return self.forward("/api/dostk/stkinfo", {"stk_cd": code}, {"authorization": auth, "api-id": "ka10001"})
        # 일봉: base_dt 부터 지난 쪽으로. 더 받을 땐 앞 응답의 cont-yn·next-key 를 헤더로 다시 보낸다
        base, upd = str(d.get("base_dt") or ""), str(d.get("upd_stkpc_tp") or "0")
        cont, nkey = self.headers.get("cont-yn") or "", self.headers.get("next-key") or ""
        if not DATE.fullmatch(base) or upd not in ("0", "1") or cont not in ("", "Y", "N") or not NEXT.fullmatch(nkey):
            return self.err(400, "잘못된 요청")
        h = {"authorization": auth, "api-id": "ka10081"}
        if cont == "Y" and nkey:
            h.update({"cont-yn": "Y", "next-key": nkey})
        self.forward("/api/dostk/chart", {"stk_cd": code, "base_dt": base, "upd_stkpc_tp": upd}, h)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8767)
    p.add_argument("--host", default="127.0.0.1")
    a = p.parse_args()
    ThreadingHTTPServer((a.host, a.port), KiwoomRelay).serve_forever()


if __name__ == "__main__":
    main()
