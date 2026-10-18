import WidgetKit
import SwiftUI

// 홈 화면 작은 위젯 7개 (시안 '작은 위젯 시안' 기준). 서재는 StoryWidget.swift
// 투명·틴트에서는 시스템이 한 색으로 칠하므로 색 대신 ▲▼ 기호·꽉 찬 점/빈 점·진하기로 구분한다

private struct Big: View { let text: String; var size: CGFloat = 30
    var body: some View { Text(text).font(.system(size: size, weight: .heavy, design: .rounded)).minimumScaleFactor(0.6).lineLimit(1) } }
private struct Mid: View { let text: String
    var body: some View { Text(text).font(.system(size: 20, weight: .heavy, design: .rounded)).minimumScaleFactor(0.6).lineLimit(1) } }

// MARK: 자산 추이
struct AssetSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    @Environment(\.widgetKind) var kind
    var body: some View {
        let t = Tint(mode: mode), hide = Store.isHidden(kind)
        VStack(alignment: .leading, spacing: 3) {
            Money { h in Big(text: h ? hiddenAmount : Fmt.eok(s.total)).flip(s.total) }
            Text("\(Fmt.arrow(s.dayChg)) \(Fmt.pct(s.dayChg))" + (hide ? "" : " · \(Fmt.man(s.dayAmt))")).font(.system(size: 12, weight: .bold)).foregroundStyle(t.chg(s.dayChg)).lineLimit(1).flip(s.dayChg)
            TrendLine(values: Array(s.spark.suffix(10)), color: t.chg(s.dayChg), tint: t).frame(height: 50).widgetAccentable().flipFade().padding(.top, 6)
            Spacer(minLength: 0)
            Foot(text: WL.t("10일"), s: s)
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 내 길 (실제 선 · 계획선 · 앞으로의 보통 범위)
struct PathSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    var body: some View {
        let t = Tint(mode: mode), d = WSmall.load() ?? (s.placeholder ? .sample : nil)
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) { Label2(text: WL.t("내 길")); Stamp(s: s, button: true, time: false); Stamp(s: s, button: false) }
                    if let d {
                        let g = d.gapNow(s.total)
                        Mid(text: WL.t(g >= 0 ? "앞섬 {p}" : "뒤처짐 {p}", ["p": String(format: "%.1f%%", abs(g) * 100)])).foregroundStyle(t.c(g >= 0 ? Palette.teal : Palette.orange)).widgetAccentable().padding(.top, 4).flip(g)
                    }
                }
                Spacer(minLength: 2)
                VStack(alignment: .trailing, spacing: 1) {
                    Label2(text: WL.t("달성 확률")).frame(height: 22)
                    Mid(text: s.pGoal.map { "\(Int(($0 * 100).rounded()))%" } ?? "-").padding(.top, 4).flip(s.pGoal ?? 0)
                }
            }
            if let d {
                PathChart(d: d, now: d.nowValue(s.total), fan: s.fan, goalDate: Day.date(s.goalDate), tint: t).widgetAccentable().flipFade().padding(.top, 4)
            } else { Spacer(); Text(WL.t("앱을 열면 보여요")).font(.caption2).foregroundStyle(Palette.ink.opacity(0.7)); Spacer() }
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 목표일 전망 (지금부터 목표일까지 부채꼴)
struct FutureSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    @Environment(\.widgetKind) var kind
    var body: some View {
        let t = Tint(mode: mode), up = s.term.map { s.total > 0 ? $0.p50 / s.total - 1 : 0 }
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) { Label2(text: WL.t("목표일 전망")); Money { h in Mid(text: h ? hiddenAmount : s.term.map { Fmt.eok($0.p50) } ?? "-").flip(s.term?.p50 ?? 0) } }
                Spacer(minLength: 2)
                VStack(alignment: .trailing, spacing: 1) {
                    Stamp(s: s)
                    // 지금보다 몇 % (분석 화면 목표일 전망과 같은 비교)
                    Mid(text: up.map { Fmt.pct($0, 0) } ?? "-").foregroundStyle(t.c((up ?? 0) >= 0 ? Palette.good : Palette.dn)).widgetAccentable().flip(up ?? 0)
                }
            }
            if let f = s.fan, !f.md.isEmpty {
                GoalFan(now: s.total, fan: f, goal: s.goal, end: s.termLabel, tint: t, hide: Store.isHidden(kind)).widgetAccentable().flipFade().padding(.top, 4)
            } else { Spacer(); Text(WL.t("앱을 열면 계산합니다")).font(.caption2).foregroundStyle(Palette.ink.opacity(0.7)); Spacer() }
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 지금 채우는 블록 (한 블록 = 목표의 1/10)
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
            Label2(text: WL.t("{n}번째 블록 채우는 중", ["n": "\(block)"]))
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
                .widgetAccentable().flipFade()
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
            HStack(alignment: .center, spacing: 5) {
                HStack(alignment: .firstTextBaseline, spacing: 1) { Mid(text: "\(inBlock)").flip(Double(inBlock)); Text("/100").font(.system(size: 11)).opacity(0.6) }
                Text(WL.t("어제 {d}", ["d": "\(d >= 0 ? "+" : "")\(d)"])).font(.system(size: 11, weight: .bold)).foregroundStyle(d == 0 ? Palette.ink : t.chg(Double(d))).lineLimit(1).flip(Double(d))
                Spacer(minLength: 0)
                Stamp(s: s)
            }
        }
        .foregroundStyle(Palette.ink)
    }
}

