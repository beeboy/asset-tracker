import SwiftUI
import CoreMotion

// 목표 달성 루트 화면 (시안 g1·g1r·g3·g3r·gt·gi·gp1r, 목표 미션 판). 설명은 계산값, 종목 추천 문구 없음.

private let mixColor: [String: Color] = ["mine": Theme.purple, "index": Color(hex: 0x1F3A7A, dark: 0x7F9BE0), "grow": Theme.green, "steady": Theme.blue]
private let compColor: [String: Color] = ["T": Theme.purple, "I": Color(hex: 0x1F3A7A, dark: 0x7F9BE0), "G": Theme.green, "B": Theme.blue, "C": Color(hex: 0xBFC6CD, dark: 0x56616C)]
private let compName: [String: String] = ["T": String(localized: "비중 1위 종목"), "I": String(localized: "지수"), "G": String(localized: "성장 지속"), "B": String(localized: "버팀목"), "C": String(localized: "현금")]

// 목표까지 길: 띠 = 100번 중 90번, 선 = 보통의 경우, 회색 점선 = 넣은 원금, 빨간 점선 = 목표
struct GoalPathChart: View {
    let sims: [(GoalSim, Color, Bool)]     // (결과, 색, 고른 것)
    let goal: Double
    var showBand = true
    var showPrincipal = true
    var body: some View {
        Canvas { ctx, size in
            guard let main = sims.first(where: { $0.2 })?.0 ?? sims.first?.0 else { return }
            let n = main.med.count - 1, ymax = max(goal * 1.3, (sims.map { $0.0.med.last ?? 0 }.max() ?? 0) * 1.25)
            let X = { (i: Int) in CGFloat(i) / CGFloat(max(1, n)) * size.width }
            let Y = { (v: Double) in size.height - CGFloat(min(v, ymax) / ymax) * size.height }
            func line(_ a: [Double]) -> Path { Path { p in for (i, v) in a.enumerated() { let pt = CGPoint(x: X(i), y: Y(v)); i == 0 ? p.move(to: pt) : p.addLine(to: pt) } } }
            if showBand {
                var band = line(main.hi)
                for i in stride(from: n, through: 0, by: -1) { band.addLine(to: CGPoint(x: X(i), y: Y(main.lo[i]))) }
                band.closeSubpath()
                ctx.fill(band, with: .color(Theme.teal.opacity(0.15)))
            }
            ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: Y(goal))); $0.addLine(to: CGPoint(x: size.width, y: Y(goal))) },
                       with: .color(Color(hex: 0xC8352E)), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            if showPrincipal { ctx.stroke(line(main.principal), with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3])) }
            for (s, c, on) in sims { ctx.stroke(line(s.med), with: .color(c.opacity(on ? 1 : 0.55)), lineWidth: on ? 3 : 1.5) }
        }
        .accessibilityLabel("목표까지 평가액이 지날 길")
    }
}

private func legend(_ c: Color, _ t: String) -> some View {
    HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12); Text(t) }
        .appFont(12).foregroundStyle(Theme.sub)
}

func inputField(_ label: String, _ text: Binding<String>, commas: Bool = false) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(label).appFont(13, .semibold).foregroundStyle(Theme.sub)
        TextField("", text: text).keyboardType(.numberPad).appFont(18, .semibold)
            .padding(.horizontal, 12).frame(minHeight: 48)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
            .accessibilityLabel(label)
            .onChange(of: text.wrappedValue) { _, v in
                if commas, commaDigits(v) != v { text.wrappedValue = commaDigits(v) }
            }
    }
}

/// 숫자만 남겨 세 자리마다 쉼표 (19530000 → 19,530,000). 원 단위 금액 칸에 쓴다
func commaDigits(_ v: String) -> String {
    let d = v.filter(\.isNumber)
    guard let n = Int(d) else { return d }
    return n.formatted(.number.locale(Locale(identifier: "en_US")))
}

/// 쉼표 넣은 금액 글을 숫자로
func commaValue(_ v: String) -> Double { Double(v.filter(\.isNumber)) ?? 0 }

/// 금액 끝의 "원"을 뗀다 (826만원 → 826만, 5.1억원 → 5.1억)
private func noWon(_ s: String) -> String { s.hasSuffix("원") ? String(s.dropLast()) : s }

private extension AppModel {
    /// 목표일 짧게 (2029.10.5)
    var goalToShort: String { let f = DateFormatter(); f.dateFormat = "yyyy.M.d"; return f.string(from: goalToDate) }
}

