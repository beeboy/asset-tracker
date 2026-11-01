import ActivityKit
import Foundation
import Observation

/// 앱 쪽 결 섬 조종: 띄우기·고치기·내리기, 클로드 연동 열쇠. 섬을 바꾸는 곳은 여기 하나뿐이다
@MainActor @Observable
public final class GyeolLive {
    public static let shared = GyeolLive()
    @ObservationIgnored private let d = UserDefaults.standard

    /// 섬에 결 띄우기 (기본 꺼짐)
    public var on: Bool = UserDefaults.standard.bool(forKey: "gyeol.on") {
        didSet {
            d.set(on, forKey: "gyeol.on")
            Task { if on { await start(pose: .wave) } else { await endAll() } }
        }
    }
    /// 클로드 연동: 맥이 답 끝 몸짓을 푸시로 보낸다 (기본 꺼짐). 바꾸면 푸시 받는 섬으로 다시 띄운다
    public var claude: Bool = UserDefaults.standard.bool(forKey: "gyeol.claude") {
        didSet { d.set(claude, forKey: "gyeol.claude"); if on { Task { await endAll(); await start(pose: .wave) } } }
    }
    /// 지금 섬의 푸시 열쇠 (16진). 섬을 새로 띄울 때마다 바뀐다
    public private(set) var pushToken: String?
    /// 섬을 새로 띄우는 열쇠 (iOS 17.2+)
    public private(set) var startToken: String?
    /// 기기 설정에서 실시간 현황을 껐으면 false
    public var allowed: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    @ObservationIgnored private var state = GyeolAttributes.ContentState(pose: GyeolPose.idle.rawValue, n: 0)
    @ObservationIgnored private var watching = false
    /// 푸시 열쇠를 듣고 있는 섬 (앱이 앞으로 올 때마다 또 듣지 않게)
    @ObservationIgnored private var heard: String?

    private init() {}

    private var current: Activity<GyeolAttributes>? { Activity<GyeolAttributes>.activities.first { $0.activityState == .active } }

    /// 앱이 앞으로 올 때: 켜져 있는데 섬이 없으면 (8시간이 지나 내려갔으면) 다시 띄운다
    public func resume() {
        guard on else { return }
        if let a = current { watch(a) } else { Task { await start(pose: .wave) } }
    }

    /// 위젯 줄이 새로 나오면: 바뀐 게 있을 때만 섬을 고친다. 줄은 맥 푸시용으로 App Group 에도 남긴다
    public func feed(lines: [String], chip: String?, pose: GyeolPose) {
        guard on else { return }
        GyeolShelf.lines = lines; GyeolShelf.chip = chip
        guard lines != state.lines || chip != state.chip || pose.rawValue != state.pose else { return }
        var s = state
        s.lines = lines; s.chip = chip; s.pose = pose.rawValue; s.say = nil; s.n += 1
        Task { await apply(s) }
    }

    /// 개발자 시험: 자세 하나와 말 한 줄
    public func show(_ pose: GyeolPose, say: String? = nil) {
        guard on else { return }
        var s = state
        s.pose = pose.rawValue; s.say = say; s.n += 1
        Task { await apply(s) }
    }

    private func apply(_ s: GyeolAttributes.ContentState) async {
        state = s   // 섬이 없으면 남겨 두었다가 띄울 때 쓴다 (띄우기는 on·resume 만)
        guard let a = current else { return }
        await a.update(ActivityContent(state: s, staleDate: nil))
    }

    private func start(pose: GyeolPose) async {
        guard on, allowed, current == nil else { return }
        var s = state
        s.pose = pose.rawValue; s.say = nil; s.n += 1
        if s.lines == nil { s.lines = GyeolShelf.lines; s.chip = GyeolShelf.chip }
        state = s
        do {
            let a = try Activity.request(attributes: GyeolAttributes(), content: ActivityContent(state: s, staleDate: nil), pushType: claude ? .token : nil)
            watch(a)
        } catch {
            print("결 섬 못 띄움: \(error)")
        }
        if claude { watchStart() }
    }

    private func endAll() async {
        pushToken = nil
        for a in Activity<GyeolAttributes>.activities { await a.end(nil, dismissalPolicy: .immediate) }
    }

    private func watch(_ a: Activity<GyeolAttributes>) {
        state = a.content.state
        guard claude, heard != a.id else { return }
        heard = a.id
        Task { for await t in a.pushTokenUpdates { pushToken = t.map { String(format: "%02x", $0) }.joined() } }
    }

    private func watchStart() {
        guard !watching else { return }
        watching = true
        if #available(iOS 17.2, *) {
            Task { for await t in Activity<GyeolAttributes>.pushToStartTokenUpdates { startToken = t.map { String(format: "%02x", $0) }.joined() } }
        }
    }
}
