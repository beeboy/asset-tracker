import SwiftUI

/// 동네 대사창 아래 칸: 기본(오늘의 한 줄 · 내가 할 말 고르기) / 우리 사이 / 꾸미기.
/// 글은 미리 써 둔 작은 표에서만 고른다 (새로 받는 데이터 없음). 한 화면에 들어가게 높이를 고정
struct TownPanel: View {
    let res: TownLook.Resident
    @Binding var tab: Int                 // 0 기본, 1 우리 사이, 2 꾸미기
    @Binding var line: String?            // 대사창에 띄울 주민 대답
    @Binding var look: TownBook.Note?     // 꾸미기 미리 보기
    @Binding var ask: Int                 // 늘면 대사창 답장 칸을 연다
    static let height: CGFloat = 196
    var body: some View {
        let special = res.id == TownLook.mayorID || res.id == TownLook.warshID
        Group {
            if tab == 1 && !special { TownUsPanel(res: res) }
            else if tab == 2 && !special { TownDressPanel(res: res, line: $line, look: $look) }
            else { TownHomePanel(res: res, line: $line, ask: $ask) }
        }
        .id(res.id)      // 다른 주민에게 말 걸면 고를 말·꾸미기 칸을 처음부터
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: Self.height, maxHeight: Self.height, alignment: .top)
        .background(Theme.cream, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.brown, lineWidth: 2))
    }
}

// MARK: - 기본: 오늘의 한 줄 · 내가 할 말 고르기

