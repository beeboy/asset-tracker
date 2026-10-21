import Foundation

// 숫자 맞추기 (Swift 쪽): SiteCalc "웹과 같은 식" 중 calc_parity 가 보지 않는 식을 고정 입력으로 돌려 sc_app.json 을 쓴다.
// 컴파일: swiftc -O <calc 폴더>/CalcSrc.swift tools/num_parity/sc/main.swift   (CalcSrc.swift 는 tools/calc_parity.mjs gen 이 앱 원본에서 꺼낸 코드)
struct ScOut: Encodable {
    var consts: [String: Double] = [:]
    var addMonths: [String] = []
    var growth: [Double] = []
    var factorMove: [Double] = []
    var replay: [[Double]] = []
    var capGainTax: [Double] = []
    var fxAt: [Double] = []
}
let scDir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
var so = ScOut()
so.consts = ["wht": SiteCalc.wht, "cgt": SiteCalc.cgt, "cgtDed": SiteCalc.cgtDed, "krDivTax": SiteCalc.krDivTax]
for d in ["2024-01-31", "2024-02-29", "2023-12-15", "2026-10-21", "2025-03-31"] { for n in [-36, -24, -13, -12, -1, 0, 1, 11, 12, 25] { so.addMonths.append(SiteCalc.addMonths(d, n)) } }
let evs: [SiteCalc.Ev] = (0..<16).map { i in SiteCalc.Ev(d: SiteCalc.addMonths("2022-11-15", 3 * i), a: 0.21 + 0.013 * Double(i)) }
for t in ["2026-10-21", "2025-12-31", "2024-06-01", "2023-01-01"] { so.growth.append(SiteCalc.divGrowth(evs, today: t)) }
for f in ["mkt", "rate", "oil", "gold", "fx"] { for b in [-1.3, -0.2, 0, 0.7, 1.9] { for s in [-30.0, -5, 0, 0.25, 12, 40] { so.factorMove.append(SiteCalc.factorMove(f, beta: b, shock: s)) } } }
let betas: [String: Double] = ["mkt": 1.27, "rate": -0.031, "oil": 0.12]
for rp in SiteCalc.replays { for usd in [true, false] {
    let a = SiteCalc.replayMove(rp, usd: usd, beta: { betas[$0] }), b = SiteCalc.replayMove(rp, usd: usd, beta: { _ in nil })
    so.replay.append([a.total, a.fx, b.total, b.fx])
} }
for g in [-1e6, 0, 2_499_999, 2_500_000, 2_500_001, 13_370_000.5, 9.9e9] { so.capGainTax.append(SiteCalc.capGainTax(g)) }
for t in [0.0, 0.25, 1, 3.5] { for z in [-1.645, -0.674, 0, 0.674, 1.645] { so.fxAt.append(SiteCalc.fxAt(now: 1391.5, t: t, z: z, mu: 0.012, vol: 0.093)) } }
try JSONEncoder().encode(so).write(to: URL(fileURLWithPath: scDir + "/sc_app.json"))
print("SiteCalc 식: \(so.addMonths.count + so.growth.count + so.factorMove.count + so.replay.count * 4 + so.capGainTax.count + so.fxAt.count)개")
