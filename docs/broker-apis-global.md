# 나라별 오픈 API 검토: G7·중국·인도네시아 외 (2026-10-17)

`us-brokers.md` 와 같은 기준이다. 사용자 본인 키로 본인 화면에만 시세를 받고, naeilo는 시세를 다시 배포하지 않는다.
기준: 계좌·거주 조건, 인증, 앱·위젯에서 직접 부를 수 있는지(중계 서버·PC 프로그램 없이), 비용.
조사는 필요한 만큼만 했다. 공식 문서로 확인한 것은 출처를 달았고, "확인 필요"는 기존 지식이거나 실제 키로 불러 봐야 아는 것이다.

## 한눈에

| 나라 | 후보 | 계좌·거주 | 인증 | 폰에서 바로 | 비용 | 판단 |
|---|---|---|---|---|---|---|
| 중국 본토·홍콩 | **Longbridge OpenAPI** | 모의(paper) 계정으로도 시세 됨. 가입 가능 나라는 확인 필요 | 앱 키·비밀키 서명 + 액세스 토큰 | 됨. 공식 SDK에 HTTP 시세 경로(`POST /quote/quotes`)가 있음 | OpenAPI 무료, 모의 계정에 홍콩·미국·A주 실시간 포함 | **1순위** (홍콩·A주·미국을 키 하나로) |
| 중국 본토·홍콩 | Futu OpenAPI (moomoo) | 계좌 필요 | OpenD 게이트웨이 프로그램을 PC·서버에 띄워야 함 | **안 됨** | 무료~ | 못 씀 (IBKR와 같은 구조) |
| 중국 본토·홍콩 | Tiger OpenAPI | 계좌 필요 | RSA 키 서명, REST | 됨 | 홍콩·미국 시세 유료 카드, 확인 필요 | 2순위 |
| 중국 본토 | 본토 증권사 QMT·PTrade | 본토 거주자 계좌 | PC 퀀트 단말 안에서만 | **안 됨** | 자산 조건 | 못 씀 |
| 일본 | J-Quants (JPX 공식) | 일본 거주 조건 명시 없음, 확인 필요. 증권 계좌 불필요 | 이메일 가입 → 리프레시 토큰 → ID 토큰 | 됨 (REST) | 무료는 12주 지연. Light ¥1,650·Standard ¥3,300/월은 일봉(장 마감 후), 실시간 없음 | 일봉 전용 2순위 |
| 일본 | kabu STATION API (三菱UFJ eスマート) | 일본 거주 계좌 | PC의 kabuステーション 앱이 켜져 있어야 함(localhost) | **안 됨** | 무료 | 못 씀 |
| 일본 | 立花証券 e支店 API | 일본 거주 계좌 | ID·비밀번호 로그인 세션 | 됨, 확인 필요 | 무료 | 일본 현지 사용자용 후보 |
| 인도 | **Kite Connect (Zerodha)** | 인도 거주자 Zerodha 계좌 | API 키 + 매일 로그인으로 액세스 토큰 (매일 아침 만료) | 됨, 다만 하루 한 번 로그인 | 무료 Personal은 시세 없음. 시세는 ₹500/월 | 시세 받으려면 유료 + 매일 재로그인 |
| 인도 | Upstox·Angel One SmartAPI | 인도 거주자 계좌 | OAuth 또는 TOTP 로그인, 매일 만료 | 됨 | 무료 (확인 필요) | Kite보다 싸지만 같은 매일 로그인 |
| 대만 | Fugle MarketData API | 대만 계좌(富果·玉山) 필요, 확인 필요 | API 토큰 | 됨 (REST·웹소켓) | 기본 무료, 확인 필요 | 대만 현지 사용자용 후보 |
| 대만 | 永豐 Shioaji | 대만 계좌 | Python SDK + 인증서 | **안 됨** | 무료 | 못 씀 |
| 캐나다 | Questrade API | Questrade 계좌(캐나다 거주자) | OAuth, 리프레시 토큰 1회용·7일 | 됨 | 캐나다 시세는 무료 지연, 실시간은 데이터 패키지 유료 | 캐나다 현지 사용자용 후보 |
| 영·독·프·이 | Saxo OpenAPI | Saxo 계좌 (한국 거주자 불가로 앎, 확인 필요) | 시험용 24시간 토큰, 실계좌는 앱 등록 + OAuth | 됨 | 계좌 무료, 거래소 시세 구독 유료 | 유럽 현지 사용자용 후보, 무거움 |
| 영·독·프·이 | IG REST API | IG 계좌 (유럽·영국 등) | API 키 + 로그인 세션 토큰 | 됨, 실시간은 Lightstreamer 스트리밍 | 무료 | CFD 가격이라 주식 시세로는 애매, 보류 |
| 영·독·프·이 | Trading 212, Scalable 등 | 계좌 필요 | 키 | - | - | 시세 API 없음 또는 계좌 정보만, 못 씀 |
| 인도네시아 | 현지 증권사 (Stockbit, Ajaib, IPOT 등) | - | - | - | - | **공개 개인 API 없음.** IDX 시세는 데이터 업체 유료뿐 |
| 호주 | CommSec, SelfWealth 등 | - | - | - | - | 공개 개인 API 없음 |