private struct TownHomePanel: View {
    let res: TownLook.Resident
    @Binding var line: String?
    @Binding var ask: Int
    @State private var opts: [String] = []
    @State private var saidDone = false
    private var log: TownLog { TownLog.shared }
    var body: some View {
        let done = log.log(res.id).last.map { $0.day == TownLog.day && $0.me != nil } ?? false
        VStack(alignment: .leading, spacing: 8) {
            Button { if !done { ask += 1 } } label: {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Text("오늘의 한 줄").appFont(12, .bold).foregroundStyle(Theme.brown)
                        Text(done ? "한 줄 답 남겼어요 ✓" : "대사에 한 줄 답 남기기 ›").appFont(14, .bold).foregroundStyle(Theme.inkFixed).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(done ? "1/1" : "0/1").appFont(13, .bold).foregroundStyle(Theme.brown)
                    }
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Theme.brown.opacity(0.18))
                            Capsule().fill(Theme.brown).frame(width: done ? g.size.width : 0)
                        }
                    }
                    .frame(height: 6)
                }
                .padding(10)
                .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.brown.opacity(0.5)))
            }
            .buttonStyle(.plain)
            Text("내가 할 말 고르기").appFont(12, .bold).foregroundStyle(Theme.brown).padding(.top, 4)
            HStack(spacing: 6) {
                ForEach(shownOpts(done), id: \.self) { o in
                    Button { pick(o) } label: {
                        Text(o).appFont(14, .bold).foregroundStyle(o == TownChat.doneKey ? Color(red: 1, green: 0.85, blue: 0.4) : .white)
                            .lineLimit(1).minimumScaleFactor(0.75).padding(.horizontal, 4)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color(red: 0.10, green: 0.11, blue: 0.20), in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .onAppear { if opts.isEmpty { opts = TownChat.start() } }
        .onChange(of: done) { _, v in if v { line = TownChat.praise } }
    }
    /// 오늘의 한 줄을 끝냈으면 "다 했어!"를 후보 맨 앞에 (한 번 말하면 빠짐)
    private func shownOpts(_ done: Bool) -> [String] {
        let o = opts.isEmpty ? TownChat.start() : opts
        return done && !saidDone ? [TownChat.doneKey] + o.prefix(2) : Array(o.prefix(3))
    }
    private func pick(_ o: String) {
        if o == TownChat.doneKey { saidDone = true }
        let n = TownChat.tree[o]
        line = n?.say ?? "그렇군요!"
        opts = n?.next ?? TownChat.start()
    }
}

/// 내가 할 말 → 주민 대답 · 다음 후보. 미리 써 둔 작은 표 (시장 이야기 없음)
enum TownChat {
    static let doneKey = "다 했어!"
    static let praise = "한 줄 남겨 줘서 고마워요! 오늘 할 일 끝, 대단해요!"
    private static let bye = "또 올게"
    static func start(_ d: Date = Date()) -> [String] {
        Calendar.current.component(.hour, from: d) < 15 ? ["좋은 아침!", "잘 잤어?", "오늘 뭐 해?"] : ["오늘 어땠어?", "저녁 먹었어?", "잘 자!"]
    }
    static let tree: [String: (say: String, next: [String])] = [
        "좋은 아침!": ("좋은 아침이에요! 오늘도 천천히 가요.", ["기분은 어때?", "날씨 좋다", bye]),
        "잘 잤어?": ("푹 잤어요. 꿈에 큰 나무가 나왔어요!", ["무슨 나무?", "좋은 꿈이네", bye]),
        "오늘 뭐 해?": ("동네 한 바퀴 걸을 거예요. 같이 갈래요?", ["좋아!", "다음에", bye]),
        "오늘 어땠어?": ("조용한 하루였어요. 그런 날도 좋죠.", ["나도 그래", "기분은 어때?", "잘 자!"]),
        "저녁 먹었어?": ("방금 먹었어요. 배불러요!", ["뭐 먹었어?", "부럽다", "잘 자!"]),
        "잘 자!": ("잘 자요. 내일 또 봐요!", ["내일 봐", "기분은 어때?", bye]),
        "기분은 어때?": ("음… 맑음이에요. 당신은요?", ["나도 맑음", "조금 흐림", bye]),
        "날씨 좋다": ("그쵸? 이런 날엔 산책이 최고예요.", ["좋아!", "기분은 어때?", bye]),
        "무슨 나무?": ("열매가 반짝이는 나무요. 천천히 자라더라고요.", ["좋은 꿈이네", "기분은 어때?", bye]),
        "좋은 꿈이네": ("그쵸? 오늘은 좋은 일이 있을 것 같아요.", ["기분은 어때?", "날씨 좋다", bye]),
        "좋아!": ("신난다! 천천히 걸어요.", ["기분은 어때?", "날씨 좋다", bye]),
        "다음에": ("괜찮아요. 언제든 불러요!", ["기분은 어때?", "날씨 좋다", bye]),
        "나도 그래": ("우리 잘 맞네요!", ["기분은 어때?", "잘 자!", bye]),
        "뭐 먹었어?": ("따끈한 국밥이요. 최고였어요!", ["부럽다", "잘 자!", bye]),
        "부럽다": ("다음엔 같이 먹어요!", ["좋아!", "잘 자!", bye]),
        "내일 봐": ("꼭이에요! 기다릴게요.", ["기분은 어때?", "날씨 좋다", bye]),
        "나도 맑음": ("좋다! 맑은 날끼리 하이파이브!", ["날씨 좋다", "오늘 뭐 해?", bye]),
        "조금 흐림": ("그럴 때도 있죠. 여기서 좀 쉬다 가요.", ["고마워", "날씨 좋다", bye]),
        "고마워": ("천만에요. 언제든 와요!", ["기분은 어때?", "날씨 좋다", bye]),
        bye: ("네, 언제든 와요!", []),
        doneKey: ("벌써요? 대단해요! 오늘 한 줄 끝!", ["고마워", "기분은 어때?", bye]),
    ]
}

// MARK: - 우리 사이

/// 사이 단계: 함께한 날 + 나눈 대화 + 남긴 답×2 로 점수 (저장 없이 그때그때 계산)
enum TownBond {
    static let names = ["이웃", "친구", "단짝", "절친", "찐친"]
    static let need = [0, 10, 30, 60, 100]
    static let bestLv = 4       // 절친 (꾸미기 잠긴 칸이 열림)
    static func days(_ n: TownBook.Note) -> Int {
        n.moved.isEmpty ? 0 : TownLog.age(n.moved) - TownLog.age(n.left ?? TownLog.day) + 1
    }
    static func points(_ id: String) -> Int {
        let l = TownLog.shared.log(id)
        return days(TownBook.shared.note(id)) + l.count + l.filter { $0.me != nil }.count * 2
    }
    static func level(_ p: Int) -> Int { (need.lastIndex { p >= $0 } ?? 0) + 1 }
    /// 오늘부터 거꾸로 하루도 안 빠지고 말 건 날 수
    static func streak(_ id: String) -> Int {
        let days = Set(TownLog.shared.log(id).map(\.day))
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        var d = Date(), n = 0
        while days.contains(f.string(from: d)) { n += 1; d = Calendar.current.date(byAdding: .day, value: -1, to: d) ?? d }
        return n
    }
}

private struct TownUsPanel: View {
    let res: TownLook.Resident
    @State private var all = false
    var body: some View {
        let n = TownBook.shared.note(res.id)
        let log = TownLog.shared.log(res.id)
        let p = TownBond.points(res.id)
        let lv = TownBond.level(p)
        let lo = TownBond.need[lv - 1], hi = lv < TownBond.need.count ? TownBond.need[lv] : nil
        let past = log.last { $0.day != TownLog.day }
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("\(TownBond.names[lv - 1]) Lv.\(lv)").appFont(16, .bold).foregroundStyle(Theme.inkFixed)
                Spacer()
                Text(hi.map { "\(TownBond.names[lv])까지 \($0 - p)점" } ?? "최고 단계예요").appFont(12).foregroundStyle(Theme.brown)
            }
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.brown.opacity(0.18))
                    Capsule().fill(Theme.brown).frame(width: g.size.width * (hi.map { CGFloat(p - lo) / CGFloat(max(1, $0 - lo)) } ?? 1))
                }
            }
            .frame(height: 8)
            HStack(spacing: 6) {
                cell("함께한 날", "\(TownBond.days(n))일")
                cell("나눈 대화", "\(log.count)번")
                cell("연속 방문", "\(TownBond.streak(res.id))일")
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("기억해요").appFont(11, .bold).foregroundStyle(Theme.brown)
                    ForEach(memo(n, log), id: \.self) { t in
                        Text(t).appFont(12).foregroundStyle(Theme.inkFixed).lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Color.white.opacity(0.7), in: Capsule())
                            .overlay(Capsule().stroke(Theme.brown.opacity(0.5)))
                    }
                }
            }
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("최근 대화").appFont(11, .bold).foregroundStyle(Theme.brown)
                    Text(past.map { $0.me.map { "나: " + $0 } ?? $0.say } ?? "아직 지난 대화가 없어요")
                        .appFont(13).foregroundStyle(Theme.inkFixed).lineLimit(1)
                }
                Spacer(minLength: 0)
                Button("전체 보기") { all = true }.appFont(13, .bold).foregroundStyle(Theme.brown)
                    .frame(minWidth: 64, minHeight: 44)
            }
        }
        .sheet(isPresented: $all) {
            NavigationStack {
                TownPast(id: res.id).padding(16).frame(maxHeight: .infinity, alignment: .top)
                    .navigationTitle(TownLook.shown(res, n)).navigationBarTitleDisplayMode(.inline)
            }
            .presentationDetents([.medium, .large])
        }
    }
    private func cell(_ k: String, _ v: String) -> some View {
        VStack(spacing: 2) {
            Text(v).appFont(15, .bold).foregroundStyle(Theme.inkFixed)
            Text(k).appFont(11).foregroundStyle(Theme.brown)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 4)
        .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
    }
    /// 기억 칩: 별명 · 최근 내 답 둘 · 받은 별사탕 · 이사 온 날 (있는 것만)
    private func memo(_ n: TownBook.Note, _ log: [TownLog.Entry]) -> [String] {
        var m: [String] = []
        if let s = n.name { m.append("별명 \(s)") }
        m += log.compactMap(\.me).suffix(2).reversed().map { "“\($0.prefix(12))”" }
        if let g = n.gifts, g > 0 { m.append("별사탕 \(g)개 받음") }
        if !n.moved.isEmpty { m.append(n.moved.dropFirst(5).replacingOccurrences(of: "-", with: ".") + " 이사") }
        return m.isEmpty ? ["말을 걸면 하나씩 기억해요"] : m
    }
}

