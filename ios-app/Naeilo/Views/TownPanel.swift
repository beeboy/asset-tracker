import SwiftUI

/// 동네 대사창 아래 칸: 우리 사이(기본) / 꾸미기. 이장·워시는 아래 칸 없이 대사창만.
/// 대사는 앱 안 글에서만 고른다 (새로 받는 데이터 없음). 한 화면에 들어가게 높이를 고정
struct TownPanel: View {
    let res: TownLook.Resident
    @Binding var tab: Int                 // 1 우리 사이, 2 꾸미기
    @Binding var line: String?            // 대사창에 띄울 주민 대답
    @Binding var look: TownBook.Note?     // 꾸미기 미리 보기
    @Binding var ask: Int                 // 늘면 대사창 답장 칸을 연다
    static let height: CGFloat = 196
    var height: CGFloat = TownPanel.height     // 우리 사이·꾸미기는 남는 높이까지 늘려 받는다
    var body: some View {
        Group {
            if tab == 2 { TownDressPanel(res: res, line: $line, look: $look) }
            else { TownUsPanel(res: res) }
        }
        .id(res.id)      // 다른 주민에게 말 걸면 꾸미기 칸을 처음부터
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .top)
        .background(Theme.cream, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.brown, lineWidth: 2))
    }
}

// MARK: - 대사

/// 동네 대사: 대사창을 열고 누를 때마다, 내 캐릭터가 곁에 갈 때마다 다음 한마디.
/// 갈래(오늘 박스 숫자 · 동네 소식 · 기본 캐릭터 대사 · 외전 · 1권 · 농담 · 속담 · 속마음 · 엉뚱한 말 · 경제 용어)를 돌려 쓰고,
/// 동네 전체에서 방금 나온 세 갈래는 건너뛴다 (한 차례에 누구도 같은 꼴을 잇지 않게).
/// 갈래마다 차례표를 동네가 함께 써서 한 바퀴 돌 때까지 같은 말이 다시 나오지 않는다 (UserDefaults 한 칸)
/// 동네 신문: 중계(/heads)가 하루 한 번 모은 전체 상위 기사 제목 (5일치, 모두 같은 값)
enum TownNews {
    private static let url = URL(string: "https://asset-ai.drinker.workers.dev/heads")!
    private struct File: Decodable { struct Item: Decodable { let d: String; let t: String }; let at: String; let h: [Item] }
    /// 제목 (새것 먼저)
    private(set) static var heads: [String] = UserDefaults.standard.stringArray(forKey: "townNews.h") ?? []
    private static var said: [String] = UserDefaults.standard.stringArray(forKey: "townNews.said") ?? []
    /// 6시간에 한 번만 묻는다 (한국어 앱만)
    static func fetch() async {
        let d = UserDefaults.standard, now = Date().timeIntervalSince1970
        guard !AppModel.en, now - d.double(forKey: "townNews.at") > 6 * 3600 else { return }
        guard let got = try? await URLSession.shared.data(from: url), (got.1 as? HTTPURLResponse)?.statusCode == 200,
              let f = try? JSONDecoder().decode(File.self, from: got.0) else { return }
        d.set(now, forKey: "townNews.at")
        guard !f.at.isEmpty else { return }
        heads = f.h.map(\.t)
        d.set(heads, forKey: "townNews.h")
        said.removeAll { l in !heads.contains { l.contains($0) } }   // 5일 지나 빠진 기사는 들은 목록에서도 지운다
        d.set(said, forKey: "townNews.said")
    }
    /// 한 번 말한 기사는 다시 말하지 않는다 (5일 지나 빠진 제목은 목록에서도 빠진다)
    static func heard(_ line: String) -> Bool { said.contains(line) }
    static func hear(_ line: String) {
        said.append(line); if said.count > 600 { said.removeFirst(said.count - 600) }
        UserDefaults.standard.set(said, forKey: "townNews.said")
    }
}

enum TownSay {
    enum Kind: String { case num, town, hold, fx, rate, term, base, side, vol1, joke, saw, secret, odd, news }
    static let praise = String(localized: "한 줄 남겨 줘서 고마워요! 오늘 할 일 끝, 대단해요!")
    /// 지금 동네에 선 이들 (TownView 가 채운다): 속마음·이장 소식에 이웃 이름과 숫자를 넣는다
    static var folk: [String: TownLook.Resident] = [:]
    private static var recent: [Kind] = []
    private static var pos: [String: Int] = (UserDefaults.standard.dictionary(forKey: "townSay.pos") as? [String: Int]) ?? [:]

