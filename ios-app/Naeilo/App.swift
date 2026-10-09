import SwiftUI

@main
struct NaeiloApp: App {
    @State private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(.light)
        }
    }
}

enum Tab: Hashable { case home, hold, analysis, board, settings }

struct RootView: View {
    @State private var tab: Tab = Self.launchTab
    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { HomeView() }
                .tabItem { Label("홈", systemImage: "house") }.tag(Tab.home)
            NavigationStack { HoldingsView() }
                .tabItem { Label("종목", systemImage: "chart.bar") }.tag(Tab.hold)
            NavigationStack { AnalysisView() }
                .tabItem { Label("분석", systemImage: "chart.line.uptrend.xyaxis") }.tag(Tab.analysis)
            NavigationStack { BoardView() }
                .tabItem { Label("미션", systemImage: "square.grid.2x2") }.tag(Tab.board)
            NavigationStack { SettingsView() }
                .tabItem { Label("설정", systemImage: "gearshape") }.tag(Tab.settings)
        }
        .tint(Theme.teal)
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
            Text("naeilo").font(.system(size: 20, weight: .bold)).tracking(-0.3)
            Spacer()
        }
        .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 4)
    }
}
