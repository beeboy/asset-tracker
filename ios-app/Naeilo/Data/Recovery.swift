import SwiftUI

// 회복 루트 계산 (시안 mk·chart 와 같은 식). 모든 설명은 이 계산값으로 만든다.

enum MissionRoute: Hashable { case m1, m1r, m2, m2r, m3, m3r, m4, m4r, nx, gp1r, g1, g1r, g3, g3r, gt, gi }

enum Basket: String, CaseIterable {
    case G, V, B, C
    var name: String { switch self { case .G: "성장 지속"; case .V: "저평가 회복"; case .B: "버팀목"; case .C: "현금" } }
    var rule: String {
        switch self {
        case .G: "PER이 평균보다 15%↑, 이익 성장으로 설명됨"
        case .V: "PER이 평균보다 10%↓, 이익 성장 5%↑"
        case .B: "PER이 평균 ±15% 안, 이익 성장 3%↑"
        case .C: ""
        }
    }
    var color: Color { switch self { case .G: Theme.green; case .V: Theme.orange; case .B: Theme.blue; case .C: Color(hex: 0xBFC6CD, dark: 0x56616C) } }

    // 분류 규칙 (tools/classify_sector_pe.py 와 같음). H = 과열, X = 세 묶음 밖
    static func of(pe: Double, pe10: Double, growth: Double) -> String {
        let x = pe / pe10
        if x >= 1.15 { return growth >= 10 && pe / growth <= 2.5 ? "G" : "H" }
        if x <= 0.90 && growth >= 5 { return "V" }
        if x > 0.85 && x < 1.15 && growth >= 3 { return "B" }
        return "X"
    }
    var industries: [(key: String, ko: String, pe: Double, pe10: Double, growth: Double, count: Int)] {
        Industries.all.filter { $0.count >= 10 && Basket.of(pe: $0.pe, pe10: $0.pe10, growth: $0.growth) == rawValue }
            .sorted { $0.count > $1.count }
    }
}

struct Plan: Identifiable {
    let id: String
    let name: String
    let tag: String
    let wt: Double          // 계획 DRNK 비중
    let sigma: Double
    let g: Double           // 보통의 연 성장 (로그)
    let mix: [Basket: Double]
    let mixText: String
    let V: Double

    /// T년 뒤 K 이상일 확률
    func prob(_ K: Double, _ T: Double) -> Double {
        guard V > 0, K > 0 else { return 0 }
        return AppModel.normCDF((log(V / K) + g * T) / (sigma * sqrt(T)))
    }
    /// T년 뒤 손익 (k = -1 나쁜 경우, 0 보통, 1 좋은 경우), 들어간 돈 C 대비
    func outcome(_ C: Double, _ T: Double, _ k: Double) -> Double {
        V * exp(g * T + k * 1.645 * sigma * sqrt(T)) / C - 1
    }
    var desc: String {
        id == "keep" ? "지금 비중 그대로 (DRNK \(AppModel.pct(wt)))" : "DRNK \(AppModel.pct(wt))로 줄이고, 줄인 돈은 \(mixText)"
    }
}

extension AppModel {
    var drnkRow: Row? { rows.first { $0.id == "DRNK" } }
    var qqqRow: Row? { rows.first { $0.id == "QQQ" } }
    var halfway: Double { total < cost ? total + (cost - total) / 2 : cost }

    var plans: [Plan] {
        let w = drnkWeight, V = total, ST = 0.58, MU = 0.09
        func mk(_ id: String, _ name: String, _ tag: String, _ cap: Double, _ s2: Double, _ r: Double, _ mu2: Double,
                _ mix: [Basket: Double], _ mixText: String) -> Plan {
            let wt = min(w, cap)
            let sg = sqrt(wt * wt * ST * ST + (1 - wt) * (1 - wt) * s2 * s2 + 2 * wt * (1 - wt) * r * ST * s2)
            let mu = wt * MU + (1 - wt) * mu2
            return Plan(id: id, name: name, tag: tag, wt: wt, sigma: sg, g: mu - sg * sg / 2, mix: mix, mixText: mixText, V: V)
        }
        return [
            mk("keep", "유지", "지금 그대로", 1, 0.22, 0.6, 0.09, [.C: 1], ""),
            mk("push", "도전", "빠른 회복 우선", 0.45, 0.27, 0.55, 0.105, [.G: 0.7, .V: 0.3], "성장 지속 7 : 저평가 회복 3"),
            mk("balance", "균형", "회복과 방어 반반", 0.35, 0.19, 0.45, 0.09, [.G: 1.0 / 3, .V: 1.0 / 3, .B: 1.0 / 3], "세 묶음에 고르게"),
            mk("guard", "방어", "덜 잃기 우선", 0.25, 0.11, 0.3, 0.07, [.V: 0.2, .B: 0.6, .C: 0.2], "버팀목 6 : 저평가 회복 2 : 현금 2"),
        ]
    }
    var keepPlan: Plan { plans[0] }
    var selectedPlan: Plan { plans.first { $0.id == planKey } ?? plans[2] }
    var horizonLabel: String { horizon == 12 ? "1년" : "\(horizon)개월" }