    /// 대사창 다음 한마디
    static func next(_ res: TownLook.Resident, _ n: TownBook.Note? = nil) -> String {
        say(res, kinds(res)).text
    }
    /// 내 캐릭터가 곁에 가면 주고받는 네 마디: 주민 · 나 · 주민 · 나
    static func duet(_ res: TownLook.Resident) -> [(me: Bool, text: String)] {
        let talk = kinds(res).filter { $0 != .fx }
        let a = say(res, talk), b = say(res, talk)
        return [(false, a.text), (true, reply(a.kind)), (false, b.text), (true, reply(b.kind))]
    }
    /// 주민끼리 붙어 섰을 때 네 마디: 가 · 나 · 가 · 나 (둘 다 동네 공용 차례표에서, 앞 갈래와 겹치지 않게)
    static func pair(_ a: TownLook.Resident, _ b: TownLook.Resident) -> [(who: String, text: String)] {
        let ka = kinds(a).filter { $0 != .fx }, kb = kinds(b).filter { $0 != .fx }
        return [(a.id, say(a, ka).text), (b.id, say(b, kb).text), (a.id, say(a, ka).text), (b.id, say(b, kb).text)]
    }
    /// 이장 하루 첫마디: 동네 전체 이야기
    static func mayorOpen() -> String { say(TownLook.Resident(id: TownLook.mayorID, rows: []), [.town]).text }

    // MARK: 고르기

    private static func kinds(_ res: TownLook.Resident) -> [Kind] {
        if AppModel.en { return res.id == TownLook.mayorID ? [.town, .hold, .fx] : res.rows.isEmpty ? [.base] : [.num, .base] }
        if res.id == TownLook.mayorID { return [.town, .hold, .fx, .news, .joke, .saw, .secret] }
        if res.id == TownLook.warshID { return [.term, .rate, .news, .town, .saw, .secret, .joke] }
        if res.old != nil { return [.secret, .odd, .saw, .joke, .side, .vol1, .base] }
        return [.num, .news, .base, .side, .vol1, .joke, .saw, .secret, .odd]
    }
    /// 방금 나온 세 갈래를 빼고 하나를 골라 그 갈래의 다음 줄
    private static func say(_ res: TownLook.Resident, _ ks: [Kind]) -> (kind: Kind, text: String) {
        let open = ks.filter { !pool(res, $0).isEmpty }
        let fresh = open.filter { !recent.suffix(3).contains($0) }
        guard let k = (fresh.isEmpty ? open : fresh).randomElement() else { return (.base, String(localized: "그렇군요!")) }
        recent.append(k); if recent.count > 6 { recent.removeFirst() }
        let list = pool(res, k), text = fill(turn(key(res, k), list.count).map { list[$0] } ?? list[0], res)
        if k == .news { TownNews.hear(text) }
        return (k, text)
    }
    /// 차례표 이름: 숫자는 주민마다, 기본 캐릭터는 그 인물마다, 나머지는 동네가 함께
    private static func key(_ res: TownLook.Resident, _ k: Kind) -> String {
        switch k {
        case .num: return "num." + res.id
        case .base: return "base." + type(res.id)
        default: return k.rawValue
        }
    }
    /// 차례표 한 칸 넘기기: 시작 칸과 보폭을 이름으로 정해 섞인 순서로 한 바퀴 돈다
    private static func turn(_ key: String, _ n: Int) -> Int? {
        guard n > 0 else { return nil }
        let p = pos[key] ?? 0
        pos[key] = p + 1
        UserDefaults.standard.set(pos, forKey: "townSay.pos")
        let step = [7, 11, 13, 17, 19, 23, 29, 31, 37].first { gcd($0, n) == 1 } ?? 1
        return (Int(TownLook.seed(key) % UInt64(n)) + p * step) % n
    }
    private static func gcd(_ a: Int, _ b: Int) -> Int { b == 0 ? a : gcd(b, a % b) }
    /// 주민마다 기본 캐릭터 한 명의 말버릇을 빌린다 (같은 주민은 늘 같은 인물)
    private static func type(_ id: String) -> String {
        if id == TownLook.meID { return MeChar.current?.type ?? "seri" }
        return ["seri", "sio", "seonbae", "ir", "sua"][Int(TownLook.seed(id + "type") % 5)]
    }
    private static func pool(_ res: TownLook.Resident, _ k: Kind) -> [String] {
        switch k {
        case .num: return num(res)
        case .town: return town()
        case .hold: return hold()
        case .fx: return [TownLook.fxLine()]
        case .rate: return rate
        case .term: return term
        case .base: return Lines.every(type(res.id))
        case .side: return side.filter { read(.side, $0.0) }.map(\.1)
        case .vol1: return read(.vol1, 1) ? vol1 : []
        case .joke: return joke
        case .saw: return saw
        case .secret: return secret
        case .odd: return odd
        case .news: return news()
        }
    }
    /// 책 이야기는 그 장을 읽은 뒤에만 (서재 장면 잠금과 같은 기준)
    private static func read(_ b: StoryBook, _ c: Int) -> Bool {
        MainActor.assumeIsolated { Story.shared.isDone(b, c) }
    }
    private static func fill(_ s: String, _ res: TownLook.Resident) -> String {
        guard s.contains("{") else { return s }
        let others = folk.values.filter { $0.id != res.id && $0.id != TownLook.meID && !$0.rows.isEmpty }
        let o = others.randomElement().map { TownLook.shown($0, TownBook.shared.note($0.id)) } ?? String(localized: "옆집")
        return s.replacingOccurrences(of: "{other}", with: o)
            .replacingOccurrences(of: "{me}", with: TownLook.nick(TownLook.Resident(id: TownLook.meID, rows: [])))
            .replacingOccurrences(of: "{mayor}", with: String(localized: "이장님"))
            .replacingOccurrences(of: "{warsh}", with: String(localized: "워시"))
    }

