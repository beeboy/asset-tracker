# naeilo 아이폰 앱

시안(목업 49판)을 SwiftUI 로 옮긴 아이폰 앱입니다. 기존 위젯 앱(`ios/`)의 위젯 11개를 이 앱으로 합쳤습니다(번들 ID `com.naeilo.widget`, App Group `group.com.naeilo.widget`). 위젯은 로그인 없이, 앱이 App Group 폴더에 써 둔 숫자(summary·feed·live.json)를 읽어 그립니다(`Naeilo/Data/WidgetBridge.swift`).

인물 보상 위젯 3개(본전 진행·블록·오늘의 움직임, `Widgets/CharWidgets.swift`)는 위젯마다 인물을 고르고, 아직 못 만난 인물은 실루엣으로 보입니다. 숫자는 앱이 쓰는 reward.json 입니다. 설정 > 시안 조작 > 인물 위젯 미리보기에서 다섯 인물을 기본·다크로 한 번에 볼 수 있습니다. 설정의 앱 아이콘은 기본(내일의 별)과 인물 5개(`AppIcon-*`, 다크·틴트 포함) 중에서 고릅니다.

## 실행

```
brew install xcodegen
cd ios-app && xcodegen
open NaeiloApp.xcodeproj   # 시뮬레이터 고르고 ▶
```

## 들어 있는 화면

- 홈: 어제 종가 기준 평가액·본전 진행, 쉼터 박스(홈에 둔 친구), 인터미션 리마인드, 오늘의 1분
- 쉼터: 친구 5명(홈에 두기), 서재(외전 프롤로그~5장, 5장 애니메이션 표지), 돌아온 물건 10개
- 서재 읽기: 글자 크기 3단계, 어두운 화면, 친구 첫 등장 표시, 다음 장
- 종목: 전체 평가액 그래프 + 기간 칩(1일~3년), 종목 목록, 종목 상세(소개 카드), 종목 추가(검색)
- 분석: 카드 목록, 3년 전망(렌즈 칩·외부 요인·추세 믿음·월 적립 슬라이더, 해마다 확률). 나머지 카드는 자리만
- 미션: 회복 루트 판(끝낸 미션 접기), 인터미션 주차 체크인(친구가 열림), 1000칸, 100칸 선물, 이번 주 예보
- 설정: 목록, 시세 기준, 시안 조작(인터미션 1주차로 / 모든 화면 열기)

## 스텁

- 시세: `Data/Services.swift` 의 `PriceProvider`. 지금은 시안과 같은 예시 값(`StubPriceProvider`). DRNK 는 가상 종목이고 가격 흐름은 지난 자산 기록 비율(`Resources/drnk_ratio.json`).
- 외전 원고: `StoryProvider`. 원고는 앱에 넣지 않고 서버에서 받는 전제라, 지금은 본문 자리(회색 줄)만.

시작 탭은 `-tab hold|analysis|board|settings` 실행 인자로 고를 수 있습니다(캡처용).

## 글자 크기

아이폰 설정 > 디스플레이 및 밝기 > 텍스트 크기(손쉬운 사용의 더 큰 텍스트 포함)를 따릅니다. 시안의 글자 크기를 기본 크기 기준으로 두고 같은 비율로 키웁니다(`Theme.swift` 의 `appFont`). 손쉬운 사용 3단계까지 반영하고, 아주 큰 글자에서는 위에 고정된 그래프도 함께 스크롤됩니다.

캡처용 실행 인자: `-demo fresh`(미션 1부터), `-route m1|m1r|m2|m2r|m3|m3r|m4|m4r|nx`(미션 화면 바로 열기).

## 빌드 메모

- 저장소가 iCloud Drive(문서 폴더) 안에 있으면, 명령줄에서 `-derivedDataPath` 를 저장소 안에 두었을 때 위젯 확장 서명이 "resource fork … not allowed" 로 실패합니다. Xcode 기본 위치(~/Library/Developer/Xcode/DerivedData)로 빌드하면 괜찮습니다.
- 테스트 시세는 저장소의 `data/prices`·`data/quotes.json`(Yahoo 중계)을 그대로 넣습니다.
