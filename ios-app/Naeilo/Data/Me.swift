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
    static let nameMax = 6     // 이름 글자 수 (공백 하나 포함): 위젯·말풍선 이름 자리
    /// 지금 저장된 내 캐릭터 (AppModel.me 가 바뀔 때 같이). 환경 없이 그리는 곳(공유 카드 그림 등)에서 쓴다
    static var current: MeChar? = nil

    /// 고른 번호 (예전 자리표시 시절 번호가 지금 수보다 크면 끝 번호로)
    func part(_ g: MeGroup) -> Int { min(parts[g.rawValue] ?? g.first, g.count) }
    func with(_ g: MeGroup, _ v: Int) -> MeChar {
        var c = self
        if g == .color && v > 100 { c.parts["skin"] = v - 100 } else { c.parts[g.rawValue] = v }
        return c
    }
    /// 고른 상태인지 (색깔 그룹의 피부색 포함)
    func picked(_ g: MeGroup, _ v: Int) -> Bool { g == .color && v > 100 ? (parts["skin"] ?? 1) == v - 100 : part(g) == v }
    var skin: Int { min(max(parts["skin"] ?? 1, 1), MeColor.skin.count) }
    var shownName: String { name.isEmpty ? "나" : String(name.prefix(Self.nameMax)) }
    /// 이름 입력 다듬기: 공백은 하나까지(앞 공백 없음), 공백 포함 6자까지
    static func clean(_ v: String) -> String {
        var out = "", space = false
        for ch in v where out.count < nameMax {
            if ch.isWhitespace {
                if space || out.isEmpty { continue }
                space = true; out.append(" ")
            } else { out.append(ch) }
        }
        return out
    }
}

/// 부품 그룹. 그림은 이미지 디자인 스레드가 만드는 me_* (24×34, 판 전체 크기로 겹침, MeArt.asset). 그림이 없는 번호만 자리표시 도트
enum MeGroup: String, CaseIterable, Identifiable {
    case hair, color, face, eyes, mouth, top, bottom, glasses, mask, prop
    var id: String { rawValue }
    var label: String {
        switch self {
        case .hair: "헤어스타일"; case .color: "색깔"; case .face: "얼굴형"; case .eyes: "눈"; case .mouth: "입"
        case .top: "윗옷"; case .bottom: "아래옷"; case .glasses: "안경"; case .mask: "마스크"; case .prop: "소품"
        }
    }
    /// 고를 수 있는 수 (= 부품 이름 수). 안경·마스크·소품은 0 = 없음
    var count: Int { self == .color ? MeColor.hair.count : Self.names[rawValue]?.count ?? 0 }
    /// 부품 이름 (o 번, 1부터)
    func name(_ o: Int) -> String {
        if self == .color { return o > 100 ? MeColor.skinNames[o - 101] : MeColor.names[o - 1] }
        return o == 0 ? "없음" : Self.names[rawValue]?[o - 1] ?? "\(o)"
    }
    /// 부품 이름 (번호 순, me-parts/names.json)
    static let names: [String: [String]] = [
        "hair": ["볼륨펌", "중단발", "긴 머리", "땋은 머리", "후드", "짧은 머리", "포니테일", "양쪽 똥머리", "삐죽 머리"],
        "face": ["기본", "둥근", "갸름한", "각진"],
        "eyes": ["점 눈", "푸른 렌즈", "금빛 눈", "새까만 눈", "늦게 깜빡이는 눈", "큰 눈", "웃는 눈", "윙크"],
        "mouth": ["기본", "작은 입", "일자", "미소", "활짝"],
        "top": ["셔츠", "블라우스", "크롭 티", "긴 재킷", "은색 기체", "앞치마", "관리국 제복", "재킷", "원피스"],
        "bottom": ["청바지", "슬랙스", "반바지", "맨발", "긴 치마", "치마", "트레이닝"],
        "glasses": ["헤드셋", "동그란 안경", "네모 안경", "반무테", "선글라스"],
        "mask": ["이목구비 없는 얼굴", "호흡기", "흰 마스크", "검정 마스크"],
        "prop": ["보리차", "휴대전화", "노트", "푸른 손빛", "고양이", "두꺼운 그릇", "가위", "보자기 꾸러미", "볍씨 주머니"],
    ]
    var optional: Bool { self == .glasses || self == .mask || self == .prop }
    var first: Int { optional ? 0 : 1 }
    /// 색깔 그룹은 머리 색(1부터) 뒤에 피부색(101부터)을 같이 고른다 (parts["skin"])
    var options: [Int] { self == .color ? Array(1...count) + (1...MeColor.skin.count).map { 100 + $0 } : Array(first...count) }
    /// 쌓는 순서 (아래 → 위, 이미지 디자인 스레드 시안). body 는 손·목·다리 피부 (피부색 번호), '색깔'은 머리색·피부색을 고른다
    static let layers = ["body", "face", "bottom", "top", "hair", "eyes", "mouth", "mask", "glasses", "prop"]
}

