import SwiftUI

/// 소개 탭: 이 앱이 무엇을 하는지 짧게, 시작 방법, 맨 끝에 naeilo.com
struct GuideView: View {
    @EnvironmentObject var m: AppModel
    @Binding var tab: AppTab

    var body: some View {
        let s = m.login == nil ? Snapshot.sample : m.snap
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("naeilo.com 의 내 자산을 위젯으로.")
                    .font(.title3.weight(.semibold))

                HStack(spacing: 12) {
                    WidgetPreview(family: .small, scale: 0.62) { AssetSmallView(s: s) }
                    WidgetPreview(family: .small, scale: 0.62) { FutureSmallView(s: s) }
                }
                .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 12) {
                    Point(icon: "lock.shield", text: "서버는 내용을 모릅니다. 암호화된 채로 오가고, 이 아이폰에서만 풀립니다.")
                    Point(icon: "arrow.triangle.2.circlepath", text: "바뀐 것만 조금씩 받아 30분마다 갱신합니다.")
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("시작하기").font(.headline)
                    Step(n: 1, text: step1) { tab = .settings }
                    Step(n: 2, text: "위젯 탭에서 고르기") { tab = .widgets }
                    Step(n: 3, text: "홈 화면을 길게 눌러 + → naeilo", action: nil)
                }

                Divider()

                VStack(spacing: 6) {
                    Text("naeilo").font(.system(size: 34, weight: .heavy, design: .rounded)).foregroundStyle(Palette.acc)
                    Text("See Tomorrow, Today.").font(.footnote).foregroundStyle(.secondary)
                    Link(destination: URL(string: "https://naeilo.com/")!) {
                        Label("naeilo.com 바로 가기", systemImage: "safari").font(.subheadline.weight(.semibold))
                    }
                    .padding(.top, 4)
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 12)
            }
            .padding(20)
        }
        .environment(\.isSample, s.placeholder)
    }
}

extension GuideView {
    private var step1: String {
        switch m.login {
        case .none: return "연결 탭에 사이트와 같은 동기화 비밀번호 넣기"
        case .password: return "연결됨 · 이 아이폰에서만 풀어 봅니다"
        case .github: return "연결됨 (GitHub)"
        case .demo: return "체험 중 · 연결 탭에서 비밀번호로 연결"
        }
    }
}

private struct Point: View {
    let icon: String, text: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).font(.body.weight(.semibold)).foregroundStyle(Palette.acc).frame(width: 24)
            Text(text).font(.subheadline).fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct Step: View {
    let n: Int, text: String
    let action: (() -> Void)?
    var body: some View {
        HStack(spacing: 10) {
            Text("\(n)").font(.caption.weight(.heavy)).foregroundStyle(.white).frame(width: 22, height: 22).background(Circle().fill(Palette.acc))
            Text(text).font(.subheadline)
            Spacer(minLength: 0)
            if action != nil { Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(.tertiary) }
        }
        .contentShape(Rectangle())
        .onTapGesture { action?() }
    }
}
