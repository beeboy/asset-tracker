import SwiftUI

// naeilo를 쓴 날부터의 내 기록 (종목 탭): 연 날마다 남긴 실제 평가액과 넣은 돈.
// 위 '지금 구성으로 본' 그래프(지금 수량 × 지난 가격)와 따로 보여 준다
struct MyRecordCard: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let D = m.isSample ? [] : m.myRecord
        if let L = D.last, let first = D.first {
            let gain = L.c > 0 ? L.v / L.c - 1 : nil
            Card {
                head(D)
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

    private func head(_ D: [DayRec]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("naeilo를 쓴 날부터의 내 기록").appFont(15, .bold)
            if let f = D.first { Text("\(Day.md(f.d))부터 · \(D.count)일 기록").appFont(12).foregroundStyle(Theme.muted) }
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
