import SwiftUI

// 판 깨기 화면: 고르는 화면(난이도·조작·선수·단계 12곳)과 게임 화면. 엔진과 규칙은 Data/BrickGame.swift

/// 1000칸 카드의 버튼
struct BrickGameButton: View {
    /// 그 카드의 칸 (회복 루트: 바닥/회복, 목표 루트: 내 돈/시장)
    let filled: Int, floor: Int, yesterday: Int
    var title = "벽돌깨기"
    var plain = false   // 흰 바탕 · 회색 테두리 (미션 탭 보조 버튼)
    @State private var show = false
    var body: some View {
        Button { show = true } label: {
            Label(title, systemImage: "gamecontroller")
                .appFont(14, .bold).lineLimit(1).minimumScaleFactor(0.8).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(plain ? Theme.ink : .white)
                .background(plain ? Theme.card : Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(plain ? Theme.border : .clear, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .fullScreenCover(isPresented: $show) { BrickGameHub(filled: filled, floor: floor, yesterday: yesterday) }
    }
}

struct BrickGameHub: View {
    let filled: Int, floor: Int, yesterday: Int
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var rec = GameRecord.load()
    @State private var game: BrickGame?
    @State private var board: GameStage?
    @State private var allStages = false
    @State private var showItems = false

    private var players: [String] {
        Shelter.friends.enumerated().filter { m.friendOn($0.offset) }.map(\.element.id) + (m.meUnlocked ? ["me"] : [])
    }
    private var items: [String] { Shelter.items.map(\.id) }   // 게임에선 처음부터 물건 모두 (쉼터 방은 하루 하나씩 돌아온 만큼)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // 이름 옆에 버튼을 한 줄로
                    VStack(alignment: .leading, spacing: 6) {
                        inline("난이도") { ChipRow(items: GameLevel.allCases.map { ($0, $0.title) }, selection: $rec.level, fill: true) }
                        Text(rec.level.note).appFont(12).foregroundStyle(Theme.sub)
                    }
                    inline("조작") { ChipRow(items: [(true, "기울이기"), (false, "끌기")], selection: $rec.tilt, fill: true) }
                    section("선수") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) { ForEach(players, id: \.self) { playerCard($0) } }.padding(.vertical, 2)
                        }
                        Text(GamePlayer.skill(skillOf(rec.player))).appFont(13, .semibold).foregroundStyle(Theme.tealDark)
                    }
                    HStack {
                        Text("단계 · 『중첩된 현실』 쉼터").appFont(13, .bold).foregroundStyle(Theme.sub)
                        Spacer()
                        Text("\(rec.level.title) 기록 합계 \(rec.total(rec.level).formatted())점").appFont(12, .bold).foregroundStyle(Theme.sub)
                    }
                    // 처음엔 세 단계만, 나머지는 접어 둔다
                    ForEach(GameStage.all.prefix(allStages ? GameStage.all.count : 3)) { stageRow($0) }
                    Button { withAnimation { allStages.toggle() } } label: {
                        Text(allStages ? "접기 ▴" : "나머지 \(GameStage.all.count - 3)단계 펼치기 ▾")
                            .appFont(14, .semibold).foregroundStyle(Theme.tealDark).frame(maxWidth: .infinity, minHeight: 44)
                            .background(Theme.tealBg, in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    // 물건 아이템은 접어 둔다
                    Button { withAnimation { showItems.toggle() } } label: {
                        HStack {
                            Text("물건 아이템 \(items.count)개").appFont(13, .bold).foregroundStyle(Theme.sub)
                            Spacer()
                            Text(showItems ? "접기 ▴" : "펼치기 ▾").appFont(13, .semibold).foregroundStyle(Theme.tealDark)
                        }
                        .frame(minHeight: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if showItems {
                        if items.isEmpty {
                            Text("쉼터에 물건이 돌아오면 벽돌에서 아이템으로 떨어져요.").appFont(13).foregroundStyle(Theme.sub)
                        } else {
                            VStack(alignment: .leading, spacing: 6) {
                                ForEach(items, id: \.self) { id in
                                    HStack(spacing: 8) {
                                        Pixel(name: "art_" + id, width: 24, height: 24)
                                        Text(Shelter.items.first { $0.id == id }?.name ?? id).appFont(13, .bold)
                                        Text(GamePlayer.item(id)).appFont(12).foregroundStyle(Theme.sub)
                                    }
                                }
                            }
                        }
                    }
                    section("내 기록") {
                        Text("기록과 순위는 이 폰 안에만 남아요. 아무 데도 보내지 않아요.")
                            .appFont(12).foregroundStyle(Theme.sub)
                    }
                }
                .screen()
            }
            .background(Theme.bg)
            .navigationTitle("내 1000칸으로 하는 벽돌깨기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
        }
        .onAppear { if !players.contains(rec.player) { rec.player = players.first ?? "seri" } }
        .onChange(of: rec) { _, v in v.save() }
        .sheet(item: $board) { BrickBoardSheet(stage: $0, level: rec.level, list: rec.top($0.n, rec.level)) }
        .fullScreenCover(isPresented: Binding(get: { game != nil }, set: { if !$0 { game?.end(); game = nil } })) {
            if let g = game {
                BrickPlayView(game: g, record: $rec) { next in
                    g.end()
                    // 다음 판은 화면을 닫지 않고 그 자리에서 바꾼다. 이어하기면 남은 생명·점수 배율을 넘긴다
                    if let next { game = make(next.0, next.1, carry: next.2 ? g : nil) } else { game = nil }
                }
                .id(g.id)
            }
        }
    }

    /// 내 캐릭터는 평정 지수 유형의 스킬 (아직 없으면 세리)
    private func skillOf(_ p: String) -> String { p == "me" ? (m.calm.type ?? m.me?.type ?? "seri") : p }

    private func start(_ s: GameStage, _ lv: GameLevel) { game = make(s, lv, carry: nil) }

    /// carry: 방금 깬 판 (이어하기 — 남은 생명 그대로, 점수 배율 +10%)
    private func make(_ s: GameStage, _ lv: GameLevel, carry: BrickGame?) -> BrickGame {
        rec.level = lv
        let g = BrickGame(stage: s, level: lv, player: rec.player, skill: skillOf(rec.player), items: items, tilt: rec.tilt,
                          assist: rec.lossCount(s.n, lv) >= 3, filled: filled, floor: min(filled, floor), yesterday: yesterday,
                          chain: carry.map { $0.chain + 1 } ?? 0, lives: carry.map { max(1, $0.lives) })
        g.friends = players
        return g
    }

    private func inline<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        HStack(spacing: 10) { Text(title).appFont(13, .bold).foregroundStyle(Theme.sub).frame(width: 52, alignment: .leading); c() }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) { Text(title).appFont(13, .bold).foregroundStyle(Theme.sub); c() }
    }

    private func playerCard(_ id: String) -> some View {
        let on = rec.player == id
        let name = id == "me" ? ((m.me?.name).flatMap { $0.isEmpty ? nil : $0 } ?? "나") : (Shelter.friends.first { $0.id == id }?.name ?? id)
        return Button { rec.player = id } label: {
            VStack(spacing: 4) {
                CharPixel(id: id, width: 36, height: 51)
                Text(name).appFont(12, .bold).lineLimit(1)
            }
            .frame(width: 72, height: 84)
            .background(on ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func stageRow(_ s: GameStage) -> some View {
        let open = rec.open(s.n, rec.level)
        let done = rec.cleared[rec.level.rawValue, default: []].contains(s.n)
        return HStack(spacing: 8) {
            stageCard(s, open: open, done: done)
            if open {
                Button { board = s } label: {
                    Image(systemName: "trophy").appFont(16, .bold).foregroundStyle(Theme.tealDark)
                        .frame(width: 44, height: 44).background(Theme.tealBg, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain).accessibilityLabel("\(s.bg.volume) 내 기록 순위")
            }
        }
    }

    private func stageCard(_ s: GameStage, open: Bool, done: Bool) -> some View {
        Button { if open { start(s, rec.level) } } label: {
            HStack(spacing: 12) {
                Image(s.bg.id).interpolation(.none).resizable().scaledToFill()
                    .frame(width: 80, height: 50).clipShape(RoundedRectangle(cornerRadius: 8))
                    .saturation(open ? 1 : 0).opacity(open ? 1 : 0.5)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(s.bg.volume).appFont(12, .bold).foregroundStyle(Theme.tealDark)
                        Text(s.bg.name).appFont(14, .bold).lineLimit(1)
                        if done { Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.teal).appFont(13) }
                    }
                    Text(open ? s.rule + " 벽돌 \(s.bricks(rec.level))개" + (s.tetrisCount(rec.level) == 0 ? "." : ", 숨은 테트리스 블럭 \(s.tetrisCount(rec.level))개.") : "앞 단계를 깨면 열려요").appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    if let b = rec.bestOf(s.n, rec.level) { Text("최고 \(b.formatted())점").appFont(12, .bold).foregroundStyle(Theme.sub) }
                }
                Spacer(minLength: 0)
                Image(systemName: open ? "play.fill" : "lock.fill").foregroundStyle(open ? Theme.teal : Theme.sub)
            }
            .padding(10)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .disabled(!open)
    }
}

struct BrickPlayView: View {
    let game: BrickGame
    @Binding var record: GameRecord
    /// 닫을 때 (다음에 시작할 단계·난이도·이어하기, 없으면 고르는 화면으로)
    let onClose: ((GameStage, GameLevel, Bool)?) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var saved = false

    private let brickDark = Theme.teal
    private let brickLight = Color(hex: 0x7FD3C9, dark: 0x3C8F86)
    private let clay = Color(hex: 0xC9A27E, dark: 0x8A6A4E)
    private let stackColor = Color(hex: 0xB9C3E6, dark: 0x4E5C8C)   // 단계가 더 쌓은 칸
    /// 테트리스 조각 색 (I O T S Z J L)
    static let tetrisColors: [Color] = [Color(hex: 0x00C8E8), Color(hex: 0xF2D000), Color(hex: 0xA03CE0),
                                        Color(hex: 0x2EC84A), Color(hex: 0xE8343C), Color(hex: 0x2F5BE8), Color(hex: 0xF08C1E)]

    var body: some View {
        let g = game
        let live = g.phase != .paused   // 끝난 뒤에도 인물은 계속 움직인다 (엔진은 멈춤)
        // 매 프레임 엔진을 한 걸음 움직이고 다시 그린다 (멈춤·끝나면 쉼)
        TimelineView(.animation(minimumInterval: nil, paused: !live)) { tl in
            let _ = g.tick(tl.date)
            screen(g, tl.date)
        }
        .contentShape(Rectangle())
        .onTapGesture { g.launch() }
        .onAppear { g.begin() }
        .onChange(of: scenePhase) { _, p in if p != .active { g.pause() } }
        .onChange(of: g.phase) { _, p in
            guard (p == .cleared || p == .over) && !saved else { return }
            saved = true
            g.newBest = record.finish(g.stage.n, g.level, score: g.score, won: p == .cleared, player: g.player)
            // 깨면 바로 다음 단계로 이어진다 (잠깐 보여 주고)
            if p == .cleared, let next = GameStage.all.first(where: { $0.n == g.stage.n + 1 }) {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { onClose((next, g.level, true)) }
            }
        }
        .statusBarHidden()
    }

    private func screen(_ g: BrickGame, _ now: Date) -> some View {
        VStack(spacing: 8) {
            hud(g)
            host(g, now)
            GeometryReader { geo in
                let s = min(geo.size.width / BrickGame.W, geo.size.height / BrickGame.H)
                let fw = BrickGame.W * s, fh = BrickGame.H * s
                ZStack {
                    Theme.card
                    Image(g.stage.bg.id).interpolation(.none).resizable().scaledToFill()
                        .frame(width: fw, height: fh).clipped().opacity(0.3)
                    field(g, s, now).frame(width: fw, height: fh)
                }
                .frame(width: fw, height: fh)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Theme.ink, lineWidth: 2))
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .onChanged { v in g.drag(to: v.location.x / s) }
                    .onEnded { _ in g.launch() })
                .position(x: geo.size.width / 2, y: geo.size.height / 2)
                .accessibilityLabel("판 깨기 게임. 깬 칸 \(g.broken), 기준 \(g.target)")
            }
            Text(g.tilt ? "기울여 이동 · 눌러서 발사" : "끌어서 이동 · 떼면 발사")
                .appFont(12).foregroundStyle(Theme.sub)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Theme.bg)
        .overlay { overlay(g) }
    }

    /// 위쪽 인물: 가끔 눈을 깜빡이고, 시작·물건·놓침·끝에 말풍선. 말이 없을 때는 방 설명
    private func host(_ g: BrickGame, _ now: Date) -> some View {
        let tt = now.timeIntervalSinceReferenceDate
        // 3.1초와 4.3초 주기를 섞어 일정하지 않게 깜빡인다
        let blink = tt.truncatingRemainder(dividingBy: 3.1) < 0.14 || tt.truncatingRemainder(dividingBy: 4.3) < 0.12
        // 말풍선은 한 줄씩 넓게: 위 줄은 선수, 아래 줄은 끼어든 인물
        return VStack(spacing: 6) {
            HStack(alignment: .center, spacing: 10) {
                Group {
                    if g.player == "me" { CharPixel(id: "me", width: 34, height: 48) }
                    else if g.speaking { MotionSprite(friend: g.player, pose: pose(g.mood), height: 52) }
                    else { Pixel(name: "spr_\(g.player)" + (blink ? "_blink" : ""), width: 34, height: 48) }
                }
                .frame(width: 44, height: 52)
                .modifier(Alive(t: tt, seed: 0, hop: g.speaking && g.t - g.moodAt < 0.5))
                if g.speaking && !g.speech.isEmpty {
                    Text(g.speech).appFont(21, .bold).lineLimit(1).minimumScaleFactor(0.6)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.ink, lineWidth: 1.5))
                } else {
                    Text(g.stage.scene).appFont(14).foregroundStyle(Theme.sub).lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            // 끼어든 인물 (오른쪽)
            if g.guestShown {
                HStack(spacing: 8) {
                    Text(g.guestLine).appFont(21, .bold).lineLimit(1).minimumScaleFactor(0.6)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .background(Theme.tealBg, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
                    VStack(spacing: 1) {
                        guestFigure(g.guest).scaleEffect(x: -1, y: 1)
                            .modifier(Alive(t: tt, seed: 1.7, hop: g.t - (g.guestUntil - 3.6) < 0.5))
                        Text(guestName(g.guest)).appFont(9, .bold).foregroundStyle(Theme.sub).lineLimit(1)
                    }
                    .frame(width: 44)
                }
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .frame(minHeight: 52)
        .animation(.easeOut(duration: 0.15), value: g.speaking)
        .animation(.easeOut(duration: 0.25), value: g.guestShown)
    }

    /// 상황 → 동작 그림 (Guide.swift 의 mo_<인물>_<동작>)
    private func pose(_ e: GameLines.Event) -> Pose {
        switch e {
        case .start: .wave
        case .item: .spread
        case .lose, .last: .think
        case .half: .point
        case .clear: .cheer
        case .over: .side
        case .rewind: .back
        }
    }

    /// 끼어든 인물 그림: 기본 인물은 도트 그림, 보조 인물은 마네킹 샘플 조합
    @ViewBuilder private func guestFigure(_ id: String) -> some View {
        if id.hasPrefix("s:"), let sm = MeSample.all.first(where: { $0.name == String(id.dropFirst(2)) }) {
            MeAvatar(me: MeChar(name: sm.name, parts: sm.parts), width: 30, height: 42)
        } else {
            Pixel(name: "spr_\(id)", width: 30, height: 42)
        }
    }
    private func guestName(_ id: String) -> String {
        id.hasPrefix("s:") ? String(id.dropFirst(2)) : (Shelter.friends.first { $0.id == id }?.name ?? "")
    }

    private func hud(_ g: BrickGame) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                Button { g.pause() } label: {
                    Image(systemName: "pause.fill").appFont(16, .bold).frame(width: 40, height: 40)
                        .background(Theme.card, in: Circle()).overlay(Circle().stroke(Theme.border, lineWidth: 1.5))
                }
                .buttonStyle(.plain).accessibilityLabel("멈춤")
                VStack(alignment: .leading, spacing: 1) {
                    Text("\(g.stage.bg.volume) · \(g.level.title)").appFont(11, .bold).foregroundStyle(Theme.sub)
                    Text(g.stage.bg.name).appFont(14, .bold).lineLimit(1)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 1) {
                    Text("\(g.score.formatted())점").appFont(18, .bold).monospacedDigit()
                    Text("남은 공 \(max(0, g.lives))").appFont(12, .bold).foregroundStyle(Theme.up)
                }
            }
            HStack(spacing: 8) {
                ProgressBar(value: min(1, Double(g.broken) / Double(g.target)), height: 8, fill: Theme.teal)
                Text("\(g.broken)/\(g.target)칸").appFont(11, .bold).foregroundStyle(Theme.sub).monospacedDigit()
            }
            HStack(spacing: 6) {
                if g.chain > 0 { tag("이어하기 · 점수 ×" + String(format: "%.1f", g.scoreMul)) }
                if g.net > 0 { tag("안전망 \(g.net)", yellow: true) }
                if g.shield { tag("보호막") }
                if g.assist { tag("도움 켜짐 · 공 느리게") }
                ForEach(g.activeEffects, id: \.self) { e in if let n = effectName(e) { tag(n) } }
                Spacer(minLength: 0)
            }
            .frame(height: 20)
            pickedRow(g)
        }
    }

    /// 이번 판에 먹은 물건
    @ViewBuilder private func pickedRow(_ g: BrickGame) -> some View {
        let got = g.items.filter { (g.picked[$0] ?? 0) > 0 }
        HStack(spacing: 6) {
            Text("먹은 물건").appFont(11, .bold).foregroundStyle(Theme.sub)
            if got.isEmpty { Text("아직 없음").appFont(11).foregroundStyle(Theme.sub) }
            ForEach(got, id: \.self) { id in
                HStack(spacing: 2) {
                    Pixel(name: "art_" + id, width: 18, height: 18)
                    Text("×\(g.picked[id] ?? 0)").appFont(11, .bold).monospacedDigit()
                }
            }
            Spacer(minLength: 0)
        }
        .frame(height: 20)
        .opacity(g.items.isEmpty ? 0 : 1)
    }

    private func tag(_ s: String, yellow: Bool = false) -> some View {
        Text(s).appFont(11, .bold).padding(.horizontal, 8).frame(height: 20)
            .foregroundStyle(yellow ? Color(hex: 0x5A4300) : Theme.tealDark).background(yellow ? Theme.yellow : Theme.tealBg, in: Capsule())
    }

    private func effectName(_ e: String) -> String? {
        switch e {
        case "slow": "보리차 · 느리게"
        case "light": "현관 등 · 길 보임"
        case "clock": "벽시계 · 멈춤"
        case "wide": "머리끈 · 긴 판"
        case "aim": "비대칭 그릇 · 꺾기"
        case "double": "대추씨 · 2배"
        case "pierce": "원로의 구슬 · 뚫기"
        case "fast": "오늘 칸 · 빠르게"
        default: nil
        }
    }

    /// now 를 그림 안에서 읽어야 프레임마다 다시 그린다 (엔진 값은 지켜보지 않으니, 같은 입력이면 Canvas 가 그리기를 건너뛴다)
    private func field(_ g: BrickGame, _ s: Double, _ now: Date) -> some View {
        Canvas { ctx, _ in
            _ = now
            func r(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect { CGRect(x: x * s, y: y * s, width: w * s, height: h * s) }
            // 벽돌
            if g.dark { ctx.fill(Path(CGRect(x: 0, y: 0, width: BrickGame.W * s, height: BrickGame.H * s)), with: .color(.black.opacity(0.25))) }
            ctx.opacity = g.dark ? 0.4 : 1
            for i in 0..<1000 where g.hp[i] != 0 {
                let b = g.rect(i)
                let rc = r(b.minX + 0.05, b.minY + 0.05, 0.9, 0.9)
                if g.hp[i] < 0 { ctx.fill(Path(rc), with: .color(Theme.ink)); continue }
                if let sp = g.special[i], g.revealed[i] { special(&ctx, sp, rc, now); continue }
                if g.group[i] >= 0, g.bigs[g.group[i]].revealed {   // 테트리스 색, 위·왼쪽은 밝게
                    let cell = r(b.minX, b.minY, 1, 1), col = Self.tetrisColors[g.bigs[g.group[i]].shape]
                    ctx.fill(Path(cell), with: .color(col))
                    ctx.fill(Path(CGRect(x: cell.minX, y: cell.minY, width: cell.width, height: cell.height * 0.18)), with: .color(.white.opacity(0.45)))
                    ctx.fill(Path(CGRect(x: cell.minX, y: cell.maxY - cell.height * 0.18, width: cell.width, height: cell.height * 0.18)), with: .color(.black.opacity(0.25)))
                    continue
                }
                let c: Color = switch g.kind[i] { case 1: brickDark; case 3: brickLight; case 4: Theme.track; case 5: stackColor; default: brickLight }
                ctx.fill(Path(rc), with: .color(c))
                if g.kind[i] == 3 { ctx.stroke(Path(rc.insetBy(dx: 0.6, dy: 0.6)), with: .color(Theme.up), lineWidth: 1.2) }
                if g.hp[i] >= 2 { ctx.fill(Path(roundedRect: rc.insetBy(dx: rc.width * 0.22, dy: rc.height * 0.22), cornerRadius: 1.5), with: .color(clay)) }
                if g.hp[i] >= 3 { ctx.stroke(Path(rc.insetBy(dx: 0.8, dy: 0.8)), with: .color(clay), lineWidth: 1.2) }
                if g.hp[i] == 1 && g.revealed[i] {   // 한 번 맞은 벽돌: 금이 간다
                    var p = Path()
                    p.move(to: CGPoint(x: rc.minX + rc.width * 0.2, y: rc.minY + rc.height * 0.15))
                    p.addLine(to: CGPoint(x: rc.midX, y: rc.midY))
                    p.addLine(to: CGPoint(x: rc.maxX - rc.width * 0.25, y: rc.maxY - rc.height * 0.1))
                    ctx.stroke(p, with: .color(.white.opacity(0.85)), lineWidth: max(1, rc.width * 0.12))
                }
            }
            // 드러난 테트리스 블럭: 남은 횟수
            for big in g.bigs where big.revealed && big.hp > 0 {
                let rs = big.cells.map { g.rect($0) }
                let mid = CGPoint(x: rs.map { $0.midX }.reduce(0, +) / Double(rs.count) * s, y: rs.map { $0.midY }.reduce(0, +) / Double(rs.count) * s)
                ctx.draw(Text("\(big.hp)").font(.system(size: max(9, s * 0.9), weight: .heavy)).foregroundColor(.white), at: mid)
            }
            // 특수 블럭이 터지는 그림
            for b in g.blasts {
                let k = min(1, (g.t - b.t0) / 0.45), a = 1 - k
                switch b.kind {
                case .bomb:
                    let rr = 1 + k * 2.6
                    ctx.fill(Path(ellipseIn: r(b.x - rr, b.y - rr, rr * 2, rr * 2)), with: .color(Theme.orange.opacity(0.55 * a)))
                    ctx.stroke(Path(ellipseIn: r(b.x - rr, b.y - rr, rr * 2, rr * 2)), with: .color(Theme.yellow.opacity(a)), lineWidth: 2)
                case .row: ctx.fill(Path(r(0, b.y - 0.5, BrickGame.W, 1)), with: .color(Theme.yellow.opacity(0.8 * a)))
                case .col: ctx.fill(Path(r(b.x - 0.5, BrickGame.top, 1, BrickGame.H - BrickGame.top)), with: .color(Theme.yellow.opacity(0.8 * a)))
                case .multi, .gift:
                    let rr = 0.6 + k * 1.4
                    ctx.stroke(Path(ellipseIn: r(b.x - rr, b.y - rr, rr * 2, rr * 2)), with: .color(Theme.blue.opacity(a)), lineWidth: 1.5)
                }
            }
            ctx.opacity = 1
            if g.exitCell >= 0, g.hp[g.exitCell] > 0 {
                let b = g.rect(g.exitCell)
                ctx.fill(Path(r(b.minX, b.minY, 1, 1)), with: .color(Theme.yellow))
            }
            // 남은 공 (판 왼쪽 아래)
            let lives = max(0, g.lives)
            for k in 0..<lives {
                let e = r(0.8 + Double(k) * 1.4, BrickGame.H - 1.4, 0.9, 0.9)
                ctx.fill(Path(ellipseIn: e), with: .color(.white)); ctx.stroke(Path(ellipseIn: e), with: .color(Theme.ink), lineWidth: 1)
            }
            // 보호막
            if g.shield {   // 진하고 두껍게, 살짝 숨 쉬듯
                let glow = 0.75 + 0.25 * sin(now.timeIntervalSinceReferenceDate * 4)
                let bar = r(0, 0.15, BrickGame.W, 0.9)
                ctx.fill(Path(r(0, 0, BrickGame.W, 1.4)), with: .color(Theme.yellow.opacity(0.25 * glow)))
                ctx.fill(Path(roundedRect: bar, cornerRadius: 0.3 * s), with: .color(Theme.orange.opacity(glow)))
                ctx.stroke(Path(roundedRect: bar, cornerRadius: 0.3 * s), with: .color(Theme.yellow), lineWidth: 2)
            }
            // 시오 안전망: 판 뒤 노란 줄
            if g.net > 0 {
                let y = BrickGame.netY * s
                var p = Path(); p.move(to: CGPoint(x: 0, y: y)); p.addLine(to: CGPoint(x: BrickGame.W * s, y: y))
                ctx.stroke(p, with: .color(Theme.yellow), lineWidth: max(2, s * 0.3))
            }
            // 판과 선수
            let pw = g.paddleW
            ctx.fill(Path(roundedRect: r(g.paddleX - pw / 2, BrickGame.PY, pw, BrickGame.PH), cornerRadius: 0.4 * s), with: .color(Theme.ink))
            if let sp = ctx.resolveSymbol(id: "player") {
                ctx.draw(sp, at: CGPoint(x: g.paddleX * s, y: (BrickGame.PY - 1.8 + sin(now.timeIntervalSinceReferenceDate * 3) * 0.12) * s))
            }
            // 떨어지는 물건 (위로 올라온다)
            for d in g.drops {
                if let sp = ctx.resolveSymbol(id: d.id) { ctx.draw(sp, at: CGPoint(x: d.x * s, y: d.y * s)) }
            }
            // 공
            let br = BrickGame.r
            for b in g.balls {
                if g.on("light") && !b.stuck {
                    var p = Path(); p.move(to: CGPoint(x: b.x * s, y: b.y * s))
                    let v = max(0.001, (b.vx * b.vx + b.vy * b.vy).squareRoot())
                    p.addLine(to: CGPoint(x: (b.x + b.vx / v * 14) * s, y: (b.y + b.vy / v * 14) * s))
                    ctx.stroke(p, with: .color(Theme.yellow), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                }
                if g.stage.mirror {
                    for mx in [b.x - 13, b.x + 13] where mx > 0 && mx < BrickGame.W {
                        ctx.fill(Path(ellipseIn: r(mx - br, b.y - br, br * 2, br * 2)), with: .color(Theme.ink.opacity(0.18)))
                    }
                }
                let e = r(b.x - br, b.y - br, br * 2, br * 2)
                ctx.fill(Path(ellipseIn: e), with: .color(.white))
                ctx.stroke(Path(ellipseIn: e), with: .color(Theme.ink), lineWidth: 1.5)
            }
            if g.stage.well {
                let c = r(BrickGame.W / 2 - 1.2, 13 - 1.2, 2.4, 2.4)
                ctx.stroke(Path(ellipseIn: c), with: .color(Theme.ink.opacity(0.35)), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
            }
            if g.stage.portal {
                ctx.fill(Path(r(0, BrickGame.PY + 2, 0.25, BrickGame.H - BrickGame.PY - 2)), with: .color(Theme.yellow.opacity(0.6)))
                ctx.fill(Path(r(BrickGame.W - 0.25, BrickGame.PY + 2, 0.25, BrickGame.H - BrickGame.PY - 2)), with: .color(Theme.yellow.opacity(0.6)))
            }
        } symbols: {
            CharPixel(id: g.player, width: 3.6 * s * 24 / 34, height: 3.6 * s).tag("player")
            ForEach(g.items, id: \.self) { id in Pixel(name: "art_" + id, width: 2.2 * s, height: 2.2 * s).tag(id) }
        }
    }

    /// 특수 블럭 한 칸 (폭탄은 반짝인다)
    private func special(_ ctx: inout GraphicsContext, _ sp: BrickGame.Special, _ rc: CGRect, _ now: Date) {
        let w = rc.width, mid = CGPoint(x: rc.midX, y: rc.midY)
        switch sp {
        case .bomb:
            ctx.fill(Path(rc), with: .color(Theme.slate))
            let pulse = 0.5 + 0.5 * sin(now.timeIntervalSinceReferenceDate * 6)
            ctx.fill(Path(ellipseIn: rc.insetBy(dx: w * 0.18, dy: w * 0.18)), with: .color(Theme.up.opacity(0.6 + 0.4 * pulse)))
            ctx.fill(Path(ellipseIn: CGRect(x: rc.maxX - w * 0.35, y: rc.minY + w * 0.05, width: w * 0.25, height: w * 0.25)), with: .color(Theme.yellow))
        case .row, .col:
            ctx.fill(Path(rc), with: .color(Theme.orange))
            var p = Path()
            if sp == .row { p.move(to: CGPoint(x: rc.minX + w * 0.15, y: mid.y)); p.addLine(to: CGPoint(x: rc.maxX - w * 0.15, y: mid.y)) }
            else { p.move(to: CGPoint(x: mid.x, y: rc.minY + w * 0.15)); p.addLine(to: CGPoint(x: mid.x, y: rc.maxY - w * 0.15)) }
            ctx.stroke(p, with: .color(.white), lineWidth: max(1.2, w * 0.18))
        case .multi:
            ctx.fill(Path(rc), with: .color(Theme.blue))
            for (dx, dy) in [(-0.2, 0.15), (0.2, 0.15), (0, -0.18)] {
                let d = w * 0.22
                ctx.fill(Path(ellipseIn: CGRect(x: mid.x + dx * w - d / 2, y: mid.y + dy * w - d / 2, width: d, height: d)), with: .color(.white))
            }
        case .gift:
            ctx.fill(Path(rc), with: .color(Theme.yellow))
            ctx.fill(Path(CGRect(x: mid.x - w * 0.08, y: rc.minY, width: w * 0.16, height: w)), with: .color(Theme.up))
            ctx.fill(Path(CGRect(x: rc.minX, y: mid.y - w * 0.08, width: w, height: w * 0.16)), with: .color(Theme.up))
        }
    }

    /// 물건 먹을 때 뜨는 말 (진한 청록 바탕 · 흰 글씨 · 청록 테두리)
    private let toastBg = Color(hex: 0x0F2E2A)

    @ViewBuilder private func overlay(_ g: BrickGame) -> some View {
        switch g.phase {
        case .ready:
            intro(g)
        case .playing:
            if let t = g.toast {
                Text(t.0).appFont(22, .bold).lineLimit(1).minimumScaleFactor(0.6)
                    .padding(.horizontal, 18).frame(minHeight: 48)
                    .foregroundStyle(.white).background(toastBg, in: Capsule())
                    .overlay(Capsule().stroke(Theme.teal, lineWidth: 2))
                    .padding(.horizontal, 16)
                    .frame(maxHeight: .infinity, alignment: .center).allowsHitTesting(false)
            }
        case .paused:
            dim {
                bubble(g, "잠깐 쉬어요.", sub: nil) {
                    button("계속", primary: true) { g.resume() }
                    button("그만하기") { g.end(); onClose(nil) }
                }
            }
        case .cleared, .over:
            let won = g.phase == .cleared
            let next = GameStage.all.first { $0.n == g.stage.n + 1 }
            let lose = record.lossCount(g.stage.n, g.level)
            dim {
                if won, next != nil {
                    bubble(g, g.speech, sub: "\(g.score.formatted())점 · 다음 단계로 이어가요") { EmptyView() }
                } else {
                bubble(g, g.speech,
                       sub: "\(won ? "깼어요" : "끝났어요") · \(g.score.formatted())점\(g.newBest ? " · 새 최고 기록" : "")\(g.picked.isEmpty ? "" : " · 먹은 물건 \(g.picked.values.reduce(0, +))개")") {
                    button("다시 하기", primary: true) { onClose((g.stage, g.level, false)) }
                    if !won, lose >= 3, let easy = g.level.easier {
                        button("쉬운 판으로 갈까요? · \(easy.title)") { onClose((g.stage, easy, false)) }
                    }
                    button("단계 고르기") { onClose(nil) }
                }
                }
            }
        }
    }

    /// 시작 카드: 한 줄씩 짧게, "눌러서 시작"을 눌러야 시작한다 (글줄이기 2026-10-18)
    private func intro(_ g: BrickGame) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                CharPixel(id: g.player, width: 34, height: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(g.stage.bg.volume) \(g.stage.bg.name)").appFont(28, .bold).lineLimit(1).minimumScaleFactor(0.6)
                    Text(g.stage.scene).appFont(19).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.7)
                }
            }
            Group {
                if g.chain > 0 { line("이어하기 · 점수 ×" + String(format: "%.1f", g.scoreMul) + " · 생명 \(g.lives)") }
                line(g.stage.short)
                line("▣ 두 번 맞아야 깨짐")
                if !g.bigs.isEmpty { line("테트리스 · 모양 둘레까지") }
                line("3번째 줄 아래 특수 블럭 \(GameStage.specials)개")
                Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                    GridRow { legend(Theme.slate, "폭탄 둘레"); legend(Theme.orange, "주황 한 줄") }
                    GridRow { legend(Theme.blue, "파랑 공 3개"); legend(Theme.yellow, "노랑 선물") }
                }
                line("벽돌 \(g.total)개")
            }
            button("눌러서 시작", primary: true) { g.startPlay() }.padding(.top, 6)
        }
        .padding(18)
        .frame(maxWidth: 400)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.ink, lineWidth: 2))
        .padding(16)
    }
    private func line(_ t: String) -> some View {
        Text(t).appFont(21).lineLimit(1).minimumScaleFactor(0.6)
    }
    private func legend(_ c: Color, _ t: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 18, height: 18)
            Text(t).appFont(21).lineLimit(1).minimumScaleFactor(0.6)
        }
    }

    private func dim<C: View>(@ViewBuilder _ c: () -> C) -> some View {
        ZStack { Color.black.opacity(0.35).ignoresSafeArea(); c() }
    }

    private func bubble<C: View>(_ g: BrickGame, _ line: String, sub: String?, @ViewBuilder buttons: () -> C) -> some View {
        VStack(spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                CharPixel(id: g.player, width: 34, height: 48)
                VStack(alignment: .leading, spacing: 4) {
                    Text(line).appFont(21, .bold).fixedSize(horizontal: false, vertical: true)
                    if let sub { Text(sub).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
                }
                Spacer(minLength: 0)
            }
            buttons()
        }
        .padding(16)
        .frame(maxWidth: 340)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.ink, lineWidth: 2))
        .padding(24)
    }

    private func button(_ t: String, primary: Bool = false, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).appFont(14, .bold).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(primary ? .white : Theme.teal)
                .background(primary ? Theme.teal : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }
}

