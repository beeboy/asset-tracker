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

    func summary(_ sym: Symbol, qty: Double, at: Int? = nil) -> Earnings.Summary? {
        guard let k = Self.key(sym) else { return nil }
        return Earnings.summary(file(k), qty: qty, fx: Market.shared.fx.last, at: at)
    }

    /// 강 팝업 장: 옛 분기 → 이번(nil). 기다리는 중이면 마지막 발표 분기도 다 찬 강으로 한 장 더 (밀어야 보인다)
    func pages(_ sym: Symbol) -> [Int?] {
        guard let k = Self.key(sym), let f = file(k), !f.quarters.isEmpty else { return [nil] }
        let n = f.quarters.count
        return Array(0..<(f.pending == nil ? n - 1 : n)).map { Optional($0) } + [nil]
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
    static let fg = Color(hex: 0x1D2624, dark: 0xE4EBE8)
    static let muted = Color(hex: 0x6B7773, dark: 0x8C9893)
    static let screen = Color(hex: 0xFFFFFF, dark: 0x1A2220)
    static let onwater = Color(hex: 0xF4FAFB, dark: 0xE4F0F3)
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
                // 높이는 크기(절댓값) 비율. 손실(순이익<0)은 같은 높이 규칙으로 속 빈 테두리
                let a = s.lastYearNet ?? 0, b = s.net ?? 0, top = max(abs(a), abs(b))
                HStack(alignment: .bottom, spacing: 2) {
                    bar(top > 0 ? abs(a) / top : 0, EarnColor.ghost, hollow: a < 0)
                    bar(top > 0 ? abs(b) / top : 0, EarnColor.gold, hollow: b < 0)
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

    @ViewBuilder
    private func bar(_ h: Double, _ c: Color, hollow: Bool) -> some View {
        // 0(자료 없음)도 바닥 2pt 는 보이게. 손실은 채움 없이 같은 색 테두리
        let r = RoundedRectangle(cornerRadius: 1.5)
        if hollow {
            r.strokeBorder(c, lineWidth: 1.2).frame(width: 6, height: max(3, 26 * h))
        } else {
            r.fill(c).frame(width: 6, height: max(2, 26 * h))
        }
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

    @State private var page: Int = -1   // -1 = 이번(at nil), 그 밖은 분기 번호

    var body: some View {
        let feed = EarningsFeed.shared
        let s = feed.summary(target.sym, qty: target.qty)
        let pages = feed.pages(target.sym)
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.sub)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain).accessibilityLabel("닫기")
            }
            if s != nil {
                // 옆으로 밀면 지난 분기 (맨 오른쪽이 이번 분기). 장에 들어올 때마다 그 분기 강이 다시 흐른다
                TabView(selection: $page) {
                    ForEach(pages, id: \.self) { at in
                        Group {
                            if let q = feed.summary(target.sym, qty: target.qty, at: at) {
                                EarningsRiver(name: target.sym.name, qty: target.qty, s: q, now: at == nil)
                            }
                        }
                        .padding(.horizontal, 8)
                        .tag(at ?? -1)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .accessibilityAdjustableAction { d in   // 보이스오버: 위·아래로 쓸어 분기 넘기기
                    let tags = pages.map { $0 ?? -1 }
                    guard let i = tags.firstIndex(of: page) else { return }
                    let j = d == .increment ? i + 1 : d == .decrement ? i - 1 : i
                    if tags.indices.contains(j) { page = tags[j] }
                }
                if pages.count > 1 { dots(pages) }
            } else {
                // 자료가 없으면 팝업을 열지 않는다 (목록이 상세로 보냄). 그사이 자료가 사라졌으면 바로 닫는다
                Color.clear.onAppear { dismiss() }
            }
        }
        .frame(maxWidth: Theme.maxWidth)
        .background(EarnColor.screen)
        .onAppear { page = -1 }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

extension EarningsRiverSheet {
    /// 장 표시 점: 이번 분기(맨 오른쪽)는 금색
    fileprivate func dots(_ pages: [Int?]) -> some View {
        HStack(spacing: 6) {
            ForEach(pages, id: \.self) { at in
                let on = (at ?? -1) == page
                Circle().fill(on ? EarnColor.gold : EarnColor.muted.opacity(0.35)).frame(width: on ? 7 : 5, height: on ? 7 : 5)
            }
        }
        .frame(height: 20).padding(.bottom, 10)
        .accessibilityHidden(true)
    }
}

/// 강 화면 글: 영어면 $·B·K. 계산 기준(Earnings.money·krw, 웹·검사와 같은 값)은 한국어 그대로 두고 보일 때만 바꾼다
enum EarnText {
    static func money(_ v: Double, _ currency: String) -> String {
        guard AppModel.en else { return Earnings.money(v, currency) }
        let a = abs(v)
        let n = a >= 10 ? String(format: "%.0f", a.rounded()) : a >= 1 ? String(format: "%.1f", a) : String(format: "%.2f", a)
        return (v < 0 ? "−" : "") + (currency == "USD" ? "$" + n : n + " " + currency)
    }
    /// 회사 매출: 9.6B · 850M
    static func big(_ v: Double) -> String {
        let b = v / 1e9
        return b >= 10 ? Earnings.grouped(Int(b.rounded())) + "B" : b >= 1 ? String(format: "%.1fB", b) : String(format: "%.0fM", v / 1e6)
    }
    /// 원화 어림: 영어면 ≈₩8K · ≈₩1.2M
    static func krw(_ s: Earnings.Summary) -> String? {
        guard let k = s.netKrw else { return nil }
        guard AppModel.en, let n = s.net else { return k }
        let v = abs(n * Market.shared.fx.last)
        return "≈₩" + (v >= 1e6 ? String(format: "%.1fM", v / 1e6) : v >= 1e3 ? String(format: "%.0fK", v / 1e3) : String(format: "%.0f", v))
    }
    static func yoy(_ y: String) -> String { y == "작년과 같음" ? String(localized: "작년과 같음") : y }
}

/// 강 그림 (시안 판16). 물이 차고 실이 흐른 뒤 떡잎이 핀다(약 3초, 팝업이 열린 동안만). 동작 줄이기면 다 흐른 정지 화면
struct EarningsRiver: View {
    let name: String
    let qty: Double
    let s: Earnings.Summary
    var now = true       // 이번 분기 장. 지난 분기 장은 분기 이름(25.7~9)을 쓴다
    var frozen = false   // 미리보기·그림 뽑기: 다 흐른 정지 화면
    @Environment(\.accessibilityReduceMotion) private var reduce
    private var still: Bool { reduce || frozen }
    @State private var start = Date()
    @State private var done = false

    private static let W: CGFloat = 360, H: CGFloat = 680, bed: CGFloat = 300, top: CGFloat = 110
    private static let left: CGFloat = 24, right: CGFloat = 250, total: CGFloat = 50, mid: CGFloat = 175
    private static let end: Double = 2.9   // 마지막 동작(떡잎)이 끝나는 초

    var body: some View {
        // 한 번 흐르고(기다리는 중이면 물만 차오르고) 멈춘다
        let paused = still || done
        TimelineView(.animation(paused: paused)) { tl in
            let el = tl.date.timeIntervalSince(start)
            Canvas { ctx, size in
                let k = min(size.width / Self.W, size.height / Self.H)
                ctx.translateBy(x: (size.width - Self.W * k) / 2, y: 0)
                ctx.scaleBy(x: k, y: k)
                draw(&ctx, t: still ? 99 : el, el: still ? 0 : el)
            }
        }
        .aspectRatio(Self.W / Self.H, contentMode: .fit)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            let st = Date()
            start = st; done = false
            Task { try? await Task.sleep(for: .seconds(Self.end + 0.1)); if start == st { done = true } }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11y)
    }

    private var a11y: String {
        if let p = s.pending { return "\(title). \(p) 실적 발표, 숫자를 기다리는 중" }
        var t = now ? "\(title) \(s.months)월. " : "\(title) \(s.label). "
        t += "내 몫 매출 \(Earnings.money(s.revenue ?? 0, s.currency))"
        if let y = s.revYoY { t += ", 작년보다 \(y)" }
        t += s.loss ? (now ? ". 이번 분기" : ". 그 분기") + " 내 몫 손실 \(Earnings.money(s.short, s.currency))" : ". 내 몫 순이익 \(s.netText)"
        if let w = s.netKrw { t += ", \(w)" }
        return t
    }

    private var title: String {
        String(localized: "\(name)\(TownLook.batchim(name) ? "이" : "가") 번 돈 중 내 몫")
    }

    // 0~1 구간 안에서 0~1 로 (밖은 0 또는 1)
    private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { max(0, min(1, (t - a) / (b - a))) }
    /// cubic-bezier(.4,0,.2,1)
    private func ease(_ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        func bz(_ u: Double, _ p1: Double, _ p2: Double) -> Double { 3 * (1 - u) * (1 - u) * u * p1 + 3 * (1 - u) * u * u * p2 + u * u * u }
        var lo = 0.0, hi = 1.0
        for _ in 0..<20 { let m = (lo + hi) / 2; if bz(m, 0.4, 0.2) < x { lo = m } else { hi = m } }
        return bz((lo + hi) / 2, 0, 1)
    }
    /// 튀듯 커짐 (back-out)
    private func pop(_ x: Double) -> Double { let c = 1.70158; return x <= 0 ? 0 : 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2) }
    /// 글자: at 초부터 .4초 페이드
    private func fade(_ t: Double, _ at: Double) -> Double { seg(t, at, at + 0.4) }

    private func text(_ ctx: inout GraphicsContext, _ s: String, _ size: CGFloat, _ w: Font.Weight, _ c: Color, _ p: CGPoint, _ a: UnitPoint, _ op: Double = 1, serif: Bool = false) {
        guard op > 0 else { return }
        // 왼쪽 맞춤 글이 오른쪽 끝(6)을 넘으면 왼쪽으로 당기고, 그래도 넓으면 글자를 줄인다 (영어 글이 길다)
        var size = size, p = p
        if a == .leading {
            let room = Self.W - 12
            let wd = ctx.resolve(Text(s).font(.system(size: size, weight: w, design: serif ? .serif : .default))).measure(in: CGSize(width: CGFloat.infinity, height: .infinity)).width
            if wd > room { size *= room / wd; p.x = 6 } else if p.x + wd > Self.W - 6 { p.x = Self.W - 6 - wd }
        }
        ctx.opacity = op
        ctx.draw(Text(s).font(.system(size: size, weight: w, design: serif ? .serif : .default)).foregroundStyle(c), at: p, anchor: a)
        ctx.opacity = 1
    }

    /// 회사 매출 "570억" (억 단위)
    private func eok(_ v: Double) -> String {
        if AppModel.en { return EarnText.big(v) }
        let e = v / 1e8
        return (e >= 10 ? Earnings.grouped(Int(e.rounded())) : String(format: "%.1f", e)) + "억"
    }

    private func draw(_ ctx: inout GraphicsContext, t: Double, el: Double) {
        let fmt = { (v: Double) in EarnText.money(v, s.currency) }
        // 제목
        text(&ctx, title, 20, .bold, EarnColor.fg, CGPoint(x: 24, y: 42), .leading, serif: true)

        // 물높이 lv(v) = 270 − (v−min)/(max−min)×210 (지난 4분기 + 지금, 회사 매출)
        let co = s.coRevenue ?? 0
        let vals = s.gauge.map { $0.co ?? 0 } + [co]
        let lo = vals.min() ?? 0, hi = vals.max() ?? 1
        let lv = { (v: Double) -> CGFloat in hi > lo ? 270 - CGFloat((v - lo) / (hi - lo)) * 210 : 165 }
        let S = lv(co)

        // 물: rect 0,S~300 + 물결. 위→아래로 채워진다 (.6초). 기다리는 중이면 강바닥에서 지난 분기 높이까지 2초 차오른다
        let wet = s.pending == nil
        let top = wet ? S : Self.bed - (Self.bed - S) * CGFloat(ease(seg(t, 0, 2)))
        let fill = wet ? CGFloat(ease(seg(t, 0, 0.6))) : 1
        ctx.drawLayer { l in
            l.clip(to: Path(CGRect(x: 0, y: top, width: Self.W, height: (Self.bed - top) * fill)))
            l.fill(Path(CGRect(x: 0, y: top, width: Self.W, height: Self.bed - top)), with: .color(EarnColor.water))
            var w = Path()
            w.move(to: CGPoint(x: 0, y: 230))
            w.addCurve(to: CGPoint(x: 360, y: 225), control1: CGPoint(x: 90, y: 210), control2: CGPoint(x: 200, y: 250))
            w.addLine(to: CGPoint(x: 360, y: 300)); w.addLine(to: CGPoint(x: 0, y: 300)); w.closeSubpath()
            l.fill(w, with: .color(EarnColor.water2))
        }
        // 강 위 글: 물이 낮아 글이 물 밖이면 진한 글자로
        let base = min(S, 200)
        let c1 = base + 40 - 8 > S ? EarnColor.onwater : EarnColor.fg, c2 = base + 70 - 12 > S ? EarnColor.onwater : EarnColor.fg
        if let p = s.pending {
            let md = p.split(separator: "-").compactMap { Int($0) }
            let day = md.count == 3 ? String(localized: "\(md[1])월 \(md[2])일") : p
            text(&ctx, String(localized: "실적이 나왔어요 · \(day)"), 12, .regular, c1, CGPoint(x: 24, y: base + 40), .leading, 0.85 * fade(t, 1.8))
            text(&ctx, String(localized: "숫자가 들어오면 강이 차요"), 20, .bold, c2, CGPoint(x: 24, y: base + 70), .leading, fade(t, 1.8), serif: true)
            gauge(&ctx, lv: lv, S: nil, last: S, t: t)
            return
        }
        text(&ctx, now ? String(localized: "\(name) · 이번 분기") : "\(name) · \(s.label)", 12, .regular, c1, CGPoint(x: 24, y: base + 40), .leading, 0.85 * fade(t, 0.3))
        let usd = s.currency == "USD"
        let unit = AppModel.en && usd ? "" : usd ? String(localized: " 달러") : " " + s.currency
        let rv = AppModel.en && usd ? "$" + eok(co) : eok(co)   // 영어는 $9.6B
        text(&ctx, String(localized: "매출 \(rv)\(unit)"), 20, .bold, c2, CGPoint(x: 24, y: base + 70), .leading, fade(t, 0.3), serif: true)
        gauge(&ctx, lv: lv, S: S, t: t)

        // 물안개: 남는 돈(이자·세금·기타). 피었다가 실 끝 전에 걷힌다 (글자 없음)
        if s.mist > 0, let rev = s.revenue, rev > 0 {
            let m = sin(.pi * seg(t, 0.6, 1.7)) * (still ? 0 : 1)
            if m > 0 {
                let r = 14 + 40 * CGFloat(min(1, s.mist / rev * 4))
                ctx.drawLayer { l in
                    l.addFilter(.blur(radius: 10))
                    l.opacity = 0.55 * m
                    for (dx, dy) in [(-50.0, 0.0), (0, -8), (52, 2)] as [(CGFloat, CGFloat)] {
                        l.fill(Path(ellipseIn: CGRect(x: Self.mid + dx - r, y: S - 16 + dy - r * 0.35, width: r * 2, height: r * 0.7)), with: .color(EarnColor.water2))
                    }
                }
            }
        }

        // 내 몫 (강바닥 아래)
        text(&ctx, String(localized: "내 몫 \(fmt(s.revenue ?? 0))"), 12, .semibold, EarnColor.fg, CGPoint(x: 216, y: 326), .leading, fade(t, 0.4))
        text(&ctx, String(localized: "\(qty.formatted())주만큼 · 확대"), 11, .regular, EarnColor.muted, CGPoint(x: 216, y: 343), .leading, fade(t, 0.4))

        // 실: 왼→오 원가·판관비·이익·연구비. 굵기 = 내 몫 매출 안 비율 × 50, 이익 실은 최소 3. 가운데 175 에 나란히
        let rev = max(s.revenue ?? 0, 1e-12), sc = Self.total / CGFloat(rev)
        let byK = Dictionary(uniqueKeysWithValues: s.branches.map { ($0.k, $0.v) })
        var ss: [(k: String, w: CGFloat)] = []
        if let v = byK["cogs"] { ss.append(("cogs", CGFloat(v) * sc)) }
        if let v = byK["spent"] { ss.append(("spent", CGFloat(v) * sc)) }
        if let v = byK["sga"] { ss.append(("sga", CGFloat(v) * sc)) }
        if !s.loss { ss.append(("net", max(3, CGFloat(s.net ?? 0) * sc))) }
        if let v = byK["rnd"] { ss.append(("rnd", CGFloat(v) * sc)) }
        var x = Self.mid - ss.reduce(0) { $0 + $1.w } / 2
        let hollow = s.loss ? CGFloat(s.short) * sc : 0   // 적자: 마지막 실 속 빈 테두리
        var goldX = Self.mid
        for (i, st) in ss.enumerated() {
            let sx = x + st.w / 2; x += st.w
            let h = i == ss.count - 1 ? hollow : 0
            var p = Path(); p.move(to: CGPoint(x: sx, y: Self.bed))
            switch st.k {
            case "cogs", "spent":
                p.addLine(to: CGPoint(x: sx, y: 350))
                p.addCurve(to: CGPoint(x: 40, y: 440), control1: CGPoint(x: sx, y: 400), control2: CGPoint(x: 70, y: 392))
                flow(&ctx, p, st.w, fadeTo: CGPoint(x: 40, y: 440), from: sx, to: .clear, grow: ease(seg(t, 0, 1)), hollow: h)
                let label = st.k == "cogs" ? String(localized: "원가 \(fmt(byK["cogs"] ?? 0))") : String(localized: "쓴 돈 \(fmt(byK["spent"] ?? 0))")
                text(&ctx, label, 12, .regular, EarnColor.muted, CGPoint(x: 24, y: 466), .leading, fade(t, 0.7))
            case "sga":
                p.addLine(to: CGPoint(x: sx, y: 470))
                p.addCurve(to: CGPoint(x: 70, y: 540), control1: CGPoint(x: sx, y: 508), control2: CGPoint(x: 100, y: 506))
                flow(&ctx, p, st.w, fadeTo: CGPoint(x: 70, y: 540), from: sx, to: .clear, grow: ease(seg(t, 0.4, 1.7)), hollow: h)
                text(&ctx, String(localized: "판매·관리비 \(fmt(byK["sga"] ?? 0))"), 12, .regular, EarnColor.muted, CGPoint(x: 24, y: 564), .leading, fade(t, 1.4))
            case "rnd":
                p.addLine(to: CGPoint(x: sx, y: 410))
                p.addCurve(to: CGPoint(x: 320, y: 488), control1: CGPoint(x: sx, y: 450), control2: CGPoint(x: 290, y: 448))
                flow(&ctx, p, st.w, fadeTo: CGPoint(x: 320, y: 488), from: sx, to: EarnColor.sprout, grow: ease(seg(t, 0.2, 1.4)), hollow: h)
                sprout(&ctx, scale: pop(seg(t, 2.2, 2.8)))
                text(&ctx, String(localized: "연구비 \(fmt(byK["rnd"] ?? 0))"), 12, .regular, EarnColor.muted, CGPoint(x: 336, y: 512), .trailing, fade(t, 1.1))
            default:   // net: 금색 실이 곧게 내려간다
                goldX = sx
                if let ly = s.lastYearNet, ly > 0, let n = s.net, n > 0 {
                    var g = Path(); g.move(to: CGPoint(x: sx + 14, y: 520)); g.addLine(to: CGPoint(x: sx + 14, y: 600))
                    ctx.stroke(g.trimmedPath(from: 0, to: ease(seg(t, 1.5, 2.1))), with: .color(EarnColor.ghost.opacity(0.6)),
                               style: StrokeStyle(lineWidth: st.w * CGFloat(ly / n), lineCap: .butt))
                    text(&ctx, String(localized: "작년 \(fmt(ly))"), 11, .regular, EarnColor.muted, CGPoint(x: sx + 20, y: 560), .leading, fade(t, 2))
                }
                p.addLine(to: CGPoint(x: sx, y: 600))
                ctx.stroke(p.trimmedPath(from: 0, to: ease(seg(t, 0, 2))), with: .color(EarnColor.gold), style: StrokeStyle(lineWidth: st.w, lineCap: .butt))
            }
        }

        // 금색 끝: 큰 금액(+원화) · 설명. 적자면 옅은 손실
        if s.loss {
            text(&ctx, fmt(-s.short), 30, .bold, EarnColor.muted, CGPoint(x: Self.mid, y: 636), .center, fade(t, 1.9), serif: true)
            text(&ctx, now ? String(localized: "이번 분기 내 몫 손실") : String(localized: "그 분기 내 몫 손실"), 12, .regular, EarnColor.muted, CGPoint(x: Self.mid, y: 660), .center, fade(t, 2.1))
        } else {
            let op = fade(t, 1.9)
            if op > 0 {
                var big = Text(fmt(s.net ?? 0)).font(.system(size: 30, weight: .bold, design: .serif)).foregroundStyle(EarnColor.gold)
                if let w = EarnText.krw(s) { big = big + Text("  " + w).font(.system(size: 13, weight: .semibold)).foregroundStyle(EarnColor.muted) }
                ctx.opacity = op
                ctx.draw(big, at: CGPoint(x: goldX, y: 636), anchor: .center)
                ctx.opacity = 1
            }
            text(&ctx, now ? String(localized: "회사가 이번 분기 내 몫으로 번 순이익") : String(localized: "회사가 그 분기 내 몫으로 번 순이익"), 12, .regular, EarnColor.muted, CGPoint(x: goldX, y: 660), .center, fade(t, 2.1))
        }
    }

    /// 수위표: 막대 x268 y62, 눈금 x261~279. S = 지금 물높이 (기다리는 중이면 nil), last = 기다리는 중 지난 분기 높이
    private func gauge(_ ctx: inout GraphicsContext, lv: (Double) -> CGFloat, S: CGFloat?, last lastS: CGFloat? = nil, t: Double) {
        ctx.fill(Path(CGRect(x: 268, y: 62, width: 4, height: 238)), with: .color(EarnColor.muted.opacity(0.55)))
        let tick = { (ctx: inout GraphicsContext, y: CGFloat, strong: Bool) in
            ctx.fill(Path(CGRect(x: 261, y: y - (strong ? 1 : 0.75), width: 18, height: strong ? 2 : 1.5)),
                     with: .color(strong ? EarnColor.fg : EarnColor.onwater.opacity(0.6)))
        }
        var others: [(y: CGFloat, s: String)] = []
        for g in s.gauge {
            let y = lv(g.co ?? 0)
            let strong = g.lastYear
            tick(&ctx, y, strong)
            let v = g.co.map(eok) ?? "–"
            if strong { text(&ctx, String(localized: "작년 \(v)"), 11, .regular, EarnColor.muted, CGPoint(x: 284, y: y - 5), .leading) }
            else { others.append((y + 4, v)) }
        }
        others.sort { $0.y < $1.y }
        var last: CGFloat = -100
        for i in others.indices { if others[i].y - last < 13 { others[i].y = last + 13 }; last = others[i].y }   // 겹치면 앞보다 13 아래로
        for o in others { text(&ctx, o.s, 11, .regular, EarnColor.onwater, CGPoint(x: 284, y: o.y), .leading, 0.75) }
        if let S {
            tick(&ctx, S, false)
            let op = fade(t, 0.3)
            text(&ctx, now ? String(localized: "지금 \(eok(s.coRevenue ?? 0))") : String(localized: "이때 \(eok(s.coRevenue ?? 0))"), 11, .semibold, EarnColor.onwater, CGPoint(x: 284, y: S + 17), .leading, op)
            if let y = s.revYoY { text(&ctx, EarnText.yoy(y), 11, .semibold, EarnColor.onwater, CGPoint(x: 284, y: S + 31), .leading, op) }
        }
        // 기다리는 중: 지난 분기 높이 눈금. 물이 다 차오른 뒤(1.8초) 나타난다
        if let L = lastS {
            let op = fade(t, 1.8)
            ctx.opacity = op
            tick(&ctx, L, false)
            ctx.opacity = 1
            text(&ctx, String(localized: "지난 \(eok(s.coRevenue ?? 0))"), 11, .semibold, EarnColor.onwater, CGPoint(x: 284, y: L + 17), .leading, op)
        }
    }

    /// 흐르는 실: 시작 색 strand → 끝 색(옅어짐 또는 떡잎색). 적자 마지막 실은 hollow 만큼 속 빈 테두리
    private func flow(_ ctx: inout GraphicsContext, _ p: Path, _ w: CGFloat, fadeTo: CGPoint, from sx: CGFloat, to end: Color, grow: Double, hollow: CGFloat) {
        guard grow > 0 else { return }
        let tp = p.trimmedPath(from: 0, to: grow)
        if hollow > 0 { strand(&ctx, tp, w, end == .clear ? EarnColor.strand : end, hollow: hollow); return }
        let sh = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [EarnColor.strand, end == .clear ? EarnColor.strand.opacity(0) : end]),
            startPoint: CGPoint(x: sx, y: Self.bed), endPoint: fadeTo)
        ctx.stroke(tp, with: sh, style: StrokeStyle(lineWidth: w, lineCap: .butt))
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

    /// 떡잎 (331,490): 줄기 0,0→0,−11, 잎 (±5,−13) rx5.5 ry2.6 ∓28도
    private func sprout(_ ctx: inout GraphicsContext, scale: Double) {
        guard scale > 0 else { return }
        var g = ctx
        g.translateBy(x: 331, y: 490)
        g.scaleBy(x: scale, y: scale)
        var stem = Path(); stem.move(to: .zero); stem.addLine(to: CGPoint(x: 0, y: -11))
        g.stroke(stem, with: .color(EarnColor.sprout), lineWidth: 1.5)
        for side in [-1.0, 1.0] {
            var l = g
            l.translateBy(x: 5 * side, y: -13)
            l.rotate(by: .degrees(-28 * side))
            l.fill(Path(ellipseIn: CGRect(x: -5.5, y: -2.6, width: 11, height: 5.2)), with: .color(EarnColor.sprout))
        }
    }
}

#Preview("강 DRNK") {
    EarningsRiverSheet(target: EarnTarget(sym: Sample.symbol("DRNK")!, qty: 3))
}
