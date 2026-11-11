package com.naeilo.app.mock

import com.naeilo.app.core.cformat
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor

// 모의투자 (docs/guides/mock.md). 아이폰 Mock/ 를 옮겼다. 안드로이드는 모든 채널에 들어간다 (아이폰 MOCK 플래그는 모든 채널 켬).
// 주문 API 는 쓰지 않는다. 토스 시세·호가로 판단하고 체결은 기기 안 가상 장부에 적는다.
// 날짜(Date)는 유닉스 초(Double), UUID 는 글자로 둔다

/** Date 자리: 유닉스 초 */
typealias MockDate = Double
fun mockNow(): MockDate = System.currentTimeMillis() / 1000.0
fun newId(): String = UUID.randomUUID().toString().uppercase()
/** Swift .rounded() (반올림, .5 는 0에서 먼 쪽) */
internal fun Double.swRound(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)
/** Int.formatted(): 세 자리 쉼표 */
internal fun Int.fmtInt(): String = cformat("%,d", this)
internal fun Long.fmtInt(): String = cformat("%,d", this)

/** 시장. 지금은 국내만 돌지만 장부·기록은 처음부터 미국도 받게 둔다 */
@Serializable
enum class MockMarket { kr, us }   // ios: Mock/MockModels.swift:9

/** 누가 냈나: 규칙 · 손 · AI */
@Serializable
enum class MockWho {   // ios: Mock/MockModels.swift:12
    rule, hand, ai;   // ios: Mock/MockModels.swift:13
    val label: String get() = when (this) {   // ios: Mock/MockModels.swift:14,15
        rule -> "규칙"   // ios: Mock/MockModels.swift:16
        hand -> "손"   // ios: Mock/MockModels.swift:17
        ai -> "AI"   // ios: Mock/MockModels.swift:18
    }
}

/** 장부: 모의 장부(rule) · 실전 장부(real) · AI 장부(꺼 둠). 모의와 실전은 절대 섞지 않는다 (2026-10-26) */
@Serializable
enum class MockBook {   // ios: Mock/MockModels.swift:25
    rule, ai, real;   // ios: Mock/MockModels.swift:26
    val label: String get() = when (this) {   // ios: Mock/MockModels.swift:27,28
        rule -> "모의 장부"   // ios: Mock/MockModels.swift:29
        real -> "실전 장부"   // ios: Mock/MockModels.swift:30
        ai -> "AI 장부"   // ios: Mock/MockModels.swift:31
    }
    /** 세 탭 위 칸 이름 */
    val tab: String get() = if (this == real) "실전" else "모의"   // ios: Mock/MockModels.swift:35
}

/** 일봉 한 칸 (토스 캔들). d = 한국 날짜 yyyy-MM-dd */
@Serializable
data class MockBar(var d: String,   // ios: Mock/MockModels.swift:39,40
    var o: Double, var h: Double, var l: Double, var c: Double, var v: Double)   // ios: Mock/MockModels.swift:41

// 관찰 조건

/** 조건 조각 하나. 값이 없는 쪽은 열어 둔다 (lo 만 있으면 '이상', hi 만 있으면 '이하') */
@Serializable
data class MockPiece(   // ios: Mock/MockModels.swift:47
    var id: String = newId(),   // ios: Mock/MockModels.swift:67
    var kind: Kind,   // ios: Mock/MockModels.swift:68
    var lo: Double? = null,   // ios: Mock/MockModels.swift:69
    var hi: Double? = null,   // ios: Mock/MockModels.swift:70
    var days: Int = 20,         // ios: Mock/MockModels.swift:71
    var above: Boolean = true,     // ios: Mock/MockModels.swift:72
) {
    @Serializable
    enum class Kind {   // ios: Mock/MockModels.swift:48
        price,      // ios: Mock/MockModels.swift:49
        change,     // ios: Mock/MockModels.swift:50
        volume,     // ios: Mock/MockModels.swift:51
        ma,         // ios: Mock/MockModels.swift:52
        amount,     // ios: Mock/MockModels.swift:53
        rise;       // ios: Mock/MockModels.swift:54
        val id: String get() = name   // ios: Mock/MockModels.swift:55
        val label: String get() = when (this) {   // ios: Mock/MockModels.swift:56,57
            price -> "가격"   // ios: Mock/MockModels.swift:58
            change -> "등락률"   // ios: Mock/MockModels.swift:59
            volume -> "거래량"   // ios: Mock/MockModels.swift:60
            ma -> "이동평균"   // ios: Mock/MockModels.swift:61
            amount -> "거래대금"   // ios: Mock/MockModels.swift:62
            rise -> "최근 상승"   // ios: Mock/MockModels.swift:63
        }
    }

    /** 조각에 필요한 지난 봉 수 (오늘 봉 포함) */
    val need: Int get() = when (kind) {   // ios: Mock/MockModels.swift:75,76
        Kind.price, Kind.amount -> 1   // ios: Mock/MockModels.swift:77
        Kind.change -> 2   // ios: Mock/MockModels.swift:78
        Kind.volume -> days + 1   // ios: Mock/MockModels.swift:79
        Kind.ma -> days   // ios: Mock/MockModels.swift:80
        Kind.rise -> days + 1   // ios: Mock/MockModels.swift:81
    }

    /** 화면·기록 글: "거래량 ≥ 20일 평균 × 2" */
    val text: String get() = when (kind) {   // ios: Mock/MockModels.swift:86,87
        Kind.price -> "가격 " + range("원")   // ios: Mock/MockModels.swift:88
        Kind.change -> "등락률 " + range("%", signed = true)   // ios: Mock/MockModels.swift:89
        Kind.volume -> {   // ios: Mock/MockModels.swift:90
            val d = if (days == 20) "" else " (${days}일)"   // ios: Mock/MockModels.swift:91
            "거래량 " + range("배") + d   // ios: Mock/MockModels.swift:92
        }
        Kind.rise -> "최근 ${days}일 " + range("%", signed = true)   // ios: Mock/MockModels.swift:93
        Kind.ma -> "가격 " + (if (above) ">" else "<") + " ${days}일선"   // ios: Mock/MockModels.swift:94
        Kind.amount -> "거래대금 " + range("억")   // ios: Mock/MockModels.swift:95
    }
    /** 둘 다 있으면 단위는 끝에 한 번: "+2~8%", "−3~+2%", "2~5배" */
    private fun range(unit: String, signed: Boolean = false): String {   // ios: Mock/MockModels.swift:99
        fun f(x: Double): String = if (signed) MockFmt.signed(x) else MockFmt.num(x)   // ios: Mock/MockModels.swift:100
        val a = lo; val b = hi   // ios: Mock/MockModels.swift:101
        return when {
            a != null && b != null -> {   // ios: Mock/MockModels.swift:102
                val bs = if (signed && a >= 0 && b > 0) MockFmt.num(b) else f(b)   // ios: Mock/MockModels.swift:103
                f(a) + "~" + bs + unit   // ios: Mock/MockModels.swift:104
            }
            a != null -> "≥ " + f(a) + unit   // ios: Mock/MockModels.swift:105
            b != null -> "≤ " + f(b) + unit   // ios: Mock/MockModels.swift:106
            else -> "아무거나"   // ios: Mock/MockModels.swift:107
        }
    }
}

