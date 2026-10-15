# 웹 입구 페이지 기획 (2026-10-15)

결정(docs/decisions.md 2026-10-15): 웹은 "입구"로 줄인다. 앱 소개 페이지처럼 꾸미고, 수량만 넣으면 평가액과 본전·목표 확률을 보여 주는 카드 한 장, 그리고 앱 받기 버튼만 둔다. 지금의 웹 전체 화면(`web/`)은 개발자용으로 남기되 입구에서 링크하지 않는다.

이 문서는 1번(기획)이다. 2번(입구 만들기)과 3번(웹 정리)은 따로 스레드에서 동시에 진행하므로 끝에 파일을 나눠 두었다.

## 1. 주소와 자리

| 주소 | 지금 | 바뀐 뒤 |
|---|---|---|
| `naeilo.com/` | `index.html`: SEO 글 + `web/`으로 바로 보냄 | **입구 페이지 그 자체**. 보내기(redirect)를 없애고 SEO 정보는 이 페이지에 그대로 담는다 |
| `naeilo.com/web/` | 전체 웹 | 그대로 동작(개발자용·예전 사용자). 검색에서 빼고(`noindex`), 위에 "앱으로 옮겨 가요 · 입구로" 한 줄 띠 |
| `naeilo.com/web/privacy.html` | 개인정보처리방침 | **주소 그대로 유지** (App Store Connect에 등록된 주소일 수 있어 옮기지 않음). 입구 바닥에서 링크 |
| `naeilo.com/web/guide.html` | 웹 사용법 | 남기되 sitemap에서 뺌 (웹 사용법이라 입구와 안 맞음) |
| `naeilo.com/data/*` | 시세·통계 | 그대로. 앱·입구·CI가 계속 씀 |

입구 파일은 저장소 루트에 둔다: `index.html`, `entry.js`, `entry.css`, `img/app-*.png`. 계산은 `web/model.js`를 그대로 불러 쓴다(고치지 않음).

## 2. 페이지 구성 (위에서 아래로)

1. **머리**: naeilo 로고, 한 줄 약속, 앱 받기 버튼.
   - 약속(초안): "수량만 넣으면, 내 자산의 내일이 보여요." 아래 작은 글씨로 "See Tomorrow, Today."
   - 버튼 동작은 아래 6번 질문에 따라 정한다.
2. **앱 화면 3~4장**: 폰 틀 안 스크린샷, 폰에서는 옆으로 넘기기. 한 장에 한 줄 설명.
   - 홈(내 길): "목표까지, 본전까지 남은 길"
   - 목표일 전망: "가장 그럴듯한 금액과 10번 중 9번 범위"
   - 미션 판 1000칸: "매일 한 칸씩 쌓는 습관"
   - 인물·쉼터: "같이 가는 다섯 사람"
   - 스크린샷은 시뮬레이터에서 샘플 자료(DRNK·QQQ)로 찍는다. 금액이 보이면 샘플임을 표시.
3. **앱에서만 되는 것** (아이콘 + 한 줄, 4칸):
   - 인물 5명과 쉼터: 세리·시오·선배·이르·수아, 내 캐릭터 만들기
   - 미션과 인터미션: 1000칸 판, 4주·3달 쉬어 가기
   - 위젯: 홈·잠금화면에서 자산·목표·움직임
   - 서재: 외전과 1권 이야기
4. **해 보기 카드 한 장** (아래 3절)
5. **앱 받기** 한 번 더 + 짧은 안내 "종목을 사라·팔라는 조언이 아니라 비율과 확률로 보여 주는 관찰 도구예요."
6. **바닥**: 개인정보처리방침, 문의(메일), © naeilo.

화면 톤은 앱과 같은 어두운 남색(공유 카드 색 `#0e161e`·`#15202B`·민트 `#5FD0C4`)을 기본으로, 밝은 모드도 같이 둔다.

## 3. 해 보기 카드

입력 (CLAUDE.md 규칙대로 수량만 필수):
- 종목 + 수량 줄 (최대 5줄). 종목은 `data/tickers.json`·`web/stocks.js` 목록에서 찾기. 국내 종목은 사이트가 모으는 종목(`kr_stocks.json`)만.
- 선택 1: **산 돈**(평균 단가 또는 총액) → 본전 확률을 보여 줌
- 선택 2: **목표 금액** → 목표 확률 (목표일 기본값 3년 뒤, 숨김 옵션으로만 바꿈)

