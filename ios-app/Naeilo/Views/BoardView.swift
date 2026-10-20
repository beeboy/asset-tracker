import SwiftUI

// 미션 판 (회복 루트): 플러스 루트와 같은 모양. 인터미션 카드, 접힌 시작 미션, 초록 다음 미션 카드, 1000칸 (아래 블록·파도 게임 버튼)
struct BoardView: View {
    @Environment(AppModel.self) private var m
    @State private var openDone = false
    @State private var cellMode = "mine"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AppHeader(tab: "미션").padding(.horizontal, -16)
                SampleNote()
                // 플러스 루트처럼: 맨 위 인터미션 카드, 접힌 시작 미션 목록, 초록 다음 미션 카드
                if let inter = m.blocks.first(where: \.inter) { interCard(inter) }
                if !m.interDone && m.playOn { checkIn }
                let starts = m.blocks.filter { !$0.inter }
                startList(starts)
                if let next = m.blocks.first(where: { !m.done.contains($0.id) && m.available($0) }), !next.inter {
                    nextCard(next, steps: starts)
                }
                if m.playUnlocked { cells } else { lockedPlay }
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: MissionRoute.self) { MissionScreen(route: $0) }
    }

    private func route(_ b: AppModel.Block) -> MissionRoute? {
        switch b.id { case 1: .m1; case 2: .m2; case 3: .m3; case 5: .nx; default: nil }
    }

    /// 다음 미션: 진한 초록 블록 (플러스 루트와 같은 모양)
    private func nextCard(_ b: AppModel.Block, steps: [AppModel.Block]) -> some View {
        let idx = (steps.firstIndex { $0.id == b.id } ?? 0) + 1
        return VStack(alignment: .leading, spacing: 10) {
            Text("다음 미션 · \(idx)/\(steps.count)").appFont(13, .semibold).foregroundStyle(.white.opacity(0.8))
            Text(b.title).appFont(20, .bold)
            Text("열리는 것: \(b.reward)").appFont(14).foregroundStyle(.white.opacity(0.85))
            Button { if let r = route(b) { m.boardPath.append(r) } } label: {
                Text("시작").appFont(16, .bold).foregroundStyle(Theme.missionGreen)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14))
            }.buttonStyle(.plain).padding(.top, 4)
        }
        .foregroundStyle(.white)
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.missionGreen, in: RoundedRectangle(cornerRadius: 20))
    }

    /// 처음 4주 체크인 카드: 주마다 비중을 지켰는지와 만난 친구
    private func interCard(_ b: AppModel.Block) -> some View {
        let done = m.done.contains(b.id), avail = m.available(b), n = m.weeks.count
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("처음 4주 체크인").appFont(12, .bold).foregroundStyle(Theme.mint)
                Spacer()
                if done { Text("완료 ✓").appFont(12, .bold).foregroundStyle(Theme.yellow) }
                else if !avail { Image(systemName: "lock").appFont(12) }
            }
            Text(done || n >= 4 ? "4주 체크인 끝"
                 : avail ? "주 1회 비중 체크 · \(n)/4주"
                 : "시작 미션 뒤 열림")
                .appFont(16, .bold).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 6) {
                ForEach(0..<4, id: \.self) { i in
                    let pick: WeekPick? = avail || done ? (i < n ? m.weeks[i] : nil) : nil
                    let cur = avail && !done && i == n
                    VStack(spacing: 2) {
                        Text("\(i + 1)주째").appFont(11)
                        Text(pick?.label ?? (cur ? "이번 주" : "–")).appFont(13, .bold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .foregroundStyle(pick != nil ? .white : cur ? Theme.yellow : Theme.muted)
                    .background(pick?.color ?? .clear, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(pick?.color ?? (cur ? Theme.yellow : Theme.slate),
                                                                      style: StrokeStyle(lineWidth: 2, dash: pick == nil ? [5, 4] : [])))
                }
            }
        }
        .foregroundStyle(done || avail ? .white : Theme.sub2)
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(done || avail ? Theme.night : Theme.track, in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("처음 4주 체크인, \(n)/4주")
    }

    /// 시작 미션: 접힌 한 줄, 펼치면 번호 목록. 끝냈거나 열린 미션은 눌러서 들어간다
    private func startList(_ list: [AppModel.Block]) -> some View {
        let done = list.filter { m.done.contains($0.id) }.count, all = done == list.count
        return VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation { openDone.toggle() } } label: {
                HStack(spacing: 8) {
                    Text(all ? "✓ 시작 미션 \(done)/\(list.count)" : "시작 미션 \(done)/\(list.count)").fontWeight(.bold)
                    Spacer(minLength: 0)
                    Image(systemName: openDone ? "chevron.up" : "chevron.down")
                }
                .appFont(14, .semibold).foregroundStyle(all ? Theme.tealDark : Theme.ink)
                .padding(.horizontal, 14).frame(minHeight: 48)
            }.buttonStyle(.plain)
            if openDone {
                ForEach(list) { b in
                    let ok = m.done.contains(b.id), avail = m.available(b), r = route(b)
                    Divider().overlay(Theme.border)
                    Button { if let r, ok || avail { m.boardPath.append(r) } } label: {
                        HStack(spacing: 10) {
                            Text(b.num).appFont(13, .bold).foregroundStyle(Theme.sub).frame(width: 18)
                            Text(b.title).appFont(14).foregroundStyle(ok || avail ? Theme.ink : Theme.sub2)
                            Spacer(minLength: 0)
                            if ok { Image(systemName: "checkmark").foregroundStyle(Theme.teal) }
                            else if !avail { Image(systemName: "lock").foregroundStyle(Theme.sub2) }
                            else { Image(systemName: "chevron.right").foregroundStyle(Theme.sub) }
                        }
                        .appFont(13, .bold).padding(.horizontal, 14).frame(minHeight: 44)
                    }
                    .buttonStyle(.plain).disabled(r == nil || !(ok || avail))
                    .accessibilityLabel("시작 미션 \(b.num): \(b.title)" + (ok ? ", 끝냄" : ""))
                }
            }
        }
        .background(all ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }

    // 인터미션 체크인: 이번 주 비중을 지켰는지 고르면 블록이 채워지고 친구가 하나 온다
    private var checkIn: some View {
        @Bindable var m = m
        // 다음 주차가 열리기 전에는 방금 한 주차를 다시 고를 수 있다
        let edit = m.weekEditable, i = edit ? m.weeks.count - 1 : m.weeks.count, step = Shelter.weekSteps[min(i, 3)]
        return VStack(alignment: .leading, spacing: 10) {
            Text("\(i + 1)주차 체크인").appFont(13, .bold).foregroundStyle(Theme.teal)
            HStack(spacing: 8) {
                Text(step.whereText).appFont(11, .bold).foregroundStyle(Theme.inkFixed)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                Text(step.task).appFont(14, .semibold).foregroundStyle(Theme.ink)
            }
            Text("이번 주 \(m.focusName) 비중은?").appFont(14).foregroundStyle(Theme.ink)
            HStack(spacing: 6) {
                ForEach(WeekPick.allCases, id: \.self) { p in
                    let on = edit ? m.weeks[i] == p : m.weekCur == p
                    Button { if edit { m.editLastWeek(p) } else { m.weekCur = p } } label: {
                        VStack(spacing: 2) {
                            Text(p.label).appFont(15, .bold)
                            Text(p.sub).appFont(11).foregroundStyle(Theme.sub)
                        }
                        .foregroundStyle(Theme.ink)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background(on ? p.color.opacity(0.15) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.color, lineWidth: on ? 3 : 2))
                    }.buttonStyle(.plain)
                }
            }
            // 주차는 시작한 날부터 7일마다 열린다 (서버 시각). 열리기 전에는 남은 날을 보여 준다
            if edit { Text("다음 주차 열리기 전까지 수정 가능").appFont(12).foregroundStyle(Theme.muted) }
            if let wait = m.weekGateText {
                Label(wait, systemImage: "clock").appFont(13).foregroundStyle(Theme.sub)
                    .fixedSize(horizontal: false, vertical: true)
            } else if m.weekCur != nil {
                Button { withAnimation { m.confirmWeek() } } label: {
                    Text("체크인").appFont(14, .semibold)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(.white)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                }.buttonStyle(.plain)
            }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private var lockedPlay: some View {
        DashedCard {
            Label("100칸 블록", systemImage: "lock").appFont(15, .bold)
            Text("시작 3단계 뒤 열림 · 칸 채우기·블록·파도").appFont(13)
        }
    }

    // 1000칸: 본전 = 1000칸. 진한 칸 = 시작 뒤 바닥에도 있던 칸, 연한 칸 = 바닥 뒤 회복한 칸
    private var cells: some View {
        let n = m.cellsNow, nFloor = min(n, m.cellsFloor), nY = m.cellsYesterday, chg = n - nY
        let unit = AppModel.man(m.cellUnit)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("본전까지 1000칸").appFont(13, .bold).foregroundStyle(Theme.sub)
                Spacer()
                Text("1칸 = \(unit)").appFont(12).foregroundStyle(Theme.sub)
            }
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(n.formatted())칸").appFont(26, .bold).roll("\(n.formatted())칸")
                Text("/ 1,000칸").appFont(14).foregroundStyle(Theme.sub)
                Text(chg == 0 ? "어제와 같음" : "어제보다 \(chg > 0 ? "+" : "")\(chg)칸").appFont(14, .bold).foregroundStyle(chg == 0 ? Theme.sub : Theme.change(Double(chg)))
            }
            ChipRow(items: [("mine", "바닥 / 회복"), ("stock", "종목별"), ("chg", "오늘 바뀐 칸")], selection: $cellMode)
            CellGrid(filled: n, floor: nFloor, yesterday: nY, mode: cellMode, parts: stockParts)
                .aspectRatio(320.0 / 200.0, contentMode: .fit)
                .accessibilityLabel("1000칸 중 \(n)칸")
            FlowRow(spacing: 10) {
                if cellMode == "stock" {
                    ForEach(stockParts, id: \.0) { k, c, cnt in legend(c, "\(k) \(cnt)칸") }
                } else {
                    legend(Theme.teal, "바닥 \(nFloor)칸")
                    legend(Color(hex: 0x7FD3C9, dark: 0x3C8F86), "회복 \(n - nFloor)칸")
                    legend(Theme.yellow, "마지막 칸 = 본전")
                }
            }
            // 공유 카드와 게임 둘(블록·파도)을 한 줄에
            HStack(spacing: 8) {
                ShareCardButton(title: "공유 카드")
                BrickGameButton(filled: n, floor: nFloor, yesterday: nY, title: "블록", icon: "square.grid.3x3.fill")
                WaveGameButton()
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.ink, lineWidth: 2))
    }

    private var stockParts: [(String, Color, Int)] {
        var acc = 0.0
        return m.rows.sorted { $0.value > $1.value }.map { r in
            let a = m.cells(acc), b = m.cells(acc + r.value); acc += r.value
            return (r.id, r.color, b - a)
        }
    }

    private func legend(_ c: Color, _ t: String) -> some View {
        HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12); Text(t) }
            .appFont(12).foregroundStyle(Theme.sub)
    }

}

