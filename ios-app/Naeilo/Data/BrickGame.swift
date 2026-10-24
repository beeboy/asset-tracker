import SwiftUI
import UIKit
import CoreMotion
import Security
import AVFoundation

// 판 깨기: 1000칸으로 하는 거꾸로 벽돌깨기. 채운 칸이 벽돌이고, 판(패들)은 위에, 공은 아래 벽돌로 떨어진다.
// 실제 칸은 바뀌지 않는다. 단계 = 『중첩된 현실』 쉼터 12곳(권마다 규칙 하나), 선수 = 기본 인물(+내 캐릭터, 스킬 하나),
// 아이템 = 쉼터에 돌아온 물건(모은 것만 떨어진다). 기록은 이 폰 안에만 저장한다 (UserDefaults game.v1, 개인 정보 없음).
// 판의 좌표: 가로 40칸 × 세로 46줄. 위 0줄 밖으로 공이 나가면 놓친 것, 21~45줄이 1000칸 (아래부터 채움, CellGrid 와 같은 배치).

enum GameLevel: String, CaseIterable, Codable, Identifiable {
    case easy, normal, hard
    var id: String { rawValue }
    var title: String { switch self { case .easy: String(localized: "하수"); case .normal: String(localized: "중수"); case .hard: String(localized: "고수") } }
    var speed: Double { switch self { case .easy: 21; case .normal: 24; case .hard: 27 } }      // 칸/초
    var paddle: Double { switch self { case .easy: 9; case .normal: 8; case .hard: 7 } }     // 판 길이 (칸)
    var lives: Int { switch self { case .easy: 5; case .normal: 4; case .hard: 3 } }
    /// 단계를 깨는 기준: 깰 수 있는 벽돌 중 이만큼
    var clear: Double { switch self { case .easy: 0.6; case .normal: 0.75; case .hard: 0.9 } }
    var note: String {
        switch self {
        case .easy: String(localized: "벽돌 750개 · 60% 깨면 다음 단계")
        case .normal: String(localized: "벽돌 800개 · 75% 깨면 다음 단계")
        case .hard: String(localized: "벽돌 850개 · 90% 깨면 다음 단계")
        }
    }
    /// 한 번 맞히면 같이 깨지는 칸 (가운데 기준 (가로, 세로))
    var burst: [(Int, Int)] {
        let plus = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]
        switch self {
        case .hard: return plus
        case .normal: return plus + [(1, 1), (1, -1), (-1, 1), (-1, -1)]
        case .easy: return plus + [(1, 1), (1, -1), (-1, 1), (-1, -1), (2, 0), (-2, 0), (0, 2), (0, -2)]
        }
    }
    /// 연습판 벽돌 수 (단계마다 10개씩 늘어난다)
    var bricks: Int { switch self { case .easy: 750; case .normal: 800; case .hard: 850 } }
    var easier: GameLevel? { switch self { case .easy: nil; case .normal: .easy; case .hard: .normal } }
}

/// 단계: 쉼터 배경 하나와 그 권의 규칙
struct GameStage: Identifiable {
    let n: Int
    let rule: String
    /// 시작 카드: 장면 한 줄 · 규칙 한 줄 (글줄이기 2026-10-18)
    var scene = "", short = ""
    var speedMul = 1.0, paddleMul = 1.0, extraLives = 0, items = true
    var mirror = false        // 프리퀄: 거울에 공이 비쳐 보임 (그림만)
    var flicker = false       // 1권: 형광등이 깜빡여 벽돌이 잠깐 안 보임, 출구 칸만 빛남
    var gravity = 0.0         // +: 벽돌 쪽으로 (2권 화성), −: 판 쪽으로 (9권 화산 연기)
    var well = false          // 3권: 가운데 원형 구조물이 공을 끌어당겨 휜다
    var portal = false        // 4권: 양쪽 벽이 입구, 반대편으로 나온다
    var sway = false          // 5권: 벽돌 줄이 물결처럼 흔들림
    var dash = false          // 7권: 줄표(—) 벽은 깨지지 않는다
    var hard2 = 0.0           // 10권: 안 구운 그릇 벽돌 (세 번 쳐야 깨짐) 비율
    var id: Int { n }
    /// 이 단계의 벽돌 수: 난이도 기본 + 단계마다 10개 (내 채운 칸이 먼저, 모자라면 위에 쌓는다)
    func bricks(_ lv: GameLevel) -> Int { min(1000, lv.bricks + 10 * n) }
    static let specials = 10
    /// 두 번 맞아야 깨지는 벽돌 비율 (나머지는 한 번)
    static let tough = 0.1
    /// 숨은 테트리스 블럭 수: 하수 3권·중수 2권·고수 1권부터 4개, 그 뒤 단계마다 2개씩
    func tetrisCount(_ lv: GameLevel) -> Int {
        let start: Int = switch lv { case .easy: 3; case .normal: 2; case .hard: 1 }
        return n < start ? 0 : 4 + 2 * (n - start)
    }
    var bg: ShelterBG { Shelter.backgrounds[n] }

    static let all: [GameStage] = [
        GameStage(n: 0, rule: String(localized: "연습판. 거울 셋에 공이 비쳐 보여요. 공이 느리고 목숨이 둘 더 있어요."), scene: String(localized: "거울 셋, 조금씩 다른 얼굴"), short: String(localized: "연습판 · 공 느림 · 목숨 +2"), speedMul: 0.85, extraLives: 2, mirror: true),
        GameStage(n: 1, rule: String(localized: "형광등이 가끔 깜빡여 벽돌이 안 보여요. 노랗게 빛나는 출구 칸은 보너스."), scene: String(localized: "떨리는 형광등, 끝없는 복도"), short: String(localized: "형광등 깜빡임 · 노란 출구 칸 +100"), flicker: true),
        GameStage(n: 2, rule: String(localized: "중력이 약해요. 공이 느리고 벽돌 쪽으로 천천히 휘어요."), scene: String(localized: "붉은 평원이 보이는 창"), short: String(localized: "약한 중력 · 공 느림 · 벽돌 쪽으로 휨"), speedMul: 0.85, gravity: 7),
        GameStage(n: 3, rule: String(localized: "가운데 원형 구조물이 지나가는 공을 끌어당겨요."), scene: String(localized: "물 위 첨탑과 원형 구조물"), short: String(localized: "가운데 구조물이 공을 끌어당김"), well: true),
        GameStage(n: 4, rule: String(localized: "양쪽 벽이 입구예요. 공이 반대편 입구로 나와요."), scene: String(localized: "두 입구 사이의 틈"), short: String(localized: "양쪽 벽이 입구 · 반대편으로 나옴"), portal: true),
        GameStage(n: 5, rule: String(localized: "벽돌 줄이 운하 물결처럼 천천히 흔들려요."), scene: String(localized: "운하 건너 백 명의 마을"), short: String(localized: "벽돌 줄이 물결처럼 흔들림"), sway: true),
        GameStage(n: 6, rule: String(localized: "창이 좁아 판이 짧아요. 대신 공이 느려요."), scene: String(localized: "손가락 몇 개만큼 연 창"), short: String(localized: "판 짧음 · 공 느림"), speedMul: 0.8, paddleMul: 0.75),
        GameStage(n: 7, rule: String(localized: "가운데 줄표(—)는 깨지지 않는 벽이에요."), scene: String(localized: "빈 현수막과 줄표 하나"), short: String(localized: "줄표(—)는 안 깨지는 벽"), dash: true),
        GameStage(n: 8, rule: String(localized: "충전할 곳이 없어요. 물건이 떨어지지 않아요."), scene: String(localized: "붉은 노을, 충전할 곳 없음"), short: String(localized: "물건 안 떨어짐"), items: false),
        GameStage(n: 9, rule: String(localized: "화산 연기가 공을 판 쪽으로 밀어 올려요."), scene: String(localized: "계단식 논과 연기 오르는 화산"), short: String(localized: "연기가 공을 판 쪽으로 밂"), gravity: -6),
        GameStage(n: 10, rule: String(localized: "안 구운 그릇 벽돌은 세 번 쳐야 깨져요."), scene: String(localized: "물레와 안 구운 그릇"), short: String(localized: "안 구운 그릇 · 세 번 쳐야 깨짐"), hard2: 0.3),
        GameStage(n: 11, rule: String(localized: "모든 규칙이 조금씩 섞인 자유판."), scene: String(localized: "도시 불빛이 보이는 방"), short: String(localized: "모든 규칙 조금씩 섞임"), flicker: true, portal: true, sway: true, hard2: 0.15),
    ]
}

