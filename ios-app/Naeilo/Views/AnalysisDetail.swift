import SwiftUI

// 분석 탭 상세 화면 (시안 31~35판): 내 길 · 외부 요인 · 배당·세금 · 환율 영향 · 종목 한눈에. (인사이트는 앱에서 뺐다)
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

private struct DetailHead: View {
    let title: String
    let sub: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).appFont(22, .bold)
            Text(sub).appFont(14).foregroundStyle(Theme.sub)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v).appFont(18, .bold).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.7)
            Text(sub).appFont(11).foregroundStyle(Theme.muted)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }
}

private func kpiGrid(_ items: [Kpi]) -> some View {
    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
        ForEach(items.indices, id: \.self) { items[$0] }
    }
}

private func footnote(_ t: String) -> some View {
    Text(t).appFont(12).foregroundStyle(Theme.muted).lineSpacing(2).frame(maxWidth: .infinity, alignment: .leading)
}

private func pinnedBox<C: View>(@ViewBuilder _ c: () -> C) -> some View {
    VStack(alignment: .leading, spacing: 10) { c() }
        .fixedSize(horizontal: false, vertical: true)
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(.white).overlay(alignment: .bottom) { Divider() }
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
            ? (0...9).map { i in let t = mS + Double(i) / 12; return (t, f.q75[i], f.q25[i]) } : []
        let vals = actPts.map(\.1) + needPts.map(\.1) + bandPts.flatMap { [$0.1, $0.2] }
        let hi = (vals.max() ?? 1) * 1.03, lo = (vals.min() ?? 0) * 0.97
        let X = { (t: Double) in t / x1 }, Y = { (v: Double) in (hi - v) / max(1, hi - lo) }
        let yrsLeft = mEnd - mS
        let now = m.trackValue, mM = p.monthly
        let needCagr = now > 0 && yrsLeft > 0 ? pow(max(1, K - mM * 12 * yrsLeft) / now, 1 / yrsLeft) - 1 : 0
        let pPlan = f.prob(min(3, yrsLeft))
        let lead = gap >= 0 ? Theme.teal : Color(hex: 0xB5651D)

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
                .accessibilityLabel("내 길과 실제 평가액 그래프")
                HStack { Text("시작 7월"); Spacer(); Text(view == "past" ? "다음 달" : m.isGoal ? "\(m.gY)년 뒤 목표일" : "1년 뒤") }.appFont(11).foregroundStyle(Theme.muted)
                FlowRow(spacing: 10) {
                    legend(Theme.teal, "실제"); legend(Theme.purple, "내 길 (정한 목표대로)")
                    if view == "band" { legend(Theme.teal.opacity(0.3), "3년 전망 (절반의 경우)") }
                }
                ChipRow(items: [("past", "시작부터"), ("all", "목표일까지"), ("band", "예보 겹치기")], selection: $view, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                Text(view == "band"
                     ? "보라 점선(내 길)이 초록 띠 안쪽에 있으면 시장이 줄 수 있는 범위 안의 길이에요. 띠 위로 벗어날수록 어려운 길이고, 그 길을 넘을 확률은 3년 전망에서 \(AppModel.pct(pPlan))예요."
                     : "보라 점선은 내가 정한 길이에요. 날씨 예보 같은 3년 전망과 달리, 지금 내가 약속한 경로보다 앞서는지 뒤처지는지만 봐요. \"예보 겹치기\"를 누르면 두 그래프를 한 번에 볼 수 있어요.")
                    .appFont(14).foregroundStyle(Theme.sub).lineSpacing(3)
                Card {
                    Text("달마다 내 길보다 앞섰나").appFont(15, .bold)
                    ForEach(1...3, id: \.self) { k in
                        let t = Double(k) / 12, g = need(t) > 0 ? act(t) / need(t) - 1 : 0
                        SignedBar(label: k == 3 ? "오늘" : "\(k)달째", value: AppModel.sgn(g), g: g, scale: 0.2, pos: Theme.green, neg: Theme.orange,
                                  valueColor: g >= 0 ? Theme.teal : Color(hex: 0xB5651D))
                    }
                }
                kpiGrid([
                    Kpi(k: "목표일까지", v: (yrsLeft >= 1 ? "\(Int(yrsLeft))년 " : "") + "\(Int((yrsLeft.truncatingRemainder(dividingBy: 1) * 12).rounded()))개월", sub: m.isGoal ? "\(m.gY)년 목표" : "1년 목표"),
                    Kpi(k: "필요 연수익률", v: AppModel.sgn(needCagr), sub: m.isGoal ? "적립 포함, 남은 기간" : "본전까지 남은 기간"),
                    Kpi(k: "이번 달 넣을 돈", v: m.isGoal ? AppModel.wonK(m.gM) : "없음", sub: m.isGoal ? "내 길에 들어 있음" : "회복은 적립 없이"),
                    Kpi(k: "내 길대로 갈 확률", v: AppModel.pct(pPlan), sub: "3년 전망 (\(m.lens.label))"),
                ])
                footnote("내 길은 정한 목표 금액, 목표일, 매달 넣는 돈으로 그린 하나의 선이에요. 3년 전망은 시장이 줄 수 있는 여러 미래의 범위예요. 과거 3년 기간 수익률과 겹쳐 보기는 PC에서 볼 수 있어요.")
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

    var body: some View {
        let v = factor == "mkt" ? mkt : factor == "rate" ? rate : fx
        let eff = { (id: String) -> Double in
            let b = Self.beta[id] ?? (1, -3, 0)
            return factor == "mkt" ? b.0 * v / 100 : factor == "rate" ? b.1 * v / 100 : b.2 * v / Sample.fx
        }
        let list = m.rows.map { (id: $0.id, dv: $0.value * eff($0.id)) }
        let tot = list.reduce(0) { $0 + $1.dv }, maxAbs = max(1, list.map { abs($0.dv) }.max() ?? 1)
        let after = m.total + tot
        let ask = factor == "mkt" ? "S&P500이 얼마나 움직이면" : factor == "rate" ? "미국 10년 금리가 얼마나 바뀌면" : "환율이 \(Int(Sample.fx).formatted())원에서 얼마나 바뀌면"
        let vText = factor == "mkt" ? (v > 0 ? "+" : "") + "\(Int(v))%" : factor == "rate" ? (v > 0 ? "+" : "") + String(format: "%g", v) + "%p" : (v > 0 ? "+" : "") + "\(Int(v))원"
        let coef = m.rows.map { r -> String in
            let b = Self.beta[r.id] ?? (1, -3, 0)
            return r.id + (factor == "mkt" ? " \(String(format: "%g", b.0))배" : factor == "rate" ? " \(String(format: "%g", b.1))%" : (b.2 > 0 ? " 환율만큼" : " 영향 없음"))
        }.joined(separator: ", ")
        let note = factor == "mkt" ? "S&P500이 1% 움직일 때 평균 반응: \(coef). 시장보다 크게 움직이는 종목이 많을수록 같은 하락에도 평가액이 더 줄어요."
            : factor == "rate" ? "미국 10년 금리가 1%p 오를 때 평균 반응: \(coef). 성장주일수록 금리에 더 민감했어요."
            : "원화로 환산하면: \(coef). 미국 종목은 주가가 그대로여도 환율이 \(Int(abs(v)))원 \(v >= 0 ? "오르면" : "내리면") 원화 평가액이 \(String(format: "%.1f", abs(v) / Sample.fx * 100))% \(v >= 0 ? "늘어요." : "줄어요.")"
        let events: [(String, String, String)] = [("10월 15일", "미국 소비자물가 발표", ""), ("10월 22일", "드링커 3분기 실적 발표", "DRNK"), ("10월 28일", "미국 금리 결정", ""),
                                                   ("10월 29일", "삼성전자 3분기 실적 발표", "005930"), ("10월 30일", "애플 실적 발표", "AAPL"), ("11월 19일", "엔비디아 실적 발표", "NVDA")]
            .filter { e in e.2.isEmpty || m.rows.contains { r in r.id == e.2 } }

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "외부 요인", sub: "시장·금리·환율이 움직이면 내 평가액은 얼마나 바뀌나")
                Text(ask + "?").appFont(13).foregroundStyle(Theme.sub)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("평가액 " + AppModel.manS(tot)).appFont(22, .bold).foregroundStyle(Theme.change(tot))
                    Text("\(AppModel.sgn(tot / max(1, m.total))) · \(AppModel.man(m.total)) → \(AppModel.man(after))").appFont(12).foregroundStyle(Theme.sub)
                }
                ForEach(list, id: \.id) { x in SignedBar(label: x.id, value: AppModel.manS(x.dv), g: x.dv, scale: maxAbs) }
                Text("\(m.keyName)까지 남은 금액 \(AppModel.man(max(0, m.keyValue - m.total))) → \(AppModel.man(max(0, m.keyValue - after)))").appFont(13, .semibold)
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
                Card {
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
                    Text("3년 전망의 \"외부 요인\"을 켜면 이런 사건을 넣어 범위를 넓혀 계산해요.").appFont(12).foregroundStyle(Theme.muted)
                }
                footnote("반응 크기는 지난 3년 일별 움직임으로 계산한 평균이에요(시안용 가정값). 유가·금·원자재와 사건별 효과 표는 PC naeilo.com에서 볼 수 있어요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
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
        let zero = per.filter { $0.gross == 0 }.map(\.id)
        let us = m.rows.filter { $0.sym.currency == .usd }, usU = us.reduce(0) { $0 + $1.value - $1.cost }
        let G = m.taxGain * 1e4, cgt = { (g: Double) in max(0, g - 250e4) * 0.22 }

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "배당·세금", sub: "앞으로 12개월 받을 배당과, 팔 때 낼 세금")
                HStack(alignment: .firstTextBaseline) {
                    Text("앞으로 12개월 배당 (세후, 원화)").appFont(13).foregroundStyle(Theme.sub)
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
                    Text("지금 구성은 배당이 적어요." + (zero.isEmpty ? "" : " \(zero.joined(separator: ", "))는 배당을 주지 않아요.") + " 이 구성에서는 배당보다 가격 움직임이 평가액을 더 크게 바꿔요.")
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
                Card {
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
                footnote("배당은 지난 12개월 지급액이 그대로 이어진다고 본 시안용 값이에요. 3년 배당 표와 연도별 매도 계획은 PC naeilo.com에서 볼 수 있어요. 세무 상담이 아니에요.")
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
    @State private var what = Sample.fx

    static let boughtAt = 1320.0     // 샀을 때 평균 환율 (시안 가정)

    var body: some View {
        let FX = Sample.fx, FXB = Self.boughtAt, sigma = 0.075
        let usd = m.rows.filter { $0.sym.currency == .usd || $0.id == "360750" }
        let usdV = usd.reduce(0) { $0 + $1.value }, usdW = usdV / max(1, m.total)
        let usdCost = m.rows.filter { $0.sym.currency == .usd }.reduce(0) { $0 + $1.cost }
        let fxEff = usdCost * (FX / FXB - 1), stkEff = (m.total - m.cost) - fxEff
        let T: Double = ["1m": 1.0 / 12, "3m": 0.25, "1y": 1, "3y": 3][period] ?? 1
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
                    Text("원/달러 (어제 종가)").appFont(13).foregroundStyle(Theme.sub)
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
                Card {
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
                footnote("샀을 때 평균 환율은 시안용 가정값(1,320원)이에요. 환율 범위는 지난 3년 흔들림으로 계산했고, 방향을 맞히려는 예측이 아니에요. 기간별 표와 환헤지 비교는 PC naeilo.com에서 볼 수 있어요.")
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
        let data = m.rows.map { r -> (row: AppModel.Row, yr: [Double], hi: Double, lo: Double, m: [String: Double]) in
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
                        SignedBar(label: d.row.id, value: fmt(metric, v), g: v, scale: maxAbs)
                    } else {
                        HStack(spacing: 8) {
                            Text(d.row.id).appFont(13, .semibold).frame(minWidth: 52, alignment: .leading)
                            GeometryReader { g in
                                RoundedRectangle(cornerRadius: 3).fill(Theme.sub2).frame(width: g.size.width * v / maxAbs, height: 12).frame(maxHeight: .infinity)
                            }.frame(height: 16)
                            Text(fmt(metric, v)).appFont(13, .bold).frame(minWidth: 60, alignment: .trailing)
                        }
                    }
                }
                Text(info.2).appFont(12).foregroundStyle(Theme.sub)
                ChipRow(items: names.map { ($0.0, $0.1) }, selection: $metric, accent: Theme.ink)
            }
        } content: {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(data, id: \.row.id) { d in
                    NavigationLink(value: d.row.sym) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(d.row.sym.name) \(d.row.id)").appFont(15, .bold)
                                    Text("어제 종가 \(AppModel.price(d.row.sym, d.yr.last ?? 0)) · 1년 \(AppModel.price(d.row.sym, d.lo))~\(AppModel.price(d.row.sym, d.hi))")
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
                        .background(.white, in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                footnote("상태를 숫자로만 보여 줘요. 사거나 팔라는 뜻이 아니에요. 업종 비교와 가격 그래프 겹쳐 보기는 PC naeilo.com에서 볼 수 있어요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: .y1) }
    }
}
