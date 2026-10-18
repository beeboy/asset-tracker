import SwiftUI

// 미션 안내 인물 (캐릭터 설정 스레드 시안 mission-flow-seri*): 미션 제목 아래 회색 안내 문구를 인물 말풍선으로.
// 화면 종류마다 동작이 다르다 — 안내: point, 결과 공개: spread, 퀴즈·결정: think → 맞으면 cheer 틀리면 point,
// 그래프 보기: back, 계획·구성 고르기: side, 정한 뒤: cheer, 입력: stand, 처음 인사: wave.
// 말은 해요체, 사실만 (투자 권유 아님).
enum Pose: String { case stand, spread, point, wave, think, cheer, side, back, sideL = "side_l" }

extension AppModel {
    /// 미션 안내 인물: 설정에서 고를 수 있고, 기본(자동)은 처음엔 세리, 그 뒤로는 가장 최근에 만난 인물
    var guideFriend: String {
        if (guidePick == "me" && meUnlocked) || (guidePick == nil && homeFriendShown == "me") { return "me" }
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
    @Environment(AppModel.self) private var model: AppModel?
    var body: some View {
        if friend == "me", let me = model?.me ?? MeChar.current {
            // 내 캐릭터는 동작 그림이 없어 선 모습 하나 (옆을 보는 동작만 뒤집는다)
            MeAvatar(me: me, width: height * 24 / 35, height: height * 34 / 35)
                .scaleEffect(x: pose == .sideL ? -1 : 1, y: 1)
                .frame(width: height * 240 / 280, height: height).accessibilityHidden(true)
        } else {
            sprite
        }
    }
    private var sprite: some View {
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
        let f = m.guideFriend, name = m.friendName(f)
        let text = Voice.say(self.text, m.voice(f), pose)
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

/// 인물별 말투. 문구(숫자·안내·면책)는 그대로 두고 말끝과 한마디만 바꾼다 (캐릭터 설정 스레드의 위젯 대사 기준)
/// 세리: 해요체, "……" 한 박자 (원문 그대로) · 시오: 짧은 반말, 센다 · 선배: 반말, 되묻는다
/// 이르: 차분한 반말, 서두르지 말라고 · 수아: 공손한 해요체, 제때
enum Voice {
    private static let banmal: [(String, String)] = [
        ("하시는군요.", "하는구나."), ("드리지 않아요", "주지 않아"), ("하셔야 해요", "해야 해"), ("같이 봐요.", "같이 보자."),
        ("정해요.", "정하자."), ("싶나요?", "싶어?"), ("아니에요", "아니야"), ("이에요", "이야"), ("예요", "야"),
        ("보세요", "봐"), ("넣으세요", "넣어"), ("주세요", "줘"),
    ]
    private static let ending = try! NSRegularExpression(pattern: "(어|아|해|여|와|워|꿔|춰|써|봐|돼|줘|져|려|내|가|서)요([.?!,]|$)")
    private static let pre: [String: [Pose: String]] = [
        "sio": [.wave: "왔네. ", .point: "짧게. ", .spread: "셌어. "],
        "seonbae": [.wave: "왔어? "],
        "ir": [.think: "서두르지 마. ", .spread: "흔들려도 괜찮아. ", .wave: "천천히 하자. ", .side: "천천히 골라. "],
        "sua": [.wave: "제때 오셨어요. ", .point: "순서대로 볼게요. "],
    ]
    private static let suf: [String: [Pose: String]] = [
        "seonbae": [.think: " 너라면 뭘 고를 것 같아?", .spread: " 왜 여기까지 왔다고 생각해?", .back: " 어디가 제일 눈에 띄어?", .side: " 어느 쪽이 마음 편해?"],
    ]
    static func say(_ t: String, _ f: String, _ pose: Pose) -> String {
        guard f != "seri" else { return t }
        var t = t.replacingOccurrences(of: "…… ", with: "").replacingOccurrences(of: "……", with: "").trimmingCharacters(in: .whitespaces)
        let decided = pose == .cheer && t.hasPrefix("정했")
        if f == "sio" || f == "seonbae" || f == "ir" {
            for (a, b) in banmal { t = t.replacingOccurrences(of: a, with: b) }
            t = ending.stringByReplacingMatches(in: t, range: NSRange(t.startIndex..., in: t), withTemplate: "$1$2")
        }
        var head = pre[f]?[pose] ?? "", tail = suf[f]?[pose] ?? ""
        // 계획을 정한 뒤: 선배는 이유를 적게 하고, 이르는 흔들려도 서두르지 말라고, 수아는 기록한다고
        if decided {
            switch f {
            case "seonbae": tail = " 왜 그렇게 정했는지 한 줄 적어 둬."
            case "ir": tail = " 이제 흔들려도 서두르지 마."
            case "sua": head = "좋아요, 기록해 둘게요. "
            case "sio": head = "됐어. "
            default: break
            }
        }
        return head + t + tail
    }
}

/// 설정 > 함께할 인물: 한 줄(그림·이름·바꾸기) + 자동 토글. 토글 = 예전 "자동" 칩, 끄면 고른 인물로 고정
struct GuidePickRow: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let _ = m.guideTick
        let opts: [(String, String)] = Shelter.friends.enumerated().filter { m.friendOn($0.offset) }.map { ($0.element.id, $0.element.name) }
            + (m.meUnlocked ? [("me", m.friendName("me"))] : [])
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                MotionSprite(friend: m.guideFriend, pose: .wave, height: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text(m.friendName(m.guideFriend)).appFont(15, .bold)
                    Text("홈·미션에서 말 걸어요").appFont(12).foregroundStyle(Theme.sub)
                }
                Spacer()
                Menu {
                    ForEach(opts, id: \.0) { id, name in
                        Button { m.guidePick = id } label: { Text(name) }
                    }
                } label: {
                    Text("바꾸기").appFont(14, .bold).foregroundStyle(Theme.teal).frame(minWidth: 44, minHeight: 44)
                }
            }
            Divider().overlay(Theme.line)
            Toggle(isOn: Binding(get: { m.guidePick == nil }, set: { m.guidePick = $0 ? nil : m.guideFriend })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("새 친구 만나면 자동 교체").appFont(14, .semibold)
                    Text("처음 세리 → 최근 만난 인물").appFont(12).foregroundStyle(Theme.sub)
                }
            }.tint(Theme.teal).frame(minHeight: 44)
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }
}
