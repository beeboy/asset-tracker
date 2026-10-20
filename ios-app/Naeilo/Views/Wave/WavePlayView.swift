import SwiftUI

// 파도 타기 한 판 (2번: 플레이 화면). 파도·안개·인물·점 효과를 Canvas 하나에 그리고 30fps 루프로 엔진을 굴린다.
// 끝나면 같은 창 위에 WaveResultPopup. 진행·별 저장(4번)은 onFinish 에 붙인다.

struct WavePlayView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    /// 없으면 내 자산 라인에서 새로 자른다
    var course: WaveEngine.Course? = nil
    var onFinish: (WaveResult) -> Void = { _ in }

    @State private var g: WaveEngine?
    @State private var fx = WaveFX()
    @State private var result: WaveResult?
    @State private var rides: [WaveResult.Ride] = []
    @State private var boardDay = 0
    @State private var run = 0
    @State private var board = SurfCloset.board
    @State private var hat = SurfCloset.hat
    /// 3년 라인은 한 번만 만든다 (바로 다시 할 때 멈칫 없게)
    @State private var line: [(d: String, v: Double)]?

    var body: some View {
        let me = m.me ?? MeChar.current ?? MeChar()
        ZStack {
            if let g {
                Canvas { ctx, size in WaveDraw.scene(ctx, size, g, fx, me: me, board: board, hat: hat) }
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture { tap() }
                hud(g)
            } else {
                VStack(spacing: 12) {
                    Text("파도를 만들 자산 기록이 아직 짧아요").appFont(14).foregroundStyle(Theme.inkFixed)
                    Button("닫기") { dismiss() }.appFont(14, .bold)
                }
            }
            if let result { WaveResultPopup(result: result, again: again, close: { dismiss() }) }
        }
        .background(WaveDraw.sky(g?.fever ?? false).ignoresSafeArea())
        .onAppear { WaveFeedback.shared.prepare() }
        .task(id: run) { await loop() }
    }

    private func hud(_ e: WaveEngine) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                Button { dismiss() } label: { Image(systemName: "xmark").appFont(15, .bold).padding(6) }
                    .accessibilityLabel("그만하기")
                VStack(alignment: .leading, spacing: 1) {
                    Text(e.course.island.name).appFont(13, .bold)
                    Text(e.course.level.name).appFont(11).opacity(0.7)
                }
                Spacer()
                Text("\(e.score)점").appFont(20, .heavy).monospacedDigit()
                Spacer()
                HStack(spacing: 2) {
                    ForEach(0..<WaveEngine.maxHearts, id: \.self) { i in
                        Image(systemName: i < e.hearts ? "heart.fill" : "heart").foregroundStyle(Theme.up)
                    }
                }
                .accessibilityLabel("하트 \(e.hearts)개")
            }
            HStack(spacing: 8) {
                ProgressView(value: e.timeLeft, total: e.duration).tint(Theme.teal)
                Text("\(Int(e.timeLeft.rounded(.up)))초").appFont(12, .bold).monospacedDigit()
                if e.streak >= 2 { Text("\(e.streak)연속").appFont(12, .bold).foregroundStyle(Theme.orange) }
                if e.fever { Text("피버 ×2").appFont(12, .heavy).foregroundStyle(Color(hex: 0xD9480F)) }
                if e.dolphin { Text("🐬") }
                if e.vest { Text("🛟") }
            }
            HStack(spacing: 6) {
                ForEach(e.missions, id: \.self) { x in
                    let done = e.missionsDone.contains(x)
                    Text((done ? "✓ " : "") + x.title).appFont(11, .bold)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(.white.opacity(done ? 0.9 : 0.55), in: Capsule())
                        .opacity(done ? 1 : 0.8)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer()
            if e.elapsed < 6 && !e.isOver {
                Text(e.riding ? "꼭대기에서 톡! 내려요" : "바닥에서 톡! 타요").appFont(13, .bold)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(.white.opacity(0.75), in: Capsule())
                    .allowsHitTesting(false)
            }
        }
        .foregroundStyle(Theme.inkFixed)
        .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 28)
    }

    // MARK: 굴리기

    private func loop() async {
        if g == nil {
            let l = line ?? m.waveLine
            line = l
            guard let c = course ?? WaveEngine.course(from: l) else { return }
            g = WaveEngine(course: c)
        }
        var last = Date()
        while !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(33))
            guard var e = g, !e.isOver else { break }
            let now = Date(), dt = min(0.1, now.timeIntervalSince(last))
            last = now
            let ev = e.tick(dt)
            g = e
            fx.step(dt, riding: e.riding, rising: WaveDraw.rising(e))
            handle(ev)
        }
    }

    private func tap() {
        guard var e = g, !e.isOver else { return }
        let ev = e.riding ? e.leave() : e.board()
        g = e
        handle(ev)
    }

    private func handle(_ ev: [WaveEngine.Event]) {
        guard !ev.isEmpty, let e = g else { return }
        WaveFeedback.shared.play(ev)
        for x in ev {
            switch x {
            case .board(let d, _): boardDay = d
            case .leave(let d, let gain, _): rides.append(.init(from: boardDay, to: d, gain: gain, splash: false))
            case .splash(let d, _, _):
                let v = e.course.values
                rides.append(.init(from: boardDay, to: d, gain: v[d] / v[boardDay] - 1, splash: true))
            case .streak(let n): if n == 3 { WaveFeedback.shared.crown() }
            case .end(let why): finish(e, why)
            case .calm, .fever, .flip, .land, .item, .saved, .mission: break
            }
            fx.react(x)
        }
    }

    private func finish(_ e: WaveEngine, _ why: WaveEngine.EndReason) {
        var r = WaveResult(course: e.course, score: e.score, hearts: e.hearts, rides: rides, why: why)
        r.missions = e.missions.map { ($0.title, e.missionsDone.contains($0)) }
        result = r
        onFinish(r)
    }

    private func again() {
        result = nil; rides = []; fx = WaveFX()
        if let c = line.flatMap({ WaveEngine.course(from: $0) }) ?? g?.course { g = WaveEngine(course: c) }
        run += 1
    }
}

