# naeilo 아이폰 앱

시안(목업 49판)을 SwiftUI 로 옮긴 아이폰 앱입니다. 기존 위젯 앱(`ios/`)의 위젯 11개를 이 앱으로 합쳤습니다(번들 ID `com.naeilo.widget`, App Group `group.com.naeilo.widget`). 위젯은 로그인 없이, 앱이 App Group 폴더에 써 둔 숫자(summary·feed·live.json)를 읽어 그립니다(`Naeilo/Data/WidgetBridge.swift`).

인물 보상 위젯 3개(본전 진행·블록·오늘의 움직임, `Widgets/CharWidgets.swift`)는 위젯마다 인물을 고르고, 아직 못 만난 인물은 실루엣으로 보입니다. 숫자는 앱이 쓰는 reward.json 입니다. 설정 > 시안 조작 > 인물 위젯 미리보기에서 다섯 인물을 기본·다크로 한 번에 볼 수 있습니다. 설정의 앱 아이콘은 기본(내일의 별)과 인물 5개(`AppIcon-*`, 다크·틴트 포함) 중에서 고르고, 인물 아이콘은 그 인물을 만나야(쉼터와 같은 단계) 열립니다.

## 실행

```
open ios-app/NaeiloApp.xcodeproj   # 시뮬레이터 고르고 ▶
```

커밋된 `project.pbxproj`로 빌드한다. xcodegen은 돌리지 않는다(워치 Info.plist가 지워져 설치가 막힌다). 새 파일·타깃은 `project.yml`과 `project.pbxproj` 둘 다에 손으로 넣는다.

## 들어 있는 화면

- 홈: 어제 종가 기준 평가액·본전 진행, 쉼터 박스(홈에 둔 친구), 인터미션 리마인드, 오늘의 1분
- 쉼터: 친구 5명(홈에 두기), 서재(외전 프롤로그~5장, 5장 애니메이션 표지), 돌아온 물건 10개
- 서재 읽기: 외전 원고(한글·영문, 기기 언어가 기본이고 한/EN 으로 바꿈), 글자 크기 3단계, 어두운 화면, 친구 첫 등장 표시, 다음 장, 읽던 자리 기억
- 종목: 전체 평가액 그래프 + 기간 칩(1일~3년), 종목 목록, 종목 상세(소개 카드), 종목 추가(검색)
- 분석: 카드 목록, 3년 전망(렌즈 칩·외부 요인·추세 믿음·월 적립 슬라이더, 해마다 확률). 나머지 카드는 자리만
- 미션: 회복 루트 판(끝낸 미션 접기), 인터미션 주차 체크인(친구가 열림), 1000칸, 100칸 선물, 이번 주 예보
- 설정: 목록, 시세 기준, 시안 조작(인터미션 1주차로 / 모든 화면 열기)

## 스텁

- 시세: `Data/Services.swift` 의 `PriceProvider`. 지금은 시안과 같은 예시 값(`StubPriceProvider`). DRNK 는 가상 종목이고 가격 흐름은 지난 자산 기록 비율(`Resources/drnk_ratio.json`).

시작 탭은 `-tab hold|analysis|board|settings` 실행 인자로 고를 수 있습니다(캡처용).

## 글자 크기

아이폰 설정 > 디스플레이 및 밝기 > 텍스트 크기(손쉬운 사용의 더 큰 텍스트 포함)를 따릅니다. 시안의 글자 크기를 기본 크기 기준으로 두고 같은 비율로 키웁니다(`Theme.swift` 의 `appFont`). 손쉬운 사용 3단계까지 반영하고, 아주 큰 글자에서는 위에 고정된 그래프도 함께 스크롤됩니다.

캡처용 실행 인자: `-demo fresh`(미션 1부터), `-route m1|m1r|m2|m2r|m3|m3r|m4|m4r|nx`(미션 화면 바로 열기).

## 빌드 메모

