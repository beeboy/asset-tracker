import Foundation

enum Fmt {
    /// 5.27억
    /// 1억이 안 되면 만원으로 (4,002만)
    static func eok(_ v: Double, _ digits: Int = 2) -> String {
        abs(v) < 1e8 ? man(v) : String(format: "%.\(digits)f억", v / 1e8)
    }
    /// 499만
    static func man(_ v: Double) -> String {
        let n = NumberFormatter(); n.numberStyle = .decimal
        return (n.string(from: NSNumber(value: Int((abs(v) / 1e4).rounded()))) ?? "0") + "만"
    }
    /// +1.8%
    static func pct(_ x: Double, _ d: Int = 1) -> String { (x > 0 ? "+" : "") + String(format: "%.\(d)f%%", x * 100) }
    static func arrow(_ x: Double) -> String { x >= 0 ? "▲" : "▼" }
    static func comma(_ v: Int) -> String { let n = NumberFormatter(); n.numberStyle = .decimal; return n.string(from: NSNumber(value: v)) ?? "\(v)" }
    static func time(_ d: Date?) -> String {
        guard let d else { return "-" }
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = Calendar.current.isDateInToday(d) ? "HH:mm" : "M/d HH:mm"
        return f.string(from: d)
    }
}

/// 미국장(프리마켓~애프터마켓) 시간: 한국 시간 평일 오후 5시 ~ 다음 날 오전 9시. 사이트 시세 수집과 같은 범위
enum MarketHours {
    static func isActive(_ d: Date = Date()) -> Bool {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "UTC")!
        let wd = c.component(.weekday, from: d), h = c.component(.hour, from: d) // 일=1 … 토=7
        if wd == 1 || wd == 7 { return wd == 7 && h == 0 } // 토요일 0시 UTC 는 금요일 장 마감 직후
        return h >= 8 || (wd != 2 && h == 0)
    }
    /// 다음 갱신 시각: 장중엔 설정한 주기, 장 밖엔 3시간 (그 사이 장이 열리면 그때)
    static func nextRefresh(_ now: Date = Date()) -> Date {
        let iv = TimeInterval(Store.intervalMin * 60)
        if isActive(now) { return now.addingTimeInterval(iv) }
        var t = now
        for _ in 0..<36 { t = t.addingTimeInterval(300); if isActive(t) { return t.addingTimeInterval(120) } }
        return now.addingTimeInterval(3 * 3600)
    }
}
