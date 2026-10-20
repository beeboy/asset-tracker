import SwiftUI

/// 파도 타기 섬 진행 저장: 섬마다 최고 점수·별 (UserDefaults 정수 몇 개)
/// 섬은 낮잠 해변 → 올라올라 언덕 → 롤러코스터 만 → 멀미 주의 바다 순서로, 앞 섬에서 별 1개를 받으면 다음 섬이 열린다.
enum WaveMap {
    static let minDays = 10   // 이보다 짧으면 섬을 못 만든다
    static let order: [WaveEngine.Island] = [.nap, .climb, .coaster, .choppy]
    private static var d: UserDefaults { SurfCloset.store }   // 처음 읽을 때 키체인에서 되살린다

    static func stars(_ i: WaveEngine.Island) -> Int { d.integer(forKey: "wave.star.\(i.rawValue)") }
    static func best(_ i: WaveEngine.Island) -> Int { d.integer(forKey: "wave.best.\(i.rawValue)") }
    /// 열린 섬 수 (1...4): 앞에서부터 별이 있는 섬 다음까지
    static var opened: Int { min(order.count, 1 + (order.firstIndex { stars($0) == 0 } ?? order.count)) }
    static func isOpen(_ i: WaveEngine.Island) -> Bool { (order.firstIndex(of: i) ?? 0) < opened }

    /// 한 판 결과 적기 (판이 끝날 때). 별은 결과 팝업과 같은 WaveResult.stars. 별·최고 점수는 줄지 않는다
    static func record(_ r: WaveResult) {
        let i = r.course.island
        if r.stars > stars(i) { d.set(r.stars, forKey: "wave.star.\(i.rawValue)") }
        if r.score > best(i) { d.set(r.score, forKey: "wave.best.\(i.rawValue)") }
        SurfCloset.keep()
    }

    /// 3년 자산 라인을 40거래일씩 훑어 섬마다 들어갈 수 있는 시작 위치를 모은다 (3거래일 간격, 한 번만).
    /// 딱 맞는 구간이 없는 섬(예: 주식 위주라 잔잔한 40일이 없으면 낮잠 해변)은 그 섬에 가장 가까운 구간 5개를 쓴다
    static func starts(_ line: [(d: String, v: Double)]) -> [WaveEngine.Island: [Int]] {
        let n = min(WaveEngine.days, line.count)   // 시세가 40거래일보다 짧으면 가진 만큼만
        guard n >= minDays else { return [:] }
        let vs = line.map(\.v)
        var out: [WaveEngine.Island: [Int]] = [:]
        var fit: [(s: Int, sd: Double, dd: Double, total: Double)] = []
        for s in stride(from: 0, through: vs.count - n, by: 3) {
            let v = Array(vs[s..<(s + n)])
            out[WaveEngine.classify(v).0, default: []].append(s)
            var peak = v[0], dd = 0.0
            for x in v { peak = max(peak, x); dd = max(dd, 1 - x / peak) }
            fit.append((s, WaveEngine.stdev(WaveEngine.rets(v)), dd, v.last! / v.first! - 1))
        }
        for i in order where out[i] == nil {
            let ranked: [Int]
            switch i {
            case .nap: ranked = fit.sorted { $0.sd < $1.sd }.map(\.s)
            case .climb: ranked = fit.sorted { $0.total - $0.dd > $1.total - $1.dd }.map(\.s)
            case .coaster: ranked = fit.sorted { $0.dd > $1.dd }.map(\.s)
            case .choppy: ranked = fit.sorted { $0.sd > $1.sd }.map(\.s)
            }
            out[i] = Array(ranked.prefix(5))
        }
        return out
    }

    /// 그 섬 이름으로 코스 만들기 (난이도는 구간 모양으로)
    static func course(_ line: [(d: String, v: Double)], at s: Int, as i: WaveEngine.Island) -> WaveEngine.Course {
        let part = line[s..<(s + min(WaveEngine.days, line.count))], vs = part.map(\.v)
        return .init(dates: part.map(\.d), values: vs, island: i, level: WaveEngine.classify(vs).1)
    }
}

