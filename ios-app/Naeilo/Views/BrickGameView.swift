import SwiftUI

// 판 깨기 화면: 고르는 화면(난이도·조작·선수·단계 12곳)과 게임 화면. 엔진과 규칙은 Data/BrickGame.swift

/// 1000칸 카드의 버튼
struct BrickGameButton: View {
    @State private var show = false
    var body: some View {
        Button { show = true } label: {
            Label("판 깨기 · 내 1000칸으로 벽돌깨기", systemImage: "gamecontroller")
                .appFont(14, .bold).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(.white)
                .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .fullScreenCover(isPresented: $show) { BrickGameHub() }
    }
}

struct BrickGameHub: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var rec = GameRecord.load()
    @State private var game: BrickGame?

    private var players: [String] {
        Shelter.friends.enumerated().filter { m.friendOn($0.offset) }.map(\.element.id) + (m.meUnlocked ? ["me"] : [])
    }
    private var items: [String] { Shelter.items.prefix(m.itemsOn).map(\.id) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("내 1000칸으로 하는 벽돌깨기").appFont(22, .bold)
                        Text("채운 칸이 벽돌이에요. 판은 위, 공은 아래로. 실제 칸은 그대로예요.").appFont(14).foregroundStyle(Theme.sub)
                    }
                    section("난이도") {
                        ChipRow(items: GameLevel.allCases.map { ($0, $0.title) }, selection: $rec.level, fill: true)
                        Text("하수는 판이 길고 한 번에 많이 깨져요. 기준 칸의 \(Int(rec.level.clear * 100))%를 깨면 다음 단계가 열려요.")
                            .appFont(12).foregroundStyle(Theme.sub)
                    }
                    section("조작") {
                        ChipRow(items: [(true, "폰 기울이기"), (false, "손가락으로 끌기")], selection: $rec.tilt, fill: true)
                    }
                    section("선수") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) { ForEach(players, id: \.self) { playerCard($0) } }.padding(.vertical, 2)
                        }
                        Text(GamePlayer.skill(skillOf(rec.player))).appFont(13, .semibold).foregroundStyle(Theme.tealDark)
                    }
                    section("물건 아이템") {
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
                    HStack {
                        Text("단계 · 『중첩된 현실』 쉼터").appFont(13, .bold).foregroundStyle(Theme.sub)
                        Spacer()
                        Text("\(rec.level.title) 기록 합계 \(rec.total(rec.level).formatted())점").appFont(12, .bold).foregroundStyle(Theme.sub)
                    }
                    ForEach(GameStage.all) { stageRow($0) }
                    Text("기록은 이 폰 안에만 남아요. 이름이나 기기 정보는 어디에도 보내지 않아요.").appFont(12).foregroundStyle(Theme.sub)
                }
                .screen()
            }
            .background(Theme.bg)
            .navigationTitle("판 깨기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { dismiss() } } }
        }
        .onAppear { if !players.contains(rec.player) { rec.player = players.first ?? "seri" } }
        .onChange(of: rec) { _, v in v.save() }
        .fullScreenCover(item: $game) { g in
            BrickPlayView(game: g, record: $rec) { next in
                game = nil
                if let next { DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { start(next.0, next.1) } }
            }
        }
    }

    /// 내 캐릭터는 평정 지수 유형의 스킬 (아직 없으면 세리)
    private func skillOf(_ p: String) -> String { p == "me" ? (m.calm.type ?? m.me?.type ?? "seri") : p }

    private func start(_ s: GameStage, _ lv: GameLevel) {
        rec.level = lv
        let n = m.cellsNow
        game = BrickGame(stage: s, level: lv, player: rec.player, skill: skillOf(rec.player), items: items, tilt: rec.tilt,
                         assist: rec.lossCount(s.n, lv) >= 3, filled: n, floor: min(n, m.cellsFloor), yesterday: m.cellsYesterday)
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
        let open = m.devAll || rec.open(s.n, rec.level)
        let done = rec.cleared[rec.level.rawValue, default: []].contains(s.n)
        return Button { if open { start(s, rec.level) } } label: {
            HStack(spacing: 12) {
                Image(s.bg.id).interpolation(.none).resizable().scaledToFill()
                    .frame(width: 80, height: 50).clipShape(RoundedRectangle(cornerRadius: 8))
                    .saturation(open ? 1 : 0).opacity(open ? 1 : 0.5)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(s.bg.volume).appFont(12, .bold).foregroundStyle(Theme.tealDark)
                        Text(s.bg.name).appFont(14, .bold).lineLimit(1)
                        if done { Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.teal).font(.system(size: 13)) }
                    }
                    Text(open ? s.rule : "앞 단계를 깨면 열려요").appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
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
    /// 닫을 때 (다음에 시작할 단계·난이도, 없으면 고르는 화면으로)
    let onClose: ((GameStage, GameLevel)?) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var saved = false

    private let brickDark = Theme.teal
    private let brickLight = Color(hex: 0x7FD3C9, dark: 0x3C8F86)
    private let clay = Color(hex: 0xC9A27E, dark: 0x8A6A4E)

    var body: some View {
        let g = game
        let _ = g.frame
        VStack(spacing: 8) {
            hud(g)
            GeometryReader { geo in
                let s = min(geo.size.width / BrickGame.W, geo.size.height / BrickGame.H)
                let fw = BrickGame.W * s, fh = BrickGame.H * s
                ZStack {
                    Theme.card
                    Image(g.stage.bg.id).interpolation(.none).resizable().scaledToFill()
                        .frame(width: fw, height: fh).clipped().opacity(0.3)
                    field(g, s).frame(width: fw, height: fh)
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
            Text(g.tilt ? "폰을 좌우로 기울여 판을 움직이고, 눌러서 공을 보내요" : "판을 손가락으로 끌고, 떼면 공을 보내요")
                .appFont(12).foregroundStyle(Theme.sub)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Theme.bg)
        .overlay { overlay(g) }
        .onAppear { g.begin() }
        .onDisappear { g.end() }
        .onChange(of: scenePhase) { _, p in if p != .active { g.pause() } }
        .onChange(of: g.phase) { _, p in
            guard (p == .cleared || p == .over) && !saved else { return }
            saved = true
            g.newBest = record.finish(g.stage.n, g.level, score: g.score, won: p == .cleared)
        }
        .statusBarHidden()
    }

    private func hud(_ g: BrickGame) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                Button { g.pause() } label: {
                    Image(systemName: "pause.fill").font(.system(size: 16, weight: .bold)).frame(width: 40, height: 40)
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
                    Text(String(repeating: "♥", count: max(0, g.lives))).appFont(12, .bold).foregroundStyle(Theme.up)
                }
            }
            HStack(spacing: 8) {
                ProgressBar(value: min(1, Double(g.broken) / Double(g.target)), height: 8, fill: Theme.teal)
                Text("\(g.broken)/\(g.target)칸").appFont(11, .bold).foregroundStyle(Theme.sub).monospacedDigit()
            }
            HStack(spacing: 6) {
                if g.shield { tag("보호막") }
                if g.assist { tag("도움 켜짐 · 공 느리게") }
                ForEach(g.activeEffects, id: \.self) { e in if let n = effectName(e) { tag(n) } }
                Spacer(minLength: 0)
            }
            .frame(height: 20)
        }
    }

    private func tag(_ s: String) -> some View {
        Text(s).appFont(11, .bold).padding(.horizontal, 8).frame(height: 20)
            .foregroundStyle(Theme.tealDark).background(Theme.tealBg, in: Capsule())
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

    private func field(_ g: BrickGame, _ s: Double) -> some View {
        Canvas { ctx, _ in
            func r(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect { CGRect(x: x * s, y: y * s, width: w * s, height: h * s) }
            // 벽돌
            ctx.opacity = g.dark ? 0.08 : 1
            for i in 0..<1000 where g.hp[i] != 0 {
                let b = g.rect(i)
                let rc = r(b.minX + 0.05, b.minY + 0.05, 0.9, 0.9)
                if g.hp[i] < 0 { ctx.fill(Path(rc), with: .color(Theme.ink)); continue }
                let c: Color = switch g.kind[i] { case 1: brickDark; case 3: brickLight; case 4: Theme.track; default: brickLight }
                ctx.fill(Path(rc), with: .color(c))
                if g.kind[i] == 3 { ctx.stroke(Path(rc.insetBy(dx: 0.6, dy: 0.6)), with: .color(Theme.up), lineWidth: 1.2) }
                if g.hp[i] == 2 { ctx.fill(Path(roundedRect: rc.insetBy(dx: rc.width * 0.22, dy: rc.height * 0.22), cornerRadius: 1.5), with: .color(clay)) }
            }
            ctx.opacity = 1
            if g.exitCell >= 0, g.hp[g.exitCell] > 0 {
                let b = g.rect(g.exitCell)
                ctx.fill(Path(r(b.minX, b.minY, 1, 1)), with: .color(Theme.yellow))
            }
            // 보호막
            if g.shield { ctx.fill(Path(r(0, 0.1, BrickGame.W, 0.3)), with: .color(Theme.yellow.opacity(0.8))) }
            // 판과 선수
            let pw = g.paddleW
            ctx.fill(Path(roundedRect: r(g.paddleX - pw / 2, BrickGame.PY, pw, BrickGame.PH), cornerRadius: 0.4 * s), with: .color(Theme.ink))
            if let sp = ctx.resolveSymbol(id: "player") {
                ctx.draw(sp, at: CGPoint(x: g.paddleX * s, y: (BrickGame.PY - 1.8) * s))
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

    @ViewBuilder private func overlay(_ g: BrickGame) -> some View {
        let lines = GamePlayer.lines(g.player)
        switch g.phase {
        case .ready:
            bubble(g, lines.0, sub: g.stage.rule) { EmptyView() }
                .allowsHitTesting(false)
        case .playing:
            if let t = g.toast {
                Text(t.0).appFont(13, .bold).padding(.horizontal, 12).frame(minHeight: 32)
                    .foregroundStyle(.white).background(Theme.ink.opacity(0.85), in: Capsule())
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
                bubble(g, won ? lines.1 : lines.2,
                       sub: "\(won ? "깼어요" : "끝났어요") · \(g.score.formatted())점\(g.newBest ? " · 새 최고 기록" : "")") {
                    if won, let next { button("다음 단계 · \(next.bg.volume)", primary: true) { onClose((next, g.level)) } }
                    button("다시 하기", primary: !won) { onClose((g.stage, g.level)) }
                    if !won, lose >= 3, let easy = g.level.easier {
                        button("쉬운 판으로 갈까요? · \(easy.title)") { onClose((g.stage, easy)) }
                    }
                    button("단계 고르기") { onClose(nil) }
                }
            }
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
                    Text(line).appFont(15, .bold).fixedSize(horizontal: false, vertical: true)
                    if let sub { Text(sub).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
                }
                Spacer(minLength: 0)
            }
            if g.phase == .ready { Text("화면을 누르면 시작해요").appFont(13, .bold).foregroundStyle(Theme.tealDark) }
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
