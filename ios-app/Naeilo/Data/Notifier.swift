import Foundation
import UserNotifications
import UIKit

// 실제 알림 (기기 안에서 예약하는 로컬 알림. 서버 없음)
// - 본전 도달 · 비중 이탈: 앱이 시세를 갱신할 때(앱이 켜져 있을 때 1분마다) 조건을 보고 바로 보낸다.
//   둘이 같은 날 겹치면 하나로 묶고, 조건 알림은 하루 한 번까지. 본전 도달은 본전 아래로 내려갔다 다시 넘을 때만 또 보낸다.
// - 아침 한 줄: 다음 평일 아침 한 번만 지금 숫자로 예약하고, 그 뒤 평일 4번은 숫자 없이 예약한다.
//   앱을 열 때마다 다시 예약하므로, 앱을 자주 열면 늘 숫자가 들어간 알림이 온다. 숫자가 오래되면 넣지 않는다.
// - 연말 세금 정리: 매년 12월 1일 오전 9시.
// - 금리 결정 전날: 앞으로 90일 안의 FOMC·한국은행 결정 하루 전 오전 9시 (금리 충격 테스트에서 켬). 날짜는 고정 규칙이라 근사일 수 있다.
// - 일정 다음 날 결과: 외부 요인 '오늘의 이벤트' 카드에서 켠 일정만, 일정 다음 날 오전 9시 한 번 (id "xf.…").
//   금리 결정 '전날' 알림(id "rate.…")과는 시점·이름이 달라 겹치지 않는다.
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

    /// 설정에서 고른 앱 아이콘을 알림 오른쪽 그림으로 붙인다.
    /// 알림 왼쪽 아이콘은 아이폰이 정하는데, 다른 아이콘을 골라도 기본 아이콘으로 나오는 경우가 있어서 그림을 같이 보낸다
    @MainActor
    static func withIcon(_ c: UNMutableNotificationContent) -> UNMutableNotificationContent {
        guard let alt = UIApplication.shared.alternateIconName,
              let prev = AppIconPicker.icons.first(where: { $0.id == alt })?.prev,
              let png = UIImage(named: prev)?.pngData() else { return c }
        // 붙인 파일은 아이폰이 가져가므로 알림마다 새로 쓴다
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("icon-\(UUID().uuidString).png")
        guard (try? png.write(to: url)) != nil,
              let a = try? UNNotificationAttachment(identifier: "icon", url: url, options: nil) else { return c }
        c.attachments = [a]
        return c
    }

    // MARK: 예약 (설정이 바뀌거나 앱을 열 때)
    @MainActor
    static func reschedule(_ m: AppModel) async {
        let old = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("rate.") }
        center.removePendingNotificationRequests(withIdentifiers: (0..<5).map { "morn.\($0)" } + ["dep"] + old)
        guard await status() == .allowed else { return }

        if m.alerts["rate"] == true {
            let cal = Calendar.current
            for e in MarketEvents.rateUpcoming(days: 90) {
                guard let d = Day.date(e.day), let prev = cal.date(byAdding: .day, value: -1, to: d),
                      let at = cal.date(bySettingHour: 9, minute: 0, second: 0, of: prev), at > Date() else { continue }
                let c = UNMutableNotificationContent()
                c.title = String(localized: "내일 \(String(describing: e.kind))")
                c.body = String(localized: "금리가 움직이면 내 목표는 어떻게 될까요? 금리 충격 테스트를 다시 보세요.")
                c.sound = .default
                c.userInfo = ["open": "rate"]
                let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: at)
                try? await center.add(UNNotificationRequest(identifier: "rate.\(e.id).\(e.day)", content: withIcon(c),
                                                            trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)))
            }
        }

        if m.alerts["dep"] == true {
            let c = UNMutableNotificationContent()
            c.title = String(localized: "연말 세금 정리")
            c.body = String(localized: "올해가 한 달 남았어요. 올해 해외주식 손익과 내년 5월 세금 예시를 앱에서 확인해 보세요.")
            c.sound = .default
            c.userInfo = ["open": "tax"]
            let t = UNCalendarNotificationTrigger(dateMatching: DateComponents(month: 12, day: 1, hour: 9), repeats: true)
            try? await center.add(UNNotificationRequest(identifier: "dep", content: withIcon(c), trigger: t))
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
                    c.title = String(localized: "아침 한 줄")
                    c.body = n == 0 ? morningLine(m) : String(localized: "오늘의 움직임과 \(String(describing: m.keyName))까지 남은 거리를 확인해 보세요.")
                    c.sound = .default
                    c.userInfo = ["open": "home"]
                    let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: at)
                    try? await center.add(UNNotificationRequest(identifier: "morn.\(n)", content: withIcon(c),
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
        let move = String(localized: "어제 하루 \(String(describing: AppModel.sgn(m.todayMove))).")
        return need <= 0 ? String(localized: "\(String(describing: move)) \(String(describing: m.keyName))을 넘었어요.") : String(localized: "\(String(describing: move)) \(String(describing: m.keyName))까지 \(String(describing: String(format: "%.1f", need * 100)))% 남았어요.")
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
                lines.append((String(localized: "본전 도달"), String(localized: "본전에 도착했어요! 평가액이 들어간 돈 \(String(describing: AppModel.man(m.cost)))을 넘었어요.")))
            }
            d.set(above, forKey: "nt.beAbove")
        }
        // 비중 이탈: 계획 비중에서 기준 넘게 벗어나면 (하루 한 번)
        if m.alerts["drift"] == true, !m.isGoal {
            let gap = abs(m.focusWeight - m.planWeight)
            if gap * 100 > Double(m.alertTh) {
                lines.append((String(localized: "비중 이탈"), String(localized: "\(String(describing: m.focusName)) 비중이 \(String(describing: AppModel.pct(m.focusWeight)))예요. 계획(\(String(describing: AppModel.pct(m.planWeight))))보다 \(String(describing: Int((gap * 100).rounded())))%p 벗어났어요.")))
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
        try? await center.add(UNNotificationRequest(identifier: "cond.\(today)", content: withIcon(c), trigger: nil))
    }

    /// 시안 확인용: 켠 알림을 5초 뒤에 지금 숫자로 한 번 보내 본다
    @MainActor
    static func test(_ m: AppModel) async {
        guard await request() else { return }
        let c = UNMutableNotificationContent()
        c.title = String(localized: "naeilo 알림 시험")
        c.body = m.alerts["morn"] == true ? morningLine(m) : String(localized: "알림이 이렇게 와요. 켠 알림은 조건이 되면 이 모양으로 와요.")
        c.sound = .default
        try? await center.add(UNNotificationRequest(identifier: "test", content: withIcon(c),
                                                    trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)))
    }
}