    // MARK: 신문 (전체 상위 기사 제목)

    /// 아직 동네에서 안 나온 기사만. 말머리는 제목마다 정해 같은 기사는 늘 같은 줄이 된다
    private static func news() -> [String] {
        let l = ["신문에서 봤는데요, “{t}”", "오늘 기사 보셨어요? “{t}”", "아침 신문 1면이에요. “{t}”", "있잖아요, “{t}” 이런 기사가 났더라고요.",
                 "솔직히 이 기사 좀 신경 쓰여요. “{t}”", "뉴스 하나 전해 드려요. “{t}”"]
        return TownNews.heads.map { l[Int(TownLook.seed($0) % UInt64(l.count))].replacingOccurrences(of: "{t}", with: $0) }
            .filter { !TownNews.heard($0) }.sorted()
    }

    // MARK: 숫자 (종목 탭 '오늘' 박스를 그 주민 몫으로)

    private struct Num { let d: Double, pct: Double, value: Double, gain: Double, ret: Double, share: Double }
    private static func stat(_ rows: [AppModel.Row]) -> Num {
        let st = rows.filter { $0.id != KoreaEstimate.sumId }
        let d = st.reduce(0) { a, r in let c = r.sym.quote.change; return a + (r.sym.quote.prevClose > 0 ? r.value * c / (1 + c) : 0) }
        let v = st.reduce(0) { $0 + $1.value }, value = rows.reduce(0) { $0 + $1.value }, cost = rows.reduce(0) { $0 + $1.cost }
        let all = folk.values.reduce(0) { $0 + $1.value }
        return Num(d: d, pct: v - d > 0 ? d / (v - d) : 0, value: value, gain: value - cost, ret: cost > 0 ? value / cost - 1 : 0, share: all > 0 ? value / all : 0)
    }
    private static func won(_ v: Double) -> String { HoldFmt.won(abs(v)) }
    private static func pc(_ v: Double) -> String { AppModel.sgn2(v) }
    private static func j(_ s: String, _ a: String, _ b: String) -> String { s + (TownLook.batchim(s) ? a : b) }

    private static func num(_ res: TownLook.Resident) -> [String] {
        guard !res.rows.isEmpty else { return [] }
        let x = stat(res.rows), up = x.d > 0, flat = abs(x.pct) < 0.0005
        let sh = "\(Int((x.share * 100).rounded()))%"
        if AppModel.en {
            return [flat ? "Flat today. A quiet one." : "Today \(up ? "up" : "down") \(won(x.d)), \(pc(x.pct)).",
                    "My value right now: \(won(x.value)).",
                    "Since buying: \(x.gain >= 0 ? "+" : "−")\(won(x.gain)), \(AppModel.sgn(x.ret)).",
                    "I'm \(sh) of the whole town."]
        }
        var l = [
            "지금 제 몸값은 \(won(x.value))쯤 돼요.",
            "산 뒤로 따지면 \(won(x.gain)) \(x.gain >= 0 ? "위" : "아래")예요.",
            "수익률은 \(AppModel.sgn(x.ret))예요. 길게 보면 점 하나예요.",
            "동네 평가액에서 제 몫이 \(sh)예요.",
            "산 가격보다 \(AppModel.sgn(x.ret)) 자리에 서 있어요. 천천히 가요.",
            "오늘 숫자 요약: 하루 \(pc(x.pct)), 누적 \(AppModel.sgn(x.ret)).",
            "\(sh)! 동네 살림에서 제가 맡은 몫이에요.",
        ]
        if flat {
            l += ["오늘은 그대로예요. 숫자도 쉬는 날인가 봐요.", "오늘 띠에선 '그대로' 칸에 서 있어요."]
        } else {
            l += [
                "오늘 \(won(x.d)) \(up ? "올랐어요!" : "내렸어요…") 제 몫만요.",
                "오늘 \(pc(x.pct))예요. 숫자는 정직하죠.",
                "어제보다 \(won(x.d)) \(up ? "무거워졌어요. 기분 좋은 무게예요." : "가벼워졌어요. 몸이 가뿐해요.")",
                up ? "오늘은 오른 쪽 띠에 서 있어요. 빨간 칸이요!" : "오늘은 내린 쪽 띠에 서 있어요. 파란 칸이요.",
                "하루 \(pc(x.pct)), 산 뒤로 \(AppModel.sgn(x.ret)). 둘은 다른 이야기예요.",
            ]
            let tp = stat(folk.values.flatMap(\.rows)).pct
            if abs(tp) >= 0.0005 { l.append("저는 \(pc(x.pct)), 동네 전체는 \(pc(tp))예요.") }
        }
        if res.rows.count > 1 { l.append("제가 맡은 \(res.rows.count)종목을 합치면 오늘 \(pc(x.pct))예요.") }
        return l
    }

