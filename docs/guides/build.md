# 빌드·검사·옮김

## 빌드
- iOS: `cd ios-app && xcodebuild -project NaeiloApp.xcodeproj -target NaeiloApp -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build 2>&1 | grep -E "error|FAIL" | head -50`
- 명령줄 빌드는 Xcode와 다른 `-derivedDataPath`를 쓴다.
- 안드로이드: `cd android-app && ./gradlew assembleDebug`. 끝나면 `./gradlew --stop`.
- 안드로이드 출시판엔 lint가 없다. 필요하면 `./gradlew lint`.
- 여러 스레드가 동시에 빌드하지 않는다.
- `ios.yml`은 손으로만 돌린다(맥 러너 분당 10배).

## Xcode 프로젝트
- 새 Swift 파일·타깃·패키지는 `project.yml`과 `project.pbxproj` 둘 다에 손으로 넣는다.
- xcodegen 금지. 워치 Info.plist·entitlements가 비어 설치가 막힌다(10-31).
- 빌드 번호는 자동 증가다.

## 아이폰 → 안드로이드 옮김
- iOS가 원본이다. 안드로이드가 따른다.
- Kotlin 줄 끝 `// ios: 파일:줄` 이름표를 지킨다.
- 따라 옮기기: `python3 tools/port_check/follow.py <옛 커밋>`.
- 짝 지도: `docs/android-map.md`(`tools/port_check/map.py`로 만듦).
- 안드로이드 화면 이름은 아이폰과 같다(XxxView).
- 화면 비교: Roborazzi 1.77·Robolectric 4.17.
- 못 맞추면 `docs/todo/known.md`에 적는다.
- 안드로이드 성능 이상은 빌드 종류(디버그·릴리즈)부터 본다. 디버그는 약 160배 느리다.

## 숫자 검사
- 빠뜨림: `tools/port_check/`.
- 숫자·데이터: `tools/num_parity/`, `tools/json_parity/`.
- 입구 전망: `tools/mc_parity.mjs`(`web/model.js` = `MonteCarlo.swift`).
- 사이트 계산: `node tools/site_calc.mjs && node tools/check_calc.mjs`.

## 문구·영어
- 원문은 한국어(String Catalog, ko). 언어는 iOS 앱별 설정을 따른다.
- 위젯: `WL.t("한국어")` + `ios-app/tools/lines.py` `WIDGET_UI`, 그다음 `python3 tools/lines.py`.
- 안드로이드 영어: `python3 tools/l10n/gen_en.py`.
- 영어 금액은 ₩와 K·M·B(₩2.25M). 서식은 `krwC` 한 곳.

## 안드로이드 버전
- compileSdk·targetSdk 37, activity-compose 1.13.0.
- Compose 1.11.4(BOM 2026.06.01). 1.12.1은 크래시 버그(b/549552303)다. 1.13 안정판이 나오면 올린다.
- 출시판 R8: `proguard-rules.pro`에 WorkDatabase_Impl 생성자를 남긴다.