/// 단계 하나의 내 기록 순위 (이 폰의 높은 점수 10판)
struct BrickBoardSheet: View {
    let stage: GameStage
    let level: GameLevel
    let list: [GameRecord.Play]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("\(stage.bg.volume) \(stage.bg.name) · \(level.title)").appFont(15, .bold)
                    if list.isEmpty { Text("아직 이 단계 기록이 없어요.").appFont(14).foregroundStyle(Theme.sub) }
                    ForEach(Array(list.enumerated()), id: \.offset) { i, e in
                        HStack(spacing: 10) {
                            Text("\(i + 1)").appFont(14, .bold).frame(width: 26, alignment: .trailing).monospacedDigit()
                            CharPixel(id: e.p == "me" ? "seri" : e.p, width: 17, height: 24)
                            Text(e.at.formatted(.dateTime.month().day())).appFont(14, .bold)
                            Spacer()
                            Text("\(e.score.formatted())점").appFont(14, .bold).monospacedDigit()
                        }
                        .padding(.horizontal, 12).frame(minHeight: 40)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    }
                    Text("이 폰 안에만 남는 기록이에요. 아무 데도 보내지 않아요.").appFont(12).foregroundStyle(Theme.sub)
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle("내 기록 순위")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
        }
    }
}

/// 정지 그림이 아니게: 숨 쉬듯 위아래로 조금, 아주 살짝 기울고, 말을 시작할 때 한 번 뛴다
private struct Alive: ViewModifier {
    let t: Double, seed: Double
    var hop = false
    func body(content: Content) -> some View {
        let bob = sin((t + seed) * 2.4) * 1.2
        let jump = hop ? -abs(sin((t + seed) * 12)) * 4 : 0
        content
            .offset(y: bob + jump)
            .rotationEffect(.degrees(sin((t + seed) * 1.3) * 1.5), anchor: .bottom)
            .scaleEffect(x: 1, y: 1 + sin((t + seed) * 2.4 + 1) * 0.012, anchor: .bottom)
    }
}