/// 미션 탭 '파도 타기' 첫 화면: 위에 보드 탄 내 캐릭터, 아래로 섬 지도(섬 윤곽 = 내 자산 코스 모양), 지금 섬 카드
struct WaveHomeView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var line: [(d: String, v: Double)] = []
    @State private var starts: [WaveEngine.Island: [Int]] = [:]
    @State private var ready = false
    @State private var sel: WaveEngine.Island?
    @State private var bob = false
    @State private var closet = false
    // 꾸미기 시트에서 고르면 바로 바뀌게 (SurfCloset.board·hat 이 같은 키에 쓴다)
    @AppStorage("surf.board") private var board = 0
    @AppStorage("surf.hat") private var hat = 0
    @State private var play: WaveEngine.Course?
    @State private var tick = 0   // 판이 끝나고 돌아오면 별을 다시 읽는다

    private var current: WaveEngine.Island {
        if let sel, WaveMap.isOpen(sel) { return sel }
        let open = WaveMap.order.prefix(WaveMap.opened)
        return open.first { WaveMap.stars($0) == 0 } ?? open.last ?? .nap
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            rider
            if !ready {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            } else if line.count < WaveMap.minDays {
                DashedCard {
                    Label("섬이 아직 없어요", systemImage: "water.waves").appFont(15, .bold)
                    Text("종목을 넣으면 내 자산 길로 섬이 생겨요").appFont(13)
                }
            } else {
                islands
                islandCard(current)
            }
        }
        .id(tick)
        .task {
            guard !ready else { return }
            let l = m.waveLine
            line = l; starts = WaveMap.starts(l); ready = true
        }
        .sheet(isPresented: $closet) { SurfClosetSheet() }
        .fullScreenCover(item: $play, onDismiss: { tick += 1 }) { WavePlayView(course: $0, onFinish: WaveMap.record) }
    }

    // MARK: 위: 보드 타고 물살 가르는 내 캐릭터 (누르면 꾸미기)

    private var rider: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(colors: [Color(hex: 0xBFE6F7, dark: 0x163A50), Color(hex: 0x5BB8E0, dark: 0x1C4D66)],
                           startPoint: .top, endPoint: .bottom)
            WaveBand(phase: bob ? 1 : 0).fill(Color.white.opacity(0.35)).frame(height: 54)
            WaveBand(phase: bob ? 0 : 1).fill(Color(hex: 0x2F8FD8, dark: 0x2A6F99)).frame(height: 34)
            // 움직이는 그림은 누르는 자리가 애니메이션 끝 위치라 빗나가니, 탭은 바깥 칸 전체로 받는다
            SurfRider(board: board, hat: hat, dot: 2.6)
                .rotationEffect(.degrees(bob ? -5 : 3), anchor: .bottom)
                .offset(x: bob ? 6 : -6, y: bob ? -26 : -18)
                .allowsHitTesting(false)
            // 물보라
            HStack(spacing: 5) { ForEach(0..<3, id: \.self) { i in Circle().fill(.white.opacity(0.85)).frame(width: CGFloat(7 - i * 2)) } }
                .offset(x: -48, y: bob ? -24 : -16)
                .allowsHitTesting(false)
        }
        .frame(height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture { closet = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("보드 탄 내 캐릭터, 눌러서 꾸미기")
        .accessibilityAddTraits(.isButton)
        .overlay(alignment: .topTrailing) {
            Button { closet = true } label: {
                Text("꾸미기").appFont(12, .bold).foregroundStyle(Theme.inkFixed)
                    .padding(.horizontal, 10).padding(.vertical, 4).background(.white.opacity(0.8), in: Capsule())
                    .padding(10).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .onAppear {
            guard !still else { return }
            withAnimation(.easeInOut(duration: 1.3).repeatForever(autoreverses: true)) { bob = true }
        }
    }

    // MARK: 섬 지도: 옆으로 이어진 섬, 점선 뱃길, 안 열린 섬은 안개

    private var islands: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(Array(WaveMap.order.enumerated()), id: \.1) { k, i in
                    if k > 0 { SeaRoute().stroke(Theme.muted, style: StrokeStyle(lineWidth: 2, dash: [4, 5])).frame(width: 28, height: 2) }
                    islandTile(i)
                }
            }
            .padding(.horizontal, 2).padding(.vertical, 4)
        }
    }

    private func islandTile(_ i: WaveEngine.Island) -> some View {
        let open = WaveMap.isOpen(i), on = i == current, st = WaveMap.stars(i)
        let vs = starts[i]?.first.map { WaveMap.course(line, at: $0, as: i).values } ?? []
        return Button { if open { withAnimation(.easeOut(duration: 0.2)) { sel = i } } } label: {
            VStack(spacing: 4) {
                IslandShape(values: vs)
                    .fill(Color(hex: 0xF3DFA2, dark: 0x8C7A4A))
                    .overlay(IslandShape(values: vs, topOnly: true).stroke(Color(hex: 0x5FAE63, dark: 0x3F7A45), lineWidth: 3))
                    .frame(width: 96, height: 56)
                    .background(alignment: .bottom) { Capsule().fill(Color(hex: 0xBFE6F7, dark: 0x1C4D66)).frame(width: 112, height: 12).offset(y: 4) }
                    .blur(radius: open ? 0 : 3).opacity(open ? 1 : 0.45)
                    .overlay { if !open { Image(systemName: "cloud.fill").appFont(26).foregroundStyle(Theme.muted.opacity(0.8)) } }
                Text(open ? i.name : String(localized: "안개 속")).appFont(11, .bold).foregroundStyle(open ? Theme.ink : Theme.muted).lineLimit(1)
                Stars(n: st, size: 9).opacity(open ? 1 : 0)
            }
            .padding(6)
            .background(on ? Theme.forecastBg : .clear, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(on ? Theme.forecast : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .disabled(!open)
        .accessibilityLabel(open ? String(localized: "\(String(describing: i.name)), 별 \(String(describing: st))개") : String(localized: "안개 속 섬, 아직 안 열림"))
    }

    // MARK: 지금 섬 카드

    private func islandCard(_ i: WaveEngine.Island) -> some View {
        let has = !(starts[i] ?? []).isEmpty, best = WaveMap.best(i)
        return Card {
            HStack {
                Text(i.name).appFont(20, .bold)
                Spacer()
                Stars(n: WaveMap.stars(i), size: 16)
            }
            Text(has ? i.line : String(localized: "내 자산 길엔 아직 이런 물결이 없어요")).appFont(14).foregroundStyle(Theme.sub)
                .fixedSize(horizontal: false, vertical: true)
            if best > 0 { Text("최고 \(best)점").appFont(12).foregroundStyle(Theme.muted) }
            Button {
                if let s = starts[i]?.randomElement() { play = WaveMap.course(line, at: s, as: i) }
            } label: {
                Text("타러 가기").appFont(16, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(has ? Theme.forecast : Theme.muted, in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain).disabled(!has).padding(.top, 2)
        }
    }

}

extension WaveEngine.Course: Identifiable { var id: String { "\(island.rawValue)\(startDate)" } }

/// 별 3칸
private struct Stars: View {
    let n: Int
    var size: CGFloat
    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<3, id: \.self) { k in
                Image(systemName: k < n ? "star.fill" : "star").font(.system(size: size, weight: .bold))
                    .foregroundStyle(k < n ? Theme.gold : Theme.muted)
            }
        }
        .accessibilityHidden(true)
    }
}

/// 섬 윤곽: 코스 평가액을 언덕 모양으로 (양 끝은 바닷가로 낮아진다). topOnly면 위 능선만
private struct IslandShape: Shape {
    let values: [Double]
    var topOnly = false
    func path(in r: CGRect) -> Path {
        let vs = values.count > 1 ? values : [1, 1.02, 1.05, 1.03, 1.06, 1.04, 1.02]
        let lo = vs.min()!, hi = vs.max()!, span = max(hi - lo, 1e-9), n = vs.count
        func pt(_ k: Int) -> CGPoint {
            let t = Double(k) / Double(n - 1), taper = sin(Double.pi * t).squareRoot()
            let h = (0.2 + 0.8 * (vs[k] - lo) / span) * taper
            return CGPoint(x: r.minX + r.width * t, y: r.maxY - r.height * h)
        }
        var p = Path()
        p.move(to: pt(0))
        for k in 1..<n { p.addLine(to: pt(k)) }
        if !topOnly { p.addLine(to: CGPoint(x: r.maxX, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY)); p.closeSubpath() }
        return p
    }
}

/// 섬 사이 뱃길 한 줄
private struct SeaRoute: Shape {
    func path(in r: CGRect) -> Path { Path { $0.move(to: CGPoint(x: r.minX, y: r.midY)); $0.addLine(to: CGPoint(x: r.maxX, y: r.midY)) } }
}

/// 출렁이는 물결 띠 (phase 0↔1 사이를 애니메이션)
private struct WaveBand: Shape {
    var phase: Double
    var animatableData: Double { get { phase } set { phase = newValue } }
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        for x in stride(from: 0.0, through: Double(r.width), by: 6) {
            let y = 8 + 6 * sin(x / 34 + phase * .pi)
            p.addLine(to: CGPoint(x: r.minX + x, y: r.minY + y))
        }
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}

/// 미션 탭 [파도] 버튼: 블록 게임처럼 전체 화면으로 섬 지도를 연다 (미션 탭은 섬·코스 계산을 안 한다)
struct WaveGameButton: View {
    var plain = false   // 흰 바탕 · 회색 테두리 (미션 탭 보조 버튼)
    @State private var show = false
    var body: some View {
        Button { show = true } label: {
            Label("파도", systemImage: "water.waves")
                .appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.8).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(plain ? Theme.ink : .white)
                .background(plain ? Theme.card : Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(plain ? Theme.border : .clear, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .fullScreenCover(isPresented: $show) { WaveHub() }
    }
}

/// 섬 지도 전체 화면: 위에 닫기, 아래로 섬 지도
struct WaveHub: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                GameHeader(title: String(localized: "내 자산으로 하는 파도타기"))
                WaveHomeView()
            }
            .screen()   // 벽돌깨기와 같은 여백
        }
        .background(Theme.bg)
    }
}

