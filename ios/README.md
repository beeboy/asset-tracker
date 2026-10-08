# naeilo 아이폰 위젯

사이트(naeilo.com)의 내 입력값으로 홈 화면·잠금 화면 위젯 11개를 보여주는 앱입니다.

| 크기 | 위젯 |
|---|---|
| 작은 | 자산 추이 · 미래 평가액(작은 부채꼴) · 1억 블록 |
| 중간 | 목표 페이스 · 이번 주 과녁 · 오늘의 움직임 |
| 큰 | 미래 평가액 추이 |
| 잠금 화면 | 자산 추이 · 목표 진행 · 3년 뒤 · 이번 주 과녁 |

## 데이터 흐름

- **로그인**: 사이트 설정 → 기기 자동 동기화의 **동기화 비밀번호**를 그대로 넣습니다. 비밀번호는 저장하지 않고, 풀 때 쓰는 키만 키체인에 둡니다. (개발자 기기 동기화를 쓰면 **GitHub 토큰**으로 로그인)
- **30분마다** (장중, 설정에서 1·3시간으로 바꿀 수 있음): `data/widget.json`(약 1.3KB)과 동기화 값을 ETag 로 묻고, 바뀐 것만 받습니다.
- **전망(미래·목표 확률·이번 주 예측)**: 사이트가 동기화할 때 계산 요약을 같이 올리고(1차), 앱은 사이트를 안 연 날 같은 파일(`web/model.js` + `web/widget-core.js`)로 직접 계산합니다(2차). 둘 중 종가 기준일이 늦은 쪽을 씁니다. 같은 파일·같은 시드라 숫자가 같습니다.
- 가격 이력(3년치)은 처음 한 번만 받고, 이후엔 `widget.json` 의 최근 10거래일로 이어 붙입니다.

## 맥에서 설치

1. `brew install xcodegen` 후 이 폴더에서 `xcodegen` → `Naeilo.xcodeproj` 를 엽니다.
2. 두 타깃(**Naeilo**, **NaeiloWidgets**) 모두 *Signing & Capabilities* 에서 Team 을 고릅니다.
   - 번들 ID(`com.naeilo.widget`)가 이미 쓰인다고 나오면 바꾸고, App Group(`group.com.naeilo.widget`)도 `project.yml` 과 `Shared/Config.swift` 에서 같이 바꾼 뒤 `xcodegen` 을 다시 실행합니다.
3. 아이폰을 연결하고 실행(▶). 처음이면 아이폰 *설정 → 개인정보 보호 및 보안 → 개발자 모드* 를 켭니다.
4. 앱에서 동기화 비밀번호를 넣고, 홈 화면을 길게 눌러 **+ → naeilo** 에서 위젯을 고릅니다.

## TestFlight 로 설치

유료 Apple Developer Program(연 $99) 계정이 있어야 합니다. 무료 개인 팀으로는 올릴 수 없습니다.

1. [App Store Connect](https://appstoreconnect.apple.com) → 앱 → **+ 새로운 앱**: 플랫폼 iOS, 번들 ID `com.naeilo.widget`, 이름은 겹치지 않게(예: "naeilo 위젯"), SKU 아무 글자.
   - 번들 ID가 목록에 없으면 Xcode 에서 Team 을 고르고 한 번 실행(▶)하면 자동으로 등록됩니다.
2. `xcodegen` 후 Xcode 에서 실행 대상을 **Any iOS Device (arm64)** 로 바꾸고 *Product → Archive*.
3. Organizer 창에서 **Distribute App → TestFlight & App Store → Distribute**. 빌드 번호는 Xcode 가 자동으로 올립니다.
4. 몇 분~수십 분 뒤 App Store Connect → TestFlight 탭에 빌드가 뜨면 **내부 테스트** 그룹을 만들고 자기 Apple ID 를 넣습니다.
5. 아이폰에 TestFlight 앱을 깔고, 메일로 온 초대에서 설치합니다.

수출 규정 질문은 `ITSAppUsesNonExemptEncryption = NO` 로 건너뛰고, 개인정보 매니페스트(`PrivacyInfo.xcprivacy`)는 앱·위젯에 들어 있습니다. TestFlight 빌드는 90일 뒤 만료되니 그 전에 다시 올립니다.

## 사이트·중계 쪽

- 사이트는 이 변경이 main 에 들어가면 동기화할 때 위젯 요약을 같이 올립니다.
- `proxy/cloudflare-worker.js` 를 Cloudflare 에 다시 배포하면 동기화 확인이 304(바뀐 것 없음)로 끝나 갱신이 더 가벼워집니다. 배포하지 않아도 동작합니다.
- 컴파일 확인: GitHub Actions `ios` (서명 없이 시뮬레이터 빌드).