// 목표까지 100칸 (1칸 = 목표의 1/100): 진한 칸 = 내가 넣은 돈, 민트 = 투자로 불어난 돈, 주황 = 잠시 줄어든 돈
// 회복 루트도 같은 카드: 본전까지 100칸, 진한 칸 = 시작 뒤 바닥에도 있던 칸, 민트 = 바닥 뒤 회복한 칸
// 공유 카드·벽돌깨기는 1000칸 그대로 (값만 넘긴다)
struct GoalCellsCard: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let goal = m.isGoal
        let K = goal ? m.gK * 1e4 : m.cost, unit = K / 100
        let tkV = goal ? m.trackValue : m.total
        let tkP = !goal ? tkV : m.bare ? tkV / 1.02 : tkV * max(0.8, min(1.25, m.cost / max(1, m.total)))
        let fl = goal ? 0 : m.cellsFloor   // 회복: 바닥 칸 (1000칸 기준)
        let fV = min(100, tkV / max(1, unit)), fP = min(100, tkP / max(1, unit))
        let mine = goal ? min(fV, fP) : min(fV, Double(fl) / 10), lost = max(0, fP - fV), gain = max(0, tkV - tkP)
        // 공유 카드·벽돌깨기용 1000칸
        let cl = { (v: Double) in max(0, min(1000, Int(floor(v / max(1, K / 1000))))) }
        let nV = cl(tkV), nP = cl(tkP), nMine = goal ? min(nV, nP) : min(nV, fl), nY = cl(tkV / (1 + m.todayMove))
        let pct = Int(tkV / max(1, K) * 100)
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline) {
                    Text(goal ? String(localized: "\(String(describing: AppModel.wonK(m.gK)))까지 100칸") : String(localized: "본전까지 100칸")).appFont(18, .bold)
                    Spacer()
                    Text(fV >= 100 ? String(localized: "100칸 완성!") : String(localized: "1칸 = \(String(describing: AppModel.man(unit)))")).appFont(14).foregroundStyle(Theme.sub)
                }
                // 박스는 60% 폭, 정보는 오른쪽
                SplitRow(ratio: 0.6, spacing: 14, rightFill: 0.9) {
                    // 100칸이 곧 벽돌. 박스를 누르면 공 발사, 패들은 기울여서만
                    BrickBox(mine: mine, filled: fV, lost: lost, g: game, on: $playing) { clear in
                        if clear { party = true } else { showGame = true }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { if !playing && !party { startMini(fV, mine) } }   // 박스·패들을 눌러도 시작
                    .accessibilityLabel(goal ? String(localized: "100칸 중 \(Int(fV))칸, 목표의 \(pct)%") : String(localized: "100칸 중 \(Int(fV))칸, 본전의 \(pct)%"))
                    VStack(alignment: .leading, spacing: 10) {
                        if goal {
                            legend(Theme.teal, nil, String(localized: "넣은 돈"), AppModel.man(min(tkV, tkP)))
                            if lost > 0 { legend(Self.lostC, nil, String(localized: "줄어든 돈"), "−" + AppModel.man(tkP - tkV)) }
                            else { legend(Self.mintC, nil, String(localized: "불어난 돈"), "+" + AppModel.man(gain)) }
                            legend(Theme.card, Theme.gold, String(localized: "마지막 칸 = 목표"), nil)
                        } else {
                            let floorV = Double(min(nV, fl)) * m.cellUnit
                            legend(Theme.teal, nil, String(localized: "바닥"), AppModel.man(floorV))
                            legend(Self.mintC, nil, String(localized: "회복"), "+" + AppModel.man(max(0, tkV - floorV)))
                            legend(Theme.card, Theme.gold, String(localized: "마지막 칸 = 본전"), nil)
                        }
                        Spacer(minLength: 0)   // 공유하기는 박스 아래 끝 줄에 맞춘다
                        action(String(localized: "공유하기"), "square.and.arrow.up", fill: false) { showShare = true }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                }
            }
            .padding(18)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 20))
            gameBox
            // 캐릭터·말풍선은 화면 맨 아래(탭 위)
            Spacer(minLength: 0)
            // 캐릭터가 자랑하자고 권한다 (누르는 곳 아님, 공유는 오른쪽 버튼). 다 깨면 친구들이 이 자리를 채운다
            if party { MiniParty().frame(maxWidth: .infinity) } else {
            HStack(alignment: .bottom, spacing: 10) {
                    CharPixel(id: m.homeFriendShown, width: 42, height: 60).charIdle().accessibilityHidden(true)
                        .onTapGesture { say = Self.talk(m, pct: pct, n: Int(fV), unit: unit, fV: fV, lost: lost, dY: nV - nY) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(m.friendName(m.homeFriendShown)).appFont(12, .semibold).foregroundStyle(Theme.blue)
                        Text(say ?? Self.boast(pct, Int(fV))).appFont(14).fixedSize(horizontal: false, vertical: true)
                            .onAppear { say = Self.talk(m, pct: pct, n: Int(fV), unit: unit, fV: fV, lost: lost, dY: nV - nY) }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.mintBg, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                }
            }
        }
        .onChange(of: scenePhase) { if scenePhase == .background { party = false } }   // 다른 앱으로 가면 친구들 퇴장
        .sheet(isPresented: $showShare) { ShareCardSheet(party: party) }
        // 블록·파도 첫 화면도 다른 팝업처럼 시트 (게임 판은 그 안에서 전체 화면)
        .sheet(isPresented: $showGame) { BrickGameHub(filled: nV, floor: nMine, yesterday: nY) }
        .sheet(isPresented: $showWave) { WaveHub() }
    }
    /// 내 자산 게임 (2026-10-25 시안 A안): 100칸 아래, 벽돌깨기·파도타기 두 칸 나란히
    private var gameBox: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("내 자산 게임").appFont(18, .bold)
            HStack(spacing: 10) {
                gameTile(.brick, String(localized: "채운 칸을 부시자"), String(localized: "벽돌깨기")) { showGame = true }
                gameTile(.wave, String(localized: "내 흐름을 즐기자"), String(localized: "파도타기")) { showWave = true }
            }
        }
        // 바탕 카드는 뺀다. 100칸 카드와 같은 색이 이어져 어색하다 (2026-10-27)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    private func gameTile(_ k: GameThumb.Kind, _ say: String, _ name: String, _ go: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            GameThumb(kind: k).frame(height: 120)
            HStack(spacing: 3) {
                Text(say).appFont(12).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                Button(action: go) {
                    Text(name).appFont(12, .bold).lineLimit(1).fixedSize()
                        .foregroundStyle(.white).padding(.horizontal, 5).padding(.vertical, 6)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 9))
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, 6).padding(.vertical, 8)
        }
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
        .contentShape(Rectangle()).onTapGesture(perform: go)
    }
    @State private var showGame = false
    @State private var showWave = false
    @State private var showShare = false
    @State private var game = MiniBreakout()
    @State private var playing = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var party = false   // 안 죽고 다 깨면 친구들이 올라와 축하 (숨은 재미)
    @State private var say: String? = nil   // 캐릭터 한마디 (열 때·캐릭터를 누를 때마다 새로)

    private func startMini(_ fV: Double, _ mine: Double) {
        let n = Int(ceil(fV))
        if n == 0 { showGame = true; return }
        game.setup(n: n, mine: mine)
        playing = true
    }

    /// 진행만큼 자랑하자는 한마디
    /// 미션 탭 한마디: 자랑·칸·어제와 견주기·게임·다음 미션을 돌려 가며 (2026-10-29)
    @MainActor static func talk(_ m: AppModel, pct: Int, n: Int, unit: Double, fV: Double, lost: Double, dY: Int) -> String {
        let brag = boast(pct, n)
        let en = AppModel.en, left = AppModel.man(max(0, (Double(n) + 1 - fV) * unit)), dc = String(format: "%.1f", Double(abs(dY)) / 10)
        var cells = en ? ["\(n) of 100 cells filled.", "One cell is \(AppModel.man(unit)). One at a time."]
                       : ["100칸 중 \(n)칸 찼어요.", "1칸은 \(AppModel.man(unit))이에요. 한 칸씩이면 돼요."]
        if fV < 100 { cells.append(en ? "\(left) to the next cell." : "다음 칸까지 \(left) 남았어요.") }
        if lost > 0 { cells.append(en ? "The red cells are what dropped. You can fill them again." : "빨간 칸은 줄어든 만큼이에요. 다시 채우면 돼요.") }
        let day = dY > 0 ? [en ? "\(dc) cells more than yesterday." : "어제보다 \(dc)칸 늘었어요."]
            : dY < 0 ? [en ? "\(dc) cells fewer than yesterday. That's okay." : "어제보다 \(dc)칸 줄었어요. 괜찮아요."]
            : [en ? "Same cells as yesterday. Holding steady counts too." : "어제랑 칸 수가 같아요. 그것도 버틴 거예요."]
        let rec = GameRecord.load(), best = rec.best.values.max() ?? 0
        var play = en ? ["Want to smash your filled cells as bricks?", "Ride a wave with today's flow?"] : ["채운 칸을 벽돌로 부숴 볼래요?", "오늘 흐름으로 파도 한 번 타 볼까요?"]
        if best > 0 { play.append(en ? "Your best Brick Breaker score is \(best). Beat it?" : "벽돌깨기 최고 점수는 \(best)점이에요. 넘어 볼까요?") }
        let next = m.boardSteps.first { !$0.ok && $0.avail }.map { en ? ["Next mission: ‘\($0.title)’. Let's do it.", "‘\($0.title)’ is open. Start it up top."] : ["다음 미션은 ‘\($0.title)’, 같이 해요.", "‘\($0.title)’ 미션이 열려 있어요. 위에서 시작할 수 있어요."] } ?? []
        let brags = en ? [brag, "Who should see these cells?", "Share it and show off."] : [brag, "이 칸, 누구한테 보여 줄까요?", "공유하기로 자랑해도 돼요."]
        return SayDeck.next("board", [("brag", brags), ("cells", cells), ("day", day), ("play", play), ("next", next)]) ?? brag
    }

    static func boast(_ pct: Int, _ n: Int) -> String {
        switch pct {
        case 100...: return String(localized: "100칸 완성! 이건 무조건 자랑이야.")
        case 75...: return String(localized: "거의 다 왔어. 지금 자랑할 때야!")
        case 50...: return String(localized: "반 넘었어! 보여 주면 다들 놀랄걸.")
        case 25...: return String(localized: "\(String(describing: pct))%나 왔어. 이건 자랑해야지.")
        default: return n > 0 ? String(localized: "벌써 \(String(describing: n))칸 채웠어. 시작한 것도 자랑이야!") : String(localized: "첫 칸 채우면 바로 자랑하자!")
        }
    }

    private func action(_ t: String, _ icon: String, fill: Bool, _ go: @escaping () -> Void) -> some View {
        Button(action: go) {
            Label(t, systemImage: icon).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, minHeight: 40)
                .foregroundStyle(fill ? .white : Theme.teal)
                .background(fill ? Theme.teal : .clear, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.teal, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }

    static let mintC = Color(hex: 0x8FCFC3, dark: 0xA8E6DC)   // 다크: 넣은 돈(진한 청록)과 구분되게 밝게
    static let lostC = Color(hex: 0xFBE3CF, dark: 0x4A3020)

    // 좁은 오른쪽 칸: 색 + 이름, 값은 아래 줄
    private func legend(_ c: Color, _ stroke: Color?, _ t: String, _ v: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 3).fill(c)
                    .overlay(RoundedRectangle(cornerRadius: 3).stroke(stroke ?? c, lineWidth: 1.5))
                    .frame(width: 12, height: 12)
                Text(t).appFont(13).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
            }
            if let v { Text(v).appFont(15, .bold).lineLimit(1).minimumScaleFactor(0.7).padding(.leading, 18) }
        }
    }
}