- 저장소가 iCloud Drive(문서 폴더) 안에 있으면, 명령줄에서 `-derivedDataPath` 를 저장소 안에 두었을 때 위젯 확장 서명이 "resource fork … not allowed" 로 실패합니다. Xcode 기본 위치(~/Library/Developer/Xcode/DerivedData)로 빌드하면 괜찮습니다.
- 테스트 시세는 저장소의 `data/prices`·`data/quotes.json`(Yahoo 중계)을 그대로 넣습니다.

## 알림과 종목 찾기 (14차)

- 알림(`Naeilo/Data/Notifier.swift`): 기기 안에서 예약하는 로컬 알림. 본전 도달·비중 이탈은 앱이 시세를 받을 때 확인해 하루 한 번까지, 아침 한 줄은 평일 정한 시각, 연말 절세 확인은 12월 1일. 앱 시작 3단계의 2단계(알림 켜기)는 실제 아이폰 알림 허용을 받는다. 앱이 꺼져 있을 때 시세를 보고 보내는 알림은 서버 푸시가 필요해 아직 없다.
- 종목 찾기(`Naeilo/Data/StockSearch.swift`, `StockCatalog.swift`): 사이트가 쓰는 Yahoo 중계로 미국 주식·ETF와 코스피·코스닥을 찾는다. Yahoo 가 한글 이름을 못 찾아서 자주 찾는 118종목의 한글 이름을 앱에 넣었다(코드는 2026-10-10 중계로 하나씩 확인). 추가한 종목은 3년 일별 종가를 기기에 저장하고 5분마다 갱신한다.

## 진행 저장 (15차)

- `Naeilo/Data/Progress.swift`: 미션 판·인터미션·오늘의 1분·쉼터 읽은 장·목표 설정·분석 선택을 기기에 저장하고, 앱을 열 때 불러온다. 안 연 날수만큼 오늘의 1분 날짜를 넘긴다.
- 캡처용 실행 인자(-demo, -route, -tab 등)를 주면 저장값을 읽지도 쓰지도 않는다. 설정 > 시안 조작 버튼은 저장값을 바꾼다.

## 기기 동기화 (16차)

- `Naeilo/Data/Sync.swift`: 사이트(naeilo.com 설정 > 기기 자동 동기화)와 같은 방식. 동기화 비밀번호 → PBKDF2-SHA256 30만 번(소금 naeilo-sync-v1) → 저장 위치 id + AES-GCM 키. 중계 `/esync` 에는 암호화한 값만 둔다. 키는 키체인(이 기기에서만).
- 종목은 사이트 holdings 형식(한국 종목 005930.KS)으로 주고받고, 앱의 미션 진행과 가상 종목은 `app` 칸에 둔다. 사이트만 쓰는 칸(목표·사건·모형)과 앱이 못 다루는 종목(예: 7203.T)은 받은 그대로 다시 올린다.
- 앱을 열 때 받고, 바뀌면 4초 모아서 보낸다. 이번에 켠 뒤 한 번 받기 전에는 보내지 않는다. 처음 켤 때 양쪽 다 값이 있으면 어느 쪽에 맞출지 묻는다.
- 확인: 사이트 web/app.js 의 암호화 코드를 그대로 옮긴 Node 스크립트로 사이트 → 앱, 앱 → 사이트 양쪽을 확인했다.
- 개발자 동기화: 설정 > 기기 동기화 > 개발자용에 GitHub 토큰(저장소 쓰기 권한)을 넣으면 사이트 개발자 동기화와 같은 칸(`/sync`, 계정별, 암호화 없음)을 쓴다. 토큰은 키체인에만.
- 보낼 때 사이트 줄을 잃지 않는다: 앱이 못 다루는 종목, 수량 0 줄, 시세를 못 받은 종목은 그대로, 사이트에 단가가 비어 있던 줄은 비워 둔 채로, 줄 순서와 메모·수동 가격도 그대로.

## 서재 원고 (17차)

