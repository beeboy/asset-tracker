package com.naeilo.app.mock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.NJson
import com.naeilo.app.core.cformat
import com.naeilo.app.core.feedJson
import com.naeilo.app.core.jObj
import com.naeilo.app.core.TossKeys
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Notifier
import com.naeilo.app.data.Support
import com.naeilo.app.net.KrxList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 모의투자 저장 상태. 기록 줄은 따로 journal.jsonl 에 차례로 쌓는다 */
@Serializable
data class MockState(   // ios: Mock/MockDesk.swift:8
    var ledgers: MutableList<MockLedger>,   // ios: Mock/MockDesk.swift:9
    var archived: MutableList<MockLedger> = mutableListOf(),   // ios: Mock/MockDesk.swift:10
    var bundles: MutableList<MockBundle>,   // ios: Mock/MockDesk.swift:11
    var current: String? = null,   // ios: Mock/MockDesk.swift:12
    var exceptions: MockExceptions = MockExceptions(),   // ios: Mock/MockDesk.swift:13
    var select: MockSelectRule = MockSelectRule(),   // ios: Mock/MockDesk.swift:14
    var sell: MockSellRule = MockSellRule(),   // ios: Mock/MockDesk.swift:15
    var fees: MockFees = MockFees(),   // ios: Mock/MockDesk.swift:16
    var scan: MockScan? = null,   // ios: Mock/MockDesk.swift:17
    var scanEvent: String? = null,   // ios: Mock/MockDesk.swift:18
    var picks: MutableList<MockCalc.Pick> = mutableListOf(),   // ios: Mock/MockDesk.swift:19
    var picksBook: MockBook? = null,   // ios: Mock/MockDesk.swift:20
    var pickEvents: MutableMap<String, String> = mutableMapOf(),   // ios: Mock/MockDesk.swift:21
    var skips: MutableList<MockCalc.Skip> = mutableListOf(),   // ios: Mock/MockDesk.swift:22
    var pickedAt: MockDate? = null,   // ios: Mock/MockDesk.swift:23
    var exceptHits: MutableMap<String, String> = mutableMapOf(),   // ios: Mock/MockDesk.swift:24
    var runs: MutableMap<String, MockRun> = mutableMapOf(),   // ios: Mock/MockDesk.swift:25
    var auto: Int = 0,   // ios: Mock/MockDesk.swift:26
    var prices: MutableMap<String, Double> = mutableMapOf(),   // ios: Mock/MockDesk.swift:27
    var pricesAt: MockDate? = null,   // ios: Mock/MockDesk.swift:28
    var session: MockFeed.Session? = null,   // ios: Mock/MockDesk.swift:29
    var shown: MockBook = MockBook.rule,   // ios: Mock/MockDesk.swift:30
    var startReal: Boolean? = null,   // ios: Mock/MockDesk.swift:31
    var ai: MockAISettings = MockAISettings(),   // ios: Mock/MockDesk.swift:32
    var aiSeen: MutableMap<String, MockDate> = mutableMapOf(),   // ios: Mock/MockDesk.swift:33
    var aiWallet: MockAIWallet? = null,   // ios: Mock/MockDesk.swift:34
    var aiViews: MutableMap<String, MockAIView> = mutableMapOf(),   // ios: Mock/MockDesk.swift:35
    var aiNote: String? = null,   // ios: Mock/MockDesk.swift:36
    var aiDaily: String? = null,   // ios: Mock/MockDesk.swift:37
    var aiDailyDay: String? = null,   // ios: Mock/MockDesk.swift:38
    var reviewShut: Boolean? = null,   // ios: Mock/MockDesk.swift:39
    var conds: MutableList<MockCond>? = null,   // ios: Mock/MockDesk.swift:40
    var rconds: MutableList<MockRuleCond>? = null,   // ios: Mock/MockDesk.swift:41
    var sconds: MutableList<MockSellCond>? = null,   // ios: Mock/MockDesk.swift:42
    var marks: MutableList<MockMark>? = null,   // ios: Mock/MockDesk.swift:43
    var dropped: MutableList<MockCalc.Pick>? = null,   // ios: Mock/MockDesk.swift:44
    var aiPurged: Boolean? = null,   // ios: Mock/MockDesk.swift:45
    var surgeAdded: Boolean? = null,   // ios: Mock/MockDesk.swift:46
    var seen: MutableMap<String, List<MockSeen>>? = null,   // ios: Mock/MockDesk.swift:47
    var hidden: MutableList<String>? = null,   // ios: Mock/MockDesk.swift:48
    var cycleSell: Boolean? = null,   // ios: Mock/MockDesk.swift:49
    var cycleOrder: Boolean? = null,   // ios: Mock/MockDesk.swift:50
    var autoOrder: Boolean? = null,   // ios: Mock/MockDesk.swift:51
    var slipAdded: Boolean? = null,   // ios: Mock/MockDesk.swift:52
    var guards: MockGuards? = null,   // ios: Mock/MockDesk.swift:53
    var live: Boolean? = null,   // ios: Mock/MockDesk.swift:54
    var halt: Boolean? = null,   // ios: Mock/MockDesk.swift:55
    var autoSell: Boolean? = null,   // ios: Mock/MockDesk.swift:56
    var checks: MutableMap<String, MockRun>? = null,   // ios: Mock/MockDesk.swift:57
    var checkDay: String? = null,   // ios: Mock/MockDesk.swift:58
    var fillAlert: Boolean? = null,   // ios: Mock/MockDesk.swift:59
    var curves: MutableList<MockCurve>? = null,   // ios: Mock/MockDesk.swift:60
    var bigCodes: List<String>? = null,   // ios: Mock/MockDesk.swift:61
    var curveTried: String? = null,   // ios: Mock/MockDesk.swift:62
    var curveNote: String? = null,   // ios: Mock/MockDesk.swift:63
    var near: List<String>? = null,   // ios: Mock/MockDesk.swift:64
    var nearKey: String? = null,   // ios: Mock/MockDesk.swift:65
    var fullAt: MockDate? = null,   // ios: Mock/MockDesk.swift:66
    var firstHit: MutableMap<String, MockDate>? = null,   // ios: Mock/MockDesk.swift:67
    var firstHitDay: String? = null,   // ios: Mock/MockDesk.swift:68
    var warns: MutableMap<String, List<String>>? = null,   // ios: Mock/MockDesk.swift:69
    var warnsAt: MockDate? = null,   // ios: Mock/MockDesk.swift:70
    var realBaseDay: String? = null,   // ios: Mock/MockDesk.swift:71
    var realBase: Double? = null,   // ios: Mock/MockDesk.swift:72
    var dayTrade: Boolean? = null,   // ios: Mock/MockDesk.swift:73
    var sellCheck: Boolean? = null,   // ios: Mock/MockDesk.swift:74
    var gone: MutableMap<String, String>? = null,   // ios: Mock/MockDesk.swift:75
    var follows: MutableList<MockFollow>? = null,   // ios: Mock/MockDesk.swift:76
    var afters: MutableList<MockAfter>? = null,   // ios: Mock/MockDesk.swift:77
    var flowReads: MutableList<MockFlowRead>? = null,   // ios: Mock/MockDesk.swift:78
    var weather: MutableMap<String, Double>? = null,   // ios: Mock/MockDesk.swift:79
    var weatherAt: MockDate? = null,   // ios: Mock/MockDesk.swift:80
    var armTouched: MutableList<String>? = null,   // ios: Mock/MockDesk.swift:81
    var newVol: Boolean? = null,   // ios: Mock/MockDesk.swift:82
) {
    companion object {
        fun fresh(now: MockDate): MockState {   // ios: Mock/MockDesk.swift:84
            val a = MockBundle(name = "A · 거래량 급증", pieces = listOf(MockPiece(kind = MockPiece.Kind.volume, lo = 2.0, days = 20), MockPiece(kind = MockPiece.Kind.change, lo = 2.0, hi = 8.0), MockPiece(kind = MockPiece.Kind.ma, days = 20)))   // ios: Mock/MockDesk.swift:85
            val b = MockBundle(name = "B · 20일선 위", pieces = listOf(MockPiece(kind = MockPiece.Kind.ma, days = 20), MockPiece(kind = MockPiece.Kind.amount, lo = 10.0)), sort = MockPiece.Kind.amount)   // ios: Mock/MockDesk.swift:86
            return MockState(ledgers = mutableListOf(MockLedger(book = MockBook.rule, started = now), MockLedger(book = MockBook.ai, started = now), MockLedger(book = MockBook.real, started = now)), bundles = mutableListOf(a, b), current = a.id)   // ios: Mock/MockDesk.swift:87
        }
    }
}

/** 모의투자 실행기. 장부를 바꾸는 곳은 여기 하나뿐이다 (두 곳이 같은 장부를 고치지 않게). 화면은 읽기만 한다.
 *  화면은 rev 를 읽어 다시 그린다 (save·log 가 올린다) */
object MockDesk {   // ios: Mock/MockDesk.swift:93
    val shared: MockDesk get() = this   // ios: Mock/MockDesk.swift:94
    private val scope = MainScope()
    private var rev by mutableIntStateOf(0)

    /** 투자 모드 (홈 평가액 박스 금액 글자 밖을 누르면 바뀐다) */
    private var onState by mutableStateOf(Defaults.prefs.getBoolean("mock.on", false))   // ios: Mock/MockDesk.swift:97
    var on: Boolean
        get() = onState
        set(v) { onState = v; Defaults.prefs.edit().putBoolean("mock.on", v).apply() }   // ios: Mock/MockDesk.swift:98
    /** 설정 > 내 Toss API 키 > 모의투자 켜기. 허용만 하고, 탭 전환은 홈 평가액 박스로 한다 */
    private var allowedState by mutableStateOf(Defaults.prefs.getBoolean("mock.allowed", false))   // ios: Mock/MockDesk.swift:101
    var allowed: Boolean
        get() = allowedState
        set(v) { allowedState = v; Defaults.prefs.edit().putBoolean("mock.allowed", v).apply(); if (!v) on = false }   // ios: Mock/MockDesk.swift:102
    /** 평가액 박스로 투자 모드에 들어갈 수 있나: 허용 + (개발자 기기 또는 토스 키). 개발자도 먼저 허용해야 한다 (2026-11-05) */
    val canEnter: Boolean get() = allowed && (Support.shared.dev || TossKeys.on)   // ios: Mock/MockDesk.swift:105
    /** 모의는 개발자 기기 또는 토스 키 사용자, 실전은 개발자 기기만 (2026-11-05 결정: 일반 사용자에겐 모의만) */
    val active: Boolean get() = on && canEnter   // ios: Mock/MockDesk.swift:107
    val realOK: Boolean get() = Support.shared.dev   // ios: Mock/MockDesk.swift:108

    private var _s: MockState = MockState.fresh(mockNow())
    val s: MockState get() { rev; return _s }   // ios: Mock/MockDesk.swift:110
    private var _events: MutableList<MockEvent> = mutableListOf()
    val events: List<MockEvent> get() { rev; return _events }   // ios: Mock/MockDesk.swift:111
    /** 지금 도는 일 ("observe" 등). 하나가 도는 동안 다른 일은 받지 않는다 */
    var busy: String? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:113
        private set
    /** 1분 주기가 도는 중 (busy 와 따로: 버튼을 잠그지 않는다) */
    var ticking by mutableStateOf(false)   // ios: Mock/MockDesk.swift:115
        private set
    /** AI 부르는 중 (서버가 Claude 를 기다리는 동안 다른 일은 막지 않는다. AI 끼리는 하나씩) */
    var aiBusy: String? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:117
        private set
    var progress: Pair<Int, Int>? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:118
        private set
    /** 앱에 넣은 내 보유 종목 (예외 조건 '내 보유 종목 전부') */
    var mine: Set<String> = emptySet()   // ios: Mock/MockDesk.swift:120
    /** 주문층. 판단 코드는 이것만 부른다. 장부마다 따로: 모의 장부는 늘 모의 브로커, 실전 장부는 토스 브로커
     *  (실전 스위치가 꺼져 있으면 토스 브로커는 아무것도 보내지 않는다) */
    private val paper = MockPaperBroker()   // ios: Mock/MockDesk.swift:123
    private val toss = MockTossBroker()   // ios: Mock/MockDesk.swift:124
    fun broker(b: MockBook): MockBroker = if (b == MockBook.real) toss else paper   // ios: Mock/MockDesk.swift:125
    val live: Boolean get() = s.live == true && realOK   // ios: Mock/MockDesk.swift:126

    /** [모의]·[실전] 칸. 탭 머리 전환 아이콘으로 바꾸면 세 탭이 같이 따른다 (앱을 켤 때는 설정의 시작 칸으로) */
    val shown: MockBook get() = if (s.shown == MockBook.real && realOK) MockBook.real else MockBook.rule   // ios: Mock/MockDesk.swift:129
    fun show(b: MockBook) {   // ios: Mock/MockDesk.swift:130
        if (b == s.shown || !(b != MockBook.real || realOK)) return   // ios: Mock/MockDesk.swift:131
        setView { it.shown = b }   // ios: Mock/MockDesk.swift:132
        if (b == MockBook.real) scope.launch { refreshAccount() }   // ios: Mock/MockDesk.swift:133
    }
    /** 자동 판단(돌리기·자동 팔기)을 돌릴 장부: 모의는 늘, 실전은 실전 스위치가 켜졌을 때만.
     *  보이는 장부를 마지막에 돌려 검토 대기 종목이 보이는 칸 몫으로 남게 한다 */
    val autoBooks: List<MockBook> get() {   // ios: Mock/MockDesk.swift:137
        val bs: List<MockBook> = if (live) listOf(MockBook.rule, MockBook.real) else listOf(MockBook.rule)   // ios: Mock/MockDesk.swift:138
        return bs.filter { it != shown } + bs.filter { it == shown }   // ios: Mock/MockDesk.swift:139
    }
    /** 보이는 칸 몫의 검토 대기 종목 */
    val picks: List<MockCalc.Pick> get() = if ((s.picksBook ?: MockBook.rule) == shown) s.picks else emptyList()   // ios: Mock/MockDesk.swift:142
    /** 서버 실전 준비가 안 된 이유 (null 이면 준비됨). 실전 칸이 열릴 때 checkLive 로 다시 묻는다 */
    var liveBlock: String? by mutableStateOf("확인 전")   // ios: Mock/MockDesk.swift:144
        private set
    suspend fun checkLive() { liveBlock = TossOrderAPI.ready() }   // ios: Mock/MockDesk.swift:145
    /** 계좌 탭·[실전] 칸: 서버 실전 상태 · 토스 계좌 (실전 스위치가 켜졌거나 [실전] 칸일 때만 계좌를 묻는다. 주기적으로 묻지 않는다). 기기 메모리에만 */
    var ready: TossOrderAPI.Ready? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:147
        private set
    var account: TossOrderAPI.Account? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:148
        private set
    var accountWhy: String? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:149
        private set
    suspend fun refreshAccount() {   // ios: Mock/MockDesk.swift:150
        if (MockFeed.base == null) { ready = null; accountWhy = "서버 주소 없음"; return }   // ios: Mock/MockDesk.swift:151
        ready = TossOrderAPI.readyInfo()   // ios: Mock/MockDesk.swift:152
        liveBlock = ready?.let { if (!it.keys) "서버에 토스 키 없음" else if (!it.account) "서버에 계좌 없음" else if (!it.armed) "서버 실전 꺼짐" else null } ?: (if (ready == null) "서버에 닿지 못함" else null)   // ios: Mock/MockDesk.swift:153
        if (!(live || shown == MockBook.real)) { account = null; accountWhy = null; return }   // ios: Mock/MockDesk.swift:154
        val (a, why) = TossOrderAPI.account()   // ios: Mock/MockDesk.swift:155
        if (a != null) {   // ios: Mock/MockDesk.swift:156
            account = a   // ios: Mock/MockDesk.swift:157
            // [실전] 하루 손실 한도 기준: 어제 마감 기준 = 지금 평가액 − 토스 오늘 손익 (몇 시에 열어도 같다)
            // 옛 서버라 오늘 손익이 없으면 그날 처음 읽은 토스 계좌 평가액
            val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:160
            val eq = realEquity   // ios: Mock/MockDesk.swift:161
            if (eq != null) {
                val pl = a.dayPl   // ios: Mock/MockDesk.swift:162
                if (pl != null) {
                    if (s.realBaseDay != today || s.realBase != eq - pl) { s.realBaseDay = today; s.realBase = eq - pl; save() }   // ios: Mock/MockDesk.swift:163
                } else if (s.realBaseDay != today) { s.realBaseDay = today; s.realBase = eq; save() }   // ios: Mock/MockDesk.swift:164
            }
        }
        accountWhy = why   // ios: Mock/MockDesk.swift:167
    }

    /** 실전 켜기·끄기 (화면에서 확인을 거친 뒤에만 부른다). 대기 주문이 있으면 바꾸지 않는다 */
    fun setLive(on: Boolean): String? {   // ios: Mock/MockDesk.swift:172
        if (s.ledgers.any { it.pending.isNotEmpty() }) return "대기 주문을 먼저 정리해 주세요"   // ios: Mock/MockDesk.swift:173
        if (on && !realOK) return "실전은 개발자 기기에서만 켤 수 있어요"   // ios: Mock/MockDesk.swift:174
        val why = liveBlock; if (on && why != null) return "실전 못 켬 · " + why   // ios: Mock/MockDesk.swift:175
        edit(if (on) "실전 켬 · 토스 브로커" else "실전 끔 · 모의 브로커") { it.live = if (on) true else null }   // ios: Mock/MockDesk.swift:176
        toss.armed = on   // ios: Mock/MockDesk.swift:177
        return null   // ios: Mock/MockDesk.swift:178
    }

    /** 받는 중인 관찰 일봉 (멈췄다 이어 받기). 기기 메모리에만 둔다: 기준일·봉 수·종목 수가 같아야 이어 쓴다 */
    private class Partial(   // ios: Mock/MockDesk.swift:182
        var key: String,   // ios: Mock/MockDesk.swift:183
        val bars: MutableMap<String, List<MockBar>> = mutableMapOf(),   // ios: Mock/MockDesk.swift:184
        val errors: MutableMap<String, String> = mutableMapOf(),   // ios: Mock/MockDesk.swift:185
        val done: MutableSet<Int> = mutableSetOf(),   // ios: Mock/MockDesk.swift:186
        var hitN: Int = 0,   // ios: Mock/MockDesk.swift:187
    ) {
        val n: Int get() = bars.size + errors.size   // ios: Mock/MockDesk.swift:188
    }
    private var partial: Partial? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:190
    private var partialRev by mutableIntStateOf(0)
    /** 훑는 중 전광판: 받은 묶음까지 걸린 수 · 못 받은 수 */
    val scanLive: Pair<Int, Int>? get() { partialRev; return partial?.let { it.hitN to it.errors.size } }   // ios: Mock/MockDesk.swift:192
    /** 멈춘 관찰 (어디서 시작했나: observe · cycle · auto). 앱이 다시 앞에 오면 이어 받는다 */
    var paused: String? by mutableStateOf(null)   // ios: Mock/MockDesk.swift:194
        private set
    private var obsFrom = "observe"   // ios: Mock/MockDesk.swift:195
    /** 마지막으로 고른 종목 (검토 문이 열려 있으면 picks 가 바로 비므로 AI 의견용으로 따로 둔다) */
    var lastPicks: List<MockCalc.Pick> by mutableStateOf(emptyList())   // ios: Mock/MockDesk.swift:197
        private set

    /** 옛 묶음 조각을 관찰 조건 알약으로, 선정 규칙 값을 고르기 조건 알약으로 옮긴다 (한 번만, 묶음 데이터는 그대로 둔다) */
    private fun migrate() {   // ios: Mock/MockDesk.swift:219
        var changed = false   // ios: Mock/MockDesk.swift:220
        if (s.conds == null) {   // ios: Mock/MockDesk.swift:221
            val cur = s.bundles.firstOrNull { it.id == s.current } ?: s.bundles.firstOrNull()   // ios: Mock/MockDesk.swift:222
            val seen = mutableSetOf<String>(); val out = mutableListOf<MockCond>()   // ios: Mock/MockDesk.swift:223
            for (b in (cur?.let { listOf(it) } ?: emptyList()) + s.bundles.filter { it.id != cur?.id }) {   // ios: Mock/MockDesk.swift:224
                for (p in b.pieces) { if (seen.contains(p.text)) continue   // ios: Mock/MockDesk.swift:225
                    seen.add(p.text)   // ios: Mock/MockDesk.swift:226
                    out.add(MockCond(piece = p, on = b.id == cur?.id))   // ios: Mock/MockDesk.swift:227
                }
            }
            s.conds = out   // ios: Mock/MockDesk.swift:230
            changed = true   // ios: Mock/MockDesk.swift:231
        }
        if (s.rconds == null) {   // ios: Mock/MockDesk.swift:233
            val cur = s.bundles.firstOrNull { it.id == s.current } ?: s.bundles.firstOrNull()   // ios: Mock/MockDesk.swift:234
            s.rconds = mutableListOf(MockRuleCond(kind = MockRuleCond.Kind.top, sort = cur?.sort ?: MockPiece.Kind.volume, n = s.select.count),   // ios: Mock/MockDesk.swift:235
                        MockRuleCond(kind = MockRuleCond.Kind.per, pct = s.select.maxPer),   // ios: Mock/MockDesk.swift:236
                        MockRuleCond(kind = MockRuleCond.Kind.cash, pct = s.select.keepCash))   // ios: Mock/MockDesk.swift:237
            changed = true   // ios: Mock/MockDesk.swift:238
        }
        if (s.sconds == null) {   // ios: Mock/MockDesk.swift:240
            // 설정에 있던 팔기 규칙을 알약으로 옮긴다 (꺼져 있던 것은 꺼진 알약으로)
            s.sconds = mutableListOf(MockSellCond(kind = MockSellCond.Kind.loss, on = s.sell.stopLoss != null, pct = s.sell.stopLoss ?: 0.05),   // ios: Mock/MockDesk.swift:242
                        MockSellCond(kind = MockSellCond.Kind.gain, on = s.sell.takeProfit != null, pct = s.sell.takeProfit ?: 0.10),   // ios: Mock/MockDesk.swift:243
                        MockSellCond(kind = MockSellCond.Kind.days, on = s.sell.maxDays != null, n = s.sell.maxDays ?: 5))   // ios: Mock/MockDesk.swift:244
            changed = true   // ios: Mock/MockDesk.swift:245
        }
        // 비정상 폭등 거르기 (일봉 거래량·종가로만 셈). 관찰은 넓게, 고르기는 더 좁게. 켠 채로 한 번만 넣는다
        if (s.surgeAdded != true) {   // ios: Mock/MockDesk.swift:248
            s.conds = ((s.conds ?: mutableListOf()) + listOf(MockCond(piece = MockPiece(kind = MockPiece.Kind.volume, hi = 50.0, days = 20)),   // ios: Mock/MockDesk.swift:249
                                         MockCond(piece = MockPiece(kind = MockPiece.Kind.rise, hi = 30.0, days = 5)))).toMutableList()   // ios: Mock/MockDesk.swift:250
            s.rconds = ((s.rconds ?: mutableListOf()) + listOf(MockRuleCond(kind = MockRuleCond.Kind.cap, capKind = MockPiece.Kind.volume, lim = 20.0),   // ios: Mock/MockDesk.swift:251
                                           MockRuleCond(kind = MockRuleCond.Kind.cap, capKind = MockPiece.Kind.rise, lim = 20.0))).toMutableList()   // ios: Mock/MockDesk.swift:252
            s.surgeAdded = true   // ios: Mock/MockDesk.swift:253
            changed = true   // ios: Mock/MockDesk.swift:254
        }
        // 미끄러짐 한도 0.5% (켠 채로 한 번만 넣는다)
        if (s.slipAdded != true) {   // ios: Mock/MockDesk.swift:257
            s.rconds = ((s.rconds ?: mutableListOf()) + listOf(MockRuleCond(kind = MockRuleCond.Kind.slip, pct = 0.005))).toMutableList()   // ios: Mock/MockDesk.swift:258
            s.select.slip = 0.005   // ios: Mock/MockDesk.swift:259
            s.slipAdded = true   // ios: Mock/MockDesk.swift:260
            changed = true   // ios: Mock/MockDesk.swift:261
        }
        // 직접 검토 설정을 뺐다 (2026-10-26). 옛 검토 켬 사용자가 돌리기에서 갑자기 자동 주문되지 않게 한 번 옮긴다
        if (s.autoOrder == null) {   // ios: Mock/MockDesk.swift:264
            s.autoOrder = !s.select.review   // ios: Mock/MockDesk.swift:265
            changed = true   // ios: Mock/MockDesk.swift:266
        }
        if (s.dayTrade != true) { toDayTrade(); changed = true }   // ios: Mock/MockDesk.swift:268
        // 단타 기준은 2026-10-29에 섰다. 그 전에 산 몫이 새 규칙에 팔리지 않게 한 번 적는다
        if (s.sell.since == null) { s.sell.since = "2026-10-29"; changed = true }   // ios: Mock/MockDesk.swift:270
        if (changed) save()   // ios: Mock/MockDesk.swift:271
    }