/// 게임 화면 맨 위 줄 (벽돌깨기·파도 타기 공용): 왼쪽 굵은 제목, 오른쪽 X 닫기
struct GameHeader: View {
    let title: String
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        HStack {
            Text(title).appFont(20, .bold).lineLimit(1).minimumScaleFactor(0.8)
            Spacer()
            Button { dismiss() } label: { Image(systemName: "xmark").appFont(16, .bold).padding(8) }
                .buttonStyle(.plain).accessibilityLabel("닫기")
        }
        .padding(.top, 8)
    }
}

/// 종목 탭 소식판 시장과 비교 밑 [동네] 한 줄: 파도처럼 전체 화면으로 동네를 연다 (종목 탭은 동네를 안 그린다).
/// 방금 종목을 더했으면(town.arrive) 바로 연다. 동네 본문은 부르는 쪽이 준다
struct TownButton<Town: View>: View {
    @ViewBuilder var town: (ScrollViewProxy) -> Town
    @AppStorage("town.arrive") private var arrive = ""
    @State private var show = false
    var body: some View {
        // 홈 리마인드 노란 띠(HomeView.stripRow)와 같은 모양: 노란 꼬리표 + 한 줄 + ›
        Button { show = true } label: {
            HStack(spacing: 8) {
                Text("동네 한 바퀴").appFont(11, .bold).foregroundStyle(Theme.inkFixed).padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Theme.yellow, in: Capsule())
                Text("내 종목 CEO가 한 자리에").appFont(13).lineLimit(2).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").appFont(11).foregroundStyle(Theme.muted)
            }
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 12).frame(minHeight: 44)
            .background(Theme.cream, in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("동네 한 바퀴: 내 종목 CEO가 한 자리에")
        .onAppear { if !arrive.isEmpty { show = true } }
        .onChange(of: arrive) { if !$1.isEmpty { show = true } }
        .fullScreenCover(isPresented: $show) {
            ScrollViewReader { sp in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        GameHeader(title: String(localized: "우리 동네 주민들"))
                        town(sp)
                    }
                    .screen()
                }
                .ignoresSafeArea(.keyboard, edges: .bottom)   // 동네는 키보드에 안 들썩이게
                .scrollDismissesKeyboard(.immediately)
                .background(Theme.bg)
            }
        }
    }
}

/// 아이콘과 글자를 기본보다 붙인 라벨 (미션 탭 [블록][파도])
struct TightLabel: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 3) { configuration.icon; configuration.title }
    }
}
