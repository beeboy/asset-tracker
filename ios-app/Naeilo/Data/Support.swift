import Foundation
import StoreKit
import Observation
import WidgetKit
import Security

// 구독 (2026-10-16 결정, docs/decisions.md). 콘텐츠를 여는 것이라 App Store 지침 3.1.1 에 따라 인앱 결제만 쓴다.
// 처음 7일은 무료로 서재(외전 6장~·1권) 빼고 다 열린다. 7일 뒤 구독하지 않으면 위젯 전부와 목표·확률 전망만 잠근다.
// 홈 기본 숫자·분석·게임·이미 연 캐릭터 상품은 앱 안에서 계속 쓴다.
// 상품은 같은 구독 그룹의 자동 갱신 구독 두 개: 월 1,000원 · 연 9,000원. 앱 연재판 1권은 연 구독 또는 월 구독 9번(9개월) 이상 결제.
// 7일 무료는 App Store 무료 체험이 아니라 앱을 처음 연 날(서버 시각)부터 센다. 키체인에 두어 앱을 지웠다 다시 깔아도 새로 시작하지 않는다.
// 구독 상태는 기기마다 StoreKit 이 Apple ID 기준으로 알려 준다. 위젯은 App Group 에 쓴 값(Store.subOn·trialEnd)으로 판단한다.

enum SubPlan: String, CaseIterable {
    case monthly, yearly
    static let idPrefix = "com.naeilo.widget.sub."
    var productID: String { Self.idPrefix + rawValue }
    init?(productID: String) {
        guard let p = Self.allCases.first(where: { $0.productID == productID }) else { return nil }
        self = p
    }
    var name: String { self == .monthly ? String(localized: "월 구독") : String(localized: "연 구독") }
    var unit: String { self == .monthly ? String(localized: "한 달") : String(localized: "1년") }
}

// 상태 값은 어디서든 읽을 수 있게 클래스는 메인 액터에 묶지 않고 (AppModel.devAll 이 읽는다), 바꾸는 함수만 메인 액터에서
@Observable
final class Support {
    static let shared = Support()
    static let trialDays = 7
    /// 1권이 열리는 월 구독 결제 횟수
    static let vol1Months = 9

    /// 지금 쓰는 구독 (끝났거나 환불되면 nil). 앱을 열 때 StoreKit 에서 다시 확인하고, 그 전에는 지난번 값
    private(set) var plan: SubPlan? = UserDefaults.standard.string(forKey: "subPlan").flatMap(SubPlan.init(rawValue:)) {
        didSet { UserDefaults.standard.set(plan?.rawValue, forKey: "subPlan") }
    }
    /// 월 구독을 결제한 횟수 (갱신 포함, 환불 뺌). 구독을 멈춰도 줄지 않는다
    private(set) var monthsPaid = UserDefaults.standard.integer(forKey: "subMonths") {
        didSet { UserDefaults.standard.set(monthsPaid, forKey: "subMonths") }
    }
    /// 7일 무료를 시작한 시각 (서버 시각, 초). 아직 서버 시각을 못 받았으면 nil = 첫날
    private(set) var startAt: Double? = TrialKey.load()
    /// 처음 연 날을 1일째로 센 오늘 (서버 시각 기준 날짜. 시각을 모르면 지난번에 센 날, 줄지 않는다)
    private(set) var dayN = max(1, UserDefaults.standard.integer(forKey: "trialDayN")) {
        didSet { UserDefaults.standard.set(dayN, forKey: "trialDayN") }
    }
    private(set) var products: [Product] = []
    /// 서버에 보내는 서명된 거래 (지금 구독 + 1권 자격용 지난 월 구독 결제)
    private(set) var jws: [String] = []
    private(set) var busy: String? = nil       // 구매 중인 상품
    var message: String? = nil                 // 구매 결과 한 줄
    /// TestFlight·Xcode 빌드인지 (App Store 빌드면 false). 개발자 전체 해제는 여기서만 쓴다
    private(set) var testBuild: Bool = {
        #if DEBUG
        true
        #else
        false
        #endif
    }()

    @ObservationIgnored private var updates: Task<Void, Never>? = nil

    /// 개발자 빌드(TestFlight·Xcode)에서 개발자 동기화로 연결한 기기 (서버가 토큰으로 확인)
    var dev: Bool { testBuild && Sync.shared.isDev }
    var subscribed: Bool { plan != nil || dev }
    var inTrial: Bool { dayN <= Self.trialDays }
    /// 7일 무료 남은 날 (오늘 포함)
    var trialLeft: Int { max(0, Self.trialDays - dayN + 1) }
    /// 위젯 전부·목표·확률 전망: 구독 중이거나 7일 무료 동안
    var pro: Bool { subscribed || inTrial }
    /// 외전 6장부터 끝까지: 구독 중 (7일 무료에는 서재가 안 열린다)
    var sideOpen: Bool { subscribed }
    /// 앱 연재판 1권: 연 구독, 또는 월 구독 9번 이상 결제
    var vol1Open: Bool { dev || plan == .yearly || monthsPaid >= Self.vol1Months }
    func open(_ b: StoryBook) -> Bool { b == .side ? sideOpen : vol1Open }

