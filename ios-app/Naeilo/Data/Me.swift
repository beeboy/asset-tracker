import SwiftUI
import Observation

// 내 캐릭터(본인): 쉼터 '친구' 옆 여섯 번째 칸. 부품을 골라 만들고(저장 칸 1개), 열리면 홈·위젯에서 기본 인물 대신 보인다.
// 열리는 조건: ① 회복 루트 인터미션 4주를 모두 했거나 목표 루트에서 4주가 지남 → ② 평정 지수 성향 매칭 + ③ 다방커피 이상 후원.
// 만들기·저장은 앱 시작 3단계부터 할 수 있지만, 열리기 전에는 쉼터에서만 보인다 (홈·위젯에는 안 나온다).
// 캐릭터는 미션 진행(Progress)에 같이 들어가서 내 폰에 저장하기·불러오기와 기기 동기화로 종목과 함께 옮겨진다.
// 인터미션 날짜는 기기 시계가 아니라 naeilo.com 서버 시각(TrustedClock)으로 센다 (폰 시간을 바꿔 앞당길 수 없게).

struct MeChar: Codable, Equatable, Hashable {
    var name = ""
    var parts: [String: Int] = [:]
    var type = "seri"          // 매칭 전에 고른 투자 유형 (인물 id). 평정 지수가 매칭되면 그 유형을 따른다
    static let nameMax = 5     // 이름 글자 수: 위젯·말풍선 이름 자리 (기본 인물 이름이 두 글자)
    /// 지금 저장된 내 캐릭터 (AppModel.me 가 바뀔 때 같이). 환경 없이 그리는 곳(공유 카드 그림 등)에서 쓴다
    static var current: MeChar? = nil

    func part(_ g: MeGroup) -> Int { parts[g.rawValue] ?? g.first }
    func with(_ g: MeGroup, _ v: Int) -> MeChar {
        var c = self
        if g == .color && v > 100 { c.parts["skin"] = v - 100 } else { c.parts[g.rawValue] = v }
        return c
    }
    /// 고른 상태인지 (색깔 그룹의 피부색 포함)
    func picked(_ g: MeGroup, _ v: Int) -> Bool { g == .color && v > 100 ? (parts["skin"] ?? 1) == v - 100 : part(g) == v }
    var skin: Int { parts["skin"] ?? 1 }
    var shownName: String { name.isEmpty ? "나" : name }
}

/// 부품 그룹. 그림은 이미지 디자인 스레드가 만드는 me_* (24×34, 판 전체 크기로 겹침, MeArt.asset). 없으면 자리표시 도트
enum MeGroup: String, CaseIterable, Identifiable {
    case hair, color, face, eyes, mouth, top, bottom, glasses, mask, prop
    var id: String { rawValue }
    var label: String {
        switch self {
        case .hair: "헤어스타일"; case .color: "색깔"; case .face: "얼굴형"; case .eyes: "눈"; case .mouth: "입"
        case .top: "윗옷"; case .bottom: "아래옷"; case .glasses: "안경"; case .mask: "마스크"; case .prop: "소품"
        }
    }
    /// 고를 수 있는 수. 안경·마스크·소품은 0 = 없음
    var count: Int {
        switch self {
        case .color: MeColor.hair.count
        case .hair, .top: 8
        case .face: 4
        case .eyes, .mouth, .bottom, .prop: 6
        case .glasses: 4
        case .mask: 3
        }
    }
    var optional: Bool { self == .glasses || self == .mask || self == .prop }
    var first: Int { optional ? 0 : 1 }
    /// 색깔 그룹은 머리 색(1부터) 뒤에 피부색(101부터)을 같이 고른다 (parts["skin"])
    var options: [Int] { self == .color ? Array(1...count) + (1...MeColor.skin.count).map { 100 + $0 } : Array(first...count) }
    /// 쌓는 순서 (아래 → 위, 이미지 디자인 스레드 시안). body 는 손·목·다리 피부 (피부색 번호), '색깔'은 머리색·피부색을 고른다
    static let layers = ["body", "face", "bottom", "top", "hair", "eyes", "mouth", "mask", "glasses", "prop"]
}

