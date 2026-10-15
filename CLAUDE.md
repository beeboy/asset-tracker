# naeilo (asset-tracker)

자산 관찰 및 미래 분석 서비스입니다. 중심은 iOS 앱(naeilo)이고, 웹 naeilo.com은 앱 소개와 해 보기 카드 한 장, 앱 받기만 있는 입구입니다 (2026-10-15 결정). 예전 전체 웹(`web/`)은 개발자용으로 남기고 검색에서 뺐습니다. 사용자가 보유 수량만 넣으면 원화 평가액, 목표·본전까지의 길, 확률 전망을 보여 줍니다. 종목별 조언이 아니라 거시 관찰 도구입니다.

## 폴더 지도
| 위치 | 내용 |
|---|---|
| `index.html` | 루트 SEO 페이지, `web/`으로 보냄 |
| `web/` | 예전 전체 웹, 개발자용 (번들러 없음, 검색 제외). CI 도구가 `app.js` 함수 원문을 꺼내 쓰니 함수를 지우지 않는다. `app.js` 화면 로직(섹션 주석 `// ----` 로 구분), `model.js` 계산(`window.Model`, 몬테카를로), `charts.js` SVG 차트, `research.js` 연구 비교, `alloc-worker.js` 무거운 계산, `sw.js` 서비스워커, `stocks.js` 종목 목록(생성 파일) |
| `web/index.html` | 개발자용 전체 웹 (`noindex`). 탭: 홈·종목·분석·설정. 스크립트 `?v=` 판 번호 |
| `server.py` | 로컬 서버(`/api/*`) + Yahoo 시세 수집(`--collect`) + 매매기준율(`data/mar.json`) |
| `macro.py` | Shiller 데이터 → `data/macro/` |
| `proxy/` | Cloudflare 워커 `asset-ai` (`cloudflare-worker.js`, `wrangler.toml`), 토스 공용 중계 `toss_relay.py`(고정 IP 서버, `docs/toss-relay.md`) |
| `tools/` | CI 도구: `site_stats.mjs`→stats.json, `site_calc.mjs`/`check_calc.mjs`→calc.json, `mc_parity.mjs`, `calc_parity.mjs`, `sample_snapshot.mjs`, `drnk_prices.py`, `gen_stock_catalog.py`, `sample_market.py`(키 등록 전 가상 시장 샘플 → `data/sample/`) |
| `data/` | 사이트가 읽는 데이터 (시세·환율·통계·설정). 수집 봇이 커밋 |
| `research/` | 오프라인 Node 연구 스크립트 (앙상블 전망) |
| `ios-app/Naeilo/` | iOS 앱. `Data/` 모델·계산·동기화, `Views/` 화면, `Resources/` 대사·이야기 |
| `ios-app/WidgetShared/` | 앱과 위젯이 함께 쓰는 코드 (`Engine.swift`, `Models.swift`, `Config.swift`) |
| `ios-app/Widgets/` | 위젯 확장 (홈·잠금화면·인물·서재) |
| `ios-app/tools/mc_parity/`, `calc_parity/` | 앱 Swift 계산(몬테카를로·평가액·배당·세금·금리)이 웹 계산 원문(`model.js`·`app.js` 함수)과 같은지 보는 CLI |
| `usage-app/` | 별도 개인용 앱: Claude 사용량 위젯 |
| `.github/workflows/` | collect·stats·macro·sample(자동), worker(수동), ios·usage(빌드) |

## 기술 스택
- 웹: 바닐라 JS/HTML/CSS, 모듈·번들러·package.json 없음. GitHub Pages + Cloudflare DNS.
- 서버·수집: Python 3 표준 라이브러리 (`macro.py`만 `xlrd`). CI 도구는 Node 22.
- 워커: Cloudflare Workers + KV(`VIEWS`), wrangler.
- iOS: SwiftUI, WidgetKit, AppIntents, StoreKit 2, iOS 17+, xcodegen(`project.yml`).

## 실행·빌드·테스트
- 웹 로컬: `python server.py --no-browser` → http://127.0.0.1:8765 (실패 시 정적 모드로 `data/` 직접 읽음)
- 시세 수집만: `python server.py --collect [--add T1,T2]`
- 계산 검사: `node tools/site_calc.mjs && node tools/check_calc.mjs`
- 웹↔Swift 몬테카를로 비교: `node tools/mc_parity.mjs gen DIR` → swiftc로 `ios-app/tools/mc_parity` 실행 → `node tools/mc_parity.mjs cmp DIR` (`.github/workflows/ios.yml` parity 잡 참고)
- 앱 계산↔웹 계산 원문 비교: `node tools/calc_parity.mjs gen DIR` → `swiftc DIR/CalcSrc.swift ios-app/tools/calc_parity/main.swift` 실행 → `node tools/calc_parity.mjs cmp DIR`. 앱·웹 원본에서 코드를 꺼내 쓰므로 함수 이름을 바꾸면 도구의 꺼내는 곳도 고친다
- iOS 빌드: `cd ios-app && xcodegen && xcodebuild -project NaeiloApp.xcodeproj -target NaeiloApp -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`
- 단위 테스트 프레임워크·린터는 없음.

