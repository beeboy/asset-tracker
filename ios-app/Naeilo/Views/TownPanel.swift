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
    var height: CGFloat = TownPanel.height     // 우리 사이·꾸미기는 남는 높이까지 늘려 받는다
    var body: some View {
        let special = res.id == TownLook.mayorID || res.id == TownLook.warshID
        Group {
            if tab == 1 && !special { TownUsPanel(res: res) }
            else if tab == 2 && !special { TownDressPanel(res: res, line: $line, look: $look) }
            else { TownHomePanel(res: res, line: $line, ask: $ask) }
        }
        .id(res.id)      // 다른 주민에게 말 걸면 고를 말·꾸미기 칸을 처음부터
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .top)
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
                        Text(done ? String(localized: "한 줄 답 남겼어요 ✓") : String(localized: "대사에 한 줄 답 남기기 ›")).appFont(14, .bold).foregroundStyle(Theme.ink).lineLimit(1)
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
                .background(Theme.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
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
        line = n?.say ?? String(localized: "그렇군요!")
        opts = n?.next ?? TownChat.start()
    }
}

/// 내가 할 말 → 주민 대답 · 다음 후보. 미리 써 둔 작은 표 (시장 이야기 없음)
enum TownChat {
    static let doneKey = String(localized: "다 했어!")
    static let praise = String(localized: "한 줄 남겨 줘서 고마워요! 오늘 할 일 끝, 대단해요!")
    private static let bye = String(localized: "또 올게")
    static func start(_ d: Date = Date()) -> [String] {
        Calendar.current.component(.hour, from: d) < 15 ? [String(localized: "좋은 아침!"), String(localized: "잘 잤어?"), String(localized: "오늘 뭐 해?")] : [String(localized: "오늘 어땠어?"), String(localized: "저녁 먹었어?"), String(localized: "잘 자!")]
    }
    static let tree: [String: (say: String, next: [String])] = [
        String(localized: "좋은 아침!"): (String(localized: "좋은 아침이에요! 오늘도 천천히 가요."), [String(localized: "기분은 어때?"), String(localized: "날씨 좋다"), bye]),
        String(localized: "잘 잤어?"): (String(localized: "푹 잤어요. 꿈에 큰 나무가 나왔어요!"), [String(localized: "무슨 나무?"), String(localized: "좋은 꿈이네"), bye]),
        String(localized: "오늘 뭐 해?"): (String(localized: "동네 한 바퀴 걸을 거예요. 같이 갈래요?"), [String(localized: "좋아!"), String(localized: "다음에"), bye]),
        String(localized: "오늘 어땠어?"): (String(localized: "조용한 하루였어요. 그런 날도 좋죠."), [String(localized: "나도 그래"), String(localized: "기분은 어때?"), String(localized: "잘 자!")]),
        String(localized: "저녁 먹었어?"): (String(localized: "방금 먹었어요. 배불러요!"), [String(localized: "뭐 먹었어?"), String(localized: "부럽다"), String(localized: "잘 자!")]),
        String(localized: "잘 자!"): (String(localized: "잘 자요. 내일 또 봐요!"), [String(localized: "내일 봐"), String(localized: "기분은 어때?"), bye]),
        String(localized: "기분은 어때?"): (String(localized: "음… 맑음이에요. 당신은요?"), [String(localized: "나도 맑음"), String(localized: "조금 흐림"), bye]),
        String(localized: "날씨 좋다"): (String(localized: "그쵸? 이런 날엔 산책이 최고예요."), [String(localized: "좋아!"), String(localized: "기분은 어때?"), bye]),
        String(localized: "무슨 나무?"): (String(localized: "열매가 반짝이는 나무요. 천천히 자라더라고요."), [String(localized: "좋은 꿈이네"), String(localized: "기분은 어때?"), bye]),
        String(localized: "좋은 꿈이네"): (String(localized: "그쵸? 오늘은 좋은 일이 있을 것 같아요."), [String(localized: "기분은 어때?"), String(localized: "날씨 좋다"), bye]),
        String(localized: "좋아!"): (String(localized: "신난다! 천천히 걸어요."), [String(localized: "기분은 어때?"), String(localized: "날씨 좋다"), bye]),
        String(localized: "다음에"): (String(localized: "괜찮아요. 언제든 불러요!"), [String(localized: "기분은 어때?"), String(localized: "날씨 좋다"), bye]),
        String(localized: "나도 그래"): (String(localized: "우리 잘 맞네요!"), [String(localized: "기분은 어때?"), String(localized: "잘 자!"), bye]),
        String(localized: "뭐 먹었어?"): (String(localized: "따끈한 국밥이요. 최고였어요!"), [String(localized: "부럽다"), String(localized: "잘 자!"), bye]),
        String(localized: "부럽다"): (String(localized: "다음엔 같이 먹어요!"), [String(localized: "좋아!"), String(localized: "잘 자!"), bye]),
        String(localized: "내일 봐"): (String(localized: "꼭이에요! 기다릴게요."), [String(localized: "기분은 어때?"), String(localized: "날씨 좋다"), bye]),
        String(localized: "나도 맑음"): (String(localized: "좋다! 맑은 날끼리 하이파이브!"), [String(localized: "날씨 좋다"), String(localized: "오늘 뭐 해?"), bye]),
        String(localized: "조금 흐림"): (String(localized: "그럴 때도 있죠. 여기서 좀 쉬다 가요."), [String(localized: "고마워"), String(localized: "날씨 좋다"), bye]),
        String(localized: "고마워"): (String(localized: "천만에요. 언제든 와요!"), [String(localized: "기분은 어때?"), String(localized: "날씨 좋다"), bye]),
        bye: (String(localized: "네, 언제든 와요!"), []),
        doneKey: (String(localized: "벌써요? 대단해요! 오늘 한 줄 끝!"), [String(localized: "고마워"), String(localized: "기분은 어때?"), bye]),
    ]
}

