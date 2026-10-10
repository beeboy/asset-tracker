import SwiftUI

// 미션 안내 인물 (캐릭터 설정 스레드 시안 mission-flow-seri*): 미션 제목 아래 회색 안내 문구를 인물 말풍선으로.
// 화면 종류마다 동작이 다르다 — 안내: point, 결과 공개: spread, 퀴즈·결정: think → 맞으면 cheer 틀리면 point,
// 그래프 보기: back, 계획·구성 고르기: side, 정한 뒤: cheer, 입력: stand, 처음 인사: wave.
// 말은 해요체, 사실만 (투자 권유 아님).
enum Pose: String { case stand, spread, point, wave, think, cheer, side, back, sideL = "side_l" }

extension AppModel {
    /// 미션 안내 인물: 설정에서 고를 수 있고, 기본(자동)은 처음엔 세리, 그 뒤로는 가장 최근에 만난 인물
    var guideFriend: String {
        if let g = guidePick, let i = Shelter.friends.firstIndex(where: { $0.id == g }), friendOn(i) { return g }
        let open = (0..<Shelter.friends.count).filter { friendOn($0) }
        return Shelter.friends[open.last ?? 0].id
    }
    var guidePick: String? {
        get { UserDefaults.standard.string(forKey: "guidePick") }
        set { UserDefaults.standard.set(newValue, forKey: "guidePick"); guideTick += 1 }
    }
}

/// 동작 그림 하나 (최근접 확대)
struct MotionSprite: View {
    let friend: String
    let pose: Pose
    var height: CGFloat = 76
    var body: some View {
        Image("mo_\(friend)_\(pose.rawValue)").interpolation(.none).resizable().aspectRatio(240.0 / 280.0, contentMode: .fit)
            .frame(height: height).accessibilityHidden(true)
    }
}

/// 미션 말풍선: 인물 + 이름(+ 동작 설명) + 말
struct GuideBubble: View {
    @Environment(AppModel.self) private var m
    let pose: Pose
    let text: String
    var note: String? = nil          // "그래프 쪽을 돌아봄" 처럼 이름 옆 작은 글
    var body: some View {
        let _ = m.guideTick
        let f = m.guideFriend, name = Shelter.friends.first { $0.id == f }?.name ?? "세리"
        HStack(alignment: .center, spacing: 10) {
            MotionSprite(friend: f, pose: pose).frame(width: 66)
            VStack(alignment: .leading, spacing: 4) {
                Text(name + (note.map { " · " + $0 } ?? "")).appFont(12, .semibold).foregroundStyle(Theme.blue)
                Text(text).appFont(14).foregroundStyle(Theme.ink).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(name): \(text)")
    }
}

/// 설정 > 미션 안내 인물
struct GuidePickRow: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let _ = m.guideTick
        let opts: [(String?, String)] = [(nil, "자동")] + Shelter.friends.enumerated().filter { m.friendOn($0.offset) }.map { ($0.element.id, $0.element.name) }
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                MotionSprite(friend: m.guideFriend, pose: .wave, height: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text("미션 안내 인물").appFont(15, .bold)
                    Text("자동: 처음엔 세리, 그 뒤로는 가장 최근에 만난 인물").appFont(12).foregroundStyle(Theme.sub)
                }
            }
            FlowRow(spacing: 6) {
                ForEach(opts, id: \.1) { id, name in
                    let on = m.guidePick == id
                    Button { m.guidePick = id } label: {
                        Text(name).appFont(13, on ? .bold : .regular).padding(.horizontal, 12).frame(minHeight: 34)
                            .foregroundStyle(on ? .white : Theme.ink)
                            .background(on ? Theme.teal : Theme.card, in: Capsule())
                            .overlay(Capsule().stroke(on ? Theme.teal : Theme.border))
                    }.buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }
}