결과 (금액은 지금 평가액 하나, 나머지는 비율·확률):
- 지금 원화 평가액 (종가 × 수량 × 매매기준율 `data/mar.json`)
- 산 돈보다 아래면: "1년 안에 본전에 닿을 확률 N%, 3년 안 N%" (`Model.breakeven`, 앱 회복 길과 같은 6·12·36개월 중 12·36)
- 목표를 넣었으면: "목표일까지 목표에 닿을 확률 N%" (`Model.simulate`의 `p_goal`)
- 아래에 "앱에서는 매일 이 길을 이어서 보여 줘요" + 앱 받기

계산 규칙:
- `web/model.js`의 `buildModel` → `simulate` → `breakeven`을 그대로 쓴다. 설정은 `data/state.default.json`의 모형 기본값, 시세는 `data/prices/`, `data/quotes.json`.
- 같은 입력이면 앱 홈의 숫자와 같아야 한다 (앱도 같은 식, `mc_parity`가 지킴). 경로 수는 화면이 멈추지 않게 1,000~2,000으로, 계산은 화면 스레드를 막지 않게 나눠서 한다.
- 넣은 값은 이 브라우저(`localStorage`)에만 저장. 주소·서버로 보내지 않는다. 보유 종목을 주소에 싣는 공유 링크는 만들지 않는다.

## 4. 공유 링크가 닿는 곳

| 들어오는 길 | 닿는 곳 |
|---|---|
| 앱 공유 카드 이미지의 "naeilo.com" 글자 | 입구 |
| 검색·SNS 미리보기 (`og:url`, canonical) | 입구 `https://naeilo.com/` (og 이미지 새로) |
| 예전 북마크·검색 결과 `naeilo.com/web/` | 전체 웹 그대로 + 입구로 가는 띠 |
| `naeilo.com/web/#d=…` (기기 옮기기 링크) | 전체 웹 그대로 (개발자용) |
| `naeilo.com/web/?toss=…` (토스 중계 등록) | 전체 웹 그대로 (개발자용) |
| 앱 안 "PC naeilo.com에서 하세요" 안내 (거래 기록·증권사 파일·기기 동기화, 8곳) | 입구에는 그 기능이 없으므로 **앱 문구를 고친다** (3번 작업) |
| App Store 소개·지원 주소 | 입구 `https://naeilo.com/` |

나중 일(이번 범위 밖): 앱이 설치돼 있으면 앱을 여는 유니버설 링크(`apple-app-site-association`).

## 5. 루트 `index.html`(SEO) 다루기

- 보내기 두 줄(`meta refresh`, `location.replace`)을 지우고 입구 페이지를 직접 만든다.
- `title`·`description`·`keywords`는 앱 소개 문장으로 다시 쓴다 (예: "naeilo · 수량만 넣으면 보이는 내 자산의 내일 (iOS 앱)").
- `canonical`·`og:url`·JSON-LD `url`을 `https://naeilo.com/`로. JSON-LD 종류는 `WebApplication` → `MobileApplication`(`operatingSystem: iOS`, `applicationCategory: FinanceApplication`). 앱 출시 전에는 `offers`·`downloadUrl`을 넣지 않는다.
- 본문에 h1·설명 글이 실제로 보이게 (지금처럼 숨은 글이 아니라 화면 글이 곧 SEO 글).
- `sitemap.xml`: `/`(priority 1.0), `/web/privacy.html`만 남김. `/web/`, `/web/guide.html` 뺌.
- `robots.txt`: 그대로 두되 `/web/`은 막지 않는다 (막으면 noindex를 못 읽음). 대신 `web/index.html`에 `<meta name="robots" content="noindex">`.
- `og.png`: 앱 화면이 들어간 새 이미지로 바꾼다 (1200×630).
- 루트의 예전 웹 스크린샷(`d-n-*.png`, `m-n-*.png`, `share.png`)은 어디서 쓰는지 확인 후 안 쓰면 지운다.

## 6. 정할 것 (Drinker)

