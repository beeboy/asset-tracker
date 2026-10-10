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
    var homeFriend: String? = nil // 앱 홈에 둔 인물 (위젯 '홈 인물 따라가기'가 쓴다)
    var spark: [Double]? = nil    // 최근 10일 평가액 (만원). 인물 · 자산 추이
    var items: [String]? = nil    // 쉼터에 돌아온 물건 (돌아온 순서). 오늘의 움직임 위젯이 쓴다

    static let sample = WReward(keyName: "본전", pct: 0.87, pctYesterday: 0.862, remain: 1.52e7, cells: 264, cellsYesterday: 261,
                                total: 1.234e8, dayChg: 0.008, tiles: [.init(t: "DRNK", w: 0.62, c: 0.019), .init(t: "QQQ", w: 0.38, c: -0.004)],
                                next: "DRNK 실적 D-12 (10/22)", friendsOn: WChar.all.map(\.id),
                                spark: [11980, 12050, 12010, 12120, 12200, 12150, 12260, 12310, 12240, 12340],
                                items: ["barley_tea", "porch_light"])
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

    /// 말풍선 (인물 말투). 세리·시오·선배는 오늘 가장 크게 움직인 종목을 넣는다.
    /// 사흘에 한 번은 쉼터에 돌아온 물건 얘기를 인물 말투로 한다
    func line(_ r: WReward) -> String {
        if let it = WItem.today(r), let say = WItem.say(it, id) { return say }
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

/// 쉼터에 돌아온 물건: 위젯 그림 이름은 w_item_<id> (앱 쪽 art_<id> 와 같은 도트)
enum WItem {
    /// 물건마다 인물 말투 (세리 관찰·해요체, 시오 짧은 반말, 선배 질문, 이르 조용한 반말, 수아 해요체)
    static let lines: [String: [String: String]] = [
        "barley_tea": ["seri": "보리차 내 뒀어요. 시키지 않았어요.", "sio": "보리차. 아직 따뜻해.", "seonbae": "보리차, 누가 내 놨을까?", "ir": "따뜻할 때 마셔.", "sua": "보리차 식기 전에 왔어요."],
        "porch_light": ["seri": "현관 등, 켜져 있어요.", "sio": "등은 켜 뒀어. 늦어도 돼.", "seonbae": "늦게 와도 켜져 있는 불, 왜일까?", "ir": "불은 꺼지지 않아.", "sua": "현관 등 보고 찾아왔어요."],
        "wall_clock": ["seri": "시계가 3분 늦어요. 그대로 둘게요.", "sio": "시계는 안 맞췄어. 셀 거라서.", "seonbae": "시간을 꼭 맞춰야 할까?", "ir": "빠르다고 먼저 닿는 건 아니야.", "sua": "시계는 늦어도 저는 제때예요."],
        "hair_tie": ["seri": "머리끈, 식탁 위에 있어요.", "sio": "머리끈. 아직 어중간해.", "seonbae": "자르지 않고 묶은 이유가 뭐야?", "ir": "묶어 두면 덜 흔들려.", "sua": "머리끈 하나 빌려 갈게요."],
        "table_chair": ["seri": "종이 한 장, 비어 있어요.", "sio": "식탁에 종이. 내일 쓸 거야.", "seonbae": "그 종이에 뭐라고 쓸 거야?", "ir": "자리 하나 비워 뒀어.", "sua": "제 자리 아직 있어요?"],
        "asym_bowl": ["seri": "그릇이 2도 기울었어요. 고치지 않을게요.", "sio": "기운 그릇. 그대로 둬.", "seonbae": "기울면 틀린 걸까?", "ir": "기운 채로도 담겨.", "sua": "기운 그릇이 제 거예요."],
        "unfired_bowl": ["seri": "그릇은 아직 안 구웠어요.", "sio": "아직 안 구웠어. 급할 거 없어.", "seonbae": "언제 구울지는 누가 정해?", "ir": "서두르면 금이 가.", "sua": "다 마르면 구울게요. 약속은 아니에요."],
        "jujube_seed": ["seri": "대추씨, 싹은 아직이에요.", "sio": "대추씨 심은 지 며칠. 세고 있어.", "seonbae": "기다리는 동안 뭘 했어?", "ir": "기다림도 자라.", "sua": "대추씨 아직 기다리는 중이에요."],
        "elder_bead": ["seri": "구슬은 하나뿐이에요.", "sio": "구슬. 하나야.", "seonbae": "하나뿐인 걸 왜 여기 뒀을까?", "ir": "원로가 준 거야. 하나뿐이야.", "sua": "구슬에 제 얼굴이 비쳐요."],
        "rice_seeds": ["seri": "볍씨 주머니, 챙겨 뒀어요.", "sio": "볍씨. 누가 싸 줬는지 알아.", "seonbae": "누가 싸 줬는지 기억나?", "ir": "심을 곳은 네가 정해.", "sua": "볍씨 주머니 들고 왔어요."],
    ]
    static func say(_ item: String, _ who: String) -> String? { lines[item]?[who] ?? lines[item]?["seri"] }
    /// 오늘 말풍선에 나오는 물건: 사흘에 한 번, 돌아온 물건을 차례로. 나머지 날은 nil
    static func today(_ r: WReward, _ date: Date = Date()) -> String? {
        guard let ids = r.items, !ids.isEmpty else { return nil }
        let n = Calendar.current.ordinality(of: .day, in: .era, for: date) ?? 0
        return n % 3 == 0 ? ids[(n / 3) % ids.count] : nil
    }
    /// 인물 발치에 둘 물건: 말풍선에 나온 물건, 아니면 마지막에 돌아온 물건
    static func shown(_ r: WReward) -> String? { today(r) ?? r.items?.last }
}
