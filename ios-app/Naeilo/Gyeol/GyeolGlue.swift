#if GYEOL
import SwiftUI
import GyeolKit

// 결 섬 잇기. 결 부분은 Gyeol/ 패키지에 있고, 앱이 닿는 곳은 이 파일과 #if GYEOL 세 곳(App·설정·WidgetBridge)뿐이다 (빼는 법: Gyeol/README.md)

enum GyeolFeed {
    /// 위젯 숫자가 새로 나오면 (WidgetBridge.write 끝): 말풍선 줄과 작은 섬 숫자
    @MainActor static func push() {
        guard GyeolLive.shared.on else { return }
        let s = Engine.snapshot()
        guard !s.placeholder else { return }
        let hide = Store.lockPrivate || Store.hideAll   // 잠금 화면에도 보이니 위젯 금액 숨기기를 따른다
        var ls = [String(localized: "오늘 \(Fmt.pct(s.dayChg))")]
        if !hide { ls.append(String(localized: "총자산 \(Fmt.eok(s.total))")) }
        if s.goal > 0 { ls.append(String(localized: "목표까지 \(String(describing: Int((s.progress * 100).rounded())))% 왔어")) }
        if let p = s.pGoal { ls.append(String(localized: "달성 확률 \(String(describing: Int((p * 100).rounded())))%")) }
        if let t = s.term, s.total > 0 { ls.append(String(localized: "\(s.termLabel) 예상 \(hide ? Fmt.pct(t.p50 / s.total - 1, 0) : Fmt.eok(t.p50))")) }
        if let e = s.events.first { ls.append("D-\(e.dday) \(e.t)") }
        // 섬은 갱신 한도가 있어 자세는 일이 생길 때만 바꾼다: 하루 ±1% 를 넘으면 만세·걱정
        let c = s.dayChg
        GyeolLive.shared.feed(lines: ls, chip: Fmt.pct(c), pose: c >= 0.01 ? .cheer : c <= -0.01 ? .worry : .idle)
    }

    /// 섬 열쇠가 바뀌면 (섬을 새로 띄울 때마다): 개발자 동기화 중계에 올려 두면 맥이 받아 간다 (proxy/ 14번 /gyeol)
    @MainActor static func keys() {
        let g = GyeolLive.shared
        guard g.on, g.claude, let tok = Sync.shared.creds?.token, g.pushToken != nil || g.startToken != nil,
              let url = URL(string: "https://asset-ai.drinker.workers.dev/gyeol") else { return }
        var req = URLRequest(url: url, timeoutInterval: 20)
        req.httpMethod = "POST"
        req.setValue("Bearer " + tok, forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["push": g.pushToken ?? "", "start": g.startToken ?? ""])
        Task { _ = try? await URLSession.shared.data(for: req) }
    }
}

/// 앱이 앞으로 올 때: 내려간 섬(8시간 한도) 다시 띄우고 숫자 넘기기
struct GyeolHooks: ViewModifier {
    let phase: ScenePhase
    func body(content: Content) -> some View {
        content
            .onChange(of: phase, initial: true) { _, p in
                guard p == .active else { return }
                GyeolFeed.push()   // 줄을 먼저 남겨야 띄울 때 바로 숫자가 보인다
                GyeolLive.shared.resume()
            }
            .onChange(of: GyeolLive.shared.on) { _, v in if v { GyeolFeed.push() } }
            .onChange(of: GyeolLive.shared.pushToken) { GyeolFeed.keys() }
            .onChange(of: GyeolLive.shared.startToken) { GyeolFeed.keys() }
    }
}

/// 설정 › 알림과 위젯: 결 띄우기·클로드 연동 (둘 다 기본 꺼짐). 개발자 기기는 자세 시험 단추도
struct GyeolSettings: View {
    var body: some View {
        @Bindable var g = GyeolLive.shared
        Text(verbatim: String(localized: "결")).appFont(13, .bold).foregroundStyle(Theme.teal).padding(.top, 8)
        VStack(alignment: .leading, spacing: 0) {
            row(String(localized: "섬에 결 띄우기"), String(localized: "다이내믹 아일랜드·잠금 화면에서 결이 위젯 숫자를 말해요"), $g.on)
            if g.on {
                Divider().overlay(Theme.line)
                row(String(localized: "클로드 연동"), String(localized: "맥에서 클로드가 답하면 결이 그 몸짓을 해요"), $g.claude)
                if !g.allowed { note(String(localized: "기기 설정 › naeilo › 실시간 현황을 켜야 결이 떠요")) }
                if g.claude {
                    Divider().overlay(Theme.line)
                    Button {
                        UIPasteboard.general.string = "{\"gyeol\":1,\"push\":\"\(g.pushToken ?? "")\",\"start\":\"\(g.startToken ?? "")\"}"
                    } label: {
                        Text(verbatim: g.pushToken == nil ? String(localized: "섬 열쇠 받는 중") : String(localized: "섬 열쇠 복사 (자동으로 안 넘어갈 때)")).appFont(15, .bold).foregroundStyle(Theme.teal)
                            .padding(.horizontal, 16).frame(minHeight: 48)
                    }.disabled(g.pushToken == nil)
                }
                if Support.shared.dev { poses }
            }
        }
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private var poses: some View {
        VStack(alignment: .leading, spacing: 8) {
            Divider().overlay(Theme.line)
            Text(verbatim: String(localized: "자세 시험 (개발자)")).appFont(13, .bold).foregroundStyle(Theme.sub).padding(.horizontal, 16)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 6)], spacing: 6) {
                ForEach(GyeolPose.allCases, id: \.self) { p in
                    Button { GyeolLive.shared.show(p, say: p == .idle ? nil : p.name) } label: {
                        Text(verbatim: p.name).appFont(12).frame(maxWidth: .infinity, minHeight: 30)
                            .background(Theme.bg, in: RoundedRectangle(cornerRadius: 8))
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 12)
        }
    }

    private func row(_ t: String, _ s: String, _ on: Binding<Bool>) -> some View {
        Toggle(isOn: on) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: t).appFont(15, .bold)
                Text(verbatim: s).appFont(13).foregroundStyle(Theme.sub)
            }
        }
        .tint(Theme.teal)
        .padding(.horizontal, 16).frame(minHeight: 60)
    }

    private func note(_ t: String) -> some View {
        Text(verbatim: t).appFont(12).foregroundStyle(Theme.sub).padding(.horizontal, 16).padding(.bottom, 10)
    }
}
#endif