/// 100칸 박스가 곧 미니 벽돌깨기. 패들은 카드 아래 빈 곳에서 조작. 끝나면 end(다 깼나)
/// 게임 중에만 화면을 매 프레임 그린다
struct BrickBox: View {
    let mine: Double, filled: Double, lost: Double
    let g: MiniBreakout
    @Binding var on: Bool
    let end: (Bool) -> Void
    var body: some View {
        ZStack(alignment: .top) {
            if !on { GoalCellGrid(mine: mine, filled: filled, lost: lost).aspectRatio(1, contentMode: .fit) }
            TimelineView(.animation(paused: !on)) { tl in
                Canvas { ctx, size in
                    if on {
                        let r = g.step(tl.date, size)
                        if r != 0 { DispatchQueue.main.async { on = false; end(r == 1) } }
                    }
                    g.draw(&ctx, size, cells: on)
                }
            }
        }
        .aspectRatio(0.9, contentMode: .fit)
        .pausesRefresh(on)
    }
}

/// 다 깨면 캐릭터와 친구들이 아래에서 차례로 튀어 올라 축하
struct MiniParty: View {
    @State private var up = false
    var body: some View {
        let ids = Shelter.friends.map(\.id) + ["me"]   // 친구 다섯 + 나, 전부 여섯
        // 원래 캐릭터 줄 높이(60)를 넘지 않게: 축하 글은 위 빈 칸에 띄운다 (글자 키워도 탭과 안 겹치게)
        HStack(alignment: .bottom, spacing: 4) {
            ForEach(Array(ids.enumerated()), id: \.offset) { i, id in
                CharPixel(id: id, width: 42, height: 60).charIdle(i)
                    .offset(y: up ? 0 : 160)
                    .animation(.spring(response: 0.45, dampingFraction: 0.55).delay(Double(i) * 0.08), value: up)
            }
        }
        .frame(height: 60)
        .overlay(alignment: .top) {
            Text("다 깼다! 대단해!").appFont(14, .bold).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Theme.teal, in: Capsule())
                .fixedSize(horizontal: false, vertical: true)
                .alignmentGuide(.top) { $0[.bottom] + 6 }
                .opacity(up ? 1 : 0)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { withAnimation(.easeOut(duration: 0.3)) { up = true } }
    }
}

final class MiniBreakout {
    var alive = [Bool](repeating: false, count: 100)
    var bomb = [Bool](repeating: false, count: 100)   // 맞으면 둘레 8칸까지 함께 터진다 (찬 칸의 약 12%)
    var mineN = 0, left = 0
    var bx: CGFloat = 0, by: CGFloat = 0, vx: CGFloat = 0, vy: CGFloat = 0
    var px: CGFloat = 0.5         // 패들 가운데 (폭 비율)
    var held = true
    var last: Date?
    private let motion = CMMotionManager()   // 기울여서 패들 (본 게임과 같은 감도)

    func setup(n: Int, mine: Double) {
        for i in 0..<100 { alive[i] = i < n; bomb[i] = i < n && Double.random(in: 0..<1) < 0.12 }
        left = n; mineN = Int(mine); held = true; last = nil
        if motion.isDeviceMotionAvailable { motion.deviceMotionUpdateInterval = 1.0 / 60; motion.startDeviceMotionUpdates() }
    }
    deinit { motion.stopDeviceMotionUpdates() }

    /// 한 프레임 진행. 0 계속, 1 다 깸, 2 놓침
    func step(_ now: Date, _ size: CGSize) -> Int {
        let w = size.width, gap: CGFloat = 4, c = (w - gap * 9) / 10 + gap
        let py = size.height - 6, pw = w * 0.28
        let dt = CGFloat(min(1.0 / 30, last.map { now.timeIntervalSince($0) } ?? 0)); last = now
        // 좌우로 약 24도 기울이면 끝까지. 센서가 없으면 손가락으로
        if let g = motion.deviceMotion?.gravity {
            let goal = 0.5 + max(-1, min(1, CGFloat(g.x) / 0.4)) * 0.5
            px += (goal - px) * min(1, dt * 14)
        }
        let x = max(pw / 2, min(w - pw / 2, px * w))
        if held {
            held = false; bx = x; by = py - 7
            vx = w * 0.38 * (Bool.random() ? 1 : -1); vy = -w * 0.92
            return 0
        }
        var nx = bx + vx * dt, ny = by + vy * dt
        if nx < 4 || nx > w - 4 { vx = -vx; nx = max(4, min(w - 4, nx)) }
        if ny < 4 { vy = abs(vy); ny = 4 }
        let col = Int(nx / c), row = Int(ny / c)
        if ny < w, (0..<10).contains(col), (0..<10).contains(row), alive[row * 10 + col] {
            let hit = row * 10 + col
            alive[hit] = false; left -= 1
            if bomb[hit] {
                for r in max(0, row - 1)...min(9, row + 1) { for c2 in max(0, col - 1)...min(9, col + 1) where alive[r * 10 + c2] { alive[r * 10 + c2] = false; left -= 1 } }
            }
            if Int(bx / c) != col { vx = -vx } else { vy = -vy }
            if left == 0 { motion.stopDeviceMotionUpdates(); return 1 }
            return 0
        }
        if vy > 0, by < py - 4, ny >= py - 4, abs(nx - x) < pw / 2 + 4 {
            let sp = hypot(vx, vy), o = max(-1, min(1, (nx - x) / (pw / 2)))
            vx = sp * 0.75 * o; vy = -sqrt(sp * sp - vx * vx); ny = py - 4
        }
        if ny > size.height + 8 { motion.stopDeviceMotionUpdates(); return 2 }
        bx = nx; by = ny
        return 0
    }

