import SwiftUI

// 내 캐릭터 그리기·만들기 화면·쉼터 배경 고르기 (데이터와 열림 조건은 Data/Me.swift)

/// 인물 도트 하나: 기본 인물은 spr_/sil_ 그림, 내 캐릭터(me)는 부품을 겹쳐 그린다
struct CharPixel: View {
    let id: String
    var width: CGFloat
    var height: CGFloat
    var sil = false
    /// 앱 안에서는 모델을 지켜봐야 저장 직후 다시 그린다 (공유 그림처럼 모델이 없는 곳은 MeChar.current)
    @Environment(AppModel.self) private var model: AppModel?
    var body: some View {
        if id == "me" {
            if let me = model?.me ?? MeChar.current { MeAvatar(me: me, width: width, height: height).opacity(sil ? 0.45 : 1) }
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

/// 내 캐릭터: 24×34 판에 부품을 아래부터 겹치고 바깥 테두리를 한 줄 두른 그림 (MeArt.image), 최근접 확대
struct MeAvatar: View {
    let me: MeChar
    var width: CGFloat = 24
    var height: CGFloat = 34
    var body: some View {
        Group {
            if let img = MeArt.image(me) { Image(uiImage: img).interpolation(.none).resizable() } else { Color.clear }
        }
        .frame(width: width, height: height)
        .accessibilityLabel(me.shownName)
    }
}

enum MeArt {
    static let W = 24, H = 34
    static let outline: UInt32 = 0x2A1E2E      // 기본 스프라이트와 같은 바깥 테두리 색

    /// 부품 그림 이름 (이미지 디자인 스레드와 맞춘 이름). 피부·머리는 색을 미리 칠한 그림
    /// body: me_body_<피부> · face: me_face_<모양>_<피부> · hair: me_hair_<모양>_<머리색> · 나머지: me_<그룹>_<번호>
    static func asset(_ layer: String, _ n: Int, _ me: MeChar) -> String {
        let f = { (x: Int) in String(format: "%02d", x) }
        switch layer {
        case "body": return "me_body_" + f(me.skin)
        case "face": return "me_face_\(f(n))_\(f(me.skin))"
        case "hair": return "me_hair_\(f(n))_\(f(me.part(.color)))"
        default: return "me_\(layer)_" + f(n)
        }
    }
    /// 이 부품의 번호 (몸은 늘 있다)
    static func option(_ me: MeChar, _ layer: String) -> Int {
        if layer == "body" { return 1 }
        return MeGroup(rawValue: layer).map { me.part($0) } ?? 0
    }

    private static var known: [String: Bool] = [:]
    /// 에셋이 있는지 (쉼터 배경 그림 등)
    static func has(_ name: String) -> Bool {
        if let k = known[name] { return k }
        let k = UIImage(named: name) != nil
        known[name] = k
        return k
    }

    private static var cache: [MeChar: UIImage] = [:]
    /// 1배로 겹친 뒤 '투명 칸 중 상하좌우에 칠한 칸이 있으면 테두리색' 한 번 (부품에는 테두리가 없다)
    static func image(_ me: MeChar) -> UIImage? {
        if let c = cache[me] { return c }
        let hair = MeColor.hair[MeColor.idx(me)], skin = MeColor.skin[MeColor.skinIdx(me)]
        let w = W, h = H, line = outline
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let cg: CGImage? = px.withUnsafeMutableBytes { buf -> CGImage? in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
            ctx.interpolationQuality = .none
            for layer in MeGroup.layers {
                let n = option(me, layer)
                guard n > 0 else { continue }
                if let img = UIImage(named: asset(layer, n, me))?.cgImage {
                    ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
                } else {
                    // 그림이 오기 전: 자리표시 도트 (위에서부터 센 y 를 CG 좌표로)
                    MePlaceholder.draw(layer, n, hair, skin) { x, y, pw, ph, c in
                        ctx.setFillColor(red: CGFloat((c >> 16) & 0xFF) / 255, green: CGFloat((c >> 8) & 0xFF) / 255, blue: CGFloat(c & 0xFF) / 255, alpha: 1)
                        ctx.fill(CGRect(x: x, y: h - y - ph, width: pw, height: ph))
                    }
                }
            }
            // 바깥 테두리 (버퍼는 위 줄부터)
            let p = buf.bindMemory(to: UInt8.self)
            let alpha: [UInt8] = (0..<(w * h)).map { p[$0 * 4 + 3] }
            for y in 0..<h {
                for x in 0..<w where alpha[y * w + x] == 0 {
                    let left = x > 0 && alpha[y * w + x - 1] > 0, right = x < w - 1 && alpha[y * w + x + 1] > 0
                    let up = y > 0 && alpha[(y - 1) * w + x] > 0, down = y < h - 1 && alpha[(y + 1) * w + x] > 0
                    guard left || right || up || down else { continue }
                    let i = (y * w + x) * 4
                    p[i] = UInt8((line >> 16) & 0xFF); p[i + 1] = UInt8((line >> 8) & 0xFF); p[i + 2] = UInt8(line & 0xFF); p[i + 3] = 255
                }
            }
            return ctx.makeImage()
        }
        guard let cg else { return nil }
        let img = UIImage(cgImage: cg)
        if cache.count > 80 { cache.removeAll() }
        cache[me] = img
        return img
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

    static func draw(_ layer: String, _ n: Int, _ hair: UInt32, _ skin: UInt32, _ fill: (Int, Int, Int, Int, UInt32) -> Void) {
        func p(_ x: Int, _ y: Int, _ w: Int, _ h: Int, _ c: UInt32) { fill(x, y, w, h, c) }
        let dark = shade(hair, 0.72)
        switch layer {
        case "body":
            p(10, 16, 4, 1, shade(skin, 0.9))                 // 목
            p(4, 24, 2, 2, skin); p(18, 24, 2, 2, skin)        // 손
            p(8, 28, 3, 4, skin); p(13, 28, 3, 4, skin)        // 다리
        case "face":
            let s = skin
            switch n {
            case 2: p(6, 5, 12, 11, s)
            case 3: p(8, 4, 8, 1, s); p(7, 5, 10, 11, s); p(8, 16, 8, 1, s)
            case 4: p(7, 6, 10, 1, s); p(5, 7, 14, 8, s); p(7, 15, 10, 1, s)
            default: p(8, 5, 8, 1, s); p(7, 6, 10, 1, s); p(6, 7, 12, 7, s); p(7, 14, 10, 1, s); p(8, 15, 8, 1, s)
            }
            p(5, 10, 1, 2, s); p(18, 10, 1, 2, s)          // 귀
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
            case 5: p(6, 26, 12, 4, c)          // 치마
            case 6: p(7, 26, 10, 3, c)          // 반바지
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
    @Environment(\.dismiss) private var dismiss
    @State private var draft = MeChar()
    @State private var group: MeGroup = .hair
    @State private var loaded = false
    @State private var note: String? = nil
    @State private var askDelete = false
    @State private var samples = false           // 샘플(소설 속 인물) 목록을 보는 중
    @State private var sample: MeSample? = nil   // 오른쪽 위에 크게 보이는 샘플 (처음엔 빈 칸)

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            left.frame(width: 84)
            options.frame(width: 76)
            right.frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 12).padding(.top, 8)
        .background(Theme.bg)
        .navigationTitle("내 캐릭터").navigationBarTitleDisplayMode(.inline)
        .onAppear { if !loaded { draft = m.me ?? MeChar(); loaded = true } }
        .confirmationDialog("처음부터 다시 만들까요?", isPresented: $askDelete, titleVisibility: .visible) {
            Button("리셋", role: .destructive) {
                // 리셋: 저장한 캐릭터도 지우고 빈 칸으로
                if m.me != nil {
                    m.me = nil
                    if m.homeFriend == "me" { m.homeFriend = "seri" }
                    WidgetBridge.write(m)
                }
                draft = MeChar(); sample = nil; note = nil
            }
        } message: {
            Text(m.me != nil ? "저장한 내 캐릭터도 지워져요." : "고른 부품과 이름이 지워져요.")
        }
    }

    /// 왼쪽: 샘플 · 부품 그룹, 그 아래 투자 유형 (보라색)
    private var left: some View {
        ScrollView {
            VStack(spacing: 4) {
                Button { samples = true } label: {
                    Text("샘플").appFont(13, samples ? .bold : .regular)
                        .foregroundStyle(samples ? .white : Theme.ink)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(samples ? Theme.teal : Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(samples ? Theme.teal : Theme.border))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("샘플, 소설 속 인물 조합")
                .accessibilityAddTraits(samples ? .isSelected : [])
                ForEach(MeGroup.allCases) { g in
                    let on = !samples && g == group
                    Button { group = g; samples = false } label: {
                        Text(g.label).appFont(13, on ? .bold : .regular).lineLimit(1).minimumScaleFactor(0.8)
                            .foregroundStyle(on ? .white : Theme.ink)
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .background(on ? Theme.teal : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : Theme.border))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
                types.padding(.top, 14)
            }
            .padding(.bottom, 12)
        }
    }

    /// 투자 유형: 평정 지수가 매칭되기 전에는 고르고, 매칭되면 그 유형만 켜진다 (말투도 그 인물)
    private var types: some View {
        let matched = m.calm.type
        let cur = matched ?? draft.type
        return VStack(alignment: .leading, spacing: 4) {
            Text("투자 유형").appFont(13, .bold).foregroundStyle(Theme.purple)
            ForEach(Calm.kinds, id: \.id) { k in
                let on = cur == k.id
                Button { draft.type = k.id } label: {
                    Text(k.title + (on ? " ✓" : "")).appFont(12, on ? .bold : .regular)
                        .lineLimit(1).minimumScaleFactor(0.7)
                        .foregroundStyle(on ? .white : Theme.purple)
                        .frame(maxWidth: .infinity, minHeight: 34).padding(.horizontal, 4)
                        .background(on ? Theme.purple : Theme.purple.opacity(0.12), in: Capsule())
                        .overlay(Capsule().stroke(Theme.purple.opacity(on ? 1 : 0.4)))
                }
                .buttonStyle(.plain)
                .disabled(matched != nil)
                .opacity(matched != nil && !on ? 0.4 : 1)
                .accessibilityLabel("\(k.title), \(m.friendName(k.id)) 말투")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    /// 샘플: 소설 속 인물 조합. 누르면 오른쪽 위에 크게 보이고, '적용'을 눌러야 내 캐릭터에 입힌다
    private var sampleList: some View {
        ScrollView {
            VStack(spacing: 6) {
                ForEach(MeSample.all) { x in
                    let on = sample?.id == x.id
                    Button { sample = x } label: {
                        VStack(spacing: 2) {
                            MeAvatar(me: MeChar(parts: x.parts), width: 34, height: 48)
                            Text(x.name).appFont(10).lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.7)
                        }
                        .foregroundStyle(Theme.ink)
                        .frame(maxWidth: .infinity, minHeight: 80).padding(.horizontal, 2)
                        .background(on ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 2.5 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(x.name)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }

    @ViewBuilder private var options: some View {
        if samples { sampleList } else { partList }
    }

    private var partList: some View {
        ScrollView {
            VStack(spacing: 6) {
                ForEach(group.options, id: \.self) { o in
                    let on = draft.picked(group, o)
                    Button { draft = draft.with(group, o) } label: {
                        VStack(spacing: 2) {
                            if group == .color && o > 100 {
                                Circle().fill(Color(hex: MeColor.skin[o - 101])).frame(width: 34, height: 34)
                                    .overlay(Circle().stroke(Theme.border))
                                Text(group.name(o)).appFont(11).lineLimit(1).minimumScaleFactor(0.7)
                            } else if group == .color {
                                Circle().fill(Color(hex: MeColor.hair[o - 1])).frame(width: 34, height: 34)
                                    .overlay(Circle().stroke(Theme.border))
                                Text(group.name(o)).appFont(11).lineLimit(1).minimumScaleFactor(0.7)
                            } else if o == 0 {
                                Image(systemName: "nosign").font(.system(size: 22)).foregroundStyle(Theme.muted).frame(height: 48)
                                Text("없음").appFont(11)
                            } else {
                                MeAvatar(me: draft.with(group, o), width: 34, height: 48)
                                Text(group.name(o)).appFont(10).foregroundStyle(Theme.sub)
                                    .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.7)
                            }
                        }
                        .foregroundStyle(Theme.ink)
                        .frame(maxWidth: .infinity, minHeight: 72).padding(.horizontal, 2)
                        .background(on ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 2.5 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(group == .color || o == 0 ? group.name(o) : "\(group.label) \(group.name(o))")
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }

    /// 오른쪽: 위는 고른 샘플(소개·적용), 아래는 내 캐릭터(같은 크기)와 이름·저장. 둘 다 가운데.
    /// 그림 크기는 칸의 폭·높이에 맞춰 24×34 의 정수배(3~6배)로 바뀐다
    private var right: some View {
        GeometryReader { geo in
            let s = scale(geo.size)
            ScrollView {
                VStack(spacing: 0) {
                    if let x = sample {
                        big(MeChar(parts: x.parts), s).padding(.top, 14)
                        VStack(spacing: 4) {
                            Text(x.name).appFont(15, .bold)
                            Text(x.about).appFont(11).foregroundStyle(Theme.sub)
                            Text(parts(x)).appFont(10).foregroundStyle(Theme.muted)
                        }
                        .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 14)
                        let applied = draft.parts == x.parts
                        Button { draft.parts = x.parts } label: {
                            Text(applied ? "적용했어요" : "적용 ↓").appFont(15, .bold).foregroundStyle(.white)
                                .frame(width: boxW(s), height: 42)
                                .background(applied ? Theme.muted : Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)
                        .disabled(applied)
                        .padding(.top, 10)
                    } else {
                        empty(s).padding(.top, 14)
                        Text("왼쪽 샘플에 『중첩된 현실』 시리즈의 주요 인물들이 있어요. 골라서 적용해 보세요.")
                            .appFont(11).foregroundStyle(Theme.sub).multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true).padding(.top, 14)
                    }
                    // 처음(저장한 것도, 고른 부품도 없을 때)에는 빈 칸
                    Group {
                        if draft.parts.isEmpty && m.me == nil { empty(s) } else { big(draft, s) }
                    }
                    .padding(.top, 28)
                    Text("나만의 캐릭터를 만들어 홈과 위젯에서 함께하세요")
                        .appFont(11).foregroundStyle(Theme.sub).multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true).padding(.top, 8)
                    nameRow.padding(.top, 10)
                    if let note { Text(note).appFont(13, .bold).foregroundStyle(Theme.teal).padding(.top, 8) }
                    Button { askDelete = true } label: {
                        Label("리셋", systemImage: "arrow.counterclockwise").appFont(13).foregroundStyle(Theme.muted)
                            .frame(minHeight: 40).padding(.horizontal, 12)
                    }
                    .buttonStyle(.plain)
                    .disabled(m.me == nil && draft == MeChar() && sample == nil)
                    .padding(.top, 4)
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 12)
            }
        }
    }

    /// 그림 배율: 폭에 들어가고, 두 그림과 글·버튼이 한 화면에 들어가는 가장 큰 정수배
    private func scale(_ size: CGSize) -> Int {
        let byW = Int((size.width - 20) / CGFloat(MeArt.W))
        let byH = Int((size.height - 330) / CGFloat(MeArt.H * 2))
        return max(3, min(6, byW, byH))
    }
    private func boxW(_ s: Int) -> CGFloat { CGFloat(MeArt.W * s) + 20 }

    /// 그림 자리 빈 칸 (같은 크기, 점선)
    private func empty(_ s: Int) -> some View {
        RoundedRectangle(cornerRadius: 14)
            .stroke(Theme.dash, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            .frame(width: boxW(s), height: CGFloat(MeArt.H * s) + 20)
    }

    /// 고른 샘플·내 캐릭터 같은 크기 (24×34 의 s배)
    private func big(_ c: MeChar, _ s: Int) -> some View {
        MeAvatar(me: c, width: CGFloat(MeArt.W * s), height: CGFloat(MeArt.H * s))
        .padding(10)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
        .accessibilityLabel(c.shownName)
    }

    /// 샘플 구성 한 줄: 머리·색·옷·소품 이름
    private func parts(_ x: MeSample) -> String {
        let c = MeChar(parts: x.parts)
        return MeGroup.allCases.compactMap { g -> String? in
            let o = g == .color ? c.part(.color) : c.part(g)
            if g.optional && o == 0 { return nil }
            return g == .color ? "\(g.name(o)) 머리 · \(MeColor.skinNames[MeColor.skinIdx(c)])" : g.name(o)
        }.joined(separator: " · ")
    }

    private var nameRow: some View {
        HStack(spacing: 6) {
            TextField("이름 (\(MeChar.nameMax)자)", text: $draft.name)
                .appFont(16).padding(.horizontal, 10).frame(minHeight: 44)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                // 한글은 조합 중에 바로 바꾸면 무시돼서, 한 박자 뒤에 다듬는다 (저장할 때도 한 번 더)
                .onChange(of: draft.name) { _, v in
                    let c = MeChar.clean(v)
                    if c != v { DispatchQueue.main.async { draft.name = MeChar.clean(draft.name) } }
                }
                .onSubmit { draft.name = MeChar.clean(draft.name) }
            Button {
                draft.name = MeChar.clean(draft.name).trimmingCharacters(in: .whitespaces)
                m.me = draft
                WidgetBridge.write(m)
                dismiss()   // 저장하면 바로 이전 화면으로
            } label: {
                Text("저장").appFont(16, .bold).foregroundStyle(.white)
                    .frame(width: 56, height: 44).background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
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
                    row(b.id, b.name, b.intro, b.volume)
                }
            }
        }
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private func row(_ id: String?, _ name: String, _ intro: String, _ volume: String? = nil) -> some View {
        let locked = !m.bgUnlocked, on = !locked && m.meBg == id
        return Button { if locked { support = true } else { m.meBg = id } } label: {
            HStack(spacing: 10) {
                RoomImage(bg: id).frame(width: 64, height: 40).clipShape(RoundedRectangle(cornerRadius: 6))
                    .grayscale(locked && id != nil ? 1 : 0).opacity(locked && id != nil ? 0.6 : 1)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(name).appFont(14, .bold)
                        if let volume { Text(volume).appFont(11).foregroundStyle(Theme.muted) }
                    }
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
        .accessibilityLabel((volume.map { $0 + " " } ?? "") + name + (on ? ", 지금 배경" : locked ? ", 잠김" : ""))
    }
}

/// 둥근 머리만 (사용량 잠금 화면 위젯과 같은 자르기: 얼굴 그림을 위쪽 맞춤으로 키워 동그랗게)
struct CharHead: View {
    let id: String
    var size: CGFloat
    @Environment(AppModel.self) private var model: AppModel?
    var body: some View {
        let s = size / 31
        Group {
            if id == "me" {
                if let me = model?.me ?? MeChar.current { MeAvatar(me: me, width: 40 * s, height: 58 * s) }
                else { Color.clear }
            } else {
                // 원래 색 캐릭터 그림에서 머리만 잘라 씀 (u_*_face는 잠금화면 위젯용 색)
                Image("spr_\(id)").interpolation(.none).resizable().aspectRatio(contentMode: .fit)
                    .frame(width: 40 * s, height: 58 * s)
            }
        }
        .frame(width: 40 * s, height: 40 * s, alignment: .top)
        .offset(y: 3 * s)
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}
