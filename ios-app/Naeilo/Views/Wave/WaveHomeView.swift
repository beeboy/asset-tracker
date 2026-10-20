import SwiftUI

/// 파도 타기 섬 진행 저장: 섬마다 최고 점수·별 (UserDefaults 정수 몇 개)
/// 섬은 낮잠 해변 → 올라올라 언덕 → 롤러코스터 만 → 멀미 주의 바다 순서로, 앞 섬에서 별 1개를 받으면 다음 섬이 열린다.
enum WaveMap {
    static let order: [WaveEngine.Island] = [.nap, .climb, .coaster, .choppy]
    /// 별 기준: 끝까지 타면 1개, 200점(약 2%) 2개, 500점 3개
    static let starScores = [200, 500]
    private static let d = UserDefaults.standard

    static func stars(_ i: WaveEngine.Island) -> Int { d.integer(forKey: "wave.star.\(i.rawValue)") }
    static func best(_ i: WaveEngine.Island) -> Int { d.integer(forKey: "wave.best.\(i.rawValue)") }
    /// 열린 섬 수 (1...4): 앞에서부터 별이 있는 섬 다음까지
    static var opened: Int { min(order.count, 1 + (order.firstIndex { stars($0) == 0 } ?? order.count)) }
    static func isOpen(_ i: WaveEngine.Island) -> Bool { (order.firstIndex(of: i) ?? 0) < opened }

    /// 한 판 결과 적기 (플레이 화면이 판이 끝날 때 부른다). 별·최고 점수는 줄지 않는다
    static func record(_ i: WaveEngine.Island, score: Int, finished: Bool) {
        let s = finished ? 1 + starScores.filter { score >= $0 }.count : 0
        if s > stars(i) { d.set(s, forKey: "wave.star.\(i.rawValue)") }
        if score > best(i) { d.set(score, forKey: "wave.best.\(i.rawValue)") }
    }

    /// 3년 자산 라인을 40거래일씩 훑어 섬마다 들어갈 수 있는 시작 위치를 모은다 (3거래일 간격, 한 번만)
    static func starts(_ line: [(d: String, v: Double)]) -> [WaveEngine.Island: [Int]] {
        let n = WaveEngine.days
        guard line.count >= n else { return [:] }
        let vs = line.map(\.v)
        var out: [WaveEngine.Island: [Int]] = [:]
        for s in stride(from: 0, through: vs.count - n, by: 3) {
            out[WaveEngine.classify(Array(vs[s..<(s + n)])).0, default: []].append(s)
        }
        return out
    }

    static func course(_ line: [(d: String, v: Double)], at s: Int) -> WaveEngine.Course {
        let part = line[s..<(s + WaveEngine.days)], vs = part.map(\.v)
        let (i, lv) = WaveEngine.classify(vs)
        return .init(dates: part.map(\.d), values: vs, island: i, level: lv)
    }
}