// MARK: 한 판 결과

struct WaveResult {
    struct Ride { let from: Int; let to: Int; let gain: Double; let splash: Bool }
    let course: WaveEngine.Course
    let score: Int
    let hearts: Int
    let rides: [Ride]
    let why: WaveEngine.EndReason
    /// 판 미션 (이름, 깼나)
    var missions: [(title: String, done: Bool)] = []

    /// 별: 점수 1점 이상 1개 · 300점 2개 · 600점 3개. 하트를 다 잃으면 1개까지
    var stars: Int {
        let s = score >= 600 ? 3 : score >= 300 ? 2 : score > 0 ? 1 : 0
        return why == .hearts ? min(s, 1) : s
    }
    var headline: String {
        guard !rides.isEmpty else { return "한 번도 안 탔어요. 바닥에서 톡!" }
        let win = rides.filter { $0.gain > 0 }.count, sp = rides.filter(\.splash).count
        return "\(rides.count)번 타서 \(win)번 벌었어요" + (sp > 0 ? " · 풍덩 \(sp)번" : "")
    }
    /// "이 코스는 2024년 8월의 내 자산이었어요"
    var dateLine: String {
        let p = course.startDate.split(separator: "-")
        guard p.count >= 2, let mo = Int(p[1]) else { return "" }
        return "이 코스는 \(p[0])년 \(mo)월의 내 자산이었어요"
    }
}

// MARK: 연출 상태 (타이머 몇 개 + 점 몇십 개)

struct WaveFX {
    enum Kind { case spray, spark, coin }
    struct Dot { var x, y, vx, vy, life: Double; let kind: Kind }
    struct Pop { let text: String; var life: Double; let color: Color }
    static let flipTime = 0.6, dizzyTime = 0.9

    var dots: [Dot] = []
    var pop: Pop?
    var flip = 0.0, dizzy = 0.0, boarded = 0.0, calm = 0.0
    /// 공중제비 각도: turn(목표)을 빠르게 따라간다. 톡마다 한 바퀴씩 늘어난다
    var angle = 0.0, turn = 0.0
    var rising = false
    private var sparkT = 0.0, sprayT = 0.0

    func pose(riding: Bool) -> CharPose {
        if dizzy > 0 { return .dizzy }
        if flip > 0 { return .cheer }
        if riding { return boarded > 0 || !rising ? .crouch : .cheer }
        return calm > 0 ? .yawn : .none
    }

    mutating func step(_ dt: Double, riding: Bool, rising: Bool) {
        flip = max(0, flip - dt); dizzy = max(0, dizzy - dt); boarded = max(0, boarded - dt); calm = max(0, calm - dt)
        self.rising = rising
        if flip > 0 { angle += (turn - angle) * min(1, dt * 14) } else { angle = 0; turn = 0 }
        if var p = pop { p.life -= dt; pop = p.life > 0 ? p : nil }
        for i in dots.indices {
            dots[i].x += dots[i].vx * dt; dots[i].y += dots[i].vy * dt
            if dots[i].kind != .spark { dots[i].vy += 520 * dt }
            dots[i].life -= dt
        }
        dots.removeAll { $0.life <= 0 }
        guard riding else { return }
        sparkT -= dt; sprayT -= dt
        if rising && sparkT <= 0 { sparkT = 0.08; add(.spark, 1) }
        if sprayT <= 0 { sprayT = 0.14; add(.spray, 1) }
    }

