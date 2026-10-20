#!/usr/bin/env python3
"""숫자 맞추기 G 단계 (분석): 아이폰 원본에서 꺼내 한 파일(main.swift)로 만들고 고정 입력을 쓴다. parity.py gen 이 부른다.

꺼내는 것 (원본 그대로): ForecastView.amt·short·ym (AnalysisView.swift 507-519), AnalysisView.freq (99-103),
ScenarioChart.step·axis (ForecastParts.swift 17-28), AppModel.manS·won1·wonRead (AnalysisDetail.swift 88-98), AppModel.man (영어 아님).
내 길 (G2): MyPath·yrs (AnalysisDetail.swift 21-29), myPath·myPathGapToday (50-86), MC.krAt, Day. 지난 평가액(pathSeries)·3년 흐름(totalAt)은 입력으로 준다.
"""
import json, sys
from pathlib import Path

V = Path(__file__).resolve().parents[3] / "ios-app" / "Naeilo"


def lines(rel, a, b):
    return "\n".join((V / rel).read_text().split("\n")[a - 1:b])


def find(rel, start, n):
    t = (V / rel).read_text().split("\n")
    i = next(k for k, l in enumerate(t) if start in l)
    return "\n".join(t[i:i + n])


def mp_src():
    """내 길 셈 원본 (고치지 않고 꺼낸다). AppModel 대신 같은 이름 값을 가진 구조체 PM 에 넣는다"""
    day = lines("../WidgetShared/Engine.swift", 76, 78)
    return f'''enum Route: String {{ case recover, plus, novice }}
enum Day {{
{day}
    static var today = ""
}}
enum MC {{
    struct KrDay {{ var d: String; var v: Double }}
{find("Data/MonteCarlo.swift", "static func krAt(_ log: [KrDay]", 5)}
}}
final class CustomSymbols {{ static let shared = CustomSymbols(); var krLog: [MC.KrDay] = [] }}
struct PM {{
    var isGoal = false, goalSpan = 1.0, pathStartDate = Date(), gM = 0.0, keyValue = 0.0, trackValue = 0.0
    var route = Route.recover, isSample = false, krSum = 0.0
    var S: [(d: String, v: Double)] = [], tot: [Double] = []
    func pathSeries(from start: String) -> [(d: String, v: Double)] {{ S }}
    func totalAt(_ x: Double) -> Double {{ tot[max(0, min(tot.count - 1, Int((x * Double(tot.count - 1) + 0.5).rounded(.down))))] }}
{lines("Views/AnalysisDetail.swift", 21, 29)}
{lines("Views/AnalysisDetail.swift", 50, 86)}
}}
func runPath(_ c: [String: Any]) -> [String: Any] {{
    var m = PM()
    m.isGoal = c["isGoal"] as! Bool; m.goalSpan = c["goalSpan"] as! Double; m.pathStartDate = Date(timeIntervalSince1970: c["start"] as! Double)
    m.gM = c["gM"] as! Double; m.keyValue = c["K"] as! Double; m.trackValue = c["now"] as! Double
    m.route = Route(rawValue: c["route"] as! String)!; m.isSample = c["isSample"] as! Bool; m.krSum = c["krSum"] as! Double
    m.S = (c["S"] as! [[Any]]).map {{ (d: $0[0] as! String, v: $0[1] as! Double) }}; m.tot = c["tot"] as! [Double]
    CustomSymbols.shared.krLog = (c["KL"] as! [[Any]]).map {{ MC.KrDay(d: $0[0] as! String, v: $0[1] as! Double) }}
    Day.today = c["today"] as! String
    let p = m.myPath, ts = (c["ts"] as! [String]).map {{ Double($0)! }}   // 글자로 받는다 (JSONSerialization 은 소수 끝자리를 틀리게 읽는다)
    return ["start": p.start, "end": p.end, "key": p.key, "monthly": p.monthly, "krIn": p.krIn,
            "pts": p.pts.map {{ [$0.0, $0.1] }}, "act": ts.map {{ p.act($0) }}, "need": ts.map {{ p.need($0) }}, "gap": m.myPathGapToday]
}}
'''