struct WeekForecastCard: View {
    @Environment(AppModel.self) private var m
    @State private var whyOpen = false

    static let names = ["lo": "더 낮게", "in": "예보대로", "hi": "더 높게"]

    /// 범위 숫자: 억 단위 소수 한 자리 (lo·hi가 같게 반올림되면 두 자리), 1억 아래는 만원 단위
    static func eok(_ v: Double, _ d: Int) -> String {
        v < 1e8 ? AppModel.man((v / 1e5).rounded() * 1e5) : String(format: "%.\(d)f억", v / 1e8)
    }

    // 이번 주 예보: 그 주 처음 연 날에 금요일 평가액 범위를 적어 두고, 금요일 종가로 판정한다.
    // 예보 · 맞혀보기 · 예보 성적 세 카드로 나눈다 (예보는 앱의 것, 맞혀보기는 사용자의 것).
    var body: some View {
        let r = m.weekRange
        let span = r.hi - r.lo, x0 = r.lo - span * 0.6, x1 = r.hi + span * 0.6
        let px = { (v: Double) in max(0, min(1, (v - x0) / (x1 - x0))) }
        let inside = r.actual >= r.lo && r.actual <= r.hi
        let pos = r.actual < r.lo ? "lo" : r.actual > r.hi ? "hi" : "in"
        let d = Self.eok(r.lo, 1) == Self.eok(r.hi, 1) ? 2 : 1
        let lo = Self.eok(r.lo, d), hi = Self.eok(r.hi, d)
        // 개발자 넘기기는 기록에 없으니 이번 주 결과를 붙여 본다
        let stamps = m.weekFridaySkip ? Array((m.pastWeeks + [inside ? 1 : 0]).suffix(8)) : m.pastWeeks
        return VStack(spacing: 12) {
            forecastCard(r: r, px: px, inside: inside, lo: lo, hi: hi)
            guessCard(pos: pos, inside: inside, lo: lo, hi: hi)
            recordCard(stamps)
        }
        .onAppear { Remind.shared.recordWeek(m) }
    }