    func draw(_ ctx: inout GraphicsContext, _ size: CGSize, cells: Bool) {
        let w = size.width, gap: CGFloat = 4, s = (w - gap * 9) / 10, pw = w * 0.28
        if cells {
            for i in 0..<100 {
                let r = CGRect(x: CGFloat(i % 10) * (s + gap), y: CGFloat(i / 10) * (s + gap), width: s, height: s)
                let path = Path(roundedRect: r, cornerRadius: 5)
                ctx.fill(path, with: .color(alive[i] ? (i < mineN ? Theme.teal : GoalCellsCard.mintC) : Theme.track))
                if alive[i] && bomb[i] { ctx.fill(Path(ellipseIn: r.insetBy(dx: s * 0.32, dy: s * 0.32)), with: .color(Theme.gold)) }
                if i == 99 && !alive[i] { ctx.stroke(Path(roundedRect: r.insetBy(dx: 1, dy: 1), cornerRadius: 4), with: .color(Theme.gold), lineWidth: 2) }
            }
        }
        let x = max(pw / 2, min(w - pw / 2, px * w)), py = size.height - 6
        ctx.fill(Path(roundedRect: CGRect(x: x - pw / 2, y: py - 2.5, width: pw, height: 5), cornerRadius: 2.5), with: .color(Theme.teal))
        let (ballX, ballY) = cells && !held ? (bx, by) : (x, py - 7)
        ctx.fill(Path(ellipseIn: CGRect(x: ballX - 4, y: ballY - 4, width: 8, height: 8)), with: .color(Theme.gold))
    }
}

/// 왼쪽 칸은 폭의 ratio만큼 정사각형, 오른쪽은 남은 폭에 세로 가운데
struct SplitRow: Layout {
    let ratio: CGFloat, spacing: CGFloat
    var rightFill: CGFloat = 1   // 오른쪽 칸 높이 = 왼쪽 높이 × 이 값 (100칸 카드: 0.9 = 패들 칸을 뺀 칸 그림 끝 줄)
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? 320, side = w * ratio
        let lh = subviews.first?.sizeThatFits(.init(width: side, height: nil)).height ?? 0
        let h = subviews.count > 1 ? subviews[1].sizeThatFits(.init(width: w - side - spacing, height: nil)).height : 0
        return CGSize(width: w, height: max(lh, h))
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let side = b.width * ratio, rw = b.width - side - spacing
        subviews.first?.place(at: CGPoint(x: b.minX, y: b.midY), anchor: .leading, proposal: .init(width: side, height: nil))
        // 오른쪽 칸은 왼쪽 높이(× rightFill)를 받는다 (안의 Spacer로 아래 버튼을 칸 그림 끝 줄에 맞출 수 있게)
        guard subviews.count > 1 else { return }
        let lh = subviews[0].sizeThatFits(.init(width: side, height: nil)).height
        let rh = subviews[1].sizeThatFits(.init(width: rw, height: nil)).height
        subviews[1].place(at: CGPoint(x: b.minX + side + spacing, y: b.minY), anchor: .topLeading, proposal: .init(width: rw, height: max(rh, lh * rightFill)))
    }
}

/// 10×10 칸, 왼쪽 위부터 줄마다 채운다. 경계 칸은 비율만큼 가로로 나눠 칠한다. 마지막 칸(오른쪽 아래)은 금색 테두리 = 목표
struct GoalCellGrid: View {
    let mine: Double, filled: Double, lost: Double
    var body: some View {
        Canvas { ctx, size in
            let gap: CGFloat = 4, s = (size.width - gap * 9) / 10
            for i in 0..<100 {
                let r = CGRect(x: CGFloat(i % 10) * (s + gap), y: CGFloat(i / 10) * (s + gap), width: s, height: s)
                let path = Path(roundedRect: r, cornerRadius: 5)
                ctx.fill(path, with: .color(Theme.track))
                if i == 99 && filled < 100 {
                    ctx.fill(path, with: .color(Theme.card))
                    ctx.stroke(Path(roundedRect: r.insetBy(dx: 1, dy: 1), cornerRadius: 4), with: .color(Theme.gold), lineWidth: 2)
                    continue
                }
                // 이 칸 [i, i+1] 안에서 각 색이 차지하는 비율
                let lo = Double(i)
                let seg: [(Double, Double, Color)] = [(0, mine, Theme.teal), (mine, filled, GoalCellsCard.mintC), (filled, filled + lost, GoalCellsCard.lostC)]
                ctx.drawLayer { l in
                    l.clip(to: path)
                    for (a, b, c) in seg {
                        let x0 = max(0, min(1, a - lo)), x1 = max(0, min(1, b - lo))
                        if x1 > x0 { l.fill(Path(CGRect(x: r.minX + s * x0, y: r.minY, width: s * (x1 - x0), height: s)), with: .color(c)) }
                    }
                }
            }
        }
    }
}

// MARK: 플러스 미션 1 결과 — 지금 평가액

struct GoalHoldResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        MissionPage(kicker: String(localized: "미션 1 완료 · 평가액·수익률 열림"), title: String(localized: "지금 내 평가액")) {
            if m.ret >= 0 { GuideBubble(pose: .spread, text: String(localized: "지금 평가액이에요. 들어간 돈보다 \(String(describing: AppModel.sgn(m.ret))) 위에 있어요. 여기서 출발해 목표 금액까지 가는 길을 그려요.")) }
            VStack(alignment: .leading, spacing: 8) {
                HStack { Spacer(); Text("원금 \(noWon(AppModel.man(m.cost)))") }.appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(noWon(AppModel.man(m.total))).appFont(30, .bold).roll(noWon(AppModel.man(m.total)))
                    Text(AppModel.sgn(m.ret)).appFont(16, .bold).foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
                }
                Text("\(m.focusName) 비중 \(AppModel.pct(m.focusWeight))").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            if m.ret >= 0 {
                BoardButtons(next: String(localized: "다음: 목표 정하기"), to: .g1)
            } else {
                Text("지금 마이너스 · 회복 루트가 더 맞아요 · 종목은 그대로")
                    .appFont(15).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0xFDF0E1, dark: 0x3A2A17), in: RoundedRectangle(cornerRadius: 12))
                PrimaryButton(title: String(localized: "회복 루트로")) { m.switchRoute(.recover) }
                BoardButtons(next: nil, to: nil)
                Button("목표 루트로 계속") { m.boardPath.append(.g1) }
                    .appFont(15, .semibold).foregroundStyle(Theme.sub).frame(maxWidth: .infinity, minHeight: 44)
            }
        }
    }
}

// MARK: 목표 정하기 — 경로 그래프 고정 + 배수 카드

