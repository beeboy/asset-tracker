import SwiftUI

/// 결 자세. 이름은 맥 결의 자리 몸짓 이름과 같다 (그림: gyeol-line/scenes/svg. 섬은 서 있는 장면, 잠금 화면도 서 있는 장면, 작은 섬은 서 있는 장면의 얼굴. 만드는 법 gyeol-line/island/v2/render_i.js·render_v.js)
public enum GyeolPose: String, CaseIterable, Sendable {
    case idle, wave, nod, think, back, cheer, thumb, laugh, shy, surprise, sad, kick, look, heart, worry, go, rise, fall, land, doze

    public static func of(_ s: String) -> GyeolPose {
        if s == "lookL" || s == "lookR" { return .look }
        return GyeolPose(rawValue: s) ?? .idle
    }

    public enum Look { case island, lock, face }

    /// 섬(검은 바탕)은 흰 테두리를 두른 그림, 잠금 화면은 맨 그림, 작은 섬은 얼굴만
    public func image(_ l: Look) -> Image {
        let k: String
        switch l {
        case .island: k = "i"
        case .lock: k = "l"
        case .face: k = "f"
        }
        return Image("gyeol_\(k)_\(rawValue)", bundle: .module)
    }

    /// 개발자 시험 단추 이름
    public var name: String {
        Self.names[self] ?? rawValue
    }
    static let names: [GyeolPose: String] = [
        .idle: "가만히", .wave: "손 흔들기", .nod: "끄덕임", .think: "생각", .back: "등 돌림",
        .cheer: "만세", .thumb: "엄지 척", .laugh: "웃음", .shy: "수줍음", .surprise: "놀람",
        .sad: "시무룩", .kick: "발장구", .look: "두리번", .heart: "손하트", .worry: "걱정",
        .go: "다짐", .rise: "들썩", .fall: "철렁", .land: "휴", .doze: "졸기",
    ]
}
