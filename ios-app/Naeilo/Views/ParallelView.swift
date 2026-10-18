import SwiftUI

// 다른 방식이었다면? (평행 세계의 나). 계산은 Data/Parallel.swift. 홈 비교 카드에서 들어온다.
// 위: 결론 카드, 세 가지 방식 목록(수익률 순, 순위 배지 없음), 출발 후 흐름, 인물 규칙 카드, 기준 문단.
// 색은 화면 사이 고정 규칙(내 실제 투자 초록, 모의 바구니 주황, 인물 규칙 보라 점선). 유도 문구 없음.

enum ParallelUI {
    static let colors: [Color] = [Theme.myReal, Theme.basket, Theme.rule]
    static let short = ["실제", "모의"]
}

struct ParallelView: View {
    @Environment(AppModel.self) private var m
    @State private var prog = 0.0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("평행 세계의 나").appFont(13, .bold).foregroundStyle(Theme.sub)
                if let w = m.pworldShown, let race = m.parallelRace {
                    let r = m.parallelSeries(race)
                    let v = Parallel.verdict(r, at: race.last)
                    verdict(race, r, v, w)
                    ForEach(v.order, id: \.self) { k in row(k, race, r, v, w) }
                    chart(race, r, w)
                    ruleCard(race, r, w)
                    buttons(w)
                    pastBaskets(race, w)
                    if m.isSample { Text("샘플 종목 · 내 종목 넣으면 그날부터 비교").appFont(12).foregroundStyle(Theme.sub) }
                    basis()
                } else {
                    Card {
                        Text(m.parallelBase.isEmpty ? "종목 탭에서 수량을 넣으면 비교 시작" : "지난 종가 받는 중 · 잠시 뒤 다시")
                            .appFont(14).foregroundStyle(Theme.sub)
                    }
                    if m.isSample { Text("샘플 종목 · 내 종목 넣으면 그날부터 비교").appFont(12).foregroundStyle(Theme.sub) }
                    Text("모의 결과 · 투자 권유 아님").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("다른 방식이었다면?").navigationBarTitleDisplayMode(.inline)
        .onAppear {
            m.openParallelIfNeeded()
            m.parallelTick()
            prog = 0
            withAnimation(.easeInOut(duration: 2.4)) { prog = 1 }
        }
        .task {
            await Market.shared.refresh(held: Set(m.holdings.map(\.symbol)))
            m.parallelTick()
        }
    }

    private func who(_ w: PWorld) -> String { m.friendName(w.who) }
    private func title(_ k: Int, _ w: PWorld) -> String { m.parallelTitle(k, who: w.who) }
    private func shortName(_ k: Int, _ w: PWorld) -> String { k == 2 ? who(w) : ParallelUI.short[k] }

