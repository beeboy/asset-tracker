package com.naeilo.app.core.calc

import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.data.Currency
import com.naeilo.app.net.*
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.truncate

// 파도 타기 엔진 (화면 없음). 내 자산 3년 흐름에서 40거래일 코스를 잘라 타기·내리기를 판정한다.
// 값 하나로 두고 화면이 tick 을 불러 굴린다 (약 0.5초 = 1거래일, 20초 = 40거래일).
// Swift 는 값 타입(struct, mutating). Kotlin 은 바뀌는 클래스: 화면은 tick 뒤 상태를 다시 읽는다.

/** Swift RandomNumberGenerator 자리: next() 가 64비트 난수 하나. 같은 생성기면 Swift 와 같은 순서로 뽑는다 */
interface RandomNumberGenerator { fun next(): ULong }
/** Swift SystemRandomNumberGenerator 자리 */
class SystemRandomNumberGenerator : RandomNumberGenerator {
    override fun next(): ULong = ThreadLocalRandom.current().nextLong().toULong()
}

// ── Swift 표준 라이브러리 난수 도우미와 같은 식 (같은 생성기 → 같은 결과) ──
private const val LO32 = 0xFFFFFFFFuL
/** 64×64 곱의 위 64비트 (multipliedFullWidth.high) */
private fun mulHigh(a: ULong, b: ULong): ULong {
    val aLo = a and LO32; val aHi = a shr 32; val bLo = b and LO32; val bHi = b shr 32
    val ll = aLo * bLo; val lh = aLo * bHi; val hl = aHi * bLo; val hh = aHi * bHi
    val mid = (ll shr 32) + (lh and LO32) + (hl and LO32)
    return hh + (lh shr 32) + (hl shr 32) + (mid shr 32)
}
/** RandomNumberGenerator.next(upperBound:) (Lemire, 거절 표본) */
private fun RandomNumberGenerator.next(upperBound: ULong): ULong {
    var random = next()
    var lo = random * upperBound; var hi = mulHigh(random, upperBound)
    if (lo < upperBound) {
        val t = (0uL - upperBound) % upperBound
        while (lo < t) { random = next(); lo = random * upperBound; hi = mulHigh(random, upperBound) }
    }
    return hi
}
/** Int.random(in: a...b, using:) */
internal fun RandomNumberGenerator.intIn(a: Int, b: Int): Int {
    val delta = (b.toLong() - a.toLong()).toULong()
    if (delta == ULong.MAX_VALUE) return next().toInt()
    return (a.toLong() + next(delta + 1uL).toLong()).toInt()
}
/** Int.random(in: 0..<n, using:) */
internal fun RandomNumberGenerator.intBelow(n: Int): Int = next(n.toULong()).toInt()
/** Array.randomElement(using:) */
internal fun <T> List<T>.randomElement(using: RandomNumberGenerator): T? = if (isEmpty()) null else this[using.intBelow(size)]
/** Array.shuffled(using:): 앞에서부터 남은 칸 중 하나와 바꾼다 (Swift 와 같은 순서) */
internal fun <T> List<T>.shuffled(using: RandomNumberGenerator): List<T> {
    val a = toMutableList()
    if (a.size <= 1) return a
    var amount = a.size; var cur = 0
    while (amount > 1) {
        val r = using.intBelow(amount)
        amount -= 1
        val j = cur + r; val t = a[cur]; a[cur] = a[j]; a[j] = t
        cur += 1
    }
    return a
}
// Swift max/min 과 같은 비교식
private fun wvMax(x: Double, y: Double): Double = if (y >= x) y else x
private fun wvMin(x: Double, y: Double): Double = if (y < x) y else x
/** Swift Double.rounded() (0.5 는 0 에서 먼 쪽) */
private fun wvRound(x: Double): Double { val t = truncate(x); return if (abs(x - t) >= 0.5) t + (if (x < 0) -1.0 else 1.0) else t }

