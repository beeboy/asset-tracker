import Foundation
import UIKit

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
    var says: [String: String]? = nil  // 인물별 말풍선 (앱이 대사 데이터에서 골라 넘긴다)
    var saysNext: [String: [String]]? = nil  // 인물별 다음 대사 후보. 위젯이 갱신하거나 ↻를 누를 때마다 다음 줄
    var news: String? = nil       // 홈 리마인드: 오늘 생긴 큰 달성 (오늘의 움직임 말풍선)
    var badge: String? = nil      // 홈 리마인드: 오늘 넘은 1000칸 이정표 (블록 위젯 머리)
    var newsDay: String? = nil    // news·badge 를 쓴 날 (다른 날이면 안 보인다)
    var newsToday: String? { newsDay == Day.today ? news : nil }
    var badgeToday: String? { newsDay == Day.today ? badge : nil }

    static let sample = WReward(keyName: String(localized: "본전"), pct: 0.87, pctYesterday: 0.862, remain: 1.52e7, cells: 264, cellsYesterday: 261,
                                total: 1.234e8, dayChg: 0.008, tiles: [.init(t: "DRNK", w: 0.62, c: 0.019), .init(t: "QQQ", w: 0.38, c: -0.004)],
                                next: String(localized: "DRNK 실적 D-12 (10/22)"), friendsOn: WChar.all.map(\.id),
                                spark: [11980, 12050, 12010, 12120, 12200, 12150, 12260, 12310, 12240, 12340],
                                items: ["barley_tea", "porch_light"])
}

/// 인물 위젯의 인물: 색은 캐릭터 설정 스레드 시안 (reward-widgets)
struct WChar: Identifiable {
    let id: String, name: String, week: Int
    let accent: UInt32, g0: UInt32, g1: UInt32
    var voice: String? = nil     // 말투 인물 (내 캐릭터는 투자 유형 인물)
    static let all: [WChar] = [
        .init(id: "seri", name: String(localized: "세리"), week: 0, accent: 0x5ab8ff, g0: 0x16203f, g1: 0x2c3e8a),
        .init(id: "sio", name: String(localized: "시오"), week: 1, accent: 0x7fd6ff, g0: 0x122838, g1: 0x265c76),
        .init(id: "seonbae", name: String(localized: "선배"), week: 2, accent: 0xe0b25a, g0: 0x301e2c, g1: 0x744658),
        .init(id: "ir", name: String(localized: "이르"), week: 3, accent: 0xffd36b, g0: 0x1a1830, g1: 0x4a3f72),
        .init(id: "sua", name: String(localized: "수아"), week: 4, accent: 0xf2a070, g0: 0x3a2228, g1: 0xb06448),
    ]
    /// 위젯에 쓰는 이름 (영어 기기면 Seri 처럼, 내 캐릭터는 지은 이름 그대로)
    var title: String { WL.t(name) }
    static func of(_ id: String) -> WChar {
        if id == "me", let m = WMe.saved { return .init(id: "me", name: m.name, week: 0, accent: m.accent, g0: m.g0, g1: m.g1, voice: m.voice) }
        return all.first { $0.id == id } ?? all[0]
    }

    /// 말풍선 (인물 말투). 세리·시오·선배는 오늘 가장 크게 움직인 종목을 넣는다.
    /// 사흘에 한 번은 쉼터에 돌아온 물건 얘기를 인물 말투로 한다
    func line(_ r: WReward) -> String {
        if let l = r.saysNext?[id], !l.isEmpty { return l[WidgetLine.index() % l.count] }
        if let s = r.says?[id], !s.isEmpty { return s }
        let v = voice ?? id
        if !WL.en, let it = WItem.today(r), let say = WItem.say(it, v) { return say }
        let top = r.tiles.max { abs($0.c) < abs($1.c) }
        let t = top?.t ?? WL.t("오늘"), c = top?.c ?? 0
        if WL.en {
            let n = String(format: "%.1f", abs(c) * 100)
            switch v {
            case "seri": return "…… \(t) moved first."
            case "sio": return "\(t) \(n). Counted."
            case "seonbae": return r.dayChg >= 0 ? "Up today. Why do you think?" : "Down today. Why do you think?"
            case "ir": return "Shaky, but don't hurry."
            default: return "Right on time today."
            }
        }
        switch v {
        case "seri": return String(localized: "……\(String(describing: t))\(String(describing: Self.josa(t))) 먼저 움직였어요.")
        case "sio": return String(localized: "\(String(describing: t)) \(String(describing: String(format: "%.1f", abs(c) * 100))). 셌어.")
        case "seonbae": return r.dayChg >= 0 ? String(localized: "올랐네. 왜 올랐다고 생각해?") : String(localized: "내렸네. 왜 내렸다고 생각해?")
        case "ir": return String(localized: "흔들려도 서두르지 마.")
        default: return String(localized: "오늘은 제때 왔어요.")
        }
    }
    /// 이/가: 한글은 받침, 영문 티커는 읽는 소리 (L·M·N·R 로 끝나면 받침)
    static func josa(_ s: String) -> String {
        if WL.en { return "" }   // 영어엔 조사가 없다
        guard let ch = s.unicodeScalars.last else { return String(localized: "가") }
        if (0xAC00...0xD7A3).contains(ch.value) { return (ch.value - 0xAC00) % 28 == 0 ? String(localized: "가") : String(localized: "이") }
        return "LMNR".unicodeScalars.contains(ch) ? String(localized: "이") : String(localized: "가")
    }
}

