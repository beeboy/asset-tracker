import SwiftUI

// 미션 판 (회복 루트): 끝낸 미션은 한 줄로 접고, 인터미션 체크인, 1000칸, 이번 주 예보
struct BoardView: View {
    @Environment(AppModel.self) private var m
    @State private var openDone = false
    @State private var cellMode = "mine"

    var body: some View {
        let need = m.cost / max(1, m.total) - 1
        let doneBlocks = m.blocks.filter { m.done.contains($0.id) }
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AppHeader().padding(.horizontal, -16)
                VStack(alignment: .leading, spacing: 4) {
                    ScreenTitle(kicker: "회복 루트", title: "본전까지 가는 길")
                    (Text("지금 \(AppModel.sgn(m.ret)) · 본전까지 ") + Text("+" + String(format: "%.1f", need * 100) + "%").bold().foregroundColor(Theme.ink) + Text(" 올라야 해요"))
                        .appFont(15).foregroundStyle(Theme.sub)
                }
                if !doneBlocks.isEmpty {
                    Button { withAnimation { openDone.toggle() } } label: {
                        HStack(spacing: 8) {
                            Text("✓ \(doneBlocks.count)개 끝냄").fontWeight(.bold)
                            Text(doneBlocks.map(\.title).joined(separator: " · ")).lineLimit(1)
                            Spacer(minLength: 0)
                            Text(openDone ? "접기 ▴" : "펼치기 ▾")
                        }
                        .appFont(14, .semibold).foregroundStyle(Theme.tealDark)
                        .padding(.horizontal, 12).frame(minHeight: 48)
                        .background(Theme.tealBg, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain)
                }
                let shown = m.blocks.filter { openDone || !m.done.contains($0.id) }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                    ForEach(shown.filter { !$0.inter }) { b in tappable(b) }
                }
                ForEach(shown.filter { $0.inter }) { b in tappable(b) }
                if let next = m.blocks.first(where: { !m.done.contains($0.id) && m.available($0) }), !next.inter { nextCard(next) }
                if !m.interDone && m.playOn { checkIn }
                // 1000칸 다음에 이번 주 예보
                if m.playOn { cells; WeekForecastCard() } else { lockedPlay }
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

    @ViewBuilder private func tappable(_ b: AppModel.Block) -> some View {
        if let r = route(b), m.available(b) || m.done.contains(b.id) {
            Button { m.boardPath.append(r) } label: { block(b) }.buttonStyle(.plain)
        } else {
            block(b)
        }
    }

    private func nextCard(_ b: AppModel.Block) -> some View {
        Card(padding: 18) {
            Text("다음 미션 \(b.num)").appFont(13, .semibold).foregroundStyle(Theme.teal)
            Text(b.title).appFont(19, .bold)
            (Text("끝내면 열려요: ") + Text(b.reward).bold().foregroundColor(Theme.ink)).appFont(14).foregroundStyle(Theme.sub)
            PrimaryButton(title: "시작하기") { if let r = route(b) { m.boardPath.append(r) } }
        }
    }

    private func block(_ b: AppModel.Block) -> some View {
        let done = m.done.contains(b.id), avail = m.available(b)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(b.num).appFont(13, .bold)
                Spacer()
                if done { Image(systemName: "checkmark").appFont(13, .bold) }
                else if !avail { Image(systemName: "lock").appFont(12) }
            }
            Text(b.title).appFont(13, .semibold).lineLimit(2)
            if b.inter && !done && avail {
                HStack(spacing: 4) {
                    ForEach(0..<4, id: \.self) { i in
                        let pick = i < m.weeks.count ? m.weeks[i] : nil
                        Text("\(i + 1)주").appFont(11, .bold)
                            .frame(maxWidth: .infinity, minHeight: 20)
                            .foregroundStyle(pick != nil ? .white : i == m.weeks.count ? Theme.yellow : Theme.muted)
                            .background(pick?.color ?? (i == m.weeks.count ? .clear : Theme.slate), in: RoundedRectangle(cornerRadius: 6))
                            .overlay { if pick == nil && i == m.weeks.count { RoundedRectangle(cornerRadius: 6).stroke(Theme.yellow, lineWidth: 2) } }
                    }
                }
                Text("\(m.weeks.count)/4주 · 친구 \(m.weeks.count)명 · 이번 주: \(Shelter.weekSteps[min(m.weeks.count, 3)].task)")
                    .appFont(12).foregroundStyle(Color(hex: 0xC9D0D6)).lineSpacing(2)
            }
        }
        .padding(b.inter ? 12 : 10)
        .frame(maxWidth: .infinity, minHeight: b.inter ? 72 : 88, alignment: .topLeading)
        .foregroundStyle(b.inter ? (done ? Theme.yellow : avail ? .white : Theme.sub2) : (done ? .white : avail ? Theme.ink : Theme.sub2))
        .background(b.inter ? (done || avail ? Theme.ink : Theme.track) : (done ? Theme.teal : avail ? .white : Theme.track), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.inter ? (done ? Theme.ink : avail ? Theme.yellow : Theme.dash) : (done || avail ? Theme.teal : Theme.track),
                                                           style: StrokeStyle(lineWidth: 2, dash: b.inter && !done ? [6, 4] : [])))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("미션 \(b.num): \(b.title)")
    }

    // 인터미션 체크인: 이번 주 비중을 지켰는지 고르면 블록이 채워지고 친구가 하나 온다
    private var checkIn: some View {
        @Bindable var m = m
        let i = m.weeks.count, step = Shelter.weekSteps[min(i, 3)]
        let f = Shelter.friends[min(i + 1, 4)]
        return VStack(alignment: .leading, spacing: 10) {
            Text("인터미션 \(i + 1)주차 · 매주 1분").appFont(13, .bold).foregroundStyle(Theme.mint)
            HStack(spacing: 8) {
                Text(step.whereText).appFont(11, .bold).foregroundStyle(Theme.ink)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                Text(step.task).appFont(14, .semibold)
            }
            Text("이번 주 DRNK 비중은 어땠나요?").appFont(14)
            HStack(spacing: 6) {
                ForEach(WeekPick.allCases, id: \.self) { p in
                    let on = m.weekCur == p
                    Button { m.weekCur = p } label: {
                        VStack(spacing: 2) {
                            Text(p.label).appFont(15, .bold)
                            Text(p.sub).appFont(11).foregroundStyle(Theme.sub)
                        }
                        .foregroundStyle(Theme.ink)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background(on ? p.color.opacity(0.15) : .white, in: RoundedRectangle(cornerRadius: 12))
                        .background(.white, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.color, lineWidth: on ? 3 : 2))
                    }.buttonStyle(.plain)
                }
            }
            if m.weekCur != nil {
                Button { withAnimation { m.confirmWeek() } } label: {
                    HStack(spacing: 8) {
                        Pixel(name: "spr_" + f.id, width: 20, height: 28)
                        Text("체크인하고 \(f.name) 만나기 (시안: 다음 주로)").appFont(14, .semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .foregroundStyle(Theme.ink)
                    .background(Color(hex: 0xF7F8FA), in: RoundedRectangle(cornerRadius: 10))
                }.buttonStyle(.plain)
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(Theme.ink, in: RoundedRectangle(cornerRadius: 18))
    }

    private var lockedPlay: some View {
        DashedCard {
            Label("1000칸 · 100칸 선물 · 이번 주 예보", systemImage: "lock").appFont(15, .bold)
            Text("앱 시작 3단계를 마치면 열려요. 인터미션 동안에도 칸을 채우고 매주 예보를 맞혀 볼 수 있어요.").appFont(13)
        }
    }

    // 1000칸: 본전 = 1000칸. 진한 칸 = 시작 뒤 바닥에도 있던 칸, 연한 칸 = 바닥 뒤 회복한 칸
    private var cells: some View {
        let n = m.cellsNow, nFloor = min(n, m.cellsFloor), nY = m.cellsYesterday, chg = n - nY
        let passed = n / 100
        let unit = AppModel.man(m.cellUnit).replacingOccurrences(of: "만원", with: "")
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("본전까지 1000칸").appFont(13, .bold).foregroundStyle(Theme.sub)
                Spacer()
                Text("1칸 = \(unit)만원").appFont(12).foregroundStyle(Theme.sub)
            }
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(n.formatted())칸").appFont(26, .bold)
                Text("/ 1,000칸").appFont(14).foregroundStyle(Theme.sub)
                Text(chg == 0 ? "어제와 같음" : "어제보다 \(chg > 0 ? "+" : "")\(chg)칸").appFont(14, .bold).foregroundStyle(chg == 0 ? Theme.sub : Theme.change(Double(chg)))
            }
            ChipRow(items: [("mine", "바닥 / 회복"), ("stock", "종목별"), ("chg", "어제 바뀐 칸")], selection: $cellMode, accent: Theme.ink)
            CellGrid(filled: n, floor: nFloor, yesterday: nY, mode: cellMode, parts: stockParts)
                .aspectRatio(320.0 / 200.0, contentMode: .fit)
                .accessibilityLabel("1000칸 중 \(n)칸")
            FlowRow(spacing: 10) {
                if cellMode == "stock" {
                    ForEach(stockParts, id: \.0) { k, c, cnt in legend(c, "\(k) \(cnt)칸") }
                } else {
                    legend(Theme.teal, "바닥에도 있던 칸 \(nFloor)칸")
                    legend(Color(hex: 0x7FD3C9), "바닥 뒤 회복한 칸 \(n - nFloor)칸")
                    legend(Theme.yellow, "마지막 칸 = 본전")
                }
            }
            Text(cellMode == "stock" ? "큰 종목부터 차례로 칸을 차지해요. 칸 수는 비중과 같아요."
                 : "진한 칸은 회복 루트를 시작한 뒤 가장 낮았던 날에도 있던 칸이에요. 연한 칸은 그 뒤 회복한 칸이에요. 빨간 테두리는 어제 바뀐 칸이에요.")
                .appFont(12).foregroundStyle(Theme.sub).lineSpacing(2)
            HStack {
                Text("100칸 선물").appFont(13, .bold)
                Spacer()
                Text(passed >= 10 ? "1000칸 완성!" : "\((passed + 1) * 100)칸까지 \((passed + 1) * 100 - n)칸").appFont(13).foregroundStyle(Theme.sub)
            }
            HStack(spacing: 4) {
                ForEach(1...10, id: \.self) { i in
                    let on = n >= i * 100
                    Text("\(i * 100)").appFont(10, .bold)
                        .frame(maxWidth: .infinity, minHeight: 30)
                        .foregroundStyle(on ? Theme.ink : i - 1 == passed ? Theme.sub : Theme.muted)
                        .background(on ? Theme.yellow : i - 1 == passed ? .clear : Theme.line, in: RoundedRectangle(cornerRadius: 8))
                        .overlay { if !on && i - 1 == passed { RoundedRectangle(cornerRadius: 8).stroke(Theme.yellow, style: StrokeStyle(lineWidth: 2, dash: [4, 3])) } }
                }
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(.white, in: RoundedRectangle(cornerRadius: 18))
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

    // 이번 주 예보: 월요일에 금요일 평가액 50% 범위를 적어 두고, 금요일 종가로 도장
    var body: some View {
        @Bindable var m = m
        let r = m.weekRange
        let span = r.hi - r.lo, x0 = r.lo - span * 0.6, x1 = r.hi + span * 0.6
        let px = { (v: Double) in max(0, min(1, (v - x0) / (x1 - x0))) }
        let inside = r.actual >= r.lo && r.actual <= r.hi
        let pos = r.actual < r.lo ? "lo" : r.actual > r.hi ? "hi" : "in"
        let stamps = m.weekFriday ? Array(m.pastWeeks.dropFirst()) + [inside ? 1 : 0] : m.pastWeeks
        // 파란 테두리와 큰 범위 숫자로 눈에 띄게 둔다
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                Label("이번 주 예보", systemImage: "scope").appFont(14, .bold).foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 4).background(Theme.blue, in: Capsule())
                Spacer()
                Text(m.weekFriday ? "10월 10일 금요일 마감" : "월요일에 적음 · 금요일 마감").appFont(12).foregroundStyle(Theme.sub)
            }
            Text("금요일 종가 평가액, 절반의 경우 이 안").appFont(13).foregroundStyle(Theme.sub)
            Text("\(AppModel.man(r.lo)) ~ \(AppModel.man(r.hi))").appFont(24, .bold).foregroundStyle(Color(hex: 0x1B5E96))
                .lineLimit(1).minimumScaleFactor(0.7)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.line).frame(height: 14)
                    Capsule().fill(Theme.blue.opacity(0.33)).frame(width: (px(r.hi) - px(r.lo)) * g.size.width, height: 14).offset(x: px(r.lo) * g.size.width)
                    Circle().fill(m.weekFriday ? (inside ? Theme.green : Theme.orange) : Theme.ink)
                        .overlay(Circle().stroke(.white, lineWidth: 3))
                        .frame(width: 20, height: 20).offset(x: px(r.actual) * g.size.width - 10)
                }
                .frame(maxHeight: .infinity)
            }
            .frame(height: 30)
            HStack {
                Text(AppModel.man(r.lo)); Spacer()
                Text((m.weekFriday ? "금요일 " : "지금 ") + AppModel.man(r.actual)).fontWeight(.bold); Spacer()
                Text(AppModel.man(r.hi))
            }
            .appFont(12).foregroundStyle(Theme.sub)
            if !m.weekFriday {
                Text("금요일엔 어디쯤일까요? (맞혀도 같은 도장이에요)").appFont(15, .bold)
                ChipRow(items: [("lo", "범위 아래"), ("in", "범위 안"), ("hi", "범위 위")],
                        selection: Binding(get: { m.weekGuess ?? "" }, set: { m.weekGuess = $0 }), accent: Theme.blue, fill: true)
                Button("금요일로 넘기기 (시안)") { withAnimation { m.weekFriday = true } }
                    .appFont(13).underline().foregroundStyle(Theme.sub).frame(minHeight: 40)
            } else {
                let names = ["lo": "범위 아래", "in": "범위 안", "hi": "범위 위"]
                Text((inside ? "범위 안에 들어왔어요. 도장 꽝!" : "범위 \(pos == "lo" ? "아래" : "위")로 나갔어요. 이런 주도 절반쯤 있어요.")
                     + (m.weekGuess.map { $0 == pos ? " 맞혔어요." : " 고른 답은 \"\(names[$0] ?? "")\"였어요." } ?? ""))
                    .appFont(14, .semibold)
                    .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                    .background(inside ? Color(hex: 0xE3F4EC) : Color(hex: 0xFDF0E1), in: RoundedRectangle(cornerRadius: 12))
            }
            HStack {
                Text("지난 8주").appFont(13, .bold)
                Spacer()
                Text("범위 안 \(stamps.reduce(0, +)) / 8주").appFont(13).foregroundStyle(Theme.sub)
            }
            HStack(spacing: 4) {
                ForEach(stamps.indices, id: \.self) { i in
                    Text(stamps[i] == 1 ? "안" : "밖").appFont(11, .bold)
                        .frame(maxWidth: .infinity, minHeight: 30)
                        .foregroundStyle(stamps[i] == 1 ? .white : Color(hex: 0x8A4B12))
                        .background(stamps[i] == 1 ? Theme.green : Color(hex: 0xFBE3CF), in: RoundedRectangle(cornerRadius: 8))
                }
            }
            Text("50% 범위라서 예보가 정직하면 절반쯤 들어와요. 너무 자주 들어오면 범위가 넓은 거고, 너무 드물면 좁은 거예요. 점수가 아니라 예보를 믿어도 되는지 보는 기록이에요.")
                .appFont(12).foregroundStyle(Theme.sub).lineSpacing(2)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0xF3F8FD), in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.blue, lineWidth: 2))
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
                else if i < floor { c = mode == "mkt" ? Color(hex: 0xB9C2CA) : Theme.teal }
                else { c = mode == "mine" ? Color(hex: 0x7FD3C9) : Theme.green }
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
