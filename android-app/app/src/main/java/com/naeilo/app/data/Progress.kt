package com.naeilo.app.data

import com.naeilo.app.net.*
import com.naeilo.app.core.calc.*
import android.content.Context
import android.content.SharedPreferences
import com.naeilo.app.*
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/** UserDefaults.standard 자리: SharedPreferences("naeilo", 키 이름 그대로). 앱 시작 때 init(ctx) 한 번 (Application.onCreate) */
object Defaults {
    lateinit var ctx: Context
        private set
    val prefs: SharedPreferences by lazy { ctx.getSharedPreferences("naeilo", Context.MODE_PRIVATE) }
    fun init(ctx: Context) { this.ctx = ctx.applicationContext }
    /** Bundle.main.url(forResource:withExtension:) 자리: assets/<name>.<ext> 를 글자로 (없으면 null) */
    fun asset(name: String, ext: String): String? =
        runCatching { ctx.assets.open("$name.$ext").bufferedReader().use { it.readText() } }.getOrNull()
}

// 미션 진행 저장: 앱을 다시 켜도 미션 판·인터미션·오늘의 1분·쉼터·목표 설정이 이어진다.
// 내 종목(holdings)과 알림 설정은 따로 저장한다. 화면 위치(어느 탭, 어느 화면)는 저장하지 않는다.
// ios: Data/Progress.swift:5
@Serializable
data class Progress(
    var v: Int = 1,  // ios: Data/Progress.swift:6
    // 회복 루트
    var done: Set<Int>,  // ios: Data/Progress.swift:8
    var weeks: List<WeekPick>,  // ios: Data/Progress.swift:9
    var weekCur: WeekPick? = null,  // ios: Data/Progress.swift:10
    var planKey: String,  // ios: Data/Progress.swift:11
    var horizon: Int,  // ios: Data/Progress.swift:12
    var quizAnswer: String? = null,  // ios: Data/Progress.swift:13
    var taxGain: Double,  // ios: Data/Progress.swift:14
    var taxSellQty: Double,  // ios: Data/Progress.swift:15
    var nxStep: Int,  // ios: Data/Progress.swift:16
    // 목표 루트
    var route: Route,  // ios: Data/Progress.swift:18
    var gK: Double, var gY: Int, var gA: Double, var gM: Double,  // ios: Data/Progress.swift:19
    var gMix: String,  // ios: Data/Progress.swift:20
    var gDone: Set<String>,  // ios: Data/Progress.swift:21
    var gWeeks: List<GoalWeek>,  // ios: Data/Progress.swift:22
    var gWeekCur: GoalWeek? = null,  // ios: Data/Progress.swift:23
    var goalTaxPick: String,  // ios: Data/Progress.swift:24
    // 오늘의 1분 (day = 시작한 날부터 며칠째, 0부터)
    var day: Int,  // ios: Data/Progress.swift:26
    var dayLog: Map<Int, DayLog>,  // ios: Data/Progress.swift:27
    var lastOpen: String,          // ios: Data/Progress.swift:28 마지막으로 연 날 (yyyy-MM-dd). 다음에 열 때 지난 날수만큼 day 를 넘긴다
    var weekGuess: String? = null,  // ios: Data/Progress.swift:29
    var itemsBack: Int? = null,           // ios: Data/Progress.swift:30 돌아온 물건 수 (없으면 예전 저장: 연속 일수로 채운다)
    var itemsBackDay: Int? = null,  // ios: Data/Progress.swift:31
    // 쉼터
    var homeFriend: String,  // ios: Data/Progress.swift:33
    var readPos: Set<Int>,  // ios: Data/Progress.swift:34
    var readLast: Int? = null,  // ios: Data/Progress.swift:35
    // 분석·기기
    var lens: Lens,  // ios: Data/Progress.swift:37
    var trust: Double,  // ios: Data/Progress.swift:38
    var shock: Boolean,  // ios: Data/Progress.swift:39
    var monthly: Double,  // ios: Data/Progress.swift:40
    var syncOn: Boolean,               // ios: Data/Progress.swift:41 기록용 (실제 상태는 이 기기 키체인)
    var widgetSel: List<String>? = null,  // ios: Data/Progress.swift:42
    // 내 캐릭터·쉼터 배경·인터미션 시작 날 (없으면 예전 저장)
    var me: MeChar? = null,  // ios: Data/Progress.swift:44
    var meBg: String? = null,  // ios: Data/Progress.swift:45
    var interStartAt: Double? = null,  // ios: Data/Progress.swift:46
    var goalStartAt: Double? = null,  // ios: Data/Progress.swift:47
    var meGate1At: Double? = null,  // ios: Data/Progress.swift:48
    var itemsOff: Set<String>? = null,   // ios: Data/Progress.swift:49 쉼터 방에서 뺀 물건 (모은 건 그대로, 다시 놓을 수 있다)
    var gInterStartAt: Double? = null,   // ios: Data/Progress.swift:50 목표 루트 인터미션 시작 날
    var recM: Int? = null,               // ios: Data/Progress.swift:51 회복 루트 적립 지렛대 (만원)
    var gTo: String? = null,             // ios: Data/Progress.swift:52 목표일·목표 시작일 (사이트와 같은 값)
    var gFrom: String? = null,  // ios: Data/Progress.swift:53
) {
    // ios: Data/Progress.swift:55
    companion object {
        private const val key = "progress.v1"
        // ios: Data/Progress.swift:56,57
        fun load(): Progress? =
            Defaults.prefs.getString(key, null)?.let { runCatching { NJson.decodeFromString<Progress>(it) }.getOrNull() }
    }
    // ios: Data/Progress.swift:59,60
    fun save() {
        runCatching { NJson.encodeToString(serializer(), this) }.getOrNull()?.let { d -> Defaults.prefs.edit().putString(key, d).apply() }
    }
}

