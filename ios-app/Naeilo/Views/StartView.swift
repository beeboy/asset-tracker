import SwiftUI

// 첫 질문 (시안 첫 화면): 지금 내 투자, 플러스인가요 마이너스인가요?
struct StartView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("naeilo").appFont(20, .bold).padding(.top, 24)
                HStack(alignment: .bottom, spacing: 12) {
                    Pixel(name: "spr_seri", width: 56, height: 80).accessibilityLabel("세리")
                    Text("처음 오셨군요. …… 하나만 물어볼게요.").appFont(15)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(Theme.card, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                        .overlay(UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14).stroke(Theme.border))
                        .padding(.bottom, 14)
                }
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

    // 길을 걸으면 열리는 인물 띠 (캐릭터 설정 스레드 시안): 세리는 지금, 나머지는 주마다 한 명씩
    private var teaser: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("외전 『이종 공명』 · 길을 걸으면 열려요").appFont(13, .bold).foregroundStyle(Theme.yellow)
            HStack(alignment: .bottom, spacing: 0) {
                ForEach(Array(Shelter.friends.enumerated()), id: \.element.id) { i, f in
                    VStack(spacing: 6) {
                        Pixel(name: (i == 0 ? "spr_" : "sil_") + f.id, width: 42, height: 60)
                        Text(i == 0 ? "지금" : "\(i)주차").appFont(11, i == 0 ? .bold : .regular)
                            .foregroundStyle(i == 0 ? Theme.yellow : Color(hex: 0x9AA5AF))
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(i == 0 ? "세리, 지금 만날 수 있어요" : "잠긴 친구, 인터미션 \(i)주차")
                }
            }
            Text("시작 3단계를 마치면 프롤로그와 1장이 열려요. 그다음 매주 한 명씩, 한 장씩 와요.")
                .appFont(13).foregroundStyle(Color(hex: 0xEEF0F7)).lineSpacing(3)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.shelter, in: RoundedRectangle(cornerRadius: 20))
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
