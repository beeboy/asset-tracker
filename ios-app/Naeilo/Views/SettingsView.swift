import SwiftUI

enum SettingsRoute: Hashable { case alerts, sync, widgets, tax, price, howto, route, goal, charPreview, support }

struct SettingsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let onN = m.alerts.values.filter { $0 }.count
        let rows: [(String, String, SettingsRoute)] = [
            ("알림", onN > 0 ? "\(onN)개 켜짐" : "모두 꺼짐", .alerts),
            ("기기 동기화", m.syncOn ? "자동 동기화 켜짐 · naeilo.com과 같은 값" : "꺼짐", .sync),
            ("위젯", "받은 위젯 \(m.baseWidgets.filter(\.ok).count)/\(m.baseWidgets.count) · 인물 \(Shelter.friends.indices.filter { m.friendOn($0) }.count)/\(Shelter.friends.count) · 미리보기", .widgets),
            ("세금 규칙", "대한민국 거주자", .tax),
            ("시세 기준", "미국 종목·환율 지금 가격 · 한국 종목 전일 종가", .price),
            ("사용 방법", "매일 루틴, 1000칸, 주간 예보", .howto),
            ("개발자 후원", Support.shared.tier == .free ? "커피 한 잔 · 구매 복원" : "\(Support.shared.tier.name) · 고마워요", .support),
            ("루트", m.route == .recover ? "회복 · 마이너스" : m.route == .plus ? "목표 · 플러스" : "목표 · 시작 전", .route),
        ] + (m.isGoal ? [("목표", "\(m.gY)년 뒤 \(AppModel.wonK(m.gK)) · 고치기", SettingsRoute.goal)] : [])
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader(tab: "설정").padding(.horizontal, -16)
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { i, r in
                        if i > 0 { Divider().overlay(Theme.line) }
                        NavigationLink(value: r.2) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.0).appFont(15, .bold)
                                    Text(r.1).appFont(13).foregroundStyle(Theme.sub)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").appFont(13).foregroundStyle(Theme.muted)
                            }
                            .padding(.horizontal, 16).frame(minHeight: 60).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                    }
                }
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))

                Text("화면 모드").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
                ChipRow(items: [("system", "아이폰 설정 따라가기"), ("light", "밝게"), ("dark", "어둡게")],
                        selection: Binding(get: { m.appearance }, set: { m.appearance = $0; UserDefaults.standard.set($0, forKey: "appearance") }), fill: true)
                Text("앱 아이콘").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
                AppIconPicker()
                GuidePickRow().padding(.top, 4)
                // 시연 상태로 되돌리기는 내 종목을 샘플로 덮어쓴다: 개발자 기기(테스트 빌드)에서만
                if m.devAll {
                    Text("시연 상태 (개발자)").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
                    HStack(spacing: 8) {
                        demoButton("첫 질문부터") { m.onboarded = false; UserDefaults.standard.set(false, forKey: "onboarded") }
                        demoButton("미션 1부터") { m.resetDemo(.fresh) }
                        demoButton("인터미션 1주차로") { m.resetDemo(.week1) }
                        demoButton("모든 화면 열기") { m.resetDemo(.all) }
                    }
                }
                Text("시세: \(Market.shared.source)").appFont(12).foregroundStyle(Theme.muted)
                Text("버전 0.1 · 서재: 외전 프롤로그~5장 (한글·영문)").appFont(12).foregroundStyle(Theme.muted)
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: SettingsRoute.self) { r in
            switch r {
            case .alerts: AlertsView()
            case .sync: SyncView()
            case .widgets: WidgetPickView()
            case .tax: TaxRulesView()
            case .price: PriceBasisView()
            case .howto: HowToView()
            case .route: RouteView()
            case .goal: GoalSetView(fromSettings: true)
            case .support: SupportView()
            case .charPreview: WidgetPickView()     // 예전 '인물 위젯 미리보기'는 위젯 화면으로 합쳤다
            }
        }
    }

    private func demoButton(_ t: String, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).appFont(14, .semibold).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(Theme.ink)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }.buttonStyle(.plain)
    }
}

