import SwiftUI

// 평행 세계의 나 화면 (계산은 Data/Parallel.swift). 쉼터의 문에서 들어온다.
// 위: 오늘 순위, 가운데: 세 줄 그래프 (예선은 흐리게, 본선은 진하게), 아래: 세 명 카드.
// 매매 내역은 보여 주지 않고, 인물 세계의 변화는 궁금할 때만 비율 요약으로. 유도 문구 없음.

enum ParallelUI {
    static let colors: [Color] = [Theme.teal, Theme.orange, Theme.purple]
}

struct ParallelView: View {
    @Environment(AppModel.self) private var m
    @State private var prog = 0.0
    @State private var ratio = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("어느 쪽이 이겨도 나예요. 세 명의 내가 같은 날 같은 금액에서 출발해 실제 종가로 달려요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                if let w = m.pworldShown, let race = m.parallelRace {
                    let r = m.parallelSeries(race)
                    ranks(race, r, w)
                    chart(race, r)
                    ForEach(0..<3, id: \.self) { k in card(k, race, r, w) }
                    pastBaskets(race, w)
                } else {
                    Card {
                        Text(m.parallelBase.isEmpty ? "종목 탭에서 내 종목 수량을 넣으면 문이 열려요." : "지난 종가를 받는 중이에요. 잠시 뒤 다시 열어 주세요.")
                            .appFont(14).foregroundStyle(Theme.sub)
                    }
                }
                if m.isSample { Text("지금은 샘플 종목으로 보여 줘요. 내 종목을 넣으면 그날부터 내 경주가 시작돼요.").appFont(12).foregroundStyle(Theme.sub) }
                Text("모의 결과이며 투자 권유가 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("평행 세계의 나").navigationBarTitleDisplayMode(.inline)
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
    private func title(_ k: Int, _ w: PWorld) -> String { k == 2 ? "\(who(w)) 세계의 나" : Parallel.names[k] }

    // MARK: 오늘 순위 (어제와 비교한 ↑↓)
    private func ranks(_ race: PRace, _ r: [[Double]], _ w: PWorld) -> some View {
        let t = race.last, y = max(0, t - 1)
        let order: [Int] = [0, 1, 2].sorted { r[$0][t] > r[$1][t] }
        let yOrder: [Int] = [0, 1, 2].sorted { r[$0][y] > r[$1][y] }
        return HStack(spacing: 6) {
            ForEach(Array(order.enumerated()), id: \.element) { i, k in
                let before = (yOrder.firstIndex(of: k) ?? i)
                let mark = before > i ? "↑" : before < i ? "↓" : ""
                VStack(spacing: 3) {
                    Text("\(i + 1)위" + (mark.isEmpty ? "" : " " + mark)).appFont(12, .bold)
                        .foregroundStyle(i == 0 ? Theme.inkFixed : Theme.ink)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(i == 0 ? Theme.yellow : Theme.line, in: Capsule())
                    Text(title(k, w)).appFont(12, .bold).lineLimit(1).minimumScaleFactor(0.7)
                    Text(AppModel.sgn(r[k][t])).appFont(13, .bold).foregroundStyle(ParallelUI.colors[k])
                }
                .frame(maxWidth: .infinity).padding(.vertical, 8)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(i == 0 ? Theme.yellow : Theme.border, lineWidth: i == 0 ? 2 : 1))
                .accessibilityElement(children: .combine)
            }
        }
    }

