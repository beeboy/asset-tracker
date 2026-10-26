// 워치 위젯 확장은 WatchConnectivity 를 못 쓴다. 아이폰 앱과 워치 앱에만 들어간다
#if !WATCH_WIDGET
import Foundation
import WatchConnectivity
#if os(watchOS)
import WidgetKit
#endif

/// 아이폰 → 워치: 위젯 값(WWatch)을 보낸다. 워치 앱은 받아서 저장하고 위젯을 다시 그린다
final class WatchSync: NSObject, WCSessionDelegate {
    static let shared = WatchSync()
    static let got = Notification.Name("watch.got")
    private var pending: Data?

    func start() {
        guard WCSession.isSupported(), WCSession.default.delegate == nil else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    #if os(iOS)
    /// 위젯 값을 쓸 때마다 부른다. 세션이 아직 안 열렸으면 열린 뒤에 보낸다
    func send(_ w: WWatch) {
        guard WCSession.isSupported(), let d = try? JSONEncoder().encode(w) else { return }
        pending = d
        start()
        flush()
    }
    private func flush() {
        let s = WCSession.default
        guard s.activationState == .activated, s.isPaired, s.isWatchAppInstalled, let d = pending else { return }
        // 마지막 값 하나만 남는다. 문자판에 올린 위젯이 있으면 워치 앱을 깨워 바로 넘긴다 (하루 횟수 제한 안에서)
        try? s.updateApplicationContext(["w": d])
        if s.isComplicationEnabled, s.remainingComplicationUserInfoTransfers > 0 { s.transferCurrentComplicationUserInfo(["w": d]) }
        pending = nil
    }
    func session(_ s: WCSession, activationDidCompleteWith st: WCSessionActivationState, error: Error?) {
        DispatchQueue.main.async { self.flush() }
    }
    func sessionDidBecomeInactive(_ s: WCSession) {}
    func sessionDidDeactivate(_ s: WCSession) { s.activate() }
    #else
    func session(_ s: WCSession, activationDidCompleteWith st: WCSessionActivationState, error: Error?) {
        if let d = s.receivedApplicationContext["w"] as? Data { got(d) }
    }
    func session(_ s: WCSession, didReceiveApplicationContext c: [String: Any]) { if let d = c["w"] as? Data { got(d) } }
    func session(_ s: WCSession, didReceiveUserInfo u: [String: Any]) { if let d = u["w"] as? Data { got(d) } }
    private func got(_ d: Data) {
        guard let w = try? JSONDecoder().decode(WWatch.self, from: d) else { return }
        // 늦게 온 옛 값으로 덮지 않는다
        if let old = WWatch.read(), old.at > w.at { return }
        w.save()
        WidgetCenter.shared.reloadAllTimelines()
        DispatchQueue.main.async { NotificationCenter.default.post(name: Self.got, object: nil) }
    }
    #endif
}
#endif