    /** 단타 기준으로 한 번 옮긴다 (2026-10-29). 하루 안에 끝내고, 종목 판단은 관찰 한 곳, 선정은 돈만.
     *  팔기: '보유 n일' → 움직임 없음 60분 ±1% · 이유 꺼짐 · 15:00 마감 정리 · 되밀림 2% (켬) · 흔들림 손절 ×1 (꺼 둠)
     *  고르기: 폭등 거르기 → 관찰 조건의 위 문턱으로 (더 좁은 값) · 나눠 사기 34% (켬). 안전장치: 시장 날씨 −1% (켬) */
    private fun toDayTrade() {   // ios: Mock/MockDesk.swift:277
        val sc = (s.sconds ?: mutableListOf()).filter { it.kind != MockSellCond.Kind.days }.toMutableList()   // ios: Mock/MockDesk.swift:278
        val add: List<Triple<MockSellCond.Kind, Boolean, Pair<Double, Int>>> = listOf(Triple(MockSellCond.Kind.trail, true, 0.02 to 0), Triple(MockSellCond.Kind.idle, true, 0.01 to 60), Triple(MockSellCond.Kind.gone, true, 0.0 to 0), Triple(MockSellCond.Kind.close, true, 0.0 to 0), Triple(MockSellCond.Kind.vol, false, 1.0 to 0))   // ios: Mock/MockDesk.swift:279
        for ((k, on, pn) in add) { if (sc.any { it.kind == k }) continue   // ios: Mock/MockDesk.swift:280
            sc.add(MockSellCond(kind = k, on = on, pct = pn.first, n = pn.second))   // ios: Mock/MockDesk.swift:281
        }
        val cs = (s.conds ?: mutableListOf()).map { it.copy(piece = it.piece.copy()) }.toMutableList()   // ios: Mock/MockDesk.swift:283
        for (c in (s.rconds ?: mutableListOf())) { if (!(c.kind == MockRuleCond.Kind.cap && c.on)) continue   // ios: Mock/MockDesk.swift:284
            val kind: MockPiece.Kind = if (c.capKind == MockPiece.Kind.rise) MockPiece.Kind.rise else MockPiece.Kind.volume; val days = if (kind == MockPiece.Kind.rise) 5 else 20   // ios: Mock/MockDesk.swift:285
            val i = cs.indexOfFirst { it.piece.kind == kind && it.piece.days == days && it.piece.hi != null }
            if (i >= 0) {   // ios: Mock/MockDesk.swift:286
                cs[i].piece.hi = min(cs[i].piece.hi ?: c.lim, c.lim); cs[i].on = true   // ios: Mock/MockDesk.swift:287
            } else {   // ios: Mock/MockDesk.swift:288
                cs.add(MockCond(piece = MockPiece(kind = kind, hi = c.lim, days = days)))   // ios: Mock/MockDesk.swift:289
            }
        }
        val rc = (s.rconds ?: mutableListOf()).filter { it.kind != MockRuleCond.Kind.cap }.toMutableList()   // ios: Mock/MockDesk.swift:292
        if (!rc.any { it.kind == MockRuleCond.Kind.split }) rc.add(MockRuleCond(kind = MockRuleCond.Kind.split, pct = 0.34))   // ios: Mock/MockDesk.swift:293
        s.conds = cs   // ios: Mock/MockDesk.swift:294
        applyRule(s, rc)   // ios: Mock/MockDesk.swift:295
        applySell(s, sc)   // ios: Mock/MockDesk.swift:296
        val g = guards.copy()   // ios: Mock/MockDesk.swift:297
        if (g.marketDrop == null) g.marketDrop = 0.01   // ios: Mock/MockDesk.swift:298
        s.guards = g   // ios: Mock/MockDesk.swift:299
        s.dayTrade = true   // ios: Mock/MockDesk.swift:300
        log(MockEvent.Kind.rules, book = MockBook.rule, text = "단타 기준으로 바꿈 · 팔기 '보유 n일' 뺌 · 마감 정리·이유 꺼짐·움직임 없음·되밀림 넣음 · 폭등 거르기는 관찰 조건으로 · 나눠 사기 · 시장 날씨")   // ios: Mock/MockDesk.swift:301
    }

    /** AI 장부의 매매 데이터와 AI 기록을 지운다 (규칙 장부는 그대로). 기록 파일도 그 줄만 빼고 다시 쓴다 */
    private fun purgeAI() {   // ios: Mock/MockDesk.swift:305
        val now = mockNow()   // ios: Mock/MockDesk.swift:306
        val aiIds = (s.ledgers.filter { it.book == MockBook.ai }.map { it.id } + s.archived.filter { it.book == MockBook.ai }.map { it.id }).toSet()   // ios: Mock/MockDesk.swift:307
        val i = s.ledgers.indexOfFirst { it.book == MockBook.ai }; if (i >= 0) s.ledgers[i] = MockLedger(book = MockBook.ai, started = now)   // ios: Mock/MockDesk.swift:308
        s.archived.removeAll { it.book == MockBook.ai }   // ios: Mock/MockDesk.swift:309
        s.aiNote = null; s.aiDaily = null; s.aiDailyDay = null; s.aiViews = mutableMapOf(); s.aiSeen = mutableMapOf(); s.aiWallet = null   // ios: Mock/MockDesk.swift:310
        s.runs.remove("ai")   // ios: Mock/MockDesk.swift:311
        s.shown = MockBook.rule   // ios: Mock/MockDesk.swift:312
        val kept = _events.filter { !aiIds.contains(it.ledger) && it.book != MockBook.ai && it.kind != MockEvent.Kind.ai && it.who != MockWho.ai }   // ios: Mock/MockDesk.swift:313
        if (kept.size != _events.size) {   // ios: Mock/MockDesk.swift:314
            _events = kept.toMutableList()   // ios: Mock/MockDesk.swift:315
            rewriteLines(kept)   // ios: Mock/MockDesk.swift:316
        }
        s.aiPurged = true   // ios: Mock/MockDesk.swift:318
        save()   // ios: Mock/MockDesk.swift:319
    }

    /** 상태 파일 꼴이 바뀌어 못 읽으면 그대로 두지 않고 옆에 옮겨 둔다 (새 상태가 옛 장부를 덮지 않게) */
    private fun keepBroken() {   // ios: Mock/MockDesk.swift:323
        val f = File(dir, "state.json")   // ios: Mock/MockDesk.swift:324
        if (!f.exists()) return   // ios: Mock/MockDesk.swift:325
        val to = File(dir, "state-unread-${mockNow().toLong()}.json")   // ios: Mock/MockDesk.swift:326
        runCatching { f.renameTo(to) }   // ios: Mock/MockDesk.swift:327
    }

    // MARK: 읽기

    fun ledger(b: MockBook): MockLedger = s.ledgers.firstOrNull { it.book == b } ?: MockLedger(book = b, started = mockNow())   // ios: Mock/MockDesk.swift:332
    /** 관찰에 쓰는 묶음: 켠 조건 알약 모두 (줄 세우기는 고르기 조건의 순서) */
    val bundle: MockBundle? get() {   // ios: Mock/MockDesk.swift:334
        val on = conds.filter { it.on }.map { it.piece }   // ios: Mock/MockDesk.swift:335
        val top = rconds.firstOrNull { it.on && it.kind == MockRuleCond.Kind.top }   // ios: Mock/MockDesk.swift:336
        return MockBundle(id = obsId, name = "조건 ${on.size}개", pieces = on, scope = MockBundle.Scope.all, sort = top?.sort ?: MockPiece.Kind.volume, sortDays = 20)   // ios: Mock/MockDesk.swift:337
    }
    val obsId: String = "00000000-0000-0000-0000-0000000000B5"   // ios: Mock/MockDesk.swift:339
    val conds: List<MockCond> get() = s.conds ?: emptyList()   // ios: Mock/MockDesk.swift:340
    val rconds: List<MockRuleCond> get() = s.rconds ?: emptyList()   // ios: Mock/MockDesk.swift:341
    val sconds: List<MockSellCond> get() = s.sconds ?: emptyList()   // ios: Mock/MockDesk.swift:342
    val dropped: List<MockCalc.Pick> get() = s.dropped ?: emptyList()   // ios: Mock/MockDesk.swift:343
    val marks: List<MockMark> get() = marks(shown)   // ios: Mock/MockDesk.swift:344
    fun marks(b: MockBook): List<MockMark> { val id = ledger(b).id; return (s.marks ?: emptyList()).filter { it.ledger == id } }   // ios: Mock/MockDesk.swift:345
    /** 평가액. [실전]은 토스 계좌 평가액 (계좌를 못 읽었으면 장부로 셈한다. 판단에는 realEquity 를 쓴다) */
    fun equity(b: MockBook): Double {   // ios: Mock/MockDesk.swift:347
        val eq = realEquity; if (b == MockBook.real && eq != null) return eq   // ios: Mock/MockDesk.swift:348
        return MockCalc.equity(ledger(b), prices = s.prices)   // ios: Mock/MockDesk.swift:349
    }
    /** [실전] 토스 계좌 평가액: 주문가능 현금 + 보유 × 지금 가격 (가격이 없으면 토스 평균가). 계좌를 못 읽었으면 null */
    val realEquity: Double? get() {   // ios: Mock/MockDesk.swift:352
        val a = account ?: return null   // ios: Mock/MockDesk.swift:353
        return (a.cash ?: 0.0) + a.items.fold(0.0) { acc, it -> acc + it.qty.toDouble() * (s.prices[it.code] ?: it.avg ?: 0.0) }   // ios: Mock/MockDesk.swift:354
    }
    /** 수익률 그래프 맨 앞 기준: [실전]은 첫 평가액 점 (없으면 null), [모의]는 시작 현금 */
    fun firstEquity(b: MockBook): Double? = if (b == MockBook.real) marks(b).minByOrNull { it.at }?.eq else ledger(b).startCash   // ios: Mock/MockDesk.swift:357
    fun ledgerEvents(b: MockBook): List<MockEvent> { val id = ledger(b).id; return events.filter { it.ledger == id } }   // ios: Mock/MockDesk.swift:358

    /** 예외 종목 → 이유. 직접 넣은 것, 내 보유 종목 전부, 조건 묶음에 걸린 것 */
    fun excluded(extra: Map<String, String>? = null): Map<String, String> {   // ios: Mock/MockDesk.swift:361
        val out = (extra ?: s.exceptHits).toMutableMap()   // ios: Mock/MockDesk.swift:362
        if (s.exceptions.mine) { for (c in mine) if (out[c] == null) out[c] = "내 보유 종목" }   // ios: Mock/MockDesk.swift:363
        for (d in s.exceptions.direct) out[d.code] = "직접 · " + d.name   // ios: Mock/MockDesk.swift:364
        return out   // ios: Mock/MockDesk.swift:365
    }

    val universe: List<MockStock> by lazy { KrxList.all.map { MockStock(code = it.hit.id, name = it.hit.name, kospi = it.hit.market == "코스피") } }   // ios: Mock/MockDesk.swift:368
    fun name(code: String): String = KrxList.name(code) ?: code   // ios: Mock/MockDesk.swift:369
    /** 팔기 칸 한 줄: "대원제약 마감 정리, 삼성전자 손절" */
    fun sigNames(sigs: List<MockCalc.SellSig>): String =   // ios: Mock/MockDesk.swift:371
        sigs.joinToString(", ") { name(it.code) + " " + (MockCalc.exitName(it.why) ?: "") }   // ios: Mock/MockDesk.swift:372

    // MARK: 장 상태

    enum class Phase { open, closed, unknown }   // ios: Mock/MockDesk.swift:377
    var phase: Phase by mutableStateOf(Phase.unknown)   // ios: Mock/MockDesk.swift:378
        private set

