# 아이폰 ↔ 안드로이드 짝 지도

Swift 파일을 옮긴 Kotlin 파일. `python3 tools/port_check/map.py` 로 다시 만든다 (Kotlin 줄 끝 `// ios:` 표시에서 뽑음).
Kotlin 경로는 `android-app/app/src/main/java/` 또는 `widget/src/main/java/` 아래.

| Swift | Kotlin (표시 수) |
|---|---|
| `App.swift` | `com/naeilo/app/App.kt` (72), `com/naeilo/app/MainActivity.kt` (7), `com/naeilo/app/AppModel.kt` (6), `com/naeilo/app/ui/AppHeader.kt` (6), `com/naeilo/app/ui/Controls.kt` (2), `com/naeilo/app/ui/Theme.kt` (1) |
| `Data/AppModel.swift` | `com/naeilo/app/AppModel.kt` (569) |
| `Data/Backup.swift` | `com/naeilo/app/net/Backup.kt` (39) |
| `Data/BrickGame.swift` | `com/naeilo/app/core/game/BrickGame.kt` (682) |
| `Data/Calm.swift` | `com/naeilo/app/core/calc/Calm.kt` (364) |
| `Data/Earnings.swift` | `com/naeilo/app/data/Earnings.kt` (23) |
| `Data/FlowData.swift` | `com/naeilo/app/data/FlowData.kt` (394) |
| `Data/ForecastEngine.swift` | `com/naeilo/app/core/calc/ForecastEngine.kt` (157) |
| `Data/GoalRoute.swift` | `com/naeilo/app/core/calc/GoalRoute.kt` (309) |
| `Data/Industries.swift` | `com/naeilo/app/core/calc/Industries.kt` (81) |
| `Data/Invest.swift` | `com/naeilo/app/core/calc/Invest.kt` (68) |
| `Data/InvestEvents.swift` | `com/naeilo/app/core/calc/InvestEvents.kt` (22) |
| `Data/Lines.swift` | `com/naeilo/app/data/Lines.kt` (42) |
| `Data/Lots.swift` | `com/naeilo/app/data/Lots.kt` (11), `com/naeilo/app/net/Market.kt` (5) |
| `Data/Market.swift` | `com/naeilo/app/net/Market.kt` (492) |
| `Data/Me.swift` | `com/naeilo/app/data/Me.kt` (106) |
| `Data/MonteCarlo.swift` | `com/naeilo/app/core/calc/MonteCarlo.kt` (546) |
| `Data/MyRecord.swift` | `com/naeilo/app/data/MyRecord.kt` (9) |
| `Data/Notifier.swift` | `com/naeilo/app/data/Notifier.kt` (129) |
| `Data/Parallel.swift` | `com/naeilo/app/core/calc/Parallel.kt` (523) |
| `Data/Progress.swift` | `com/naeilo/app/data/Progress.kt` (68) |
| `Data/Recovery.swift` | `com/naeilo/app/core/calc/Recovery.kt` (248) |
| `Data/Remind.swift` | `com/naeilo/app/data/Remind.kt` (378) |
| `Data/SampleData.swift` | `com/naeilo/app/data/SampleData.kt` (181) |
| `Data/Services.swift` | `com/naeilo/app/data/Services.kt` (51) |
| `Data/SiteCalc.swift` | `com/naeilo/app/core/calc/SiteCalc.kt` (63) |
| `Data/SiteStats.swift` | `com/naeilo/app/net/SiteStats.kt` (124) |
| `Data/StockCatalog.swift` | `com/naeilo/app/net/StockCatalog.kt` (126) |
| `Data/StockSearch.swift` | `com/naeilo/app/net/StockSearch.kt` (316) |
| `Data/StoreAPI.swift` | `com/naeilo/app/net/StoreAPI.kt` (27) |
| `Data/Story.swift` | `com/naeilo/app/data/Story.kt` (118) |
| `Data/Support.swift` | `com/naeilo/app/data/Support.kt` (107) |
| `Data/Sync.swift` | `com/naeilo/app/net/Sync.kt` (335) |
| `Data/WaveEngine.swift` | `com/naeilo/app/core/calc/WaveEngine.kt` (259) |
| `Data/WidgetBridge.swift` | `com/naeilo/app/data/WidgetBridge.kt` (82) |
| `Theme.swift` | `com/naeilo/app/ui/Theme.kt` (157), `com/naeilo/app/ui/Controls.kt` (4) |
| `Views/AnalysisDetail.swift` | `com/naeilo/app/ui/analysis/ExternalViews.kt` (369), `com/naeilo/app/ui/analysis/RateViews.kt` (327), `com/naeilo/app/ui/analysis/FxGlanceViews.kt` (293), `com/naeilo/app/ui/analysis/DividendViews.kt` (215), `com/naeilo/app/ui/analysis/MyPathViews.kt` (177), `com/naeilo/app/ui/analysis/AnalysisCommon.kt` (90), `com/naeilo/app/core/calc/GoalRoute.kt` (8) |
| `Views/AnalysisView.swift` | `com/naeilo/app/ui/analysis/AnalysisView.kt` (392), `com/naeilo/app/ui/analysis/AnalysisCommon.kt` (3), `com/naeilo/app/App.kt` (1) |
| `Views/BoardView.swift` | `com/naeilo/app/ui/board/BoardView.kt` (290) |
| `Views/BrickGameView.swift` | `com/naeilo/app/ui/game/BrickGameView.kt` (432) |
| `Views/CalmView.swift` | `com/naeilo/app/ui/calm/CalmView.kt` (133) |
| `Views/CharMotion.swift` | `com/naeilo/app/ui/CharMotion.kt` (416) |
| `Views/Charts.swift` | `com/naeilo/app/ui/Charts.kt` (70) |
| `Views/EarningsRiver.swift` | `com/naeilo/app/ui/hold/EarningsRiver.kt` (242), `com/naeilo/app/data/Earnings.kt` (8) |
| `Views/FlowView.swift` | `com/naeilo/app/ui/flow/FlowView.kt` (437) |
| `Views/ForecastParts.swift` | `com/naeilo/app/ui/analysis/ForecastParts.kt` (72) |
| `Views/GoalViews.swift` | `com/naeilo/app/ui/goal/GoalViews.kt` (513), `com/naeilo/app/ui/Controls.kt` (1) |
| `Views/Guide.swift` | `com/naeilo/app/ui/Guide.kt` (78) |
| `Views/HoldingsView.swift` | `com/naeilo/app/ui/town/TownViews.kt` (871), `com/naeilo/app/ui/hold/HoldingsView.kt` (355), `com/naeilo/app/ui/hold/HoldingDetail.kt` (269), `com/naeilo/app/ui/home/ThemeScenes.kt` (1) |
| `Views/HomeView.swift` | `com/naeilo/app/ui/home/HomeView.kt` (457), `com/naeilo/app/App.kt` (1) |
| `Views/InvestViews.swift` | `com/naeilo/app/ui/analysis/InvestViews.kt` (95) |
| `Views/MeViews.swift` | `com/naeilo/app/ui/me/MeViews.kt` (480) |
| `Views/MissionViews.swift` | `com/naeilo/app/ui/board/MissionViews.kt` (471) |
| `Views/MyRecordCard.swift` | `com/naeilo/app/ui/MyRecordCard.kt` (44) |
| `Views/ParallelView.swift` | `com/naeilo/app/ui/analysis/ParallelViews.kt` (215) |
| `Views/RemindViews.swift` | `com/naeilo/app/ui/remind/RemindViews.kt` (151) |
| `Views/SettingsDetail.swift` | `com/naeilo/app/ui/settings/SettingsDetail.kt` (311) |
| `Views/SettingsView.swift` | `com/naeilo/app/ui/settings/SettingsView.kt` (386) |
| `Views/ShareCard.swift` | `com/naeilo/app/ui/ShareCard.kt` (106) |
| `Views/ShelterView.swift` | `com/naeilo/app/ui/shelter/ShelterView.kt` (464) |
| `Views/StartView.swift` | `com/naeilo/app/ui/start/StartView.kt` (88) |
| `Views/SupportView.swift` | `com/naeilo/app/ui/support/SupportView.kt` (60) |
| `Views/ThemeScenes.swift` | `com/naeilo/app/ui/home/ThemeScenes.kt` (51) |
| `Views/TownPanel.swift` | `com/naeilo/app/ui/town/TownPanel.kt` (287) |
| `Views/Wave/SurfCloset.swift` | `com/naeilo/app/ui/wave/SurfCloset.kt` (162) |
| `Views/Wave/WaveFeedback.swift` | `com/naeilo/app/ui/wave/WaveFeedback.kt` (83), `com/naeilo/app/ui/WaveFeedbackToggles.kt` (9) |
| `Views/Wave/WaveHomeView.swift` | `com/naeilo/app/ui/wave/WaveHomeView.kt` (172) |
| `Views/Wave/WavePlayView.swift` | `com/naeilo/app/ui/wave/WavePlayView.kt` (309) |
| `Views/Wave/WaveResultPopup.swift` | `com/naeilo/app/ui/wave/WaveResultPopup.kt` (57) |
| `WidgetShared/Config.swift` | `com/naeilo/app/core/Config.kt` (48) |
| `WidgetShared/Engine.swift` | `com/naeilo/app/core/Engine.kt` (77) |
| `WidgetShared/Flip.swift` | `com/naeilo/app/core/Flip.kt` (6) |
| `WidgetShared/Format.swift` | `com/naeilo/app/core/Format.kt` (35) |
| `WidgetShared/Models.swift` | `com/naeilo/app/core/Feeds.kt` (396), `com/naeilo/app/core/Models.kt` (173) |
| `WidgetShared/Reward.swift` | `com/naeilo/app/core/Reward.kt` (68) |
| `WidgetShared/StoryWidgetData.swift` | `com/naeilo/app/core/StoryWidgetData.kt` (11) |
| `Widgets/CharWidgets.swift` | `com/naeilo/widget/CharViews.kt` (210), `com/naeilo/widget/CharWidgets.kt` (49), `com/naeilo/widget/Intent.kt` (1) |
| `Widgets/HomeWidgets.swift` | `com/naeilo/widget/HomeWidgets.kt` (46) |
| `Widgets/Intent.swift` | `com/naeilo/widget/Intent.kt` (30) |
| `Widgets/LockWidgets.swift` | `com/naeilo/widget/LockWidgets.kt` (6), `com/naeilo/widget/Glance.kt` (1) |
| `Widgets/Provider.swift` | `com/naeilo/widget/Provider.kt` (12) |
| `Widgets/StoryWidget.swift` | `com/naeilo/widget/StoryWidgetView.kt` (30), `com/naeilo/widget/StoryWidget.kt` (10) |
| `Widgets/Views/Catalog.swift` | `com/naeilo/widget/Catalog.kt` (37) |
| `Widgets/Views/Charts.swift` | `com/naeilo/widget/Charts.kt` (95) |
| `Widgets/Views/HomeViews.swift` | `com/naeilo/widget/HomeViews.kt` (130) |
| `Widgets/Views/Locked.swift` | `com/naeilo/widget/Locked.kt` (14) |
| `Widgets/Views/Theme.swift` | `com/naeilo/widget/Theme.kt` (41) |

