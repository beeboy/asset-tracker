import WidgetKit
import SwiftUI

// 홈 화면 위젯 7개 (시안 '후보 11선' 기준)

private struct Big: View { let text: String; var size: CGFloat = 30
    var body: some View { Text(text).font(.system(size: size, weight: .heavy, design: .rounded)).minimumScaleFactor(0.6).lineLimit(1) } }
private struct Mid: View { let text: String
    var body: some View { Text(text).font(.system(size: 20, weight: .heavy, design: .rounded)).minimumScaleFactor(0.6).lineLimit(1) } }

// MARK: 1-A 자산 추이 (작은)
struct AssetSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    @Environment(\.widgetKind) var kind
    var body: some View {
        let t = Tint(mode: mode), hide = Store.isHidden(kind)
        VStack(alignment: .leading, spacing: 3) {
            Money { h in Big(text: h ? hiddenAmount : Fmt.eok(s.total)) }
            Text("\(Fmt.arrow(s.dayChg)) \(Fmt.pct(s.dayChg))" + (hide ? "" : " · \(Fmt.man(s.dayAmt))")).font(.system(size: 12, weight: .bold)).foregroundStyle(t.chg(s.dayChg)).lineLimit(1)
            DotSpark(values: Array(s.spark.suffix(10)), color: Palette.ink.opacity(0.9), tint: t, labels: !hide).widgetAccentable().padding(.top, 6)
            Foot(text: "10일 · \(shortTime(s.updated))")
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 3-A 미래 (작은 부채꼴)
struct FutureSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    var body: some View {
        let t = Tint(mode: mode)
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) { Label2(text: "3년 뒤 예상"); Money { h in Mid(text: h ? hiddenAmount : s.term.map { Fmt.eok($0.p50) } ?? "-") } }
                Spacer(minLength: 2)
                VStack(alignment: .trailing, spacing: 1) { Label2(text: "목표"); Mid(text: s.pGoal.map { "\(Int(($0 * 100).rounded()))%" } ?? "-").foregroundStyle(t.c(Palette.goal)).widgetAccentable() }
            }
            if let f = s.fan {
                FanChart(hist: Array(s.spark.suffix(63)), fan: f, goal: s.goal, log: true, tint: t, labels: false).padding(.top, 4)
                    .overlay(alignment: .bottomLeading) { ReloadButton() }
            } else { Spacer(); Text("앱을 열면 계산합니다").font(.caption2).foregroundStyle(Palette.ink.opacity(0.6)); Spacer(); ReloadButton() }
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 5-B 지금 채우는 블록 (작은, 한 블록 = 목표의 1/10)
// 오른쪽 10층 탑이 목표 전체, 왼쪽 격자는 지금 채우는 층을 확대한 100칸
struct BlockSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    private let cell: CGFloat = 7, gap: CGFloat = 1.6, link: CGFloat = 12, towerW: CGFloat = 16
    var body: some View {
        let t = Tint(mode: mode)
        let inBlock = s.cells % 100, block = min(s.cells / 100 + 1, 10), prevIn = s.cellsPrev / 100 == s.cells / 100 ? s.cellsPrev % 100 : (s.cellsPrev > s.cells ? 100 : 0)
        let d = s.cells - s.cellsPrev
        let side = cell * 10 + gap * 9, floorTop = CGFloat(10 - block) * (cell + gap)
        VStack(alignment: .leading, spacing: 6) {
            Label2(text: "\(block)번째 블록 채우는 중")
            HStack(spacing: 0) {
                Grid(horizontalSpacing: gap, verticalSpacing: gap) {
                    ForEach(0..<10, id: \.self) { r in
                        GridRow {
                            ForEach(0..<10, id: \.self) { c in
                                let i = r * 10 + c
                                RoundedRectangle(cornerRadius: 1.5)
                                    .fill(i < inBlock ? t.c(Palette.goal) : (i < prevIn ? t.c(Palette.dn, 0.6) : Palette.ink.opacity(0.14)))
                                    .frame(width: cell, height: cell)
                            }
                        }
                    }
                }
                .widgetAccentable()
                // 격자와 오른쪽 끝 탑 사이를 채우는 띠: 격자 전체 → 탑의 지금 층
                GeometryReader { g in
                    let w = g.size.width, m: CGFloat = 2
                    Path { p in
                        p.move(to: .init(x: m, y: 0)); p.addLine(to: .init(x: w - m, y: floorTop))
                        p.addLine(to: .init(x: w - m, y: floorTop + cell)); p.addLine(to: .init(x: m, y: side)); p.closeSubpath()
                    }
                    .fill(t.c(Palette.goal, 0.18))
                }
                .frame(minWidth: link, maxWidth: .infinity).frame(height: side)
                VStack(spacing: gap) {
                    ForEach(0..<10, id: \.self) { k in
                        let n = 10 - k // 위가 10층, 아래가 1층
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 1.5).fill(Palette.ink.opacity(0.14))
                            if n < block { RoundedRectangle(cornerRadius: 1.5).fill(t.c(Palette.goal)) }
                            if n == block {
                                RoundedRectangle(cornerRadius: 1.5).fill(t.c(Palette.goal)).frame(width: towerW * CGFloat(inBlock) / 100)
                                RoundedRectangle(cornerRadius: 1.5).stroke(t.c(Palette.goal), lineWidth: 1)
                            }
                        }
                        .frame(width: towerW, height: cell)
                    }
                }
                .widgetAccentable()
            }
            HStack(alignment: .center, spacing: 8) {
                HStack(alignment: .firstTextBaseline, spacing: 1) { Mid(text: "\(inBlock)"); Text("/100").font(.system(size: 11)).opacity(0.6) }
                Text("어제 \(d >= 0 ? "+" : "")\(d)").font(.system(size: 11, weight: .bold)).foregroundStyle(d == 0 ? Palette.ink : t.chg(Double(d))).lineLimit(1)
                Spacer(minLength: 0)
                ReloadButton()
            }
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 2-C 목표 페이스 메이커 (중간)
struct PaceMediumView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    var body: some View {
        let t = Tint(mode: mode), pace = s.pace
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Label2(text: "목표 페이스")
                    if let p = pace {
                        (Text("필요 속도보다 ") + Text(Fmt.pct(p.ahead)).foregroundColor(t.chg(p.ahead)) + Text(p.ahead >= 0 ? " 앞섬" : " 뒤처짐"))
                            .font(.system(size: 19, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.7)
                    } else { Mid(text: "목표 시작일 확인 중") }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 1) { Mid(text: String(format: "%.1f%%", s.progress * 100)); Text("D-\(Fmt.comma(s.dday))").font(.system(size: 12)).opacity(0.6) }
            }
            PaceChart(s: s, tint: t).padding(.top, 4)
                .overlay(alignment: .bottomTrailing) { ReloadButton() }
        }
        .foregroundStyle(Palette.ink)
    }
}

