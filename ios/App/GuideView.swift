import SwiftUI

/// 소개 탭: 이 앱이 무엇을 하는지 짧게, 시작 방법, 맨 끝에 naeilo.com
struct GuideView: View {
    @EnvironmentObject var m: AppModel
    @Binding var tab: AppTab

    var body: some View {
        let s = m.login == nil ? Snapshot.sample : m.snap
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("naeilo.com 에 입력한 내 자산·목표 데이터를 받아 홈 화면과 잠금 화면 위젯으로 보여줍니다.")
                    .font(.title3.weight(.semibold))

                HStack(spacing: 12) {
                    WidgetPreview(family: .small, scale: 0.62) { AssetSmallView(s: s) }
                    WidgetPreview(family: .small, scale: 0.62) { FutureSmallView(s: s) }
                }
                .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 12) {
                    Point(icon: "lock.shield", text: "사이트의 동기화 비밀번호로 내 입력값을 받아옵니다. 비밀번호는 저장하지 않습니다.")
                    Point(icon: "arrow.triangle.2.circlepath", text: "장중에는 30분마다 바뀐 것만 받아 위젯을 갱신합니다. 위젯의 ↻ 로 바로 확인할 수 있습니다.")
                    Point(icon: "chart.line.uptrend.xyaxis", text: "미래 전망과 목표 확률은 사이트와 같은 계산식으로 구합니다.")
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("시작하기").font(.headline)
                    Step(n: 1, text: m.login == nil ? "연결 탭에서 동기화 비밀번호를 넣습니다" : "로그인 완료") { tab = .settings }
                    Step(n: 2, text: "위젯 탭에서 마음에 드는 위젯을 고릅니다") { tab = .widgets }
                    Step(n: 3, text: "홈 화면을 길게 눌러 + → naeilo 에서 추가합니다", action: nil)
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
