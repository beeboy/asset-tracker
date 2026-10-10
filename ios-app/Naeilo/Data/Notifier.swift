import Foundation
import UserNotifications

// 실제 알림 (기기 안에서 예약하는 로컬 알림. 서버 없음)
// - 본전 도달 · 비중 이탈: 앱이 시세를 갱신할 때(앱이 켜져 있을 때 1분마다) 조건을 보고 바로 보낸다.
//   둘이 같은 날 겹치면 하나로 묶고, 조건 알림은 하루 한 번까지. 본전 도달은 본전 아래로 내려갔다 다시 넘을 때만 또 보낸다.
// - 아침 한 줄: 다음 평일 아침 한 번만 지금 숫자로 예약하고, 그 뒤 평일 4번은 숫자 없이 예약한다.
//   앱을 열 때마다 다시 예약하므로, 앱을 자주 열면 늘 숫자가 들어간 알림이 온다. 숫자가 오래되면 넣지 않는다.
// - 연말 절세 확인: 매년 12월 1일 오전 9시.
enum Notifier {
    static let center = UNUserNotificationCenter.current()
    private static let d = UserDefaults.standard

    enum Status { case unknown, allowed, denied }
    static func status() async -> Status {
        switch await center.notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: .allowed
        case .denied: .denied
        default: .unknown
        }
    }
    /// 처음 한 번 아이폰의 알림 허용 창을 띄운다. 이미 정했으면 그 결과를 돌려준다
    @discardableResult
    static func request() async -> Bool {
        if await status() == .allowed { return true }
        return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    // MARK: 예약 (설정이 바뀌거나 앱을 열 때)
    @MainActor
    static func reschedule(_ m: AppModel) async {
        center.removePendingNotificationRequests(withIdentifiers: (0..<5).map { "morn.\($0)" } + ["dep"])
        guard await status() == .allowed else { return }

        if m.alerts["dep"] == true {
            let c = UNMutableNotificationContent()
            c.title = "연말 절세 확인"
            c.body = "올해가 한 달 남았어요. 손실 난 종목 일부를 팔면 내년 세금이 줄 수 있어요. 앱에서 계산을 확인해 보세요."
            c.sound = .default
            c.userInfo = ["open": "tax"]
            let t = UNCalendarNotificationTrigger(dateMatching: DateComponents(month: 12, day: 1, hour: 9), repeats: true)
            try? await center.add(UNNotificationRequest(identifier: "dep", content: c, trigger: t))
        }

        if m.alerts["morn"] == true {
            let cal = Calendar.current
            var day = Date()
            var n = 0
            // 오늘 그 시각이 지났으면 내일부터
            if let today = cal.date(bySettingHour: m.alertHr, minute: 0, second: 0, of: day), today <= Date() {
                day = cal.date(byAdding: .day, value: 1, to: day)!
            }
            while n < 5 {
                let wd = cal.component(.weekday, from: day)
                if wd != 1 && wd != 7, let at = cal.date(bySettingHour: m.alertHr, minute: 0, second: 0, of: day) {
                    let c = UNMutableNotificationContent()
                    c.title = "아침 한 줄"
                    c.body = n == 0 ? morningLine(m) : "오늘의 움직임과 \(m.keyName)까지 남은 거리를 확인해 보세요."
                    c.sound = .default
                    c.userInfo = ["open": "home"]
                    let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: at)
                    try? await center.add(UNNotificationRequest(identifier: "morn.\(n)", content: c,
                                                                trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)))
                    n += 1
                }
                day = cal.date(byAdding: .day, value: 1, to: day)!
            }
        }
    }

    /// 아침 한 줄: 지금 앱이 아는 숫자 (어제 장 마감 기준)
    @MainActor
    static func morningLine(_ m: AppModel) -> String {
        // 남은 거리 = 지금에서 몇 % 더 올라야 본전(목표)인지 (시안 알림 미리보기와 같은 식)
        let need = m.keyValue / max(1, m.trackValue) - 1
        let move = "어제 하루 \(AppModel.sgn(m.todayMove))."
        return need <= 0 ? "\(move) \(m.keyName)을 넘었어요." : "\(move) \(m.keyName)까지 \(String(format: "%.1f", need * 100))% 남았어요."
    }

    // MARK: 조건 알림 (시세를 갱신할 때마다)
    @MainActor
    static func check(_ m: AppModel) async {
        guard await status() == .allowed else { return }
        var lines: [(String, String)] = []

        // 본전 도달: 본전 아래 → 위로 넘을 때 한 번
        if !m.isGoal, m.cost > 0 {
            let above = m.total >= m.cost
            // 처음 보는 날은 지금 상태만 적는다 (이미 본전 위에서 시작했으면 보내지 않는다)
            if m.alerts["be"] == true, above, d.object(forKey: "nt.beAbove") != nil, !d.bool(forKey: "nt.beAbove") {
                lines.append(("본전 도달", "본전에 도착했어요! 평가액이 들어간 돈 \(AppModel.man(m.cost))을 넘었어요."))
            }
            d.set(above, forKey: "nt.beAbove")
        }
        // 비중 이탈: 계획 비중에서 기준 넘게 벗어나면 (하루 한 번)
        if m.alerts["drift"] == true, !m.isGoal {
            let gap = abs(m.drnkWeight - m.planWeight)
            if gap * 100 > Double(m.alertTh) {
                lines.append(("비중 이탈", "DRNK 비중이 \(AppModel.pct(m.drnkWeight))예요. 계획(\(AppModel.pct(m.planWeight)))보다 \(Int((gap * 100).rounded()))%p 벗어났어요."))
            }
        }
        guard !lines.isEmpty else { return }
        let today = Day.today
        guard d.string(forKey: "nt.sentDay") != today else { return }    // 조건 알림은 하루 한 번
        d.set(today, forKey: "nt.sentDay")

        let c = UNMutableNotificationContent()
        c.title = lines.map(\.0).joined(separator: " · ")
        c.body = lines.map(\.1).joined(separator: "\n")
        c.sound = .default
        c.userInfo = ["open": "board"]
        try? await center.add(UNNotificationRequest(identifier: "cond.\(today)", content: c, trigger: nil))
    }

    /// 시안 확인용: 켠 알림을 5초 뒤에 지금 숫자로 한 번 보내 본다
    @MainActor
    static func test(_ m: AppModel) async {
        guard await request() else { return }
        let c = UNMutableNotificationContent()
        c.title = "naeilo 알림 시험"
        c.body = m.alerts["morn"] == true ? morningLine(m) : "알림이 이렇게 와요. 켠 알림은 조건이 되면 이 모양으로 와요."
        c.sound = .default
        try? await center.add(UNNotificationRequest(identifier: "test", content: c,
                                                    trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)))
    }
}

/// 앱이 켜져 있을 때도 알림을 배너로 보여 주고, 누르면 알맞은 탭으로
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()
    var open: ((String) -> Void)?
    func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }
    func userNotificationCenter(_ c: UNUserNotificationCenter, didReceive r: UNNotificationResponse) async {
        if let o = r.notification.request.content.userInfo["open"] as? String {
            await MainActor.run { open?(o) }
        }
    }
}
