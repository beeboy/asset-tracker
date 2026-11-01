#if GYEOLPIP
import AVFoundation
import AVKit
import CoreText
import SwiftUI
import UIKit

// 결 PiP: naeilo가 작은 창(PiP)에 결을 그려 다른 앱 위에도 띄운다.
// 아이폰 클로드 앱에서 답이 끝나면 클로드가 커스텀 커넥터 도구 gesture를 부르고, 중계(proxy/ 15번)가 웹소켓으로 넘겨 결이 몸짓을 한다.
// 그림은 결의 자리 구운 장면(gyeol-bake.json, GyeolBake·GyeolDraw), 언제 무엇을 하나는 bake/brain.js·tail.js를 줄여 옮겼다.
// 개발자 기기만 켤 수 있고 기본은 꺼짐. 빼려면 GYEOLPIP 플래그와 Naeilo/Gyeol/GyeolPip*.swift·GyeolBake·GyeolDraw·gyeol-bake.json.

private let relay = "https://asset-ai.drinker.workers.dev"

@MainActor @Observable
final class GyeolPip {
    static let shared = GyeolPip()
    nonisolated static let debugGesture = false   // 몸짓 시험 중: 작은 창 왼쪽 아래에 받은 몸짓과 재생 결과를 빨간 글로 띄운다
    @ObservationIgnored private let d = UserDefaults.standard
    var on: Bool { didSet { d.set(on, forKey: "gyeolPip.on"); on ? start() : stop() } }
    var voice: Bool { didSet { d.set(voice, forKey: "gyeolPip.voice") } }   // 속마음을 목소리로도 (유나)
    private(set) var linked = false      // 중계 웹소켓
    private(set) var active = false      // 작은 창이 떠 있음
    private(set) var possible = false    // 작은 창을 열 수 있음
    private(set) var mcp: String?        // 클로드 커넥터 주소
    var note: String?
    var size: String?        // 작은 창이 실제로 그려지는 크기
    @ObservationIgnored let host = GyeolPipHost()
    var engine: GyeolEngine { GyeolEngine.shared }
    @ObservationIgnored private var started = false
    @ObservationIgnored private var pip: AVPictureInPictureController?
    @ObservationIgnored private let del = GyeolPipDelegate()
    @ObservationIgnored private var kvo: NSKeyValueObservation?
    @ObservationIgnored private var link: GyeolLink?
    @ObservationIgnored private var obs: [NSObjectProtocol] = []

    private init() {
        on = d.bool(forKey: "gyeolPip.on")
        mcp = d.string(forKey: "gyeolPip.mcp")
        voice = d.object(forKey: "gyeolPip.voice") as? Bool ?? true
    }

    /// 설정 화면이 뜰 때: 켜져 있으면 다시 잇는다
    func wake() { if on { start() } }

