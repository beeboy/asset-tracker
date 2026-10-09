import SwiftUI

// 쉼터: 친구, 서재(외전 프롤로그~5장), 돌아온 물건. 시장 숫자와는 상관없고 내 행동으로만 열린다.
struct ShelterView: View {
    @Environment(AppModel.self) private var m
    @State private var sel = "seri"
    @State private var item: String? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("쉼터").font(.system(size: 22, weight: .bold))
                hero
                header("친구", "\((0..<5).filter { m.friendOn($0) }.count)/5")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                    ForEach(Array(Shelter.friends.enumerated()), id: \.element.id) { i, f in friendTile(i, f) }
                }
                header("서재 · 외전 『이종 공명』", "\((0..<6).filter { m.chapterOn($0) }.count)/6장 열림")
                if let last = m.readLast, m.chapterOn(last) {
                    NavigationLink(value: "read:\(last)") {
                        HStack { Text("이어 읽기 · \(Shelter.chapters[last].title)"); Spacer(); Text("›") }
                            .font(.system(size: 15, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 14).frame(minHeight: 48)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain)
                }
                library
                header("쉼터에 돌아온 물건", "\(m.itemsOn)/10")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                    ForEach(Array(Shelter.items.enumerated()), id: \.element.id) { i, it in itemTile(i, it) }
                }
                itemNote
                Text("앱에서는 외전 프롤로그부터 5장까지만 읽을 수 있어요. 친구와 장은 내 행동으로만 열리고, 시장 숫자와는 상관없어요. 물건은 오늘의 1분을 7일 연속 할 때마다 하나씩 돌아와요.")
                    .font(.system(size: 12)).foregroundStyle(Theme.muted).lineSpacing(3)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("").navigationBarTitleDisplayMode(.inline)
        .onAppear { sel = m.homeFriend }
    }

    private func header(_ t: String, _ r: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(t).font(.system(size: 15, weight: .bold)); Spacer()
            Text(r).font(.system(size: 13)).foregroundStyle(Theme.sub)
        }
    }

    private var hero: some View {
        let i = Shelter.friends.firstIndex { $0.id == sel } ?? 0, f = Shelter.friends[i], on = m.friendOn(i)
        return HStack(spacing: 14) {
            Pixel(name: (on ? "spr_" : "sil_") + f.id, width: 84, height: 119)
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(Color(hex: 0x232B42), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(on ? f.name : "???").font(.system(size: 20, weight: .bold))
                    Text(m.friendWhen(i)).font(.system(size: 12)).foregroundStyle(Color(hex: 0x8FD0FF))
                }
                Text(on ? f.bio : "아직 만나지 않았어요. 인터미션 \(i)주차 체크인: \(Shelter.weekSteps[max(0, i - 1)].task).")
                    .font(.system(size: 13)).lineSpacing(3).foregroundStyle(Color(hex: 0xD5D9E6))
                    .fixedSize(horizontal: false, vertical: true)
                if on { Text(f.line).font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.gold) }
                if on && m.homeFriend != f.id {
                    Button("홈에 두기") { m.homeFriend = f.id }
                        .font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.ink)
                        .padding(.horizontal, 14).frame(minHeight: 36).background(Theme.mint, in: Capsule())
                } else if m.homeFriend == f.id {
                    Text("홈에 있어요").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.ink)
                        .padding(.horizontal, 10).padding(.vertical, 3).background(Theme.gold, in: Capsule())
                }
            }
            Spacer(minLength: 0)
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
                Text(on ? f.name : "???").font(.system(size: 12, weight: .bold))
                Text(on && m.homeFriend == f.id ? "홈에 있음" : i == 0 ? "처음부터" : "\(i)주차")
                    .font(.system(size: 10)).foregroundStyle(Theme.sub)
            }
            .frame(maxWidth: .infinity, minHeight: 96)
            .background(cur ? Theme.mintBg : on ? .white : Color(hex: 0xF6F7F8), in: RoundedRectangle(cornerRadius: 12))
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
                    Text(ch.title).font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.teal).frame(width: 44, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(on ? "외전 \(ch.title)" : "???").font(.system(size: 14, weight: .bold))
                        Text(on ? (i < 2 ? "앱 시작 3단계에서 열림" : "\(Shelter.friends[fi].name)와 같이 열림")
                             : (i < 2 ? "앱 시작 3단계를 마치면 열려요" : "인터미션 \(fi)주차가 되면 열려요"))
                            .font(.system(size: 12)).foregroundStyle(Theme.sub)
                    }
                    Spacer(minLength: 0)
                    Text(on ? (m.readPos.contains(i) ? "이어 읽기" : "읽기") : "잠김").font(.system(size: 12)).foregroundStyle(Theme.muted)
                }
                .padding(.horizontal, 14).frame(minHeight: 64)
                .foregroundStyle(on ? Theme.ink : Theme.muted)
                .contentShape(Rectangle())
                if i > 0 { Divider().overlay(Theme.line) }
                if on { NavigationLink(value: "read:\(i)") { row }.buttonStyle(.plain) }
                else { Button { sel = ch.friend } label: { row }.buttonStyle(.plain) }
            }
            Divider().overlay(Theme.line)
            HStack(spacing: 10) {
                Image("art_ch6").interpolation(.none).resizable().frame(width: 64, height: 40)
                    .grayscale(1).brightness(-0.3).opacity(0.6).clipShape(RoundedRectangle(cornerRadius: 6))
                Text("6장").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.teal).frame(width: 44, alignment: .leading)
                VStack(alignment: .leading, spacing: 2) {
                    Text("중첩된 현실 외전에서 이어져요").font(.system(size: 14, weight: .bold))
                    Text("앱에서는 5장까지만 열려요").font(.system(size: 12))
                }
                Spacer(minLength: 0)
            }
            .foregroundStyle(Theme.sub)
            .padding(.horizontal, 14).frame(minHeight: 56)
            .background(Color(hex: 0xF7F8FA))
        }
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private func itemTile(_ i: Int, _ it: ShelterItem) -> some View {
        let on = i < m.itemsOn
        return Button { if on { item = it.id } } label: {
            VStack(spacing: 2) {
                Pixel(name: "art_" + it.id + (on ? "" : "_l"), width: 32, height: 32)
                Text(on ? it.name : "???").font(.system(size: 11, weight: .bold)).lineLimit(2).multilineTextAlignment(.center)
                Text("연속 \((i + 1) * 7)일").font(.system(size: 10)).foregroundStyle(Theme.sub)
            }
            .foregroundStyle(on ? Theme.ink : Theme.muted)
            .frame(maxWidth: .infinity, minHeight: 84)
            .background(on ? (item == it.id ? Theme.mintBg : .white) : Color(hex: 0xF6F7F8), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? (item == it.id ? Theme.teal : Theme.border) : Theme.dash, style: StrokeStyle(lineWidth: 2, dash: on ? [] : [4, 3])))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? it.name : "오늘의 1분 \((i + 1) * 7)일 연속에 돌아오는 물건")
    }

    private var itemNote: some View {
        let cur = Shelter.items.first { $0.id == item } ?? (m.itemsOn > 0 ? Shelter.items[m.itemsOn - 1] : nil)
        return HStack(spacing: 10) {
            Pixel(name: "art_" + (cur?.id ?? "barley_tea") + (cur == nil ? "_l" : ""), width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(cur?.name ?? "아직 돌아오지 않았어요").font(.system(size: 14, weight: .bold))
                Text(cur?.line ?? "오늘의 1분을 7일 연속 하면 첫 물건이 돌아와요.").font(.system(size: 13)).foregroundStyle(Theme.sub)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(.white, in: RoundedRectangle(cornerRadius: 12))
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

// 서재 읽기: 원고는 서버에서 받는다 (지금은 스텁이라 본문 자리만 회색 줄)
struct ReaderView: View {
    @Environment(AppModel.self) private var m
    @State var index: Int
    @State private var size = 17.0
    @State private var dark = false
    @State private var paras: [StoryParagraph] = []

    var body: some View {
        let accent = dark ? Theme.mint : Theme.teal
        let lineCol = dark ? Color(hex: 0x3A4459) : Theme.border
        let ch = Shelter.chapters[index]
        let fi = Shelter.friends.firstIndex { $0.id == ch.friend } ?? 0
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 6) {
                    Spacer()
                    ForEach([(15.0, 12.0, "작은 글자"), (17.0, 15.0, "보통 글자"), (20.0, 18.0, "큰 글자")], id: \.0) { s, f, label in
                        Button("가") { size = s }
                            .font(.system(size: f, weight: .bold))
                            .frame(minWidth: 36, minHeight: 36)
                            .foregroundStyle(size == s ? (dark ? Color(hex: 0x141824) : .white) : (dark ? Color(hex: 0xEEF0F7) : Theme.ink))
                            .background(size == s ? accent : .clear, in: Capsule())
                            .overlay(Capsule().stroke(size == s ? accent : (dark ? Color(hex: 0x4A5578) : Theme.border), lineWidth: 2))
                            .accessibilityLabel(label)
                    }
                    Button(dark ? "밝게" : "어둡게") { dark.toggle() }
                        .font(.system(size: 13, weight: .bold)).padding(.horizontal, 12).frame(minHeight: 36)
                        .foregroundStyle(dark ? Color(hex: 0xEEF0F7) : Theme.ink)
                        .background(dark ? Color(hex: 0x262E45) : .white, in: Capsule())
                        .overlay(Capsule().stroke(dark ? Color(hex: 0x4A5578) : Theme.border, lineWidth: 2))
                }
                ChapterCover(index: index).aspectRatio(1.6, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 12))
                Text("외전 『이종 공명』 · \(ch.title)").font(.system(size: 20, weight: .bold))
                Text(m.readPos.contains(index) ? "읽던 곳에서 이어 읽는 중" : "처음부터").font(.system(size: 13)).foregroundStyle(dark ? Color(hex: 0xA6ADC6) : Theme.sub2)
                ForEach(paras.indices, id: \.self) { p in
                    VStack(alignment: .leading, spacing: size * 0.55) {
                        ForEach(0..<paras[p].lines, id: \.self) { l in
                            GeometryReader { g in
                                RoundedRectangle(cornerRadius: 4).fill(lineCol)
                                    .frame(width: g.size.width * (l == paras[p].lines - 1 ? paras[p].lastWidth : 1))
                            }
                            .frame(height: size * 0.6)
                        }
                    }
                    .padding(.bottom, size * 0.75)
                    .accessibilityHidden(true)
                    if p == 1 {
                        Button { } label: {
                            HStack(spacing: 10) {
                                Pixel(name: "spr_" + ch.friend, width: 24, height: 34)
                                VStack(alignment: .leading) {
                                    Text("\(Shelter.friends[fi].name) 처음 나오는 장면").font(.system(size: 13, weight: .bold))
                                    Text("쉼터에서 만날 수 있어요 ›").font(.system(size: 12))
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(dark ? Color(hex: 0x262E45) : Theme.cream, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(dark ? Color(hex: 0x4A5578) : Theme.yellow))
                        }
                        .buttonStyle(.plain).padding(.bottom, 14)
                    }
                }
                if index < 5 && m.chapterOn(index + 1) {
                    Button { m.readPos.insert(index); index += 1; m.readLast = index; load() } label: {
                        Text("다음: \(Shelter.chapters[index + 1].title) ›").font(.system(size: 15, weight: .bold)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity, minHeight: 48).background(accent, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain)
                } else {
                    Text(index == 5 ? "중첩된 현실 외전에서 이어져요" : "다음 장은 " + (index >= 1 ? m.friendWhen(fi + 1) + "에" : "앱 시작 3단계를 마치면") + " 열려요.")
                        .font(.system(size: 14, weight: .bold)).multilineTextAlignment(.center)
                        .foregroundStyle(dark ? Theme.gold : Theme.sub)
                        .frame(maxWidth: .infinity).padding(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(dark ? Color(hex: 0x4A5578) : Theme.dash, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                }
            }
            .padding(16)
            .foregroundStyle(dark ? Color(hex: 0xEEF0F7) : Theme.ink)
        }
        .background(dark ? Color(hex: 0x141824) : Color(hex: 0xFBFAF7))
        .toolbarBackground(dark ? Color(hex: 0x141824) : Color(hex: 0xFBFAF7), for: .navigationBar)
        .navigationTitle("서재").navigationBarTitleDisplayMode(.inline)
        .onAppear { m.readLast = index; load() }
    }

    private func load() { Task { paras = await m.stories.chapter(index) } }
}