struct PaceChart: View {
    let s: Snapshot; let tint: Tint
    @Environment(\.widgetKind) var kind
    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height - 12
            let v0 = s.v0 ?? s.total, lo = min(v0, s.total) * 0.95, hi = s.goal * 1.02
            let span = max(1, Double((Day.date(s.goalDate) ?? Date()).timeIntervalSince(Day.date(s.start ?? Day.today) ?? Date())))
            let fNow = min(1, max(0, Date().timeIntervalSince(Day.date(s.start ?? Day.today) ?? Date()) / span))
            let X: (Double) -> CGFloat = { 6 + CGFloat($0) * (w - 20) }
            let Y: (Double) -> CGFloat = { h * (1 - CGFloat((min(max($0, lo), hi) - lo) / (hi - lo))) }
            let req: (Double) -> Double = { v0 * pow(s.goal / v0, $0) }
            ZStack(alignment: .topLeading) {
                Path { p in for i in 0...40 { let f = Double(i) / 40, pt = CGPoint(x: X(f), y: Y(req(f))); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } }
                    .stroke(tint.c(Palette.goal, 0.9), style: StrokeStyle(lineWidth: 2, dash: [5, 4]))
                Text("⚑ \(Fmt.eok(s.goal, 0))").font(.system(size: 12, weight: .bold)).position(x: w - 26, y: Y(s.goal) + 12)
                if let p = s.pace { Text("연 \(String(format: "%.1f", p.req * 100))% 필요 경로").font(.system(size: 10)).opacity(0.6).position(x: X(0.55), y: Y(req(0.55)) + 14) }
                Circle().fill(Palette.ink).frame(width: 12, height: 12).overlay(Circle().stroke(tint.c(Palette.goal), lineWidth: 3)).position(x: X(fNow), y: Y(s.total))
                Text("지금 \(Store.isHidden(kind) ? hiddenAmount : Fmt.eok(s.total))").font(.system(size: 11, weight: .bold)).position(x: X(fNow) + 42, y: Y(s.total) - 13)
                Text("시작").font(.system(size: 9.5)).opacity(0.5).position(x: 12, y: h + 8)
                Text("목표일").font(.system(size: 9.5)).opacity(0.5).position(x: w - 16, y: h - 20)
            }
        }
    }
}

