import SwiftUI
import AVFoundation

/// 파도 타기 소리·진동. 소리 파일 없이 짧은 소리 4개를 처음 한 번 메모리에서 만들어 둔다 (각 10KB 안팎).
/// 오디오 세션은 .ambient: 무음 스위치를 따르고 다른 앱 음악을 끊지 않는다.
/// 플레이 화면은 엔진 이벤트를 그대로 넘기면 된다: WaveFeedback.shared.play(events)
@MainActor
final class WaveFeedback {
    static let shared = WaveFeedback()
    static let soundKey = "waveSound", hapticKey = "waveHaptic"

    enum Sound: CaseIterable { case splash, coin, plunge, yawn }

    private var players: [Sound: AVAudioPlayer] = [:]
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let note = UINotificationFeedbackGenerator()

    private var soundOn: Bool { UserDefaults.standard.object(forKey: Self.soundKey) as? Bool ?? true }
    private var hapticOn: Bool { UserDefaults.standard.object(forKey: Self.hapticKey) as? Bool ?? true }

    /// 게임 화면이 뜰 때 부르면 첫 소리가 늦지 않다
    func prepare() {
        light.prepare(); rigid.prepare()
        guard soundOn, players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
        for s in Sound.allCases {
            if let p = try? AVAudioPlayer(data: Self.wav(s)) { p.prepareToPlay(); players[s] = p }
        }
    }

    func play(_ events: [WaveEngine.Event]) { events.forEach(play) }

    func play(_ e: WaveEngine.Event) {
        switch e {
        case .board(_, let perfect):
            buzz { perfect ? rigid.impactOccurred() : light.impactOccurred() }; sound(.splash)
        case .leave(_, _, let points):
            buzz { rigid.impactOccurred() }
            if points > 0 { sound(.coin) }
        case .splash:
            buzz { note.notificationOccurred(.error) }; sound(.plunge)
        case .calm:
            sound(.yawn)
        case .streak:
            crown()
        case .fever(let on):
            if on { crown() }
        case .flip:
            buzz { light.impactOccurred(intensity: 0.7) }
        case .land, .item, .mission:
            buzz { note.notificationOccurred(.success) }; sound(.coin)
        case .time:
            break
        case .saved:
            buzz { rigid.impactOccurred() }
        case .end:
            break
        }
    }

    /// 왕관: 톡 톡 두 번
    func crown() {
        buzz {
            rigid.impactOccurred(intensity: 0.8)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { [rigid] in rigid.impactOccurred() }
        }
        sound(.coin)
    }

    private func buzz(_ f: () -> Void) { if hapticOn { f() } }

    private func sound(_ s: Sound) {
        guard soundOn else { return }
        if players.isEmpty { prepare() }
        guard let p = players[s] else { return }
        p.currentTime = 0; p.play()
    }

    // MARK: 소리 만들기 (22.05kHz 모노 16비트 WAV)

    private static let rate = 22050.0

    private static func wav(_ s: Sound) -> Data {
        let (dur, f): (Double, (Double) -> Double) = {
            switch s {
            case .splash:   // 첨벙: 짧게 터지는 물소리
                return (0.18, { t in noise() * exp(-t * 28) * 0.5 })
            case .coin:     // 동전: 띵-딩 두 음
                return (0.22, { t in
                    let hz = t < 0.07 ? 988.0 : 1319.0
                    let t0 = t < 0.07 ? t : t - 0.07
                    return sin(2 * .pi * hz * t) * exp(-t0 * 18) * 0.35
                })
            case .plunge:   // 풍덩: 낮게 꺼지는 소리 + 물
                return (0.32, { t in
                    let hz = 220 * exp(-t * 4)
                    return (sin(2 * .pi * hz * t) * 0.45 + noise() * 0.15) * exp(-t * 9)
                })
            case .yawn:     // 하품: 천천히 내려가는 음
                return (0.45, { t in
                    let hz = 420 - 220 * t / 0.45
                    let env = sin(.pi * t / 0.45)
                    return sin(2 * .pi * hz * t) * env * 0.25
                })
            }
        }()
        let n = Int(dur * rate)
        var pcm = Data(capacity: n * 2)
        for i in 0..<n {
            let v = Int16(max(-1, min(1, f(Double(i) / rate))) * 32000)
            withUnsafeBytes(of: v.littleEndian) { pcm.append(contentsOf: $0) }
        }
        var d = Data()
        func u32(_ v: UInt32) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        func u16(_ v: UInt16) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        d.append(contentsOf: Array("RIFF".utf8)); u32(UInt32(36 + pcm.count))
        d.append(contentsOf: Array("WAVEfmt ".utf8)); u32(16); u16(1); u16(1)
        u32(UInt32(rate)); u32(UInt32(rate) * 2); u16(2); u16(16)
        d.append(contentsOf: Array("data".utf8)); u32(UInt32(pcm.count))
        d.append(pcm)
        return d
    }

    private static func noise() -> Double { Double.random(in: -1...1) }
}

/// 설정 '화면' 묶음에 넣는 스위치 두 줄
struct WaveFeedbackToggles: View {
    @AppStorage(WaveFeedback.soundKey) private var sound = true
    @AppStorage(WaveFeedback.hapticKey) private var haptic = true

    var body: some View {
        VStack(spacing: 0) {
            row("게임 소리", "파도 타기 효과음 · 무음 모드면 안 나요", $sound)
            Divider().overlay(Theme.line)
            row("게임 진동", "탈 때 톡, 내릴 때 탁", $haptic)
        }
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private func row(_ t: String, _ s: String, _ on: Binding<Bool>) -> some View {
        Toggle(isOn: on) {
            VStack(alignment: .leading, spacing: 2) {
                Text(t).appFont(15, .bold)
                Text(s).appFont(13).foregroundStyle(Theme.sub)
            }
        }
        .tint(Theme.teal)
        .padding(.horizontal, 16).frame(minHeight: 60)
    }
}
