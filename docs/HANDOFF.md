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

- 결이 위아래 걷기: 저장소 파일 없음(맥 ~/.hammerspoon/gyeol.lua, 결의 자리 아티팩트 bake-v77)
- 사용량 앱 아이콘 주황 게이지: usage-app/UsageApp/Assets.xcassets/AppIcon.appiconset/icon.png, icon-dark.png, icon-tinted.png
- 홈 말풍선 1분 질문 대사 지우기: ios-app/Naeilo/Views/HomeView.swift, android-app/app/src/main/java/com/naeilo/app/ui/home/HomeView.kt
- AI 스위치 하나로 끄기(계좌 탭 AI 조언·AI 매매 자리·AI 알약·부르기, MockAI.on): ios-app/Naeilo/Mock/MockAI.swift, MockDesk.swift, MockOrderViews.swift, MockViews.swift (안드로이드 짝 없음)
- 투자 모드 설명 문구(평가액 눌러 탭 바꾸기): ios-app/Naeilo/Views/SettingsView.swift, ios-app/Naeilo/Localizable.xcstrings (port_check 짝 없음 627 · 참조 이상 114, 원래부터. 안드로이드엔 이 문구 없음)
