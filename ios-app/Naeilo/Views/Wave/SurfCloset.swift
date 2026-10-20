import SwiftUI

/// 파도 타기 꾸미기: 보드·모자 (문자 격자 도트, 이미지 파일 없음). 뽑기 없음, 누적 점수·특별 조건으로 열린다.
/// 시안 좌표(12칸 인물)를 2배로 키워 내 캐릭터 버퍼(MeArt 24×34)에 맞춘다: 모자는 머리 위(시안 y1 = 0줄), 보드는 발밑(시안 y11 = 34줄).
enum SurfOpen: Equatable {
    case free, score(Int), hardClear, fever(Int), splash(Int), snap(Int)
}

struct SurfItem: Identifiable {
    let id: Int
    let name: String
    /// 모자: 왼쪽 위 (시안 좌표). 보드: 윗줄 x1 y11, 아랫줄 x2 y12 고정
    let x: Int, y: Int
    let rows: [String]
    var deco: [(Int, Int, Character)] = []
    let open: SurfOpen
}

enum SurfCloset {
    static let palette: [Character: UInt32] = [
        "c": 0xF06A4A, "C": 0xC2410C, "y": 0xFFD84D, "Y": 0xE0A800, "o": 0xF59E0B, "b": 0x8A5A2B,
        "g": 0x3FAE5A, "G": 0x1E7A3A, "r": 0xE8505B, "k": 0x14304D, "w": 0xFFFFFF, "s": 0x9FD3F5,
        "p": 0xF7A08A, "v": 0x8B6FD6, "B": 0x2F7FD8, "a": 0x8A9BB0, "A": 0x5C6B7D, "h": 0xE8D3A8, "q": 0xF2C14E,
    ]

    static let boards: [SurfItem] = [
        SurfItem(id: 0, name: String(localized: "코랄"), x: 1, y: 11, rows: ["cccccccccc", ".CCCCCCCC"], open: .free),
        SurfItem(id: 1, name: String(localized: "바나나"), x: 1, y: 11, rows: ["byyyyyyyyb", ".oooooooo"], open: .score(500)),
        SurfItem(id: 2, name: String(localized: "수박"), x: 1, y: 11, rows: ["rrkrrrrkrr", ".GgGgGgGg"], open: .score(1200)),
        SurfItem(id: 3, name: String(localized: "구름"), x: 1, y: 11, rows: ["wwwwwwwwww", ".ssssssss"],
                 deco: [(1, 10, "w"), (2, 10, "w"), (10, 10, "w")], open: .score(2000)),
        SurfItem(id: 4, name: String(localized: "무지개"), x: 1, y: 11, rows: ["rroyygBBvv", ".royygBBv"], open: .score(3200)),
        SurfItem(id: 5, name: String(localized: "상어"), x: 1, y: 11, rows: ["aaaaaaaaka", ".wwwwwwww"],
                 deco: [(2, 10, "a"), (1, 9, "a")], open: .score(5000)),
        SurfItem(id: 6, name: String(localized: "캔들"), x: 1, y: 11, rows: ["ggrgggrrgg", ".kkkkkkkk"],
                 deco: [(2, 10, "g"), (8, 10, "r")], open: .hardClear),
        SurfItem(id: 7, name: String(localized: "황금"), x: 1, y: 11, rows: ["yyyyyyyyyy", ".YYYYYYYY"],
                 deco: [(0, 9, "q"), (11, 10, "q"), (12, 8, "q")], open: .fever(10)),
    ]

