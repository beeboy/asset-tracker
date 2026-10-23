import SwiftUI
import UserNotifications

@main
struct NaeiloApp: App {
    @State private var model = AppModel()
    init() {
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        TossKeys.share()   // 내 토스 키를 위젯도 읽는 자리로 (예전 판에서 넣은 키)
        Task { @MainActor in Support.shared.start() }   // 구독: 지금 구독 확인, 거래 듣기
    }
    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(model.appearance == "light" ? .light : model.appearance == "dark" ? .dark : nil)
                // 아주 큰 손쉬운 사용 크기에서도 화면이 무너지지 않게 상한을 둔다
                .dynamicTypeSize(...DynamicTypeSize.accessibility3)
                // 넘길 때 옆에 뜨는 스크롤 막대는 앱 전체에서 끈다 (2026-10-23)
                .scrollIndicators(.hidden)
        }
    }
}

enum Tab: Hashable { case home, hold, flow, analysis, board }   // 설정은 탭이 아니라 홈 위 팝업 (2026-10-21)

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var phase
    var body: some View {
        let showStart = !model.onboarded
        tabs
            .modifier(Hooks(model: model, phase: phase))
            .modifier(Links(model: model, phase: phase))
            // 처음 실행이면 첫 질문을 탭 화면 위에 덮는다
            .overlay { if showStart { StartView().transition(.opacity) } }
            .animation(.default, value: showStart)
    }

    // 본문을 탭·고리·열기로 나눠 둔다 (한 식이 길면 CI 타입 검사가 시간 초과)
    @ViewBuilder private var tabs: some View {
        @Bindable var model = model
        TabView(selection: $model.tab) {
            NavigationStack(path: $model.homePath) { HomeView() }
                .tabItem { Label("홈", systemImage: "house") }.tag(Tab.home)
            NavigationStack(path: $model.holdPath) { HoldingsView() }
                .tabItem { Label("종목", systemImage: "chart.bar") }.tag(Tab.hold)
            NavigationStack { FlowView() }   // 차트 탭 (이름은 FlowTab 한 곳에서)
                .tabItem { Label(LocalizedStringKey(FlowTab.name), systemImage: FlowTab.icon) }.tag(Tab.flow)
            NavigationStack(path: $model.analysisPath) { AnalysisView() }
                .tabItem { Label("분석", systemImage: "chart.line.uptrend.xyaxis") }.tag(Tab.analysis)
            NavigationStack(path: $model.boardPath) {
                BoardView()   // 목표·회복 루트 같은 미션 판
            }
                .tabItem { Label("미션", systemImage: "square.grid.2x2") }.tag(Tab.board)
        }
        .tint(Theme.teal)
        .modifier(HomeSheets(model: model))
    }

    // 위젯·동기화·알림 다시 예약
    private struct Hooks: ViewModifier {
        let model: AppModel
        let phase: ScenePhase
        func body(content: Content) -> some View {
            content
                // 보유·루트가 바뀌면 위젯도 바로
                .onChange(of: model.holdings) { _, _ in WidgetBridge.write(model) }
                .onChange(of: model.route) { _, _ in WidgetBridge.write(model) }
                .onChange(of: model.nxStep) { _, _ in WidgetBridge.write(model) }
                // 홈 인물을 바꾸면 위젯 인물도 같이 ('홈 인물 따라가기' 위젯)
                .onChange(of: model.homeFriend) { _, _ in WidgetBridge.write(model) }
                .onChange(of: Sync.shared.isOn) { _, _ in WidgetBridge.write(model) }
                .onChange(of: Sync.shared.isDev) { _, _ in WidgetBridge.write(model) }
                // 인물 대사: 화면을 열거나 앱을 앞으로 가져올 때만 다음 줄로 (1분 자동 갱신으로는 안 바뀐다)
                .modifier(LineTurns(model: model, phase: phase))
                // 켜 둔 채 날이 바뀌었으면 오늘의 1분도 다음 날로.
                // 전망 몬테카를로: 앱이 뒤로 가면 돌던 계산도 멈추고, 다시 앞으로 오면 이어 간다 (뒤에서 폰이 뜨거워지지 않게)
                .onChange(of: phase) { _, p in
                    Compute.setActive(p != .background)
                    if p == .background && model.persists { Sync.shared.flush(model) }   // 동기화 올리기는 앱을 닫을 때 한 번
                    if p == .active && model.persists { model.catchUpDay(); model.calmOpen() }   // calmOpen 안에 recordDay
                }
                // 미션 진행: 바뀔 때마다 저장, 동기화가 켜져 있으면 다른 기기로도
                .onChange(of: model.progress) { _, _ in model.saveProgress(); Remind.shared.refresh(model); if model.persists { Sync.shared.changed(model) } }
                .onChange(of: model.holdings) { _, _ in if model.persists { Sync.shared.changed(model) } }
                .modifier(KoreaSumHooks(model: model))
                // 내 기록: 새 날 줄이 생기면 다른 기기로도 (같은 날 값 고침은 다음 변경 때 같이)
                .onChange(of: model.myRecord.count) { _, _ in if model.persists { Sync.shared.changed(model) } }
                // 앱을 열거나 앞으로 올 때 다른 기기 값 받기
                // 서버 시각도 같이 받는다 (인터미션 날짜·내 캐릭터 조건, Me.swift)
                .onChange(of: phase, initial: true) { _, p in
                    guard p == .active else { return }
                    Task { await TrustedClock.shared.sync(); model.fixStarts(); Support.shared.tick() }   // 7일 무료·캐릭터 상품 열림 날짜도
                    if model.persists { Task { await Sync.shared.pull(model) } }
                }
                // 알림: 설정이 바뀌면 다시 예약
                .onChange(of: model.alerts) { _, _ in Task { await Notifier.reschedule(model) } }
                .onChange(of: model.alertHr) { _, _ in Task { await Notifier.reschedule(model) } }
        }
    }

    // 알림·위젯 주소 열기, 1분 시세 갱신
    private struct Links: ViewModifier {
        let model: AppModel
        let phase: ScenePhase
        func body(content: Content) -> some View {
            content
                .onAppear {
                    NotificationDelegate.shared.open = { o in
                        switch o {
                        case "home": model.tab = .home
                        case "tax": model.openSettings(.tax)
                        case "rate": model.go(.rateScen)
                        case "ext": model.go(.external)
                        default: model.tab = .board
                        }
                    }
                }
                // 잠긴 위젯을 누르면 앱 시작 3단계로
                .onOpenURL { url in
                    if url.host == "toss" { TossFeed.setBase(from: url); Task { await Market.shared.refresh(held: Set(model.holdings.map(\.symbol)), force: true) }; return }   // 개발자: 토스 시세 주소
                    if url.host == "shelter" { model.tab = .home; model.homePath = ["shelter"]; return }   // 인물 위젯 (못 만난 인물)
                    if url.host == "read", let i = Int(url.lastPathComponent), (0..<6).contains(i), model.chapterOn(i) {   // 서재 위젯
                        model.tab = .home; model.homePath = ["shelter", "read:\(i)"]; return
                    }
                    if url.host == "subscribe" { model.openSettings(.support); return }   // 7일 무료가 끝나 잠긴 위젯
                    guard url.host == "unlock" else { return }
                    model.tab = .board
                    model.boardPath = model.playOn ? [] : [.nx]
                }
                // 지금 시세: 화면이 켜져 있는 동안만 1분마다 갱신 (뒤로 가면 멈춰 배터리·데이터를 아낀다)
                .task(id: phase) {
                    guard phase == .active else { return }
                    var lastResched = Date.distantPast
                    var lastKey = ""   // 날짜·평가액이 그대로면 뒤 계산은 건너뛴다 (장 닫힌 동안 매분 화면이 멈칫하지 않게)
                    while !Task.isCancelled {
                        // 미니 게임 중엔 갱신을 미뤄 화면이 멈칫하지 않게 (끝나면 곧 다시 받는다)
                        while GamePause.on && !Task.isCancelled { try? await Task.sleep(for: .seconds(2)) }
                        await Market.shared.refresh(held: Set(model.holdings.map(\.symbol)))
                        let key = "\(Day.today)|\(model.total)"
                        if key != lastKey {
                            lastKey = key
                            if model.persists { model.recordDay() }   // 내 기록: 시세를 받은 뒤 오늘 줄을 지금 값으로
                            Remind.shared.refresh(model)     // 홈 리마인드 (위젯 말풍선·다음 일정도 여기서)
                            WidgetBridge.write(model)        // 위젯에 지금 숫자를 넘긴다
                            await Notifier.check(model)       // 본전 도달 · 비중 이탈
                        }
                        if Date().timeIntervalSince(lastResched) >= 600 {   // 아침 한 줄은 10분마다 지금 숫자로 (매분 지우고 다시 넣지 않게)
                            lastResched = Date(); await Notifier.reschedule(model)
                        }
                        try? await Task.sleep(for: .seconds(60))
                    }
                }
        }
    }

    // 시뮬레이터 캡처용: -tab hold 처럼 시작 탭을 고를 수 있다
    static var launchTab: Tab {
        switch UserDefaults.standard.string(forKey: "tab") {
        case "hold": .hold
        case "analysis": .analysis
        case "board": .board
        default: .home
        }
    }
}

