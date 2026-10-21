package com.naeilo.app.data

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.naeilo.app.core.Store
import com.naeilo.app.core.epochSec
import com.naeilo.app.net.Sync
import com.naeilo.widget.WidgetCenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

// 구독 (2026-10-16 결정, docs/decisions.md). 콘텐츠를 여는 것이라 스토어 인앱 결제만 쓴다 (안드로이드: Google Play 결제).
// 처음 7일은 무료로 서재(외전 6장~·1권) 빼고 다 열린다. 7일 뒤 구독하지 않으면 위젯 전부와 목표·확률 전망만 잠근다.
// 홈 기본 숫자·분석·게임·이미 연 캐릭터 상품은 앱 안에서 계속 쓴다.
// 상품은 같은 구독의 자동 갱신 두 개: 월 1,000원 · 연 9,000원. 앱 연재판 1권은 연 구독 또는 월 구독 9번(9개월) 이상 결제.
// 7일 무료는 스토어 무료 체험이 아니라 앱을 처음 연 날(서버 시각)부터 센다.
// 안드로이드: 키체인이 없어 시작 시각을 따로 둔 SharedPreferences 파일("com.naeilo.trial")에 두고 (앱 안 '기록 지우기'로 안 지워짐,
// 자동 백업(allowBackup)으로 다시 깔 때 돌아옴), 처음 정할 때 앱을 처음 깐 시각(firstInstallTime, '데이터 지우기'로 안 바뀜)과 견줘 더 이른 쪽을 쓴다.
// 구독 상태는 기기마다 Google Play 결제 라이브러리가 Google 계정 기준으로 알려 준다. 위젯은 Store.subOn·trialEnd 로 판단한다.
// Play 상품 ID 는 iOS 와 같은 글자: com.naeilo.widget.sub.monthly · com.naeilo.widget.sub.yearly (기본 요금제 monthly · yearly)

// ios: Data/Support.swift:14
enum class SubPlan {
    monthly, yearly;   // ios: Data/Support.swift:15
    val productID: String get() = idPrefix + name   // ios: Data/Support.swift:17
    /** Swift name (Kotlin enum 은 name 이 이미 있어 nameText) */
    val nameText: String get() = if (this == monthly) "월 구독" else "연 구독"   // ios: Data/Support.swift:22
    val unit: String get() = if (this == monthly) "한 달" else "1년"   // ios: Data/Support.swift:23

    companion object {
        const val idPrefix = "com.naeilo.widget.sub."   // ios: Data/Support.swift:16
        /** init?(productID:) */
        // ios: Data/Support.swift:18,19,20
        fun of(productID: String): SubPlan? { val p = entries.firstOrNull { it.productID == productID } ?: return null; return p }
    }
}

// StoreKit Product 의 이름 자리 (화면이 쓰는 id·displayPrice·price)
val ProductDetails.id: String get() = productId
/** 기본 요금제(offerId 없음)의 첫 가격 단계. 없으면 첫 혜택 */
private val ProductDetails.basePhase: ProductDetails.PricingPhase?
    get() = (subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: subscriptionOfferDetails?.firstOrNull())?.pricingPhases?.pricingPhaseList?.firstOrNull()
val ProductDetails.displayPrice: String get() = basePhase?.formattedPrice ?: ""
val ProductDetails.price: Long get() = basePhase?.priceAmountMicros ?: 0L

// 상태 값은 어디서든 읽을 수 있게 (AppModel.devAll 이 읽는다). 바꾸는 함수는 메인 스레드에서 (MainScope)
// ios: Data/Support.swift:27,28 (@Observable 자리: 상태는 mutableStateOf)
class Support private constructor() {
    companion object {
        val shared: Support by lazy { Support() }   // ios: Data/Support.swift:29
        const val trialDays = 7   // ios: Data/Support.swift:30
        /** 1권이 열리는 월 구독 결제 횟수 */
        const val vol1Months = 9   // ios: Data/Support.swift:32
    }
    private val ud get() = Defaults.prefs
    private val scope = MainScope()