    static let hats: [SurfItem] = [
        SurfItem(id: 0, name: String(localized: "없음"), x: 0, y: 0, rows: [], open: .free),
        SurfItem(id: 1, name: String(localized: "밀짚"), x: 1, y: -1, rows: ["..yyyyyy..", "..rrrrrr..", "oyyyyyyyyo"], open: .score(300)),
        SurfItem(id: 2, name: String(localized: "비니"), x: 3, y: -3, rows: ["..ww..", ".BBBB.", "BBBBBB", "ssssss"], open: .score(800)),
        SurfItem(id: 3, name: String(localized: "꽃핀"), x: 7, y: 0, rows: [".p.", "pyp", ".p."], open: .score(1500)),
        SurfItem(id: 4, name: String(localized: "고깔"), x: 4, y: -3, rows: [".yy.", ".vv.", "vyyv", "vvvv"], open: .score(2500)),
        SurfItem(id: 5, name: String(localized: "개구리"), x: 2, y: -2, rows: [".gg..gg.", "gwkggwkg", "gggggggg", ".gggggg."], open: .score(4000)),
        SurfItem(id: 6, name: String(localized: "곰 귀"), x: 2, y: -1, rows: ["bb....bb", "bp....pb", ".bbbbbb."], open: .splash(20)),
        SurfItem(id: 7, name: String(localized: "황소 뿔"), x: 0, y: -2, rows: ["h..........h", "h..........h", ".hh......hh.", "..A......A.."], open: .snap(10)),
    ]

    // MARK: 저장 (UserDefaults 정수 몇 개)
    private static var d: UserDefaults { store }
    static var total: Int { d.integer(forKey: "surf.total") }
    static var fevers: Int { d.integer(forKey: "surf.fever") }
    static var splashes: Int { d.integer(forKey: "surf.splash") }
    static var snaps: Int { d.integer(forKey: "surf.snap") }
    static var hardCleared: Bool { d.integer(forKey: "surf.hard") > 0 }
    static var board: Int {
        get { let v = d.integer(forKey: "surf.board"); return isOpen(boards[min(max(v, 0), boards.count - 1)]) ? v : 0 }
        set { d.set(newValue, forKey: "surf.board"); keep() }
    }
    static var hat: Int {
        get { let v = d.integer(forKey: "surf.hat"); return isOpen(hats[min(max(v, 0), hats.count - 1)]) ? v : 0 }
        set { d.set(newValue, forKey: "surf.hat"); keep() }
    }

    // MARK: 앱을 지웠다 깔아도 남게: 벽돌 게임 기록(GameRecord)처럼 키체인에도 한 벌 (UserDefaults 는 앱과 같이 지워진다)
    /// 파도 타기 정수 키 전부: 꾸미기 + 섬 별·최고 점수 (Backup 파일에도 같이 넣는다)
    static let keys = ["surf.total", "surf.fever", "surf.splash", "surf.snap", "surf.hard", "surf.board", "surf.hat"]
        + WaveMap.order.flatMap { ["wave.star.\($0.rawValue)", "wave.best.\($0.rawValue)"] }
    private static let kc: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.game", kSecAttrAccount as String: "wave.v1"]
    /// 처음 읽을 때 한 번: 이 폰 UserDefaults 가 비어 있으면 키체인에서 되살린다
    static let store: UserDefaults = {
        let u = UserDefaults.standard
        guard keys.allSatisfy({ u.object(forKey: $0) == nil }) else { return u }
        var q = kc; q[kSecReturnData as String] = true
        var out: AnyObject?
        if SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data,
           let m = try? JSONDecoder().decode([String: Int].self, from: data) {
            for (k, v) in m where keys.contains(k) { u.set(v, forKey: k) }
        }
        return u
    }()
    static func keep() {
        let m = Dictionary(uniqueKeysWithValues: keys.map { ($0, d.integer(forKey: $0)) })
        guard let data = try? JSONEncoder().encode(m) else { return }
        let set: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        if SecItemUpdate(kc as CFDictionary, set as CFDictionary) == errSecItemNotFound {
            SecItemAdd(kc.merging(set) { $1 } as CFDictionary, nil)
        }
    }

    static func isOpen(_ i: SurfItem) -> Bool {
        switch i.open {
        case .free: return true
        case .score(let n): return total >= n
        case .hardClear: return hardCleared
        case .fever(let n): return fevers >= n
        case .splash(let n): return splashes >= n
        case .snap(let n): return snaps >= n
        }
    }