    private func start() {
        guard !started else { engine.resume(); return }
        started = true
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        engine.start(layer: host.disp)
        dark = nil; mode()
        // 작은 창이 다른 앱 위에 있는 동안엔 화면 바뀜 소식이 안 올 수 있어 3초마다 본다 (가벼운 값 하나 읽기)
        modeTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { _ in Task { @MainActor in GyeolPip.shared.mode() } }
        if AVPictureInPictureController.isPictureInPictureSupported() {
            let p = AVPictureInPictureController(contentSource: .init(sampleBufferDisplayLayer: host.disp, playbackDelegate: del))
            p.delegate = del
            p.canStartPictureInPictureAutomaticallyFromInline = true
            kvo = p.observe(\.isPictureInPicturePossible, options: [.initial, .new]) { _, c in
                let v = c.newValue ?? false
                Task { @MainActor in GyeolPip.shared.possible = v }
            }
            pip = p
        } else { note = String(localized: "이 기기는 작은 창을 지원하지 않아요") }
        let nc = NotificationCenter.default
        obs = [
            nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in Task { @MainActor in GyeolPip.shared.background() } },
            nc.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { _ in Task { @MainActor in GyeolPip.shared.foreground() } },
        ]
        connect()
    }

    private func stop() {
        pip?.stopPictureInPicture(); pip = nil; kvo = nil
        obs.forEach { NotificationCenter.default.removeObserver($0) }; obs = []; started = false
        link?.close(); link = nil; linked = false; active = false; possible = false
        modeTimer?.invalidate(); modeTimer = nil
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func toggleWindow() {
        guard let pip else { return }
        pip.isPictureInPictureActive ? pip.stopPictureInPicture() : pip.startPictureInPicture()
    }

    private func connect() {
        guard link == nil, let tok = Sync.shared.creds?.token else { return }
        link = GyeolLink(token: tok)
    }
    /// 앱이 뒤로 가도 작은 창이 떠 있으면 계속 그린다. 창이 없으면 쉰다
    private func background() { if !active { engine.pause(); link?.close(); link = nil; linked = false } }
    private func foreground() { if on { engine.resume(); connect(); mode() } }
    /// 앱 안 밝기 설정이 아니라 기기 설정을 따른다 (작은 창은 다른 앱 위에 뜬다)
    func mode() {
        let d = UIScreen.main.traitCollection.userInterfaceStyle == .dark
        if d != dark { dark = d; engine.setDark(d) }
    }
    @ObservationIgnored private var dark: Bool?
    @ObservationIgnored private var modeTimer: Timer?

    // ---------- 작은 창·중계 소식 ----------
    func pipChanged(_ v: Bool) {
        active = v
        if !v && UIApplication.shared.applicationState == .background { background() }
    }
    func pipFailed(_ e: Error) { note = e.localizedDescription }
    func linkChanged(_ v: Bool) { linked = v }

    /// 클로드 커넥터 주소: 처음 물을 때 중계가 만든다. 개발자 동기화 토큰으로만 받는다
    func fetchConnector() async {
        guard let tok = Sync.shared.creds?.token, let url = URL(string: relay + "/gyeol/mcp") else { note = String(localized: "개발자 동기화를 먼저 켜 주세요"); return }
        var req = URLRequest(url: url, timeoutInterval: 20)
        req.setValue("Bearer " + tok, forHTTPHeaderField: "Authorization")
        guard let res = try? await URLSession.shared.data(for: req),
              let j = try? JSONSerialization.jsonObject(with: res.0) as? [String: Any], let u = j["url"] as? String
        else { note = String(localized: "커넥터 주소를 받지 못했어요"); return }
        mcp = u; d.set(u, forKey: "gyeolPip.mcp")
        UIPasteboard.general.string = u
        note = String(localized: "커넥터 주소를 복사했어요")
    }
}

/// AVKit 대리자: 메인 액터 밖에서 불려도 되게 따로 둔다
final class GyeolPipDelegate: NSObject, AVPictureInPictureControllerDelegate, AVPictureInPictureSampleBufferPlaybackDelegate {
    func pictureInPictureControllerDidStartPictureInPicture(_ c: AVPictureInPictureController) { Task { @MainActor in GyeolPip.shared.pipChanged(true) } }
    func pictureInPictureControllerDidStopPictureInPicture(_ c: AVPictureInPictureController) { Task { @MainActor in GyeolPip.shared.pipChanged(false) } }
    func pictureInPictureController(_ c: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) { Task { @MainActor in GyeolPip.shared.pipFailed(error) } }
    func pictureInPictureController(_ c: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler done: @escaping (Bool) -> Void) { done(true) }
    // 영상이 아니라 늘 지금인 그림: 재생 막대 없이, 멈춤 없이
    func pictureInPictureController(_ c: AVPictureInPictureController, setPlaying playing: Bool) {}
    func pictureInPictureControllerTimeRangeForPlayback(_ c: AVPictureInPictureController) -> CMTimeRange { CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity) }
    func pictureInPictureControllerIsPlaybackPaused(_ c: AVPictureInPictureController) -> Bool { false }
    func pictureInPictureController(_ c: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {
        let w = Int(newRenderSize.width), h = Int(newRenderSize.height)
        GyeolEngine.shared.setWindow(Double(w))
        Task { @MainActor in GyeolPip.shared.size = "\(w)×\(h)" }   // 작은 창 크기(픽셀). 크기를 바꿀 때마다 온다
    }
    func pictureInPictureController(_ c: AVPictureInPictureController, skipByInterval skipInterval: CMTime, completion done: @escaping () -> Void) { done() }
}

/// 작은 창의 원본 화면. 설정에서 미리보기로 보이고, 작은 창을 열면 iOS가 이 층을 창으로 옮긴다
final class GyeolPipHost: UIView {
    override class var layerClass: AnyClass { AVSampleBufferDisplayLayer.self }
    var disp: AVSampleBufferDisplayLayer { layer as! AVSampleBufferDisplayLayer }
    override init(frame: CGRect) {
        super.init(frame: frame)
        disp.videoGravity = .resizeAspect
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (_: GyeolPipHost, _: UITraitCollection) in GyeolPip.shared.mode() }
        var tb: CMTimebase?
        CMTimebaseCreateWithSourceClock(allocator: kCFAllocatorDefault, sourceClock: CMClockGetHostTimeClock(), timebaseOut: &tb)
        if let tb { CMTimebaseSetTime(tb, time: .zero); CMTimebaseSetRate(tb, rate: 1); disp.controlTimebase = tb }
    }
    required init?(coder: NSCoder) { fatalError() }
}