struct GoalSetView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    /// 설정에서 열었을 때: 저장하면 돌아가고, 미션 진행은 건드리지 않는다
    var fromSettings = false
    @FocusState private var focus: Bool
    @State private var k = ""
    @State private var y = ""
    @State private var a = ""
    @State private var mo = ""

    @State private var put: Task<Void, Never>?
    private var editing: Bool { fromSettings || m.gDone.contains("goal") }

    /// 고친 숫자는 손을 멈추고 0.35초 뒤에 넣는다 (넣을 때마다 위 그래프를 다시 세니, 한 글자마다 세지 않게)
    private func later() {
        put?.cancel()
        put = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(350))
            if !Task.isCancelled { putAll() }
        }
    }
    private func putAll() {
        put?.cancel()
        if let d = Double(k.replacingOccurrences(of: ",", with: "")), d > 0 { m.gK = d }
        if let d = Int(y.replacingOccurrences(of: ",", with: "")) { m.gY = min(30, max(1, d)) }
        if m.bare { m.gA = max(0, Double(a) ?? 0) } else { m.gM = max(0, Double(mo.replacingOccurrences(of: ",", with: "")) ?? 0) }
    }

    var body: some View {
        let start = m.goalStart
        let ok = m.gK > 0 && m.gY >= 1
        PinnedLayout {
            GoalSetHead(mission: !editing)
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if editing {
                    GuideBubble(pose: .point, text: String(localized: "저장해도 미션 진행은 그대로예요."))
                } else if m.bare {
                    GuideBubble(pose: .wave, text: String(localized: "처음 시작하시는군요. …… 첫 목표부터 정해요. 지금 모아 둔 투자금이 없으면 0을 넣으세요."))
                } else {
                    GuideBubble(pose: .think, text: String(localized: "언제까지, 얼마를 모으고 싶나요? …… 카드는 매달 넣는 돈으로 닿을 확률이 반반쯤인 금액이에요. 모두 지금 \(String(describing: AppModel.wonK(start)))에서 출발한 계산이에요."))
                }
                if !m.bare {
                    Text(m.gM > 0 ? String(localized: "확률 50%인 목표") : String(localized: "적립 없이") + String(localized: " 확률 50%인 목표")).appFont(13, .semibold).foregroundStyle(Theme.sub)
                }
                HStack(spacing: 8) {   // 가로 한 줄에 넷
                    ForEach(m.goalPresets) { p in
                        let on = m.gK == p.k && m.gY == p.y
                        Button { put?.cancel(); m.gK = p.k; m.gY = p.y; k = String(Int(p.k)); y = String(p.y) } label: {
                            VStack(spacing: 2) {
                                Text(p.name).appFont(15, .bold).lineLimit(1).minimumScaleFactor(0.7)
                                Text("\(p.y)년").appFont(12).foregroundStyle(Theme.sub)
                                if !p.need.isEmpty { Text(p.need).appFont(13, .bold).foregroundStyle(Theme.teal).lineLimit(1).minimumScaleFactor(0.7) }
                            }
                            .padding(.horizontal, 4).padding(.vertical, 10).frame(maxWidth: .infinity, minHeight: 64)
                            .background(on ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
                        }.buttonStyle(.plain)
                    }
                }
                HStack(alignment: .top, spacing: 8) {   // 가로 한 줄에 셋
                    inputField(String(localized: "목표 (만원)"), $k).focused($focus).onChange(of: k) { later() }.frame(maxWidth: .infinity)
                    inputField(String(localized: "기간 (년)"), $y).focused($focus).onChange(of: y) { later() }.frame(maxWidth: .infinity)
                    if m.bare {
                        inputField(String(localized: "모아 둔 돈 (만원)"), $a).focused($focus).onChange(of: a) { later() }.frame(maxWidth: .infinity)
                    } else {
                        inputField(String(localized: "월 적립 (만원)"), $mo).focused($focus).onChange(of: mo) { later() }.frame(maxWidth: .infinity)
                    }
                }
                if !m.bare {
                    Text("출발 \(noWon(AppModel.wonK(start))) · 목표일 \(m.goalToShort)").appFont(13).foregroundStyle(Theme.sub)
                }
                PrimaryButton(title: editing ? String(localized: "저장") : String(localized: "길 보기"), color: ok ? Theme.teal : Theme.muted) {
                    guard ok else { return }
                    focus = false
                    putAll()
                    if m.bare { m.gM = max(5, (m.neededMonthly() / 5).rounded() * 5) }
                    if fromSettings { dismiss(); return }
                    if m.gDone.contains("goal") { m.boardPath.removeLast(); return }   // 고치기: 미션 판으로 돌아간다
                    m.gDone.insert("goal"); m.boardPath.append(.g1r)
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .onAppear { k = String(Int(m.gK)); y = String(m.gY); a = String(Int(m.gA)); mo = String(Int(m.gM)) }
        .scrollDismissesKeyboard(.interactively)
        .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료") { focus = false } } }
    }
}

// 목표 정하기 위 칸. 따로 두어 자판을 칠 때는 다시 세지 않고, 넣은 값이 바뀔 때만 그래프·확률을 다시 센다
private struct GoalSetHead: View {
    @Environment(AppModel.self) private var m
    let mission: Bool

    var body: some View {
        // 플러스: 내가 매달 넣을 돈(gM)으로 닿을 확률. 시작 전: 보통의 경우로 맞춘 월 적립으로
        let plus = !m.bare
        let need = m.neededMonthly(), path = m.simulate(plus ? m.mineMix : AppModel.mixes[0], monthly: plus ? m.gM : need)
        VStack(alignment: .leading, spacing: 8) {
            if mission { Text(String(localized: "미션 \(String(describing: m.goalNo("goal"))) / \(String(describing: m.goalTotal))")).appFont(14, .semibold).foregroundStyle(Theme.teal) }
            Text("언제까지, 얼마나?").appFont(22, .bold)
            HStack(alignment: .firstTextBaseline) {
                Text("\(m.gY)년 뒤 \(noWon(AppModel.wonK(m.gK)))").appFont(17, .bold)
                Spacer()
                (Text("닿을 확률 ") + Text(AppModel.pct(path.prob)).bold().foregroundColor(Theme.teal)).appFont(14)
            }
            if plus {
                Text(need > 0 ? String(localized: "맞추려면 월 \(String(describing: noWon(AppModel.wonK(need))))") : String(localized: "이미 충분")).appFont(14, .semibold)
            } else {
                Text((need > 0 ? String(localized: "월 ") + noWon(AppModel.wonK(need)) : String(localized: "이미 충분")) + String(localized: " · 1년 \(String(describing: noWon(AppModel.wonK(need * 12))))")).appFont(14, .semibold)
                Text(m.gK <= 1000 ? String(localized: "첫 목표 · 적립 습관") : String(localized: "연 7%면 해마다 +\(String(describing: noWon(AppModel.wonK(m.gK * 0.07))))")).appFont(13).foregroundStyle(Theme.sub)
            }
            GoalPathChart(sims: [(path, Theme.teal, true)], goal: m.gK).frame(height: 80)
            HStack { Text("지금"); Spacer(); Text("\(m.gY)년") }.appFont(11).foregroundStyle(Theme.muted)
        }
        .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
    }
}

struct GoalSetResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let need = m.neededMonthly(), base = m.bare ? AppModel.mixes[0] : m.mineMix
        let path = m.simulate(base, monthly: need)
        MissionPage(kicker: String(localized: "미션 \(String(describing: m.goalNo("goal"))) 완료 · 경로 열림"), title: String(localized: "\(String(describing: m.gY))년 뒤 \(String(describing: noWon(AppModel.wonK(m.gK))))까지")) {
            VStack(alignment: .leading, spacing: 6) {
                Text("보통의 경우 매달").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Text(need > 0 ? noWon(AppModel.wonK(need)) : String(localized: "0 · 이미 충분")).appFont(30, .bold).roll(need > 0 ? noWon(AppModel.wonK(need)) : String(localized: "0 · 이미 충분"))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            GuideBubble(pose: .back, text: m.bare
                        ? String(localized: "같이 봐요. 매달 \(String(describing: AppModel.wonK(need)))씩 넣으면 지날 길이에요. 점선은 내가 넣은 원금이에요.")
                        : String(localized: "같이 봐요. 지금 금액이 보통의 경우처럼 자란다고 보고, 모자란 만큼을 매달로 나눈 금액이에요."), note: String(localized: "그래프 쪽을 돌아봄"))
            Card {
                (Text("월 \(noWon(AppModel.wonK(need)))씩 ") + Text("· \(base.name)").foregroundColor(Theme.sub)).appFont(15, .bold)
                GoalPathChart(sims: [(path, Theme.teal, true)], goal: m.gK).frame(height: 150)
                HStack { Text("지금"); Spacer(); Text("\(String(format: "%g", Double(m.gY) / 2))년"); Spacer(); Text("\(m.gY)년") }.appFont(11).foregroundStyle(Theme.muted)
                FlowRow(spacing: 10) {
                    legend(Theme.teal, String(localized: "보통의 경우")); legend(Theme.teal.opacity(0.2), String(localized: "100번 중 90번"))
                    legend(Theme.muted, String(localized: "원금")); legend(Color(hex: 0xC8352E), String(localized: "목표"))
                }
                (Text("목표일 달성 ") + Text(AppModel.pct(path.prob)).bold() + Text(" · 보통 \(path.etaText)")).appFont(14)
            }
            BoardButtons(next: String(localized: "다음: 구성 고르기"), to: .g3)
        }
    }
}

// MARK: 구성 고르기 — 구성별 길 비교 고정 + 칩

private func compositionBar(_ weights: [String: Double]) -> some View {
    let keys = ["T", "I", "G", "B", "C"].filter { (weights[$0] ?? 0) > 0.001 }
    return GeometryReader { g in
        HStack(spacing: 0) {
            ForEach(keys, id: \.self) { k in
                Rectangle().fill(compColor[k]!).frame(width: g.size.width * (weights[k] ?? 0))
                    .overlay(alignment: .trailing) { Rectangle().fill(Theme.card).frame(width: 2) }
            }
        }
    }
    .frame(height: 10).clipShape(Capsule()).background(Theme.track, in: Capsule())
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(keys.map { "\(compName[$0]!) \(AppModel.pct(weights[$0] ?? 0))" }.joined(separator: ", "))
}

struct GoalMixView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []

    var body: some View {
        @Bindable var m = m
        let sims = m.goalMixes.map { m.simulate($0) }
        let sel = sims.first { $0.mix.id == m.gMix } ?? sims[0]
        let sh = m.shift(sel.mix)
        PinnedLayout {
            VStack(alignment: .leading, spacing: 8) {
                Text("미션 \(m.goalNo("mix")) / \(m.goalTotal)").appFont(14, .semibold).foregroundStyle(Theme.teal)
                Text("어떤 구성으로?").appFont(20, .bold)
                Text("지수·업종 묶음 · 월 \(noWon(AppModel.wonK(m.gM))) · \(m.gY)년").appFont(12).foregroundStyle(Theme.sub)
                GoalPathChart(sims: sims.map { ($0, mixColor[$0.mix.id] ?? Theme.teal, $0.mix.id == sel.mix.id) }, goal: m.gK, showPrincipal: false).frame(height: 100)
                FlowRow(spacing: 10) {
                    ForEach(sims, id: \.mix.id) { s in legend(mixColor[s.mix.id] ?? Theme.teal, s.mix.name) }
                    legend(Color(hex: 0xC8352E), String(localized: "목표"))
                }
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 3).fill(mixColor[sel.mix.id] ?? Theme.teal).frame(width: 10, height: 10)
                    Text(sel.mix.name).appFont(15, .bold)
                    Text(sel.mix.tag).appFont(12).foregroundStyle(Theme.sub)
                }
                compositionBar(sh.weights)
                HStack(spacing: 6) {
                    pill(String(localized: "목표일 달성"), AppModel.pct(sel.prob))
                    pill(String(localized: "보통 도착"), sel.etaText)
                    pill(String(localized: "나쁜 해 한 번"), AppModel.sgn0(sel.badYear))
                }
                ChipRow(items: m.goalMixes.map { ($0.id, $0.name) }, selection: $m.gMix, fill: true)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if m.bare {
                    GuideBubble(pose: .think, text: String(localized: "어떤 구성이 덜 흔들리는지 보세요. …… 처음이라면 단순한 구성부터 비교해 봐도 돼요."))
                } else {
                    GuideBubble(pose: .side, text: String(localized: "구성을 하나씩 눌러 지금 내 구성과 비교해 보세요. 빨리 가는 구성일수록 더 흔들려요. 나쁜 해 한 번의 크기도 같이 보세요."))
                }
                if !sh.line.isEmpty { Text(sh.line).appFont(13).foregroundStyle(Theme.sub) }
                Text(sel.mix.desc).appFont(15).lineSpacing(3)
                BasketList(baskets: [.G, .B], open: $open, title: String(localized: "업종 묶음"))
                Text("나쁜 해 한 번: 20년에 한 번쯤 오는 해의 1년 변화 · 가정값 · 투자 권유 아님")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
                PrimaryButton(title: String(localized: "\"\(String(describing: sel.mix.name))\"으로 정하기")) { m.gDone.insert("mix"); m.boardPath.append(.g3r) }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }

    private func pill(_ k: String, _ v: String) -> some View {
        VStack(spacing: 2) { Text(k).appFont(11).foregroundStyle(Theme.sub); Text(v).appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.7) }
            .frame(maxWidth: .infinity).padding(.vertical, 6)
            .background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
    }
}

