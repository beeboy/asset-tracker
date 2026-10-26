# 토스증권 공용 중계 (일반 사용자용)

일반 사용자가 자기 토스증권 Open API 키로 앱에서 국내·미국 지금 가격을 받게 한다.

## 왜 중계가 필요한가
- 토스는 사용자가 WTS > 설정 > Open API > 허용 IP 관리에 등록한 IP 에서 온 호출만 받는다 (토큰 발급 포함, 미등록은 403·`access_denied`).
- 아이폰은 IP 가 계속 바뀌므로 토스를 직접 부를 수 없다. 그래서 고정 IP 서버 한 곳을 모든 사용자가 같이 등록한다.

## 흐름
1. 사용자: 토스 PC 웹에서 `client_id`·`client_secret` 발급, 허용 IP 에 중계 IP 등록 (앱 설정 > 토스증권 시세 화면이 `/api/toss/ip` 로 받아 보여 준다).
2. 앱: 키를 키체인(`TossKeys`, `com.naeilo.toss`, 이 기기만)에 저장. `POST {중계}/api/toss/token` 으로 토큰을 받아 키체인에 만료 시각과 함께 둔다.
3. 앱: `GET {중계}/api/toss/v1/prices?symbols=…` 에 `Authorization: Bearer` 를 붙여 부른다. 401 이면 토큰을 한 번 새로 받는다. 404 묶음은 종목별로 다시 묻고 없는 종목은 하루 뺀다 (`server.py` 개발자 경로와 같은 규칙).
4. 앱: 같은 토큰으로 `GET {중계}/api/toss/v1/candles?symbol=…&interval=1d&adjusted=false&count=…[&before=…]`(지난 종가, 종목마다 하루 한 번)와 `GET {중계}/api/toss/v1/exchange-rate[?dateTime=…]`(원/달러, 10분마다)도 부른다 (`TossHistory`).
5. 중계(`proxy/toss_relay.py`): 토큰·현재가·일봉(1d만)·원/달러(USD/KRW만) 네 경로만 토스에 그대로 넘긴다. 키·토큰을 저장·기록하지 않고, 주문·계좌 경로와 계좌 헤더는 없다. IP 하나가 1분에 60번 넘게 부르면 429.

개발자 주소(`naeilo://toss?u=…ts.net`)가 있으면 그쪽이 먼저다. 둘 다 없으면 기본 시세(Yahoo·사이트)를 쓴다.

## 서버에 올리기 (AWS Lightsail 서울, 개발자 중계와 같은 서버)
1. Cloudflare DNS: `toss.naeilo.com` A 레코드 → 서버 고정 IP, 프록시 끔(회색 구름). 앱의 주소는 `Config.tossRelay`.
2. Lightsail 방화벽에 80·443 열기.
3. Caddy 설치 후 `/etc/caddy/Caddyfile`:
   ```
   toss.naeilo.com {
       bind <서버 내부 IP>   # 개발자용 Tailscale 이 443 을 쓰므로 겹치지 않게 (hostname -I 첫 값)
       reverse_proxy 127.0.0.1:8766
       log { output discard }
   }
   ```
4. `sudo cp proxy/naeilo-toss-relay.service /etc/systemd/system/` → `sudo systemctl edit naeilo-toss-relay` 로 `Environment=NAEILO_TOSS_IP=<고정 IP>` → `sudo systemctl enable --now naeilo-toss-relay` → `sudo systemctl reload caddy`.
5. 확인: `curl https://toss.naeilo.com/api/toss/ip` 가 고정 IP 를 돌려주면 된다.

## 권한과 한도 (2026-10-25 기준, 바꾸면 여기부터 고친다)
| 누가 | 어느 길 | 무엇을 | 한도 |
|---|---|---|---|
| 모든 사용자 (내 토스 키) | 공용 중계 `toss.naeilo.com/api/toss/*` → 8766 `proxy/toss_relay.py` | 토큰 · 현재가 · 일봉(1d) · 1분봉(1m, 종목 상세 1일) · 원/달러. 주문·계좌·호가 없음 | IP 하나 1분 60번 (같은 와이파이 폰은 나눠 씀). 넘으면 429, 1분 뒤 풀림 |
| 개발자 폰 (모의투자 열쇠를 넣은 내부 베타) | 같은 공용 중계 | 위와 같음 | 없음. 앱이 머리글 `X-Naeilo-Dev` 를 보내고 중계가 서버 `~/asset-tracker/dev_token.json` 과 견준다 (Drinker 결정) |
| 개발자 폰만 | `toss.naeilo.com/api/mock/*` → Caddy → 8765 `server.py` (naeilo-dev) | 모의투자 일봉·현재가·호가·장 달력·1분봉(정규장)·AI | 자체 한도 없음. 열쇠가 틀리면 403. 토스 쪽 한도(응답 머리 X-RateLimit)만 맞춘다 |
| 토스 | — | 봉은 1m·1d 뿐 (1h 는 400, 서버에서 확인) | 한 번에 200개 |

- 60 이라는 값은 열린 통로로 쓰이지 않게 2026-10-15 Claude 가 근거 없이 잡았다. Drinker 가 정하면 바꾼다.
- 서버 파일은 콘솔에서 손으로 고친 적이 있다 (2026-10-25: server.py 1분봉, proxy/toss_relay.py 1m·개발자 열쇠). 저장소에 올린 뒤 서버에서 `git pull` 하기 전에 `git checkout server.py proxy/toss_relay.py` 먼저.