// ---------- 중계 잇기 ----------
/// 중계 방(proxy/ 15번 GyeolRoom)에 웹소켓으로 붙어 몸짓을 받는다. 끊기면 2·4·8…60초 뒤 다시 붙는다
final class GyeolLink: NSObject, URLSessionWebSocketDelegate, @unchecked Sendable {
    private let token: String
    private var session: URLSession!
    private var task: URLSessionWebSocketTask?
    private var ping: Timer?
    private var wait = 2.0
    private var closed = false

    init(token: String) {
        self.token = token
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: .main)
        open()
    }
    private func open() {
        guard !closed, let url = URL(string: relay.replacingOccurrences(of: "https://", with: "wss://") + "/gyeol/live") else { return }
        var req = URLRequest(url: url)
        req.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        let t = session.webSocketTask(with: req)
        task = t; t.resume(); receive(t)
        ping?.invalidate()
        ping = Timer.scheduledTimer(withTimeInterval: 25, repeats: true) { [weak self] _ in self?.task?.send(.string("ping")) { _ in } }   // 방이 깨지 않고 pong으로 답한다
    }
    private func receive(_ t: URLSessionWebSocketTask) {
        t.receive { [weak self] r in
            guard let self, t === self.task else { return }
            switch r {
            case .success(let m):
                if case .string(let s) = m, s != "pong", let j = try? JSONSerialization.jsonObject(with: Data(s.utf8)) as? [String: Any], let g = j["g"] as? String {
                    let at = (j["at"] as? Double) ?? 0
                    if at == 0 || Date().timeIntervalSince1970 * 1000 - at < 30000 { GyeolEngine.shared.gesture(g, say: j["say"] as? String) }   // 오래된 몸짓은 하지 않는다
                }
                self.receive(t)
            case .failure: self.retry()
            }
        }
    }
    private func retry() {
        task = nil; ping?.invalidate()
        Task { @MainActor in GyeolPip.shared.linkChanged(false) }
        guard !closed else { return }
        let w = wait; wait = min(60, wait * 2)
        DispatchQueue.main.asyncAfter(deadline: .now() + w) { [weak self] in self?.open() }
    }
    func close() { closed = true; ping?.invalidate(); task?.cancel(with: .goingAway, reason: nil); task = nil; session.invalidateAndCancel() }
    func urlSession(_ s: URLSession, webSocketTask t: URLSessionWebSocketTask, didOpenWithProtocol p: String?) {
        wait = 2; Task { @MainActor in GyeolPip.shared.linkChanged(true) }
    }
    func urlSession(_ s: URLSession, webSocketTask t: URLSessionWebSocketTask, didCloseWith code: URLSessionWebSocketTask.CloseCode, reason: Data?) { if t === task { retry() } }
}

// ---------- 그리기와 마음 ----------
/// 한 줄(q)에서만 돈다: 장면 섞기, 그리기, 언제 무엇을 하나(brain.js), 속마음 말풍선(tail.js)
final class GyeolEngine: @unchecked Sendable {
    static let shared = GyeolEngine()
    private let q = DispatchQueue(label: "gyeol.pip", qos: .userInitiated)
    private var bk: GyeolBake?, st: GyeolStage?
    private weak var layer: AVSampleBufferDisplayLayer?
    private var timer: DispatchSourceTimer?
    private var pool: CVPixelBufferPool?, fmt: CMVideoFormatDescription?, poolSize: [Int] = []
    private var look: GyeolDraw.Look = { var l = GyeolDraw.Look(); l.bg = "#fcfcfb"; return l }()   // 작은 창은 투명이 안 돼 기기 모드 따라 흰색·검정
    static let unit = 6.0                      // 한 칸 = 6픽셀 (창 120×64칸 → 720×384)
    private var fast = false

