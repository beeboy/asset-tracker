import WidgetKit
import SwiftUI

// 잠금 화면 위젯 4개. 잠금 화면은 늘 단색이라 굵기·명암으로만 구분한다

struct LockAssetView: View { // 1 자산 추이
    let s: Snapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("총자산 · 목표 \(Int((s.progress * 100).rounded()))%").font(.system(size: 11, weight: .bold)).opacity(0.75)
            Text(Fmt.eok(s.total)).font(.system(size: 20, weight: .heavy, design: .rounded)).minimumScaleFactor(0.7)
            HStack(spacing: 6) {
                Text("\(Fmt.arrow(s.dayChg))\(Fmt.pct(s.dayChg))").font(.system(size: 12, weight: .bold))
                Spark(values: Array(s.spark.suffix(21)), color: Color.primary, width: 1.6).frame(width: 60, height: 14)
                Text("1달").font(.system(size: 9)).opacity(0.7)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct LockGoalView: View { // 2 목표
    let s: Snapshot
    var body: some View {
        Gauge(value: min(1, max(0, s.progress))) {
            Text("목표")
        } currentValueLabel: {
            Text("\(Int((s.progress * 100).rounded()))%").font(.system(size: 15, weight: .heavy))
        }
        .gaugeStyle(.accessoryCircularCapacity)
    }
}

struct LockFutureView: View { // 3 미래: 3년 뒤 막대 하나
    let s: Snapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("3년 뒤 · 목표 확률 \(s.pGoal.map { "\(Int(($0 * 100).rounded()))%" } ?? "-")").font(.system(size: 11, weight: .bold)).opacity(0.75)
            if let f = s.fan, let k = f.p50.indices.last {
                (Text(Fmt.eok(f.p50[k])).font(.system(size: 18, weight: .heavy, design: .rounded)) + Text("  \(Fmt.eok(f.p25[k]))~\(Fmt.eok(f.p75[k]))").font(.system(size: 10, weight: .semibold)))
                    .lineLimit(1).minimumScaleFactor(0.7)
                GeometryReader { g in
                    let lo = log(max(1, min(f.p5[k], s.goal) * 0.8)), hi = log(max(f.p95[k], s.goal) * 1.1), w = g.size.width
                    let x: (Double) -> CGFloat = { w * CGFloat((log(max(1, $0)) - lo) / (hi - lo)) }
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.primary.opacity(0.18))
                        Capsule().fill(Color.primary.opacity(0.3)).frame(width: x(f.p95[k]) - x(f.p5[k])).offset(x: x(f.p5[k]))
                        Capsule().fill(Color.primary.opacity(0.85)).frame(width: x(f.p75[k]) - x(f.p25[k])).offset(x: x(f.p25[k]))
                        Rectangle().fill(Color.primary).frame(width: 2, height: 14).offset(x: x(s.goal) - 1)
                    }
                }
                .frame(height: 8)
            } else {
                Text("앱을 열면 계산").font(.system(size: 13, weight: .bold))
            }
        }
    }
}

struct LockTargetView: View { // 4 적중 과녁
    let s: Snapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("이번 주 과녁 · 적중 \(s.hits.filter { $0 }.count)/\(s.hits.count)").font(.system(size: 11, weight: .bold)).opacity(0.75)
            if let w = s.week {
                (Text(Fmt.eok(w.act)).font(.system(size: 18, weight: .heavy, design: .rounded)) + Text("  예측 \(Fmt.eok(w.p50))").font(.system(size: 11, weight: .semibold))).lineLimit(1)
                GeometryReader { g in
                    let x = g.size.width * CGFloat(min(1, max(0, (w.act - w.lo) / max(1, w.hi - w.lo))))
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.primary.opacity(0.3)).frame(height: 6)
                        RoundedRectangle(cornerRadius: 3).fill(Color.primary).frame(width: 10, height: 12).offset(x: x - 5)
                    }
                }
                .frame(height: 12)
            } else {
                Text("이번 주 예측 대기").font(.system(size: 13, weight: .bold))
            }
        }
    }
}

struct LockAsset: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.asset", provider: Provider()) { e in LockAssetView(s: e.snap).containerBackground(for: .widget) { Color.clear } }
            .configurationDisplayName("자산 추이").description("총자산·오늘 등락").supportedFamilies([.accessoryRectangular])
    }
}
struct LockGoal: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.goal", provider: Provider()) { e in LockGoalView(s: e.snap).containerBackground(for: .widget) { Color.clear } }
            .configurationDisplayName("목표 진행").description("목표 대비 %").supportedFamilies([.accessoryCircular])
    }
}
struct LockFuture: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.future", provider: Provider()) { e in LockFutureView(s: e.snap).containerBackground(for: .widget) { Color.clear } }
            .configurationDisplayName("3년 뒤").description("3년 뒤 예상 범위").supportedFamilies([.accessoryRectangular])
    }
}
struct LockTarget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "lock.target", provider: Provider()) { e in LockTargetView(s: e.snap).containerBackground(for: .widget) { Color.clear } }
            .configurationDisplayName("이번 주 과녁").description("금요일 마감 예측과 지금").supportedFamilies([.accessoryRectangular])
    }
}

@main
struct NaeiloWidgets: WidgetBundle {
    var body: some Widget {
        AssetSmall()
        FutureSmall()
        BlockSmall()
        PaceMedium()
        TargetMedium()
        MovesMedium()
        FutureLarge()
        LockWidgets().body
    }
}

struct LockWidgets: WidgetBundle {
    var body: some Widget {
        LockAsset()
        LockGoal()
        LockFuture()
        LockTarget()
    }
}
