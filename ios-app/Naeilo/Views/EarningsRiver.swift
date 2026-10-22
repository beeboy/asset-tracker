import SwiftUI
import Observation

// 내 몫 실적: 목록 작은 막대 두 개 → 누르면 강 팝업 (시안 판16, viewBox 360×680).
// 강: 이번 분기 내 몫 매출이 물높이, 강바닥에서 실(원가·판관비·이익·연구비)이 새어 나온다. 금색 실 끝이 내 몫 순이익.
// 계산은 Data/Earnings.swift (tools/earn_calc.mjs 와 같은 답). 자료는 naeilo.com/data/earnings/{종목}.json 을 보유 종목만 하루 한 번

/// 보유 종목 실적 파일 (키 = 사이트 티커). 받은 것은 기기에 두고 다음에 먼저 쓴다
@Observable
final class EarningsFeed {
    static let shared = EarningsFeed()
    private(set) var files: [String: Earnings.File] = [:]

    /// 실적을 볼 수 있는 종목의 사이트 티커. 미국 종목만 (국내 DART 는 아직), DRNK 는 앱 안 견본
    static func key(_ sym: Symbol) -> String? {
        if sym.id == "DRNK" { return "DRNK" }
        return sym.currency == .usd ? SiteStats.key(sym.id) : nil
    }

    /// 받은 자료 → 앱 안 견본 순
    func file(_ k: String) -> Earnings.File? { k == "DRNK" ? Earnings.drnk : files[k] ?? Earnings.seed?.files[k] }

    func summary(_ sym: Symbol, qty: Double) -> Earnings.Summary? {
        guard let k = Self.key(sym) else { return nil }
        return Earnings.summary(file(k), qty: qty, fx: Market.shared.fx.last)
    }

    /// 받은 지 20시간이 안 됐으면 받지 않는다 (봇이 하루 한 번 만든다). force(당겨 갱신)면 바로
    @MainActor
    func refresh(held: Set<String>, force: Bool) async {
        let keys = Set(held.compactMap { id in Sample.symbol(id).flatMap(Self.key) }).subtracting(["DRNK"])
        for k in keys where files[k] == nil { if let f = Earnings.cached(k) { files[k] = f } }
        let now = Date().timeIntervalSince1970
        for k in keys.sorted() {
            let got = "earnGot.\(k)"
            if !force && now - Store.defaults.double(forKey: got) < 20 * 3600 { continue }
            switch await Earnings.fetch(k) {
            case .file(let f): files[k] = f; Store.defaults.set(now, forKey: got)
            case .none: files[k] = nil; Store.remove(Earnings.cacheName(k)); Store.defaults.set(now, forKey: got)
            case .failed: break
            }
        }
    }
}

enum EarnColor {
    static let water = Color(hex: 0x7FA9B8, dark: 0x3F6F80)
    static let water2 = Color(hex: 0xA9C7D2, dark: 0x2C5160)
    static let strand = Color(hex: 0xB9C6CB, dark: 0x4A5A60)
    static let ghost = Color(hex: 0xA9B3B0, dark: 0x5D6965)
    static let sprout = Color(hex: 0x6FA85A, dark: 0x8CC477)
    static let gold = Color(hex: 0xC8962E, dark: 0xE0B04A)
}

extension Earnings.Summary {
    /// 실적 나온 날: 숫자를 기다리는 중이거나 오늘 발표 (이름 옆 금색 점)
    var fresh: Bool {
        if pending != nil { return true }
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
        return announced == f.string(from: Date())
    }
}

// MARK: 목록 작은 막대

/// 폭 14 높이 26: 옅은 = 작년 같은 분기 내 몫 이익, 금색 = 이번 분기. 숫자 없음. 자료가 없으면(ETF) 짧은 줄표
struct EarnBars: View {
    let s: Earnings.Summary?
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var grow: CGFloat = 1

