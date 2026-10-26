import Foundation

/// 워치 위젯이 읽는 값. 아이폰 앱이 위젯 값을 쓸 때 워치로 보내고(WatchSync), 워치 앱이 받아 App Group 에 쓴다.
/// 워치는 아이폰 App Group 을 못 읽어서 따로 보낸다. 계산은 모두 아이폰이 하고 워치는 그리기만 한다
struct WWatch: Codable {
    var at: Double               // 보낸 시각 (ms)
    var char: String             // 인물 id (앱 홈의 인물)
    var accent: UInt32, g0: UInt32, g1: UInt32
    var total: Double            // 평가액 (원). 인물 위젯처럼 현금은 뺀다
    var dayChg: Double           // 오늘 등락 (비율)
    var spark: [Double]          // 최근 10일 평가액 (만원)
    var keyName: String          // "본전" 또는 "목표"
    var pct: Double              // 본전(목표)까지 진행 0~1
    var line: String             // 인물 한마디
    var art: [String: Data]? = nil   // 내 캐릭터 그림 PNG (키: 포즈 이름, 기본 그림은 "base"). 워치엔 이 그림이 없어 같이 보낸다

    static let sample = WWatch(at: 0, char: "seri", accent: 0x5ab8ff, g0: 0x16203f, g1: 0x2c3e8a, total: 1.234e8, dayChg: 0.008,
                               spark: [11980, 12050, 12010, 12120, 12200, 12150, 12260, 12310, 12240, 12340],
                               keyName: "본전", pct: 0.87, line: "…… 오늘은 조용했어.")

    private static let group = "group.com.naeilo.widget", key = "watch.json"
    static func read() -> WWatch? {
        UserDefaults(suiteName: group)?.data(forKey: key).flatMap { try? JSONDecoder().decode(WWatch.self, from: $0) }
    }
    func save() {
        if let d = try? JSONEncoder().encode(self) { UserDefaults(suiteName: Self.group)?.set(d, forKey: Self.key) }
    }

    /// 2,732만원 · 1억 2,340만원
    var moneyText: String {
        let man = Int((abs(total) / 1e4).rounded()), eok = man / 10000, rest = man % 10000
        let f = NumberFormatter(); f.numberStyle = .decimal
        let c = { (n: Int) in f.string(from: NSNumber(value: n)) ?? "\(n)" }
        if eok == 0 { return c(man) + "만원" }
        return "\(eok)억" + (rest > 0 ? " \(c(rest))만원" : "원")
    }
    /// +1.2%
    var dayText: String { (dayChg >= 0 ? "+" : "−") + String(format: "%.1f%%", abs(dayChg) * 100) }
}