// MARK: - 꾸미기

/// 꾸밀 거리: 모자 · 모자 색 · 소품(아직 준비 중) · 동네 배경. 가격은 별사탕, 산 것은 모든 주민이 같이 쓴다
private struct TownItem: Identifiable {
    let kind: Int, i: Int, name: String, price: Int, lock: Bool
    var id: String { "\(kind).\(i)" }
}

private struct TownDressPanel: View {
    let res: TownLook.Resident
    @Binding var line: String?
    @Binding var look: TownBook.Note?
    @AppStorage("town.bg") private var bg = 0
    @AppStorage("town.owned") private var ownedRaw = ""
    @State private var kind = 0
    @State private var bg0: Int? = nil       // 고르기 전 배경 (되돌리기)
    @State private var name = false
    private static let kinds = ["모자", "색", "소품", "동네"]
    private static let hatPrice = [0, 0, 0, 3, 1, 2]
    private static let colorNames = ["노랑", "빨강", "파랑", "초록", "보라", "하양", "검정"]
    private var book: TownBook { TownBook.shared }
    var body: some View {
        let n = look ?? book.note(res.id)
        let lv = TownBond.level(TownBond.points(res.id))
        VStack(spacing: 6) {
            HStack(spacing: 4) {
                ForEach(0..<Self.kinds.count, id: \.self) { k in
                    Button { kind = k } label: {
                        Text(Self.kinds[k]).appFont(13, .bold).foregroundStyle(kind == k ? .white : Theme.brown)
                            .padding(.horizontal, 10).frame(minHeight: 30)
                            .background(kind == k ? Theme.brown : .clear, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
                Button("이름") { name = true }.appFont(12, .bold).foregroundStyle(Theme.brown).frame(minHeight: 32)
                Text("⭐ \(book.candy)").appFont(13, .bold).foregroundStyle(Theme.inkFixed)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
                ForEach(items(kind, lv)) { it in cell(it, n) }
            }
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                Button { revert(); line = "원래대로 돌아왔어요." } label: {
                    Text("되돌리기").appFont(14, .bold).foregroundStyle(Theme.brown)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.brown, lineWidth: 2))
                }
                .buttonStyle(.plain)
                Button { wear(n) } label: {
                    Text("이대로 입기").appFont(14, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Theme.brown, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .onDisappear { revert() }      // 입지 않고 나가면 미리 보기는 버린다
        .sheet(isPresented: $name) { TownDress(res: res) }
    }

    private var owned: Set<String> { Set(ownedRaw.split(separator: ",").map(String.init)) }
    private func items(_ k: Int, _ lv: Int) -> [TownItem] {
        switch k {
        case 0: return TownFace.hats.enumerated().map { TownItem(kind: 0, i: $0, name: $1, price: Self.hatPrice[min($0, Self.hatPrice.count - 1)], lock: $1 == "왕관" && lv < TownBond.bestLv) }
        case 1: return TownFace.tints.indices.prefix(6).map { TownItem(kind: 1, i: $0, name: Self.colorNames[min($0, Self.colorNames.count - 1)], price: $0 < 3 ? 0 : 1, lock: false) }
        case 2: return TownFace.propNames.enumerated().map { TownItem(kind: 2, i: $0, name: $1, price: $0, lock: false) }   // 안경 1 · 목도리 2 · 풍선 3
        default: return TownGround.names.enumerated().map { TownItem(kind: 3, i: $0, name: $1, price: 0, lock: false) }
        }
    }
    private func has(_ it: TownItem) -> Bool { it.price == 0 || owned.contains(it.id) }
    private func on(_ it: TownItem, _ n: TownBook.Note) -> Bool {
        switch it.kind { case 0: n.hat == it.i; case 1: n.hat > 0 && n.tint == it.i; case 2: (n.prop ?? 0) == it.i; case 3: bg == it.i; default: false }
    }
    private func cell(_ it: TownItem, _ n: TownBook.Note) -> some View {
        let wearing = on(it, n)
        return Button { pick(it, n) } label: {
            VStack(spacing: 2) {
                if it.kind == 1 {
                    let c = TownFace.tints[it.i]
                    Circle().fill(Color(red: Double(c >> 16 & 0xFF) / 255, green: Double(c >> 8 & 0xFF) / 255, blue: Double(c & 0xFF) / 255))
                        .frame(width: 16, height: 16).overlay(Circle().stroke(Color.black.opacity(0.2)))
                } else if (it.kind == 0 || it.kind == 2) && it.i > 0 {
                    HStack(spacing: 4) {
                        TownItemIcon(px: it.kind == 0 ? TownFace.pixels(it.i, TownFace.tints[0]) : TownFace.props(it.i) + TownFace.thin(it.i))
                            .frame(width: 18, height: 18).opacity(it.lock ? 0.4 : 1)
                        Text(it.name).appFont(13, .bold).foregroundStyle(it.lock ? Theme.brown.opacity(0.5) : Theme.inkFixed).lineLimit(1).minimumScaleFactor(0.7)
                    }
                } else {
                    Text(it.name).appFont(13, .bold).foregroundStyle(it.lock ? Theme.brown.opacity(0.5) : Theme.inkFixed).lineLimit(1).minimumScaleFactor(0.7)
                }
                Text(it.lock ? "절친 때 열림" : wearing ? "입는 중" : has(it) ? (it.kind == 1 ? it.name : "보유") : "⭐ \(it.price)")
                    .appFont(10, .bold).foregroundStyle(wearing ? Theme.teal : Theme.brown.opacity(it.lock ? 0.5 : 0.9))
            }
            .frame(maxWidth: .infinity, minHeight: 40)
            .background(it.lock ? Color.clear : Color.white.opacity(wearing ? 0.95 : 0.6), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8)
                .stroke(wearing ? Theme.teal : Theme.brown.opacity(0.5), style: StrokeStyle(lineWidth: wearing ? 2 : 1, dash: it.lock ? [4, 3] : [])))
        }
        .buttonStyle(.plain)
    }
    private func pick(_ it: TownItem, _ n: TownBook.Note) {
        if it.lock { line = "\(it.name)은 절친이 되면 써 볼게요!"; return }
        var m = n
        switch it.kind {
        case 0: m.hat = it.i
        case 1: if m.hat == 0 { m.hat = 1 }; m.tint = it.i
        case 2: m.prop = it.i == 0 ? nil : it.i
        default: if bg0 == nil { bg0 = bg }; bg = it.i
        }
        if it.kind != 3 { look = m }
        line = it.i == 0 && it.kind == 0 ? "모자 없이도 괜찮죠?" : it.i == 0 && it.kind == 2 ? "소품 없이 가볍게!" : "\(it.name) 어때요? 잘 어울려요!"
    }
    private func revert() {
        look = nil
        if let b = bg0 { bg = b; bg0 = nil }
    }
    /// 이대로 입기: 안 산 것은 별사탕으로 사고 수첩에 적는다
    private func wear(_ n: TownBook.Note) {
        let lv = TownBond.level(TownBond.points(res.id))
        let all = (0..<4).flatMap { items($0, lv) }
        let need = all.filter { on($0, n) && !has($0) && !$0.lock }
        let cost = need.reduce(0) { $0 + $1.price }
        guard cost == 0 || book.spend(cost) else { line = "별사탕이 \(cost - book.candy)개 모자라요. 답을 남기면 생겨요!"; return }
        if !need.isEmpty { ownedRaw = (owned.union(need.map(\.id))).sorted().joined(separator: ",") }
        book.set(res.id, n)
        look = nil; bg0 = nil
        line = cost > 0 ? "고마워요! 별사탕 \(cost)개로 샀어요. 마음에 들어요!" : "고마워요! 마음에 들어요!"
    }
}

/// 꾸미기 칸의 작은 그림: 모자·소품 도트를 칸 크기에 맞춰 그린다 (그림 파일 없이)
private struct TownItemIcon: View {
    let px: [(Int, Int, Int, Int, UInt32)]
    var body: some View {
        Canvas { ctx, size in
            guard !px.isEmpty else { return }
            let x0 = px.map(\.0).min()!, y0 = px.map(\.1).min()!
            let w = px.map { $0.0 + $0.2 }.max()! - x0, h = px.map { $0.1 + $0.3 }.max()! - y0
            let u = min(size.width / CGFloat(w), size.height / CGFloat(h))
            let ox = (size.width - CGFloat(w) * u) / 2, oy = (size.height - CGFloat(h) * u) / 2
            for r in px {
                let v = r.4
                ctx.fill(Path(CGRect(x: ox + CGFloat(r.0 - x0) * u, y: oy + CGFloat(r.1 - y0) * u, width: CGFloat(r.2) * u, height: CGFloat(r.3) * u)),
                         with: .color(Color(red: Double(v >> 16 & 0xFF) / 255, green: Double(v >> 8 & 0xFF) / 255, blue: Double(v & 0xFF) / 255)))
            }
        }
    }
}
