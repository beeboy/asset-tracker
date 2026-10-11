import SwiftUI

// 분석 탭 상세 화면 (시안 31~35판): 내 길 · 금리 시나리오 · 외부 요인 · 배당·세금 · 환율 영향 · 종목 한눈에. (인사이트는 앱에서 뺐다)
// 숫자는 모두 계산값이고 사거나 팔라는 문구는 넣지 않는다. 반응 크기·배당은 시안용 예시 값.

// MARK: 공통

extension AppModel {
    // 내 길: 회복 = 본전을 1년 안에(적립 없음), 목표 = 목표 금액·목표일·매달 적립. 시작은 3개월 전(시안 가정)
    struct MyPath { let start: Double; let end: Double; let key: Double; let monthly: Double; let act: (Double) -> Double; let need: (Double) -> Double }
    var myPath: MyPath {
        let mS = 0.25, mEnd = isGoal ? Double(gY) : 1, mM = isGoal ? gM * 1e4 : 0, K = keyValue
        let now = trackValue
        let act: (Double) -> Double = route == .novice
            ? { t in max(0, now - mM * 12 * max(0, mS - t)) }
            : { t in self.totalAt(1 - (mS - t) / 3) }
        let vS = act(0), kAdj = K - mM * 12 * mEnd
        let need: (Double) -> Double = { t in mM * 12 * t + (kAdj > 0 && vS > 0 ? vS * pow(kAdj / vS, t / mEnd) : vS + (K - vS) * t / mEnd) }
        return MyPath(start: mS, end: mEnd, key: K, monthly: mM, act: act, need: need)
    }
    var myPathGapToday: Double { let p = myPath; let n = p.need(p.start); return n > 0 ? p.act(p.start) / n - 1 : 0 }

    static func manS(_ krw: Double) -> String { (krw >= 0 ? "+" : "−") + man(abs(krw)) }
    static func won1(_ krw: Double) -> String { krw > 0 && krw < 1e4 ? Int(krw.rounded()).formatted() + "원" : man(krw) }
    /// x 시점(0 = 3년 전, 1 = 어제)의 전체 평가액 (원화)
    func totalAt(_ x: Double) -> Double {
        rows.reduce(0) { $0 + krw($1.sym, $1.h.qty * prices.price($1.sym, at: x)) }
    }
    func weight(_ id: String) -> Double { rows.first { $0.id == id }.map { $0.value / max(1, total) } ?? 0 }
}

/// 상세 화면 머리: 제목은 위 막대 가운데(뒤로 가기 옆)에 두고, 여기에는 한 줄 설명만
struct DetailHead: View {
    let title: String
    let sub: String
    var body: some View {
        // 설명이 없으면 빈 Group 이라 제목이 막대에 안 붙었다: 높이 0 인 뷰라도 둔다
        Text(sub).appFont(14).foregroundStyle(Theme.sub)
            .frame(maxWidth: .infinity, maxHeight: sub.isEmpty ? 0 : nil, alignment: .leading)
            .opacity(sub.isEmpty ? 0 : 1)
            .navigationTitle(title)
    }
}

// 가운데 0 을 기준으로 왼쪽(음수)·오른쪽(양수)으로 뻗는 막대
private struct SignedBar: View {
    let label: String
    let value: String
    let g: Double
    let scale: Double
    var pos: Color = Theme.up
    var neg: Color = Theme.down
    var valueColor: Color? = nil
    var body: some View {
        HStack(spacing: 8) {
            Text(label).appFont(13, .semibold).frame(minWidth: 52, alignment: .leading).lineLimit(1)
            GeometryReader { geo in
                let half = geo.size.width / 2, w = min(1, abs(g) / max(1e-9, scale)) * half
                ZStack(alignment: .leading) {
                    Rectangle().fill(Theme.line).frame(width: 1).offset(x: half)
                    if g < 0 { RoundedRectangle(cornerRadius: 3).fill(neg).frame(width: w, height: 12).offset(x: half - w) }
                    if g > 0 { RoundedRectangle(cornerRadius: 3).fill(pos).frame(width: w, height: 12).offset(x: half) }
                }
                .frame(maxHeight: .infinity)
            }
            .frame(height: 16)
            Text(value).appFont(13, .bold).foregroundStyle(valueColor ?? (g >= 0 ? pos : neg)).monospacedDigit()
                .frame(minWidth: 76, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(value)")
    }
}

private struct Kpi: View {
    let k: String, v: String, sub: String
    var color: Color = Theme.ink
    var dark = false
    func darkened(_ on: Bool) -> Kpi { var k = self; k.dark = on; return k }
    var body: some View {
        let box = VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v).appFont(18, .bold).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.7)
            Text(sub).appFont(11).foregroundStyle(Theme.muted)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        if dark {
            box.darkBox(14)
        } else {
            box.background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
        }
    }
}

private func kpiGrid(_ items: [Kpi], dark: Bool = false) -> some View {
    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
        ForEach(items.indices, id: \.self) { items[$0].darkened(dark) }
    }
}

private func footnote(_ t: String) -> some View {
    Text(t).appFont(12).foregroundStyle(Theme.muted).lineSpacing(2).frame(maxWidth: .infinity, alignment: .leading)
}

private func pinnedBox<C: View>(@ViewBuilder _ c: () -> C) -> some View {
    VStack(alignment: .leading, spacing: 10) { c() }
        .fixedSize(horizontal: false, vertical: true)
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card).overlay(alignment: .bottom) { Divider() }
}

// 0~1 정규 좌표 선 그리기
private func linePath(_ pts: [(Double, Double)], in size: CGSize) -> Path {
    Path { p in
        for (i, q) in pts.enumerated() {
            let pt = CGPoint(x: q.0 * size.width, y: q.1 * size.height)
            i == 0 ? p.move(to: pt) : p.addLine(to: pt)
        }
    }
}

// MARK: 내 길 — 정한 길(보라 점선) vs 실제

struct MyPathView: View {
    @Environment(AppModel.self) private var m
    @State private var view = "all"

