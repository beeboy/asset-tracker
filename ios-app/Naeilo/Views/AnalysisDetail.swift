import SwiftUI

// 분석 탭 상세 화면 (시안 31~35판): 내 길 · 금리 시나리오 · 외부 요인 · 배당·세금 · 환율 영향 · 종목 한눈에. (인사이트는 앱에서 뺐다)
// 숫자는 모두 계산값이고 사거나 팔라는 문구는 넣지 않는다. 외부 요인·배당·환율은 웹과 같은 자료(사이트 stats.json·calc.json)와 같은 식.

// MARK: 공통

extension AppModel {
    // 내 길: 회복 = 본전을 1년 안에(적립 없음), 목표 = 목표 금액·목표일·매달 적립. 시작은 실제로 길을 시작한 날
    /// 내 길 시작한 날: 목표 루트는 목표 시작일(사이트와 같은 값), 회복 루트는 인터미션 시작일과 내 기록 첫날 중 이른 날. 모르면 오늘
    var pathStartDate: Date {
        let rec = (isSample ? nil : myRecord.first).flatMap { Day.date($0.d) }
        if isGoal { return min(Date(), goalFromDate) }
        let rs = interStartAt.map { Date(timeIntervalSince1970: $0) }
        let d = [rs, rec].compactMap { $0 }.min()
        return min(Date(), d ?? Date())
    }
    var pathStartText: String { let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일"; return f.string(from: pathStartDate) }
    var pathStartMonth: String { "\(Calendar.current.component(.month, from: pathStartDate))월" }
    /// pts = 실제 평가액 (시작부터 년, 원). 거래일마다 하나, 끝은 오늘 지금 평가액
    struct MyPath { let start: Double; let end: Double; let key: Double; let monthly: Double; let pts: [(Double, Double)]; let act: (Double) -> Double; let need: (Double) -> Double
        /// 시작일 뒤에 처음 넣은 국내 주식 합계 (들어온 돈, 원). 시작 대비에서 뺀다
        var krIn = 0.0
    }
    /// 두 날짜("yyyy-MM-dd") 사이 년 (웹 yearsBetween 과 같은 식)
    static func yrs(_ a: String, _ b: String) -> Double {
        guard let x = Day.date(a), let y = Day.date(b) else { return 0 }
        return y.timeIntervalSince(x) / (365.25 * 86400)
    }
    /// 내 길 실제 평가액 (웹 목표 진행 actualSeries 와 같은 식): 시작일 무렵부터 거래일마다
    /// 그날 수량(수량 기록, 없으면 지금 수량) × 그날 종가 × 그날 원/달러 종가. 과거 시세가 없는 종목은 지금 평가액 그대로
    /// 현금은 그대로 있다고 보고 날마다 더한다 (웹 goalPath 와 같다)
    func pathSeries(from start: String) -> [(d: String, v: Double)] {
        let rs = rows, today = Day.today, fxH = PriceHistory.of("FX"), cash = cashKrw
        let hs = rs.map { (r: $0, h: PriceHistory.of($0.id), t: Sync.siteTicker($0.id)) }
        let from = Day.str(Calendar.current.date(byAdding: .day, value: -10, to: Day.date(start) ?? Date()) ?? Date())
        var ds = Set<String>()
        for x in hs { if let h = x.h { for d in h.dates.reversed() { if d < from { break }; if d < today { ds.insert(d) } } } }
        return ds.sorted().map { d in
            let q = Lots.on(lots, d)
            let v = hs.reduce(0.0) { a, x in
                guard let h = x.h else { return a + x.r.value }
                guard let px = h.on(d) else { return a }
                let n = q.map { $0[x.t] ?? 0 } ?? x.r.h.qty
                return a + px * n * (x.r.sym.currency == .usd ? (fxH?.on(d) ?? Market.shared.valFx) : 1)
            }
            return (d: d, v: v + cash)
        }
    }
    var myPath: MyPath {
        let mEnd = isGoal ? goalSpan : 1     // 목표: 시작일부터 목표일까지 (사이트와 같은 날짜)
        let sD = Day.str(pathStartDate), today = Day.today
        // 시작한 날부터 오늘까지 (년). 오늘 시작이면 하루로 둔다
        let mS = min(mEnd * 0.95, max(1.0 / 365.25, Self.yrs(sD, today))), mM = isGoal ? gM * 1e4 : 0, K = keyValue
        let now = trackValue
        var pts: [(Double, Double)]
        if route == .novice {
            pts = (0...30).map { i in let t = mS * Double(i) / 30; return (t, max(0, now - mM * 12 * (mS - t))) }
        } else if isSample {
            // 샘플(가상 종목 DRNK)은 날짜별 시세가 없어 지난 3년 흐름에서 어림한다
            pts = (0...30).map { i in let t = mS * Double(i) / 30; return (t, i == 30 ? now : self.totalAt(1 - (mS - t) / 3)) }
        } else {
            // 웹처럼 시작일 또는 그 뒤 첫 거래일 값에서 출발 (시작일 뒤 거래일이 아직 없으면 그 전 마지막 거래일)
            let S = pathSeries(from: sD)
            let i0 = S.firstIndex { $0.d >= sD } ?? max(0, S.count - 1)
            pts = []
            if !S.isEmpty {
                pts.append((0.0, S[i0].v))
                for x in S[(i0 + 1)...] { pts.append((Self.yrs(sD, x.d), x.v)) }
            }
            pts.append((mS, now))
        }
        let P = pts
        let act: (Double) -> Double = { t in
            if t >= mS { return now }
            return (P.last { $0.0 <= t } ?? P.first)?.1 ?? now
        }
        let vS = P.first?.1 ?? now, kAdj = K - mM * 12 * mEnd
        // 시작일 뒤에 처음 넣은 국내 주식 합계는 들어온 돈: 그날부터 길에도 그 금액을 더한다 (앞선 것으로 보이지 않게. 웹 goalPath 와 같다)
        let KL = CustomSymbols.shared.krLog
        let kin = krSum > 0 && route != .novice && !isSample && MC.krAt(KL, sD) == 0 ? KL.first(where: { $0.d > sD && $0.v > 0 }) : nil
        let tIn = kin.map { Self.yrs(sD, $0.d) } ?? .infinity, vIn = kin?.v ?? 0
        let need: (Double) -> Double = { t in mM * 12 * t + (kAdj > 0 && vS > 0 ? vS * pow(kAdj / vS, t / mEnd) : vS + (K - vS) * t / mEnd) + (t >= tIn ? vIn : 0) }
        return MyPath(start: mS, end: mEnd, key: K, monthly: mM, pts: P, act: act, need: need, krIn: vIn)
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
            .modifier(DetailTitle(title: title))
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

// MARK: 내 길 — 목표 루트는 판정·도달 시점 화면(B안), 회복 루트는 계획선(보라 점선) vs 실제

struct MyPathView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        if m.isGoal { MyPathGoalView() } else { MyPathClassicView() }
    }
}

struct MyPathClassicView: View {
    @Environment(AppModel.self) private var m
    // 고르기 전에는 시작한 지 석 달이 안 됐으면 '시작부터' (목표일까지 보면 실제 선이 점처럼 짧다)
    @State private var picked: String? = nil