    mutating func react(_ e: WaveEngine.Event) {
        switch e {
        case .board(_, let perfect):
            boarded = 0.35; add(.spray, 6)
            if perfect { pop = Pop(text: "딱!", life: 0.9, color: Color(hex: 0xF06A4A)) }
        case .leave(_, let gain, let pts):
            if gain > 0 { flip = Self.flipTime; angle = 0; turn = 2 * .pi; add(.coin, 5) }
            pop = Pop(text: pts >= 0 ? "+\(pts)" : "\(pts)", life: 0.9, color: pts >= 0 ? Color(hex: 0xE0A800) : Color(hex: 0x1B5E96))
        case .splash:
            dizzy = Self.dizzyTime; add(.spray, 12)
            pop = Pop(text: "풍덩!", life: 0.9, color: Color(hex: 0x1B5E96))
        case .calm: calm = 1.5
        case .streak(let n): pop = Pop(text: "\(n)연속!", life: 0.9, color: Color(hex: 0xE8862A))
        case .fever(let on):
            if on { pop = Pop(text: "피버! ×2", life: 1.2, color: Color(hex: 0xD9480F)); add(.spark, 10) }
        case .flip: turn += 2 * .pi; add(.spark, 3)
        case .land(let n, let combo, let pts):
            pop = Pop(text: (combo >= 2 ? "콤보\(combo) " : "\(n)바퀴 ") + "+\(pts)", life: 0.9, color: Color(hex: 0xE8862A))
            add(.spray, 6)
        case .item(let it): pop = Pop(text: it.icon + " " + it.name, life: 1.2, color: Color(hex: 0x0F7B8A)); add(.spark, 8)
        case .saved: pop = Pop(text: "🛟 살았다!", life: 1.0, color: Color(hex: 0x0F7B8A))
        case .mission(let x): pop = Pop(text: "✓ " + x.title + " +50", life: 1.2, color: Color(hex: 0x2F9E44)); add(.coin, 4)
        case .end: break
        }
    }

    /// 점 n개. 좌표는 발밑 기준 pt
    private mutating func add(_ k: Kind, _ n: Int) {
        guard dots.count < 80 else { return }
        for _ in 0..<n {
            switch k {
            case .spray: dots.append(Dot(x: .random(in: -16...16), y: .random(in: -2...4), vx: .random(in: -150...40),
                                         vy: .random(in: -240 ... -120), life: 0.6, kind: k))
            case .spark: dots.append(Dot(x: .random(in: -24 ... -8), y: .random(in: -60 ... -10), vx: -140,
                                         vy: .random(in: -10...10), life: 0.5, kind: k))
            case .coin: dots.append(Dot(x: .random(in: -6...6), y: -60, vx: .random(in: -70...70),
                                        vy: .random(in: -340 ... -220), life: 0.9, kind: k))
            }
        }
    }
}

// MARK: 그리기

enum WaveDraw {
    /// 한 거래일 = 화면 너비 1/9, 인물은 왼쪽 30%
    struct Geo {
        let w, h, dx, rx, top, bot: CGFloat
        let lo, hi, p: Double
        func x(_ i: Double) -> CGFloat { rx + CGFloat(i - p) * dx }
        func y(_ v: Double) -> CGFloat { bot - CGFloat((v - lo) / max(hi - lo, 1)) * (bot - top) }
    }

    static func sky(_ sunset: Bool) -> LinearGradient {
        LinearGradient(colors: sunset ? [Color(hex: 0xFFB37A), Color(hex: 0xFFE7C2)] : [Color(hex: 0xA9DDF3), Color(hex: 0xEAF7FB)],
                       startPoint: .top, endPoint: .bottom)
    }

    static func pos(_ e: WaveEngine) -> Double { min(Double(e.course.count - 1), e.elapsed / WaveEngine.secondsPerDay) }