    var body: some View {
        let p = m.myPath, mS = p.start, mEnd = p.end, K = p.key, act = p.act, need = p.need
        let gap = m.myPathGapToday
        let f = m.forecast
        let x1 = view == "past" ? mS + 1.0 / 12 : mEnd
        let actPts = (0...30).map { i -> (Double, Double) in let t = mS * Double(i) / 30; return (t, act(t)) }
        let needPts = (0...60).map { i -> (Double, Double) in let t = x1 * Double(i) / 60; return (t, need(t)) }
        let bandPts: [(Double, Double, Double)] = view == "band"
            // 3년 전망(달마다 36칸)을 목표일까지 겹친다. 목표일이 3년보다 멀면 전망이 있는 3년까지만
            ? (0...min(f.q75.count - 1, max(1, Int(((x1 - mS) * 12).rounded())))).map { i in let t = mS + Double(i) / 12; return (t, f.q75[i], f.q25[i]) } : []
        let vals = actPts.map(\.1) + needPts.map(\.1) + bandPts.flatMap { [$0.1, $0.2] }
        let hi = (vals.max() ?? 1) * 1.03, lo = (vals.min() ?? 0) * 0.97
        let X = { (t: Double) in t / x1 }, Y = { (v: Double) in (hi - v) / max(1, hi - lo) }
        let yrsLeft = mEnd - mS
        let now = m.trackValue, mM = p.monthly
        let needCagr = now > 0 && yrsLeft > 0 ? pow(max(1, K - mM * 12 * yrsLeft) / now, 1 / yrsLeft) - 1 : 0
        let pPlan = f.prob(min(3, yrsLeft))
        let lead = gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060)

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "내 길", sub: m.isGoal ? "목표 \(AppModel.wonK(m.gK)) · \(m.gY)년 · 매달 \(AppModel.wonK(m.gM)) (7월 9일 시작)" : "본전 \(AppModel.man(K)) · 1년 안에 (7월 9일 시작)")
                HStack(alignment: .firstTextBaseline) {
                    Text("내 길보다 \(String(format: "%.1f", abs(gap) * 100))% \(gap >= 0 ? "앞섬" : "뒤처짐")").appFont(22, .bold).foregroundStyle(lead)
                    Spacer()
                    Text("오늘 내 길 \(AppModel.man(need(mS)))").appFont(12).foregroundStyle(Theme.sub)
                }
                Canvas { ctx, size in
                    if !bandPts.isEmpty {
                        var band = linePath(bandPts.map { (X($0.0), Y($0.1)) }, in: size)
                        for q in bandPts.reversed() { band.addLine(to: CGPoint(x: X(q.0) * size.width, y: Y(q.2) * size.height)) }
                        band.closeSubpath()
                        ctx.fill(band, with: .color(Theme.teal.opacity(0.14)))
                    }
                    let tx = X(mS) * size.width
                    ctx.stroke(Path { $0.move(to: CGPoint(x: tx, y: 0)); $0.addLine(to: CGPoint(x: tx, y: size.height)) },
                               with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                    ctx.stroke(linePath(needPts.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.purple), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                    ctx.stroke(linePath(actPts.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.teal), style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
                    let ty = Y(actPts.last!.1) * size.height
                    ctx.fill(Path(ellipseIn: CGRect(x: tx - 4, y: ty - 4, width: 8, height: 8)), with: .color(Theme.teal))
                }
                .frame(height: 140)
                // 범례와 예보 겹치기 확률은 그래프 안에 작게
                .overlay(alignment: .topLeading) {
                    VStack(alignment: .leading, spacing: 2) {
                        legend(Theme.teal, "실제"); legend(Theme.purple, "내 길")
                        if view == "band" { legend(Theme.teal.opacity(0.3), "3년 전망 절반" + (mEnd - mS > 3.05 ? " (3년까지)" : "")) }
                    }
                    .padding(6).background(Theme.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
                }
                .overlay(alignment: .bottomTrailing) {
                    if view == "band" {
                        Text("넘을 확률 \(AppModel.pct(pPlan))").appFont(12, .bold).foregroundStyle(Theme.purple)
                            .padding(.horizontal, 8).padding(.vertical, 4).background(Theme.card.opacity(0.85), in: Capsule())
                            .padding(.bottom, 4)
                    }
                }
                .accessibilityLabel("내 길과 실제 평가액 그래프")
                HStack { Text("시작 7월"); Spacer(); Text(view == "past" ? "다음 달" : m.isGoal ? "\(m.gY)년 뒤 목표일" : "1년 뒤") }.appFont(11).foregroundStyle(Theme.muted)
                ChipRow(items: [("past", "시작부터"), ("all", "목표일까지"), ("band", "예보 겹치기")], selection: $view, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                Card {
                    Text("달마다 내 길보다 앞섰나").appFont(15, .bold)
                    ForEach(1...3, id: \.self) { k in
                        let t = Double(k) / 12, g = need(t) > 0 ? act(t) / need(t) - 1 : 0
                        SignedBar(label: k == 3 ? "오늘" : "\(k)달째", value: AppModel.sgn(g), g: g, scale: 0.2, pos: Theme.green, neg: Theme.orange,
                                  valueColor: g >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060))
                    }
                }
                kpiGrid([
                    Kpi(k: "목표일까지", v: (yrsLeft >= 1 ? "\(Int(yrsLeft))년 " : "") + "\(Int((yrsLeft.truncatingRemainder(dividingBy: 1) * 12).rounded()))개월", sub: m.isGoal ? "\(m.gY)년 목표" : "1년 목표"),
                    Kpi(k: "필요 연수익률", v: AppModel.sgn(needCagr), sub: m.isGoal ? "적립 포함, 남은 기간" : "본전까지 남은 기간"),
                    Kpi(k: "이번 달 넣을 돈", v: m.isGoal ? AppModel.wonK(m.gM) : "없음", sub: m.isGoal ? "내 길에 들어 있음" : "회복은 적립 없이"),
                    Kpi(k: "내 길대로 갈 확률", v: AppModel.pct(pPlan), sub: "3년 전망 (\(m.lens.label))"),
                ], dark: true)
                footnote("예측이 아닌 \"만약\" 도구. 정한 목표와 적립으로 길을 긋고 실제 평가액과 견줘요. 확률은 3년 전망(촐레스키 분해·몬테카를로)으로 계산해요. 종목 추천이 아닙니다.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private func legend(_ c: Color, _ t: String) -> some View {
    HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12); Text(t) }
        .appFont(12).foregroundStyle(Theme.sub)
}

// MARK: 외부 요인 — 시장·금리·환율이 움직이면

struct ExternalView: View {
    @Environment(AppModel.self) private var m
    @State private var factor = "mkt"
    @State private var mkt = -10.0
    @State private var rate = 0.5
    @State private var fx = -100.0

    // [S&P500 1% 움직일 때 %, 미국 10년 금리 +1%p 일 때 %, 달러 노출] (시안용 가정값)
    static let beta: [String: (Double, Double, Double)] = [
        "DRNK": (2.0, -8, 1), "QQQ": (1.15, -6, 1), "AAPL": (1.2, -4, 1), "NVDA": (1.8, -7, 1), "MSFT": (1.1, -4, 1), "SPY": (1, -3, 1),
        "005930": (0.6, -2, 0), "000660": (0.9, -3, 0), "035720": (0.7, -3, 0), "069500": (0.6, -2, 0), "360750": (1, -3, 1),
    ]

    /// 표에 없는 종목(직접 추가한 종목): 시장 반응은 지난 3년 일별 종가로 S&P500(SPY)에 대해 계산하고,
    /// 금리 반응은 평균값 -3%로 두고, 환율은 미국 종목이면 그대로 받는다
    static func coef(_ s: Symbol) -> (Double, Double, Double) {
        if let b = beta[s.id] { return b }
        return (estBeta(s.id) ?? 1, -3, s.currency == .usd ? 1 : 0)
    }
    static func estBeta(_ id: String) -> Double? {
        guard let h = PriceHistory.of(id), let spy = PriceHistory.of("SPY") else { return nil }
        let sp = Dictionary(zip(spy.dates, spy.closes), uniquingKeysWith: { a, _ in a })
        // 한국 장은 미국 장보다 먼저 끝나서, 한국 종목은 그 전날 밤 미국 장 움직임과 짝을 짓는다
        let kr = Sample.symbol(id)?.currency == .krw
        let usDays = spy.dates
        var spRet: [String: Double] = [:]
        for i in 1..<max(1, spy.closes.count) where spy.closes[i - 1] > 0 {
            let r: Double = log(spy.closes[i] / spy.closes[i - 1])
            spRet[usDays[i]] = r
        }
        func prevUS(_ d: String) -> String? {
            var lo = 0, hi = usDays.count - 1, ans: String? = nil
            while lo <= hi { let mid = (lo + hi) / 2; if usDays[mid] < d { ans = usDays[mid]; lo = mid + 1 } else { hi = mid - 1 } }
            return ans
        }
        var xs: [Double] = [], ys: [Double] = []
        for i in 1..<h.closes.count {
            guard h.closes[i - 1] > 0, let key = kr ? prevUS(h.dates[i]) : h.dates[i], let x = spRet[key] else { continue }
            if kr, let pk = prevUS(h.dates[i - 1]), pk == key { continue }     // 미국 장이 쉰 날은 건너뛴다
            xs.append(x); ys.append(log(h.closes[i] / h.closes[i - 1]))
        }
        _ = sp
        guard xs.count > 120 else { return nil }
        let mx = xs.reduce(0, +) / Double(xs.count), my = ys.reduce(0, +) / Double(ys.count)
        let cov = zip(xs, ys).reduce(0) { $0 + ($1.0 - mx) * ($1.1 - my) }, vx = xs.reduce(0) { $0 + ($1 - mx) * ($1 - mx) }
        return vx > 0 ? (cov / vx * 20).rounded() / 20 : nil       // 0.05 단위
    }

    var body: some View {
        let v = factor == "mkt" ? mkt : factor == "rate" ? rate : fx
        let eff = { (id: String) -> Double in
            let b = Sample.symbol(id).map(Self.coef) ?? (1, -3, 0)
            return factor == "mkt" ? b.0 * v / 100 : factor == "rate" ? b.1 * v / 100 : b.2 * v / Market.shared.fx.last
        }
        let list = m.rows.map { (id: $0.id, label: $0.sym.short, dv: $0.value * eff($0.id)) }
        let tot = list.reduce(0) { $0 + $1.dv }, maxAbs = max(1, list.map { abs($0.dv) }.max() ?? 1)
        let after = m.total + tot
        let ask = factor == "mkt" ? "S&P500이 얼마나 움직이면" : factor == "rate" ? "미국 10년 금리가 얼마나 바뀌면" : "환율이 \(Int(Market.shared.fx.last).formatted())원에서 얼마나 바뀌면"
        let vText = factor == "mkt" ? (v > 0 ? "+" : "") + "\(Int(v))%" : factor == "rate" ? (v > 0 ? "+" : "") + String(format: "%g", v) + "%p" : (v > 0 ? "+" : "") + "\(Int(v))원"
        let coef = m.rows.map { r -> String in
            let b = Self.coef(r.sym)
            return r.sym.short + (factor == "mkt" ? " \(String(format: "%g", b.0))배" : factor == "rate" ? " \(String(format: "%g", b.1))%" : (b.2 > 0 ? " 환율만큼" : " 영향 없음"))
        }.joined(separator: ", ")
        let note = factor == "mkt" ? "1% 움직일 때 평균 반응: \(coef). 시장보다 등락이 큰 종목이 많을수록 하락 시 손실이 증폭돼요."
            : factor == "rate" ? "미국 10년 금리가 1%p 오를 때 평균 반응: \(coef). 성장주일수록 금리에 더 민감했어요."
            : "원화로 환산하면: \(coef). 미국 종목은 주가가 그대로여도 환율이 \(Int(abs(v)))원 \(v >= 0 ? "오르면" : "내리면") 원화 평가액이 \(String(format: "%.1f", abs(v) / Market.shared.fx.last * 100))% \(v >= 0 ? "늘어요." : "줄어요.")"
        let events: [(String, String, String)] = [("10월 15일", "미국 소비자물가 발표", ""), ("10월 22일", "드링커 3분기 실적 발표", "DRNK"), ("10월 28일", "미국 금리 결정", ""),
                                                   ("10월 29일", "삼성전자 3분기 실적 발표", "005930"), ("10월 30일", "애플 실적 발표", "AAPL"), ("11월 19일", "엔비디아 실적 발표", "NVDA")]
            .filter { e in e.2.isEmpty || m.rows.contains { r in r.id == e.2 } }

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "외부 요인", sub: "")
                VStack(alignment: .leading, spacing: 2) {
                    Text("평가액 " + AppModel.manS(tot)).appFont(22, .bold).foregroundStyle(Theme.change(tot))
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(AppModel.sgn(tot / max(1, m.total))).appFont(14, .semibold).foregroundStyle(Theme.change(tot))
                        Text("\(AppModel.man(m.total)) → \(AppModel.man(after))").appFont(12).foregroundStyle(Theme.sub)
                    }
                }
                ForEach(list, id: \.id) { x in SignedBar(label: x.label, value: AppModel.manS(x.dv), g: x.dv, scale: maxAbs) }
                ChipRow(items: [("mkt", "미국 시장"), ("rate", "미국 금리"), ("fx", "원/달러")], selection: $factor, accent: Theme.orange, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                Card {
                    HStack { Text(ask).appFont(14); Spacer(); Text(vText).appFont(15, .bold) }
                    switch factor {
                    case "mkt": Slider(value: $mkt, in: -30...30, step: 5).tint(Theme.orange).accessibilityLabel(ask)
                    case "rate": Slider(value: $rate, in: -1.5...1.5, step: 0.25).tint(Theme.orange).accessibilityLabel(ask)
                    default: Slider(value: $fx, in: -300...300, step: 20).tint(Theme.orange).accessibilityLabel(ask)
                    }
                    Text(note).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                }
                Card(dark: true) {
                    Text("다가오는 사건 (시안 예시 일정)").appFont(15, .bold)
                    ForEach(events, id: \.1) { d, t, k in
                        HStack(alignment: .top, spacing: 10) {
                            Text(d).appFont(12, .semibold).foregroundStyle(Theme.teal).frame(minWidth: 64, alignment: .leading)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(t).appFont(14, .bold)
                                Text(k.isEmpty ? "모든 종목" : "\(k) 비중 \(AppModel.pct(m.weight(k)))").appFont(12).foregroundStyle(Theme.sub)
                            }
                        }
                    }
                    Text("3년 전망에서 \"외부 요인\"을 켜면 반영돼요.").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: 금리 시나리오 — 금리→이익 관계식 식단 1000벌 (naeilo.com 외부 요인 › 금융·통화와 같은 계산)

/// data/macro/rates_earnings.json (매달 5일 갱신, 출처 Robert J. Shiller). 받은 파일은 저장해 두고 다음에 먼저 쓴다
@MainActor @Observable
final class MacroData {
    static let shared = MacroData()
    struct File: Decodable {
        struct Now: Decodable { let date: String?; let rate: Double?; let change_1y: Double? }
        struct Meals: Decodable { let g_now: Double; let coef: [[[Double]]] }
        let now: Now?
        let eps_last: String?
        let meals: Meals?
    }
    var file: File?
    var failed = false
    private var loading = false
    private static let name = "macro.json"

    init() {
        if let d = Store.readData(Self.name) { file = try? JSONDecoder().decode(File.self, from: d) }
    }
    func load() async {
        guard !loading else { return }
        loading = true; defer { loading = false }
        var req = URLRequest(url: Config.data.appendingPathComponent("macro/rates_earnings.json"))
        req.cachePolicy = .reloadIgnoringLocalCacheData
        do {
            let (d, r) = try await URLSession.shared.data(for: req)
            guard (r as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            let f = try JSONDecoder().decode(File.self, from: d)
            guard f.meals?.coef.isEmpty == false else { throw URLError(.cannotParseResponse) }
            file = f; failed = false
            Store.writeData(d, Self.name)
        } catch {
            if file == nil { failed = true }
        }
    }
    /// 지난 1년 10년물 금리 변화 (%p)
    var change1y: Double? { file?.now?.change_1y }
    /// 슬라이드 처음 값: 지난 1년 실제 변화 (0.1 단위)
    var defaultDr: Double { ((change1y ?? 0) * 10).rounded() / 10 }
}

/// 식단 j, y년째 S&P 실질 EPS 변화(로그 %) = a + b·Δ금리 + c·지금 EPS 증가율. 무작위 경로 없이 식단마다 답 하나
struct RateScen {
    let years: Int
    let yr: [[Double]]          // y년째 변화 [하위10, 중앙, 상위10] (%)
    let cum: [[Double]]         // y년까지 누적
    let dropYr: [Double]        // y년째 감소 비율
    let dropCum: Double         // 끝 해 누적 감소 비율
    let mkt: [Double]           // 시장 연 기대수익 [하위10, 중앙, 상위10]
    let slope: [Double]         // 식단마다 3년 동안의 b 합 (금리 1%p 당 이익 변화 %)

    static func q(_ sorted: [Double], _ p: Double) -> Double {
        guard !sorted.isEmpty else { return 0 }
        let x = p * Double(sorted.count - 1), i = Int(x.rounded(.down)), j = min(sorted.count - 1, i + 1)
        let w = x - Double(i)
        return sorted[i] * (1 - w) + sorted[j] * w
    }
    static func band(_ a: [Double]) -> [Double] {
        let s = a.sorted()
        return [q(s, 0.1), q(s, 0.5), q(s, 0.9)]
    }

    init(coef: [[[Double]]], g0: Double, dr: Double, m: Double) {
        let H = coef.first?.count ?? 0
        var tot: [[Double]] = [], acc: [[Double]] = [], sl: [Double] = []
        for c in coef {
            var t: [Double] = [], s: [Double] = [], run = 0.0, b = 0.0
            for abc in c where abc.count >= 3 {
                let v: Double = abc[0] + abc[1] * dr + abc[2] * g0
                run += v; t.append(v); s.append(run); b += abc[1]
            }
            if t.count == H { tot.append(t); acc.append(s); sl.append(b) }
        }
        let n = Double(max(1, tot.count))
        years = H
        yr = (0..<H).map { y in Self.band(tot.map { $0[y] }) }
        cum = (0..<H).map { y in Self.band(acc.map { $0[y] }) }
        dropYr = (0..<H).map { y in Double(tot.filter { $0[y] < 0 }.count) / n }
        dropCum = H > 0 ? Double(acc.filter { $0[H - 1] < 0 }.count) / n : 0
        // 시장 연 수익 ≈ 3년 누적 이익 변화(연) + 배당 1.5% + 금리 외 공통 기대수익 (PER 그대로)
        let Hd = Double(max(1, H))
        mkt = Self.band(acc.map { a in exp((a.last ?? 0) / 100 / Hd) - 1 + 0.015 + m })
        slope = sl
    }
}

struct RateScenarioView: View {
    @Environment(AppModel.self) private var m
    @State private var dr: Double? = nil
    @State private var mk = 0.0
    private var macro: MacroData { MacroData.shared }

    var body: some View {
        Group {
            if let f = macro.file, let M = f.meals {
                main(f, M)
            } else {
                VStack(spacing: 12) {
                    if macro.failed { Text("금리 자료를 불러오지 못했어요. 인터넷 연결을 확인해 주세요.").appFont(14).foregroundStyle(Theme.sub) }
                    else { ProgressView(); Text("금리 자료를 불러오는 중이에요.").appFont(13).foregroundStyle(Theme.sub) }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .task { await macro.load() }
    }

    /// 포트폴리오 시장 민감도 β (외부 요인 화면과 같은 값)
    private var beta: Double {
        let tot = max(1, m.total)
        return m.rows.reduce(0) { $0 + $1.value / tot * ExternalView.coef($1.sym).0 }
    }

    /// 3년 전망(로그정규 근사)의 연 기대를 shift 만큼 옮겼을 때 3년 안에 목표를 넘을 확률
    private func prob(_ f: AppModel.Forecast, _ shift: Double) -> Double {
        let T = 3.0, sg = max(0.01, f.sigma), g: Double = f.mu - sg * sg / 2 + shift
        let V0 = m.trackValue, K = m.keyValue, M = m.monthly * 1e4
        let eff: Double = V0 + M * 12 * T * exp(-g * T / 2)
        guard eff > 0, K > 0 else { return 0 }
        let num: Double = log(eff / K) + g * T
        return AppModel.normCDF(num / (sg * sqrt(T)))
    }

    @ViewBuilder private func main(_ file: MacroData.File, _ M: MacroData.File.Meals) -> some View {
        let d = dr ?? macro.defaultDr, mm = mk / 100
        let r = RateScen(coef: M.coef, g0: M.g_now, dr: d, m: mm)
        let f = m.forecast, kb = beta
        let base = prob(f, 0)
        let shifts: [Double] = r.slope.map { b in kb * (b * d / 3 / 100 + mm) }
        let pb = RateScen.band(shifts.map { prob(f, $0) })
        let drBinding = Binding<Double>(get: { dr ?? macro.defaultDr }, set: { dr = $0 })
        let H = r.years

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "금리 시나리오", sub: "")
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(m.keyName) 확률 " + AppModel.pct(pb[1])).appFont(22, .bold).foregroundStyle(Theme.teal)
                    Text("모형 범위 \(AppModel.pct(pb[0]))~\(AppModel.pct(pb[2])) · 금리 영향 빼면 \(AppModel.pct(base))").appFont(12).foregroundStyle(Theme.sub)
                }
                RateBandChart(cum: r.cum).frame(height: 130)
                Text("S&P500 실질 이익 (오늘 = 100) · 진한 선: 중앙 · 띠: 모형 80%가 드는 범위").appFont(12).foregroundStyle(Theme.sub)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                Card {
                    HStack { Text("앞으로 1년 10년물 금리 변화").appFont(14); Spacer(); Text(signed(d, "%p", 1)).appFont(15, .bold) }
                    Slider(value: drBinding, in: -2...3, step: 0.1).tint(Theme.orange).accessibilityLabel("앞으로 1년 10년물 금리 변화")
                    if let c = macro.change1y {
                        Text("지난 1년 실제 변화 \(signed(c, "%p", 2))" + (file.now?.rate.map { String(format: " · 지금 %.2f%%", $0) } ?? "")).appFont(12).foregroundStyle(Theme.muted)
                    }
                    HStack { Text("시장 공통 기대수익 (금리 외 요인, 연)").appFont(14); Spacer(); Text(signed(mk, "%", 0)).appFont(15, .bold) }
                    Slider(value: $mk, in: -10...10, step: 1).tint(Theme.orange).accessibilityLabel("시장 공통 기대수익")
                    Button("지금 값으로") { dr = nil; mk = 0 }.appFont(13, .bold).foregroundStyle(Theme.teal)
                }
                Card(dark: true) {
                    Text("S&P 실질 이익").appFont(15, .bold)
                    tableRow("", "하위 10%", "중앙", "상위 10%", "감소", head: true)
                    ForEach(0..<H, id: \.self) { y in
                        tableRow("\(y + 1)년째 변화", signed(r.yr[y][0], "%", 1), signed(r.yr[y][1], "%", 1), signed(r.yr[y][2], "%", 1), AppModel.pct(r.dropYr[y]))
                    }
                    if H > 0 {
                        tableRow("\(H)년 누적", signed(r.cum[H - 1][0], "%", 1), signed(r.cum[H - 1][1], "%", 1), signed(r.cum[H - 1][2], "%", 1), AppModel.pct(r.dropCum))
                    }
                    Divider()
                    tableRow("시장 연 기대수익", AppModel.sgn(r.mkt[0]), AppModel.sgn(r.mkt[1]), AppModel.sgn(r.mkt[2]), "")
                    Text("포트폴리오 시장 민감도 β \(String(format: "%.2f", kb)) · 3년 전망 \(m.keyName) 확률 \(AppModel.pct(base)) 기준").appFont(12).foregroundStyle(Theme.muted)
                }
                footnote("예측이 아닌 \"만약\" 도구. 1960년 이후 금리와 S&P500 이익의 관계식 1000개로 계산. 이익 자료 \(file.eps_last ?? "")까지. 출처 Robert J. Shiller.")
            }
            .padding(16)
        }
    }

    private func signed(_ v: Double, _ unit: String, _ digits: Int) -> String {
        (v >= 0 ? "+" : "−") + String(format: "%.\(digits)f", abs(v)) + unit
    }

    private func tableRow(_ k: String, _ a: String, _ b: String, _ c: String, _ e: String, head: Bool = false) -> some View {
        HStack(spacing: 4) {
            Text(k).frame(maxWidth: .infinity, alignment: .leading)
            Text(a).frame(width: 58, alignment: .trailing)
            Text(b).fontWeight(head ? .regular : .bold).frame(width: 58, alignment: .trailing)
            Text(c).frame(width: 58, alignment: .trailing)
            Text(e).frame(width: 40, alignment: .trailing)
        }
        .appFont(12, head ? .regular : .semibold).foregroundStyle(head ? Theme.sub : Theme.ink).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
    }
}

/// 누적 이익 띠 (오늘 = 100, 0~H년)
private struct RateBandChart: View {
    let cum: [[Double]]
    var body: some View {
        Canvas { ctx, size in
            let lv: [[Double]] = (0..<3).map { k in [100.0] + cum.map { 100 * exp($0[k] / 100) } }
            let n = lv[1].count
            let all = lv.flatMap { $0 }
            let hi = (all.max() ?? 110) * 1.03, lo = (all.min() ?? 90) * 0.97
            let x = { (i: Int) -> CGFloat in CGFloat(i) / CGFloat(max(1, n - 1)) * (size.width - 8) + 4 }
            let y = { (v: Double) -> CGFloat in CGFloat((hi - v) / max(1e-9, hi - lo)) * (size.height - 14) }
            let area = Path { p in
                for i in 0..<n { let pt = CGPoint(x: x(i), y: y(lv[2][i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                for i in stride(from: n - 1, through: 0, by: -1) { p.addLine(to: CGPoint(x: x(i), y: y(lv[0][i]))) }
                p.closeSubpath()
            }
            ctx.fill(area, with: .color(Theme.teal.opacity(0.16)))
            ctx.stroke(Path { p in p.move(to: CGPoint(x: 0, y: y(100))); p.addLine(to: CGPoint(x: size.width, y: y(100))) },
                       with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            for k in [0, 2] {
                ctx.stroke(Path { p in for i in 0..<n { let pt = CGPoint(x: x(i), y: y(lv[k][i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } },
                           with: .color(Theme.teal.opacity(0.6)), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            }
            ctx.stroke(Path { p in for i in 0..<n { let pt = CGPoint(x: x(i), y: y(lv[1][i])); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } },
                       with: .color(Theme.teal), lineWidth: 2)
            for i in 1..<max(2, n) where i < n {
                ctx.draw(Text("\(i)년").font(.system(size: 10)).foregroundStyle(Theme.muted), at: CGPoint(x: x(i) - 12, y: size.height - 6))
            }
        }
        .accessibilityLabel("S&P500 이익 띠 그래프")
    }
}

// MARK: 배당·세금

struct DividendView: View {
    @Environment(AppModel.self) private var m
    @State private var mode = "month"

    // [1년 배당(현지 통화), 지급 월] (시안용 예시. DRNK 는 배당 없음)
    static let divs: [String: (Double, [Int])] = [
        "QQQ": (2.9, [3, 6, 9, 12]), "AAPL": (1.04, [2, 5, 8, 11]), "NVDA": (0.04, [3, 6, 9, 12]), "MSFT": (3.32, [3, 6, 9, 12]), "SPY": (7.0, [1, 4, 7, 10]),
        "005930": (1444, [4, 5, 8, 11]), "000660": (1500, [2, 5, 8, 11]), "035720": (61, [4]), "069500": (800, [1, 4, 7, 10]), "360750": (250, Array(1...12)),
    ]

    var body: some View {
        let per = m.rows.map { r -> (id: String, gross: Double, net: Double, months: [Int]) in
            let d = Self.divs[r.id] ?? (0, [])
            let gross = m.krw(r.sym, r.h.qty * d.0)
            return (r.id, gross, gross * (1 - (r.sym.currency == .usd ? 0.15 : 0.154)), d.1)
        }
        let gross = per.reduce(0) { $0 + $1.gross }, net = per.reduce(0) { $0 + $1.net }
        let months = (0..<12).map { (10 + $0) % 12 + 1 }     // 11월부터
        let byMonth = months.map { mo in per.reduce(0) { $0 + ($1.months.contains(mo) ? $1.net / Double($1.months.count) : 0) } }
        let mMax = max(1, byMonth.max() ?? 1), sMax = max(1, per.map(\.net).max() ?? 1)
        // DRNK 는 배당이 없는 종목, 예시 표에 없는 종목(직접 추가한 종목)은 배당 자료가 없어 0으로 계산
        let zero = per.filter { $0.gross == 0 && Self.divs[$0.id] == nil && $0.id == "DRNK" }.map(\.id)
        let unknown = m.rows.filter { Self.divs[$0.id] == nil && $0.id != "DRNK" }.map(\.sym.short)
        let us = m.rows.filter { $0.sym.currency == .usd }, usU = us.reduce(0) { $0 + $1.value - $1.cost }
        let G = m.taxGain * 1e4, cgt = { (g: Double) in max(0, g - 250e4) * 0.22 }

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "배당·세금", sub: "앞으로 12개월 받을 배당과, 팔 때 낼 세금")
                HStack(alignment: .firstTextBaseline) {
                    Text("세후(원화)").appFont(13).foregroundStyle(Theme.sub)
                    Spacer()
                    Text(AppModel.won1(net)).appFont(22, .bold).foregroundStyle(Theme.teal)
                }
                if mode == "month" {
                    HStack(alignment: .bottom, spacing: 4) {
                        ForEach(months.indices, id: \.self) { i in
                            VStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 3).fill(byMonth[i] > 0 ? Theme.teal : Theme.track)
                                    .frame(height: max(2, byMonth[i] / mMax * 80))
                                Text("\(months[i])").appFont(10).foregroundStyle(Theme.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .frame(height: 100, alignment: .bottom)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("달마다 배당: " + months.indices.filter { byMonth[$0] > 0 }.map { "\(months[$0])월 \(AppModel.won1(byMonth[$0]))" }.joined(separator: ", "))
                } else {
                    ForEach(per, id: \.id) { x in
                        HStack(spacing: 8) {
                            Text(x.id).appFont(13, .semibold).frame(minWidth: 52, alignment: .leading)
                            GeometryReader { g in
                                RoundedRectangle(cornerRadius: 3).fill(Theme.teal).frame(width: g.size.width * x.net / sMax, height: 12)
                                    .frame(maxHeight: .infinity)
                            }.frame(height: 16)
                            Text(AppModel.won1(x.net)).appFont(13, .bold).frame(minWidth: 70, alignment: .trailing)
                        }
                    }
                }
                Text("세전 \(AppModel.won1(gross)) · 월평균 \(AppModel.won1(net / 12)) · 평가액의 \(String(format: "%.2f", net / max(1, m.total) * 100))%")
                    .appFont(12).foregroundStyle(Theme.sub)
                ChipRow(items: [("month", "달마다"), ("stock", "종목별")], selection: $mode, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                if net < 10e4 {
                    Text("지금 구성은 배당이 적어요." + (zero.isEmpty ? "" : " \(zero.joined(separator: ", "))는 배당을 주지 않아요.") + (unknown.isEmpty ? "" : " \(unknown.joined(separator: ", "))는 배당 자료가 아직 없어서 0으로 계산했어요.") + " 이 구성에서는 배당보다 가격 움직임이 평가액을 더 크게 바꿔요.")
                        .appFont(13).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.cream, in: RoundedRectangle(cornerRadius: 12))
                }
                Card {
                    Text("배당에 붙는 세금").appFont(15, .bold)
                    ruleRow("미국 종목 배당", "미국에서 15%를 떼고 들어와요. 국내에서 더 내지 않아요", "15%")
                    ruleRow("국내 종목·국내 상장 ETF 배당", "배당소득세 14% + 지방세 1.4%", "15.4%")
                    ruleRow("배당·이자 합계 2,000만원까지", "넘으면 다른 소득과 합쳐 계산(금융소득 종합과세). 지금 1년 \(AppModel.won1(gross))",
                            gross >= 2000e4 ? "넘음" : AppModel.man(2000e4 - gross) + " 남음")
                }
                Card(dark: true) {
                    Text("팔 때 세금 (해외주식 양도세, 내년 5월 신고)").appFont(15, .bold)
                    ruleRow("올해 이미 판 이익", "세금 규칙에 넣은 값", AppModel.man(G))
                    ruleRow("미국 종목 평가 손익", us.isEmpty ? "미국 종목 없음" : us.map(\.id).joined(separator: ", ") + " 지금 다 판다면", AppModel.manS(usU),
                            color: Theme.change(usU))
                    ruleRow("내년 5월 낼 세금", "지금 그대로 / 미국 종목 다 판다면", AppModel.man(cgt(G)) + " / " + AppModel.man(cgt(G + usU)))
                    Text(usU < 0
                         ? "평가 손실 \(AppModel.man(-usU))을 올해 팔아 확정하면 이미 판 이익과 합쳐져 세금이 \(AppModel.man(cgt(G) - cgt(G + usU))) 줄어요. 회복 미션의 절세 단계와 같은 계산이에요."
                         : "이익 250만원까지는 세금이 없고, 넘는 부분에 22%(지방세 포함)를 내요. 국내 상장주식은 대주주가 아니면 팔 때 양도세가 없어요(거래세만).")
                        .appFont(12).foregroundStyle(Theme.sub).lineSpacing(2)
                }
                footnote("지난 12개월 배당이 이어진다고 본 값이에요. 세무 상담이 아니에요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func ruleRow(_ k: String, _ sub: String, _ v: String, color: Color = Theme.ink) -> some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(k).appFont(14, .semibold)
                Text(sub).appFont(12).foregroundStyle(Theme.sub)
            }
            Spacer(minLength: 4)
            Text(v).appFont(14, .bold).foregroundStyle(color).multilineTextAlignment(.trailing)
        }
    }
}

// MARK: 환율 영향

struct FxImpactView: View {
    @Environment(AppModel.self) private var m
    @State private var period = "1y"
    @State private var what = Market.shared.fx.last.rounded()

    static let boughtAt = 1320.0     // 샀을 때 평균 환율 (시안 가정)

    var body: some View {
        let FX = Market.shared.fx.last, FXB = Self.boughtAt, sigma = 0.075
        let usd = m.rows.filter { $0.sym.currency == .usd || $0.id == "360750" }
        let usdV = usd.reduce(0) { $0 + $1.value }, usdW = usdV / max(1, m.total)
        let usdCost = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.cost }
        let fxEff = usdCost * (FX / FXB - 1), stkEff = (m.total - m.cost) - fxEff
        let periods: [String: Double] = ["1m": 1.0 / 12, "3m": 0.25, "1y": 1, "3y": 3]
        let T: Double = periods[period] ?? 1
        let label = ["1m": "1개월", "3m": "3개월", "1y": "1년", "3y": "3년"][period] ?? "1년"
        // 지난 환율 흐름 (시안 예시, t: 년, 음수 = 과거)
        let fxAt = { (t: Double) -> Double in
            FX + 70 * (sin(t * 2.1 + 1) - sin(1)) + 25 * sin(t * 9) * min(1, -t) + 9 * sin(t * 70) * min(1, -t * 8) + 4 * sin(t * 260) * min(1, -t * 30)
        }
        let past = (0...120).map { i -> (Double, Double) in let t = -T + T * Double(i) / 120; return (t, fxAt(t)) }
        let trend = past.indices.map { i -> (Double, Double) in
            let w = past[max(0, i - 12)...i]; return (past[i].0, w.reduce(0) { $0 + $1.1 } / Double(w.count))
        }
        let q = { (z: Double) in (0...20).map { i -> (Double, Double) in let t = Double(i) / 20; return (t, FX * exp(z * sigma * sqrt(t))) } }
        let vals = past.map(\.1) + [FXB], pad = ((vals.max() ?? FX) - (vals.min() ?? FX)) * 0.25 + 5
        let hi = (vals.max() ?? FX) + pad, lo = (vals.min() ?? FX) - pad
        let X = { (t: Double) in t <= 0 ? (t + T) / T * 0.7 : 0.7 + t * 0.3 }
        let Y = { (v: Double) in max(0, min(1, (hi - v) / (hi - lo))) }
        let valAt = { (r: Double) in m.total + usdV * (r / FX - 1) }
        let pMin = past.map(\.1).min() ?? FX, pMax = past.map(\.1).max() ?? FX
        let chg = FX / past[0].1 - 1

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "환율 영향", sub: "달러 자산 비중 \(AppModel.pct(usdW)) · 환율 1%면 평가액 \(AppModel.man(usdV * 0.01))")
                HStack(alignment: .firstTextBaseline) {
                    Text("원/달러 (지금 · 오늘 \(AppModel.sgn(Market.shared.fx.change)))").appFont(13).foregroundStyle(Theme.sub)
                    Spacer()
                    Text("\(Int(FX).formatted())원").appFont(22, .bold)
                }
                Text("\(label) 동안 \(AppModel.sgn(chg)) · 범위 \(Int(pMin.rounded()).formatted())~\(Int(pMax.rounded()).formatted())원")
                    .appFont(13, .bold).foregroundStyle(Theme.change(chg))
                Canvas { ctx, size in
                    func band(_ a: [(Double, Double)], _ b: [(Double, Double)]) -> Path {
                        var p = linePath(a.map { (X($0.0), Y($0.1)) }, in: size)
                        for x in b.reversed() { p.addLine(to: CGPoint(x: X(x.0) * size.width, y: Y(x.1) * size.height)) }
                        p.closeSubpath(); return p
                    }
                    ctx.fill(band(q(1.645), q(-1.645)), with: .color(Theme.blue.opacity(0.12)))
                    ctx.fill(band(q(0.674), q(-0.674)), with: .color(Theme.blue.opacity(0.24)))
                    let nx = X(0) * size.width
                    ctx.stroke(Path { $0.move(to: CGPoint(x: nx, y: 0)); $0.addLine(to: CGPoint(x: nx, y: size.height)) }, with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                    let by = Y(FXB) * size.height
                    ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: by)); $0.addLine(to: CGPoint(x: size.width, y: by)) }, with: .color(Theme.orange), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                    ctx.stroke(linePath(trend.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1.6, dash: [4, 3]))
                    ctx.stroke(linePath(past.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
                    ctx.stroke(linePath(q(0).map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.blue), lineWidth: 2)
                }
                .frame(height: 140)
                .accessibilityLabel("원/달러 지난 흐름과 앞으로 1년 범위")
                HStack { Text("\(label) 전"); Spacer(); Text("오늘"); Spacer().frame(maxWidth: 60); Text("1년 뒤") }.appFont(11).foregroundStyle(Theme.muted)
                Text("검은 선: 지난 환율 · 회색 점선: 추세 · 파란 띠: 앞으로 1년 절반(진함)·90%의 경우 · 주황 점선: 샀을 때 평균 \(Int(FXB).formatted())원")
                    .appFont(11).foregroundStyle(Theme.sub)
                ChipRow(items: [("1m", "1개월"), ("3m", "3개월"), ("1y", "1년"), ("3y", "3년")], selection: $period, accent: Theme.blue, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                kpiGrid([
                    Kpi(k: "내 손익 중 주가 몫", v: AppModel.manS(stkEff), sub: "현지 통화로 오르내린 만큼", color: Theme.change(stkEff)),
                    Kpi(k: "내 손익 중 환율 몫", v: AppModel.manS(fxEff), sub: "샀을 때 \(Int(FXB).formatted())원 → 지금 \(Int(FX).formatted())원", color: Theme.change(fxEff)),
                    Kpi(k: "1년 뒤 환율 (절반의 경우)", v: "\(Int((FX * exp(-0.674 * sigma)).rounded()).formatted())~\(Int((FX * exp(0.674 * sigma)).rounded()).formatted())", sub: "지난 3년 흔들림 기준"),
                    Kpi(k: "환율 100원 바뀌면", v: AppModel.man(usdV * 100 / FX), sub: "평가액이 이만큼 함께 움직여요"),
                ])
                Card {
                    HStack { Text("환율이 이렇게 되면").appFont(14); Spacer(); Text("\(Int(what).formatted())원").appFont(15, .bold) }
                    Slider(value: $what, in: 1200...1600, step: 10).tint(Theme.blue).accessibilityLabel("환율")
                    Text("내 평가액 \(AppModel.man(valAt(what))) (\(AppModel.manS(valAt(what) - m.total))) · \(m.keyName)까지 \(AppModel.man(max(0, m.keyValue - valAt(what))))")
                        .appFont(13, .semibold)
                }
                Card(dark: true) {
                    Text("환율별 내 평가액 (주가는 그대로)").appFont(15, .bold)
                    ForEach([1250.0, 1300, FX, 1450, 1500], id: \.self) { r in
                        HStack {
                            Text("\(Int(r).formatted())원" + (r == FX ? " (지금)" : "")).appFont(13, r == FX ? .bold : .regular)
                            Spacer()
                            Text(AppModel.man(valAt(r))).appFont(14, .bold)
                            Text("\(m.keyName)까지 \(AppModel.man(max(0, m.keyValue - valAt(r))))").appFont(12).foregroundStyle(Theme.sub).frame(minWidth: 110, alignment: .trailing)
                        }
                    }
                }
                footnote("평균 매수 환율 1,320원은 가정값. 범위 지난 3년 흔들림 기준.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: 종목 한눈에 — 숫자만, 추천 문구 없음

struct GlanceView: View {
    @Environment(AppModel.self) private var m
    @State private var metric = "dd"

    private let names: [(String, String, String)] = [
        ("dd", "고점 대비", "지난 1년 가장 높았던 종가와 비교한 값이에요."), ("r1", "1년", "1년 전 종가와 비교한 값이에요 (현지 통화)."),
        ("vc", "내 단가 대비", "내 평균 단가와 비교한 값이에요."), ("w", "비중", "내 평가액에서 차지하는 몫이에요."), ("vol", "흔들림", "1년에 보통 이 정도 오르내렸어요 (연 변동성)."),
    ]

    var body: some View {
        // 카드 순서는 내 종목 탭에서 고른 정렬을 따른다
        let data = m.sortedRows.map { r -> (row: AppModel.Row, yr: [Double], hi: Double, lo: Double, m: [String: Double]) in
            let yr = (0...52).map { m.prices.price(r.sym, at: 2.0 / 3 + Double($0) / 156) }
            let hi = yr.max() ?? 1, lo = yr.min() ?? 1, p1 = yr.last ?? 1
            return (r, yr, hi, lo, ["dd": p1 / hi - 1, "r1": p1 / yr[0] - 1, "vc": r.cost > 0 ? r.value / r.cost - 1 : 0,
                                     "w": r.value / max(1, m.total), "vol": (Sample.lensParams[r.id] ?? (0, 0, 0.25)).2])
        }
        let signed = metric != "w" && metric != "vol"
        let fmt = { (k: String, v: Double) in k == "w" || k == "vol" ? AppModel.pct(v) : AppModel.sgn(v) }
        let col = { (k: String, v: Double) -> Color in k == "w" || k == "vol" ? Theme.ink : Theme.change(v) }
        let maxAbs = max(0.01, data.map { abs($0.m[metric] ?? 0) }.max() ?? 0.01)
        let info = names.first { $0.0 == metric }!

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "종목 한눈에", sub: "종목마다 지금 상태를 숫자로만 보여 줘요")
                Text("\(info.1) (큰 순서)").appFont(14, .bold)
                ForEach(data.sorted { ($0.m[metric] ?? 0) > ($1.m[metric] ?? 0) }, id: \.row.id) { d in
                    let v = d.m[metric] ?? 0
                    if signed {
                        SignedBar(label: d.row.sym.short, value: fmt(metric, v), g: v, scale: maxAbs)
                    } else {
                        HStack(spacing: 8) {
                            Text(d.row.sym.short).appFont(13, .semibold).frame(minWidth: 52, alignment: .leading)
                            GeometryReader { g in
                                RoundedRectangle(cornerRadius: 3).fill(Theme.sub2).frame(width: g.size.width * v / maxAbs, height: 12).frame(maxHeight: .infinity)
                            }.frame(height: 16)
                            Text(fmt(metric, v)).appFont(13, .bold).frame(minWidth: 60, alignment: .trailing)
                        }
                    }
                }
                Text(info.2).appFont(12).foregroundStyle(Theme.sub)
                ChipRow(items: names.map { ($0.0, $0.1) }, selection: $metric)
            }
        } content: {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(data, id: \.row.id) { d in
                    NavigationLink(value: d.row.sym) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(d.row.sym.name) \(d.row.id)").appFont(15, .bold)
                                    Text("지금 \(AppModel.price(d.row.sym, d.row.sym.last)) · 1년 \(AppModel.price(d.row.sym, d.lo))~\(AppModel.price(d.row.sym, d.hi))")
                                        .appFont(12).foregroundStyle(Theme.sub)
                                }
                                Spacer(minLength: 6)
                                Sparkline(points: d.yr).frame(width: 96, height: 34)
                            }
                            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), alignment: .leading, spacing: 8) {
                                ForEach(names, id: \.0) { k, n, _ in
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(n).appFont(11).foregroundStyle(Theme.sub)
                                        Text(fmt(k, d.m[k] ?? 0)).appFont(14, .bold).foregroundStyle(col(k, d.m[k] ?? 0))
                                            .underline(k == metric)
                                    }
                                }
                            }
                        }
                        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                footnote("상태를 숫자로만 보여 줘요. 사거나 팔라는 뜻이 아니에요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: .y1) }
    }
}