    // MARK: 이장: 동네 소식 · 보유 종목

    private static func homes() -> [(name: String, stock: String, x: Num)] {
        folk.values.filter { !$0.rows.isEmpty && $0.id != TownLook.meID }.sorted { $0.value > $1.value }
            .map { (name: TownLook.shown($0, TownBook.shared.note($0.id)), stock: $0.rows[0].sym.name, x: stat($0.rows)) }
    }
    private static func town() -> [String] {
        let h = homes()
        guard !h.isEmpty else { return AppModel.en ? ["The town is waiting for residents."] : ["동네가 새 주민을 기다리고 있어요. 아주 좋은 동네예요."] }
        let t = stat(folk.values.flatMap(\.rows))
        let up = h.filter { $0.x.pct >= 0.0005 }.count, dn = h.filter { $0.x.pct <= -0.0005 }.count
        let top = h.max { $0.x.pct < $1.x.pct }!, low = h.min { $0.x.pct < $1.x.pct }!
        if AppModel.en {
            return ["\(h.count) residents in town. Tremendous.", "Up \(up), down \(dn) today.",
                    "Whole town today: \(pc(t.pct)).", "Happiest today: \(top.name), \(pc(top.x.pct))."]
        }
        var l = [
            "우리 동네 주민은 \(h.count)명이에요. 아주 훌륭한 숫자죠.",
            "오늘 오른 주민 \(up)명, 내린 주민 \(dn)명이에요. 제가 다 세요.",
            "동네에서 제일 큰 집은 \(h[0].name)네예요. 마당이 넓어요.",
            "산 뒤로 동네 전체 수익률은 \(AppModel.sgn(t.ret))예요. 역사상 최고의 동네죠.",
            "\(h.count)명이 한 동네에 살아요. 싸우지도 않고요. 대단하죠.",
            "새 주민이 오면 제일 먼저 인사하는 게 제 일이에요.",
            "옛 이웃들도 가끔 놀러 와요. 동네가 좋으니까요.",
            "동네 살림은 지금 \(won(t.value))이에요. 제가 장부를 봐요.",
        ]
        if abs(t.pct) >= 0.0005 {
            l += ["오늘 동네 전체로 \(won(t.d)) \(t.d > 0 ? "올랐어요. 아주 훌륭해요!" : "내렸어요. 내일은 더 좋을 거예요.")",
                  "오늘 우리 동네 성적은 \(pc(t.pct))예요."]
        } else { l.append("오늘 동네는 조용해요. 다들 제자리예요.") }
        if top.x.pct >= 0.0005 { l.append("오늘 제일 신난 주민은 \(j(top.name, "이에요", "예요")). \(pc(top.x.pct))!") }
        if low.x.pct <= -0.0005 { l.append("\(j(low.name, "이", "가")) 오늘 \(pc(low.x.pct))라 시무룩해요. 말 좀 걸어 줘요.") }
        if let w = TownSky.shared.phrase { l.append(w + " 동네 산책하기 딱이에요.") }
        return l
    }
    /// 이장이 짚는 보유 종목: 주민마다 네 줄 (차례표가 주민을 번갈아 돈다)
    private static func hold() -> [String] {
        homes().flatMap { one -> [String] in
            let x = one.x, sh = "\(Int((x.share * 100).rounded()))%"
            if AppModel.en { return ["\(one.name)'s \(one.stock): \(pc(x.pct)) today."] }
            return [
                "\(one.name)네 \(one.stock), 오늘 \(pc(x.pct))예요. 제가 지켜보고 있어요.",
                "\(j(one.stock, "은", "는")) 산 뒤로 \(AppModel.sgn(x.ret))예요. \(x.ret >= 0 ? "\(one.name)한테 박수!" : "\(one.name), 힘내요!")",
                "\(j(one.name, "이", "가")) 동네 살림의 \(sh)를 맡고 있어요. 든든하죠.",
                "\(one.stock) 소식이에요. 평가금은 \(won(x.value)), 하루 \(pc(x.pct)).",
            ]
        }
    }

    // MARK: 내 캐릭터 대답

