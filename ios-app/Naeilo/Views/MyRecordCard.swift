import SwiftUI

// naeilo를 쓴 날부터의 내 기록 (종목 탭): 연 날마다 남긴 실제 평가액과 넣은 돈.
// 위 '지금 구성으로 본' 그래프(지금 수량 × 지난 가격)와 따로 보여 준다
struct MyRecordCard: View {
    @Environment(AppModel.self) private var m
    /// 국내 주식 합계 기록 보기 (넣은 금액이 있을 때만 버튼이 보인다. 웹 내 기록과 같다)

    var body: some View {
        let D = m.isSample ? [] : m.myRecord, K = m.isSample ? [] : CustomSymbols.shared.krLog
        if !K.isEmpty && (m.recKr || D.isEmpty) {
            Card {
                head(D, K)
                KrRecord(log: K)
            }
        } else if let L = D.last, let first = D.first {
            let gain = L.c > 0 ? L.v / L.c - 1 : nil
            Card {
                head(D, K)
                HStack(alignment: .top, spacing: 8) {
                    cell("평가액", AppModel.man(L.v), Theme.ink)
                    cell("넣은 돈", AppModel.man(L.c), Theme.ink)
                    cell("넣은 돈 대비", gain.map(AppModel.sgn) ?? "-", gain.map(Theme.change) ?? Theme.ink)
                }
                if D.count >= 2 {
                    RecordLines(v: D.map(\.v), c: D.map(\.c)).frame(height: 110)
                    HStack(spacing: 12) {
                        legend("평가액", Theme.teal, dash: false)
                        legend("넣은 돈", Theme.muted, dash: true)
                        Spacer()
                        Text("\(Day.md(first.d)) → \(Day.md(L.d))").appFont(11).foregroundStyle(Theme.muted)
                    }
                }
                Text(D.count < 2 ? "오늘부터 쌓기 시작했어요. 내일 다시 열면 선이 그려져요."
                     : "연 날마다 남긴 실제 값이에요. 넣은 돈은 '더 샀어요·팔았어요' 금액으로 바뀌어요. 위 그래프는 지금 수량을 지난 가격에 대입한 값이라 달라요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3)
            }
        } else {
            // 아직 한 줄도 없을 때도 카드 자리는 보인다 (어디에 쌓이는지 알 수 있게)
            Card {
                Text("naeilo를 쓴 날부터의 내 기록").appFont(15, .bold)
                Text(m.isSample || m.holdings.isEmpty ? "내 종목을 넣은 날부터 하루 한 줄씩 평가액과 넣은 돈이 쌓여요. 샘플 종목은 남기지 않아요."
                     : "오늘 시세를 받으면 첫 줄이 남아요. 잠시 뒤 다시 열거나 아래로 당겨 새로 고쳐 보세요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder private func head(_ D: [DayRec], _ K: [MC.KrDay]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("naeilo를 쓴 날부터의 내 기록").appFont(15, .bold)
            if let f = D.first, !(m.recKr && !K.isEmpty) { Text("\(Day.md(f.d))부터 · \(D.count)일 기록").appFont(12).foregroundStyle(Theme.muted) }
            else if let f = K.first { Text("\(Day.md(f.d))부터 · \(K.count)번 넣음").appFont(12).foregroundStyle(Theme.muted) }
        }
        if !K.isEmpty && !D.isEmpty {
            ChipRow(items: [(false, "내 기록"), (true, "국내 주식 합계")], selection: Binding(get: { m.recKr }, set: { m.recKr = $0 }), fill: true)
        }
    }

    private func cell(_ k: String, _ v: String, _ c: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v).appFont(15, .bold).foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.6)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
    private func legend(_ t: String, _ c: Color, dash: Bool) -> some View {
        HStack(spacing: 4) {
            Path { p in p.move(to: CGPoint(x: 0, y: 4)); p.addLine(to: CGPoint(x: 14, y: 4)) }
                .stroke(c, style: StrokeStyle(lineWidth: 2, dash: dash ? [3, 2] : [])).frame(width: 14, height: 8)
            Text(t).appFont(11).foregroundStyle(Theme.sub)
        }
    }
}

/// 평가액(실선)과 넣은 돈(점선) 두 줄. 같은 세로 눈금
private struct RecordLines: View {
    let v: [Double], c: [Double]
    var body: some View {
        GeometryReader { g in
            let all = v + c, lo = all.min() ?? 0, hi = all.max() ?? 1
            let n = CGFloat(max(1, v.count - 1))
            let pt = { (i: Int, y: Double) in
                CGPoint(x: CGFloat(i) / n * g.size.width, y: (1 - CGFloat((y - lo) / max(1, hi - lo))) * (g.size.height - 4) + 2)
            }
            let line = { (ys: [Double]) in Path { p in for (i, y) in ys.enumerated() { i == 0 ? p.move(to: pt(i, y)) : p.addLine(to: pt(i, y)) } } }
            ZStack {
                line(c).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1.2, dash: [4, 3]))
                line(v).stroke(Theme.teal, style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
            }
        }
        .accessibilityHidden(true)
    }
}

/// 내 기록 '국내 주식 합계': 넣은 금액을 날짜별 선으로 (점 = 넣은 날, 넣지 않은 날은 앞 금액을 이어 그림)
private struct KrRecord: View {
    let log: [MC.KrDay]
    var body: some View {
        let today = Day.today, L = log.last!, F = log.first!
        // 날마다 (금액이 바뀐 날만 계단처럼 오르내린다. 웹과 같다)
        let xs: [String] = {
            var out: [String] = [], t = Day.date(F.d) ?? Date()
            while out.count < 4000 { let d = Day.str(t); if d > today { break }; out.append(d); t = Calendar.current.date(byAdding: .day, value: 1, to: t) ?? t.addingTimeInterval(86400) }
            return out.isEmpty ? [today] : out
        }()
        let ys = xs.map { MC.krAt(log, $0) }
        let prev = log.count > 1 ? log[log.count - 2].v : nil
        let ago = Day.date(L.d).map { Int((Date().timeIntervalSince($0) / 86400).rounded(.down)) } ?? 0
        HStack(alignment: .top, spacing: 8) {
            cell("지금 합계", AppModel.man(L.v), L.d == today ? "오늘 넣음" : "\(ago)일 전 금액")
            cell("처음 넣은 금액", AppModel.man(F.v), Day.md(F.d))
            cell("지난번 대비", prev.map { $0 > 0 ? AppModel.sgn(L.v / $0 - 1) : "-" } ?? "-", prev.map { AppModel.man($0) + " →" } ?? "한 번 넣음")
        }
        if xs.count >= 2 {
            GeometryReader { g in
                let hi = max(1, (ys.max() ?? 1) * 1.05), t0 = MC.dayNum(xs[0]), span = Double(max(1, MC.dayNum(xs[xs.count - 1]) - t0))
                let pt = { (d: String, v: Double) in CGPoint(x: CGFloat(Double(MC.dayNum(d) - t0) / span) * g.size.width, y: (1 - CGFloat(v / hi)) * (g.size.height - 6) + 3) }
                ZStack {
                    Path { p in for (i, d) in xs.enumerated() { i == 0 ? p.move(to: pt(d, ys[i])) : p.addLine(to: pt(d, ys[i])) } }
                        .stroke(Theme.teal, style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
                    ForEach(log, id: \.d) { x in
                        Circle().fill(Theme.teal).frame(width: 6, height: 6).position(pt(x.d, x.v))
                    }
                }
            }
            .frame(height: 110).accessibilityHidden(true)
            HStack { Text(Day.md(xs[0])); Spacer(); Text(Day.md(xs[xs.count - 1])) }.appFont(11).foregroundStyle(Theme.muted)
        }
        Text("넣은 금액 그대로예요 (점 = 넣은 날). 넣지 않은 날은 앞 금액을 이어 그려요. 처음 넣은 금액은 들어온 돈으로, 그 뒤 바뀐 금액은 시장 움직임으로 셉니다. 엑셀로 내보내기·불러오기는 naeilo.com 내 기록에서 해요.")
            .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
    }
    private func cell(_ k: String, _ v: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v).appFont(15, .bold).foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.6)
            Text(sub).appFont(11).foregroundStyle(Theme.muted).lineLimit(1).minimumScaleFactor(0.7)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