/// 선수: 기본 인물 다섯 (+내 캐릭터는 평정 지수 유형의 스킬)
enum GamePlayer {
    static func skill(_ id: String) -> String {
        switch id {
        case "sio": String(localized: "안전망 · 판 뒤 노란 줄이 공을 한 번 받아 줘요")
        case "seonbae": String(localized: "되묻기 · 놓친 공을 판마다 한 번 되감아요")
        case "ir": String(localized: "앞서 달림 · 공이 빠르고 점수 1.5배")
        case "sua": String(localized: "자라기 · 깰수록 판이 조금씩 길어져요")
        default: String(localized: "원칙 · 판이 길고 공 속도가 늘 같아요")
        }
    }
    /// 물건을 먹었을 때 뜨는 짧은 말 (예: 현관 등 · 10초 공 길 보임)
    static func itemShort(_ id: String) -> String {
        switch id {
        case "barley_tea": String(localized: "10초 공 느림")
        case "porch_light": String(localized: "10초 공 길 보임")
        case "wall_clock": String(localized: "3초 공 멈춤")
        case "hair_tie": String(localized: "15초 판 길게")
        case "table_chair": String(localized: "공 하나 더")
        case "asym_bowl": String(localized: "10초 벽돌 많은 쪽으로")
        case "unfired_bowl": String(localized: "보호막 1번")
        case "jujube_seed": String(localized: "10초 점수 2배")
        case "elder_bead": String(localized: "5초 벽돌 뚫기")
        case "rice_seeds": String(localized: "공 셋으로")
        default: item(id)
        }
    }
    /// 쉼터 물건이 아이템일 때 하는 일
    static func item(_ id: String) -> String {
        switch id {
        case "barley_tea": String(localized: "공이 10초 느려져요")
        case "porch_light": String(localized: "10초 동안 공이 갈 길이 보여요")
        case "wall_clock": String(localized: "공이 3초 멈춰요")
        case "hair_tie": String(localized: "판이 15초 길어져요")
        case "table_chair": String(localized: "공이 하나 더")
        case "asym_bowl": String(localized: "10초 동안 판에 맞은 공이 벽돌이 많은 쪽으로 꺾여요")
        case "unfired_bowl": String(localized: "위쪽에 한 번 막아 주는 보호막")
        case "jujube_seed": String(localized: "10초 동안 점수 2배")
        case "elder_bead": String(localized: "5초 동안 공이 벽돌을 뚫고 지나가요")
        case "rice_seeds": String(localized: "공이 셋으로 갈라져요")
        default: ""
        }
    }
}

/// 게임 대사: 상황 × 인물 줄 모음 + 쉼터 물건 대사(lines_ko.json 의 item.<물건>.<인물>)를 섞고,
/// 앞말·뒷말을 따로 골라 이어 붙여 같은 문장이 자주 반복되지 않게 한다. 한 바퀴 돌기 전에는 같은 줄을 다시 쓰지 않는다
enum GameLines {
    enum Event: String { case start, item, lose, clear, over, half, rewind, last }