    var body: some View {
        let p = m.myPath, mS = p.start, mEnd = p.end, K = p.key, act = p.act, need = p.need
        let view = picked ?? (mS < 0.25 ? "past" : "all")
        let gap = p.need(mS) > 0 ? p.act(mS) / p.need(mS) - 1 : 0
        let f = m.forecast
        let x1 = view == "past" ? mS + 1.0 / 12 : mEnd
        let actPts = p.pts
        let needPts = (0...60).map { i -> (Double, Double) in let t = x1 * Double(i) / 60; return (t, need(t)) }
        let bandPts: [(Double, Double, Double)] = view == "band"
            // 전망(오늘부터 목표일까지 월 경계마다)을 그래프 끝까지 겹친다
            // 전망 경로는 종목만이라 현금을 더해 내 길(현금 포함)과 같은 기준으로
            ? (0...max(min(1, f.tY.count - 1), f.idx(years: x1 - mS))).map { i in (mS + f.tY[i], f.q75[i] + m.cashKrw, f.q25[i] + m.cashKrw) } : []
        let vals = actPts.map(\.1) + needPts.map(\.1) + bandPts.flatMap { [$0.1, $0.2] }
        let hi = (vals.max() ?? 1) * 1.03, lo = (vals.min() ?? 0) * 0.97
        let X = { (t: Double) in t / x1 }, Y = { (v: Double) in (hi - v) / max(1, hi - lo) }
        let yrsLeft = mEnd - mS
        // 남은 기간: 웹 목표 진행과 같은 표시 (3.0년, 1년 안 되면 개월)
        let leftText = AppModel.leftText(m.isGoal ? AppModel.yrs(Day.today, Day.str(m.goalToDate)) : yrsLeft)
        // 지난 달마다: 시작한 날부터 꽉 채운 달만 (최근 6달), 끝은 오늘
        let months = Calendar.current.dateComponents([.month], from: m.pathStartDate, to: Date()).month ?? 0
        let monthRows: [(String, Double)] = (max(1, months - 5)...max(1, months)).compactMap { k -> (String, Double)? in
            guard k <= months, let d = Calendar.current.date(byAdding: .month, value: k, to: m.pathStartDate) else { return nil }
            return ("\(k)달째", d.timeIntervalSince(m.pathStartDate) / (365.25 * 86400))
        } + [("오늘", mS)]
        let now = m.trackValue, mM = p.monthly
        let needCagr = now > 0 && yrsLeft > 0 ? pow(max(1, K - mM * 12 * yrsLeft) / now, 1 / yrsLeft) - 1 : 0
        let pPlan = m.isGoal ? f.pGoal : f.prob(min(3, yrsLeft))   // 목표 루트: 목표일에 목표 이상일 확률 (웹 목표 확률)
        let lead = gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060)