    func start(layer: AVSampleBufferDisplayLayer) {
        nonisolated(unsafe) let l = layer   // 층은 이 줄(q)에서만 만진다
        q.async { [self] in
            self.layer = l
            if bk == nil {
                guard let u = Bundle.main.url(forResource: "gyeol-bake", withExtension: "json"), let data = try? Data(contentsOf: u), let b = try? GyeolBake(data: data) else { return }
                bk = b; st = GyeolStage(b, pose: "stand")   // 작은 창의 결은 서 있다
            }
            let n = now(); lastActive = n; nextFidget = n + 15000; nextBlink = n + 2500 + .random(in: 0...3500)
            later(400) { [self] in act("wave") }
            run(fast: true)
        }
    }
    /// 기기 밝기 모드: 클로드 앱 바탕색에 맞춘다 (밝음 #fcfcfb, 어두움 #151515, 캡처에서 잼)
    // 작은 창의 실제 폭(픽셀). 말풍선 글자를 화면 기준 크기로 맞추는 데 쓴다
    private var winPx = UserDefaults.standard.double(forKey: "gyeolPipLastPx")
    private var fitKey = "", fitFs = 0.0
    // 창 너비가 올 때마다 화면 글자 크기를 맞춘다. 마지막 너비를 남겨, 다시 켤 때 너비가 오기 전에도 같은 크기로 그린다
    func setWindow(_ w: Double) { q.async { [self] in if w > 0 { winPx = w; UserDefaults.standard.set(w, forKey: "gyeolPipLastPx") } } }
    func setDark(_ d: Bool) { q.async { [self] in look.bg = d ? "#151515" : "#fcfcfb"; look.ledge = d ? "#34403a" : "#d7ddd6" } }
    private var debugLine = ""
    func debug(_ t: String) { q.async { [self] in debugLine = t } }
    // 몸짓 시험용: 받은 몸짓(←)과 실제로 튼 장면을 차례로 남겨 작은 창 왼쪽 아래에 빨간 글로 띄운다
    private var traces: [String] = []
    private func trace(_ t: String) {
        guard GyeolPip.debugGesture else { return }
        traces.append(t); if traces.count > 6 { traces.removeFirst() }
        debugLine = "창\(Int(winPx)) " + traces.joined(separator: " ")
    }
    func stop() { q.async { [self] in timer?.cancel(); timer = nil; layer?.sampleBufferRenderer.flush(removingDisplayedImage: true, completionHandler: nil) } }
    func pause() { q.async { [self] in timer?.cancel(); timer = nil } }
    func resume() { q.async { [self] in if st != nil, timer == nil { run(fast: true) } } }
    func gesture(_ g: String, say text: String?) { q.async { [self] in
        if let text, !text.trimmingCharacters(in: .whitespaces).isEmpty { say(text, then: g) } else { gesture(g) }
    } }

    private func now() -> Double { CACurrentMediaTime() * 1000 }
    private var laters: [(at: Double, tag: String, f: () -> Void)] = []
    private func later(_ ms: Double, tag: String = "", _ f: @escaping () -> Void) { laters.append((now() + ms, tag, f)); run(fast: true) }

    /// 몸짓 중엔 초당 40장, 쉬는 동안엔 숨만 초당 12장
    private func run(fast f: Bool) {
        if timer != nil && f == fast { return }
        fast = f; timer?.cancel()
        let t = DispatchSource.makeTimerSource(queue: q)
        t.schedule(deadline: .now(), repeating: f ? .milliseconds(25) : .milliseconds(83), leeway: .milliseconds(f ? 4 : 20))
        t.setEventHandler { [weak self] in self?.frame() }
        timer = t; t.resume()
    }

    private func frame() {
        guard let st, let bk else { return }
        let n = now()
        let due = laters.filter { $0.at <= n }; laters.removeAll { $0.at <= n }; due.forEach { $0.f() }
        brain(n)
        let moving = st.tick(n)
        look.w = 140   // 늘 와이드. 결은 오른쪽, 말풍선 자리는 왼쪽에 비워 둔다
        let b = (n.truncatingRemainder(dividingBy: 3800)) / 3800, sm = { (x: Double) in x * x * (3 - 2 * x) }
        look.breath = b < 0.4 ? sm(b / 0.4) : 1 - sm((b - 0.4) / 0.6)
        look.sit = st.pose == "sit"
        draw(GyeolDraw.scene(st.sample(n), bk, look), n)
        run(fast: moving || st.busy || !laters.isEmpty || bubbleShown(n))
    }