    private static let pool: [String: [String: [String]]] = [
        "seri": [
            "start": [String(localized: "관찰을 시작합니다."), String(localized: "{stage}. 기록을 켭니다."), String(localized: "준비됐어요. 공은 아래로 가요."), String(localized: "한 박자 두고, 시작해요."), String(localized: "벽돌 {left}칸. 확인했어요.")],
            "lose": [String(localized: "공 하나를 놓쳤어요. 남은 공 {n}개."), String(localized: "기록했어요. 다음 공이 있어요."), String(localized: "거기까지만요. 다시 받으면 돼요."), String(localized: "위치가 조금 빨랐어요.")],
            "clear": [String(localized: "기준 칸에 닿았어요."), String(localized: "{stage}, 통과로 기록할게요."), String(localized: "{score}점. 사실 그대로예요."), String(localized: "다음 방이 열렸어요.")],
            "over": [String(localized: "여기까지예요. 다시 하면 돼요."), String(localized: "공이 다 떨어졌어요. 기록은 남았어요."), String(localized: "오늘은 {broken}칸까지였어요.")],
            "half": [String(localized: "절반을 넘었어요."), String(localized: "기준의 반쯤 왔어요.")],
            "rewind": [String(localized: "한 번은 되돌려요.")],
        ],
        "sio": [
            "start": [String(localized: "서두르지 않아도 돼."), String(localized: "놓쳐도 괜찮아, 한 번은 받아 줄게."), String(localized: "{stage}이야. 천천히 가 보자."), String(localized: "기다리는 것도 실력이야."), String(localized: "준비되면 눌러.")],
            "lose": [String(localized: "괜찮아. 한 박자 쉬고 다시."), String(localized: "놓쳐도 돼. 공은 {n}개 더 있어."), String(localized: "조금 기다렸다 받아 봐."), String(localized: "급했지? 숨 한 번.")],
            "clear": [String(localized: "기다린 만큼 깼네."), String(localized: "{stage}, 지나왔다."), String(localized: "{score}점. 좋은 기다림이었어."), String(localized: "다음 방으로 가자.")],
            "over": [String(localized: "오늘은 여기까지. 내일 또 하자."), String(localized: "괜찮아, 다시 기다리면 돼."), String(localized: "{broken}칸이나 깼어.")],
            "half": [String(localized: "반 왔어. 그대로."), String(localized: "절반이야. 서두르지 마.")],
            "rewind": [String(localized: "한 번 더 기다려 줄게.")],
        ],
        "seonbae": [
            "start": [String(localized: "어디로 보낼 건데?"), String(localized: "{stage}. 이 방, 어떻게 깰 생각이야?"), String(localized: "한 번은 되돌릴 수 있어. 알지?"), String(localized: "그게 정말 네 계획이야?"), String(localized: "시작해 봐. 질문은 나중에.")],
            "lose": [String(localized: "왜 놓쳤는지 알겠어?"), String(localized: "방금 그건 판 탓이야, 네 탓이야?"), String(localized: "남은 공 {n}개. 다음엔 어디서 받을래?"), String(localized: "너무 끝으로 갔지?")],
            "clear": [String(localized: "그게 정말 네 실력이야? 맞네."), String(localized: "{stage} 통과. 다음 질문으로 가자."), String(localized: "{score}점이면 됐어. 다음은?"), String(localized: "어떻게 깼는지 말할 수 있어?")],
            "over": [String(localized: "다시 하면 뭘 바꿀 거야?"), String(localized: "끝났네. 그래서 배운 건?"), String(localized: "{broken}칸. 나쁘지 않은데, 다음은?")],
            "half": [String(localized: "반 왔어. 여기서 뭘 바꿀래?"), String(localized: "절반. 계속 이대로?")],
            "rewind": [String(localized: "한 번 되돌려 줄게. 이번엔 어디로?")],
        ],
        "ir": [
            "start": [String(localized: "빨리 가자!"), String(localized: "{stage}! 바로 시작!"), String(localized: "점수는 더 줄게. 달려!"), String(localized: "벽돌 {left}칸, 다 깨 버리자!"), String(localized: "준비됐지? 간다!")],
            "lose": [String(localized: "앗, 너무 빨랐나!"), String(localized: "괜찮아, 공 {n}개 남았어!"), String(localized: "다시! 바로 다시!"), String(localized: "아깝다! 한 번 더!")],
            "clear": [String(localized: "벌써 끝! 다음 판!"), String(localized: "{stage} 통과! 빠르다!"), String(localized: "{score}점! 더 갈 수 있어!"), String(localized: "다음 방 열렸어, 가자!")],
            "over": [String(localized: "너무 달렸나. 한 번 더!"), String(localized: "끝! 근데 다시 하고 싶다!"), String(localized: "{broken}칸! 다음엔 더!")],
            "half": [String(localized: "반 왔어! 속도 올려!"), String(localized: "절반! 계속 계속!")],
            "rewind": [String(localized: "한 번 더 달려!")],
        ],
        "sua": [
            "start": [String(localized: "깰수록 판이 자라요. 천천히요."), String(localized: "{stage}이에요. 같이 가요."), String(localized: "조금씩 하면 돼요."), String(localized: "오늘은 어디까지 자랄까요?"), String(localized: "준비되면 눌러 주세요.")],
            "lose": [String(localized: "괜찮아요. 다시 자라면 돼요."), String(localized: "공이 {n}개 남았어요."), String(localized: "조금 아쉬웠어요. 다음엔 더 잘될 거예요."), String(localized: "천천히, 판을 가운데로요.")],
            "clear": [String(localized: "조금씩 자라서 여기까지 왔어요."), String(localized: "{stage}, 지나왔어요."), String(localized: "{score}점이에요. 많이 컸어요."), String(localized: "다음 방이 열렸어요.")],
            "over": [String(localized: "다음엔 판이 더 일찍 자랄 거예요."), String(localized: "오늘은 {broken}칸 자랐어요."), String(localized: "쉬었다가 다시 해요.")],
            "half": [String(localized: "절반 왔어요. 판도 자랐어요."), String(localized: "반쯤 왔어요. 잘하고 있어요.")],
            "rewind": [String(localized: "한 번 더 해 봐요.")],
        ],
        "me": [
            "start": [String(localized: "내 판이야. 가 보자."), String(localized: "{stage}. 시작!"), String(localized: "벽돌 {left}칸, 해 보자."), String(localized: "준비됐다.")],
            "lose": [String(localized: "놓쳤다. 공 {n}개 남았어."), String(localized: "다시 받자."), String(localized: "아깝다.")],
            "clear": [String(localized: "해냈다."), String(localized: "{stage} 통과!"), String(localized: "{score}점!")],
            "over": [String(localized: "다시 해 보자."), String(localized: "{broken}칸까지 왔어.")],
            "half": [String(localized: "반 왔다.")],
            "rewind": [String(localized: "한 번 더.")],
        ],
    ]
    /// 옆에서 끼어드는 다른 인물의 한마디 (상황별)
    private static let guest: [String: [String: [String]]] = [
        "seri": ["last": [String(localized: "마지막 공이에요. 가운데를 지켜요."), String(localized: "마지막 공. 관찰하고 받아요.")], "half": [String(localized: "절반 지났어요. 기록 중이에요.")],
                 "clear": [String(localized: "통과 확인했어요."), String(localized: "저도 봤어요. 깔끔했어요.")], "over": [String(localized: "기록은 남았어요. 다음에 이어 가요.")], "item": [String(localized: "그 물건, 쉼터에서 본 거예요.")]],
        "sio": ["last": [String(localized: "마지막이야. 서두르지 말고."), String(localized: "하나 남았어. 기다렸다 받아.")], "half": [String(localized: "반 왔네. 잘하고 있어.")],
                "clear": [String(localized: "봤어? 기다린 보람 있지."), String(localized: "멋졌어.")], "over": [String(localized: "괜찮아. 다음엔 내가 옆에 있을게.")], "item": [String(localized: "오, 그거 쓸모 있지.")]],
        "seonbae": ["last": [String(localized: "마지막 공이야. 어디서 받을 거야?"), String(localized: "하나 남았는데, 계획 있어?")], "half": [String(localized: "반이야. 이대로 갈 거야?")],
                    "clear": [String(localized: "깼네. 다음 방은 쉬울 것 같아?"), String(localized: "나쁘지 않았어.")], "over": [String(localized: "뭘 바꾸면 될지 알겠지?")], "item": [String(localized: "그거 언제 쓸지 생각했어?")]],
        "ir": ["last": [String(localized: "마지막! 할 수 있어!"), String(localized: "하나 남았어, 집중!")], "half": [String(localized: "반 왔다! 더 빨리!")],
               "clear": [String(localized: "와, 빨랐다!"), String(localized: "최고! 다음 판 가자!")], "over": [String(localized: "한 번 더 하자, 한 번만!")], "item": [String(localized: "좋은 거 먹었다!")]],
        "sua": ["last": [String(localized: "마지막 공이에요. 천천히요."), String(localized: "하나 남았어요. 응원할게요.")], "half": [String(localized: "절반이에요. 많이 자랐어요.")],
                "clear": [String(localized: "축하해요!"), String(localized: "같이 와서 좋았어요.")], "over": [String(localized: "쉬었다가 같이 다시 해요.")], "item": [String(localized: "그 물건, 반가워요.")]],
    ]
    static func guestLine(_ e: Event, _ who: String) -> String? {
        if who.hasPrefix("s:") { return sampleLines[String(who.dropFirst(2))]?.randomElement() }
        return guest[who]?[e.rawValue]?.randomElement()
    }

    /// 마네킹 샘플(『중첩된 현실』 보조 인물)이 그 권의 방에서 끼어든다: 단계 → 인물 이름 (MeSample.all)
    static let stageSamples: [Int: [String]] = [
        0: [String(localized: "미용사")], 1: [String(localized: "현실의 시오"), String(localized: "1권 선배")], 2: [String(localized: "니아"), String(localized: "묶은 머리 시오")], 4: [String(localized: "관리국의 수아")],
        6: [String(localized: "온유")], 7: [String(localized: "활동가")], 8: [String(localized: "담당관")], 9: [String(localized: "아내"), String(localized: "루")], 10: [String(localized: "아내"), String(localized: "루")], 11: [String(localized: "외전 세리")],
    ]
    private static let sampleLines: [String: [String]] = [
        String(localized: "미용사"): [String(localized: "선택하지 않은 벽돌은 모두 잘라낼게요."), String(localized: "가위 소리처럼, 사각. 잠시 멈춤. 사각."), String(localized: "거울 셋 중 어느 쪽 공이 진짜일까요?")],
        String(localized: "현실의 시오"): [String(localized: "복도 끝에 출구가 있어. 노란 줄 하나."), String(localized: "여기선 오래 버티는 게 이기는 거야."), String(localized: "맨발로도 걸었어. 공 하나쯤이야.")],
        String(localized: "1권 선배"): [String(localized: "그 방향, 정말 네가 고른 거야?"), String(localized: "출구는 늘 마지막에 보이지."), String(localized: "질문 하나. 왜 거기서 받았어?")],
        String(localized: "니아"): [String(localized: "지구에 보낼 녹음에 이것도 넣을게요."), String(localized: "여기선 공도 천천히 떨어져요."), String(localized: "제2돔에서 응원하고 있어요.")],
        String(localized: "묶은 머리 시오"): [String(localized: "고무줄 세 번. 다시 묶고 가자."), String(localized: "0.1초 늦어도 괜찮아.")],
        String(localized: "관리국의 수아"): [String(localized: "또 올게요. 약속은 아니에요."), String(localized: "입구는 둘이에요. 공은 어느 쪽으로 나올까요?"), String(localized: "기록해 둘게요. 하급 연구원이라서요.")],
        String(localized: "온유"): [String(localized: "창문은 손가락 몇 개만큼만요."), String(localized: "좁아도 바람은 들어와요."), String(localized: "천천히 해도 돼요. 여긴 그런 곳이에요.")],
        String(localized: "활동가"): [String(localized: "잘린 문장도 끝내 붙일 수 있어요."), String(localized: "줄표 하나는 남겨 둬요. 그건 깨지지 않아요."), String(localized: "멀리서도 보여요. 계속해요.")],
        String(localized: "담당관"): [String(localized: "솔직히 저도 당혹스럽습니다."), String(localized: "충전할 곳은 없습니다. 있는 걸로 가시죠."), String(localized: "보고서에는 잘했다고 쓰겠습니다.")],
        String(localized: "아내"): [String(localized: "밥 먹고 해요."), String(localized: "안 구운 그릇은 두 번 만져야 해요."), String(localized: "묻지 않을게요. 계속해요.")],
        String(localized: "루"): [String(localized: "꾸러미 배달 왔어요. 이건 응원."), String(localized: "배송 유닛 XX-2214, 지켜보는 중."), String(localized: "열여섯 살쯤인데, 저도 해 보고 싶어요.")],
        String(localized: "외전 세리"): [String(localized: "화면 속에서 보고 있어요."), String(localized: "프로필 사진처럼, 그대로 있어요."), String(localized: "거기까지만요. 아니, 조금 더요.")],
    ]

