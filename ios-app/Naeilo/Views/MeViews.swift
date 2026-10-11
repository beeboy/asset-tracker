import SwiftUI

// 내 캐릭터 그리기·만들기 화면·쉼터 배경 고르기 (데이터와 열림 조건은 Data/Me.swift)

/// 인물 도트 하나: 기본 인물은 spr_/sil_ 그림, 내 캐릭터(me)는 부품을 겹쳐 그린다
struct CharPixel: View {
    let id: String
    var width: CGFloat
    var height: CGFloat
    var sil = false
    var body: some View {
        if id == "me" {
            if let me = MeChar.current { MeAvatar(me: me, width: width, height: height).opacity(sil ? 0.45 : 1) }
            else { MeEmpty(width: width, height: height) }
        } else {
            Pixel(name: (sil ? "sil_" : "spr_") + id, width: width, height: height)
        }
    }
}

/// 아직 만들지 않은 칸 (레이아웃만)
struct MeEmpty: View {
    var width: CGFloat
    var height: CGFloat
    var body: some View {
        RoundedRectangle(cornerRadius: 6)
            .stroke(Theme.dash, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
            .overlay(Image(systemName: "plus").font(.system(size: max(10, width * 0.35), weight: .bold)).foregroundStyle(Theme.muted))
            .frame(width: width, height: height)
            .accessibilityLabel("내 캐릭터 만들기")
    }
}

/// 내 캐릭터: 24×34 판에 부품을 아래부터 겹친다 (MeGroup.layers)
struct MeAvatar: View {
    let me: MeChar
    var width: CGFloat = 24
    var height: CGFloat = 34
    var body: some View {
        let color = MeColor.hair[MeColor.idx(me)], skin = MeColor.skin[MeColor.skinIdx(me)]
        ZStack {
            ForEach(MeGroup.layers, id: \.self) { layer in
                let n = MeArt.option(me, layer)
                if n > 0 { MeLayer(layer: layer, n: n, color: color, skin: skin) }
            }
        }
        .frame(width: width, height: height)
        .accessibilityLabel(me.shownName)
    }
}

private struct MeLayer: View {
    let layer: String
    let n: Int
    let color: UInt32
    let skin: UInt32
    var body: some View {
        let name = MeArt.asset(layer, n)
        if MeArt.has(name) {
            // 머리는 회색 단계 그림에 머리 색을, 얼굴은 피부색을 곱한다
            Image(name).interpolation(.none).resizable()
                .colorMultiply(layer == "hair" || layer == "hairb" ? Color(hex: color) : layer == "face" ? Color(hex: skin) : .white)
        } else {
            Canvas { ctx, size in MePlaceholder.draw(layer, n, color, skin, &ctx, size.width / 24) }
        }
    }
}

enum MeArt {
    static func asset(_ layer: String, _ n: Int) -> String { "me_\(layer)_" + String(format: "%02d", n) }
    /// 이 부품의 번호 (뒷머리는 헤어스타일 번호)
    static func option(_ me: MeChar, _ layer: String) -> Int {
        if layer == "hairb" { return me.part(.hair) }
        return MeGroup(rawValue: layer).map { me.part($0) } ?? 0
    }
    private static var known: [String: Bool] = [:]
    static func has(_ name: String) -> Bool {
        if let k = known[name] { return k }
        let k = UIImage(named: name) != nil
        known[name] = k
        return k
    }

    /// 위젯이 읽는 내 캐릭터 (me.json · me.png). 바뀐 때만 다시 쓴다
    private static var lastKey = ""
    @MainActor
    static func push(_ m: AppModel) {
        guard let me = m.me, m.meUnlocked else {
            if lastKey != "none" { Store.remove("me.json"); Store.remove("me.png"); lastKey = "none" }
            return
        }
        let i = MeColor.idx(me)
        let w = WMe(name: me.shownName, voice: m.meVoice, accent: MeColor.accent[i], g0: MeColor.g0, g1: MeColor.g1[i])
        let key = "\(me.hashValue)|\(w.voice)"
        guard key != lastKey else { return }
        let r = ImageRenderer(content: MeAvatar(me: me, width: 192, height: 272))
        r.scale = 1
        guard let png = r.uiImage?.pngData() else { return }
        Store.writeData(png, "me.png")
        Store.write(w, "me.json")
        lastKey = key
    }
}

/// 그림이 오기 전 자리표시 도트 (24×34 칸, u = 한 칸 크기)
enum MePlaceholder {
    private static let ink: UInt32 = 0x2A2230, white: UInt32 = 0xF8F9FA
    private static let tops: [UInt32] = [0x4C6EF5, 0xE8590C, 0x2F9E44, 0xF1F3F5, 0x343A40, 0xAE3EC9, 0xF59F00, 0x1C3F7A]
    private static let bottoms: [UInt32] = [0x2C3E6B, 0x4D7CC4, 0x2B2B33, 0xB59B6A, 0xC0392B, 0x868E96]