    /// 열리는 조건 (잠긴 칸에 짧게)
    static func need(_ i: SurfItem) -> String {
        switch i.open {
        case .free: return ""
        case .score(let n): return String(localized: "\(String(describing: n))점")
        case .hardClear: return String(localized: "어려움 완주")
        case .fever(let n): return String(localized: "피버 \(String(describing: min(fevers, n)))/\(String(describing: n))")
        case .splash(let n): return String(localized: "풍덩 \(String(describing: min(splashes, n)))/\(String(describing: n))")
        case .snap(let n): return String(localized: "딱! \(String(describing: min(snaps, n)))/\(String(describing: n))")
        }
    }

    /// 한 판 결과를 쌓고 새로 열린 보드·모자를 돌려준다 (누적 점수는 줄지 않는다)
    @discardableResult
    static func add(score: Int, fever: Int = 0, splash: Int = 0, snap: Int = 0, hardClear: Bool = false) -> [SurfItem] {
        let all = boards + hats
        let before = all.map(isOpen)
        d.set(total + max(score, 0), forKey: "surf.total")
        d.set(fevers + max(fever, 0), forKey: "surf.fever")
        d.set(splashes + max(splash, 0), forKey: "surf.splash")
        d.set(snaps + max(snap, 0), forKey: "surf.snap")
        if hardClear { d.set(1, forKey: "surf.hard") }
        keep()
        return zip(all, before).filter { !$1 && isOpen($0) }.map(\.0)
    }

    /// 다음 점수 선물: 이름·남은 점수·막대 비율 (모두 열렸으면 nil)
    static func nextGift() -> (item: SurfItem, left: Int, ratio: Double)? {
        let steps = (boards + hats).compactMap { i -> (SurfItem, Int)? in
            if case .score(let n) = i.open { return (i, n) } else { return nil }
        }.sorted { $0.1 < $1.1 }
        guard let next = steps.first(where: { $0.1 > total }) else { return nil }
        let prev = steps.last(where: { $0.1 <= total })?.1 ?? 0
        return (next.0, next.1 - total, Double(total - prev) / Double(max(next.1 - prev, 1)))
    }
}

/// 보드 위 내 캐릭터 + 모자. 도트 하나 = dot pt. hideHat: 피버 왕관이 뜰 때 모자 숨기기
struct SurfRider: View {
    var me: MeChar = MeChar.current ?? MeChar()
    var board = SurfCloset.board
    var hat = SurfCloset.hat
    var hideHat = false
    var dot: CGFloat = 3
    // 캔버스: 버퍼 x -2...25, y -8...37
    private static let ox = 2, oy = 8, cw = 28, ch = 46

    var body: some View {
        let img = MeMotion.image(me)
        ZStack(alignment: .topLeading) {
            if let img {
                Image(uiImage: img).interpolation(.none).resizable()
                    .frame(width: dot * img.size.width, height: dot * img.size.height)
                    .offset(x: dot * CGFloat(Self.ox), y: dot * (CGFloat(Self.oy + MeArt.H) - img.size.height))
            }
            Canvas { ctx, _ in
                let b = SurfCloset.boards[min(max(board, 0), SurfCloset.boards.count - 1)]
                // 보드: 시안 y → 버퍼 34 + 2(y-11)
                for (r, row) in b.rows.enumerated() { cells(ctx, row, b.x, 11 + r) { 2 * $0 } y: { MeArt.H + 2 * ($0 - 11) } }
                for (x, y, c) in b.deco { cells(ctx, String(c), x, y) { 2 * $0 } y: { MeArt.H + 2 * ($0 - 11) } }
                guard !hideHat else { return }
                let h = SurfCloset.hats[min(max(hat, 0), SurfCloset.hats.count - 1)]
                // 모자: 시안 y → 버퍼 2(y-1)
                for (r, row) in h.rows.enumerated() { cells(ctx, row, h.x, h.y + r) { 2 * $0 } y: { 2 * ($0 - 1) } }
            }
        }
        .frame(width: dot * CGFloat(Self.cw), height: dot * CGFloat(Self.ch), alignment: .topLeading)
    }

    /// 한 줄 문자를 시안 1칸 = 버퍼 2×2 칸으로 칠한다 ('.' 투명)
    private func cells(_ ctx: GraphicsContext, _ row: String, _ x0: Int, _ y0: Int, x: (Int) -> Int, y: (Int) -> Int) {
        for (i, ch) in row.enumerated() {
            guard let hex = SurfCloset.palette[ch] else { continue }
            let r = CGRect(x: dot * CGFloat(x(x0 + i) + Self.ox), y: dot * CGFloat(y(y0) + Self.oy), width: dot * 2, height: dot * 2)
            ctx.fill(Path(r), with: .color(Color(hex: hex)))
        }
    }
}