    /** 장 일정을 10분마다 다시 받는다. 못 받으면 '확인 못 함' (새로 사지 않는다) */
    suspend fun refreshPhase(now: MockDate = mockNow()): Phase {   // ios: Mock/MockDesk.swift:382
        val today = MockFmt.day(now)   // ios: Mock/MockDesk.swift:383
        val stale = s.session?.let { it.day != today || now - it.got > 600 } ?: true   // ios: Mock/MockDesk.swift:384
        if (stale) { val ss = try { MockFeed.calendar() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (ss != null) s.session = ss }   // ios: Mock/MockDesk.swift:385
        // 받은 지 30분이 넘었는데 새로 못 받았으면 모른다고 둔다
        val ss = s.session; if (ss == null || now - ss.got >= 1800) { phase = Phase.unknown; return phase }   // ios: Mock/MockDesk.swift:387
        val o = ss.open; val c = ss.close
        phase = if (ss.day == today && o != null && c != null && now >= o && now < c) Phase.open else Phase.closed   // ios: Mock/MockDesk.swift:388
        return phase   // ios: Mock/MockDesk.swift:389
    }

    /** 지금 하루 구간. 장 일정이 닫힘인데 시각이 장중이면 휴장 */
    fun seg(now: MockDate = mockNow()): MockClock.Seg {   // ios: Mock/MockDesk.swift:393
        val g = MockClock.seg(now)   // ios: Mock/MockDesk.swift:394
        if (phase == Phase.closed && (g == MockClock.Seg.watch || g == MockClock.Seg.pick || g == MockClock.Seg.tidy)) return MockClock.Seg.holiday   // ios: Mock/MockDesk.swift:395
        return g   // ios: Mock/MockDesk.swift:396
    }

    // MARK: 관찰

    /** 훑기 조건 표시 (조건·범위가 바뀌면 가까운 종목을 다시 잰다) */
    private fun nearKey(b: MockBundle): String = b.scope.name + "|" + b.pieces.joinToString("|") { it.text } + (if (s.newVol == true) "|new" else "")   // ios: Mock/MockDesk.swift:402

    /** 이번 훑기가 전체인가: 오늘 첫 훑기 · 조건이 바뀜 · 가까운 종목 없음 · 전체 간격이 지남 */
    fun fullDue(b: MockBundle, now: MockDate = mockNow()): Boolean {   // ios: Mock/MockDesk.swift:405
        if (wantFull) return true   // ios: Mock/MockDesk.swift:406
        val at = s.fullAt; if (at == null || MockFmt.day(at) != MockFmt.day(now) || s.nearKey != nearKey(b) || (s.near ?: emptyList()).isEmpty()) return true   // ios: Mock/MockDesk.swift:407
        // 장 전에 한 전체 훑기는 지난 봉 기준이라, 9:00이 지나면 한 번 더 전체
        if (MockClock.minute(now) >= MockClock.watchFrom && MockClock.minute(at) < MockClock.watchFrom) return true   // ios: Mock/MockDesk.swift:409
        return now - at >= MockClock.fullEvery   // ios: Mock/MockDesk.swift:410
    }

    /** 관찰 탭 한 줄: 다음 훑기가 전체인지 가까운 종목만인지, 몇 종목인지, 다음 전체 시각 */
    val scanPlan: String? get() {   // ios: Mock/MockDesk.swift:414
        val b = bundle; if (b == null || b.pieces.isEmpty()) return null   // ios: Mock/MockDesk.swift:415
        // 현황판 오른쪽 짧은 안내: 국내 전체 · 또는 N종목만 · 다음 전체 시각
        if (fullDue(b)) return "국내 전체"   // ios: Mock/MockDesk.swift:417
        val next = (s.fullAt ?: mockNow()) + MockClock.fullEvery   // ios: Mock/MockDesk.swift:418
        return "${(s.near ?: emptyList()).size.fmtInt()}종목만 · ${MockFmt.time(next)}"   // ios: Mock/MockDesk.swift:419
    }

    /** '전체 훑기' 단추로 건 다음 전체 바퀴. 전체 바퀴가 끝날 때까지 남는다 (멈췄다 이어 받아도 전체로) */
    var wantFull by mutableStateOf(false)   // ios: Mock/MockDesk.swift:423
        private set
    /** 지금 가까운 종목만 훑을 차례인가 ('전체 훑기' 단추를 보일지) */
    val nearOnly: Boolean get() = bundle?.let { !fullDue(it) } ?: false   // ios: Mock/MockDesk.swift:425

    suspend fun runObserve(full: Boolean = false) {   // ios: Mock/MockDesk.swift:427
        if (!waitTick() || busy != null) return   // ios: Mock/MockDesk.swift:428
        if (full) wantFull = true   // ios: Mock/MockDesk.swift:429
        busy = "observe"   // ios: Mock/MockDesk.swift:430
        keepAlive("observe")   // ios: Mock/MockDesk.swift:431
        busy = null   // ios: Mock/MockDesk.swift:432
        save()   // ios: Mock/MockDesk.swift:433
    }

    /** 단추로 시작한 훑기. 안드로이드는 앱 밖에서 계속 받지 않는다: 화면을 떠나 끊기면 멈춤으로 두고 돌아오면 이어 받는다 */
    private suspend fun keepAlive(from: String) {   // ios: Mock/MockDesk.swift:437
        obsFrom = from   // ios: Mock/MockDesk.swift:438
        val t = scope.launch { observe() }   // ios: Mock/MockDesk.swift:439
        t.join()
    }

    /** 앱이 다시 앞에 왔을 때: 멈춘 훑기를 남은 묶음부터 이어 받는다 (자동은 다음 1분 주기가 잇는다) */
    suspend fun resume() {   // ios: Mock/MockDesk.swift:451
        val p = paused; if (p == null || busy != null) return   // ios: Mock/MockDesk.swift:452
        when (p) {   // ios: Mock/MockDesk.swift:453
            "observe" -> runObserve()   // ios: Mock/MockDesk.swift:454
            "cycle" -> runCycle()   // ios: Mock/MockDesk.swift:455
            else -> {}   // ios: Mock/MockDesk.swift:456
        }
    }

    private suspend fun observe() {   // ios: Mock/MockDesk.swift:460
        val b = bundle   // ios: Mock/MockDesk.swift:461
        if (b == null || b.pieces.isEmpty()) {
            s.runs["observe"] = MockRun(at = mockNow(), ok = false, text = "${MockFmt.time(mockNow())} 켠 조건이 없어요")   // ios: Mock/MockDesk.swift:462
            return   // ios: Mock/MockDesk.swift:463
        }
        try { observeBody(b) } finally { progress = null }   // ios: Mock/MockDesk.swift:465
    }
    private suspend fun observeBody(b: MockBundle) {
        armNewVol()   // ios: Mock/MockDesk.swift:466
        // 오늘 첫 훑기·간격마다는 전체, 그 사이는 조건 문턱에 가까운 종목만 (MockClock.fullEvery · nearMin)
        val t0 = mockNow(); val full = fullDue(b, now = t0); val nearSet = (s.near ?: emptyList()).toSet()   // ios: Mock/MockDesk.swift:468
        // 규칙이 든 종목은 가까운 종목이 아니어도 늘 함께 받는다 (산 이유를 다시 잰다)
        val held = s.ledgers.flatMap { l -> l.positions.values.filter { it.who != MockWho.hand }.map { it.code } }.toSet()   // ios: Mock/MockDesk.swift:470
        val stocks = if (full) universe else universe.filter { nearSet.contains(it.code) || held.contains(it.code) }; val codes = stocks.map { it.code }   // ios: Mock/MockDesk.swift:471
        val need = max(61, (listOf(b) + s.exceptions.bundles).maxOfOrNull { it.need } ?: 0); val count = min(200, need)   // ios: Mock/MockDesk.swift:472
        // 같은 날·같은 봉 수·같은 방식이면 멈춘 데서 이어 받는다 (기준일이 바뀌면 처음부터)
        val key = "${MockFmt.day(mockNow())}|$count|${if (full) "all" else "near"}|${codes.size}"
        if (partial?.key != key) partial = Partial(key = key)   // ios: Mock/MockDesk.swift:475
        progress = (partial?.n ?: 0) to codes.size   // ios: Mock/MockDesk.swift:476
        val exNow = excluded().keys.toSet(); val fr = MockCalc.frac(s.curves ?: emptyList(), at = mockNow()); val useNew = s.newVol == true   // ios: Mock/MockDesk.swift:477
        try {   // ios: Mock/MockDesk.swift:478
            MockFeed.daily(codes, count = count, skip = partial?.done?.toSet() ?: emptySet(),   // ios: Mock/MockDesk.swift:479
                                         progress = { n -> progress = n to codes.size },   // ios: Mock/MockDesk.swift:480
                                         batch = { i, bars, errs ->   // ios: Mock/MockDesk.swift:481
                                             partial?.bars?.putAll(bars)   // ios: Mock/MockDesk.swift:482
                                             val got = bars.keys.toSet()   // ios: Mock/MockDesk.swift:483
                                             partial?.let { it.hitN += MockCalc.scan(b, stocks = stocks.filter { s0 -> got.contains(s0.code) }, bars = bars, excluded = exNow, at = mockNow(), frac = fr, useNew = useNew).hits.size }   // ios: Mock/MockDesk.swift:484
                                             partial?.errors?.putAll(errs)   // ios: Mock/MockDesk.swift:485
                                             partial?.done?.add(i)   // ios: Mock/MockDesk.swift:486
                                             partialRev++
                                         })
            val got = partial ?: Partial(key = key)   // ios: Mock/MockDesk.swift:488
            partial = null; paused = null   // ios: Mock/MockDesk.swift:489
            val exHits = MockCalc.exceptionHits(s.exceptions.bundles, stocks = stocks, bars = got.bars)   // ios: Mock/MockDesk.swift:490
            val ex = excluded(extra = exHits)   // ios: Mock/MockDesk.swift:491
            var scan = MockCalc.scan(b, stocks = stocks, bars = got.bars, excluded = ex.keys.toSet(), at = mockNow(), seconds = mockNow() - t0,   // ios: Mock/MockDesk.swift:492
                                     frac = MockCalc.frac(s.curves ?: emptyList(), at = mockNow()), useNew = useNew)   // ios: Mock/MockDesk.swift:493
            scan.full = full   // ios: Mock/MockDesk.swift:494
            if (got.errors.isNotEmpty()) scan.fails = got.errors.values.groupingBy { it }.eachCount()   // ios: Mock/MockDesk.swift:495
            if (full) {   // ios: Mock/MockDesk.swift:496
                // 전체 바퀴: 가까운 종목 줄을 새로 세운다 (새로 가까워진 종목이 들어온다)
                s.bigCodes = MockCalc.big(got.bars, n = 20)   // ios: Mock/MockDesk.swift:498
                s.near = MockCalc.near(b, stocks = stocks, bars = got.bars, excluded = ex.keys.toSet(), use = if (useNew) scan.frac else null)   // ios: Mock/MockDesk.swift:499
                s.nearKey = nearKey(b); s.fullAt = mockNow(); wantFull = false   // ios: Mock/MockDesk.swift:500
            }
            val mode = if (full) " · 전체" else " · 가까운 종목만"   // ios: Mock/MockDesk.swift:502
            val pt = scan.pieceTexts
            val dropText = scan.dropped.entries.sortedBy { it.key }.joinToString(" · ") { (k, n) -> (if (k < 0) "자료 없음" else (if (k < pt.size) pt[k] else "?")) + " $n" }   // ios: Mock/MockDesk.swift:503
            val ev = log(MockEvent.Kind.scan, book = MockBook.rule, text = "${b.name} · 걸린 ${scan.hits.size} / ${scan.total}" + mode + " · 기준 ${scan.barDay}" + (if (dropText.isEmpty()) "" else " · 떨어진 " + dropText) + newText(scan), bundle = b.name)   // ios: Mock/MockDesk.swift:504
            if (scan.excluded.isNotEmpty()) {   // ios: Mock/MockDesk.swift:505
                val why = scan.excluded.groupBy { (ex[it] ?: "").split(" · ").firstOrNull() ?: "" }.map { "${it.key} ${it.value.size}" }.joinToString(" · ")   // ios: Mock/MockDesk.swift:506
                log(MockEvent.Kind.exclude, book = MockBook.rule, text = "예외로 뺀 ${scan.excluded.size} · " + why, bundle = b.name, ref = ev.id)   // ios: Mock/MockDesk.swift:507
            }
            s.scanEvent = ev.id; s.exceptHits = exHits.toMutableMap()   // ios: Mock/MockDesk.swift:509
            addSeen(scan)   // ios: Mock/MockDesk.swift:510
            // 15분 재확인: 장중에 처음 걸린 시각 (장 밖 훑기는 지난 봉이라 세지 않는다)
            val today = MockFmt.day(scan.at)   // ios: Mock/MockDesk.swift:512
            if (s.firstHitDay != today) { s.firstHit = mutableMapOf(); s.firstHitDay = today }   // ios: Mock/MockDesk.swift:513
            if (phase == Phase.open) { for (h in scan.hits) if (s.firstHit?.get(h.code) == null) s.firstHit?.set(h.code, scan.at) }   // ios: Mock/MockDesk.swift:514
            for (h in scan.hits) s.prices[h.code] = h.price   // ios: Mock/MockDesk.swift:515
            // 산 이유 다시 재기: 든 종목이 켠 관찰 조건에서 약한 쪽으로 벗어났나 (봉을 못 받은 종목은 모른다)
            val gone = mutableMapOf<String, String>()   // ios: Mock/MockDesk.swift:517
            for (c in held) { val bs = got.bars[c]; val w = bs?.let { MockCalc.weak(b, it, use = if (useNew) scan.frac else null) }; if (w != null) gone[c] = w }   // ios: Mock/MockDesk.swift:518
            s.gone = gone   // ios: Mock/MockDesk.swift:519
            refreshWeather()   // ios: Mock/MockDesk.swift:520
            // 종목 판단은 여기 한 곳 (2026-10-29): 거래대금 바닥 · 15분 재확인 · 유의사항 · 시장 날씨. 선정은 돈만 본다
            // 뒤 흐름은 거르기 전 걸린 종목 전부를 적는다 (2026-10-30, 거른 종목이 빠져 '안 산' 쪽이 0으로 남던 것)
            addFollows(scan)   // ios: Mock/MockDesk.swift:523
            scan = judge(scan)   // ios: Mock/MockDesk.swift:524
            s.scan = scan   // ios: Mock/MockDesk.swift:525
            val sk = scan.skipped; if (sk != null && sk.isNotEmpty()) {   // ios: Mock/MockDesk.swift:526
                val why = sk.groupBy { it.why.split(" · ").firstOrNull() ?: "" }.map { "${it.key} ${it.value.size}" }.sorted().joinToString(" · ")   // ios: Mock/MockDesk.swift:527
                log(MockEvent.Kind.exclude, book = MockBook.rule, text = "고르기 전에 거름 ${sk.size} · " + why, bundle = b.name, ref = ev.id)   // ios: Mock/MockDesk.swift:528
            }
            val errN = got.errors.size   // ios: Mock/MockDesk.swift:530
            s.runs["observe"] = MockRun(at = mockNow(), ok = errN < codes.size / 10,   // ios: Mock/MockDesk.swift:531
                                        text = "${MockFmt.time(mockNow())} 훑음" + mode + " ${scan.total.fmtInt()} · ${scan.seconds.toInt()}초 · 일봉 기준 ${scan.barDay}" + (if (errN > 0) " · 못 받은 $errN" else ""))   // ios: Mock/MockDesk.swift:532
        } catch (e: CancellationException) {   // ios: Mock/MockDesk.swift:533
            // 취소(앱 나감·화면 전환)는 오류가 아니다. 받은 묶음은 두고 멈춤으로
            paused = obsFrom   // ios: Mock/MockDesk.swift:535
            s.runs["observe"] = MockRun(at = mockNow(), ok = false, text = "${MockFmt.time(mockNow())} 멈춤 · 돌아오면 이어 받아요 (${(partial?.n ?: 0).fmtInt()} / ${codes.size.fmtInt()})")   // ios: Mock/MockDesk.swift:536
        } catch (e: Exception) {   // ios: Mock/MockDesk.swift:537
            val why = (e as? MockFeed.Fail)?.why ?: (e.localizedMessage ?: e.toString())   // ios: Mock/MockDesk.swift:538
            s.runs["observe"] = MockRun(at = mockNow(), ok = false, text = "${MockFmt.time(mockNow())} 실패 · " + why)   // ios: Mock/MockDesk.swift:539
            log(MockEvent.Kind.error, book = MockBook.rule, text = "관찰 실패 · " + why)   // ios: Mock/MockDesk.swift:540
        }
    }

    /** 종목 판단: 걸린 종목에서 거래대금 바닥 · 15분 재확인 · 유의사항 · 시장 날씨로 거른다. 거른 종목은 이유와 함께 skipped 에 */
    private suspend fun judge(raw: MockScan): MockScan {   // ios: Mock/MockDesk.swift:545
        val sc = raw.copy(); val out = mutableListOf<MockCalc.Skip>()   // ios: Mock/MockDesk.swift:546
        val today = MockFmt.day(raw.at); val floor = MockClock.amountFloor; val first = if (s.firstHitDay == today) (s.firstHit ?: emptyMap()) else emptyMap()   // ios: Mock/MockDesk.swift:547
        sc.hits = raw.hits.filter { h ->   // ios: Mock/MockDesk.swift:548
            val amt = h.amt20 ?: h.amount ?: 0.0   // ios: Mock/MockDesk.swift:549
            if (amt < floor) {   // ios: Mock/MockDesk.swift:550
                out.add(MockCalc.Skip(code = h.code, name = h.name, why = "거래대금 바닥 · 20일 평균 ${MockFmt.num((amt * 10).swRound() / 10)}억 < ${MockFmt.num(floor)}억", hit = h))   // ios: Mock/MockDesk.swift:551
                return@filter false   // ios: Mock/MockDesk.swift:552
            }
            if (phase != Phase.open) return@filter true   // ios: Mock/MockDesk.swift:554
            val t = first[h.code]; if (t == null || raw.at - t < MockClock.recheck) {   // ios: Mock/MockDesk.swift:555
                val from = first[h.code]?.let { " · ${MockFmt.time(it + MockClock.recheck)}부터" } ?: ""   // ios: Mock/MockDesk.swift:556
                out.add(MockCalc.Skip(code = h.code, name = h.name, why = "재확인 대기" + from, hit = h))   // ios: Mock/MockDesk.swift:557
                return@filter false   // ios: Mock/MockDesk.swift:558
            }
            true   // ios: Mock/MockDesk.swift:560
        }
        refreshWarns(sc)   // ios: Mock/MockDesk.swift:562
        sc.hits = sc.hits.filter { h ->   // ios: Mock/MockDesk.swift:563
            val why = MockCalc.warnBlock(s.warns?.get(h.code) ?: emptyList()) ?: return@filter true   // ios: Mock/MockDesk.swift:564
            out.add(MockCalc.Skip(code = h.code, name = h.name, why = why, hit = h))   // ios: Mock/MockDesk.swift:565
            false   // ios: Mock/MockDesk.swift:566
        }
        val drop = guards.marketDrop; if (drop != null) {   // ios: Mock/MockDesk.swift:568
            sc.hits = sc.hits.filter { h ->   // ios: Mock/MockDesk.swift:569
                val m = market(h.code)   // ios: Mock/MockDesk.swift:570
                val w = s.weather?.get(m); if (w == null || w > -drop) return@filter true   // ios: Mock/MockDesk.swift:571
                out.add(MockCalc.Skip(code = h.code, name = h.name, why = "시장 약함 · " + (if (m == "kospi") "코스피 " else "코스닥 ") + MockFmt.pct(w, digits = 1), hit = h))   // ios: Mock/MockDesk.swift:572
                false   // ios: Mock/MockDesk.swift:573
            }
        }
        sc.skipped = out   // ios: Mock/MockDesk.swift:576
        return sc   // ios: Mock/MockDesk.swift:577
    }

    /** 시장 날씨는 지수 대신 ETF 로 잰다 (KODEX 200 · KODEX 코스닥150). 시세 길이 종목과 같아 따로 받을 곳이 없다 */
    val weatherCodes: Map<String, String> = mapOf("kospi" to "069500", "kosdaq" to "229200")   // ios: Mock/MockDesk.swift:581
    val kospiCodes: Set<String> by lazy { universe.filter { it.kospi }.map { it.code }.toSet() }   // ios: Mock/MockDesk.swift:582
    fun market(code: String): String = if (kospiCodes.contains(code)) "kospi" else "kosdaq"   // ios: Mock/MockDesk.swift:583

    /** 오늘 시장 날씨. 오늘 봉이 없으면(장 전) 모른다로 두고 거르지 않는다 */
    private suspend fun refreshWeather() {   // ios: Mock/MockDesk.swift:586
        val r = try { MockFeed.daily(weatherCodes.values.toList(), count = 2, progress = { _ -> }) } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return   // ios: Mock/MockDesk.swift:587
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:588
        val w = mutableMapOf<String, Double>()   // ios: Mock/MockDesk.swift:589
        for ((k, c) in weatherCodes) {   // ios: Mock/MockDesk.swift:590
            val bs = r.bars[c]; val last = bs?.lastOrNull(); if (bs == null || bs.size < 2 || last == null || last.d != today || bs[bs.size - 2].c <= 0) continue   // ios: Mock/MockDesk.swift:591
            w[k] = last.c / bs[bs.size - 2].c - 1   // ios: Mock/MockDesk.swift:592
        }
        s.weather = if (w.isEmpty()) null else w   // ios: Mock/MockDesk.swift:594
        s.weatherAt = if (w.isEmpty()) null else mockNow()   // ios: Mock/MockDesk.swift:595
    }

    /** 걸린 종목 뒤 흐름: 장중에 오늘 처음 걸린 종목을 거르기 전에 적어 둔다 (하루 100종목까지, 14일 지난 것은 버림). 값은 1분 주기가 채운다 */
    private fun addFollows(sc: MockScan) {   // ios: Mock/MockDesk.swift:599
        if (phase != Phase.open) return   // ios: Mock/MockDesk.swift:600
        val day = MockFmt.day(sc.at); val cut = MockFmt.day(sc.at + (-14 * 86_400))   // ios: Mock/MockDesk.swift:601
        val fs = (s.follows ?: mutableListOf()).filter { it.day >= cut }.toMutableList()   // ios: Mock/MockDesk.swift:602
        val have = fs.filter { it.day == day }.map { it.code }.toSet()   // ios: Mock/MockDesk.swift:603
        var n = have.size   // ios: Mock/MockDesk.swift:604
        for (h in sc.hits) { if (have.contains(h.code) || n >= 100) continue   // ios: Mock/MockDesk.swift:605
            fs.add(MockFollow(day = day, code = h.code, name = h.name, at = sc.at, p0 = s.prices[h.code] ?: h.price))   // ios: Mock/MockDesk.swift:606
            n += 1   // ios: Mock/MockDesk.swift:607
        }
        s.follows = fs   // ios: Mock/MockDesk.swift:609
    }

    /** 든 종목의 산 뒤 고점·저점 (되밀림 팔기 · 흔들림 손절을 켰다면 어땠을지) */
    private fun markPeaks() {   // ios: Mock/MockDesk.swift:613
        for (l in s.ledgers) {   // ios: Mock/MockDesk.swift:614
            for ((c, p) in l.positions) {   // ios: Mock/MockDesk.swift:615
                val px = s.prices[c]; if (px == null || px <= 0) continue   // ios: Mock/MockDesk.swift:616
                if (px > (p.peak ?: 0.0)) p.peak = px   // ios: Mock/MockDesk.swift:617
                if (px < (p.low ?: Double.POSITIVE_INFINITY)) p.low = px   // ios: Mock/MockDesk.swift:618
            }
        }
    }

    /** 1분 주기: 걸린 종목의 30분 · 1시간 · 마감 값을 지금 가격으로 채운다 */
    private fun fillFollows(now: MockDate) {   // ios: Mock/MockDesk.swift:624
        val fs = s.follows; if (fs == null || fs.isEmpty()) return   // ios: Mock/MockDesk.swift:625
        val day = MockFmt.day(now); val m = MockClock.minute(now)   // ios: Mock/MockDesk.swift:626
        for (f in fs) { if (f.day != day) continue   // ios: Mock/MockDesk.swift:627
            val px = s.prices[f.code]; if (px == null || px <= 0) continue   // ios: Mock/MockDesk.swift:628
            val t = now - f.at   // ios: Mock/MockDesk.swift:629
            // 앱이 꺼져 그때를 놓쳤으면 늦은 가격으로 채우지 않고 비워 둔다 (5분 안만)
            if (f.p30 == null && t >= 1800 && t < 2100) f.p30 = px   // ios: Mock/MockDesk.swift:631
            if (f.p60 == null && t >= 3600 && t < 3900) f.p60 = px   // ios: Mock/MockDesk.swift:632
            if (f.pEnd == null && m >= MockClock.closeAt) f.pEnd = px   // ios: Mock/MockDesk.swift:633
        }
        s.follows = fs   // ios: Mock/MockDesk.swift:635
    }
    /** 아직 값을 채울 걸린 종목 (1분 주기 가격 받기에 더한다) */
    private fun followCodes(now: MockDate): List<String> {   // ios: Mock/MockDesk.swift:638
        val day = MockFmt.day(now); val m = MockClock.minute(now)   // ios: Mock/MockDesk.swift:639
        return (s.follows ?: emptyList()).filter { f ->   // ios: Mock/MockDesk.swift:640
            val t = now - f.at   // ios: Mock/MockDesk.swift:641
            f.day == day && ((f.p30 == null && t < 2100) || (f.p60 == null && t < 3900) || (f.pEnd == null && m >= MockClock.closeAt))   // ios: Mock/MockDesk.swift:642
        }.map { it.code } + afterCodes(now)   // ios: Mock/MockDesk.swift:643
    }

    /** 판 종목 뒤 흐름: 판 체결마다 적어 둔다. 값은 1분 주기가 채운다 */
    private fun addAfter(b: MockBook, e: MockEvent) {   // ios: Mock/MockDesk.swift:647
        val code = e.code; val p = e.price; if (code == null || p == null || p <= 0) return   // ios: Mock/MockDesk.swift:648
        val cut = MockFmt.day(mockNow() + (-60 * 86_400))   // ios: Mock/MockDesk.swift:649
        val xs = (s.afters ?: mutableListOf()).filter { it.day >= cut }.toMutableList()   // ios: Mock/MockDesk.swift:650
        xs.add(MockAfter(day = MockFmt.day(e.at), book = b, code = code, name = e.name ?: name(code), at = e.at, p0 = p,   // ios: Mock/MockDesk.swift:651
                            qty = e.qty ?: 0, exit = e.exit ?: "판"))   // ios: Mock/MockDesk.swift:652
        s.afters = xs.takeLast(300).toMutableList()   // ios: Mock/MockDesk.swift:653
    }

    /** 1분 주기: 판 뒤 30분 · 1시간 · 마감 · 다음 날 시가. 앱이 꺼져 그때를 놓쳤으면 비워 둔다 */
    private fun fillAfters(now: MockDate, open: Boolean) {   // ios: Mock/MockDesk.swift:657
        val xs = s.afters; if (xs == null || xs.isEmpty()) return   // ios: Mock/MockDesk.swift:658
        val day = MockFmt.day(now); val m = MockClock.minute(now)   // ios: Mock/MockDesk.swift:659
        for (x in xs) {   // ios: Mock/MockDesk.swift:660
            val px = s.prices[x.code]; if (px == null || px <= 0) continue   // ios: Mock/MockDesk.swift:661
            if (x.day == day) {   // ios: Mock/MockDesk.swift:662
                val t = now - x.at   // ios: Mock/MockDesk.swift:663
                if (x.p30 == null && t >= 1800 && t < 2100) x.p30 = px   // ios: Mock/MockDesk.swift:664
                if (x.p60 == null && t >= 3600 && t < 3900) x.p60 = px   // ios: Mock/MockDesk.swift:665
                if (x.pEnd == null && m >= MockClock.closeAt) x.pEnd = px   // ios: Mock/MockDesk.swift:666
            } else if (x.pOpen == null && open && m >= 9 * 60 && m < 9 * 60 + 5 && MockCalc.weekdays(from = x.day, to = day) == 1) {   // ios: Mock/MockDesk.swift:667
                x.pOpen = px   // ios: Mock/MockDesk.swift:668
            }
        }
        s.afters = xs   // ios: Mock/MockDesk.swift:671
    }
    private fun afterCodes(now: MockDate): List<String> {   // ios: Mock/MockDesk.swift:673
        val day = MockFmt.day(now); val m = MockClock.minute(now)   // ios: Mock/MockDesk.swift:674
        return (s.afters ?: emptyList()).filter { x ->   // ios: Mock/MockDesk.swift:675
            val t = now - x.at   // ios: Mock/MockDesk.swift:676
            if (x.day == day) return@filter (x.p30 == null && t < 2100) || (x.p60 == null && t < 3900) || (x.pEnd == null && m >= MockClock.closeAt)   // ios: Mock/MockDesk.swift:677
            x.pOpen == null && m < 9 * 60 + 5 && MockCalc.weekdays(from = x.day, to = day) == 1   // ios: Mock/MockDesk.swift:678
        }.map { it.code }   // ios: Mock/MockDesk.swift:679
    }

    /** 걸린 종목 위쪽 30개의 유의사항을 묻는다 (고르기 순서 기준). 못 받으면 지난 값을 버린다 (모르는 채로 거르지 않게) */
    suspend fun refreshWarns(sc: MockScan) {   // ios: Mock/MockDesk.swift:683
        val top = sc.hits.take(30).map { it.code }   // ios: Mock/MockDesk.swift:684
        try {   // ios: Mock/MockDesk.swift:685
            s.warns = MockFeed.warnings(top).toMutableMap(); s.warnsAt = mockNow()   // ios: Mock/MockDesk.swift:686
        } catch (e: CancellationException) {   // ios: Mock/MockDesk.swift:687
        } catch (e: Exception) {   // ios: Mock/MockDesk.swift:688
            s.warns = null; s.warnsAt = null   // ios: Mock/MockDesk.swift:689
            log(MockEvent.Kind.error, book = MockBook.rule, text = "유의사항 확인 못 함 · " + ((e as? MockFeed.Fail)?.why ?: ""))   // ios: Mock/MockDesk.swift:690
        }
    }

    /** 종목 유의사항 짧은 이름들 ("VI · 투자경고") */
    fun warnText(code: String): String? {   // ios: Mock/MockDesk.swift:695
        val w = s.warns?.get(code); if (w == null || w.isEmpty()) return null   // ios: Mock/MockDesk.swift:696
        return MockFmt.uniq(w.joinToString(" · ") { MockCalc.warnName(it) })   // ios: Mock/MockDesk.swift:697
    }

    /** 새 배수로 잰 결과 한 토막: " · 새 배수(쌓임 23%) 걸린 40 · 새로 12 삼성전자·… · 빠진 3" */
    private fun newText(sc: MockScan): String {   // ios: Mock/MockDesk.swift:701
        val f = sc.frac; val nh = sc.newHits; if (f == null || nh == null) return ""   // ios: Mock/MockDesk.swift:702
        val old = sc.hits.map { it.code }.toSet(); val new = nh.toSet()   // ios: Mock/MockDesk.swift:703
        val added = nh.filter { !old.contains(it) }; val gone = sc.hits.count { !new.contains(it.code) }   // ios: Mock/MockDesk.swift:704
        val names = added.take(5).joinToString("·") { name(it) } + (if (added.size > 5) "…" else "")   // ios: Mock/MockDesk.swift:705
        return " · 새 배수(쌓임 ${(f * 100).swRound().toInt()}%) 걸린 ${nh.size} · 새로 ${added.size}" + (if (names.isEmpty()) "" else " " + names) + " · 빠진 $gone"   // ios: Mock/MockDesk.swift:706
    }

    /** 하루 거래량 곡선: 장 밖(8:30 전·15:45 뒤)에 하루 두 번까지 큰 종목 20개의 마지막 거래일 1분봉을 받아 하나 더한다
     *  큰 종목은 마지막 전체 훑기 기준, 아직 없으면 고정 20개. 받기에 실패하면 그때를 태우지 않고 10분 뒤 다시 (2026-10-27) */
    private var curving = false   // ios: Mock/MockDesk.swift:711
    private var curveLast: MockDate? = null   // ios: Mock/MockDesk.swift:712
    val bigDefault: List<String> = listOf("005930", "000660", "373220", "207940", "005380", "000270", "068270", "105560", "035420", "055550",   // ios: Mock/MockDesk.swift:713
                             "005490", "012450", "035720", "006400", "051910", "028260", "329180", "086790", "032830", "011200")   // ios: Mock/MockDesk.swift:714
    suspend fun curveIfNeeded(now: MockDate = mockNow()) {   // ios: Mock/MockDesk.swift:715
        val m = MockClock.minute(now)   // ios: Mock/MockDesk.swift:716
        val key = MockFmt.day(now) + (if (m < 720) "a" else "p")   // ios: Mock/MockDesk.swift:717
        if (curving || !(m < MockClock.curveUntil || m >= MockClock.curveFrom) || s.curveTried == key ||   // ios: Mock/MockDesk.swift:718
              now - (curveLast ?: 0.0) < 600) return   // ios: Mock/MockDesk.swift:719
        val codes = if ((s.bigCodes ?: emptyList()).isEmpty()) bigDefault else s.bigCodes!!   // ios: Mock/MockDesk.swift:720
        curving = true; try {   // ios: Mock/MockDesk.swift:721
        curveLast = now   // ios: Mock/MockDesk.swift:722
        val days = mutableMapOf<String, MutableList<List<MockBar>>>(); var n = 0   // ios: Mock/MockDesk.swift:723
        for (code in codes) {   // ios: Mock/MockDesk.swift:724
            val r = try { MockFeed.minutes(code) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (r != null && r.first.isNotEmpty() && r.second.isNotEmpty()) { days.getOrPut(r.first) { mutableListOf() }.add(r.second); n += 1 }   // ios: Mock/MockDesk.swift:725
        }
        val best = days.maxByOrNull { it.key }; if (best == null) {   // ios: Mock/MockDesk.swift:727
            s.curveNote = "${MockFmt.time(now)} 받기 실패 · 1분봉 0/${codes.size} · 10분 뒤 다시"; save(); return   // ios: Mock/MockDesk.swift:728
        }
        if ((s.curves ?: emptyList()).any { it.day == best.key }) { s.curveTried = key; s.curveNote = null; save(); return }   // ios: Mock/MockDesk.swift:730
        val cum = MockCalc.curve(best.value); if (cum == null) {   // ios: Mock/MockDesk.swift:731
            s.curveNote = "${MockFmt.time(now)} 받기 실패 · 1분봉 $n/${codes.size} · 10분 뒤 다시"; save(); return   // ios: Mock/MockDesk.swift:732
        }
        s.curves = ((s.curves ?: emptyList()) + listOf(MockCurve(day = best.key, cum = cum))).sortedBy { it.day }.takeLast(20).toMutableList()   // ios: Mock/MockDesk.swift:734
        s.curveTried = key; s.curveNote = null   // ios: Mock/MockDesk.swift:735
        save()   // ios: Mock/MockDesk.swift:736
        } finally { curving = false }
    }

    /** 종목별 관찰 기록: 걸린 종목마다 한 줄. 7일 지난 것은 버리고 종목당 100번까지 (2026-10-29, 점 횟수가 20에서 멈추던 것) */
    private fun addSeen(sc: MockScan) {   // ios: Mock/MockDesk.swift:740
        val all = (s.seen ?: mutableMapOf()).toMutableMap()   // ios: Mock/MockDesk.swift:741
        val cut = sc.at + (-7 * 86_400)   // ios: Mock/MockDesk.swift:742
        for (h in sc.hits) { all[h.code] = (all[h.code] ?: emptyList()) + MockSeen(at = sc.at, price = h.price, change = h.change, why = h.why, vol = h.vol, volNew = h.volNew) }   // ios: Mock/MockDesk.swift:743
        for ((c, xs) in all.toMap()) {   // ios: Mock/MockDesk.swift:744
            val keep = xs.filter { it.at >= cut }.takeLast(100)   // ios: Mock/MockDesk.swift:745
            if (keep.isEmpty()) all.remove(c) else all[c] = keep   // ios: Mock/MockDesk.swift:746
        }
        s.seen = all   // ios: Mock/MockDesk.swift:748
    }

    fun seen(code: String): List<MockSeen> = s.seen?.get(code) ?: emptyList()   // ios: Mock/MockDesk.swift:751

    /** 오늘 관찰에 걸린 횟수 (2번 넘은 종목만). 15분 칸마다 한 번만 센다 */
    val seenToday: Map<String, Int> get() {   // ios: Mock/MockDesk.swift:754
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:755
        return (s.seen ?: emptyMap()).mapValues { slots(it.value, day = today) }.filter { it.value >= 2 }   // ios: Mock/MockDesk.swift:756
    }

    /** 걸린 횟수: 그날 기록을 15분 칸(MockClock.recheck)으로 묶어 칸 수를 센다. 단추를 연달아 눌러도 한 칸은 한 번 (2026-10-29) */
    fun slots(xs: List<MockSeen>, day: String): Int =   // ios: Mock/MockDesk.swift:760
        xs.filter { MockFmt.day(it.at) == day }.map { (it.at / MockClock.recheck).toInt() }.toSet().size   // ios: Mock/MockDesk.swift:761

    // MARK: 선정

    suspend fun runSelect() {   // ios: Mock/MockDesk.swift:766
        if (!waitTick() || busy != null) return   // ios: Mock/MockDesk.swift:767
        busy = "select"   // ios: Mock/MockDesk.swift:768
        select(shown)   // ios: Mock/MockDesk.swift:769
        busy = null   // ios: Mock/MockDesk.swift:770
        save()   // ios: Mock/MockDesk.swift:771
        startAIReview()   // ios: Mock/MockDesk.swift:772
    }

    /** 지금 팔기: 내 종목을 지금 가격으로 팔기 조건에 대 본다 (지금 고르기의 반대) */
    suspend fun runSell() {   // ios: Mock/MockDesk.swift:776
        if (!waitTick() || busy != null) return   // ios: Mock/MockDesk.swift:777
        busy = "sell"   // ios: Mock/MockDesk.swift:778
        sellNow(shown)   // ios: Mock/MockDesk.swift:779
        busy = null   // ios: Mock/MockDesk.swift:780
        save()   // ios: Mock/MockDesk.swift:781
    }

    /** 팔기 조건은 그 장부 보유(positions)에만 건다. 실전 장부도 앱이 산 몫만 (토스 계좌 보유는 읽지 않는다) */
    private suspend fun sellNow(b: MockBook) {   // ios: Mock/MockDesk.swift:785
        val l = ledger(b)   // ios: Mock/MockDesk.swift:786
        if (!s.sell.on) { s.runs["sell"] = MockRun(at = mockNow(), ok = false, text = "켠 팔기 조건이 없어요"); return }   // ios: Mock/MockDesk.swift:787
        if (l.positions.isEmpty()) { s.runs["sell"] = MockRun(at = mockNow(), ok = true, text = "팔 종목이 없어요"); return }   // ios: Mock/MockDesk.swift:788
        val p = try { MockFeed.last(l.positions.keys.sorted()) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (p != null) { s.prices.putAll(p); s.pricesAt = mockNow() }   // ios: Mock/MockDesk.swift:789
        val n = sellSigs(b, hold = false)   // ios: Mock/MockDesk.swift:790
        val sigs = n.first   // ios: Mock/MockDesk.swift:791
        s.runs["sell"] = MockRun(at = mockNow(), ok = n.second == sigs.size, text = MockFmt.stamp(mockNow()) + (if (sigs.isEmpty()) " · 조건에 맞는 종목 없음" else " · ${n.second}종목 팔기 주문 · " + sigNames(sigs)))   // ios: Mock/MockDesk.swift:792
    }

    /** 팔기 신호를 보고 판다. 익절 첫 번은 반만 팔고 '반 팜'으로 적는다 (남은 몫은 되밀림·이유 꺼짐·마감에) */
    private suspend fun sellSigs(b: MockBook, hold: Boolean = true): Pair<List<MockCalc.SellSig>, Int> {   // ios: Mock/MockDesk.swift:796
        var sigs = sellPreview(b)   // ios: Mock/MockDesk.swift:797
        if (hold && b == MockBook.real && s.sellCheck == true) sigs = sigs.filter { it.why.startsWith("손절") }   // ios: Mock/MockDesk.swift:798
        var n = 0   // ios: Mock/MockDesk.swift:799
        for (sig in sigs) {   // ios: Mock/MockDesk.swift:800
            val ref = log(MockEvent.Kind.sell, book = b, who = MockWho.rule, code = sig.code, name = name(sig.code), text = sig.why, ref = ledger(b).positions[sig.code]?.buyRef).id   // ios: Mock/MockDesk.swift:801
            if (!placeSell(b, code = sig.code, qty = sig.qty, who = MockWho.rule, reason = sig.why, ref = ref)) continue   // ios: Mock/MockDesk.swift:802
            n += 1   // ios: Mock/MockDesk.swift:803
            val l = s.ledgers.firstOrNull { it.book == b }; if (sig.why.startsWith("익절") && l != null && l.positions[sig.code] != null) {   // ios: Mock/MockDesk.swift:804
                l.positions[sig.code]?.took = true   // ios: Mock/MockDesk.swift:805
                set(l)   // ios: Mock/MockDesk.swift:806
            }
        }
        return sigs to n   // ios: Mock/MockDesk.swift:809
    }

    /** 지금 가격이면 팔기 규칙이 낼 신호 (주문은 내지 않는다) */
    fun sellPreview(b: MockBook): List<MockCalc.SellSig> =   // ios: Mock/MockDesk.swift:813
        MockCalc.sellSignals(ledger(b), rule = s.sell, prices = s.prices, excluded = excluded(), today = MockFmt.day(mockNow()),   // ios: Mock/MockDesk.swift:814
                             seg = seg(), gone = s.gone ?: emptyMap())   // ios: Mock/MockDesk.swift:815

    /** 바뀐 팔기 규칙을 실전에 적용한다 (확인 카드) */
    fun confirmSellRule() {   // ios: Mock/MockDesk.swift:819
        edit("팔기 규칙 확인 · 실전에 적용") { it.sellCheck = null }   // ios: Mock/MockDesk.swift:820
    }

    /** 고른다. buy = 참이면 확인 없이 바로 주문 (한 바퀴·돌리기의 주문 알약). 지금 고르기는 고르기까지만, 주문은 '주문' 단추 */
    private suspend fun select(b: MockBook, buy: Boolean = false) {   // ios: Mock/MockDesk.swift:824
        val scan = s.scan; if (scan == null) { s.runs["select"] = MockRun(at = mockNow(), ok = false, text = "관찰 결과 없음 · 먼저 훑어 주세요"); return }   // ios: Mock/MockDesk.swift:825
        // 고르기 직전 가격 (위쪽 200종목만)
        val top = scan.hits.take(200).map { it.code }   // ios: Mock/MockDesk.swift:827
        val p = try { MockFeed.last(top) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (p != null) { s.prices.putAll(p); s.pricesAt = mockNow() }   // ios: Mock/MockDesk.swift:828
        // 종목 판단(거래대금·재확인·유의사항·시장 날씨)은 관찰에서 끝났다. 선정은 몇 종목을 얼마씩 살지만 본다 (2026-10-29)
        val sc = scan; val capped = scan.skipped ?: emptyList()   // ios: Mock/MockDesk.swift:830
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:831
        val sold = ledgerEvents(b).filter { it.kind == MockEvent.Kind.fill && it.side == MockSide.sell && MockFmt.day(it.at) == today }.mapNotNull { it.code }.toSet()   // ios: Mock/MockDesk.swift:832
        val (picks, rest) = MockCalc.select(sc, ledger = ledger(b), rule = s.select, fees = s.fees, prices = s.prices, excluded = excluded(), soldToday = sold)   // ios: Mock/MockDesk.swift:833
        val skips = capped + rest   // ios: Mock/MockDesk.swift:834
        val pe = mutableMapOf<String, String>()   // ios: Mock/MockDesk.swift:835
        for (pk in picks) {   // ios: Mock/MockDesk.swift:836
            val e = log(MockEvent.Kind.select, book = b, who = MockWho.rule, code = pk.code, name = pk.name,   // ios: Mock/MockDesk.swift:837
                        text = pk.why + " · 비중 " + cformat("%.0f%%", pk.weight * 100) + " · ${pk.qty}주 남짓", bundle = scan.bundleName, ref = s.scanEvent)   // ios: Mock/MockDesk.swift:838
            pe[pk.code] = e.id   // ios: Mock/MockDesk.swift:839
        }
        for (k in rest.take(30)) { if (!(k.short || k.why.startsWith("예외") || k.why.startsWith("0주") || k.why.startsWith("오늘 판") || k.why.startsWith("더 담기"))) continue   // ios: Mock/MockDesk.swift:841
            log(if (k.short) MockEvent.Kind.short else MockEvent.Kind.exclude, book = b, who = MockWho.rule, code = k.code, name = k.name, text = k.why, bundle = scan.bundleName, ref = s.scanEvent)   // ios: Mock/MockDesk.swift:842
        }
        s.picks = picks.toMutableList(); s.picksBook = b; s.pickEvents = pe; s.skips = skips.toMutableList(); s.pickedAt = mockNow(); s.dropped = mutableListOf()   // ios: Mock/MockDesk.swift:844
        s.aiViews = mutableMapOf()   // ios: Mock/MockDesk.swift:845
        lastPicks = picks   // ios: Mock/MockDesk.swift:846
        val shortN = skips.count { it.short } + picks.count { it.short == true }   // ios: Mock/MockDesk.swift:847
        s.runs["select"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} 고름 · ${picks.size}종목 · 한 번 ≤${MockFmt.num((s.select.roundMax ?: 0.10) * 100)}% · 현금 부족 ${shortN}건")   // ios: Mock/MockDesk.swift:848
        if (buy) order()   // ios: Mock/MockDesk.swift:849
    }

    /** 검토하고 빼기 (손으로 고친 것으로 남긴다) */
    fun dropPick(code: String) {   // ios: Mock/MockDesk.swift:853
        val i = s.picks.indexOfFirst { it.code == code }; if (i < 0) return   // ios: Mock/MockDesk.swift:854
        val p = s.picks.removeAt(i)   // ios: Mock/MockDesk.swift:855
        s.dropped = ((s.dropped ?: emptyList()) + listOf(p)).toMutableList()   // ios: Mock/MockDesk.swift:856
        log(MockEvent.Kind.select, book = s.picksBook ?: MockBook.rule, who = MockWho.hand, code = p.code, name = p.name, text = "검토에서 뺌", ref = s.pickEvents[p.code])   // ios: Mock/MockDesk.swift:857
        save()   // ios: Mock/MockDesk.swift:858
    }

    /** 검토 중인 종목의 주수를 손으로 고친다 (쓸 돈도 그 주수만큼) */
    fun setPickQty(code: String, qty: Int) {   // ios: Mock/MockDesk.swift:862
        val i = s.picks.indexOfFirst { it.code == code }; if (i < 0 || qty < 1 || s.picks[i].qty == qty) return   // ios: Mock/MockDesk.swift:863
        val p = s.picks[i].copy()   // ios: Mock/MockDesk.swift:864
        p.qty = qty   // ios: Mock/MockDesk.swift:865
        p.budget = qty.toDouble() * p.price * (1 + s.fees.commission)   // ios: Mock/MockDesk.swift:866
        p.who = MockWho.hand   // ios: Mock/MockDesk.swift:867
        s.picks[i] = p   // ios: Mock/MockDesk.swift:868
        log(MockEvent.Kind.select, book = s.picksBook ?: MockBook.rule, who = MockWho.hand, code = p.code, name = p.name, text = "검토에서 ${qty}주로 고침", ref = s.pickEvents[p.code])   // ios: Mock/MockDesk.swift:869
        save()   // ios: Mock/MockDesk.swift:870
    }

    /** 고른 종목을 주문한다 (검토가 끝났거나 검토 문이 열려 있을 때) */
    suspend fun orderPicks() {   // ios: Mock/MockDesk.swift:874
        if (!waitTick() || busy != null) return   // ios: Mock/MockDesk.swift:875
        busy = "order"   // ios: Mock/MockDesk.swift:876
        order()   // ios: Mock/MockDesk.swift:877
        busy = null   // ios: Mock/MockDesk.swift:878
        save()   // ios: Mock/MockDesk.swift:879
    }

    private suspend fun order() {   // ios: Mock/MockDesk.swift:882
        if (s.picks.isEmpty()) return   // ios: Mock/MockDesk.swift:883
        val picks = s.picks.toList()   // ios: Mock/MockDesk.swift:884
        s.picks = mutableListOf()   // ios: Mock/MockDesk.swift:885
        var n = 0   // ios: Mock/MockDesk.swift:886
        val b = s.picksBook ?: MockBook.rule   // ios: Mock/MockDesk.swift:887
        for (p in picks) if (placeBuy(b, code = p.code, name = p.name, budget = if (p.who == MockWho.hand) null else p.budget, qty = if (p.who == MockWho.hand) p.qty else null,   // ios: Mock/MockDesk.swift:888
                                            who = p.who, reason = p.why, ref = s.pickEvents[p.code])) n += 1   // ios: Mock/MockDesk.swift:889
        s.runs["order"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} 주문 ${n}건 · 고른 ${picks.size}")   // ios: Mock/MockDesk.swift:890
    }

    val cycleSell: Boolean get() = s.cycleSell ?: true   // ios: Mock/MockDesk.swift:893
    val cycleOrder: Boolean get() = s.cycleOrder ?: true   // ios: Mock/MockDesk.swift:894
    val autoOrder: Boolean get() = s.autoOrder ?: true   // ios: Mock/MockDesk.swift:895
    val autoSell: Boolean get() = s.autoSell ?: true   // ios: Mock/MockDesk.swift:896

    /** 한 바퀴: 훑기 → 고르기 → 주문 → 팔기 (주문·팔기 알약을 끄면 건너뜀) */
    suspend fun runCycle() {   // ios: Mock/MockDesk.swift:899
        if (!waitTick() || busy != null) return   // ios: Mock/MockDesk.swift:900
        busy = "cycle"   // ios: Mock/MockDesk.swift:901
        keepAlive("cycle")   // ios: Mock/MockDesk.swift:902
        if (s.runs["observe"]?.ok == true && !halted) select(shown, buy = cycleOrder)   // ios: Mock/MockDesk.swift:903
        if (cycleSell && paused == null && !halted) sellNow(shown)   // ios: Mock/MockDesk.swift:904
        busy = null   // ios: Mock/MockDesk.swift:905
        save()   // ios: Mock/MockDesk.swift:906
        startAIReview()   // ios: Mock/MockDesk.swift:907
    }

    /** 손 주문 (보이는 칸의 장부에 '손'으로 적는다. 예외 종목도 막지 않는다) */
    suspend fun runHand(side: MockSide, code: String, name: String, qty: Int?, type: MockOrderType, limit: Double?): Boolean {   // ios: Mock/MockDesk.swift:912
        if (!waitTick() || busy != null) return false   // ios: Mock/MockDesk.swift:913
        busy = "hand"   // ios: Mock/MockDesk.swift:914
        val ok = if (side == MockSide.buy)   // ios: Mock/MockDesk.swift:915
            placeBuy(shown, code = code, name = name, qty = qty, type = type, limit = limit, who = MockWho.hand, reason = "손 주문", ref = null)   // ios: Mock/MockDesk.swift:916
            else placeSell(shown, code = code, qty = qty, type = type, limit = limit, who = MockWho.hand, reason = "손 주문")   // ios: Mock/MockDesk.swift:917
        busy = null   // ios: Mock/MockDesk.swift:918
        save()   // ios: Mock/MockDesk.swift:919
        return ok   // ios: Mock/MockDesk.swift:920
    }

    // MARK: 주문

    /** 매수 한 건. budget 이나 qty 중 하나. 성공하면 참 (대기로 들어가도 참) */
    suspend fun placeBuy(b: MockBook, code: String, name: String, budget: Double? = null, qty: Int? = null,   // ios: Mock/MockDesk.swift:927
                  type: MockOrderType = MockOrderType.market, limit: Double? = null, who: MockWho, reason: String, ref: String?): Boolean {   // ios: Mock/MockDesk.swift:928
        val ph = refreshPhase()   // ios: Mock/MockDesk.swift:929
        if (ph == Phase.unknown) { log(MockEvent.Kind.error, book = b, who = who, code = code, name = name, text = "장 일정 확인 못 함 · 새로 사지 않음", ref = ref); return false }   // ios: Mock/MockDesk.swift:930
        val test = ph == Phase.closed   // ios: Mock/MockDesk.swift:931
        if (b == MockBook.real && test) { log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = "실전 · 장 밖이라 주문 안 냄", test = test, side = MockSide.buy, ref = ref); return false }   // ios: Mock/MockDesk.swift:932
        // 안전장치: 모두 멈춤 · 하루 손실 한도 (손 주문은 막지 않는다)
        if (who != MockWho.hand && halted) { log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = "모두 멈춤 중 · 안 냄", test = test, side = MockSide.buy, ref = ref); return false }   // ios: Mock/MockDesk.swift:934
        // [실전]: 매수 직전에 토스 계좌를 한 번 읽어 한도와 견준다. 못 읽으면 사지 않는다
        if (b == MockBook.real && who != MockWho.hand && !test) {   // ios: Mock/MockDesk.swift:936
            refreshAccount()   // ios: Mock/MockDesk.swift:937
            if (realEquity == null || s.realBaseDay != MockFmt.day(mockNow())) {   // ios: Mock/MockDesk.swift:938
                log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = "토스 계좌 못 읽음 · 새로 사지 않음", test = test, side = MockSide.buy, ref = ref)   // ios: Mock/MockDesk.swift:939
                return false   // ios: Mock/MockDesk.swift:940
            }
        }
        if (who != MockWho.hand && !test && dayLossHit(b)) {   // ios: Mock/MockDesk.swift:943
            log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = "하루 손실 한도 −${MockFmt.num(guards.dayLoss * 100)}% 넘음 · 새로 사지 않음", test = test, side = MockSide.buy, ref = ref)   // ios: Mock/MockDesk.swift:944
            return false   // ios: Mock/MockDesk.swift:945
        }
        // 주문 직전에 예외를 또 한 번 막는다 (손 주문은 막지 않는다)
        val exWhy = excluded()[code]; if (who != MockWho.hand && exWhy != null) { log(MockEvent.Kind.exclude, book = b, who = who, code = code, name = name, text = "주문 직전 예외 · " + exWhy, ref = ref); return false }   // ios: Mock/MockDesk.swift:948
        if (ledger(b).pending.any { it.code == code }) { log(MockEvent.Kind.order, book = b, who = who, code = code, name = name, text = "같은 종목 주문 대기 중 · 안 냄", ref = ref); return false }   // ios: Mock/MockDesk.swift:949
        var book: MockCalc.Book   // ios: Mock/MockDesk.swift:950
        try { book = broker(b).book(code) }   // ios: Mock/MockDesk.swift:951
        catch (e: CancellationException) { return false }   // ios: Mock/MockDesk.swift:952
        catch (e: Exception) { log(MockEvent.Kind.error, book = b, who = who, code = code, name = name, text = "호가 확인 못 함 · " + ((e as? MockFeed.Fail)?.why ?: ""), ref = ref); return false }   // ios: Mock/MockDesk.swift:953
        val tpx = s.prices[code]; if (test && book.asks.isEmpty() && tpx != null) book = MockCalc.Book(asks = listOf(MockCalc.Level(p = tpx, v = 1e12)), bids = book.bids, at = book.at)   // ios: Mock/MockDesk.swift:954
        val ask = book.bestAsk; if (ask == null) { log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = "매도 호가 없음 (거래 정지·VI 의심) · 거부", test = test, side = MockSide.buy, ref = ref); return false }   // ios: Mock/MockDesk.swift:955
        // 살 때 잰 값: 꺼 둔 조건(연속 손절 멈춤·호가 벽·흔들림 손절)도 늘 적어 둔다 (2026-10-29)
        val bidV = book.bids.fold(0.0) { a, x -> a + x.v }; val askV = book.asks.fold(0.0) { a, x -> a + x.v }   // ios: Mock/MockDesk.swift:957
        val wall: Double? = if (bidV > 0) askV / bidV else null   // ios: Mock/MockDesk.swift:958
        val streak = lossStreakToday(b)   // ios: Mock/MockDesk.swift:959
        val entry = MockBuyNote(range20 = s.scan?.hits?.firstOrNull { it.code == code }?.range20, streak = streak, wall = wall,   // ios: Mock/MockDesk.swift:960
                              market = s.weather?.get(market(code)), minute = MockClock.minute(mockNow()))   // ios: Mock/MockDesk.swift:961
        if (who != MockWho.hand && !test) {   // ios: Mock/MockDesk.swift:962
            val ln = guards.lossStreak; if (ln != null && streak >= ln) {   // ios: Mock/MockDesk.swift:963
                log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = "오늘 손절 ${streak}번 연달아 · 새로 사지 않음", test = test, side = MockSide.buy, ref = ref); return false   // ios: Mock/MockDesk.swift:964
            }
            val x = guards.bookWall; if (x != null && wall != null && wall > x) {   // ios: Mock/MockDesk.swift:966
                log(MockEvent.Kind.order, book = b, who = who, code = code, name = name, text = "호가 벽 · 매도 잔량이 매수의 ${MockFmt.num((wall * 10).swRound() / 10)}배 · 미룸", test = test, ref = ref); return false   // ios: Mock/MockDesk.swift:967
            }
        }
        // 기다리는 동안 장부가 바뀌었을 수 있으니 호가를 받은 뒤에 다시 읽는다
        val l0 = s.ledgers.firstOrNull { it.book == b }; if (l0 == null || l0.pending.any { it.code == code }) return false   // ios: Mock/MockDesk.swift:971
        var l = l0
        val lim = limit?.let { MockCalc.snap(it, side = MockSide.buy) }   // ios: Mock/MockDesk.swift:972
        val px = if (type == MockOrderType.limit) (lim ?: ask) else ask   // ios: Mock/MockDesk.swift:973
        var q = qty ?: floor((budget ?: 0.0) / (px * (1 + s.fees.commission))).toInt()   // ios: Mock/MockDesk.swift:974
        val mult = if (type == MockOrderType.market) 1.02 else 1.0   // ios: Mock/MockDesk.swift:975
        val unit = px * mult * (1 + s.fees.commission)   // ios: Mock/MockDesk.swift:976
        // 안전장치: 한 번 매수 주문 금액 상한 (손 주문도)
        q = min(q, floor(guards.orderCap / unit).toInt())   // ios: Mock/MockDesk.swift:978
        // 미수 안 씀: 주수를 정한 주문(손 주문)이 현금보다 크면 줄이지 않고 거부한다. 결제 대기 돈은 쓰지 못한다
        // 실전 장부는 토스 주문가능 현금도 넘지 않는다 (모르면 한 번 읽는다. 그래도 모르면 사지 않는다)
        if (b == MockBook.real && account == null) refreshAccount()   // ios: Mock/MockDesk.swift:981
        val cash = if (b == MockBook.real) min(l.cash, account?.cash ?: 0.0) else l.cash   // ios: Mock/MockDesk.swift:982
        // [모의] 장부는 모든 매수(규칙·AI·손)가 남길 현금(평가액 대비, 고르기 규칙)을 지킨다. [실전]은 그대로
        val keep = if (b == MockBook.rule) s.select.keepCash * equity(b) else 0.0   // ios: Mock/MockDesk.swift:984
        val room = MockCalc.usable(cash = cash, keep = keep)   // ios: Mock/MockDesk.swift:985
        val can = floor(room / unit).toInt()   // ios: Mock/MockDesk.swift:986
        if (qty != null && q > can) {   // ios: Mock/MockDesk.swift:987
            val need = q.toDouble() * unit   // ios: Mock/MockDesk.swift:988
            val why = if (l.cash + l.settling >= need)   // ios: Mock/MockDesk.swift:989
                "현금 부족 · 결제 대기 " + MockFmt.won(l.settling) + "은 이틀 안에 들어올 돈 · 미수 안 씀 · 거부"   // ios: Mock/MockDesk.swift:990
                else "현금 부족 · 필요 " + MockFmt.won(need) + (if (keep > 0) " · 남길 현금 " + MockFmt.won(keep) else "") + " · 쓸 수 있는 돈 " + MockFmt.won(room) + " · 거부"   // ios: Mock/MockDesk.swift:991
            log(MockEvent.Kind.reject, book = b, who = who, code = code, name = name, text = why, test = test, side = MockSide.buy, qty = q, ref = ref)   // ios: Mock/MockDesk.swift:992
            notify("거부 · " + name, why)   // ios: Mock/MockDesk.swift:993
            return false   // ios: Mock/MockDesk.swift:994
        }
        // 금액으로 정한 주문(규칙·AI)은 살 수 있는 만큼으로 줄인다
        q = min(q, can)   // ios: Mock/MockDesk.swift:997
        if (q < 1) {   // ios: Mock/MockDesk.swift:998
            val wait = if (l.settling > 0) " · 결제 대기 " + MockFmt.won(l.settling) else ""   // ios: Mock/MockDesk.swift:999
            val why = if (keep > 0) "현금 부족 · 남길 현금 " + MockFmt.won(keep) + " · 쓸 수 있는 " + MockFmt.won(room) else "현금 부족 · 남은 " + MockFmt.won(l.cash)   // ios: Mock/MockDesk.swift:1000
            log(if (room < unit) MockEvent.Kind.short else MockEvent.Kind.order, book = b, who = who, code = code, name = name,   // ios: Mock/MockDesk.swift:1001
                text = if (room < unit) why + wait else "0주 · 주문 안 냄 (정상)", test = test, ref = ref)   // ios: Mock/MockDesk.swift:1002
            return false   // ios: Mock/MockDesk.swift:1003
        }
        // 미끄러짐 한도: 다 채우면 평균 단가가 1호가 + 한도를 넘을 때, 한도 값까지만 지금 채우고 남은 몫은 지정가로 30분 기다린다
        val (oType, oLim, until) = split(MockSide.buy, type = type, limit = lim, qty = q, book = book, reason = reason)   // ios: Mock/MockDesk.swift:1006
        val r = MockCalc.reserve(type = oType, qty = q, limit = oLim, bestAsk = ask, fees = s.fees, cash = l.cash)   // ios: Mock/MockDesk.swift:1007
        val auc = !test && inCloseAuction()   // ios: Mock/MockDesk.swift:1008
        val o = MockOrder(code = code, name = name, side = MockSide.buy, type = oType, qty = q, limit = oLim, at = mockNow(), who = who, reason = reason, reserved = r, ref = ref,   // ios: Mock/MockDesk.swift:1009
                          until = if (auc) s.session?.close?.let { it + 1200 } else until, state = MockOrderState.accepted, prev = prevClose(code), auction = if (auc) true else null,   // ios: Mock/MockDesk.swift:1010
                          expect = MockCalc.expect(side = MockSide.buy, type = oType, qty = q, limit = oLim, book = book, fees = s.fees),   // ios: Mock/MockDesk.swift:1011
                          conds = if (who == MockWho.hand) null else bundle?.let { it.pieces.joinToString(" · ") { p -> p.text } }, entry = entry)   // ios: Mock/MockDesk.swift:1012
        val subWhy = broker(b).submit(o, book = book); if (subWhy != null) { reject(b, o, subWhy, test = test); return false }   // ios: Mock/MockDesk.swift:1013
        o.tossId = broker(b).remoteId(o)   // ios: Mock/MockDesk.swift:1014
        // 접수를 기다리는 동안 장부가 바뀌었을 수 있다
        val l2 = s.ledgers.firstOrNull { it.book == b }; if (l2 == null || l2.pending.any { it.code == code } || l2.cash < r) return false   // ios: Mock/MockDesk.swift:1016
        l = l2   // ios: Mock/MockDesk.swift:1017
        l.cash -= r   // ios: Mock/MockDesk.swift:1018
        l.pending = l.pending + o   // ios: Mock/MockDesk.swift:1019
        set(l)   // ios: Mock/MockDesk.swift:1020
        log(MockEvent.Kind.order, id = o.id, book = b, who = who, code = code, name = name,   // ios: Mock/MockDesk.swift:1021
            text = "접수 · ${oType.label} ${q}주" + (o.tossId?.let { " · 토스 " + it.take(8) } ?: "") + (oLim?.let { " · " + MockFmt.won(it) } ?: "") + (if (until != null) " · 나눔 (미끄러짐 한도)" else "") + " · 묶은 돈 " + MockFmt.won(r) + (if (reason.isEmpty()) "" else " · " + reason), test = test, side = MockSide.buy, qty = q, ref = ref)   // ios: Mock/MockDesk.swift:1022
        settle(b, o, book = book, test = test)   // ios: Mock/MockDesk.swift:1023
        return true   // ios: Mock/MockDesk.swift:1024
    }

    /** 오늘 마지막 판 체결부터 거슬러 센 연속 손절 수 (정식 체결만) */
    fun lossStreakToday(b: MockBook): Int {   // ios: Mock/MockDesk.swift:1028
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1029
        var n = 0   // ios: Mock/MockDesk.swift:1030
        for (e in ledgerEvents(b).reversed()) { if (!(e.kind == MockEvent.Kind.fill && e.side == MockSide.sell && !e.test && MockFmt.day(e.at) == today)) continue   // ios: Mock/MockDesk.swift:1031
            if ((e.exit ?: "").startsWith("손절")) n += 1 else break   // ios: Mock/MockDesk.swift:1032
        }
        return n   // ios: Mock/MockDesk.swift:1034
    }

    /** 매도 한 건. qty 없으면 팔 수 있는 만큼 전부 */
    suspend fun placeSell(b: MockBook, code: String, qty: Int? = null, type: MockOrderType = MockOrderType.market, limit: Double? = null,   // ios: Mock/MockDesk.swift:1039
                   who: MockWho, reason: String, ref: String? = null): Boolean {   // ios: Mock/MockDesk.swift:1040
        val p = ledger(b).positions[code] ?: return false   // ios: Mock/MockDesk.swift:1041
        val selling = ledger(b).pending.filter { it.code == code && it.side == MockSide.sell }.fold(0) { a, x -> a + x.qty }   // ios: Mock/MockDesk.swift:1042
        val q = min(qty ?: p.qty, p.qty - selling)   // ios: Mock/MockDesk.swift:1043
        if (q < 1) return false   // ios: Mock/MockDesk.swift:1044
        if (who != MockWho.hand && halted) { log(MockEvent.Kind.reject, book = b, who = who, code = code, name = p.name, text = "모두 멈춤 중 · 안 냄", side = MockSide.sell, ref = ref); return false }   // ios: Mock/MockDesk.swift:1045
        val ph = refreshPhase()   // ios: Mock/MockDesk.swift:1046
        val test = ph != Phase.open   // ios: Mock/MockDesk.swift:1047
        if (b == MockBook.real && test) { log(MockEvent.Kind.reject, book = b, who = who, code = code, name = p.name, text = "실전 · 장 밖이라 주문 안 냄", test = test, side = MockSide.sell, ref = ref); return false }   // ios: Mock/MockDesk.swift:1048
        var book: MockCalc.Book   // ios: Mock/MockDesk.swift:1049
        try { book = broker(b).book(code) }   // ios: Mock/MockDesk.swift:1050
        catch (e: CancellationException) { return false }   // ios: Mock/MockDesk.swift:1051
        catch (e: Exception) { log(MockEvent.Kind.error, book = b, who = who, code = code, name = p.name, text = "호가 확인 못 함 · 팔기 미룸 · " + ((e as? MockFeed.Fail)?.why ?: "")); return false }   // ios: Mock/MockDesk.swift:1052
        val tpx = s.prices[code]; if (test && book.bids.isEmpty() && tpx != null) book = MockCalc.Book(asks = book.asks, bids = listOf(MockCalc.Level(p = tpx, v = 1e12)), at = book.at)   // ios: Mock/MockDesk.swift:1053
        val lim = limit?.let { MockCalc.snap(it, side = MockSide.sell) }   // ios: Mock/MockDesk.swift:1054
        val l0 = s.ledgers.firstOrNull { it.book == b }; val p2 = l0?.positions?.get(code)   // ios: Mock/MockDesk.swift:1055
        if (l0 == null || p2 == null || p2.qty - l0.pending.filter { it.code == code && it.side == MockSide.sell }.fold(0) { a, x -> a + x.qty } < q) return false   // ios: Mock/MockDesk.swift:1056
        var l = l0
        val (oType, oLim, until) = split(MockSide.sell, type = type, limit = lim, qty = q, book = book, reason = reason)   // ios: Mock/MockDesk.swift:1057
        val auc = !test && inCloseAuction()   // ios: Mock/MockDesk.swift:1058
        val o = MockOrder(code = code, name = p.name, side = MockSide.sell, type = oType, qty = q, limit = oLim, at = mockNow(), who = who, reason = reason, ref = ref ?: p.buyRef,   // ios: Mock/MockDesk.swift:1059
                          until = if (auc) s.session?.close?.let { it + 1200 } else until, state = MockOrderState.accepted, prev = prevClose(code), auction = if (auc) true else null,   // ios: Mock/MockDesk.swift:1060
                          expect = MockCalc.expect(side = MockSide.sell, type = oType, qty = q, limit = oLim, book = book, fees = s.fees))   // ios: Mock/MockDesk.swift:1061
        val subWhy = broker(b).submit(o, book = book); if (subWhy != null) { reject(b, o, subWhy, test = test); return false }   // ios: Mock/MockDesk.swift:1062
        o.tossId = broker(b).remoteId(o)   // ios: Mock/MockDesk.swift:1063
        val l2 = s.ledgers.firstOrNull { it.book == b }; val p3 = l2?.positions?.get(code)   // ios: Mock/MockDesk.swift:1064
        if (l2 == null || p3 == null || p3.qty - l2.pending.filter { it.code == code && it.side == MockSide.sell }.fold(0) { a, x -> a + x.qty } < q) return false   // ios: Mock/MockDesk.swift:1065
        l = l2   // ios: Mock/MockDesk.swift:1066
        l.pending = l.pending + o   // ios: Mock/MockDesk.swift:1067
        set(l)   // ios: Mock/MockDesk.swift:1068
        log(MockEvent.Kind.order, id = o.id, book = b, who = who, code = code, name = p.name,   // ios: Mock/MockDesk.swift:1069
            text = "접수 · ${oType.label} 매도 ${q}주" + (o.tossId?.let { " · 토스 " + it.take(8) } ?: "") + (oLim?.let { " · " + MockFmt.won(it) } ?: "") + (if (until != null) " · 나눔 (미끄러짐 한도)" else "") + (if (reason.isEmpty()) "" else " · " + reason), test = test, side = MockSide.sell, qty = q, ref = o.ref)   // ios: Mock/MockDesk.swift:1070
        settle(b, o, book = book, test = test)   // ios: Mock/MockDesk.swift:1071
        return true   // ios: Mock/MockDesk.swift:1072
    }

    /** 미끄러짐 한도로 나눌지: 시장가를 지금 호가로 끝까지 채운 평균 단가가 1호가에서 한도보다 더 벗어나면
     *  한도 값의 지정가로 바꾸고 30분 기한을 둔다 (한도까지는 지금 채우고, 남은 몫은 1분 주기가 새 호가로 채워 본다). 손절·마감 정리 매도는 한 번에 */
    private fun split(side: MockSide, type: MockOrderType, limit: Double?, qty: Int, book: MockCalc.Book, reason: String): Triple<MockOrderType, Double?, MockDate?> {   // ios: Mock/MockDesk.swift:1077
        val sl = s.select.slip; if (type != MockOrderType.market || sl == null || sl <= 0 || reason.startsWith("손절") || reason.startsWith("마감")) return Triple(type, limit, null)   // ios: Mock/MockDesk.swift:1078
        val f = MockCalc.walk(side = side, type = MockOrderType.market, qty = qty, limit = null, book = book, fees = s.fees)   // ios: Mock/MockDesk.swift:1079
        if (f.qty <= 0) return Triple(type, limit, null)   // ios: Mock/MockDesk.swift:1080
        val a = book.bestAsk; if (side == MockSide.buy && a != null && f.avg > a * (1 + sl)) {   // ios: Mock/MockDesk.swift:1081
            return Triple(MockOrderType.limit, max(a, MockCalc.snap(a * (1 + sl), side = MockSide.buy)), mockNow() + 1800)   // ios: Mock/MockDesk.swift:1082
        }
        val b = book.bestBid; if (side == MockSide.sell && b != null && f.avg < b * (1 - sl)) {   // ios: Mock/MockDesk.swift:1084
            return Triple(MockOrderType.limit, min(b, MockCalc.snap(b * (1 - sl), side = MockSide.sell)), mockNow() + 1800)   // ios: Mock/MockDesk.swift:1085
        }
        return Triple(type, limit, null)   // ios: Mock/MockDesk.swift:1087
    }

    /** 나눈 남은 몫이 기한까지 다 못 채워졌으면 취소하고 묶은 돈을 돌려준다 */
    private fun expire(b: MockBook, orderId: String) {   // ios: Mock/MockDesk.swift:1091
        val l = s.ledgers.firstOrNull { it.book == b } ?: return; val i = l.pending.indexOfFirst { it.id == orderId }; if (i < 0) return   // ios: Mock/MockDesk.swift:1092
        val o = l.pending[i]; l.pending = l.pending.filterIndexed { k, _ -> k != i }   // ios: Mock/MockDesk.swift:1093
        l.cash += max(0.0, o.reserved)   // ios: Mock/MockDesk.swift:1094
        set(l)   // ios: Mock/MockDesk.swift:1095
        log(MockEvent.Kind.cancel, book = b, who = o.who, code = o.code, name = o.name, text = "남은 ${o.qty}주 취소 · 30분 안에 다 못 채움", ref = o.id)   // ios: Mock/MockDesk.swift:1096
        scope.launch { broker(b).cancel(o) }   // ios: Mock/MockDesk.swift:1097
    }

    val guards: MockGuards get() = s.guards ?: MockGuards()   // ios: Mock/MockDesk.swift:1100
    val halted: Boolean get() = s.halt == true   // ios: Mock/MockDesk.swift:1101

    /** 하루 손실 한도를 넘었나 (장부마다: 오늘 시작 평가액 대비)
     *  [실전]은 오늘 처음 읽은 토스 평가액 대비 지금 토스 평가액 (모르면 넘지 않은 것으로 보이고, 매수는 placeBuy 가 막는다) */
    fun dayLossHit(b: MockBook): Boolean {   // ios: Mock/MockDesk.swift:1105
        if (b == MockBook.real) {   // ios: Mock/MockDesk.swift:1106
            val base = s.realBase; val eq = realEquity; if (s.realBaseDay != MockFmt.day(mockNow()) || base == null || eq == null) return false   // ios: Mock/MockDesk.swift:1107
            return base > 0 && eq <= base * (1 - guards.dayLoss)   // ios: Mock/MockDesk.swift:1108
        }
        val base = dayBase(b); return base > 0 && equity(b) <= base * (1 - guards.dayLoss)   // ios: Mock/MockDesk.swift:1110
    }
    val dayLossHit: Boolean get() = dayLossHit(shown)   // ios: Mock/MockDesk.swift:1112

    /** 모두 멈춤: 모든 대기 주문을 취소하고 묶은 돈을 돌려준다. 자동매매(한 바퀴·돌리기·자동 팔기)와 규칙 주문을 막는다 */
    fun haltAll() {   // ios: Mock/MockDesk.swift:1115
        s.halt = true   // ios: Mock/MockDesk.swift:1116
        for (l in s.ledgers) {   // ios: Mock/MockDesk.swift:1117
            val os = l.pending   // ios: Mock/MockDesk.swift:1118
            l.cash += os.fold(0.0) { a, o -> a + max(0.0, o.reserved) }   // ios: Mock/MockDesk.swift:1119
            l.pending = emptyList()   // ios: Mock/MockDesk.swift:1120
            for (o in os) {   // ios: Mock/MockDesk.swift:1121
                log(MockEvent.Kind.cancel, book = l.book, who = MockWho.hand, code = o.code, name = o.name, text = "모두 멈춤 · 남은 ${o.qty}주 취소", ref = o.id)   // ios: Mock/MockDesk.swift:1122
                val br = broker(l.book); scope.launch { br.cancel(o) }   // ios: Mock/MockDesk.swift:1123
            }
        }
        log(MockEvent.Kind.rules, book = MockBook.rule, text = "모두 멈춤")   // ios: Mock/MockDesk.swift:1126
        save()   // ios: Mock/MockDesk.swift:1127
    }
    fun resumeAll() { edit("모두 멈춤 풂") { it.halt = null } }   // ios: Mock/MockDesk.swift:1129

    /** 마감 동시호가 (정규장 끝 10분 전부터 끝까지): 접수만 하고 끝나는 시각에 단일가로 체결 */
    fun inCloseAuction(now: MockDate = mockNow()): Boolean {   // ios: Mock/MockDesk.swift:1132
        val ss = s.session; val c = ss?.close; if (ss == null || ss.day != MockFmt.day(now) || c == null) return false   // ios: Mock/MockDesk.swift:1133
        return now >= c + (-600) && now < c   // ios: Mock/MockDesk.swift:1134
    }

    /** 전일 종가: 마지막 훑기의 가격·등락으로 거꾸로 셈한다 (훑기에 없으면 모름) */
    fun prevClose(code: String): Double? {   // ios: Mock/MockDesk.swift:1138
        val h = s.scan?.hits?.firstOrNull { it.code == code }; val c = h?.change; if (h == null || c == null || c <= -1) return null   // ios: Mock/MockDesk.swift:1139
        return h.price / (1 + c)   // ios: Mock/MockDesk.swift:1140
    }

    /** 거부: 장부는 그대로, 이유만 기록 (실전 주문 API 의 거부와 같은 자리) */
    private fun reject(b: MockBook, o: MockOrder, why: String, test: Boolean) {   // ios: Mock/MockDesk.swift:1144
        log(MockEvent.Kind.reject, book = b, who = o.who, code = o.code, name = o.name, text = why, test = test, side = o.side, qty = o.qty, ref = o.ref)   // ios: Mock/MockDesk.swift:1145
        notify("거부 · " + o.name, o.side.label + " ${o.qty}주 · " + why + (if (o.reason.isEmpty()) "" else "\n" + o.reason))   // ios: Mock/MockDesk.swift:1146
    }

    /** 접수 뒤 체결: 브로커가 주는 호가로 채운다 (모의는 1~2초 지연 뒤 새 호가, 장 밖 시험은 받은 호가 그대로) */
    private suspend fun settle(b: MockBook, o: MockOrder, book: MockCalc.Book, test: Boolean) {   // ios: Mock/MockDesk.swift:1150
        val nb = if (test) book else (broker(b).settle(o) ?: book)   // ios: Mock/MockDesk.swift:1151
        fill(b, orderId = o.id, book = nb, test = test)   // ios: Mock/MockDesk.swift:1152
    }

    /** 대기 주문 하나를 호가로 채워 본다. 남으면 왜 기다리는지 (바뀌었을 때만) 남긴다 */
    private fun fill(b: MockBook, orderId: String, book: MockCalc.Book, test: Boolean) {   // ios: Mock/MockDesk.swift:1156
        val l = s.ledgers.firstOrNull { it.book == b } ?: return; val i = l.pending.indexOfFirst { it.id == orderId }; if (i < 0) return   // ios: Mock/MockDesk.swift:1157
        val o = l.pending[i]   // ios: Mock/MockDesk.swift:1158
        if (o.auction == true && inCloseAuction()) {   // ios: Mock/MockDesk.swift:1159
            if (o.waitWhy == null) {   // ios: Mock/MockDesk.swift:1160
                l.pending[i].waitWhy = "마감 동시호가 · 15:30 단일가 체결 대기"   // ios: Mock/MockDesk.swift:1161
                set(l)   // ios: Mock/MockDesk.swift:1162
                log(MockEvent.Kind.wait, book = b, who = o.who, code = o.code, name = o.name, text = "마감 동시호가 · 15:30 단일가 체결 대기", test = test, ref = o.id)   // ios: Mock/MockDesk.swift:1163
            }
            return   // ios: Mock/MockDesk.swift:1165
        }
        val cwhy = broker(b).closed(o); if (cwhy != null) {   // ios: Mock/MockDesk.swift:1167
            l.cash += max(0.0, o.reserved)   // ios: Mock/MockDesk.swift:1168
            l.pending = l.pending.filterIndexed { k, _ -> k != i }   // ios: Mock/MockDesk.swift:1169
            set(l)   // ios: Mock/MockDesk.swift:1170
            log(if (cwhy.contains("거부")) MockEvent.Kind.reject else MockEvent.Kind.cancel, book = b, who = o.who, code = o.code, name = o.name, text = cwhy + " · 남은 ${o.qty}주", test = test, ref = o.id)   // ios: Mock/MockDesk.swift:1171
            return   // ios: Mock/MockDesk.swift:1172
        }
        val f = broker(b).match(o, book = book, fees = s.fees)   // ios: Mock/MockDesk.swift:1174
        // 실전 체결이 나면 토스 계좌(현금·보유)를 한 번 다시 읽는다 (평가액이 옛 계좌로 남지 않게)
        if (b == MockBook.real && f.qty > 0) scope.launch { refreshAccount() }   // ios: Mock/MockDesk.swift:1176
        if (f.qty > 0) {   // ios: Mock/MockDesk.swift:1177
            val e = if (o.side == MockSide.buy) MockCalc.applyBuy(l, orderIndex = i, fill = f, fees = s.fees, at = mockNow(), test = test)   // ios: Mock/MockDesk.swift:1178
                                   else MockCalc.applySell(l, orderIndex = i, fill = f, fees = s.fees, at = mockNow(), test = test)   // ios: Mock/MockDesk.swift:1179
            if (e != null) {   // ios: Mock/MockDesk.swift:1180
                e.expect = o.expect; e.tossId = o.tossId   // ios: Mock/MockDesk.swift:1181
                append(e)   // ios: Mock/MockDesk.swift:1182
                if (o.side == MockSide.sell && !test) addAfter(b, e)   // ios: Mock/MockDesk.swift:1183
                val left = l.pending.firstOrNull { it.id == orderId }?.qty   // ios: Mock/MockDesk.swift:1184
                notify((if (left == null) "체결 · " else "부분 체결 · ") + o.name,   // ios: Mock/MockDesk.swift:1185
                       o.side.label + " ${e.qty ?: f.qty}주 · 평균 " + MockFmt.won(f.avg) + (left?.let { " · 남은 ${it}주" } ?: "") + (if (test) " · 장 밖 시험" else "")   // ios: Mock/MockDesk.swift:1186
                       + (if (o.reason.isEmpty()) "" else "\n" + o.reason))   // ios: Mock/MockDesk.swift:1187
            }
        }
        // 다 채우지 못했으면 대기 이유
        val j = l.pending.indexOfFirst { it.id == orderId }; if (j >= 0) {   // ios: Mock/MockDesk.swift:1191
            val w = l.pending[j].copy()   // ios: Mock/MockDesk.swift:1192
            val why: String   // ios: Mock/MockDesk.swift:1193
            if (w.side == MockSide.buy && w.reserved < (book.bestAsk ?: 0.0) * (1 + s.fees.commission)) {   // ios: Mock/MockDesk.swift:1194
                // 묶은 돈으로 한 주도 못 사면 남은 몫은 취소
                l.cash += max(0.0, w.reserved)   // ios: Mock/MockDesk.swift:1196
                l.pending = l.pending.filterIndexed { k, _ -> k != j }   // ios: Mock/MockDesk.swift:1197
                set(l)   // ios: Mock/MockDesk.swift:1198
                log(MockEvent.Kind.cancel, book = b, who = w.who, code = w.code, name = w.name, text = "남은 ${w.qty}주 취소 · 묶은 돈 모자람", test = test, ref = w.id)   // ios: Mock/MockDesk.swift:1199
                return   // ios: Mock/MockDesk.swift:1200
            }
            val wl = w.limit; why = if (w.type == MockOrderType.limit && wl != null) MockCalc.waitWhy(side = w.side, limit = wl, book = book)   // ios: Mock/MockDesk.swift:1202
            else "호가 잔량 모자람 · 남은 ${w.qty}주"   // ios: Mock/MockDesk.swift:1203
            if (f.qty > 0) w.state = MockOrderState.partial   // ios: Mock/MockDesk.swift:1204
            if (w.waitWhy != why) {   // ios: Mock/MockDesk.swift:1205
                w.waitWhy = why   // ios: Mock/MockDesk.swift:1206
                l.pending = l.pending.toMutableList().also { it[j] = w }   // ios: Mock/MockDesk.swift:1207
                log(MockEvent.Kind.wait, book = b, who = w.who, code = w.code, name = w.name, text = why, test = test, ref = w.id)   // ios: Mock/MockDesk.swift:1208
            }
        }
        set(l)   // ios: Mock/MockDesk.swift:1211
    }

    /** 대기 주문 정정 (가격·수량, 정정하면 지정가). 실전이면 토스 정정을 먼저 보내고 받아들여지면 장부를 고친다 */
    suspend fun modify(b: MockBook, orderId: String, qty: Int, limit: Double): String? {   // ios: Mock/MockDesk.swift:1216
        val l0 = s.ledgers.firstOrNull { it.book == b }; val o = l0?.pending?.firstOrNull { it.id == orderId }; if (l0 == null || o == null) return "주문이 없어요"   // ios: Mock/MockDesk.swift:1217
        val t = l0.copy()   // ios: Mock/MockDesk.swift:1218
        val i0 = t.pending.indexOfFirst { it.id == orderId }; if (i0 < 0) return "주문이 없어요"   // ios: Mock/MockDesk.swift:1219
        MockCalc.modify(t, orderIndex = i0, qty = qty, limit = limit, fees = s.fees)?.let { return it }   // ios: Mock/MockDesk.swift:1220
        val bwhy = broker(b).modify(o, qty = qty, limit = MockCalc.snap(limit, side = o.side)); if (bwhy != null) {   // ios: Mock/MockDesk.swift:1221
            log(MockEvent.Kind.reject, book = b, who = MockWho.hand, code = o.code, name = o.name, text = "정정 거부 · " + bwhy, side = o.side, qty = qty, ref = o.id)   // ios: Mock/MockDesk.swift:1222
            return bwhy   // ios: Mock/MockDesk.swift:1223
        }
        // 기다리는 동안 장부가 바뀌었을 수 있으니 다시 읽고 고친다
        val l = s.ledgers.firstOrNull { it.book == b } ?: return "주문이 없어요"; val i = l.pending.indexOfFirst { it.id == orderId }; if (i < 0) return "주문이 없어요"   // ios: Mock/MockDesk.swift:1226
        MockCalc.modify(l, orderIndex = i, qty = qty, limit = limit, fees = s.fees)?.let { return it }   // ios: Mock/MockDesk.swift:1227
        set(l)   // ios: Mock/MockDesk.swift:1228
        log(MockEvent.Kind.order, book = b, who = MockWho.hand, code = o.code, name = o.name,   // ios: Mock/MockDesk.swift:1229
            text = "정정 · 지정가 ${qty}주 · " + MockFmt.won(MockCalc.snap(limit, side = o.side)) + " (전 ${o.qty}주" + (o.limit?.let { " · " + MockFmt.won(it) } ?: " · 시장가") + ")",   // ios: Mock/MockDesk.swift:1230
            side = o.side, qty = qty, ref = o.id)   // ios: Mock/MockDesk.swift:1231
        val bk = try { broker(b).book(o.code) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (bk != null) fill(b, orderId = orderId, book = bk, test = phase != Phase.open)   // ios: Mock/MockDesk.swift:1232
        save()   // ios: Mock/MockDesk.swift:1233
        return null   // ios: Mock/MockDesk.swift:1234
    }

    fun cancel(b: MockBook, orderId: String) {   // ios: Mock/MockDesk.swift:1237
        val l = s.ledgers.firstOrNull { it.book == b } ?: return; val i = l.pending.indexOfFirst { it.id == orderId }; if (i < 0) return   // ios: Mock/MockDesk.swift:1238
        val o = l.pending[i]; l.pending = l.pending.filterIndexed { k, _ -> k != i }   // ios: Mock/MockDesk.swift:1239
        l.cash += max(0.0, o.reserved)   // ios: Mock/MockDesk.swift:1240
        set(l)   // ios: Mock/MockDesk.swift:1241
        log(MockEvent.Kind.cancel, book = b, who = MockWho.hand, code = o.code, name = o.name, text = "손으로 취소 · 남은 ${o.qty}주", ref = o.id)   // ios: Mock/MockDesk.swift:1242
        scope.launch { broker(b).cancel(o) }   // ios: Mock/MockDesk.swift:1243
        save()   // ios: Mock/MockDesk.swift:1244
    }

    // MARK: 주기 (앱이 열려 있는 동안 1분마다)

    /** 지켜보는 종목 가격 받기 → 대기 주문 다시 보기 → 팔기 규칙 → 한 바퀴 자동 → AI 차례 */
    suspend fun tick() {   // ios: Mock/MockDesk.swift:1250
        if (!active || busy != null || ticking || MockFeed.base == null) return   // ios: Mock/MockDesk.swift:1251
        ticking = true   // ios: Mock/MockDesk.swift:1252
        settleDue()   // ios: Mock/MockDesk.swift:1253
        checkDaily()   // ios: Mock/MockDesk.swift:1254
        val ph = refreshPhase()   // ios: Mock/MockDesk.swift:1255
        if (ph == Phase.closed) scope.launch { curveIfNeeded() }   // ios: Mock/MockDesk.swift:1256
        val watch = mutableSetOf<String>()   // ios: Mock/MockDesk.swift:1257
        for (l in s.ledgers) { watch.addAll(l.positions.keys); watch.addAll(l.pending.map { it.code }) }   // ios: Mock/MockDesk.swift:1258
        // 토스 계좌 보유도 같은 호출로 가격을 받는다 ([실전] 평가액)
        account?.let { a -> watch.addAll(a.items.map { it.code }) }   // ios: Mock/MockDesk.swift:1260
        for (h in (s.scan?.hits ?: emptyList()).take(150)) watch.add(h.code)   // ios: Mock/MockDesk.swift:1261
        watch.addAll(followCodes(mockNow()))   // ios: Mock/MockDesk.swift:1262
        val p = if (watch.isEmpty()) null else try { MockFeed.last(watch.sorted()) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; if (p != null) {   // ios: Mock/MockDesk.swift:1263
            s.prices.putAll(p); s.pricesAt = mockNow()   // ios: Mock/MockDesk.swift:1264
            if (ph == Phase.open) markPeaks()   // ios: Mock/MockDesk.swift:1265
            fillFollows(mockNow())   // ios: Mock/MockDesk.swift:1266
            fillAfters(mockNow(), open = ph == Phase.open)   // ios: Mock/MockDesk.swift:1267
        }
        // 대기 주문: 지금 가격이 지정가를 넘었거나 시장가 남은 몫일 때만 호가를 묻는다
        for (l in s.ledgers.toList()) {   // ios: Mock/MockDesk.swift:1270
            for (o in l.pending.toList()) {   // ios: Mock/MockDesk.swift:1271
                val u = o.until; if (u != null && mockNow() >= u) { expire(l.book, orderId = o.id); continue }   // ios: Mock/MockDesk.swift:1272
                if (o.auction == true) {   // ios: Mock/MockDesk.swift:1273
                    // 마감이 지나면 단일가(종가)로 한 번에. 지정가가 안 닿으면 남는다
                    val px = s.prices[o.code]; if (inCloseAuction() || px == null || px <= 0) continue   // ios: Mock/MockDesk.swift:1275
                    val single = if (o.side == MockSide.buy) MockCalc.Book(asks = listOf(MockCalc.Level(p = px, v = 1e12)), bids = emptyList(), at = mockNow())   // ios: Mock/MockDesk.swift:1276
                                                else MockCalc.Book(asks = emptyList(), bids = listOf(MockCalc.Level(p = px, v = 1e12)), at = mockNow())   // ios: Mock/MockDesk.swift:1277
                    s.ledgers.firstOrNull { it.id == l.id }?.pending?.firstOrNull { it.id == o.id }?.let {   // ios: Mock/MockDesk.swift:1278
                        it.auction = false   // ios: Mock/MockDesk.swift:1279
                    }
                    fill(l.book, orderId = o.id, book = single, test = false)   // ios: Mock/MockDesk.swift:1281
                    continue   // ios: Mock/MockDesk.swift:1282
                }
                val px = s.prices[o.code]   // ios: Mock/MockDesk.swift:1284
                val cross: Boolean = o.type == MockOrderType.market || px == null || (if (o.side == MockSide.buy) px <= (o.limit ?: 0.0) else px >= (o.limit ?: Double.POSITIVE_INFINITY))   // ios: Mock/MockDesk.swift:1285
                val br = broker(l.book)   // ios: Mock/MockDesk.swift:1286
                if (!(cross || br.live)) continue; val book = try { br.book(o.code) } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: continue   // ios: Mock/MockDesk.swift:1287
                br.refresh(o)   // ios: Mock/MockDesk.swift:1288
                fill(l.book, orderId = o.id, book = book, test = ph != Phase.open)   // ios: Mock/MockDesk.swift:1289
            }
        }
        // 팔기 규칙 (장부마다 그 장부 보유만, 장중 가격). 실전 장부는 실전 스위치가 켜졌을 때만 (autoBooks)
        // 자동 팔기는 돌리기가 켜져 있고(간격 > 0) 돌리기 팔기 알약이 켜져 있을 때만 (2026-10-26)
        if (ph != Phase.unknown && s.sell.on && s.auto > 0 && autoSell && !halted) {   // ios: Mock/MockDesk.swift:1294
            for (b in autoBooks) {   // ios: Mock/MockDesk.swift:1295
                val (sigs, n) = sellSigs(b)   // ios: Mock/MockDesk.swift:1296
                // 1분 주기가 판 것도 팔기 칸 상태에 남긴다 ('지금 팔기'만 적어 자동으로 판 뒤에도 '없음'으로 보이던 일)
                if (sigs.isNotEmpty()) s.runs["sell"] = MockRun(at = mockNow(), ok = n == sigs.size, text = MockFmt.stamp(mockNow()) + " · 자동 · " + b.tab + " · ${n}종목 팔기 주문 · " + sigNames(sigs))   // ios: Mock/MockDesk.swift:1298
            }
        }
        // 한 바퀴 자동 (장중만)
        var picked = false   // ios: Mock/MockDesk.swift:1302
        if (s.auto > 0 && ph == Phase.open && !halted && (paused == "auto" || mockNow() - (s.runs["observe"]?.at ?: 0.0) >= (s.auto * 60).toDouble())) {   // ios: Mock/MockDesk.swift:1303
            obsFrom = "auto"   // ios: Mock/MockDesk.swift:1304
            observe()   // ios: Mock/MockDesk.swift:1305
            // 훑기가 끝났을 때만 고른다 (멈춤·실패면 옛 결과로 고르지 않는다)
            // 고르기·매수는 '고르고 주문' 구간만 (지켜보기·정리는 훑기만, 팔기 규칙은 위에서 계속 돈다)
            if (s.runs["observe"]?.ok == true && seg().buys) {   // ios: Mock/MockDesk.swift:1308
                for (b in autoBooks) { if (halted) continue; select(b, buy = autoOrder) }   // ios: Mock/MockDesk.swift:1309
                picked = true   // ios: Mock/MockDesk.swift:1310
            }
        }
        addMark()   // ios: Mock/MockDesk.swift:1313
        ticking = false   // ios: Mock/MockDesk.swift:1314
        save()   // ios: Mock/MockDesk.swift:1315
        if (picked) startAIReview()   // ios: Mock/MockDesk.swift:1316
        startAITick(phase = ph)   // ios: Mock/MockDesk.swift:1317
    }

    /** 1분 주기는 버튼 잠금(busy)과 따로 돈다 (버튼이 잠겼다 풀리며 깜빡이지 않게). 단추 일은 주기가 끝날 때까지 기다렸다 돈다
     *  시간 한도를 두지 않는다: 주기 안의 자동 훑기는 1분을 넘기기도 해서, 60초만 기다리면 단추 훑기가 겹쳐 관찰이 두 번 적혔다 (2026-10-26).
     *  주기는 모든 서버 호출에 시간 한도가 있어 반드시 끝난다
     *  기다리던 단추 일이 취소되면 false (그때는 일을 하지 않는다) */
    private suspend fun waitTick(): Boolean {   // ios: Mock/MockDesk.swift:1324
        while (ticking) {   // ios: Mock/MockDesk.swift:1325
            try { delay(200) } catch (e: CancellationException) { return false }   // ios: Mock/MockDesk.swift:1326
        }
        return true   // ios: Mock/MockDesk.swift:1328
    }

    /** 평가액 점: 5분에 하나. 이틀 지난 점은 하루 마지막 하나만 남긴다 */
    private suspend fun addMark() {   // ios: Mock/MockDesk.swift:1332
        val now = mockNow()   // ios: Mock/MockDesk.swift:1333
        var ms = (s.marks ?: emptyList()).toMutableList()   // ios: Mock/MockDesk.swift:1334
        for (b in listOf(MockBook.rule, MockBook.real)) {   // ios: Mock/MockDesk.swift:1335
            val l = ledger(b)   // ios: Mock/MockDesk.swift:1336
            val last = ms.lastOrNull { it.ledger == l.id }; if (last != null && now - last.at < 300) continue   // ios: Mock/MockDesk.swift:1337
            // [실전] 점은 토스 계좌 평가액. 실전 스위치가 켜졌거나 [실전]을 볼 때만 이 회차에 계좌를 한 번 읽고, 그래도 모르면 건너뛴다
            if (b == MockBook.real && (live || shown == MockBook.real)) refreshAccount()   // ios: Mock/MockDesk.swift:1339
            val eq = (if (b == MockBook.real) realEquity else equity(b)) ?: continue   // ios: Mock/MockDesk.swift:1340
            ms.add(MockMark(at = now, eq = eq, ledger = l.id))   // ios: Mock/MockDesk.swift:1341
        }
        val cut = now + (-2 * 86_400)   // ios: Mock/MockDesk.swift:1343
        val lastOfDay = mutableMapOf<String, Int>()   // ios: Mock/MockDesk.swift:1344
        for ((i, m) in ms.withIndex()) { if (m.at < cut) lastOfDay[MockFmt.day(m.at) + m.ledger] = i }   // ios: Mock/MockDesk.swift:1345
        val keep = lastOfDay.values.toSet()   // ios: Mock/MockDesk.swift:1346
        ms = ms.withIndex().filter { it.value.at >= cut || keep.contains(it.index) }.map { it.value }.toMutableList()   // ios: Mock/MockDesk.swift:1347
        s.marks = ms   // ios: Mock/MockDesk.swift:1348
    }

    // MARK: 결제 · 잔고 맞추기 · 체결 알림

    /** 결제일이 된 판 돈을 현금으로 (앱을 열 때와 1분 주기마다) */
    private fun settleDue() {   // ios: Mock/MockDesk.swift:1354
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1355
        var moved = false   // ios: Mock/MockDesk.swift:1356
        for (l in s.ledgers) {   // ios: Mock/MockDesk.swift:1357
            for (x in MockCalc.settle(l, today = today)) {   // ios: Mock/MockDesk.swift:1358
                moved = true   // ios: Mock/MockDesk.swift:1359
                log(MockEvent.Kind.settle, book = l.book, code = x.code, name = name(x.code), text = "결제 · " + MockFmt.won(x.amount) + " 현금으로 (T+2)")   // ios: Mock/MockDesk.swift:1360
            }
        }
        if (moved) save()   // ios: Mock/MockDesk.swift:1363
    }

    /** 하루 한 번 장부를 체결 기록과 견준다. 어긋나도 고치지 않고 표시만 */
    private fun checkDaily() {   // ios: Mock/MockDesk.swift:1367
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1368
        if (s.checkDay == today) return   // ios: Mock/MockDesk.swift:1369
        runCheck()   // ios: Mock/MockDesk.swift:1370
        armIfReady()   // ios: Mock/MockDesk.swift:1371
        save()   // ios: Mock/MockDesk.swift:1372
    }
    fun runCheck() {   // ios: Mock/MockDesk.swift:1374
        val out = (s.checks ?: mutableMapOf()).toMutableMap()   // ios: Mock/MockDesk.swift:1375
        for (l in s.ledgers) {   // ios: Mock/MockDesk.swift:1376
            val bad = MockCalc.reconcile(l, events = events)   // ios: Mock/MockDesk.swift:1377
            out[l.book.name] = MockRun(at = mockNow(), ok = bad == null, text = bad ?: "맞음")   // ios: Mock/MockDesk.swift:1378
            if (bad != null) log(MockEvent.Kind.check, book = l.book, text = "어긋남 · " + bad)   // ios: Mock/MockDesk.swift:1379
        }
        s.checks = out   // ios: Mock/MockDesk.swift:1381
        s.checkDay = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1382
        save()   // ios: Mock/MockDesk.swift:1383
    }
    fun check(b: MockBook): MockRun? = s.checks?.get(b.name)   // ios: Mock/MockDesk.swift:1385

    val fillAlert: Boolean get() = s.fillAlert ?: true   // ios: Mock/MockDesk.swift:1387
    fun setFillAlert(on: Boolean) {   // ios: Mock/MockDesk.swift:1388
        edit(if (on) "체결 알림 켬" else "체결 알림 끔") { it.fillAlert = on }   // ios: Mock/MockDesk.swift:1389
        if (on) scope.launch { Notifier.request() }   // ios: Mock/MockDesk.swift:1390
    }
    /** 체결 · 부분 체결 · 거부 알림 */
    private fun notify(title: String, body: String) {   // ios: Mock/MockDesk.swift:1393
        if (!fillAlert) return   // ios: Mock/MockDesk.swift:1394
        scope.launch {   // ios: Mock/MockDesk.swift:1395
            when (Notifier.status()) {   // ios: Mock/MockDesk.swift:1396
                Notifier.Status.denied -> return@launch   // ios: Mock/MockDesk.swift:1397
                Notifier.Status.unknown -> if (!Notifier.request()) return@launch   // ios: Mock/MockDesk.swift:1398
                Notifier.Status.allowed -> {}   // ios: Mock/MockDesk.swift:1399
            }
            Notifier.post(Defaults.ctx, id = "mock." + newId(), title = title, body = body, open = null, icon = null)   // ios: Mock/MockDesk.swift:1401,1402,1404
        }
    }

    // MARK: 장부·설정 바꾸기

    /** 장 밖 시험으로 산 몫만 장부에서 빼고 들어간 돈을 현금으로 돌린다 (기록은 남긴다) */
    fun dropTest(b: MockBook) {   // ios: Mock/MockDesk.swift:1411
        val i = s.ledgers.indexOfFirst { it.book == b }; if (i < 0) return   // ios: Mock/MockDesk.swift:1412
        val l = s.ledgers[i]   // ios: Mock/MockDesk.swift:1413
        val pos = l.positions.toMutableMap()
        for ((code, p0) in l.positions) { if (!p0.test) continue   // ios: Mock/MockDesk.swift:1414
            val p = p0.copy()   // ios: Mock/MockDesk.swift:1415
            val q = min(p.qty, p0.tQty ?: p.qty); val c = p0.tCost ?: p.cost; val g = p0.tGross ?: p.gross   // ios: Mock/MockDesk.swift:1416
            p.qty -= q; p.cost -= c; p.gross -= g   // ios: Mock/MockDesk.swift:1417
            p.test = false; p.tQty = null; p.tCost = null; p.tGross = null   // ios: Mock/MockDesk.swift:1418
            l.cash += c; l.fees -= c - g   // ios: Mock/MockDesk.swift:1419
            if (p.qty > 0) pos[code] = p else pos.remove(code)   // ios: Mock/MockDesk.swift:1420
            log(MockEvent.Kind.cancel, book = b, who = p.who, code = code, name = p.name, text = "시험 몫 뺌 · ${q}주 · 돌린 돈 " + MockFmt.won(c), test = true, side = MockSide.buy, qty = q, amount = c, ref = p.buyRef)   // ios: Mock/MockDesk.swift:1421
        }
        l.positions = pos
        s.ledgers[i] = l   // ios: Mock/MockDesk.swift:1423
        save()   // ios: Mock/MockDesk.swift:1424
    }

    /** 장부를 처음부터. 옛 장부는 닫아서 보관한다 (기록도 그대로) */
    fun reset(b: MockBook) {   // ios: Mock/MockDesk.swift:1428
        val i = s.ledgers.indexOfFirst { it.book == b }; if (i < 0) return   // ios: Mock/MockDesk.swift:1429
        val old = s.ledgers[i]   // ios: Mock/MockDesk.swift:1430
        old.ended = mockNow()   // ios: Mock/MockDesk.swift:1431
        s.archived.add(old)   // ios: Mock/MockDesk.swift:1432
        val n = MockLedger(book = b, started = mockNow())   // ios: Mock/MockDesk.swift:1433
        s.ledgers[i] = n   // ios: Mock/MockDesk.swift:1434
        if (b == (s.picksBook ?: MockBook.rule)) { s.picks = mutableListOf(); s.skips = mutableListOf(); s.aiViews = mutableMapOf(); s.dropped = mutableListOf(); lastPicks = emptyList() }   // ios: Mock/MockDesk.swift:1435
        if (b != MockBook.ai) s.marks = (s.marks ?: emptyList()).filter { it.ledger != old.id }.toMutableList()   // ios: Mock/MockDesk.swift:1436
        if (b == MockBook.ai) s.aiNote = null   // ios: Mock/MockDesk.swift:1437
        log(MockEvent.Kind.reset, book = b, text = "${b.label} 처음부터 · 옛 장부 평가 ${MockFmt.won(MockCalc.equity(old, prices = s.prices))} 보관")   // ios: Mock/MockDesk.swift:1438
        save()   // ios: Mock/MockDesk.swift:1439
    }

    /** 조건·규칙이 바뀐 때를 기록한다 (구성 변화가 성과처럼 보이지 않게) */
    fun edit(what: String, change: (MockState) -> Unit) {   // ios: Mock/MockDesk.swift:1443
        change(_s)   // ios: Mock/MockDesk.swift:1444
        log(MockEvent.Kind.rules, book = MockBook.rule, text = what)   // ios: Mock/MockDesk.swift:1445
        save()   // ios: Mock/MockDesk.swift:1446
    }
    /** 걸린 종목 뒤 흐름 모두 지우기 (그 흐름의 지난 AI 조언도). 장부를 가리지 않고 하나로 쌓여 모두 지운다 */
    fun clearFollows() {   // ios: Mock/MockDesk.swift:1449
        edit("걸린 종목 뒤 흐름 지움") { s ->   // ios: Mock/MockDesk.swift:1450
            s.follows = null   // ios: Mock/MockDesk.swift:1451
            s.flowReads?.removeAll { it.kind == MockFlowRead.Kind.follow }   // ios: Mock/MockDesk.swift:1452
        }
    }
    /** 판 뒤 흐름 지우기: 그 장부 것만 (그 장부의 판 뒤 흐름 AI 조언도) */
    fun clearAfters(b: MockBook) {   // ios: Mock/MockDesk.swift:1456
        edit("판 뒤 흐름 지움 · " + (if (b == MockBook.real) "실전" else "모의")) { s ->   // ios: Mock/MockDesk.swift:1457
            s.afters?.removeAll { it.book == b }   // ios: Mock/MockDesk.swift:1458
            s.flowReads?.removeAll { it.kind == MockFlowRead.Kind.after && it.book == b }   // ios: Mock/MockDesk.swift:1459
        }
    }
    /** 판 뒤 흐름 한 건 지우기 (판 시각·종목으로 찾는다) */
    fun deleteAfter(x: MockAfter) {   // ios: Mock/MockDesk.swift:1463
        edit("판 뒤 흐름 한 건 지움 · " + x.name) { s ->   // ios: Mock/MockDesk.swift:1464
            s.afters?.removeAll { it.at == x.at && it.code == x.code && it.book == x.book }   // ios: Mock/MockDesk.swift:1465
        }
    }
    /** 관찰 조건 알약을 바꾼다 (켜기·끄기·넣기·고치기·지우기) */
    fun setConds(what: String, new: List<MockCond>) {   // ios: Mock/MockDesk.swift:1469
        edit("관찰 조건 · " + what) { it.conds = new.toMutableList() }   // ios: Mock/MockDesk.swift:1470
    }

    /** 고르기 조건 알약을 바꾸고 선정 규칙 값을 맞춘다 (끈 조건은 막지 않는 값: 상위 10 · 한도 없음 · 현금 남김 없음) */
    fun setRuleConds(what: String, new: List<MockRuleCond>) {   // ios: Mock/MockDesk.swift:1474
        edit("고르기 조건 · " + what) { applyRule(it, new) }   // ios: Mock/MockDesk.swift:1475
    }
    fun applyRule(st: MockState, new: List<MockRuleCond>) {   // ios: Mock/MockDesk.swift:1477
        st.rconds = new.toMutableList()   // ios: Mock/MockDesk.swift:1478
        val on = new.filter { it.on }   // ios: Mock/MockDesk.swift:1479
        st.select.count = on.firstOrNull { it.kind == MockRuleCond.Kind.top }?.n ?: 10   // ios: Mock/MockDesk.swift:1480
        st.select.maxPer = on.filter { it.kind == MockRuleCond.Kind.per }.map { it.pct }.minOrNull() ?: 1.0   // ios: Mock/MockDesk.swift:1481
        st.select.keepCash = on.filter { it.kind == MockRuleCond.Kind.cash }.map { it.pct }.maxOrNull() ?: 0.0   // ios: Mock/MockDesk.swift:1482
        st.select.slip = on.filter { it.kind == MockRuleCond.Kind.slip }.map { it.pct }.minOrNull()   // ios: Mock/MockDesk.swift:1483
        st.select.split = on.filter { it.kind == MockRuleCond.Kind.split }.map { it.pct }.minOrNull()   // ios: Mock/MockDesk.swift:1484
        st.select.name = if (on.joinToString(" · ") { it.text }.isEmpty()) "조건 없음" else on.joinToString(" · ") { it.text }   // ios: Mock/MockDesk.swift:1485
    }

    fun setSellConds(what: String, new: List<MockSellCond>) {   // ios: Mock/MockDesk.swift:1488
        // 흔들림 손절을 손으로 만지면 저절로 켜기에서 뺀다
        if (sconds.filter { it.kind == MockSellCond.Kind.vol } != new.filter { it.kind == MockSellCond.Kind.vol }) touchArm("vol")   // ios: Mock/MockDesk.swift:1490
        edit("팔기 조건 · " + what) { applySell(it, new) }   // ios: Mock/MockDesk.swift:1491
    }
    fun applySell(st: MockState, new: List<MockSellCond>) {   // ios: Mock/MockDesk.swift:1493
        val before = st.sell.copy()   // ios: Mock/MockDesk.swift:1494
        st.sconds = new.toMutableList()   // ios: Mock/MockDesk.swift:1495
        val on = new.filter { it.on }   // ios: Mock/MockDesk.swift:1496
        st.sell.stopLoss = on.filter { it.kind == MockSellCond.Kind.loss }.map { it.pct }.minOrNull()   // ios: Mock/MockDesk.swift:1497
        st.sell.takeProfit = on.filter { it.kind == MockSellCond.Kind.gain }.map { it.pct }.minOrNull()   // ios: Mock/MockDesk.swift:1498
        st.sell.maxDays = on.filter { it.kind == MockSellCond.Kind.days }.map { it.n }.minOrNull()   // ios: Mock/MockDesk.swift:1499
        val idle = on.firstOrNull { it.kind == MockSellCond.Kind.idle }   // ios: Mock/MockDesk.swift:1500
        st.sell.idleMin = idle?.n; st.sell.idleBand = idle?.pct   // ios: Mock/MockDesk.swift:1501
        st.sell.gone = if (on.any { it.kind == MockSellCond.Kind.gone }) true else null   // ios: Mock/MockDesk.swift:1502
        st.sell.closeOut = if (on.any { it.kind == MockSellCond.Kind.close }) true else null   // ios: Mock/MockDesk.swift:1503
        st.sell.trail = on.filter { it.kind == MockSellCond.Kind.trail }.map { it.pct }.minOrNull()   // ios: Mock/MockDesk.swift:1504
        st.sell.volK = on.filter { it.kind == MockSellCond.Kind.vol }.map { it.pct }.minOrNull()   // ios: Mock/MockDesk.swift:1505
        // 규칙이 바뀐 날을 적는다. 그 전에 산 몫은 손절·익절만 본다
        val a = before.copy(); val b = st.sell.copy()   // ios: Mock/MockDesk.swift:1507
        a.since = null; b.since = null   // ios: Mock/MockDesk.swift:1508
        if (a != b) { st.sell.since = MockFmt.day(mockNow()); st.sellCheck = true }   // ios: Mock/MockDesk.swift:1509
    }

    fun touchArm(key: String) {   // ios: Mock/MockDesk.swift:1512
        if ((s.armTouched ?: emptyList()).contains(key)) return   // ios: Mock/MockDesk.swift:1513
        s.armTouched = ((s.armTouched ?: emptyList()) + listOf(key)).toMutableList()   // ios: Mock/MockDesk.swift:1514
    }

    /** 거래량 곡선이 쌓인 날 수 (27칸 곡선만) */
    val curveDays: Int get() = (s.curves ?: emptyList()).count { it.cum.size == 27 }   // ios: Mock/MockDesk.swift:1518

    /** 새 배수 알약: 곡선이 MockClock.newVolDays 일 쌓이면 저절로 켠다. 손으로 만졌으면 두지 않는다 */
    private fun armNewVol() {   // ios: Mock/MockDesk.swift:1521
        val n = curveDays   // ios: Mock/MockDesk.swift:1522
        if (s.newVol == true || (s.armTouched ?: emptyList()).contains("newVol") || n < MockClock.newVolDays) return   // ios: Mock/MockDesk.swift:1523
        s.newVol = true   // ios: Mock/MockDesk.swift:1524
        touchArm("newVol")   // ios: Mock/MockDesk.swift:1525
        val why = "저절로 켬 · 새 배수 · 거래량 곡선 ${n}일"   // ios: Mock/MockDesk.swift:1526
        log(MockEvent.Kind.rules, book = MockBook.rule, text = why)   // ios: Mock/MockDesk.swift:1527
        notify("조건 켬 · 새 배수", why)   // ios: Mock/MockDesk.swift:1528
    }

    /** 새 배수 알약을 손으로 켜고 끈다 (곡선이 하루라도 있으면). 만지면 저절로 켜기에서 뺀다 */
    fun setNewVol(on: Boolean) {   // ios: Mock/MockDesk.swift:1532
        touchArm("newVol")   // ios: Mock/MockDesk.swift:1533
        edit(if (on) "새 배수 켬" else "새 배수 끔") { it.newVol = if (on) true else false }   // ios: Mock/MockDesk.swift:1534
    }

    /** 꺼 둔 조건을 기록이 쌓이면 저절로 켠다 (하루 한 번, [모의] 장부 기록으로). 손으로 만진 조건은 두지 않는다 */
    private fun armIfReady() {   // ios: Mock/MockDesk.swift:1538
        val l = ledger(MockBook.rule); val g = guards   // ios: Mock/MockDesk.swift:1539
        val k = sconds.firstOrNull { it.kind == MockSellCond.Kind.vol }?.pct ?: 1.0   // ios: Mock/MockDesk.swift:1540
        val (n, days, arms) = MockCalc.arms(events, ledger = l.id, fees = s.fees, volK = k, streak = g.lossStreak ?: 3, wall = g.bookWall ?: 3.0)   // ios: Mock/MockDesk.swift:1541
        if (n < MockCalc.armTrades || days < MockCalc.armDays) return   // ios: Mock/MockDesk.swift:1542
        for (a in arms) { if ((s.armTouched ?: emptyList()).contains(a.key) || a.hits < MockCalc.armHits || a.gain <= 0) continue   // ios: Mock/MockDesk.swift:1543
            val name: String   // ios: Mock/MockDesk.swift:1544
            when (a.key) {   // ios: Mock/MockDesk.swift:1545
                "vol" -> {   // ios: Mock/MockDesk.swift:1546
                    val cs = sconds.toMutableList()   // ios: Mock/MockDesk.swift:1547
                    val i = cs.indexOfFirst { it.kind == MockSellCond.Kind.vol }; if (i >= 0) cs[i] = cs[i].copy(on = true) else cs.add(MockSellCond(kind = MockSellCond.Kind.vol, pct = k))   // ios: Mock/MockDesk.swift:1548
                    applySell(_s, cs)   // ios: Mock/MockDesk.swift:1549
                    name = "흔들림 손절 ×" + MockFmt.num(k)   // ios: Mock/MockDesk.swift:1550
                }
                "streak" -> {   // ios: Mock/MockDesk.swift:1551
                    val x = guards.copy(); x.lossStreak = g.lossStreak ?: 3; s.guards = x   // ios: Mock/MockDesk.swift:1552
                    name = "연속 손절 ${g.lossStreak ?: 3}번이면 멈춤"   // ios: Mock/MockDesk.swift:1553
                }
                else -> {   // ios: Mock/MockDesk.swift:1554
                    val x = guards.copy(); x.bookWall = g.bookWall ?: 3.0; s.guards = x   // ios: Mock/MockDesk.swift:1555
                    name = "호가 벽 ${MockFmt.num(g.bookWall ?: 3.0)}배면 미룸"   // ios: Mock/MockDesk.swift:1556
                }
            }
            touchArm(a.key)   // ios: Mock/MockDesk.swift:1558
            val why = "저절로 켬 · $name · 매매 ${n}건 · ${days}일 · 걸렸을 ${a.hits}건 · 켰다면 손익 " + MockFmt.won(a.gain) + " 나음"   // ios: Mock/MockDesk.swift:1559
            log(MockEvent.Kind.rules, book = MockBook.rule, text = why)   // ios: Mock/MockDesk.swift:1560
            notify("조건 켬 · " + name, why)   // ios: Mock/MockDesk.swift:1561
        }
    }

    /** 기록 없이 바꾸는 화면 값 (보이는 장부·지금 쓰는 묶음 고르기 등) */
    fun setView(change: (MockState) -> Unit) { change(_s); save() }   // ios: Mock/MockDesk.swift:1566

    // MARK: 기록

    fun log(k: MockEvent.Kind, id: String = newId(), book: MockBook, who: MockWho? = null, code: String? = null, name: String? = null, text: String,   // ios: Mock/MockDesk.swift:1571
             test: Boolean = false, bundle: String? = null, side: MockSide? = null, qty: Int? = null, amount: Double? = null, ref: String? = null, ai: MockAIUse? = null): MockEvent {   // ios: Mock/MockDesk.swift:1572
        val e = MockEvent(id = id, at = mockNow(), ledger = ledger(book).id, book = book, kind = k, who = who, code = code, name = name, text = text, test = test,   // ios: Mock/MockDesk.swift:1573
                          bundle = bundle ?: (if (book != MockBook.ai && k in listOf(MockEvent.Kind.select, MockEvent.Kind.order)) this.bundle?.name else null),   // ios: Mock/MockDesk.swift:1574
                          rule = if (book != MockBook.ai && k in listOf(MockEvent.Kind.select, MockEvent.Kind.order, MockEvent.Kind.fill)) s.select.name else null,   // ios: Mock/MockDesk.swift:1575
                          side = side, qty = qty, amount = amount, ref = ref, ai = ai)   // ios: Mock/MockDesk.swift:1576
        append(e)   // ios: Mock/MockDesk.swift:1577
        return e   // ios: Mock/MockDesk.swift:1578
    }

    private fun append(e: MockEvent) {   // ios: Mock/MockDesk.swift:1581
        if (e.rule == null && e.book != MockBook.ai && e.kind == MockEvent.Kind.fill) e.rule = s.select.name   // ios: Mock/MockDesk.swift:1582,1583
        _events.add(e)   // ios: Mock/MockDesk.swift:1584
        appendLine(e)   // ios: Mock/MockDesk.swift:1585
        rev++
    }

    private fun set(l: MockLedger) {   // ios: Mock/MockDesk.swift:1588
        val i = s.ledgers.indexOfFirst { it.id == l.id }; if (i >= 0) s.ledgers[i] = l   // ios: Mock/MockDesk.swift:1589
    }

    // MARK: 기록 줄 지우기·숨기기 (줄을 왼쪽으로 밀어서)

    val hiddenIds: Set<String> get() = (s.hidden ?: emptyList()).toSet()   // ios: Mock/MockDesk.swift:1594

    /** 판 이유 전문. 전문을 적기 전 체결은 그날 앞선 매도 신호 기록에서 찾는다 */
    fun sellWhy(e: MockEvent): String? {   // ios: Mock/MockDesk.swift:1597
        if (e.side != MockSide.sell) return null   // ios: Mock/MockDesk.swift:1598
        e.exitWhy?.let { return it }   // ios: Mock/MockDesk.swift:1599
        val day = MockFmt.day(e.at)   // ios: Mock/MockDesk.swift:1600
        return events.lastOrNull { it.kind == MockEvent.Kind.sell && it.code == e.code && it.book == e.book && it.at <= e.at && MockFmt.day(it.at) == day }?.text ?: e.exit   // ios: Mock/MockDesk.swift:1601
    }

    /** 체결·주문 등 계산에 쓰는 기록은 화면에서만 숨긴다 */
    fun hide(ids: List<String>) {   // ios: Mock/MockDesk.swift:1605
        s.hidden = hiddenIds.union(ids).toMutableList()   // ios: Mock/MockDesk.swift:1606
        save()   // ios: Mock/MockDesk.swift:1607
    }

    /** 오류·규칙 바뀜 줄은 계산에 쓰지 않아 기록 파일에서 실제로 지운다 */
    fun deleteNotes(ids: Set<String>) {   // ios: Mock/MockDesk.swift:1611
        val n = _events.size   // ios: Mock/MockDesk.swift:1612
        _events.removeAll { (it.kind == MockEvent.Kind.error || it.kind == MockEvent.Kind.rules) && ids.contains(it.id) }   // ios: Mock/MockDesk.swift:1613
        if (_events.size != n) { rewriteLines(_events); rev++ }   // ios: Mock/MockDesk.swift:1614
    }

    /** 관찰 기록 한 줄 지우기 (빨간 점 셈에서도 빠진다) */
    fun deleteSeen(code: String, at: MockDate) {   // ios: Mock/MockDesk.swift:1618
        s.seen?.get(code)?.let { xs -> s.seen?.set(code, xs.filter { it.at != at }) }   // ios: Mock/MockDesk.swift:1619
        if (s.seen?.get(code)?.isEmpty() == true) s.seen?.remove(code)   // ios: Mock/MockDesk.swift:1620
        save()   // ios: Mock/MockDesk.swift:1621
    }

    /** 오늘 걸린 횟수 지우기: 그 종목의 오늘 관찰 기록을 모두 지운다 (지난 날 기록 · 15분 재확인 시각은 그대로) */
    fun clearSeenToday(code: String) {   // ios: Mock/MockDesk.swift:1625
        val today = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1626
        s.seen?.get(code)?.let { xs -> s.seen?.set(code, xs.filter { MockFmt.day(it.at) != today }) }   // ios: Mock/MockDesk.swift:1627
        if (s.seen?.get(code)?.isEmpty() == true) s.seen?.remove(code)   // ios: Mock/MockDesk.swift:1628
        save()   // ios: Mock/MockDesk.swift:1629
    }

    // MARK: 파일 (기기 안, 앱 전용 폴더. 위젯·동기화와 나누지 않는다)

    private val dir: File get() {   // ios: Mock/MockDesk.swift:1634
        val d = File(Defaults.ctx.filesDir, "mock")
        d.mkdirs()   // ios: Mock/MockDesk.swift:1636
        return d   // ios: Mock/MockDesk.swift:1637
    }

    private fun loadState(): MockState? {   // ios: Mock/MockDesk.swift:1642
        val f = File(dir, "state.json"); if (!f.exists()) return null; val d = runCatching { f.readText() }.getOrNull() ?: return null   // ios: Mock/MockDesk.swift:1643
        runCatching { NJson.decodeFromString(MockState.serializer(), d) }.getOrNull()?.let { return it }   // ios: Mock/MockDesk.swift:1644
        keepBroken()   // ios: Mock/MockDesk.swift:1645
        return null   // ios: Mock/MockDesk.swift:1646
    }
    fun save() {   // ios: Mock/MockDesk.swift:1648
        rev++
        val d = runCatching { NJson.encodeToString(MockState.serializer(), _s) }.getOrNull() ?: return   // ios: Mock/MockDesk.swift:1649
        runCatching { val t = File(dir, "state.json.tmp"); t.writeText(d); t.renameTo(File(dir, "state.json")) }   // ios: Mock/MockDesk.swift:1650
    }
    private fun loadEvents(): List<MockEvent> {   // ios: Mock/MockDesk.swift:1652
        val f = File(dir, "journal.jsonl"); if (!f.exists()) return emptyList()   // ios: Mock/MockDesk.swift:1653
        return runCatching { f.readText().split("\n") }.getOrDefault(emptyList()).filter { it.isNotBlank() }.mapNotNull { runCatching { NJson.decodeFromString(MockEvent.serializer(), it) }.getOrNull() }   // ios: Mock/MockDesk.swift:1654
    }
    /** 기록 파일을 통째로 다시 쓴다 (AI 기록 지우기 · 오류·규칙 줄 지우기) */
    private fun rewriteLines(es: List<MockEvent>) {   // ios: Mock/MockDesk.swift:1657
        val d = StringBuilder()   // ios: Mock/MockDesk.swift:1658
        for (e in es) { runCatching { NJson.encodeToString(MockEvent.serializer(), e) }.getOrNull()?.let { d.append(it); d.append("\n") } }   // ios: Mock/MockDesk.swift:1659
        runCatching { val t = File(dir, "journal.jsonl.tmp"); t.writeText(d.toString()); t.renameTo(File(dir, "journal.jsonl")) }   // ios: Mock/MockDesk.swift:1660
    }
    /** 내보낼 파일: 장부 상태와 기록 (있는 것만) */
    val exportFiles: List<File> get() =   // ios: Mock/MockDesk.swift:1663
        listOf("state.json", "journal.jsonl").map { File(dir, it) }.filter { it.exists() }   // ios: Mock/MockDesk.swift:1664
    private fun appendLine(e: MockEvent) {   // ios: Mock/MockDesk.swift:1666
        val d = runCatching { NJson.encodeToString(MockEvent.serializer(), e) }.getOrNull() ?: return   // ios: Mock/MockDesk.swift:1667
        runCatching { File(dir, "journal.jsonl").appendText(d + "\n") }   // ios: Mock/MockDesk.swift:1668,1669,1670,1671,1672,1673,1674,1675
    }

    // MARK: AI (조언자. 규칙 장부는 고치지 않고 자기 장부만 굴린다)

    fun aiName(kind: String): String =   // ios: Mock/MockDesk.swift:1683
        when (kind) {   // ios: Mock/MockDesk.swift:1684
            "ledger" -> "장부"   // ios: Mock/MockDesk.swift:1685
            "review" -> "선정 의견"   // ios: Mock/MockDesk.swift:1686
            "daily" -> "하루 정리"   // ios: Mock/MockDesk.swift:1687
            "words" -> "말로 조건"   // ios: Mock/MockDesk.swift:1688
            "follow" -> "걸린 종목 흐름 읽기"   // ios: Mock/MockDesk.swift:1689
            "after" -> "판 뒤 흐름 읽기"   // ios: Mock/MockDesk.swift:1690
            else -> kind   // ios: Mock/MockDesk.swift:1691
        }

    /** 1분 주기 끝에 부른다. 때가 된 AI 일 하나만 (AI 끼리는 하나씩, 다른 일은 막지 않는다) */
    fun startAITick(phase: Phase) {   // ios: Mock/MockDesk.swift:1696
        val ph = phase
        if (!MockAI.on || aiBusy != null) return   // ios: Mock/MockDesk.swift:1697
        val now = mockNow(); val a = s.ai   // ios: Mock/MockDesk.swift:1698
        val ss = s.session; val c = ss?.close
        if (a.ledgerEvery > 0 && ph == Phase.open && now - (s.aiSeen["ledger"] ?: 0.0) >= (a.ledgerEvery * 60).toDouble()) {   // ios: Mock/MockDesk.swift:1699
            scope.launch { runAI("ledger") }   // ios: Mock/MockDesk.swift:1700
        } else if (a.daily && ph == Phase.closed && ss != null && ss.day == MockFmt.day(now) && c != null && now >= c && s.aiDailyDay != ss.day) {   // ios: Mock/MockDesk.swift:1701
            scope.launch { runAI("daily") }   // ios: Mock/MockDesk.swift:1702
        }
    }

    /** 규칙이 고른 뒤 의견을 묻는다. 다른 AI 일이 돌고 있으면 끝날 때까지 (2분 반까지) 기다린다 */
    fun startAIReview() {   // ios: Mock/MockDesk.swift:1707
        if (!MockAI.on || !s.ai.reviewEach || lastPicks.isEmpty()) return   // ios: Mock/MockDesk.swift:1708
        scope.launch {   // ios: Mock/MockDesk.swift:1709
            var n = 0   // ios: Mock/MockDesk.swift:1710
            while (aiBusy != null && n < 75) { delay(2_000); n += 1 }   // ios: Mock/MockDesk.swift:1711
            runAI("review")   // ios: Mock/MockDesk.swift:1712
        }
    }

    /** AI 일 하나. 설정·분석 화면의 '지금 돌리기'도 이것을 부른다 (장 밖이면 AI 장부 주문은 시험 체결) */
    suspend fun runAI(kind: String) {   // ios: Mock/MockDesk.swift:1717
        if (!MockAI.on || aiBusy != null || !active) return   // ios: Mock/MockDesk.swift:1718
        aiBusy = kind   // ios: Mock/MockDesk.swift:1719
        try { runAIBody(kind) } finally { aiBusy = null; save() }   // ios: Mock/MockDesk.swift:1720
    }
    private suspend fun runAIBody(kind: String) {
        s.aiSeen[kind] = mockNow()   // ios: Mock/MockDesk.swift:1721
        val day = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1722
        when (kind) {   // ios: Mock/MockDesk.swift:1723
            "ledger" -> {   // ios: Mock/MockDesk.swift:1724
                val ex = excluded()   // ios: Mock/MockDesk.swift:1725
                val hits = (s.scan?.hits ?: emptyList()).filter { ex[it.code] == null }.take(30)   // ios: Mock/MockDesk.swift:1726
                val prompt = MockAI.ledgerPrompt(ledger(MockBook.ai), prices = s.prices, hits = hits, bundleName = s.scan?.bundleName, excluded = ex, day = day)   // ios: Mock/MockDesk.swift:1727
                if (prompt == null) {
                    s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} AI 장부 · 후보·보유 없음 · 안 부름")   // ios: Mock/MockDesk.swift:1728
                    return   // ios: Mock/MockDesk.swift:1729
                }
                val o = ask(kind, prompt, book = MockBook.ai) ?: return   // ios: Mock/MockDesk.swift:1731
                val cands = LinkedHashMap<String, String>().also { m -> hits.forEach { h -> m.putIfAbsent(h.code, h.name) } }   // ios: Mock/MockDesk.swift:1732
                // 기다리는 동안 장부가 바뀌었을 수 있으니 답을 받은 뒤의 장부로 확인한다
                @Suppress("UNCHECKED_CAST")
                val r = MockAI.ledgerPlans(o["answer"] as? Map<String, Any?> ?: emptyMap(), ledger = ledger(MockBook.ai), candidates = cands, excluded = excluded())   // ios: Mock/MockDesk.swift:1734
                s.aiNote = if (r.note.isEmpty()) null else r.note   // ios: Mock/MockDesk.swift:1735
                val ev = log(MockEvent.Kind.ai, book = MockBook.ai, who = MockWho.ai,   // ios: Mock/MockDesk.swift:1736
                             text = "AI 장부 · " + (if (r.note.isEmpty()) "판단 글 없음" else r.note) + " · 주문 ${r.plans.size}" + (if (r.dropped.isEmpty()) "" else " · 버린 " + r.dropped.joinToString(", ")),   // ios: Mock/MockDesk.swift:1737
                             ai = MockAI.use(o, kind = kind, summary = "후보 ${hits.size} · 보유 ${ledger(MockBook.ai).positions.size}"))   // ios: Mock/MockDesk.swift:1738
                var n = 0   // ios: Mock/MockDesk.swift:1739
                for (p in r.plans) {   // ios: Mock/MockDesk.swift:1740
                    val ok: Boolean   // ios: Mock/MockDesk.swift:1741
                    if (p.side == MockSide.buy) ok = placeBuy(MockBook.ai, code = p.code, name = p.name, budget = p.won, who = MockWho.ai, reason = "AI · " + p.why, ref = ev.id)   // ios: Mock/MockDesk.swift:1742
                    else ok = placeSell(MockBook.ai, code = p.code, qty = p.qty, who = MockWho.ai, reason = "AI · " + p.why, ref = ev.id)   // ios: Mock/MockDesk.swift:1743
                    if (ok) n += 1   // ios: Mock/MockDesk.swift:1744
                }
                s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} AI 장부 · 주문 ${n}건" + (if (o["cached"] as? Boolean ?: false) " · 지난 답" else ""))   // ios: Mock/MockDesk.swift:1746
            }
            "review" -> {   // ios: Mock/MockDesk.swift:1747
                val picks = lastPicks   // ios: Mock/MockDesk.swift:1748
                val prompt = MockAI.reviewPrompt(picks, bundleName = s.scan?.bundleName, day = day) ?: return   // ios: Mock/MockDesk.swift:1749
                val o = ask(kind, prompt, book = MockBook.rule) ?: return   // ios: Mock/MockDesk.swift:1750
                @Suppress("UNCHECKED_CAST")
                val ans = o["answer"] as? Map<String, Any?> ?: emptyMap()   // ios: Mock/MockDesk.swift:1751
                val v = MockAI.reviewViews(ans, picks = picks)   // ios: Mock/MockDesk.swift:1752
                s.aiViews = v.toMutableMap()   // ios: Mock/MockDesk.swift:1753
                val c = { k: String -> v.values.count { it.view == k } }   // ios: Mock/MockDesk.swift:1754
                val note = ((ans["note"] as? String) ?: "").take(120)   // ios: Mock/MockDesk.swift:1755
                log(MockEvent.Kind.ai, book = MockBook.rule, who = MockWho.ai, text = "선정 의견 · 찬성 ${c("agree")} · 글쎄 ${c("doubt")} · 빼자 ${c("drop")}" + (if (note.isEmpty()) "" else " · " + note),
                    ref = s.scanEvent, ai = MockAI.use(o, kind = kind, summary = "고른 ${picks.size}"))   // ios: Mock/MockDesk.swift:1757
                s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} AI 선정 의견 · ${v.size}종목")   // ios: Mock/MockDesk.swift:1758
            }
            "daily" -> {   // ios: Mock/MockDesk.swift:1759
                s.aiDailyDay = day   // ios: Mock/MockDesk.swift:1760
                val o = ask(kind, MockAI.dailyPrompt(day = day, lines = dailyLines(day)), book = MockBook.rule) ?: return   // ios: Mock/MockDesk.swift:1761
                @Suppress("UNCHECKED_CAST")
                val ans = o["answer"] as? Map<String, Any?> ?: emptyMap()   // ios: Mock/MockDesk.swift:1762
                val sum = ((ans["summary"] as? String) ?: "").take(300)   // ios: Mock/MockDesk.swift:1763
                val pts = ((ans["points"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()).take(3).map { "· " + it.take(120) }   // ios: Mock/MockDesk.swift:1764
                s.aiDaily = (listOf(sum) + pts).filter { it.isNotEmpty() }.joinToString("\n")   // ios: Mock/MockDesk.swift:1765
                log(MockEvent.Kind.ai, book = MockBook.rule, who = MockWho.ai, text = "하루 정리 · " + sum, ai = MockAI.use(o, kind = kind, summary = day))   // ios: Mock/MockDesk.swift:1766
                s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} AI 하루 정리")   // ios: Mock/MockDesk.swift:1767
            }
            else -> {}   // ios: Mock/MockDesk.swift:1768,1769
        }
    }

    /** 말로 쓴 조건 → 조건 묶음 (저장은 화면에서 보고 정한다) */
    suspend fun aiWords(words: String): Pair<MockBundle, String>? {   // ios: Mock/MockDesk.swift:1774
        val w = words.trim()   // ios: Mock/MockDesk.swift:1775
        if (!MockAI.on || w.isEmpty() || aiBusy != null || !active) return null   // ios: Mock/MockDesk.swift:1776
        aiBusy = "words"   // ios: Mock/MockDesk.swift:1777
        try {   // ios: Mock/MockDesk.swift:1778
        val o = ask("words", MockAI.wordsPrompt(w.take(300)), book = MockBook.rule) ?: return null   // ios: Mock/MockDesk.swift:1779
        @Suppress("UNCHECKED_CAST")
        val r = MockAI.wordsBundle(o["answer"] as? Map<String, Any?> ?: emptyMap(), words = w)   // ios: Mock/MockDesk.swift:1780
        if (r == null) {
            s.runs["ai"] = MockRun(at = mockNow(), ok = false, text = "${MockFmt.time(mockNow())} 말로 조건 · 조각으로 못 바꿈")   // ios: Mock/MockDesk.swift:1781
            log(MockEvent.Kind.error, book = MockBook.rule, who = MockWho.ai, text = "말로 조건 · 조각으로 못 바꿈 · \"${w.take(40)}\"", ai = MockAI.use(o, kind = "words", summary = w))   // ios: Mock/MockDesk.swift:1782
            return null   // ios: Mock/MockDesk.swift:1783
        }
        log(MockEvent.Kind.ai, book = MockBook.rule, who = MockWho.ai, text = "말로 조건 · \"${w.take(40)}\" → " + r.first.pieces.joinToString(" · ") { it.text },   // ios: Mock/MockDesk.swift:1785
            ai = MockAI.use(o, kind = "words", summary = w))   // ios: Mock/MockDesk.swift:1786
        s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} 말로 조건 · 조각 ${r.first.pieces.size}")   // ios: Mock/MockDesk.swift:1787
        return r   // ios: Mock/MockDesk.swift:1788
        } finally { aiBusy = null; save() }
    }

    /** 뒤 흐름 AI 읽기: 단추로만 부른다. 지금 보이는 장부(모의·실전)의 그 흐름만 보낸다. 사라·팔아라는 하지 않는다 */
    suspend fun aiFlow(kind: MockFlowRead.Kind) {   // ios: Mock/MockDesk.swift:1792
        if (!MockAI.flowOn || aiBusy != null || !active) return   // ios: Mock/MockDesk.swift:1793
        val b = s.shown; val day = MockFmt.day(mockNow())   // ios: Mock/MockDesk.swift:1794
        aiBusy = kind.name   // ios: Mock/MockDesk.swift:1795
        try { aiFlowBody(kind, b, day) } finally { aiBusy = null; save() }   // ios: Mock/MockDesk.swift:1796
    }
    private suspend fun aiFlowBody(kind: MockFlowRead.Kind, b: MockBook, day: String) {
        val fills = ledgerEvents(b).filter { it.kind == MockEvent.Kind.fill && !it.test }   // ios: Mock/MockDesk.swift:1797
        val made: Pair<String, String>?   // ios: Mock/MockDesk.swift:1798
        when (kind) {   // ios: Mock/MockDesk.swift:1799
            MockFlowRead.Kind.follow -> {   // ios: Mock/MockDesk.swift:1800
                val bought = fills.filter { it.side == MockSide.buy }.mapNotNull { e -> e.code?.let { MockFmt.day(e.at) + "|" + it } }.toSet()   // ios: Mock/MockDesk.swift:1801
                made = MockAI.followPrompt(book = b, follows = (s.follows ?: emptyList()).map { MockAI.FB(f = it, bought = bought.contains(it.day + "|" + it.code)) }, day = day)   // ios: Mock/MockDesk.swift:1802
            }
            MockFlowRead.Kind.after -> {   // ios: Mock/MockDesk.swift:1803
                val xs = (s.afters ?: emptyList()).filter { it.book == b }   // ios: Mock/MockDesk.swift:1804
                val recent = xs.takeLast(MockAI.afterMax)   // ios: Mock/MockDesk.swift:1805
                val sells = recent.map { x -> MockAI.XE(x = x, e = fills.firstOrNull { it.side == MockSide.sell && it.code == x.code && abs(it.at - x.at) < 1 }) }   // ios: Mock/MockDesk.swift:1806
                made = MockAI.afterPrompt(book = b, sells = sells, older = xs.dropLast(recent.size), day = day)   // ios: Mock/MockDesk.swift:1807
            }
        }
        val name = aiName(kind.name)   // ios: Mock/MockDesk.swift:1809
        val m = made; if (m == null) {   // ios: Mock/MockDesk.swift:1810
            s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} $name · 쌓인 흐름 없음 · 안 부름")   // ios: Mock/MockDesk.swift:1811
            return   // ios: Mock/MockDesk.swift:1812
        }
        val o = ask(kind.name, m.first, book = b, server = "daily") ?: return   // ios: Mock/MockDesk.swift:1814
        @Suppress("UNCHECKED_CAST")
        val r = MockAI.flowRead(o["answer"] as? Map<String, Any?> ?: emptyMap(), kind = kind, book = b, range = m.second)   // ios: Mock/MockDesk.swift:1815
        // 같은 데이터라 지난 답을 다시 받았으면 기록을 늘리지 않는다
        val last = (s.flowReads ?: emptyList()).lastOrNull { it.kind == kind && it.book == b }; if (o["cached"] as? Boolean == true && last != null && last.text == r.text) {   // ios: Mock/MockDesk.swift:1817
            s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} $name · 지난 답")   // ios: Mock/MockDesk.swift:1818
            return   // ios: Mock/MockDesk.swift:1819
        }
        val xs = ((s.flowReads ?: emptyList()) + listOf(r)).toMutableList()   // ios: Mock/MockDesk.swift:1821
        val mine = xs.filter { it.kind == kind && it.book == b }   // ios: Mock/MockDesk.swift:1822
        if (mine.size > 60) { val old = mine.take(mine.size - 60).map { it.id }.toSet(); xs.removeAll { old.contains(it.id) } }   // ios: Mock/MockDesk.swift:1823
        s.flowReads = xs   // ios: Mock/MockDesk.swift:1824
        log(MockEvent.Kind.ai, book = b, who = MockWho.ai, text = name + " · " + m.second, ai = MockAI.use(o, kind = kind.name, summary = m.second))   // ios: Mock/MockDesk.swift:1825
        s.runs["ai"] = MockRun(at = mockNow(), ok = true, text = "${MockFmt.time(mockNow())} $name" + (if (o["cached"] as? Boolean ?: false) " · 지난 답" else ""))   // ios: Mock/MockDesk.swift:1826
    }

    suspend fun refreshWallet() {   // ios: Mock/MockDesk.swift:1829
        if (!(MockAI.on || MockAI.flowOn)) return   // ios: Mock/MockDesk.swift:1830
        val o = try { MockFeed.wallet(limits = s.ai.limits) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }; val w = o?.let { MockAI.wallet(it, at = mockNow()) }; if (w != null) s.aiWallet = w   // ios: Mock/MockDesk.swift:1831
    }

    /** 서버에 묻는다. 실패면 이유를 기록하고 null (AI 장부는 그 차례만 건너뛴다). 실패해도 쓴 돈이 있으면 남긴다
     *  server: 서버에 보낼 꼴 이름 (없으면 kind) */
    private suspend fun ask(kind: String, prompt: String, book: MockBook, server: String? = null): Map<String, Any?>? {   // ios: Mock/MockDesk.swift:1836
        if (!MockAI.on) return null   // ios: Mock/MockDesk.swift:1837
        val name = aiName(kind)   // ios: Mock/MockDesk.swift:1838
        val o: Map<String, Any?>   // ios: Mock/MockDesk.swift:1839
        try { o = MockFeed.ai(kind = server ?: kind, prompt = prompt, limits = s.ai.limits) }   // ios: Mock/MockDesk.swift:1840
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {   // ios: Mock/MockDesk.swift:1841
            val why = (e as? MockFeed.Fail)?.why ?: (e.localizedMessage ?: e.toString())   // ios: Mock/MockDesk.swift:1842
            log(MockEvent.Kind.error, book = book, who = MockWho.ai, text = "AI $name 실패 · " + why)   // ios: Mock/MockDesk.swift:1843
            s.runs["ai"] = MockRun(at = mockNow(), ok = false, text = "${MockFmt.time(mockNow())} AI $name 실패 · " + why)   // ios: Mock/MockDesk.swift:1844
            return null   // ios: Mock/MockDesk.swift:1845
        }
        MockAI.wallet(o, at = mockNow())?.let { s.aiWallet = it }   // ios: Mock/MockDesk.swift:1847
        val e = o["error"] as? String; if (e != null) {   // ios: Mock/MockDesk.swift:1848
            val paid = (MockFeed.num(o["cost"]) ?: 0.0) > 0   // ios: Mock/MockDesk.swift:1849
            log(MockEvent.Kind.error, book = book, who = MockWho.ai, text = "AI $name 실패 · " + e, ai = if (paid) MockAI.use(o, kind = kind, summary = e) else null)   // ios: Mock/MockDesk.swift:1850
            s.runs["ai"] = MockRun(at = mockNow(), ok = false, text = "${MockFmt.time(mockNow())} AI $name 실패 · " + e)   // ios: Mock/MockDesk.swift:1851
            return null   // ios: Mock/MockDesk.swift:1852
        }
        return o   // ios: Mock/MockDesk.swift:1854
    }

    /** 하루 정리에 보낼 줄: 장부마다 평가·현금·오늘 체결, 규칙·손·AI 성적 */
    private fun dailyLines(day: String): List<String> {   // ios: Mock/MockDesk.swift:1858
        val out = mutableListOf<String>()   // ios: Mock/MockDesk.swift:1859
        for (b in MockBook.entries) {   // ios: Mock/MockDesk.swift:1860
            val l = ledger(b)   // ios: Mock/MockDesk.swift:1861
            val eq = equity(b)   // ios: Mock/MockDesk.swift:1862
            out.add("[${b.label}] 평가 ${MockFmt.won(eq)} (${MockFmt.pct(eq / l.startCash - 1)}) · 현금 ${MockFmt.won(l.cash)} · 보유 ${l.positions.size}종목 · 대기 ${l.pending.size}")   // ios: Mock/MockDesk.swift:1863
            val today = ledgerEvents(b).filter { it.kind == MockEvent.Kind.fill && MockFmt.day(it.at) == day }   // ios: Mock/MockDesk.swift:1864
            for (w in MockWho.entries) {   // ios: Mock/MockDesk.swift:1865
                val st = MockCalc.stat(today, ledger = l.id, who = w)   // ios: Mock/MockDesk.swift:1866
                if (st.trades > 0) out.add("  ${w.label}: 판 ${st.trades}건 · 이긴 ${st.wins} · 손익 ${MockFmt.won(st.pnl)}")   // ios: Mock/MockDesk.swift:1867
            }
            for (e in today.take(30)) {   // ios: Mock/MockDesk.swift:1869
                out.add("  ${MockFmt.time(e.at)} ${e.name ?: e.code ?: ""} ${e.side?.label ?: ""} ${e.qty ?: 0}주 ${MockFmt.won(e.price ?: 0.0)}" +   // ios: Mock/MockDesk.swift:1870
                           (e.pnl?.let { " 손익 " + MockFmt.won(it) } ?: "") + " · ${e.who?.label ?: ""}" + (if (e.test) " · 시험" else ""))
            }
        }
        return out   // ios: Mock/MockDesk.swift:1874
    }

    // 모든 값이 선 뒤에 읽는다 (object 의 init 은 적은 차례대로 돈다)
    init {   // ios: Mock/MockDesk.swift:199
        val now = mockNow()   // ios: Mock/MockDesk.swift:200
        _s = loadState() ?: MockState.fresh(now)   // ios: Mock/MockDesk.swift:201
        _events = loadEvents().toMutableList()   // ios: Mock/MockDesk.swift:202
        // 옛 상태는 검토 문이 열린 채 저장돼 있다. 기본이 닫힘으로 바뀌어 한 번만 닫는다
        if (s.reviewShut == null) { s.select.review = true; s.reviewShut = true; save() }   // ios: Mock/MockDesk.swift:204
        migrate()   // ios: Mock/MockDesk.swift:205
        if (s.aiPurged != true) purgeAI()   // ios: Mock/MockDesk.swift:206
        // 실전 장부가 없던 옛 상태: 빈 실전 장부를 더한다 (모의 장부는 그대로)
        if (!s.ledgers.any { it.book == MockBook.real }) { s.ledgers.add(MockLedger(book = MockBook.real, started = now)); save() }   // ios: Mock/MockDesk.swift:208
        // 기본은 [모의]로 시작 (설정 '시작 실전'을 켜면 [실전])
        s.shown = if (s.startReal == true) MockBook.real else MockBook.rule   // ios: Mock/MockDesk.swift:210
        toss.armed = s.live == true && realOK   // ios: Mock/MockDesk.swift:211
        settleDue()   // ios: Mock/MockDesk.swift:212
        checkDaily()   // ios: Mock/MockDesk.swift:213
        val n = guards.perSec   // ios: Mock/MockDesk.swift:214
        MockGate.set(n)   // ios: Mock/MockDesk.swift:215
    }

}