// MARK: 목표 페이스 (나 vs 모의 바구니, 같은 날 출발한 뒤 수익률)
struct PaceSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    var body: some View {
        let t = Tint(mode: mode), d = WSmall.load() ?? (s.placeholder ? .sample : nil)
        VStack(alignment: .leading, spacing: 0) {
            Label2(text: WL.t("목표 페이스"))
            if let me = d?.me, let bk = d?.basket {
                let lo = min(0, me, bk), hi = max(me, bk, lo + 0.0001)
                let at = { (r: Double) in 0.15 + 0.75 * (r - lo) / (hi - lo) }
                Text(WL.t(me >= bk ? "내가 {p} 빨라요" : "내가 {p} 느려요", ["p": String(format: "%.1f%%", abs(me - bk) * 100)]))
                    .font(.system(size: 15, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.7).padding(.top, 8).flip(me - bk)
                VStack(spacing: 8) {
                    track(WL.t("나"), me, at(me), t.c(Palette.teal), filled: true)
                    track(WL.t("모의 바구니"), bk, at(bk), t.c(Palette.orange), filled: t.full)
                }
                .widgetAccentable().flipFade().padding(.top, 10)
            } else {
                Spacer()
                Text(WL.t("앱에서 모의 바구니를 담으면 보여요")).font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.ink.opacity(0.75)).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Foot(s: s)
        }
        .foregroundStyle(Palette.ink)
    }

    /// 한 줄 트랙: 이름·수익률, 출발선에서 지금 위치까지. 투명·틴트에서는 바구니 점을 빈 점으로
    private func track(_ name: String, _ r: Double, _ f: Double, _ c: Color, filled: Bool) -> some View {
        VStack(spacing: 3) {
            HStack { Text(name).foregroundStyle(c); Spacer(); Text(Fmt.pct(r)) }.font(.system(size: 11, weight: .bold)).lineLimit(1)
            GeometryReader { g in
                let x = g.size.width * CGFloat(f)
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.ink.opacity(0.14)).frame(height: 2)
                    Capsule().fill(c.opacity(filled ? 1 : 0.6)).frame(width: x, height: 2)
                    Circle().fill(filled ? c : .clear).overlay(Circle().stroke(c, lineWidth: filled ? 0 : 2)).frame(width: 12, height: 12).offset(x: x - 6)
                }
                .frame(maxHeight: .infinity)
            }
            .frame(height: 12)
        }
    }
}

// MARK: 이번 주 과녁 (가운데 = 예측 중앙값, 바깥 원 = 예측 범위, 점 = 지금)
struct TargetSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    var body: some View {
        let t = Tint(mode: mode)
        VStack(alignment: .leading, spacing: 0) {
            if let w = s.week {
                let g = w.act / w.p50 - 1, half = w.act >= w.p50 ? w.hi - w.p50 : w.p50 - w.lo
                HStack { Label2(text: WL.t("이번 주 과녁")); Spacer(); Text(w.dday == 0 ? WL.t("오늘") : "D-\(w.dday)").font(.system(size: 12, weight: .bold)) }
                HStack(spacing: 8) {
                    TargetBoard(off: half > 0 ? max(-1, min(1, (w.act - w.p50) / half)) : 0, tint: t).flipFade()
                    VStack(alignment: .leading, spacing: 6) {
                        Text(WL.t("예측보다")).font(.system(size: 11)).opacity(0.75)
                        Mid(text: Fmt.pct(g)).foregroundStyle(t.chg(g)).flip(g)
                    }
                }
                .padding(.top, 8)
                Spacer(minLength: 0)
                HStack { Spacer(); Stamp(s: s) }
                hits(t).padding(.top, 4)
            } else {
                Label2(text: WL.t("이번 주 과녁"))
                Spacer()
                Text(WL.t("이번 주 예측은 앱이나 사이트를 열면 만들어집니다")).font(.system(size: 12)).opacity(0.75)
                Spacer()
                HStack { Spacer(); Stamp(s: s) }
            }
        }
        .foregroundStyle(Palette.ink)
    }

    /// 지난 8주 적중: 꽉 찬 점 = 맞힘, 빈 점 = 못 맞힘 (예측을 적기 전 주는 흐리게)
    private func hits(_ t: Tint) -> some View {
        let hs = Array(s.hits.suffix(8))
        return HStack(spacing: 4) {
            Text("\(hs.filter { $0.hit }.count)/\(hs.count)").font(.system(size: 10)).opacity(0.7).padding(.trailing, 2)
            ForEach(Array(hs.enumerated()), id: \.offset) { _, h in
                if h.hit { Circle().fill(t.c(Palette.good, h.retro ? 0.45 : 1)).frame(width: 9, height: 9) }
                else { Circle().stroke(Palette.ink.opacity(h.retro ? 0.35 : 0.6), lineWidth: 1).frame(width: 7, height: 7).frame(width: 9, height: 9) }
            }
        }
        .widgetAccentable()
    }
}