// MARK: - 우리 사이

/// 사이 단계: 함께한 날 + 나눈 대화 + 남긴 답×2 로 점수 (저장 없이 그때그때 계산)
enum TownBond {
    static let names = [String(localized: "이웃"), String(localized: "친구"), String(localized: "단짝"), String(localized: "절친"), String(localized: "찐친")]
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
        let past = log.last { $0.me != nil }   // 내가 답한 대화만
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("\(TownBond.names[lv - 1]) Lv.\(lv)").appFont(16, .bold).foregroundStyle(Theme.ink)
                Spacer()
                Text(hi.map { String(localized: "\(String(describing: TownBond.names[lv]))까지 \(String(describing: $0 - p))점") } ?? String(localized: "최고 단계예요")).appFont(12).foregroundStyle(Theme.brown)
            }
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.brown.opacity(0.18))
                    Capsule().fill(Theme.brown).frame(width: g.size.width * (hi.map { CGFloat(p - lo) / CGFloat(max(1, $0 - lo)) } ?? 1))
                }
            }
            .frame(height: 8)
            HStack(spacing: 6) {
                cell(String(localized: "함께한 날"), String(localized: "\(String(describing: TownBond.days(n)))일"))
                cell(String(localized: "나눈 대화"), String(localized: "\(String(describing: log.count))번"))
                cell(String(localized: "연속 방문"), String(localized: "\(String(describing: TownBond.streak(res.id)))일"))
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("기억해요").appFont(11, .bold).foregroundStyle(Theme.brown)
                    ForEach(memo(n, log), id: \.self) { t in
                        Text(t).appFont(12).foregroundStyle(Theme.ink).lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Theme.card.opacity(0.7), in: Capsule())
                            .overlay(Capsule().stroke(Theme.brown.opacity(0.5)))
                    }
                }
            }
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("최근 대화").appFont(11, .bold).foregroundStyle(Theme.brown)
                    Text(past.map { String(localized: "나: ") + ($0.me ?? "") } ?? String(localized: "아직 지난 대화가 없어요"))
                        .appFont(13).foregroundStyle(Theme.ink).lineLimit(1)
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
            Text(v).appFont(15, .bold).foregroundStyle(Theme.ink)
            Text(k).appFont(11).foregroundStyle(Theme.brown)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 4)
        .background(Theme.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
    }
    /// 기억 칩: 별명 · 최근 내 답 둘 · 받은 별사탕 · 이사 온 날 (있는 것만)
    private func memo(_ n: TownBook.Note, _ log: [TownLog.Entry]) -> [String] {
        var m: [String] = []
        if let s = n.name { m.append(String(localized: "별명 \(String(describing: s))")) }
        m += log.compactMap(\.me).suffix(2).reversed().map { "“\($0.prefix(12))”" }
        if let g = n.gifts, g > 0 { m.append(String(localized: "별사탕 \(String(describing: g))개 받음")) }
        if !n.moved.isEmpty { m.append(n.moved.dropFirst(5).replacingOccurrences(of: "-", with: ".") + String(localized: " 이사")) }
        return m.isEmpty ? [String(localized: "말을 걸면 하나씩 기억해요")] : m
    }
}

