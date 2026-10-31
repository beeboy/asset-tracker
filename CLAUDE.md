# naeilo (asset-tracker)

자산 관찰·미래 분석 앱이다. 종목 조언은 아니다.
수량만 넣으면 원화 평가액·목표 길·확률을 보여 준다.

## 기술 스택
- iOS: Swift·SwiftUI, 위젯·워치. 원본이다.
- 안드로이드: Kotlin·Compose. iOS를 따르는 복제다.
- 입구 웹: 정적 HTML·JS(GitHub Pages).
- 서버: `server.py`(표준 라이브러리만), Cloudflare 워커(`proxy/`), Lightsail 중계.
- 데이터: `data/` JSON(봇 커밋), GitHub Actions 수집.

## 폴더
- `ios-app/Naeilo/` 앱, `WidgetShared/` 공용, `Widgets/` 위젯. `NaeiloApp.xcodeproj`.
- `android-app/app/` 앱, `android-app/widget/` 위젯.
- 루트 `index.html`·`entry.*`·`img/` 입구.
- `web/` 예전 웹(검색 제외). CI가 `web/app.js` 함수 원문을 쓴다.
- `proxy/` 워커·중계, `tools/` CI·검사, `data/` 사이트 데이터, `usage-app/` 개인 앱.

## 핵심 규칙
- 앱 빌드·검사는 맥에서 끝낸다.
- iOS를 먼저 고친다. 안드로이드가 따른다.
- 화면에 "종목+금액+시점" 조언 금지.
- 사용자에게 토큰·설정을 묻지 않는다.
- 비밀값은 시크릿에만 둔다.
- 주석·UI 문구·커밋 메시지는 한국어다.
- `web/` JS/CSS를 고치면 `web/index.html`의 `?v=`를 올린다.
- 앱 작업이 끝나면 `docs/HANDOFF.md`에 백업 줄 하나. 남은 일은 `docs/todo/`, 결정은 `docs/decisions.md`에 한 줄.

## 읽지 말 것
- `data/` 시세·통계 JSON, `web/stocks.js`, `Resources/*.json`, `*.xcassets`, 이미지, `*.pbxproj` 전체, `build/`·`DerivedData/`·`node_modules/`.
- 큰 파일은 grep으로 필요한 줄만 본다.

## 필요할 때 읽는 문서
- 빌드·옮김·영어: `docs/guides/build.md`
- 시세·환율·계산·실적: `docs/guides/data.md`
- 분석 화면 계산(전망·금리·외부 요인·미션): `docs/guides/analysis.md`
- 화면·위젯·구독: `docs/guides/ui.md`
- 서버·배포·백업: `docs/guides/deploy.md`
- 모의투자: `docs/guides/mock.md`
- 파일 찾기: `docs/codemap/`
- iOS↔안드로이드 짝: `docs/android-map.md`
- 토스 중계: `docs/toss-relay.md`
- 백업 대기 줄: `docs/HANDOFF.md`
- 남은 일·불일치·보류: `docs/todo/`
- 결정 기록: `docs/decisions.md`

## md 문서 쓰는 법 (새로 쓰거나 고치는 모든 md)
- 헤딩과 리스트로 쓴다.
- 문장은 짧게, 결론만 쓴다. 불필요한 문장은 뺀다.
- 한 문서는 한 주제다. 길어지면 보조 문서로 나누고 이 목차에 한 줄 더한다.
