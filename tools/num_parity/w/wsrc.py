#!/usr/bin/env python3
"""숫자 맞추기 W 단계 (파도 타기 한 판 다시 돌리기): 아이폰 원본에서 꺼내 한 파일(main.swift)로 만들고 고정 입력을 쓴다.

같은 시드(SplitMix64)·같은 코스 라인·같은 프레임 간격(dt)·같은 톡 프레임으로 한 판을 끝까지 굴려
프레임마다 엔진 상태(거래일·점수·하트·연속·탄 날·시간·피버·점프…), 연출 상태(WaveFX 타이머·각도·포즈·글자·점 개수),
그린 곡선 위 값(WaveDraw.pos·value·rising), 그 프레임의 이벤트와 소리·진동 기록을 남긴다. Kotlin 쪽은 WParityTest.

꺼내는 것 (원본 그대로):
  Data/WaveEngine.swift 2-363 (엔진 전부, AppModel 확장 빼고)
  Views/CharMotion.swift 40-44 (CharPose: 이름·sink)
  Views/Wave/WavePlayView.swift 160-176 (handle: 이벤트 → 탄 구간·왕관·연출), 198-226 (WaveResult), 230-312 (WaveFX), 330-340 (WaveDraw.pos·value·rising)
  Views/Wave/WaveFeedback.swift 8-126 (소리·진동 고르기. UIKit·AVFoundation 은 기록만 하는 대역)
화면 루프(WavePlayView.loop·tap 131-158)의 순서는 아래 Run.frame·Run.tap 이 그대로 따른다 (sleep 대신 입력 dt).
점 효과의 난수(Double.random)는 시스템 난수라 점 위치는 비교하지 않고 개수만 본다. 소리 데이터는 길이(바이트)만 본다.
asyncAfter(왕관 두 번째 톡)는 명령줄에선 돌지 않는다: Kotlin 시험도 after 를 기록하지 않는다.

  python3 tools/num_parity/w/wsrc.py <폴더>     # main.swift · input.json
  swiftc -O <폴더>/main.swift -o w && ./w <폴더>  # app.json
"""
import json, random, sys
from datetime import date, timedelta
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


def check(rel, n, want):
    got = (V / rel).read_text().split("\n")[n - 1].strip()
    assert got.startswith(want), f"{rel}:{n} 가 바뀜: {got!r} (wsrc.py 줄 번호 확인)"