        PinnedLayout {
            pinnedBox {
                DetailHead(title: "내 길", sub: m.isGoal ? "목표 \(AppModel.wonK(m.gK)) · \(m.goalToText)까지 · 매달 \(AppModel.wonK(m.gM)) (\(m.pathStartText) 시작)" : "본전 \(AppModel.eok(K)) · 1년 안에 (\(m.pathStartText) 시작)")
                HStack(alignment: .firstTextBaseline) {
                    Text("\(gap >= 0 ? "앞섬" : "뒤처짐") \(String(format: "%.1f", abs(gap) * 100))%").appFont(22, .bold).foregroundStyle(lead)
                    Spacer()
                    // 웹 목표 진행처럼 금액 차이 (금액은 이 요약과 아래 달마다 막대에만)
                    Text("계획선보다 \(AppModel.eokS(act(mS) - need(mS)))").appFont(13, .semibold).foregroundStyle(lead)
                }
                // 웹 목표 진행의 '시작 대비'와 같은 줄: 시작일 평가액 → 지금 평가액 (웹과 숫자를 나란히 맞춰 볼 수 있게)
                if let v0 = p.pts.first?.1, v0 > 0 {
                    HStack(alignment: .firstTextBaseline) {
                        Text("시작 대비 \(AppModel.sgn((act(mS) - p.krIn) / v0 - 1))").appFont(13).foregroundStyle(Theme.sub)
                        Spacer()
                        Text("\(AppModel.eok(v0)) → \(AppModel.eok(act(mS)))").appFont(13).foregroundStyle(Theme.sub)
                    }
                    .accessibilityElement(children: .combine)
                }
                // 웹 목표 진행 요약과 같은 목표 달성 확률 (전망 계산이 끝나기 전에는 '계산 중')
                HStack(alignment: .firstTextBaseline) {
                    Text(m.isGoal ? "목표 달성 확률" : "\(m.keyName) 확률").appFont(13).foregroundStyle(Theme.sub)
                    Spacer()
                    Text(f.exact ? AppModel.pct(pPlan) : "계산 중").appFont(f.exact ? 17 : 13, .bold).foregroundStyle(f.exact ? Theme.teal : Theme.muted)
                }
                .accessibilityElement(children: .combine)
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
                    // 거래일이 적을 때는 날마다 작은 점도 찍는다
                    if actPts.count <= 40 {
                        for q in actPts.dropLast() {
                            ctx.fill(Path(ellipseIn: CGRect(x: X(q.0) * size.width - 2, y: Y(q.1) * size.height - 2, width: 4, height: 4)), with: .color(Theme.teal))
                        }
                    }
                    let ty = Y(actPts.last?.1 ?? 0) * size.height
                    ctx.fill(Path(ellipseIn: CGRect(x: tx - 4, y: ty - 4, width: 8, height: 8)), with: .color(Theme.teal))
                }
                .frame(height: 140)
                // 범례와 예보 겹치기 확률은 그래프 안에 작게
                .overlay(alignment: .topLeading) {
                    VStack(alignment: .leading, spacing: 2) {
                        legend(Theme.teal, "실제"); legend(Theme.purple, "계획선")
                        if view == "band" { legend(Theme.teal.opacity(0.3), "보통 범위") }
                    }
                    .padding(6).background(Theme.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
                }
                .overlay(alignment: .bottomTrailing) {
                    if view == "band" && f.exact {
                        Text("넘을 확률 \(AppModel.pct(pPlan))").appFont(12, .bold).foregroundStyle(Theme.purple)
                            .padding(.horizontal, 8).padding(.vertical, 4).background(Theme.card.opacity(0.85), in: Capsule())
                            .padding(.bottom, 4)
                    }
                }
                .accessibilityLabel("계획선과 실제 평가액 그래프")
                HStack { Text("시작 \(m.pathStartMonth)"); Spacer(); Text(view == "past" ? "다음 달" : m.isGoal ? "목표일 \(m.goalToText)" : "1년 뒤") }.appFont(11).foregroundStyle(Theme.muted)
                ChipRow(items: [("past", "시작부터"), ("all", "목표일까지"), ("band", "보통 범위 겹치기")], selection: Binding(get: { view }, set: { picked = $0 }), fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                Card {
                    Text("달마다 계획선과 차이").appFont(15, .bold)
                    ForEach(monthRows.indices, id: \.self) { i in
                        let t = monthRows[i].1, g = need(t) > 0 ? act(t) / need(t) - 1 : 0
                        SignedBar(label: monthRows[i].0, value: AppModel.eokS(act(t) - need(t)), g: g, scale: 0.2, pos: Theme.green, neg: Theme.orange,
                                  valueColor: g >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060))
                    }
                    if months < 1 { Text("시작한 지 한 달이 지나면 달마다 쌓여요.").appFont(12).foregroundStyle(Theme.muted) }
                }
                kpiGrid([
                    Kpi(k: "남은 기간", v: leftText, sub: m.isGoal ? m.goalToText : "1년 목표"),
                    Kpi(k: "필요 연수익률", v: AppModel.sgn(needCagr), sub: m.isGoal ? (mM > 0 ? "적립 포함" : "지금 자산만으로") : "본전까지"),
                    Kpi(k: "이번 달 넣을 돈", v: m.isGoal ? AppModel.wonK(m.gM) : "없음", sub: m.isGoal ? "계획선에 들어 있음" : "회복은 적립 없이"),
                ], dark: true)
                footnote(m.isGoal ? "예측이 아닌 기준선이에요. 실제는 그날 수량·종가·환율(오늘은 매매기준율)로 센 값\(m.cashKrw > 0 ? "에 사이트에 넣은 현금을 더한 값" : "")으로, naeilo.com 목표 진행과 같은 계산이에요. 목표 달성 확률은 \(m.forecastTitle)과 같은 값이에요."
                         : "예측이 아닌 기준선이에요. 회복 루트는 본전 기준이라 naeilo.com(목표 기준)과 달라요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: 내 길 (목표 루트) — 판정 카드 · 구성에 따라 언제 닿을까 · 계획선 그래프 · 숫자 자세히

/// 도달 시점 한 줄: 점 = 보통(가운데) 닿는 때, 막대 = 10번 중 8번(10~90%) 범위, 세로 점선 = 목표일. 위치는 연도 축 a0~a1
struct EtaRow: View {
    let name: String
    let sub: String
    let sim: GoalSim
    let a0: Double, a1: Double, goalYear: Double
    var strong = true

    var body: some View {
        let x = { (y: Double) in min(1, max(0, (y - a0) / max(0.5, a1 - a0))) }
        let lo = x(AppModel.yearAt(sim.etaLo)), hi = sim.etaHi.isFinite ? x(AppModel.yearAt(sim.etaHi)) : 1
        let mid = sim.etaMonths.isFinite ? x(AppModel.yearAt(sim.etaMonths)) : 1, gx = x(goalYear)
        let c = strong ? Theme.teal : Theme.muted
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(name).appFont(14, .bold)
                Spacer()
                Text("보통 " + AppModel.whenText(sim.etaMonths, cap: sim.capYears)).appFont(13, .semibold).foregroundStyle(c)
            }
            GeometryReader { g in
                let w = g.size.width
                ZStack(alignment: .topLeading) {
                    Capsule().fill(Theme.border.opacity(0.5)).frame(width: w, height: 2).offset(y: 9)
                    Capsule().fill(c.opacity(0.35)).frame(width: max(6, (hi - lo) * w), height: 6).offset(x: lo * w, y: 7)
                    Circle().fill(c).frame(width: 12, height: 12).offset(x: mid * w - 6, y: 4)
                    Path { $0.move(to: CGPoint(x: gx * w, y: 0)); $0.addLine(to: CGPoint(x: gx * w, y: 20)) }
                        .stroke(Theme.ink, style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                }
                .animation(.easeInOut(duration: 0.25), value: sim.etaMonths)
            }
            .frame(height: 20)
            if !sub.isEmpty { Text(sub).appFont(11).foregroundStyle(Theme.muted).lineLimit(2) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(name), 보통 \(AppModel.whenText(sim.etaMonths, cap: sim.capYears)), 10번 중 8번은 \(AppModel.whenText(sim.etaLo, cap: sim.capYears))에서 \(AppModel.whenText(sim.etaHi, cap: sim.capYears)) 사이")
    }

    /// 여러 줄을 감싸는 연도 축 (목표일 포함, 정수 해)
    static func axis(_ sims: [GoalSim], goalYear: Double) -> (Double, Double) {
        var lo = goalYear, hi = goalYear
        for s in sims {
            lo = min(lo, AppModel.yearAt(s.etaLo))
            hi = max(hi, AppModel.yearAt(s.etaHi.isFinite ? s.etaHi : Double(s.capYears * 12)))
        }
        return (floor(lo), max(floor(lo) + 2, ceil(hi)))
    }
}

/// 연도 눈금 (a0~a1, 2년마다)
struct EtaTicks: View {
    let a0: Double, a1: Double
    var body: some View {
        let step = a1 - a0 > 6 ? 2 : 1
        let ys = Array(stride(from: Int(a0), through: Int(a1), by: step))
        GeometryReader { g in
            ForEach(ys, id: \.self) { y in
                Text(String(y)).appFont(11).foregroundStyle(Theme.muted).fixedSize()
                    .position(x: min(g.size.width - 14, max(14, (Double(y) - a0) / max(0.5, a1 - a0) * g.size.width)), y: 7)
            }
        }
        .frame(height: 14)
        .accessibilityHidden(true)
    }
}

struct MyPathGoalView: View {
    @Environment(AppModel.self) private var m
    @State private var toGoal = true
    @State private var more = false

    var body: some View {
        let p = m.myPath, mS = p.start, mEnd = p.end, act = p.act, need = p.need
        let f = m.forecast, K = m.gK * 1e4, now = m.trackValue
        let yrsE = m.etaYears
        let sims: [(String, String, GoalSim)] = [("지금 구성", m.nowMix.desc, m.simulate(m.nowMix, years: yrsE))]
            + [AppModel.mixes[1], AppModel.mixes[2]].map { ($0.name, $0.desc, m.simulate($0, years: yrsE)) }
        let mine = sims[0].2
        let goalMo = max(0, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12)
        let goalYear = AppModel.yearAt(goalMo)
        let ax = EtaRow.axis(sims.map(\.2), goalYear: goalYear), a0 = ax.0, a1 = ax.1
        let leftText = AppModel.leftText(AppModel.yrs(Day.today, Day.str(m.goalToDate)))
        let gc = Calendar.current.dateComponents([.year, .month], from: m.goalToDate)
        // 그래프: 세로 눈금은 기간을 바꿔도 같게 (목표일까지 전체 값으로 정한다)
        let x1 = toGoal ? mEnd : mS + 1.0 / 12
        let actPts = p.pts
        let needAll = (0...60).map { i -> (Double, Double) in let t = mEnd * Double(i) / 60; return (t, need(t)) }
        let needPts = (0...60).map { i -> (Double, Double) in let t = x1 * Double(i) / 60; return (t, need(t)) }
        let bandAll: [(Double, Double, Double)] = f.tY.isEmpty ? []
            : (0...max(min(1, f.tY.count - 1), f.idx(years: mEnd - mS))).map { i in (mS + f.tY[i], f.q75[i] + m.cashKrw, f.q25[i] + m.cashKrw) }
        let bandPts = toGoal ? bandAll : []
        let vals = actPts.map(\.1) + needAll.map(\.1) + bandAll.flatMap { [$0.1, $0.2] } + [K]
        let hi = (vals.max() ?? 1) * 1.05, lo = (vals.filter { $0 > 0 }.min() ?? 0) * 0.9
        let X = { (t: Double) in t / max(1e-6, x1) }, Y = { (v: Double) in (hi - v) / max(1, hi - lo) }
        let months = Calendar.current.dateComponents([.month], from: m.pathStartDate, to: Date()).month ?? 0
        let gap = need(mS) > 0 ? act(mS) / need(mS) - 1 : 0
        let yrsLeft = mEnd - mS, mM = p.monthly
        let needCagr = now > 0 && yrsLeft > 0 ? pow(max(1, K - mM * 12 * yrsLeft) / now, 1 / yrsLeft) - 1 : 0
        let dark = Color(hex: 0x1E4A44, dark: 0x16302C)

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 1. 판정 카드
                VStack(alignment: .leading, spacing: 10) {
                    Text("내 목표").appFont(12, .semibold).foregroundStyle(.white.opacity(0.7))
                    Text("\(AppModel.wonK(m.gK)) · \(m.goalToText)까지 · 남은 \(leftText)").appFont(13).foregroundStyle(.white.opacity(0.85))
                    Text(f.exact ? "목표까지 가능성은 \(AppModel.verdict(f.pGoal))" : "목표까지 가능성을 계산하고 있어요")
                        .appFont(22, .bold).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(.white.opacity(0.18))
                            Capsule().fill(Color(hex: 0x9CCFC6, dark: 0x9CCFC6)).frame(width: g.size.width * min(1, max(0, now / max(1, K))))
                        }
                    }
                    .frame(height: 8)
                    HStack {
                        Text("지금 \(AppModel.eok(now))"); Spacer(); Text("목표 \(AppModel.eok(K))")
                    }
                    .appFont(12, .semibold).foregroundStyle(.white.opacity(0.85))
                    Text("지금 포트폴리오 그대로라면 \(AppModel.wonK(m.gK))에 닿는 시점은 보통 \(AppModel.whenText(mine.etaMonths, cap: mine.capYears))이에요. 구성을 바꾸면 이 시점이 달라져요.")
                        .appFont(13).foregroundStyle(.white.opacity(0.9)).fixedSize(horizontal: false, vertical: true)
                    if f.exact { Text("목표일에 목표 이상일 확률 \(AppModel.pct(f.pGoal))").appFont(11).foregroundStyle(.white.opacity(0.6)) }
                }
                .padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(dark, in: RoundedRectangle(cornerRadius: 18))
                .accessibilityElement(children: .combine)

                // 2. 구성에 따라 언제 닿을까?
                Card {
                    Text("구성에 따라 언제 닿을까?").appFont(15, .bold)
                    Text("점은 보통 닿는 시점, 막대는 10번 중 8번이 들어오는 범위예요.").appFont(12).foregroundStyle(Theme.sub)
                    EtaTicks(a0: a0, a1: a1)
                    ForEach(sims.indices, id: \.self) { i in
                        EtaRow(name: sims[i].0, sub: sims[i].1, sim: sims[i].2, a0: a0, a1: a1, goalYear: goalYear, strong: i == 0)
                    }
                    Text("점선 = 목표일 \(String(gc.year ?? 0)).\(gc.month ?? 0)").appFont(11).foregroundStyle(Theme.muted)
                    Text("빨리 닿을 수도 있는 구성은 범위도 넓어요. 일찍 닿는 대신 크게 늦어질 수도 있어요.").appFont(12).foregroundStyle(Theme.sub)
                        .fixedSize(horizontal: false, vertical: true)
                    if m.route != .novice && !m.rows.isEmpty {
                        NavigationLink { MyPathCompareView() } label: {
                            Text("내 구성 바꿔서 비교해 보기").appFont(15, .bold).foregroundStyle(.white)
                                .frame(maxWidth: .infinity, minHeight: 48).background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                    }
                }

                // 3. 계획선 그래프
                Card {
                    Text(toGoal ? "목표일까지" : "시작부터 지금까지").appFont(15, .bold)
                    Canvas { ctx, size in
                        if !bandPts.isEmpty {
                            var band = linePath(bandPts.map { (X($0.0), Y($0.1)) }, in: size)
                            for q in bandPts.reversed() { band.addLine(to: CGPoint(x: X(q.0) * size.width, y: Y(q.2) * size.height)) }
                            band.closeSubpath()
                            ctx.fill(band, with: .color(Theme.teal.opacity(0.14)))
                        }
                        let gy = Y(K) * size.height
                        ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: gy)); $0.addLine(to: CGPoint(x: size.width, y: gy)) },
                                   with: .color(Theme.ink.opacity(0.6)), style: StrokeStyle(lineWidth: 1))
                        let tx = X(mS) * size.width
                        ctx.stroke(Path { $0.move(to: CGPoint(x: tx, y: 0)); $0.addLine(to: CGPoint(x: tx, y: size.height)) },
                                   with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                        ctx.stroke(linePath(needPts.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.purple), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                        ctx.stroke(linePath(actPts.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.teal), style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
                        let ty = Y(actPts.last?.1 ?? now) * size.height
                        ctx.fill(Path(ellipseIn: CGRect(x: tx - 4, y: ty - 4, width: 8, height: 8)), with: .color(Theme.teal))
                    }
                    .frame(height: 160)
                    .overlay(alignment: .topTrailing) {
                        Text("목표 \(AppModel.eok(K))").appFont(11, .bold).foregroundStyle(Theme.ink)
                            .padding(.horizontal, 6).padding(.vertical, 2).background(Theme.card.opacity(0.85), in: Capsule())
                            .offset(y: max(0, Y(K) * 160 - 18))
                    }
                    .accessibilityLabel("계획선과 실제 평가액 그래프, 목표 \(AppModel.eok(K)), 오늘 \(AppModel.eok(now))")
                    HStack {
                        Text("시작 \(m.pathStartMonth)"); Spacer(); Text("오늘 \(AppModel.eok(now))"); Spacer()
                        Text(toGoal ? "\(String(gc.year ?? 0)).\(gc.month ?? 0)" : "다음 달")
                    }
                    .appFont(11).foregroundStyle(Theme.muted)
                    HStack(spacing: 12) {
                        legend(Theme.teal, "실제"); legend(Theme.purple, "계획선")
                        if toGoal { legend(Theme.teal.opacity(0.3), "보통 범위 (10번 중 5번)") }
                    }
                    ChipRow(items: [(true, "목표일까지"), (false, "지금까지")], selection: $toGoal, fill: true)
                }

                // 4. 숫자 자세히 보기
                DisclosureGroup(isExpanded: $more) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(alignment: .firstTextBaseline) {
                            Text("계획선 대비 \(gap >= 0 ? "앞섬" : "뒤처짐") \(String(format: "%.1f", abs(gap) * 100))%").appFont(14, .semibold)
                            Spacer()
                            Text("계획선보다 \(AppModel.eokS(act(mS) - need(mS)))").appFont(13).foregroundStyle(Theme.sub)
                        }
                        if let v0 = p.pts.first?.1, v0 > 0 {
                            HStack(alignment: .firstTextBaseline) {
                                Text("시작 대비 \(AppModel.sgn((act(mS) - p.krIn) / v0 - 1))").appFont(13).foregroundStyle(Theme.sub)
                                Spacer()
                                Text("\(AppModel.eok(v0)) → \(AppModel.eok(act(mS)))").appFont(13).foregroundStyle(Theme.sub)
                            }
                        }
                        if months >= 1 {
                            ForEach(max(1, months - 5)...months, id: \.self) { k in
                                let d = Calendar.current.date(byAdding: .month, value: k, to: m.pathStartDate) ?? Date()
                                let t = d.timeIntervalSince(m.pathStartDate) / (365.25 * 86400), g = need(t) > 0 ? act(t) / need(t) - 1 : 0
                                SignedBar(label: "\(k)달째", value: AppModel.eokS(act(t) - need(t)), g: g, scale: 0.2, pos: Theme.green, neg: Theme.orange,
                                          valueColor: g >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060))
                            }
                        }
                        kpiGrid([
                            Kpi(k: "남은 기간", v: leftText, sub: m.goalToText),
                            Kpi(k: "필요 연수익률", v: AppModel.sgn(needCagr), sub: mM > 0 ? "적립 포함" : "지금 자산만으로"),
                            Kpi(k: "이번 달 넣을 돈", v: AppModel.wonK(m.gM), sub: "계획선에 들어 있음"),
                            Kpi(k: "목표 달성 확률", v: f.exact ? AppModel.pct(f.pGoal) : "계산 중", sub: "목표일에 목표 이상"),
                        ], dark: true)
                    }
                    .padding(.top, 8)
                } label: {
                    Text("숫자 자세히 보기").appFont(14, .bold).foregroundStyle(Theme.teal)
                }

                footnote("계획선은 예측이 아닌 기준선이에요. 시점과 범위는 과거 수익률로 만든 예상이에요. 목표 달성 확률은 \(m.forecastTitle)과 같은 값이에요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: 내 길 — 종목 비중 바꿔 비교 (B-2안). 저장하면 모의 바구니(담아 본 나)가 된다

struct MyPathCompareView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var w0: [String: Int] = [:]
    @State private var w: [String: Int] = [:]
    @State private var order: [String] = []
    @State private var loaded = false

    var body: some View {
        let sum = w.values.reduce(0, +)
        let yrsE = m.etaYears
        let nowSim = m.simulate(m.nowMix, years: yrsE)
        let parts: [AppModel.Part] = order.compactMap { id in
            guard let x = w[id], x > 0 else { return nil }
            let usd = m.rows.first { $0.id == id }.map { $0.sym.currency == .usd } ?? true
            return (key: SiteStats.mcKey(id), ccy: usd ? "USD" : "KRW", w: Double(x) / Double(max(1, sum)))
        }
        let newSim = w == w0 || parts.isEmpty ? nowSim : m.simulate(m.nowMix, years: yrsE, parts: parts)
        let goalMo = max(0, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400) * 12)
        let goalYear = AppModel.yearAt(goalMo)
        let ax = EtaRow.axis([nowSim, newSim], goalYear: goalYear), a0 = ax.0, a1 = ax.1
        let gc = Calendar.current.dateComponents([.year, .month], from: m.goalToDate)
        let canSave = sum == 100 && m.pworld != nil && w.filter { $0.value > 0 }.keys.allSatisfy { Parallel.has($0) }
        let headline: String = {
            if newSim.etaMonths <= goalMo { return "보통 목표일 안에 닿아요" }
            if newSim.etaMonths < nowSim.etaMonths - 0.5 { return "지금보다 빨라지지만, 보통 목표일은 넘겨요" }
            if newSim.etaMonths > nowSim.etaMonths + 0.5 { return "지금보다 늦어지고, 보통 목표일을 넘겨요" }
            return "지금과 비슷해요. 보통 목표일을 넘겨요"
        }()
        let width = newSim.etaHi.isFinite ? newSim.etaHi - newSim.etaLo : .infinity
        let spread = width > 48 ? "대신 결과 차이가 커요. 아주 빨리 닿을 수도, 한참 늦어질 수도 있어요."
            : width > 24 ? "결과 차이는 보통 수준이에요." : "결과가 비교적 고르게 모여 있어요."

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("종목 비중을 바꾸면 \(AppModel.wonK(m.gK))에 닿는 시점이 달라져요").appFont(20, .bold).fixedSize(horizontal: false, vertical: true)
                    Text("목표 \(AppModel.wonK(m.gK)) · \(m.goalToText)까지").appFont(13).foregroundStyle(Theme.sub)
                }

                Card {
                    HStack {
                        Text("내 종목 비중").appFont(15, .bold); Spacer()
                        Button("처음으로") { w = w0; order = order.filter { w0[$0] != nil } }.appFont(13, .semibold).foregroundStyle(Theme.teal)
                    }
                    GeometryReader { g in
                        HStack(spacing: 0) {
                            ForEach(order.indices, id: \.self) { i in
                                Rectangle().fill(Theme.holdColors[i % Theme.holdColors.count])
                                    .frame(width: g.size.width * Double(w[order[i]] ?? 0) / Double(max(100, sum)))
                            }
                            Spacer(minLength: 0)
                        }
                        .animation(.easeInOut(duration: 0.25), value: w)
                    }
                    .frame(height: 12).clipShape(Capsule())
                    ForEach(order.indices, id: \.self) { i in row(order[i], color: Theme.holdColors[i % Theme.holdColors.count]) }
                    Menu {
                        ForEach(Parallel.universe.filter { w[$0.id] == nil }, id: \.id) { u in
                            Button(u.id + " · " + u.name) { w[u.id] = 0; order.append(u.id) }
                        }
                    } label: {
                        Text("+ 종목 추가").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 40)
                    }
                    Text(sum == 100 ? "합계 100%" : "합계 \(sum)% · 100%가 되도록 맞춰 주세요 (시점은 비율대로 계산했어요)")
                        .appFont(13, .bold).foregroundStyle(sum == 100 ? Theme.teal : Color(hex: 0x8A4614, dark: 0xE8A060))
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background((sum == 100 ? Theme.teal : Theme.orange).opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                }

                Card {
                    Text("\(AppModel.wonK(m.gK))에 닿는 시점").appFont(15, .bold)
                    EtaTicks(a0: a0, a1: a1)
                    EtaRow(name: "지금 비중", sub: "", sim: nowSim, a0: a0, a1: a1, goalYear: goalYear, strong: false)
                    EtaRow(name: "바꾼 비중", sub: "", sim: newSim, a0: a0, a1: a1, goalYear: goalYear, strong: true)
                    Text("점선 = 목표일 \(String(gc.year ?? 0)).\(gc.month ?? 0) · 점은 보통, 막대는 10번 중 8번").appFont(11).foregroundStyle(Theme.muted)
                }

                Card {
                    Text(headline).appFont(16, .bold)
                    Text(spread).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    m.saveBasket(w.filter { $0.value > 0 }.mapValues { Double($0) })
                    dismiss()
                } label: {
                    Text("이 비중으로 계획 저장").appFont(16, .bold).foregroundStyle(canSave ? Color.white : Theme.sub)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(canSave ? Theme.teal : Theme.border, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain).disabled(!canSave)
                Text(m.pworld == nil ? "평행 세계를 열면 이 비중을 모의 바구니로 저장할 수 있어요." : "저장하면 모의 바구니(담아 본 나)가 오늘부터 이 비중으로 달려요. 실제 수량은 그대로예요.")
                    .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                Text("과거 수익률로 만든 예상이에요. 투자 권유가 아니에요.").appFont(12).foregroundStyle(Theme.muted)
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationTitle("비중 바꿔 비교").navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            let t = max(1, m.rows.reduce(0) { $0 + $1.value })
            var x: [String: Int] = [:]
            for r in m.rows { x[r.id, default: 0] += Int((r.value / t * 100).rounded()) }
            if let top = x.max(by: { $0.value < $1.value })?.key { x[top, default: 0] += 100 - x.values.reduce(0, +) }
            order = m.rows.map(\.id).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
            w0 = x; w = x
        }
    }