/// 머리 색 (색깔 그룹) + 위젯 바탕 (인물 색 그라데이션: 강조, 진한 쪽, 밝은 쪽)
enum MeColor {
    // 이미지 디자인 스레드 확정안 (me-parts/colors.json). 배열 길이가 곧 선택지 수
    static let hair: [UInt32] = [0x3A2A26, 0x7A5134, 0xE0B25A, 0xA9ADB4, 0xE4E6EA, 0xB5523B, 0x4A6FB0, 0x7CC8B4, 0xD8C48A]
    static let names = ["검정", "갈색", "금발", "애쉬그레이", "은발", "빨강", "파랑", "민트", "희끗한 금빛"]
    /// 위젯 바탕: 머리색을 밝게(강조) · 어둡게(진한 쪽)
    static var accent: [UInt32] { hair.map { mix($0, 0xFFFFFF, 0.35) } }
    static var g1: [UInt32] { hair.map { mix($0, 0x10131F, 0.6) } }
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        [16, 8, 0].reduce(UInt32(0)) { r, sh in
            let x = Double((a >> UInt32(sh)) & 0xFF), y = Double((b >> UInt32(sh)) & 0xFF)
            return r | (UInt32((x + (y - x) * t).rounded()) << UInt32(sh))
        }
    }
    static let skin: [UInt32] = [0xF6D2B0, 0xE0B08A, 0xA67A57]
    static let skinNames = ["밝은 피부", "중간 피부", "짙은 피부"]
    static func skinIdx(_ c: MeChar) -> Int { max(0, min(skin.count - 1, c.skin - 1)) }
    static let g0: UInt32 = 0x161A2C
    static func idx(_ c: MeChar) -> Int { max(0, min(hair.count - 1, c.part(.color) - 1)) }
}

/// 쉼터 배경: 『중첩된 현실』 권마다 대표 쉼터 하나 (라이트·다크). 그림은 이미지 디자인 스레드가 만드는 bg_<NN> (128×80 ×5, 다크는 _dark).
struct ShelterBG: Identifiable, Hashable {
    let id: String
    let name: String
    let intro: String
    let volume: String
}

extension Shelter {
    /// 이미지 디자인 스레드 확정안 (shelter-bg/list.json): bg_01 프리퀄, bg_02~11 본편 1~10권, bg_12 외전
    static let backgrounds: [ShelterBG] = [
        ShelterBG(id: "bg_01", name: "시뮬레이션 속 미용실", intro: "거울 셋에 조금씩 다른 얼굴. 가위 소리가 사각, 잠시 멈춤, 사각.", volume: "프리퀄"),
        ShelterBG(id: "bg_02", name: "백룸의 노란 복도", intro: "노란 벽과 떨리는 형광등, 끝없는 복도. 밤에는 출구에만 노란빛 한 줄.", volume: "1권"),
        ShelterBG(id: "bg_03", name: "화성 제2돔 거주 구역", intro: "아키다리아 제2돔. 붉은 평원이 보이는 창과 녹슨 크랭크 상자.", volume: "2권"),
        ShelterBG(id: "bg_04", name: "아르켄의 정자", intro: "물 위 첨탑과 구름 다리, 그리고 가운데의 원형 구조물.", volume: "3권"),
        ShelterBG(id: "bg_05", name: "정거장 대합실", intro: "아시아 입구와 이름 모를 입구 사이의 손바닥만 한 틈.", volume: "4권"),
        ShelterBG(id: "bg_06", name: "아르켄 운하 둑", intro: "운하 건너 백 명의 마을. 감각 연구소 창에 걸린 파형.", volume: "5권"),
        ShelterBG(id: "bg_07", name: "요양 시설 301호", intro: "흙길과 지붕들이 보이는 창. 손가락 몇 개만큼만 연다.", volume: "6권"),
        ShelterBG(id: "bg_08", name: "해 질 녘 원형 광장", intro: "빈 현수막과 줄표 하나. 멀리 선 사람들.", volume: "7권"),
        ShelterBG(id: "bg_09", name: "들판", intro: "붉은 노을과 강, 밤에는 지나치게 많은 별. 충전할 곳은 없다.", volume: "8권"),
        ShelterBG(id: "bg_10", name: "화산 아래 논", intro: "응아디레조의 계단식 논과 대추나무, 연기 오르는 화산.", volume: "9권"),
        ShelterBG(id: "bg_11", name: "응아디레조 집 작업실", intro: "물레와 안 구운 그릇들, 진흙으로 메운 벽 금, 마당의 우물.", volume: "10권"),
        ShelterBG(id: "bg_12", name: "도시의 시오 방", intro: "도시 불빛이 보이는 방. 휴대전화 속 세리의 프로필.", volume: "외전")
    ]
    static func background(_ id: String?) -> ShelterBG? { backgrounds.first { $0.id == id } }
}