struct GoalMixResultView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []
    var body: some View {
        let sel = m.simulate(m.selectedMix), sh = m.shift(m.selectedMix)
        let next = m.goalSteps.first { !m.gDone.contains($0.id) && m.goalAvailable($0) }
        MissionPage(kicker: String(localized: "미션 \(String(describing: m.goalNo("mix"))) 완료 · 구성 비교 열림"), title: String(localized: "내 계획: \(String(describing: sel.mix.name))")) {
            GuideBubble(pose: .cheer, text: m.bare ? String(localized: "정했어요. 매달 적립을 나누는 비율이에요. 실제 매매 판단은 직접 하셔야 해요.") : String(localized: "정했어요. 계획만 기록해요. 실제 매매 판단은 직접 하셔야 해요."))
            Text(sel.mix.desc).appFont(15).foregroundStyle(Theme.sub)
            Card {
                (Text("목표일 달성 ") + Text(AppModel.pct(sel.prob)).bold() + Text(" · 보통 ") + Text(sel.etaText).bold()).appFont(15)
            }
            if !sh.rows.isEmpty {
                Card {
                    Text(m.bare ? String(localized: "적립 나누기") : String(localized: "비중이 바뀌면")).appFont(15, .bold)
                    ForEach(sh.rows, id: \.0) { label, sub, amt, up in
                        HStack {
                            Text(label).appFont(15, .semibold)
                            Text(sub).appFont(13).foregroundStyle(Theme.sub)
                            Spacer()
                            Text(amt).appFont(15, .bold).foregroundStyle(up ? Theme.teal : Color(hex: 0x2450C8, dark: 0x7FA2F0))
                        }
                        .frame(minHeight: 34)
                    }
                    if !sh.note.isEmpty { Text(sh.note).appFont(12).foregroundStyle(Theme.sub).lineSpacing(2) }
                }
            }
            if let b = sel.mix.basket { BasketList(baskets: [b], open: $open, title: String(localized: "담을 업종")) }
            Text("계획만 기록 · 매매는 직접").appFont(14).foregroundStyle(Theme.sub)
            BoardButtons(next: next.map { String(localized: "다음: \(String(describing: $0.title))") }, to: next?.to)
        }
    }
}

// MARK: 비중 조정 세금 (플러스)

