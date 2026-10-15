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

## 남은 확인
- 토스 약관: "본인 매매 목적에 한해 이용, 제3자 배포 금지". 사용자가 자기 키로 자기 화면에 보는 건 본인 이용에 가깝다고 봤지만, 제3자 앱이 키를 중계하는 것을 허용하는지는 문서에 없다. 공개 출시 전에 토스에 문의.
- 키 하나에 토큰 하나: 사용자가 같은 키를 다른 프로그램에서도 쓰면 서로 끊긴다. 화면에 안내함.
- 웹(`web/`, 개발자용)에는 이 경로가 없다 (앱만).
- 중계에 일봉·환율 경로를 넣은 새 판을 서버에 올려야 앱이 지난 종가·환율을 토스로 받는다. 첫 3년치는 종목당 4번쯤 불러서, 종목이 많으면 1분 60번 제한에 걸려 다음 갱신 때 이어 받는다.