    var body: some View {
        Group {
            if let s {
                let a = max(0, s.lastYearNet ?? 0), b = max(0, s.net ?? 0), top = max(a, b)
                HStack(alignment: .bottom, spacing: 2) {
                    bar(top > 0 ? a / top : 0, EarnColor.ghost)
                    bar(top > 0 ? b / top : 0, EarnColor.gold)
                }
                .scaleEffect(x: 1, y: grow, anchor: .bottom)
                .onAppear {
                    guard s.fresh, !still else { return }
                    grow = 0
                    withAnimation(.easeOut(duration: 0.9).delay(0.2)) { grow = 1 }
                }
            } else {
                Capsule().fill(Theme.muted).frame(width: 8, height: 2)
            }
        }
        .frame(width: 14, height: 26, alignment: .bottom)
    }

    private func bar(_ h: Double, _ c: Color) -> some View {
        // 0(손실·자료 없음)도 바닥 2pt 는 보이게
        RoundedRectangle(cornerRadius: 1.5).fill(c).frame(width: 6, height: max(2, 26 * h))
    }
}

/// 목록 위 첫 안내 (닫으면 다시 안 뜸)
struct EarnTip: View {
    @AppStorage("earn.tipClosed") private var closed = false
    var body: some View {
        if !closed {
            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 1.5).fill(EarnColor.gold).frame(width: 6, height: 14)
                Text("금색 막대 누르면 내 몫 이익").appFont(13).foregroundStyle(Theme.sub)
                Spacer()
                Button { withAnimation(.snappy) { closed = true } } label: {
                    Image(systemName: "xmark").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.muted)
                        .frame(width: 32, height: 32).contentShape(Rectangle())
                }
                .buttonStyle(.plain).accessibilityLabel("안내 닫기")
            }
            .padding(.leading, 12).padding(.vertical, 2)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
        }
    }
}

// MARK: 강 팝업

struct EarnTarget: Identifiable { let sym: Symbol; let qty: Double; var id: String { sym.id } }

struct EarningsRiverSheet: View {
    let target: EarnTarget
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let s = EarningsFeed.shared.summary(target.sym, qty: target.qty)
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.sub)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain).accessibilityLabel("닫기")
            }
            if let s {
                EarningsRiver(name: target.sym.name, qty: target.qty, s: s)
                    .padding(.horizontal, 8).padding(.bottom, 12)
            } else {
                // 자료가 없으면 팝업을 열지 않는다 (목록이 상세로 보냄). 그사이 자료가 사라졌으면 바로 닫는다
                Color.clear.onAppear { dismiss() }
            }
        }
        .frame(maxWidth: Theme.maxWidth)
        .background(Theme.card)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

/// 강 그림. 위→아래로 한 번 흐른다(약 2초, 팝업이 열린 동안만). 동작 줄이기면 다 흐른 정지 화면
struct EarningsRiver: View {
    let name: String
    let qty: Double
    let s: Earnings.Summary
    var frozen = false   // 미리보기·그림 뽑기: 다 흐른 정지 화면
    @Environment(\.accessibilityReduceMotion) private var reduce
    private var still: Bool { reduce || frozen }
    @State private var start = Date()
    @State private var done = false

    private static let W: CGFloat = 360, H: CGFloat = 680, bed: CGFloat = 300, top: CGFloat = 110
    private static let left: CGFloat = 24, right: CGFloat = 250, total: CGFloat = 50

    var body: some View {
        // 기다리는 중이면 물이 계속 차오른다 (실 없음). 아니면 2초 흐르고 멈춘다
        let paused = still || (done && s.pending == nil)
        TimelineView(.animation(paused: paused)) { tl in
            let el = tl.date.timeIntervalSince(start)
            Canvas { ctx, size in
                let k = min(size.width / Self.W, size.height / Self.H)
                ctx.translateBy(x: (size.width - Self.W * k) / 2, y: 0)
                ctx.scaleBy(x: k, y: k)
                draw(&ctx, t: still ? 1 : min(1, el / 2), el: still ? 0 : el)
            }
        }
        .aspectRatio(Self.W / Self.H, contentMode: .fit)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            start = Date(); done = false
            Task { try? await Task.sleep(for: .seconds(2.1)); done = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11y)
    }