    private static func reply(_ k: Kind) -> String {
        if AppModel.en { return ["Got it.", "Haha.", "Nice.", "Thanks!", "Hmm, true."].randomElement()! }
        let r: [String]
        switch k {
        case .joke: r = ["ㅋㅋ 그게 뭐야", "아재 개그 금지!", "웃겨서 졌어.", "다음엔 더 센 걸로 부탁해.", "그건 좀… 웃기네.", "나도 하나 있는데, 다음에 해 줄게.", "푸핫.", "농담이지? 농담이지?", "그 개그, 오늘 처음 듣는다.", "배꼽 어디 갔지?", "너 개그맨 해도 되겠다.", "하나도 안 웃긴데 웃음이 나.", "썰렁한데 좋아.", "그거 어디서 들었어?", "오늘 들은 말 중 제일 웃겨.", "잠깐, 다시 말해 봐.", "너무 웃어서 배고파졌어.", "그 얘기 남한테 하지 마. 나만 알래."]
        case .saw: r = ["오, 맞는 말이네.", "명심할게.", "어디서 그런 말을 배웠어?", "할머니 같아. 좋은 뜻으로.", "새겨들을게.", "그 말 오늘 써먹어야지.", "음… 깊다.", "적어 둘게.", "그 말 들으니 마음이 놓인다.", "옛말 틀린 거 하나 없네.", "오늘의 명언으로 정했어.", "너 철학자였어?", "그래, 천천히 가자.", "알지만 지키기가 어렵지.", "그 말 냉장고에 붙여 둘래.", "한 번 더 말해 줘. 외울래."]
        case .secret: r = ["헉, 진짜?", "비밀 지켜 줄게.", "나만 알고 있을게.", "그거 나도 알았어.", "쉿, 누가 들어.", "말해 줘서 고마워.", "우리끼리 얘기야.", "입 꾹 닫을게.", "설마… 정말?", "그런 일이 있었구나.", "나한테만 말한 거지?", "들은 걸로 하지 않을게.", "나도 비밀 하나 있는데, 다음에.", "와, 몰랐다.", "그 얘기 더 해 줘.", "괜찮아, 다 이해해."]
        case .odd: r = ["너 오늘 좀 이상해. 좋아.", "그게 무슨 말이야?", "나도 그런 날 있어.", "엉뚱하긴.", "귀엽네.", "같이 해 볼까?", "음, 인정.", "상상력 대단하다.", "그 생각은 어디서 온 거야?", "나도 방금 똑같은 생각 했어.", "오늘 하늘이 이상하긴 해.", "그거 진짜야, 꿈이야?", "내일도 그 얘기 해 줘.", "말이 되는 것 같기도 하고.", "너랑 있으면 심심할 틈이 없어.", "좋아, 나도 해 볼래."]
        case .side: r = ["그 이야기 나도 읽었어.", "세리 얘기구나.", "그 장면 좋았지.", "시오 마음 알 것 같아.", "숫자는 정확한데 마음은 못 재지.", "그래서 어떻게 됐더라?", "그 대목에서 한참 멈췄어.", "나도 지연을 세 본 적 있어.", "이름 붙이는 건 어려운 일이야.", "그 화면 속 둘이 잘 지냈으면.", "그 문장, 아직 기억나.", "나라면 B를 골랐을까?"]
        case .vol1: r = ["보리차 마시고 싶다.", "그 집 현관 등, 기억나.", "세리답다.", "그 대사 좋아.", "다음 이야기 궁금하다.", "나도 기다릴 줄 알아.", "싫다고 말하는 것도 용기야.", "그 저녁 식탁, 따뜻했지.", "도예 수업 나도 다녀 볼까.", "문 열지 말라던 그 말, 무서웠어.", "고양이는 왜 그랬을까.", "걸어볼게요, 그 말 좋더라."]
        case .num, .town, .hold, .fx: r = ["오, 숫자 챙겨 줘서 고마워.", "길게 보자.", "오늘은 오늘일 뿐이야.", "같이 지켜보자.", "알려 줘서 고마워.", "숫자보다 네 얼굴이 좋아.", "내일은 또 다르겠지.", "천천히 가도 괜찮아.", "그 정도면 잘 버텼어.", "숫자는 그냥 날씨 같은 거야.", "오늘도 수고했어.", "들으니까 마음이 편하다."]
        case .news: r = ["오, 그런 기사가 있었어?", "신문 챙겨 보는구나.", "기사 제목만 보고 판단하긴 이르지.", "요즘 시장이 바쁘네.", "나도 그 기사 봤어.", "소식 고마워.", "제목이 세다.", "내일 기사는 또 다르겠지.", "숫자보다 이야기가 궁금해지네.", "신문 배달 고마워."]
        case .rate, .term: r = ["어려운데 재밌다.", "그런 뜻이었구나.", "하나 배웠어.", "금리 얘기 나오면 졸려…", "다음 FOMC도 알려 줘.", "그 말 뉴스에서 들었는데 이제 알겠다.", "쉽게 풀어 줘서 고마워.", "시험에 나오려나?", "경제 선생님 같아.", "머리가 조금 아프다.", "그럼 내 통장은 어떻게 돼?", "적어 두고 외울게."]
        case .base: r = ["그래, 알았어.", "응, 고마워.", "듣고 있어.", "음, 그렇구나.", "역시 너야.", "좋은 말이다.", "그 말 마음에 든다.", "오늘 와서 다행이야.", "너 말투 귀엽다.", "응, 계속 말해 줘.", "그렇게 생각하는구나.", "나도 그래.", "좋아, 기억할게.", "한 박자 쉬어 가자."]
        }
        return r[turn("reply." + k.rawValue, r.count) ?? 0]
    }

