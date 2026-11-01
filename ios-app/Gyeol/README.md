# 결 (다이내믹 아일랜드·잠금 화면)

## 보류 (2026-10-31)
앱 빌드에서 뺐다. 소스·그림·`NaeiloGyeol` 타깃·중계(/gyeol)는 남겨 둔다. 맥 `~/.hammerspoon/init.lua` 의 `gyeolIsland = require("gyeol-island")` 줄도 주석으로 막았다.
- 뺀 것: `project.pbxproj`·`project.yml` 의 `NAEILO_GYEOL` 을 비움(→ `#if GYEOL` 세 곳과 GyeolGlue.swift 가 빠짐), NaeiloApp 의 NaeiloGyeol 의존성·Embed Foundation Extensions 의 NaeiloGyeol.appex·GyeolKit 링크. `Info.plist` 의 `NSSupportsLiveActivities`, `App.entitlements` 의 `aps-environment` 는 그대로 둠.
- 다시 붙이는 법: 위 셋을 되살린다(`NAEILO_GYEOL = GYEOL`, NaeiloApp 에 NaeiloGyeol 타깃 의존성·appex 넣기·GyeolKit 패키지 링크, project.yml 의 주석 두 줄 풀기). 맥은 init.lua 줄 주석을 풀고 Hammerspoon 다시 불러오기.
- 까닭
  - 작은 섬: 오른쪽 칸 너비를 iOS 가 약 96pt 로 막아 20pt 글자 다섯 자쯤만 보인다(160pt 상자는 앞글자가 카메라 쪽에서 잘림).
  - 큰 섬: 알림(alert)으로 펼쳐도 iOS 가 곧 닫아 말을 읽을 수 없다(뒤 푸시 없이도 닫힘, 기록으로 확인).
- 되는 것: 중계 /gyeol 로 섬 열쇠 자동 넘기기, APNs 푸시 200, 서 있는 결·얼굴 그림, 영어.

결 부분은 앱과 떼어 둔다. 공개 때 빼려면 아래만 지운다. 맥은 xcodegen을 쓰지 않으니 `project.yml`과 `project.pbxproj` 둘 다 고친다(xcodegen을 돌리면 워치 Info.plist가 지워져 설치가 막힌다).

1. 이 폴더 `ios-app/Gyeol/` (GyeolKit 패키지·결 위젯 확장·그림)
2. `ios-app/Naeilo/Gyeol/GyeolGlue.swift`
3. `project.pbxproj`에서 NaeiloGyeol 타깃·GyeolKit 패키지·GyeolGlue.swift·NAEILO_GYEOL·빌드 번호 스크립트의 결 블록. `Naeilo/Info.plist`의 `NSSupportsLiveActivities`, `Naeilo/App.entitlements`의 `aps-environment`
4. `project.yml`에서 `# 결` 표시가 붙은 줄: `packages`의 GyeolKit, 앱의 `GYEOL` 플래그·`NSSupportsLiveActivities`·`aps-environment`·의존성 두 줄, 앱 빌드 번호 스크립트의 결 블록, `NaeiloGyeol` 타깃
5. 앱 코드의 `#if GYEOL` 세 곳: `App.swift`(GyeolHooks), `SettingsView.swift`(GyeolSettings), `WidgetBridge.swift`(GyeolFeed.push)

## 얼개
- `GyeolAttributes`: 섬 상태. 자세·말·숫자·말풍선 줄·순번. 맥 푸시는 `pose`·`say`·`n`만 보내도 된다(줄은 App Group에 앱이 남긴 것).
- `GyeolLive`: 앱 쪽 조종. 띄우기·고치기·내리기, 푸시 열쇠.
- `GyeolLiveActivity`: 화면. 작은 섬은 얼굴과 오늘 등락, 펼친 섬·잠금 화면은 앉은 결과 말풍선.
- 그림: `Sources/GyeolKit/Resources/Gyeol.xcassets`. `gyeol_i_*`(섬, 흰 테두리)·`gyeol_l_*`(잠금)·`gyeol_f_*`(얼굴). 원본은 project-files `gyeol-line/scenes/svg`의 앉은 장면, 만드는 스크립트는 `gyeol-line/island/render.js`.

## 한도
- 섬은 한 번 띄우면 8시간 뒤 내려간다. 앱을 열면 다시 띄운다. 클로드 연동이 켜져 있으면 맥이 띄우기 열쇠로 다시 띄울 수 있다(iOS 17.2+).
- 앱이 뒤에 있을 때 앱은 섬을 못 고친다. 그때 바뀌는 건 맥 푸시뿐이다.