// MARK: 주문층 (브로커)

/** 주문층: 모의와 실전(토스 주문 API)이 같은 모양. 관찰·선정·팔기 판단 코드는 이것만 부른다 */
interface MockBroker {   // ios: Mock/MockDesk.swift:1881
    /** 실전 주문을 보내는 브로커인가 */
    val live: Boolean   // ios: Mock/MockDesk.swift:1883
    val name: String   // ios: Mock/MockDesk.swift:1884
    /** 지금 호가 (주문 판단·대기 주문 다시 보기) */
    suspend fun book(code: String): MockCalc.Book   // ios: Mock/MockDesk.swift:1886
    /** 주문 넣기: 접수면 null, 거부면 이유 */
    suspend fun submit(o: MockOrder, book: MockCalc.Book): String?   // ios: Mock/MockDesk.swift:1888
    /** 상태 보기: 접수 뒤 체결을 셈할 호가 (모의는 지연 뒤 새 호가). 못 받으면 null */
    suspend fun settle(o: MockOrder): MockCalc.Book?   // ios: Mock/MockDesk.swift:1890
    /** 호가로 채울 몫 (모의는 같은 종목에 1분 안에 먹은 잔량을 빼고 셈한다) */
    fun match(o: MockOrder, book: MockCalc.Book, fees: MockFees): MockCalc.Fill   // ios: Mock/MockDesk.swift:1892
    /** 주문 취소 */
    suspend fun cancel(o: MockOrder)   // ios: Mock/MockDesk.swift:1894
    /** 상태 다시 읽기 (실전: 토스 주문 상세. 모의는 할 일 없음) */
    suspend fun refresh(o: MockOrder)   // ios: Mock/MockDesk.swift:1896
    /** 체결 없이 끝난 주문 (취소·거부) 이유. 모의는 없음 */
    fun closed(o: MockOrder): String?   // ios: Mock/MockDesk.swift:1898
    /** 정정: 받아들여지면 null, 거부면 이유 */
    suspend fun modify(o: MockOrder, qty: Int, limit: Double): String?   // ios: Mock/MockDesk.swift:1900
    /** 바깥 주문번호 (실전: 토스 orderId). 모의는 없음 */
    fun remoteId(o: MockOrder): String?   // ios: Mock/MockDesk.swift:1902
}

