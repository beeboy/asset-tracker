import SwiftUI
import UIKit
import CoreMotion
import QuartzCore

// 판 깨기: 1000칸으로 하는 거꾸로 벽돌깨기. 채운 칸이 벽돌이고, 판(패들)은 위에, 공은 아래 벽돌로 떨어진다.
// 실제 칸은 바뀌지 않는다. 단계 = 『중첩된 현실』 쉼터 12곳(권마다 규칙 하나), 선수 = 기본 인물(+내 캐릭터, 스킬 하나),
// 아이템 = 쉼터에 돌아온 물건(모은 것만 떨어진다). 기록은 이 폰 안에만 저장한다 (UserDefaults game.v1, 개인 정보 없음).
// 판의 좌표: 가로 40칸 × 세로 46줄. 위 0줄 밖으로 공이 나가면 놓친 것, 21~45줄이 1000칸 (아래부터 채움, CellGrid 와 같은 배치).

enum GameLevel: String, CaseIterable, Codable, Identifiable {
    case easy, normal, hard
    var id: String { rawValue }
    var title: String { switch self { case .easy: "하수"; case .normal: "중수"; case .hard: "고수" } }
    var speed: Double { switch self { case .easy: 21; case .normal: 27; case .hard: 33 } }      // 칸/초
    var paddle: Double { switch self { case .easy: 9; case .normal: 7; case .hard: 5.5 } }     // 판 길이 (칸)
    var lives: Int { switch self { case .easy: 5; case .normal: 3; case .hard: 2 } }
    /// 단계를 깨는 기준: 깰 수 있는 벽돌 중 이만큼
    var clear: Double { switch self { case .easy: 0.6; case .normal: 0.75; case .hard: 0.9 } }
    /// 한 번 맞히면 같이 깨지는 칸 (가운데 기준 (가로, 세로))
    var burst: [(Int, Int)] {
        let plus = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]
        switch self {
        case .hard: return plus
        case .normal: return plus + [(1, 1), (1, -1), (-1, 1), (-1, -1)]
        case .easy: return plus + [(1, 1), (1, -1), (-1, 1), (-1, -1), (2, 0), (-2, 0), (0, 2), (0, -2)]
        }
    }
    var easier: GameLevel? { switch self { case .easy: nil; case .normal: .easy; case .hard: .normal } }
}

/// 단계: 쉼터 배경 하나와 그 권의 규칙
struct GameStage: Identifiable {
    let n: Int
    let rule: String
    var speedMul = 1.0, paddleMul = 1.0, extraLives = 0, items = true
    var mirror = false        // 프리퀄: 거울에 공이 비쳐 보임 (그림만)
    var flicker = false       // 1권: 형광등이 깜빡여 벽돌이 잠깐 안 보임, 출구 칸만 빛남
    var gravity = 0.0         // +: 벽돌 쪽으로 (2권 화성), −: 판 쪽으로 (9권 화산 연기)
    var well = false          // 3권: 가운데 원형 구조물이 공을 끌어당겨 휜다
    var portal = false        // 4권: 양쪽 벽이 입구, 반대편으로 나온다
    var sway = false          // 5권: 벽돌 줄이 물결처럼 흔들림
    var dash = false          // 7권: 줄표(—) 벽은 깨지지 않는다
    var hard2 = 0.0           // 10권: 안 구운 그릇 벽돌 (두 번 쳐야 깨짐) 비율
    var id: Int { n }
    var bg: ShelterBG { Shelter.backgrounds[n] }