    func product(_ p: SubPlan) -> Product? { products.first { $0.id == p.productID } }

    /// 앱을 열 때 한 번: 상품 목록, 지금 구독, 다른 기기·환불·갱신으로 바뀐 거래 듣기
    @MainActor func start() {
        guard updates == nil else { return }
        pushWidget()
        updates = Task { [weak self] in
            for await r in Transaction.updates { await self?.handle(r) }
        }
        Task {
            if case .verified(let a)? = try? await AppTransaction.shared { testBuild = testBuild || a.environment != .production }
            await loadProducts()
            await refresh()
            for await r in Transaction.unfinished { await handle(r) }
        }
    }

    /// 서버 시각을 받은 뒤: 7일 무료 시작 날을 정하고 오늘이 며칠째인지 센다
    @MainActor func tick() {
        let c = TrustedClock.shared
        guard let now = c.now, !c.skewed else { return }
        if startAt == nil { startAt = now.timeIntervalSince1970; TrialKey.save(now.timeIntervalSince1970) }
        guard let s = startAt else { return }
        let cal = Calendar.current
        let d = cal.dateComponents([.day], from: cal.startOfDay(for: Date(timeIntervalSince1970: s)), to: cal.startOfDay(for: now)).day ?? 0
        let n = max(dayN, d + 1)
        if n != dayN { dayN = n }
        pushWidget()
    }

    /// 위젯이 읽는 값: 구독 중인지, 7일 무료가 끝나는 시각
    func pushWidget() {
        var end = 0.0
        if let s = startAt {
            let cal = Calendar.current
            end = cal.date(byAdding: .day, value: Self.trialDays, to: cal.startOfDay(for: Date(timeIntervalSince1970: s)))?.timeIntervalSince1970 ?? 0
        }
        guard Store.subOn != subscribed || Store.trialEnd != end else { return }
        Store.subOn = subscribed; Store.trialEnd = end
        WidgetCenter.shared.reloadAllTimelines()
    }

    @MainActor func loadProducts() async {
        if let p = try? await Product.products(for: SubPlan.allCases.map(\.productID)) { products = p.sorted { $0.price < $1.price } }
    }

    /// 지금 구독(환불·끝난 것은 뺀다)과 월 구독 결제 횟수
    @MainActor func refresh() async {
        var best: SubPlan? = nil, signed: [String] = []
        for await r in Transaction.currentEntitlements {
            guard case .verified(let t) = r, t.revocationDate == nil, let p = SubPlan(productID: t.productID) else { continue }
            if let e = t.expirationDate, e < Date() { continue }
            if best != .yearly { best = p }
            signed.append(r.jwsRepresentation)
        }
        // 월 구독은 갱신마다 거래가 하나씩 생긴다. 서버도 1권 자격을 확인하도록 최근 9개를 같이 보낸다
        var paid: [(Date, String)] = []
        for await r in Transaction.all {
            guard case .verified(let t) = r, t.revocationDate == nil, t.productID == SubPlan.monthly.productID else { continue }
            paid.append((t.purchaseDate, r.jwsRepresentation))
        }
        let recent = paid.sorted { $0.0 > $1.0 }.prefix(Self.vol1Months).map(\.1)
        plan = best
        monthsPaid = max(monthsPaid, paid.count)
        jws = signed + recent.filter { !signed.contains($0) }
        pushWidget()
    }

    @MainActor func buy(_ p: Product) async {
        busy = p.id; defer { busy = nil }
        do {
            switch try await p.purchase() {
            case .success(let r):
                await handle(r)
                message = String(localized: "\(String(describing: SubPlan(productID: p.id)?.name ?? String(localized: "구독"))) 고마워요. 바로 열렸어요.")
            case .pending: message = String(localized: "승인을 기다리고 있어요. 승인되면 자동으로 열려요.")
            case .userCancelled: break
            @unknown default: break
            }
        } catch {
            message = String(localized: "구독하지 못했어요. 잠시 뒤 다시 해 주세요.")
        }
    }

    /// 구매 복원 (지침 3.1.1): App Store 와 맞춘 뒤 다시 확인
    @MainActor func restore() async {
        busy = "restore"; defer { busy = nil }
        try? await AppStore.sync()
        await refresh()
        message = plan.map { String(localized: "\(String(describing: $0.name))을 복원했어요.") } ?? String(localized: "복원할 구독이 없어요.")
    }

    @MainActor private func handle(_ r: VerificationResult<Transaction>) async {
        guard case .verified(let t) = r else { return }
        await t.finish()
        await refresh()
    }
}

/// 7일 무료 시작 시각. 키체인은 앱을 지워도 남아서, 다시 깔아도 7일이 새로 시작하지 않는다
private enum TrialKey {
    private static let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.naeilo.trial", kSecAttrAccount as String: "start"]
    static func save(_ t: Double) {
        SecItemDelete(q as CFDictionary)
        var a = q
        a[kSecValueData as String] = Data(String(t).utf8)
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(a as CFDictionary, nil)
    }
    static func load() -> Double? {
        var a = q
        a[kSecReturnData as String] = true
        a[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(a as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return Double(String(decoding: d, as: UTF8.self))
    }
}
