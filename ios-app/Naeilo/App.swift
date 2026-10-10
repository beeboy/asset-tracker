import SwiftUI

@main
struct NaeiloApp: App {
    @State private var model = AppModel()
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
