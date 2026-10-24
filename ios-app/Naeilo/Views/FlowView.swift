import SwiftUI

// 차트 탭: 내 종목 흐름을 겹(추세 / 지지·저항 / 큰 패턴 / 캔들 패턴)으로 켜고 끈다.
// 1단계: 키 없는 사용자도 샘플 종목 DRNK 만 실제로 그린다. 정보 제공용 (매매 권유 아님)
struct FlowView: View {
    @Environment(AppModel.self) private var model
    @State private var sel: String? = nil
    @AppStorage(BoxLook.key) private var lookRaw = BoxLook.jump.rawValue
    @State private var showAll = false
    @State private var layers: Set<FlowLayer> = [.trend]
    @State private var term: FlowTerm? = nil
    @State private var showGuide = false
    @State private var guideFocus: String? = nil

    /// 차트 실데이터를 받을 키 (토스·알파카)
    private var hasKey: Bool {
        _ = Market.shared.tossDone      // 키를 넣고 받기를 마치면 다시 그린다
        return TossKeys.on || AlpacaKeys.on
    }
    /// 칩 줄: 키가 없으면 샘플 DRNK 를 맨 앞에, 키가 있으면 DRNK 는 빼고 내 종목만
    private var ids: [String] {
        var out = hasKey ? [] : ["DRNK"]
        for h in model.holdings where !out.contains(h.symbol) && h.symbol != KoreaEstimate.sumId && !Sample.isVirtual(h.symbol) { out.append(h.symbol) }
        return out
    }
    /// 고른 종목이 없거나 칩 줄에서 빠졌으면(키를 넣어 DRNK 가 숨는 등) 큰 흐름 박스의 첫 종목
    private var cur: String? {
        if let sel, ids.contains(sel) { return sel }
        return mood.box.first ?? byWeight.first
    }
    /// 평가액 (원). 샘플 DRNK 는 담지 않았어도 1
    private func weight(_ id: String) -> Double { model.rows.first { $0.id == id }?.value ?? (id == "DRNK" ? 1 : 0) }
    /// 비중 큰 순서
    private var byWeight: [String] { ids.enumerated().sorted { (weight($0.1), -$0.0) > (weight($1.1), -$1.0) }.map(\.1) }
    /// 아래 칩 줄: 큰 흐름 박스에 없는 종목만 비중 순
    private var restIds: [String] { let b = Set(mood.box); return byWeight.filter { !b.contains($0) } }

    /// 큰 흐름 박스 값: 일봉이 있는 종목의 큰 흐름(22% 넘는 출렁임 띠) 마지막 방향을 평가액 몫으로 쌓는다
    struct Mood { var shares: [Double] = [0, 0, 0]; var side = 0; var strong = false; var box: [String] = [] }
    private var mood: Mood {
        let items: [(String, Int, Double)] = byWeight.compactMap { id in
            store.series(id)?.trends[2].last.map { (id, $0.dir > 0 ? 1 : $0.dir < 0 ? -1 : 0, weight(id)) }
        }
        guard !items.isEmpty else { return Mood() }
        let total = items.reduce(0) { $0 + $1.2 }
        let w = { (d: Int) in items.filter { $0.1 == d }.reduce(0) { $0 + (total > 0 ? $1.2 / total : 1 / Double(items.count)) } }
        let sh = [w(1), w(0), w(-1)]
        // 횡보는 빼고 상승·하락 중 큰 쪽. 둘 다 없으면 횡보
        let side = sh[0] == 0 && sh[2] == 0 ? 0 : sh[0] >= sh[2] ? 1 : -1
        let win = side > 0 ? sh[0] : side < 0 ? sh[2] : sh[1]
        return Mood(shares: sh, side: side, strong: win > 0.5, box: items.filter { $0.1 == side }.map(\.0))
    }
    private func name(_ id: String) -> String { Sample.symbol(id)?.name ?? id }
    private var store: FlowStore { FlowStore.shared }
    /// 키가 없어 못 그리는 종목이 있으면 설정 링크를 보인다
    private var needKey: Bool { _ = hasKey; return ids.contains { !Sample.isVirtual($0) && !FlowStore.canFetch($0) } }