def x_src():
    """G2 나머지 (외부 요인·요인 상세·금리 시나리오·배당·환율·한눈에, AnalysisDetail.swift 781-2691). 원본 줄을 그대로 꺼내
    같은 이름 값을 가진 작은 대역(SiteCalc·Market·PriceHistory·Sample·MacroData)과 함께 둔다. 화면(body)은 빼고 셈만"""
    A = "Views/AnalysisDetail.swift"
    acc = find(A, ".accessibilityLabel(String(localized: \"달마다 배당: \")", 1).strip()
    acc = acc[len(".accessibilityLabel("):-1]   # 달마다 배당 읽기 글 (1999, 수식어 안의 식만)
    return f'''enum Currency {{ case usd, krw }}
struct Symbol {{ let id: String; let name: String; let currency: Currency; var short: String {{ currency == .usd ? id : name }} }}
extension MC {{
    struct BetaStat {{ var corr: Double?; var agree: Double?; var n: Double? }}
    struct FactorMove {{ var sd: Double; var max: Double; var n: Double? }}
}}
struct Hist {{ let dates: [String]; let closes: [Double] }}
enum PriceHistory {{ static var h: [String: Hist] = [:]; static func of(_ id: String) -> Hist? {{ h[id] }} }}
enum Sample {{ static var syms: [String: Symbol] = [:]; static func symbol(_ id: String) -> Symbol? {{ syms[id] }} }}
struct Quote {{ var last: Double }}
final class Market {{ static let shared = Market(); var fx = Quote(last: 0) }}
final class SiteCalc {{
    static let shared = SiteCalc()
    struct DivOut {{ var byMonth: [String: Double] }}
    var betas: [String: [String: Double]] = [:], stat: [String: [String: MC.BetaStat]] = [:], factors: [String: MC.FactorMove] = [:]
    func beta(_ f: String, _ id: String) -> Double? {{ betas[f]?[id] }}
    func betaStat(_ f: String, _ id: String) -> MC.BetaStat? {{ stat[f]?[id] }}
    func factorMonth(_ f: String) -> MC.FactorMove? {{ factors[f] }}
{lines("Data/SiteCalc.swift", 94, 96)}
}}
struct XRow {{ let id: String; let sym: Symbol; let value: Double }}
struct XM {{ var rows: [XRow] = []; var total: Double {{ rows.reduce(0) {{ $0 + $1.value }} }} }}

struct ExternalView {{
    var m = XM()
{lines(A, 786, 879)}
}}
extension ExternalView {{
    func t_ranks() -> [[Any]] {{ ranks.map {{ [$0.k, $0.amt, $0.gross, $0.weak, desc($0)] }} }}
    func t_summary() -> Any {{ N(summary(ranks)) }}
    func t_summary(_ rs: [[Any]]) -> Any {{ N(summary(rs.map {{ Rank(k: $0[0] as! String, amt: Double($0[1] as! String)!, gross: 0, weak: $0[2] as! Bool) }})) }}
}}
struct FactorDetailView {{
    let factor: String
    var shock: Double? = nil
{lines(A, 1124, 1159)}
{lines(A, 1289, 1296)}
}}
extension FactorDetailView {{
    func t_presets() -> [[Any]] {{ presets.map {{ [$0.0, $0.1] }} }}
    func t_range() -> [Double] {{ [range.lowerBound, range.upperBound] }}
    func t_text(_ v: Double) -> String {{ text(v) }}
    func t_huge(_ v: Double) -> Bool {{ huge(v) }}
    func t_label(_ dv: Double, _ weak: Bool, _ known: Bool) -> String {{ label(dv, weak, known) }}
    func t_rows(_ m: XM, _ v: Double, _ total: Double) -> [[Any]] {{
{lines(A, 1166, 1172)}
        return rows.map {{ [$0.id, $0.label, $0.dv, N($0.agree), $0.weak, $0.known] }}
    }}
}}
final class MacroData {{
    static let shared = MacroData()
{lines(A, 1335, 1342)}
    private var memo: [String: RateScen] = [:]
{lines(A, 1378, 1391)}
}}
{lines(A, 1394, 1439)}
struct RateScenarioView {{
{lines(A, 1479, 1480)}
{lines(A, 1484, 1503)}
{lines(A, 1666, 1674)}
{lines(A, 1704, 1713)}
}}
extension RateScenarioView {{
    private static func t_closed(_ c: [Double]) -> Closed {{ Closed(T: c[0], sg: c[1], mu: c[2], V0: c[3], K: c[4], M: c[5]) }}
    static func t_out(_ c: [Double], _ pGoal: Double, _ shifts: [Double]) -> [String: Any] {{
        let ci = t_closed(c), f = AppModel.Forecast(pGoal: pGoal)
        return ["rateClosed": shifts.map {{ closed(ci, $0) }}, "rateProb": shifts.map {{ prob(f, ci, $0) }}, "months": shifts.map {{ months(ci, $0) ?? -1 }},
                "timing": shifts.map {{ timing(months(ci, 0), months(ci, $0)) }}]
    }}
    static func t_timing(_ a: Int?, _ b: Int?) -> String {{ timing(a, b) }}
}}
struct RateWhyView {{
{lines(A, 1754, 1760)}
}}
extension RateWhyView {{ func t_lv(_ v: Double) -> String {{ lv(v) }} }}
struct DividendView {{
{lines(A, 1829, 1830)}
{lines(A, 2162, 2165)}
}}
extension DividendView {{
    func t_month(months: [String], byMonth: [Double], paid: [Paid]) -> [Any] {{
{lines(A, 1952, 1981)}
        let label = {acc}
        return [mMax, hi.sorted(), years.map {{ [$0.y, $0.n] as [Any] }}, notes, label]
    }}
}}
{lines(A, 2398, 2419)}
struct GlanceView {{
{lines(A, 2431, 2433)}
}}

func N<T>(_ x: T?) -> Any {{ x.map {{ $0 as Any }} ?? NSNull() }}
func D(_ x: Any) -> Double {{ Double(x as! String)! }}   // 글자로 받는다 (JSONSerialization 은 소수 끝자리를 틀리게 읽는다)
func DS(_ x: Any) -> [Double] {{ (x as! [String]).map {{ Double($0)! }} }}
func ON(_ x: Any?) -> Double? {{ (x as? String).map {{ Double($0)! }} }}
func runX(_ X: [String: Any]) -> [String: Any] {{
    var o: [String: Any] = [:]
    Market.shared.fx = Quote(last: D(X["fx"]!))
    for (id, h) in X["hist"] as! [String: [String: Any]] {{ PriceHistory.h[id] = Hist(dates: h["dates"] as! [String], closes: DS(h["closes"]!)) }}
    var syms: [String: Symbol] = [:]
    for s in X["syms"] as! [[String]] {{ syms[s[0]] = Symbol(id: s[0], name: s[1], currency: s[2] == "usd" ? .usd : .krw) }}
    for id in X["sample"] as! [String] {{ Sample.syms[id] = syms[id] }}
    let ids = X["ids"] as! [String], shocks = X["shocks"] as! [String: [String]]
    let facs = ["mkt", "fx", "rate", "oil", "gold", "cmdty"]
    var cs: [[String: Any]] = []
    for c in X["cases"] as! [[String: Any]] {{
        let sc = SiteCalc.shared
        sc.betas = (c["betas"] as! [String: [String: String]]).mapValues {{ $0.mapValues {{ Double($0)! }} }}
        sc.stat = (c["stat"] as! [String: [String: String]]).mapValues {{ $0.mapValues {{ MC.BetaStat(corr: nil, agree: Double($0)!, n: nil) }} }}
        sc.factors = (c["factors"] as! [String: [String]]).mapValues {{ MC.FactorMove(sd: Double($0[0])!, max: Double($0[1])!, n: nil) }}
        let ev = ExternalView(m: XM(rows: (c["rows"] as! [[String]]).map {{ XRow(id: $0[0], sym: syms[$0[0]]!, value: Double($0[1])!) }}))
        var r: [String: Any] = [:]
        r["extBeta"] = ids.map {{ id -> [Any] in let s = syms[id]!, k = ExternalView.coef(s); return [N(ExternalView.estBeta(id)), N(ExternalView.mktBeta(id)), k.0, k.1, k.2] }}
        r["extMove"] = facs.map {{ f in ids.map {{ id in DS(shocks[f] ?? shocks["etc"]!).map {{ N(ExternalView.move(f, id, syms[id]!, $0)) }} }} }}
        r["extShock"] = facs.map {{ N(ExternalView.monthShock($0)) }}
        r["extRanks"] = ev.t_ranks(); r["summary"] = ev.t_summary()
        var fd: [[String: Any]] = []
        for (i, f) in facs.enumerated() {{
            let vs = DS(shocks[f] ?? shocks["etc"]!) + DS(X["texts"]!)
            var d = FactorDetailView(factor: f, shock: i % 2 == 0 ? nil : vs[0])
            var a: [String: Any] = ["range": d.t_range(), "text": vs.map {{ d.t_text($0) }}, "huge": vs.map {{ d.t_huge($0) }}]
            a["label"] = [[0.0, false, true], [-5.0, false, true], [7.0, false, true], [3.0, true, true], [0.0, false, false], [-2.0, false, true]].map {{ d.t_label($0[0] as! Double, $0[1] as! Bool, $0[2] as! Bool) }}
            d.shock = vs[2]; a["label2"] = [1.0, -1.0, 0.0].map {{ d.t_label($0, false, true) }}
            a["extRows"] = d.t_rows(ev.m, vs[1], max(1, ev.m.total))
            fd.append(a)
        }}
        r["fd"] = fd
        cs.append(r)
    }}
    o["cases"] = cs; o["fx"] = Market.shared.fx.last
    o["summary"] = (X["rankLists"] as! [[[Any]]]).map {{ ExternalView().t_summary($0) }}
    var pres: [[[Any]]] = []
    for ch in X["change1y"] as! [Any] {{
        MacroData.shared.file = MacroData.File(now: .init(date: nil, rate: nil, change_1y: ON(ch)), eps_last: nil, meals: nil)
        pres.append(FactorDetailView(factor: "rate").t_presets() + FactorDetailView(factor: "fx").t_presets() + [[N(MacroData.shared.change1y), MacroData.shared.defaultDr]])
    }}
    o["presets"] = pres
    return o
}}

func scenOut(_ r: RateScen?) -> Any {{
    guard let r else {{ return NSNull() }}
    return ["years": r.years, "yr": r.yr, "cum": r.cum, "dropYr": r.dropYr, "dropCum": r.dropCum, "dropCumYr": r.dropCumYr, "slope": r.slope, "rateMkt": r.mkt] as [String: Any]
}}
func runR(_ R: [String: Any]) -> [String: Any] {{
    var o: [String: Any] = [:]
    o["q"] = (R["q"] as! [[Any]]).map {{ q -> Double in RateScen.q(DS(q[0]), D(q[1])) }}
    o["band"] = (R["q"] as! [[Any]]).map {{ RateScen.band(DS($0[0])) }}
    var files: [Any] = []
    for f in R["files"] as! [[String: Any]] {{
        let coef = (f["coef"] as! [[[String]]]).map {{ $0.map {{ $0.map {{ Double($0)! }} }} }}
        let m = MacroData()
        m.file = MacroData.File(now: .init(date: nil, rate: nil, change_1y: ON(f["ch"])), eps_last: f["eps"] as? String, meals: f["noMeals"] != nil ? nil : MacroData.File.Meals(g_now: D(f["g"]!), coef: coef))
        var a: [String: Any] = ["dr": m.defaultDr, "ch": N(m.change1y)]
        a["scen"] = (R["calls"] as! [[String]]).map {{ scenOut(m.scen(dr: Double($0[0])!, m: Double($0[1])!)) }}
        files.append(a)
    }}
    o["files"] = files
    o["closed"] = (R["closed"] as! [[String: Any]]).map {{ RateScenarioView.t_out(DS($0["c"]!), D($0["p"]!), DS($0["s"]!)) }}
    o["timing"] = (R["timing"] as! [[Int]]).map {{ RateScenarioView.t_timing($0[0] < 0 ? nil : $0[0], $0[1] < 0 ? nil : $0[1]) }}
    o["ym"] = (R["ym"] as! [String]).map {{ [RateScenarioView.ym($0), RateWhyView.ymDot($0), DividendView.ym($0)] }}
    o["signed"] = DS(R["nums"]!).map {{ v in [RateScenarioView.signed(v, "%p", 1), RateScenarioView.signed(v, "%", 0), RateScenarioView.signed(v, "%", 2), RateWhyView().t_lv(v), DividendView.short(abs(v) * 1e4), DividendView.short(abs(v) * 1e3)] }}
    return o
}}
func runD(_ Dv: [String: Any]) -> [Any] {{
    (Dv["cases"] as! [[String: Any]]).map {{ c in
        let paid = (c["paid"] as! [[String: Any]]).map {{ p in DividendView.Paid(id: p["id"] as! String, name: "", out: SiteCalc.DivOut(byMonth: (p["by"] as! [String: String]).mapValues {{ Double($0)! }}), tax: 0, krw: false, payMonths: p["pm"] as! [Int]) }}
        return DividendView().t_month(months: c["months"] as! [String], byMonth: DS(c["by"]!), paid: paid)
    }}
}}
func runF(_ F: [String: Any]) -> [String: Any] {{
    ["ticks": (F["ticks"] as! [[String]]).map {{ fxTicks(lo: Double($0[0])!, hi: Double($0[1])!) }},
     "trend": (F["trend"] as! [Any]).map {{ trendWord($0 as? String) }},
     "vol": DS(F["vol"]!).map {{ [volWord($0), GlanceView.swing($0), GlanceView.swingName[GlanceView.swing($0)]] as [Any] }}]
}}
'''