    static let all: [GameStage] = [
        GameStage(n: 0, rule: "연습판. 거울 셋에 공이 비쳐 보여요. 공이 느리고 목숨이 둘 더 있어요.", speedMul: 0.85, extraLives: 2, mirror: true),
        GameStage(n: 1, rule: "형광등이 가끔 깜빡여 벽돌이 안 보여요. 노랗게 빛나는 출구 칸은 보너스.", flicker: true),
        GameStage(n: 2, rule: "중력이 약해요. 공이 느리고 벽돌 쪽으로 천천히 휘어요.", speedMul: 0.85, gravity: 7),
        GameStage(n: 3, rule: "가운데 원형 구조물이 지나가는 공을 끌어당겨요.", well: true),
        GameStage(n: 4, rule: "양쪽 벽이 입구예요. 공이 반대편 입구로 나와요.", portal: true),
        GameStage(n: 5, rule: "벽돌 줄이 운하 물결처럼 천천히 흔들려요.", sway: true),
        GameStage(n: 6, rule: "창이 좁아 판이 짧아요. 대신 공이 느려요.", speedMul: 0.8, paddleMul: 0.75),
        GameStage(n: 7, rule: "가운데 줄표(—)는 깨지지 않는 벽이에요.", dash: true),
        GameStage(n: 8, rule: "충전할 곳이 없어요. 물건이 떨어지지 않아요.", items: false),
        GameStage(n: 9, rule: "화산 연기가 공을 판 쪽으로 밀어 올려요.", gravity: -6),
        GameStage(n: 10, rule: "안 구운 그릇 벽돌은 두 번 쳐야 깨져요.", hard2: 0.3),
        GameStage(n: 11, rule: "모든 규칙이 조금씩 섞인 자유판.", flicker: true, portal: true, sway: true, hard2: 0.15),
    ]
}

/// 선수: 기본 인물 다섯 (+내 캐릭터는 평정 지수 유형의 스킬)
enum GamePlayer {
    static func skill(_ id: String) -> String {
        switch id {
        case "sio": "기다림 · 공을 판에 붙였다가 원할 때 보내요"
        case "seonbae": "되묻기 · 놓친 공을 판마다 한 번 되감아요"
        case "ir": "앞서 달림 · 공이 빠르고 점수 1.5배"
        case "sua": "자라기 · 깰수록 판이 조금씩 길어져요"
        default: "원칙 · 판이 길고 공 속도가 늘 같아요"
        }
    }
    /// (시작, 깼을 때, 끝났을 때)
    static func lines(_ id: String) -> (String, String, String) {
        switch id {
        case "sio": ("서두르지 않아도 돼. 잡았다가 보내자.", "기다린 만큼 깼네.", "괜찮아. 한 박자 쉬고 다시.")
        case "seonbae": ("한 번은 되돌릴 수 있어. 어디로 보낼 건데?", "그게 정말 네 실력이야? 맞네.", "왜 놓쳤는지 알겠어?")
        case "ir": ("빨리 가자. 점수는 더 줄게.", "벌써 끝! 다음 판!", "너무 빨랐나. 한 번 더!")
        case "sua": ("깰수록 판이 자라요. 천천히요.", "조금씩 자라서 여기까지 왔어요.", "다음엔 판이 더 일찍 자랄 거예요.")
        case "me": ("내 판이야. 가 보자.", "해냈다.", "다시 해 보자.")
        default: ("관찰을 시작합니다. 공은 위에서 아래로.", "기준 칸 수에 닿았어요. 기록했어요.", "여기까지예요. 다시 하면 돼요.")
        }
    }
    /// 쉼터 물건이 아이템일 때 하는 일
    static func item(_ id: String) -> String {
        switch id {
        case "barley_tea": "공이 10초 느려져요"
        case "porch_light": "10초 동안 공이 갈 길이 보여요"
        case "wall_clock": "공이 3초 멈춰요"
        case "hair_tie": "판이 15초 길어져요"
        case "table_chair": "공이 하나 더"
        case "asym_bowl": "10초 동안 판에 맞은 공이 벽돌이 많은 쪽으로 꺾여요"
        case "unfired_bowl": "위쪽에 한 번 막아 주는 보호막"
        case "jujube_seed": "10초 동안 점수 2배"
        case "elder_bead": "5초 동안 공이 벽돌을 뚫고 지나가요"
        case "rice_seeds": "공이 셋으로 갈라져요"
        default: ""
        }
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

    private static let key = "game.v1"
    static func load() -> GameRecord {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(GameRecord.self, from: $0) } ?? GameRecord()
    }
    func save() { if let d = try? JSONEncoder().encode(self) { UserDefaults.standard.set(d, forKey: Self.key) } }