    /** 지금 쓰는 구독 (끝났거나 환불되면 null). 앱을 열 때 Play 에서 다시 확인하고, 그 전에는 지난번 값 */
    // ios: Data/Support.swift:35
    private var plan0 by mutableStateOf(Defaults.prefs.getString("subPlan", null)?.let { s -> SubPlan.entries.firstOrNull { it.name == s } })
    var plan: SubPlan?
        get() = plan0
        private set(v) { plan0 = v; ud.edit().putString("subPlan", v?.name).apply() }   // ios: Data/Support.swift:36
    /** 월 구독을 결제한 횟수 (갱신 포함, 환불 뺌). 구독을 멈춰도 줄지 않는다 */
    // ios: Data/Support.swift:39
    private var months0 by mutableStateOf(Defaults.prefs.getInt("subMonths", 0))
    var monthsPaid: Int
        get() = months0
        private set(v) { months0 = v; ud.edit().putInt("subMonths", v).apply() }   // ios: Data/Support.swift:40
    /** 7일 무료를 시작한 시각 (서버 시각, 초). 아직 서버 시각을 못 받았으면 null = 첫날 */
    var startAt: Double? by mutableStateOf(TrialKey.load()); private set   // ios: Data/Support.swift:43
    /** 처음 연 날을 1일째로 센 오늘 (서버 시각 기준 날짜. 시각을 모르면 지난번에 센 날, 줄지 않는다) */
    // ios: Data/Support.swift:45
    private var day0 by mutableStateOf(max(1, Defaults.prefs.getInt("trialDayN", 0)))
    var dayN: Int
        get() = day0
        private set(v) { day0 = v; ud.edit().putInt("trialDayN", v).apply() }   // ios: Data/Support.swift:46
    var products: List<ProductDetails> by mutableStateOf(emptyList()); private set   // ios: Data/Support.swift:48
    /** 서버에 보내는 구매 증표 (iOS 서명된 거래 자리: Play 구매 토큰. 지금 구독 + 1권 자격용 월 구독) */
    var jws: List<String> by mutableStateOf(emptyList()); private set   // ios: Data/Support.swift:50
    var busy: String? by mutableStateOf(null); private set       // ios: Data/Support.swift:51 구매 중인 상품
    var message: String? by mutableStateOf(null)                 // ios: Data/Support.swift:52 구매 결과 한 줄
    /** 개발용 빌드(디버그·Play 밖에서 깐 것)인지 (Play 스토어 빌드면 false). 개발자 전체 해제는 여기서만 쓴다 */
    // ios: Data/Support.swift:54,55,56,57,58,59,60 (#if DEBUG 자리: 디버그 가능 빌드)
    var testBuild: Boolean by mutableStateOf((Defaults.ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0); private set

    // ios: Data/Support.swift:62 Transaction.updates 를 듣는 자리: Play 결제 연결(구매 바뀜 알림을 받는다)
    private var updates: BillingClient? = null
    private var waiting: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null

    /** 개발자 빌드에서 개발자 동기화로 연결한 기기 (서버가 토큰으로 확인) */
    val dev: Boolean get() = testBuild && Sync.shared.isDev   // ios: Data/Support.swift:65
    val subscribed: Boolean get() = plan != null || dev   // ios: Data/Support.swift:66
    val inTrial: Boolean get() = dayN <= trialDays   // ios: Data/Support.swift:67
    /** 7일 무료 남은 날 (오늘 포함) */
    val trialLeft: Int get() = max(0, trialDays - dayN + 1)   // ios: Data/Support.swift:69
    /** 위젯 전부·목표·확률 전망: 구독 중이거나 7일 무료 동안 */
    val pro: Boolean get() = subscribed || inTrial   // ios: Data/Support.swift:71
    /** 외전 6장부터 끝까지: 구독 중 (7일 무료에는 서재가 안 열린다) */
    val sideOpen: Boolean get() = subscribed   // ios: Data/Support.swift:73
    /** 앱 연재판 1권: 연 구독, 또는 월 구독 9번 이상 결제 */
    val vol1Open: Boolean get() = dev || plan == SubPlan.yearly || monthsPaid >= vol1Months   // ios: Data/Support.swift:75
    fun open(b: StoryBook): Boolean = if (b == StoryBook.side) sideOpen else vol1Open   // ios: Data/Support.swift:76

    fun product(p: SubPlan): ProductDetails? = products.firstOrNull { it.id == p.productID }   // ios: Data/Support.swift:78

    /** Play 결제 연결 (한 번 만들고, 끊기면 다음 호출 때 다시 잇는다) */
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(Defaults.ctx)
            .setListener { r, ps -> onPurchases(r, ps) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    private suspend fun ready(): Boolean {
        if (client.isReady) return true
        val done = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { done.complete(r.responseCode == BillingClient.BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { done.complete(false) }
        })
        return done.await()
    }

    /** 구매가 바뀜 (구매 직후·승인 대기 끝·다른 기기): 사는 중이면 buy 가 받고, 아니면 바로 처리 */
    private fun onPurchases(r: BillingResult, ps: List<Purchase>?) {
        val w = waiting
        if (w != null) { waiting = null; w.complete(r to ps); return }
        if (r.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { ps.orEmpty().forEach { handle(it) } }
    }

    /** 앱을 열 때 한 번: 상품 목록, 지금 구독, 다른 기기·환불·갱신으로 바뀐 거래 듣기 */
    // ios: Data/Support.swift:81
    fun start() {
        if (updates != null) return   // ios: Data/Support.swift:82
        pushWidget()   // ios: Data/Support.swift:83
        updates = client   // ios: Data/Support.swift:84,85 (구매 바뀜은 client 의 listener → onPurchases)
        // ios: Data/Support.swift:87
        scope.launch {
            testBuild = testBuild || !fromPlay()   // ios: Data/Support.swift:88 AppTransaction 환경 자리: Play 에서 깐 앱이 아니면 개발용
            loadProducts()   // ios: Data/Support.swift:89
            refresh()   // ios: Data/Support.swift:90
            for (r in unfinished()) handle(r)   // ios: Data/Support.swift:91
        }
    }

    /** Play 스토어에서 깐 앱인지 */
    private fun fromPlay(): Boolean = runCatching {
        val pm = Defaults.ctx.packageManager; val pkg = Defaults.ctx.packageName
        val src = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
        src == "com.android.vending"
    }.getOrDefault(false)

    /** 서버 시각을 받은 뒤: 7일 무료 시작 날을 정하고 오늘이 며칠째인지 센다 */
    // ios: Data/Support.swift:96
    fun tick() {
        val c = TrustedClock.shared   // ios: Data/Support.swift:97
        val now = c.now; if (now == null || c.skewed) return   // ios: Data/Support.swift:98
        // ios: Data/Support.swift:99 (처음 깐 시각이 더 이르면 그쪽: '데이터 지우기'로 7일이 새로 시작하지 않게)
        if (startAt == null) { val t = min(now.epochSec, installedAt() ?: now.epochSec); startAt = t; TrialKey.save(t) }
        val s = startAt ?: return   // ios: Data/Support.swift:100
        val cal = ZoneId.systemDefault()   // ios: Data/Support.swift:101
        val d = ChronoUnit.DAYS.between(Instant.ofEpochMilli((s * 1000).toLong()).atZone(cal).toLocalDate(), now.atZone(cal).toLocalDate()).toInt().coerceAtLeast(0)   // ios: Data/Support.swift:102
        val n = max(dayN, d + 1)   // ios: Data/Support.swift:103
        if (n != dayN) dayN = n   // ios: Data/Support.swift:104
        pushWidget()   // ios: Data/Support.swift:105
    }

    /** 이 기기에 처음 깐 시각 (초). 다시 깔면 바뀌지만 '데이터 지우기'로는 안 바뀐다 */
    private fun installedAt(): Double? = runCatching {
        Defaults.ctx.packageManager.getPackageInfo(Defaults.ctx.packageName, 0).firstInstallTime / 1000.0
    }.getOrNull()?.takeIf { it > 0 }

    /** 위젯이 읽는 값: 구독 중인지, 7일 무료가 끝나는 시각 */
    // ios: Data/Support.swift:109
    fun pushWidget() {
        var end = 0.0   // ios: Data/Support.swift:110
        val s = startAt   // ios: Data/Support.swift:111
        if (s != null) {
            val cal = ZoneId.systemDefault()   // ios: Data/Support.swift:112
            end = Instant.ofEpochMilli((s * 1000).toLong()).atZone(cal).toLocalDate().plusDays(trialDays.toLong()).atStartOfDay(cal).toInstant().epochSec.takeIf { it > 0 } ?: 0.0   // ios: Data/Support.swift:113
        }
        if (Store.subOn == subscribed && Store.trialEnd == end) return   // ios: Data/Support.swift:115
        Store.subOn = subscribed; Store.trialEnd = end   // ios: Data/Support.swift:116
        WidgetCenter.reloadAllTimelines()   // ios: Data/Support.swift:117
    }

    // ios: Data/Support.swift:120
    suspend fun loadProducts() {
        if (!ready()) return
        val q = QueryProductDetailsParams.newBuilder().setProductList(SubPlan.entries.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productID).setProductType(BillingClient.ProductType.SUBS).build()
        }).build()
        // ios: Data/Support.swift:121
        val r = client.queryProductDetails(q); val p = r.productDetailsList; if (r.billingResult.responseCode == BillingClient.BillingResponseCode.OK && p != null) products = p.sortedBy { it.price }
    }

    /** 지금 구독(환불·끝난 것은 뺀다)과 월 구독 결제 횟수 */
    // ios: Data/Support.swift:125
    suspend fun refresh() {
        if (!ready()) return
        val all = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (all.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        var best: SubPlan? = null; val signed = ArrayList<String>()   // ios: Data/Support.swift:126
        // Play 는 지금 살아 있는 구독만 준다 (끝난 것·환불된 것은 빠져 있다)
        // ios: Data/Support.swift:127
        for (r in all.purchasesList) {
            val p = r.products.firstNotNullOfOrNull { SubPlan.of(it) }; if (r.purchaseState != Purchase.PurchaseState.PURCHASED || p == null) continue   // ios: Data/Support.swift:128
            // ios: Data/Support.swift:129 (만료일 확인: Play 목록에 만료된 구독은 없다)
            if (best != SubPlan.yearly) best = p   // ios: Data/Support.swift:130
            signed.add(r.purchaseToken)   // ios: Data/Support.swift:131
        }
        // 월 구독은 갱신마다 한 번씩 결제된다. Play 는 지난 갱신 거래를 앱에 주지 않아, 지금 월 구독을 처음 산 날부터 지난 달 수로 센다
        // (서버는 이 토큰으로 Google Play Developer API 에서 시작일을 확인해야 한다)
        val paid = ArrayList<Pair<Long, String>>()   // ios: Data/Support.swift:134
        // ios: Data/Support.swift:135
        for (r in all.purchasesList) {
            if (r.purchaseState != Purchase.PurchaseState.PURCHASED || !r.products.contains(SubPlan.monthly.productID)) continue   // ios: Data/Support.swift:136
            val n = monthsSince(r.purchaseTime) + 1; repeat(n) { paid.add(r.purchaseTime to r.purchaseToken) }   // ios: Data/Support.swift:137
        }
        val recent = paid.sortedByDescending { it.first }.take(vol1Months).map { it.second }.distinct()   // ios: Data/Support.swift:139
        plan = best   // ios: Data/Support.swift:140
        monthsPaid = max(monthsPaid, paid.size)   // ios: Data/Support.swift:141
        jws = signed + recent.filter { !signed.contains(it) }   // ios: Data/Support.swift:142
        pushWidget()   // ios: Data/Support.swift:143
    }

    private fun monthsSince(ms: Long): Int {
        val z = ZoneId.systemDefault()
        return ChronoUnit.MONTHS.between(Instant.ofEpochMilli(ms).atZone(z), Instant.now().atZone(z)).toInt().coerceAtLeast(0)
    }

    /** 확인(acknowledge) 안 된 결제 (Transaction.unfinished 자리). 3일 안에 확인하지 않으면 Play 가 환불한다 */
    private suspend fun unfinished(): List<Purchase> {
        if (!ready()) return emptyList()
        val r = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        return r.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
    }

    /** 결제 창은 화면(Activity)이 있어야 띄울 수 있다 */
    // ios: Data/Support.swift:146
    suspend fun buy(act: Activity, p: ProductDetails) {
        busy = p.id; try {   // ios: Data/Support.swift:147
            // ios: Data/Support.swift:148,149
            if (!ready()) throw IllegalStateException()
            val offer = p.subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: p.subscriptionOfferDetails?.firstOrNull() ?: throw IllegalStateException()
            val w = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>(); waiting = w
            val start = client.launchBillingFlow(act, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p).setOfferToken(offer.offerToken).build())).build())
            if (start.responseCode != BillingClient.BillingResponseCode.OK) { waiting = null; w.complete(start to null) }
            val (r, ps) = w.await()
            val done = ps.orEmpty().filter { p.id in it.products }
            when {
                // ios: Data/Support.swift:150
                r.responseCode == BillingClient.BillingResponseCode.OK && done.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> {
                    done.forEach { handle(it) }   // ios: Data/Support.swift:151
                    // Data/Support.swift:152 (검사 예외: 보간 속 따옴표 — 다음 줄에 옮김)
                    val nm = SubPlan.of(p.id)?.nameText ?: "구독"; message = "$nm 고마워요. 바로 열렸어요."
                }
                r.responseCode == BillingClient.BillingResponseCode.OK && done.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> message = "승인을 기다리고 있어요. 승인되면 자동으로 열려요."   // ios: Data/Support.swift:153
                r.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {}   // ios: Data/Support.swift:154
                r.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()   // ios: Data/Support.swift:155 (이미 산 것: 다시 확인)
                else -> throw IllegalStateException()
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) {   // ios: Data/Support.swift:157
            message = "구독하지 못했어요. 잠시 뒤 다시 해 주세요."   // ios: Data/Support.swift:158
        } finally { busy = null; waiting = null }
    }

