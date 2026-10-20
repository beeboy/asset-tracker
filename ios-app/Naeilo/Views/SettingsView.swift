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
        .navigationBarTitleDisplayMode(.inline)
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

enum SettingsRoute: Hashable { case alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence }

struct SettingsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let onN = m.alerts.values.filter { $0 }.count
        // 위에서 아래로: 오픈 API 카드 → 내 목표 → 알림과 위젯 → 함께할 인물 → 화면 → 계산 기준 → 도움과 기타
        let routeLine = m.route == .recover ? String(localized: "본전부터 되찾는 중이에요") : m.route == .plus ? String(localized: "목표를 향해 가는 중이에요") : String(localized: "아직 첫 단계예요")
        let goalRows: [(String, String, SettingsRoute)] = [
            (String(localized: "목표 고치기"), m.isGoal ? String(localized: "\(String(describing: m.goalToText))까지 \(String(describing: AppModel.wonK(m.gK)))") : String(localized: "아직 목표가 없어요"), .goal),
            (String(localized: "루트 바꾸기"), routeLine, .route),
        ]
        let oftenRows: [(String, String, SettingsRoute)] = [
            (String(localized: "알림"), onN > 0 ? String(localized: "\(String(describing: onN))개 켜짐") : String(localized: "모두 꺼짐"), .alerts),
            (String(localized: "홈 화면 위젯"), String(localized: "\(String(describing: m.baseWidgets.count + Shelter.friends.count))가지 · 미리보기"), .widgets),
        ]
        // 증권사는 나라와 상관없이 누구나 연결: 거주 국가에 맞는 것만 위로 올린다
        let kr = m.residence == "KR"
        let lbFirst = ["HK", "CN", "SG", "MO", "TW", "MY"].contains(m.residence)
        let brokerRows: [(String, String, SettingsRoute)] = [
            (String(localized: "내 Alpaca 키"), AlpacaKeys.on ? String(localized: "미국 종목을 Alpaca(IEX) 시세로 받는 중") : String(localized: "넣지 않음 · 미국 종목 실시간 시세"), .alpaca),
            (String(localized: "내 Longbridge 키"), LongbridgeKeys.on ? String(localized: "미국 종목을 Longbridge 시세로 받는 중") : String(localized: "넣지 않음 · 미국·홍콩·중국 실시간 시세"), .longbridge),
        ]
        let refRows: [(String, String, SettingsRoute)] = [
            (String(localized: "시세 기준"), String(localized: "종목·환율 시세를 어디서 받는지"), .price),
            (String(localized: "거주 국가"), String(localized: "\(m.residenceName) · 세금 계산만 이 나라를 따라요"), .residence),
            (String(localized: "세금"), kr ? String(localized: "한국 거주자 기준으로 계산해요") : String(localized: "이 나라 규칙은 준비 중 · 지금은 한국 기준"), .tax),
        ] + (lbFirst ? brokerRows.reversed() : brokerRows)
        // 토스 키 줄은 맨 위 오픈 API 카드로 옮겼다 (동기화 코드는 남김)
        let helpRows: [(String, String, SettingsRoute)] = [
            (String(localized: "처음 쓰는 법"), String(localized: "매일 1분 · 목표 칸 · 주간 예보"), .howto),
            (String(localized: "구독"), Support.shared.plan.map { String(localized: "\(String(describing: $0.name)) 중 · 고마워요") } ?? (Support.shared.inTrial ? String(localized: "7일 무료 · \(String(describing: Support.shared.trialLeft))일 남음") : String(localized: "월 1,000원 · 연 9,000원")), .support),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader(tab: String(localized: "설정")).padding(.horizontal, -16)
                if kr { apiCard }
                group(String(localized: "내 목표"), goalRows)
                group(String(localized: "알림과 위젯"), oftenRows)
                head(String(localized: "함께할 인물"))
                GuidePickRow()
                head(String(localized: "화면"))
                Text("밝기 모드").appFont(13, .bold).foregroundStyle(Theme.sub)
                ChipRow(items: [("system", String(localized: "기기 설정대로")), ("light", String(localized: "밝게")), ("dark", String(localized: "어둡게"))],
                        selection: Binding(get: { m.appearance }, set: { m.appearance = $0; UserDefaults.standard.set($0, forKey: "appearance") }), fill: true)
                group(nil, [(String(localized: "앱 아이콘"), AppIconPicker.currentName, .icon)])
                // 앱 언어는 iOS 설정의 앱 화면에서 바꾼다 (누르면 그 화면이 열린다)
                Button {
                    if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
                } label: {
                    HStack {
                        Text("언어 변경").appFont(15, .bold)
                        Spacer()
                        Image(systemName: "arrow.up.right").appFont(13).foregroundStyle(Theme.muted)
                    }
                    .padding(.horizontal, 16).frame(minHeight: 52).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
                WaveFeedbackToggles()
                group(String(localized: "숫자를 계산하는 기준"), refRows)
                if !kr { apiCard }
                group(String(localized: "도움과 기타"), helpRows)
                // 시연 상태로 되돌리기는 내 종목을 샘플로 덮어쓴다: 개발자 기기(테스트 빌드)에서만
                if m.devAll {
                    Text("시연 상태 (개발자)").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
                    HStack(spacing: 8) {
                        demoButton(String(localized: "첫 질문부터")) { m.onboarded = false; UserDefaults.standard.set(false, forKey: "onboarded") }
                        demoButton(String(localized: "미션 1부터")) { m.resetDemo(.fresh) }
                        demoButton(String(localized: "인터미션 1주차로")) { m.resetDemo(.week1) }
                        demoButton(String(localized: "모든 화면 열기")) { m.resetDemo(.all) }
                    }
                    group(nil, [(String(localized: "출시 알림 신청 목록"), String(localized: "naeilo.com 앱 받기 신청"), .notifyList)])
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
            }
        }
    }

    /// 맨 위 오픈 API 카드: 연결 상태 한 줄 + 내 폰·서버·토스 작은 띠. 누르면 토스증권 오픈 API 화면으로
    private var apiCard: some View {
        _ = Market.shared.tossDone      // 토스 받기를 마치면 다시 그린다
        let on = TossKeys.on
        return NavigationLink(value: SettingsRoute.toss) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 4) {
                    Text(on ? String(localized: "내 오픈 API 키로 시세 받는 중") : String(localized: "내 오픈 API 키 연결 안 됨")).appFont(15, .semibold)
                    if on { Image(systemName: "chevron.right").appFont(12) }
                }
                HStack(spacing: 5) {
                    miniNode("iphone", on); miniLine(on)
                    miniNode("server.rack", on); miniLine(on)
                    miniNode("chart.line.uptrend.xyaxis", on)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(on ? String(localized: "이어져 있어요") : String(localized: "키는 이 폰에만 있어요")).appFont(13, .bold)
                        Text(on ? String(localized: "현재가·일봉·환율만 받아 왔어요") : String(localized: "서버는 지나가기만 해요 · 현재가·일봉·환율만"))
                            .appFont(11).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
                    }.padding(.leading, 5)
                    Spacer(minLength: 0)
                }
                .padding(10).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                if !on {
                    // 다른 버튼(PrimaryButton)과 같은 모양: 청록 바탕 + 흰 글자 (라이트·다크 공통)
                    Text("연결하기").appFont(16, .bold).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
                }
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func miniNode(_ icon: String, _ on: Bool) -> some View {
        RoundedRectangle(cornerRadius: 8).fill(on ? Theme.teal : Color.white.opacity(0.12))
            .frame(width: 30, height: 30)
            .overlay { Image(systemName: icon).font(.system(size: 13, weight: .semibold)).foregroundStyle(on ? .white : Color(hex: 0xC9D0D6)) }
            .accessibilityHidden(true)
    }

    /// 작은 띠의 줄: 끊김은 회색 점선, 이어짐은 청록 실선
    private func miniLine(_ on: Bool) -> some View {
        Path { p in p.move(to: CGPoint(x: 0, y: 1)); p.addLine(to: CGPoint(x: 12, y: 1)) }
            .stroke(on ? Theme.teal : Theme.muted, style: StrokeStyle(lineWidth: 2, dash: on ? [] : [3, 2]))
            .frame(width: 12, height: 2)
            .accessibilityHidden(true)
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
                    row(String(localized: "미국 종목"), mk.toss != nil ? String(localized: "토스 지금 가격") : mk.alpaca != nil ? String(localized: "Alpaca(IEX) 지금 가격") : mk.longbridge != nil ? String(localized: "Longbridge 지금 가격") : String(localized: "사이트가 평일 30분마다 모은 가격"))
                    row(String(localized: "한국 종목"), mk.toss != nil ? String(localized: "토스 지금 가격") : String(localized: "사이트가 모은 가격. 미국 지수를 따르는 국내 ETF는 그 미국 ETF × 원/달러로 추정해요"))
                    row(String(localized: "장 마감 뒤"), String(localized: "미국 장이 끝나면 정규장 종가로 세요 (토스 총자산과 같은 기준)"))
                    row(String(localized: "원/달러"), String(localized: "\(String(describing: Int(fx.last.rounded()).formatted()))원 · 오늘 \(String(describing: AppModel.sgn(fx.change)))"))
                    row(String(localized: "평가 환율"), mk.marRate != nil ? String(localized: "매매기준율 \(String(describing: String(format: "%.2f", mk.valFx)))원 · 웹 평가액과 같아요") : String(localized: "매매기준율을 아직 못 받아 지금 환율"))
                    row(String(localized: "오늘의 움직임"), String(localized: "전일 종가 대비 지금 평가액"))
                }
                Text("그래프와 미션은 종가로 계산해요. 아래로 당기면 바로 다시 받아요.")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
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
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm:ss" : String(localized: "M월 d일 a h:mm")
            s = String(localized: "마지막으로 받은 때 \(String(describing: f.string(from: d)))")
        } else { s = String(localized: "아직 받은 적 없어요") }
        if let e = TossFeed.lastError { s += String(localized: " · 실패: \(String(describing: e))") }
        return s
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("오픈 API").appFont(22, .bold)
                TossLinkMap(on: on)
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
            msg = (e ?? String(localized: "연결 실패")) + (e == "허용 IP에 중계 IP가 없어요" ? String(localized: ". 오픈 API 허용 IP에 위 IP를 넣고 다시 눌러 주세요.") : "")
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
    @State private var on = AlpacaKeys.on
    @State private var askOff = false

    private var status: String {
        _ = Market.shared.alpacaDone      // Alpaca 받기를 마치면 다시 그린다
        let t = AlpacaFeed.okAt
        var s = ""
        if t > 0 {
            let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR")
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
                Text("내 Alpaca 키").appFont(22, .bold)
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
        .navigationBarTitleDisplayMode(.inline)
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
    @State private var on = LongbridgeKeys.on
    @State private var askOff = false

    private var status: String {
        _ = Market.shared.longbridgeDone      // Longbridge 받기를 마치면 다시 그린다
        let t = LongbridgeFeed.okAt
        var s = ""
        if t > 0 {
            let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR")
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
                Text("내 Longbridge 키").appFont(22, .bold)
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
        .navigationBarTitleDisplayMode(.inline)
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
struct TossLinkMap: View {
    let on: Bool

    var body: some View {
        Card {
            HStack(alignment: .top, spacing: 0) {
                node("iphone", String(localized: "내 폰"), String(localized: "열쇠는 나만"), lock: true)
                TossLinkLine(on: on)
                node("server.rack", String(localized: "naeilo 서버"), String(localized: "통로만 해요"), lock: false)
                TossLinkLine(on: on)
                node("chart.line.uptrend.xyaxis", String(localized: "증권사 시세"), " ", lock: false)
            }
            Text(on ? String(localized: "이어졌어요") : String(localized: "키를 넣으면 이어져요")).appFont(13, .semibold)
                .foregroundStyle(on ? Theme.teal : Theme.muted).frame(maxWidth: .infinity)
            Text(on ? String(localized: "지금 이어져 있어요.") : String(localized: "어디에도 남지 않아요.")).appFont(17, .heavy).padding(.top, 4)
            Text(on ? String(localized: "그만두려면 아래 키 지우기를 누르면 돼요.") : String(localized: "키는 암호화되어 지나가기만 해요."))
                .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                Image(systemName: on ? "chart.line.uptrend.xyaxis" : "key.fill").font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.teal)
                Text(on ? String(localized: "현재가·일봉·환율 정보만 받아 왔어요.") : String(localized: "naeilo에 추가한 주식의 현재가·일봉·환율 정보만 받아올 거예요."))
                    .appFont(13).fixedSize(horizontal: false, vertical: true)
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

// 앱 아이콘 바꾸기: 기본 A · 내일의 별 + 인물 5. 각각 다크·틴트 모양이 따로 있어 홈 화면 모드를 따라간다
// 인물 아이콘은 그 인물을 만나야 열린다 (세리는 처음부터, 나머지는 인터미션 1~4주차. 쉼터와 같은 단계)
struct AppIconPicker: View {
    @Environment(AppModel.self) private var m
    static let icons: [(id: String?, name: String, prev: String, friend: Int?)] = [
        (nil, String(localized: "내일의 별"), "iconprev_star", nil), ("AppIcon-seri", String(localized: "세리"), "iconprev_seri", 0), ("AppIcon-sio", String(localized: "시오"), "iconprev_sio", 1),
        ("AppIcon-seonbae", String(localized: "선배"), "iconprev_seonbae", 2), ("AppIcon-ir", String(localized: "이르"), "iconprev_ir", 3), ("AppIcon-sua", String(localized: "수아"), "iconprev_sua", 4),
    ]
    @State private var current: String? = UIApplication.shared.alternateIconName
    static var currentName: String { icons.first { $0.id == UIApplication.shared.alternateIconName }?.name ?? String(localized: "내일의 별") }

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
                        Text(ok ? ic.name : String(localized: "인터미션 \(String(describing: ic.friend ?? 0))주차")).appFont(ok ? 13 : 11, on ? .bold : .regular)
                            .foregroundStyle(on ? Theme.ink : ok ? Theme.sub : Theme.muted).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!ok)
                .accessibilityLabel(ok ? String(localized: "\(String(describing: ic.name)) 아이콘") : String(localized: "\(String(describing: ic.name)) 아이콘, 인터미션 \(String(describing: ic.friend ?? 0))주차에 열려요"))
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