/// 꾸미기 시트: 미리보기 + 보드·모자 칸(잠긴 칸은 흐리게, 조건 표시) + 다음 선물 막대
struct SurfClosetSheet: View {
    @State private var board = SurfCloset.board
    @State private var hat = SurfCloset.hat
    @State private var hint: String?
    // 열 때 한 번만 읽는다 (그릴 때마다 UserDefaults 를 읽지 않게)
    private let total = SurfCloset.total
    private let next = SurfCloset.nextGift()
    private let opened = Set(SurfCloset.boards.filter(SurfCloset.isOpen).map { String(localized: "보드\(String(describing: $0.id))") }
                             + SurfCloset.hats.filter(SurfCloset.isOpen).map { String(localized: "모자\(String(describing: $0.id))") })
    @Environment(\.dismiss) private var dismiss
    private let cols = [GridItem(.adaptive(minimum: 76), spacing: 8)]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    SurfRider(board: board, hat: hat, dot: 5).padding(.top, 8)
                    gift
                    section(String(localized: "보드"), SurfCloset.boards, board) { board = $0; SurfCloset.board = $0 }
                    section(String(localized: "모자"), SurfCloset.hats, hat) { hat = $0; SurfCloset.hat = $0 }
                }
                .padding(16)
            }
            .navigationTitle("꾸미기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("닫기") { dismiss() } } }
        }
    }

    /// 맨 위: 지금 누적 점수 / 다음 선물까지 남은 점수 (크게). 잠긴 칸을 누르면 그 조건을 한 줄로
    @ViewBuilder private var gift: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("누적 \(total)점").font(.title2.weight(.heavy)).monospacedDigit()
                Spacer()
                if let g = next { Text("다음까지 \(g.left)점").font(.headline).monospacedDigit() }
            }
            if let g = next {
                ProgressView(value: g.ratio)
                Text("다음 선물: \(g.item.name) · 파도 타기 점수가 쌓이면 저절로 열려요").font(.caption).foregroundStyle(.secondary)
            } else {
                Text("점수 선물 다 받음").font(.caption).foregroundStyle(.secondary)
            }
            if let hint { Text(hint).font(.footnote.weight(.semibold)).foregroundStyle(Color(hex: 0xD9480F)) }
        }
    }

    private static func hint(_ i: SurfItem) -> String {
        switch i.open {
        case .free: return ""
        case .score(let n): return String(localized: "\(String(describing: i.name)): 누적 \(String(describing: n))점이면 열려요")
        case .hardClear: return String(localized: "\(String(describing: i.name)): 어려움 코스를 끝까지 타면 열려요")
        case .fever(let n): return String(localized: "\(String(describing: i.name)): 피버 \(String(describing: n))번이면 열려요 (\(String(describing: SurfCloset.need(i))))")
        case .splash(let n): return String(localized: "\(String(describing: i.name)): 풍덩 \(String(describing: n))번이면 열려요 (\(String(describing: SurfCloset.need(i))))")
        case .snap(let n): return String(localized: "\(String(describing: i.name)): 딱! \(String(describing: n))번이면 열려요 (\(String(describing: SurfCloset.need(i))))")
        }
    }

    private func section(_ title: String, _ items: [SurfItem], _ sel: Int, _ pick: @escaping (Int) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.headline)
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(items) { i in
                    let open = opened.contains("\(title)\(i.id)")
                    Button { if open { pick(i.id); hint = nil } else { hint = Self.hint(i) } } label: {
                        VStack(spacing: 4) {
                            Text(i.name).font(.caption.weight(.semibold))
                            Text(open ? " " : SurfCloset.need(i)).font(.caption2).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.secondary.opacity(0.12)))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(sel == i.id ? Color.accentColor : .clear, lineWidth: 2))
                        .opacity(open ? 1 : 0.4)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}