/** 캡처·확인용 실행 인자가 있으면 저장된 진행을 읽지도 쓰지도 않는다 */
// ios: Data/Progress.swift:64,66,67,68
val AppModel.persists: Boolean get() {
    // iOS 실행 인자는 UserDefaults 에 들어온다. 안드로이드 실행 인자는 intent extra 라 arg() 로 함께 본다 (안 보면 캡처 실행이 저장된 진행을 읽고 쓴다)
    val d = Defaults.prefs
    return !listOf("demo", "goal", "route", "tab", "home", "hold", "set", "an", "addTest", "searchTest").any { d.contains(it) || arg(it) != null }
}

// ios: Data/Progress.swift:71,72,73,74,75,76,77,78,79
val AppModel.progress: Progress get() =
    Progress(done = done, weeks = weeks, weekCur = weekCur, planKey = planKey, horizon = horizon, quizAnswer = quizAnswer,
             taxGain = taxGain, taxSellQty = taxSellQty, nxStep = nxStep,
             route = route, gK = gK, gY = gY, gA = gA, gM = gM, gMix = gMix, gDone = gDone, gWeeks = gWeeks, gWeekCur = gWeekCur, goalTaxPick = goalTaxPick,
             day = day, dayLog = dayLog, lastOpen = Day.today, weekGuess = weekGuess, itemsBack = itemsBack, itemsBackDay = itemsBackDay,
             homeFriend = homeFriend, readPos = readPos, readLast = readLast,
             lens = lens, trust = trust, shock = shock, monthly = monthly, syncOn = syncOn, widgetSel = widgetSel,
             me = me, meBg = meBg, interStartAt = interStartAt, goalStartAt = goalStartAt, meGate1At = meGate1At, itemsOff = if (itemsOff.isEmpty()) null else itemsOff,
             gInterStartAt = gInterStartAt, recM = if (recM == 0) null else recM, gTo = gTo, gFrom = gFrom)