    func open(_ n: Int, _ lv: GameLevel) -> Bool { n == 0 || cleared[lv.rawValue, default: []].contains(n - 1) }
    func bestOf(_ n: Int, _ lv: GameLevel) -> Int? { best["\(lv.rawValue).\(n)"] }
    func total(_ lv: GameLevel) -> Int { GameStage.all.reduce(0) { $0 + (bestOf($1.n, lv) ?? 0) } }
    func lossCount(_ n: Int, _ lv: GameLevel) -> Int { losses["\(lv.rawValue).\(n)"] ?? 0 }

    /// 한 판 끝: 새 최고 점수면 true
    @discardableResult
    mutating func finish(_ n: Int, _ lv: GameLevel, score: Int, won: Bool) -> Bool {
        let k = "\(lv.rawValue).\(n)"
        if won { cleared[lv.rawValue, default: []].insert(n); losses[k] = 0 } else { losses[k, default: 0] += 1 }
        let isBest = score > (best[k] ?? 0)
        if isBest { best[k] = score }
        return isBest
    }
}

/// 화면 새로고침마다 한 번 부른다
final class GameLoop: NSObject {
    var onStep: ((Double) -> Void)?
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0
    func start() {
        guard link == nil else { return }
        last = 0
        link = CADisplayLink(target: self, selector: #selector(step(_:)))
        link?.add(to: .main, forMode: .common)
    }
    func stop() { link?.invalidate(); link = nil }
    @objc private func step(_ l: CADisplayLink) {
        let dt = last == 0 ? 1.0 / 60 : min(1.0 / 20, l.timestamp - last)
        last = l.timestamp
        onStep?(dt)
    }
}

@Observable
final class BrickGame: Identifiable {
    enum Phase { case ready, playing, paused, cleared, over }
    struct Ball { var x: Double, y: Double, vx: Double, vy: Double; var stuck = true; var off = 0.0; var until = 0.0 }
    struct Drop { let id: String; var x: Double, y: Double }

    static let W = 40.0, H = 46.0, PY = 5.0, PH = 0.8, top = 21.0, r = 0.45

    // 그리기는 frame 이 바뀔 때마다 다시 한다 (나머지 값은 지켜보지 않는다)
    var frame = 0
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
    @ObservationIgnored var kind = [UInt8](repeating: 0, count: 1000)  // 1 바닥에도 있던 칸, 2 회복한 칸, 3 오늘 늘어난 칸, 4 연습 칸
    @ObservationIgnored var exitCell = -1
    @ObservationIgnored var balls: [Ball] = []
    @ObservationIgnored var drops: [Drop] = []
    @ObservationIgnored var effects: [String: Double] = [:]   // 이름 → 끝나는 시각
    @ObservationIgnored var shield = false
    @ObservationIgnored var rewindUsed = false
    @ObservationIgnored var paddleX = BrickGame.W / 2
    @ObservationIgnored var t = 0.0
    @ObservationIgnored var score = 0
    @ObservationIgnored var lives: Int
    @ObservationIgnored var broken = 0
    @ObservationIgnored var total = 0
    @ObservationIgnored var toast: (String, Double)? = nil
    @ObservationIgnored var newBest = false

    private let loop = GameLoop()
    private let motion = CMMotionManager()
    private let tap = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private var lastTap = 0.0
    @ObservationIgnored private var axisSign: (Bool, Double) = (true, 1)   // (가로 화면이면 y축, 부호)
    @ObservationIgnored private var axisCheck = -1.0

    var target: Int { max(1, Int((Double(total) * level.clear).rounded(.up))) }

