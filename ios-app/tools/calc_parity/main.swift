import Foundation

// 웹·앱 화면 계산 비교 도구 (앱 쪽): tools/calc_parity.mjs gen 이 만든 input.json 과, 앱 원본에서 꺼낸 CalcSrc.swift 로 app.json 을 쓴다.
// 컴파일: swiftc -O <폴더>/CalcSrc.swift ios-app/tools/calc_parity/main.swift -o calc_parity
// 사용: ./calc_parity <폴더>   (GitHub Actions ios.yml)
struct Input: Decodable {
    struct FxCase: Decodable { var name: String; var mar: MarFile?; var live: Double }
    struct H: Decodable { var t: String; var ccy: String; var qty: Double; var last: Double; var avg: Double }
    struct ValCase: Decodable { var name: String; var mar: MarFile?; var live: Double; var rows: [H] }
    struct DivCase: Decodable { var name: String; var ccy: String; var events: [SiteCalc.Ev]; var shares: Double; var price: Double }
    struct TaxCase: Decodable { var name: String; var rows: [H] }
    struct Rate: Decodable { struct C: Decodable { var dr: Double; var m: Double }; var coef: [[[Double]]]; var g0: Double; var cases: [C]; var kb: Double; var T: [Double] }
    var today: String; var live: Double; var fresh: MarFile
    struct WPathCase: Decodable { var name: String; var start: String; var goalDate: String; var goal: Double; var v0: Double; var monthly: Double }
    var fxRule: [FxCase]; var val: [ValCase]; var divs: [DivCase]; var tax: [TaxCase]; var rate: Rate; var wpath: [WPathCase]
}
struct Out: Encodable {
    struct Named: Encodable { var name: String; var fx: Double }
    struct Val: Encodable { var name: String; var rows: [Double]; var total: Double }
    struct D: Encodable { var n: Int; var y1, y3, gr, yld: Double; var byMonth: [String: Double] }
    struct Div: Encodable { var name: String; var fx: Double; var sumFx: Double; var out: D? }
    struct Tax: Encodable { var name: String; var gain: Double; var tax: Double }
    struct Rate: Encodable { var dr, m, T: Double; var yr, cum: [[Double]]; var drop: [Double]; var dropCum: Double; var mkt: [Double]; var shift: [Double] }
    var consts: [String: Double] = [:]
    struct WPath: Encodable { var name: String; var need: [Double]? }
    var fxRule: [Named] = []; var val: [Val] = []; var divs: [Div] = []; var tax: [Tax] = []; var rate: [Rate] = []; var wpath: [WPath] = []
}

let dir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let inp = try JSONDecoder().decode(Input.self, from: Data(contentsOf: URL(fileURLWithPath: dir + "/input.json")))
let sym = { (h: Input.H) in Symbol(currency: h.ccy == "USD" ? .usd : .krw, last: h.last) }
let row = { (h: Input.H) in Row(id: h.t, sym: sym(h), h: Hold(avg: h.avg, qty: h.qty)) }
var out = Out()
out.consts = ["WHT": SiteCalc.wht, "CGT": SiteCalc.cgt, "CGT_DED": SiteCalc.cgtDed]

// 환율 고르기 (Market.valFx)
for c in inp.fxRule {
    Market.shared = FxState(mar: c.mar, fx: FxQuote(last: c.live))
    out.fxRule.append(.init(name: c.name, fx: Market.shared.valFx))
}
// 평가액 (AppModel.rows: krw(종목, 수량 × 지금 가격))
for c in inp.val {
    Market.shared = FxState(mar: c.mar, fx: FxQuote(last: c.live))
    let v = c.rows.map { App.krw(sym($0), $0.qty * $0.last) }
    out.val.append(.init(name: c.name, rows: v, total: v.reduce(0, +)))
}
// 배당 (DividendView·AnalysisView: SiteCalc.project, 환율은 화면이 쓰는 식 그대로)
Market.shared = FxState(mar: inp.fresh, fx: FxQuote(last: inp.live))
for c in inp.divs {
    let krw = c.ccy == "KRW", fx = krw ? 1 : App.divFx(), sumFx = krw ? 1 : App.sumFx()
    let o = SiteCalc.project(c.events, shares: c.shares, fx: fx, krw: krw, price: c.price, today: inp.today)
    out.divs.append(.init(name: c.name, fx: fx, sumFx: sumFx, out: o.map { Out.D(n: $0.n, y1: $0.y1, y3: $0.y3, gr: $0.gr, yld: $0.yld, byMonth: $0.byMonth) }))
}
// 세금 "미국 종목 다 판다면" (올해 실현 이익 0)
for c in inp.tax {
    let g = App.usGain(M(rows: c.rows.map(row)))
    out.tax.append(.init(name: c.name, gain: g, tax: SiteCalc.capGainTax(0 + g)))
}
// 금리 시나리오 (RateScen, RateScenarioView 의 shifts). 앱은 기간을 받지 않으니 T 마다 같은 값
for c in inp.rate.cases {
    let r = RateScen(coef: inp.rate.coef, g0: inp.rate.g0, dr: c.dr, m: c.m)
    let s = App.shifts(r, kb: inp.rate.kb, d: c.dr, mm: c.m)
    for T in inp.rate.T {
        out.rate.append(.init(dr: c.dr, m: c.m, T: T, yr: r.yr, cum: r.cum, drop: r.dropYr, dropCum: r.dropCum, mkt: r.mkt, shift: s))
    }
}
// 위젯 필요 경로 (Snapshot.need). 비율 f 는 웹이 계산한 값을 받는다 (web.json)
struct WebW: Decodable { struct C: Decodable { var name: String; var f: [Double] }; var wpath: [C] }
let webW = try JSONDecoder().decode(WebW.self, from: Data(contentsOf: URL(fileURLWithPath: dir + "/web.json")))
for c in inp.wpath {
    let f = webW.wpath.first { $0.name == c.name }?.f ?? []
    out.wpath.append(.init(name: c.name, need: widgetNeed(v0: c.v0, start: c.start, goalDate: c.goalDate, goal: c.goal, monthly: c.monthly, f: f)))
}
try JSONEncoder().encode(out).write(to: URL(fileURLWithPath: dir + "/app.json"))
print("앱 계산: 환율 \(out.fxRule.count) · 평가액 \(out.val.count) · 배당 \(out.divs.count) · 세금 \(out.tax.count) · 금리 \(out.rate.count)")
