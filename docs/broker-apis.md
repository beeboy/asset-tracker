# 증권사 개인 오픈 API 검토 (2026-10-16)

원칙: 사용자 본인 키로 본인 화면에만 시세를 받는다. naeilo는 시세를 모아 다시 배포하지 않는다 (`docs/decisions.md`).
출처는 공개 블로그·오픈소스 문서이고 증권사 공식 명세는 직접 보지 못한 것이 많다. "확인 필요"는 키를 받아 실제로 불러 봐야 한다.

| 증권사 | 개인 API | 지금 가격·일봉 | 해외·환율 | 허용 IP | naeilo 연결 |
|---|---|---|---|---|---|
| 토스증권 | 있음, REST | 됨 | 미국 됨, 원/달러 됨 | 필수 | 연결됨 (`proxy/toss_relay.py`) |
| 키움 (REST, 2025~) | 있음, `api.kiwoom.com` | 됨 (ka10001, ka10081) | 국내만 | 필수, 키당 10개 | 중계 코드만 있음, **일시 보류** (국내만이라서) |
| 한국투자 (KIS Developers) | 있음, REST | 됨 (국내 `FHKST01010100`·`FHKST03010100`) | 해외 현재가 `HHDFS00000300`·일봉 `HHDFS76240000`, 원/달러 일봉 `FHKST03030100`(환율 X), 당일 환율 `HHDFS76200200` | 개인은 공개 자료·공식 예제에 없음 (화이트리스트는 제휴 법인 절차에만). 키로 확인 필요 | **보류 (2026-10-16)**. 되면 중계 없이 앱이 바로 부름. 토큰은 1분 1번, 하루 유효 |
| LS (구 이베스트) | 있음, REST `openapi.ls-sec.co.kr:8080` | 됨 | 해외 일부 (확인 필요) | 확인 필요 | 다음 차례. 토큰 필드 이름이 다름(`appsecretkey`, `scope=oob`), 토큰은 다음 날 07시까지 |
| 대신 CYBOS Plus, NH QV | 윈도 프로그램 전용 | 됨 | 일부 | 해당 없음 | 서버·폰에서 못 씀 |
| 미래에셋, 삼성, KB, 신한 | 개인용 REST 없음 | - | - | - | 못 씀 |
| 해외: Alpaca | 있음, REST `data.alpaca.markets` (무료 모의 계정 키) | 됨 (IEX 실시간 스냅샷) | 미국 주식만 | 없음 | **연결됨 (2026-10-17)**: 설정 > 내 Alpaca 키, 앱이 바로 부름(중계 없음). 토스 > Alpaca > 사이트 순 |
| 해외: Longbridge | 있음, REST `openapi.longbridge.com/quote/quotes` (모의 계정 키, HMAC 서명) | 됨 (홍콩·미국·A주 기본 실시간) | 미국·홍콩·중국. 앱은 아직 미국만 | 없음 | **연결됨 (2026-10-17)**: 설정 > 내 Longbridge 키, 앱이 바로 부름. 토스 > Alpaca > Longbridge > 사이트 순 |
| 해외: Schwab | 있음 | 됨 | 미국 주식 | 앱 승인 필요 | 보류: 7일마다 다시 로그인해야 함 |
| 해외: IBKR, Webull, Tradier | - | - | - | - | 못 씀 (한국 개인 사용자에게 시세 키를 못 줌) |

다른 나라(G7·중국·인도네시아·인도·대만·호주) 검토는 `docs/broker-apis-global.md`.

약관은 공통으로 "본인 이용, 받은 시세를 제3자에게 배포 금지"다. 사용자가 자기 키로 자기 화면에 보는 것은 본인 이용에 가깝다고 보지만, 제3자 앱이 키를 중계하는 것을 허용하는지는 어느 곳도 문서에 없다. 공개 출시 전에 각 증권사에 문의한다.

## 키움 중계 (`proxy/kiwoom_relay.py`), 일시 보류
2026-10-16 보류: 국내 주식만 받아서 미국 종목·원/달러를 따로 받아야 한다. 서버에 올리지 않았고 앱 화면도 없다. 다시 쓰게 되면 아래 순서대로.

토스 중계와 같은 서버·같은 고정 IP를 쓴다. 사용자는 같은 IP 하나를 키움 REST API 사이트 > API 사용신청 > 계좌 APP Key 관리에 등록한다.

| 중계 경로 | 키움 | 내용 |
|---|---|---|
| `GET /api/kiwoom/ip` | - | 등록할 IP |
| `POST /api/kiwoom/token` `{"appkey","secretkey"}` | `POST /oauth2/token` (au10001) | 응답 `token`, `expires_dt`(만료 시각) |
| `POST /api/kiwoom/price` `{"stk_cd":"005930"}` + `Authorization: Bearer` | `POST /api/dostk/stkinfo`, `api-id: ka10001` | 지금 가격 (`cur_prc`, 부호 붙은 문자열) |
| `POST /api/kiwoom/daily` `{"stk_cd","base_dt":"YYYYMMDD","upd_stkpc_tp":"0"}` + `Authorization` (+ `cont-yn: Y`, `next-key`) | `POST /api/dostk/chart`, `api-id: ka10081` | 일봉 `stk_dt_pole_chart_qry[]` (`dt`, `cur_prc`), 한 번에 600개쯤, 더 받을 땐 응답의 `cont-yn`·`next-key`를 다시 보냄 |

- 키·토큰을 저장·기록하지 않고, 주문·계좌 경로는 없다. IP 하나가 1분에 60번 넘게 부르면 429.
- 키움 오류는 HTTP 200에 `return_code`로 온다. 앱은 `return_code == 0`만 성공으로 본다.

### 서버에 올리기 (토스 중계가 이미 있는 서버)
1. `cd ~/asset-tracker && git pull`
2. `sudo cp proxy/naeilo-kiwoom-relay.service /etc/systemd/system/` → `sudo systemctl edit naeilo-kiwoom-relay` 로 `Environment=NAEILO_TOSS_IP=<고정 IP>` → `sudo systemctl enable --now naeilo-kiwoom-relay`
3. `/etc/caddy/Caddyfile` 의 `toss.naeilo.com { … }` 안에서 `reverse_proxy 127.0.0.1:8766` 앞에 넣고 `sudo systemctl reload caddy`:
   ```
   handle /api/kiwoom/* {
       reverse_proxy 127.0.0.1:8767
   }
   ```
   (`reverse_proxy 127.0.0.1:8766` 은 `handle { … }` 으로 감싼다.)
4. 확인: `curl https://toss.naeilo.com/api/kiwoom/ip` 가 고정 IP 를 돌려주면 된다.

## 남은 일
- 키움: 일시 보류 (서버 배포·앱 화면 안 함).
- 한국투자: 보류 (2026-10-16). 다시 할 때 허용 IP가 없는지 키로 확인한 뒤 앱 직결로 붙인다.
  - 약관: 한국투자는 "제3자에게 서비스를 제공하려는 법인"만 제휴 대상이고 핀테크사는 제휴 불가, 개인은 "본인 자산으로 자기만의 전략" 용도라고 적어 둠. naeilo가 사용자 본인 키로 본인 화면에 보여 주는 것이 이에 걸리는지는 회색 지대라 공개 출시 전에 문의한다.
  - 출처: <https://apiportal.koreainvestment.com/provider>, <https://apiportal.koreainvestment.com/provider-process>, <https://github.com/koreainvestment/open-trading-api> (`examples_llm/`)
- LS: 허용 IP·해외 시세 확인 뒤 결정.
