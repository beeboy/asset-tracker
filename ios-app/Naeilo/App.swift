import SwiftUI
import UserNotifications

@main
struct NaeiloApp: App {
    @State private var model = AppModel()
    init() { UNUserNotificationCenter.current().delegate = NotificationDelegate.shared }
    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(model.appearance == "light" ? .light : model.appearance == "dark" ? .dark : nil)
                // 아주 큰 손쉬운 사용 크기에서도 화면이 무너지지 않게 상한을 둔다
                .dynamicTypeSize(...DynamicTypeSize.accessibility3)
        }
    }
}

enum Tab: Hashable { case home, hold, analysis, board, settings }

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var phase
    var body: some View {
        @Bindable var model = model
        let showStart = !model.onboarded
        TabView(selection: $model.tab) {
            NavigationStack(path: $model.homePath) { HomeView() }
                .tabItem { Label("홈", systemImage: "house") }.tag(Tab.home)
            NavigationStack(path: $model.holdPath) { HoldingsView() }
                .tabItem { Label("종목", systemImage: "chart.bar") }.tag(Tab.hold)
            NavigationStack(path: $model.analysisPath) { AnalysisView() }
                .tabItem { Label("분석", systemImage: "chart.line.uptrend.xyaxis") }.tag(Tab.analysis)
            NavigationStack(path: $model.boardPath) {
                if model.isGoal { GoalBoardView() } else { BoardView() }
            }
                .tabItem { Label("미션", systemImage: "square.grid.2x2") }.tag(Tab.board)
            NavigationStack(path: $model.settingsPath) { SettingsView() }
                .tabItem { Label("설정", systemImage: "gearshape") }.tag(Tab.settings)
        }
        .tint(Theme.teal)
        // 보유·루트가 바뀌면 위젯도 바로
        .onChange(of: model.holdings) { _, _ in WidgetBridge.write(model) }
        .onChange(of: model.route) { _, _ in WidgetBridge.write(model) }
        .onChange(of: model.nxStep) { _, _ in WidgetBridge.write(model) }
        // 홈 인물을 바꾸면 위젯 인물도 같이 ('홈 인물 따라가기' 위젯)
        .onChange(of: model.homeFriend) { _, _ in WidgetBridge.write(model) }
        // 앱 시작 3단계: 동기화를 켜면 끝난다 (이미 켜져 있으면 3단계에 오는 순간)
        .onChange(of: Sync.shared.isOn, initial: true) { _, on in if on && model.nxStep == 2 { model.nxStep = 3 }; WidgetBridge.write(model) }
        .onChange(of: Sync.shared.isDev) { _, _ in WidgetBridge.write(model) }
        .onChange(of: model.nxStep) { _, n in if n == 2 && Sync.shared.isOn { model.nxStep = 3 } }
        // 켜 둔 채 날이 바뀌었으면 오늘의 1분도 다음 날로
        .onChange(of: phase) { _, p in if p == .active && model.persists { model.catchUpDay() } }
        // 미션 진행: 바뀔 때마다 저장, 동기화가 켜져 있으면 다른 기기로도
        .onChange(of: model.progress) { _, _ in model.saveProgress(); if model.persists { Sync.shared.changed(model) } }
        .onChange(of: model.holdings) { _, _ in if model.persists { Sync.shared.changed(model) } }
        // 앱을 열거나 앞으로 올 때 다른 기기 값 받기
        .onChange(of: phase, initial: true) { _, p in if p == .active && model.persists { Task { await Sync.shared.pull(model) } } }
        // 알림: 설정이 바뀌면 다시 예약
        .onChange(of: model.alerts) { _, _ in Task { await Notifier.reschedule(model) } }
        .onChange(of: model.alertHr) { _, _ in Task { await Notifier.reschedule(model) } }
        .onAppear {
            NotificationDelegate.shared.open = { o in
                switch o {
                case "home": model.tab = .home
                case "tax": model.tab = .settings; model.settingsPath = [.tax]
                default: model.tab = .board
                }
            }
        }
        // 잠긴 위젯을 누르면 앱 시작 3단계로
        .onOpenURL { url in
            if url.host == "shelter" { model.tab = .home; model.homePath = ["shelter"]; return }   // 인물 위젯 (못 만난 인물)
            if url.host == "read", let i = Int(url.lastPathComponent), (0..<6).contains(i), model.chapterOn(i) {   // 서재 위젯
                model.tab = .home; model.homePath = ["shelter", "read:\(i)"]; return
            }
            guard url.host == "unlock" else { return }
            model.tab = .board
            model.boardPath = model.playOn ? [] : [.nx]
        }
        // 지금 시세: 화면이 켜져 있는 동안 1분마다 갱신
        .task {
            while !Task.isCancelled {
                await Market.shared.refresh(held: Set(model.holdings.map(\.symbol)))
                WidgetBridge.write(model)        // 위젯에 지금 숫자를 넘긴다
                await Notifier.check(model)       // 본전 도달 · 비중 이탈
                await Notifier.reschedule(model)  // 아침 한 줄을 지금 숫자로
                try? await Task.sleep(for: .seconds(60))
            }
        }
        // 처음 실행이면 첫 질문을 탭 화면 위에 덮는다
        .overlay { if showStart { StartView().transition(.opacity) } }
        .animation(.default, value: showStart)
    }

    // 시뮬레이터 캡처용: -tab hold 처럼 시작 탭을 고를 수 있다
    static var launchTab: Tab {
        switch UserDefaults.standard.string(forKey: "tab") {
        case "hold": .hold
        case "analysis": .analysis
        case "board": .board
        case "settings": .settings
        default: .home
        }
    }
}

// 탭 화면 머리글 (시안의 'naeilo' 줄)
struct AppHeader: View {
    var body: some View {
        HStack {
            Text("naeilo").appFont(20, .bold).tracking(-0.3)
            Spacer()
        }
        .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 4)
    }
}
