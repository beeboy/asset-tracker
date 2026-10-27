package com.naeilo.app

import com.naeilo.app.core.calc.bare
import com.naeilo.app.core.cformat
import com.naeilo.app.data.Support
import com.naeilo.app.data.Story
import com.naeilo.app.data.StoryBook
import com.naeilo.app.data.Lines
import com.naeilo.app.data.Remind
import com.naeilo.app.ui.analysis.AnalysisRoute
import com.naeilo.app.ui.settings.SettingsRoute
import com.naeilo.app.core.swFmtFrac
import com.naeilo.app.core.usdText
import com.naeilo.app.net.*
import com.naeilo.app.core.calc.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.naeilo.app.core.Config
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.WChar
import com.naeilo.app.core.tr
import com.naeilo.app.ui.Palette
import com.naeilo.app.data.Currency
import com.naeilo.app.data.DayRec
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Lot
import com.naeilo.app.data.Lots
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.MyRecord
import com.naeilo.app.data.PriceProvider
import com.naeilo.app.data.Progress
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.StubPriceProvider
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.WeekGate
import com.naeilo.app.data.apply
import com.naeilo.app.data.fixStarts
import com.naeilo.app.data.meUnlocked
import com.naeilo.app.data.persists
import com.naeilo.app.data.recordDay
import com.naeilo.app.data.short
import com.naeilo.app.data.voice
import com.naeilo.app.data.weekGate
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.Collator
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// 앱 상태 + 계산. 설명 문구는 모두 계산값으로 만든다 (종목 추천 문구 없음).
// 저장은 iOS UserDefaults 자리인 SharedPreferences("naeilo", 키 이름 같게). 읽기는 만들 때 한 번.

@Serializable
enum class WeekPick { kept, changed, raised;   // ios: Data/AppModel.swift:6
    val label: String get() = when (this) { kept -> "지킴"; changed -> "바꿈"; raised -> "높임" }   // ios: Data/AppModel.swift:7
    val sub: String get() = when (this) { kept -> "계획한 비중 그대로"; changed -> "계획 자체를 바꿈"; raised -> "몰린 종목을 다시 늘림" }   // ios: Data/AppModel.swift:8
    val color: Color get() = when (this) { kept -> Palette.Light.green; changed -> Palette.Light.orange; raised -> Color(0xFF000000.toInt() or 0xC8352E) }   // ios: Data/AppModel.swift:9
}

// ios: Data/AppModel.swift:12 q = 그날 낸 질문, ok = 퀴즈를 맞혔는지
@Serializable
data class DayLog(var seen: Boolean = false, var answer: Int? = null, var q: String? = null, var ok: Boolean? = null)

// Kotlin enum 은 name 을 경우 이름으로 못 써서 byName, 저장 값(raw)은 iOS rawValue 와 같게
enum class HoldSort(val raw: String) { added("added"), `value`("value"), byName("name"), ret("ret"), today("today");   // Data/AppModel.swift:14 (검사 예외: 경우 이름 name)
    val label: String get() = when (this) { added -> "넣은 순"; value -> "평가액 큰 순"; byName -> "이름 순"; ret -> "수익률 순"; today -> "오늘 등락 순" }   // ios: Data/AppModel.swift:15
}

@Serializable
enum class Lens { base, mine, smooth;   // ios: Data/AppModel.swift:18
    val label: String get() = when (this) { base -> "신중하게"; mine -> "내 설정"; smooth -> "지난 3년처럼" }   // ios: Data/AppModel.swift:19
}

/** Swift didSet 자리: Compose 상태로 들고, 값을 넣은 뒤 예전 값과 함께 부른다 */
private class Observed<T>(init: T, private val didSet: (T) -> Unit) : ReadWriteProperty<Any?, T> {
    private val s = mutableStateOf(init)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = s.value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { val old = s.value; s.value = value; didSet(old) }
}
private fun <T> observed(init: T, didSet: (old: T) -> Unit): ReadWriteProperty<Any?, T> = Observed(init, didSet)