<!-- 아래는 손으로 쓰는 칸 -->

## 부품 대응표

안드로이드 이름은 아이폰과 같게 쓴다 (화면 `XxxView`, 계산·상수는 같은 이름의 `object XxxView`).
아래는 SwiftUI 가 기본으로 주는 것이라 Swift 쪽 이름이 없어 안드로이드에서 따로 만든 부품이다. 옮길 때 왼쪽을 보면 오른쪽을 쓴다.

| SwiftUI | 안드로이드 (`ui/Controls.kt` 등) | 메모 |
|---|---|---|
| `Text` | `T` | 색을 안 주면 LocalFg |
| `Text(AttributedString)` | `TA` | 굵게 등 꾸민 글 |
| `.foregroundStyle(색)` | `Fg` | 안쪽 글자 기본색 |
| `.lineLimit(1).minimumScaleFactor` | `FitText` |  |
| `ViewThatFits(in: .horizontal)` | `FitsOr` |  |
| `Image(systemName:)` | `Sym` | 기호 표 `tools/port_check/symbols.md` |
| `(표에 없는 SF 기호)` | `SymOr` | 비슷한 글자로 대신 |
| `Divider()` | `HDivider` |  |
| `TextField·SecureField` | `InputField` |  |
| `Toggle(isOn:)` | `ToggleRow` | 스위치는 IosSwitch |
| `(iOS 스위치 모양)` | `IosSwitch` | 51×31 |
| `Slider(value:in:step:)` | `IosSlider` |  |
| `ProgressView()` | `Spinner` |  |
| `.alert` | `IosAlert` |  |
| `.confirmationDialog` | `ConfirmSheet` |  |
| `.contextMenu` | `ContextMenu` |  |
| `Menu { Button… }` | `DropMenu` |  |
| `.sheet` | `Sheet` | 맨 위 줄은 SheetBar |
| `navigationTitle + ToolbarItem(.topBarTrailing)` | `SheetBar` | 시트 맨 위 줄 |
| `.fullScreenCover` | `WaveCover` |  |
| `NavigationStack 이동 막대(inline)` | `NavBar` |  |
| `NavigationStack 스와이프 백 · .sheet 끌어내리기` | `rememberBackSwipe` | NavStack·Sheet 가 쓴다 |
| `.refreshable` | `Refreshable` |  |
| `.contentTransition(.numericText) (Charts.swift rollText)` | `RollText` |  |
| `Theme.swift 화면 바탕(242)` | `Screen` |  |
| `Image(이름).interpolation(.none)` | `PixelN` | 도트 그림 |
| `Image(이름) (매끈하게)` | `SmoothImage` | 앱 아이콘 미리보기 등 |
| `AnalysisDetail.swift footnote·pinnedBox` | `Footnote·PinnedBox` | Swift는 private func |
| `AnalysisDetail.swift:155 상자·170 두 칸 격자` | `KpiBox·KpiGrid` | Swift는 이름 없는 VStack·Grid |
| `ParallelView.swift:198 규칙 한 줄(HStack)` | `RuleStep` | Swift는 이름 없음 |
| `GoalSetView(fromSettings: true)` | `GoalSetPage` | 전망에서 여는 길 (GoalSetView 를 부름) |
| `Widgets 미리보기 그리기` | `FaceCanvas·HomeWidgetPreview·StoryWidgetPreview` | 위젯 쪽 |
