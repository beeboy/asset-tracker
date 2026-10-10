import SwiftUI
import StoreKit

// 개발자 후원(커피 4등급 + 한 잔 더). 서재의 6장을 누르거나 설정 > 개발자 후원에서 연다.
// 윗등급은 정가(차액 결제 없음). 구매 복원 버튼은 지침 3.1.1 에 따라 늘 보인다.
struct SupportView: View {
    @State private var s = Support.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("개발자에게 커피 한 잔").appFont(22, .bold)
                    Text("naeilo는 구독이나 유료 판매 없이 커피 후원으로 만들어요. 후원하면 외전 뒷이야기와 보상 미리 보기가 열려요.")
                        .appFont(14).foregroundStyle(Theme.sub).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                    if s.tier != .free {
                        Text("지금 등급 · \(s.tier.name)").appFont(13, .bold).foregroundStyle(Theme.inkFixed)
                            .padding(.horizontal, 10).padding(.vertical, 4).background(Theme.gold, in: Capsule())
                    }
                }
                ForEach(SupportTier.allCases.filter { $0 != .free }, id: \.self) { tierCard($0) }
                tipCard
                if s.has(.specialty) { SponsorForm() }
                if s.products.isEmpty {
                    Text("상품을 불러오지 못했어요. App Store 연결을 확인하거나 잠시 뒤 다시 열어 주세요.")
                        .appFont(12).foregroundStyle(Theme.muted)
                }
                if let msg = s.message {
                    Text(msg).appFont(14, .bold).foregroundStyle(Theme.teal)
                }
                Button { Task { await s.restore() } } label: {
                    Text(s.busy == "restore" ? "복원하는 중…" : "구매 복원").appFont(15, .bold).foregroundStyle(Theme.teal)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
                }
                .buttonStyle(.plain).disabled(s.busy != nil)
                SponsorList()
                Text("Apple ID로 결제하는 인앱 구매예요. 한 번 사면 계속 열리고, 다른 기기와 가족 공유 가족도 같이 열려요. 윗등급은 아래 등급을 모두 포함하고 정가예요. '한 잔 더'는 아무것도 열지 않는 후원이에요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("개발자 후원").navigationBarTitleDisplayMode(.inline)
        .task { if s.products.isEmpty { await s.loadProducts() }; await s.refresh() }
        .onDisappear { s.message = nil }
    }

    private func tierCard(_ t: SupportTier) -> some View {
        let owned = s.tier >= t, p = s.product(t)
        return Card(stroke: owned ? Theme.teal : Theme.border) {
            HStack(alignment: .firstTextBaseline) {
                Text("☕︎ " + t.name).appFont(17, .bold)
                Spacer()
                Text(p?.displayPrice ?? "—").appFont(15, .bold).foregroundStyle(Theme.sub)
            }
            VStack(alignment: .leading, spacing: 4) {
                if t.rawValue > 1 { Text("+ \(SupportTier(rawValue: t.rawValue - 1)!.name)의 모든 것").appFont(13).foregroundStyle(Theme.sub) }
                ForEach(t.adds, id: \.self) { a in
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("·").appFont(14, .bold)
                        Text(a).appFont(14).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            Button { if let p { Task { await s.buy(p) } } } label: {
                Text(owned ? "가지고 있어요" : p == nil ? "지금은 살 수 없어요" : s.busy == p?.id ? "결제하는 중…" : "후원하기")
                    .appFont(15, .bold).foregroundStyle(owned ? Theme.teal : .white)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(owned ? Theme.mintBg : Theme.teal, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain).disabled(owned || p == nil || s.busy != nil)
        }
    }

    private var tipCard: some View {
        let p = s.tipProduct
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("☕︎ 한 잔 더").appFont(17, .bold)
                Spacer()
                Text(p?.displayPrice ?? "—").appFont(15, .bold).foregroundStyle(Theme.sub)
            }
            Text(s.tips > 0 ? "지금까지 \(s.tips)잔 고마워요. 몇 번이든 보낼 수 있어요." : "아무것도 열리지 않는 순수 후원이에요. 몇 번이든 보낼 수 있어요.")
                .appFont(14).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            Button { if let p { Task { await s.buy(p) } } } label: {
                Text(p == nil ? "지금은 살 수 없어요" : s.busy == p?.id ? "결제하는 중…" : "한 잔 보내기").appFont(15, .bold).foregroundStyle(Theme.teal)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
            }
            .buttonStyle(.plain).disabled(p == nil || s.busy != nil)
        }
    }
}