## 남은 확인
- 토스 약관: "본인 매매 목적에 한해 이용, 제3자 배포 금지". 사용자가 자기 키로 자기 화면에 보는 건 본인 이용에 가깝다고 봤지만, 제3자 앱이 키를 중계하는 것을 허용하는지는 문서에 없다. 공개 출시 전에 토스에 문의.
- 키 하나에 토큰 하나: 사용자가 같은 키를 다른 프로그램에서도 쓰면 서로 끊긴다. 화면에 안내함.
- 웹(`web/`, 개발자용)에는 이 경로가 없다 (앱만).
- 중계에 일봉·환율 경로를 넣은 새 판을 서버에 올려야 앱이 지난 종가·환율을 토스로 받는다. 첫 3년치는 종목당 4번쯤 불러서, 종목이 많으면 1분 60번 제한에 걸려 다음 갱신 때 이어 받는다.

## 모의투자 경로 (개발자 전용, 2026-10-25)
- 같은 Caddy 의 `toss.naeilo.com { … }` 안, `reverse_proxy 127.0.0.1:8766` 앞에 넣고 `sudo systemctl reload caddy`:
  ```
  handle /api/mock/* {
      reverse_proxy 127.0.0.1:8765
  }
  ```
- 8765 는 개발자 서버 `server.py` (서비스 `naeilo-dev`, `ExecStart=/usr/bin/python3 /home/ubuntu/asset-tracker/server.py --port 8765 --no-browser`).
- 열쇠: 서버 `~/asset-tracker/dev_token.json` 에 `{"token": "<16자 이상 영숫자·-·_>"}` (.gitignore). 앱이 머리글 `X-Naeilo-Dev` 로 같은 값을 보내야 답하고, 아니면 403. 파일이 없으면 모의 경로는 모두 403.
- 앱: 폰에서 `naeilo://mock?t=<같은 값>` 을 한 번 열면 그 기기에만 저장된다(내부 베타만). Tailscale 주소(`naeilo://toss?u=…ts.net`)가 있으면 그쪽이 먼저다.
- 종목 상세(2026-10-25): `/api/mock/minutes?symbol=` 는 마지막 거래일 1분봉을 토스 `interval=1m` 정규장(9:00~15:30)만 장 끝부터 200개씩 두 번 받아 준다(1분 캐시). 토스 봉은 1분·일봉뿐(외부 도구 기준, 공식 명세 미확인). 시간봉(1h)은 400 으로 없음 확인(2026-10-25).
- 확인: `curl -H "X-Naeilo-Dev: <값>" https://toss.naeilo.com/api/mock/wallet` 가 `{"spent": …}` 를 돌려주면 된다. 머리글 없이 부르면 403.

## 토스 실주문 중계 (개발자 전용, 2026-10-26)
- 토스 주문 API 도 허용 IP 에서만 받고 키는 서버에만 있으므로, 앱 실전 브로커(`MockTossBroker`)는 토스를 직접 부르지 않고 `server.py` 를 거친다. 앱에는 토큰·계좌가 없다.
- 경로 (모두 머리글 `X-Naeilo-Dev` 필요, 아니면 403. Caddy 의 `handle /api/mock/*` 가 그대로 넘기므로 Caddy 는 안 고친다):
  | 앱 | 서버가 부르는 토스 | 서버 스위치 꺼짐일 때 |
  |---|---|---|
  | `GET /api/mock/order/ready` | — (준비 상태만: armed·account·keys·max_krw) | 답함 |
  | `POST /api/mock/order` | `POST /api/v1/orders` | 막음 `relay-off` |
  | `GET /api/mock/order/{orderId}` | `GET /api/v1/orders/{orderId}` | 답함 |
  | `POST /api/mock/order/{orderId}/cancel` | `POST /api/v1/orders/{orderId}/cancel` | 답함 (꺼도 대기 주문을 치울 수 있게) |
- 서버 스위치: 서버 `~/asset-tracker/toss_live.json` (.gitignore) `{"armed": true, "account_seq": "<토스 계좌 accountSeq>", "max_krw": 100000}`. 파일이 없거나 `armed` 가 true 가 아니면 새 주문은 토스로 안 나간다. `max_krw` 는 한 번 주문 금액 상한(지정가는 가격×수량, 시장가는 지금 가격×수량). 0 이면 모두 막는다.
- 주문 본문은 정한 칸만 넘긴다: symbol·side(BUY/SELL)·orderType(LIMIT/MARKET)·quantity(정수)·price(지정가만, 정수)·clientOrderId·timeInForce=DAY.
- 토큰은 시세와 같은 `toss_keys.json` 토큰을 같이 쓴다 (클라이언트당 토큰 1개). 401 이면 한 번 새로 받아 다시. 429 는 앱에 그대로 넘기고 앱이 Retry-After 뒤 같은 clientOrderId 로 다시 보낸다 (토스 멱등성 10분이라 두 번 안 나감).
- 기록: `~/asset-tracker/toss_orders.log` (한 줄 JSON: 시각·종목·방향·수량·가격·clientOrderId·결과·토스 오류 코드). 키·토큰·계좌는 안 남긴다.
- 앱 실전 스위치는 서버가 `ready` 로 준비됐다고 답할 때만 켜진다.
- 남은 일: 계좌 잔고 맞추기·정정·웹소켓 체결 알림. 토스 주문 경로·머리글은 앱 `TossOrderAPI`(토스 문서 2026-10-26 읽음)를 따랐고, 실주문으로는 아직 확인하지 않았다.