    // MARK: 결론 카드 (진한 바탕)
    private func verdict(_ race: PRace, _ r: [[Double]], _ v: PVerdict, _ w: PWorld) -> some View {
        let t = race.last
        let start = Self.md(race.dates.first ?? "")
        let first = (m.isGoal ? "" : "최저점 ") + "\(start) · 같은 \(AppModel.eok(race.V0))으로 출발"
        let lead = title(v.order[0], w)
        let head = v.similar ? "셋 다 비슷해요" : "\(lead)\(WChar.josa(lead)) 앞서요"
        let gap = String(format: "%.1f%%p", v.gap * 100) + " · " + AppModel.aboutWon(race.V0 * v.gap)
        let goalPcts = Set((0..<3).map { AppModel.pct(min(1, race.V0 * (1 + r[$0][t]) / max(1, m.gK * 1e4))) })
        let mid = race.V0 * (1 + r[v.order[1]][t])
        return VStack(alignment: .leading, spacing: 8) {
            Text(first).appFont(13).foregroundStyle(Color.white.opacity(0.8)).fixedSize(horizontal: false, vertical: true)
            Text(head).appFont(22, .bold).foregroundStyle(.white)
            Text("차이 **\(gap)** · 몇 달 더 지켜봐요")
                .appFont(13).foregroundStyle(Color.white.opacity(0.85)).fixedSize(horizontal: false, vertical: true)
            if m.isGoal && goalPcts.count == 1, let p = goalPcts.first {
                Text("셋 다 목표 \(p)").appFont(13, .bold).foregroundStyle(.white)
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.2))
                        Capsule().fill(Color.white).frame(width: g.size.width * min(1, mid / max(1, m.gK * 1e4)))
                    }
                }
                .frame(height: 6).accessibilityHidden(true)
            }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.myReal, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }

    // MARK: 세 가지 방식 (수익률 높은 순, 1등만 회색 칩)
    private func row(_ k: Int, _ race: PRace, _ r: [[Double]], _ v: PVerdict, _ w: PWorld) -> some View {
        let t = race.last, ret = r[k][t], amount = race.V0 * (1 + ret)
        let goalSame = m.isGoal && Set((0..<3).map { AppModel.pct(min(1, race.V0 * (1 + r[$0][t]) / max(1, m.gK * 1e4))) }).count == 1
        return HStack(alignment: .center, spacing: 10) {
            RoundedRectangle(cornerRadius: 2).fill(ParallelUI.colors[k]).frame(width: 4).frame(maxHeight: .infinity)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(title(k, w)).appFont(15, .bold)
                    if k == v.order[0] && !v.similar {
                        Text("1위").appFont(11, .bold).foregroundStyle(Theme.sub)
                            .padding(.horizontal, 7).padding(.vertical, 2).background(Theme.line, in: Capsule())
                    }
                }
                Text(desc(k, w)).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                if !goalSame { Text(m.parallelDistance(amount)).appFont(12).foregroundStyle(Theme.muted) }
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 2) {
                Text(AppModel.sgn(ret)).appFont(17, .bold).foregroundStyle(Theme.change(ret))
                Text(AppModel.eok(amount)).appFont(12).foregroundStyle(Theme.sub)
            }
        }
        .padding(12).fixedSize(horizontal: false, vertical: true)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
        .accessibilityElement(children: .combine)
    }
    private func desc(_ k: Int, _ w: PWorld) -> String {
        switch k {
        case 0: return "지금 종목 그대로"
        case 1:
            let parts = (w.segs.last?.w ?? [:]).sorted { $0.value > $1.value }.map { "\($0.key) \(Int(($0.value * 100).rounded()))" }
            return parts.joined(separator: " · ")
        default: return Parallel.rules[w.who] ?? ""
        }
    }

    // MARK: 출발 후 수익률 흐름 (이 앱을 연 날 앞은 흐리게)
    private func chart(_ race: PRace, _ r: [[Double]], _ w: PWorld) -> some View {
        let all: [Double] = r.flatMap { $0 }
        let lo = min(0, all.min() ?? 0), hi = max(0, all.max() ?? 0)
        let span = max(1e-6, hi - lo)
        let n = race.dates.count, t = race.last
        let openX: Double = n > 1 ? Double(min(race.open, n - 1)) / Double(n - 1) : 1
        let pts: [[CGPoint]] = r.map { Self.points($0, hi: hi, span: span) }
        let zeroY = hi / span
        let ends = [0, 1, 2].sorted { r[$0][t] > r[$1][t] }
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("출발 후 수익률").appFont(15, .bold)
                Spacer()
                Text("최대 차이 \(String(format: "%.1f%%p", Self.maxSpread(r, race) * 100))").appFont(12).foregroundStyle(Theme.sub)
            }
            HStack(alignment: .top, spacing: 8) {
                GeometryReader { g in
                    ZStack(alignment: .topLeading) {
                        Path { p in p.move(to: CGPoint(x: 0, y: zeroY * g.size.height)); p.addLine(to: CGPoint(x: g.size.width, y: zeroY * g.size.height)) }
                            .stroke(Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                        if race.open < n && race.open > 0 {
                            Path { p in p.move(to: CGPoint(x: openX * g.size.width, y: 0)); p.addLine(to: CGPoint(x: openX * g.size.width, y: g.size.height)) }
                                .stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                            Text("앱 연 날").appFont(10).foregroundStyle(Theme.muted)
                                .position(x: min(g.size.width - 34, max(34, openX * g.size.width)), y: 6)
                        }
                        ForEach(0..<3, id: \.self) { k in
                            // 인물 규칙은 규칙이 움직이기 전까지 내 실제 투자와 같은 길이라 점선으로 위에 그린다
                            RaceLine(pts: pts[k], from: 0, progress: prog).stroke(ParallelUI.colors[k].opacity(0.35), style: Self.lineStyle(k, 2))
                            RaceLine(pts: pts[k], from: openX, progress: prog).stroke(ParallelUI.colors[k], style: Self.lineStyle(k, 2.5))
                        }
                    }
                }
                .frame(height: 160)
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(ends, id: \.self) { k in
                        Text("\(shortName(k, w)) \(AppModel.sgn(r[k][t]))").appFont(11, .bold).foregroundStyle(ParallelUI.colors[k])
                    }
                }
                .fixedSize()
            }
            HStack {
                Text(Self.mdShort(race.dates.first ?? "")).appFont(11).foregroundStyle(Theme.muted)
                Spacer()
                Text("0% = 출발").appFont(11).foregroundStyle(Theme.muted)
                Spacer()
                Text(Self.mdShort(race.dates.last ?? "")).appFont(11).foregroundStyle(Theme.muted)
            }
            if Self.sameRoad(race) {
                Text("\(who(w)) 규칙 아직 실행 전 · 실제와 같은 길").appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
        }
    }
    /// 세 선이 가장 크게 벌어진 차이
    static func maxSpread(_ r: [[Double]], _ race: PRace) -> Double {
        var spread = 0.0
        for t in race.dates.indices { spread = max(spread, (0..<3).map { r[$0][t] }.max()! - (0..<3).map { r[$0][t] }.min()!) }
        return spread
    }

    // MARK: 인물 규칙 카드
    private func ruleCard(_ race: PRace, _ r: [[Double]], _ w: PWorld) -> some View {
        let f = Shelter.friends.first { $0.id == w.who }
        let place = Parallel.verdict(r, at: race.last).order.firstIndex(of: 2).map { $0 + 1 } ?? 2
        return Card {
            Text("\(who(w)) 규칙").appFont(15, .bold)
            ForEach(Array((f?.ruleSteps ?? []).enumerated()), id: \.offset) { i, s in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(i + 1)").appFont(12, .bold).foregroundStyle(.white)
                        .frame(width: 20, height: 20).background(Theme.rule, in: Circle())
                    Text(Self.md(bold: s)).appFont(14).fixedSize(horizontal: false, vertical: true)
                }
            }
            if w.who == "sua" && race.contrib > 0 {
                Text("월 \(AppModel.wonK(m.gM)) 적립분 \(AppModel.eok(race.contrib)) 포함").appFont(12).foregroundStyle(Theme.sub)
            }
            Text("\(who(w)): “\(Parallel.line(w.who, place: place, day: Day.today))”").appFont(13).lineSpacing(3)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 12))
                .fixedSize(horizontal: false, vertical: true)
            NavigationLink(value: "char:\(w.who):invest") {
                Text("과거 폭락장에선? ›").appFont(13, .bold).foregroundStyle(Theme.rule).frame(minHeight: 44)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: 보조 버튼 두 개
    @ViewBuilder private func buttons(_ w: PWorld) -> some View {
        if m.pworld != nil {
            HStack(spacing: 8) {
                NavigationLink(value: "parallel:basket") { pill("바구니 바꾸기") }.buttonStyle(.plain)
                Menu {
                    ForEach(Shelter.friends.filter { $0.id != w.who && $0.id != m.calm.type }, id: \.id) { f in
                        Button(f.name + " · " + f.does) { m.pickWho(f.id) }
                    }
                } label: { pill("다른 인물 비교") }
            }
        }
    }
    private func pill(_ s: String) -> some View {
        Text(s).appFont(13, .bold).foregroundStyle(Theme.ink)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
    }

    // MARK: 기준 문단
    private func basis() -> some View {
        Text("어제 종가 기준 · 입출금 제외 · 투자 권유 아님")
            .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
    }

    // MARK: 지난 바구니 (고친 결과로 과거 기록을 덮지 않는다)
    @ViewBuilder private func pastBaskets(_ race: PRace, _ w: PWorld) -> some View {
        let segs = w.segs.sorted { $0.from < $1.from }
        if segs.count > 1 {
            Card {
                Text("지난 바구니").appFont(15, .bold)
                ForEach(Array(segs.dropLast().enumerated()), id: \.offset) { i, g in
                    let end = segs[i + 1].from
                    let ret: Double = i < race.segRet.count ? race.segRet[i] : 0
                    let parts: [String] = g.w.sorted { $0.value > $1.value }.map { "\($0.key) \(AppModel.pct($0.value))" }
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text((i == 0 ? "출발" : Self.md(g.from)) + " ~ " + Self.md(end)).appFont(13, .bold)
                            Spacer()
                            Text(AppModel.sgn(ret)).appFont(13, .bold).foregroundStyle(ret >= 0 ? Theme.up : Theme.down)
                        }
                        Text(parts.joined(separator: " · ")).appFont(12).foregroundStyle(Theme.sub)
                    }
                }
            }
        }
    }

    /// 인물 규칙이 지금까지 내 실제 투자와 같은 길인지 (규칙이 한 번도 움직이지 않음)
    static func sameRoad(_ race: PRace) -> Bool {
        var d = 0.0
        for t in race.dates.indices { d = max(d, abs(race.r[0][t] - race.r[2][t])) }
        return d < 0.0005
    }

    /// 내 실제 투자는 굵게, 인물 규칙은 점선 (두 줄이 겹쳐도 둘 다 보이게)
    static func lineStyle(_ k: Int, _ w: CGFloat) -> StrokeStyle {
        k == 0 ? StrokeStyle(lineWidth: w + 1.5, lineCap: .round, lineJoin: .round)
            : k == 2 ? StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round, dash: [5, 4])
            : StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round)
    }

    /// 그래프 점 (x 0~1 날짜 순서, y 0~1 위가 높은 수익률)
    static func points(_ s: [Double], hi: Double, span: Double) -> [CGPoint] {
        let n = s.count
        var out: [CGPoint] = []
        for t in 0..<n {
            let x: Double = n > 1 ? Double(t) / Double(n - 1) : 0
            out.append(CGPoint(x: x, y: (hi - s[t]) / span))
        }
        return out
    }

    static func md(_ d: String) -> String {
        guard let x = Day.date(d) else { return d }
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "yy.M.d"
        return f.string(from: x)
    }
    /// 그래프 축: "9.11"
    static func mdShort(_ d: String) -> String {
        guard let x = Day.date(d) else { return d }
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M.d"
        return f.string(from: x)
    }
    /// "**굵게**" 표시가 든 문구
    static func md(bold s: String) -> AttributedString { (try? AttributedString(markdown: s)) ?? AttributedString(s) }
}

