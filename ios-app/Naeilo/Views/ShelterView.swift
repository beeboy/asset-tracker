import SwiftUI

// 쉼터: 친구, 서재(외전 프롤로그~5장, 후원하면 6장~코다와 본편 1권), 돌아온 물건. 시장 숫자와는 상관없고 내 행동으로만 열린다.
struct ShelterView: View {
    @Environment(AppModel.self) private var m
    @State private var sel = "seri"
    @State private var item: String? = nil
    @State private var support = false
    @State private var story = Story.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("쉼터").appFont(22, .bold)
                hero
                header("친구", "\((0..<5).filter { m.friendOn($0) }.count)/5")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                    ForEach(Array(Shelter.friends.enumerated()), id: \.element.id) { i, f in friendTile(i, f) }
                }
                header("서재 · 외전 『이종 공명』", "\((0..<6).filter { m.chapterOn($0) }.count)/6장 열림")
                if let last = m.readLast, m.chapterOn(last) {
                    NavigationLink(value: "read:\(last)") {
                        HStack { Text("이어 읽기 · \(Shelter.chapters[last].title) \(Shelter.chapters[last].name)"); Spacer(); Text("›") }
                            .appFont(15, .bold).foregroundStyle(.white)
                            .padding(.horizontal, 14).frame(minHeight: 48)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain)
                }
                library
                if Support.shared.has(.franchise) { vol1 }
                header("쉼터에 돌아온 물건", "\(m.itemsOn)/10")
                room
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                    ForEach(Array(Shelter.items.enumerated()), id: \.element.id) { i, it in itemTile(i, it) }
                }
                itemNote
                Text("외전 프롤로그부터 5장까지는 내 행동으로 열리고, 6장부터는 커피 후원으로 이어져요. 친구와 장은 내 행동으로만 열리고, 시장 숫자와는 상관없어요. 물건은 오늘의 1분을 7일 연속 할 때마다 하나씩 돌아오고, 연속이 끊겨도 돌아온 물건은 그대로 있어요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("").navigationBarTitleDisplayMode(.inline)
        .onAppear { sel = m.shelterSel ?? m.homeFriend }
        .sheet(isPresented: $support) { NavigationStack { SupportView() } }
    }

    private func header(_ t: String, _ r: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(t).appFont(15, .bold); Spacer()
            Text(r).appFont(13).foregroundStyle(Theme.sub)
        }
    }

    private var hero: some View {
        let i = Shelter.friends.firstIndex { $0.id == sel } ?? 0, f = Shelter.friends[i], real = m.friendOn(i)
        let on = real || m.peek     // 다방커피 이상은 못 만난 친구도 미리 보기 (홈에 두기는 만나야)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 84, height: 119).opacity(real ? 1 : on ? 0.75 : 1)
                    .padding(.horizontal, 10).padding(.vertical, 8)
                    .background(Shelter.spriteBacking(f.id), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 6) {
                    Text(on ? f.name : "???").appFont(20, .bold)
                    Text(real ? f.appearsText : on ? "미리 보기 · 인터미션 \(i)주차에 만나요" : "인터미션 \(i)주차에 만나요").appFont(12).foregroundStyle(Color(hex: 0x8FD0FF))
                        .fixedSize(horizontal: false, vertical: true)
                    Text(on ? f.bio : "아직 만나지 않았어요. 인터미션 \(i)주차 체크인: \(Shelter.weekSteps[max(0, i - 1)].task).")
                        .appFont(13).lineSpacing(3).foregroundStyle(Color(hex: 0xD5D9E6))
                        .fixedSize(horizontal: false, vertical: true)
                    if on { Text(f.line).appFont(13, .bold).foregroundStyle(Theme.gold) }
                }
                Spacer(minLength: 0)
            }
            if on {
                HStack(spacing: 8) {
                    NavigationLink(value: "char:" + f.id) {
                        Text("자세히 보기 ›").appFont(13, .bold).foregroundStyle(Color(hex: 0xEEF0F7))
                            .padding(.horizontal, 14).frame(minHeight: 40)
                            .overlay(Capsule().stroke(Color(hex: 0x8FD0FF), lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                    if real && m.homeFriend != f.id {
                        Button("홈에 두기") { m.homeFriend = f.id }
                            .appFont(13, .bold).foregroundStyle(Theme.inkFixed)
                            .padding(.horizontal, 14).frame(minHeight: 40).background(Theme.mint, in: Capsule())
                    } else if real {
                        Text("홈에 있어요").appFont(12, .bold).foregroundStyle(Theme.inkFixed)
                            .padding(.horizontal, 10).padding(.vertical, 4).background(Theme.gold, in: Capsule())
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .foregroundStyle(Color(hex: 0xEEF0F7))
        .padding(16)
        .background(Theme.shelter, in: RoundedRectangle(cornerRadius: 20))
    }

    private func friendTile(_ i: Int, _ f: Friend) -> some View {
        let on = m.friendOn(i), cur = sel == f.id
        return Button { sel = f.id } label: {
            VStack(spacing: 2) {
                Pixel(name: (on || m.peek ? "spr_" : "sil_") + f.id, width: 34, height: 48).opacity(on ? 1 : m.peek ? 0.6 : 1)
                Text(on || m.peek ? f.name : "???").appFont(12, .bold)
                Text(on && m.homeFriend == f.id ? "홈에 있음" : i == 0 ? "처음부터" : "\(i)주차")
                    .appFont(10).foregroundStyle(Theme.sub)
            }
            .frame(maxWidth: .infinity, minHeight: 96)
            .background(cur ? Theme.mintBg : on ? (f.id == "ir" || f.id == "sua" ? Color(hex: 0xFFFFFF, dark: 0x2C3846) : Theme.card) : Color(hex: 0xF6F7F8, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(cur ? Theme.teal : on ? Theme.border : Theme.track, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? f.name : "잠긴 친구")
    }

    private var library: some View {
        VStack(spacing: 0) {
            ForEach(0..<6, id: \.self) { i in
                let on = m.chapterOn(i), ch = Shelter.chapters[i]
                let fi = Shelter.friends.firstIndex { $0.id == ch.friend } ?? 0
                let row = HStack(spacing: 10) {
                    ChapterCover(index: i, on: on).frame(width: 64, height: 40)
                    Text(ch.title).appFont(12, .bold).foregroundStyle(Theme.teal).frame(width: 44, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(on ? ch.name : "???").appFont(14, .bold)
                        Text(on ? (i < 2 ? "앱 시작 3단계에서 열림" : "인터미션 \(fi)주차에 열림")
                             : (i < 2 ? "앱 시작 3단계를 마치면 열려요" : "인터미션 \(fi)주차가 되면 열려요"))
                            .appFont(12).foregroundStyle(Theme.sub)
                    }
                    Spacer(minLength: 0)
                    Text(on ? (m.readPos.contains(i) ? "이어 읽기" : "읽기") : "잠김").appFont(12).foregroundStyle(Theme.muted)
                }
                .padding(.horizontal, 14).frame(minHeight: 64)
                .foregroundStyle(on ? Theme.ink : Theme.muted)
                .contentShape(Rectangle())
                if i > 0 { Divider().overlay(Theme.line) }
                if on { NavigationLink(value: "read:\(i)") { row }.buttonStyle(.plain) }
                else { Button { sel = ch.friend } label: { row }.buttonStyle(.plain) }
            }
            Divider().overlay(Theme.line)
            // 6장부터: 믹스커피 이상이면 후원 서버에서 받은 장, 아니면 누르면 개발자 후원(커피)
            if Support.shared.has(.mix) && story.count(.side) > 6 {
                ForEach(6..<story.count(.side), id: \.self) { i in
                    if i > 6 { Divider().overlay(Theme.line) }
                    NavigationLink(value: "read:\(i)") { paidRow(i == 6 ? "art_ch6" : nil, story.chapter(i)?.title ?? "", story.chapter(i)?.name ?? "", (story.pos[i] ?? 0) > 0 ? "이어 읽기" : "읽기") }
                        .buttonStyle(.plain)
                }
            } else if Support.shared.has(.mix) {
                Button { Task { await story.fetch(.side, force: true) } } label: {
                    paidRow("art_ch6", "6장", Shelter.chapter6Name, story.isLoading(.side) ? "받는 중…" : story.failText(.side) == nil ? "받기" : "다시 받기",
                            note: story.failText(.side))
                }
                .buttonStyle(.plain).disabled(story.isLoading(.side))
            } else {
                Button { support = true } label: {
                    paidRow("art_ch6", "6장", Shelter.chapter6Name, "☕︎ ›", note: "커피 한 잔으로 이어 읽기", locked: true)
                }
                .buttonStyle(.plain)
            }
        }
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        .task(id: story.lang) { await story.fetch(.side) }   // 믹스커피 이상이면 6장부터 받아 둔다
    }

    /// 서버에서 받는 장 한 줄 (6장부터, 본편 1권)
    private func paidRow(_ art: String?, _ title: String, _ name: String, _ right: String, note: String? = nil, locked: Bool = false) -> some View {
        HStack(spacing: 10) {
            Group {
                if let art {
                    Image(art).interpolation(.none).resizable()
                        .grayscale(locked ? 1 : 0).brightness(locked ? -0.3 : 0).opacity(locked ? 0.6 : 1)
                } else { Theme.shelter }
            }
            .frame(width: 64, height: 40).clipShape(RoundedRectangle(cornerRadius: 6))
            Text(title).appFont(12, .bold).foregroundStyle(Theme.teal).frame(width: 44, alignment: .leading).lineLimit(1).minimumScaleFactor(0.7)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).appFont(14, .bold)
                if let note { Text(note).appFont(12).foregroundStyle(Theme.sub).lineLimit(2) }
            }
            Spacer(minLength: 0)
            Text(right).appFont(12).foregroundStyle(Theme.muted)
        }
        .foregroundStyle(locked ? Theme.sub : Theme.ink)
        .padding(.horizontal, 14).frame(minHeight: 56)
        .background(locked ? Color(hex: 0xF7F8FA, dark: 0x202933) : .clear)
        .contentShape(Rectangle())
    }

    // 본편 『중첩된 현실』 1권: 프랜차이즈 커피 이상. 후원 서버에서 받고, 한국어만
    private var vol1: some View {
        VStack(alignment: .leading, spacing: 8) {
            header("본편 『중첩된 현실』 1권", story.count(.vol1) > 0 ? "\(story.count(.vol1))장" : "")
            VStack(spacing: 0) {
                if story.count(.vol1) > 0 {
                    ForEach(0..<story.count(.vol1), id: \.self) { i in
                        if i > 0 { Divider().overlay(Theme.line) }
                        NavigationLink(value: "vol1:\(i)") {
                            paidRow(nil, story.chapter(i, .vol1)?.title ?? "", story.chapter(i, .vol1)?.name ?? "",
                                    (story.pos[Story.key(.vol1, i)] ?? 0) > 0 ? "이어 읽기" : "읽기")
                        }
                        .buttonStyle(.plain)
                    }
                } else {
                    Button { Task { await story.fetch(.vol1, force: true) } } label: {
                        paidRow(nil, "1권", "중첩된 현실", story.isLoading(.vol1) ? "받는 중…" : story.failText(.vol1) == nil ? "받기" : "다시 받기", note: story.failText(.vol1))
                    }
                    .buttonStyle(.plain).disabled(story.isLoading(.vol1))
                }
            }
            .background(Theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        }
        .task { await story.fetch(.vol1) }
    }

    private func itemTile(_ i: Int, _ it: ShelterItem) -> some View {
        let on = i < m.itemsOn
        return Button { if on { item = it.id } } label: {
            VStack(spacing: 2) {
                Pixel(name: "art_" + it.id + (on || m.peek ? "" : "_l"), width: 32, height: 32).opacity(on ? 1 : m.peek ? 0.6 : 1)
                Text(on ? it.name : "???").appFont(11, .bold).lineLimit(2).multilineTextAlignment(.center)
                Text(i == m.itemsOn ? "\(m.itemDaysLeft)일 남음" : "\(i + 1)번째").appFont(10).foregroundStyle(i == m.itemsOn ? Theme.teal : Theme.sub)
            }
            .foregroundStyle(on ? Theme.ink : Theme.muted)
            .frame(maxWidth: .infinity, minHeight: 84)
            .background(on ? (item == it.id ? Theme.mintBg : Theme.card) : Color(hex: 0xF6F7F8, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? (item == it.id ? Theme.teal : Theme.border) : Theme.dash, style: StrokeStyle(lineWidth: 2, dash: on ? [] : [4, 3])))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? it.name : i == m.itemsOn ? "다음에 돌아오는 물건, \(m.itemDaysLeft)일 남음" : "\(i + 1)번째로 돌아오는 물건")
    }

    /// 쉼터 방: 돌아온 물건이 제자리에 놓이고, 홈에 둔 친구가 고른 물건(없으면 마지막에 돌아온 물건) 옆에 선다.
    /// 다방커피 이상은 아직 안 돌아온 물건이 흐리게 보인다
    private var room: some View {
        let focus = item.flatMap { id in Shelter.items.first { $0.id == id } } ?? (m.itemsOn > 0 ? Shelter.items[m.itemsOn - 1] : nil)
        let fx: CGFloat = focus.flatMap { Shelter.slots[$0.id] }.map { $0.x <= 86 ? $0.x + 18 : $0.x - 26 } ?? 60
        return GeometryReader { g in
            let u = g.size.width / 128
            ZStack(alignment: .topLeading) {
                Image("art_room").interpolation(.none).resizable().frame(width: g.size.width, height: g.size.height)
                ForEach(Array(Shelter.items.enumerated()), id: \.element.id) { i, it in
                    if let p = Shelter.slots[it.id], i < m.itemsOn || m.peek {
                        let on = i < m.itemsOn
                        Button { if on { item = it.id } } label: {
                            Image("art_" + it.id).interpolation(.none).resizable().frame(width: 16 * u, height: 16 * u)
                                .opacity(on ? 1 : 0.35)
                        }
                        .buttonStyle(.plain).disabled(!on)
                        .offset(x: p.x * u, y: p.y * u)
                        .accessibilityLabel(on ? it.name : "아직 안 돌아온 물건 미리 보기")
                    }
                }
                Pixel(name: "spr_" + m.homeFriendShown, width: 24 * u, height: 34 * u)
                    .offset(x: max(4, min(100, fx)) * u, y: 42 * u)
                    .animation(.easeOut(duration: 0.3), value: fx)
                    .allowsHitTesting(false).accessibilityHidden(true)
            }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("쉼터 방, 돌아온 물건 \(m.itemsOn)개")
    }

    private var itemNote: some View {
        let cur = Shelter.items.first { $0.id == item } ?? (m.itemsOn > 0 ? Shelter.items[m.itemsOn - 1] : nil)
        return HStack(spacing: 10) {
            Pixel(name: "art_" + (cur?.id ?? "barley_tea") + (cur == nil ? "_l" : ""), width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(cur?.name ?? "아직 돌아오지 않았어요").appFont(14, .bold)
                Text(cur?.line ?? "오늘의 1분을 7일 연속 하면 첫 물건이 돌아와요.").appFont(13).foregroundStyle(Theme.sub)
                if let c = cur, let say = WItem.say(c.id, m.homeFriendShown) {
                    let who = Shelter.friends.first { $0.id == m.homeFriendShown }?.name ?? "세리"
                    Text("\(who) “\(say)”").appFont(13, .bold).foregroundStyle(Theme.teal)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
    }
}

// 장면 도트 표지. 5장은 4프레임 애니메이션 (움직임 줄이기 설정이면 첫 프레임만).
struct ChapterCover: View {
    let index: Int
    var on = true
    @Environment(\.accessibilityReduceMotion) private var reduce
    var body: some View {
        Group {
            if index == 5 && on && !reduce {
                TimelineView(.periodic(from: .now, by: 0.7)) { ctx in
                    let f = Int(ctx.date.timeIntervalSinceReferenceDate / 0.7) % 4
                    Image("art_ch5_\(f)").interpolation(.none).resizable()
                }
            } else {
                Image(Shelter.cover(index)).interpolation(.none).resizable()
            }
        }
        .grayscale(on ? 0 : 1).brightness(on ? 0 : -0.3).opacity(on ? 1 : 0.6)
        .background(Theme.shelter)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

// 서재 읽기: 외전 원고(한글·영문)를 앱에 넣어 두고 읽는다. 언어는 기기 언어가 기본이고, 위의 한/EN 으로 바꾸면 기억한다.
// 읽던 자리는 장마다 블록 번호로 남기고(두 언어가 같은 번호), 서재 위젯이 그 자리의 문단을 보여 준다.
struct ReaderView: View {
    @Environment(AppModel.self) private var m
    @State var index: Int
    var book: StoryBook = .side
    @State private var size = 17.0
    @State private var dark = false
    @State private var top: Int? = nil
    @State private var story = Story.shared
    @State private var support = false
    @Environment(\.colorScheme) private var scheme

    /// 앱에 든 외전 장(프롤로그~5장): 쉼터 친구·표지·미션 열림과 이어진다. 6장부터와 본편 1권은 후원 서버에서 받은 장
    private var inApp: Bool { book == .side && index < 6 }

    var body: some View {
        let accent = dark ? Theme.mint : Theme.teal
        let ch = inApp ? Shelter.chapters[index] : nil
        let fi = ch.map { c in Shelter.friends.firstIndex { $0.id == c.friend } ?? 0 }
        let text = story.chapter(index, book)
        let started = inApp ? m.readPos.contains(index) : (story.pos[Story.key(book, index)] ?? 0) > 0
        ScrollViewReader { proxy in
            ScrollView {
              VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 12) {
                    controls(accent)
                    cover
                    VStack(alignment: .leading, spacing: 2) {
                        Text(story.heading(index, book)).appFont(13, .semibold).foregroundStyle(accent)
                        Text(text?.name ?? ch?.name ?? "").appFont(22, .bold)
                    }
                    if text == nil {
                        fetchNote(accent)
                    } else {
                        Text(started ? "읽던 곳에서 이어 읽는 중" : "처음부터").appFont(13).foregroundStyle(dark ? Color(hex: 0xA6ADC6) : Theme.sub2)
                    }
                }
                .padding([.horizontal, .top], 16)
                .id("top")
                LazyVStack(alignment: .leading, spacing: size * 0.8) {
                    ForEach(Array((text?.blocks ?? []).enumerated()), id: \.offset) { p, b in
                        VStack(alignment: .leading, spacing: 0) {
                            block(b, accent)
                            if p == 1, let ch, let fi, Shelter.friends[fi].inSideStory { firstAppear(ch.friend, fi).padding(.top, size * 0.8) }
                        }
                        .id(p)
                    }
                }
                .scrollTargetLayout()
                .padding(.horizontal, 16).padding(.top, 8)
                next(fi ?? 0, accent, proxy).padding(16)
              }
            }
            .scrollPosition(id: $top, anchor: .top)
            .onChange(of: top) { _, t in
                guard let t else { return }
                story.setPos(book, index, t)
                if inApp && t > 0 && !m.readPos.contains(index) { m.readPos.insert(index) }
            }
            .onAppear {
                if inApp { m.readLast = index }
                if scheme == .dark { dark = true }
                if let p = story.pos[Story.key(book, index)], p > 0 { DispatchQueue.main.async { proxy.scrollTo(p, anchor: .top) } }
            }
        }
        .foregroundStyle(dark ? Color(hex: 0xEEF0F7) : Theme.ink)
        .background(dark ? Color(hex: 0x141824) : Color(hex: 0xFBFAF7))
        .toolbarBackground(dark ? Color(hex: 0x141824) : Color(hex: 0xFBFAF7), for: .navigationBar)
        .navigationTitle(book == .side ? "서재" : "본편 1권").navigationBarTitleDisplayMode(.inline)
        // 받은 장이 없으면(처음이거나 다른 언어) 받는다. 5장 끝에서는 6장을 미리 받아 둔다
        .task(id: "\(index)-\(story.lang(book).rawValue)") {
            if story.chapter(index, book) == nil || (book == .side && index == 5) { await story.fetch(book) }
        }
        .onChange(of: story.lang) { _, _ in story.pushWidget(m) }
        .onDisappear { story.pushWidget(m) }
        .sheet(isPresented: $support) { NavigationStack { SupportView() } }
    }

    @ViewBuilder private var cover: some View {
        if inApp {
            ChapterCover(index: index).aspectRatio(1.6, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 12))
        } else if book == .side {
            Image("art_ch6").interpolation(.none).resizable().aspectRatio(1.6, contentMode: .fit)
                .background(Theme.shelter).clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    /// 받는 중 · 오류 · 다시 받기
    @ViewBuilder private func fetchNote(_ accent: Color) -> some View {
        if story.isLoading(book) {
            HStack(spacing: 8) { ProgressView(); Text("원고를 받는 중이에요").appFont(14) }
        } else if !Support.shared.has(book.need) {
            Button { support = true } label: {
                Text("\(book.need.name) 이상 후원하면 읽을 수 있어요 ›").appFont(14, .bold).foregroundStyle(accent)
            }.buttonStyle(.plain)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(story.failText(book) ?? "원고를 아직 받지 못했어요.").appFont(14).foregroundStyle(dark ? Color(hex: 0xA6ADC6) : Theme.sub2)
                    .fixedSize(horizontal: false, vertical: true)
                Button("다시 받기") { Task { await story.fetch(book, force: true) } }
                    .appFont(14, .bold).foregroundStyle(accent)
            }
        }
    }

    // 한/EN · 글자 크기 3단계 · 어둡게
    private func controls(_ accent: Color) -> some View {
        let ink = dark ? Color(hex: 0xEEF0F7) : Theme.ink, edge = dark ? Color(hex: 0x4A5578) : Theme.border
        return HStack(spacing: 6) {
            if book.langs.count == 1 {
                Text("한국어만 있어요").appFont(12).foregroundStyle(dark ? Color(hex: 0xA6ADC6) : Theme.sub2)
            } else {
            HStack(spacing: 0) {
                ForEach(StoryLang.allCases, id: \.self) { l in
                    Button(l.label) { story.pick(l) }
                        .appFont(13, .bold).frame(minWidth: 38, minHeight: 32)
                        .foregroundStyle(story.lang == l ? (dark ? Color(hex: 0x141824) : .white) : ink)
                        .background(story.lang == l ? accent : .clear, in: Capsule())
                        .accessibilityLabel(l == .ko ? "한글 원고" : "영문 번역본")
                        .accessibilityAddTraits(story.lang == l ? .isSelected : [])
                }
            }
            .padding(2).overlay(Capsule().stroke(edge, lineWidth: 2))
            }
            Spacer(minLength: 4)
            ForEach([(15.0, 12.0, "작은 글자"), (17.0, 15.0, "보통 글자"), (20.0, 18.0, "큰 글자")], id: \.0) { s, f, label in
                Button("가") { size = s }
                    .font(.system(size: f, weight: .bold))
                    .frame(minWidth: 36, minHeight: 36)
                    .foregroundStyle(size == s ? (dark ? Color(hex: 0x141824) : .white) : ink)
                    .background(size == s ? accent : .clear, in: Capsule())
                    .overlay(Capsule().stroke(size == s ? accent : edge, lineWidth: 2))
                    .accessibilityLabel(label)
            }
            Button(dark ? "밝게" : "어둡게") { dark.toggle() }
                .appFont(13, .bold).padding(.horizontal, 10).frame(minHeight: 36)
                .foregroundStyle(ink)
                .background(dark ? Color(hex: 0x262E45) : Theme.card, in: Capsule())
                .overlay(Capsule().stroke(edge, lineWidth: 2))
        }
    }

    @ViewBuilder private func block(_ b: StoryBlock, _ accent: Color) -> some View {
        let sub = dark ? Color(hex: 0xA6ADC6) : Theme.sub2
        switch b.k {
        case "h":
            Text(b.t).appFont(size * 0.85, .bold).foregroundStyle(accent).padding(.top, size)
        case "q":
            // 화면에 뜬 글·메시지: 왼쪽 줄 + 조금 흐린 색
            Text(Self.md(b.t)).appFont(size * 0.95).lineSpacing(size * 0.35).foregroundStyle(sub)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, 12)
                .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 1).fill(accent.opacity(0.6)).frame(width: 2) }
        case "s":
            Text("⁂").appFont(size).foregroundStyle(sub).frame(maxWidth: .infinity).accessibilityHidden(true)
        case "t":
            Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 6) {
                ForEach(Array((b.rows ?? []).enumerated()), id: \.offset) { r, row in
                    GridRow {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, c in
                            Text(Self.md(c)).appFont(size * 0.85, r == 0 ? .bold : .regular).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    if r == 0 { Divider().overlay(sub) }
                }
            }
            .padding(12)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(dark ? Color(hex: 0x3A4459) : Theme.border))
        default:
            Text(Self.md(b.t)).appFont(size).lineSpacing(size * 0.45).fixedSize(horizontal: false, vertical: true)
        }
    }

    private func firstAppear(_ id: String, _ fi: Int) -> some View {
        NavigationLink(value: "char:" + id) {
            HStack(spacing: 10) {
                Pixel(name: "spr_" + id, width: 24, height: 34)
                VStack(alignment: .leading) {
                    Text("\(Shelter.friends[fi].name) 처음 나오는 장면").appFont(13, .bold)
                    Text("쉼터에서 만날 수 있어요 ›").appFont(12)
                }
                Spacer()
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(dark ? Color(hex: 0x262E45) : Theme.cream, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(dark ? Color(hex: 0x4A5578) : Theme.yellow))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private func next(_ fi: Int, _ accent: Color, _ proxy: ScrollViewProxy) -> some View {
        let last = inApp && index < 5 ? !m.chapterOn(index + 1) : index + 1 >= story.count(book)
        if !last {
            let n = story.chapter(index + 1, book)
            Button {
                if inApp { m.readPos.insert(index); story.pushWidget(m) }
                index += 1; top = nil
                if inApp { m.readLast = index }
                proxy.scrollTo("top", anchor: .top)
            } label: {
                Text("다음: \(n?.title ?? "") · \(n?.name ?? "") ›").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48).background(accent, in: RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(.plain)
        } else if book == .side && index == 5 && !Support.shared.has(.mix) {
            // 5장 끝: 6장부터는 커피 후원으로
            Button { support = true } label: {
                Text("6장 · \(Shelter.chapter6Name)는 커피 한 잔으로 이어 읽어요 ☕︎ ›").appFont(14, .bold).foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 48).padding(.horizontal, 8).background(accent, in: RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(.plain)
        } else {
            Text(inApp && index < 5 ? "다음 장은 " + (index >= 1 ? m.friendWhen(fi + 1) + "에" : "앱 시작 3단계를 마치면") + " 열려요."
                 : book == .side && index == 5 ? (story.isLoading(.side) ? "6장을 받는 중이에요" : story.failText(.side) ?? "6장은 곧 이어져요")
                 : "끝까지 읽었어요")
                .appFont(14, .bold).multilineTextAlignment(.center)
                .foregroundStyle(dark ? Theme.gold : Theme.sub)
                .frame(maxWidth: .infinity).padding(12)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(dark ? Color(hex: 0x4A5578) : Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }
    }

    /// 문단 안의 **굵게**·_기울임_·~~지움~~ 과 줄바꿈을 그대로
    static func md(_ s: String) -> AttributedString {
        (try? AttributedString(markdown: s, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(s)
    }
}

// 인물 자세히 보기: 『중첩된 현실』 등장 시점, 특징, 배경, 대표 장면 (캐릭터 설정 스레드 요약 기준)
struct CharacterDetailView: View {
    let friend: Friend

    var body: some View {
        let f = friend
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .bottom, spacing: 14) {
                    Pixel(name: "spr_" + f.id, width: 84, height: 119)
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(Shelter.spriteBacking(f.id), in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 6) {
                        Text(f.name).appFont(24, .bold)
                        Text(f.line).appFont(14, .bold).foregroundStyle(Color(hex: 0x8A6400, dark: 0xE8C060))
                    }
                }
                Card {
                    Text("소설에 나오는 곳").appFont(15, .bold)
                    appear("본편", f.main)
                    appear("외전 『이종 공명』", f.side)
                    appear("프리퀄", f.prequel)
                    if f.side == nil {
                        Text("앱 서재의 외전에는 나오지 않아요. 쉼터에는 인터미션 체크인으로 찾아와요.").appFont(12).foregroundStyle(Theme.muted)
                    }
                }
                section("특징", f.traits)
                section("배경", f.background)
                Card {
                    Text("대표 장면").appFont(15, .bold)
                    ForEach(f.scenes, id: \.1) { k, v in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(k).appFont(12, .bold).foregroundStyle(Theme.teal)
                            Text(v).appFont(14).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.vertical, 4)
                    }
                }
                Text("본편 9·10권의 결말과 일부 인물 이야기는 넣지 않았어요.").appFont(12).foregroundStyle(Theme.muted)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle(f.name).navigationBarTitleDisplayMode(.inline)
    }

    private func appear(_ k: String, _ v: String?) -> some View {
        HStack(alignment: .top) {
            Text(k).appFont(14).foregroundStyle(Theme.sub).frame(minWidth: 110, alignment: .leading)
            Text(v ?? "나오지 않음").appFont(14, v == nil ? .regular : .semibold).foregroundStyle(v == nil ? Theme.muted : Theme.ink)
            Spacer(minLength: 0)
        }
    }

    private func section(_ t: String, _ body: String) -> some View {
        Card {
            Text(t).appFont(15, .bold)
            Text(body).appFont(14).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
        }
    }
}

extension Shelter {
    // 캐릭터 받침: 원래 남색. 검은 후드(이르)·흑발(수아)만 조금 밝게 해서 머리가 보이게 한다
    static func spriteBacking(_ id: String) -> Color {
        id == "ir" || id == "sua" ? Color(hex: 0x3A4566) : Color(hex: 0x232B42)
    }
}