    init(stage: GameStage, level: GameLevel, player: String, skill: String, items: [String], tilt: Bool, assist: Bool,
         filled: Int, floor: Int, yesterday: Int) {
        self.stage = stage; self.level = level; self.player = player; self.skill = skill
        self.items = stage.items ? items : []
        self.tilt = tilt; self.assist = assist
        self.lives = level.lives + stage.extraLives
        // 칸이 적어도 놀 수 있게 120칸까지는 연습 칸으로 채운다
        let f = max(0, min(1000, filled)), n = max(f, 120)
        for i in 0..<n {
            hp[i] = Double.random(in: 0..<1) < stage.hard2 ? 2 : 1
            kind[i] = i >= f ? 4 : i < floor ? 1 : (yesterday < f && i >= yesterday) ? 3 : 2
        }
        if stage.dash { for c in 14...25 { hp[12 * 40 + c] = -1 } }
        if stage.flicker { exitCell = (0..<1000).last { hp[$0] > 0 } ?? -1 }
        total = hp.filter { $0 > 0 }.count
        balls = [Ball(x: Self.W / 2, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0)]
        loop.onStep = { [weak self] dt in self?.step(dt) }
    }

    deinit { loop.stop(); motion.stopDeviceMotionUpdates() }

    // MARK: 조작

    func begin() {
        if tilt, motion.isDeviceMotionAvailable {
            motion.deviceMotionUpdateInterval = 1.0 / 60
            motion.startDeviceMotionUpdates()
        }
        tap.prepare()
        loop.start()
    }
    func end() { loop.stop(); motion.stopDeviceMotionUpdates() }

    /// 화면을 누르면: 처음엔 시작, 붙은 공은 보낸다
    func launch() {
        switch phase {
        case .ready: phase = .playing
        case .playing: break
        default: return
        }
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
    var dark: Bool { stage.flicker && t.truncatingRemainder(dividingBy: 7) > 5.7 }

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
            frame &+= 1; return
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
        balls = alive
        moveDrops(dt)
        if broken >= target { win(); frame &+= 1; return }
        if balls.isEmpty { loseBall() }
        frame &+= 1
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
        b.y = ny
        if b.y < Self.r && shield { shield = false; b.y = Self.r; b.vy = abs(b.vy); say("보호막이 막았어요") }
        return b.y > -Self.r
    }

    private func paddleHit(_ b: inout Ball, _ sp: Double) {
        buzz()
        if skill == "sio" {
            b.stuck = true; b.off = b.x - paddleX; b.until = t + 2
            b.vx = 0; b.vy = 0; return
        }
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
        if hp[i] == 2 { hp[i] = 1; return }
        let row = i / 40, col = i % 40
        var mul = skill == "ir" ? 1.5 : 1.0
        if on("double") { mul *= 2 }
        for (dx, dy) in level.burst {
            let c = col + dx, r = row + dy
            guard c >= 0, c < 40, r >= 0, r < 25 else { continue }
            let j = r * 40 + c
            guard hp[j] > 0 else { continue }
            hp[j] -= 1
            if hp[j] == 0 {
                broken += 1
                let p: Double = switch kind[j] { case 2: 2; case 3: 3; default: 1 }
                score += Int((p * mul).rounded())
                if j == exitCell { score += 100; say("출구 칸 +100") }
            }
        }
        if kind[i] == 3 { effects["fast"] = t + 5 }
        if !items.isEmpty, Double.random(in: 0..<1) < 0.14, let it = items.randomElement() {
            drops.append(Drop(id: it, x: Double(col) + 0.5, y: rect(i).midY))
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
        say("\(name) · \(GamePlayer.item(id))")
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
        if skill == "seonbae" && !rewindUsed {
            rewindUsed = true
            balls = [Ball(x: paddleX, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0, stuck: true, off: 0, until: t + 1.5)]
            say("되감기 · 한 번 돌려받았어요"); return
        }
        lives -= 1
        if lives <= 0 { phase = .over; end(); return }
        drops = []
        balls = [Ball(x: paddleX, y: Self.PY + Self.PH + Self.r, vx: 0, vy: 0, stuck: true, off: 0, until: t + 2.5)]
    }

    private func win() {
        score += lives * 50
        phase = .cleared
        end()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func say(_ s: String) { toast = (s, t + 2.2) }

    private func buzz() {
        guard t - lastTap > 0.06 else { return }
        lastTap = t; tap.impactOccurred(intensity: 0.6)
    }
}