    // MARK: 세 줄 그래프
    private func chart(_ race: PRace, _ r: [[Double]]) -> some View {
        let all: [Double] = r.flatMap { $0 }
        let lo = min(0, all.min() ?? 0), hi = max(0, all.max() ?? 0)
        let span = max(1e-6, hi - lo)
        let n = race.dates.count
        let openX: Double = n > 1 ? Double(min(race.open, n - 1)) / Double(n - 1) : 1
        let pts: [[CGPoint]] = r.map { Self.points($0, hi: hi, span: span) }
        let zeroY = hi / span
        return Card {
            HStack {
                Text("과거 (예선)").appFont(11, .bold).foregroundStyle(Theme.muted)
                Spacer()
                Text(race.open < n ? "본선 · 연 날부터" : "본선은 연 날부터 쌓여요").appFont(11, .bold).foregroundStyle(Theme.sub)
            }
            GeometryReader { g in
                ZStack(alignment: .topLeading) {
                    Path { p in p.move(to: CGPoint(x: 0, y: zeroY * g.size.height)); p.addLine(to: CGPoint(x: g.size.width, y: zeroY * g.size.height)) }
                        .stroke(Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                    if race.open < n {
                        Path { p in p.move(to: CGPoint(x: openX * g.size.width, y: 0)); p.addLine(to: CGPoint(x: openX * g.size.width, y: g.size.height)) }
                            .stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                    }
                    ForEach(0..<3, id: \.self) { k in
                        // 인물 세계는 규칙이 움직이기 전까지 진짜 나와 같은 길이라 점선으로 위에 그린다 (밑의 진짜 나가 보이게)
                        RaceLine(pts: pts[k], from: 0, progress: prog).stroke(ParallelUI.colors[k].opacity(0.55), style: Self.lineStyle(k, 2))
                        RaceLine(pts: pts[k], from: openX, progress: prog).stroke(ParallelUI.colors[k], style: Self.lineStyle(k, 2.5))
                    }
                }
            }
            .frame(height: 170)
            .accessibilityHidden(true)
            HStack {
                Text(Self.md(race.dates.first ?? "")).appFont(11).foregroundStyle(Theme.muted)
                Spacer()
                Text("0% = 출발 · " + Self.md(race.dates.last ?? "")).appFont(11).foregroundStyle(Theme.muted)
            }
            HStack(spacing: 12) {
                ForEach(0..<3, id: \.self) { k in
                    HStack(spacing: 4) {
                        Path { p in p.move(to: CGPoint(x: 0, y: 2)); p.addLine(to: CGPoint(x: 16, y: 2)) }
                            .stroke(ParallelUI.colors[k], style: Self.lineStyle(k, 3)).frame(width: 16, height: 4)
                        Text(k == 2 ? "인물 세계" : Parallel.names[k]).appFont(11).foregroundStyle(Theme.sub)
                    }
                }
            }
            Text("\(Self.md(race.dates.first ?? ""))에 \(AppModel.eok(race.V0))로 같이 출발했어요." + (m.isGoal ? " 출발일은 목표 시작일이에요." : " 출발일은 지금 종목으로 본 평가액이 가장 낮았던 날이에요."))
                .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: 세 명 카드
    private func card(_ k: Int, _ race: PRace, _ r: [[Double]], _ w: PWorld) -> some View {
        let t = race.last, ret = r[k][t], amount = race.V0 * (1 + ret)
        let place = [0, 1, 2].sorted { r[$0][t] > r[$1][t] }.firstIndex(of: k).map { $0 + 1 } ?? 1
        return Card {
            HStack(alignment: .center, spacing: 10) {
                avatar(k, w)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title(k, w)).appFont(15, .bold)
                    Text(sub(k, w)).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(AppModel.sgn(ret)).appFont(17, .bold).foregroundStyle(ret >= 0 ? Theme.up : Theme.down)
                    Text(AppModel.eok(amount)).appFont(12).foregroundStyle(Theme.sub)
                }
            }
            Text(distance(amount)).appFont(13, .semibold).foregroundStyle(ParallelUI.colors[k])
            // 홈 평가액과 다른 까닭: 경주는 마감 종가·출발 금액 기준, 홈은 지금 시세·국내 합계 포함
            if k == 0 && !m.isSample && abs(m.total - amount) >= 1e4 {
                Text("홈 평가액 \(AppModel.eok(m.total)) (지금 시세·국내 합계 포함). 경주는 마감 종가로, 넣고 뺀 돈은 빼고 셈해요.")
                    .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
            }
            if k == 1 { basketLine(w) }
            if k == 2 { charPart(place, race, w) }
        }
    }

    @ViewBuilder private func avatar(_ k: Int, _ w: PWorld) -> some View {
        if k == 2 { CharPixel(id: w.who, width: 34, height: 48) }
        else if k == 0 && m.me != nil { CharPixel(id: "me", width: 34, height: 48) }
        else {
            Image(systemName: k == 0 ? "person.fill" : "basket.fill").font(.system(size: 18, weight: .bold))
                .foregroundStyle(ParallelUI.colors[k]).frame(width: 34, height: 48)
        }
    }
    private func sub(_ k: Int, _ w: PWorld) -> String {
        switch k {
        case 0: return "내 실제 종목·수량. 수량을 고칠 때만 움직여요."
        case 1: return "모의 바구니. 내가 고칠 때만 움직여요."
        default: return Parallel.rules[w.who] ?? ""
        }
    }
    /// 마이너스족은 본전까지 남은 거리, 장투족은 목표까지 온 거리
    private func distance(_ amount: Double) -> String {
        if m.isGoal {
            let k = m.gK * 1e4
            return "목표 \(AppModel.wonK(m.gK))까지 \(AppModel.pct(min(1, amount / max(1, k)))) 왔어요"
        }
        let need = m.cost / max(1, amount) - 1
        return need > 0 ? "본전까지 +\(String(format: "%.1f", need * 100))% 남음" : "본전을 넘었어요"
    }

    private func basketLine(_ w: PWorld) -> some View {
        let seg = w.segs.last
        let parts: [String] = (seg?.w ?? [:]).sorted { $0.value > $1.value }.map { "\($0.key) \(AppModel.pct($0.value))" }
        return VStack(alignment: .leading, spacing: 6) {
            Text("지금 바구니: " + parts.joined(separator: " · ")).appFont(12).foregroundStyle(Theme.sub)
                .fixedSize(horizontal: false, vertical: true)
            if m.pworld != nil {
                NavigationLink(value: "parallel:basket") {
                    Text("바구니 고치기 ›").appFont(13, .bold).foregroundStyle(Theme.teal).frame(minHeight: 36)
                }.buttonStyle(.plain)
            }
        }
    }

    private func charPart(_ place: Int, _ race: PRace, _ w: PWorld) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(Parallel.line(w.who, place: place, day: Day.today)).appFont(14).lineSpacing(3)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.mintBg, in: UnevenRoundedRectangle(topLeadingRadius: 4, bottomLeadingRadius: 14, bottomTrailingRadius: 14, topTrailingRadius: 14))
                .fixedSize(horizontal: false, vertical: true)
            if Self.sameRoad(race) {
                Text("아직 규칙이 움직일 때가 오지 않아 진짜 나와 같은 길을 가고 있어요. 그래프에서는 점선이 진짜 나 위에 겹쳐 있어요.")
                    .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
            if w.who == "sua" && race.contrib > 0 {
                Text("매달 \(AppModel.wonK(m.gM))씩 넣은 \(AppModel.eok(race.contrib))까지 넣은 돈으로 쳐서 셌어요.").appFont(12).foregroundStyle(Theme.sub)
            }
            HStack(spacing: 12) {
                Button(ratio ? "비율 요약 접기" : "저쪽 세계 비율 보기 ›") { ratio.toggle() }
                    .appFont(13, .bold).foregroundStyle(Theme.teal).frame(minHeight: 36)
                Spacer()
                if m.pworld != nil { whoMenu(w) }
            }
            if ratio {
                Text("지난달 저쪽 세계: 현금 비중 \(AppModel.pct(race.cash.before)) → \(AppModel.pct(race.cash.now)) · 가장 큰 한 종목 비중 \(AppModel.pct(race.top.before)) → \(AppModel.pct(race.top.now))")
                    .appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func whoMenu(_ w: PWorld) -> some View {
        Menu {
            ForEach(Shelter.friends.filter { $0.id != w.who && $0.id != m.calm.type }, id: \.id) { f in
                Button(f.name + " · " + (Calm.kind(f.id)?.title ?? "")) { m.pickWho(f.id) }
            }
        } label: {
            Text("인물 바꾸기").appFont(12, .bold).foregroundStyle(Theme.sub)
                .padding(.horizontal, 10).frame(minHeight: 32).overlay(Capsule().stroke(Theme.border))
        }
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

    /// 인물 세계가 지금까지 진짜 나와 같은 길인지 (규칙이 한 번도 움직이지 않음)
    static func sameRoad(_ race: PRace) -> Bool {
        var d = 0.0
        for t in race.dates.indices { d = max(d, abs(race.r[0][t] - race.r[2][t])) }
        return d < 0.0005
    }

    /// 진짜 나는 굵게, 인물 세계는 점선 (두 줄이 겹쳐도 둘 다 보이게)
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
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "yy년 M월 d일"
        return f.string(from: x)
    }
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
                Text("고치면 오늘부터 새 기록이 시작되고, 지금까지 기록은 지난 바구니로 남아요. 출발 금액은 진짜 나와 같아요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                Text("견본에서 시작").appFont(15, .bold)
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
                    if w.isEmpty { Text("담은 종목이 없어요.").appFont(13).foregroundStyle(Theme.sub) }
                    Menu {
                        ForEach(Parallel.universe.filter { w[$0.id] == nil }, id: \.id) { u in
                            Button(u.id + " · " + u.name + (Parallel.has(u.id) ? "" : " (시세 준비 중)")) { w[u.id] = 10 }
                                .disabled(!Parallel.has(u.id))
                        }
                    } label: {
                        Text("+ 종목 담기").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minHeight: 40)
                    }
                    Text("지금은 사이트가 지난 종가를 모으는 종목과 견본 ETF만 담을 수 있어요.").appFont(12).foregroundStyle(Theme.muted)
                }
                HStack {
                    Text("합계").appFont(14, .bold); Spacer()
                    Text("\(sum)%").appFont(14, .bold).foregroundStyle(sum == 100 ? Theme.green : Theme.orange)
                }
                if sum != 100 && sum > 0 { Text("저장하면 합이 100%가 되게 비율대로 맞춰요.").appFont(12).foregroundStyle(Theme.sub) }
                Button {
                    m.saveBasket(w.mapValues { Double($0) })
                    dismiss()
                } label: {
                    Text("이 바구니로 오늘부터").appFont(15, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(sum > 0 ? Theme.teal : Theme.muted, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain).disabled(sum <= 0)
                Text("모의 결과이며 투자 권유가 아닙니다.").appFont(12).foregroundStyle(Theme.muted)
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

/// 쉼터의 문: 평행 세계로
struct ParallelDoor: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        NavigationLink(value: "parallel") {
            HStack(spacing: 12) {
                Image(systemName: "door.left.hand.open").font(.system(size: 26, weight: .semibold)).foregroundStyle(Theme.gold)
                    .frame(width: 44, height: 52)
                    .background(Color(hex: 0x2A3350), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 3) {
                    Text("다른 세계로 가는 문").appFont(15, .bold).foregroundStyle(Color(hex: 0xEEF0F7))
                    Text(m.parallelLeader.map { "오늘 앞선 나: \($0)" } ?? "세 명의 내가 실제 종가로 달려요. 어느 쪽이 이겨도 나.")
                        .appFont(12).foregroundStyle(Color(hex: 0xBFC6E0)).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Text("›").appFont(18, .bold).foregroundStyle(Color(hex: 0xBFC6E0))
            }
            .padding(14)
            .background(Theme.shelter, in: RoundedRectangle(cornerRadius: 16))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("다른 세계로 가는 문, 평행 세계의 나 보기")
    }
}

/// 홈 한 줄: 오늘 앞선 나 (평행 세계를 연 뒤에만)
struct ParallelHomeLine: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        if let lead = m.parallelLeader {
            Button { m.homePath.append("parallel") } label: {
                HStack(spacing: 8) {
                    Image(systemName: "door.left.hand.open").foregroundStyle(Theme.purple)
                    Text("오늘 앞선 나").appFont(13).foregroundStyle(Theme.sub)
                    Text(lead).appFont(13, .bold).lineLimit(1).minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                    Text("›").appFont(14, .bold).foregroundStyle(Theme.sub)
                }
                .padding(.horizontal, 14).frame(minHeight: 44)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("평행 세계, 오늘 앞선 나: \(lead)")
        }
    }
}