// 탭 화면 머리글: 'naeilo' 로고 뒤에 탭 이름 ("naeilo 홈")
struct AppHeader: View {
    var tab: String = ""
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text("naeilo").appFont(20, .bold).tracking(-0.3)
            if !tab.isEmpty { Text(tab).appFont(20, .regular).foregroundStyle(Theme.sub) }
            Spacer()
        }
        .accessibilityElement(children: .combine)
        .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 4)
    }
}

/// 설정·위젯 팝업: 다른 팝업처럼 시트, 맨 위 줄 제목 + X로 닫는다. 어느 탭에서든 뜬다 (RootView 수식어가 길어 따로 뺐다)
private struct HomeSheets: ViewModifier {
    @Bindable var model: AppModel
    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $model.settingsOpen, onDismiss: { model.settingsPath = [] }) {
                NavigationStack(path: $model.settingsPath) { SettingsView(cover: true) }.environment(model)
            }
            .sheet(isPresented: $model.widgetsOpen) {
                NavigationStack { WidgetPickView(cover: true) }.environment(model)
            }
    }
}

/// 인물 대사 차례 올리기: 탭·화면 이동, 앱을 앞으로 가져올 때 (타입 검사 시간을 줄이려고 따로 묶었다)
private struct LineTurns: ViewModifier {
    let model: AppModel
    let phase: ScenePhase
    func body(content: Content) -> some View {
        content
            .onChange(of: phase) { _, p in if p == .active { LineClock.shared.next() } }
            .onChange(of: model.tab) { _, _ in LineClock.shared.next() }
            .onChange(of: model.homePath) { _, _ in LineClock.shared.next() }
            .onChange(of: model.boardPath) { _, _ in LineClock.shared.next() }
            .onChange(of: model.settingsPath) { _, _ in LineClock.shared.next() }
            .onChange(of: model.analysisPath.count) { _, _ in LineClock.shared.next() }
            .onChange(of: model.holdPath.count) { _, _ in LineClock.shared.next() }
    }
}

/// 한국 종목 가격·국내 주식 합계를 고치면 다른 기기로도 보내고, 리마인드 '오늘 국내 주식 합계'를 누르면 입력 칸을 한 번 띄운다
/// (RootView 수식어가 길어 타입 검사가 늦어지지 않게 따로 뺐다)
struct KoreaSumHooks: ViewModifier {
    @Bindable var model: AppModel
    func body(content: Content) -> some View {
        content
            .onChange(of: CustomSymbols.shared.krStamp) { _, _ in if model.persists { Sync.shared.changed(model) } }
            .sheet(isPresented: $model.krSumAsk) {
                // 키보드 위 '완료' 버튼이 뜨도록 NavigationStack 으로 감싼다
                NavigationStack {
                    ScrollView { KoreaSumCard(done: { model.krSumAsk = false }).padding(16) }
                        .scrollDismissesKeyboard(.immediately).background(Theme.bg)
                        .toolbar(.hidden, for: .navigationBar)
                }
                .presentationDetents([.medium])
            }
    }
}
