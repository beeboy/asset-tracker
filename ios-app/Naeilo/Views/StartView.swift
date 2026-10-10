import SwiftUI

// 첫 질문 (시안 첫 화면): 지금 내 투자, 플러스인가요 마이너스인가요?
struct StartView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("naeilo").appFont(20, .bold).padding(.top, 24)
                Text("처음 오셨군요. 하나만 물어볼게요.").appFont(15).foregroundStyle(Theme.sub)
                Text("지금 내 투자,\n플러스인가요\n마이너스인가요?").appFont(30, .bold).lineSpacing(4)
                VStack(spacing: 10) {
                    option("마이너스예요", "본전까지 가는 길을 같이 찾아요", .recover)
                    option("플러스예요", "목표 금액까지 가는 길을 그려요", .plus)
                    option("아직 시작 전이에요", "한 달에 얼마씩이면 언제 얼마가 되는지 봐요", .novice)
                }
                Text("잘 모르겠다면 \"마이너스\"로 시작하세요. 매수 단가를 넣으면 자동으로 알려드려요.").appFont(13).foregroundStyle(Theme.muted)
            }
            .padding(.horizontal, 20).padding(.bottom, 24)
            .foregroundStyle(Theme.ink)
        }
        .background(Theme.bg)
    }

    private func option(_ t: String, _ sub: String, _ r: Route) -> some View {
        Button { m.startRoute(r) } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(t).appFont(19, .bold)
                Text(sub).appFont(14).foregroundStyle(Theme.sub)
            }
            .padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border, lineWidth: 2))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