## 증권사 대신: 데이터 업체 키 하나로 여러 나라

증권사 API가 없는 나라(유럽·인도네시아·호주)와 계좌가 필요한 나라를 한 번에 덮는 방법은 사용자가 데이터 업체 키를 넣는 것이다. 계좌·거주 조건이 없고, REST라 위젯에서도 바로 부를 수 있다.
- 예: EODHD, Twelve Data, Finnhub. 전 세계 일봉 + 15~20분 지연 시세가 월 $20~30 안팎, 무료는 하루 호출 수가 아주 적다 (요금·지원 거래소는 확인 필요).
- 대신 실시간이 아니고(지연), 약관상 "개인 이용" 범위를 공개 출시 전에 업체에 문의해야 한다. 지금 사이트 시세(지연)보다 나은 점은 나라 범위다.

## 추천 (고를 것)

1. **홍콩·중국 A주: Longbridge 키** 를 Alpaca 다음 "내 시세 키"로 넣는다. 모의 계정으로 실시간이 나와 조건이 가장 가볍다. **2026-10-17 앱에 붙임** (지금은 미국 종목만, 앱이 원화·달러만 다뤄서). 해외 거주자 가입은 키로 확인한다.
2. **일본·유럽·인도네시아·호주: 데이터 업체 키 하나** (EODHD 또는 Twelve Data 중 한 곳). 지연 시세지만 계좌 없이 여러 나라를 덮는다. 일본 일봉만이면 J-Quants도 된다.
3. **인도·캐나다·대만: 현지 사용자가 생길 때만** 각 나라 증권사(Kite, Questrade, Fugle)를 붙인다. 모두 현지 거주 계좌가 필요하고, 인도는 매일, 캐나다는 7일마다 재로그인이 필요하다.
4. 못 쓰는 것: Futu(OpenD), kabu STATION, 본토 QMT·PTrade, Shioaji. 모두 PC 프로그램이 켜져 있어야 해서 IBKR와 같은 이유로 폰·위젯에서 못 쓴다.

## 출처
- Longbridge 모의 계정 시세·키 구조: <https://open.longbridge.com/docs/qa/general>, <https://open.longbridge.com/docs/qa/broker>, <https://open.longbridge.com/pricing>
- Kite Connect 요금 (Personal 무료는 시세 없음, ₹500/월): <https://support.zerodha.com/category/trading-and-markets/general-kite/kite-api/articles/historical-data-and-live-market-data-payment-plan>
- J-Quants 요금·12주 지연: <https://www.jpx.co.jp/english/corporate/news/news-releases/6020/20250505-01.html>, <https://jpx-jquants.com/en/spec/rate-limits.md>
- Saxo OpenAPI: <https://www.developer.saxo/openapi/learn>
- Questrade API: <https://www.questrade.com/api/documentation/getting-started>, 시세 요금 <https://www.questrade.com/pricing/self-directed-commissions-plans-fees/market-data>
- 인도네시아 IDX 데이터(개인 API 없음, 업체·비공식 경로뿐): <https://community.developers.refinitiv.com/discussion/comment/54134>
- EODHD: <https://eodhd.com/lp/realtime-api>