// MARK: 오늘의 움직임 (비중 순 히트맵: 1위 가로 띠, 2위 아래 왼쪽 정사각형, 3위·나머지 오른쪽 가로 두 칸)
struct MovesSmallView: View {
    let s: Snapshot
    @Environment(\.widgetRenderingMode) var mode
    var body: some View {
        let t = Tint(mode: mode), ms = s.moves
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    Label2(text: WL.t("오늘의 움직임"))
                    Text("\(Fmt.arrow(s.dayChg))\(Fmt.pct(s.dayChg))").font(.system(size: 12, weight: .heavy)).foregroundStyle(t.chg(s.dayChg)).flip(s.dayChg)
                }
                Spacer(minLength: 2)
                Stamp(s: s)
            }
            GeometryReader { g in
                let w = g.size.width, gap: CGFloat = 2, h1 = (g.size.height - gap) * 0.46, h2 = g.size.height - gap - h1
                VStack(alignment: .leading, spacing: gap) {
                    tile(ms.first, t, row: true, big: true).frame(width: w, height: h1)
                    HStack(spacing: gap) {
                        tile(ms.count > 1 ? ms[1] : nil, t, row: false, big: false).frame(width: h2, height: h2)
                        VStack(spacing: gap) {
                            tile(ms.count > 2 ? ms[2] : nil, t, row: true, big: false)
                            tile(rest(ms), t, row: true, big: false)
                        }
                        .frame(width: max(0, w - h2 - gap), height: h2)
                    }
                }
            }
        }
        .foregroundStyle(Palette.ink)
    }

    /// 넷째 칸: 남은 종목이 하나면 그 종목, 여럿이면 '기타 N개' (평가액 가중 등락)
    private func rest(_ ms: [Snapshot.Move]) -> Snapshot.Move? {
        let r = Array(ms.dropFirst(3))
        if r.count <= 1 { return r.first }
        let krw = r.reduce(0) { $0 + $1.krw }
        let pct = krw > 0 ? r.reduce(0) { $0 + $1.krw * $1.pct } / krw : 0
        return .init(t: WL.t("기타 {n}개", ["n": "\(r.count)"]), pct: pct, krw: krw, w: 0)
    }

    /// 히트맵 칸: 많이 움직일수록 진하다. 오르면 빨강·내리면 파랑, 투명·틴트에서는 흰색 진하기와 ▲▼ 로
    @ViewBuilder private func tile(_ m: Snapshot.Move?, _ t: Tint, row: Bool, big: Bool) -> some View {
        if let m {
            let a = min(1, abs(m.pct) / 0.03)
            let name = Text(m.t).font(.system(size: big ? 13 : 11, weight: .heavy)).lineLimit(1)
            let pct = Text("\(Fmt.arrow(m.pct))\(String(format: "%.1f", abs(m.pct) * 100))%").font(.system(size: big ? 17 : 11, weight: .heavy)).lineLimit(1).minimumScaleFactor(0.7)
            ZStack {
                RoundedRectangle(cornerRadius: 4).fill(t.full ? Palette.heatBase : Palette.ink.opacity(0.08 + a * 0.32))
                if t.full { RoundedRectangle(cornerRadius: 4).fill((m.pct >= 0 ? Palette.heatUp : Palette.heatDn).opacity(0.15 + a * 0.85)) }
                Group {
                    if row { HStack(spacing: 4) { name; Spacer(minLength: 0); pct.flip(m.pct) } }
                    else { VStack(alignment: .leading) { name; Spacer(minLength: 0); pct.flip(m.pct) }.frame(maxWidth: .infinity, alignment: .leading) }
                }
                .padding(.horizontal, 6).padding(.vertical, row ? 0 : 5)
                .foregroundStyle(t.full && a > 0.4 ? Color.white : Palette.ink)
            }
            .widgetAccentable()
        } else {
            RoundedRectangle(cornerRadius: 4).fill(Palette.ink.opacity(0.06))
        }
    }
}