STUBS = r'''
// ── 대역 (기록만) ──
extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: opacity)
    }
}
var LOG: [String] = []
final class UIImpactFeedbackGenerator {
    enum FeedbackStyle { case light, rigid }
    let style: FeedbackStyle
    init(style: FeedbackStyle) { self.style = style }
    func prepare() {}
    func impactOccurred(intensity: CGFloat = 1) { LOG.append("impact:\(style):\(Double(intensity))") }
}
final class UINotificationFeedbackGenerator {
    enum FeedbackType { case error, success }
    func notificationOccurred(_ t: FeedbackType) { LOG.append("note:\(t)") }
}
final class AVAudioSession {
    enum Category { case ambient }
    struct CategoryOptions: OptionSet { let rawValue: Int; static let mixWithOthers = CategoryOptions(rawValue: 1) }
    static func sharedInstance() -> AVAudioSession { AVAudioSession() }
    func setCategory(_ c: Category, options: CategoryOptions) throws {}
}
final class AVAudioPlayer {
    let n: Int
    var currentTime: Double = 0
    init(data: Data) throws { n = data.count }
    func prepareToPlay() {}
    func play() { LOG.append("sound:\(n)") }
}
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
func enc(_ x: WaveEngine.Event) -> [Any] {
    switch x {
    case .board(let d, let p): return ["board", d, p]
    case .leave(let d, let g, let pts): return ["leave", d, g, pts]
    case .splash(let d, let drop, let h): return ["splash", d, drop, h]
    case .calm(let d): return ["calm", d]
    case .streak(let n): return ["streak", n]
    case .fever(let on): return ["fever", on]
    case .flip(let n): return ["flip", n]
    case .land(let f, let c, let p): return ["land", f, c, p]
    case .item(let it): return ["item", "\(it)"]
    case .time(let s): return ["time", s]
    case .saved(let d): return ["saved", d]
    case .mission(let m): return ["mission", "\(m)"]
    case .end(let w): return ["end", "\(w)"]
    }
}
'''


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    check("Data/WaveEngine.swift", 363, "}")
    check("Views/CharMotion.swift", 40, "enum CharPose")
    check("Views/Wave/WavePlayView.swift", 160, "private func handle")
    check("Views/Wave/WavePlayView.swift", 198, "struct WaveResult")
    check("Views/Wave/WavePlayView.swift", 230, "struct WaveFX")
    check("Views/Wave/WavePlayView.swift", 330, "static func pos")
    check("Views/Wave/WavePlayView.swift", 340, "static func rising")
    check("Views/Wave/WaveFeedback.swift", 8, "final class WaveFeedback")
    check("Views/Wave/WaveFeedback.swift", 126, "}")
    src = f'''import Foundation
import SwiftUI
// 꺼낸 원본 (tools/num_parity/w/wsrc.py 가 만든다. 고치지 말 것)
{STUBS}
{span("Data/WaveEngine.swift", "", "}")}
{span("Views/CharMotion.swift", "enum CharPose: Int, Hashable {", "    var sink: Int { self == .crouch ? 2 : self == .yawn ? 4 : 0 }")}
}}
{span("Views/Wave/WavePlayView.swift", "struct WaveResult {", "}")}
{span("Views/Wave/WavePlayView.swift", "struct WaveFX {", "}")}
enum WaveDraw {{
{span("Views/Wave/WavePlayView.swift", "    static func pos(_ e: WaveEngine) -> Double { min(Double(e.course.count - 1), e.elapsed / WaveEngine.secondsPerDay) }", "    static func rising(_ e: WaveEngine) -> Bool { let p = pos(e); return value(e, p + 0.15) > value(e, p - 0.15) }")}
}}
{span("Views/Wave/WaveFeedback.swift", "final class WaveFeedback {", "}")}

/// WavePlayView 의 @State g·fx·rides·boardDay 와 loop·tap 순서 (131-158)
final class Run {{
    var g: WaveEngine?
    var fx = WaveFX()
    var rides: [WaveResult.Ride] = []
    var boardDay = 0
    var ended: WaveEngine.EndReason?
    var evs: [[Any]] = []
    func finish(_ e: WaveEngine, _ why: WaveEngine.EndReason) {{ ended = why }}
{span("Views/Wave/WavePlayView.swift", "    private func handle(_ ev: [WaveEngine.Event]) {", "    }")}
    /// 한 걸음 (loop 141-148). 끝났으면 false
    func frame(_ raw: Double) -> Bool {{
        guard var e = g, !e.isOver else {{ return false }}
        let dt = min(0.1, raw)
        let ev = e.tick(dt)
        g = e
        evs += ev.map(enc)
        fx.step(dt, riding: e.riding, rising: WaveDraw.rising(e))
        handle(ev)
        return true
    }}
    /// 톡 (tap 154-157, 규칙 카드는 이미 닫힘)
    func tap() {{
        guard var e = g, !e.isOver else {{ return }}
        let ev = e.riding ? e.leave() : e.board()
        g = e
        evs += ev.map(enc)
        handle(ev)
    }}
}}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var cases: [[String: Any]] = []
var frames = 0
for c in inp["cases"] as! [[String: Any]] {{
    var rng = SplitMix64(s: UInt64(c["seed"] as! Int))
    let line = (c["line"] as! [[String]]).map {{ (d: $0[0], v: Double($0[1])!) }}
    guard let course = WaveEngine.course(from: line, length: c["len"] as! Int, using: &rng) else {{ cases.append(["course": "none"]); continue }}
    let run = Run()
    run.g = WaveEngine(course: course, using: &rng)
    let e0 = run.g!
    var o: [String: Any] = [
        "island": "\\(course.island)", "level": "\\(course.level)", "start": course.startDate, "count": course.count,
        "items": e0.items.keys.sorted().map {{ [$0, "\\(e0.items[$0]!)"] as [Any] }},
        "missions": e0.missions.map {{ "\\($0)" }}, "splashDrop": e0.splashDrop,
    ]
    var F: [String: [Any]] = [:]
    let taps = Set(c["taps"] as! [Int])
    for (i, s) in (c["dts"] as! [String]).enumerated() {{
        LOG = []; run.evs = []
        guard run.frame(Double(s)!) else {{ break }}
        if taps.contains(i) {{ run.tap() }}
        let e = run.g!, fx = run.fx, p = WaveDraw.pos(e)
        let row: [(String, Any)] = [
            ("day", e.day), ("score", e.score), ("hearts", e.hearts), ("streak", e.streak), ("boardedAt", e.boardedAt ?? -1),
            ("elapsed", e.elapsed), ("clock", e.clock), ("extra", e.extra), ("feverLeft", e.feverLeft), ("air", e.air),
            ("flips", e.flips), ("combo", e.combo), ("beacon", e.beacon), ("slow", e.slow), ("boost", e.boost), ("vest", e.vest), ("dolphin", e.dolphin),
            ("over", e.isOver), ("timeLeft", e.timeLeft),
            ("fxFlip", fx.flip), ("fxDizzy", fx.dizzy), ("fxBoarded", fx.boarded), ("fxCalm", fx.calm), ("fxAngle", fx.angle), ("fxTurn", fx.turn),
            ("fxTimeFlash", fx.timeFlash), ("fxTimeGot", fx.timeGot), ("fxRising", fx.rising), ("pose", "\\(fx.pose(riding: e.riding))"),
            ("pop", fx.pop?.text ?? ""), ("popLife", fx.pop?.life ?? -1), ("dots", fx.dots.count),
            ("pos", p), ("val", WaveDraw.value(e, p)), ("rising", WaveDraw.rising(e)),
            ("ev", run.evs), ("fb", LOG),
        ]
        for (k, v) in row {{ F[k, default: []].append(v) }}
        frames += 1
    }}
    o["frames"] = F
    let e = run.g!
    o["rides"] = run.rides.map {{ [$0.from, $0.to, $0.gain, $0.splash] as [Any] }}
    o["ended"] = run.ended.map {{ "\\($0)" }} ?? "none"
    var r = WaveResult(course: e.course, score: e.score, hearts: e.hearts, rides: run.rides, why: run.ended ?? .time)
    r.missions = e.missions.map {{ ($0.title, e.missionsDone.contains($0)) }}
    o["result"] = ["stars": r.stars, "headline": r.headline, "dateLine": r.dateLine,
                   "missions": r.missions.map {{ [$0.title, $0.done] as [Any] }},
                   "counts": [e.perfectCount, e.maxStreak, e.flipCount, e.splashCount, e.feverCount, e.got.count]]
    cases.append(o)
}}
let d = try! JSONSerialization.data(withJSONObject: ["cases": cases], options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity w: 판 \\(cases.count) · 프레임 \\(frames)")
'''
    (out / "main.swift").write_text(src)

    # 고정 입력: 모양이 다른 라인 몇 개 (잔잔·오르막·한 번 크게 꺼짐·출렁) × 시드, 프레임 간격·톡 프레임
    R = random.Random(20261021)
    def line(kind, n=220):
        v, out_l, d0 = 1.0e8, [], date(2023, 1, 2)
        for i in range(n):
            if kind == "nap": r = R.gauss(0.0002, 0.0012)
            elif kind == "climb": r = R.gauss(0.004, 0.006)
            elif kind == "coaster": r = R.gauss(0.001, 0.008) - (0.09 if i in (60, 61, 140) else 0)
            elif kind == "crash": r = R.gauss(0.003, 0.003) - (0.07 if i % 9 == 4 else 0)   # 자주 꺼짐: 풍덩·하트·구명조끼
            else: r = R.gauss(0, 0.02) * (1 if i % 2 else -1)
            if i % 17 == 0: r = 0.0004   # 아주 잔잔한 날 (calm)
            v *= 1 + r
            out_l.append([(d0 + timedelta(days=i)).isoformat(), repr(round(v, 2))])
        return out_l
    cases = []
    for k, kind in enumerate(["nap", "climb", "coaster", "choppy", "crash", "crash", "coaster"]):
        for seed in (1 + k, 1000 + 7 * k):
            dts, taps, t, i = [], [], 0, 0
            while i < 2400:
                x = 1 / 30 + R.uniform(-0.004, 0.006)
                if R.random() < 0.01: x = R.choice([0.0, 0.12, 0.25, 0.05])   # 멈칫·느린 프레임 (0.1 로 자른다)
                dts.append(repr(x)); i += 1
            j = R.randint(3, 10)
            while j < 2400:
                taps.append(j)
                if R.random() < 0.3:   # 내린 뒤 공중에서 톡톡 (공중제비)
                    for q in range(1, R.randint(2, 5)): taps.append(j + 2 * q)
                j += R.randint(4, 40)
            cases.append({"seed": seed, "len": 100 if seed % 2 else 60, "line": line(kind), "dts": dts, "taps": sorted(set(taps))})
    # 짧은 라인 (코스 못 만듦) 하나
    cases.append({"seed": 5, "len": 100, "line": line("nap", 40), "dts": ["0.033"], "taps": []})
    (out / "input.json").write_text(json.dumps({"cases": cases}, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1])