    // ---------- 그리기 ----------
    private func draw(_ ops: [GyeolOp], _ n: Double) {
        guard let layer else { return }
        let W = Int(look.w * Self.unit), H = Int(look.h * Self.unit)
        if pool == nil || poolSize != [W, H] {
            poolSize = [W, H]; fmt = nil
            let a: [String: Any] = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey as String: W, kCVPixelBufferHeightKey as String: H,
                                    kCVPixelBufferIOSurfacePropertiesKey as String: [:], kCVPixelBufferCGBitmapContextCompatibilityKey as String: true]
            CVPixelBufferPoolCreate(nil, nil, a as CFDictionary, &pool)
        }
        var pbo: CVPixelBuffer?
        guard let pool, CVPixelBufferPoolCreatePixelBuffer(nil, pool, &pbo) == kCVReturnSuccess, let pb = pbo else { return }
        CVPixelBufferLockBaseAddress(pb, [])
        if let cx = CGContext(data: CVPixelBufferGetBaseAddress(pb), width: W, height: H, bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(pb),
                              space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) {
            cx.translateBy(x: 0, y: CGFloat(H)); cx.scaleBy(x: Self.unit, y: -Self.unit)   // 위에서 아래로, 칸 단위
            cx.setShouldAntialias(true); cx.interpolationQuality = .high
            GyeolDraw.run(ops, in: cx)
            bubble(cx, n)
            if !debugLine.isEmpty {
                cx.saveGState(); cx.translateBy(x: 1.5, y: look.h - 1.5); cx.scaleBy(x: 1, y: -1); cx.textMatrix = .identity
                let f = CTFontCreateUIFontForLanguage(.system, 3, nil)
                let line = CTLineCreateWithAttributedString(NSAttributedString(string: debugLine, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): f as Any, NSAttributedString.Key(kCTForegroundColorAttributeName as String): GyeolDraw.color("#ff3b30")]))
                cx.textPosition = .zero; CTLineDraw(line, cx); cx.restoreGState()
            }
        }
        CVPixelBufferUnlockBaseAddress(pb, [])
        // 색 꼬리표를 sRGB로 달아 영상용 감마·밝기 보정을 막는다.
        CVBufferSetAttachment(pb, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
        CVBufferSetAttachment(pb, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_sRGB, .shouldPropagate)
        CVBufferSetAttachment(pb, kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2, .shouldPropagate)
        CVBufferSetAttachment(pb, kCVImageBufferCGColorSpaceKey, CGColorSpace(name: CGColorSpace.sRGB)!, .shouldPropagate)
        if fmt == nil || !CMVideoFormatDescriptionMatchesImageBuffer(fmt!, imageBuffer: pb) {
            CMVideoFormatDescriptionCreateForImageBuffer(allocator: nil, imageBuffer: pb, formatDescriptionOut: &fmt)
        }
        guard let fmt else { return }
        var timing = CMSampleTimingInfo(duration: .invalid, presentationTimeStamp: layer.controlTimebase.map { CMTimebaseGetTime($0) } ?? .zero, decodeTimeStamp: .invalid)
        var sbo: CMSampleBuffer?
        CMSampleBufferCreateReadyWithImageBuffer(allocator: nil, imageBuffer: pb, formatDescription: fmt, sampleTiming: &timing, sampleBufferOut: &sbo)
        guard let sb = sbo else { return }
        if let arr = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: true), CFArrayGetCount(arr) > 0 {
            let dict = unsafeBitCast(CFArrayGetValueAtIndex(arr, 0), to: CFMutableDictionary.self)
            CFDictionarySetValue(dict, Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(), Unmanaged.passUnretained(kCFBooleanTrue).toOpaque())
        }
        let r = layer.sampleBufferRenderer
        if r.status == .failed { r.flush() }
        r.enqueue(sb)
    }

    // ---------- 속마음 말풍선 (tail.js say) ----------
    private var sayChars: [Character] = [], sayShown = 0, sayOn = -1.0, sayOff = -1.0
    // 목소리: 맥과 같은 '유나'. 프리미엄 > 고음질 > 기본 순으로 고른다. 받은 목소리가 없으면 한국어 기본
    private let synth = AVSpeechSynthesizer()
    private lazy var yuna: AVSpeechSynthesisVoice? = {
        let ko = AVSpeechSynthesisVoice.speechVoices().filter { $0.language == "ko-KR" }
        let y = ko.filter { $0.name.hasPrefix("Yuna") || $0.name.hasPrefix("유나") }
        return y.max { $0.quality.rawValue < $1.quality.rawValue } ?? AVSpeechSynthesisVoice(language: "ko-KR")
    }()
    private func speak(_ text: String) {
        guard UserDefaults.standard.object(forKey: "gyeolPip.voice") as? Bool ?? true else { return }
        synth.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: text)
        u.voice = yuna
        u.preUtteranceDelay = 0.3   // 맥에서 첫 음절이 먹혀 0.3초 쉬고 말한 것과 같게
        synth.speak(u)
    }
    private func bubbleShown(_ n: Double) -> Bool { sayOn >= 0 && (sayOff < 0 || n < sayOff + 250) }
    private func say(_ text: String, then g: String?) {
        let chars = Array(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80))
        laters.removeAll { $0.tag == "say" }
        lastActive = now(); dozing = false; if mode == "doze" { mode = "" }
        sayChars = chars; sayShown = 0; sayOn = now(); sayOff = -1
        speak(String(chars))
        mode = "talk"; play("talk", lead: 120)
        for i in chars.indices { later(150 + Double(i) * 70, tag: "say") { [self] in sayShown = i + 1 } }
        let done = 150 + Double(chars.count) * 70
        later(done, tag: "say") { [self] in if mode == "talk" { mode = ""; if let g { gesture(g) } else { play("smile", lead: 200) } } }
        sayOff = now() + done + 2500 + Double(chars.count) * 90
    }
    private func bubble(_ cx: CGContext, _ n: Double) {
        guard sayOn >= 0 else { return }
        let a = sayOff >= 0 && n > sayOff ? max(0, 1 - (n - sayOff) / 200) : min(1, (n - sayOn) / 200)
        if a <= 0 { if sayOff >= 0 && n > sayOff { sayOn = -1 }; return }
        // 결 머리 왼쪽에 꼬리를 붙인다. 줄 너비는 결이 다 비킨 자리 기준으로 고정(비키는 동안 줄이 바뀌지 않게)
        let padX = 3.6, padY = 2.2, right = look.figX + 12.5, maxW = look.w - 62 + 12.5 - 3 - padX * 2
        let text = String(sayChars.prefix(max(1, sayShown))), full = String(sayChars)
        // 글자는 창 크기와 상관없이 화면 19pt로 둔다(앱 본문 17pt보다 조금 크게, 10-31 정함). 다 안 들어가면 들어갈 만큼만 줄인다
        func setter(_ t: String, _ fs: Double) -> CTFramesetter {
            var gap = CGFloat(fs * 0.2)
            let para = withUnsafeBytes(of: &gap) { b in [CTParagraphStyleSetting(spec: .lineSpacingAdjustment, valueSize: MemoryLayout<CGFloat>.size, value: b.baseAddress!)].withUnsafeBufferPointer { CTParagraphStyleCreate($0.baseAddress, 1) } }
            let font = CTFontCreateUIFontForLanguage(.system, fs, "ko" as CFString).map { CTFontCreateCopyWithSymbolicTraits($0, fs, nil, .traitBold, .traitBold) ?? $0 }
            let str = NSAttributedString(string: t, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font as Any, NSAttributedString.Key(kCTForegroundColorAttributeName as String): GyeolDraw.color(GyeolDraw.ink), NSAttributedString.Key(kCTParagraphStyleAttributeName as String): para])
            return CTFramesetterCreateWithAttributedString(str)
        }
        func size(_ f: CTFramesetter) -> CGSize { CTFramesetterSuggestFrameSizeWithConstraints(f, CFRange(), nil, CGSize(width: maxW, height: 1000), nil) }
        let key = "\(winPx)|\(look.w)|" + full
        if key != fitKey {   // 다 친 글 기준으로 한 번만 정해, 치는 동안 크기가 안 바뀌게
            var f = winPx > 0 ? 19 * look.w / winPx : 6.7   // iOS가 주는 창 너비는 픽셀이 아니라 pt다(큰 창 398)
            while f > 4, size(setter(full, f)).height > look.h - 2 - padY * 2 { f *= 0.92 }
            fitKey = key; fitFs = f
        }
        let fs = fitFs
        let fsr = setter(sayShown == 0 ? " " : text, fs)
        let sz = size(fsr)
        let w = ceil(sz.width) + padX * 2, h = ceil(sz.height) + padY * 2, x = right - w, y = 1.0
        cx.saveGState(); cx.setAlpha(a)
        let s = 0.92 + 0.08 * a; cx.translateBy(x: right, y: y + h * 0.6); cx.scaleBy(x: s, y: s); cx.translateBy(x: -right, y: -(y + h * 0.6))
        let box = CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: min(fs, h / 2), cornerHeight: min(fs, h / 2), transform: nil)
        let tail = CGMutablePath(); let ty = y + h * 0.58
        tail.move(to: CGPoint(x: right - 0.3, y: ty - 1.8)); tail.addLine(to: CGPoint(x: right + 2.6, y: ty)); tail.addLine(to: CGPoint(x: right - 0.3, y: ty + 1.8))
        cx.setFillColor(GyeolDraw.color("#ffffff")); cx.setStrokeColor(GyeolDraw.color(GyeolDraw.ink)); cx.setLineWidth(0.6); cx.setLineJoin(.round)
        cx.addPath(box); cx.addPath(tail); cx.drawPath(using: .fillStroke)
        cx.addPath(box); cx.fillPath()   // 꼬리 뿌리의 선을 지운다
        cx.addPath(box); cx.strokePath()
        cx.setFillColor(GyeolDraw.color("#ffffff")); cx.fill(CGRect(x: right - 0.31, y: ty - 1.5, width: 0.6, height: 3))
        // 글자: CoreText는 아래에서 위로 그리니 이 자리만 뒤집는다
        cx.translateBy(x: x + padX, y: y + padY + ceil(sz.height)); cx.scaleBy(x: 1, y: -1)
        let frame = CTFramesetterCreateFrame(fsr, CFRange(), CGPath(rect: CGRect(x: 0, y: 0, width: maxW, height: ceil(sz.height)), transform: nil), nil)
        cx.textMatrix = .identity
        if sayShown > 0 { CTFrameDraw(frame, cx) }
        cx.restoreGState()
    }

    // ---------- 언제 무엇을 하나 (brain.js) ----------
    private var lastActive = 0.0, nextFidget = 0.0, busyUntil = 0.0, nextBlink = 0.0, nextCheck = 0.0
    private var mode = "", dozing = false
    private static let holdLast: [String: Double] = ["think": 2400, "worry": 2000, "sad": 2200, "shy": 1600, "heart": 1500, "thumb": 1100, "go": 900, "surprise": 700, "look": 1300, "lookR": 1300,
        "smallfist": 900, "facepalm": 1400, "upright": 1500, "stare": 2600, "puzzle": 1000, "snap": 500, "startle": 400, "tuck": 500, "cheeks": 900]
    private static let alias: [String: String] = ["lookL": "look", "cheekskick": "cheeks"]   // 볼 감싸고 발 동동은 앉은 장면뿐이라, 서 있는 작은 창에선 볼 감싸기로

    @discardableResult private func play(_ name: String, lead: Double? = nil, back: Double? = nil, stay: Bool = false, holdLast: Double? = nil, times: Int? = nil, done: (() -> Void)? = nil) -> Bool {
        guard let st else { return false }
        var o = GyeolStage.Opt(); o.lead = lead; o.back = back; o.stay = stay; o.holdLast = holdLast; o.times = times; o.done = done
        let ok = st.play(name, o, now: now())
        if name != "blink" { trace(ok ? name : "\(name)✗") }
        if ok { run(fast: true) }
        return ok
    }
    private func act(_ name: String, holdLast: Double? = nil, times: Int? = nil, mode m: String = "") {
        lastActive = now()
        if dozing && name != "doze" && name != "hugdoze" { dozing = false }
        mode = m
        let ok = play(name, holdLast: holdLast ?? Self.holdLast[name], times: times)
        if ok, let st, let p = st.plan { busyUntil = now() + p.total + 300 }
    }
    private func gesture(_ g: String) {
        trace("←\(g)")
        guard let st else { return }
        if g == "back" { mode = "back"; play("back", stay: true); lastActive = now(); return }
        if g == "doze" { mode = "doze"; dozing = true; play("doze", stay: true); return }
        if g == "dance" { act(st.pose == "stand" ? ["combo_up", "combo_soft", "combo_party"].randomElement()! : ["sitjam", "sitsway"].randomElement()!); return }
        if mode == "back" { mode = "" }
        act(Self.alias[g] ?? g)
    }
    private func brain(_ t: Double) {
        guard let st else { return }
        // 깜빡임: 2.5~6초마다(가끔 두 번). 바탕일 때만
        if t >= nextBlink {
            nextBlink = t + 2500 + .random(in: 0...3500)
            if st.plan == nil && !dozing { play("blink", lead: 70, back: 120) }
            if Double.random(in: 0..<1) < 0.2 { later(330) { [self] in if st.plan == nil && !dozing { play("blink", lead: 70, back: 120) } } }
        }
        guard t >= nextCheck else { return }
        nextCheck = t + 400
        if !mode.isEmpty || t < busyUntil || st.plan != nil { return }
        // 4분 넘게 조용하면 존다(서 있으면 무릎 안고)
        if !dozing && t - lastActive > 4 * 60 * 1000 { dozing = true; mode = "doze"; play(st.pose == "stand" ? "hugdoze" : "doze", stay: true); return }
        if t < nextFidget { return }
        let h = Calendar.current.component(.hour, from: Date()), night = h >= 23 || h < 4, dawn = h >= 4 && h < 6
        var bag = st.pose == "sit" ? ["dangle", "dangle", "dangle", "tuck", "tuck", "look", "lookR", "kick", "nod"] : ["tuck", "tuck", "look", "lookR", "sway", "nod", "foldArms"]
        if night { bag += ["yawn", "yawn"] }; if dawn { bag.append("stare") }
        if t - lastActive > 90000 { bag.append(st.pose == "sit" ? "sitsway" : "combo_soft") }   // 오래 혼자면 흥얼흥얼
        let g = bag.randomElement()!
        act(g, holdLast: g == "foldArms" ? 2200 : nil, times: g == "sway" ? 1 : nil)
        nextFidget = t + 15000 + .random(in: 0...30000)
    }
}