def path_cases():
    """내 길 입력: 회복·목표(플러스)·처음·샘플, 시작일 앞뒤 거래일, 국내 주식 들어온 날, 빈 기록, 목표보다 큰 지금 값"""
    import math, random
    from datetime import date, timedelta, datetime, timezone
    rnd = random.Random(7)
    def series(d0, n, v0, drift):
        out, v, d = [], v0, d0
        for i in range(n):
            d = d + timedelta(days=1 if d.weekday() < 4 else 3)
            v = v * (1 + drift + 0.012 * math.sin(i * 0.7) + rnd.uniform(-0.01, 0.01))
            out.append([d.isoformat(), round(v, 6) if i % 3 else v])
        return out
    def ks(d):   # 서울 자정 (Day.str 이 서울 날짜로 바꾼다)
        return datetime(d.year, d.month, d.day, 9, 30, tzinfo=timezone.utc).timestamp() - 9 * 3600
    today = "2026-10-21"
    cases = []
    tot = [1.2e7 * (1 + 0.4 * i / 36 + 0.05 * math.sin(i)) for i in range(37)]
    base = dict(isGoal=False, goalSpan=1.0, gM=0.0, K=0.0, now=0.0, route="recover", isSample=False, krSum=0.0, S=[], KL=[], tot=tot, today=today)
    # 회복: 본전 1,500만, 지금 1,380만, 넉 달 전 시작
    s0 = date(2026, 6, 15)
    cases.append({**base, "start": ks(s0), "K": 1.5e7, "now": 1.38e7, "S": series(s0 - timedelta(days=9), 90, 1.2e7, 0.0006)})
    # 회복: 시작일이 거래일이 아님 (주말) · 시작 뒤 거래일 없음
    cases.append({**base, "start": ks(date(2026, 10, 18)), "K": 2e7, "now": 1.9e7, "S": series(date(2026, 10, 8), 6, 1.85e7, 0.001)})
    # 회복: 지난 기록 없음
    cases.append({**base, "start": ks(date(2026, 9, 1)), "K": 1e7, "now": 9.1e6, "S": []})
    # 목표(플러스): 1억, 월 50만, 5년 목표, 국내 주식이 시작 뒤에 들어옴
    s1 = date(2026, 3, 2)
    cases.append({**base, "isGoal": True, "goalSpan": 5.0, "gM": 50.0, "K": 1e8, "now": 3.4e7, "route": "plus", "krSum": 4.2e6,
                  "start": ks(s1), "S": series(s1 - timedelta(days=9), 160, 2.8e7, 0.0008), "KL": [["2026-05-11", 4.0e6], ["2026-07-01", 4.2e6]]})
    # 목표: 국내 주식이 시작 전부터 있음 (들어온 돈 아님) · 지금 값이 목표 길보다 큼 · kAdj <= 0
    cases.append({**base, "isGoal": True, "goalSpan": 2.0, "gM": 500.0, "K": 1e8, "now": 6e7, "route": "plus", "krSum": 1e6,
                  "start": ks(s1), "S": series(s1 - timedelta(days=5), 150, 5e7, 0.0005), "KL": [["2026-01-05", 1e6]]})
    # 처음(모으는 중): 월 30만, 목표 3천만
    cases.append({**base, "isGoal": True, "goalSpan": 3.0, "gM": 30.0, "K": 3e7, "now": 2.1e6, "route": "novice", "start": ks(date(2026, 4, 20))})
    # 샘플: 3년 흐름에서 어림
    cases.append({**base, "isSample": True, "start": ks(date(2026, 8, 3)), "K": 1.6e7, "now": 1.52e7})
    # 오늘 시작 (하루로 둔다)
    cases.append({**base, "start": ks(date(2026, 10, 21)), "K": 1e7, "now": 1.01e7, "S": series(date(2026, 10, 9), 8, 1e7, 0.0)})
    for c in cases:
        c["ts"] = [repr(x) for x in [0.0, 1 / 365.25, 0.05, 0.1, 0.25, 0.3333, 0.5, 0.9, 1.0, 2.5, 4.99]]
    return cases


