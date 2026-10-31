# 인수인계 (HANDOFF)

- 여기엔 백업 대기 줄만 둔다.
- 남은 일과 불일치는 필요할 때 아래 문서를 읽는다.

## 필요할 때 읽는 문서
- 출시·운영 남은 일: `docs/todo/release.md`
- 화면·기능 남은 일: `docs/todo/app.md`
- 모의투자 남은 일: `docs/todo/mock.md`
- 알려진 불일치: `docs/todo/known.md`
- 지난 기록: git 3c452a72 판

## 백업 대기 ("백업해" 때 이 줄의 파일만 커밋)

- 홈·미션 말풍선 새 한마디 (2026-10-29): 파일 적힌 것 없음 (10-23 "브리핑 한 문장" 결정을 바꿈)
- 영어 앱 대사 (2026-10-29): 파일 적힌 것 없음
- 동네 판 밖 오른쪽 아래 전체 평가액·오늘 등락 점수판 (2026-10-29): ios-app/Naeilo/Data/AppModel.swift, ios-app/Naeilo/Views/HoldingsView.swift, android-app/app/src/main/java/com/naeilo/app/AppModel.kt, android-app/app/src/main/java/com/naeilo/app/ui/hold/HoldingsView.kt, android-app/app/src/main/java/com/naeilo/app/ui/town/TownViews.kt
- 모의투자 훑기 단추를 '+ 조건' 줄 오른쪽에, 다음 훑기 안내 (2026-10-29): ios-app/Naeilo/Mock/MockViews.swift, ios-app/Naeilo/Mock/MockDesk.swift, ios-app/Naeilo/Views/HomeView.swift
- 아이폰 동네 말풍선 글자 11→14, 폭은 실제 글꼴로 재서 줄마다 (2026-10-29): ios-app/Naeilo/Views/HoldingsView.swift
- 동네 대사 끝 '비밀이에요' 줄이기 (2026-10-29): ios-app/Naeilo/Views/TownPanel.swift, android-app/app/src/main/java/com/naeilo/app/ui/town/TownPanel.kt
- 동네 말풍선 판 밖으로 안 나가게 옆으로 밀기 + 워시·나가 옛 (2026-10-29): ios-app/Naeilo/Views/HoldingsView.swift, android-app/app/src/main/java/com/naeilo/app/ui/town/TownViews.kt
- 동네 한 줄 남기기 고맙다는 말 12가지로 돌아가며 (2026-10-29): ios-app/Naeilo/Views/TownPanel.swift, ios-app/Naeilo/Views/HoldingsView.swift, ios-app/Naeilo/Localizable.xcstrings, android-app/app/src/main/java/com/naeilo/app/ui/town/TownPanel.kt, android-app/app/src/main/java/com/naeilo/app/ui/town/TownViews.kt, android-app/app/src/main/assets/l10n_en.json
- 종목 탭 묶음·지도 다듬기 (2026-10-29): ios-app/Naeilo/Views/HoldingsView.swift, ios-app/Naeilo/Views/AnalysisDetail.swift, ios-app/Naeilo/Localizable.xcstrings, android-app/app/src/main/java/com/naeilo/app/ui/hold/HoldingsView.kt, android-app/app/src/main/java/com/naeilo/app/ui/analysis/FxGlanceViews.kt, android-app/app/src/main/assets/l10n_en.json, docs/android-map.md
- 실전 매도 이유 보이기·옛 종목 보호 (2026-10-29): ios-app/Naeilo/Mock/MockModels.swift, ios-app/Naeilo/Mock/MockEngine.swift, ios-app/Naeilo/Mock/MockDesk.swift, ios-app/Naeilo/Mock/MockViews.swift, ios-app/Naeilo/Mock/MockOrderViews.swift, ios-app/tools/mock_test/main.swift, docs/decisions.md
- 매매 뒤 흐름 AI 조언 (2026-10-29): ios-app/Naeilo/Mock/MockAI.swift, ios-app/Naeilo/Mock/MockDesk.swift, ios-app/Naeilo/Mock/MockModels.swift, ios-app/Naeilo/Mock/MockOrderViews.swift, ios-app/Naeilo/Mock/MockViews.swift
- 관찰 새 배수 알약을 곡선 하루만 있어도 손으로 켜기 (2026-10-30): ios-app/Naeilo/Mock/MockViews.swift, ios-app/Naeilo/Mock/MockDesk.swift
- 걸린 종목 뒤 흐름 안 산 종목 쌓기 (2026-10-30): ios-app/Naeilo/Mock/MockDesk.swift, ios-app/Naeilo/Mock/MockOrderViews.swift
- 분석 탭 '어떻게 올릴까?' 목표일 금액 비교 카드 (2026-10-30): ios-app/Naeilo/Views/AnalysisView.swift, ios-app/Naeilo/Data/GoalRoute.swift, ios-app/Naeilo/Localizable.xcstrings, android-app/app/src/main/java/com/naeilo/app/ui/analysis/AnalysisView.kt, android-app/app/src/main/java/com/naeilo/app/core/calc/GoalRoute.kt, android-app/app/src/main/assets/l10n_en.json, tools/port_check/exceptions.txt, docs/android-map.md
- 홈 말풍선에서 이어 온 날·쉼터 물건 줄 빼기 (2026-10-30): ios-app/Naeilo/Views/HomeView.swift, android-app/app/src/main/java/com/naeilo/app/ui/home/HomeView.kt, tools/port_check/exceptions.txt
- 결 머리 손보기 (2026-10-30): 저장소 파일 없음 (결의 자리 45판)
- 결 섬 (2026-10-31): ios-app/Gyeol/, ios-app/Naeilo/Gyeol/GyeolGlue.swift, ios-app/project.yml, ios-app/NaeiloApp.xcodeproj/project.pbxproj, ios-app/Naeilo/Info.plist, ios-app/Naeilo/App.entitlements, ios-app/Naeilo/App.swift, ios-app/Naeilo/Views/SettingsView.swift, ios-app/Naeilo/Data/WidgetBridge.swift
- 결 섬 열쇠 자동 넘기기 (2026-10-31): ios-app/Naeilo/Gyeol/GyeolGlue.swift
- 결 섬 그림 크게 (2026-10-31): ios-app/Gyeol/Sources/GyeolKit/GyeolActivity.swift
- 결 섬 그림 키우기 (2026-10-31): ios-app/Gyeol/Sources/GyeolKit/GyeolActivity.swift, ios-app/Gyeol/Sources/GyeolKit/GyeolPose.swift, ios-app/Gyeol/Sources/GyeolKit/Resources/Gyeol.xcassets/
- 결 설정·말풍선 영어 (2026-10-31): ios-app/Naeilo/Gyeol/GyeolGlue.swift, ios-app/Naeilo/Localizable.xcstrings, ios-app/Gyeol/Sources/GyeolKit/GyeolActivity.swift, ios-app/Gyeol/Sources/GyeolKit/GyeolAttributes.swift
- 결 섬 말은 작은 섬 얼굴 옆 글자로 (2026-10-31): ios-app/Gyeol/Sources/GyeolKit/GyeolLive.swift, ios-app/Gyeol/Sources/GyeolKit/GyeolActivity.swift
- 다이내믹 아일랜드 결 보류 (2026-10-31): ios-app/NaeiloApp.xcodeproj/project.pbxproj, ios-app/Gyeol/README.md, ios-app/project.yml
- 결 작은 창 (2026-10-31): ios-app/Naeilo/Gyeol/GyeolPip.swift, ios-app/Naeilo/Gyeol/GyeolBake.swift, ios-app/Naeilo/Gyeol/GyeolDraw.swift, ios-app/Naeilo/Gyeol/gyeol-bake.json, ios-app/NaeiloApp.xcodeproj/project.pbxproj, ios-app/project.yml, ios-app/Naeilo/Info.plist, ios-app/Naeilo/Views/SettingsView.swift
- 결 걸터앉기를 기획(sit2)대로 (2026-10-31): ios-app/Naeilo/Gyeol/gyeol-bake.json
- 결 반바지를 사진 참고로 하이웨이스트 A라인 데님으로 다시 그림 (2026-10-31): ios-app/Naeilo/Gyeol/gyeol-bake.json
