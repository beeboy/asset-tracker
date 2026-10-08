import SwiftUI
import BackgroundTasks
import WidgetKit

@main
struct NaeiloApp: App {
    @Environment(\.scenePhase) private var phase
    @StateObject private var model = AppModel()

    init() { Background.register() }

    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(model)
        }
        .onChange(of: phase) { _, p in
            if p == .active { Task { await model.sync(compute: true) } }
            if p == .background { Background.schedule() }
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    @Published var login: Login? = Credentials.load()
    @Published var busy = false
    @Published var message: String?
    @Published var snap: Snapshot = Engine.snapshot()

    func signIn(password: String) async {
        busy = true; message = "비밀번호를 확인하는 중…"
        let l = await Task.detached(priority: .userInitiated) { Credentials.derive(password: password) }.value
        guard let l else { busy = false; message = "비밀번호를 처리하지 못했습니다"; return }
        await finishSignIn(l)
    }

    func signIn(token: String) async { busy = true; await finishSignIn(.github(token: token.trimmingCharacters(in: .whitespacesAndNewlines))) }

    private func finishSignIn(_ l: Login) async {
        Store.wipe()
        do {
            _ = try await Refresher.pullState(l)
            Credentials.save(l); login = l
            message = nil
            busy = false
            await sync(compute: true)
        } catch {
            Store.wipe(); busy = false; message = error.localizedDescription
        }
    }

    func signOut() {
        Credentials.clear(); Store.wipe(); login = nil; snap = Engine.snapshot()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// 받기 → (필요하면) 계산 → 위젯 다시 그리기
    func sync(compute: Bool, force: Bool = false) async {
        guard login != nil, !busy else { return }
        busy = true; defer { busy = false }
        do {
            _ = try await Refresher.refresh()
            snap = Engine.snapshot()
            if compute, force || Calculator.needed() {
                message = "전망 계산 중…"
                try await Calculator.run(force: force)
            }
            message = nil
        } catch {
            message = error.localizedDescription
        }
        snap = Engine.snapshot()
        WidgetCenter.shared.reloadAllTimelines()
    }
}

/// 앱이 꺼져 있어도: 가벼운 갱신(시세·입력값)과 하루 한 번 계산. 실제 실행 시점은 iOS 가 정한다
enum Background {
    static let refreshId = "com.naeilo.widget.refresh"
    static let computeId = "com.naeilo.widget.compute"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshId, using: nil) { task in
            handle(task, compute: false)
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: computeId, using: nil) { task in
            handle(task, compute: true)
        }
    }

    static func schedule() {
        let r = BGAppRefreshTaskRequest(identifier: refreshId)
        r.earliestBeginDate = Market.nextRefresh()
        try? BGTaskScheduler.shared.submit(r)
        let c = BGProcessingTaskRequest(identifier: computeId)
        c.requiresNetworkConnectivity = true
        c.earliestBeginDate = Date().addingTimeInterval(4 * 3600)
        try? BGTaskScheduler.shared.submit(c)
    }

    private static func handle(_ task: BGTask, compute: Bool) {
        schedule()
        let work = Task {
            do {
                _ = try await Refresher.refresh()
                if compute || Calculator.needed() { try await Calculator.run() }
                WidgetCenter.shared.reloadAllTimelines()
                task.setTaskCompleted(success: true)
            } catch {
                task.setTaskCompleted(success: false)
            }
        }
        task.expirationHandler = { work.cancel() }
    }
}