- 외전 『이종 공명』 프롤로그~5장(사이트 공개 범위)을 앱에 넣었다: `Naeilo/Resources/story_ko.json`·`story_en.json`. MyVault 원고 폴더의 `side_story_v2.1.md`·`Side Story - Resonance Across Kinds.md` 에서 `python3 tools/story.py <한글.md> <영문.md>` 로 만든다(원고가 바뀌면 다시 돌린다).
- 두 언어는 블록(문단·인용·절 제목·표)이 하나씩 맞는다. 스크립트가 확인하고, 안 맞으면 멈춘다. 그래서 언어를 바꿔도 읽던 자리가 같다.
- 언어(`Naeilo/Data/Story.swift`): 기기 언어 목록의 첫 번째가 한국어면 한글, 아니면 영문. 읽기 화면의 한/EN 으로 바꾸면 기억하고, 기기 언어 쪽을 다시 고르면 '기기 언어 따라가기'로 돌아간다. 앱 UI(버튼·안내문)는 아직 한국어만.
- 읽던 자리: 장마다 화면 맨 위 블록 번호를 이 기기에만 저장한다.
- 서재 위젯(`Widgets/StoryWidget.swift`, 작은·중간): 마지막으로 연 장의 읽던 자리 한 문단과 진행 막대. 누르면 `naeilo://read/<장>` 으로 그 장이 열린다. 서재가 아직 안 열렸으면 앱 시작 3단계로.
- 6장부터는 앱에 넣지 않는다. 나중에 인앱 구매(커피 후원)를 확인한 뒤 서버에서 받는다.

## 개발자 후원 (18차)

- 커피 4등급(믹스·다방·프랜차이즈·고급 스페셜티)은 비소모성 인앱 구매, '한 잔 더'는 소모성 팁(`Naeilo/Data/Support.swift`, 화면 `Views/SupportView.swift`). 서재의 6장을 누르거나 설정 > 개발자 후원에서 연다. 구매 복원 버튼이 늘 있다.
- 상품 ID: `com.naeilo.widget.support.mix|dabang|franchise|specialty|tip`. App Store Connect 에 같은 ID 로 만들고, 등급 4개는 가족 공유를 켠다(한 번 켜면 끌 수 없음). 윗등급은 정가.
- 앱은 가진 비소모성 중 가장 높은 등급만 본다(`Transaction.currentEntitlements`, 환불된 것 제외, 가족 공유 포함). 구매 상태는 기기 동기화로 넘기지 않는다.
- 믹스커피 이상: 못 만난 친구 미리 보기(`AppModel.peekFriends`). 다방커피 이상: 안 돌아온 물건 미리 보기(`peekItems`)와 내 캐릭터 쓰기. 프랜차이즈 이상: 쉼터 배경 변경. 홈에 두기·열기는 여전히 미션으로.
- 내 캐릭터(쉼터 '본인' 칸, `Data/Me.swift`·`Views/MeViews.swift`): 헤어스타일·색깔·얼굴형·눈·입·윗옷·아래옷·안경·마스크·소품을 골라 만들고(저장 칸 1개, 이름은 공백 하나 포함 6자), 미션 진행에 같이 저장돼 내 폰 저장·불러오기와 동기화로 옮겨진다. 앱 시작 3단계부터 만들 수 있고, ① 회복 루트 인터미션 4주 모두(또는 목표 루트 4주) ② 평정 지수 매칭 ③ 다방커피 이상을 채우면 홈·위젯(인물 위젯 '내 캐릭터', 홈 인물 따라가기)에 기본 인물 대신 나온다. 말투는 투자 유형 인물(매칭 전에는 고른 유형). 부품 그림은 `me_<layer>_<NN>`(24×34) 에셋, 없으면 자리표시 도트. 위젯에는 앱이 겹쳐 그린 me.png 를 넘긴다.
- 인터미션 날짜: 회복 루트 주차 체크인은 시작한 날부터 7일마다 열리고, 날짜는 naeilo.com 응답 시각(`TrustedClock`)에 기기가 켜진 뒤 흐른 시간을 더해 센다. 폰 시간을 바꾸면(5분 넘게 다르면) 체크인이 막힌다.
- 개발자 전체 해제(`devAll`)는 Xcode·TestFlight 빌드에서만 동작한다(`Support.testBuild`, AppTransaction 환경). App Store 빌드에서는 꺼진다.
- 로컬 테스트: `ios-app/Support.storekit` 을 Xcode 의 Scheme > Edit Scheme > Run > Options > StoreKit Configuration 에 고르면 App Store Connect 없이 구매·복원·환불을 시험할 수 있다.