    // 1. 예보
    private func forecastCard(r: (lo: Double, hi: Double, actual: Double), px: @escaping (Double) -> Double, inside: Bool, lo: String, hi: String) -> some View {
            Card {
                Label("\(m.weekFridayText) 금 마감 예보", systemImage: "sun.max.fill").appFont(13, .bold).foregroundStyle(Theme.forecast)
                Text("\(lo) ~ \(hi)").appFont(28, .bold).lineLimit(1).minimumScaleFactor(0.7).roll("\(lo) ~ \(hi)")
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.track).frame(height: 12)
                        Capsule().fill(Theme.blue.opacity(0.55)).frame(width: (px(r.hi) - px(r.lo)) * g.size.width, height: 12).offset(x: px(r.lo) * g.size.width)
                        Circle().fill(m.weekFriday ? (inside ? Theme.green : Theme.orange) : Theme.ink)
                            .overlay(Circle().stroke(Theme.card, lineWidth: 3))
                            .frame(width: 18, height: 18).offset(x: px(r.actual) * g.size.width - 9)
                    }
                    .frame(maxHeight: .infinity)
                }
                .frame(height: 26)
                HStack {
                    Text(lo); Spacer()
                    Text((m.weekFriday ? "금 " : "지금 ") + Self.eok(r.actual, 2)).fontWeight(.bold); Spacer()
                    Text(hi)
                }
                .appFont(12).foregroundStyle(Theme.sub)
                (Text("2주 중 1주").fontWeight(.bold) + Text("는 이 범위 안"))
                    .appFont(13).foregroundStyle(Theme.ink).lineSpacing(2)
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.forecastBg, in: RoundedRectangle(cornerRadius: 12))
            }
            .accessibilityElement(children: .combine)
    }

    // 2. 맞혀보기
    private func guessCard(pos: String, inside: Bool, lo: String, hi: String) -> some View {
            Card {
                HStack {
                    Text("맞혀보기").appFont(12, .bold).foregroundStyle(Theme.brown)
                    Spacer()
                    Text("결과는 토요일").appFont(12).foregroundStyle(Theme.sub)
                }
                Text("금요일엔?").appFont(17, .bold)
                HStack(spacing: 8) {
                    ForEach([("lo", "\(lo) ↓"), ("in", "범위 안"), ("hi", "\(hi) ↑")], id: \.0) { o in
                        let on = m.weekGuess == o.0
                        Button { m.weekGuess = o.0; Remind.shared.recordWeek(m) } label: {
                            VStack(spacing: 2) {
                                Text(Self.names[o.0] ?? "").appFont(15, .bold)
                                Text(o.1).appFont(11).opacity(0.85).lineLimit(1).minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity, minHeight: 64)
                            .foregroundStyle(on ? .white : Theme.ink)
                            .background(on ? Theme.forecast : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Theme.forecast : Theme.border, lineWidth: 2))
                        }
                        .buttonStyle(.plain)
                        .disabled(m.weekFriday)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
                if m.weekFriday {
                    let out = pos == "in" ? "범위 안" : "범위보다 \(pos == "hi" ? "높게" : "낮게")"
                    Text((m.weekGuess.map { $0 == pos ? "맞혔어요! " : "아쉬워요. 고른 답 ‘\(Self.names[$0] ?? "")’ · " } ?? "") + out)
                        .appFont(14, .semibold)
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background(inside ? Color(hex: 0xE3F4EC, dark: 0x163226) : Color(hex: 0xFDF0E1, dark: 0x3A2A17), in: RoundedRectangle(cornerRadius: 12))
                } else {
                    if let g = m.weekGuess { Text("‘\(Self.names[g] ?? "")’ 고름 · 금요일 마감 뒤 확인").appFont(13, .semibold).foregroundStyle(Theme.forecast) }
                    if m.devAll {
                        Button("금요일로 넘기기 (개발자)") { withAnimation { m.weekFridaySkip = true } }
                            .appFont(13).underline().foregroundStyle(Theme.sub).frame(minHeight: 40)
                    }
                }
            }
    }

    // 3. 예보 성적 + 배지
    private func recordCard(_ stamps: [Int]) -> some View {
            Card {
                HStack {
                    Text(stamps.isEmpty ? "예보 성적" : "지난 \(stamps.count)주").appFont(15, .bold)
                    Spacer()
                    if !stamps.isEmpty { Text("\(stamps.reduce(0, +))/\(stamps.count) 범위 안").appFont(13).foregroundStyle(Theme.sub) }
                }
                if stamps.isEmpty {
                    Text("첫 주 끝나면 쌓여요").appFont(13).foregroundStyle(Theme.sub)
                } else {
                    HStack(spacing: 6) {
                        ForEach(stamps.indices, id: \.self) { i in
                            VStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(stamps[i] == 1 ? Theme.forecast : Theme.card)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(stamps[i] == 1 ? Theme.forecast : Theme.dash, lineWidth: 2))
                                    .overlay { if stamps[i] == 1 { Image(systemName: "checkmark").appFont(13, .bold).foregroundStyle(.white) } }
                                    .frame(height: 32)
                                Text(i == stamps.count - 1 ? "지난주" : "\(stamps.count - i)주 전").appFont(10).foregroundStyle(Theme.muted)
                                    .lineLimit(1).minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(stamps.count)주 중 \(stamps.reduce(0, +))주 범위 안")
                    HStack(spacing: 14) {
                        legend(Theme.forecast, nil, "범위 안")
                        legend(Theme.card, Theme.dash, "범위 밖")
                    }
                }
                Divider()
                HStack {
                    Text("배지").appFont(14, .bold)
                    Spacer()
                    Text("\(Remind.badges.filter { m.guessHits >= $0.n }.count)개").appFont(13).foregroundStyle(Theme.sub)
                }
                HStack(spacing: 6) {
                    ForEach(Remind.badges, id: \.n) { b in
                        let got = m.guessHits >= b.n
                        VStack(spacing: 4) {
                            Image(systemName: b.icon).appFont(16, .bold)
                                .foregroundStyle(got ? Theme.inkFixed : Theme.muted)
                                .frame(width: 40, height: 40)
                                .background(got ? Theme.gold : Theme.track, in: Circle())
                            Text(b.n >= 20 ? "달인" : "\(b.n)회").appFont(10).foregroundStyle(got ? Theme.ink : Theme.muted).lineLimit(1).minimumScaleFactor(0.7)
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(b.name) 배지, \(got ? "받음" : "\(b.n)번 맞히면 받아요")")
                    }
                }
                Button { withAnimation { whyOpen.toggle() } } label: {
                    HStack {
                        Text("반만 맞아도 괜찮은 이유").appFont(13, .semibold)
                        Spacer()
                        Image(systemName: whyOpen ? "chevron.up" : "chevron.down").appFont(12)
                    }
                    .foregroundStyle(Theme.forecast).frame(minHeight: 40).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if whyOpen {
                    Text("범위는 2번 중 1번 맞게 잡아요.\n넓으면 쓸모없고, 좁으면 자주 틀려요.")
                        .appFont(13).foregroundStyle(Theme.ink).lineSpacing(2)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.forecastBg, in: RoundedRectangle(cornerRadius: 12))
                }
            }
    }

    private func legend(_ c: Color, _ stroke: Color?, _ t: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 4).fill(c)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(stroke ?? c, lineWidth: 2))
                .frame(width: 14, height: 14)
            Text(t).appFont(12).foregroundStyle(Theme.sub)
        }
    }
}