/// 내 캐릭터 (앱이 열림 조건을 채웠을 때만 me.json · me.png 로 쓴다, MeViews.swift MeArt.push)
struct WMe: Codable {
    var name: String
    var voice: String
    var accent: UInt32, g0: UInt32, g1: UInt32
    static var saved: WMe? { Store.read(WMe.self, "me.json") }
    static var image: UIImage? { Store.readData("me.png").flatMap { UIImage(data: $0) } }
    /// w_me → me.png, w_me_pose_<동작> → me_pose_<동작>.png (없으면 me.png). 앱이 MePose 로 그려 둔다
    static func image(_ name: String) -> UIImage? {
        Store.readData(String(name.dropFirst(2)) + ".png").flatMap { UIImage(data: $0) } ?? image
    }
}

/// 쉼터에 돌아온 물건: 위젯 그림 이름은 w_item_<id> (앱 쪽 art_<id> 와 같은 도트)
enum WItem {
    /// 물건마다 인물 말투 (세리 관찰·해요체, 시오 짧은 반말, 선배 질문, 이르 조용한 반말, 수아 해요체)
    static let lines: [String: [String: String]] = [
        "barley_tea": ["seri": String(localized: "보리차 내 뒀어요. 시키지 않았어요."), "sio": String(localized: "보리차. 아직 따뜻해."), "seonbae": String(localized: "보리차, 누가 내 놨을까?"), "ir": String(localized: "따뜻할 때 마셔."), "sua": String(localized: "보리차 식기 전에 왔어요.")],
        "porch_light": ["seri": String(localized: "현관 등, 켜져 있어요."), "sio": String(localized: "등은 켜 뒀어. 늦어도 돼."), "seonbae": String(localized: "늦게 와도 켜져 있는 불, 왜일까?"), "ir": String(localized: "불은 꺼지지 않아."), "sua": String(localized: "현관 등 보고 찾아왔어요.")],
        "wall_clock": ["seri": String(localized: "시계가 3분 늦어요. 그대로 둘게요."), "sio": String(localized: "시계는 안 맞췄어. 셀 거라서."), "seonbae": String(localized: "시간을 꼭 맞춰야 할까?"), "ir": String(localized: "빠르다고 먼저 닿는 건 아니야."), "sua": String(localized: "시계는 늦어도 저는 제때예요.")],
        "hair_tie": ["seri": String(localized: "머리끈, 식탁 위에 있어요."), "sio": String(localized: "머리끈. 아직 어중간해."), "seonbae": String(localized: "자르지 않고 묶은 이유가 뭐야?"), "ir": String(localized: "묶어 두면 덜 흔들려."), "sua": String(localized: "머리끈 하나 빌려 갈게요.")],
        "table_chair": ["seri": String(localized: "종이 한 장, 비어 있어요."), "sio": String(localized: "식탁에 종이. 내일 쓸 거야."), "seonbae": String(localized: "그 종이에 뭐라고 쓸 거야?"), "ir": String(localized: "자리 하나 비워 뒀어."), "sua": String(localized: "제 자리 아직 있어요?")],
        "asym_bowl": ["seri": String(localized: "그릇이 2도 기울었어요. 고치지 않을게요."), "sio": String(localized: "기운 그릇. 그대로 둬."), "seonbae": String(localized: "기울면 틀린 걸까?"), "ir": String(localized: "기운 채로도 담겨."), "sua": String(localized: "기운 그릇이 제 거예요.")],
        "unfired_bowl": ["seri": String(localized: "그릇은 아직 안 구웠어요."), "sio": String(localized: "아직 안 구웠어. 급할 거 없어."), "seonbae": String(localized: "언제 구울지는 누가 정해?"), "ir": String(localized: "서두르면 금이 가."), "sua": String(localized: "다 마르면 구울게요. 약속은 아니에요.")],
        "jujube_seed": ["seri": String(localized: "대추씨, 싹은 아직이에요."), "sio": String(localized: "대추씨 심은 지 며칠. 세고 있어."), "seonbae": String(localized: "기다리는 동안 뭘 했어?"), "ir": String(localized: "기다림도 자라."), "sua": String(localized: "대추씨 아직 기다리는 중이에요.")],
        "elder_bead": ["seri": String(localized: "구슬은 하나뿐이에요."), "sio": String(localized: "구슬. 하나야."), "seonbae": String(localized: "하나뿐인 걸 왜 여기 뒀을까?"), "ir": String(localized: "원로가 준 거야. 하나뿐이야."), "sua": String(localized: "구슬에 제 얼굴이 비쳐요.")],
        "rice_seeds": ["seri": String(localized: "볍씨 주머니, 챙겨 뒀어요."), "sio": String(localized: "볍씨. 누가 싸 줬는지 알아."), "seonbae": String(localized: "누가 싸 줬는지 기억나?"), "ir": String(localized: "심을 곳은 네가 정해."), "sua": String(localized: "볍씨 주머니 들고 왔어요.")],
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

/// 위젯 대사 차례: 앱이 마지막으로 쓴 뒤 위젯이 새로 그려진 횟수 (↻ 포함). 앱이 다시 쓰면 0
enum WidgetLine {
    static func index() -> Int { Store.defaults.integer(forKey: "lineTaps") }
    /// 위젯이 새로 그려질 때. 여러 위젯이 한꺼번에 그려져도 한 번만 센다
    static func bump(minGap: TimeInterval = 300) {
        let d = Store.defaults, now = Date().timeIntervalSince1970
        guard now - d.double(forKey: "lineBumpAt") > minGap else { return }
        d.set(now, forKey: "lineBumpAt")
        d.set(index() + 1, forKey: "lineTaps")
    }
    static func reset() { Store.defaults.set(0, forKey: "lineTaps"); Store.defaults.set(Date().timeIntervalSince1970, forKey: "lineBumpAt") }
}

/// 작은 위젯 내 길·목표 페이스가 읽는 값 (앱이 small.json 으로 쓴다). 모두 앱의 계산값이다
struct WSmall: Codable {
    var path: [[Double]]     // 내 길 실제 평가액 [f, 원]: f = 시작 0 → 목표일 1. 마지막 점 = 앱이 쓴 때의 지금
    var need: [Double]       // 계획선 41점 (f = 0, 0.025 … 1)
    var gap: Double          // 계획선보다 앞섬(+)·뒤처짐(−) (앱 내 길 화면과 같은 값)
    var t0: Double           // 앱이 쓴 때의 위젯 총액. 위젯은 지금 총액과의 비율로 지금 점·앞섬을 맞춘다
    var me: Double?          // 평행 세계: 같은 날 출발한 뒤 진짜 나의 수익률 (모의 바구니가 없으면 nil)
    var basket: Double?      // 모의 바구니 수익률

    static let file = "small.json"
    static func load() -> WSmall? { Store.read(WSmall.self, file) }
    /// 지금 점 (앱이 쓴 뒤 받은 시세만큼)
    func nowValue(_ total: Double) -> Double { (path.last?.last ?? total) * (t0 > 0 && total > 0 ? total / t0 : 1) }
    func gapNow(_ total: Double) -> Double { t0 > 0 && total > 0 ? (1 + gap) * total / t0 - 1 : gap }

    /// 위젯 갤러리·연결 전 예시
    static var sample: WSmall {
        let need = (0...40).map { 4.8e8 * pow(1e9 / 4.8e8, Double($0) / 40) }
        let path = (0...10).map { i -> [Double] in let f = 0.25 * Double(i) / 10; return [f, 4.8e8 * pow(1e9 / 4.8e8, f) * (1 + 0.012 * sin(Double(i)) + 0.001 * Double(i))] }
        return WSmall(path: path, need: need, gap: 0.011, t0: 0, me: 0.084, basket: 0.078)
    }
}
