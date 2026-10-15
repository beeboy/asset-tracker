import SwiftUI

enum SettingsRoute: Hashable { case alerts, sync, widgets, tax, price, toss, howto, route, goal, charPreview, support, icon }

struct SettingsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let onN = m.alerts.values.filter { $0 }.count
        // 위에서 아래로: 목표 카드 → 알림과 위젯 → 함께할 인물 → 화면 → 계산 기준 → 도움과 기타
        let oftenRows: [(String, String, SettingsRoute)] = [
            ("알림", onN > 0 ? "\(onN)개 켜짐" : "모두 꺼짐", .alerts),
            ("홈 화면 위젯", "\(m.baseWidgets.count + Shelter.friends.count)가지 · 미리보기", .widgets),
        ]
        let refRows: [(String, String, SettingsRoute)] = [
            ("가격 기준", "미국 종목·환율은 지금 가격 · 한국 종목은 넣은 가격", .price),
            ("세금", "한국 거주자 기준으로 계산해요", .tax),
        ]
        // 기기 동기화(웹과 앱) 자리를 내 토스 키 입력으로 바꿨다 (동기화 코드는 남김)
        let helpRows: [(String, String, SettingsRoute)] = [
            ("처음 쓰는 법", "매일 1분 · 목표 칸 · 주간 예보", .howto),
            ("내 토스 키", TossKeys.on ? "내 토스 키로 시세 받는 중" : "내 토스증권 Open API 키 연결하기", .toss),
            ("개발자에게 커피 한 잔", Support.shared.tier == .free ? "구매 복원" : "\(Support.shared.tier.name) · 고마워요", .support),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader(tab: "설정").padding(.horizontal, -16)
                goalCard
                group("알림과 위젯", oftenRows)
                head("함께할 인물")
                GuidePickRow()
                head("화면")
                Text("밝기 모드").appFont(13, .bold).foregroundStyle(Theme.sub)
                ChipRow(items: [("system", "기기 설정대로"), ("light", "밝게"), ("dark", "어둡게")],
                        selection: Binding(get: { m.appearance }, set: { m.appearance = $0; UserDefaults.standard.set($0, forKey: "appearance") }), fill: true)
                group(nil, [("앱 아이콘", AppIconPicker.currentName, .icon)])
                group("숫자를 계산하는 기준", refRows)
                group("도움과 기타", helpRows)
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
                Text("시세: \(Market.shared.source) · \(Config.channel.rawValue)").appFont(12).foregroundStyle(Theme.muted)
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
            case .toss: TossKeyView()
            case .howto: HowToView()
            case .route: RouteView()
            case .goal: GoalSetView(fromSettings: true)
            case .support: SupportView()
            case .icon: AppIconScreen()
            case .charPreview: WidgetPickView()     // 예전 '인물 위젯 미리보기'는 위젯 화면으로 합쳤다
            }
        }
    }

    /// 목표 카드: 날짜·금액 + 지금 어느 길인지 쉬운 말 한 줄 (예전 "루트 · 플러스" 줄을 합쳤다)
    private var goalCard: some View {
        let routeLine = m.route == .recover ? "본전부터 되찾는 중이에요" : m.route == .plus ? "목표를 향해 가는 중이에요" : "아직 첫 단계예요"
        return VStack(alignment: .leading, spacing: 6) {
            if m.isGoal {
                Text("\(m.goalToText)까지").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Text(AppModel.wonK(m.gK)).appFont(28, .bold)
            } else {
                Text("내 목표").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            NavigationLink(value: SettingsRoute.route) {
                HStack(spacing: 4) {
                    Text(routeLine).appFont(15, .semibold)
                    Image(systemName: "chevron.right").appFont(12)
                }.frame(minHeight: 44).contentShape(Rectangle())
            }.buttonStyle(.plain)
            if m.isGoal {
                NavigationLink(value: SettingsRoute.goal) {
                    // 다른 버튼(PrimaryButton)과 같은 모양: 청록 바탕 + 흰 글자 (라이트·다크 공통)
                    Text("목표 고치기").appFont(16, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                }.buttonStyle(.plain)
            }
        }
        .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    private func head(_ t: String) -> some View {
        Text(t).appFont(13, .bold).foregroundStyle(Theme.teal).padding(.top, 8)
    }

    @ViewBuilder private func group(_ title: String?, _ rows: [(String, String, SettingsRoute)]) -> some View {
        if let title { head(title) }
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
                    row("평가 환율", mk.marRate != nil ? "매매기준율 \(String(format: "%.2f", mk.valFx))원 · 웹 평가액과 같아요" : "매매기준율을 아직 못 받아 지금 환율")
                    row("한국 종목", "많이 찾는 국내 주식은 사이트가 매일 모은 가격이에요. 1년 기록은 모으기 시작한 날부터 쌓여요. 미국 지수를 따르는 국내 ETF는 넣은 가격에서 그 미국 ETF × 원/달러 움직임으로 추정해요")
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

// 토스증권 시세 (일반 사용자): 내가 받은 토스 Open API 키를 넣으면 국내·미국 종목 지금 가격을 토스에서 받는다.
// 키는 이 폰 키체인에만 두고, 공용 중계(Config.tossRelay)는 키를 저장하지 않고 토스에 넘기기만 한다.
// 토스는 등록한 IP 에서만 받으므로 사용자는 중계 IP 를 토스 WTS 허용 IP 에 넣어야 한다.
struct TossKeyView: View {
    @Environment(AppModel.self) private var m
    @State private var id = ""
    @State private var secret = ""
    @State private var ip: String? = nil
    @State private var busy = false
    @State private var msg: String? = nil
    @State private var on = TossKeys.on
    @State private var askOff = false
    @State private var copied = false

    private var status: String {
        _ = Market.shared.tossDone      // 토스 받기를 마치면 다시 그린다
        let t = TossFeed.okAt
        var s = ""
        if t > 0 {
            let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR")
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm:ss" : "M월 d일 a h:mm"
            s = "마지막으로 받은 때 \(f.string(from: d))"
        } else { s = "아직 받은 적 없어요" }
        if let e = TossFeed.lastError { s += " · 실패: \(e)" }
        return s
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("내 토스 키").appFont(22, .bold)
                if on { onCard } else { steps }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .task { ip = await TossKeys.relayIP() }
    }

    private var onCard: some View {
        Card {
            Text("내 키로 받는 중").appFont(15, .bold)
            Text(status).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            if let ip { ipRow(ip) }
            PrimaryButton(title: busy ? "받는 중…" : "지금 다시 받기") { Task { await check() } }
            if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
            Button("키 지우기") { askOff = true }
                .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                .confirmationDialog("토스 키를 이 폰에서 지울까요?", isPresented: $askOff, titleVisibility: .visible) {
                    Button("지우기", role: .destructive) { TossKeys.clear(); on = false; msg = nil; Task { await Market.shared.pullRefresh(m) } }
                } message: { Text("기본 시세로 돌아가요. 토스에서 받은 키는 토스 WTS에서 따로 지울 수 있어요.") }
        }
    }

    private var steps: some View {
        Card {
            // 안내는 세 줄만 (토스 키 발급 절차는 설명하지 않는다)
            Text("1. 내 토스증권 Open API 키를 연결하면 시세를 받을 수 있어요.").appFont(15, .bold).fixedSize(horizontal: false, vertical: true)
            Text("2. 토스 허용 IP에 이 중계 IP를 넣어 주세요.").appFont(15, .bold).padding(.top, 6).fixedSize(horizontal: false, vertical: true)
            if let ip { ipRow(ip) } else { Text("중계 IP를 불러오는 중이에요…").appFont(13).foregroundStyle(Theme.muted) }
            Text("3. client_id·client_secret을 넣고 연결하기를 눌러 주세요.").appFont(15, .bold).padding(.top, 6).fixedSize(horizontal: false, vertical: true)
            field("client_id", $id, secure: false)
            field("client_secret", $secret, secure: true)
            let ok = !id.trimmingCharacters(in: .whitespaces).isEmpty && !secret.trimmingCharacters(in: .whitespaces).isEmpty
            PrimaryButton(title: busy ? "확인 중…" : "연결하기", color: ok ? Theme.teal : Theme.muted) {
                guard ok, !busy else { return }
                TossKeys.save(.init(id: id.trimmingCharacters(in: .whitespaces), secret: secret.trimmingCharacters(in: .whitespaces)))
                secret = ""
                Task { await check() }
            }
            if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
        }
    }

    private func ipRow(_ ip: String) -> some View {
        HStack {
            Text(ip).appFont(17, .bold).textSelection(.enabled)
            Spacer()
            Button(copied ? "복사됨" : "복사") { UIPasteboard.general.string = ip; copied = true }
                .appFont(14, .semibold).foregroundStyle(Theme.teal).frame(minHeight: 44)
        }
        .padding(.horizontal, 12).background(Theme.track, in: RoundedRectangle(cornerRadius: 12))
    }

    private func field(_ title: String, _ v: Binding<String>, secure: Bool) -> some View {
        Group {
            if secure { SecureField(title, text: v) } else { TextField(title, text: v) }
        }
        .textContentType(secure ? .password : nil).autocorrectionDisabled().textInputAutocapitalization(.never)
        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
    }

    /// 토큰을 새로 받아 키·허용 IP 를 확인하고, 되면 시세를 바로 받는다
    private func check() async {
        busy = true; msg = nil
        // 토큰은 만료 전이면 그대로 쓴다 (새로 받으면 받는 중인 다른 요청의 토큰이 끊긴다). 처음 연결이면 키를 넣을 때 토큰을 지워 새로 받는다
        let (t, e) = await TossFeed.token()
        if t == nil {
            msg = (e ?? "연결 실패") + (e == "허용 IP에 중계 IP가 없어요" ? ". 토스 허용 IP에 위 IP를 넣고 다시 눌러 주세요." : "")
            if !on { TossKeys.clear() }     // 처음 연결이 실패하면 키를 남기지 않는다
        } else {
            on = true
            await Market.shared.refresh(held: Set(m.holdings.map(\.symbol)), force: true)
        }
        busy = false
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
    static var currentName: String { icons.first { $0.id == UIApplication.shared.alternateIconName }?.name ?? "내일의 별" }

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

/// 설정 > 앱 아이콘 › 고르는 화면 (설정 첫 화면에서 격자를 뺐다)
struct AppIconScreen: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("앱 아이콘").appFont(22, .bold)
                Text("인물 아이콘은 그 인물을 만나면 열려요.").appFont(13).foregroundStyle(Theme.sub)
                AppIconPicker()
            }.padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}