// ios: Data/WaveEngine.swift:6
class WaveEngine(
    // ios: Data/WaveEngine.swift:160,184
    val course: Course,
    using: RandomNumberGenerator,
) {
    // MARK: 섬 · 등급 · 이벤트

    // ios: Data/WaveEngine.swift:9,10
    enum class Island { nap, climb, coaster, choppy;
        val raw: String get() = name
        /** Swift name (Kotlin enum 은 name 을 못 써서 label) */
        // ios: Data/WaveEngine.swift:11,12
        val label: String get() = when (this) {
            nap -> "낮잠 해변"   // ios: Data/WaveEngine.swift:13
            climb -> "올라올라 언덕"   // ios: Data/WaveEngine.swift:14
            coaster -> "롤러코스터 만"   // ios: Data/WaveEngine.swift:15
            choppy -> "멀미 주의 바다"   // ios: Data/WaveEngine.swift:16
        }
        // ios: Data/WaveEngine.swift:19,20
        val line: String get() = when (this) {
            nap -> "파도가 순해요. 보드 위에서 졸지만 마세요."   // ios: Data/WaveEngine.swift:21
            climb -> "계속 오르막이에요. 너무 일찍 내리면 아까워요."   // ios: Data/WaveEngine.swift:22
            coaster -> "한 번 크게 꺼져요. 바닥을 잘 노려 보세요."   // ios: Data/WaveEngine.swift:23
            choppy -> "작은 파도가 쉴 새 없이 와요."   // ios: Data/WaveEngine.swift:24
        }
    }
    // ios: Data/WaveEngine.swift:28
    enum class Level { easy, hard;
        val raw: String get() = name
        // ios: Data/WaveEngine.swift:29
        val label: String get() = if (this == easy) "쉬움" else "어려움"
    }
    // ios: Data/WaveEngine.swift:31
    enum class EndReason { time, hearts }
    /** 파도 위에 떠 있는 아이템 (한 판에 1~2개, 탄 채 지나가면 먹는다) */
    // ios: Data/WaveEngine.swift:33,34
    enum class Item { lighthouse, turtle, vest, dolphin, rocket, snail, clock;
        // ios: Data/WaveEngine.swift:35,36
        val icon: String get() = when (this) {
            lighthouse -> "🗼"; turtle -> "🐢"; vest -> "🛟"; dolphin -> "🐬"   // ios: Data/WaveEngine.swift:37
            rocket -> "🚀"; snail -> "🐌"; clock -> "⏰"   // ios: Data/WaveEngine.swift:38
        }
        /** Swift name (Kotlin enum 은 name 을 못 써서 label) */
        // ios: Data/WaveEngine.swift:41,42
        val label: String get() = when (this) {
            lighthouse -> "등대! 바닥이 보여요"   // ios: Data/WaveEngine.swift:43
            turtle -> "거북이! 느릿느릿"   // ios: Data/WaveEngine.swift:44
            vest -> "구명조끼!"   // ios: Data/WaveEngine.swift:45
            dolphin -> "돌고래! 다음 점프 2배"   // ios: Data/WaveEngine.swift:46
            rocket -> "로켓! 빨라지고 점수 1.5배"   // ios: Data/WaveEngine.swift:47
            snail -> "달팽이! 아주 느릿느릿"   // ios: Data/WaveEngine.swift:48
            clock -> "시계! +3초"   // ios: Data/WaveEngine.swift:49
        }
    }
    /** 판 미션 (한 판에 3개, 하나 깰 때마다 +50) */
    // ios: Data/WaveEngine.swift:54,55
    enum class Mission { bottoms, streak3, flips3, dry, item, fever, score500;
        // ios: Data/WaveEngine.swift:56,57
        val title: String get() = when (this) {
            bottoms -> "바닥 3번 잡기"   // ios: Data/WaveEngine.swift:58
            streak3 -> "3번 연속 벌기"   // ios: Data/WaveEngine.swift:59
            flips3 -> "공중제비 3번"   // ios: Data/WaveEngine.swift:60
            dry -> "풍덩 없이 끝내기"   // ios: Data/WaveEngine.swift:61
            item -> "아이템 먹기"   // ios: Data/WaveEngine.swift:62
            fever -> "피버 켜기"   // ios: Data/WaveEngine.swift:63
            score500 -> "500점 넘기"   // ios: Data/WaveEngine.swift:64
        }
    }
    /** 2·3·4번(화면·소리·말풍선)이 붙일 이벤트 */
    // ios: Data/WaveEngine.swift:69
    sealed class Event {
        /** 탑승. perfect = 바닥 그날 탔다 ('딱') */
        // ios: Data/WaveEngine.swift:71
        data class board(val day: Int, val perfect: Boolean) : Event()
        /** 내림. gain = 탄 동안 수익률, points = 이번에 번 점수(보너스 포함) */
        // ios: Data/WaveEngine.swift:73
        data class leave(val day: Int, val gain: Double, val points: Int) : Event()
        /** 탄 채 급락에 휘말림. hearts = 남은 하트 */
        // ios: Data/WaveEngine.swift:75
        data class splash(val day: Int, val drop: Double, val hearts: Int) : Event()
        /** 아주 잔잔한 하루 */
        // ios: Data/WaveEngine.swift:77
        data class calm(val day: Int) : Event()
        /** 번 채로 내린 게 n 번 이어짐 (2번부터) */
        // ios: Data/WaveEngine.swift:79
        data class streak(val n: Int) : Event()
        /** 바닥 3번 연속 '딱' → 피버 켜짐(true) / 꺼짐(false) */
        // ios: Data/WaveEngine.swift:81
        data class fever(val on: Boolean) : Event()
        /** 공중에서 톡: n 바퀴째 */
        // ios: Data/WaveEngine.swift:83
        data class flip(val n: Int) : Event()
        /** 공중제비 착지. combo = 연속으로 돈 점프 수 */
        // ios: Data/WaveEngine.swift:85
        data class land(val flips: Int, val combo: Int, val points: Int) : Event()
        // ios: Data/WaveEngine.swift:86
        data class item(val item: Item) : Event()
        /** 남은 시간 늘어남 (초) */
        // ios: Data/WaveEngine.swift:88
        data class time(val s: Double) : Event()
        /** 구명조끼가 풍덩을 막음 (하트 그대로) */
        // ios: Data/WaveEngine.swift:90
        data class saved(val day: Int) : Event()
        // ios: Data/WaveEngine.swift:91
        data class mission(val m: Mission) : Event()
        // ios: Data/WaveEngine.swift:92
        data class end(val why: EndReason) : Event()
    }

    // MARK: 코스

    /** 라인 한 점 (Swift 튜플 (d: String, v: Double) 자리) */
    data class Pt(val d: String, val v: Double)

    // ios: Data/WaveEngine.swift:97
    data class Course(
        val dates: List<String>,      // ios: Data/WaveEngine.swift:98 "yyyy-MM-dd", 거래일
        val values: List<Double>,     // ios: Data/WaveEngine.swift:99 그날 평가액 (원)
        val island: Island,  // ios: Data/WaveEngine.swift:100
        val level: Level,  // ios: Data/WaveEngine.swift:101
    ) {
        // ios: Data/WaveEngine.swift:102
        val startDate: String get() = dates.firstOrNull() ?: ""
        // ios: Data/WaveEngine.swift:103
        val count: Int get() = values.size
    }

    // ios: Data/WaveEngine.swift:6 (static 멤버)
    companion object {
        // ios: Data/WaveEngine.swift:106
        const val days = 100              // 30초 + 보너스 시간보다 넉넉하게
        // ios: Data/WaveEngine.swift:107
        const val secondsPerDay = 0.42   // 점점 빨라져 30초 끝에 1.4배
        // ios: Data/WaveEngine.swift:108
        const val timeLimit = 30.0        // 한 판 기본 30초. '딱'·아이템·미션·피버로 늘어난다
        // ios: Data/WaveEngine.swift:109
        const val maxHearts = 3
        // ios: Data/WaveEngine.swift:110
        const val perfectBonus = 50       // '딱' 보너스 점수
        // ios: Data/WaveEngine.swift:111
        const val streakBonus = 20        // 연속 성공 한 번 더할 때마다
        // ios: Data/WaveEngine.swift:112
        const val feverNeed = 3; const val feverTime = 6.0   // 바닥 3연속이면 6초간 점수 2배
        // ios: Data/WaveEngine.swift:113
        const val airTime = 0.6; const val flipBonus = 15    // 번 채로 내리면 0.6초 점프, 그동안 톡마다 한 바퀴
        // ios: Data/WaveEngine.swift:114
        const val missionBonus = 50
        /** 아이템 뽑기 주머니: 시간·속도 아이템이 자주 나온다 */
        // ios: Data/WaveEngine.swift:116
        val itemBag: List<Item> = listOf(Item.clock, Item.clock, Item.clock, Item.rocket, Item.rocket, Item.turtle, Item.snail, Item.lighthouse, Item.vest, Item.dolphin)

        /** 3년 라인에서 무작위 시작 40거래일 코스. 라인이 짧거나 평평하면 null */
        // ios: Data/WaveEngine.swift:119
        fun course(from: List<Pt>, length: Int, using: RandomNumberGenerator): Course? {
            val line = from; val g = using
            // ios: Data/WaveEngine.swift:120
            if (!(line.size >= length && length >= 2)) return null
            // ios: Data/WaveEngine.swift:121
            val s = g.intIn(0, line.size - length)
            // ios: Data/WaveEngine.swift:122
            val part = line.subList(s, s + length)
            // ios: Data/WaveEngine.swift:123
            val vs = part.map { it.v }
            // ios: Data/WaveEngine.swift:124
            val lo = minOfSwift(vs) ?: return null; if (!(lo > 0) || maxOfSwift(vs) == lo) return null
            // ios: Data/WaveEngine.swift:125
            val (i, lv) = classify(vs)
            // ios: Data/WaveEngine.swift:126
            return Course(dates = part.map { it.d }, values = vs, island = i, level = lv)
        }
        // ios: Data/WaveEngine.swift:128,129,130
        fun course(from: List<Pt>, length: Int = days): Course? {
            val g = SystemRandomNumberGenerator()
            return course(from = from, length = length, using = g)
        }
        // Swift Array.min()/max() 와 같은 비교식 (앞에서부터 < 로 고른다)
        private fun minOfSwift(v: List<Double>): Double? { if (v.isEmpty()) return null; var m = v[0]; for (k in 1 until v.size) if (v[k] < m) m = v[k]; return m }
        private fun maxOfSwift(v: List<Double>): Double? { if (v.isEmpty()) return null; var m = v[0]; for (k in 1 until v.size) if (m < v[k]) m = v[k]; return m }

        /** 변동성·모양으로 섬과 등급을 정한다 */
        // ios: Data/WaveEngine.swift:134
        fun classify(v: List<Double>): Pair<Island, Level> {
            // ios: Data/WaveEngine.swift:135
            val r = rets(v)
            // ios: Data/WaveEngine.swift:136
            val sd = stdev(r)
            // ios: Data/WaveEngine.swift:137
            val total = v.last() / v.first() - 1
            // ios: Data/WaveEngine.swift:138
            var peak = v[0]; var dd = 0.0
            // ios: Data/WaveEngine.swift:139
            for (x in v) { peak = wvMax(peak, x); dd = wvMax(dd, 1 - x / peak) }
            // ios: Data/WaveEngine.swift:140
            var flips = 0; for (k in 0 until r.size - 1) if (r[k] * r[k + 1] < 0) flips += 1
            // ios: Data/WaveEngine.swift:141
            val flipRate = if (r.size > 1) flips.toDouble() / (r.size - 1).toDouble() else 0.0
            // ios: Data/WaveEngine.swift:142,143,144,145,146
            val island: Island = if (dd >= 0.06) Island.coaster
                else if (total >= 0.04 && dd < 0.04) Island.climb
                else if (sd >= 0.01 || flipRate >= 0.6) Island.choppy
                else Island.nap
            // ios: Data/WaveEngine.swift:147
            val hard = sd >= 0.012 || dd >= 0.08
            // ios: Data/WaveEngine.swift:148
            return Pair(island, if (hard) Level.hard else Level.easy)
        }

        // ios: Data/WaveEngine.swift:151
        fun rets(v: List<Double>): DoubleArray = DoubleArray(maxOf(0, v.size - 1)) { v[it + 1] / v[it] - 1 }
        // ios: Data/WaveEngine.swift:152
        fun stdev(r: DoubleArray): Double {
            // ios: Data/WaveEngine.swift:153
            if (r.size <= 1) return 0.0
            // ios: Data/WaveEngine.swift:154
            var sum = 0.0; for (x in r) sum += x; val m = sum / r.size.toDouble()
            // ios: Data/WaveEngine.swift:155
            var ss = 0.0; for (x in r) ss += (x - m) * (x - m); return sqrt(ss / (r.size - 1).toDouble())
        }
    }

    // MARK: 판 상태

    // ios: Data/WaveEngine.swift:161
    var day = 0; private set              // 지금 거래일 (0...count-1)
    // ios: Data/WaveEngine.swift:162
    var elapsed = 0.0; private set        // 코스 시간 (속도 반영, 거래일 = elapsed / secondsPerDay)
    // ios: Data/WaveEngine.swift:163
    var clock = 0.0; private set          // 실제 지난 초
    // ios: Data/WaveEngine.swift:164
    var extra = 0.0; private set          // 보너스로 늘어난 초
    // ios: Data/WaveEngine.swift:165
    var boost = 0.0; private set          // 로켓 남은 초
    // ios: Data/WaveEngine.swift:166
    var hearts = maxHearts; private set
    // ios: Data/WaveEngine.swift:167
    var score = 0; private set
    // ios: Data/WaveEngine.swift:168
    var boardedAt: Int? = null; private set      // 탄 날 (안 타고 있으면 null)
    // ios: Data/WaveEngine.swift:169
    var boardedPerfect = false; private set
    // ios: Data/WaveEngine.swift:170
    var streak = 0; private set
    // ios: Data/WaveEngine.swift:171
    var ended: EndReason? = null; private set
    // ios: Data/WaveEngine.swift:172
    private val itemsMap = LinkedHashMap<Int, Item>()   // 거래일 → 떠 있는 아이템
    val items: Map<Int, Item> get() = itemsMap
    // ios: Data/WaveEngine.swift:173
    val missions: List<Mission>
    // ios: Data/WaveEngine.swift:174
    private val missionsDoneSet = LinkedHashSet<Mission>()
    val missionsDone: Set<Mission> get() = missionsDoneSet
    // ios: Data/WaveEngine.swift:175
    private val gotList = ArrayList<Item>()
    val got: List<Item> get() = gotList
    // ios: Data/WaveEngine.swift:176
    var perfectRun = 0; private set
    var perfectCount = 0; private set
    var maxStreak = 0; private set
    var flipCount = 0; private set
    var splashCount = 0; private set
    var feverCount = 0; private set
    // ios: Data/WaveEngine.swift:177
    var feverLeft = 0.0; private set
    var beacon = 0.0; private set
    var slow = 0.0; private set
    var air = 0.0; private set
    // ios: Data/WaveEngine.swift:178
    var vest = false; private set
    var dolphin = false; private set
    // ios: Data/WaveEngine.swift:179
    var flips = 0; private set
    var combo = 0; private set
    /** 탄 채 하루 낙폭이 이보다 크면 풍덩 (코스 변동성 2배, 최소 1.5%) */
    // ios: Data/WaveEngine.swift:181
    val splashDrop: Double

    // ios: Data/WaveEngine.swift:183
    init {
        val g = using
        // ios: Data/WaveEngine.swift:185
        splashDrop = wvMax(0.015, 2 * stdev(rets(course.values)))
        // ios: Data/WaveEngine.swift:186,187
        if (course.count >= 16) {
            // ios: Data/WaveEngine.swift:188
            for (rep in 0 until maxOf(1, course.count / 12)) {
                // ios: Data/WaveEngine.swift:189
                val d = g.intIn(6, course.count - 6)
                // ios: Data/WaveEngine.swift:190
                if (itemsMap.keys.all { abs(it - d) >= 5 }) { itemsMap[d] = itemBag.randomElement(using = g)!! }
            }
        }
        // ios: Data/WaveEngine.swift:193 (items = its: 위에서 itemsMap 에 바로 넣었다)
        // ios: Data/WaveEngine.swift:194
        missions = Mission.entries.shuffled(using = g).take(3)
    }
    // ios: Data/WaveEngine.swift:196,197,198
    constructor(course: Course) : this(course = course, using = SystemRandomNumberGenerator())

    /** 한 판 제한 시간 (보너스 포함) */
    // ios: Data/WaveEngine.swift:202
    val duration: Double get() = timeLimit + extra
    // ios: Data/WaveEngine.swift:203
    val timeLeft: Double get() = wvMax(0.0, duration - clock)
    // ios: Data/WaveEngine.swift:204
    val courseTime: Double get() = (course.count - 1).toDouble() * secondsPerDay
    // ios: Data/WaveEngine.swift:205
    val riding: Boolean get() = boardedAt != null
    // ios: Data/WaveEngine.swift:206
    val isOver: Boolean get() = ended != null
    // ios: Data/WaveEngine.swift:207
    val fever: Boolean get() = feverLeft > 0
    // ios: Data/WaveEngine.swift:208
    val airborne: Boolean get() = air > 0
    // ios: Data/WaveEngine.swift:209
    val date: String get() = course.dates[day]
    // ios: Data/WaveEngine.swift:210
    val value: Double get() = course.values[day]
    /** 탄 뒤 지금까지 수익률 (안 타면 0) */
    // ios: Data/WaveEngine.swift:212
    val rideGain: Double get() = boardedAt?.let { value / course.values[it] - 1 } ?: 0.0

    /** 그날이 앞뒤 3거래일 안에서 가장 낮으면 바닥 */
    // ios: Data/WaveEngine.swift:215
    fun isBottom(i: Int): Boolean {
        // ios: Data/WaveEngine.swift:216
        val a = maxOf(0, i - 3); val b = minOf(course.count - 1, i + 3)
        // ios: Data/WaveEngine.swift:217
        for (k in a..b) if (!(course.values[k] >= course.values[i])) return false; return true
    }

    // MARK: 조작

    // ios: Data/WaveEngine.swift:222
    fun board(): List<Event> {
        // ios: Data/WaveEngine.swift:223
        if (isOver || riding) return emptyList()
        // ios: Data/WaveEngine.swift:224
        if (airborne) return flipTap()
        // ios: Data/WaveEngine.swift:225
        boardedAt = day
        // ios: Data/WaveEngine.swift:226
        boardedPerfect = isBottom(day)
        // ios: Data/WaveEngine.swift:227
        val ev = mutableListOf<Event>(Event.board(day = day, perfect = boardedPerfect))
        // ios: Data/WaveEngine.swift:228
        if (boardedPerfect) {
            // ios: Data/WaveEngine.swift:229
            perfectCount += 1; perfectRun += 1
            // ios: Data/WaveEngine.swift:230
            ev += addTime(1.0)
            // ios: Data/WaveEngine.swift:231
            if (perfectRun >= feverNeed) {
                // ios: Data/WaveEngine.swift:232
                if (!fever) { feverCount += 1; ev.add(Event.fever(true)); ev += addTime(2.0) }
                // ios: Data/WaveEngine.swift:233
                feverLeft = feverTime; perfectRun = 0
            }
        } else { perfectRun = 0 }   // ios: Data/WaveEngine.swift:235
        // ios: Data/WaveEngine.swift:236
        return ev + checkMissions()
    }

    /** 점프 중 톡: 한 바퀴 더 (점프 끝 0.15초 전까지, 최대 3바퀴) */
    // ios: Data/WaveEngine.swift:240
    fun flipTap(): List<Event> {
        // ios: Data/WaveEngine.swift:241
        if (!(airborne && air > 0.15 && flips < 3)) return emptyList()
        // ios: Data/WaveEngine.swift:242
        flips += 1; flipCount += 1
        // ios: Data/WaveEngine.swift:243
        return listOf<Event>(Event.flip(flips)) + checkMissions()
    }

    // ios: Data/WaveEngine.swift:246
    fun leave(): List<Event> {
        // ios: Data/WaveEngine.swift:247
        val b = boardedAt; if (isOver || b == null) return emptyList()
        // ios: Data/WaveEngine.swift:248
        val gain = value / course.values[b] - 1
        // ios: Data/WaveEngine.swift:249
        var pts = wvRound(gain * 10000).toInt()        // 0.01% = 1점
        // ios: Data/WaveEngine.swift:250
        if (boardedPerfect && gain > 0) pts += perfectBonus
        // ios: Data/WaveEngine.swift:251
        val ev = mutableListOf<Event>()
        // ios: Data/WaveEngine.swift:252
        if (gain > 0) {
            // ios: Data/WaveEngine.swift:253
            streak += 1
            // ios: Data/WaveEngine.swift:254
            if (streak >= 2) { pts += streakBonus * (streak - 1); ev.add(Event.streak(streak)) }
        } else { streak = 0 }   // ios: Data/WaveEngine.swift:255
        // ios: Data/WaveEngine.swift:256
        maxStreak = maxOf(maxStreak, streak)
        // ios: Data/WaveEngine.swift:257
        if (gain > 0) {
            // ios: Data/WaveEngine.swift:258
            if (dolphin) { pts *= 2; dolphin = false }
            // ios: Data/WaveEngine.swift:259
            if (fever) { pts *= 2 }
            // ios: Data/WaveEngine.swift:260
            if (boost > 0) { pts = pts * 3 / 2 }
            // ios: Data/WaveEngine.swift:261
            air = airTime; flips = 0
        }
        // ios: Data/WaveEngine.swift:263
        score += pts
        // ios: Data/WaveEngine.swift:264
        boardedAt = null; boardedPerfect = false
        // ios: Data/WaveEngine.swift:265
        return listOf<Event>(Event.leave(day = day, gain = gain, points = pts)) + ev + checkMissions()
    }

    /** 화면이 매 프레임 부른다. dt 초만큼 시간을 흘리고 넘어간 거래일마다 판정한다 */
    // ios: Data/WaveEngine.swift:269
    fun tick(dt: Double): List<Event> {
        // ios: Data/WaveEngine.swift:270
        if (isOver) return emptyList()
        // ios: Data/WaveEngine.swift:271
        val real = wvMax(0.0, dt)
        // ios: Data/WaveEngine.swift:272
        clock += real
        // ios: Data/WaveEngine.swift:273
        var speed = 1 + 0.4 * wvMin(1.0, clock / timeLimit)    // 뒤로 갈수록 빨라져 30초에 1.4배
        // ios: Data/WaveEngine.swift:274
        if (boost > 0) { speed *= 1.6 }                           // 로켓
        // ios: Data/WaveEngine.swift:275
        if (slow > 0) { speed *= 0.5 }                            // 거북이·달팽이
        // ios: Data/WaveEngine.swift:276
        elapsed += real * speed
        // ios: Data/WaveEngine.swift:277
        slow = wvMax(0.0, slow - real); beacon = wvMax(0.0, beacon - real); boost = wvMax(0.0, boost - real)
        // ios: Data/WaveEngine.swift:278
        val ev = mutableListOf<Event>()
        // ios: Data/WaveEngine.swift:279
        if (feverLeft > 0) { feverLeft -= real; if (feverLeft <= 0) { feverLeft = 0.0; ev.add(Event.fever(false)) } }
        // ios: Data/WaveEngine.swift:280
        if (air > 0) { air -= real; if (air <= 0) { air = 0.0; ev += land() } }
        // ios: Data/WaveEngine.swift:281
        val target = minOf(course.count - 1, (elapsed / secondsPerDay).toInt())
        // ios: Data/WaveEngine.swift:282
        while (day < target && !isOver) {
            // ios: Data/WaveEngine.swift:283
            day += 1
            // ios: Data/WaveEngine.swift:284
            val r = course.values[day] / course.values[day - 1] - 1
            // ios: Data/WaveEngine.swift:285
            if (riding) { itemsMap[day]?.let { itm -> itemsMap.remove(day); ev += use(itm) } }
            // ios: Data/WaveEngine.swift:286
            if (riding && r <= -splashDrop) {
                // 탄 채 휘말리면 그날 값으로 강제로 내려지고 하트 하나를 잃는다
                // ios: Data/WaveEngine.swift:288
                val b = boardedAt!!
                // ios: Data/WaveEngine.swift:289
                score += wvRound((value / course.values[b] - 1) * 10000).toInt()
                // ios: Data/WaveEngine.swift:290
                boardedAt = null; boardedPerfect = false; streak = 0; perfectRun = 0
                // ios: Data/WaveEngine.swift:291
                splashCount += 1
                // ios: Data/WaveEngine.swift:292
                if (!vest) { hearts -= 1 }
                // ios: Data/WaveEngine.swift:293
                ev.add(Event.splash(day = day, drop = r, hearts = hearts))
                // ios: Data/WaveEngine.swift:294
                if (vest) { vest = false; ev.add(Event.saved(day = day)) }
                // ios: Data/WaveEngine.swift:295
                if (hearts <= 0) { ev += finish(EndReason.hearts) }
            } else if (abs(r) < 0.0015) {   // ios: Data/WaveEngine.swift:296
                // ios: Data/WaveEngine.swift:297
                ev.add(Event.calm(day = day))
            }
        }
        // ios: Data/WaveEngine.swift:300
        if (!isOver) { ev += checkMissions() }
        // ios: Data/WaveEngine.swift:301
        if (!isOver && (clock >= duration || elapsed >= courseTime)) { ev += finish(EndReason.time) }
        // ios: Data/WaveEngine.swift:302
        return ev
    }

    // ios: Data/WaveEngine.swift:305
    private fun use(it: Item): List<Event> {
        // ios: Data/WaveEngine.swift:306
        when (it) {
            Item.lighthouse -> beacon = 5.0   // ios: Data/WaveEngine.swift:307
            Item.turtle -> slow = 3.0   // ios: Data/WaveEngine.swift:308
            Item.vest -> vest = true   // ios: Data/WaveEngine.swift:309
            Item.dolphin -> dolphin = true   // ios: Data/WaveEngine.swift:310
            Item.rocket -> boost = 3.0   // ios: Data/WaveEngine.swift:311
            Item.snail -> slow = 4.0   // ios: Data/WaveEngine.swift:312
            Item.clock -> {}   // ios: Data/WaveEngine.swift:313
        }
        // ios: Data/WaveEngine.swift:315
        gotList.add(it)
        // ios: Data/WaveEngine.swift:316
        return listOf<Event>(Event.item(it)) + addTime(if (it == Item.clock) 3.0 else 1.0)
    }

    /** 남은 시간 늘리기: '딱' 1초 · 아이템 1초(시계 3초) · 피버 2초 · 미션 3초 */
    // ios: Data/WaveEngine.swift:320
    private fun addTime(s: Double): List<Event> {
        // ios: Data/WaveEngine.swift:321
        extra += s
        // ios: Data/WaveEngine.swift:322
        return listOf(Event.time(s))
    }

    /** 점프 끝. 한 바퀴라도 돌았으면 바퀴 × 콤보 점수, 안 돌았으면 콤보가 끊긴다 */
    // ios: Data/WaveEngine.swift:326
    private fun land(): List<Event> {
        // ios: Data/WaveEngine.swift:327
        if (!(flips > 0)) { combo = 0; return emptyList() }
        // ios: Data/WaveEngine.swift:328
        combo += 1
        // ios: Data/WaveEngine.swift:329
        val pts = flipBonus * flips * combo * (if (fever) 2 else 1)
        // ios: Data/WaveEngine.swift:330
        score += pts
        // ios: Data/WaveEngine.swift:331
        return listOf(Event.land(flips = flips, combo = combo, points = pts))
    }

    // ios: Data/WaveEngine.swift:334
    fun met(m: Mission): Boolean = when (m) {   // ios: Data/WaveEngine.swift:335
        Mission.bottoms -> perfectCount >= 3   // ios: Data/WaveEngine.swift:336
        Mission.streak3 -> maxStreak >= 3   // ios: Data/WaveEngine.swift:337
        Mission.flips3 -> flipCount >= 3   // ios: Data/WaveEngine.swift:338
        Mission.dry -> ended == EndReason.time && splashCount == 0   // ios: Data/WaveEngine.swift:339
        Mission.item -> gotList.isNotEmpty()   // ios: Data/WaveEngine.swift:340
        Mission.fever -> feverCount > 0   // ios: Data/WaveEngine.swift:341
        Mission.score500 -> score >= 500   // ios: Data/WaveEngine.swift:342
    }

    // ios: Data/WaveEngine.swift:346
    private fun checkMissions(): List<Event> {
        // ios: Data/WaveEngine.swift:347
        val ev = mutableListOf<Event>()
        // ios: Data/WaveEngine.swift:348
        for (m in missions) { if (!(!missionsDoneSet.contains(m) && met(m))) continue
            // ios: Data/WaveEngine.swift:349
            missionsDoneSet.add(m); score += missionBonus; ev.add(Event.mission(m))
            // ios: Data/WaveEngine.swift:350
            if (!isOver) { ev += addTime(3.0) }
        }
        // ios: Data/WaveEngine.swift:352
        return ev
    }

    // ios: Data/WaveEngine.swift:355
    private fun finish(why: EndReason): List<Event> {
        // ios: Data/WaveEngine.swift:356
        val ev = mutableListOf<Event>()
        // ios: Data/WaveEngine.swift:357
        if (riding && why == EndReason.time) { ev += leave() }   // 시간 끝: 타고 있던 건 그날 값으로 내린다
        // ios: Data/WaveEngine.swift:358
        if (air > 0) { air = 0.0; ev += land() }
        // ios: Data/WaveEngine.swift:359
        ended = why
        // ios: Data/WaveEngine.swift:360
        ev += checkMissions()
        // ios: Data/WaveEngine.swift:361
        return ev + listOf(Event.end(why))
    }
}

