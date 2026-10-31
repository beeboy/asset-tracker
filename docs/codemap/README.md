# 코드 지도

작업 전에 소스를 다 읽지 말고 여기부터 본다.

- `ios.md` — 아이폰(`ios-app/Naeilo`·`WidgetShared`·`Widgets`·`Watch`): 폴더별 파일·줄 수, 파일별 최상위 선언, 파일 관계
- `android.md` — 안드로이드(`android-app/app/src/main`·`android-app/widget/src/main`, 테스트 뺌): 같은 세 부분. 위젯 모듈 경로는 `w/`로 시작
- 두 파일은 스크립트가 쓴다. 손으로 고치지 않는다.

## 갱신

```bash
python3 docs/codemap/gen.py
```

표준 라이브러리만 쓴다. 파일을 더하거나 옮긴 뒤 돌린다.

## 주요 파일 역할 (파일 머리를 열어 확인한 것만)

아이폰
- `Naeilo/App.swift` — 앱 시작·탭(`Tab`)·바탕 화면(`RootView`)·팝업 틀
- `Naeilo/Theme.swift` — 색(오르면 빨강·내리면 파랑, 다크 모드)·카드·버튼 등 공용 꾸밈
- `Naeilo/Data/AppModel.swift` — 앱 상태와 계산. 설명 문구도 계산값으로 만든다
- `Naeilo/Data/Market.swift` — 지금 시세. 내 오픈 API 키로 받고 없으면 사이트 값
- `Naeilo/Data/MonteCarlo.swift` — 전망 몬테카를로. `web/model.js`와 같은 결과
- `Naeilo/Data/Lines.swift` — 인물 대사(`Resources/lines_*.json`) 고르기
- `Naeilo/Views/HoldingsView.swift` — 종목 탭(오늘 박스·구성 그래프·목록)
- `Naeilo/Views/AnalysisDetail.swift` — 분석 탭 상세(내 길·금리·외부 요인·배당·환율)
- `Naeilo/Mock/MockDesk.swift` — 모의투자 저장 상태·실행기. 장부를 바꾸는 유일한 곳
- `Naeilo/Mock/MockEngine.swift` — 모의투자 판단·체결 계산(화면·네트워크 없음)
- `WidgetShared/Models.swift` — 앱·위젯 공용 위젯 요약 모델(`data/widget.json`)
- `Widgets/Provider.swift` — 위젯 타임라인(그리기·갱신 시각)
- `Watch/Shared/WatchSync.swift` — 아이폰 → 워치 위젯 값 보내기

안드로이드 (`com/naeilo/app/` 아래, 짝은 `docs/android-map.md`)
- `App.kt` — 탭 순서·앱 바탕(`RootView`). App.swift 짝
- `AppModel.kt` — 앱 상태와 계산. 저장은 SharedPreferences("naeilo")
- `net/Market.kt` — 지금 시세. 받기는 Dispatchers.IO
- `core/calc/MonteCarlo.kt` — 전망 몬테카를로. 웹·아이폰과 비트 단위로 같게
- `data/WidgetBridge.kt` — 앱 계산값을 위젯 파일(summary·feed·live.json)로 넘김
- `ui/town/TownViews.kt` — 동네(보유 종목 주민·대사창)
- 위젯 모듈 `widget/.../com/naeilo/app/core/` — 아이폰 `WidgetShared` 자리(패키지 이름은 앱과 같음), `widget/.../com/naeilo/widget/` — 위젯 화면(Glance)

## 여기 두지 않는 것

수정할 위치·의심 지점은 작업마다 달라 여기 두지 않고 작업 기록(`docs/HANDOFF.md` 등)에 적는다.

## 지도가 놓치는 것

정규식으로 줄 맨 앞만 본다. 들여쓴 선언(중첩 타입·멤버 함수), Kotlin 최상위 `val`, 문자열 안 `"""` 여러 줄 글, 두 파일에 같은 이름으로 선언된 타입(관계에서 뺌), 타입 이름을 쓰지 않는 호출(전역 함수·확장 함수만 부르는 경우)은 관계에 안 잡힌다.
