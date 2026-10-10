import Foundation
import StoreKit
import Observation

// 개발자 후원(커피) = 인앱 구매. 콘텐츠를 여는 것이라 App Store 지침 3.1.1 에 따라 인앱 결제만 쓴다 (도네이션 아님).
// 등급 4개는 비소모성(한 번 사면 영구, 구매 복원, 가족 공유 켬), '한 잔 더'는 아무것도 열지 않는 소모성 팁.
// 앱은 가진 상품 중 가장 높은 등급만 본다. 기능은 등급으로 판단하니(예: 다방 이상 광고 없음) 나중에 붙여도 산 사람에게 그대로 적용된다.
// 구매 상태는 기기 동기화(비밀번호)로 넘기지 않고, 기기마다 StoreKit 이 Apple ID 기준으로 알려 준다.

enum SupportTier: Int, Comparable, CaseIterable {
    case free = 0, mix, dabang, franchise, specialty
    static func < (a: Self, b: Self) -> Bool { a.rawValue < b.rawValue }

    static let idPrefix = "com.naeilo.widget.support."
    var productID: String { Self.idPrefix + ["", "mix", "dabang", "franchise", "specialty"][rawValue] }
    init?(productID: String) {
        guard let t = Self.allCases.first(where: { $0 != .free && $0.productID == productID }) else { return nil }
        self = t
    }

    var name: String { ["", "믹스커피", "다방커피", "프랜차이즈 커피", "고급 스페셜티 커피"][rawValue] }
    /// 이 등급에서 새로 열리는 것 (아래 등급 것은 모두 포함)
    var adds: [String] {
        switch self {
        case .free: []
        case .mix: ["외전 6장부터 코다까지 읽기 (한글·영문)"]
        case .dabang: ["모든 보상 미리 보기 (못 만난 친구·아직 안 돌아온 물건)", "나중에 광고가 생겨도 광고 없음"]
        case .franchise: ["본편 『중첩된 현실』 1권 읽기 (한국어)"]
        case .specialty: ["도움말·정보 화면에 후원자 이름(또는 로고)"]
        }
    }
}

// 등급·테스트 빌드 값은 어디서든 읽을 수 있게 클래스는 메인 액터에 묶지 않고 (AppModel.devAll 이 읽는다), 바꾸는 함수만 메인 액터에서
@Observable
final class Support {
    static let shared = Support()
    static let tipID = SupportTier.idPrefix + "tip"

    /// 가진 가장 높은 등급. 앱을 열 때 StoreKit 에서 다시 확인하고, 그 전에는 지난번 값
    private(set) var tier: SupportTier = SupportTier(rawValue: UserDefaults.standard.integer(forKey: "supportTier")) ?? SupportTier.free {
        didSet { UserDefaults.standard.set(tier.rawValue, forKey: "supportTier") }
    }
    private(set) var products: [Product] = []
    /// 가진 구매의 서명된 거래 (후원 서버가 등급을 확인한다). 가족 공유로 받은 것도 포함
    private(set) var jws: [String] = []
    private(set) var busy: String? = nil       // 구매 중인 상품
    var message: String? = nil                 // 구매 결과 한 줄
    private(set) var tips = UserDefaults.standard.integer(forKey: "supportTips") {
        didSet { UserDefaults.standard.set(tips, forKey: "supportTips") }
    }
    /// TestFlight·Xcode 빌드인지 (App Store 빌드면 false). 개발자 전체 해제는 여기서만 쓴다
    private(set) var testBuild: Bool = {
        #if DEBUG
        true
        #else
        false
        #endif
    }()

    @ObservationIgnored private var updates: Task<Void, Never>? = nil

    /// 앱을 열 때 한 번: 상품 목록, 가진 등급, 다른 기기·가족 공유·환불로 바뀐 거래 듣기
    @MainActor func start() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await r in Transaction.updates { await self?.handle(r) }
        }
        Task {
            if case .verified(let a)? = try? await AppTransaction.shared { testBuild = testBuild || a.environment != .production }
            await loadProducts()
            await refresh()
            // 끝내지 못한 팁(구매 중 앱이 꺼진 경우)
            for await r in Transaction.unfinished { await handle(r) }
        }
    }

    @MainActor func loadProducts() async {
        let ids = SupportTier.allCases.filter { $0 != .free }.map(\.productID) + [Self.tipID]
        if let p = try? await Product.products(for: ids) { products = p.sorted { $0.price < $1.price } }
    }
    /// 이 등급 이상인지. 개발자 빌드(TestFlight·Xcode)에서 개발자 동기화로 연결한 기기도 (서버가 토큰으로 확인)
    func has(_ t: SupportTier) -> Bool { tier >= t || (testBuild && Sync.shared.isDev) }
    func product(_ t: SupportTier) -> Product? { products.first { $0.id == t.productID } }
    var tipProduct: Product? { products.first { $0.id == Self.tipID } }

    /// 가진 비소모성 상품 중 가장 높은 등급 (환불·취소된 것은 뺀다. 가족 공유로 받은 것도 포함)
    @MainActor func refresh() async {
        var best = SupportTier.free, signed: [String] = []
        for await r in Transaction.currentEntitlements {
            guard case .verified(let t) = r, t.revocationDate == nil, let tier = SupportTier(productID: t.productID) else { continue }
            best = max(best, tier)
            signed.append(r.jwsRepresentation)
        }
        tier = best; jws = signed
    }

    @MainActor func buy(_ p: Product) async {
        busy = p.id; defer { busy = nil }
        do {
            switch try await p.purchase() {
            case .success(let r):
                await handle(r)
                message = p.id == Self.tipID ? "커피 한 잔 고마워요." : "\(SupportTier(productID: p.id)?.name ?? "") 고마워요. 바로 열렸어요."
            case .pending: message = "승인을 기다리고 있어요. 승인되면 자동으로 열려요."
            case .userCancelled: break
            @unknown default: break
            }
        } catch {
            message = "구매하지 못했어요. 잠시 뒤 다시 해 주세요."
        }
    }

    /// 구매 복원 (지침 3.1.1): App Store 와 맞춘 뒤 다시 확인
    @MainActor func restore() async {
        busy = "restore"; defer { busy = nil }
        try? await AppStore.sync()
        await refresh()
        message = tier == .free ? "복원할 구매가 없어요." : "\(tier.name)까지 복원했어요."
    }

    @MainActor private func handle(_ r: VerificationResult<Transaction>) async {
        guard case .verified(let t) = r else { return }
        if t.productID == Self.tipID, t.revocationDate == nil { tips += 1 }
        await t.finish()
        await refresh()
    }
}