struct PriceBasisView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let mk = Market.shared, fx = mk.fx
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("시세 기준").appFont(22, .bold)
                VStack(alignment: .leading, spacing: 6) {
                    Text("지금 보는 숫자").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    Text(mk.source == "Tiingo" ? "실시간 참고 시세" : "테스트 자료").appFont(28, .bold)
                    Text(mk.asOfText).appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
                Card {
                    row("미국 종목", "지금 가격과 전일 종가")
                    row("원/달러", "\(Int(fx.last.rounded()).formatted())원 · 오늘 \(AppModel.sgn(fx.change))")
                    row("한국 종목", "전일 종가")
                    row("오늘의 움직임", "전일 대비 지금 평가액")
                    row("지금 값", mk.source == "Tiingo" ? "Tiingo에서 받은 값" : mk.site != nil ? "사이트가 평일 30분마다 모으는 테스트 자료. 당기면 바로 받아요" : "앱에 넣어 둔 테스트 자료")
                }
                Text("그래프와 미션은 종가로 계산해요.")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                // 시세 업체와 계약하면 그 업체의 출처 표기를 여기에 넣는다 (Tiingo: "Data powered by Tiingo.com")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await Market.shared.pullRefresh(m) }
    }
    private func row(_ k: String, _ v: String) -> some View {
        HStack(alignment: .top) { Text(k).fontWeight(.semibold).frame(width: 80, alignment: .leading); Text(v).foregroundStyle(Theme.sub) }
            .appFont(14)
    }
}

// 앱 아이콘 바꾸기: 기본 A · 내일의 별 + 인물 5. 각각 다크·틴트 모양이 따로 있어 홈 화면 모드를 따라간다
// 인물 아이콘은 그 인물을 만나야 열린다 (세리는 처음부터, 나머지는 인터미션 1~4주차. 쉼터와 같은 단계)
struct AppIconPicker: View {
    @Environment(AppModel.self) private var m
    static let icons: [(id: String?, name: String, prev: String, friend: Int?)] = [
        (nil, "내일의 별", "iconprev_star", nil), ("AppIcon-seri", "세리", "iconprev_seri", 0), ("AppIcon-sio", "시오", "iconprev_sio", 1),
        ("AppIcon-seonbae", "선배", "iconprev_seonbae", 2), ("AppIcon-ir", "이르", "iconprev_ir", 3), ("AppIcon-sua", "수아", "iconprev_sua", 4),
    ]
    @State private var current: String? = UIApplication.shared.alternateIconName

    private func open(_ f: Int?) -> Bool { f.map { m.friendOn($0) } ?? true }
    private func set(_ id: String?) {
        guard UIApplication.shared.supportsAlternateIcons, current != id else { return }
        UIApplication.shared.setAlternateIconName(id) { err in if err == nil { current = id } }
    }

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 12) {
            ForEach(Self.icons, id: \.name) { ic in
                let on = current == ic.id, ok = open(ic.friend)
                Button { if ok { set(ic.id) } } label: {
                    VStack(spacing: 6) {
                        Image(ic.prev).resizable().interpolation(.high).scaledToFit()
                            .frame(width: 64, height: 64)
                            .saturation(ok ? 1 : 0).brightness(ok ? 0 : -0.35)
                            .overlay { if !ok { Image(systemName: "lock.fill").font(.system(size: 20, weight: .bold)).foregroundStyle(.white) } }
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 3 : 1))
                        Text(ok ? ic.name : "인터미션 \(ic.friend ?? 0)주차").appFont(ok ? 13 : 11, on ? .bold : .regular)
                            .foregroundStyle(on ? Theme.ink : ok ? Theme.sub : Theme.muted).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!ok)
                .accessibilityLabel(ok ? "\(ic.name) 아이콘" : "\(ic.name) 아이콘, 인터미션 \(ic.friend ?? 0)주차에 열려요")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(8)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        // 시안 조작으로 단계를 되돌려 지금 아이콘이 다시 잠기면 기본 아이콘으로 돌린다
        .onAppear {
            if let c = current, let ic = Self.icons.first(where: { $0.id == c }), !open(ic.friend) { set(nil) }
        }
    }
}