/// 한 줄을 x(0~1) 기준으로 progress 까지만 그린다. from 앞은 그리지 않는다 (본선 진하게)
struct RaceLine: Shape {
    let pts: [CGPoint]
    let from: Double
    var progress: Double
    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }
    func path(in rect: CGRect) -> Path {
        var p = Path()
        var started = false
        for q in pts where q.x >= from - 1e-9 && q.x <= progress + 1e-9 {
            let c = CGPoint(x: q.x * rect.width, y: q.y * rect.height)
            if started { p.addLine(to: c) } else { p.move(to: c); started = true }
        }
        return p
    }
}

/// 모의 바구니 고치기: 견본에서 시작해 비중(%)으로 고친다. 저장하면 오늘부터 새 기록
struct ParallelBasketView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var w: [String: Int] = [:]
    @State private var loaded = false

    var body: some View {
        let sum = w.values.reduce(0, +)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("고치면 오늘부터 새 기록 · 이전 기록은 보관")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                Text("견본").appFont(15, .bold)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Parallel.templates, id: \.id) { tpl in
                            Button { apply(tpl.w) } label: {
                                Text(tpl.name).appFont(13, .bold).padding(.horizontal, 14).frame(minHeight: 40)
                                    .background(Theme.card, in: Capsule()).overlay(Capsule().stroke(Theme.border, lineWidth: 2))
                            }.buttonStyle(.plain)
                        }
                    }
                }
                Card {
                    ForEach(w.keys.sorted(), id: \.self) { k in row(k) }
                    if w.isEmpty { Text("담은 종목 없음").appFont(13).foregroundStyle(Theme.sub) }
                    Menu {
                        ForEach(Parallel.universe.filter { w[$0.id] == nil }, id: \.id) { u in
                            Button(u.id + " · " + u.name + (Parallel.has(u.id) ? "" : " (시세 준비 중)")) { w[u.id] = 10 }
                                .disabled(!Parallel.has(u.id))
                        }
                    } label: {
                        Text("+ 종목 담기").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 40)
                    }
                    Text("종가 있는 종목·견본 ETF만").appFont(12).foregroundStyle(Theme.muted)
                }
                HStack {
                    Text("합계").appFont(14, .bold); Spacer()
                    Text("\(sum)%").appFont(14, .bold).foregroundStyle(sum == 100 ? Theme.green : Theme.orange)
                }
                if sum != 100 && sum > 0 { Text("저장 시 비율대로 100% 맞춤").appFont(12).foregroundStyle(Theme.sub) }
                Button {
                    m.saveBasket(w.mapValues { Double($0) })
                    dismiss()
                } label: {
                    Text("오늘부터 시작").appFont(15, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(sum > 0 ? Theme.teal : Theme.muted, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain).disabled(sum <= 0)
                Text("모의 결과 · 투자 권유 아님").appFont(12).foregroundStyle(Theme.muted)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("모의 바구니 고치기").navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            apply(m.pworld?.segs.last?.w ?? [:])
        }
        .task { await Market.shared.fetchParallelUniverse() }
    }

    private func apply(_ x: [String: Double]) {
        var out: [String: Int] = [:]
        for (k, v) in Parallel.resolve(x) { out[k] = Int((v * 100).rounded()) }
        w = out
    }

    private func row(_ k: String) -> some View {
        let v = w[k] ?? 0
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(k).appFont(14, .bold)
                Text(Parallel.name(k)).appFont(11).foregroundStyle(Theme.sub)
            }
            Spacer()
            Button { w[k] = max(0, v - 5) } label: { Image(systemName: "minus").frame(width: 36, height: 36) }
                .buttonStyle(.plain).accessibilityLabel("\(k) 5%p 줄이기")
            Text("\(v)%").appFont(15, .bold).frame(minWidth: 48)
            Button { w[k] = min(100, v + 5) } label: { Image(systemName: "plus").frame(width: 36, height: 36) }
                .buttonStyle(.plain).accessibilityLabel("\(k) 5%p 늘리기")
            Button { w[k] = nil } label: { Image(systemName: "xmark").foregroundStyle(Theme.muted).frame(width: 32, height: 36) }
                .buttonStyle(.plain).accessibilityLabel("\(k) 빼기")
        }
    }
}

/// 홈 비교 카드: 다른 방식이었다면? (처음 진입도 여기서. 쉼터 문은 뺐다)
struct ParallelHomeCard: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        if !m.parallelBase.isEmpty {
            Button { m.homePath.append("parallel") } label: {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("다른 방식이었다면?").appFont(15, .bold)
                        if let v = m.parallelVerdictLine { Text(v).appFont(13, .semibold).foregroundStyle(Theme.myReal) }
                    }
                    Spacer(minLength: 0)
                    Text("›").appFont(16, .bold).foregroundStyle(Theme.sub)
                }
                .padding(14).frame(minHeight: 44)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
        }
    }
}