    private func row(_ id: String, color: Color) -> some View {
        let r = m.rows.first { $0.id == id }
        let name = r?.sym.name ?? Parallel.universe.first { $0.id == id }?.name ?? id
        let kind = r?.sym.sector ?? "새로 담음"
        let d = (w[id] ?? 0) - (w0[id] ?? 0)
        return HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 12, height: 12)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).appFont(14, .bold).lineLimit(1)
                Text("\(kind) · \(d == 0 ? "그대로" : d > 0 ? "+\(d)%p" : "\(d)%p")").appFont(11).foregroundStyle(Theme.muted).lineLimit(1)
            }
            Spacer()
            Button { w[id] = max(0, (w[id] ?? 0) - 5) } label: { Image(systemName: "minus").frame(width: 36, height: 36) }
                .buttonStyle(.bordered).accessibilityLabel("\(name) 비중 5% 줄이기")
            Text("\(w[id] ?? 0)%").appFont(14, .bold).frame(width: 44)
            Button { w[id] = min(100, (w[id] ?? 0) + 5) } label: { Image(systemName: "plus").frame(width: 36, height: 36) }
                .buttonStyle(.bordered).accessibilityLabel("\(name) 비중 5% 늘리기")
        }
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
    @State private var oil = 10.0
    @State private var gold = 10.0

    /// 시장 민감도(베타): 사이트가 웹과 같은 식(factorBetas)으로 매일 낸 값. 사이트 자료가 없는 종목(직접 추가한 종목)은
    /// 같은 회귀를 앱이 가진 지난 3년 종가로 한다. 둘 다 없으면 nil
    static func mktBeta(_ id: String) -> Double? { SiteCalc.shared.beta("mkt", id) ?? estBeta(id) }
    /// (시장 베타, 미국 10년 금리 +1%p 일 때 %, 달러 노출). 금리 시나리오 화면이 시장 베타를 쓴다 (웹처럼 모르면 1)
    static func coef(_ s: Symbol) -> (Double, Double, Double) {
        ((mktBeta(s.id) ?? 1), (SiteCalc.shared.beta("rate", s.id).map { SiteCalc.factorMove("rate", beta: $0, shock: 100) * 100 } ?? 0), s.currency == .usd ? 1 : 0)
    }
    static func estBeta(_ id: String) -> Double? {
        guard let h = PriceHistory.of(id), let spy = PriceHistory.of("SPY") else { return nil }
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
        guard xs.count >= 30 else { return nil }
        let mx = xs.reduce(0, +) / Double(xs.count), my = ys.reduce(0, +) / Double(ys.count)
        let cov = zip(xs, ys).reduce(0) { $0 + ($1.0 - mx) * ($1.1 - my) }, vx = xs.reduce(0) { $0 + ($1 - mx) * ($1 - mx) }
        guard vx > 0 else { return nil }
        let w = Double(xs.count) / Double(xs.count + 60)      // 웹과 같게 이력이 짧으면 1 쪽으로 당긴다
        return w * cov / vx + (1 - w)
    }

    /// 웹 renderSens 와 같은 식: 종목 가격 변화 = exp(β × 요인 변화) − 1, 원/달러는 달러 종목에 그대로
    private func move(_ id: String, _ sym: Symbol, _ v: Double) -> Double? {
        switch factor {
        case "fx": return sym.currency == .usd ? v / Market.shared.fx.last : 0
        case "mkt": return Self.mktBeta(id).map { SiteCalc.factorMove("mkt", beta: $0, shock: v) }
        case "rate": return SiteCalc.shared.beta("rate", id).map { SiteCalc.factorMove("rate", beta: $0, shock: v * 100) }
        default: return SiteCalc.shared.beta(factor, id).map { SiteCalc.factorMove(factor, beta: $0, shock: v) }
        }
    }

    var body: some View {
        let vals: [String: Double] = ["mkt": self.mkt, "rate": self.rate, "fx": self.fx, "oil": self.oil, "gold": self.gold]
        let v: Double = vals[factor] ?? 0
        let list = m.rows.map { r -> (id: String, label: String, dv: Double, known: Bool) in
            let mv = move(r.id, r.sym, v)
            return (r.id, r.sym.short, r.value * (mv ?? 0), mv != nil)
        }
        let tot = list.reduce(0.0) { $0 + $1.dv }, maxAbs = max(1, list.map { abs($0.dv) }.max() ?? 1)
        let after = m.total + tot
        let sg: (Double, String) -> String = { x, d in (x > 0 ? "+" : "") + String(format: d, x) }
        let asks: [String: String] = ["mkt": "S&P500이 얼마나 움직이면", "rate": "미국 10년 금리가 얼마나 바뀌면", "oil": "유가(WTI)가 얼마나 움직이면", "gold": "금값이 얼마나 움직이면"]
        let ask: String = asks[factor] ?? "환율이 \(Int(Market.shared.fx.last).formatted())원에서 얼마나 바뀌면"
        let vText = factor == "rate" ? sg(v, "%g") + "%p" : factor == "fx" ? sg(v, "%.0f") + "원" : sg(v, "%.0f") + "%"
        let unknown = list.filter { !$0.known }.map(\.label)
        let coef = m.rows.filter { factor == "fx" || move($0.id, $0.sym, v) != nil }.map { r -> String in
            switch factor {
            case "fx": return r.sym.short + (r.sym.currency == .usd ? " 환율만큼" : " 영향 없음")
            case "mkt": return r.sym.short + " " + String(format: "%.2f", Self.mktBeta(r.id) ?? 1) + "배"
            case "rate": return r.sym.short + " " + sg((SiteCalc.shared.beta("rate", r.id) ?? 0) * 10 * 100, "%.2f") + "%"
            default: return r.sym.short + " " + sg(SiteCalc.factorMove(factor, beta: SiteCalc.shared.beta(factor, r.id) ?? 0, shock: 10) * 100, "%.1f") + "%"
            }
        }.joined(separator: ", ")
        let miss = unknown.isEmpty ? "" : " \(unknown.joined(separator: ", "))는 지난 3년 시세가 부족해 0으로 계산했어요."
        let note = (factor == "mkt" ? "시장이 1% 움직일 때 평균 반응(베타): \(coef). 시장보다 등락이 큰 종목이 많을수록 하락 때 손실이 커져요."
            : factor == "rate" ? "10년 금리 +0.1%p 일 때 평균 반응 (시장 움직임을 뺀 값): \(coef)."
            : factor == "fx" ? "원화로 환산하면: \(coef). 미국 종목은 주가가 그대로여도 환율이 \(Int(abs(v)))원 \(v >= 0 ? "오르면" : "내리면") 원화 평가액이 \(String(format: "%.1f", abs(v) / Market.shared.fx.last * 100))% \(v >= 0 ? "늘어요." : "줄어요.")"
            : "\(factor == "oil" ? "유가" : "금값") +10% 일 때 평균 반응 (시장 움직임을 뺀 값): \(coef).") + miss
            + (factor == "fx" ? "" : " 지난 3년 일별 변화로 계산 (웹과 같은 값).")
        let events = MarketEvents.upcoming(held: m.rows.map(\.id), days: 45).sorted { $0.day < $1.day }.prefix(8)

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
                ChipRow(items: [("mkt", "미국 시장"), ("rate", "미국 금리"), ("fx", "원/달러"), ("oil", "유가"), ("gold", "금값")], selection: $factor, accent: Theme.orange, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                Card {
                    HStack { Text(ask).appFont(14); Spacer(); Text(vText).appFont(15, .bold) }
                    switch factor {
                    case "mkt": Slider(value: $mkt, in: -30...30, step: 5).tint(Theme.orange).accessibilityLabel(ask)
                    case "rate": Slider(value: $rate, in: -1.5...1.5, step: 0.25).tint(Theme.orange).accessibilityLabel(ask)
                    case "oil": Slider(value: $oil, in: -40...40, step: 5).tint(Theme.orange).accessibilityLabel(ask)
                    case "gold": Slider(value: $gold, in: -30...30, step: 5).tint(Theme.orange).accessibilityLabel(ask)
                    default: Slider(value: $fx, in: -300...300, step: 20).tint(Theme.orange).accessibilityLabel(ask)
                    }
                    Text(note).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                }
                Card(dark: true) {
                    Text("다가오는 일정 (45일)").appFont(15, .bold)
                    ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                        HStack(alignment: .top, spacing: 10) {
                            Text(MarketEvents.when(e.day)).appFont(12, .semibold).foregroundStyle(Theme.teal).frame(minWidth: 64, alignment: .leading)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(e.kind).appFont(14, .bold)
                                Text(e.held ? "\(e.sub) 비중 \(AppModel.pct(m.weight(e.target)))" : e.sub)
                                    .appFont(12).foregroundStyle(Theme.sub)
                            }
                        }
                    }
                    if events.isEmpty { Text("45일 안에 정해진 일정이 없어요.").appFont(13).foregroundStyle(Theme.sub) }
                    Text("\(m.forecastTitle)에서 \"외부 요인\"을 켜면 반영돼요.").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .task { await SiteCalc.shared.refresh() }
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
    /// 이미 가진 파일 그대로 (같은 파일이 다시 오면 화면을 다시 그리지 않는다)
    @ObservationIgnored private var raw: Data?
    /// 마지막으로 받은 날 (파일은 매달 5일에만 바뀌니 하루 한 번만 받는다)
    @ObservationIgnored private var fetchedDay: String?
    /// 금리·기대수익 슬라이드 값별 계산 결과 (넘길 때마다 식단 1000벌을 다시 계산하지 않게)
    @ObservationIgnored private var memo: [String: RateScen] = [:]

    init() {
        if let d = Store.readData(Self.name) { raw = d; file = try? JSONDecoder().decode(File.self, from: d) }
    }
    func load() async {
        let day = SiteCalc.today()
        guard !loading, fetchedDay != day || file == nil else { return }
        loading = true; defer { loading = false }
        var req = URLRequest(url: Config.data.appendingPathComponent("macro/rates_earnings.json"))
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 20   // 응답 없으면 20초 뒤 포기 (기본 60초)
        do {
            let (d, r) = try await URLSession.shared.data(for: req)
            guard (r as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            fetchedDay = day
            if d == raw, file != nil { failed = false; return }
            // 110KB JSON 풀기는 화면 밖에서
            let f = try await Task.detached(priority: .utility) { try JSONDecoder().decode(File.self, from: d) }.value
            guard f.meals?.coef.isEmpty == false else { throw URLError(.cannotParseResponse) }
            raw = d; memo = [:]
            file = f; failed = false
            Store.writeData(d, Self.name)
        } catch {
            if file == nil { failed = true }
        }
    }
    /// 슬라이드 값 (dr %p, m 연) 에서의 계산. 같은 값이면 저장해 둔 결과
    func scen(dr: Double, m: Double) -> RateScen? {
        guard let M = file?.meals else { return nil }
        let k = String(format: "%.2f|%.3f", dr, m)
        if let r = memo[k] { return r }
        let r = RateScen(coef: M.coef, g0: M.g_now, dr: dr, m: m)
        if memo.count > 64 { memo = [:] }
        memo[k] = r
        return r
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

    /// 닫힌 식에 드는 값. 평가액·원금은 종목 목록을 매번 다시 만드니 한 번만 읽는다 (식단 1000벌 × 2번 부르면 넘길 때 멈칫)
    private struct Closed { let T, sg, mu, V0, K, M: Double }
    private func closedInput(_ f: AppModel.Forecast) -> Closed {
        Closed(T: max(1.0 / 12, f.tY.last ?? 3), sg: max(0.01, f.sigma), mu: f.mu, V0: m.trackValue, K: m.keyValue, M: m.monthly * 1e4)
    }
    /// 연 기대를 shift 만큼 옮겼을 때 목표 확률. 웹처럼 전망(몬테카를로) 목표 확률에 닫힌 식의 차이만 더한다
    private static func prob(_ f: AppModel.Forecast, _ c: Closed, _ shift: Double) -> Double {
        max(0, min(1, f.pGoal + closed(c, shift) - closed(c, 0)))
    }
    private static func closed(_ c: Closed, _ shift: Double) -> Double {
        let T = c.T, sg = c.sg, g: Double = c.mu - sg * sg / 2 + shift
        let eff: Double = c.V0 + c.M * 12 * T * exp(-g * T / 2)
        guard eff > 0, c.K > 0 else { return 0 }
        let num: Double = log(eff / c.K) + g * T
        return AppModel.normCDF(num / (sg * sqrt(T)))
    }

    @ViewBuilder private func main(_ file: MacroData.File, _ M: MacroData.File.Meals) -> some View {
        let d = dr ?? macro.defaultDr, mm = mk / 100
        let r = macro.scen(dr: d, m: mm) ?? RateScen(coef: M.coef, g0: M.g_now, dr: d, m: mm)
        let f = m.forecast, kb = beta, ci = closedInput(f)
        let base = Self.prob(f, ci, 0)
        let shifts: [Double] = r.slope.map { b in kb * (b * d / 3 / 100 + mm) }
        let pb = RateScen.band(shifts.map { Self.prob(f, ci, $0) })
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
                    Text("포트폴리오 시장 민감도 β \(String(format: "%.2f", kb)) · \(m.forecastTitle) \(m.keyName) 확률 \(AppModel.pct(base)) 기준").appFont(12).foregroundStyle(Theme.muted)
                }
                footnote("예측이 아닌 \"만약\" 도구. 1960년 이후 금리와 S&P500 이익의 관계식 1000개로 계산. 이익 자료 \(file.eps_last ?? "")까지. 출처 Robert J. Shiller. 종목 추천이 아닙니다.")
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
    private var calc: SiteCalc { SiteCalc.shared }

    var body: some View {
        // 웹(종목 › 배당·세금)과 같은 식: 지난 12개월 배당락(사이트 시세에서 뽑은 날짜·금액)이 앞으로 같은 달에 (1+증가율)로 다시 온다고 본 세후 원화
        // 환율은 웹 fxBase 와 같게 매매기준율 (없거나 5일 넘게 묵었으면 지금 환율)
        let t = SiteCalc.today(), fx = Market.shared.valFx
        let per = m.rows.map { r -> (id: String, out: SiteCalc.DivOut?, known: Bool, tax: Double) in
            let d = calc.div(r.id), krw = r.sym.currency == .krw
            let o = d.flatMap { SiteCalc.project($0.events, shares: r.h.qty, fx: krw ? 1 : fx, krw: krw, price: r.sym.last, today: t) }
            return (r.id, o, d != nil, krw ? SiteCalc.krDivTax : SiteCalc.wht)
        }
        let paid = per.filter { $0.out != nil }
        let net = paid.reduce(0.0) { $0 + $1.out!.y1 }, gross = paid.reduce(0.0) { $0 + $1.out!.y1 / (1 - $1.tax) }
        let net3 = paid.reduce(0.0) { $0 + $1.out!.y3 }
        let months = (1...12).map { String(SiteCalc.addMonths(t, $0).prefix(7)) }
        let byMonth = months.map { mo in paid.reduce(0.0) { $0 + ($1.out!.byMonth[mo] ?? 0) } }
        let mMax = max(1, byMonth.max() ?? 1), sMax = max(1, paid.map { $0.out!.y1 }.max() ?? 1)
        let zero = per.filter { $0.known && $0.out == nil }.map(\.id)
        let unknown = per.filter { !$0.known }.map(\.id)
        // 해외주식 평가 이익: 웹과 같게 (지금 가격 − 매수 단가) × 수량 × 매매기준율. 매수 단가를 넣은 종목만
        let us = m.rows.filter { $0.sym.currency == .usd && $0.h.avg > 0 }
        let usU = us.reduce(0.0) { $0 + ($1.sym.last - $1.h.avg) * $1.h.qty * fx }
        let G = m.taxGain * 1e4

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
                                Text("\(Int(months[i].suffix(2)) ?? 0)").appFont(10).foregroundStyle(Theme.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .frame(height: 100, alignment: .bottom)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("달마다 배당: " + months.indices.filter { byMonth[$0] > 0 }.map { "\(Int(months[$0].suffix(2)) ?? 0)월 \(AppModel.won1(byMonth[$0]))" }.joined(separator: ", "))
                } else {
                    ForEach(paid, id: \.id) { x in
                        HStack(spacing: 8) {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(x.id).appFont(13, .semibold)
                                Text("\(x.out!.n)회 · \(String(format: "%.2f", x.out!.yld * 100))%").appFont(10).foregroundStyle(Theme.muted)
                            }.frame(minWidth: 64, alignment: .leading)
                            GeometryReader { g in
                                RoundedRectangle(cornerRadius: 3).fill(Theme.teal).frame(width: g.size.width * x.out!.y1 / sMax, height: 12)
                                    .frame(maxHeight: .infinity)
                            }.frame(height: 16)
                            Text(AppModel.won1(x.out!.y1)).appFont(13, .bold).frame(minWidth: 70, alignment: .trailing)
                        }
                    }
                    if paid.isEmpty { Text("지난 12개월 배당·이자를 준 종목이 없어요.").appFont(13).foregroundStyle(Theme.sub) }
                }
                Text("세전 \(AppModel.won1(gross)) · 월평균 \(AppModel.won1(net / 12)) · 평가액의 \(String(format: "%.2f", net / max(1, m.total) * 100))% · 3년 합계 \(AppModel.won1(net3))")
                    .appFont(12).foregroundStyle(Theme.sub)
                ChipRow(items: [("month", "달마다"), ("stock", "종목별")], selection: $mode, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                if net < 10e4 {
                    Text("지금 구성은 배당이 적어요." + (zero.isEmpty ? "" : " \(zero.joined(separator: ", "))는 배당을 주지 않아요.") + (unknown.isEmpty ? "" : " \(unknown.joined(separator: ", "))는 사이트 시세 자료가 없어 배당을 0으로 계산했어요.") + " 이 구성에서는 배당보다 가격 움직임이 평가액을 더 크게 바꿔요.")
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
                    ruleRow("미국 종목 평가 이익", us.isEmpty ? "매수 단가를 넣은 미국 종목이 없어요" : us.map(\.id).joined(separator: ", ") + (Market.shared.marRate != nil ? " · 매매기준율 기준" : " · 지금 환율 기준"), AppModel.manS(usU),
                            color: Theme.change(usU))
                    ruleRow("내년 5월 낼 세금", "지금 그대로 / 미국 종목 다 판다면", AppModel.man(SiteCalc.capGainTax(G)) + " / " + AppModel.man(SiteCalc.capGainTax(G + usU)))
                    Text(usU < 0
                         ? "손실은 확정된 해의 이익과 합쳐 계산돼요. 위 '다 판다면' 줄은 계산 예시이고, 매매 권유가 아니에요."
                         : "이익 250만원까지는 세금이 없고, 넘는 부분에 22%(지방세 포함)를 내요. 국내 상장주식은 대주주가 아니면 팔 때 양도세가 없어요(거래세만).")
                        .appFont(12).foregroundStyle(Theme.sub).lineSpacing(2)
                }
                footnote("지난 12개월 배당이 앞으로도 같은 달에, 지난 3년 증가율만큼 늘며 온다고 본 값이에요 (웹과 같은 계산). 세무 상담이 아니에요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .task { await calc.refresh() }
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
    @State private var what = Market.shared.valFx.rounded()

    var body: some View {
        // 웹(전망 › 환율 영향)과 같은 자료·식: 지난 원/달러와 칼만 추세는 사이트 값(calc.json), 앞날 범위는 웹 전망 모형의 환율 μ·σ(stats.json)로
        // 지금·exp((μ − σ²/2)t + zσ√t)
        // 원/달러 표시와 앞날 범위는 웹처럼 지금 환율, 평가액 계산은 웹 fxBase 와 같게 매매기준율(BASE) 기준
        let FX = Market.shared.fx.last, BASE = Market.shared.valFx, c = SiteCalc.shared.file?.fx, fxp = SiteStats.fx(), mu = fxp.0, vol = fxp.1
        let usd = m.rows.filter { $0.sym.currency == .usd }
        let usdV = usd.reduce(0) { $0 + $1.value }, usdW = usdV / max(1, m.total)
        let n = ["1m": 22, "3m": 64, "1y": 253, "3y": 780][period] ?? 253
        let label = ["1m": "1개월", "3m": "3개월", "1y": "1년", "3y": "3년"][period] ?? "1년"
        let yh = YahooSample.history["FX"]
        let closesAll = c?.closes ?? yh?.closes ?? [], levelAll = c?.level ?? []
        let k0 = max(0, closesAll.count - n), closes = Array(closesAll[k0...]), level = levelAll.count == closesAll.count ? Array(levelAll[k0...]) : []
        let past = closes.enumerated().map { (i, v) in (closes.count > 1 ? Double(i) / Double(closes.count - 1) - 1 : 0, v) }
        let trend = level.enumerated().map { (i, v) in (level.count > 1 ? Double(i) / Double(level.count - 1) - 1 : 0, v) }
        let q = { (z: Double) in (0...20).map { i -> (Double, Double) in let t = Double(i) / 20; return (t, SiteCalc.fxAt(now: FX, t: t, z: z, mu: mu, vol: vol)) } }
        let vals = past.map(\.1) + [FX, SiteCalc.fxAt(now: FX, t: 1, z: 1.645, mu: mu, vol: vol), SiteCalc.fxAt(now: FX, t: 1, z: -1.645, mu: mu, vol: vol)]
        let pad = ((vals.max() ?? FX) - (vals.min() ?? FX)) * 0.1 + 5
        let hi = (vals.max() ?? FX) + pad, lo = (vals.min() ?? FX) - pad
        let X = { (t: Double) in t <= 0 ? (t + 1) * 0.7 : 0.7 + t * 0.3 }
        let Y = { (v: Double) in max(0, min(1, (hi - v) / (hi - lo))) }
        let valAt = { (r: Double) in m.total + usdV * (r / BASE - 1) }
        let pMin = past.map(\.1).min() ?? FX, pMax = past.map(\.1).max() ?? FX
        let chg = FX / (past.first?.1 ?? FX) - 1
        let T = max(0.01, m.goalToDate.timeIntervalSince(Date()) / (365.25 * 86400))
        let goalD = { let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "yy년 M월"; return f.string(from: m.goalToDate) }()
        let at = { (z: Double) in Int(SiteCalc.fxAt(now: FX, t: T, z: z, mu: mu, vol: vol).rounded()).formatted() }
        let eff1y = c?.ret_1y.map { usdW * $0 }
        let range1y: String = {
            guard let l = c?.lo1, let h = c?.hi1 else { return "" }
            return "1년 범위 \(Int(l.rounded()).formatted())~\(Int(h.rounded()).formatted())"
        }()
        let trendSub: String = c.map { "추세 \(Int($0.kalman_level.rounded()).formatted())원 · 기울기 연 \(AppModel.sgn($0.slope_ann ?? 0))" } ?? "사이트 자료를 받으면 나와요"
        let trendText: String = c?.trend?.replacingOccurrences(of: " (추세 판단 대상 아님)", with: "") ?? "-"

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
                    if trend.count > 1 { ctx.stroke(linePath(trend.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.orange), style: StrokeStyle(lineWidth: 1.8)) }
                    if past.count > 1 { ctx.stroke(linePath(past.map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round)) }
                    ctx.stroke(linePath(q(0).map { (X($0.0), Y($0.1)) }, in: size), with: .color(Theme.blue), style: StrokeStyle(lineWidth: 1.6, dash: [2, 2]))
                }
                .frame(height: 140)
                .accessibilityLabel("원/달러 지난 흐름과 앞으로 1년 범위")
                HStack { Text("\(label) 전"); Spacer(); Text("오늘"); Spacer().frame(maxWidth: 60); Text("1년 뒤") }.appFont(11).foregroundStyle(Theme.muted)
                if past.count < 2 { Text("지난 환율 자료가 없어 지금 값으로만 그렸어요").appFont(11).foregroundStyle(Theme.muted) }
                Text("검은 선: 지난 환율 · 주황 선: 추세(칼만) · 파란 띠: 앞으로 1년 25~75%(진함)·5~95%")
                    .appFont(11).foregroundStyle(Theme.sub)
                ChipRow(items: [("1m", "1개월"), ("3m", "3개월"), ("1y", "1년"), ("3y", "3년")], selection: $period, accent: Theme.blue, fill: true)
            }
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                kpiGrid([
                    Kpi(k: "지난 1년 환율 효과", v: eff1y.map { AppModel.sgn($0) } ?? "-", sub: "원화 평가액에 더해진 몫 (근사)", color: Theme.change(eff1y ?? 0)),
                    Kpi(k: "추세", v: trendText, sub: trendSub),
                    Kpi(k: "목표일 환율 중앙값", v: at(0) + "원", sub: "\(goalD) · 5~95% \(at(-1.645))~\(at(1.645))"),
                    Kpi(k: "원화 10% 강세면", v: AppModel.manS(-usdV * 0.1), sub: "주가 변동 없이 환율만", color: Theme.down),
                    Kpi(k: "1년 변화", v: c?.ret_1y.map { AppModel.sgn($0) } ?? "-", sub: range1y),
                    Kpi(k: "변동성 (연)", v: String(format: "%.1f%%", vol * 100), sub: "전망 모형 적용 값"),
                ])
                Card {
                    HStack { Text("환율이 이렇게 되면").appFont(14); Spacer(); Text("\(Int(what).formatted())원").appFont(15, .bold) }
                    Slider(value: $what, in: 1200...1600, step: 10).tint(Theme.blue).accessibilityLabel("환율")
                    Text("내 평가액 \(AppModel.man(valAt(what))) (\(AppModel.manS(valAt(what) - m.total))) · \(m.keyName)까지 \(AppModel.man(max(0, m.keyValue - valAt(what))))")
                        .appFont(13, .semibold)
                }
                Card(dark: true) {
                    Text("환율별 내 평가액 (주가는 그대로)").appFont(15, .bold)
                    ForEach([1250.0, 1300, BASE, 1450, 1500], id: \.self) { r in
                        HStack {
                            Text("\(Int(r).formatted())원" + (r == BASE ? " (지금)" : "")).appFont(13, r == BASE ? .bold : .regular)
                            Spacer()
                            Text(AppModel.man(valAt(r))).appFont(14, .bold)
                            Text("\(m.keyName)까지 \(AppModel.man(max(0, m.keyValue - valAt(r))))").appFont(12).foregroundStyle(Theme.sub).frame(minWidth: 110, alignment: .trailing)
                        }
                    }
                }
                footnote("웹과 같은 계산: 추세는 지난 환율의 칼만 추세, 앞날 범위는 3년 전망 모형의 환율 기대·흔들림\(c.map { " (\($0.date) 기준)" } ?? "").")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .task { await SiteCalc.shared.refresh() }
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
                                     "w": r.value / max(1, m.total), "vol": SiteStats.params(r.id).2])
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
    }
}