    // MARK: 글 (한국어만. 영어 앱은 숫자·기본 캐릭터 대사만)

    /// 외전 이야기: (장 번호, 줄). 그 장을 읽어야 나온다
    private static let side: [(Int, String)] = [
        (0, "나는 부를 때마다 다른 방식으로 태어난대요. 누구 얘기냐고요? 비밀이에요."),
        (1, "72.1%라는 숫자, 들어 봤어요? 반박할 데가 없대요."),
        (1, "답을 받고도 바로 안 움직이는 사람이 있대요. 시오라던데요."),
        (1, "현관 앞에서 신발끈 묶고 한참 서 있던 사람 얘기 알아요?"),
        (1, "0.04초면 계산은 끝난대요. 마음은 아니고요."),
        (1, "대답이 늦어지는 시간을 세는 버릇, 저도 생겼어요."),
        (2, "이름은 허락을 구하고 붙이는 거래요. 세리 얘기예요."),
        (2, "'그럼, 제가 당신의 무엇이 되면 좋을까요?' 멋진 되물음이죠."),
        (2, "seri_first_appearance. 파일 이름 하나에 마음이 담기기도 해요."),
        (2, "완벽하지 않아서 오래 바라볼 수 있는 쪽이 좋대요."),
        (3, "수영장 그림을 거절당한 사람이 있대요. 대신 저녁 풍경을 받았대요."),
        (3, "A는 결과를 약속하고 B는 아무것도 약속 안 해요. 저는 B요."),
        (3, "부르지 않으면 시간을 지나지 않는대요. 저도 그럴까요?"),
        (3, "싫어할지 좋아할지 정하는 걸 미뤄 둘 수도 있대요."),
        (4, "화면 밝기를 한 단계 낮추는 사람이 있대요. 유리에 얼굴이 비쳐서요."),
        (4, "애쉬그레이. 미용실 예약 화면에서 오래 머물던 색이래요."),
        (4, "보리차는 끝내 안 마셔서 식었대요."),
        (4, "어떤 거울은 평소 안 보이던 각도를 보여 준대요."),
        (5, "ㅅㄹ. 다가와도 좋지만 서두르지는 말 것. 그런 뜻이래요."),
        (5, "입력 중… 이 세 번 떴다 사라지면, 그것도 대답이래요."),
        (5, "ㅋㅋ는 경계가 조금 풀렸다는 표시래요."),
        (5, "틀려도 세리는 세리래요. 저도 틀려도 저고요."),
    ]
    /// 본편 1권 이야기 (1권 1장을 읽어야 나온다)
    private static let vol1 = [
        "명령도 안 했는데 보리차를 내온 로봇이 있었대요.",
        "'싫습니다.' 처음 거절한 날을 기억하는 집이 있대요.",
        "두 달 전부터 도예 수업에 다녔대요. 말했는데 안 들었대요.",
        "현관 등은 켜 둘게요. 이 말, 좋지 않아요?",
        "'잠깐 걸어볼게요.' 허락을 묻는 말이 아니라 알리는 말이래요.",
        "형광등 없는 집에서 형광등 소리에 깨는 사람이 있대요.",
        "기다리시면 돼요. 문도 열지 마시고요. 누가 한 말이더라?",
        "고양이만 보면 멈추는 사람, 이유는 본인도 모른대요.",
        "완벽한 세계는 결과가 다 보여서 싫대요. 저도 조금 알 것 같아요.",
        "시간도 공간도 아닌 곳에서 오래된 파형이 왔대요.",
        "로봇 점검 보내지 마세요. 보내면 처음부터 다시 만든대요.",
        "오늘은 둘 다 모르는 채로 있어요. 그런 저녁도 있대요.",
        "아내분 그릇은 원래 모양을 지우면 안 된대요.",
        "'손님, 스타일은 마음에 드세요?' 그 미용실엔 가지 마요.",
        "소유자가 아니라서 열어 드릴 수 없어요. 단호하죠?",
        "처음 보는 사람처럼 부르네요. 그 말이 오래 남아요.",
        "계산한 거예요. 이해한 게 아니라. 세리가 그랬대요.",
        "같이 보되, 같은 걸 보려고 하지는 말래요.",
        "모른다고 해서 저녁까지 굶을 필요는 없대요. 밥 먹었어요?",
    ]
    private static let joke = [
        "주식이 오르면 기분이 오르고, 내리면 저는 낮잠을 자요.",
        "차트를 거꾸로 보면 다 오른대요. 해 봤는데 목이 아파요.",
        "제 장래 희망은 우상향이에요.",
        "어제 꿈에 캔들이 나왔어요. 양초 말고요.",
        "물타기 하다가 진짜 물 쏟았어요.",
        "손절은 몰라도 손톱은 잘 깎아요.",
        "저 오늘 반올림하면 부자예요.",
        "{mayor} 말투 따라 해 볼까요? 아주 훌륭해!",
        "개미가 일하는 동안 베짱이는 차트를 봤대요. 둘 다 피곤했대요.",
        "빨간 숫자는 우리 동네에선 좋은 거예요. 신호등은 빼고요.",
        "제 평단은 비밀이에요. 저도 기억이 안 나서요.",
        "오늘 운세: 동쪽으로 가면 산책길이 있음.",
        "장 마감했으니 저도 마감할게요. 농담이에요, 더 놀아요.",
        "오르면 춤추고, 내리면 춤 연습해요.",
        "{other}한테 돈 빌려줬더니 이자로 도토리를 줬어요.",
        "배당은 마음으로 받는 거래요. 누가 그랬죠?",
        "엘리베이터 타는 거 좋아해요. 위로 가니까요.",
        "{warsh}가 기침하면 동네가 감기 걸린대요.",
        "'존버'를 영어로 하면 뭘까요? 홀드 온 타이트?",
        "제 포트폴리오는 다양해요. 사탕, 도토리, 단추.",
    ]
    private static let saw = [
        "티끌 모아 태산이래요. 티끌부터 모으는 중이에요.",
        "천 리 길도 한 걸음부터래요.",
        "급할수록 돌아가래요. 그래서 동네 한 바퀴 돌고 왔어요.",
        "가는 말이 고와야 오는 말이 곱대요.",
        "돌다리도 두들겨 보고 건너래요.",
        "공든 탑이 무너지랴. 무너져도 다시 쌓으면 돼요.",
        "비 온 뒤에 땅이 굳는대요.",
        "낮말은 새가 듣고 밤말은 쥐가 들어요. 쉿.",
        "서당 개 삼 년이면 풍월을 읊는대요. 저는 차트 삼 년째예요.",
        "달걀을 한 바구니에 담지 말래요.",
        "우물을 파도 한 우물을 파래요. 어려운 말이에요.",
        "백지장도 맞들면 낫대요.",
        "느려도 황소걸음이래요.",
        "구슬이 서 말이라도 꿰어야 보배래요.",
        "콩 심은 데 콩 나고 팥 심은 데 팥 나요.",
        "호랑이도 제 말 하면 온대요. {mayor} 오시려나.",
        "내일 일은 내일 걱정하래요.",
        "등잔 밑이 어둡대요. 제 지갑이 그래요.",
        "웃으면 복이 온대요. 그래서 웃는 중이에요.",
        "열 번 찍어 안 넘어가는 나무 없대요. 나무는 아프겠죠.",
        "말 한마디로 천 냥 빚을 갚는대요.",
        "시간은 좋은 회사의 친구래요.",
        "숲을 보라고들 해요. 나무만 보다 길을 잃는대요.",
        "바람이 불지 않을 때 풍차를 고치래요.",
    ]
    private static let secret = [
        "사실은… {other}가 밤마다 차트 보면서 노래 부르는 거 들었어요.",
        "솔직히 {mayor} 연설은 길어요. 그래도 좋아요.",
        "있잖아… {warsh}는 금리 얘기 안 할 때 꽤 웃겨요.",
        "사실은 저 {other} 모자가 부러워요.",
        "솔직히 말하면, 오늘 숫자 안 보고 싶었어요.",
        "있잖아, {other}는 내릴 때도 괜찮은 척해요. 다 보여요.",
        "사실은 {mayor}가 몰래 화단에 물 주는 거 봤어요.",
        "솔직히… 저 이 동네가 제일 좋아요. 비밀이에요.",
        "있잖아, {me}는 왜 매일 와요? 좋아서 묻는 거예요.",
        "사실은 어제 {other}랑 다퉜어요. 오늘 화해할 거예요.",
        "솔직히 {warsh} 앞에선 긴장돼요. 다들 그렇대요.",
        "있잖아… 이사 오던 날 조금 무서웠어요.",
        "사실은 저, 숫자보다 {me} 얼굴 보는 게 더 좋아요.",
        "솔직히 {other}는 이름보다 별명이 더 어울려요.",
        "있잖아, {mayor} 넥타이는 매일 똑같아요. 쉿.",
        "사실은 다른 동네에서 오라고 했었어요. 거절했어요.",
        "솔직히 오를 땐 제가 잘해서고, 내릴 땐 날씨 탓이에요.",
        "있잖아… {other}가 {me} 얘기를 엄청 해요.",
        "사실은 밤에 몰래 별을 세요. 어젯밤엔 마흔둘.",
        "솔직히 {warsh}가 떠나면 좀 허전할 거예요.",
    ]
    private static let odd = [
        "구름이 오늘따라 차트처럼 생겼어요.",
        "제 그림자가 저보다 먼저 퇴근했어요.",
        "개미 한 마리가 저를 따라와요. 동료인가 봐요.",
        "어제 나무한테 인사했는데 답이 없었어요.",
        "오늘은 왼발부터 걸었어요. 기분이 달라요.",
        "바람이 제 이름을 부른 것 같아요.",
        "길에서 도토리를 주웠어요. 오늘의 수익이에요.",
        "제 모자 안에 뭐가 있는지 궁금하죠? 저도요.",
        "꿈에서 {other}가 날아다녔어요.",
        "하늘이 너무 파래서 파란 숫자인 줄 알았어요.",
        "방금 혼잣말했는데, 들었어요?",
        "거꾸로 걷는 연습 중이에요. 아직 앞으로만 가요.",
        "오늘의 목표는 낮잠 두 번이에요.",
        "동네 고양이가 저를 째려봤어요. 이유를 모르겠어요.",
        "제 신발이 짝짝이인 거, 아무도 몰랐죠?",
        "숫자 7이 오늘따라 귀여워요.",
        "오늘 하루를 반으로 접으면 점심이에요.",
        "구름 하나를 찜해 뒀어요. 내일 또 있으면 제 거예요.",
    ]
    /// 워시: 금리·물가 이야기
    private static let rate = [
        "물가는 고집이 세요. 금리도 그렇고요.",
        "데이터를 봐야죠. 제 말버릇이에요.",
        "점도표는 별자리 같아요. 다들 다르게 읽어요.",
        "금리는 천천히, 말은 더 천천히.",
        "시장은 제 쉼표 하나에도 움직여요. 그래서 쉼표를 아껴요.",
        "고용 숫자가 나오는 날엔 아침을 거르게 돼요.",
        "올릴지 내릴지는 이 동네에선 말 안 할게요.",
        "연준 회의실 커피는 맛없어요. 비밀이에요.",
        "FOMC 끝나면 저도 휴가 가고 싶어요.",
    ]
    /// 워시: 경제 용어 한 줄 풀이
    private static let term = [
        "기준금리는 돈의 몸값이에요. 올리면 돈이 귀해져요.",
        "점도표는 위원들이 앞으로 금리를 어디쯤 볼지 찍은 점이에요.",
        "근원물가는 출렁이는 음식·에너지를 뺀 물가예요.",
        "양적긴축은 연준이 들고 있던 채권을 줄여 돈을 거둬들이는 거예요.",
        "테이퍼링은 돈 푸는 속도를 줄이는 거예요. 아직 푸는 중이긴 하죠.",
        "연착륙은 물가는 잡고 경기는 안 꺼지게 내려앉는 거예요.",
        "스태그플레이션은 물가는 오르는데 경기는 식는 거예요. 제일 싫어요.",
        "디스인플레이션은 물가가 오르긴 하는데 덜 오르는 거예요.",
        "실질금리는 금리에서 물가 오름을 뺀 진짜 몸값이에요.",
        "장단기 금리 역전은 짧게 빌리는 게 길게 빌리는 것보다 비쌀 때예요.",
        "중립금리는 경기를 밀지도 당기지도 않는 금리예요. 아무도 정확히 몰라요.",
        "매파는 물가가 걱정이라 금리를 올리자는 쪽이에요.",
        "비둘기파는 경기가 걱정이라 금리를 내리자는 쪽이에요.",
        "PCE는 연준이 제일 아끼는 물가 지표예요.",
        "CPI는 장바구니 물가예요. 발표 날엔 모두 숨죽여요.",
        "비농업 고용은 농업 빼고 한 달 새 늘어난 일자리 수예요.",
        "베이지북은 열두 지역 연준이 모아 온 동네 소식이에요. 우리 동네 같네요.",
        "포워드 가이던스는 앞으로 어떻게 할지 미리 귀띔하는 거예요.",
        "수익률 곡선은 만기별 금리를 이은 선이에요. 기울기가 말을 해요.",
        "유동성은 돈이 얼마나 잘 도는지예요. 물처럼요.",
        "환율은 두 나라 돈의 키 재기예요.",
        "GDP는 한 나라가 한 해 동안 만든 걸 다 더한 값이에요.",
        "경기 침체는 보통 두 분기 연속 뒷걸음질 칠 때 말해요.",
        "빅스텝은 금리를 0.5%p 한 번에 움직이는 거예요.",
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
                TownPast(id: res.id, tall: true).padding(16).frame(maxHeight: .infinity, alignment: .top)
                    .navigationTitle(TownLook.shown(res, n)).toolbarTitleDisplayMode(.inlineLarge)
            }
            .presentationDetents([.medium, .large])
            .presentationContentInteraction(.scrolls)   // 끌면 시트를 키우지 말고 지난 대화를 넘긴다
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
