# naeilo (asset-tracker)

자산 관찰 및 미래 분석 서비스입니다. 웹(naeilo.com)과 iOS 앱(naeilo)이 있습니다. 사용자가 보유 수량만 넣으면 원화 평가액, 목표·본전까지의 길, 확률 전망을 보여 줍니다. 종목별 조언이 아니라 거시 관찰 도구입니다.

## 폴더 지도
| 위치 | 내용 |
|---|---|
| `index.html` | 루트 SEO 페이지, `web/`으로 보냄 |
| `web/` | 웹 전체 (번들러 없음). `app.js` 화면 로직(섹션 주석 `// ----` 로 구분), `model.js` 계산(`window.Model`, 몬테카를로), `charts.js` SVG 차트, `research.js` 연구 비교, `alloc-worker.js` 무거운 계산, `sw.js` 서비스워커, `stocks.js` 종목 목록(생성 파일) |
| `web/index.html` | 탭 5개: 내 길(dash)·전망·종목·기록·설정. 스크립트 `?v=` 판 번호 |
| `server.py` | 로컬 서버(`/api/*`) + Yahoo 시세 수집(`--collect`) + 매매기준율(`data/mar.json`) |
| `macro.py` | Shiller 데이터 → `data/macro/` |
| `proxy/` | Cloudflare 워커 `asset-ai` (`cloudflare-worker.js`, `wrangler.toml`) |
| `tools/` | CI 도구: `site_stats.mjs`→stats.json, `site_calc.mjs`/`check_calc.mjs`→calc.json, `mc_parity.mjs`, `sample_snapshot.mjs`, `drnk_prices.py`, `gen_stock_catalog.py` |
| `data/` | 사이트가 읽는 데이터 (시세·환율·통계·설정). 수집 봇이 커밋 |
| `research/` | 오프라인 Node 연구 스크립트 (앙상블 전망) |
| `ios-app/Naeilo/` | iOS 앱. `Data/` 모델·계산·동기화, `Views/` 화면, `Resources/` 대사·이야기 |
| `ios-app/WidgetShared/` | 앱과 위젯이 함께 쓰는 코드 (`Engine.swift`, `Models.swift`, `Config.swift`) |
| `ios-app/Widgets/` | 위젯 확장 (홈·잠금화면·인물·서재) |
| `ios-app/tools/mc_parity/` | Swift 몬테카를로를 웹과 비교하는 CLI |
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
- iOS 빌드: `cd ios-app && xcodegen && xcodebuild -project NaeiloApp.xcodeproj -target NaeiloApp -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`
- 단위 테스트 프레임워크·린터는 없음.

## 코딩 규칙
- 주석·UI 문구·커밋 메시지는 한국어, 쉬운 말.
- 웹 JS/CSS를 고치면 `web/index.html`의 `?v=YYYYMMDDx`를 반드시 올린다 (alloc-worker도 같은 판 사용).
- 웹: IIFE + `"use strict"`, 전역 `window.X`, 상태는 `S` 한 객체. 빈 catch는 `/* 무시 */`.
- 웹에 같은 화면이 있는 앱 숫자는 웹 기본 옵션 결과와 같아야 한다. 계산을 고치면 양쪽과 parity 도구를 같이 고친다.
- 새 Swift 파일은 `project.yml`뿐 아니라 커밋된 `project.pbxproj`에도 손으로 넣는다 (Mac은 xcodegen을 돌리지 않음).
- 화면에 "종목 + 금액 + 시점" 조언을 쓰지 않는다. 비율·확률로만 보여 준다.
- 사용자에게 토큰·개발 설정을 묻지 않는다. 입력은 수량만, 나머지는 기본값과 숨김 옵션.
- 비밀값은 Actions/워커 시크릿에만. 저장소에 쓰지 않는다.

## 작업 규칙
- 빌드·테스트 출력은 실패 부분만 보이게 줄인다 (예: `2>&1 | grep -E "error|FAIL" | head -50`, `tail -40`).
- 읽지 말 것: `data/prices/`, `data/quotes.json`, `data/stats.json`, `data/calc.json`, `data/sample_calc.json`, `data/beyora.json`, `data/app_news.json`, `data/macro/`, `web/stocks.js`, `ios-app/Naeilo/Resources/*.json`, `*.xcassets`, `*.png`, `*.pbxproj` 전체 읽기(필요한 줄만 grep), `node_modules/`, `build/`, `DerivedData/`, `Pods/`.
- `web/app.js`(약 3,500줄)는 통째로 읽지 말고 함수 이름으로 grep한다.
- 작업이 끝나면 `docs/HANDOFF.md`의 상태(완성·진행 중·버그·다음 순서)를 갱신한다.
- 결정이 생기면 `docs/decisions.md`에 날짜·이유와 함께 한 줄 추가한다.

## 상세 문서 (docs/)
- `docs/product.md`: 서비스 목적, 타깃 사용자, 웹·앱 기능 목록
- `docs/architecture.md`: 웹·앱·워커 구조, 데이터 흐름, 계산 위치와 웹·앱 계산 기준이 갈라지는 지점, 외부 API·저장 방식
- `docs/decisions.md`: 주요 설계 결정과 이유 (확인 안 된 것은 TODO)
- `docs/HANDOFF.md`: 완성·진행 중 기능, 알려진 버그·불일치, 다음 작업 우선순위