    private static var bag: [String: [Int]] = [:]

    /// who 의 그 상황 대사 한 줄. vars 로 {stage} {n} {score} {broken} {left} 를 채운다
    static func pick(_ e: Event, _ who: String, item: String? = nil, _ vars: [String: String] = [:]) -> String {
        if e == .item, let id = item {
            let name = Shelter.items.first { $0.id == id }?.name ?? id
            // 쉼터 대사(인물별 100줄)를 쓰고, 없으면 물건 이름과 효과
            if let l = Lines.pick("item.\(id).\(who == "me" ? "seri" : who)", salt: Int.random(in: 1...9)) { return l }
            return "\(name)! \(GamePlayer.item(id))."
        }
        let ev = e == .last ? "lose" : e.rawValue
        let list = pool[who]?[ev] ?? pool["seri"]?[ev] ?? [""]
        let key = "\(who).\(e.rawValue)"
        if bag[key]?.isEmpty ?? true { bag[key] = Array(list.indices).shuffled() }
        let i = bag[key]!.removeLast()
        var line = list[i % list.count]
        for (k, v) in vars { line = line.replacingOccurrences(of: "{\(k)}", with: v) }
        return line
    }
}

/// 이 폰 안에만 남는 기록
struct GameRecord: Codable, Equatable {
    var cleared: [String: Set<Int>] = [:]   // 난이도별 깬 단계
    var best: [String: Int] = [:]           // "난이도.단계" → 최고 점수
    var losses: [String: Int] = [:]         // "난이도.단계" → 연속으로 진 횟수 (깨면 0)
    var level: GameLevel = .normal
    var tilt = true
    var player = "seri"
    var plays: [String: [Play]]? = nil  // "난이도.단계" → 이 폰의 높은 점수 10판 (내 기록 순위)

    /// 내 기록 순위 한 줄: 점수·고른 인물·날짜. 서버로 보내지 않는다
    struct Play: Codable, Equatable, Hashable { let score: Int; let p: String; let at: Date }