/** 모의 브로커: 거래소 대신 받은 호가로 체결을 셈한다 */
class MockPaperBroker : MockBroker {   // ios: Mock/MockDesk.swift:1906
    override val live = false   // ios: Mock/MockDesk.swift:1907
    override val name = "모의 브로커"   // ios: Mock/MockDesk.swift:1908
    /** 접수에서 체결까지 지연 (초). 실전처럼 그사이 호가가 바뀐다 */
    var latency = 1.5   // ios: Mock/MockDesk.swift:1910
    /** 종목 → 최근에 먹은 호가 (매수는 매도 호가, 매도는 매수 호가). 1분이 지나면 잔량이 다시 찬 것으로 본다 */
    private class Used(var at: MockDate, val asks: MutableMap<Double, Double> = mutableMapOf(), val bids: MutableMap<Double, Double> = mutableMapOf())
    private val used = mutableMapOf<String, Used>()   // ios: Mock/MockDesk.swift:1912

    override suspend fun book(code: String): MockCalc.Book = trim(code, MockFeed.book(code))   // ios: Mock/MockDesk.swift:1914

    /** 접수: 거래소가 거부할 주문을 여기서 거부한다 (실전은 토스가 같은 이유로 거부) */
    override suspend fun submit(o: MockOrder, book: MockCalc.Book): String? = exchangeReject(o, book = book)   // ios: Mock/MockDesk.swift:1917