    static func draw(_ layer: String, _ n: Int, _ hair: UInt32, _ skin: UInt32, _ ctx: inout GraphicsContext, _ u: CGFloat) {
        func p(_ x: Int, _ y: Int, _ w: Int, _ h: Int, _ c: UInt32) {
            ctx.fill(Path(CGRect(x: CGFloat(x) * u, y: CGFloat(y) * u, width: CGFloat(w) * u, height: CGFloat(h) * u)), with: .color(Color(hex: c)))
        }
        let dark = shade(hair, 0.72)
        switch layer {
        case "hairb":
            switch n {
            case 3: p(5, 6, 14, 14, dark)
            case 4: p(5, 5, 14, 10, dark)
            case 7: p(18, 7, 3, 3, dark); p(19, 10, 3, 10, dark)
            default: break
            }
        case "face":
            let s = skin
            switch n {
            case 2: p(6, 5, 12, 11, s)
            case 3: p(8, 4, 8, 1, s); p(7, 5, 10, 11, s); p(8, 16, 8, 1, s)
            case 4: p(7, 6, 10, 1, s); p(5, 7, 14, 8, s); p(7, 15, 10, 1, s)
            default: p(8, 5, 8, 1, s); p(7, 6, 10, 1, s); p(6, 7, 12, 7, s); p(7, 14, 10, 1, s); p(8, 15, 8, 1, s)
            }
            p(5, 10, 1, 2, s); p(18, 10, 1, 2, s)          // 귀
            p(10, 16, 4, 1, shade(s, 0.9))                    // 목
        case "eyes":
            switch n {
            case 2: p(8, 10, 2, 2, ink); p(14, 10, 2, 2, ink); p(8, 10, 1, 1, white); p(14, 10, 1, 1, white)
            case 3: p(8, 11, 2, 1, ink); p(14, 11, 2, 1, ink)
            case 4: p(8, 10, 2, 1, ink); p(9, 11, 1, 1, ink); p(14, 10, 2, 1, ink); p(14, 11, 1, 1, ink)
            case 5: p(8, 10, 2, 2, 0x3A6FD8); p(14, 10, 2, 2, 0x3A6FD8); p(8, 10, 1, 1, white); p(14, 10, 1, 1, white)
            case 6: p(8, 9, 2, 3, ink); p(14, 9, 2, 3, ink); p(8, 9, 1, 1, white); p(14, 9, 1, 1, white)
            default: p(9, 10, 1, 2, ink); p(14, 10, 1, 2, ink)
            }
        case "mouth":
            switch n {
            case 2: p(10, 13, 4, 1, ink)
            case 3: p(11, 13, 2, 2, 0xB2454A)
            case 4: p(11, 13, 2, 1, 0xB2454A)
            case 5: p(10, 13, 4, 1, white); p(10, 14, 4, 1, ink)
            case 6: p(11, 13, 2, 2, ink)
            default: p(10, 12, 1, 1, ink); p(11, 13, 2, 1, ink); p(13, 12, 1, 1, ink)
            }
        case "hair":
            p(7, 3, 10, 1, dark); p(6, 4, 12, 2, hair)        // 정수리
            switch n {
            case 1: p(5, 5, 2, 4, hair); p(17, 5, 2, 4, hair); p(7, 6, 10, 1, hair)
            case 2: p(5, 5, 2, 4, hair); p(17, 5, 2, 3, hair); p(6, 6, 7, 2, hair); p(13, 6, 3, 1, hair)
            case 3: p(7, 6, 10, 2, hair); p(5, 5, 2, 11, hair); p(17, 5, 2, 11, hair)
            case 4: p(6, 6, 12, 1, hair); p(5, 5, 2, 9, hair); p(17, 5, 2, 9, hair)
            case 5: p(6, 2, 2, 1, hair); p(10, 1, 2, 2, hair); p(14, 2, 2, 1, hair); p(7, 6, 2, 2, hair); p(11, 6, 2, 1, hair); p(15, 6, 2, 2, hair)
            case 6: p(10, 0, 4, 3, hair); p(5, 5, 2, 4, hair); p(17, 5, 2, 4, hair); p(7, 6, 10, 1, hair)
            case 7: p(5, 5, 2, 5, hair); p(17, 5, 2, 4, hair); p(7, 6, 10, 1, hair); p(17, 7, 2, 2, dark)
            default: p(6, 4, 12, 1, dark)                      // 짧게 민 머리
            }
        case "top":
            let c = tops[(n - 1) % tops.count], d = shade(c, 0.78)
            p(6, 17, 12, 9, c); p(4, 17, 2, 7, d); p(18, 17, 2, 7, d)
            p(4, 24, 2, 2, skin); p(18, 24, 2, 2, skin)        // 손
            switch n {
            case 2: p(7, 16, 10, 2, d)                         // 후드
            case 4: p(11, 18, 2, 6, 0xC92A2A)                  // 넥타이
            case 5: p(11, 17, 2, 9, white)                     // 열린 재킷
            case 8: p(6, 19, 12, 1, white); p(6, 22, 12, 1, white)   // 줄무늬
            default: p(10, 17, 4, 1, d)
            }
        case "bottom":
            let c = bottoms[(n - 1) % bottoms.count]
            switch n {
            case 5: p(6, 26, 12, 4, c); p(8, 30, 3, 2, skin); p(13, 30, 3, 2, skin)          // 치마
            case 6: p(7, 26, 10, 3, c); p(8, 29, 3, 3, skin); p(13, 29, 3, 3, skin)          // 반바지
            default: p(7, 26, 10, 2, c); p(7, 28, 4, 4, c); p(13, 28, 4, 4, c)
            }
            p(7, 32, 4, 2, ink); p(13, 32, 4, 2, ink)          // 신발
        case "glasses":
            let c: UInt32 = [ink, 0xC9A227, 0xC92A2A, 0x222226][(n - 1) % 4]
            if n == 4 { p(7, 9, 4, 3, c); p(13, 9, 4, 3, c) }    // 선글라스
            else {
                for x in [7, 13] { p(x, 9, 4, 1, c); p(x, 12, 4, 1, c); p(x, 9, 1, 4, c); p(x + 3, 9, 1, 4, c) }
            }
            p(11, 10, 2, 1, c)
        case "mask":
            let c: UInt32 = [white, 0x212529, 0x96F2D7][(n - 1) % 3]
            p(7, 12, 10, 4, c); p(6, 12, 1, 1, shade(c, 0.7)); p(17, 12, 1, 1, shade(c, 0.7))
        case "prop":
            switch n {
            case 1: p(19, 21, 3, 4, white); p(22, 22, 1, 2, white); p(19, 21, 3, 1, 0x7A4A2E)     // 커피
            case 2: p(19, 19, 4, 6, 0xC0392B); p(19, 19, 1, 6, 0x8E2A20)                         // 책
            case 3: p(19, 21, 4, 4, 0xF2C230); p(20, 22, 2, 2, 0xD9A400)                         // 동전
            case 4: p(20, 24, 3, 2, 0x8D5A3B); p(19, 20, 2, 3, 0x2F9E44); p(21, 19, 2, 4, 0x40C057) // 화분
            case 5: p(20, 19, 2, 6, 0xF2C14E); p(18, 21, 6, 2, 0xF2C14E)                         // 별
            default: p(20, 20, 3, 5, 0x2B2B33); p(20, 21, 3, 3, 0x5FC9B0)                        // 휴대폰
            }
        default: break
        }
    }
    static func shade(_ c: UInt32, _ k: Double) -> UInt32 {
        let r = UInt32(Double((c >> 16) & 0xFF) * k), g = UInt32(Double((c >> 8) & 0xFF) * k), b = UInt32(Double(c & 0xFF) * k)
        return (r << 16) | (g << 8) | b
    }
}

// MARK: 만들기 화면

/// 내 캐릭터 만들기: 그룹(세로) · 그룹별 선택지(세로) · 캐릭터. 아래에 이름·투자 유형·저장 (저장 칸 1개)
struct MeEditView: View {
    @Environment(AppModel.self) private var m
    @State private var draft = MeChar()
    @State private var group: MeGroup = .hair
    @State private var loaded = false
    @State private var note: String? = nil
    @State private var askDelete = false

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 8) {
                groups.frame(width: 84)
                options.frame(width: 76)
                preview.frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 12).padding(.top, 8)
            .frame(maxHeight: .infinity)
            Divider().overlay(Theme.line)
            bottom.padding(12)
        }
        .background(Theme.bg)
        .navigationTitle("내 캐릭터").navigationBarTitleDisplayMode(.inline)
        .onAppear { if !loaded { draft = m.me ?? MeChar(); loaded = true } }
        .confirmationDialog("내 캐릭터를 지울까요?", isPresented: $askDelete, titleVisibility: .visible) {
            Button("지우기", role: .destructive) {
                m.me = nil; draft = MeChar()
                if m.homeFriend == "me" { m.homeFriend = "seri" }
                WidgetBridge.write(m)
                note = "지웠어요."
            }
        }
    }

    private var groups: some View {
        ScrollView {
            VStack(spacing: 4) {
                ForEach(MeGroup.allCases) { g in
                    let on = g == group
                    Button { group = g } label: {
                        Text(g.label).appFont(13, on ? .bold : .regular).lineLimit(1).minimumScaleFactor(0.8)
                            .foregroundStyle(on ? .white : Theme.ink)
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .background(on ? Theme.teal : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : Theme.border))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }

    private var options: some View {
        ScrollView {
            VStack(spacing: 6) {
                ForEach(group.options, id: \.self) { o in
                    let on = draft.picked(group, o)
                    Button { draft = draft.with(group, o) } label: {
                        VStack(spacing: 2) {
                            if group == .color && o > 100 {
                                Circle().fill(Color(hex: MeColor.skin[o - 101])).frame(width: 34, height: 34)
                                    .overlay(Circle().stroke(Theme.border))
                                Text(MeColor.skinNames[o - 101]).appFont(11).lineLimit(1).minimumScaleFactor(0.7)
                            } else if group == .color {
                                Circle().fill(Color(hex: MeColor.hair[o - 1])).frame(width: 34, height: 34)
                                    .overlay(Circle().stroke(Theme.border))
                                Text(MeColor.names[o - 1]).appFont(11)
                            } else if o == 0 {
                                Image(systemName: "nosign").font(.system(size: 22)).foregroundStyle(Theme.muted).frame(height: 48)
                                Text("없음").appFont(11)
                            } else {
                                MeAvatar(me: draft.with(group, o), width: 34, height: 48)
                                Text("\(o)").appFont(11).foregroundStyle(Theme.sub)
                            }
                        }
                        .foregroundStyle(Theme.ink)
                        .frame(maxWidth: .infinity, minHeight: 72)
                        .background(on ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 2 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(group == .color ? (o > 100 ? MeColor.skinNames[o - 101] : MeColor.names[o - 1]) : o == 0 ? "없음" : "\(group.label) \(o)")
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }

    private var preview: some View {
        VStack(spacing: 10) {
            MeAvatar(me: draft, width: 96, height: 136)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(Theme.shelter, in: RoundedRectangle(cornerRadius: 14))
            Text(draft.shownName).appFont(17, .bold)
            Text(m.meUnlocked ? "홈과 위젯에 둘 수 있어요" : "조건을 채우면 홈과 위젯에 나와요")
                .appFont(11).foregroundStyle(Theme.sub).multilineTextAlignment(.center)
            Spacer(minLength: 0)
        }
    }

    private var bottom: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                TextField("이름 (\(MeChar.nameMax)자까지)", text: $draft.name)
                    .appFont(15).padding(.horizontal, 12).frame(minHeight: 44)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                    .onChange(of: draft.name) { _, v in if v.count > MeChar.nameMax { draft.name = String(v.prefix(MeChar.nameMax)) } }
                Text("\(draft.name.count)/\(MeChar.nameMax)").appFont(12).foregroundStyle(Theme.muted)
            }
            typeRow
            HStack(spacing: 8) {
                Button {
                    draft.name = draft.name.trimmingCharacters(in: .whitespaces)
                    m.me = draft
                    WidgetBridge.write(m)
                    note = m.meUnlocked ? "저장했어요." : "저장했어요. 조건을 채우면 홈과 위젯에 나와요."
                } label: {
                    Text(m.me == nil ? "저장하기" : "저장하기 (바꾸기)").appFont(15, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 46).background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                if m.me != nil {
                    Button("지우기") { askDelete = true }.appFont(14).foregroundStyle(Theme.muted).frame(minHeight: 46)
                }
            }
            if let note { Text(note).appFont(13, .bold).foregroundStyle(Theme.teal) }
        }
    }

    /// 투자 유형: 평정 지수가 매칭되기 전에는 고르고, 매칭되면 그 유형을 따른다 (말투도 그 인물)
    @ViewBuilder private var typeRow: some View {
        if let t = m.calm.type, let k = Calm.kind(t) {
            Text("투자 유형 · \(k.title) (평정 지수 매칭, \(m.friendName(t)) 말투)").appFont(13).foregroundStyle(Theme.sub)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Text("투자 유형 (평정 지수가 매칭되면 그 유형을 따라요)").appFont(12).foregroundStyle(Theme.sub)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Calm.kinds, id: \.id) { k in
                            let on = draft.type == k.id
                            Button { draft.type = k.id } label: {
                                Text(k.title).appFont(13, on ? .bold : .regular).padding(.horizontal, 12).frame(minHeight: 34)
                                    .foregroundStyle(on ? .white : Theme.ink)
                                    .background(on ? Theme.teal : Theme.card, in: Capsule())
                                    .overlay(Capsule().stroke(on ? Theme.teal : Theme.border))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(k.title), \(m.friendName(k.id)) 말투")
                        }
                    }
                }
            }
        }
    }
}

// MARK: 쉼터 방 그림

/// 쉼터 방 바탕: 고른 배경(bg_NN, 라이트·다크), 없으면 기본 방. 그림이 오기 전에는 기본 방을 색만 바꿔 보여 준다
struct RoomImage: View {
    let bg: String?
    var body: some View {
        if let bg, MeArt.has(bg) {
            Image(bg).interpolation(.none).resizable()
        } else {
            let i = bg.flatMap { id in Shelter.backgrounds.firstIndex { $0.id == id } }
            Image("art_room").interpolation(.none).resizable().hueRotation(.degrees(Double((i ?? -1) + 1) * 30))
        }
    }
}

/// 쉼터 아래 '쉼터 배경 변경' 상자 (접어 둠). 프랜차이즈 커피 이상이면 고를 수 있다
struct ShelterBGBox: View {
    @Environment(AppModel.self) private var m
    @Binding var support: Bool
    @State private var open = false

    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation(.easeOut(duration: 0.2)) { open.toggle() } } label: {
                HStack(spacing: 8) {
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.teal)
                        .rotationEffect(.degrees(open ? 90 : 0))
                    Text("쉼터 배경 변경").appFont(14, .bold)
                    Spacer(minLength: 0)
                    Text(m.bgUnlocked ? (Shelter.background(m.meBg)?.name ?? "기본 쉼터") : "☕︎ 프랜차이즈 커피").appFont(12).foregroundStyle(Theme.muted).lineLimit(1)
                }
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 14).frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("쉼터 배경 변경, \(open ? "펼침" : "접힘")")
            if open {
                Divider().overlay(Theme.line)
                row(nil, "기본 쉼터", "돌아온 물건이 놓이는 처음 방")
                ForEach(Shelter.backgrounds) { b in
                    Divider().overlay(Theme.line)
                    row(b.id, b.name, b.intro)
                }
            }
        }
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private func row(_ id: String?, _ name: String, _ intro: String) -> some View {
        let locked = !m.bgUnlocked, on = !locked && m.meBg == id
        return Button { if locked { support = true } else { m.meBg = id } } label: {
            HStack(spacing: 10) {
                RoomImage(bg: id).frame(width: 64, height: 40).clipShape(RoundedRectangle(cornerRadius: 6))
                    .grayscale(locked && id != nil ? 1 : 0).opacity(locked && id != nil ? 0.6 : 1)
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).appFont(14, .bold)
                    Text(intro).appFont(12).foregroundStyle(Theme.sub).lineLimit(2)
                }
                Spacer(minLength: 0)
                if on { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.teal) }
                else if locked && id != nil { Image(systemName: "lock.fill").font(.system(size: 12)).foregroundStyle(Theme.muted) }
            }
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 14).frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(name + (on ? ", 지금 배경" : locked ? ", 잠김" : ""))
    }
}