## 후원 원고·후원자 이름 (19차)

- `Naeilo/Data/StoreAPI.swift`: 중계 워커의 `/store/*` (PR #38). 가진 구매의 JWS(`Support.jws`, 가족 공유 포함)를 보내면 서버가 등급을 확인한다. 개발자 빌드에서 개발자 동기화 토큰이 있으면 `Authorization: Bearer` 로 구매 없이 받는다.
- 외전 6장~코다(믹스커피 이상, 한글·영문)와 본편 1권(프랜차이즈 이상, 한국어만)은 서재에서 처음 열 때 받아 `Application Support/story/side_ko.json` 처럼 저장하고, 다음부터는 바로 연다. 블록 형식은 story_*.json 과 같고 외전은 한·영 블록이 맞아서 언어를 바꿔도 자리가 같다. 읽던 자리 키는 외전 장 번호, 본편 1권은 1000 + 장 번호.
- 서재: 5장 다음에 받은 장이 이어지고(아직이면 '받기'), 본편 1권은 서재 아래 따로. 후원 등급이 모자라면 6장 줄·5장 끝에서 후원 화면이 열린다.
- 후원자 이름: 고급 스페셜티 커피 이상은 후원 화면에서 이름(40자)과 로고(긴 변 256px, 200KB 이하로 줄임)를 보낸다. 개발자가 store-admin 에서 승인하면 도움말(사용 방법)과 후원 화면의 '후원해 주신 분들'에 보인다.
- 워커 다시 배포와 원고 올리기(naeilo.com/web/store-admin.html)를 해야 실제로 받아진다. 그 전에는 '원고가 아직 올라오지 않았어요'.

## 받은 장 그림 (20차)

- 외전 6장~코다와 본편 1권의 장별 도트 그림은 유료라 저장소에 넣지 않는다. MyVault `원고/chapter-art/` 에 두고, store-admin 이 원고와 같이 읽어 128×80 으로 줄여 장마다 `cover`(PNG data URL)로 올린다.
- 앱은 받은 장의 `cover` 를 서재 줄과 읽기 화면 맨 위에 보여 준다 (`Story.cover`). 그림이 없는 장은 예전처럼 6장 그림 또는 빈 칸. 그림이 들어간 원고를 받으려면 store-admin 에서 다시 올린 뒤 서재의 '다시 받기'(또는 캐시가 없을 때 처음 받기).

## TestFlight 내부 테스트

- 번들 ID: 앱 `com.naeilo.widget`, 위젯 `com.naeilo.widget.widgets`, 앱 그룹 `group.com.naeilo.widget`. 팀 `252W43H2KE`, 자동 서명.
- 버전은 `project.yml` 의 `MARKETING_VERSION`(1.0), 빌드 번호는 `CURRENT_PROJECT_VERSION`. 앱·위젯 Info.plist 가 같은 값을 읽는다. 업로드 때 Xcode 가 겹치는 빌드 번호를 자동으로 올린다.
- 암호화 신고: `ITSAppUsesNonExemptEncryption = NO` (HTTPS 만 씀). 개인정보 매니페스트: 앱 `Naeilo/PrivacyInfo.xcprivacy`, 위젯 `Widgets/PrivacyInfo.xcprivacy`.
- 올리기: Xcode 에서 기기를 Any iOS Device (arm64) 로 고르고 Product > Archive → Organizer 의 Distribute App → App Store Connect (내부·외부 테스트 둘 다 됨. "TestFlight Internal Only" 로 올린 빌드는 외부 테스트에 못 씀).
- 인앱 구매(후원)는 App Store Connect 에 상품을 만들어야 TestFlight 에서 시험된다 (위 상품 ID).