/** 조건 묶음: 이름 붙여 저장한다. 조각은 모두 맞아야(그리고) 걸린다 */
@Serializable
data class MockBundle(   // ios: Mock/MockModels.swift:113
    var id: String = newId(),   // ios: Mock/MockModels.swift:124
    var name: String,   // ios: Mock/MockModels.swift:125
    var pieces: List<MockPiece>,   // ios: Mock/MockModels.swift:126
    var scope: Scope = Scope.all,   // ios: Mock/MockModels.swift:127
    /** 결과 줄 세우기 (큰 순). 선정 규칙이 따로 정하지 않으면 이 순서 */
    var sort: MockPiece.Kind = MockPiece.Kind.volume,   // ios: Mock/MockModels.swift:129
    var sortDays: Int = 20,   // ios: Mock/MockModels.swift:130
) {
    @Serializable
    enum class Scope {   // ios: Mock/MockModels.swift:114
        all, kospi, kosdaq;   // ios: Mock/MockModels.swift:115
        val label: String get() = when (this) {   // ios: Mock/MockModels.swift:116,117
            all -> "국내 전체"   // ios: Mock/MockModels.swift:118
            kospi -> "코스피"   // ios: Mock/MockModels.swift:119
            kosdaq -> "코스닥"   // ios: Mock/MockModels.swift:120
        }
    }
    /** 이 묶음이 봐야 하는 지난 봉 수 */
    val need: Int get() = maxOf((listOf(sortPiece) + pieces).maxOfOrNull { it.need } ?: 1, 2)   // ios: Mock/MockModels.swift:133
    val sortPiece: MockPiece get() = MockPiece(kind = sort, days = sortDays)   // ios: Mock/MockModels.swift:134
}

/** 훑을 종목 한 줄 (코드 · 이름 · 코스피 여부) */
@Serializable
data class MockStock(var code: String, var name: String, var kospi: Boolean)   // ios: Mock/MockModels.swift:138,139,140,141

/** 훑기 결과의 걸린 종목 */
@Serializable
data class MockHit(   // ios: Mock/MockModels.swift:145
    var code: String,   // ios: Mock/MockModels.swift:147
    var name: String,   // ios: Mock/MockModels.swift:148
    var price: Double,   // ios: Mock/MockModels.swift:149
    var change: Double?,          // ios: Mock/MockModels.swift:150
    var score: Double,            // ios: Mock/MockModels.swift:151
    var why: String,              // ios: Mock/MockModels.swift:152
    var vol: Double? = null,       // ios: Mock/MockModels.swift:153
    var rise5: Double? = null,     // ios: Mock/MockModels.swift:154
    var volNew: Double? = null,    // ios: Mock/MockModels.swift:155
    var amount: Double? = null,    // ios: Mock/MockModels.swift:156
    var amt20: Double? = null,     // ios: Mock/MockModels.swift:157
    var range20: Double? = null,   // ios: Mock/MockModels.swift:158
) {
    val id: String get() = code   // ios: Mock/MockModels.swift:146
}

/** 관찰에 한 번 걸린 기록 (종목별 관찰 기록) */
@Serializable
data class MockSeen(   // ios: Mock/MockModels.swift:162
    var at: MockDate,   // ios: Mock/MockModels.swift:163
    var price: Double,   // ios: Mock/MockModels.swift:164
    var change: Double?,   // ios: Mock/MockModels.swift:165
    var why: String,   // ios: Mock/MockModels.swift:166
    var vol: Double? = null,       // ios: Mock/MockModels.swift:167
    var volNew: Double? = null,    // ios: Mock/MockModels.swift:168
)

/** 하루 거래량 곡선: 9:00부터 15분마다 하루 거래량이 얼마나 쌓였는지 (27칸, 0~1). 큰 종목들의 가운데 값 */
@Serializable
data class MockCurve(var day: String, var cum: List<Double>)   // ios: Mock/MockModels.swift:172,173,174

/** 한 번 훑은 결과. 떨어진 종목은 이유(처음 걸린 조각 번호)별 수만 남긴다 (-1 = 자료 없음) */
@Serializable
data class MockScan(   // ios: Mock/MockModels.swift:178
    var id: String = newId(),   // ios: Mock/MockModels.swift:179
    var at: MockDate,   // ios: Mock/MockModels.swift:180
    var bundleId: String,   // ios: Mock/MockModels.swift:181
    var bundleName: String,   // ios: Mock/MockModels.swift:182
    var pieceTexts: List<String>,   // ios: Mock/MockModels.swift:183
    var barDay: String,           // ios: Mock/MockModels.swift:184
    var total: Int,               // ios: Mock/MockModels.swift:185
    var hits: List<MockHit>,   // ios: Mock/MockModels.swift:186
    var dropped: Map<Int, Int>,      // ios: Mock/MockModels.swift:187
    var excluded: List<String> = emptyList(),  // ios: Mock/MockModels.swift:188
    var seconds: Double = 0.0,   // ios: Mock/MockModels.swift:189
    var frac: Double? = null,      // ios: Mock/MockModels.swift:190
    var newHits: List<String>? = null, // ios: Mock/MockModels.swift:191
    var full: Boolean? = null,        // ios: Mock/MockModels.swift:192
    var skipped: List<MockCalc.Skip>? = null,  // ios: Mock/MockModels.swift:193
    var fails: Map<String, Int>? = null,      // ios: Mock/MockModels.swift:194
)

/** 관찰 조건 하나 (알약). 켠 조건을 모두 맞는 종목이 걸린다 */
@Serializable
data class MockCond(var piece: MockPiece, var on: Boolean = true) {   // ios: Mock/MockModels.swift:198,199,200
    val id: String get() = piece.id   // ios: Mock/MockModels.swift:201
}