    private var a11y: String {
        if let p = s.pending { return "\(title). \(p) 실적 발표, 숫자를 기다리는 중" }
        var t = "\(title) \(s.months)월. "
        t += "내 몫 매출 \(Earnings.money(s.revenue ?? 0, s.currency))"
        if let y = s.revYoY { t += ", 작년보다 \(y)" }
        t += s.loss ? ". 이번 분기 내 몫 손실 \(Earnings.money(s.short, s.currency))" : ". 내 몫 순이익 \(s.netText)"
        if let w = s.netKrw { t += ", \(w)" }
        return t
    }

    private var title: String {
        String(localized: "\(name)\(TownLook.batchim(name) ? "이" : "가") 번 돈 중 내 몫")
    }

    // 0~1 구간 안에서 0~1 로 (밖은 0 또는 1)
    private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { max(0, min(1, (t - a) / (b - a))) }
    private func ease(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }

    private func text(_ ctx: inout GraphicsContext, _ s: String, _ size: CGFloat, _ w: Font.Weight, _ c: Color, _ p: CGPoint, _ a: UnitPoint, _ op: Double = 1) {
        guard op > 0 else { return }
        ctx.opacity = op
        ctx.draw(Text(s).font(.system(size: size, weight: w)).foregroundStyle(c), at: p, anchor: a)
        ctx.opacity = 1
    }