struct GoalTaxView: View {
    @Environment(AppModel.self) private var m
    @State private var gain = ""
    var body: some View {
        @Bindable var m = m
        let sh = m.shift(m.selectedMix), sale = sh.sell * m.goalStart
        // 양도세는 해외(미국) 주식만. 가장 큰 종목이 국내 주식이면 계산하지 않는다
        let drnk = m.focusRow, us = drnk?.sym.currency == .usd, gainRate = us ? drnk.map { 1 - $0.h.avg / $0.sym.last } ?? 0 : 0
        let gainSale = sale * gainRate, G0 = m.taxGain
        let tx = { (x: Double) in max(0, x - 250) * 0.22 }
        let tOne = tx(G0 + gainSale), tSplit = tx(G0 + gainSale / 2) + tx(gainSale / 2), tNo = tx(G0)
        let rows: [(String, String, String, Double)] = [
            ("one", String(localized: "한 해에 모두"), String(localized: "이익 \(String(describing: noWon(AppModel.wonK(max(0, G0 + gainSale))))) − 공제 250만"), tOne),
            ("split", String(localized: "두 해에 반씩"), String(localized: "공제 250만 × 2"), tSplit),
            ("none", String(localized: "적립만"), String(localized: "오래 걸림"), tNo),
        ]
        let pick = rows.first { $0.0 == m.goalTaxPick } ?? rows[1]
        MissionPage(kicker: String(localized: "미션 \(String(describing: m.goalNo("tax"))) / \(String(describing: m.goalTotal))"), title: String(localized: "비중 맞추려 팔면 세금은?")) {
            GuideBubble(pose: .point, text: String(localized: "비중을 매도로 맞추면 이익에 세금이 붙어요. 공제는 해마다 있어서, 나누는 방식에 따라 세금이 달라져요. 계산 예시예요."))
            Text("한국 거주자 · 미국 주식만").appFont(13, .semibold).foregroundStyle(Theme.teal)
            inputField(String(localized: "올해 해외주식 이익 (원)"), $gain, commas: true).onChange(of: gain) { _, v in m.taxGain = commaValue(v) / 1e4 }
            if sh.sell > 0 && gainSale > 0 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("내년 5월 세금 · \(pick.1)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        if pick.0 != "one" { Text(noWon(AppModel.wonK(tOne))).appFont(18).strikethrough().foregroundStyle(Theme.muted) }
                        Text(noWon(AppModel.wonK(pick.3))).appFont(30, .bold).roll(noWon(AppModel.wonK(pick.3)))
                        Text(pick.0 == "one" ? String(localized: "가장 많음") : "−\(noWon(AppModel.wonK(max(0, tOne - pick.3))))").appFont(13, .bold).foregroundStyle(Theme.mint)
                    }
                    Text(pick.2).appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
                ChipRow(items: [("one", String(localized: "한 해에")), ("split", String(localized: "두 해에")), ("none", String(localized: "적립만"))], selection: $m.goalTaxPick, fill: true)
                Text("가장 큰 종목 \(AppModel.pct(m.focusWeight))→\(AppModel.pct(m.focusWeight - sh.sell)) · 두 해에 나누면 세금 −\(noWon(AppModel.wonK(max(0, tOne - tSplit))))")
                    .appFont(14).foregroundStyle(Theme.sub).lineSpacing(2)
            } else {
                Text(!us ? String(localized: "가장 큰 종목이 국내 주식 · 양도세 계산 제외")
                     : sh.sell > 0 ? String(localized: "줄이는 부분이 손실 · 낼 세금 없음 · 같은 해 이익과 합산")
                     : String(localized: "\"\(String(describing: m.selectedMix.name))\"은 팔 게 없음 · 미션 \(String(describing: m.goalNo("mix")))에서 다른 구성으로 비교"))
                    .appFont(15).padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
            Text("결제일 기준 · 계산 예시 · 세무 상담·매매 권유 아님").appFont(12).foregroundStyle(Theme.muted)
            PrimaryButton(title: String(localized: "확인")) { m.gDone.insert("tax"); m.boardPath = [] }
        }
        .onAppear { gain = commaDigits(String(Int((m.taxGain * 1e4).rounded()))) }
    }
}