// MARK: 4-B 이번 주 과녁 (중간)
struct TargetMediumView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    @Environment(\.widgetKind) var kind
    var body: some View {
        let t = Tint(mode: mode), hide = Store.isHidden(kind)
        VStack(alignment: .leading, spacing: 4) {
            if let w = s.week {
                let g = w.act / w.p50 - 1
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Label2(text: "이번 주 과녁 · 금 \(Day.md(w.f)) 마감")
                        Money { h in
                            (Text("지금 \(h ? hiddenAmount : Fmt.eok(w.act)) ").font(.system(size: 19, weight: .heavy, design: .rounded))
                             + Text("예측보다 \(Fmt.pct(g))").font(.system(size: 14, weight: .bold)).foregroundColor(t.chg(g))).lineLimit(1).minimumScaleFactor(0.7)
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 1) { Label2(text: "마감까지"); Mid(text: w.dday == 0 ? "오늘" : "D-\(w.dday)") }
                }
                RangeBar(lo: w.lo, hi: w.hi, mid: w.p50, now: w.act, tint: t).frame(height: 26).padding(.top, 10).widgetAccentable()
                HStack { Text(hide ? "예측 범위" : Fmt.eok(w.lo)); Spacer(); Text(hide ? "" : Fmt.eok(w.hi)) }.font(.system(size: 11)).opacity(0.6)
                HStack(spacing: 4) {
                    Text(s.hitText).font(.system(size: 11)).opacity(0.6)
                    ForEach(Array(s.hits.suffix(8).enumerated()), id: \.offset) { _, h in
                        Circle().fill(h.hit ? t.c(Palette.good, h.retro ? 0.4 : 1) : .clear).overlay(Circle().stroke(Palette.ink.opacity(h.retro ? 0.3 : 0.55), lineWidth: h.hit ? 0 : 1.5)).frame(width: 10, height: 10)
                    }
                    Spacer(minLength: 0)
                    ReloadButton()
                }
            } else {
                Label2(text: "이번 주 과녁")
                Spacer()
                Text("이번 주 예측은 앱이나 사이트를 열면 만들어집니다").font(.system(size: 13)).opacity(0.7)
                Spacer()
                Foot(text: shortTime(s.updated))
            }
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 6-A 오늘의 움직임 (중간)
struct MovesMediumView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    @Environment(\.widgetKind) var kind
    var body: some View {
        let t = Tint(mode: mode), ms = s.moves
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label2(text: "오늘의 움직임")
                Spacer()
                Text("\(Store.isHidden(kind) ? "" : Fmt.eok(s.total) + " ")\(Fmt.arrow(s.dayChg))\(Fmt.pct(s.dayChg))").font(.system(size: 12, weight: .heavy)).foregroundStyle(t.chg(s.dayChg))
            }
            GeometryReader { g in
                HStack(spacing: 4) {
                    if let a = ms.first { tile(a, t, big: true).frame(width: max(80, (g.size.width - 4) * CGFloat(a.w))) }
                    VStack(spacing: 4) {
                        if ms.count > 1 { tile(ms[1], t, big: false) }
                        HStack(spacing: 4) { ForEach(ms.dropFirst(2).prefix(3)) { m in tile(m, t, big: false) } }
                    }
                }
            }
            HStack {
                if let e = s.events.first { Text("다음: ").font(.system(size: 11)) + Text("\(e.t) \(e.k)").font(.system(size: 11, weight: .bold)) + Text(" D-\(e.dday) (\(Day.md(e.d)))").font(.system(size: 11)) }
                Spacer(minLength: 0)
                ReloadButton()
            }
            .opacity(0.85)
        }
        .foregroundStyle(Palette.ink)
    }

    private func tile(_ m: Snapshot.Move, _ t: Tint, big: Bool) -> some View {
        let a = min(1, abs(m.pct) / 0.03)
        let fill: Color = t.full ? (m.pct >= 0 ? Palette.up : Palette.dn).opacity(0.25 + a * 0.55) : Palette.ink.opacity(0.08 + a * 0.22)
        return VStack(alignment: .leading) {
            Text(m.t).font(.system(size: big ? 13 : 11, weight: .heavy)).lineLimit(1)
            Spacer(minLength: 0)
            Text("\(Fmt.arrow(m.pct))\(String(format: "%.1f", abs(m.pct) * 100))%").font(.system(size: big ? 17 : 11, weight: .heavy)).lineLimit(1).minimumScaleFactor(0.7)
        }
        .padding(.horizontal, big ? 8 : 6).padding(.vertical, 5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 9).fill(fill))
    }
}