    private func draw(_ ctx: inout GraphicsContext, t: Double, el: Double) {
        let fmt = { (v: Double) in Earnings.money(v, s.currency) }
        // 제목
        text(&ctx, title, 19, .bold, Theme.ink, CGPoint(x: 24, y: 42), .leading)
        let sub = s.pending == nil ? String(localized: "지금 \(qty.formatted())주 기준 · \(s.months)월") : String(localized: "지금 \(qty.formatted())주 기준")
        text(&ctx, sub, 13, .regular, Theme.sub, CGPoint(x: 24, y: 66), .leading)

        // 물높이: 지난 4분기와 이번 분기 중 가장 큰 것이 맨 위(y 110)
        let vals = s.gauge.compactMap(\.value) + [s.revenue ?? 0]
        let maxV = max(vals.max() ?? 1, 1e-12)
        let yOf = { (v: Double) -> CGFloat in Self.bed - CGFloat(max(0, v) / maxV) * (Self.bed - Self.top) }

        // 수위표 막대 x268 (폭 4)
        ctx.fill(Path(roundedRect: CGRect(x: 268, y: Self.top - 6, width: 4, height: Self.bed - Self.top + 6), cornerRadius: 2), with: .color(EarnColor.strand))
        var marks: [(y: CGFloat, label: String, value: Double?, strong: Bool)] = s.gauge.map { (yOf($0.value ?? 0), $0.label, $0.value, $0.lastYear) }
        marks.sort { $0.y < $1.y }
        var last: CGFloat = -100
        for i in marks.indices { if marks[i].y - last < 13 { marks[i].y = last + 13 }; last = marks[i].y }   // 겹치면 아래로 13씩
        for (i, g) in s.gauge.enumerated() {
            let y0 = yOf(g.value ?? 0), m = marks.first { $0.label == g.label } ?? marks[i]
            let strong = g.lastYear && s.pending == nil   // 기다리는 중이면 '작년 같은 분기'가 다음 분기 기준이라 진하게 하지 않는다
            let c = strong ? Theme.sub : EarnColor.ghost
            ctx.fill(Path(CGRect(x: 264, y: y0 - 0.75, width: 12, height: 1.5)), with: .color(c))
            let w: Font.Weight = strong ? .bold : .regular
            text(&ctx, g.label + "  " + (g.value.map(fmt) ?? "–"), 9, w, c, CGPoint(x: 282, y: m.y), .leading)
        }

        // 기다리는 중: 물이 차오르는 중, 실 없음
        if let p = s.pending {
            let rise = still ? 0.6 : (el.truncatingRemainder(dividingBy: 4)) / 4
            let lvl = Self.bed - CGFloat(rise) * (Self.bed - Self.top) * 0.9
            water(&ctx, top: lvl, phase: el)
            let md = p.split(separator: "-").compactMap { Int($0) }
            let day = md.count == 3 ? String(localized: "\(md[1])월 \(md[2])일") : p
            text(&ctx, String(localized: "실적이 나왔어요 · \(day)"), 15, .bold, EarnColor.gold, CGPoint(x: 24, y: 350), .leading)
            text(&ctx, String(localized: "숫자가 들어오면 강이 차요"), 13, .regular, Theme.sub, CGPoint(x: 24, y: 374), .leading)
            return
        }

        // 물: 위→아래로 채워진다 (0~0.3)
        let wt = yOf(s.revenue ?? 0)
        let fill = ease(seg(t, 0, 0.3))
        ctx.drawLayer { l in
            l.clip(to: Path(CGRect(x: 0, y: 0, width: Self.W, height: wt + (Self.bed - wt) * CGFloat(fill) + 6)))
            water(&l, top: wt, phase: el)
        }
        // 지금 값 (수위표 옆): 내 몫 매출 + 작년 대비
        let op = seg(t, 0.2, 0.4)
        ctx.fill(Path(CGRect(x: 262, y: wt - 1, width: 16, height: 2)), with: .color(Theme.ink.opacity(op)))
        text(&ctx, fmt(s.revenue ?? 0), 12, .bold, Theme.ink, CGPoint(x: 256, y: wt - 12), .trailing, op)
        if let y = s.revYoY { text(&ctx, y, 10, .semibold, Theme.sub, CGPoint(x: 256, y: wt + 4), .trailing, op) }
        text(&ctx, String(localized: "내 몫 매출"), 10, .regular, Theme.sub, CGPoint(x: Self.left + 8, y: wt + 16), .leading, op)

        // 물안개: 남는 돈(이자·세금·기타). 피었다가 실 끝 전에 걷힌다 (글자 없음)
        if s.mist > 0, let rev = s.revenue, rev > 0 {
            let m = sin(.pi * seg(t, 0.3, 0.85)) * (still ? 0 : 1)
            if m > 0 {
                let r = 14 + 40 * CGFloat(min(1, s.mist / rev * 4))
                ctx.drawLayer { l in
                    l.addFilter(.blur(radius: 10))
                    l.opacity = 0.55 * m
                    for (dx, dy) in [(-50.0, 0.0), (0, -8), (52, 2)] as [(CGFloat, CGFloat)] {
                        l.fill(Path(ellipseIn: CGRect(x: 137 + dx - r, y: wt - 16 + dy - r * 0.35, width: r * 2, height: r * 0.7)), with: .color(EarnColor.water2))
                    }
                }
            }
        }

        // 실: 왼→오 원가·판관비·이익·연구비. 굵기 = 내 몫 매출 안 비율 × 50, 이익 실은 최소 3
        let rev = max(s.revenue ?? 0, 1e-12), sc = Self.total / CGFloat(rev)
        struct Strand { var k: String; var w: CGFloat; var end: CGPoint }
        var ss: [Strand] = []
        let byK = Dictionary(uniqueKeysWithValues: s.branches.map { ($0.k, $0.v) })
        let lossCut: CGFloat = s.loss ? 0.78 : 1   // 적자면 바닥까지 못 간다
        let endY = { (y: CGFloat) in Self.bed + (y - Self.bed) * lossCut }
        if let v = byK["cogs"] { ss.append(Strand(k: "cogs", w: CGFloat(v) * sc, end: CGPoint(x: 54, y: endY(470)))) }
        if let v = byK["spent"] { ss.append(Strand(k: "spent", w: CGFloat(v) * sc, end: CGPoint(x: 70, y: endY(490)))) }
        if let v = byK["sga"] { ss.append(Strand(k: "sga", w: CGFloat(v) * sc, end: CGPoint(x: 104, y: endY(500)))) }
        if !s.loss { ss.append(Strand(k: "net", w: max(3, CGFloat(s.net ?? 0) * sc), end: CGPoint(x: 160, y: 560))) }
        if let v = byK["rnd"] { ss.append(Strand(k: "rnd", w: CGFloat(v) * sc, end: CGPoint(x: 236, y: endY(520)))) }
        // 강바닥에서 나란히 붙어 나온다 (가운데 137)
        let sumW = ss.reduce(0) { $0 + $1.w }
        var x = 137 - sumW / 2
        let grow = ease(seg(t, 0.3, 0.85))
        for (i, st) in ss.enumerated() {
            let sx = x + st.w / 2; x += st.w
            let mid = Self.bed + (st.end.y - Self.bed) * 0.5
            var p = Path()
            p.move(to: CGPoint(x: sx, y: Self.bed))
            p.addCurve(to: st.end, control1: CGPoint(x: sx, y: mid), control2: CGPoint(x: st.end.x, y: mid))
            let tp = p.trimmedPath(from: 0, to: grow)
            let isLast = i == ss.count - 1
            switch st.k {
            case "net":
                // 작년 실: 마지막 구간에만 흐리게
                if let ly = s.lastYearNet, ly > 0, grow > 0.6 {
                    let gw = max(2, CGFloat(ly) * sc)
                    var g = Path()
                    g.move(to: CGPoint(x: sx - st.w / 2 - gw / 2 - 3, y: Self.bed))
                    g.addCurve(to: CGPoint(x: st.end.x - st.w / 2 - gw / 2 - 3, y: st.end.y),
                               control1: CGPoint(x: sx - st.w / 2 - gw / 2 - 3, y: mid), control2: CGPoint(x: st.end.x - st.w / 2 - gw / 2 - 3, y: mid))
                    ctx.stroke(g.trimmedPath(from: 0.6, to: grow), with: .color(EarnColor.ghost.opacity(0.7)), style: StrokeStyle(lineWidth: gw, lineCap: .round))
                }
                ctx.stroke(tp, with: .color(EarnColor.gold), style: StrokeStyle(lineWidth: st.w, lineCap: .round))
            case "rnd":
                strand(&ctx, tp, st.w, EarnColor.sprout, hollow: isLast && s.loss ? CGFloat(s.short) * sc : 0)
                if grow > 0.98 { sprout(&ctx, at: st.end, size: max(7, st.w * 0.9)) }   // 끝에 떡잎 (글자 없음)
            default:
                // 원가·판관비·쓴 돈: 끝으로 옅어지며 사라진다
                let fade = GraphicsContext.Shading.linearGradient(
                    Gradient(colors: [EarnColor.strand, EarnColor.strand.opacity(0)]),
                    startPoint: CGPoint(x: sx, y: Self.bed), endPoint: st.end)
                if isLast && s.loss {
                    strand(&ctx, tp, st.w, EarnColor.strand, hollow: CGFloat(s.short) * sc)
                } else {
                    ctx.stroke(tp, with: fade, style: StrokeStyle(lineWidth: st.w, lineCap: .butt))
                }
            }
        }

        // 실 끝 글: 금색 큰 글씨(내 몫 순이익) 또는 옅은 손실
        let to = seg(t, 0.8, 1)
        if s.loss {
            text(&ctx, fmt(-s.short), 26, .bold, Theme.muted, CGPoint(x: 24, y: 580), .leading, to)
            text(&ctx, String(localized: "이번 분기 내 몫 손실"), 13, .regular, Theme.muted, CGPoint(x: 24, y: 610), .leading, to)
        } else {
            if to > 0 {
                // 큰 금색 금액 + 옆에 원화 어림 (한 줄)
                var big = Text(s.netText).font(.system(size: 32, weight: .heavy)).foregroundStyle(EarnColor.gold)
                if let w = s.netKrw { big = big + Text("  " + w).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.sub) }
                ctx.opacity = to
                ctx.draw(big, at: CGPoint(x: 24, y: 604), anchor: .leading)
                ctx.opacity = 1
            }
            text(&ctx, String(localized: "회사가 이번 분기 내 몫으로 번 순이익"), 13, .regular, Theme.sub, CGPoint(x: 24, y: 636), .leading, to)
        }
        if let ly = s.lastYearMonths, (s.lastYearNet ?? 0) > 0, !s.loss {
            text(&ctx, String(localized: "흐린 실: \(ly)월"), 11, .regular, EarnColor.ghost, CGPoint(x: 24, y: 660), .leading, to)
        }
    }

    /// 물: 위 rect + 물결 (기다리는 중이면 물결이 움직인다)
    private func water(_ ctx: inout GraphicsContext, top: CGFloat, phase: Double) {
        let l = Self.left, r = Self.right, b = Self.bed
        var wave = Path()
        wave.move(to: CGPoint(x: l, y: b))
        wave.addLine(to: CGPoint(x: l, y: top))
        var xx = l
        while xx <= r {
            let y = top + 3 * CGFloat(sin(Double(xx - l) / 18 + phase * 1.6))
            wave.addLine(to: CGPoint(x: xx, y: y)); xx += 4
        }
        wave.addLine(to: CGPoint(x: r, y: b))
        wave.closeSubpath()
        ctx.fill(wave, with: .linearGradient(Gradient(colors: [EarnColor.water2, EarnColor.water]),
                                             startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: b)))
        // 강바닥 줄
        ctx.fill(Path(CGRect(x: l, y: b - 1, width: r - l, height: 2)), with: .color(EarnColor.water))
    }

    /// 실 하나. hollow > 0 이면 그만큼(모자란 몫)은 속 빈 테두리로 옆에 붙인다
    private func strand(_ ctx: inout GraphicsContext, _ p: Path, _ w: CGFloat, _ c: Color, hollow: CGFloat) {
        let solid = max(0, w - hollow)
        if solid > 0 {
            ctx.drawLayer { l in
                l.translateBy(x: -hollow / 2, y: 0)
                l.stroke(p, with: .color(c), style: StrokeStyle(lineWidth: solid, lineCap: .butt))
            }
        }
        if hollow > 0 {
            ctx.drawLayer { l in
                l.translateBy(x: solid / 2, y: 0)
                l.stroke(p, with: .color(c), style: StrokeStyle(lineWidth: hollow, lineCap: .butt))
                l.blendMode = .destinationOut
                l.stroke(p, with: .color(.black), style: StrokeStyle(lineWidth: max(0, hollow - 3), lineCap: .butt))
            }
        }
    }

    /// 떡잎 두 장
    private func sprout(_ ctx: inout GraphicsContext, at p: CGPoint, size: CGFloat) {
        for a in [-0.6, 0.6] {
            var l = ctx
            l.translateBy(x: p.x, y: p.y)
            l.rotate(by: .radians(a))
            l.fill(Path(ellipseIn: CGRect(x: a < 0 ? -size * 1.4 : 0, y: -size * 0.35, width: size * 1.4, height: size * 0.7)), with: .color(EarnColor.sprout))
        }
    }
}

#Preview("강 DRNK") {
    EarningsRiverSheet(target: EarnTarget(sym: Sample.symbol("DRNK")!, qty: 3))
}
