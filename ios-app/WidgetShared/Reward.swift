import Foundation

/// 인물 보상 위젯이 읽는 숫자 (앱이 reward.json 으로 쓴다). 모두 앱의 계산값이고, 위젯은 그리기만 한다
struct WReward: Codable {
    struct Tile: Codable { let t: String; let w: Double; let c: Double }   // 종목 이름, 비중, 오늘 등락
    var keyName: String          // "본전" 또는 "목표"
    var pct: Double              // 본전(목표)까지 진행 0~1
    var pctYesterday: Double
    var remain: Double           // 남은 금액 (원)
    var cells: Int               // 1000칸 중 채운 칸
    var cellsYesterday: Int
    var total: Double            // 평가액 (원)
    var dayChg: Double           // 오늘 등락 (원화 평가액 기준)
    var tiles: [Tile]
    var next: String?            // "DRNK 실적 D-12 (10/22)"
    var friendsOn: [String]      // 만난 인물 (세리는 처음부터)

    static let sample = WReward(keyName: "본전", pct: 0.87, pctYesterday: 0.862, remain: 1.52e7, cells: 264, cellsYesterday: 261,
                                total: 1.234e8, dayChg: 0.008, tiles: [.init(t: "DRNK", w: 0.62, c: 0.019), .init(t: "QQQ", w: 0.38, c: -0.004)],
                                next: "DRNK 실적 D-12 (10/22)", friendsOn: WChar.all.map(\.id))
}

/// 인물 위젯의 인물: 색은 캐릭터 설정 스레드 시안 (reward-widgets)
struct WChar: Identifiable {
    let id: String, name: String, week: Int
    let accent: UInt32, g0: UInt32, g1: UInt32
    static let all: [WChar] = [
        .init(id: "seri", name: "세리", week: 0, accent: 0x5ab8ff, g0: 0x16203f, g1: 0x2c3e8a),
        .init(id: "sio", name: "시오", week: 1, accent: 0x7fd6ff, g0: 0x122838, g1: 0x265c76),
        .init(id: "seonbae", name: "선배", week: 2, accent: 0xe0b25a, g0: 0x301e2c, g1: 0x744658),
        .init(id: "ir", name: "이르", week: 3, accent: 0xffd36b, g0: 0x1a1830, g1: 0x4a3f72),
        .init(id: "sua", name: "수아", week: 4, accent: 0xf2a070, g0: 0x3a2228, g1: 0xb06448),
    ]
    static func of(_ id: String) -> WChar { all.first { $0.id == id } ?? all[0] }

    /// 말풍선 (인물 말투). 세리·시오·선배는 오늘 가장 크게 움직인 종목을 넣는다
    func line(_ r: WReward) -> String {
        let top = r.tiles.max { abs($0.c) < abs($1.c) }
        let t = top?.t ?? "오늘", c = top?.c ?? 0
        switch id {
        case "seri": return "……\(t)\(Self.josa(t)) 먼저 움직였어요."
        case "sio": return "\(t) \(String(format: "%.1f", abs(c) * 100)). 셌어."
        case "seonbae": return r.dayChg >= 0 ? "올랐네. 왜 올랐다고 생각해?" : "내렸네. 왜 내렸다고 생각해?"
        case "ir": return "흔들려도 서두르지 마."
        default: return "오늘은 제때 왔어요."
        }
    }
    /// 이/가: 한글은 받침, 영문 티커는 읽는 소리 (L·M·N·R 로 끝나면 받침)
    static func josa(_ s: String) -> String {
        guard let ch = s.unicodeScalars.last else { return "가" }
        if (0xAC00...0xD7A3).contains(ch.value) { return (ch.value - 0xAC00) % 28 == 0 ? "가" : "이" }
        return "LMNR".unicodeScalars.contains(ch) ? "이" : "가"
    }
}