    /// 그린 곡선(가운데점 잇는 2차 곡선) 위 값. t = 거래일(소수)
    static func value(_ e: WaveEngine, _ t: Double) -> Double {
        let v = e.course.values, n = v.count
        let i = min(max(Int((t + 0.5).rounded(.down)), 0), n - 1)
        let a = (v[max(i - 1, 0)] + v[i]) / 2, b = (v[i] + v[min(i + 1, n - 1)]) / 2
        let s = min(max(t - (Double(i) - 0.5), 0), 1)
        return (1 - s) * (1 - s) * a + 2 * (1 - s) * s * v[i] + s * s * b
    }
    static func rising(_ e: WaveEngine) -> Bool { let p = pos(e); return value(e, p + 0.15) > value(e, p - 0.15) }

    static func scene(_ ctx: GraphicsContext, _ size: CGSize, _ e: WaveEngine, _ fx: WaveFX, me: MeChar, board: Int, hat: Int) {
        let v = e.course.values, n = v.count
        let g = Geo(w: size.width, h: size.height, dx: size.width / 9, rx: size.width * 0.3,
                    top: size.height * 0.34, bot: size.height * 0.8, lo: v.min() ?? 0, hi: v.max() ?? 1, p: pos(e))
        let sunset = e.fever

        // 파도: 보이는 거래일만
        let i0 = max(0, Int(g.p - Double(g.rx / g.dx)) - 1), i1 = min(n - 1, Int(g.p + Double((g.w - g.rx) / g.dx)) + 2)
        func pt(_ i: Int) -> CGPoint { CGPoint(x: g.x(Double(i)), y: g.y(v[i])) }
        var line = Path()
        line.move(to: CGPoint(x: min(pt(i0).x, -10), y: pt(i0).y))
        line.addLine(to: pt(i0))
        for i in i0..<i1 {
            let a = pt(i), b = pt(i + 1)
            line.addQuadCurve(to: CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2), control: a)
        }
        line.addLine(to: pt(i1))
        let endX = max(pt(i1).x, g.w + 10)
        line.addLine(to: CGPoint(x: endX, y: pt(i1).y))
        var sea = line
        sea.addLine(to: CGPoint(x: endX, y: g.h)); sea.addLine(to: CGPoint(x: -10, y: g.h)); sea.closeSubpath()
        let water = sunset ? [Color(hex: 0xF28C6A), Color(hex: 0x8B4F9E)] : [Color(hex: 0x4FB3E8), Color(hex: 0x1B5E96)]
        ctx.fill(sea, with: .linearGradient(Gradient(colors: water), startPoint: CGPoint(x: 0, y: g.top), endPoint: CGPoint(x: 0, y: g.h)))
        ctx.stroke(line, with: .color(.white.opacity(0.85)), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))

        // 탄 구간: 금빛 줄
        if let b = e.boardedAt, g.p > Double(b) {
            var ride = Path()
            ride.move(to: CGPoint(x: g.x(Double(b)), y: g.y(value(e, Double(b)))))
            var t = Double(b)
            while t < g.p { t = min(g.p, t + 0.25); ride.addLine(to: CGPoint(x: g.x(t), y: g.y(value(e, t)))) }
            ctx.stroke(ride, with: .color(Color(hex: 0xFFD84D)), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        }

        // 아이템: 파도 위에 둥실 (안개 아래라 가까이 와야 보인다)
        for (d, it) in e.items where d >= i0 && d <= i1 {
            let c = CGPoint(x: g.x(Double(d)), y: g.y(v[d]) - 24 + CGFloat(sin(e.elapsed * 3 + Double(d))) * 3)
            ctx.draw(Text(it.icon).font(.system(size: 24)), at: c)
        }

        // 안개: 쉬움은 5거래일, 어려움은 2.5거래일 앞까지 보인다. 등대를 먹으면 잠시 걷힌다
        let fog = g.rx + CGFloat(e.fogClear > 0 ? 12 : e.course.level == .easy ? 5 : 2.5) * g.dx
        ctx.fill(Path(CGRect(x: fog - g.dx, y: 0, width: g.w - fog + g.dx + 10, height: g.h)),
                 with: .linearGradient(Gradient(colors: [.white.opacity(0), .white.opacity(0.95)]),
                                       startPoint: CGPoint(x: fog - g.dx, y: 0), endPoint: CGPoint(x: fog + g.dx * 0.3, y: 0)))

        // 인물
        var foot = CGPoint(x: g.rx, y: g.y(value(e, g.p)) - 6)
        if !e.riding && fx.dizzy <= 0 { foot.y += CGFloat(sin(e.elapsed * 4)) * 1.5 }   // 둥실
        rider(ctx, foot, e, fx, me: me, board: board, hat: hat, g: g)

        // 점 효과 · 글자
        for d in fx.dots {
            let c = CGPoint(x: foot.x + d.x, y: foot.y + d.y), a = min(1, d.life * 2)
            switch d.kind {
            case .spray:
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 2.5, y: c.y - 2.5, width: 5, height: 5)), with: .color(.white.opacity(a)))
            case .spark:
                var s = Path()
                s.move(to: CGPoint(x: c.x, y: c.y - 3)); s.addLine(to: CGPoint(x: c.x + 3, y: c.y))
                s.addLine(to: CGPoint(x: c.x, y: c.y + 3)); s.addLine(to: CGPoint(x: c.x - 3, y: c.y)); s.closeSubpath()
                ctx.fill(s, with: .color(Color(hex: 0xFFD84D).opacity(a)))
            case .coin:
                let r = CGRect(x: c.x - 4.5, y: c.y - 4.5, width: 9, height: 9)
                ctx.fill(Path(ellipseIn: r), with: .color(Color(hex: 0xFFD84D).opacity(a)))
                ctx.stroke(Path(ellipseIn: r), with: .color(Color(hex: 0xE0A800).opacity(a)), lineWidth: 1.5)
            }
        }
        if let p = fx.pop {
            var c = ctx
            c.opacity = min(1, p.life * 3)
            c.draw(Text(p.text).font(.system(size: 24, weight: .heavy, design: .rounded)).foregroundColor(p.color),
                   at: CGPoint(x: foot.x, y: foot.y - 110 - CGFloat(0.9 - p.life) * 30))
        }
    }

    /// 보드 + 포즈 그림 + 모자(연속 3번부터는 왕관). 버퍼 좌표 (x 0...23, 발 = 34줄)를 발밑 기준으로 옮긴다
    private static func rider(_ ctx: GraphicsContext, _ foot: CGPoint, _ e: WaveEngine, _ fx: WaveFX, me: MeChar, board: Int, hat: Int, g: Geo) {
        let dot: CGFloat = 2, pose = fx.pose(riding: e.riding)
        var c = ctx
        c.translateBy(x: foot.x, y: foot.y)
        if fx.dizzy > 0 { c.translateBy(x: 0, y: CGFloat(sin((1 - fx.dizzy / WaveFX.dizzyTime) * .pi)) * 22) }   // 풍덩: 잠겼다 올라옴
        if fx.flip > 0 {
            let k = 1 - fx.flip / WaveFX.flipTime
            c.translateBy(x: 0, y: -CGFloat(sin(k * .pi)) * 34 - 17 * dot)
            c.rotate(by: .radians(fx.angle))
            c.translateBy(x: 0, y: 17 * dot)
        } else {
            let y1 = g.y(value(e, g.p - 0.15)), y2 = g.y(value(e, g.p + 0.15))
            c.rotate(by: .radians(max(-0.4, min(0.4, Double(atan2(y2 - y1, 0.3 * g.dx))))))
        }
        func X(_ bx: Int) -> CGFloat { (CGFloat(bx) - 12) * dot }
        func Y(_ by: Int) -> CGFloat { (CGFloat(by) - CGFloat(MeArt.H)) * dot }
        func cell(_ ch: Character, _ bx: Int, _ by: Int) {
            guard let hex = SurfCloset.palette[ch] else { return }
            c.fill(Path(CGRect(x: X(bx), y: Y(by), width: dot * 2, height: dot * 2)), with: .color(Color(hex: hex)))
        }
        if let img = MeMotion.image(me, CharScene(dir: .down, pose: pose)) {
            let iw = dot * img.size.width, ih = dot * img.size.height
            c.draw(Image(uiImage: img).interpolation(.none), in: CGRect(x: X(0), y: Y(MeArt.H) - ih, width: iw, height: ih))
        }
        let b = SurfCloset.boards[min(max(board, 0), SurfCloset.boards.count - 1)]
        for (r, row) in b.rows.enumerated() { for (i, ch) in row.enumerated() { cell(ch, 2 * (b.x + i), MeArt.H + 2 * r) } }
        for (x, y, ch) in b.deco { cell(ch, 2 * x, MeArt.H + 2 * (y - 11)) }
        let sink = pose.sink
        if e.streak >= 3 {
            for (r, row) in ["y.y.y", "yyyyy"].enumerated() { for (i, ch) in row.enumerated() { cell(ch, 7 + 2 * i, -4 + 2 * r + sink) } }
        } else {
            let h = SurfCloset.hats[min(max(hat, 0), SurfCloset.hats.count - 1)]
            for (r, row) in h.rows.enumerated() { for (i, ch) in row.enumerated() { cell(ch, 2 * (h.x + i), 2 * (h.y + r - 1) + sink) } }
        }
    }
}
