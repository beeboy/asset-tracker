import SwiftUI
import StoreKit

// 구독 (월 1,000원 · 연 9,000원). 설정 > 구독, 잠긴 위젯·서재 6장·잠긴 분석에서 연다.
// 구매 복원·구독 관리는 지침 3.1.1·3.1.2 에 따라 늘 보이고, 가격·기간·자동 갱신 안내와 이용약관 링크를 같이 둔다.
struct SupportView: View {
    @State private var s = Support.shared
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                FriendSay(key: "sponsor", fallback: "커피 한 잔이면 충분해요.")
                VStack(alignment: .leading, spacing: 6) {
                    Text("구독").appFont(22, .bold)
                    Text(lead).appFont(14).foregroundStyle(Theme.sub).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                    if let p = s.plan {
                        Text("지금 \(p.name) 중 · 고마워요").appFont(13, .bold).foregroundStyle(Theme.inkFixed)
                            .padding(.horizontal, 10).padding(.vertical, 4).background(Theme.gold, in: Capsule())
                    } else if s.inTrial {
                        Text("7일 무료 · \(s.trialLeft)일 남았어요").appFont(13, .bold).foregroundStyle(Theme.inkFixed)
                            .padding(.horizontal, 10).padding(.vertical, 4).background(Theme.gold, in: Capsule())
                    }
                }
                opens
                ForEach(SubPlan.allCases, id: \.self) { planCard($0) }
                if s.products.isEmpty {
                    Text("상품을 불러오지 못했어요. App Store 연결을 확인하거나 잠시 뒤 다시 열어 주세요.")
                        .appFont(12).foregroundStyle(Theme.muted)
                }
                if let msg = s.message {
                    Text(msg).appFont(14, .bold).foregroundStyle(Theme.teal)
                }
                HStack(spacing: 10) {
                    outline(s.busy == "restore" ? "복원하는 중…" : "구매 복원") { Task { await s.restore() } }
                    outline("구독 관리") { openURL(URL(string: "https://apps.apple.com/account/subscriptions")!) }
                }
                .disabled(s.busy != nil)
                Text("Apple ID로 결제하는 자동 갱신 구독이에요. 기간이 끝나기 24시간 전까지 끄지 않으면 같은 값으로 다시 결제돼요. 끄기는 위 '구독 관리'(iPhone 설정 > Apple 계정 > 구독)에서 할 수 있어요. 구독을 멈춰도 이미 연 캐릭터 상품과 받은 이야기는 그대로예요.")
                    .appFont(12).foregroundStyle(Theme.muted).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                Link("이용약관 (Apple 표준 EULA)", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
                    .appFont(12, .bold).foregroundStyle(Theme.teal)
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle("구독").navigationBarTitleDisplayMode(.inline)
        .task { if s.products.isEmpty { await s.loadProducts() }; await s.refresh() }
        .onDisappear { s.message = nil }
    }

    private var lead: String {
        if s.plan != nil { return "구독 덕분에 naeilo를 계속 만들어요. 위젯, 목표와 확률 전망, 서재 외전이 모두 열려 있어요." }
        if s.inTrial { return "처음 7일은 서재 빼고 모두 무료예요. 7일 뒤에도 위젯과 목표·확률 전망을 보려면 구독해 주세요." }
        return "7일 무료가 끝났어요. 홈 숫자·분석·게임과 이미 연 캐릭터는 그대로 쓸 수 있고, 위젯과 목표·확률 전망은 구독하면 다시 열려요."
    }

    /// 구독하면 열리는 것
    private var opens: some View {
        Card {
            Text("구독하면").appFont(15, .bold)
            VStack(alignment: .leading, spacing: 4) {
                ForEach(["위젯 전부 (기본·인물·서재·잠금 화면)", "목표(내 길)와 확률 전망", "서재 외전 『이종 공명』 6장부터 끝까지 (앱 연재판)",
                         "앱 연재판 1권: 연 구독, 또는 월 구독 \(Support.vol1Months)개월 이상"], id: \.self) { a in
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("·").appFont(14, .bold)
                        Text(a).appFont(14).fixedSize(horizontal: false, vertical: true)
                    }
                }
                if s.plan == .monthly && !s.vol1Open {
                    Text("1권까지 월 구독 \(Support.vol1Months - s.monthsPaid)번 남았어요.").appFont(13).foregroundStyle(Theme.sub)
                }
            }
        }
    }

    private func planCard(_ plan: SubPlan) -> some View {
        let on = s.plan == plan, p = s.product(plan)
        return Card(stroke: on ? Theme.teal : Theme.border) {
            HStack(alignment: .firstTextBaseline) {
                Text(plan.name).appFont(17, .bold)
                Spacer()
                Text(p.map { "\($0.displayPrice) / \(plan.unit)" } ?? (plan == .monthly ? "1,000원 / 한 달" : "9,000원 / 1년"))
                    .appFont(15, .bold).foregroundStyle(Theme.sub)
            }
            if plan == .yearly { Text("월 구독보다 25% 싸고, 1권이 바로 열려요.").appFont(13).foregroundStyle(Theme.sub) }
            Button { if let p { Task { await s.buy(p) } } } label: {
                Text(on ? "구독 중이에요" : p == nil ? "지금은 구독할 수 없어요" : s.busy == p?.id ? "결제하는 중…" : "\(plan.name)하기")
                    .appFont(15, .bold).foregroundStyle(on ? Theme.teal : .white)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(on ? Theme.mintBg : Theme.teal, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain).disabled(on || p == nil || s.busy != nil)
        }
    }

    private func outline(_ t: String, _ go: @escaping () -> Void) -> some View {
        Button(action: go) {
            Text(t).appFont(15, .bold).foregroundStyle(Theme.teal)
                .frame(maxWidth: .infinity, minHeight: 44)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }
}

/// 7일 무료가 끝나 잠긴 화면 (분석 › 내 길·확률 전망). 누르면 설정 > 구독으로
struct SubLocked: View {
    @Environment(AppModel.self) private var m
    let what: String
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "lock.fill").font(.system(size: 28, weight: .bold)).foregroundStyle(Theme.teal)
            Text("7일 무료가 끝났어요").appFont(20, .bold)
            Text("\(what)은 구독하면 다시 볼 수 있어요.\n월 1,000원 · 연 9,000원")
                .appFont(14).foregroundStyle(Theme.sub).multilineTextAlignment(.center).lineSpacing(3)
            Button { m.tab = .settings; m.settingsPath = [.support] } label: {
                Text("구독 보기").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: 240, minHeight: 44)
                    .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.bg)
    }
}