    var body: some View {
        GeometryReader { geo in
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader(tab: String(localized: String.LocalizationValue(FlowTab.name))).padding(.horizontal, -16)
                if !mood.box.isEmpty { moodBox }
                if !restIds.isEmpty { chips }
                if needKey {
                    Button { model.openSettings(.toss) } label: {
                        Text("설정 > 오픈 API에서 키를 넣으면 내 종목도 그려져요").appFont(13).underline()
                            .foregroundStyle(Theme.teal).multilineTextAlignment(.leading)
                    }
                    .buttonStyle(.plain)
                }
                layerRow
                Group {
                    if let sel = cur, let s = store.series(sel) {
                        FlowChart(series: s, title: sel == "DRNK" ? String(localized: "DRNK · 샘플") : name(sel), layers: layers, term: $term, more: { t in guideFocus = t.key; showGuide = true })
                            .id(sel)
                    } else if cur == nil {
                        Text("종목을 담으면 흐름이 보여요").appFont(14).foregroundStyle(Theme.sub)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let sel = cur {
                        VStack(spacing: 6) {
                            Text(name(sel)).appFont(15, .bold).foregroundStyle(Theme.ink)
                            Text(!FlowStore.canFetch(sel) ? String(localized: "키를 넣으면 이 종목 차트가 그려져요")
                                 : store.loading.contains(sel) ? String(localized: "일봉을 받는 중이에요")
                                 : store.failed.contains(sel) ? String(localized: "일봉을 못 받았어요")
                                 : String(localized: "아직 받은 일봉이 없어요"))
                                .appFont(13).foregroundStyle(Theme.sub)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                // 차트는 아래 탭 바 바로 위까지 (화면이 작아도 300 은 둔다)
                .frame(minHeight: 300, maxHeight: .infinity)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
            }
            .screen()
            .padding(.bottom, -12)
            .frame(minHeight: geo.size.height, alignment: .top)
        }
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showAll) { allSheet }
        .sheet(isPresented: $showGuide) { FlowGuide(focus: guideFocus) }
        // 열 때 담은 종목 일봉을 받는다 (하루 한 번만 실제로 부른다)
        .task(id: hasKey) { await store.ensure(ids) }
        // 당기면 다른 탭처럼 시세를 받고, 일봉도 다시 묻는다 (장이 끝나 새 날이 생겼을 때만 실제로 받는다)
        .refreshable { await Market.shared.pullRefresh(model); await store.ensure(ids) }
    }

    // 맨 위 큰 흐름 박스: 홈에서 고른 박스 모양. 큰 글자 = 상승·하락 중 큰 쪽 (절반 넘으면 강한), 버튼 = 그쪽 종목만
    private var moodBox: some View {
        let md = mood, look = BoxLook(rawValue: lookRaw) ?? .jump
        let c = md.side > 0 ? TodayBox.upC : md.side < 0 ? TodayBox.downC : TodayBox.dim
        let head = md.side == 0 ? String(localized: "횡보")
            : md.side > 0 ? (md.strong ? String(localized: "강한 상승") : String(localized: "약한 상승"))
            : (md.strong ? String(localized: "강한 하락") : String(localized: "약한 하락"))
        let share = md.side > 0 ? md.shares[0] : md.side < 0 ? md.shares[2] : md.shares[1]
        return VStack(alignment: .leading, spacing: 0) {
            HStack { Text("내 종목 · 큰 흐름"); Spacer(); FreshLabel() }.appFont(13).foregroundStyle(TodayBox.dim)
            (Text(head + " · ").foregroundColor(.white) + Text(AppModel.pct(share)).foregroundColor(c))
                .font(.system(size: 30, weight: .heavy)).tracking(-0.5).lineLimit(1).minimumScaleFactor(0.6)
                .padding(.top, 6)
            MoodBand(look: look, shares: md.shares, road: 10).padding(.top, 14)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) { ForEach(md.box, id: \.self) { boxChip($0, c) } }
                    .padding(.horizontal, 18)
            }
            .mask(LinearGradient(stops: [.init(color: .black, location: 0.84), .init(color: .clear, location: 1)], startPoint: .leading, endPoint: .trailing))
            .padding(.horizontal, -18).padding(.top, 14)
        }
        .padding(.horizontal, 18).padding(.top, 14).padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background { MoodBackdrop(look: look) }
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private func boxChip(_ id: String, _ dot: Color) -> some View {
        let on = cur == id
        return Button { sel = id; term = nil } label: {
            HStack(spacing: 6) {
                Circle().fill(dot).frame(width: 8, height: 8)
                Text(id == "DRNK" ? "DRNK" : name(id)).appFont(15, on ? .bold : .regular).lineLimit(1)
            }
            .foregroundStyle(on ? .white : Color(hex: 0xC9D0D6))
            .padding(.horizontal, 14).frame(minHeight: 34)
            .overlay(Capsule().stroke(on ? .white : .white.opacity(0.22)))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    // 큰 흐름 박스 밖 내 종목 칩 줄 + '전체'
    private var chips: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) { ForEach(restIds, id: \.self) { chip($0) } }
            }
            // 오른쪽 끝을 흐리게 지워 잘린 칩이 '더 있음'으로 녹아들게
            .mask(LinearGradient(stops: [.init(color: .black, location: 0.9), .init(color: .clear, location: 1)], startPoint: .leading, endPoint: .trailing))
            Button("전체") { showAll = true }
                .appFont(13, .semibold).foregroundStyle(Theme.teal).frame(minHeight: 36)
        }
    }

    private func chip(_ id: String) -> some View {
        let on = cur == id, locked = id != "DRNK" && !FlowStore.canFetch(id)
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
            List(byWeight, id: \.self) { id in
                Button { sel = id; term = nil; showAll = false } label: {
                    HStack {
                        Text(id == "DRNK" ? "DRNK · " + name(id) : name(id)).foregroundStyle(Theme.ink)
                        Spacer()
                        if id == "DRNK" { Text("샘플").appFont(12).foregroundStyle(Theme.muted) }
                        else if !FlowStore.canFetch(id) { Text("키 필요").appFont(12).foregroundStyle(Theme.muted) }
                        if cur == id { Image(systemName: "checkmark").foregroundStyle(Theme.teal) }
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
                Button { guideFocus = nil; showGuide = true } label: {
                    Image(systemName: "questionmark.circle").font(.system(size: 18)).foregroundStyle(Theme.teal)
                        .frame(width: 34, height: 34)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("흐름 읽는 법"))
            }
        }
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
    /// 설명 페이지에서 그 줄로 바로 가는 이름
    var key: String {
        switch self {
        case .dots: "dots"
        case .band: "band"
        case .support: "support"
        case .resistance: "resistance"
        case .pattern(let k): k.rawValue
        }
    }
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
        case .dots: String(localized: "고점·저점 표시 · 계단 위로 상승 · 아래로 하락 · 같은 높이 횡보")
        case .band: String(localized: "큰 흐름 · 22% 넘는 출렁임만 셈\n중간 흐름 · 12% 넘는 출렁임\n작은 흐름 · 6% 넘는 출렁임\n빨강 상승 · 파랑 하락 · 회색 횡보")
        case .support: String(localized: "여러 번 내려와 받친 가격 · 많이 닿을수록 진하게")
        case .resistance: String(localized: "여러 번 올라와 막힌 가격 · 많이 닿을수록 진하게")
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
        case .ascTriangle: String(localized: "상승삼각형")
        case .descTriangle: String(localized: "하락삼각형")
        case .bullFlag, .bearFlag: String(localized: "플래그")
        case .hammer: String(localized: "해머")
        case .shootingStar: String(localized: "유성")
        case .bullEngulf: String(localized: "강세 포위형")
        case .bearEngulf: String(localized: "약세 포위형")
        case .insideBar: String(localized: "인사이드바")
        }
    }
    var body: String {
        switch self {
        case .doubleBottom: String(localized: "비슷한 바닥 두 번 (W) · 사이 고점 넘는 종가에 확정")
        case .doubleTop: String(localized: "비슷한 꼭대기 두 번 (M) · 사이 저점 깨는 종가에 확정")
        case .headShoulders: String(localized: "가운데가 가장 높은 세 봉우리 · 두 골짜기 선 깨면 확정")
        case .ascTriangle: String(localized: "꼭대기 같고 바닥은 높아짐 · 꼭대기 넘는 종가에 확정")
        case .descTriangle: String(localized: "바닥 같고 꼭대기는 낮아짐 · 바닥 깨는 종가에 확정")
        case .bullFlag: String(localized: "급등(깃대) 뒤 쉬어 가기 · 위로 벗어나면 확정")
        case .bearFlag: String(localized: "급락(깃대) 뒤 쉬어 가기 · 아래로 벗어나면 확정")
        case .hammer: String(localized: "아래꼬리가 긴 캔들 · 밀렸다 끌어올린 날")
        case .shootingStar: String(localized: "위꼬리가 긴 캔들 · 올랐다 밀려난 날")
        case .bullEngulf: String(localized: "전날 음봉을 양봉이 다 덮음")
        case .bearEngulf: String(localized: "전날 양봉을 음봉이 다 덮음")
        case .insideBar: String(localized: "전날 범위 안에서만 움직인 날 · 잠시 멈춤")
        }
    }
    /// 흐름 읽는 법 '이럴 때': 흔히 생기는 장면 한 줄 (널리 쓰이는 차트 읽기 지식, 특정 종목 사례 아님)
    var when: String {
        switch self {
        case .doubleBottom: String(localized: "실적 쇼크 급락 뒤 · 첫 바닥 근처에서 또 사들임")
        case .doubleTop: String(localized: "신고가 뒤 차익 매물 · 같은 가격에서 또 막힘")
        case .headShoulders: String(localized: "긴 상승 끝물 · 호재에 마지막 신고가")
        case .ascTriangle: String(localized: "한 가격에 매물 · 사는 쪽이 점점 높게 받침")
        case .descTriangle: String(localized: "받치는 가격 하나 · 파는 쪽이 점점 낮게 내놓음")
        case .bullFlag: String(localized: "실적 깜짝·큰 계약에 급등 뒤 쉬어 가기")
        case .bearFlag: String(localized: "악재 급락 뒤 · 힘 약한 저가 매수 반등")
        case .hammer: String(localized: "며칠 하락 뒤 · 아침 악재에 빠졌다 오후에 회복")
        case .shootingStar: String(localized: "며칠 상승 뒤 · 아침 호재에 치솟다 차익 매물에 밀림")
        case .bullEngulf: String(localized: "공포로 급락한 다음 날 · 하락을 다 되돌림")
        case .bearEngulf: String(localized: "기대로 급등한 다음 날 · 실적 실망에 다 내줌")
        case .insideBar: String(localized: "금리 결정·실적·물가 발표 하루 전")
        }
    }
}