// ---------- 설정 ----------
/// 설정 › 알림과 위젯: 결 작은 창 (개발자 기기만, 기본 꺼짐)
struct GyeolPipSettings: View {
    @State private var test = false
    var body: some View {
        if Support.shared.dev {
            @Bindable var g = GyeolPip.shared
            Text(verbatim: String(localized: "결 작은 창")).appFont(13, .bold).foregroundStyle(Theme.teal).padding(.top, 8)
            VStack(alignment: .leading, spacing: 0) {
                Toggle(isOn: $g.on) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: String(localized: "작은 창에 결 띄우기")).appFont(15, .bold)
                        Text(verbatim: String(localized: "다른 앱 위에서도 결이 보이고, 클로드가 답하면 몸짓을 해요")).appFont(13).foregroundStyle(Theme.sub)
                    }
                }
                .tint(Theme.teal).padding(.horizontal, 16).frame(minHeight: 60)
                if g.on {
                    Divider().overlay(Theme.line)
                    GyeolPipPreview().aspectRatio(120.0 / 64, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 12)).padding(16)
                    HStack(spacing: 8) {
                        button(g.active ? String(localized: "작은 창 닫기") : String(localized: "작은 창 열기")) { g.toggleWindow() }.disabled(!g.possible && !g.active)
                        button(String(localized: "커넥터 주소 복사")) { Task { await g.fetchConnector() } }
                    }.padding(.horizontal, 16)
                    Toggle(isOn: $g.voice) { Text(verbatim: String(localized: "속마음을 목소리로도 (유나)")).appFont(14) }
                        .tint(Theme.teal).padding(.horizontal, 16).padding(.top, 10)
                    Text(verbatim: [g.linked ? String(localized: "중계에 붙어 있어요") : String(localized: "중계에 붙는 중"), g.size.map { String(localized: "창 \($0)") }, g.note].compactMap { $0 }.joined(separator: " · "))
                        .appFont(12).foregroundStyle(Theme.sub).padding(.horizontal, 16).padding(.vertical, 10)
                    if let u = g.mcp { Text(verbatim: u).appFont(11).foregroundStyle(Theme.sub).textSelection(.enabled).padding(.horizontal, 16).padding(.bottom, 10) }
                    Divider().overlay(Theme.line)
                    DisclosureGroup(isExpanded: $test) {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 6)], spacing: 6) {
                            ForEach(Self.names, id: \.self) { n in
                                Button { GyeolEngine.shared.gesture(n, say: n == "wave" ? String(localized: "안녕, 여기서도 보여?") : nil) } label: {
                                    Text(verbatim: n).appFont(12).frame(maxWidth: .infinity, minHeight: 30).background(Theme.bg, in: RoundedRectangle(cornerRadius: 8))
                                }.buttonStyle(.plain)
                            }
                        }.padding(.vertical, 8)
                    } label: { Text(verbatim: String(localized: "몸짓 시험 (개발자)")).appFont(13, .bold).foregroundStyle(Theme.sub) }
                    .padding(.horizontal, 16).padding(.vertical, 8)
                }
            }
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
            .onAppear { g.wake() }
        }
    }
    static let names = ["wave", "nod", "think", "back", "cheer", "thumb", "laugh", "shy", "surprise", "sad", "kick", "look", "heart", "worry", "go", "rise", "fall", "land",
                        "tuck", "cheeks", "ponder", "scratch", "snap", "doze", "yawn", "facepalm", "smallfist", "spin", "sway", "dance"]
    private func button(_ t: String, _ f: @escaping () -> Void) -> some View {
        Button(action: f) {
            Text(verbatim: t).appFont(14, .bold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 40)
                .background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
        }.buttonStyle(.plain)
    }
}

/// 미리보기: 작은 창의 원본 층을 그대로 보여 준다 (iOS가 이 층을 작은 창으로 옮긴다)
struct GyeolPipPreview: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let v = UIView(); v.backgroundColor = .clear
        let h = GyeolPip.shared.host; h.removeFromSuperview(); h.frame = v.bounds; h.autoresizingMask = [.flexibleWidth, .flexibleHeight]; v.addSubview(h)
        return v
    }
    func updateUIView(_ v: UIView, context: Context) {}
}
#endif