/** 고르기 조건 하나 (알약): 순서·상위 · 종목당 한도 · 현금 남김 */
@Serializable
data class MockRuleCond(   // ios: Mock/MockModels.swift:205
    var id: String = newId(),   // ios: Mock/MockModels.swift:222
    var kind: Kind,   // ios: Mock/MockModels.swift:223
    var on: Boolean = true,   // ios: Mock/MockModels.swift:224
    var sort: MockPiece.Kind = MockPiece.Kind.volume,   // ios: Mock/MockModels.swift:225
    var n: Int = 5,                       // ios: Mock/MockModels.swift:226
    var pct: Double = 0.2,                // ios: Mock/MockModels.swift:227
    var capKind: MockPiece.Kind = MockPiece.Kind.volume, // ios: Mock/MockModels.swift:228
    var lim: Double = 50.0,                 // ios: Mock/MockModels.swift:229
) {
    @Serializable
    enum class Kind {   // ios: Mock/MockModels.swift:206
        top, per, cash, cap, slip, split;   // ios: Mock/MockModels.swift:207
        val id: String get() = name   // ios: Mock/MockModels.swift:208
        val label: String get() = when (this) {   // ios: Mock/MockModels.swift:209,210
            top -> "순서·상위"   // ios: Mock/MockModels.swift:211
            per -> "종목당 한도"   // ios: Mock/MockModels.swift:212
            cash -> "현금 남김"   // ios: Mock/MockModels.swift:213
            cap -> "폭등 거르기"   // ios: Mock/MockModels.swift:214
            slip -> "미끄러짐 한도"   // ios: Mock/MockModels.swift:215
            split -> "나눠 사기"   // ios: Mock/MockModels.swift:216
        }
        companion object {
            /** 넣을 수 있는 것. 폭등 거르기는 종목 판단이라 관찰 조건으로 옮겼다 (2026-10-29, 옛 상태 읽기용으로만 남김) */
            val choices: List<Kind> get() = listOf(top, per, cash, slip, split)   // ios: Mock/MockModels.swift:220
        }
    }
    val text: String get() = when (kind) {   // ios: Mock/MockModels.swift:230,231
        Kind.top -> "${sort.label} 순 상위 $n"   // ios: Mock/MockModels.swift:232
        Kind.per -> "종목당 ≤${(pct * 100).swRound().toInt()}%"   // ios: Mock/MockModels.swift:233
        Kind.cash -> "현금 ${(pct * 100).swRound().toInt()}% 남김"   // ios: Mock/MockModels.swift:234
        Kind.cap -> if (capKind == MockPiece.Kind.rise) "최근 5일 ≤ +${MockFmt.num(lim)}%" else "거래량 ≤ ${MockFmt.num(lim)}배"   // ios: Mock/MockModels.swift:235
        Kind.slip -> "미끄러짐 ≤${MockFmt.num(pct * 100)}%"   // ios: Mock/MockModels.swift:236
        Kind.split -> "나눠 사기 · 한 번 ${(pct * 100).swRound().toInt()}%"   // ios: Mock/MockModels.swift:237
    }
    /** cap: 걸린 종목이 이 조건을 넘나 (값이 없으면 넘지 않은 것으로 본다) */
    fun over(h: MockHit): Boolean {   // ios: Mock/MockModels.swift:241
        if (kind != Kind.cap) return false   // ios: Mock/MockModels.swift:242
        if (capKind == MockPiece.Kind.rise) return (h.rise5 ?: Double.NEGATIVE_INFINITY) > lim   // ios: Mock/MockModels.swift:243
        return (h.vol ?: 0.0) > lim   // ios: Mock/MockModels.swift:244
    }
}

/** 팔기 조건 알약 하나 (켠 알약으로 sell 값을 맞춘다) */
@Serializable
data class MockSellCond(   // ios: Mock/MockModels.swift:249
    var id: String = newId(),   // ios: Mock/MockModels.swift:268
    var kind: Kind,   // ios: Mock/MockModels.swift:269
    var on: Boolean = true,   // ios: Mock/MockModels.swift:270
    var pct: Double = 0.05,   // ios: Mock/MockModels.swift:271
    var n: Int = 5,           // ios: Mock/MockModels.swift:272
) {
    @Serializable
    enum class Kind {   // ios: Mock/MockModels.swift:250
        loss, gain, days, idle, gone, close, trail, vol;   // ios: Mock/MockModels.swift:251
        val id: String get() = name   // ios: Mock/MockModels.swift:252
        val label: String get() = when (this) {   // ios: Mock/MockModels.swift:253,254
            loss -> "손절"   // ios: Mock/MockModels.swift:255
            gain -> "익절"   // ios: Mock/MockModels.swift:256
            days -> "오래 들면"   // ios: Mock/MockModels.swift:257
            idle -> "움직임 없음"   // ios: Mock/MockModels.swift:258
            gone -> "이유 꺼짐"   // ios: Mock/MockModels.swift:259
            close -> "마감 정리"   // ios: Mock/MockModels.swift:260
            trail -> "고점 되밀림"   // ios: Mock/MockModels.swift:261
            vol -> "흔들림 손절"   // ios: Mock/MockModels.swift:262
        }
        companion object {
            /** 넣을 수 있는 것. 단타라 '보유 n일'은 뺐다 (2026-10-29, 옛 상태 읽기용으로만 남김) */
            val choices: List<Kind> get() = listOf(loss, gain, trail, idle, gone, close, vol)   // ios: Mock/MockModels.swift:266
        }
    }
    val text: String get() = when (kind) {   // ios: Mock/MockModels.swift:273,274
        Kind.loss -> "손절 −${MockFmt.num((pct * 100).swRound())}%"   // ios: Mock/MockModels.swift:275
        Kind.gain -> "익절 +${MockFmt.num((pct * 100).swRound())}%"   // ios: Mock/MockModels.swift:276
        Kind.days -> "보유 ${n}일 넘음"   // ios: Mock/MockModels.swift:277
        Kind.idle -> "${n}분 ±${MockFmt.num(pct * 100)}% 안이면"   // ios: Mock/MockModels.swift:278
        Kind.gone -> "산 이유 꺼지면"   // ios: Mock/MockModels.swift:279
        Kind.close -> "${MockClock.hm(MockClock.tidyFrom)} 모두 정리"   // ios: Mock/MockModels.swift:280
        Kind.trail -> "반 판 뒤 고점 −${MockFmt.num(pct * 100)}%"   // ios: Mock/MockModels.swift:281
        Kind.vol -> "손절 · 하루 폭 ×${MockFmt.num(pct)}"   // ios: Mock/MockModels.swift:282
    }
}

/** 평가액 한 점 (내 수익률 꺾은선) */
@Serializable
data class MockMark(var at: MockDate, var eq: Double, var ledger: String)   // ios: Mock/MockModels.swift:288,289,290,291

// 예외 종목

/** 규칙이 사지도 팔지도 않는 종목. 직접 넣은 것 + 조건 묶음 + 앱의 내 보유 종목 전부 */
@Serializable
data class MockExceptions(   // ios: Mock/MockModels.swift:297
    var direct: List<MockStock> = emptyList(),   // ios: Mock/MockModels.swift:298
    var bundles: List<MockBundle> = emptyList(),   // ios: Mock/MockModels.swift:299
    var mine: Boolean = true,   // ios: Mock/MockModels.swift:300
)

// 선정·팔기 규칙

