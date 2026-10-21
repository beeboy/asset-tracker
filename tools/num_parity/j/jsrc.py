#!/usr/bin/env python3
"""숫자 맞추기 J 단계 (판 깨기 게임 엔진): 아이폰 원본 Data/BrickGame.swift 엔진 줄(12-862)을 그대로 꺼내 한 파일(main.swift)로 만들고,
고정 입력(시드 × 단계 × 난이도 × 인물 × 물건 × 판 움직임 대본 × 프레임)으로 돌린 기록을 app.json 에 쓴다. parity.py gen 이 부른다.
Kotlin 쪽은 android-app/app/src/test/java/com/naeilo/app/JParityTest.kt (같은 input.json → kotlin.json).

원본에서 바꾸는 것 (기계적으로, 값은 그대로):
- 아이폰은 난수를 시스템(SystemRandomNumberGenerator)에서 뽑는다. 다시 돌릴 수 있게 `.random(in:)`·`shuffled()`·`randomElement()`·`Bool.random()` 에
  `using: &gRng` (SplitMix64 시드) 를 붙인다. 뽑는 셈은 표준 라이브러리 그대로라 Kotlin GameRng 확장 함수와 같은 수가 나온다.
- `@Observable`·`@ObservationIgnored` 를 뺀다 (관찰 매크로 없이 같은 값). GameLines.bag 의 private 를 빼서 판마다 비운다.
- `DispatchQueue.main.async` → gAsync (틱 뒤에 바로 비운다. Kotlin 은 post 로 같은 차례).
- UIKit(진동·화면 방향)·CoreMotion(기울기)·AVFoundation(소리)은 기록만 하는 작은 대역. GameSound 는 Kind(866줄)만 원본.
- 공·판·물건 위치(ballXY·padX·dropXY)는 +100 해서 쓴다: 삼각함수(맥 libm 과 JVM StrictMath)가 끝자리에서 달라
  0 근처 값이 상대 1e-12 를 넘지 않게 (parity.py TOL_KEYS). 끝자리를 일부러 흔든 시험(약 6% 를 1ulp)에서도 칸·점수·소리는 그대로였다.
- 쉼터 물건·배경 이름은 SampleData.swift·Me.swift 원본 줄. Lines.pick(대사 파일)은 nil (Kotlin 도 linePick 을 null 로).
"""
import json, re, sys
from pathlib import Path

V = Path(__file__).resolve().parents[3] / "ios-app" / "Naeilo"


def span(rel, first, last, nth=1, k=1):
    """원본 줄을 번호 대신 내용으로 찾는다 (아이폰 코드가 위아래로 밀려도 같은 덩어리): first 줄부터 그 뒤 처음 나오는 last 줄까지"""
    t = (V / rel).read_text().split("\n")
    hits = [k for k, l in enumerate(t) if l == first]
    if len(hits) < nth: raise SystemExit(f"{rel}: 시작 줄을 못 찾음: {first!r}")
    i = hits[nth - 1]
    j = [x for x in range(i, len(t)) if t[x] == last][k - 1]
    return "\n".join(t[i:j + 1])


def lines(rel, a, b):
    return "\n".join((V / rel).read_text().split("\n")[a - 1:b])


def find(rel, start_pat, end_line_pat):
    ls = (V / rel).read_text().split("\n")
    a = next(i for i, l in enumerate(ls) if re.search(start_pat, l))
    b = next(i for i in range(a + 1, len(ls)) if re.fullmatch(end_line_pat, ls[i]))
    return "\n".join(ls[a:b + 1])


def rng_rewrite(src):
    """표준 라이브러리 난수 호출에 using: &gRng 를 붙인다"""
    out, i, n = [], 0, 0
    while True:
        j = src.find(".random(in:", i)
        if j < 0:
            out.append(src[i:]); break
        p = j + len(".random")
        depth, k = 0, p
        while True:
            c = src[k]
            depth += {"(": 1, ")": -1}.get(c, 0)
            if depth == 0: break
            k += 1
        out.append(src[i:k] + ", using: &gRng)"); i = k + 1; n += 1
    s = "".join(out)
    for a, b in ((".shuffled()", ".shuffled(using: &gRng)"), (".randomElement()", ".randomElement(using: &gRng)"), ("Bool.random()", "Bool.random(using: &gRng)")):
        n += s.count(a); s = s.replace(a, b)
    return s, n