/// 소설 속 인물 샘플 조합 (me-parts/mannequins.json). 만들기 화면에서 마네킹처럼 골라 시작한다
struct MeSample: Identifiable {
    let name: String
    let about: String
    let parts: [String: Int]
    var id: String { name }
    static let all: [MeSample] = [
        MeSample(name: "현실의 시오", about: "원래 짧게 깎은 검은 머리. 백룸에서 맨발로 5년 반을 지냄.", parts: ["hair": 6, "face": 1, "eyes": 1, "mouth": 1, "top": 1, "bottom": 4, "glasses": 0, "mask": 0, "prop": 0, "color": 1, "skin": 1]),
        MeSample(name: "묶은 머리 시오", about: "2권부터 어중간한 길이를 고무줄 세 번으로 묶음. 0.1초 늦게 깜빡이는 눈.", parts: ["hair": 7, "face": 1, "eyes": 5, "mouth": 1, "top": 1, "bottom": 2, "glasses": 0, "mask": 0, "prop": 2, "color": 4, "skin": 1]),
        MeSample(name: "1권 선배", about: "절반쯤 희끗한 금빛 중단발. 질문으로 말함.", parts: ["hair": 2, "face": 1, "eyes": 1, "mouth": 1, "top": 2, "bottom": 1, "glasses": 0, "mask": 0, "prop": 3, "color": 9, "skin": 1]),
        MeSample(name: "외전 세리", about: "휴대전화 속 AI. 몸은 없고 금발 어깨 중단발 프로필을 받음.", parts: ["hair": 2, "face": 3, "eyes": 2, "mouth": 1, "top": 9, "bottom": 6, "glasses": 0, "mask": 0, "prop": 4, "color": 3, "skin": 1]),
        MeSample(name: "아내", about: "이름이 끝까지 나오지 않음. 흙을 빚고, 묻지 않고 밥을 냄.", parts: ["hair": 8, "face": 1, "eyes": 7, "mouth": 1, "top": 6, "bottom": 5, "glasses": 0, "mask": 0, "prop": 6, "color": 2, "skin": 1]),
        MeSample(name: "온유", about: "요양 시설에서 방마다 창문 폭을 정함. 왼쪽부터 세 갈래로 땋은 머리.", parts: ["hair": 4, "face": 1, "eyes": 1, "mouth": 2, "top": 8, "bottom": 1, "glasses": 0, "mask": 0, "prop": 0, "color": 1, "skin": 2]),
        MeSample(name: "루", about: "배송 유닛 XX-2214. 열여섯 살쯤의 키. 아내의 보자기 꾸러미를 안고 옴.", parts: ["hair": 6, "face": 1, "eyes": 2, "mouth": 3, "top": 5, "bottom": 2, "glasses": 0, "mask": 0, "prop": 8, "color": 5, "skin": 1]),
        MeSample(name: "미용사", about: "시뮬레이션 속 미용실. \"선택하지 않은 얼굴은 모두 잘라낼게요.\"", parts: ["hair": 9, "face": 1, "eyes": 1, "mouth": 1, "top": 6, "bottom": 2, "glasses": 0, "mask": 0, "prop": 7, "color": 6, "skin": 1]),
        MeSample(name: "활동가", about: "잘린 문장을 현수막에 끝내 붙임. 7권에서 머리가 짧아짐.", parts: ["hair": 6, "face": 1, "eyes": 1, "mouth": 1, "top": 3, "bottom": 7, "glasses": 0, "mask": 3, "prop": 3, "color": 2, "skin": 2]),
        MeSample(name: "담당관", about: "\"솔직히 저도 당혹스럽습니다.\" 8권에서 머리가 희끗해짐.", parts: ["hair": 6, "face": 4, "eyes": 1, "mouth": 3, "top": 8, "bottom": 2, "glasses": 4, "mask": 0, "prop": 0, "color": 9, "skin": 1]),
        MeSample(name: "니아", about: "화성 아키다리아 제2돔 거주 구역 C의 열세 살. 매일 지구에 녹음을 보냄.", parts: ["hair": 6, "face": 2, "eyes": 1, "mouth": 1, "top": 7, "bottom": 2, "glasses": 0, "mask": 2, "prop": 2, "color": 2, "skin": 3]),
        MeSample(name: "관리국의 수아", about: "정거장관리국 하급 연구원. \"또 올게요. 약속은 아니에요.\"", parts: ["hair": 4, "face": 1, "eyes": 4, "mouth": 1, "top": 7, "bottom": 2, "glasses": 0, "mask": 0, "prop": 9, "color": 1, "skin": 1])
    ]
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