/** 선정 규칙: 관찰 결과에서 몇 개를, 무슨 순서로, 얼마씩 */
@Serializable
data class MockSelectRule(   // ios: Mock/MockModels.swift:306
    var name: String = "상위 5",   // ios: Mock/MockModels.swift:307
    var count: Int = 5,            // ios: Mock/MockModels.swift:308
    var maxPer: Double = 0.20,     // ios: Mock/MockModels.swift:309
    var keepCash: Double = 0.20,   // ios: Mock/MockModels.swift:310
    var roundMax: Double? = null,   // ios: Mock/MockModels.swift:311
    /** 나눠 사기: 한 종목 몫 중 한 번에 살 비율 (0.34 = 세 번에 나눔, 없으면 한 번에) */
    var split: Double? = null,   // ios: Mock/MockModels.swift:313
    /** 검토 문: 기본은 닫는다(켬 = 고른 뒤 내가 보고 주문, 끔 = 고르면 바로 주문) */
    var review: Boolean = true,   // ios: Mock/MockModels.swift:315
    /** 미끄러짐 한도 (0.005 = 0.5%) */
    var slip: Double? = null,   // ios: Mock/MockModels.swift:317
)

/** 팔기 규칙 (값이 없으면 끔). 관찰 주기마다 장중 가격으로 판단한다 */
@Serializable
data class MockSellRule(   // ios: Mock/MockModels.swift:321
    var stopLoss: Double? = null,   // ios: Mock/MockModels.swift:322
    var takeProfit: Double? = null, // ios: Mock/MockModels.swift:323
    var maxDays: Int? = null,       // ios: Mock/MockModels.swift:324
    var idleMin: Int? = null,       // ios: Mock/MockModels.swift:325
    var idleBand: Double? = null,   // ios: Mock/MockModels.swift:326
    var gone: Boolean? = null,         // ios: Mock/MockModels.swift:327
    var closeOut: Boolean? = null,     // ios: Mock/MockModels.swift:328
    var trail: Double? = null,      // ios: Mock/MockModels.swift:329
    var volK: Double? = null,       // ios: Mock/MockModels.swift:330
    var since: String? = null,      // ios: Mock/MockModels.swift:331
) {
    val on: Boolean get() = stopLoss != null || takeProfit != null || maxDays != null || idleMin != null || gone == true || closeOut == true || trail != null || volK != null   // ios: Mock/MockModels.swift:332
}

/** 안전장치 (실전 전에 꼭 필요한 막음). 기본: 하루 손실 −3%, 한 번 주문 300만원 */
@Serializable
data class MockGuards(   // ios: Mock/MockModels.swift:336
    var dayLoss: Double = 0.03,        // ios: Mock/MockModels.swift:337
    var orderCap: Double = 3_000_000.0,  // ios: Mock/MockModels.swift:338
    var apiPerSec: Double? = null,      // ios: Mock/MockModels.swift:339
    var marketDrop: Double? = null,     // ios: Mock/MockModels.swift:340
    var lossStreak: Int? = null,        // ios: Mock/MockModels.swift:341
    var bookWall: Double? = null,       // ios: Mock/MockModels.swift:342
) {
    val perSec: Double get() = apiPerSec ?: 5.0   // ios: Mock/MockModels.swift:343
}

/** 수수료·세금 (설정값). 2026-10 확인: 토스증권 국내 0.015%, 증권거래세 매도 0.20% */
@Serializable
data class MockFees(   // ios: Mock/MockModels.swift:347
    var commission: Double = 0.00015,   // ios: Mock/MockModels.swift:348
    var sellTax: Double = 0.0020,   // ios: Mock/MockModels.swift:349
) {
    /** 한 번 체결의 수수료 (원 아래 버림) */
    fun fee(amount: Double): Double = floor(amount * commission)   // ios: Mock/MockModels.swift:351
    fun tax(amount: Double): Double = floor(amount * sellTax)   // ios: Mock/MockModels.swift:352
}

// 장부

@Serializable
enum class MockSide { buy, sell;   // ios: Mock/MockModels.swift:357
    val label: String get() = if (this == buy) "매수" else "매도"   // ios: Mock/MockModels.swift:358
}
/** 주문 상태: 접수 → 부분 체결 → 체결 · 취소 · 거부 (실전 주문 API 와 같은 단계) */
@Serializable
enum class MockOrderState { accepted, partial, filled, canceled, rejected }   // ios: Mock/MockModels.swift:361

@Serializable
enum class MockOrderType { market, limit;   // ios: Mock/MockModels.swift:363
    val label: String get() = if (this == market) "시장가" else "지정가"   // ios: Mock/MockModels.swift:364
}

/** 보유 한 종목 */
@Serializable
data class MockPosition(   // ios: Mock/MockModels.swift:368
    var code: String,   // ios: Mock/MockModels.swift:370
    var name: String,   // ios: Mock/MockModels.swift:371
    var market: MockMarket = MockMarket.kr,   // ios: Mock/MockModels.swift:372
    var qty: Int,   // ios: Mock/MockModels.swift:373
    var cost: Double,             // ios: Mock/MockModels.swift:374
    var gross: Double,            // ios: Mock/MockModels.swift:375
    var opened: MockDate,   // ios: Mock/MockModels.swift:376
    var openedDay: String,        // ios: Mock/MockModels.swift:377
    var who: MockWho,   // ios: Mock/MockModels.swift:378
    var test: Boolean,               // ios: Mock/MockModels.swift:379
    var buyRef: String? = null,      // ios: Mock/MockModels.swift:380
    var tQty: Int? = null,         // ios: Mock/MockModels.swift:381
    var tCost: Double? = null,   // ios: Mock/MockModels.swift:382
    var tGross: Double? = null,   // ios: Mock/MockModels.swift:383
    var why: String? = null,       // ios: Mock/MockModels.swift:384
    var conds: String? = null,     // ios: Mock/MockModels.swift:385
    var took: Boolean? = null,        // ios: Mock/MockModels.swift:386
    var peak: Double? = null,      // ios: Mock/MockModels.swift:387
    var low: Double? = null,       // ios: Mock/MockModels.swift:388
    var entry: MockBuyNote? = null,  // ios: Mock/MockModels.swift:389
) {
    val id: String get() = code   // ios: Mock/MockModels.swift:369
    val avg: Double get() = if (qty > 0) gross / qty.toDouble() else 0.0   // ios: Mock/MockModels.swift:390
}