// MARK: 3-C 미래 평가액 (큰)
struct FutureLargeView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    @Environment(\.widgetKind) var kind
    var body: some View {
        let t = Tint(mode: mode)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Label2(text: "미래 평가액 추이 · 내 관점")
                    Money { h in Big(text: h ? hiddenAmount : s.term.map { Fmt.eok($0.p50) } ?? "-") }
                    Text("목표일 예상 중앙값").font(.system(size: 12)).opacity(0.6)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) { Label2(text: "목표 확률"); Big(text: s.pGoal.map { "\(Int(($0 * 100).rounded()))%" } ?? "-").foregroundStyle(t.c(Palette.goal)).widgetAccentable() }
            }
            if let f = s.fan { FanChart(hist: Array(s.spark.suffix(63)), fan: f, goal: s.goal, tint: t).widgetAccentable() }
            else { Spacer(); Text("앱을 한 번 열면 전망을 계산합니다").font(.system(size: 13)).opacity(0.7).frame(maxWidth: .infinity); Spacer() }
            if let tm = s.term {
                HStack(spacing: 6) {
                    cell("나쁠 때 (하위 5%)", tm.p5); cell("보통", tm.p50); cell("좋을 때 (상위 5%)", tm.p95)
                }
            }
            Foot(text: "\(s.fcAsOf.map { Day.md($0) + " 종가 기준" } ?? "") · \(shortTime(s.updated))")
        }
        .foregroundStyle(Palette.ink)
    }
    private func cell(_ a: String, _ v: Double) -> some View {
        VStack(spacing: 1) { Text(a).font(.system(size: 10.5)).opacity(0.6).lineLimit(1).minimumScaleFactor(0.7); Text(Store.isHidden(kind) ? hiddenAmount : Fmt.eok(v)).font(.system(size: 15, weight: .heavy)) }
            .frame(maxWidth: .infinity).padding(.vertical, 6).background(RoundedRectangle(cornerRadius: 10).fill(Palette.ink.opacity(0.08)))
    }
}