## 코딩 규칙
- 주석·UI 문구·커밋 메시지는 한국어, 쉬운 말.
- `web/`(개발자용) JS/CSS를 고칠 때만 `web/index.html`의 `?v=YYYYMMDDx`를 올린다 (alloc-worker도 같은 판 사용). 입구 파일은 입구의 규칙을 따른다.
- 웹: IIFE + `"use strict"`, 전역 `window.X`, 상태는 `S` 한 객체. 빈 catch는 `/* 무시 */`.
- 입구 해 보기 카드 숫자는 같은 입력의 앱 홈 숫자와 같아야 한다. 계산 규칙(`web/model.js`·`calc.json`·`app.js` 계산 함수)은 웹·앱이 같게 두고, 고치면 양쪽과 parity 도구를 같이 고친다. 웹 화면을 앱 화면에 맞춰 새로 만들지는 않는다.
- 앱과 웹은 기본적으로 같은 데이터를 같은 규칙으로 쓴다 (시세·지난 종가·환율·기록 등 어디서 받아 어떻게 쓰는지). 한쪽만 바꾸지 않는다. 한 PR에서 못 맞추면 `docs/HANDOFF.md` 알려진 불일치에 적고 다음 작업으로 맞춘다.
- 새 Swift 파일은 `project.yml`뿐 아니라 커밋된 `project.pbxproj`에도 손으로 넣는다 (Mac은 xcodegen을 돌리지 않음).
- 화면에 "종목 + 금액 + 시점" 조언을 쓰지 않는다. 비율·확률로만 보여 준다.
- 사용자에게 토큰·개발 설정을 묻지 않는다. 입력은 수량만, 나머지는 기본값과 숨김 옵션.
- 비밀값은 Actions/워커 시크릿에만. 저장소에 쓰지 않는다.

## 작업 규칙
- 사용자가 직접 할 일(링크 열기·명령 실행 등)은 바로 복사해 쓸 수 있는 완성된 값으로 답에 적는다. 사용자가 알려 준 값(주소 등)은 채워 넣고, 지난 스레드를 찾게 하지 않는다. 단, 공개 저장소 파일에는 그 값을 쓰지 않는다.
- 사용자에게 주는 링크는 코드 블록에 넣지 않고 누르면 바로 열리는 링크로 쓴다. `naeilo://`처럼 채팅에서 안 눌리는 앱 링크는 누를 수 있는 https 주소로 감싸서 준다.
- 코드를 고친 답에는 커밋 해시·push한 브랜치·PR 링크·머지 여부를 한 줄로 꼭 적는다 (예: "커밋 abc1234 push 완료 (브랜치 X), PR #N CI 대기").
- "됐다", "띄웠다"고 말하기 전에 결과를 직접 확인한다 (화면이면 바뀐 내용이 실제로 나오는지 curl·스크린샷으로). 한 번에 맞게 한다.
- 바뀐 것은 머지를 기다리지 않고 바로 웹·앱에서 볼 수 있게 띄워 준다. 웹 미리보기: `web/`만 바꿨으면 `server.py`, 루트(`index.html`·`entry.*`·`img/`)를 바꿨으면 저장소 루트에서 `python3 -m http.server PORT` (앱은 `/web/`). 다른 세션과 겹치지 않게 PR 브랜치는 따로 worktree로 꺼낸다.
- 시킨 작업은 묻지 않고 커밋·push·draft PR까지 하고, CI가 초록이면 바로 머지한다 (쌓인 PR은 아래부터).
- 빌드·테스트 출력은 실패 부분만 보이게 줄인다 (예: `2>&1 | grep -E "error|FAIL" | head -50`, `tail -40`).
- 읽지 말 것: `data/prices/`, `data/sample/prices/`, `data/quotes.json`, `data/stats.json`, `data/calc.json`, `data/sample_calc.json`, `data/beyora.json`, `data/app_news.json`, `data/macro/`, `web/stocks.js`, `ios-app/Naeilo/Resources/*.json`, `*.xcassets`, `*.png`, `*.pbxproj` 전체 읽기(필요한 줄만 grep), `node_modules/`, `build/`, `DerivedData/`, `Pods/`.
- `web/app.js`(약 3,500줄)는 통째로 읽지 말고 함수 이름으로 grep한다.
- 작업이 끝나면 `docs/HANDOFF.md`의 상태(완성·진행 중·버그·다음 순서)를 갱신한다.
- 결정이 생기면 `docs/decisions.md`에 날짜·이유와 함께 한 줄 추가한다.
- 한 스레드는 기능·화면 하나만 다룬다. 요청 범위가 넓으면("전체", "끝까지" 등) 바로 작업하지 말고 작은 단위로 나누는 안을 먼저 제안한다.
- 한 스레드에서 코드 수정(PR)은 한 번만 한다. 다시 고칠 일이나 새 문제가 생기면 그 스레드에서 이어 하지 않고 새 스레드에서 한다. 어기면 100자 안팎의 반성문을 사용자에게 보여 준다.
- 코드 변경 없는 분석·기획·긴 논의는 일반 채팅에서 하도록 제안하고, 결론이 나오면 docs/에 저장한다.
- 같은 오류 수정이 2~3회 반복되면 멈추고, 원인 후보와 시도한 내용을 정리해 HANDOFF.md에 남긴 뒤 새 스레드를 제안한다.
- 맥락이 많이 쌓였다고 판단되면 HANDOFF.md를 갱신하고 새 스레드로 넘어가길 제안한다.
- 작업이 끝나면 docs/HANDOFF.md 상태를 갱신한다.

## 상세 문서 (docs/)
- `docs/product.md`: 서비스 목적, 타깃 사용자, 웹·앱 기능 목록
- `docs/architecture.md`: 웹·앱·워커 구조, 데이터 흐름, 계산 위치와 웹·앱 계산 기준이 갈라지는 지점, 외부 API·저장 방식
- `docs/decisions.md`: 주요 설계 결정과 이유 (확인 안 된 것은 TODO)
- `docs/HANDOFF.md`: 완성·진행 중 기능, 알려진 버그·불일치, 다음 작업 우선순위