/** 파도 타기용 3년 자산 라인: 지금 수량 × 지난 3년 거래일 종가 (달러 종목은 그날 원/달러), 현금은 그대로 더한다.
 *  분석 화면 pathSeries 와 같은 자료·식이고 수량만 지금 수량으로 고정한다 */
// ios: Data/WaveEngine.swift:365,368
val AppModel.waveLine: List<WaveEngine.Pt> get() {
    // ios: Data/WaveEngine.swift:369
    val today = Day.today; val fxH = PriceHistory.of("FX"); val cash = cashKrw
    // ios: Data/WaveEngine.swift:370
    val from = Day.str(ZonedDateTime.now(ZoneId.systemDefault()).minusYears(3).toInstant())
    // ios: Data/WaveEngine.swift:371
    val hs = rows.map { Pair(it, PriceHistory.of(it.id)) }
    // ios: Data/WaveEngine.swift:372
    val ds = HashSet<String>()
    // ios: Data/WaveEngine.swift:373
    for (x in hs) { val h = x.second ?: continue; for (d in h.dates.asReversed()) { if (d < from) break; if (d < today) ds.add(d) } }
    // ios: Data/WaveEngine.swift:374
    return ds.sorted().map { d ->
        // ios: Data/WaveEngine.swift:375
        val v = hs.fold(0.0) { a, x ->
            // ios: Data/WaveEngine.swift:376
            val h = x.second ?: return@fold a + x.first.value
            // ios: Data/WaveEngine.swift:377
            val px = h.on(d) ?: return@fold a
            // ios: Data/WaveEngine.swift:378
            a + px * x.first.h.qty * (if (x.first.sym.currency == Currency.usd) (fxH?.on(d) ?: Market.shared.valFx) else 1.0)
        }
        // ios: Data/WaveEngine.swift:380
        WaveEngine.Pt(d = d, v = v + cash)
    }
}
/** 새 파도 코스 (라인이 짧으면 null) */
// ios: Data/WaveEngine.swift:384
fun AppModel.waveCourse(): WaveEngine.Course? = WaveEngine.course(from = waveLine)
