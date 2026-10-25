import SwiftUI

// 개발자: 출시 알림 신청 목록 (워커 GET /notify/list). 관리자 키는 키체인에, 비어 있으면 개발자 동기화 토큰을 쓴다.
struct NotifyListView: View {
    struct Row: Decodable, Hashable { let email: String; let at: String }
    private struct Resp: Decodable { let n: Int?; let rows: [Row]?; let error: String? }

    @State private var key = AdminKey.load() ?? ""
    @State private var rows: [Row] = []
    @State private var busy = false
    @State private var msg: String? = nil
    @State private var copied = false

    private var token: String { key.isEmpty ? (Sync.shared.creds?.token ?? "") : key }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("출시 알림 신청").appFont(22, .bold)
                Card {
                    SecureField("관리자 키", text: $key)
                        .textContentType(nil).autocorrectionDisabled().textInputAutocapitalization(.never)
                        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                        .onSubmit { Task { await load() } }
                    PrimaryButton(title: busy ? String(localized: "받는 중…") : String(localized: "새로고침")) { Task { await load() } }
                    if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
                }
                if !rows.isEmpty {
                    HStack {
                        Text("\(rows.count)명").appFont(17, .bold)
                        Spacer()
                        Button(copied ? String(localized: "복사됨") : String(localized: "주소 전부 복사")) {
                            UIPasteboard.general.string = rows.map(\.email).joined(separator: ", "); copied = true
                        }.appFont(15, .semibold)
                    }
                    Card {
                        ForEach(Array(rows.enumerated()), id: \.offset) { i, r in
                            if i > 0 { Divider().overlay(Theme.line) }
                            HStack {
                                Text(r.email).appFont(14).textSelection(.enabled).lineLimit(1).truncationMode(.middle)
                                Spacer()
                                Text(r.at).appFont(13).foregroundStyle(Theme.sub)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .task { if !token.isEmpty { await load() } }
    }

    private func load() async {
        guard !token.isEmpty else { msg = String(localized: "관리자 키를 넣어 주세요"); return }
        busy = true; msg = nil; copied = false
        defer { busy = false }
        var req = URLRequest(url: URL(string: "https://asset-ai.drinker.workers.dev/notify/list")!)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.timeoutInterval = 20
        do {
            let (d, res) = try await URLSession.shared.data(for: req)
            let r = try? JSONDecoder().decode(Resp.self, from: d)
            let code = (res as? HTTPURLResponse)?.statusCode ?? 0
            if code == 403 { msg = String(localized: "키가 맞지 않아요"); return }
            guard code == 200, let list = r?.rows else { msg = r?.error ?? String(localized: "목록을 못 받았어요 (\(String(describing: code)))"); return }
            if !key.isEmpty { AdminKey.save(key) }
            rows = list.sorted { $0.at > $1.at }
            if rows.isEmpty { msg = String(localized: "아직 신청한 사람이 없어요") }
        } catch {
            msg = String(localized: "인터넷 연결을 확인해 주세요")
        }
    }
}

/// 출시 알림 목록용 관리자 키 (이 기기 키체인에만)
enum AdminKey {
    private static let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.admin", kSecAttrAccount as String: "notify"]
    static func save(_ k: String) {
        SecItemDelete(q as CFDictionary)
        var a = q
        a[kSecValueData as String] = Data(k.utf8)
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(a as CFDictionary, nil)
    }
    static func load() -> String? {
        var a = q
        a[kSecReturnData as String] = true
        a[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(a as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }
}

enum SettingsRoute: Hashable { case alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence, goalGroup, oftenGroup, screenGroup, numberGroup }

/// 설정 첫 화면: 내 키 한 줄 → 함께할 인물 → 타일 넷(목표·알림과 위젯·화면·숫자 기준) → 작은 글 링크.
/// 타일을 누르면 그 묶음 화면(SettingsGroupView)으로 간다
struct SettingsView: View {
    @Environment(AppModel.self) private var m
    var cover = false   // 홈 [설정] 전체 화면 팝업: 맨 위 줄을 블록·파도처럼 제목 + X로
    @AppStorage(BoxLook.key) private var boxLook = BoxLook.jump.rawValue
    @State private var showDemo = false     // 시연 상태: 개발자 기기에서 버전 글을 길게 누르면 열린다

    /// Longbridge 를 Alpaca 보다 먼저 보이는 거주 국가
    static let lbFirst = ["HK", "CN", "SG", "MO", "TW", "MY"]

    var body: some View {
        let onN = m.alerts.values.filter { $0 }.count
        let looks: [String: String] = ["system": String(localized: "기기 설정대로"), "light": String(localized: "밝게"), "dark": String(localized: "어둡게")]
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if cover { GameHeader(title: String(localized: "설정")) } else { AppHeader(tab: String(localized: "설정")).padding(.horizontal, -16) }
                apiLine
                GuidePickRow(compact: true)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    tile("flag", String(localized: "내 목표"),
                         m.isGoal ? String(localized: "\(String(describing: m.goalToText))까지 \(String(describing: AppModel.wonK(m.gK)))") : String(localized: "아직 목표가 없어요"),
                         SettingsGroupView.routeLine(m), .goalGroup)
                    tile("bell", String(localized: "알림과 위젯"),
                         onN > 0 ? String(localized: "알림 \(String(describing: onN))개 켜짐") : String(localized: "알림 모두 꺼짐"),
                         String(localized: "위젯 \(String(describing: m.baseWidgets.count + Shelter.friends.count))가지"), .oftenGroup)
                    tile("iphone", String(localized: "화면"),
                         looks[m.appearance] ?? looks["system"]!,
                         BoxLook(rawValue: boxLook)?.name ?? "", .screenGroup)
                    tile("chart.pie", String(localized: "숫자 기준"),
                         m.residenceName, String(localized: "시세 · 내 API 키"), .numberGroup)
                }
                footer
                if m.devAll && showDemo { demo }
                Text("버전 0.1 · 서재: 외전 프롤로그~5장 (한글·영문)").appFont(12).foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, minHeight: 32)
                    .contentShape(Rectangle())
                    .onLongPressGesture { if m.devAll { showDemo.toggle() } }
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
            case .alpaca: AlpacaKeyView()
            case .longbridge: LongbridgeKeyView()
            case .howto: HowToView()
            case .route: RouteView()
            case .goal: GoalSetView(fromSettings: true)
            case .support: SupportView()
            case .icon: AppIconScreen()
            case .notifyList: NotifyListView()
            case .residence: ResidenceView()
            case .charPreview: WidgetPickView()     // 예전 '인물 위젯 미리보기'는 위젯 화면으로 합쳤다
            case .goalGroup, .oftenGroup, .screenGroup, .numberGroup: SettingsGroupView(kind: r)
            }
        }
    }

    /// 맨 위 조용한 한 줄: 거주 국가에 맞는 내 키(한국 토스, lbFirst 나라 Longbridge, 그 밖 Alpaca).
    /// 평소엔 배경과 같은 결, 키를 넣었는데 받기가 실패하면 주황으로 눈에 띈다
    private var apiLine: some View {
        let kr = m.residence == "KR"
        let lb = Self.lbFirst.contains(m.residence)
        _ = kr ? Market.shared.tossDone : lb ? Market.shared.longbridgeDone : Market.shared.alpacaDone      // 받기를 마치면 다시 그린다
        let on = kr ? TossKeys.on : lb ? LongbridgeKeys.on : AlpacaKeys.on
        let broken = on && (kr ? TossFeed.lastError : lb ? LongbridgeFeed.lastError : AlpacaFeed.lastError) != nil
        return NavigationLink(value: kr ? SettingsRoute.toss : lb ? SettingsRoute.longbridge : SettingsRoute.alpaca) {
            HStack(spacing: 10) {
                Image(systemName: broken ? "info.circle.fill" : on ? "lock.fill" : "key.fill")
                    .appFont(14).foregroundStyle(broken ? Theme.orange : on ? Theme.teal : Theme.muted)
                Text(broken ? String(localized: "내 키로 못 받고 있어요") : on ? String(localized: "내 키로 시세 받는 중") : String(localized: "내 키로 시세 받기"))
                    .appFont(14, .semibold).lineLimit(1)
                Spacer(minLength: 4)
                Text(kr ? String(localized: "현재가·일봉·환율") : String(localized: "미국 종목 현재가")).appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
                Image(systemName: "chevron.right").appFont(12).foregroundStyle(Theme.muted)
            }
            .padding(.horizontal, 14).frame(minHeight: 48)
            .background(broken ? Theme.orange.opacity(0.16) : Theme.track, in: RoundedRectangle(cornerRadius: 14))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func tile(_ icon: String, _ title: String, _ line1: String, _ line2: String, _ r: SettingsRoute) -> some View {
        NavigationLink(value: r) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: icon).appFont(20).foregroundStyle(Theme.teal).accessibilityHidden(true)
                Text(title).appFont(16, .bold)
                VStack(alignment: .leading, spacing: 1) {
                    Text(line1).lineLimit(1)
                    Text(line2).lineLimit(1)
                }
                .appFont(12).foregroundStyle(Theme.sub)
            }
            .padding(14).frame(maxWidth: .infinity, minHeight: 116, alignment: .topLeading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 자주 안 여는 것: 작은 글 링크 한 줄
    private var footer: some View {
        HStack(spacing: 18) {
            NavigationLink(value: SettingsRoute.howto) { Text("처음 쓰는 법") }
            NavigationLink(value: SettingsRoute.sync) { Text("기기 옮기기") }
            NavigationLink(value: SettingsRoute.support) { Text("구독") }
            // 앱 언어는 iOS 설정의 앱 화면에서 바꾼다 (누르면 그 화면이 열린다)
            Button("언어") { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
        }
        .buttonStyle(.plain).appFont(13).foregroundStyle(Theme.sub)
        .frame(maxWidth: .infinity, minHeight: 44).padding(.top, 8)
    }

    // 시연 상태로 되돌리기는 내 종목을 샘플로 덮어쓴다: 개발자 기기(테스트 빌드)에서만
    @ViewBuilder private var demo: some View {
        Text("시연 상태 (개발자)").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
        HStack(spacing: 8) {
            demoButton(String(localized: "첫 질문부터")) { m.onboarded = false; UserDefaults.standard.set(false, forKey: "onboarded") }
            demoButton(String(localized: "미션 1부터")) { m.resetDemo(.fresh) }
            demoButton(String(localized: "인터미션 1주차로")) { m.resetDemo(.week1) }
            demoButton(String(localized: "모든 화면 열기")) { m.resetDemo(.all) }
        }
        SettingsRows(title: nil, rows: [(String(localized: "출시 알림 신청 목록"), String(localized: "naeilo.com 앱 받기 신청"), .notifyList)])
        Text("시세: \(Market.shared.source) · \(Config.channel.rawValue)").appFont(12).foregroundStyle(Theme.muted)
    }

    private func demoButton(_ t: String, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).appFont(14, .semibold).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(Theme.ink)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }.buttonStyle(.plain)
    }
}

/// 타일을 누르면 열리는 묶음 화면
struct SettingsGroupView: View {
    @Environment(AppModel.self) private var m
    let kind: SettingsRoute
    @AppStorage(BoxLook.key) private var boxLook = BoxLook.jump.rawValue
    @AppStorage(BoxScheme.key) private var boxScheme = BoxScheme.system.rawValue

    static func routeLine(_ m: AppModel) -> String {
        m.route == .recover ? String(localized: "본전부터 되찾는 중이에요") : m.route == .plus ? String(localized: "목표를 향해 가는 중이에요") : String(localized: "아직 첫 단계예요")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch kind {
                case .goalGroup:
                    Text("내 목표").appFont(22, .bold)
                    SettingsRows(title: nil, rows: [
                        (String(localized: "목표 고치기"), m.isGoal ? String(localized: "\(String(describing: m.goalToText))까지 \(String(describing: AppModel.wonK(m.gK)))") : String(localized: "아직 목표가 없어요"), .goal),
                        (String(localized: "루트 바꾸기"), Self.routeLine(m), .route),
                    ])
                case .oftenGroup:
                    let onN = m.alerts.values.filter { $0 }.count
                    Text("알림과 위젯").appFont(22, .bold)
                    SettingsRows(title: nil, rows: [
                        (String(localized: "알림"), onN > 0 ? String(localized: "\(String(describing: onN))개 켜짐") : String(localized: "모두 꺼짐"), .alerts),
                        (String(localized: "홈 화면 위젯"), String(localized: "\(String(describing: m.baseWidgets.count + Shelter.friends.count))가지 · 미리보기"), .widgets),
                    ])
                case .screenGroup:
                    Text("화면").appFont(22, .bold)
                    Text("밝기 모드").appFont(13, .bold).foregroundStyle(Theme.sub)
                    ChipRow(items: [("system", String(localized: "기기 설정대로")), ("light", String(localized: "밝게")), ("dark", String(localized: "어둡게"))],
                            selection: Binding(get: { m.appearance }, set: { m.appearance = $0; UserDefaults.standard.set($0, forKey: "appearance") }), fill: true)
                    // 홈 평가액 박스 모양 (홈 박스를 길게 눌러도 고른다)
                    Text("홈 평가액 박스").appFont(13, .bold).foregroundStyle(Theme.sub)
                    ChipRow(items: BoxLook.allCases.map { ($0.rawValue, $0.name) }, selection: $boxLook, fill: true)
                    // 상단 박스(홈·종목·흐름·분석)만 밝기 모드를 따로 고른다. 앱 전체 모드는 그대로
                    Text("상단 박스").appFont(13, .bold).foregroundStyle(Theme.sub)
                    ChipRow(items: BoxScheme.allCases.map { ($0.rawValue, $0.name) }, selection: $boxScheme, fill: true)
                    SettingsRows(title: nil, rows: [(String(localized: "앱 아이콘"), AppIconPicker.currentName, .icon)])
                    WaveFeedbackToggles()
                case .numberGroup:
                    let kr = m.residence == "KR"
                    // 증권사는 나라와 상관없이 누구나 연결: 거주 국가에 맞는 것만 위로 올린다
                    let toss: (String, String, SettingsRoute) = ("Toss", TossKeys.on ? String(localized: "받는 중") : String(localized: "넣지 않음"), .toss)
                    let alpaca: (String, String, SettingsRoute) = ("Alpaca", AlpacaKeys.on ? String(localized: "받는 중") : String(localized: "넣지 않음 · 미국 종목 실시간 시세"), .alpaca)
                    let lb: (String, String, SettingsRoute) = ("Longbridge", LongbridgeKeys.on ? String(localized: "받는 중") : String(localized: "넣지 않음 · 미국·홍콩·중국 실시간 시세"), .longbridge)
                    Text("숫자 기준").appFont(22, .bold)
                    SettingsRows(title: String(localized: "나라와 세금"), rows: [
                        (String(localized: "거주 국가"), String(localized: "\(m.residenceName) · 세금 계산만 이 나라를 따라요"), .residence),
                        (String(localized: "세금"), kr ? String(localized: "한국 거주자 기준으로 계산해요") : String(localized: "이 나라 규칙은 준비 중 · 지금은 한국 기준"), .tax),
                    ])
                    SettingsRows(title: String(localized: "시세"), rows: [(String(localized: "시세 기준"), String(localized: "종목·환율 시세를 어디서 받는지"), .price)])
                    SettingsRows(title: String(localized: "내 API 키"), rows: kr ? [toss, alpaca, lb] : SettingsView.lbFirst.contains(m.residence) ? [lb, alpaca, toss] : [alpaca, lb, toss])
                default:
                    EmptyView()
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }
}

/// 제목 + 누르면 넘어가는 줄 묶음 (설정 화면들이 같이 쓴다)
struct SettingsRows: View {
    let title: String?
    let rows: [(String, String, SettingsRoute)]

    var body: some View {
        if let title { Text(title).appFont(13, .bold).foregroundStyle(Theme.teal).padding(.top, 8) }
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
}

struct PriceBasisView: View {
    @Environment(AppModel.self) private var m
    @State private var yahooOff = Config.yahooOff
    @State private var tossPause = TossKeys.paused
    @State private var alpacaPause = AlpacaKeys.paused
    @State private var lbPause = LongbridgeKeys.paused
    var body: some View {
        let mk = Market.shared, fx = mk.fx
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("시세 기준").appFont(22, .bold)
                let main = mk.toss != nil ? String(localized: "토스 오픈 API") : mk.alpaca != nil ? String(localized: "Alpaca 시세") : mk.longbridge != nil ? String(localized: "Longbridge 시세") : mk.site != nil ? String(localized: "naeilo 사이트 시세") : String(localized: "예시 값")
                VStack(alignment: .leading, spacing: 6) {
                    Text("지금 보는 숫자").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    Text(main).appFont(28, .bold)
                    Text(mk.asOfText.replacingOccurrences(of: " (테스트 자료)", with: "")).appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
                Card {
                    row(String(localized: "받는 순서"), String(localized: "내 토스 키 → 내 Alpaca·Longbridge 키 → naeilo 사이트. 앞에서 받은 값을 먼저 써요"))
                    row(String(localized: "미국 종목"), mk.toss != nil ? String(localized: "토스 지금 가격") : mk.alpaca != nil ? String(localized: "Alpaca 지금 가격") : mk.longbridge != nil ? String(localized: "Longbridge 지금 가격") : String(localized: "사이트가 평일 30분마다 모은 가격"))
                    row(String(localized: "한국 종목"), mk.toss != nil ? String(localized: "토스 지금 가격") : String(localized: "사이트가 모은 가격. 미국 지수를 따르는 국내 ETF는 그 미국 ETF × 원/달러로 추정해요"))
                    row(String(localized: "장 마감 뒤"), String(localized: "미국 장이 끝나면 정규장 종가로 세요 (토스 총자산과 같은 기준)"))
                    row(String(localized: "원/달러"), String(localized: "\(String(describing: Int(fx.last.rounded()).formatted()))원 · 오늘 \(String(describing: AppModel.sgn(fx.change)))"))
                    row(String(localized: "평가 환율"), mk.marRate != nil ? String(localized: "매매기준율 \(String(describing: String(format: "%.2f", mk.valFx)))원 · 웹 평가액과 같아요") : String(localized: "매매기준율을 아직 못 받아 지금 환율"))
                    row(String(localized: "오늘의 움직임"), String(localized: "전일 종가 대비 지금 평가액"))
                }
                Text("그래프와 미션은 종가로 계산해요. 아래로 당기면 바로 다시 받아요.")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                if Config.isInternal {
                    // 시험용 (내부 베타만): 공개판처럼 내 키 시세만 보게 Yahoo·사이트 자료를 끈다
                    Card {
                        Toggle(isOn: Binding(get: { yahooOff }, set: { v in yahooOff = v; setYahooOff(v) })) { Text("Yahoo·사이트 시세 끄기").appFont(15, .bold) }.tint(Theme.teal)
                        Text("시험용이에요. 켜면 내 키로 받은 시세만 보여요.").appFont(13).foregroundStyle(Theme.sub)
                        if TossKeys.stored {
                            Toggle(isOn: Binding(get: { tossPause }, set: { v in tossPause = v; setPause("toss", v) })) { Text("토스 키 멈추기").appFont(15, .bold) }.tint(Theme.teal)
                        }
                        if AlpacaKeys.stored {
                            Toggle(isOn: Binding(get: { alpacaPause }, set: { v in alpacaPause = v; setPause("alpaca", v) })) { Text("Alpaca 키 멈추기").appFont(15, .bold) }.tint(Theme.teal)
                        }
                        if LongbridgeKeys.stored {
                            Toggle(isOn: Binding(get: { lbPause }, set: { v in lbPause = v; setPause("longbridge", v) })) { Text("Longbridge 키 멈추기").appFont(15, .bold) }.tint(Theme.teal)
                        }
                        if TossKeys.stored || AlpacaKeys.stored || LongbridgeKeys.stored {
                            Text("키는 그대로 두고 잠시 안 불러요.").appFont(13).foregroundStyle(Theme.sub)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .refreshable { await Market.shared.pullRefresh(m) }
    }
    /// 받아 둔 사이트 값을 바로 빼거나 다시 읽고, 내 키 시세를 새로 받는다
    private func setYahooOff(_ v: Bool) {
        Store.defaults.set(v, forKey: "yahoo_off")
        let mk = Market.shared
        mk.site = QuoteFeed.cached()
        mk.prices = AlpacaHistory.merged(TossHistory.merged(SitePrices.cached(), TossHistory.cached()))
        Task { await mk.pullRefresh(m) }
    }
    /// 키 멈춤을 바꾸고 받아 둔 내 키 시세를 다시 읽는다 (멈추면 비고, 풀면 돌아온다)
    private func setPause(_ broker: String, _ v: Bool) {
        Store.defaults.set(v, forKey: "\(broker)_pause")
        let mk = Market.shared
        mk.toss = TossFeed.cached()
        mk.alpaca = AlpacaFeed.cached()
        mk.longbridge = LongbridgeFeed.cached()
        mk.prices = AlpacaHistory.merged(TossHistory.merged(SitePrices.cached(), TossHistory.cached()))
        FlowStore.shared.reload()
        Task { await mk.pullRefresh(m) }
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
    @State private var ip: String? = Config.tossRelayIP   // 고정 IP 를 바로 보여 준다: 받기 실패해도 빈칸 없음
    @State private var busy = false
    @State private var msg: String? = nil
    @State private var on = TossKeys.stored   // 멈춰도 키 있음 화면을 보인다
    @State private var askOff = false
    @State private var copied = false

    private var status: String {
        _ = Market.shared.tossDone      // 토스 받기를 마치면 다시 그린다
        let t = TossFeed.okAt
        var s = ""
        if t > 0 {
            let f = DateFormatter(); f.locale = AppModel.dateLocale
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm:ss" : String(localized: "M월 d일 a h:mm")
            s = String(localized: "마지막으로 받은 때 \(String(describing: f.string(from: d)))")
        } else { s = String(localized: "아직 받은 적 없어요") }
        if let e = TossFeed.lastError { s += String(localized: " · 실패: \(String(describing: e))") }
        return s
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("내 Toss API 키").appFont(22, .bold)
                TossLinkMap(on: on)
                if on { onCard } else { steps }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .task { if let v = await TossKeys.relayIP() { ip = v } }
    }

    private var onCard: some View {
        Card {
            Text("내 키로 받는 중").appFont(15, .bold)
            Text(status).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            if let ip { ipRow(ip) }
            PrimaryButton(title: busy ? String(localized: "받는 중…") : String(localized: "지금 다시 받기")) { Task { await check() } }
            if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
            Button("키 지우기") { askOff = true }
                .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                .confirmationDialog("오픈 API 키를 이 폰에서 지울까요?", isPresented: $askOff, titleVisibility: .visible) {
                    Button("지우기", role: .destructive) { TossKeys.clear(); on = false; msg = nil; Task { await Market.shared.pullRefresh(m) } }
                } message: { Text("기본 시세로 돌아가요. 증권사에서 받은 키는 그 증권사에서 따로 지울 수 있어요.") }
        }
    }

    private var steps: some View {
        Card {
            // 안내는 세 줄만 (토스 키 발급 절차는 설명하지 않는다)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: "info.circle.fill").foregroundStyle(Theme.teal)
                Text("투자자가 본인의 API Key를 통해 본인 정보를 호출하는 구조예요.").fixedSize(horizontal: false, vertical: true)
            }
            .appFont(15, .bold)
            Text("1. 허용 IP에 서버 중계 IP를 넣어 주세요.").appFont(14, .semibold).padding(.top, 6).fixedSize(horizontal: false, vertical: true)
            if let ip { ipRow(ip) } else { Text("중계 IP를 불러오는 중이에요…").appFont(13).foregroundStyle(Theme.muted) }
            Text("2. 클라이언트 키를 넣고 서버와 연결하세요.").appFont(14, .semibold).padding(.top, 6).fixedSize(horizontal: false, vertical: true)
            field("client_id", $id, secure: true)
            field("client_secret", $secret, secure: true)
            let ok = !id.trimmingCharacters(in: .whitespaces).isEmpty && !secret.trimmingCharacters(in: .whitespaces).isEmpty
            PrimaryButton(title: busy ? String(localized: "확인 중…") : String(localized: "연결하기"), color: ok ? Theme.teal : Theme.muted) {
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
            Button(copied ? String(localized: "복사됨") : String(localized: "복사")) { UIPasteboard.general.string = ip; copied = true }
                .appFont(14, .semibold).foregroundStyle(Theme.teal).frame(minHeight: 44)
        }
        .padding(.horizontal, 12).background(Theme.track, in: RoundedRectangle(cornerRadius: 12))
    }

    private func field(_ title: String, _ v: Binding<String>, secure: Bool) -> some View {
        Group {
            if secure { SecureField(title, text: v) } else { TextField(title, text: v) }
        }
        .textContentType(nil).autocorrectionDisabled().textInputAutocapitalization(.never)
        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
    }

    /// 토큰을 새로 받아 키·허용 IP 를 확인하고, 되면 시세를 바로 받는다
    private func check() async {
        busy = true; msg = nil
        // 토큰은 만료 전이면 그대로 쓴다 (새로 받으면 받는 중인 다른 요청의 토큰이 끊긴다). 처음 연결이면 키를 넣을 때 토큰을 지워 새로 받는다
        let (t, e) = await TossFeed.token()
        if t == nil {
            msg = (e ?? String(localized: "연결 실패")) + (e == String(localized: "허용 IP에 중계 IP가 없어요") ? String(localized: ". 오픈 API 허용 IP에 위 IP를 넣고 다시 눌러 주세요.") : "")
            if !on { TossKeys.clear() }     // 처음 연결이 실패하면 키를 남기지 않는다
        } else {
            on = true
            await Market.shared.refresh(held: Set(m.holdings.map(\.symbol)), force: true)
        }
        busy = false
    }
}

// 내 Alpaca 키 (미국 종목만): app.alpaca.markets 모의 계정에서 무료로 받은 키를 넣으면 미국 종목 지금 가격을 Alpaca(IEX)에서 받는다.
// 키는 이 폰 키체인에만 두고 Alpaca 를 직접 부른다 (중계 없음). 토스 키가 있으면 토스 값이 먼저다.
struct AlpacaKeyView: View {
    @Environment(AppModel.self) private var m
    @State private var id = ""
    @State private var secret = ""
    @State private var busy = false
    @State private var msg: String? = nil
    @State private var on = AlpacaKeys.stored   // 멈춰도 키 있음 화면을 보인다
    @State private var askOff = false

    private var status: String {
        _ = Market.shared.alpacaDone      // Alpaca 받기를 마치면 다시 그린다
        let t = AlpacaFeed.okAt
        var s = ""
        if t > 0 {
            let f = DateFormatter(); f.locale = AppModel.dateLocale
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm:ss" : String(localized: "M월 d일 a h:mm")
            s = String(localized: "마지막으로 받은 때 \(String(describing: f.string(from: d)))")
        } else { s = String(localized: "아직 받은 적 없어요") }
        if let e = AlpacaFeed.lastError { s += String(localized: " · 실패: \(String(describing: e))") }
        return s
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("내 Alpaca API 키").appFont(22, .bold)
                TossLinkMap(on: on, server: false, broker: "Alpaca", what: String(localized: "미국 종목의 현재가를 받아 와요."))
                Card {
                    if on {
                        Text("내 키로 받는 중").appFont(15, .bold)
                        Text(status).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        PrimaryButton(title: busy ? String(localized: "받는 중…") : String(localized: "지금 다시 받기")) { Task { await check() } }
                        if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
                        Button("키 지우기") { askOff = true }
                            .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                            .confirmationDialog("Alpaca 키를 이 폰에서 지울까요?", isPresented: $askOff, titleVisibility: .visible) {
                                Button("지우기", role: .destructive) { AlpacaKeys.clear(); on = false; msg = nil; Task { await Market.shared.pullRefresh(m) } }
                            } message: { Text("기본 시세로 돌아가요.") }
                    } else {
                        Text("미국 종목만 Alpaca에서 지금 가격을 받아요. 키는 이 폰에만 둬요.").appFont(15, .bold).fixedSize(horizontal: false, vertical: true)
                        Text("app.alpaca.markets 모의(Paper) 계정에서 무료로 받을 수 있어요. 무료 키는 IEX 거래소 실시간 시세예요.")
                            .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        field("API Key ID", $id)
                        field("Secret Key", $secret)
                        let ok = !id.trimmingCharacters(in: .whitespaces).isEmpty && !secret.trimmingCharacters(in: .whitespaces).isEmpty
                        PrimaryButton(title: busy ? String(localized: "확인 중…") : String(localized: "연결하기"), color: ok ? Theme.teal : Theme.muted) {
                            guard ok, !busy else { return }
                            AlpacaKeys.save(.init(id: id.trimmingCharacters(in: .whitespaces), secret: secret.trimmingCharacters(in: .whitespaces)))
                            secret = ""
                            Task { await check() }
                        }
                        if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }

    private func field(_ title: String, _ v: Binding<String>) -> some View {
        SecureField(title, text: v)
            .textContentType(nil).autocorrectionDisabled().textInputAutocapitalization(.never)
            .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
    }

    /// 키를 확인하고, 되면 시세를 바로 받는다. 처음 연결이 실패하면 키를 남기지 않는다
    private func check() async {
        busy = true; msg = nil
        if let e = await AlpacaFeed.check() {
            msg = e
            if !on { AlpacaKeys.clear() }
        } else {
            on = true
            await Market.shared.refresh(held: Set(m.holdings.map(\.symbol)), force: true)
        }
        busy = false
    }
}

// 내 Longbridge 키: open.longbridge.com 개발자 센터에서 받은 App Key·App Secret·Access Token 을 넣으면 지금 가격을 Longbridge 에서 받는다.
// 앱은 아직 원화·달러 종목만 다루므로 지금은 미국 종목만. 키는 이 폰 키체인에만 두고 Longbridge 를 직접 부른다 (중계 없음). 토스·Alpaca 키가 있으면 그 값이 먼저다.
struct LongbridgeKeyView: View {
    @Environment(AppModel.self) private var m
    @State private var key = ""
    @State private var secret = ""
    @State private var token = ""
    @State private var busy = false
    @State private var msg: String? = nil
    @State private var on = LongbridgeKeys.stored   // 멈춰도 키 있음 화면을 보인다
    @State private var askOff = false

    private var status: String {
        _ = Market.shared.longbridgeDone      // Longbridge 받기를 마치면 다시 그린다
        let t = LongbridgeFeed.okAt
        var s = ""
        if t > 0 {
            let f = DateFormatter(); f.locale = AppModel.dateLocale
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm:ss" : String(localized: "M월 d일 a h:mm")
            s = String(localized: "마지막으로 받은 때 \(String(describing: f.string(from: d)))")
        } else { s = String(localized: "아직 받은 적 없어요") }
        if let e = LongbridgeFeed.lastError { s += String(localized: " · 실패: \(String(describing: e))") }
        return s
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("내 Longbridge API 키").appFont(22, .bold)
                TossLinkMap(on: on, server: false, broker: "Longbridge", what: String(localized: "미국 종목의 현재가를 받아 와요."))
                Card {
                    if on {
                        Text("내 키로 받는 중").appFont(15, .bold)
                        Text(status).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        PrimaryButton(title: busy ? String(localized: "받는 중…") : String(localized: "지금 다시 받기")) { Task { await check() } }
                        if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
                        Button("키 지우기") { askOff = true }
                            .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                            .confirmationDialog("Longbridge 키를 이 폰에서 지울까요?", isPresented: $askOff, titleVisibility: .visible) {
                                Button("지우기", role: .destructive) { LongbridgeKeys.clear(); on = false; msg = nil; Task { await Market.shared.pullRefresh(m) } }
                            } message: { Text("기본 시세로 돌아가요.") }
                    } else {
                        Text("미국 종목 지금 가격을 Longbridge에서 받아요. 키는 이 폰에만 둬요.").appFont(15, .bold).fixedSize(horizontal: false, vertical: true)
                        Text("Longbridge 앱에서 모의 계정을 만든 뒤 open.longbridge.com 개발자 센터에서 무료로 받아요. Access Token은 기한이 있어서 지나면 새로 넣어야 해요. 홍콩·중국 종목은 앱이 그 종목을 다루게 되면 같은 키로 받아요.")
                            .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        field("App Key", $key)
                        field("App Secret", $secret)
                        field("Access Token", $token)
                        let t = { (v: String) in v.trimmingCharacters(in: .whitespacesAndNewlines) }
                        let ok = !t(key).isEmpty && !t(secret).isEmpty && !t(token).isEmpty
                        PrimaryButton(title: busy ? String(localized: "확인 중…") : String(localized: "연결하기"), color: ok ? Theme.teal : Theme.muted) {
                            guard ok, !busy else { return }
                            LongbridgeKeys.save(.init(key: t(key), secret: t(secret), token: t(token)))
                            secret = ""; token = ""
                            Task { await check() }
                        }
                        if let msg { Text(msg).appFont(13).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }

    private func field(_ title: String, _ v: Binding<String>) -> some View {
        SecureField(title, text: v)
            .textContentType(nil).autocorrectionDisabled().textInputAutocapitalization(.never)
            .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
    }

    /// 키를 확인하고, 되면 시세를 바로 받는다. 처음 연결이 실패하면 키를 남기지 않는다
    private func check() async {
        busy = true; msg = nil
        if let e = await LongbridgeFeed.check() {
            msg = e
            if !on { LongbridgeKeys.clear() }
        } else {
            on = true
            await Market.shared.refresh(held: Set(m.holdings.map(\.symbol)), force: true)
        }
        busy = false
    }
}

/// 오픈 API 맨 위 그림: 내 폰 · naeilo 서버 · 토스 시세. 키를 넣기 전에는 끊겨 있고, 넣으면 이어진다
/// (키는 이 폰 키체인에만 두고, 중계 서버는 저장·기록 없이 넘기기만 한다. docs/toss-relay.md)
/// 내 키 화면 맨 위 그림: 내 폰 → (토스만) naeilo 서버 → 증권사. 안심은 말보다 그림으로 보인다
struct TossLinkMap: View {
    let on: Bool
    var server = true                                   // Alpaca·Longbridge 는 폰에서 바로 받는다
    var broker = String(localized: "Toss증권")
    var what = String(localized: "내 종목의 현재가·일봉·환율을 받아 와요.")

    var body: some View {
        Card {
            HStack(alignment: .top, spacing: 0) {
                node("iphone", String(localized: "내 폰"), String(localized: "키가 있는 곳"), lock: true)
                TossLinkLine(on: on)
                if server {
                    node("server.rack", String(localized: "naeilo 서버"), String(localized: "이어 주는 곳"), lock: false)
                    TossLinkLine(on: on)
                }
                node("chart.line.uptrend.xyaxis", broker, String(localized: "시세"), lock: false)
            }
            Text(on ? String(localized: "이어졌어요") : String(localized: "키를 넣으면 이어져요")).appFont(13, .semibold)
                .foregroundStyle(on ? Theme.teal : Theme.muted).frame(maxWidth: .infinity)
            HStack(spacing: 10) {
                Image(systemName: on ? "chart.line.uptrend.xyaxis" : "key.fill").font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.teal)
                Text(what).appFont(13).fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12).padding(.vertical, 10).background(Theme.track, in: RoundedRectangle(cornerRadius: 12))
        }
        .animation(.easeInOut(duration: 0.4), value: on)
    }

    private func node(_ icon: String, _ name: String, _ note: String, lock: Bool) -> some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 16).fill(on ? Theme.teal : Theme.track)
                .frame(width: 52, height: 52)
                .overlay { Image(systemName: icon).font(.system(size: 22, weight: .semibold)).foregroundStyle(on ? .white : Theme.muted) }
                .overlay(alignment: .bottomTrailing) {
                    if lock {
                        Image(systemName: "lock.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(on ? Theme.teal : Theme.muted)
                            .frame(width: 22, height: 22).background(Theme.card, in: Circle())
                            .overlay(Circle().stroke(on ? Theme.teal : Theme.muted, lineWidth: 2)).offset(x: 6, y: 6)
                    }
                }
            Text(name).appFont(12, .bold).multilineTextAlignment(.center)
            Text(note).appFont(11, lock ? .semibold : .regular).foregroundStyle(lock && on ? Theme.teal : Theme.muted).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}

/// 두 칸 사이 줄: 끊김은 점선 + 빨간 끊긴 표시, 이어짐은 청록 실선 위로 점이 오른쪽에서 왼쪽(내 폰 쪽)으로 흐른다
private struct TossLinkLine: View {
    let on: Bool
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var flow = false

    var body: some View {
        ZStack(alignment: .leading) {
            if on {
                Rectangle().fill(Theme.teal).frame(height: 2)
                Circle().fill(Theme.teal).frame(width: 8, height: 8)
                    .offset(x: still ? 11 : (flow ? 0 : 22))   // 시세가 토스에서 내 폰 쪽으로 온다
                    .onAppear { if !still { withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) { flow = true } } }
                    .onDisappear { flow = false }
            } else {
                Line().stroke(Theme.muted.opacity(0.7), style: StrokeStyle(lineWidth: 2, dash: [4, 3])).frame(height: 2)
                Rectangle().fill(Theme.up.opacity(0.9)).frame(width: 2, height: 16).rotationEffect(.degrees(25)).offset(x: 14)
            }
        }
        .frame(width: 30, height: 52)
        .accessibilityHidden(true)
    }

    private struct Line: Shape {
        func path(in r: CGRect) -> Path { Path { p in p.move(to: CGPoint(x: 0, y: r.midY)); p.addLine(to: CGPoint(x: r.maxX, y: r.midY)) } }
    }
}

// 앱 아이콘 바꾸기 (2026-10-24): 내일의 별 + 상단 박스 테마 7개. 모양은 내일의 별 그대로, 색만 테마 장면에서 뽑았다
// 라이트·다크 그림이 따로 있고 틴트는 내일의 별 것을 같이 쓴다 (틴트·투명은 아이폰이 색을 지워 어차피 같아 보인다)
// 테마 따라가기를 켜면 홈 평가액 박스 테마를 바꿀 때 아이콘도 그 테마 것으로 바뀐다 (IconFollow)
struct AppIconPicker: View {
    static let icons: [(id: String?, name: String, prev: String)] = {
        var a: [(id: String?, name: String, prev: String)] = [(nil, String(localized: "내일의 별"), "iconprev_star")]
        for l in BoxLook.allCases { a.append((AppIconPicker.id(for: l), l.name, "iconprev_\(l.rawValue)")) }
        return a
    }()
    static let linkKey = "appIconLinked"
    static func id(for look: BoxLook) -> String { "AppIcon-\(look.rawValue)" }
    static var currentName: String {
        let name = icons.first { $0.id == UIApplication.shared.alternateIconName }?.name ?? String(localized: "내일의 별")
        return UserDefaults.standard.bool(forKey: linkKey) ? String(localized: "테마 따라 · \(String(describing: name))") : name
    }
    /// 아이콘을 바꾼다. 같은 아이콘이면 아이폰 알림 창이 뜨지 않게 그냥 둔다
    static func apply(_ id: String?, done: ((String?) -> Void)? = nil) {
        guard UIApplication.shared.supportsAlternateIcons, UIApplication.shared.alternateIconName != id else { return }
        UIApplication.shared.setAlternateIconName(id) { err in if err == nil { done?(id) } }
    }

    @AppStorage(AppIconPicker.linkKey) private var linked = false
    @AppStorage(BoxLook.key) private var boxLook = BoxLook.jump.rawValue
    @State private var current: String? = UIApplication.shared.alternateIconName

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ChipRow(items: [(true, String(localized: "테마 따라가기")), (false, String(localized: "직접 고르기"))],
                    selection: Binding(get: { linked }, set: { on in
                        linked = on
                        if on { Self.apply(Self.id(for: BoxLook(rawValue: boxLook) ?? .jump)) { current = $0 } }
                    }), fill: true)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 12) {
                ForEach(Self.icons, id: \.name) { ic in
                    let on = current == ic.id
                    Button { Self.apply(ic.id) { current = $0 } } label: {
                        VStack(spacing: 6) {
                            Image(ic.prev).resizable().interpolation(.high).scaledToFit()
                                .frame(width: 64, height: 64)
                                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(on ? Theme.teal : Theme.border, lineWidth: on ? 3 : 1))
                            Text(ic.name).appFont(13, on ? .bold : .regular)
                                .foregroundStyle(on ? Theme.ink : Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 8).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(linked)
                    .opacity(linked && !on ? 0.45 : 1)
                    .accessibilityLabel(String(localized: "\(String(describing: ic.name)) 아이콘"))
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(8)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        }
        .onAppear { current = UIApplication.shared.alternateIconName }
    }
}

/// 테마 따라가기: 홈 박스를 길게 눌러 고르든 설정에서 고르든 테마가 바뀌면 아이콘도 따라 바꾼다
struct IconFollow: ViewModifier {
    @AppStorage(AppIconPicker.linkKey) private var linked = false
    @AppStorage(BoxLook.key) private var boxLook = BoxLook.jump.rawValue
    func body(content: Content) -> some View {
        content
            .onChange(of: boxLook) { _, raw in
                if linked, let look = BoxLook(rawValue: raw) { AppIconPicker.apply(AppIconPicker.id(for: look)) }
            }
            // 예전 인물 아이콘(세리·시오·선배·이르·수아)을 쓰던 사람은 기본 아이콘으로 돌린다
            .onAppear {
                if let alt = UIApplication.shared.alternateIconName, !AppIconPicker.icons.contains(where: { $0.id == alt }) { AppIconPicker.apply(nil) }
            }
    }
}

/// 설정 > 앱 아이콘 › 고르는 화면 (설정 첫 화면에서 격자를 뺐다)
struct AppIconScreen: View {
    @AppStorage(AppIconPicker.linkKey) private var linked = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("앱 아이콘").appFont(22, .bold)
                Text(linked ? String(localized: "테마를 바꾸면 아이콘도 함께 바뀌어요.") : String(localized: "테마와 상관없이 아이콘을 골라요.")).appFont(13).foregroundStyle(Theme.sub)
                AppIconPicker()
            }.padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }
}
