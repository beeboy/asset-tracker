import SwiftUI

enum SettingsRoute: Hashable { case alerts, sync, widgets, tax, price, howto, route, charPreview }

struct SettingsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let onN = m.alerts.values.filter { $0 }.count
        let rows: [(String, String, SettingsRoute)] = [
            ("알림", onN > 0 ? "\(onN)개 켜짐" : "모두 꺼짐", .alerts),
            ("기기 동기화", m.syncOn ? "PC naeilo.com과 연결됨" : "연결 안 됨", .sync),
            ("위젯", "받은 위젯 \(m.widgets.filter(\.ok).count)개 · 홈 화면 \(m.widgetSelected.count)개", .widgets),
            ("세금 규칙", "대한민국 거주자", .tax),
            ("시세 기준", "미국 종목·환율 지금 가격 · 한국 종목 전일 종가", .price),
            ("사용 방법", "매일 루틴, 1000칸, 주간 예보", .howto),
            ("루트", m.route == .recover ? "회복 · 마이너스" : m.route == .plus ? "목표 · 플러스" : "목표 · 시작 전", .route),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader().padding(.horizontal, -16)
                Text("설정").appFont(22, .bold)
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
                Text("시안 조작").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
                HStack(spacing: 8) {
                    demoButton("첫 질문부터") { m.onboarded = false; UserDefaults.standard.set(false, forKey: "onboarded") }
                    demoButton("미션 1부터") { m.resetDemo(.fresh) }
                    demoButton("인터미션 1주차로") { m.resetDemo(.week1) }
                    demoButton("모든 화면 열기") { m.resetDemo(.all) }
                }
                NavigationLink(value: SettingsRoute.charPreview) {
                    Text("인물 위젯 미리보기 ›").appFont(14, .semibold).frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(Theme.ink)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
                }.buttonStyle(.plain)
                Text("시세: \(Market.shared.source)").appFont(12).foregroundStyle(Theme.muted)
                Text("시세는 스텁(시안과 같은 예시 값)이고, 외전 원고도 서버 대신 스텁이에요. 버전 0.1").appFont(12).foregroundStyle(Theme.muted)
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
            case .charPreview: CharWidgetPreview()
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
                    row("미국 종목", "지금 가격과 전일 종가 (계약하면 실시간 참고 시세)")
                    row("원/달러", "\(Int(fx.last.rounded()).formatted())원 · 오늘 \(AppModel.sgn(fx.change))")
                    row("한국 종목", "전일 종가 (한국 시세 제공처를 정하면 바뀌어요)")
                    row("오늘의 움직임", "전일 종가·전일 환율 대비 지금 평가액")
                    row("지금 값", mk.source == "Tiingo" ? "Tiingo에서 받은 값" : "Yahoo 중계 테스트 자료 (QQQ·AAPL·NVDA·SPY·원/달러). DRNK는 가상 종목, 한국 종목은 시안 값")
                }
                Text("숫자가 하루 동안 움직이지만, naeilo는 사고파는 앱이 아니라 본전과 목표까지의 길을 보는 앱이에요. 그래서 그래프와 미션은 종가로 계산해요.")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                // 시세 업체와 계약하면 그 업체의 출처 표기를 여기에 넣는다 (Tiingo: "Data powered by Tiingo.com")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await Market.shared.refresh() }
    }
    private func row(_ k: String, _ v: String) -> some View {
        HStack(alignment: .top) { Text(k).fontWeight(.semibold).frame(width: 80, alignment: .leading); Text(v).foregroundStyle(Theme.sub) }
            .appFont(14)
    }
}

// 앱 아이콘 바꾸기: 기본 A · 내일의 별 + 인물 5. 각각 다크·틴트 모양이 따로 있어 홈 화면 모드를 따라간다
struct AppIconPicker: View {
    static let icons: [(id: String?, name: String, prev: String)] = [
        (nil, "내일의 별", "iconprev_star"), ("AppIcon-seri", "세리", "iconprev_seri"), ("AppIcon-sio", "시오", "iconprev_sio"),
        ("AppIcon-seonbae", "선배", "iconprev_seonbae"), ("AppIcon-ir", "이르", "iconprev_ir"), ("AppIcon-sua", "수아", "iconprev_sua"),
    ]
    @State private var current: String? = UIApplication.shared.alternateIconName

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 12) {
            ForEach(Self.icons, id: \.name) { ic in
                let on = current == ic.id
                Button {
                    guard UIApplication.shared.supportsAlternateIcons, !on else { return }
                    UIApplication.shared.setAlternateIconName(ic.id) { err in
                        if err == nil { current = ic.id }
                    }
                } label: {
                    VStack(spacing: 6) {
                        Image(ic.prev).resizable().interpolation(.high).scaledToFit()
                            .frame(width: 64, height: 64)
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 3 : 1))
                        Text(ic.name).appFont(13, on ? .bold : .regular).foregroundStyle(on ? Theme.ink : Theme.sub)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(ic.name) 아이콘")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(8)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }
}