    companion object {
        /** 거래소 규칙 (데이터로 판별 가능한 만큼) */
        fun exchangeReject(o: MockOrder, book: MockCalc.Book): String? {   // ios: Mock/MockDesk.swift:1920
            // 거래 정지·VI 단일가: 호가가 양쪽 다 비어 있으면 (둘을 가를 신호는 없다)
            if (book.asks.isEmpty() && book.bids.isEmpty() && o.auction != true) return "호가 없음 (거래 정지·VI 의심) · 거부"   // ios: Mock/MockDesk.swift:1922
            // 가격제한폭 ±30%: 넘는 지정가는 거래소가 받지 않는다 (전일 종가를 알 때만)
            val lim = o.limit; val p = o.prev; if (o.type == MockOrderType.limit && lim != null && p != null && p > 0) {   // ios: Mock/MockDesk.swift:1924
                if (lim > p * 1.3) return "상한가(전일 종가 +30%) 넘는 지정가 · 거부"   // ios: Mock/MockDesk.swift:1925
                if (lim < p * 0.7) return "하한가(전일 종가 −30%) 밑 지정가 · 거부"   // ios: Mock/MockDesk.swift:1926
            }
            return null   // ios: Mock/MockDesk.swift:1928
        }
    }

    override suspend fun settle(o: MockOrder): MockCalc.Book? {   // ios: Mock/MockDesk.swift:1931
        delay((latency * 1000).toLong())   // ios: Mock/MockDesk.swift:1932
        return try { book(o.code) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }   // ios: Mock/MockDesk.swift:1933
    }