def engine():
    src = span("Data/BrickGame.swift", "enum GameLevel: String, CaseIterable, Codable, Identifiable {", "}", 1, 6)
    src = src.replace("@Observable\n", "").replace("@ObservationIgnored ", "")
    src = src.replace("private static var bag", "static var bag")
    src = src.replace("DispatchQueue.main.async {", "gAsync {")
    src, n = rng_rewrite(src)
    assert n == 18, f"난수 호출 수가 바뀜: {n} (BrickGame.swift 가 바뀌었으면 jsrc.py·JParityTest 확인)"
    return src


STUBS = r'''
// ── 대역 (기록만) ──
struct SplitMix64: RandomNumberGenerator {
    var s: UInt64
    mutating func next() -> UInt64 {
        s &+= 0x9E3779B97F4A7C15
        var z = s
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}
var gRng = SplitMix64(s: 1)
var gAsyncQ: [() -> Void] = []
func gAsync(_ f: @escaping () -> Void) { gAsyncQ.append(f) }
var gFrame = 0
var gSounds: [[Any]] = []
var gHaptics: [[Any]] = []
var gGravity: (Double, Double)? = nil
var gOrient: UIInterfaceOrientation = .portrait

final class CMMotionManager {
    struct G { let x: Double; let y: Double }
    struct DM { let gravity: G }
    var isDeviceMotionAvailable = true
    var deviceMotionUpdateInterval = 0.0
    private(set) var isDeviceMotionActive = false
    func startDeviceMotionUpdates() { isDeviceMotionActive = true }
    func stopDeviceMotionUpdates() { isDeviceMotionActive = false }
    var deviceMotion: DM? { guard isDeviceMotionActive, let g = gGravity else { return nil }; return DM(gravity: G(x: g.0, y: g.1)) }
}
final class UIImpactFeedbackGenerator {
    enum FeedbackStyle { case light, medium, heavy }
    let style: FeedbackStyle
    init(style: FeedbackStyle) { self.style = style }
    func prepare() { gHaptics.append([gFrame, "prepare:\(style)"]) }
    func impactOccurred() { gHaptics.append([gFrame, "\(style)"]) }
    func impactOccurred(intensity: Double) { gHaptics.append([gFrame, "\(style):\(intensity)"]) }
}
final class UINotificationFeedbackGenerator {
    enum FeedbackType { case success, warning, error }
    func notificationOccurred(_ t: FeedbackType) { gHaptics.append([gFrame, "\(t)"]) }
}
enum UIInterfaceOrientation { case portrait, portraitUpsideDown, landscapeLeft, landscapeRight }
final class UIWindowScene { var interfaceOrientation: UIInterfaceOrientation { gOrient } }
final class UIApplication { static let shared = UIApplication(); var connectedScenes: [AnyObject] { [UIWindowScene()] } }
enum Lines { static func pick(_ key: String, _ vars: [String: String] = [:], salt: Int = 0) -> String? { nil } }
final class GameSound {
__KIND__
    static let shared = GameSound()
    func start() {}
    func play(_ k: Kind) { gSounds.append([gFrame, "\(k)"]) }
}
'''