    private static let key = "game.v1"
    /// 앱을 지웠다 깔아도 남도록 키체인에도 한 벌 둔다 (UserDefaults 는 앱과 같이 지워진다)
    private static let kc: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.game", kSecAttrAccount as String: key]
    static func load() -> GameRecord {
        if let d = UserDefaults.standard.data(forKey: key), let r = try? JSONDecoder().decode(GameRecord.self, from: d) { return r }
        var q = kc; q[kSecReturnData as String] = true
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data,
              let r = try? JSONDecoder().decode(GameRecord.self, from: d) else { return GameRecord() }
        UserDefaults.standard.set(d, forKey: key)
        return r
    }
    func save() {
        guard let d = try? JSONEncoder().encode(self) else { return }
        UserDefaults.standard.set(d, forKey: Self.key)
        let set: [String: Any] = [kSecValueData as String: d, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        if SecItemUpdate(Self.kc as CFDictionary, set as CFDictionary) == errSecItemNotFound {
            SecItemAdd(Self.kc.merging(set) { $1 } as CFDictionary, nil)
        }
    }

    func open(_ n: Int, _ lv: GameLevel) -> Bool { n == 0 || cleared[lv.rawValue, default: []].contains(n - 1) }
    func bestOf(_ n: Int, _ lv: GameLevel) -> Int? { best["\(lv.rawValue).\(n)"] }
    func total(_ lv: GameLevel) -> Int { GameStage.all.reduce(0) { $0 + (bestOf($1.n, lv) ?? 0) } }
    func lossCount(_ n: Int, _ lv: GameLevel) -> Int { losses["\(lv.rawValue).\(n)"] ?? 0 }
    func top(_ n: Int, _ lv: GameLevel) -> [Play] { plays?["\(lv.rawValue).\(n)"] ?? [] }

    /// 한 판 끝: 새 최고 점수면 true
    @discardableResult
    mutating func finish(_ n: Int, _ lv: GameLevel, score: Int, won: Bool, player: String = "seri") -> Bool {
        let k = "\(lv.rawValue).\(n)"
        if score > 0 {
            var list = plays?[k] ?? []
            list.append(Play(score: score, p: player, at: Date()))
            list.sort { $0.score > $1.score }
            plays = (plays ?? [:]).merging([k: Array(list.prefix(10))]) { $1 }
        }
        if won { cleared[lv.rawValue, default: []].insert(n); losses[k] = 0 } else { losses[k, default: 0] += 1 }
        let isBest = score > (best[k] ?? 0)
        if isBest { best[k] = score }
        return isBest
    }
}

@Observable
final class BrickGame: Identifiable {
    enum Phase { case ready, playing, paused, cleared, over }
    struct Ball { var x: Double, y: Double, vx: Double, vy: Double; var stuck = true; var off = 0.0; var until = 0.0; var spawned = false }
    struct Drop { let id: String; var x: Double, y: Double }
    /// 고전 벽돌깨기의 특수 블럭: 폭탄(둘레 터짐), 가로·세로 한 줄, 공 셋, 선물(물건 또는 점수)
    enum Special: CaseIterable { case bomb, row, col, multi, gift }
    struct Blast { let x: Double, y: Double; let kind: Special; let t0: Double }
    /// 숨은 테트리스 블럭: 두 번 맞는 벽돌인 척 숨어 있다가 한 칸이 깨지면 드러나고, 칸 수(4)만큼 더 맞아야 없어진다
    struct Big { let cells: [Int]; let shape: Int; var hp: Int; var revealed = false }
    /// 테트리스 일곱 조각 (I O T S Z J L), 칸 (가로, 세로)
    static let tetrominoes: [[(Int, Int)]] = [
        [(0, 0), (1, 0), (2, 0), (3, 0)], [(0, 0), (1, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (2, 0), (1, 1)],
        [(1, 0), (2, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (1, 1), (2, 1)], [(0, 0), (0, 1), (1, 1), (2, 1)],
        [(2, 0), (0, 1), (1, 1), (2, 1)],
    ]

    static let W = 40.0, H = 46.0, PY = 5.0, PH = 0.8, top = 21.0, r = 0.45

    // 화면은 TimelineView 가 매 프레임 tick 을 부르며 다시 그린다. 지켜보는 값은 phase 뿐
    var phase: Phase = .ready

    let id = UUID()
    let stage: GameStage
    let level: GameLevel
    let player: String       // 그림에 쓰는 인물 (me 포함)
    let skill: String        // 스킬을 정하는 인물 (내 캐릭터는 평정 지수 유형)
    let items: [String]
    let assist: Bool
    @ObservationIgnored var tilt: Bool

    @ObservationIgnored var hp = [Int8](repeating: 0, count: 1000)     // 0 빈칸, -1 깨지지 않는 벽, 1·2 남은 횟수
    @ObservationIgnored var kind = [UInt8](repeating: 0, count: 1000)  // 1 바닥에도 있던 칸, 2 회복한 칸, 3 오늘 늘어난 칸, 4 연습 칸, 5 단계가 더 쌓은 칸
    @ObservationIgnored var special = [Special?](repeating: nil, count: 1000)
    @ObservationIgnored var revealed = [Bool](repeating: false, count: 1000)   // 특수 블럭은 한 번 맞아야 보인다
    @ObservationIgnored var group = [Int](repeating: -1, count: 1000)          // 큰 숨은 블럭 번호
    @ObservationIgnored var bigs: [Big] = []
    @ObservationIgnored private var touched: Set<Int> = []                     // 한 번 맞을 때 큰 블럭은 한 번만 깎는다
    @ObservationIgnored var blasts: [Blast] = []          // 터지는 그림 (잠깐)
    @ObservationIgnored var stacked = 0                   // 이 단계에서 더 쌓은 벽돌
    @ObservationIgnored var combo = 0                     // 판에 다시 닿기 전까지 깬 벽돌
    @ObservationIgnored private var comboSaid = 0
    @ObservationIgnored var exitCell = -1
    @ObservationIgnored var balls: [Ball] = []
    @ObservationIgnored var drops: [Drop] = []
    @ObservationIgnored var effects: [String: Double] = [:]   // 이름 → 끝나는 시각
    @ObservationIgnored var shield = false
    @ObservationIgnored var net = 0                       // 시오 안전망: 판 뒤 노란 줄이 놓친 공을 받아 주는 남은 횟수
    static let netY = 2.4
    /// 이어하기: 앞 단계를 깨고 바로 넘어온 횟수. 단계마다 점수 +10%
    let chain: Int
    var scoreMul: Double { 1 + 0.1 * Double(chain) }
    /// 연속 3단계를 깰 때마다 (3·6·9…번째) 생명 +1
    var lifeBonus: Bool { chain > 0 && chain % 3 == 0 }
    @ObservationIgnored var rewindUsed = false
    @ObservationIgnored var paddleX = BrickGame.W / 2
    @ObservationIgnored var t = 0.0
    @ObservationIgnored var score = 0
    @ObservationIgnored var lives: Int
    @ObservationIgnored var broken = 0
    @ObservationIgnored var total = 0
    @ObservationIgnored var toast: (String, Double)? = nil
    @ObservationIgnored var speech = ""                  // 위쪽 인물 말풍선
    @ObservationIgnored var mood: GameLines.Event = .start   // 지금 말하는 상황 (동작 그림을 고른다)
    @ObservationIgnored var moodAt = 0.0
    @ObservationIgnored var speechUntil = 0.0
    @ObservationIgnored private var halfSaid = false
    @ObservationIgnored var friends: [String] = []       // 끼어들 수 있는 다른 인물 (열린 인물)
    @ObservationIgnored var guest = ""                   // 오른쪽에 나온 인물
    @ObservationIgnored var guestLine = ""
    @ObservationIgnored var guestUntil = 0.0
    @ObservationIgnored var newBest = false
    @ObservationIgnored var picked: [String: Int] = [:]   // 이번 판에 먹은 물건 → 횟수

    @ObservationIgnored private var lastTick: Date?
    @ObservationIgnored private var stopped = false
    private let motion = CMMotionManager()
    private let tap = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private var lastTap = 0.0
    @ObservationIgnored private var axisSign: (Bool, Double) = (true, 1)   // (가로 화면이면 y축, 부호)
    @ObservationIgnored private var axisCheck = -1.0

    var target: Int { max(1, Int((Double(total) * level.clear).rounded(.up))) }

    init(stage: GameStage, level: GameLevel, player: String, skill: String, items: [String], tilt: Bool, assist: Bool,
         filled: Int, floor: Int, yesterday: Int, chain: Int = 0, lives: Int? = nil) {
        self.stage = stage; self.level = level; self.player = player; self.skill = skill
        self.items = stage.items ? items : []
        self.tilt = tilt; self.assist = assist
        self.chain = chain
        // 어느 단계에서 시작해도 난이도 기본 생명, 이어하면 남은 생명 그대로
        self.lives = lives ?? level.lives + stage.extraLives
        if lives != nil && chain > 0 && chain % 3 == 0 { self.lives += 1 }
        self.net = skill == "sio" ? 1 : 0
        // 벽돌 수는 난이도·단계로 정한다. 내 채운 칸이 먼저 (아래부터), 모자라면 그 위에 더 쌓는다
        let n = stage.bricks(level), f = max(0, min(n, filled))
        for i in 0..<n {
            let roll = Double.random(in: 0..<1)   // 대부분 한 번, 일부만 두 번 (10권 그릇은 세 번)
            hp[i] = roll < stage.hard2 ? 3 : roll < stage.hard2 + GameStage.tough ? 2 : 1
            kind[i] = i >= f ? 5 : i < floor ? 1 : (yesterday < f && i >= yesterday) ? 3 : 2
        }
        stacked = n - f
        if stage.dash { for c in 14...25 { hp[12 * 40 + c] = -1 } }
        // 숨기는 곳: 맨 위 3번째 줄부터 바닥까지
        let topRow = ((0..<1000).last { hp[$0] > 0 } ?? 0) / 40
        let hidden = { (i: Int) in self.hp[i] > 0 && i / 40 <= topRow - 2 && self.group[i] < 0 && self.special[i] == nil }
        // 숨은 테트리스 블럭 (안에는 특수 블럭이 없다)
        var tries = 0
        while bigs.count < stage.tetrisCount(level) && tries < 3000 {
            tries += 1
            let shape = Int.random(in: 0..<Self.tetrominoes.count)
            var pts = Self.tetrominoes[shape]
            for _ in 0..<Int.random(in: 0..<4) { pts = pts.map { ($0.1, -$0.0) } }   // 돌리기
            let mx = pts.map { $0.0 }.min()!, my = pts.map { $0.1 }.min()!
            pts = pts.map { ($0.0 - mx, $0.1 - my) }
            let w = pts.map { $0.0 }.max()! + 1, h = pts.map { $0.1 }.max()! + 1
            guard topRow - 1 - h >= 0 else { continue }
            let c0 = Int.random(in: 0...(40 - w)), r0 = Int.random(in: 0...(topRow - 1 - h))
            let cells = pts.map { (r0 + $0.1) * 40 + c0 + $0.0 }
            guard cells.allSatisfy(hidden) else { continue }
            for c in cells { group[c] = bigs.count; hp[c] = 2 }
            bigs.append(Big(cells: cells, shape: shape, hp: cells.count))
        }
        // 특수 블럭 10개를 숨긴다 (폭탄이 가장 많다)
        let pool: [Special] = [.bomb, .bomb, .bomb, .row, .col, .multi, .gift]
        for i in (0..<1000).filter(hidden).shuffled().prefix(GameStage.specials) { special[i] = pool.randomElement() }
        if stage.flicker { exitCell = (0..<1000).last { hp[$0] > 0 && group[$0] < 0 } ?? -1 }
        total = hp.filter { $0 > 0 }.count
        balls = [Ball(x: Self.W / 2, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0)]
        talk(.start)
    }

    deinit { motion.stopDeviceMotionUpdates() }

    // MARK: 조작

    func begin() {
        if tilt, motion.isDeviceMotionAvailable {
            motion.deviceMotionUpdateInterval = 1.0 / 60
            motion.startDeviceMotionUpdates()
        }
        tap.prepare()
        GameSound.shared.start()
    }
    func end() { stopped = true; motion.stopDeviceMotionUpdates() }

    /// 화면이 그릴 때마다 (TimelineView). 여기서는 지켜보는 값(phase)을 바로 바꾸지 않는다
    func tick(_ now: Date) {
        defer { lastTick = now }
        guard let last = lastTick, !stopped else { return }
        let dt = min(1.0 / 20, max(0, now.timeIntervalSince(last)))
        if dt > 0 { step(dt) }
    }
    /// 판이 끝남: 그리는 중이라 다음 차례에 phase 를 바꾼다
    private func finish(_ p: Phase) {
        guard !stopped else { return }
        end()
        DispatchQueue.main.async { self.phase = p }
    }

    /// 시작 카드의 "눌러서 시작"
    func startPlay() {
        guard phase == .ready else { return }
        phase = .playing
        GameSound.shared.play(lifeBonus ? .item : .start)
        if lifeBonus { say(String(localized: "생명 +1 · 연속 \(String(describing: chain))단계")) }
        launch()
    }
    /// 화면을 누르면 붙은 공을 보낸다 (시작은 시작 카드 버튼으로만)
    func launch() {
        guard phase == .playing else { return }
        let sp = speed
        for i in balls.indices where balls[i].stuck { send(&balls[i], sp) }
    }
    func pause() { if phase == .playing || phase == .ready { phase = .paused } }
    func resume() { if phase == .paused { phase = .playing } }

    /// 손가락으로 끌 때 (판 좌표)
    func drag(to x: Double) { if !tilt || !motion.isDeviceMotionActive { paddleX = clampPaddle(x) } }

    // MARK: 값

    var paddleW: Double {
        var w = level.paddle * stage.paddleMul
        if skill == "seri" { w *= 1.3 }
        if skill == "sua" { w *= 1 + min(0.5, Double(broken / 10) * 0.02) }
        if on("wide") { w *= 1.4 }
        return min(w, Self.W * 0.6)
    }
    var speed: Double {
        var s = level.speed * stage.speedMul
        if skill == "ir" { s *= 1.2 }
        if skill != "seri" { s *= min(1.2, 1 + t / 600) }   // 오래 버티면 조금씩 빨라진다 (세리는 그대로)
        if assist { s *= 0.85 }
        if on("slow") { s *= 0.7 }
        if on("fast") { s *= 1.15 }
        return s
    }
    func on(_ e: String) -> Bool { (effects[e] ?? 0) > t }
    var activeEffects: [String] { effects.filter { $0.value > t }.map(\.key).sorted() }
    /// 1권 형광등: 7초마다 1초쯤 어두워진다 (벽돌은 흐리게만, 완전히 사라지지 않음)
    var dark: Bool { stage.flicker && t.truncatingRemainder(dividingBy: 7) > 6 }

    func sway(_ row: Int) -> Double { stage.sway ? sin(t * 1.2 + Double(row) * 0.5) * 0.8 : 0 }

    /// 판 좌표의 칸 번호 (1000칸 번호: 아래 줄부터 0)
    func cell(_ x: Double, _ y: Double) -> Int? {
        guard y >= Self.top, y < Self.H else { return nil }
        let row = 24 - Int(y - Self.top)
        let xx = x - sway(row)
        guard xx >= 0, xx < Self.W else { return nil }
        return row * 40 + Int(xx)
    }
    /// 칸 번호 → 판에서의 네모
    func rect(_ i: Int) -> CGRect {
        let row = i / 40
        return CGRect(x: Double(i % 40) + sway(row), y: Self.top + Double(24 - row), width: 1, height: 1)
    }

    private func clampPaddle(_ x: Double) -> Double {
        let h = paddleW / 2
        return min(max(x, h), Self.W - h)
    }

    // MARK: 한 걸음

    private func step(_ dt: Double) {
        guard phase == .playing || phase == .ready else { return }
        if phase == .playing { t += dt }
        movePaddle(dt)
        if phase == .ready {
            for i in balls.indices where balls[i].stuck { balls[i].x = paddleX + balls[i].off }
            return
        }
        if let ts = toast, ts.1 < t { toast = nil }
        let sp = speed, frozen = on("clock")
        var alive: [Ball] = []
        for var b in balls {
            if b.stuck {
                b.x = min(max(paddleX + b.off, Self.r), Self.W - Self.r)
                b.y = Self.PY + Self.PH + Self.r
                if t >= b.until { send(&b, sp) }
                alive.append(b); continue
            }
            if frozen { alive.append(b); continue }
            if stage.gravity != 0 { b.vy += stage.gravity * dt }
            if stage.well {
                let dx = Self.W / 2 - b.x, dy = 13 - b.y, d2 = max(dx * dx + dy * dy, 6), d = d2.squareRoot()
                let a = 180 / d2
                b.vx += dx / d * a * dt; b.vy += dy / d * a * dt
            }
            normalize(&b, sp)
            let n = max(1, Int((sp * dt / 0.3).rounded(.up))), sdt = dt / Double(n)
            var lost = false
            for _ in 0..<n { if !move(&b, sdt, sp) { lost = true; break } }
            if !lost { alive.append(b) }
        }
        // 이번 걸음에 특수 블럭이 만든 공 (balls 에 바로 붙은 것)
        balls = alive + balls.filter { $0.spawned }.map { var b = $0; b.spawned = false; return b }
        blasts.removeAll { t - $0.t0 > 0.45 }
        moveDrops(dt)
        if broken >= target { win(); return }
        if !halfSaid && broken * 2 >= target { halfSaid = true; talk(.half) }
        if balls.isEmpty { loseBall() }
    }

    private func movePaddle(_ dt: Double) {
        guard tilt, let g = motion.deviceMotion?.gravity else { paddleX = clampPaddle(paddleX); return }
        if t - axisCheck > 0.5 || axisCheck < 0 { axisCheck = t; axisSign = Self.axis() }
        let a = axisSign.0 ? g.y * axisSign.1 : g.x
        // 좌우로 약 24도 기울이면 끝까지
        let goal = Self.W / 2 + max(-1, min(1, a / 0.4)) * Self.W / 2
        paddleX = clampPaddle(paddleX + (goal - paddleX) * min(1, dt * 14))
    }

    /// 화면 오른쪽이 기기의 어느 축인지 (세로: +x, 가로: ±y)
    private static func axis() -> (Bool, Double) {
        let o = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.interfaceOrientation }.first ?? .portrait
        switch o {
        case .landscapeRight: return (true, -1)
        case .landscapeLeft: return (true, 1)
        default: return (false, 1)
        }
    }

    private func normalize(_ b: inout Ball, _ sp: Double) {
        let v = (b.vx * b.vx + b.vy * b.vy).squareRoot()
        if v < 0.001 { b.vx = 0; b.vy = sp; return }
        b.vx *= sp / v; b.vy *= sp / v
        // 옆으로만 오가지 않게
        let minY = sp * 0.28
        if abs(b.vy) < minY {
            b.vy = b.vy < 0 ? -minY : minY
            b.vx = (b.vx < 0 ? -1 : 1) * (sp * sp - minY * minY).squareRoot()
        }
    }

    private func send(_ b: inout Ball, _ sp: Double) {
        let off = max(-1, min(1, b.off / (paddleW / 2)))
        let a = off * .pi / 3.5 + Double.random(in: -0.08...0.08)
        b.stuck = false; b.vx = sp * sin(a); b.vy = sp * cos(a)
    }

    /// false = 위로 놓침
    private func move(_ b: inout Ball, _ dt: Double, _ sp: Double) -> Bool {
        let pierce = on("pierce")
        // 가로
        var nx = b.x + b.vx * dt
        if stage.portal {
            if nx < 0 { nx += Self.W } else if nx >= Self.W { nx -= Self.W }
        } else if nx < Self.r { nx = Self.r; b.vx = abs(b.vx) } else if nx > Self.W - Self.r { nx = Self.W - Self.r; b.vx = -abs(b.vx) }
        if let i = cell(nx + (b.vx < 0 ? -Self.r : Self.r), b.y), hp[i] != 0 {
            let wall = hp[i] < 0
            hit(i)
            if wall || !pierce { b.vx = -b.vx; nx = b.x }
        }
        b.x = nx
        // 세로
        var ny = b.y + b.vy * dt
        if ny > Self.H - Self.r { ny = Self.H - Self.r; b.vy = -abs(b.vy) }
        if let i = cell(b.x, ny + (b.vy < 0 ? -Self.r : Self.r)), hp[i] != 0 {
            let wall = hp[i] < 0
            hit(i)
            if wall || !pierce { b.vy = -b.vy; ny = b.y }
        }
        // 판: 아래에서 올라온 공이 판 아랫면에 닿으면
        let face = Self.PY + Self.PH
        if b.vy < 0, b.y - Self.r >= face - 0.05, ny - Self.r < face, abs(b.x - paddleX) <= paddleW / 2 + Self.r {
            ny = face + Self.r
            paddleHit(&b, sp)
        }
        // 시오 안전망: 판을 지나 위로 간 공을 한 번 받아 준다
        if net > 0, b.vy < 0, b.y >= Self.netY, ny < Self.netY {
            net -= 1; ny = Self.netY; b.vy = abs(b.vy)
            GameSound.shared.play(.paddle); say(String(localized: "안전망이 받았어요"))
        }
        b.y = ny
        if b.y < Self.r && shield { shield = false; b.y = Self.r; b.vy = abs(b.vy); say(String(localized: "보호막이 막았어요")) }
        return b.y > -Self.r
    }

    private func paddleHit(_ b: inout Ball, _ sp: Double) {
        buzz(); GameSound.shared.play(.paddle)
        combo = 0; comboSaid = 0
        var a = max(-1, min(1, (b.x - paddleX) / (paddleW / 2))) * .pi / 3
        if on("aim") {
            // 남은 벽돌이 가장 많은 세로줄 쪽으로
            var cnt = [Int](repeating: 0, count: 40)
            for i in 0..<1000 where hp[i] > 0 { cnt[i % 40] += 1 }
            if let c = cnt.indices.max(by: { cnt[$0] < cnt[$1] }), cnt[c] > 0 {
                a = max(-.pi / 3, min(.pi / 3, atan2(Double(c) + 0.5 - b.x, Self.top - Self.PY)))
            }
        }
        b.vx = sp * sin(a); b.vy = sp * cos(a)
    }

    private func hit(_ i: Int) {
        guard hp[i] > 0 else { return }
        buzz()
        let row = i / 40, col = i % 40, before = broken
        var queue: [Int] = []
        touched = []
        // 맞은 곳 둘레를 한 번씩 깎는다 (두 번째에 깨진다)
        for (dx, dy) in level.burst {
            let c = col + dx, r = row + dy
            guard c >= 0, c < 40, r >= 0, r < 25 else { continue }
            crack(r * 40 + c, &queue)
        }
        GameSound.shared.play(broken > before ? .hit : .tough)
        // 특수 블럭은 이어서 터진다 (폭탄이 폭탄을 터뜨리는 연쇄)
        var k = 0
        while k < queue.count { let j = queue[k]; trigger(j, &queue); k += 1 }
        if kind[i] == 3 { effects["fast"] = t + 5 }
        if broken > before, !items.isEmpty, Double.random(in: 0..<1) < 0.14, let it = items.randomElement() {
            drops.append(Drop(id: it, x: Double(col) + 0.5, y: rect(i).midY))
        }
        if combo / 15 > comboSaid { comboSaid = combo / 15; say(String(localized: "콤보 \(String(describing: combo))!")) }
    }

    /// 한 칸 깎기. 깨지면 점수, 특수 블럭이면 터질 차례에 넣는다
    private func crack(_ j: Int, _ queue: inout [Int], all: Bool = false) {
        guard hp[j] > 0 else { return }
        if group[j] >= 0 { crackBig(group[j], j, &queue); return }
        hp[j] = all ? 0 : hp[j] - 1
        revealed[j] = true
        guard hp[j] == 0 else { return }
        broken += 1; combo += 1
        var mul = skill == "ir" ? 1.5 : 1.0
        if on("double") { mul *= 2 }
        if combo > 10 { mul *= 1.5 }    // 한 번에 많이 깨면 점수가 더
        mul *= scoreMul
        let p: Double = switch kind[j] { case 2: 2; case 3: 3; default: 1 }
        score += Int((p * mul).rounded())
        if j == exitCell { score += 100; say(String(localized: "출구 칸 +100")) }
        if special[j] != nil { queue.append(j) }
    }

    /// 테트리스 블럭: 숨어 있을 땐 칸마다 보통 두 번 맞는 벽돌과 똑같다. 그중 한 칸이 깨지면 조각 전체가 드러나고,
    /// 그 뒤로 한 번 맞을 때(폭발 한 번도) 1씩, 칸 수만큼 더 맞으면 한꺼번에 없어진다
    private func crackBig(_ g: Int, _ j: Int, _ queue: inout [Int]) {
        guard bigs[g].hp > 0 else { return }
        if !bigs[g].revealed {
            revealed[j] = true
            if hp[j] > 1 { hp[j] -= 1; return }
            bigs[g].revealed = true
            for c in bigs[g].cells { hp[c] = 1 }
            touched.insert(g)
            say(String(localized: "테트리스 블럭! \(String(describing: bigs[g].hp))번 더"))
            GameSound.shared.play(.tetris(bigs[g].shape)); return
        }
        guard !touched.contains(g) else { return }
        touched.insert(g)
        bigs[g].hp -= 1
        guard bigs[g].hp == 0 else { GameSound.shared.play(.tough); return }
        let b = bigs[g], bonus = Int((Double(b.cells.count * 10) * scoreMul).rounded())
        for c in b.cells where hp[c] > 0 { hp[c] = 0; broken += 1; combo += 1; score += Int((2 * scoreMul).rounded()) }
        score += bonus
        // 모양을 따라 바로 옆 한 칸씩 같이 깨진다 (위·아래·왼쪽·오른쪽)
        for c in b.cells {
            let r = c / 40, col = c % 40
            for (dx, dy) in [(1, 0), (-1, 0), (0, 1), (0, -1)] {
                let cc = col + dx, rr = r + dy
                guard cc >= 0, cc < 40, rr >= 0, rr < 25 else { continue }
                let k = rr * 40 + cc
                if group[k] != g { crack(k, &queue, all: true) }
            }
        }
        let xs = b.cells.map { rect($0).midX }, ys = b.cells.map { rect($0).midY }
        blasts.append(Blast(x: xs.reduce(0, +) / Double(xs.count), y: ys.reduce(0, +) / Double(ys.count), kind: .bomb, t0: t))
        GameSound.shared.play(.tetris(b.shape))
        say(String(localized: "테트리스 블럭 +\(String(describing: bonus))"))
    }

    private func trigger(_ j: Int, _ queue: inout [Int]) {
        guard let sp = special[j] else { return }
        special[j] = nil
        touched = []
        let row = j / 40, col = j % 40, c = rect(j)
        blasts.append(Blast(x: c.midX, y: c.midY, kind: sp, t0: t))
        switch sp {
        case .bomb:
            GameSound.shared.play(.bomb)
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            for dy in -2...2 { for dx in -2...2 where abs(dx) + abs(dy) <= 3 {
                let cc = col + dx, r = row + dy
                if cc >= 0, cc < 40, r >= 0, r < 25 { crack(r * 40 + cc, &queue, all: true) }
            } }
        case .row:
            GameSound.shared.play(.row)
            for cc in 0..<40 { crack(row * 40 + cc, &queue, all: true) }
        case .col:
            GameSound.shared.play(.col)
            for r in 0..<25 { crack(r * 40 + col, &queue, all: true) }
        case .multi:
            GameSound.shared.play(.multi)
            let v = speed
            for a in [-0.6, 0.0, 0.6] where balls.count < 12 {
                balls.append(Ball(x: c.midX, y: c.minY - Self.r - 0.05, vx: v * sin(a), vy: -v * cos(a), stuck: false, spawned: true))
            }
            say(String(localized: "공 셋!"))
        case .gift:
            GameSound.shared.play(.gift)
            if let it = items.randomElement() { drops.append(Drop(id: it, x: c.midX, y: c.midY)) }
            else { let p = Int((50 * scoreMul).rounded()); score += p; say(String(localized: "선물 +\(String(describing: p))")) }
        }
    }

    private func moveDrops(_ dt: Double) {
        var keep: [Drop] = []
        for var d in drops {
            d.y -= 9 * dt
            if d.y <= Self.PY + Self.PH + 0.8, d.y >= Self.PY - 1, abs(d.x - paddleX) <= paddleW / 2 + 0.8 { use(d.id); continue }
            if d.y > -1 { keep.append(d) }
        }
        drops = keep
    }

    private func use(_ id: String) {
        let name = Shelter.items.first { $0.id == id }?.name ?? id
        say("\(name) · \(GamePlayer.itemShort(id))")
        picked[id, default: 0] += 1
        GameSound.shared.play(.item)
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        talk(.item, item: id)
        switch id {
        case "barley_tea": effects["slow"] = t + 10
        case "porch_light": effects["light"] = t + 10
        case "wall_clock": effects["clock"] = t + 3
        case "hair_tie": effects["wide"] = t + 15
        case "table_chair":
            balls.append(Ball(x: paddleX, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0, stuck: true, off: 0, until: t + 0.3))
        case "asym_bowl": effects["aim"] = t + 10
        case "unfired_bowl": shield = true
        case "jujube_seed": effects["double"] = t + 10
        case "elder_bead": effects["pierce"] = t + 5
        case "rice_seeds":
            var more: [Ball] = []
            for b in balls where !b.stuck && balls.count + more.count < 12 {
                for a in [-0.35, 0.35] {
                    var c = b
                    c.vx = b.vx * cos(a) - b.vy * sin(a); c.vy = b.vx * sin(a) + b.vy * cos(a)
                    more.append(c)
                }
            }
            balls += more
        default: break
        }
    }

    private func loseBall() {
        combo = 0; comboSaid = 0   // 공을 잃으면 콤보도 끊긴다
        if skill == "seonbae" && !rewindUsed {
            rewindUsed = true
            balls = [Ball(x: paddleX, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0, stuck: true, off: 0, until: t + 1.5)]
            say(String(localized: "되감기 · 한 번 돌려받았어요")); talk(.rewind); return
        }
        lives -= 1
        GameSound.shared.play(.lose)
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
        if lives <= 0 { talk(.over); GameSound.shared.play(.over); finish(.over); return }
        talk(lives == 1 ? .last : .lose)
        drops = []
        balls = [Ball(x: paddleX, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0, stuck: true, off: 0, until: t + 2.5)]
    }

    private func win() {
        score += Int((Double(lives * 50) * scoreMul).rounded())
        GameSound.shared.play(.clear)
        talk(.clear)
        finish(.cleared)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func say(_ s: String) { toast = (s, t + 2.2) }

    /// 인물 말풍선 (시작 전에는 계속, 그 밖에는 3초)
    func talk(_ e: GameLines.Event, item: String? = nil) {
        let vars = ["stage": stage.bg.volume + " " + stage.bg.name, "n": "\(max(0, lives - 1))", "score": score.formatted(),
                    "broken": "\(broken)", "left": "\(total)"]
        speech = GameLines.pick(e, player, item: item, vars)
        speechUntil = t + 3.2
        mood = e; moodAt = t
        // 특정 상황에는 다른 인물이 오른쪽에 나와 한마디 (물건은 가끔)
        let chance: Double = switch e { case .last, .clear, .over: 1; case .half: 0.7; case .item: 0.3; default: 0 }
        // 그 권의 보조 인물이 있으면 절반쯤은 그쪽이 나온다
        let samples = (GameLines.stageSamples[stage.n] ?? []).map { "s:" + $0 }
        let others = friends.filter { $0 != player && $0 != "me" }
        let pickFrom = !samples.isEmpty && (others.isEmpty || Bool.random()) ? samples : others
        if Double.random(in: 0..<1) < chance, let who = pickFrom.randomElement(),
           let line = GameLines.guestLine(e, who) {
            guest = who; guestLine = line; guestUntil = t + 3.6
        }
    }
    var guestShown: Bool { !guest.isEmpty && (phase == .cleared || phase == .over || t < guestUntil) }
    var speaking: Bool { phase == .ready || phase == .cleared || phase == .over || t < speechUntil }

    private func buzz() {
        guard t - lastTap > 0.06 else { return }
        lastTap = t; tap.impactOccurred(intensity: 0.6)
    }
}

/// 효과음: 짧은 소리를 코드로 만들어 둔다 (파일 없음). 무음 스위치를 따르고 다른 앱 소리와 섞인다
final class GameSound {
    enum Kind: Hashable { case hit, tough, paddle, item, lose, clear, over, start, bomb, row, col, multi, gift, tetris(Int) }
    static let shared = GameSound()
    private let engine = AVAudioEngine()
    private var players: [AVAudioPlayerNode] = []
    private var next = 0
    private var buffers: [Kind: AVAudioPCMBuffer] = [:]
    private var lastHit = 0.0
    private var ready = false

    func start() {
        guard !ready else { if !engine.isRunning { try? engine.start() }; return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        guard let fmt = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 1) else { return }
        for _ in 0..<6 {
            let p = AVAudioPlayerNode()
            engine.attach(p); engine.connect(p, to: engine.mainMixerNode, format: fmt)
            players.append(p)
        }
        engine.mainMixerNode.outputVolume = 0.5
        // (주파수, 길이) 음표들
        let notes: [Kind: [(Double, Double)]] = [
            .hit: [(880, 0.035)], .tough: [(330, 0.05)], .paddle: [(523, 0.05)],
            .item: [(660, 0.06), (880, 0.06), (1320, 0.09)],
            .lose: [(440, 0.1), (330, 0.1), (220, 0.18)],
            .clear: [(523, 0.1), (659, 0.1), (784, 0.1), (1047, 0.25)],
            .over: [(392, 0.14), (330, 0.14), (262, 0.14), (196, 0.35)],
            .start: [(392, 0.07), (523, 0.07), (659, 0.07), (784, 0.16)],
            // 특수 블럭마다 다른 소리: 폭탄 낮게 쿵, 주황 줄 가로는 위로·세로는 아래로 쓸기, 파랑 공 셋 통통통, 노랑 선물 반짝
            .bomb: [(140, 0.06), (95, 0.08), (70, 0.14)],
            .row: [(1047, 0.04), (1397, 0.04), (1760, 0.07)],
            .col: [(1760, 0.04), (1397, 0.04), (1047, 0.07)],
            .multi: [(784, 0.05), (0, 0.02), (784, 0.05), (0, 0.02), (1047, 0.08)],
            .gift: [(1568, 0.05), (2093, 0.05), (1568, 0.05), (2637, 0.1)],
            // 테트리스 일곱 조각 (I O T S Z J L) 마다 다른 가락
            .tetris(0): [(523, 0.05), (659, 0.05), (784, 0.05), (1047, 0.12)],
            .tetris(1): [(587, 0.08), (587, 0.08), (880, 0.14)],
            .tetris(2): [(659, 0.06), (880, 0.06), (659, 0.12)],
            .tetris(3): [(440, 0.06), (554, 0.06), (659, 0.06), (554, 0.1)],
            .tetris(4): [(698, 0.06), (587, 0.06), (466, 0.06), (587, 0.1)],
            .tetris(5): [(330, 0.06), (392, 0.06), (494, 0.06), (659, 0.12)],
            .tetris(6): [(988, 0.06), (784, 0.06), (659, 0.06), (523, 0.12)],
        ]
        for (k, ns) in notes { buffers[k] = tone(ns, fmt) }
        do { try engine.start(); ready = true } catch { ready = false }
    }

    func play(_ k: Kind) {
        guard ready, let b = buffers[k] else { return }
        if k == .hit {   // 벽돌 소리는 너무 겹치지 않게
            let now = ProcessInfo.processInfo.systemUptime
            guard now - lastHit > 0.045 else { return }
            lastHit = now
        }
        if !engine.isRunning { try? engine.start() }
        let p = players[next]; next = (next + 1) % players.count
        p.stop(); p.scheduleBuffer(b); p.play()
    }

    private func tone(_ notes: [(Double, Double)], _ fmt: AVAudioFormat) -> AVAudioPCMBuffer? {
        let sr = fmt.sampleRate, total = notes.reduce(0) { $0 + $1.1 }
        guard let buf = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: AVAudioFrameCount(total * sr) + 1), let d = buf.floatChannelData?[0] else { return nil }
        var i = 0
        for (f, len) in notes {
            let n = Int(len * sr)
            for j in 0..<n {
                let t = Double(j) / sr, env = min(1, Double(j) / 200) * (1 - Double(j) / Double(n))
                // 네모파에 가까운 8비트 소리 (홀수 배음 셋)
                let w = f == 0 ? 0 : sin(2 * .pi * f * t) + sin(6 * .pi * f * t) / 3 + sin(10 * .pi * f * t) / 5
                d[i + j] = Float(w * env * 0.35)
            }
            i += n
        }
        buf.frameLength = AVAudioFrameCount(i)
        return buf
    }
}