/** 주문 (미체결 대기도 이것). 매수는 넣을 때 현금에서 묶어 둔다(reserved) */
@Serializable
data class MockOrder(   // ios: Mock/MockModels.swift:394
    var id: String = newId(),   // ios: Mock/MockModels.swift:395
    var code: String,   // ios: Mock/MockModels.swift:396
    var name: String,   // ios: Mock/MockModels.swift:397
    var market: MockMarket = MockMarket.kr,   // ios: Mock/MockModels.swift:398
    var side: MockSide,   // ios: Mock/MockModels.swift:399
    var type: MockOrderType,   // ios: Mock/MockModels.swift:400
    var qty: Int,                 // ios: Mock/MockModels.swift:401
    var limit: Double? = null,   // ios: Mock/MockModels.swift:402
    var at: MockDate,   // ios: Mock/MockModels.swift:403
    var who: MockWho,   // ios: Mock/MockModels.swift:404
    var reason: String,   // ios: Mock/MockModels.swift:405
    var reserved: Double = 0.0,     // ios: Mock/MockModels.swift:406
    var ref: String? = null,         // ios: Mock/MockModels.swift:407
    var waitWhy: String? = null,   // ios: Mock/MockModels.swift:408
    var until: MockDate? = null,       // ios: Mock/MockModels.swift:409
    var state: MockOrderState? = null,   // ios: Mock/MockModels.swift:410
    var prev: Double? = null,      // ios: Mock/MockModels.swift:411
    var auction: Boolean? = null,     // ios: Mock/MockModels.swift:412
    var expect: Double? = null,    // ios: Mock/MockModels.swift:413
    var tossId: String? = null,    // ios: Mock/MockModels.swift:414
    var conds: String? = null,     // ios: Mock/MockModels.swift:415
    var entry: MockBuyNote? = null,  // ios: Mock/MockModels.swift:416
)

/** 결제 대기 돈 한 건: 판 날부터 2영업일 뒤(T+2)에 현금이 된다 */
@Serializable
data class MockSettle(var amount: Double, var day: String, var code: String)   // ios: Mock/MockModels.swift:420,421,422,423

/** 가상 계좌 하나 */
@Serializable
data class MockLedger(   // ios: Mock/MockModels.swift:427
    var id: String = newId(),   // ios: Mock/MockModels.swift:428
    var book: MockBook,   // ios: Mock/MockModels.swift:429
    var startCash: Double = 10_000_000.0,   // ios: Mock/MockModels.swift:430
    var cash: Double = 10_000_000.0,          // ios: Mock/MockModels.swift:431
    var positions: Map<String, MockPosition> = emptyMap(),   // ios: Mock/MockModels.swift:432
    var pending: List<MockOrder> = emptyList(),   // ios: Mock/MockModels.swift:433
    var started: MockDate,   // ios: Mock/MockModels.swift:434
    var ended: MockDate? = null,                 // ios: Mock/MockModels.swift:435
    var fees: Double = 0.0,                   // ios: Mock/MockModels.swift:436
    var taxes: Double = 0.0,                  // ios: Mock/MockModels.swift:437
    var unsettled: List<MockSettle>? = null,     // ios: Mock/MockModels.swift:438
) {
    val reserved: Double get() = pending.fold(0.0) { a, o -> a + o.reserved }   // ios: Mock/MockModels.swift:439
    val settling: Double get() = (unsettled ?: emptyList()).fold(0.0) { a, s -> a + s.amount }   // ios: Mock/MockModels.swift:440
}

// 기록

/** 한 기록 파일에 차례로 쌓는 줄. 지난 줄은 고치지 않는다 */
@Serializable
data class MockEvent(   // ios: Mock/MockModels.swift:446
    var id: String = newId(),   // ios: Mock/MockModels.swift:470
    var at: MockDate,   // ios: Mock/MockModels.swift:471
    var ledger: String,   // ios: Mock/MockModels.swift:472
    var book: MockBook,   // ios: Mock/MockModels.swift:473
    var kind: Kind,   // ios: Mock/MockModels.swift:474
    var who: MockWho? = null,   // ios: Mock/MockModels.swift:475
    var code: String? = null,   // ios: Mock/MockModels.swift:476
    var name: String? = null,   // ios: Mock/MockModels.swift:477
    var text: String,                       // ios: Mock/MockModels.swift:478
    var test: Boolean = false,                 // ios: Mock/MockModels.swift:479
    var bundle: String? = null,              // ios: Mock/MockModels.swift:480
    var rule: String? = null,                // ios: Mock/MockModels.swift:481
    var side: MockSide? = null,   // ios: Mock/MockModels.swift:482
    var qty: Int? = null,   // ios: Mock/MockModels.swift:483
    var price: Double? = null,               // ios: Mock/MockModels.swift:484
    var amount: Double? = null,              // ios: Mock/MockModels.swift:485
    var fee: Double? = null,   // ios: Mock/MockModels.swift:486
    var tax: Double? = null,   // ios: Mock/MockModels.swift:487
    var pnl: Double? = null,                 // ios: Mock/MockModels.swift:488
    var pnlGross: Double? = null,            // ios: Mock/MockModels.swift:489
    var basis: Double? = null,               // ios: Mock/MockModels.swift:490
    var opener: MockWho? = null,             // ios: Mock/MockModels.swift:491
    var ref: String? = null,                   // ios: Mock/MockModels.swift:492
    var ai: MockAIUse? = null,   // ios: Mock/MockModels.swift:493
    var expect: Double? = null,              // ios: Mock/MockModels.swift:494
    var tossId: String? = null,              // ios: Mock/MockModels.swift:495
    var conds: String? = null,               // ios: Mock/MockModels.swift:496
    var buyWhy: String? = null,              // ios: Mock/MockModels.swift:497
    var exit: String? = null,                // ios: Mock/MockModels.swift:498
    var exitWhy: String? = null,             // ios: Mock/MockModels.swift:499
    var entry: MockBuyNote? = null,            // ios: Mock/MockModels.swift:500
    var low: Double? = null,                 // ios: Mock/MockModels.swift:501
) {
    @Serializable
    enum class Kind {   // ios: Mock/MockModels.swift:447
        scan, exclude, select, short, order, fill, wait, cancel, sell, reset, rules, ai, error, reject, settle, check;   // ios: Mock/MockModels.swift:448
        val label: String get() = when (this) {   // ios: Mock/MockModels.swift:449,450
            scan -> "관찰"   // ios: Mock/MockModels.swift:451
            exclude -> "예외"   // ios: Mock/MockModels.swift:452
            select -> "선정"   // ios: Mock/MockModels.swift:453
            short -> "현금 부족"   // ios: Mock/MockModels.swift:454
            order -> "주문"   // ios: Mock/MockModels.swift:455
            fill -> "체결"   // ios: Mock/MockModels.swift:456
            wait -> "미체결"   // ios: Mock/MockModels.swift:457
            cancel -> "취소"   // ios: Mock/MockModels.swift:458
            sell -> "팔기 신호"   // ios: Mock/MockModels.swift:459
            reset -> "장부 새로"   // ios: Mock/MockModels.swift:460
            rules -> "규칙 바뀜"   // ios: Mock/MockModels.swift:461
            ai -> "AI"   // ios: Mock/MockModels.swift:462
            error -> "오류"   // ios: Mock/MockModels.swift:463
            reject -> "거부"   // ios: Mock/MockModels.swift:464
            settle -> "결제"   // ios: Mock/MockModels.swift:465
            check -> "잔고 맞추기"   // ios: Mock/MockModels.swift:466
        }
    }
}

