import SwiftUI

// 인물 위젯 미리보기 (설정 > 시안 조작). 홈 화면에 하나씩 놓지 않고 다섯 인물 × 세 위젯을 기본·다크로 한 번에 본다.
// 숫자는 앱이 위젯에 넘기는 값(reward.json)과 같다.
struct CharWidgetPreview: View {
    @State var dark = UserDefaults.standard.bool(forKey: "cpDark")     // 캡처용: -cpDark YES -cpLock YES
    @State var lockOthers = UserDefaults.standard.bool(forKey: "cpLock")

    private var reward: WReward { Store.read(WReward.self, "reward.json") ?? .sample }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Picker("", selection: $dark) { Text("기본").tag(false); Text("다크").tag(true) }.pickerStyle(.segmented)
                Toggle("못 만난 인물은 잠김", isOn: $lockOthers).appFont(14)
                ForEach(WChar.all) { c in
                    let locked = lockOthers && !(c.id == "seri" || reward.friendsOn.contains(c.id))
                    let e = CharEntry(date: Date(), r: reward, c: c, locked: locked)
                    Text(c.name).appFont(13, .bold).foregroundStyle(Theme.sub)
                    HStack(spacing: 10) {
                        tile(e, w: 158) { RecoverCharView(e: e) }
                        tile(e, w: 158) { BlockCharView(e: e) }
                    }
                    tile(e, w: 364) { MovesCharView(e: e) }
                }
            }
            .padding(16)
        }
        .background(dark ? Color.black : Theme.bg)
        .navigationTitle("인물 위젯 미리보기")
        .preferredColorScheme(dark ? .dark : nil)
    }

    private func tile<V: View>(_ e: CharEntry, w: CGFloat, @ViewBuilder _ v: () -> V) -> some View {
        ZStack {
            CharBG(c: e.c, locked: e.locked, dark: dark)
            v().padding(16).foregroundStyle(.white).environment(\.colorScheme, .dark).environment(\.charDark, dark)
        }
        .frame(width: w, height: 158)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
