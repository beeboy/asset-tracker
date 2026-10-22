# naeilo (asset-tracker)

자산 관찰·미래 분석 서비스. 중심은 iOS 앱, 웹 naeilo.com은 소개·해 보기 카드·앱 받기만 있는 입구. 보유 수량만 넣으면 원화 평가액·목표까지의 길·확률 전망을 보여 주는 거시 관찰 도구(종목 조언 아님).

## 폴더
- `ios-app/Naeilo/` 앱, `ios-app/WidgetShared/` 앱·위젯 공용, `ios-app/Widgets/` 위젯. Xcode는 `NaeiloApp.xcodeproj`(‘NaeiloApp 3’은 예전 복사본).
- 입구: 루트 `index.html`·`entry.*`·`img/`. `web/`은 개발자용 예전 웹(검색 제외). CI 도구가 `web/app.js` 함수 원문을 꺼내 쓰니 함수를 지우거나 이름을 바꾸면 `tools/`도 고친다.
- `server.py` 로컬 서버·시세 수집, `proxy/` Cloudflare 워커, `tools/` CI 도구, `data/` 사이트 데이터(봇이 커밋), `usage-app/` 별도 개인 앱.

## 빌드·검사
- iOS: `cd ios-app && xcodebuild -project NaeiloApp.xcodeproj -target NaeiloApp -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build 2>&1 | grep -E "error|FAIL" | head -50`
- 웹 로컬: `python3 server.py --no-browser` (8765). 계산 검사: `node tools/site_calc.mjs && node tools/check_calc.mjs`
- 웹↔앱 계산 비교: `tools/mc_parity.mjs`, `tools/calc_parity.mjs` (`.github/workflows/ios.yml` 참고)
- 앱 빌드·검사는 맥에서 끝낸다. `ios.yml`(맥 러너, 분당 비용 10배)은 push·PR(백업)에서 돌지 않는다. 매일 stats 작업 뒤 parity 와 손으로 돌리기만 남긴다. 시동을 다시 넣지 않는다.

## 작업 규칙
- 큰 파일은 통째로 읽지 말고 필요한 줄만 grep한다. 읽지 말 것: `data/` 아래 시세·통계·calc·macro JSON, `web/stocks.js`, `Resources/*.json`, `*.xcassets`, 이미지, `*.pbxproj` 전체, `build/`·`DerivedData/`·`node_modules/`.
- 코드를 고친 답에는 커밋 해시·브랜치를 한 줄 적는다. 완료라고 말하기 전에 결과를 직접 확인한다.
- 작업이 끝나면 `docs/HANDOFF.md` 상태를, 결정이 생기면 `docs/decisions.md`에 날짜·이유를 한 줄 적는다.

## 코딩 규칙
- 주석·UI 문구·커밋 메시지는 한국어.
- 계산 규칙과 데이터 사용은 웹·앱이 같게 둔다. 한쪽을 고치면 다른 쪽과 parity 도구도 고치고, 못 맞추면 HANDOFF.md 알려진 불일치에 적는다.
- 새 Swift 파일은 `project.yml`과 `project.pbxproj` 둘 다에 넣는다(맥은 xcodegen 안 씀). 빌드 번호는 자동 증가.
- 위젯 문구는 `WL.t("한국어")` + `ios-app/tools/lines.py`의 `WIDGET_UI`에 영어 추가 후 `python3 tools/lines.py`.
- `web/` JS/CSS를 고치면 `web/index.html`의 `?v=` 판 번호를 올린다.
- 화면에 "종목+금액+시점" 조언을 쓰지 않는다(비율·확률만). 사용자에게 토큰·설정을 묻지 않는다(입력은 수량만).
- 비밀값은 Actions/워커 시크릿에만 둔다.

## 상세 문서
`docs/product.md`, `docs/architecture.md`, `docs/decisions.md`, `docs/HANDOFF.md` — 필요할 때만 읽는다.