/** 살 때 잰 값. 꺼 둔 조건(흔들림 손절·연속 손절 멈춤·호가 벽)도 늘 적어, 켰다면 어땠을지 나중에 센다 (2026-10-29) */
@Serializable
data class MockBuyNote(   // ios: Mock/MockModels.swift:505
    var range20: Double? = null,   // ios: Mock/MockModels.swift:506
    var streak: Int? = null,       // ios: Mock/MockModels.swift:507
    var wall: Double? = null,      // ios: Mock/MockModels.swift:508
    var market: Double? = null,    // ios: Mock/MockModels.swift:509
    var minute: Int? = null,       // ios: Mock/MockModels.swift:510
)

/** 걸린 종목 뒤 흐름: 오늘 처음 걸린 값과 30분·1시간·마감 값 (산 것과 안 산 것을 견준다) */
@Serializable
data class MockFollow(   // ios: Mock/MockModels.swift:514
    var day: String,   // ios: Mock/MockModels.swift:515
    var code: String,   // ios: Mock/MockModels.swift:516
    var name: String,   // ios: Mock/MockModels.swift:517
    var at: MockDate,   // ios: Mock/MockModels.swift:518
    var p0: Double,   // ios: Mock/MockModels.swift:519
    var p30: Double? = null,   // ios: Mock/MockModels.swift:520
    var p60: Double? = null,   // ios: Mock/MockModels.swift:521
    var pEnd: Double? = null,   // ios: Mock/MockModels.swift:522
) {
    fun ret(p: Double?): Double? = p?.let { if (p0 > 0) it / p0 - 1 else null }   // ios: Mock/MockModels.swift:523
}

/** 판 종목 뒤 흐름: 판 값과 판 뒤 30분·1시간·마감·다음 날 시가 (판 자리가 반환점이었나 본다, 2026-10-29) */
@Serializable
data class MockAfter(   // ios: Mock/MockModels.swift:527
    var day: String,   // ios: Mock/MockModels.swift:528
    var book: MockBook,   // ios: Mock/MockModels.swift:529
    var code: String,   // ios: Mock/MockModels.swift:530
    var name: String,   // ios: Mock/MockModels.swift:531
    var at: MockDate,   // ios: Mock/MockModels.swift:532
    var p0: Double,               // ios: Mock/MockModels.swift:533
    var qty: Int,   // ios: Mock/MockModels.swift:534
    var exit: String,             // ios: Mock/MockModels.swift:535
    var p30: Double? = null,   // ios: Mock/MockModels.swift:536
    var p60: Double? = null,   // ios: Mock/MockModels.swift:537
    var pEnd: Double? = null,   // ios: Mock/MockModels.swift:538
    var pOpen: Double? = null,     // ios: Mock/MockModels.swift:539
) {
    /** 가장 늦게 채운 값과 그 이름 */
    val last: Pair<String, Double>? get() {   // ios: Mock/MockModels.swift:541
        pOpen?.let { return "다음 날 시가" to it }   // ios: Mock/MockModels.swift:542
        pEnd?.let { return "마감" to it }   // ios: Mock/MockModels.swift:543
        p60?.let { return "1시간 뒤" to it }   // ios: Mock/MockModels.swift:544
        p30?.let { return "30분 뒤" to it }   // ios: Mock/MockModels.swift:545
        return null   // ios: Mock/MockModels.swift:546
    }
    /** 판 뒤 오른 비율 (+면 기다렸으면 나았음) */
    val ret: Double? get() = last?.let { if (p0 > 0) it.second / p0 - 1 else 0.0 }   // ios: Mock/MockModels.swift:549
}

/** 뒤 흐름 AI 읽기: 걸린 종목·판 종목 흐름을 단추로 부를 때만 읽어 준다 (2026-10-29). 사라·팔아라는 하지 않는다 */
@Serializable
data class MockFlowRead(   // ios: Mock/MockModels.swift:553
    var id: String = newId(),   // ios: Mock/MockModels.swift:555
    var kind: Kind,   // ios: Mock/MockModels.swift:556
    var book: MockBook,   // ios: Mock/MockModels.swift:557
    var at: MockDate,   // ios: Mock/MockModels.swift:558
    var range: String,             // ios: Mock/MockModels.swift:559
    var text: String,              // ios: Mock/MockModels.swift:560
    var terms: List<List<String>> = emptyList(),    // ios: Mock/MockModels.swift:561
) {
    @Serializable
    enum class Kind { follow, after }   // ios: Mock/MockModels.swift:554
}

/** AI 한 번 쓴 기록 */
@Serializable
data class MockAIUse(   // ios: Mock/MockModels.swift:565
    var model: String,   // ios: Mock/MockModels.swift:566
    var input: Int,   // ios: Mock/MockModels.swift:567
    var output: Int,   // ios: Mock/MockModels.swift:568
    var cost: Double,                       // ios: Mock/MockModels.swift:569
    var summary: String,                    // ios: Mock/MockModels.swift:570
    var cached: Boolean = false,               // ios: Mock/MockModels.swift:571
)

// 화면 상태 한 줄

/** 탭마다 상태 한 줄: 마지막으로 돈 시각, 성공 여부, 실패 이유 */
@Serializable
data class MockRun(var at: MockDate, var ok: Boolean, var text: String)   // ios: Mock/MockModels.swift:577,578,579,580

// 숫자 글

object MockFmt {   // ios: Mock/MockModels.swift:585
    fun num(x: Double): String {   // ios: Mock/MockModels.swift:586
        if (x == x.swRound() && abs(x) < 1e15) return x.toLong().fmtInt()   // ios: Mock/MockModels.swift:587
        return cformat("%.2f", x).replace(Regex("""\.?0+$"""), "")   // ios: Mock/MockModels.swift:588
    }
    fun won(x: Double): String = x.swRound().toLong().fmtInt() + "원"   // ios: Mock/MockModels.swift:590
    /** " · " 로 이은 글에서 같은 조각은 한 번만 */
    fun uniq(s: String): String {   // ios: Mock/MockModels.swift:592
        val seen = HashSet<String>()   // ios: Mock/MockModels.swift:593
        return s.split(" · ").filter { seen.add(it) }.joinToString(" · ")   // ios: Mock/MockModels.swift:594
    }
    fun signed(x: Double): String = (if (x > 0) "+" else if (x < 0) "−" else "") + num(abs(x))   // ios: Mock/MockModels.swift:596
    /** 비율 → "+1.24%" */
    fun pct(r: Double, digits: Int = 2): String =   // ios: Mock/MockModels.swift:598
        (if (r > 0) "+" else if (r < 0) "−" else "") + cformat("%.${digits}f%%", abs(r) * 100)   // ios: Mock/MockModels.swift:599
    val kst: ZoneId = ZoneId.of("Asia/Seoul")   // ios: Mock/MockModels.swift:601
    fun inst(d: MockDate): Instant = Instant.ofEpochMilli((d * 1000).toLong())
    private val fDay = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(kst)   // ios: Mock/MockModels.swift:603
    fun day(d: MockDate): String = fDay.format(inst(d))   // ios: Mock/MockModels.swift:602,605