// ios: Data/Progress.swift:82
fun AppModel.apply(p: Progress) {
    done = p.done; weeks = p.weeks; weekCur = p.weekCur; planKey = p.planKey; horizon = p.horizon; quizAnswer = p.quizAnswer  // ios: Data/Progress.swift:83
    taxGain = p.taxGain; taxSellQty = p.taxSellQty; nxStep = p.nxStep  // ios: Data/Progress.swift:84
    route = p.route; gK = p.gK; gY = p.gY; gA = p.gA; gM = p.gM; gMix = p.gMix; gDone = p.gDone; gWeeks = p.gWeeks; gWeekCur = p.gWeekCur  // ios: Data/Progress.swift:85
    goalTaxPick = p.goalTaxPick  // ios: Data/Progress.swift:86
    dayLog = p.dayLog; weekGuess = p.weekGuess  // ios: Data/Progress.swift:87
    homeFriend = p.homeFriend; readPos = p.readPos; readLast = p.readLast  // ios: Data/Progress.swift:88
    lens = p.lens; trust = p.trust; shock = p.shock; monthly = p.monthly; widgetSel = p.widgetSel  // ios: Data/Progress.swift:89
    if (p.me != null) me = p.me    // ios: Data/Progress.swift:90 캐릭터가 없는 저장(예전 파일·다른 기기)으로 지우지 않는다
    meBg = p.meBg; interStartAt = p.interStartAt; goalStartAt = p.goalStartAt; meGate1At = meGate1At ?: p.meGate1At  // ios: Data/Progress.swift:91
    day = p.day  // ios: Data/Progress.swift:92
    itemsBack = p.itemsBack ?: min(10, streak / 7); itemsBackDay = p.itemsBackDay  // ios: Data/Progress.swift:93
    itemsOff = p.itemsOff ?: emptySet()  // ios: Data/Progress.swift:94
    gInterStartAt = p.gInterStartAt; recM = p.recM ?: 0  // ios: Data/Progress.swift:95
    gTo = p.gTo     // ios: Data/Progress.swift:96 gY 다음에 둔다 (gY 를 바꾸면 목표일이 지워진다)
    // 예전 저장: 목표 루트 시작일을 목표 시작일로
    gFrom = p.gFrom ?: (if (p.route != Route.recover) Day.str(p.goalStartAt?.let { Instant.ofEpochMilli((it * 1000).toLong()) } ?: Instant.now()) else null)  // ios: Data/Progress.swift:98
    // 예전 기본값(3)으로 저장돼 미션 4 전에 위젯이 다 열려 보이던 것: 앱 시작을 마치기 전이면 1단계부터
    if (!(if (route == Route.recover) done.contains(5) else gDone.contains("link")) && nxStep >= 3) nxStep = 1  // ios: Data/Progress.swift:100
    catchUpDay(p.lastOpen)  // ios: Data/Progress.swift:101
}

/** 달력이 넘어간 만큼 오늘의 1분 날짜도 넘긴다 (안 연 날은 빈 칸으로 남아 연속 기록이 끊긴다).
 *  앱을 열 때와, 켜 둔 채 날이 바뀐 뒤 다시 앞으로 올 때 부른다 */
// ios: Data/Progress.swift:106,107,108,109,110,111
fun AppModel.catchUpDay(last: String? = null) {
    val from = last ?: Defaults.prefs.getString("progress.openedOn", null) ?: Day.today
    val gap = Day.date(from)?.let { Duration.between(it, Day.date(Day.today) ?: Instant.now()).toDays().toInt() } ?: 0
    if (gap > 0) day += gap
    Defaults.prefs.edit().putString("progress.openedOn", Day.today).apply()
    saveProgress()
}

// ios: Data/Progress.swift:114
fun AppModel.saveProgress() { if (persists) progress.save() }