/// 게임 박스 칸 그림: 판(위)에 웅크린 졸라맨과 아래 벽돌 / 자산 흐름 파도 위 보드 탄 졸라맨. 값이 바뀌지 않는 정지 그림
private struct GameThumb: View {
    enum Kind { case brick, wave }
    let kind: Kind
    var body: some View {
        Canvas { ctx, sz in
            let W = sz.width, H = sz.height
            // 바탕·벽돌·파도는 외곽선만. 채우는 것은 졸라맨·패들·공 / 졸라맨·보드·별 (2026-10-27)
            if kind == .brick { brick(ctx, W, H) } else { wave(ctx, W, H) }
        }
        .accessibilityHidden(true)
    }
    private func line(_ c: GraphicsContext, _ ls: [(CGFloat, CGFloat, CGFloat, CGFloat)]) {
        var p = Path()
        for l in ls { p.move(to: CGPoint(x: l.0, y: l.1)); p.addLine(to: CGPoint(x: l.2, y: l.3)) }
        c.stroke(p, with: .color(Theme.ink), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }
    private func head(_ c: GraphicsContext, _ x: CGFloat, _ y: CGFloat) {
        c.fill(Path(ellipseIn: CGRect(x: x - 4, y: y - 4, width: 8, height: 8)), with: .color(Theme.ink))
    }
    private func brick(_ c: GraphicsContext, _ W: CGFloat, _ H: CGFloat) {
        let cols = 8, rows = 4, gap: CGFloat = 3, ch: CGFloat = 9, cw = (W - 16 - CGFloat(cols - 1) * gap) / CGFloat(cols)
        let kb = Int((W * 0.66 - 8) / (cw + gap))   // 공 바로 밑 칸
        for r in 0..<rows { for k in 0..<cols {
            // 깨진 자리: 공은 위에서 오니 위 칸이 깨진 곳만 아래도 깨진다. 공 밑 칸과 그 오른쪽 칸도 비운다
            if (r == 0 && (k % 5 == 2 || k == kb || k == kb + 1)) || (r == 1 && k == 2) { continue }
            let y = H - 8 - CGFloat(rows - r) * (ch + gap)
            c.stroke(Path(roundedRect: CGRect(x: 8 + CGFloat(k) * (cw + gap), y: y, width: cw, height: ch), cornerRadius: 2),
                     with: .color(Theme.muted), lineWidth: 1)
        } }
        let px = W * 0.38, pw = W * 0.24, py: CGFloat = 30
        c.fill(Path(roundedRect: CGRect(x: px, y: py, width: pw, height: 6), cornerRadius: 3), with: .color(Theme.ink))
        // 졸라맨: 판 위에 웅크려 아래 공을 가리킨다
        let X = { (f: CGFloat) in px + pw * f }
        head(c, X(0.5), py - 17)
        line(c, [(X(0.5), py - 13, X(0.42), py - 6),
                 (X(0.42), py - 6, X(0.3), py - 9), (X(0.3), py - 9, X(0.32), py), (X(0.42), py - 6, X(0.58), py - 5), (X(0.58), py - 5, X(0.62), py),
                 (X(0.47), py - 11, X(0.62), py - 11), (X(0.62), py - 11, X(0.74), py - 4), (X(0.47), py - 11, X(0.36), py - 16)])
        var tr = Path(); tr.move(to: CGPoint(x: X(0.7), y: py + 8)); tr.addLine(to: CGPoint(x: W * 0.66, y: H * 0.44))
        c.stroke(tr, with: .color(Theme.muted), style: StrokeStyle(lineWidth: 1.5, dash: [3, 4]))
        c.fill(Path(ellipseIn: CGRect(x: W * 0.66 - 5, y: H * 0.44 - 5, width: 10, height: 10)), with: .color(Theme.gold))
    }
    private func wave(_ c: GraphicsContext, _ W: CGFloat, _ H: CGFloat) {
        let pts: [CGPoint] = (0...40).map { i in
            let t = CGFloat(i) / 40
            return CGPoint(x: t * W, y: H * 0.62 - sin(t * 6.5) * H * 0.14 - t * H * 0.22 + sin(t * 19) * 3)
        }
        var top = Path(); top.addLines(pts)
        c.stroke(top, with: .color(Theme.muted), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        // 졸라맨: 무릎 굽혀 보드 위에, 두 팔을 넓게
        var s = c
        s.translateBy(x: pts[17].x, y: pts[17].y - 3); s.rotate(by: .radians(-0.35))
        s.fill(Path(ellipseIn: CGRect(x: -14, y: -3, width: 28, height: 6)), with: .color(Theme.gold))
        head(s, 1, -25)
        line(s, [(0, -21, -1, -10), (-1, -10, -6, -5), (-6, -5, -7, -2), (-1, -10, 5, -6), (5, -6, 7, -2),
                 (0, -18, -9, -21), (-9, -21, -14, -17), (0, -18, 8, -14), (8, -14, 13, -16)])
        c.draw(Text("★").font(.system(size: 13, weight: .bold)).foregroundColor(Theme.gold), at: CGPoint(x: pts[30].x, y: pts[30].y - 14))
    }
}

// MARK: 3개월 인터미션

struct GoalIntermissionView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        @Bindable var m = m
        let fixed = m.gWeeks
        let now = m.goalSaved, eta = m.monthsTo(from: now, monthly: m.gM), plan = Double(m.gY * 12 - fixed.count - (m.gWeekCur == nil ? 0 : 1))
        let last = m.gWeekCur ?? fixed.last
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack { Text("석 달 체크인").appFont(12, .bold).foregroundStyle(Theme.mint); Spacer(); Text("\(fixed.count)/3").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                    Text("이번 달, 계획대로 넣었나요?").appFont(20, .bold)
                    HStack(spacing: 6) {
                        ForEach(0..<3, id: \.self) { j in
                            let p: GoalWeek? = j < fixed.count ? fixed[j] : (j == fixed.count ? m.gWeekCur : nil)
                            VStack(spacing: 2) {
                                Text(String(localized: "\(String(describing: j + 1))달") + (j == fixed.count ? String(localized: " · 이번 달") : "")).appFont(12)
                                Text(p?.label ?? (j == fixed.count ? String(localized: "고르는 중") : "")).appFont(12, .bold)
                            }
                            .frame(maxWidth: .infinity, minHeight: 64)
                            .foregroundStyle(p != nil ? .white : j == fixed.count ? Theme.yellow : Theme.muted)
                            .background(p?.color ?? .clear, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(j == fixed.count ? Theme.yellow : (p?.color ?? Theme.slate), style: StrokeStyle(lineWidth: 2, dash: p == nil ? [5, 4] : [])))
                        }
                    }
                    if let last {
                        Text((last == .more ? String(localized: "더 넣음") : last == .less ? String(localized: "덜 넣음") : String(localized: "계획대로"))
                             + (eta.isFinite ? String(localized: " · \(String(describing: AppModel.eta(eta))) 도착 (") + (eta < plan ? String(localized: "\(String(describing: Int(plan - eta)))개월 빠름") : eta > plan ? String(localized: "\(String(describing: Int(eta - plan)))개월 늦음") : String(localized: "목표 시점")) + ")" : ""))
                            .appFont(14, .semibold).foregroundStyle(Theme.ink)
                            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(last == .less ? Color(hex: 0xFDF0E1, dark: 0x3A2A17) : last == .more ? Color(hex: 0xE4F0FB, dark: 0x16283A) : Color(hex: 0xE3F4EC, dark: 0x163226), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .foregroundStyle(.white).padding(16)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))

                GuideBubble(pose: .stand, text: String(localized: "3개월 동안 매달 1분이면 돼요. 이번 달 할 일을 하고 나서 표시해 주세요. 체크인할 때마다 캐릭터 위젯이 하나씩 열려요."))

                if fixed.count < 3 {
                    // 다음 달이 열리기 전에는 방금 한 달을 다시 고를 수 있다
                    let edit = m.monthEditable, k = edit ? fixed.count - 1 : fixed.count, step = AppModel.goalMonthSteps[k]
                    Card {
                        Text("\(k + 1)달째 체크인").appFont(13, .bold).foregroundStyle(Theme.teal)
                        HStack(spacing: 8) {
                            Text(step.whereText).appFont(11, .bold).padding(.horizontal, 8).padding(.vertical, 2).background(Theme.mint, in: Capsule())
                            Text(step.task).appFont(15, .bold)
                        }
                        HStack(spacing: 6) {
                            ForEach(GoalWeek.allCases, id: \.self) { p in
                                let on = edit ? fixed[k] == p : m.gWeekCur == p
                                Button { if edit { m.editLastMonth(p) } else { m.gWeekCur = p } } label: {
                                    VStack(spacing: 2) {
                                        Text(p.label).appFont(15, .bold)
                                        Text(String(localized: "월 ") + AppModel.wonK(m.gM * p.factor)).appFont(11).foregroundStyle(Theme.sub)
                                    }
                                    .foregroundStyle(Theme.ink).padding(.vertical, 8)
                                    .frame(maxWidth: .infinity, minHeight: 64)
                                    .background(on ? p.color.opacity(0.15) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(p.color, lineWidth: on ? 3 : 2))
                                }.buttonStyle(.plain)
                            }
                        }
                        Text("다음 달 전까지 바꿀 수 있음").appFont(12).foregroundStyle(Theme.muted)
                        // 달은 시작한 날부터 30일마다 열린다 (서버 시각). 열리기 전에는 남은 날을 보여 준다
                        if let wait = m.monthGateText {
                            Label(wait, systemImage: "clock").appFont(13).foregroundStyle(Theme.sub)
                                .fixedSize(horizontal: false, vertical: true)
                        } else if m.gWeekCur != nil {
                            Button("체크인") { withAnimation { m.confirmGoalMonth() } }
                                .appFont(14, .semibold).foregroundStyle(Theme.ink).frame(maxWidth: .infinity, minHeight: 44)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                        }
                    }
                }
                Card {
                    Text("체크인 보상 위젯").appFont(15, .bold)
                    Text("오늘의 움직임 · 1달 세리 · 2달 시오 · 3달 모두").appFont(13).foregroundStyle(Theme.sub)
                    ForEach(0..<3, id: \.self) { j in
                        let on = j < fixed.count
                        HStack(spacing: 12) {
                            if j < 2 {
                                Pixel(name: (on ? "spr_" : "sil_") + ["seri", "sio"][j], width: 42, height: 60)
                            } else {
                                HStack(spacing: 0) { ForEach(Shelter.friends) { f in Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 18, height: 26) } }
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack { Text("오늘의 움직임").appFont(11, .bold); Spacer(); Text(on ? String(localized: "받음") : j == fixed.count ? String(localized: "이번 달") : String(localized: "\(String(describing: j + 1))달 뒤")).appFont(11).foregroundStyle(Theme.sub) }
                                Text(AppModel.sgn(m.todayMove)).appFont(16, .bold)
                                Text(AppModel.goalMonthSteps[j].reward).appFont(12).foregroundStyle(Theme.sub)
                            }
                        }
                        .padding(12)
                        .background(on ? Theme.card : Color(hex: 0xF0F2F4, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.border : Theme.dash, style: StrokeStyle(lineWidth: 1, dash: on ? [] : [4, 3])))
                        .opacity(on ? 1 : 0.8)
                    }
                }
                if fixed.count >= 3 {
                    PrimaryButton(title: m.interDone ? String(localized: "미션 판으로") : String(localized: "인터미션 끝")) { m.gDone.insert("inter"); m.boardPath = [] }
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }
}