DRIVER = r'''
// ── 돌리기 ──
func phaseIdx(_ p: BrickGame.Phase) -> Int { switch p { case .ready: 0; case .playing: 1; case .paused: 2; case .cleared: 3; case .over: 4 } }
func specIdx(_ s: BrickGame.Special?) -> Int {
    guard let s else { return -1 }
    switch s { case .bomb: return 0; case .row: return 1; case .col: return 2; case .multi: return 3; case .gift: return 4 }
}
func moodName(_ e: GameLines.Event) -> String { e.rawValue }
/** 판 움직임 대본: 판 쪽으로 오는(vy<0) 공 중 가장 가까운 공의 x + 흔들림 */
func aim(_ g: BrickGame, _ k: Int, _ lazy: Bool) -> Double? {
    var best: BrickGame.Ball? = nil
    for b in g.balls where !b.stuck && b.vy < 0 { if best == nil || b.y < best!.y { best = b } }
    guard let b = best else { return nil }
    return b.x + Double((k / 40) % 5 - 2) * (lazy ? 2.7 : 0.9)
}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var out: [String: Any] = [:]
var games: [[String: Any]] = []
for sc in inp["games"] as! [[String: Any]] {
    gRng = SplitMix64(s: UInt64(sc["seed"] as! Int))
    GameLines.bag = [:]
    gFrame = 0; gSounds = []; gHaptics = []; gGravity = nil; gOrient = .portrait; gAsyncQ = []
    let g = BrickGame(stage: GameStage.all[sc["stage"] as! Int], level: GameLevel(rawValue: sc["level"] as! String)!, player: sc["player"] as! String,
                      skill: sc["skill"] as! String, items: sc["items"] as! [String], tilt: sc["tilt"] as! Bool, assist: sc["assist"] as! Bool,
                      filled: sc["filled"] as! Int, floor: sc["floor"] as! Int, yesterday: sc["yesterday"] as! Int, chain: sc["chain"] as! Int, lives: sc["lives"] as? Int)
    g.friends = sc["friends"] as! [String]
    var r: [String: Any] = [:]
    r["init"] = ["hp": g.hp.map { Int($0) }, "kind": g.kind.map { Int($0) }, "special": g.special.map { specIdx($0) }, "group": g.group,
                 "bigs": g.bigs.map { [$0.cells, $0.shape, $0.hp] as [Any] }, "exitCell": g.exitCell, "total": g.total, "target": g.target,
                 "stacked": g.stacked, "lives": g.lives, "net": g.net, "speech": g.speech, "guest": g.guest, "guestLine": g.guestLine] as [String: Any]
    g.begin()
    var now = 800000000.0
    let step = 1.0 / Double(sc["den"] as! Int)
    let ctrl = sc["ctrl"] as! String
    let events = sc["events"] as! [[Any]]
    g.tick(Date(timeIntervalSinceReferenceDate: now))
    var f: [[Int]] = [], padX: [Double] = [], ballXY: [[[Double]]] = [], dropXY: [[[Double]]] = [], dropId: [[String]] = []
    var cells: [[Int]] = [], bigs: [[Int]] = [], speech: [[Any]] = [], toast: [[Any]] = [], guest: [[Any]] = []
    var hp = g.hp.map { Int($0) }, rev = g.revealed, spec = g.special.map { specIdx($0) }, bg = g.bigs.map { [$0.hp, $0.revealed ? 1 : 0] }
    var sU = g.speechUntil, tU: Double = -1, tS = "", gU = g.guestUntil
    var ended = 0
    for k in 1...(sc["frames"] as! Int) {
        gFrame = k
        for ev in events where ev[0] as! Int == k {
            switch ev[1] as! String {
            case "start": g.startPlay()
            case "pause": g.pause()
            case "resume": g.resume()
            case "launch": g.launch()
            case "hitch": now += ev[2] as! Double
            case "orient": gOrient = [UIInterfaceOrientation.portrait, .landscapeLeft, .landscapeRight][ev[2] as! Int]
            case "drag": g.drag(to: ev[2] as! Double)
            default: break
            }
        }
        if ctrl == "track" || (ctrl == "lazy" && k % 3 == 0), let x = aim(g, k, ctrl == "lazy") { g.drag(to: x) }
        if ctrl == "tilt", let x = aim(g, k, false) { gGravity = ((x - 20) / 50, (x - 20) / 50) }
        now += step
        g.tick(Date(timeIntervalSinceReferenceDate: now))
        let q = gAsyncQ; gAsyncQ = []; q.forEach { $0() }
        f.append([phaseIdx(g.phase), g.score, g.broken, g.lives, g.combo, g.balls.count, g.drops.count, g.shield ? 1 : 0, g.net, g.picked.values.reduce(0, +)])
        padX.append(g.paddleX + 100)
        ballXY.append(g.balls.map { [$0.x + 100, $0.y + 100, $0.vx + 100, $0.vy + 100] })
        dropXY.append(g.drops.map { [$0.x + 100, $0.y + 100] })
        dropId.append(g.drops.map { $0.id })
        for i in 0..<1000 where Int(g.hp[i]) != hp[i] || g.revealed[i] != rev[i] || specIdx(g.special[i]) != spec[i] {
            hp[i] = Int(g.hp[i]); rev[i] = g.revealed[i]; spec[i] = specIdx(g.special[i])
            cells.append([k, i, hp[i], rev[i] ? 1 : 0, spec[i]])
        }
        for (i, b) in g.bigs.enumerated() where [b.hp, b.revealed ? 1 : 0] != bg[i] { bg[i] = [b.hp, b.revealed ? 1 : 0]; bigs.append([k, i, b.hp, bg[i][1]]) }
        if g.speechUntil != sU { sU = g.speechUntil; speech.append([k, g.speech, moodName(g.mood)]) }
        if (g.toast?.0 ?? "") != tS || (g.toast?.1 ?? -1) != tU { tS = g.toast?.0 ?? ""; tU = g.toast?.1 ?? -1; toast.append([k, tS]) }
        if g.guestUntil != gU { gU = g.guestUntil; guest.append([k, g.guest, g.guestLine]) }
        if g.phase == .cleared || g.phase == .over { ended += 1; if ended > 3 { break } }
    }
    r["f"] = f; r["padX"] = padX; r["ballXY"] = ballXY; r["dropXY"] = dropXY; r["dropId"] = dropId
    r["cells"] = cells; r["bigs"] = bigs; r["speech"] = speech; r["toast"] = toast; r["guest"] = guest
    r["sounds"] = gSounds; r["haptics"] = gHaptics
    r["end"] = ["t": g.t, "effects": g.effects.keys.sorted().map { [$0, g.effects[$0]!] as [Any] }, "picked": g.picked.keys.sorted().map { [$0, g.picked[$0]!] as [Any] },
                "active": g.activeEffects, "rewindUsed": g.rewindUsed, "paddleW": g.paddleW, "speed": g.speed, "dark": g.dark, "guestShown": g.guestShown, "speaking": g.speaking] as [String: Any]
    games.append(r)
}
out["games"] = games

// 기록 (UserDefaults game.v1 와 같은 JSON): 읽기 → 한 판씩 끝내기 → 다시 쓰기
func recOut(_ r: GameRecord) -> [String: Any] {
    var o: [String: Any] = ["cleared": r.cleared.mapValues { $0.sorted() }, "best": r.best, "losses": r.losses, "level": r.level.rawValue, "tilt": r.tilt, "player": r.player]
    o["plays"] = (r.plays ?? [:]).mapValues { $0.map { [$0.score, $0.p] as [Any] } }
    o["total"] = GameLevel.allCases.map { r.total($0) }
    o["open"] = GameLevel.allCases.map { lv in (0..<12).map { r.open($0, lv) } }
    o["loss"] = GameLevel.allCases.map { lv in (0..<12).map { r.lossCount($0, lv) } }
    return o
}
var recs: [[String: Any]] = []
for rc in inp["records"] as! [[String: Any]] {
    var rec = try! JSONDecoder().decode(GameRecord.self, from: (rc["json"] as! String).data(using: .utf8)!)
    var o: [String: Any] = ["read": recOut(rec)]
    var enc = try! JSONSerialization.jsonObject(with: JSONEncoder().encode(rec)) as! [String: Any]
    enc["cleared"] = (enc["cleared"] as! [String: [Int]]).mapValues { $0.sorted() }
    o["enc"] = enc
    o["isBest"] = (rc["finishes"] as! [[Any]]).map { x in
        rec.finish(x[0] as! Int, GameLevel(rawValue: x[1] as! String)!, score: x[2] as! Int, won: x[3] as! Bool, player: x[4] as! String)
    }
    o["after"] = recOut(rec)
    recs.append(o)
}
out["records"] = recs
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
let fr = games.map { ($0["f"] as! [[Int]]).count }.reduce(0, +)
print("num_parity j: 판 \(games.count) · 프레임 \(fr) · 점수 \(games.map { (($0["f"] as! [[Int]]).last ?? [0, 0])[1] }) · 기록 \(recs.count)")
'''


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    kind = span("Data/BrickGame.swift", "    enum Kind: Hashable { case hit, tough, paddle, item, lose, clear, over, start, bomb, row, col, multi, gift, tetris(Int) }", "    enum Kind: Hashable { case hit, tough, paddle, item, lose, clear, over, start, bomb, row, col, multi, gift, tetris(Int) }")
    src = "import Foundation\nimport CoreGraphics\nimport Security\n// 꺼낸 원본 (tools/num_parity/j/jsrc.py 가 만든다. 고치지 말 것)\n"
    src += STUBS.replace("__KIND__", kind)
    src += span("Data/SampleData.swift", "struct ShelterItem: Identifiable {", "}") + "\nenum Shelter {\n" + find("Data/SampleData.swift", r"static let items: \[ShelterItem\]", r"    \]") + "\n}\n"
    src += span("Data/Me.swift", "struct ShelterBG: Identifiable, Hashable {", "    let volume: String") + "\n}\nextension Shelter {\n" + find("Data/Me.swift", r"static let backgrounds: \[ShelterBG\]", r"    \]") + "\n}\n"
    src += engine() + "\n" + DRIVER
    (out / "main.swift").write_text(src)

    allItems = ["barley_tea", "porch_light", "wall_clock", "hair_tie", "table_chair", "asym_bowl", "unfired_bowl", "jujube_seed", "elder_bead", "rice_seeds"]
    base = dict(items=[], tilt=False, assist=False, filled=600, floor=300, yesterday=500, chain=0, lives=None, friends=["sio", "ir", "sua"],
                frames=2000, den=60, ctrl="track", events=[[20, "start"]])
    G = lambda **k: {**base, **k}
    games = [
        G(seed=11, stage=0, level="easy", player="seri", skill="seri"),
        G(seed=12, stage=1, level="normal", player="sio", skill="sio", items=allItems[:5], ctrl="lazy", friends=["seri", "sio"]),
        G(seed=13, stage=2, level="hard", player="seonbae", skill="seonbae", items=allItems, ctrl="lazy", filled=900, floor=900, yesterday=850),
        G(seed=14, stage=3, level="easy", player="ir", skill="ir", items=["rice_seeds", "elder_bead", "jujube_seed"], chain=3, lives=2),
        G(seed=15, stage=4, level="normal", player="sua", skill="sua", items=["asym_bowl", "unfired_bowl", "table_chair"], tilt=True, ctrl="tilt", assist=True),
        G(seed=16, stage=5, level="hard", player="me", skill="calm", items=allItems, tilt=True, ctrl="tilt", friends=["me", "seri", "ir"],
          events=[[10, "orient", 1], [20, "start"], [600, "orient", 2], [1200, "orient", 0]]),
        G(seed=17, stage=7, level="normal", player="seri", skill="seri", ctrl="none", friends=[], frames=3000),
        G(seed=18, stage=9, level="easy", player="sio", skill="sio", items=allItems, den=30, frames=1500,
          events=[[15, "start"], [100, "hitch", 0.2], [400, "hitch", 0.031], [700, "hitch", -0.01]]),
        G(seed=19, stage=10, level="normal", player="ir", skill="ir", items=allItems, filled=1000, floor=0, yesterday=0,
          events=[[5, "pause"], [8, "resume"], [20, "start"], [21, "launch"], [300, "pause"], [360, "resume"], [361, "launch"], [900, "drag", 3.0]]),
        G(seed=20, stage=11, level="hard", player="seonbae", skill="seonbae", items=allItems, ctrl="lazy", chain=1, lives=1, frames=3000),
        G(seed=21, stage=6, level="easy", player="sua", skill="sua", filled=0, floor=0, yesterday=0, items=allItems),
        G(seed=22, stage=8, level="hard", player="seri", skill="seri", items=allItems, events=[[1, "start"], [2, "drag", 39.0]]),
    ]
    rec = {"cleared": {"normal": [1, 0], "easy": [0]}, "best": {"normal.0": 120, "normal.1": 340}, "losses": {"normal.2": 3}, "level": "hard",
           "tilt": False, "player": "sio", "plays": {"normal.0": [{"score": 120, "p": "sio", "at": 812345678.25}, {"score": 90, "p": "ir", "at": 812300000.5}]}}
    rec2 = {k: v for k, v in rec.items() if k != "plays"}
    fin = [[2, "normal", 500, True, "seri"], [3, "normal", 0, False, "seri"], [3, "normal", 80, False, "ir"], [0, "normal", 150, True, "sua"],
           [0, "normal", 100, True, "sua"], [0, "easy", 30, False, "seri"], [5, "hard", 10, True, "me"]] + [[0, "normal", 10 * i, False, "seri"] for i in range(1, 12)]
    records = [{"json": json.dumps(rec), "finishes": fin}, {"json": json.dumps(rec2), "finishes": fin[:3]}]
    (out / "input.json").write_text(json.dumps({"games": games, "records": records}, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1])