    // 미션 2: 손실이 어디서 왔나
    var lossShareDRNK: Double {
        let lt = (drnkRow.map { $0.value - $0.cost } ?? 0), lq = (qqqRow.map { $0.value - $0.cost } ?? 0)
        let tot = lt + lq
        return tot < 0 ? max(0, min(1, lt / tot)) : 0
    }
    var quizRight: String { drnkWeight > 0.5 && lossShareDRNK > 0.6 ? "conc" : "mkt" }

    // 미션 3 결과: 옮길 금액 (QQQ 는 그대로, 줄인 DRNK 금액을 묶음에 나눔)
    var freedAmount: Double { max(0, (drnkRow?.value ?? 0) - selectedPlan.wt * total) }

    // 절세 (대한민국 거주자: 해외주식 이익-손실 합계에서 250만원 공제 뒤 22%)
    var taxLossMan: Double {
        guard let d = drnkRow else { return 0 }
        return taxSellQty * (d.sym.last - d.h.avg) * Market.shared.fx.last / 1e4
    }
    var taxBefore: Double { max(0, taxGain - 250) * 0.22 }
    var taxAfter: Double { max(0, taxGain + taxLossMan - 250) * 0.22 }
}

// 1년 범위 그래프: 띠 = 100번 중 90번, 선 = 보통의 경우, 빨간 점선 = 본전, 회색 점선 = 손실 절반
struct PathChart: View {
    let plans: [(Plan, Color, Bool)]   // (계획, 색, 점선 여부) 뒤에 올수록 위에 그림
    let V: Double
    let C: Double
    var half: Double? = nil
    var marker: Int? = nil             // 기간 칩 위치 (개월)

    var body: some View {
        Canvas { ctx, size in
            let W = size.width, H = size.height, ymax = max(C * 1.2, V * 1.9)
            let X = { (t: Double) in t / 12 * W }
            let Y = { (v: Double) in H - min(v, ymax) / ymax * H }
            func val(_ p: Plan, _ t: Double, _ k: Double) -> Double { V * exp(p.g * t / 12 + k * 1.645 * p.sigma * sqrt(t / 12)) }
            for x in [3.0, 6.0] {
                ctx.stroke(Path { $0.move(to: CGPoint(x: X(x), y: 0)); $0.addLine(to: CGPoint(x: X(x), y: H)) }, with: .color(Theme.border), lineWidth: 1)
            }
            let ts = stride(from: 0.0, through: 12, by: 0.5).map { $0 }
            for (p, c, dashed) in plans {
                var band = Path()
                for (i, t) in ts.enumerated() { let pt = CGPoint(x: X(t), y: Y(val(p, t, 1))); i == 0 ? band.move(to: pt) : band.addLine(to: pt) }
                for t in ts.reversed() { band.addLine(to: CGPoint(x: X(t), y: Y(val(p, t, -1)))) }
                band.closeSubpath()
                ctx.fill(band, with: .color(c.opacity(dashed ? 0.12 : 0.18)))
            }
            ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: Y(C))); $0.addLine(to: CGPoint(x: W, y: Y(C))) },
                       with: .color(Color(hex: 0xC8352E)), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            if let half {
                ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: Y(half))); $0.addLine(to: CGPoint(x: W, y: Y(half))) },
                           with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1, dash: [2, 4]))
            }
            if let marker {
                ctx.stroke(Path { $0.move(to: CGPoint(x: X(Double(marker)), y: 0)); $0.addLine(to: CGPoint(x: X(Double(marker)), y: H)) },
                           with: .color(Theme.ink), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
            }
            for (p, c, dashed) in plans {
                var med = Path()
                for (i, t) in ts.enumerated() { let pt = CGPoint(x: X(t), y: Y(val(p, t, 0))); i == 0 ? med.move(to: pt) : med.addLine(to: pt) }
                ctx.stroke(med, with: .color(c), style: StrokeStyle(lineWidth: dashed ? 2 : 2.5, dash: dashed ? [3, 3] : []))
            }
        }
        .accessibilityLabel("1년 평가액 범위와 본전선")
    }
}