/// 캔들 차트. 보이는 구간의 캔들만 그리고, 넓게 보면 5일·21일씩 묶어 주봉·월봉으로 접는다
struct FlowChart: View {
    let series: FlowSeries
    let title: String
    let layers: Set<FlowLayer>
    @Binding var term: FlowTerm?
    var more: (FlowTerm) -> Void = { _ in }
    @State private var count = 160          // 보이는 날 수
    @State private var end: Int? = nil      // 맨 오른쪽 날 (nil = 마지막 날)
    @State private var pinchBase: Int? = nil
    @State private var dragBase: Int? = nil
    @State private var tapAt: CGPoint = .zero   // 마지막으로 누른 자리 (말풍선을 그 근처에 띄운다)
    @AppStorage("flow.hintSeen") private var hintSeen = false
    @Environment(\.colorScheme) private var scheme

    // 말풍선: 라이트는 흰 바탕 + 테두리 + 그림자, 다크는 카드보다 한 단 밝은 바탕
    private static let bb = Color(hex: 0xFFFFFF, dark: 0x2E3945), bbLine = Color(hex: 0xD5DBE1, dark: 0x46535F)
    private static let bbTitle = Color(hex: 0x15202B, dark: 0xF2F5F8), bbBody = Color(hex: 0x46525E, dark: 0xC6CED6)
    private static let bbFoot = Color(hex: 0x8A949E, dark: 0x8E99A4), bbMore = Color(hex: 0x0B6B66, dark: 0x8FD8CE)