    /** 구매 복원: Play 와 다시 연결한 뒤 다시 확인 */
    // ios: Data/Support.swift:163
    suspend fun restore() {
        busy = "restore"; try {   // ios: Data/Support.swift:164
            ready()   // ios: Data/Support.swift:165 (AppStore.sync 자리: Play 는 구매 목록을 서버와 맞춰 준다)
            refresh()   // ios: Data/Support.swift:166
            message = plan?.let { "${it.nameText}을 복원했어요." } ?: "복원할 구독이 없어요."   // ios: Data/Support.swift:167
        } finally { busy = null }
    }

    // ios: Data/Support.swift:170
    private suspend fun handle(r: Purchase) {
        if (r.purchaseState != Purchase.PurchaseState.PURCHASED) return   // ios: Data/Support.swift:171
        if (!r.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(r.purchaseToken).build())   // ios: Data/Support.swift:172 finish 자리
        refresh()   // ios: Data/Support.swift:173
    }
}

/** 7일 무료 시작 시각. iOS 는 키체인(앱을 지워도 남음). 안드로이드는 따로 둔 SharedPreferences 파일:
 *  앱 안 '기록 지우기'로 안 지워지고, 자동 백업(allowBackup)이 켜져 있으면 다시 깔 때 돌아온다 */
// ios: Data/Support.swift:178
private object TrialKey {
    private val q = mapOf("service" to "com.naeilo.trial", "account" to "start")   // ios: Data/Support.swift:179
    private val file get() = Defaults.ctx.getSharedPreferences(q.getValue("service"), Context.MODE_PRIVATE)
    // ios: Data/Support.swift:180
    fun save(t: Double) {
        val a = file.edit().remove(q.getValue("account"))   // ios: Data/Support.swift:181,182
        a.putString(q.getValue("account"), t.toString())   // ios: Data/Support.swift:183
        // Data/Support.swift:184 (검사 예외: 키체인 접근성 — 앱 전용 파일이라 플랫폼에 없음)
        a.apply()   // ios: Data/Support.swift:185
    }
    // ios: Data/Support.swift:187
    fun load(): Double? {
        val a = file   // ios: Data/Support.swift:188
        // ios: Data/Support.swift:189,190,191,192
        val out = a.getString(q.getValue("account"), null) ?: return null
        return out.toDoubleOrNull()   // ios: Data/Support.swift:193
    }
}