/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
private fun Double.rnd(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
/** Swift Int.formatted() (천 단위 쉼표) */
private fun Int.fmt(): String = cformat("%,d", this)
/** String(format:) 은 소수점이 늘 . 이다 */
private fun fmtF(f: String, x: Double): String = cformat(f, x)
/** .number.precision(.significantDigits(lo...hi)): 유효 숫자 hi 자리로 반올림, 끝 0 은 뺀다 */
private fun sig(x: Double, digits: IntRange): String =
    BigDecimal(x).round(MathContext(digits.last, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()

class AppModel(ctx: Context, intent: Intent?) {   // ios: Data/AppModel.swift:22,23
    private val prefs: SharedPreferences = ctx.getSharedPreferences("naeilo", Context.MODE_PRIVATE)
    private val cr = ctx.contentResolver
    /** 캡처·확인용 실행 인자 (iOS 는 -key value 가 UserDefaults 로 들어온다. 안드로이드는 --es key value) */
    private val launchIntent = intent
    fun arg(key: String): String? = launchIntent?.getStringExtra(key)
    /** #if DEBUG 자리: 디버그 빌드에서만 */
    private val debug = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val prices: PriceProvider = StubPriceProvider()   // ios: Data/AppModel.swift:24
    /** 목표 몬테카를로 결과 기억 (같은 입력이면 화면을 다시 그릴 때 또 돌리지 않는다. 난수 씨앗이 고정이라 결과가 같다) */
    var simCache: MutableMap<String, GoalSim> = mutableMapOf()   // ios: Data/AppModel.swift:26
    var autoCache: MutableMap<String, Double> = mutableMapOf()   // ios: Data/AppModel.swift:27
    var depCache: MutableMap<String, Deposit> = mutableMapOf()   // ios: Data/AppModel.swift:28
    var pathSetCache: MutableMap<String, PathSet> = mutableMapOf()   // ios: Data/AppModel.swift:29
    /** 목표 자동 고르기를 경로 계산 전 닫힌 식으로 정했을 때: 경로가 나오면 같은 조건으로 다시 고른다 (그사이 목표를 고치지 않았으면) */
    data class AutoPending(val years: Int, val monthly: Double?, val from: Double?, val value: Double)
    var autoPending: AutoPending? = null   // ios: Data/AppModel.swift:31

    // 보유
    // 내 종목은 앱을 다시 켜도 남는다 (다른 시안 상태는 켤 때마다 시연 상태로 시작)
    private var _holdings by mutableStateOf(savedHoldings(prefs) ?: Sample.startHoldings)   // ios: Data/AppModel.swift:80
    var holdings: List<Holding>
        get() = _holdings
        set(v) {   // ios: Data/AppModel.swift:81
            _holdings = v
            prefs.edit().putString("holdings", NJson.encodeToString(v)).apply()   // ios: Data/AppModel.swift:82
            // 국내 주식 합계(KOREA) 줄은 없앴다: 국내 주식은 종목별로 넣는다. 예전 저장값·동기화로 들어와도 뺀다 (kr_log 기록은 그대로)
            if (v.any { it.symbol == KoreaEstimate.sumId }) _holdings = v.filter { it.symbol != KoreaEstimate.sumId }   // ios: Data/AppModel.swift:84
            recordDay()   // ios: Data/AppModel.swift:85
        }
    /** naeilo를 쓴 날부터의 내 기록 (MyRecord). 수량을 처음 넣은 날부터 하루 한 줄 */
    var myRecord: List<DayRec> by observed(MyRecord.load()) { MyRecord.save(myRecord) }   // ios: Data/AppModel.swift:89
    /** 평행 세계의 나 (Parallel). 샘플·시안 실행에서는 남기지 않는다 */
    var pworld: PWorld? by observed(PWorld.load()) { if (persists) PWorld.save(pworld) }   // ios: Data/AppModel.swift:91
    var raceCache: Pair<String, PRace?>? = null   // ios: Data/AppModel.swift:92
    var raceCacheOther: Pair<String, PRace?>? = null   // ios: Data/AppModel.swift:93 인물 자세히 보기: 다른 인물 규칙으로 굴린 경주
    /** 수량이 바뀐 날만 남기는 기록 (Lots, 사이트 lots 칸과 같은 모양). 내 길 실제 평가액을 그날 수량으로 계산한다 */
    var lots: List<Lot> by observed(Lots.load()) { Lots.save(lots) }   // ios: Data/AppModel.swift:95
    /** 종목 추가: 수량·평균 단가가 0보다 큰 숫자여야 한다 (쉼표·공백 허용). 같은 종목은 새 값으로 바꾼다 */
    fun addHolding(id: String, qty: String, avg: String): Boolean {   // ios: Data/AppModel.swift:97,98
        val num = { t: String -> t.replace(",", "").trim().toDoubleOrNull() }   // ios: Data/AppModel.swift:99
        val q = num(qty); val a = num(avg)   // ios: Data/AppModel.swift:100
        if (Sample.symbol(id) == null || q == null || q <= 0 || a == null || a <= 0) return false
        val old = if (isSample) null else holdings.firstOrNull { it.symbol == id }; val drift0 = calmDrift()   // ios: Data/AppModel.swift:101
        if (isSample) holdings = emptyList()       // ios: Data/AppModel.swift:102 처음 넣는 내 종목이면 샘플(DRNK·QQQ)은 치운다
        holdings = holdings.filter { it.symbol != id }   // ios: Data/AppModel.swift:103
        holdings = holdings + Holding(symbol = id, qty = q, avg = a)   // ios: Data/AppModel.swift:104
        // 내 투자 습관 기록: 새 종목은 '새 종목 매수', 있던 종목은 수량이 늘면 산 것·줄면 판 것
        val s = Sample.symbol(id)   // ios: Data/AppModel.swift:106
        if (s != null && old?.qty != q) {
            calmTrade(s, old?.let { q > it.qty } ?: true, old?.avg ?: a, drift0, old == null)   // ios: Data/AppModel.swift:107
        }
        return true   // ios: Data/AppModel.swift:109
    }
    /** 평정 지수 기록 (Calm). 내 종목처럼 앱을 다시 켜도 남는다 */
    var calm: Calm by observed(Calm.load()) { calm.save() }   // ios: Data/AppModel.swift:112
    /** 지금 종목이 샘플(가상 종목 DRNK + QQQ)인지. 새 사용자는 샘플로 시작하고, 화면마다 샘플이라고 알린다 */
    val isSample: Boolean get() {   // ios: Data/AppModel.swift:114
        val ids = holdings.map { it.symbol }.toSet()   // ios: Data/AppModel.swift:115
        return ids.contains("DRNK") && Sample.startHoldings.map { it.symbol }.toSet().containsAll(ids)   // ios: Data/AppModel.swift:116
    }

    // 미션 (회복 루트): 1 종목·단가, 2 원인 진단, 3 내 계획, 5 앱 시작 3단계, 6 인터미션
    // 시안 시연용 시작 상태: 미션 1~3과 앱 시작 3단계를 마치고 인터미션 1주차를 고른 뒤
    var done by mutableStateOf(setOf(1, 2, 3, 5))   // ios: Data/AppModel.swift:126
    var weeks by mutableStateOf(listOf(WeekPick.kept))   // ios: Data/AppModel.swift:127
    var weekCur by mutableStateOf<WeekPick?>(null)   // ios: Data/AppModel.swift:128

    // 오늘의 1분
    var day by mutableStateOf(5)   // ios: Data/AppModel.swift:131
    var dayLog by mutableStateOf(mapOf<Int, DayLog>())   // ios: Data/AppModel.swift:132

    // 쉼터
    var homeFriend by mutableStateOf("seri")   // ios: Data/AppModel.swift:135
    var guideTick by mutableStateOf(0)          // ios: Data/AppModel.swift:136 미션 안내 인물을 바꾸면 다시 그리게
    /** 홈·위젯에 실제로 보이는 인물: 아직 안 열린 인물이면 세리 (개발자 연결을 끊었을 때 등).
     *  내 캐릭터(me)는 열림 조건을 모두 채워야 보인다 (Me) */
    val homeFriendShown: String get() {   // ios: Data/AppModel.swift:139
        if (homeFriend == "me") return if (meUnlocked) "me" else "seri"   // ios: Data/AppModel.swift:140
        val i = Shelter.friends.indexOfFirst { it.id == homeFriend }.takeIf { it >= 0 } ?: 0   // ios: Data/AppModel.swift:141
        return if (friendOn(i)) homeFriend else "seri"   // ios: Data/AppModel.swift:142
    }
    var shelterSel by mutableStateOf<String?>(null)   // ios: Data/AppModel.swift:144
    var readPos by mutableStateOf(setOf<Int>())   // ios: Data/AppModel.swift:145
    var readLast by mutableStateOf<Int?>(null)   // ios: Data/AppModel.swift:146
    // 내 캐릭터·쉼터 배경 (Me). 날짜는 서버 시각 (1970년부터 초)
    var me: MeChar? by observed(null) { MeChar.current = me }   // ios: Data/AppModel.swift:148
    var meBg by mutableStateOf<String?>(null)   // ios: Data/AppModel.swift:149
    var interStartAt by mutableStateOf<Double?>(null)    // ios: Data/AppModel.swift:150 회복 루트 인터미션을 시작한 때
    var goalStartAt by mutableStateOf<Double?>(null)     // ios: Data/AppModel.swift:151 목표 루트를 시작한 때
    var gInterStartAt by mutableStateOf<Double?>(null)   // ios: Data/AppModel.swift:152 목표 루트 인터미션(3개월)을 시작한 때
    var meGate1At by mutableStateOf<Double?>(null)       // ios: Data/AppModel.swift:153 내 캐릭터 1차 조건을 채운 때 (한 번 채우면 루트를 바꿔도 남는다)

    // 주간 예보
    var weekGuess by mutableStateOf<String?>(null)   // ios: Data/AppModel.swift:156
    var weekFridaySkip by mutableStateOf(false)   // ios: Data/AppModel.swift:157 개발자 확인용: 금요일 마감으로 미리 넘기기
    /** 이번 주 예보가 마감됐는지: 미국 금요일 종가가 나온 뒤(한국 시각 토·일요일) */
    val weekFriday: Boolean get() {   // ios: Data/AppModel.swift:159
        if (weekFridaySkip) return true   // ios: Data/AppModel.swift:160
        val c = ZoneId.of("Asia/Seoul")   // ios: Data/AppModel.swift:161
        val wd = ZonedDateTime.now(c).dayOfWeek.value % 7 + 1   // ios: Data/AppModel.swift:162 Calendar.weekday: 1 = 일요일 … 7 = 토요일
        return wd == 7 || wd == 1   // ios: Data/AppModel.swift:163
    }
    /** 이번 주 금요일 (마감일 표시) */
    val weekFridayText: String get() {   // ios: Data/AppModel.swift:166
        val c = ZoneId.of("Asia/Seoul")   // ios: Data/AppModel.swift:167
        val now = ZonedDateTime.now(c); val wd = now.dayOfWeek.value % 7 + 1   // ios: Data/AppModel.swift:168
        val fri = now.plusDays((if (wd == 1) -2 else 6 - wd).toLong())   // ios: Data/AppModel.swift:169
        val f = DateTimeFormatter.ofPattern("M/d", dateLocale)   // ios: Data/AppModel.swift:170
        return f.format(fri)   // ios: Data/AppModel.swift:171
    }

    // 3년 전망
    /** 전망 시나리오. 신중하게 = 추세 0%, 지난 3년처럼 = 추세 100% 와 계산이 같다 (MC.blendMu) */
    var lens by mutableStateOf(Lens.mine)   // ios: Data/AppModel.swift:176
    /** '내 설정'의 추세 신뢰 % (신중하게·지난 3년처럼을 골라도 이 값은 기억해 둔다) */
    var trust by mutableStateOf(50.0)   // ios: Data/AppModel.swift:178
    var shock by mutableStateOf(true)   // ios: Data/AppModel.swift:179
    var monthly by mutableStateOf(0.0)   // ios: Data/AppModel.swift:180

    // 보유 계산 (원화)
    /** 원화 평가: 웹 평가액처럼 매매기준율 (없거나 5일 넘게 묵었으면 지금 환율) */
    fun krw(s: Symbol, x: Double): Double = if (s.currency == Currency.usd) x * Market.shared.valFx else x   // ios: Data/AppModel.swift:246
    /** 들어간 돈(원금) = 매수 단가 × 수량 × 지금 환율 (웹 본전 카드와 같다. 매수 때 환율은 모른다) */
    fun costKrw(s: Symbol, x: Double): Double = krw(s, x)   // ios: Data/AppModel.swift:248
    data class Row(val id: String, val sym: Symbol, val h: Holding, val value: Double, val cost: Double, val color: Color)   // ios: Data/AppModel.swift:249
    val rows: List<Row> get() =   // ios: Data/AppModel.swift:250
        holdings.mapNotNull { h ->   // ios: Data/AppModel.swift:251
            val s = Sample.symbol(h.symbol)
            if (h.qty > 0 && s != null) Pair(s, h) else null   // ios: Data/AppModel.swift:252,253
        }.mapIndexed { i, p ->   // ios: Data/AppModel.swift:254
            // 공개 베타·출시에서 내 키 시세가 없는 실제 종목은 평가액·들어간 돈 모두에서 뺀다 (Market.needsKey)
            if (Market.shared.needsKey(p.first)) return@mapIndexed Row(id = p.first.id, sym = p.first, h = p.second, value = 0.0, cost = 0.0, color = Palette.Light.holdColors[i % Palette.Light.holdColors.size])   // ios: Data/AppModel.swift:256
            // 국내 주식 합계는 현금처럼: 들어간 돈 = 지금 금액 (본전·수익률을 움직이지 않는다)
            Row(id = p.first.id, sym = p.first, h = p.second, value = krw(p.first, p.second.qty * p.first.last), cost = if (p.first.id == KoreaEstimate.sumId) krw(p.first, p.second.qty * p.first.last) else costKrw(p.first, p.second.qty * p.second.avg),   // ios: Data/AppModel.swift:256
                color = Palette.Light.holdColors[i % Palette.Light.holdColors.size])   // ios: Data/AppModel.swift:257
        }
    /** 종목 하나의 숫자 (종목 상세·종목 한눈에가 같이 쓴다)
     *  dd 고점 대비 · r1 1년 전 대비(현지 통화) · vc 산 가격 대비(원화) · w 비중 · yr 1년 주간 종가 */
    data class HoldMetrics(val dd: Double, val r1: Double, val vc: Double, val w: Double, val yr: List<Double>, val hi: Double, val lo: Double)   // ios: Data/AppModel.swift:262
    fun metrics(r: Row): HoldMetrics {   // ios: Data/AppModel.swift:263
        val yr = (0..52).map { prices.price(r.sym, 2.0 / 3 + it.toDouble() / 156) }   // ios: Data/AppModel.swift:264
        val hi = yr.maxOrNull() ?: 1.0; val lo = yr.minOrNull() ?: 1.0; val p1 = yr.lastOrNull() ?: 1.0   // ios: Data/AppModel.swift:265
        return HoldMetrics(dd = p1 / hi - 1, r1 = p1 / yr[0] - 1, vc = if (r.cost > 0) r.value / r.cost - 1 else 0.0,   // ios: Data/AppModel.swift:266
                           w = r.value / max(1.0, total), yr = yr, hi = hi, lo = lo)   // ios: Data/AppModel.swift:267
    }
    /** 내 종목 정렬 (종목 탭 목록. 종목 한눈에는 자기 정렬을 쓴다). 색은 넣은 순서 그대로 */
    var holdSort: HoldSort by observed(HoldSort.entries.firstOrNull { it.raw == (prefs.getString("holdSort", null) ?: "") } ?: HoldSort.value) {   // ios: Data/AppModel.swift:270
        prefs.edit().putString("holdSort", holdSort.raw).apply()   // ios: Data/AppModel.swift:271
    }
    val sortedRows: List<Row> get() {   // ios: Data/AppModel.swift:273
        val rs = rows   // ios: Data/AppModel.swift:274
        return when (holdSort) {   // ios: Data/AppModel.swift:275
            HoldSort.added -> rs   // ios: Data/AppModel.swift:276
            HoldSort.value -> rs.sortedByDescending { it.value }   // ios: Data/AppModel.swift:277
            HoldSort.byName -> Collator.getInstance().let { c -> rs.sortedWith { a, b -> c.compare(a.sym.name, b.sym.name) } }   // ios: Data/AppModel.swift:278
            HoldSort.ret -> rs.sortedWith { a, b -> (if (b.cost > 0) b.value / b.cost else 1.0).compareTo(if (a.cost > 0) a.value / a.cost else 1.0) }   // ios: Data/AppModel.swift:279
            HoldSort.today -> rs.sortedByDescending { it.sym.quote.change }   // ios: Data/AppModel.swift:280
        }
    }
    val total: Double get() = rows.fold(0.0) { a, r -> a + r.value }   // ios: Data/AppModel.swift:283
    /** 사이트에 넣은 현금 (원화 + 달러 × 매매기준율, 웹 cashKrw 와 같다). 앱에는 입력 칸이 없고 동기화로 받은 값만. 샘플·회복 루트는 0 */
    val cashKrw: Double get() {   // ios: Data/AppModel.swift:285
        if (!isGoal || bare || isSample) return 0.0   // ios: Data/AppModel.swift:286
        val c = Sync.shared.siteCash   // ios: Data/AppModel.swift:287
        return c.krw + c.usd * Market.shared.valFx   // ios: Data/AppModel.swift:288
    }
    /** 국내 주식 합계 (날짜별 기록의 오늘 금액, 웹 krNow 와 같다). 샘플·시작 전 루트는 0. 전망은 다른 종목처럼 EWY × 원/달러 움직임으로 */
    val krSum: Double get() {   // ios: Data/AppModel.swift:291
        if (bare || isSample || !holdings.any { it.symbol == KoreaEstimate.sumId }) return 0.0   // ios: Data/AppModel.swift:292
        return CustomSymbols.shared.krNow   // ios: Data/AppModel.swift:293
    }
    val cost: Double get() = rows.fold(0.0) { a, r -> a + r.cost }   // ios: Data/AppModel.swift:295
    val ret: Double get() = if (cost > 0) total / cost - 1 else 0.0   // ios: Data/AppModel.swift:296
    /** 회복 루트의 '몰린 종목' 비중 (내 평가액이 가장 큰 종목, Recovery focusRow) */
    val focusWeight: Double get() = if (total > 0) (focusRow?.value ?: 0.0) / total else 0.0   // ios: Data/AppModel.swift:298
    /** 국내 주식 합계를 뺀 평가액 (오늘 움직임용: 합계는 넣은 날만 바뀐다) */
    val stockTotal: Double get() = rows.fold(0.0) { a, r -> a + (if (r.id == KoreaEstimate.sumId) 0.0 else r.value) }   // ios: Data/AppModel.swift:300
    /** 오늘의 움직임: 원화 평가액 기준 (전일 종가 → 지금). 환율 변화는 빼고 전일·지금 모두 같은 환율(평가액과 같은 매매기준율)로 비교한다.
     *  홈·잠금 위젯(core/Engine)과 같은 기준. 홈·목표·알림·인물 위젯이 이 값을 쓴다 */
    val todayMove: Double get() {   // ios: Data/AppModel.swift:303
        val fx = Market.shared.valFx   // ios: Data/AppModel.swift:304
        val usd = { r: Row -> r.sym.currency == Currency.usd }   // ios: Data/AppModel.swift:305
        val rows = this.rows.filter { it.id != KoreaEstimate.sumId }   // ios: Data/AppModel.swift:306 국내 주식 합계는 넣은 날만 바뀌어 0%로 전체 폭을 줄이지 않게 뺀다
        val prev = rows.fold(0.0) { a, r -> a + r.h.qty * r.sym.quote.prevClose * (if (usd(r)) fx else 1.0) }   // ios: Data/AppModel.swift:307
        val now = rows.fold(0.0) { a, r -> a + r.h.qty * r.sym.last * (if (usd(r)) fx else 1.0) }   // ios: Data/AppModel.swift:308
        return if (prev > 0) now / prev - 1 else 0.0   // ios: Data/AppModel.swift:309
    }

    // 회복 계획 (계산은 Recovery)
    var planKey by mutableStateOf("balance")      // ios: Data/AppModel.swift:318 미션 3에서 고른 계획
    var recM by mutableStateOf(0)                 // ios: Data/AppModel.swift:319 회복 루트 적립 지렛대: 매달 더 넣을 돈 (만원, 계획 4안과 별개)
    // 목표 루트 (만원)
    var route by mutableStateOf(Route.recover)   // ios: Data/AppModel.swift:321
    var gK by mutableStateOf(10000.0)   // ios: Data/AppModel.swift:322
    var gY: Int by observed(5) { old -> if (gY != old) gTo = null }   // ios: Data/AppModel.swift:323 앱에서 기간(년)을 고치면 목표일 = 시작일 + 그 햇수
    var gTo by mutableStateOf<String?>(null)       // ios: Data/AppModel.swift:324 목표일 (yyyy-MM-dd, 사이트 goal.date). null = 시작일 + gY년
    var gFrom by mutableStateOf<String?>(null)     // ios: Data/AppModel.swift:325 목표 시작일 (사이트 goal.start_date). 내 길이 여기서 출발한다
    var gA by mutableStateOf(2000.0)   // ios: Data/AppModel.swift:326
    var gM by mutableStateOf(0.0)   // ios: Data/AppModel.swift:327
    var gMix by mutableStateOf("index")   // ios: Data/AppModel.swift:328
    var gDone by mutableStateOf(setOf<String>())   // ios: Data/AppModel.swift:329
    var gWeeks by mutableStateOf(listOf<GoalWeek>())   // ios: Data/AppModel.swift:330
    var gWeekCur by mutableStateOf<GoalWeek?>(null)   // ios: Data/AppModel.swift:331
    var goalTaxPick by mutableStateOf("split")   // ios: Data/AppModel.swift:332
    var horizon by mutableStateOf(3)              // ios: Data/AppModel.swift:333 미션 1·3의 기간 칩 (3·6·12개월)
    var quizAnswer by mutableStateOf<String?>(null)   // ios: Data/AppModel.swift:334
    var taxGain by mutableStateOf(0.0)            // ios: Data/AppModel.swift:335 올해 실현 이익 (만원). 사용자가 넣는다
    var taxSellQty by mutableStateOf(0.0)   // ios: Data/AppModel.swift:336
    var nxStep by mutableStateOf(1)               // ios: Data/AppModel.swift:337 앱 시작 3단계 중 끝낸 단계 수 (미션 4에 왔으면 1단계 종목 추가는 이미 한 것)
    var boardPath by mutableStateOf(listOf<MissionRoute>())   // ios: Data/AppModel.swift:338
    /** NavigationPath 자리: 여러 종류(분석 화면·종목)를 같이 쌓는 목록 */
    var analysisPath by mutableStateOf(listOf<Any>())   // ios: Data/AppModel.swift:339
    var homePath by mutableStateOf(listOf<String>())   // ios: Data/AppModel.swift:340
    var tab by mutableStateOf(launchTab(intent))   // ios: Data/AppModel.swift:341
    // ios: Data/AppModel.swift:343,344 처음 실행이면 첫 질문(플러스·마이너스·시작 전)부터. 캡처·시연용 실행 인자가 있으면 건너뛴다
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false) || intent?.getStringExtra("tab") != null
        || intent?.getStringExtra("goal") != null || intent?.getStringExtra("demo") != null)
    var appearance by mutableStateOf(prefs.getString("appearance", null) ?: "system")   // ios: Data/AppModel.swift:345 system · light · dark
    /** 거주 국가(ISO 코드). 세금 기능만 이 값을 따른다. 처음엔 기기 지역, 없으면 한국 */
    var residence: String by observed(prefs.getString("residence", null) ?: Locale.getDefault().country.ifEmpty { null } ?: "KR") {   // ios: Data/AppModel.swift:347
        prefs.edit().putString("residence", residence).apply()   // ios: Data/AppModel.swift:348
    }
    val residenceName: String get() = (runCatching { Locale.Builder().setRegion(residence).build().displayCountry }.getOrNull() ?: "").ifEmpty { residence }   // ios: Data/AppModel.swift:350
    // 종목 탭은 종목(Symbol)과 '종목 추가'(String)를, 분석 탭은 분석 화면과 종목을 같이 쌓으므로 여러 종류 목록
    var holdPath by mutableStateOf(listOf<Any>())   // ios: Data/AppModel.swift:352
    var holdOpen by mutableStateOf<Symbol?>(null)   // ios: Data/AppModel.swift:355 종목 탭에서 띄울 종목 상세 팝업 (알림·시험 길. 띄우면 비운다)
    /** 내 기록 카드에서 '국내 주식 합계' 그래프를 보고 있는지 (합계 입력 칸의 안내 줄에서 바로 연다) */
    var recKr by mutableStateOf(false)   // ios: Data/AppModel.swift:354
    /** 리마인드에서 국내 주식 합계 입력 칸 띄우기 */
    var krSumAsk by mutableStateOf(false)   // ios: Data/AppModel.swift:356

    /** scenePhase == .active: 앱이 앞에 있는지 (onStart/onStop) */
    var active by mutableStateOf(false)   // ios: App.swift:27
    /** 동작 줄이기: 앱을 앞으로 가져올 때마다 다시 읽는다 */
    var reduceMotion by mutableStateOf(false)

    fun onForeground(on: Boolean) {
        active = on
        if (on) reduceMotion = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // 설정
    // 알림 설정은 기기에 남는다 (실제 예약은 Notifier). SharedPreferences 에 사전이 없어 JSON 글자로 둔다
    var alerts: Map<String, Boolean> by observed(prefs.getString("alerts", null)?.let { runCatching { NJson.decodeFromString<Map<String, Boolean>>(it) }.getOrNull() } ?: mapOf("be" to true, "drift" to false, "dep" to false, "morn" to false)) {   // ios: Data/AppModel.swift:360
        prefs.edit().putString("alerts", NJson.encodeToString(alerts)).apply()   // ios: Data/AppModel.swift:361
    }
    var alertLast by mutableStateOf<String?>(null)   // ios: Data/AppModel.swift:363
    var alertTh: Int by observed(prefs.getInt("alertTh", 5)) { prefs.edit().putInt("alertTh", alertTh).apply() }   // ios: Data/AppModel.swift:364
    var alertHr: Int by observed(prefs.getInt("alertHr", 8)) { prefs.edit().putInt("alertHr", alertHr).apply() }   // ios: Data/AppModel.swift:365
    /** 기기 자동 동기화 (Sync). 시안 때의 가짜 연결 대신 실제 상태 */
    val syncOn: Boolean get() = Sync.shared.isOn   // ios: Data/AppModel.swift:367
    var widgetSel by mutableStateOf<List<String>?>(null)   // ios: Data/AppModel.swift:368
    var settingsPath by mutableStateOf(listOf<SettingsRoute>())   // ios: Data/AppModel.swift:369
    var settingsOpen by mutableStateOf(false)   // ios: Data/AppModel.swift:370 설정 팝업 (탭에서 뺐다, 2026-10-21)
    var widgetsOpen by mutableStateOf(false)    // ios: Data/AppModel.swift:371 홈 화면 위젯 팝업
    /** 설정 팝업을 r 화면까지 열어 둔다. 소식함 같은 다른 팝업이 닫히는 중일 수 있어 잠깐 뒤에 띄운다 */
    // ios: Data/AppModel.swift:373,374,375 (0.35초)
    fun openSettings(r: SettingsRoute? = null) {
        settingsPath = r?.let { listOf(it) } ?: emptyList()
        MainScope().launch { kotlinx.coroutines.delay(350); settingsOpen = true }
    }

    val planWeight: Double get() = selectedPlan.wt   // ios: Data/AppModel.swift:378
    fun planBreakEven(T: Double): Double = selectedPlan.prob(cost, T)   // ios: Data/AppModel.swift:379 years

    // 미션 판
    data class Block(val id: Int, val num: String, val title: String, val reward: String, val inter: Boolean)   // ios: Data/AppModel.swift:382
    val blocks: List<Block> = listOf(   // ios: Data/AppModel.swift:383
        Block(id = 1, num = "1", title = "종목·단가 넣기", reward = "회복 확률", inter = false),   // ios: Data/AppModel.swift:384
        Block(id = 2, num = "2", title = "원인 진단", reward = "회복 경로", inter = false),   // ios: Data/AppModel.swift:385
        Block(id = 3, num = "3", title = "내 계획 정하기", reward = "비중 비교", inter = false),   // ios: Data/AppModel.swift:386
        Block(id = 5, num = "4", title = "앱 시작 3단계", reward = "위젯 전부", inter = false),   // ios: Data/AppModel.swift:387
        Block(id = 6, num = "인터미션", title = "처음 4주 체크인", reward = "캐릭터 위젯 4개, 1년 전망", inter = true),   // ios: Data/AppModel.swift:388
    )
    fun available(b: Block): Boolean {   // ios: Data/AppModel.swift:390
        val i = blocks.indexOfFirst { it.id == b.id }; if (i < 0) return false   // ios: Data/AppModel.swift:391
        return i == 0 || done.contains(blocks[i - 1].id)   // ios: Data/AppModel.swift:392
    }
    val playOn: Boolean get() = if (route == Route.recover) done.contains(5) else gDone.contains("link")   // ios: Data/AppModel.swift:394
    val interDone: Boolean get() = if (route == Route.recover) done.contains(6) else gDone.contains("inter")   // ios: Data/AppModel.swift:395
    fun finishAppStart() {   // ios: Data/AppModel.swift:396
        if (route == Route.recover) done = done + 5 else gDone = gDone + "link"   // ios: Data/AppModel.swift:397
        boardPath = emptyList()   // ios: Data/AppModel.swift:398
        fixStarts()   // ios: Data/AppModel.swift:399
    }

    /** 체크인: 시작한 날부터 7일마다 한 주씩만 (서버 시각, Me weekGate). 폰 시간을 바꿔 앞당길 수 없다 */
    fun confirmWeek() {   // ios: Data/AppModel.swift:403
        val c = weekCur; if (c == null || weeks.size >= 4 || weekGate != WeekGate.open) return   // ios: Data/AppModel.swift:404
        weeks = weeks + c; weekCur = null   // ios: Data/AppModel.swift:405
        if (weeks.size == 4) done = done + 6   // ios: Data/AppModel.swift:406
    }
    /** 지난 주차 체크인은 다음 주차가 열리기 전까지 바꿀 수 있다 */
    val weekEditable: Boolean get() = weeks.isNotEmpty() && weeks.size < 4 && weekGate != WeekGate.open   // ios: Data/AppModel.swift:409
    fun editLastWeek(p: WeekPick) { if (weekEditable) weeks = weeks.dropLast(1) + p }   // ios: Data/AppModel.swift:410

    // 친구·장
    // 세리 외에 열린 친구 수. 처음 연 날부터 이틀째에 한 명씩 하루 1명 (2026-10-16 결정, Support.dayN).
    // 미션으로 먼저 만난 친구도 그대로 (목표 루트는 첫 달 1~4주차, 회복 루트는 인터미션 주마다)
    /** 개발자 계정(개발자 동기화로 연결한 기기)은 모든 보상이 열린다. 미션 진행 자체는 그대로 둔다.
     *  내부 테스트 빌드에서만: 스토어 빌드에서 자체 방식으로 여는 건 결제 정책 위반이라 끈다 (구독은 인앱 구매로) */
    val devAll: Boolean get() {   // ios: Data/AppModel.swift:417
        if (debug) {   // ios: Data/AppModel.swift:418
            if (arg("devAllTest") == "YES") return true   // ios: Data/AppModel.swift:419 확인용: --es devAllTest YES
        }   // ios: Data/AppModel.swift:420
        return Support.shared.testBuild && Sync.shared.isDev   // ios: Data/AppModel.swift:421
    }
    /** 앱 시작 3단계 보상(오늘의 1분·1000칸·주간 예보)이 열렸는지 */
    val playUnlocked: Boolean get() = playOn || devAll   // ios: Data/AppModel.swift:424
    val friendsOpen: Int get() {   // ios: Data/AppModel.swift:425
        if (devAll) return 4   // ios: Data/AppModel.swift:426
        val byDay = min(4, Support.shared.dayN - 1)   // ios: Data/AppModel.swift:427
        // 루트를 바꿔도 이미 만난 친구는 그대로 (회복 → 목표로 넘어간 뒤에도)
        if (done.contains(6) || gDone.contains("inter")) return 4   // ios: Data/AppModel.swift:429
        if (route == Route.recover) return max(byDay, weeks.size)   // ios: Data/AppModel.swift:430
        return maxOf(byDay, weeks.size, if (gWeeks.isNotEmpty()) 4 else if (playOn) min(4, day / 7) else 0)   // ios: Data/AppModel.swift:431
    }
    fun friendOn(i: Int): Boolean = i == 0 || i <= friendsOpen   // ios: Data/AppModel.swift:433
    /** 외전 프롤로그~5장: 처음부터 프롤로그, 앞 장을 끝까지 읽으면 다음 장 (2026-10-16 결정) */
    fun chapterOn(i: Int): Boolean =   // ios: Data/AppModel.swift:435
        devAll || i == 0 || Story.shared.isDone(StoryBook.side, i - 1)   // ios: Data/AppModel.swift:436
    fun friendWhen(i: Int): String = if (i == 0) "처음부터 · 홈" else "${i + 1}일째"   // ios: Data/AppModel.swift:438

    // 오늘의 1분
    val today: DayLog get() = dayLog[day] ?: DayLog()   // ios: Data/AppModel.swift:441
    val streak: Int get() {   // ios: Data/AppModel.swift:442
        var k = 0; var d = day - (if (today.answer != null) 0 else 1)   // ios: Data/AppModel.swift:443
        while (d >= 0) {   // ios: Data/AppModel.swift:444
            val ok = dayLog[d]?.let { it.answer != null } ?: (d < 5)   // ios: Data/AppModel.swift:445
            if (!ok) break   // ios: Data/AppModel.swift:446
            k += 1; d -= 1   // ios: Data/AppModel.swift:447
        }
        return k   // ios: Data/AppModel.swift:449
    }
    /** 돌아온 물건: 연속 7일을 채울 때마다 하나씩 늘고, 연속이 끊겨도 줄지 않는다 */
    var itemsBack by mutableStateOf(0)   // ios: Data/AppModel.swift:452
    var itemsBackDay by mutableStateOf<Int?>(null)   // ios: Data/AppModel.swift:453 마지막으로 물건이 돌아온 날 (같은 날 답을 바꿔도 두 번 세지 않게)
    var itemsOff by mutableStateOf(setOf<String>())   // ios: Data/AppModel.swift:454 쉼터 방 배치에서 뺀 물건
    /** 열린 물건: 처음 연 날부터 하루 2개 (2026-10-16 결정). 오늘의 1분으로 먼저 돌아온 것도 그대로. 개발자 권한은 물건 모두 */
    val itemsOn: Int get() = if (devAll) 10 else min(10, max(if (playOn) itemsBack else 0, 2 * Support.shared.dayN))   // ios: Data/AppModel.swift:456

    // 오늘 숫자와 질문은 내 종목의 실제 시세로 만든다 (Market: 전일 종가 → 지금, 종목 통화 기준)
    /** 오늘 많이 움직인 순서 */
    val movers: List<Row> get() = rows.sortedByDescending { abs(it.sym.quote.change) }   // ios: Data/AppModel.swift:462
    /** 홈 말풍선 정보 문장: 오늘 평가액 움직임(원화)에 크게 기여한 종목 1~2개. 전체 움직임의 30% 이상일 때만 이름을 쓴다 */
    val todayMoversText: String? get() {   // ios: Data/AppModel.swift:464
        val fx = Market.shared.valFx   // ios: Data/AppModel.swift:465
        val rs = rows.filter { it.id != KoreaEstimate.sumId }   // ios: Data/AppModel.swift:466
        if (rs.isEmpty()) return null   // ios: Data/AppModel.swift:467
        val c = rs.map { r -> Pair(r, r.h.qty * r.sym.quote.prevClose * (if (r.sym.currency == Currency.usd) fx else 1.0) * r.sym.quote.change) }   // ios: Data/AppModel.swift:468
        val all = c.fold(0.0) { a, x -> a + abs(x.second) }   // ios: Data/AppModel.swift:469
        if (all <= 0) return null   // ios: Data/AppModel.swift:470
        val mv = todayMove; val up = mv >= 0   // ios: Data/AppModel.swift:471
        val top = c.filter { (it.second >= 0) == up }.sortedByDescending { abs(it.second) }.take(2).filter { abs(it.second) >= all * 0.3 }   // ios: Data/AppModel.swift:472
        if (top.isEmpty()) return "오늘은 여러 종목이 고르게 움직였어요."   // ios: Data/AppModel.swift:473
        val names = top.joinToString(tr("와 ")) { "${it.first.sym.short}(${sgn2(it.first.sym.quote.change)})" }   // ios: Data/AppModel.swift:474
        return "오늘 ${if (up) "상승" else "하락"}은 주로 ${names} 때문이에요."   // Data/AppModel.swift:466 (검사 예외: 보간 속 따옴표)
    }
    val routineFact: String get() {   // ios: Data/AppModel.swift:477
        val tot = todayMove   // ios: Data/AppModel.swift:478
        val mv = movers.take(2).joinToString(" · ") { "${it.sym.short} ${sgn2(it.sym.quote.change)}" }   // ios: Data/AppModel.swift:479
        val head = if (rows.isEmpty()) "" else "**${sgn(tot)}**" + (if (mv.isEmpty()) "" else " · $mv") + "\n"     // ios: Data/AppModel.swift:480 오늘 움직임은 진하게 (마크다운)
        if (bare) {   // ios: Data/AppModel.swift:481
            return head + "모은 돈은" + " 목표의 ${pct(goalSaved / max(1.0, gK))}예요. 다음 적립일에 ${wonK(gM)}이 더해져요."   // ios: Data/AppModel.swift:482
        }
        if (total <= 0) return "아직 넣은 종목이 없어요. 종목 탭에서 수량을 넣으면 내일부터 내 숫자를 가져올게요."   // ios: Data/AppModel.swift:484
        val vY = total / (1 + tot)   // ios: Data/AppModel.swift:485
        if (route == Route.plus) {   // ios: Data/AppModel.swift:486
            val k = gK * 1e4   // ios: Data/AppModel.swift:487
            val a = pct(vY / k); val b = pct(total / k)   // ios: Data/AppModel.swift:488
            return head + (if (a == b) "목표 **${b}** 그대로" else "목표 **${a} → ${b}**")   // ios: Data/AppModel.swift:489
        }
        val distNow = cost / total - 1; val distY = cost / vY - 1   // ios: Data/AppModel.swift:491
        if (distNow <= 0) return head + "본전보다 ${sgn(ret)} 위에 있어요."   // ios: Data/AppModel.swift:492
        return head + "본전까지 ${fmtF("%.1f", distY * 100)}% → ${fmtF("%.1f", distNow * 100)}%" + (if (distNow < distY) ", 가까워졌어요." else ", 조금 멀어졌어요.")   // Data/AppModel.swift:484 (검사 예외: 보간 속 따옴표)
    }

    class Question(   // ios: Data/AppModel.swift:496
        val id: String, val tag: String, val q: String, val opts: List<String>, val right: Int, val fb: (Int) -> String   // ios: Data/AppModel.swift:497
    ) {
        /** 보기 글자가 겹치거나 0원·NaN 같은 이상한 값이면 안 낸다 (Kotlin 은 NaN·Infinity 로 써서 대소문자 무시) */
        val fair: Boolean get() = opts.toSet().size == opts.size && !opts.any { it.startsWith("0원") || it.contains("nan", ignoreCase = true) || it.contains("inf", ignoreCase = true) }   // ios: Data/AppModel.swift:499
    }

    /** 오늘 낼 수 있는 질문 전부. 내 종목·계산값으로 만들고, 조건이 안 맞는 질문은 빠진다 */
    val questionPool: List<Question> get() {   // ios: Data/AppModel.swift:511
        val qs = mutableListOf<Question>()   // ios: Data/AppModel.swift:512
        val rs = rows; val tot = max(1.0, total); val mv = todayMove   // ios: Data/AppModel.swift:513
        if (rs.isNotEmpty()) {   // ios: Data/AppModel.swift:514
            val diff = stockTotal - stockTotal / (1 + mv)     // ios: Data/AppModel.swift:515 오늘 움직임은 종목만 (국내 주식 합계 빼고)
            qs.add(Question(id = "updown", tag = "오늘 움직임", q = "어제 종가와 비교해, 오늘 내 평가액은?", opts = listOf("올랐어요", "내렸어요"), right = if (mv >= 0) 0 else 1,   // ios: Data/AppModel.swift:516
                            fb = { "오늘 ${sgn(mv)}, 원화로 ${won(diff)} ${if (diff >= 0) "늘었어요" else "줄었어요"}." }))   // Data/AppModel.swift:508 (검사 예외: 보간 속 따옴표)
        }
        // 종목이 둘 이상: 많이 움직인 종목·비중 1위 (보기는 이름순이라 순서로 답이 새지 않게)
        val byW = rs.sortedByDescending { it.value }   // ios: Data/AppModel.swift:520
        if (byW.size >= 2) {   // ios: Data/AppModel.swift:521
            val c = byW.take(3).sortedBy { it.sym.short }   // ios: Data/AppModel.swift:522
            val chg = c.map { abs(it.sym.quote.change) }; val wt = c.map { it.value / tot }   // ios: Data/AppModel.swift:523
            val mvList = c.joinToString(" · ") { "${it.sym.short} ${sgn2(it.sym.quote.change)}" }   // ios: Data/AppModel.swift:524
            val wtList = c.sortedByDescending { it.value }.joinToString(" · ") { "${it.sym.short} ${pct(it.value / tot)}" }   // ios: Data/AppModel.swift:525
            qs.add(Question(id = "mover", tag = "오늘 움직임", q = "오늘 가장 크게 움직인 종목은?", opts = c.map { it.sym.short },   // ios: Data/AppModel.swift:526
                            right = chg.indices.maxByOrNull { chg[it] } ?: 0,   // ios: Data/AppModel.swift:527
                            fb = { "${mvList}. 크게 움직인 종목일수록 내 평가액도 많이 흔들어요." }))   // ios: Data/AppModel.swift:528
            qs.add(Question(id = "weight", tag = "비중 퀴즈", q = "가장 큰 종목은?", opts = c.map { it.sym.short },   // ios: Data/AppModel.swift:529
                            right = wt.indices.maxByOrNull { wt[it] } ?: 0,   // ios: Data/AppModel.swift:530
                            fb = { wtList }))   // ios: Data/AppModel.swift:531
            val top = byW[0]; val w = top.value / tot; val n = near3(w, step = 0.2, lo = 0.2, hi = 1.0, salt = 1)   // ios: Data/AppModel.swift:532
            qs.add(Question(id = "topshare", tag = "비중 퀴즈", q = "${top.sym.short}는 내 평가액의 몇 %쯤일까요?", opts = n.opts.map { "약 ${pct(it)}" }, right = n.right,   // ios: Data/AppModel.swift:533
                            fb = { "${top.sym.short} ${won(top.value)} ÷ 전체 ${won(tot)} = ${pct(w)}." }))   // ios: Data/AppModel.swift:534
        }
        // 환율: 달러 종목이 있을 때
        val usd = rs.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value } / tot; val fxc = Market.shared.fx.change   // ios: Data/AppModel.swift:537
        if (usd > 0) {   // ios: Data/AppModel.swift:538
            val eff = usd * fxc   // ios: Data/AppModel.swift:539
            qs.add(Question(id = "fx", tag = "환율 퀴즈", q = "오늘 원/달러가 ${sgn(fxc)} 움직였어요. 달러 종목이 ${pct(usd)}인 내 평가액에는?",   // ios: Data/AppModel.swift:540
                            opts = listOf("올려 줬어요", "내렸어요", "거의 그대로예요"), right = if (abs(eff) < 0.0005) 2 else if (eff > 0) 0 else 1,   // ios: Data/AppModel.swift:541
                            fb = { "달러 비중 × 환율 변화 ≈ ${sgn(eff)}. 환율이 오르면 달러 종목의 원화 값도 같이 올라요." }))   // ios: Data/AppModel.swift:542
        }
        if (cost > 0 && rs.isNotEmpty()) {   // ios: Data/AppModel.swift:544
            val r = ret; val n = near3(r, step = 0.1, lo = -1.0, hi = 10.0, salt = 2)   // ios: Data/AppModel.swift:545
            val c = cost; val t = total   // ios: Data/AppModel.swift:546
            qs.add(Question(id = "ret", tag = "수익률 퀴즈", q = "산 값과 비교한 지금 내 수익률은 어느 쪽에 가까울까요?", opts = n.opts.map { "약 " + sgn0(it) }, right = n.right,   // ios: Data/AppModel.swift:547
                            fb = { "지금 ${sgn(r)}. 들어간 돈 ${won(c)}, 지금 ${won(t)}." }))   // ios: Data/AppModel.swift:548
        }
        if (route == Route.recover) {   // ios: Data/AppModel.swift:550
            if (cost > 0 && rs.isNotEmpty()) {   // ios: Data/AppModel.swift:551
                val p12 = planBreakEven(1.0); val p6 = planBreakEven(0.5); val n = near3(p12, step = 0.2, lo = 0.1, hi = 0.9, salt = 3)   // ios: Data/AppModel.swift:552
                qs.add(Question(id = "be1y", tag = "확률 퀴즈", q = "지금 계획(${selectedPlan.name})으로 1년 안에 본전에 닿을 확률은 어느 쪽에 가까울까요?",   // ios: Data/AppModel.swift:553
                                opts = n.opts.map { "약 ${pct(it)}" }, right = n.right,   // ios: Data/AppModel.swift:554
                                fb = { "모형 계산으로 약 ${pct(p12)}예요. 6개월 안이면 ${pct(p6)}. 기간이 길수록 높아져요." }))   // ios: Data/AppModel.swift:555
            }
            if (total > 0 && cost > total) {   // ios: Data/AppModel.swift:557
                val d = cost / total - 1; val loss = 1 - total / cost; val n = near3(d, step = 0.1, lo = 0.0, hi = 10.0, salt = 4)   // ios: Data/AppModel.swift:558
                qs.add(Question(id = "dist", tag = "본전 퀴즈", q = "들어간 돈보다 ${pct(loss)} 내려 있어요. 본전까지는 몇 % 올라야 할까요?",   // ios: Data/AppModel.swift:559
                                opts = n.opts.map { "약 ${pct(it)}" }, right = n.right,   // ios: Data/AppModel.swift:560
                                fb = { "${fmtF("%.1f", d * 100)}% 올라야 해요. 내린 만큼보다 더 올라야 하는 건, 줄어든 돈에서 다시 오르기 때문이에요." }))   // Data/AppModel.swift:552 (검사 예외: 보간 속 따옴표)
            }
            if (rs.isNotEmpty()) {   // ios: Data/AppModel.swift:563
                val y = cellsYesterday; val now = cellsNow; val unit = cellUnit   // ios: Data/AppModel.swift:564
                qs.add(Question(id = "cells", tag = "1000칸", q = "어제 ${y}칸이었어요. 오늘 1000칸은?", opts = listOf("늘었어요", "그대로예요", "줄었어요"),   // ios: Data/AppModel.swift:565
                                right = if (now > y) 0 else if (now == y) 1 else 2,   // ios: Data/AppModel.swift:566
                                fb = { "어제 ${y}칸 → 오늘 ${now}칸. 1칸은 ${won(unit)}이에요." }))   // ios: Data/AppModel.swift:567
                val wr = weekRange; val v = trackValue   // ios: Data/AppModel.swift:568
                qs.add(Question(id = "week", tag = "주간 예보", q = "이번 주 예보 범위는 ${won(wr.lo)}~${won(wr.hi)}예요. 지금 평가액은 그 안일까요?",   // ios: Data/AppModel.swift:569
                                opts = listOf("안이에요", "밖이에요"), right = if (v >= wr.lo && v <= wr.hi) 0 else 1,   // ios: Data/AppModel.swift:570
                                fb = { "지금 ${won(v)}. 예보 범위는 절반쯤 맞도록 잡은 폭이라, 밖으로 나가는 날도 자주 있어요." }))   // ios: Data/AppModel.swift:571
            }
            // 계획 비중 확인: 비중 1위 종목 기준 (종목이 둘 이상일 때)
            if (rs.size >= 2) {   // ios: Data/AppModel.swift:574
                val w = pct(focusWeight); val pw = pct(planWeight); val T = horizon.toDouble() / 12   // ios: Data/AppModel.swift:575
                val p0 = keepPlan.prob(cost, T); val p1 = selectedPlan.prob(cost, T)   // ios: Data/AppModel.swift:576
                qs.add(Question(id = "plan", tag = "비중 확인", q = "가장 큰 종목 비중 ${w}, 계획은 ${pw}예요. 계획 비중이면 ${horizonLabel} 안 본전 확률은?", opts = listOf("올라요", "비슷해요", "내려요"),   // ios: Data/AppModel.swift:577
                                right = if (abs(p1 - p0) < 0.02) 1 else if (p1 > p0) 0 else 2,   // ios: Data/AppModel.swift:578
                                fb = { "지금 비중 ${pct(p0)}, 계획 비중 ${pct(p1)}. 계산 예시이고, 매매 판단은 직접 하세요." }))   // ios: Data/AppModel.swift:579
            }
            qs.add(Question(id = "mind", tag = "오늘 마음", q = "오늘 내 투자, 어떻게 느껴져요?", opts = listOf("불안해요", "괜찮아요", "기대돼요"), right = -1,   // ios: Data/AppModel.swift:581
                            fb = { if (it == 0) "불안한 날엔 하루 숫자 말고 종목 상세의 1년 칩을 보세요. 길이 더 잘 보여요." else "좋아요. 내일도 1분이면 돼요." }))   // ios: Data/AppModel.swift:582
            return qs.filter { it.fair }   // ios: Data/AppModel.swift:583
        }
        // 목표 루트
        val principal = gM * 12 * gY.toDouble(); val m = gM; val y = gY   // ios: Data/AppModel.swift:586
        if (m >= 1 && y > 0) {     // ios: Data/AppModel.swift:587 매달 금액·기간이 없으면 계산 퀴즈·적립 질문은 안 낸다. 보기는 1년치 간격
            val n = near3(principal, step = m * 12, lo = m * 12, hi = Double.POSITIVE_INFINITY, salt = 9)   // ios: Data/AppModel.swift:588
            qs.add(Question(id = "principal", tag = "계산 퀴즈", q = "매달 ${wonK(m)}씩 ${y}년 넣으면 넣은 원금만 얼마일까요?",   // ios: Data/AppModel.swift:589
                            opts = n.opts.map { wonK(it) }, right = n.right,   // ios: Data/AppModel.swift:590
                            fb = { "${wonK(m)} × 12달 × ${y}년 = ${wonK(principal)}. 그 위에 수익이 더해져 목표로 가요." }))   // ios: Data/AppModel.swift:591
            qs.add(Question(id = "deposit", tag = "이번 달 적립", q = "이번 달 ${wonK(m)}, 넣었나요?", opts = listOf("넣었어요", "아직이요"), right = -1,   // ios: Data/AppModel.swift:592
                            fb = { if (it == 0) "좋아요. 3개월 블록에 반영해 둘게요." else "괜찮아요. 적립일 알림이 한 번 더 알려 드릴게요." }))   // ios: Data/AppModel.swift:593
        }
        if (keyValue > 0) {   // ios: Data/AppModel.swift:595
            val p = trackValue / keyValue; val n = near3(p, step = 0.1, lo = 0.0, hi = 1.5, salt = 5); val v = trackValue; val k = keyValue   // ios: Data/AppModel.swift:596
            qs.add(Question(id = "progress", tag = "목표 퀴즈", q = "지금 모은 돈은 목표의 몇 %쯤일까요?", opts = n.opts.map { "약 ${pct(it)}" }, right = n.right,   // ios: Data/AppModel.swift:597
                            fb = { "${won(v)} ÷ 목표 ${won(k)} = ${pct(p)}." }))   // ios: Data/AppModel.swift:598
            val pg = forecast.prob(y.toDouble()); val g = near3(pg, step = 0.2, lo = 0.0, hi = 1.0, salt = 6)   // ios: Data/AppModel.swift:599
            qs.add(Question(id = "goalprob", tag = "확률 퀴즈", q = "지금 그대로 ${y}년 가면 목표에 닿을 확률은 어느 쪽에 가까울까요?", opts = g.opts.map { "약 ${pct(it)}" }, right = g.right,   // ios: Data/AppModel.swift:600
                            fb = { "모형 계산으로 약 ${pct(pg)}예요. 매달 넣는 돈을 늘리면 이 숫자가 올라가요." }))   // ios: Data/AppModel.swift:601
        }
        qs.add(Question(id = "mind", tag = "오늘 마음", q = "오늘 목표까지의 길, 어떻게 느껴져요?", opts = listOf("멀어요", "보통이에요", "가까워요"), right = -1,   // ios: Data/AppModel.swift:603
                        fb = { if (it == 0) "먼 길은 매달 넣는 돈이 끌고 가요. 1년 뒤 그래프를 한 번 보세요." else "그 느낌 그대로 가요. 내일도 1분이면 돼요." }))   // ios: Data/AppModel.swift:604
        return qs.filter { it.fair }   // ios: Data/AppModel.swift:605
    }

    /** 오늘 질문: 이미 본 날은 그 질문 그대로. 아니면 지난 7일에 안 나온 것 중 날짜로 정한 무작위 (하루 동안은 같은 질문) */
    val question: Question get() {   // ios: Data/AppModel.swift:609
        val pool = questionPool   // ios: Data/AppModel.swift:610
        dayLog[day]?.q?.let { id -> pool.firstOrNull { it.id == id }?.let { return it } }   // ios: Data/AppModel.swift:611
        val recent = (max(0, day - 7) until max(0, day)).mapNotNull { dayLog[it]?.q }.toSet()   // ios: Data/AppModel.swift:612
        val fresh = pool.filter { !recent.contains(it.id) }   // ios: Data/AppModel.swift:613
        val c = if (fresh.isEmpty()) pool else fresh   // ios: Data/AppModel.swift:614
        var x = day.toLong().toULong() + 0x9E3779B97F4A7C15uL   // Data/AppModel.swift:606 (검사 예외: 부호 없는 64비트 글자)
        x = (x xor (x shr 30)) * 0xBF58476D1CE4E5B9uL   // Data/AppModel.swift:607 (검사 예외)
        x = (x xor (x shr 27)) * 0x94D049BB133111EBuL   // Data/AppModel.swift:608 (검사 예외)
        x = x xor (x shr 31)   // ios: Data/AppModel.swift:618
        return c[(x % c.size.toULong()).toInt()]   // ios: Data/AppModel.swift:619
    }
    /** 퀴즈 정답 기록 (맞힌 수, 푼 수) */
    data class QuizScore(val right: Int, val all: Int)
    val quizScore: QuizScore get() {   // ios: Data/AppModel.swift:622
        val r = dayLog.values.mapNotNull { it.ok }   // ios: Data/AppModel.swift:623
        return QuizScore(r.count { it }, r.size)   // ios: Data/AppModel.swift:624
    }
    fun markSeen() { val t = today.copy(); t.seen = true; t.q = t.q ?: question.id; dayLog = dayLog + (day to t) }   // ios: Data/AppModel.swift:626
    fun answer(i: Int) {   // ios: Data/AppModel.swift:627
        val q = question   // ios: Data/AppModel.swift:628
        val t = today.copy(); t.seen = true; t.answer = i; t.q = q.id   // ios: Data/AppModel.swift:629
        if (q.right >= 0) t.ok = i == q.right   // ios: Data/AppModel.swift:630
        dayLog = dayLog + (day to t)   // ios: Data/AppModel.swift:631
        if (playOn && itemsBackDay != day && itemsBack < 10) { itemsBack += 1; itemsBackDay = day }   // ios: Data/AppModel.swift:632
    }
    val routineDoneText: String get() {   // ios: Data/AppModel.swift:634
        val n = itemsOn   // ios: Data/AppModel.swift:635
        if (itemsBackDay == day && n > 0) { val x = Shelter.items[n - 1].name; return "루틴 끝! ${x}${WChar.josa(x)} 돌아왔어요" }   // ios: Data/AppModel.swift:636
        if (n < 10) { val x = Shelter.items[n].name; return "루틴 끝! 내일 하면 ${x}${WChar.josa(x)} 돌아와요" }   // ios: Data/AppModel.swift:637
        return "루틴 끝! 물건이 다 돌아왔어요"   // ios: Data/AppModel.swift:638
    }
    val homeSay: String get() {   // ios: Data/AppModel.swift:640
        val who = voice(homeFriendShown)   // ios: Data/AppModel.swift:641
        // 앱 시작 3단계 안내는 홈 말풍선에 두지 않는다. 미션에만 (2026-10-27)
        if (today.answer != null) {   // ios: Data/AppModel.swift:645
            val n = itemsOn   // ios: Data/AppModel.swift:646
            val info = if (n < 10) "내일 또 오면 쉼터에 물건(${Shelter.items[n].name})이 돌아와요." else ""   // ios: Data/AppModel.swift:647
            return listOf(Lines.pick("home.done.$who") ?: "오늘은 여기까지예요.", info).filter { it.isNotEmpty() }.joinToString(" ")   // ios: Data/AppModel.swift:648
        }
        return Lines.pick("home.fresh.$who", mapOf("move" to sgn(todayMove))) ?: "오늘 숫자 가져왔어요. 한 번만 보고 가요."   // ios: Data/AppModel.swift:650
    }

    // 전망 (웹과 같은 몬테카를로, 목표일까지. 회복 루트는 3년)
    data class YearP(val year: Int, val p: Double)
    class Forecast(   // ios: Data/AppModel.swift:654
        /** 월 경계마다 평가액 백분위 (원). 첫 칸 = 오늘 */
        val q5: List<Double>, val q25: List<Double>, val q50: List<Double>, val q75: List<Double>, val q95: List<Double>,   // ios: Data/AppModel.swift:656
        /** 각 칸의 날짜 (yyyy-MM-dd)와 오늘부터 햇수 */
        val dates: List<String>,   // ios: Data/AppModel.swift:658
        val tY: List<Double>,   // ios: Data/AppModel.swift:659
        /** T년 안에 기준선(목표·본전)에 한 번이라도 닿을 확률 (웹 '연도별 도달 확률'과 같은 셈, 월말 기준) */
        val prob: (Double) -> Double,   // ios: Data/AppModel.swift:661
        /** 마지막 날(목표일) 기준선 이상일 확률 (웹 '목표 확률') */
        val pGoal: Double,   // ios: Data/AppModel.swift:663
        val byYear: List<YearP>,   // ios: Data/AppModel.swift:664
        val mu: Double, val sigma: Double, val muBase: Double, val muTrend: Double,   // ios: Data/AppModel.swift:665
        /** 몬테카를로 결과면 true. 계산이 끝나기 전 닫힌 식 값이면 false */
        val exact: Boolean,   // ios: Data/AppModel.swift:667
    ) {
        /** 이 햇수에 가장 가까운 칸 (그래프·3년 값) */
        fun idx(years: Double): Int {   // ios: Data/AppModel.swift:669
            var k = 0   // ios: Data/AppModel.swift:670
            while (k + 1 < tY.size && tY[k + 1] <= years + 1.0 / 48) k += 1   // ios: Data/AppModel.swift:671
            return k   // ios: Data/AppModel.swift:672
        }
    }
    val forecastTitle: String get() = if (isGoal) "목표일 전망" else "3년 전망"   // ios: Data/AppModel.swift:675
    /** 몬테카를로에 넣는 목표: 웹처럼 현금은 그대로 있다고 보고 목표에서 뺀다 (전망 경로는 종목만) */
    val mcGoal: Double get() = if (isGoal) max(1.0, keyValue - cashKrw) else keyValue   // ios: Data/AppModel.swift:677
    /** 전망 끝날: 목표 루트는 목표일, 회복 루트는 3년 뒤 (웹: 목표일) */
    val forecastHorizon: String get() = if (isGoal) Day.str(goalToDate) else MC.addMonths(Day.today, 36)   // ios: Data/AppModel.swift:679

    /** 웹과 같은 계산에 넣을 값. 종목이 없거나 시작 전 루트, 사이트 통계가 없으면 null (닫힌 식만) */
    val forecastJob: ForecastEngine.Job? get() {   // ios: Data/AppModel.swift:682
        val rs = rows.filter { !Market.shared.needsKey(it.sym) }; val today = Day.today; val H = forecastHorizon   // ios: Data/AppModel.swift:685 키 시세 없는 종목은 전망에서도 뺀다
        val inp = mcInputs(true); if (bare || rs.isEmpty() || H <= today || inp == null) return null   // ios: Data/AppModel.swift:684 full
        // 전망 기준값 = 사이트 마지막 종가 × 매매기준율 (웹 buildModelNow 와 같다. 계산한 시각의 실시간 시세에 따라 웹·앱 숫자가 갈리지 않게).
        // 사이트에 없는 종목은 전일 종가, 매매기준율이 없으면 전일 환율: 키가 1분마다 바뀌어 계산이 끝없이 다시 돌지 않게 (PR #115 발열)
        val px = rs.map { r ->   // ios: Data/AppModel.swift:688
            val c = if (!Sample.isVirtual(r.id)) Market.shared.prices?.h?.get(SiteStats.key(r.id))?.closes?.lastOrNull() else null   // ios: Data/AppModel.swift:689
            if (c != null && c > 0) return@map c
            val q = r.sym.quote   // ios: Data/AppModel.swift:690
            if (q.prevClose > 0) q.prevClose else q.last   // ios: Data/AppModel.swift:691
        }
        val fx0 = Market.shared.marRate ?: Market.shared.fx.prevClose   // ios: Data/AppModel.swift:693
        val hs = rs.zip(px).map { (r, p) -> MC.Holding(key = SiteStats.mcKey(r.id), ccy = if (r.sym.currency == Currency.usd) "USD" else "KRW", valueKrw = r.h.qty * p * (if (r.sym.currency == Currency.usd) fx0 else 1.0), price0 = p) }   // ios: Data/AppModel.swift:694
        val st = inp.st; val settings = inp.settings; val events = inp.events   // ios: Data/AppModel.swift:695
        val snap = rs.zip(px).joinToString(",") { (r, p) -> "${SiteStats.mcKey(r.id)}:${r.h.qty}:${p}" }   // ios: Data/AppModel.swift:696
        val key = listOf(today, H, snap, fx0.toString(), inp.sig).joinToString("|")   // ios: Data/AppModel.swift:697
        return ForecastEngine.Job(key = key, holdings = hs, stats = st, settings = settings, events = events, start = today, horizon = H, goal = mcGoal, monthly = fcMonthly)   // ios: Data/AppModel.swift:698
    }

    /** 전망의 월 적립 (원): 웹처럼 목표의 매달 넣는 돈(monthly_contribution)을 넣는다. 목표 루트가 아니면 0. monthly 는 동기화 호환으로만 남긴다 */
    val fcMonthly: Double get() = if (isGoal) gM * 1e4 else 0.0   // ios: Data/AppModel.swift:702
    val forecast: Forecast get() = forecast(lens)   // ios: Data/AppModel.swift:703
    /** 시나리오 하나의 전망. 같은 경로를 μ만 바꿔 다시 무게를 달아서(reweight) 셋을 같이 불러도 싸다 */
    fun forecast(lens: Lens): Forecast {   // ios: Data/AppModel.swift:705
        val cf = closedForecast(lens)   // ios: Data/AppModel.swift:706
        val job = forecastJob ?: return cf   // ios: Data/AppModel.swift:707
        val eng = ForecastEngine.shared   // ios: Data/AppModel.swift:708
        eng.ensure(job)   // ios: Data/AppModel.swift:709
        val lensKey = if (lens == Lens.base) "base" else if (lens == Lens.smooth) "smooth" else "mine"   // ios: Data/AppModel.swift:710
        val run = eng.run; if (run == null || run.key != job.key) return cf   // ios: Data/AppModel.swift:711
        val r = eng.view(job.key, lensKey, trust, mcGoal, fcMonthly, shock) ?: return cf   // ios: Data/AppModel.swift:712 key · lens · trust · goal · monthly · withEvents
        val sim = run.ev; val s0 = MC.dayNum(sim.start)   // ios: Data/AppModel.swift:713
        val tY = sim.monthDates.map { (MC.dayNum(it) - s0).toDouble() / 365.25 }   // ios: Data/AppModel.swift:714
        val dates = sim.monthDates; val cum = r.cumHit; val V0 = r.V0; val K = mcGoal   // ios: Data/AppModel.swift:715
        // 연 기대 (웹 portMu): 계산 때 평가액 비중으로 종목 기대수익을 더한다
        val fs = run.factors.take(sim.A); val w = sim.v0.map { it / max(1.0, V0) }   // ios: Data/AppModel.swift:717
        fun wsum(f: (MC.Factor) -> Double): Double = fs.zip(w).fold(0.0) { a, x -> a + x.second * f(x.first) }   // ios: Data/AppModel.swift:718
        val mB = wsum { it.base }; val mS = wsum { it.smooth }; val mM = wsum { MC.blendMu(it.base, it.smooth, trust / 100) }   // ios: Data/AppModel.swift:719
        val prob = { T: Double ->   // ios: Data/AppModel.swift:720
            if (T <= 0) { if (V0 >= K) 1.0 else 0.0 } else {   // ios: Data/AppModel.swift:721
                val target = MC.addMonths(sim.start, (T * 12).rnd().toInt())   // ios: Data/AppModel.swift:722
                var k = 0; while (k + 1 < dates.size && dates[k + 1] <= target) k += 1   // ios: Data/AppModel.swift:723
                cum[k]   // ios: Data/AppModel.swift:724
            }
        }
        return Forecast(q5 = r.bands[0], q25 = r.bands[1], q50 = r.bands[2], q75 = r.bands[3], q95 = r.bands[4], dates = dates, tY = tY, prob = prob,   // ios: Data/AppModel.swift:726
                        pGoal = r.pGoal, byYear = r.byYear.map { YearP(year = it.year, p = it.p) },   // ios: Data/AppModel.swift:727
                        mu = if (lens == Lens.base) mB else if (lens == Lens.smooth) mS else mM, sigma = cf.sigma, muBase = mB, muTrend = mS, exact = true)   // ios: Data/AppModel.swift:728
    }

    private data class Part(val w: Double, val id: String, val p: Triple<Double, Double, Double>)
    /** 닫힌 식 (로그정규 근사): 몬테카를로가 끝나기 전 잠깐, 시작 전 루트, 주간 예보 폭(sigma) */
    fun closedForecast(lens: Lens): Forecast {   // ios: Data/AppModel.swift:732
        val rs = rows; val tot = max(1.0, total)   // ios: Data/AppModel.swift:733
        // 종목 값은 사이트가 매일 만드는 stats.json (웹 전망과 같은 μ·σ·상관). 달러 종목은 원/달러 흔들림도 더한다
        val st = Market.shared.stats; val t = trust / 100   // ios: Data/AppModel.swift:735
        val parts: MutableList<Part> = if (rs.isEmpty() || bare) mutableListOf(Part(1.0, "", Triple(0.07, 0.11, 0.16)))   // ios: Data/AppModel.swift:736
            else rs.map { r -> SiteStats.params(r.id, st).let { (a, b, c) -> Part(r.value / tot, r.id, Triple(a, b, c)) } }.toMutableList()   // ios: Data/AppModel.swift:737
        val usdW = if (rs.isEmpty() || bare) 0.0 else rs.filter { it.sym.currency == Currency.usd }.fold(0.0) { a, r -> a + r.value } / tot   // ios: Data/AppModel.swift:738
        if (usdW > 0) { val (f0, f1) = SiteStats.fx(st); parts.add(Part(usdW, "KRW=X", Triple(f0, f0, f1))) }   // ios: Data/AppModel.swift:739
        var muB = 0.0; var muS = 0.0; var muM = 0.0   // ios: Data/AppModel.swift:740
        for (x in parts) {   // ios: Data/AppModel.swift:741
            muB += x.w * x.p.first; muS += x.w * x.p.second   // ios: Data/AppModel.swift:742
            // 내 관점: 웹처럼 종목마다 현재 정세와 과거 추세를 로그 공간에서 섞는다
            val lb: Double = (1 - t) * ln(1 + x.p.first) + t * ln(1 + max(-0.99, x.p.second))   // ios: Data/AppModel.swift:744
            muM += x.w * (exp(lb) - 1)   // ios: Data/AppModel.swift:745
        }
        var v = 0.0   // ios: Data/AppModel.swift:747
        for (a in parts.indices) { for (b in parts.indices) {   // ios: Data/AppModel.swift:748
            val r: Double = if (a == b) 1.0 else if (parts[a].id.isEmpty()) 0.5 else SiteStats.corr(parts[a].id, parts[b].id, st)   // ios: Data/AppModel.swift:749
            v += parts[a].w * parts[b].w * parts[a].p.third * parts[b].p.third * r   // ios: Data/AppModel.swift:750
        } }
        val sg = sqrt(max(0.0, v))   // ios: Data/AppModel.swift:752
        val mu = if (lens == Lens.base) muB else if (lens == Lens.smooth) muS else muM   // ios: Data/AppModel.swift:753
        val g = mu - sg * sg / 2; val V0 = trackValue; val K = keyValue; val M = fcMonthly   // ios: Data/AppModel.swift:754
        val qAt = { T: Double, z: Double ->   // ios: Data/AppModel.swift:755
            val shockT: Double = z * sg * sqrt(T)   // ios: Data/AppModel.swift:756
            val lump: Double = V0 * exp(g * T + shockT)   // ios: Data/AppModel.swift:757
            val flow: Double = M * 12 * T * exp(g * T / 2 + shockT / 2)   // ios: Data/AppModel.swift:758
            lump + flow   // ios: Data/AppModel.swift:759
        }
        val pAt = fun(T: Double): Double {   // ios: Data/AppModel.swift:761
            if (T <= 0) return if (V0 >= K) 1.0 else 0.0   // ios: Data/AppModel.swift:762
            val eff: Double = V0 + M * 12 * T * exp(-g * T / 2)   // ios: Data/AppModel.swift:763
            if (eff <= 0) return 0.0   // ios: Data/AppModel.swift:764
            val num: Double = ln(eff / K) + g * T   // ios: Data/AppModel.swift:765
            return normCDF(num / (sg * sqrt(T)))   // ios: Data/AppModel.swift:766
        }
        val today = Day.today; val H = max(1.0 / 12, (MC.dayNum(forecastHorizon) - MC.dayNum(today)).toDouble() / 365.25)   // ios: Data/AppModel.swift:768
        val n = max(1, (H * 12).rnd().toInt())   // ios: Data/AppModel.swift:769
        val q = { z: Double -> (0..n).map { i -> if (i == 0) V0 else qAt(i.toDouble() / 12, z) } }   // ios: Data/AppModel.swift:770
        return Forecast(q5 = q(-1.645), q25 = q(-0.674), q50 = q(0.0), q75 = q(0.674), q95 = q(1.645),   // ios: Data/AppModel.swift:771
                        dates = (0..n).map { MC.addMonths(today, it) }, tY = (0..n).map { it.toDouble() / 12 }, prob = pAt,   // ios: Data/AppModel.swift:772
                        pGoal = pAt(H), byYear = (1..max(1, (H + 1.0 / 48).toInt())).map { YearP(year = it, p = pAt(min(H, it.toDouble()))) },   // ios: Data/AppModel.swift:773
                        mu = mu, sigma = sg, muBase = muB, muTrend = muS, exact = false)   // ios: Data/AppModel.swift:774
    }

    // 1000칸 (회복: 본전 = 1000칸)
    val cellUnit: Double get() = cost / 1000   // ios: Data/AppModel.swift:778
    fun cells(v: Double): Int = max(0, min(1000, floor(v / max(1.0, cellUnit)).toInt()))   // ios: Data/AppModel.swift:779
    val cellsNow: Int get() = cells(total)   // ios: Data/AppModel.swift:780
    /** 시작(3개월 전) 뒤 가장 낮았던 날: 지금 수량에 지난 3개월 종가를 대입해 찾는다 (과거 시세가 없으면 지금 가격) */
    val cellsFloor: Int get() = cells(min(total, (0..30).map { totalAt(1 - it.toDouble() / 360) }.minOrNull() ?: total))   // ios: Data/AppModel.swift:782
    val cellsYesterday: Int get() = cells(total / (1 + todayMove))   // ios: Data/AppModel.swift:783

    // 주간 예보 (월요일에 적은 금요일 평가액 50% 범위)
    data class WeekRange(val lo: Double, val hi: Double, val actual: Double)
    /** 이번 주에 적어 둔 범위가 있으면 그 값 (주중에 평가액이 움직여도 범위는 그대로), 없으면 지금 값으로 계산 */
    val weekRange: WeekRange get() {   // ios: Data/AppModel.swift:787
        val now = trackValue   // ios: Data/AppModel.swift:788
        Remind.shared.log.weeks[Remind.week(Instant.now())]?.let { w -> return WeekRange(w.lo, w.hi, now) }   // ios: Data/AppModel.swift:789
        val wSg = forecast.sigma / sqrt(52.0); val base = now / 1.004   // ios: Data/AppModel.swift:790
        return WeekRange(base * exp(-0.674 * wSg), base * exp(0.674 * wSg), now)   // ios: Data/AppModel.swift:791
    }
    /** 지난 주간 예보 성적 (1 = 범위 안, 오래된 순, 최근 8주). 기록이 없으면 비어 있다. 개발자 기기에서는 시연 값 */
    val pastWeeks: List<Int> get() {   // ios: Data/AppModel.swift:794
        val w = Remind.shared.log.weeks   // ios: Data/AppModel.swift:795
        val r = Remind.endedWeeks(w).takeLast(8).mapNotNull { k -> w[k]?.let { if (it.pos == "in") 1 else 0 } }   // ios: Data/AppModel.swift:796
        return if (r.isEmpty() && devAll) listOf(1, 0, 1, 1, 0, 1, 0, 1) else r   // ios: Data/AppModel.swift:797
    }
    /** 맞혀보기를 맞힌 횟수 (배지) */
    val guessHits: Int get() = Remind.shared.log.guessHits ?: 0   // ios: Data/AppModel.swift:800

    // 시안 조작
    enum class Demo { fresh, week1, all }   // ios: Data/AppModel.swift:803
    /** 첫 질문 답: 마이너스 = 회복 루트 미션 1부터, 플러스 = 목표 루트(샘플은 이익 난 단가), 시작 전 = 목표 루트 적립 */
    fun startRoute(r: Route) {   // ios: Data/AppModel.swift:805
        // 사용자가 넣은 종목이 있으면 루트를 다시 골라도 그대로 불러온다 (예시 종목은 처음일 때만)
        val mine: List<Holding>? = if (holdings.isEmpty() || isSample) null else holdings   // ios: Data/AppModel.swift:807
        resetDemo(Demo.fresh)   // ios: Data/AppModel.swift:808
        if (mine != null) holdings = mine   // ios: Data/AppModel.swift:809
        else if (r == Route.plus) holdings = listOf(Holding(symbol = "DRNK", qty = 4.0, avg = 180.0), if (Config.isInternal) Holding(symbol = "QQQ", qty = 1.0, avg = 400.0) else Holding(symbol = "NXIX", qty = 1.0, avg = 340.0))   // ios: Data/AppModel.swift:812
        if (r == Route.plus) taxGain = 0.0   // ios: Data/AppModel.swift:811
        if (r != Route.recover) { switchRoute(r); gDone = emptySet() }   // ios: Data/AppModel.swift:812
        day = 0                  // ios: Data/AppModel.swift:813 실제로 시작하는 날이 1일째 (시연 상태는 6일째)
        finishStart()   // ios: Data/AppModel.swift:814
        tab = Tab.board   // ios: Data/AppModel.swift:815
    }

    fun finishStart() {   // ios: Data/AppModel.swift:814
        onboarded = true; prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun resetDemo(d: Demo) {   // ios: Data/AppModel.swift:818
        holdings = Sample.startHoldings   // ios: Data/AppModel.swift:819
        when (d) {   // ios: Data/AppModel.swift:820
            Demo.fresh -> { done = emptySet(); weeks = emptyList(); nxStep = 1 }   // ios: Data/AppModel.swift:821
            Demo.week1 -> { done = setOf(1, 2, 3, 5); weeks = listOf(WeekPick.kept); nxStep = 3 }   // ios: Data/AppModel.swift:822
            Demo.all -> { done = setOf(1, 2, 3, 5, 6); weeks = listOf(WeekPick.kept, WeekPick.kept, WeekPick.changed, WeekPick.kept); nxStep = 3 }   // ios: Data/AppModel.swift:823
        }
        route = Route.recover; gDone = emptySet(); gWeeks = emptyList(); gWeekCur = null   // ios: Data/AppModel.swift:825
        weekCur = null; day = 5; dayLog = emptyMap(); itemsBack = 0; itemsBackDay = null; itemsOff = emptySet(); homeFriend = "seri"; readPos = emptySet(); readLast = null   // ios: Data/AppModel.swift:826
        interStartAt = null; goalStartAt = null; gInterStartAt = null; recM = 0   // ios: Data/AppModel.swift:827
        weekGuess = null; weekFridaySkip = false; planKey = "balance"; horizon = 3; quizAnswer = null   // ios: Data/AppModel.swift:828
        taxGain = 0.0; taxSellQty = 0.0; boardPath = emptyList()   // ios: Data/AppModel.swift:829
    }

    // 만들 때: 모든 저장 속성이 정해진 뒤 (Swift init 은 기본값 다음에 돈다). init 안에서 바로 넣는 값은 didSet 이 안 불린다
    init {   // ios: Data/AppModel.swift:33
        // 저장된 진행을 먼저 불러오고, 캡처용 실행 인자가 있으면 그 위에 시연 상태를 덮는다 (그때는 저장하지 않는다)
        if (persists) Progress.load()?.let { this.apply(it) }   // ios: Data/AppModel.swift:35
        if (isSample && holdings.any { it.qty != sampleQty(it.symbol) }) _holdings = holdings.map { Holding(symbol = it.symbol, qty = sampleQty(it.symbol), avg = it.avg) }   // ios: Data/AppModel.swift:36 예전 샘플(60주·10주)은 소액으로
        // 캡처용: --es demo fresh 로 미션 1부터 시작
        if (arg("demo") == "fresh") resetDemo(Demo.fresh)   // ios: Data/AppModel.swift:38
        if (arg("demo") == "all") resetDemo(Demo.all)   // ios: Data/AppModel.swift:39
        // 캡처용: --es goal novice|plus (목표 루트, 미션은 모두 끝낸 상태로)
        val g = arg("goal"); val gr = Route.entries.firstOrNull { it.name == g }   // ios: Data/AppModel.swift:41
        if (g != null && gr != null) {
            switchRoute(gr); gDone = goalSteps.filter { !it.inter }.map { it.id }.toSet(); nxStep = 3   // ios: Data/AppModel.swift:42
        }
        // 확인용: --es gY 3 → 목표 기간 (년)
        if (debug) {   // ios: Data/AppModel.swift:45
            arg("gY")?.toIntOrNull()?.let { gY = it }   // ios: Data/AppModel.swift:46
        }   // ios: Data/AppModel.swift:47
        // 확인용: --es addTest 005930,10,84000 → 종목 추가와 같은 경로로 넣는다
        arg("addTest")?.split(",")?.let { t -> if (t.size == 3) addHolding(t[0], t[1], t[2]) }   // ios: Data/AppModel.swift:49
        // 확인용: --es holdN 10 → 예시 종목 앞에서부터 n개를 보유로 (위젯에 종목이 많을 때 모습)
        if (debug) {   // ios: Data/AppModel.swift:51
            arg("holdN")?.toIntOrNull()?.let { n ->   // ios: Data/AppModel.swift:52
                _holdings = Sample.baseSymbols.take(n).mapIndexed { i, s ->   // ios: Data/AppModel.swift:53
                    Holding(symbol = s.id, qty = ceil(3e7 / (i + 1).toDouble() / (s.close * (if (s.currency == Currency.usd) Sample.fx else 1.0))), avg = s.close)   // ios: Data/AppModel.swift:54
                }
            }
        }   // ios: Data/AppModel.swift:57
        // 확인용: --es searchTest tesla → 종목 추가 화면을 그 검색어로 연다
        if (arg("searchTest") != null) holdPath = listOf("add")   // ios: Data/AppModel.swift:59
        // 확인용: --es syncTest <비밀번호> → 그 비밀번호로 동기화를 켠다 (사이트와 주고받기 확인)
        if (debug) {   // ios: Data/AppModel.swift:61
            arg("syncTest")?.let { pw -> if (!Sync.shared.isOn) MainScope().launch { Sync.shared.turnOn(pw, this@AppModel) } }   // ios: Data/AppModel.swift:62 password · model
            arg("syncDevTest")?.let { t -> if (!Sync.shared.isOn) MainScope().launch { Sync.shared.turnOnDev(t, this@AppModel) } }   // ios: Data/AppModel.swift:63 token · model
        }   // ios: Data/AppModel.swift:64
        // 캡처용: --es home shelter 또는 --es home char:ir
        arg("home")?.let { h -> homePath = if (h == "shelter") listOf("shelter") else listOf("shelter", h) }   // ios: Data/AppModel.swift:66
        arg("friend")?.let { f -> shelterSel = f }   // ios: Data/AppModel.swift:67
        arg("hold")?.let { k -> Sample.symbol(k)?.let { sy -> holdOpen = sy } }   // ios: Data/AppModel.swift:68
        val st: Map<String, SettingsRoute> = mapOf("alerts" to SettingsRoute.alerts, "sync" to SettingsRoute.sync, "widgets" to SettingsRoute.widgets, "tax" to SettingsRoute.tax, "price" to SettingsRoute.price, "howto" to SettingsRoute.howto, "route" to SettingsRoute.route, "goal" to SettingsRoute.goal, "charPreview" to SettingsRoute.charPreview)   // ios: Data/AppModel.swift:69
        arg("set")?.let { st[it] }?.let { r -> settingsPath = listOf(r) }   // ios: Data/AppModel.swift:70
        // 캡처용: --es route m3 처럼 미션 화면을 바로 연다
        val routes: Map<String, MissionRoute> = mapOf("m1" to MissionRoute.m1, "m1r" to MissionRoute.m1r, "m2" to MissionRoute.m2, "m2r" to MissionRoute.m2r, "m3" to MissionRoute.m3, "m3r" to MissionRoute.m3r, "m4" to MissionRoute.m4, "m4r" to MissionRoute.m4r, "nx" to MissionRoute.nx, "g1" to MissionRoute.g1, "g1r" to MissionRoute.g1r, "g3" to MissionRoute.g3, "g3r" to MissionRoute.g3r, "gt" to MissionRoute.gt, "gi" to MissionRoute.gi, "gp1r" to MissionRoute.gp1r)   // ios: Data/AppModel.swift:72
        arg("route")?.let { routes[it] }?.let { r -> boardPath = listOf(r) }   // ios: Data/AppModel.swift:73
        val an: Map<String, AnalysisRoute> = mapOf("forecast" to AnalysisRoute.forecast, "external" to AnalysisRoute.external, "dividend" to AnalysisRoute.dividend, "fx" to AnalysisRoute.fx, "glance" to AnalysisRoute.glance)   // ios: Data/AppModel.swift:74
        arg("an")?.let { an[it] }?.let { r -> analysisPath = listOf(r) }   // ios: Data/AppModel.swift:75
    }

    companion object {
        // 에뮬레이터 캡처용: adb shell am start -n com.naeilo.app/.MainActivity --es tab hold 처럼 시작 탭을 고를 수 있다
        // ios: App.swift:132,133
        fun launchTab(intent: Intent?): Tab = when (intent?.getStringExtra("tab")) {
            "hold" -> Tab.hold            // ios: App.swift:134
            "analysis" -> Tab.analysis    // ios: App.swift:135
            "board" -> Tab.board          // ios: App.swift:136
            else -> Tab.home              // ios: App.swift:137
        }

        /** 둘러보기 샘플 수량: DRNK 4주 (가장 큰 종목이 DRNK가 되게), QQQ 1주 */
        fun sampleQty(id: String): Double = if (id == "DRNK") 4.0 else 1.0   // ios: Data/AppModel.swift:119
        fun savedHoldings(prefs: SharedPreferences): List<Holding>? =   // ios: Data/AppModel.swift:120
            prefs.getString("holdings", null)?.let { runCatching { NJson.decodeFromString<List<Holding>>(it) }.getOrNull() }?.filter { it.symbol != KoreaEstimate.sumId }   // ios: Data/AppModel.swift:121

        // 형식
        /** 앱 언어가 영어면 금액은 만·억 대신 기기 서식으로 (₩2.3M). 한 번만 읽는다 */
        val en: Boolean by lazy {   // ios: Data/AppModel.swift:184
            val v = Locale.getDefault().language == "en"   // ios: Data/AppModel.swift:185
            Store.defaults.edit().putString("appLang", if (v) "en" else "ko").apply()   // ios: Data/AppModel.swift:186 위젯이 같은 언어로 보이게
            v   // ios: Data/AppModel.swift:187,188
        }
        /** 날짜 글자(오전·오후, 달 이름)도 앱 언어를 따른다 */
        val dateLocale: Locale by lazy { Locale.forLanguageTag((if (en) "en_US" else "ko_KR").replace('_', '-')) }   // ios: Data/AppModel.swift:190
        fun krwC(krw: Double): String {   // ios: Data/AppModel.swift:191
            val a = abs(krw); val u = listOf(Pair(1e12, "T"), Pair(1e9, "B"), Pair(1e6, "M"), Pair(1e3, "K"))   // ios: Data/AppModel.swift:192
            val s = u.firstOrNull { a >= it.first }?.let { sig(a / it.first, 1..3) + it.second } ?: a.rnd().toInt().fmt()   // ios: Data/AppModel.swift:193
            return (if (krw < 0) "−" else "") + "₩" + s   // ios: Data/AppModel.swift:194
        }
        /** 1억 이상은 "5억 2,528만"처럼 억과 만을 나눠 쓴다 (만 단위에 다섯 자리 숫자는 읽기 어렵다). 만·억 뒤 "원"은 뺀다 (글줄이기) */
        fun man(krw: Double): String {   // ios: Data/AppModel.swift:197
            if (en) return krwC(krw)   // ios: Data/AppModel.swift:198
            val n = (krw / 1e4).rnd().toInt()   // ios: Data/AppModel.swift:199
            val a = abs(n); val sign = if (n < 0) "−" else ""   // ios: Data/AppModel.swift:200
            if (a < 10_000) return sign + a.fmt() + "만"   // ios: Data/AppModel.swift:201
            val eok = a / 10_000; val rest = a % 10_000   // ios: Data/AppModel.swift:202
            return sign + "${eok.fmt()}억" + (if (rest > 0) " ${rest.fmt()}만" else "")   // ios: Data/AppModel.swift:203
        }
        /** 웹 krw()와 같은 표시: 1억 넘으면 소수 둘째 자리 억 (5.25억), 1만 넘으면 만 (826만). 내 길·전망·홈 평가액 */
        fun eok(krw: Double): String {   // ios: Data/AppModel.swift:206
            if (en) return krwC(krw)   // ios: Data/AppModel.swift:207
            val a = abs(krw); val s = if (krw < 0) "−" else ""   // ios: Data/AppModel.swift:208
            if (a >= 1e8) return s + fmtF(if (a >= 1e10) "%.0f억" else "%.2f억", a / 1e8)   // ios: Data/AppModel.swift:209
            if (a >= 1e4) return s + (a / 1e4).rnd().toInt().fmt() + "만"   // ios: Data/AppModel.swift:210
            return s + a.rnd().toInt().fmt() + "원"   // ios: Data/AppModel.swift:211
        }
        /** 부호 붙인 금액 (+826만, −826만) */
        fun eokS(krw: Double): String = (if (krw >= 0) "+" else "−") + eok(abs(krw))   // ios: Data/AppModel.swift:214
        /** 남은 기간 (웹 목표 진행과 같은 표시): 1년 넘으면 소수 한 자리 년, 안 되면 개월·일 */
        fun leftText(y: Double): String {   // ios: Data/AppModel.swift:216
            if (y <= 0) return "지남"   // ios: Data/AppModel.swift:217
            if (y >= 1) return fmtF("%.1f년", y)   // ios: Data/AppModel.swift:218
            if (y * 12 >= 1) return "${(y * 12).toInt()}개월"   // ios: Data/AppModel.swift:219
            return "${ceil(y * 365.25).toInt()}일"   // ios: Data/AppModel.swift:220
        }
        /** 퍼센트와 금액을 함께: "−2.1% · 약 1,060만원". 금액 = base × rate, 만원 반올림, 1억 이상은 "약 1.2억원" */
        fun pctWon(rate: Double, base: Double): String {   // ios: Data/AppModel.swift:223
            val p = (if (rate >= 0) "+" else "−") + fmtF("%.1f%%", abs(rate) * 100)   // ios: Data/AppModel.swift:224
            return p + " · 약 " + aboutWon(base * rate)   // ios: Data/AppModel.swift:225
        }
        /** 금액 어림: 만원 반올림, 1억 이상은 "1.2억원" (부호 없음) */
        fun aboutWon(krw: Double): String {   // ios: Data/AppModel.swift:228
            val a = abs(krw)   // ios: Data/AppModel.swift:229
            if (en) return krwC(a)   // ios: Data/AppModel.swift:230
            return if (a >= 1e8) fmtF("%.1f억", a / 1e8) else (a / 1e4).rnd().toInt().fmt() + "만"   // ios: Data/AppModel.swift:231
        }
        /** 아직 값이 없으면 0 대신 "—" */
        fun dash(v: String, ready: Boolean): String = if (ready) v else "—"   // ios: Data/AppModel.swift:234
        fun pct(p: Double): String = "${(p * 100).rnd().toInt()}%"   // ios: Data/AppModel.swift:235
        fun sgn(p: Double): String = (if (p >= 0) "+" else "−") + fmtF("%.1f%%", abs(p) * 100)   // ios: Data/AppModel.swift:236
        /** 종목 하루 등락: 소수 둘째 자리 (종목 목록·오늘 많이 움직인 종목·말풍선이 같은 자릿수) */
        fun sgn2(p: Double): String = (if (p >= 0) "+" else "−") + fmtF("%.2f%%", abs(p) * 100)   // ios: Data/AppModel.swift:238
        fun sgn0(p: Double): String = (if (p >= 0) "+" else "−") + "${(abs(p) * 100).rnd().toInt()}%"   // ios: Data/AppModel.swift:239
        fun price(s: Symbol, x: Double): String =   // ios: Data/AppModel.swift:240
            if (s.currency == Currency.usd) "$" + x.usdText(0, 2) else x.rnd().toInt().fmt() + "원"   // ios: Data/AppModel.swift:241 (0...2 자리, 십진 반올림은 swFmt)

        // 회복 계획 (계산은 Recovery)
        fun normCDF(x: Double): Double {   // ios: Data/AppModel.swift:313
            val t = 1 / (1 + 0.2316419 * abs(x)); val d = 0.3989423 * exp(-x * x / 2)   // ios: Data/AppModel.swift:314
            val p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))   // ios: Data/AppModel.swift:315
            return if (x > 0) 1 - p else p   // ios: Data/AppModel.swift:316
        }

        fun won(krw: Double): String = if (en) krwC(abs(krw)) else if (abs(krw) < 1e4) abs(krw).rnd().toInt().fmt() + "원" else wonK(abs(krw) / 1e4)   // ios: Data/AppModel.swift:460

        /** 세 칸 보기: 실제 값에 가장 가까운 칸이 정답 (step 간격, lo~hi 안). 정답 칸은 날마다·질문마다 옮겨 '가운데만 고르기'로 안 맞게 */
        data class Near3(val opts: List<Double>, val right: Int)
        private fun near3(x: Double, step: Double, lo: Double, hi: Double, salt: Int = 0): Near3 {   // ios: Data/AppModel.swift:502
            val c = (x / step).rnd() * step; val k = (quizDay + salt) % 3   // ios: Data/AppModel.swift:503
            val s = min(hi - 2 * step, max(lo, c - k.toDouble() * step))   // ios: Data/AppModel.swift:504
            val o = listOf(s, s + step, s + 2 * step)   // ios: Data/AppModel.swift:505
            return Near3(o, o.indices.minByOrNull { abs(o[it] - x) } ?: 1)   // ios: Data/AppModel.swift:506
        }
        /** 서기 1년 1월 1일부터 셈한 오늘 (Calendar.ordinality(of: .day, in: .era)) */
        private val quizDay: Int get() = (LocalDate.now().toEpochDay() + 719_163).toInt().takeIf { it > 0 } ?: 0   // ios: Data/AppModel.swift:508
    }
}
