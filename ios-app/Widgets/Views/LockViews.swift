import WidgetKit
import SwiftUI

// 잠금 화면 위젯 4개. 잠금 화면은 늘 단색이라 굵기·명암으로만 구분한다

struct LockAssetView: View { // 1 자산 추이
    let s: Snapshot
    @Environment(\.widgetKind) var kind
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 4) {
                Text(WL.t("총자산 · 목표 {p}%", ["p": "\(Int((s.progress * 100).rounded()))"])).font(.system(size: 11, weight: .bold)).opacity(0.75).lineLimit(1)
                Spacer(minLength: 0)
                LockReload()
            }
            Money { h in Text(h ? hiddenAmount : Fmt.eok(s.total)).font(.system(size: 20, weight: .heavy, design: .rounded)).minimumScaleFactor(0.7).flip(s.total) }
            HStack(spacing: 6) {
                Text("\(Fmt.arrow(s.dayChg))\(Fmt.pct(s.dayChg))").font(.system(size: 12, weight: .bold)).flip(s.dayChg)
                Spark(values: Array(s.spark.suffix(21)), color: Color.primary, width: 1.6).frame(width: 60, height: 14).flipFade()
                LockAgo(s: s)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct LockGoalView: View { // 2 목표
    let s: Snapshot
    var body: some View {
        Gauge(value: min(1, max(0, s.progress))) {
            Text(WL.t("목표"))
        } currentValueLabel: {
            Text("\(Int((s.progress * 100).rounded()))%").font(.system(size: 15, weight: .heavy)).flip(s.progress)
        }
        .gaugeStyle(.accessoryCircularCapacity)
    }
}

struct LockFutureView: View { // 3 미래: 3년 뒤 막대 하나
    let s: Snapshot
    @Environment(\.widgetKind) var kind
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(WL.t("{term} · 목표 확률 {p}", ["term": s.termLabel, "p": s.pGoal.map { "\(Int(($0 * 100).rounded()))%" } ?? "-"])).font(.system(size: 11, weight: .bold)).opacity(0.75).lineLimit(1)
                Spacer(minLength: 0)
                LockReload()
            }
            if let f = s.fan, let k = f.p50.indices.last {
                Money { h in
                    (Text(h ? hiddenAmount : Fmt.eok(f.p50[k])).font(.system(size: 18, weight: .heavy, design: .rounded)) + Text(h ? "" : "  \(Fmt.eok(f.p25[k]))~\(Fmt.eok(f.p75[k]))").font(.system(size: 10, weight: .semibold)))
                        .lineLimit(1).minimumScaleFactor(0.7).flip(f.p50[k])
                }
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
                .frame(height: 8).flipFade()
            } else {
                Text(WL.t("앱을 열면 계산")).font(.system(size: 13, weight: .bold))
            }
        }
    }
}

struct LockTargetView: View { // 4 적중 과녁
    let s: Snapshot
    @Environment(\.widgetKind) var kind
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(WL.t("이번 주 과녁 · {hit}", ["hit": s.hitText])).font(.system(size: 11, weight: .bold)).opacity(0.75).lineLimit(1)
                Spacer(minLength: 0)
                LockReload()
            }
            if let w = s.week {
                Money { h in
                    (Text(h ? hiddenAmount : Fmt.eok(w.act)).font(.system(size: 18, weight: .heavy, design: .rounded)) + Text(h ? "" : "  " + WL.t("예측 {v}", ["v": Fmt.eok(w.p50)])).font(.system(size: 11, weight: .semibold))).lineLimit(1).flip(w.act)
                }
                GeometryReader { g in
                    let x = g.size.width * CGFloat(min(1, max(0, (w.act - w.lo) / max(1, w.hi - w.lo))))
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.primary.opacity(0.3)).frame(height: 6)
                        RoundedRectangle(cornerRadius: 3).fill(Color.primary).frame(width: 10, height: 12).offset(x: x - 5)
                    }
                }
                .frame(height: 12).flipFade()
            } else {
                Text(WL.t("이번 주 예측 대기")).font(.system(size: 13, weight: .bold))
            }
        }
    }
}

/// 잠금화면 ↻: 잠금화면은 단색이라 옅은 동그라미 위 기호만. 누르면 홈 위젯의 ↻ 와 같이 받는다
struct LockReload: View {
    var body: some View {
        let busy = Store.isReloading
        let icon = Image(systemName: busy ? "ellipsis" : "arrow.clockwise").font(.system(size: 9, weight: .bold))
            .frame(width: 16, height: 16).background(Circle().fill(Color.primary.opacity(busy ? 0.35 : 0.2)))
            .accessibilityLabel(WL.t(busy ? "받는 중" : "다시 받기"))
        #if WIDGET_EXT
        Button(intent: ReloadIntent()) { icon }.buttonStyle(.plain)
        #else
        icon
        #endif
    }
}

/// 잠금화면의 '3분 전' (자리가 좁아 출처는 뺀다. 마지막 ↻ 가 실패했으면 '받기 실패')
struct LockAgo: View {
    let s: Snapshot
    @Environment(\.entryDate) private var now
    var body: some View {
        Text(Store.fetchFailed ? WL.t("받기 실패") : Fresh.ago(s.pricedAt, now: now)).font(.system(size: 9, weight: .semibold)).opacity(0.7).lineLimit(1).fixedSize()
    }
}