**앱 출시 전, "앱 받기" 버튼은 무엇을 하나?**
- 출시 알림 신청 (추천): 이메일을 받아 출시 때 한 번 알림. 워커에 `/notify` 하나 추가(KV `VIEWS`에 저장)와 개인정보처리방침 한 줄 추가가 필요. 워커 배포는 수동이라 Drinker가 한 번 해야 함.
- "곧 나와요" 글자만: 가장 간단. 받는 사람 목록이 안 남음.
- TestFlight 공개 링크: 바로 써 볼 수 있음. 외부 테스트 심사를 먼저 통과해야 함 (HANDOFF 출시 차단 항목).

출시 뒤에는 모두 App Store 링크로 바꾼다.

## 7. 다음 작업 나누기 (2·3번을 동시에)

두 스레드가 같은 파일을 건드리지 않게 나눴다. `web/model.js`는 **둘 다 고치지 않는다** (입구·앱·CI가 같이 씀).

### 2번: 입구 만들기
- 건드리는 파일: `index.html`(루트), 새 `entry.js`·`entry.css`, 새 `img/app-*.png`, `og.png`, `sitemap.xml`, (알림 신청이면) `proxy/cloudflare-worker.js`·`web/privacy.html`.
- 할 일: 페이지 틀과 문구 → 앱 스크린샷 4장(시뮬레이터, 샘플 자료) → 해 보기 카드(종목 찾기, 시세 읽기, `Model` 계산, 나눠 계산) → SEO 메타·sitemap·og 이미지 → 같은 입력으로 앱 홈 숫자와 비교 한 번.
- 크기: 새 코드 약 400~600줄(카드 계산 연결 250~350, 페이지·CSS 150~250) + 이미지. PR 1개. 알림 신청을 고르면 워커 50줄 안팎과 수동 배포가 더해짐.
- 어려운 곳: 지금 `web/app.js`의 `buildModelNow`가 시세·환율·민감도(`factorBetas`)를 모아 `buildModel`에 넘기는데, 이 묶는 부분은 app.js 안에 있어 그대로 못 가져온다. 입구용으로 작게 다시 쓴다 (app.js는 3번이 안 지우므로 참고만).

### 3번: 웹 정리
- 건드리는 파일: `web/index.html`(noindex, 띠, `?v=`), `web/app.js`(띠 정도만), `ios-app/`의 "PC naeilo.com" 안내 문구 8곳, `CLAUDE.md`, `docs/product.md`, `docs/architecture.md`.
- 할 일:
  - `web/` 검색 제외·입구로 가는 띠. 코드는 지우지 않는다: CI 도구(`check_calc.mjs`, `calc_parity.mjs`)가 `web/app.js`의 함수 원문(`dividends` 등)을 꺼내 쓰고, 앱의 `calc.json`이 그 검사를 거쳐 만들어진다.
  - 앱 문구: 거래 기록·증권사 파일·기기 동기화를 "PC naeilo.com"으로 보내는 안내를 지운다. 기본안: 기기 동기화 메뉴는 앱 설정에서 숨기고 코드는 남김.
  - 규칙 줄이기: CLAUDE.md의 "웹에 같은 화면이 있는 앱 숫자는 웹과 같아야" → "입구 카드 숫자는 앱 홈과 같아야, 계산 규칙(`model.js`·`calc.json`)은 웹·앱 같게"로. 화면 parity(웹 쉬운 화면 개편 따라가기)는 끝. `mc_parity`·`check_calc`는 유지, `calc_parity`는 앱 화면 계산이 `web/app.js` 원문과 같은지 보는 것이라 유지하되 웹 화면을 새로 만들 일은 없음.
  - `docs/plans/web-tabs.md` 남은 단계와 열린 PR(#218~#222 쌓인 것) 처리 방침을 HANDOFF에 적기.
- 크기: 대부분 문구·문서, 코드 변경 약 100~200줄. PR 1개. iOS는 빌드 확인 필요.
- 순서 주의: 3번의 띠가 가리키는 `naeilo.com/`은 2번이 합쳐지기 전까지 다시 `web/`으로 보낸다. 띠는 2번이 합쳐진 뒤 켜지게 하거나, 3번을 2번 뒤에 합친다.

### 함께 쓰는 문서
`docs/HANDOFF.md`·`docs/decisions.md`는 둘 다 고친다. 각자 자기 줄만 추가하고, 나중에 합치는 쪽이 최신 main 위에서 다시 맞춘다.