/// 머리 색 (색깔 그룹) + 위젯 바탕 (인물 색 그라데이션: 강조, 진한 쪽, 밝은 쪽)
enum MeColor {
    // 수는 이미지 디자인 스레드 확정안에 맞춰 바꾼다 (머리 색·피부색 배열 길이가 곧 선택지 수)
    static let hair: [UInt32] = [0x2B2B33, 0x6B4430, 0xA0623A, 0xE2C36A, 0xC9CED8, 0x4F7BD9, 0xE88AB0, 0x4FBFA5, 0xB03A3A]
    static let names = ["검정", "갈색", "밤색", "금발", "은색", "파랑", "분홍", "민트", "빨강"]
    static let accent: [UInt32] = [0x9AA8D8, 0xD9A27A, 0xE8A070, 0xFFD36B, 0xDDE3F0, 0x7FB2FF, 0xFF9CC4, 0x6FE0C4, 0xFF8A80]
    static let g1: [UInt32] = [0x3A3F5C, 0x5C3E30, 0x7A4A2E, 0x6A5A2A, 0x4A5068, 0x2C3E8A, 0x7A3A58, 0x1F5C52, 0x6A2A2A]
    static let skin: [UInt32] = [0xF2C9A5, 0xD9A27A, 0x9C6B4A]
    static let skinNames = ["밝은 피부", "중간 피부", "어두운 피부"]
    static func skinIdx(_ c: MeChar) -> Int { max(0, min(skin.count - 1, c.skin - 1)) }
    static let g0: UInt32 = 0x161A2C
    static func idx(_ c: MeChar) -> Int { max(0, min(hair.count - 1, c.part(.color) - 1)) }
}

/// 쉼터 배경: 『중첩된 현실』 권마다 대표 쉼터 하나 (라이트·다크). 그림은 이미지 디자인 스레드가 만드는 bg_<NN> (128×80).
/// 그림이 오기 전에는 지금 방(art_room)을 색만 바꿔 자리표시로 쓴다
struct ShelterBG: Identifiable, Hashable {
    let id: String
    let name: String
    let intro: String
}

extension Shelter {
    static let backgrounds: [ShelterBG] = (1...10).map { ShelterBG(id: String(format: "bg_%02d", $0), name: "본편 \($0)권의 쉼터", intro: "그림 준비 중") }
        + [ShelterBG(id: "bg_11", name: "외전 『이종 공명』의 쉼터", intro: "그림 준비 중"), ShelterBG(id: "bg_12", name: "프리퀄의 쉼터", intro: "그림 준비 중")]
    static func background(_ id: String?) -> ShelterBG? { backgrounds.first { $0.id == id } }
}

// MARK: 서버 시각

/// 서버 시각: naeilo.com 응답의 Date 머리를 받고, 그 뒤로는 기기가 켜진 뒤 흐른 시간(잠자기 포함, 시계를 바꿔도 안 바뀜)을 더한다.
/// 기기를 다시 켜면 다시 받아야 한다. 받은 적이 없거나 다시 켠 뒤 인터넷이 없으면 nil (그동안 인터미션 체크인은 기다린다)
@Observable
final class TrustedClock {
    static let shared = TrustedClock()
    private var server = UserDefaults.standard.double(forKey: "tc.server")
    private var mono = UserDefaults.standard.double(forKey: "tc.mono")
    private var boot = UserDefaults.standard.string(forKey: "tc.boot") ?? ""
    @ObservationIgnored private var busy = false

    /// 켜진 뒤 흐른 시간 (초, 잠자기 포함)
    static var monoNow: Double { Double(clock_gettime_nsec_np(CLOCK_MONOTONIC)) / 1e9 }
    /// 이번에 켜진 기기 표시 (다시 켜면 바뀐다)
    static var bootID: String {
        var size = 0
        guard sysctlbyname("kern.bootsessionuuid", nil, &size, nil, 0) == 0, size > 0 else { return "" }
        var buf = [CChar](repeating: 0, count: size)
        guard sysctlbyname("kern.bootsessionuuid", &buf, &size, nil, 0) == 0 else { return "" }
        return String(cString: buf)
    }

    var now: Date? {
        guard server > 0, boot == Self.bootID else { return nil }
        let m = Self.monoNow
        guard m >= mono else { return nil }
        return Date(timeIntervalSince1970: server + (m - mono))
    }
    /// 기기 시계가 서버 시각과 5분 넘게 다르다 (폰 시간을 바꿨다)
    var skewed: Bool { now.map { abs(Date().timeIntervalSince($0)) > 300 } ?? false }

    @MainActor func sync() async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        for u in [Config.site, Config.relay] {
            var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: 10)
            r.httpMethod = "HEAD"
            guard let res = try? await URLSession.shared.data(for: r),
                  let h = (res.1 as? HTTPURLResponse)?.value(forHTTPHeaderField: "Date"), let d = Self.httpDate.date(from: h) else { continue }
            server = d.timeIntervalSince1970; mono = Self.monoNow; boot = Self.bootID
            let ud = UserDefaults.standard
            ud.set(server, forKey: "tc.server"); ud.set(mono, forKey: "tc.mono"); ud.set(boot, forKey: "tc.boot")
            return
        }
    }
    private static let httpDate: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "GMT")
        f.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return f
    }()
}