def x_inputs():
    """G2 나머지 입력: 외부 요인(지난 종가 회귀·요인 민감도·순위 글), 요인 상세, 금리 식단, 배당 달 막대, 환율 눈금·말"""
    import math, random
    from datetime import date, timedelta
    rnd = random.Random(11)
    R = lambda x: repr(float(x))
    # 지난 종가: SPY 는 평일 (미국 휴장 몇 날 뺌), 한국 종목은 한국 휴장을 빼고 미국 휴장 날은 넣는다
    days, d = [], date(2025, 1, 2)
    while len(days) < 300:
        if d.weekday() < 5: days.append(d.isoformat())
        d += timedelta(days=1)
    us_off = {days[i] for i in (17, 40, 41, 99, 150, 151, 152, 230)}
    kr_off = {days[i] for i in (5, 6, 60, 61, 62, 181, 182, 270)}
    us = [x for x in days if x not in us_off]
    spy, v = [], 500.0
    mret = {}
    for x in us:
        r = rnd.gauss(0.0004, 0.011); v *= math.exp(r); spy.append(v); mret[x] = r
    def stock(dates, beta, v0, noise, lag=False):
        out, v, prev = [], v0, None
        for x in dates:
            r = beta * (mret.get(prev if lag else x, 0.0) if (prev or not lag) else 0.0) + rnd.gauss(0, noise)
            v *= math.exp(r); out.append(round(v, 2) if rnd.random() < 0.5 else v); prev = x
        return out
    kr_dates = [x for x in days if x not in kr_off]
    hist = {
        "SPY": {"dates": us, "closes": [R(c) for c in spy]},
        "AAPL": {"dates": us, "closes": [R(c) for c in stock(us, 1.2, 220, 0.012)]},
        "NVDA": {"dates": us[30:], "closes": [R(c) for c in stock(us[30:], 1.9, 120, 0.025)]},
        "005930": {"dates": kr_dates, "closes": [R(c) for c in stock(kr_dates, 0.6, 70000, 0.014, lag=True)]},
        "000660": {"dates": kr_dates[-25:], "closes": [R(c) for c in stock(kr_dates[-25:], 1.1, 200000, 0.02, lag=True)]},
        "X1": {"dates": us[-120:], "closes": [R(c) for c in stock(us[-120:], -0.4, 33.3, 0.009)]},
        "Z1": {"dates": us[-20:], "closes": [R(c) for c in ([0.0 if i == 7 else 10 + i * 0.1 for i in range(20)])]},
    }
    syms = [["SPY", "S&P500 ETF", "usd"], ["AAPL", "애플", "usd"], ["NVDA", "엔비디아", "usd"], ["MSFT", "마이크로소프트", "usd"],
            ["005930", "삼성전자", "krw"], ["000660", "SK하이닉스", "krw"], ["X1", "내 종목", "usd"], ["Z1", "영 종목", "usd"], ["K9", "국내 직접", "krw"]]
    ids = [x[0] for x in syms]
    shocks = {"fx": [R(x) for x in (-300, -138, 0, 50.5, 276)], "mkt": [R(x) for x in (-25, -8, -2, 0, 8)],
              "rate": [R(x) for x in (-1.5, -0.5, 0, 0.5, 2.5)], "etc": [R(x) for x in (-30, -10, 0, 10, 30)]}
    texts = [R(x) for x in (0.25, 1e-5, 0.1 + 0.2, -1234567.5, 2.675, 0.04, 12.5, -0.0001, 1234.125, 999999.5, 2.5e-05, 0.15)]   # %g 유효 6자리 딱 반 (1234.125) 포함
    full = {"mkt": [R(0.045), R(0.19)], "fx": [R(0.025), R(0.08)], "rate": [R(28.5), R(80)], "oil": [R(0.09), R(0.35)], "gold": [R(0.05), R(0.12)]}
    cases = [
        # 사이트 민감도 일부 + 회귀, 여러 종목
        {"betas": {"mkt": {"AAPL": R(1.15), "MSFT": R(0.93)}, "rate": {"AAPL": R(-0.0011), "005930": R(0.0004), "X1": R(0.002)},
                   "oil": {"AAPL": R(0.03), "NVDA": R(-0.12), "005930": R(0.08)}, "gold": {"X1": R(0.2)}, "cmdty": {"MSFT": R(0.05)}},
         "stat": {"mkt": {"AAPL": R(0.8), "MSFT": R(0.72)}, "rate": {"AAPL": R(0.62), "005930": R(0.52)}, "oil": {"NVDA": R(0.4)}},
         "factors": full, "rows": [["AAPL", R(5.2e6)], ["005930", R(3.1e6)], ["X1", R(2e6)], ["NVDA", R(1.4e6)], ["MSFT", R(8e5)], ["K9", R(1e6)]]},
        # 종목 없음
        {"betas": {}, "stat": {}, "factors": full, "rows": []},
        # 한국 종목만 · 금 자료 없음 · 유가 sd 0
        {"betas": {"oil": {"005930": R(0.05)}}, "stat": {}, "factors": {"mkt": full["mkt"], "fx": full["fx"], "rate": full["rate"], "oil": [R(0), R(0.3)]},
         "rows": [["005930", R(4e7)], ["000660", R(9e6)]]},
        # 아주 작은 평가액 (0.1% 기준 아래) · 같은 방향 비율이 모두 반반
        {"betas": {"mkt": {"AAPL": R(2.4)}, "oil": {"AAPL": R(0.5)}}, "stat": {"mkt": {"AAPL": R(0.52)}, "oil": {"AAPL": R(0.47)}},
         "factors": full, "rows": [["AAPL", R(100)]]},
        # 서로 상쇄 (유가 민감도 반대) · 시장보다 작게
        {"betas": {"mkt": {"AAPL": R(0.5), "NVDA": R(0.6)}, "oil": {"AAPL": R(0.4), "NVDA": R(-0.38)}, "gold": {"AAPL": R(0.1), "NVDA": R(0.12)}},
         "stat": {"mkt": {"AAPL": R(0.75)}, "oil": {"AAPL": R(0.7)}, "gold": {"NVDA": R(0.66)}}, "factors": full, "rows": [["AAPL", R(6e7)], ["NVDA", R(5.5e7)], ["Z1", R(3e6)]]},
        # 시장보다 크게 (시장의 1.25배: %.1f 딱 반 → C 는 1.2)
        {"betas": {"mkt": {"AAPL": R(1.25)}}, "stat": {"mkt": {"AAPL": R(0.8)}}, "factors": full, "rows": [["AAPL", R(1e7)]]},
        # 시장의 1.9배 근처 · 달러 비중 낮음
        {"betas": {"mkt": {"NVDA": R(2.1), "AAPL": R(1.6)}, "rate": {"NVDA": R(-0.003)}}, "stat": {"mkt": {"NVDA": R(0.8)}, "rate": {"NVDA": R(0.9)}},
         "factors": full, "rows": [["NVDA", R(4e6)], ["AAPL", R(1e6)], ["005930", R(2e5)]]},
    ]
    rank_lists = [[], [["mkt", R(1e5), False]], [["mkt", R(9e5), False], ["fx", R(1e5), False], ["rate", R(1e5), False]],
                  [["fx", R(5e5), False], ["mkt", R(4e5), False]], [["oil", R(3e5), False], ["gold", R(2e5), False], ["mkt", R(2e5), False]],
                  [["rate", R(0), False], ["mkt", R(1), False]], [["gold", R(1e5), True], ["oil", R(5e4), False]]]
    ext = {"fx": R(1380.0), "hist": hist, "syms": syms, "sample": ["SPY", "AAPL", "NVDA", "MSFT", "005930", "000660"], "ids": ids,
           "shocks": shocks, "texts": texts, "cases": cases, "rankLists": rank_lists, "change1y": [None, R(0.0), R(0.35), R(-0.04), R(1.26), R(-0.25)]}

    # 금리 식단: 식단 60벌 × 3년, 짧은 식단·칸 모자란 줄 섞음
    def meals(n, H, seed):
        g = random.Random(seed); out = []
        for j in range(n):
            out.append([[R(g.gauss(1.5, 4)), R(g.gauss(-2.2, 1.5)), R(g.gauss(0.3, 0.2))] for _ in range(H)])
        out[3] = out[3][:2]                       # 칸 모자란 식단 (빠진다)
        out[5][1] = out[5][1][:2]                 # 숫자 모자란 줄 (그 줄만 빠지고 식단도 빠진다)
        return out
    files = [{"ch": R(0.35), "g": R(0.021), "coef": meals(60, 3, 1), "eps": "2026-06"},
             {"ch": R(-0.86), "g": R(-0.004), "coef": meals(25, 3, 2)},
             {"ch": None, "g": R(0.0), "coef": []},
             {"ch": R(0.125), "g": R(0.01), "coef": [[[R(1), R(-2), R(0.5)]]]},
             {"ch": R(2.0), "noMeals": True, "coef": []}]
    calls = [[R(a), R(b)] for a, b in ((0.125, 0), (0.13, 0), (0.5, 0.01), (-0.5, 0), (2.5, 0.0005), (0, 0), (0.0049, 0), (0.005, 0), (0.375, 0.0125), (0.38, 0.0125), (-1.5, -0.02))]
    qs = [[[], R(0.5)], [[R(3)], R(0.1)], [[R(x) for x in sorted([1.5, -2, 7, 0.25, 3])], R(0.1)], [[R(x) for x in sorted([1.5, -2, 7, 0.25, 3])], R(0.9)],
          [[R(x) for x in sorted(rnd.gauss(0, 5) for _ in range(101))], R(0.5)], [[R(1), R(2)], R(1.0)], [[R(1), R(2)], R(0.0)]]
    closed = [{"c": [R(x) for x in c], "p": R(p), "s": [R(x) for x in (0, -0.02, -0.005, 0.004, 0.03, -0.3)]} for c, p in (
        ((3.0, 0.18, 0.07, 3.3e7, 1e8, 5e5), 0.42), ((1 / 12, 0.01, 0.0, 1e7, 1e7, 0), 0.9), ((5.0, 0.25, 0.09, 2e7, 1e8, 0), 0.05),
        ((2.0, 0.2, 0.06, 1.2e8, 1e8, 1e6), 0.97), ((3.0, 0.15, 0.05, 1e7, 0, 3e5), 0.3), ((10.0, 0.4, -0.2, 1e6, 1e9, 1e4), 0.0),
        ((0.5, 0.3, 0.12, -5e6, 1e7, 1e5), 1.0))]
    timing = [[-1, -1], [5, -1], [-1, 7], [0, 30], [20, 20], [20, 32], [20, 33], [33, 20], [40, 39], [1, 145], [145, 1], [12, 24]]
    ym = ["2029-10-01", "2026-06", "2026-6", "2026", "", "abc-xy", "2026--06", "2031-07-15", "-2026-03", "2026-+3", "2026-1x"]
    nums = [R(x) for x in (0, 0.25, -0.25, 0.05, 0.15, 1.25, 0.35, 12.345, -0.04, 2.675, 99.95, 1e-9, -3.5, 0.45, 1.05, 0.0125, 10.25)]
    rate = {"files": files, "calls": calls, "q": qs, "closed": closed, "timing": timing, "ym": ym, "nums": nums}

    # 배당 달 막대
    def months_from(y, m):
        out = []
        for i in range(12):
            mm = (m - 1 + i) % 12 + 1; yy = y + (m - 1 + i) // 12
            out.append(f"{yy}-{mm:02d}")
        return out
    M1 = months_from(2026, 11)
    def by(vals): return [R(v) for v in vals]
    dv = {"cases": [
        {"months": M1, "by": by([0, 12500, 0, 0, 98000, 0, 0, 12500, 0, 0, 14000, 3000]),
         "paid": [{"id": "AAPL", "by": {M1[1]: R(12500), M1[7]: R(12500), M1[4]: R(3000)}, "pm": [12, 6, 3]},
                  {"id": "005930", "by": {M1[4]: R(95000), M1[10]: R(14000)}, "pm": [3, 9]},
                  {"id": "O", "by": {m: R(250) for m in M1}, "pm": list(range(1, 13))}]},
        {"months": M1, "by": by([5000] * 12), "paid": [{"id": "O", "by": {m: R(5000) for m in M1}, "pm": list(range(1, 13))}]},
        {"months": M1, "by": by([0] * 12), "paid": []},
        {"months": months_from(2026, 1), "by": by([1e5, 0, 0, 4e5, 0, 0, 3.9e5, 0, 0, 1e5, 0, 9999.5]),
         "paid": [{"id": "A", "by": {"2026-04": R(4e5)}, "pm": [4]}, {"id": "B", "by": {"2026-07": R(3.9e5)}, "pm": [7]}, {"id": "C", "by": {"2026-01": R(1e5)}, "pm": [1, 10]}]},
        {"months": M1, "by": by([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 1e6]), "paid": [{"id": "M", "by": {m: R(1) for m in M1}, "pm": list(range(1, 13))}]},
    ]}
    fxg = {"ticks": [[R(a), R(b)] for a, b in ((1300, 1450), (1380, 1380), (0, 0), (-50, 30), (1000, 3000), (1234.5, 1236), (1391.2, 1402.7), (1290, 1510), (1100, 1700))],
           "trend": ["상승 추세", "약한 상승", "하락 추세", "약세 전환 주의", "횡보", "", None],
           "vol": [R(x) for x in (0, 0.069999, 0.07, 0.12, 0.120001, 0.19999, 0.2, 0.39, 0.4, 1.5, -0.1)]}
    return {"ext": ext, "rate": rate, "div": dv, "fxg": fxg}


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    MP = mp_src() + x_src()
    src = f'''import Foundation
{MP}
// 꺼낸 원본 (tools/num_parity/g/gsrc.py 가 만든다. 고치지 말 것)
enum Lens {{ case base, mine, smooth }}
enum AppModel {{
    static let en = false
    static func krwC(_ x: Double) -> String {{ "" }}
{find("Data/AppModel.swift", "static func man(_ krw: Double)", 8)}
{lines("Views/AnalysisDetail.swift", 88, 98)}
{find("Data/AppModel.swift", "static func pct(_ p: Double)", 1)}
{find("Data/AppModel.swift", "static func normCDF(_ x: Double)", 5)}
    struct Forecast {{ var pGoal: Double }}
}}
enum ForecastView {{
{lines("Views/AnalysisView.swift", 507, 519)}
}}
enum AnalysisView {{
{lines("Views/AnalysisView.swift", 99, 103)}
}}
enum ScenarioChart {{
{lines("Views/ForecastParts.swift", 17, 28)}
}}

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let inp = try! JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("input.json"))) as! [String: Any]
var out: [String: Any] = [:]
let won = inp["won"] as! [Double], ps = inp["p"] as! [Double]
out["amt"] = won.map {{ ForecastView.amt($0) }}
out["short"] = [Lens.base, .mine, .smooth].map {{ ForecastView.short($0) }}
out["ym"] = (inp["dates"] as! [String]).map {{ let x = ForecastView.ym($0); return [x.long, x.short] }}
out["freq"] = ps.map {{ AnalysisView.freq($0) }}
out["step"] = won.map {{ ScenarioChart.step(abs($0)) }}
out["axis"] = won.map {{ ScenarioChart.axis($0) }}
out["manS"] = won.map {{ AppModel.manS($0) }}
out["won1"] = won.map {{ AppModel.won1($0) }}
out["wonRead"] = won.map {{ AppModel.wonRead($0) }}
out["myPath"] = (inp["path"] as! [[String: Any]]).map {{ runPath($0) }}
out["ext"] = runX(inp["ext"] as! [String: Any])
out["rate"] = runR(inp["rate"] as! [String: Any])
out["div"] = runD(inp["div"] as! [String: Any])
out["fxg"] = runF(inp["fxg"] as! [String: Any])
let d = try! JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try! d.write(to: dir.appendingPathComponent("app.json"))
print("num_parity g: 금액 \\(won.count) · 확률 \\(ps.count) · 내 길 \\((inp["path"] as! [Any]).count)")
'''
    (out / "main.swift").write_text(src)
    inp = {
        "won": [0, 1, 4999, 5000, 9999.5, 12345, 46000, 46499, 99500, 1e5, 3.3e7, 9.99e7, 1e8, 1.5e8, 2.5e8, 3.30e8, 9.99e8, 1e9, 1.15e9, 2.6e9, 1.2345e10, -5e7, -3.3e8, 5e7, 2.5e8 + 1],
        "p": [0, 0.01, 0.05, 0.25, 0.26, 0.28, 0.3, 0.33, 0.36, 0.5, 0.52, 0.6, 0.66, 0.7, 0.75, 0.77, 0.9, 0.94, 0.96, 1],
        "dates": ["2029-10-14", "2026-01-05", "2030-12-31", "bad", "2031-7"],
        "path": path_cases(),
        **x_inputs(),
    }
    (out / "input.json").write_text(json.dumps(inp))


if __name__ == "__main__":
    main(sys.argv[1])