    override fun match(o: MockOrder, book: MockCalc.Book, fees: MockFees): MockCalc.Fill {   // ios: Mock/MockDesk.swift:1936
        val b = trim(o.code, book)   // ios: Mock/MockDesk.swift:1937
        val f = MockCalc.walk(side = o.side, type = o.type, qty = o.qty, limit = o.limit, book = b, fees = fees, cap = if (o.side == MockSide.buy) o.reserved else null)   // ios: Mock/MockDesk.swift:1938
        if (f.qty <= 0) return f   // ios: Mock/MockDesk.swift:1939
        val u = used[o.code]?.let { if (mockNow() - it.at < 60) it else null } ?: Used(at = mockNow())   // ios: Mock/MockDesk.swift:1940
        for (lv in f.taken) {   // ios: Mock/MockDesk.swift:1941
            if (o.side == MockSide.buy) u.asks[lv.p] = (u.asks[lv.p] ?: 0.0) + lv.v else u.bids[lv.p] = (u.bids[lv.p] ?: 0.0) + lv.v   // ios: Mock/MockDesk.swift:1942
        }
        u.at = mockNow()   // ios: Mock/MockDesk.swift:1944
        used[o.code] = u   // ios: Mock/MockDesk.swift:1945
        return f   // ios: Mock/MockDesk.swift:1946
    }