/// 미션 탭 '파도 타기' 첫 화면: 위에 보드 탄 내 캐릭터, 아래로 섬 지도(섬 윤곽 = 내 자산 코스 모양), 지금 섬 카드, 예보 한 줄
struct WaveHomeView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var line: [(d: String, v: Double)] = []
    @State private var starts: [WaveEngine.Island: [Int]] = [:]
    @State private var ready = false
    @State private var sel: WaveEngine.Island?
    @State private var bob = false
    @State private var closet = false
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
            } else if line.count < WaveEngine.days {
                DashedCard {
                    Label("섬이 아직 없어요", systemImage: "water.waves").appFont(15, .bold)
                    Text("종목을 넣으면 내 자산 길로 섬이 생겨요").appFont(13)
                }
            } else {
                islands
                islandCard(current)
            }
            forecastLine
        }
        .id(tick)
        .task {
            guard !ready else { return }
            let l = m.waveLine
            line = l; starts = WaveMap.starts(l); ready = true
        }
        .sheet(isPresented: $closet) { SurfClosetSheet() }
        .fullScreenCover(item: $play, onDismiss: { tick += 1 }) { WavePlaySlot(course: $0) }
    }

    // MARK: 위: 보드 타고 물살 가르는 내 캐릭터 (누르면 꾸미기)

    private var rider: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(colors: [Color(hex: 0xBFE6F7, dark: 0x163A50), Color(hex: 0x5BB8E0, dark: 0x1C4D66)],
                           startPoint: .top, endPoint: .bottom)
            WaveBand(phase: bob ? 1 : 0).fill(Color.white.opacity(0.35)).frame(height: 54)
            WaveBand(phase: bob ? 0 : 1).fill(Color(hex: 0x2F8FD8, dark: 0x2A6F99)).frame(height: 34)
            Button { closet = true } label: {
                SurfRider(dot: 2.6)
                    .rotationEffect(.degrees(bob ? -5 : 3), anchor: .bottom)
                    .offset(x: bob ? 6 : -6, y: bob ? -26 : -18)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("보드 탄 내 캐릭터, 눌러서 꾸미기")
            // 물보라
            HStack(spacing: 5) { ForEach(0..<3, id: \.self) { i in Circle().fill(.white.opacity(0.85)).frame(width: CGFloat(7 - i * 2)) } }
                .offset(x: -48, y: bob ? -24 : -16)
        }
        .frame(height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(alignment: .topTrailing) {
            Text("꾸미기").appFont(12, .bold).foregroundStyle(Theme.inkFixed)
                .padding(.horizontal, 10).padding(.vertical, 4).background(.white.opacity(0.8), in: Capsule()).padding(10)
                .allowsHitTesting(false)
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
        let vs = starts[i]?.first.map { WaveMap.course(line, at: $0).values } ?? []
        return Button { if open { withAnimation(.easeOut(duration: 0.2)) { sel = i } } } label: {
            VStack(spacing: 4) {
                IslandShape(values: vs)
                    .fill(Color(hex: 0xF3DFA2, dark: 0x8C7A4A))
                    .overlay(IslandShape(values: vs, topOnly: true).stroke(Color(hex: 0x5FAE63, dark: 0x3F7A45), lineWidth: 3))
                    .frame(width: 96, height: 56)
                    .background(alignment: .bottom) { Capsule().fill(Color(hex: 0xBFE6F7, dark: 0x1C4D66)).frame(width: 112, height: 12).offset(y: 4) }
                    .blur(radius: open ? 0 : 3).opacity(open ? 1 : 0.45)
                    .overlay { if !open { Image(systemName: "cloud.fill").appFont(26).foregroundStyle(Theme.muted.opacity(0.8)) } }
                Text(open ? i.name : "안개 속").appFont(11, .bold).foregroundStyle(open ? Theme.ink : Theme.muted).lineLimit(1)
                Stars(n: st, size: 9).opacity(open ? 1 : 0)
            }
            .padding(6)
            .background(on ? Theme.forecastBg : .clear, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(on ? Theme.forecast : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .disabled(!open)
        .accessibilityLabel(open ? "\(i.name), 별 \(st)개" : "안개 속 섬, 아직 안 열림")
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
            Text(has ? i.line : "내 자산 길엔 아직 이런 물결이 없어요").appFont(14).foregroundStyle(Theme.sub)
                .fixedSize(horizontal: false, vertical: true)
            if best > 0 { Text("최고 \(best)점").appFont(12).foregroundStyle(Theme.muted) }
            Button {
                if let s = starts[i]?.randomElement() { play = WaveMap.course(line, at: s) }
            } label: {
                Text("타러 가기").appFont(16, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(has ? Theme.forecast : Theme.muted, in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain).disabled(!has).padding(.top, 2)
        }
    }

    // MARK: 예보 한 줄

    @ViewBuilder private var forecastLine: some View {
        if !m.rows.isEmpty {
            let r = m.weekRange
            let d = WeekForecastCard.eok(r.lo, 1) == WeekForecastCard.eok(r.hi, 1) ? 2 : 1
            Label("이번 주 예보 \(WeekForecastCard.eok(r.lo, d)) ~ \(WeekForecastCard.eok(r.hi, d))", systemImage: "sun.max.fill")
                .appFont(13, .semibold).foregroundStyle(Theme.forecast)
                .padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
                .background(Theme.forecastBg, in: RoundedRectangle(cornerRadius: 12))
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

/// 플레이 화면 자리: 다른 작업이 WavePlayView를 만들면 이 본문을 WavePlayView(course:)로 바꾼다
private struct WavePlaySlot: View {
    let course: WaveEngine.Course
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 16) {
            Text(course.island.name).appFont(22, .bold)
            Text("\(course.startDate)부터 \(course.count)거래일 · \(course.level.name)").appFont(14).foregroundStyle(Theme.sub)
            Text("플레이 화면 준비 중").appFont(14).foregroundStyle(Theme.muted)
            Button("닫기") { dismiss() }.appFont(16, .bold).frame(minHeight: 44)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.bg)
    }
}