    private let axisW: CGFloat = 46, bandH: CGFloat = 36, topH: CGFloat = 50, botH: CGFloat = 20

    var body: some View {
        GeometryReader { g in
            let k = series.candles, n = k.count
            let cnt = max(20, min(n, count))
            let hi = max(cnt - 1, min(n - 1, end ?? n - 1)), lo = hi - cnt + 1
            let step = cnt <= 200 ? 1 : cnt <= 600 ? 5 : 21
            let W = g.size.width - axisW, top = topH + bandH + 12, H = g.size.height - top - botH
            let slice = k[lo...hi]
            let pMin = slice.map(\.l).min() ?? 0, pMax = slice.map(\.h).max() ?? 1
            let pad = (pMax - pMin) * 0.08
            let yMin = pMin - pad, yMax = pMax + pad
            let y = { (v: Double) in top + CGFloat(1 - (v - yMin) / max(1e-12, yMax - yMin)) * H }
            let last = k[hi]
            let piv = series.pivotSets[step == 1 ? 0 : step == 5 ? 1 : 2]
            // 지지·저항: 보이는 것 중 많이 닿은 4개. 이름표는 위아래로 붙으면 뒤의 것을 뺀다
            let lvls = Array(series.levels.filter { $0.price >= yMin && $0.price <= yMax && $0.from <= hi }.prefix(4))
            let x = { (i: Double) in CGFloat((i - Double(lo) + 0.5) / Double(cnt)) * W }
            // 월봉으로 볼 땐 패턴 없이 추세·지지/저항만 (한 칸이 한 달이라 몇 주짜리 모양은 뜻이 없다)
            let bigs = step == 21 ? [] : series.patterns.filter { $0.big && $0.to >= lo && $0.from <= hi }
            // 흐린(확정 전·거래량 적은) 캔들 패턴은 확대해서 60일 이하로 볼 때만 띄운다
            let showWeak: Bool = cnt <= 60
            let smalls = step == 1 ? series.patterns.filter { !$0.big && $0.to >= lo && $0.to <= hi && ($0.strong || showWeak) } : []
            // 큰 패턴 틀: 구간 안 최고가·최저가
            let box = { (pt: FlowPattern) -> (CGFloat, CGFloat, CGFloat, CGFloat) in
                let a = x(Double(max(lo, pt.from)) - 0.5), b = x(Double(min(hi, pt.to)) + 0.5)
                let seg = k[max(lo, pt.from)...min(hi, pt.to)]
                return (a, b, y(seg.map(\.h).max() ?? pt.price) - 4, y(seg.map(\.l).min() ?? pt.price) + 4)
            }
            let spots = placeTags(levels: lvls.map { lv in
                let w = tagW(levelText(lv)), yy = y(lv.price), cx = W - w / 2 - 2
                return TagWant(key: "L\(lv.id)", layer: .levels, strong: true, w: w, cands: [CGPoint(x: cx, y: yy), CGPoint(x: cx, y: yy - 12), CGPoint(x: cx, y: yy + 12)])
            }, big: bigs.map { pt in
                let (a, b, t, bt) = box(pt), w = tagW(pt.kind.title), cx = (a + b) / 2
                let up = t - 13, dn = bt + 13
                let pref = pt.below ? [dn, bt - 13, up] : [up, t + 13, dn]
                return TagWant(key: "B\(pt.id)", layer: .big, strong: pt.strong, w: w,
                               cands: pref.map { CGPoint(x: cx, y: $0) } + [CGPoint(x: cx - w, y: pref[0]), CGPoint(x: cx + w, y: pref[0])])
            }, candle: smalls.map { pt in
                let w = tagW(pt.kind.title), cx = x(Double(pt.from + pt.to) / 2), yy = y(pt.price)
                let other = pt.below ? y(k[pt.to].h) - 14 : y(k[pt.to].l) + 14
                let d: CGFloat = pt.below ? 1 : -1
                return TagWant(key: "C\(pt.id)", layer: .candle, strong: pt.strong, w: w,
                               cands: [CGPoint(x: cx, y: yy + 14 * d), CGPoint(x: cx, y: yy + 34 * d), CGPoint(x: cx, y: other), CGPoint(x: cx, y: yy + 54 * d)])
            }, on: layers, bounds: CGRect(x: 0, y: top - 6, width: W, height: H + 14))

            ZStack(alignment: .topLeading) {
                // 머리: ▲44% 테슬라 · 시작 대비 (세모는 숫자의 70%) + 지금 단위
                HStack(alignment: .center) {
                    let v = series.pct(last.c)
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        (Text(v > 0 ? "▲" : v < 0 ? "▼" : "").font(.system(size: 15, weight: .heavy)).baselineOffset(2)
                         + Text(String(format: "%.0f%%", abs(v))).font(.system(size: 22, weight: .heavy)))
                            .monospacedDigit().foregroundStyle(Theme.change(v)).fixedSize()
                        Text(title + " · " + String(localized: "시작 대비")).appFont(13).foregroundStyle(Theme.muted).lineLimit(1)
                    }
                    Spacer(minLength: 6)
                    // 지금 단위 표시: 손 그림으로 두 손가락 확대를 알린다. 핀치로 넓히면 주봉·월봉으로 바뀐다
                    HStack(spacing: 3) {
                        Text(verbatim: "🤏").font(.system(size: 11))   // 두 기기 같은 그림 (SF 기호는 안드로이드에서 못 써서 이모지)
                        Text(LocalizedStringKey(step == 1 ? "일봉" : step == 5 ? "주봉" : "월봉")).appFont(12, .semibold)
                    }
                    .foregroundStyle(Theme.sub)
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
                        // 장중 오늘 캔들은 흐리게 (아직 끝나지 않은 날)
                        let col = (c >= o ? Theme.up : Theme.down).opacity(series.live && b == n - 1 ? 0.4 : 1)
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
                                let r = CGRect(x: a, y: topH + CGFloat(row) * (bandH / 3) + 1.5, width: max(1, b - a), height: bandH / 3 - 3)
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
                    // 띠 이름: 오른쪽 눈금 칸에 아주 작게 (차트 폭은 그대로)
                    ForEach(Array(["큰", "중간", "작은"].enumerated()), id: \.offset) { row, t in
                        Text(LocalizedStringKey(t)).appFont(8).foregroundStyle(Theme.muted).lineLimit(1).fixedSize()
                            .position(x: W + 4 + 12, y: topH + CGFloat(row) * (bandH / 3) + bandH / 6)
                    }
                    // 띠를 누르면 띠 설명
                    Color.clear.contentShape(Rectangle()).frame(width: W, height: bandH + 6)
                        .offset(y: topH - 3)
                        .onTapGesture { tapAt = CGPoint(x: W / 2, y: topH + bandH); term = .band }
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
                        if let sp = spots["L\(lv.id)"] {
                            Button { tapAt = sp; term = lv.support ? .support : .resistance } label: {
                                Text(levelText(lv))
                                    .appFont(10, .semibold).foregroundStyle(col.opacity(max(0.7, op))).fixedSize()
                                    .padding(.horizontal, 4).background(Theme.card.opacity(0.9), in: Capsule())
                                    .frame(minHeight: 24).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .position(sp)
                        }
                    }
                }
                .opacity(layers.contains(.levels) ? 1 : 0)
                .allowsHitTesting(layers.contains(.levels))

                // 겹 3: 큰 패턴 (구간을 묶는 틀 + 이름표)
                ZStack(alignment: .topLeading) {
                    ForEach(bigs) { pt in
                        let (a, b, t, bt) = box(pt)
                        let col = Theme.purple
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(col.opacity(pt.strong ? 0.9 : 0.45), style: StrokeStyle(lineWidth: pt.strong ? 1.5 : 1, dash: pt.strong ? [] : [4, 3]))
                            .background(RoundedRectangle(cornerRadius: 6).fill(col.opacity(pt.strong ? 0.07 : 0.03)))
                            .frame(width: max(4, b - a), height: max(4, bt - t))
                            .position(x: (a + b) / 2, y: (t + bt) / 2)
                            .allowsHitTesting(false)
                        if let sp = spots["B\(pt.id)"] {
                            tag(pt.kind.title, col, pt.strong) { tapAt = sp; term = .pattern(pt.kind) }.position(sp)
                        }
                    }
                }
                .opacity(layers.contains(.big) ? 1 : 0)
                .allowsHitTesting(layers.contains(.big))

                // 겹 4: 캔들 패턴 (일봉으로 볼 때만. 주봉·월봉에선 캔들 하나가 하루가 아니라 뜻이 달라진다)
                ZStack(alignment: .topLeading) {
                    ForEach(smalls) { pt in
                        if let sp = spots["C\(pt.id)"] {
                            tag(pt.kind.title, Theme.orange, pt.strong) { tapAt = sp; term = .pattern(pt.kind) }.position(sp)
                        }
                    }
                }
                .opacity(layers.contains(.candle) ? 1 : 0)
                .allowsHitTesting(layers.contains(.candle))

                // 아래: 보이는 기간
                Text(date(lo) + " – " + date(hi) + "  ·  " + String(localized: "← 옆으로 밀면 지난 날"))
                    .appFont(10).monospacedDigit().foregroundStyle(Theme.muted).lineLimit(1)
                    .position(x: W / 2, y: g.size.height - botH / 2)

                // 말풍선: 누른 것의 뜻. 처음 한 번은 사용 안내
                if let t = term {
                    bubble(t.title, t.body, at: tapAt, top: top, W: W) { more(t) }
                } else if !hintSeen {
                    bubble(String(localized: "이름표나 선을 눌러 보세요"), String(localized: "누른 것의 뜻이 여기 떠요. 흐린 점선 이름표는 확정 전이거나 거래량이 적었던 것이에요."), at: CGPoint(x: W / 2, y: g.size.height - botH), top: top, W: W, more: nil)
                        .onTapGesture { hintSeen = true }
                }
            }
            .contentShape(Rectangle())
            .onChange(of: term) { if term != nil { hintSeen = true } }
            // 점·선 근처를 누르면 그 뜻. 이름표는 버튼이라 먼저 받는다
            .onTapGesture(coordinateSpace: .local) { loc in
                term = nil; tapAt = loc
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
                        if dragBase == nil { dragBase = hi; term = nil }
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

    /// 누른 자리 바로 위에 띄운다. 차트 위쪽 끝이면 바로 아래로. 가로는 화면 밖으로 안 나가게. 꼬리는 누른 자리를 가리킨다
    private func bubble(_ title: String, _ body: String, at p: CGPoint, top: CGFloat, W: CGFloat, more: (() -> Void)?) -> some View {
        let bw: CGFloat = min(280, W - 8)
        let cx: CGFloat = min(max(p.x, bw / 2 + 4), W - bw / 2 - 4)
        let below: Bool = p.y < top + 110
        let tailX: CGFloat = min(max(p.x - (cx - bw / 2), 16), bw - 16)
        // 꼬리: 바탕색으로 채우고 바깥 두 변만 테두리. 몸통에 1pt 겹쳐 이음매 테두리를 덮는다
        let tail = ZStack {
            FlowTail(up: below).fill(Self.bb)
            FlowTail(up: below, open: true).stroke(Self.bbLine, lineWidth: 1)
        }
        .frame(width: 14, height: 7)
        .frame(width: bw, alignment: .leading).offset(x: tailX - 7, y: below ? 1 : -1).zIndex(1)
        return Color.clear.frame(width: 1, height: 1)
            .overlay(alignment: below ? .top : .bottom) {
                VStack(spacing: 0) {
                    if below { tail }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title).appFont(15, .bold).foregroundStyle(Self.bbTitle)
                        Text(body).appFont(14).foregroundStyle(Self.bbBody).fixedSize(horizontal: false, vertical: true)
                        HStack {
                            Text("정보 제공용 · 매매 권유 아님").appFont(11).foregroundStyle(Self.bbFoot)
                            Spacer(minLength: 4)
                            if let more {
                                Button(action: more) {
                                    Text("자세히 ›").appFont(13, .semibold).foregroundStyle(Self.bbMore)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(10)
                    .frame(width: bw, alignment: .leading)
                    .background(Self.bb, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Self.bbLine))
                    if !below { tail }
                }
                .fixedSize(horizontal: false, vertical: true)
                .compositingGroup()
                .shadow(color: scheme == .dark ? .black.opacity(0.55) : Color(hex: 0x15202B).opacity(0.16), radius: 12, y: 8)
                .offset(x: cx - p.x, y: below ? 8 : -8)
                .onTapGesture { term = nil }
            }
            .position(p)
            .transition(.opacity)
    }

    private func levelText(_ lv: FlowLevel) -> String {
        (lv.support ? String(localized: "지지") : String(localized: "저항")) + " ×\(lv.touches)"
    }
    /// 10pt 굵은 글자 이름표 폭 어림 (한글 10.5, 영문 6.2 + 여백)
    private func tagW(_ t: String) -> CGFloat { t.reduce(CGFloat(14)) { $0 + ($1.isASCII ? 6.2 : 10.5) } }

    struct TagWant { let key: String; let layer: FlowLayer; let strong: Bool; let w: CGFloat; let cands: [CGPoint] }

    /// 이름표 자리 잡기: 지지·저항 → 큰 패턴 → 캔들 패턴 순서로, 켜진 앞 겹 이름표와 이미 놓인 것을 피해
    /// 후보 자리를 차례로 본다. 진한 것부터 놓으니 끝내 겹치면 약한 쪽이 숨는다.
    /// 뒤 겹을 켜고 꺼도 앞 겹 이름표는 움직이지 않는다
    private func placeTags(levels: [TagWant], big: [TagWant], candle: [TagWant], on: Set<FlowLayer>, bounds: CGRect) -> [String: CGPoint] {
        var out: [String: CGPoint] = [:], taken: [CGRect] = []
        for group in [levels, big, candle] {
            var mine: [CGRect] = []
            for t in group.sorted(by: { $0.strong && !$1.strong }) {
                for c in t.cands {
                    let cx = min(max(c.x, bounds.minX + t.w / 2), bounds.maxX - t.w / 2)
                    let cy = min(max(c.y, bounds.minY + 9), bounds.maxY - 9)
                    let r = CGRect(x: cx - t.w / 2, y: cy - 9, width: t.w, height: 18)
                    if !(taken + mine).contains(where: { $0.intersects(r) }) {
                        out[t.key] = CGPoint(x: cx, y: cy); mine.append(r); break
                    }
                }
            }
            // 이 겹이 켜져 있을 때만 뒤 겹이 피한다
            if let l = group.first?.layer, on.contains(l) { taken += mine }
        }
        return out
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

    /// 실데이터는 거래일, DRNK 기록은 어제가 마지막 날로 하루에 한 칸
    private func date(_ i: Int) -> String {
        if let d = series.dates, i < d.count { return d[i].replacingOccurrences(of: "-", with: ".") }
        return Self.fmt.string(from: Date().addingTimeInterval(-Double(series.candles.count - i) * 86400))
    }
    static let fmt: DateFormatter = { let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "yyyy.MM.dd"; return f }()
}

/// 말풍선 꼬리 (작은 삼각형)
struct FlowTail: Shape {
    let up: Bool
    var open = false     // 몸통 쪽 밑변 없이 두 변만 (테두리용)
    func path(in r: CGRect) -> Path {
        var p = Path()
        if up {
            p.move(to: CGPoint(x: r.minX, y: r.maxY)); p.addLine(to: CGPoint(x: r.midX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        } else {
            p.move(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.midX, y: r.maxY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY))
        }
        if !open { p.closeSubpath() }
        return p
    }
}

/// '흐름 읽는 법': 겹마다 항목 한 줄씩, 작은 그림 + 두세 줄 설명
struct FlowGuide: View {
    let focus: String?
    @Environment(\.dismiss) private var dismiss

    private struct Item: Identifiable { let id: String; let title: String; let body: String; var when: String? = nil }
    private var groups: [(String, [Item])] {
        let t = { (x: FlowTerm) in Item(id: x.key, title: x.title, body: x.body) }
        let k = { (x: FlowPattern.Kind) in Item(id: x.rawValue, title: x.title, body: x.body, when: x.when) }
        return [
            (FlowLayer.trend.title, [t(.band), t(.dots)]),
            (FlowLayer.levels.title, [t(.support), t(.resistance)]),
            (FlowLayer.big.title, [k(.doubleTop), k(.doubleBottom), k(.headShoulders), k(.ascTriangle), k(.descTriangle), k(.bullFlag), k(.bearFlag)]),
            (FlowLayer.candle.title, [k(.hammer), k(.shootingStar), k(.bullEngulf), k(.bearEngulf), k(.insideBar)]),
        ]
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center) {
                Text("흐름 읽는 법").appFont(22, .bold).foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.sub)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain).accessibilityLabel("닫기")
            }
            .padding(.leading, 20).padding(.trailing, 8).padding(.top, 10)
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        ForEach(groups.indices, id: \.self) { gi in
                            let g = groups[gi]
                            VStack(alignment: .leading, spacing: 8) {
                                Text(g.0).appFont(14, .semibold).foregroundStyle(Theme.muted).padding(.leading, 4)
                                VStack(spacing: 0) {
                                    ForEach(Array(g.1.enumerated()), id: \.element.id) { i, it in
                                        if i > 0 { Rectangle().fill(Theme.line).frame(height: 1).padding(.leading, 84) }
                                        row(it).id(it.id)
                                    }
                                }
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
                            }
                        }
                        Text("흐린 점선 = 확정 전·거래량 적음 · 투자 권유 아님")
                            .appFont(13).foregroundStyle(Theme.muted).padding(.horizontal, 4)
                    }
                    .padding(16)
                }
                .background(Theme.bg)
                .onAppear { if let focus { proxy.scrollTo(focus, anchor: .center) } }
            }
        }
        .background(Theme.bg)
    }

    private func row(_ it: Item) -> some View {
        let on = it.id == focus
        return HStack(alignment: .top, spacing: 12) {
            FlowMini(key: it.id)
                .frame(width: 56, height: 40)
                .padding(4)
                .background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 3) {
                Text(it.title).appFont(17, .bold).foregroundStyle(Theme.ink)
                Text(it.body).appFont(15).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                if let w = it.when {
                    (Text("이럴 때").fontWeight(.semibold).foregroundColor(Theme.teal) + Text("  " + w).foregroundColor(Theme.sub))
                        .appFont(14).fixedSize(horizontal: false, vertical: true).padding(.top, 2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(on ? Theme.tealBg : .clear, in: RoundedRectangle(cornerRadius: 16))
    }
}

/// 설명 페이지의 작은 그림. 좌표는 0~1 (y 0 = 위)
struct FlowMini: View {
    let key: String

    private static let lines: [String: [CGPoint]] = {
        func pts(_ a: [(CGFloat, CGFloat)]) -> [CGPoint] { a.map { CGPoint(x: $0.0, y: $0.1) } }
        return [
            "dots": pts([(0, 0.85), (0.22, 0.5), (0.38, 0.68), (0.62, 0.3), (0.78, 0.48), (1, 0.12)]),
            "support": pts([(0, 0.25), (0.2, 0.75), (0.4, 0.4), (0.6, 0.75), (0.8, 0.35), (1, 0.75)]),
            "resistance": pts([(0, 0.75), (0.2, 0.25), (0.4, 0.6), (0.6, 0.25), (0.8, 0.65), (1, 0.25)]),
            "doubleTop": pts([(0, 0.9), (0.25, 0.15), (0.5, 0.55), (0.75, 0.15), (1, 0.9)]),
            "doubleBottom": pts([(0, 0.1), (0.25, 0.85), (0.5, 0.45), (0.75, 0.85), (1, 0.1)]),
            "headShoulders": pts([(0, 0.9), (0.18, 0.42), (0.33, 0.65), (0.5, 0.08), (0.67, 0.65), (0.82, 0.42), (1, 0.9)]),
            "ascTriangle": pts([(0, 0.9), (0.2, 0.18), (0.4, 0.72), (0.6, 0.18), (0.8, 0.5), (1, 0.18)]),
            "descTriangle": pts([(0, 0.1), (0.2, 0.82), (0.4, 0.28), (0.6, 0.82), (0.8, 0.5), (1, 0.82)]),
            "bullFlag": pts([(0, 0.95), (0.35, 0.15), (0.5, 0.32), (0.65, 0.22), (0.8, 0.38), (1, 0.05)]),
            "bearFlag": pts([(0, 0.05), (0.35, 0.85), (0.5, 0.68), (0.65, 0.78), (0.8, 0.62), (1, 0.95)]),
        ]
    }()
    /// 캔들 (시가, 고가, 저가, 종가), 0~1 (1 = 높음)
    private static let candles: [String: [(CGFloat, CGFloat, CGFloat, CGFloat)]] = [
        "hammer": [(0.62, 0.72, 0.08, 0.7)],
        "shootingStar": [(0.38, 0.92, 0.3, 0.32)],
        "bullEngulf": [(0.62, 0.68, 0.4, 0.45), (0.38, 0.78, 0.34, 0.72)],
        "bearEngulf": [(0.45, 0.66, 0.4, 0.6), (0.7, 0.74, 0.28, 0.34)],
        "insideBar": [(0.25, 0.95, 0.08, 0.85), (0.45, 0.68, 0.38, 0.62)],
    ]
    /// 겹 선: 지지는 아래, 저항·삼각형 꼭대기는 위
    private static let guide: [String: (CGFloat, Color)] = [
        "support": (0.75, Theme.up), "resistance": (0.25, Theme.down),
        "ascTriangle": (0.18, Theme.purple), "descTriangle": (0.82, Theme.purple),
    ]

    var body: some View {
        Canvas { ctx, size in
            let P = { (p: CGPoint) in CGPoint(x: p.x * size.width, y: p.y * size.height) }
            if key == "band" {
                let rows: [[(CGFloat, CGFloat, Color)]] = [
                    [(0, 1, Theme.up)],
                    [(0, 0.4, Theme.dash), (0.4, 1, Theme.up)],
                    [(0, 0.3, Theme.down), (0.3, 0.7, Theme.up), (0.7, 1, Theme.down)],
                ]
                for (i, r) in rows.enumerated() {
                    for (a, b, c) in r {
                        let rect = CGRect(x: a * size.width + 1, y: CGFloat(i) * size.height / 3 + 3, width: (b - a) * size.width - 2, height: size.height / 3 - 6)
                        ctx.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(c))
                    }
                }
                return
            }
            if let g = Self.guide[key] {
                var l = Path(); l.move(to: P(CGPoint(x: 0, y: g.0))); l.addLine(to: P(CGPoint(x: 1, y: g.0)))
                ctx.stroke(l, with: .color(g.1.opacity(0.7)), lineWidth: 1.5)
            }
            if let pts = Self.lines[key] {
                var l = Path(); l.addLines(pts.map(P))
                let col: Color = key == "dots" || key == "support" || key == "resistance" ? Theme.sub : Theme.purple
                ctx.stroke(l, with: .color(col), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                if key == "dots" {
                    for p in pts { ctx.fill(Path(ellipseIn: CGRect(x: P(p).x - 3, y: P(p).y - 3, width: 6, height: 6)), with: .color(Theme.teal)) }
                }
            }
            if let cs = Self.candles[key] {
                let n = CGFloat(cs.count), bw: CGFloat = 10
                for (i, c) in cs.enumerated() {
                    let x = size.width * (CGFloat(i) + 1) / (n + 1)
                    let Y = { (v: CGFloat) in (1 - v) * size.height }
                    let col = c.3 >= c.0 ? Theme.up : Theme.down
                    var w = Path(); w.move(to: CGPoint(x: x, y: Y(c.1))); w.addLine(to: CGPoint(x: x, y: Y(c.2)))
                    ctx.stroke(w, with: .color(col), lineWidth: 1.2)
                    let top = Y(max(c.0, c.3)), h = max(2, abs(Y(c.0) - Y(c.3)))
                    ctx.fill(Path(CGRect(x: x - bw / 2, y: top, width: bw, height: h)), with: .color(col))
                }
            }
        }
        .accessibilityHidden(true)
    }
}