    override suspend fun cancel(o: MockOrder) {}   // ios: Mock/MockDesk.swift:1949
    override suspend fun refresh(o: MockOrder) {}   // ios: Mock/MockDesk.swift:1950
    override fun closed(o: MockOrder): String? = null   // ios: Mock/MockDesk.swift:1951
    override suspend fun modify(o: MockOrder, qty: Int, limit: Double): String? {   // ios: Mock/MockDesk.swift:1952
        val x = o.copy(); x.type = MockOrderType.limit; x.limit = limit; x.qty = qty   // ios: Mock/MockDesk.swift:1953
        return exchangeReject(x, book = MockCalc.Book(asks = listOf(MockCalc.Level(p = limit, v = 0.0)), bids = emptyList(), at = null))   // ios: Mock/MockDesk.swift:1954
    }
    override fun remoteId(o: MockOrder): String? = null   // ios: Mock/MockDesk.swift:1956

    /** 1분 안에 먹은 잔량을 호가에서 뺀다 (같은 순간에 나눠 내도 같은 잔량을 두 번 먹지 않게) */
    private fun trim(code: String, b: MockCalc.Book): MockCalc.Book {   // ios: Mock/MockDesk.swift:1959
        val u = used[code]; if (u == null || mockNow() - u.at >= 60) { used.remove(code); return b }   // ios: Mock/MockDesk.swift:1960
        return MockCalc.Book(asks = b.asks.map { MockCalc.Level(p = it.p, v = it.v - (u.asks[it.p] ?: 0.0)) },   // ios: Mock/MockDesk.swift:1961
                             bids = b.bids.map { MockCalc.Level(p = it.p, v = it.v - (u.bids[it.p] ?: 0.0)) }, at = b.at)   // ios: Mock/MockDesk.swift:1962
    }
}

/** 토스 실주문 브로커 (토스 주문 API 문서대로). 실전 스위치(armed)가 켜졌을 때만 개발자 서버 중계로 주문을 보낸다 (서버 스위치도 켜져 있어야 토스로 나간다).
 *  체결은 토스가 알려 준 주문 상세(체결 수량·평균가)로 셈한다. 호가는 판단용으로 개발자 서버 시세를 그대로 쓴다 */
class MockTossBroker : MockBroker {   // ios: Mock/MockDesk.swift:1968
    override val live = true   // ios: Mock/MockDesk.swift:1969
    override val name = "토스 브로커"   // ios: Mock/MockDesk.swift:1970
    /** 실전 스위치 (MockDesk 가 맞춘다). 꺼져 있으면 아무것도 보내지 않는다 */
    var armed = false   // ios: Mock/MockDesk.swift:1972
    private val ids = mutableMapOf<String, String>()   // ios: Mock/MockDesk.swift:1973
    private val last = mutableMapOf<String, TossOrderAPI.Order>()   // ios: Mock/MockDesk.swift:1974
    private val counted = mutableMapOf<String, Pair<Int, Double>>()   // ios: Mock/MockDesk.swift:1975
    private val lastCall = mutableMapOf<String, MockDate>()   // ios: Mock/MockDesk.swift:1976

    override suspend fun book(code: String): MockCalc.Book = MockFeed.book(code)   // ios: Mock/MockDesk.swift:1978

    override suspend fun submit(o: MockOrder, book: MockCalc.Book): String? {   // ios: Mock/MockDesk.swift:1980
        if (!armed) return "실전 꺼짐 · 주문 안 냄"   // ios: Mock/MockDesk.swift:1981
        val r = call("POST", "/api/mock/order", body = TossOrderAPI.createBody(o), group = "ORDER") ?: return "개발자 서버 주소·열쇠 없음 · 주문 안 냄"   // ios: Mock/MockDesk.swift:1982
        val id = r.second?.let { TossOrderAPI.orderId(it) }; if (r.first != 200 || id == null) return "토스 거부 · " + TossOrderAPI.reason(r.second, status = r.first)   // ios: Mock/MockDesk.swift:1983
        ids[o.id] = id   // ios: Mock/MockDesk.swift:1984
        return null   // ios: Mock/MockDesk.swift:1985
    }

    override suspend fun settle(o: MockOrder): MockCalc.Book? {   // ios: Mock/MockDesk.swift:1988
        delay(1000)   // ios: Mock/MockDesk.swift:1989
        refresh(o)   // ios: Mock/MockDesk.swift:1990
        return try { book(o.code) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }   // ios: Mock/MockDesk.swift:1991
    }

    /** 토스 주문 상세를 다시 읽는다 (한도 ORDER_HISTORY 초당 5회) */
    override suspend fun refresh(o: MockOrder) {   // ios: Mock/MockDesk.swift:1995
        val id = ids[o.id] ?: o.tossId; if (!armed || id == null) return; val r = call("GET", "/api/mock/order/" + id, body = null, group = "ORDER_HISTORY") ?: return   // ios: Mock/MockDesk.swift:1996
        val x = r.second?.let { TossOrderAPI.order(it) }; if (r.first != 200 || x == null) return   // ios: Mock/MockDesk.swift:1997
        last[o.id] = x   // ios: Mock/MockDesk.swift:1998
    }

    /** 새로 체결된 몫만 (토스 체결 수량·금액 − 장부에 이미 적은 몫) */
    override fun match(o: MockOrder, book: MockCalc.Book, fees: MockFees): MockCalc.Fill {   // ios: Mock/MockDesk.swift:2002
        val x = last[o.id] ?: return MockCalc.Fill()   // ios: Mock/MockDesk.swift:2003
        val c = counted[o.id] ?: (0 to 0.0)   // ios: Mock/MockDesk.swift:2004
        val amount = x.filledAmount ?: ((x.avg ?: 0.0) * x.filledQty.toDouble())   // ios: Mock/MockDesk.swift:2005
        val dq = x.filledQty - c.first   // ios: Mock/MockDesk.swift:2006
        if (dq <= 0) return MockCalc.Fill()   // ios: Mock/MockDesk.swift:2007
        counted[o.id] = x.filledQty to amount   // ios: Mock/MockDesk.swift:2008
        return MockCalc.Fill(qty = dq, amount = amount - c.second, levels = 1)   // ios: Mock/MockDesk.swift:2009
    }

    /** 토스가 체결 없이 끝낸 주문 (취소·거부) → 이유 */
    override fun closed(o: MockOrder): String? {   // ios: Mock/MockDesk.swift:2013
        val x = last[o.id]; val st = x?.let { TossOrderAPI.state(it.status) }; if (x == null || st == null || !(st == MockOrderState.canceled || st == MockOrderState.rejected)) return null   // ios: Mock/MockDesk.swift:2014
        return if (st == MockOrderState.rejected) "토스 거부 (REJECTED)" else "토스 취소 (${x.status})"   // ios: Mock/MockDesk.swift:2015
    }

    override suspend fun cancel(o: MockOrder) {   // ios: Mock/MockDesk.swift:2018
        val id = ids[o.id] ?: o.tossId; if (!armed || id == null) return   // ios: Mock/MockDesk.swift:2019
        call("POST", "/api/mock/order/" + id + "/cancel", body = emptyMap(), group = "ORDER")   // ios: Mock/MockDesk.swift:2020
    }

    override suspend fun modify(o: MockOrder, qty: Int, limit: Double): String? {   // ios: Mock/MockDesk.swift:2023
        if (!armed) return "실전 꺼짐 · 정정 안 냄"   // ios: Mock/MockDesk.swift:2024
        val id = ids[o.id] ?: o.tossId ?: return "토스 주문번호 없음"   // ios: Mock/MockDesk.swift:2025
        val r = call("POST", "/api/mock/order/" + id + "/modify", body = TossOrderAPI.modifyBody(o, qty = qty, limit = limit), group = "ORDER") ?: return "개발자 서버 주소·열쇠 없음"   // ios: Mock/MockDesk.swift:2026
        if (r.first != 200) return "토스 거부 · " + TossOrderAPI.reason(r.second, status = r.first)   // ios: Mock/MockDesk.swift:2027
        // 정정하면 토스가 새 주문번호를 줄 수 있다 (문서 응답의 orderId)
        r.second?.let { TossOrderAPI.orderId(it) }?.let { ids[o.id] = it }   // ios: Mock/MockDesk.swift:2029
        return null   // ios: Mock/MockDesk.swift:2030
    }

    override fun remoteId(o: MockOrder): String? = ids[o.id]   // ios: Mock/MockDesk.swift:2033

    /** 한 번 부르기: 서버 주소·열쇠가 없으면 null. 그룹별 초당 한도를 넘지 않게 사이를 두고, 429 면 Retry-After 만큼 기다려 한 번 더 */
    private suspend fun call(method: String, path: String, body: Map<String, Any?>?, group: String): Pair<Int, Map<String, Any?>?>? {   // ios: Mock/MockDesk.swift:2036
        if (!armed) return null; val req = TossOrderAPI.request(method, path, body = body) ?: return null   // ios: Mock/MockDesk.swift:2037
        for (attempt in 0 until 2) {   // ios: Mock/MockDesk.swift:2038
            val gap = 1 / (TossOrderAPI.perSecond[group] ?: 1.0)   // ios: Mock/MockDesk.swift:2039
            val t = lastCall[group]; if (t != null && mockNow() - t < gap) delay(((gap - (mockNow() - t)) * 1000).toLong())   // ios: Mock/MockDesk.swift:2040
            lastCall[group] = mockNow()   // ios: Mock/MockDesk.swift:2041
            val h = try { req.send() } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return 0 to null   // ios: Mock/MockDesk.swift:2042
            val json = jObj(feedJson(h.data))   // ios: Mock/MockDesk.swift:2043
            if (h.code == 429 && attempt == 0) {   // ios: Mock/MockDesk.swift:2044
                val wait = h.header("Retry-After")?.toDoubleOrNull() ?: 1.0   // ios: Mock/MockDesk.swift:2045
                delay((min(wait, 5.0) * 1000).toLong())   // ios: Mock/MockDesk.swift:2046
                continue   // ios: Mock/MockDesk.swift:2047
            }
            return h.code to json   // ios: Mock/MockDesk.swift:2049
        }
        return 429 to null   // ios: Mock/MockDesk.swift:2051
    }
}