// MARK: - 꾸미기

/// 꾸밀 거리: 모습(부품 조합, 공짜) · 모자 · 모자 색 · 소품 · 동네 배경. 가격은 별사탕, 산 것은 모든 주민이 같이 쓴다
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
    @State private var kind = 4          // 4 모습 (맨 앞에 보인다)
    @State private var bg0: Int? = nil       // 고르기 전 배경 (되돌리기)
    @State private var name = false
    private static let kinds = [String(localized: "모자"), String(localized: "색"), String(localized: "소품"), String(localized: "동네"), String(localized: "모습")]
    private static let tabs = [4, 0, 1, 2, 3]
    private static let hatPrice = [0, 0, 0, 3, 1, 2]
    private static let colorNames = [String(localized: "노랑"), String(localized: "빨강"), String(localized: "파랑"), String(localized: "초록"), String(localized: "보라"), String(localized: "하양"), String(localized: "검정")]
    private var book: TownBook { TownBook.shared }
    var body: some View {
        let n = look ?? book.note(res.id)
        let lv = TownBond.level(TownBond.points(res.id))
        VStack(spacing: 6) {
            HStack(spacing: 4) {
                ForEach(Self.tabs, id: \.self) { k in
                    Button { kind = k } label: {
                        Text(Self.kinds[k]).appFont(13, .bold).foregroundStyle(kind == k ? .white : Theme.brown)
                            .padding(.horizontal, 10).frame(minHeight: 30)
                            .background(kind == k ? Theme.brown : .clear, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
                Button("이름") { name = true }.appFont(12, .bold).foregroundStyle(Theme.brown).frame(minHeight: 32)
                Text("⭐ \(book.candy)").appFont(13, .bold).foregroundStyle(Theme.ink)
            }
            if kind == 4 { ScrollView(showsIndicators: false) { parts(n) } }   // 작은 화면에선 칸 안에서 밀어 본다
            else {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
                    ForEach(items(kind, lv)) { it in cell(it, n) }
                }
            }
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                Button { revert(); line = String(localized: "원래대로 돌아왔어요.") } label: {
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

    /// 모습: 머리 모양 · 머리색 · 피부 · 윗옷 · 아래옷을 ◀ ▶ 로 넘겨 고른다 (유명인은 머리를 따로 그려 피부·옷만)
    private static let partRows: [(key: String, label: String)] = [("hair", String(localized: "머리 모양")), ("color", String(localized: "머리색")), ("skin", String(localized: "피부")), ("top", String(localized: "윗옷")), ("bottom", String(localized: "아래옷"))]
    private func parts(_ n: TownBook.Note) -> some View {
        let star = TownStar.find(res.id) != nil
        let me = TownLook.char(res.id, n)
        // 두 줄로: 다섯 칸 + 처음 모습으로
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 2), spacing: 8) {
            ForEach(Self.partRows.filter { !star || ($0.key != "hair" && $0.key != "color") }, id: \.key) { r in
                let cnt = r.key == "skin" ? MeColor.skin.count : MeGroup(rawValue: r.key)?.count ?? 1
                let cur = me.parts[r.key] ?? 1
                let nm = r.key == "skin" ? MeColor.skinNames[max(0, min(cur, cnt) - 1)] : MeGroup(rawValue: r.key)?.name(cur) ?? ""
                HStack(spacing: 0) {
                    Button { step(r.key, cur, cnt, -1, n) } label: { Text("◀").appFont(12, .bold).frame(width: 36, height: 40) }
                        .buttonStyle(.plain).foregroundStyle(Theme.brown).accessibilityLabel("\(r.label) 앞으로")
                    VStack(spacing: 0) {
                        Text(r.label).appFont(10, .bold).foregroundStyle(Theme.brown)
                        Text(nm).appFont(12, .bold).foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity)
                    Button { step(r.key, cur, cnt, 1, n) } label: { Text("▶").appFont(12, .bold).frame(width: 36, height: 40) }
                        .buttonStyle(.plain).foregroundStyle(Theme.brown).accessibilityLabel("\(r.label) 다음")
                }
                .frame(height: 40)
                .background(Theme.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
            }
            Button { var m = n; m.parts = nil; look = m; line = String(localized: "처음 모습이에요.") } label: {
                Text("처음 모습으로").appFont(12, .bold).foregroundStyle(Theme.brown.opacity(n.parts == nil ? 0.4 : 1))
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.brown.opacity(0.5), lineWidth: 1))
            }
            .buttonStyle(.plain).disabled(n.parts == nil)
        }
    }
    private func step(_ key: String, _ cur: Int, _ cnt: Int, _ d: Int, _ n: TownBook.Note) {
        guard cnt > 0 else { return }
        var m = n, p = n.parts ?? [:]
        p[key] = (cur - 1 + d + cnt) % cnt + 1
        m.parts = p; look = m
        line = String(localized: "이런 모습 어때요?")
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
                        Text(it.name).appFont(13, .bold).foregroundStyle(it.lock ? Theme.brown.opacity(0.5) : Theme.ink).lineLimit(1).minimumScaleFactor(0.7)
                    }
                } else {
                    Text(it.name).appFont(13, .bold).foregroundStyle(it.lock ? Theme.brown.opacity(0.5) : Theme.ink).lineLimit(1).minimumScaleFactor(0.7)
                }
                Text(it.lock ? String(localized: "절친 때 열림") : wearing ? String(localized: "입는 중") : has(it) ? (it.kind == 1 ? it.name : String(localized: "보유")) : "⭐ \(it.price)")
                    .appFont(10, .bold).foregroundStyle(wearing ? Theme.teal : Theme.brown.opacity(it.lock ? 0.5 : 0.9))
            }
            .frame(maxWidth: .infinity, minHeight: 40)
            .background(it.lock ? Color.clear : Theme.card.opacity(wearing ? 0.95 : 0.6), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8)
                .stroke(wearing ? Theme.teal : Theme.brown.opacity(0.5), style: StrokeStyle(lineWidth: wearing ? 2 : 1, dash: it.lock ? [4, 3] : [])))
        }
        .buttonStyle(.plain)
    }
    private func pick(_ it: TownItem, _ n: TownBook.Note) {
        if it.lock { line = String(localized: "\(String(describing: it.name))은 절친이 되면 써 볼게요!"); return }
        var m = n
        switch it.kind {
        case 0: m.hat = it.i
        case 1: if m.hat == 0 { m.hat = 1 }; m.tint = it.i
        case 2: m.prop = it.i == 0 ? nil : it.i
        default: if bg0 == nil { bg0 = bg }; bg = it.i
        }
        if it.kind != 3 { look = m }
        line = it.i == 0 && it.kind == 0 ? String(localized: "모자 없이도 괜찮죠?") : it.i == 0 && it.kind == 2 ? String(localized: "소품 없이 가볍게!") : String(localized: "\(String(describing: it.name)) 어때요? 잘 어울려요!")
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
        guard cost == 0 || book.spend(cost) else { line = String(localized: "별사탕이 \(String(describing: cost - book.candy))개 모자라요. 답을 남기면 생겨요!"); return }
        if !need.isEmpty { ownedRaw = (owned.union(need.map(\.id))).sorted().joined(separator: ",") }
        book.set(res.id, n)
        look = nil; bg0 = nil
        line = cost > 0 ? String(localized: "고마워요! 별사탕 \(String(describing: cost))개로 샀어요. 마음에 들어요!") : String(localized: "고마워요! 마음에 들어요!")
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