// 40×25 칸, 왼쪽 아래부터 채운다. 마지막 칸(본전)은 별.
struct CellGrid: View {
    let filled: Int
    let floor: Int
    let yesterday: Int
    let mode: String
    let parts: [(String, Color, Int)]

    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 40
            func rect(_ i: Int) -> CGRect {
                CGRect(x: CGFloat(i % 40) * s + 0.5, y: CGFloat(24 - i / 40) * s + 0.5, width: s - 1, height: s - 1)
            }
            var stockColor: [Color] = []
            if mode == "stock" { for (_, c, n) in parts { stockColor += Array(repeating: c, count: n) } }
            for i in 0..<1000 {
                let c: Color
                if i >= filled { c = Theme.track }
                else if mode == "stock" { c = i < stockColor.count ? stockColor[i] : Theme.muted }
                else if i < floor { c = mode == "mkt" ? Color(hex: 0xB9C2CA, dark: 0x4A5560) : Theme.teal }
                else { c = mode == "mine" ? Color(hex: 0x7FD3C9, dark: 0x3C8F86) : Theme.green }
                ctx.fill(Path(rect(i)), with: .color(c))
            }
            if mode == "chg" || mode == "mine" {
                let a = min(filled, yesterday), b = max(filled, yesterday)
                for i in a..<b { ctx.stroke(Path(rect(i)), with: .color(filled >= yesterday ? Theme.up : Theme.down), lineWidth: 1.2) }
            }
            ctx.fill(Path(ellipseIn: rect(999).insetBy(dx: 0.5, dy: 0.5)), with: .color(Theme.yellow))
        }
    }
}
