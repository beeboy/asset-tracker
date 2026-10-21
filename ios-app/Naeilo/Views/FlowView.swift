import SwiftUI

// 차트 탭: 내 종목 흐름을 겹(추세 / 지지·저항 / 큰 패턴 / 캔들 패턴)으로 켜고 끈다.
// 1단계: 키 없는 사용자도 샘플 종목 DRNK 만 실제로 그린다. 정보 제공용 (매매 권유 아님)
struct FlowView: View {
    @Environment(AppModel.self) private var model
    @State private var sel = "DRNK"
    @State private var showAll = false
    @State private var layers: Set<FlowLayer> = [.trend]
    @State private var term: FlowTerm? = nil

    private var hasKey: Bool {
        _ = Market.shared.tossDone      // 키를 넣고 받기를 마치면 다시 그린다
        return TossKeys.on || AlpacaKeys.on || LongbridgeKeys.on
    }
    /// 칩 줄: DRNK 를 맨 앞에, 그다음 내 종목
    private var ids: [String] {
        var out = ["DRNK"]
        for h in model.holdings where !out.contains(h.symbol) { out.append(h.symbol) }
        return out
    }
    private func name(_ id: String) -> String { Sample.symbol(id)?.name ?? id }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                chips
                if !hasKey {
                    Button { model.tab = .settings; model.settingsPath = [.toss] } label: {
                        Text("설정 > 오픈 API에서 키를 넣으면 내 종목도 그려져요").appFont(13).underline()
                            .foregroundStyle(Theme.teal).multilineTextAlignment(.leading)
                    }
                    .buttonStyle(.plain)
                }
                layerRow
                Group {
                    if sel == "DRNK" {
                        FlowChart(series: DrnkSample.series, layers: layers, term: $term)
                    } else {
                        VStack(spacing: 6) {
                            Text(name(sel)).appFont(15, .bold).foregroundStyle(Theme.ink)
                            Text(hasKey ? String(localized: "이 종목 차트는 다음 업데이트에서 그려져요") : String(localized: "키를 넣으면 이 종목 차트가 그려져요"))
                                .appFont(13).foregroundStyle(Theme.sub)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .frame(height: 380)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
                Text("두 손가락으로 벌리면 가까이, 오므리면 주봉·월봉으로 접혀요 · 옆으로 밀면 지난 날")
                    .appFont(12).foregroundStyle(Theme.muted).lineLimit(2).frame(height: 32, alignment: .topLeading)
                explain
            }
            .padding(16)
            .frame(maxWidth: Theme.maxWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.bg)
        .navigationTitle(FlowTab.name)
        .sheet(isPresented: $showAll) { allSheet }
    }

    // 맨 위 내 종목 칩 줄 + '전체'
    private var chips: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) { ForEach(ids, id: \.self) { chip($0) } }
            }
            Button("전체") { showAll = true }
                .appFont(13, .semibold).foregroundStyle(Theme.teal).frame(minHeight: 36)
        }
    }

    private func chip(_ id: String) -> some View {
        let on = sel == id, locked = id != "DRNK" && !hasKey
        return Button { sel = id; term = nil } label: {
            HStack(spacing: 4) {
                Text(id == "DRNK" ? "DRNK" : name(id)).appFont(13, on ? .bold : .regular).lineLimit(1)
                if id == "DRNK" { Text("샘플").appFont(10).foregroundStyle(on ? .white.opacity(0.8) : Theme.muted) }
                if locked { Text("키 필요").appFont(10).foregroundStyle(on ? .white.opacity(0.8) : Theme.muted) }
            }
            .foregroundStyle(on ? .white : Theme.ink)
            .padding(.horizontal, 12).frame(minHeight: 36)
            .background(on ? Theme.teal : Theme.card, in: Capsule())
            .overlay(Capsule().stroke(on ? .clear : Theme.border))
        }
        .buttonStyle(.plain)
    }

    private var allSheet: some View {
        NavigationStack {
            List(ids, id: \.self) { id in
                Button { sel = id; term = nil; showAll = false } label: {
                    HStack {
                        Text(id == "DRNK" ? "DRNK · " + name(id) : name(id)).foregroundStyle(Theme.ink)
                        Spacer()
                        if id == "DRNK" { Text("샘플").appFont(12).foregroundStyle(Theme.muted) }
                        else if !hasKey { Text("키 필요").appFont(12).foregroundStyle(Theme.muted) }
                        if sel == id { Image(systemName: "checkmark").foregroundStyle(Theme.teal) }
                    }
                }
            }
            .navigationTitle("전체 종목").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("닫기") { showAll = false } } }
        }
        .presentationDetents([.medium, .large])
    }

    // 겹 켜고 끄기. 칸 크기는 늘 같고 색만 바뀐다
    private var layerRow: some View {
        let all = layers.count == FlowLayer.allCases.count
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(FlowLayer.allCases, id: \.self) { l in
                    let on = layers.contains(l)
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) { if on { layers.remove(l) } else { layers.insert(l) } }
                    } label: {
                        Text(l.title).appFont(13, .semibold)
                            .foregroundStyle(on ? Theme.tealDark : Theme.sub)
                            .padding(.horizontal, 12).frame(minHeight: 34)
                            .background(on ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : Theme.border))
                    }
                    .buttonStyle(.plain)
                }
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { layers = all ? [.trend] : Set(FlowLayer.allCases) }
                } label: {
                    // 두 글자 폭을 같게 두어 바뀔 때 줄이 흔들리지 않게
                    ZStack {
                        Text("모두 켜기").opacity(all ? 0 : 1)
                        Text("추세만").opacity(all ? 1 : 0)
                    }
                    .appFont(13, .semibold).foregroundStyle(Theme.teal).padding(.horizontal, 8).frame(minHeight: 34)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // 차트 아래 고정 높이 설명 칸
    private var explain: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let t = term {
                Text(t.title).appFont(15, .bold).foregroundStyle(Theme.ink)
                Text(t.body).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            } else {
                Text("이름표나 선을 눌러 보세요").appFont(15, .bold).foregroundStyle(Theme.ink)
                Text("누른 것의 뜻만 여기에 떠요. 흐린 점선 이름표는 확정 전이거나 거래량이 적었던 것이에요. 정보 제공용이며 매매 권유가 아니에요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 132, maxHeight: 132, alignment: .topLeading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .clipped()
    }
}

enum FlowLayer: Int, CaseIterable, Hashable {
    case trend, levels, big, candle
    var title: String {
        switch self {
        case .trend: String(localized: "추세")
        case .levels: String(localized: "지지·저항")
        case .big: String(localized: "큰 패턴")
        case .candle: String(localized: "캔들 패턴")
        }
    }
}

/// 설명 칸에 띄울 용어
enum FlowTerm: Equatable {
    case dots, band, support, resistance, pattern(FlowPattern.Kind)
    var title: String {
        switch self {
        case .dots: String(localized: "추세 점")
        case .band: String(localized: "추세 띠")
        case .support: String(localized: "지지선")
        case .resistance: String(localized: "저항선")
        case .pattern(let k): k.title
        }
    }
    var body: String {
        switch self {
        case .dots: String(localized: "고점·저점마다 찍은 점이에요. 점이 계단처럼 올라가면 상승, 내려가면 하락, 같은 높이를 오가면 횡보예요.")
        case .band: String(localized: "위에서부터 월·주·일 흐름이에요. 빨강은 상승, 파랑은 하락, 회색은 횡보예요.")
        case .support: String(localized: "주가가 여러 번 내려와 받친 가격이에요. 여러 번 닿을수록 선이 진해요.")
        case .resistance: String(localized: "주가가 여러 번 올라와 튕겨 나간 가격이에요. 여러 번 닿을수록 선이 진해요.")
        case .pattern(let k): k.body
        }
    }
}

extension FlowPattern.Kind {
    var title: String {
        switch self {
        case .doubleBottom: String(localized: "더블바텀")
        case .doubleTop: String(localized: "더블탑")
        case .headShoulders: String(localized: "헤드앤숄더")
        case .hammer: String(localized: "해머")
        case .shootingStar: String(localized: "유성")
        case .bullEngulf: String(localized: "강세 포위형")
        case .bearEngulf: String(localized: "약세 포위형")
        case .insideBar: String(localized: "인사이드바")
        }
    }
    var body: String {
        switch self {
        case .doubleBottom: String(localized: "비슷한 높이의 바닥이 두 번 생긴 모양(W)이에요. 두 바닥 사이 고점을 종가가 넘으면 확정이에요.")
        case .doubleTop: String(localized: "비슷한 높이의 꼭대기가 두 번 생긴 모양(M)이에요. 두 꼭대기 사이 저점 아래로 종가가 내려가면 확정이에요.")
        case .headShoulders: String(localized: "가운데 꼭대기가 양옆보다 높은 세 봉우리예요. 두 골짜기를 이은 선 아래로 내려가면 확정이에요.")
        case .hammer: String(localized: "아래꼬리가 몸통보다 훨씬 긴 캔들이에요. 내려갔다가 다시 끌어올린 날이라는 뜻이에요.")
        case .shootingStar: String(localized: "위꼬리가 몸통보다 훨씬 긴 캔들이에요. 올라갔다가 다시 밀려 내려온 날이라는 뜻이에요.")
        case .bullEngulf: String(localized: "전날 음봉 몸통을 다음 날 양봉 몸통이 다 덮은 모양이에요.")
        case .bearEngulf: String(localized: "전날 양봉 몸통을 다음 날 음봉 몸통이 다 덮은 모양이에요.")
        case .insideBar: String(localized: "전날 고가·저가 안에서만 움직인 날이에요. 힘이 잠시 멈춘 자리예요.")
        }
    }
}

/// 캔들 차트. 보이는 구간의 캔들만 그리고, 넓게 보면 5일·21일씩 묶어 주봉·월봉으로 접는다
struct FlowChart: View {
    let series: FlowSeries
    let layers: Set<FlowLayer>
    @Binding var term: FlowTerm?
    @State private var count = 160          // 보이는 날 수
    @State private var end: Int? = nil      // 맨 오른쪽 날 (nil = 마지막 날)
    @State private var pinchBase: Int? = nil
    @State private var dragBase: Int? = nil

    private let axisW: CGFloat = 46, bandH: CGFloat = 16, topH: CGFloat = 56, botH: CGFloat = 20

    var body: some View {
        GeometryReader { g in
            let k = series.candles, n = k.count
            let cnt = max(20, min(n, count))
            let hi = max(cnt - 1, min(n - 1, end ?? n - 1)), lo = hi - cnt + 1
            let step = cnt <= 200 ? 1 : cnt <= 600 ? 5 : 21
            let W = g.size.width - axisW, top = topH + bandH + 6, H = g.size.height - top - botH
            let slice = k[lo...hi]
            let pMin = slice.map(\.l).min() ?? 0, pMax = slice.map(\.h).max() ?? 1
            let pad = (pMax - pMin) * 0.08
            let yMin = pMin - pad, yMax = pMax + pad
            let x = { (i: Double) in CGFloat((i - Double(lo) + 0.5) / Double(cnt)) * W }
            let y = { (v: Double) in top + CGFloat(1 - (v - yMin) / max(1e-12, yMax - yMin)) * H }
            let last = k[hi]
            let piv = series.pivotSets[step == 1 ? 0 : step == 5 ? 1 : 2]
            // 지지·저항: 보이는 것 중 많이 닿은 4개. 이름표는 위아래로 붙으면 뒤의 것을 뺀다
            let lvls = Array(series.levels.filter { $0.price >= yMin && $0.price <= yMax && $0.from <= hi }.prefix(4))
            let lvlTags: Set<Int> = lvls.reduce(into: (Set<Int>(), [CGFloat]())) { acc, lv in
                let yy = y(lv.price)
                if !acc.1.contains(where: { abs($0 - yy) < 18 }) { acc.0.insert(lv.id); acc.1.append(yy) }
            }.0

            ZStack(alignment: .topLeading) {
                // 머리: 종목·마지막 값 (시작 대비) + 지금 단위
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("DRNK · 샘플").appFont(12).foregroundStyle(Theme.muted)
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(String(format: "%+.0f%%", series.pct(last.c))).appFont(20, .bold).monospacedDigit()
                                .foregroundStyle(Theme.change(series.pct(last.c)))
                            Text("시작 대비").appFont(12).foregroundStyle(Theme.muted)
                        }
                    }
                    Spacer()
                    Text(step == 1 ? "일봉" : step == 5 ? "주봉" : "월봉").appFont(12, .semibold).foregroundStyle(Theme.sub)
                        .padding(.horizontal, 8).padding(.vertical, 3).background(Theme.line, in: Capsule())
                }
                .padding(.horizontal, 12).padding(.top, 8)

                // 오른쪽 눈금 (+x% 시작 대비)
                ForEach(0..<4, id: \.self) { t in
                    let v = yMin + (yMax - yMin) * (0.1 + 0.8 * Double(t) / 3)
                    Path { p in p.move(to: CGPoint(x: 0, y: y(v))); p.addLine(to: CGPoint(x: W, y: y(v))) }
                        .stroke(Theme.line, lineWidth: 1)
                    Text(String(format: "%+.0f%%", series.pct(v))).appFont(10).monospacedDigit().foregroundStyle(Theme.muted)
                        .frame(width: axisW - 4, alignment: .leading)
                        .position(x: W + axisW / 2 + 2, y: y(v))
                }

                // 캔들 (늘 보임)
                Canvas { ctx, _ in
                    var g0 = lo / step
                    while g0 * step <= hi {
                        let a = max(lo, g0 * step), b = min(hi, g0 * step + step - 1)
                        let o = k[a].o, c = k[b].c
                        var h = -Double.infinity, l = Double.infinity
                        for i in a...b { h = max(h, k[i].h); l = min(l, k[i].l) }
                        let cx = x(Double(a + b) / 2)
                        let bw = max(1, CGFloat(b - a + 1) / CGFloat(cnt) * W * 0.68)
                        let col = c >= o ? Theme.up : Theme.down
                        var wick = Path(); wick.move(to: CGPoint(x: cx, y: y(h))); wick.addLine(to: CGPoint(x: cx, y: y(l)))
                        ctx.stroke(wick, with: .color(col), lineWidth: 1)
                        let r = CGRect(x: cx - bw / 2, y: y(max(o, c)), width: bw, height: max(1, abs(y(o) - y(c))))
                        ctx.fill(Path(r), with: .color(col))
                        g0 += 1
                    }
                }
                .frame(width: W)

                // 겹 1: 추세 띠 + 추세 점
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        for (row, ts) in series.trends.reversed().enumerated() {
                            for t in ts where t.to >= lo && t.from <= hi {
                                let a = x(Double(max(lo, t.from)) - 0.5), b = x(Double(min(hi, t.to)) + 0.5)
                                let r = CGRect(x: a, y: topH + CGFloat(row) * (bandH / 3), width: max(1, b - a), height: bandH / 3 - 1)
                                ctx.fill(Path(r), with: .color(t.dir > 0 ? Theme.up : t.dir < 0 ? Theme.down : Theme.dash))
                            }
                        }
                        let vis = piv.filter { $0.i >= lo - 40 && $0.i <= hi + 40 }
                        var line = Path()
                        for (j, p) in vis.enumerated() {
                            let pt = CGPoint(x: x(Double(p.i)), y: y(p.price) + (p.high ? -5 : 5))
                            j == 0 ? line.move(to: pt) : line.addLine(to: pt)
                        }
                        ctx.stroke(line, with: .color(Theme.teal.opacity(0.55)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        for p in vis where p.i >= lo && p.i <= hi {
                            let pt = CGPoint(x: x(Double(p.i)), y: y(p.price) + (p.high ? -5 : 5))
                            let r = CGRect(x: pt.x - 3.5, y: pt.y - 3.5, width: 7, height: 7)
                            ctx.fill(Path(ellipseIn: r), with: .color(Theme.teal))
                            ctx.stroke(Path(ellipseIn: r), with: .color(Theme.card), lineWidth: 1.2)
                        }
                    }
                    .frame(width: W)
                    // 띠를 누르면 띠 설명
                    Color.clear.contentShape(Rectangle()).frame(width: W, height: bandH + 6)
                        .offset(y: topH - 3)
                        .onTapGesture { term = .band }
                }
                .opacity(layers.contains(.trend) ? 1 : 0)
                .allowsHitTesting(layers.contains(.trend))

                // 겹 2: 지지·저항 가로선
                ZStack(alignment: .topLeading) {
                    ForEach(lvls) { lv in
                        let a = max(0, x(Double(max(lo, lv.from)) - 0.5)), yy = y(lv.price)
                        let col = lv.support ? Theme.up : Theme.down
                        let op = min(1, 0.25 + 0.2 * Double(lv.touches))
                        Path { p in p.move(to: CGPoint(x: a, y: yy)); p.addLine(to: CGPoint(x: W, y: yy)) }
                            .stroke(col.opacity(op), lineWidth: lv.touches >= 4 ? 2 : 1.2)
                        Button { term = lv.support ? .support : .resistance } label: {
                            Text((lv.support ? String(localized: "지지") : String(localized: "저항")) + " ×\(lv.touches)")
                                .appFont(10, .semibold).foregroundStyle(col.opacity(max(0.7, op)))
                                .padding(.horizontal, 4).background(Theme.card.opacity(0.9), in: Capsule())
                                .frame(minWidth: 44, minHeight: 24)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .opacity(lvlTags.contains(lv.id) ? 1 : 0)
                        .position(x: W - 28, y: yy)
                    }
                }
                .opacity(layers.contains(.levels) ? 1 : 0)
                .allowsHitTesting(layers.contains(.levels))

                // 겹 3: 큰 패턴 (구간을 묶는 틀 + 이름표)
                ZStack(alignment: .topLeading) {
                    ForEach(series.patterns.filter { $0.big && $0.to >= lo && $0.from <= hi }) { pt in
                        let a = x(Double(max(lo, pt.from)) - 0.5), b = x(Double(min(hi, pt.to)) + 0.5)
                        let seg = k[max(lo, pt.from)...min(hi, pt.to)]
                        let t = y(seg.map(\.h).max() ?? pt.price) - 4, bt = y(seg.map(\.l).min() ?? pt.price) + 4
                        let col = Theme.purple
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(col.opacity(pt.strong ? 0.9 : 0.45), style: StrokeStyle(lineWidth: pt.strong ? 1.5 : 1, dash: pt.strong ? [] : [4, 3]))
                            .background(RoundedRectangle(cornerRadius: 6).fill(col.opacity(pt.strong ? 0.07 : 0.03)))
                            .frame(width: max(4, b - a), height: max(4, bt - t))
                            .position(x: (a + b) / 2, y: (t + bt) / 2)
                            .allowsHitTesting(false)
                        tag(pt.kind.title, col, pt.strong) { term = .pattern(pt.kind) }
                            .position(x: min(max((a + b) / 2, 34), W - 34), y: pt.below ? (bt + 13 > top + H ? bt - 13 : bt + 13) : (t - 13 < top ? t + 13 : t - 13))
                    }
                }
                .opacity(layers.contains(.big) ? 1 : 0)
                .allowsHitTesting(layers.contains(.big))

                // 겹 4: 캔들 패턴 (일봉으로 볼 때만. 주봉·월봉에선 캔들 하나가 하루가 아니라 뜻이 달라진다)
                ZStack(alignment: .topLeading) {
                    if step == 1 {
                        ForEach(series.patterns.filter { !$0.big && $0.to >= lo && $0.to <= hi }) { pt in
                            let cx = x(Double(pt.from + pt.to) / 2), yy = y(pt.price)
                            tag(pt.kind.title, Theme.orange, pt.strong) { term = .pattern(pt.kind) }
                                .position(x: min(max(cx, 30), W - 30), y: pt.below ? min(yy + 14, top + H + 6) : max(yy - 14, top + 4))
                        }
                    }
                }
                .opacity(layers.contains(.candle) ? 1 : 0)
                .allowsHitTesting(layers.contains(.candle))

                // 아래: 보이는 기간
                Text(Self.fmt.string(from: date(lo)) + " – " + Self.fmt.string(from: date(hi)))
                    .appFont(10).monospacedDigit().foregroundStyle(Theme.muted)
                    .position(x: W / 2, y: g.size.height - botH / 2)
            }
            .contentShape(Rectangle())
            // 점·선 근처를 누르면 그 뜻. 이름표는 버튼이라 먼저 받는다
            .onTapGesture(coordinateSpace: .local) { loc in
                if layers.contains(.levels), let lv = lvls
                    .min(by: { abs(y($0.price) - loc.y) < abs(y($1.price) - loc.y) }), abs(y(lv.price) - loc.y) < 10 {
                    term = lv.support ? .support : .resistance; return
                }
                if layers.contains(.trend), piv.contains(where: { $0.i >= lo && $0.i <= hi && hypot(x(Double($0.i)) - loc.x, y($0.price) - loc.y) < 22 }) {
                    term = .dots
                }
            }
            // 이름표 위에서 시작해도 밀기·핀치가 먹게 같이 받는다
            .simultaneousGesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { v in
                        let base = dragBase ?? hi
                        if dragBase == nil { dragBase = hi }
                        let shift = Int((v.translation.width / max(1, W) * CGFloat(cnt)).rounded())
                        end = max(cnt - 1, min(n - 1, base - shift))
                    }
                    .onEnded { _ in dragBase = nil }
                    .simultaneously(with: MagnifyGesture()
                        .onChanged { v in
                            let base = pinchBase ?? cnt
                            if pinchBase == nil { pinchBase = cnt }
                            count = max(20, min(n, Int(Double(base) / max(0.05, v.magnification))))
                        }
                        .onEnded { _ in pinchBase = nil })
            )
            .sensoryFeedback(.selection, trigger: step)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .contain)
    }

    /// 이름표: 확정은 진하게, 약한 것은 흐린 점선 테두리
    private func tag(_ t: String, _ col: Color, _ strong: Bool, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            Text(t).appFont(10, .bold).lineLimit(1).fixedSize()
                .foregroundStyle(strong ? .white : col.opacity(0.8))
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(strong ? col : Theme.card.opacity(0.85), in: Capsule())
                .overlay(Capsule().stroke(col.opacity(strong ? 0 : 0.6), style: StrokeStyle(lineWidth: 1, dash: [3, 2])))
                .opacity(strong ? 1 : 0.75)
                .frame(minHeight: 24).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// DRNK 기록은 어제가 마지막 날, 하루에 한 칸
    private func date(_ i: Int) -> Date {
        Date().addingTimeInterval(-Double(series.candles.count - i) * 86400)
    }
    static let fmt: DateFormatter = { let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "yyyy.MM.dd"; return f }()
}
