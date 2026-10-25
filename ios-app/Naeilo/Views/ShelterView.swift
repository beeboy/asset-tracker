import SwiftUI

// 쉼터: 친구, 서재(외전 프롤로그~5장, 구독하면 6장~코다와 앱 연재판 1권), 돌아온 물건. 시장 숫자와는 상관없고 내 행동으로만 열린다.
struct ShelterView: View {
    @Environment(AppModel.self) private var m
    @State private var sel = "seri"
    @State private var item: String? = nil
    @State private var support = false
    @State private var story = Story.shared
    @State private var fold: [String: Bool] = [:]   // 손으로 펼치고 접은 묶음 (없으면 읽는 묶음만 펼침)

    var onlyLibrary = false   // 서재 전체: 장 목록만 따로 보는 화면

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if onlyLibrary {
                    library
                    if vol1Shown { vol1 }
                } else { shelter }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle(onlyLibrary ? String(localized: "서재") : String(localized: "쉼터")).toolbarTitleDisplayMode(.inlineLarge)
        .onAppear {
            sel = m.shelterSel ?? m.homeFriend
            for i in 0..<5 where m.readPos.contains(i + 1) { story.markDone(.side, i) }   // 다음 장을 연 장은 다 읽은 것으로 (예전 기록)
        }
        .sheet(isPresented: $support) { NavigationStack { SupportView() } }
    }

    @ViewBuilder private var shelter: some View {
        Text("꾸준히 쓰면 물건·이야기가 돌아와요").appFont(13).foregroundStyle(Theme.sub)
        hero
        let met = (0..<5).filter { m.friendOn($0) }.count
        header(String(localized: "만난 친구"), "\(met)/5")
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 5), count: 5), spacing: 6) {
            ForEach(Array(Shelter.friends.enumerated()), id: \.element.id) { i, f in friendTile(i, f) }
        }
        Text("눌러서 홈 인물 바꾸기").appFont(12).foregroundStyle(Theme.muted)
        // 내 캐릭터는 친구와 따로 한 줄
        meTile
        header(String(localized: "이어서 읽기"), "")
        continueCard
        HomeLink(value: "library") {
            HStack { Text("서재 전체").appFont(14, .bold); Spacer(); Text("›").foregroundStyle(Theme.muted) }
                .foregroundStyle(Theme.ink).padding(.horizontal, 14).frame(minHeight: 44)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
        }.buttonStyle(.plain)
        let placed = (0..<m.itemsOn).filter { !m.itemsOff.contains(Shelter.items[$0].id) }.count
        header(String(localized: "돌아온 물건"), String(localized: "\(String(describing: placed))/\(String(describing: Shelter.items.count)) 배치"))
        room
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
            ForEach(Array(Shelter.items.enumerated()), id: \.element.id) { i, it in itemTile(i, it) }
        }
        itemNote
        openTable
        ShelterBGBox(support: $support)
    }

    /// 이어서 읽기: 마지막에 읽던 장 큰 카드 하나 + 작품별 진도
    private var continueCard: some View {
        let i = m.readLast.flatMap { $0 < 6 && m.chapterOn($0) ? $0 : nil } ?? (0..<6).first { m.chapterOn($0) && !story.isDone(.side, $0) } ?? 0
        let ch = Shelter.chapters[i], on = m.chapterOn(i)
        let sideDone = (0..<6).filter { story.isDone(.side, $0) }.count
        let v1 = story.count(.vol1)
        return VStack(alignment: .leading, spacing: 10) {
            HomeLink(value: "read:\(i)") {
                HStack(spacing: 12) {
                    ChapterCover(index: i, on: on).frame(width: 72, height: 46)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("『이종 공명』 · \(ch.title)").appFont(12).foregroundStyle(Theme.sub)
                        Text(on ? ch.name : "???").appFont(15, .bold)
                        ProgressView(value: Double(sideDone), total: 6).tint(Theme.teal)
                    }
                    Image(systemName: "play.fill").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 40, height: 40).background(Theme.teal, in: Circle())
                }
                .foregroundStyle(Theme.ink).contentShape(Rectangle())
            }
            .buttonStyle(.plain).disabled(!on)
            .accessibilityLabel("이어 읽기, \(ch.title) \(on ? ch.name : "")")
            Divider().overlay(Theme.line)
            progressLine(String(localized: "『이종 공명』"), String(localized: "\(String(describing: sideDone))/6장"))
            if vol1Shown, v1 > 0 {
                progressLine(String(localized: "본편 1권"), String(localized: "\(String(describing: (0..<v1).filter { story.isDone(.vol1, $0) }.count))/\(String(describing: v1))장"))
            }
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
    }
    private func progressLine(_ t: String, _ r: String) -> some View {
        HStack { Text(t).appFont(13); Spacer(); Text(r).appFont(13).foregroundStyle(Theme.sub) }
    }

    /// 열리는 방법 (예전 맨 아래 긴 문단을 표로)
    private var openTable: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("열리는 방법").appFont(15, .bold)
            ForEach([(String(localized: "물건·배경"), String(localized: "하루 2개")), (String(localized: "친구"), String(localized: "이틀째부터 하루 1명")), (String(localized: "외전 ~5장"), String(localized: "앞 장 읽으면")), (String(localized: "외전 6장~"), String(localized: "구독"))], id: \.0) { r in
                HStack(alignment: .firstTextBaseline) {
                    Text(r.0).appFont(13, .bold).frame(width: 130, alignment: .leading)
                    Text(r.1).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }
            }
            Text("수익률과 무관 · 잃어도 계속 열려요")
                .appFont(13, .semibold).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
            Text("방 물건 누르면 빼기 · 위 칸에서 다시 놓기").appFont(12).foregroundStyle(Theme.muted)
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
    }

    private func header(_ t: String, _ r: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(t).appFont(15, .bold); Spacer()
            Text(r).appFont(13).foregroundStyle(Theme.sub)
        }
    }

    @ViewBuilder private var hero: some View {
        if sel == "me" && m.me != nil { meHero } else { friendHero }
    }

    private var friendHero: some View {
        let i = Shelter.friends.firstIndex { $0.id == sel } ?? 0, f = Shelter.friends[i], real = m.friendOn(i)
        let on = real
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                Group {
                    // 만난 친구는 숨 쉬고, 누르면 한 바퀴 돌며 걷는다 (포즈 그림이 없으면 예전 그림)
                    if on && FriendMotion.has(f.id) { CharFigure(friend: f.id, dot: 3.4).frame(width: 84, height: 119, alignment: .bottom) }
                    else { Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 84, height: 119) }
                }
                .opacity(real ? 1 : on ? 0.75 : 1)
                    .padding(.horizontal, 10).padding(.vertical, 8)
                    .background(Shelter.spriteBacking(f.id), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 6) {
                    Text(on ? f.name : "???").appFont(20, .bold)
                    Text(real ? f.appearsText : on ? String(localized: "미리 보기 · 인터미션 \(String(describing: i))주차에 만나요") : String(localized: "인터미션 \(String(describing: i))주차에 만나요")).appFont(12).foregroundStyle(Color(hex: 0x8FD0FF))
                        .fixedSize(horizontal: false, vertical: true)
                    Text(on ? f.bio : String(localized: "\(String(describing: i))주차 체크인: \(String(describing: Shelter.weekSteps[max(0, i - 1)].task))"))
                        .appFont(13).lineSpacing(3).foregroundStyle(Color(hex: 0xD5D9E6))
                        .fixedSize(horizontal: false, vertical: true)
                    if on { Text(f.line).appFont(13, .bold).foregroundStyle(Theme.gold) }
                }
                Spacer(minLength: 0)
            }
            if on {
                HStack(spacing: 8) {
                    HomeLink(value: "char:" + f.id) {
                        Text("자세히 ›").appFont(13, .bold).foregroundStyle(Color(hex: 0xEEF0F7))
                            .padding(.horizontal, 14).frame(minHeight: 40)
                            .overlay(Capsule().stroke(Color(hex: 0x8FD0FF), lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                    if real && m.homeFriend != f.id {
                        Button("홈에 두기") { m.homeFriend = f.id }
                            .appFont(13, .bold).foregroundStyle(Theme.inkFixed)
                            .padding(.horizontal, 14).frame(minHeight: 40).background(Theme.mint, in: Capsule())
                    } else if real {
                        Text("홈에 있음").appFont(12, .bold).foregroundStyle(Theme.inkFixed)
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
                Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 34, height: 48)
                Text(on ? f.name : "???").appFont(12, .bold).lineLimit(1).minimumScaleFactor(0.8)
                Text(on && m.homeFriend == f.id ? String(localized: "홈에 있음") : m.friendWhen(i))
                    .appFont(10).foregroundStyle(Theme.sub)
            }
            .frame(maxWidth: .infinity, minHeight: 96)
            .background(cur ? Theme.mintBg : on ? (f.id == "ir" || f.id == "sua" ? Color(hex: 0xFFFFFF, dark: 0x2C3846) : Theme.card) : Color(hex: 0xF6F7F8, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(cur ? Theme.teal : on ? Theme.border : Theme.track, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? f.name : String(localized: "잠긴 친구"))
    }

    // 본인 칸: 비어 있으면 누르는 즉시 만들기 화면으로
    @ViewBuilder private var meTile: some View {
        if m.me == nil {
            HomeLink(value: "me") { meTileLabel }.buttonStyle(.plain)
        } else {
            Button { sel = "me" } label: { meTileLabel }.buttonStyle(.plain)
        }
    }
    private var meTileLabel: some View {
        let cur = sel == "me", made = m.me != nil, open = m.meUnlocked
        let gates = [(m.meGate1, m.meGate1Text), (m.meGate2, String(localized: "평정 지수 성향 매칭"))]
        let done = gates.filter { $0.0 }.count
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                // 잠겼을 땐 그림자
                CharPixel(id: "me", width: 28, height: 40).brightness(open ? 0 : -1).opacity(open ? 1 : 0.45)
                Text(String(localized: "나") + (m.me.map { " · " + $0.shownName } ?? "")).appFont(14, .bold).lineLimit(1)
                Spacer(minLength: 0)
                Text(!made ? String(localized: "만들기 ›") : m.homeFriendShown == "me" ? String(localized: "홈에 있음") : open ? String(localized: "열림") : String(localized: "2개 중 \(String(describing: done))개"))
                    .appFont(12).foregroundStyle(made ? Theme.sub : Theme.teal)
            }
            if !open {
                ForEach(Array(gates.enumerated()).filter { !$0.element.0 }, id: \.offset) { g in
                    Text("· " + g.element.1).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .foregroundStyle(Theme.ink)
        .padding(.horizontal, 14).padding(.vertical, 10).frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .background(cur ? Theme.mintBg : made ? Theme.card : .clear, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(cur ? Theme.teal : made ? Theme.border : Theme.dash, style: StrokeStyle(lineWidth: 2, dash: made ? [] : [4, 3])))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(made ? String(localized: "나, \(String(describing: m.me?.shownName ?? "")), \(String(describing: open ? String(localized: "열림") : String(localized: "2개 중 \(String(describing: done))개 채움")))") : String(localized: "나, 내 캐릭터 만들기"))
    }

    /// 내 캐릭터 소개: 투자 유형, 열림 조건 세 가지, 고치기·홈에 두기
    private var meHero: some View {
        let open = m.meUnlocked, k = Calm.kind(m.meVoice)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                Group {
                    if let me = m.me { CharFigure(me: me, dot: 3.5).frame(width: 84, height: 119) } else { CharPixel(id: "me", width: 84, height: 119) }
                }
                    .padding(.horizontal, 10).padding(.vertical, 8)
                    .background(Color(hex: 0x232B42), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 6) {
                    Text(m.friendName("me")).appFont(20, .bold)
                    Text(String(localized: "내 캐릭터 · \(String(describing: k?.title ?? "")) (\(String(describing: m.friendName(m.meVoice))) 말투)") + (m.calm.type != nil ? String(localized: " · 평정 지수 매칭") : ""))
                        .appFont(12).foregroundStyle(Color(hex: 0x8FD0FF)).fixedSize(horizontal: false, vertical: true)
                    Text(open ? String(localized: "홈과 위젯에서 기본 인물 대신 둘 수 있어요.") : String(localized: "세 가지를 채우면 홈과 위젯에 나와요. 그 전에는 쉼터에서만 보여요."))
                        .appFont(13).lineSpacing(3).foregroundStyle(Color(hex: 0xD5D9E6)).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            if !open {
                VStack(alignment: .leading, spacing: 6) {
                    gate(m.meGate1, "1", m.meGate1Text)
                    gate(m.meGate2, "2", m.meGate2 ? String(localized: "평정 지수 성향 매칭") : String(localized: "평정 지수 성향 매칭 (기록 \(String(describing: m.calm.stats().n))/\(String(describing: Calm.minEvents)))"))
                }
            }
            HStack(spacing: 8) {
                HomeLink(value: "me") {
                    Text("고치기 ›").appFont(13, .bold).foregroundStyle(Color(hex: 0xEEF0F7))
                        .padding(.horizontal, 14).frame(minHeight: 40)
                        .overlay(Capsule().stroke(Color(hex: 0x8FD0FF), lineWidth: 1.5))
                }
                .buttonStyle(.plain)
                if open && m.homeFriend != "me" {
                    Button("홈에 두기") { m.homeFriend = "me" }
                        .appFont(13, .bold).foregroundStyle(Theme.inkFixed)
                        .padding(.horizontal, 14).frame(minHeight: 40).background(Theme.mint, in: Capsule())
                } else if open {
                    Text("홈에 있음").appFont(12, .bold).foregroundStyle(Theme.inkFixed)
                        .padding(.horizontal, 10).padding(.vertical, 4).background(Theme.gold, in: Capsule())
                }
                Spacer(minLength: 0)
            }
        }
        .foregroundStyle(Color(hex: 0xEEF0F7))
        .padding(16)
        .background(Theme.shelter, in: RoundedRectangle(cornerRadius: 20))
    }
    private func gate(_ ok: Bool, _ n: String, _ t: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: ok ? "checkmark.circle.fill" : "circle").font(.system(size: 13, weight: .bold))
                .foregroundStyle(ok ? Theme.mint : Color(hex: 0x8A93AD))
            Text(t).appFont(13, ok ? .regular : .semibold).foregroundStyle(ok ? Color(hex: 0xA6ADC6) : Color(hex: 0xEEF0F7))
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(t), \(ok ? String(localized: "채움") : String(localized: "아직"))")
    }

    private var library: some View {
        let paid = story.count(.side) > 6 ? Array(6..<story.count(.side)) : []
        let cur = current
        return VStack(spacing: 0) {
            foldRow("side0", String(localized: "프롤로그 ~ 5장"), String(localized: "\(String(describing: (0..<6).filter { story.isDone(.side, $0) }.count))/6 읽음"), cur)
            if isOpen("side0", cur) { ForEach(0..<6, id: \.self) { i in
                let on = m.chapterOn(i), ch = Shelter.chapters[i]
                let row = HStack(spacing: 10) {
                    ChapterCover(index: i, on: on).frame(width: 64, height: 40)
                    Text(ch.title).appFont(12, .bold).foregroundStyle(Theme.teal).frame(width: 44, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(on ? ch.name : "???").appFont(14, .bold)
                        Text(on ? (i == 0 ? String(localized: "처음부터 열림") : String(localized: "앞 장을 읽어 열림")) : String(localized: "앞 장을 끝까지 읽으면 열려요"))
                            .appFont(12).foregroundStyle(Theme.sub)
                    }
                    Spacer(minLength: 0)
                    Text(on ? (m.readPos.contains(i) ? String(localized: "이어 읽기") : String(localized: "읽기")) : String(localized: "잠김")).appFont(12).foregroundStyle(Theme.muted)
                }
                .padding(.horizontal, 14).frame(minHeight: 64)
                .foregroundStyle(on ? Theme.ink : Theme.muted)
                .contentShape(Rectangle())
                Divider().overlay(Theme.line)
                if on { HomeLink(value: "read:\(i)") { row }.buttonStyle(.plain) }
                else { Button { sel = ch.friend } label: { row }.buttonStyle(.plain) }
            } }
            Divider().overlay(Theme.line)
            foldRow("side1", String(localized: "6장 ~ 코다"), paid.isEmpty ? String(localized: "구독") : String(localized: "\(String(describing: paid.filter { story.isDone(.side, $0) }.count))/\(String(describing: paid.count)) 읽음"), cur)
            // 6장부터: 구독하면 서버에서 받은 장 (구독을 멈춰도 받은 장은 그대로), 아니면 누르면 구독 화면
            if !isOpen("side1", cur) {
            } else if story.count(.side) > 6 {
                ForEach(6..<story.count(.side), id: \.self) { i in
                    Divider().overlay(Theme.line)
                    HomeLink(value: "read:\(i)") { paidRow(story.cover(i) ?? (i == 6 ? Image("art_ch6") : nil), story.chapter(i)?.title ?? "", story.chapter(i)?.name ?? "", (story.pos[i] ?? 0) > 0 ? String(localized: "이어 읽기") : String(localized: "읽기")) }
                        .buttonStyle(.plain)
                }
            } else if Support.shared.sideOpen {
                Divider().overlay(Theme.line)
                Button { Task { await story.fetch(.side, force: true) } } label: {
                    paidRow(Image("art_ch6"), String(localized: "6장"), Shelter.chapter6Name, story.isLoading(.side) ? String(localized: "받는 중…") : story.failText(.side) == nil ? String(localized: "받기") : String(localized: "다시 받기"),
                            note: story.failText(.side))
                }
                .buttonStyle(.plain).disabled(story.isLoading(.side))
            } else {
                Divider().overlay(Theme.line)
                Button { support = true } label: {
                    paidRow(Image("art_ch6"), String(localized: "6장"), Shelter.chapter6Name, String(localized: "구독 ›"), note: String(localized: "구독하면 이어 읽어요"), locked: true)
                }
                .buttonStyle(.plain)
            }
        }
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        .task(id: story.lang) { await story.fetch(.side) }   // 구독 중이면 6장부터 받아 둔다
    }

    // 서재 묶음 접기: 외전 프롤로그~5장 · 6장~코다 · 본편 1권 1부 · 2부 · 3부 … 중에 가장 최근에 읽고 있는 묶음 하나만 펼치고 나머지는 접는다.
    // 최근에 읽은 장을 끝까지 읽었으면 그다음 장이 든 묶음을 펼친다 (한 묶음을 다 읽으면 다음 묶음이 저절로 펼쳐진다).
    // 읽은 기록이 없으면 다 읽지 않은 첫 묶음. 손으로 펼치고 접은 것(fold)이 이보다 먼저다
    private var groups: [(id: String, book: StoryBook, idx: [Int])] {
        let paid = story.count(.side) > 6 ? Array(6..<story.count(.side)) : []
        var g: [(id: String, book: StoryBook, idx: [Int])] = [("side0", .side, Array(0..<6)), ("side1", .side, paid)]
        if vol1Shown { g += vol1Parts.map { ($0.id, .vol1, $0.idx) } }
        return g
    }
    private var current: String? {
        let gs = groups
        if let k = story.last ?? m.readLast {   // 예전 기록은 외전 이어 읽기 장
            let b: StoryBook = k >= 1000 ? .vol1 : .side, i = k >= 1000 ? k - 1000 : k
            let n = story.isDone(b, i) && i + 1 < story.count(b) ? i + 1 : i
            if let g = gs.first(where: { $0.book == b && $0.idx.contains(n) }) ?? gs.first(where: { $0.book == b && $0.idx.contains(i) }) { return g.id }
        }
        return gs.first { g in g.idx.isEmpty || !g.idx.allSatisfy { story.isDone(g.book, $0) } }?.id
    }
    private func isOpen(_ id: String, _ cur: String?) -> Bool { fold[id] ?? (id == cur) }
    private func foldRow(_ id: String, _ title: String, _ right: String, _ cur: String?) -> some View {
        let open = isOpen(id, cur)
        return Button { withAnimation(.easeOut(duration: 0.2)) { fold[id] = !open } } label: {
            HStack(spacing: 8) {
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.teal)
                    .rotationEffect(.degrees(open ? 90 : 0))
                Text(title).appFont(14, .bold)
                Spacer(minLength: 0)
                Text(right).appFont(12).foregroundStyle(Theme.muted)
            }
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 14).frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title), \(right), \(open ? String(localized: "펼침") : String(localized: "접힘"))")
    }
    /// 본편 1권의 부: 장 제목 앞의 'N부'('Part N, …') 로 묶고, 이름은 그 부 첫 장의 '1부. 거부할 권리'('Part One — The Right to Refuse') 같은 절 제목
    private var vol1Parts: [(id: String, name: String, idx: [Int])] {
        var out: [(id: String, name: String, idx: [Int])] = []
        for i in 0..<story.count(.vol1) {
            let ch = story.chapter(i, .vol1), title = ch?.title ?? ""
            let p = title.hasPrefix("Part") ? title.components(separatedBy: ",")[0] : title.split(separator: " ").first.map(String.init) ?? ""
            if out.last?.id != "vol1-" + p {
                let h = ch?.blocks.first { $0.k == "h" && ($0.t.hasPrefix(p) || $0.t.hasPrefix("Part ")) }?.t
                out.append((id: "vol1-" + p, name: h.map { $0.replacingOccurrences(of: ". ", with: " · ").replacingOccurrences(of: " — ", with: " · ") } ?? p, idx: []))
            }
            out[out.count - 1].idx.append(i)
        }
        return out
    }

    /// 서버에서 받는 장 한 줄 (6장부터, 본편 1권)
    private func paidRow(_ art: Image?, _ title: String, _ name: String, _ right: String, note: String? = nil, locked: Bool = false) -> some View {
        HStack(spacing: 10) {
            Group {
                if let art {
                    art.interpolation(.none).resizable()
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

    /// 앱 연재판 1권: 연 구독 또는 월 구독 9개월 이상이면 서버에서 받는다. 받은 장은 구독을 멈춰도 그대로
    private var vol1Shown: Bool { Support.shared.vol1Open || story.count(.vol1) > 0 }
    // 본편 『중첩된 현실』 1권 (앱 연재판). 한국어만
    private var vol1: some View {
        VStack(alignment: .leading, spacing: 8) {
            header(String(localized: "본편 『중첩된 현실』 1권"), story.count(.vol1) > 0 ? String(localized: "\(String(describing: story.count(.vol1)))장") : "")
            VStack(spacing: 0) {
                if story.count(.vol1) > 0 {
                    let parts = vol1Parts, cur = current
                    ForEach(Array(parts.enumerated()), id: \.element.id) { n, part in
                        if n > 0 { Divider().overlay(Theme.line) }
                        foldRow(part.id, part.name, String(localized: "\(String(describing: part.idx.filter { story.isDone(.vol1, $0) }.count))/\(String(describing: part.idx.count)) 읽음"), cur)
                        if isOpen(part.id, cur) {
                            ForEach(part.idx, id: \.self) { i in
                                Divider().overlay(Theme.line)
                                HomeLink(value: "vol1:\(i)") {
                                    paidRow(story.cover(i, .vol1), story.chapter(i, .vol1)?.title ?? "", story.chapter(i, .vol1)?.name ?? "",
                                            story.isDone(.vol1, i) ? String(localized: "다 읽음") : (story.pos[Story.key(.vol1, i)] ?? 0) > 0 ? String(localized: "이어 읽기") : String(localized: "읽기"))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                } else {
                    Button { Task { await story.fetch(.vol1, force: true) } } label: {
                        paidRow(nil, String(localized: "1권"), String(localized: "중첩된 현실"), story.isLoading(.vol1) ? String(localized: "받는 중…") : story.failText(.vol1) == nil ? String(localized: "받기") : String(localized: "다시 받기"), note: story.failText(.vol1))
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
        let off = on && m.itemsOff.contains(it.id)
        return Button { if on { if item == it.id { togglePlace(it.id) } else { item = it.id } } } label: {
            VStack(spacing: 2) {
                Pixel(name: "art_" + it.id + (on ? "" : "_l"), width: 32, height: 32).opacity(off ? 0.4 : 1)
                Text(on ? it.name : "???").appFont(11, .bold).lineLimit(2).multilineTextAlignment(.center)
                Text(off ? String(localized: "빼 둠") : i == m.itemsOn ? String(localized: "다음 차례") : String(localized: "\(String(describing: i + 1))번째")).appFont(10).foregroundStyle(i == m.itemsOn ? Theme.teal : Theme.sub)
            }
            .foregroundStyle(on ? Theme.ink : Theme.muted)
            .frame(maxWidth: .infinity, minHeight: 84)
            .background(on ? (item == it.id ? Theme.mintBg : Theme.card) : Color(hex: 0xF6F7F8, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? (item == it.id ? Theme.teal : off ? Theme.dash : Theme.border) : Theme.dash, style: StrokeStyle(lineWidth: 2, dash: on && !off ? [] : [4, 3])))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? it.name + (off ? String(localized: ", 방에서 빼 둠") : "") : i == m.itemsOn ? String(localized: "다음에 돌아오는 물건") : String(localized: "\(String(describing: i + 1))번째로 돌아오는 물건"))
    }

    /// 쉼터 방: 돌아온 물건이 제자리에 놓이고, 홈에 둔 친구는 물건과 겹치지 않게 문 앞에 선다.
    private var room: some View {
        GeometryReader { g in
            let u = g.size.width / 128
            ZStack(alignment: .topLeading) {
                RoomImage(bg: m.roomBG).frame(width: g.size.width, height: g.size.height)
                ForEach(Array(Shelter.items.enumerated()), id: \.element.id) { i, it in
                    if let p = Shelter.slots[it.id], i < m.itemsOn, !m.itemsOff.contains(it.id) {
                        let on = i < m.itemsOn
                        Button { if on { item = it.id; m.itemsOff.insert(it.id) } } label: {
                            Image("art_" + it.id).interpolation(.none).resizable().frame(width: 16 * u, height: 16 * u)
                                .opacity(on ? 1 : 0.35)
                        }
                        .buttonStyle(.plain).disabled(!on)
                        .offset(x: p.x * u, y: p.y * u)
                        .accessibilityLabel(on ? it.name : String(localized: "아직 안 돌아온 물건 미리 보기"))
                        .accessibilityHint(on ? String(localized: "누르면 방에서 빼요") : "")
                    }
                }
                CharPixel(id: m.homeFriendShown, width: 24 * u, height: 34 * u).charIdle()
                    .offset(x: 4 * u, y: 42 * u)     // 문(6~26) 앞. 의자는 30부터라 겹치지 않는다
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
                Text(cur?.name ?? String(localized: "아직 돌아오지 않았어요")).appFont(14, .bold)
                Text(cur?.line ?? String(localized: "물건은 하루 두 개씩 돌아와요.")).appFont(13).foregroundStyle(Theme.sub)
                if let c = cur, let say = Lines.pick("item.\(c.id).\(m.voice(m.homeFriendShown))") ?? WItem.say(c.id, m.voice(m.homeFriendShown)) {
                    let who = m.friendName(m.homeFriendShown)
                    Text("\(who) “\(say)”").appFont(13, .bold).foregroundStyle(Theme.teal)
                }
            }
            Spacer(minLength: 0)
            if let c = cur {
                let off = m.itemsOff.contains(c.id)
                Button { item = c.id; togglePlace(c.id) } label: {
                    Text(off ? String(localized: "다시 놓기") : String(localized: "빼기")).appFont(13, .bold).foregroundStyle(off ? .white : Theme.teal)
                        .padding(.horizontal, 12).frame(minHeight: 36)
                        .background(off ? Theme.teal : Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(off ? String(localized: "\(String(describing: c.name)) 방에 다시 놓기") : String(localized: "\(String(describing: c.name)) 방에서 빼기"))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
    }

    /// 방 배치에서 빼거나 다시 놓는다. 모은 물건은 그대로 남는다
    private func togglePlace(_ id: String) {
        if m.itemsOff.contains(id) { m.itemsOff.remove(id) } else { m.itemsOff.insert(id) }
    }
}

// 장면 도트 표지. 5장은 4프레임 애니메이션 (움직임 줄이기 설정이면 첫 프레임만).
/// 서버에서 받은 장의 도트 그림 (data URL → Image). 한 번 푼 그림은 기억해 둔다
@MainActor private var coverCache: [String: Image] = [:]
extension Story {
    func cover(_ i: Int, _ b: StoryBook = .side) -> Image? {
        guard let url = chapter(i, b)?.cover else { return nil }
        if let img = coverCache[url] { return img }
        guard let comma = url.firstIndex(of: ","), let d = Data(base64Encoded: String(url[url.index(after: comma)...])),
              let ui = UIImage(data: d) else { return nil }
        let img = Image(uiImage: ui)
        coverCache[url] = img
        return img
    }
}

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

    /// 앱에 든 외전 장(프롤로그~5장): 쉼터 친구·표지·미션 열림과 이어진다. 6장부터와 앱 연재판 1권은 구독으로 서버에서 받은 장
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
                        Text(started ? String(localized: "읽던 곳에서 이어 읽는 중") : String(localized: "처음부터")).appFont(13).foregroundStyle(dark ? Color(hex: 0xA6ADC6) : Theme.sub2)
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
                    .onAppear { if text != nil { story.markDone(book, index) } }   // 끝까지 내려오면 다 읽은 장
              }
            }
            .scrollPosition(id: $top, anchor: .top)
            .onChange(of: top) { _, t in
                guard let t else { return }
                story.setPos(book, index, t)
                if inApp && t > 0 && !m.readPos.contains(index) { m.readPos.insert(index) }
            }
            .onAppear {
                story.touch(book, index)
                if inApp { m.readLast = index }
                if scheme == .dark { dark = true }
                if let p = story.pos[Story.key(book, index)], p > 0 { DispatchQueue.main.async { proxy.scrollTo(p, anchor: .top) } }
            }
        }
        .foregroundStyle(dark ? Color(hex: 0xEEF0F7) : Theme.ink)
        .background(dark ? Color(hex: 0x141824) : Color(hex: 0xFBFAF7))
        .toolbarBackground(dark ? Color(hex: 0x141824) : Color(hex: 0xFBFAF7), for: .navigationBar)
        .navigationTitle(book == .side ? String(localized: "서재") : String(localized: "본편 1권")).toolbarTitleDisplayMode(.inlineLarge)
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
        } else if let art = story.cover(index, book) {
            art.interpolation(.none).resizable().aspectRatio(1.6, contentMode: .fit)
                .background(Theme.shelter).clipShape(RoundedRectangle(cornerRadius: 12))
        } else if book == .side {
            Image("art_ch6").interpolation(.none).resizable().aspectRatio(1.6, contentMode: .fit)
                .background(Theme.shelter).clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    /// 받는 중 · 오류 · 다시 받기
    @ViewBuilder private func fetchNote(_ accent: Color) -> some View {
        if story.isLoading(book) {
            HStack(spacing: 8) { ProgressView(); Text("원고를 받는 중이에요").appFont(14) }
        } else if !Support.shared.open(book) {
            Button { support = true } label: {
                Text(book == .side ? String(localized: "구독하면 읽을 수 있어요 ›") : String(localized: "연 구독, 또는 월 구독 \(String(describing: Support.vol1Months))개월 이상이면 읽을 수 있어요 ›")).appFont(14, .bold).foregroundStyle(accent)
            }.buttonStyle(.plain)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(story.failText(book) ?? String(localized: "원고를 아직 받지 못했어요.")).appFont(14).foregroundStyle(dark ? Color(hex: 0xA6ADC6) : Theme.sub2)
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
                        .accessibilityLabel(l == .ko ? String(localized: "한글 원고") : String(localized: "영문 번역본"))
                        .accessibilityAddTraits(story.lang == l ? .isSelected : [])
                }
            }
            .padding(2).overlay(Capsule().stroke(edge, lineWidth: 2))
            }
            Spacer(minLength: 4)
            ForEach([(15.0, 12.0, String(localized: "작은 글자")), (17.0, 15.0, String(localized: "보통 글자")), (20.0, 18.0, String(localized: "큰 글자"))], id: \.0) { s, f, label in
                Button("가") { size = s }
                    .font(.system(size: f, weight: .bold))
                    .frame(minWidth: 36, minHeight: 36)
                    .foregroundStyle(size == s ? (dark ? Color(hex: 0x141824) : .white) : ink)
                    .background(size == s ? accent : .clear, in: Capsule())
                    .overlay(Capsule().stroke(size == s ? accent : edge, lineWidth: 2))
                    .accessibilityLabel(label)
            }
            Button(dark ? String(localized: "밝게") : String(localized: "어둡게")) { dark.toggle() }
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
        HomeLink(value: "char:" + id) {
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
                story.touch(book, index)
                if inApp { m.readLast = index }
                proxy.scrollTo("top", anchor: .top)
            } label: {
                Text("다음: \(n?.title ?? "") · \(n?.name ?? "") ›").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 48).background(accent, in: RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(.plain)
        } else if book == .side && index == 5 && !Support.shared.sideOpen && story.count(.side) <= 6 {
            // 5장 끝: 6장부터는 구독으로
            Button { support = true } label: {
                Text("6장 · \(Shelter.chapter6Name)는 구독하면 이어 읽어요 ›").appFont(14, .bold).foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 48).padding(.horizontal, 8).background(accent, in: RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(.plain)
        } else {
            Text(inApp && index < 5 ? String(localized: "이 장을 끝까지 읽으면 다음 장이 열려요.")
                 : book == .side && index == 5 ? (story.isLoading(.side) ? String(localized: "6장을 받는 중이에요") : story.failText(.side) ?? String(localized: "6장은 곧 이어져요"))
                 : String(localized: "끝까지 읽었어요"))
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

// 인물 자세히 보기: 위에 두 갈래 전환 "투자하는 ○○ | 소설 속 ○○" (한 화면 안, 깊이 추가 없음).
// 소설 속 = 『중첩된 현실』 등장 시점, 특징, 배경, 대표 장면 (캐릭터 설정 스레드 요약 기준). 투자하는 = InvestBranch.
// 들어온 길이 처음 갈래를 정한다: 쉼터 → 소설 속, 평정 지수·평행 세계·홈 리마인드 → 투자하는
struct CharacterDetailView: View {
    let friend: Friend
    @Environment(AppModel.self) private var m
    @State private var invest: Bool
    @State private var story = Story.shared
    var focus: Int? = nil          // 규칙 일지에서 왔으면 그 버릇 번호 (펴서 표시)

    init(friend: Friend, invest: Bool = false, focus: Int? = nil) {
        self.friend = friend
        self.focus = focus
        _invest = State(initialValue: invest && Calm.kind(friend.id) != nil)
    }

    var body: some View {
        let f = friend, k = Calm.kind(f.id)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .bottom, spacing: 14) {
                    Pixel(name: "spr_" + f.id, width: 84, height: 119).charIdle()
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(Shelter.spriteBacking(f.id), in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 6) {
                        Text(f.name + (k.map { " · " + $0.title } ?? "")).appFont(24, .bold).lineLimit(1).minimumScaleFactor(0.7)
                        // 대사 대신 하는 일 한 줄 (투자 성향이 없는 인물은 기존 대사)
                        Text(f.does.isEmpty ? f.line : f.does).appFont(14, .semibold).foregroundStyle(Theme.sub)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                if k != nil {
                    Picker("갈래", selection: $invest) {
                        Text("규칙과 결과").tag(true)
                        Text("\(f.name) 이야기").tag(false)
                    }
                    .pickerStyle(.segmented)
                }
                if invest { InvestBranch(id: f.id, focus: focus) { invest = false } } else { novel(f) }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle(invest ? String(localized: "\(String(describing: f.name)) 규칙") : f.name).toolbarTitleDisplayMode(.inlineLarge)
    }

    @ViewBuilder private func novel(_ f: Friend) -> some View {
        Card {
            Text("소설에 나오는 곳").appFont(15, .bold)
            appear(String(localized: "본편"), f.main)
            appear(String(localized: "외전 『이종 공명』"), f.side)
            appear(String(localized: "프리퀄"), f.prequel.map { $0 + String(localized: " · 준비 중") })
            if f.side == nil {
                Text("앱 외전엔 안 나옴 · 쉼터엔 인터미션 체크인으로").appFont(12).foregroundStyle(Theme.muted)
            }
        }
        section(String(localized: "특징"), f.traits)
        section(String(localized: "배경"), f.background)
        Card {
            let open = f.scenes.filter { Self.sceneOpen($0.0, story) }, shut = f.scenes.filter { !Self.sceneOpen($0.0, story) }
            Text("대표 장면").appFont(15, .bold)
            Text("읽은 장면만").appFont(12).foregroundStyle(Theme.muted)
            ForEach(open, id: \.1) { k, v in
                VStack(alignment: .leading, spacing: 4) {
                    Text(k).appFont(12, .bold).foregroundStyle(Theme.teal)
                    Text(v).appFont(14).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 4)
            }
            if !shut.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "lock.fill").foregroundStyle(Theme.muted)
                    Text(Self.lockedText(shut.map(\.0))).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }
                .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
            }
        }
        Text("본편 9·10권 결말·일부 인물 이야기 제외").appFont(12).foregroundStyle(Theme.muted)
    }

    /// 장면 잠금: 앱에서 읽을 수 있는 책의 장을 끝까지 읽어야 열린다. 외전 장면 = 외전 마지막 장, 본편 1권 장면 = 본편 1권 1장.
    /// 앱에 없는 책(본편 2권~, 프리퀄)의 장면은 잠긴 채로 둔다
    static func sceneOpen(_ label: String, _ s: Story) -> Bool {
        switch label {
        case String(localized: "외전"): return s.isDone(.side, StoryBook.side.bundled - 1)
        case String(localized: "본편 1권"): return s.isDone(.vol1, 1)
        default: return false
        }
    }
    /// "본편 3·4권, 프리퀄 장면 3개 · 그 장 읽으면 열림"
    static func lockedText(_ labels: [String]) -> String {
        var vols: [String] = [], other: [String] = []
        for l in labels {
            if !AppModel.en, l.hasPrefix("본편 "), l.hasSuffix("권") { let v = String(l.dropFirst(3).dropLast()); if !vols.contains(v) { vols.append(v) } }
            else if !other.contains(l) { other.append(l) }
        }
        let parts = (vols.isEmpty ? [] : [String(localized: "본편 ") + vols.joined(separator: "·") + String(localized: "권")]) + other
        return parts.joined(separator: ", ") + String(localized: " 장면 \(String(describing: labels.count))개 · 그 장 읽으면 열림")
    }

    private func appear(_ k: String, _ v: String?) -> some View {
        HStack(alignment: .top) {
            Text(k).appFont(14).foregroundStyle(Theme.sub).frame(minWidth: 110, alignment: .leading)
            Text(v ?? String(localized: "나오지 않음")).appFont(14, v == nil ? .regular : .semibold).foregroundStyle(v == nil ? Theme.muted : Theme.ink)
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