    private val fTime = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(kst)
    fun time(d: MockDate): String = fTime.format(inst(d))   // ios: Mock/MockModels.swift:607,609

    private val fStamp = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.US).withZone(kst)
    fun stamp(d: MockDate): String = fStamp.format(inst(d))   // ios: Mock/MockModels.swift:611,613
}

// 실전 규칙 목록 (탭 우상단 '실전' 아이콘)

/** 모의투자에 실전 매매 규칙을 어떻게 반영했나. 규칙을 넣거나 바꾸면 여기도 함께 고친다 (화면은 이 목록만 보여 준다) */
object MockLive {   // ios: Mock/MockModels.swift:619
    data class Rule(   // ios: Mock/MockModels.swift:620
        val name: String,   // ios: Mock/MockModels.swift:621
        val how: String,          // ios: Mock/MockModels.swift:622
        val gap: String?,         // ios: Mock/MockModels.swift:623
        val at: Set<Tab> = common,  // ios: Mock/MockModels.swift:624
    ) { val id: String get() = name }   // ios: Mock/MockModels.swift:625
    /** 실전 목록이 보이는 곳: 관찰 = 실전에 더 볼 조건, 선정 = 매수 규칙, 일지 = 매도·수수료·세금·모두 멈춤 */
    enum class Tab { observe, select, journal }   // ios: Mock/MockModels.swift:628
    /** 모든 주문에 걸리는 공통 규칙: 선정 · 일지 둘 다 */
    val common: Set<Tab> = setOf(Tab.select, Tab.journal)   // ios: Mock/MockModels.swift:630
    fun rules(t: Tab): List<Rule> = rules.filter { t in it.at }   // ios: Mock/MockModels.swift:631
    val rules: List<Rule> = listOf(   // ios: Mock/MockModels.swift:632
        Rule(name = "주문층", how = "관찰·선정·팔기 판단은 브로커(주문 넣기·취소·상태 보기)만 부른다. 기본은 모의 브로커, 계좌 탭 [실전]의 실전 자동주문 불빛(기본 끔, 확인 한 번 더)을 켜면 토스 브로커. 대기 주문이 있으면 못 바꾼다",   // ios: Mock/MockModels.swift:633
             gap = null),   // ios: Mock/MockModels.swift:634
        Rule(name = "토스 주문 API", how = "고정 IP 개발자 서버가 중계한다(앱에는 토큰·계좌 없음). 서버 스위치(armed)가 꺼지면 새 주문은 안 나가고 상태 보기·취소만 된다. 문서대로 주문 생성 POST /api/v1/orders(clientOrderId = 앱 주문 번호, 10분 멱등), 상세 GET /api/v1/orders/{id}, 취소 POST …/cancel. 세부 상태를 접수·부분 체결·체결·취소·거부로 바꾸고 오류 코드를 한국어로 기록",   // ios: Mock/MockModels.swift:635
             gap = null),   // ios: Mock/MockModels.swift:636
        Rule(name = "호출 한도", how = "그룹별 초당 한도(ORDER 10 · ORDER_HISTORY 5 · ORDER_INFO 6 · AUTH 5) 사이를 두고, 429 면 Retry-After 만큼 기다려 한 번 더",   // ios: Mock/MockModels.swift:637
             gap = "09:00~09:10 피크 한도(ORDER_INFO 3회)는 따로 맞추지 않는다"),   // ios: Mock/MockModels.swift:638
        Rule(name = "실전 장부", how = "실전에서도 앱 장부로 예산을 셈하고, 체결은 토스가 알려 준 수량·금액으로 적는다. 매수 현금은 장부 현금과 토스 주문가능금액 중 작은 값. 자동 매수 직전에 토스 계좌를 읽고, 못 읽으면 사지 않는다",   // ios: Mock/MockModels.swift:639
             gap = "수수료·세금은 앱 설정값으로 셈한다(토스 commission·tax 는 읽기만)"),   // ios: Mock/MockModels.swift:640
        Rule(name = "주문 상태", how = "접수 → 부분 체결 → 체결 · 취소 · 거부를 기록에 남긴다",   // ios: Mock/MockModels.swift:641
             gap = "모의는 앱이 호가로 셈한다. 실전은 토스 주문 상세를 1분마다 다시 읽는다(웹소켓 주문 알림은 아직 안 씀)"),   // ios: Mock/MockModels.swift:642
        Rule(name = "체결 지연", how = "접수 1.5초 뒤 새로 받은 호가로 체결한다 (그사이 호가가 바뀐다)",   // ios: Mock/MockModels.swift:643
             gap = "실제 지연은 망·거래소 상황마다 다르다"),   // ios: Mock/MockModels.swift:644
        Rule(name = "호가 잔량", how = "1호가부터 차례로 채우고, 먹은 잔량은 1분 동안 빼고 셈한다",   // ios: Mock/MockModels.swift:645
             gap = "다른 사람 주문으로 잔량이 다시 차는 것은 모른다"),   // ios: Mock/MockModels.swift:646
        Rule(name = "시장가 · 지정가", how = "지정가는 호가 단위(2023년 표)에 맞춘다. 매수는 내림, 매도는 올림", gap = null),   // ios: Mock/MockModels.swift:647
        Rule(name = "미끄러짐 한도", how = "다 채운 평균 단가가 1호가에서 0.5% 넘게 벗어나면 그 값의 지정가로 나누고, 남은 몫은 30분 뒤 취소. 손절·마감 정리 매도는 한 번에",   // ios: Mock/MockModels.swift:648
             gap = null, at = setOf(Tab.select)),   // ios: Mock/MockModels.swift:649
        Rule(name = "수수료 · 세금", how = "수수료 0.015% (사고팔 때), 매도 세금 0.20%. 설정에서 고친다",   // ios: Mock/MockModels.swift:650
             gap = "증권사 우대 수수료·세율이 바뀌면 손으로 맞춘다", at = setOf(Tab.journal)),   // ios: Mock/MockModels.swift:651
        Rule(name = "가격제한폭 ±30%", how = "전일 종가 ±30% 밖의 지정가는 거부한다",   // ios: Mock/MockModels.swift:652
             gap = "전일 종가를 아는 종목(마지막 훑기에 걸린 종목)만 확인한다", at = setOf(Tab.observe, Tab.select, Tab.journal)),   // ios: Mock/MockModels.swift:653
        Rule(name = "마감 동시호가", how = "15:20~15:30 에는 접수만 하고 15:30 단일가(종가)로 체결한다. 20분 안에 못 채우면 취소",   // ios: Mock/MockModels.swift:654
             gap = "장 시작 동시호가(08:30~09:00)는 장 밖 시험으로 처리한다"),   // ios: Mock/MockModels.swift:655
        Rule(name = "VI · 거래 정지", how = "훑은 뒤 걸린 종목 위쪽 30개의 토스 유의사항(VI·투자경고·투자위험·단기과열·정리매매·거래정지)을 받아 배지로 보인다. 거래정지·정리매매·투자위험은 고르기에서 뺀다. 호가가 양쪽 다 비어 있으면 거부",   // ios: Mock/MockModels.swift:656
             gap = "VI 기준가·VI 2분 단일가는 토스 API 에 없어 흉내 내지 못한다. 위쪽 30개 밖은 묻지 않는다", at = setOf(Tab.observe, Tab.select, Tab.journal)),   // ios: Mock/MockModels.swift:657
        Rule(name = "하루 손실 한도", how = "하루 기준 평가액보다 −3%(설정) 넘게 내리면 새로 사지 않는다. [모의]는 오늘 시작 평가액, [실전]은 어제 마감 토스 평가액(지금 평가액 − 토스 오늘 손익). 팔기·손 주문은 된다", gap = null, at = setOf(Tab.select)),   // ios: Mock/MockModels.swift:658
        Rule(name = "한 번 주문 상한", how = "한 번 매수 주문은 300만원(설정)까지. 손 주문도. [실전]은 서버 상한(max_krw)도 넘지 못하고, 넘으면 서버가 거부한다", gap = null, at = setOf(Tab.select)),   // ios: Mock/MockModels.swift:659
        Rule(name = "모두 멈춤", how = "설정의 단추. 모든 대기 주문을 취소하고 한 바퀴·돌리기·자동 팔기와 규칙 주문을 막는다. 손 주문은 된다", gap = null, at = setOf(Tab.journal)),   // ios: Mock/MockModels.swift:660
        Rule(name = "결제 T+2", how = "판 돈은 2영업일 뒤에 현금이 된다. 그 전엔 '결제 대기'로 따로 두고 평가액에는 넣는다",   // ios: Mock/MockModels.swift:661
             gap = "공휴일은 몰라 주말만 뺀다. 장 밖 시험으로 판 돈은 바로 현금", at = setOf(Tab.journal)),   // ios: Mock/MockModels.swift:662
        Rule(name = "증거금 · 미수", how = "미수를 쓰지 않는다. 현금보다 큰 손 주문은 거부하고, 결제 대기 돈이면 '이틀 뒤 들어올 돈'이라 알린다",   // ios: Mock/MockModels.swift:663
             gap = "실전 증거금률(종목별 40~100%)은 흉내 내지 않는다. 늘 현금 100%", at = setOf(Tab.select)),   // ios: Mock/MockModels.swift:664
        Rule(name = "계좌 잔고 맞추기", how = "하루 한 번, 체결 기록을 다시 더해 장부 현금·보유 수량과 견준다. 어긋나면 설정 안전장치 칸에 표시만 한다",   // ios: Mock/MockModels.swift:665
             gap = "실전 장부와 토스 실보유를 견주는 칸은 없다(보유는 일지에서 본다)", at = setOf(Tab.journal)),   // ios: Mock/MockModels.swift:666
        Rule(name = "토스 API 호출 한도", how = "시세·호가 부르기를 초당 5번(설정)으로 줄 세우고, 한도 응답(429)이면 기다렸다 3번까지 다시 부른다",   // ios: Mock/MockModels.swift:667
             gap = "토스 실제 한도 숫자는 확인 못 했다. 서버 쪽 한도는 따로"),   // ios: Mock/MockModels.swift:668
        Rule(name = "하루 구간", how = "9:00 지켜보기(훑기만) · 9:15 고르고 주문 · 15:00 정리(새로 사지 않음) · 15:30 마감. 자동 고르기·매수는 고르고 주문 구간에서만, 팔기·손절은 늘 돈다",   // ios: Mock/MockModels.swift:669
             gap = null, at = setOf(Tab.observe, Tab.select)),   // ios: Mock/MockModels.swift:670
        Rule(name = "하루 안에 끝냄", how = "단타라 하루를 넘겨 들지 않는다. 15:00 정리 구간에 남은 몫은 손익과 상관없이 시장가로 모두 판다. 앱이 꺼져 놓친 몫은 다음 장이 열리면 바로 판다",   // ios: Mock/MockModels.swift:671
             gap = "앱이 열려 있고 돌리기·팔기 알약이 켜져 있을 때만 돈다", at = setOf(Tab.journal)),   // ios: Mock/MockModels.swift:672
        Rule(name = "나눠 사고팔기", how = "한 종목 몫의 34%씩 산다. 더 담기는 이익 중에 다시 걸렸을 때만. 익절은 처음에 반만 팔고, 남은 몫은 고점 되밀림·이유 꺼짐·마감에 판다",   // ios: Mock/MockModels.swift:673
             gap = null, at = setOf(Tab.select, Tab.journal)),   // ios: Mock/MockModels.swift:674
        Rule(name = "시장 날씨", how = "코스피는 KODEX 200, 코스닥은 KODEX 코스닥150 의 오늘 등락으로 잰다. 그 종목 시장이 −1%(설정) 넘게 내리면 고르기 전에 거른다",   // ios: Mock/MockModels.swift:675
             gap = "지수 시세 길이 없어 ETF 로 대신한다. 장 전에는 모른다로 두고 거르지 않는다", at = setOf(Tab.observe)),   // ios: Mock/MockModels.swift:676
        Rule(name = "실전 장 밖", how = "실전이 켜져 있으면 장 밖에는 주문하지 않는다", gap = "장 개시 단일가(OPG)·장 마감 지정가(CLS) 주문, 웹소켓 체결 알림은 아직 쓰지 않는다"),   // ios: Mock/MockModels.swift:677
        Rule(name = "주문 정정", how = "대기 주문의 수량·지정가를 고친다(정정하면 지정가). 매수는 묶은 돈을 새로 셈하고, 실전이면 토스 정정을 먼저 보낸다",   // ios: Mock/MockModels.swift:678
             gap = "부분 체결 뒤 정정 수량을 토스가 남은 수량으로 보는지는 실주문으로 확인 전"),   // ios: Mock/MockModels.swift:679
        Rule(name = "예상가 대 체결가", how = "주문 순간 호가로 잰 예상 평균가를 남기고, 체결마다 차이를 적는다 (불리하면 +). 계좌 탭에 오늘·전체 누적", gap = null, at = setOf(Tab.journal)),   // ios: Mock/MockModels.swift:680
        Rule(name = "체결 알림", how = "체결 · 부분 체결 · 거부 때 휴대폰 알림. 앱을 보고 있으면 소리 없이 위쪽 띠로만. 설정에서 끈다",   // ios: Mock/MockModels.swift:681
             gap = "실전은 토스 체결 통지로 알린다. 지금은 앱이 셈한 체결로 알린다"),   // ios: Mock/MockModels.swift:682
    )
}