// MARK: 열림 조건·인터미션 날짜

enum WeekGate: Equatable { case open, wait(Int), offline, skew }

extension AppModel {
    private static let dayS = 86400.0

    /// 회복 루트 인터미션: 다음 주차 체크인이 열렸는지. 시작한 날부터 7일마다 한 주씩 (서버 시각 기준)
    var weekGate: WeekGate {
        if devAll { return .open }
        let c = TrustedClock.shared
        guard let now = c.now?.timeIntervalSince1970, let s = interStartAt else { return .offline }
        if c.skewed { return .skew }
        let left = s + Double(weeks.count) * 7 * Self.dayS - now
        return left <= 0 ? .open : .wait(Int(ceil(left / Self.dayS)))
    }
    var weekGateText: String? {
        switch weekGate {
        case .open: return nil
        case .wait(let d): return "\(weeks.count + 1)주차 체크인은 \(d)일 뒤에 열려요."
        case .offline: return "인터넷에 연결되면 날짜를 확인하고 열려요."
        case .skew: return "폰 시간이 실제 시간과 달라요. 설정 > 일반 > 날짜 및 시간에서 '자동으로 설정'을 켜 주세요."
        }
    }

    /// 서버 시각을 받은 뒤: 인터미션·목표 루트 시작 날을 정하고, 내 캐릭터 1차 조건을 확인한다
    func fixStarts() {
        let c = TrustedClock.shared
        guard let now = c.now?.timeIntervalSince1970, !c.skewed else { return }
        // 예전 진행(시작 날 없음)은 이미 지난 주만큼 앞당겨 둔다
        if route == .recover, done.contains(5), interStartAt == nil { interStartAt = now - Double(weeks.count) * 7 * Self.dayS }
        if isGoal, goalStartAt == nil { goalStartAt = now }
        if meGate1At == nil, meGate1Live(now) { meGate1At = now }
    }
    private func meGate1Live(_ now: Double) -> Bool {
        route == .recover ? done.contains(6) : goalStartAt.map { now - $0 >= 28 * Self.dayS } ?? false
    }

    /// ① 회복 루트 인터미션 모두 / 목표 루트 4주
    var meGate1: Bool { meGate1At != nil || (route == .recover && done.contains(6)) }
    var meGate1Text: String {
        if meGate1 { return route == .recover ? "회복 루트 인터미션 4주 모두" : "목표 루트 4주" }
        if route == .recover { return "회복 루트 인터미션 4주 모두 (\(weeks.count)/4주)" }
        guard let s = goalStartAt, let now = TrustedClock.shared.now?.timeIntervalSince1970 else { return "목표 루트에서 4주 지나기" }
        return "목표 루트에서 4주 지나기 (\(max(1, Int(ceil((s + 28 * Self.dayS - now) / Self.dayS))))일 남음)"
    }
    /// ② 평정 지수 성향 매칭
    var meGate2: Bool { calm.type != nil }
    /// ③ 다방커피 이상
    var meGate3: Bool { Support.shared.has(.dabang) }
    /// 홈·위젯에 내 캐릭터를 둘 수 있는지
    var meUnlocked: Bool { me != nil && (devAll || (meGate1 && meGate2 && meGate3)) }

    /// 내 캐릭터의 말투 = 투자 유형 인물 (매칭되면 평정 지수 유형, 아니면 고른 유형)
    var meVoice: String { calm.type ?? me?.type ?? "seri" }
    /// 대사·말투를 고를 인물 id ("me" 는 유형 인물)
    func voice(_ id: String) -> String { id == "me" ? meVoice : id }
    func friendName(_ id: String) -> String {
        id == "me" ? (me?.shownName ?? "나") : Shelter.friends.first { $0.id == id }?.name ?? "세리"
    }
    /// 미리 보기: 못 만난 친구는 믹스커피, 안 돌아온 물건은 다방커피 이상 (여는 건 여전히 미션으로)
    var peekFriends: Bool { Support.shared.tier >= .mix }
    var peekItems: Bool { Support.shared.tier >= .dabang }
    /// 쉼터 배경 바꾸기: 프랜차이즈 커피 이상
    var bgUnlocked: Bool { Support.shared.has(.franchise) }
    /// 지금 쉼터 방 그림 (배경을 고르지 않았거나 잠기면 기본 방)
    var roomBG: String? { bgUnlocked ? meBg : nil }
}
