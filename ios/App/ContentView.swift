import SwiftUI
import WidgetKit

enum AppTab: Hashable { case guide, widgets, settings }

struct ContentView: View {
    @EnvironmentObject var m: AppModel
    @State private var tab: AppTab = .guide

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { GuideView(tab: $tab).navigationTitle("naeilo 위젯") }
                .tabItem { Label("naeilo", systemImage: "chart.line.uptrend.xyaxis") }.tag(AppTab.guide)
            NavigationStack { WidgetsView().navigationTitle("위젯 11개") }
                .tabItem { Label("위젯", systemImage: "square.grid.2x2") }.tag(AppTab.widgets)
            NavigationStack {
                Group { if m.login == nil { SignInView() } else { HomeView() } }
                    .navigationTitle("연결")
            }
            .tabItem { Label("연결", systemImage: "link") }.tag(AppTab.settings)
        }
        // 위젯을 누르면 첫 탭(naeilo)으로
        .onOpenURL { _ in tab = .guide }
    }
}

struct SignInView: View {
    @EnvironmentObject var m: AppModel
    @State private var useToken = false
    @State private var secret = ""

    var body: some View {
        Form {
            Section {
                Picker("방식", selection: $useToken) {
                    Text("동기화 비밀번호").tag(false)
                    Text("GitHub 토큰").tag(true)
                }
                .pickerStyle(.segmented)
                SecureField(useToken ? "GitHub 토큰 (개발자 기기 동기화)" : "동기화 비밀번호 (10자 이상)", text: $secret)
                    .textContentType(.password)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                Button {
                    let s = secret
                    Task { if useToken { await m.signIn(token: s) } else { await m.signIn(password: s) } }
                } label: {
                    HStack { Text("시작하기"); Spacer(); if m.busy { ProgressView() } }
                }
                .disabled(m.busy || secret.count < (useToken ? 20 : 10))
            } footer: {
                Text(useToken
                     ? "사이트 설정의 GitHub 연결에 쓰는 토큰입니다. 이 아이폰 안(키체인)에만 저장됩니다."
                     : "사이트 설정 → 기기 자동 동기화에 넣은 비밀번호와 같은 것을 넣으세요. 비밀번호는 저장하지 않고, 풀 때 쓰는 키만 이 아이폰 안(키체인)에 둡니다.")
            }
            if let msg = m.message { Section { Text(msg).foregroundStyle(.secondary) } }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject var m: AppModel
    @State private var interval = Store.intervalMin
    @State private var hideEach = Store.hideEach
    @State private var hideAll = Store.hideAll

    var body: some View {
        let s = m.snap
        Form {
            Section("지금") {
                LabeledContent("총자산", value: Fmt.eok(s.total))
                LabeledContent("오늘", value: "\(Fmt.arrow(s.dayChg)) \(Fmt.pct(s.dayChg)) · \(Fmt.man(s.dayAmt))")
                LabeledContent("목표 진행", value: String(format: "%.1f%% · D-%@", s.progress * 100, Fmt.comma(s.dday)))
                if let p = s.pGoal { LabeledContent("목표 확률", value: "\(Int((p * 100).rounded()))%") }
            }
            Section {
                LabeledContent("마지막 확인", value: Fmt.time(s.updated))
                LabeledContent("전망 기준", value: (s.fcAsOf ?? "-") + (s.source == "site" ? " · 사이트 계산" : s.source == "app" ? " · 앱 계산" : ""))
                Picker("장중 갱신 주기", selection: $interval) {
                    Text("30분").tag(30); Text("1시간").tag(60); Text("3시간").tag(180)
                }
                .onChange(of: interval) { _, v in Store.intervalMin = v }
                Button {
                    Task { await m.sync(compute: true, force: true) }
                } label: {
                    HStack { Text("지금 갱신·다시 계산"); Spacer(); if m.busy { ProgressView() } }
                }
                .disabled(m.busy)
            } header: { Text("갱신") } footer: {
                Text("장중(한국 시간 평일 오후 5시~다음 날 오전 9시)에는 고른 주기로, 그 밖에는 3시간마다 확인합니다. 실제 시각은 iOS 가 조금 늦출 수 있습니다. 위젯의 ↻ 를 누르면 바로 확인합니다.")
            }
            Section {
                Picker("누르면", selection: $hideEach) {
                    Text("모든 위젯 함께").tag(false)
                    Text("위젯마다 따로").tag(true)
                }
                .onChange(of: hideEach) { _, v in Store.hideEach = v; WidgetCenter.shared.reloadAllTimelines() }
                if !hideEach {
                    Toggle("지금 금액 숨김", isOn: $hideAll)
                        .onChange(of: hideAll) { _, v in Store.hideAll = v; WidgetCenter.shared.reloadAllTimelines() }
                }
            } header: { Text("금액 숨기기") } footer: {
                Text("위젯의 큰 금액을 누르면 숨기고, 한 번 더 누르면 다시 보입니다. 등락 %·목표 %는 그대로 보입니다.")
            }
            if let msg = m.message { Section { Text(msg).foregroundStyle(.secondary) } }
            Section {
                LabeledContent("로그인", value: m.login?.label ?? "-")
                Button("로그아웃 (이 아이폰의 값 지우기)", role: .destructive) { m.signOut() }
            } footer: {
                Text("홈 화면을 길게 눌러 + 를 누르고 naeilo 를 찾으면 위젯을 고를 수 있습니다.")
            }
        }
        .refreshable { await m.sync(compute: true) }
        .onAppear { hideAll = Store.hideAll }
    }
}