/// 지난 일 돌아보기: 일정 전에 미리 본 범위(±금액)와 그때 평가액을 기기에 적어 두고,
/// 일정 다음 날 이후 처음 볼 때의 평가액과 비교한다. 서버 없음, 최근 10개만 둔다
enum EventRetro {
    struct Snap: Codable { var kind: String; var day: String; var amt: Double; var base: Double; var after: Double? }
    private static let key = "xf.retro.v1", askKey = "xf.ask.v1"
    private static let d = UserDefaults.standard

    static var snaps: [String: Snap] {
        get { (d.data(forKey: key)).flatMap { try? JSONDecoder().decode([String: Snap].self, from: $0) } ?? [:] }
        set { if let x = try? JSONEncoder().encode(newValue) { d.set(x, forKey: key) } }
    }
    /// 일정 당일·전날에 본 값을 적는다 (일정 날까지 덮어써서 가장 가까운 값이 남는다). 지난 일정은 결과 평가액을 한 번 채운다
    static func record(_ evs: [(id: String, kind: String, day: String, amt: Double)], total: Double) {
        guard total > 0 else { return }
        var s = snaps
        let today = Day.today
        for e in evs where Day.dday(e.day) <= 1 && e.day >= today {
            s["\(e.id).\(e.day)"] = Snap(kind: e.kind, day: e.day, amt: e.amt, base: total, after: nil)
        }
        for (k, v) in s where v.after == nil && v.day < today { s[k]?.after = total }
        if s.count > 10 { for k in s.sorted(by: { $0.value.day < $1.value.day }).prefix(s.count - 10).map(\.key) { s.removeValue(forKey: k) } }
        snaps = s
    }
    /// 결과까지 채워진 가장 최근 지난 일정
    static var latest: Snap? { snaps.values.filter { $0.after != nil }.max { $0.day < $1.day } }

    // MARK: 다음 날 결과 알림
    static func isAsked(_ id: String, _ day: String) -> Bool { (d.stringArray(forKey: askKey) ?? []).contains("\(id).\(day)") }
    @MainActor
    static func ask(id: String, kind: String, day: String, on: Bool) async {
        let k = "\(id).\(day)"
        // 이 일정과, 다음 날 알림까지 끝난 지난 일정은 목록에서 뺀다
        var a = (d.stringArray(forKey: askKey) ?? []).filter { $0 != k && (String($0.split(separator: ".").last ?? "")) >= Day.today }
        Notifier.center.removePendingNotificationRequests(withIdentifiers: ["xf.\(k)"])
        if on {
            guard await Notifier.request() else { d.set(a, forKey: askKey); return }
            let cal = Calendar.current
            guard let dd = Day.date(day), let next = cal.date(byAdding: .day, value: 1, to: dd),
                  let at = cal.date(bySettingHour: 9, minute: 0, second: 0, of: next), at > Date() else { return }
            let c = UNMutableNotificationContent()
            c.title = String(localized: "어제 \(String(describing: kind)) 결과")
            c.body = String(localized: "내 자산이 미리 본 범위 안에서 움직였는지 '지난 일 돌아보기'에서 확인해 보세요.")
            c.sound = .default
            c.userInfo = ["open": "ext"]
            let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: at)
            try? await Notifier.center.add(UNNotificationRequest(identifier: "xf.\(k)", content: Notifier.withIcon(c),
                                                                 trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)))
            a.append(k)
        }
        d.set(a, forKey: askKey)
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
